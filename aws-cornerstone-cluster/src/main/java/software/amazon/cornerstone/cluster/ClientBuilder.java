package software.amazon.cornerstone.cluster;

import software.amazon.awssdk.services.cornerstone.CornerstoneClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {

    public static CornerstoneClient getClient() {
        return CornerstoneClient.builder().httpClient(LambdaWrapper.HTTP_CLIENT).build();
    }

}
