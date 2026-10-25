package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
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
 *   extra_info  TEXT                              -- JsonTextTypeHandler（2026-10-23 由 JSONB 改来）
 *   create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p><b>⚠️ 2026-10-23：{@code extra_info} 由 {@code JSONB} 改为 {@code TEXT}</b>
 * —— 对齐 Go 的 {@code serializer:json}（{@code data_model.go:11}），修 DRIFT-ARPC-004①。
 * 双侧活体对拍实测：含 U+0000 的 extra_info，Go 落库成功、RAG 被 jsonb 以 <b>22P05</b> 拒收
 * （{@code unsupported Unicode escape sequence}）⇒ 真实可观察差异。
 * 配套改用 {@link com.rag.users.admin.rpc.mapper.JsonTextTypeHandler}。
 *
 * <p><b>⚠️ 2026-10-25（第十八轮 · global B10）：那句「同模块的 {@code UserEntity.extra_info}
 * 仍走 {@code JsonbTypeHandler}」已作废</b> —— 那一列<b>也是 text</b>：
 * {@code users-common/.../db/01_create_user.sql:41} 建表就是 {@code extra_info  TEXT}，
 * {@code :103} 每次启动幂等 {@code ALTER ... TYPE text USING extra_info::text}。
 * ⇒ 两张表的同名列<b>都是 text</b> ⇒ {@code JsonbTypeHandler} 已整体删除，
 * 本模块的 {@code extra_info} 统一走 {@code JsonTextTypeHandler}。
 *
 * <p><b>⚠️ 2026-10-23：两个时间列已由 {@code timestamp} 改为 {@code timestamptz}，
 * 实体同步 {@code LocalDateTime ⇒ OffsetDateTime}</b> —— 对齐 Go 的
 * {@code data_model.go:12-13}（{@code time.Time} 无 type tag
 * ⇒ {@code gorm.io/driver/postgres/postgres.go:251-256} 生成 {@code timestamptz}）。
 * <b>两处必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
 * 等于把「列类型不一致」换成「值算错」，那比现状更糟。
 * 本表两列<b>不可观察</b>（admin.proto 无对应字段）；同族里唯一可观察的那一列
 * 是 {@code admin_invite.expires_at}，见 {@link AdminInviteEntity}。
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>Hibernate {@code @JdbcTypeCode(SqlTypes.JSON)} → MyBatis-Plus {@code JsonTextTypeHandler}
 *       （Go 侧是 {@code serializer:json} 写进 <b>text</b> 列 ⇒ 现在两侧同构）</li>
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

    @TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonTextTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime updateTime;
}
