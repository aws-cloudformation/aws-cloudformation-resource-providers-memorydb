package software.amazon.memorydb.parametergroup;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ParameterGroupNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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

}
