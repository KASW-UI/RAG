<!-- @move AGENTS.md L2410-L2442 · §6.2 已知未修（按优先级） -->
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

<!-- @move AGENTS.md L2900-L2909 · §6.2.1 本次从本表移出 -->
### 6.2.1 本次从本表移出（已修或已定案）

| 原优先级 | 项 | 状态 |
|---|---|---|
| ~~P0~~ | ~~**B-1：rpc 超时无法取消在途 IO**~~ | ✅ **2026-10-21 已关闭**（issue #71187373 实验 → #71187874 实施） | 三层证据：① 纯 JDBC 层 `probe-jdbc-cancel.java` 证明 **interrupt 对等锁查询零效果**（照常返回、11014ms）、**`setQueryTimeout` 有效**（2034ms 抛 `SQLState=57014`，与 Go ctx cancel 同一条服务端语义且连接保可用）；② 同日同场端到端；③ 手段定性（`socketTimeout` 会让连接报废，语义相反，不可用）。**实施**：`DeadlineQueryTimeoutInjector`（按 `DeadlineState.remainingMillis()` 注入）+ `SqlTimeoutStatusTranslator`（57014→DEADLINE_EXCEEDED）+ 46 处 `catch (StatusRuntimeException) { throw e; }`（user-rpc 17 + admin-rpc 29）。**api 层零改动** —— `DEADLINE_EXCEEDED` 本就落 `mapRPCStatusToCode` 的 default → 1015。**结果**：客户端 3170ms→**2223ms**（Go 2208ms）、响应体 `text/plain`→**`{"code":1015}`**、PG 查询滞留 4303ms→**176ms**（Go 182ms）；difftest 39/39 unexpected=0；**admin 域另跑两轮**（RAG 2544ms/179ms vs Go 2181ms/176ms，同样对齐）；全仓 383→**421 条** 0 failures，其中 6 条是**扫描字节码异常表的全量守恒元测试**（`UserDomainServiceCatchStructureTest` / `AdminDomainServiceCatchStructureTest`）—— 46 处放行分支由「逐点人工读」升级为「机器守恒」，变异测试已实证能变红。⚠️ 过程中发现更大前置缺陷 —— gRPC `ServerInterceptor` 从未挂到 server 上，连带 **DRIFT-018 的 24 处 `checkDeadline()` 此前全是死代码**（见上一行二次更正）。详见 §3.4.1「B-1 补记」 |
| ~~P0~~ | admin-api 默认配置下起不来（`WeakKeyException`） | ✅ **已修**（commit `71be288`）。`AdminJwtAuthInterceptor.java:67` 与 `JwtAuthInterceptor.java:76` 均已走 `JwtUtil.padToHmacKeySize(...)`；`JwtUtil.java:44` 已是 `public static`——即原表建议的「提成 `users-common` public 方法」方案已实施。**本行此前一直挂在 P0 未修，是过期信息** |
| ~~P2~~ | user-api 4 项清理 | ⚠️ **半修**。① 4 个端点 `@RequestBody(required=false)` **已完成**（`UserController.java:118/126/137/153`）；② 「删 `codeFromRPCError` 死代码」**未完成，且原文把位置写错**——它在 **admin-api**（`AdminApiSupport.java:66`），不在 user-api。剩余部分已并入本表 P3 |
| ~~P3~~ | Flyway 迁移归属 | ✅ **已定案** → §6.3 问题 1 |
| ~~P3~~ | `admin-rpc` 边界缺失 | ✅ **已定案**（不转发，与 Go 一致）→ §6.3 问题 2 |


<!-- @author mavis 2026-10-23 · 本节非搬移内容，为 2026-10-23 实测新发现 -->

---

## 🔴 DRIFT-INFRA-001：Flyway 账本撞号（2026-10-23 新发现 · **跨 3 个模块**）

| 项 | 值 |
|---|---|
| 优先级 | **P0**（三个服务里任意两个同时对空库启动即触发） |
| 最后核对 | **2026-10-23 01:32**（真实执行产出：`check-flyway-ledger.py` 由 `EXIT=1` 转 `EXIT=0`） |
| 状态 | **配置层已修** —— `message-rpc`/`like-rpc` 已加 `spring.flyway.table`；`article-rpc` 按指示未动（此时默认账本由它独占）。❌ **运行时验证仍缺 ⇒ 按 §3.3 记 `UNKNOWN`** |
| 决策记录 | AGENTS.md §6.3 **问题 4** |
| 守门 | `tools/difftest/check-flyway-ledger.py` **已转绿 `EXIT=0`**；各模块 `FlywayLedgerIsolationTest` **全仓不存在**（文档误称其存在） |

### 实测证据

| 模块 | 库 | schema | 账本表（修前） | 迁移 |
|---|---|---|---|---|
| `service/message/message-rpc` | `first_db` | `public` | `flyway_schema_history` | V1 V2 V3 |
| `service/like/like-rpc` | `first_db` | `public` | `flyway_schema_history` | V1 V2 V3 V4 V5 |
| `service/article/article-rpc` | `first_db` | `public` | `flyway_schema_history` | V8 V9 V10 |

**三个硬条件同时成立**（缺一不成立）：

1. **同一个库** —— `docker-compose.yaml:16` 与 `:127` 均为 `POSTGRES_DB: ${POSTGRES_DB:-first_db}`；
   五个模块 JDBC URL 全部指向该库（`users/user-rpc/…application.yaml:13`、`admin-rpc/…application.yml:10`、
   `message-rpc/…application.yml:16`、`like-rpc/…application.yml:18`、`article-rpc/…application.yml:18`）
2. **同一个 schema** —— 三处均为 `spring.flyway.schemas: public`
3. **同一本账** —— 全仓 `spring.flyway.table` **零命中**，无任何模块改过账本表名

⇒ `message-rpc` 与 `like-rpc` 的 **V1–V3 版本号完全撞车**且内容互不相同：

```
message-rpc: V1__create_message_conversation.sql
             V2__create_message_notification.sql
             V3__create_message_conversation_message.sql
like-rpc:    V1__create_like_record.sql
             V2__create_like_consume_inbox.sql
             V3__create_like_outbox_event.sql
```

**可观察后果**：任一方先迁移后，另一方启动时会命中 §6.3 问题 1 记录的那个失败模式 ——
`version (3) is newer than the latest available migration (2)` 并**静默跳过自己的迁移**
（⇒ `like_*` 或 `message_*` 表永远建不出来），或 checksum mismatch 直接启动失败。

### 根因与「为什么 10-21 没发现」

§6.3 问题 1 在 2026-10-21 判定 users 域必须丢掉 Flyway，根因**仅有一条**：
`flyway_schema_history` 是 schema 级账本，一个 schema 只能有一本账。

⚠️ **该根因在另外三个模块原封不动地成立，而问题 1 当时只处理了 users 域。**
`like-rpc` / `article-rpc` 是在 10-21 之后才建的，落地时**没有继承**这条约束。

`article-rpc/pom.xml:147-148` 的注释至今写着「§6.3 定的全局唯一号段：message-rpc 占 V1-V3、
users 占 V1-V4」，**该注释两处失真**：
① users 走的是 `users-common/src/main/resources/db/01_*.sql` ~ `04_*.sql`，**无版本号**，不占 V1-V4；
② 号段表**整个漏了 `like-rpc`**（它占 V1-V5，与 message-rpc 直接重叠）。

### 修法取舍

| 方案 | 结论 |
|---|---|
| **每模块独立账本表名** `spring.flyway.table` | ✅ **采纳并已实施（2026-10-23）**。改动面 = 3 个 yml 各加一行；不动任何 SQL 文件名；各模块版本号从 V1 各自重来也互不干扰；**直接消除病根** |
| 排全局唯一号段 | ❌ 否决。是 2026-10-20 的旧结论、**10-21 已被推翻过一次**；要改 8 个 SQL 文件名，却不解决「账本是 schema 级」这个根因 —— 只要还有第二个模块往同一 schema 放迁移，撞号就再现 |
| 全仓统一丢 Flyway | ⏸ 未采纳（与 Go 的 `AutoMigrate` 同构、最彻底，但要重做 13 个迁移脚本，超出本次修复范围） |
| ⛔ `validate-on-migrate=false` + `out-of-order=true` | ❌ **已被实测证伪，禁止再提**。`tools/difftest/exp-drift015-flyway-flags.sh`：不但修不好，还往账本塞一条语义重复的 `version 3 = create user`，把 schema 永久钉在错误版本上，日后正修得先人工清账本 |

### 效力边界（不得越读）

- ✅ 静态检查保证**声明层**账本表名两两不同。
- ✅ `FlywayLedgerIsolationTest` 保证配置能绑进 `FlywayProperties`（真实 `application.yml`，非 mock）。
- ❌ **尚未证明 Flyway 运行时真的按 `spring.flyway.table` 建表** —— 需真 PG 上双模块对启动（§3.3
  「框架机制必须验证实际生效」）。**本条尚未做，是本项的剩余缺口。**

### 同批发现（顺带更正）

`article-api` / `article-rpc` **已完整实现**，AGENTS.md §0「article 域整体仍未完成」计数过期
（36 文件 / 4,275 行 与 36 文件 / 7,029 行，骨架 0 与 2；最新写入 10-22 23:56）。
详见 [`../article/api.md`](../article/api.md) 与 [`../article/rpc.md`](../article/rpc.md)。
