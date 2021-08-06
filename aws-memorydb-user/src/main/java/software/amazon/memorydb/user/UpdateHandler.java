package software.amazon.memorydb.user;

import com.amazonaws.util.CollectionUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> updateUser(proxy, progress, proxyClient))
            .then(progress -> updateTags(proxy, progress, request, proxyClient))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateUser(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy.initiate("AWS-MemoryDB-User::Update", proxyClient, progress.getResourceModel(),
            progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToUpdateRequest)
            .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updateUser)))
            .stabilize(
                (updateUserRequest, updateUserResponse, proxyInvocation, model, context) -> isUserStabilized(
                    proxyInvocation, model, logger))
            .progress();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        return progress
            .then(o ->
                handleExceptions(() -> {
                    handleTagging(proxy, proxyClient,  request.getDesiredResourceTags(), progress.getResourceModel());
                    return ProgressEvent.progress(o.getResourceModel(), o.getCallbackContext());
                })
            );
    }

    private void handleTagging(AmazonWebServicesClientProxy proxy, ProxyClient<MemoryDbClient> client,
        final Map<String, String> tags, final ResourceModel model) {
        final Set<Tag> newTags = tags == null ? Collections.emptySet() : new HashSet<>(Translator.translateTags(tags));
        final Set<Tag> existingTags =
            new HashSet<>(
                Translator.translateTags(
                    proxy.injectCredentialsAndInvokeV2(
                        Translator.translateToListTagsRequest(model),
                        client.client()::listTags).tagList()));

        final List<Tag> tagsToRemove = existingTags.stream()
            .filter(tag -> !newTags.contains(tag))
            .collect(Collectors.toList());
        final List<Tag> tagsToAdd = newTags.stream()
            .filter(tag -> !existingTags.contains(tag))
            .collect(Collectors.toList());

        if (!CollectionUtils.isNullOrEmpty(tagsToRemove)) {
            proxy.injectCredentialsAndInvokeV2(
                Translator.translateToUntagResourceRequest(model.getArn(), tagsToRemove),
                client.client()::untagResource);
        }
        if (!CollectionUtils.isNullOrEmpty(tagsToAdd)) {
            proxy.injectCredentialsAndInvokeV2(
                Translator.translateToTagResourceRequest(model.getArn(), tagsToAdd),
                client.client()::tagResource);
        }
    }

}
