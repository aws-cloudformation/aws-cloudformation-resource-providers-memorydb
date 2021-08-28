package software.amazon.memorydb.subnetgroup;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        logger.log("Input Model: ");
        logger.log(request.getDesiredResourceState().toString());

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> describeSubnetGroups(proxy, progress, proxyClient))
                .then(progress -> listTags(proxy, progress, proxyClient))
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }
}
