-- V1__create_user.sql
--
-- 对应 Go: service/user/model/user.go 的 User struct（AutoMigrate 生成）
-- 表名: users_user（HUMAN_ONLY.MD 2026-10-22 修改后的版本）
--
-- 列结构与原 JPA @Column 注解严格对齐：
--   id          BIGSERIAL PRIMARY KEY (Go 端用 Snowflake 生成的 uid，但这里保留 id 自增；
--                                       uid 来自 SnowflakeBean.nextId()，作为业务主键)
--   uid         BIGINT NOT NULL UNIQUE
--   username    VARCHAR(255) UNIQUE
--   password    VARCHAR(255)
--   email       VARCHAR(255) UNIQUE
--   status      BIGINT NOT NULL DEFAULT 0
--   score       INTEGER
--   extra_info  JSONB                              -- PostgreSQL JSONB 列
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