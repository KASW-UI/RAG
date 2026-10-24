-- DDL 双侧活体对拍 · **命名行**（与 sql/dump.sql 配对使用）
--
-- ⚠️ 同样**两侧共用同一份**：Go 侧用 gorm Raw 读它，RAG 侧用 psql -f 跑它。
--
-- 输出格式：N|<table>|<kind>|<name>|<payload>
--   payload = **配对键**：与 sql/dump.sql 里同名 kind 的语义 payload **逐字相同**
--   ⇒ 两侧按 (table, kind, payload) 就能配对 ⇒ 名字不同 = 命名层差异，
--     而 payload 不同 = 语义差异（真漂移）。
--
-- ⚠️ 归一化（与 dump.sql 完全一致，否则配不上）：
--   ① 去掉 `current_database() || '.'`
--   ② 去掉 `public.` 前缀
--   ③ 索引定义里的索引名替换成 `<NAME>` 占位符

SELECT l FROM (
    SELECT 'N|' || i.tablename || '|idx|' || i.indexname || '|' ||
           replace(
             replace(
               replace(i.indexdef, 'INDEX ' || i.indexname || ' ', 'INDEX <NAME> '),
               current_database() || '.', ''),
             'public.', '') AS l
    FROM pg_indexes i
    WHERE i.schemaname = current_schema()
      AND i.tablename NOT LIKE 'flyway_schema_history%'

    UNION ALL
    SELECT 'N|' || c.relname || '|con|' || con.conname || '|' || con.contype::text || '|' ||
           replace(replace(pg_get_constraintdef(con.oid), current_database() || '.', ''), 'public.', '') AS l
    FROM pg_constraint con
    JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()
      AND c.relname NOT LIKE 'flyway_schema_history%'

    UNION ALL
    SELECT 'N|' || c.relname || '|trg|' || t.tgname || '|' ||
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
) t ORDER BY 1;
