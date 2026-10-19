-- V2__create_user_avatar_history.sql
--
-- 对应 Go: service/user/model/user.go 的 UserAvatarHistory struct
-- 表名: users_user_avatar_history（HUMAN_ONLY.MD 2026-10-22 修改后的版本）
--
-- 列结构：
--   id           BIGSERIAL PRIMARY KEY
--   history_id   VARCHAR(255) NOT NULL UNIQUE    -- 服务端 Snowflake 生成的字符串 ID（D-013）
--   uid          BIGINT NOT NULL                  -- 索引（Go gorm:"column:uid;index;not null"）
--   avatar_url   VARCHAR(255) NOT NULL
--   content_type VARCHAR(255)
--   size_bytes   BIGINT
--   is_current   BOOLEAN NOT NULL DEFAULT FALSE   -- 索引（Go 端 gorm:"column:is_current;index;not null;default:false"）
--   create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP

CREATE TABLE IF NOT EXISTS users_user_avatar_history (
    id           BIGSERIAL PRIMARY KEY,
    history_id   VARCHAR(255) NOT NULL UNIQUE,
    uid          BIGINT NOT NULL,
    avatar_url   VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes   BIGINT,
    is_current   BOOLEAN NOT NULL DEFAULT FALSE,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引：业务逻辑按 uid 查 + 按 (uid, is_current) 找当前头像
CREATE INDEX IF NOT EXISTS idx_users_user_avatar_history_uid
    ON users_user_avatar_history (uid);
CREATE INDEX IF NOT EXISTS idx_users_user_avatar_history_uid_is_current
    ON users_user_avatar_history (uid, is_current);