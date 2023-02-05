package com.maciejwalkowiak.testcontainers.localstack;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.rnorth.ducttape.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

public class LocalStackContainer extends GenericContainer<LocalStackContainer> {
    private static final Logger log = LoggerFactory.getLogger(LocalStackContainer.class);

    static final int PORT = 4566;

    private static final String HOSTNAME_EXTERNAL_ENV_VAR = "HOSTNAME_EXTERNAL";

    private final List<LocalStackContainer.EnabledService> services = new ArrayList<>();

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("localstack/localstack");

    private static final String DEFAULT_TAG = "0.11.2";

    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * Whether or to assume that all APIs run on different ports (when <code>true</code>) or are
     * exposed on a single port (<code>false</code>). From the Localstack README:
     *
     * <blockquote>Note: Starting with version 0.11.0, all APIs are exposed via a single edge
     * service [...] The API-specific endpoints below are still left for backward-compatibility but
     * may get removed in a future release - please reconfigure your client SDKs to start using the
     * single edge endpoint URL!</blockquote>
     * <p>
     * Testcontainers will use the tag of the docker image to infer whether or not the used version
     * of Localstack supports this feature.
     */
    private final boolean legacyMode;

    /**
     * @deprecated use {@link LocalStackContainer (DockerImageName)} instead
     */
    @Deprecated
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
        this(dockerImageName, shouldRunInLegacyMode(dockerImageName.getVersionPart()));
    }

    /**
     * @param dockerImageName    image name to use for Localstack
     * @param useLegacyMode      if true, each AWS service is exposed on a different port
     */
    public LocalStackContainer(final DockerImageName dockerImageName, boolean useLegacyMode) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        this.legacyMode = useLegacyMode;

        withFileSystemBind(DockerClientFactory.instance().getRemoteDockerUnixSocketPath(), "/var/run/docker.sock");
        waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));
    }

    private static boolean shouldRunInLegacyMode(String version) {
        if (version.equals("latest")) {
            return false;
        }

        ComparableVersion comparableVersion = new ComparableVersion(version);
        if (comparableVersion.isSemanticVersion()) {
            boolean versionRequiresLegacyMode = comparableVersion.isLessThan("0.11");
            return versionRequiresLegacyMode;
        }

        log.warn("Version {} is not a semantic version, LocalStack will run in legacy mode.", version);
        log.warn(
                "Consider using \"LocalStackContainer(DockerImageName dockerImageName, boolean legacyMode)\" constructor if you want to disable legacy mode."
        );
        return true;
    }

    @Override
    protected void configure() {
        super.configure();

        Preconditions.check("services list must not be empty", !services.isEmpty());

        withEnv("SERVICES", services.stream().map(LocalStackContainer.EnabledService::getName).collect(
                Collectors.joining(",")));

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
        services.stream().map(this::getServicePort).distinct().forEach(this::addExposedPort);
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

    public URI getEndpointOverride(LocalStackContainer.Service service) {
        return getEndpointOverride((LocalStackContainer.EnabledService) service);
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
     * @param service the service that is to be accessed
     * @return an {@link URI} endpoint override
     */
    public URI getEndpointOverride(LocalStackContainer.EnabledService service) {
        try {
            final String address = getHost();
            String ipAddress = address;
            // resolve IP address and use that as the endpoint so that path-style access is automatically used for S3
            ipAddress = InetAddress.getByName(address).getHostAddress();
            return new URI("http://" + ipAddress + ":" + getMappedPort(getServicePort(service)));
        } catch (UnknownHostException | URISyntaxException e) {
            throw new IllegalStateException("Cannot obtain endpoint URL", e);
        }
    }

    private int getServicePort(LocalStackContainer.EnabledService service) {
        return legacyMode ? service.getPort() : PORT;
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

        default int getPort() {
            return PORT;
        }
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
