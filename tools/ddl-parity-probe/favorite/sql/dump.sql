-- DDL 双侧活体对拍 · 规范化 schema dump（**两侧共用同一份**）
--
-- ⚠️ 本文件是探针的**唯一真源**：Go 侧用 gorm Raw 读它，RAG 侧用 psql -f 跑它
--    ⇒ 两侧不可能「各说一半」（若两侧各自写查询，查询本身的差异就会伪装成 schema 差异）。
--
-- 输出两类行：
--   S|<table>|<kind>|<payload>   —— **语义行**：列属性 / 索引语义 / 约束语义 / 触发器语义 / 函数体哈希 / 表
--                                   ⇒ 不含任何对象名 ⇒ 两侧必须逐行一致
--   N|<table>|<kind>|<name>      —— **命名行**：对象名
--                                   ⇒ 两侧可能不同（纯命名层）⇒ 由 run.sh 配对后进 OBS 段
--
-- 为什么把「语义」与「名字」拆成两类：
--   PG 对「内联约束」与「ALTER ADD CONSTRAINT」给的**自动名不同**（`..._check` vs 自定义名），
--   GORM 对未命名的 uniqueIndex 给 `idx_<table>_<column>` ⇒ 两侧名字天然不同。
--   ⇒ 若把名字混进语义行 ⇒ 每次都比出一堆**假差异** ⇒ 真差异会被淹没。
--   ⇒ 拆开后：**语义不一致 = 真漂移**；**仅名字不同 = 命名层差异**（登记后冻结）。
--
-- 归一化只做两件事（都是**必做**，否则会出现假差异）：
--   ① 去掉 `current_database() || '.'` —— 序列名里带库名，两侧库名不同
--   ② 去掉 `public.` 前缀 —— 两侧 schema 名相同但 PG 输出里可能带前缀

-- ── 语义行 ────────────────────────────────────────────────────────────────
SELECT l FROM (

    -- ① 列：序号 / 类型 / 长度 / 可空性 / DEFAULT
    SELECT 'S|' || c.table_name || '|col|' || c.ordinal_position || '|' || c.data_type || '|' ||
           COALESCE(c.character_maximum_length::text, '-') || '|' || c.is_nullable || '|' ||
           COALESCE(replace(c.column_default, current_database() || '.', ''), '') AS l
    FROM information_schema.columns c
    WHERE c.table_schema = current_schema()
      AND c.table_name NOT LIKE 'flyway_schema_history%'

    -- ② 索引语义：唯一性 + 列表达式 + 部分索引条件（**去掉了索引名**）
    UNION ALL
    SELECT 'S|' || i.tablename || '|idx|' ||
           replace(
             replace(
               replace(i.indexdef, 'INDEX ' || i.indexname || ' ', 'INDEX <NAME> '),
               current_database() || '.', ''),
             'public.', '') AS l
    FROM pg_indexes i
    WHERE i.schemaname = current_schema()
      AND i.tablename NOT LIKE 'flyway_schema_history%'

    -- ③ 约束语义：contype + 定义（pg_get_constraintdef **不含**约束名 ⇒ 天然去名）
    UNION ALL
    SELECT 'S|' || c.relname || '|con|' || con.contype::text || '|' ||
           replace(replace(pg_get_constraintdef(con.oid), current_database() || '.', ''), 'public.', '') AS l
    FROM pg_constraint con
    JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname NOT LIKE 'flyway_schema_history%'

    -- ④ 触发器语义：时机 / 事件 / 粒度（**不可变触发器是行为级的**）
    UNION ALL
    SELECT 'S|' || c.relname || '|trg|' ||
           CASE WHEN (t.tgtype & 2) <> 0 THEN 'BEFORE'
                WHEN (t.tgtype & 64) <> 0 THEN 'INSTEAD' ELSE 'AFTER' END || '|' ||
           CASE WHEN (t.tgtype & 4) <> 0 THEN 'INSERT+' ELSE '' END ||
           CASE WHEN (t.tgtype & 8) <> 0 THEN 'DELETE+' ELSE '' END ||
           CASE WHEN (t.tgtype & 16) <> 0 THEN 'UPDATE+' ELSE '' END ||
           CASE WHEN (t.tgtype & 32) <> 0 THEN 'TRUNCATE+' ELSE '' END || '|' ||
           CASE WHEN (t.tgtype & 1) <> 0 THEN 'ROW' ELSE 'STATEMENT' END AS l
    FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND NOT t.tgisinternal
      AND c.relname NOT LIKE 'flyway_schema_history%'

    -- ⑤ 触发器函数体：**只比哈希**（函数名两侧同名，比全文会引入噪声）
    UNION ALL
    SELECT 'S|' || p.proname || '|func|' || md5(p.prosrc) AS l
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = current_schema()

    -- ⑥ 表：表集合不一致是最粗的漂移
    UNION ALL
    SELECT 'S|' || c.relname || '|tbl|' ||
           CASE c.relkind WHEN 'r' THEN 'table' WHEN 'S' THEN 'sequence' ELSE c.relkind::text END AS l
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relkind IN ('r', 'S')
      AND c.relname NOT LIKE 'flyway_schema_history%'
) t ORDER BY 1;
