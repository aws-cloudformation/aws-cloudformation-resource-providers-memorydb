package software.amazon.memorydb.user;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.InvalidUserStateException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-MemoryDB-User::Delete", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    .backoffDelay(STABILIZATION_DELAY)
                    .makeServiceCall((modelRequest, proxyInvocation) -> {
                        return handleExceptions(() -> {
                            try {
                                return proxyInvocation.injectCredentialsAndInvokeV2(modelRequest,
                                    proxyInvocation.client()::deleteUser);
                            } catch (final InvalidUserStateException e) {
                                //Out of band flow
                                return null;
                            }
                        });
                    })
                    .stabilize(
                        (deleteUserRequest, deleteUserResponse, proxyInvocation, model, context) -> isUserDeleted(
                            proxyInvocation, model, logger))
                    .done((deleteUserRequest, deleteUserResponse, proxyInvocation, model, context) -> ProgressEvent
                        .defaultSuccessHandler(null))
            );
    }

    protected boolean isUserDeleted(
        final ProxyClient<MemoryDbClient> proxyClient,
        final ResourceModel model,
        final Logger logger) {
        try {
            isUserStabilized(proxyClient, model, logger);
        } catch (CfnNotFoundException e) {
            return true;
        }
        return false;
    }
}
