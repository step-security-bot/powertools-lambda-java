package software.amazon.lambda.powertools.sqs.internal;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.lambda.powertools.sqs.MessageExceptionHandler;
import software.amazon.lambda.powertools.sqs.SqsLargeMessage;
import software.amazon.payloadoffloading.PayloadS3Pointer;

import static com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import static java.lang.String.format;
import static software.amazon.lambda.powertools.core.internal.LambdaHandlerProcessor.isHandlerMethod;
import static software.amazon.lambda.powertools.sqs.MessageExceptionHandler.NoOpExceptionHandler;
import static software.amazon.lambda.powertools.sqs.SqsUtils.s3Client;

@Aspect
public class SqsLargeMessageAspect {

    private static final Logger LOG = LoggerFactory.getLogger(SqsLargeMessageAspect.class);

    @SuppressWarnings({"EmptyMethod"})
    @Pointcut("@annotation(sqsLargeMessage)")
    public void callAt(SqsLargeMessage sqsLargeMessage) {
    }

    @Around(value = "callAt(sqsLargeMessage) && execution(@SqsLargeMessage * *.*(..))", argNames = "pjp,sqsLargeMessage")
    public Object around(ProceedingJoinPoint pjp,
                         SqsLargeMessage sqsLargeMessage) throws Throwable {
        Object[] proceedArgs = pjp.getArgs();

        if (isHandlerMethod(pjp)
                && placedOnSqsEventRequestHandler(pjp)) {
            List<PayloadS3Pointer> pointersToDelete = rewriteMessages((SQSEvent) proceedArgs[0], sqsLargeMessage);

            Object proceed = pjp.proceed(proceedArgs);

            if (sqsLargeMessage.deletePayloads()) {
                pointersToDelete.forEach(SqsLargeMessageAspect::deleteMessage);
            }
            return proceed;
        }

        return pjp.proceed(proceedArgs);
    }

    private List<PayloadS3Pointer> rewriteMessages(SQSEvent sqsEvent, SqsLargeMessage sqsLargeMessage) {
        List<SQSMessage> records = sqsEvent.getRecords();
        return processMessages(records, sqsLargeMessage.failureHandler());
    }

    public static List<PayloadS3Pointer> processMessages(final List<SQSMessage> records) {
        return processMessages(records, NoOpExceptionHandler.class);
    }

    private static List<PayloadS3Pointer> processMessages(final List<SQSMessage> records,
                                                          final Class<? extends MessageExceptionHandler> failureHandler) {

        List<PayloadS3Pointer> s3Pointers = new ArrayList<>();

        for (SQSMessage sqsMessage : records) {
            if (isBodyLargeMessagePointer(sqsMessage.getBody())) {

                PayloadS3Pointer s3Pointer = PayloadS3Pointer.fromJson(sqsMessage.getBody())
                        .orElseThrow(() -> new FailedProcessingLargePayloadException(format("Failed processing SQS body to extract S3 details. [ %s ].", sqsMessage.getBody())));

                ResponseInputStream<GetObjectResponse> s3Object = callS3Gracefully(s3Pointer, sqsMessage, failureHandler, pointer -> {
                    ResponseInputStream<GetObjectResponse> response = s3Client().getObject(GetObjectRequest.builder()
                            .bucket(pointer.getS3BucketName())
                            .key(pointer.getS3Key())
                            .build());

                    LOG.debug("Object downloaded with key: " + s3Pointer.getS3Key());
                    return response;
                });

                sqsMessage.setBody(readStringFromS3Object(s3Object, s3Pointer));
                s3Pointers.add(s3Pointer);
            }
        }

        return s3Pointers;
    }

    private static boolean isBodyLargeMessagePointer(String record) {
        return record.startsWith("[\"software.amazon.payloadoffloading.PayloadS3Pointer\"");
    }

    private static String readStringFromS3Object(ResponseInputStream<GetObjectResponse> response,
                                                 PayloadS3Pointer s3Pointer) {
        try (ResponseInputStream<GetObjectResponse> content = response) {
            return IoUtils.toUtf8String(content);
        } catch (IOException e) {
            LOG.error("Error converting S3 object to String", e);
            throw new FailedProcessingLargePayloadException(format("Failed processing S3 record with [Bucket Name: %s Bucket Key: %s]", s3Pointer.getS3BucketName(), s3Pointer.getS3Key()), e);
        }
    }

    public static void deleteMessage(PayloadS3Pointer s3Pointer) {
        deleteMessage(s3Pointer, NoOpExceptionHandler.class);
    }

    public static void deleteMessage(PayloadS3Pointer s3Pointer, Class<? extends MessageExceptionHandler> failureHandler) {
        callS3Gracefully(s3Pointer, failureHandler, pointer -> {
            s3Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(pointer.getS3BucketName())
                    .key(pointer.getS3Key())
                    .build());
            LOG.info("Message deleted from S3: " + s3Pointer.toJson());
            return null;
        });
    }

    private static <R> R callS3Gracefully(final PayloadS3Pointer pointer,
                                          final SQSMessage message,
                                          final Class<? extends MessageExceptionHandler> failureHandler,
                                          final Function<PayloadS3Pointer, R> function) {
        try {
            return function.apply(pointer);
        } catch (S3Exception e) {
            LOG.error("A service exception", e);
            handleFailure(message, failureHandler, e, format("Failed processing S3 record with [Bucket Name: %s Bucket Key: %s]", pointer.getS3BucketName(), pointer.getS3Key()));
        } catch (SdkClientException e) {
            LOG.error("Some sort of client exception", e);
            handleFailure(message, failureHandler, e, format("Failed processing S3 record with [Bucket Name: %s Bucket Key: %s]", pointer.getS3BucketName(), pointer.getS3Key()));
        }
    }

    private static void handleFailure(final SQSMessage message,
                                      final Class<? extends MessageExceptionHandler> failureHandler,
                                      Exception e,
                                      String exceptionMessage) {
        if (null != failureHandler && !failureHandler.isAssignableFrom(NoOpExceptionHandler.class)) {
            MessageExceptionHandler messageExceptionHandler = instantiatedHandler(failureHandler);
            messageExceptionHandler.handle(e, message);
        } else {
            throw new FailedProcessingLargePayloadException(exceptionMessage, e);
        }
    }

    private static MessageExceptionHandler instantiatedHandler(final Class<? extends MessageExceptionHandler> handler) {
        try {
            if (null == handler.getDeclaringClass()) {
                return handler.getDeclaredConstructor().newInstance();
            }

            final Constructor<? extends MessageExceptionHandler> constructor = handler.getDeclaredConstructor(handler.getDeclaringClass());
            constructor.setAccessible(true);
            return constructor.newInstance(handler.getDeclaringClass().getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            LOG.error("Failed creating handler instance", e);
            throw new RuntimeException("Unexpected error occurred. Please raise issue at " +
                    "https://github.com/awslabs/aws-lambda-powertools-java/issues", e);
        }
    }

    public static boolean placedOnSqsEventRequestHandler(ProceedingJoinPoint pjp) {
        return pjp.getArgs().length == 2
                && pjp.getArgs()[0] instanceof SQSEvent
                && pjp.getArgs()[1] instanceof Context;
    }

    public static class FailedProcessingLargePayloadException extends RuntimeException {
        public FailedProcessingLargePayloadException(String message, Throwable cause) {
            super(message, cause);
        }

        public FailedProcessingLargePayloadException(String message) {
            super(message);
        }
    }
}
