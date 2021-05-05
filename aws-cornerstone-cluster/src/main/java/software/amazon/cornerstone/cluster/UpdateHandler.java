package software.amazon.cornerstone.cluster;

import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<CornerstoneClient> proxyClient,
                                                                          final Logger logger) {
        //TODO Actual implementation of Update Handler, This is just placeholder to delegate the call to Read Handler.
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
