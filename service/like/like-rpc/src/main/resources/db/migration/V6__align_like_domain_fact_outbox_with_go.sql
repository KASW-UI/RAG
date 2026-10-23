--
-- V6__align_like_domain_fact_outbox_with_go.sql
-- DRIFT-LIKE-017 的修复：把 RAG 侧的 like_domain_fact_outbox 对齐到 Go 的实际 schema。
--
-- ⚠️ 为什么是新文件而不是改 V4：
--   like-rpc 仍用 Flyway（独立账本 flyway_schema_history_like，AGENTS.md §6.3 问题 4），
--   **改已发布的 V4 会导致 checksum mismatch 而启动失败**。本项目 users 域已废弃 Flyway
--   改幂等重放，但 like-rpc 没有 —— 两条机制不能混用。故只能追加版本。
--
-- ---------------------------------------------------------------------------
-- 逐项差异与依据（Go 侧全部给到**消费点**，不是 struct tag 声明行）
-- ---------------------------------------------------------------------------

-- ===== ① 列类型：delivery_status / retry_count = BIGINT ⇒ integer =====
--
-- Go 侧这两个字段是 int32 且**无 type tag**：
--   internal/model/domain_fact.go:21  DeliveryStatus int32 `gorm:"column:delivery_status;not null;default:0;index"`
--   internal/model/domain_fact.go:22  RetryCount     int32 `gorm:"column:retry_count;not null;default:0"`
--
-- 两个消费点串起来才是结论：
--   ① gorm@v1.31.2/schema/field.go:330-341
--        if field.Size == 0 {
--            switch ... {
--            case reflect.Int, reflect.Int64, ...:      field.Size = 64
--            case reflect.Int32, reflect.Uint32, ...:    field.Size = 32   // ← int32 落这里
--            }}
--   ② gorm.io/driver/postgres@v1.6.0/postgres.go:214-235
--        case schema.Int: size := field.Size
--            case size <= 16: return "smallint"
--            case size <= 32: return "integer"        // ← 32 ⇒ integer
--            default:          return "bigint"
-- ⇒ AutoMigrate 建出来的是 **integer**。
--
-- ⚠️ **V4 原注释「GORM 对『无 size 的整数』统一取 bigint」是错的**（上一轮已证伪，
--    此处只留纠错记录）。错因：只看了 field.Size 默认分支的 Int64/Int 那一行，
--    没看 Int32 有独立分支。
--
-- ⚠️ **Go 的 003_like_fact_delivery.sql 里写的是 `smallint` / `integer`，但那不是最终类型**：
--    那两条是 `ADD COLUMN IF NOT EXISTS`，而 AutoMigrate 已经建好了列 ⇒ **整条 ALTER 是空操作**。
--    所以 Go 的实际类型始终是 AutoMigrate 产出的 **integer**。
--
-- USING 显式转型：PG 的 ALTER TYPE 默认不允许 bigint→integer 的隐式窄化，
-- 必须写 USING。取值范围上安全（这两列是 0/1/2/3 与小计数）。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'like_domain_fact_outbox'
          AND column_name = 'delivery_status'
          AND data_type = 'bigint'
    ) THEN
        ALTER TABLE like_domain_fact_outbox
            ALTER COLUMN delivery_status TYPE integer USING delivery_status::integer;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'like_domain_fact_outbox'
          AND column_name = 'retry_count'
          AND data_type = 'bigint'
    ) THEN
        ALTER TABLE like_domain_fact_outbox
            ALTER COLUMN retry_count TYPE integer USING retry_count::integer;
    END IF;
END $$;

-- ===== ② like_new_fact_has_dc_wire CHECK（Go 002）=====
-- Go：CHECK (aggregate_id IS NOT NULL AND fact_version IS NOT NULL
--            AND delivery_envelope IS NOT NULL) NOT VALID
--
-- 语义：存量行放行（002 的注释明写「Old rows are retained for
-- evidence-backed reconciliation」），但**新写入必须三件套齐全** ⇒
-- 「一个老消费者不能再悄悄造出一条无版本事实」。
--
-- NOT VALID 逐字照抄：它对 INSERT/UPDATE 照常生效，只是不回扫存量行。
-- 若改成 VALID，会在存量行不满足时直接迁移失败 —— 那是 Go 没有的行为。
--
-- 安全性已核对：RAG 的 LikeDomainFactService:157-158 调 insertFact 时
-- streamId / factVersion / envelopeJson **三者都非 null**，加约束不会打脸生产写路径。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'like_new_fact_has_dc_wire') THEN
        ALTER TABLE like_domain_fact_outbox
            ADD CONSTRAINT like_new_fact_has_dc_wire
            CHECK (aggregate_id IS NOT NULL AND fact_version IS NOT NULL
                   AND delivery_envelope IS NOT NULL) NOT VALID;
    END IF;
END $$;

-- ===== ③ legacy 部分索引（Go 002）=====
-- RAG 侧此前完全没有；fact_dispatch 的「未投递信封」查询会退化成全表扫。
CREATE INDEX IF NOT EXISTS idx_like_legacy_fact_subject_target
    ON like_domain_fact_outbox ((payload->>'subject_ref'), (payload->>'target_type'), (payload->>'target_id'))
    WHERE delivery_envelope IS NULL;

-- ===== ④ 投递复合索引（Go 003）=====
-- RAG 侧只有 idx_like_domain_fact_outbox_delivery_status (delivery_status) 单列索引
-- （对应 Go 的 `index` tag）。Go 额外有 (delivery_status, created_at, event_id) 复合索引，
-- 正是 fact_dispatch 的 WHERE delivery_status IN (0,2) AND created_at < ? ORDER BY
-- created_at LIMIT … FOR UPDATE SKIP LOCKED 所需。
-- 两者都保留：单列那条对应 Go 的 tag，不删。
CREATE INDEX IF NOT EXISTS idx_like_fact_delivery
    ON like_domain_fact_outbox (delivery_status, created_at, event_id);

-- ===== ⑤ 信封不可变触发器（Go 003）=====
-- 与 users 的 article_revision_immutable 同型：
-- 「投递元数据可变，来源身份与事务冻结的 EventSpec 不可变」。
--
-- ⚠️ 函数体与触发器形式**逐字照抄 Go**（BEFORE UPDATE / FOR EACH ROW，非 constraint trigger）：
--    改形式会让 RAISE 的时机（行改写前 vs 提交时）不同 —— Go 是「改写前就拒」，
--    constraint trigger 是「提交时才拒」⇒ 错误类型与事务失败点都会变。
-- 安全性已核对：RAG 侧三处 UPDATE 只写 delivery_status / retry_count /
-- technical_* / delivered_at，**没有一列落在下面的不可变清单里** ⇒ 不会被触发器误伤。
CREATE OR REPLACE FUNCTION reject_like_domain_fact_identity_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.fact_version IS DISTINCT FROM OLD.fact_version
       OR NEW.delivery_envelope IS DISTINCT FROM OLD.delivery_envelope THEN
        RAISE EXCEPTION 'like fact envelope is immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS like_domain_fact_identity_immutable ON like_domain_fact_outbox;
CREATE TRIGGER like_domain_fact_identity_immutable
BEFORE UPDATE ON like_domain_fact_outbox
FOR EACH ROW EXECUTE FUNCTION reject_like_domain_fact_identity_change();

-- ===== ⑥ like_fact_stream.version 范围 CHECK（Go 002）=====
-- version 是 JSON-safe 整数上界（2^53-1），因为它要进 JSON 信封。
-- 这一条是**语义约束**不是性能约束，RAG 侧此前完全没有 ⇒ 版本号可能被写成天文数字
-- 而下游 JSON 静默丢精度。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'like_fact_stream_version_range') THEN
        ALTER TABLE like_fact_stream
            ADD CONSTRAINT like_fact_stream_version_range
            CHECK (version BETWEEN 1 AND 9007199254740991);
    END IF;
END $$;