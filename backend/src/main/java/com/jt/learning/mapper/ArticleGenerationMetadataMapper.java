package com.jt.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface ArticleGenerationMetadataMapper {

    int insertArticleGenerationMetadata(
            @Param("questionId") Long questionId,
            @Param("seed") UUID seed,
            @Param("blueprint") String blueprint,
            @Param("createdAt") LocalDateTime createdAt
    );
}
