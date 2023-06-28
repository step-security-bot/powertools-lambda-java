package software.amazon.lambda.powertools.batch2;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.lambda.powertools.utilities.EventDeserializer;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

public interface SQSBatchProcessor<ITEM> extends BatchProcessor<SQSEvent, ITEM, SQSBatchResponse> {

    @Override
    default SQSBatchResponse processBatch(SQSEvent event, Context context) {
        Class<ITEM> bodyClass = (Class<ITEM>) ((ParameterizedTypeImpl) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];

        SQSBatchResponse response = new SQSBatchResponse();
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                if (bodyClass.equals(SQSEvent.SQSMessage.class)) {
                    processItem(message, context);
                } else {
                    processItem(EventDeserializer.extractDataFrom(message).as(bodyClass), context);
                }
            } catch (Throwable t) {
                response.getBatchItemFailures().add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }
        return response;
    }

    default void processItem(SQSEvent.SQSMessage message, Context context) {
        System.out.println("Processing message " + message.getMessageId());
    }

}