# AGENTS.md: RAG 项目政策

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件，**只放政策与索引**。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **真源 = Go**（2026-10-20 第三次切换，非 Spring Boot 的 Java 实现不在范围内，详见 §1.1）。
> **2026-10-23 瘦身**：逐条漂移与修复记录移出到 [`docs/DRIFT/`](docs/DRIFT/_INDEX.md)，
> 踩坑与元教训在 [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md)。

## 〇、当前状态（2026-10-23）

**权威索引：[`docs/DRIFT/_INDEX.md`](docs/DRIFT/_INDEX.md)** —— 按域列出全部台账、搬移账目、证据边界。本节只给结论。

> 🔴 **2026-10-23 02:00 重大更新**：§3.5 的 **23 个组件里，12 个 api/rpc 已全部完成首轮组件级对照**
> （12 个并发会话，台账 +11,697 行）。**12 个组件结论全部是 `SEMANTICALLY NOT EQUIVALENT`**
> —— 合计 **34 条 `SEMANTIC MISMATCH` + 52 条 `OBSERVABLE DIFFERENCE`**。
> 下表已按台账重写，**旧表述全部作废**；逐条证据以各组件台账为准。

| 域 | 语义对照（Go ↔ RAG） | 台账 |
|---|---|---|
| `users` | 🔴 **首轮对照**：4 个组件全部 `NOT EQUIVALENT`（user-api 2 MISMATCH、user-rpc 1、admin-api 3、admin-rpc 0 MISMATCH 但 6 OBSERVABLE）。✅ 已修：`extra_info` / `is_current`（真 PG 实证）、**admin-rpc `update_time` 5 处推进**（2026-10-23；守门 `AdminRpcUpdateTimeAdvanceWiringTest`，**81 tests / 0 failures**）。⚠️ **admin-api 的 `OperatorId`（1 条）与 user-api 的 `uid`（1 条）已签字不修**（`SD-2026-10-23-01` / `-02`）——对拍看到它们变红是**预期**。**`UpdateSelf` 与 uid 两条历史回归经复核不属该域** | [`docs/DRIFT/users/`](docs/DRIFT/users/_shared.md) |
| `message` | 🔴 **首轮对照**：api 5 MISMATCH + 1 OBSERVABLE；rpc 1 MISMATCH（负 `offset` ⇒ 500）+ 5 OBSERVABLE。✅ **`broadcast:1` 误发全量已修**（2026-10-23，`Boolean←Integer`=Fail；守门 `MessageApiMvcPipelineTest` 42/42）。`affected` 的 `,string` 漂移**已签字**（`SD-2026-10-23-03`，不修） | [`docs/DRIFT/message/`](docs/DRIFT/message/api.md) |
| `like` | 🔴 **首轮对照**：api **9 MISMATCH**（本轮最高，**62 条绿测试零覆盖**）；rpc 4 MISMATCH。✅ **rpc 6 条启动阻塞已修**（2026-10-23，缺 `spring-boot-kafka` 等 ⇒ 此前**根本起不来**；守门 `LikeRpcSpringContextWiringTest` 7/7）。事实投递链路**跨三个仓库且末端不在任何仓** ⇒ 链路无法闭环 | [`docs/DRIFT/like/`](docs/DRIFT/like/rpc.md) |
| `common` | ✅ 审计完成 | [`docs/DRIFT/common/`](docs/DRIFT/common/audit.md) |
| `hot` | 🔴 **首轮对照**：rpc ✅ **4 条启动阻塞已修**（2026-10-23，Jackson 3 装配缺失等 ⇒ 此前**根本起不来**；守门 `HotRpcSpringContextWiringTest` 6/6）；🔴 **重桶 `hash()` 仿错仍影响 49.5% 的 key（未修）**；api 1 MISMATCH（重复 query 参数）。⚠️ 旧结论「双侧对拍逐字节一致」**仅在 `width=100000` 无碰撞区间成立，不可外推** | [`docs/DRIFT/hot/`](docs/DRIFT/hot/api.md) |
| `article` | 🔴 **首轮对照**：rpc 4 MISMATCH + 4 OBSERVABLE。✅ **jsonb `@ResultMap`（实为 4 处，非台账原记 3 处）+ `updated_at` 推进已修**；🔴 **并新发现公开列表双重损坏**（`<if>` 参数名 typo 挡住解析期，后面还藏着 `<where>` 生成双 `WHERE` ⇒ **公开列表 100% 报错**）—— 一并修（2026-10-23；守门 `ArticleJsonbResultMapWiringTest` + `ArticleTransactionWiringTest`，**105 tests / 0 failures**）。**S-3 的论证依据仍被推翻**。api 2 MISMATCH（gRPC 码映射丢失、超时作用域差 35×）—— **api 首次对照即 NOT EQUIVALENT** | [`docs/DRIFT/article/`](docs/DRIFT/article/rpc.md) |
| 其余子模块 | ⚠️ **6 个 `*-proto` 语义层一致**（复制非翻译，4 个无回归网 ⇒ §3.4）；5 个 `*-common` 本轮**未单独审查**（作为同域公理被引用） | 见 DRIFT 索引 |

> ⚠️ **表内的 MISMATCH / OBSERVABLE 计数是「首轮组件级对照」的审计结论，不是当前状态**。
> 已修项一律在上表用 ✅ 单独标出（带核对日期 + 守门测试名）；判断某一条的现状**一律回台账**，勿凭本表下结论。
> ⚠️ **「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」**（前者只证明读 Go 读得对）。
> ⚠️ **证据强度分层（2026-10-23 实测）**：只有 `like-api`/`hot-api` 拿到**双侧活体对拍**；
> 其余多为**真 PG 或真启动**；`article-api`/`admin-api`/`message-api`/`user-api` 的上限是**静态源码**。
> ⇒ 台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。

1. **已签字的有意偏离** 9+ 条 —— 是决策不是缺陷，但两侧确实不一致 ⇒ [`docs/Intentional_Deviations/`](docs/Intentional_Deviations/_INDEX.md)
   ⚠️ **2026-10-23 修复轮新增 3 条签字偏离**（`SD-2026-10-23-01/02/03`）—— 共性：**RAG 做了更严/更安全的那一侧**。
   ⇒ 对拍看到它们变红，先确认是不是这三条，**不要当新回归去「修」**。
2. **DDL 12 处列类型差异** 未处理；**索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED**。
   仅 message 域时间列已评估过影响（DRIFT-MSG-V-007：JVM 非 UTC 时时间戳偏移一个时区差），**其余 10 处从未评估**。
   ⚠️ **实际差异数远超 12 处**（admin-rpc 7 处、user-rpc 4 个时间列、like-rpc 丢失 CHECK/触发器/部分索引、message-rpc `V2` 迁移无 `IF NOT EXISTS`）⇒ 明细见 DRIFT 台账。
3. **跨组件不变量仍无法单侧闭环**（§3.5 表）：
   `users_user` 建表交叉（user-rpc ↔ admin-rpc）、**事实投递链路末端不在任何仓**（like 三仓库）、
   三模块 Flyway 账本共存（message ↔ like ↔ article）、client 契约。
   ⇒ **每个模块接手的第一件事仍是「先让它真的启动一次」** —— 这条纪律**不因为修过就作废**：
   2026-10-23 修复轮**之前**实测 `hot-rpc` / `like-rpc` **根本起不来，而它们此前的测试全绿**；
   本轮已修（见上）。⚠️ 更该记住的是：**本轮所有修复都是在「测试全绿 / 编译通过 / 上下文能启动」的状态下做的** ——
   article-rpc 101 条全绿时 jsonb 恒 `null`、公开列表 100% 报错；
   admin-rpc 的绿测试还把 `update_time` 不推进**锁死**了。⇒ 「跑绿」不是「对」。

> **2026-10-23 修复轮**：5 个组件的启动阻塞 / 语义缺陷已修，**每条都配真 PG 守门测试**（非 mock）。
> 逐组件记录 ⇒ [`docs/DRIFT/_cross/fix-round-2026-10-23.md`](docs/DRIFT/_cross/fix-round-2026-10-23.md)
> 本轮 4 条元教训 ⇒ [`docs/Pitfalls/_cross/fix-round-lessons.md`](docs/Pitfalls/_cross/fix-round-lessons.md)

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

真源三次切换（10-18 前 Go → 10-18~10-20 Java 镜像 → **10-20 起回到 Go**）。
退回 Go 的依据：2026-10-20 行为一致性审计实测 —— **Go 0 缺陷，RAG 11 处回归**，
且 11 处**全部**集中在「把 Java 写法换成更地道的 Spring 写法」的位置。
⇒ **教训**：多一跳的代价不是「多一次翻译」，而是多一整层可以无声漂移的地方 ⇒ 政策结论：**只留一个源**。
⇒ 三次切换时间线与 11 处回归逐条清单 ⇒ [`docs/DRIFT/_cross/truth-source-switches.md`](docs/DRIFT/_cross/truth-source-switches.md)


**2026-10-20 起两条线的分工**：

- **Sea-RideTheWind_golang**：**真理之源**。冻结，不再接收反向同步——一个不会移动的源才是最可靠的真源。
- **RAG**：Spring Boot 重构产物。行为以 Go 为准，表达以 Spring 为准。任何偏离 Go 的"更地道的 Spring 写法"都是潜在回归点（§3）。

当前已落 **4 份**（`users/user` 与 `users/admin` 各 2 份）。
⇒ **存在性台账、已撤销的误登记与未覆盖清单** ⇒ [`docs/DRIFT/_cross/human-only-status.md`](docs/DRIFT/_cross/human-only-status.md)


### 3.1 Go ↔ RAG 对照（强制）

任何行为一致性审查必须**以 Go 为唯一基准**核对 RAG。Go 已冻结，行为漂移只可能从 RAG 一侧发生，因此 Go ↔ RAG 两方对照足以发现所有行为问题。

**审查方式**：

```
对每一个行为决策点，读 Go 对应位置的源文件 → 与 RAG 端对应实现逐字对照
```

**对照必须覆盖**（不限于）：
- proto 字段号、message 名称、错误码语义
- JWT claim 名称与必需字段（如 `userId`、`status` 的 presence 语义）
- 数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT
- 行级悲观锁与事务边界
- 字符串解析（uid、keyword）失败行为
- HTTP 路径与方法

**避免的对照陷阱**（历史教训）：
- **不要拿"现有 RAG 代码"当对照基准**——现存代码里就有 11 处回归，对照它会"对齐到错"
- **不要拿代码注释当对照基准**——现存注释里写的是 Java 镜像视角，Go 视角可能完全不同
- **不要"读两遍 Go 取并集"**——两遍都错的机会不等于零，必须有一遍回到原文件 line-by-line

> **历史**：2026-10-18 政策曾引入 Java 镜像作为第二对照面，但实测发现 Java 镜像本身带 2 处缺陷且与 Go 在多处一致地一起错，导致两方对照也漏掉 `admin-rpc` 直写 `users_user` 与 Flyway 拆分两类问题。**结论**：只留一个源、两方对照——而不是三方对照。

### 3.2 idiomatization 留痕（强制）

RAG 每次选择「用更地道的 Spring 写法」时，必须能回答：

> **这段比 Go 多做了什么？语义还一样吗？**

回答不了 = 无出处的 idiomatization = **不可 review，不得合入**。

落法：写入该类/方法的 javadoc，格式：

```java
/**
 * <p>L4 idiomatization：HandlerInterceptor 取代 Go 的 rest.WithJwt + CheckBlacklistMiddleware。
 * <p>Go 出处：service/user/user/api/internal/handler/routes.go（两段 AddRoutes，public 路径不带 JWT）。
 * <p>语义等价论证：public 路径集合逐条一致；blacklist key 前缀与 fail-open 行为一致；
 *   <b>HTTP 状态码差异</b>：Go 写 401，本实现走异常处理器 —— 见下方已知差异。
 */
```

**11 处历史回归全部属于「无出处 idiomatization」**，它们在注释里都写得很完整，唯独没有一处标注 Go 出处，导致 review 时无从判断"这是必要表达差异还是悄悄改了行为"。

### 3.3 框架机制必须验证实际生效（强制）

**3.3.1（2026-10-22 新增，全仓适用）「配置里没写」≠「没开」，也≠「没做」**

Go 侧大量行为的默认值**藏在 struct tag 里**而不是 yaml 里；反之，Java 侧大量行为**藏在框架里**
而不是注解里。只看配置文件/注解名称做双向推断，两头都会错。

⇒ **强制要求**：
1. 判断某个 Go 默认行为是否生效，**必须读到消费该 tag 的那段控制流**，
   只贴 struct 定义行不算证据；
2. 判断某个 Java 框架能力是否生效，**必须有运行证据**（真实管线 / 真容器），
   「pom 里有依赖」「类上有注解」都不算（AGENTS.md §3.3 原有纪律）；
3. 写 `文件:行号` 证据时，行号要指向**消费点**而不是**声明点**。

**3.3.2（2026-10-22 新增）以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，本身是高危项**
（案例 ⇒ 同上文件）


⇒ **硬要求**：任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，
否则**不得**用该措辞，改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。

#### 3.3.3 原有纪律（2026-10-20 起）

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler）全部是运行期动态装配，出错时**不报错**。
（四类静默失效的实证 ⇒ 同上文件）


**规则**：凡引入新的框架机制，必须配一条**能真正触发该机制**的测试。
纯 Mockito mock 数据层的单测**不算**——它不会执行 SQL，也不会装配 Spring 上下文，天然测不到本节三类问题。

**规则（2026-10-21 补）**：判断「一个注入/开关有没有真生效」**不能看启动日志**——
本节第四类里，注入点的 echo 照打、jar 照起、日志零告警，效果却是零。
唯一可靠的判据是**运行时证据**（本例是 PG 侧是否出现 `57014 query_canceled`）。
凡是「注入后实测无变化」的结论，一律先怀疑注入本身没到位，再怀疑机制无效。

**规则（2026-10-22 补 · 由「E-1 收口抓到 text/plain 400 丢 Content-Type」推出）**：
**「代码里写了」与「客户端收到」之间隔着一次 commit。**

**规则（2026-10-21 再补 · 由 H2 假绿事件推出）**：**断言「Bean 存在」不等于断言「机制生效」。**

**规则（2026-10-21 再补 · 由「46 处批量插入」推出）**：**凡是「脚本批量改 N 处」，
必须配一条覆盖全部 N 处的守恒断言，而不是逐点行为测试。**

**规则（2026-10-22 补 · 由 message-api 的 `,string` 漂移推出）**：
**绿测试可能是「错误答案的守卫」，比没有测试更危险。**

**强制三条**：
1. 论证里的每个 Go 事实都必须带 `文件:行号`，**不许只写结论**；
2. 凡写「与 Go 一致」，必须有**两侧同时跑的探针**（不是只跑 RAG）——
   最小形态：Go 侧直接调用真源的解析包，RAG 侧用项目**真实的** mapper（不是自己 new 一个）；
3. 修复漂移时**先改/删错误断言，再改实现** —— 否则会先撞上「测试红了」而误以为自己改错了。

（案例与完整经过见 `docs/Pitfalls/_cross/testing-pitfalls.md`。）

⚠️ **「只跑 RAG 的探针」同样不算数**：它证明的只是「代码写成了这样」，
证明不了「Go 是这样」。§docs/DRIFT/message/rpc.md 的变异测试是同一问题的另一半 ——
变异测试能证明测试**不是假绿**，但**不能证明测试的期望值是对的**。两者缺一不可。

**规则（2026-10-22 晚补 · 由 `hot-rpc` 的 Spring Boot 4 模块化迁移推出）**：
**「注解存在」≠「机制生效」，且 Boot 4 让这一层比以往更难发现。**

**强制**：
1. 凡用 `@KafkaListener`，必须有一条**装配层**测试断言 listener **真的进了容器的 registry**
   （不是「listener 方法存在」），且须覆盖「缺少 `spring-boot-kafka` 会怎样」；
2. 引入任何 `spring-*-starter` 类框架时，先确认该能力在 **Boot 4 下仍在 `spring-boot-autoconfigure` 里**，
   已被拆走的必须显式补对应模块（版本由 BOM 管理，不写 `<version>`）；
3. **不得**把「编译通过 / 上下文能启动」当作「注解已生效」的证据。

⚠️ **`like-rpc` 的缺依赖已实测（2026-10-23）：不是「静默失效」，是硬启动失败。**
`service/like/like-rpc/pom.xml` 只引 `spring-kafka`、缺 `spring-boot-kafka`，实测
`NoSuchBeanDefinitionException: KafkaTemplate` ⇒ **组件起不来**。
⚠️ **这条修正了本节此前的表述**：原文把它归入「静默失效（照常启动不消费）」，
实测证明这类 Boot 4 拆模块缺口在本项目表现为**硬失败**——反而更容易发现，不会带病上线。
`hot-rpc` 同款（Jackson 3 ⇒ `ObjectMapper` Bean 缺失）亦为硬失败。
⇒ 判定时**先实测启动，再决定风险等级**，不要凭形态推定。
明细见 [docs/DRIFT/like/rpc.md](docs/DRIFT/like/rpc.md) 与 [docs/DRIFT/hot/rpc.md](docs/DRIFT/hot/rpc.md)。

### 3.4 proto 是**复制粘贴**，不是翻译（2026-10-23 实测）

RAG 侧 6 个 `*.proto` **全部从 Go 侧 `proto/*.proto` 复制而来**，
既不是重写也不是翻译。改动**严格限定在 option 块与空白层面**：

| 改动项 | 覆盖 | 性质 |
|---|---|---|
| 删 `option go_package` | ✅ 6/6 | 表达层（Go 专用，Java 不消费） |
| 加 `option java_package` + `java_multiple_files = true` | ✅ 6/6 | 表达层（生成代码落包必需） |
| 缩进风格统一（`admin` / `like` 由 4 空格改 2 空格）、末尾空行 | ✅ 6/6 | 表达层 |
| `user.proto` → RAG 侧改名 `users.proto`，并加一段解释性注释 | 仅 user | 表达层 |
| **message 名 / 字段名 / 字段号 / 类型 / 枚举值** | **✅ 6/6 逐条一致** | **行为层（wire contract）** |

⇒ **实测数据、回归网状态与待办** ⇒ [`docs/DRIFT/_cross/proto-verification.md`](docs/DRIFT/_cross/proto-verification.md)

⚠️ **本条的效力边界（三条，越界即失效）**：
1. **复制忠实 ≠ 消费方式已验**。本条只证明 `.proto` **文件本身**两侧一致，
   **不证明 RAG 侧使用生成代码的方式**与 Go 一致（`optional` presence 语义、
   字段号是否被某处硬编码覆盖、Jackson/protobuf 配置差异）。
2. **不外推到 api / rpc 组件**。proto 一致 **推不出** wire 层行为一致；
   §0 的组件状态一律以各域台账为准。
3. **不覆盖范围外的 6 个 proto**（见上表第三档）——它们**不是待办**。

### 3.5 组件级审查范围（2026-10-23 用户定 · 23 个组件）

**审查粒度 = 组件级**，不是微服务级、也不是 api/rpc 二分。**common / proto 不是「可选项」**（同域对照的默认公理：
共享 DTO、错误码契约、wire 契约）—— 少审它们 = **拿一面没人校过的镜子照自己**，与 §3.1 禁止的
「拿现有 RAG 代码当对照基准」同源。

**范围内组件清单（权威，审查任务按此拆分）** —— 6 微服务 / **23 个组件** = 6 api + 6 rpc + 5 common + 6 proto：
`article` 4 · `hot` 4 · `like` 4 · `message` 4 · `users` 7（含 `user`+`admin` 两套 api/rpc/proto）。
⇒ 逐组件台账入口见 [`docs/DRIFT/_INDEX.md`](docs/DRIFT/_INDEX.md)。

**为什么不能退到微服务粒度**：域粒度下 `hot`（rpc 逐字节一致 + api 仅源码级对照）与
`article`（rpc 首轮完成 + api 从未对照）都会被打成 ✅ —— **那是假绿灯**。

**跨组件不变量（不属于任何单个组件，别塞进组件任务里）**：

| 不变量 | 涉及组件 | 状态 |
|---|---|---|
| 共享 Flyway 账本 | `message-rpc` ↔ `like-rpc` ↔ `article-rpc` | 配置层已修，运行时 `UNKNOWN`（§6.3 问题 4） |
| 建表责任交叉 | `user-rpc` ↔ `admin-rpc`（都建 `users_user`） | 未审 |
| 事实投递链路 | `like` 的 api ↔ rpc ↔ 第三个仓库 | 链路本身不是任何组件的属性 |
| client 契约 | api 侧 client ↔ 对端 rpc 的 proto | 未审 |

**排序约束**：**rpc 必须先于 api**。Go 侧边界是 api→rpc，api 的行为依赖 rpc 的正确实现；
rpc 没验对就先验 api，产出的是「RAG api 行为正确」的假结论
（`message-rpc` 曾有 21 条全绿测试却一次没起过）。

## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService` / `AdminDomainService` | HUMAN_ONLY.MD 描述单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口 | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + users 域 DDL 脚本 | **迁移机制已于 2026-10-21 改为幂等重放**（Flyway 条目处置完毕，理由见 §6.3 问题 1） |

### 4.2 5 阶段范围

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **Phase 0** | RAG 仓库建 worktree、提交基线、4 个 issue、4 份 spec（`RAG/.agents/specs/<slug>.md`） | 4 个 issue + 4 份 spec 落仓 |
| **Phase 1（pilot）** | `user-rpc` 落地完整 pattern：JPA→MyBatis-Plus + DataModel→2 Mapper + 9 Logic 合 1 + SnowflakeBootstrap + users 域 DDL | `mvn -pl user-rpc -am package` 通过 + 既有 LogicTest 改完跑绿 |
| **Phase 2** | `user-api`：HTTP 层（controller / dto / interceptor / advice / client），不引入 Mapper | `mvn -pl user-api -am package` 通过 |
| **Phase 3** | `admin-rpc`：平行 user-rpc，13 用例合 1 `AdminDomainService` + 3 Mapper + admin 域 DDL | `mvn -pl admin-rpc -am package` 通过 |
| **Phase 4** | `admin-api`：平行 user-api，13 Service + 2 client（admin-rpc + user-rpc） | `mvn -pl admin-api -am package` 通过 |
| **Phase 5** | 全量 `mvn clean package` + docker-compose 起来跑 gRPC 端到端 | 全绿 |

每阶段独立 stop point：跑绿后停下等用户 review，再决定继续。

### 4.3 用户问答原话

> 📖 原话已搬出存档（防止后续被「优化」掉）：
> [`docs/DRIFT/_cross/user-qa-verbatim.md`](docs/DRIFT/_cross/user-qa-verbatim.md)

## 五、用户协作规则（精简）

1. **每个 phase 一个 PR**。PR 自带 spec（`RAG/.agents/specs/<slug>.md`），提交前 spec 已落仓。
2. **issue 在 PR 之前**。每个 phase 起手先在 RAG 仓库开 issue 跟踪，PR body 引用 issue。
3. **分支策略：所有动作直接在 `do-my-own-gh` 上做**（2026-10-21 用户明确要求）。不开 `row/*` 分支、不建独立 worktree——即使改动很大或涉及多文件。需要隔离时用 `git stash` 而不是切分支。历史上的 `row/*` 分支（如 `row/common-fix-semantic-drift`）均为该规则生效前的产物，现已全部合并回 `do-my-own-gh`。
4. **作用范围**：本次重构仅限 RAG 仓库。`Sea-RideTheWind_golang` 已冻结只读；其他非 Spring Boot 的 Java 实现（`Sea-RideTheWind_java` 等）不在 RAG 工作范围内。
5. **Go 是真理之源**（2026-10-20 第三次切换，所有子模块统一适用）。所有 proto 字段号、错误码、JWT claims、表结构、HTTP path、Kafka topic 等语义细节，一律以 `Sea-RideTheWind_golang` 对应子模块为准。
   - RAG 与 Go 的差异**只允许出现在表达层**（框架、分层、命名、注解）。出现行为差异时，默认判定为 **RAG 错**，而非 Go 与 RAG 各有一套。
   - 引入新的 Spring 特有写法时，按 §3.2 强制标注 Go 出处；按 §3.1 走 Go ↔ RAG 对照；按 §3.3 配能真正触发该机制的测试。
6. **session 语言**：用户用中文；技术术语、代码、文件路径保留英文。
7. **review**：每个 PR 走完本地 `mvn -pl <module> -am package` + 全量 `mvn clean verify` 后，等待用户或独立 review 才能合并。**review 必须含 Go ↔ RAG 对照**。

## 六、架构决策与其他留档

> **状态表纪律（2026-10-21）**：本文件任何「已修 / 未修」断言都必须带**核对日期**，否则按 `UNKNOWN` 处理。

### 6.3 架构决策（问题 1~4；问题 1/2/3 于 2026-10-21 拍板，问题 4 于 2026-10-23 新增）

**✅ 问题 1：建表机制 —— 2026-10-21 用户拍板「直接把 Flyway 丢掉」。** users 域改用 `SchemaInitializer` 幂等重放（无版本号、无账本，接近 Go `AutoMigrate`）。⚠️ **实际范围比原记录更宽** —— 病根在其余三域原封不动成立，即**问题 4**。⇒ [`decisions.md`](docs/DRIFT/_cross/decisions.md)

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷。** 实测 Go 侧 `admin/rpc` **零出向 gRPC 依赖**，7 个管理 RPC 全是直接 SQL。⇒ [`decisions.md`](docs/DRIFT/_cross/decisions.md)

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包**（已实施）。Go 里**不存在**「全局自动注册」，RAG 曾把 `@Component` 当自动注册（一次**无出处 idiomatization**）。连带：不再有模块扫 `com.rag.common` ⇒ 曾提的拆包**动机自我消解、已撤回**。⚠️ `message-rpc` 归一仍未做。⇒ [`decisions.md`](docs/DRIFT/_cross/decisions.md)

**📌 问题 4：Flyway 账本撞号 —— 问题 1 的结论只应用到 1/4 的模块上。（2026-10-23 新增）**

问题 1 丢掉 Flyway 的**唯一根因**：`flyway_schema_history` 是 **schema 级**账本，一个 schema 只能有一本账。
⚠️ 该根因在 `message-rpc` / `like-rpc` / `article-rpc` **原封不动成立**——三者同库 `first_db`、
同 schema `public`、同账本表（**发现时** `spring.flyway.table` 全仓零命中），且 message 与 like 的 **V1–V3 直接撞号**。
任一方先启动，另一方的表就永远建不出来。

**定案**：每模块独立账本表名 `spring.flyway.table: flyway_schema_history_<模块>` ——
不动任何迁移文件名，各模块从 V1 各自重来也互不干扰，**直接消除病根**。
（否决「排全局唯一号段」：那是 10-20 已被 10-21 推翻的旧结论，且不解决根因。）

> **实施状态（2026-10-23 实测核对）**：配置层**已修** —— `message-rpc` / `like-rpc` 各加一行
> `spring.flyway.table`（**未改任何迁移文件名**）；`article-rpc` 按指示保持默认表名，此时默认账本由它独占，
> 三本账互不共享。守门脚本已转绿（`check-flyway-ledger.py` `EXIT=0`）。
>
> ✅ **运行时已确证（2026-10-23 实测，本条 `UNKNOWN` 可关闭）**：
> `message-rpc` 单模块启动日志出现 `Creating Schema History table "public"."flyway_schema_history_message"`；
> `like-rpc` 真 PG 启动**已建出** `flyway_schema_history_like` + 5 张表。
> ⇒ 静态脚本 + 启动日志 + 真 PG 三重证据，`spring.flyway.table` 运行时生效**已闭环**。
> ⚠️ **仍未闭环的是「三模块同库共存」**（message ↔ like ↔ article 同时指向 `first_db/public`）——
> 单模块各自确证不等于三本账能同时跑，该项仍记 `unresolved`。
> ⚠️ **残留风险**：将来新增第 4 个模块若忘加 `table`，会直接撞上仍用默认表名的 article。
> ⚠️ **文档缺陷仍未修**：多处声称运行时由 `FlywayLedgerIsolationTest` 负责 —— **该测试类全仓不存在**
> （`grep -r FlywayLedgerIsolationTest` 只命中文档自身）。
> 完整经过、被证伪的绕法与证据 ⇒ `docs/Pitfalls/_cross/flyway-ledger-collision.md`

### 6.4 其他子模块

| 阶段（module） | 状态 |
|---|---|
| `service/message` | `message-rpc` / `message-api` 均已完成审计 + 修复（2026-10-22）⇒ 台账 [`docs/DRIFT/message/`](docs/DRIFT/message/rpc.md) |
| Phase 5（全量 + 端到端） | 未启动；需真实 PG + Redis 集成测试补上（当前测试大量 mock 数据层） |

## 七、引用清单

### 7.1 真理之源（判定行为）

Go 真源仓库：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（**已冻结**，不接受写入）。
⚠️ **2026-10-23 实测校正**：此处原写 `/Users/pr/Documents/GitHub/Sea-RideTheWind_golang` —— **该路径不存在**（`ls` 报 No such file or directory），真实路径是 §1 表里那一行，两者不一致；已按实测改正。
RAG 不是从 Go「推测」行为，而是**逐字对照 Go 源码**——见 §3.1。

> 📖 **完整锚点表（路由/事务/行锁/身份/JWT/建表 + message 域 9 个 RPC）已搬出**：
> [`docs/DRIFT/_cross/go-anchors.md`](docs/DRIFT/_cross/go-anchors.md)

### 7.2 本仓库：每份文档各管什么

> **读法**：想知道「某条结论的依据在哪」查本表；想知道「怎么自己跑一遍」看 `复现` 列。
> ⚠️ 任何「已修 / 未修 / 已对齐」断言**以对应台账为准**，本表只负责指路。

**根目录**：`/Users/pr/Documents/java&golang/RAG`

#### 文档档（`docs/`）

| 位置 | 作用 | 什么时候看它 |
|---|---|---|
| [`docs/DRIFT/`](docs/DRIFT/_INDEX.md) | **语义漂移台账** —— 「RAG 与 Go 不一致」逐条留档。<br>结构 `<域>/<api\|rpc>.md`，`_cross/` 放跨模块档案 | 判定某模块是否已对齐、要查某条漂移的证据 |
| [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md) | **踩坑档** —— 「为什么会踩」：框架机制静默失效、绿测试锁死错误答案、基础设施误用 | 写测试 / 接新框架 / 「配了却没生效」时 |
| [`docs/Intentional_Deviations/`](docs/Intentional_Deviations/_INDEX.md) | **有意偏离档** —— 「明知与 Go 不同、**经签字批准**」的偏离。
  ⚠️ **是决策不是缺陷：审查时不重复质疑，但也不得「顺手修正回旧行为」。**
  三类性质不同的差异不可混谈：有意偏离 / 框架限制 / 表述差异 | 判断某条差异「要不要修」时 |
| [`docs/FileTree/`](docs/FileTree/README.MD) | **理论 Java 文件树**（15 份）—— ⚠️ **迁移期脚手架，不是长期规范**，phase 跑绿 review 后即可退休 | 规划某模块要落哪些文件 |
| [`docs/HUMAN_ONLY/`](docs/HUMAN_ONLY/simple.md) | 包级阅读指南（api / rpc 两份） | 第一次进一个包时 |
| [`docs/MappingTable/`](docs/MappingTable/README.MD) | 表名 / 字段名映射表 | 跨服务对表结构时 |

- ⚠️ **`docs/` 下两个 `common` 不是同一个组件**：`FileTree/common.md` = Go `service/common`（跨服务共享）；
  `FileTree/hot/common.md` = Go `service/hot/common`（**hot 专属，仅 1 个 `errmsg.go`**）。

#### 工具档（`tools/`）—— 全部是「机器会看」的东西

| 工具 | 作用 | 复现 |
|---|---|---|
| [`tools/semantic-probe/`](tools/semantic-probe/) | **双侧语义探针（首个落地实现）**：Go 半边跑真源镜像 struct + go-zero `core/mapping`，Java 半边跑**项目真实** mapper + DTO，比对 ACCEPTED/REJECTED。23 例全一致 `EXIT=0`，已变异验证 | `bash tools/semantic-probe/run-parity.sh`（cwd 无关） |
| [`tools/errmsg-probe/`](tools/errmsg-probe/) | **错误码契约逐字节对拍**（码值/文案/未知码回落）。Go 半边把真源 `errmsg.go` **同包编译**（能读未导出的 `codeMsg` 表），Java 半边跑 article-common 真实产物 | `bash tools/errmsg-probe/run-parity.sh` |
| [`tools/difftest/`](tools/difftest/) | **双端运行时对拍**：起 Go 与 RAG 两套栈逐项比；`matrix.py` 生成请求矩阵。对拍结论见 `docs/DRIFT/_cross/message-domain-wide.md` | `bash tools/difftest/run.sh` |
| `tools/difftest/check-ddl-static.py` | DDL 列级对照，并把索引/唯一约束/NOT NULL/DEFAULT 标成 **NOT CHECKED** | `python3 tools/difftest/check-ddl-static.py` |
| `tools/difftest/check-proto-static.sh` | proto 契约对照（字段号 / message 名 / RPC 签名） | `bash tools/difftest/check-proto-static.sh` |
| `tools/difftest/check-plaintext400-headers.sh` | 真 Tomcat 下 400 的 `Content-Type`/`nosniff`（**MockMvc 测不到这层**） | `bash tools/difftest/check-plaintext400-headers.sh` |
| `tools/difftest/check-flyway-ledger.py` | **Flyway 账本隔离**：按 (库, schema) 分组断言账本表名两两不同且非默认值。2026-10-23 由 `EXIT=1` 转 **`EXIT=0`**（配置层已修，运行时验证仍缺 ⇒ §6.3 问题 4） | `python3 tools/difftest/check-flyway-ledger.py` |
| `tools/drift-move.py` | **AGENTS.md ↔ docs/ 搬移工具**（`extract`/`verify`/`delete` 三段幂等）。台账 `docs/DRIFT/_MANIFEST.json` | `python3 tools/drift-move.py verify` |

> ⚠️ `tools/difftest/exp-*.sh` 是**被证伪方案的实证记录**（脚本头部已写明），**不要当修复用**。

#### 任务档

- `RAG/.agents/` —— 任务指南（**不得削弱本文件的规则**）
- `RAG/.agents/specs/<slug>.md` —— 每个 phase 一个 PR 的 spec

#### 模块内文档

- `service/users/{user/user,user/user-api,admin/admin-rpc,admin/admin-api}/HUMAN_ONLY.MD`（**4 份，2026-10-22 glob 实测校正**）
  - ⚠️ 原清单另列的 `users-common` / `message-rpc` / `message-api` 三份**不存在**，已移除（详见 §2）

### 7.3 已知的 Go 侧孤儿（勿照搬）

`service/user/rpc/internal/logic/` 是 7 个 `_test.go` 的孤儿目录，全仓无任何引用——搬迁时不要把它一起带过来。