package software.amazon.memorydb.subnetgroup;

import junit.framework.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.CreateSubnetGroupRequest;
import software.amazon.awssdk.services.memorydb.model.CreateSubnetGroupResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

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
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SubnetGroupNameNull() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
        }
    }

    @Test
    public void handleRequest_SubnetIdsNull() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().subnetGroupName("test").build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
        }
    }

    @Test
    public void handleRequest_SubnetGroupAlreadyExists() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = getDesiredResourceStateModel();

        when(proxyClient.client().createSubnetGroup(any(CreateSubnetGroupRequest.class))).thenThrow(SubnetGroupAlreadyExistsException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            assertThat(e.getCause() instanceof SubnetGroupAlreadyExistsException).isTrue();
        }

        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = getDesiredResourceStateModel();

        final SubnetGroup subnetGroup = getSubnetGroup();
        final ResourceModel expectedResourceModel = getResourceModel(subnetGroup);

        final CreateSubnetGroupResponse createSubnetGroupResponse = getCreateSubnetGroupResponse();
        final DescribeSubnetGroupsResponse describeSubnetGroupsResponse = DescribeSubnetGroupsResponse.builder().subnetGroups(subnetGroup).build();
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder().build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        when(proxyClient.client().createSubnetGroup(any(CreateSubnetGroupRequest.class))).thenReturn(createSubnetGroupResponse);
        when(proxyClient.client().describeSubnetGroups(any(DescribeSubnetGroupsRequest.class))).thenReturn(describeSubnetGroupsResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        Assert.assertEquals(expectedResourceModel, model);

        verify(sdkClient, atLeastOnce()).serviceName();
    }
}
