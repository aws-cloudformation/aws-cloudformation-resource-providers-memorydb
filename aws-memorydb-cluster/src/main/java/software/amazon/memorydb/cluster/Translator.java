package software.amazon.memorydb.cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.CreateClusterRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteClusterRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.ReplicaConfigurationRequest;
import software.amazon.awssdk.services.memorydb.model.SecurityGroupMembership;
import software.amazon.awssdk.services.memorydb.model.ShardConfigurationRequest;
import software.amazon.awssdk.services.memorydb.model.Tag;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateClusterRequest;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

public class Translator {

    /**
     * Returns true if desiredValue is not null and it is not equal to the currentValue.
     *
     * Property may be skipped from the template if no modification is needed for it, hence a property is considered as
     * modified only if value is provided and provided value is different from the current value.
     *
     * @param desiredValue requested new value
     * @param currentValue current value
     * @param <T> type of the property value
     * @return true if modification for the property is requested, otherwise false
     */
    static <T> boolean isModified(T desiredValue, T currentValue) {
        return (desiredValue != null && (desiredValue instanceof String ?
                isModifiedIgnoreCase(desiredValue, currentValue)
                : desiredValue instanceof List ?
                isModifiedIgnoreOrder(desiredValue, currentValue)
                : ! desiredValue.equals(currentValue)));
    }

    private static <T> boolean isModifiedIgnoreOrder(T desiredValue, T currentValue) {
        return currentValue != null ? ! CollectionUtils.isEqualCollection((List<?>) desiredValue,
                (List<?>) currentValue) : true;
    }

    private static <T> boolean isModifiedIgnoreCase(T desiredValue, T currentValue) {
        return ! ((String) desiredValue).equalsIgnoreCase((String) currentValue);
    }

    static CreateClusterRequest translateToCreateRequest(final ResourceModel model, Map<String, String> tags) {
        return CreateClusterRequest.builder()
                .clusterName(model.getClusterName())
                .nodeType(model.getNodeType())
                .parameterGroupName(model.getParameterGroupName())
                .description(model.getDescription())
                .numShards(model.getNumShards())
                .numReplicasPerShard(model.getNumReplicasPerShard())
                .subnetGroupName(model.getSubnetGroupName())
                .securityGroupIds(model.getSecurityGroupIds())
                .maintenanceWindow(model.getMaintenanceWindow())
                .port(model.getPort())
                .snsTopicArn(model.getSnsTopicArn())
                .tlsEnabled(model.getTLSEnabled())
                .kmsKeyId(model.getKmsKeyId())
                .snapshotArns(model.getSnapshotArns())
                .snapshotName(model.getSnapshotName())
                .snapshotRetentionLimit(model.getSnapshotRetentionLimit())
                .tags(translateTagsToSdk(tags))
                .snapshotWindow(model.getSnapshotWindow())
                .aclName(model.getACLName())
                .engineVersion(model.getEngineVersion())
                .autoMinorVersionUpgrade(model.getAutoMinorVersionUpgrade())
                .build();

    }

    // Translate tags
    static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.cluster.Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
                .collect(Collectors.toSet());
    }

    // Translate tags
    static Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
        return tags!= null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
                .stream()
                .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
                .collect(Collectors.toSet()) : null;
    }

    static Set<software.amazon.memorydb.cluster.Tag> mapToTags(final Map<String, String> tags) {
        return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
                .stream()
                .map(entry -> software.amazon.memorydb.cluster.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                .collect(Collectors.toSet()) : null;
    }

    static Set<software.amazon.memorydb.cluster.Tag> translateTagsFromSdk(final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> software.amazon.memorydb.cluster.Tag.builder()
                        .key(tag.key())
                        .value(tag.value()).build())
                .collect(Collectors.toSet());
    }

    static DescribeClustersRequest translateToReadRequest(final ResourceModel model) {
        return DescribeClustersRequest.builder().clusterName(model.getClusterName()).showShardDetails(true).build();
    }

    static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
        return translateToListTagsRequest(model.getARN());
    }

    static ListTagsRequest translateToListTagsRequest(final String arn) {
        return ListTagsRequest.builder().resourceArn(arn).build();
    }

    static UpdateClusterRequest translateToUpdateRequest(final ResourceModel model, ClusterUpdateFieldType fieldType) {
        UpdateClusterRequest.Builder builder = UpdateClusterRequest.builder().clusterName(model.getClusterName());
        switch (fieldType) {
            case DESCRIPTION:
                builder.description(model.getDescription());
                break;
            case SECURITY_GROUP_IDS:
                builder.securityGroupIds(model.getSecurityGroupIds());
                break;
            case MAINTENANCE_WINDOW:
                builder.maintenanceWindow(model.getMaintenanceWindow());
                break;
            case SNS_TOPIC_ARN:
                builder.snsTopicArn(model.getSnsTopicArn());
                builder.snsTopicStatus(model.getSnsTopicStatus());
                break;
            case SNS_TOPIC_STATUS:
                builder.snsTopicStatus(model.getSnsTopicStatus());
                break;
            case PARAMETER_GROUP_NAME:
                builder.parameterGroupName(model.getParameterGroupName());
                break;
            case SNAPSHOT_WINDOW:
                builder.snapshotWindow(model.getSnapshotWindow());
                break;
            case SNAPSHOT_RETENTION_LIMIT:
                builder.snapshotRetentionLimit(model.getSnapshotRetentionLimit());
                break;
            case NODE_TYPE:
                builder.nodeType(model.getNodeType());
                break;
            case ENGINE_VERSION:
                builder.engineVersion(model.getEngineVersion());
                builder.parameterGroupName(model.getParameterGroupName());
                break;
            case REPLICA_CONFIGURATION:
                builder.replicaConfiguration(ReplicaConfigurationRequest.builder().replicaCount(model.getNumReplicasPerShard()).build());
                break;
            case SHARD_CONFIGURATION:
                builder.shardConfiguration(ShardConfigurationRequest.builder().shardCount(model.getNumShards()).build());
                break;
            case ACL_NAME:
                builder.aclName(model.getACLName()).build();
                break;
            default:
                throw new RuntimeException("Unknown ClusterUpdateFieldType " + fieldType);

        }
        return builder.build();
    }

    static ResourceModel translateFromReadResponse(final DescribeClustersResponse response) {
        return translateFromReadResponse(response.clusters().get(0));
    }

    static ResourceModel translateFromReadResponse(Cluster cluster) {
        final int replicaCount = cluster.shards().stream().mapToInt(software.amazon.awssdk.services.memorydb.model.Shard::numberOfNodes).min().orElse(1) - 1;
        final List<String> securityGroupIds = cluster.securityGroups().stream().map(SecurityGroupMembership::securityGroupId).collect(Collectors.toList());
        return ResourceModel.builder()
                .clusterName(cluster.name())
                .description(cluster.description())
                .status(cluster.status())
                .nodeType(cluster.nodeType())
                .numShards(cluster.numberOfShards())
                .numReplicasPerShard(replicaCount)
                .subnetGroupName(cluster.subnetGroupName())
                .securityGroupIds(securityGroupIds)
                .port(cluster.clusterEndpoint().port())
                .snsTopicArn(cluster.snsTopicArn())
                .tLSEnabled(cluster.tlsEnabled())
                .aRN(cluster.arn())
                .engineVersion(cluster.engineVersion())
                .parameterGroupName(cluster.parameterGroupName())
                .parameterGroupStatus(cluster.parameterGroupStatus())
                .autoMinorVersionUpgrade(cluster.autoMinorVersionUpgrade())
                .maintenanceWindow(cluster.maintenanceWindow())
                .snapshotWindow(cluster.snapshotWindow())
                .snapshotRetentionLimit(cluster.snapshotRetentionLimit())
                .aCLName(cluster.aclName())
                .snsTopicStatus(cluster.snsTopicStatus())
                .clusterEndpoint(translateEndpoint(cluster)).build();
    }

    static Endpoint translateEndpoint(final Cluster cluster) {
        return Endpoint.builder().address(cluster.clusterEndpoint().address()).port(cluster.clusterEndpoint().port()).build();
    }

    static DeleteClusterRequest translateToDeleteRequest(final ResourceModel model) {
        return DeleteClusterRequest.builder().clusterName(model.getClusterName()).finalSnapshotName(model.getFinalSnapshotName()).build();
    }

    static DescribeClustersRequest translateToListRequest(final String nextToken) {
        return DescribeClustersRequest.builder().nextToken(nextToken).showShardDetails(true).build();
    }

    static List<ResourceModel> translateFromListResponse(final DescribeClustersResponse describeClustersResponse) {
        return streamOfOrEmpty(describeClustersResponse.clusters()).map(cluster -> translateFromReadResponse(cluster)).collect(Collectors.toList());
    }

    public static UntagResourceRequest translateToUntagResourceRequest(String arn, Set<software.amazon.memorydb.cluster.Tag> tagsToRemove) {
        return UntagResourceRequest.builder()
                .resourceArn(arn)
                .tagKeys(tagsToRemove != null ? tagsToRemove.stream()
                        .map(tag -> tag.getKey())
                        .collect(Collectors.toList()) : null)
                .build();
    }

    public static TagResourceRequest translateToTagResourceRequest(String arn, Set<software.amazon.memorydb.cluster.Tag> tagsToAdd) {
        return  TagResourceRequest.builder()
                .resourceArn(arn)
                .tags(translateTagsToSdk(tagsToAdd))
                .build();
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection).map(Collection::stream).orElseGet(Stream::empty);
    }

    static Set<software.amazon.memorydb.cluster.Tag> translateTags(final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> software.amazon.memorydb.cluster.Tag.builder().key(tag.key()).value(tag.value()).build())
                .collect(Collectors.toSet());
    }
}
