package software.amazon.cornerstone.cluster;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;

import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.awssdk.services.cornerstone.model.Cluster;
import software.amazon.awssdk.services.cornerstone.model.ClusterNotFoundException;
import software.amazon.awssdk.services.cornerstone.model.DescribeClustersResponse;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

    private static final String MESSAGE_FORMAT_FAILED_TO_STABILIZE = "Cluster %s failed to stabilize.";
    protected static final Constant STABILIZATION_DELAY = Constant.of().timeout(Duration.ofHours(1L)).delay(Duration.ofSeconds(60)).build();
    protected static final BiFunction<ResourceModel, ProxyClient<CornerstoneClient>, ResourceModel> EMPTY_CALL = (model, proxyClient) -> model;
    protected static String STABILIZED_STATUS = "available";

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                             final ResourceHandlerRequest<ResourceModel> request,
                                                                             final CallbackContext callbackContext,
                                                                             final Logger logger) {
        return handleRequest(proxy, request, callbackContext != null ? callbackContext : new CallbackContext(), proxy.newProxy(ClientBuilder::getClient),
                             logger);
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                                   final ResourceHandlerRequest<ResourceModel> request,
                                                                                   final CallbackContext callbackContext,
                                                                                   final ProxyClient<CornerstoneClient> proxyClient,
                                                                                   final Logger logger);

    protected ProgressEvent<ResourceModel, CallbackContext> waitForClusterAvailableStatus(final AmazonWebServicesClientProxy proxy,
                                                                                          final ProxyClient<CornerstoneClient> proxyClient,
                                                                                          final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                                          final Logger logger) {

        return proxy.initiate("AWS-Cornerstone-Cluster::stabilizeCluster", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Function.identity()).backoffDelay(STABILIZATION_DELAY).makeServiceCall(EMPTY_CALL)
                    .stabilize((resourceModel, response, client, model, callbackContext) -> isStabilized(proxy, client, model, logger)).progress();
    }

    protected Boolean isStabilized(final AmazonWebServicesClientProxy proxy,
                                   final ProxyClient<CornerstoneClient> client,
                                   final ResourceModel model,
                                   final Logger logger) {
        final Cluster cluster = getCluster(proxy, client, model);
        return STABILIZED_STATUS.equalsIgnoreCase(cluster.status());
    }

    public Cluster getCluster(final AmazonWebServicesClientProxy proxy,
                              final ProxyClient<CornerstoneClient> client,
                              final ResourceModel model) {
        try {
            final DescribeClustersResponse response =
                    proxy.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), client.client()::describeClusters);
            return response.clusters().stream().findFirst().get();
        } catch (ClusterNotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (Exception e) {
            throw new CfnNotStabilizedException(MESSAGE_FORMAT_FAILED_TO_STABILIZE, model.getName(), e);
        }
    }

}
