package software.amazon.memorydb.parametergroup;

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

    static final String NAME_REQUIRED_FOR_PARAMETER_GROUP = "Name is required for parameter-group creation";
    static final String FAMILY_REQUIRED_FOR_PARAMETER_GROUP = "Family is required for parameter-group creation";
    public static final String ID_WRONG_FORMAT = "Name must begin with a letter; must contain only lowercase ASCII "
            + "letters, digits, and hyphens; and must not end with a hyphen or contain two consecutive hyphens.";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel desiredResourceState = request.getDesiredResourceState();

        try {
            Validate.isTrue(desiredResourceState.getParameterGroupName() != null, NAME_REQUIRED_FOR_PARAMETER_GROUP);
            Validate.isTrue(desiredResourceState.getFamily() != null, FAMILY_REQUIRED_FOR_PARAMETER_GROUP);

            if (!desiredResourceState.getParameterGroupName().matches("[a-z][a-z0-9\\\\-]*")) {
                throw new CfnInvalidRequestException(ID_WRONG_FORMAT);
            }

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
                .makeServiceCall((awsRequest, memorydbClientProxyClient) -> handleExceptions(() -> memorydbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memorydbClientProxyClient.client()::createParameterGroup)))
                .progress();
    }
}
