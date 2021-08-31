package software.amazon.memorydb.parametergroup;

import static software.amazon.memorydb.parametergroup.Translator.mapToTags;
import static software.amazon.memorydb.parametergroup.Translator.translateTagsFromSdk;

import com.amazonaws.util.StringUtils;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParametersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.Parameter;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UpdateHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<MemoryDbClient> proxyClient,
            final Logger logger) {
        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        return ProgressEvent.progress(desiredResourceState, callbackContext)
                .then(progress -> updateParameterGroup(proxy, proxyClient, progress, request))
                .then(progress -> waitForStabilize(proxy, proxyClient, progress, request))
                .then(progress -> describeParameterGroups(proxy, progress, proxyClient))
                .then(progress -> tagResource(proxy, proxyClient, progress, request, logger))
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }

    private boolean isUpdateNeeded(final Map<String, String> desiredResourceTags,
                                   final Map<String, String> currentResourceTags) {
        return Translator.isModified(desiredResourceTags, currentResourceTags);
    }

    private boolean isUpdateNeeded(final ResourceModel desiredResourceState,
                                   final ResourceModel currentResourceState) {
        return Translator.isModified(desiredResourceState.getParameters(), currentResourceState.getParameters());
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

    protected ProgressEvent<ResourceModel, CallbackContext> waitForStabilize(final AmazonWebServicesClientProxy proxy,
                                                                             final ProxyClient<MemoryDbClient> proxyClient,
                                                                             final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                             final ResourceHandlerRequest<ResourceModel> request) {

        if (!isUpdateNeeded(request.getDesiredResourceState(), request.getPreviousResourceState())) {
            return progress; // if same params then skip stabilization
        }
        final CallbackContext cxt = progress.getCallbackContext();
        try {
            if (!cxt.isClusterStabilized()) { // if not stabilized then we keep describing clusters and memoizing the nextToken into the set

                final DescribeClustersResponse describeClustersResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.translateToDescribeClustersRequest(cxt.getNextToken()), proxyClient.client()::describeClusters);

                List<Cluster> clusters = describeClustersResponse.clusters();
                if ((clusters == null) || (clusters != null && clusters.isEmpty())) {
                    cxt.setClusterStabilized(true);
                    return progress;
                }

                if (clusters.stream()
                        .filter(cluster -> cluster.parameterGroupName().equals(request.getDesiredResourceState().getParameterGroupName())) // all db clusters that use param group
                        .allMatch(dbCluster -> STABILIZED_STATUS.equals(dbCluster.parameterGroupStatus()))) { // if all stabilized then move to the next page

                    if (describeClustersResponse.nextToken() != null) { // more pages left
                        cxt.setNextToken(describeClustersResponse.nextToken());
                        progress.setCallbackDelaySeconds(CALLBACK_DELAY); // if there are more to describe
                    } else { // nothing left to stabilized
                        cxt.setClusterStabilized(true);
                    }
                } else {
                    progress.setCallbackDelaySeconds(CALLBACK_DELAY); // if some still in transition status need some delay to describe
                }
            }
            progress.setCallbackContext(cxt);
            return progress;
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateParameterGroup(final AmazonWebServicesClientProxy proxy,
                                                                                 final ProxyClient<MemoryDbClient> proxyClient,
                                                                                 final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                                 final ResourceHandlerRequest<ResourceModel> request) {
        try {

            if (!isUpdateNeeded(request.getDesiredResourceState(), request.getPreviousResourceState())) {
                return progress;
            }

            Map<String, Object> specifiedParamsToUpdate = request.getDesiredResourceState().getParameters();

            Map<String, Object> existingParams = request.getPreviousResourceState().getParameters();

            Set<String> removedParamKeys = existingParams != null && !existingParams.isEmpty() ?
                    existingParams.entrySet().stream().filter(existingParam ->
                            !specifiedParamsToUpdate.containsKey(existingParam.getKey()))
                            .map(removedParam -> removedParam.getKey())
                            .collect(Collectors.toSet()) : null;

            final List<Parameter> finalParamsToUpdate = specifiedParamsToUpdate
                    .entrySet()
                    .stream()
                    .map(kv -> Parameter.builder()
                            .name(kv.getKey())
                            .value(String.valueOf(kv.getValue()))
                            .build())
                    .collect(Collectors.toList());

            //get the default parameter values for all the params which were removed from the desired state
            if(removedParamKeys != null && !removedParamKeys.isEmpty()) {
                finalParamsToUpdate.addAll(getDefaultParametersForRemovedParams(removedParamKeys, proxyClient, progress));
            }

            //initiate parameter-group update
            return proxy.initiate("AWS-memorydb-ParameterGroup::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(model -> Translator.translateToUpdateRequest(model, finalParamsToUpdate))
                    .backoffDelay(STABILIZATION_DELAY)
                    .makeServiceCall((awsRequest, proxyInvocation) -> handleExceptions(() -> proxyInvocation.injectCredentialsAndInvokeV2(awsRequest, proxyInvocation.client()::updateParameterGroup)))
                    .progress();
        } catch (BaseHandlerException e) {
            throw e;
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }

    Set<Parameter> getDefaultParametersForRemovedParams(final Set<String> removedParamKeys,
                                                        final ProxyClient<MemoryDbClient> proxyClient,
                                                        final ProgressEvent<ResourceModel, CallbackContext> progress) {
        Set<Parameter> removedParams;
        String nextToken = null;
        final ParameterGroup paramGroup = proxyClient.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(progress.getResourceModel()), proxyClient.client()::describeParameterGroups).parameterGroups().get(0);
        do {
            try {
                final DescribeParametersResponse describeParametersResponse = proxyClient.injectCredentialsAndInvokeV2(
                        Translator.translateToDescribeParametersRequest(DEFAULT_PARAMETER_GROUP_NAME_PREFIX + paramGroup.family(), nextToken), proxyClient.client()::describeParameters);

                nextToken = describeParametersResponse.nextToken();
                removedParams = describeParametersResponse.parameters().stream().filter(defaultParam -> removedParamKeys.contains(defaultParam.name())).collect(Collectors.toSet());
                if (removedParams != null && removedParams.size() == removedParamKeys.size()) {
                    break;
                }
            } catch (final ParameterGroupNotFoundException e) {
                throw new CfnNotFoundException(e);
            } catch (final Exception e) {
                throw new CfnGeneralServiceException(e);
            }
        } while (!StringUtils.isNullOrEmpty(nextToken));
        return removedParams;
    }
}
