package software.amazon.memorydb.cluster;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.Node;
import software.amazon.awssdk.services.memorydb.model.SecurityGroupMembership;
import software.amazon.awssdk.services.memorydb.model.Shard;
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
    protected static final String SNS_TOPIC_STATUS;
    protected static final Boolean TLS_ENABLED;
    protected static final String CLUSTER_ARN;
    protected static final String ENGINE_VERSION;
    protected static final String AVAILABILITY_MODE;
    protected static final String ENDPOINT_ADDRESS;
    protected static final Integer ENDPOINT_PORT;
    protected static final String AVAILABILITY_ZONE;
    protected static final String CREATING_STATUS;
    protected static final String MAINTENANCE_WINDOW;
    protected static final String SNAPSHOT_WINDOW;
    protected static final Integer SNAPSHOT_RETENTION_LIMIT;
    protected static final String ACL_NAME;
    protected static final Set<Tag> TAG_SET;

    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
        CLUSTER_NAME = "test-memorydb-cluster";
        CLUSTER_DESCRIPTION = "unit test cluster";
        CLUSTER_STATUS = "available";
        NODE_TYPE = "db.r6g.large";
        NUM_SHARDS = 2;
        NUM_REPLICAS_PER_SHARD = 1;
        SUBNET_GROUP_NAME = "testSubnetGroup";
        SECURITY_GROUP_IDS = Lists.newArrayList("sgId1", "sgId2");
        PORT = 8000;
        SNS_TOPIC_ARN = "test-sns-topic-arn";
        SNS_TOPIC_STATUS = "disabled";
        TLS_ENABLED = true;
        CLUSTER_ARN = "test-cluster-arn";
        ENGINE_VERSION = "test-engine-version";
        AVAILABILITY_MODE = "MultiAZ";
        ENDPOINT_ADDRESS = "memorydbcfg.test-memorydb-cluster.amazonaws.com";
        ENDPOINT_PORT = 6379;
        AVAILABILITY_ZONE = "us-east-1a";
        CREATING_STATUS = "creating";
        MAINTENANCE_WINDOW = "03:00–11:00 UTC";
        SNAPSHOT_WINDOW = "09:00-10:00";
        SNAPSHOT_RETENTION_LIMIT = 0;
        ACL_NAME = "open-access";
        TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());
        AwsSessionCredentials awsSessionCredentials =
                AwsSessionCredentials.create(MOCK_CREDENTIALS.getAccessKeyId(), MOCK_CREDENTIALS.getSecretAccessKey(), MOCK_CREDENTIALS.getSessionToken());
        credentialsProvider = StaticCredentialsProvider.create(awsSessionCredentials);
    }

    @SuppressWarnings("unchecked")
    protected <T extends AwsRequest> T injectCredentials(T request) {
        AwsRequestOverrideConfiguration overrideConfiguration = AwsRequestOverrideConfiguration.builder().credentialsProvider(credentialsProvider).build();
        return (T) request.toBuilder().overrideConfiguration(overrideConfiguration).build();
    }

    static ProxyClient<MemoryDbClient> MOCK_PROXY(final AmazonWebServicesClientProxy proxy,
                                                     final MemoryDbClient sdkClient) {
        return new ProxyClient<MemoryDbClient>() {
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
            public MemoryDbClient client() {
                return sdkClient;
            }
        };
    }

    static List<SecurityGroupMembership> getSecurityGroupMemberships(List<String> securityGroupIds) {
        return securityGroupIds.stream().map(sgId -> SecurityGroupMembership.builder().securityGroupId(sgId).status("ACTIVE").build())
                               .collect(Collectors.toList());
    }

    static List<software.amazon.awssdk.services.memorydb.model.Shard> getShards(int numShards,
                                                                                   int numReplica) {
        return IntStream.rangeClosed(1, numShards).mapToObj(
                i -> Shard.builder().name("000" + i).status("ACTIVE").numberOfNodes(numReplica + 1).slots("slot" + i).nodes(getNodes(numReplica)).build())
                        .collect(Collectors.toList());
    }

    static List<software.amazon.awssdk.services.memorydb.model.Node> getNodes(int numReplica) {
        return IntStream.rangeClosed(1, numReplica)
                        .mapToObj(i -> Node.builder().name("000" + i).status("ACTIVE").availabilityZone(AVAILABILITY_ZONE).createTime(Instant.now()).build())
                        .collect(Collectors.toList());
    }

    static Cluster getTestCluster(ResourceModel model) {
        return Cluster.builder().name(model.getClusterName()).description(model.getDescription()).status(model.getStatus()).nodeType(model.getNodeType())
                .maintenanceWindow(model.getMaintenanceWindow()).snapshotWindow(model.getSnapshotWindow()).snapshotRetentionLimit(model.getSnapshotRetentionLimit()).aclName(model.getACLName())
                .numberOfShards(model.getNumShards()).subnetGroupName(model.getSubnetGroupName()).securityGroups(getSecurityGroupMemberships(model.getSecurityGroupIds()))
                .snsTopicArn(model.getSnsTopicArn()).snsTopicStatus(model.getSnsTopicStatus()).tlsEnabled(model.getTLSEnabled()).arn(model.getARN()).engineVersion(model.getEngineVersion())
                .clusterEndpoint(software.amazon.awssdk.services.memorydb.model.Endpoint.builder().address(model.getClusterEndpoint().getAddress()).port(model.getClusterEndpoint().getPort()).build())
                .shards(getShards(model.getNumShards(), model.getNumReplicasPerShard())).parameterGroupName(model.getParameterGroupName()).parameterGroupStatus(model.getParameterGroupStatus())
                .status(model.getStatus())
                .build();
    }
    static Cluster getTestCluster() {
        return Cluster.builder().name(CLUSTER_NAME).description(CLUSTER_DESCRIPTION).status(CLUSTER_STATUS).nodeType(NODE_TYPE).numberOfShards(NUM_SHARDS)
                      .subnetGroupName(SUBNET_GROUP_NAME).securityGroups(getSecurityGroupMemberships(SECURITY_GROUP_IDS)).snsTopicArn(SNS_TOPIC_ARN).snsTopicStatus(SNS_TOPIC_STATUS)
                      .tlsEnabled(TLS_ENABLED).arn(CLUSTER_ARN).engineVersion(ENGINE_VERSION).aclName(ACL_NAME)
                      .maintenanceWindow(MAINTENANCE_WINDOW).snapshotWindow(SNAPSHOT_WINDOW).snapshotRetentionLimit(SNAPSHOT_RETENTION_LIMIT)
                      .clusterEndpoint(software.amazon.awssdk.services.memorydb.model.Endpoint.builder().address(ENDPOINT_ADDRESS).port(PORT).build())
                      .availabilityMode(AVAILABILITY_MODE).shards(getShards(NUM_SHARDS, NUM_REPLICAS_PER_SHARD)).build();
    }

    static ResourceModel getResourceModel(final Cluster cluster) {
        final int numReplica = cluster.shards().stream().mapToInt(Shard::numberOfNodes).min().orElse(1) - 1;

        ResourceModel.ResourceModelBuilder builder =  ResourceModel.builder();

        builder.clusterName(cluster.name())
                .description(cluster.description())
                .maintenanceWindow(cluster.maintenanceWindow())
                .status(cluster.status())
                .nodeType(cluster.nodeType())
                .numShards(cluster.numberOfShards())
                .numReplicasPerShard(numReplica)
                .subnetGroupName(cluster.subnetGroupName())
                .securityGroupIds(getSecurityGroupIds(cluster))
                .port(cluster.clusterEndpoint().port())
                .snsTopicArn(cluster.snsTopicArn())
                .snsTopicStatus(cluster.snsTopicStatus())
                .tLSEnabled(cluster.tlsEnabled())
                .aRN(cluster.arn())
                .engineVersion(cluster.engineVersion())
                .aCLName(cluster.aclName())
                .clusterEndpoint(Translator.translateEndpoint(cluster))
                .snapshotRetentionLimit(cluster.snapshotRetentionLimit())
                .snapshotWindow(cluster.snapshotWindow());
        return builder.build();
    }

    static ResourceModel getDesiredTestResourceModel() {
        return ResourceModel.builder().clusterName(CLUSTER_NAME).description(CLUSTER_DESCRIPTION).nodeType(NODE_TYPE).numShards(NUM_SHARDS)
                            .numReplicasPerShard(NUM_REPLICAS_PER_SHARD).subnetGroupName(SUBNET_GROUP_NAME).securityGroupIds(SECURITY_GROUP_IDS).port(PORT)
                            .clusterEndpoint(Endpoint.builder().address(ENDPOINT_ADDRESS).port(ENDPOINT_PORT).build()).maintenanceWindow(MAINTENANCE_WINDOW)
                            .snsTopicArn(SNS_TOPIC_ARN).snsTopicStatus(SNS_TOPIC_STATUS).tLSEnabled(TLS_ENABLED).aCLName(ACL_NAME).build();
    }

    static List<String> getSecurityGroupIds(final Cluster cluster) {
        return cluster.securityGroups().stream().map(SecurityGroupMembership::securityGroupId).collect(Collectors.toList());
    }

    static Map<String, String> translateTagsToMap(final Set<Tag> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

    }

    static Set<software.amazon.awssdk.services.memorydb.model.Tag> translateTagsToSdk(final Set<Tag> tags) {
        return tags.stream()
                .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder()
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toSet());
    }

}
