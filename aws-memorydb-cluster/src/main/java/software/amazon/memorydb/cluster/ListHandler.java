package software.amazon.memorydb.cluster;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandlerStd {

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                       final ResourceHandlerRequest<ResourceModel> request,
                                                                       final CallbackContext callbackContext,
                                                                       final ProxyClient<MemoryDbClient> proxyClient,
                                                                       final Logger logger) {
        final DescribeClustersResponse response =
                proxy.injectCredentialsAndInvokeV2(Translator.translateToListRequest(request.getNextToken()), proxyClient.client()::describeClusters);

        return ProgressEvent.<ResourceModel, CallbackContext>builder().resourceModels(Translator.translateFromListResponse(response))
                                                                      .nextToken(response.nextToken()).status(OperationStatus.SUCCESS).build();
    }
}
