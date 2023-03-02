package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.cloudformation.AbstractCustomResourceHandler;
import software.amazon.lambda.powertools.cloudformation.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * Handler for requests to Lambda function.
 */

public class App extends AbstractCustomResourceHandler{
    private final static Logger log = LogManager.getLogger(App.class);

    @Override
    protected Response create(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {

        log.info(cloudFormationCustomResourceEvent);
        final String pageContents;
        try {
            pageContents = this.getPageContents("https://checkip.amazonaws.com");
            String output = String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents);
            log.info("Create, {} ", pageContents);
            return Response.success();
        } catch (IOException e) {
            return Response.failed();
        }
    }

    @Override
    protected Response update(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {

        log.info(cloudFormationCustomResourceEvent);
        final String pageContents;
        try {
            pageContents = this.getPageContents("https://checkip.amazonaws.com");
            String output = String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents);
            log.info("Update, {} ", pageContents);
            return Response.success();
        } catch (IOException e) {
            return Response.failed();
        }
    }

    @Override
    protected Response delete(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {

        log.info(cloudFormationCustomResourceEvent);
        final String pageContents;
        try {
            pageContents = this.getPageContents("https://checkip.amazonaws.com");
            String output = String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents);
            log.info("Delete, {} ", pageContents);
            return Response.success();
        } catch (IOException e) {
            return Response.failed();
        }
    }

    private String getPageContents(String address) throws IOException{
        URL url = new URL(address);
        try(BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}