package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户编码，本地用户或后续账号用户的稳定标识
     */
    private String userCode;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户类型：LOCAL=本地用户，REGISTERED=注册用户
     */
    private String userType;

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
