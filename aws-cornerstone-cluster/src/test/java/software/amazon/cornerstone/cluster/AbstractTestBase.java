package software.amazon.cornerstone.cluster;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.awssdk.services.cornerstone.model.Cluster;
import software.amazon.awssdk.services.cornerstone.model.Node;
import software.amazon.awssdk.services.cornerstone.model.SecurityGroupMembership;
import software.amazon.awssdk.services.cornerstone.model.Shard;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
    protected static final Credentials MOCK_CREDENTIALS;
    protected static final AwsCredentialsProvider credentialsProvider;
    protected static final LoggerProxy logger;

    protected static final String CLUSTER_NAME;
    protected static final String CLUSTER_DESCRIPTION;
    protected static final String CLUSTER_STATUS;
    protected static final String NODE_TYPE;
    protected static final Integer NUM_SHARDS;
    protected static final Integer NUM_REPLICAS_PER_SHARD;
    protected static final String SUBNET_GROUP_NAME;
    protected static final List<String> SECURITY_GROUP_IDS;
    protected static final Integer PORT;
    protected static final String SNS_TOPIC_ARN;
    protected static final Boolean TLS_ENABLED;
    protected static final String CLUSTER_ARN;
    protected static final String ENGINE_VERSION;
    protected static final String AVAILABILITY_MODE;
    protected static final String ENDPOINT_ADDRESS;
    protected static final String AVAILABILITY_ZONE;
    protected static final String CREATING_STATUS;

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
        CLUSTER_NAME = "test-cornerstone-cluster";
        CLUSTER_DESCRIPTION = "unit test cluster";
        CLUSTER_STATUS = "available";
        NODE_TYPE = "db.r6g.large";
        NUM_SHARDS = 2;
        NUM_REPLICAS_PER_SHARD = 1;
        SUBNET_GROUP_NAME = "testSubnetGroup";
        SECURITY_GROUP_IDS = Lists.newArrayList("sgId1", "sgId2");
        PORT = 8000;
        SNS_TOPIC_ARN = "test-sns-topic-arn";
        TLS_ENABLED = true;
        CLUSTER_ARN = "test-cluster-arn";
        ENGINE_VERSION = "test-engine-version";
        AVAILABILITY_MODE = "MultiAZ";
        ENDPOINT_ADDRESS = "cornerstonecfg.test-cornerstone-cluster.amazonaws.com";
        AVAILABILITY_ZONE = "us-east-1a";
        CREATING_STATUS = "creating";
        AwsSessionCredentials awsSessionCredentials =
                AwsSessionCredentials.create(MOCK_CREDENTIALS.getAccessKeyId(), MOCK_CREDENTIALS.getSecretAccessKey(), MOCK_CREDENTIALS.getSessionToken());
        credentialsProvider = StaticCredentialsProvider.create(awsSessionCredentials);
    }

    @SuppressWarnings("unchecked")
    protected <T extends AwsRequest> T injectCredentials(T request) {
        AwsRequestOverrideConfiguration overrideConfiguration = AwsRequestOverrideConfiguration.builder().credentialsProvider(credentialsProvider).build();
        return (T) request.toBuilder().overrideConfiguration(overrideConfiguration).build();
    }

    static ProxyClient<CornerstoneClient> MOCK_PROXY(final AmazonWebServicesClientProxy proxy,
                                                     final CornerstoneClient sdkClient) {
        return new ProxyClient<CornerstoneClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT injectCredentialsAndInvokeV2(RequestT request,
                                                                                                                       Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> CompletableFuture<ResponseT> injectCredentialsAndInvokeV2Async(RequestT request,
                                                                                                                                               Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>> IterableT injectCredentialsAndInvokeIterableV2(RequestT request,
                                                                                                                                                                         Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT> injectCredentialsAndInvokeV2InputStream(RequestT requestT,
                                                                                                                                                       Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT> injectCredentialsAndInvokeV2Bytes(RequestT requestT,
                                                                                                                                           Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CornerstoneClient client() {
                return sdkClient;
            }
        };
    }

    static List<SecurityGroupMembership> getSecurityGroupMemberships(List<String> securityGroupIds) {
        return securityGroupIds.stream().map(sgId -> SecurityGroupMembership.builder().securityGroupId(sgId).status("ACTIVE").build())
                               .collect(Collectors.toList());
    }

    static List<software.amazon.awssdk.services.cornerstone.model.Shard> getShards(int numShards,
                                                                                   int numReplica) {
        return IntStream.rangeClosed(1, numShards).mapToObj(
                i -> Shard.builder().name("000" + i).status("ACTIVE").numNodes(numReplica + 1).slots("slot" + i).nodes(getNodes(numReplica)).build())
                        .collect(Collectors.toList());
    }

    static List<software.amazon.awssdk.services.cornerstone.model.Node> getNodes(int numReplica) {
        return IntStream.rangeClosed(1, numReplica)
                        .mapToObj(i -> Node.builder().name("000" + i).status("ACTIVE").availabilityZone(AVAILABILITY_ZONE).createTime(Instant.now()).build())
                        .collect(Collectors.toList());
    }

    static Cluster getTestCluster() {
        return Cluster.builder().name(CLUSTER_NAME).description(CLUSTER_DESCRIPTION).status(CLUSTER_STATUS).nodeType(NODE_TYPE).numShards(NUM_SHARDS)
                      .subnetGroupName(SUBNET_GROUP_NAME).securityGroups(getSecurityGroupMemberships(SECURITY_GROUP_IDS)).snsTopicArn(SNS_TOPIC_ARN)
                      .tlsEnabled(TLS_ENABLED).arn(CLUSTER_ARN).engineVersion(ENGINE_VERSION)
                      .clusterEndpoint(software.amazon.awssdk.services.cornerstone.model.Endpoint.builder().address(ENDPOINT_ADDRESS).port(PORT).build())
                      .availabilityMode(AVAILABILITY_MODE).shards(getShards(NUM_SHARDS, NUM_REPLICAS_PER_SHARD)).build();
    }

    static ResourceModel getResourceModel(final Cluster cluster) {
        final int numReplica = cluster.shards().stream().mapToInt(Shard::numNodes).min().orElse(1) - 1;
        return ResourceModel.builder().name(cluster.name()).description(cluster.description()).status(cluster.status()).nodeType(cluster.nodeType())
                            .numShards(cluster.numShards()).numReplicasPerShard(numReplica).subnetGroupName(cluster.subnetGroupName())
                            .securityGroupIds(getSecurityGroupIds(cluster)).port(cluster.clusterEndpoint().port()).snsTopicArn(cluster.snsTopicArn())
                            .tLSEnabled(cluster.tlsEnabled()).aRN(cluster.arn()).engineVersion(cluster.engineVersion())
                            .clusterEndpoint(Translator.translateEndpoint(cluster)).availabilityMode(cluster.availabilityModeAsString())
                            .shards(Translator.translateShards(cluster)).build();
    }

    static ResourceModel getDesiredTestResourceModel() {
        return ResourceModel.builder().name(CLUSTER_NAME).description(CLUSTER_DESCRIPTION).nodeType(NODE_TYPE).numShards(NUM_SHARDS)
                            .numReplicasPerShard(NUM_REPLICAS_PER_SHARD).subnetGroupName(SUBNET_GROUP_NAME).securityGroupIds(SECURITY_GROUP_IDS).port(PORT)
                            .snsTopicArn(SNS_TOPIC_ARN).tLSEnabled(TLS_ENABLED).build();
    }

    static List<String> getSecurityGroupIds(final Cluster cluster) {
        return cluster.securityGroups().stream().map(SecurityGroupMembership::securityGroupId).collect(Collectors.toList());
    }

}
