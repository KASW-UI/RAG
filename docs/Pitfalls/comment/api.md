<!-- @new 2026-10-24 · comment 域 api 组件踩坑档 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# 踩坑档 · `service/comment/comment-api`

> 归类：**「为什么会踩」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「哪里不一致」，本文记「为什么会踩成这样」。**

---

## PIT-COMMENT-API-01 🔴 · 跨模块的自定义 trailer：**两端都「写对了」，链路却是断的**

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

## PIT-COMMENT-API-02 · 第三方库的**默认值**常常和你的直觉相反

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

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | 本文件各坑对应的**漂移条目** |
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | `comment-rpc` 的台账 |
| [`../../DRIFT/comment/common.md`](../../DRIFT/comment/common.md) | `comment-common` 的台账（业务码通道的**生产端**在这里） |
| [`../_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md) | 全仓级的「绿测试 ≠ 对」—— **共享文件，本工作树不碰** |
