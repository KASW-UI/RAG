<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · common 尾巴踩坑档（common-tails）

---

## PIT-GLOBAL-001 🔴 **查 Go 出处时，只在项目树里 grep 一定会落空**

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
- 方法论归档：`docs/DRIFT/global/cross-domain.md` X-8
- 2026-10-25，`align/global` @ `58e8314`

---

## PIT-GLOBAL-002 ⚠️ **「档位错置」：代码自称「有意」，但没人签字**

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

## PIT-GLOBAL-007 🔴 **Servlet 容器不向应用暴露「客户端已断连」信号**

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

## PIT-GLOBAL-008 🔴 **「写失败」观测不到 ⇒ 必须显式 flush；flush 又会把 `Content-Length` 退化成 chunked**

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

## PIT-GLOBAL-009 ⚠️ **显式声明一个 test 作用域依赖，会把 compile classpath 上的同名 artifact 顶掉**

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
