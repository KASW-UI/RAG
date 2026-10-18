package com.rag.users.admin.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 对应 Go 端 internal/model/admin.go 的 AdminInvite struct（GORM 表 admin_invites）。
 *
 * 表结构（与 Go 端 AutoMigrate 一致）：
 *   id           BIGSERIAL PRIMARY KEY
 *   code         VARCHAR NOT NULL UNIQUE     -- 前缀 "ADM-" + 8 字节 hex（大写）
 *   inviter_uid  BIGINT NOT NULL            -- 创建邀请码的 admin uid
 *   expires_at   TIMESTAMP NOT NULL
 *   used_by_uid  BIGINT                     -- nullable，使用该 invite 的新 admin uid
 *   used_at      TIMESTAMP                  -- nullable，使用时间
 *   create_time  TIMESTAMP
 *   update_time  TIMESTAMP
 *
 * 行为约束（来自 CreateInviteCodeLogic + CreateAdminLogic）：
 *   - invite 仅在 used_at IS NULL 且 expires_at > NOW 时可用
 *   - 一旦 used_by_uid 被写入，邀请码永久作废（即使 expires_at 未到）
 *   - CreateInviteCodeLogic 生成 code 后 InsertOneAdminInvite，捕获 unique violation 后重试（最多 3 次）
 *   - CreateAdminLogic 在事务中悲观锁 FindAdminInviteByCodeForUpdate + UpdateAdminInviteUsedTx 原子写入
 */
@Entity
@Table(name = "admin_invites")
public class AdminInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "inviter_uid", nullable = false)
    private Long inviterUid;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // nullable：Go 端用 *int64 / *time.Time 表示 optional；Java 端用包装类型
    @Column(name = "used_by_uid")
    private Long usedByUid;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getInviterUid() { return inviterUid; }
    public void setInviterUid(Long inviterUid) { this.inviterUid = inviterUid; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Long getUsedByUid() { return usedByUid; }
    public void setUsedByUid(Long usedByUid) { this.usedByUid = usedByUid; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}