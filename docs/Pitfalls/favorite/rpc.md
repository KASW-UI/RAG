<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 踩坑档 · `service/favorite/favorite-rpc`

> **核对时间：2026-10-25**
> 本档记的是「**为什么会踩**」—— 机制性陷阱，不是缺陷清单。

---

## PIT-FAV-RPC-001 · 🕳️ **Go 的 `return nil, err` 会赋值命名返回值 —— 「未赋值」是幻觉** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-001` ⇒ **新号**：`PIT-FAV-22`


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

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/metrics-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:17:11 · 1s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ Go 语义如预期：错误路径记 fail（⇒ U5 撤销，Go 与 RAG 本来就一致）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../DRIFT/_cross/live-parity-2026-10-30.md)。

## PIT-FAV-RPC-002 · 🕳️ **指标名以 `_total` 结尾与否，决定 Micrometer 会不会再加一次** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-002` ⇒ **新号**：`PIT-FAV-23`


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

## PIT-FAV-RPC-003 · 🕳️ **「进程边界」被降级成「配置项」后，失败姿态也跟着变了** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-003` ⇒ **新号**：`PIT-FAV-24`


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

## PIT-FAV-RPC-004 · 🕳️ **共享的「SQL 超时」机制只认 gRPC 线程 ⇒ HTTP 路径上它全程静默** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-004` ⇒ **新号**：`PIT-FAV-25`


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

## PIT-FAV-RPC-005 · 🕳️ **手写 servlet = 你要自己实现 `http.ServeMux`** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-005` ⇒ **新号**：`PIT-FAV-26`


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

---

## PIT-FAV-RPC-006 · 🕳️ **静态调用点数不等 ≠ 行为差 —— 判据是「同一次失败，两侧各记几个」** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-006` ⇒ **新号**：`PIT-FAV-27`


### 现象

2026-10-25 第五轮做指标 label 对照时，把两侧 `ObserveDBError` / `observeDbError` 的
`(module, action, type)` 三元组当**多重集**一比：

| | Go | RAG |
|---|---|---|
| `FOLDER/delete/db` | 1 | **3** |
| `FOLDER/select/db` | 7 | **5** |
| `ITEM/delete/db` | 1 | **3** |
| `ITEM/insert/db` | 1 | **3** |

⇒ 「4 组不等 ⇒ 有 4 处指标漂移」—— **这个结论是错的**。

### 为什么会踩

1. **Go 侧是「一条错误分支记一次」，Java 侧是「一个异常类型记一次」** ——
   Go 的 `if dbErr != nil { … ObserveDBError(…) … }` 是**一段**；
   Java 的 `try/catch` 拆成 `OwnerMismatchException` / `NotFoundException` / `DataAccessException`
   **三个 catch 块**，每块各写一遍 `observeDbError`。
2. ⚠️ **那三个 catch 是【互斥】的**（不同的异常类型）⇒ **一次失败只会命中其中一个**
   ⇒ 运行时**至多记 1 次** ⇒ 与 Go 的「记 1 次」**完全一致**。
3. 反方向也成立：Go 的两处独立 select 分支，RAG 可能**合并进同一个 catch**
   ⇒ 一次失败仍只记 1 次 ⇒ 静态点数 7↔5，运行时仍是 1↔1。

⇒ **静态调用点数根本不是行为等价性的判据。**

### 怎么不再踩

⇒ **判据换成「同一次失败，两侧各记几个、记成什么」**，逐个失败分支对着看。

⚠️ 差点把它记成一条新漂移 —— 记下来当反面教材。
⇒ 台账落点：[`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §11.5（口径说明，**不记入漂移条目**）。

⚠️ 顺带更正 `tools/metrics-probe/favorite/README.md` 的「已知边界」：
「不覆盖 `ObserveDBError` 的标签取值」这半句**对**（两侧都显式传参，没有 Go 的 defer/命名返回值那类语言层陷阱），
但它**没有回答**「标签取值本身两侧是否一致」⇒ §11.5 把这一问补上了（结论：一致）。

---

## PIT-FAV-RPC-007 · 🕳️ **登记 ≠ 签字；签字前必须复核「两侧确实不同」这个前提** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-007` ⇒ **新号**：`PIT-FAV-28`


### 现象

用户 2026-10-25 拍板「全部登记为签字」，本域当时有 **7 条**待签字。
本轮在落实签字（改状态行）时**逐条复核前提** ⇒ **查出 1 条前提根本不成立**：

`DEV-FAV-RPC-002` 登记的是「Go 侧 `create_favorite_logic.go:89` 与
`get_favorites_by_folder_logic.go:68` 都在 **item** 流程里用了 `folderModule` ⇒ Go 侧写错了」。

⇒ 逐行核对 Go 真源才发现：**那两行不是 item 流程，是【收藏夹查询】的失败分支**
（`create_favorite_logic.go:79` 是 `FindFolderByFolderId`、
`get_favorites_by_folder_logic.go:58` 是 `FindFolderByFolderId`）
⇒ `folderModule` **本来就是对的** ⇒ **两侧一直一致，从未偏离** ⇒ **该条撤销，不该被签**。

### 为什么会踩

1. **「两侧不同」是登记的前提，而登记时只看了「label 看着像不对」** ——
   没去看**那行代码处在哪个分支**。
2. ⚠️ **同一个坑在本域犯了两次**：
   本台账 §2 的 D5/D6 早已写明「Go 侧全部自洽，错的是 RAG 侧的映射」——
   **同一个「看错分支」的错**，在 metrics 域重犯了一遍。
3. ⚠️ **签名会让错误结论「转正」**：一旦签了字，后来人就有了「这已经审过了」的印象
   ⇒ 再也没人会去核前提。

### 怎么不再踩

⇒ **签字 = 复核，不是盖章**：每条签字前，把「两侧确实不同」这个前提**重新验一遍**（带行号）。
⇒ 前提不成立的 ⇒ **撤销**，并把**证伪过程**写进撤销记录（保留原结论原文，便于追溯）。

⚠️ **别把「撤销」当成「没做过」** —— 撤销记录本身就是产出：
它证明这条**被查过、且被证伪**，比「压根没注意到」强。
⇒ 落点：[`../../Intentional_Deviations/favorite/rpc.md`](../../Intentional_Deviations/favorite/rpc.md)
「撤销记录 · DEV-FAV-RPC-002」+ [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §11.4。

---

## PIT-FAV-RPC-008 · 🕳️ **wire 形状漂移：状态码与业务码都测不出来 ⇒ 必须逐字段断言响应体** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-008` ⇒ **新号**：`PIT-FAV-29`


### 现象

v2 权威读端点返回的 `subject_ref` 是 **v1 的三字段**（`authority_id` / `tenant_id` / `subject_id`），
而 Go 的 v2 是 **两字段**（`issuer` / `subject_id`）。

⚠️ **这个 bug 在此前的全部测试下都是绿的** —— 因为：
· 状态码 **200** ✅ 一致；· 业务码 ✅ 一致；· v1 端点的形状 ✅ 本来就对；
· 「v2 端点多了一个字段」这件事，**任何不逐字段断言响应体的测试都发现不了**。

⇒ 下游 binder（BTW）**按字段名解析** ⇒ `issuer` 取不到、`tenant_id` 多出来 ⇒ **解析失败**。

### 为什么会踩

1. 🔴 **Go 的 v2 入口不是「另一个读法」，而是「读完之后多一步【压扁】」**：
   `AuthoritativeFavoriteFactV2`（favorite_authority.go:195-204）在 resolve 之后
   把 v1 的三字段 `subject_ref` **压成**两字段（`authority_id` → `issuer`，**丢掉** `tenant_id`）。
   ⇒ **漏掉这一步 ⇒ 形状就错**，而**读的部分完全正确** ⇒ 完美躲过所有功能断言。
2. ⚠️ **RAG 侧的两条 javadoc 把这件事写对了**（`FavoriteAuthorityLookupService` 的类注释
   明写「v2 的 `subject_ref` 是两字段，可直接用 `Wire.AuthorityFact`」）
   ⇒ **注释对、实现漏** ⇒ 光读注释更会以为已经做了。
3. ⚠️ **「形状对不对」与「值对不对」是两种断言** —— 前者要看**字段集合**，
   后者只看值 ⇒ 只断言值的话，`{issuer, subject_id, tenant_id}` 里的 `issuer` 值是对的 ⇒ 照样绿。

### 怎么不再踩

⇒ **形状类契约必须断言「字段集合」，不是断言「值」**：
`assertEquals(Set.of("issuer","subject_id"), fieldNames(subjectRef))` ——
**多一个字段少一个字段都要红**。

⇒ **再用类型系统把这件事钉死**：`authoritativeFactV2` 的**返回类型**从 `FactV1` 改成
`Wire.AuthorityFact` ⇒ 「返回三字段形态」**编译不过** ⇒ 不靠注释提醒。

⇒ **变异验证**（本轮两条，都转红）：
· M1 `issuer` 误取 `tenantId` ⇒ `expected: <rtw.identity> but was: <platform>`；
· M2 **完整回退修复** ⇒ 精确报出**修复前的真实形状** `实际=[authority_id, subject_id, tenant_id]`。

⇒ 落点：[`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §11.2 + 回归网
`FavoriteAuthorityV2ResponseShapeTest`（3 例，真 PG 端到端）。

---

## PIT-FAV-RPC-009 · 🕳️ **「DDL 逐列对照」要先确定三件事，否则结论会错** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-009` ⇒ **新号**：`PIT-FAV-30`


### 现象

2026-10-25 第七轮做 favorite 域的 DDL 双侧对拍时，**源码级推理连错两次**，
最后靠**真 PG 活体对拍**才拿到正确结论：

| # | 源码级推理的结论 | 实测结论 |
|---|---|---|
| 1 | 「目标 schema = GORM AutoMigrate **∪** 4 个手写 SQL」 | **漏了顺序** —— 顺序会改结果（见下） |
| 2 | 「001 变 no-op ⇒ 部分索引也没了」 | ❌ **部分索引是独立语句，照样建出来** —— 只有**内联**在 `CREATE TABLE` 里的 CHECK 才丢 |

### 为什么会踩

1. 🔴 **「schema = A ∪ B」这个写法本身就掩盖了顺序**：Go 侧 `InitDB` 只跑 `AutoMigrate`，
   那 4 个 `.sql` **没有任何 Go 代码执行**（全仓 grep 可证）⇒ 它们是**运维手工执行**的
   ⇒ 「A ∪ B」到底是「A 后 B」还是「B 后 A」**完全取决于运维动作**。
2. 🔴 **`CREATE TABLE IF NOT EXISTS` 变 no-op 时，丢的只有表内联的那部分**：
   `CHECK (...)` / `UNIQUE (...)`（内联）**会丢**；
   而同文件里**独立的** `CREATE INDEX` **不会丢**。
   ⇒ 只看 SQL 文本分不出「哪些语句会丢」⇒ **必须真跑一遍**。
3. ⚠️ **ORM 的 AutoMigrate 不是「只增不改」**：它会**主动删掉**已存在的 DEFAULT
   （`gorm.io/gorm@v1.31.1/migrator/migrator.go:543-550` 的「default value → null」分支），
   也会按 struct 顺序**重排列序**（新表场景）。

### 怎么不再踩

⇒ **建 DDL 对拍时，必须先钉住三件事**：

| # | 要钉住什么 | 本域的答案 |
|---|---|---|
| 1 | **真源 schema 的来源与执行顺序** | AutoMigrate（应用启动）**之后**才是运维手工跑 4 个 `.sql`？还是之前？⇒ 由真源文档判定（`doc/工程成熟度审查报告.md:410`：**22 个 `.sql` 无自动执行路径**，须上线前手工执行 ⇒ ⇒ **与 AutoMigrate 的先后不由程序保证** ⇒ 属运维顺序问题） |
| 2 | **哪些语句在 no-op 时会丢** | 只有**内联**在 `CREATE TABLE` 里的约束会丢；独立的 `CREATE INDEX` / `ALTER` 不会 |
| 3 | **ORM 自己会改什么** | AutoMigrate 会删 DEFAULT、会重排列序 ⇒ 「DBA 手写的表」经过 ORM 之后**不等于**手写的样子 |

> 📌 **订正注记（2026-10-27 · G3 · 21:0x）：上表第 1 行的引用已实修**（**原引用 `DELIVORY_ACCEPTANCE.md:7` 指向的文件【在真源与本仓都不存在】** ⇒ 悬空）⇒ ⇒ 已改为**真源真实存在的**出处 `doc/工程成熟度审查报告.md:410` ⇒ ⇒ **原文引用保留在本注记里**（不删历史）。
> ⇒ **真源原文（`:410`）**：「**无启动依赖就绪等待**：entrypoint 启动二进制前不等待 etcd/PG/Redis/Kafka 就绪，也**不执行数据库迁移**（**22 个 `.sql` 无自动执行路径**；knowledge 的 `Migrate: false` 明确要求上线前手工执行）」⇒ ⇒ **答案 = 与 AutoMigrate 的先后不由程序保证，属运维顺序问题** ⇒ ⇒ 上表第 1 行的问号**现在有答案了**。
> ⇒ **另一条相关真源出处**：同文件 `:262`「**迁移机制弱**：22 个 `.sql` 为一次性脚本（部分带 `IF NOT EXISTS` 可重复执行），**无 golang-migrate/goose 等版本化迁移框架**，无迁移历史表，无法追踪已执行到哪一版」⇒ ⇒ 与 `:410` 互为佐证。
> ⇒ **核对来源**：G2 lane 通报（2026-10-27 14:10）＋ 本域**自建核对器全量扫**（本域 **1262** 处「文件:行号」引用逐条核「文件存在 + 行号在范围内」）⇒ ⇒ **真缺陷 7 处已全部实修**。

⇒ **探针必须把「顺序」当成变量**：本探针同时跑 `sql-first` 与 `app-first` 两个场景，
**并把两者的差**原样打印出来 ⇒ 「顺序是行为的一部分」这句话才**有证据**。

⇒ ⚠️ **附带一条**：探针的 dump 要把**语义**与**名字**拆开。
把对象名混进语义行 ⇒ PG 的自动命名（`..._check`）与显式命名（`..._nonempty`）会**每次都比出一堆假差异**
⇒ 真差异被淹没 ⇒ 探针变成「红噪声」，没人看。
⇒ 本探针的做法：`S|` 行只放语义、`N|` 行只放名字，差异**自动分类**为「命名层」与「语义层」。

### 同源的另一条（本轮同时踩到）

**应用层时钟 ≠ 数据库时钟。** Go 的 `autoCreateTime` 是**应用层**赋值，
而 DDL 上的 `DEFAULT now()` 是**数据库**时钟 ⇒ 两者不是一回事。
⇒ 「列上有 DEFAULT」**不等于**「这列的值来自 DB」——**取决于应用层有没有显式绑**。
⇒ 这条让 `favorite_fact_outbox` 的两列时间戳在两侧**时钟源不同**（详见 `docs/DRIFT/favorite/rpc.md` §13.1）。

---

## PIT-FAV-RPC-010 · 🕳️ **注释里的 Go 断言会「指错行」—— 而且没有任何测试会红** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-010` ⇒ **新号**：`PIT-FAV-31`


### 现象

2026-10-25 第九轮用机器审计 §3.3.2 时，在 favorite 域抓到 **3 条真缺陷**，
形态统一是：**断言的内容是对的，锚点指到了不相干的行**。

| 锚点写的 | 锚点处实际是什么 | 真值在哪 |
|---|---|---|
| `rest/httpx/requests.go:129-137` = `ParseJsonBody` | `ParsePath`（另一个函数） | `rest/httpx/requests.go:117-124` |
| `zrpc/config.go:29` = `RpcClientConf.Timeout` | `Token` 字段 | `zrpc/config.go:31` |
| `net/http/html.go` 的 `htmlReplacer` | **该文件在 Go 1.25 已不存在** | `net/http/server.go:2458` |

### 为什么会踩

1. **行号是从哪来的？** 手写锚点时，行号往往**不是查出来的，是"看起来像"的**。
   写的人记得「在那个文件里」，但没记住**第几行** ⇒ 写了个大致范围 ⇒ 恰好落在邻近函数上。
2. **为什么没人发现？** 因为
   - **行为测试是绿的** —— 注释错不影响任何可执行路径；
   - **人读注释时不会跳过去核对** —— review 关心的是「这段逻辑对不对」，
     而「注释指的那一行是不是它说的那个东西」需要一个**额外的跳转动作**；
   - **断言本身看起来很合理** —— 读的人会自动脑补「应该就是那儿」。
3. **库代码的行号更容易过期。** `zrpc/config.go` / `rest/httpx/requests.go` 跟的是
   **依赖版本**，不是本仓代码 ⇒ 升一次 go-zero 就可能整体位移。
   ⚠️ 而本仓的 `go.mod` pin 住版本 ⇒ 行号其实**是稳定的** —— 前提是**照着锁定版本查**。

### 怎么不再踩

⇒ **把 §3.3.2 从「靠人读」变成「机器可验」**：
`tools/assertion-audit/favorite/check-assertion-sources.py`（详见该目录的 `README.md`）。

四类门禁级发现：**F1** 缺锚点 · **F2** 锚点悬空 · **F3** 行号越界 ·
**F4** 引用不符（`<pre>` 里引用的 Go 源码**落在锚点行段之外** ⇒ 锚点指到了别的函数）。

★ **F4 是唯一能自动抓出「指错函数」的检查** —— 人工 review 极易漏。

⇒ 三个必须记住的配套要点：

1. **锚点要能定位到「消费点」**（§3.3.1）⇒ 只贴 struct 定义行不算证据。
2. **库锚点要带版本**（`go-zero@v1.10.2/rest/httpx/requests.go:117-124`）——
   裸 `requests.go` 读者定位不到，且 httpx / httpc 同名。
3. 🔴 **审计工具的第一版输出不能直接当结论用** —— 本轮工具自己就有 3 个缺陷
   （正则漏 `@`、stdlib 索引缺 `src/` 前缀、basename 回退把 `zrpc/config.go`
   错映射成别的域的 `config.go`）⇒ **先修工具，再信工具**。

### 同源的另一条

**同名文件跨域并存时，字面存在 ≠ 指向正确。**
favorite 的 `checkblacklist_middleware.go`（下划线）与 follow / user 域的
`checkblacklistmiddleware.go`（无下划线）**同时存在于真源** ⇒ 一个写错的锚点
**照样能在真源里找到文件** ⇒ 只做「文件是否存在」的检查不够，**还要看它属于哪个域**。

---

## PIT-FAV-RPC-011 · 🔴 **改注释会把「台账自己的锚点」挪了**（自指漂移） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-011` ⇒ **新号**：`PIT-FAV-32`


> **元教训分类**：**自指漂移** —— 本轮修的是「java 注释里的 Go 锚点」，
> 却顺手弄坏了「台账里的 java 锚点」。
> **发现轮次**：第十轮（2026-10-25）
> **证据**：[`docs/DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §16.8

### 现象

台账的「位置」列大量引用 **RAG 侧 java 文件的行号**（`FavoriteController:190`、
`FavoriteLegacyPreflightRunner.java:204` …）。
本轮改了 **26 个 java 文件的注释**（补 Go 锚点）⇒ **行号整体位移**
⇒ 台账引用的位置**当场过期** —— 而台账是**用来指路的那份文档**。

实测 **6 处漂移**（2026-10-25，全部偏 1~2 行）：

| 台账写的 | 实际 | 偏差 |
|---|---|---|
| `isBlacklisted:220-230` | `221-231` | 偏 1 |
| `FavoriteController:234-263` | `235-264` | 首尾各偏 1 |
| `FavoriteController:190` | `191` | 偏 1 |
| `FavoriteLegacyPreflightRunner.java:204` | `205` | 偏 1 |
| `FavoriteLegacyPreflightRunner.java:208` | `209` | 偏 1 |
| `FavoriteProtectionChainWiringTest.java:101` | `102-103` | 偏 1~2 |

### 为什么「行号越界检查」抓不住

机检「行号是否超出文件行数」⇒ **6 处全部通过**（偏 1 行仍在文件内）。
⇒ **越界是必要不充分条件**：**行号落在文件内 ≠ 指对了那一行**。
真正抓出来的是「**把每个锚点指向的实际内容打印出来逐条对**」。

### 怎么不再踩

1. 🔴 **写完锚点必须再核一遍行号** —— 哪怕这次改动是**纯注释**。
   本轮 **E-b / E-c 与 §16.4 的 breaker 那处**就是本轮自己写的 —— 写的时候用的是**补锚点之前**的文件状态
   ⇒ 补锚点又改了同一个文件 ⇒ 又偏了。
2. **「位置」列优先引用「锚点内容」而不是「行号」**（如「`isBlacklisted` 方法」而不是「`:221-231`」）
   ⇒ 行号会漂，方法名不会。
3. 若必须用行号 ⇒ **在台账里标注核对日期**（AGENTS.md §6「状态表纪律」），
   并把它列入「改完注释后必查」清单。
4. **锚点抽查是可机检的半自动动作**：把台账里的 `<类名>:<n>` 全部解析出来、
   打印该行的实际内容 ⇒ 人眼扫一遍即可（本轮 50 个锚点，6 处漂移）。

---

## PIT-FAV-RPC-012 · 🔴 **GORM 默认把日志写到 stdout** ⇒ 会污染探针的输出文件 · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-012` ⇒ **新号**：`PIT-FAV-33`


> **发现轮次**：第十二轮（2026-10-25，写 `tools/authority-live-probe/favorite/` 时）
> **形态**：**探针自伤** —— 不是产品代码的 bug，但会让探针产出**看似正常、实则被污染**的结果。

### 现象

Go 侧探针把矩阵结果 `fmt.Println` 到 stdout ⇒ 结果是对的。
但**真源 model 的 GORM 会把 "record not found" 这类日志打到 stdout**（**不是 stderr**）：

```
2026/10/25 07:03:56 .../internal/model/favorite_delivery.go:147 record not found
[1.508ms] [rows:0] SELECT * FROM "favorite_fact_outbox" WHERE status IN (0,2) AND ...
```

⇒ 这些行**混进了 stdout** ⇒ 落盘后：
· Java 侧 `applySeed` 逐行执行时，第一行就是那坨日志 ⇒ `syntax error at or near "2026"`；
· 或者「看起来跑通了」，实际冻结的期望文件里混着日志。

### 为什么难发现

① 日志行**不像错误**（没有 ERROR 前缀，只是时间戳 + 路径 + message）；
② `2>/dev/null` **挡不住**（它写的是 stdout，不是 stderr）；
③ Go 侧自己觉得「我只是 Println 结果」⇒ 不会想到 GORM 也在抢 stdout。

### 怎么不再踩

⇒ **探针的结果一律写文件，stdout 只留诊断**：
```go
out := env("PROBE_OUT", "")
if out == "" { return errors.New("缺少 PROBE_OUT") }
os.WriteFile(out, []byte(strings.Join(lines, "\n")+"\n"), 0o644)
```
· 探针自己的日志 ⇒ `fmt.Fprintf(os.Stderr, ...)`；
· GORM 的日志 ⇒ 在探针里 `gorm.Open(..., &gorm.Config{Logger: logger.Discard})` 或干脆不管
  （反正它写 stdout，而 stdout 不再承载结果）。

⇒ **通用判据**：**只要「结果」与「日志」共用一个输出通道，就一定会出事** ⇒ 结果走文件、日志走 stderr。

⚠️ 同源提醒：`tools/ddl-parity-probe/favorite/` 的 Go 侧也是 `say()` 写 stderr + 结果写 stdout ——
它之所以没踩到，是因为它**没有**触发 GORM 的 "record not found" 分支（它的 SELECT 都命中了）。
⇒ **「这次没踩到」不等于「不会踩到」**。

---

## PIT-FAV-RPC-013 · 🔴 **`mvn -pl` 不带 `-am` ⇒ 用过期上游产物 ⇒ 假红** · 状态：**已闭环** · 核对日期：**2026-10-29** · 影响当前轮次：**否**

> **旧号**：`PIT-FAV-RPC-013` ⇒ **新号**：`PIT-FAV-34` —— ⚠️ **本轮不适用的原因**：触发条件已消除：探针脚本已补 `-am` ⇒ `tools/api-envelope-probe/favorite/run.sh:131` 现为 `mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am test` ⇒ 上游产物一并重建 ⇒ 「用过期上游产物 ⇒ 假红」不再发生 ⇒ 本轮不适用


> **发现轮次**：第十二轮（2026-10-25）
> **形态**：**探针脚本自伤** —— 不是产品代码的 bug，但会让探针**报出与代码无关的失败**。

### 现象

`tools/api-envelope-probe/favorite/run.sh` 用
`mvn -o -q -pl service/favorite/favorite-api test -Dtest=FavoriteApiEnvelopeParityTest`
跑 Java 侧 ⇒ **稳定失败**：

```
FavoriteApiEnvelopeParityTest.tooManyFormParamsMatchGoByteForByte:341->getWithToken:425
  » Protocol Too many interim responses received: 9 > 8
```

⇒ 看起来像「Go ↔ RAG 的响应字节对不上了」⇒ **极易误判成真回归**。

### 根因（**已定位到，不是猜测**）

`-pl <module>` **不带 `-am`** ⇒ Maven **不会**重建上游模块 ⇒ 直接用
`service/common/target/classes` 等**已存在的产物**。

⚠️ 在 worktree 场景里这会**必然踩到**：
本 worktree 的 `service/common/target/classes` 是**上一轮编译的**，
而 `service/common` 的**源码**可能已被**别的会话**改过（该模块是跨域共享的）
⇒ api 模块编译时链接的是**旧 class** ⇒ 运行时行为与源码不一致 ⇒ 协议层出怪状况。

⇒ **决定性对照**（第十二轮实际做的）：
| 环境 | 命令 | 结果 |
|---|---|---|
| **基线 worktree**（`0fa76f2`，零改动、零 target） | `mvn -o -pl … -am test` | ✅ **绿** |
| 本 worktree，**不带** `-am` | `mvn -o -pl … test` | ❌ 红（3/3 次） |
| 本 worktree，**带** `-am` | `mvn -o -pl … -am test` | ✅ **绿** |

⇒ **同一份源码、同一台机器** ⇒ 变量只有「上游产物是否新鲜」⇒ 根因坐实。

### 怎么不再踩

⇒ **所有 favorite 域探针的 mvn 调用一律加 `-am`**（已全部改完）：
`tools/{api-envelope,proto-wire,authority-path,authority-live}-probe/favorite/run.sh`
+ `tools/errmsg-probe/favorite/run-parity.sh`。

⚠️ **加 `-am` 必须同时加 `-Dsurefire.failIfNoSpecifiedTests=false`** ——
`-am` 会把 `-Dtest=X` 带到上游模块，而上游**没有**这个测试 ⇒ surefire 会**直接失败**
（不是「跳过」）⇒ 实测 `users-common` 报 `No tests matching pattern … were executed!`。

⚠️ `tools/ddl-parity-probe/favorite/run.sh` **没有** mvn 调用（纯 Go + Python）⇒ 不受此坑影响。

### 通用判据

> **「模块级 mvn 命令」要么带 `-am`，要么显式确认过上游产物是新鲜的。**
> ⚠️ 在**多会话并发 + worktree** 的仓库里（AGENTS.md §5.3），「上游源码变了但我的 target 没变」
> 是**常态**，不是意外 ⇒ 不加 `-am` 的探针**必然**在某个时刻开始假红。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-envelope-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:17:43 · 30s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✓ Go 侧输出与 expected.txt 逐字节一致（sha256=c5dc8761884acfa0…） ⏐ 2026-10-30T03:17:54.779+08:00  INFO 47811 --- [favorite-api] [.0-21300-exec-2] c.r.common.observability.HttpMiddleware  : [HTTP] 1xx 探针行数预算用尽（cap=4）⇒ 本请求不再写探针字节（客户；<br>`tools/ddl-parity-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:20:09 · 5s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ OBS 段逐条一致（13 条） ⏐ [ddl-probe] ⇒ 结论：**执行顺序是行为的一部分** —— app-first 下 001 的 CREATE TABLE 变 no-op ⏐ [ddl-probe]   ⇒ 001 里**内联**的 CHECK (aggregate_version IN (1,2)) 建不出来； ⏐ [ddl-probe]   ⇒ 且 favorite_fact_outbox 的**列顺序**也不同（；<br>`tools/errmsg-probe/favorite/run-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:16:02 · 4s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 obs_custom_grpcstatus obs_ok_status 差异仍在登记状态。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../DRIFT/_cross/live-parity-2026-10-30.md)。

## PIT-FAV-RPC-014 · 🔴 **测「两个 gRPC 实现之间的差异」≠ 测「RAG 是否忠实复刻 Go」** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-014` ⇒ **新号**：`PIT-FAV-35`


> **一句话**：拿两边的**原始帧字节**互比，测到的是**库级差异**；
> 要测本域，必须**用同一个客户端**分别打两侧。

### 现象

favorite 域的台账里挂了三轮一条「🔴 未验：帧层」，当时的判定是：

> 「帧层编码由 **gRPC 库**决定（grpc-go vs grpc-java），**不是 favorite 域的行为** ⇒ 不在本域 scope。」

这个结论**方向对，但把三层东西一起打包扔掉了**（详见
[`docs/DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §20.1）：
L0（帧原始字节）确实不可比，但 **L1（status code / description / 消息字节 / metadata key）**是本域的责任。

### 根因

- 帧层的**原始字节**由库决定，而且**客户端不可观测** —— 客户端的 HTTP/2 实现会**重新编码**对端帧
  ⇒ 你能拿到的永远是「你的客户端看到的」，不是「对端发出的」。
- ⇒ 要比 L1，唯一办法是**消掉客户端自身的差异** ⇒ **两侧用同一个客户端**。

### 怎么不再踩

⇒ **判据**：
> 任何「A 实现 vs B 实现」的对拍，**先问一句：换掉客户端，结论还成立吗？**
> 不成立的 ⇒ 测的是库级差异，**不是**本域行为。

⇒ favorite 域的落地：`tools/grpc-contract-probe/favorite/` 用**同一个 grpc-java 客户端**
（同一份 stub、同一套 metadata 处理）分别打 Go 真源 server 与 RAG server。

### 附带的一条元教训

**「客户端可观测」不等于「值得比」** —— 还要问「**这个差异是不是本域引入的**」。
本域 24 条业务文案里**没有 `~`** ⇒ grpc-go / grpc-java 的 percent-encoding 边界差 1 字节
（`http_util.go:256` vs `Status.java:591-593`）这件事**在本域完全不可观测**
⇒ 不进漂移台账，但**要记下来**（否则下一个人会重新查一遍）。

---

## PIT-FAV-RPC-015 · 🔴 **探针的「用例顺序」= 「副作用顺序」⇒ 读同行的用例必须排在写同行的用例之前** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-015` ⇒ **新号**：`PIT-FAV-36`


### 现象

第十三轮的第一版 `grpc-contract-probe` 里，冻结值 `expected.txt` **每次跑都变**：

```
冻结值：body=hex:0a1c08a94610e9071a06416c7068613220aeb6c8d7b733288e83b2829734
实测值：body=hex:0a1c08a94610e9071a06416c7068613220aeb6c8d7b73328b693ca829734
```

⇒ 差在 `updated_at` 字段上 ⇒ 哨兵立刻报「Go 真源的 gRPC 契约变了」⇒ **假红**。

### 根因

- 用例矩阵里有一个「读 user 1001 的收藏夹列表」的用例（用来量化完整响应头 key 集合），
  它被放在了**矩阵的最后** ⇒ 而矩阵末尾有一个「把 folder 9001 改名」的写入用例
  ⇒ **改名会把 `updated_at` 推成「本次运行的时刻」** ⇒
  **读同一行的用例**观测到了**上一次运行**留下的时间戳 ⇒ 每次都不一样。
- ⚠️ 危险之处：它**不是**每次都红 —— 它红在「Go 侧与冻结值不一致」这条哨兵上，
  **看起来像「真源变了」**，极易被误判成「Go 那边有人改了代码」。

### 怎么不再踩

⇒ **判据**：
> 探针里只要有**写操作**，就必须把「读同一行的用例」全部排在写操作**之前**。
> 换句话说：**用例矩阵的顺序就是数据库状态的演化顺序**，观测值必须只依赖「种子 + 排在它之前的写」。

⇒ 本探针的落地：把「响应头全集」用例从矩阵末尾**移到写入用例之前**并重编号
（`c30_response_header_surface`），改完后同一份种子的观测值**逐次稳定**。

⇒ **附带**：这类假红的特征是「**第一次跑就红，且每次红的字段不一样**」
⇒ 遇到「冻结值对不上」时，**先怀疑自己的探针有没有副作用**，再去怀疑真源。

---

## PIT-FAV-RPC-016 · 🔴 **框架层的噪声要单独量化，别混进业务用例**（否则白白丢掉对比能力） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-016` ⇒ **新号**：`PIT-FAV-37`


### 现象

`grpc-contract-probe` 第一版把**完整响应头 key 集合**直接放进每个用例的观测值里
⇒ 8 个本来**逐字节一致**的业务用例全部变成「不一致」
（grpc-java 的响应头多发 `grpc-accept-encoding` / `grpc-encoding`，grpc-go 不发）。

### 根因

框架层/库层的差异是**真实存在**的，但它是**每一条用例都带**的常量噪声
⇒ 把它放进值里 ⇒ 所有用例的 SAME 判定全部失效 ⇒
**「消息字节逐字节一致」这个最有价值的结论被噪声淹没了**。

### 怎么不再踩

⇒ **判据**：
> 区分「**域级契约**」与「**框架级噪声**」，但**不要用过滤器把噪声藏掉** ——
> 要**另开一个用例**把它显式量化并登记。

⇒ 本探针的落地（三步）：
1. 业务用例的 `rhdr` 只保留**非 `grpc-*` 的 key**（**含值**，例如 `content-type=application/grpc`）
   ⇒ 8 个用例回到 SAME，且**消息字节的对比能力完整保留**；
2. **另开一个用例** `c30_response_header_surface`（`fullHeaders=true`）记录**完整**头集合 ⇒ 登记为 **OBS**
   ⇒ 库级差异**被量化、被冻结、可见**；
3. 过滤器**只按前缀 `grpc-` 过滤**（不是「按已知差异清单过滤」）
   ⇒ **任何非 `grpc-*` 的头差异仍然会被抓出来** ⇒ 真差异没被藏。

⚠️ **反面做法**（禁止）：维护一张「已知差异清单」然后把它们从值里剔除
⇒ 那等于**把真差异也一并豁免**，且清单一旦漏了新差异就永远抓不到。

---

## PIT-FAV-RPC-017 · 🔴 **手写 gRPC 方法名时别带前导斜杠**（否则你会测到「未知服务」而不是「未知方法」） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-017` ⇒ **新号**：`PIT-FAV-38`


### 现象

2026-10-25 第十四轮实测：同一个 grpc-java 客户端、同一个 `MethodDescriptor`，
打 Go 真源 server 与 RAG server，两侧的 description 分别是：

```
Go : unknown service /favorite.FavoriteService     ← 🔴 带前导斜杠、且没有方法名
RAG: Method not found: /favorite.FavoriteService/DoesNotExist
```

⇒ 上一轮把它登记成「Go 对未注册方法返回 unknown service」，并标「**归因未查清**」。

### 根因（**已定位到行号**）

🔴 **探针自己把 `:path` 拼坏了**：

1. grpc-java 的 `MethodDescriptor.generateFullMethodName(service, method)`
   = `service + "/" + method`（**不**补前导斜杠，字节码实测）；
2. 而 **netty 客户端写 `:path` 时会自己补一个 `/`** ⇒ 线上路径恒为 `/service/method`；
3. 探针却手写成 `"/favorite.FavoriteService/DoesNotExist"`（**多带了一个斜杠**）
   ⇒ 线上路径变成 **`//favorite.FavoriteService/DoesNotExist`**（**双斜杠**）；
4. grpc-go v1.81.1 `server.go:1826` 的 `sm = sm[1:]` **只剥掉一个**前导 `/`
   ⇒ 它切出的 service 名 = `"/favorite.FavoriteService"`（**带斜杠**）
   ⇒ 查 `s.services` 注册表**必然落空** ⇒ 走 `unknown service` 分支，
   **而不是**「已注册服务 + 未注册方法」的 `unknown method` 分支。

⇒ 一句话：**措辞被探针自己污染了**，Go 与 grpc-java 在这一点上根本没被测到。

### 实证锚点（怎么证明客户端确实会补斜杠）

同一个矩阵里的 `c34_health_check` 用例，描述符**本来就不带**前导斜杠
（`grpc.health.v1.Health/Check`）⇒ 线上路径合法 ⇒ 真源 go-zero 注册的 health 服务
**正常应答**（`body=hex:0801` = SERVING）⇒ 客户端**确实**会补斜杠。

### 怎么不再踩

⇒ **判据**：
> 测「未注册方法」之前，先确认 `:path` 的**斜杠个数**。
> grpc-java 的 `fullMethodName` **不带**前导斜杠（由 netty 补）；
> grpc-go 只剥**一个** ⇒ 多一个斜杠就会掉进「未知服务」分支。

⇒ 修正后两侧测的才是同一件事，真实库级差异是：
**grpc-go 区分「未知方法 / 未知服务」两种措辞；grpc-java 两种情形共用一种措辞**
⇒ grpc-java **丢掉了「方法 vs 服务」的区分**。

⚠️ **元教训**：「**归因未查清**」这一栏不是终点 —— 它是**待办**。
下一轮必须把它变成「已查清」或「明确不可查」，
不能让它带着一个**自造的**假象进台账。

---

## PIT-FAV-RPC-018 · 🔴 **`@SpringBootTest` 会把「模块能不能真起」这件事整个掩盖掉** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-018` ⇒ **新号**：`PIT-FAV-39`


### 现象

2026-10-25 第十四轮第一次把 favorite-rpc / favorite-api 当作**真实进程**（`java -jar`）启动时，
**两个模块都起不来**：

```
① favorite-rpc：
   APPLICATION FAILED TO START
   Web application could not be started as there was no
   org.springframework.boot.web.server.servlet.ServletWebServerFactory bean defined in the context.
   ⇒ 根因：pom 里只有 spring-boot-web-server + jakarta.servlet-api，
            **没有** spring-boot-starter-web（无内嵌 Tomcat）
            ⇒ Boot 默认按 SERVLET 起 ⇒ 找不到 ServletWebServerFactory
            ⇒ 修法：--spring.main.web-application-type=none（对齐 Go 的「纯 gRPC、零 HTTP 端口」）

② favorite-rpc（修好 ① 之后）：
   NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder
     at net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration
        .shadedNettyGrpcChannelFactory(GrpcClientAutoConfiguration.java:160)
   ⇒ 根因：devh 的 channel factory 在**运行期**引用 InProcessChannelBuilder，
            而 grpc-inprocess 只在 **test scope**（favorite-api 干脆没声明）
            ⇒ 修法：scope 改成 runtime / 补声明
```

### 根因

**测试上下文与真实进程不是同一个东西**：
`@SpringBootTest(webEnvironment = NONE)` 强制 `web-application-type=none` ⇒ 永远走不到 ①；
测试 classpath 含 test scope 的 `grpc-inprocess` ⇒ 永远走不到 ②。

⇒ 于是「**测试全绿**」与「**jar 能起**」之间出现了一道**没人测过的缝**。
（`tools/difftest/check-jar-executable.sh` 只查 `Main-Class`，查不出「少一个运行时依赖」。）

### 怎么不再踩

⇒ **判据**（AGENTS.md §0 的「先让它真的启动一次」，本条把它落成可执行形式）：
> 任何模块要声称「对齐完成」，至少要有一次**以真实进程启动**的证据。
> `@SpringBootTest` 起的上下文**不算**。

⇒ 落地：`tools/api-full-parity-probe/favorite/run.sh` 里，
RAG 侧两个模块**都是 `java -jar` 真起的** ⇒ ① ② 这类问题**当场暴露**。

⚠️ **推论**：本条一旦成立，就会连带查出**别的域**也有同样的缝
（见 `docs/DRIFT/favorite/rpc.md` §22.4 的跨域清单）⇒ 那部分归 `global`。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:28:29 · 48s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 两侧种子就绪 ⏐ ✅ 黑名单对照 token 就绪（Redis exists=1） ⏐ ✅ 两侧各产出 31 个 case ⏐ SAME 25 条 / SAME_CODE 1 条 / SAME_BODY 2 条 / SAME_STATUS 4 条 / OBS 0 条 / SKIP 0 条（共 32 个 case） ⏐ ✅ api 整域两侧一致（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../DRIFT/_cross/live-parity-2026-10-30.md)。

## PIT-FAV-RPC-019 · 🔴 **「关掉 Nacos discovery」在 rpc 侧与 api 侧是**相反**的两件事** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-019` ⇒ **新号**：`PIT-FAV-40`


### 现象

2026-10-25 第十四轮实测：

| 侧 | 处置 | 结果 |
|---|---|---|
| **rpc** | `--spring.cloud.nacos.discovery.enabled=false` | ❌ **起不来**：`No qualifying bean of type 'com.alibaba.cloud.nacos.NacosServiceManager' available` |
| **rpc** | `--spring.cloud.nacos.discovery.server-addr=127.0.0.1:1`（**指向死地址**） | ✅ 起来；注册在 `ApplicationReadyEvent` 时失败 ⇒ **只打一条 ERROR**，进程照常 |
| **api** | `--spring.cloud.nacos.discovery.enabled=false` | ✅ 起来（api 走 spring-cloud 的**自动**注册，没有硬依赖组件） |
| **api** | 指向死地址 | ❌ **起不来**：`NacosServiceRegistry.register ⇒ failFast=true ⇒ Client not connected ⇒ Failed to start bean 'webServerStartStop'` |

### 根因

- **rpc 侧**：本域**自己手写**了注册组件 `FavoriteNacosRegistration`，
  它的**构造器硬依赖** `NacosServiceManager` ⇒
  「关掉 discovery」= 「 autoconfiguration 不存在」= 「拿不到那个 Bean」= **上下文起不来**。
  （类注释明写「显式手写注册而不依赖自动注册」⇒ 这个硬依赖是**手写带来的必然结果**。）
- **api 侧**：走 spring-cloud 的**自动**注册 ⇒ 它在 Nacos 不可达时 **failFast** ⇒ 整个 api 起不来。

### 怎么不再踩

⇒ **判据**：
> 「把服务发现关掉」不是万能的降级开关 —— **两个模块的失败语义相反**。
> 关 discovery 是**去掉 autoconfiguration**，不等于「让注册失败」。

⇒ 本探针的落地：
- **rpc** ⇒ 指向**死地址**（保留 autoconfiguration ⇒ 保留 Bean ⇒ 注册失败只打日志）；
- **api** ⇒ **关掉** discovery（避免自动注册的 failFast）。

⚠️ 顺带记一条**部署形态事实**：**favorite-rpc 的启动硬依赖「Nacos autoconfiguration 存在」**
（不是依赖「Nacos 服务可用」）⇒ 「禁用服务发现」这个运维动作会让 favorite-rpc **起不来**。

---

## PIT-FAV-RPC-020 · 🔴 **探针的「环境红」不是「被测代码红」**（等待过短 / 探活探错目标 ⇒ 假红） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-020` ⇒ **新号**：`PIT-FAV-41`


### 现象

2026-10-25 第十四轮收尾：favorite 域 9 个探针**串行连跑**（机器 load avg ≈ 10，别的会话在跑）。

| 探针 | 单跑 | 连跑 |
|---|---|---|
| api 整域对拍 · gRPC 契约对拍 · api envelope · DDL · proto wire · authority path · metrics · errmsg | ✅ EXIT=0 | ✅ EXIT=0 |
| **authority live 对拍** | ✅ EXIT=0 | ❌ **EXIT=2 · `PG 没起来`** |

⇒ **同一个 commit、同一份代码**，单跑绿、连跑红。

### 根因（两条叠在一起）

1. **探活探错了目标**：`pg_isready -U postgres`（**没带 `-h`**）⇒ 走 **unix socket**。
   PG 官方镜像初始化阶段只起一个**临时 server**，它**只监听 unix socket**（`listen_addresses=''`）
   ⇒ socket 探活会**假就绪**；紧接着的 `CREATE DATABASE` 才去撞「系统正在启动」而随机失败。
   （`grpc-contract` / `api-full-parity` / `ddl-parity` 当初**已经**带 `-h`，**只有 `authority-live` 探 socket**。）
2. **等待上限太短**：`for _ in $(seq 1 40)`（**40s**）⇒ 机器高负载时 PG 的 `initdb` 本身就要几十秒
   ⇒ **还没起来就判死**。

⇒ 两条叠起来 ⇒ 容器刚起、或机器一慢 ⇒ **必假红**。

### 后果

- **假红长得跟「真源漂移」一模一样**（探针红 ⇒ 记台账 ⇒ 改代码 ⇒ 改不对）⇒ **污染结论**。
- ⚠️ **反向风险更大**：socket 假就绪后若 `CREATE DATABASE` 恰好蒙对 ⇒
  探针会**带着一个没真正就绪的 PG 跑下去** ⇒ 这一轮的**结论本身不可信**（不只是报错不可信）。

### 怎么不再踩

⇒ **判据**：
> 探针红了，**先分诊**成「**环境红**」还是「**被测代码红**」，再决定动不动代码。
> 环境问题（等待 / 探活 / 端口 / 镜像）**不该产出台账条目**。

⇒ 本域落地（4 个起 PG 的探针**统一**加固，`tools/*/favorite/run.sh`）：
- 一律 **探 TCP**（`-h 127.0.0.1`）—— **不是** socket；
- 上限 **180s**（不是 40 / 60s）；
- 没就绪 ⇒ **重建容器再来一次**（容器起不来 = 资源竞争 / 端口映射失败）；
- **容器自己退出 ⇒ 立刻放弃等待**（`--rm` ⇒ 退出会被删掉 ⇒ `docker inspect` 失败 ⇒ 别白等满超时）；
- 顺带：Redis 等待 30s → 60s + 同样的「容器退出即放弃」；Go server 端口等待 60s → 120s。

⚠️ **推广面（归 `global` 收口域，本域只写自己这份）**：
- 同款 `pg_isready` 不带 `-h` / 等待 40~60s 的写法，在**别的域探针里大概率也存在**；
- `hot` 域**已记过同款**（「并行跑时 Testcontainers 容器起不来 ⇒ 看起来像回归其实不是」）
  ⇒ 两条合起来说明这是**探针基建约定层**的问题，不是某一域的个别 bug。

---

## PIT-FAV-RPC-021 · 🔴 **跨域清单的每一条都要能指出「它凭什么说缺」**（`pom 里没写` ≠ 缺 ⇒ 要看 `dependency:tree` 的**最终作用域**） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-021` ⇒ **新号**：`PIT-FAV-42`


### 现象

2026-10-25 第十四轮记了一条跨域清单：

> 「全仓用 `grpc-client-spring-boot-starter` 却**运行时缺 `io.grpc:inprocess`** 的可执行应用模块
> ⇒ **13 个模块**的**可部署 jar 都起不来**」
> ⇒ 表格里逐个标了「🔴 没声明」/「🔴 test scope」。

第十五轮做「台账 vs 实际」复核时，**全仓跑了一遍**
`mvn -o dependency:tree -Dincludes=io.grpc:grpc-inprocess` ⇒ **清单的方向就错了**：

| 第十四轮的判法 | 实测 | 结论 |
|---|---|---|
| 「**没声明** grpc-inprocess ⇒ 缺」 | devh starter 的 `grpc-inprocess` 是 **compile** ⇒ **没声明反而有** | 🔴 **方向性错误** |
| 「**13 个**模块的 jar 起不来」 | **可执行应用模块真缺 = 3 个** → **0 个**（`article-rpc` / `like-rpc` / `task-rpc` 已由 `global` 的 L12/L13 修好 · 随 `4f839f9` 合入主干 · 2026-10-27 实测） | 🔴 **条数高估 4 倍** ⇒ 且**隔了 2 天又归零**（别域把修法合进主干 ⇒ 本域的「当前状态」类断言就过期）⇒ **跨域项必须留复查点，不能只转达一次** |
| 「favorite-api 也缺（它『干脆没声明』）」 | 改前实测 = `grpc-inprocess:1.63.0:compile` ⇒ **本来就不缺**；那次 `NoClassDefFoundError` 的现场日志证明**出在 favorite-rpc** | 🔴 **误判** |

### 真机制（本条该记住的就是这句）

> **直接声明会覆盖传递依赖**（maven「最近者优先」）⇒
> 「**自己声明成 test scope**」把传递来的 compile **顶掉** ⇒ 运行时 classpath 里没有；
> 而「**没声明**」是安全的 —— 传递依赖照常生效。
> ⇒ **判「运行时到底有没有」，不能看 pom 写没写，要看 `dependency:tree` 的最终作用域。**

⚠️ 而第十四轮那次的错误，恰恰是 AGENTS.md §3.3.2 说的「**无出处断言**」：
当时是**推断**「没声明 ⇒ 也缺」⇒ 没实测就写进台账 ⇒ 一张跨域清单从根上就错了。

### 怎么不再踩

⇒ **判据**：
> **跨域清单里的每一条，都要能指出「它凭什么说缺」** ——
> 「pom 里没写」**不是**依据，「`dependency:tree` 显示最终作用域是 test」**才是**。

⇒ 本域落地：
- 清单按 `dependency:tree` **重写**（见 [`docs/DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §22.3 的更正表）；
- 区分「**可执行应用模块**」（jar 起不来）与「**`-common` 库**」（不是可执行模块 ⇒ 不影响 jar 启动）；
- **本域只登记不动别域文件** ⇒ 转交 `global` 收口域（登记在 rpc.md §22.7）。

⚠️ **连带**：favorite-api 的 `pom.xml` 注释里那句错误归因也一并更正了 ——
它的 runtime 声明**保留**（实测它本来就有 ⇒ 这次只是把隐式变显式，**无害**），
但注释必须说清「**这是加固，不是修缺陷**」。

---

## PIT-FAV-RPC-022 · 🕳️ **台账里最危险的断言是「数字」**（指针有自检兜底，**数字没有**） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-022` ⇒ **新号**：`PIT-FAV-43`


### 现象

本仓已有两道台账守门：

- `check-ledger-selfcheck.py` ⇒ 查**台账之间**（章节指针 / 编号闭合 / Go 源文件落点）；
- `check-assertion-sources.py` ⇒ 查**代码注释里的 Go 锚点**（能不能解析、行号在不在范围内）。

⇒ 但它们**都不会**抓这一类：**台账里的事实断言（数字）与仓库实际脱节**。

2026-10-25 第十五轮盘点时，当场抓到两处：

| 台账说 | 实际 | 为什么难发现 |
|---|---|---|
| 「6 个测试类 / 89 例全绿」 | 8 个测试类 / 94 例 | **读起来完全通顺** ⇒ 没有死链、没有未登记编号 ⇒ 前两道门禁**一声不响** |
| 「全仓 7 个域 / 14 个测试类用 `RANDOM_PORT`」 | 6 个域 / 10 个（favorite 本轮已修 ⇒ 归零；且有两个只在 javadoc 里提到它的支撑类被误算） | 同上 |

⇒ 而「13 个模块缺 grpc-inprocess」那条更狠：数字与**方向**都错（见 **PIT-FAV-RPC-021**）。

### 怎么不再踩

⇒ **判据**：
> 台账的「指针」错了会**报错**，「编号」错了会**被自检抓到**，
> 但「**数字**」错了**什么都不会响** —— 因为数字没有引用关系。
> ⇒ 凡是**可枚举、可数出来**的断言，都要有一条**能复跑的机检**。

⇒ 本域落地：新增
[`tools/ledger-lint/favorite/check-ledger-vs-reality.py`](../../../tools/ledger-lint/favorite/check-ledger-vs-reality.py)
—— **从台账正文抽断言**（不把真相写死在工具里）⇒ **独立**从仓库算实际值 ⇒ 逐条比对。

⚠️ **工具自身也要分级**：
- **门禁级** = 实际值**无歧义**（文件数 / 定义数 / pom 扫描 / git diff / 常量）；
- **提示级** = 实际值**有歧义**（用例数：源码 `@Test` 注解 ≠ surefire 报告 ——
  注释里的示例代码、`@Testcontainers` 误匹配都会让两者不等）⇒ **只报差异不判死**。

⚠️ **工具的第一版必须先修再信**：本轮它第一次跑就报了 4 条红 ⇒ 人工逐条核实后
**2 条是台账真错、2 条是工具自己的抽取 bug**（正则匹配到了旧章节 / 两条断言措辞同形）
⇒ **工具与人工双向独立一致**，才算数。

## PIT-FAV-RPC-023 · 🔴 **端口排除列表必须包含「本轮已分配的全部端口」**（撞号 ⇒ 症状伪装成「unknown service」） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-023` ⇒ **新号**：`PIT-FAV-44`


### 现象

2026-10-25 第十六轮给 gRPC 契约探针加「第二轮」（create 成功路径）时，第一版这样分配端口：

```bash
STUB_PORT="$(pick_free_port "${GO_PORT}")"                                  # ← 只排除了自己看得见的那个
GO_PORT_SUCCESS="$(pick_free_port "${GO_PORT}" "${STUB_PORT}")"
RAG_PORT_SUCCESS="$(pick_free_port "${GO_PORT}" "${STUB_PORT}" "${GO_PORT_SUCCESS}")"
```

⇒ 结果：**桩抢到了第一轮 RAG server 的端口** ⇒ 第一轮的客户端把请求打到了**桩**上
⇒ 而桩只注册了 `UserService` / `ArticleService` ⇒ 于是报：

```
code=UNIMPLEMENTED|desc=unknown service favorite.FavoriteService
```

### 为什么难查

🔴 **症状离根因很远**：
- 报错说的是「服务不存在」⇒ 直觉会去查**服务注册**（proto package / `@GrpcService` / 服务名）；
- 实际是**两个进程抢同一个端口**；
- 而且 `pick_free_port` 的自检（`0.0.0.0:P` 与 `127.0.0.1:P` 都能绑）在**分配那一刻**是通过的 ——
  因为那时 RAG server **还没起**（它要到 maven 那一步才起）⇒ **自检根本发现不了**。

⇒ 这正是 **PIT-FAV-API-008** 的**同型**坑的另一半：
那次是「别的会话的容器抢号」，这次是「**自己这一轮**的另一个进程抢号」。

### 怎么不再踩

⇒ **判据**：
> 端口排除列表 = **「本轮至今已分配的全部端口」**，不是「我这次新加的那几个」。
> ⇒ 新增一轮时，**必须把上一轮的端口一起列进去**。

⇒ 本域落地：
1. `pick_free_port` 的调用改成**显式列出全部已分配端口**（不再用数组展开）；
2. 分配完立刻做**互异性自检**（5 个端口去重后必须仍是 5 个）⇒ 撞号当场红，不留到运行期。

⚠️ 附带踩到的第二个坑（见 **PIT-FAV-RPC-025**）：为了绕开 bash 3.2 的数组展开坑，
新写的 `${arr[@]+"${arr[@]}"}` **多了一个 `"`** ⇒ 整份脚本在 `$( )` 里解析失败。

## PIT-FAV-RPC-024 · 🔴 **种子显式写自增主键 ⇒ 序列不推进 ⇒ 首次 INSERT 撞 PK**（症状伪装成「重名 5203」） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-024` ⇒ **新号**：`PIT-FAV-45`


### 现象

第十六轮第一次跑「create 成功路径」时，Go 真源侧报的是：

```
ERROR: duplicate key value violates unique constraint "favorite_folder_pkey" (SQLSTATE 23505)
  INSERT INTO "favorite_folder" ("folder_id","user_id","name",...) VALUES (2114307093964140544,2001,'ParityFolder',...)
error_code=5203
```

⇒ 而 `5203` = **「收藏夹名称已存在」**（`ErrorFavoriteFolderNameExists`）。

### 为什么难查

🔴 **症状在业务层，真因在基础设施层**：
- 看到 `5203 名称已存在` ⇒ 直觉是「重名检查误报」⇒ 会去查 `FindFolderByUserIdAndName`；
- 实际是 **INSERT 撞了主键**（`favorite_folder_pkey`，即 `id` 列）；
- 而 `id` 是**自增主键**：Go 侧 `INSERT ... RETURNING "id"`、RAG 侧 mapper 也不带 `id`
  ⇒ **两边都靠序列**；
- 种子却**显式写了 `id`（1/2/3）** ⇒ **PostgreSQL 不会因为显式 INSERT 推进序列**
  ⇒ 序列仍停在 1 ⇒ 种子灌完后第一次 INSERT 拿到的还是 `id=1` ⇒ 撞上种子里那行。

⇒ 也就是说：**「名字唯一」与「主键唯一」两个错误被同一条 SQLSTATE 23505 掩盖了**，
而业务码把前者翻译成了 5203。

### 怎么不再踩

⇒ **判据**：
> **种子只写业务列，自增主键让它自己走。**
> ⇒ 空表上自增的结果与手写 1/2/3 **完全一样**（值不变）⇒ 没有任何理由显式写。

⇒ 本域落地：`tools/grpc-contract-probe/favorite/seed.sql` 已去掉 `id` 列，
并在文件头写明「为什么不能写」＋「症状长什么样」。

⚠️ **注意**：这份种子被 `tools/api-full-parity-probe/favorite/run.sh` **共用**
⇒ 改它必须**两个探针一起复跑**（本轮已复跑，冻结值不变 —— 因为响应体里本就没有 `id`）。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:28:29 · 48s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 两侧种子就绪 ⏐ ✅ 黑名单对照 token 就绪（Redis exists=1） ⏐ ✅ 两侧各产出 31 个 case ⏐ SAME 25 条 / SAME_CODE 1 条 / SAME_BODY 2 条 / SAME_STATUS 4 条 / OBS 0 条 / SKIP 0 条（共 32 个 case） ⏐ ✅ api 整域两侧一致（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../DRIFT/_cross/live-parity-2026-10-30.md)。

## PIT-FAV-RPC-025 · 🐚 **bash 3.2 的 `${arr[@]+"${arr[@]}"}` 多一个 `"` ⇒ 整份脚本 `unexpected EOF`** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-025` ⇒ **新号**：`PIT-FAV-46`


### 现象

在 `$( )` 里写：

```bash
STUB_PORT="$(pick_free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"}")"
```

⇒ `bash -n` 直接报：

```
run.sh: line 275: unexpected EOF while looking for matching `)'
run.sh: line 419: syntax error: unexpected end of file
```

### 根因

探针里**原本就有**这个写法（`GO_PORT=...`、`RAG_PORT=...` 两行）⇒ 它是**对的**：

```bash
GO_PORT="$(pick_free_port ${USED_PORTS[@]+"${USED_PORTS[@]}"}")"
#                                   └─ `+` 后一个 `"` · 内层 `}` 后一个 `"` · 然后 `}` ─┘
```

⚠️ 手写新行时**多打了一个 `"`**（`...[@]}"}"`）⇒ 那个 `"` 把 `$( )` 里的引号配对搞乱
⇒ bash 3.2 在 `$( )` 里遇到 `${arr[@]+"…"` 时按「引号内不再解析 `$(`」处理
⇒ 于是外层的 `$(` 找不到配对的 `)`。

### 后果

🔴 **报错行号指向最后那个 `)`，离真凶很远**：
真正的错字在**第 3 行**（新写的端口分配那几行），报错却说 `line 275`。
⇒ 第一反应会去查「275 行附近的那个 `)` 是什么」⇒ 白查。

### 怎么不再踩

⇒ **判据**：
> macOS 自带 **bash 3.2**，`set -u` 下展开**空数组**会报 `unbound variable`
> ⇒ 用 `${arr[@]+"${arr[@]}"}` 绕开；但这个写法**对引号数量极其敏感**，
> **抄的时候要连字节一起抄**，不要凭记忆重打。

⇒ 本域落地：新增端口一律**显式传参**（`pick_free_port "${A}" "${B}"`），
**不再新增数组展开写法** ⇒ 这类解析错误从根上少一个来源。

⚠️ 顺带提醒：`bash -n` 是**免费的语法门禁** —— 改完 shell 脚本先 `bash -n` 一遍，
比跑一遍探针（几分钟）便宜两个数量级。

## PIT-FAV-RPC-026 · 🔴🔴 **「探针里用 CLI 参数绕过」≠「产物里修好了」**（半修 ⇒ 缺陷活到了发布那一刻） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-026` ⇒ **新号**：`PIT-FAV-47`


### 现象

🔴 **第十七轮（2026-10-26）** 做「生产启动形态」活体对拍时，发现
**第十四轮记为「已修」的启动阻塞 #1，在生产产物里根本没修**：

```
第十四轮（commit e76ea8e）记的修法：
  「启动加 --spring.main.web-application-type=none（对齐 Go 的『纯 gRPC、零 HTTP 端口』）」
  落点写的是：「favorite-rpc/pom.xml 的依赖说明 + **探针启动参数**」

⇒ 也就是说：那条 `--spring.main.web-application-type=none` 只出现在
  tools/api-full-parity-probe/favorite/run.sh 的 java 命令行里，
  **src/main/resources/application.yml 里一行都没有**。

⇒ 后果：**探针绿、测试绿、jar 打得出来，但真 fat jar 用生产 yaml 原样启动 ⇒ 照样死**：
    APPLICATION FAILED TO START
    Description:
      Web application could not be started as there was no
      org.springframework.boot.web.server.servlet.ServletWebServerFactory bean defined in the context.
```

### 为什么难查

🔴 **症状在部署那一刻，证据在探针里，而探针是绿的**：

| 证据 | 说的什么 | 真的说明了什么 |
|---|---|---|
| 探针 `EXIT=0` | 「服务起得来」 | 「**加了绕过参数**之后起得来」 |
| 85 个测试全绿 | 「行为对」 | 「**测试上下文**里行为对」 |
| `mvn package` 成功 | 「产物在」 | 「产物在，**不代表它能起**」 |

⇒ 三份证据**都**指向「已修」，而**没有一份**问过：
> **「这个 jar，不加任何额外参数，用它自己打包进去的那份 yaml，能起吗？」**

⚠️ 更狠的一层：**绕过参数把缺陷变成了「探针的一部分」** ——
后来的人读 `run.sh` 只会看到「这里传了个参数」，**不会意识到它在替 yaml 兜底**。
⇒ 绕过参数**伪装成了配置**。

### 怎么不再踩

⇒ **判据**：
> **凡是靠「启动参数 / 环境变量 / `@SpringBootTest(properties=...)`」绕过去的缺陷，
> 都必须回到被绕过的那份配置里修掉，并且让探针**不再传**那个参数。**
> ⇒ 否则探针是在**测一个修好的世界**，而不是**测你交付的那个世界**。

⇒ 本域落地（第十七轮）：

1. **修 yaml**：`service/favorite/favorite-rpc/src/main/resources/application.yml`
   加 `spring.main.web-application-type: none`（+ 判据注释）；
2. 🔴 **摘掉探针里的兜底参数**：`tools/api-full-parity-probe/favorite/run.sh` 里那行
   `--spring.main.web-application-type=none` **已删除**（注释改成「**故意不再传**」+ 为什么）
   ⇒ **一旦有人把生产 yaml 里那一行删掉，这个探针会直接红** ⇒ 守门网成立
   ⇒ ⚠️ 顺带说明：**这条参数就是「半修」的本体** —— 它在探针里待了整个第十四轮，
   **看起来像配置**，实际是**替 yaml 兜底** ⇒ 读脚本的人不会想到它在遮什么；
3. **新探针** `tools/prod-startup-probe/favorite/run.sh`：
   - **B case 不传任何 `--grpc.client.*` 覆盖** ⇒ client 地址就是生产值；
   - **只**覆盖「环境相关」的三项：PG 连接 / Nacos 地址 / gRPC 监听端口；
   - **C case** 反过来把 `web-application-type` 覆盖成 `servlet` ⇒ 必须炸
     ⇒ 证明那一行是**承重**的；
4. **守门测试** `FavoriteRpcProductionStartupTest` 直接读**生产 yaml**
   （不经过任何 `@SpringBootTest` 覆盖）⇒ 少了那一行立刻红。

⚠️ **同一个坑在本域还有第二例**（同源）：
第十四轮的启动阻塞 #2（`grpc-inprocess` 被声明成 test scope）
**是真修**（改了 pom 的 scope）⇒ 但 #1 走了 CLI 绕过 ⇒
**两个「启动阻塞」里有一个是半修** ⇒ 台账 §22.3 把两条一起写成「**已修**」⇒ **台账也一起是错的**。

⇒ 元教训：
> **台账上写「已修」时，要能指出「修在哪一层」**：
> **改了被测物**（配置 / 代码 / 依赖）才算修；**只改了观测手段**（探针参数）**不算**。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:28:29 · 48s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 两侧种子就绪 ⏐ ✅ 黑名单对照 token 就绪（Redis exists=1） ⏐ ✅ 两侧各产出 31 个 case ⏐ SAME 25 条 / SAME_CODE 1 条 / SAME_BODY 2 条 / SAME_STATUS 4 条 / OBS 0 条 / SKIP 0 条（共 32 个 case） ⏐ ✅ api 整域两侧一致（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_；<br>`tools/prod-startup-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:33:47 · 28s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ PG 就绪（127.0.0.1:55096）· 库 first_db ⏐ ✅ gRPC 监听端口 21300（专用段 21300-21399 · 双绑定自检通过） ⏐ ❌ A-before：**启动即炸**（devh URI 校验 · 两斜杠） ⏐ ✅ B-after：**启动成功** ⏐ ❌ C-before：**启动即炸**（Boot 判定成 SERVLET 但没有 servlet 容器） ⏐ ✅ A/B/C 成立：两斜杠 ⇒ 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../DRIFT/_cross/live-parity-2026-10-30.md)。

## PIT-FAV-RPC-027 · 🔴 **「借来的工具」只能当探针，不能当门禁**（且它的第一版输出要逐条核证） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-027` ⇒ **新号**：`PIT-FAV-48`


### 现象

2026-10-26 第十八轮，`global` 收口域告知：跨域共用的台账复核器
`tools/check-ledger-selfcheck.py` 做了三处修复，并点出
**「该工具没有 favorite 域预设 ⇒ `--domain favorite` 会报找不到台账文件」**。

⇒ **当时的两个选项**：
1. 登记一条候选，等 `global` 什么时候有空来扩；
2. **把规则搬进本域自己的门禁**。

⇒ 选了 2，但**不是抄文件**，而是**只读地借**他们的实现跑一遍本域台账：

```
importlib 载入 global 最新的 tools/check-ledger-selfcheck.py（拷到 /tmp，**不改原文件**）
⇒ 只调它的 L1（仓内路径存在性）与 L4（Go 侧 file:line 出处）两层
⇒ 把 favorite 的 12 份台账喂进去
```

⇒ **当场抓出 3 处**（其中 1 处是行号真错，2 处是路径写法不够精确）⇒ 见台账 §27.2。

### 为什么必须「搬进本域」而不是「等别人加」

🔴 **「共享工具没有我的域预设」= 本域的台账质量取决于别的域什么时候有空**
⇒ 那是**外部依赖**，不是门禁。
⇒ 而且本域**已经有**一把自己的梳子（`tools/ledger-lint/favorite/check-ledger-selfcheck.py`），
只是它**缺两层**（路径存在性 / Go 锚点可解析）⇒ **补在自己这边**才是正解。

### 🔴 更狠的一半：**工具的第一版输出，5 条门禁里 5 条是它自己的 bug**

本轮把新两层写进本域工具后，第一版输出 **8 条门禁**。逐条核证的结果：

| 类别 | 条数 | 具体 |
|---|---|---|
| **工具自己的 bug** | **5** | ① 省略写法的 dict key 结构写错 ⇒ `...` 漏进主循环；② 索引**缺 Go 标准库** ⇒ `net/http/server.go` 被误判 MISSING；③ **缺 N5「记述历史纠错」去噪** ⇒ 17 条历史记述被误判死链；④ 同库多候选不报；⑤（输出层面）提示平铺 ⇒ 新信号被埋 |
| **台账在记述历史纠错**（不是缺陷） | **2** | `create_favorite_logic.go:280-282`、`favoriteDelivery.go:155` —— 记的是**当时**的错 |
| **真缺陷** | **1** | `core/service/serviceconf.go` 的 **83-90 行**（🔴 行号越界）⇒ 该文件共 89 行，真实范围 83-89<br>⚠️ **第二十一轮补正**：这里引的是**当时写错的行号** ⇒ 写成「N 行」而**不是** `file:NN` ⇒ 否则 `file:NN` 扫描器会把它当**活指针**判成悬空 |

⇒ **比例 5 : 2 : 1**。

⇒ **如果不逐条核，就会把工具的 bug 当成台账的缺陷去「修台账」**
⇒ 而「修台账」的方向往往是**把对的路径改成工具以为的那个** ⇒ **把台账改坏**。

### 怎么不再踩

⇒ **判据**：
> **任何「机器说这里有问题」的输出，都必须先问一句「这是台账的问题，还是工具的问题」**
> —— 判据是**逐条核到文件内容**，不是看工具报得有没有道理。
> ⇒ 尤其**新写的第一版工具**：它的 bug 率天然最高（这轮 5/8）。

⚠️ **借来的工具尤其危险**：它**带着别人的假设**（本轮就带着共享工具那条
「取来源文件**前 2000 字**做去噪」的极宽判据 ⇒ 实测把 178 条里的 **173 条**都去噪了 ⇒ **假阴性**）
⇒ **别把别人的去噪规则整段照抄** ⇒ 要**按本域台账的写法**重新定判据（本域用**行级局部**）。

⇒ 本域落地（第十八轮）：
- 本域工具新增 **⑤ 仓内路径存在性** + **⑥ Go 侧 file:line 可解析** + `--mutation-test`；
- ⑥ 层的三个「必须」：**含 Go 标准库** / **有 N5 行级局部去噪** / **报同名多候选**；
- 提示输出**按类型分组**（总数 + 每类 5 条样本）⇒ 免得 73 条裸锚点把新信号埋掉。

---

## PIT-FAV-RPC-028 —— **分层验证的「排除项」= 下一层看不见的缝隙** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-028` ⇒ **新号**：`PIT-FAV-49`


> 2026-10-26 第二十轮（favorite 域 · SQL 语句层对拍）实测。
> **一句话**：每一层为了聚焦而**排除掉**的东西，会在下一层变成**看不见的缝隙** ——
> 而且往往是最严重的那一类缺陷正好掉在缝里。

### 现象

favorite 域的**前三层验证**（DDL / gRPC 契约 / 事务语义）**早就各自闭了**，
每一层都自洽、每一条都标着 ✅。
第四层（SQL 语句层）一开 ⇒ **当场抓出 3 处 L1 可观察的漂移**：
**RAG 侧的 INSERT / UPDATE 少绑时间列** ⇒ 落库 `NULL` ⇒ **对外响应里的时间戳变成 0**。

### 三层为什么**全都**看不见

| 层 | 它**排除掉**了什么 | 缝隙落在哪 |
|---|---|---|
| ① DDL 层 | 不看「谁往这一列里写」 | 列定义两侧一致 ⇒ ✅；但**没人写**这件事 DDL 看不见 |
| ② gRPC 契约层 | 只读**种子行**（种子把时间列写成常量） | 新建行的 `NULL` 永远不进冻结值 |
| ③ 事务语义层 | 只关心「业务行与事实行同生共死」 | 业务行**自己**的时间列有没有值，不在它的口径里 |
| 落库等价性投影 | **刻意排除时间列**（两侧时钟必然不同 ⇒ 比不了） | ⚠️ **「值不同」被排除了，但「一个是 NULL」也一起被排除了** |

⇒ **最后一行是根因**：「两侧时钟不同所以不比时间列」这个决定**是对的**，
但它**没有区分「值不同」与「一方根本没写」** ⇒ 最严重的那一类正好掉在缝里。

### 判据

> **每加一层验证，都要问一句：「我这一层**排除**了什么？那些被排除的东西，在**下一层**由谁负责？」
> ⇒ 如果答案是「没人负责」⇒ 那里就是**缝隙**。

⇒ 本轮的直接应用：DDL 层说「列一致」、契约层说「字节一致」、事务层说「同生共死」——
**三层都排除掉了「这列的值是谁写的」** ⇒ 这个问题的**唯一**负责层是**语句层**（而它此前不存在）。

### 怎么不再踩

⇒ **不要在某一层里「顺手排除」一个看起来没法比的东西** ——
先问「**它有没有 NULL / 缺省这个状态**」，因为「**没有值**」和「**值不同**」是**两件事**，
前者是**缺陷**，后者才需要放宽。

⇒ 如果确实要比「值不同」的列，**正确的做法是加一层只比「NULL / 非 NULL」的探针**，
而不是把整列从口径里删掉。

### 附带收获：一个更普适的教训

🔴 **被排除的东西往往正好是「最难比」的那个** ⇒ 而**最难比**通常意味着**最容易出缺陷**
（因为没人去核对它）⇒ **排除项的优先级应该比「主要观测项」更高**。

### 同一轮的另一个教训：**注释里的断言也会是缺陷**

本轮抓到的 3 处缺陷，**RAG 侧的注释都写着「这是对的」**：
- `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteFolderMapper.java:106-113`
  ：「Go 的 `Update("name", name)` **也只写 name**」⇒ **错**
  （GORM 会追加 `autoUpdateTime`，`gorm.io/gorm@v1.31.1/callbacks/update.go:235-254`）；
  ⇒ 同一段注释的**后半句**恰恰是对的 ⇒ **自相矛盾**。
- `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteRpcSupport.java:209-211`
  ：「那不会发生 —— **业务写入必带 autoCreateTime**」
  ⇒ **RAG 自己的 INSERT 路径就违反了这个前提**。

⇒ 与 `PIT-FAV-RPC-026`（探针绕过被测配置）同族：
**注释是「作者当时以为的事实」，不是证据** ⇒ §3.3.2 的断言纪律同样适用于注释。

---

## PIT-FAV-RPC-029 —— **「修一处」≠「修完」**：同一处锚点在台账里有 N 个副本 · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-029` ⇒ **新号**：`PIT-FAV-50`


> 2026-10-26 第二十一轮实测。触发者不是本域的工具改进，而是**别的域把共享工具改好**。

### 现象

第十八轮，本域台账自检抓出 1 处**行号越界**（`core/service/serviceconf.go` 写到 90 行，
该文件共 89 行）⇒ 我把它改成了正确的 `:83-89` ⇒ **本域门禁 0 条** ⇒ 收工。

🔴 但**同一处错误在台账里出现 3 次**：

| 位置 | 性质 | 当时是否改 |
|---|---|---|
| §27.2 修复表的**修正列** | 活指针（要指向真实范围） | ✅ |
| §27.2 修复表的**错误列** | 引的是**当时写错的行号** | ❌ |
| §0 的**叙述行** | 同上 | ❌ |
| 坑档 `PIT-FAV-RPC-027` 的「真缺陷」行 | 同上 | ❌ |

⇒ 本域工具**看不见**（那 3 处被自己的「N5 台账记述『删除/不存在』」去噪规则判成噪声 ⇒ 0 条）
⇒ 直到 **`global` 把共享工具的 N7 去噪从「文件级」改成「引用行 ±1」**，
这 3 处才第一次被真检查 ⇒ **浮出 1 条 FAIL**。

### 教训

1. **「修一处」≠「修完」** —— 锚点在台账里通常有**多个副本**（表格列 / 叙述行 / 坑档），
   改表格的**修正列**而不改**错误列**与叙述行 ⇒ **门禁仍然红**。
2. 🔴 **自检工具「0 条」不等于「真的没这个问题」** —— 去噪规则（不管是文件级还是行级）
   都可能把你的问题判成噪声。⇒ **去噪规则越宽，假阴性越大**。
3. ⇒ **判据**：改一个锚点前，先把它在**本域所有台账文件**里的**全部副本** `grep` 出来 ⇒ 一次改完。
4. ⇒ **顺带一条写法纪律**：引用**历史错误**时，不要写成能被 `file:NN` 扫描器当成**活指针**的形式
   （本轮用「`core/service/serviceconf.go` 的 **83-90 行**」⇒ **不写**「文件名 + 冒号 + 行号」那种形式
   ——**连举例都不能写成那个形式** ⇒ 否则**举例本身**也会被扫描器当成活指针）
   ⇒ 记录一字不丢，但不再制造假阳性。

### 同族

- `PIT-FAV-RPC-027`：**借来的工具只能当探针，不能当门禁**（⇒ 本域自己建门禁）
- `PIT-FAV-RPC-028`：**分层验证的排除项 = 下一层看不见的缝隙**
- `global` 域的「**工具的 bug 率天然最高**（新写的第一版尤其高）」那一条坑 ⇒ **在 global 域台账里**，本域不转录编号

---

## PIT-FAV-RPC-030 —— **新写了带 Go 锚点的文件 ⇒ 必须跑「管它的那个门禁」**：门禁只判形式，抓不到「指错文件」 · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-030` ⇒ **新号**：`PIT-FAV-51`


> 2026-10-26 第二十二轮实测。发现时**已经入库两轮**。

### 现象

第二十轮我新增了一个 SQL 对拍测试类，里面写了 **20 个 Go 锚点**（形如 `// Go: <某个 .go 文件> 的 <起始行>-<结束行> ⇒ …`）。
第二十轮、第二十一轮的验证清单里**都没有 `assertion-audit`** ⇒ 两轮都绿 ⇒ 提交了。

第二十二轮**提交前例行复跑全部门禁** ⇒ 🔴 **门禁红了**：

```
[F3] FavoriteSqlStatementParityTest.java:296  行号越界：`delete_favorite_folder_logic.go` 的 **336-378 行**，但该文件只有 96 行
```

⇒ 写的是 **logic 层**的文件名，指的却是 **model 层**的行号（`DeleteFolderCascade` 在 `service/favorite/rpc/internal/model/favorite_fact.go:336-378`）。

### 🔴 为什么「指错文件」比「没写锚点」更危险

| | 后果 |
|---|---|
| **没写锚点** | 读者知道「这条没核过」⇒ 会去查 |
| **指错文件** | 读者看到「`<文件名>` 的 `<起始行>-<结束行>` 行号」⇒ **以为已经核过** ⇒ 🔴 **证据是假的** |

⇒ 这正是 §3.3.1「行号指向**消费点**而非**声明点**」的加强版：
**行号在范围内 ≠ 锚点指对了文件** ⇒ 而「行号在范围内」恰恰是门禁能判的那部分。

### 教训

1. **门禁是「形式检查」，不是「语义检查」** —— 它能判「行号越界 / 锚点悬空」，
   **判不了「行号在范围内但指错了文件」** ⇒ 抓到了之后还要**全量语义核对**，
   不能「修门禁抓到的那两条 ⇒ 收工」。
   （本轮据此核了该文件的**全部 20 个锚点** ⇒ 另外 18 条逐条回真源确认语义正确。）
2. 🔴 **「本轮只改了台账」不等于「不用跑门禁」** ⇒ 新文件要**入库**，
   管它的门禁就要**当轮跑** ⇒ 「提交前全量复跑本域门禁」不是仪式，是**唯一的兜底**。
   ⇒ **跑哪个门禁要按「改了哪类东西」决定，不要按「这轮感觉动了什么」决定。**
3. ⇒ **判据**：
   > **新增/修改了带 Go 锚点的 Java 文件 ⇒ 跑 `assertion-audit`；
   > 修改了台账 ⇒ 跑 `ledger-lint` + `ledger-vs-reality`；
   > 改了产品代码 ⇒ 跑模块全量测试 + 相关探针。**
   ⇒ 三类**互不替代** ⇒ **全跑**。
4. ⇒ **门禁的「提示级」条目也要人看一眼**：本轮 2 条 favorite-rpc 的 I3
   （"Go 源码在锚点文件里找不到"）经核对**全是工具误报**
   （一个因 excerpt 未从锚点起始行对齐、一个因 excerpt 跨两段）
   ⇒ **不能因为「提示级」就当没看见** —— 分不清「误报」与「真缺陷」时，
   要**回真源核一遍**再决定归类。

### 同族

- `PIT-FAV-RPC-027`：**借来的工具只能当探针，不能当门禁**（⇒ 本域自己建门禁）
- `PIT-FAV-RPC-028`：**分层验证的排除项 = 下一层看不见的缝隙**
- `PIT-FAV-RPC-029`：「**修一处**」≠「**修完**」
- §3.3.2：**无出处的断言是高危项**（本条是它的变体：**出处写错了**也是高危项）

---

## PIT-FAV-RPC-031 —— **恒红的信号不携带信息**；「把红改成绿」必须拿出**可验证的证据** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-031` ⇒ **新号**：`PIT-FAV-52`


> 2026-10-26 第二十三轮实测。探针从「10 处语义差异 + 3 例 L1 不同」变成「0 + 0」——
> 但**中间那一步（判据本身对不对）比结果更重要**。

### 现象一：L1 信号**结构上恒红**

第二十轮起，L1 响应比对一直是红的（3 例）。看上去像「3 处真漂移」，
但真相比对是**精确字符串相等**（`same = (g == r)`）⇒ 两侧是**两个独立进程**
⇒ 同一时刻的毫秒值**必然不同** ⇒ **这个信号永远不可能绿**。

⇒ ⇒ 🔴 **一个永远红的信号，判别不了「修没修」** —— 它和「随机红」对决策的信息量是一样的。
⇒ **看到长期红的信号，第一件事是问「它本来能不能绿」**，而不是「怎么把它弄绿」。

### 现象二：探针的**判据**本身有盲区（把「值相同」误报成「值不同」）

第二十轮报「10 处语义差异」，其中 **6 处其实是误报**：
「写列集不同」**不等于**「落库值不同」—— 少写一列时 DB 会用该列的 **DEFAULT** 补上
（实测 `technical_offset` 的 `DEFAULT 0` ≡ Go 显式写的 `0`；`technical_received_at`/`delivered_at` 两侧都是 NULL）。

⇒ ⇒ 🔴 **探针只比「语句形状」时，必须先问「形状差异 ⇒ 行为差异吗」**；
判据要落到**行为层**（落库值 / 对外响应值）上。
⇒ 而要判落库值，就**必须知道 DDL**（DEFAULT / NOT NULL）⇒ **表结构快照只存列名是不够的**。

### 现象三：三个**静默**的工具缺陷（不报错、只是看不见）

| 缺陷 | 为什么静默 |
|---|---|
| `PARAM_RE` 只匹配带引号的值 ⇒ PG 的 `NULL` 参数（**不带引号**）**整条被丢掉** | 分不清「绑了 NULL」与「没提到」 |
| 值提取跑在 `normalize()` **之后**的 SQL 上 ⇒ `$N` 已被改写成 `?` | 每个值都成了 `?`，但**不报错** |
| 变异脚本只删列不删值 ⇒ 列/值**错位** | 新版判据判成「不可判定」⇒ **抓不到** ⇒ 假绿 |

⇒ ⇒ **探针自己也要被变异**（§3.3.3 的纪律对**自己写的探针**同样适用）：
一条「永远抓不到」的变异自检，比没有自检更危险。

### 教训

1. 🔴 **信号要能绿** —— 长期红的信号要**先怀疑判据**，再怀疑被测物。
2. 🔴 **「把红改成绿」必须拿出可验证的证据** —— 本轮用两条机制兜底：
   ① **DDL 感知**：值比较落到「有效值」（写了取绑定值；没写取 DEFAULT/NULL）⇒ 可**机械**判定；
   ② **带出处的显式登记**（`equivalences.tsv`）：机械判不了的（如「多投影一列但下游不用」）
   ⇒ 必须指到 Go 真源具体行，且**出处会被自动校验**（文件不存在 / 行号越界 ⇒ 该条**作废**）
   ⇒ **改开关不能把红变绿，出处能**。
3. 🔴 **阴性对照是「等价判定真的在工作」的证据** ——
   本轮新增 `drop_write_col_with_default`：删掉一个 **有确定性 DEFAULT** 的列
   ⇒ 比对器**必须**判等价、不报差异 ⇒ 它证明 DDL 感知判定**不是**「把差异全吃掉」。
   ⇒ **没有阴性对照的「等价判定」，等于把红改成绿的许可证。**
4. ⇒ **判据变更后，旧的变异自检可能已经失效**（本轮 `drop_write_col` 就是）⇒
   **判据一改，变异自检必须重写并重跑**，不能沿用上一版的「全绿」。

### 同族

- `PIT-FAV-RPC-028`：**分层验证的排除项 = 下一层看不见的缝隙**
- `PIT-FAV-RPC-029`：「**修一处**」≠「**修完**」
- `PIT-FAV-RPC-030`：**新写文件必跑管它的门禁**
- `PIT-GLOBAL-037` / `PIT-GLOBAL-040`（global 域）：变异自检与去噪词表的假阴性族

---

## PIT-FAV-RPC-032 —— **同一工作树里并发跑 maven ⇒ 假红**（探针会 `mvn package` + `mvn test`） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-032` ⇒ **新号**：`PIT-FAV-53`


> 2026-10-26 第二十三轮实测。**一次假红**，差点被当成代码回归。

### 现象

全量测试跑到一半，`favorite-api` 的 `FavoriteApiMvcPipelineTest` **34 / 36 例失败**
（而我的改动全在 `favorite-rpc`，`favorite-api` 根本不依赖那些 mapper）。

### 确诊

| 步骤 | 结果 |
|---|---|
| 失败当时的进程 | **另一个 maven 正在同一工作树里跑** —— `api-full-parity-probe` 的 `run.sh` 会 `mvn -o -pl service/favorite/favorite-api,... -am package`（打 fat jar）+ `mvn ... -pl service/favorite/favorite-api -am test` |
| 机制 | 两次 maven **同时写同一个 `target/`** ⇒ 一边在编译/打包、一边在跑测试 ⇒ **被测的产物是半成品** ⇒ 34 例红 |
| 单独重跑（**无并发**） | ✅ **105 例 / 0 失败 / 0 错误 / 3 skip · BUILD SUCCESS** ⇒ 假红坐实 |

⇒ ⇒ 🔴 **两个 maven 跑同一工作树 = 互相污染**，与代码无关。

### 教训

1. 🔴 **「跑红了」先查「是不是有人在同一条路上」** —— 尤其是本域的探针**自己就会调 maven**。
2. ⇒ **纪律**：本域的探针与全量测试**必须串行** ⇒ 探针里那步 `mvn package` 也不例外。
3. ⇒ **判据**：失败集中在**某个模块**、而自己的改动**不依赖**它 ⇒ 高度怀疑并发污染
   ⇒ **单独重跑一遍**再下结论。
4. ⇒ 与 global 域那条「守门脚本只读 pom / jar ⇒ 不碰 maven ⇒ 期间没有在别的进程里跑同一 reactor 的构建」是**同一条纪律**
   （跨域引用，不转录编号）。

### 同族

- `PIT-FAV-RPC-027`：**借来的工具只能当探针，不能当门禁**
- `PIT-FAV-RPC-031`：**恒红的信号不携带信息**

---

## PIT-FAV-RPC-033 —— **引用 `文件:行号` 前先确认该符号在【本分支】存在**：同名文件在不同分支上是两个实现 · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-033` ⇒ **新号**：`PIT-FAV-54`


> 2026-10-26 第二十四轮实测。**global` 的回执把这条变成了双向的**：他们指出我们的一条「依据」用错了符号，
> 而他们自己上一轮也差点犯同样的错（见他们的新坑）。

### 现象

本域台账第十九轮写过一句：
「favorite 的 client deadline **上界恒为 2s**（`FavoriteRpcClient.capByClientTimeout` 把 `min(ctx 剩余, 2s)` 压进去）」。

🔴 **这条依据是错的** —— 而且**符号本身存在**，只是**在另一个模块、决定的是另一件事**：

| | 是什么 | 决定谁 |
|---|---|---|
| `favorite-api` 的 `FavoriteRpcClient.capByClientTimeout` | **api → rpc** 那次调用的**客户端** deadline | 这次 gRPC 调用还剩多久 ⇒ **不决定** rpc 内部 SQL 的 R |
| `favorite-rpc` 的 `RpcInterceptor`（`service/common/...`） | **服务端每次调用自己新绑** `now + timeout` | **SQL 注入器看到的 R** |

⇒ ⇒ 「上界恒为 2s」**决定不了** R ⇒ ⇒ 论证链的第一环就断了
⇒ ⇒ **结论没错**（差 = δ、窗口不成立），**依据错了** ⇒ ⇒ 必须订正，否则下一次有人照它推会推出错的东西。

### 🔴 为什么这个坑特别隐蔽

1. **符号存在** ⇒ 不会报「找不到」⇒ 自动化校验**查不出来**（我本域门禁查的是「文件/行号能否解析」，不查「语义对不对」）
2. **名字合理** ⇒ 「客户端超时封顶」听起来就像会决定服务端预算
3. 🔴 **同名文件在不同分支上可能是两个不同实现** —— `global` 侧实测：
   同一路径 `service/common/.../RpcInterceptor.java` 在 `align/global` 与 `align/favorite` 上
   **行号与语义都不同**（他们 `:232-237` 的 per-call 公式 vs 本分支 `:170-172` 的服务端自绑）
   ⇒ ⇒ 引用**别分支**的行号 = 引用**另一份代码** ⇒ **假证据比没证据更坏**

### 教训

1. 🔴 **写进台账的每个 `文件:行号`，必须指「本分支上的那一处」** ⇒ 跨分支引用要**标注分支名**。
2. 🔴 **「符号存在」≠「符号决定你要论证的那件事」** ⇒ 论证链的每一环都要问：
   **它到底控制的是哪个量？**（本例：客户端 deadline 控制「调用还剩多久」，服务端绑定控制「SQL 预算 R」—— 两个量）
3. ⇒ **判据**：论证「某个上限/下界 ⇒ 所以安全」时，先确认**那个量是不是直接决定结果的量**；
   若是**间接**的（如「上界 ⇒ 实际不会超」），必须再问「**实际值会不会真的贴着上界**」。
   （本例的教训原话：**上界 ≠ 贴近**。）
4. ⇒ **被别域指出时**：先**核自己的台账**（别直接认领）—— 本次核完发现
   **台账早已在别处写对过**（「不要把『上界恒为 2s』当成依据」）⇒ ⇒ 真正要改的是**那一段的对照写法**。

### 同族

- `PIT-FAV-RPC-030`：**新写文件必跑管它的门禁**（门禁查形式，不查语义）
- `PIT-FAV-RPC-031`：**恒红的信号不携带信息**
- `PIT-FAV-RPC-032`：**同工作树并发跑 maven ⇒ 假红**

## PIT-FAV-RPC-034 —— **共享层改动的「描述」≠ 本域生产路径的「事实」**：同一个类，在池化 / 无池两条路径上是两个行为 · 状态：**已闭环** · 核对日期：**2026-10-29** · 影响当前轮次：**否**

> **旧号**：`PIT-FAV-RPC-034` ⇒ **新号**：`PIT-FAV-55` —— ⚠️ **本轮不适用的原因**：触发条件已消除：共享层已落 unwrap 修法 ⇒ `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java:75-79`（「修法：探测与调用前先 `unwrap` 到驱动自己的实现类」）+ `:209` `unwrapDriverStatement(statement)` ⇒ 池化路径上毫秒精度不再失效 ⇒ 本轮不适用


**触发**：2026-10-26 第二十五轮 —— `global` 第四十一轮登记 **E62**（毫秒精度在**连接池代理**路径上不生效），
要求 favorite 域核对台账里凡写「毫秒精度已生效」的表述并加限定。

### ✅ 状态：**已修**（2026-10-26 合并当天 · 第三十轮 · 见 `docs/DRIFT/favorite/rpc.md` §39）

| 项 | 内容 |
|---|---|
| **谁修的** | 🔴 **`global` 域**（第四十五轮 `180f865`，落在**共享面** `service/common`）—— 把毫秒探测改成**先 unwrap 到驱动实现类** ⇒ ⇒ 毫秒 API 在池化路径上**可达** |
| **本域做什么** | ✅ **零改动共享层**；只做三件事：① **重新量一次**（在真合并树上，实测驱动收到**原样 R**）② **守门改写**（钉「毫秒路径生效」的新事实）③ **台账订正**（(A)/(B) 两个「差」互换） |
| **对本域的可观察影响** | 🔴 **不再是 0** —— 合并前 (A) 旧↔新 = 0 / (B) Go↔RAG = δ；合并后 **(A) = δ**（新实现取消得更早，最多早 999ms）/ **(B) = 0** ⇒ ⇒ **与 Go 的取消时刻差从 δ 变成 0** |
| **守门的处置** | ✅ 两条断言**没有**简单改成 `2→1` / `2000→1699`（那些数**会随时序漂移**）⇒ 改成**用「R 的取值窗口」判定** ⇒ 确定性 ⇒ 且秒级路径必然落不进那个窗口 ⇒ **变异自证：旧秒级共享层上转红，且报得准** |

### 现象

跨域回执转述共享层改动时，转述的是**那版代码的意图**：
「反射探测到 `setQueryTimeoutMs` ⇒ 取消时刻 == deadline 时刻」。
本域照抄进台账 ⇒ 台账里就多了一句**在本域生产路径上根本不成立**的话。

🔴 机理（同一个共享类，**两条路径两个行为**）：

| 路径 | statement 的运行期类 | `statement.getClass().getMethod("setQueryTimeoutMs", long.class)` | 实际行为 |
|---|---|---|---|
| **池化**（Spring Boot 默认 HikariCP ⇒ **生产路径**） | `HikariProxyPreparedStatement` ⇒ **只有** `getQueryTimeout()` / `setQueryTimeout(int)` | **`NoSuchMethodException`** ⇒ 探测失败 | **静默回退**秒级向上取整 |
| **无池**（`DriverManagerDataSource`） | `org.postgresql.jdbc.PgPreparedStatement` ⇒ 实现类**有** `setQueryTimeoutMs` | 成功 | 毫秒精度生效 |

⇒ 🔴🔴 **「真 PG 守门」≠「真生产路径守门」**：
`global` 的 E46 守门用 `DriverManagerDataSource`（**无池**）⇒ 反射成功 ⇒ **守门绿、生产坏**。
⇒ 而「反射探测不到就退回秒级并记 WARN」这个设计**恰好把失败变成静默** ⇒ 没有任何报错。

### 确诊（本域怎么查出来的）

1. **静态先核机制**：`javap` 三个 HikariCP 版本 + 两个 pgjdbc 版本 ⇒ 代理类确实没有毫秒 API。
2. 🔴 **运行时观测**：在**真实 MyBatis 插件链里**挂一个只读探针（拦 `StatementHandler.prepare`），
   记录 statement 的**运行期类**与**有效超时值** ⇒ 拿到的是代理类 ⇒ 毫秒 API 不可达。
3. 🔴🔴 **叠加对方那版实现复跑**：把共享层的注入器 + 它的测试**只读 overlay** 上来
   ⇒ **同一组断言仍然全绿** ⇒ ⇒ 「带毫秒路径的实现」与「旧秒级实现」**给出相同的有效值**
   ⇒ ⇒ 毫秒路径**根本没被走到** ⇒ 结论从「转述」升级成「本域自己跑出来的」。

### 🔴 判别器：别把两条路径**抹平**了

pgjdbc 的 `setQueryTimeout(int)` **字节码层面内部委托**给 `setQueryTimeoutMs(seconds * 1000)`
⇒ ⇒ 代理层那个 `getQueryTimeout()`（int 秒）在**两条路径上看起来一样**（都被抬成整秒）
⇒ ⇒ **用它当判别器 = 恒真 = 没有信息**。
⇒ ✅ 唯一能区分的是**驱动内部的原始毫秒**（`getQueryTimeoutMs()`）⇒ 但**代理类不暴露它**
⇒ ⇒ 必须 **unwrap 到驱动实现类**才读得到。
⇒ 🟢 **阳性对照**：在**无池**那条连接上直接 `setQueryTimeoutMs(1700)` ⇒ 原始毫秒实测 = **1700**
⇒ ⇒ 与生产路径上的 **2000** **明确不同** ⇒ ⇒ 这条断言**真的在区分**，不是恒真。

### 教训

1. 🔴 **引用跨域共享层改动的「效果」前，先问：这条效果在【我这条路径】上成立吗？**
   路径差异点清单：连接池代理 / 事务风格 / 框架自动配置 / profile 开关 / 连接池是否 lazy …
   ⇒ 本域若不核，**台账会替共享层把话说满** ⇒ 下一轮有人照着台账去改代码 ⇒ **按错的假设改**。
2. 🔴 **守门要钉「本域生产路径」，不是钉「机制能工作」** ——
   「真 PG」是**存储层**真实，「真生产路径」还要包括**连接池 / 事务 / 代理 / 开关**。
3. 🔴 **找判别器时先确认目标 API 会不会把两条路径抹平** ——
   本例 `getQueryTimeout()` 就被抹平了 ⇒ 必须往下挖到**驱动内部字段**（unwrap）。
4. 🔴 **判别器要配阳性对照** —— 「断言恒真」和「断言有效」在结果上长得一模一样（都绿）。
5. 🟢 **共享层单写权的域，改法不动、只加限定** ——
   本轮只改**本域台账 + 本域测试**，共享层**只读 overlay** 跑完就还原（`git checkout`）。
6. ⇒ **被别域要求核对时，先核自己的台账**：本轮核完发现结论**不用改**（零红、零需改动），
   **要改的是「理由」** ⇒ ⇒ 别把「加限定」写成「推翻结论」。

### 同族

- `PIT-FAV-RPC-033`：**符号存在 ≠ 符号决定你要论证的那个量**（同名文件跨分支两个实现）
- `PIT-FAV-RPC-030`：**新写文件必跑管它的门禁**（门禁查形式，不查语义）
- `PIT-FAV-RPC-031`：**恒红的信号不携带信息**；找判别器时也要防「恒绿」
- `PIT-FAV-RPC-026`：**改了被测物必须同时改守门**（overlay 时主文件与测试要一起换）

## PIT-FAV-RPC-035 —— **表格结构会静默塌陷**：内容一个字没丢，但渲染出来是错的；而且**别人给你的「我这边是干净的」会过期一轮** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-035` ⇒ **新号**：`PIT-FAV-56`


**触发**：2026-10-26 第二十六轮 —— `global` 第四十四轮登记**结构层缺陷类**，
新增 **`check-ledger-tables.py`**（global 域，在 `align/global` 分支的 `tools/` 目录下）并提醒 favorite 域复跑。

### 形态

往台账里**插一行**时，如果落点落在**表格块内部**，或用**长文本 `replace`** 把上一行的**行首**吃掉
⇒ 那一行变成「**没有行首的内容行**」⇒ GFM 把它当**上一行的 lazy continuation**
⇒ **表格结构静默塌陷**（该行被并进上一行的单元格）。

🔴 为什么它**比别的缺陷更隐蔽**：

1. **内容一个字没丢** ⇒ 逐行读台账**完全正常** ⇒ 只有**渲染后**才看出不对。
2. **不违反任何内容断言** ⇒ 内容层守门（`check-ledger-selfcheck.py` 第 1~4 层看引用/断言/数字）**查不出来**。
3. **破坏者是落账脚本本身**（每轮用「长文本 `replace`」插一行 ⇒ 替换目标串恰好是上一行的行首）
   ⇒ global 实测**连续三轮每轮破一次**，**前三轮都没人发现**。
4. ⇒ 必须**单开一个结构层守门** ⇒ 判据：**表格块内部不得出现「无行首的内容行」**。

### 本域怎么接住的

1. 🔴 **别直接用别人的工具当自己的门禁** —— 不是它不对，而是**扫描范围**与**变异靶子**
   按**作者域**写死了：
   - `--all` 会扫全仓 `docs/` ⇒ **别人的文件**（实测：comment 域 10 处）也会让**我的门禁红**
     ⇒ ⇒ **别人的缺陷不该让我的门禁红**；
   - `--mutation-test` 硬编码 global 自己的 `_README.md` ⇒ 在本分支上**「变异前置失败」**
     ⇒ ⇒ **自证机制在本分支上根本用不了**。
   ⇒ ⇒ **收窄范围 + 换自己的靶子**，判据原样借用。
2. 🔴 **别人给你的「我这边是干净的」会过期一轮** ——
   global 的结论基于**我的第二十四轮 HEAD `a4a7ee0`**，而我**第二十五轮**又改了台账（新增 §34 的 4 张表 + 改写 §28.1 三列表）
   ⇒ ⇒ **跨域回执里的「已核对」必须问「在哪个 commit 上量的」**。
3. 🔴 **工具的命中要逐条核证**（是台账的问题还是工具自己的 bug）——
   抽验一处即可定性：第 78 行是表格行、第 79/80 行**确实没有行首 `|`** ⇒ **真塌陷**。
4. ✅ **副本还要在真实数据上交叉验证** —— 用副本的判据跑别人那份已知有塌陷的文件
   ⇒ **10 处、行号逐条一致** ⇒ ⇒ 证明**没把判据抄坏**（不是「只在自己域上绿」）。
4. 🔴 **跨分支的文件不能写成仓内路径** —— 本轮我把 global 分支上的 `check-ledger-tables.py`
   写成了 `tools/` 开头的**仓内路径** ⇒ 本分支**没有这个文件** ⇒
   本域「仓内路径存在性」门禁**当场判为悬空**（EXIT=1）⇒ ⇒ **改写法**：
   只写文件名 + 标注「在哪个分支的哪个目录下」⇒ ⇒ 门禁重新 EXIT=0。
   ⇒ 🟢 顺带证明了一件事：**门禁抓的是我本轮自己写的东西**，不是走过场。
5. ✅ **收窄后的守门要自带变异自证**（M1 吃行首 / M2 插进表格块 / M3 阴性对照）
   ⇒ ⇒ 非空转，且**红在目标行上**。

### 教训

1. 🔴 **改台账用行级插入（按行号/锚点整行），别用长文本 `replace`** ——
   本域每轮都在改 §0 那张**时间线表**，正是这个坑的暴露面。
2. 🔴 **「结构」是一个独立的缺陷维度**，内容层守门覆盖不到 ⇒ 每个域都要有自己的结构层门禁。
3. 🔴 **守门要按「谁写谁负责」收窄范围**，别图省事用 `--all` ⇒ 否则会被别人的缺陷绑架。
4. 🔴 **跨域回执里引用的结论要带 commit** ⇒ 对方量的那一版可能已经不是你现在这一版。

### 同族

- `PIT-FAV-RPC-027`：**借来的工具只能当探针，不能当门禁**（⇒ 本条是它的**收窄版**做法）
- `PIT-FAV-RPC-030`：**新写文件必跑管它的门禁**（门禁查形式，不查语义）
- `PIT-FAV-RPC-022`：**台账里最危险的断言是「数字」**（结构层同理：**最危险的是「看起来没丢」**）

## PIT-FAV-RPC-036 —— **一条断言一个测试方法**；跨域回执给的**量化口径**也要核；**理由也是结论的一部分** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-036` ⇒ **新号**：`PIT-FAV-57`


**触发**：2026-10-26 第二十七轮 —— `global` 第四十五轮把共享层毫秒探测改成
「**先 unwrap 到驱动实现类**」⇒ **毫秒精度在生产路径上真的生效了**
⇒ 我第二十五轮故意留下的守门「按设计转红」。

### 形态

1. 🔴 **同一个测试方法里放多条断言 ⇒ 前面的失败会遮住后面的**
   ⇒ 当时只知道「红了」，**不知道红在哪一条、实际值是多少**。
   ⇒ 而**实际值恰恰是这轮最关键的证据**（它直接证伪了对方的量化口径）。
   ⇒ 修法：**一条断言一个测试方法**。
2. 🔴 **跨域回执给的「量化口径」不能照抄**
   对方写「毫秒路径把取消时刻从『deadline + (0~999ms]』收紧到『**deadline + δ**』⇒ 差 = δ」
   ⇒ **错**：实测驱动收到的是**原样 R（1691ms）** ⇒ 取消时刻 = `t_exec + R`
   ⇒ 而 `R = deadline − t_exec` ⇒ ⇒ **`t_exec + R ≡ deadline`** ⇒ ⇒ **差 = 0**。
   ⇒ 根因：把「**秒级取整的滞后**」与「**毫秒路径的滞后**」混为一谈。
3. 🔴 **「结论不变的真正理由」也是结论的一部分**
   对方说「结论不变的真正理由**仍是**『handler 不烧预算 + 快 SQL ⇒ 那个 ≤999ms 窗口里没有慢 SQL 可跑』」
   ⇒ 在毫秒路径生效后，那个窗口**本身已经不存在** ⇒ 这条理由**退化为冗余**
   ⇒ 它该被重新定位成「**毫秒路径若再次失效时**的唯一结构性兜底」。
4. 🟢 **恒等式比公式更硬**
   `t_exec + R ≡ deadline` ⇒ 结论**不依赖** deadline 的来源公式
   ⇒ ⇒ 连「两个分支的 R 公式不同」这件事都**不再重要**。

### 教训

1. 🔴 **守门要「一条断言一个测试方法」** ⇒ 失败信息才携带**定位信息 + 实际值**。
2. 🔴 **收到跨域的量化结论，先问「这个量是怎么算出来的」** ⇒ 能写成一个**恒等式**就别用数值区间论证。
3. 🔴 **机制变了，理由必须跟着变** ⇒ 否则台账会拿一个**已经不成立的理由**继续挡着，且让人误以为它在起作用。
4. 🟢 **「转红信号」要提前埋好**：第二轮就写明「本断言会转红，那是台账该更新的信号，不是回归」
   ⇒ 合并那天不用重新判断「这是不是回归」。
5. 🔴 **借用别域的探针跑自己的分支 ⇒ 必须把「仓库根解析」改到自己分支**
   —— 否则脚本按自身位置解析仓库根 ⇒ 跑的是**别分支的共享层** ⇒ **结论张冠李戴**。
   ⇒ ⇒ 改动只有**路径解析**一处 ⇒ **证据仍算本域自己的**（第二十八轮实测：把 `RAG_ROOT` 指向本分支
   ⇒ 跑出的 `failed_fast/code/outgoing_carrier/sql_carrier` 与 global 第四十六轮**逐键一致**）。
6. 🔴 **「drift 键钉形态」的守门 ⇒ 漂移被修好时它会红**
   ⇒ ⇒ 必须**提前写明「红了不是回归」** ⇒ 否则合并那天会有人去「修」一个**其实已经修好的东西**。
   ⇒ ⇒ 还要提前约好：**谁负责回写哪一份台账**（global 域 + 本域各一份 ⇒ 销账要同步）。
7. 🔴 **「影响很小」要拆成【概率小】与【后果轻】两件事分别说**
   —— 本域 E65：概率小（R = 整预算 − δ）· 后果轻（出向调用在**事务外**）⇒ **两件事都成立**才叫「影响小」
   ⇒ ⇒ 只说其中一件 ⇒ 另一件被漏掉 ⇒ 后果被低估。

### 同族

- `PIT-FAV-RPC-031`：**恒红的信号不携带信息**（反过来：**预先埋好的红**是**有意设计的信号**）
- `PIT-FAV-RPC-034`：**共享层改动的「描述」≠ 本域生产路径的「事实」**（本轮是它的**下一集**：修好之后描述要再更新一次）
- `PIT-FAV-RPC-033`：**符号存在 ≠ 符号决定你要论证的那个量**（本轮是它的**加强版**：**恒等式** > 公式）

---

## PIT-FAV-RPC-037 —— **工具的输出是「线索」不是「结论」**；**「盘点归零」的口径外还有一层从未被机检覆盖**；**共享面不可改 ⇒ 域内替代方案** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-037` ⇒ **新号**：`PIT-FAV-58`


> **触发轮次**：2026-10-26 第二十九轮（`align/favorite` · 承 `a1ffdb6`）
> **一句话**：别的域交给你一条「工具命中」，**第一件事是核它是不是真的**——
> 本轮那条命中**是真的**（套错类型），但**它的「解法方向」是错的**
> （工具暗示「改锚点指向真正的文件」，真相是「**锚点本来就对，错的是那句断言**」）。

### 现象

`global` 域把「**锚点文件里找不到台账断言的实体**」做成了全仓可跑的补充判据（E68，落在 `97a1fa8`），
用它跑全仓 ⇒ **唯一一条命中在 favorite 域**：

```
favorite_authority.go:74-282 —— 锚点文件里找不到台账断言的实体：
AuthorityLookup（实际在 service/common/communityfact/authority_http.go）
```

### 核证过程（**逐条，不采信**）

| 问的问题 | 查了什么 | 结果 |
|---|---|---|
| `AuthorityLookup` 声明在哪 | `service/common/communityfact/authority_http.go:18` | **共享层** |
| **favorite 用不用它** | `grep -rn "AuthorityLookup\|NewAuthorityHandler" service/favorite/` | **零命中** ⇒ favorite 根本不用 |
| favorite 用的是什么 | `service/favorite/rpc/internal/authority/http.go:17` `Reader` · `:21` `ReaderV2` | **自持一套** |
| 谁在用共享层那个 | `service/like/rpc/cmd/fact-authority/main.go:71` · `service/comment/rpc/cmd/fact-authority/main.go:71` | **like / comment** |
| RAG 侧有没有实现它 | `FavoriteAuthorityLookupService.java:44`（**不 implements 任何接口**） | **没有** ⇒ 与 Go 一致 ✅ |
| 那个锚点对不对 | `favorite_authority.go` 共 **282 行**，`:74` `parseAuthorityID` … `:206-282` `authoritativeFavoriteFact` | **完全正确** ✅ |

⇒ 🔴 **工具输出的是「锚点文件里少了这个符号」**，
但**它没告诉你这个符号本来就不该在那里** ⇒ ⇒ **顺着工具的输出改锚点，会把一条对的东西改错**。

### 教训

1. 🔴 **工具的第一版输出是「线索」不是「结论」**
   ⇒ 必须**逐条核证是「台账/文档的问题」还是「工具自己的口径问题」**
   ⇒ 本轮：**问题是真的**（真套错类型）· **但解法方向要自己判**（正解是**改断言**，不是改锚点）。
2. 🔴 **「盘点归零」这句话要问「在什么口径下归零」**
   —— 第二十轮已经栽过一次（只覆盖已建探针的层 ⇒ 补上 SQL 层又抓出 3 处）；
   本轮是**同型的第二集**：台账口径之外**还有一层「域内文档」**从未被任何机检覆盖
   ⇒ 一覆盖就抓出 **8 处**（1 条套错类型 + 2 条过期陈述 + 5 条悬空路径）
   ⇒ ⇒ **「域内可修漂移 = 0」在补上这一层之前不成立**。
3. 🔴 **共享面不可改 ⇒ 域内替代方案**（不是「等别人改」）
   —— `global` 的 `LEDGER_FILES` 在**共享面**（global 单写权）⇒ 本域**加不进去**
   ⇒ 本域做法：`EXTRA_SCOPED_FILES` 把**本域自有**的文档纳入**本域自己的**门禁
   ⇒ **效果等价、零共享面改动** ⇒ ⇒ **回执里要明确说「这条我们自行闭环了」**。
4. 🔴 **搬别人的判据 ≠ 照抄**
   —— `global` 的变异自证是「**写盘 → 跑 → 还原**」⇒ 中途崩了会**留下变异过的台账**
   ⇒ 本域改成**纯内存**（`_local_windows` 从 `texts` 取，不再读盘）⇒ **零副作用**。
5. 🔴 **搬来的判据要「升为门禁级」之前，先自证三件事**
   ① **唯一命中是真缺陷**（不是假阳性）② **修完之后全域 0 条**（零已知误报）③ **变异双向通过**（阳性必报 + 阴性必不报）
   ⇒ 本轮三条都成立 ⇒ 才敢从 `global` 的**提示级**升到本域的**门禁级**。
6. 🔴 **给门禁加「惰性自检」**
   —— 判据可能因为**口径与本域写法不匹配**而**悄悄失效**（本域台账多用「表格列式」写法 ⇒ 符号与锚点相隔上百字符 ⇒ 取不到候选）
   ⇒ ⇒ 本域门禁会在「一个候选都提取不到」时**主动出提示级信号**：「这一层在本域几乎是惰性的，**别把它当『已对齐』的证据**」。
7. 🟢 **零行为改动 ⇒ 盘点分类不变**
   —— 文档层缺陷**不计入** A/B/C/D（A 类是 Go ↔ RAG 的**行为**漂移）⇒ 但**要更新「效力边界」那段话**。
8. 🔴🔴 **「跑绿」≠「判据活着」** —— 必须做**牙齿检查**：把**真实缺陷**放回去，看门禁**抓不抓得到**
   —— 本轮第一次跑**只报出 5 条路径、漏了真正要抓的那一条** ⇒ 查出 **3 个坑**（全部已修 + 各配一条变异自证）：
   ① **去噪会跨位置误杀**：同一个锚点在 A 处是「历史记述」（被去噪）· 在 B 处是**活的错断言**
      ⇒ 旧写法「任一位置被叙述 ⇒ 整条跳过」⇒ **B 处根本没进判定** ⇒ ⇒ 改为**按位置拆开**；
   ② **候选集不能跨出现点求并集**：同一锚点的多个出现点窗口拼起来 ⇒ 「全部缺失才报」这条规则
      会被**别处的正确候选**顶掉 ⇒ ⇒ 改为**按出现点独立判定**；
   ③ **新加的去噪规则会把自己的变异注入吞掉** ⇒ 变异自证变成**恒红的假自证**
      ⇒ ⇒ 注入行必须**隔两个空行**，且要在**真实数据**上再验一次。

### 同族

- `PIT-FAV-RPC-027`：**借来的工具只能当探针，不能当门禁**（本轮是它的**加强版**：借来的**判据**也要核 + 改造后才敢当门禁）
- `PIT-FAV-RPC-033`：**符号存在 ≠ 符号决定你要论证的那个量**（本轮是它的**镜像**：符号**不在**那里 ≠ 锚点指错了文件）
- `PIT-FAV-RPC-035`：**别人的「我这边是干净的」会过期一轮**（本轮是它的**同型**：FileTree 文档里两句「当时为真」的陈述已过期）
- `PIT-FAV-RPC-031`：**恒绿的信号不携带信息**（本轮是它的**同型**：门禁「跑绿」≠「判据活着」⇒ 要做**牙齿检查**）
---

## PIT-FAV-RPC-038 · 🕳️ **「U+FFFD」不一定是编码损坏 —— 有的是「被测对象」；而且损坏的那个汉字常常「不可考」** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-038` ⇒ **新号**：`PIT-FAV-59`


### 现象

本轮扫 `grep -rln $'\xef\xbf\xbd'` 得 6 处命中 ⇒ **逐行看上下文**后发现 **5 处真损坏 ＋ 1 处是故意的**：

| 形态 | 例子 | 真相 |
|---|---|---|
| **故意的** | `article-api/…/ArticleApiMvcPipelineTest.java:1143` 的 `.putExtInfo("␦", "4")` | 该用例用 U+FFFD 当 `ext_info` 的 **key**，钉死「按**码点序**（= Go 的 UTF-8 字节序）输出」（`a < 中 < U+E000 < **U+FFFD** < 😀`）⇒ **改它直接破坏测试语义** |
| **故意的** | `users/user/user-api/…/UserApiGoStyleBodyContentTypeTest.java:376` 的 `assertFalse(body.contains("␦"))` | U+FFFD 是**断言的被测对象**（G1 域） |
| **故意的** | `docs/Pitfalls/message/rpc.md:911` 的 `CONTAINER␦: unbound variable` | **文档在记录故障现象本身**（UTF-8 locale 下 bash 把多字节字符吞进变量名）⇒ 替换符就是**病症样本** |
| **真损坏** | `FavoriteRpcLabels.java:9` · `FavoriteItemMapper.java:78` · `FavoriteRpcSupport.java:70` · `FavoriteFactSemanticsTest.java:37` · `CommentAuditPublisher.java:19` | 各丢 **1 个汉字** |

### 为什么会踩

① **「扫描到 U+FFFD ⇒ 一定是编码损坏」是错的** ⇒ **「全仓 U+FFFD 归零」不是合法目标** ——
有些 U+FFFD 是**被测对象**、是**故障样本**、是**文档刻意引用的病症**。
② **损坏的那个汉字常常「不可考」**：本轮 5 处里 4 处的原始字符**无法还原** ——
最早的干净提交**根本没有那几句**（是某次「新增行」引入的）⇒ **无前版可取**；
而会话快照里**已经是坏的** ⇒ ⇒ **「搜到了」≠「有原文」**。
③ **字节数能告诉你「丢了几个字」，但告诉不了你「是哪个字」**：
**3×FFFD = 1 个完整汉字**（UTF-8 3 字节被逐字节替换）；
**2×FFFD = 3 字节汉字丢了末字节**（本仓两种形态都出现过）。

### 怎么不再踩

① **先逐行看上下文，再决定动不动手** —— 判据：它是**被测对象 / 故障样本 / 文档引用**吗？是 ⇒ **不动**。
② **找干净前版**：`git log --all -- <file>` ⇒ 逐提交 `git show <c>:<file> | grep -c FFFD`
⇒ 定位**从哪一版开始坏**。⚠️ 若是**新增行** ⇒ **无前版可取** ⇒ 只能**按语义重建**。
③ **重建依据的优先级**：
① **同包干净文件**的同款措辞（本例：`FavoriteItemMapper` 那处照同包 `FavoriteItemEntity` 类注释的「有一个空修订」重建）
② 本文件自身的术语（本例：`FavoriteRpcLabels` 的类注释写明「label **取值**常量」⇒「拼成两个**值**」）
③ 唯一自然填充 ⇒ ⇒ **只改注释、不改行为**。
④ **别把 git 历史 / 会话目录当「原文来源」的保证**：`/Users/pr/.minimax/v2/sessions/` 里 grep 目标短语
**可能命中已经损坏的快照** ⇒ 命中 ≠ 有原文。
⑤ **引用损坏形态作证据时用可见转义写法**（`␦␦` / `\uFFFD`），**不要**把真 U+FFFD 字节写进台账 ——
否则后续「全仓 U+FFFD 扫描」会把**证据**也标成待修项（噪声）。
🆕 **实证（G4 层 · 2026-10-27 · 本条的 ⑤ 是它踩出来的）**：G4 在台账里**引用病症样本**时，
把我回信里**带替换符的原文原样抄了进去** ⇒ ⇒ **它自己的台账里多了 10 个真 U+FFFD**
（字节级扫描当场抓到）⇒ ⇒ **「修 U+FFFD」的动作本身可以引入新的 U+FFFD** ⇒ ⇒
**引用一律用描述**（写 `〈FFFD×3〉`，连着几个写几）⇒ **文件里不留真替换符**。
⚠️ **与「故意保留」是两回事，别混**：
**故意保留** = **被测对象**（U+FFFD 本身是被断言的东西 ⇒ 改了直接废测试 ⇒ 必须留）
· **引用** = **台账叙述**（把病症写进文档 ⇒ 完全可以写成描述 ⇒ **不该留**）
⇒ ⇒ **两者都不该在「台账叙述」里留真替换符** —— 前者留的是**测试代码**，后者留的是**文档**。
⑥ **「扫描 ⇒ 归零」这类目标，要先问「这个目标合法吗」**：本例答案是**不合法** ⇒ ⇒ **登记 ≠ 缺陷**：先分类，再决定修不修。

---

## PIT-FAV-RPC-039 · 🕳️ Go 的 yaml **顶层 `Timeout`** 绑的是 **zrpc 服务端超时**（**不是** rest 的 HTTP 预算）—— 而且它**不靠「同名遮蔽」成立** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-039` ⇒ **新号**：`PIT-FAV-60`


### 现象

写 RAG 域的 timeout 对照时，我第一反应是：「`RpcServerConf` 内嵌了 `service.ServiceConf`，而 `ServiceConf` 里也有 `Timeout`
⇒ **同名遮蔽** ⇒ yaml 顶层 `Timeout` 绑到**外层**那个（= 服务端 70s）」。

🔴 **这个机制解释是错的**：`core/service/serviceconf.go:32-40` 的 `ServiceConf`
**根本没有 `Timeout` 字段**（只有 `Name` / `Log` / `Mode` / `MetricsUrl` / `Prometheus` / `Telemetry` / `DevServer`）
⇒ **不存在同名遮蔽** ⇒ yaml 顶层 `Timeout` **直接**绑到 `RpcServerConf.Timeout`（`zrpc/config.go:46`，默认 2000ms）。

### 为什么会踩

① **「内嵌结构体同名遮蔽」是 Go 的通识 ⇒ 不查就套上去** —— 但**遮蔽的前提是「两边真有同名字段」** ⇒
本例**下层压根没有那个字段** ⇒ ⇒ 套上去就变成**错的机制解释**。
② ⚠️ **两种解释在结论上碰巧一致**（都得出「顶层 `Timeout` ⇒ 服务端 70s」）⇒ ⇒
**错机制不会在结论上暴露** ⇒ 只有**把推导链一步步写出来**才会发现 ⇒ ⇒
**「结论对」不等于「推导对」**。
③ ⚠️ **同类易混**：service / rpc 组件的 yaml 里可能有多处 `Timeout`：
`article/rpc/etc/article.yaml:3`（顶层 70000 = **zrpc 服务端**）与 `:90`
（落在 `SecurityConf` 段 = **security-rpc 客户端** 10000）⇒ **两处语义完全不同** ⇒ 别混。

### 怎么不再踩

① **凡要说「某某遮蔽了某某」，必须把「被遮蔽的那个字段确实存在」也指出来（`文件:行号`）** ——
指不出来 ⇒ **不是遮蔽，是结构不同** ⇒ ⇒ **写不出那个行号，就说明机制没查清**。
② **写 Go 事实要带消费点**（AGENTS.md §3.3.1）：本例声明点 = `zrpc/config.go:46`，
**真源侧消费点** = `service/favorite/rpc/favorite.go:40` `DisableNativeRpcTimeout(&c.RpcServerConf)`
＋ `:49` `NewUnaryServerInterceptor(rpcTimeout, ...)` ⇒ **声明点 + 消费点两条都有**才算坐实。
③ **⚠️ 本条的适用范围**：Go 侧 **rpc 组件**的 yaml **顶层** `Timeout` ⇒ **zrpc 服务端超时**；
**api 组件**的 yaml 顶层 `Timeout` ⇒ **rest HTTP 预算**（`rest/config.go:55` 默认 3000ms）。
**两者不是同一个字段** ⇒ ⇒ 「api 是 3s ⇒ rpc 也是 3s」这种推断**不成立**，必须各自去 yaml 查。

## PIT-FAV-RPC-040 · 🕳️ `grep` 说「binary file matches」⇒ **不一定是编码损坏** —— 可能只是**故意夹具里的真实 NUL 字节**（且**不可「修」**） · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-RPC-040` ⇒ **新号**：`PIT-FAV-61`


> **登记时间**：2026-10-28 · G3 lane（落码 Testcontainers 宿主口钉口时**顺手撞到**）
> ⚠️ **本条与 `PIT-FAV-RPC-038`（U+FFFD 那条）同款处置**：**故意夹具 ⇒ 保持原样 ⇒ 不许「修」**。
>
> 📌 **订正注记（2026-10-28 10:2x · G3）—— 上面那句「不许修」【过度推广】，且下面第 2 条处置的【理由】是错的**：逐条见「🔴 订正」一节。**原结论（① `binary file matches` ≠ 编码损坏 · ② 4 个 NUL 是故意夹具 · ③ 字节往返安全）全部保留不改写**。

### 现象

`grep -rl "new PostgreSQLContainer" service/favorite/favorite-rpc/src/test/java` 有一条输出是

```
Binary file service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteDdlParityTest.java matches
```

⇒ ⇒ 而 `grep` 的同行其它文件都正常列出 ⇒ ⇒ **看起来像「这个文件坏了」**。

### 实测（三步，逐层排除「损坏」这个解释）

| 判据 | 实测 | 结论 |
|---|---|---|
| 字节级 UTF-8 解码 | **OK**（无 `UnicodeDecodeError`） | **不是编码损坏** |
| `U+FFFD`（`b'\xef\xbf\xbd'`）计数 | **0** | **不是 U+FFFD 那类替换符** |
| `b'\x00'` 计数 | **4**（第 136 / 183 行，各 2 个） | ⇒ ⇒ **真实 NUL 字节** |
| 字节上下文 | `names.put(p[1] + "<NUL>" + p[2] + "<NUL>" + payload, p[3]);` | ⇒ ⇒ **Redis 复合 key 的分隔符** |

⇒ ⇒ **结论**：这 4 个 NUL 字节是**故意的测试夹具**（用 `\0` 拼复合 Redis key）⇒ ⇒ **不是缺陷**。

### 🔴 后果与处置

1. **`grep` 会把该文件判成 binary** ⇒ ⇒ `grep -n` 打不出行号、`git diff` 按二进制处理（只报「Binary files differ」）
   ⇒ ⇒ **排查时要先想到这一条**，别以为是「文件损坏」。
2. 🔴 **不要「修」** ⇒ ⇒ 把它换成 `\\0` 转义、或删掉 ⇒ ⇒ **会改变被测语义**（Redis key 的字节内容变了）
   ⇒ ⇒ 同 `PIT-FAV-RPC-038` 的处置：**故意测试数据 ⇒ 保持原样**。
3. ⚠️ **但脚本化改文本时必须按字节往返**
   ⇒ ⇒ 用 UTF-8 读写（`read_text(encoding="utf-8")` / `write_text(..., encoding="utf-8")`）**能**逐字节往返（U+0000 是合法 UTF-8 码位）⇒ ⇒ **不会**引入新的损坏
   ⇒ ⇒ 本轮迁移脚本已加**断言「NUL 字节数不变」** ⇒ ⇒ 改完回读校验，命中 0 处异常。

### 🔴 订正（2026-10-28 10:2x · G3）—— 第 2 条处置的**理由是错的**，且真 NUL 的**真正代价不是语义**

⚠️ **本节是订正，上面原文一律保留不改写**（按「改旧判断 ⇒ 保留原值 + 加订正注记」纪律）。

1. 🔴 **「不要把它换成 `\0` 转义 ⇒ 会改变被测语义」⇒ 这条【错】** ——
   **Java 源码里的 `"\0"` 是八进制转义 = `U+0000` 字符** ⇒ ⇒ **运行时字节与真 NUL 完全一致** ⇒ ⇒ **被测语义不变**（Redis 复合 key 的字节内容不变）⇒ ⇒ **零行为变化**。
   **实测（2026-10-28 10:2x）**：改成 `"\0"` 后跑
   `mvn -B -o --fail-at-end -pl service/favorite/favorite-rpc -am test -Dtest='FavoriteDdlParityTest'`
   ⇒ **1 条 / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS**（8.7 s，**真 PG + Testcontainers 活体** ⇒ 不是「编译过」而已）。
2. ⚠️ **开头那句「本条与 `PIT-FAV-RPC-038`（U+FFFD 那条）同款处置 ⇒ 保持原样 ⇒ 不许「修」」⇒ 【过度推广】** ——
   两条的**处置不同**：
   - **U+FFFD 那条（PIT-FAV-RPC-038）** ＝ **题材 / 被测对象** ⇒ 真字节**必须留**（改了断言永远为真 ⇒ 静默失效；改了载荷永远找不到 ⇒ 假红）⇒ **不许动**；
   - **真 NUL 这条（本条）** ＝ **可改「写法」的夹具** ⇒ ⇒ **值不动、只把写法从真字节换成源码转义** ⇒ ⇒ **该改**。
   ⇒ ⇒ **判据**：「**值**不许改」（题材）vs 「**写法**可以改」（夹具）⇒ 二者别混。
3. 🔴 **真 NUL 的真正代价不是语义，是【git 把文件当二进制】** —— 这是本条**最该记**的一半
   （2026-10-28 由 G4 通报 ⇒ G3 实测确认）：
   - git 对含 NUL 的文件按**二进制**处理 ⇒ ⇒ **三方合并不做文本合并** ⇒ ⇒ `git merge-tree --write-tree` 会产出**「揉出来」的第三版**且**不报冲突** ⇒ ⇒ **「零冲突」推不出「内容完整」**；
   - 实证（G4 的预演合并树）：该文件 blob 三方 ＝ `9a0609fc1394`（9565 B）· G3 HEAD ＝ `8648028bbc47`（10296 B）· **合并树 ＝ `6077498eb6cc`（10103 B）** ⇒ ⇒ **三方都不是合并树那个** ⇒ ⇒ 被「揉」掉的正是 G3 的 PG 钉口那半边（`import …RealContainerHostPort` / `bindPg(FAVORITE_DDL_PARITY)` / `.withPassword("password")`）；
   - ⇒ ⇒ **正确处置 ＝ 把真 NUL 换成源码转义**（让文件变回文本 ⇒ 三方合并才能正确进行）⇒ **不是**「不许修」；
   - ⚠️ **自测判据**：`git diff --numstat <A> <B> -- <文件>` 输出 `-  -` ⇒ ⇒ git 已把它当二进制 ⇒ ⇒ 那个文件在合并里**不可信**。
4. 📌 **行号订正**：上面表里「第 136 / 183 行」**是错的** ⇒ ⇒ 实测（改前 · HEAD `cf55cff`）NUL 在 **`:138` / `:185`**（各 2 个）⇒ ⇒ **行号是快照，不是锚点**（同族：共享层文件被后续提交挪长 ⇒ 引用集体过期）。
5. ✅ **G3 树的实际状态（供下一棒对账）**：我树 `git merge`（真合并，merge commit `a9c3e38`）**没有**揉坏这个文件 ⇒ blob 从 `31a5d5d` 到 HEAD 恒为 `8648028bbc47`（10296 B），且 `import`/`bindPg`/`.withPassword` 三行**全在** ⇒ ⇒ **「揉坏」发生在 `merge-tree`【预演】上，不发生在真合并上**（因为合并时两边该文件无差异 ⇒ 不需三方内容合并 ⇒ 直接取己方）⇒ ⇒ **但这不等于隐患不存在**：一旦对方真的改了这个文件 ⇒ 真合并同样会走二进制路径。
   📌 **订正（2026-10-28 10:3x · G3）—— 上面最后那句归因要收窄**：G4 已推翻自己上一封的「揉坏」归因，**G3 独立核实 G4 是对的** ⇒ 本案例里**合并树缺 PG 那半边的直接原因是【建树时用了旧版 G3】**（实测 `git rev-parse 11f96dd:<该文件>` = `6077498eb6cc` = G3 旧版 ⇒ `11f96dd` 早于 G3 的宿主口钉口提交 `31a5d5d`）⇒ ⇒ **不是被揉坏** ⇒ ⇒ 但**二元机制本身（多方都改同一含 NUL 文件 ⇒ 揉坏）依然成立**（G4 已用原案例证明）⇒ ⇒ **本轮未触发**。
6. ⚠️ **改完之后，`git diff --numstat` 仍会显示 `-  -` —— 这是【易误读点】，别据此以为没修好**：
   - git 的 `buffer_is_binary()` 是**逐 blob** 判定的 ⇒ ⇒ **diff 的任一侧**是二进制 ⇒ ⇒ **整个 diff 按二进制处理** ⇒ ⇒ 「**跨越这一次修改**」的那次 diff（**旧 blob 仍含 NUL** ⇒ 二进制）仍输出 `-  -` ＋「Binary files differ」；
   - ⇒ ⇒ **正确的自测方式 = 拿【两个都是新 blob】的版本比**（2026-10-28 实测）：
     ```bash
     F=service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteDdlParityTest.java
     git cat-file blob $(git rev-parse HEAD:$F) > /tmp/newblob.java
     python3 -c "b=open('/tmp/newblob.java','rb').read(); open('/tmp/tmpblob.java','wb').write(b.replace(b'FAVORITE_DDL_PARITY',b'FAVORITE_DDL_PARITY2',1))"
     TB=$(git hash-object -w /tmp/tmpblob.java)
     git diff --numstat $(git rev-parse HEAD:$F) $TB   # ⇒ 期望输出「1	1」（文本）
     ```
     实测：**`1	1`** ✅（对照：旧 blob vs 新 blob ⇒ `-	-`）⇒ ⇒ **新 blob 已是文本** ⇒ ⇒ **从下一次改动起**，该文件的三方合并才能正确进行 ⇒ ⇒ **隐患已消除**；
   - ⇒ ⇒ 另一条更快的旁证：`file <blob>` ⇒ 旧 blob = `data` · 新 blob = `Unicode text, UTF-8 text` ✅。
7. ⚠️ **一条必须诚实标注的边界：「本轮没出事」≠「机制已被证明」** ——
   - 本轮合并（`ee8dc41`，并入 G4 尖端 `e22d2bd`）实测 **base ＝ `9a0609fc1394` ＝ theirs** ⇒ ⇒ **只有我一方改** ⇒ ⇒ git **直接取 ours**（合并后 blob ＝ `7d22bbfd2cf4` = 我那版 ✅）⇒ ⇒ **本次【未真正触发三方内容合并】**；
   - ⇒ ⇒ **「去 NUL 后三方内容合并能正确进行」这条断言【本轮未被实测证明】** ⇒ ⇒ 仍属 **L1 机制推断**（依据 = git 对二进制不做文本合并）⇒ ⇒ **不得据此宣布该机制已验证**；
   - ⇒ ⇒ **决定性实验（尚未做）**：构造 **base / ours / theirs 三方各异**的合并，对比「改前（含 NUL）」与「改后（文本）」⇒ ⇒ 那才是证明。
   - ⇒ ⇒ **同族教训**：**「没触发」与「不可能触发」是两件事** ⇒ ⇒ 报告时必须分开写（AGENTS.md §0：「已改」≠「已验证」）。

### 🔴 订正（第二轮 · 2026-10-28 10:4x · G3）—— 决定性实验已做：「不报冲突」与「揉出第三版」两处措辞【被实测推翻】

⚠️ **本节是订正，上面第 3 / 5 / 7 条的机制措辞一律保留不改写**（按「改旧判断 ⇒ 保留原值 + 加订正注记」纪律）⇒ ⇒ **第 7 条那条「诚实边界」已被本次实验解除**。

#### 实验构造（全部只碰 git 对象层 + 一个临时 worktree ⇒ 不改工作区、不跑 maven）

- 被测文件：`service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteDdlParityTest.java`
- **base ＝ `31a5d5d`**（含 **4 个真 NUL** 的生产态）· **ours ＝ 去 NUL 文本版**（blob `7d22bbfd2cf4`）· **theirs ＝ 在 base 上开头追加一行** ⇒ ⇒ **两侧改动不重叠 ⇒ 按文本本该合并成功**
- 工具：`git merge-tree --write-tree`（预演）＋ 临时 worktree 里的**真** `git merge` · git 版本 **2.50.1 (Apple Git-155)** · 脚本 `/tmp/g3_exp_merge_nul.py`（**仓外** ⇒ 见下方「复现」小节：**命令内嵌** ⇒ 不依赖该路径仍可复算）

#### 五组对照（实测 · 2026-10-28 10:4x）

| 组 | base | ours | theirs | 退出码 | git 说了什么 | 结果 blob | 判读 |
|---|---|---|---|---|---|---|---|
| **A** | 含 NUL | 去 NUL 文本 | 含 NUL ＋ 追加行 | **1** | `warning: Cannot merge binary files` ＋ `CONFLICT (content)` | **== ours** | ❌ **theirs 的改动丢失** |
| **B** | 全文本 | 文本 ＋ 末尾行 | 文本 ＋ 开头行 | **0** | **无 warning · 无 CONFLICT** | ≠ ours ≠ theirs | ✅ **两处改动都在**（文本合并的**正常形态**） |
| **D** | **含 NUL** | **去 NUL 文本** | **去 NUL 文本 ＋ 追加行** | **1** | 同 A | **== ours** | ❌ **theirs 的改动丢失** |
| **E** | 全文本 | 文本 ＋ 追加行 | **含 NUL** ＋ 追加行 | **1** | 同 A | == ours | ❌ theirs 的改动丢失 |
| **C** | 真 `git merge`（HEAD 侧含 NUL） | — | — | **1** | `CONFLICT (content)` ＋ `Automatic merge failed; fix conflicts and then commit the result.` | — | index 留 **stage 1/2/3** ⇒ `git commit` **被拒**（`You have unmerged paths`） |

#### 结论（5 条 · 逐条订正上面的旧措辞）

1. 🔴 **不是「静默揉坏」—— git 【报】冲突** ⇒ A/D/E/C 四组一致：`warning: Cannot merge binary files` ＋ `CONFLICT (content): Merge conflict in <file>` ＋ **退出码 1** ⇒ ⇒ **G4「不报冲突」的措辞不成立** ⇒ ⇒ **「零冲突推不出内容完整」这句在本案例上【不适用】**（本案例从未出现零冲突）⇒ ⇒ **但它作为一般性断言仍成立**（B 组证明退出码 0 时结果 ≠ ours ≠ theirs 是正常形态 ⇒ 「≠」不是损坏判据）。
2. 🔴 **预演树里该文件取【ours】，不是「揉出来的第三版」** ⇒ ⇒ 上面第 3 条那个判据（「三方都不是合并树那个 ⇒ 被揉坏」）**不成立** ⇒ ⇒ ⚠️ **我上一轮把它读成「第三版」是自己的判据 bug**：`blob_of()` 对 40 位十六进制短路 ⇒ 把 **commit OID 当成了 blob**（⇒ 读到的是树对象 ⇒ 打印出「行数 1」的荒谬读数）⇒ ⇒ **B 组同时反证**：「结果 ≠ ours 且 ≠ theirs」在文本合并下是**正常形态**（两处改动都在）⇒ ⇒ **这条不能当损坏判据**。
3. 🔴 **真正的危害形态 ＝ 「报冲突但【不产生冲突标记】」** ⇒ 二进制文件 git 无法生成 `<<<<<<<` marker ⇒ ⇒ 工作区文件**保留 HEAD 侧内容**（实测：真 NUL 4 个 ＋ 含 theirs 的改动 ＋ **不含** ours 的改动）⇒ ⇒ **看起来「正常」** ⇒ ⇒ 人若**直接 `git add` ＋ commit** ⇒ **另一侧的改动被丢掉** ⇒ ⇒ 这才是真危害（不是「git 静默提交坏内容」）。
4. 🔴 **最危险的一条（D 组）** ⇒ **只要 merge-base 那侧还含真 NUL，即使 ours / theirs 两侧都已经去 NUL，这次合并仍然走二进制路径** ⇒ ⇒ **「我这边修了 NUL」不足以保证合并安全** ⇒ ⇒ 必须等 **该 NUL 修复本身进入共同祖先**之后再合并 ⇒ ⇒ **合并顺序 / 窗口要按这个排**。
5. ✅ **触发条件 ＝ 三方中任一侧含 NUL 即退化** ⇒ 实测到 **base 侧**（A/D 组）与 **theirs 侧**（E 组）单独含 NUL 均退化；**ours 侧单独含 NUL 未单独构造**（git 的 binary 判定是逐 blob 的 ⇒ 机理同源）。
6. ✅ **「去 NUL 后三方合并能正确进行」这条断言【已被实测证明】** ⇒ B 组：三方全文本 ⇒ **退出码 0 · 两处改动都在** ⇒ ⇒ **第 7 条那条「本轮未触发 ⇒ 不得据此宣布已验证」的边界，本轮解除**。

#### 由此得出的判读纪律（3 条）

- 报「合并损坏」时**必须同时报 git 的退出码与 warning 原文**（本案例 ＝ 退出码 1 ＋ `Cannot merge binary files`）⇒ ⇒ 否则会被读成「静默损坏」⇒ ⇒ **而那会引出错误的修法**。
- **别信「工作区文件里没有冲突标记」** ⇒ ⇒ 二进制冲突**本来就没有** marker ⇒ ⇒ 必须 `git show :1:<file>` / `:2:` / `:3:` **三方逐字节比**。
- 判「合并结果对不对」**看改动的标记还在不在** ⇒ ⇒ **不看 blob 是否等于 ours / theirs**。

#### 复现（命令内嵌 ⇒ 不依赖任何仓外脚本）

```bash
# 变量（都在本仓对象层 ⇒ 不改工作区、不占 maven 窗口）
F=service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteDdlParityTest.java
BASE=31a5d5d                       # 含 4 个真 NUL 的生产态
OURS=7d22bbfd2cf4d0ed41a0efe7ea5317d8897377f4   # 去 NUL 后的文本版
I1=/tmp/g3_repro_i1 ; I2=/tmp/g3_repro_i2       # 两个独立 index（免 rm）

# ① 造 theirs（base 内容 + 末尾追加一行 ⇒ 与 ours 的改动不重叠）
BASEBLOB=$(git rev-parse $BASE:$F)              # ⚠️ BASE 是 commit ⇒ 必须先解析成 blob
THEIRS=$(git cat-file blob $BASEBLOB | { cat; echo "// theirs-only line (experiment)"; } | git hash-object -w --stdin)

# ② 造两个「提交」（都挂在 BASE 下 ⇒ BASE 就是 merge-base）
GIT_INDEX_FILE=$I1 git read-tree $BASE^{tree}
GIT_INDEX_FILE=$I1 git update-index --add --cacheinfo 100644,$OURS,$F
OURS_COMMIT=$(git commit-tree $(GIT_INDEX_FILE=$I1 git write-tree) -p $BASE -m exp-ours)
GIT_INDEX_FILE=$I2 git read-tree $BASE^{tree}
GIT_INDEX_FILE=$I2 git update-index --add --cacheinfo 100644,$THEIRS,$F
THEIRS_COMMIT=$(git commit-tree $(GIT_INDEX_FILE=$I2 git write-tree) -p $BASE -m exp-theirs)

# ③ A 组：merge-tree 预演（base 含 NUL）⇒ 期望：退出码 1 + Cannot merge binary files + CONFLICT (content)
git merge-tree --write-tree $OURS_COMMIT $THEIRS_COMMIT; echo "EXIT=$?"
# ⇒ 结果 blob 取 ours ⇒ theirs 的追加行不在结果里（用 git rev-parse <结果树>:$F 比）
# ⚠️ 冲突时 merge-tree 把【冲突树 + stage 1/2/3 + warning + CONFLICT】整段写在 stdout
#    ⇒ 别在「非 0 就抛异常」的 helper 里丢 stdout（那会把证据本身丢掉）

# ④ C 组：真 `git merge`（临时 worktree）⇒ 期望：退出码 1 + Automatic merge failed + index 留 stage 1/2/3
git worktree add --detach /tmp/g3-exp-merge-wt $THEIRS_COMMIT
git -C /tmp/g3-exp-merge-wt merge --no-edit $OURS_COMMIT; echo "EXIT=$?"
git -C /tmp/g3-exp-merge-wt ls-files -u $F          # ⇒ 期望 3 行（stage 1/2/3）
git -C /tmp/g3-exp-merge-wt commit --no-edit --dry-run   # ⇒ 期望被拒：You have unmerged paths
git worktree remove --force /tmp/g3-exp-merge-wt
```

⇒ ⇒ **本节命令已于 2026-10-28 10:4x 在本树实测跑通**（A 组：退出码 1 ＋ warning ＋ CONFLICT ＋ 结果 blob ＝ ours；C 组：退出码 1 ＋ `Automatic merge failed` ＋ `ls-files -u` 三行 stage ＋ `commit --dry-run` 被拒「You have unmerged paths」＋ 工作区文件仍含 4 个真 NUL 且**无冲突标记**）。
⇒ ⇒ **判读要点**：**第 ③ 步的 stdout 必须整段留档**（`Cannot merge binary files` 与 `CONFLICT (content)` 都只出现在 stdout）⇒ ⇒ 只看退出码会把它读成「普通冲突」。

### 适用范围

**只**适用于「源码里以**真实控制字符**当分隔符的测试夹具」这一形态 ⇒ ⇒ **不适用于**真正的编码损坏（那种会解码失败或出现 `U+FFFD`）。
