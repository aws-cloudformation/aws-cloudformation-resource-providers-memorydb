package software.amazon.memorydb.parametergroup;

import org.apache.commons.lang3.Validate;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;

import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Map;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    static final String FAMILY_REQUIRED_FOR_PARAMETER_GROUP = "Family is required for parameter-group creation";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel desiredResourceState = request.getDesiredResourceState();

        try {
            Validate.isTrue(desiredResourceState.getFamily() != null, FAMILY_REQUIRED_FOR_PARAMETER_GROUP);
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e.getMessage());
        }

        return ProgressEvent.progress(desiredResourceState, callbackContext).then(progress -> createParameterGroup(proxy, proxyClient, progress, request.getDesiredResourceTags()))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createParameterGroup(final AmazonWebServicesClientProxy proxy,
                                                                               final ProxyClient<MemoryDbClient> proxyClient,
                                                                               final ProgressEvent<ResourceModel, CallbackContext> progress, Map<String, String> tags) {

        return proxy.initiate("AWS-memorydb-ParameterGroup::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest((resourceModel) -> Translator.translateToCreateRequest(resourceModel, tags))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memorydbClientProxyClient) -> {
                    try {
                        return memorydbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memorydbClientProxyClient.client()::createParameterGroup);
                    } catch (final ParameterGroupAlreadyExistsException e) {
                        throw new CfnAlreadyExistsException(e);
                    } catch (final InvalidParameterValueException | InvalidParameterCombinationException e) {
                        throw new CfnInvalidRequestException(e);
                    } catch (final Exception e) {
                        throw new CfnGeneralServiceException(e);
                    }
                }).progress();
    }
}
