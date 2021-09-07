package software.amazon.memorydb.acl;

import com.amazonaws.util.CollectionUtils;
import com.amazonaws.util.StringUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ACL;
import software.amazon.awssdk.services.memorydb.model.AclNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.awssdk.services.memorydb.model.UpdateAclRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateAclRequest.Builder;
import software.amazon.awssdk.services.memorydb.model.UpdateAclResponse;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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
            .then(progress -> updateACL(proxy, progress,request, proxyClient))
            .then(progress -> updateTags(proxy, progress, request, proxyClient))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateACL(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        if (hasChangeOnCoreModel(request.getDesiredResourceState(), request.getPreviousResourceState())) {
            return proxy.initiate("AWS-MemoryDB-User::Update", proxyClient, progress.getResourceModel(),
                progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .makeServiceCall((awsRequest, proxyInvocation)  -> handleExceptions(() -> {
                        try {
                            //Get current acl
                            DescribeAcLsRequest describeRequest =
                                DescribeAcLsRequest.builder().aclName(awsRequest.aclName()).build();
                            final DescribeAcLsResponse describeResponse =
                                proxyInvocation.injectCredentialsAndInvokeV2(describeRequest,
                                    proxyInvocation.client()::describeACLs);

                            ACL acl = describeResponse.acLs().get(0);

                            //Create list of resources to add and remove
                            List<String> userIdsToAdd = progress.getResourceModel().getUserNames().stream()
                                .distinct()
                                .filter(((Predicate<String>) acl.userNames()::contains).negate())
                                .collect(Collectors.toList());

                            this.logger.log(acl.toString());

                            List<String> userIdsToRemove = acl.userNames().stream()
                                .distinct()
                                .filter(
                                    ((Predicate<String>) progress.getResourceModel().getUserNames()::contains).negate())
                                .collect(Collectors.toList());

                            Builder builder = UpdateAclRequest.builder();
                            builder.aclName(acl.name());
                            builder.userNamesToAdd(userIdsToAdd.isEmpty() ? null : userIdsToAdd);
                            builder.userNamesToRemove(userIdsToRemove.isEmpty() ? null : userIdsToRemove);
                            UpdateAclRequest updateRequest = builder.build();

                            //Update ACL
                            final UpdateAclResponse response =
                                proxyInvocation.injectCredentialsAndInvokeV2(updateRequest,
                                    proxyInvocation.client()::updateACL);

                            return response;
                        } catch (final AclNotFoundException e) {
                            throw new CfnNotFoundException(e);
                        } catch (final AwsServiceException e) {
                            throw new CfnGeneralServiceException(e);
                        }
                    }
                ))
                .stabilize(
                    (updateUserRequest, updateUserResponse, proxyInvocation, model, context) -> isAclStabilized(
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
        return !getRequestResource(r1).equals(getRequestResource(r2));
    }

    private ResourceModel getRequestResource(final ResourceModel resourceModel) {
        return ResourceModel.builder()
            .aCLName(resourceModel.getACLName())
            .userNames(resourceModel.getUserNames())
            .build();
    }

    private void setModelArn(AmazonWebServicesClientProxy proxy, ProxyClient<MemoryDbClient> client,
        final ResourceModel model) {
        if (StringUtils.isNullOrEmpty(model.getArn())) {
            DescribeAcLsResponse response = proxy.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(model),
                client.client()::describeACLs);
            if (response.acLs().size() > 0) {
                model.setArn(response.acLs().get(0).arn());
            }
        }
    }

}
