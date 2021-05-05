package software.amazon.cornerstone.cluster;

import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.awssdk.services.cornerstone.model.ClusterNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<CornerstoneClient> proxyClient,
                                                                          final Logger logger) {
        return proxy.initiate("AWS-Cornerstone-Cluster::Read", proxyClient, request.getDesiredResourceState(), callbackContext)
                    .translateToServiceRequest(Translator::translateToReadRequest).backoffDelay(STABILIZATION_DELAY).makeServiceCall((awsRequest, client) -> {
                    try {
                        return client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeClusters);
                    } catch (final ClusterNotFoundException e) {
                        throw new CfnNotFoundException(e);
                    }
                }).done(awsResponse -> ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(awsResponse)));
    }

}
