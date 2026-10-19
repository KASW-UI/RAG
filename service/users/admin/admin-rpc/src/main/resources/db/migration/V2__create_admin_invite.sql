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
