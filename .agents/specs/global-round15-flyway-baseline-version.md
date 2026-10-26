# global 第十五轮 —— X-1 取证与修复：3 个 rpc 模块缺 `spring.flyway.baseline-version: 0`（同库共存下的 V1 被整条跳过）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`1adc627`
> **性质**：**真缺陷修复（3 个域各一行 yaml）+ 真 PG 守门（3 个新测试类）+ 台账**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（同款缺陷 ⇒ **记账 + 告知该域 owner**）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二·十二（X-1「共享 Flyway 账本」）：
原状态是「配置层 + 运行时已闭环；**三模块同库共存**仍 `unresolved`」。

⇒ 本轮把 X-1 **拆成两半**查：

- **配置层**：Flyway 的 baseline 语义是否每个模块都配对了？
- **运行时**：非空 schema 下 V1 到底会不会被跳过？

---

## 二、🔴 取证结论

### ① 7 个 Flyway rpc 模块**全部连同一个库**（`first_db`）

⇒ 「先启动的模块已经把 public schema 弄非空」是**必然**，不是「万一」
⇒ ⇒ **每个 V1 起始的模块都必须能扛住非空 schema**。

### ② 缺陷形态

```
spring.flyway.baseline-on-migrate: true      ← 7/7 都配了
spring.flyway.baseline-version                ← 🔴 部分模块【没配】⇒ 取默认值 1
```

⇒ 非空 schema + 没有自己的账本 ⇒ Flyway 在 **version 1** 处打 BASELINE
⇒ **只执行版本 > 1 的迁移** ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来
⇒ ⇒ 后续迁移报 `relation "…" does not exist` ⇒ **服务起不来**。

⚠️ **同一个缺陷在 `message-rpc` 上已于 2026-10-24 被抓到并修掉**
（`MessageFlywaySharedSchemaWiringTest` + `spring.flyway.baseline-version: 0`）
⇒ ⇒ **同一个坑，在另外 3 个域上原封不动地留着** ⇒ 「已知模式没被推广」的典型。

### ③ 逐模块判定（**不是所有模块都要改**）

| rpc 模块 | 最低迁移版本 | 受影响？ | 处置 |
|---|---|---|---|
| `comment-rpc` | **V1**（`subject`） | 🔴 是 | ✅ **修** |
| `follow-rpc` | **V1**（`follow_relation`） | 🔴 是 | ✅ **修** |
| `like-rpc` | **V1**（`like_record`） | 🔴 是 | ✅ **修** |
| `favorite-rpc` | **V1**（`favorite_folder`） | 🔴 是 | ⛔ **零触碰** ⇒ 记账 + 告知 owner |
| `message-rpc` | V1 | ✅ 已配 | 不动 |
| `task-rpc` | V1 | ✅ 已配 | 不动 |
| `article-rpc` | **V8** | 🟢 **否** —— 最低版本 8 > baseline 默认值 1 ⇒ V8/V9/V10 照常执行 | **不动** |

⇒ ⚠️ **`article-rpc` 是逐模块判定后才排除的** —— 一开始按「5 个模块缺」准备统一补
⇒ ⇒ 复核迁移版本号才发现它的最低版本是 **V8** ⇒ **差点误修一个不存在的缺陷**。

---

## 三、改动

### ① 3 个域各一行 yaml

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0     # ← 本轮新增
    ...
```

⇒ ⚠️ **不能改成 `baseline-on-migrate: false`** —— 那会让 Flyway 在非空 schema 上**直接抛异常退出**
（"Found non-empty schema(s) … but no schema history table"）⇒ 更糟（起不来且无修复路径）。
⇒ `baseline-version: 0` ⇒ baseline 标记打在 version 0 ⇒ **V1 之后全部迁移照常执行**。

### ② 3 个新守门测试（真 PG 16 + 真 Flyway 引擎）

| 类 | 域 |
|---|---|
| `CommentFlywaySharedSchemaGateTest` | comment |
| `FollowFlywaySharedSchemaGateTest` | follow |
| `LikeFlywaySharedSchemaGateTest` | like |

设计要点：

1. **真 PG 16**（Testcontainers）+ **与生产同版本的 Flyway 引擎** + 裸 JDBC
   ⇒ 沿用本仓既有先例（`CommentSchemaPgGateTest` 明确写过「不起 Spring 上下文，
   避免把 DDL 守门变成全栈启动测试」）。
2. 🔴 **配置取自本模块自己的 `application.yml`**（用 `YamlPropertySourceLoader` 读 classpath 上的
   `application.yml`）⇒ **不硬编码** locations / schemas / table / baseline-on-migrate / baseline-version
   ⇒ ⇒ 测的就是生产真正用的那几个值 ⇒ 配置一改，测试立刻跟着变。
3. 🔴 **先把 public schema 弄成非空**（预建一张无关表）⇒ 让 baseline 路径**真的被走到**
   ⇒ 这是「空库测试永远测不出来」的那一层。
4. 断言三件事：① 首张表**真的存在** ② 账本的 BASELINE 行**版本必须是 0** ③ V1..VN **一条不缺**。
5. ⚠️ 先断言「读到的 yaml 的账本表名 == 本模块的账本表名」⇒ 防止 classpath 上读串别的模块的 yaml。

---

## 四、实测依据

| 项 | 结果 |
|---|---|
| `CommentFlywaySharedSchemaGateTest` | ✅ **2/2** |
| `FollowFlywaySharedSchemaGateTest` | ✅ **2/2** |
| `LikeFlywaySharedSchemaGateTest` | ✅ **2/2** |
| **变异测试**（删掉 3 个模块的 `baseline-version: 0`） | ✅ **6 条全部转红**（3 域 × 2 条）⇒ 守门非空转 |
| 3 个 rpc 模块全量测试 | 见提交信息 |

⚠️ **本轮踩到的小坑**：第一次跑变异时 Maven 的 **fail-fast** 让 reactor 在第一个失败模块就停了
⇒ 只看到 like 一个域 ⇒ **必须加 `-fae`** 才能看到三个域的全貌。

---

## 五、Go 出处 / 依据

- **Flyway 语义**：`baselineOnMigrate=true` + 非空 schema + 无账本 ⇒ 打 BASELINE 记录于
  `baselineVersion`（默认 **1**）⇒ 只跑版本 > baseline 的迁移
  ⇒ 实测依据见本仓 `service/message/message-rpc/src/main/resources/application.yml:64-104`
  （2026-10-24 真 PG 16 + 真 jar 的实测记录）与
  `MessageFlywaySharedSchemaWiringTest`（真 PG 守门 + 断言口径的实测纠正）。
- **Go 侧对照**：Go 用 gorm `AutoMigrate`（`service/*/model/db.go`）⇒ **没有 baseline 概念**
  ⇒ 任何时候都会把表建出来 ⇒ RAG 侧要与之对齐 ⇒ 必须 `baseline-version: 0`。
- **同库事实**：7 个 rpc 模块的 `spring.datasource.url` 默认值全部是
  `jdbc:postgresql://${POSTGRES_HOST:postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:first_db}`。

---

## 六、🔴 `favorite-rpc` 同款缺陷：零触碰 ⇒ 记账 + 告知该域 owner

| 项 | 内容 |
|---|---|
| 缺陷 | `service/favorite/favorite-rpc/src/main/resources/application.yml` 的 `spring.flyway` 块**缺 `baseline-version`** ⇒ 与 comment/follow/like **完全同款**（V1 = `favorite_folder`） |
| 为什么 global 不能改 | favorite 域**零写权**（AGENTS.md §5.3c）⇒ 代码 / 配置 / 测试 / 台账一律不碰 |
| 为什么共享层也代劳不了 | 这次**不是共享层的问题** —— 缺的是**该域自己 yaml 里的一行** ⇒ 共享层无法代劳 |
| 后果 | favorite-rpc 在**非首个启动者**时 V1 被跳过 ⇒ `favorite_folder` 建不出来 ⇒ 后续迁移失败 ⇒ **服务起不来**（**生产可用性**问题，不是形状漂移） |
| **交给 favorite 域 owner 的修法** | 在 `spring.flyway:` 块下加 `baseline-version: 0`（与 comment/follow/like 同款）；守门可照抄 `CommentFlywaySharedSchemaGateTest` |

---

## 七、不做（明确排除）

- ❌ **不动 `favorite` 的任何文件** ⇒ 它的 yaml **一个字节没改**。
- ❌ **不动 `article-rpc`**（它的最低迁移版本是 V8 ⇒ 不受 baseline 默认值影响 ⇒ 不是缺陷）。
- ❌ **不动 `message-rpc` / `task-rpc`**（已配 `baseline-version: 0`）。
- ❌ **不写 `docs/DRIFT/favorite/rpc.md`**（不在 global 的台账写权内）⇒ 同款缺陷记在 global 台账 + 本 spec。
- ❌ **不做「三进程并发」的运行时验证**（本轮做的是逐模块的非空 schema 验证 —— 等价且更聚焦）⇒ 留一轮。
- ❌ **不改 `article-rpc` 的默认账本表名**（属跨域迁移，代价大于收益 ⇒ 见 AGENTS.md §6.3 问题 4 的残留风险）。

---

## 八、验证

- `mvn -o test -pl service/comment/comment-rpc -Dtest=CommentFlywaySharedSchemaGateTest` ⇒ ✅ 2/2
- `mvn -o test -pl service/follow/follow-rpc -Dtest=FollowFlywaySharedSchemaGateTest` ⇒ ✅ 2/2
- `mvn -o test -pl service/like/like-rpc -Dtest=LikeFlywaySharedSchemaGateTest` ⇒ ✅ 2/2
- 变异（删 `baseline-version: 0`）+ `-fae` ⇒ ✅ **6 条全红**
