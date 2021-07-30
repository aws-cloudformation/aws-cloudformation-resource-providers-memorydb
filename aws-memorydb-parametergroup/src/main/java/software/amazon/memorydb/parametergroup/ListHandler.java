package software.amazon.memorydb.parametergroup;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class ListHandler extends BaseHandlerStd {

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(AmazonWebServicesClientProxy proxy, ResourceHandlerRequest<ResourceModel> request, CallbackContext callbackContext, ProxyClient<MemoryDbClient> proxyClient, Logger logger) {
        final DescribeParameterGroupsResponse response =
                proxy.injectCredentialsAndInvokeV2(Translator.translateToListRequest(request.getNextToken()), proxyClient.client()::describeParameterGroups);

        return ProgressEvent.<ResourceModel, CallbackContext>builder().resourceModels(Translator.translateFromListResponse(response))
                .nextToken(response.nextToken()).status(OperationStatus.SUCCESS).build();
    }

}
