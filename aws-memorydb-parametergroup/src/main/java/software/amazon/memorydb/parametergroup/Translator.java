package software.amazon.memorydb.parametergroup;

import com.google.common.collect.Lists;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.memorydb.model.CreateParameterGroupRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteParameterGroupRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeParameterGroupsResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeParametersRequest;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.Parameter;
import software.amazon.awssdk.services.memorydb.model.ParameterGroup;
import software.amazon.awssdk.services.memorydb.model.ParameterNameValue;
import software.amazon.awssdk.services.memorydb.model.Tag;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateParameterGroupRequest;

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
  private static final int MAX_RECORDS_TO_DESCRIBE = 20;

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
   * Request to create a resource
   * @param model resource model
   * @param tags
   * @return awsRequest the aws service request to create a resource
   */
  static CreateParameterGroupRequest translateToCreateRequest(final ResourceModel model, Map<String, String> tags) {
    return CreateParameterGroupRequest.builder()
            .parameterGroupName(model.getParameterGroupName())
            .description(model.getDescription())
            .family(model.getFamily())
            .tags(translateTagsToSdk(tags)).build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeParameterGroupsRequest translateToReadRequest(final ResourceModel model) {
    return DescribeParameterGroupsRequest.builder().parameterGroupName(model.getParameterGroupName()).build();
  }

  /**
   * Request to list tags on a resource
   * @param model
   * @return
   */
  static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
    return translateToListTagsRequest(model.getARN());
  }

  static ListTagsRequest translateToListTagsRequest(final String arn) {
    return ListTagsRequest.builder().resourceArn(arn).build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeParameterGroupsResponse awsResponse) {
    return translateFromReadResponse(awsResponse.parameterGroups().get(0));
  }
  static ResourceModel translateFromReadResponse(final ParameterGroup parameterGroup) {
    return ResourceModel.builder()
            .parameterGroupName(parameterGroup.name())
            .aRN(parameterGroup.arn())
            .description(parameterGroup.description())
            .family(parameterGroup.family())
            .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteParameterGroupRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteParameterGroupRequest.builder().parameterGroupName(model.getParameterGroupName()).build();
  }



  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static DescribeParameterGroupsRequest translateToListRequest(final String nextToken) {
    return DescribeParameterGroupsRequest.builder().nextToken(nextToken).build();
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static List<ResourceModel> translateFromListResponse(final DescribeParameterGroupsResponse describeParameterGroupsResponse) {
    return streamOfOrEmpty(describeParameterGroupsResponse.parameterGroups()).map(parameterGroup -> translateFromReadResponse(parameterGroup)).collect(Collectors.toList());
  }

  public static UpdateParameterGroupRequest translateToUpdateRequest(ResourceModel resourceModel, List<Parameter> params) {
    return UpdateParameterGroupRequest.builder().parameterGroupName(resourceModel.getParameterGroupName())
            .parameterNameValues(params != null ? params.stream()
                    .map(kv -> ParameterNameValue.builder().parameterName(kv.name()).parameterValue(kv.value()).build())
                    .collect(Collectors.toList()) : null)
            .build();
  }

  public static DescribeClustersRequest translateToDescribeClustersRequest(String token) {
    return DescribeClustersRequest.builder()
            .nextToken(token)
            .maxResults(MAX_RECORDS_TO_DESCRIBE)
            .build();
  }

  static Set<software.amazon.memorydb.parametergroup.Tag> mapToTags(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(entry -> software.amazon.memorydb.parametergroup.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .collect(Collectors.toSet()) : null;
  }

  static Set<software.amazon.memorydb.parametergroup.Tag> translateTagsFromSdk(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.memorydb.parametergroup.Tag.builder()
                    .key(tag.key())
                    .value(tag.value()).build())
            .collect(Collectors.toSet());
  }

  static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.parametergroup.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
            .collect(Collectors.toSet());
  }

  static Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
            .collect(Collectors.toSet()) : null;
  }

  public static UntagResourceRequest translateToUntagResourceRequest(String arn, Set<software.amazon.memorydb.parametergroup.Tag> tagsToRemove) {
    return UntagResourceRequest.builder()
            .resourceArn(arn)
            .tagKeys(tagsToRemove != null ? tagsToRemove.stream()
                    .map(tag -> tag.getKey())
                    .collect(Collectors.toList()) : null)
            .build();
  }

  public static TagResourceRequest translateToTagResourceRequest(String arn, Set<software.amazon.memorydb.parametergroup.Tag> tagsToAdd) {
    return  TagResourceRequest.builder()
            .resourceArn(arn)
            .tags(translateTagsToSdk(tagsToAdd))
            .build();
  }

  public static DescribeParametersRequest translateToDescribeParametersRequest(String parameterGroupName, String nextToken) {
    return DescribeParametersRequest.builder().parameterGroupName(parameterGroupName).maxResults(MAX_RECORDS_TO_DESCRIBE).nextToken(nextToken).build();
  }

  static Set<software.amazon.memorydb.parametergroup.Tag> translateTags(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.memorydb.parametergroup.Tag.builder().key(tag.key()).value(tag.value()).build())
            .collect(Collectors.toSet());
  }
}
