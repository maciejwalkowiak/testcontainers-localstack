package com.maciejwalkowiak.testcontainers.localstack;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class LocalStackContainer extends GenericContainer<LocalStackContainer> {

    static final int PORT = 4566;

    private static final String HOSTNAME_EXTERNAL_ENV_VAR = "HOSTNAME_EXTERNAL";

    private final List<LocalStackContainer.EnabledService> services = new ArrayList<>();

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("localstack/localstack");

    private static final String DEFAULT_TAG = "1.3.1";

    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * @deprecated use {@link LocalStackContainer (DockerImageName)} instead
     */
    public LocalStackContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    /**
     * @deprecated use {@link LocalStackContainer (DockerImageName)} instead
     */
    @Deprecated
    public LocalStackContainer(String version) {
        this(DEFAULT_IMAGE_NAME.withTag(version));
    }

    /**
     * @param dockerImageName    image name to use for Localstack
     */
    public LocalStackContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        withFileSystemBind(DockerClientFactory.instance().getRemoteDockerUnixSocketPath(), "/var/run/docker.sock");
        waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));
    }

    @Override
    protected void configure() {
        super.configure();

        if (!services.isEmpty()) {
            withEnv("SERVICES", services.stream().map(LocalStackContainer.EnabledService::getName).collect(
                    Collectors.joining(",")));
        }

        String hostnameExternalReason;
        if (getEnvMap().containsKey(HOSTNAME_EXTERNAL_ENV_VAR)) {
            // do nothing
            hostnameExternalReason = "explicitly as environment variable";
        } else if (getNetwork() != null && getNetworkAliases() != null && getNetworkAliases().size() >= 1) {
            withEnv(HOSTNAME_EXTERNAL_ENV_VAR, getNetworkAliases().get(getNetworkAliases().size() - 1)); // use the last network alias set
            hostnameExternalReason = "to match last network alias on container with non-default network";
        } else {
            withEnv(HOSTNAME_EXTERNAL_ENV_VAR, getHost());
            hostnameExternalReason = "to match host-routable address for container";
        }
        logger()
                .info(
                        "{} environment variable set to {} ({})",
                        HOSTNAME_EXTERNAL_ENV_VAR,
                        getEnvMap().get(HOSTNAME_EXTERNAL_ENV_VAR),
                        hostnameExternalReason
                );

        exposePorts();
    }

    private void exposePorts() {
        this.addExposedPort(PORT);
    }

    public LocalStackContainer withServices(
            LocalStackContainer.Service... services) {
        this.services.addAll(Arrays.asList(services));
        return self();
    }

    /**
     * Declare a set of simulated AWS services that should be launched by this container.
     * @param services one or more service names
     * @return this container object
     */
    public LocalStackContainer withServices(
            LocalStackContainer.EnabledService... services) {
        this.services.addAll(Arrays.asList(services));
        return self();
    }

    /**
     * Provides an endpoint override that is preconfigured to communicate with a given simulated service.
     * The provided endpoint override should be set in the AWS Java SDK v2 when building a client, e.g.:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * <p><strong>Please note that this method is only intended to be used for configuring AWS SDK clients
     * that are running on the test host. If other containers need to call this one, they should be configured
     * specifically to do so using a Docker network and appropriate addressing.</strong></p>
     *
     * @return an {@link URI} endpoint override
     */
    public URI getEndpointOverride() {
        try {
            final String address = getHost();
            String ipAddress = address;
            // resolve IP address and use that as the endpoint so that path-style access is automatically used for S3
            ipAddress = InetAddress.getByName(address).getHostAddress();
            return new URI("http://" + ipAddress + ":" + getMappedPort(PORT));
        } catch (UnknownHostException | URISyntaxException e) {
            throw new IllegalStateException("Cannot obtain endpoint URL", e);
        }
    }

    /**
     * Provides a default access key that is preconfigured to communicate with a given simulated service.
     * The access key can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default access key
     */
    public String getAccessKey() {
        return "accesskey";
    }

    /**
     * Provides a default secret key that is preconfigured to communicate with a given simulated service.
     * The secret key can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default secret key
     */
    public String getSecretKey() {
        return "secretkey";
    }

    /**
     * Provides a default region that is preconfigured to communicate with a given simulated service.
     * The region can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default region
     */
    public String getRegion() {
        return this.getEnvMap().getOrDefault("DEFAULT_REGION", DEFAULT_REGION);
    }

    public interface EnabledService {
        static LocalStackContainer.EnabledService named(String name) {
            return () -> name;
        }

        String getName();
    }

    public enum Service implements LocalStackContainer.EnabledService {
        API_GATEWAY("apigateway"),
        EC2("ec2"),
        KINESIS("kinesis"),
        DYNAMODB("dynamodb"),
        DYNAMODB_STREAMS("dynamodbstreams"),
        // TODO: Clarify usage for ELASTICSEARCH and ELASTICSEARCH_SERVICE
        //        ELASTICSEARCH("es",           4571),
        S3("s3"),
        FIREHOSE("firehose"),
        LAMBDA("lambda"),
        SNS("sns"),
        SQS("sqs"),
        REDSHIFT("redshift"),
        SES("ses"),
        ROUTE53("route53"),
        CLOUDFORMATION("cloudformation"),
        CLOUDWATCH("cloudwatch"),
        SSM("ssm"),
        SECRETSMANAGER("secretsmanager"),
        STEPFUNCTIONS("stepfunctions"),
        CLOUDWATCHLOGS("logs"),
        STS("sts"),
        IAM("iam"),
        KMS("kms");

        private final String localStackName;

        Service(String localStackName) {
            this.localStackName = localStackName;
        }

        @Override
        public String getName() {
            return localStackName;
        }
    }
}
