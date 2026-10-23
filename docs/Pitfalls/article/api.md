# Pitfalls · `service/article/article-api`

> 本文件只写**教训**：现象 → 根因（框架机制）→ 为什么测试抓不到 → 下次怎么防。
> 差异本体在 [`docs/DRIFT/article/api.md`](../../DRIFT/article/api.md)。
>
> **证据强度**：本轮全部为「静态源码（Go `v1.10.2` + RAG 侧）」+「纯 MockMvc/Mockito 测试实跑 50 条全绿」。
> **无双侧活体对拍、无真实 Tomcat、无装配层测试** —— 下面每条教训的最后一节都据此写「怎么防」。

---

## P-1 「没有 error handler ⇒ 一律 400」是一条**只走了一半**的推理

**现象**
`POST /v1/upload` 在下游 article-rpc 失败时：Go 返回 **HTTP 503/500/504**（按 gRPC 码映射），
RAG 返回 **HTTP 400**。RAG 侧的实现与注释都写着「Go 无 error handler ⇒ 400 text/plain」，
逻辑自洽、测试全绿、注释详尽 —— 但**前提是错的**。

**根因（框架机制）**
`httpx.ErrorCtx` 的落点是 `doHandleError`（`rest/httpx/responses.go:142-158`），它有**三条**分支：

```go
if handler == nil {
    if len(fns) > 0      { ... }                        // ① 自定义 fns（本模块没用）
    else if errcode.IsGrpcError(err) {                  // ② 🔴 读的人普遍漏掉这一条
        http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))
    } else {
        http.Error(w, err.Error(), http.StatusBadRequest) // ③ 「大家记住的那条」
    }
    return
}
```

② 的判据是「错误是否实现 `GRPCStatus()`」（`rest/internal/errcode/grpc.go:45-55`）。
gRPC-Go 的 `status.Error` **恰好实现**它，且 `articleservice.UploadFile`
（`rpc/articleservice/article_service.go:76-79`）**原样返回**该错误、不包装 ⇒ ② 命中。

**为什么测试抓不到**
`ArticleApiMvcPipelineTest.java:353-372` 注入 `Status.INTERNAL` 并断言 400 —— 这条测试
**测的是 RAG，不是 Go**。它把一个未经真源验证的推断写成了带 `Go：` 前缀的事实，
于是成为「错误答案的守卫」：修 DRIFT 时它会先变红，且测试名会让人误判「修复 = 漂移」。

**元教训**
> **框架的 fallback 分支不是一条线，是一个 `if/else if/else` 链。**
> 读「错误处理」时必须把**所有**分支读完，尤其要问：
> 「这个错误对象的**运行时类型**会不会改变我落进哪一条？」

**下次怎么防**
1. 断言「Go 的行为」时，证据必须指向**框架的消费点**（这里是 `responses.go:150-153`），
   而不是「文档里说 fallback 是 400」。
2. 凡写 `@DisplayName("Go: …")`，先按 AGENTS.md §3.3.2 自查：这条断言有 `文件:行号` 的消费点支撑吗？
3. **改实现前先改/删错误断言**（AGENTS.md §3.3 硬要求）—— 否则会先撞上红测试而误判方向。
4. 涉及「错误对象类型决定分支」的地方（gRPC error / `context.DeadlineExceeded` / 自定义 error），
   在台账里单列一行，不要塞进「错误码映射」那一节。

---

## P-2 「配置里没写」≠「没设」：Go 的默认值藏在 struct tag 里，且**同一个 yaml 内可以不一致**

**现象**
`GET /v1/article/:id` 与 `GET /v1/articles` 里的「作者昵称补全」调用 `user-rpc`。
Go 侧单次调用预算是 **2 秒**；RAG 侧是 **最多 70 秒**（整个 HTTP 预算）。
降级行为完全相同（都回落成 `用户 <id>`），但延迟差约 35 倍；
若 user-rpc 挂到 70s，RAG 会返回 **503**，Go 早已返回 **200**。

**根因（Go 的 tag 默认值）**
`etc/article-api.yaml` 的三个 RpcClientConf 待遇**完全不同**：

```yaml
ArticleRpcConf:
  Timeout: 70000     # ← 显式写了
SecurityRpcConf:      # ← 没写 Timeout
UserRpcConf:          # ← 没写 Timeout
```

没写的走 `zrpc/config.go:31` 的 tag 默认：
```go
Timeout int64 `json:",default=2000"`
```
消费点在 `zrpc/client.go:70-71` → `zrpc/internal/client.go:83`，装进 unary interceptor 链。

⚠️ **同一个 yaml 里两个同名段的行为不同**，而**逐字段对 yaml 完全看不出来**。
RAG 侧只对齐了显式的 `ArticleRpcConf.Timeout=70000`，
对 `UserRpcConf` 直接套了「HTTP 预算 = ctx deadline」的通用等价 —— 而这条等价**只在 Go 侧没设 per-client 超时时成立**。

**为什么测试抓不到**
测试全部用 `@MockitoBean` 把 `userServiceBlockingStub` 换成同步 mock，
**秒回**。延迟作用域在任何断言里都不可见。

**元教训**
> **对齐 Go 的配置，不能只对齐「yaml 里写了什么」，要对齐「每个消费点实际拿到的值」。**
> 而且必须**逐段**（每个 `XxxRpcConf` 单独看），不能按「rpc 超时 = 70s」一锅端。

**下次怎么防**
1. 对 Go 配置逐字段跑一遍：`grep -o 'json:",default=[^"]*"'` 拿到全部 tag 默认值，
   再把 yaml 的显式值叠上去，得到**每个字段的真实生效值**。
2. 每个默认值在台账里单列一行，标出「声明点」与「**消费点**」两个行号（AGENTS.md §3.3.1 硬要求）。
3. 超时类结论必须配一条**慢响应探针**（Go 侧显式写 `Timeout` + 注入慢 client），
   静态推断出的超时值一律记 `UNKNOWN`。

---

## P-3 框架的「单值化」规则要连**数组塌缩**那一行一起看

**现象**
`?page=1&page=2`：Go 返回 **200，page=1**；RAG 返回 **400**。
`?incr_view=1&incr_view=0`：Go `true`；RAG 400。

**根因（框架机制）**
Go 的 query 解析分两步，**第二步常被忽略**：

1. `httpx/util.go:35-62`：同名 key 的多个值收成 `[]string` 存进 params。
2. `core/mapping/unmarshaler.go:835-846`（`WithFromArray` 的**消费点**）：
   ```go
   if u.opts.fromArray {
       if 目标字段不是 slice {
           mapValue = val.Index(0).Interface()   // ← 取第一个，其余丢弃
       }
   }
   ```

Spring 侧 `@RequestParam String` 对多值走 `RequestParamMethodArgumentResolver`
⇒ **逗号拼接**成 `"1,2"` ⇒ `Integer.parseInt("1,2")` 抛 ⇒ 400。

**为什么测试抓不到**
现有测试覆盖了「单值」「空值」「非数字值」，**没有一条覆盖「同一个 key 出现两次」**。
`GoFormValues.raw()`（`ArticleUploadService`… 更正：`form/GoFormValues.java:83-88`）
的职责边界写的是「空值丢弃」，多值不在其中。

**元教训**
> **「把 HTTP 参数收成 String 再自己解析」这条路线，必须同时补齐三类输入：**
> ① 缺席 ② 空值 ③ **多值**。
> 前两类几乎所有人都会想到，第三类最容易漏，而它恰好是框架「自动帮你做掉的」那一步。

**下次怎么防**
1. 自己实现参数解析时，把 Go 的 `WithFromArray` / `WithStringValues` / `WithOpaqueKeys`
   三个 option 逐条列出，每条对应一个输入形态测试。
2. 至少覆盖：`?a` / `?a=` / `?a=1&a=2` / `?a=1,2` / `?a[]=1&a[]=2` / 完全缺席。

---

## P-4 「注解存在」≠「机制生效」：本组件的 50 条测试恰好把要验证的那一段替换掉了

**现象**
`ArticleRpcClient` / `UserRpcClient` 用 `@GrpcClient("...")` 字段注入；
`ArticleApiApplication` 用 `scanBasePackages = {"com.rag.article.article.api", "com.rag.common"}`
依赖「扫到 `com.rag.common` ⇒ 保护层 / CORS / JSON 转换器 / 观测中间件全部自动注册」。
**全组件没有任何一条测试验证这五件事真的发生了。**

**为什么测试抓不到**
50 条测试全是 `@MockitoBean ArticleServiceBlockingStub` + `@MockitoBean UserServiceBlockingStub`。
**两个 client 都被 mock 掉了** —— 也就是说，测试验证的是「我 mock 的东西的行为」，
不是「装配是否成立」。这是 MockMvc/Mockito 测试的典型盲区：
它们验证逻辑分支，不验证容器。

**元教训（与 AGENTS.md §3.3 同源，但这里更隐蔽）**
> 替换掉被测依赖的测试，会让**装配层的 bug 完全不可见**，而且不产生任何失败信号。
> 「50 条绿测试」在这里恰恰是**覆盖率错觉的来源**：行覆盖率高，装配覆盖率 0。

**下次怎么防**
1. 凡引入 `@GrpcClient` / `@KafkaListener` / 依赖 `@ComponentScan` 自动注册的能力，
   必配一条**装配层**测试：起真实（或至少不 mock 掉该 Bean 的）上下文，断言 Bean 存在且非 null。
2. 显式列出本组件「靠扫描自动注册」的清单，逐条断言：
   `HttpMiddlewareFilter` / `GoStyleMaxConnsFilter` / `GoStyleCpuSheddingFilter` /
   `GoStyleMaxBytesFilter` / `GoStyleGrpcClientBreakerInterceptor` / `GoStyleJsonBodyConverterConfig`。
3. `@Order` 相同的两个 Filter（本组件的 path-normalize 与 CORS 都是 `HIGHEST_PRECEDENCE`）
   其先后**只有容器起来才知道** ⇒ 补一条「打印实际 Filter 顺序」的断言。
4. 参照 AGENTS.md §3.3 的 Boot-4 条款：凡 `spring-*-starter` 能力，先确认它在 Boot 4 下仍需显式模块。

---

## P-5 「代码里写了」与「客户端收到」之间隔着一次容器提交

**现象**
上游把这条教训写进了代码注释（`DRIFT-005`）：
`GoStyleJsonBodyLimitFilter.java:104-108` 刻意用 `setHeader("Content-Type", …)` 而**不用** `setContentType`，
理由是「后者会被 Tomcat 规范化并重写成 `text/plain;charset=utf-8`（分号后无空格）」。
这个判断在本组件**无法验证**，因为 50 条测试全是 MockMvc。

**为什么抓不到**
`HttpMiddleware`（`com.rag.common`）的注释里记着同款事故：
上一版只在 `flushTo` 里统一写 header，而 `flushTo` 在 `chain.doFilter()` **之后**执行，
此时 Tomcat 已 commit ⇒ `setHeader` 被**静默忽略**。4 轮对拍全绿，
只因为 difftest **只比 body 不比响应头**。

**元教训**
> MockMvc 能断言 `response.getContentType()`，但它**不经过**容器的 header 规范化与 commit 时序。
> 凡涉及「响应头的确切字节」或「header 写入时机」的断言，
> **MockMvc 的绿灯不构成证据**，必须真 Tomcat。

**下次怎么防**
1. 凡要断言 `Content-Type` / `X-Content-Type-Options` / `Vary` / `Access-Control-*`，
   一律起真实容器（`webEnvironment = RANDOM_PORT` + `TestRestTemplate`，或裸 `HttpURLConnection`）。
2. 本仓已有可复制的样板：`like-api` 的 `LikeApiRouterErrorRealTomcatTest`、
   `message-api` 的 `MessageApiProtectionRealTomcatTest`、
   `admin-api` 的 `AdminApiPlainText400HeaderProbeTest`。**照抄样板，不要自创。**
3. 断言时**比原始字节**（含 `"; charset=utf-8"` 的空格），不要用 `contentTypeCompatibleWith(...)`
   这种宽容比较。

---

## P-6 台账里的「实测计数」会**在同一天内**过期

**现象**
本文件所在台账的 §0 记录了一次真实执行：`src/main/java` **36 个文件** / **4,275 行**。
本轮复核：**38 个文件** / **4,666 行**，且 7 个主源文件在 **2026-10-23 当日**仍有写入。
两次「实测」都真实，间隔不到一天。

**根因**
计数类指标**不记录时间戳与「此后是否还有写入」**，就只是一张会腐烂的快照。
§0 那次的 mtime 是 `2026-10-22 23:56`，而当天 02:0x 已经有 7 个文件被改过 ——
说明写入在**那条记录之后**继续发生，而台账没有被更新。

**元教训**
> 「实测核对」不是一次性动作，是**带日期的断言**。
> AGENTS.md §6.3 的状态表纪律要求「每行带最后核对日期，且该日期必须由一次真实执行产出」——
> 补上**反向条件**（此后有无写入）才算完整。

**下次怎么防**
1. 每条计数后面附三个字段：`值` / `核对时间` / `此后是否仍有写入`。
2. 发现不一致时**追加**复核段落并标注「原文保留」，**不要覆盖** ——
   覆盖等于抹掉一次真实执行的证据（AGENTS.md 的处置方式）。
3. 「某模块已完成 / 未完成」这类结论，**不要用文件数当证据**：
   写「7 个端点逐条对照完毕，见 §4 矩阵」远比「38 个文件」抗腐烂。

---

## P-7 「它有这个配置项」≠「它有消费者」：先证明零消费，再决定不实现

**现象**
Go 的 `ServiceContext` 装配了**三个** gRPC client，其中 `SecurityRpc`
（`security.rpc`，即图片安全服务）在 `api/` 全目录下**只有定义、没有消费者**：
`grep -rn "SecurityRpc|ImageSecurity" api/` 只命中
`etc/article-api.yaml:14`、`internal/config/config.go:18`、
`internal/svc/service_context.go:17,25` —— 全是「定义它」。
RAG 侧**刻意不建**这个 client。

**根因（比「有没有消费者」多一层）**
「不建 client」看起来少了一个启动期行为（Go 会调 `zrpc.MustNewClient`）。
结论成立的**真正依据**不是「零消费者」，而是默认值链：

```go
zrpc/config.go:30    NonBlock bool `json:",default=true"`     // yaml 未配 ⇒ true
zrpc/internal/client.go:78-80
    if !cliOpts.NonBlock { options = append(options, grpc.WithBlock()) }
    // NonBlock=true ⇒ **不加** WithBlock ⇒ 非阻塞 dial ⇒ 解析不到也不 panic
```

**为什么容易搞错**
直觉会说「Go 启动时要连 security-rpc，RAG 不连 ⇒ 少了启动前置条件」。
但默认配置下**两条错误路径恰好同值**（都不失败）。
这是**巧合对齐，不是设计对齐** —— 一旦 yaml 加一行 `NonBlock: false`，结论立刻反转。

**元教训**
> 「不实现某个依赖」的论证要分两层写：
> ① **响应层面**：有没有消费者？（零消费者 ⇒ 行为不可观测）
> ② **生命周期层面**：装配它会不会改变启动结果？（取决于 tag 默认值，要读到消费点）
> 只写第 ① 层就下结论，等于把一个可反转的结论写成永久事实。

**下次怎么防**
1. 跳过任何「配了但没人用」的东西之前，先跑一遍「消费者 grep」。
2. 若结论依赖 tag 默认值，在台账里**明确标注依赖**并登记为 UNRESOLVED
   （本仓做法：`docs/DRIFT/article/api.md` §1.3 + §9 UNRESOLVED-007）。

---

## P-8 「业务上显然该有的规则」在真源里可能不存在 —— 而直觉会把它加回去

**现象**
`POST /v1/upload` —— 「上传图片必须登录」是任何人凭直觉都会加的规则。
Go 侧**没有**：`routes.go:63-73` 的第三段 `AddRoutes` 不带 `rest.WithJwt`。
RAG 侧通过一张**带 method 的**受保护路由表精确排除它。

**根因（框架机制的形状差异）**
Go 的鉴权边界挂在**路由注册**上（`rest.WithJwt` 是 `AddRoutes` 的 option）。
Spring 的 `addPathPatterns("/v1/**")` 只按**路径**匹配，看不到 method。
若照抄成 `addPathPatterns("/v1/article/**")`：

```
POST /v1/article/123     Spring: 拦截器 → 401（无 token）
                        Go:     该路由未注册 → 404，且**根本不进鉴权链**
```

⇒ 凭空多出「401 压过 404」的一整条路径。

**元教训**
> 从 Go 迁到 Spring 时，**「路由级开关」降级成「路径级拦截器」**，
> 必须把 Go 的「method × path」二维约束显式重建，否则会：
> ① 过度拦截（公开端点被要求带 token）；② 过度放行（未注册路径被当成受保护）。
> 两条错都是**静默**的，且方向相反。

**下次怎么防**
1. 把 Go 的每段 `AddRoutes(..., WithJwt/WithPrefix/WithTimeout/WithMaxBytes, ...)`
   逐 option 抄成一张表，**一个 option 一行**，并在 RAG 侧标出对应物
   （本组件做在 `ArticleController` 的类注释 + `JwtAuthInterceptor.PROTECTED_ROUTES`）。
2. 补一条「路由匹配先于鉴权」的守卫（`handler instanceof HandlerMethod`）——
   本组件**必须有**，因为它有 3 个公开端点，一旦守卫丢失，404 的形状会随路径落在保护区还是公开区而变化。
3. 加一条**边界快照测试**：「7 个端点的鉴权边界：4 个要 JWT、3 个公开」，
   任何人改动路由表时这条会红。

---

## P-9 响应成形层的「放行分支」比包装逻辑本身更容易出错

**现象**
`GlobalResponseAdvice.beforeBodyWrite` 需要**五条**放行分支才能既包信封又不误包错误响应。
其中任何一条缺失，症状都不是报错而是**形状悄悄变了**：

| 漏掉 | 后果 |
|---|---|
| `body == null` | 401 空 body 被包成 `{"code":200,"msg":"OK","data":null}` ⇒ **鉴权失败伪装成成功** |
| `body instanceof Response` | 双层信封 `{"code":200,…,"data":{"code":1002,…}}` |
| `returnType` 是 `ResponseEntity` | `POST /v1/upload` 的裸响应被包进信封 ⇒ 客户端读不到 `image_url` |
| `byte[]` / `String` | 400 text/plain 被包成 `{"code":200,…,"data":[B@1234]}` ⇒ **解析失败变成成功** |
| 非 JSON media type | 同上 |

**根因（框架机制）**
`HttpEntityMethodProcessor` 在处理 `@ExceptionHandler` 返回的 `ResponseEntity` 时，
**会继续查 advice chain**（AGENTS.md §3.3.3 已实证的静默失效形态）。
它会**先把 `ResponseEntity` 拆开**，所以：

- `body instanceof ResponseEntity` **恒为假** ⇒ 判据必须看 `returnType.getParameterType()`；
- 错误处理器的 `ResponseEntity<byte[]>` 拆开后 `body` 就是 `byte[]` ⇒ 也会落进 advice。

**为什么测试抓不到（在本组件的特定形态）**
第一版实现就漏了 `byte[]` 那条，5 条断言 400 text/plain 的用例全部失败 ——
**这条是绿的**。反过来，如果测试只断言「状态码」不断言「body 不含 `code`」，
漏掉放行分支 1/2 会完全静默。

**元教训**
> advice / filter 这类**改写所有响应**的层，它的测试必须**逐条覆盖每条放行分支**，
> 而且断言要落在「**不该被包装的形状**」上（例如「body 不含 `\"code\"`」），
> 不能只断言「状态码对」—— 状态码对而 body 被包，是最常见的漏网形态。

**下次怎么防**
1. 每条放行分支配一条**专门的**用例，命名写清「哪个形状、为什么不许被包」。
2. `GlobalExceptionHandler` 每条分支的测试同时断言 **Content-Type** 与 **body 前缀**，
   不要只断言 `status().isXxx()`。

---

## P-10 「同一语义的错误映射」不要合并成「一个 mapper」

**现象**
`PUT /v1/article/:id` 的**读**（鉴权那次 `GetArticle`）有 4 个分支，
**写**（`UpdateArticle`）只有 3 个 —— 写的那份**没有** `PermissionDenied → 1006`。
而 `DELETE` 的读写两处是**逐字相同的 4 分支**。
这看起来像「代码重复，应统一」，但它们是 Go 的现状（真源如此）。

**根因**
这不是冗余，而是**两次 RPC 的真实契约不同**：
读的那次就是鉴权，越权必须给 1006；写的那次正常路径下越权已在读阶段被拦，
所以少了那一支。`DELETE` 因为写调用本身也要按操作人鉴权，所以两处都有。

**元教训**
> 「两处结构相同 ⇒ 抽成一个函数」是重构直觉；
> 「两处分支集合不同 ⇒ 那是行为的一部分」。
> **错误映射表是行为，不是样板代码。**

**下次怎么防**
1. 合并前先逐条列两处的分支集合；有差异就不合并，并在两侧注释里写明「刻意不同」。
2. 反向自查：如果**合并后**业务码集合变大了或变小了，那就是行为改动，必须登记为 DRIFT。
3. 抽取时把差异写进函数名/注释（本组件：`mapAuthReadError` vs `updateArticle` 内联三分支）。

---

## P-11 「从 status 文案里抠 ID」：脆弱协议必须逐字复刻，不能顺手「改进」

**现象**
`create_article_logic.go:82-92` 从 gRPC status 的**消息文本**里，
取**最后一个冒号之后**的内容当作 `article_id`：

```go
idx := strings.LastIndex(message, ":")
if idx < 0 || idx+1 >= len(message) { return "" }
return strings.TrimSpace(message[idx+1:])
```

RAG 侧逐字复刻了它（`ArticleCreateService.java:144-153`），包括
「无冒号返回空串」「冒号在末尾返回空串」两个边界。

**元教训**
> 这类「约定优于契约」的协议有两条纪律：
> ① **逐字复刻，包括你觉得是 bug 的边界**（这里的 `""` 而不是整串）；
> ② 复刻时**在注释里写明「不要改成结构化传递」并说明为什么**（需要改对端 = 跨组件变更，超出本组件范围）。
>
> 同时它是一个**跨组件不变量**：api 侧解析 + rpc 侧拼装，两边都在改就会破。

**下次怎么防**
1. 登记为**跨组件条目**（本仓：写进 `docs/DRIFT/<域>/api.md` 并注明「需与 `rpc.md` 合并判读」）。
2. 若日后要改成结构化传递（metadata / 新字段），必须**两侧同批改**并登记为**已签字的有意偏离**。

---

## P-12 域专属补强要**局部叠加**，不要污染已验证的共享层

**现象**
共享的 `GoStyleCoercion` 保留了一条豁免：「String → 整数字段保持接受」，
理由是 users 域的 uid 带 `,string`（真源实测）。
但 **article 域的 `types.go` 全文没有一个 `,string` option** ——
`{"status":"2"}` 在 Go 侧必然是 `type mismatch` ⇒ 400。
若直接用共享规则，Spring 会**接受**它并真的下发到 article-rpc ——
一个 Go 侧拒绝、RAG 侧接受的请求。

**元教训**
> 共享层里每一条豁免都带着**当初实测它的那个域的前提**。
> 前提不成立的域要**局部叠加**补强，**不要改共享层** ——
> 改共享层会连带把已验证域的行为退回去，而且退得很安静。

**次生教训：coercion 对 `Object` 形参无效**
本模块的 `CreateArticleReq` / `UpdateArticleReq` setter 形参刻意是 `Object`
（为了区分「key 缺席」与「显式 null」两条错误文案）。
Jackson 的 coercion 要作用在**声明的目标类型**上，形参是 `Object` ⇒ **没有落脚点**。
⇒ 类型判定必须自己补（`GoStyleStringFields`）。上游注释自述：
「实测：只挂 coercion 不加本类时，`{"title":123}` 被**成功**绑定」。
**「框架能力写了」与「框架能力生效」是两件事。**

**下次怎么防**
1. 补强前先跑一次「真源逐字段扫描」，确认该域**确实没有**触发共享豁免的前提。
2. 补强写成域内 `XxxGoStyleCoercion`，共享层**一行不动**。
3. 每条 coercion 规则配一条「该形状必须被拒」的用例 —— 尤其检查**形参是 `Object` 的字段**。