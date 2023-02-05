package com.maciejwalkowiak.testcontainers.localstack;

import org.testcontainers.shaded.com.google.common.base.Preconditions;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class SyncClients {
	private final LocalStackContainer localstack;

	public SyncClients(LocalStackContainer localstack) {
		Preconditions.checkNotNull(localstack);
		this.localstack = localstack;
	}

	public S3Client s3() {
		return configure(S3Client.builder()).build();
	}

	public <T extends AwsClientBuilder<?, ?>> T configure(T builder) {
		builder.endpointOverride(localstack.getEndpointOverride())
			.credentialsProvider(localstack.getCredentialsProvider())
			.region(Region.of(localstack.getRegion()));
		return builder;
	}
}
