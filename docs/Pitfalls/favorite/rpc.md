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

---

## PIT-FAV-RPC-009 · 🕳️ **「DDL 逐列对照」要先确定三件事，否则结论会错**

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
| 1 | **真源 schema 的来源与执行顺序** | AutoMigrate（应用启动）**之后**才是运维手工跑 4 个 `.sql`？还是之前？⇒ 由真源文档（`DELIVORY_ACCEPTANCE.md:7`）判定 |
| 2 | **哪些语句在 no-op 时会丢** | 只有**内联**在 `CREATE TABLE` 里的约束会丢；独立的 `CREATE INDEX` / `ALTER` 不会 |
| 3 | **ORM 自己会改什么** | AutoMigrate 会删 DEFAULT、会重排列序 ⇒ 「DBA 手写的表」经过 ORM 之后**不等于**手写的样子 |

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

## PIT-FAV-RPC-010 · 🕳️ **注释里的 Go 断言会「指错行」—— 而且没有任何测试会红**

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

## PIT-FAV-RPC-011 · 🔴 **改注释会把「台账自己的锚点」挪了**（自指漂移）

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

## PIT-FAV-RPC-012 · 🔴 **GORM 默认把日志写到 stdout** ⇒ 会污染探针的输出文件

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

## PIT-FAV-RPC-013 · 🔴 **`mvn -pl` 不带 `-am` ⇒ 用过期上游产物 ⇒ 假红**

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

## PIT-FAV-RPC-014 · 🔴 **测「两个 gRPC 实现之间的差异」≠ 测「RAG 是否忠实复刻 Go」**

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

## PIT-FAV-RPC-015 · 🔴 **探针的「用例顺序」= 「副作用顺序」⇒ 读同行的用例必须排在写同行的用例之前**

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

## PIT-FAV-RPC-016 · 🔴 **框架层的噪声要单独量化，别混进业务用例**（否则白白丢掉对比能力）

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

## PIT-FAV-RPC-017 · 🔴 **手写 gRPC 方法名时别带前导斜杠**（否则你会测到「未知服务」而不是「未知方法」）

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

## PIT-FAV-RPC-018 · 🔴 **`@SpringBootTest` 会把「模块能不能真起」这件事整个掩盖掉**

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

## PIT-FAV-RPC-019 · 🔴 **「关掉 Nacos discovery」在 rpc 侧与 api 侧是**相反**的两件事**

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

## PIT-FAV-RPC-020 · 🔴 **探针的「环境红」不是「被测代码红」**（等待过短 / 探活探错目标 ⇒ 假红）

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

## PIT-FAV-RPC-021 · 🔴 **跨域清单的每一条都要能指出「它凭什么说缺」**（`pom 里没写` ≠ 缺 ⇒ 要看 `dependency:tree` 的**最终作用域**）

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
| 「**13 个**模块的 jar 起不来」 | **可执行应用模块真缺 = 3 个**（`article-rpc` / `like-rpc` / `task-rpc`） | 🔴 **条数高估 4 倍** |
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

## PIT-FAV-RPC-022 · 🕳️ **台账里最危险的断言是「数字」**（指针有自检兜底，**数字没有**）

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
[`tools/ledger-lint/favorite/check-ledger-vs-reality.py`](../../../../tools/ledger-lint/favorite/check-ledger-vs-reality.py)
—— **从台账正文抽断言**（不把真相写死在工具里）⇒ **独立**从仓库算实际值 ⇒ 逐条比对。

⚠️ **工具自身也要分级**：
- **门禁级** = 实际值**无歧义**（文件数 / 定义数 / pom 扫描 / git diff / 常量）；
- **提示级** = 实际值**有歧义**（用例数：源码 `@Test` 注解 ≠ surefire 报告 ——
  注释里的示例代码、`@Testcontainers` 误匹配都会让两者不等）⇒ **只报差异不判死**。

⚠️ **工具的第一版必须先修再信**：本轮它第一次跑就报了 4 条红 ⇒ 人工逐条核实后
**2 条是台账真错、2 条是工具自己的抽取 bug**（正则匹配到了旧章节 / 两条断言措辞同形）
⇒ **工具与人工双向独立一致**，才算数。

## PIT-FAV-RPC-023 · 🔴 **端口排除列表必须包含「本轮已分配的全部端口」**（撞号 ⇒ 症状伪装成「unknown service」）

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

## PIT-FAV-RPC-024 · 🔴 **种子显式写自增主键 ⇒ 序列不推进 ⇒ 首次 INSERT 撞 PK**（症状伪装成「重名 5203」）

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

## PIT-FAV-RPC-025 · 🐚 **bash 3.2 的 `${arr[@]+"${arr[@]}"}` 多一个 `"` ⇒ 整份脚本 `unexpected EOF`**

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

## PIT-FAV-RPC-026 · 🔴🔴 **「探针里用 CLI 参数绕过」≠「产物里修好了」**（半修 ⇒ 缺陷活到了发布那一刻）

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

## PIT-FAV-RPC-027 · 🔴 **「借来的工具」只能当探针，不能当门禁**（且它的第一版输出要逐条核证）

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
| **真缺陷** | **1** | `core/service/serviceconf.go:83-90` ⇒ 该文件共 89 行，真实范围 83-89 |

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
