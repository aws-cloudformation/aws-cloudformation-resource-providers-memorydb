package software.amazon.memorydb.subnetgroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

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

    @Test
    public void handleRequest_SimpleSuccess() {
        final ListHandler handler = new ListHandler();

        final ResourceModel desiredResourceStateModel = getDesiredResourceStateModel();

        final SubnetGroup subnetGroup = getSubnetGroup();
        final ResourceModel expectedResourceStateModel = getResourceModel(subnetGroup);

        final DescribeSubnetGroupsResponse describeSubnetGroupsResponse = DescribeSubnetGroupsResponse.builder().subnetGroups(subnetGroup).nextToken("TEST_MARKER").build();
        when(proxyClient.client().describeSubnetGroups(any(DescribeSubnetGroupsRequest.class))).thenReturn(describeSubnetGroupsResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredResourceStateModel)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(expectedResourceStateModel.equals(desiredResourceStateModel));

    }
}
