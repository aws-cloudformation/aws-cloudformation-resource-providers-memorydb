package software.amazon.memorydb.parametergroup;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterGroupStateException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.CallChain;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Supplier;

// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final Constant STABILIZATION_DELAY = Constant.of()
            // Set the timeout to something silly/way too high, because
            // we already set the timeout in the schema https://github.com/aws-cloudformation/aws-cloudformation-resource-schema
            .timeout(Duration.ofDays(365L))
            // Set the delay to 1 minutes so the stabilization code only calls
            // DescribeGlobalReplicationgroups every 1 minute - create takes
            // 10+ minutes so there's no need to check if the cluster is available more than every couple minutes.
            .delay(Duration.ofSeconds(60))
            .build();
    protected static final int CALLBACK_DELAY = 30;
    protected static final BiFunction<ResourceModel, ProxyClient<MemoryDbClient>, ResourceModel> EMPTY_CALL = (model, proxyClient) -> model;
    protected static String STABILIZED_STATUS = "in-sync";
    protected static String DEFAULT_PARAMETER_GROUP_NAME_PREFIX = "default.";

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {
        return handleRequest(
                proxy,
                request,
                callbackContext != null ? callbackContext : new CallbackContext(),
                proxy.newProxy(ClientBuilder::getClient),
                logger
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<MemoryDbClient> proxyClient,
            final Logger logger);

    protected CallChain.Completed<DescribeParameterGroupsRequest,
            DescribeParameterGroupsResponse,
            MemoryDbClient,
            ResourceModel,
            CallbackContext> describeClusterParameterGroup(final AmazonWebServicesClientProxy proxy,
                                                           final ProxyClient<MemoryDbClient> proxyClient,
                                                           final ResourceModel model,
                                                           final CallbackContext callbackContext) {
        return proxy.initiate("AWS-MemoryDB-ParameterGroup::Read", proxyClient, model, callbackContext)
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall((awsRequest, client) -> {
                    try {
                        return client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeParameterGroups);
                    } catch (final ParameterGroupNotFoundException e) {
                        throw new CfnNotFoundException(e);
                    }
                });
    }

    protected <T> T handleExceptions(Supplier<T> call) {
        try {
            return call.get();
        } catch (final InvalidParameterValueException | InvalidParameterCombinationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ParameterGroupAlreadyExistsException e) {
            throw new CfnAlreadyExistsException(e);
        } catch (final ParameterGroupNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final InvalidParameterGroupStateException e) {
            throw new CfnNotStabilizedException(e);
        } catch (final BaseHandlerException e) {
            throw e;
        } catch (final Exception e) {
            e.printStackTrace();
            throw new CfnGeneralServiceException(e);
        }
    }

    protected boolean isArnPresent(ResourceModel model) {
        return model.getARN() != null && !model.getARN().isEmpty();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> describeParameterGroups(
            AmazonWebServicesClientProxy proxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy
                .initiate("AWS-MemoryDB-ParameterGroup::Describe", proxyClient, progress.getResourceModel(),
                        progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeParameterGroups)))
                .done((describeUserRequest, describeClustersResponse, proxyInvocation, resourceModel, context) ->
                        ProgressEvent.progress(Translator.translateFromReadResponse(describeClustersResponse), context));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> listTags(
            AmazonWebServicesClientProxy proxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            ProxyClient<MemoryDbClient> proxyClient
    ) {

        if(!isArnPresent(progress.getResourceModel())) {
            return progress;
        }

        return proxy
                .initiate("AWS-MemoryDB-ParameterGroup::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToListTagsRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() -> client.injectCredentialsAndInvokeV2(awsRequest, client.client()::listTags)))
                .done( (listTagsRequest, listTagsResponse, proxyInvocation, resourceModel, context) -> {
                            if(CollectionUtils.isNotEmpty(listTagsResponse.tagList())) {
                                resourceModel.setTags(Translator.translateTags(listTagsResponse.tagList()));
                            }
                            return ProgressEvent.progress(resourceModel, context);
                        }
                );
    }
}
