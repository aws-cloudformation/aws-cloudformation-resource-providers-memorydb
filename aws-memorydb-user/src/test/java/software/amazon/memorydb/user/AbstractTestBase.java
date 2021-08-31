package software.amazon.memorydb.user;

import com.google.common.collect.ImmutableList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Authentication;
import software.amazon.awssdk.services.memorydb.model.User;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import java.util.Set;

public class AbstractTestBase {

  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;

  protected static final String USER_NAME;
  protected static final String DELETING;
  protected static final String MODIFYING;
  protected static final String ACTIVE;
  protected static final String STATUS;
  protected static final String ARN;
  protected static final String PASSWORD;
  protected static final String AUTHMODE;
  protected static final Set<Tag> TAG_SET;

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();

    USER_NAME = "user-name";
    DELETING = "Deleting";
    MODIFYING = "Modifying";
    ACTIVE = "Active";
    ARN = "testarn";
    AUTHMODE = "password";
    PASSWORD = "test";
    STATUS = ACTIVE;
    TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());
  }

  protected User buildDefaultUser() {
    return buildDefaultUser(STATUS);
  }

  protected User buildDefaultUser(String status) {
    return User
        .builder()
        .name(USER_NAME)
        .status(status)
        .arn(ARN)
        .authentication(Authentication.builder()
            .type(AUTHMODE)
            .passwordCount(1)
            .build())
        .build();
  }

  protected ResourceModel buildDefaultResourceModel() {
    return ResourceModel.builder()
        .status(STATUS)
        .userName(STATUS)
        .userName(USER_NAME)
        .authenticationMode(
            AuthenticationMode.builder()
                .type(AUTHMODE)
                .passwords(ImmutableList.of(PASSWORD))
                .build())
        .arn(ARN)
        .tags(TAG_SET)
        .build();
  }

  static ProxyClient<MemoryDbClient> MOCK_PROXY(
    final AmazonWebServicesClientProxy proxy,
    final MemoryDbClient sdkClient) {
    return new ProxyClient<MemoryDbClient>() {
      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
      injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
        return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      CompletableFuture<ResponseT>
      injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
      IterableT
      injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
        return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
      injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public MemoryDbClient client() {
        return sdkClient;
      }
    };
  }
}
