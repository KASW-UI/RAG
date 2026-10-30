<!-- @move AGENTS.md L1925-L1977 · §3.4.3 service/users/admin 审计后三条新增留档 -->
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

| **N-7** | **`like-rpc` 列表接口的 DB 回源：Go 恒空，Java 返回真实数据** | `GetUserLikeList` / `GetTargetLikerList` 在 Redis 未命中后的 DB 回源路径上，Go 因 **GORM 列名不匹配**而恒返回空列表：Go 结构体字段是 `CreateTime int64`（无 column tag，GORM 命名规则推出列名 `create_time`），而查询 `Select("id, target_type, target_id, created_at")` 取的是 `created_at` ⇒ **该列映射不上、被丢弃、`CreateTime` 恒为 0** ⇒ `if timestamp == 0 { continue }` 对每一行都成立 ⇒ `list` 恒空、`nextCursor` 恒 0；但 `isEnd` 仍按 `len(dbResults) > limit` 置 `false`，`ExpireCtx` 仍会执行 ⇒ 返回 `isEnd=false` + `nextCursor=0` 的**自相矛盾态**。**连带**：Redis ZSet 的 ZADD 只在回源成功后执行 ⇒ 该路径**从功能上是死的** ⇒ Go 侧冷缓存下用户点赞列表 / 作品点赞人列表实质不可用，且客户端会反复请求第一页。Java 侧 `LikeRecordMapper.java:121,139-147` 显式 `SELECT ... created_at` 映射到 `OffsetDateTime`，`LikeDomainService.java:439-466,539-563` 取到真实 Unix 秒 ⇒ 返回真实分页数据 | **用户 2026-10-22 拍板：Java 现状即为「已修正的 bug」，两侧不一致属有意，不再对齐。** 理由：Go 的空列表是**缺陷不是设计**（GORM 命名规则不匹配，可自行验证），照搬它等于让 Java 复刻一个让功能不可用的 bug。⚠️ **代价必须同时记住**：灰度期两端同跑时 **Java 会返回 Go 永远返回不了的数据**，调用方若按 Go 的行为（恒空）做过降级或兜底，切换时会出现行为跳变。**实证**：真 PG + gorm v1.31.1 复刻 Go 结构体，`schema.LookUpField("created_at")` 返回 `nil`（`CreateTime` 的 `DBName` 实为 `create_time`）；插入真实行后 `Scan` 得 `CreateTime=0`（期望 `1792670400`） | Go 侧补 `column:created_at` tag 后即可撤销本条签字。⚠️ **在此之前任何人不得以「必须对齐 Go」为由把 Java 改回空列表** —— 那是把一个已确认的缺陷搬过来。另：Go 侧该功能仍不可用，**Go 分支的冷缓存路径需在灰度方案里单独说明** |

##### ⚠️ N-3 勘误（2026-10-21，commit 690ba4c 之后）

留档初版（commit `9b2303c`）写「Go admin 侧的 uid 走 `form:"uid,string"` 绑定，**key 缺失时静默保留零值**且不报错 ⇒ `uid=0` 进 rpc ⇒ 查无此人 ⇒ 返回 1003」。**前半段理由已被实测推翻**：

- go-zero `httpx.Parse` 对 `form:"uid,string"` 的 required 缺失行为是**直接 400**（`go-zero@v1.10.2/core/mapping/unmarshaler.go` 的 `parseOption` 把所有非 `optional` 字段标 `Required=true`，缺失走 `newInitError` 报 `field %q is not set`），不是「静默保留零值」。
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

<!-- @move AGENTS.md L2523-L2585 · §6.2.2 users/admin 语义审计修复清单 #71186443 -->
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
| DRIFT-FRAMEWORK-001 | 共享 bug | `HttpMiddleware$ObservabilityResponseWriter.setStatus/sendError` | 此前只缓存到 `this.status`，等 `chain.doFilter` 跑完再 `flushTo(dst)` 才写底层。**实测**：Spring `HttpEntityMethodProcessor.handleReturnValue` 末尾调 `outputMessage.flush()` → `wrapper.flushBuffer()` → 底层 `MockHttpServletResponse.setCommitted(true)`；后续 `flushTo` 的 `dst.setStatus(401)` 被 `MockHttpServletResponse.setStatus(int)` 的 `if (!isCommitted())` 守卫吞掉。**结果**：`@ExceptionHandler(AuthenticationException.class)` 返回 `ResponseEntity.status(UNAUTHORIZED)` 也只输出 HTTP 200 + `{code:1006}` —— 整条 admin-api / user-api 的「鉴权短路 → 401」在所有运行期看起来全绿，实则全部被降级。这是把 DRIFT-009 与 N-1「推到 401」的修复完全埋没的根因 | setStatus / sendError 立即 propagate 到 underlying（`((HttpServletResponse) this.original).setStatus(sc)`，每次**同时**更新 `this.status`，因为 timeout/canceled 分支的合成响应仍走 `rec.statusCode()` 读）；body 仍 buffer；setHeader 仍 buffer（不影响 commit 状态）。**全栈验证**：admin-api 83 + admin-rpc 36 + user-api/user-rpc 5 modules 全绿<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **已修**（共享底层 bug ⇒ 修法「setStatus / sendError 立即 propagate 到 underlying」）⇒ **现行态 = ✅ 维持已修**（**修法仍在共享层现行代码里**）。依据：**RAG** `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:942-958`（`public void setStatus(int sc)` ⇒ `writeToOriginal(resp -> { this.status = sc; … resp.setStatus(sc); })` ⇒ **立即 propagate**）· `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:960-973`（`public void sendError(int sc)` ⇒ `this.status = sc; ((HttpServletResponse) this.original).sendError(sc);` ⇒ **同步 propagate**）⇒ **与本行「修法」列逐字相符**。守门（**文件在位**）：`service/common/src/test/java/com/rag/common/observability/HttpMiddlewareTest.java:252`（`non200StatusSurvivesFlushBufferCommit`）+ `service/users/admin/admin-api/src/test/java/com/rag/users/admin/api/AdminApiMvcPipelineTest.java` + `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/bootstrap/AdminRpcContainerWiringTest.java`。⚠️ **指针订正**：`docs/DRIFT/users/user/api.md:2312` 引的 `HttpMiddleware.java:560-575` **已过期**（现行 = `:942-973`）。⚠️ **Go 侧不适用**：本条是 Spring `MockHttpServletResponse` 的 commit 时序问题，**不是与 Go 的行为差异** ⇒ 无 Go 侧指针（非漏核）。⚠️ 本轮**未复跑** `mvn -pl admin-rpc,admin-api -am test` ⇒ 守门只证明**用例存在**，不证明**当前全绿**。核对日期 2026-10-30。<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-admin-errors.sh` ⇒ **EXIT=0**（2026-10-30 04:30:58 · 6s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [1/4] 编译 admin-proto 桩 + 计算 classpath ⏐ ==> [2/4] 编译 GrpcAdminDualPortRunner ⏐ ==> [3/4] 种 probe user（go_db.users / rag_db.users_user） ⏐ ==> [4/4] 跑实验(写入 grpc-admin-results.txt) ⏐ code_mismatch>0 ⇒ 语义级差异，exit 1 ⏐ des。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。|

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

<!-- @move AGENTS.md L2586-L2611 · §6.2.3 users/admin 的 UNRESOLVED -->
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



---

<!-- @round 2026-10-23 21:45 · users/admin 域审计轮 · 共享台账 -->

# 2026-10-23 审计轮（users/admin 域 · 共享台账增补）

> **核对日期**：2026-10-23　　**范围**：`admin-rpc` + `admin-api` + `admin-proto`
> ⚠️ **本节是增量追加**，上文 §3.4.3 / §6.2.2 / §6.2.3 的原始记录**一字未删**。
> 逐条结论见 [`rpc.md`](rpc.md) 与 [`api.md`](api.md)；本节只放**跨组件**的部分。

## 1. 本轮的证据强度（**域级**）

| 面 | 证据强度 | 命令 / 产物 |
|---|---|---|
| **admin-rpc（gRPC 层）** | ✅ **双侧活体对拍**（本轮首次） | `tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up` ⇒ **cases=18 / code_mismatch=0 / payload_diff=0 / description_diff=5**，`EXIT=0`；产物 `tools/difftest/grpc-admin-results.txt` |
| **admin-api（HTTP 层）** | ✅ **双侧活体对拍** | `tools/difftest/diff.py --scenario all` ⇒ **steps=39 / asserted=10 / byte_equal=29 / unexpected=0**，`EXIT=0` |
| **admin-proto** | ✅ 静态双侧（逐字节） | `tools/difftest/check-proto-static.sh` ⇒ `EXIT=0`（13 rpc） |
| **admin-rpc（真 PG 守门）** | ✅ 真 PG 16 容器 | `mvn -o -pl service/users/admin/admin-rpc -am test` ⇒ **88 tests / 0 failures / 0 errors / 0 skipped**（真 PG 用例 **33** 条） |
| **admin-api（真容器守门）** | ✅ 真 Redis 容器 + 真 Spring 上下文 | 同上 ⇒ **121 tests / 0 failures / 0 errors / 0 skipped** |

> ⚠️ **「skipped = 0」是本轮硬门槛**（AGENTS.md §0 的 2026-10-23 新纪律）：
> `task-rpc` 曾因漏配 `RYUK_DISABLED` 被 `@EnabledIfDockerAvailable` **静默全跳**，
> 而 reactor 仍报 `BUILD SUCCESS`。
> 本域两个模块的 pom **都注入了** `TESTCONTAINERS_RYUK_DISABLED=true`
> （`admin-rpc/pom.xml:212-217`、`admin-api/pom.xml:165-170`），
> 且实测 `skipped=0` ⇒ **真 PG / 真 Redis 确实跑了**，不是假绿。
> 复核命令：`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml`

## 2. 🔴 本轮推翻的三条既有结论（**均在本域内**）

| # | 既有结论 | 位置 | 本轮实测 | 处置 |
|---|---|---|---|---|
| 1 | 「admin-rpc 同款 `status=0L` 未修」 | `AGENTS.md` §0 | 🔴 **证伪**：Go 把 `status IS NULL` 读成 **0**（不是「读不出来」），RAG 也是 0 ⇒ **一致** | 本组件**不改**；`AGENTS.md` 那行建议撤销（不在本 worker 所有权内） |
| 2 | 「Go 的语义是『NULL 读不出来』⇒ Java 应 fail-closed」 | `users/user/rpc.md` DRIFT-USERRPC-V-001 | 🔴 **证伪**（GORM 机制 + 双侧活体对拍 + 独立 Go 探针，三路一致） | **跨组件上报**（见 `users/user` owner）⇒ 与 §3.4.3 的教训同款：**「签字 + 防回退测试都齐了，结论仍可能是错的」** |
| 3 | §5.1 错误映射全表的「Go desc」一栏写中文并判 EQUIVALENT | `admin/rpc.md` §5.1 | 🔴 **5 处错**：Go 那几处的字面量在仓库里是**乱码字节**（UTF-8→GBK 误解码往返） | 见 `admin/rpc.md` §2（DRIFT-ARPC-007） |

> ⚠️ **第 2 条值得单独强调**：它与 §3.4.3 末尾那段教训（「N-3 / N-6 都经过了签字、都有防回退测试，
> 而它们描述的行为**从未存在过**」）**完全同型** —— 这次是**反方向**：
> 这次是一条**真实存在**的行为被台账误判成不存在，于是「按台账修」会**制造**新的漂移。
> ⇒ **台账里的「Go 行为是什么」这一栏，本身也需要运行证据**，
> 不能只靠「读源码 → 推机制」—— §3.3.1 的纪律同样适用于**对 Go 侧的断言**。

## 3. ⚠️ §3.4.3 的 N-4 / N-5 需要重新审视（**本轮新增证据**）

| 条目 | 原签字文本 | 本轮相关证据 | 建议 |
|---|---|---|---|
| **N-4**（`operator_id` 不恒为 0） | 「Go 的 `ctx["uid"]` 永远取不到 ⇒ `OperatorId` 恒 0；RAG 传真实 uid。该字段不参与任何业务判定，只写进 OTel span 的 `audit.operator_id`」 | ① admin-rpc 侧核到：`operator_id` **只在 `createInviteCode` 里用于查 admin**，其余 5 个方法只是审计字段 ⇒ 「不参与业务判定」**成立**；② 但 `admin/rpc.md` DRIFT-ARPC-006 实测：**RAG 侧一个 `audit.*` 属性都没有** ⇒ N-4 的原始理由（「恒 0 会让审计日志失去操作人信息」）在 RAG 侧**并不成立** —— RAG 本来就没有那个 span | N-4 的**行为**（RAG 传真实 uid）可以保留，但签字文本里「只写进 OTel span 的 `audit.operator_id` 属性」这一句**需要更正**（RAG 侧没有手写 span）。⇒ 建议与 DRIFT-ARPC-006 一起重签 |
| **N-5**（JWT HMAC 密钥长度） | 「Go 用 15 字节原始 secret；RAG zero-pad 到 32 字节 ⇒ 两侧 token 互不认」 | 本轮未复验（未涉及 admin-api 的登录 token 互认测试） | 结论未变，但本轮**未复跑** ⇒ 按 §6.2 记 `UNKNOWN` |

## 4. ⚠️ §6.2.3 A-U2 的记录需要更新（**证据强度变了**）

| 项 | 原记录（2026-10-21） | 本轮（2026-10-23） |
|---|---|---|
| **A-U2**（两端运行时对拍） | 「已关闭（两侧）：RAG 侧实证 + Go 侧由 `tools/difftest` 补齐，39 步 / 39 同 / 0 已签字 / 0 未预期差异」 | **重跑并更新**：`steps=39 / asserted=10 / byte_equal=29 / unexpected=0`。⚠️ **「39 步」这个数字没变，但内容变了**：现在有 10 步是**已登记的签字差异**（不再算「同」），29 步是逐字节相同。⚠️ **且这 39 步只是 HTTP 层** —— gRPC 层此前**零证据**，本轮才补上（18 用例，code 全同） |

> ⚠️ **`diff.py` 不是可重复运行的**（`admin.update` 会改 admin 密码、`admin.delete` 会删用户）
> ⇒ 同一库上第二次跑会让后续带 token 的步骤全变 401，而 diff.py 仍判「两侧相同 ⇒ OK」。
> ⇒ **每轮必须先 `run.sh --keep --serve-only`（重建库）再跑，且只跑一次。**

## 5. user 侧 / users-common 待裁决问题（汇总，**本 worker 无权改**）

| # | 问题 | 归属 | 出处 |
|---|---|---|---|
| **U-1** | 🔴 `users/user` 的 **DRIFT-USERRPC-V-001 结论被证伪** ⇒ user-rpc 的「status 为 null ⇒ fail-closed 抛 INTERNAL」**与 Go 相反** | users/user owner | `admin/rpc.md` §3 |
| **U-2** | 🟡 **2026-10-27 订正：定性为「DDL 层差异 · 行为层零差异」⇒ 建议保持 RAG NOT NULL ⇒ 落点无动作**（Go 读 NULL ⇒ **0** 且不报错；RAG NOT NULL ⇒ 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**）⇒ ⚠️ **原写「🔴 `NOT NULL` 与 Go 的可空不一致」只是 DDL 层陈述，未带「行为层零差异」这一层** ⇒ 且**放开可空反而制造新漂移**（RAG `callSettersOnNulls=false` ⇒ 读出 null ≠ Go 的 0）⇒ ⇒ **不需要联合裁决**；⚠️ **销号签字不在 G1 写权面** ⇒ 仍待 G4 / 合并收口执行者确认 | users-common owner（**但落点无动作**） | `admin/rpc.md` §3 · 逐层证据链见 [`user/rpc.md` 的 V-002 订正块](../user/rpc.md) |
| **U-3** | ✅ **已修**（2026-10-23 修复轮 §1.1）：`admin_admin.extra_info` 已由 `jsonb` 改为 `text`（对齐 Go 的 `serializer:json`），配套新增 `JsonTextTypeHandler`（text 列专用，**不复用** `JsonbTypeHandler`） | ~~users-common owner~~ **本域已改** | `admin/rpc.md` 修复轮 §1.1 |
| **U-4** | ✅ **已修**（2026-10-23 修复轮 §1.2）：`admin_admin` / `admin_invite` 的 **6 个时间列**已改为 `timestamptz`，实体侧同步改 `OffsetDateTime` | ~~users-common owner~~ **本域已改** | `admin/rpc.md` 修复轮 §1.2 |
| **U-5** | ✅ **已修**（2026-10-23 验证轮）：两份 DDL 注释里「**GORM 默认表名 admin_admin / admin_invite**」是**事实错误** —— Go 的 `TableName()` 返回 `admins` / `admin_invites`（`data_model.go:16-18` / `:31-33`）。同时订正了 `UserEntity.java` 的同款错误说法（「GORM 默认表名 user_user」⇒ 实际是 `users`） | ~~users-common owner~~ **本域已改** | `admin/rpc.md` 审计轮 §5 第 3 项 |
| **U-6** | ⚠️ `users/user` 域另有 **3 个 Go 文件**的字面量同样被转码损坏 | users/user owner | `admin/rpc.md` §5 |
| **A-1** | ✅ **已闭合**（2026-10-30 订正 · 前提已不成立）—— 原记「`user-api` 的 400 响应**同样缺 `charset`**（`user-api/.../advice/GlobalExceptionHandler.java:287`）」⇒ ⇒ 现行 `:287` = `headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));` ⇒ **已显式钉 charset** ⇒ 修复提交 `c79d1131`（`align(users/user): 修 DRIFT-USERAPI-006`）⇒ ⇒ **销账** | users/user owner（已由其 owner 修掉 · 本域零代码改动） | `admin/api.md` §5 |
| **A-2** | ⚠️ **~~`docs/Intentional_Deviations/users/**` 下 6 个文件全是 0 字节（空占位）~~**<br>**2026-10-23 验证轮复核：admin 域 3 个已补齐为正式签字档（`_shared.md`/`api.md`/`rpc.md`），`users/user` 域 3 个仍空 + `users/_shared.md` 仍空 ⇒ 归 users/user 域** | `wc -c` 实测 | N-1 / N-4 / N-5 / SD-2026-10-23-06/07/08 现已进签字档 |
| **A-3** | ⚠️ `tools/difftest/diff.py` 把 DRIFT-ADMINAPI-004 标成「N-1 已签字」，但 N-1 只签了 body 形状 | tools owner | `admin/api.md` §5 |
| **A-4** | ⚠️ `tools/difftest/check-plaintext400-headers.sh` 只打 user-api，**没有 admin 入口**；且只断言「Content-Type 含 text/plain」，**抓不到 charset 差异** | tools owner | `admin/api.md` §5 |
| **U-7** | ✅ **已答（2026-10-26，users/user 域 owner 提问）** —— **ADJ-04**：admin 侧的写入路径**是否依赖 `users_user` 时间列的 DEFAULT `CURRENT_TIMESTAMP`**？<br>**答：不存在 INSERT 依赖** —— admin-rpc 对 `users_user` **只 UPDATE / DELETE，零 INSERT**（`AdminDomainService.java:688/730/1040/1192` = `userMapper.update(...)`；`:1088` = `userMapper.delete(...)`；全模块 `userMapper.insert` **零命中**）⇒ **不依赖 DEFAULT ⇒ 该差异对 admin 侧不显现**。<br>⚠️ **但同一机制在 admin 自己的两张表上成立**（`admin_admin` / `admin_invite` 的 INSERT **确实**靠 DB DEFAULT，实体 `insertStrategy = NEVER`）⇒ 那属于 **`admin/rpc.md` 的 `DRIFT-ARPC-001` 范围补充**（`create_time` 在 INSERT 路径上，机制与 `update_time` 不同、**实际不可观察**）⇒ 详见该补充段 | ~~users/user owner~~ **本域已答** | `admin/rpc.md` ARPC-001 范围补充 |
| **U-8** | ✅ **已闭环（只读指针 · 原文订正归 users 域级 owner）** —— **ADJ-03**：`users_user` **建表责任交叉**（`user-rpc` ↔ `admin-rpc`）的「双栈并发未复跑」结论**已过期**：user 半边在 **2026-10-24 第五轮 §4** 做了**双侧对称并发启动实测**（两 rpc 同时对同一个**空库**启动，共 **9 轮**，2026-10-24 独立复现，结论逐条一致）⇒ 两侧**都能起来**、表**都建齐**、**0 致命错误**；机制不同（RAG 靠 advisory lock **机制性**串行；Go 靠 GORM `AutoMigrate` 的 check-then-act，**机制上仍有竞态窗口**，实测未撞 ≠ 机制保证）⇒ 结论 **OBSERVABLE EQUIVALENT**（可观察结果一致），**不是「机制等价」**。<br>⇒ ⚠️ **本域不改 `docs/DRIFT/users/_shared.md`**（域级共享文件，两个子域都会写 ⇒ 按 §5.3 合并时统一更新）⇒ 原文订正由 users 域级 owner 落 | ~~users 域级~~ **只读指针** | `users/user/rpc.md` §9.2 + §4「建表责任交叉」行 |

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


### lane W11

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| U-2 | D（与 V-002 同一处列差异；经分析确认不实现） | P2 | 否 | Go `service/user/admin/rpc/internal/model/data_model.go:41`（admin 侧的 `User` 模型同样 `Status int64` + gorm tag `column:status;default:0` ⇒ **无 `not null`** ⇒ 可空）＋ `service/user/admin/rpc/internal/model/db.go:15`（`db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` ⇒ admin 侧把 `User` 建在**同一张** `users` 表上，与 user-rpc 共用）vs RAG `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（`status      BIGINT NOT NULL DEFAULT 0,` ⇒ 不可空）⇒ **与 `DRIFT-USERRPC-V-002` 是同一处列**（admin-rpc ↔ user-rpc 共表）⇒ **可空性差异仍成立**；⚠️ admin 侧原写的 🔴「`NOT NULL` 与 Go 的可空不一致」是**只带 DDL 层、未带行为层**的陈述（`docs/DRIFT/users/admin/rpc.md:1476`）⇒ 已被 2026-10-27 订正块取代（`docs/DRIFT/users/admin/_shared.md:212` 明写「落点无动作」）⇒ ⇒ **经分析确认「保持 RAG NOT NULL」＝ 不实现**（Go 读 NULL ⇒ **0** 且不报错；RAG NOT NULL ⇒ 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**；**放开可空反而制造新漂移**）⇒ 归 **D**；⚠️ **销号签字不在 G1 写权面** ⇒ 仍待 G4 ／ 合并收口执行者确认（**无代码动作**）；**P2**（零可观察行为影响 ⇒ **不阻断阶段一**） | 2026-10-30 |

### lane W32

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| `U-1` | D（经分析确认不实现：两侧行为已一致，台账登记的那条已不存在） | P3 | 否 | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status     int64             \`gorm:"column:status;default:0"\`` ⇒ **值类型 · 无 `not null` tag**）＋ `service/user/user/rpc/internal/logic/loginlogic.go:67`（`if user.Status == 1 {` ⇒ status 读成 0 ⇒ 不判封禁 ⇒ 落到 `:75-78` 的 `LoginResp{Uid, Status: 0}` ⇒ **放行登录**；`:56` 的 `codes.Internal "DB查询失败"` 只在**非 sentinel** 错误时可达）vs RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:260`（`if (user.getStatus() != null && user.getStatus() == 1L) {` ⇒ NULL 行读出 0L ⇒ 条件为假 ⇒ 落到 `:270-273` 的 `LoginResp{uid, status=0}` ⇒ **放行登录**）＋ `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:110`（`private Long status = 0L;` ⇒ MyBatis `callSettersOnNulls=false` 跳过 setter ⇒ NULL 行读出 **0L**）⇒ ⇒ **两侧同形**（`status IS NULL` ⇒ 0 ⇒ 不判封禁 ⇒ 放行登录）⇒ 台账 `docs/DRIFT/users/admin/rpc.md:1475` 与 `docs/DRIFT/users/admin/_shared.md:211` 登记的「user-rpc 的 fail-closed 抛 INTERNAL **与 Go 相反**」**已不存在** ⇒ **差异已消失**（证伪关闭：撤销 commit `9ae121d7`「撤销 V-001 反向判定 —— Go 侧 NULL status 是 fail-open 而非 fail-closed」⇒ fail-closed 守卫与被删的 `= 0L` 同批回滚）⇒ ⇒ **经分析确认「两侧行为一致 ⇒ 无需任何实现动作」＝ 不实现**（D 类构成要件「经分析确认」＋「明确理由」齐备；**不是「还没查」**）⇒ 归 **D**；⚠️ 台账既有 🔴 标记**已过期**（按「原值一律保留」纪律不回改 ⇒ 由合并收口执行者订正）；**P3**（残留可观察差异 = **0** ⇒ 不命中阶段一 P0 九条 ⇒ **不阻断阶段一**）｜ 机制三段（**本轮独立复核，未沿用台账指针**）：`gorm.io/gorm@v1.31.1/scan.go:19`（`values[idx] = reflect.New(reflect.PointerTo(field.FieldType)).Interface()` ⇒ int64 字段的 dest 是 **`**int64`**）＋ `gorm.io/gorm@v1.31.1/schema/field.go:616-622`（`case **int64: if data != nil && *data != nil` ⇒ 内层指针为 nil 时**不 SetInt** ⇒ 字段留零值 0 且**不报错**）＋ Go 1.25.11 `database/sql/convert.go:431-435`（`case reflect.Pointer: if src == nil { dv.SetZero(); return nil }` ⇒ **不报错**；`:438-440` 的 "unsupported" 只对**非指针** int64 生效；版本 pin 见 Go `go.mod:28` `gorm.io/gorm v1.31.1`）＋ RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:187`（`**该理由已被证伪并撤销**`）＋ `:201`（`⇒ 守卫已删除。status 为 NULL 时 UserEntity 的 0L 初始化值生效`）＋ `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（`status      BIGINT NOT NULL DEFAULT 0,` ⇒ RAG 侧 NULL **根本不可达** ⇒ 只会更窄、不会更宽）＋ `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcLoginNullStatusReadsAsZeroTest.java:158`（`assertEquals(0L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),` ⇒ 守门已**翻成 Go 一致的断言**：status=0 成功、**不是 INTERNAL**；旧守门 `UserRpcLoginNullStatusFailClosedTest` 全仓**零命中** ⇒ 锁错误答案的那条已随撤销一起清掉）＋ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/UserEntity.java:136`（admin 侧 `private Long status = 0L;` ⇒ 与 Go 的 `service/user/admin/rpc/internal/model/data_model.go:41` 同形 ⇒ admin 侧本就「与 Go 一致、不改」）＋ 台账 `docs/DRIFT/users/user/rpc.md:517-529` 的 2026-10-29 真实性复核亦确认「守卫确已删除」；⚠️ 证据强度 = **静态源码逐层对照 ＋ 机制源码当场复核 ＋ 守门测试在位**（本轮**未复跑**该 Testcontainers 用例 ⇒ 按 AGENTS.md §0 读作「在该证据强度下一致」） | 2026-10-30 |
