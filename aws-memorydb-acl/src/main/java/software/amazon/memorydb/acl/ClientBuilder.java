package software.amazon.memorydb.acl;

import java.net.URI;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.utils.AttributeMap;

public class ClientBuilder {

  public static MemoryDbClient getClient() {
    final SdkHttpClient sdkHttpClient = ApacheHttpClient.builder().buildWithDefaults(AttributeMap.builder()
        .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true).build());
    return MemoryDbClient.builder().httpClient(sdkHttpClient).endpointOverride(URI.create("https://cornerstone-qa-us-east-1.aka.amazon.com")).build();
  }
}
