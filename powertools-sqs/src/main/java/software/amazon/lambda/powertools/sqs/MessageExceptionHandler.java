package software.amazon.lambda.powertools.sqs;

import software.amazon.payloadoffloading.PayloadS3Pointer;

import static com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

public interface MessageExceptionHandler {

    void handleGetObjectFailure(Exception e, SQSMessage message);

    void handleDeleteObjectFailure(Exception e, PayloadS3Pointer pointer);

    class NoOpExceptionHandler implements MessageExceptionHandler {
        @Override
        public void handle(Exception e, SQSMessage message) {

        }
    }
}
