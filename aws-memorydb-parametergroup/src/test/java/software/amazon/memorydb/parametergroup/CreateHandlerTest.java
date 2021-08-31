package software.amazon.memorydb.parametergroup;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.CreateParameterGroupRequest;
import software.amazon.awssdk.services.memorydb.model.CreateParameterGroupResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.memorydb.parametergroup.CreateHandler.FAMILY_REQUIRED_FOR_PARAMETER_GROUP;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<MemoryDbClient> proxyClient;

    @Mock
    MemoryDbClient memoryDbClient;

    private CreateHandler handler;

    private ResourceModel RESOURCE_MODEL;


    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        memoryDbClient = mock(MemoryDbClient.class);
        proxyClient = MOCK_PROXY(proxy, memoryDbClient);

        RESOURCE_MODEL = getDesiredTestResourceModel();

        handler = new CreateHandler();
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(memoryDbClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final CreateParameterGroupResponse createParameterGroupResponse = CreateParameterGroupResponse.builder().build();
        when(proxyClient.client().createParameterGroup(any(CreateParameterGroupRequest.class))).thenReturn(createParameterGroupResponse);
        final DescribeParameterGroupsResponse describeParameterGroupsResponse = DescribeParameterGroupsResponse.builder()
                .parameterGroups(ParameterGroup.builder()
                        .arn(RESOURCE_MODEL.getARN())
                        .name(RESOURCE_MODEL.getParameterGroupName())
                        .family(RESOURCE_MODEL.getFamily())
                        .description(RESOURCE_MODEL.getDescription()).build()).build();
        when(proxyClient.client().describeParameterGroups(any(DescribeParameterGroupsRequest.class))).thenReturn(describeParameterGroupsResponse);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tagList(translateTagsToSdk(RESOURCE_MODEL.getTags())).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceTags(translateTagsToMap(TAG_SET))
                .desiredResourceState(RESOURCE_MODEL)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        verify(proxyClient.client()).createParameterGroup(any(CreateParameterGroupRequest.class));
        verify(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));
        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));

        verify(memoryDbClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_ParameterGroupAlreadyExists() {
        final ResourceModel desiredTestResourceModel = RESOURCE_MODEL;

        doThrow(ParameterGroupAlreadyExistsException.class).when(proxyClient.client()).createParameterGroup(any(CreateParameterGroupRequest.class));

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            assertThat(e.getCause() instanceof ParameterGroupAlreadyExistsException).isTrue();
        }

        verify(memoryDbClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_Failure_FamilyMissing() {
        final ResourceModel desiredTestResourceModel = RESOURCE_MODEL;
        desiredTestResourceModel.setFamily(null);

        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
            assertThat(e.getMessage()).contains(FAMILY_REQUIRED_FOR_PARAMETER_GROUP);
        }

        verify(memoryDbClient, never()).serviceName();
    }
}
