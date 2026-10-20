package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * user user 表的 MyBatis-Plus 实体（admin-rpc 端独立持有）。
 *
 * <p>对应 Go 端 internal/model/admin.go 的 User struct（GORM 默认表名 user_user，
 * HUMAN_ONLY.MD 2026-10-22 改名为 users_user）。
 *
 * <p>admin-rpc 持 User 表虽然语义上奇怪（用户表通常归 user-rpc 管），但 Go 端 admin-rpc
 * 通过 internal/model/admin.go 同时持有 User struct，本质是把 admin-rpc 当作
 * "管理员对 user 表的 CRUD 网关"：BanUser / DeleteUser / ResetUserPassword / UpdateUser
 * 都是 admin-rpc 直接读写 users_user 表。
 *
 * <p>L1 表结构冻结（与 Phase 3 V3__create_user.sql DDL 严格对齐；与 user-rpc 端 UserEntity 共用同一张物理表）：
 * <pre>
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE
 *   username    VARCHAR(255) UNIQUE
 *   password    VARCHAR(255)
 *   email       VARCHAR(255) UNIQUE             -- 非 null（与 Admin.Email nullable 不同）
 *   status      BIGINT NOT NULL DEFAULT 0      -- 0=正常 1=封禁（BanUser / UnBanUser 改此列）
 *   score       INTEGER
 *   extra_info  JSONB
 *   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>L4 实现机制差异：与 user-rpc 端 UserEntity 字段定义完全一致（同一张物理表两侧共同读写）。
 */
@Data
@TableName(value = "users_user", autoResultMap = true)
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("uid")
    private Long uid;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("email")
    private String email;

    @TableField("status")
    private Long status = 0L;

    @TableField("score")
    private Integer score;

    @TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
