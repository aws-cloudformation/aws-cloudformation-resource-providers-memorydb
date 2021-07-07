package software.amazon.memorydb.cluster;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ClusterAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupNotFoundException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Map;


public class CreateHandler extends BaseHandlerStd {

    static final String NODE_TYPE_REQUIRED_FOR_CLUSTER = "Node type is required for cluster creation";
    static final String SUBNET_GROUP_REQUIRED = "Subnet group is required for cluster creation";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        final ResourceModel desiredResourceState = request.getDesiredResourceState();

        try {
            Validate.isTrue(desiredResourceState.getNodeType() != null, NODE_TYPE_REQUIRED_FOR_CLUSTER);
            Validate.isTrue(StringUtils.isNotEmpty(desiredResourceState.getSubnetGroupName()), SUBNET_GROUP_REQUIRED);
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e.getMessage());
        }

        return ProgressEvent.progress(desiredResourceState, callbackContext).then(progress -> createCluster(proxy, proxyClient, progress, request.getDesiredResourceTags()))
                .then(progress -> waitForClusterAvailableStatus(proxy, proxyClient, progress, logger))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createCluster(final AmazonWebServicesClientProxy proxy,
                                                                        final ProxyClient<MemoryDbClient> proxyClient,
                                                                        final ProgressEvent<ResourceModel, CallbackContext> progress, Map<String, String> tags) {

        return proxy.initiate("AWS-memorydb-Cluster::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest((resourceModel) -> Translator.translateToCreateRequest(resourceModel, tags))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memorydbClientProxyClient) -> {
                    try {
                        return memorydbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memorydbClientProxyClient.client()::createCluster);
                    } catch (final ClusterAlreadyExistsException e) {
                        throw new CfnAlreadyExistsException(e);
                    } catch (final InvalidParameterValueException | InvalidParameterCombinationException e) {
                        throw new CfnInvalidRequestException(e);
                    } catch (final SubnetGroupNotFoundException e) {
                        throw new CfnNotFoundException(e);
                    } catch (final Exception e) {
                        throw new CfnGeneralServiceException(e);
                    }
                }).progress();
    }

}
