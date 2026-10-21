-- V2__create_user_avatar_history.sql
--
-- 对应 Go: service/user/model/user.go 的 UserAvatarHistory struct
-- 表名: users_user_avatar_history（HUMAN_ONLY.MD 2026-10-22 修改后的版本）
--
-- 列结构：
--   id           BIGSERIAL PRIMARY KEY
--   history_id   TEXT NOT NULL UNIQUE           -- 服务端 Snowflake 生成的字符串 ID（D-013）
--   uid          BIGINT NOT NULL                  -- 索引（Go gorm:"column:uid;index;not null"）
--   avatar_url   TEXT NOT NULL
--   content_type TEXT
--   size_bytes   BIGINT
--   is_current   BOOLEAN NOT NULL DEFAULT FALSE   -- 索引（Go 端 gorm:"column:is_current;index;not null;default:false"）
--   create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP

-- ⚠️ 2026-10-22（E-3 根因 R3）：字符串列从 VARCHAR(255) 改成 **TEXT**。
--   原注释里的「与 @Column 注解严格对齐」正是**漂移的来源**：Go 的 GORM AutoMigrate
--   给 Go `string` 字段生成的是 `text`（无上限），不是 varchar(255)。
--   `ALTER ... TYPE text` 对已存在的列恒成功且无需 USING 子句，可反复重放。

CREATE TABLE IF NOT EXISTS users_user_avatar_history (
    id           BIGSERIAL PRIMARY KEY,
    history_id   TEXT NOT NULL UNIQUE,
    uid          BIGINT NOT NULL,
    avatar_url   TEXT NOT NULL,
    content_type TEXT,
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

-- R3 自愈：把历史上按 VARCHAR(255) 建出来的列就地放宽成 TEXT（幂等，可反复重放）
ALTER TABLE users_user_avatar_history ALTER COLUMN history_id   TYPE text;
ALTER TABLE users_user_avatar_history ALTER COLUMN avatar_url   TYPE text;
ALTER TABLE users_user_avatar_history ALTER COLUMN content_type TYPE text;