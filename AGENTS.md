# AGENTS.md: RAG 项目政策

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件，**只放政策与索引**。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **2026-10-20 第三次真理之源切换**：真源是 **Go**；非 Spring Boot 的 Java 实现
> （`Sea-RideTheWind_java` 等）不在工作范围内。起因是 `service/users` Go ↔ RAG
> 对照发现 **11 处行为回归**，全部集中在「换成更地道的 Spring 写法」的位置。详见 §1.1。
> **2026-10-23 瘦身**：369 KB → 42 KB。逐条漂移与修复记录已全部移出到
> [`docs/DRIFT/`](docs/DRIFT/_INDEX.md)，踩坑与元教训在 [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md)。

## 〇、当前状态（2026-10-23）

**权威索引：[`docs/DRIFT/_INDEX.md`](docs/DRIFT/_INDEX.md)** —— 按域列出全部台账、搬移账目、证据边界。本节只给结论。

| 域 | 语义对照（Go ↔ RAG） | 台账 |
|---|---|---|
| `users` | ✅ 唯一做过完整行为审计的域；**DTO 字段边界层**已无漂移 | [`docs/DRIFT/users/`](docs/DRIFT/users/_shared.md) |
| `message` | ✅ api + rpc 双端验证完成 | [`docs/DRIFT/message/`](docs/DRIFT/message/api.md) |
| `like` | ✅ 已验证；事实投递链路**跨三个仓库** | [`docs/DRIFT/like/`](docs/DRIFT/like/rpc.md) |
| `common` | ✅ 审计完成 | [`docs/DRIFT/common/`](docs/DRIFT/common/audit.md) |
| `hot` | ⚠️ common + rpc 双侧对拍逐字节一致；**api 仍是 13 个空骨架** | [`docs/DRIFT/hot/`](docs/DRIFT/hot/api.md) |
| `article` | ⚠️ common 已对拍；**api/rpc 代码已落地但从未对照**（36 文件 / 4,275 行 / 7,029 行，2026-10-22 夜写入） | [`docs/DRIFT/article/`](docs/DRIFT/article/api.md) |
| 其余子模块 | ❌ **从未做过 Go ↔ RAG 对照** | 见 DRIFT 索引 |

**仍未关闭的三类**（明细台账在 DRIFT，**勿凭本表下结论**）：

1. **已签字的有意偏离** 6+ 条 —— 是决策不是缺陷，但两侧确实不一致 ⇒
   [`_cross/signed-deviations.md`](docs/DRIFT/_cross/signed-deviations.md)
2. **DDL 12 处列类型差异** 未处理；**索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED**。
   ⚠️ 其中 message 域时间列已首次做出可观察影响评估（DRIFT-MSG-V-007：JVM 非 UTC 时全部时间戳偏移一个时区差），
   **其余 10 处从未评估过影响**。
3. **从未验证过的子模块** ⇒ **每个模块接手的第一件事是「先让它真的启动一次」**，
   而不是先写业务单测（`message-rpc` 曾有 21 条全绿测试却一次没起过）。

⚠️ **测试数不是质量指标**：本项目 4 条漂移里有 **3 条都曾被绿测试锁死**；
真正锚定真源的是**双侧同时跑的探针**，不是测试条数。
⇒ [`docs/Pitfalls/_cross/testing-pitfalls.md`](docs/Pitfalls/_cross/testing-pitfalls.md)

⚠️ **并发构建期间不要采信全仓测试数字** —— 另一个会话并行跑 Maven 会重写共享 `target/` 与 `~/.m2`
的 SNAPSHOT jar；**单模块隔离跑才有效**。

## 一、项目身份与双轨策略

RAG 仓库路径：`/Users/pr/Documents/java&golang/RAG`。

本项目对**已废弃商用架构** `Sea-RideTheWind_golang` 做 Spring Boot 重构。RAG 不是教学练手项目，而是商用架构的现代化迁移产物——按工作项目标准交付。

| 项目 | 路径 | 角色 | 实现风格 |
|---|---|---|---|
| **Sea-RideTheWind_golang** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | **真理之源（唯一真源，2026-10-20 第三次切换）** —— 商用架构的原始实现，2026-10-18 起冻结，所有行为的唯一判定标准 | go-zero + GORM |
| **RAG（本仓库）** | `/Users/pr/Documents/java&golang/RAG` | **Spring Boot 重构产物** —— 按工作项目标准把 Go 行为用 Spring 生态表达；非 Spring Boot 的 Java 写法（如 JPA + flat `internal/{logic,model,server,svc}`）不在重构范围内 | **MyBatis-Plus + domain/mapper/service 分层 + 幂等 DDL 脚本 + Bootstrap 抽出** |

**RAG 的存在范围**：用 Spring 生态表达 Go 的行为。**不**对任何"非 Spring Boot 的 Java 实现"做对照——那些是另一种工作，不属于本项目的范围；也不接收 RAG 反向同步。

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
+ article / comment / favorite / follow / hot / like / points / security / task / common
（**连 Go ↔ RAG 对照都还没做过**，见 §0）。前者建议按 phase 补文档，后者暂按 Go 行为对齐、进入对应 phase 再补。

`HUMAN_ONLY.MD` 第 2 部分的"Java 理论文件树"是 aspirational design，从未被代码落地过。RAG 的本次重构就是把这个目标架构在 RAG 上落地一遍。

**2026-10-20 起取消「反向同步」**：真源（Go）不接受任何方向的写入或修正。此前允许 RAG 反向同步到 Java 镜像，等于让真源变成**双向可变**——这比单向漂移更危险，因为任何一侧都无法再被信任为判定标准。RAG 产出的是 Spring 表达经验（值得沉淀进 `HUMAN_ONLY.MD`），不是行为定义。

## 三、语义对齐纪律（Spring idiomatization 的唯一防线）

RAG 与 Go 的差异只允许出现在**表达层**。本节规定如何确保差异不越界。

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

**已实证的两组反向例子（`like-api`，见 §0 对应小节）：**

| 你会看到 | 真实情况 | 出处 |
|---|---|---|
| Go yaml 里**没有** `Timeout` | 实际 **3000ms**，struct tag `json:",default=3000"` 生效 ⇒ 3s 超时 503 真实存在 | `go-zero@v1.10.2/rest/config.go:55` |
| Go tag 是 `json:"target_ids"`，看着「没有 optional」 | 实际**解析失败**（`type mismatch`），不是「填空值放行」 | `core/mapping/unmarshaler.go:944-950` → `processFieldNotFromString` → `processFieldPrimitive` |

⇒ **强制要求**：
1. 判断某个 Go 默认行为是否生效，**必须读到消费该 tag 的那段控制流**，
   只贴 struct 定义行不算证据；
2. 判断某个 Java 框架能力是否生效，**必须有运行证据**（真实管线 / 真容器），
   「pom 里有依赖」「类上有注解」都不算（AGENTS.md §3.3 原有纪律）；
3. 写 `文件:行号` 证据时，行号要指向**消费点**而不是**声明点**。

**3.3.2（2026-10-22 新增）以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，本身是高危项**

`like-api` 的 DRIFT-LIKEAPI-002 里，两条绿测试的 `@DisplayName` 直接写着
「Go: Slice 永不计必填」，而这个 Go 结论后来被活体探针**推翻**。
⇒ 危害不是「测试没抓到 bug」，而是**修复时会先撞上这两条红测试**，
且测试名会让人误判「修复」才是漂移方向。
⇒ **硬要求**：任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，
否则**不得**用该措辞，改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。

#### 3.3.3 原有纪律（2026-10-20 起）

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler）全部是运行期动态装配，出错时**不报错**。

已实证的四类静默失效：

| 机制 | 静默失效形态 |
|---|---|
| `@Transactional` | self-invocation 不经代理 → 注解形同虚设，且**无任何异常** |
| `@ResponseBodyAdvice` | `HttpEntityMethodProcessor` 继承 `writeWithMessageConverters` 并查询 advice chain → 连 `@ExceptionHandler` 返回的 `ResponseEntity` 也会被二次包裹 |
| `@TableField(typeHandler=…)` | 只对 MP 自己生成的 SQL 生效；`LambdaUpdateWrapper.set()` 的参数走 `ew.paramNameValuePairs`，**丢掉字段类型信息** |
| **命令行注入的配置属性** | 前缀写错 ⇒ 落到**不存在的** `@ConfigurationProperties` ⇒ Spring Boot 对未知命令行属性**静默接受** |

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

⚠️ **「只跑 RAG 的探针」同样不算数**：它证明的只是「代码写成了这样」，
证明不了「Go 是这样」。§docs/DRIFT/message/rpc.md 的变异测试是同一问题的另一半 ——
变异测试能证明测试**不是假绿**，但**不能证明测试的期望值是对的**。两者缺一不可。

**规则（2026-10-22 晚补 · 由 `hot-rpc` 的 Spring Boot 4 模块化迁移推出）**：
**「注解存在」≠「机制生效」，且 Boot 4 让这一层比以往更难发现。**

   （不是「listener 方法存在」），且须覆盖「缺少 `spring-boot-kafka` 会怎样」；
2. 引入任何 `spring-*-starter` 类框架时，先确认该能力在 **Boot 4 下仍在 `spring-boot-autoconfigure` 里**，
   已被拆走的必须显式补对应模块（版本由 BOM 管理，不写 `<version>`）；
3. **不得**把「编译通过 / 上下文能启动」当作「注解已生效」的证据。

⚠️ 同款缺依赖已确认存在于 `service/like/like-rpc/pom.xml:120`（只引 `spring-kafka`）——**尚未修**。
明细见 §docs/DRIFT/hot/rpc.md。

## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper 接口 `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService`（admin-rpc 是 1 个 `AdminDomainService`） | HUMAN_ONLY.MD 描述的是单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口（user-rpc：2 个；admin-rpc：3 个） | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + users 域 DDL 脚本。~~Flyway `V*__create_*.sql`~~ → **2026-10-21 改为幂等重放，见 §docs/DRIFT/users/_shared.md DRIFT-NEW-015** | HUMAN_ONLY.MD 列出这两个文件。Bootstrap 保留；**迁移机制被推翻**（有状态账本与「双 rpc 共用一个 schema」不兼容） |

> **✅ Flyway 条目已于 2026-10-21 处置完毕**：Go 用 `AutoMigrate`（无状态），Flyway 是**有状态账本**，双 rpc 共用同一 schema 时版本号必然冲突（已实证两轮）。最终改成**幂等重放 DDL**（`SchemaInitializer`），无账本无版本号 —— 与 Go 的 AutoMigrate 同构。完整经过与实证见 §docs/DRIFT/users/_shared.md DRIFT-NEW-015。

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

**✅ 问题 1：建表机制 —— 2026-10-21 用户拍板「直接把 Flyway 丢掉」。**
（原为待定；2026-10-20 曾定案为「保留 Flyway + 全局版本号」，**该结论已被推翻**）

| 表 | 谁建 |
|---|---|
| `users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite` | user-rpc 与 admin-rpc 都建（幂等，谁先启动都一样） |
| message 三张表 | message-rpc **仍用 Flyway**（本次未动 ⇒ 引出问题 4） |

执行要点：4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`（编号前缀只保证执行顺序，**不表示版本**）；
`SchemaInitializer` 用 `ScriptUtils` 原样重放；两个 rpc 的 Flyway 三件套**全部从 pom 移除**
（只删 `flyway-core` 不够，`spring-boot-flyway` 还在 classpath 上就会自动 migrate）。
⚠️ **本问题的实际范围比原记录更宽** —— 病根在另外三个模块原封不动成立着，即**问题 4**。
⇒ 详述与被否决的三个方向：[`docs/DRIFT/_cross/decisions.md`](docs/DRIFT/_cross/decisions.md)

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷。**
实测 Go 侧 `admin/rpc` **零出向 gRPC 依赖**，7 个管理 RPC 全是 `internal/model/admin.go` 的直接 SQL，
Go 自己就有两份同样的表定义。⚠️ 原记录前提有误：Go 的 gRPC 边界是 **api→rpc**，「读身份」与「管用户数据」不是同一个轴。
⇒ 详述与被推翻的反对理由：[`docs/DRIFT/_cross/decisions.md`](docs/DRIFT/_cross/decisions.md)

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包。**（2026-10-21 已实施）
Go 里**不存在**「全局自动注册」；RAG 把 `@Component` 当自动注册是一次**无出处 idiomatization**。
**连带结果**：不再有任何模块扫 `com.rag.common` ⇒ 曾提的 `spring/web/`+`spring/grpc/` 拆包**动机自我消解，已撤回**。
⚠️ `message-rpc` 归一到显式 `@Import` 仍**未做**（现在能跑属撞运气）。
⇒ 详述：[`docs/DRIFT/_cross/decisions.md`](docs/DRIFT/_cross/decisions.md)

**📌 问题 4：Flyway 账本撞号 —— 问题 1 的结论只应用到 1/4 的模块上。（2026-10-23 新增）**

问题 1 丢掉 Flyway 的**唯一根因**：`flyway_schema_history` 是 **schema 级**账本，一个 schema 只能有一本账。
⚠️ 该根因在 `message-rpc` / `like-rpc` / `article-rpc` **原封不动成立**——三者同库 `first_db`、
同 schema `public`、同账本表（**发现时** `spring.flyway.table` 全仓零命中），且 message 与 like 的 **V1–V3 直接撞号**。
任一方先启动，另一方的表就永远建不出来。

**定案**：每模块独立账本表名 `spring.flyway.table: flyway_schema_history_<模块>` ——
不动任何迁移文件名，各模块从 V1 各自重来也互不干扰，**直接消除病根**。
（否决「排全局唯一号段」：那是 10-20 已被 10-21 推翻的旧结论，且不解决根因。）

> **实施状态（2026-10-23 实测核对，本次实施后重写）**：
> - ✅ **配置层已修**：`message-rpc` → `flyway_schema_history_message`、`like-rpc` → `flyway_schema_history_like`，
>   两个 `application.yml` 各加一行 `spring.flyway.table`，**未改任何迁移文件名**。
> - ⚠️ **`article-rpc` 按用户指示未动**，保持默认表名 `flyway_schema_history`。
>   这**不影响病根消除**：默认值此时是 article 独占，message/like 已各自让开 ⇒ 三本账互不共享。
>   代价是 article 的账本表名仍是默认值，**将来新增第 4 个模块若忘了加 `table` 就会直接撞上 article**。
> - ✅ **守门脚本转绿**：`tools/difftest/check-flyway-ledger.py` 由 `EXIT=1` → **`EXIT=0`**
>   （2026-10-23 实跑；同组三本账表名两两不同）。
> - ❌ **运行时验证仍未做 ⇒ 按 §3.3 记 `UNKNOWN`，不得写成「已修复」**：
>   静态脚本只证明**声明层**不撞，不证明 Flyway 运行时真的按 `spring.flyway.table` 建账本。
>   真正的证明需要**真 PG 上 message-rpc 与 like-rpc 对启动**（article 不参与，缺 `spring-boot-flyway` 的 Boot 4 拆模块坑另计）。
> - ⚠️ **文档缺陷（2026-10-23 核出）**：`check-flyway-ledger.py` 的 docstring 与
>   `docs/DRIFT/_cross/open-items.md` 都声称运行时那一条「由各模块的 `FlywayLedgerIsolationTest` 负责」——
>   **该测试类全仓不存在**（`grep -r FlywayLedgerIsolationTest` 只命中这三处文档自身）。
>   ⇒ 又一条「文件已写」型未核实断言（§2 同款形态）；真要闭环，要么真写这个测试，要么改掉这句声明。
>
> 症状 / 完整证据 / 被证伪的绕法 / 效力边界 ⇒ **`docs/Pitfalls/_cross/flyway-ledger-collision.md`**。

### 6.4 其他子模块

| 阶段（module） | 状态 |
|---|---|
| `service/message` | **`message-rpc` 已完成审计 + 修复（2026-10-22）**：首轮 3 项 P0 已全部关闭，补测试时又新挖出 3 项「模块根本起不来」级缺陷（Bean 名撞车 / Mapper 未注册 / Flyway 未加载）⇒ 见 §docs/DRIFT/message/rpc.md。**`message-api` 也已完成首轮实现 + 语义验证 + 修复（2026-10-22）**：验证结论 `SEMANTICALLY NOT EQUIVALENT`，3 处请求解析层漂移（-001/-002/-003）全部已关，防回退网 = 41 条 MVC 管线用例 + `tools/semantic-probe` 23 例双侧对拍 ⇒ 见 §0 |
| Phase 5（全量 + 端到端） | 未启动；需真实 PG + Redis 集成测试补上（当前测试全部 mock 数据层） |

## 七、引用清单

### 7.1 真理之源（判定行为）

Go 真源仓库：`/Users/pr/Documents/GitHub/Sea-RideTheWind_golang`（**已冻结**，不接受写入）。
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
| [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md) | **踩坑档** —— 「为什么会踩」：框架机制静默失效、绿测试锁死错误答案、基础设施误用。<br>与 DRIFT 的分工：DRIFT 记**差异**，Pitfalls 记**教训** | 写测试 / 接新框架 / 遇到「配了却没生效」时 |
| [`docs/FileTree/`](docs/FileTree/README.MD) | **理论 Java 文件树**（15 份）—— 按 §3.1/§3.2 推导的目标结构。<br>⚠️ **迁移期脚手架，不是长期规范**；某 phase 跑绿并 review 后即可退休 | 规划某个模块要落哪些文件 |
| [`docs/HUMAN_ONLY/`](docs/HUMAN_ONLY/simple.md) | 包级阅读指南（api / rpc 两份） | 第一次进一个包时 |
| [`docs/MappingTable/`](docs/MappingTable/README.MD) | 表名 / 字段名映射表 | 跨服务对表结构时 |

- ⚠️ **`docs/` 下两个 `common` 不是同一个组件**：`FileTree/common.md` = Go `service/common`（跨服务共享）；
  `FileTree/hot/common.md` = Go `service/hot/common`（**hot 专属，仅 1 个 `errmsg.go`**）。

#### 工具档（`tools/`）—— 全部是「机器会看」的东西

| 工具 | 作用 | 复现 |
|---|---|---|
| [`tools/semantic-probe/`](tools/semantic-probe/) | **双侧语义探针**（首个落地实现）：Go 半边跑真源镜像 struct + go-zero `core/mapping`，Java 半边跑**项目真实** Jackson3 + 项目 DTO，逐用例比对 ACCEPTED/REJECTED。现状 23 例全一致 `EXIT=0`，已做变异验证 | `bash tools/semantic-probe/run-parity.sh`（cwd 无关） |
| [`tools/errmsg-probe/`](tools/errmsg-probe/) | **错误码契约逐字节对拍**（码值/文案/未知码回落）。Go 半边把真源 `errmsg.go` 逐字节复制进临时模块**同包编译**（因此能读未导出的 `codeMsg` 表），Java 半边跑 article-common 的真实编译产物 | `bash tools/errmsg-probe/run-parity.sh`（`GO_REPO=…` 可覆盖路径） |
| [`tools/difftest/`](tools/difftest/) | **双端运行时对拍**：`run.sh` 起 Go 与 RAG 两套栈逐项比；`matrix.py` 生成请求矩阵；`initdb.sql` 建库。⚠️ 完整对拍结论见 `docs/DRIFT/_cross/message-domain-wide.md` | `bash tools/difftest/run.sh` |
| `tools/difftest/check-ddl-static.py` | DDL 列级对照（`information_schema.columns`），并把索引/唯一约束/NOT NULL/DEFAULT 标成 **NOT CHECKED** | `python3 tools/difftest/check-ddl-static.py` |
| `tools/difftest/check-proto-static.sh` | proto 契约对照（字段号 / message 名 / RPC 签名） | `bash tools/difftest/check-proto-static.sh` |
| `tools/difftest/check-plaintext400-headers.sh` | 真 Tomcat 下 400 响应的 `Content-Type` / `nosniff`（MockMvc 测不到这一层） | `bash tools/difftest/check-plaintext400-headers.sh` |
| `tools/difftest/check-flyway-ledger.py` | **Flyway 账本隔离**：按 (库, schema) 分组，断言账本表名两两不同且非默认值。⚠️ **当前为红 `EXIT=1`** —— 决策已定、修复未实施，见 §6.3 问题 4 | `python3 tools/difftest/check-flyway-ledger.py` |
| `tools/drift-move.py` | **AGENTS.md ↔ docs/ 的搬移工具**（`extract` / `verify` / `delete` 三段幂等）。台账 `docs/DRIFT/_MANIFEST.json` 记录 39 段源行区间 | `python3 tools/drift-move.py verify` |

> ⚠️ `tools/difftest/exp-*.sh` 是**被证伪方案的实证记录**（脚本头部都写明了），
> **不要当修复用**：`exp-drift015-flyway-flags.sh`（Flyway 开关绕法）、`exp-b1-timeout-cancel.sh` 等。

#### 任务档

- `RAG/.agents/` —— 任务指南（**不得削弱本文件的规则**）
- `RAG/.agents/specs/<slug>.md` —— 每个 phase 一个 PR 的 spec

#### 模块内文档

- `service/users/{user/user,user/user-api,admin/admin-rpc,admin/admin-api}/HUMAN_ONLY.MD`（**4 份，2026-10-22 glob 实测校正**）
  - ⚠️ 原清单另列的 `users-common` / `message-rpc` / `message-api` 三份**不存在**，已移除（详见 §2）

### 7.3 已知的 Go 侧孤儿（勿照搬）

`service/user/rpc/internal/logic/` 是 7 个 `_test.go` 的孤儿目录，全仓无任何引用——搬迁时不要把它一起带过来。