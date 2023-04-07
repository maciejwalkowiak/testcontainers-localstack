package com.maciejwalkowiak.testcontainers.localstack;

import org.testcontainers.shaded.com.google.common.base.Preconditions;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public class SyncClients {
	private final AwsClientConfigurer configurer;

	public SyncClients(AwsClientConfigurer configurer) {
		Preconditions.checkNotNull(configurer);
		this.configurer = configurer;
	}

	public S3Client s3() {
		return configurer.configure(S3Client.builder()).build();
	}

	public SnsClient sns() {
		return configurer.configure(SnsClient.builder()).build();
	}

	public DynamoDbClient dynamoDb() {
		return configurer.configure(DynamoDbClient.builder()).build();
	}

	public SqsClient sqs() {
		return configurer.configure(SqsClient.builder()).build();
	}
}
