package software.amazon.memorydb.subnetgroup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.memorydb.subnetgroup.Translator.translateTagsToSdk;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

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
        final UpdateHandler handler = new UpdateHandler();

        final SubnetGroup subnetGroup = getSubnetGroup();
        final ResourceModel currentResourceModel = getDesiredResourceStateModel();
        final ResourceModel desiredTestResourceModel = currentResourceModel;
        final ResourceModel expectedResourceModel = getResourceModel(subnetGroup);
        expectedResourceModel.setTags(TAG_SET);
        final DescribeSubnetGroupsResponse describeSubnetGroupsResponse = DescribeSubnetGroupsResponse.builder().subnetGroups(subnetGroup).build();
        when(proxyClient.client().describeSubnetGroups(any(DescribeSubnetGroupsRequest.class))).thenReturn(describeSubnetGroupsResponse);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder().tagList(translateTagsToSdk(TAG_SET)).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);


        final ResourceHandlerRequest<ResourceModel> request = buildRequest(desiredTestResourceModel, currentResourceModel);
        request.setDesiredResourceTags(translateTagsToMap(TAG_SET));
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expectedResourceModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxyClient.client(), times(1)).listTags(any(ListTagsRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }

    private ResourceHandlerRequest<ResourceModel> buildRequest(ResourceModel desiredModel,
                                                               ResourceModel previousModel) {
        return ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(previousModel)
                .desiredResourceState(desiredModel)
                .build();
    }
}
