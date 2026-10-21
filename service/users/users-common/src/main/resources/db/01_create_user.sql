-- V1__create_user.sql
--
-- 对应 Go: service/user/model/user.go 的 User struct（AutoMigrate 生成）
-- 表名: users_user（HUMAN_ONLY.MD 2026-10-22 修改后的版本）
--
-- 列结构：
--   id          BIGSERIAL PRIMARY KEY (Go 端用 Snowflake 生成的 uid，但这里保留 id 自增；
--                                       uid 来自 SnowflakeBean.nextId()，作为业务主键)
--   uid         BIGINT NOT NULL UNIQUE
--   username    TEXT UNIQUE
--   password    TEXT
--   email       TEXT UNIQUE
--   status      BIGINT NOT NULL DEFAULT 0
--   score       INTEGER
--   extra_info  JSONB                              -- PostgreSQL JSONB 列
--   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--
-- ⚠️ 2026-10-22（E-3 根因 R3）：字符串列从 VARCHAR(255) 改成 **TEXT**。
--
--   原注释写「列结构与原 JPA @Column 注解严格对齐」—— 那正是**漂移的来源**，
--   不是对齐的目标。Go 的 GORM AutoMigrate 给 Go 的 `string` 字段生成的是
--   **`text`**（无长度上限），不是 varchar(255)。实测（tools/difftest/check-ddl-static.py）
--   两侧 35 列里 22 列类型不同，本表占 6 列。
--   后果是可见的：5000 字符的 username 在 Go 写成功，在 RAG 被 PG 拒
--   （22001 value too long）⇒ 5001；反噬读路径时 Go 命中、RAG 报「用户不存在」。
--
--   `ALTER TABLE ... ALTER COLUMN ... TYPE text` 在 PG 里对已存在的列**恒成功**
--   （text 是 varchar 的超集，且不需要 USING 子句），所以下面三条可以每次启动原样重放，
--   让**已存在的库自愈**—— 这正是 SchemaInitializer 无版本账本模式下
--   「建表」之外还能承担「升级」的唯一手段。

CREATE TABLE IF NOT EXISTS users_user (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT NOT NULL UNIQUE,
    username    TEXT UNIQUE,
    password    TEXT,
    email       TEXT UNIQUE,
    status      BIGINT NOT NULL DEFAULT 0,
    score       INTEGER,
    extra_info  JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 业务层大部分 findOneByXxx 都按 uid 查，所以单列索引
CREATE INDEX IF NOT EXISTS idx_users_user_uid ON users_user (uid);

-- R3 自愈：把历史上按 VARCHAR(255) 建出来的列就地放宽成 TEXT（幂等，可反复重放）
ALTER TABLE users_user ALTER COLUMN username TYPE text;
ALTER TABLE users_user ALTER COLUMN password TYPE text;
ALTER TABLE users_user ALTER COLUMN email   TYPE text;