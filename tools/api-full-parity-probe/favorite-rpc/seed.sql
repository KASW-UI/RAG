-- favorite 域 · gRPC 层双侧活体对拍探针 · 种子数据（**两侧同一份**）
--
-- ⚠️ **绝不写自增代理键 `id`**（tools/grpc-contract-probe/favorite/README.md 坑 #4）：
--   显式写 `id` **不会推进 PostgreSQL 的序列** ⇒ 种子灌完后第一次业务 INSERT
--   撞 `favorite_folder_pkey` ⇒ 而业务码把 23505 翻译成「名称已存在 5203」
--   ⇒ **症状伪装成重名误报**。⇒ 这里只写业务列，让序列自己走。
--
-- ⚠️ **时间戳写成显式绝对时刻**（+00）：
--   两侧 DSN 都带 TimeZone=Asia/Shanghai（Go: model/db.go:21-28 · RAG: application.yml:53），
--   但 timestamptz 存的是绝对时刻 ⇒ 同一个字面量在两侧是同一瞬间
--   ⇒ `UnixMilli()` 两侧必然相同（helper.go:69,83 / FavoriteRpcSupport.unixMillis）。
--
-- ⚠️ **title / cover 一律给非 NULL 值**：
--   Go 的 `toProtoItem`（helper.go:74-85）把 `Title string` 直接塞进 proto，
--   RAG 的 `toProtoItem` 用 `nullToEmpty`；NULL 列在两边的扫描语义不同
--   （GORM 的 nil→零值 vs MyBatis 的 null）⇒ **NULL 列被排除在本探针的用例之外**
--   （见 README「证据边界」）。

-- ── favorite_folder ───────────────────────────────────────────────────────
-- F1/F2 属 user 1001；F3 属 user 1002（用于「归属不符」）；F4 是 g01 的牺牲品。
INSERT INTO favorite_folder (folder_id, user_id, name, create_time, update_time) VALUES
  (7000000000000000001, 1001, '收藏夹A', '2026-01-02 03:04:05+00', '2026-01-02 03:04:05+00'),
  (7000000000000000002, 1001, '收藏夹B', '2026-01-03 03:04:05+00', '2026-01-03 03:04:06+00'),
  (7000000000000000003, 1002, '别人的收藏夹', '2026-01-04 03:04:05+00', '2026-01-04 03:04:05+00'),
  (7000000000000000004, 1001, '待删收藏夹', '2026-01-05 03:04:05+00', '2026-01-05 03:04:05+00');

-- ── favorite_item ─────────────────────────────────────────────────────────
-- F1 里两条（create_time 递减 ⇒ 列表按 create_time DESC 出「条目2、条目1」）；
-- F4 里一条（g01 级联删除的对象）。
INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type,
                           target_revision, title, cover, create_time) VALUES
  (8000000000000000001, 7000000000000000001, 1001, '9007199254740993', 'article',
   NULL, '标题一', 'https://example.invalid/a.png', '2026-01-02 04:00:00+00'),
  (8000000000000000002, 7000000000000000001, 1001, '9007199254740995', 'post',
   NULL, '标题二', '', '2026-01-02 05:00:00+00'),
  (8000000000000000003, 7000000000000000004, 1001, '9007199254740997', 'article',
   NULL, '待删条目', 'https://example.invalid/c.png', '2026-01-05 04:00:00+00');
