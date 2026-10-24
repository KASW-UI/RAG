<!-- @move AGENTS.md §〇 item 2 · DDL 列类型差异明细 · 2026-10-25 填实 -->

# DDL 列类型 / 约束差异明细（运行时权威）

> 从 AGENTS.md §〇 搬出。**AGENTS.md 只留结论 + 一行指针，逐条明细在本文件。**

## 〇、核对口径（2026-10-25 实测）

**证据强度 = 双侧活体 DDL 对拍**（不是读 DDL 文件推断）。工具：[`tools/difftest/check-ddl-static.py`](../../../tools/difftest/check-ddl-static.py)。

| 域 | 容器 | Go 库 | RAG 库 | Go 侧 schema 来源 | RAG 侧 schema 来源 |
|---|---|---|---|---|---|
| `users` | `difftest-postgres` | `go_db` | `rag_db` | **真源 GORM `AutoMigrate`** | `users-common/db/01~04_*.sql` 幂等重放 |
| `task` | `taskprobe-postgres` | `task_go_db` | `task_rag_db` | 真源 GORM `AutoMigrate` | Flyway `V1`/`V2` |
| `message` | `msgddl-pg` | `go_msg_db` | `rag_msg_db` | 真源 GORM `AutoMigrate` | Flyway `V1`~`V5` |
| `like` | —— | —— | —— | **无运行时探针** | **无运行时探针** ⇒ 只到源码级 |

**为什么必须活体对拍**（§3.3.1）：Go 侧 schema **不是手写 SQL**，是 GORM `AutoMigrate` 运行时生成的 ⇒ 索引名、可空性、DEFAULT 全由 `gorm.io/driver/postgres@v1.6.0` 的行为决定，**读 Go 源码推不出来**。消费点：`gorm.io/driver/postgres@v1.6.0/postgres.go:251-256`（`schema.Time` 无 precision ⇒ `timestamptz`）。

### ⚠️ 本次核对发现的两个「口径陷阱」（都会造成假结论）

1. **users 域检查会顺带读到 message 域的表** —— 2026-10-25「按模块独立 Flyway 账本表名」落地后，**所有 rpc 模块共用同一个 `rag_db`** ⇒ users 域的 72 列里含 message 的 13 列 ⇒ 表里已按表归属分开计。
2. **Flyway 账本表必须排除** —— RAG 侧独有 `flyway_schema_history*`（Go 侧用 AutoMigrate，没有账本表）⇒ 不排除则「仅 RAG 有」会把列集合判成不一致。
   ⚠️ **2026-10-25 实测复现了这个假阳性**：`users` 域 `exclude_tables` 原为空 ⇒ 脚本 `EXIT=2`（列集合不一致）。**已修**：改为 `("flyway_schema_history",)`（前缀匹配，一条覆盖 7 个模块的账本表）。

---

## 一、总表（2026-10-25 实测）

| 维度 | `users`（72 列） | `task`（11 列） | `message`（37 列） |
|---|---|---|---|
| **类型 / 长度** | ✅ **0 处**（72 列逐字相同） | ✅ **0 处**（11 列逐字相同） | ✅ **0 处**（37 列逐字相同） |
| **可空性（NOT NULL）** | 🔴 **1 处**（见 §二.1） | ✅ 0 处 | ✅ 0 处 |
| **DEFAULT** | 🔴 **8 处**真差（另 4 处为序列名映射，非真差） | ✅ **0 处** | 🔴 **6 处**真差 |
| **索引** | 🔴 Go 独有 3 条（真缺）+ RAG 独有 4 条（冗余） | ✅ 0 差异（仅主键名不同） | 🟡 1 条（**已签字** `SD-2026-10-23-06`） |
| **唯一约束 / 主键** | 🟡 命名形态差（语义等价，见 §二.4） | 🟡 仅主键名不同 | ✅ **3/3 逐条一致** |
| **CHECK / 触发器** | 不查（脚本不覆盖） | 不查 | 不查 |

> **结论一句话**：**列类型层面三个域全部 0 差异** —— 「12 处列类型差异」这个老口径**已被 2026-10-23 的修复轮清零**。剩下的全是**可空性 1 处 + DEFAULT 14 处 + 索引形态**，不是「类型」问题。

---

## 二、逐条明细

### 2.1 🔴 可空性差异 1 处 —— 仍未修

| 项 | 内容 |
|---|---|
| **分类** | **OBSERVABLE DIFFERENCE**（写入行为差异） |
| **位置** | `users_user.status` |
| **Go** | `is_nullable = YES`（可空） |
| **RAG** | `is_nullable = NO`（`NOT NULL`） |
| **台账** | `DRIFT-USERRPC-V-002` ⇒ [`docs/DRIFT/users/user/rpc.md`](../users/user/rpc.md) |
| **状态** | 🔴 **2026-10-25 仍未修 · 被卡**：改动必须与 admin 域同改 |

**为什么被卡**（`users/user/rpc.md:1413`）：`admin-rpc` 的 `UserEntity.status` 是 `private Long status = 0L;`，MyBatis `callSettersOnNulls=false` ⇒ 读出 `NULL` 会被当成 `0`（「活跃」）⇒ 单独放开 `users_user.status` 的 `NOT NULL` 会**立刻激活** admin 侧把 `NULL` 当活跃的缺陷 ⇒ 必须两侧一起裁决。

### 2.2 🔴 DEFAULT 差异 14 处（真差）—— 仍未修，但是**刻意保留**

**全部是同一形态**：Go 侧 `create_time`/`update_time` 等时间列**无默认**，RAG 侧是 `DEFAULT CURRENT_TIMESTAMP`。

| 域 | 表 | 列 | 计数 |
|---|---|---|---|
| `users` | `users_user` | `create_time` / `update_time` | 2 |
| `users` | `users_user_avatar_history` | `create_time` / `update_time` | 2 |
| `users` | `admin_admin` | `create_time` / `update_time` | 2 |
| `users` | `admin_invite` | `create_time` / `update_time` | 2 |
| `message` | `message_notification` | `created_at` / `updated_at` | 2 |
| `message` | `message_conversation` | `created_at` / `updated_at` / `last_message_at` | 3 |
| `message` | `message_conversation_message` | `created_at` | 1 |

**已知的 4 处「假差」**（**不是差异**）：`users_user.id` / `users_user_avatar_history.id` / `admin_admin.id` / `admin_invite.id` 的序列名（Go `admins_id_seq` ↔ RAG `admin_admin_id_seq`）—— 这是**表名映射**的副产品（两侧表名本来就不同，见 §三）⇒ 不是漂移。

**为什么保留**（`message-rpc` 的 `V5` 文件头第 58-62 行给了完整论证）：
- DEFAULT 只在「INSERT 省略该列」时发作 ⇒ **两侧所有 INSERT 都显式绑定这些列**（例：`ConversationMapper#insertConv` 的 VALUES 列表）⇒ 默认永远不触发。
- 删掉默认对**不可观测的维度**冒险（有人在某条裸 SQL 路径上依赖了默认）**不划算**。
- message 域已签字残留 ⇒ `DRIFT-MSG-V-020`（见 [`docs/DRIFT/message/rpc.md`](../message/rpc.md)）。

> ⚠️ **未签字的部分**：`users` 域那 **8 处**（4 张表 × 2 列）目前**没有**对应的签字条目或 `V-xxx` 编号 —— 与 message 域那 6 处**不同源**。⇒ 登记为**待裁决**，见 [`docs/DRIFT/_cross/open-items.md`](open-items.md)。

### 2.3 🟡 索引差异

#### 2.3.1 users 域 —— Go 独有 3 条（**真缺**）

| 索引名 | 表 | 列 | 唯一性 |
|---|---|---|---|
| `idx_admin_invites_expires_at` | `admin_invite` | `expires_at` | 非唯一 |
| `idx_admin_invites_inviter_uid` | `admin_invite` | `inviter_uid` | 非唯一 |
| `idx_user_avatar_history_is_current` | `users_user_avatar_history` | `is_current` | 非唯一 |

**分类**：**性能差异，非行为差异** —— 三条都是**非唯一**普通索引 ⇒ 缺失**不改变写入语义**（PG 只用唯一索引/约束判唯一），只改变查询计划。
**影响面**：`admin_invite` 的过期清理（`expires_at`）与邀请人维度查询、`avatar_history` 的「当前头像」查询 —— 后者**有**业务语义（`is_current` 标记当前头像），走 `WHERE uid=? AND is_current=true` ⇒ RAG 侧现靠 `idx_users_user_avatar_history_uid_is_current`（RAG 独有的复合索引）覆盖 ⇒ **实际不慢**，只是索引构成与 Go 不同。

#### 2.3.2 users 域 —— RAG 独有 4 条（**冗余，非漂移**）

| 索引名 | 表 | 列 | 唯一性 | Go 侧对应 |
|---|---|---|---|---|
| `idx_users_user_uid` | `users_user` | `uid` | 非唯一 | `idx_users_uid` ≡ `users_user_uid_key`（**唯一**） |
| `idx_admin_admin_uid` | `admin_admin` | `uid` | 非唯一 | `idx_admins_uid` ≡ `admin_admin_uid_key`（**唯一**） |
| `idx_admin_invite_code` | `admin_invite` | `code` | 非唯一 | `idx_admin_invites_code` ≡ `admin_invite_code_key`（**唯一**） |
| `idx_users_user_avatar_history_uid_is_current` | `users_user_avatar_history` | `uid` + `is_current` | 非唯一 | `idx_user_avatar_history_uid`（单列 `uid`） |

**分类**：**EQUIVALENT（多不少）** —— 每条对应列在 Go 侧**都已有等价 UNIQUE 约束** ⇒ RAG 侧是「唯一约束之外又加了一条非唯一索引」⇒ 语义等价，只多占一点写入成本。
**处置建议**：可删，但**收益小于风险**（删索引在生产大表上是 DDL 风险，收益只是省一点写入开销）⇒ **不建议动**，登记为**已知冗余**。

#### 2.3.3 message 域 —— 1 条，已签字

| 索引 | Go | RAG |
|---|---|---|
| `idx_notification_recipient_read` | `is_read`（**1 列**） | `(recipient_id, is_read)`（**2 列**） |

**分类**：**已签字的有意偏离** ⇒ `SD-2026-10-23-06`（台账 `DRIFT-MSG-V-022`）⇒ [`docs/Intentional_Deviations/message/rpc.md`](../../Intentional_Deviations/message/rpc.md)。
**保留理由**（原文）：`ListNotifications` 的真实过滤是 `WHERE recipient_id = ? AND is_read = false` ⇒ **RAG 的两列索引正好是这个前缀**，Go 的单列索引只能定位 `is_read=false` 的全部行再回表过滤 ⇒ **在这个查询形态下 RAG 更优**，回改会造成计划回退。
⚠️ **不得做的事**：不要为了让 DDL 对拍变绿而把它改回单列。

### 2.4 🟡 约束的「命名形态差」—— 语义等价，不是漂移

**Go 侧**（GORM 约定命名）用**索引式**唯一约束：`uni_users_email` / `uni_users_username` / `uni_admins_email` / `uni_admins_username` / `idx_users_uid` / `idx_admins_uid` / `idx_admin_invites_code` / `idx_user_avatar_history_history_id`，主键 `<表>_pkey`。
**RAG 侧**用 PG 默认命名：`<表>_<列>_key` / `pk_<表>`。

**判定**：脚本把「名字不同但**表 + 唯一性 + 列**完全等价」判为**等价**（`compare_indexes` 的配对逻辑）⇒ 14 组全部配对成功。
**语义影响**：**零**。唯一性判定、冲突行为、外键行为在 PG 层面完全相同 ⇒ 只是 `pg_constraint.conname` 的字符串不同。
⚠️ **唯一真正被这个差异遮住的是「多了一条非唯一索引」**（§2.3.2）—— 因为 RAG 侧 `uid` 既有唯一约束又有普通索引，看起来像「多了个非唯一索引」，但它的**唯一性由约束保证**，不靠索引。

**行数差异说明**：`pg_constraint` Go 侧 11 行 / RAG 侧 15 行 ⇒ 差的 4 行是**主键**（Go 侧 `<表>_pkey` / RAG 侧 `pk_<表>` 或 `<表>_pkey`，且 RAG 侧 `admin_invite` 的主键名与 Go 不同 ⇒ 计入「仅一侧有」）⇒ 逐条一致 3 行。

### 2.5 ✅ task 域 —— 完全等价

类型/长度 0 差、可空性 0 差、DEFAULT 0 差；索引 Go 2 / RAG 2 **逐条等价**（仅主键名不同：`task_progress_pkey` ↔ `pk_task_progress`、`user_like_count_pkey` ↔ `pk_user_like_count`）；约束 Go 2 / RAG 2 **逐条等价**。
⇒ **task 域 DDL 是三个已探针域里最干净的一个。**

---

## 三、「已修」清单（AGENTS.md §〇 item 2 那条清单**已过期**）

⚠️ **AGENTS.md §〇 item 2 原列的「仍未修」清单里，5 条全部已不成立**：

| 原清单项 | 实际状态（2026-10-25 实测） | 修法位置 |
|---|---|---|
| message 域时间列 `TIMESTAMP` | ✅ **已修** —— `V5__message_time_columns_timestamptz.sql`（8 列 `ALTER … TYPE TIMESTAMPTZ USING … AT TIME ZONE 'Asia/Shanghai'` + 5 列 `DROP NOT NULL`）⇒ **运行时实测类型 0 差、可空性 0 差** | `service/message/message-rpc/src/main/resources/db/migration/V5__message_time_columns_timestamptz.sql` |
| users 域其余 3 张表时间列 | ✅ **已修** —— `02/03/04_*.sql` 均带 `ALTER COLUMN … TYPE TIMESTAMPTZ` ⇒ **运行时实测 users 域 72 列类型 0 差** | `service/users/users-common/src/main/resources/db/0{2,3,4}_*.sql` |
| admin-rpc 7 处 | ✅ **已修** —— 「12 处中的 7 处」这个老口径已归零；`admin_admin`/`admin_invite` 的时间列与 `extra_info`（jsonb→text）全部对齐 | `03_create_admin.sql` / `04_create_admin_invite.sql` |
| like-rpc 丢失 CHECK / 触发器 / 索引 | ✅ **已修**（2026-10-23 第三轮）—— `V6__align_like_domain_fact_outbox_with_go.sql` 补 6 项（2 个 CHECK + 2 个部分索引 + 1 个触发器 + 版本范围 CHECK），**不改 V4** ⇒ `DRIFT-LIKE-017` 已结案 | `service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql` |
| message-rpc `V2` 迁移无 `IF NOT EXISTS` | ✅ **前提不成立** —— V2 的 `ADD CONSTRAINT uk_conversation_pair` 在 **GORM 建过表的库上本来就能跑**（GORM 建的是**索引**，不是同名**约束**）⇒ 原文担心的失败场景不存在。`V4` 已把该约束降回索引 | [`docs/DRIFT/message/rpc.md`](../message/rpc.md) §`UNRESOLVED-MSG-005` |

**⇒ AGENTS.md §〇 item 2 的「仍未修」应改为**：**`users_user.status` 可空性 1 处 + DEFAULT 残留 14 处 + users 域缺 Go 的 3 条索引**。

### 表名映射（§三 提到的「两侧表名本来就不同」）

Go → RAG（来自 AGENTS.md §3.4.1，非本文件自定）：

| Go | RAG |
|---|---|
| `users` | `users_user` |
| `admins` | `admin_admin` |
| `admin_invites` | `admin_invite` |
| `user_avatar_history` | `users_user_avatar_history` |

⚠️ admin 侧是**两端都改名**（不是一端改）⇒ 写错映射会让脚本静默「列集合不一致」而不报错。

---

## 四、仍未覆盖（**没有结论 ≠ 没问题**）

| 域 | 状态 | 说明 |
|---|---|---|
| `like` | 🟡 **只到源码级** —— **无运行时 DDL 探针** | `check-ddl-static.py` 只有 `users`/`task`/`message` 三个域预设 ⇒ like 域的 `like_domain_fact_outbox` / `like_fact_stream` 等表的 DDL **只有源码级证据**（`V6` 迁移文件 + `LikeDomainFactSchemaParityTest` 8 条真 PG 守门）⇒ **没有一次「双侧活体逐列对拍」**。<br>⚠️ 与 §3.3 同源：**「迁移脚本写了」≠「库里的 schema 与 Go 一致」**。补法：给 `check-ddl-static.py` 加 `like` 域预设 + 一个 `run-like-ddl-parity.sh`（照 `run-message-ddl-parity.sh` 抄）。 |
| 其余 5 域 | 🔴 **未对照** | `hot` / `article` / `comment` / `follow` / `favorite` 均**无 DDL 探针** ⇒ 这 5 域的表结构**从未做过双侧逐列对照**（`favorite` 域台账整体 0 字节，见 [`docs/DRIFT/_INDEX.md`](../_INDEX.md)）。 |
| CHECK / 触发器 / 外键 / 分区 | 🔴 **脚本不查** | `check-ddl-static.py` 明确只查「列类型 / 可空性 / DEFAULT / 索引 / 约束」，**不查触发器与 CHECK**（file header 第 31-32 行）⇒ like 域 V6 补的那 6 项**没有**被任何自动对拍覆盖。 |

---

## 五、复现命令

```bash
# users 域（容器 difftest-postgres 已在跑时）
python3 tools/difftest/check-ddl-static.py --domain users --indexes

# task 域（容器 taskprobe-postgres 已在跑时）
python3 tools/difftest/check-ddl-static.py --domain task --indexes

# message 域（一键拉起干净的 PG + 真源 AutoMigrate + V1~V5 + 对拍）
bash tools/difftest/run-message-ddl-parity.sh
#   ⇒ 预期 EXIT=1（残留 = 6 处 DEFAULT + 1 处已签字索引 V-022）
```

⚠️ **`EXIT=1` 在 users/message 域是预期值**（差异已分类登记，不是「门禁坏了」）。
⚠️ **users 域跑前须先起 difftest 栈**：`bash tools/difftest/run.sh --no-build --keep --serve-only`。
