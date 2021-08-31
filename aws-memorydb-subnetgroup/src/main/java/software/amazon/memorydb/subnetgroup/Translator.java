package software.amazon.memorydb.subnetgroup;

import software.amazon.awssdk.services.memorydb.model.CreateSubnetGroupRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteSubnetGroupRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeSubnetGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.Subnet;
import software.amazon.awssdk.services.memorydb.model.SubnetGroup;
import software.amazon.awssdk.services.memorydb.model.Tag;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateSubnetGroupRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
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
    return (desiredValue != null && !desiredValue.equals(currentValue));
  }

  /**
   * Request to create a subnet group
   * @param model resource model
   * @param tags map of tags (key, value)
   * @return createSubnetGroupRequest the aws service request to create a resource
   */
  static CreateSubnetGroupRequest translateToCreateRequest(final ResourceModel model, Map<String, String> tags) {
    return CreateSubnetGroupRequest.builder()
            .subnetGroupName(model.getSubnetGroupName())
            .description(model.getDescription())
            .subnetIds(model.getSubnetIds())
            .tags(translateTagsToSdk(tags))
            .build();
  }

  /**
   * Translate tags from model to SDK
   * @param tags map<string,string>
   * @return tags Set<string,string>
   */
  static Set<software.amazon.awssdk.services.memorydb.model.Tag> translateTagsToSdk(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
            .collect(Collectors.toSet()) : null;
  }

  static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.subnetgroup.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
            .collect(Collectors.toSet());
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return describeSubnetGroupsRequest the aws service request to describe a resource
   */
  static DescribeSubnetGroupsRequest translateToReadRequest(final ResourceModel model) {
    return DescribeSubnetGroupsRequest.builder()
            .subnetGroupName(model.getSubnetGroupName())
            .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param response the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeSubnetGroupsResponse response) {
    return translateFromDescribeSubnetGroupResponse(response.subnetGroups().get(0));
  }

  /**
   * Translates Response to resource Model
   * @param subnetGroup
   * @return resourceModel
   */
  public static ResourceModel translateFromDescribeSubnetGroupResponse(final SubnetGroup subnetGroup) {
    Set<String> subnetIds = subnetGroup.subnets().stream().map(Subnet::identifier).collect(Collectors.toSet());
    ResourceModel resourceModel = ResourceModel.builder()
            .subnetGroupName(subnetGroup.name())
            .description(subnetGroup.description())
            .subnetIds(subnetIds)
            .aRN(subnetGroup.arn())
            .build();
    return resourceModel;
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteSubnetGroupRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteSubnetGroupRequest.builder()
            .subnetGroupName(model.getSubnetGroupName())
            .build();
  }

  static UpdateSubnetGroupRequest translateToUpdateRequest(final ResourceModel model,SubnetGroupUpdateFieldType fieldType) {
    UpdateSubnetGroupRequest.Builder builder = UpdateSubnetGroupRequest.builder().subnetGroupName(model.getSubnetGroupName());
    switch (fieldType) {
      case DESCRIPTION:
        builder.description(model.getDescription());
        break;
      case SUBNET_IDS:
        builder.subnetIds(model.getSubnetIds());
        break;
      default:
        throw new RuntimeException("Unknown SubnetGroupUpdateFieldType " + fieldType);

    }
    return builder.build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static DescribeSubnetGroupsRequest translateToListRequest(final String nextToken) {
    return DescribeSubnetGroupsRequest.builder()
            .nextToken(nextToken)
            .build();
  }

  public static List<ResourceModel> translateFromListResponse(final DescribeSubnetGroupsResponse describeSubnetGroupsResponse) {
    return streamOfOrEmpty(describeSubnetGroupsResponse.subnetGroups()).map(
            subnetGroup -> translateFromDescribeSubnetGroupResponse(subnetGroup)).collect(Collectors.toList());
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static ListTagsRequest translateToListTagsRequest(final String arn) {
    return ListTagsRequest.builder().resourceArn(arn).build();
  }

  static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
    return translateToListTagsRequest(model.getARN());
  }

  static Set<software.amazon.memorydb.subnetgroup.Tag> translateTagsFromSdk(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.memorydb.subnetgroup.Tag.builder()
                    .key(tag.key())
                    .value(tag.value()).build())
            .collect(Collectors.toSet());
  }

  public static TagResourceRequest translateToTagResourceRequest(String arn, Set<software.amazon.memorydb.subnetgroup.Tag> tagsToAdd) {
    return TagResourceRequest.builder()
            .resourceArn(arn)
            .tags(translateTagsToSdk(tagsToAdd))
            .build();
  }

  public static UntagResourceRequest translateToUntagResourceRequest(String arn, Set<software.amazon.memorydb.subnetgroup.Tag> tagsToRemove) {
    return UntagResourceRequest.builder()
            .resourceArn(arn)
            .tagKeys(tagsToRemove != null ? tagsToRemove.stream()
                    .map(tag -> tag.getKey())
                    .collect(Collectors.toList()) : null)
            .build();
  }

  static Set<software.amazon.memorydb.subnetgroup.Tag> mapToTags(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(entry -> software.amazon.memorydb.subnetgroup.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .collect(Collectors.toSet()) : null;
  }

  static Set<software.amazon.memorydb.subnetgroup.Tag> translateTags(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.memorydb.subnetgroup.Tag.builder().key(tag.key()).value(tag.value()).build())
            .collect(Collectors.toSet());
  }
}
