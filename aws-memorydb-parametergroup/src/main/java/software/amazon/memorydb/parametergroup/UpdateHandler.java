package software.amazon.memorydb.parametergroup;

import static software.amazon.memorydb.parametergroup.Translator.mapToTags;

import com.amazonaws.util.StringUtils;
import com.google.common.collect.Sets;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParametersResponse;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterGroupStateException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.ListTagsResponse;
import software.amazon.awssdk.services.memorydb.model.Parameter;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.Map;
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
                .then(progress -> tagResource(proxy, proxyClient, progress, request))
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
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
                                                                        final ResourceHandlerRequest<ResourceModel> request) {

        if (!isUpdateNeeded(request.getDesiredResourceTags(), request.getPreviousResourceTags())) {
            return progress;
        }
        return describeClusterParameterGroup(proxy, proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .done((paramGroupRequest, paramGroupResponse, rdsProxyClient, resourceModel, cxt) ->
                        tagResource(paramGroupResponse, proxyClient, resourceModel, cxt, request.getDesiredResourceTags()));

    }

    private ProgressEvent<ResourceModel, CallbackContext> tagResource(final DescribeParameterGroupsResponse describeParameterGroupsResponse,
                                                                      final ProxyClient<MemoryDbClient> proxyClient,
                                                                      final ResourceModel model,
                                                                      final CallbackContext callbackContext,
                                                                      final Map<String, String> tags) {

        final String arn = describeParameterGroupsResponse.parameterGroups().stream().findFirst().get().arn();
        final Set<Tag> currentTags = mapToTags(tags);
        final Set<Tag> existingTags = listTags(proxyClient, arn);
        final Set<Tag> tagsToRemove = Sets.difference(existingTags, currentTags);
        final Set<Tag> tagsToAdd = Sets.difference(currentTags, existingTags);

        try {
            if (tagsToRemove != null && !tagsToRemove.isEmpty()) {
                proxyClient.injectCredentialsAndInvokeV2(Translator.translateToUntagResourceRequest(arn, tagsToRemove), proxyClient.client()::untagResource);
            }

            if (tagsToAdd != null && !tagsToAdd.isEmpty()) {
                proxyClient.injectCredentialsAndInvokeV2(Translator.translateToTagResourceRequest(arn, tagsToAdd), proxyClient.client()::tagResource);
            }

            return ProgressEvent.progress(model, callbackContext);
        } catch (ParameterGroupNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }

    protected Set<Tag> listTags(final ProxyClient<MemoryDbClient> proxyClient,
                                final String arn) {
        try {
            final ListTagsResponse listTagsResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.translateToListTagsRequest(arn), proxyClient.client()::listTags);
            return Translator.translateTagsFromSdk(listTagsResponse.tagList());
        } catch (ParameterGroupNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }
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
                        .filter(cluster -> cluster.parameterGroupName().equals(request.getDesiredResourceState().getName())) // all db clusters that use param group
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
                    .makeServiceCall((awsRequest, proxyInvocation) -> {
                        try {
                            return proxyInvocation.injectCredentialsAndInvokeV2(awsRequest, proxyInvocation.client()::updateParameterGroup);
                        } catch (final ParameterGroupNotFoundException e) {
                            throw new CfnNotFoundException(e);
                        } catch (final InvalidParameterValueException | InvalidParameterCombinationException e) {
                            throw new CfnInvalidRequestException(e);
                        } catch (final InvalidParameterGroupStateException e) {
                            throw new CfnNotStabilizedException(e);
                        } catch (final Exception e) {
                            throw e;
                        }
                    })
                    .progress();
        } catch (final Exception e) {
            if (e instanceof BaseHandlerException) {
                throw e;
            }
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
