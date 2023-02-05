package com.maciejwalkowiak.testcontainers.localstack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class LocalStackContainerTests {

    @Test
    void startsContainer() {
        LocalStackContainer localstack = new LocalStackContainer();
        localstack.start();

        assertThat(localstack.getAccessKey()).isEqualTo("accesskey");
        assertThat(localstack.getSecretKey()).isEqualTo("secretkey");
        assertThat(localstack.getRegion()).isEqualTo("us-east-1");
        assertThat(localstack.getEndpointOverride()).isNotNull();

        localstack.stop();
    }

}
