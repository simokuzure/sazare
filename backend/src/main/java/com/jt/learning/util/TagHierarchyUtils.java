package com.jt.learning.util;

import com.jt.learning.entity.Tag;

import java.util.List;

public final class TagHierarchyUtils {

    private TagHierarchyUtils() {
    }

    public static List<Tag> secondLevelTags(List<Tag> tags) {
        return tags.stream()
                .filter(tag -> tag.getParentId() != null)
                .toList();
    }
}
