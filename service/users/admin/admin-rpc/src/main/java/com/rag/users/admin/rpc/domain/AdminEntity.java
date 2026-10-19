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
 * admin admin 表的 MyBatis-Plus 实体。
 *
 * <p>对应 Go 端 internal/model/admin.go 的 Admin struct（GORM 默认表名 admin_admin）。
 *
 * <p>L1 表结构冻结（与 Phase 3 V1__create_admin.sql DDL 严格对齐）：
 * <pre>
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE         -- Snowflake uid（admin-rpc 业务主键）
 *   username    VARCHAR(255) UNIQUE            -- nullable（admin 可没 username）
 *   password    VARCHAR(255)                   -- bcrypt 哈希（PasswordUtil.encrypt）
 *   email       VARCHAR(255) UNIQUE            -- nullable（1:1 保留 Go 端 *string 语义）
 *   extra_info  JSONB                          -- @TableField(typeHandler = JacksonTypeHandler)
 *   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} → MyBatis-Plus {@code JacksonTypeHandler} + 全局配置</li>
 *   <li>JPA {@code @Entity} → MyBatis-Plus {@code @TableName}（无状态 entity，纯 POJO + Lombok @Data）</li>
 * </ul>
 *
 * <p>注意：admin.Email 在 Go 端是 {@code *string}（nullable），
 * Java 端用包装类型 {@link String}（{@code null} 表示空，{@code ""} 表示空字符串）；
 * 不写 {@code nullable=false}，MyBatis-Plus 不会强制非 null。
 */
@Data
@TableName(value = "admin_admin", autoResultMap = true)
public class AdminEntity {

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

    @TableField(value = "extra_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
