package com.jt.learning.mapper;

import com.jt.learning.dto.QuestionTagRow;
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

    List<Tag> selectEnabledTagsByType(@Param("tagType") String tagType);

    List<Tag> selectEnabledTagsByCodes(
            @Param("tagType") String tagType,
            @Param("codes") List<String> codes
    );

    List<Tag> selectEnabledTagsByAnyCodes(@Param("codes") List<String> codes);

    List<Tag> selectEnabledTagsByQuestionId(@Param("questionId") Long questionId);

    List<QuestionTagRow> selectEnabledTagsByQuestionIds(@Param("questionIds") List<Long> questionIds);
}
