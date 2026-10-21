-- ============================================================
-- 迁移归属（DRIFT-NEW-015，2026-10-21）
--
-- 本文件原是 admin/admin-rpc 的 V2__create_admin_invite.sql，重编号为 **V4**。
-- 收归 users-common 单一迁移目录的原因见同目录 V3__create_admin.sql 的头部说明。
--
-- Go 出处：对应 Go service/user/admin/rpc/internal/model/admin.go 的
-- AdminInvite struct（GORM 默认表名 admin_invite）。
--
-- ⚠️ CreateAdmin 的 4 步事务里对这张表做 W1 行级悲观锁（SELECT ... FOR UPDATE），
--    所以 code 列上的唯一约束与索引都不能退化。
-- ============================================================
-- V2__create_admin_invite.sql
--
-- 对应 Go 端 internal/model/admin.go 的 AdminInvite struct（GORM 默认表名 admin_invite）。
-- admin-rpc invite 表的 DDL：admin_invite
--
-- 列结构（与 Phase 3 AdminInviteEntity @TableField 注解严格对齐）：
--   id           BIGSERIAL PRIMARY KEY
--   code         VARCHAR(255) NOT NULL UNIQUE      -- 前缀 "ADM-" + 8 字节 hex（大写）
--   inviter_uid  BIGINT NOT NULL                  -- 创建邀请码的 admin uid
--   expires_at   TIMESTAMP NOT NULL
--   used_by_uid  BIGINT                           -- nullable，使用该 invite 的新 admin uid
--   used_at      TIMESTAMP                        -- nullable，使用时间
--   create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP

CREATE TABLE IF NOT EXISTS admin_invite (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(255) NOT NULL UNIQUE,
    inviter_uid  BIGINT NOT NULL,
    expires_at   TIMESTAMP NOT NULL,
    used_by_uid  BIGINT,
    used_at      TIMESTAMP,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引：业务逻辑按 code 查（CreateAdmin 4 步事务 W1 悲观锁）
CREATE INDEX IF NOT EXISTS idx_admin_invite_code ON admin_invite (code);
