package software.amazon.memorydb.cluster;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Set;
import java.util.stream.Collectors;

public class ReadHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        return describeClusters(proxy, proxyClient, request.getDesiredResourceState(), callbackContext)
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
                            final DescribeClustersResponse readResponse) {
        try {
            final ListTagsResponse response =
                    proxy.injectCredentialsAndInvokeV2(Translator.translateToListTagsRequest(readResponse.clusters().get(0)), client.client()::listTags);
            return response.tagList() != null && !response.tagList().isEmpty() ? response
                    .tagList()
                    .stream()
                    .map(tag -> Tag.builder()
                            .key(tag.key())
                            .value(tag.value())
                            .build())
                    .collect(Collectors.toSet())
                    : null;
        } catch (ClusterNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (Exception e) {
            throw new CfnServiceInternalErrorException(e);
        }
    }
}
