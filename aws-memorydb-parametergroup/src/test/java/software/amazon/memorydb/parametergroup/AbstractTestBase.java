package software.amazon.memorydb.parametergroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;
  protected static final String DESCRIPTION;
  protected static final String FAMILY;
  protected static final Set<Tag> TAG_SET;

  private static final String NAME;

  private static final String ARN;

  protected static final HashMap<String, Object> PARAMS ;

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();
    DESCRIPTION = "sample description";
    FAMILY = "default.durable_redis6.2";
    TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());
    PARAMS = new HashMap<>();
    PARAMS.put("param", "value");
    PARAMS.put("param2", "value");
    ARN = "arn";
    NAME = "test-parameter-group";
  }

  static ResourceModel getDesiredTestResourceModel() {
    return ResourceModel.builder().parameterGroupName(NAME).description(DESCRIPTION).aRN(ARN).family(FAMILY).tags(TAG_SET).build();
  }

  static ParameterGroup getTestParameterGroup() {
    return ParameterGroup.builder().name(NAME).description(DESCRIPTION).arn(ARN).family(FAMILY).build();
  }

  static Map<String, String> translateTagsToMap(final Set<Tag> tags) {
    return tags.stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

  }

  static Set<software.amazon.awssdk.services.memorydb.model.Tag> translateTagsToSdk(final Set<Tag> tags) {
    return tags.stream()
            .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder()
                    .key(tag.getKey())
                    .value(tag.getValue())
                    .build())
            .collect(Collectors.toSet());
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
