package software.amazon.cornerstone.cluster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import software.amazon.awssdk.services.cornerstone.model.Cluster;
import software.amazon.awssdk.services.cornerstone.model.CreateClusterRequest;
import software.amazon.awssdk.services.cornerstone.model.DeleteClusterRequest;
import software.amazon.awssdk.services.cornerstone.model.DescribeClustersRequest;
import software.amazon.awssdk.services.cornerstone.model.DescribeClustersResponse;
import software.amazon.awssdk.services.cornerstone.model.SecurityGroupMembership;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

public class Translator {

    static CreateClusterRequest translateToCreateRequest(final ResourceModel model) {
        return CreateClusterRequest.builder().clusterName(model.getName()).description(model.getDescription()).nodeType(model.getNodeType())
                                   .numShards(model.getNumShards()).numReplicasPerShard(model.getNumReplicasPerShard())
                                   .subnetGroupName(model.getSubnetGroupName()).securityGroupIds(model.getSecurityGroupIds())
                                   .maintenanceWindow(model.getMaintenanceWindow()).port(model.getPort()).snsTopicArn(model.getSnsTopicArn())
                                   .tlsEnabled(model.getTLSEnabled()).build();

    }

    static DescribeClustersRequest translateToReadRequest(final ResourceModel model) {
        return DescribeClustersRequest.builder().clusterName(model.getName()).showShardDetails(true).build();
    }

    static ResourceModel translateFromReadResponse(final DescribeClustersResponse response) {
        return translateFromReadResponse(response.clusters().get(0));
    }

    static ResourceModel translateFromReadResponse(Cluster cluster) {
        final int replicaCount = cluster.shards().stream().mapToInt(software.amazon.awssdk.services.cornerstone.model.Shard::numNodes).min().orElse(1) - 1;
        final List<String> securityGroupIds = cluster.securityGroups().stream().map(SecurityGroupMembership::securityGroupId).collect(Collectors.toList());
        final List<Shard> shards = translateShards(cluster);

        return ResourceModel.builder().name(cluster.name()).description(cluster.description()).status(cluster.status()).nodeType(cluster.nodeType())
                            .numShards(cluster.numShards()).numReplicasPerShard(replicaCount).subnetGroupName(cluster.subnetGroupName())
                            .securityGroupIds(securityGroupIds).port(cluster.clusterEndpoint().port()).snsTopicArn(cluster.snsTopicArn())
                            .tLSEnabled(cluster.tlsEnabled()).aRN(cluster.arn()).engineVersion(cluster.engineVersion())
                            .clusterEndpoint(translateEndpoint(cluster)).availabilityMode(cluster.availabilityModeAsString()).shards(shards).build();
    }

    static List<Shard> translateShards(final Cluster cluster) {
        return cluster.shards().stream().map(Translator::translateShard).collect(Collectors.toList());
    }

    static Endpoint translateEndpoint(final Cluster cluster) {
        return Endpoint.builder().address(cluster.clusterEndpoint().address()).port(cluster.clusterEndpoint().port()).build();
    }

    static Shard translateShard(final software.amazon.awssdk.services.cornerstone.model.Shard shard) {
        final List<Node> nodes = shard.nodes().stream().map(Translator::translateNode).collect(Collectors.toList());
        return Shard.builder().name(shard.name()).status(shard.status()).slots(shard.slots()).numNodes(shard.numNodes()).nodes(nodes).build();
    }

    private static Node translateNode(final software.amazon.awssdk.services.cornerstone.model.Node node) {
        return Node.builder().name(node.name()).status(node.status()).availabilityZone(node.availabilityZone()).createTime(node.createTime().toString())
                   .build();
    }

    static DeleteClusterRequest translateToDeleteRequest(final ResourceModel model) {
        return DeleteClusterRequest.builder().clusterName(model.getName()).build();
    }

    static DescribeClustersRequest translateToListRequest(final String nextToken) {
        return DescribeClustersRequest.builder().marker(nextToken).showShardDetails(true).build();
    }

    static List<ResourceModel> translateFromListResponse(final DescribeClustersResponse describeClustersResponse) {
        return streamOfOrEmpty(describeClustersResponse.clusters()).map(cluster -> translateFromReadResponse(cluster)).collect(Collectors.toList());
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection).map(Collection::stream).orElseGet(Stream::empty);
    }
}
