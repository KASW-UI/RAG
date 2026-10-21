# AGENTS.md: RAG 项目政策与当前重构状态

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **2026-10-18 重写**：原文件是从 vLLM 模板继承的、含大量与本项目无关的内容（GGUF、ModelRegistry、FusedChain 等）。本次重写只保留 RAG 实际需要的政策与本次重构的当前状态。
> **2026-10-20 第三次真理之源切换**：真源退回 **Go**；非 Spring Boot 的 Java 实现（`Sea-RideTheWind_java` 等）从策略中移除，不在 RAG 工作范围内；并新增 §3 语义对齐纪律。起因是 `service/users` Go ↔ RAG 对照发现 **11 处行为回归**，全部集中在「换成更地道的 Spring 写法」的位置。详见 §1.1。

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

当前已落 HUMAN_ONLY.MD 的子模块：

- **service/users**：user-rpc / user-api / admin-rpc / admin-api / users-common 共 5 份
- **service/message**：message-rpc / message-api 共 2 份

未被 HUMAN_ONLY.MD 覆盖的子模块（article / comment / favorite / follow / hot / like / points / security / task / common）暂按 Go 行为对齐即可，**等进入对应 phase 再补 HUMAN_ONLY.MD**。

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

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler）全部是运行期动态装配，出错时**不报错**。

已实证的四类静默失效：

| 机制 | 静默失效形态 |
|---|---|
| `@Transactional` | self-invocation 不经代理 → 注解形同虚设，且**无任何异常** |
| `@ResponseBodyAdvice` | `HttpEntityMethodProcessor` 继承 `writeWithMessageConverters` 并查询 advice chain → 连 `@ExceptionHandler` 返回的 `ResponseEntity` 也会被二次包裹 |
| `@TableField(typeHandler=…)` | 只对 MP 自己生成的 SQL 生效；`LambdaUpdateWrapper.set()` 的参数走 `ew.paramNameValuePairs`，**丢掉字段类型信息** |
| **命令行注入的配置属性** | 前缀写错 ⇒ 落到**不存在的** `@ConfigurationProperties` ⇒ Spring Boot 对未知命令行属性**静默接受** |

**第四类的完整经过（2026-10-21，B-1 实验）**：为验证 `setQueryTimeout` 能否缓解 B-1，
`run.sh` 注入 `--mybatis.configuration.default-statement-timeout=2`。**前缀错了** ——
MyBatis-Plus 的前缀是 `mybatis-plus`（`MybatisPlusProperties`），全仓**没有**任何
`@ConfigurationProperties(prefix="mybatis")`。结果：启动无 warn、无报错、jar 正常起、
**PG 阻塞查询照旧存活 4.5s，效果恒为零**。改对前缀后同一实验立刻出效果。

> 绑定链路本身是通的、已逐段核实，**不要**误判成「MP 不支持该属性」：
> `--mybatis-plus.configuration.default-statement-timeout=2`
> → `MybatisPlusProperties$CoreConfiguration.defaultStatementTimeout`（`Integer`，有 getter+setter）
> → `MybatisPlusAutoConfiguration.applyConfiguration()` 调 `CoreConfiguration.applyTo(MybatisConfiguration)` 搬运
> → `MybatisConfiguration.defaultStatementTimeout`
> → MyBatis `BaseStatementHandler` 调 `statement.setQueryTimeout(int)`
> → pgjdbc `StatementCancelTimerTask` → 向 PG 发 **CancelRequest**。
> 另外 `user-rpc/application.yaml` 里 `mybatis-plus.configuration.map-underscore-to-camel-case`
> 一直生效（否则实体映射全崩），本身就是「`configuration` 子块绑定正常」的现成旁证。

**规则**：凡引入新的框架机制，必须配一条**能真正触发该机制**的测试。
纯 Mockito mock 数据层的单测**不算**——它不会执行 SQL，也不会装配 Spring 上下文，天然测不到本节三类问题。

**规则（2026-10-21 补）**：判断「一个注入/开关有没有真生效」**不能看启动日志**——
本节第四类里，注入点的 echo 照打、jar 照起、日志零告警，效果却是零。
唯一可靠的判据是**运行时证据**（本例是 PG 侧是否出现 `57014 query_canceled`）。
凡是「注入后实测无变化」的结论，一律先怀疑注入本身没到位，再怀疑机制无效。

**规则（2026-10-21 再补 · 由 H2 假绿事件推出）**：**断言「Bean 存在」不等于断言「机制生效」。**

`ObservabilityWiringTest#grpcInterceptorBeanExists` 长期为绿，因为它断言的是
`ctx.getBean(ServerInterceptor.class)` 非空 —— 而真实缺陷是
**grpc-spring-boot-starter 3.1.0 根本不会把用户提供的 `ServerInterceptor` Bean 挂到 server 上**
（`javap` 证据见 §6.4 勘误小节）。Bean 在容器里、starter 不用它，两件事都对，机制照样是死的。

⇒ 凡依赖**框架自动注册**的机制（starter 自动装配、`AutoConfiguration.imports`、拦截器链），
测试必须断言二者之一：
- **运行时的可观测后果**（最强）：本例即「客户端是否 ≈2s 返回 1015」，由
  `tools/difftest/exp-b1-timeout-cancel.sh` 端到端覆盖；
- **对象确实进入了框架的接管点**（次强）：如 `verify(serverBuilder).intercept(interceptor)`，
  由 `GrpcInterceptorWiringTest` 锁住。

锁死用例：`user-rpc` 的 `GrpcInterceptorWiringTest`（2 条）。

### 3.4 已知偏离（有意为之，需留档）

以下偏离经过确认并记录，审查时不必重复质疑，但也不得"顺手修正回旧行为"：

| 偏离 | 内容 | 理由 |
|---|---|---|
| HTTP path | `/user/v1/*`、`/admin/v1/*`（Go 为 `/usercenter/v1/user/*`、`/admincenter/v1/admin/*`） | HUMAN_ONLY.MD 决策；路由不属于行为语义，但属 wire 契约变更 |
| HTTP method | 统一 POST（Go 为 GET/DELETE 混合） | 同上 |
| 表名 | `users_user` / `admin_admin`（Go 为 GORM 默认名） | HUMAN_ONLY.MD 决策 |

#### 3.4.2 `service/users` 语义审计后的两条新增留档（2026-10-21）

Go → Spring Boot 语义一致性审计 `service/users/user`（9 RPC + 9 HTTP 端点 + `users-common`）产出
**3 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 6 项 UNRESOLVED**，已修 11 处 + 新增 22 条回归测试。
以下两条**是修复后仍然存在的有意偏离**：

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-1** | **鉴权失败的响应体形状** | Go 侧两处 401 的 body **互不相同**：`rest.WithJwt` → `authhandler.go:106 unauthorized()` 写 **HTTP 401 + 空 body**；`CheckBlacklistMiddleware` → `checkblacklistmiddleware.go:38` 写 **HTTP 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（无 `data`）。RAG 两侧统一成 **HTTP 401 + 完整 `{code,msg,data}`** | **用户 2026-10-21 拍板**。HTTP **状态码 401 已与 Go 对齐**（这是本次修掉的 SEMANTIC MISMATCH）；body 统一属于表达层收敛——Go 自身两处不一致，RAG 不复制这种不一致。业务码仍是 `ERROR_TOKEN_RUNTIME(1006)` | 若要求逐字复刻 Go 的两种 body，则需在拦截器里区分「JWT 验签失败」与「黑名单命中」两条路径分别写 body |
| **N-2** | **Go 真源的中文错误消息乱码** | Go `user/rpc/internal/logic/` 下 `uploadavatarlogic.go` / `selectavatarlogic.go` / `getavatarhistorylogic.go` 共 **9 处** `status.Error(...)` 的中文 message 是 **GBK 双重编码乱码**（`鐢ㄦ埛涓嶅瓨鍦?`，尾字节已丢、还原不出「用户不存在」）；而同模块 `loginlogic.go` / `registerlogic.go` / `getuserlogic.go` / `updateuserlogic.go` / `deleteuserlogic.go` 是正常 UTF-8。RAG 写的是正确中文 | 取证：`"鐢熸垚澶辫触".encode('gbk').decode('utf-8') == "生成失败"`。Go 已冻结（§2 不接受写入），无法在真源侧修；照抄乱码会让日志与 gRPC status message 对人类不可读。**用户 2026-10-21 拍板：RAG 保留正确中文**，沿用 §3.4.1 A-2（`Logger.isFiltered`）的既有先例。影响面仅 gRPC status message——Go api 层只按 `status.Code` 分支、不透传 message，**不进 HTTP body** | 若日后在 Go 侧修正编码，需同步 RAG 的 9 条 message |

> **这两条的共同教训**：N-1 的修复前状态是「HTTP 200 + 业务码 1006」，而支撑它的注释写着
> 「401 只由鉴权层的 Servlet Filter 写出，不经过本处理器」——**本工程根本没有这样的 Filter**，
> 鉴权是 `HandlerInterceptor` 抛异常、必然经过该处理器。
> 这是一条**引用了不存在的机制**来论证 idiomatization 的典型，
> 与 §3.2「无出处 idiomatization」同类，但更隐蔽：它有出处、只是出处是假的。
> 新的防线是 `AuthenticationException`：**层次由类型承载，不靠业务码推断**
> （1006 在鉴权层与 Service 层都会出现，按码分流必然搅在一起）。
> 锁定用例：`user-api` 的 `AuthenticationHttpStatusTest`（3 条，含「同码不同型 → 不同状态码」）。

> **N-1 的二次坍塌（2026-10-21 补）**：§6.1 第 10 项「HTTP 状态码语义变更」修了
> 「`GlobalExceptionHandler` 拿不到 401」这一层。但还有一层被跳过：
> **`HttpMiddleware$ObservabilityResponseWriter.setStatus` 的 status 没 propagate 到
> `MockHttpServletResponse`**——下游 `outputMessage.flush()` 触发 `flushBuffer()` 把底层
> `setCommitted(true)`，再走 `HttpMiddleware.flushTo` 的 `dst.setStatus(401)` 就被守卫吞掉。
> **`AuthenticationHttpStatusTest` 用的是 `standaloneSetup`**，不挂 `HttpMiddleware` filter；
> 这就是它能在 user-api 写完时全绿、而 admin-api 实测却出 HTTP 200 的根因——两层一起错时
> 单独那一层全绿就够骗过 CI。直到 `AdminApiMvcPipelineTest`（真 MVC + 真 filter）才把它们联起来。
> 锁定用例（覆盖本批发现的 DRIFT-FRAMEWORK-001）：`admin-api` 的 `AdminApiMvcPipelineTest`
> 4 条 401 路径用例（`blacklistCheckedBeforeSignatureVerification` /
> `invalidSignatureAfterBlacklistMiss` / `missingHeaderSkipsBlacklistLookup` / `emptyBearerTokenRejected`）。
> 修复方法：`HttpMiddleware$ObservabilityResponseWriter.setStatus` / `sendError`
> 立即 propagate 到 underlying（用 `(HttpServletResponse) this.original` 绕过
> javac 对 inner class `getResponse()` 的回退到 `ServletResponse` 的怪题），
> 见 §6.2.2 DRIFT-FRAMEWORK-001 行。

#### 3.4.3 `service/users/admin` 语义审计后的三条新增留档（2026-10-21，issue #71186443）

Go → Spring Boot 语义一致性审计 `service/users/admin`（13 RPC + 13 HTTP 端点）产出
**10 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 3 项 UNRESOLVED**。
**9 项 MISMATCH + 2 项 wire 契约 + 1 项共享底层 bug（DRIFT-FRAMEWORK-001）已修**，
新增 **47 条回归测试**
（`admin-rpc` 的 `AdminSemanticDriftRegressionTest` 12 条 + `admin-api` 的
`AdminSemanticDriftApiRegressionTest` 24 条 + `AdminApiMvcPipelineTest` 11 条 ——
**首条把真实 MVC 管线 + 真实 Redis 容器 + 真实拦截器 advice 链纳入回归网**，
同时锁住 DRIFT-FRAMEWORK-001 / DRIFT-009 / N-1）。
以下四条是本节记录过的条目。**注意：其中 N-3 与 N-6 已于 2026-10-21（DRIFT-NEW-016 审计期间）
被实测证伪并降级** —— 它们描述的 RAG 行为从未存在过，RAG 本来就与 Go 一致。
**当前真正成立的「有意偏离」只有 N-4、N-5 两条**。
保留 N-3 / N-6 两行是为了让后人看到「签字 + 防回退测试都齐了，结论仍可能是错的」。

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-3** | ~~**`uid=0` 被错误归类为「业务不存在」**~~ **⚠️ 已证伪，本条不是偏离（2026-10-21 DRIFT-016 审计期间）** | 留档曾写「RAG 在 `AdminApiSupport.parseUid` **提前拦截 uid=0** → 1017」。**代码里没有这个拦截**：`parseUid` 只拒 null / 空串 / 非数字 / 溢出，全仓 grep `uid == 0` 在 admin-api 侧**零命中**。实测 RAG 真实行为：`uid=0` → **透传到 rpc** → 查无此人 → `1003`，**与 Go 逐字相同** | **本条降级为「留档与代码不符，已修正」**，不是有意偏离。Go 侧三态（探针实测）：不传 uid → `400 field "uid" is not set`；`uid=abc` → `400 strconv.ParseInt`；`uid=0` → `200 {code:1003}`。**RAG 在这三条上本来就与 Go 一致** ⇒ 无需签字、无需改动行为 | 已闭合。防回退测试 `AdminApiMvcPipelineTest#uidZeroIsPassedThroughAndMatchesGo`（断言 uid=0 **确实被透传**，即证明「没有 1017 拦截」） |
| **N-4** | **`operator_id` 不恒为 0** | Go 的 `updateuser` / `banuser` / `unbanuser` / `delete` / `resetuserpassword` 读 `ctx["uid"]`，而 `rest.WithJwt` 写入 ctx 的 key 是 JWT claim 名 **`userId`** ⇒ 永远取不到 ⇒ `OperatorId` 恒为 **0**。RAG 五个端点都传真实 uid | **用户 2026-10-21 拍板：保留真实 uid**。该字段**不参与任何业务判定**，只写进 OTel span 的 `audit.operator_id` 属性；恒 0 会让审计日志失去操作人信息。影响面 = 可观测性数据，不是业务行为 | 若要求审计 span 与 Go 逐字一致，需在这 5 个 Service 里刻意丢弃 token 中的 uid |
| **N-5** | **JWT HMAC 密钥长度** | Go 用 15 字节原始 secret 参与 HS256；RAG 用 `JwtUtil.padToHmacKeySize` zero-pad 到 32 字节 | jjwt 0.12 强制 key ≥256 bit。RAG 内部签发与验签共用同一份 padded key、自洽；代价是 **Go 签的 token 在 RAG 验不过，反之亦然**（两套部署各自闭环）。与 `user-api` 同款 | 若需跨部署互认 token，必须统一 key 长度——要么改 Go 侧 secret 到 ≥32 字节，要么 RAG 放弃 padding（jjwt 会抛 `WeakKeyException`） |
| **N-6** | ~~**uid 格式非法时 body 形状 ≠ envelope**~~ **⚠️ 已证伪，本条不是偏离（2026-10-21 DRIFT-016 审计期间）** | 留档曾写「`uid=abc` 经过 `parseUid` 拦截 → `200 + 1017` envelope」。**代码里到不了 `parseUid`**：`GetUserReq.uid` 类型是 `Long`，admin-api **无自定义 ObjectMapper**（grep 零命中），Jackson 在反序列化阶段就抛 `InvalidFormatException` → Spring 包成 `HttpMessageNotReadableException` → `handleUnreadableBody` → `400 + text/plain`。Go 侧 `uid=abc` **同样**是 `400 + text/plain` | **本条降级为「留档与代码不符，已修正」**，不是有意偏离。**状态码本就一致**，只有错误**文本**来自不同框架（Jackson vs go-zero）—— 与 §3.4.4 DRIFT-006 已确立的「形状对齐、文本不对齐」边界同款 | 已闭合。防回退测试 `AdminApiMvcPipelineTest#nonNumericUidIsRejectedByJacksonNotByRequiredCheck`（同时断言错误文本**不是**我们自己的 `is not set`，以证明来源是 Jackson） |

##### ⚠️ N-3 勘误（2026-10-21，commit 690ba4c 之后）

留档初版（commit `9b2303c`）写「Go admin 侧的 uid 走 `form:"uid,string"` 绑定，**key 缺失时静默保留零值**且不报错 ⇒ `uid=0` 进 rpc ⇒ 查无此人 ⇒ 返回 1003」。**前半段理由已被实测推翻**：

- go-zero `httpx.Parse` 对 `form:"uid,string"` 的 required 缺失行为是**直接 400**（`core/mapping/unmarshaler.go` 的 `parseOption` 把所有非 `optional` 字段标 `Required=true`，缺失走 `newInitError` 报 `field %q is not set`），不是「静默保留零值」。
- 实测覆盖：`admin.getuser` 三态（完全不传 → `400 field "uid" is not set` / `uid=abc` → `400 strconv.ParseInt` / `uid=0` → `200 {code:1003}`），取证脚本 `tools/difftest/probe-go-required-fields.py`（page_size=100 三组各 3 次，可复现）。

**结论本身不变**（保留 RAG 的 1017），但理由改为「Go 把 `uid=0` 当合法查询，业务不存在语义掩盖参数拼装 bug」。边界也明确收窄到 `uid=0` 这一路径 —— 缺失/非数字/空串的覆盖在 N-6 + DRIFT-016（实施后两边统一 400+text/plain）。**N-3 与 N-6 不再是「同源」**，N-3 单独覆盖「显式 0 被业务路径吃掉」这一边界。

> **这四条的共同教训**：与 N-1/N-2 不同，N-3/N-4/N-5/N-6 都被归入「Go 的行为本身有缺陷」
> 而非「Go 表达得更地道」。真源冻结（§2）意味着 Go 的缺陷也会被冻结，于是产生了
> 「按政策应当对齐、但对齐会让 RAG 变差」的真实张力。
> **新的处置纪律**：这类项不得由单个 agent 自行判断，必须显式列出、用户签字、写进本节，
> 并用**防回退测试**锁住现状 —— 否则下一个「语义一致性审计」agent 会把它当成新发现的漂移再「修」一遍。
>
> **⚠️ 2026-10-21 补一条更硬的纪律（DRIFT-NEW-016 审计的产物）**：
> 上面那套「签字 + 防回退测试」**并不足以保证结论正确**。
> **N-3 与 N-6 两条都经过了签字、都有防回退测试、javadoc 出处写得很完整，
> 而它们描述的行为从未存在过。** 见 §6.2.5「DRIFT-NEW-016 的处置」。
> 真正的防线是：**任何留档声称「RAG 的行为是 X」时，都必须有一条断言 X 的运行测试；
> 若该测试不存在或从未跑过，它只是推测，不得当作事实引用。**
> 区分「代码里写了什么」与「运行时真的发生什么」——
> 前者 grep 得到，后者只有真进程能证明。
>
> **同一条纪律的反面也必须台账化**：**证据缺口本身要写下来**。
> 否则下一个 agent 跑完 `tools/difftest` 看到「39/39 全绿」就以为已经验证完了 ——
> 而 39 步里有 **13 步只比状态码不比 body**，且完全没有**超时 / 并发 / gRPC 层**的对拍。
> 完整台账见 **§6.2.6「`service/users` 的证据边界台账」**（E-1 ~ E-6）。

#### 3.4.4 `service/users` 基础组件审计后的新增留档（2026-10-21，issue #71186893）

对 `service/users` 9 个**基础组件**（JWT 签发 / 错误码表 / 密码哈希 / 身份断言 / 鉴权链 / 响应信封 / 雪花 ID / 事务持久化 / RPC 客户端）做 Go → Spring Boot 语义一致性验证（只读审查），
结论 **SEMANTICALLY NOT EQUIVALENT**：1 项方向性错误 + 1 项**安全漏洞** + 3 项可观察差异 + 1 项死代码。

##### DRIFT-018 的登记（2026-10-21 补记 · 代码里早已存在、AGENTS.md 零命中）

`UserDomainService` 的 `checkDeadline()` 上方注释写着「**DRIFT-018 修复**」，
`RpcInterceptor.handle()` 里也写着同一条注释，但 **AGENTS.md 全文 grep `DRIFT-018` 零命中**
—— 一个被代码引用了、却从没有留档的编号。本条补上它的登记。

| 项 | 内容 |
|---|---|
| **做了什么** | 两条路径各绑一次：`RpcInterceptor.handle()`（主动调用的路径）在提交 worker 时 `DeadlineState.bindToCurrent(deadlineEpochMs)`；gRPC server 路径在 `newUnaryServerInterceptor` 的 `onHalfClose()` 里绑定（那里才是真正跑 handler 的线程）。业务侧在**发起** DB / bcrypt / snowflake / 下游调用**之前**调 `checkDeadline()`，已过期则抛 `Status.DEADLINE_EXCEEDED` |
| **Go 对应** | `context.WithTimeout(ctx, timeout)` 的 `ctx.Done()`：每个 Logic 入口 / 关键步骤前的 select |
| **覆盖情况（2026-10-21 实测）** | `UserDomainService` **11 处**（login/register/update/selectavatar/uploadavatar 等主路径）；`AdminDomainService` 此前 **0 处**，本轮已补到 **13 处**（admin-api 对 admin-rpc 的全部业务调用） |
| **⚠️⚠️ 此前从未真正生效** | **这 24 处守卫在 gRPC server 路径上一直是通过死代码在跑**：`onHalfClose()` 从未被调用（gRPC `ServerInterceptor` 压根没挂到 server 上，根因见 §6.4 的 H2 勘误），handler 线程上 `DeadlineState` 恒未设置 ⇒ `checkDeadline()` 的条件恒为 false，**一次都没有拦下过任何请求**。2026-10-21 补 `@Bean GrpcServerConfigurer` 后才第一次真正可达 |
| **回归网** | `AdminDomainServiceDeadlineTest`（2 条，13 方法逐个拦 + 反向放行）。**但它只能证明「分支逻辑对」，不能证明「生产路径会走到」** —— 这一点已被本轮实测打脸：守卫全绿，而 `onHalfClose` 从未执行。见下 |
| **⚠️ 它不能做什么** | **不能取消在途 IO**。JDBC 驱动不响应线程 interrupt，这是 Java 侧的结构性限制，属 §3.4.1 B-1，**不是 DRIFT-018 能修的**。B-1 由 `DeadlineQueryTimeoutInjector` 的 `setQueryTimeout` 关闭（已实施并端到端验证：查询滞留 4303ms → **176ms**，与 Go 的 182ms 同量级） |

> **为什么单独记这一条**：它说明了 §6.2.6 E-4 那类缺口的典型形态 ——
> **「框架限制」与「业务侧主动响应」是两件不同的事**，前者改不了，后者能做但容易漏做。
> B-1 原留档把两者混成一句「当前 user-rpc 一处都未使用」，既高估了缺失（其实 11 处）、
> 又让人误以为补齐 `checkDeadline` 就能关闭 B-1（不能）。
>
> **⚠️ 本条留档自身也曾踩了 §3.4.4 那条纪律（2026-10-21 补记）**：
> 「回归网已实证能真红」是**真的**（删掉守卫确实会红），但它证明的只是
> **「给定 `DeadlineState` 已过期，业务分支会走对」**，
> 而**没有**证明「`DeadlineState` 真的会在生产请求里被设上」。
> 这是「单测红 ≠ 生产可达」的又一例，与 `ObservabilityWiringTest` 的假绿同源。
> **⇒ 补一条判据**：对「超时 / 取消 / deadline」这类**由框架注入的状态**，
> 必须有一条**端到端**测试证明它真的被注入；只测「拿到该状态之后的行为」不够。

> **为什么单独记这一条**：它说明了 §6.2.6 E-4 那类缺口的典型形态 ——
> **「框架限制」与「业务侧主动响应」是两件不同的事**，前者改不了，后者能做但容易漏做。
> B-1 原留档把两者混成一句「当前 user-rpc 一处都未使用」，既高估了缺失（其实 11 处）、
> 又让人误以为补齐 `checkDeadline` 就能关闭 B-1（不能）。

##### 已修（4 项，全部带能真正触发机制的回归测试）

| ID | 内容 | Go 行为 | RAG 修复前 | 修复后 |
|---|---|---|---|---|
| **DRIFT-009** | **鉴权链顺序反了**（SEMANTIC MISMATCH） | `Authorize(JWT)` → `CheckBlacklist` | `CheckBlacklist` → `Authorize` | 改回验签优先 |
| **DRIFT-010** | **注销绕过**（安全，本批新发现） | `stripBearerPrefixFromTokenString` 前缀不匹配时**原样返回整串** → 验签必失败 | `split(" ", 2).parts[1]` 取到 `"eyJ..."` **验签通过**，黑名单又用整串查不到 | 按 Go 逐字重写；`uid <= 0` 检查移出拦截器，交给 `IdentityResolver` |
| **DRIFT-002** | 前缀语义 | 前缀**大小写不敏感**；缺失前缀**不报错**、原样当 token | `startsWith("Bearer ")` 大小写敏感 → 小写 `bearer` 被误拒 | 与 admin 侧共用 `BearerTokenExtractor` |
| **DRIFT-006** | 400 响应形状 | `http.Error(w, msg, 400)` → `text/plain; charset=utf-8` + 裸文本 + 尾换行 + `nosniff` | `ResponseEntity.status(400).build()` → **空 body** | `goStylePlainTextBadRequest` + advice 对非 JSON 透传 |

> **DRIFT-009 的病灶形态与 §3.4.2 N-1 完全同构，但方向相反，值得单独记住**：
> N-1 是「引用了不存在的机制」，DRIFT-009 是「**引用了真实文件、但把 chain 的内外关系读反了**」。
> 原 javadoc 写「WithJwt 追加到切片尾部 ⇒ 黑名单是外层、先执行」——
> `Append` 到尾部确实是 chain 里最内层，但**黑名单根本不在 chain 里**：
> `rest/server.go:222-233` `WithMiddleware` 在**构造期**就把 `route.Handler` 包成
> `middleware(route.Handler)`，它是 `ThenFunc` 的**参数**，比 chain 任何元素都靠内。
> **假出处比无出处更贵**：无出处时 review 会去查；假出处会让 review 直接采信一个方向相反的结论。
>
> 更严重的是它还**同时锁死了测试**：`AdminApiMvcPipelineTest#blacklistCheckedBeforeSignatureVerification`
> 与 `AdminSemanticDriftApiRegressionTest#interceptor_checksBlacklistBeforeSignatureVerification`
> 两个用例名都把错误顺序写成了规格。**评审纪律的推论：测试名里出现「先后顺序」时，
> 必须独立回 Go 源码复核一次，不能因为「已有测试锁住」就默认它对。**

> **DRIFT-010 的教训是「安全规则的第二份实现本身就是缺陷来源」**。
> 同样的前缀提取逻辑当时散在 admin（`split(" ", 2)`）与 user（`startsWith("Bearer ")`）两处，
> 各错各的。已抽到 `users-common` 的 `BearerTokenExtractor`，两个模块共用一份，
> 且**刻意保留 Go 内部两套互不相同的取法**（验签用大小写不敏感的 strip，
> 黑名单用大小写敏感的 `TrimPrefix`）—— 合并成一个方法等于擅自修好 Go 的 bug，
> 那会制造一处新的语义漂移。

##### 不改代码、只留档（3 项）

| ID | 内容 | 理由 |
|---|---|---|
| **DRIFT-003** | Go 的 `errmsg.codeMsg`（`errmsg.go:38-68`）**缺 1017 / 1018** ⇒ `GetErrMsg(1017)==GetErrMsg(1018)=="FAIL"`；RAG 的 `ErrorCode` 补了真实中文 | 当前潜伏：Go 从不返回这两个码；RAG 侧 1018 **只作日志码**（`UserDomainService.java:550,607`，线上抛的仍是 `Status.NOT_FOUND`）。**但若 N-3 日后改成对齐 Go，`GetErrMsg(1017)` 会变成 `"FAIL"`，届时 RAG 的提示语必须同步回退。下一个 agent 不得把它当成新发现的漂移再「修」一遍** |
| **DRIFT-005** | Snowflake workerId 优先级：Go = `env → hash`（`snowflake.go:52-85`），RAG = `env → properties → hash`（多一层配置面）；截断 Go = `uint16(id)` 后 `%1024`，RAG 直接 `%1024` | RAG 是配置超集；Go 的截断对 `>65535` 的值是缺陷。两者都不可观察，不改代码 |
| **DRIFT-004** | ~~`cryptx.CheckPassword` 对任何 bcrypt 错误返回 false，RAG 抛 `IllegalArgumentException` ⇒ `500 + 5001`~~ | ✅ **2026-10-21 已修（用户签字「对外对齐 Go，但保留可观测性」）** | 见下方「DRIFT-004 的处置」小节。**触发面比原留档窄得多**：实测 Spring Security 7.0.7 下只有 **cost 字段越界**（`$2a$99$…`）会抛，空串 / 非 bcrypt / 截断这些形态 Spring 本就返回 false、与 Go 已一致 |

##### 本批新增的测试（11 条）

| 模块 | 测试类 | 条数 | 关键点 |
|---|---|---|---|
| admin-api | `AdminApiMvcPipelineTest` | 13 → 17 | 改用 `@MockitoSpyBean` 包真 Redis，使「黑名单**一次都没查**」成为可断言的顺序证据 |
| admin-api | `AdminSemanticDriftApiRegressionTest` | 24 → 26 | 新增注销绕过 + 大小写不敏感两条 |
| user-api | `UserApiMvcPipelineTest` | 9 → 14 | 新增顺序 / 绕过 / 大小写 / 黑名单 key / 400 text/plain 五条 |

> **`@MockitoSpyBean` 而非纯 mock Redis 的理由**：纯 mock 只能证明「代码调用了 `hasKey`」，
> 证明不了「它一次都没调用」—— 而 DRIFT-009 的语义**恰恰是后者**。
> 旧的 `blacklistCheckedBeforeSignatureVerification` 用「Redis 里有这条 key」当顺序判据，
> 那条断言在任何实现下都成立，属于**假绿**。

#### 3.4.1 `service/common` 的 Java 端差异（2026-10-21 逐行审计产出）

Go ↔ RAG 对照审计 `service/common` 全部 8 个 Go 非测试源文件与 RAG 全部主源码，产出 20 项偏差、修复 21 处（分支 `row/common-fix-semantic-drift`，issue #71186341）。**以下 5 条是修复后仍然存在、且无法靠改代码消除的差异**。

**分类原则**：「有意偏离」= 主动选择与 Go 不同（须签字，不得顺手改回）；「框架限制」= 想对齐但语言/框架做不到；「表述差异」= 语义已对齐、仅字面不同。三者性质不同，review 时不可混谈。

##### A. 有意偏离（需签字）

| # | 位置 | 差异 | Go 行为 | Java 行为 | 理由 | 关闭条件 |
|---|---|---|---|---|---|---|
| A-1 | `Logger.doLogFatal` / `LoggerFactoryBean.logFatal` | **等价域收窄** | `logger.go:293-296` 输出 `fatal_error` 后无条件 `os.Exit(1)`，**永远终止** | 抛 `FatalError`，**仅在无人 catch 时**终止 | 用户 2026-10-21 拍板：`System.exit` 会绕过 Spring graceful shutdown 与 `@PreDestroy` | 若业务出现 `catch (FatalError)` 后继续运行的写法，等价性即被打破 |
| A-2 | `Logger.isFiltered` / `LoggerFactoryBean.isFiltered` | **`call_chain` 内容比 Go 少框架帧** | `logger.go:53-58` 的 `github.com/zeromicro/go-zero/` 前缀**本身是死的**（`getFuncName` 同样剥掉包路径），只有 `runtime.`/`testing.` 生效 | 按该列表的**意图**执行，6 条前缀全部可命中 | Go 侧是 bug；Java 照抄会让「过滤」永远不生效。已在 javadoc 留档 | 若要求逐字复刻 Go 的**实际行为**（即不过滤），需回退本项 |

##### B. 框架限制导致的已知不等价（**不是**有意偏离，无法靠改 `service/common` 消除）

| # | 位置 | 差异 | 影响面 | 关闭条件 |
|---|---|---|---|---|
| B-1 | `HttpMiddleware` 超时分支 / `RpcInterceptor` 超时调度 | **超时无法取消在途 IO**。Go 的 `context.WithTimeout` 到点 ctx Done，所有 ctx-aware IO（`http.Client` / `sql.DB` / `grpc.Dial`）立即返回、连接池立刻释放；Java 只能 `Future.cancel(true)` 发 `InterruptedException`，而 **JDBC 驱动、grpc-java 阻塞 stub、`java.net.http.HttpClient` 均不响应线程 interrupt** —— 阻塞在 socket read 上的调用会挂到查询自行结束 | **✅ 已实测坐实并已修复（E-4 + issue #71187874，2026-10-21）**。**修前**：`exp-b1-timeout-cancel.sh` 用 ACCESS EXCLUSIVE 表锁造真阻塞、两侧同跑 `user.login`，客户端返回后 **Go 侧 PG 查询 203ms 消失、RAG 侧还存活 4657ms**。**修后**：`DeadlineQueryTimeoutInjector` 按 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 发 CancelRequest，**RAG 侧查询 176ms 消失，与 Go 的 182ms 同量级**；响应体也从 `text/plain "Request Timeout"` 变成 Go 的 `{"code":1015,"msg":"服务繁忙"}`。完整三层证据（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」 | **✅ 已关闭**（本行原写「无法靠改 `service/common` 关闭」，该结论**已过时**）。① **字节码证据**：链路 MyBatis-Plus → MyBatis → Spring JDBC → pgjdbc → socket，逐层 `javap -c` 数 `isInterrupted` 命中**均为 0** —— interrupt 在整条数据访问路径上无人检查。② **行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：主动 interrupt 一条等锁查询，它**照常正常返回、耗时 11014ms**（比不 interrupt 还长 1s）。③ **修复路径**：拦 `StatementHandler.prepare` 注入 timeout —— 与 Go `database/sql` 的 ctx-aware 取消是**同一条服务端语义且连接保可用**；`socketTimeout` 会让连接报废，语义相反，不可用。⚠️ 修复过程中还暴露更大的前置缺陷：gRPC `ServerInterceptor` 从未挂到 server 上，`DeadlineState` 恒未设置 ⇒ **DRIFT-018 的 24 处守卫此前全部是死代码**，见 §6.4 的 H2 勘误 |

##### B-1 补记：机制实验与缓解手段定性（2026-10-21 同日同场三组对照）

B-1 上方的时长数字取自首轮实验。本轮把它拆成三层各测一遍，**结论有实质修正**，此前数字一并更正。

**第一层 —— 纯 JDBC 机制层（`tools/difftest/probe-jdbc-cancel.java`，绕开 HTTP/gRPC/MyBatis）**
ACCESS EXCLUSIVE 表锁持 10s，三组对照：

| mode | 结果 | 耗时 |
|---|---|---|
| `none` | ✅ 正常返回（阻塞到锁释放） | 10022ms |
| `qto:2` | ❌ **`SQLState=57014`** `canceling statement due to user request` | **2034ms** |
| `interrupt` | ✅ 正常返回 —— **interrupt 零效果** | 11014ms |

⇒ interrupt 组比 none 组还慢 1s（interrupt 就发生在 1000ms 处），说明 interrupt **连让查询稍早返回都没做到**。B-1 由此从「字节码推断」升级为「行为实测」。

**第二层 —— 同日同场端到端对照（`exp-b1-timeout-cancel.sh`，21:03 一场连跑基线与注入两组）**

| | 客户端返回 | 响应体 | PG 查询总寿命 | 客户端返回后又存活 |
|---|---|---|---|---|
| **Go**（基线） | 2190ms | `{"code":1015,"msg":"服务繁忙"}` | ~2.2s | 203ms |
| **RAG 基线** | 3174ms | `text/plain "Request Timeout"` | **~7.4s** | **4657ms** |
| **RAG 注入 `default-statement-timeout=2`** | 2449ms | `{"code":5001,"msg":"系统内部错误"}` | ~2.06s | 193ms |

⇒ 注入组 RAG 与 Go 已同量级（worker `business_error` 落在请求发起 +2.06s，与设定值吻合）。
基线组的 RAG 查询 `pid=113 query_start=21:03:15.203` 一直活到 `21:03:22.600`，而 worker `business_error` 在 `21:03:22.722` —— **worker 是在查询真正结束后才退出的**，比客户端返回（21:03:18.382）晚了 4.34s。

> **⚠️ 本次差点踩的坑：把日志里相邻两行当成同一次请求。**
> 首轮观测出现「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后才结束」的「三方矛盾」，
> 追了很久。真相是：`business_info` 那一行属于 **[2/4] 阶段的注册请求**，
> `business_error` 才是本轮 login 的 —— 中间隔着整段 Go 探测，**根本不是同一次调用**。
> **纪律**：任何靠日志两行之差推断耗时的读数，必须先确认两行属于**同一次请求**
> （本例靠 worker 侧 span 名 / 请求内容区分），否则该读数不可用。
> 这与 N-3/N-6「留档声称的行为从未存在过」同类：**先证伪读数，再谈结论**。

**第三层 —— 缓解手段定性**

| 手段 | 实现 | 超时后果 | 与 Go 是否等价 |
|---|---|---|---|
| **`Statement.setQueryTimeout`** | pgjdbc 起 `StatementCancelTimerTask` → 发 **CancelRequest** | 查询停止，**连接保持可用** | ✅ 等价 Go 的 ctx cancel |
| `socketTimeout` | `PGStream.setSocketTimeout` → `Socket.setSoTimeout` | socket 读超时 → **连接报废** | ❌ 语义相反，会污染连接池 |

⇒ **只能用 `setQueryTimeout`，不能用 `socketTimeout`。**

##### B-1 补记（续）：生产方案已实施（2026-10-21，issue #71187874）

上述实验结论已落地为代码，**两半缺一不可**：

| # | 组件 | 位置 | 职责 |
|---|---|---|---|
| ① | `DeadlineQueryTimeoutInjector` | `service/common` `persistence/` | 拦 `StatementHandler.prepare`，按 `DeadlineState.remainingMillis()` 设 `setQueryTimeout(ceil)`；**无 deadline 时完全不动** |
| ② | `SqlTimeoutStatusTranslator` | 同上 | 拦 `Executor.query/update`，把 `SQLState=57014` 翻成 `Status.DEADLINE_EXCEEDED` |
| ③ | 46 处 `catch (StatusRuntimeException e) { throw e; }` | `UserDomainService` 17 + `AdminDomainService` 29 | 让 ①② 的产物、以及 `checkDeadline()` 自己抛的 DEADLINE_EXCEEDED，**不被 `catch (Exception e)` 吞成 INTERNAL** |

**api 层零改动**：`DEADLINE_EXCEEDED` 落到 `mapRPCStatusToCode` 的 default 分支 → 1015，与 Go 一致。

> ⚠️ ③ 顺带修掉一个既有缺陷：`checkDeadline()` 抛的正是 `StatusRuntimeException(DEADLINE_EXCEEDED)`，
> 而它被写在 `try` 块内 ⇒ 此前会被同方法的 `catch (Exception e)` 吞成 INTERNAL。
> **即 DRIFT-018 那 24 处守卫里，落在 try 内部的部分从未生效过。**

**端到端结果（修复前 → 修复后，对照 Go）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |
| difftest 39 步 | 39/39 | **39/39，unexpected=0** | — |

**⚠️ 但实施过程先撞上一个更大的前置缺陷**：③ 全部补齐、①② 全部就位后，
端到端**仍然零效果**（3318ms / `text/plain` / 查询滞留 4378ms）。诊断日志显示：
`newUnaryServerInterceptor` 确实收到 `timeout=PT2S`，可 `interceptCall` 与 `onHalfClose`
**从未被调用**，`DeadlineState.remainingMillis()` 恒为 `Long.MAX_VALUE`。

**根因不在 B-1，而是 gRPC `ServerInterceptor` 从未真正挂到 server 上** ——
`@Import` 只保证 Bean 存在，starter 3.1.0 不自动注册。详见 **§6.4 的 H2 勘误小节**。
⇒ 这条链路上**任何**依赖 gRPC 超时 / deadline 传播的机制此前都是死的，
包括 §3.4.4 登记的 DRIFT-018（24 处 `checkDeadline()`）——**它们一次都没真正生效过**。

**新增回归网（32 条）**：
`DeadlineQueryTimeoutInjectorTest`（7）+ `SqlTimeoutStatusTranslatorTest`（8）+
`DeadlinePassthroughTest`（user-rpc 7）+ `AdminDeadlinePassthroughTest`（admin-rpc 8）+
`GrpcInterceptorWiringTest`（2，防 H2 假绿复发）。全仓 383 → **415 条，0 failures**。

> **实现期踩到的三个坑（均已写进代码注释）**
> ① `Invocation.proceed()` 声明抛的是 `InvocationTargetException`（反射包装），**不是** `SQLException`
>    —— 写 `catch (SQLException)` 编译通过但**永远匹配不上**，翻译器静默失效。
> ② MyBatis 3.5.19 的 `Invocation` 构造器校验 `method.getDeclaringClass()` ∈ 内部白名单，
>    测试里自造同名方法会报 `"is not supported as a plugin target"`；必须用 `mock(接口)`。
> ③ `LambdaUpdateWrapper` 在无 Spring 上下文时缺 MP 的 TableInfo 缓存，抛
>    `MybatisPlusException: can not find lambda cache` —— 它**不是** StatusRuntimeException，
>    于是被**正确地**吞成 INTERNAL，测试以「放行分支没生效」的面貌失败，极易误导排查方向。

##### C. 表述差异（语义已对齐，仅字面不同，无需签字）

| # | 位置 | 差异 |
|---|---|---|
| C-1 | `RpcInterceptor.goStyleStatusMessage` | gRPC Java 的 `Code.name()` 返回 `NOT_FOUND`，Go 的 `codes.Code.String()` 返回 `NotFound`。code 与 desc 均已保留（Go 出处 `rpc.go:95`），仅常量表示不同，不做臆造映射 |

##### D. UNRESOLVED（证据不足，**不得**按「已对齐」处理）

| # | 项 | 未知点 | 需要什么才能定 |
|---|---|---|---|
| D-1 | `Wire.canonicalHash` 的 JCS 库 | Go 用 `github.com/cyberphone/json-canonicalization`，Java 用 `org.erdtman.jcs`，两者均声称 RFC 8785。**数值边界**（超长小数、指数记法、Unicode 转义）是否真等价，**无差分测试即不可知** | 补 RFC 8785 官方测试向量对拍；或对同一批 payload 同时跑两库比对 hash |
| D-2 | `communityfact` 整体 | RAG 侧**零消费者**。代码层面已对齐，但**没有任何运行证据**，不能算行为对齐 | 接入 `comment` / `article` 等域后补集成测试 |

##### 本节的效力边界

- 本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**（两边同时喂同一批输入比对输出）。因此「已修复 20 项偏差」≠「行为已验证等价」。
  - ⚠️ **2026-10-21 更正**：本行原写「全仓也**没有真实 PG / Redis 集成测试**（现有测试全部 mock 数据层）」，
    该句**已被后续工作推翻**，现共 5 个 testcontainers 文件：`UserRpcContainerWiringTest`（真 PG 12 条，
    见 §6.2 P1 已关闭）、`AvatarTransactionWiringTest`、`AdminRpcContainerWiringTest`、
    `UserApiMvcPipelineTest` / `AdminApiMvcPipelineTest`（真 Redis 容器 + 真 MVC 管线）。
    **但覆盖面仍有限**：`admin-rpc` 侧只有 wiring 级验证，`service/common` 的 20 项偏差
    仍**没有任何一条**跑在真实 PG/Redis 上 —— mock 数据层的单测证明不了 DDL、索引、
    事务隔离与连接池行为。
- 尤其 B-1 这类差异，**只有真实慢查询 + 真实超时才能暴露**，静态审查与单测都测不到，
  `tools/difftest` 也**没有慢查询场景**（见 §6.2.6「证据边界台账」）。

---

## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper 接口 `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService`（admin-rpc 是 1 个 `AdminDomainService`） | HUMAN_ONLY.MD 描述的是单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口（user-rpc：2 个；admin-rpc：3 个） | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + users 域 DDL 脚本。~~Flyway `V*__create_*.sql`~~ → **2026-10-21 改为幂等重放，见 §6.2.5 DRIFT-NEW-015** | HUMAN_ONLY.MD 列出这两个文件。Bootstrap 保留；**迁移机制被推翻**（有状态账本与「双 rpc 共用一个 schema」不兼容） |

> **✅ Flyway 条目已于 2026-10-21 处置完毕**：Go 用 `AutoMigrate`（无状态），Flyway 是**有状态账本**，双 rpc 共用同一 schema 时版本号必然冲突（已实证两轮）。最终改成**幂等重放 DDL**（`SchemaInitializer`），无账本无版本号 —— 与 Go 的 AutoMigrate 同构。完整经过与实证见 §6.2.5 DRIFT-NEW-015。

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

### 4.3 用户问答原话（备查，避免后续被覆盖）

| 问题 | 用户答案 |
|---|---|
| ORM 这一层怎么处理？ | 严格按 HUMAN_ONLY：JPA → MyBatis-Plus（重写数据层） |
| Logic 类怎么处理？ | 严格按 HUMAN_ONLY：9 合 1 |
| DataModel.java 怎么映射？ | 严格按 HUMAN_ONLY：拆成两个 Mapper（仅在选 MyBatis-Plus 时适用） |
| SnowflakeBootstrap 与 Flyway 脚本要不要落地？ | 落地：从 UserRpcApplication 抽 SnowflakeBootstrap + 引入 Flyway + 写两份 DDL。**⚠️ 2026-10-21 补充：Bootstrap 保留，Flyway 已被移除**（有状态账本不兼容双 rpc 共库），DDL 改为幂等重放 |
| 全量 5 阶段怎么走？ | 你先修改 `/Users/pr/Documents/java&golang/RAG/AGENTS.md`，说明是什么情况 |
| 重构以什么形式落到 RAG 仓库？ | 先等下（暂停，等本文件落地后再议） |

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

## 六、当前状态（§6.1–§6.3 截至 2026-10-21 核对；§6.4 起为 2026-10-20 快照）

> **状态表纪律见 §6.2 表头的三条约定**——本节任何「已修 / 未修」断言都必须带核对日期，否则按 `UNKNOWN` 处理。

### 6.1 service/users：行为一致性审计已完成（本次）

Go ↔ RAG 对照审计 `service/users` 全部 5 个子模块（22 个 RPC / 22 个 HTTP 端点），**结论：修复前不具备验收条件**。

**已修复（10 项，全部有回归测试锁定）**：

| # | 问题 | 类别 |
|---|---|---|
| 1 | `@ResponseBodyAdvice` 二次包裹 `@ExceptionHandler` 的 `ResponseEntity` → **全部错误响应顶层 `code` 恒为 200** | 实测确认的致命缺陷 |
| 2 | `CreateAdmin` 4 步事务 self-invocation → `@Transactional` 静默失效 | 安全（邀请码可并发消费） |
| 3 | `CreateAdmin` 邀请码查询缺 `FOR UPDATE`（注释写了锁、SQL 没有） | 安全 |
| 4 | `UpdateUser` 写 `extra_info` 走 `wrapper.set(Map)` → jsonb 被绑成 varchar | 功能失败 |
| 5 | `SelectAvatar` 响应 `is_current` 返回更新前的旧值 | 响应自相矛盾 |
| 6 | `UpdateSelf` 空 email 把列写成 SQL NULL → 只改密码会丢邮箱 | 数据丢失 |
| 7 | `JwtUtil` zero-pad 被删 → admin 默认密钥（15B）签发必抛 `WeakKeyException` | 启动阻断 |
| 8 | 登录 `UNAUTHENTICATED` 未映射 → 密码错报成 1015「服务繁忙」 | 用户可见回退 |
| 9 | uid 解析失败静默转 `0` → 坏请求报成 1003「用户不存在」 | 错误分类丢失 |
| 10 | HTTP 状态码语义变更（业务码 1004/1006/1009 → 401；框架错误 → 500） | wire 契约 |
| 10' | §6.2.2 **DRIFT-FRAMEWORK-001** —— 第 10 项的修复在 admin-api 侧**实际上未生效**：`HttpMiddleware$ObservabilityResponseWriter.setStatus` 只更新缓存字段，等下游 `outputMessage.flush()` 把底层 `MockHttpServletResponse.flushBuffer` 提交后，`flushTo` 的 `dst.setStatus(401)` 才会被 `!isCommitted()` 守卫吞掉 → 任何 `@ExceptionHandler → ResponseEntity.status(...)` 在运行期都退化为 HTTP 200。这是同一份代码里本批 §6.2.3 A-U0 admin-api 侧补验时发现的 | 实测确认的根因；原表第 10 项已修，但缺少「transport 层 status 必须 propagate 到 underlying」的链路。本批**新增**；该 bug 同时破坏 user-api 的 N-1「推到 401」——`AuthenticationHttpStatusTest` 看似通过是因为它走的是 `standaloneSetup`，不挂 `HttpMiddleware` filter；用 `AdminApiMvcPipelineTest`（真 MVC 管线）才能复现 |

新增 **19 个回归测试**（含 2 个 `ResponseEnvelopeAdviceTest` 把 Web 层纳入回归网——此前 RAG 无任何 `@WebMvcTest`/`MockMvc`，这是这批缺陷能长期潜伏的根因）。
验证：`mvn -pl <5 modules> -am test` + `package` 全绿（users-common 3 / service-common 69 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30）。

### 6.2 已知未修（按优先级）

> **⚠️ 本表的阅读约定（2026-10-21 新增）**
>
> 1. **每行必须带「最后核对」日期，且该日期必须由一次真实执行产出**——写代码、读代码、推断都不算核对。
> 2. **无核对日期或日期过期的行，一律按 `UNKNOWN` 处理**，不得直接采信其状态或结论。这与 §3.1「不拿现存代码当对照基准」是同一条纪律：一份没有时效标记的状态表，和未标日期的旧结论一样不可信。
> 3. 发现状态与本表不符时，**先更新本表再动手**，不要让文档漂移到代码后面。

| 优先级 | 项 | 最后核对 | 说明与证据 |
|---|---|---|---|
| ~~P0~~ | ~~`RpcServerInterceptorFactory` 在 user-rpc / admin-rpc 均未注册~~ | ✅ 2026-10-21 已修 · **2026-10-21 二次更正（issue #71187874）** | `UserRpcApplication` / `AdminRpcApplication` 的 `@Import` 均补 `RpcServerInterceptorFactory` + `ObservabilityProperties` + `ObservabilityLifecycle`；admin-rpc 另补 `SnowflakeBean/SnowflakeProperties/LoggerFactoryBean`（它此前连 `@Import` 都没有）。新增 `ObservabilityWiringTest`（`@SpringBootTest`）断言 `ServerInterceptor` 真是容器里的 Bean。分支 `row/common-fix-semantic-drift` **⚠️ 二次更正：本次「已修」不成立。** `@Import` 只让 Bean **存在**，grpc-spring-boot-starter 3.1.0 **从不自动注册**用户提供的 `ServerInterceptor`（`javap GrpcServerFactoryAutoConfiguration` → 只注入 `List<GrpcServerConfigurer>`；`GrpcServerProperties` 无 interceptor 字段）。真实缺陷由 B-1 的端到端实验暴露（见 §3.4.1「B-1 补记」）：`interceptCall` / `onHalfClose` 从未执行。**真正修法**：两个 rpc 模块各补 `@Bean GrpcServerConfigurer { server -> server.intercept(interceptor); }`。原测试之所以假绿，是因为它断言的是「Bean 存在」而非「server 用了它」⇒ §3.3 新增纪律与 `GrpcInterceptorWiringTest`（2 条） |
| ~~P0~~ | ~~`ObservabilityProperties` 无 timeout 字段~~ | **2026-10-21 已修** | 新增 `httpTimeout` / `rpcTimeout`；两个 factory 改为读取该配置。**目标值已取证**：Go user 系 yaml 均未配 `Timeout`，实际生效值来自 go-zero v1.10.2 `rest/config.go:55` `default=3000` 与 `zrpc/config.go:46` `default=2000` ⇒ 各模块 yaml 显式落 `http-timeout: 3s` / `rpc-timeout: 2s` |
| **P1** | `message-rpc` 6 个 `@Transactional` 方法的 self-invocation 风险 | —（**未核对**） | 同上。按约定 2 按 UNKNOWN 处理 |
| ~~P1~~ | ~~`Logger`（静态）与 `LoggerFactoryBean`（Bean）两份逐行同构实现，重复约 370 行~~ | ✅ **2026-10-21 已去重**（用户 2026-10-21 批准动共享库公开 API） | 经方法体逐字比对，两份实现只差三处：方法可见性、方法名自调用、Bean 缺 serviceName 判空守卫。**修法**：给 `Logger` 加 `public Logger(String serviceName)` 构造 + 把 `doLogBusinessErr/doLogInfo/doLogFatal` 由包私有改为 public；`LoggerFactoryBean`（392 行 → 95 行）改为薄委托，同时删掉它自己那份 `FILTER_PREFIXES` / `IMAGE_BASE64_TRUNCATE` / `getFuncName` / `getCallChain` / `isFiltered` / `extractTraceId` / `fileBaseName`。**两套 public API 全部保留**（静态 `Logger.logXxx` 与 Bean 的 `logXxx`），调用方零改动；serviceName 仍各自独立（决策 3 未变）。**委托顺带修正一个 off-by-one**：委托前 Bean 路径栈是 `getCallChain → Bean.logInfo → 业务`，`getCallChain(3)` 跳 3 帧会落在业务调用方的**上一层**（少一帧）；委托后多一层 `doLogXxx`，正好对齐静态入口。锁定用例 `LoggerFactoryBeanTest.callChainStartsAtBusinessCaller`（探针放在未被 `FILTER_PREFIXES` 过滤的 `com.rag.common.verify` 包） |
| ~~P2~~ | ~~`Logger.call_chain` 过滤完全失效~~ | **2026-10-21 已修** | `isFiltered` 改用 `StackFrame.getDeclaringClass().getName()` 全名比对包前缀（短名比对仅作补充）；`getFuncName` 的 `contains("$")` 分支按 Go 两分支差异化。`Logger` 与 `LoggerFactoryBean` 两份同步修改。新增 `LoggerTest.callChainFilterActuallyRemovesFrameworkFrames` 锁定。**留档**：Go 侧 `github.com/zeromicro/go-zero/` 那条前缀**本来也是死的**（`getFuncName` 同样剥掉包路径），Java 版按其**意图**执行 → `call_chain` 内容比 Go 少框架帧，属 OBSERVABLE DIFFERENCE |
| ~~P2~~ | ~~499 状态码永远到不了客户端~~ | **2026-10-21 已修** | 断连分支改为 `writeTimeoutResponse(resp, 499)`，不再抛 `ServletException`；503/499 body 一并对齐 Go `http.Error`（`text/plain; charset=utf-8` + 尾部换行）。新增 `HttpMiddlewareTest.clientDisconnectReturns499`（此前**零条** 499 断言）。**连带**：两条既有 503 断言的期望值因 D-11 变更，已同步并注明变更原因 |
| ~~P2~~ | ~~`Observability.isTimeoutError` 漏判 HTTP 超时~~ | **2026-10-21 已修** | 补 `HttpTimeoutException` / `HttpConnectTimeoutException` / `InterruptedIOException`，并沿 cause 链生效。新增 `ObservabilityTest.isTimeoutErrorDetectsHttpClientTimeouts` |
| ~~P1~~ | ~~user-rpc mapper `default` 方法上的 `@Transactional` 是否真生效~~ | ✅ **2026-10-21 已验证生效** | 新增 `AvatarTransactionWiringTest`（`@Configuration @EnableTransactionManagement(proxyTargetClass = false) @MapperScan`，与 Boot `TransactionAutoConfiguration` 同构；`MybatisSqlSessionFactoryBean` + `SqlSessionTemplate`；事务管理器换成**记录型**实现）。5 条用例全绿，其中 2 条是**真触发**：DataSource 的 `getConnection()` 抛异常，断言 `createAvatarHistory` / `selectAvatarHistory` 的记录序列恰为 **`begin → rollback`** 且无 `commit`。结论：`@Transactional` **确实生效**，头像 4/5 步原子性成立。**原表建议的修法「抽独立 `@Service` Bean」不需要** —— 问题从来不是自调用，而是**缺证据**。**效力边界**：无真实 PG，故只证明「事务边界装上了、异常走回滚分支」，不证明 jsonb `CAST` 等 PG 行为（§3.3） |
| ~~P1~~ | ~~**user-rpc 无真实 PG 集成测试（jsonb / CAST / 事务真实回滚均未被证明）**~~ | ✅ **2026-10-21 已关闭**（issue #71186893） | 新增 `UserRpcContainerWiringTest`（`@SpringBootTest(classes = UserRpcApplication.class)` + Testcontainers `postgres:16-alpine`），**12 条用例全绿**。关闭 5 个 §3.3 缺口：① `JsonbTypeHandler` 的 `ps.setObject(..., Types.OTHER)` 在 MP 生成的 insert 上真能写进 jsonb 列（失效则 PG 报 `column "extra_info" is of type jsonb but expression is of type character varying`，**H2 与 mock 都测不到**）；② 手写 `@Select` 上的 `@Results` 真把 jsonb 读成 `Map` 而非静默 null；③ `updateUserById` 的 `CAST({0} AS jsonb)` 在真 PG 成立；④ 4/5 步事务**真的回滚行** —— 让 W3 撞 `UNIQUE(history_id)` 失败后断言 W2 的 `clearCurrent` 被回滚（旧类只证明 `begin→rollback` 的**调用序列**，证明不了行是否还在）；⑤ `username`/`email` 的 UNIQUE 约束真拦得住（Go 侧 `AlreadyExists` 的地基）。另加两条防御：`extra_info` 列类型必须是 `jsonb`（若退化成 text，全部 jsonb 断言会假绿）、全零值 patch 不发 UPDATE。**Ryuk 已关**（与 admin-rpc 同款 surefire env 注入）。**效力边界**：只证明 RAG 侧在真 PG 上的行为，**仍未做 Go ↔ RAG 双端对拍**（A-U2） |
| ~~P2~~ | ~~admin-api 全部 13 个端点 `@RequestBody` 仍是 `required=true`~~ | ✅ **2026-10-21 已修 · 2026-10-21 二次更正（DRIFT-NEW-016）** | 13 个端点改 `required=false` + Controller 侧 `null → new XxxReq()` 归一化。**⚠️ 二次更正**：本行原写「Go 三段取证（§6.2.4）证明 Go 对**空 body 一律不报错**」—— **该前提已被 DRIFT-NEW-016 实测证伪**（Go 的 `UnmarshalJsonMap(nil, v)` 收的是有类型的 nil map，照常遍历字段并报必填 ⇒ 真空 body 返回 `400 field "xxx" is not set`）。**连带**：(a) 本行引用的两条防回退测试 `emptyBodyDoesNotReturn400` / `emptyBodyOnPublicEndpointDoesNotReturn400` 已在 DRIFT-016 中**重命名**（现为 `emptyBodyOnProtectedEndpointIs400TextPlain` / `emptyBodyOnPublicEndpointIs400TextPlain`），旧名已不存在；(b) 原写「user-api 侧仍有 5 个端点保持 `required=true`、本轮未处理」—— grep `required = true` 在 `service/users` 全域 `src/main` **零命中**，且那 5 个端点（register / login / avatar / avatar-select 中有必填字段的 4 个 + user.update 全 `,optional`）已被 DRIFT-NEW-016 改走 `GoStyleRequest.normalize`，见 §6.2.5 |
| ~~P3~~ | ~~`admin-api` 的 `AdminApiSupport.codeFromRPCError` 未删，且与 `GlobalExceptionHandler.mapRPCStatusToCode` 重复~~ | ✅ **2026-10-21 已删** | grep 确认**零调用方**（只有自身声明与 javadoc 提到）。已删除方法与常量依赖，删除理由写进类 javadoc：留着的真实风险不是「重复」而是它是一份**可被误用的错误映射** —— 新 Service 顺手调它就会绕过自身 switch 落进默认值，这正是 `user-api` 的 `AvatarSelectService` 已踩过的坑（Go `NOT_FOUND → 500` 变成 `1003`，DRIFT-003） |
| ~~P1~~ | ~~**`service/users/admin` 全部 10 项 SEMANTIC MISMATCH**~~ | ✅ **2026-10-21 已修**（issue #71186443） | 见 §6.2.2 |
| ~~P3~~ | ~~`admin-rpc` 三个 Mapper 的 javadoc 与实现不符~~ | ✅ **2026-10-21 已修** | 三个接口都改为「空壳接口 + 方法真实归属表」。原注释不仅指错位置，还**与文件末尾的注释自相矛盾**（一个说在 Mapper default 方法里、一个说在 Service 里）。悲观锁的真实位置已写明：`LambdaQueryWrapper.last("FOR UPDATE")` 在 `AdminCreateAtomicService.createAdminAtomic`，`@Transactional` 在该 Service 上。**动机**：注释说在 Mapper 会让后续 review 去 Mapper 找锁、找不到就怀疑锁没生效 |
| ~~P2~~ | ~~`MessageRpcSupport.trimPreview` 截断长度错：255 vs Go 的 80 runes~~ | ✅ **2026-10-21 已修** | Go 出处 `message.go:234-240` 字面量 `80`。旧实现的 255 抄自列宽 `varchar(255)` 而非 Go 规则；且用 `String.length()`/`substring` 按 UTF-16 code unit 切，**中文测不出来、代理对会切出半个非法字符**。改为 `codePointCount` + `offsetByCodePoints`。新增 `MessageRpcSupportTrimPreviewTest` 6 条，其中 `surrogatePairCountedAsOneRune` 用 100 个 emoji 锁住 char≠rune |
| ~~P2~~ | ~~`markNotificationRead` 拆 SELECT + UPDATE~~ | ✅ **2026-10-21 已修** | Go `message.go:72-86` 是单条 `UPDATE ... WHERE id = ? AND recipient_id = ?` + `RowsAffected == 0 → ErrRecordNotFound`。旧实现 `selectById` 校验归属后再 `updateById` 写入（**TOCTOU 竞态**），且 `updateById` 生成的 WHERE **不含 `recipient_id`**、受影响行数被丢弃。已折成单条原子 UPDATE，归属条件进 WHERE，受影响行数透出并映射 `NotFound`。新增 `MessageMarkNotificationReadTest` 4 条，断言打在 `LambdaUpdateWrapper.getSqlSegment()` 上（§3.3：mock 数据层测不到 SQL） |
| **P0** | **`NotificationEntity.isRead` 字段名导致 MP lambda 运行期抛异常** | ✅ **2026-10-21 已修（本轮附带发现）** | 见 §6.2.4。这是本轮**唯一的运行期致命项**，且与 §6.2 上表所有条目无关，是修 `markNotificationRead` 时顺带撞出来的 |
| ~~P0~~ | ~~**admin-api 鉴权链顺序反了（DRIFT-009）**~~ | ✅ **2026-10-21 已修**（issue #71186893） | Go 出处三段：`rest/engine.go:101-113` bindRoute 先 `appendAuthHandler` 再 `ThenFunc(route.Handler)`；`rest/chain/chain.go` 的 `Then = m0(m1(...mN(h))` 索引 0 最外层先执行；`rest/server.go:222-233` `WithMiddleware` 在构造期把黑名单包进 `route.Handler`（`ThenFunc` 的参数，比 chain 任何元素都内层）。⇒ **Authorize 先于 CheckBlacklist**。原 javadoc 的 Go 出处把内外关系读反了。已改回并把两个反向锁定的测试改名反向。详见 §3.4.4 |
| ~~P0~~ | ~~**admin-api 注销绕过（DRIFT-010，安全）**~~ | ✅ **2026-10-21 已修**（issue #71186893） | `golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12` 的 `stripBearerPrefixFromTokenString` 在前缀不匹配时**原样返回整串**；旧实现 `split(" ",2).parts[1]` 会用 `"eyJ..."` 验签通过，而黑名单又按整串查不到 ⇒ **已注销的管理员 token 加 `XXBearer ` 前缀后仍可调用 admin-api**。已按 Go 逐字重写，并把该逻辑抽到 `users-common/BearerTokenExtractor` 由两个 api 模块共用。锁定用例 `AdminApiMvcPipelineTest#nonBearerPrefixedTokenCannotBypassBlacklist` + `AdminSemanticDriftApiRegressionTest#interceptor_nonBearerPrefixCannotBypassBlacklist` |
| ~~P1~~ | ~~**user-api 前缀语义与 Go 不一致（DRIFT-002）**~~ | ✅ **2026-10-21 已修** | 旧 `startsWith("Bearer ")` 大小写敏感 ⇒ 小写 `bearer <有效token>` 被误拒 401；Go 是 `strings.ToUpper(tok[0:7])=="BEARER "` 的大小写不敏感比较。已改走 `BearerTokenExtractor`。锁定用例 `UserApiMvcPipelineTest#bearerPrefixIsCaseInsensitive` |
| ~~P2~~ | ~~**400 响应体空 vs Go 的 text/plain（DRIFT-006）**~~ | ✅ **2026-10-21 已修** | Go：`service/user` 全树未注册 `httpx.SetErrorHandler` ⇒ `httpx.Parse` 失败走 `doHandleError` 的 `handler==nil` 分支 → `http.Error(w, msg, 400)` = `text/plain; charset=utf-8` + 裸文本 + 尾换行 + `nosniff`。RAG 原为 `ResponseEntity.status(400).build()`（空 body）。已加 `goStylePlainTextBadRequest`，并让 `GlobalResponseAdvice#beforeBodyWrite` 对非 JSON 响应透传（否则 400 文本会被再包成 envelope）。锁定用例 `UserApiMvcPipelineTest#malformedJsonReturnsPlainTextBadRequest` |
| ~~P3~~ | ~~**`UserApiSupport.extractUserId` / `AdminApiSupport.extractAdminId` 零调用方**~~ | ✅ **2026-10-21 已删** | 二者是 `IdentityResolver.claimedUID` 的薄包装，Go 侧无对应物（Go 直接调 `identity.ClaimedUID`），`src/main` 下各 0 个调用方。按 §3.4.1 D-2 先例「零消费者的代码不能算已对齐」删除，避免留下第二个入口 |
| ~~P1~~ | ~~`PasswordUtil.check` 对损坏 bcrypt hash 抛异常 vs Go 返回 false（DRIFT-004）~~ | ✅ **2026-10-21 已修**（用户签字「对外对齐 Go + 保留可观测性」） | Go `cryptx.CheckPassword` 对任何 bcrypt 错误都返回 false ⇒ 登录报密码错；RAG `BCryptPasswordEncoder.matches` 对 **cost 越界**的 hash 抛 `IllegalArgumentException` ⇒ `500 + 5001`。已加 `PasswordUtil.checkDetailed` + `Outcome` 枚举，对外行为对齐 Go、额外把「hash 格式非法」记进 span/log。**实测推翻原留档的触发面估计**（只有 cost 越界才抛，空串/非 bcrypt/截断 Spring 本就返回 false）。回归网 11 条，已实证能真红。详见 §3.4.4「DRIFT-004 的处置」 |

### 6.2.4 本轮（2026-10-21 第二批）取证与附带发现

#### ① Go 对「空 body」的三段取证（支撑 admin-api 13 端点 `required=false`）

不是推断，是逐段读源码得到：

| # | 出处 | 事实 |
|---|---|---|
| 1 | `service/user/admin/api/internal/handler/admin/*.go` | 13 个 handler 一律 `if err := httpx.Parse(r, &req); err != nil`，`req` 是包级 `var req types.XxxReq` |
| 2 | go-zero v1.10.2 `rest/httpx/requests.go:153` `withJsonBody` | `r.ContentLength > 0 && ContentType 含 application/json`。空 body ⇒ false ⇒ `ParseJsonBody` 走 `mapping.UnmarshalJsonMap(nil, v)`，对 struct 是 **no-op、不报错** |
| 3 | admin `api/internal/types/types.go` | 13 个 Req **零个** `Validate()` 方法、**零个** `validate:` tag；且 admin 服务**从未**调 `httpx.SetValidator` ⇒ `getValidator()` 返回 nil ⇒ `httpx.Parse` 的校验环节整体不生效 |

**结论**：Go 对空 body 的 POST 一律 **HTTP 200 + 业务码**，req 停在**零值 struct**。
以 `loginlogic.go:37-56` 为例：零值 username/password **直接透传给 rpc**，
由 rpc 判 `NotFound → 1003` / `Unauthenticated → ErrorLoginWrong`（= **1002**，不是 1008）。

**RAG 处置**：13 端点全改 `@RequestBody(required = false)` +
Controller 侧 `if (req == null) req = new XxxReq()`（对齐 Go 的零值 struct，服务层因此不会 NPE）。

> **user 侧同样做了独立取证**（2026-10-21，**未套用 admin 的结论** ——
> §3.4.3 N-3 明确警告过「admin 侧取参机制不同，不可跨模块套用结论」）：
> `internal/handler/user/*.go` 9 个 handler 一律 `var req types.XxxReq` + `httpx.Parse`；
> `types.go` 8 个 Req、**零个** `validate:` tag、**零个** `Validate()`；
> 整个 user 服务**从未**调 `httpx.SetValidator`。结论与 admin 侧一致 ⇒
> user-api **9 个端点全部**改为 `required=false` + Controller 侧 `null → new XxxReq()`。
> 落点验证：`loginlogic.go` 的 switch 是 `Internal → ErrorServerCommon` / `default → CodeServerBusy`，
> 零值 username/password **直接透传给 rpc**（`UserLoginService` 同样对 null 字段兜底成 `""`）。
>
> **⚠️ 2026-10-21 二次更正（DRIFT-NEW-016）**：本段原写「新增 2 条真管线断言
> `emptyBodyOnLoginDoesNotReturn400` / `emptyBodyOnUpdateDoesNotReturn400`」并断言
> 零值 LoginReq **带着空 username/password 抵达 rpc**。该结论**已被实测证伪**：
> Go 的 `UnmarshalJsonMap(nil, v)` 收的是**有类型的 nil map**，在 `unmarshaler.go:988`
> 的 type switch 里命中 `case map[string]any`，照常遍历字段 ⇒ 真空 body 返回
> `400 field "username" is not set`，**零值 req 到不了 rpc**。
> 该条已改名 `emptyBodyOnLoginIs400NotPassedThrough` 并反转断言；
> `emptyBodyOnUpdateDoesNotReturn400` 因 `user.update` 四字段全 `,optional` **不反转**，
> 改名 `emptyBodyOnUpdateStillPassesThroughBecauseAllFieldsAreOptional`，留作对照组
> ——同一个「空 body」在两个端点一个 400 一个 200，差别只在 DTO 的 tag。

#### ② `NotificationEntity.isRead` → 运行期致命（本轮唯一 P0，附带发现）

修 `markNotificationRead` 时为写「断言打在真实 SQL 上」的测试（§3.3），
用 `TableInfoHelper.initTableInfo` 初始化 MP 元数据后撞出来的：

```
### keySet=[content->content, ..., isRead->isRead, readAt->readAt, ...]
### NotificationEntity::isRead  →  MybatisPlusException:
        can not find lambda cache for this property [read] of entity [...NotificationEntity]
### NotificationEntity::getReadAt →  OK
```

**根因**：MP 的 `LambdaXxxWrapper` 解析 `Entity::method` 时按 JavaBean 约定剥掉
`get`/`is` 前缀再拿**属性名**查 `TableInfo.propertyMap`。
字段叫 `isRead` ⇒ 属性名 `isRead`、getter `isRead()` ⇒ lambda 解析成属性 `read` ⇒ 落空。

**影响面**：`NotificationMapper` 的 `countUnread` / `markAllReadByRecipient` / `markRead`
三处都用 `NotificationEntity::isRead` 构造条件 ⇒
**`listNotifications(unreadOnly)` / `getUnreadSummary` / `markAllNotificationsRead` /
`markNotificationRead` 四条业务路径一调即抛异常**。

**为什么长期没暴露**：message-rpc **此前没有任何一条会真正执行 SQL 的测试**，
全部是纯 Mockito mock —— mock 数据层不装配 MP 元数据，天然测不到这条（§3.3）。

**修法**：字段改名 `isRead` → `read` + `@TableField("is_read")` 显式列映射，
getter 仍为 `isRead()`。实测改后：

```
### keySet=[read->is_read, readAt->read_at, recipientId->recipient_id]
### E::isRead → (is_read = ? AND recipient_id = ?)   ✅
```

**新增防线**：`MessageMarkNotificationReadTest#entityFieldMustBeNamedReadNotIsRead`
用反射锁死字段名 + `@TableField` 值（静态引用在编译期看不出问题，只有真正解析 lambda 时才炸）。

> **这条的教训**：`@TableField` / Lambda wrapper 属于 §3.3 点名的「运行期动态装配、不报错」
> 那一类，但它比 `@Transactional` 更隐蔽 —— `@Transactional` 失效是**静默降级**，
> 而 lambda 解析失败是**立即抛异常**。也就是说：一个模块只要从来没跑过真实 SQL，
> 这类问题就<b>完全不会出现在任何测试报告里</b>。判据不是「有没有报错」，
> 而是「<b>有没有真的执行过这条 SQL</b>」。

### 6.2.2 `service/users/admin` 语义审计修复清单（issue #71186443，2026-10-21）

审计范围：Go `service/user/admin/{rpc,api}` 14 logic + 12 api logic + model + middleware
↔ RAG `service/users/admin/{admin-rpc,admin-api,admin-proto}`。结论 **SEMANTICALLY NOT EQUIVALENT**。
**已修 13 项（9 MISMATCH + 2 wire 契约 + 1 处随修复连带纠正的文档病灶 + 1 服务共享底层 bug）**，
留档 4 项见 §3.4.3 的 N-3/N-4/N-5/N-6。

| # | 类别 | 位置 | Go 行为 | 修法 |
|---|---|---|---|---|
| DRIFT-001 | MISMATCH | `AdminSeederBootstrap` | seeder 用包内常量 `defaultAdminPassword = "admin"`（`svc/servicecontext.go:18`），**不读** `Config.System.DefaultPassword` | seeder 不再依赖 `AdminRpcProperties`，改用 `AdminDomainService.DEFAULT_ADMIN_PASSWORD`；`resetUserPassword` **仍读配置**（Go 侧本就是两个不同来源，已加防回退测试锁住） |
| DRIFT-002 | MISMATCH | `AdminGetUserService` | api 层写死 `Found: true`（`getuserlogic.go:59`）；rpc 层确实不设（`false`） | 显式 `setFound(true)`；同步修正 `AdminServiceGrpcImpl.getUser` 里「参照了错误层」的注释 |
| DRIFT-003 | MISMATCH | `AdminUpdateSelfService` | `NotFound → 1003`（`updatelogic.go:61`） | 补 `NOT_FOUND` case（此前落 default `1015`，与 user-api DRIFT-003 同类） |
| DRIFT-004 | MISMATCH | `update` / `resetuserpassword` / `unbanuser` | 这 3 个端点 `Internal → 5003 查询数据库失败`，其余 9 个 → `5001` | 三处改 5003，并加**防回退测试**断言 `banuser` 仍是 5001 |
| DRIFT-006 | MISMATCH | `AdminDomainService.getUserList` | `if len(keyword) > 0`，**不 trim**（`model/admin.go:152`） | 去掉 trim |
| DRIFT-007 | MISMATCH | 同上 + `GetUserListReq` | API 层 `form:"page,default=1"` 只在**参数缺失**时生效；rpc 层不兜底，`page=0` → 负 OFFSET → PG 报错 → `Internal` | default 上提到 api 层（DTO 改 `Long` 以区分 null / 显式 0，新增 `pageOrDefault()` / `pageSizeOrDefault()`）；rpc 层删掉归一化 |
| DRIFT-008 | MISMATCH | `AdminDomainService.createAdmin` | 无 username 空校验，空串可建号 | 移除 `INVALID_ARGUMENT` 分支（此前该 code 在 api 层无 case ⇒ 报成 `1015 服务繁忙`） |
| DRIFT-009 | MISMATCH | `AdminJwtAuthInterceptor` | go-zero `WithJwt` 把 Jwt middleware 追加到切片尾部 + `withMiddlewares` 反向包裹 ⇒ 实际链路 `CheckBlacklist(Jwt(handler))`，**黑名单先执行** | 两段拆开，黑名单前置；各自 fail-open 行为逐条对齐 |
| DRIFT-010 | MISMATCH | `AdminSeederBootstrap` | `NewServiceContext` 在 `zrpc.MustNewServer` **之前**跑 seeder | `@EventListener(ApplicationReadyEvent)` → `@PostConstruct`（早于 `finishRefresh` 的 `SmartLifecycle.start()`）。**连带修正**：注入 `SnowflakeBean` 强制 bean 创建顺序，否则 `@PostConstruct` 阶段会用静态兜底 workerId产出与后续不同 workerId 的 ID，存在撞号风险。**再连带**：补 `@DependsOnDatabaseInitialization`（实测证明框架已通过传递链保证该顺序，本注解是冗余保险而非修复 —— 见下方⚠）|
| A-01 | wire | `AdminListUserService` + `GetUserListResp` | 空结果 → `"list": null`（Go 是 nil slice，且 `json:"list"` **无 omitempty**） | Service 侧空结果置 null；DTO 侧摘掉 `@JsonInclude(NON_NULL)`（否则 null 会变成「字段消失」） |
| A-02 | wire | `AdminApiSupport.fromProto(AdminInfo)` | Go `types.AdminInfo.Email` 是 `json:"email"`（**无 omitempty**）⇒ 恒输出 `"email":""` | 总是 setEmail。**注意 `UserInfo` 侧不改**：Go `types.UserInfo.Email` 带 `omitempty`，空时应省略 |
| DRIFT-FRAMEWORK-001 | 共享 bug | `HttpMiddleware$ObservabilityResponseWriter.setStatus/sendError` | 此前只缓存到 `this.status`，等 `chain.doFilter` 跑完再 `flushTo(dst)` 才写底层。**实测**：Spring `HttpEntityMethodProcessor.handleReturnValue` 末尾调 `outputMessage.flush()` → `wrapper.flushBuffer()` → 底层 `MockHttpServletResponse.setCommitted(true)`；后续 `flushTo` 的 `dst.setStatus(401)` 被 `MockHttpServletResponse.setStatus(int)` 的 `if (!isCommitted())` 守卫吞掉。**结果**：`@ExceptionHandler(AuthenticationException.class)` 返回 `ResponseEntity.status(UNAUTHORIZED)` 也只输出 HTTP 200 + `{code:1006}` —— 整条 admin-api / user-api 的「鉴权短路 → 401」在所有运行期看起来全绿，实则全部被降级。这是把 DRIFT-009 与 N-1「推到 401」的修复完全埋没的根因 | setStatus / sendError 立即 propagate 到 underlying（`((HttpServletResponse) this.original).setStatus(sc)`，每次**同时**更新 `this.status`，因为 timeout/canceled 分支的合成响应仍走 `rec.statusCode()` 读）；body 仍 buffer；setHeader 仍 buffer（不影响 commit 状态）。**全栈验证**：admin-api 83 + admin-rpc 36 + user-api/user-rpc 5 modules 全绿 |

**回归测试**：`AdminSemanticDriftRegressionTest`（admin-rpc，12 条）+ `AdminSemanticDriftApiRegressionTest`（admin-api，24 条）
+ `AdminApiMvcPipelineTest`（admin-api，11 条；**首次把真实 MVC 管线 + 真实 Redis 容器 + 真实拦截器 advice 链纳入回归**，同时锁住 DRIFT-FRAMEWORK-001 / DRIFT-009 / N-1）。
其中 DRIFT-006/007 的断言打在**MyBatis-Plus 实际生成的 SQL 片段与绑定参数**上，不是 mock 返回值 —— 后者无论 wrapper 里装什么都照常返回预设值，测试必然假绿（§3.3）。
**容器集成测试**：`AdminRpcContainerWiringTest`（admin-rpc，7 条，真实 PG 16 + 真实 Spring 容器），见 §6.2.3。
验证：`mvn -pl admin-rpc,admin-api -am test` + `package` 全绿（**admin-rpc 36 / admin-api 83** / users-common 3 / service-common 78，均为 2026-10-21 实测计数；新加的 11 条被 DRIFT-FRAMEWORK-001 修复前全部因 HTTP 200 假绿，修复后全部断言 401 / 2003 通过）。

**效力边界（已部分更新）**：DRIFT-001 / 006 / 007 的行为已在真实 PG 上验证（§6.2.3 A-U2 / A-U3）；
**DRIFT-009 的鉴权链与 HTTP 401（N-1）现已穿过真实 MVC 管线 + 真实 Redis 容器验证**（`AdminApiMvcPipelineTest`）。
仍缺的：DRIFT-008 的**建号路径**尚未在真库上跑过（需要真实 invite 行 + 并发消费）。

> **⚠ DRIFT-010 提前 seeder 后会丢 Flyway 时序吗？—— 曾误判，实测已定论（2026-10-21）**
>
> **结论：不会丢。框架本来就保证顺序。**
>
> 把 seeder 从 `ApplicationReadyEvent` 提前到 `@PostConstruct` 后，它会在
> `finishBeanFactoryInitialization` 期间创建；而 Flyway 迁移跑在 `flywayInitializer`
> bean 创建时。单看 bean definition 注册顺序（组件扫描早于自动配置）**看起来**会先跑 seeder。
> 当时据此写下断言：「没有机制把二者串起来，不加约束会报
> `relation "admin_admin" does not exist` 并让容器启动失败」。
>
> **那个断言是错的 —— 而它恰好违反 §3.2/§3.3：凭静态推理就断定了一个框架机制。**
> 加 Testcontainers 集成测试后做负向验证：把 `@DependsOnDatabaseInitialization`
> 注释掉重跑，**7 条仍全部通过**。真正原因是：
>
> MyBatis-Plus 自带 `com.baomidou.mybatisplus.autoconfigure.MybatisDependsOnDatabaseInitializationDetector`
> （继承 `AbstractBeansOfTypeDependsOnDatabaseInitializationDetector`，监听类型为
> `org.mybatis.spring.SqlSessionTemplate`），Spring Boot 的
> `DatabaseInitializationDependencyConfigurer` 会自动给匹配的 bean 打上「依赖数据库初始化」标记：
> ```
> AdminSeederBootstrap → AdminMapper → SqlSessionTemplate（被自动标注） → flywayInitializer 先跑
> ```
>
> **处置**：注解**保留**在 seeder 上，但定位从「必需的修复」降级为「不依赖传递链的冗余保险」——
> 传递链依赖「MyBatis-Plus 提供 detector」+「Boot 收集 detector」两个前提，
> 任一变化（换 ORM、Boot 大版本升级、detector 被裁掉）都会让 seeder 静默失去保护。
> javadoc 已按实测结论重写，不再声称它是必需的。
>
> **这条教训比结论本身更重要**：上一轮正是用「读代码 → 推理 bean 生命周期 → 断言会失败」
> 得出的结论，而 §3.3 明写「不得凭注解名称断定机制已启用」。
> **推理出的机制风险必须配一条能真触发的测试去证实或证伪，否则只能记 UNRESOLVED，不能写成断言。**

### 6.2.3 `service/users/admin` 的 UNRESOLVED（2026-10-21 收尾）

> 下列状态由 `AdminRpcContainerWiringTest`（真实 PostgreSQL 16 容器 + 真实 Spring 容器，7 条）
> 与 `AdminApiMvcPipelineTest`（admin-api，11 条；真实 Redis 容器 + 真实 Spring 容器 + 真 MVC 管线）
> 两套实测产出，非静态推导。**4 项原始 UNRESOLVED 已全部关闭**（A-U0 两端、A-U1、A-U2、A-U3），
> 其中 A-U2 已于 2026-10-21 由 `tools/difftest` 双端运行时对拍补齐（见 §6.2.5）。

| # | 项 | 状态 | 证据 / 剩余未知点 |
|---|---|---|---|
| A-U0 | 真实 Spring 上下文测试 | **已关闭（admin-rpc + admin-api 两侧）** | admin-rpc 由 `AdminRpcContainerWiringTest`（7 条，真 PG）关闭；admin-api 由 `AdminApiMvcPipelineTest`（11 条，真 Redis 容器 + `@SpringBootTest(AdminApiApplication)`）关闭，本批**新增**。该测试同时暴露并锁住一个 **共享底层 bug**（DRIFT-FRAMEWORK-001，`HttpMiddleware` 的 status propagation 与 Spring 的 commit 时序冲突：所有 `ExceptionHandler → 401 / 5003` 在 MockHttpServletResponse 的 `!isCommitted()` 守卫下被降级为 200） |
| A-U1 | Go 侧 uid **格式非法**（非缺失）时的 HTTP 状态码 | **已关闭**（2026-10-21 + §3.4.3 后补行补录） | 已读 go-zero v1.10.2 源码原文：admin 服务 yaml 未注册 `httpx.SetErrorHandler` ⇒ `buildErrorHandler` 返回 `nil` ⇒ `doHandleError` 走到 `http.Error(w, err.Error(), http.StatusBadRequest)` ⇒ Go 输出 **HTTP 400 + text/plain**（无 JSON envelope）。与 user-api 不可跨模块套用结论——user-api 走的是另一个 handler 配置。这里由 `AdminApiMv*cPipelineTest` 里未覆盖，**不进入回归网**——若日后有人加 uid 路径的端到端测试，按 Go 现状写 `uid=abc → HTTP 400 + text/plain` 即可 |
| A-U2 | 两端运行时对拍 | **已关闭（两侧，2026-10-21）** | RAG 侧实证：空库上 seeder 造出的 admin 用 `admin/admin` 登录通过、用 `123456` **不**通过（DRIFT-001 关闭）；重复执行 seeder 不产生第二行（幂等）；`resetUserPassword` 用的是配置值 `123456` 而非 seeder 常量（两个来源未串）。Go 侧由 `tools/difftest` 补齐：两套栈同时起、各跑同一串 39 步请求序列，逐字段比对响应。最新一次结果 **39 步 / 39 同 / 0 已签字 / 0 未预期差异**（DRIFT-NEW-016 对齐后，原「36 同 / 3 已签字」的 3 条已消解）。**⚠️ 效力边界见 §6.2.6：39 步里有 13 步只比状态码不比 body，且是 HTTP wire 层对拍、gRPC 层未直接对拍** |
| A-U3 | `getUserList` 的 LIKE 双重包裹 | **已关闭**（双端对拍二次确认） | 真 PG 上实测：RAG 的 `%%alice%%` 与 `%%%alice%%%` 命中集合完全相同，且与原生 `LIKE '%alice%'`（Go 写法）结果集一致；已覆盖 username 命中 + email 命中（OR 分支）两个来源。判定为 IMPLEMENTATION DIFFERENCE 而非漂移，已实证。**2026-10-21 双端对拍二次确认**：`admin.getuserlist` 三种形态（精确匹配 / 前缀 `dt_` / 匹配不到）在两侧返回完全相同的 `list` 与 `total`，含 `extra_info` jsonb 内容逐字段一致 |

##### 搭建容器测试时踩到的环境坑（换机器 / 换网络时必读）

| 坑 | 现象 | 解法 | 出处 |
|---|---|---|---|
| **Ryuk 拉不下来** | `Can't get Docker image: RemoteDockerImage(testcontainers/ryuk:0.14.0)`，发生在任何 `@Container` 之前，整类 ContainerFetch | surefire 注入**环境变量** `TESTCONTAINERS_RYUK_DISABLED=true`（见 admin-rpc/pom.xml）。**`testcontainers.properties` 与 `-D` 都无效** —— Testcontainers 2.x 的 `ResourceReaper.instance()` 字节码里就一句 `System.getenv(...)` | 已实测 |
| **`disabledWithoutDocker` 误判** | Docker 明明可用，日志显示已 Connected，但全部用例 Skipped | 去掉该参数。2.x 的 `DockerAvailableDetector` 在本机判定为不可用，而静态 `@Container` 字段初始化早于条件求值，所以日志顺序具有欺骗性 | 已实测 |
| **gRPC server 关不掉** | 写 `grpc.server.enabled=false` 无效，仍起 server 去抢 8081 → `BindException: Address already in use` | 改 `grpc.server.port=0` 绑临时端口。devh 3.1.0 的 `GrpcServerLifecycle` **没有** `@ConditionalOnProperty("grpc.server.enabled")`，无开关可用 | 已实测 |
| **`PullPolicy` 位置变了** | 2.x 里 `PullPolicy` 在 `org.testcontainers.images`，且**没有** `neverPull()` | 用默认的 `DefaultPullPolicy`（本地已有镜像则不拉）。测试用的 `postgres:16-alpine` 需本地预先存在 | 已实测 |

> ⚠ 关掉 Ryuk 的代价是**容器不再自动回收**，CI 跑完可能残留。
> Docker Hub 可达后删掉 admin-rpc/pom.xml 里那段 surefire 配置即可恢复。

### 6.2.5 双端运行时对拍 `tools/difftest` 的产出（2026-10-21）

首次把 Go 与 Spring Boot **同时起进程、喂同一串请求、逐字段比对响应**。
产物在 `tools/difftest/`，读法与踩过的坑都写在那里的 README 里。
最新一次：**39 步 / 36 同 / 3 已签字 / 0 未预期差异**。

它立刻抓出了三个静态审查完全看不到的东西 —— **前两个是 RAG 的真缺陷，不是对拍环境的产物**。

| ID | 级别 | 内容 | 证据 |
|---|---|---|---|
| **DRIFT-NEW-014** | **P0 · 配置缺陷 · 已修（2026-10-21 当日）** | **`user-api` 连不上 `user-rpc`。** `user-api/application.yml` 的 `grpc.client.user-rpc` **漏了 `negotiation-type: plaintext`**，而 devh `grpc-client-spring-boot-starter` 3.1.0 的 `grpc.client.GLOBAL.negotiation-type` **默认值就是 TLS**（证据：jar 内 `META-INF/spring-configuration-metadata.json` 的 `defaultValue`）。于是 user-api 发 TLS ClientHello，user-rpc 的 netty 报 `Http2Exception: HTTP/2 client preface string missing or corrupt. Hex dump: 16030301...`（`0x16` = TLS handshake record），所有经 user-rpc 的业务码塌成 `1015 服务繁忙` | `admin-api/application.yml` **有**这一行且带 DRIFT-NEW-008 注释解释为什么必须有 —— 当年 admin 侧踩过并修了，**user 侧单纯被漏掉**。这不是对拍环境特有的：默认值在任何环境都一样 |
| **DRIFT-NEW-014 修法** | — | `user-api/application.yml` 补 `negotiation-type: plaintext`（含字节码级出处注释）。回归网 `UserApiGrpcNegotiationTest`（3 条）：把 `grpc.client.user-rpc.address` 覆盖成 `static://127.0.0.1:<动态端口>`，起一个**真 plaintext gRPC server**，用容器里**真实的** `UserRpcClient` 打一次**真实调用** —— 走的是 devh `ShadedNettyChannelFactory` 的生产代码路径，不是测试里写死 plaintext。外加一条 **TLS 负向对照**（同一个 server 用 TLS 连必须失败），保证正向用例不是假绿。**已实证能真红**：临时删掉那行配置后，该用例报 `NotSslRecordException: not an SSL/TLS record`、pipeline 里出现 `SslHandler` —— 正是生产上 `HTTP/2 client preface string missing` 的镜像（同根因、客户端侧视角） |
| **DRIFT-NEW-015** | **P0 · 结构性缺陷 · 已修（2026-10-21，用户拍板去掉 Flyway）** | **Flyway 版本号撞车 ⇒ `admin-rpc` 根本无法启动。** 两个模块各自维护 `classpath:db/migration`，`V1/V2` **版本号相同、内容不同**：`user-rpc` = `V1__create_user` / `V2__create_user_avatar_history`；`admin-rpc` = `V1__create_admin` / `V2__create_admin_invite` / `V3__create_user`。`flyway_schema_history` 挂在 schema 上、**一个 schema 只能有一本账**，共用库时后启动的要么报 `version (3) is newer than the latest available migration (2)` 并**静默跳过自己的迁移**（进程照样 Started，表却是空的），要么 checksum mismatch 直接启动失败 | 而 `admin-rpc` **按设计必须**与 `user-rpc` 共用 `users_user` —— 这是它自己 `V3__create_user.sql` 的注释写明的（「admin-rpc 也读写 users_user 表……用 `IF NOT EXISTS 保证幂等：user-rpc 启动时已经建过表」）。⇒ **要满足设计意图就必须共用库，共用库就撞账本，无解**。§4.1 早就警告过这点，现在有实证了 |
| **DRIFT-NEW-015 修法** | — | **去掉 Flyway，改为幂等重放 DDL**：4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`，由各 rpc 的 `bootstrap/*SchemaBootstrap` 在 `@PostConstruct` 调 `SchemaInitializer` 原样重放；两个 rpc 的 flyway 三件套依赖全删；`AdminSeederBootstrap` 补显式 `@DependsOn`。详见下方「已定案」小节 |
| **DRIFT-NEW-016** | P0 · 语义差异 · **已修（2026-10-21，用户拍板「向 Go 对齐」）** | **Go 有必填字段校验，RAG 没有。** go-zero 的 `httpx.Parse` 走 `core/mapping`，只认 `,optional` 标签，**`,omitempty` 不算 optional**（`unmarshaler.go:966-969` 走 `!opts.optional()` 分支直接 `newInitError`）。缺字段 ⇒ `HTTP 400 + text/plain + field "xxx" is not set` | 取证脚本 `tools/difftest/probe-go-required-fields.py`（真 Go 进程，2026-10-21）。修法与完整规则见下方「DRIFT-NEW-016 的处置」小节 |
| **DRIFT-NEW-016 修法** | — | **新增 `users-common/http/` 四件套**（`GoStyleRequiredFields` 接口 / `GoRequiredFieldException` / `GoStyleRequired.field()` / `GoStyleRequest.normalize()`），14 个 DTO 显式实现必填校验、14 个 controller 端点走 `GoStyleRequest.normalize`，两份 `GlobalExceptionHandler` 各加一个 `@ExceptionHandler(GoRequiredFieldException)` → 复用既有 `goStylePlainTextBadRequest`。**同时推翻了 `UserController` / `AdminController` 类级 javadoc 里的「空 body 是 no-op、不报错」这条错误论证**（详见下方小节） |

> **为什么 DRIFT-NEW-015 至今没被发现**：RAG 的 `docker-compose.yaml` 里**只有
> `user-rpc` / `user-api` 两个服务**，根本没有 `admin-rpc` / `admin-api`。
> 单跑 user 域时账本永远是自洽的；admin 域从未被编排放上过，
> 冲突自然一次都没露过。**这不是「还没测」，是「根本没部署过」。**

##### DRIFT-004 的处置：**已定案（用户 2026-10-21 签字「对外对齐 Go，但保留可观测性」）· 已实施**

**决策**：对外行为与 Go 逐字一致（都报密码错），但**额外**把「hash 格式非法」记进
log 与 OTel span。理由是 §1「行为以 Go 为准、表达以 Spring 为准」：
**错误码属行为必须对齐，指标属表达可以比 Go 更强**。Go 侧这一路**完全静默**。

**实测行为谱**（Spring Security **7.0.7** + `spring-security-crypto`，真跑非推断）：

| 输入形态 | 修复前 | Go | 修复后 |
|---|---|---|---|
| 合法 hash + 密码匹配 | `true` | `true` | `true` |
| 合法 hash + 密码不匹配 | `false` | `false` | `false` |
| 空串 hash | `false` | `false` | `false` |
| `"abc"`（完全不是 bcrypt） | `false` | `false` | `false` |
| `"$2a$10$tooshort"`（截断） | `false` | `false` | `false` |
| **`"$2a$99$…"`（cost 越界）** | **抛异常 → `500+5001`** | `false` | **`false`** |
| `"$2b$"` / `"$2y$"` 合法 hash | `true` | `true` | `true` |
| null hash / null 明文 | `false` | `false` | `false` |

> **⚠️ 本次实测推翻了两个广泛流传的猜测，下一轮审计不要重新"发现"它们**：
> 1. **触发面远窄于原留档**。原留档写「对**任何** bcrypt 错误抛异常」，
>    实测**只有 cost 字段越界**才抛。Spring 的
>    `BCRYPT_PATTERN`（`\A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}`）
>    在 `matchesNonNull` 里把绝大多数畸形 hash 挡在 `BCrypt.checkpw` 之前并**返回 false**。
> 2. **「RAG 不认 `$2b$`」是伪漂移**。`BCRYPT_PATTERN` 的 `(a|y|b)?` 已覆盖
>    `$2$`/`$2a$`/`$2b$`/`$2y$`，与 Go `decodeVersion`（只判 major ≤ `'2'`）接受的集合相同。
>    老版本 Spring 的 `startsWith("$2a$")` 硬检查在 **7.0.7 已不存在**。

**实现**：`users-common/cryptx/PasswordUtil` 新增 `Outcome` 枚举与
`checkDetailed(raw, hash)`；`check(raw, hash)` 保留原签名但**契约收紧**为「永不抛异常」。
两个 rpc 的登录调用点改用 `checkDetailed`：
- `UserDomainService#login`：`MALFORMED_HASH` 时 `span.setAttribute("auth.malformed_hash", true)`
  + `logger.logBusinessErr`，**对外仍返回 `status=1`**（与 Go 的 `loginlogic.go` 一致）
- `AdminDomainService#login`：`MALFORMED_HASH` 时打一条 `log.error`（该类**没有** span 上下文，
  只有 `log` + `metrics`，见其字段声明），对外仍抛 `UNAUTHENTICATED`

**回归网**（11 条，`mvn clean verify` 全绿）：
- `PasswordUtilGoCompatibilityTest`（users-common，7 条）—— 上表每一行逐条钉死
- `LoginMalformedHashDrift004Test`（user-rpc，4 条）—— 端到端证明「报密码错而非 500」，
  外加「正常密码错不被误吞成 malformed」「正常登录路径未受影响」

**已实证能真红**：临时移除 `checkDetailed` 的 catch 后，
两条用例立刻报 `IllegalArgumentException: Bad number of rounds` ——
正是生产上冒泡成 `500 + 5001` 的那个异常（§3.3 要求）。

**残留的已知反向差异（不修，留档）**：无 minor 的 `"$2$"` 前缀。
实测 RAG 返回 `false`，而 Go 的 `decodeVersion` 在 `sbytes[2] == '$'` 时会接受 ⇒ Go 应返回 `true`。
**不修的三个理由**：① 方向是「RAG 更严格」，登录场景 fail-closed 更安全，不是漏洞；
② 修它要绕过 `BCryptPasswordEncoder` 直接调 `BCrypt.checkpw`，等于放弃模式校验，
为一个几乎不会出现的旧格式牺牲防御；③ `$2$`（无 minor）是 bcrypt 最早的实验格式。
锁定用例 `PasswordUtilGoCompatibilityTest#barePrefixIsFalseInRagButGoWouldAcceptIt`——
它断言的是**当前偏差**，所以哪天要对齐 Go，改的是这条测试的期望值而不是删掉它。

##### DRIFT-NEW-016 的处置：**已定案（用户 2026-10-21 拍板「向 Go 对齐」）· 已实施**

**规则（全部经 `tools/difftest/probe-go-required-fields.py` 真进程实测）**：

```
字段必填  ⟺  tag 不含 ,optional
              AND tag 不含 ,default=
              AND 字段类型是【标量】（Map / Slice / Array 永不计必填）
```

| 输入 | Go 行为 | Go 出处 |
|---|---|---|
| 标量必填字段**缺席**（含**真空 body** 与 `{}`） | `400 field "xxx" is not set` | `unmarshaler.go:966-969` → `newInitError`（`:1199`） |
| 标量字段值 `""`（**json 通道**） | **接受**，继续往下走 | `processFieldPrimitive` → `fillWithSameType`，不查必填 |
| 标量字段值 `null` | `400 field "xxx" mustn't be nil` | `unmarshaler.go:862` |
| Map / Slice 字段缺席 | **接受** | `unmarshaler.go:944-948` 走 `processFieldNotFromString` |
| 带 `,default=` 的字段缺席 | **填默认值**，不报必填 | `unmarshaler.go:918` 先命中 `opts.getDefault()` |

**实测命中的必填字段**（此前注释里标成 optional 的，全部是错的）：
`CreateUserReq.email`、`CreateAdminReq.email` / `invite_code`、
`UpdateSelfReq` 的 username / password / email **三个全是**、
`UpdateUserReq` 的 username / password / email、`GetUserListReq.keyword`、
`SelectAvatarReq.history_id`、`UploadAvatarReq.avatar_url`。
**不**必填的：`user.update` 四个字段（全 `,optional`）、
`GetUserListReq.page` / `page_size`（有 `default=`）、所有 map 字段。

**实现**：`users-common/http/` 四件套 + 14 个 DTO + 14 个 controller 端点。
刻意**不用反射**：反射读字段名是隐式约定，字段改名时编译器不报错、校验静默失效
（§3.3「框架机制必须验证实际生效」的同一类病灶）；每个 DTO 显式写出自己的必填字段，
改名即编译失败。

> **⚠️ 本次同时推翻了 `UserController` / `AdminController` 类级 javadoc 的一条错误论证**
> （与 §3.4.4 DRIFT-009 **完全同型**：引用了真实文件、但把行为读反了）。
>
> 原文写：「空 body ⇒ `withJsonBody` 为 false ⇒ `ParseJsonBody` 走
> `mapping.UnmarshalJsonMap(nil, v)` ⇒ **对 struct 是 no-op、不报错**」。
> **前半句对，后半句错**：`core/mapping/jsonunmarshaler.go:19-21` 的
> `UnmarshalJsonMap(m map[string]any, v any)` 收的是**有类型的 nil map**，
> 传进 `Unmarshal(i any, ...)` 后在 `unmarshaler.go:988` 的 type switch 里
> **命中 `case map[string]any`**（不会落到 `default` 的 `errUnsupportedType`），
> 于是照常 `unmarshalValuer` 遍历全部字段 —— 空 map 下 `getValue` 全部落空，
> 标量必填照样走 `newInitError`。
>
> **实测坐实**：真空 body（Content-Length: 0）与 `{}` 都返回 `400 field "username" is not set`。
>
> **代价**：这个错误结论曾让 22 个端点全部设成 `required = false` + 零值归一化，
> 并被写进 **7 条测试**当作「A-P2 回归」锁住。本批把它们逐条对照 Go 源码 + 探针实测后
> **反转了 6 条**。**这正是 DRIFT-009 末尾那条评审纪律的第二次应验：
> 测试名与注释写得再完整，也必须独立回 Go 源码复核一次。**
> 被反转的 6 条：`AdminApiMvcPipelineTest` 的
> `emptyBodyDoesNotReturn400` / `emptyBodyOnPublicEndpointDoesNotReturn400` /
> `missingUidIs1017EndToEnd` / `emptyUserListIsNullEndToEnd` /
> `updateSelfNotFoundIs1003EndToEnd` / `updateSelfInternalIs5003EndToEnd`，
> 以及 `UserApiMvcPipelineTest` 的 `emptyBodyOnLoginDoesNotReturn400`
> （其中 3 条只是**请求 body 不完整**才 400，补全 body 后仍测原目标；另 1 条
> `/update` 因全 `,optional` **不反转**，留作对照组）。

> **⚠️ 本次同时证伪了 §3.4.3 的 N-3 与 N-6 两条「有意偏离」留档** —— 它们描述的
> RAG 行为**从未存在过**，观测行为其实一直与 Go 一致：
>
> | 留档声称 | 实际代码 | 实际观测 | Go |
> |---|---|---|---|
> | N-3：「RAG 在 `AdminApiSupport.parseUid` **提前拦截 uid=0** → 1017」 | `parseUid` 只拒 null / 空串 / 非数字 / 溢出（`AdminUidParseTest` 5 条），**没有 uid==0 分支**；全仓 grep 零命中 | `uid=0` → 透传 rpc → 查无此人 → **`1003`** | **同样是 `1003`** ⇒ **本就一致** |
> | N-6：「`uid=abc` 经过 `parseUid` 拦截 → 200 + `1017` envelope」 | `GetUserReq.uid` 是 `Long` 且 admin-api **无自定义 ObjectMapper** ⇒ Jackson 在反序列化阶段就抛 `InvalidFormatException`，**到不了 `parseUid`** | `uid=abc` → **`400` + text/plain**（文本来自 Jackson） | **同样 `400` + text/plain**（文本来自 go-zero）⇒ **状态码本就一致**，仅文本不同 |
>
> ⇒ **N-3 与 N-6 应从「有意偏离」降级为「留档与代码不符，已修正」**。
> 这不是改行为，是**改记录**——按 §1「Go 是唯一真源」，RAG 本来就对，
> 是留档把「本该对齐」写成了「有意偏离」，下一个审计 agent 会据此
> **主动把 RAG 改坏**去"恢复"一个从未存在的偏离。防回退测试已同步反转，
> 见 `AdminApiMvcPipelineTest#uidZeroIsPassedThroughAndMatchesGo` 与
> `#nonNumericUidIsRejectedByJacksonNotByRequiredCheck`。

> **残留的已知差异（不修，留档）**：`GetUserListReq.keyword` 在 Go 侧走 **form 通道**，
> 而 `rest/httpx/util.go:37-43` 的 `GetFormValues` 会把空值（`len(v) == 0`）整个丢弃，
> 于是 `keyword=` 退化成「缺席」⇒ 400；RAG 的 keyword 在 JSON body 里，`keyword=""`
> 会被**接受**。两边是否 400 取决于传输通道，而传输通道差异是 §3.4.1 已签字的 wire 契约偏离。
> **刻意不为单个字段引入第二套空串规则** —— DRIFT-010 的教训是
> 「安全规则的第二份实现本身就是缺陷来源」。

##### DRIFT-NEW-015 的处置：**已定案（用户 2026-10-21 拍板「直接把 Flyway 丢掉」）· 已实施**

**方案 A（保留 Flyway + 单一迁移拥有者）曾被实现并验证通过**，两个 rpc 各自对着空库
都能应用完整的 4 份迁移。但用户最终选择更彻底的方案 —— 见下面的取舍说明。

最终落地：

| 项 | 做法 |
|---|---|
| DDL 位置 | 4 份全部收归 `users-common/src/main/resources/db/`：`01_create_user.sql` / `02_create_user_avatar_history.sql` / `03_create_admin.sql` / `04_create_admin_invite.sql`。数字前缀**只保证执行顺序，不表示版本** |
| 执行者 | `users-common` 的 `com.rag.users.common.ddl.SchemaInitializer`（静态工具，非 `@Component` —— 两个 rpc 只扫自己的包，且注释里明确禁止扩 `scanBasePackages`）；由各模块的 `bootstrap/UserSchemaBootstrap` / `AdminSchemaBootstrap` 在 `@PostConstruct` 调用 |
| 失败语义 | 任一 DDL 失败 → 抛 `IllegalStateException` → **进程拒绝启动**（对齐 Go 的 `log.Fatalln`） |
| 时序 | `AdminSeederBootstrap` 显式 `@DependsOn("adminSchemaBootstrap")`。原先后序靠「MyBatis-Plus detector → SqlSessionTemplate → flywayInitializer」这条**传递**链兜住，去掉 Flyway 后整条链消失，必须点名 |
| pom | 两个 rpc 的 `spring-boot-flyway` + `flyway-core` + `flyway-database-postgresql` **三件套全删**。⚠️ 只删 `flyway-core` 不够：`spring-boot-flyway` 在 classpath 上就会让 `FlywayAutoConfiguration` 自动 migrate，然后报 `Found non-empty schema(s) "public" but no schema history table.`（本次实测撞过） |
| 验证 | `UserRpcContainerWiringTest` 12 条 + `AdminRpcContainerWiringTest` 7 条全绿；两份日志里 `schema init` 各打印 4 份脚本全部 applied；整份构建日志 `flyway` 出现 **0** 次 |

##### ⚠️ 丢掉账本丢掉的东西：PG 的 `CREATE TABLE IF NOT EXISTS` **在并发下并不安全**

这是去掉 Flyway 后**必须自己补上**的一层，也是本轮唯一一处「单元测试全绿、端到端才炸」的问题。

实测现象（2026-10-21 双端对拍）：user-rpc 与 admin-rpc **同时**启动、各自对同一个空库
执行 `CREATE TABLE IF NOT EXISTS users_user`，后到者直接失败：

```
org.springframework.jdbc.datasource.init.ScriptStatementFailedException:
  Failed to execute SQL script statement #1 of db/01_create_user.sql
Caused by: org.postgresql.util.PSQLException:
  ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
```

**为什么单测测不到**：`UserRpcContainerWiringTest` / `AdminRpcContainerWiringTest`
每次只起一个服务进程，并发窗口根本不存在。
只有 `tools/difftest` 同时起两套栈才会复现 —— 这正是双端对拍存在的意义。

**修法**：`SchemaInitializer.applyAll` 在跑 DDL 前先取 **PG 会话级 advisory lock**
（`select pg_advisory_lock(8675309)`），跑完显式 `pg_advisory_unlock`。
阻塞语义、不需要额外表、不需要事务、连接断开自动释放 —— 比模拟 Flyway 的行锁轻得多。
键是 `SchemaInitializer` 里的常量，**两个模块必须用同一个**。

> Flyway 原本用 schema history 上的行锁天然挡住了这一层。去掉它就等于去掉这层保护，
> 这是「丢掉有状态账本」的代价之一 —— 与 §6.3 问题 1 记的「失去升级路径」并列。

##### 「为什么 Go 没有这个问题」—— 准确答案（别再问第二遍）

这是本轮被问到的问题，实测 + 读源码后的结论是：**要分成三件事，别混谈。**

| 问题 | Go 有没有 | 依据 |
|---|---|---|
| **① 账本撞车（Flyway 特有）** | **结构性没有** | GORM `AutoMigrate` 无状态：没有版本号、没有 `flyway_schema_history`、没有 checksum、没有 `baseline`。DRIFT-NEW-015 是**本仓库自己引入的复杂度造成的**，不是 PG 或 Go 的固有问题 |
| **② PG 的 DDL 并发竞态** | **有，只是窗口窄得多** | GORM 的建表 SQL **没有** `IF NOT EXISTS`（`migrator.go:309` 就是 `tx.Exec("CREATE TABLE ? (...")`），保护只来自 `HasTable` 先查（`migrator.go:124` 的 check-then-act）。并发失败形态会是 `relation already exists`，与 RAG 的 `pg_type_typname_nsp_index` 不同 |
| **③ 真实部署里两个服务同时对空库启动** | **两边都不发生** | Go 与 RAG 的 `docker-compose` **都只有基建、没有应用服务**（Go 侧连 user-rpc / admin-rpc 都没有）。首次初始化通常由单个服务完成 |

**② 的实测**：把 Go 的 user-rpc 与 admin-rpc **同时**对着一个全新空库启动（刻意不 sleep 错开），
连续 5 轮全部成功，4 张表齐活（`users` / `user_avatar_history` / `admins` / `admin_invites`）。
脚本见 `tools/difftest/exp-go-concurrent-automigrate.sh`。

**为什么 Go 能扛住（推测，未进一步验证）**：两个服务的模型顺序不同 ——
user-rpc 是 `AutoMigrate(&User{}, &UserAvatarHistory{})`，**第一个就碰 `users`**；
admin-rpc 是 `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`，**`users` 排第三**。
admin-rpc 要先建完 `admins` + `admin_invites` 才轮到 `users`，
那时 user-rpc 早已建好、它的 `HasTable(users)` 返回 true 直接跳过。
**靠的是时序侥幸，不是机制。**

> ⚠️ 所以 RAG 这边加的 advisory lock **比 Go 更强**，不是多余的：
> Go 那个是「时序恰好错开」，RAG 若照抄同样依赖错开，一旦模型顺序或启动时序变化就会炸。
> 锁是机制，不靠运气 —— 这条要守住。

**为什么丢而不是留**（用户的取舍）：项目仍在重构期、**无任何已部署环境**
（`docker-compose.yaml` 里连 admin-rpc 都没有），表结构还会继续动 ——
升级路径的价值要等到第一次 `ALTER` 才兑现，而那时只有一张库要改，代价可控。
更接近 Go（Go 用的是无状态 `AutoMigrate`），且去掉了一整类约束
（版本号唯一、checksum 稳定性、跨进程状态）。

**已知代价（第一次 `ALTER` 之前可以无视）**：脚本要同时承担「建表」与「升级」两件事，
只能到处加 `ADD COLUMN IF NOT EXISTS`；也没有任何地方记录「这张表现在该长什么样」。
**第一次 `ALTER` 之前必须重新引入版本化机制。**

被否决的两个方向（记录下来，避免下一个 agent 重提）：

| 方案 | 为什么没选 |
|---|---|
| **保留 Flyway + 单一迁移拥有者** | 技术上可行且已验证，但保留了「有状态账本」这一整类约束。**已被用户否决** |
| **拆库 + 跨域 RPC** | 直接违反设计意图（Go 两个服务都直连同一库建 `users_user`），改动面最大 |

##### ⚠️ 已被实证证伪的「最省事方案」（别再试第二次）

看上去最该先试的是「给 admin-rpc 加两个 Flyway 开关，让它容忍 user-rpc 的账本」：

```bash
--spring.flyway.validate-on-migrate=false --spring.flyway.out-of-order=true
```

**它行不通，而且会让账本更脏。** `tools/difftest/exp-drift015-flyway-flags.sh` 用真 jar +
真 PG 16 跑过一遍，实测终态：

```
账本 = [ 1|create user,  2|create user avatar history,  3|create user ]
        ↑ user-rpc 写的              ↑ user-rpc 写的         ↑ admin-rpc 的 V3（与第 1 行语义重复）
✗ admin_admin   缺失   ← V1__create_admin.sql        根本没跑
✗ admin_invite  缺失   ← V2__create_admin_invite.sql  根本没跑
admin-rpc 启动失败：relation "admin_admin" does not exist
```

机理：Flyway 只应用「pending」的迁移。admin-rpc 的 V1/V2 版本号已被 user-rpc 占用，
于是被判定为**已应用而静默跳过** —— 校验开关只关掉 checksum 检查，管不了这一步。
结果 admin 域两张核心表永远建不出来，`AdminSeederBootstrap`（`@PostConstruct`）起来就炸。
更糟的是账本里多了一条 `version 3 = create user`，把 schema 永久钉在错误版本上，
日后想正修还得先人工清账本。

> **教训**：这正是 §3.3 说的「推理出的机制风险必须配一条能真触发的验证去证实或证伪」。
> 本条最初只是推理（"两个开关应该能糊过去"），实测直接推翻。

> 在用户拍板前，**不得**由单个 agent 自行选一条实施 —— 这正是 §3.4.2 纪律针对的情形：
> 「按政策应当对齐、但对齐会让 RAG 变差」的真实张力。
> 对拍 harness 里那条绕法已经在 `initdb.sql` 的注释里写明是权宜之计，不要当成既定结论。

##### 附带发现：冻结 Go 仓库的配置占位符不生效（**不改，只留档**）

`Sea-RideTheWind_golang/service/user/user/rpc/etc/user.yaml:13` 写的是
`Password: "${POSTGRES_PASSWORD}"`，`admin/rpc/etc/admin.yaml:14` 的 `DataSource`
更是整条 DSN 都用 `${POSTGRES_USER}:${POSTGRES_PASSWORD}@${POSTGRES_ADDR}:...`。
但四个入口都是 `conf.MustLoad(*configFile, &c)`（`user/rpc/user.go:29`、
`admin/rpc/admin.go:30` 等），**没有传 `conf.UseEnv()`**；
而 go-zero v1.10.2 只在 `opt.env` 为真时才展开（`core/conf/config.go:62-63`
的 `os.ExpandEnv`、`core/conf/properties.go:58-59` 同理）。

⇒ **这些占位符不会被展开**，密码会被当成字面量字符串 `${POSTGRES_PASSWORD}`，
SASL 认证必然失败。真源已冻结（§2 不接受写入），**只在 `tools/difftest/conf/go/*.yaml`
的副本里换成了字面值**。

记这一条是因为：**任何「起 Go 进程跑一遍」的验证都会先撞上它**，
而报错信息是 `password authentication failed`，看起来像密码写错了，不像配置没展开 ——
很容易被误诊成对拍脚本的问题。

### 6.2.1 本次从本表移出（已修或已定案）

| 原优先级 | 项 | 状态 |
|---|---|---|
| ~~P0~~ | ~~**B-1：rpc 超时无法取消在途 IO**~~ | ✅ **2026-10-21 已关闭**（issue #71187373 实验 → #71187874 实施） | 三层证据：① 纯 JDBC 层 `probe-jdbc-cancel.java` 证明 **interrupt 对等锁查询零效果**（照常返回、11014ms）、**`setQueryTimeout` 有效**（2034ms 抛 `SQLState=57014`，与 Go ctx cancel 同一条服务端语义且连接保可用）；② 同日同场端到端；③ 手段定性（`socketTimeout` 会让连接报废，语义相反，不可用）。**实施**：`DeadlineQueryTimeoutInjector`（按 `DeadlineState.remainingMillis()` 注入）+ `SqlTimeoutStatusTranslator`（57014→DEADLINE_EXCEEDED）+ 46 处 `catch (StatusRuntimeException) { throw e; }`（user-rpc 17 + admin-rpc 29）。**api 层零改动** —— `DEADLINE_EXCEEDED` 本就落 `mapRPCStatusToCode` 的 default → 1015。**结果**：客户端 3170ms→**2223ms**（Go 2208ms）、响应体 `text/plain`→**`{"code":1015}`**、PG 查询滞留 4303ms→**176ms**（Go 182ms）；difftest 39/39 unexpected=0；全仓 383→**415 条** 0 failures。⚠️ 过程中发现更大前置缺陷 —— gRPC `ServerInterceptor` 从未挂到 server 上，连带 **DRIFT-018 的 24 处 `checkDeadline()` 此前全是死代码**（见上一行二次更正）。详见 §3.4.1「B-1 补记」 |
| ~~P0~~ | admin-api 默认配置下起不来（`WeakKeyException`） | ✅ **已修**（commit `71be288`）。`AdminJwtAuthInterceptor.java:67` 与 `JwtAuthInterceptor.java:76` 均已走 `JwtUtil.padToHmacKeySize(...)`；`JwtUtil.java:44` 已是 `public static`——即原表建议的「提成 `users-common` public 方法」方案已实施。**本行此前一直挂在 P0 未修，是过期信息** |
| ~~P2~~ | user-api 4 项清理 | ⚠️ **半修**。① 4 个端点 `@RequestBody(required=false)` **已完成**（`UserController.java:118/126/137/153`）；② 「删 `codeFromRPCError` 死代码」**未完成，且原文把位置写错**——它在 **admin-api**（`AdminApiSupport.java:66`），不在 user-api。剩余部分已并入本表 P3 |
| ~~P3~~ | Flyway 迁移归属 | ✅ **已定案** → §6.3 问题 1 |
| ~~P3~~ | `admin-rpc` 边界缺失 | ✅ **已定案**（不转发，与 Go 一致）→ §6.3 问题 2 |

### 6.2.6 `service/users` 的**证据边界台账**（2026-10-21 新增）

§3.4.4 末尾那条新纪律管的是「留档声称 RAG 的行为是 X ⇒ 必须有断言 X 的运行测试」。
**本节管的是它的反面：证据缺口本身必须台账化。**
动机很具体 —— 一个 agent 跑完 `tools/difftest` 看到「39/39 全绿」，
若不知道下面第 1 条，就会以为 `service/users` 已经验证完了。

**不要把本节的条目当成「已知缺陷」读**：这里列的绝大多数**不是代码写错了**，
而是「目前还没有能证明它对（或不对）的运行证据」。

| # | 缺口 | 现状 | 要补什么 |
|---|---|---|---|
| **E-1** | **difftest 有 13/39 步只比状态码、不比 body** | `diff.py:41` 的 `KNOWN_STATUS_ONLY` 白名单（13 条：6 条 N-1 鉴权、2 条畸形 JSON、1 条 `user.delete`、4 条必填校验）。判定逻辑在 `diff.py:554` | 逐条判断 body 是否已可逐字比对。**DRIFT-NEW-016 对齐后那 4 条本应可比对**（两侧都产出 `field "xxx" is not set\n`），仍留在白名单属历史遗留；改它需要先让 `diff_json` 支持 `text/plain` |
| **E-2** | **gRPC 层没有直接对拍** | difftest 是 HTTP wire 层。proto 字段号、错误码语义、rpc 边界只能**通过 HTTP 结果间接推断** | 起两套 gRPC server，用同一个 client 分别打 Go / RAG 的 rpc，比对响应字节。§3.1 明确要求覆盖的「proto 字段号 / message 名称 / 错误码语义」目前**没有逐条验证** |
| **E-3** | **39 步不是状态空间穷举** | 是「每个端点一个典型场景」，22 个端点全覆盖，但每端点只跑了 1–3 条路径 | 边界组合（字段全缺 / 部分缺 / 类型错 / 超长 / 特殊字符）需要按 DTO 笛卡尔展开 |
| **E-4** | **超时路径 —— 已于 2026-10-21 实测坐实，见下方专节** | ~~零覆盖~~ → 已有数据。**这是 B-1 的运行证据**，此前只有静态分析 | ✅ 已补（`exp-b1-timeout-cancel.sh` 端到端 + `probe-jdbc-cancel.java` 纯 JDBC 机制层，三层拆解见 §3.4.1「B-1 补记」） |
| **E-5** | **并发路径零覆盖** | 同用户名并发注册、同用户并发改/删/封禁，全部未测。Go 侧靠 GORM 事务 + 行锁，RAG 侧靠 `@Transactional` + 可能的 `FOR UPDATE` | 两侧同时发 N 个并发请求，比对最终数据与错误码分布 |
| **E-6** | **事务异常回滚只测了 `user-rpc`** | `AvatarTransactionWiringTest`（真 PG）证明了 W3 撞 `UNIQUE(history_id)` 时 W2 的 `clearCurrent` 被回滚。但 `admin-rpc` 侧**没有**同级验证，service/common 的 20 项偏差也没有一条跑在真 PG 上 | 按 admin 侧的写路径逐个造「中间步失败」的真 PG 场景 |

##### E-4 的实测结果（2026-10-21，`exp-b1-timeout-cancel.sh`）

**造阻塞的方式**：不用 `pg_sleep`（需要能执行任意 SQL 的端点，两栈都没有），
而是用 **`LOCK TABLE ... IN ACCESS EXCLUSIVE MODE`** —— 起一个后台事务独占整张表，
两端的 `user.login`（`SELECT ... WHERE username=?`）就阻塞在 PG 上。
这是真实的长事务形态，且不碰任何代码。

> **⚠️ 第一版实验失败了一次，值得记下来**：最初用的是 `SELECT ... FOR UPDATE` 行锁，
> **两侧 login 都在 150ms 内成功返回了**，实验毫无意义。
> 原因是 **PostgreSQL 的普通 SELECT 走 MVCC 快照读，不会被 `FOR UPDATE` 持有的行锁挡住** ——
> 只有 `FOR UPDATE`/`FOR SHARE`/`UPDATE`/`DELETE` 才等行锁。
> 必须用**表级 ACCESS EXCLUSIVE 锁**才能挡住读。
> 同一次还踩了两个：`psql "SQL"` 会把 SQL 当成 **dbname**（静默失败、输出空），
> 必须走 `-c`；Go 库的表名是 `users`（GORM 默认名）而 RAG 是 `users_user`（§3.4 wire 契约偏离）。

**数据**（持锁 12s；两侧各自的用户表上各挂一个 ACCESS EXCLUSIVE 事务）。
**以下是 21:03 同日同场连跑「基线 + 注入」两组的复测值**；首轮曾测得 Go `2035ms/378ms`、
RAG `3047ms/4455ms`，量级一致、可复现，**但以复测值为准**：

| | 客户端返回耗时 | 响应体 | **客户端返回后，PG 里的阻塞查询又存活** |
|---|---|---|---|
| **Go**（基线） | 2190ms | `200 + {"code":1015,"msg":"服务繁忙"}` | 203ms |
| RAG 基线 | 3174ms | `Request Timeout`（`text/plain`） | **4657ms** |
| RAG 注入静态 `default-statement-timeout=2` | 2449ms | `{"code":5001,"msg":"系统内部错误"}` | 193ms |
| **RAG 生产方案实施后** | **2223ms** | **`{"code":1015,"msg":"服务繁忙"}`** | **176ms** |
| Go（同场对照） | 2208ms | `{"code":1015,"msg":"服务繁忙"}` | 182ms |

> ⚠️ 第三行那个 `{"code":5001}` 曾经让人差点选错方案：它说明**只注入超时而不做异常映射**，
> 会把 `text/plain` 差异换成一个**离 Go 更远**的差异。
>
> **第四行是最终结果（issue #71187874 实施后）**：三项判据与 Go 逐字对齐 ——
> 耗时差 15ms、查询滞留差 6ms、响应体完全相同；`difftest` 39 步仍 39/39、unexpected=0。
> 三层拆解（纯 JDBC / 端到端 / 手段定性）与生产方案的组件清单见 §3.4.1「B-1 补记」。

> 第三行是 B-1 缓解实验，**该机制的三层拆解（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」**。
> 一句话：注入组把 RAG 的「查询又存活 4657ms」压到 193ms，与 Go 同量级；
> 但错误体从 `text/plain` 变成了 `5001`，**离 Go 更远** ⇒ 生产方案不能只做注入，还必须做异常映射。
>
> **⚠️ 这张表读起来最刺眼的一行是「注入组 ⇒ 5001」**：那正是**差点选错方案**的地方。
> 若当时只按「资源占用解决了」收工，RAG 的超时响应体会长期停在 5001，
> 而 5001 在 Go 侧表示**数据库故障**、不表示超时 —— 等于用一个新的 wire 差异替换掉旧的。
> 生产方案（第 4 行）之所以要做两半，根因就在这里。

**观测方法的坑（2026-10-21 记录，值得单独记）**：探针最早只统计
`count(*) WHERE wait_event_type='Lock'`，并在 count 掉到 0 时判定「查询消失」。
这个判据太弱 —— 一次实验就撞出「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后结束」
的**伪矛盾**。真相是日志里 `business_info` 那一行属于**上一个请求**（[2/4] 阶段的注册），
不是本次 login。**现在探针改为打印完整明细 `pid:state:wait_event_type:query_start` 并逐次打时间戳**，
且新增「worker 业务起止时刻」一节与该时间轴对齐 —— 客户端返回 / PG 查询消失 / worker 退出
是三件不同的事，**混在一起量必然读错**。

**判读（三个可观测差异，同一个根因）**：

1. **② PG 查询存活时长 = B-1 的核心**。Go 的 ctx cancel 触达 PG、查询立刻消失（203ms）；
   RAG 的 `cancel(true)` 发不到 JDBC，查询一直挂到持锁事务释放（4657ms）。
   静态分析预言的「Java 可能占用到 10s」，实测是「**客户端已返回超时、底层连接仍被占 4.7 秒**」。
   复测还额外确认：**worker 比客户端返回晚 4.34s 才退出** —— 线程池里这段时间一直占着。

2. **① 客户端感知时延差 ~1 秒，且是同一个根因的另一个面**。
   Go：内层 rpc 2s 超时 → ctx cancel 真的让 PG 返回 → 外层立刻收到 → 总计 2190ms。
   RAG：内层 2s 的 `cancel(true)` **外层感知不到**，拖到自己的 http-timeout 3s 才返回（3174ms）。
   ⇒ **内层超时不生效 ⇒ 外层多等 1 秒**，不是两个独立问题。
   ⚠️ 注入组反向印证：一旦内层 SQL 真在 2s 被取消，RAG 客户端耗时从 3174ms **回落到 2449ms**，
   差值消失 —— 这条判读被实验闭环了。

3. **③ 响应体形状不同**（Go `200 + envelope{code:1015}` vs RAG `text/plain`）。
   这是 ① 的下游后果，但**它是一个独立的 wire 差异，且此前从未被对拍过**
   （difftest 的 39 步里没有任何超时场景）。**新发现，尚未定性** ——
   需先确认 `HttpMiddleware` 的超时应不应该对齐 Go 的 envelope 表达，再决定改还是留档。

   > **⚠️ 定性时必须连同注入组一起看，否则会选错方案**。三种形态实测如下，
   > 它们**不是同一个问题的三种写法**，而是两条不同路径的产物：
   >
   > | 场景 | 响应体 | 由哪条路径产出 |
   > |---|---|---|
   > | Go | `{"code":1015,"msg":"服务繁忙"}` | rpc 超时 → ctx cancel |
   > | RAG 基线 | `text/plain "Request Timeout"` | `HttpMiddleware` 超时分支 |
   > | RAG 注入 `setQueryTimeout` | `{"code":5001,"msg":"系统内部错误"}` | SQL 抛 57014 → 被 `catch (Exception e)` 吞成 `Status.INTERNAL` |
   >
   > ⇒ **只做「注入超时」这一个动作，会把这个 wire 差异换成另一个，而且离 Go 更远。**
   > 任何 B-1 缓解方案都必须同时处理 57014 → `DEADLINE_EXCEEDED` 的映射。

**复现**：`cd tools/difftest && ./exp-b1-timeout-cancel.sh`（自带 `--serve-only` 起栈与收摊）。

**机制侧的字节码佐证**（比黑盒更精确）：对数据访问链路逐层 `javap -c` 数 `isInterrupted` 命中 ——
**MyBatis `BaseExecutor` 0 次 / `SimpleExecutor` 0 次 / pgjdbc 42.6.2 `QueryExecutorImpl` 0 次**。
即这条不是「框架大概不响应 interrupt」的推测，而是**整条路径上没有任何一层检查中断标志**。
注意「RAG 侧 MyBatis-Plus」这个上层 ORM 换成什么都不会改变结论 ——
**只要底层还是 JDBC socket，就是同一个限制**。

**⚠️ 但字节码仍是「静态证据」，2026-10-21 已补上行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：
绕开 HTTP / gRPC / MyBatis 直连 PG，用 `ACCESS EXCLUSIVE` 锁造阻塞，
在查询跑到 1000ms 时对该线程 `interrupt()`：

| mode | 结果 | 耗时 |
|---|---|---|
| `none` | ✅ 正常返回（阻塞到锁释放） | 10022ms |
| `interrupt` | ✅ **正常返回 —— interrupt 零效果** | 11014ms |

⇒ `interrupt` 组比 `none` 组**还慢 1s**（interrupt 就发生在 1000ms 处），
说明它连「让查询稍早返回」都没做到。**这才是 B-1 的直接运行证据**，
字节码只是佐证。复现：`cd tools/difftest && java -cp <pgjdbc> probe-jdbc-cancel.java interrupt`（约 10s）。

> **这三条为什么是「缺口」而不是「缺陷」**：E-4 的根因 B-1 是**已知且已签字**的框架限制
> （Java 线程 interrupt 不被 JDBC / grpc-java / HttpClient 响应），它需要的是**业务侧主动响应**
> 而不是「改代码对齐」；E-5 / E-6 缺的是证据，代码本身可能已经是对的。
> **三者都不能靠静态审查关闭** —— §3.3 的纪律同样适用于此。
>
> **优先次序建议（2026-10-21 更新）**：E-4 **已完成**（纯 JDBC 机制层 + 同日同场端到端 + 手段定性，
> 且已据此实施修复并消除 B-1，见 §3.4.1「B-1 补记」）⇒ 下一位是 **E-2**
> （§3.1 硬性要求项：proto 字段号 / message 名称 / 错误码语义）→ E-5 / E-6 → E-1 的清理 → E-3。
> ⚠️ E-4 的经历给 E-2 留了一条现成的做法：**不要只做 HTTP 层**，本轮的 B-1 正是
> 「HTTP 全绿 + gRPC 链路从未执行」才暴露出来的。

### 6.3 架构决策（问题 1、2 已于 2026-10-21 拍板；问题 3 为新增留档）

**✅ 问题 1：建表机制 —— 2026-10-21 用户拍板「直接把 Flyway 丢掉」。**（原为待定，2026-10-20 曾定案为「保留 Flyway + 全局版本号」，该结论已被推翻）

| 表 | 谁建 |
|---|---|
| `users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite` | **user-rpc 与 admin-rpc 都会建**（幂等，谁先启动都一样） |
| message 三张表 | **message-rpc 仍用 Flyway**（本次未动，见下方范围说明） |

执行要点：

1. **4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`**，编号前缀（`01_`…`04_`）
   只保证确定性执行顺序，**不表示版本**。
2. `users-common` 的 `com.rag.users.common.ddl.SchemaInitializer` 用 `ScriptUtils` 原样重放；
   user-rpc / admin-rpc 各自的 `bootstrap/UserSchemaBootstrap` / `AdminSchemaBootstrap`
   在 `@PostConstruct` 里调用，任一 DDL 失败即启动失败（对齐 Go 的 `log.Fatalln`）。
3. `AdminSeederBootstrap` 显式 `@DependsOn("adminSchemaBootstrap")`。
4. 两个 rpc 的 `spring-boot-flyway` / `flyway-core` / `flyway-database-postgresql`
   **三件套全部从 pom 移除** —— 只删 `flyway-core` 是不够的，`spring-boot-flyway`
   在 classpath 上就会让 `FlywayAutoConfiguration` 按默认值自动 migrate。

Go 侧证据：`user/rpc/internal/model/db.go:33` = `AutoMigrate(&User{}, &UserAvatarHistory{})`，`admin/rpc/internal/model/db.go:15` = `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— **Go 两个服务都建 `users_user`**，
Go 之所以无冲突，正因为 `AutoMigrate` 无状态且幂等。丢掉 Flyway 之后 Java 侧回到同一条路上。

**⚠️ 已知代价（第一次 `ALTER` 之前可以无视）**：脚本要同时承担「建表」与「升级」两件事，
只能到处加 `ADD COLUMN IF NOT EXISTS`；也没有任何地方记录「这张表现在该长什么样」。
**第一次 `ALTER` 之前必须重新引入版本化机制。**

**⚠️ 本问题的实际范围比原记录更宽**：`message-rpc` **仍在用 Flyway**，本次未动。
它与 users 域共用同一个 `first_db`，一旦 users 域也留下 Flyway 账本就会撞号 ——
现在 users 域已经没有账本了，所以 message-rpc 暂时安全。
若日后 message 域要接入 users 域的数据、或想让 message-rpc 也去掉 Flyway，
按同一套 `SchemaInitializer` 模式处理即可。

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷。**（原为待定）

实测证据：

- `admin/rpc` 全仓 `UserRpc` / `userpb` / `UserService` **零命中**；`ServiceContext` 只有 `Config` / `AdminModel` / `BizRedis` —— **零出向 gRPC 依赖**
- 7 个用户管控 RPC（`BanUser` / `UnBanUser` / `UpdateUser` / `DeleteUser` / `ResetUserPassword` / `GetUser` / `GetUserList`）全是 `internal/model/admin.go` 的直接 SQL
- Go 两份 `User` struct（`user/rpc` 与 `admin/rpc` 的 `internal/model/data_model.go`）**逐字一致** —— Go 自己就有两份同样的表定义

**⚠️ 原记录的前提有误，已更正**：原文称「Go 把『读身份』边界建好了，只漏了『管理用户数据』」。实测 Go 的 gRPC 边界是 **api→rpc**：`identity.UserReader`（`user/identity/resolve.go:60-62`）签名带 `grpc.CallOption`（只能被 gRPC stub 满足），但调用方只有 `user/api`（`svcCtx.UserRpc = userservice.NewUserService(zrpc.MustNewClient(...))`）与 `knowledge/api` —— **都是 api**。Go 里**不存在 rpc→rpc 的用户数据边界**，「读身份」与「管用户数据」不是同一个轴。

**⚠️ 一条常见反对理由已被推翻**：`CreateAdmin` **不建 User**（只做锁邀请码 / 查重 / 插 `Admin` / 标记已用，见 `createadminlogic.go`），7 个管控 RPC 也都是单表独立操作，**无跨边界事务** —— 所以「转发会破坏 `CreateAdmin` 原子性」**不成立**。

若日后仍决定转发，属 §3.4 留档的有意偏离，须单独立 spec；代价是：admin-rpc **新增一个可用性依赖**（user-rpc 挂则管控挂，Go 无此耦合）+ 错误分类跨两层映射 + 7 个 RPC 多一跳延迟。

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包。**（2026-10-21 新增）

Go 侧每个 rpc 服务都在入口显式注册（`user/rpc/user.go:32,43`、`admin/rpc/admin.go:33,43`、`hot/rpc/hot.go:32,42` 三处一致）：

```go
rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)
s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
```

**Go 里不存在「全局自动注册」**。RAG 把 `@Component` 当自动注册，是一次**无出处 idiomatization**（§3.2）—— `UserRpcApplication` 的 `@Import` 只含 3 个 bean、`AdminRpcApplication` 连 `@Import` 都没有，两者**当时**均无 gRPC span / 无 deadline 传播 / 无 gRPC 超时。

> **2026-10-21 已实施并验证**：本问题已随 `row/common-fix-semantic-drift` 合并落地（见 §6.5）。`ObservabilityWiringTest` 用 `@SpringBootTest` 断言 `ServerInterceptor` 确实是容器里的 Bean —— 正是这条「自动注册」假设此前无任何测试覆盖，才让缺陷长期静默。`message-rpc` 归一到显式 `@Import` 仍**未做**，见 §3.4.1 效力边界。

定案（**2026-10-21 已实施**，见 §6.5）：`@Import` 数组补 `RpcServerInterceptorFactory.class`（`@Import` 本就接受普通 `@Component` 类，**零搬迁**）。连带结果：**不再有任何模块扫 `com.rag.common`** ⇒ servlet 类不会被拖进纯 gRPC 进程 ⇒ §12.5 曾提的 `spring/web/` + `spring/grpc/` 拆包**动机自我消解，已撤回**。`message-rpc` 现在能跑属**撞运气**（恰好有 `spring-boot-starter-web-server` 兜住），须一并归一到显式 `@Import`。

### 6.4 其他子模块

| 阶段（module） | 状态 |
|---|---|
| `service/message` | 未按新政策复核；**下一轮审计必须 Go ↔ RAG 对照** |
| Phase 5（全量 + 端到端） | 未启动；需真实 PG + Redis 集成测试补上（当前测试全部 mock 数据层） |

### 6.5 `service/common` 语义一致性审计与修复（2026-10-21）

只读审计（Go ↔ RAG 逐行对照，Go 侧 8 个非测试源文件全部通读原文）结论 **SEMANTICALLY NOT EQUIVALENT**，共 20 项偏差。修复在分支 `row/common-fix-semantic-drift`（issue #71186341，spec `.agents/specs/common-semantic-drift-fix.md`）。

**消费者现状（决定了修复优先级）**：

| | Go 侧被谁用 | RAG 侧被谁用 |
|---|---|---|
| communityfact | 11 个服务 | **0** |
| observability | 全部 13 个服务 | 1（user-rpc，且只 import 了 `DeadlineState` 类型） |
| logger | 全部服务 | `LoggerFactoryBean` ← user-rpc 12 处 |
| snowflake | 全部服务 | user-rpc / admin-rpc / message-rpc |

**已修 21 项**（5 项 HIGH 全部处理）：

| 组 | 内容 |
|---|---|
| HIGH | ① HTTP 超时整体失效（新增 `httpTimeout`/`rpcTimeout` 配置，5 个 yaml 落 3s/2s）② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**③ `logFatal` 不终止进程（新增 `FatalError` + users 两处 advice 显式 rethrow）④ 499 分支不可达（改为写响应）⑤ 超时启用后业务跑在无请求上下文的线程（补 `RequestContextHolder`/MDC/OTel Context） |
| MEDIUM | `isTimeoutError` 漏判 HTTP 超时、`RecordError` 空指针、`biz_msg` 丢 code、span 重复打标、span 二次 `end()`、`DeadlineState` 设在调度线程而非 handler 线程、`MqConsumer` 吞 panic、`shutdown()` 无调用者、`call_chain` 过滤失效、Snowflake 双份生成状态、admin-rpc `SnowflakeBean` 未装配 |
| LOW | 时钟回拨按 bwmarrin v0.3.0 **真实算法**（无回拨保护，继承 Go 风险并留档）、进程日志 KV 一律 `toString()` 破坏 JSON schema、timestamp 精度与尾零裁剪、trace 字段跨线程丢失、charset 默认编码、64KB 截断多读、`getFuncName` 闭包分支死代码 |

**顺带修掉的潜伏缺陷**（都是本轮改动触发才暴露的）：

- **`logback-classic` 1.6.3 与 `logback-core` 1.5.38 版本错配** → 任何真正启动的 Spring Boot 应用都会在 `LoggingApplicationListener` 抛 `AbstractMethodError: PatternLayout.getDefaultConverterMap()`。此前能长期潜伏，正因为测试全是纯单元测试。已改为交由父 pom 统一管理。
- `ObservabilityProperties` 同时带 `@Component` + `@ConfigurationProperties`，与 `@EnableConfigurationProperties` 并存会产生**两个同名类型 Bean**。本轮统一走 `@Import` 路径（与 rpc 模块一致），测试也照抄该路径。

**新增测试（§3.3 要求：必须真正装配 Spring 上下文）**：

`service/common` 现有 78 个测试（原 75 + 新增），其中 `ObservabilityWiringTest` 是**全仓第一个 `@SpringBootTest`** —— H1/H2 这类「机制没接上」的缺陷按定义无法被纯单测捕获。新增回归用例：`clientDisconnectReturns499`、`workerThreadInheritsRequestContext`、`isTimeoutErrorDetectsHttpClientTimeouts`、`wireAuthorityUnavailableSentinelMapsTo404`、`callChainFilterActuallyRemovesFrameworkFrames`、`logFatalThrowsFatalErrorInsteadOfOnExit`×2。

验证：`mvn clean verify` 全仓 9 模块 BUILD SUCCESS（common 78 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30 / users-common 3 / message-rpc 待复核）。

> **⚠️ 上面这行是 2026-10-21 那轮的快照，数字已过时**。截至 issue #71187874 之后的最新值：
> `common 97 / admin-rpc 53 / admin-api 97 / user-rpc 84 / user-api 63 / users-common 11 / message-rpc 10`，
> **全仓 415 条、0 failures**。**但更要紧的不是数字变化，而是本节那句「H1/H2 这类机制没接上的缺陷
> 按定义无法被纯单测捕获」** —— `ObservabilityWiringTest` 恰恰就是纯装配测试，
> 而它**漏掉了 H2**：断言 `ctx.getBean(ServerInterceptor.class)` 非空是对的、也是绿的，
> 但 starter 根本不会用这个 Bean。**装配对了 ≠ 机制接上了**，见本节的 H2 勘误小节。

##### ⚠️⚠️ 勘误：**H2 当时并未真正修好**（2026-10-21，issue #71187874 期间实测发现）

本节「已修 21 项」里的 **② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**** ——**结论是错的**。
`@Import(RpcServerInterceptorFactory.class)` 只让 `ServerInterceptor` **Bean 存在**，
而 **grpc-spring-boot-starter 3.1.0.RELEASE 从不自动把它挂到 server 上**：

```
javap GrpcServerFactoryAutoConfiguration
  → inProcessGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
                                             ↑ 只认 Configurer，没有任何 ServerInterceptor 形参
javap GrpcServerConfigurer → extends Consumer<ServerBuilder<?>>
javap GrpcServerProperties | grep -i intercept → （无字段）
```

唯一注册入口是 `GrpcServerConfigurer` —— 在 `accept(ServerBuilder)` 里调 `server.intercept(...)`。
已在 `UserRpcApplication` / `AdminRpcApplication` 各补一个 `@Bean GrpcServerConfigurer`。

**代价远不止「少了几个 span」**：`interceptCall` / `onHalfClose` 从未执行 ⇒

1. gRPC **无 deadline 传播** ⇒ handler 线程上 `DeadlineState` 恒未设置
   （实测 `DeadlineState.remainingMillis() == Long.MAX_VALUE`）；
2. ⇒ `UserDomainService` 的 11 处 + `AdminDomainService` 的 13 处 **`checkDeadline()` 全部失效**；
3. ⇒ B-1 的 `setQueryTimeout` 注入无从谈起（它就挂在 `DeadlineState` 上）；
4. gRPC **无超时**（客户端只能等 api 层 `http-timeout` 3s）⇒ 超时响应体退化成
   `text/plain "Request Timeout"`，与 Go 的 `{"code":1015}` 不一致。

**实测对照（`exp-b1-timeout-cancel.sh`，修复前 → 修复后）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |

> **为什么 `ObservabilityWiringTest` 没抓到**：`grpcInterceptorBeanExists` 断言的是
> `ctx.getBean(ServerInterceptor.class)` 非空 —— 断言的是**Bean 存在**，
> 而缺陷是**框架没用它**。这类断言在任何实现下都成立，属**结构性假绿**。
>
> **由此新增一条纪律（补在 §3.3）**：**断言「Bean 存在」≠ 断言「机制生效」。**
> 凡依赖框架自动注册的机制（starter 的自动装配、auto-configuration、拦截器链），
> 测试必须断言**运行时的可观测后果**（本例：客户端是否 ≈2s 返回 1015），
> 或至少断言「该对象确实进入了框架的接管点」（本例：`verify(builder).intercept(...)`）。
> 锁死用例：`GrpcInterceptorWiringTest`（2 条）。

**残留差异**不在本节重复维护，统一见 **§3.4.1**（按「有意偏离 / 框架限制 / 表述差异 / UNRESOLVED」四类分列，每条带关闭条件）。

其中本轮新增、值得单独强调的一条：**B-1 超时无法取消在途 IO**。
> ⚠️ **本行原写「按 Go 的 3s/2s 打开超时后转为当前生效」—— 该描述本身也是错的**（2026-10-21 补）。
> 配置确实绑上了（实测 `newUnaryServerInterceptor` 收到 `timeout=PT2S`），
> 但因为同一个 H2 缺陷（gRPC `ServerInterceptor` 从未挂到 server 上），
> **`interceptCall` / `onHalfClose` 一次都没执行过** ⇒ 超时**从未生效**，
> 客户端只能等 api 层 `http-timeout` 3s。
> ⇒ 结论：这条「潜伏 → 生效」的叙述，以及本节对 H1 的「已修」判断，**都建立在一个从未接通的链路上**。
⚠️ 本行末句「`user-rpc` 业务侧一处都未主动响应取消」**已被 §3.4.4 的 DRIFT-018 登记推翻**
（`user-rpc` 实有 11 处、`admin-rpc` 已补到 13 处 `checkDeadline()`），
且 2026-10-21 的机制实验证明 **`checkDeadline` 对在途 IO 无效、缓解手段是 `setQueryTimeout`** ——
完整结论与数据见 §3.4.1「B-1 补记」。

另一条：Snowflake 时钟回拨**照搬 bwmarrin v0.3.0 的无保护行为**，继承其「回拨可能撞 ID」的风险。RAG 不擅自加保护（加了即与 Go 不等价），若要加须单独立项。

## 七、引用清单

### 7.1 真理之源（判定行为）

**`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`** —— 冻结的唯一真源

| RAG 子模块 | Go 对应路径 |
|---|---|
| `service/users` | `<go>/service/user` |
| `service/message` | `<go>/service/message` |

常用锚点（审计 `service/users` 时已验证）：

| 主题 | Go 路径 |
|---|---|
| 路由 / 鉴权中间件装配 | `service/user/user/api/internal/handler/routes.go` |
| 响应封装（显式 `httpx.OkJson`） | `service/user/admin/api/internal/handler/admin/*.go` |
| 建表（`AutoMigrate`，无状态） | `service/user/{user,admin}/rpc/internal/model/db.go` |
| 事务边界（闭包传 `tx`） | `service/user/admin/rpc/internal/logic/createadminlogic.go` |
| 邀请码行锁（`clause.Locking`） | `service/user/admin/rpc/internal/model/admin.go` |
| 身份解析（**走 gRPC**，边界正确的范例） | `service/user/user/identity/resolve.go` |
| JWT 签发（无长度校验） | `service/user/common/jwt/jwt.go` |
| 字段与 ORM 映射（struct tag） | `service/user/admin/rpc/internal/model/*.go` |

### 7.2 本仓库

- 根目录：`/Users/pr/Documents/java&golang/RAG`
- **`FileTree/`（13 份，§6.2 多数行的证据出处）** —— 按 §3.1/§3.2 推导的**理论 Java 文件树**，每份含 §12.1–§12.7；**§12.5 是可直接落地的纯文件树**，`§12.7` 是不通过项与 `UNKNOWN` 的汇总。
  - `common.md`、`message/{api,rpc}.md`、`hot/{api,rpc}.md`、`like/{api,rpc}.md`、`article/{api,rpc}.md`、`users/{user,admin}/{api,rpc}.md`
  - **状态**：`article` / `hot` / `like` 的 RAG 模块仍是空骨架（仅 pom，0 个 Java 文件），其树为**待实施**目标；`users` / `message` 的树与现存代码对照过，含已知偏差（见各文件 §12.7）
  - **消费约定**：文件树是**迁移期脚手架**，不是长期规范。按 phase 消费——某 phase 跑绿并 review 后，对应文件即可退休
- 模块 `HUMAN_ONLY.MD`（7 份）：
  - `service/users/users-common/HUMAN_ONLY.MD`
  - `service/users/user/user-rpc/HUMAN_ONLY.MD`
  - `service/users/user/user-api/HUMAN_ONLY.MD`
  - `service/users/admin/admin-rpc/HUMAN_ONLY.MD`
  - `service/users/admin/admin-api/HUMAN_ONLY.MD`
  - `service/message/message-rpc/HUMAN_ONLY.MD`
  - `service/message/message-api/HUMAN_ONLY.MD`
- 任务指南：`RAG/.agents/`（不得削弱本文件的规则）
- spec：`RAG/.agents/specs/<slug>.md`

### 7.3 已知的 Go 侧孤儿（勿照搬）

`service/user/rpc/internal/logic/` 是 7 个 `_test.go` 的孤儿目录，全仓无任何引用——搬迁时不要把它一起带过来。