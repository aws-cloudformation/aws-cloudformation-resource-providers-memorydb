package software.amazon.memorydb.acl;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ACL;
import software.amazon.awssdk.services.memorydb.model.InvalidAclStateException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
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
            .then(progress -> checkAclIsAssociated(proxy, proxyClient, progress, request))
            .then(progress -> deleteAcl(proxy, proxyClient, progress, request, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> checkAclIsAssociated(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<MemoryDbClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final ResourceHandlerRequest<ResourceModel> request) {
        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        ACL acl = null;
        try {
            acl = getACL(proxy, proxyClient, desiredResourceState);
        } catch (CfnNotFoundException exception) {
            return progress;
        }

        if (!acl.clusters().isEmpty()) {
            throw new CfnGeneralServiceException(
                InvalidAclStateException.builder().message("Acl associated "
                    + "to a cluster can not deleted.").build());
        }

        return progress;
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteAcl(final AmazonWebServicesClientProxy proxy,
        final ProxyClient<MemoryDbClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final ResourceHandlerRequest<ResourceModel> request,
        final Logger logger) {
        return proxy.initiate("AWS-MemoryDB-ACL::Delete", proxyClient,
            request.getDesiredResourceState(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToDeleteRequest)
            .backoffDelay(STABILIZATION_DELAY)
            .makeServiceCall((modelRequest, proxyInvocation) -> {
                return handleExceptions(() -> {
                    try {
                        return proxyInvocation.injectCredentialsAndInvokeV2(modelRequest,
                            proxyInvocation.client()::deleteACL);
                    } catch (final InvalidAclStateException e) {
                        //Out of band flow
                        return null;
                    }
                });
            })
            .stabilize((deleteUserRequest, deleteUserResponse, proxyInvocation, model, context) -> isAclDeleted(
                proxyInvocation, model, logger))
            .done((deleteUserRequest, deleteUserResponse, proxyInvocation, model, context) -> ProgressEvent
                .defaultSuccessHandler(null));
    }

    protected boolean isAclDeleted(
        final ProxyClient<MemoryDbClient> proxyClient,
        final ResourceModel model,
        Logger logger) {
        try {
            isAclStabilized(proxyClient, model, logger);
        } catch (CfnNotFoundException e) {
            return true;
        }
        return false;
    }
}
