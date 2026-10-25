# global 第二十二轮 —— T-13：超时路径的「响应撕裂 / 状态码被抢」修掉（对齐 Go 的**单写者**语义）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`1d179ff`（第二十一轮 T-11 收口）
> **性质**：**共享层（`service/common`）的行为缺陷修复** + 1 条真守门 + 变异测试
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**；共享层改动 ⇒ favorite 的**可观察行为会变** ⇒ 按 §5.3c **记账并告知 owner**）

---

## 一、要解决什么

第二十一轮（T-11）的**全仓回归**只红了一条：

```
com.rag.message.message.api.MessageApiTimeoutRealTomcatTest.slowDownstreamYieldsGoTimeoutContract
  ⇒ java.io.IOException: HTTP/1.1 header parser received no bytes
服务端日志 ⇒ java.lang.NullPointerException:
     Cannot invoke "org.apache.tomcat.util.http.MimeHeaderField.getName()" because "this.headers[i]" is null
  at org.apache.tomcat.util.http.MimeHeaders.getValue(MimeHeaders.java:367)
  at org.apache.coyote.http11.Http11Processor.prepareResponse(Http11Processor.java:980)
  …
  at org.springframework.http.server.ServletServerHttpResponse.flush(ServletServerHttpResponse.java:126)
  at org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor.handleReturnValue(…)
  at com.rag.common.observability.HttpMiddleware$1.lambda$doFilter$0(HttpMiddleware.java:322)
```

单独复跑 ⇒ **PASS** ⇒ **偶发**。

### 🔴 归因结论（先做 A/B，再谈修法）

| 步骤 | 做法 | 结果 |
|---|---|---|
| ① 结构取证 | `git diff aac691c..1d179ff -- service/common/.../HttpMiddleware.java service/common/.../GoStyleMaxBytesFilter.java` | **空** ⇒ 涉事的**竞态代码在 T-11 前后逐字节相同** |
| ② 写可复现的门 | 新增 `GoStyleTimeoutResponseRaceGateTest`（真 Tomcat + **生产用的** `HttpMiddleware` + 30ms 超时 + 200 轮 + 裸 socket） | 修前 **200/200 全坏** |
| ③ **A/B 对拍** | 同一个门拷到 **`aac691c`（T-11 之前）** 的隔离工作树跑 | **同样 200/200 全坏** |

⇒ ⇒ **该缺陷早于 T-11 ⇒ 不是 T-11 引入的**（T-11 的全仓回归只是**第一次把它撞出来**）。

---

## 二、根因（逐行取证）

### 2.1 Go 侧：**socket 的写者永远只有一个**

> 真源 = **本项目自己的中间件**（**不是** go-zero 的原生超时中间件 —— Go 侧 `observability.DisableNativeRestTimeout` 把它关掉了）。

`Sea-RideTheWind_golang/service/common/observability/http.go`：

```go
case <-ctx.Done():
    rec.markTimedOut()                                    // ← 先把业务写封掉
    statusCode := http.StatusServiceUnavailable
    if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }
    MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
    http.Error(w, httpTimeoutBody, statusCode)            // ← 写**原始** w
```

```go
type observabilityResponseWriter struct {
    header http.Header
    body   bytes.Buffer
    status int
    mu       sync.Mutex
    timedOut bool
}
func (w *observabilityResponseWriter) Write(data []byte) (int, error) {
    w.mu.Lock(); defer w.mu.Unlock()
    if w.timedOut { return 0, http.ErrHandlerTimeout }     // ← 超时后**丢弃**业务写
    return w.body.Write(data)
}
```

⇒ Go 的 writer 是**纯 buffer**：`Write` / `WriteHeader` **从不碰**底层 `http.ResponseWriter`；
唯一落 socket 的地方是 `flushTo(dst)`，而它**只在 done 分支**被调用
⇒ ⇒ **任何时刻只有一个写者碰 socket** ⇒ 超时响应必然完整（503 + body + 头）。

### 2.2 RAG 侧：**worker 线程也能碰底层响应**

`service/common/.../HttpMiddleware.java` 的 `ObservabilityResponseWriter`
（`extends HttpServletResponseWrapper`）**没有覆写 `flushBuffer()`**
⇒ `ServletResponseWrapper.flushBuffer()` **直接透传**给底层 Tomcat 响应
⇒ **worker 线程可以提交响应**。

而超时分支里，容器线程与 worker 线程**同时**在写同一个响应：

| 线程 | 动作 |
|---|---|
| 容器线程 | `writeTimeoutResponse`： `setStatus(503)` + `setContentType` + `setHeader(nosniff)` + `getWriter().write(...)` + `getWriter().flush()`（⇒ 触发 commit） |
| worker 线程 | 被 interrupt ⇒ 业务抛 `InterruptedException` ⇒ Spring `@ExceptionHandler` 写错误响应 ⇒ `ServletServerHttpResponse.flush()` ⇒ **`flushBuffer()`** ⇒ **commit** |

⇒ **两个线程同时改 Tomcat 的头数组** ⇒ Tomcat 11 的 `MimeHeaders.createHeader()`：

```java
if ((mh = headers[count]) == null) { headers[count] = mh = new MimeHeaderField(); }
count++;
```

⇒ 两个线程各自 `count++` 却只填了一个槽位 ⇒ **数组里留 null 洞**
⇒ 随后任何线程读头（`Http11Processor.prepareResponse` 读 `"Date"`）⇒ `headers[i].getName()` ⇒ **NPE**
⇒ 响应根本没写出去 ⇒ 客户端只看到 **EOF**。

### 2.3 三种可观察形态（修前实测）

| 形态 | 触发条件 | 客户端看到 |
|---|---|---|
| **① 状态码被抢** | worker 先 commit | `HTTP/1.1 200` + `Transfer-Encoding: chunked` + body 却是 `Request Timeout` ⇒ **不是 503** |
| **② 头被写坏** | 两线程 `createHeader()` 交错 | 状态行变成 `Transfer-Encoding: chunked`、头重复/交错、`Content-Type` 掉 charset |
| **③ NPE ⇒ EOF** | 头数组留 null 洞 | `IOException: HTTP/1.1 header parser received no bytes`（**生产实测到的那次**） |

⇒ 三者同源：**两个写者**。

---

## 三、修法（4 条，缺一不可）

| # | 修法 | 挡的是什么 |
|---|---|---|
| ① | **`ObservabilityResponseWriter` 加一把 `underlyingLock`**，所有对底层响应的写（`setStatus` / `sendError` / `setHeader` / `addHeader` / `setContentType` / `flushBuffer` / `flushTo`）都在锁内；`markTimedOut()` / `markClientCancelled()` **也持同一把锁** | 形态 ②③（`count` 交错 ⇒ null 洞 ⇒ NPE） |
| ② | **超时 / 断连之后，`flushBuffer()` 不再透传**（no-op） | 形态 ①（worker 抢先 commit ⇒ 状态码被抢） |
| ③ | **先标记、后中断**：`rec.markTimedOut()` 排在 `workerFuture.cancel(true)` **之前**（Go 侧 `rec.markTimedOut()` 也在 `http.Error` 之前） | worker 在标记落地前的漏网之写 |
| ④ | **超时响应显式定 `Content-Length: 15`** | wire 对齐：Go 的 `http.Error` 写进 net/http 缓冲 ⇒ 收尾时算长度 ⇒ 线上是 `Content-Length`；RAG 侧 `getWriter().flush()` 即提交 ⇒ 退化成 `Transfer-Encoding: chunked` ⇒ **wire 不一致** |

⚠️ **② 为什么不是「永远 no-op」**：Servlet 规范里「commit 之后设 status 无效」是**容器语义**，
`HttpMiddlewareTest#statusSetAfterCommitIsIgnoredByServletSemantics` 把它固化成显式契约
⇒ 正常路径下 `flushBuffer()` 必须**照常透传** ⇒ 只在**超时 / 断连之后**掐掉。

⚠️ **① 与 ② 是叠加的，不是二选一**：锁挡的是「标记落地前已在飞行中的那次写」，
no-op 挡的是「worker 抢先提交」⇒ 少任一条都留缺口。

---

## 四、守门（真证据）

`service/common/src/test/java/com/rag/common/observability/GoStyleTimeoutResponseRaceGateTest`

| 项 | 做法 |
|---|---|
| 容器 | **真 Tomcat 11.0.24**（`tomcat-embed-core`） |
| 被测对象 | **生产用的** `HttpMiddleware.newHttpMiddleware(...)`（**不是 mock**、不是简化版） |
| 时序 | 超时阈值 **30ms**（生产 3s）⇒ 一轮几十毫秒 ⇒ 可多轮；Servlet 被 interrupt 后**刻意复刻**生产里 `@ExceptionHandler` 的那次晚写（`setHeader` + 写 body + `flushBuffer()`）⇒ 把窗口顶到最大 |
| 客户端 | **裸 socket**（不是 HttpClient）⇒ 才能看见「一个字节都没收到」这种撕裂；且读**原始字节**（`BufferedReader.readLine()` 会归一化行尾 ⇒ 字节级断言会失真） |
| 轮数 | 200（`-Drag.timeoutRace.rounds=N` 可调） |

断言（每一次都必须成立）：

1. 状态行 `HTTP/1.1 503`；
2. body（**解 chunk 后**）恰好 `"Request Timeout\n"`；
3. **不能**出现 worker 晚写留下的 `X-Late-Write` 头；
4. 必须带 `X-Content-Type-Options: nosniff`（Go `http.Error` 自带）；
5. `Content-Type` 必须是 `text/plain` + `charset=utf-8`（**分号后的空格**是 Tomcat 的已知框架限制 ⇒ 只断言到 charset）。

> ⚠️ **1xx 要按 RFC 9110 跳过**：Tomcat 11 会自发发一条 `HTTP/1.1 103 Early Hints`（RAG 侧**无人**设 103 ⇒ 容器自己的行为；修前修后都出现 ⇒ 与本缺陷无关）⇒ 门按规范忽略 1xx 再看最终响应。

---

## 五、变异测试（5 条）

隔离工作树跑（PIT-GLOBAL-020），三道闸（PIT-GLOBAL-018）：删旧 XML + 看 maven 退出码 + grep `COMPILATION ERROR`。
判定口径：目标 testcase **变红**（failure 或 error 都算）；连带红数记录但不扣分。

| # | 变异 | 期望红点 |
|---|---|---|
| M0 | **整体回退**（`git checkout` 生产文件 ⇒ 修前状态） | 门红（200/200 全坏） |
| M1 | `flushBuffer()` 去掉「超时后 no-op」 | 门红（状态码被抢 ⇒ 200） |
| M2 | `writeTimeoutResponseQuietly` 改走 `writeToOriginal`（⇒ 被「已超时」标志 no-op 吞掉） | 门红（客户端拿不到 503） |
| M3 | `writeToOriginal` 去掉「超时/断连后 no-op」标志检查 | 门红（晚写漏进响应） |
| M4 | 顺序倒转：`cancel(true)` 提到 `markTimedOut()` 之前 | 门红（晚写漏进响应） |

---

## 六、可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **超时响应不再可能被 worker 抢走** ⇒ 稳定是 `503 + "Request Timeout\n"` | Go 的单写者语义 | **全部 10 个 api**（共享层） |
| ② | **超时响应多一个 `Content-Length: 15`**（原先是 `Transfer-Encoding: chunked`） | Go 的 `http.Error` ⇒ net/http 算长度 | 全部 10 个 api |
| ③ | **错误处理路径（`@ExceptionHandler` 返回 `ResponseEntity`）的响应不再被 worker 提前提交** ⇒ 由 `flushCaptured` 统一提交 ⇒ 框架从 `chunked` 变 `Content-Length` | 同上 | 全部 10 个 api（**向 Go 收敛**） |

---

## 七、未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Tomcat 那条 `103 Early Hints` 的触发点未定位** | Tomcat 11 只有 `Response.sendError(103)` → `sendEarlyHints()` 会发它，而 RAG 侧**无人**调 `sendError(103)` ⇒ 触发链未查清；**但**它 ① 是 informational（客户端按规范忽略）② 修前修后都出现 ③ 最终响应完好 ⇒ **判为无影响** |
| ② | **未做 Go 侧活体对拍** | Go 事实全部来自**逐行读源码**（`service/common/observability/http.go`）⇒ 没有真跑 Go 进程 |
| ③ | **并发压测未做** | 本门是 200 轮串行；未做多线程并发压测 |

---

## 八、favorite 记账（**本轮对 favorite 零文件改动**）

`service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
但**共享层改动会影响 favorite 的可观察行为** ⇒ 按 §5.3c 记账：

| 项 | 内容 |
|---|---|
| **favorite 侧受影响** | favorite-api 的**超时路径**现在稳定返回 `503 + "Request Timeout\n" + Content-Length: 15`（原先可能返回 200 / 撕裂 / EOF）⇒ **向 Go 收敛** |
| **favorite 代码要改吗** | **不需要** ⇒ 修法全在 `service/common` |
| **favorite owner 要知道什么** | ① favorite 的超时响应**不再可能状态码被抢**；② 多一个 `Content-Length` 头；③ 若 favorite 域有守门断言「超时时是 chunked / 是 200」⇒ 需要复核（若有 ⇒ 那是**依赖缺陷行为的断言**） |

---

## 九、交接整合者

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` | 共享台账 ⇒ 协议 §二.3 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` | 共享索引 |
| ③ | 把**本轮变异脚本**落仓到 `tools/difftest/`（建议名 `timeout-race-mutation.py`） | `tools/` **不在 L1 白名单** ⇒ 只留在会话临时目录 `/tmp/mutation-round22.py`；§5 已把 5 条变异的锚点与期望红点写全 ⇒ 可据此重建 |
| ④ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |
