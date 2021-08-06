package software.amazon.memorydb.user;

import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {

  public static MemoryDbClient getClient() {
    return MemoryDbClient.builder().httpClient(LambdaWrapper.HTTP_CLIENT).build();
  }
}
