package software.amazon.memorydb.acl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.security.InvalidParameterException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ACL;
import software.amazon.awssdk.services.memorydb.model.AclAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.AclNotFoundException;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.UserNotFoundException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  private static final String MESSAGE_FORMAT_FAILED_TO_STABILIZE = "ACL %s failed to stabilize.";
  @VisibleForTesting
  static Constant STABILIZATION_DELAY = Constant.of()
      .timeout(Duration.ofHours(1L))
      .delay(Duration.ofSeconds(60))
      .build();

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


  protected boolean isAclStabilized(
      final ProxyClient<MemoryDbClient> proxyClient,
      final ResourceModel model,
      Logger logger) {
    logger.log("isACLStabilized");
    try {
      final Optional<ACL> acl =
          proxyClient.injectCredentialsAndInvokeV2(
              Translator.translateToReadRequest(model), proxyClient.client()::describeACLs
          ).acLs().stream().findFirst();

      if (!acl.isPresent()) {
        throw AclNotFoundException.builder().build();
      }

      logger.log("Stable status: " + acl.get().name() + " " + acl.get().status());
      return "ACTIVE".equalsIgnoreCase(acl.get().status());
    } catch (AclNotFoundException e) {
      throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
    } catch (Exception e) {
      logger.log(
          e.toString() + " " + e.getMessage() + " " + e.getCause() + "\n" + Throwables.getStackTraceAsString(e));
      throw new CfnNotStabilizedException(MESSAGE_FORMAT_FAILED_TO_STABILIZE, model.getACLName(), e);
    }
  }

  protected ACL getACL(final AmazonWebServicesClientProxy proxy,
      final ProxyClient<MemoryDbClient> client,
      final ResourceModel model) {
    final DescribeAcLsResponse response = handleExceptions(() ->
        proxy.injectCredentialsAndInvokeV2(Translator.translateToReadRequest(model),
            client.client()::describeACLs));
    return response.acLs()
        .stream()
        .findFirst()
        .get();
  }

  protected <T> T handleExceptions(Supplier<T> call) {
    try {
      return call.get();
    } catch (final InvalidParameterException | InvalidParameterValueException | UserNotFoundException e) {
      throw new CfnInvalidRequestException(e);
    } catch (final AclAlreadyExistsException e) {
      throw new CfnAlreadyExistsException(e);
    } catch (final AclNotFoundException e) {
      throw new CfnNotFoundException(e);
    } catch (final AwsServiceException e) {
      throw new CfnGeneralServiceException(e);
    }
  }
}
