package com.sazare.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Tag {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 标签类型：SCENE=场景标签，FUNCTION=功能标签
     */
    private String tagType;

    /**
     * 父标签ID，用于一级、二级标签层级关系，由代码维护有效性
     */
    private Long parentId;

    /**
     * 标签编码，供后端和AI稳定识别使用
     */
    private String code;

    /**
     * 标签中文名称
     */
    private String name;

    /**
     * 标签说明
     */
    private String description;

    private String nameEn;

    private String descriptionEn;

    /**
     * 排序值，数值越小越靠前
     */
    private Integer sortOrder;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
