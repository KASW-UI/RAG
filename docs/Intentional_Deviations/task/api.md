# `task-api` 有意偏离（已签字）

> **本域暂无已登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-2026-10-[0-9]{2}-TASK-[0-9]{2}' docs/Intentional_Deviations/task/api.md` ⇒ **0**；
> 且 `grep -rn 'SD-2026-10-[0-9]{2}-TASK' docs/Intentional_Deviations/task/` ⇒ **全部命中都在 `rpc.md`** ⇒ 本文件无条目）。
>
> **核对依据**：
> - `docs/DRIFT/task/api.md:25-27` 的「已修 / 对拍新发现 / 未修」三行里**没有任何「已签字偏离」条目**
>   （未修项只有 **1 条全域共性** `DRIFT-TASK-API-004`「`Content-Type` 的 charset 与空白格式」⇒ 按 §三·三 **不在本域单独修** ⇒ **它不是签字偏离**）。
> - `docs/DRIFT/task/rpc.md:25` 记的 **2 条**已签字偏离（`SD-2026-10-23-TASK-01` · `SD-2026-10-24-TASK-01`）**都在 `rpc.md`**。
> - `docs/DRIFT/task/proto.md:41` 明写「与 `comment` 域不同，**task 的 `package` 两侧都是 `task`，未归一** ⇒ 不存在 `comment` 那样的签字偏离」⇒ **proto 侧也没有**。
>
> ⚠️ **若将来 task-api 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-TASK-NN`**，
> 并同步更新 [`docs/DRIFT/task/api.md`](../../DRIFT/task/api.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/api.md`](../../DRIFT/task/api.md)

## 阶段归属定级附录（2026-11-01 · G2 lane · 残余差异登记 · **已接受 · 不新签**）

> 本节由 G2 lane（`align/g2-message-like-task`）追加 · **只追加，不改任何既有结论**。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层的**两个扫描器**都不读它（保留区机制）⇒ **零投影影响**。
> ⚠️ **本节不是「已签字偏离（SD 编号）」的记录** ⇒ **不与本文件既有的「正本在 `_cross/signed-deviations.md`」约束冲突**
> （那约束只管 SD 编号的**已签字偏离**）⇒ ⇒ 本节是**「已接受 · 不新签」的残余差异登记**，
> 与共享面先例 `docs/Intentional_Deviations/hot/api.md:230`「残余：那个空格（**已接受，不新签**）」**同款**。

### 残余差异登记：`Content-Type` 分号后的那个空格（**已接受 · 不新签**）

| 项 | 值 |
|---|---|
| 性质 | **框架限制（容器层）** —— 不是缺陷、不是待办 |
| 差异（**字节级仍在**） | Go `text/plain; charset=utf-8`（分号后**有**空格）↔ RAG `text/plain;charset=utf-8`（**无**空格） |
| 原台账编号 | `DRIFT-TASK-API-004`（`docs/DRIFT/task/api.md:510` · **原判 🟡 部分闭合 · 不可修**，保留） |
| 销账动作 | **2026-11-01**（G2 lane）⇒ 该行**现行态 = ⚫ 已签字偏离**（不再作为待修漂移跟踪）⇒ **离开派工面**（`⚫` ∉ `open_states`） |
| 是否需要新签字 | ❌ **不需要** —— 共享面**已有接受面**：`docs/Intentional_Deviations/hot/api.md:230`「残余：那个空格（**已接受，不新签**）」⇒ **沿用既有接受** ⇒ ⇒ **本 lane 不代签**（Jason 决策） |
| 同款先例 | `docs/Intentional_Deviations/like/api.md:23`（同一条差异的域内登记面）· 销账 house form `docs/DRIFT/like/rpc.md:234` |

**Go 出处（`文件:行号`）**：`go-zero@v1.10.2/rest/internal/header/headers.go:17`
（`ContentTypeJson = "application/json; charset=utf-8"` ⇒ 分号后**有**空格）＋ Go 标准库
`net/http/server.go:2351`（`h.Set("Content-Type", "text/plain; charset=utf-8")` ⇒ 错误响应同样带空格）·
Go `go-zero@v1.10.2/rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)`）

**RAG 现状（`文件:行号`）**：`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`
（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"` ⇒ **charset 已在**）＋ `:151`（写入点）·
`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:718`
（`resp.setContentType("text/plain; charset=utf-8")` ⇒ **应用层写的本来就是 Go 的带空格原串**）＋
`:723-727`（同文件注释自记「Tomcat 序列化吃掉空格 ⇒ 归框架限制」）

**语义等价论证**：Tomcat 11 `org.apache.catalina.connector.Response.setContentType(String)` 走
`MEDIA_TYPE_CACHE.parse` ⇒ media type 本体交 `setContentTypeNoCharset`、charset 单独走 `CharsetHolder`
⇒ 重新序列化时**必然无空格**；`setHeader` / `addHeader` 遇到 `"Content-Type"` 都**转手调 `setContentType`**
（`checkSpecialHeader`）⇒ ⇒ **Servlet API 的三条写法（`setContentType` / `setHeader` / `addHeader`）
全部产出同一个被规范化后的值** ⇒ ⇒ **应用层无路可走** ⇒ ⇒ **框架限制 · 不是本域可修的差异**；
且按 **RFC 9110**，参数分隔符两旁的 **OWS 不显著** ⇒ ⇒ 两者**语义相同** ⇒ ⇒ **不是行为漂移**

**已知差异（保留可见 · 不藏）**：线上真发 `text/plain;charset=utf-8` ／ Go 真发 `text/plain; charset=utf-8`
⇒ **wire 字节级不同**（**展示面 · 非功能面**；影响面 = 全部 api 的 `text/plain` 响应）

**证据强度**：**E4**（真容器实测 + 框架源码机制 + `javap` 实测 `tomcat-embed-core` **11.0.24**）
· **核对日期 2026-11-01**

### 附：本域另一处同款措辞的**交叉说明**（**原值一律保留** · 未改写）

⚠️ `docs/DRIFT/task/vs-common.md:1601` 的 `## ⑤ 🟡 `DRIFT-TASK-API-004` 的**复验**（**仍开放 · 容器层不可修 · 不在本域单独修**）`
是 **2026-10-29** 的旧快照措辞（**原值保留 · 本轮未改写**）⇒ ⇒ **现行态以 `docs/DRIFT/task/api.md:510` 为准**
（**⚫ 已签字偏离 · 已接受 · 不新签**）。
⚠️ **本轮刻意不改那一行**：实测该行落在 `docs/DRIFT/task/vs-common.md` 里 **`FB-06` 条目的段落内**
⇒ ⇒ 改它会连带改掉 `FB-06`（当前 **✅ 已一致**）的派生读数 ⇒ ⇒ 属**越界副作用** ⇒ ⇒ **只在此交叉说明**。
· 核对日期 2026-11-01
