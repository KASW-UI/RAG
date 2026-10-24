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
---

## P-13 grpc-java 的 `withDeadline` 是**覆盖**，不是「取最小」

> ⚠️ **本条对所有域适用**（凡同时用「请求级 deadline」+「per-client 超时上限」的地方）。
> 合并时建议搬到 `docs/Pitfalls/_cross/`。

**现象**
给 `user-rpc` 的 stub 补 Go 侧的 per-client 上限（2s）时，最自然的写法是：

```java
GoStyleRequestDeadline.applyTo(stub).withDeadlineAfter(2, TimeUnit.SECONDS)
```

⇒ 看起来「HTTP 预算与 2s 上限都设上了」。但实测发现：**HTTP 剩余预算被顶掉了**。
HTTP 只剩 0.5s 时，这个 stub 拿到的是 **2s** ⇒ 单次调用可能**超出 HTTP 总预算**。

**根因（框架机制）**

Go 侧（`zrpc/internal/clientinterceptors/timeoutinterceptor.go:25`）：

```go
ctx, cancel := context.WithTimeout(ctx, t)      // ← 取父子 deadline 中**较早**的
```

Java 侧（**grpc-api 1.66.0 字节码实测**，`io.grpc.CallOptions`）：

```
withDeadlineAfter(duration, unit) → Deadline.after(...) → withDeadline(deadline)
withDeadline(deadline)            → builder.deadline = deadline;   // ← **直接覆盖**
```

⇒ Go 的 `context.WithTimeout` 是「**父子取小**」，
grpc-java 的 `withDeadlineAfter` 是「**新的覆盖旧的**」。**语义不同，且没有任何告警。**

**为什么测试抓不到**
「不顶掉预算」是一个**否定式**断言：
「deadline ≤ HTTP 剩余预算」。若测试只断言「deadline ≈ 2s」，
覆盖行为与取小行为**都满足** ⇒ 假绿。
⇒ 必须**显式构造「HTTP 预算 < client 上限」的场景**才能测出来。

**元教训**
> **「设置一个上限」在不同框架里可能是完全不同的两件事。**
> Go 的 `context.WithTimeout` = 取小；grpc-java 的 `withDeadlineAfter` = 覆盖。
> ⇒ 迁移时**不能假设语义等价**，必须回到两边各自的**消费点**确认。

**下次怎么防**
1. 凡是「请求级 deadline」与「per-client 上限」**并存**，一律**显式算 `min`**，不依赖框架合并。
2. 回归网必须包含**「HTTP 预算更短」**与**「预算已耗尽」**两个场景 ——
   只测「上限生效」测不出覆盖。
3. 断言用**相对量**（`remaining ≤ min(预算, 上限)`），不要用绝对值。

---

## P-14 Go 的 `codes.Code.String()` 是 CamelCase，Java 的 `Status.Code.name()` 是 SCREAMING_SNAKE

**现象**
把 gRPC 错误映射成 HTTP 状态码后，还要复刻 Go 的**错误文案**。
Go 的 `http.Error(w, err.Error(), …)` 写的是 `err.Error()`，
而 grpc-go 的 `*status.Error.Error()` 转发到 `Status.String()`：

```go
fmt.Sprintf("rpc error: code = %s desc = %s", s.Code(), s.Message())
```

⇒ Go 的 body 形如 `rpc error: code = Unavailable desc = minio down`。

Java 侧最自然的写法 `code.name()` 给的是 **`UNAVAILABLE`** ⇒ **wire 上字面不同**。
再叠加 `StatusRuntimeException.getMessage()` 给的是 `UNAVAILABLE: minio down`（**另一种**形状），
一共三种可能写法，**没有一种是 Go 的**。

**根因（语言映射）**
两边的**枚举命名约定**不同（Go 的 `codes` 用 CamelCase 的 `String()`，Java 用 SCREAMING_SNAKE 的 `name()`），
且**异常类的 `getMessage()` 约定**也不同（grpc-go 把 code+desc 编进 `Error()`，
grpc-java 把它编进 `Status.toString()`，`StatusRuntimeException.getMessage()` 又另走一路）。

**为什么测试抓不到**
状态码（400/503）对了就容易收工 —— 但 **body 是另一层**。
若测试只断言状态码，body 的差异**完全不可见**。
⇒ 而且 body 的差异**只有在客户端解析这段文案时**才显形（本域的
`extractArticleIDFromStatusMessage` 就是从 status 文案里抠 article_id 的那类协议）。

**元教训**
> **「错误语义」至少有三层：状态码 · 错误码 · 文案。**
> 只对齐第一层就收工，是这类漂移最常见的形态 ——
> 状态码对了，客户端仍可能因为文案不同而解析失败。

**下次怎么防**
1. 断言错误响应时，**状态码与 body 逐字**都要断（body 断言用 `startsWith`，不要只断 `contains`）。
2. 需要复刻 Go 文案时，**显式维护一张规范名映射表**，不要用 `name()` / `toString()` 顶替。
3. 文案差异按 §3.1 归类：若只是**文案**不同而码与状态码一致，可记为
   `IMPLEMENTATION DIFFERENCE`；若文案被下游协议依赖（如抠 ID），按 `SEMANTIC MISMATCH` 记。

---

## P-15 变异验证：被变异的模块**没被重编** ⇒ 会产出**假的「仍全绿」**

**现象**
变异 `service/common` 里的 `GoStyleCpuSheddingFilter` 的 `@Order`（+3 → +9），
想验证新写的 Filter 链序断言能不能抓住它。脚本判定：**仍全绿 ⇒ 假绿**。

但断言**本身是对的** —— 加 `-am` 重编 `service/common` 之后，同一个变异**立刻转红**。

**根因（构建机制）**
```bash
mvn -pl service/article/article-api test          # ❌ 不带 -am
```
⇒ `service/common` **不参与本次构建**，用的是**本地仓库里已 install 的旧 jar**
⇒ **源码改了，运行的东西没变** ⇒ 变异**根本没生效** ⇒ 测试当然还绿。

**元教训**
> **变异验证的判据是「行为变了」，不是「源码变了」。**
> 源码改了而产物没变 ⇒ 变异无效 ⇒ 「仍全绿」**不能**判成「回归网无效」，
> 只能判成「**变异没生效**」。
> ⇒ **三态判定是必须的**：`转红` / `仍全绿` / `变异无效`。
> 本轮之前已因为 `sed` 多行替换失败踩过一次「假绿」，**同一个坑踩了两次**。

**下次怎么防**
1. 变异**非本模块**的源码时，命令必须带 `-am`（或先 `mvn -pl <被变异模块> install -DskipTests`）。
2. 带 `-am` 时 `-Dtest=X` 会传播到所有模块 ⇒ 还要补
   `-Dsurefire.failIfNoSpecifiedTests=false`，否则先在别的模块上失败。
3. 每次变异后**先确认变异真的进了产物**（如 `javap` 看字节码 / 看编译时间戳变化），
   再看测试红不红。

---

## P-16 「我写的那个字节」到不了客户端 —— 框架的**归一化**会覆盖它

**现象**
为了对齐 Go 的 `http.Error`，RAG 侧写的是**逐字相同**的
`Content-Type: text/plain; charset=utf-8`（**分号后有空格**）。
真 Tomcat 实测：客户端收到的是 **`text/plain;charset=utf-8`（无空格）**。

⇒ 第一反应是「常量写错了」⇒ 把它改成无空格 ⇒ **输出字节没变**。
⇒ 真相是：**写进去的那个原值根本没到达客户端**。

**根因（框架机制）**
Spring 的 `ServletServerHttpResponse.writeHeaders()`：

```java
getHeaders().forEach((name, values) -> values.forEach(v -> servletResponse.addHeader(name, v)));
// ↓ 紧接着
if (servletResponse.getContentType() == null) {
    MediaType contentType = getHeaders().getContentType();   // ← parseMediaType 再 toString
    if (contentType != null) servletResponse.setContentType(contentType.toString());
}
```
⇒ **`MediaType.toString()` 不在 `;` 后加空格** ⇒ 刚 `addHeader` 进去的原始值被**覆盖**。

⚠️ 同一个仓里已经有一处**绕过**了它（`GoStyleJsonBodyLimitFilter` 用 `setHeader` 裸写），
所以「为什么那一处对、这一处不对」曾是个谜 —— 答案是**路径不同**（Filter vs ResponseEntity）。

**为什么测试抓不到**
MockMvc **不经过 Tomcat 的 header 规范化** ⇒ 50 条 MockMvc 测试全绿，
而真客户端拿到的是另一个字节串。

**元教训**
> **断言要打在「客户端真正收到的」那一份上，而不是「我写出去的」那一份。**
> 中间隔着：框架的消息转换器 → `MediaType` 的解析与再序列化 → 容器 header 规范化。
> ⇒ 只要这条链上有一环会**再序列化**，你写的常量就只是**意图**，不是**结果**。

**下次怎么防**
1. 涉及**响应头字节**的契约，必须有**真容器**（`RANDOM_PORT`）用例 + **JDK `HttpClient`** 断言
   —— MockMvc 只能证明「应用层设了这个 header」。
2. 断言**客户端收到的那一份**（`response.headers().firstValue("Content-Type")`），
   不要断言 `ResponseEntity` / `HttpServletResponse` 上设的值。
3. ⚠️ **characterization test 的写法**：当实测与 Go 不一致又决定不修时，
   断言**实测值**，但 `@DisplayName` 必须写明「与 Go 差在哪」——
   否则就变成 AGENTS.md §3.3 说的「把 RAG 的行为写成 Go 的事实」。

---

## P-17 「两侧排序一致」的用例若只用 ASCII ⇒ 它**测不出**任何排序差异

**现象**
`ext_info` 的 key 要按 Go 的顺序输出。RAG 侧本来就用 `TreeMap`（会排序），
台账把它判成 OBSERVABLE DIFFERENCE（"UTF-8 字节序 vs UTF-16 码元序"），
但**两侧其实在绝大多数输入上是一致的** —— 只在一个很窄的边界上分叉。

**根因（不是框架机制，是**比较语义**）**

| | 排序依据 |
|---|---|
| Go `encoding/json`（`encode.go:805-807`） | `strings.Compare` ⇒ **UTF-8 字节序** = **码点序** |
| Java `String.compareTo` | **UTF-16 码元序** |

- BMP 内字符：UTF-16 码元序 == 码点序 == UTF-8 字节序 ⇒ **完全一致**。
- **非 BMP**（增补平面，U+10000+）：UTF-16 用代理对表示（U+D800–U+DFFF），
  **排在 U+E000–U+FFFF 之前**；码点序里排在**之后** ⇒ **分叉**。

实测（Go 1.25.11 + JDK 21，同一组 key `a` / `中` / U+E000 / U+FFFD / `😀`）：

```
Go encoding/json       : a, 中, U+E000, U+FFFD, 😀      ← 码点序
Java TreeMap 自然序     : a, 中, 😀, U+E000, U+FFFD      ← ❌ 分叉
Java TreeMap 码点比较器 : a, 中, U+E000, U+FFFD, 😀      ← ✅ 与 Go 一致
```

**为什么测试抓不到**
原来的用例只用 ASCII key（`Mid` / `alpha` / `zeta`）⇒ **恰好落在两侧一致的那一侧** ⇒ 永远绿。
⚠️ 而且它还"证明"了"Java 的自然序恰好同构"—— **只对 BMP 成立**，却把结论写成了普遍成立。

**元教训**
> **写"两侧应当一致"的用例之前，先问一句：「什么样的输入会让两侧不一致？」并把它写进用例。**
>
> ⇒ 对**排序 / 比较 / 编码 / 精度 / 边界值**这类契约，
> 随手写的用例几乎必然落在"两侧碰巧一致"的区域 ⇒ 用例**假绿**，
> 而且还会反过来给出一个**过强的结论**（"自然序恰好同构"）。
>
> ⇒ 判据：**用例里必须出现一个"专门挑出来的分歧点"**，
> 并在注释里写清「两侧为什么会在这个输入上分叉」。

**与本轮另一条同源**
`DRIFT-ARTICLE-RPC-010`（直方图桶）是同一类错误的另一面：
一个**测了名字没测桶**，一个**测了 BMP 没测非 BMP**。
⇒ 通用处方：**对每个"已对齐"的断言，问一句"它到底测的是哪一面"**。

**顺带两条实操细节**
1. `MockHttpServletResponse.getContentAsString()` 用的是 response 的 character encoding
   （没带 charset 时是 **ISO-8859-1**）⇒ 断言非 ASCII 内容必须用
   `getContentAsByteArray()` + **UTF-8** 解码。
2. Java 里要按**码点**排序，不能用 `String.compareTo`；
   实现时逐码点比较，并注意**一方是另一方前缀**时短者在前（与 `strings.Compare` 一致）。

---

## P-18 「你的代码里没写」≠「运行时没有」：RAG 跑在**容器**里，容器自带一堆上限

**现象**
台账写「`DRIFT-005` query 参数总数上限 2048 ⇒ Go 有上限，**RAG 无上限**」。
依据是源码：Spring 的 `@RequestParam` 确实不限制参数总数。
⇒ 于是这条被当成「RAG 侧缺一道 DoS 闸门」记成了 OBSERVABLE DIFFERENCE。

**2026-10-24 真 Tomcat 实测**

| 场景（默认配置） | 实测 |
|---|---|
| 2049 个非空 query 值（≈8196 字节） | **400**，body = **Tomcat 的 HTML 错误页** |
| **2048** 个非空 query 值（≈8192 字节） | **400**（Go 允许的上限在 RAG 上**就已经被拒**） |
| 3000 个**空值** query 参数（≈6000 字节） | 请求**通过** ⇒ 走到 controller ⇒ 下游 gRPC 不可达 ⇒ 500 |

⇒ **RAG 并不"无上限"**：Tomcat 在**连接器层**就拒了
（Boot 4 的 `server.max-http-request-header-size`，默认 **8 KB**），
而且**比 Go 更严**（≈2040 个值 < Go 的 2049）。
⇒ 这条漂移从「DoS 暴露」降级成「**响应体形状**差异（Tomcat HTML vs Go text/plain）」。

> 🟢 **2026-10-25 第十三轮订正**：上表描述的是「**默认配置**」下的行为，
> 而**默认配置已被改掉**（10 个 api 模块统一配 `server.max-http-request-header-size: 1028KB`
> ＋ `server.tomcat.max-parameter-count: 2048`）⇒ 实测：
> 2049 个非空值 ⇒ **400 + Go 的 text/plain body**；**恰好 2048** 个 ⇒ **200**（与 Go 一致）。
> ⇒ 本条「响应体形状差异」**已关闭**（`DRIFT-ARTICLE-API-005` 结案）。
> ⇒ **元教训本身依然成立**（容器自带上限、且比业务代码更早触发）——
> 只是**上限被调到了 Go 的值**，于是「更严」变成了「一致」。
> ⇒ ⚠️ 「另开一个测试类把上限调大」那条做法**仍然需要**
> （`ArticleApiFormParamLimitTest` 仍显式覆盖 `=64KB`）——
> 但它的**理由变了**：不再是「默认 8 KB 挡住了闸门」，而是「**别让本模块的上限配置影响这条用例**」。

**元教训**
> **「RAG 侧没有 X」这类断言，必须在运行时验证，不能只看 RAG 的源码。**
> ⇒ 因为 RAG 跑在**容器**里，容器自带一堆上限（请求行长度、参数个数、参数大小、body 大小…），
> 它们往往**比业务代码更早**触发。
> ⇒ 这是 AGENTS.md §3.3.1「配置里没写 ≠ 没开」的**镜像版**：
> **「你的代码里没写 ≠ 运行时没有」**。
> ⇒ 推论：**台账里凡是"RAG 缺少某个上限/校验"的条目，都要先用真容器量一次**，
> 否则很可能把一条**更严的**现实误判成"缺失"。

**元教训 2：一条闸门在默认配置下不可达 ⇒ 它的回归网要单独开一个测试类**

本例里，应用层的 2048 闸门在**默认配置下永远不触发**（Tomcat 先拒）⇒
**用默认配置的测试类根本测不到它**（会一直绿，且是**假绿**：断言的是 Tomcat 的行为）。
⇒ 做法：另开一个测试类，把 `server.max-http-request-header-size` 调大，
**只为**让请求能进到应用层，然后在那里断言闸门本身的语义。
⇒ 与 P-15（变异时模块没被重编）同族：**「测试跑过了」不等于「测到了你写的那段」**。

**元教训 3：属性名写错 ⇒ 被静默忽略 ⇒ 看起来像"实现没生效"**

第一版测试写的是 `server.max-http-header-size=65536` ⇒ Boot 4 里**这个属性已不存在**
⇒ 未知的 Boot 属性**不报错**，被静默忽略 ⇒ 请求仍被 Tomcat 以 400 拒掉
⇒ 现象是「**我的 filter 好像没执行**」。
⇒ 真正的属性名是 `server.max-http-request-header-size`（Boot 4 的 `ServerProperties`：
`maxHttpRequestHeaderSize`，默认 `DataSize.ofKilobytes(8)`）。
⇒ 对照 AGENTS.md §3.3：**配置写错/写旧名 ⇒ 机制静默不生效，且没有任何告警**。

---

## P-19 域内编号跨域会撞号：「查无此编号」可能是**搜窄了**，不是"不存在"

**现象**
`UNRESOLVED-008` 记的是：`GlobalExceptionHandler.java:118` 的注释引用了「DRIFT-024」，
而 **`docs/DRIFT/` 全树查无此编号** ⇒ 由此推断「存在一份**未入库**的历史漂移清单」。

**真相**（2026-10-24 全仓 + 全部工作树搜索后）：
`DRIFT-024` **存在** —— 在 **users 域**的 `docs/DRIFT/users/_shared.md:103`
（「text/plain 400 的响应头丢失」），`docs/DRIFT/common/http.md` 与
`docs/DRIFT/_cross/status-snapshot-2026-10-22.md` 都在引用它。

⇒ **推断错了**：不是"有一份未入库的清单"，而是**我搜窄了**（只搜了本域/本仓的某些位置）。

**根因**
`DRIFT-0xx` / `UNRESOLVED-0xx` 是**域内编号**，**每个域各自从 001 开始**
⇒ 同号在不同域指向**完全不同的条目**
⇒ 跨域引用若不带域前缀，**在别的域的台账里根本查不到**。

**元教训**
> **「查无此编号」有两个可能：「它不存在」与「我搜窄了」—— 后者更常见。**
> ⇒ 检索"查不到"时，必须：**全仓 + 全部工作树 + 别的域的台账**一起搜，
> 而不是据此推出"存在未入库的清单"（那是一个**很重的结论**，需要更硬的证据）。
> ⇒ 同理，**在注释里引用别的域的编号**时，要么写全路径
> （`docs/DRIFT/users/_shared.md` 的 `DRIFT-024`），要么别引 ——
> 否则下一个 agent 会和你一样查不到，然后重复掉进同一个坑。

**顺带一条：跨域引用的条目，本身可能是"本域不受影响"的**
⇒ 找到之后还要**确认它对本域的实际影响**（本例：共享层已修 + 本域真 Tomcat 用例已断言
⇒ 不受影响），而不是"找到了 ⇒ 就算一条漂移"。

---

## P-20 「错误被记录了」≠「错误被上报了」：复刻 Go 的解析，**必须复刻它失败时怎么办**

**现象（本轮实际发生的一件事）**
本轮把 `GoStyleFormParamCountFilter` 改成复刻 Go 的 `net/url` 解析规则。
自测时逐条对着 Go 的 `parseQuery` 源码核对，分段规则、`;` 跳过、`%` 解码失败跳过 —— **全对**。

但真容器实测（2026-10-24）发现：

| 场景 | Go | RAG（当时） |
|---|---|---|
| `?a=1;b=2` | 400 + `invalid semicolon separator in query` | 🔴 **200**（请求被接受） |
| `?a=%zz` | 400 + `invalid URL escape "%zz"` | 🔴 **500 空 body** |

⇒ 复刻了「**怎么解析**」，漏了「**解析失败时怎么办**」⇒ 一个 400 变成了 200。

**根因（在 Go 侧，是「一行代码」的差别）**
```
Go 1.25 标准库 net/url/url.go:1019-1054  parseQuery
  if strings.Contains(key, ";") {
      err = fmt.Errorf("invalid semicolon separator in query")   // ← **记**错误
      continue
  }
  …
go-zero@v1.10.2/rest/httpx/util.go:23-25  GetFormValues（ParseForm 的 error 在这里上抛）
  if err := r.ParseForm(); err != nil {
      return nil, err          // ← **抛**错误（不是吞掉）
  }
```
⇒ 链是：`parseQuery` 记 ⇒ `ParseForm` 返回 ⇒ `GetFormValues` 抛 ⇒ `httpx.Parse` 失败
⇒ `httpx.ErrorCtx` ⇒ `http.Error(w, err.Error(), 400)`。
⇒ **中间任何一层把它吞了，Go 就不会返 400** —— 而这一层恰好是 go-zero 的一行代码。

**元教训**
> **复刻一个「会失败」的函数时，「失败时怎么办」不是细节，是它的可观察行为。**
>
> 「分段规则」是**成功路径**的语义；「错误怎么冒泡」是**失败路径**的语义。
> 只复刻前者 ⇒ 在畸形输入上两侧行为不同，而且**测试全绿**（因为测试用的是正常输入）。
>
> ⇒ 判据：**这个函数有可能失败吗？** 有 ⇒ 必须把「失败时的输出」也逐条对照，
> 而且**必须挑一条会让它失败的输入**做用例。
>
> ⇒ 同 AGENTS.md §3.3.1 的镜像版：**「错误被记录了」≠「错误被上报了」**。

**下次怎么防**
1. 复刻 Go 的某个函数时，把它的**所有 return 路径**列出来（成功 / 各种失败），逐条对。
2. 写用例时，**刻意挑一条会让 Go 失败的输入**，而不是挑一条「两边都成功」的。
3. Go 侧用**真包**跑探针（`httpx.Parse`，不是自己 new 一个 `url.ParseQuery`）——
   因为「谁把错误抛出去」这件事，**恰恰是在调用链的中间层决定的**。

---

## P-21 「仍全绿」的第一嫌疑是「**变异没打中**」，不是「测试测不到」

**现象（本轮实际发生的一件事）**
本轮给新写的三条 query 解析用例做变异验证，两次拿到「仍全绿」：

| 变异 | 结果 | 真相 |
|---|---|---|
| B₁：**只**删掉 **key** 分支的错误上报 | 仍全绿 | ⚠️ **变异没打中** —— 用例测的是 **value** 分支（`?a=%zz` 的坏转义在 value 里） |
| D₁：把段数上限挪到 2048 计数**之前** | 仍全绿 | ⚠️ **变异没打中** —— 挪了位置，但**相对顺序没变**（仍是「10000 先」） |

⇒ 两次都不是「测试测不到」，而是**变异本身无效**。
⇒ 若不查这一步，就会得出「这条用例测不出锁序/顺序」的**错误结论**，
进而可能去写一条**其实不需要**的新用例，或错怪测试本身。

**根因**
三态判定里的「仍全绿」有**两种**含义，含义相反：
- 「测试覆盖不到这个维度」⇒ 要**另写探针**（见 PF-ARTICLE-RPC-012）；
- 「变异没生效」⇒ **测试是好的**，变异要重做。

⇒ 而「变异没生效」有多种来源：
① **打错分支**（B₁：改的是 key，用例测的是 value）；
② **相对量没变**（D₁：挪了位置，但两个检查的**先后**没变）；
③ **产物没重新编译**（P-15 已记：`-pl` 不带 `-am`）；
④ **变异本身写错了**（本会话早前踩过：`sed` 多行替换失败 ⇒ 文件改坏 ⇒ 编译失败
   ⇒ 脚本把「没跑到测试」误判成「仍全绿」）。

**元教训**
> **「仍全绿」不是一个结论，是一个待排查的状态。**
> ⇒ 在写「这条用例测不到 X」之前，**先证明变异真的生效了**。
>
> ⇒ 对照 AGENTS.md §3.3「三态判定」：本轮把它从「三种结果」细化成
> **「仍全绿」还要再分「测不到」与「变异无效」两支**。

**下次怎么防**
1. 变异后**先确认它进了产物 / 打在了被测行为上**：
   - `grep -c "MUT-…"` 确认标记在文件里；
   - 改的是**相对顺序**时，**看代码确认相对次序真的变了**（本轮 D₁ 就栽在这）。
2. 变异**打偏**时的信号：失败信息与「被测行为」毫无关系
   （例如改 key 分支却测 value）⇒ 那就是变异打偏了。
3. 脚本判定「仍全绿」时，**必须同时打印变异内容**，让「没打中」当场可见。

## P-22 「端到端测不到」是**两个问题**；「javadoc 写了 Go 出处」是**待核实的断言**，不是证据

**现象（2026-10-24 台账审计时实际挖到的一件事）**

审计 `article-api` 的 Go 行号引用时，发现 `GoStyleGetFormValuesFilter.isGoPrintable` 的 javadoc 写着：

> Go 的 `strconv.IsPrint`（`strconv/quote.go:296-303`）

两件事同时成立：

1. **行号指错了函数** —— `quote.go:296-303` 落在 `UnquoteChar`（起于 `:259`）的转义解析里，
   `IsPrint` 的真身在 **`:522`**；
2. **代码也没实现 Go 的语义** —— RAG 写 `r >= 0x20 && r < 0x7F`，
   而 Go 在 **`0xA1..0xFF` 判可打印**（唯一例外 `0xAD` 软连字符）
   ⇒ `é` 在 Go 是原样输出（`c3 a9`），在 RAG 是 `\u00e9` ⇒ **wire 字节不同**。

**坑在哪：三处**

| # | 坑 | 本次的具体表现 |
|---|---|---|
| 1 | 「**端到端测不到**」被当成「**不用管**」 | 这段分支在 HTTP 路径上确实进不去 ⇒ 差点按「不可观察 ⇒ 不修」结案 |
| 2 | 「**端到端测不到**」被当成「**不用写网**」 | 若真不写网 ⇒ 这段代码就**永远没人守**，下次有人「简化」它时**没有任何网会响** |
| 3 | 「**javadoc 写了 Go 出处**」被当成「**已经对齐了**」 | 行号错 + 语义错 ⇒ **两层都是「看起来对齐」**；而 javadoc 恰恰是最容易骗过 review 的地方 |

**根因：框架的「连接器级」校验先于应用层**

`goQuote` 的唯一输入是 **query 串**里 `%` 开头的**原始字节**切片
⇒ 要让非 ASCII 进去，请求行里就得有原始非 ASCII 字节
⇒ **Tomcat 在连接器层就拒了**（Tomcat 11.0.24，逐行）：

- **HTTP/1.1**：`Http11InputBuffer.java:468-473` ⇒ 逐字节查 `HttpParser.isQueryRelaxed`，
  不过即抛 `IllegalArgumentException` ⇒ 400（**应用层 filter 根本没跑**）；
- **HTTP/2**：`StreamProcessor.java:539-545` ⇒ 对 `:path` 的 query 段做**同一个**检查 ⇒ 400；
- **判据本身**：`HttpParser.java:145-172` ⇒ `IS_QUERY_RELAXED` 只由
  `IS_USERINFO` / `'%'` / `IS_SUBDELIM` / `':'` 组成（`:97-108`）⇒ **全是 ASCII**
  ⇒ `0x80..0xFF` 恒为 false；
- **放宽口子也堵死**：`HttpParser.java:1138-1148`（`relax`）只放宽
  `isRelaxable` 字符集 `" < > [ \ ] ^ \` { | }`（`:114-117`）⇒ **同样全是 ASCII**
  ⇒ **任何 `relaxedQueryChars` 配置都放宽不了非 ASCII**。

⇒ **query 串恒为纯 ASCII** ⇒ 该分支是**结构性死代码**（不是「目前碰巧没碰到」）。

**元教训**

> **「端到端测不到」要拆成两问，缺一不可：**
> ① 它**可观察**吗？（本条：**否** ⇒ 不立「需要对齐」的 `DRIFT` 条目）
> ② 它**有网**吗？（本条：**没有** ⇒ 必须补一张**直接单元测试**）
>
> ⇒ 只答 ① 就放过 ⇒ 就会留下一段**没人守、还自称等价**的死代码。

> **「以 Go 措辞写下的断言」是「待核实的断言」，不是证据。**
>
> ⇒ 本轮正是从一句 javadoc 的**行号写错**出发，顺藤摸到**代码也没实现 Go 的语义**。
> ⇒ **行号错**与**语义错**往往是**同一个疏漏的两面**：抄的时候抄的是「看起来像」的那一段，
> 而不是**真正在消费这个值**的那一段（AGENTS.md §3.3.1 的「声明点 ≠ 消费点」，
> 同样适用于行号**范围**）。
>
> ⇒ 修的时候也要**两件事一起做**：改代码 + 改 javadoc；
> 只改代码 ⇒ javadoc 继续说谎；只改 javadoc ⇒ 差异还在。

**下次怎么防**

1. **审计时把「源码里的 Go 行号引用」当独立待核验项抽样** ——
   本轮抽样 34 条才抓到 4 条错 ⇒ 抽样比例要够，且**「看起来合理」的行号最容易蒙混过关**。
2. **不可达分支也要写网，但要用「直接单元测试」** ——
   端到端恒绿等于没网；用**反射**调私有静态方法即可（本轮 `ArticleGoStrconvQuoteParityTest`），
   **不要**为了测试去改生产代码的可见性。
3. **「不可达」要用框架源码的**消费点**证明，不是「实测恰好没碰到」** ——
   「实测如此」只是**当前配置下**如此；**逐行读框架源码**才能证明「换配置也绕不过」。
   ⇒ 本域第八轮记的是「实测如此」（LOW，不修），
   第十二轮把它**升级**为「结构性必然」——**同一件事，两种证据强度**。
4. **变异验证仍然要做** —— 直接单元测试也可能是假绿：
   本轮 4 个变异（退整段 / 去掉软连字符例外 / 改下界 / 改上界）**全部转红** ⇒ 网是真的。

**证据 / 指针**

- 完整取证（含 Go 探针源码与逐字输出、Tomcat 逐行引用）：[`docs/DRIFT/article/api.md`](../../DRIFT/article/api.md) 第十二轮（FIX-18）。
- 对应条目：`DRIFT-ARTICLE-API-010`。
