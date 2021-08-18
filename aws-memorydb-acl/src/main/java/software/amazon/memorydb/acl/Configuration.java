package software.amazon.memorydb.acl;

import com.amazonaws.util.CollectionUtils;
import java.util.Map;
import java.util.stream.Collectors;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-memorydb-acl.json");
    }

    @Override
    public Map<String, String> resourceDefinedTags(final ResourceModel resourceModel) {
        if(CollectionUtils.isNullOrEmpty(resourceModel.getTags()))
            return null;

        return resourceModel.getTags()
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }
}
