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
--   create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
--   update_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
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
    create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 业务层大部分 findOneByXxx 都按 uid 查，所以单列索引
CREATE INDEX IF NOT EXISTS idx_users_user_uid ON users_user (uid);

-- R3 自愈：把历史上按 VARCHAR(255) 建出来的列就地放宽成 TEXT（幂等，可反复重放）
ALTER TABLE users_user ALTER COLUMN username TYPE text;
ALTER TABLE users_user ALTER COLUMN password TYPE text;
ALTER TABLE users_user ALTER COLUMN email   TYPE text;

-- ---------------------------------------------------------------------------
-- 2026-10-23（DRIFT-USERRPC-V-004）：时间列从无时区的 TIMESTAMP 改成 TIMESTAMPTZ
--
-- Go 出处（消费点，不是 struct tag 声明行）：
--   声明点  service/user/user/rpc/internal/model/data_model.go:14-15、:30-31
--           service/user/admin/rpc/internal/model/data_model.go:12-13、:27-28、:44-45
--           共 8 处全是 `time.Time` + `gorm:"column:…;auto…Time"`，**无 type tag**
--   消费点  gorm.io/driver/postgres@v1.6.0/postgres.go:251-256
--             case schema.Time:
--                 if field.Precision > 0 { return fmt.Sprintf("timestamptz(%d)", field.Precision) }
--                 return "timestamptz"
--           ⇒ 无 precision 一律 **timestamptz**
--
-- 为什么必须改（不是「类型洁癖」）：
--   Go 读出后 `item.CreateTime.Format(time.RFC3339)`（user/rpc/internal/logic/avatarhelpers.go:17-18）
--   —— `time.Time` **带 location**，输出含偏移量，如 `2026-01-01T08:00:00+08:00`。
--   RAG 侧原先是 `TIMESTAMP` + `LocalDateTime` 实体，**根本没有偏移量可输出**，
--   wire 上不可能产生 Go 那种字符串 ⇒ 这是可观察的（proto 字段是 `string create_time`，
--   差异体现在**字符串内容**上）。与 message-rpc 的 DRIFT-MSG-V-007 同型。
--
-- ⚠️ **必须与「两个实体 LocalDateTime ⇒ OffsetDateTime」在同一个提交里改**：
--   timestamptz 列配 LocalDateTime 实体，会让 JVM 默认时区参与换算
--   ⇒ 把「列类型不一致」换成「值算错」，那比现状更糟。
--
-- ⚠️ **USING 子句不能省**：PG 的 timestamp → timestamptz 不是隐式转换。
--
-- ⚠️⚠️ **为什么不能照抄上面 R3 三条那样直接 ALTER**（2026-10-23 实测踩到）：
--   SchemaInitializer 每次启动都**原样重放**本文件（无版本账本，靠重放做升级），
--   而 `timestamptz AT TIME ZONE 'UTC'` 对**已经是 timestamptz** 的列并不恒等：
--     timestamptz --AT TIME ZONE 'UTC'--> timestamp(UTC 墙钟)
--                  --按会话 TimeZone 铸回--> timestamptz(可能被平移)
--   会话时区非 UTC 时，**每启动一次整列时刻就偏移一次，且永不收敛**。
--   上面三条 R3 自愈没这个问题：varchar→text 是超集、无 USING、不重新解释取值。
--   ⇒ **「照抄 R3 模式」只在语义同型时成立 —— 外形像不等于语义像。**
--
--   ❌ 最初想用 `DO $$ … $$` + information_schema 做条件守卫（只在列真是无时区类型时才转）。
--      **实测不可行，已回退**：`SchemaInitializer` 用 Spring `ScriptUtils` 切分语句（分隔符 `;`），
--      而 spring-jdbc 7.0.9 的 ScriptUtils **没有 PostgreSQL 美元引号支持**
--      （反编译确认常量池里不存在 `$$`）⇒ 块内分号照样被切断，DDL 语法错、
--      applyAll 抛异常 ⇒ **user-rpc 与 admin-rpc 双双起不来**（34 个测试全红在启动阶段）。
--      ⇒ 「无版本账本的 ScriptUtils 执行器」+「PL/pgSQL 块」= 不兼容组合，不要再试。
--
--   ✅ 改为：把会话时区**显式钉成 UTC** 再转，转完 `RESET` 还原 ⇒ 两条路径都恒等，无需条件分支：
--        首迁  timestamp 08:00 --AT TIME ZONE 'UTC'--> 08:00+00（按 UTC 解释，正确）
--        重放  08:00+00    --AT TIME ZONE 'UTC'--> 08:00 --(TZ=UTC 铸回)--> 08:00+00（恒等）
--      代价：这几条语句期间会话 TZ 是 UTC；`RESET TIME ZONE` 还原成库默认值，
--      连接归还连接池后的行为与改动前一致。
SET TIME ZONE 'UTC';

ALTER TABLE users_user ALTER COLUMN create_time TYPE TIMESTAMPTZ USING create_time AT TIME ZONE 'UTC';
ALTER TABLE users_user ALTER COLUMN update_time TYPE TIMESTAMPTZ USING update_time AT TIME ZONE 'UTC';

RESET TIME ZONE;
