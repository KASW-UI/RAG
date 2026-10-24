<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 · 本轮只做「复核 + 定位」，未改动 common 代码 -->

# global · common 尾巴台账（T-1 ~ T-10）

> **归属**：`global` 收口域的「common-tails」议题档（§5.3b）。
> **对象**：**别的域**的漂移，但**修法落在** `service/common` 的共享类里 ⇒ 不是 `common` 域自身的语义漂移
> （边界见 [`_README.md`](../_README.md) §二）。
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
⇒ 已登记为踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md)。

---

## 一、逐条复核结论

### T-1 ✅ `Content-Type` 大小写敏感判定 —— **已修**（残余 ① 见下）

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/rest/httpx/requests.go:153-155`<br>`func withJsonBody(r *http.Request) bool { return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson) }`<br>常量：`rest/internal/header/headers.go:5` ⇒ `ApplicationJson = "application/json"` |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java:115-117`（`read()` 取 Content-Type）<br>判定在 `:98-101`（`goReadsBody`）⇒ `contentType.contains(GO_JSON_CONTENT_TYPE_MARK)`，`GO_JSON_CONTENT_TYPE_MARK = "application/json"`（`:77`） |
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

#### 🔴 T-1 残余 ①（**本轮未修，已登记**）

**只修好了「判定」这一层；解析那一层仍会碰 MediaType 解析。**

| | 事实 |
|---|---|
| **Go** | `strings.Contains(raw, "application/json")` ⇒ **纯字面**，**不做 MIME 解析** ⇒ 畸形头**不会抛**，命中就照常读 body |
| **RAG（修后）** | 判定用原始值 ✔；但命中后 `super.read(...)` 内部仍会调 `getHeaders().getContentType()` 做 MediaType 解析 ⇒ **`application/json, application/xml` 仍抛 `InvalidMediaTypeException`**（实测，`Invalid token character ','`）⇒ 请求 500 |
| **状态** | 🔴 **未修** |

⇒ **建议修法**（下一轮）：命中后把传给 `super.read(...)` 的 `HttpInputMessage` **包一层**，
让 Spring 看到一个**已净化的** `Content-Type`（如 `application/json`）
⇒ 顺带修掉「`charset=gbk` 时 RAG 按 GBK 解码、Go 恒按 UTF-8」的另一条偏差
（Go 的 `encoding/json` 只认 UTF-8）。

⚠️ **本轮刻意没做**：那会**扩大改动面**（要包 `HttpInputMessage`），
且本轮授权范围是「修 T-1 的大小写判定」⇒ 按 §5.3「不擅自扩大 scope」登记待办。
⚠️ **守门测试也刻意不断言该行为** —— 断言它「会抛」等于把一个**已知与 Go 不一致**的行为
**锁成标准**（§3.3.3：绿测试可能是「错误答案的守卫」）。

⚠️ **修法的连带面**：`goReadsBody` 是 **public**（`:94` 的 javadoc 明写：它是「Go 何时读 body」这条规则的**唯一真源**，
且业务模块的测试直接断言它 ⇒ 复制品必然与实现漂移）⇒ 修它必须**同时**修所有调用方的测试期望值。
✅ **实测**：唯一跨域调用方是 `like-api` 的 `LikeApiGoStyleBodyContentTypeTest`（7 条断言，**全为已小写值**）
⇒ **不需改任何别的域的代码**。

🔴 **但发现 3 份「生产复制品」** ⇒ 见 [`cross-domain.md` X-9](cross-domain.md)。

---

### T-2 / T-5 ✅ `MaxConns` 闸门作用域 —— **已签字为有意偏离**（移出本档）

> ⚠️ **本两条已不在 DRIFT 档**（§5.3b 硬边界 2：「不要在 DRIFT 里记『决定不对齐』的偏离」）
> ⇒ 决策记录在 [`docs/Intentional_Deviations/global/common-tails.md` §SD-2026-10-25-01`](../../../Intentional_Deviations/global/common-tails.md)
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
[`docs/Intentional_Deviations/global/common-tails.md`](../../../Intentional_Deviations/global/common-tails.md)。
⇒ **签字决定 = 保留 RAG 的全局闸门，不改成 per-route**。
⇒ 配套落地：`GoStyleMaxConnsFilter` 的 javadoc 已从「有意的表达层收敛」自述
**改成指向该签字档的指针**（并加一句「不要因为 Go 是 per-route 就顺手改回」）。

---

### T-3 ✅ 已修 —— **实测确认，无需 global 再动**

`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:431`
⇒ `resp.setHeader("X-Content-Type-Options", "nosniff")` **在位**（`writeTimeoutResponse` 内）。
⇒ 关闭。（Go 出处：`net/http.Error` 自 Go 1.11 起写该头。）

---

### T-4 🔴 499 / 2s deadline —— **仍开放，本轮未展开**

落点 `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:332`
⇒ 实测该行是 `writeTimeoutResponse(httpResp, HTTP_CLIENT_CLOSED_REQUEST)`，位于 `InterruptedException` 分支
（客户端断连 ⇒ 499）。**本轮未展开逐条对照**，状态维持 🔴 仍开放。

---

### T-6 / T-7 / T-8 🟡 Content-Type / reason phrase / Traceparent —— **仍 OPEN，本轮未展开**

落点 `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83`
⇒ 本轮未展开逐条对照，状态维持 🟡 仍 OPEN（待拍板）。
⚠️ 三条**同落点**，应**一次拍板一次改**，不要分三次动同一文件（并发合并冲突）。

---

### T-9 🔴 熔断计数键缺 target 前缀 —— **确认为真漂移**，且🔴 **发现 §3.2 缺陷**

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18`<br>`breakerName := path.Join(cc.Target(), method)` ⇒ `breaker.DoWithAcceptableCtx(ctx, breakerName, ...)` |
| **Go 语义** | 熔断器名 = **`target` + 方法名** ⇒ **每个下游 target 一份独立计数** |
| **RAG 出处** | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:193`<br>`String fullMethodName = method.getFullMethodName();` ⇒ **无 target 前缀**<br>闸门在 `:161-165` `breakerOf(fullMethodName)`（实例级 `ConcurrentHashMap`） |
| **可观察差异** | 两个**不同下游服务**调用**同名方法** ⇒<br>**Go：两份独立熔断器，互不影响**<br>**RAG：共用一份 ⇒ A 服务的失败把 B 服务的熔断器也拖开** |
| **状态** | 🔴 未修 |

🔴 **附带发现（§3.2 缺陷）**：RAG 该类的 javadoc **原文照抄了 Go 那一行**
`breakerName := path.Join(cc.Target(), method)`，却**只字未提自己没实现 target 前缀**。
⇒ 按 §3.2（「这段比 Go 多做了什么？语义还一样吗？」），此处是一处**无出处的 idiomatization** ——
**注释写着 Go 的语义，代码是另一套语义**，属于 AGENTS.md §3.2 明令「不可 review，不得合入」的那一类。

---

### T-10 ✅ **本轮闭环** ⇒ 详见 [`cycle-deps.md`](cycle-deps.md)

`service/users/user/user-rpc/.../UserDomainService.java:113` 的 javadoc 假断言已订正，
`service/common/.../RpcInterceptor.java` 的 `DeadlineState.markExpired(long)` 已删除，
配真 in-process gRPC 守门测试 + 变异测试。

---

## 二、本轮复核汇总表

| # | 条目 | 复核前 | 复核后 | 本轮动了？ |
|---|---|---|---|---|
| T-1 | Content-Type 大小写 | ⬜ 待复核 | ✅ **已修**（残余 ① 已登记） | ✅ |
| T-2 | MaxConns 作用域 | 🔴 仍未修 | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 | ✅ 指针 |
| T-3 | 503/499 缺 nosniff | ✅ 已修 · 待确认 | ✅ **实测确认已修** | ❌ |
| T-4 | 499 / 2s deadline | 🔴 仍开放 | 🔴 仍开放（落点已定位到 `:332`） | ❌ |
| T-5 | MaxConns 作用域 | 🔴 仍开放 | ✅ **已签字**（同 T-2）⇒ 移出本档 | ✅ 指针 |
| T-6 | 成功/失败 Content-Type | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-7 | 状态行 reason phrase | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-8 | 缺 Traceparent | 🟡 仍 OPEN | 🟡 仍 OPEN | ❌ |
| T-9 | 熔断计数键 | ⬜ 待复核 | 🔴 **确认为真漂移 + 发现 §3.2 缺陷** | ❌ |
| T-10 | UserDomainService 假断言 | ❌ 未销账 | ✅ **已闭环** | ✅ |

⇒ **本轮净效果**：10 条里 **1 条闭环**（T-10）· **3 条从「待复核」升级为「已确认的真漂移」并补齐 Go 出处**
（T-1 / T-2+T-5 / T-9）· **6 条维持原状**（未展开）。

---

## 三、下一轮的排期建议（按 §5.3 的「rpc 必须先于 api」与单写权约束）

| 序 | 条目 | 落点 | 是否需要别的域配合 |
|---|---|---|---|
| ~~1~~ | ~~**T-1**~~ | `service/common/http/GoStyleJsonBodyConverter.java` | ✅ **已完成**（2026-10-25）；残余 ① 待下一轮 |
| **1′** | **T-1 残余 ①** | 同上（包一层 `HttpInputMessage`） | ⚠️ 需先决定是否顺带修 charset 偏差 |
| **2** | **T-9** | `service/common/protection/GoStyleGrpcClientBreakerInterceptor.java` | ⚠️ 需引入「target」概念 ⇒ 取决于 devh starter 能否拿到 channel target ⇒ **先做可行性探针** |
| ~~3~~ | ~~**T-2 / T-5**~~ | —— | ✅ **已签字**（`SD-2026-10-25-01`）⇒ 移出本档 |
| 4 | **T-4 / T-6 / T-7 / T-8** | `service/common/observability/HttpMiddleware.java` + `service/common/http/GoStyleRouterErrorResponses.java` | 🟡 **同落点，一次拍板一次改**（T-6/7/8 同文件） |

⚠️ **排序提醒**：T-1 与 T-9 都改 `service/common` ⇒ **本域独占写点**，别的域此刻在改 common 会撞
（§5.3b 规则 1）⇒ 这几条**必须串行**。

---

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **第二轮（T-1 修复）**。T-1 改为**取原始头值**（`getFirst(CONTENT_TYPE)`）⇒ 大小写敏感判定与 Go 对齐；配真 `HttpHeaders` 守门测试 6 条 + 变异测试（转红）。⚠️ 登记 **T-1 残余 ①**（解析层仍走 MediaType ⇒ 畸形头仍抛，Go 不会）。🔴 新增 **X-9**（3 份生产复制品）⇒ [`cross-domain.md`](cross-domain.md)。T-2 / T-5 **用户签字** ⇒ 转 `SD-2026-10-25-01` 并移出本档 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 T-1~T-10 逐条复核结论；纠正「Go 出处在项目树里」的方法论错误（真源分 L1 项目层 / L2 go-zero 框架层）；确认 T-1 / T-2 / T-5 / T-9 为真漂移；发现 T-2/T-5 档位错置、T-9 的 §3.2 缺陷；T-10 转 [`cycle-deps.md`](cycle-deps.md) |
