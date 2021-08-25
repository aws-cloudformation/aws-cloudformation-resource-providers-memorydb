package software.amazon.memorydb.parametergroup;

import java.time.Duration;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

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

        final ReadHandler handler = new ReadHandler();
        final ParameterGroup parameterGroup = getTestParameterGroup();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        final DescribeParameterGroupsResponse describeParameterGroupsResponse = DescribeParameterGroupsResponse.builder()
                .parameterGroups(parameterGroup).build();
        when(proxyClient.client().describeParameterGroups(any(DescribeParameterGroupsRequest.class))).thenReturn(describeParameterGroupsResponse);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tagList(translateTagsToSdk(desiredTestResourceModel.getTags())).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredTestResourceModel).build();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getResourceModel().getTags()).isNotEmpty();

        verify(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));
        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));
    }

    @Test
    public void handleRequest_FailedWithResourceNotFound() {
        final ReadHandler handler = new ReadHandler();
        final ResourceModel desiredTestResourceModel = getDesiredTestResourceModel();
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(desiredTestResourceModel).build();

        doThrow(ParameterGroupNotFoundException.class).when(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            //expected
            assertThat(e.getCause() instanceof ParameterGroupNotFoundException).isTrue();
        }
    }
}