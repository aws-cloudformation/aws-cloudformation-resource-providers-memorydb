package software.amazon.memorydb.cluster;

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

    static final String NAME_REQUIRED_FOR_CLUSTER = "Name is required for cluster creation";
    static final String NODE_TYPE_REQUIRED_FOR_CLUSTER = "Node type is required for cluster creation";
    static final String ACL_NAME_REQUIRED_FOR_CLUSTER = "ACL name is required for cluster creation";
    public static final String ID_WRONG_FORMAT = "Name must begin with a letter; must contain only lowercase ASCII "
            + "letters, digits, and hyphens; and must not end with a hyphen or contain two consecutive hyphens.";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        final ResourceModel desiredResourceState = request.getDesiredResourceState();

        try {

            logger.log(String.format("Resource model: %s", desiredResourceState.toString()));
            Validate.isTrue(desiredResourceState.getClusterName() != null, NAME_REQUIRED_FOR_CLUSTER);
            Validate.isTrue(desiredResourceState.getNodeType() != null, NODE_TYPE_REQUIRED_FOR_CLUSTER);
            Validate.isTrue(desiredResourceState.getACLName() != null, ACL_NAME_REQUIRED_FOR_CLUSTER);

            if (!desiredResourceState.getClusterName().matches("[a-z][a-z0-9\\\\-]*")) {
                throw new CfnInvalidRequestException(ID_WRONG_FORMAT);
            }


        } catch (Exception e) {
            throw new CfnInvalidRequestException(e.getMessage());
        }


        return ProgressEvent.progress(desiredResourceState, callbackContext).then(progress -> createCluster(proxy, proxyClient, progress, request.getDesiredResourceTags()))
                .then(progress -> waitForClusterAvailableStatus(proxy, proxyClient, progress))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCluster(final AmazonWebServicesClientProxy proxy,
                                                                        final ProxyClient<MemoryDbClient> proxyClient,
                                                                        final ProgressEvent<ResourceModel, CallbackContext> progress, Map<String, String> tags) {

        return proxy.initiate("AWS-memorydb-Cluster::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest((resourceModel) -> Translator.translateToCreateRequest(resourceModel, tags))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memorydbClientProxyClient) -> handleExceptions(() ->
                        memorydbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memorydbClientProxyClient.client()::createCluster)))
                .progress();
    }

}
