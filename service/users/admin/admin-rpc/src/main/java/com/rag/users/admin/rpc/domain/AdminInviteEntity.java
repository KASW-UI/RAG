package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * admin invite 表的 MyBatis-Plus 实体。
 *
 * <p>对应 Go 端 internal/model/data_model.go 的 AdminInvite struct。
 *
 * <p><b>⚠️ 表名两侧不同（不是笔误）</b>：Go 的 {@code AdminInvite.TableName()} 返回
 * <b>{@code admin_invites}</b>（{@code service/user/admin/rpc/internal/model/data_model.go:31-33}），
 * 本实体映射的是 <b>{@code admin_invite}</b>（{@code users-common/.../04_create_admin_invite.sql:33}）。
 * ⇒ 与 {@code admins → admin_admin}、{@code users → users_user} 同族的表名加前缀约定，
 * **两侧物理上不共用 schema** ⇒ 对拍只能落在 HTTP wire 层。
 *
 * <p>L1 表结构（权威来源＝共用 DDL {@code users-common/src/main/resources/db/04_create_admin_invite.sql}）：
 * <pre>
 *   id           BIGSERIAL PRIMARY KEY
 *   code         TEXT NOT NULL UNIQUE            -- 前缀 "ADM-" + 8 字节 hex（大写）
 *   inviter_uid  BIGINT NOT NULL                 -- 创建邀请码的 admin uid
 *   expires_at   TIMESTAMP NOT NULL              -- ⚠️ Go 侧是 timestamptz，见下
 *   used_by_uid  BIGINT                          -- nullable，使用该 invite 的新 admin uid
 *   used_at      TIMESTAMP                       -- nullable，使用时间；⚠️ Go 侧是 timestamptz
 *   create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p><b>⚠️ 四个时间列两侧类型不同</b>（Go {@code timestamptz} vs 本侧 {@code timestamp}）
 * —— 属 DRIFT-ARPC-004 ②，<b>修法归属 {@code users-common} 的 DDL</b>（本组件无 DDL 所有权）。
 * Go 出处：{@code data_model.go:24,26,27,28} 的 {@code time.Time} 无 type tag。
 *
 * <p><b>可观察性边界</b>：{@code expires_at} 在 wire 上是 <b>Unix 秒</b>
 * （{@code CreateInviteCodeResp.expires_at}），两侧算法一致 ⇒ HTTP 层不可见差异；
 * 差异只在「跨时区多实例部署」时影响 10 分钟有效期的判定（Go 绝对时刻比较 vs 本侧墙钟比较）。
 *
 * <p>行为约束（来自 CreateInviteCode + CreateAdminLogic）：
 * <ul>
 *   <li>invite 仅在 {@code used_at IS NULL} 且 {@code expires_at > NOW} 时可用</li>
 *   <li>一旦 {@code used_by_uid} 被写入，邀请码永久作废（即使 expires_at 未到）</li>
 *   <li>CreateInviteCode 生成的 code 唯一约束冲突 → 重试最多 3 次（INVITE_CODE_MAX_RETRY）</li>
 *   <li>CreateAdmin 在事务中悲观锁 SELECT + UPDATE 原子写入（CreateAdminAtomic 4 步事务）</li>
 * </ul>
 *
 * <p>L4 实现机制差异：Go 端用 {@code *int64 / *time.Time} 表示 nullable；
 * Java 端用包装类型 {@link Long} / {@link LocalDateTime}（{@code null} 表示 SQL NULL）。
 */
@Data
@TableName("admin_invite")
public class AdminInviteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String code;

    @TableField("inviter_uid")
    private Long inviterUid;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used_by_uid")
    private Long usedByUid;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
