package software.amazon.memorydb.cluster;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                            .then(progress -> deleteCluster(proxy, proxyClient, progress, request, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteCluster(final AmazonWebServicesClientProxy proxy,
                                                                        final ProxyClient<MemoryDbClient> proxyClient,
                                                                        final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                        final Logger logger) {

        return proxy.initiate("AWS-memorydb-Cluster::Delete", proxyClient, request.getDesiredResourceState(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToDeleteRequest).backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteCluster)))
                .stabilize((awsRequest, awsResponse, client, model, context) -> isDeleted(proxyClient, model))
                .done((deleteClusterRequest, deleteClusterResponse, proxyInvocation, model, context) -> ProgressEvent
                        .defaultSuccessHandler(null));
    }

    private Boolean isDeleted(final ProxyClient<MemoryDbClient> proxyClient,
                              final ResourceModel model) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), proxyClient.client()::describeClusters);
            return false;
        } catch (ClusterNotFoundException e) {
            return true;
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }
}
