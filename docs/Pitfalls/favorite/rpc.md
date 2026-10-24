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

---

## PIT-FAV-RPC-006 · 🕳️ **静态调用点数不等 ≠ 行为差 —— 判据是「同一次失败，两侧各记几个」**

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

## PIT-FAV-RPC-007 · 🕳️ **登记 ≠ 签字；签字前必须复核「两侧确实不同」这个前提**

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

## PIT-FAV-RPC-008 · 🕳️ **wire 形状漂移：状态码与业务码都测不出来 ⇒ 必须逐字段断言响应体**

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
