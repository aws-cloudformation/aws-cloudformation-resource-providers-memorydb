package software.amazon.memorydb.subnetgroup;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.SubnetGroupInUseException;
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

import java.security.InvalidParameterException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  protected static final Constant STABILIZATION_DELAY = Constant.of()
          .timeout(Duration.ofHours(1L))
          .delay(Duration.ofSeconds(60))
          .build();

  static final String NAME_REQUIRED_FOR_SUBNET_GROUP = "Name is required for subnet group creation";
  static final String SUBNET_IDS_REQUIRED_FOR_SUBNET_GROUP = "Atleast two journal supported AZs subnet ids are required";
  public static final String ID_WRONG_FORMAT = "Name must begin with a letter; must contain only lowercase ASCII "
          + "letters, digits, and hyphens; and must not end with a hyphen or contain two consecutive hyphens.";
  protected static final String UPDATE_FAILED_WITH_STABILIZATION_SUCCESS =
          "Update operation failed due to internal error. Please retry the operation";

  protected static final BiFunction<ResourceModel, ProxyClient<MemoryDbClient>, ResourceModel> EMPTY_CALL = (model, proxyClient) -> model;

  protected <T> T handleExceptions(Supplier<T> call) {
    try {
      return call.get();
    } catch (final InvalidParameterException | InvalidParameterValueException | InvalidParameterCombinationException e) {
      throw new CfnInvalidRequestException(e);
    } catch (final SubnetGroupAlreadyExistsException e) {
      throw new CfnAlreadyExistsException(e);
    } catch (final SubnetGroupInUseException e) {
      throw new CfnNotStabilizedException(e);
    } catch (final SubnetGroupNotFoundException e) {
      throw new CfnNotFoundException(e);
    } catch (final BaseHandlerException e) {
      throw e;
    } catch (final Exception e) {
      throw new CfnGeneralServiceException(e);
    }
  }

  protected ProgressEvent<ResourceModel, CallbackContext> waitForSubnetGroupAvailableStatus(final AmazonWebServicesClientProxy proxy,
                                                                                            final ProxyClient<MemoryDbClient> proxyClient,
                                                                                            final ProgressEvent<ResourceModel, CallbackContext> progress) {

    return proxy.initiate("AWS-MemoryDB-SubnetGroup::stabilizeSubnetGroup", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Function.identity()).backoffDelay(STABILIZATION_DELAY).makeServiceCall(EMPTY_CALL)
            .stabilize((resourceModel, response, client, model, callbackContext) -> isStabilized(proxy, client, model)).progress();
  }

  protected Boolean isStabilized(final AmazonWebServicesClientProxy proxy,
                                 final ProxyClient<MemoryDbClient> client,
                                 final ResourceModel model) {
    try {
      final SubnetGroup subnetGroup = getSubnetGroup(proxy, client, model);
      return subnetGroup != null;
    } catch (SubnetGroupNotFoundException e) {
      throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
    } catch (Exception e) {
      throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getSubnetGroupName(), e);
    }
  }

  public SubnetGroup getSubnetGroup(final AmazonWebServicesClientProxy proxy,
                                    final ProxyClient<MemoryDbClient> client,
                                    final ResourceModel model) {
    try {
      final DescribeSubnetGroupsResponse response =
              proxy.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model), client.client()::describeSubnetGroups);
      return response.subnetGroups().stream().findFirst().get();
    } catch (SubnetGroupNotFoundException e) {
      throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
    } catch (Exception e) {
      throw new CfnServiceInternalErrorException(e);
    }
  }


  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
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

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final ProxyClient<MemoryDbClient> proxyClient,
    final Logger logger);

  protected boolean isArnPresent(ResourceModel model) {
    return model.getARN() != null && !model.getARN().isEmpty();
  }

  protected ProgressEvent<ResourceModel, CallbackContext> describeSubnetGroups(
          AmazonWebServicesClientProxy proxy,
          ProgressEvent<ResourceModel, CallbackContext> progress,
          ProxyClient<MemoryDbClient> proxyClient
  ) {
    return proxy
            .initiate("AWS-MemoryDB-SubnetGroup::Describe", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToReadRequest)
            .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest, client.client()::describeSubnetGroups)))
            .done((describeSubnetGroupsRequest, describeSubnetGroupsResponse, proxyInvocation, resourceModel, context) ->
                    ProgressEvent.progress(Translator.translateFromReadResponse(describeSubnetGroupsResponse), context));
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
            .initiate("AWS-MemoryDB-SubnetGroup::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
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
