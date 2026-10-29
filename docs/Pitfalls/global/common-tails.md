<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · common 尾巴踩坑档（common-tails）

---

## PIT-GLOBAL-001 🔴 **查 Go 出处时，只在项目树里 grep 一定会落空**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— Go 出处要分 L1/L2 两层 ⇒ 本轮每条 Go 出处仍须标层，别只 grep 项目树。
> **旧号**：`PIT-GLOBAL-001` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-01`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §现象 ＋ `docs/DRIFT/global/cross-domain.md` §一·1.1

### 现象

`global` 台账前几版给 T-1 / T-2 / T-5 / T-9 写的 Go 出处都形如「Go 侧 `service/common/...`」。
本轮逐条复核时，**这些位置在 Go 项目树里一个都不存在**：

| 台账写的 | 项目树实测 |
|---|---|
| `withJsonBody` | ❌ `grep -rn "withJsonBody" service/` → 空 |
| `MaxConnsHandler` | ❌ `grep -rn "MaxConns" service/` → 只命中 `knowledge` 域（**范围外**） |
| `path.Join(cc.Target(), method)` | ❌ `grep -rln "reaker" --include=*.go .` → **全仓空** |
| `UnmarshalJsonMap` | ❌ 空 |

⇒ 差点被误判成「台账幻觉」或「Go 侧没有这个行为 ⇒ 无差异」。

### 根因

**真源有两层**，AGENTS.md §3.1 的「Go 为唯一基准」没说清是哪一层：

| 层 | 位置 | 覆盖 |
|---|---|---|
| **L1 项目层** | `Sea-RideTheWind_golang/service/**` | 业务代码。`service/common` 只有 `logger` / `response` / `snowflake` / `observability` / `communityfact` |
| **L2 框架层** | `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` | **请求保护层四层**、HTTP body 解析、gRPC 中间件（超时/熔断/降载） |

⇒ Go 侧 `service/common` **根本没有** `http/` 与 `protection/` 子包
（`git log --all --diff-filter=A -- "service/common/http/*" "service/common/protection/*"` → **空**，从未有过）。
⇒ **RAG 的 `service/common/http/` + `service/common/protection/` 对应的是 go-zero，不是 Go 项目的 `service/common`。**

### 正确做法

```bash
GZ="$(go env GOMODCACHE)/github.com/zeromicro/go-zero@v1.10.2"   # ⚠️ 版本取自 go.mod
grep -rn "MaxConnsHandler" "$GZ/rest/"
grep -rn "path.Join" "$GZ/zrpc/internal/clientinterceptors/"
```

⚠️ **版本必须从 `go.mod` 读**（本项目是 `v1.10.2`），模块缓存里同时躺着 `v1.10.0` / `v1.10.2` / `v1.10.3` ⇒ 读错版本同样是漂移。

### 判定口诀

> **「请求保护层」「body 解析」「RPC 中间件」这三类 ⇒ 先去 L2（go-zero）找。**
> **「业务逻辑」「错误码」「表结构」「proto」⇒ L1（项目树）。**

### 连带教训（AGENTS.md §3.1 的老问题，这里再撞一次）

台账里的「Go 出处」也可能**本身就是错的** ⇒ 必须**回到 Go 源文件 line-by-line 复核**，
不能拿台账当基准（§3.1 明令：不要拿「现有 RAG 代码」或「注释」当对照基准）。
本轮就有两处 RAG javadoc **照抄了 Go 那一行、却没实现它**（见 `DRIFT/global/common-tails.md` T-9）。

### 出处

- 踩坑位置：`docs/DRIFT/global/common-tails.md` §〇、T-1、T-2/T-5、T-9
- 方法论归档：`docs/DRIFT/global/cross-domain.md` CD-8
- 2026-10-25，`align/global` @ `58e8314`

---

## PIT-GLOBAL-002 ⚠️ **「档位错置」：代码自称「有意」，但没人签字**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮动作**就是逐条重核签字** ⇒「代码自称『有意』但没人签字」正是本轮要查的形态。
> **旧号**：`PIT-GLOBAL-002` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-02`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置

### 现象

`service/common/protection/GoStyleMaxConnsFilter.java` 的 javadoc 自称：

> 「本类取**全局一个闸门**……**此处为有意的表达层收敛，已在此显式留痕**，不是等价性的隐藏缺口。」

而 `global` 台账把同一条记为 🔴 **仍未修**。
⇒ **同一件事，两个互斥的结论。**

### 根因

§5.3b 规定三档台账是**互斥**的，判据只有一条：

> **这条差异要不要「修回去」？要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations。**

而「有意」这件事**需要签字**（`docs/Intentional_Deviations/`）。
⇒ 这条差异**既不在 DRIFT 的「要修」侧，也不在签字档** ⇒ 是个**无归属的第三态**。
⇒ 代码注释里的「有意」**不构成签字** —— 注释是实现者的自述，不是决策记录。

### 正确处置

1. 发现代码自称「有意」而台账记「未修」⇒ **先判档位归属**，不要急着改代码；
2. 归 DRIFT ⇒ 修；
3. 归 Intentional_Deviations ⇒ **要用户签字**（签字权不在 agent 手上）⇒ 落 `docs/Intentional_Deviations/global/`，并从 DRIFT 侧移除；
4. ⚠️ **不许因为「代码里写了有意」就当它已获批**（§5.3b 的核心告诫：
   「不要因为签了本条就把它当成已解决」是同一条纪律的镜像面）。

### 出处

- 踩坑位置：`docs/DRIFT/global/common-tails.md` T-2 / T-5「附带发现：档位错置」
- 2026-10-25，`align/global` @ `58e8314`

---

## PIT-GLOBAL-007 🔴 **Servlet 容器不向应用暴露「客户端已断连」信号**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不处理断连取消**（T-4 资源面已由 `SD-2026-10-25-04` 认账 ⇒ 本轮不改行为）⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-007` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-07`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §⚠️ 2026-10-25 第八轮订正（Tomcat 源码同证）

### 现象

想复刻 Go 的「客户端断开 ⇒ 立刻取消在飞工作 ⇒ 记 499」，
第一反应是走 Servlet 的 **async + `AsyncListener`**（Servlet 规范里唯一像样的事件源）。

### 实测（真 Tomcat 11.0.24，嵌入式）

Servlet 里 `request.startAsync()` + 注册 `AsyncListener`，
客户端用裸 socket + `SO_LINGER{1,0}` 强制 **RST**：

| 回调 | 触发？ |
|---|---|
| `onError` | ❌ **不触发** |
| `onTimeout` | ❌ 不触发 |
| `onComplete` | ❌ 不触发 |

### Tomcat 源码同证

- `org/apache/coyote/AbstractProcessorLight.java:62`
  ⇒ 收到 `SocketEvent.DISCONNECT` 时明写 **"Do nothing here, just wait for it to get recycled"**；
- `org/apache/catalina/connector/CoyoteAdapter.java:140-160`
  ⇒ `asyncConImpl.setErrorState(t, true)`（唯一会 fire `onError` 的地方）
  **只在「非容器线程上的 I/O 错误」时触发** —— 即「**写失败**」，**不是「对端消失」**；
- `org/apache/catalina/connector/Request.java`
  ⇒ **没有任何 socket 访问器** ⇒ 应用层也**无法自己轮询**「对端还在不在」。

### 正确处置

⇒ 断连的**唯一可观测时刻** = 「**往 socket 写响应失败**」。
⇒ 499 的判据因此必须落在 **filter 线程的写**上，而不是 worker 里的写
（worker 写的是内存缓冲，socket 是 filter 写的 ⇒ 见 `docs/DRIFT/global/common-tails.md` T-4）。

### 教训

「Servlet 规范里有 `AsyncListener`」**不等于**「它会告诉你对端断了」——
`AsyncListener` 报的是**服务端这一侧**的错误（写超时、写失败、异步派发出错），
**不是**「客户端消失了」这件事。
⇒ 断言任何「容器会通知你客户端断连」的设计前，**先做一次真容器探针**（§3.3）。

### ⚠️ 2026-10-25 第八轮订正：**「容器不给信号」≠「生态做不到」**

⇒ 用户质询「springboot 做分布式肯定有办法」⇒ **成立**。
⇒ 本坑的**正确边界**是：**「Tomcat 的 Servlet 层不向应用暴露断连信号」**，
**不是**「Java/Spring 生态做不到」⇒
⇒ **6 条路径全查**（源码 + 2 个真 Tomcat 探针）后：

| 路径 | 判定 | 关键实测 |
|---|---|---|
| 轮询 Tomcat 内部 `SocketWrapperBase.isClosed()` | ❌ **否决** | 反射链**通**（`RequestFacade`→`Request.getCoyoteRequest()`→字段 `hook`→`AbstractProcessor.getSocketWrapper()`），但实测**滞后 5.6 秒**（客户端 986ms 断 ⇒ 6622ms 才翻 true）⇒ 因为它只在 Tomcat **真正 close socket** 时才置位 ⇒ **比主动写还慢** |
| **主动探测写**（周期 `flush()`） | ✅ **有效** | 客户端 986ms 断 ⇒ `flush()` **1649ms** 抛 `ClientAbortException: Broken pipe`（`catalina/connector/OutputBuffer.java:327-331` 包装）⇒ 延迟 ≈ 探测周期；**代价**：必须提前提交响应 ⇒ `Content-Length` → chunked ⇒ **wire 差异** |
| HTTP/2 `RST_STREAM` | 🟡 可行但**偏离 Go** | Tomcat **真触发 `AsyncListener.onError`**（`Http2UpgradeHandler.java:1808-1822` → `http2/Stream.java:216-226,1461-1494` → `AbstractProcessor.java:646-652` → `CoyoteAdapter.java:140-157`）；HTTP/1.1 **无此路径**；⚠️ **Go 侧实测是明文 HTTP/1.1** ⇒ 换协议 = 偏离 Go |
| 换 Jetty / 换 WebFlux+Netty | 🟡 可行 | Jetty `HttpChannel.Listener#onAbort()`；Netty `channelInactive`；**代价** = 换运行时 / 换栈 |
| 缩短在飞 gRPC 的 deadline | ✅ **零改动可用** | 断连后浪费 = 剩余 deadline；**零 wire 差异、零签字** |

⇒ **教训（本坑的真正教训，比原版更宽）**：
**「我实测了 A 组合下拿不到信号」⇒ 只能推出「A 组合下拿不到」**，
**推不出「生态层面做不到」** ⇒
⇒ **定性前先问一句：「我试的是不是唯一可能的组合？」**
（这次组合轴 = 容器 × 协议版本 × 同步/异步 × 应用栈，**四轴**都还有别的取值。）

### 出处

- 踩坑位置：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（T-4 的修法选型）
- 一次性探针：`/tmp/probe-async/AsyncDisconnectProbe.java` + `/tmp/probe-tomcat/DisconnectProbe.java`（均不进仓）
- 关联：`docs/DRIFT/global/common-tails.md` T-4 ③；`docs/Intentional_Deviations/global/common-tails.md` `SD-2026-10-25-02`
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-008 🔴 **「写失败」观测不到 ⇒ 必须显式 flush；flush 又会把 `Content-Length` 退化成 chunked**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不改** `ObservabilityResponseWriter` ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-008` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-08`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置（两步，缺一不可）

### 现象

「客户端断连 ⇒ 写 socket 失败 ⇒ 记 499」这条链，第一版**跑不出 499**：
客户端明明 RST 了，记录里还是 **200**。

### 根因（真 Tomcat 11.0.24 实测）

Tomcat 的 `OutputBuffer` 默认缓冲 **8192 字节** ⇒ 小 body 写进去**只进了缓冲区**，
**socket 一个字节都没发** ⇒ 「写」根本没发生 ⇒ **不抛异常** ⇒ 499 分支永远进不去。

⇒ 必须**显式 `flush()`**（⇒ `OutputBuffer.doFlush(true)` ⇒ 真网络 flush ⇒
写失败被 Tomcat 包成 `ClientAbortException` 抛出来）。

### ⚠️ 但 flush 会引入**新的 wire 差异**

`flush()` ⇒ 响应**提前提交** ⇒ Tomcat 再也没机会在收尾时按缓冲内容算 `Content-Length`
⇒ 响应退化成 **`Transfer-Encoding: chunked`**；
而 **Go 的 `net/http` 对「装得下」的响应体是算 `Content-Length` 的**
⇒ ⇒ **这就是一条新增的 wire 差异**（比原问题更难查）。

### 正确处置（两步，缺一不可）

1. **先自己定 `Content-Length`**（`setContentLength(body.length)`），
   阈值取 Tomcat 自己的响应缓冲 8192；且 **1xx / 204 / 304 不设**（按 HTTP 语义不带 body）；
2. **再 `flush()`**。

⇒ **改前 vs 改后逐字对比（真 Tomcat）**：

| 响应 | 改前 | 改后 |
|---|---|---|
| 有 body（23 字节） | `Content-Length: 23` | `Content-Length: 23` ✅ |
| 空 body | `Content-Length: 0` | `Content-Length: 0` ✅ |
| 204 | 无 `Content-Length`、无 `Transfer-Encoding` | 同左 ✅ |

### 教训

「把响应提前 flush 出去」不是一个中性的动作 —— 它会**提前提交响应**，
从而**改掉 framing**（`Content-Length` vs `chunked`）。
⇒ 任何「为了观测而提前提交」的改动，都必须**同时把 framing 显式钉死**，
并配一条**逐字对比 framing 的守门**（否则回归会以「客户端仍能正常解析」的形式**静默通过**）。

### 出处

- 踩坑位置：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（`flushCaptured`）
- 守门：`service/common/src/test/java/com/rag/common/observability/HttpClientDisconnectRealTomcatTest.java`
  ⇒ `responseFramingStaysContentLength`（变异 **M3** 去掉 `setContentLength` ⇒ 转红）
- 关联：`docs/DRIFT/global/common-tails.md` T-4 ④
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-009 ⚠️ **显式声明一个 test 作用域依赖，会把 compile classpath 上的同名 artifact 顶掉**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不改 pom** ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-009` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-09`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置

### 现象

给 `service/common` 加一个 test 作用域的 `jakarta.annotation:jakarta.annotation-api`
（为了真 Tomcat 启动），结果 **主源码编译失败**：

```
ObservabilityLifecycle.java:[5,26] 程序包jakarta.annotation不存在
ObservabilityLifecycle.java:[52,6] 找不到符号: 类 PreDestroy
```

### 根因

`jakarta.annotation-api` 本来就**由 `spring-boot-starter`（compile 作用域）传递进来**，
而 `ObservabilityLifecycle` 的 `@PreDestroy` 用的正是它。
⇒ 一旦把它**显式**声明成 `test` 作用域，Maven 的「**最近者优先**」规则
⇒ **本模块的直接声明**（test）**压过**传递依赖（compile）
⇒ **compile classpath 上这个 artifact 消失了** ⇒ 主源码编译失败。

### 正确处置

- 要么**根本不声明**（让 `spring-boot-starter` 的传递依赖顶着）；
- 要么声明成 **compile**（与既有解析结果一致）。

⇒ 本轮选了「**不声明**」：真 Tomcat 需要的 `jakarta.annotation.Resource`
由 `spring-boot-starter` 的传递依赖提供（compile 作用域 ⇒ 测试期也在 classpath 上）。

### 教训

加 test 作用域依赖前，先确认**它没有以更近的路径提供同名 artifact**。
⇒ 一旦 artifact 坐标相同，**作用域就变成了「谁更近谁说了算」**，
⇒ 「我只是想加一个测试期依赖」可能**悄悄改掉主源码的编译类路径**。

### 出处

- 踩坑位置：`service/common/pom.xml`
- 关联：`docs/DRIFT/global/common-tails.md` T-4 ⑤（守门落在 `service/common` ⇒ 需要真 Tomcat）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-023 🔴 **「这个出口在 XX 层 ⇒ 不经过 X」是推断，不是证据** —— 「层」的判断必须落到**注册点**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要判「某出口是否经过某层 / 某闸」⇒「层」的判断必须落到**注册点**，不能靠推断。
> **旧号**：`PIT-GLOBAL-023` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-23`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §根因 ＋ `docs/DRIFT/global/vs-common.md` §4.11 G4-A4-05

### 现象（2026-10-25 第二十六轮 L5 实测）

T-12 的判定要立在一句话上：

> 「Go 的『**方法不匹配**』这个出口在 **router 层** ⇒ 不经过 `TraceHandler` ⇒ 不带 `Traceparent`。」

⇒ 如果**只读到** `rest/router/patrouter.go:59-83` 的出口分支（`pr.notAllowed.ServeHTTP` / `w.WriteHeader(405)`），
这句话就只是**听起来合理的推断** ——
`TraceHandler` 到底是挂在 router **外面**（包住整个分发 ⇒ 那 405 **也会**经过它 ⇒ 结论相反），
还是**挂在每条路由里面**（被注册进 router ⇒ router 自己生成的 405 **不会**经过它），
**光看 `rest/router/patrouter.go:59-83` 看不出来**。

### 根因

go-zero 的 `TraceHandler` **不是**一个「全局中间件」，它是**每条路由的 handler 链的一部分**：

```go
// go-zero@v1.10.2/rest/engine.go:113-115
handle := chn.ThenFunc(route.Handler)
return router.Handle(route.Method, route.Path, handle)
```

⇒ `chn` 的**第一个**就是 `handler.TraceHandler(...)`（`rest/engine.go:134-138`），
但这条链是被**注册进 router** 的 ⇒ **router 自己产生的响应（405）根本不经过它**。

⇒ ⇒ 「层」这个说法，必须落到**注册点**才站得住：
**注册进去的 handler 内部产生 ⇒ 有**；**分发器自己产生 ⇒ 没有**。

### 正确做法

判定「某个响应头 / 副作用在某个出口是否发生」时：

1. **逐条枚举分发器的出口**（不要只测「命中路由」那一条）；
2. 对每条出口回答**同一个问题**：**这个响应是在「被注册进去的 handler」里产生的，还是在「分发器自己」里产生的？**
3. **别忘了「分发器自建链」的那条** —— 本条的出口②（404）之所以**带** `Traceparent`，
   就是因为 go-zero 在 `rest/engine.go:245-251` 的 `notFoundHandler` 里**自建了一条以 `TraceHandler` 开头的新链**；
   而 `rest/server.go:58`（`opts = append([]RunOption{WithNotFoundHandler(nil)}, opts...)`）
   让**每个 api 默认**都装上了它 ⇒ **默认 404 也是带 traceparent 的**。

⇒ 落到本条的具体行号（**判定所需的全部证据**）：

| 事实 | 出处 |
|---|---|
| 含 `TraceHandler` 的链被**注册进 router**（⇒ 405 绕过它） | `rest/engine.go:113-115` |
| `TraceHandler` 是 native 链**第一个** | `rest/engine.go:134-138` |
| 分发器的**三条出口** | `rest/router/patrouter.go:59-83`（`:61-68` 命中 / `:71-75` 404 / `:77-82` 405） |
| 404 走**自建的** TraceHandler 链 | `rest/engine.go:245-251`（`:248`） |
| 每个 api **默认**装了 notFoundHandler | `rest/server.go:58` |
| 注入点在**每路由链内部** | `rest/handler/tracehandler.go:61` |

### 教训

**「层」的判断必须落到「注册点」** —— 否则「router 层 ⇒ 不经过」只是一个**听起来合理**的说法，
而它的**反面**（router 层 ⇒ 也会经过）在另一种实现下**同样成立**。

⚠️ 同族：**PIT-GLOBAL-015**（探针测的层 ≠ 端到端跑的层）——
那条讲「**测**的层」，本条讲「**注入发生在哪一层**」；
两者指向同一条纪律：**链路有分层 ⇒ 必须逐层点名，不能靠「层」的直觉。**

### 出处

- 首踩位置：2026-10-25 第二十六轮（L5 lane `align/global-signoff`）—— 给 T-12 定性时；
- 关联：`docs/DRIFT/global/common-tails.md` §一·T-12 ·
  `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-CAND-15` ·
  `docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-015（探针测的层 ≠ 端到端跑的层）

---

## PIT-GLOBAL-024 🔴 **阻塞式启动器 + 句柄设在「返回之后」⇒ 句柄永远拿不到 ⇒ 调度器跨用例泄漏 ⇒ 守门失真**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要核 `DRIFT-CF-018` 的共享层半边是否真修好 ⇒ 该坑的形态就在那。**共享层半边已修**（阻塞重载标 `@Deprecated` ＋ 提供「调用方先造句柄」的重载 ＋ `runAsync` 路径 ＋ 守门 `DispatcherHandleLifecycleGateTest`）；🔴 **但 favorite 域半边仍未修**（仍调阻塞版 `Dispatcher.run(...)`）⇒ 触发条件仍在。
> **旧号**：`PIT-GLOBAL-024` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-24`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java:109` / `:113`（`@Deprecated`）＋ `:149` / `:221`（调用方造句柄 / `runAsync`）＋ `docs/DRIFT/global/vs-common.md` §4.18.1；🔴 残留：`service/favorite/favorite-rpc/.../fact/FavoriteFactDispatchScheduler.java:119`

### 现象

2026-10-25 第二十五轮全仓回归里，`LikeFactDispatchContractGateTest.failedDeliveryBreaksTheInnerBatchImmediately`
**偶发红**（上一轮全绿、这一轮红），失败信息：

```
expected: 0
 but was: 1
[毒丸失败后同轮 break ⇒ msg-02 不该被尝试过（应仍是 pending=0）]
```

### 根因（**不是生产缺陷，是守门自身的缺陷**）

`service/common` 的 `Dispatcher` 有两个入口：

| 入口 | 语义 | 句柄什么时候拿得到 |
|---|---|---|
| `Dispatcher.run(...)` | **阻塞式** —— 在**调用线程**上跑整个循环，**直到 `stop()` 被调用才返回** | **永远拿不到**（除非循环先结束） |
| `Dispatcher.runAsync(...)` | **非阻塞** —— 在独立线程跑循环，**进入循环之前**就把句柄发布出去 | 立刻拿到 |

守门当时的写法是：

```java
Thread runner = new Thread(() -> {
    Dispatcher.DispatcherHandle handle = Dispatcher.run(...);   // ← 阻塞，直到 stop 才返回
    handleRef.set(handle);                                       // ← 只有 run 返回后才设
}, "like-contract-dispatcher");
runner.start();
```

⇒ `run` **永不返回** ⇒ `handleRef.set(...)` **永不执行** ⇒ `handleRef` 永远是 `null`
⇒ `@AfterEach` 的 `handleRef.getAndSet(null)` 每次都拿到 `null` ⇒ **调度器永远停不掉**
⇒ **每个用例都往下一个用例泄漏一个还在 1s 一跳的调度器线程**。

### 为什么它表现为「偶发红」，而且红得莫名其妙

泄漏的调度器**仍在领行**（`FOR UPDATE SKIP LOCKED`，1s 一跳）⇒ 它和本用例的调度器**并发抢行**：

1. 泄漏的调度器领到毒丸 `msg-01` ⇒ 把它标成 `delivery_status=1`（**在途**）；
2. 本用例的调度器再领行时，`msg-01` 已被标成 1 ⇒ 被排除 ⇒ **领走了 `msg-02`**；
3. ⇒ 「毒丸失败 ⇒ 同轮 break ⇒ 下一条一次都不许被试」这条断言就**因为并发**、而不是因为 break 而变红。

⚠️ 更糟的是它**污染兄弟用例**：第 2 个用例断言「每轮 16 条」，而第 1 个用例泄漏的调度器也在同时领行
⇒ 16 条这个数是**两个调度器凑出来的** ⇒ 那条断言即使绿，**测的也不是它声称在测的东西**。

### 正确处置

```java
// ✅ 守门必须用 runAsync：句柄立刻可拿 ⇒ @AfterEach 真的能停
Dispatcher.DispatcherHandle handle = Dispatcher.runAsync(
        Context.current(), deliveryService::dispatchFactOnce, sender, LOG, false, false);
handleRef.set(handle);
```

⇒ 生产路径（`LikeFactDispatchScheduler` / `CommentFactDispatchScheduler`）**本来就是 `runAsync`** ⇒ **没有这个缺陷**。
⇒ ⚠️ 但 `FavoriteFactDispatchScheduler`（**favorite 域，global 零写权**）用的是**阻塞式 `Dispatcher.run`**，
不过它的触发点是 `@EventListener(ApplicationReadyEvent)`（**不是** `@PostConstruct`）
⇒ **上下文已刷新** ⇒ 不会卡住启动，但会**占住主线程** ⇒ **记账 + 告知 owner**，global 不改。

### 教训

**「拿到句柄」这件事，要先确认启动器是阻塞式还是非阻塞式** ——
阻塞式入口 + 「句柄设在返回之后」= **句柄永远拿不到**，而代码读起来完全正常（编译过、类型对）。

⚠️ 同族：**PIT-GLOBAL-009**（显式声明 test 作用域依赖把 compile classpath 上的同名 artifact 顶掉）
—— 那条是「**声明看起来对、实际被顶掉**」；本条是「**赋值看起来对、实际永不执行**」
⇒ 两者都是「**代码写成了这样，但那一行永远跑不到 / 跑到的不是它**」。

### 出处

- 首踩位置：2026-10-25 第二十五轮全仓回归（`mvn -o test -fae`）——
  `LikeFactDispatchContractGateTest` 偶发红 ⇒ 按「**先归因再修**」做结构 diff ⇒ 定位到句柄拿不到；
- 定性依据：`service/common/src/main/java/com/rag/common/communityfact/Dispatcher.java`
  的 `run`（阻塞）与 `runAsync`（句柄在进循环前发布）·
  `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeFactDispatchScheduler.java`（生产路径用 `runAsync`）；
- 守门：`service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeFactDispatchContractGateTest.java`
  （`startDispatcher()` 改用 `runAsync`）；
- 关联：`docs/DRIFT/global/lanes/L1-delivery.md` §十三（第二十五轮 T-6）。

---

## PIT-GLOBAL-025 🔴 **MockMvc 与真 Tomcat 是「两条」观测路径 ⇒ 共享层写的响应头在两条路径上表现不同**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要引用「真 Tomcat 守门」的结论 ⇒「MockMvc 与真 Tomcat 是两条观测路径」仍需排除。
> **旧号**：`PIT-GLOBAL-025` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-25`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §根因 ＋ `docs/Pitfalls/common/http.md`（E-1 案例）

### 现象

2026-10-25 第二十五轮全仓回归里，**favorite 域**的一条守门红了：

```
FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant
Response header 'Content-Type' expected:<application/json;charset=utf-8> but was:<application/json; charset=utf-8>
```

⇒ 而本轮的 T-6 修法在**真 Tomcat** 守门里是**全绿**的（`GoStyleJsonContentTypeCharsetTomcatTest` 3 条全绿）。

### 根因

**两个测试跑的不是同一条路径**：

| 路径 | 谁在写 Content-Type | 有没有归一化 |
|---|---|---|
| **真 Tomcat**（`@SpringBootTest(webEnvironment = RANDOM_PORT)` + 裸 socket） | Tomcat 11 的 `Response.setContentType(String)` 把值**拆成 (type/subtype, charset) 两份存**，写出去时**重新拼**（`Http11OutputBuffer.writeHeaders` ⇒ `";"` 后面**没有空格**） | ✅ **有** ⇒ 写什么都是 `application/json;charset=utf-8` |
| **MockMvc**（各域的 `*MvcPipelineTest`） | 没有容器 ⇒ **原样**写出去 | ❌ **没有** ⇒ 写什么就是什么 |

⇒ T-6 初版写的是 Go 的字面量 `"application/json; charset=utf-8"`（**带空格**）
⇒ 真 Tomcat 路径**照样**归一化成无空格（所以真 Tomcat 守门绿）
⇒ **MockMvc 路径原样保留带空格** ⇒ 把 **favorite** 的守门打红了
⇒ 而 **favorite 域对 global 零写权** ⇒ 修法**只能**落在共享层。

### 正确处置

共享层改写 **`application/json;charset=utf-8`（无空格）** —— 也就是
**各域自己的 advice 产出的那个值**（favorite 的 `GlobalResponseAdvice` 用
`MediaType.parseMediaType("application/json; charset=utf-8")`，而 Spring **保留原样的大小写** ⇒ 产出小写 `utf-8`）：

- **真 Tomcat**：写什么都被归一化成同一个值 ⇒ **不变**；
- **MockMvc**：写的值 **== Spring 转换器自己会写的值** ⇒ **零变化** ⇒ 别的域的守门不会被共享层改动打到。

⚠️ **不能用 `new MediaType(APPLICATION_JSON, UTF_8).toString()` 写死期望值**：
它产出的是 `application/json;charset=UTF-8`（**大写** UTF-8）⇒ 与各域实际产出的**小写**不一致
⇒ 用它当期望值会把**正确**的实现判成红。

### 教训

**「真容器守门绿」不等于「这个改动在所有路径上都无害」** ——
**MockMvc 路径没有容器的归一化**，它看到的是**应用层写的原始字节**。

⇒ **共享层改响应头/响应体这类「会被容器再加工」的东西时，必须同时问两句**：
① **真容器**上是什么样？（本仓已有先例：`GoStyleJsonContentTypeCharsetTomcatTest` 用裸 socket 逐字节比对）
② **MockMvc** 上是什么样？（各域的 `*MvcPipelineTest` 钉的就是这个）
⇒ **两条路径都要钉**，否则改一边只会打红另一边。

⚠️ 同族：**PIT-GLOBAL-015**（探针测的层 ≠ 端到端跑的层）——
那条讲「**测**的层」；本条讲「**同一份代码在两种测试基座上被观测的方式不同**」
⇒ 指向同一条纪律：**链路有分层 ⇒ 必须逐层点名，不能靠「层」的直觉。**

### 出处

- 首踩位置：2026-10-25 第二十五轮全仓回归（`mvn -o test -fae`）——
  `FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant` 红 ⇒ 先归因（不是 favorite 域的问题，是共享层改动）；
- 修法：`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java`
  的 `GO_JSON_CONTENT_TYPE` 改为**无空格**形态；
- 新守门：`service/common/src/test/java/com/rag/common/http/GoStyleJsonContentTypeCharsetTomcatTest.java`
  的第 4 条 `adviceWritesExactlyWhatDomainsOwnAdviceWritesInMockMvc`
  ⇒ 锁「Advice 写的值 == 各域自己 advice 会写的值」；
- 关联：`docs/DRIFT/global/lanes/L1-delivery.md` §十三（第二十五轮 T-6 + 自曝错账 #3）。
