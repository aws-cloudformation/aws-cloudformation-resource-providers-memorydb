package software.amazon.memorydb.acl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.services.memorydb.model.ACL;
import software.amazon.awssdk.services.memorydb.model.CreateAclRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteAclRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeAcLsResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.Tag;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateAclRequest;

public class Translator {

  public static final int MAX_RESULTS = 50;

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateAclRequest translateToCreateRequest(final ResourceModel model) {
    return CreateAclRequest.builder()
        .aclName(model.getACLName())
        .userNames(model.getUserNames())
        .tags(translateTagsToSdk(model.getTags()))
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeAcLsRequest translateToReadRequest(final ResourceModel model) {
    return DescribeAcLsRequest.builder()
        .aclName(model.getACLName())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param response the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeAcLsResponse response) {
    ACL acl = response.acLs().get(0);
    return ResourceModel.builder()
        .status(acl.status())
        .userNames(acl.userNames())
        .aCLName(acl.name())
        .arn(acl.arn())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteAclRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteAclRequest.builder()
        .aclName(model.getACLName())
        .build();
  }


  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateAclRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateAclRequest.builder()
        .aclName(model.getACLName())
        .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static DescribeAcLsRequest translateToListRequest(final String nextToken) {
    return DescribeAcLsRequest.builder()
        .maxResults(MAX_RESULTS)
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param response the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final DescribeAcLsResponse response) {
    return streamOfOrEmpty(response.acLs())
        .map(acl -> ResourceModel.builder()
            .status(acl.status())
            .aCLName(acl.name())
            .userNames(acl.userNames())
            .arn(acl.arn())
            .build())
        .collect(Collectors.toList());
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static Set<software.amazon.awssdk.services.memorydb.model.Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.acl.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
        .collect(Collectors.toSet());
  }

  static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
    return ListTagsRequest.builder().resourceArn(model.getArn()).build();
  }

  static Set<software.amazon.memorydb.acl.Tag> translateTags(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> software.amazon.memorydb.acl.Tag.builder().key(tag.key()).value(tag.value()).build())
        .collect(Collectors.toSet());
  }

  static Set<software.amazon.memorydb.acl.Tag> translateTags(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
        .stream()
        .map(entry -> software.amazon.memorydb.acl.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
        .collect(Collectors.toSet()) : null;
  }

  static Map<String, String> translateTags(final Set<software.amazon.memorydb.acl.Tag> tags) {
    return tags != null ? tags
        .stream()
        .collect(Collectors.toMap(software.amazon.memorydb.acl.Tag::getKey, software.amazon.memorydb.acl.Tag::getValue)) :
        null;
  }

  static UntagResourceRequest translateToUntagResourceRequest(String arn,
      Collection<software.amazon.memorydb.acl.Tag> tagsToRemove) {
    return UntagResourceRequest.builder()
        .resourceArn(arn)
        .tagKeys(tagsToRemove != null ? tagsToRemove.stream()
            .map(tag -> tag.getKey())
            .collect(Collectors.toList()) : null)
        .build();
  }

  static TagResourceRequest translateToTagResourceRequest(String arn,
      Collection<software.amazon.memorydb.acl.Tag> tags) {
    return  TagResourceRequest.builder()
        .resourceArn(arn)
        .tags(translateTagsToSdk(tags))
        .build();
  }

}
