<!-- @move AGENTS.md L1089-L1142 · §0 补网：admin-api / user-api 继承共享组件却无测试 -->

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（首行直接是 2026-10-22 的「补网」节，且中段还留着「🔴 仍然 OPEN：V-004 的可观察半边」这类**已被同文件第三轮推翻**的断言）⇒ **当前状态一律以文件末尾「2026-10-24「台账 vs 实际」机器复核（users/user 域 · 首次）」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**
#### 🔴 补网：admin-api / user-api **自动继承了共享组件，却一条测试都没有**（2026-10-22 晚）

上一节那次 admin-api 回归之所以**没有任何一条本模块测试抓到**，根因不是判据写错，而是
**admin-api 从来没有发过非 JSON 的 Content-Type**。`GoStyleJsonBodyConverterConfig` 被
`com.rag.common` 的组件扫描自动应用到所有 api 模块（admin / user / hot 全部），
而 like-api 有 7 条、message-api 有 4 条对拍用例，**admin-api 与 user-api 各 0 条**。
⇒ 这是一处**已知的、无人看守的行为变更**：共享层下一次改动照样能把它们打坏，而 CI 全绿。

补 `AdminApiGoStyleBodyContentTypeTest`(6) + `UserApiGoStyleBodyContentTypeTest`(6)，与 like/message 同构。

##### 判据测试刻意走 **public 的 `/x/v1/login`**，这是选择而不是顺手

`LoginReq` 两个字段都是 go-zero **标量必填**（`types.go` 里无 tag ⇒ 非 `,optional`），
所以「body 被丢弃」与「body 为空」必然撞出**同一条错误文本**，可以做逐字节对比。
而它**不需要 token**（Go `routes.go` 第一段 `AddRoutes` 不带 `WithJwt`）
⇒ 判据测试完全不碰 Redis，把「body 解析」与「鉴权」两层**隔离开**。

⚠️ **刻意没有用 `/admin/v1/getuser` 做这条断言**：Go `types.go` 的 `GetUserReq.Uid` 标的是
**`form:"uid,string"` 而不是 `json`**。没有 `json` tag 时 go-zero 回退到**字段名**，
那条「key 到底叫什么、是否必填」的推导链在本次范围外且未实测
⇒ 拿它当判据的支点，等于把一个**未验证的前提**写进断言（§3.3.2）。

##### 🔴 变异实测暴露了一件**反直觉**的事：只比「两条响应相等」是**不够**的

| 变异 | admin-api | user-api |
|---|---|---|
| `goReadsBody` 改成「永远返回 true」 | **3 条红** | **3 条红** |
| `canWrite(ResolvableType,...)` 改成 `true`（复现 2026-10-22 事故） | **3 条红** | **3 条红** |

两次变异都用 `javap -p -c` 读 `~/.m2` 里的 class 确认过**真的生效**
（分别看到 `ldc MUTATION_ALWAYS_READ` 与 `iconst_1; ireturn`）。

⚠️ **第二项变异才是本节最有价值的产出**：它的失败形态是

```
expected: <field "username" is not set
 but was: <"field \"username\" is not set\n">
```

**状态码仍是 400、Content-Type 仍是 `text/plain`，只有 body 被转义** —— 与事故当天逐字一致。
而此时「`{}` 与 `text/plain` 两条响应逐字节相等」那条断言**依然是绿的**，
因为**两侧被同样地转义了，相对关系没变**。

⇒ **两条响应互相比较，只能证伪「判据不同」，证伪不了「两条一起被写坏」。**
必须再有一条**独立于响应之间比较**的**精确文案断言**
（`assertEquals("field \"username\" is not set\n", body)`）才抓得住。
⇒ 写「两侧对拍」类测试时，**「彼此相等」与「等于某个绝对值」是两种不同的断言，不能互相替代**。

##### 附带：这两类的 400 是 `text/plain`，与 like/message 的 `200 + envelope` 不同

测试里同时断言了 `Content-Type: text/plain` + `X-Content-Type-Options: nosniff` +
**尾随换行**（Go 的 `fmt.Fprintln`），就是为了把「共享判据、不共享表现」这条线在测试里钉死 ——
否则日后有人「顺手统一」两个域的失败形态，这 12 条不会有任何一条报警。

<!-- @move AGENTS.md L1978-L2066 · §3.4.4 service/users 基础组件审计后新增留档 -->
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
| **⚠️ 它不能做什么** | **不能取消在途 IO**。JDBC 驱动不响应线程 interrupt，这是 Java 侧的结构性限制，属 §3.4.1 B-1，**不是 DRIFT-018 能修的**。B-1 由 `DeadlineQueryTimeoutInjector` 的 `setQueryTimeout` 关闭（已实施并端到端验证：查询滞留 4303ms → **176ms**，与 Go 的 182ms 同量级）<br>🔴 **2026-10-26 补记（装配层前提）**：上面那句「已实施并端到端验证」**隐含一个从未被断言过的前提** —— 那两个拦截器**真的进了 MyBatis 的插件链**（`@Import` 只是**声明点**，收不收进链是**消费点**上的运行期事实）。端到端实测证明它**事实上是通的**，但那是**一次性证据、不是常驻断言** ⇒ 谁摘掉 `@Import`，测试全绿、线上才炸（缺翻译器 ⇒ 57014 不翻译 ⇒ 落进 `catch (Exception e)` ⇒ 被改写成 `INTERNAL` ⇒ api 渲染 **5001** 而 Go 是 **1015**）。<br>⇒ **已补常驻守网**：`users/user` 域 `UserRpcContainerWiringTest` +3 条（插件链 / 挂载点 / 负控，含 M1·M2·M3 三次变异验证）、`users/admin` 域 `AdminRpcContainerWiringTest` +2 条 ⇒ 详见 [`users/user/rpc.md`](user/rpc.md) **第十二轮**（**覆盖缺口闭环，不是新漂移**）<br>🔴 **2026-10-26 晚补记 · 全仓 10 个 rpc 组件的 B-1 插件链盘点（只读，跨域）** —— 我域先前报给 global 的待办「其余 9 个 rpc 组件是否注册 B-1 两个拦截器」**已实测结掉**：<br>**判据必须逐个看「有无 SQL 路径」**，不能一刀切（SQL 路径 = 有 `*Mapper.java` 或 `@Select/@Insert/BaseMapper/DataSource/JdbcTemplate`）：

| rpc 组件 | `SqlTimeoutStatusTranslator` 注册点 | `DeadlineQueryTimeoutInjector` 注册点 | `*Mapper.java` | 判定 |
|---|---|---|---|---|
| `article-rpc` / `like-rpc` / `message-rpc` / **`user-rpc`** / `admin-rpc` / `comment-rpc` / `favorite-rpc` / `follow-rpc` / `task-rpc` | 各 **1** | 各 **1** | 2~7 | ✅ **9/9 全注册，1:1 无一遗漏** |
| `hot-rpc` | **0** | **0** | **0** | ⛔ **无 SQL 路径 ⇒ B-1 对它不适用**（不是「该注册没注册」） |

⇒ **结论：全仓无缺口** —— 有 SQL 路径的 rpc 组件**全部**注册了两个拦截器；唯一没注册的 `hot-rpc` **一条 SQL 都没有**（`Mapper.java` 0 个、全仓 grep `@Select|@Insert|@Update|@Delete|BaseMapper|DataSource|JdbcTemplate` **零命中**）⇒ ⇒ **admin 域先前「hot-rpc 未注册 ⇒ 前提不成立 ⇒ 销案」的结论成立，并补上了根因**。<br>⚠️ **方法论**：grep 命中数**不等于**注册数（`@Import({...})` 是**多行**块，单行正则会全数漏掉；且 javadoc 里的提及也会被计上 —— `hot-rpc` 的 `DeadlineQueryTimeoutInjector` 只出现在**注释**里）⇒ 盘点必须读**注册块上下文**，不能只数命中。 |

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
| **DRIFT-024** | **text/plain 400 的响应头丢失**（2026-10-22 E-1 收口时发现，**DRIFT-006 的另一半从未真正对齐**） | 每次 400 都带 `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff` + `Content-Length` | **`Content-Type` 与 `nosniff` 全部缺失**，状态行连 reason phrase 都没有，传输退化为 `Transfer-Encoding: chunked` + `Connection: close` | `HttpMiddleware$ObservabilityResponseWriter.setHeader` 只收进 `capturedHeaders`、等 `flushTo` 才写回 underlying，而那时底层已 committed，Tomcat **静默忽略**。已改为三个覆写点（`setHeader`/`addHeader`/`setContentType`）实时 propagate，与 `setStatus` 同一策略。守门：`tools/difftest/check-plaintext400-headers.sh`（**必须真实 Tomcat**） |

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

<!-- @move AGENTS.md L2067-L2191 · §3.4.5 DTO 边界矩阵产出差异 R1~R5 -->
#### 3.4.5 `service/users` **DTO 边界矩阵**产出的差异：R1~R4 已修、R5 新发现（2026-10-21 起，§6.2.6 **E-3**）

按 DTO 逐字段展开边界组合（字段全缺 / 部分缺 / 类型错 / 超长 / 特殊字符 / 越界），
Go 与 RAG 双端对拍 **273 条**。首次产出 **77 条 NEW_DRIFT**，收敛到 **4 个根因**，
**UNCLASSIFIED = 0**（连续三次运行结果逐字一致）。
**2026-10-22：R1~R4 全部已修并经端到端复测（NEW_DRIFT 77 → 0）；
修复过程中又暴露第 5 条根因 R5（曾被分类器误归入 R3）。** 复现：

```bash
./tools/difftest/run.sh --keep --serve-only   # 起两套栈（**必须完整构建**，见下方警告）
python3 tools/difftest/matrix.py             # 运行时半（273 条）
python3 tools/difftest/check-ddl-static.py   # 静态半（R3 的 DDL 证据）
python3 tools/difftest/r5-order-probe.py     # R5 专项（用**真邀请码**补齐矩阵盲区）
```

> ⚠️ **不要用 `--no-build`**：该参数**不会把新 jar 拷到 `$RUN_DIR/bin/`**，
> 起的是旧副本 —— 表现为「修复没生效」，是本项目反复误判的头号来源。
>
> ⚠️ **不要在被矩阵跑过的 cluster 上取证**：`admin.update` 的边界用例会真的改写
> `admin/admin` 的口令，跑过一轮后两侧登录均失败（`1002`）。取证一律用
> `run.sh` 重建的**全新 cluster**。
>
> **本节 5 条均已由用户逐条签字并有防回退测试**。按 §3.4.3 末尾那条纪律 ——
> 签字 + 防回退测试**并不足以保证结论正确**（N-3 / N-6 都签了字、都有防回退测试，
> 而行为从未存在过）—— 每条下面都只写**实测到的**行为，并附**独立于矩阵**的取证命令。

| # | 根因 | 实测现象 | Go 出处 | RAG 修复前 | 状态 |
|---|---|---|---|---|---|
| **R1** | **JSON 标量被 Jackson 静默强转 / 截断**（64 条） | `{"username":99999}`、`{"password":true}`：Go `400`；RAG `200`+业务码。`{"page":1.5}`：Go `400`；RAG **按 page=1 执行** | Go 走 `encoding/json` 的类型检查 / go-zero 的 `processNamedFieldWithValueFromString`（先字符串化再 `strconv.ParseInt`） | 字段是 Java `String` / `Long`，Jackson 默认允许 number→String、bool→String，并**向零截断** float→Long | | ✅ **已修**：新增 `GoStyleCoercion`（Jackson 3）+ 两端 `WebMvcConfig` 注册 `JsonMapperBuilderCustomizer`；锁死用例 `GoStyleParseSemanticsMvcTest`（14 条，真 MVC）
| **R2** | **显式 `null` ≠ 字段缺席**（4 条） | `{"extra_info":null}`：Go `400 field "extra_info" mustn't be nil`；**缺席该字段**则 Go `200`。RAG 两种都 `200` | go-zero `core/mapping/unmarshaler.go:856-862`：`mapValue == nil` 且非 `optional` ⇒ `mustn't be nil`，**对 map / slice 一样生效**；而「缺席」走 `processNamedFieldWithoutValue`，Map 分支不报错 | `RegisterReq` / `CreateAdminReq` / `UpdateSelfReq` / `UpdateUserReq` 四处 javadoc 都写着「Extrainfo 是 map ⇒ 不校验」，把 null 与缺席**混为一谈** | | ✅ **已修**：4 个 DTO 加 `extraInfoPresent` 标志 + `@JsonProperty` setter，`GoStyleRequired.explicitNull` 判「显式 null」；锁死用例同上
| **R3** | **DDL 列宽：`text` vs `varchar(255)`**（4 条） | 5000 字符 `username`：Go 写入成功；RAG `5001`/`5003`。反噬读路径：Go 库里有、RAG 存不下 ⇒ 登录时 Go 命中、RAG `1002`/`1003` | Go 的 GORM `AutoMigrate` 给 Go `string` 生成 **`text`**（无上限） | 4 份 `users-common/.../db/*.sql` 的注释自陈「列结构与**原 JPA `@Column`** 注解严格对齐」⇒ `VARCHAR(255)`。**全量 DDL 对比：35 列里 22 列类型不同**（12 列 text→varchar(255)、8 列 timestamptz→timestamp、2 列 text→jsonb） | | ✅ **已修**（用户签字）：4 份 `db/*.sql` 改 `TEXT` + 各补幂等 `ALTER TABLE ... TYPE text` 自愈；锁死用例 `UserRpcSchemaTextColumnTest`（4 条，真 PG）
| **R4** | **分页越界：GORM 静默抑制 vs RAG 直传 PG**（5 条） | `page=0` / `page=-1` / `page_size=-5` / `page=9223372036854775807`：Go 全 `200`+`code 200`；RAG 全 `5001` | `service/user/admin/rpc/internal/model/admin.go:159-160`：`Offset(int(offset)).Limit(int(pageSize))` —— GORM 只在 `offset>0` / `limit>=0` 时才生成子句，负值**静默忽略**并返回首页全量 | 负数原样交给 PG ⇒ `22023: OFFSET must not be negative` ⇒ `5001` | | ✅ **已修**：`getUserList` 改 `long`、新增 `goStylePagination` 逐字镜像 GORM `clause/limit.go` 抑制规则；锁死用例 `AdminRpcPaginationGormSemanticsTest`（7 条，真 PG）
| **R5** | **bcrypt 哈希抢在 invite / username 检查之前**（2 条；**曾被分类器误归入 R3**） | 见下方「R5 专节」实测表 | `createadminlogic.go` 事务闭包：`FindAdminInviteByCodeForUpdate` → `FindOneAdminByUsernameTx` → `cryptx.PasswordEncrypt` → `GetID` → insert | 事务外先 `PasswordUtil.encrypt`，72 字节上限抢跑 ⇒ 该报 1016/1001 的报成 5001 | ✅ **已修**（用户签字「完全对齐 Go」）：哈希移进事务、排在 W2 之后；锁死用例 `AdminCreatePasswordOrderRegressionTest`（9 条，真 PG，变异测试实证 6 条真红） |

> **R3 的条数依赖环境状态，不是稳定的 4**：写侧那 4 条（5001/5003）任何时候都在；
> 读路径另有 2 条（`user/admin.login.Username.long5000` 报 1002/1003）**只在 Go 库里
> 已经存在一条超长用户名行时**才出现 —— 那是**上一轮矩阵自己写进去的**。
> 本节基线 **4 条**取自 `./run.sh` 刚重建的全新 cluster（`down -v` 后 `serve-only`）；
> 在被矩阵跑过的库上重跑会变成 **6 条**。**根因分类两处完全一致，不要按总数判断 R3 变了。**

**R1 附一条反直觉的实测**（写下来免得下次又按 `,string` 标签想当然）：
Go 的 `json:"uid,string"` **不等于「只收字符串」**。go-zero 先把 JSON 值字符串化再
`strconv.ParseInt`，所以 `{"uid":1}`（数字）**两端都接受**（实测均 `1003`），
真正的分叉只在浮点。取证：`curl -d '{"uid":1.5}'` → Go `400 strconv.ParseInt: parsing "1.5"` / RAG `200 + 1003`，
且 RAG 的 `{"uid":1.5}` 与 `{"uid":1}` 响应逐字相同（截断）。
`page` 侧同款：`{"page":1.5}` 与 `{"page":1}` 响应逐字相同、`{"page":2.9}` 与 `{"page":2}` 相同。

**R1 的 `float→Long` 截断比 `→String` 强转更危险**：前者让请求**成功执行但作用在错误的行上**
（`banuser {"uid":1.5}` 等价于封禁 `uid=1`），后者只是把错的值存进去。

##### R1 修复踩的三个坑（Jackson **3**，不是 2）

1. 本工程用的是 **Jackson 3**（`tools.jackson.core:jackson-databind`），API 位置与 2.x 不同：
   `JsonMapper.builder()` / `MapperBuilder`，不是 `ObjectMapper`。
2. **`withCoercionConfig(Class, …)` 对全局规则无效**：实测 3.1.5 下完全不起作用，
   必须用 `LogicalType.Textual` 的 `CoercionInputShape` / `CoercionAction` 逐项配置。
   `GoStyleCoercion` 类注释里已写死这条，**防止下一个人重试**。
3. 规则只能**全局生效**：每端在 `WebMvcConfig` 注册一个 `JsonMapperBuilderCustomizer` bean。

**R1 有一条只对 json 通道成立**：Go 走 **form 通道**的字段值本就是字符串，
所以 `GetUserListReq.setKeyword(Object)` 需要**自己标量化**
（只接受 String/Number/Boolean，其余抛 `IllegalArgumentException`）——
全项目仅此一个字段走 form 通道的 `Object` 入参。
⚠️ 第一版曾用 `String.valueOf` 把 `{"a":1}` 变成 `"{a=1}"`，等于把「拒」变成了「静默接受」，已修。

##### R5 专节：矩阵为什么把它误分类成了 R3，以及真实分歧面（2026-10-22）

R3 修完后矩阵仍报 `R3 = 1`，一度以为 R3 没修干净。**实际是分类器的锅**：
`matrix.py` 的 `classify()` 有一条宽规则「`long5000` 且 RAG 报 5001/5003 ⇒ R3（列宽）」，
它把这条**顺序问题**顺手归进了 R3。

**真实分歧面比矩阵看到的大一倍**——因为 `matrix.py` 在 `admin.create` 这一族里
**恒定使用假邀请码** `MATRIX-INVITE-NOT-A-REAL-CODE`，只能命中「邀请码校验失败」
这条最早返回的分支，**永远走不到**「邀请码有效 → 后续检查」那一段。

新增 `tools/difftest/r5-order-probe.py` 用**真邀请码**补齐，实测（全新 cluster，两端真实 HTTP）：

| 支 | 场景 | Go | RAG 修复前 | RAG 修复后 |
|---|---|---|---|---|
| A | 有效邀请码 + 超长密码 + **新**用户名 | `5001` | `5001` | `5001`（本就一致） |
| **B** | 有效邀请码 + 超长密码 + **用户名已存在** | `1001`「用户名已存在」 | `5001` ★ | `1001` ✅ |
| **C** | 无效邀请码 + 超长密码 | `1016`「邀请码错误」 | `5001` ★ | `1016` ✅ |

**根因**：两端 bcrypt 实现**相同**（Go `bcrypt.DefaultCost`=10 / RAG `STRENGTH`=10，
都拒 >72 字节），分歧**纯粹来自步骤顺序**。
交集 = 「密码 >72 字节」且「本该先被前置检查拦下的输入」。

**修复（用户 2026-10-22 签字「完全对齐 Go」）**：bcrypt 从 `AdminDomainService` 的事务外
移进 `AdminCreateAtomicService`，排在 **W2 查 username 之后**。
Go 步骤序（`createadminlogic.go` 事务闭包）逐条镜像：
`FindAdminInviteByCodeForUpdate` → `FindOneAdminByUsernameTx` → `PasswordEncrypt` → `GetID` → insert。

> ⚠️ **本次撤销了一条「无出处 idiomatization」**：原注释写着
> 「bcrypt password（事务外，**避免延长事务持有时间**）」——这是一条**从未对照 Go 出处**
> 的优化（§3.2 的典型形态：注释写得很有道理，唯独没有出处）。
> 代价已知并接受：cost=10 约 60~100ms，会延长 invite 行 `SELECT ... FOR UPDATE`
> 的持锁时间；但该锁**只锁 invite 单行**。
> **`Snowflake.nextId()` 仍留在事务外**：它不触碰 invite 锁、不参与错误码归属，
> 与 Go 放在哈希之后相比无可观测差异（Go 侧 `GetID` 失败同样是 Internal）。

**回归网**：`AdminCreatePasswordOrderRegressionTest`（9 条，**真 PG**）。
变异测试已实证 **6 条能真红**——把哈希挪回 W1 之前，
B 支报 `expected ALREADY_EXISTS but was INTERNAL`、C 支报 `expected PERMISSION_DENIED but was INTERNAL`，
与端到端实测的漂移形态逐字一致。

**连带改动（不是回归，是结构变更的必然结果）**：
`AdminDomainService` 的 `catch(Exception)` 由 **29 → 28**（bcrypt 那处移进了
`AdminCreateAtomicService`）。`AdminDomainServiceCatchStructureTest` 的守恒期望值已同步，
并在注释里写明「**不要改回 29，那意味着有人又把 bcrypt 搬回了事务外**」。

> ⚠️ **`matrix.py` 的 `classify()` 那条 R3 宽规则仍有误分类风险**：
> `long5000` + 5001 的形态既可能是列宽，也可能是顺序。已由独立探针覆盖，
> 但**不要把「矩阵 R3 = 0」当作「列宽问题全部消失」的充分证据**。

> **R3 修复的副产品**：`check-ddl-static.py` 的列类型差异从 **22 处降到 12 处**
> （10 个 `varchar(255)` 全部变 `text`）。剩下 10 处 `timestamptz→timestamp` + 2 处 `text→jsonb`
> 属**范围外**（本轮只修列宽，不动时间精度与 jsonb 归属）。

> **E-3 同时暴露了一个「此前没人查」的层**：§3.1 的对照清单里写着
> 「数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT」，但**此前没有任何脚本在查这一层**
> （`check-proto-static.sh` 只比 proto 契约）。R3 就是从这个缺口里漏出来的 ——
> 运行时只看到「5001」，顺着查才落到 DDL。现补 `tools/difftest/check-ddl-static.py`，
> 但它**只查列类型与长度**；索引 / 唯一约束 / NOT NULL / DEFAULT 仍是 **NOT CHECKED**，
> 不得把该脚本的结论读成「DDL 已一致」。

<!-- @move AGENTS.md L2612-L2899 · §6.2.5 tools/difftest 产出（users 全域） -->
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
| **② PG 的 DDL 并发竞态** | **有，只是窗口窄得多** | GORM 的建表 SQL **没有** `IF NOT EXISTS`（`gorm.io/gorm@v1.31.1/migrator/migrator.go:309` 就是 `tx.Exec(createTableSQL, values...).Error`，而 `createTableSQL = "CREATE TABLE ? ("` 定义在同文件 `:225`），保护只来自 `HasTable` 先查（`gorm.io/gorm@v1.31.1/migrator/migrator.go:124` 的 `if !queryTx.Migrator().HasTable(value)` check-then-act，`HasTable` 本体在 `:333`）。并发失败形态会是 `relation already exists`，与 RAG 的 `pg_type_typname_nsp_index` 不同<br>⚠️ **2026-10-26 更正引用路径**：本行初稿把这两处写成「`gorm@v1.31.1` 下的**根** `migrator.go`」与「**裸** `migrator.go`」—— **行号全对**（已逐行核对：`:124` 就是那行 `HasTable` 判断、`:309` 就是那句 `tx.Exec`），**但路径少了 `migrator/` 子目录**：gorm 包里 `migrator.go`（根，111 行）与 `migrator/migrator.go`（1038 行）是**两个不同文件**，且 modcache 内同名文件不止一处（`v1.31.1` / `v1.31.2` 各一份）⇒ 短路径**有歧义** ⇒ 已按 `docs/DRIFT/users/user/rpc.md:1332` 的同款写法补全 |
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

<!-- @move AGENTS.md L2910-L3054 · §6.2.6 service/users 证据边界台账 E-1~E-6 -->
### 6.2.6 `service/users` 的**证据边界台账**（2026-10-21 新增）

§3.4.4 末尾那条新纪律管的是「留档声称 RAG 的行为是 X ⇒ 必须有断言 X 的运行测试」。
**本节管的是它的反面：证据缺口本身必须台账化。**
动机很具体 —— 一个 agent 跑完 `tools/difftest` 看到「39/39 全绿」，
若不知道下面第 1 条，就会以为 `service/users` 已经验证完了。

**不要把本节的条目当成「已知缺陷」读**：这里列的绝大多数**不是代码写错了**，
而是「目前还没有能证明它对（或不对）的运行证据」。

| # | 缺口 | 现状 | 要补什么 |
|---|---|---|---|
| **E-1** | **difftest 10/39 步只比状态码、不比 body** （历史轨迹：2026-10-21 由 13 → 12（b3066ab） → 10 → **0（2026-10-22 关闭）**） | ✅ **已关（2026-10-22）**。做法**不是**把两侧 body 改成逐字相同 —— 那 10 步**注定**不同：N-1 七条是 Go 自身两处 401 body 就互不一致（空 body vs `{code:401}` 无 data），DRIFT-006 三条的正文来自两个框架各自的 JSON 解析层。**真正缺的不是「比成一样」，而是「body 连看都没看」** —— 旧写法只断言 `gs == rs`，于是「RAG 把 401 改成 200 空 envelope」「RAG 的 400 变成 JSON」这类回归也能一路绿灯。现改为 `BODY_EXPECTATION` + `check_body_expectation()`：**分别断言两侧各自该是什么样**（N-1：两侧 status 均 401；Go body 必须命中它自己的两种形态之一；RAG 必须是 `{code,msg,data}` 三键齐全且业务码 1006）（DRIFT-006：两侧 status 均 400；两侧 Content-Type 均含 text/plain；两侧 body 均非空裸文本）。`http()` 相应改为返回 `(status, text, resp_headers)` —— 不取响应头就无法断言「形状」。统计口径同步改为 `asserted=10 / byte_equal=29`，**两个数分开报**，避免把「靠断言通过」误读成「两侧 body 逐字相同」。**变异测试**：把 RAG 的 N-1 envelope 拆掉 / 把 400 改回 JSON，10 条全部变红。 **⚠️ 关闭 E-1 当场抓出一条此前不可见的真实漂移**：详见 §3.3 新增的「代码里写了 ≠ 客户端收到」规则 —— RAG 所有 text/plain 400 都**丢 Content-Type 与 nosniff**（真实 Tomcat 证据），根因是 `HttpMiddleware$ObservabilityResponseWriter.setHeader` 只进 buffer、等 `flushTo` 才写回，而那时底层已 committed。**已修**（三个覆写点改实时 propagate）。守门脚本 `tools/difftest/check-plaintext400-headers.sh` —— **必须真实 Tomcat，MockMvc 测不到**（MockMvc 里 Content-Type 完好，会假绿）。**留档一个 SCENARIOS 排序的副作用**：admin.update 在第 28 步把 admin 密码改成 `newadmin123`，**同 cluster 第二次跑** admin_token-using 步骤会因密码不匹配而全 DIFF （本体已从 stale 状态隔离，**留作 SCENARIOS 排序整治一项**） | |
| **E-2** | **gRPC 层没有直接对拍** | **✅ 已关（2026-10-21）**。拆为两段同时收口：**静态半**（字段号 / message 名）—— Go 真源 `proto/{user,admin}.proto` 与 RAG `*-proto/.../{users,admin}.proto` 字节级对齐，`tools/difftest/check-proto-static.sh` 三秒跑完；**动态半**（错误码语义）—— 同 stub 双端口打 4 档 RPC（`OK` 注册新号 / `INVALID_ARGUMENT` logout 空 token / `NOT_FOUND` getUser 鬼 uid / `ALREADY_EXISTS` 注册重名），4/4 双端口 `Status.getCode()` 一致（首测结果见 `tools/difftest/grpc-results.txt`）。**留档注意点**：Login 三种业务失败（用户不存在 / 密码错 / 被禁）**不**抛 gRPC code、走 `LoginResp.Status = 1|2`，是 go-zero 业务级状态码设计；本实验因此特意不把 login 列进 4 档，避免误把业务码当 wire code 测 |
| **E-3** | **39 步不是状态空间穷举** | ✅ **已关（2026-10-21）**，**5 个根因全部已修（2026-10-22，用户逐条签字）**。`tools/difftest/matrix.py` 把 9 个 DTO 按**字段**展开边界组合，共 **273 条**（原 39 步只覆盖每端点 1~3 条典型路径）。首轮产出 **77 条 NEW_DRIFT**，UNCLASSIFIED = 0。**根因见 §3.4.5**：R1 Jackson 标量强转与截断 64 条、R2 显式 null ≠ 缺席 4 条、R3 DDL `text` vs `varchar(255)` 4 条、R4 GORM 静默抑制负 OFFSET/LIMIT 5 条，**R5 bcrypt 抢在 invite/username 检查之前 2 条（修复过程中新暴露，曾被误分类进 R3）**。**端到端复测：NEW_DRIFT 77 → 0**（R1/R2/R4/R5 均 0；R3 的 0 由 `r5-order-probe.py` 独立佐证，因为 `classify()` 那条宽规则仍可能误分类）。**已验证与既有 difftest 共存**：全新 cluster 上先跑矩阵再跑 `diff.py`，仍是 **39/39 unexpected=0**。配套静态证据 `tools/difftest/check-ddl-static.py`（列类型差异已从 22 处降到 12 处）。**⚠️ 工具本身的坑已记在脚本头**：① token 不可跨端复用（N-5）② method/参数通道必须按端声明（A-03 + `form:` tag）③ 字段来源必须是真源 `types.go` 而非手抄 ④ **`--no-build` 不拷新 jar，起的是旧副本** ⑤ **`admin.create` 恒用假邀请码 ⇒ 看不到「邀请码有效」分支**，R5 即由此漏掉，补齐靠 `tools/difftest/r5-order-probe.py`（用真邀请码，实测出比矩阵多一倍的 2 处分歧） |
| **E-4** | **超时路径 —— 已于 2026-10-21 实测坐实，见下方专节** | ~~零覆盖~~ → 已有数据。**这是 B-1 的运行证据**，此前只有静态分析 | ✅ 已补（`exp-b1-timeout-cancel.sh` 端到端 + `probe-jdbc-cancel.java` 纯 JDBC 机制层，三层拆解见 §3.4.1「B-1 补记」） |
| **E-5** | **并发路径零覆盖** | ✅ **已关（2026-10-21）**。admin-rpc 侧 `AdminRpcConcurrencyWiringTest`（真 PG + Testcontainers，2 条全过）针对 `AdminCreateAtomicService` 双场景：(a) 同一 invite code N=8 并发消费 ⇒ **恰好 1 OK + 7 InviteUnavailable**（W1 悲观锁 `SELECT ... FOR UPDATE` 实证有效），admin 表里 `username LIKE 'u-%'` 严格 1 行；(b) 同一 username N=8 并发注册（各用唯一 invite）⇒ **恰好 1 INSERT 成功 + 7 撞 unique**，最终严格 1 行。Go 一致性靠「同样策略（行锁/UNIQUE 兜底）+ 同样约束」按设计而非按端到端对拍；要做端到端对拍那是 E-2 的范围 |
| **E-6** | **事务异常回滚只测了 `user-rpc`** | ✅ **已关（2026-10-21）**。`AdminRpcTransactionWiringTest`（真 PG + Testcontainers，4 条全过）在 admin-rpc 侧同款补齐 `AdminCreateAtomicService.createAdminAtomic` 4 步事务的回滚行为：(1) W3 admin uid 唯一冲突 ⇒ invite `used_at`/`used_by_uid` 双 null（rollback 实证）；(2) W1 invite 不存在 ⇒ admin_admin 表行数无变化；(3) W2 username 已存在 ⇒ invite 不被标 used；(4) happy path ⇒ invite 被正确标 used，admin 行被插入。user 侧 `AvatarTransactionWiringTest` + admin 侧本类 ⇒ 两端写路径回滚都有真 PG 实证。**service/common 的 20 项偏差**这条不在 E-6 范围,留作另一笔 |

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

**admin 域同样跑了四轮（2026-10-21 扩展，user 域两轮 + admin 域两轮）**：

| | 客户端返回 | 响应体 | PG 查询又存活 |
|---|---|---|---|
| Go / user | 2236ms | `{"code":1015,"msg":"服务繁忙"}` | 161ms |
| **RAG / user** | 2294ms | 同上 | 191ms |
| Go / admin | 2181ms | 同上 | 176ms |
| **RAG / admin** | 2544ms | 同上 | **179ms** |

⇒ **admin-rpc 的 29 处放行分支 + 13 处 `checkDeadline()` 也拿到了端到端证据**，
不再只是「user 侧全绿所以推断 admin 侧也成立」。admin 域 RAG 与 Go 的查询滞留差 3ms。

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


---

<!-- @verify 2026-10-23 第三轮：DRIFT-USERRPC-V-004 时间列类型 —— 已修 users_user 半边；users_user_avatar_history 半边仍 OPEN -->

## `users_user` 时间列 `TIMESTAMP` ⇄ `timestamptz`：`users_user` 半边**已修**（2026-10-23）

> **用户已拍板**：照改（存量数据无所谓 —— 只有测试种子），且 DDL + 两个实体 + 格式化点**同一提交**做完。
> 下文「改动三部分」里**第 3 部分经复核是错的**，已更正；实际只落地了第 1、2 部分。

### 落地内容（已验证）

| # | 改了什么 | 文件 | 守门 |
|---|---|---|---|
| 1 | `CREATE TABLE` 里两列改 `TIMESTAMPTZ` + 幂等 `SET TIME ZONE 'UTC'` 包裹的 `ALTER … USING … AT TIME ZONE 'UTC'` + `RESET TIME ZONE` | `users-common/src/main/resources/db/01_create_user.sql:42-43` / `:101-106` | `UserRpcUsersUserTimestamptzParityTest` |
| 2 | 两个实体 4 个字段 `LocalDateTime` ⇒ `OffsetDateTime` | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java`、`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/UserEntity.java` | 同上（反射断言声明类型） |

**测试**：admin-rpc **84/0**、user-rpc **102/0**（新增 6 条真 PG 用例）。
**变异测试已做**：删掉 DDL 里的 `SET TIME ZONE 'UTC'` ⇒
`replayUnderNonUtcSessionTimeZone_doesNotShiftStoredInstants` 立刻红，
`2020-01-01T08:00:00Z` 实际变成 `2020-01-01T00:00:00Z`（差 8 小时 = Asia/Shanghai 偏移）⇒ **守门非假绿**。

### ⚠️ 两个「本以为显然、实际踩到」的坑（详见 [`docs/Pitfalls/_cross/fix-round-lessons.md`](../../Pitfalls/_cross/fix-round-lessons.md)）

1. **裸 `ALTER` 在无账本重放模型下会累积损坏数据。**
   `SchemaInitializer` 每次启动原样重放 DDL，而 `timestamptz AT TIME ZONE 'UTC'`
   对**已是 timestamptz** 的列**不恒等**（先压成 UTC 墙钟，再按会话 TZ 铸回）
   ⇒ 会话时区非 UTC 时每启动一次偏移一次，永不收敛。
   **「照抄同文件 R3 三条的写法」在此不成立** —— `varchar→text` 是超集、不重新解释取值，
   `timestamp→timestamptz` 会重新解释。**外形像 ≠ 语义像。**
   修法是把会话时区钉成 UTC 再转，转完 `RESET`，让首迁与重放两条路径都恒等。
2. **条件守卫不能用 `DO $$ … $$`。**
   `SchemaInitializer` 用 Spring `ScriptUtils` 切分语句（分隔符 `;`），
   而 spring-jdbc 7.0.9 的 `ScriptUtils` **没有 PostgreSQL 美元引号支持**
   （反编译 `ScriptUtils.class` 常量池中不存在 `$$`）⇒ 块内分号照样被切断，
   DDL 语法错、`applyAll` 抛异常 ⇒ **user-rpc 与 admin-rpc 双双起不来**（实测 34 个测试红在启动阶段）。

### 🔴 台账原文第 3 部分「格式化点」是错的，已更正

原记录（`_shared.md` 旧版第 3 行）写：

> 3 | 格式化点改发带偏移的 RFC3339 | 主代码实测只有 3 处：`AvatarDomainService`(2)、`user-api/UserApiSupport`(1)

**复核结论：两处都归错了。**

| 原记的「格式化点」 | 实际 | 证据 |
|---|---|---|
| `AvatarDomainService.fromModel:71-74` | 格式化的是 **`UserAvatarHistoryEntity.createTime`**，数据来自 **`users_user_avatar_history`**（`02_create_user_avatar_history.sql:30-31`，**仍是 `TIMESTAMP`**），**不是 `users_user`** | `AvatarDomainService.java:63` 形参类型即 `UserAvatarHistoryEntity` |
| `user-api/UserApiSupport:69` | **不是格式化点**，是 proto string → DTO string 的原样透传 | `dto.setCreateTime(proto.getCreateTime() == null ? "" : proto.getCreateTime())` |

⇒ **`users_user` 的时间值在 RAG 侧根本没有任何格式化点**，不进入任何 proto/DTO ⇒
本轮改动对 wire **无可观察差异**，是**纯 schema / 类型层对齐**。
⇒ 「3 处格式化」实际属于 **`users_user_avatar_history` 那条线**，属另一份 DDL 文件、另一个实体。

### 🔴 仍然 OPEN：V-004 的**可观察半边**在 `users_user_avatar_history`

`docs/DRIFT/users/user/rpc.md` 的 `CASE-27` / `OBSERVABLE DIFFERENCE`（客户端可见字段
`AvatarHistoryItem.create_time`）指向的是**头像历史表**，本次**未修**：

- `users-common/.../02_create_user_avatar_history.sql:30-31` 仍是 `TIMESTAMP`；
- `UserAvatarHistoryEntity.java:47,50` 仍是 `LocalDateTime`；
- `AvatarDomainService.java:74` 仍 `atOffset(ZoneOffset.UTC)` ⇒ wire 上恒 `Z`，Go 侧带偏移；
- 叠加 `user-rpc/application.yaml:13` 的 JDBC URL **未钉** `TimeZone`（Go 侧 `db.go:21` 钉 `Asia/Shanghai`）。

⇒ **同根因（Go `time.Time` 无 type tag ⇒ `timestamptz`）还剩 3 张表**：
`users_user_avatar_history`、`admin_admin`（`03_create_admin.sql:55-56`）、
`admin_invite`（`04_create_admin_invite.sql:37-41`，4 个时间列含 `expires_at`/`used_at`）。
⚠️ **未单独评估过**，不要照本轮的改法批量套用 —— 每张表的 wire 可见性与时区敏感点都不同。

### 跨组件影响面提醒

`users_user` 由 `user-rpc` 与 `admin-rpc` **共同建表**（AGENTS.md §6.3 问题 1），
且共用 `users-common` 的同一份 DDL ⇒ 改这份 DDL **同时影响两个组件的启动**。
两边的 `AdminSchemaBootstrap` / `UserSchemaBootstrap` 都会幂等重放它 ——
本轮实测两个组件的真 PG 用例均已跑通（含上述重放守门）。

---

## 2026-10-23 第三轮：`users/user` 半边双侧活体对拍收口（+ **admin 侧待裁决问题**）

> 本节只覆盖 `users/user` 半边（`user-rpc` / `user-api` / `user-proto` / `users-common`）。
> 逐组件明细 ⇒ [`user/rpc.md`](./user/rpc.md) 与 [`user/api.md`](./user/api.md) 的同名章节。
> **本节的所有权只有 user 半边**；`admin_admin` / `admin_invite` 只**报告**，不修改。

### 0. 元信息

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23**（21:00–21:35，全部实跑） |
| **证据强度** | ✅ **双侧活体对拍**（Go 进程 ↔ RAG 进程，各自真 PG + 各自真 Redis，逐字比对）+ ✅ **真 PG 守门测试**（Testcontainers） |
| 隔离 harness | 容器 `userprobe-postgres`(127.0.0.1:56432) / `userprobe-redis`(127.0.0.1:57379)；Go rpc **19104** / api **19188**，RAG rpc **61210** / api **60900** |
| ⚠️ 为什么另起 harness | 并行的 **users/admin 会话正在跑 `tools/difftest/run.sh`**，占共享端口与 `difftest-postgres:55432` ⇒ 本轮**不碰**共享资源，避免两边取证互相污染 |
| 冻结仓库 | Go 二进制从冻结仓 `go build`；配置用 `tools/difftest/conf/go/*.yaml` 的**副本** ⇒ 冻结仓一个字节未动 |
| 基线（修复前） | users-common **11**/0/0/0 · user-rpc **102**/0/0/0 · user-api **69**/0/0/0 |
| 终态（修复后，21:35 复跑） | users-common **11**/0/0/0 · user-rpc **112**/0/0/0 · user-api **76**/0/0/0（**skipped 全 0**） |
| user-proto | 本轮**未单独跑测试** —— §3.4 的「`.proto` 与 Go 逐字节一致（10/10）」是**上一轮的既有结论**，本轮既未推翻也未复核 |
| 全场景对拍 | `diff.py --scenario user`：**17 步 / 12 byte_equal / 5 asserted（已签字）/ 0 unexpected** |

> ⚠️ **skipped 纪律（本轮新立，已逐模块核对）**：真 PG 用例带 `@EnabledIfDockerAvailable`，
> **无 Docker 时整类被跳过而 reactor 仍报 `BUILD SUCCESS`**。
> ⇒ 本轮逐模块读了 surefire XML 的 `skipped` 属性，三者**全为 0**。
> 复现：`grep -ho 'skipped="[0-9]*"' service/users/user/*/target/surefire-reports/*.xml`

### 1. `users_user` 时间列：本节上半部分的结论**已被推翻一半**

上一节（"2026-10-23" `users_user` 时间列节）写：

> 「🔴 **仍然 OPEN**：V-004 的**可观察半边**在 `users_user_avatar_history`」
> 「`AvatarDomainService.java:74` 仍 `atOffset(ZoneOffset.UTC)` ⇒ wire 上恒 `Z`，Go 侧带偏移」

⇒ **这两句现在都不成立**：

| 上一节的断言 | 本轮实测（21:01 前后，双侧活体） | 现状 |
|---|---|---|
| `AvatarDomainService:74` 恒渲染成 `Z` | Go `2026-10-23T21:01:48+08:00` vs RAG `2026-10-23T21:01:48Z`（**时刻差 8h**） | ✅ 已修 → 两侧**逐字相同** |
| V-004 的可观察半边 OPEN | 同上 | ✅ 已修 → V-004 整条关闭 |

**🔴 但归因是错的，上一节必须连同归因一起更正**：上一节把差异归给
「JDBC URL 未钉 `TimeZone`（Go 侧 `db.go:21` 钉 `Asia/Shanghai`）」。
本轮实测**否掉了这个归因**：

- 把 RAG 的 JDBC URL 加上 `TimeZone=Asia/Shanghai`，Go 与 RAG 的渲染**都不变**；
- 把 **Go 进程的 `TZ` 设成 UTC**，Go 的渲染立刻变成 `…Z`（与 RAG 修复前同形）；
- pgjdbc `getObject(OffsetDateTime.class)` **恒返回 UTC**，与 session TZ / JVM TZ 都无关
  （实测 pgjdbc **42.7.13**）。

⇒ **偏移量由进程 `time.Local` 决定（Go 侧）/ 由渲染点显式换算决定（RAG 侧），与 PG 会话时区无关。**
⇒ RAG 的修法是在 `AvatarDomainService.fromModel` 用
`ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()` 渲染，
并把列类型改 `TIMESTAMPTZ` + 绑定应用侧时钟。
⚠️ **两侧同形的条件是「两侧进程时区相同」** —— 这与 Go 自身的前提完全一致，不是新引入的限制。

### 2. `users_user` 建表：真库对照的结论（`UNRESOLVED-USERRPC-006` 关闭）

对**两个真库**跑 `ddl_compare.py`（不是读脚本，是连上去查 `information_schema`）：

| 项 | Go | RAG | 判定 |
|---|---|---|---|
| `users.status` | `is_nullable = YES` | `NO` | 🟡 **V-002 仍在（真库确证）· 但 2026-10-27 订正为「DDL 层差异 · 行为层零差异」⇒ 建议保持 NOT NULL**（逐层证据见 [`user/rpc.md` 的 V-002 订正块](user/rpc.md)） |
| `users.extra_info` | `text` | ✅ `text` | ✅ **V-003 已修**（2026-10-23，`ec33159`）；`admin_admin.extra_info` 同轮前后一致 |
| `users.create_time` / `update_time` 的 **DEFAULT** | **无** | **有 `CURRENT_TIMESTAMP`** | 🔴 **新登记 V-011** |
| 索引 / 唯一约束 / NOT NULL | 见下 | 见下 | ✅ **功能等价** |

- 唯一约束：Go `uni_users_email` / `uni_users_username` ↔ RAG `users_user_email_key` / `users_user_username_key`
  ⇒ **名字不同、语义相同**（RAG 另有 `users_user_uid_key`，Go 侧靠 `tag:uniqueIndex` 生成，同语义）。
- RAG 多一个 `idx_users_user_avatar_history_uid_is_current (uid, is_current)` 复合索引
  ⇒ **RAG 略强**（Go 只有单列 `uid`），属表达层增强，**不算漂移**。
- `users_user_avatar_history`：Go `PRIMARY KEY (id)` ↔ RAG 同；RAG 另有 `history_id` 唯一索引，
  Go 侧同样是 tag 生成 ⇒ 等价。
- **V-011 的可观察性**：两侧写入路径都**显式写该列** ⇒ 当前**不可观察**，
  故本轮**不修**（改了也没有守门能证明，且可能干扰 admin 侧的写入路径）。

### 3. 🔴 关闭 `DRIFT-USERRPC-V-005`（上一轮结论**被证伪**）

上一轮记的是「`UpdateUser` 对**显式空 map** 的分支不同」。本轮双侧实测：

- Go 侧**根本不写** `extra_info`：wire 传来的空 map 经 go-zero `core/mapping` 解出**非 nil 的空 map**，
  GORM 的 `Updates(struct)` 对空字段**全部跳过** ⇒ SQL 里根本没有 `extra_info`；
- 实测两侧 DB 里 `extra_info` **都是 NULL** ⇒ **RAG 也没写**。

⇒ **原结论错误，予以关闭。**
⚠️ 这是本轮**第二次**推翻自己（第一次是 V-004 的归因）⇒ 与 `docs/Pitfalls/_cross/testing-pitfalls.md`
的「绿测试可能是错误答案的守卫」同源：**结论错，比没结论更贵。**
真正残留的差异是 **`V-010`（`update_time` 推进）**，已修：GORM 的
`gorm.io/gorm@v1.31.1/callbacks/update.go:235-252` 会**无条件**把 `UpdateTime`（autoUpdateTime）放进 SET，
RAG 曾用 `if (!any) return 0;` 把它挡掉了。修复后双侧实测：空 body / `extra_info={}` / 改 email
**三种情况两侧 `update_time` 都推进**。

### 4. `users/user` 半边本轮修掉的全部漂移

| 编号 | 一句话 | 归属 |
|---|---|---|
| `DRIFT-USERRPC-V-004`（可观察半边） | `users_user_avatar_history` 时间列 `TIMESTAMP⇒TIMESTAMPTZ` + 渲染换算到进程本地时区 | user 半边 ✅ |
| `DRIFT-USERRPC-V-009`（新） | `insertHistory` 由 DB 的 `CURRENT_TIMESTAMP` 改绑**应用侧时钟**（对齐 GORM autoCreateTime/autoUpdateTime 同源） | user 半边 ✅ |
| `DRIFT-USERRPC-V-010`（新） | `update_time` 无条件进 SET（Go 依据 `gorm.io/gorm@v1.31.1/callbacks/update.go:235-252`） | user 半边 ✅ |
| `DRIFT-USERAPI-001` | 畸形 claim 走业务层 200+1006，**不是**鉴权层 401 | user 半边 ✅ |
| `DRIFT-USERAPI-002` | delete / update 的 `uid<=0` 透传（Go 的两套 uid 语义不抹平） | user 半边 ✅ |
| `DRIFT-USERRPC-V-005` | **关闭**（结论被证伪，非修复） | user 半边 ✅ |
| `UNRESOLVED-USERRPC-006` | **关闭**（真库对照完成） | user 半边 ✅ |

**同时反转了 5 条错误断言**（AGENTS.md §3.3.3「先改/删错误断言，再改实现」）：
`UserDomainServiceTest` 3 条（改期望值 + `TimeZone.setDefault` 强制时区，机器无关）、
`UserRpcJsonTextAndAvatarProjectionTest` 1 条、`UserRpcContainerWiringTest` 1 条。

### 5. 🔴 admin 侧待裁决问题（本轮**只报告，不修改**）

> ⚠️ **所有权边界**：admin 域由**另一个并行会话**审查。本轮**一个字都没改** `service/users/admin/**`
> 与 `docs/DRIFT/users/admin/**`。以下 4 条需要**联合裁决**，单方面改会引入新漂移。

#### ADJ-01 🔴 `users_user.status` 的 NOT NULL 与 admin-rpc 的 `status = 0L` **必须同时改**

- 真库实证：Go `users.status` **`is_nullable = YES`**，RAG 是 `NO`（`DRIFT-USERRPC-V-002`）。
- 但**单独放开 NOT NULL 会立刻激活一个已存在的缺陷**：`admin-rpc` 的 `UserEntity.status = 0L`
  是与 `DRIFT-USERRPC-V-001`（`Login` fail-open）**同款**的缺陷 ——
  若 `status` 变可空，RAG 的 `WHERE status = 0L` 就会**匹配到 NULL 行**（SQL 三值逻辑下 `NULL = 0` 为 NULL，
  但 MyBatis 若生成 `status = 0` 且列可空，行为与 Go 的 `status <> 0` 判据不同），
  **暴露面是 `GetUser` 响应**（未注册用户被当成已封禁/已删除）。
- ⇒ **裁决点**：`users_user.status` 是否放开 NOT NULL；放开则必须同步修 admin-rpc 的判据。
  **本轮不单方面改。**

> 🟢🟡 **2026-10-27 订正（ADJ-01 的前提已被证伪 ⇒ 本条不再是「待裁决」）** —— G1 lane · **本轮独立复核**
>
> ⚠️ **上面那条「`= 0L` 是同款缺陷」是 2026-10-23 上午误修期间的判断，已被同日第三轮的双侧活体对拍撤销**
> （`admin-rpc` 自己的台账 `admin/rpc.md` 的 `UserEntity.status` javadoc 明写：「admin-rpc 的同名实体一直保留 `= 0L`（与本类同形）—— 是**当时正确**的一侧 …该指认**已撤销**」）。
>
> ⇒ **本轮独立复核的逐层证据链**（不是转述）：
> | 层 | 证据（本轮在 `GOMODCACHE` / `GOROOT` 里逐行读过） | 结论 |
> |---|---|---|
> | ① GORM 的 int64 setter | `gorm.io/gorm@v1.31.2/schema/field.go:616-622`：`case **int64: if data != nil && *data != nil { …SetInt(**data) }` | DB 值 NULL ⇒ `*data == nil` ⇒ **什么都不做** ⇒ 字段保持 **0**，**不报错** |
> | ② stdlib 转换层 | `database/sql/convert.go:431-435`：`case reflect.Pointer: if src == nil { dv.SetZero(); return nil }` | dest 的 kind 是 **Pointer** ⇒ 走这一支 ⇒ **不报错** |
> | ③ 双侧活体对拍 | `user/rpc.md`「2026-10-23 第三轮」：`status IS NULL` 打 Login ⇒ **Go 200 + token** | ⇒ **删掉 `= 0L` 才是不等价的那一侧** |
>
> ⇒ ⇒ **行为层零差异**：Go 的「可空 + 读成 0」与 RAG 的「不可空 ⇒ 读不到 NULL ⇒ 也是 0」⇒ **可观察结果相同**。
> ⇒ ⇒ **「必须与 admin 侧同改」是错的**：那会**制造**新漂移（RAG 读出 `null` ≠ Go 的 `0`；admin 侧从「NULL 当活跃(0)」变成「NULL 当未知(null)」⇒ **方向相反**）。
> ⇒ ⇒ **ADJ-01 的正确处置 = 保持 RAG 的 NOT NULL**（= RAG 更严的一侧，且与 Go 可观察行为同形）⇒ **不需要联合裁决**。
> ⇒ ⚠️ **本条尚未销号**（销号须与 G4 共同确认 —— G4 的派单 #6 用了相反的前提）⇒ 逐层证据见 [`user/rpc.md` 的 V-002 订正块](user/rpc.md)。

#### ADJ-02 ⚠️ `admin_admin` / `admin_invite` 时间列仍是 `TIMESTAMP`

- 同根因（Go `time.Time` 无 type tag ⇒ `timestamptz`）：`03_create_admin.sql:55-56`（`admin_admin`）
  与 `04_create_admin_invite.sql:37-41`（`admin_invite`，**4 个**时间列含 `expires_at` / `used_at`）。
- ⚠️ **不要照 user 半边的改法批量套用**：本轮实测证明
  「偏移量由**渲染点**决定，不是由列类型决定」⇒ **列类型改不改，取决于该表的字段是否进 wire**。
  `admin_invite.expires_at` / `used_at` 是**比较型**字段（`<` / `>`），时区错会直接改变过期判定；
  这与 `users_user_avatar_history.create_time`（纯展示型）**风险等级不同**。
- ⇒ **裁决点**：admin 侧需单独评估这两张表，本轮只登记。

#### ~~ADJ-03~~ ✅ **已闭环（2026-10-26 订正）** —— `users_user` 建表责任交叉（`user-rpc` ↔ `admin-rpc`）**已在当前代码上复跑过**

> ⚠️ **本节原文（2026-10-24）写的是「仍未在当前代码上复跑 / 仍 unresolved」—— 那是过期状态。**
> ⇒ 原文保留供追溯，订正见下。

- `users_user` 由**两个组件共同建表**，共用 `users-common` 的同一份 DDL
  （两边各有 `UserSchemaBootstrap` / `AdminSchemaBootstrap` 幂等重放）。
- **✅ 已复跑**：**2026-10-23 第五轮 §4** 做了**双侧对称并发启动实测**（两个 rpc 同时对同一个**空库**启动，共 **9 轮**），
  **2026-10-24 独立复现**，结论逐条一致。
  - 两侧**都能起来**、表**都建齐**、**0 致命错误** ⇒ **OBSERVABLE EQUIVALENT**（可观察结果一致）。
  - ⚠️ **但机制不同，务必别读成「机制也等价」**：
    **RAG 侧**靠 advisory lock **机制性**串行（同一把锁，两个进程严格错开）；
    **Go 侧** GORM `AutoMigrate` 是 check-then-act ⇒ **机制上仍有竞态窗口** ⇒ **实测未撞 ≠ 机制保证**。
  - ⇒ 证据：`docs/DRIFT/users/user/rpc.md` 的 **§9.2** 与 **§4「建表责任交叉」行**。
- ⇒ **admin 域**（2026-10-26）已在 `docs/DRIFT/users/admin/_shared.md` §5 加 **U-8 只读指针**指向该结论
  （admin 不写域级共享文件 ⇒ 由 users 域级 owner 落原文订正 ⇒ 即本节）。
- ⚠️ **残留**：Go 侧那个**机制性**竞态窗口本身**未被消除**（只是当前不可观察）⇒ 若将来要消除，那是**另一个议题**（改 Go 侧建表机制 ⇒ 真源冻结 ⇒ 不可改 ⇒ 只能靠 RAG 侧锁 + 部署纪律）。

#### ~~ADJ-04~~ ✅ **已答（2026-10-26，admin 域回信）** —— admin 侧**不存在**依赖 DEFAULT 的 INSERT

- RAG 的 `users_user` 时间列有 `DEFAULT CURRENT_TIMESTAMP`，Go 无。
- user 半边两侧都显式写该列 ⇒ **不可观察**。
- **✅ admin 域已答**（证据在其 `admin/_shared.md` §5 的 **U-7**）：
  **admin-rpc 对 `users_user` 零 INSERT** —— 只有 UPDATE / DELETE
  （`userMapper.update(null, wrapper)` ×4 + `userMapper.delete(...)` ×1；全模块 `userMapper.insert` **零命中**）
  ⇒ **不依赖 `users_user` 的 `DEFAULT CURRENT_TIMESTAMP`** ⇒ **该差异对 admin 侧不显现** ✅ ⇒ **本条关闭**。
- ⚠️ **但同一机制在 admin 自己的两张表上成立** —— admin 域已顺带查出 **`DRIFT-ARPC-001` 有一个范围缺口**并自行登记：
  该条标题写「`update_time` 永不推进（**所有写路径**）」，但那 6 条**全是 UPDATE**；
  **INSERT 路径的 `create_time` 不在范围内**（Go 靠 GORM `autoCreateTime` 应用层填 / RAG 靠 DB 层 DEFAULT ⇒ 两侧都有值 ⇒ 实际不可观察）
  ⇒ ⇒ **修 `DRIFT-ARPC-001` 时不要顺手改 INSERT 路径，也别以为该条被修就等于 `create_time` 也被证明了**。

### 6. 本轮**没有**做的事（诚实边界）

1. **`user-proto` 未单独跑测试** —— 「`.proto` 与 Go 逐字节一致」是上一轮既有结论，本轮未复核。
2. **`user-api` 无真 PG 守门** —— 该组件**零 DB 访问**，「真 PG」对它**不适用**（不是漏做）。
   替代方案：真 MVC 管线 + 真 Redis + mock gRPC 边界（见 [`user/api.md`](./user/api.md) §5）。
3. **`UNRESOLVED-USERRPC-004②`（Redis 启动 fail-open/fail-fast）未验** —— 时间预算内没做。
   ⇒ ⚠️ **更正指针（2026-10-24 第十轮复核）**：本条**已于 2026-10-24 闭环**
   （[`user/rpc.md`](./user/rpc.md) §9.3 · 活体实验**证伪**了原推断：go-zero `RedisConf.NonBlock`
   默认 `true` ⇒ **启动不 ping** ⇒ 两侧都是「懒连接」）。
   ⇒ 上面的「未验」是 **2026-10-23 第三轮**当时的诚实边界，**已过期**，**不要再据此认为它还开着**。
4. **`DRIFT-USERRPC-V-006`（`users_user.id` / `avatar_history.id` 谁胜出）未验** ——
   本轮只验了「两侧 id 不同但同形」，没验「同一 id 是否会撞」。
5. **`api.md` 的 DRIFT-USERAPI-003 ~ 010 一条没动** —— 本轮只锁 claim 语义两条。
6. **未复跑 `users/admin` 的任何测试** —— 那是并行会话的范围。

---

## 2026-10-24：「台账 vs 实际」机器复核（users/user 域 · 首次）

### 0. 为什么要做

AGENTS.md §9 / §8 / §0 三处都写着「**过期状态必须随实测同步更正**」，
而本域台账历史上反复出现三类**台账脱节**：
① 台账指向的测试类 / 文件**根本不存在**；
② 台账引用的 Go `文件:行号` 与真源**对不上**；
③ 台账声明的**计数**与仓库实际对不上。

⇒ 这三类**全部是机器可判的** ⇒ 本轮把复核写成脚本（`tools/check-ledger-selfcheck.py`），
让「复核」从「靠人读」变成「一条命令」。

### 1. 复核工具

`tools/check-ledger-selfcheck.py`（本轮新建）：
- **A 引用存在性**：台账提到的每个测试类 / 文件路径，在 **RAG 仓 + Go 真源仓 + Go 模块缓存**里是否真的存在；
- **B 行号引用**：每个 `file.go:NN` 能否解析到真实文件、行号是否在范围内，并**打印该行附近的实际文本**供人工判读
  （⚠️ **只验「那行存在且长这样」，不自动判语义** —— 语义判断必须由人/agent 读原文，见 §3.3.1）；
- **C 计数一致性**：台账声明的 `Tests run: N` vs surefire 报告；
- **D 已修事实**：台账写「✅ 已修」的条目，逐条挂一个**可机器验证的代码事实**
  （文件 + 必须出现的字符串），当前 5 条（API-011 / API-012 / API-014 / V-003 / V-004）**全部命中**。

⚠️ **去噪规则也是实测踩出来的**（写进脚本注释，别删）：
框架注解名不是测试类 · Go 侧路径在真源仓不在 RAG 仓 · go-zero / pgx / gorm 的文件在**模块缓存**里 ·
同名文件多份时**按行数消歧** · 「删除 / 已改名 / 不存在」是**历史记述**不是引用 ·
`tmp/…` 临时探针本就不在仓内。

### 2. 复核结果（6 份台账 · 467 条 OK / 87 条 WARN / 3 条 FAIL）

| # | 发现 | 类型 | 处置 |
|---|---|---|---|
| **1** | `docs/DRIFT/users/user/api.md` 第八轮 MaxBytes 表写「真容器由 `check-protection-layer.sh` 守」—— **该脚本在仓内不存在** | 🔴 **台账事实错误** | ✅ 已更正（见下） |
| **2** | `rpc.md:1282` / `rpc.md:1477` / `_shared.md:889` 引用的守门类 **`UserRpcJsonbAndAvatarProjectionTest`** —— 该类**已改名为 `UserRpcJsonTextAndAvatarProjectionTest`**（真源：`service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/service/UserRpcJsonTextAndAvatarProjectionTest.java`） | 🔴 **守门清单指向不存在的类** | ✅ 三处已改新名 |
| **3** | 同上改名**未同步到 admin 域**：`docs/DRIFT/users/admin/rpc.md:372,382` 仍用旧名 | ⚠️ **跨域** | ✅ **admin 域已于 2026-10-26 改完**（提交 `c09fa45`，分支 `align/users-admin`：`admin/rpc.md:372,382` + `admin/rpc.md` 的 Pitfalls `:78,:92` 共 4 处）⇒ 本条关闭 |
| **4** | ⚠️ **`.xml` 报告名**（`UserRpcJsonbAndAvatarProjectionTest.xml`）**不是类名引用**，是**历史 surefire 报告文件名**（Oct 23 的真实产物、已成孤儿） | ✅ **不是缺陷** | ✅ **保持原样，勿「顺手统一」** —— 改了反而失真（报告文件名就是当时那次的真实名字）。<br>本域的 2 处（`users/user/api.md` 的「Oct 23 的旧报告」+ `Pitfalls/users/_shared.md` 的「上一轮遗留旧报告」）**已确认为报告名、保留**；admin 域同样保留。<br>⇒ **约定**：**类名引用**改新名，**`.xml` 报告名**保留原样。 |

**另新查出 1 个覆盖缺口**（复核的副产品）：

> **user-api 侧的四个过载保护里，`MaxBytes` / `MaxConns` / `CpuShedding` 三层在 user-api 自身**零守网**
> （`service/users/user/user-api/src/test/` 下只有熔断的两个测试）；
> `service/common` 侧也只有 `GoStyleMaxConnsFilterTest` / `GoStyleCpuSheddingFilterTest` /
> `GoStyleAdaptiveShedderTest` / `GoStyleBreakerTest` / `GoStyleGrpcClientBreakerInterceptorTest`
> —— **不存在** `GoStyleMaxBytesFilterTest`（专用测试类）。
> ⇒ MaxBytes（413 空 body + chunked 放行）目前只被**别的域**的 5 个 MVC 测试间接覆盖
> ⇒ **users/user 域这一层是裸的**。

**发现 1 的更正后表述**（`api.md` 第八轮 MaxBytes 表）：

> | 运行时证据 | ⚠️ 静态 | ⚠️ 静态 —— ⚠️ **2026-10-24 复核更正**：本行原写「真容器由
> `check-protection-layer.sh` 守」，**该脚本在仓内不存在** ⇒ **MaxBytes 在
> `service/common` 没有专用测试类**；仅被 5 个**别的域**的 MVC 测试间接断言 413
> ⇒ **user-api 侧自身零守网** | ⚠️ |

### 3. WARN 的 87 条：不处理，但**记下性质**

| 类别 | 条数 | 性质 | 处置 |
|---|---|---|---|
| 路径写法与实际不符（`user-api/advice/GlobalExceptionHandler.java` 之类） | ~20 | **表述差异**，文件都在 | 不改（本仓允许省略路径前缀） |
| 跨仓同名（`config.go:55` 在真源与模块缓存都有） | ~60 | **行号可能指错文件**的风险提示 | 不改，但**人读时必须确认是哪一份** |
| 临时探针 `tmp/userprobe/*` | 2 | **不可复现** | 不改（本就不在仓内） |

⚠️ 跨仓同名这条**值得单列**：本仓的 Go 引用大量形如 `rest/config.go:55`（go-zero 模块内）
与 `db.go:21`（真源仓内）—— **同名不同仓** ⇒ 脚本能验「行号在范围内」，
但**验不出「引的是哪一份」** ⇒ 这一类**仍需人工判读**，不能拿脚本的 OK 当结论。

### 4. 复核脚本本身的边界（诚实声明）

- ❌ **不判语义**：只保证「你引用的那行存在且长这样」；
- ❌ **不覆盖非 users/user 域**（其它域台账要各会话自己跑）；
- ❌ **不检查 Go 侧行为**（那是活体对拍的事，不是台账复核的事）；
- ⚠️ **C 类（计数）只能用 surefire 报告目录** ⇒ 该目录**可能含陈旧 XML** ⇒
  以 **mvn 输出的 `Tests run:`** 为准（见 `PIT-USERS-SHARED-016`）。

### 5. 复跑方式

```bash
python3 tools/check-ledger-selfcheck.py                       # 复核 task 域（工具的默认域）
python3 tools/check-ledger-selfcheck.py --domain users        # 复核 users 域（本域）
python3 tools/check-ledger-selfcheck.py --mutation-test       # 跑变异自检
```

⚠️ **2026-10-26 更正（下方原句是 2026-10-24 时的状态，已过期）**：
原写「`--strict`（有 FAIL 时退出码非 0）/ `--ledger <file>`（指定台账）」——
**这两个 flag 工具实际不支持**（现有 flag 只有 `--domain` / `--go-root` / `--repo-root` / `--mutation-test`）。
且 **`--strict` 本来就是多余的**：有 FAIL 时工具**本就退出 1** ⇒ 直接看退出码即可。

⚠️ 本轮复核后： **FAIL 3 → 0**（发现 1、2 已修；发现 3 属 admin 域，本轮不动 ⇒ 仍为 WARN 级）。

### ⚠️ 2026-10-26 复测（**已随 global 的工具修复而更新**）

> 用 `global` 域 **2026-10-26 最新**的 `tools/check-ledger-selfcheck.py`（`bb680a3` → `a4f3029` → `d52031e`）
> 对**本域台账**复跑（`--domain users --repo-root <主仓>`）：

| 层 | 结果 |
|---|---|
| 第 1 层 | ✅ **54 引用 ⇒ 缺失 0** —— ✅ **2026-10-26 已归零**：原先那 1 条 `RpcInterceptorTimeoutDescriptionGateTest.java`（`global` 域的守门类，**当年主干未合回**时路径悬空 —— ✅ **该守门类已随 `4f839f9` 合入主干（2026-10-26 23:1x）⇒ 现在主干上直接可查** ⇒ 这条去噪记录已是**历史状态**）**已被工具的 N13「状态叙述」正确去噪**。<br>⚠️ **为去噪能做而做的改写**：本条原先写作「主干上**查不到** ⇒ 路径**悬空**是**预期状态**」⇒ 这三个词**都不在** N13 的叙述词表里 ⇒ 工具认不出 ⇒ 仍报缺失。<br>⇒ 改成「该文件在 `global` 提交并合入主干前，主干上**不存在**（路径悬空是预期状态…）」⇒ 命中「不存在」⇒ **缺失归零**。<br>⇒ ⚠️ **工具侧的根因已报 global**：N13 词表（`删除\|已删除\|不存在\|已移除\|被删\|不再存在\|不建\|未建\|不创建\|0 字节\|错放\|空文件\|占位`）**缺「悬空 / 查不到 / 预期状态 / 尚未存在」等常见措辞** ⇒ 其他域会踩同款<br>🔴 **2026-10-26 晚：工具侧已修（global `45067ce`，E61）** —— N13 **拆三层**（A 存在性 / B 落地态 `待合入`·`尚未存在`… / C 免责 `是预期状态`·`不是台账事实错误`…）+ **一道护栏**（行里出现「路径写错 / 笔误 / 应为」⇒ B/C 层一律不生效）⇒ **「查不到 / 悬空」只靠 C 层免责半句生效**（裸词进表 = 假阴性，真缺陷恰恰这么写）。<br>⇒ ✅ **本域已把权宜措辞改回自然措辞**（「主干上查不到」+「是预期状态」）—— **语义更准**（文件在 global 的提交里**存在**，只是在主干上查不到；「不存在」反而失真）⇒ **两种写法现在都认**，改与不改都不影响机器判定<br>🔴 **2026-10-26 第三十九轮补记（跨域陷阱，global 转达）**：**合回之前，主仓代码仍是旧串**（`RpcInterceptor` 的超时 description 未生效）⇒ ⇒ **任何基于主仓的验证都看不到 E30 的修复** ⇒ ⇒ **别把「主仓还是旧串」误读成「修复没做对」**；同理 ⇒ 那条「守门类缺失」报告是**分支差异**（守门类在 `align/global` 上、主干上没有），**不是**「守门没写」的缺陷 ⇒ ⇒ **本域台账零改动即正确** |
| 第 2 层 | ⚠️ **10/10 OK，但断言全是 task 域的** ⇒ 该层**没按域筛选**<br>🔴 **2026-10-26 已被 `global` 的 `35a7b8e` 修掉**（见下方「工具已修」一节）⇒ 现在对本域输出 **「本层对 users 域不适用（⚠️ 不是『通过』，是『没查』）」** |
| 第 3 层 | ❌ **45 处「commit 清单缺陷」** ⇒ **全是工具假阳性**：① 台账路径**写死** `docs/DRIFT/task/_README.md`（那 45 条登记**属 task 域**）；② 窗口算法 `merge-base(HEAD, do-my-own-gh)..HEAD` **在主分支上恒空** ⇒ 44 条哈希被误判「不在本分支上」（实测它们**都在**本地 `do-my-own-gh` 上）+ 1 条行数不守恒<br>⇒ ~~**仍未修**（属工具 owner 域 ⇒ 已回信 global）~~ ⇒ 🔴 **2026-10-26 已被 `global` 的 `35a7b8e`「域适配 + commit 清单口径」修掉** ⇒ 现在对本域输出 **「本层对 users 域不适用（⚠️ 不是『通过』，是『没查』）」** ⇒ **45 条假阳性消失**（⚠️ **不是「本域台账变干净了」，是「工具不再拿 task 域的判据来审 users 域」）|
| 第 4 层 | ✅ **0 条** —— ✅ **2026-10-26 已归零**（`global` 的 **`d52031e`**「go.mod 权威化」）：第 4 层的第三方库清单改为**从真源 `go.mod` 动态解析**（含 `// indirect`），手写表只作兜底 ⇒ `gorm.io/gorm` 等**不再被误判**。<br>⚠️ 本域侧此前也已把 3 处引用补全为全路径（`gorm.io/gorm@v1.31.1/...`）—— 那是**消歧**（modcache 里 `migrator.go` ≥4 个同名），**行号全部核对无误**（`:124` = `HasTable` 判断、`:309` = `tx.Exec`、`:235-252` = AutoUpdateTime 那 18 行）⇒ 工具修好后两条都成立 |

⇒ **「FAIL 3 → 0」那句是 2026-10-24 的旧结论**；当前实况（**2026-10-26 复测**）：
**第 1 层 0 · 第 2 层「本域不适用」· 第 3 层「本域不适用」（45 条假阳性已由工具侧消除）· 第 4 层 0**
⇒ 工具侧输出 **「✅ 层 0~4 全部通过」**。
⚠️ **但「第 2/3 层不适用」不等于「本域这两层已核过」** —— 工具明写「**不是『通过』，是『没查』**」
⇒ 本域第 2/3 层的判据**至今仍缺**（第 3 层的「commit 清单」在本域台账里根本不是那个格式），
⇒ **本域不因此宣称「已复核」**；真要覆盖，得由工具 owner 给 users 域补判据。

### 🔴 工具已修（`global` 域 `35a7b8e`，2026-10-26）—— 本域报的第 3 层缺陷**已闭环**

本域于 2026-10-26 向 `global` 报的三处工具缺陷，第 3 层那两处（域筛选只到第 1 层 · 台账路径写死 task 域）已由
`global` 的 **`35a7b8e`「共享复核工具两缺陷修复：域适配 + commit 清单口径」**修掉：
第 2/3 层现在**先看该域的台账格式**再决定判据 ⇒ 对 `users` 域输出「**不适用**」而不是套用 task 域的判据。

⇒ **本域先前记的「第 3 层 45 条未修」已过期**（**核对日期 2026-10-26**），保留原文仅为留痕。
⚠️ **复跑方式**（用 `global` 工作树里的工具 + `--repo-root` 指回主仓，**不要**用本仓那份旧版）：
```bash
python3 "<global worktree>/tools/check-ledger-selfcheck.py" \
    --domain users --repo-root "/Users/pr/Documents/java&golang/RAG"
```
⇒ 实测：**层 0~4 全部通过**；第 1 层 54⇒0；第 4 层 288 条去重 ⇒ **235 条不悬空** + 53 条去噪（N5/N7 台账已自陈）。
⚠️ **层 5/6 仍报「17 个测试类 + 35 个生产类台账未点名」** ⇒ 那是**覆盖缺口提示**、**不算失败**，
⇒ 但它确实指出本域台账**没有逐个点名**本域的生产类 ⇒ 后续补台账时按它给清单来。
⚠️ **另注（历史状态 · 已过期）**：本仓 `tools/check-ledger-selfcheck.py` **曾是**旧版（`7a01b51`）⇒ 那时直接跑它会得到 60 条「不一致」
（45 第 3 层 + 12 第 1 层 + 3 第 4 层，**全是旧版误判**）⇒ ⇒ **别照着旧版输出改台账**。
🔴 **2026-10-26 23:1x 已过期**：`align/global` 已合回（`4f839f9`）⇒ **本仓那份工具已随合回更新**（另含 comment 域**刻意不登记**的理由说明）⇒
**现在直接跑 `python3 tools/check-ledger-selfcheck.py --domain users --repo-root .` ⇒ 层 0~4 全部通过** ⇒ **不必再绕道 worktree 版**（2026-10-26 23:30 实测）。
⇒ 逐层根因与复现命令 ⇒ [`docs/Pitfalls/users/_shared.md` 的 PIT-USERS-SHARED-019](../../Pitfalls/users/_shared.md)。

### 📐 可机检不变量（给 `global` 域的 `DOMAIN_CFG['users']`）—— 2026-10-26

> `global` 第三十五轮问：「**要不要给 users 域定义它自己的配置，取决于你们想查哪几条不变量**
> —— 你们给不变量清单，global 可以把 `DOMAIN_CFG['users']` 补上。」
> ⇒ 本节就是那份清单。**前三条不需要任何新数据**（台账现有结构即可支撑）⇒ 可以直接实现。
> ⚠️ **「不适用」≠「通过」**：下面这些是 users 域**真正想被机器盯住**的不变量，
> 若工具对本域仍输出「不适用」，本域就**没有在受监督**（不是「已通过」）。

| # | 不变量 | 判据（可机器实现） | 需要新数据吗 | 本域现状（2026-10-26） |
|---|---|---|---|---|
| **I1** | **分类计数守恒** | `rpc.md §4.4` 的每类计数 ↔ `§7` 里 `### DRIFT-USERRPC-V-NNN` 标题的**状态标记**（✅已修 / ✅已销账 / ✅判为不成立 / 🔴未修）**一一对应**：每条目**恰好**被计入一类，且被计入的类计数与表里写的数字相等。`api.md` 同理（`§4` 矩阵 ↔ `§7` 的 `DRIFT-USERAPI-NNN`） | ❌ **不需要** | 手工核对过：rpc 侧 §4.4 的「OBSERVABLE DIFFERENCE = 2」= V-002 + V-012；其余 7 条分属「已修/已销账/判为不成立/不可观察」四类 |
| **I2** | **销账提交存在性 + 祖先链** | 每个「已修 / 已关闭 / 已签字」条目**必须登记至少一个 SHA**；该 SHA 必须存在于仓库，且在 `do-my-own-gh` 的祖先链上 | ⚠️ **需要** —— 但**本域已补好**：[`users/user/rpc.md §8.1`](user/rpc.md) 与 [`users/user/api.md §8.2`](user/api.md) 两张**结构化销账提交清单**（含「销账方式」列：代码修复 / 证伪关闭 / 销账回写 / 签字保留 / 未修） | ✅ 已补（2026-10-26）⇒ **原先 12 个条目里只有 D-004 带 SHA**，现已逐条补全并逐个 `git show --stat` 核过 |
| **I3** | **台账写死的测试条数 ↔ 实测条数** | 台账里「`XxxTest` **N** 条」「user-rpc 全量 **N** 条」这类**写死的数字** ↔ surefire 实测条数 | ❌ **不需要** | ⚠️ **这类数字会漂** —— 2026-10-26 本域就因新增 3 条守网而必须把 `12 条 → 16 条`、`125 → 128` 全部同步（`global` 在 task 域抓到过同类：台账 106 vs 实测 108）⇒ **最该交给机器的一条** |
| **I4** | **CASE 编号守恒** | `rpc.md §8` 的 `CASE-NN` 与 `api.md §8` 的 `CASE-NN`（含 `5b/5c/5e/5f` 子编号）：**不得重号、不得跳号**；且 `§4.2 逐 OP` 的行数 ↔ `§8` 的 CASE 覆盖不得有缺口 | ❌ **不需要** | 手工核对过：rpc 侧 CASE-01..39 + 子编号，api 侧 CASE-01..60 + 子编号，无重号 |
| **I5** | **「已修」断言 ↔ 代码里真有对应物** | 第 2 层的 users 版：每条「已修」要指向一个**可机检的锚点**（文件 + 符号/正则），例如 V-003 ⇒ `01_create_user.sql` 里 `extra_info` 必须是 `text`；V-008 ⇒ 那段死代码必须**不存在** | 🔴 **需要** —— 本域要补一张「条目 → 锚点」表 | ❌ **尚未做**（本域不编造判据 ⇒ 本条待办） |

⚠️ **一条必须提醒 `global` 的口径差异**：
`global` 第三十五轮新加的「**登记的哈希必须构成连续祖先链**」这条判据，
**对 users 域结构性不适用** ——
users 域与其它 9 个域**共用 `do-my-own-gh` 这条共享主干** ⇒ 各域的提交**必然交错**
⇒ 本域登记的 SHA 在链上**不连续** ⇒ 建议对 `users` 域**降级**为
「**存在 + 在 `do-my-own-gh` 祖先链上（不要求连续）**」。
（task 域能成立是因为它有**专属分支** `align/task` + 专属清单表。）

⚠️ **另两条本域的机器可检项（global 若有余力可一并做）**：
① **N8「`...` 省略中间目录」的写法** —— 🔴 **2026-10-26 晚订正（`users/admin` 域回信转达 global 工具的新行为）**：
本域此前写的是「会让路径**永久不可解析** ⇒ 路径写错也**没有机制能发现**」
⇒ **这句作废**：global 的新版工具**把简写正确识别为「简写」并放过**
⇒ 那批 MISSING 是**旧版工具的口径问题**、**不是本域路径写错**（admin 域逐条解析过 33 处，**全部唯一命中真实文件、零「真缺失」**）
⇒ ⇒ **展开 N8 的真正理由**是：「**展开 = 从「不被检查」变成「被检查」**」
（简写一旦被放过，路径写错就永远没人发现；展开后工具能解析它，路径写错才报得出来）
（`admin` 域已把 33 处展开为完整路径；本域新增引用也一律写全路径）；
② **Go 侧 `file:line` 的行号正确性**（不只是「悬空」）—— 第 4 层只查「该行是否存在」，
**不查「该行的内容是不是台账说的那件事」** ⇒ 这是 §3.3.1 的残余缺口，本域目前靠人工核。
