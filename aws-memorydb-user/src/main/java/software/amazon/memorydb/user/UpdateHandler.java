package software.amazon.memorydb.user;

import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.StringUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
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
            .then(progress -> updateUser(proxy, progress, request, proxyClient))
            .then(progress -> updateTags(proxy, progress, request, proxyClient))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateUser(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        if (hasChangeOnCoreModel(request.getDesiredResourceState(), request.getPreviousResourceState())) {
            return proxy.initiate("AWS-MemoryDB-User::Update", proxyClient, progress.getResourceModel(),
                progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updateUser)))
                .stabilize(
                    (updateUserRequest, updateUserResponse, proxyInvocation, model, context) -> isUserStabilized(
                        proxyInvocation, model, logger))
                .progress();
        } else {
            return progress;
        }

    }

    private ProgressEvent<ResourceModel, CallbackContext> updateTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        if (!request.getPreviousResourceTags().equals(request.getDesiredResourceTags())) {
            return progress
                .then(o ->
                    handleExceptions(() -> {
                        handleTagging(proxy, proxyClient,  request.getDesiredResourceTags(), progress.getResourceModel());
                        return ProgressEvent.progress(o.getResourceModel(), o.getCallbackContext());
                    })
                );
        } else {
            return progress;
        }
    }

    private void handleTagging(AmazonWebServicesClientProxy proxy, ProxyClient<MemoryDbClient> client,
        final Map<String, String> tags, final ResourceModel model) {

        final Set<Tag> newTags = tags == null ? Collections.emptySet() : new HashSet<>(Translator.translateTags(tags));
        final Set<Tag> existingTags = new HashSet<>();

        //Fix for unpopulated arn on resource model
        setModelArn(proxy, client, model);
        if (!StringUtils.isNullOrEmpty(model.getArn())) {
            existingTags.addAll(
                Translator.translateTags(
                    proxy.injectCredentialsAndInvokeV2(
                        Translator.translateToListTagsRequest(model),
                        client.client()::listTags).tagList()));
        }

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

    private boolean hasChangeOnCoreModel(final ResourceModel r1, final ResourceModel r2){
        return !getResourceWithoutTags(r1).equals(getResourceWithoutTags(r2));
    }

    private ResourceModel getResourceWithoutTags(final ResourceModel resourceModel) {
        return ResourceModel.builder()
            .status(resourceModel.getStatus())
            .userName(resourceModel.getUserName())
            .accessString(resourceModel.getAccessString())
            .authenticationMode(resourceModel.getAuthenticationMode())
            .arn(resourceModel.getArn())
            .build();
    }

    private void setModelArn(AmazonWebServicesClientProxy proxy, ProxyClient<MemoryDbClient> client,
        final ResourceModel model) {
        if (StringUtils.isNullOrEmpty(model.getArn())) {
            DescribeUsersResponse response = proxy.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(model),
                client.client()::describeUsers);
            if (response.users().size() > 0) {
                model.setArn(response.users().get(0).arn());
            }
        }
    }

}
