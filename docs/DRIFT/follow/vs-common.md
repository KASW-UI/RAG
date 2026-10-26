<!-- @new 2026-10-27 · `follow-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `follow-common` lane 台账 —— 「follow ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/follow/follow-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G1` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `follow-common`（**原第 4/10 条**） |
| **现分支 / 工作树** | **`align/g1-users-follow`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow`<br>⚠️ 原 `align/follow-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/follow/{follow-api, follow-rpc, follow-common, follow-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/follow/**` ＋ `docs/DRIFT/follow/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| 🔴 `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | 🔴 **未修**（域台账原文即写「派单」）⇒ **本 lane 的头号事项** | [`api.md`](api.md) |
| `DRIFT-FOLLOW-002` | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | `service/common`（pom 作用域） | ✅ **已修** | [`rpc.md`](rpc.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地** | [`api.md`](api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `DRIFT-FOLLOW-008` | 🔴 未修（落点 `service/common`） | 🔴 **维持未修**，**落点已核准** ⇒ 派单 G4（**与 `DRIFT-USERAPI-013` 同族 ⇒ 两条一起对账**） | 域台账原文（L1）+ 共享层台账 `alignment-2026-10-23.md` §十二·二 T-2/T-5 |
| `DRIFT-FOLLOW-002` | ✅ 已修 | ✅ **维持已修**，且**本轮真启动复验**（`follow-rpc` 6.03s 起 · `NoClassDefFoundError` 未复现） | **L3 活体**（真容器） |
| `DRIFT-HOTAPI-030` | ✅ 已落地 | ✅ **维持已落地**（共享层 6 个类在位） | L1 |
| 🆕 `DRIFT-G1-001` / `DRIFT-G1-002` | —（快照里没有） | 🔴 **本轮新立**（真启动抓出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4** | **L3 活体 A/B + Go 侧 L1 源码行号** ⇒ 见 §7 |

⚠️ **本轮复核范围声明（诚实边界）**：`DRIFT-FOLLOW-002` 有**真启动活体复验**；
`DRIFT-FOLLOW-008` / `DRIFT-HOTAPI-030` **状态未变**（按 AGENTS.md §6 记「状态未变」）
⇒ **派单时仍须按反馈单 7 项重新核 Go 侧 `文件:行号`**（台账的「抽取快照」**不算数**）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① `DRIFT-FOLLOW-009`（下游挂死：Go **2s ⇒ 200+1015** / RAG **3s ⇒ 503**）✅ 已修 ② 🆕 `DRIFT-G1-002`（**`follow-api` 缺 `immediate-connect-timeout`** ⇒ 与 Go `zrpc.MustNewClient` 的 panic 语义不一致）🔴 ③ 🟡 **follow-api 的 Redis 命令超时 2s ≠ go-redis `ReadTimeout` 默认 3s**（`DRIFT-USERAPI-015 附带` 登记的待办 · **本波未审**） | 🔴 **1 条域内新** ＋ 🟡 **1 条待审** | L3 双侧活体（台账原有）＋ 本轮真启动活体 | 2026-10-27 |
| **A2** | gRPC 拦截器链 | ① `DRIFT-FOLLOW-002`（运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败）✅ 已修 ② `DRIFT-FOLLOW-003`（`discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败）✅ 已修 | ✅ **一致**（两条都属**启动阻塞型硬失败**，已修） | L1 + 真启动复验 | 2026-10-27 |
| **A3** | HTTP 保护链 | ① 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **envelope** ② `DRIFT-FOLLOW-001`（空列表出参 Go `null` / RAG `[]`）✅ 已修 ③ `DRIFT-FOLLOW-008`（**MaxConns 闸门：Go 每路由一个 vs RAG 全局一个**）🔴 | 🔴 **1 条 G4** ＋ ✅ 2 条 | L1 源码 + L2 | 2026-10-27 |
| **A4** | 错误码 / envelope | ① `DRIFT-FOLLOW-001` ✅ ② `DRIFT-FOLLOW-009` ✅ ③ A3 锚点：Go 侧**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅ | ✅ **一致** | L1 + 活体对拍（台账原有） | 2026-10-27 |
| **A5** | 端口治理 | ⚠️ 台账**无 `RANDOM_PORT` / `RealTomcatPort` 任何记录**；`follow-api` 端口 = `${FOLLOW_API_PORT:8891}`（**可配**）· `follow-rpc` gRPC 端口 = **写死 `8082`**（`application.yml:103`） | 🟡 **台账零覆盖 `RANDOM_PORT` 面** ＋ 🟡 **rpc 端口不可配**（Go 侧 `follow.yaml:2` `ListenOn: 0.0.0.0:8082` ⇒ 两侧同值，但**配置面收窄**） | L1 静态 | 2026-10-27 |
| **A6** | errmsg 契约 | 本域 `follow-common` 的业务码 ↔ gRPC code ↔ description（Go `service/follow/common/errmsg.go`） | 🟡 **本轮未逐条复核**（`docs/DRIFT/follow/common.md` 无状态日期 ⇒ 按 AGENTS.md §6 记 `UNKNOWN`） | — | 2026-10-27（**状态 UNKNOWN**） |
| **A7** | proto 契约 | ① `DRIFT-FOLLOW-005`（recommend 出参**字段顺序** Go=`target_id` 在前 / RAG=`mutual_score` 在前）✅ 已修 ② 两侧 `.proto` 逐行 diff | ✅ **一致** | L1 实测 diff | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | follow 域 **五维全等**（**10 列 / 8 索引 / 2 约束**）⇒ 类型/长度 0、可空性 0、DEFAULT 0、索引 8≡8、约束 2≡2、触发器 0≡0 · 建表走 Flyway 独立账本 `flyway_schema_history_follow` ＋ `baseline-version: 0` | ✅ **一致** | **L3 双侧活体**（真 PG 16）· 2026-10-25 · 权威源 `docs/DRIFT/global/lanes/L3-ddl.md` §8.1/§8.9 ＋ 🆕 **本轮真启动复验**（非空 schema 下 baseline 打在 version 0 ⇒ V1/V2 照常执行） | 2026-10-27 |
| **A9** | trace / otel | ① `DRIFT-FOLLOW-007`（指标文本层多一个 `application` tag / 直方图**零桶**）✅ 已修（残留为**框架限制**）② `DRIFT-FOLLOW-006`（**5 个 `follow_rpc_*` 指标无任何抓取出口**）🔴 **未修**（用户 2026-10-24 定：本轮不落地，转「全仓 rpc 层统一方案」） | 🟡 **1 条 OPEN（已决策暂缓）** ＋ ✅ 1 条 | L1 + 抓取侧观察 | 2026-10-27 |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go 侧 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- 本域**调用** `GoStyleMaxConnsFilter` 的**粒度参数**（若 RAG 侧只是传参口径问题 ⇒ 域侧可自修；**只有共享层本身实现错**才派单）
- `follow-api` 的 A3 接线复核：`mode=envelope`

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

- 🔴 `DRIFT-FOLLOW-008` ⇒ 落点 `service/common` ⇒ **派单 **G4****；⚠️ **与 users 域 `DRIFT-USERAPI-013` 同族** ⇒ 两条**一起**对账，别各修各的

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）

---

## 6. 🆕 **G1 ⇄ G4 配合范围**（常驻 · 2026-10-27 02:4x 记）

> ⚠️ 本节 = **操作口径**（谁写哪 · 怎么派 · 派单长什么样）⇒ **不替代** AGENTS.md §5.3d·§5.3e 与
> [`cross-domain-coupling.md`](../_cross/cross-domain-coupling.md) §三 **C1~C7**。
> ⇒ **通用口径写在 [`docs/DRIFT/users/vs-common.md` §6](../users/vs-common.md)（users 侧那份更全）** ⇒
> **本节只记 follow 域特有的部分**，不重复抄一遍。

### 6.1 follow 侧写权（守门：`tools/difftest/check-wave-lane-ownership.py` ⇒ EXIT=0）

| 面 | G1（`align/g1-users-follow`） | G4（`align/g4-service-common`） |
|---|---|---|
| `service/follow/**`（api/rpc/common/proto） | ✅ **可写**（含 `follow-common` ⇒ **本域真私有**，§5.3f） | 🔴 只读 |
| `service/common/**` · `service/users/users-common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `docs/DRIFT/follow/**` | ✅ 可写 | 🔴 只读 |
| `docs/DRIFT/global/vs-common.md` | 🔴 只读 | ✅ 可写 |
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由整合者统一更新** |

### 6.2 follow 侧派单（G4）—— **1 条，已核准落点**

| 编号 | 摘要 | 落点 | 归属 | 状态（2026-10-27 核） |
|---|---|---|---|---|
| `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | 🔴 **G4** | 🔴 未修 · **与 users 侧 `DRIFT-USERAPI-013` 同族 ⇒ 两条一起对账，别各修各的** |

⚠️ **派单口径（7 项 · 缺项不进排期）**见 [`users/vs-common.md` §6.2](../users/vs-common.md) ⇒
**编号必须带全前缀**（`DRIFT-FOLLOW-008` ≠ `DRIFT-USERAPI-013` ⇒ 同族 ≠ 同一条）⇒
**落点双轨**：实时 `session send` 给 G4 会话 ＋ 正式按写权写台账（**G1 写本文件、G4 写 `docs/DRIFT/global/vs-common.md`** ⇒ 合并前互相看不见对方的文件）。

### 6.3 follow 侧**自修**（不甩 G4）

- `follow-api` 的 A3 接线：`mode=envelope`（Go 侧 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler` ⇒ Go = 200 + 业务信封 ⇒ 同向 ✅）
- `follow-api` 的 `JsonMapperBuilderCustomizer` 注册的是**本域私有**的 `FollowGoStyleCoercion::applyTo` ⇒
  **不共用 `users-common` 的通用版** ⇒ ✅ 域侧自修面，**不牵连 users 通用版**（对比：`favorite-api` 直接复用了 users 通用版 ⇒ 见 users 侧 §6.4.1）
- `follow-common` 是**本域真私有**（§5.3f：只被本域引用）⇒ **G1 可写** ⇒ 共享层缺陷不适用

---

## 7. 🆕 **第 0 步真启动抓到的两条域内真缺陷**（2026-10-27 · G1 lane · follow 侧也在链上）

> ⚠️ **完整证据链见 [`docs/DRIFT/users/vs-common.md` §7](../users/vs-common.md)**（同一轮、同一 A/B 实验、同一批进程）。
> ⇒ **本节只记 follow 侧受影响的部分** ⇒ **两条都落在 G1 写权内 ⇒ 域侧能自修 ⇒ 不派 G4**。

### 7.1 `DRIFT-G1-001` — 🔴 `NACOS_GROUP` 对 `follow-rpc` **完全无效**（配置项形同虚设）

| 侧 | 位置 | 值 | 读配置？ |
|---|---|---|---|
| `follow-rpc`（注册方） | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/bootstrap/NacosRegistration.java:113` | `.registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance)` | ❌ **写死** |
| `follow-rpc`（注销方） | 同文件 `:133` | `.deregisterInstance(SERVICE_NAME, DEFAULT_GROUP, …)` | ❌ **写死** |
| `follow-api`（查找方） | `service/follow/follow-api/src/main/resources/application.yml:42` | `group: ${NACOS_GROUP:DEFAULT_GROUP}` | ✅ 读配置 |
| Go（真源） | `service/follow/rpc/etc/follow.yaml:4-7` | `Etcd: { Hosts: [...], Key: follow.rpc }` | ✅ **可配** |

**活体 A/B（唯一变量 = `NACOS_GROUP`）**：

| 组 | `follow-rpc` 注册到 | `follow-api` 查询的 group | 结果 |
|---|---|---|---|
| **A**（不设） | `DEFAULT_GROUP` | `DEFAULT_GROUP` | ✅ 一致 ⇒ follow-api 6.62s 起 · 解析正常 |
| **B**（`G1_GROUP`） | `DEFAULT_GROUP`（**无视环境变量**） | `G1_GROUP` | ❌ **失配** |

⚠️ **`DRIFT-FOLLOW-004`（`server-addr` 硬编码 `:8848` ⇒ 忽略 `NACOS_PORT`）已于 2026-10-24 修复** ⇒
那一轮审的是「`NACOS_ADDR` / `NACOS_PORT` 的**变量语义**」这一维 ⇒ ⚠️ **「`NACOS_GROUP` 是否真被消费」这一维没人查过**
⇒ **同域、同族、漏审一维** ⇒ 见 users 侧 §7.4 的元教训（「配置项存在」≠「配置项被消费」）。

### 7.2 `DRIFT-G1-002` — 🔴 `follow-api` 缺 `immediate-connect-timeout` ⇒ 与 Go 的 `MustNewClient` panic 语义不一致

- **Go**：`service/follow/api/internal/svc/servicecontext.go:27` ⇒ `followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc))`
  ⇒ go-zero v1.10.2 `zrpc/client.go:50-54` `logx.Must(err)` ⇒ **失败即 panic ⇒ 进程起不来**；`zrpc/internal/client.go:18` `dialTimeout = 3s`
- **RAG**：`follow-api` 的 `grpc.client.follow-rpc` **没有** `immediate-connect-timeout`
  ⇒ 对照：`admin-api` **有**（`application.yml:120`，`DRIFT-ADMINAPI-009` 已修）⇒ **同一 Go 语义，RAG 三个 api 里只对齐了一个**

**活体实证（B 组）**：`follow-api` **照样启动**（6.50s）⇒ 启动日志**无异常** ⇒ 首次业务请求才解析失败
⇒ 与 `user-api` 同款（实测 `user-api` ⇒ `HTTP 200 {"code":1015,"msg":"服务繁忙"}` + 日志 `No servers found for user-rpc`）
⇒ 🔴 **DRIFT：Go = 起不来 / RAG = 起来但请求全失败**。

### 7.3 🆕 A8 活体新证据：`baseline-version: 0` 在**非空 schema** 下真的生效

本轮 follow-rpc 与 users 域两个 rpc **同库并发启动**（`g1probe` 库，`public` schema）⇒
users 域的 `SchemaInitializer` **先**建了 `users_user` / `admin_admin` 等表 ⇒ schema **非空** ⇒
follow-rpc 的 Flyway 触发 `baseline-on-migrate: true` ⇒ 实测账本：

```
installed_rank | version |      description       | success
              1 |       0 | << Flyway Baseline >> | t
              2 |       1 | create follow relation | t
              3 |       2 | create block relation  | t
```

⇒ ✅ **baseline 标记打在 version 0 ⇒ V1/V2 照常执行**（若 `baseline-version` 是默认的 1 ⇒ **V1 会被整条跳过**）
⇒ ⇒ **AGENTS.md §6.3 问题 4 记的残留风险（「新增模块若忘加 `baseline-version: 0` ⇒ 起得来、首次查库才炸」）本轮实测**没踩** ⇒
⇒ 🟢 **follow-rpc 的 `baseline-version: 0` 有活体证据**（此前只有源码级）。

⚠️ 但这**只证明 follow-rpc 一家**。其余模块仍需各自验证（`article-rpc` / `favorite-rpc` 缺 `baseline-version: 0` ⇒ 那两条**是 G2/G3 的活**，不是 G1 的）。

### 7.4 第 0 步执行口径（先记，避免「跑绿」被误读）

| 项 | 值 |
|---|---|
| 组件来源 | 全部由本工作树 `mvn package` 产出（**不碰 `Sea-user-api` 容器里的旧镜像**） |
| 隔离 | PG `userprobe-postgres:56432` 的自建库 `g1probe` · Redis `userprobe-redis:57379` · Nacos `nacos-standalone:8848` 的自建 namespace `g1probe` · 端口全在 5xxxx 段 —— **不碰 `8081`/`8889`/`8082`（有别人的进程）** |
| A 组（不设 `NACOS_GROUP`） | ✅ **6/6 组件真启动** · **0 解析失败** |
| B 组（设 `NACOS_GROUP=G1_GROUP`） | ⚠️ 3 rpc 注册到写死 group · `admin-api` 进程退出 · `user-api`/`follow-api` 起得来但运行时解析不到 |
| 证据强度 | **RAG 侧 = L3 活体**（真容器 + 真 Nacos + 真 PG + 真 Redis + 真 HTTP 请求）· **Go 侧 = L1 源码行号** ⇒ **两侧未同时活体对拍 ⇒ 不是 L4** |
