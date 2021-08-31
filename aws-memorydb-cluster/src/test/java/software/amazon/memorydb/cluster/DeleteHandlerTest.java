package software.amazon.memorydb.cluster;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DeleteClusterRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteClusterResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.SnapshotAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<MemoryDbClient> proxyClient;

    @Mock
    MemoryDbClient sdkClient;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(MemoryDbClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DeleteHandler handler = new DeleteHandler();

        final Cluster cluster = getTestCluster();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder().clusters(cluster).nextToken(null).build();
        doReturn(describeClustersResponse).doThrow(ClusterNotFoundException.class).when(proxyClient.client())
                                          .describeClusters(any(DescribeClustersRequest.class));
        doReturn(DeleteClusterResponse.builder().cluster(cluster.toBuilder().status("deleting").build()).build()).when(proxyClient.client()).deleteCluster(
                any(DeleteClusterRequest.class));

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_FailedWithResourceNotFound() {
        final DeleteHandler handler = new DeleteHandler();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        doThrow(ClusterNotFoundException.class).when(proxyClient.client()).deleteCluster(any(DeleteClusterRequest.class));

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            //expected
            assertThat(e.getCause() instanceof ClusterNotFoundException).isTrue();
        }
    }

    @Test
    public void handleRequest_FailedWithResourceAlreadyExists() {
        final DeleteHandler handler = new DeleteHandler();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        desiredTestResourceModel.setFinalSnapshotName("final-snapshot");
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        doThrow(SnapshotAlreadyExistsException.class).when(proxyClient.client()).deleteCluster(any(DeleteClusterRequest.class));

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            //expected
            assertThat(e.getCause() instanceof SnapshotAlreadyExistsException).isTrue();
        }
    }
}
