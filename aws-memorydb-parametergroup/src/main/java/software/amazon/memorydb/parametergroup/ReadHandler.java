package software.amazon.memorydb.parametergroup;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return describeClusterParameterGroup(proxy, proxyClient, request.getDesiredResourceState(), callbackContext)
                .done(awsResponse -> {
                    ResourceModel respModel = Translator.translateFromReadResponse(awsResponse);
                    Set<Tag> tags = getTags(proxy, proxyClient, awsResponse);
                    if (tags != null) {
                        respModel.setTags(tags);
                    }
                    return ProgressEvent.defaultSuccessHandler(respModel);
                });

    }

    public Set<Tag> getTags(final AmazonWebServicesClientProxy proxy,
                            final ProxyClient<MemoryDbClient> client,
                            final DescribeParameterGroupsResponse readResponse) {
        try {
            final ListTagsResponse response =
                    proxy.injectCredentialsAndInvokeV2(Translator.translateToListTagsRequest(readResponse.parameterGroups().get(0)), client.client()::listTags);
            return response.tagList() != null && !response.tagList().isEmpty() ? response
                    .tagList()
                    .stream()
                    .map(tag -> Tag.builder()
                            .key(tag.key())
                            .value(tag.value())
                            .build())
                    .collect(Collectors.toSet())
                    : null;
        } catch (ParameterGroupNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (Exception e) {
            throw new CfnServiceInternalErrorException(e);
        }
    }
}
