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
| **RAG（本仓库）** | `/Users/pr/Documents/java&golang/RAG` | **Spring Boot 重构产物** —— 按工作项目标准把 Go 行为用 Spring 生态表达；非 Spring Boot 的 Java 写法（如 JPA + flat `internal/{logic,model,server,svc}`）不在重构范围内 | **MyBatis-Plus + domain/mapper/service 分层 + Flyway + Bootstrap 抽出** |

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

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler、Flyway）全部是运行期动态装配，出错时**不报错**。

已实证的三类静默失效：

| 机制 | 静默失效形态 |
|---|---|
| `@Transactional` | self-invocation 不经代理 → 注解形同虚设，且**无任何异常** |
| `@ResponseBodyAdvice` | `HttpEntityMethodProcessor` 继承 `writeWithMessageConverters` 并查询 advice chain → 连 `@ExceptionHandler` 返回的 `ResponseEntity` 也会被二次包裹 |
| `@TableField(typeHandler=…)` | 只对 MP 自己生成的 SQL 生效；`LambdaUpdateWrapper.set()` 的参数走 `ew.paramNameValuePairs`，**丢掉字段类型信息** |

**规则**：凡引入新的框架机制，必须配一条**能真正触发该机制**的测试。
纯 Mockito mock 数据层的单测**不算**——它不会执行 SQL，也不会装配 Spring 上下文，天然测不到本节三类问题。

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

#### 3.4.3 `service/users/admin` 语义审计后的三条新增留档（2026-10-21，issue #71186443）

Go → Spring Boot 语义一致性审计 `service/users/admin`（13 RPC + 13 HTTP 端点）产出
**10 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 3 项 UNRESOLVED**。
**9 项 MISMATCH + 2 项 wire 契约已修**，新增 **36 条回归测试**
（`admin-rpc` 的 `AdminSemanticDriftRegressionTest` 12 条 + `admin-api` 的
`AdminSemanticDriftApiRegressionTest` 24 条）。
以下三条**是修复后仍然存在的有意偏离**：

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-3** | **非法 uid 的错误分类** | Go admin 侧的 uid 走 `form:"uid,string"` 绑定，**key 缺失时静默保留零值**且不报错 ⇒ `uid=0` 进 rpc ⇒ 查无此人 ⇒ 返回 **`1003` 用户不存在**。RAG 对 null / 空 / 非数字一律返回 **`1017` 请求参数错误**（`AdminApiSupport.parseUid`） | **用户 2026-10-21 拍板：保留 RAG 的 1017**。Go 的 0 值路径是 go-zero form 绑定的**副作用**而非业务设计；把它复制过来会重新引入 `user-api` 已在 §6.1 第 9 项修掉的同类回归（坏请求被误分类成业务不存在，且掩盖调用方的参数拼装 bug）。**注意与 `user-api` 的区别**：那条修复成立是因为 user-api 的 Go 确实返回 1017；**admin 侧取参机制不同，不可跨模块套用结论** | 若坚持逐字复刻 Go 的 0 值路径，需把 `AdminApiSupport.parseUid` 改为返回 0，并同步删除 `AdminSemanticDriftApiRegressionTest` 的两条防回退断言 |
| **N-4** | **`operator_id` 不恒为 0** | Go 的 `updateuser` / `banuser` / `unbanuser` / `delete` / `resetuserpassword` 读 `ctx["uid"]`，而 `rest.WithJwt` 写入 ctx 的 key 是 JWT claim 名 **`userId`** ⇒ 永远取不到 ⇒ `OperatorId` 恒为 **0**。RAG 五个端点都传真实 uid | **用户 2026-10-21 拍板：保留真实 uid**。该字段**不参与任何业务判定**，只写进 OTel span 的 `audit.operator_id` 属性；恒 0 会让审计日志失去操作人信息。影响面 = 可观测性数据，不是业务行为 | 若要求审计 span 与 Go 逐字一致，需在这 5 个 Service 里刻意丢弃 token 中的 uid |
| **N-5** | **JWT HMAC 密钥长度** | Go 用 15 字节原始 secret 参与 HS256；RAG 用 `JwtUtil.padToHmacKeySize` zero-pad 到 32 字节 | jjwt 0.12 强制 key ≥256 bit。RAG 内部签发与验签共用同一份 padded key、自洽；代价是 **Go 签的 token 在 RAG 验不过，反之亦然**（两套部署各自闭环）。与 `user-api` 同款 | 若需跨部署互认 token，必须统一 key 长度——要么改 Go 侧 secret 到 ≥32 字节，要么 RAG 放弃 padding（jjwt 会抛 `WeakKeyException`） |

> **这三条的共同教训**：与 N-1/N-2 不同，N-3/N-4/N-5 都是**「Go 的行为本身有缺陷」**
> 而非「Go 表达得更地道」。真源冻结（§2）意味着 Go 的缺陷也会被冻结，于是产生了
> 「按政策应当对齐、但对齐会让 RAG 变差」的真实张力。
> **新的处置纪律**：这类项不得由单个 agent 自行判断，必须显式列出、用户签字、写进本节，
> 并用**防回退测试**（`AdminSemanticDriftApiRegressionTest#parseUid_missingIsRequestParamError`）
> 锁住现状 —— 否则下一个「语义一致性审计」agent 会把它当成新发现的漂移再「修」一遍。

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
| B-1 | `HttpMiddleware` 超时分支 / `RpcInterceptor` 超时调度 | **超时无法取消在途 IO**。Go 的 `context.WithTimeout` 到点 ctx Done，所有 ctx-aware IO（`http.Client` / `sql.DB` / `grpc.Dial`）立即返回、连接池立刻释放；Java 只能 `Future.cancel(true)` 发 `InterruptedException`，而 **JDBC 驱动、grpc-java 阻塞 stub、`java.net.http.HttpClient` 均不响应线程 interrupt** —— 阻塞在 socket read 上的调用会挂到查询自行结束 | **当前生效**（2026-10-21 前 timeout 为 `ZERO`、该分支从不进入；按 Go 的 3s/2s 打开后转为活跃路径）。同一慢查询：Go 3s 后连接归还，Java 可能占用到 10s | 业务侧主动响应：长耗时方法内周期性查 `DeadlineState.isExpired()`、阻塞 IO 前用 `remainingMillis()` 限流、或捕获 `InterruptedException`。**当前 `user-rpc` 一处都未使用** —— 这是本项尚未关闭的根因 |

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

- 本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**（两边同时喂同一批输入比对输出），全仓也**没有真实 PG / Redis 集成测试**（现有测试全部 mock 数据层）。因此「已修复 20 项偏差」≠「行为已验证等价」。
- 尤其 B-1 这类差异，**只有真实慢查询 + 真实超时才能暴露**，静态审查与单测都测不到。

---

## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper 接口 `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService`（admin-rpc 是 1 个 `AdminDomainService`） | HUMAN_ONLY.MD 描述的是单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口（user-rpc：2 个；admin-rpc：3 个） | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + Flyway `V*__create_*.sql` DDL | HUMAN_ONLY.MD 列出这两个文件，且当前项目根本没有 Flyway |

> **⚠️ Flyway 条目已确认会出问题**：Go 用 `AutoMigrate`（无状态），Flyway 是**有状态账本**。双 rpc 服务共享同一 schema 时，版本号必然冲突（已实证）。迁移归属需按「数据所有权」重定，见 §6 待办。

### 4.2 5 阶段范围

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **Phase 0** | RAG 仓库建 worktree、提交基线、4 个 issue、4 份 spec（`RAG/.agents/specs/<slug>.md`） | 4 个 issue + 4 份 spec 落仓 |
| **Phase 1（pilot）** | `user-rpc` 落地完整 pattern：JPA→MyBatis-Plus + DataModel→2 Mapper + 9 Logic 合 1 + SnowflakeBootstrap + Flyway V1/V2 | `mvn -pl user-rpc -am package` 通过 + 既有 LogicTest 改完跑绿 |
| **Phase 2** | `user-api`：HTTP 层（controller / dto / interceptor / advice / client），不引入 Mapper | `mvn -pl user-api -am package` 通过 |
| **Phase 3** | `admin-rpc`：平行 user-rpc，13 用例合 1 `AdminDomainService` + 3 Mapper + Flyway V3 | `mvn -pl admin-rpc -am package` 通过 |
| **Phase 4** | `admin-api`：平行 user-api，13 Service + 2 client（admin-rpc + user-rpc） | `mvn -pl admin-api -am package` 通过 |
| **Phase 5** | 全量 `mvn clean package` + docker-compose 起来跑 gRPC 端到端 | 全绿 |

每阶段独立 stop point：跑绿后停下等用户 review，再决定继续。

### 4.3 用户问答原话（备查，避免后续被覆盖）

| 问题 | 用户答案 |
|---|---|
| ORM 这一层怎么处理？ | 严格按 HUMAN_ONLY：JPA → MyBatis-Plus（重写数据层） |
| Logic 类怎么处理？ | 严格按 HUMAN_ONLY：9 合 1 |
| DataModel.java 怎么映射？ | 严格按 HUMAN_ONLY：拆成两个 Mapper（仅在选 MyBatis-Plus 时适用） |
| SnowflakeBootstrap 与 Flyway 脚本要不要落地？ | 落地：从 UserRpcApplication 抽 SnowflakeBootstrap + 引入 Flyway + 写两份 DDL |
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
| ~~P0~~ | ~~`RpcServerInterceptorFactory` 在 user-rpc / admin-rpc 均未注册~~ | **2026-10-21 已修** | `UserRpcApplication` / `AdminRpcApplication` 的 `@Import` 均补 `RpcServerInterceptorFactory` + `ObservabilityProperties` + `ObservabilityLifecycle`；admin-rpc 另补 `SnowflakeBean/SnowflakeProperties/LoggerFactoryBean`（它此前连 `@Import` 都没有）。新增 `ObservabilityWiringTest`（`@SpringBootTest`）断言 `ServerInterceptor` 真是容器里的 Bean。分支 `row/common-fix-semantic-drift` |
| ~~P0~~ | ~~`ObservabilityProperties` 无 timeout 字段~~ | **2026-10-21 已修** | 新增 `httpTimeout` / `rpcTimeout`；两个 factory 改为读取该配置。**目标值已取证**：Go user 系 yaml 均未配 `Timeout`，实际生效值来自 go-zero v1.10.2 `rest/config.go:55` `default=3000` 与 `zrpc/config.go:46` `default=2000` ⇒ 各模块 yaml 显式落 `http-timeout: 3s` / `rpc-timeout: 2s` |
| **P1** | `message-rpc` 6 个 `@Transactional` 方法的 self-invocation 风险 | —（**未核对**） | 同上。按约定 2 按 UNKNOWN 处理 |
| **P1** | `Logger`（静态）与 `LoggerFactoryBean`（Bean）两份逐行同构实现，重复约 370 行 | **2026-10-21 更正原结论** | ⚠️ 原表写「实际消费者为零」，**该结论错误**：`LoggerFactoryBean` 在 **user-rpc** 有真实消费方 —— `UserDomainService.java:87,95` 注入 + **12 处** `logger.logInfo/logBusinessErr`（`:155,161,170,178,184,218,233,245,268,274,305,308,324`），由 `UserRpcApplication` 的 `@Import` 装配。消费者为**零**的只是静态 `Logger` 路径（`Logger.init` 仅测试调用）。结论修正：本层支撑着 user-rpc 全部登录/注册路径的日志输出，不可按死代码处置。两份实现的重复仍待处理（`FileTree/common.md` §12.5 第 3 条，**动共享库公开 API，需 review 签字**） |
| **P2** | `MessageRpcSupport.trimPreview` 截断长度错：`String.length() <= 255` vs Go 的 80 runes | 2026-10-21 | Go `model/message.go:233-239` 用 `[]rune(content)[:80]`；RAG `MessageRpcSupport.java:316-325` 用 `substring(0,255)`。根因：255 抄自列宽 `varchar(255)` 而非 Go 规则。附带：`substring` 会在代理对中间切断。**RAG 错**，证据见 `FileTree/message/rpc.md` §12.7 #1 |
| **P2** | `MessageDomainService.markNotificationRead` 拆成 SELECT + UPDATE | —（**未核对**） | Go 是单条 `UPDATE ... WHERE id + recipient_id`；RAG 拆两步存在竞态窗口。按约定 2 按 UNKNOWN 处理 |
| ~~P2~~ | ~~`Logger.call_chain` 过滤完全失效~~ | **2026-10-21 已修** | `isFiltered` 改用 `StackFrame.getDeclaringClass().getName()` 全名比对包前缀（短名比对仅作补充）；`getFuncName` 的 `contains("$")` 分支按 Go 两分支差异化。`Logger` 与 `LoggerFactoryBean` 两份同步修改。新增 `LoggerTest.callChainFilterActuallyRemovesFrameworkFrames` 锁定。**留档**：Go 侧 `github.com/zeromicro/go-zero/` 那条前缀**本来也是死的**（`getFuncName` 同样剥掉包路径），Java 版按其**意图**执行 → `call_chain` 内容比 Go 少框架帧，属 OBSERVABLE DIFFERENCE |
| ~~P2~~ | ~~499 状态码永远到不了客户端~~ | **2026-10-21 已修** | 断连分支改为 `writeTimeoutResponse(resp, 499)`，不再抛 `ServletException`；503/499 body 一并对齐 Go `http.Error`（`text/plain; charset=utf-8` + 尾部换行）。新增 `HttpMiddlewareTest.clientDisconnectReturns499`（此前**零条** 499 断言）。**连带**：两条既有 503 断言的期望值因 D-11 变更，已同步并注明变更原因 |
| ~~P2~~ | ~~`Observability.isTimeoutError` 漏判 HTTP 超时~~ | **2026-10-21 已修** | 补 `HttpTimeoutException` / `HttpConnectTimeoutException` / `InterruptedIOException`，并沿 cause 链生效。新增 `ObservabilityTest.isTimeoutErrorDetectsHttpClientTimeouts` |
| ~~P1~~ | ~~user-rpc mapper `default` 方法上的 `@Transactional` 是否真生效~~ | ✅ **2026-10-21 已验证生效** | 新增 `AvatarTransactionWiringTest`（`@Configuration @EnableTransactionManagement(proxyTargetClass = false) @MapperScan`，与 Boot `TransactionAutoConfiguration` 同构；`MybatisSqlSessionFactoryBean` + `SqlSessionTemplate`；事务管理器换成**记录型**实现）。5 条用例全绿，其中 2 条是**真触发**：DataSource 的 `getConnection()` 抛异常，断言 `createAvatarHistory` / `selectAvatarHistory` 的记录序列恰为 **`begin → rollback`** 且无 `commit`。结论：`@Transactional` **确实生效**，头像 4/5 步原子性成立。**原表建议的修法「抽独立 `@Service` Bean」不需要** —— 问题从来不是自调用，而是**缺证据**。**效力边界**：无真实 PG，故只证明「事务边界装上了、异常走回滚分支」，不证明 jsonb `CAST` 等 PG 行为（§3.3） |
| **P2** | **admin-api 全部 13 个端点 `@RequestBody` 仍是 `required=true`** | 2026-10-21 | user-api 侧同类问题已修（`UserController.java:118/126/137/153`），admin-api 未同步 ⇒ `/admin/v1/logout` 等空 body 请求返回 400，与 Go 语义漂移 |
| **P3** | `admin-api` 的 `AdminApiSupport.codeFromRPCError` 未删，且与 `GlobalExceptionHandler.mapRPCStatusToCode` 重复 | 2026-10-21 | 当前靠 service 层精确业务码优先兜底，属**潜在**语义漂移：新增 Service 漏写分支会静默落到 advice 的默认映射。**2026-10-21 审计已实证这不是「潜在」而是「已发生」**：`user-api` 的 `AvatarSelectService` 就因为走通用映射，把 Go 的 `NOT_FOUND → 500` 变成 `1003`（DRIFT-003，已修）。现已在该 Service 显式写出 Go 的整个 switch |
| ~~P1~~ | ~~**`service/users/admin` 全部 10 项 SEMANTIC MISMATCH**~~ | ✅ **2026-10-21 已修**（issue #71186443） | 见 §6.2.2 |
| **P3** | `admin-rpc` 三个 Mapper 的 javadoc 与实现不符 | 2026-10-21 | `AdminMapper` / `AdminInviteMapper` / `UserMapper` 的类注释都声称「复杂方法写在 Mapper 的 `default` 方法里，用 `@Select` + `@Transactional`」，实际三个接口都是**空壳**，`findAdminInviteByCodeForUpdate` / `updateAdminInviteUsed` / 分页查询全在 `AdminDomainService` 与 `AdminCreateAtomicService` 里。**不影响行为**（悲观锁实测生效），但属 §3.2「有出处、出处指错位置」的文档级病灶，会让后续 review 误判机制归属 |

### 6.2.2 `service/users/admin` 语义审计修复清单（issue #71186443，2026-10-21）

审计范围：Go `service/user/admin/{rpc,api}` 14 logic + 12 api logic + model + middleware
↔ RAG `service/users/admin/{admin-rpc,admin-api,admin-proto}`。结论 **SEMANTICALLY NOT EQUIVALENT**。
**已修 12 项（9 MISMATCH + 2 wire 契约 + 1 处随修复连带纠正的文档病灶）**，留档 3 项见 §3.4.3 的 N-3/N-4/N-5。

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

**回归测试**：`AdminSemanticDriftRegressionTest`（admin-rpc，12 条）+ `AdminSemanticDriftApiRegressionTest`（admin-api，24 条）。
其中 DRIFT-006/007 的断言打在**MyBatis-Plus 实际生成的 SQL 片段与绑定参数**上，不是 mock 返回值 —— 后者无论 wrapper 里装什么都照常返回预设值，测试必然假绿（§3.3）。
**容器集成测试**：`AdminRpcContainerWiringTest`（admin-rpc，7 条，真实 PG 16 + 真实 Spring 容器），见 §6.2.3。
验证：`mvn -pl admin-rpc,admin-api -am test` + `package` 全绿（**admin-rpc 43 / admin-api 72** / users-common 4 / service-common 78，均为 2026-10-21 实测计数）。

**效力边界（已部分更新）**：DRIFT-001 / 006 / 007 的行为已在真实 PG 上验证（§6.2.3 A-U2 / A-U3）。
仍缺的：DRIFT-008 的**建号路径**尚未在真库上跑过（需要真实 invite 行 + 并发消费）；
DRIFT-009 的**鉴权链**未经真实 MVC 管线验证（见 A-U0）。

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

### 6.2.3 `service/users/admin` 的 UNRESOLVED（2026-10-21 部分关闭）

> 下列状态由 `AdminRpcContainerWiringTest`（真实 PostgreSQL 16 容器 + 真实 Spring 容器，7 条）实测产出，
> 非静态推导。A-U1 与 admin-api 侧仍为 UNRESOLVED。

| # | 项 | 状态 | 证据 / 剩余未知点 |
|---|---|---|---|
| A-U0 | 真实 Spring 上下文测试 | **admin-rpc 已关闭 / admin-api 未关闭** | admin-rpc 新增 `AdminRpcContainerWiringTest`（7 条，真 PG + `@SpringBootTest(AdminRpcApplication)`），本批对 **DRIFT-010 启动时序**的改动首次在真实容器执行。**admin-api 仍缺**：`AdminResponseEnvelopeAdviceTest` 用的是 `MockMvcBuilders.standaloneSetup` + 内联 `@RestController`，不加载真实的 `AdminController` / `WebMvcConfig` / `AdminJwtAuthInterceptor` ⇒ **DRIFT-009 的鉴权链改动仍未经真实 MVC 管线验证**。关闭它需要给 admin-api 也加 Testcontainers（它不需要 PG，需要 Redis 容器或把 `RedisTemplate` 换成内嵌/内存实现） |
| A-U1 | Go 侧 uid **格式非法**（非缺失）时的 HTTP 状态码 | **仍 UNRESOLVED** | 已确认**缺失**场景走零值路径（→ `1003` HTTP 200）；`uid=abc` 这类解析失败走 `httpx.ErrorCtx` 的非 `*BindError` 分支，未读到 go-zero v1.10.2 源码原文，不确定 500 还是 400。需读 `rest/httpx/responses.go` + `httpx.Parse` 的错误分类 |
| A-U2 | 两端运行时对拍 | **RAG 侧已关闭 / Go 侧未做** | RAG 侧实证：空库上 seeder 造出的 admin 用 `admin/admin` 登录通过、用 `123456` **不**通过（DRIFT-001 关闭）；重复执行 seeder 不产生第二行（幂等）；`resetUserPassword` 用的是配置值 `123456` 而非 seeder 常量（两个来源未串）。**Go 侧仍需单独起进程跑一遍同样三步**才能做端到端对拍 |
| A-U3 | `getUserList` 的 LIKE 双重包裹 | **已关闭** | 真 PG 上实测：RAG 的 `%%alice%%` 与 `%%%alice%%%` 命中集合完全相同，且与原生 `LIKE '%alice%'`（Go 写法）结果集一致；已覆盖 username 命中 + email 命中（OR 分支）两个来源。判定为 IMPLEMENTATION DIFFERENCE 而非漂移，已实证 |

##### 搭建容器测试时踩到的环境坑（换机器 / 换网络时必读）

| 坑 | 现象 | 解法 | 出处 |
|---|---|---|---|
| **Ryuk 拉不下来** | `Can't get Docker image: RemoteDockerImage(testcontainers/ryuk:0.14.0)`，发生在任何 `@Container` 之前，整类 ContainerFetch | surefire 注入**环境变量** `TESTCONTAINERS_RYUK_DISABLED=true`（见 admin-rpc/pom.xml）。**`testcontainers.properties` 与 `-D` 都无效** —— Testcontainers 2.x 的 `ResourceReaper.instance()` 字节码里就一句 `System.getenv(...)` | 已实测 |
| **`disabledWithoutDocker` 误判** | Docker 明明可用，日志显示已 Connected，但全部用例 Skipped | 去掉该参数。2.x 的 `DockerAvailableDetector` 在本机判定为不可用，而静态 `@Container` 字段初始化早于条件求值，所以日志顺序具有欺骗性 | 已实测 |
| **gRPC server 关不掉** | 写 `grpc.server.enabled=false` 无效，仍起 server 去抢 8081 → `BindException: Address already in use` | 改 `grpc.server.port=0` 绑临时端口。devh 3.1.0 的 `GrpcServerLifecycle` **没有** `@ConditionalOnProperty("grpc.server.enabled")`，无开关可用 | 已实测 |
| **`PullPolicy` 位置变了** | 2.x 里 `PullPolicy` 在 `org.testcontainers.images`，且**没有** `neverPull()` | 用默认的 `DefaultPullPolicy`（本地已有镜像则不拉）。测试用的 `postgres:16-alpine` 需本地预先存在 | 已实测 |

> ⚠ 关掉 Ryuk 的代价是**容器不再自动回收**，CI 跑完可能残留。
> Docker Hub 可达后删掉 admin-rpc/pom.xml 里那段 surefire 配置即可恢复。

### 6.2.1 本次从本表移出（已修或已定案）

| 原优先级 | 项 | 状态 |
|---|---|---|
| ~~P0~~ | admin-api 默认配置下起不来（`WeakKeyException`） | ✅ **已修**（commit `71be288`）。`AdminJwtAuthInterceptor.java:67` 与 `JwtAuthInterceptor.java:76` 均已走 `JwtUtil.padToHmacKeySize(...)`；`JwtUtil.java:44` 已是 `public static`——即原表建议的「提成 `users-common` public 方法」方案已实施。**本行此前一直挂在 P0 未修，是过期信息** |
| ~~P2~~ | user-api 4 项清理 | ⚠️ **半修**。① 4 个端点 `@RequestBody(required=false)` **已完成**（`UserController.java:118/126/137/153`）；② 「删 `codeFromRPCError` 死代码」**未完成，且原文把位置写错**——它在 **admin-api**（`AdminApiSupport.java:66`），不在 user-api。剩余部分已并入本表 P3 |
| ~~P3~~ | Flyway 迁移归属 | ✅ **已定案** → §6.3 问题 1 |
| ~~P3~~ | `admin-rpc` 边界缺失 | ✅ **已定案**（不转发，与 Go 一致）→ §6.3 问题 2 |

### 6.3 架构决策（问题 1、2 已于 2026-10-21 拍板；问题 3 为新增留档）

**✅ 问题 1：Flyway 迁移归属 —— 单一归属 + 全局唯一版本号。**（原为待定）

| 表 | 归属服务 |
|---|---|
| `users_user` / `users_user_avatar_history` | **user-rpc** |
| `admin_admin` / `admin_invite` | **admin-rpc** |
| message 三张表 | **message-rpc** |

执行要点：

1. **版本号跨服务全局唯一**（user-rpc V1/V2、admin-rpc V3/V4、message-rpc V5+）。
2. **admin-rpc 删除 `V3__create_user.sql`**，改由 `bootstrap/AdminSchemaGate` 校验 `users_user` 存在，缺失 fail-fast（**只校验不创建**）。
3. **候选 ②「每服务独立 schema」已被证据排除** —— admin-rpc 直接查 `users_user`，拆 schema 必须跨 schema 查询或改转发，而 Go 两边都直连同一库。

Go 侧证据：`user/rpc/internal/model/db.go:33` = `AutoMigrate(&User{}, &UserAvatarHistory{})`，`admin/rpc/internal/model/db.go:15` = `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— **Go 两个服务都建 `users_user`**，无冲突只因 `AutoMigrate` 无状态且幂等。

**⚠️ 本问题的实际范围比原记录更宽**：Go 全部服务连同一 `first_db`，共用一张 `flyway_schema_history`，因此 **message-rpc 的 V1/V2/V3 同样与两个 users 模块撞号**。admin-rpc 现有 `V3__create_user.sql` 里的 `IF NOT EXISTS` 只解决建表，**解决不了版本号与 checksum 冲突**。

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
| HIGH | ① HTTP 超时整体失效（新增 `httpTimeout`/`rpcTimeout` 配置，5 个 yaml 落 3s/2s）② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）③ `logFatal` 不终止进程（新增 `FatalError` + users 两处 advice 显式 rethrow）④ 499 分支不可达（改为写响应）⑤ 超时启用后业务跑在无请求上下文的线程（补 `RequestContextHolder`/MDC/OTel Context） |
| MEDIUM | `isTimeoutError` 漏判 HTTP 超时、`RecordError` 空指针、`biz_msg` 丢 code、span 重复打标、span 二次 `end()`、`DeadlineState` 设在调度线程而非 handler 线程、`MqConsumer` 吞 panic、`shutdown()` 无调用者、`call_chain` 过滤失效、Snowflake 双份生成状态、admin-rpc `SnowflakeBean` 未装配 |
| LOW | 时钟回拨按 bwmarrin v0.3.0 **真实算法**（无回拨保护，继承 Go 风险并留档）、进程日志 KV 一律 `toString()` 破坏 JSON schema、timestamp 精度与尾零裁剪、trace 字段跨线程丢失、charset 默认编码、64KB 截断多读、`getFuncName` 闭包分支死代码 |

**顺带修掉的潜伏缺陷**（都是本轮改动触发才暴露的）：

- **`logback-classic` 1.6.3 与 `logback-core` 1.5.38 版本错配** → 任何真正启动的 Spring Boot 应用都会在 `LoggingApplicationListener` 抛 `AbstractMethodError: PatternLayout.getDefaultConverterMap()`。此前能长期潜伏，正因为测试全是纯单元测试。已改为交由父 pom 统一管理。
- `ObservabilityProperties` 同时带 `@Component` + `@ConfigurationProperties`，与 `@EnableConfigurationProperties` 并存会产生**两个同名类型 Bean**。本轮统一走 `@Import` 路径（与 rpc 模块一致），测试也照抄该路径。

**新增测试（§3.3 要求：必须真正装配 Spring 上下文）**：

`service/common` 现有 78 个测试（原 75 + 新增），其中 `ObservabilityWiringTest` 是**全仓第一个 `@SpringBootTest`** —— H1/H2 这类「机制没接上」的缺陷按定义无法被纯单测捕获。新增回归用例：`clientDisconnectReturns499`、`workerThreadInheritsRequestContext`、`isTimeoutErrorDetectsHttpClientTimeouts`、`wireAuthorityUnavailableSentinelMapsTo404`、`callChainFilterActuallyRemovesFrameworkFrames`、`logFatalThrowsFatalErrorInsteadOfOnExit`×2。

验证：`mvn clean verify` 全仓 9 模块 BUILD SUCCESS（common 78 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30 / users-common 3 / message-rpc 待复核）。

**残留差异**不在本节重复维护，统一见 **§3.4.1**（按「有意偏离 / 框架限制 / 表述差异 / UNRESOLVED」四类分列，每条带关闭条件）。

其中本轮新增、值得单独强调的一条：**B-1 超时无法取消在途 IO**。它在本轮之前是潜伏的（timeout 为 `ZERO`，分支从不进入），按 Go 的 3s/2s 打开超时后转为**当前生效**，且 `user-rpc` 业务侧一处都未主动响应取消。

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