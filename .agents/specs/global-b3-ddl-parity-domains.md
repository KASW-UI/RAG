<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 171b38b -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global B3 —— X-7「DDL 双侧活体逐列对照」部分闭合

> **PR 范围**：`align/global` → `do-my-own-gh`
> **域**：`global`（收口域，§3.5）· **议题档**：`cross-domain`（X-7 / **X-10**）
> **基线**：`align/global` @ `171b38b`
> **写权**：`tools/difftest/check-ddl-static.py` + `docs/DRIFT/global/`
> ⇒ **不碰** 任何域的业务代码（含 X-10 的修法，它落在 `service/message/message-rpc` ⇒ 属 message 域）

---

## 一、要解决什么

**X-7 登记为「🔴 未修 —— `check-ddl-static.py` 只有 4 域预设 ⇒ 其余域只到源码级」。**

本轮先问一个更前置的问题：**那 4 个「有预设」的域，现在真的能跑吗？**

⇒ **答：不能。** 实测查出 **2 个域的预设已失效**（users / message），
⇒ 也就是说 X-7 的真实缺口比台账写的更严重：**不是「只覆盖 4 个域」，而是「可跑的只有 1 个域」**。

---

## 二、改什么

### ① 修好失效的域预设（工具改动）

| 域 | 原预设 | 实测现状 | 处置 |
|---|---|---|---|
| `users` | `difftest-postgres` / `go_db`,`rag_db` | 容器已改名 `uudifftest-postgres`；库名带 `uu_` 前缀 | 订正 |
| `message` | `msgddl-pg` / `go_msg_db`,`rag_msg_db` / 用户 `admin` | 容器是 `msg_ddl_pg`（下划线）；库是 `go_db`,`rag_db`；**该容器没有 `admin` 角色** | 三重订正 |
| `task` | `taskprobe-postgres` / `task_go_db`,`task_rag_db` | ✅ 仍有效 | 不动 |

⇒ 脚本的 `load()` / `load_indexes()` / `load_constraints()` 原把 psql 用户**写死成 `admin`**
⇒ 改为**按域预设**传 `pg_user`（默认 `admin`）。

### ② 扩域：新增 `comment` 预设

用现成的 `cdifftest-postgres`（`go_comment` / `rag_comment`）⇒ **覆盖域数 3 → 4**。

---

## 三、实测结论（跑出来的，不是推的）

| 域 | 结论 |
|---|---|
| `task` | ✅ **11 列逐字相同**；类型/长度 0、可空性 0、DEFAULT 0；索引 2≡2；约束逐条一致（**仅约束名不同** ⇒ 命名差异） |
| `message` | ✅ **37 列逐字相同**；类型/长度 0、可空性 0；🔴 **DEFAULT 6 处**（Go 无 / RAG `CURRENT_TIMESTAMP`）= **已知且刻意保留**；🔴 **索引列 1 处不同** ⇒ **X-10**；约束 3/3 一致 |
| `users` | 🟡 Go 侧**没有** admin 表（`service/user/admin/rpc/internal/model/data_model.go:17,32` 的 `admins`/`admin_invites`）⇒ 那套库里 **Go 侧未被 AutoMigrate** ⇒ **环境缺口，不是漂移** |
| `comment` | 🟡 Go 侧 `report_record`（`service/comment/rpc/internal/model/data_model.go:66`）**未被 migrate** ⇒ 环境缺口 |

⇒ **X-7 的定性变了**：不再是「不知道差在哪」，而是
「**工具与 4 个域的环境已就绪；剩下 5 个域（article/like/hot/follow/favorite）缺双侧库**」
⇒ 缺口是**环境供给**，不是**审计方法**。

---

## 四、🔴 新发现 X-10 —— `message` 域索引**两侧列不同**

| | 定义 |
|---|---|
| **Go** | `idx_notification_recipient_read ... USING btree (is_read)`（真 PG 活体） |
| **RAG** | `idx_notification_recipient_read ... USING btree (recipient_id, is_read)`（真 PG 活体） |

**根因（Go 侧的一处声明笔误）**：
`service/message/rpc/internal/model/data_model.go:32,38,41`
⇒ `is_read` 声明的复合索引 `idx_notification_recipient_read` **只有 `priority:2`、没有 `priority:1`**；
而 `recipient_id` 的 `priority:1` 被写到了**另一个索引名**（`idx_notification_recipient_time`）上
⇒ GORM 只建出**单列索引** `(is_read)`；RAG 建的是「本意」的复合索引。

⇒ **处置**：修法落在 `service/message/message-rpc` 的迁移脚本 ⇒ **属 message 域**
⇒ `global` **不写别域业务代码** ⇒ **只记账 + 等用户拍板**。

---

## 五、守门 / 复现

```bash
python3 tools/difftest/check-ddl-static.py --domain users    # 环境缺口（Go 侧 admin 表未 migrate）
python3 tools/difftest/check-ddl-static.py --domain task     # ✅ 11 列逐字相同
python3 tools/difftest/check-ddl-static.py --domain message  # ✅ 37 列逐字相同 + X-10
python3 tools/difftest/check-ddl-static.py --domain comment  # 环境缺口（Go 侧 report_record 未 migrate）
python3 tools/difftest/check-ddl-static.py --domain <d> --indexes   # 追加索引/约束对照
```

⚠️ **结论只覆盖「键集合一致」的那部分**：
`users` / `comment` 当前是**列集合不一致**（环境缺口）⇒ 它们的输出**不能**读成「DDL 已一致」。

---

## 六、**不在**本次范围

| 项 | 为什么 |
|---|---|
| article / like / hot / follow / favorite 五域的 DDL 对照 | 机器上**没有**这 5 个域的「Go 侧 AutoMigrate 库 + RAG 侧 Flyway 库」成对容器 ⇒ 缺 runner ⇒ **登记为待办**（不假装已覆盖） |
| X-10 的修法 | 属 `message` 域的迁移脚本 ⇒ global 无写权 ⇒ **等用户拍板** |
| `DEFAULT CURRENT_TIMESTAMP` 的 6 处差异 | **已知且刻意保留**（两侧 INSERT 都显式绑定 ⇒ AGENTS.md §0 已登记）⇒ 不是新漂移 |
| 约束名差异（`task_progress_pkey` vs `pk_task_progress`） | 语义等价（都是 PRIMARY KEY）⇒ **命名差异**，不是行为差异 |
| B/C 其它条目 | 用户明确「先等下」 |
