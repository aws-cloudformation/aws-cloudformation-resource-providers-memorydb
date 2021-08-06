package software.amazon.memorydb.acl;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.AclAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.CreateAclRequest;
import software.amazon.awssdk.services.memorydb.model.CreateAclResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
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
        final CreateAclResponse createAclResponse = CreateAclResponse.builder().build();
        when(proxyClient.client().createACL(any(CreateAclRequest.class)))
            .thenReturn(createAclResponse);
        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(Translator.translateTagsToSdk(TAG_SET)).build();

        final DescribeAcLsResponse describeInProgressUserResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(CREATING)).build();
        final DescribeAcLsResponse describeModifiedUserResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE)).build();
        AtomicInteger attempt = new AtomicInteger(2);
        when(sdkClient.describeACLs(any(DescribeAcLsRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 2:
                    return describeInProgressUserResponse;
                default:
                    return describeModifiedUserResponse;
            }
        });
        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);

        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();

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
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(sdkClient, atLeast(1)).serviceName();
    }

    @Test
    public void handleRequest_Failed() {
        doThrow(AclAlreadyExistsException.class)
            .when(proxyClient.client()).createACL(any(CreateAclRequest.class));

        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler
                .handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnAlreadyExistsException");
        } catch (CfnAlreadyExistsException e) {
            assertThat(e.getCause() instanceof AclAlreadyExistsException).isTrue();
            verify(sdkClient, atLeast(1)).serviceName();
        }
    }

    @Test
    public void handleRequestInvalidUserId_Failed() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = buildDefaultResourceModel();
        model.setACLName(model.getACLName().toUpperCase());

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
