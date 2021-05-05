package software.amazon.cornerstone.cluster;

import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.awssdk.services.cornerstone.model.ClusterNotFoundException;
import software.amazon.awssdk.services.cornerstone.model.DeleteClusterRequest;
import software.amazon.awssdk.services.cornerstone.model.DeleteClusterResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<CornerstoneClient> proxyClient,
                                                                          final Logger logger) {
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                            .then(progress -> deleteCluster(proxy, proxyClient, progress, request, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteCluster(final AmazonWebServicesClientProxy proxy,
                                                                        final ProxyClient<CornerstoneClient> proxyClient,
                                                                        final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                        final Logger logger) {

        return proxy.initiate("AWS-Cornerstone-Cluster::Delete", proxyClient, request.getDesiredResourceState(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToDeleteRequest).backoffDelay(STABILIZATION_DELAY)
                    .makeServiceCall((awsRequest, client) -> client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteCluster))
                    .stabilize((awsRequest, awsResponse, client, model, context) -> isDeleted(proxyClient, model))
                    .done(this::setResourceModelToNullAndReturnSuccess);
    }

    private ProgressEvent<ResourceModel, CallbackContext> setResourceModelToNullAndReturnSuccess(final DeleteClusterRequest deleteDataSourceRequest,
                                                                                                 final DeleteClusterResponse deleteDataSourceResponse,
                                                                                                 final ProxyClient<CornerstoneClient> proxyClient,
                                                                                                 final ResourceModel resourceModel,
                                                                                                 final CallbackContext callbackContext) {
        return ProgressEvent.defaultSuccessHandler(null);
    }

    private Boolean isDeleted(final ProxyClient<CornerstoneClient> proxyClient,
                              final ResourceModel model) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), proxyClient.client()::describeClusters);
            return false;
        } catch (ClusterNotFoundException e) {
            return true;
        }
    }
}
