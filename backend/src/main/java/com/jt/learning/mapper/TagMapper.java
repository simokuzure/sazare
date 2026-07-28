package com.jt.learning.mapper;

import com.jt.learning.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper {

    long countTags(
            @Param("tagType") String tagType,
            @Param("parentId") Long parentId,
            @Param("enabledOnly") boolean enabledOnly
    );

    List<Tag> selectTags(
            @Param("tagType") String tagType,
            @Param("parentId") Long parentId,
            @Param("enabledOnly") boolean enabledOnly,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
