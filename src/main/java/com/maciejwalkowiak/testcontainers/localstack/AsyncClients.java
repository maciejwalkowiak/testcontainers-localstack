package com.maciejwalkowiak.testcontainers.localstack;

import java.util.function.Consumer;
import org.testcontainers.shaded.com.google.common.base.Preconditions;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

public class AsyncClients {
	private final AwsClientConfigurer configurer;

	public AsyncClients(AwsClientConfigurer configurer) {
		Preconditions.checkNotNull(configurer);
		this.configurer = configurer;
	}

	public S3AsyncClient s3() {
		return configurer.configure(S3AsyncClient.builder()).build();
	}

	public SnsAsyncClient sns() {
		return configurer.configure(SnsAsyncClient.builder()).build();
	}

	public SqsAsyncClient sqs() {
		return configurer.configure(SqsAsyncClient.builder()).build();
	}

	public SqsAsyncClient sqs(Consumer<SqsAsyncClientBuilder> consumer) {
		SqsAsyncClientBuilder builder = SqsAsyncClient.builder();
		consumer.accept(builder);
		return configurer.configure(builder).build();
	}

	public DynamoDbAsyncClient dynamoDb() {
		return configurer.configure(DynamoDbAsyncClient.builder()).build();
	}
}
