package software.amazon.memorydb.cluster;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.MemoryDbRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.awssdk.services.memorydb.model.ReplicaConfigurationRequest;
import software.amazon.awssdk.services.memorydb.model.ShardConfigurationRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UpdateClusterRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateClusterResponse;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<MemoryDbClient> proxyClient;

    @Mock
    MemoryDbClient sdkClient;

    private UpdateHandler handler;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(MemoryDbClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
        handler = new UpdateHandler();
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {

        final Cluster cluster = getTestCluster();
        final ResourceModel currentTestResourceModel = getDesiredTestResourceModel();
        final ResourceModel desiredTestResourceModel = currentTestResourceModel;
        final ResourceModel expectedResourceModel = getResourceModel(cluster);
        expectedResourceModel.setTags(TAG_SET);
        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder().clusters(cluster).nextToken(null).build();
        when(proxyClient.client().describeClusters(any(DescribeClustersRequest.class))).thenReturn(describeClustersResponse);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder().tagList(translateTagsToSdk(TAG_SET)).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);

        final ResourceHandlerRequest<ResourceModel> request = buildRequest(desiredTestResourceModel, currentTestResourceModel);
        request.setDesiredResourceTags(translateTagsToMap(TAG_SET));
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expectedResourceModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxyClient.client()).describeClusters(any(DescribeClustersRequest.class));
        verify(proxyClient.client(), times(1)).listTags(any(ListTagsRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequestUpdateCluster() {
        for(ClusterUpdateFieldType fieldType : ClusterUpdateFieldType.values()) {
            handleRequestTest(fieldType);
        }
        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_ClusterNotFound() {
        final ResourceModel previousTestResourceModel = getDesiredTestResourceModel();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        desiredTestResourceModel.setNumShards(5);
        desiredTestResourceModel.setStatus("available");
        doThrow(ClusterNotFoundException.class).when(proxyClient.client()).updateCluster(any(UpdateClusterRequest.class));

        final ResourceHandlerRequest<ResourceModel> request =
                buildRequest(desiredTestResourceModel, previousTestResourceModel);

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            assertThat(e.getCause() instanceof ClusterNotFoundException).isTrue();
        }

        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_ParameterGroupNotFound() {
        final ResourceModel previousTestResourceModel = getDesiredTestResourceModel();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();

        desiredTestResourceModel.setParameterGroupName("redis_8.0");
        desiredTestResourceModel.setStatus("available");

        doThrow(ParameterGroupNotFoundException.class).when(proxyClient.client()).updateCluster(any(UpdateClusterRequest.class));

        final ResourceHandlerRequest<ResourceModel> request =
                buildRequest(desiredTestResourceModel, previousTestResourceModel);

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            assertThat(e.getCause() instanceof ParameterGroupNotFoundException).isTrue();
        }

        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_UpdateClusterUpdateTags(){
        final ResourceModel previousTestResourceModel = getDesiredTestResourceModel();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        desiredTestResourceModel.setARN(CLUSTER_ARN);
        final ResourceHandlerRequest<ResourceModel> request =
                buildRequest(desiredTestResourceModel, previousTestResourceModel);
        Set<Tag> newTags = Sets.newSet(Tag.builder().key("key").value("newValue").build(),
                Tag.builder().key("keyNew").value("value").build());
        Set<Tag> oldTags = Sets.newSet(
                Tag.builder().key("key").value("oldValue").build(),
                Tag.builder().key("keyOld").value("value").build());

        request.setPreviousResourceTags(translateTagsToMap(oldTags));
        request.setDesiredResourceTags(translateTagsToMap(newTags));
        request.getDesiredResourceState().setTags(newTags);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tagList(translateTagsToSdk(oldTags)).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();
        when(sdkClient.untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().tagList(translateTagsToSdk(request.getDesiredResourceState().getTags())).build();
        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.tagResource(proxy, proxyClient, ProgressEvent.progress(desiredTestResourceModel, new CallbackContext()), request, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));
        verify(proxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(proxyClient.client()).tagResource(any(TagResourceRequest.class));

        verify(sdkClient, atLeastOnce()).serviceName();
    }


    private void handleRequestTest(ClusterUpdateFieldType fieldType) {
        final ResourceModel desiredModel =  getDesiredTestResourceModel();
        final UpdateClusterRequest.Builder expectedRequestBuilder =
                UpdateClusterRequest.builder()
                        .clusterName(CLUSTER_NAME);
        desiredModel.setStatus("available");

        UpdateClusterRequest expectedRequest = null;
        final ArgumentCaptor<UpdateClusterRequest> captor =
                ArgumentCaptor.forClass(UpdateClusterRequest.class);

        switch (fieldType) {
            case DESCRIPTION:
                desiredModel.setDescription("New description");
                expectedRequestBuilder.description("New description");
                break;
            case SECURITY_GROUP_IDS:
                List<String> sgIds = Lists.newArrayList("sgId3", "sgId4");
                desiredModel.setSecurityGroupIds(sgIds);
                expectedRequestBuilder.securityGroupIds(sgIds);
                break;
            case MAINTENANCE_WINDOW:
                desiredModel.setMaintenanceWindow("05:00–13:00 UTC");
                expectedRequestBuilder.maintenanceWindow("05:00–13:00 UTC");
                break;
            case SNS_TOPIC_ARN:
                desiredModel.setSnsTopicArn("test-sns-topic-arn-updated");
                expectedRequestBuilder.snsTopicArn("test-sns-topic-arn-updated");
                expectedRequestBuilder.snsTopicStatus(desiredModel.getSnsTopicStatus());
                break;
            case SNS_TOPIC_STATUS:
                desiredModel.setSnsTopicStatus("enabled");
                expectedRequestBuilder.snsTopicStatus("enabled");
                break;
            case PARAMETER_GROUP_NAME:
                desiredModel.setParameterGroupName("test-pg-updated");
                expectedRequestBuilder.parameterGroupName("test-pg-updated");
                break;
            case SNAPSHOT_WINDOW:
                desiredModel.setSnapshotWindow("05:00–13:00 UTC");
                expectedRequestBuilder.snapshotWindow("05:00–13:00 UTC");
                break;
            case SNAPSHOT_RETENTION_LIMIT:
                desiredModel.setSnapshotRetentionLimit(100);
                expectedRequestBuilder.snapshotRetentionLimit(100);
                break;
            case NODE_TYPE:
                desiredModel.setNodeType("db.r6g.xlarge");
                expectedRequestBuilder.nodeType("db.r6g.xlarge");
                break;
            case ENGINE_VERSION:
                desiredModel.setEngineVersion("6.2");
                expectedRequestBuilder.engineVersion("6.2");
                break;
            case REPLICA_CONFIGURATION:
                desiredModel.setNumReplicasPerShard(5);
                expectedRequestBuilder.replicaConfiguration(ReplicaConfigurationRequest.builder().replicaCount(5).build());
                break;
            case SHARD_CONFIGURATION:
                desiredModel.setNumShards(5);
                expectedRequestBuilder.shardConfiguration(ShardConfigurationRequest.builder().shardCount(5).build());
                break;
            case ACL_NAME:
                desiredModel.setACLName("test-acl");
                expectedRequestBuilder.aclName("test-acl");
                break;
            default:
                break;
        }

        final Cluster desiredCluster = getTestCluster(desiredModel);
        final DescribeClustersResponse finalResponse =
                DescribeClustersResponse
                        .builder()
                        .clusters(desiredCluster).build();
        when(proxyClient.client().updateCluster(captor.capture()))
                .thenReturn(UpdateClusterResponse.builder().build());
        when(proxyClient.client().describeClusters(any(DescribeClustersRequest.class)))
                .thenReturn(finalResponse);

        expectedRequest = expectedRequestBuilder.build();

        ProgressEvent<ResourceModel, CallbackContext> progress = ProgressEvent
                .progress(desiredModel, new CallbackContext());

        final ProgressEvent<ResourceModel, CallbackContext> progressEvent = handler.updateCluster(proxy, proxyClient, progress, desiredModel, fieldType, logger);
        assertThat(progressEvent.isInProgress()).isTrue();

        verifyRequest(expectedRequest, captor);
    }

    private ResourceHandlerRequest<ResourceModel> buildRequest(ResourceModel desiredModel,
                                                               ResourceModel previousModel) {
        return ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousModel)
                .desiredResourceState(desiredModel)
                .build();
    }

    private <T extends MemoryDbRequest> void verifyRequest (T expectedRequest,
                                                            ArgumentCaptor<T> captor) {
        T actualRequest = captor.getValue();
        AwsRequest requestWithoutCreds = actualRequest.toBuilder()
                .overrideConfiguration((AwsRequestOverrideConfiguration) null).build();
        assertThat(requestWithoutCreds).isEqualTo(expectedRequest);
    }


}
