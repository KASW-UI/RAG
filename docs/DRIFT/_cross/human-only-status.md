<!-- @move AGENTS.md §二 · HUMAN_ONLY.MD 的存在性状态与校正记录 -->

# HUMAN_ONLY.MD 存在性台账

> 从 AGENTS.md §二 搬出（**定义留在 AGENTS.md，状态在这里**）。
> ⚠️ 改这份清单前**先跑一次 `glob **/HUMAN_ONLY.MD`**，别照抄上一版 ——
> 「文件已写」同样是需要证据的断言（AGENTS.md §3.3 同款形态）。

---


## 〇、当前状态（2026-10-23）

**权威索引：[`docs/DRIFT/_INDEX.md`](../../../docs/DRIFT/_INDEX.md)** —— 按域列出全部台账、搬移账目、证据边界。本节只给结论。

> 🔴 **2026-10-23 02:00 重大更新**：§3.5 的 **23 个组件里，12 个 api/rpc 已全部完成首轮组件级对照**
> （12 个并发会话，台账 +11,697 行）。**12 个组件结论全部是 `SEMANTICALLY NOT EQUIVALENT`**
> —— 合计 **34 条 `SEMANTIC MISMATCH` + 52 条 `OBSERVABLE DIFFERENCE`**。
> 下表已按台账重写，**旧表述全部作废**；逐条证据以各组件台账为准。

| 域 | 语义对照（Go ↔ RAG） | 台账 |
|---|---|---|
| `users` | 🔴 4 个组件全部 `NOT EQUIVALENT`（user-api 2 MISMATCH、user-rpc 1、admin-api 3、admin-rpc 0 MISMATCH 但 6 OBSERVABLE）。`extra_info` / `is_current` 已修并经真 PG 实证；**`UpdateSelf` 与 uid 两条历史回归经复核不属该域** | [`docs/DRIFT/users/`](../../../docs/DRIFT/users/_shared.md) |
| `message` | 🔴 api 5 MISMATCH + 1 OBSERVABLE；rpc 1 MISMATCH（负 `offset` ⇒ 500）+ 5 OBSERVABLE。**`broadcast:1` 会误发全量**。`,string` 漂移复核仍关闭 | [`docs/DRIFT/message/`](../../../docs/DRIFT/message/api.md) |
| `like` | 🔴 api **9 MISMATCH**（本轮最高，**62 条绿测试零覆盖**）；rpc 4 MISMATCH 且**组件根本起不来**。事实投递链路**跨三个仓库且末端不在任何仓** ⇒ 链路无法闭环 | [`docs/DRIFT/like/`](../../../docs/DRIFT/like/rpc.md) |
| `common` | ✅ 审计完成 | [`docs/DRIFT/common/`](../../../docs/DRIFT/common/audit.md) |
| `hot` | 🔴 rpc **组件根本起不来**（Jackson 3 装配缺失）+ 重桶 `hash()` 仿错影响 **49.5% 的 key**；api 1 MISMATCH（重复 query 参数）。⚠️ 旧结论「双侧对拍逐字节一致」**仅在 `width=100000` 无碰撞区间成立，不可外推** | [`docs/DRIFT/hot/`](../../../docs/DRIFT/hot/api.md) |
| `article` | 🔴 rpc 4 MISMATCH（jsonb `@ResultMap` 缺失致静默 null、`updated_at` 永不推进）+ 4 OBSERVABLE，**推翻了 S-3 的论证依据**；api 2 MISMATCH（gRPC 码映射丢失、超时作用域差 35×）—— **api 首次对照即 NOT EQUIVALENT** | [`docs/DRIFT/article/`](../../../docs/DRIFT/article/rpc.md) |
| 其余子模块 | ⚠️ **6 个 `*-proto` 语义层一致**（复制非翻译，4 个无回归网 ⇒ §3.4）；5 个 `*-common` 本轮**未单独审查**（作为同域公理被引用） | 见 DRIFT 索引 |

> ⚠️ **「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」**（前者只证明读 Go 读得对）。
> ⚠️ **证据强度分层（2026-10-23 实测）**：只有 `like-api` / `hot-api` 拿到**双侧活体对拍**；
> `article-rpc` / `admin-rpc` / `hot-rpc` / `like-rpc` / `message-rpc` / `user-rpc` 拿到**真 PG 或真启动**；
> `article-api` / `admin-api` / `message-api` / `user-api` 的上限是**静态源码**。
> ⇒ 12 份台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。

> **仍未关闭的三类**（明细台账在 DRIFT，**勿凭本表下结论**）：

1. **已签字的有意偏离** 9+ 条 —— 是决策不是缺陷，但两侧确实不一致 ⇒ [`docs/Intentional_Deviations/`](../../../docs/Intentional_Deviations/_INDEX.md)
   ⚠️ **2026-10-23 修复轮新增 3 条签字偏离**（`SD-2026-10-23-01/02/03`）：
   `admin-api` 的 `OperatorId`（Go 恒 0 且不校验 claim vs RAG 严格校验）、
   `user-api` 的 `uid`（Go 两套解析语义 vs RAG 统一拒绝）、
   `message-api` 的 `affected`（Go `int64` 无 `,string` vs RAG 输出字符串）。
   **三条共性：RAG 做了更严/更安全的那一侧，不是忘了做。**
   ⇒ 对拍看到这三条变红，先确认是不是这三条，**不要当新回归去「修」**。
2. **DDL 12 处列类型差异** 未处理；**索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED**。
   仅 message 域时间列已评估过影响（DRIFT-MSG-V-007：JVM 非 UTC 时时间戳偏移一个时区差），**其余 10 处从未评估**。
   ⚠️ **2026-10-23 复核后实际差异数远超 12 处**：admin-rpc 7 处、user-rpc 4 个时间列
   `timestamptz↔timestamp`、like-rpc `like_domain_fact_outbox` 列类型不一致**且丢失 Go 侧 CHECK / 触发器 / 部分索引**、
   message-rpc 的 `V2` 迁移 `ALTER TABLE ADD CONSTRAINT` **无 `IF NOT EXISTS`**（在 GORM 建过表的库上会失败）。
3. **跨组件不变量仍无法单侧闭环**（§3.5 表）：
   `users_user` 建表交叉（user-rpc ↔ admin-rpc）、**事实投递链路末端不在任何仓**（like 三仓库）、
   三模块 Flyway 账本共存（message ↔ like ↔ article）、client 契约。
   ⇒ **每个模块接手的第一件事仍是「先让它真的启动一次」**：
   本轮已实测 **`hot-rpc` 与 `like-rpc` 根本起不来**，而它们此前的测试都是全绿的。

> **2026-10-23 修复轮**：5 个组件的启动阻塞 / 语义缺陷已修，**每条都配真 PG 守门测试**（非 mock）。
> 逐组件记录 ⇒ [`docs/DRIFT/_cross/fix-round-2026-10-23.md`](../../../docs/DRIFT/_cross/fix-round-2026-10-23.md)
> 本轮 4 条元教训 ⇒ [`docs/Pitfalls/_cross/fix-round-lessons.md`](../../../docs/Pitfalls/_cross/fix-round-lessons.md)

## 一、项目身份与双轨策略

RAG 仓库路径：`/Users/pr/Documents/java&golang/RAG`。

本项目对**已废弃商用架构** `Sea-RideTheWind_golang` 做 Spring Boot 重构。RAG 不是教学练手项目，而是商用架构的现代化迁移产物——按工作项目标准交付。

| 项目 | 路径 | 角色 | 实现风格 |
|---|---|---|---|
| **Sea-RideTheWind_golang** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | **真理之源（唯一真源，2026-10-20 第三次切换）** —— 商用架构的原始实现，2026-10-18 起冻结，所有行为的唯一判定标准 | go-zero + GORM |
| **RAG（本仓库）** | `/Users/pr/Documents/java&golang/RAG` | **Spring Boot 重构产物** —— 按工作项目标准把 Go 行为用 Spring 生态表达；非 Spring Boot 的 Java 写法（如 JPA + flat `internal/{logic,model,server,svc}`）不在重构范围内 | **MyBatis-Plus + domain/mapper/service 分层 + 幂等 DDL 脚本 + Bootstrap 抽出** |

**RAG 的存在范围**：用 Spring 生态表达 Go 的行为。**不**对任何"非 Spring Boot 的 Java 实现"做对照——那些是另一种工作，不属于本项目的范围；也不接收 RAG 反向同步。

**微服务范围（2026-10-23 用户定）**：本项目**只搬这 6 个微服务** ——
`article` / `hot` / `like` / `users-user` / `users-admin` / `message`，
共 **23 个组件**（6 api + 6 rpc + 5 common + 6 proto，权威清单见 **§3.5**）。
Go 侧另外 **7 个微服务**（`comment` / `favorite` / `follow` / `knowledge` / `points` / `security` / `task`）
**不在本项目范围内** ⇒ **不是待办**。
⚠️ **「未翻译」≠「该做」**：范围是**决策**，不是进度。
任何「proto 还差 6 个没复制」「还有 N 个微服务没搬」的说法一律作废。

### 1.1 三次真理之源切换的结论

| 时间 | 真源 | 结果 |
|---|---|---|
| 2026-10-18 前 | Go | 基线正确 |
| 2026-10-18 ~ 10-20 | Java 镜像 | 引入了一整层可漂移的中间副本 |
| **2026-10-20 起（本文件）** | **Go** | 回到唯一真源 |

**为什么退回 Go（2026-10-20 行为一致性审计的实测结论）**：

`service/users` 的 Go vs RAG 对照结果：

| 实现 | 缺陷数 | 说明 |
|---|---|---|
| **Go** | **0** | 冻结不动，每个行为有唯一确定答案 |
| **RAG** | **11（相对 Go 的回归）** | 全部集中在「把 Java 写法换成更地道的 Spring 写法」的位置 |

那 11 处回归：`CreateAdmin` 事务 self-invocation 失效、邀请码缺 `FOR UPDATE`、`extra_info` jsonb 写入丢失 typeHandler、`SelectAvatar` 响应 `is_current` 陈旧、`UpdateSelf` 空 email 清空列、uid 解析静默转 0、`success` 字段语义反转、`JwtUtil` 密钥补齐被删、HTTP 状态码语义变更、Flyway 双服务版本冲突、`@ResponseBodyAdvice` 二次包裹。

**教训**：多一跳的代价不是「多一次翻译」，而是**多一整层可以无声漂移的地方**。政策上的直接结论就是：**只留一个源**。

**2026-10-20 起两条线的分工**：

- **Sea-RideTheWind_golang**：**真理之源**。冻结，不再接收反向同步——一个不会移动的源才是最可靠的真源。
- **RAG**：Spring Boot 重构产物。行为以 Go 为准，表达以 Spring 为准。任何偏离 Go 的"更地道的 Spring 写法"都是潜在回归点（§3）。

## 二、HUMAN_ONLY.MD：设计目标，不是现状

每个非叶子子模块各有一份 `HUMAN_ONLY.MD`，包含两部分：

1. **文件阅读顺序与职责**：描述当前实现下每个文件的角色（这一部分反映现状）。
2. **Java 理论文件树**：描述目标架构——`domain/Entity + mapper/Mapper + service/DomainService + service/impl/GrpcImpl + bootstrap/* + resources/db/migration/*.sql` 等。

当前已落 HUMAN_ONLY.MD 的子模块（**2026-10-22 用 `glob **/HUMAN_ONLY.MD` 实测校正，全仓共 4 份**）：

- **service/users/user**：user-rpc / user-api
- **service/users/admin**：admin-rpc / admin-api

⚠️ **本节此前写的「共 7 份」是错的**（§7.2 的文件清单也同步错了）。以下 3 条**被登记为已存在、实际不存在**：
`service/users/users-common/HUMAN_ONLY.MD`、`service/message/message-rpc/HUMAN_ONLY.MD`、
`service/message/message-api/HUMAN_ONLY.MD`。
⇒ **「文件已写」同样是需要证据的断言**（§3.3 同款形态）：改这些清单前先跑一次 glob，别照抄上一版。

未覆盖的子模块：**users-common / message-rpc / message-api**（虽已实现并做过 Go ↔ RAG 对照，但**文档缺失**）
+ **Go 有、RAG 尚未翻译的 7 个微服务**：`comment` / `favorite` / `follow` / `knowledge` / `points` / `security` / `task`
（🚫 **不在本项目范围内，不是待办** ⇒ §1「微服务范围」）
+ **已翻译但对照未闭环的组件**：`article-api`（从未对照）、`article-rpc`（首轮组件级完成）、
`hot-api`（仅源码级对照）、以及 `like-common` / `message-common`
（**连 Go ↔ RAG 对照都还没做过**，见 §0）。前者建议按 phase 补文档，后者暂按 Go 行为对齐、进入对应 phase 再补。
  - ⚠️ **2026-10-23 校正**：此处原把「6 个 `*-proto`」也列进"从未做过"，**该条已过期**——
    6 个 proto 实为**从 Go 侧复制**（非翻译），2026-10-23 实测**语义层 6/6 逐条一致**，
    详见 **§3.4**；其中 `user` / `admin` 有守门脚本，另 4 个**只有一次性实测、无回归网**。
⚠️ **2026-10-23 实测校正**：原枚举写作「article / comment / favorite / follow / hot / like / points / security / task / common」——
① **漏了 `knowledge`**：Go 侧是**独立微服务**（含 api 组件，依赖 Neo4j + Milvus），RAG 侧**完全未翻译**，
仅 [`Infrastructure.md`](../../../Infrastructure.md) 标「❌ 未实装」；② **`hot` / `like` / `common` 三项已过期**——
§0 记录它们已完成或部分完成对照，不该继续列在「从未做过」里。

`HUMAN_ONLY.MD` 第 2 部分的"Java 理论文件树"是 aspirational design，从未被代码落地过。RAG 的本次重构就是把这个目标架构在 RAG 上落地一遍。

**2026-10-20 起取消「反向同步」**：真源（Go）不接受任何方向的写入或修正。此前允许 RAG 反向同步到 Java 镜像，等于让真源变成**双向可变**——这比单向漂移更危险，因为任何一侧都无法再被信任为判定标准。RAG 产出的是 Spring 表达经验（值得沉淀进 `HUMAN_ONLY.MD`），不是行为定义。

## 三、语义对齐纪律（Spring idiomatization 的唯一防线）

RAG 与 Go 的差异只允许出现在**表达层**。本节规定如何确保差异不越界。
