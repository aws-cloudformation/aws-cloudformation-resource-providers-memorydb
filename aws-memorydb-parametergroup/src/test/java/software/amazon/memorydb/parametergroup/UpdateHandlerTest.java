package software.amazon.memorydb.parametergroup;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParametersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParametersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.Parameter;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UpdateParameterGroupRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
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

    private UpdateHandler handler;

    private ResourceModel RESOURCE_MODEL_PREV;
    private ResourceModel RESOURCE_MODEL;


    private ResourceHandlerRequest<ResourceModel> requestSameParams;
    private ResourceHandlerRequest<ResourceModel> requestUpdParams;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(MemoryDbClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
        handler = new UpdateHandler();

        RESOURCE_MODEL_PREV = getDesiredTestResourceModel();
        RESOURCE_MODEL_PREV.setTags(null);

        RESOURCE_MODEL = getDesiredTestResourceModel();
        RESOURCE_MODEL.setParameters(PARAMS);


        requestSameParams = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(RESOURCE_MODEL)
                .previousResourceState(RESOURCE_MODEL)
                .desiredResourceTags(translateTagsToMap(TAG_SET))
                .previousResourceTags(translateTagsToMap(TAG_SET))
                .build();
        requestUpdParams = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(RESOURCE_MODEL)
                .previousResourceState(RESOURCE_MODEL_PREV)
                .desiredResourceTags(translateTagsToMap(TAG_SET))
                .build();
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(true);

        final DescribeParameterGroupsResponse describeParameterGroupsResponse = DescribeParameterGroupsResponse.builder()
                .parameterGroups(getTestParameterGroup()).build();
        when(sdkClient.describeParameterGroups(any(DescribeParameterGroupsRequest.class))).thenReturn(describeParameterGroupsResponse);

        final ListTagsResponse listTagsResponse = ListTagsResponse.builder().build();
        when(sdkClient.listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().tagList(translateTagsToSdk(requestUpdParams.getDesiredResourceState().getTags())).build();
        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestUpdParams, callbackContext, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).updateParameterGroup(any(UpdateParameterGroupRequest.class));
        verify(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));
        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));
        verify(proxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequest_StabilizationWithNextPage(){

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(false);

        final Cluster cluster = Cluster.builder()
                .parameterGroupName(RESOURCE_MODEL.getParameterGroupName())
                .parameterGroupStatus("in-sync").build();

        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder()
                .clusters(Lists.newArrayList(cluster))
                .nextToken("token")
                .build();
        when(sdkClient.describeClusters(any(DescribeClustersRequest.class))).thenReturn(describeClustersResponse);


        final ProgressEvent<ResourceModel, CallbackContext> response = handler.waitForStabilize(proxy, proxyClient, ProgressEvent.progress(RESOURCE_MODEL, callbackContext), requestUpdParams);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(30);
        assertThat(response.getCallbackContext().isClusterStabilized()).isEqualTo(false);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeClusters(any(DescribeClustersRequest.class));
    }

    @Test
    public void handleRequest_StabilizationWithoutNextPage(){

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(false);

        final Cluster cluster = Cluster.builder()
                .parameterGroupName(RESOURCE_MODEL.getParameterGroupName())
                .parameterGroupStatus("in-sync").build();

        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder()
                .clusters(Lists.newArrayList(cluster))
                .build();
        when(sdkClient.describeClusters(any(DescribeClustersRequest.class))).thenReturn(describeClustersResponse);


        final ProgressEvent<ResourceModel, CallbackContext> response = handler.waitForStabilize(proxy, proxyClient, ProgressEvent.progress(RESOURCE_MODEL, callbackContext), requestUpdParams);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().isClusterStabilized()).isEqualTo(true);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeClusters(any(DescribeClustersRequest.class));
    }

    @Test
    public void handleRequest_StabilizationApplying(){

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(false);

        final Cluster cluster = Cluster.builder()
                .parameterGroupName(RESOURCE_MODEL.getParameterGroupName())
                .parameterGroupStatus("applying").build();

        final DescribeClustersResponse describeClustersResponse = DescribeClustersResponse.builder()
                .clusters(Lists.newArrayList(cluster))
                .build();
        when(sdkClient.describeClusters(any(DescribeClustersRequest.class))).thenReturn(describeClustersResponse);


        final ProgressEvent<ResourceModel, CallbackContext> response = handler.waitForStabilize(proxy, proxyClient, ProgressEvent.progress(RESOURCE_MODEL, callbackContext), requestUpdParams);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(30);
        assertThat(response.getCallbackContext().isClusterStabilized()).isEqualTo(false);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeClusters(any(DescribeClustersRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccessSameParams(){
        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(true);

        final DescribeParameterGroupsResponse describeParameterGroupsResponse = DescribeParameterGroupsResponse.builder()
                .parameterGroups(getTestParameterGroup()).build();
        when(sdkClient.describeParameterGroups(any(DescribeParameterGroupsRequest.class))).thenReturn(describeParameterGroupsResponse);
        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tagList(translateTagsToSdk(requestSameParams.getDesiredResourceState().getTags())).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestSameParams, callbackContext, proxyClient, logger);

        ResourceModel expectedModel = requestSameParams.getDesiredResourceState();
        expectedModel.setParameters(null);


        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(requestSameParams.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));
        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_UpdateParameterGroupWithParamsRemoved(){

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(true);

        Map<String, Object> params = new HashMap<>();
        params.put("param2", "value");
        params.put("param3", "newValue");
        RESOURCE_MODEL_PREV.setParameters(params
        );

        final DescribeParameterGroupsResponse describeParameterGroupsResponse = DescribeParameterGroupsResponse.builder()
                .parameterGroups(getTestParameterGroup()).build();
        when(sdkClient.describeParameterGroups(any(DescribeParameterGroupsRequest.class))).thenReturn(describeParameterGroupsResponse);

        final DescribeParametersResponse describeParametersResponse = DescribeParametersResponse.builder()
                .parameters(Parameter.builder().name("param").value("default_value").build()).build();
        when(sdkClient.describeParameters(any(DescribeParametersRequest.class))).thenReturn(describeParametersResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.updateParameterGroup(proxy, proxyClient, ProgressEvent.progress(RESOURCE_MODEL, new CallbackContext()), requestUpdParams);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().isClusterStabilized()).isEqualTo(false);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).describeParameterGroups(any(DescribeParameterGroupsRequest.class));
        verify(proxyClient.client()).describeParameters(any(DescribeParametersRequest.class));
        verify(proxyClient.client()).updateParameterGroup(any(UpdateParameterGroupRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_UpdateParameterGroupUpdateTags(){

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setClusterStabilized(true);
        Set<Tag> newTags = Sets.newSet(Tag.builder().key("key").value("newValue").build(),
                Tag.builder().key("keyNew").value("value").build());
        Set<Tag> oldTags = Sets.newSet(
                Tag.builder().key("key").value("oldValue").build(),
                Tag.builder().key("keyOld").value("value").build());

        ResourceHandlerRequest<ResourceModel> requestUpdTags = requestSameParams;
        requestUpdTags.setDesiredResourceTags(translateTagsToMap(newTags));
        requestUpdTags.setPreviousResourceTags(translateTagsToMap(oldTags));
        requestUpdTags.getDesiredResourceState().setTags(newTags);

        final ListTagsResponse listTagsResponse = ListTagsResponse.builder()
                .tagList(translateTagsToSdk(oldTags)).build();
        when(proxyClient.client().listTags(any(ListTagsRequest.class))).thenReturn(listTagsResponse);
        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();
        when(sdkClient.untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().tagList(translateTagsToSdk(requestUpdTags.getDesiredResourceState().getTags())).build();
        when(sdkClient.tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.tagResource(proxy, proxyClient, ProgressEvent.progress(RESOURCE_MODEL, callbackContext), requestUpdTags, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().isClusterStabilized()).isEqualTo(true);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getNextToken()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyClient.client()).listTags(any(ListTagsRequest.class));
        verify(proxyClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(proxyClient.client()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).serviceName();
    }
}
