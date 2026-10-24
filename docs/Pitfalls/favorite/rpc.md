<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 踩坑档 · `service/favorite/favorite-rpc`

> **核对时间：2026-10-25**
> 本档记的是「**为什么会踩**」—— 机制性陷阱，不是缺陷清单。

---

## PIT-FAV-RPC-001 · 🕳️ **Go 的 `return nil, err` 会赋值命名返回值 —— 「未赋值」是幻觉**

### 现象

2026-10-23 的台账写着：

> 「Go 侧 **21 处**错误返回因**未赋值命名返回值 `err`**，被记成 `success`。Java 侧按真实结果计数
> ⇒ **有意的指标行为变更，未签字**。」

⇒ 照这个结论，RAG 侧要么「照抄缺陷」（在 22 个分支里手写 `observeRpc(..., SUCCESS)`），
要么「登记为有意偏离」。

### 为什么会踩

Go 的 logic 头部是：

```go
func (l *XxxLogic) Xxx(in *pb.Req) (resp *pb.Resp, err error) {
    defer func() { metrics.ObserveRPC(module, action, started, err) }()
    ...
    return nil, favoritecommon.GRPCError(codes.Internal, favoritecommon.ErrorDbSelect)
}
```

读到「`return nil, X`」时，**直觉**是「err 没被赋值，还是 nil」。
⚠️ 但 **Go 规范：`return` 语句会把返回值赋给对应的命名返回值** ⇒ `err = X` ⇒ defer 里看到**非 nil**。

⇒ 也就是说：**这不是「编码疏忽」，这是 Go 的正常语义**，两侧行为**本来就一致**。

### 后果

若照原结论走，会把一个**不存在的偏离**登记进签字档；
更糟的是「照抄缺陷」方案会把 Go 的**正常语义**当成缺陷固化到 22 个分支里。

### 怎么不再踩

1. **凡涉及「Go 的 defer 看到什么」的问题，必须实测，不能靠读代码推断**。
   ⇒ 最小复刻程序（命名返回值 + defer + `return nil, X`）4 行就能给出答案。
   ⇒ 已固化为 `bash tools/metrics-probe/favorite/run.sh`（**并从真源抽取判定片段做 sha256 比对**，
   真源变了探针立刻响）。
2. **数字也要核**：「21 处」实为 `return nil, GRPCError(...)` 的**出现处数**，实测 **22**。
   ⇒ 量化结论里的每个数字都要能重新数出来。

### ⚠️ 附带一条：断言要**运行时**验，不是读源码验

同一轮里，我试图用「扫源码找每个 catch 块里是否设了 `RESULT_FAIL`」来守门
⇒ 朴素扫描报出 **7 处「漏设」** ⇒ **逐条核实后全是误报**（一个 catch 块可覆盖多个 throw，
而 `RESULT_FAIL` 只在块首设一次；私有辅助方法里的 throw 由调用方兜住）。

⇒ 改成**运行时**断言（`FavoriteRpcMetricsResultTagTest`：让 mapper 真的抛异常，看指标里到底记成什么）——
这才拿得到真实结论。⇒ **「读源码做静态断言」在有控制流时极易误报**；
真要用静态断言，必须写**括号配对**的解析器，不能用「向上看 N 行」。

---

## PIT-FAV-RPC-002 · 🕳️ **指标名以 `_total` 结尾与否，决定 Micrometer 会不会再加一次**

### 现象

favorite-rpc 的四个指标里，**只有一个**在 Prometheus 渲染后与 Go 不同：

| Go（`metrics.go`） | Micrometer 侧 | Prometheus 渲染 |
|---|---|---|
| `favorite_request_total`（Name=`request_total`） | `favorite_request_total` | `favorite_request_total` ✅ |
| `favorite_ops_total` | `favorite_ops_total` | `favorite_ops_total` ✅ |
| `favorite_db_error_total` | `favorite_db_error_total` | `favorite_db_error_total` ✅ |
| **`favorite_request_seconds_counter`** | `favorite_request_seconds_counter` | **`favorite_request_seconds_counter_total`** 🔴 |

### 为什么会踩

Micrometer 的 `Counter` 在 Prometheus 侧**无条件追加 `_total`**；
Go 的 `CounterVec` 则是**按 `Name` 字面量渲染**（不追加）。
⇒ 只有「名字**不以** `_total` 结尾」的那个会分叉。
⚠️ 而四个指标里三个都以 `_total` 结尾 ⇒ **只有一个**不一致 ⇒ 不逐条列出来根本发现不了。

### 怎么不再踩

1. 迁移指标时**逐个**列出「Go 的 Prometheus 渲染名」与「RAG 的渲染名」，**不要**按「名字一样」推断。
2. ⚠️ Prometheus 里查询**改名后静默返回空**（不是报错）⇒ 迁移前先查看板/告警有没有引用旧名。
3. ⚠️ **不要**用「改成 Timer/Histogram」来消除 `_total` 后缀 ——
   Go 那个指标是 `CounterVec.Add(time.Since(started).Seconds())`（**累计秒数**），
   换成 Histogram 会把语义从「累计」变成「分布」⇒ 那是**另一个指标**，不是同一个指标的修正。

---

## PIT-FAV-RPC-003 · 🕳️ **「进程边界」被降级成「配置项」后，失败姿态也跟着变了**

### 现象

Go 的 `cmd/fact-authority` 是**独立进程**：配置不完整 ⇒ **进程起不来**（exit 1）。
RAG 侧把 4 个进程合并成 1 个 JVM ⇒ 该开关变成 `@ConditionalOnProperty` ⇒
配置不完整时 ⇒ **不注册端点 + ERROR 告警，进程照常跑**。

### 为什么会踩

「默认关闭 ⇒ 默认零副作用」听起来完全对齐。
⚠️ 但**开启后**的失败姿态不同：Go 是「整个进程不存在」，RAG 是「服务活着但端点不存在」
⇒ 下游看到的是 **连接被拒**，而不是「服务没起来」
⇒ 监控上「进程存活」不再等价于「能力可用」。

### 怎么不再踩

1. 凡把「独立进程」降级成「同进程开关」的域，必须在台账里写清**开启后的失败姿态**。
2. 端点类能力建议加一个**就绪探针**（本轮未做，已记在 DEV-FAV-RPC-004 的「什么时候会变成真差异」里）。

---

## PIT-FAV-RPC-004 · 🕳️ **共享的「SQL 超时」机制只认 gRPC 线程 ⇒ HTTP 路径上它全程静默**

### 现象

仓库里有一套现成机制（本模块**早已注册**）：

- `DeadlineQueryTimeoutInjector`（MyBatis 拦截器）—— 按**当前线程**的
  `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout`；
- `SqlTimeoutStatusTranslator` —— 把 CancelRequest 的 SQLState `57014` 翻成 `DEADLINE_EXCEEDED`。

而 `DeadlineState` **只由 gRPC 的 `RpcInterceptor` 绑定**（`RpcInterceptor.java:281 / :372`）。

⇒ **权威读端点是 HTTP servlet**，跑在 Tomcat 的 worker 线程上 ⇒ 那条线程**没有** deadline
⇒ ⇒ 拦截器读到的 `remainingMillis()` 是 `Long.MAX_VALUE`（= 无 deadline）⇒ **完全不动**
⇒ **每条 SQL 都没有超时**。

### 为什么会踩

这套机制「在 rpc 侧工作得很好」⇒ 很容易以为**全进程**都受 deadline 保护。
⚠️ 而它**静默失效**：没有 deadline 时拦截器**直接 return**（连 WARN 都没有）——
「SQL 不设超时」正是引入它之前的行为 ⇒ **一切照常跑，只是保护没了**。

⚠️ 更坑的一层：Go 侧的 3s 是 `context.WithTimeout(r.Context(), 3s)`，
**绑在 handler 里**（`http.go:44`）⇒ 它对 **HTTP 路径**一样生效。
⇒ **Go 有、RAG 没有** ⇒ 而这个差异要等到「DB 慢查询」才暴露。

### 怎么不再踩

1. **凡新增非 gRPC 的入口（HTTP servlet / 定时任务 / MQ 消费），
   都要显式判断「这个线程有没有 deadline」** ⇒ 本域的修法是
   在**调用点**用 `DeadlineState.bindToCurrent(now + 3_000)` 显式绑。
2. ⚠️ **绑定位置要与 Go 一致**：Go 绑在 **handler** 里 ⇒ RAG 也绑在 servlet 里。
   下沉到 service 会让 service 的**其它**调用方也被套上预算（Go 侧没有这层）。
3. ⚠️ **不要用 `@Options(timeout=3)` 代替**：那是**每条语句**各给 3s，
   而 Go 的一个 ctx **覆盖整次 lookup**（17 道校验里的多次查询共用）⇒ 6 条慢查询能拖到 18s
   ⇒ **量纲不同**。
4. **超时值硬编码，不做配置项**：Go 侧是硬编码常量 ⇒ 做成配置就等于允许部署时改掉它。

⇒ 回归网 `FavoriteAuthorityLookupTimeoutTest`（真 PG + 真 CancelRequest），
**且必须断言「耗时」**：只断言状态码区分不出「3s 预算到期」与「其它错误」。

---

## PIT-FAV-RPC-005 · 🕳️ **手写 servlet = 你要自己实现 `http.ServeMux`**

### 现象

Go 的权威读端点是 `http.NewServeMux()` + 一个 handler。
RAG 侧是**手写的 `HttpServlet`**（`FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet`）。

⇒ 2026-10-25 第四轮实测：**Go 侧有 5 处行为，手写 servlet 一处都没有**。

| # | Go | RAG 修复前 |
|---|---|---|
| 1 | 少一段的 path ⇒ **404** | 🔴 抛 `StringIndexOutOfBoundsException` ⇒ **500** |
| 2 | path 需要清理 ⇒ **301 + Location** | **404** |
| 3 | 404 的 body = `"404 page not found\n"` + text/plain + nosniff | **只写状态码** |
| 4 | 401 的 body = `"unauthorized\n"` | `"unauthorized"`（**少一个 `\n`**） |
| 5 | mux 层判定**先于**鉴权 | 先撞鉴权 ⇒ **401** |

### 为什么会踩

1. **「Go 的 mux 是框架的一部分」这个事实不会出现在 handler 的源码里** ——
   只读 `http.go` 的人看到的是「注册了一个 pattern + 一个 handler」，
   **看不到 mux 自己会做 path 清理、会在匹配失败时给 404、会在清理后重定向**。
2. ⚠️ **段数判定是最容易写错的一行**：
   `rest.indexOf('/') != rest.lastIndexOf('/')` 在「**没有** `/`」时两者都是 **-1**
   ⇒ 判据**不成立** ⇒ 继续往下走 ⇒ `substring(0, -1)` 抛异常。
   ⇒ 一行判据、两个取值、一个符号，就制造了一个 **500**。
3. **`http.Error` 用 `fmt.Fprintln`** ⇒ body 末尾**有** `\n`；
   手写 `getWriter().write(msg)` ⇒ **没有** ⇒ 逐字节不一致但**看不出**。

### 怎么不再踩

⇒ **把 Go 的框架行为也变成可测的东西**：
`tools/authority-path-probe/favorite/` 用 **Go 标准库**复刻 `ServeMux` +
`http.Error` / `http.NotFound` / `http.Redirect` 的行为，输出 15 行矩阵，
与 Java 侧的 `FavoriteAuthorityPathParityTest` **逐行比对**。

⚠️ **探针只用标准库是刻意的**：本探针不需要真源仓库、不需要网络 ⇒ 能在任何机器上跑。
⇒ 而**期望值必须来自 Go 真源的实测**，不是「读 Go 源码推断」。

⚠️ **三条「照抄 Go 而不是照抄正确」的**（都是 Go 的既有行为，偏离会造成客户端行为分叉）：
① `Location` **二次转义**（`%` → `%25`）；② 301 的 body 末尾**两个** `\n`；
③ 404/401 的 body 末尾**一个** `\n`。
