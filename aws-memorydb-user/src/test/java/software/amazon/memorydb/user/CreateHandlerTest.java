package software.amazon.memorydb.user;

import java.time.Duration;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.CreateUserRequest;
import software.amazon.awssdk.services.memorydb.model.CreateUserResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.UserAlreadyExistsException;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

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
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateUserResponse createUserResponse = CreateUserResponse.builder().build();
        final DescribeUsersResponse describeUserResponse =
            DescribeUsersResponse.builder().users(buildDefaultUser()).build();
        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(Translator.translateTagsToSdk(TAG_SET)).build();
        when(sdkClient.createUser(any(CreateUserRequest.class))).thenReturn(createUserResponse);
        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        when(sdkClient.describeUsers(any(DescribeUsersRequest.class))).thenReturn(describeUserResponse);

        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();
        model.getAuthenticationMode().setPasswords(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler
            .handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModel().getStatus()).isEqualTo(ACTIVE);
        assertThat(response.getResourceModel().getUserName()).isEqualTo(USER_NAME);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(sdkClient, atLeast(1)).serviceName();
    }

    @Test
    public void handleRequest_Failed() {
        doThrow(UserAlreadyExistsException.class)
            .when(proxyClient.client()).createUser(any(CreateUserRequest.class));

        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            assertThat(e.getCause() instanceof UserAlreadyExistsException).isTrue();
        }
        verify(sdkClient, atLeast(1)).serviceName();
    }

    @Test
    public void handleRequestInvalidUserId_Failed() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();
        model.setUserName(model.getUserName().toUpperCase());
        model.getAuthenticationMode().setPasswords(null);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnInvalidRequestException");
        } catch (CfnInvalidRequestException e) {
            //success
        }
    }
}
