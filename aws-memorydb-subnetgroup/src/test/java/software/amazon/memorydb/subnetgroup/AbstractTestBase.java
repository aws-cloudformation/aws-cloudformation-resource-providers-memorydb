package software.amazon.memorydb.subnetgroup;

import org.mockito.internal.util.collections.Sets;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.CreateSubnetGroupResponse;
import software.amazon.awssdk.services.memorydb.model.DeleteSubnetGroupResponse;
import software.amazon.awssdk.services.memorydb.model.Subnet;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final AwsCredentialsProvider credentialsProvider;
  protected static final LoggerProxy logger;

  protected static final String SUBNET_GROUP_NAME;
  protected static final String DESCRIPTION;
  protected static final Set<String> SUBNET_IDS;
  protected static final String ARN;
  protected static final Set<Tag> TAG_SET;

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();
    AwsSessionCredentials awsSessionCredentials =
            AwsSessionCredentials.create(MOCK_CREDENTIALS.getAccessKeyId(), MOCK_CREDENTIALS.getSecretAccessKey(), MOCK_CREDENTIALS.getSessionToken());
    credentialsProvider = StaticCredentialsProvider.create(awsSessionCredentials);

    SUBNET_GROUP_NAME = "test-subnet-group";
    DESCRIPTION = "test-subnet-group-description";
    SUBNET_IDS = Sets.newSet("subnetid1", "subnetid2", "subnetid3");
    ARN = "test-subnet-group";
    TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());

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

  public static CreateSubnetGroupResponse getCreateSubnetGroupResponse() {
    return CreateSubnetGroupResponse.builder()
            .subnetGroup(getSubnetGroup())
            .build();
  }

  public static DeleteSubnetGroupResponse deleteSubnetGroupResponse() {
    return DeleteSubnetGroupResponse.builder()
            .subnetGroup(getSubnetGroup())
            .build();
  }

  public static ResourceModel getDesiredResourceStateModel() {
    return ResourceModel.builder()
            .subnetGroupName(SUBNET_GROUP_NAME)
            .description(DESCRIPTION)
            .subnetIds(SUBNET_IDS)
            .aRN(ARN)
            .build();
  }

  public static SubnetGroup getSubnetGroup() {
    List<Subnet> subnets = SUBNET_IDS.stream().map(subnetId -> Subnet.builder().identifier(subnetId).build()).collect(Collectors.toList());
    return SubnetGroup.builder()
            .name(SUBNET_GROUP_NAME)
            .description(DESCRIPTION)
            .subnets(subnets)
            .arn(ARN)
            .build();
  }

  public static ResourceModel getResourceModel(SubnetGroup subnetGroup) {
    return ResourceModel.builder()
            .subnetGroupName(subnetGroup.name())
            .description(subnetGroup.description())
            .subnetIds(subnetGroup.subnets().stream().map(Subnet::identifier).collect(Collectors.toSet()))
            .aRN(subnetGroup.arn())
            .build();
  }

  static Map<String, String> translateTagsToMap(final Set<Tag> tags) {
    return tags.stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

  }
}
