package com.maciejwalkowiak.testcontainers.localstack;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

public class AsyncClients {
	private final LocalStackContainer localstack;

	public AsyncClients(LocalStackContainer localstack) {
		this.localstack = localstack;
	}

	public S3AsyncClient s3() {
		return configure(S3AsyncClient.builder()).build();
	}

	public <T extends AwsClientBuilder<?, ?>> T configure(T builder) {
		builder.endpointOverride(localstack.getEndpointOverride())
			.credentialsProvider(localstack.getCredentialsProvider())
			.region(Region.of(localstack.getRegion()));
		return builder;
	}
}
