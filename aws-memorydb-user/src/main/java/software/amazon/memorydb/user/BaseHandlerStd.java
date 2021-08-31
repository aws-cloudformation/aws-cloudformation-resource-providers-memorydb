package software.amazon.memorydb.user;

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.User;
import software.amazon.awssdk.services.memorydb.model.UserAlreadyExistsException;
import software.amazon.awssdk.services.memorydb.model.UserNotFoundException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  private static final String MESSAGE_FORMAT_FAILED_TO_STABILIZE = "User %s failed to stabilize.";
  protected static final Constant STABILIZATION_DELAY = Constant.of()
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


  protected boolean isUserStabilized(
      final ProxyClient<MemoryDbClient> proxyClient,
      final ResourceModel model,
      Logger logger) {

    logger.log("isUserStabilized");
    try {
      final Optional<User> user =
          proxyClient.injectCredentialsAndInvokeV2(
              Translator.translateToReadRequest(model), proxyClient.client()::describeUsers
          ).users().stream().findFirst();

      if (!user.isPresent()) {
        throw UserNotFoundException.builder().build();
      }

      logger.log("Stable status: " + user.get().name() + " " + user.get().status());
      return "ACTIVE".equalsIgnoreCase(user.get().status());
    } catch (UserNotFoundException e) {
      throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
    } catch (Exception e) {
      logger.log(
          e.toString() + " " + e.getMessage() + " " + e.getCause() + "\n" + Throwables.getStackTraceAsString(e));
      throw new CfnNotStabilizedException(MESSAGE_FORMAT_FAILED_TO_STABILIZE, model.getUserName(), e);
    }
  }

  protected <T> T handleExceptions(Supplier<T> call) {
    try {
      return call.get();
    } catch (final UserAlreadyExistsException e) {
      throw new CfnAlreadyExistsException(e);
    } catch (final UserNotFoundException e) {
      throw new CfnNotFoundException(e);
    } catch (final AwsServiceException e) {
      throw new CfnGeneralServiceException(e);
    }
  }
}
