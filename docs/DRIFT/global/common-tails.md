<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 · 本轮只做「复核 + 定位」，未改动 common 代码 -->

# global · common 尾巴台账（T-1 ~ T-10）

> **归属**：`global` 收口域的「common-tails」议题档（§5.3b）。
> **对象**：**别的域**的漂移，但**修法落在** `service/common` 的共享类里 ⇒ 不是 `common` 域自身的语义漂移
> （边界见 [`_README.md`](../_README.md) §二）。
> **基线**：`align/global` @ `58e8314`（`do-my-own-gh`）。
>
> ⚠️ **本轮（2026-10-25）只做「复核 + 定位 + 事实认定」**：把 10 条里标「⬜ 待复核」的 3 条
> （T-1 / T-2 / T-5 / T-9）落到**逐行可核的 Go 出处 + RAG 出处**上。
> ⇒ **除 T-10 外，本轮未改任何 common 代码。**

---

## 〇、🔴 本轮最重要的方法论修正：**Go 出处在框架源码里，不在项目树里**

> **台账前几版把 Go 出处写成「Go 侧 `service/common/...`」，实测全部落空。**

| 台账原写的「Go 出处」 | 实测 |
|---|---|
| `Go path.Join(cc.Target(), method)`（T-9） | ❌ 项目树里**没有** `breaker` 相关代码 |
| `Go MaxConnsHandler`（T-2 / T-5） | ❌ 项目树里**没有** `MaxConnsHandler` |
| `Go withJsonBody`（T-1） | ❌ 项目树里**没有** `withJsonBody` |

⇒ **真源分成两层**（AGENTS.md §3.1 的「对照基准」必须落到第二层）：

| 层 | 位置 | 内容 |
|---|---|---|
| **L1 项目层** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | 本项目的业务代码（`service/<域>/...`）⇒ `service/common` 只有 `logger` / `response` / `snowflake` / `observability` / `communityfact` 五个包 |
| **L2 框架层** | `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` | **请求保护层 / RPC 中间件 / HTTP body 解析的真正出处** ⇒ 绝大多数「请求保护层四层」的 Go 语义在这里 |

⇒ **`service/common` 在 Go 侧根本没有 `http/` 与 `protection/` 子包**（`git log --all --diff-filter=A` 实证：从未有）。
⇒ RAG 的 `service/common/http/` + `service/common/protection/` **整体对应的是 go-zero 的对应件**，不是 Go 项目的 `service/common`。

⚠️ **对后续所有域的含义**：查「请求保护层」类漂移的 Go 出处时，**只在项目树里 grep 一定会落空**。
⇒ 已登记为踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md)。

---

## 一、逐条复核结论

### T-1 ✅ `Content-Type` 大小写敏感判定 —— **已修**（残余 ① 见下）

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/rest/httpx/requests.go:153-155`<br>`func withJsonBody(r *http.Request) bool { return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson) }`<br>常量：`rest/internal/header/headers.go:5` ⇒ `ApplicationJson = "application/json"` |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java:115-117`（`read()` 取 Content-Type）<br>判定在 `:98-101`（`goReadsBody`）⇒ `contentType.contains(GO_JSON_CONTENT_TYPE_MARK)`，`GO_JSON_CONTENT_TYPE_MARK = "application/json"`（`:77`） |
| **差异根因** | Go 读的是**原始头值**（`r.Header.Get` **不做大小写归一**）⇒ `strings.Contains` **大小写敏感**；<br>RAG 读的是 `getContentType().toString()` ⇒ **Spring 的 `MediaType.toString()` 已把 type/subtype 小写化** ⇒ `contains` **大小写不敏感** |
| **可观察差异** | 请求头 `Content-Type: Application/JSON`（大写 A/J）⇒<br>**Go：不读 body**（走 `mapping.UnmarshalJsonMap(nil, v)`，body 被忽略）<br>**RAG（修前）：读 body**（进 Jackson 反序列化） |
| **状态** | ✅ **2026-10-25 已修**（`align/global` 收口提交） |

⚠️ **不是「RAG 更严」**：两侧行为**方向相反**（Go 忽略 body / RAG 解析 body）⇒ 不是签字偏离能覆盖的类型，是**行为漂移**。

#### ✅ 修法与守门（2026-10-25）

**改一行**：`GoStyleJsonBodyConverter#read` 里把
`inputMessage.getHeaders().getContentType().toString()`
换成 `inputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)`（**原始头值**）。

⇒ **落点全在 `service/common`**（`align/global` 的单写权范围）⇒ **不需要改别的域的代码**。
⚠️ 已核对 `like-api` 的 `LikeApiGoStyleBodyContentTypeTest#goReadsBodyBoundaries` 的 7 个断言
**全是已小写值** ⇒ 修复**不改变任何现有期望值**（实测：该模块测试全绿）。

**守门**：`service/common/src/test/java/com/rag/common/http/GoStyleJsonBodyConverterContentTypeCaseTest.java`（6 条）
⇒ 用**真 `HttpHeaders` + 真 converter**（刻意不用 Mockito —— 被测的正是「`HttpHeaders` 到底返回什么」）
⇒ 覆盖：`Application/JSON` / `APPLICATION/JSON` / `application/JSON` ⇒ **不读 body**；
`application/json` / 带 charset ⇒ **读 body**；Spring 能容忍的头内畸形 ⇒ **不抛**。
**🧪 变异测试**：把修复改回 `MediaType.toString()` ⇒ **转红**
（`expected: <null> but was: <{target_type=article, target_ids=[1]}>`）⇒ 守门网非空转。

#### ✅ T-1 残余 ① —— **2026-10-25 已修**（本轮第三轮 A1）

**只修好了「判定」这一层；解析那一层仍会碰 MediaType 解析。**

| | 事实 |
|---|---|
| **Go** | `strings.Contains(raw, "application/json")` ⇒ **纯字面**，**不做 MIME 解析** ⇒ 畸形头**不会抛**，命中就照常读 body |
| **RAG（第二轮修后）** | 判定用原始值 ✔；但命中后 `super.read(...)` 内部仍会调 `getHeaders().getContentType()` 做 MediaType 解析 ⇒ **`application/json, application/xml` 仍抛 `InvalidMediaTypeException`**（实测，`Invalid token character ','`）⇒ 请求 500 |
| **状态** | ✅ **已修**（2026-10-25 第三轮） |

**本轮修法（`GoStyleJsonBodyConverter#read`）**：命中判据后，把传给 `super.read(...)` 的
`HttpInputMessage` **包一层**（新增私有静态类 `SanitizedJsonContentTypeInputMessage`），
让 Spring 看到一个**已净化的** `Content-Type: application/json`（**不带 charset**）：

| 场景 | 改前 | 改后 | Go |
|---|---|---|---|
| `application/json, application/xml` | 🔴 抛 `InvalidMediaTypeException` | ✅ 正常读 body | ✅ 不抛 |
| `application/json; charset=gbk` | 🔴 按 GBK 解码 | ✅ **回归 UTF-8** | ✅ `encoding/json` 只认 UTF-8 |
| `Application/JSON` | ✅ 不读 body（大小写敏感） | ✅ 同左 | ✅ 同左 |

⇒ **顺带修掉了「charset=gbk 时 RAG 按 GBK 解码、Go 恒按 UTF-8」这条偏差**
（Go 的 `encoding/json` 只认 UTF-8）。

**守门**：`GoStyleJsonBodyConverterContentTypeCaseTest`（**8 条**，真 `HttpHeaders` + 真 converter，
**刻意不用 Mockito**）⇒ 变异双向证明：去掉净化层 ⇒ **逗号头抛异常** + **GBK 被成功解码** ⇒ 均转红。

⚠️ **残余（不可修，已登记）**：Jackson 仍会**自动探测 UTF-16 BOM** ⇒ 带 BOM 的请求体可能被按 UTF-16 解码，
而 Go 的 `encoding/json` 只接受 UTF-8（且会因 BOM 直接报错）。
⇒ 归「框架限制」，**不修**（改动面落在 Jackson 内部 ⇒ 无法在 common 层精准关掉而不伤其它路径）。

⚠️ **修法的连带面**：`goReadsBody` 是 **public**（`:94` 的 javadoc 明写：它是「Go 何时读 body」这条规则的**唯一真源**，
且业务模块的测试直接断言它 ⇒ 复制品必然与实现漂移）⇒ 修它必须**同时**修所有调用方的测试期望值。
✅ **实测**：唯一跨域调用方是 `like-api` 的 `LikeApiGoStyleBodyContentTypeTest`（7 条断言，**全为已小写值**）
⇒ **不需改任何别的域的代码**。

🔴 **但发现 3 份「生产复制品」** ⇒ 见 [`cross-domain.md` X-9](cross-domain.md)。

---

### T-2 / T-5 ✅ `MaxConns` 闸门作用域 —— **已签字为有意偏离**（移出本档）

> ⚠️ **本两条已不在 DRIFT 档**（§5.3b 硬边界 2：「不要在 DRIFT 里记『决定不对齐』的偏离」）
> ⇒ 决策记录在 [`docs/Intentional_Deviations/global/common-tails.md` §SD-2026-10-25-01`](../../../Intentional_Deviations/global/common-tails.md)
> ⇒ 下面是**历史记录**，保留以便追溯「它曾是第三态、怎么归位的」。

**（以下为签字前的历史）**

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12-36`<br>`latch := syncx.NewLimit(n)` 在 **`MaxConnsHandler(n)` 返回的闭包内** ⇒ **每调用一次新建一个 latch** |
| **Go 调用点** | `go-zero@v1.10.2/rest/engine.go:132-146` `buildChainWithNativeMiddlewares(fr, route, metrics)` ⇒ `chn = chn.Append(handler.MaxConnsHandler(ng.conf.MaxConns))`<br>该函数**按路由**调用：`engine.go:102-116` `bindRoute` ⇒ `chn = ng.buildChainWithNativeMiddlewares(fr, route, metrics)` |
| **Go 前提** | `engine.go:103` `chn := ng.chain`；**`ng.chain` 全仓只在一处被赋值**：`rest/server.go:141-145` `WithChain(chn)` RunOption<br>⇒ 实测**本项目 0 处使用 `rest.WithChain`**，10 个 api 全用 `rest.MustNewServer(c.RestConf)`（`article` 多一个 `rest.WithCors()`，不设 `ng.chain`）<br>⇒ **`ng.chain` 恒为 nil** ⇒ native 中间件**必然**按路由构建 |
| **Go 结论** | **每条路由一个 latch ⇒ per-route 10000**（10 个 api × 13 端点 ⇒ 理论上限 13×10000） |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java` ⇒ 构造函数里 `new Semaphore(maxConns)` ⇒ **全进程一个闸门** |
| **差异** | Go = per-route 10000；RAG = 全局 10000 ⇒ **一个端点打满就能把其它端点一起拒掉** |
| **状态** | 🔴 未修 |

#### 🔴 附带发现：**档位错置**（同一件事被记成两个互斥的结论）

- `GoStyleMaxConnsFilter` 的 javadoc 自称：「**此处为有意的表达层收敛，已在此显式留痕**，不是等价性的隐藏缺口」
- 而 `global/_README.md` §三 把 T-2 / T-5 列为 🔴 **仍未修**
- 且全仓 `docs/Intentional_Deviations/` **无对应签字**（`common/_signed-deviations.md` 与各域 `api.md` 里的 MaxConns 提及均**不是**这一条）

⇒ 按 §5.3b 判据（「这条差异**要不要**『修回去』？要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations」），
**它目前既不是 DRIFT 也不是签字偏离 —— 是个无归属的第三态。**
⇒ **已归位**到待签字候选 `SD-2026-10-25-CAND-11` ⇒ **2026-10-25 用户已签字**
⇒ 候选号转正为 **`SD-2026-10-25-01`**（状态：已签字）⇒ 详见
[`docs/Intentional_Deviations/global/common-tails.md`](../../../Intentional_Deviations/global/common-tails.md)。
⇒ **签字决定 = 保留 RAG 的全局闸门，不改成 per-route**。
⇒ 配套落地：`GoStyleMaxConnsFilter` 的 javadoc 已从「有意的表达层收敛」自述
**改成指向该签字档的指针**（并加一句「不要因为 Go 是 per-route 就顺手改回」）。

---

### T-3 ✅ 已修 —— **实测确认，无需 global 再动**

`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:431`
⇒ `resp.setHeader("X-Content-Type-Options", "nosniff")` **在位**（`writeTimeoutResponse` 内）。
⇒ 关闭。（Go 出处：`net/http.Error` 自 Go 1.11 起写该头。）

---

### T-4 🟡 **观测面已修**（499 可见）· **资源面残留为框架限制** —— 2026-10-25

> **对应**：`hot` 域 `D25` / `DRIFT-HOTAPI-025`（**代码在 `service/common` ⇒ 跨域 ⇒ global 修**）。
> **原结论**：「客户端断连后 RAG **等 2s deadline**、**断连后继续发下游调用**、**无 499 记录**」
> ⇒ 三条里**两条已修、一条不可修**。

#### ① 根因（Go 与 RAG 的 499 判据不同源）

| | Go（真源） | RAG（改前） |
|---|---|---|
| 499 的判据 | **`ctx` 被取消** —— `errors.Is(ctx.Err(), context.Canceled)`<br>（`service/common/observability/http.go:84-86`） | **worker 线程里抛了 IOException**（写响应失败）<br>（`HttpMiddleware.java` 的 `panicHolder` 分支） |
| 断连 ⇒ ctx 取消？ | ✅ **`net/http` 在客户端断开时取消 `r.Context()`** ⇒ 断连后 **1~3ms** 内定型 | ❌ **Servlet 容器不向应用暴露「客户端已断连」信号**（见下） |
| worker 会写 socket 吗？ | —— | ❌ **不会**：`ObservabilityResponseWriter` 把 body 写进**内存缓冲**，真正写 socket 的是 filter 线程的 `rec.flushTo(httpResp)`<br>⇒ **写失败抛在 filter 上，不在 worker 的 panic 通道里** ⇒ `panicHolder[0]` 永远 null ⇒ **永远进不了 499 分支** |
| 记录里留的态码 | **499** | **200**（`markHttpResult` 已在写之前跑完） |

⇒ 这不是配置问题，是**结构性差异**：Go 的 499 由 **ctx** 驱动（与写在哪一层无关），
RAG 的 499 由 **worker 内的写失败** 驱动（而本组件的写发生在 worker 之外）。

#### ② 本轮修法：499 判据改成「**filter 写 socket 失败**」

> 这正是 `DRIFT-HOTAPI-025` 处置一节给出的两条建议之一（「把 499 的判定从『worker 内的写失败』改成
> **『filter 写 socket 失败（`flushTo` 抛 `IOException`）』**」）。

`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`
⇒ 新增 `flushCaptured(...)`（替换两处 `rec.flushTo(httpResp)` 的裸调用）：

1. **先定 `Content-Length`**（保形，见 §④）⇒ 再 `rec.flushTo(httpResp)` ⇒ **再显式 `flush()`**；
2. 写失败若是「客户端断连」（`EOFException` / `ClientAbortException`，判定沿用既有 `isClientCancelledException`）
   ⇒ **记 499**：`markTimeout(ctx, …, httpAttrs(serviceName, httpReq, 499))`
   （对齐 Go 的 `MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, 499)...)`）
   ⇒ 覆盖掉先前的 200（`markTimeout` 的 `setAllAttributes` 覆盖同名键）；
3. 并打一条 **`[HTTP] 499 - <method> <uri> - <ip> - duration <n>ms`** 日志
   —— Go 侧 499 之所以「可见」是因为 **go-zero 的 access log** 会打 `[HTTP] 499 - …`；
   RAG **没有 access log** ⇒ 只写 span 的话，**没接 trace 后端时事件依然不可见**（实测：本仓库 OTEL exporter 默认关闭）
   ⇒ 必须补一条日志，否则「断连不可见」只修了一半；
4. **非断连类 IOException 照旧上抛**（保持原有行为，不吞真实故障）。

另：`writeTimeoutResponse(...)` 的三处调用改走 `writeTimeoutResponseQuietly(...)`
⇒ 客户端已走时写 503/499 失败**不再把异常栈抛给容器**
（对齐 Go：`http.Error` 内部的 `fmt.Fprintln` **忽略写错误**）。

#### ③ 🔴 残留（**框架限制，不可修**）—— 在飞 gRPC 不会被提前取消

| | Go 真源 | RAG（修后） |
|---|---|---|
| 断连后在飞 gRPC | **断连后 1~3ms 内全部取消**（ctx 传播） | ❌ **不取消** ⇒ 仍要等到自己的 deadline 才结束 |
| 断连后是否继续发下游调用 | **否** | **是**（同改前） |

**为什么不可修（真 Tomcat 11.0.24 实测 + Tomcat 源码取证）**：

1. **Servlet 容器不向应用暴露「客户端已断连」信号**
   ⇒ 实测：Servlet 里 `request.startAsync()` + 注册 `AsyncListener`，
   客户端裸 socket `SO_LINGER{1,0}` 强制 **RST** ⇒ **`onError` / `onTimeout` / `onComplete` 一个都不触发**；
2. ⇒ Tomcat 源码同证：`org/apache/coyote/AbstractProcessorLight.java:62`
   对 `SocketEvent.DISCONNECT` 明写 **"Do nothing here, just wait for it to get recycled"**；
   `org/apache/catalina/connector/CoyoteAdapter.java:140-160` 的 `setErrorState(..., true)`
   只在**「非容器线程上的 I/O 错误」**时触发（即写失败，不是对端消失）；
3. ⇒ Servlet 层也**拿不到 socket**：`org.apache.catalina.connector.Request` 里**没有任何 socket 访问器**
   ⇒ 无法自己轮询「对端还在不在」。

⇒ **本条 ⇒ 已归位为「框架限制」待签字候选 `SD-2026-10-25-CAND-12`**
⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../../Intentional_Deviations/global/common-tails.md)。
⚠️ **签字权在用户**：本轮**未**自行批准。

> ⚠️ **口径提醒**：本条**不是**「RAG 少写了 499」—— 499 **已经产出**了；
> 残留的是「**取消的传播**」这一层（Go 有 ctx 传播，RAG 没有等价物）。

#### ④ 保形：提前 flush 会把 `Content-Length` 退化成 `chunked`（**已处理**）

⚠️ 这是本轮**实测踩到并修掉**的一个坑（否则会引入**新的 wire 差异**）：

- 499 的观测依赖「**真的写到 socket**」⇒ 必须显式 `flush()`；
- 但 Tomcat 的 `OutputBuffer` 默认缓冲 **8192 字节** ⇒ 小 body 写进去**只进了缓冲区**，socket 一个字节都没发
  ⇒ **不 flush 就观测不到断连**（实测：去掉 flush ⇒ 记录仍是 200）；
- 而 **flush ⇒ 响应提前提交** ⇒ Tomcat 再也没机会在收尾时按缓冲内容算 `Content-Length`
  ⇒ 响应退化成 **`Transfer-Encoding: chunked`**（真 Tomcat 实测），而 **Go 的 `net/http` 是 `Content-Length`**
  ⇒ ⇒ **必须自己先把 `Content-Length` 定下来**（`statusAllowsContentLength` 排除 1xx/204/304，
  阈值取 Tomcat 自己的响应缓冲 8192）。

⇒ **逐字对比（真 Tomcat，改前 vs 改后）**：

| 响应 | 改前（基线） | 改后 |
|---|---|---|
| 有 body（23 字节） | `Content-Length: 23` | ✅ `Content-Length: 23` |
| 空 body | `Content-Length: 0` | ✅ `Content-Length: 0` |
| 204 | 无 `Content-Length`、无 `Transfer-Encoding` | ✅ 同左 |
| 大 body（20000 字节 > 8192） | `Transfer-Encoding: chunked` | ✅ `Transfer-Encoding: chunked`（**溢出直写 socket ⇒ 基线本来就是 chunked**） |

⇒ ⚠️ **大 body 这一格是「阈值 8192」的唯一理由**：
Tomcat 的 `OutputBuffer.appendByteArray`（`org/apache/catalina/connector/OutputBuffer.java:807-822`）
在 body **超过缓冲容量**时会把溢出部分**直写 socket** ⇒ 响应**在写完前就被提交**
⇒ 基线本来就是 chunked ⇒ 我们也**不能**给大 body 设 `Content-Length`（否则反而与基线不一致）。

#### ⑤ 守门

`service/common/src/test/java/com/rag/common/observability/HttpClientDisconnectRealTomcatTest.java`
（**真 Tomcat**（`tomcat-embed-core`，test 作用域）+ **真 `HttpMiddleware`** + 真 OTel SDK + 真裸 socket RST）：

| # | 断言 | 变异 | 结果 |
|---|---|---|---|
| 1 | 断连 ⇒ span 的 `http.status_code == 499` | **M1**：去掉 499 判定（一律上抛） | 🔴 转红（`expected: <499> but was: <200>`） |
| 2 | 断连 ⇒ 日志有 `[HTTP] 499` | 同 M1 | 🔴 转红 |
| 3 | 不断连 ⇒ 200 且**无** 499 日志（证明 1/2 确实是断连触发的） | —— | —— |
| 4 | framing：`Content-Length`（非 chunked）/ 空 body `Content-Length: 0` / 204 无 `Content-Length` / **大 body 仍是 chunked** | **M2**：去掉显式 flush ⇒ 499 观测不到<br>**M3**：去掉 `setContentLength` ⇒ 退化成 chunked | 🔴 均转红 |

⇒ **基线 3/3 绿；M1 / M2 / M3 三组变异全部转红**（变异体均已还原）。

⚠️ **为什么必须真 Tomcat**：`MockHttpServletResponse` / MockMvc 的 `write()` **永远不失败**
⇒ 断连分支**根本触发不了** ⇒ 那种测试是「错误答案的守卫」，比没有测试更危险（§3.3）。

#### ⑥ 与 Go 的两处已知差异（不掩盖）

| # | 差异 | 说明 |
|---|---|---|
| ① | **定型时点** | Go 在断连**瞬间**定 499；RAG 要等业务跑完、写到 socket 才知道 ⇒ RAG 的 499 **必然晚于** Go |
| ② | **窄竞态窗口** | 若客户端在**业务跑完之后、flush 之前**才断：Go 的 `select` 可能已选中 `done` 分支 ⇒ 记 **200**；RAG 会记 **499**（RAG 更贴近真相，但与 Go 在这一窗口内不一致） |

---

### T-6 / T-7 / T-8 🟡 Content-Type / reason phrase / Traceparent —— **仍 OPEN，本轮未展开**

落点 `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83`
⇒ 本轮未展开逐条对照，状态维持 🟡 仍 OPEN（待拍板）。
⚠️ 三条**同落点**，应**一次拍板一次改**，不要分三次动同一文件（并发合并冲突）。

---

### T-9 🔴 熔断计数键缺 target 前缀 —— **确认为真漂移**，且🔴 **发现 §3.2 缺陷**

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18`<br>`breakerName := path.Join(cc.Target(), method)` ⇒ `breaker.DoWithAcceptableCtx(ctx, breakerName, ...)` |
| **Go 语义** | 熔断器名 = **`target` + 方法名** ⇒ **每个下游 target 一份独立计数** |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:193`<br>`String fullMethodName = method.getFullMethodName();` ⇒ **无 target 前缀**<br>闸门在 `:161-165` `breakerOf(fullMethodName)`（实例级 `ConcurrentHashMap`） |
| **可观察差异** | 两个**不同下游服务**调用**同名方法** ⇒<br>**Go：两份独立熔断器，互不影响**<br>**RAG：共用一份 ⇒ A 服务的失败把 B 服务的熔断器也拖开** |
| **状态** | ✅ **已修**（2026-10-25 第三轮 A2） |

🔴 **附带发现（§3.2 缺陷）**：RAG 该类的 javadoc **原文照抄了 Go 那一行**
`breakerName := path.Join(cc.Target(), method)`，却**只字未提自己没实现 target 前缀**。
⇒ 按 §3.2（「这段比 Go 多做了什么？语义还一样吗？」），此处是一处**无出处的 idiomatization** ——
**注释写着 Go 的语义，代码是另一套语义**，属于 AGENTS.md §3.2 明令「不可 review，不得合入」的那一类。
✅ **本轮已随修复一并订正**（javadoc 改成「本模块要求的语义：…」+ 指向签字/台账的指针）。

**本轮修法**（`GoStyleGrpcClientBreakerInterceptor`）：
熔断键由 `method.getFullMethodName()` 改为 `goBreakerName(targetOf(next), fullMethodName)`
⇒ 折叠连续斜杠（对齐 Go 的 `path.Join`）。

**身份从哪来（实测，非推测）**：

| 问 | 答 | 依据 |
|---|---|---|
| `ManagedChannel` 有 `getTarget()` 吗？ | ❌ **没有** | grpc-java **1.63**，`javap` 实测（**不是** 1.10.2 那种老 API） |
| 那用什么？ | `Channel.authority()` | devh `DiscoveryClientNameResolver.getServiceAuthority()` 返回 `uri.getPath().substring(1)` ⇒ `discovery:///message-rpc` 下 = **`message-rpc`**（≡ Go 的 `key=message.rpc`）；直连 = `host:port` |
| ⚠️ 坑 | **in-process channel 的 authority 恒为 `"localhost"`** ⇒ 会塌缩成同一个 key | ⇒ 守门断言改用**直连地址**的真 channel |

⇒ **表达式层差异（不掩盖）**：Go 的 target 形如 `etcd:///127.0.0.1:2379` 且 breaker key 里带 `key=message.rpc`；
RAG 用**服务名**。⇒ breaker 名会出现在日志/统计里 ⇒ **字面不相同**，但**隔离语义相同**。

**守门**：`GoStyleGrpcClientBreakerTargetKeyTest`（**6 条**）。
⚠️ **守门网曾有洞并已修正**：第一版自己算 key 再查 breaker ⇒ **变异不转红**（等于自证）
⇒ 改为**只调 `interceptCall` 再看 `trackedMethods()`**；现变异转红
（`expected: <2> but was: <1>` + **跨服务污染复现**）。
⇒ 同批更新测试接缝语义：`trackedMethods()` 变为「(服务,方法) 对数」；新增 `breakerForName(String)`。

---

### T-10 ✅ **本轮闭环** ⇒ 详见 [`cycle-deps.md`](cycle-deps.md)

`service/users/user/user-rpc/.../UserDomainService.java:113` 的 javadoc 假断言已订正，
`service/common/.../RpcInterceptor.java` 的 `DeadlineState.markExpired(long)` 已删除，
配真 in-process gRPC 守门测试 + 变异测试。

---

## 二、本轮复核汇总表

| # | 条目 | 复核前 | 复核后 | 本轮动了？ |
|---|---|---|---|---|
| T-1 | Content-Type 大小写 | ⬜ 待复核 | ✅ **已修**（残余 ① **本轮第三轮亦已修**） | ✅ |
| T-2 | MaxConns 作用域 | 🔴 仍未修 | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 | ✅ 指针 |
| T-3 | 503/499 缺 nosniff | ✅ 已修 · 待确认 | ✅ **实测确认已修** | ❌ |
| T-4 | 499 / 2s deadline | 🔴 仍开放 | 🟡 **观测面已修**（499 可见）· **资源面残留为框架限制**（⇒ `SD-2026-10-25-CAND-12` 待签字） | ✅ |
| T-5 | MaxConns 作用域 | 🔴 仍开放 | ✅ **已签字**（同 T-2）⇒ 移出本档 | ✅ 指针 |
| T-6 | 成功/失败 Content-Type | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-7 | 状态行 reason phrase | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-8 | 缺 Traceparent | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-9 | 熔断计数键 | ⬜ 待复核 | ✅ **已修**（键补 target 前缀）+ §3.2 缺陷一并订正 | ✅ |
| T-10 | UserDomainService 假断言 | ❌ 未销账 | ✅ **已闭环** | ✅ |

⇒ **首轮净效果**：10 条里 **1 条闭环**（T-10）· **3 条从「待复核」升级为「已确认的真漂移」并补齐 Go 出处**
（T-1 / T-2+T-5 / T-9）· **6 条维持原状**（未展开）。

⇒ **第三轮净效果（2026-10-25）**：**T-1 残余 ① / T-9 / T-4 全部落地**
⇒ 10 条里 **6 条已闭环或已签字**（T-1 + 残余① / T-2 / T-3 / T-4观测面 / T-5 / T-9 / T-10）·
**T-4 资源面残留转「框架限制」待签字**（`SD-2026-10-25-CAND-12`）·
**T-6 / T-7 / T-8 维持 🟡 仍 OPEN**（同落点，待用户拍板）。

---

## 三、下一轮的排期建议（按 §5.3 的「rpc 必须先于 api」与单写权约束）

| 序 | 条目 | 落点 | 是否需要别的域配合 |
|---|---|---|---|
| ~~1~~ | ~~**T-1**~~ | `service/common/http/GoStyleJsonBodyConverter.java` | ✅ **已完成**（2026-10-25 第二轮） |
| ~~1′~~ | ~~**T-1 残余 ①**~~ | 同上（包一层 `HttpInputMessage`） | ✅ **已完成**（2026-10-25 第三轮）⇒ 顺带修掉 `charset=gbk` 偏差 |
| ~~2~~ | ~~**T-9**~~ | `service/common/protection/GoStyleGrpcClientBreakerInterceptor.java` | ✅ **已完成**（2026-10-25 第三轮）⇒ 身份源 = `Channel.authority()` |
| ~~3~~ | ~~**T-2 / T-5**~~ | —— | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 |
| ~~4a~~ | ~~**T-4**~~ | `service/common/observability/HttpMiddleware.java` | 🟡 **观测面已修**（第三轮）；资源面 ⇒ `SD-2026-10-25-CAND-12` 待签字 |
| 4b | **T-6 / T-7 / T-8** | `service/common/http/GoStyleRouterErrorResponses.java` | 🟡 **仍 OPEN**，同落点 ⇒ **一次拍板一次改** |

⚠️ **排序提醒**：T-1 与 T-9 都改 `service/common` ⇒ **本域独占写点**，别的域此刻在改 common 会撞
（§5.3b 规则 1）⇒ 这几条**必须串行**。

---

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **第三轮（A1 / A2 / A3 / A4 四项落地）**。① **T-1 残余 ① 已修** —— 包一层 `SanitizedJsonContentTypeInputMessage` ⇒ 畸形头不再抛 + `charset=gbk` 回归 UTF-8；守门扩到 8 条 + 变异双向转红 ② **T-9 已修** —— 熔断键补 target 前缀（身份源 = `Channel.authority()`，因 grpc-java 1.63 的 `ManagedChannel` 无 `getTarget()`）；守门 6 条 + 变异转红（第一版守门有洞 ⇒ 已改为只调 `interceptCall` 再看 `trackedMethods()`）③ **T-4 观测面已修** —— 499 判据从「worker 内写失败」改成「**filter 写 socket 失败**」；配真 Tomcat 守门 3 条 + M1/M2/M3 三组变异全转红；⚠️ 顺带修掉「提前 flush 把 `Content-Length` 退化成 chunked」这个**新引入的 wire 差异**；🔴 残留「在飞 gRPC 不被提前取消」⇒ **框架限制** ⇒ `SD-2026-10-25-CAND-12`（待签字）④ **CYCLE-002 普查** —— `service/common` 的 `@Deprecated` 归零、无反向 import 业务域、无跨域 `{@link}` ⇒ **0 命中** ⇒ 落成可重跑守门 `tools/difftest/check-cross-domain-holds.py` |
| 2026-10-25 | **第二轮（T-1 修复）**。T-1 改为**取原始头值**（`getFirst(CONTENT_TYPE)`）⇒ 大小写敏感判定与 Go 对齐；配真 `HttpHeaders` 守门测试 6 条 + 变异测试（转红）。⚠️ 登记 **T-1 残余 ①**（解析层仍走 MediaType ⇒ 畸形头仍抛，Go 不会）。🔴 新增 **X-9**（3 份生产复制品）⇒ [`cross-domain.md`](cross-domain.md)。T-2 / T-5 **用户签字** ⇒ 转 `SD-2026-10-25-01` 并移出本档 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 T-1~T-10 逐条复核结论；纠正「Go 出处在项目树里」的方法论错误（真源分 L1 项目层 / L2 go-zero 框架层）；确认 T-1 / T-2 / T-5 / T-9 为真漂移；发现 T-2/T-5 档位错置、T-9 的 §3.2 缺陷；T-10 转 [`cycle-deps.md`](cycle-deps.md) |
