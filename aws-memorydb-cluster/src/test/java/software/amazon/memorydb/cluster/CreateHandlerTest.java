package software.amazon.memorydb.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.memorydb.cluster.CreateHandler.NODE_TYPE_REQUIRED_FOR_CLUSTER;
import static software.amazon.memorydb.cluster.CreateHandler.ACL_NAME_REQUIRED_FOR_CLUSTER;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.ClusterAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.CreateClusterRequest;
import software.amazon.awssdk.services.memorydb.model.CreateClusterResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<MemoryDbClient> proxyClient;

    @Mock
    MemoryDbClient sdkClient;

    private CreateHandler handler;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(MemoryDbClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
        handler = new CreateHandler();
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final Cluster cluster = getTestCluster();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        final ResourceModel expectedResourceModel = getResourceModel(cluster);

        final CreateClusterResponse createClusterResponse = getCreateClusterReponse();

        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder().clusters(cluster).nextToken(null).build();
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder().build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        when(proxyClient.client().createCluster(any(CreateClusterRequest.class))).thenReturn(createClusterResponse);
        when(proxyClient.client().describeClusters(any(DescribeClustersRequest.class))).thenReturn(describeClustersResponse);

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getClusterName()).isEqualTo(expectedResourceModel.getClusterName());
        assertThat(response.getResourceModel()).isEqualTo(expectedResourceModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_ClusterAlreadyExists() {
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();

        doThrow(ClusterAlreadyExistsException.class).when(proxyClient.client()).createCluster(any(CreateClusterRequest.class));

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            assertThat(e.getCause() instanceof ClusterAlreadyExistsException).isTrue();
        }

        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_NodeTypeMissing() {
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        desiredTestResourceModel.setNodeType(null);

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
            assertThat(e.getMessage()).contains(NODE_TYPE_REQUIRED_FOR_CLUSTER);
        }

        verify(sdkClient, never()).serviceName();
    }

    @Test
    public void handleRequest_Failure_ACLNameMissing() {
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        desiredTestResourceModel.setACLName(null);

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
            assertThat(e.getMessage()).contains(ACL_NAME_REQUIRED_FOR_CLUSTER);
        }

        verify(sdkClient, never()).serviceName();
    }

    private CreateClusterResponse getCreateClusterReponse() {
        return CreateClusterResponse.builder().cluster(Cluster.builder().name(CLUSTER_NAME).description(CLUSTER_DESCRIPTION).status(CREATING_STATUS).nodeType(NODE_TYPE)
                                    .numberOfShards(NUM_SHARDS).subnetGroupName(SUBNET_GROUP_NAME).securityGroups(getSecurityGroupMemberships(SECURITY_GROUP_IDS))
                                    .snsTopicArn(SNS_TOPIC_ARN).tlsEnabled(TLS_ENABLED).arn(CLUSTER_ARN).engineVersion(ENGINE_VERSION)
                                    .clusterEndpoint(software.amazon.awssdk.services.memorydb.model.Endpoint.builder().port(PORT).build())
                                    .availabilityMode(AVAILABILITY_MODE).build()).build();
    }
}
