package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
 * <p>L1 表结构（权威来源＝共用 DDL {@code users-common/src/main/resources/db/01_create_user.sql}；
 * 与 user-rpc 端 UserEntity 共用同一张物理表）：
 * <pre>
 *   id          BIGSERIAL PRIMARY KEY
 *   uid         BIGINT NOT NULL UNIQUE
 *   username    TEXT UNIQUE
 *   password    TEXT
 *   email       TEXT UNIQUE                      -- 非 null（与 Admin.Email nullable 不同）
 *   status      BIGINT NOT NULL DEFAULT 0        -- 0=正常 1=封禁（BanUser / UnBanUser 改此列）
 *   score       INTEGER
 *   extra_info  JSONB
 *   create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 *   update_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p>⚠️ 本块原注释写 {@code VARCHAR(255)} 与 {@code TIMESTAMP}，两者<b>都已被修掉</b>：
 * 2026-10-22（E-3 根因 R3）按 Go 的 GORM AutoMigrate 改成 {@code TEXT}（无长度上限），
 * 2026-10-23（DRIFT-USERRPC-V-004）按 Go 的 {@code postgres.go:251-256} 改成 {@code TIMESTAMPTZ}。
 * 「L1 与 DDL 严格对齐」的说法已恢复为真 —— 此前它恰好是不成立的那一版。
 *
 * <p>L4 实现机制差异：与 user-rpc 端 UserEntity 字段类型逐条一致（同一张物理表两侧共同读写），
 * 但<b>仍有一处未对齐</b>：本类 {@code status = 0L} 字段初始化值未删，
 * user-rpc 端已于 2026-10-23 删除（DRIFT-USERRPC-V-001）。见 {@link #status} 的说明。
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

    /**
     * 🔴 <b>此处的 {@code = 0L} 是同款缺陷，未修</b>（跨组件待办，见台账
     * {@code docs/DRIFT/users/_shared.md}）。
     *
     * <p>user-rpc 端同名字段已因 DRIFT-USERRPC-V-001 删掉初始化值（2026-10-23）。成因：
     * MyBatis {@code DefaultResultSetHandler} 在 {@code value == null && !callSettersOnNulls}
     * 时跳过 setter（{@code callSettersOnNulls} 默认 false）⇒ {@code status IS NULL} 的行
     * 读出来是 {@code 0L} 而非 {@code null}。
     *
     * <p>Go 侧语义是「NULL 读不出来」：{@code gorm@v1.31.2/scan.go:153} 对
     * 「NULL → int64」直接返回错误 ⇒ 整条查询走 db_error 支。
     *
     * <p><b>本组件是否可观察取决于读路径</b>：admin-rpc 的读点必须逐个核到
     * 「拿到 {@code 0L} 之后会不会误判为未封禁」，<b>不能照抄 user-rpc 的结论</b>
     * （§3.1：不得拿现有 RAG 代码当对照基准）。未核完之前不改，先把缺陷标在这里。
     */
    @TableField("status")
    private Long status = 0L;

    @TableField("score")
    private Integer score;

    @TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonbTypeHandler.class)
    private Map<String, String> extraInfo;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime createTime;

    /**
     * 🔴 类型为 {@link OffsetDateTime} 而非 {@link LocalDateTime}（2026-10-23，DRIFT-USERRPC-V-004）。
     *
     * <p><b>Go 出处</b>：声明点 {@code service/user/admin/rpc/internal/model/data_model.go:12-13、27-28、44-45}
     * 共 3 组 {@code time.Time} + {@code gorm:"column:…;auto…Time"}，<b>无 type tag</b>；
     * 消费点 {@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256} 的
     * {@code case schema.Time} 在 {@code field.Precision == 0} 时返回 {@code "timestamptz"}。
     *
     * <p>与 user-rpc 端同名实体<b>共用同一张物理表</b>（两侧都建 {@code users_user}，
     * 见 §6.3 问题 1）⇒ 本字段类型必须与 user-rpc 端一致，否则同一行数据两侧读出的
     * 时间字符串不同，属跨组件漂移。
     *
     * <p>⚠️ <b>与 DDL 必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
     * 会让 JVM 默认时区参与换算，把「列类型不一致」换成「值算错」。
     * 见 {@code users-common/src/main/resources/db/01_create_user.sql:54-83}。
     */
    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime updateTime;
}
