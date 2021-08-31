package software.amazon.memorydb.user;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UpdateUserRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateUserResponse;
import software.amazon.awssdk.services.memorydb.model.UserNotFoundException;
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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<MemoryDbClient> proxyClient;

    @Mock
    private MemoryDbClient sdkClient;

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
        final UpdateUserResponse updateUserResponse = UpdateUserResponse.builder().build();
        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(
                ImmutableList.of(
                    software.amazon.awssdk.services.memorydb.model.Tag.builder().key("test").value("test").build())
            ).build();

        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(TagResourceResponse.builder().build());
        when(sdkClient.untagResource(any(UntagResourceRequest.class))).thenReturn(UntagResourceResponse.builder().build());
        when(sdkClient.updateUser(any(UpdateUserRequest.class))).thenReturn(updateUserResponse);

        final DescribeUsersResponse describeInProgressUserResponse =
            DescribeUsersResponse.builder().users(buildDefaultUser(MODIFYING)).build();
        final DescribeUsersResponse describeModifiedUserResponse =
            DescribeUsersResponse.builder().users(buildDefaultUser(ACTIVE)).build();
        AtomicInteger attempt = new AtomicInteger(2);
        when(sdkClient.describeUsers(any(DescribeUsersRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 2:
                    return describeInProgressUserResponse;
                default:
                    return describeModifiedUserResponse;
            }
        });

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel modelPrevious = buildDefaultResourceModel();
        final ResourceModel modelDesired = buildDefaultResourceModel();
        modelDesired.setAccessString(modelPrevious.getAccessString() + "v2");

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(modelPrevious)
            .desiredResourceState(modelDesired)
            .build();
        request.setDesiredResourceTags(Translator.translateTags(TAG_SET));
        request.setPreviousResourceTags(Collections.singletonMap("test", "test"));

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel().getStatus()).isEqualTo(ACTIVE);
        assertThat(response.getResourceModel().getUserName()).isEqualTo(USER_NAME);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_AlreadyDeleted() {
        doThrow(UserNotFoundException.class)
            .when(proxyClient.client()).updateUser(any(UpdateUserRequest.class));

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceModel modelPrevious = buildDefaultResourceModel();
        final ResourceModel modelDesired = buildDefaultResourceModel();
        modelDesired.setAccessString(modelPrevious.getAccessString() + "v2");

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(modelPrevious)
            .desiredResourceState(modelDesired)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler
                .handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            assertThat(e.getCause() instanceof UserNotFoundException).isTrue();
        }
    }

    @Test
    public void handleRequest_TagUpdate() {
        final DescribeUsersResponse describeUserResponse =
            DescribeUsersResponse.builder().users(buildDefaultUser()).build();
        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(
                ImmutableList.of(
                    software.amazon.awssdk.services.memorydb.model.Tag.builder().key("test").value("test").build())
            ).build();

        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(TagResourceResponse.builder().build());
        when(sdkClient.untagResource(any(UntagResourceRequest.class))).thenReturn(UntagResourceResponse.builder().build());
        when(sdkClient.describeUsers(any(DescribeUsersRequest.class))).thenReturn(describeUserResponse);

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(model)
            .desiredResourceState(model)
            .build();
        request.setDesiredResourceTags(Translator.translateTags(TAG_SET));
        request.setPreviousResourceTags(Collections.singletonMap("test", "test"));

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel().getStatus()).isEqualTo(ACTIVE);
        assertThat(response.getResourceModel().getUserName()).isEqualTo(USER_NAME);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
