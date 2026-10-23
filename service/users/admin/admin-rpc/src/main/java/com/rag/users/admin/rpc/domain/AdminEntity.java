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
 * <p>对应 Go 端 internal/model/data_model.go 的 Admin struct。
 *
 * <p><b>⚠️ 表名两侧不同（不是笔误）</b>：Go 的 {@code Admin.TableName()} 返回
 * <b>{@code admins}</b>（{@code service/user/admin/rpc/internal/model/data_model.go:16-18}），
 * 本实体映射的是 <b>{@code admin_admin}</b>（{@code users-common/.../03_create_admin.sql:48}）。
 * ⇒ 这是 users 域统一的表名加前缀约定（同族的还有 {@code users → users_user}、
 * {@code admin_invites → admin_invite}），**两侧物理上不共用 schema**，
 * 所以对拍只能落在 HTTP wire 层（{@code tools/difftest/README.md} §1）。
 *
 * <p>L1 表结构（权威来源＝共用 DDL {@code users-common/src/main/resources/db/03_create_admin.sql}）：
 * <pre>
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE         -- Snowflake uid（admin-rpc 业务主键）
 *   username    TEXT UNIQUE                    -- nullable（admin 可没 username）
 *   password    TEXT                           -- bcrypt 哈希（PasswordUtil.encrypt）
 *   email       TEXT UNIQUE                    -- nullable（1:1 保留 Go 端 *string 语义）
 *   extra_info  JSONB                          -- JsonbTypeHandler
 *   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p><b>⚠️ 两个时间列是 {@code timestamp}（无时区），Go 侧是 {@code timestamptz}</b>
 * —— 属 DRIFT-ARPC-004 ②③，**修法归属 {@code users-common} 的 DDL**（本组件无 DDL 所有权）。
 * Go 出处：{@code data_model.go:12-13} 的 {@code time.Time} 无 type tag
 * ⇒ GORM 的 postgres driver（{@code postgres.go:251-256}）生成 {@code timestamptz}。
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} → MyBatis-Plus {@code JsonbTypeHandler}
 *       （Go 侧是 {@code serializer:json} 写进 <b>text</b> 列 ⇒ 边界行为不同，见 DRIFT-ARPC-004 ①）</li>
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

    @TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
