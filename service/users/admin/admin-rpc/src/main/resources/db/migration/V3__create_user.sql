-- V3__create_user.sql
--
-- 对应 Go 端 internal/model/admin.go 的 User struct（GORM 默认表名 user_user，
-- HUMAN_ONLY.MD 2026-10-22 改名为 users_user）。
--
-- 注意：admin-rpc 也读写 users_user 表（BanUser / DeleteUser / ResetUserPassword /
-- UpdateUser 业务）。两侧 user 表 DDL 必须完全一致（user-rpc 端 V1__create_user.sql 也是这版）。
-- 用 IF NOT EXISTS 保证幂等：user-rpc 启动时已经建过表，admin-rpc 启动时本 SQL 跳过。
--
-- 列结构（与 Phase 3 UserEntity @TableField 注解严格对齐）：
--   id          BIGSERIAL PRIMARY KEY
--   uid         BIGINT NOT NULL UNIQUE
--   username    VARCHAR(255) UNIQUE
--   password    VARCHAR(255)
--   email       VARCHAR(255) UNIQUE                -- 非 null（与 Admin.Email nullable 不同）
--   status      BIGINT NOT NULL DEFAULT 0         -- 0=正常 1=封禁
--   score       INTEGER
--   extra_info  JSONB
--   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP

CREATE TABLE IF NOT EXISTS users_user (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT NOT NULL UNIQUE,
    username    VARCHAR(255) UNIQUE,
    password    VARCHAR(255),
    email       VARCHAR(255) UNIQUE,
    status      BIGINT NOT NULL DEFAULT 0,
    score       INTEGER,
    extra_info  JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 业务层大部分 findOneByXxx 都按 uid 查，所以单列索引
CREATE INDEX IF NOT EXISTS idx_users_user_uid ON users_user (uid);
