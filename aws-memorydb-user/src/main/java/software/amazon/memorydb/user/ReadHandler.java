package software.amazon.memorydb.user;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        logger.log(String.format("%s read handler is being invoked", ResourceModel.TYPE_NAME));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> describeUser(proxy, progress, proxyClient))
            .then(progress -> listTags(proxy, progress, proxyClient))
            .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }

    private ProgressEvent<ResourceModel, CallbackContext> describeUser(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy
            .initiate("AWS-MemoryDB-User::Describe", proxyClient, progress.getResourceModel(),
                progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToReadRequest)
            .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeUsers)))
            .done((describeUserRequest, describeUserResponse, proxyInvocation, resourceModel, context) ->
                ProgressEvent.progress(Translator.translateFromReadResponse(describeUserResponse), context));
    }

    private ProgressEvent<ResourceModel, CallbackContext> listTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy
            .initiate("AWS-MemoryDB-User::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToListTagsRequest)
            .makeServiceCall((awsRequest, client) -> handleExceptions(() -> client.injectCredentialsAndInvokeV2(awsRequest, client.client()::listTags)))
            .done( (listTagsRequest, listTagsResponse, proxyInvocation, resourceModel, context) -> {
                    resourceModel.setTags(Translator.translateTags(listTagsResponse.tagList()));
                    return ProgressEvent.progress(resourceModel, context);
                }
            );
    }
}
