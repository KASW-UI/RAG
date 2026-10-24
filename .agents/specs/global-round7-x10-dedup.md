# global 第七轮 —— X-10 重复登记去重

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`228da5c`
> **性质**：**销账 / 指针订正**（不是新增修复，不是新增签字）

---

## 一、要解决什么

2026-10-25 第四轮（`228da5c`）把 **X-10**（`message` 域索引 `idx_notification_recipient_read` 两侧列不同）
登记为新签字 **`SD-2026-10-25-03`**。

**复查发现：这条偏离早在 2026-10-23 就已被签字过** —— message 域的
**`SD-2026-10-23-06`**（`docs/Intentional_Deviations/message/rpc.md:141`），
且 `docs/DRIFT/message/rpc.md:1489` 的 `DRIFT-MSG-V-022` 早已标注「已签字」并指向它。

⇒ **本轮重复签了一次**。按 §5.3b 硬边界 2「一条问题只在一个档落笔」，重复条目必须撤销。

---

## 二、重复判定的逐条依据

| 判据 | `SD-2026-10-23-06`（既有） | `SD-2026-10-25-03`（本轮误签） | 是否同条 |
|---|---|---|---|
| 对象 | `idx_notification_recipient_read` 列数 | 同 | ✅ |
| Go 侧 | 1 列 `(is_read)` | 同 | ✅ |
| RAG 侧 | 2 列 `(recipient_id, is_read)` | 同 | ✅ |
| 根因 | Go gorm tag 的 `priority:1` 挂到了另一个索引名 | 同 | ✅ |
| 保留理由 | 真实过滤 `WHERE recipient_id = ? AND is_read = false` | 同 | ✅ |
| 签字日 | **2026-10-23** | 2026-10-25 | ⇒ **重复** |

⚠️ **附带发现**：`SD-2026-10-23-06` 这个编号在 **3 个域各有含义**
（`like/rpc.md` / `message/rpc.md` / `users/admin/rpc.md`）⇒ **编号不可寻址**
（`docs/Intentional_Deviations/_INDEX.md:44` 已记载该口径）⇒ 引用必须**带域限定**。

---

## 三、改动

| # | 改动 | 落点 |
|---|---|---|
| ① | `SD-2026-10-25-03` **撤销**，改为指向 `SD-2026-10-23-06` 的指针 | `docs/Intentional_Deviations/global/cross-domain.md` |
| ② | DRIFT 侧 X-10 的「决策记录」指针改指真实记录处 | `docs/DRIFT/global/cross-domain.md` |
| ③ | `_README.md` 的域表 + 变更记录同步订正 | `docs/DRIFT/global/_README.md` |
| ④ | 迁移脚本注释的指针改指 `SD-2026-10-23-06`（**纯注释，DDL 未动**） | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql` |

### 保留（本轮 B3 的增量证据，不随撤销丢弃）

Go 侧 `service/message/rpc/internal/model/message.go` 的 **4 处**查询形状逐条核实：
`CountNotifications`(`:30,:32`) · `ListNotifications`(`:40,:42`) · 未读计数(`:52`) · `MarkAllNotificationsRead`(`:89`)
⇒ **全部落在 `(recipient_id, is_read)` 的前缀上** ⇒ 改回单列会让这 4 处全部退化
⇒ **签字保留 RAG 复合索引是有实测依据的**。

---

## 四、实测依据

| 项 | 结果 |
|---|---|
| 迁移改动性质 | `git diff` 过滤掉 `--` 注释行后 **为空** ⇒ **纯注释**，DDL 零变化 |
| checksum 风险 | 全环境扫描：唯一 Flyway 账本是 `postgres/first_db` 的 `flyway_schema_history_task`（**task 域** V1/V2）；`msg_ddl_pg` 的 `go_db`/`rag_db` **均无账本**（手工建的对照库）⇒ **message 域迁移从未被 Flyway 应用过** ⇒ 无 checksum mismatch |
| 迁移可执行性 | `mvn -o -pl service/message/message-rpc -am test` ⇒ **BUILD SUCCESS · Tests run: 76, Failures: 0, Errors: 0**（含 `MessageFlywaySharedSchemaWiringTest` 等真 PG 容器测试 ⇒ 迁移在全新库上实际跑通） |

---

## 五、守门

⚠️ **本条无行为守门网**（索引定义不改变任何可观察行为 ⇒ 无行为断言可写）——
与 `SD-2026-10-25-02` 同款。
⇒ 能守的只有**「别把它悄悄改回单列」**：迁移脚本留指针 + 签字档是唯一记录处。

---

## 六、Go 出处

- 索引声明：`Sea-RideTheWind_golang/service/message/rpc/internal/model/data_model.go:32,38,41`
- 查询形状：`Sea-RideTheWind_golang/service/message/rpc/internal/model/message.go:30,32,40,42,52,89`

---

## 七、验证

- [x] `mvn -o -pl service/message/message-rpc -am test` ⇒ **76 条全绿 / BUILD SUCCESS**
- [x] `git diff` ⇒ 迁移脚本改动**纯注释**
- [x] 全环境 Flyway 账本扫描 ⇒ 无 message 域已应用记录
- [x] 三处指针（签字档 / DRIFT 档 / 迁移脚本）一致指向 `SD-2026-10-23-06`
- [x] `docs/Intentional_Deviations/global/cross-domain.md` 变更记录已加撤销行

---

## 八、边界声明

- **未改任何业务代码** ⇒ 无 wire 行为变化。
- **未产生新签字** ⇒ 用户 2026-10-25 的「保留 RAG 的复合索引并签字」**早已生效**（2026-10-23），本轮只是把指针指对。
- **未碰共享面**（三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`）。
- **未碰 `favorite`**。
