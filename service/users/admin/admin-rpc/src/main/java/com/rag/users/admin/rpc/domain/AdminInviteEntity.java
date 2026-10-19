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
 * <p>对应 Go 端 internal/model/admin.go 的 AdminInvite struct（GORM 默认表名 admin_invite）。
 *
 * <p>L1 表结构冻结（与 Phase 3 V2__create_admin_invite.sql DDL 严格对齐）：
 * <pre>
 *   id           BIGSERIAL PRIMARY KEY
 *   code         VARCHAR(255) NOT NULL UNIQUE  -- 前缀 "ADM-" + 8 字节 hex（大写）
 *   inviter_uid  BIGINT NOT NULL               -- 创建邀请码的 admin uid
 *   expires_at   TIMESTAMP NOT NULL
 *   used_by_uid  BIGINT                        -- nullable，使用该 invite 的新 admin uid
 *   used_at      TIMESTAMP                     -- nullable，使用时间
 *   create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * </pre>
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
