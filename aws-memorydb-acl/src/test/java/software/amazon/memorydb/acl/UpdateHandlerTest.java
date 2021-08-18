package software.amazon.memorydb.acl;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.AclNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UpdateAclRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateAclResponse;
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
        final UpdateAclResponse updateAclResponse = UpdateAclResponse.builder().build();
        when(sdkClient.updateACL(any(UpdateAclRequest.class))).thenReturn(updateAclResponse);

        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(
                ImmutableList.of(
                    software.amazon.awssdk.services.memorydb.model.Tag.builder().key("test").value("test").build())
            ).build();
        AtomicInteger attemptList = new AtomicInteger(2);
        final ListTagsResponse listTagsAfterPlayResponse =
            ListTagsResponse.builder().tagList(Translator.translateTagsToSdk(TAG_SET)).build();
        when(sdkClient.listTags(any(ListTagsRequest.class))).then((m) -> {
            switch (attemptList.getAndDecrement()) {
                case 2:
                    return listTagsResponse;
                default:
                    return listTagsAfterPlayResponse;
            }
        });

        final DescribeAcLsResponse describeInProgressAclResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(MODIFYING, true, ImmutableList.of("test"))).build();
        final DescribeAcLsResponse describeUpdatedAclResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE, true, ImmutableList.of("test"))).build();
        AtomicInteger attempt = new AtomicInteger(2);

        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(TagResourceResponse.builder().build());
        when(sdkClient.untagResource(any(UntagResourceRequest.class))).thenReturn(UntagResourceResponse.builder().build());
        when(sdkClient.describeACLs(any(DescribeAcLsRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 2:
                    return describeInProgressAclResponse;
                default:
                    return describeUpdatedAclResponse;
            }
        });

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel modelPrevious = buildDefaultResourceModel();
        final ResourceModel modelDesired = buildDefaultResourceModel(ImmutableList.of("test"));

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(modelPrevious)
            .desiredResourceState(modelDesired)
            .previousResourceTags(Collections.singletonMap("test", "test"))
            .desiredResourceTags(Translator.translateTags(TAG_SET))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler
            .handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getStatus()).isEqualTo(ACTIVE);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_AlreadyDeleted() {
        doThrow(AclNotFoundException.class)
            .when(proxyClient.client()).describeACLs(any(DescribeAcLsRequest.class));

        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel modelPrevious = buildDefaultResourceModel();
        final ResourceModel modelDesired = buildDefaultResourceModel(ImmutableList.of("test"));
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
            assertThat(e.getCause() instanceof AclNotFoundException).isTrue();
        }
    }

    @Test
    public void handleRequest_AddUser() {
        testUserIdsChangeHelper(ImmutableList.of(), ImmutableList.of("test"));
    }

    @Test
    public void handleRequest_RemoveUser() {
        testUserIdsChangeHelper(ImmutableList.of("test"), ImmutableList.of());
    }

    @Test
    public void handleRequest_NoChange() {
        testUserIdsChangeHelper(ImmutableList.of(), ImmutableList.of());
    }

    @Test
    public void handleRequest_AddRemove() {
        testUserIdsChangeHelper(ImmutableList.of("t1"), ImmutableList.of("t2"));
    }

    public void testUserIdsChangeHelper(List<String> current, List<String> target) {

        final ListTagsResponse listTagsResponse =
            ListTagsResponse.builder().tagList(Translator.translateTagsToSdk(TAG_SET)).build();
        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);

        final ArgumentCaptor<UpdateAclRequest> updateAclCapture = ArgumentCaptor.forClass(UpdateAclRequest.class);

        final UpdateAclResponse updateAclResponse = UpdateAclResponse.builder().build();
        when(sdkClient.updateACL(any(UpdateAclRequest.class))).thenReturn(updateAclResponse);

        final DescribeAcLsResponse describeAclBefore =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE, false, current)).build();

        final DescribeAcLsResponse describeInProgressUserResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(MODIFYING, false, target)).build();
        final DescribeAcLsResponse describeModifiedUserResponse =
            DescribeAcLsResponse.builder().acLs(buildDefaultAcl(ACTIVE, false, target)).build();

        AtomicInteger attempt = new AtomicInteger(3);
        when(sdkClient.describeACLs(any(DescribeAcLsRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 3:
                    return describeAclBefore;
                case 2:
                    return describeInProgressUserResponse;
                default:
                    return describeModifiedUserResponse;
            }
        });
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel modelPrevious = buildDefaultResourceModel();
        final ResourceModel modelDesired = buildDefaultResourceModel(target);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceState(modelPrevious)
            .desiredResourceState(modelDesired)
            .previousResourceTags(Collections.singletonMap("test", "test"))
            .desiredResourceTags(Translator.translateTags(TAG_SET))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler
            .handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getStatus()).isEqualTo(ACTIVE);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        List<String> userIdsToAdd = target.stream()
            .distinct()
            .filter(((Predicate<String>) current::contains).negate())
            .collect(Collectors.toList());

        List<String> userIdsToRemove = current.stream()
            .distinct()
            .filter(((Predicate<String>) target::contains).negate())
            .collect(Collectors.toList());

        verify(sdkClient).updateACL(updateAclCapture.capture());
        UpdateAclRequest convertedRequest = updateAclCapture.getValue();
        assertEquals(convertedRequest.userNamesToAdd(), userIdsToAdd);
        assertEquals(convertedRequest.userNamesToRemove(), userIdsToRemove);
    }

}
