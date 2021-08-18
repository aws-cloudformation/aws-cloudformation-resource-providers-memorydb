package software.amazon.memorydb.acl;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ACL;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.delay.Constant;

public class AbstractTestBase {

  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;

  protected static final String ACL_NAME;
  protected static final String ENGINE;
  protected static final String DELETING;
  protected static final String MODIFYING;
  protected static final String CREATING;
  protected static final String ACTIVE;
  protected static final String STATUS;
  protected static final String ARN;
  protected static final List<String> USER_NAMES;
  protected static final List<String> CLUSTERS;
  protected static final Set<Tag> TAG_SET;

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();

    ACL_NAME = "acl-name";
    ENGINE = "redis";
    DELETING = "Deleting";
    MODIFYING = "Modifying";
    CREATING = "Creating";
    ACTIVE = "Active";
    STATUS = ACTIVE;
    ARN = "testarn";
    USER_NAMES = ImmutableList.of("test-user-names");
    CLUSTERS = ImmutableList.of("test-clusters");
    TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());

    BaseHandlerStd.STABILIZATION_DELAY = Constant.of()
        .timeout(Duration.ofSeconds(5L))
        .delay(Duration.ofSeconds(1L))
        .build();
  }

  protected ACL buildDefaultAcl() {
    return buildDefaultAcl(STATUS);
  }

  protected ACL buildDefaultAcl(String status) {
    return buildDefaultAcl(status, false, USER_NAMES);
  }

  protected ACL buildDefaultAcl(String status, boolean hasCluster, List<String> userNames) {

    ACL.Builder builder = ACL
        .builder()
        .name(ACL_NAME)
        .userNames(userNames)
        .arn(ARN)
        .status(status);

    builder.clusters(hasCluster ? CLUSTERS : ImmutableList.of());

    return builder.build();
  }

  protected ResourceModel buildDefaultResourceModel(List<String> userNames) {
    ResourceModel.ResourceModelBuilder builder = ResourceModel.builder()
        .status(STATUS)
        .aCLName(ACL_NAME)
        .userNames(userNames)
        .tags(TAG_SET)
        .arn(ARN);

    return builder.build();
  }

  protected ResourceModel buildDefaultResourceModel() {
    return buildDefaultResourceModel(USER_NAMES);
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
      injectCredentialsAndInvokeV2Async(RequestT request,
          Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
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
      injectCredentialsAndInvokeV2InputStream(RequestT requestT,
          Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT,
          Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public MemoryDbClient client() {
        return sdkClient;
      }
    };
  }
}
