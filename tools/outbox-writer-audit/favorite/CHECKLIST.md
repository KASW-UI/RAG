# V6 / V7 上线前 · 域外写入方**人工确认清单**

> **用途**：把「机器看不见的写入方」从一句提醒变成**可逐条勾掉**的清单。
> **谁来做**：favorite 域的对齐负责人 + DBA / 运维（涉及仓库外的部分）。
> **什么时候做**：`V7__remove_outbox_time_defaults.sql`（去 `DEFAULT now()`）**上线前**。
> **配套机检**：`python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py`（EXIT=0 只说明**本仓**闭合）。

---

## 一、这两张表为什么需要人工确认

| 表 | 变更 | 域外写入方的后果 |
|---|---|---|
| `favorite_fact_outbox` | **V7** 去掉 `created_at` / `updated_at` 的 `DEFAULT now()` | 直接 INSERT 而**不显式绑**这两列 ⇒ **立刻 NOT NULL 违例**（硬失败，不静默） |
| `favorite_item` | **V6** 多一道 `target_revision scope` CHECK | 写入「非法修订号」（`target_revision` 非空但 `target_type <> 'article'`）⇒ **RAG 拒（SQLSTATE 23514）/ Go 接受** ⇒ 两侧分叉 |

> ⚠️ 两条的**责任方向相反**：
> · `favorite_fact_outbox` ⇒ **写入方必须改**（否则直接报错）。
> · `favorite_item` ⇒ **写入方只要知道**（RAG 更严，不需要改 RAG；见 `DEV-FAV-RPC-007`）。

---

## 二、机器已经替我们查过的部分（不用重复查）

| 项 | 结论 | 谁验的 |
|---|---|---|
| 本仓 `favorite_fact_outbox` 的 INSERT 站点 | **9 个，全部已绑两列**（1 个生产 + 7 个测试 + 1 个探针 seed） | `check-outbox-writers.py` |
| 本仓 `favorite_item` 的 INSERT 站点 | **12 个，全部域内**（1 个生产 + 8 个测试 + 3 个探针 seed） | 同上 |
| 两张表**有没有 ORM 写入路径**（`BaseMapper<T>`） | **没有** ⇒ 只有显式 SQL 写入 ⇒ 字面量扫描即**全覆盖** | 同上（**机器判定**，不是人假设） |
| 域外写入方（本仓范围内） | **0 个** | 同上 |

---

## 三、机器**看不见**的 —— 需要人工逐条确认

> 每条都给了「去哪看」与「确认口径」。**任一条答不上来，就不能上 V7。**

### ☐ 1. 运维手册 / DBA 手册里的**一次性 SQL**

- **去哪看**：运维手册、变更记录、`psql` 命令历史、`docs/ops/`（若有）、DBA 的 SQL 归档。
- **口径**：里面有没有 `INSERT INTO favorite_fact_outbox` / `INSERT INTO favorite_item`？
  - 有 ⇒ 该命令**必须补上** `created_at` / `updated_at`（outbox），
    并确认写入的 `target_revision` 是否满足 V6 的 CHECK（item）。
- **常见形态**：补数据脚本、清库脚本、迁移脚本、临时造数。

### ☐ 2. 报表 / BI 工具

- **去哪看**：报表平台（Metabase / Superset / FineBI / 自研）的**数据集定义**与**查询**。
- **口径**：报表一般只读 ⇒ 只要**不写**就没事；但若有「导出到表」「一键修复」「同步」类功能，要确认写入目标。
- ⚠️ 报表若**读** `favorite_item.target_revision`，V6 的 CHECK **不会**影响它（只约束写入）⇒ 无需改。

### ☐ 3. 数据修复 / 迁移脚本（**仓库外**）

- **去哪看**：运维机器上的 `/opt`、`/srv`、脚本仓库、`data-fix/` 类目录；CI/CD 的流水线脚本。
- **口径**：同上；重点是**能跑在生产库上的**那些。

### ☐ 4. 另一个仓库 / 另一个服务

- **去哪看**：本项目的其它服务（users / admin / message / like / comment / follow / task）、
  以及**本仓之外**的服务（若存在）。
- **口径**：本仓内已由机检覆盖 ⇒ 剩下的只有**本仓之外**的服务。
  - ⚠️ 特别注意 **Go 真源那侧**：`Sea-RideTheWind_golang` 是冻结只读的**真源**，
    它的写入形态**不受 RAG 的 V6/V7 影响**（Go 侧本来就没有那两列 DEFAULT、也没有那道 CHECK）
    ⇒ **不是**要改的对象，只是「行为会分叉」的已知边界。

### ☐ 5. DBA 侧的**存储过程 / 触发器 / 事件触发器**

- **去哪看**：生产库的 `pg_proc` / `pg_trigger`；`information_schema.triggers`。
- **口径**：有没有对这两张表的 INSERT 型触发器或函数？
  - 有 ⇒ 触发器里若没显式绑 `created_at` / `updated_at` ⇒ **V7 之后触发器会失败**
    （而且是**运行时**才失败 ⇒ 更隐蔽）⇒ 必须改触发器。

### ☐ 6. 另一个可能写 `favorite_item` 的**角色**：第二个应用写入方

- **去哪看**：是否有别的服务直接连 `first_db` 写 favorite 域的表。
- **口径**：若有 ⇒ 告知对方两件事：
  1. `favorite_item` **多一道** CHECK（写非法修订号会被拒 23514）；
  2. `favorite_fact_outbox` 的两列**不再有 DEFAULT**，必须显式绑定。

---

## 四、确认完成后的留痕

- 把本清单的勾选结果（谁、哪天、确认了什么）追加到
  [`docs/DRIFT/favorite/rpc.md`](../../../docs/DRIFT/favorite/rpc.md) §13.6 的「V7 上线前置」小节。
- ⚠️ **不要**改共享索引（`docs/DRIFT/_INDEX.md` 等）—— 那是合并后由主会话统一更新的。

---

## 五、复现机检

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite   # align/favorite 工作树
python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py
echo $?   # 0 = 本仓范围内闭合（域外仍需按本清单人工确认）
```

> ⚠️ 机检报告里的「机器看不见的」那一段，**就是本清单存在的理由** ⇒ 两者必须一起读。
