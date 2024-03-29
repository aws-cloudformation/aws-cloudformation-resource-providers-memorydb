package software.amazon.memorydb.cluster;

public enum ClusterUpdateFieldType {
    DESCRIPTION,
    SECURITY_GROUP_IDS,
    MAINTENANCE_WINDOW,
    SNS_TOPIC_ARN,
    SNS_TOPIC_STATUS,
    PARAMETER_GROUP_NAME,
    SNAPSHOT_WINDOW,
    SNAPSHOT_RETENTION_LIMIT,
    NODE_TYPE,
    ENGINE_VERSION,
    REPLICA_CONFIGURATION,
    SHARD_CONFIGURATION,
    ACL_NAME
}
