package software.amazon.memorydb.subnetgroup;

import org.apache.commons.lang3.Validate;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupNotFoundException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
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

        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        logger.log("Input Model: ");
        logger.log(desiredResourceState.toString());

        try {
            logger.log(String.format("Resource model: %s", desiredResourceState.toString()));
            Validate.isTrue(desiredResourceState.getSubnetGroupName() != null, NAME_REQUIRED_FOR_SUBNET_GROUP);
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e.getMessage());
        }

        return ProgressEvent.progress(desiredResourceState, callbackContext)
                .then(progress -> deleteSubnetGroup(proxy, proxyClient, progress, request, logger));

    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteSubnetGroup(final AmazonWebServicesClientProxy proxy,
                                                                            final ProxyClient<MemoryDbClient> proxyClient,
                                                                            final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                            final ResourceHandlerRequest<ResourceModel> request,
                                                                            final Logger logger) {

        return proxy.initiate("AWS-memorydb-SubnetGroup::Delete", proxyClient, request.getDesiredResourceState(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteSubnetGroup)))
                .stabilize((awsRequest, awsResponse, client, model, context) -> isDeleted(proxyClient, model))
                .done((deleteSubnetGroupRequest, deleteSubnetGroupResponse, proxyInvocation, model, context) -> ProgressEvent
                        .defaultSuccessHandler(null));
    }

    private Boolean isDeleted(final ProxyClient<MemoryDbClient> proxyClient,
                              final ResourceModel model) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), proxyClient.client()::describeSubnetGroups);
            return false;
        } catch (SubnetGroupNotFoundException e) {
            return true;
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }
}
