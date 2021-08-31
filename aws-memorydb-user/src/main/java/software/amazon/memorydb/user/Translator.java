package software.amazon.memorydb.user;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.services.memorydb.model.CreateUserRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteUserRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateUserRequest;
import software.amazon.awssdk.services.memorydb.model.User;
import software.amazon.awssdk.services.memorydb.model.Tag;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  public static final int MAX_RECORDS = 50;

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateUserRequest translateToCreateRequest(final ResourceModel model) {
    return CreateUserRequest.builder()
        .userName(model.getUserName())
        .authenticationMode(
            software.amazon.awssdk.services.memorydb.model.AuthenticationMode.builder()
                .type(model.getAuthenticationMode().getType())
                .passwords(model.getAuthenticationMode().getPasswords())
              .build())
        .accessString(model.getAccessString())
        .tags(translateTagsToSdk(model.getTags()))
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeUsersRequest translateToReadRequest(final ResourceModel model) {
    return DescribeUsersRequest.builder()
        .userName(model.getUserName())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param response the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeUsersResponse response) {
    User user = response.users().get(0);
    return ResourceModel.builder()
        .status(user.status())
        .userName(user.name())
        .accessString(user.accessString())
        .authenticationMode(
            AuthenticationMode.builder()
                .type(user.authentication().type().toString())
                .build())
        .arn(user.arn())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteUserRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteUserRequest.builder()
        .userName(model.getUserName())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateUserRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateUserRequest.builder()
        .userName(model.getUserName())
        .authenticationMode(
            software.amazon.awssdk.services.memorydb.model.AuthenticationMode.builder()
                .type(model.getAuthenticationMode().getType())
                .passwords(model.getAuthenticationMode().getPasswords())
                .build())
        .accessString(model.getAccessString())
        .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static DescribeUsersRequest translateToListRequest(final String nextToken) {
    return DescribeUsersRequest.builder()
        .maxResults(MAX_RECORDS)
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param response the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final DescribeUsersResponse response) {
    return streamOfOrEmpty(response.users())
        .map(user -> ResourceModel.builder()
            .status(user.status())
            .userName(user.name())
            .accessString(user.accessString())
            .arn(user.arn())
            .build())
        .collect(Collectors.toList());
  }

  static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.user.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
        .collect(Collectors.toSet());
  }

  static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
    return ListTagsRequest.builder().resourceArn(model.getArn()).build();
  }

  static Set<software.amazon.memorydb.user.Tag> translateTags(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> software.amazon.memorydb.user.Tag.builder().key(tag.key()).value(tag.value()).build())
        .collect(Collectors.toSet());
  }

  static Set<software.amazon.memorydb.user.Tag> translateTags(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
        .stream()
        .map(entry -> software.amazon.memorydb.user.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
        .collect(Collectors.toSet()) : null;
  }

  static Map<String, String> translateTags(final Set<software.amazon.memorydb.user.Tag> tags) {
    return tags != null ? tags
        .stream()
        .collect(Collectors.toMap(software.amazon.memorydb.user.Tag::getKey, software.amazon.memorydb.user.Tag::getValue)) :
        null;
  }

  static UntagResourceRequest translateToUntagResourceRequest(String arn,
      Collection<software.amazon.memorydb.user.Tag> tagsToRemove) {
    return UntagResourceRequest.builder()
        .resourceArn(arn)
        .tagKeys(tagsToRemove != null ? tagsToRemove.stream()
            .map(tag -> tag.getKey())
            .collect(Collectors.toList()) : null)
        .build();
  }

  static TagResourceRequest translateToTagResourceRequest(String arn, Collection<software.amazon.memorydb.user.Tag> tagsToAdd) {
    return  TagResourceRequest.builder()
        .resourceArn(arn)
        .tags(translateTagsToSdk(tagsToAdd))
        .build();
  }

}
