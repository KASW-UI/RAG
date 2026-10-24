package com.rag.users.admin.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

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
 *   expires_at   TIMESTAMPTZ NOT NULL           -- ⚠️ 2026-10-23 由 TIMESTAMP 改来
 *   used_by_uid  BIGINT                          -- nullable，使用该 invite 的新 admin uid
 *   used_at      TIMESTAMPTZ                     -- nullable，使用时间
 *   create_time  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 *   update_time  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
 * </pre>
 *
 * <p><b>⚠️ 2026-10-23：四个时间列已由 {@code timestamp} 改为 {@code timestamptz}，
 * 实体同步 {@code LocalDateTime ⇒ OffsetDateTime}</b>，修 DRIFT-ARPC-004②。
 * Go 出处：{@code data_model.go:24,26,27,28} 的 {@code time.Time} 无 type tag
 * ⇒ {@code gorm.io/driver/postgres/postgres.go:251-256} 生成 {@code timestamptz}。
 * <b>DDL 与实体必须同一提交</b>：列改 timestamptz 而实体仍是 LocalDateTime，
 * 等于把「列类型不一致」换成「值算错」，那比现状更糟。
 *
 * <p><b>为什么 {@code expires_at} 是本域唯一可观察的时间列</b>：
 * 它参与「邀请码是否仍可用」的判定，而判定代码两侧<b>结构同构、语义不同</b>：
 * <pre>
 *   Go  createadminlogic.go:64-68        now := time.Now()
 *                                       if invite.UsedAt != nil || !invite.ExpiresAt.After(now)
 *   RAG AdminCreateAtomicService:124-127 （同构，OffsetDateTime 版）
 * </pre>
 * Go 的 {@code invite.ExpiresAt} 来自 timestamptz ⇒ <b>带 location 的绝对时刻</b>，
 * {@code .After(now)} 比绝对时刻；RAG 原先是 {@code timestamp} ⇒ <b>墙钟</b>，
 * {@code .isAfter(now)} 比墙钟。单实例同时区下两者恰好等价，
 * <b>跨时区多实例部署时会分叉</b>：实例 A（Asia/Shanghai）建码存下上海墙钟 12:00，
 * 实例 B（UTC）消费时 now=04:00 ⇒ {@code 04:00.isAfter(12:00)} 为 false
 * ⇒ <b>邀请码刚生成就被判为已过期</b>。改用 {@link OffsetDateTime} 后与 Go 同构。
 *
 * <p><b>其余三列（{@code used_at} / {@code create_time} / {@code update_time}）不可观察</b>：
 * admin.proto 全部 13 个 rpc message 里<b>没有</b>这三个字段；
 * {@code used_at} 在 RAG 侧只做 {@code != null} 判定，不参与大小比较。
 * 改它们是纯 DDL 对齐，但必须一起改 —— 同一张表里一半 timestamptz 一半 timestamp 才是病灶。
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
 * Java 端用包装类型 {@link Long} / {@link OffsetDateTime}（{@code null} 表示 SQL NULL）。
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
    private OffsetDateTime expiresAt;

    @TableField("used_by_uid")
    private Long usedByUid;

    @TableField("used_at")
    private OffsetDateTime usedAt;

    @TableField(value = "create_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime createTime;

    @TableField(value = "update_time", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private OffsetDateTime updateTime;
}
