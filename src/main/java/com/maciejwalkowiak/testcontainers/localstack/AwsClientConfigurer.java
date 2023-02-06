package com.maciejwalkowiak.testcontainers.localstack;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

class AwsClientConfigurer {
    private final LocalStackContainer localstack;

    AwsClientConfigurer(LocalStackContainer localstack) {
        this.localstack = localstack;
    }

    <T extends AwsClientBuilder<?, ?>> T configure(T builder) {
        builder.endpointOverride(localstack.getEndpointOverride())
                .credentialsProvider(localstack.getCredentialsProvider())
                .region(Region.of(localstack.getRegion()));
        return builder;
    }
}
