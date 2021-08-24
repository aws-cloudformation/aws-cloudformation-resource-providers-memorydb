package software.amazon.memorydb.cluster;

import java.security.InvalidParameterException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.AclNotFoundException;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.ClusterAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.InvalidClusterStateException;
import software.amazon.awssdk.services.memorydb.model.InvalidNodeStateException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.awssdk.services.memorydb.model.SnapshotAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupNotFoundException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final String UPDATE_FAILED_WITH_STABILIZATION_SUCCESS =
            "Update operation failed due to internal error. Please retry the operation";
    protected static final Constant STABILIZATION_DELAY = Constant.of()
        // Set the timeout to something silly/way too high, because
        // we already set the timeout in the schema https://github.com/aws-cloudformation/aws-cloudformation-resource-schema
        .timeout(Duration.ofDays(365L))
        // Set the delay to 1 minutes so the stabilization code only calls
        // DescribeClusters every 1 minute - create takes
        // 15+ minutes so there's no need to check if the cluster is available more than every couple minutes.
        .delay(Duration.ofSeconds(60))
        .build();
    protected static final BiFunction<ResourceModel, ProxyClient<MemoryDbClient>, ResourceModel> EMPTY_CALL = (model, proxyClient) -> model;
    protected static String STABILIZED_STATUS = "available";

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                             final ResourceHandlerRequest<ResourceModel> request,
                                                                             final CallbackContext callbackContext,
                                                                             final Logger logger) {
        try {
            return handleRequest(proxy, request, callbackContext != null ? callbackContext : new CallbackContext(), proxy.newProxy(ClientBuilder::getClient), logger);
        } catch (Exception e) {
            logger.log("Request Failed : " + e.getMessage() + Arrays.stream(e.getStackTrace())
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n")));
            throw e;
        }
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                                   final ResourceHandlerRequest<ResourceModel> request,
                                                                                   final CallbackContext callbackContext,
                                                                                   final ProxyClient<MemoryDbClient> proxyClient,
                                                                                   final Logger logger);

    protected ProgressEvent<ResourceModel, CallbackContext> waitForClusterAvailableStatus(final AmazonWebServicesClientProxy proxy,
                                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                                          final ProgressEvent<ResourceModel, CallbackContext> progress) {

        return proxy.initiate("AWS-MemoryDB-Cluster::stabilizeCluster", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Function.identity()).backoffDelay(STABILIZATION_DELAY).makeServiceCall(EMPTY_CALL)
                    .stabilize((resourceModel, response, client, model, callbackContext) -> isStabilized(proxy, client, model)).progress();
    }

    protected Boolean isStabilized(final AmazonWebServicesClientProxy proxy,
                                   final ProxyClient<MemoryDbClient> client,
                                   final ResourceModel model) {
        try {
            final Cluster cluster = getCluster(proxy, client, model);
            return STABILIZED_STATUS.equalsIgnoreCase(cluster.status());
        } catch (ClusterNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (Exception e) {
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getClusterName(), e);
        }
    }

    protected ResourceModel getCurrentState(final AmazonWebServicesClientProxy proxy,
                                            final ProxyClient<MemoryDbClient> client,
                                            final ResourceModel model) {
        final Cluster cluster = getCluster(proxy, client, model);
        return Translator.translateFromReadResponse(cluster);
    }

    public Cluster getCluster(final AmazonWebServicesClientProxy proxy,
                              final ProxyClient<MemoryDbClient> client,
                              final ResourceModel model) {
        try {
            final DescribeClustersResponse response =
                    proxy.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), client.client()::describeClusters);
            return response.clusters().stream().findFirst().get();
        } catch (ClusterNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (Exception e) {
            throw new CfnServiceInternalErrorException(e);
        }
    }

    protected ProgressEvent<ResourceModel, CallbackContext> describeClusters(
            AmazonWebServicesClientProxy proxy,
            ProgressEvent<ResourceModel, CallbackContext> progress,
            ProxyClient<MemoryDbClient> proxyClient
    ) {
        return proxy
                .initiate("AWS-MemoryDB-Cluster::Describe", proxyClient, progress.getResourceModel(),
                        progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                        client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeClusters)))
                .done((describeClustersRequest, describeClustersResponse, proxyInvocation, resourceModel, context) ->
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
                .initiate("AWS-MemoryDB-Cluster::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
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

    protected <T> T handleExceptions(Supplier<T> call) {
        try {
            return call.get();
        } catch (final InvalidParameterException | InvalidParameterValueException  | InvalidParameterCombinationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ClusterAlreadyExistsException | SnapshotAlreadyExistsException e) {
            throw new CfnAlreadyExistsException(e);
        } catch (final ClusterNotFoundException | SubnetGroupNotFoundException | ParameterGroupNotFoundException | AclNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final InvalidClusterStateException | InvalidNodeStateException e) {
            throw new CfnNotStabilizedException(e);
        } catch (final BaseHandlerException e) {
            throw e;
        } catch (final Exception e) {
            throw new CfnGeneralServiceException(e);
        }
    }

    protected boolean isArnPresent(ResourceModel model) {
        return model.getARN() != null && !model.getARN().isEmpty();
    }
}
