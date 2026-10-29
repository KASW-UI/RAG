# 踩坑台账 · `service/users/user/user-api`

> **本文件只写教训。** 差异本体在 [`docs/DRIFT/users/user/api.md`](../../../DRIFT/users/user/api.md)。
> 每条写成「**现象 → 根因（框架机制）→ 为什么既有测试抓不到 → 下次怎么防**」。
>
> | | |
> |---|---|
> | 组件 | `service/users/user/user-api` |
> | 核对日期 | **2026-10-23** |
> | 条目 | P-1 ~ P-7（其中 P-1~P-5 是本轮新增，P-6/P-7 是本组件既有案例的沉淀） |

---

> **📌 2026-10-29 台账对齐轮 · 编号与状态口径**（本轮新增；**只增不删**，旧内容一律保留）
>
> - **新号段**：`PIT-USERS-25 … PIT-USERS-31`（旧号 `P-1 … P-7`）⇒ **逐条对照**写在每个条目首的「**旧号** ⇒ **新号**」行里。
> - **状态字段**：每个条目标题行尾以 `· **状态：X**` 标注（取值域：`仍生效` / `已闭环` / `已撤销` / `待核实`），
>   条目首的引用块给出 **核对日期（2026-10-29）** · **是否影响当前轮次**（填「否」时写明不适用的具体原因）· **证据**（`文件:行号` 或守门退出码）。
> - **判定口径**：`仍生效` ＝ 该坑描述的错误做法**今天仍可能发生**（触发条件仍存在）；
>   `已闭环` ＝ **本条记录的那个具体实例**已被消除且**有证据指针**；
>   `待核实` ＝ 判不了（**不许默认「仍生效」也不许默认「已闭环」**）。
> - ⚠️ **本轮只改台账**（`docs/**`），**未复跑**任何代码侧守门 ⇒ 「已闭环」类条目的证据指向**上一次实测**，本轮只做「该实例是否仍在」的存在性复核。

---

## PIT-USERS-25（2026-10-23 新增）· MockMvc 的 `getContentType()` **结构上**测不到真实响应头 · **状态：仍生效**

> **旧号**：`P-1` ⇒ **新号**：`PIT-USERS-25`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮若核 user-api 的响应头时直接适用
> **证据**：`MockHttpServletResponse.getContentType()` 取的是「我们传给 `setContentType` 的原串」⇒ 结构上测不到真实响应行；RAG 侧仍 `setContentType(MediaType.TEXT_PLAIN)`（不带 charset）而 Go 的 `http.Error` 写 `text/plain; charset=utf-8` ⇒ 差异真实存在、MockMvc 看不见


**现象**

`UserApiGoStyleBodyContentTypeTest.java:166-169` 有一条绿测试：

```java
assertEquals("text/plain", asPlainText.getResponse().getContentType(),
        "Go 的 http.Error 写的是 Content-Type: text/plain; charset=utf-8");
assertEquals("nosniff", asPlainText.getResponse().getHeader("X-Content-Type-Options"), ...);
```

**它的说明文字描述的是 Go 的字面量（带 `; charset=utf-8`），它断言的却是 RAG 侧不带 charset 的字面量，
而且这条测试永远绿。** RAG 的 `GlobalExceptionHandler.java:216` 写的是
`headers.setContentType(MediaType.TEXT_PLAIN)`，字面量就是 `text/plain`，
而 Go 的 `net/http.Error` 固定写 `text/plain; charset=utf-8` —— 差异真实存在，测试看不见。

**根因（框架机制）**

不是「测试写得不够细」，是**断言的对象选错了**。
`MockHttpServletResponse` 有两个字段：

- `private String contentType` —— `getContentType()` 原样返回我们传给 `setContentType(...)` 的那串；
- `headers` map —— 真正对外可见的 header 集合。

`MockHttpServletResponse.setContentType(String)`（spring-test **7.0.9**，`javap -c` 取证）的流程是：

```text
MediaType.parseMediaType(type)
  ├─ getCharset() != null          → setExplicitCharacterEncoding(charset)
  ├─ 兼容 application/json         → characterEncoding = UTF-8
  ├─ 兼容 application/*+json       → characterEncoding = UTF-8
  └─ 都不兼容（text/plain 就是这种） → characterEncoding 保持 null
      ↓
updateContentTypePropertyAndHeader()
  └─ characterEncodingSet == false ⇒ contentType 原样，不拼 charset
      ⇒ doAddHeaderValue("Content-Type", "text/plain")
```

⇒ MockMvc **不会替你补 charset**，它把你给的字面量原样存下来。
换句话说：**这条测试断言的是「我们代码里写了什么」，不是「客户端会收到什么」。**

**为什么既有测试抓不到**

`AGENTS.md` §3.3 有一句「**代码里写了 ≠ 客户端收到**」。
本条是它在**测试断言的取样点**上的具体化 ——
即使机制真的生效了（Header 确实实时 propagate 到 underlying 了），
只要断言取自 `MockHttpServletResponse` 的字段而不是**原始响应行**，就永远差一层。

**附带发现**：body 的**编码**也偏了。
`StringHttpMessageConverter`（spring-web **7.0.9**）的静态块是：

```text
static { DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; }
```

`text/plain` 无 charset 且非 JSON 兼容 ⇒ `getContentTypeCharset` 返回 `getDefaultCharset()` ⇒
**ISO-8859-1**。当前 `GoRequiredFieldException` 的文案全 ASCII，看不出来；
但 `handleUnreadableBody`（`GlobalExceptionHandler.java:163-167`）把
`HttpMessageNotReadableException.getMessage()` 原样写进 body，
那个消息**可能带非 ASCII**（畸形 JSON 里含中文即可触发）⇒ 那种输入下会乱码。

**下次怎么防**

1. **响应头一律用真容器验**。真 Tomcat + `curl -i` 抓原始响应行，是唯一能验响应头的手段。
   已登记为 [`DRIFT` 台账 UNRESOLVED-001](../../../DRIFT/users/user/api.md#unresolved-001--真-tomcat-下-400-响应的精确-content-type-与-body-字节--✅-第六轮已关2026-10-24)。
2. MockMvc 只用来断言**状态码 + body**；body 里若可能含非 ASCII，
   额外断言 `getContentType()` 或用 UTF-8 显式设置，避免编码问题被 body 断言掩盖。
3. 凡是**照抄 Go 的响应头**（`http.Error` 的四件事：
   `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff`
   + 状态码 + `fmt.Fprintln` 的尾换行），charset 也算「四件事」之一。
   当前 `GlobalExceptionHandler.goStylePlainTextBadRequest` 只做了三件。
4. 教训升级版：**断言的说明文字和断言的对象必须是同一个东西。**
   本条的说明文字写 Go 的字面量、断言写 RAG 的字面量 ——
   这种「注释描述 A、断言校验 B」的写法本身就是 `AGENTS.md` §3.3.2
   「以「Go: …」措辞出现的断言是高危项」的变体。

---

## PIT-USERS-26（2026-10-23 新增）· 「抽一个统一的解析器」会抹掉 Go 侧**故意不统一**的两套语义 · **状态：已闭环**

> **旧号**：`P-2` ⇒ **新号**：`PIT-USERS-26`
> **状态**：已闭环 · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本轮不改代码 ⇒ 触发面不落地
> **证据**：**实例已闭环**：`service/users/user/user-api/.../service/IdentityResolver.java:160` 提供 `claimedUIDAllowNonPositive`，`UserDeleteService.java:58` 与 `UserUpdateService.java:59` 改走它（`DRIFT-USERAPI-002` 已修）⇒ Go 的「两套 uid 语义」已被**逐端点复刻**，不再是统一收紧


**现象**

Go 侧 user-api 对「从 JWT 拿 uid」有**两套**实现，而且不一致不是笔误：

```go
// 第 1 组（get / avatar×3）：走 identity.ClaimedUID，有 uid <= 0 判定
identity/resolve.go:75    if err != nil || uid <= 0 { return 0, ErrInvalidClaim }

// 第 2 组（delete / update）：Logic 内联，只判类型和 Int64() 能不能过
deleteuserlogic.go:37-47  userId, ok := l.ctx.Value("userId").(json.Number)
                          uid, err := userId.Int64()   // 没有 uid <= 0
```

RAG 把它们**统一**成了 `IdentityResolver.claimedUID`（有 `uid <= 0` 判定），
`UserDeleteService.java:50` 与 `UserUpdateService.java:51` 都走它。

结果：对一个**签名合法、`userId:0`** 的 token 打 `/user/delete`，
Go 会把 `uid=0` 透传给 user-rpc，RAG 直接 1006 且**一次 rpc 都不发**。

**根因（这不是框架机制，是重构惯性）**

`AGENTS.md` §1.1 记的第 9 条历史回归叫「uid 解析失败静默转 `0`」。
修它的动作天然会长成「抽一个 `claimedUID`，所有端点统一用它」——
而这恰恰是 Go 自己没有做的事。
**Go 的两套语义是它的既有行为（哪怕看起来不一致），真源冻结 ⇒ 默认判定 RAG 错。**

**为什么既有测试抓不到**

Go 侧唯一的 uid 测试是 `internal/logic/user/getuserlogic_test.go:51-52`：

```go
{"zero", json.Number("0"), nil, nil, errmsg.ErrorTokenRuntime, 0},
{"client-value-not-jwt", int64(9123), nil, nil, errmsg.ErrorTokenRuntime, 0},
```

**只覆盖 `get`**。delete / update 从来没被测过 ⇒ 两套语义在 Go 侧也是**无网**状态，
所以 RAG 单方面「统一」时，没有任何一侧的测试会红。

RAG 侧同样无网：`IdentityResolverTest` 测的是 `claimedUID` 本身
（它当然对 `uid<=0` 抛异常），**没有任何一条测试说「delete 端点不应该用 claimedUID」**。

**下次怎么防**

1. 看到 Go 侧同一件事有**两个实现**，先问「这是笔误还是行为？」，
   再问「区分它们的那一条测试在哪」。找不到测试 ⇒ 是行为 ⇒ 必须逐端点复刻。
2. **禁止在跨端点的公共 helper 上做「更合理」的收紧。**
   `AGENTS.md` §3.2 要求 idiomatization 必须留 Go 出处 + 语义等价论证；
   `claimedUID` 的出处在 §3.2 的例子里是有的，但论证里**只覆盖了它覆盖的那一组端点**，
   覆盖面没写出来 ⇒ 论证形式完整、实质不完整。
3. 守恒式断言：对「同一个语义维度在不同端点上的不同取值」，
   必须**逐端点各写一条**，而不是写一条 helper 级测试。
   参见 `AGENTS.md` §3.3.3「凡是脚本批量改 N 处，必须配一条覆盖全部 N 处的守恒断言」
   —— 这条的精神同样适用于「把 N 套实现合并成 1 套」。

---

## PIT-USERS-27（2026-10-23 新增）· 单条「已修」的缺陷，要按**响应路径逐条**重新验 —— advice 只是其中一层 · **状态：仍生效**

> **旧号**：`P-3` ⇒ **新号**：`PIT-USERS-27`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核任何「已修」类结论时直接适用
> **证据**：「测试层级比缺陷层级浅一格」的形态不变（真 MVC 管线 + 真 filter + 真外部依赖仍是唯一充分判据）⇒ `standaloneSetup` 只能测 advice 自身逻辑


**现象**

`AGENTS.md` §3.3.3 第四类记着 `@ResponseBodyAdvice` 会把 `@ExceptionHandler` 返回的
`ResponseEntity` **二次包裹**，导致顶层 `code` 恒为 200（§6.1 第 1 条）。
本轮复核：该缺陷确实已修，`GlobalResponseAdvice.java:57-59` 放行 `Response`、`null` 与非 JSON。

但复核过程中同时发现：`ResponseEnvelopeAdviceTest` 的两条用例是
`standaloneSetup` 级别的，**没有挂 `HttpMiddleware` filter**；
而 §6.1 第 10' 条记的 `DRIFT-FRAMEWORK-001` 恰恰是**挂上 filter 才暴露**的那一层。
也就是说：「advice 修好了」和「401 在真管线下真的是 401」是**两件事**，
它们被同一张绿测试表看起来像一件事。

**根因（框架机制，两层叠加）**

```text
第 1 层：@RestControllerAdvice extends ResponseBodyAdvice
        ⇒ HttpEntityMethodProcessor（Spring 7）**继承** writeWithMessageConverters
          并查询 advice chain ⇒ ResponseEntity 的 body 也走 advice

第 2 层：Servlet Filter 包在 DispatcherServlet **外**
        ⇒ ObservabilityResponseWriter.setStatus 只缓存到 this.status，
          等 flushTo 再写底层，而此时底层已 committed ⇒ 401 被吞成 200
```

**两层同时错的时候，单独修任何一层都能全绿。** 这正是 N-1 的「二次坍塌」。

**为什么既有测试抓不到**

测试层级选得比缺陷层级浅一格。
`standaloneSetup` 不经过 Filter 链；`MockMvcBuilders.webAppContextSetup` 才会挂 Filter。
**「测试通过了」和「这个缺陷层被覆盖了」不是同一件事。**

**下次怎么防**

1. 修 transport / advice 这类**贯穿多层**的缺陷时，每层都要有**独立触发**的用例，
   且用例必须**走到最深的那一层**（真 MVC 管线 + 真 filter + 真外部依赖）。
   本组件现在的 `UserApiMvcPipelineTest`（`@SpringBootTest` + `@AutoConfigureMockMvc`
   + 真 Testcontainers Redis）就是这条要求的产物。
2. **不要因为某个缺陷被登记为「已修」就停止复核** —— 复核要问的是
   「它在真管线的每一条响应路径上还成立吗」，而不是「修复代码还在不在」。
3. 呼应 N-1 的教训：**层次由类型承载，不靠业务码推断**。
   `AuthenticationException extends BusinessException` 让 Spring 自动选最具体的 handler，
   这比在 `decideStatus` 里按 1006/1004/1009 分流健壮得多 ——
   但它**只在类型层次设计正确时才有效**，而类型层次的正确性同样需要真管线验证。

---

## PIT-USERS-28（2026-10-23 新增）· `HandlerInterceptor` 与 `rest.Middleware` 的**相对位置**不是链序直觉 · **状态：仍生效**

> **旧号**：`P-4` ⇒ **新号**：`PIT-USERS-28`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核中间件链序时直接适用
> **证据**：`service/common/.../spring/HttpMiddlewareFilter.java:41` 是 Servlet Filter（容器外）、`service/users/user/user-api/.../interceptor/JwtAuthInterceptor.java:87` 是 HandlerInterceptor（容器内）⇒ 分属两层容器，`@Order` 数值不可跨层比较


**现象**

「验签先于黑名单」这条，两侧都做对了，但**是靠不同的机制**做对的：

```text
Go：rest/server.go:222-233 WithMiddleware 把黑名单包进 route.Handler
    rest/engine.go:101-113  bindRoute: chn.ThenFunc(route.Handler)
    ⇒ 黑名单是 ThenFunc 的**参数**，比 chain 里任何元素都内层 ⇒ 必然在 Authorize 之后

RAG：JwtAuthInterceptor.preHandle 里手写「先 JWT → 再黑名单」（:132-182）
```

而 **observability 的位置两侧是反的**：

```text
Go：server.Use(...) 的中间件在 engine.go:110-112 追加，排在 appendAuthHandler **之后**
    ⇒ 索引更小 ⇒ 更外层 ⇒ 先执行
    ⇒ **Authorize 在 observability 之内** ⇒ JWT 401 不计指标、3s 预算不含验签

RAG：HttpMiddlewareFilter 是 Servlet Filter（:36-37），Servlet 的所有 Filter
    都在 DispatcherServlet 外，HandlerInterceptor 在内
    ⇒ **observability 在鉴权之外** ⇒ JWT 401 计入指标、3s 预算含验签
```

**根因（框架机制）**

「Servlet Filter vs HandlerInterceptor」不是一条可以用 `@Order` 数值比较的链 ——
它们分属**两层不同的容器**（容器外 / 容器内）。
`@Order` 只在 Filter 之间、或 Interceptor 之间排序。

这一点在本仓库里已经被另一个组件踩过一次并写进了
`GoStyleMaxBytesFilter.java:62-79` 的注释：变异测试发现把 `@Order` 改成
`LOWEST_PRECEDENCE` 后「413 压过 401」的用例**依然全绿** ——
因为它是 Filter，怎么排都在 Interceptor 外面。
⚠️ **user-api 的 `HttpMiddlewareFilter` 属于同一类风险点，但没人给它写过变异测试。**

**为什么容易错**

直觉会告诉你「`@Order(HIGHEST_PRECEDENCE + 5)` ⇒ 最先执行 ⇒ 在拦截器前面」——
结论对了，**理由是错的**。理由错了，就会在换一种实现（比如把 Filter 改成 Interceptor、
或者给某个中间件换层级）时做出错误推断。

**下次怎么防**

1. 断言链序时，用**变异**而不是用注释：让某一层失效（摘掉 `@Component` / 改成 `@Order` 链尾），
   看断言是否真的变红。注释里的链序说明不算证据。
2. 每次引入新的中间件（Filter / Interceptor / `HandlerInterceptor` / `OncePerRequestFilter`），
   都要在设计笔记里写清三件事：**层级（容器外/内）、@Order、与既有每一层的先后**。
3. 尤其注意：**改层级会同时改变超时预算的作用域和可观测性的覆盖面**，
   这两件事都不会报错，只会安静地变。

---

## PIT-USERS-29（2026-10-23 新增）· 「Go 少一个能力」比「Go 多一个能力」更难被发现 · **状态：仍生效**

> **旧号**：`P-5` ⇒ **新号**：`PIT-USERS-29`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮做任何一侧对照时直接适用
> **证据**：对照必须**双向**（「RAG 有、Go 无」同样是凭空新增的行为）；Go 侧 `rest/config.go` 十项 native middleware 默认全 `default=true` 的事实不变


**现象**

`AGENTS.md` §3.1 要求对照 Go 的 **struct tag 隐藏默认值**，§3.3.1 要求「读到消费该 tag 的控制流」。
这两条都指向 **「Go 有、RAG 也要有」** 的方向。

但本轮撞上了反方向的一条：**Go 的 `httpx.Parse` 做的三件事，RAG 只做了一件。**

```go
rest/httpx/requests.go:50-69
Parse = ParsePath → ParseForm → ParseHeaders → ParseJsonBody
```

`ParseForm` 与 `ParseHeaders` 用的是 `"form"` / `"header"` 两个 tag key，
而 user-api 的 9 个 Req struct **只有 `json` tag**
⇒ `core/mapping/utils.go:271-275 parseKeyAndOptions` 在 tag 缺失时**回落到 Go 字段名**
⇒ `POST /login?Username=a&Password=b`（空 body）在 Go 侧**能登录成功**。
RAG 的 9 个 `@RequestBody` 完全不读 query 与 header。

反方向的例子同一个组件里就有两个：

| | Go | RAG |
|---|---|---|
| `avatar/history` 的 body | handler **不调** `Parse`（`getavatarhistoryhandler.go:17-25`） | `@RequestBody` 会解析，畸形 JSON ⇒ 400 |
| 过载保护 | `rest/config.go:12-23` 十项 native middleware 全 `default=true`（Breaker / Shedding / MaxConns） | 无任何对应物 |

**根因**

对照动作天然是**枚举式**的：把 Go 的能力列出来，逐条查 RAG 有没有。
「RAG 缺什么」能被枚举；**「Go 其实没在做这件事」不能** ——
因为它在 Go 侧的形态是「某处少写了一句」，而不是「某处多了一个功能」。

`avatar/history` 尤其阴险：9 个 handler 里 8 个都 Parse，
**用「其余 8 个都对」推第 9 个也对** —— 这是 §3.1 明令禁止的
「读两遍 Go 取并集」的同一类错误（此处是「取交集的反面」）。

**下次怎么防**

1. 对照清单要**双向**：既列「Go 有、RAG 无」，也列「**RAG 有、Go 无**」。
   后者往往长得像 idiomatization，实际上是凭空新增的行为。
   本轮的 `avatar/history @RequestBody` 就是「RAG 有、Go 无」。
2. 对每个端点问一句：**Go 侧这个 handler 到底读了什么输入？**
   逐个读 handler 全文，不要从「同域其它 handler 都 Parse」推。
3. 「Go 的框架默认能力」也要逐条查 —— `rest/config.go` 的 `MiddlewaresConf`
   十项全是 `default=true`，yaml 里一个字都没写，
   但它们**全部生效**。这与 §3.3.1 说的 struct tag 隐藏默认值是同一个陷阱的镜像：
   **struct tag 藏的是「会做什么」，框架默认藏的是「会做什么而你看不见」。**

---

## PIT-USERS-30（既有案例沉淀）· `N-1` 的四次坍塌：修一处不够，每修一次就多一层要验 · **状态：仍生效**

> **旧号**：`P-6` ⇒ **新号**：`PIT-USERS-30`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮改响应面（状态码 / 头 / body）时直接适用
> **证据**：N-1 的四层坍塌仍是本仓最贵的样本之一 ⇒ 「响应不只是 body」+「每层修复都要单独有触发用例」两条纪律不变


本组件的 `N-1`（鉴权失败的 401）在本轮之前的演化路径，值得完整留一条，因为它示范了
**「一个缺陷有多层、每层修完都有绿测试、但每层单修都可能被 CI 骗过」**：

```text
第 0 层  业务码 1006 被降级成 HTTP 200
        └─ 修法：decideStatus 里按业务码分流
第 1 层  支撑第 0 层修法的注释引用了「不存在的 Servlet Filter」
        └─ 根因：鉴权是 HandlerInterceptor，**必然**经过 advice/handler
        └─ 修法：引入 AuthenticationException，**层次由类型承载**（本组件 `JwtAuthInterceptor` 现在抛它）
第 2 层  transport 层：ObservabilityResponseWriter.setStatus 不 propagate
        └─ 表现：@ExceptionHandler 返回的 ResponseEntity.status(...) 在运行期全退化成 200
        └─ 为什么没被发现：`AuthenticationHttpStatusTest` 用 standaloneSetup，**不挂 filter**
第 3 层  header 层：setHeader / addHeader / setContentType 同样只缓存不 propagate
        └─ 表现（2026-10-22 E-1 实测，真 Tomcat）：400 text/plain 的 **Content-Type 完全丢失**，
          且响应变成 Transfer-Encoding: chunked + Connection: close
        └─ 为什么没被发现：difftest **只比 body 不比响应头**；body 恰好是对的
```

**共同的机制性根因（两条）**

1. **「加了 filter / interceptor 就等于进了那条链」是错的** ——
   要问「它在哪一层容器里、在链的哪个位置、写操作是同步还是延迟」。
2. **响应不只是 body**。状态码、Content-Type、`X-Content-Type-Options`、
   `Transfer-Encoding`、`Connection` 都是响应契约的一部分。
   只比 body 的对拍工具，会让「body 恰好是对的」把 header 的丢失整条盖过去。

**下次怎么防**

1. 任何 `@ResponseBodyAdvice` / `@ExceptionHandler` / Filter 的改动，
   验收用例必须走**真 MVC 管线 + 真 filter + 真外部依赖**（Redis / PG / RPC），
   `standaloneSetup` 只能用来测「某个 advice 自身」的逻辑，不能用来测「响应最终长什么样」。
2. 对拍工具的输入要包含**完整响应**（状态码 + 头 + body），不是只 body。
3. 每一层修复都要**单独**有触发用例 —— 两层一起错时，只修一层的那次全绿是必然的。

---

## PIT-USERS-31（既有案例沉淀）· 灰度验证：绿测试 + 精心注释 + 「引用的机制根本不存在」 · **状态：仍生效**

> **旧号**：`P-7` ⇒ **新号**：`PIT-USERS-31`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮写/读 javadoc 时直接适用
> **证据**：「注释引用的机制在本工程不存在」这一形态仍在（注释手写、无任何机制保证与代码同步）⇒ 注释里的「本工程 X 层会……」仍属高危断言


这条是 `AGENTS.md` §3.2「无出处 idiomatization」在本组件的具体形态，
但比它更隐蔽：**注释有出处，出处指向的机制不存在。**

原注释写的是：

> 「401 只由鉴权层的 Servlet Filter 写出，不经过本处理器」

**本工程根本没有这样的 Servlet Filter。** 鉴权是 `HandlerInterceptor`，它抛异常，
必然经过 `GlobalExceptionHandler`。注释引用了一个真实存在的类名
（`Servlet Filter` 是真的），但**它在这个工程里不存在** ——
所以 review 时无从判断「这是必要表达差异还是悄悄改了行为」。

**下次怎么防**

1. 注释里每出现一个「某某层会做某事」的断言，都要能指出**那个类在本仓库的哪个文件**。
   指不出来 ⇒ 它是推测，不是出处。
2. `AGENTS.md` §3.3.2 已经把「以「Go: …」措辞出现的断言」列为高危项。
   本条是它的变体：**「本工程 X 层会……」**同样是高危项 ——
   而且更隐蔽，因为它读起来像在解释本地实现，不需要读者去查 Go。
3. 灰度验证（改一半看反应）在这类缺陷上成本极低：
   把那个「不存在的机制」真的接上去，或者把现有的 HandlerInterceptor 临时改成 Filter，
   看测试是否还绿。绿了就说明测试没覆盖那一层。

---

## 本轮的方法论教训（一句话版）

| 现象 | 本质 | 对应纪律 |
|---|---|---|
| MockMvc 测不到真实响应头 | 断言的取样点比缺陷层级浅一格 | §3.3「代码里写了 ≠ 客户端收到」；P-1 |
| 统一 `claimedUID` 抹掉 Go 的两套语义 | 重构惯性覆盖了「Go 本来就不统一」的事实 | §3.1「禁止拿现有代码当基准」；P-2 |
| advice 修了但 transport 层还没修 | 每修一层就多一层要验，单修必被 CI 骗 | §3.3.3 框架机制必须验证实际生效；P-3 |
| Filter vs Interceptor 的先后 | 层级 ≠ `@Order` 数值 | P-4 |
| Go 少做的一件事没被发现 | 枚举式对照只能发现「Go 有、RAG 无」 | §3.1 双向对照；P-5 |