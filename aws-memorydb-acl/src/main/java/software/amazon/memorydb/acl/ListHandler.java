package software.amazon.memorydb.acl;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandlerStd {

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<MemoryDbClient> proxyClient, Logger logger) {
        final DescribeAcLsRequest awsRequest = Translator.translateToListRequest(request.getNextToken());

        final DescribeAcLsResponse describeAclResponse = proxy.injectCredentialsAndInvokeV2(awsRequest,
                proxyClient.client()::describeACLs);

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(Translator.translateFromListRequest(describeAclResponse))
                .nextToken(describeAclResponse.nextToken())
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
