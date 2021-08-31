package software.amazon.memorydb.subnetgroup;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static software.amazon.memorydb.subnetgroup.Translator.mapToTags;
import static software.amazon.memorydb.subnetgroup.Translator.translateTagsFromSdk;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> updateSubnetGroup(proxy, proxyClient, progress, request, SubnetGroupUpdateFieldType.DESCRIPTION, logger))
                .then(progress -> updateSubnetGroup(proxy, proxyClient, progress, request, SubnetGroupUpdateFieldType.SUBNET_IDS, logger))
                .then(progress -> describeSubnetGroups(proxy, progress, proxyClient))
                .then(progress -> tagResource(proxy, proxyClient, progress, request, logger))
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }

    ProgressEvent<ResourceModel, CallbackContext> updateSubnetGroup(final AmazonWebServicesClientProxy proxy,
                                                                final ProxyClient<MemoryDbClient> proxyClient,
                                                                final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                final ResourceHandlerRequest<ResourceModel> request,
                                                                final SubnetGroupUpdateFieldType fieldType,
                                                                final Logger logger) {

        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        final ResourceModel currentResourceState = request.getPreviousResourceState();

        if (!isUpdateNeeded(desiredResourceState, currentResourceState, fieldType, logger)) {
            return progress;
        }

        return updateSubnetGroup(proxy, proxyClient, progress, desiredResourceState, fieldType, logger);
    }

    private boolean isUpdateNeeded(final Map<String, String> desiredResourceTags,
                                   final Map<String, String> currentResourceTags) {
        return Translator.isModified(desiredResourceTags, currentResourceTags);
    }

    private boolean isUpdateNeeded(final ResourceModel desiredResourceState,
                                   final ResourceModel currentResourceState,
                                   final SubnetGroupUpdateFieldType fieldType,
                                   final Logger logger) {
        boolean isModified;
        switch (fieldType) {
            case DESCRIPTION:
                isModified = Translator.isModified(desiredResourceState.getDescription(), currentResourceState.getDescription());
                break;
            case SUBNET_IDS:
                isModified = Translator.isModified(desiredResourceState.getSubnetIds(), currentResourceState.getSubnetIds());
                break;
            default:
                logger.log(String.format("Modification type [%s] not supported", fieldType));
                throw new CfnInternalFailureException();
        }
        return isModified;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> tagResource(final AmazonWebServicesClientProxy proxy,
                                                                        final ProxyClient<MemoryDbClient> proxyClient,
                                                                        final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                        final ResourceHandlerRequest<ResourceModel> request,
                                                                        final Logger logger) {
        logger.log("Previous Resource Tags : " + request.getPreviousResourceTags());
        logger.log("Desired Resource Tags : " + request.getDesiredResourceTags());
        if (!isUpdateNeeded(request.getDesiredResourceTags(), request.getPreviousResourceTags()) || !isArnPresent(progress.getResourceModel())) {
            logger.log("No tags to update.");
            return listTags(proxy, progress, proxyClient);
        }

        return progress.then(o -> handleExceptions(() -> {
            tagResource(proxy, proxyClient,  progress.getResourceModel(), progress.getCallbackContext(), request.getDesiredResourceTags());
            return ProgressEvent.progress(o.getResourceModel(), o.getCallbackContext()); })
        );
    }

    private ProgressEvent<ResourceModel, CallbackContext> tagResource(final AmazonWebServicesClientProxy proxy,
                                                                      final ProxyClient<MemoryDbClient> proxyClient,
                                                                      final ResourceModel model,
                                                                      final CallbackContext callbackContext,
                                                                      final Map<String, String> tags) {
        final String arn = model.getARN();
        final Set<Tag> currentTags = mapToTags(tags);
        final Set<Tag> existingTags = listTags(proxy, proxyClient, model, callbackContext);
        final Set<Tag> tagsToRemove = Sets.difference(existingTags, currentTags);
        final Set<Tag> tagsToAdd = Sets.difference(currentTags, existingTags);

        if (CollectionUtils.isNotEmpty(tagsToRemove)) {
            UntagResourceResponse untagResourceResponse = proxy.injectCredentialsAndInvokeV2(Translator.translateToUntagResourceRequest(arn, tagsToRemove), proxyClient.client()::untagResource);
            model.setTags(translateTagsFromSdk(untagResourceResponse.tagList()));
        }

        if (CollectionUtils.isNotEmpty(tagsToAdd)) {
            TagResourceResponse tagResourceResponse = proxy.injectCredentialsAndInvokeV2(Translator.translateToTagResourceRequest(arn, tagsToAdd), proxyClient.client()::tagResource);
            model.setTags(translateTagsFromSdk(tagResourceResponse.tagList()));
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    private Set<Tag> listTags(final AmazonWebServicesClientProxy proxy,
                              final ProxyClient<MemoryDbClient> proxyClient,
                              final ResourceModel model,
                              final CallbackContext callbackContext) {
        return Optional.ofNullable(ProgressEvent.progress(model, callbackContext)
                .then(progress -> listTags(proxy, progress, proxyClient))
                .getResourceModel()
                .getTags())
                .orElse(Collections.emptySet());
    }

    ProgressEvent<ResourceModel, CallbackContext> updateSubnetGroup(final AmazonWebServicesClientProxy proxy,
                                                                final ProxyClient<MemoryDbClient> proxyClient,
                                                                final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                final ResourceModel desiredResourceState,
                                                                final SubnetGroupUpdateFieldType fieldType,
                                                                final Logger logger) {

        return proxy.initiate("AWS-memorydb-SubnetGroup::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToUpdateRequest(model, fieldType))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memoryDbClientProxyClient) -> handleExceptions(() ->
                        memoryDbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memoryDbClientProxyClient.client()::updateSubnetGroup)))
                .progress();
    }
}
