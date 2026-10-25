<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25（第十九轮 · 第二轮）· 本轮**改了共享层代码**：新增 `GoStyleTraceparentFilter`（T-8 落地）
     + 统一 `service/common` 的 OpenTelemetry 版本（修运行时 `NoSuchMethodError`）
     + **T-8 的「链上位置」定案**（9 个 filter 的 `@Order` 整体后移，相对顺序不变）
     ⇒ 详见 §一·T-8 / §一·T-12 与文末变更记录 -->

# global · common 尾巴台账（T-1 ~ T-12）

> **归属**：`global` 收口域的「common-tails」议题档（§5.3b）。
> **对象**：**别的域**的漂移，但**修法落在** `service/common` 的共享类里 ⇒ 不是 `common` 域自身的语义漂移
> （边界见 [`_README.md`](_README.md) §二）。
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
⇒ 已登记为踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md)。

---

## 一、逐条复核结论

### T-1 ✅ `Content-Type` 大小写敏感判定 —— **已修**（残余 ① 见下）

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/rest/httpx/requests.go:153-155`<br>`func withJsonBody(r *http.Request) bool { return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson) }`<br>常量：`rest/internal/header/headers.go:5` ⇒ `ApplicationJson = "application/json"` |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java:331`（`read()` 取 Content-Type）<br>判定在 `:241-244`（`goReadsBody(String,long)`）⇒ `contentType.contains(GO_JSON_CONTENT_TYPE_MARK)`，`GO_JSON_CONTENT_TYPE_MARK = "application/json"`（`:79`）<br>🆕 **第十六轮新增**：servlet 版判据 `:296`（`goReadsBody(HttpServletRequest)`）· Go 的第二道闸 `maxBodyLen = 8 MiB` 在 `:117`（`GO_MAX_BODY_LEN`）+ `goLimitedBody(...)` |
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
> ⇒ 决策记录在 [`docs/Intentional_Deviations/global/common-tails.md` §SD-2026-10-25-01`](../../Intentional_Deviations/global/common-tails.md)
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
[`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)。
⇒ **签字决定 = 保留 RAG 的全局闸门，不改成 per-route**。
⇒ 配套落地：`GoStyleMaxConnsFilter` 的 javadoc 已从「有意的表达层收敛」自述
**改成指向该签字档的指针**（并加一句「不要因为 Go 是 per-route 就顺手改回」）。

---

### T-3 ✅ 已修 —— **实测确认，无需 global 再动**

`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:431`
⇒ `resp.setHeader("X-Content-Type-Options", "nosniff")` **在位**（`writeTimeoutResponse` 内）。
⇒ 关闭。（Go 出处：`net/http.Error` 自 Go 1.11 起写该头。）

---

### T-4 ✅ **观测面 + 资源面均已修**（499 可见 + 在飞 gRPC 会被提前取消）—— 2026-10-25（第十轮落地 · 第十三轮订正）

> **对应**：`hot` 域 `D25` / `DRIFT-HOTAPI-025`（**代码在 `service/common` ⇒ 跨域 ⇒ global 修**）。
> **原结论**：「客户端断连后 RAG **等 2s deadline**、**断连后继续发下游调用**、**无 499 记录**」
> ⇒ ⇒ **三条全部已修**（观测面第三轮 · 资源面第十轮方案⑤）⇒ **残留的只有「时序差」，已签字认账**。

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

#### ③ ✅ **资源面已修（2026-10-25 第十轮，方案⑤「主动探测写」）** —— 在飞 gRPC **会被提前取消**

| | Go 真源 | RAG（**第十轮修后**） |
|---|---|---|
| 断连后在飞 gRPC | **断连后 1~3ms 内全部取消**（ctx 传播） | 🟡 **会取消**，但**晚于 Go**（实测 **461ms** ≈ 1 个探测周期；探测间隔 500ms）<br>⇒ 🔴 残留的是**时序差**，不是「不取消」 |
| 断连后是否继续发下游调用 | **否** | 🟡 探测判定断连后不再发新的下游调用 |

⇒ ⚠️ **本条不再是「不修」项**：第九轮查明 6 条替代路径后，**第十轮落地方案⑤**（用户 2026-10-25 拍板）⇒
⇒ 🆕 **新签字条目** [`docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-04`](../../Intentional_Deviations/global/common-tails.md)
   —— 记的是**方案⑤ 带来的 wire 偏离**（客户端先收到 N 行 `103 Early Hints`）+ **499 定型时点晚于 Go**；
⇒ ❌ 原「框架限制」记录 `SD-2026-10-25-02` **已撤销**（问题被修掉 ⇒ 不需签字）。

**为什么在「Tomcat + HTTP/1.1 + 同步 Servlet + 保 `Content-Length`」下拿不到信号**
（真 Tomcat 11.0.24 实测 + Tomcat 源码取证）：

1. **Servlet 容器不向应用暴露「客户端已断连」信号**
   ⇒ 实测：Servlet 里 `request.startAsync()` + 注册 `AsyncListener`，
   客户端裸 socket `SO_LINGER{1,0}` 强制 **RST** ⇒ **`onError` / `onTimeout` / `onComplete` 一个都不触发**；
2. ⇒ Tomcat 源码同证：`org/apache/coyote/AbstractProcessorLight.java:62`
   对 `SocketEvent.DISCONNECT` 明写 **"Do nothing here, just wait for it to get recycled"**；
   `org/apache/catalina/connector/CoyoteAdapter.java:140-160` 的 `setErrorState(..., true)`
   只在**「非容器线程上的 I/O 错误」**时触发（即写失败，不是对端消失）；
3. ⇒ Servlet 层也**拿不到 socket**：`org.apache.catalina.connector.Request` 里**没有任何 socket 访问器**
   ⇒ 无法自己轮询「对端还在不在」。

> ⚠️ **2026-10-25 第八轮订正（用户质询后）** —— 上面的第 3 条**只证明了「公开 API 拿不到」**，
> **不证明「生态做不到」** ⇒ 已用**源码 + 2 个真 Tomcat 探针**把 6 条替代路径全查完：
> **③ 轮询 Tomcat 内部 `isClosed()` ❌实测否决**（反射链通，但**滞后 5.6 秒**，比主动写还慢）
> · **⑤ 主动探测写 ✅实测有效**（断连后 ~500ms 抛 `ClientAbortException`，但**改 framing**）
> · **④ HTTP/2 `RST_STREAM` ✅ Tomcat 真触发 `AsyncListener.onError`**（但 **Go 侧实测明文 HTTP/1.1 ⇒ 偏离 Go**）
> · **① 换 Jetty / ② 换 WebFlux+Netty** 🟡 可行但**换运行时/换栈**
> · **⑥ 缩短在飞 deadline** ✅ **零改动 · 零 wire 差异 · 零签字**
> ⇒ ⇒ **定性从「框架限制（事实登记）」改判为「要不要为这一点动约束（决策项）」**
> ⇒ **逐条 ⇒ [`docs/Intentional_Deviations/global/common-tails.md` §①②③④](../../Intentional_Deviations/global/common-tails.md)**。

⇒ **本条 ⇒ 已闭环（第十轮修掉）⇒ 偏离由 `SD-2026-10-25-04` 签字认账**（2026-10-25 06:40 用户再次明确签字）
⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)。
⚠️ **签字权在用户**：本轮**未**自行批准（⚠️ 第四轮曾误转正为「已签字」，已撤销）。
⇒ 🟡 **官方路径已记录为「可选后续优化」（不是必选项）** ⇒ **不触发返工**
⇒ ⇒ [`docs/Intentional_Deviations/global/common-tails.md` §「`SD-2026-10-25-04` 附记 · 官方路径记录」](../../Intentional_Deviations/global/common-tails.md)
   —— 记录：Spring 官方**没有「检测」API**（只有 `DisconnectedClientHelper` 事后分类 + `AsyncListener.onError` / `DeferredResult.onError` 这条**事件消费**链路）
   + Tomcat `AbstractProtocol.java:1494-1503`（**async 期间不注册读兴趣** ⇒ HTTP/1.1 下官方链路也不更早）
   + 四个**可选**优化项 O-1~O-4（O-1 用官方 `DisconnectedClientHelper` 分类 ⇒ 成本最低）。

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

### T-6 / T-7 / T-8 ✅ **归因已定位（第九轮，2026-10-25）** —— 三条**落点各不相同**，不是「同落点一次改」

> ⚠️ **原台账写的「落点 `GoStyleRouterErrorResponses.java:83`」是错的** ——
> 该文件只管 **404 / 405** 两条框架级错误响应（且**已逐字节对齐**），与这三条**无关**。
> 九条逐条定位后，**三条的真实落点分属三个不同层次** ⇒ **不能一起拍板、也不能一起改**。

#### T-6 · 成功/失败响应的 `Content-Type` 缺 `charset=utf-8` —— ✅ **已修（第二十五轮，2026-10-25）**

| | 内容 |
|---|---|
| **Go 侧事实** | `go-zero v1.10.2/rest/internal/header/headers.go:17` ⇒ `ContentTypeJson = "application/json; charset=utf-8"`<br>`go-zero v1.10.2/rest/httpx/responses.go` 的 `doWriteJson` ⇒ `w.Header().Set(ContentType, header.ContentTypeJson)`<br>⇒ **Go 的 JSON 响应头 = `application/json; charset=utf-8`（分号后有空格）** |
| **RAG 侧落点** | **不是** `GoStyleRouterErrorResponses` ⇒ 是 **Spring 的 `MappingJackson2HttpMessageConverter`**：默认 `Content-Type: application/json`（`writeAcceptCharset` 默认 `false` ⇒ 不补 charset） |
| **修法（实际落地）** | ✅ 在 `service/common` 新增 `GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` + `@RestControllerAdvice`）⇒ JSON 响应头写 **`"application/json;charset=utf-8"`（无空格）**<br>⇒ **改 1 处 ⇒ 10 个 api 全部生效 ⇒ 不碰任何 api 模块的代码（`favorite-api` 也不碰）**<br>⚠️ **为什么挂在 Advice 而不是 `HttpMessageConverter`**：共享层的 `GoStyleJsonBodyConverter` **不是所有 api 都启用**（`task-api` 明确把它排除在组件扫描之外）⇒ 挂在它上面 ⇒ **task-api 不生效** ⇒ Advice 按「响应体是不是 JSON」判定 ⇒ **与用哪个转换器无关**<br>⚠️ **为什么写「无空格」而不是 Go 的字面量（带空格）**：**第二十六轮订正** —— 见下方「🔴 自曝错账 #3」。**无空格**形态 = **各域自己的 advice 会写的那个值**（`MediaType.parseMediaType("application/json; charset=utf-8").toString()` ⇒ Spring 保留原样大小写 ⇒ 小写 `utf-8`）⇒ 真 Tomcat 路径**不变**（照样归一化）· MockMvc 路径**零变化** ⇒ **别的域的守门不会被共享层改动打到** |
| **🔴 冲突（已解决）** | **hot 域的 `SD-HOT-API-006` 已签字「不复刻」** ⇒ **2026-10-25 21:57 用户拍板撤销** ⇒ 撤销属**用户决策**（签字权在用户，§5.3b）<br>⇒ ⇒ **签字项已撤销**（`docs/Intentional_Deviations/hot/api.md`）⇒ 被它签字描述的行为（「RAG 的 JSON 响应不带 charset」）**已不存在** |
| **🔴 自曝错账 #3（第二十六轮订正）** | ⚠️ **「写 Go 的原始字符串（带空格）」这个修法只在真 Tomcat 上成立** —— 当时的推理是「Tomcat 归一化 ⇒ 写什么都一样 ⇒ 写原始字符串就能**自动**回到与 Go 逐字节一致」<br>⇒ ⇒ **第二十六轮全仓回归实测推翻**：**MockMvc 路径没有容器归一化** ⇒ 写什么就**原样**出现在响应头里 ⇒ **带空格** ⇒ **打红了 favorite-api 自己的守门**（`FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant`：期望 `application/json;charset=utf-8`，实得 `application/json; charset=utf-8`）⇒ 而 favorite 域对 global **零写权** ⇒ **修法只能落在共享层**<br>⇒ ⇒ **改写无空格形态**（= 各域自己 advice 会写的值）⇒ 真 Tomcat **不变** · MockMvc **零变化** ⇒ **favorite 一行不用改**<br>⇒ ⇒ **教训**：「**真容器守门绿**」≠「**这个改动在所有观测路径上都无害**」⇒ ⇒ **PIT-GLOBAL-025**<br>⇒ 逐条 ⇒ [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §十四 · 教训 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) PIT-GLOBAL-025 |
| **🔴 自曝错账 #2（本轮订正）** | ⚠️ **本文档原先写的「该理由已被本仓自己证伪」是错的** —— 当时的推理是「`GoStyleRouterErrorResponses.java:81-88` 对 `text/plain; charset=utf-8` 就是直接写原始字符串 ⇒ 同一手法用在 `application/json; charset=utf-8` 上即可」<br>⇒ ⇒ **第二十五轮实测证明这个推理不成立**：**写原始字符串并不管用** —— **Tomcat 11 把 Content-Type 拆成 (type/subtype, charset) 两份存、写出时重新拼**：<br>`Response.setContentType(String)`（11.0.24）⇒ `MediaTypeCache.parse(type)` ⇒ 有 charset 时 `coyoteResponse.setContentTypeNoCharset(parsedType[0])` + `setCharsetHolder(parsedType[1])`；`Http11OutputBuffer.writeHeaders()` ⇒ `put(contentType)` + `put(SEMICOLON)` + `put(CHARSET)` ⇒ **线上恒为 `type/subtype;charset=xxx`（分号后无空格）**<br>⇒ ⇒ `setContentType` / `setHeader` / `addHeader` 三条路都汇到 `setContentType` ⇒ **应用层根本拿不到那个空格**<br>⇒ ⇒ **`SD-HOT-API-006` 的原始理由「不可逐字节复刻」是成立的，而且比当初写的更强**（真实原因是**容器层**，不是 Spring 的 `MediaType.toString()`）⇒ ⇒ **撤销的性质因此是「纯用户决策」**（「技术上仍不可逐字节复刻，但选择向 Go 靠拢」） |
| **残余（已接受，不新签）** | 修后 RAG = `application/json;charset=utf-8`（**无空格**）vs Go = `application/json; charset=utf-8`（**有空格**）⇒ **只差一个 OWS** ⇒ 按 RFC 9110，参数分隔符两旁的 **OWS 不显著** ⇒ **语义相同**<br>⇒ ⇒ 与 **404 路径是同一类、仓库早已接受**的做法（`LikeApiRouterErrorRealTomcatTest` 对 404 就是**刻意解析后比对**，注释明写「比原始字符串会得到一个『必然红、且红了也不代表有错』的断言（已实测）」）⇒ **不需要新登记** |
| **守门** | ✅ `service/common` 的 `GoStyleJsonContentTypeCharsetTomcatTest`（**真 Tomcat + 裸 socket**，3 条：JSON 成功 / JSON 失败 / **显式 Content-Type 不许被误伤**）<br>⇒ ✅ **变异 2/2 全红**（M8 不写头 ⇒ 两个 JSON 门红；M9 无条件写 ⇒ 「不误伤」门红）⇒ **各打各的** |
| **可观察变化** | JSON 响应头**多一个** `charset=utf-8` ⇒ **向 Go 收敛**（全部 10 个 api）⇒ ⚠️ **favorite 侧同样生效**（共享层）⇒ 已按 §5.3c **记账并告知 owner**（favorite 代码/配置/测试/台账**零改动**） |
| **证据强度** | ✅ Go 侧常量与调用点逐行取证（`headers.go:17` + `responses.go:doWriteJson`）<br>✅ RAG 侧 6 档真容器响应头对照已在 message 域台账留档（`docs/DRIFT/message/api.md` §2.1 / §2.2） |

#### T-7 · 状态行缺 reason phrase —— 🔴 **框架限制（真）**，落点在**容器层**，不在 `service/common`

| | 内容 |
|---|---|
| **Go 侧** | Go 的 `net/http` 写完整状态行：`HTTP/1.1 200 OK` / `401 Unauthorized` / `404 Not Found` / `405 Method Not Allowed` |
| **RAG 侧** | Tomcat 只写 `HTTP/1.1 200` / `401` / `404` / `405` ⇒ **无 reason phrase** |
| **归因** | **Tomcat 的 `org.apache.coyote.http11.Http11OutputBuffer`**（状态行由容器生成）⇒ **不是 `service/common` 的任何一行** ⇒ **`service/common` 改不了** |
| **判定** | 🔴 **真「框架限制」**（与 T-4 同类，但 T-4 是「可绕、代价高」，本条是「**不可绕**」）<br>⇒ RFC 7230/9110 **允许**省略 reason phrase ⇒ 客户端按状态码解析 ⇒ **业务语义无影响**，只是**字节级不等价** |
| **⚠️ 边界** | 「归因尚未定位到具体一行」这句话**本轮已作废** —— 归因在**容器层**，不在共享层 ⇒ 别再在 `service/common` 里找 |

#### T-8 · 响应缺 `Traceparent` —— 🟢 **可修（Go 等价）**，落点 = `service/common`

| | 内容 |
|---|---|
| **Go 侧事实** | `go-zero v1.10.2/rest/handler/tracehandler.go` ⇒ `TraceHandler` 里<br>`propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))`<br>⇒ **把 server span 自己的 span context 注入响应头** ⇒ 客户端收到 `Traceparent: 00-<trace-id>-<span-id>-01`<br>（go-zero 的 `rest.MustNewServer` 默认挂这个 handler ⇒ **10 个 api 全都有**） |
| **RAG 侧落点** | **完全没有** ⇒ 缺 `Traceparent`（message 域 6 档真容器 dump 里 Go 侧 401/404/200 **都带**，`docs/DRIFT/message/api.md:1366/1383/1401`） |
| **修法** | 在 `service/common` 的 HTTP 入口（`HttpMiddleware` 或一个专门的 filter）用**当前的 server span 的 SpanContext** 做 W3C `TraceContext` 注入 ⇒ 写 `traceparent`（+ `tracestate`）响应头<br>⇒ **改 1 处 ⇒ 10 个 api 全生效 ⇒ 不碰任何 api 模块的代码** |
| **⚠️ 注意** | Go 注入的是**当前 server span**（不是入向的 parent）⇒ RAG 侧必须对齐到「**本请求的 server span**」，不能直接回显入向的 `traceparent` |

#### T-8 · ✅ **已修（2026-10-25 第十九轮落地）** —— 响应头补 W3C `Traceparent`

**落点**：`service/common` 新增 `com/rag/common/http/GoStyleTraceparentFilter.java`
（`@Component` + `@Order(HIGHEST_PRECEDENCE + 3)` ⇒ **10 个 api 全生效，零 api 代码改动**）。

**🔴 链上位置是「承重」的，不是随手挑的** —— 三条约束各有 Go 出处，缺一条就产生字节级差异：

| # | 约束 | Go 出处（**消费点**，§3.3.1） | 违反后的差异 |
|---|---|---|---|
| ① | **CORS 必须在本类之前** | `rest/internal/cors/handlers.go:53-67` 的 `cors.Middleware` 对 `OPTIONS` **短路**（`:61-63` 写 204、**不调 next**）⇒ 预检响应**不经过 router** ⇒ **不经过 TraceHandler** | 预检响应**多带**一个 `Traceparent`（Go 没有） |
| ② | **MaxConns/Breaker/Shedding 必须在本类之后** | `rest/engine.go:135` 的 `TraceHandler` 是 native middleware 链**第一个**（`:146/:149/:152` 才是 MaxConns/Breaker/Shedding） | 被拒成 503 的响应**丢头**（Go 带着） |
| ③ | **必须在 `HttpMiddlewareFilter` 之前** | `HttpMiddleware.java:269` 在**容器线程**抓 `Context.current()`（`parentOtelCtx`），`:312` 在 worker 线程 `parentOtelCtx.makeCurrent()` 复原 ⇒ 它给 span 打的属性（`:358/:455/:485` 的 `Span.fromContext`）落在「**进入 HttpMiddleware 时当前的那个 span**」上 | 属性全落到 no-op span 上（本类的 server span 不 current） |

⇒ **实测链序**（`article-api` 真容器，`ArticleApiAssemblyWiringTest` 钉死）：

```
ArticleGoStyleCorsFilter(+1) → GoStylePathNormalizingFilter(+2) → GoStyleTraceparentFilter(+3)
  → GoStyleMaxConnsFilter(+4) → GoStyleBreakerFilter(+5) → GoStyleCpuSheddingFilter(+6)
  → HttpMiddlewareFilter(+7) → GoStyleMaxBytesFilter(+8) → GoStyleJsonBodyLimitFilter(+9)
```

⚠️ **相对顺序没变，变的是整块的取值**（保护层四件套 + `HttpMiddleware` + `MaxBytes` **整体后移**）
—— 因为要给本类腾出「CORS 之后、MaxConns 之前」这个**整数位**。
⚠️ 顺带修掉一个**隐式依赖**：原先 CORS 与 `path.Clean` **同为** `HIGHEST_PRECEDENCE`
⇒ 两者相对顺序靠 **Bean 注册序** ⇒ 现在**各自显式取值**（`+1` / `+2`）⇒ 由 `@Order` 表达。
⚠️ `path.Clean` 与本类的相对位置**不影响任何响应头**：RAG 的 `GoStylePathNormalizingFilter`
**不短路**（只包装 request 后继续下传）⇒ 它自己**不产生响应**。

**🔴 根因不是「忘了写一行」，而是「RAG 侧压根没有 server span」**：
全仓**没有** `micrometer-tracing-bridge-otel`、也没有 `opentelemetry-sdk-extension-autoconfigure`
⇒ Spring Boot 的 tracing 自动配置**不生效** ⇒ `GlobalOpenTelemetry.get()` 是 **no-op**
⇒ `Span.fromContext(ctx).getSpanContext()` 恒为 **INVALID**
⇒ **任何**「从当前上下文取 traceparent 再写出去」的实现都写不出东西。
⇒ 故本类自带一个**私有** `SdkTracerProvider`（**刻意不注册成全局**，理由见类注释），
每个请求起一个 `SpanKind.SERVER` 的 span，再注入。

**对齐的三条语义**（对着 go-zero `rest/handler/tracehandler.go:47-63` 逐条）：

| # | 语义 | Go 出处 | 本实现 |
|---|---|---|---|
| ① | 注入的是**当前 server span**，**不是**回显入向 parent | `tracer.Start(ctx, …)` ⇒ `propagator.Inject(spanCtx, …)` | `PROPAGATOR.inject(Context.current(), …)`（scope 内的当前 context） |
| ② | 注入在**业务执行之前** ⇒ 任何状态码都带 | Inject 在 `next.ServeHTTP` 之前 | 在 `chain.doFilter` 之前 `setHeader` |
| ③ | trace-id **沿用入向**（server span 是入向的子 span） | `propagator.Extract(r.Context(), …)` → `tracer.Start(ctx, …)` | `PROPAGATOR.extract(...)` → `.setParent(parent)` |

**🔴 头名大小写**：Go 的 `propagation.HeaderCarrier.Set` 走 `textproto.MIMEHeader.Set`
⇒ 键被 canonical 成 **`Traceparent`**；而 OTel Java 的 propagator 写的是**小写** `traceparent`，
Tomcat 又**原样**发出 ⇒ 直接照抄会得到 `traceparent: …`（与 Go **字节级不一致**）
⇒ 本类的 setter 主动 canonical 化（**变异 M3 实测**：不 canonical 化时 wire 上就是小写）。

**✅ 守门**（真 Tomcat + **裸 socket**，看**原始字节**）：`service/common` 的
`GoStyleTraceparentFilterTest`（**6 条**）——
trace-id 沿用 / span-id 换新（**证明不是回显**）/ 采样位随入向（`ParentBased` 语义）/
无入向也生成 / 错误状态码也带 / 头名 canonical。
**🟢 变异 3 条全部转红且红在目标断言上**：M1 去注入 · M2 改成回显入向 · M3 去 canonical 化。

**✅ 守门（第二轮 · 链上位置）**：`article-api` 的 `ArticleApiAssemblyWiringTest`
（**+3 条**，共 13 条）——
① 完整链序**逐条钉死**（含本类的位置）· ② `cors < traceparent < maxConns` **成对断言** ·
③ **OPTIONS 预检 204 不带 `Traceparent`**（真容器 ⇒ 证明 CORS 确实在本类之前）·
④ 正常请求的响应**带** `Traceparent`（证明共享层 filter 真进了 api 的链，§3.3）。
**🟢 变异 2 条全部转红且红在目标断言上**：
**M4** 把本类挪到 `MaxConns` **之后**（`+3 → +10`）⇒ 红在「traceparent 必须在 MaxConns 之前」；
**M5** 把 CORS 挪到本类**之后**（`+1 → +10`）⇒ 红在「OPTIONS 预检**不带** Traceparent」。
⚠️ 这两条**只有 article-api 的守门能抓住** —— `service/common` 的 6 条**全绿**
（它们手工把 filter 挂进 Tomcat，**看不见 `@Order`**）⇒ 这正是「链序断言必须放在**真容器**里」的例证。

**⚠️ 一并修掉的共享层缺陷**：`service/common/pom.xml` 里 `opentelemetry-api` 被**手写成 1.62.0**，
而同模块的 context/sdk/sdk-testing 由 BOM（1.44.0）管 ⇒ **本模块 classpath 版本错配**
⇒ 实测运行时 `NoSuchMethodError: TextMapGetter.getAll`（该方法 1.55.0 才有）
⇒ 已统一由 BOM 管（**对 10 个 api 零影响**：它们的 otel 版本由根 pom 的 Boot BOM 1.55.0 管）。

**⚠️ 边界（本轮不碰，已记账）**：

1. **trace 导出链路在 RAG 侧整体缺失**（Go 导出到 jaeger；RAG 侧**没有任何装配** ——
   ⚠️ **措辞订正（2026-10-25 实测 `mvn dependency:tree -pl service/article/article-api`）**：
   `opentelemetry-exporter-otlp:1.55.0` **其实已在 api 的 classpath 上**（Boot BOM 带进来的）
   ⇒ **不是「jar 都没装」，而是「装了 jar 但没人装配它」**
   ⇒ 真正缺的是 **`opentelemetry-sdk-extension-autoconfigure`**（Boot 的 tracing 自动配置所在模块）
   ⇒ ⇒ **一句话**：缺的是**装配**，不是**依赖**
   ⇒ 新登记 **T-11**（见下）—— **另一条独立缺口**，本轮只闭合「响应头」这一维。
2. **容器级 404**（Tomcat error valve 生成的错误页）**不经过 filter 链** ⇒ 任何 filter 都加不上头
   ⇒ **不构成**与 Go 的漂移（Go 的 404 由链内的 `notFoundHandler` 生成）。
3. span 名：Go 用**路由 pattern**，本类用请求 URI ⇒ span 名**不可 wire 观察** ⇒ 本轮不追求一致。
4. 🔴 **「方法不匹配」这个出口，RAG 比 Go 多带一个 `Traceparent`** ⇒ 新登记 **T-12**（见下）。
   ⚠️ 这是**本轮修 T-8 才产生的新差异**（修之前 RAG 压根没有这个头 ⇒ 不存在差异）
   ⇒ **T-8 与 T-12 必须一起读**：T-8 把「绝大多数响应」补齐了，T-12 是剩下那一个出口的残留。

**📌 交各域 owner 的账**：

| 域 | 事项 |
|---|---|
| **favorite** | 代码 / 配置 / 测试 / 台账**全零改动**；但共享层生效 ⇒ favorite 的响应也**多带**了 `Traceparent`（**不是新漂移**：Go 的 favorite api 同样挂 `TraceHandler`）⇒ favorite 也**同吃 T-12** ⇒ 仅需知悉 |
| **like** | 🟢 **`SD-2026-10-24-10` 的描述需订正**（🔴 **不是新漂移**，是既有签字候选的措辞）—— ① `docs/Intentional_Deviations/like/api.md:77-111` 的「性质」写成「`GoStyleRequestDeadline` 的**公开 API 只暴露毫秒**」⇒ **不成立**（`applyTo(stub)` 就是**纳秒**的，`GoStyleRequestDeadline.java:110-120`）；② 其「为什么不修」的理由（`applyTo` 要「读回来」）也**不对** ⇒ 真正的理由在**代码注释**里（`LikeRpcClient.java:108-121`）：**`applyTo` 没有「上界」概念** ⇒ 表达不了 `min(上限, 剩余)` ⇒ 只能读**毫秒**的只读入口<br>⇒ 准确措辞见 §一·**附** ⇒ ⚠️ **该文件不在 global 的写权内 ⇒ 只记账，不改**<br>⇒ 📌 若要**撤销**这条候选（而不是签字认账）：共享层加一个**只读** `remainingNanos()` ⇒ 落 `service/common`（global 写权内）⇒ 但 like 的调用点需该域 owner 配合 ⇒ 且该差异**当前不可观察**（< 1ms）⇒ **不急** |
| **common** | 🟢 **台账里的编号记错了** —— `docs/DRIFT/common/alignment-2026-10-23.md:575` 与 `:600` 把 deadline 那条写成 **`SD-2026-10-23-09`** ⇒ 实际是 **`SD-2026-10-24-10`**（`-09` 是**worker 池饱和的裸 503**，与 deadline 无关）⇒ ⚠️ **该文件不在 global 的写权内 ⇒ 只记账，不改** |
| **article** | ⚠️ **本轮改到了 `article-api` 的代码**：它的 6 个 filter 的 `@Order` **数值变了**（**相对顺序不变**）+ 中间插入 `GoStyleTraceparentFilter` ⇒ **`docs/DRIFT/article/api.md` ~`:1410-1435` 的「真容器实测链序（2026-10-24）」快照已过期**（数值全变、多了 traceparent、且「前两位同级 ⇒ 靠 Bean 注册序定序」**已不成立**）⇒ ⚠️ **该文件不在 global 的写权内** ⇒ **只记账，不改** ⇒ 请 article owner 下次动该域时一并刷新 |

⇒ **三条的处置汇总**：

| 条目 | 性质 | 落点 | 能否现在做 |
|---|---|---|---|
| **T-6** | 真漂移 | `service/common`（Spring converter） | 🟡 **能，但要先撤销 hot 域 `SD-HOT-API-006`**（用户决策） |
| **T-7** | **框架限制** | **Tomcat 容器层** | ❌ **不能**（`service/common` 够不着）⇒ 只能签字认账 |
| **T-8** | 真漂移 | `service/common`（HTTP 入口注入） | ✅ **第十九轮已落地**（不需拍板） |

⚠️ ⇒ **原台账「三条同落点 ⇒ 一次拍板一次改」的处置方式作废** ——
**三条分属三个层次，其中两条可修、一条不可修** ⇒ **必须拆开做**。

---

### T-9 ✅ **已修**（2026-10-25 第三轮 A2）—— 熔断计数键缺 target 前缀（原确认为真漂移，且🔴 **发现 §3.2 缺陷**）

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

### T-11 ✅ **已修（第二十一~二十四轮，2026-10-25）** —— trace **导出**链路（RAG 侧此前整体缺失）

> 🟥 **2026-10-25 订正**：本条原状态是「🔴 第十九轮新发现 · 未修」⇒ **已修**，且**不是误报**
> （Go 侧**确实导出** ⇒ 当时的登记是对的）。
> ⇒ **逐轮落地**：**第二十一轮**（`service/common` 装全局 `OpenTelemetrySdk` + OTLP exporter + 关停 flush）
> → **第二十三轮**（补采样比例 `Telemetry.Sampler` + batcher 协议校验 `Telemetry.Batcher`，并**自曝错账**：原台账声称「非 grpc 的 protocol ⇒ 抛错」而代码里**没这层校验**）
> → **第二十四轮**（**Go 侧活体对拍**：`tools/semantic-probe/trace/`，11 用例 · 8 一致 · 3 已知能力缺口 · **0 漂移**）
> ⇒ **逐条证据 ⇒ [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §九 / §十一 / §十二。

| | 内容 |
|---|---|
| **Go 侧** | `go-zero@v1.10.2/core/trace/agent.go:105-132` 的 `startAgent` 装 `sdktrace.NewTracerProvider(opts...)`（**`:126`**）+ **`otel.SetTracerProvider(tp)`（`:127`）** ⇒ **全局 provider 真的被装上**；`core/trace/agent.go:115-124` **端点非空**才装 exporter（**`:123`** `sdktrace.WithBatcher(exp)`）⇒ **span 真的被导出**；`core/trace/agent.go:100-101` 非法 exporter ⇒ `unknown exporter`；`core/trace/agent.go:43-52` `StartAgent` 用 `sync.Once`（`:48`）⇒ 装不上**只 `logx.Error`（`:50`）继续启动**；`core/trace/propagation.go:8-11` 全局传播器 = `Composite(TraceContext, Baggage)`，**无条件**装<br>Go 各 api yaml：`Telemetry: { Name: …, Endpoint: jaeger:4317, Sampler: 1.0, Batcher: otlpgrpc }` |
| **RAG 侧（实测）** | ① 全仓**无** `micrometer-tracing-bridge-otel`、**无** `opentelemetry-sdk-extension-autoconfigure` ⇒ Spring Boot 的 tracing 自动配置**不生效**<br>② `GlobalOpenTelemetry.get()` 是 **no-op** ⇒ 既有代码里所有 `GlobalOpenTelemetry.get().getTracer(...).spanBuilder(...)`（`RpcInterceptor` / `MqConsumer` / `AdminSpans` / `MessageDomainService` / `UserDomainService` / `FavoriteFactDispatchScheduler`）建出来的 span **全部无效**（`SpanContext` 为 INVALID）⇒ **既没有 span，也没有导出**<br>③ 各 api yaml 里 `otel: { exporter.otlp.endpoint: jaeger:4317, traces.sampler: parentbased_always_on }` **一直是对的** ⇒ 缺的只是**装配**（自动配置 / exporter / 关闭钩子） |
| **可观察差异** | Go 的 trace 在 jaeger 里**可见**；RAG 的 trace **不存在** ⇒ 线上排障时「没有 trace」这件事两侧表现不同 |
| **本轮做了什么** | **只**闭合 T-8 的「**响应头**」那一维：`GoStyleTraceparentFilter` 自带**私有** `SdkTracerProvider`（**不配任何 SpanProcessor**）⇒ span 的 `SpanContext` **有效** ⇒ 响应头能写；但 **span 不会被导出** |
| **为何不全局注册** | `GlobalOpenTelemetry.set(...)` 全局只能设一次 ⇒ 一旦本轮就设，**同 JVM 内**所有 `GlobalOpenTelemetry.get().getTracer(...)` 的调用方行为**全部改变**（从 no-op 变真建 span）⇒ 那是**另一件事**，不该由 T-8 带出来 |
| **建议修法（下一轮）** | ① 在 `service/common` 装一个**全局** `OpenTelemetrySdk`（`ParentBased(TraceIDRatioBased(1.0))` + OTLP exporter + `BatchSpanProcessor`）⇒ 接管 yaml 的 `otel.*` 配置<br>② 补 `SpanProcessor` 的**关闭钩子**（`ObservabilityLifecycle` 已有 `@PreDestroy` 收口线程池的位置）<br>③ ⚠️ 装了之后 **rpc 侧**（`RpcInterceptor` 等）的 span 会从 no-op 变真 ⇒ **必须配套守门**（否则那条变更没人盯）<br>④ ⚠️ **favorite 域零改动**：共享层装配 ⇒ 10 个 api 全部受影响 ⇒ 按 §5.3c 记账并告知 owner |
| **证据边界** | 本条**未实测**「RAG 侧真的没有 span 被导出」—— 依据是**依赖树 + `GlobalOpenTelemetry` 无 provider**（静态取证）。⚠️ 若某处**运行时**装过 provider（进程启动脚本 / 环境变量），本条结论需重判 ⇒ **下一轮第一步应是用真进程验证** |

---

### T-12 🔴 **已定性（第二十六轮 L5）= 出口层级差异 · 共享层改不动** —— 「方法不匹配」这个出口，RAG 比 Go **多带**一个 `Traceparent`

> **本轮（第十九轮）只登记、未修** —— 它是 **T-8 落地后才暴露的残留**（修之前 RAG 压根没有这个头
> ⇒ 不存在差异）⇒ **T-8 与 T-12 必须一起读**。
>
> ✅ **第二十六轮 L5 定性（基于 Go 真源实读）** —— **不是推断，是落到注册点的结论**：
> Go `rest/engine.go:113-115` `handle := chn.ThenFunc(route.Handler)` ⇒ `router.Handle(route.Method, route.Path, handle)`
> ⇒ **含 `TraceHandler` 的链是被「注册进 router」的** ⇒ **router 自己生成的 405 根本不经过它**；
> `rest/router/patrouter.go:59-83` 三条出口里 **`:77-82` 就是「方法不匹配（405）」** ⇒ **绕过 `TraceHandler`**
> ⇒ ⇒ **性质 = 出口层级差异（Go 在 router 层 / RAG 在 `DispatcherServlet` 内）** ⇒ **共享层改不动**
> ⇒ 🏷 **已登记为待签字候选 `SD-2026-10-25-CAND-15`**（`docs/Intentional_Deviations/global/common-tails.md`）。
> ⇒ 教训 ⇒ **PIT-GLOBAL-023**（「这个出口在 XX 层 ⇒ 不经过 X」是推断 ⇒ 必须落到**注册点**）。
> ⚠️ **效应边界（不要越读）**：差异**只**在「方法不匹配」**这一个**出口 —— 404 与正常响应**两侧都带** traceparent。

| | 内容 |
|---|---|
| **Go 侧的三个出口** | `go-zero@v1.10.2/rest/router/patrouter.go:59-83` 的分发有**三条**出口，**只有前两条经过 TraceHandler**：<br>① **路由命中**（`:61-68`）⇒ 路由链（**注册点** `rest/engine.go:113-115`；`TraceHandler` 是 native 链第一个，`engine.go:134-138`）⇒ **带**<br>② **路径都没注册**（`:71-75`）⇒ `pr.handleNotFound` ⇒ `engine.notFoundHandler`（`engine.go:245-251`，**也挂** TraceHandler）⇒ **带**<br>③ **路径命中、方法不对**（**`:77-82`**）⇒ `pr.notAllowed`（article 侧 = `cors.NotAllowedHandler`）或 `w.WriteHeader(405)` ⇒ **两条都在 router 层 ⇒ 都不经过 TraceHandler ⇒ 不带**<br>⇒ 🔴 **注册点才是判据**（不是「router 层」这种层级的说法）：`router.Handle(...)` 注册的是**已被 `TraceHandler` 包过的 handler** ⇒ **router 自己生成的 405 没有被包过** |
| **② 为什么 Go 的 404 也带**（易误判点） | `rest/server.go:58` 的 `NewServer` **默认** `opts = append([]RunOption{WithNotFoundHandler(nil)}, opts...)` ⇒ **每个 api 都装上了 notFoundHandler** ⇒ 未注册路径的 404 **经过**它 ⇒ 带 traceparent。<br>⚠️ 与之相对，**方法不匹配**走的是 `SetNotAllowedHandler`，**全仓 10 个 api 只有 article 注册**（`article/api/article.go:33` 的 `rest.WithCors()` 顺带注册）⇒ 其余 9 个 api 走**默认 405** ⇒ **同样在 router 层 ⇒ 同样不带** |
| **RAG 侧** | 出口③ 落在 `DispatcherServlet` **内**（`article-api` 的 `GlobalExceptionHandler.handleMethodNotSupported` ⇒ 404 空 body）⇒ filter 链**早已跑完** ⇒ 共享层的 traceparent filter **已经注入过头** ⇒ **多带一个 `Traceparent`** |
| **实测证据** | ✅ `ArticleApiAssemblyWiringTest.methodNotAllowedCarriesTraceparentUnlikeGo`（**真 Tomcat**）：`GET /v1/article`（该路径只注册了 POST）⇒ **404 + 空 body + 带 `Traceparent`** ⇒ 与 Go 的同一路径（404 空 body、**无** `Traceparent`）**只差这一个头** |
| **性质** | 🔴 **真字节级差异**（RAG **多**一个头 ⇒ 属「更严/更全」那一侧，**不影响任何业务语义**）<br>🔴 **根因是「出口所在层级不同」**（router 层 vs DispatcherServlet 内）⇒ **共享层改不动**：filter 阶段**无法知道**「这个请求最终会不会落到方法不匹配」—— 那要等 `RequestMappingHandlerMapping` 匹配完才知道，而那时响应已经开始写了 |
| **修它的代价** | 要么在共享层**自建一份路由表**（与各域的 controller 重复维护 ⇒ 新的漂移源）· 要么让**各域把路由元数据**暴露给共享层（⇒ 改 10 个域的代码，且**跨域改动**）⇒ **代价远超「多一个头」的收益** ⇒ 建议**签字认账** |
| **影响面** | **10 个 api 全适用**（共享层）⇒ 但**只影响「方法不匹配」这一个出口**；<br>**404（路径未注册）与所有正常响应都与 Go 一致**（都带）⇒ 别把它读成「RAG 的 traceparent 一律多一个」 |
| **守门** | ✅ `ArticleApiAssemblyWiringTest.methodNotAllowedCarriesTraceparentUnlikeGo` —— **characterization test**（钉的是**实测现状**，不是 Go 的事实；`@DisplayName` 已明说「残留差异」） |
| **证据边界** | ✅ Go 侧三出口的**控制流逐行取证**（**注册点** `rest/engine.go:113-115` + `patrouter.go:59-83` + `server.go:58` + `engine.go:245-251` + `tracehandler.go:61`）· ✅ RAG 侧**真 Tomcat 实测**<br>⚠️ **未做双侧活体对拍**（未起 Go 侧进程）⇒ 「Go 的该响应无 traceparent」是**源码级**结论；如需升级为活体证据，须在 Go 侧起 api 实测同一请求 |

---

### 📎 附 · 2026-10-25 第十九轮末核实：`like` 域 `SD-2026-10-24-10` 的**描述**需订正（**不是新漂移**，是既有签字候选的措辞）

| 项 | 核实结论 |
|---|---|
| **待订正的出处** | ① `docs/Intentional_Deviations/like/api.md:77-111` 的 **`SD-2026-10-24-10`**（⏳ 待签字候选）<br>② 🔴 `docs/DRIFT/common/alignment-2026-10-23.md:575` 与 `:600` 把它**记成了 `SD-2026-10-23-09`** ⇒ **编号记错** —— `-09` 是同档的另一条（**worker 池饱和的裸 503**），与 deadline **无关** |
| **like 档的「性质」字段** | 原文：「**框架限制**（共享组件 `GoStyleRequestDeadline` 的**公开 API 只暴露毫秒**）」 ⇒ 🔴 **不成立** —— 该类有**两个** public API：`remainingMillis()`（毫秒）与 **`applyTo(stub)`（纳秒** —— `GoStyleRequestDeadline.java:110-120` 的 `stub.withDeadlineAfter(remainingNanos, TimeUnit.NANOSECONDS)`**）** |
| **like 档的「为什么不修」** | 原文：「要拿到纳秒精度，只能改走 `applyTo(stub)` 再把 `stub.getCallOptions().getDeadline()` **读回来** —— 多一层间接」 ⇒ 🔴 **理由不对**：`applyTo` **本来就是纳秒的**，**不需要读回来**；真正的理由**在代码注释里是对的**（`LikeRpcClient.java:108-121`）：**`applyTo` 没有「上界」这个概念** ⇒ 表达不了 `min(2000ms, 剩余预算)` ⇒ 必须**读**剩余预算再自己取 min ⇒ 于是只能读**毫秒**的 `remainingMillis()` |
| **真正准确的一句话** | 🔴 **不是「公开 API 只暴露毫秒」，而是「**只读** API 只有毫秒粒度」** —— `remainingMillis()`（毫秒）是唯一的**只读**入口；唯一的纳秒入口 `applyTo` 是**写接口**且**没有上界概念** ⇒ 表达不了 `min(上限, 剩余)` |
| **common 台账那句话** | 「真正只有毫秒的是 `DeadlineState`」 ⇒ ✅ **成立** —— `RpcInterceptor.java:586-630` 的 `DeadlineState` 用 `System.currentTimeMillis()` 存取、`remainingMillis()` 返回毫秒 ⇒ 毫秒是**它**的固有限制，不是 `GoStyleRequestDeadline` 的 |
| **修法（若要真闭合这条，而不是签字认账）** | 在共享层 `GoStyleRequestDeadline` 加一个**只读** `remainingNanos()`（纳秒）⇒ like 域即可算 `min(capNanos, remainingNanos)` ⇒ 这条候选**即可撤销**<br>⚠️ 共享层落 `service/common` ⇒ **global 的写权内**；但 like 域的**调用点**在 `service/like` ⇒ 需与该域 owner 协调（§5.3c）<br>⚠️ 该差异**当前不可观察**（< 1ms；两侧在同一个毫秒窗口内都以 `DEADLINE_EXCEEDED` 收场）⇒ **不急**，且**不动比动更稳** |
| **本轮处置** | ✅ **只订正描述，不改代码、不改他域台账**（§5.3b）⇒ ① 事实与准确措辞写在本档（global 的写权内）② `like` 档与 `common` 档**各需 owner 订正** ⇒ **只记账** |

---

## 二、本轮复核汇总表

| # | 条目 | 复核前 | 复核后 | 本轮动了？ |
|---|---|---|---|---|
| T-1 | Content-Type 大小写 | ⬜ 待复核 | ✅ **已修**（残余 ① **本轮第三轮亦已修**） | ✅ |
| T-2 | MaxConns 作用域 | 🔴 仍未修 | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 | ✅ 指针 |
| T-3 | 503/499 缺 nosniff | ✅ 已修 · 待确认 | ✅ **实测确认已修** | ❌ |
| T-4 | 499 / 2s deadline | ✅ **已闭环** | ✅ **观测面已修**（499 可见）· ✅ **资源面已修**（第十轮方案⑤「主动探测写」⇒ 在飞 gRPC 被提前取消，实测 **461ms**）⇒ 🟡 **残留时序差** ⇒ 已签字 `SD-2026-10-25-04`（2026-10-25 06:40 用户再次明确签字）⇒ ⚠️ 原 `SD-2026-10-25-02` **已撤销**；🟡 官方路径已记为**可选后续优化**（非必选） | ✅ |
| T-5 | MaxConns 作用域 | 🔴 仍开放 | ✅ **已签字**（同 T-2）⇒ 移出本档 | ✅ 指针 |
| T-6 | 成功/失败 Content-Type | 🟡 仍 OPEN | ✅ **已修**（**第二十五轮**：共享层 `GoStyleJsonContentTypeAdvice` ⇒ JSON 响应带 `charset=utf-8` ⇒ 10 个 api 全生效）⇒ ⚠️ 「不可逐字节复刻」**仍成立**（真实原因是 **Tomcat 11 归一化**，不是 Spring 的 `MediaType`）⇒ 残余只差一个 OWS（RFC 9110 不显著）⇒ `SD-HOT-API-006` **已撤销**（用户拍板） | ✅ **已修** |
| T-7 | 状态行 reason phrase | 🟡 仍 OPEN | 🔴 **框架限制（真）** —— 归因在 **Tomcat `Http11OutputBuffer`（容器层）** ⇒ `service/common` 够不着 | ✅ 归因 |
| T-8 | 缺 Traceparent | 🟡 仍 OPEN | ✅ **第十九轮已落地**（共享层新增 `GoStyleTraceparentFilter` ⇒ 10 个 api 全生效、零 api 代码改动）⇒ 🟢 **真 Tomcat + 裸 socket 守门 6 条 + 变异 3 条全红**（含「不是回显入向」「头名 canonical」）<br>⇒ 🟢 **第二轮（链上位置）**：`article-api` 真容器 **+3 条**（完整链序钉死 / `cors < traceparent < maxConns` 成对断言 / **OPTIONS 预检 204 不带 `Traceparent`**）+ **变异 M4/M5 全红**（红在目标断言上）⇒ ⚠️ 这两条**只有 article-api 的守门能抓**（common 那 6 条看不见 `@Order`） | ✅ **已修** |
| T-9 | 熔断计数键 | ⬜ 待复核 | ✅ **已修**（键补 target 前缀）+ §3.2 缺陷一并订正 | ✅ |
| T-10 | UserDomainService 假断言 | ❌ 未销账 | ✅ **已闭环** | ✅ |
| **T-11** | **trace 导出链路整体缺失** | 🟢 **此前无人登记** | ✅ **已修**（**第二十一~二十四轮**：全局 OTel SDK + 采样/batcher 补齐 + **Go 侧活体对拍**）⇒ 见 §一·T-11 | ✅ **已修** |
| **T-12** | **「方法不匹配」出口多带 `Traceparent`** | 🟢 **此前无人登记** | 🔴 **第十九轮新发现（随 T-8 落地暴露），未修**（Go 的该出口在 router 层 ⇒ 不带；RAG 在 DispatcherServlet 内 ⇒ 多带）⇒ **共享层改不动 ⇒ 签字候选**（定性由 L5 落档）⇒ 见 §一·T-12 | 🟢 **签字候选** |

⇒ **首轮净效果**：10 条里 **1 条闭环**（T-10）· **3 条从「待复核」升级为「已确认的真漂移」并补齐 Go 出处**
（T-1 / T-2+T-5 / T-9）· **6 条维持原状**（未展开）。

⇒ **第三轮净效果（2026-10-25）**：**T-1 残余 ① / T-9 / T-4 全部落地**
⇒ 10 条里 **7 条已闭环或已签字**（T-1 + 残余① / T-2 / T-3 / **T-4 观测面 + 资源面** / T-5 / T-9 / T-10）·
**T-6 / T-7 / T-8 维持 🟡 仍 OPEN**（同落点，待用户拍板）。

⇒ **第十九轮净效果（2026-10-25）**：**T-8 落地**（真 Tomcat + 裸 socket 守门 6 条 + 变异 3 条全红）
⇒ 10 条里 **8 条已闭环或已签字** ⇒ **T-6 / T-7 维持**（T-6 需先撤销 hot 域签字 ⇒ 用户决策；T-7 真框架限制）
⇒ **新增 T-11**（trace 导出链路整体缺失，**本轮只登记、未修**）。

⇒ **第二十五轮净效果（2026-10-25）**：**T-6 落地**（用户拍板撤销 `SD-HOT-API-006`）
⇒ ⇒ 共享层 `GoStyleJsonContentTypeAdvice` ⇒ **JSON 响应带 `charset=utf-8`** ⇒ 10 个 api 全生效、零 api 代码改动
⇒ ⇒ **真 Tomcat + 裸 socket 守门 3 条 + 变异 2/2 全红**
⇒ ⇒ 🔴 **顺带自曝错账 #2**：本文档原写的「`SD-HOT-API-006` 的理由已被本仓自己证伪」**不成立**
（真实原因是 **Tomcat 11 归一化** ⇒ 应用层拿不到那个空格 ⇒ 原始理由**成立且更强**）
⇒ ⇒ ⇒ 撤销的性质 = **纯用户决策**（「技术上仍不可逐字节复刻，但选择向 Go 靠拢」）

⇒ **第十九轮 · 第二轮净效果（2026-10-25）**：**T-8 的「链上位置」定案**
⇒ ① 🔴 **链上位置是承重的**（不是随手挑的）：CORS **之前** / MaxConns·Breaker·Shedding **之后** /
`HttpMiddleware` **之前** —— 三条约束各有 Go 出处 ⇒ 整块 filter 的 `@Order` 整体后移
（**相对顺序不变**），并顺带修掉「CORS 与 path.Clean 同级 ⇒ 靠 Bean 注册序定序」的**隐式依赖**
⇒ ② 🟢 `article-api` 真容器 **+3 条**守门 + **变异 M4/M5 全红**（红在目标断言上）
⇒ ③ 🔴 **新增 T-12**（「方法不匹配」出口多带一个 `Traceparent` —— **随 T-8 落地才暴露的残留**，
**本轮只登记、未修**）⇒ **T-8 与 T-12 必须一起读**。

---

## 三、下一轮的排期建议（按 §5.3 的「rpc 必须先于 api」与单写权约束）

| 序 | 条目 | 落点 | 是否需要别的域配合 |
|---|---|---|---|
| ~~1~~ | ~~**T-1**~~ | `service/common/http/GoStyleJsonBodyConverter.java` | ✅ **已完成**（2026-10-25 第二轮） |
| ~~1′~~ | ~~**T-1 残余 ①**~~ | 同上（包一层 `HttpInputMessage`） | ✅ **已完成**（2026-10-25 第三轮）⇒ 顺带修掉 `charset=gbk` 偏差 |
| ~~2~~ | ~~**T-9**~~ | `service/common/protection/GoStyleGrpcClientBreakerInterceptor.java` | ✅ **已完成**（2026-10-25 第三轮）⇒ 身份源 = `Channel.authority()` |
| ~~3~~ | ~~**T-2 / T-5**~~ | —— | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 |
| ~~4a~~ | ~~**T-4**~~ | `service/common/observability/HttpMiddleware.java` | ✅ **已闭环**（第三轮观测面 + 第十轮资源面方案⑤）⇒ 偏离签字 `SD-2026-10-25-04`（2026-10-25 06:40 用户再次明确签字）⇒ 🟡 官方路径 = **可选后续优化**，不返工 |
| ~~4b~~ | ~~**T-8**~~（先做，不需拍板） | `service/common`（HTTP 入口注入 W3C traceparent） | ✅ **已完成**（2026-10-25 第十九轮 · 含第二轮「链上位置」定案）⇒ 残留见 **T-12** |
| 4b′ | **T-12** | 共享层**改不动**（出口层级差异）⇒ 建议**签字认账** | ⚠️ 修它要动 **10 个域**（暴露路由元数据）或在共享层**自建路由表** ⇒ 代价远超收益 |
| 4c | **T-6** | `service/common`（Spring converter 写原始 Content-Type 字符串） | 🟡 可修，**但要先撤销 hot 域 `SD-HOT-API-006`**（用户决策） |
| 4d | **T-7** | **Tomcat 容器层** | ❌ 不可修 ⇒ 只能签字认账 |

⚠️ **排序提醒**：T-1 与 T-9 都改 `service/common` ⇒ **本域独占写点**，别的域此刻在改 common 会撞
（§5.3b 规则 1）⇒ 这几条**必须串行**。

---

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | ✅ **第十九轮 · 第二轮（T-8 链上位置定案 + T-12 登记）** —— ① 🔴 **链上位置是「承重」的**：三条约束各有 Go 出处 —— **CORS 必须在本类之前**（`rest/internal/cors/handlers.go:61-63` 的 `cors.Middleware` 对 `OPTIONS` **短路** ⇒ 预检**不经过 router** ⇒ **不经过 TraceHandler** ⇒ Go 的 204 预检**没有** traceparent）、**MaxConns/Breaker/Shedding 必须在它之后**（`rest/engine.go:135` 的 TraceHandler 是 native 链**第一个** ⇒ 503 响应**也带**头）、**必须在 `HttpMiddlewareFilter` 之前**（`HttpMiddleware.java:269` 在容器线程抓 `Context.current()`、`:312` 在 worker 线程复原 ⇒ 它给 span 打的属性落在「进入时当前的那个 span」上）⇒ 整块 filter 的 `@Order` **整体后移**（CORS `+1` / path.Clean `+2` / **traceparent `+3`** / MaxConns `+4` / Breaker `+5` / Shedding `+6` / HttpMiddleware `+7` / MaxBytes `+8` / JsonBodyLimit `+9`）⇒ **相对顺序不变**；⚠️ 顺带修掉「CORS 与 path.Clean **同级** ⇒ 靠 Bean 注册序定序」的**隐式依赖**（现各自显式取值）② 🟢 `article-api` 真容器 **+3 条**守门（完整链序钉死 / `cors < traceparent < maxConns` 成对断言 / **OPTIONS 预检 204 不带 `Traceparent`** / 正常请求带 `Traceparent`）③ 🟢 **变异 M4/M5 全红且红在目标断言上**（M4 挪到 MaxConns 之后 ⇒ 红在「必须在 MaxConns 之前」；M5 CORS 挪到本类之后 ⇒ 红在「预检不带 Traceparent」）⇒ ⚠️ **这两条只有 article-api 的守门能抓**（`service/common` 那 6 条**看不见 `@Order`** ⇒ 全绿）④ 🔴 **新增 T-12**（「方法不匹配」出口 RAG **多带**一个 `Traceparent`）⇒ 随 T-8 落地才暴露 ⇒ **T-8 与 T-12 必须一起读**；⚠️ 顺带查清一个**易误判点**：Go 的「未注册路径 404」**也带** traceparent（`rest/server.go:58` 的 `NewServer` **默认** prepend `WithNotFoundHandler(nil)`）⇒ T-12 **只**涉及「方法不匹配」这一个出口 ⑤ ✅ **全仓 `mvn -o test -fae` BUILD SUCCESS**（36 模块 / 15:11 / surefire 汇总 **2191 条 0 失败**）⇒ ⚠️ **第一轮那次全仓构建不算数**（构建**进行中**改了源码 ⇒ 绿的是**旧字节码** ⇒ `stat` 比对 class/source mtime 才发现）⇒ **重跑了一遍**才算数（教训已并入 `PIT-GLOBAL-019` 的第 4 种形态） |
| 2026-10-25 | ✅ **第十九轮 · 末（台账描述订正，只记账）** —— 核实 `like` 域签字候选 **`SD-2026-10-24-10`** 的描述 ⇒ 🔴 **「`GoStyleRequestDeadline` 的公开 API 只暴露毫秒」不成立**（`applyTo(stub)` 就是**纳秒**的，`GoStyleRequestDeadline.java:110-120`）⇒ 其「为什么不修」的理由（要「读回来」）**也不对** ⇒ 真正的理由在**代码注释**里是对的（`LikeRpcClient.java:108-121`：**`applyTo` 没有「上界」概念** ⇒ 表达不了 `min(上限, 剩余)`）⇒ 准确措辞 = 「**只读** API 只有毫秒粒度」；⇒ 🔴 `common` 域台账 `docs/DRIFT/common/alignment-2026-10-23.md:575/:600` 把它**记成了 `SD-2026-10-23-09`** ⇒ **编号记错**（`-09` 是 worker 池裸 503 那条）⇒ ⇒ **两个域的台账各需 owner 订正 ⇒ global 只记账、不改**（§5.3b）⇒ 详细事实见 §一·**附** |
| 2026-10-25 | ✅ **第十九轮（T-8 落地）** —— ① ✅ **T-8 落地**（`service/common` 新增 `GoStyleTraceparentFilter` ⇒ **10 个 api 全生效、零 api 代码改动**）：🔴 **根因不是「忘了写一行」，而是 RAG 侧压根没有 server span**（无 `micrometer-tracing-bridge-otel` / 无 otel 自动配置 ⇒ `GlobalOpenTelemetry.get()` 是 no-op ⇒ `SpanContext` 恒 INVALID ⇒ **任何**「从当前上下文取 traceparent 再写出去」的实现都写不出东西）⇒ 故自带**私有** `SdkTracerProvider`（**刻意不注册成全局**：全局只能设一次，一设就改变同 JVM 内所有 `GlobalOpenTelemetry.get().getTracer(...)` 调用方的行为 ⇒ 那是另一件事）② ✅ **对齐 Go 的三条语义**：注入的是**当前 server span**（**不是**回显入向 parent）/ 注入在**业务执行之前**（任何状态码都带）/ trace-id **沿用入向** ③ 🔴 **头名大小写**：Go 的 `propagation.HeaderCarrier.Set` 走 `textproto.MIMEHeader.Set` ⇒ canonical 成 **`Traceparent`**；OTel Java 写的是**小写**且 Tomcat **原样**发出 ⇒ 照抄会**字节级不一致** ⇒ 本类 setter 主动 canonical 化（**变异 M3 实测** wire 上就是小写）④ 🟢 **守门 6 条**（**真 Tomcat + 裸 socket**，看**原始字节**）+ **变异 3 条全部转红且红在目标断言上**（M1 去注入 / M2 改回显 / M3 去 canonical）⑤ 🔴 **一并修掉的共享层缺陷**：`service/common/pom.xml` 的 `opentelemetry-api` 被手写成 **1.62.0** 而 context/sdk 由 BOM **1.44.0** 管 ⇒ **本模块 classpath 版本错配** ⇒ 实测运行时 `NoSuchMethodError: TextMapGetter.getAll`（该方法 1.55.0 才有）⇒ 已统一由 BOM 管（**对 10 个 api 零影响**：它们的 otel 版本由根 pom 的 Boot BOM 1.55.0 管）⑥ 🟢 **新增 T-11**（**trace 导出链路整体缺失** —— RAG **缺装配**（`opentelemetry-sdk-extension-autoconfigure` 不在 ⇒ Boot 的 tracing 自动配置不生效；⚠️ exporter 的 jar 是在的）⇒ span 建了也无人收 ⇒ **本轮只登记、未修**）⑦ ⚠️ **边界**：**容器级 404**（Tomcat error valve）**不经过 filter 链** ⇒ 加不上头 ⇒ **不构成**与 Go 的漂移（Go 的 404 由链内 `notFoundHandler` 生成） |
| 2026-10-25 | ✅ **第十轮（T-4 资源面已修 · 方案⑤）** —— 用户 2026-10-25 拍板「**CAND-12：主动探测写（周期 flush()）（签字偏离）**」⇒ 🔴 **「在飞 gRPC 不会被提前取消」不再是事实** —— 落地**方案⑤**：① `GoStyleClientDisconnectProbe`（反射取 Tomcat `SocketWrapper`，`write(true, "HTTP/1.1 103 Early Hints\r\n\r\n")` **+ `flush(true)`** ⇒ 断连时抛 `IOException`；两个前置守卫：非 HTTP/1.1 跳过、`isCommitted()` 跳过；探测间隔默认 500ms，system property 可覆盖，**未动任何 `application.yml`**）② `HttpMiddleware` 单次 `await` ⇒ **切片等待 + 周期探测**（总预算按真实经过时间重算 ⇒ **不延长不缩短**）③ worker 线程上 `io.grpc.Context.withCancellation()` + `attach/detach` ⇒ 探针判定断连即 `cancel(null)` ⇒ **在飞下游 RPC 立刻中止**（grpc-core 1.63 `ClientCallImpl:363` `context.addListener(this, …)`）④ 🟡 **残留时序差**：取消延迟 **461ms**（Go 1~3ms）⇒ 签字记入 `SD-2026-10-25-04`（含 wire 偏离：客户端先收到 N 行 `103 Early Hints`）
| 2026-10-25 | ✅ **第九轮（T-6 / T-7 / T-8 归因定位）** —— 🔴 **原台账的「落点 `GoStyleRouterErrorResponses.java:83`」是错的**（该文件只管 404/405 且已逐字节对齐）⇒ 三条**分属三个层次**：**T-6** 落点 = Spring `MappingJackson2HttpMessageConverter`（Go = go-zero `rest/internal/header/headers.go:17` 的 `application/json; charset=utf-8`，**带空格**）⇒ **可 Go 等价修**（写原始字符串，绕开 `MediaType.toString()`）🟡 **但 hot 域 `SD-HOT-API-006` 的「不可逐字节复刻」前提已被本仓自己证伪**（`GoStyleRouterErrorResponses` 对 text/plain 就是这么干的）⇒ 需先撤销该签字；**T-7** 落点 = **Tomcat `Http11OutputBuffer`（容器层）** ⇒ 🔴 **真框架限制**，`service/common` 够不着 ⇒ 「归因尚未定位」这句话作废；**T-8** Go 出处 = go-zero `rest/handler/tracehandler.go` 的 `propagator.Inject(spanCtx, HeaderCarrier(w.Header()))` ⇒ RAG **完全没有** ⇒ 落点 = `service/common` HTTP 入口 ⇒ ✅ **可 Go 等价修且不需拍板** ⇒ ⇒ **「三条同落点一次改」的处置作废，必须拆开**
| 2026-10-25 | **第三轮（A1 / A2 / A3 / A4 四项落地）**。① **T-1 残余 ① 已修** —— 包一层 `SanitizedJsonContentTypeInputMessage` ⇒ 畸形头不再抛 + `charset=gbk` 回归 UTF-8；守门扩到 8 条 + 变异双向转红 ② **T-9 已修** —— 熔断键补 target 前缀（身份源 = `Channel.authority()`，因 grpc-java 1.63 的 `ManagedChannel` 无 `getTarget()`）；守门 6 条 + 变异转红（第一版守门有洞 ⇒ 已改为只调 `interceptCall` 再看 `trackedMethods()`）③ **T-4 观测面已修** —— 499 判据从「worker 内写失败」改成「**filter 写 socket 失败**」；配真 Tomcat 守门 3 条 + M1/M2/M3 三组变异全转红；⚠️ 顺带修掉「提前 flush 把 `Content-Length` 退化成 chunked」这个**新引入的 wire 差异**；🔴 残留「在飞 gRPC 不被提前取消」⇒ **框架限制** ⇒ `SD-2026-10-25-CAND-12`（待签字）④ **CYCLE-002 普查** —— `service/common` 的 `@Deprecated` 归零、无反向 import 业务域、无跨域 `{@link}` ⇒ **0 命中** ⇒ 落成可重跑守门 `tools/difftest/check-cross-domain-holds.py` |
| 2026-10-25 | **第二轮（T-1 修复）**。T-1 改为**取原始头值**（`getFirst(CONTENT_TYPE)`）⇒ 大小写敏感判定与 Go 对齐；配真 `HttpHeaders` 守门测试 6 条 + 变异测试（转红）。⚠️ 登记 **T-1 残余 ①**（解析层仍走 MediaType ⇒ 畸形头仍抛，Go 不会）。🔴 新增 **X-9**（3 份生产复制品）⇒ [`cross-domain.md`](cross-domain.md)。T-2 / T-5 **用户签字** ⇒ 转 `SD-2026-10-25-01` 并移出本档 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 T-1~T-10 逐条复核结论；纠正「Go 出处在项目树里」的方法论错误（真源分 L1 项目层 / L2 go-zero 框架层）；确认 T-1 / T-2 / T-5 / T-9 为真漂移；发现 T-2/T-5 档位错置、T-9 的 §3.2 缺陷；T-10 转 [`cycle-deps.md`](cycle-deps.md) |
