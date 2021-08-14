package software.amazon.memorydb.cluster;

import org.apache.commons.collections.CollectionUtils;
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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ReadHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> describeClusters(proxy, progress, proxyClient))
                .then(progress -> listTags(proxy, progress, proxyClient))
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));

    }

    private ProgressEvent<ResourceModel, CallbackContext> describeClusters(
            AmazonWebServicesClientProxy proxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy
                .initiate("AWS-MemoryDB-Cluster::Describe", proxyClient, progress.getResourceModel(),
                        progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeClusters)))
                .done((describeUserRequest, describeClustersResponse, proxyInvocation, resourceModel, context) ->
                        ProgressEvent.progress(Translator.translateFromReadResponse(describeClustersResponse), context));
    }

    private ProgressEvent<ResourceModel, CallbackContext> listTags(
            AmazonWebServicesClientProxy proxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            ProxyClient<MemoryDbClient> proxyClient
    ) {

        if(!isArnPresent(progress.getResourceModel())) {
            return progress;
        }

        return proxy
                .initiate("AWS-MemoryDB-Cluster::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToListTagsRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() -> client.injectCredentialsAndInvokeV2(awsRequest, client.client()::listTags)))
                .done( (listTagsRequest, listTagsResponse, proxyInvocation, resourceModel, context) -> {
                            if(CollectionUtils.isNotEmpty(listTagsResponse.tagList())) {
                                resourceModel.setTags(Translator.translateTags(listTagsResponse.tagList()));
                            }
                            return ProgressEvent.progress(resourceModel, context);
                        }
                );
    }
}
