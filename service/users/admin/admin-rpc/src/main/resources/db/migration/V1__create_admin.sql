-- V1__create_admin.sql
--
-- 对应 Go 端 internal/model/admin.go 的 Admin struct（GORM 默认表名 admin_admin）。
-- admin-rpc admin 表的 DDL：admin_admin
--
-- 列结构（与 Phase 3 AdminEntity @TableField 注解严格对齐）：
--   id          BIGSERIAL PRIMARY KEY (Go 端用 Snowflake 生成的 uid，但这里保留 id 自增；
--                                       uid 来自 SnowflakeBean.nextId()，作为业务主键)
--   uid         BIGINT NOT NULL UNIQUE
--   username    VARCHAR(255) UNIQUE
--   password    VARCHAR(255)                       -- bcrypt 哈希（PasswordUtil.encrypt）
--   email       VARCHAR(255) UNIQUE                -- nullable（1:1 保留 Go 端 *string 语义）
--   extra_info  JSONB                              -- PostgreSQL JSONB 列
--   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP

CREATE TABLE IF NOT EXISTS admin_admin (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT NOT NULL UNIQUE,
    username    VARCHAR(255) UNIQUE,
    password    VARCHAR(255),
    email       VARCHAR(255) UNIQUE,
    extra_info  JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 业务层大部分 findOneByXxx 都按 uid 查，所以单列索引
CREATE INDEX IF NOT EXISTS idx_admin_admin_uid ON admin_admin (uid);
