package software.amazon.memorydb.subnetgroup;

import org.apache.commons.lang3.Validate;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Map;


public class CreateHandler extends BaseHandlerStd {
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
            Validate.isTrue(desiredResourceState.getSubnetIds() != null, SUBNET_IDS_REQUIRED_FOR_SUBNET_GROUP);

            if (!desiredResourceState.getSubnetGroupName().matches("[a-z][a-z0-9\\\\-]*")) {
                throw new CfnInvalidRequestException(ID_WRONG_FORMAT);
            }
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e.getMessage());
        }

        return ProgressEvent.progress(desiredResourceState, callbackContext)
                .then(progress -> createSubnetGroup(proxy, proxyClient, progress, request.getDesiredResourceTags()))
                .then(progress -> waitForSubnetGroupAvailableStatus(proxy, proxyClient, progress))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createSubnetGroup(final AmazonWebServicesClientProxy proxy,
                                                                            final ProxyClient<MemoryDbClient> proxyClient,
                                                                            final ProgressEvent<ResourceModel, CallbackContext> progress, Map<String, String> tags) {
        return proxy.initiate("AWS-memorydb-SubnetGroup::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest((resourceModel) -> Translator.translateToCreateRequest(resourceModel, tags))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memorydbClientProxyClient) -> handleExceptions(() ->
                        memorydbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memorydbClientProxyClient.client()::createSubnetGroup)))
                .progress();
    }

}
