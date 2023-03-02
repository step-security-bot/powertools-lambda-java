package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Map.entry;
import static software.amazon.awscdk.BundlingOutput.ARCHIVED;

public class PowertoolsExamplesCloudformationCdkStack extends Stack {
    public PowertoolsExamplesCloudformationCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public PowertoolsExamplesCloudformationCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);


        List<String> functionPackagingInstructions = Arrays.asList(
                "/bin/sh",
                "-c",
                "mvn clean install" +
                        "&& cp target/powertools-examples-cloudformation-*.jar  /asset-output/"
        );
        BundlingOptions bundlingOptions = BundlingOptions.builder()
                .command(functionPackagingInstructions)
                .image(Runtime.JAVA_11.getBundlingImage())
                .volumes(singletonList(
                        // Mount local .m2 repo to avoid download all the dependencies again inside the container
                        DockerVolume.builder()
                                .hostPath(System.getProperty("user.home") + "/.m2/")
                                .containerPath("/root/.m2/")
                                .build()
                ))
                .user("root")
                .outputType(ARCHIVED)
                .build();


        Function helloWorldFunction = new Function(this, "HelloWorldFunction", FunctionProps.builder()
                .runtime(Runtime.JAVA_11)
                .code(Code.fromAsset("../../", AssetOptions.builder().bundling(bundlingOptions)
                        .build()))
                .handler("helloworld.App")
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .environment(Map.ofEntries(entry("JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1")))
                .build());

        CustomResource.Builder.create(this, "HelloWorldCustomResource").serviceToken(helloWorldFunction.getFunctionArn()).build();

    }
}
