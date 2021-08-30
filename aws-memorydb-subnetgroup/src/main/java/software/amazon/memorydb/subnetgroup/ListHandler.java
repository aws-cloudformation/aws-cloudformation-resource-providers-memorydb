package software.amazon.memorydb.subnetgroup;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandlerStd {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        final DescribeSubnetGroupsResponse response =
                proxy.injectCredentialsAndInvokeV2(Translator.translateToListRequest(request.getNextToken()), proxyClient.client()::describeSubnetGroups);

        return ProgressEvent.<ResourceModel, CallbackContext>builder().resourceModels(Translator.translateFromListResponse(response))
                .nextToken(response.nextToken()).status(OperationStatus.SUCCESS).build();
    }
}
