<!-- @new 2026-10-24 · comment 域 api 组件踩坑档 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# 踩坑档 · `service/comment/comment-api`

> 归类：**「为什么会踩」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「哪里不一致」，本文记「为什么会踩成这样」。**

---

## PIT-COMMENT-API-01 🔴 · 跨模块的自定义 trailer：**两端都「写对了」，链路却是断的** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-COMMENT-API-01` ⇒ **新号**：`PIT-COMMENT-01`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「跨模块的自定义错误通道必须两侧同时实现」这一形态今天仍在：comment-rpc 生产端写 trailer（`CommentGrpcError.java:141`）、comment-api 消费端读 trailer（`CommentApiSupport.java:210`）—— **本域这一处已修**，但**新增**同类通道时两侧仍可能只实现一端 ⇒ 本轮若新增跨模块错误码通道，必须同步落一条「起真 server + 真 client」的链路测试。
> **依据**：本域实例**已修**：`service/comment/comment-api/src/main/java/com/rag/comment/comment/api/service/CommentApiSupport.java:210`（`int bizCode = CommentGrpcError.bizCodeFromError(err);`）· 生产端 `service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/CommentGrpcError.java:72,141,214` · 台账 `docs/DRIFT/comment/api.md:44`（MISMATCH-API-01 ✅ 已修）⇒ **证据强度：源码行号（静态）**。

> ✅ **本条已于 2026-10-24 修复并配变异验证**（详见 [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) 的 §0.1 与 §2）。
> 修法**不是发明新方案**，而是**照抄同仓已有正确范式** `like-api/.../LikeApiSupport.java:179-186`
> —— ⚠️ **comment 域本就是从 like 域抄来的，却把 `bizCodeFromError` 这一步漏了**。
> ⇒ **教训升级为：抄同域范式时，要连「那一步为什么存在」一起抄，否则就是抄了个断的链路。**

### 一句话

**一个跨模块的错误码通道，需要**「生产端写」和「消费端读」**两处同时实现；
少任何一端，两侧各自的单测都会绿，而真实链路上业务码就丢了。**

### 具体形态

| 角色 | 实现 | 位置 |
|---|---|---|
| **生产端** | `comment-rpc` 把业务码写进自定义 trailer `rag-comment-biz-code` | `comment-common/.../CommentGrpcError.java:72`（key）、`:130`（写） |
| **读端（存在但没人调）** | `comment-common` 自己提供了 `bizCodeFromError`，**能**从 trailer 取回业务码 | `CommentGrpcError.java:168-200`（尤其 `:186-196`） |
| **真正的消费端** | `comment-api` 的 `rpcError` 只读 `status.getCode().value()` ⇒ **从不读 trailer** | `CommentApiSupport.java:139-145` |

⇒ 链路上**没有一环**在读那个 trailer（`comment-api` 全模块 grep `trailersFromThrowable` / `BIZ_CODE_TRAILER` **零命中**）。

### 为什么会踩

1. **「业务码该放哪」这个问题，两侧模块各自做了决策**：
   - `comment-rpc` 想「把业务码和 gRPC 标准码分开」（避免 `grpc-status` 出现非法数值），于是用 trailer。
   - `comment-api` 想「严格照搬 Go 的 `int(st.Code())` 字面搬运」，于是只读标准码。
2. **两边的论证各自都成立** —— 直到它们**拼在一起**。
3. 🔴 **两侧的 javadoc 还互相矛盾**：
   - `CommentServiceGrpcImpl.java:124`：「comment-api 侧从 trailer 取业务码、从 description 取原文」
   - `CommentApiSupport.java:120-137`：「本方法**刻意不**使用 `bizCodeFromError`」
   ⇒ **两句话互斥**，而两边都以为自己是对的。

### 绿测试为什么没抓到（本仓最经典的一种）

- `comment-common` 的 `CommentGrpcErrorTest.java:112-114` **有**断言：trailer 写进去能被读回来 ⇒ **绿**。
- `comment-api` 的 `CommentApiBehaviorTest#createMapsGrpcCodeVerbatim` 用 **mock** 抛 `Status.NOT_FOUND`
  ⇒ 测的是「**Go 侧 rpc** 的形态」，**测不到 RAG↔RAG 的 trailer 路径** ⇒ **绿**。
- ⇒ **两个绿测试各自守着自己那一半，谁也没守住「拼起来」**。

⇒ 这正是 `docs/Pitfalls/_cross/testing-pitfalls.md` 记的
「**绿测试可能是『错误答案的守卫』，比没有测试更危险**」的又一实例：
**修复时会先撞上这两条红测试**，且测试名会让人误判「修复」才是漂移方向。

### 可复用的判据

> **凡引入一个跨模块的自定义错误通道，必须有一条**跨模块的真链路测试**（起真 server + 真 client），
> 断言「业务码原样到达对端」。只测「写」或只测「读」都不算。**

### 对应本轮的漂移

[`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) 的 **MISMATCH-API-01**（本轮最严重）。

---

## PIT-COMMENT-API-02 · 第三方库的**默认值**常常和你的直觉相反 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-COMMENT-API-02` ⇒ **新号**：`PIT-COMMENT-02`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 的默认值藏在 struct tag 里、第三方库源码在 `~/go/pkg/mod/` 可读 —— 这两个前提都没变 ⇒ 任何新引入的 Go 依赖（zrpc / go-zero rest / golang-jwt）都会再触发一次「默认值与直觉相反」⇒ 本轮仍须把「Go 侧某个框架特性默认开/关」的论断逐条核到消费点。
> **依据**：本域三处断言**已订正**（`docs/DRIFT/comment/api.md:99` MISMATCH-API-02 ✅ 已修；`docs/DRIFT/comment/api.md` §0.1 已修清单）⇒ 但**触发条件未消除**：Go 真源侧 `zrpc` 的 tag 默认值仍在依赖里 ⇒ **证据强度：静态**。

### 一句话

**「熔断默认开 / 关」「超时默认多少」「数字 claim 解成什么类型」——
这些都在第三方库的 struct tag / 构造默认值里，不在业务代码里。**

### 本轮的三处（全部推翻了 RAG 侧注释里的假设）

| 判定点 | 直觉 | 实测真源 |
|---|---|---|
| go-zero 的 **gRPC 客户端熔断** | 「默认关」 | ✅ **默认开** —— `zrpc/internal/config.go:14` `Breaker bool \`json:",default=true"\``；`zrpc/internal/client.go:112-113` 装 `BreakerInterceptor` |
| go-zero 的 **gRPC per-call 超时** | 「用 HTTP 的超时」 | ✅ **独立默认 2000ms** —— `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\``；`zrpc/internal/client.go:115-116` |
| golang-jwt 的**数值 claim 类型** | 「是 `float64`」 | ✅ **是 `json.Number`** —— go-zero `rest/token/tokenparser.go:123` `jwt.NewParser(jwt.WithJSONNumber())` |

⚠️ **第一处的后果**：既有台账（`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45`）记着
「已登记未修：gRPC client 熔断缺失（Go `zrpc` `Breaker` 默认 `true`）」——
**本轮实测 RAG 侧熔断已存在**（`application.yml:88-89` `rag.protection.breaker.enabled: true`
+ `common/protection/GoStyleGrpcClientBreakerInterceptor` 的 `@GrpcGlobalClientInterceptor`）
⇒ **那条既有结论作废**。

### 可复用的动作

```bash
grep -E "zeromicro/go-zero" go.mod                                     # 拿精确版本
D=~/go/pkg/mod/github.com/zeromicro/go-zero@$(grep -E "zeromicro/go-zero" go.mod | head -1 | awk '{print $2}')
grep -rn "json:\",default=" $D/zrpc/ $D/rest/                         # 看 struct tag 里的默认值
grep -rn "WithJSONNumber\|jwt.NewParser" $D/rest/                     # 看解析器装配
```

⇒ 凡是**「Go 侧某个框架特性默认开/关」**的论断，都该用这条命令核一遍，
而不是靠「我记得 go-zero 默认是……」。

---

## PIT-COMMENT-API-03 🔴 · **自己的 javadoc 会成为「证据」** ⇒ 一条被推翻的漂移，靠的是读异常处理类 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-COMMENT-API-03` ⇒ **新号**：`PIT-COMMENT-03`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 触发条件（RAG 源码里带「Go: …」措辞的 javadoc 断言会被人当成既成事实继承）**今天仍在** ⇒ 本轮任何新增/改动 javadoc 的动作都可能再犯 ⇒ 「以 Go 措辞写下的断言 = 待核实项」这条约束仍须落进本轮防错。
> **依据**：本条点名的两处（U5/U6）**已订正**：`service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/CommentGrpcError.java:97`（已写「原措辞『若未来引入熔断器』**已过时**」）· U6 见 `docs/DRIFT/comment/api.md:339` ⇒ ⚠️ **本条正文「⚠️ 同类风险仍在：… ⇒ 待订正」一句已过期**（订正见本条追加段）⇒ **证据强度：源码行号（静态）**。

### 一句话

**D1（非 JSON Content-Type ⇒ Go 200+5001 vs RAG 415）立项时，唯一的依据是
RAG 自己异常处理类里的一句 javadoc；撤销时，活体实测证明那句 javadoc 本身是错的。**

### 形态

`CommentGlobalExceptionHandler.java` 里原本写着：

> 「Go 侧 … 仍会报必填错 ⇒ 200 + 5001。**Java 侧由 Spring 的消息转换器选择直接判 415。**」

⇒ 首轮对照（**纯静态**）据此把 D1 登记成 OBSERVABLE DIFFERENCE，并一路写进台账与 `§0 摘要`。

**2026-10-24 双侧活体对拍（T21）实测**：两侧**都是 200 + `{code:5001}`** ⇒ **D1 撤销**。

### 为什么静态推导会错

「Java 侧 ⇒ 415」这个推断隐含了一个**没被验证的前提**：
「非 JSON Content-Type ⇒ Spring 一定抛 `HttpMediaTypeNotSupportedException`」。
实际发生的是另一条路径：

| 判别实验（同一份 1 字节 body） | Spring 的反应 | 说明 |
|---|---|---|
| `Content-Type: application/json` | **`JSON parse error: Unrecognized token 'x'`** | body **被读了** |
| `Content-Type: text/plain` | **`Required request body is missing`** | body **读不出字节** ⇒ 走的是「body 缺失」分支，**不是** 415 |

⇒ 真实路径是 `HttpMessageNotReadableException` ⇒ `handleUnreadableBody` ⇒ 200 + 5001。

### 可复用的判据

> **判据 1：把「我方代码里的注释」当证据前，先问一句「这句话本身是谁写的、验过吗」。**
> 本仓的 javadoc 里**有已登记的漂移、有已签字的偏离、有过时的论证** ——
> 它们是**结论的载体**，不是**结论的依据**。
>
> **判据 2：断言「某框架会抛某异常」之前，先确认那个异常**真的是这条路径抛的** ——
> Spring 的 `@ExceptionHandler` 分支很多，落到哪个分支是**运行期**决定的。
>
> **判据 3：一条差异从立项到撤销，如果**全程没有活体证据**，那么它的「证据强度」标签就应当一直是「静态」。**

### 连带动作

- ✅ **已订正** `CommentGlobalExceptionHandler.java` 的那段 javadoc（写明实测结论 + 机制未查实）。
- 🟢 **已撤销** `docs/DRIFT/comment/api.md` 的 D1（并同步 §0 计数）。
- ⚠️ **同类风险仍在**：`CommentGrpcError.java:97-103` / `:150-162` 的两段论证也带着「若未来 …」的措辞（见 `DRIFT/comment/api.md` 的 U5 / U6）⇒ **待订正**。
- 📌 **2026-10-29 订正（台账对齐 phase · W-D lane）**：上一行的「**待订正**」**已过期** —— 本条点名的两处**都已订正**：
  `service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/CommentGrpcError.java:97`（已写「原措辞『若未来引入熔断器』**已过时**」）·
  U6 见 `docs/DRIFT/comment/api.md:339`（✅ 已订正 2026-10-24）⇒ ⇒ **原文保留**（不抹除一次真实执行的记录），**订正追加在上**。

### 对应本轮的漂移

[`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) §3.3（D1 撤销）。

---

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | 本文件各坑对应的**漂移条目** |
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | `comment-rpc` 的台账 |
| [`../../DRIFT/comment/common.md`](../../DRIFT/comment/common.md) | `comment-common` 的台账（业务码通道的**生产端**在这里） |
| [`../_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md) | 全仓级的「绿测试 ≠ 对」—— **共享文件，本工作树不碰** |
