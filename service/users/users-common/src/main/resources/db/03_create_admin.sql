-- ============================================================
-- 迁移归属（DRIFT-NEW-015，2026-10-21）
--
-- 本文件原是 admin/admin-rpc 的 V1__create_admin.sql，重编号为 **V3**。
-- 全部 users 域 DDL 收归 users-common 的这一个目录，原因：
--   admin-rpc **也读写 users_user 表**（BanUser / DeleteUser / ResetUserPassword /
--   UpdateUser 业务），所以它与 user-rpc **必须连同一个库**；
--   而 flyway_schema_history 挂在 schema 上、一个 schema 只能有一本账。
--   两个模块各维护一套 classpath:db/migration 时 V1/V2 **版本号相同、内容不同**，
--   共用库必然出���（实测见 AGENTS.md §6.2.5 DRIFT-NEW-015）。
--   ⇒ 单一账本 + 单一脚本集，谁先启动都正确。
--
--   Go 出处：admin 表对应 Go service/user/admin/rpc/internal/model/admin.go 的
--   Admin struct（GORM 默认表名 admin_admin）。
--
-- 全局编号（**不要在没有同步改全部脚本的情况下插号**，改版本号等于改 checksum）：
--   V1  create_user                  users_user                    （原 user-rpc 的 V1）
--   V2  create_user_avatar_history   users_user_avatar_history     （原 user-rpc 的 V2）
--   V3  create_admin                 admin_admin                   （原 admin-rpc 的 V1）
--   V4  create_admin_invite          admin_invite                  （原 admin-rpc 的 V2）
--
-- ⚠️ 原 admin-rpc 的 V3__create_user.sql 已删除：它与 V1 建的是同一张表，
--   连 DDL 都是逐字节相同的副本（原文注释自己写着「用 IF NOT EXISTS 保证幂等：
--   user-rpc 启动时已经建过表，admin-rpc 启动时本 SQL 跳过」），
--   唯一作用就是把账本版本号顶高，让后续迁移更难插号。
-- ============================================================
-- V1__create_admin.sql
--
-- 对应 Go 端 internal/model/admin.go 的 Admin struct（GORM 默认表名 admin_admin）。
-- admin-rpc admin 表的 DDL：admin_admin
--
-- 列结构：
--   id          BIGSERIAL PRIMARY KEY (Go 端用 Snowflake 生成的 uid，但这里保留 id 自增；
--                                       uid 来自 SnowflakeBean.nextId()，作为业务主键)
--   uid         BIGINT NOT NULL UNIQUE
--   username    TEXT UNIQUE
--   password    TEXT                              -- bcrypt 哈希（PasswordUtil.encrypt）
--   email       TEXT UNIQUE                       -- nullable（1:1 保留 Go 端 *string 语义）
--   extra_info  JSONB                              -- PostgreSQL JSONB 列
--   create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
--   update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP

-- ⚠️ 2026-10-22（E-3 根因 R3）：字符串列从 VARCHAR(255) 改成 **TEXT**。
--   原注释里的「与 @Column 注解严格对齐」正是**漂移的来源**：Go 的 GORM AutoMigrate
--   给 Go `string` 字段生成的是 `text`（无上限），不是 varchar(255)。
--   `ALTER ... TYPE text` 对已存在的列恒成功且无需 USING 子句，可反复重放。

CREATE TABLE IF NOT EXISTS admin_admin (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT NOT NULL UNIQUE,
    username    TEXT UNIQUE,
    password    TEXT,
    email       TEXT UNIQUE,
    extra_info  JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 业务层大部分 findOneByXxx 都按 uid 查，所以单列索引
CREATE INDEX IF NOT EXISTS idx_admin_admin_uid ON admin_admin (uid);

-- R3 自愈：把历史上按 VARCHAR(255) 建出来的列就地放宽成 TEXT（幂等，可反复重放）
ALTER TABLE admin_admin ALTER COLUMN username TYPE text;
ALTER TABLE admin_admin ALTER COLUMN password TYPE text;
ALTER TABLE admin_admin ALTER COLUMN email    TYPE text;
