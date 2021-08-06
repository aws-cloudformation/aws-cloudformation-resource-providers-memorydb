package software.amazon.memorydb.acl;


import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    public static final String ID_WRONG_FORMAT = "ACL must begin with a letter; must contain only lowercase ASCII "
        + "letters, digits, and hyphens; and must not end with a hyphen or contain two consecutive hyphens.";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        ResourceModel resourceModel = request.getDesiredResourceState();
        logger.log(String.format("Resource model: %s", resourceModel.toString()));

        if (!resourceModel.getACLName().matches("[a-z][a-z0-9\\\\-]*")) {
            throw new CfnInvalidRequestException(ID_WRONG_FORMAT);
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-MemoryDB-ACL::Create", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createACL)))
                    .stabilize(
                        (updateUserRequest, updateUserResponse, proxyInvocation, model, context) -> isAclStabilized(
                            proxyInvocation, model, logger))
                    .progress()
            ).then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
