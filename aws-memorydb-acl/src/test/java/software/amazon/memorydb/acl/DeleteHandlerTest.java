package software.amazon.memorydb.acl;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.AclNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DeleteAclRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteAclResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
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

import static junit.framework.Assert.assertEquals;
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
    public void handleRequest_SimpleFailWithRGAssociation() {
        final DescribeAcLsResponse describeAcLsResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE, true, null)).build();

        when(sdkClient.describeACLs(any(DescribeAcLsRequest.class))).thenReturn(describeAcLsResponse);

        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            handler.handleRequest(proxy, request,
                new CallbackContext(), proxyClient, logger);
            fail("Expected to receive InvalidACLStateException");
        } catch (Exception e) {
            sdkClient.serviceName();
            assertEquals(e.getClass(), CfnGeneralServiceException.class);
        }
    }

    @Test
    public void handleRequest_SimpleSuccessWithoutRG() {
        final DeleteAclResponse deleteAclResponse = DeleteAclResponse.builder().build();
        when(sdkClient.deleteACL(any(DeleteAclRequest.class))).thenReturn(deleteAclResponse);

        final DescribeAcLsResponse describeAcl =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE, false, null)).build();

        final DescribeAcLsResponse describeInProgressAclsResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(DELETING, false, null)).build();

        AtomicInteger attempt = new AtomicInteger(3);
        when(sdkClient.describeACLs(any(DescribeAcLsRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 3:
                    return describeAcl;
                case 2:
                    return describeInProgressAclsResponse;
                default:
                    throw AclNotFoundException.builder().build();
            }
        });

        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = buildDefaultResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request,
            new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_AlreadyDeleted() {
        doThrow(AclNotFoundException.class)
            .when(proxyClient.client()).describeACLs(any(DescribeAcLsRequest.class));
        doThrow(AclNotFoundException.class)
            .when(proxyClient.client()).deleteACL(any(DeleteAclRequest.class));

        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected CfnNotFoundException");
        } catch (CfnNotFoundException e) {
            assertThat(e.getCause() instanceof AclNotFoundException).isTrue();
        }
    }
}
