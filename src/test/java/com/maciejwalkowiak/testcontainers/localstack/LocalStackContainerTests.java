package com.maciejwalkowiak.testcontainers.localstack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Testcontainers
class LocalStackContainerTests {

    @Container
    private static LocalStackContainer localstack = new LocalStackContainer();

    @Test
    void startsContainer() {
        assertThat(localstack.getAccessKey()).isEqualTo("accesskey");
        assertThat(localstack.getSecretKey()).isEqualTo("secretkey");
        assertThat(localstack.getRegion()).isEqualTo("us-east-1");
        assertThat(localstack.getEndpointOverride()).isNotNull();
    }

    @Test
    void configuresClients() {
        S3Client s3Client = localstack.clients().s3();
        s3Client.createBucket(r -> r.bucket("sync-bucket"));
        assertThat(s3Client.listBuckets().buckets())
                .extracting(Bucket::name)
                .contains("sync-bucket");
    }

    @Test
    void configuresAsyncClients() {
        S3AsyncClient s3Client = localstack.asyncClients().s3();
        s3Client.createBucket(r -> r.bucket("async-bucket")).join();
        assertThat(s3Client.listBuckets().join().buckets())
                .extracting(Bucket::name)
                .contains("async-bucket");
    }

    @Test
    void configuresS3Presigner() {
        S3Presigner s3Presigner = localstack.s3Presigner();
        assertThat(s3Presigner).isNotNull();
    }

}
