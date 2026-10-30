# L4 · `align/global-flyway` —— CD-1 残留（三进程同库共存真 PG 验证）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **B1** 的残留做完：**「三进程并发同库共存」这个运行时形态从未验证**。

### 已完成的部分（不要重做）

第十五轮已查明并修掉：
- 7 个 Flyway rpc 模块**全部连同一个库**（默认 DSN 都是 `first_db`）⇒ **同库共存是常态，不是边缘场景**。
- `comment` / `follow` / `like` 三域缺 `spring.flyway.baseline-version: 0`
  ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表建不出来 ⇒ 服务起不来** ⇒ **已修 + 各配真 PG 守门（变异 6 条全红）**。
- `article-rpc` **逐模块判定后排除**（最低迁移版本 V8 > baseline 默认值 1）⇒ **差点误修一个不存在的缺陷**。

### 本 lane 的任务

**验证「三个进程同时起、同一个库、同一 schema」这个形态** ⇒ 现在只验过「单模块」。

## 二、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| CD-1 逐条取证 | `docs/DRIFT/global/cross-domain.md` | **§二·十二**（含 ① 取证「同库共存是常态」/ ② 缺陷形态 / ③ 逐模块判定 / ④ 修法+守门 / ⑤ favorite 同款缺陷 / **⑥ 残留**） |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **B1** 行 |
| 现有账本隔离检查 | `tools/difftest/check-flyway-ledger.py` | 全文（⚠️ 它只查**表名两两不同**，**不查运行时共存**） |
| 已有真 PG 守门样板 | `service/like/like-rpc/src/test/.../LikeFlywaySharedSchemaGateTest.java`<br>`service/comment/comment-rpc/src/test/.../CommentFlywaySharedSchemaGateTest.java` | 逐字读（**真 PG 16 + 真 Flyway 引擎 + 配置取自模块自己的 yaml**） |
| Go 真源 | `Sea-RideTheWind_golang/service/<域>/**/model/*.go` 的 `AutoMigrate` | 逐字读 |

## 三、要回答的问题（不是「跑一遍绿」）

1. **三个 rpc 进程并发启动**（同一库、同一 schema `public`、三本独立账本）
   ⇒ 首张表是否都建出来了？有没有互相 baseline 掉？
2. **启动顺序无关**（谁先起都行）⇒ 六种顺序是否等价？⇒ 至少覆盖「A 先」「C 先」两端。
3. **账本表互不覆盖**：三本账本各自的 `flyway_schema_history_*` 表**都在**、**内容对**。
4. `message` / `like` / `article` 三域（原台账点名的那三个）与 `comment` / `follow` 三域
   ⇒ **哪些域的 V1 依赖 `baseline-version: 0`**？⇒ 写成矩阵。

## 四、写权范围（白名单）

**只写这些**：
- `tools/difftest/check-flyway-concurrent.sh`（**新建**，名字固定 ⇒ 别与 L3 撞）
- `tools/difftest/` 下**新**建的、按本 lane 需要的小脚本 ⇒ ⚠️ **文件名必须以 `flyway` 开头**
  ⇒ 与 L3 的 `ddl*` 前缀脚本**天然不撞名**
- `docs/DRIFT/global/lanes/L4-flyway.md`（本文件）

⚠️ 若验证结论要求改某个域的 `application.yml`（比如还要补一行 baseline 配置）
⇒ **先在台账写明理由**，并**只改一个域**（其余域留给整合者或后续轮次），避免与其他 lane 抢同一批 yaml。

**⛔ 禁区**：
- `service/favorite/**` 与 `docs/*/favorite/`（**`favorite-rpc` 同款缺陷永久阻塞 ⇒ 只记账 + 告知 owner，不修**）
- `service/common/**`
- `tools/difftest/check-ddl-static.py`（L3 的）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **可重跑的脚本** `tools/difftest/check-flyway-concurrent.sh` ⇒ 非 0 退出表示有差异。
2. **结论矩阵**：哪些域可以安全并发同库、哪些不行、不行的缺什么。
3. 若发现**新的真漂移** ⇒ 登记（不修）。
4. 台账：本文件末尾追加「实测步骤 + 结果 + 结论 + 未覆盖的部分」。

## 六、验收标准

- [ ] 脚本**连跑两次结果一致**（幂等），且**会清理自己的库/容器**（不留下脏状态给别的 lane）
- [ ] 至少覆盖「三域并发 + 不同启动顺序」两种形态
- [ ] **真 PG 16**，不是 H2、不是 mock（§3.3）
- [ ] 明确写出**哪些域没验**（favorite 是必然的 ⇒ 显式说明）
- [ ] 若结论依赖某个域的 yaml 改动 ⇒ 在台账里写清「谁改了什么、为什么、影响面」

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **「单模块确证」≠「多本账能同时跑」** | 7 个模块各有独立账本表名（`flyway_schema_history_<模块>`）⇒ 撞号病根已消除，但**运行时共存从未验证** ⇒ 这正是本 lane 的存在理由 |
| **baseline 语义** | `baseline-on-migrate=true` + 非空 schema + 无账本 ⇒ Flyway 在 **version 1** 处打 BASELINE ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来 ⇒ 服务起不来 |
| **`article-rpc` 是反例** | 最低迁移版本 V8 > baseline 默认值 1 ⇒ **不受影响** ⇒ 判定必须**逐模块**做，不能套同款结论 |
| **Docker 抢资源** | 全仓并行跑会互抢容器 ⇒ 本 lane **必须用自己独立的容器名/网络**，别抢 `difftest-postgres` |
| **上轮遗留** | `docs/Pitfalls/_cross/flyway-ledger-collision.md` 记录了「文档声称由 `FlywayLedgerIsolationTest` 负责，**该测试类全仓不存在**」⇒ 写脚本时**别引用不存在的类** |

---

# 八、实测（2026-10-25 · L4 lane 执行）

> 本节即本 lane 的**台账片段**：任务书的一~七节是别人写给我的，本节是我交出去的东西。

## 8.1 复现命令

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/global-flyway"
bash tools/difftest/check-flyway-concurrent.sh          # 退出码 0 = 无差异 / 1 = 有差异 / 2 = 环境不满足
bash tools/difftest/check-flyway-concurrent.sh --keep   # 保留容器与临时目录，便于现场复核
```

**新增文件（全部在 L4 白名单内）**：

| 文件 | 作用 |
|---|---|
| `tools/difftest/check-flyway-concurrent.sh` | 编排：起独立 PG 16 容器 → 建 5 个场景的库 → 每模块一个 JVM 并发跑 → 调判定器 → 退出码有语义 |
| `tools/difftest/flyway-concurrent-boot.java` | 驱动：`migrate`（起**真的 Spring Boot 上下文**跑迁移，带并发栅栏）/ `inspect`（dump 表·索引·账本行）/ `createdb` |
| `tools/difftest/flyway-concurrent-verify.py` | 判定器 A~J + 静态撞名前置 + 结论矩阵 |

## 8.2 方法（为什么这不是「跑一遍绿」）

| 维度 | 做法 |
|---|---|
| 库 | **真 PG 16**（`postgres:16-alpine`）· **独立容器名** `rag-l4-flyway-pg-$$` + **独立网络** + 随机端口 ⇒ 不抢别的 lane 的 `difftest-postgres` |
| 进程 | 每个模块一个**独立 JVM** ⇒ 起**真的 Spring Boot 上下文**（只 `import` `DataSourceAutoConfiguration` + `FlywayAutoConfiguration` ⇒ 不拉 Kafka/Nacos/Redis/web）⇒ 走 **Spring Boot 自己的 `FlywayAutoConfiguration`** ⇒ 测的是「模块 yaml → Spring Boot → Flyway 引擎」这条**生产接线**，**不是**手写 `FluentConfiguration` 映射（手写映射会把「yaml 键名写错」变成假绿） |
| 配置 | **不硬编码**：读各模块自己的 `application.yml`；DSN 由 yaml 里的 `${POSTGRES_HOST:…}/${POSTGRES_PORT:…}/${POSTGRES_DB:…}` 拼出（只把宿主/端口/库名指到临时容器）⇒ `message` 的 `?sslmode=disable&options=-c TimeZone=Asia/Shanghai`、`favorite` 的 `?TimeZone=Asia/Shanghai` **原样保留**（实测 `effectiveJdbcUrl` 可见） |
| 并发 | 同一组的所有进程**等到同一个墙钟瞬间**才起上下文 ⇒ 实测**起跑时刻偏差 0~4ms**（脚本把「偏差 > 3000ms」直接判成失败 ⇒ 场景退化成串行会被抓住） |
| 判定 | 逐条判 A~J：① 进程成功 ② **生效账本表名** == yaml 配的 ③ **生效 baseline 版本** == yaml 配的 ④ 生效 `baseline-on-migrate` == true ⑤ 该模块 `CREATE TABLE` 的表**全部建出** ⑥ 账本表存在 ⑦ 账本 `(version, description)` 集合**精确等于**本模块自己的迁移集合 ⑧ BASELINE 行落在生效 baseline 版本上 ⑨ 账本里**没有别的模块**的迁移描述 ⑩ **没有任何迁移被跳过**（Flyway 的 `BASELINE_IGNORED` 会被抓出来） |
| 静态前置 | 7 个 rpc 模块的 **schema 级对象名**（表 28 / 索引 56 / 约束 16 / 序列 0 / 类型 0）**零撞名** ⇒ 并发同库不会因为同名对象互相挤掉 |

## 8.3 场景与结果（2026-10-25 实测）

| 场景 | 形态 | 模块 | 结果 |
|---|---|---|---|
| **S1** | 三进程**同时**起（空库） | `message` ‖ `like` ‖ `article` | ✅ 3 个模块的表全部建出 · 3 本账本互不覆盖 · 起跑偏差 **4ms** |
| **S2** | **article 先**起 | `article` →（`message` ‖ `like`） | ✅ article 空库首发（**无 BASELINE 行**）· message/like 各在 **version 0** 打 BASELINE（V1 照常执行）· 偏差 **2ms** |
| **S3** | **message 先**起 | `message` →（`like` ‖ `article`） | ✅ message 空库首发（无 BASELINE 行）· like@0 · article@1（V8~V10 照常执行）· 偏差 **2ms** |
| **S4** | 三个 **V1 起始**域同时起 | `comment` ‖ `follow` ‖ `task` | ✅ 三个模块的表全部建出 · 三本账本各自完整 · 偏差 **4ms** |
| **S5** | `like` 先起（把 schema 弄非空） | `like` →（`favorite` ‖ `message`） | 🟡 **favorite 复现已知阻塞缺陷**（V1 被 baseline 跳过）· ✅ 同场景的 `message` **完全不受影响**（V1~V5 照常）· 偏差 **0ms** |

> ⚠️ **一次实测里「有没有 BASELINE 行」会变** —— 同一场景连跑两次，S1 的 `like` 一次打了 `BASELINE@0`、一次没打
> （它检查「schema 是否为空」的时刻，取决于别的模块建表建得快慢）⇒
> ⇒ **这恰恰是 `baseline-version: 0` 的价值**：打了 BASELINE 也是落在 **0** ⇒ V1 照常执行 ⇒ **行为与竞态无关**；
> 而默认值 1 落在 **1** ⇒ V1 被跳过 ⇒ **行为随竞态漂移**。
>
> 📌 **整轮重跑两次的实测对照**（2026-10-25，两次都 `EXIT=0`，结论矩阵逐行相同）：
>
> | | 第 1 轮 | 第 2 轮 |
> |---|---|---|
> | S1 `like` 的 BASELINE 行 | 无 | 无 |
> | S4 `comment` 的 BASELINE 行 | 无 | **`0`** |
> | S4 `comment` 的 SQL 行数 | **8**（V1~V8 一条不缺） | **8**（V1~V8 一条不缺） |
>
> ⇒ **「有没有 BASELINE 行」随竞态变，但「V1~VN 一条不缺」不变** ⇒ 结论对竞态**免疫**。

## 8.4 结论矩阵（可并发同库？）

| 模块 | 可并发同库 | 账本表 | baseline | 依据 |
|---|---|---|---|---|
| `article-rpc` | ✅ 可以 | `flyway_schema_history`（**默认表名**） | 1（默认） | S1 / S2 / S3 |
| `comment-rpc` | ✅ 可以 | `flyway_schema_history_comment` | 0 | S4 |
| `follow-rpc` | ✅ 可以 | `flyway_schema_history_follow` | 0 | S4 |
| `like-rpc` | ✅ 可以 | `flyway_schema_history_like` | 0 | S1 / S2 / S3 / S5 |
| `message-rpc` | ✅ 可以 | `flyway_schema_history_message` | 0 | S1 / S2 / S3 / S5 |
| `task-rpc` | ✅ 可以 | `flyway_schema_history_task` | 0 | S4 |
| **`favorite-rpc`** | 🔴 **不行** | `flyway_schema_history_favorite` | **1（默认 ⇒ V1 被跳过）** | S5（**缺 `spring.flyway.baseline-version: 0`** ⇒ **global 无写权 ⇒ 只记账不修**） |

## 8.5 回答任务书的三个问题

1. **三个 rpc 进程并发启动（同一库、同一 schema `public`、三本独立账本）⇒ 首张表是否都建出来了？有没有互相 baseline 掉？**
   ⇒ **都建出来了，没有互相 baseline 掉**。5 个场景 × 7 个模块，每个模块的 `CREATE TABLE` 全部落地；
   每本账本的 `(version, description)` 集合**精确等于**该模块自己的迁移集合，**零串味**；
   没有任何模块的迁移出现在别的模块账本里。
2. **启动顺序无关？哪些域可以安全并发同库、哪些不行？不行的缺什么？**
   ⇒ 「article 先」「message 先」「三个同时」「like 先」四种形态**结论一致** ⇒ **顺序无关**。
   ⇒ **可以**：`article` / `comment` / `follow` / `like` / `message` / `task`（6 个）。
   ⇒ **不行**：`favorite` —— 缺 `spring.flyway.baseline-version: 0`（**一行 yaml**，与 comment/follow/like 完全同款）。
3. **独立账本表名是否真的消除了撞号（运行时层面）？**
   ⇒ **是**。运行时三本账**同时存在、互不覆盖**，且**互不阻塞**：
   Flyway 的 PG advisory lock 的 key 是**按「账本表名」算的**
   （`flyway-database-postgresql` 10.22.0 `PostgreSQLConnection.lock(Table)` → `table.toString().hashCode()`
   ⇒ 独立账本表名**顺带把锁也隔离了**）⇒ 实测三进程起跑偏差 0~4ms、各自迁移 3~7s **同时在跑**，没有被串行化。

## 8.6 新发现（**登记不修**）

| # | 发现 | 证据 | 处置 |
|---|---|---|---|
| **L4-01** | 🔴 **`favorite-rpc` 的故障形态被修正**：上一轮台账写的是「后续迁移失败 ⇒ **服务起不来**」。**实测不是** —— `favorite` 的 V2~V6 **全部成功**（它的迁移里**没有**指向 `favorite_folder` 的外键）⇒ **进程正常退出**，但 `favorite_folder` 表**不存在** ⇒ 故障形态是「**服务起得来、首次查库才炸**」（`relation "favorite_folder" does not exist`） | S5：Flyway 自己的迁移信息里 **V1 的 state = `BASELINE_IGNORED`**；账本 `flyway_schema_history_favorite` 的 SQL 行只有 `['2','3','4','5','6']` + BASELINE 行落在 `version 1`；`public` schema 里**没有** `favorite_folder` | **只记账**（favorite 零写权）⇒ 告知 favorite 域 owner 时**按这个形态说**，别再按「起不来」说 |
| **L4-02** | 🟡 `article-rpc` 用**默认账本表名** `flyway_schema_history` ⇒ 5 个场景里它是**唯一**使用者（没撞）⇒ 但「**将来新增模块忘配 `table` 就直接撞上它**」这条残留风险**仍在** | S1/S2/S3 三个场景里 `flyway_schema_history` 只被 article 使用 | **不动**（改表名属跨域迁移，代价大于收益；AGENTS.md §6.3 问题 4 已记为残留风险） |
| **L4-03** | 🟡 **Flyway 的 advisory lock 按「账本表名」算 key** ⇒ 独立账本表名**顺带把迁移锁也隔离了**（不是只有账本表隔离） | `flyway-database-postgresql-10.22.0` 源码 `PostgreSQLConnection.java:105` | **纯记录**（正面结论 ⇒ §6.3 问题 4 的定案比原以为的还彻底） |
| **L4-04** | 🟢 7 个 rpc 模块的 **schema 级对象名零撞名**（表 28 / 索引 56 / 约束 16 / 序列 0 / 类型 0）⇒ 并发同库不会因为同名对象互相挤掉 | 脚本内置静态前置（每次运行都查） | **纯记录** |

## 8.7 未覆盖的部分（**明确说明**）

1. **不是完整的 rpc 进程** —— 起的是「只 import DataSource + Flyway 两个 auto-configuration」的**最小 Spring 上下文**
   ⇒ **没起** Kafka / Nacos / Redis / gRPC server / web ⇒ 「**三个真 rpc 服务同时起**」这个形态**没验**
   （要验得把整套中间件拉起来）。本 lane 验的是 **Flyway 层**的并发共存。
2. **没测「两个模块同时迁同一张表」** —— 本仓 7 个模块表名零重叠 ⇒ 不存在这种冲突；
   脚本只做了**静态**撞名检查，没做人为造冲突的运行时验证。
3. **没测迁移中途失败 / 回滚** —— 两个进程同时改同一个 schema 时若其中一个 V_N 失败，
   PG 里 DDL 的事务语义在本 lane **没验**。
4. **没测「同一模块起两个副本」**（同账本同表）—— 生产里同一模块多副本是常态，但它们跑的是**同一本账 + 同一套迁移**
   ⇒ 不属于「跨模块撞号」这条线 ⇒ 本 lane 不覆盖。
5. **favorite 只做了只读探针** —— 没改它的一行代码/配置 ⇒ 缺陷**仍在**（按要求）。
6. **并发强度有限** —— 每个场景各跑 1 次（整轮重跑两次结论一致），**没做**多轮压力 / 随机延迟注入 / 慢机器模拟
   ⇒ 「极端时序下会不会互相踩」**未被穷尽验证**（只验证了起跑偏差 ≤ 4ms 这一种时序）。
7. **没验 Go 侧** —— 本 lane 与 Go 无对照需求（Flyway 是 RAG 引入的机制，Go 侧 GORM `AutoMigrate` 无版本号/无账本/无 baseline）
   ⇒ §3.1 的「双侧探针」在此**不适用**，故本 lane **无 Go 半边**。

## 8.8 交给整合者（**本 lane 无写权 ⇒ 由整合者统一更新**）

| 要改的地方 | 建议内容 |
|---|---|
| `docs/DRIFT/global/cross-domain.md` §二·十二 的 ⑥「残留」 | 「三进程并发的运行时验证」**已完成**（2026-10-25，L4）⇒ 指到本文件的 §8 |
| `docs/DRIFT/global/_README.md` §七之二 **B1** 行 | B1 的运行时那一半**已闭环**（配置层早已闭环）⇒ 指向 `tools/difftest/check-flyway-concurrent.sh` |
| `tools/difftest/README.md` | 加一行新脚本说明（本 lane **不写**该共享文件） |
| `docs/Pitfalls/_cross/flyway-ledger-collision.md` | ①「三模块同库共存仍记 `unresolved`」⇒ **已闭环**（见本文件 §8）②「文档声称由 `FlywayLedgerIsolationTest` 负责，**该测试类全仓不存在**」⇒ 本 lane **仍未创建**该类；实测由 `check-flyway-concurrent.sh` + 各域 `*FlywaySharedSchemaGateTest` 承担 ⇒ **要么真写这个类，要么改掉那句声明**（本 lane 无权改该文件） |
| `docs/DRIFT/global/cross-domain.md` ⑤ 的 `favorite-rpc` 段 | 故障形态订正：**不是「服务起不来」，是「服务起得来、首次查库才炸」**（L4-01） |

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W3

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| `L4-01` | D | P3 | 否 | Go `service/favorite/rpc/internal/model/db.go:33`（`db.AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})` ⇒ Go 侧建表成功即有 `favorite_folder`，无版本账本故**不可能**被 baseline 跳过）vs RAG `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteFolderMapper.java:76-82`（`@Select` 查 `favorite_folder` ⇒ 台账记的故障形态「服务起得来、首次查库才炸（relation favorite_folder does not exist）」的落点）＋ `service/favorite/favorite-rpc/src/main/resources/application.yml:108`（`baseline-version: 0`，**2026-10-27 G3 域侧自修**）⇒ **差异已消失**（V1 不再被 baseline 跳过 ⇒ `favorite_folder` 建得出来 ⇒ 台账记的那条故障形态不复存在）⇒ 本条原为「只记账」的登记项，现已无待办 | 2026-10-30 |

### lane W8

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| L4-02 | D（经分析确认不实现：当前 0 撞号 ＋ 守门在撞上后才报 ＋ 改表名属跨域迁移、代价大于收益） | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ **Go 侧无版本化账本、无 baseline、无 advisory lock** ⇒ 本条无 Go 对照物）＋ `service/article/rpc/internal/model/db.go:70`（手写 `002_community_revision.sql` 是 Go 侧的迁移载体）vs RAG `service/article/article-rpc/src/main/resources/application.yml:53`（`flyway:` 段起点）＋ `:54`（`enabled: true`）＋ `:55`（`baseline-on-migrate: true`）＋ `:76`（`baseline-version: 0`）＋ `:77`（`locations: classpath:db/migration`）＋ `:78`（`schemas: public`）⇒ 该文件**无 `table:` 键**（实测 `grep table:` 零命中）⇒ 用**默认账本表名** `flyway_schema_history`；对照 `service/message/message-rpc/src/main/resources/application.yml:105`（`table: flyway_schema_history_message`）／`service/like/like-rpc/src/main/resources/application.yml:81`（`..._like`）／`service/comment/comment-rpc/src/main/resources/application.yml:112`（`..._comment`）／`service/favorite/favorite-rpc/src/main/resources/application.yml:111`（`..._favorite`）／`service/follow/follow-rpc/src/main/resources/application.yml:79`（`..._follow`）／`service/task/task-rpc/src/main/resources/application.yml:79`（`..._task`）⇒ **6／7 有独立账本表名，article 独占默认名** ⇒ **差异仍成立**（残留风险**仍在**：将来新增模块忘配 `table` 就会撞上它）⚠️ 但**不是静默风险**：`tools/difftest/check-flyway-ledger.py:112`（`table_explicit`）＋ `:135`（`← 未显式声明` 会打印）＋ `:149-154`（**同库同 schema 两模块共用一张账本表**才判违规 ⇒ 一旦真撞上即 `EXIT=1`）⇒ 撞号在**构建期**被拦、不是运行期才炸 ⇒ 经分析确认**阶段一不实现** ⇒ 唯一残留落点 = 新增 rpc 模块时**必须**同时写 `spring.flyway.table` | 2026-10-30 |
| L4-03 | D（正面记录 ⇒ 无待实现内容） | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(...)` ⇒ **Go 侧无版本化账本 ⇒ 无 advisory lock** ⇒ 本条**无 Go 对照物**；`docs/DRIFT/global/lanes/L4-flyway.md:191` 已声明「本 lane 与 Go 无对照需求（Flyway 是 RAG 引入的机制，Go 侧 GORM `AutoMigrate` 无版本号／无账本／无 baseline）」）vs RAG `flyway-database-postgresql-10.22.0` 源码 `org/flywaydb/database/postgresql/PostgreSQLConnection.java:105`（`new PostgreSQLAdvisoryLockTemplate(database.getConfiguration(), jdbcTemplate, table.toString().hashCode())` ⇒ **lock key = 账本表名的 hashCode**）＋ `pom.xml:81`（`<flyway.version>10.22.0</flyway.version>` ⇒ 与上面那份源码同版本）＋ 7 个 rpc 模块的账本表名（`service/message/message-rpc/src/main/resources/application.yml:105`／`service/like/like-rpc/src/main/resources/application.yml:81`／`service/comment/comment-rpc/src/main/resources/application.yml:112`／`service/favorite/favorite-rpc/src/main/resources/application.yml:111`／`service/follow/follow-rpc/src/main/resources/application.yml:79`／`service/task/task-rpc/src/main/resources/application.yml:79`／article 走默认名）⇒ **差异已消失**：那条「独立账本表名**只**隔离账本表、迁移锁仍共用」的担心**从未存在** —— 锁 key 由账本表名派生 ⇒ 表名不同 ⇒ 锁不同；且实测 7 个表名（含默认名）的 **Java 32 位 `String.hashCode()` 两两不同**（1610714361／935340737／225054493／930814233／443676194／1362809271／225285387 ⇒ 零碰撞）⇒ 7 个 rpc 模块的迁移锁确实互不阻塞 ⇒ 与 `docs/DRIFT/global/lanes/L4-flyway.md:164-166` 的实测（三进程起跑偏差 0~4ms、各自迁移 3~7s **同时在跑**、没有被串行化）一致 ⇒ 纯记录，无待实现内容 | 2026-10-30 |
