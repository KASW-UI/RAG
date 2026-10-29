<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-api`

> ✅ **2026-10-25 用户已签字**：**2 条**——
> · **DEV-FAV-API-001**（401 响应的 body 形态）
> · **DEV-FAV-API-002**（500 兜底的 body 形态）
>
> 「**保留现状，登记为签字偏离**」⇒ 审查时**不必重复质疑**，但**也不得「顺手修回 Go 行为」**。
>
> ⚠️ **DEV-FAV-API-002 签字附带的前置动作**：
> 客户端若在 500 时**靠 body 区分成因** ⇒ RAG 侧多给了信息 ⇒ 客户端行为**分叉**（但这是 RAG 侧更宽，不是更窄）；
> 「5xx ⇒ 不解析 body」的客户端**两侧都安全** ⇒ 无需改动。
> ⚠️ **DEV-FAV-API-001 的反面理由仍需拍板后与其它域收敛**：`follow` 域选择了**逐条对齐 Go** ⇒ 本仓两种做法都存在。
>
> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/api`）
> 🆕 **核对时间：2026-10-29**（台账对齐 phase · W-E lane 逐条重核）—— 结论见下表。
> ⚠️ **本文件是「明知与 Go 不同」的登记册** —— 每条都是**决策**，不是缺陷。
> ⇒ **不要**把这里的差异当新回归去「顺手修回 Go 行为」（AGENTS.md §0 的共性提醒）。

## 🆕 2026-10-29 逐条重核结论（台账对齐 phase · W-E lane）

> **判定口径**：`docs/Intentional_Deviations/_INDEX.md:34` ——「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
> **方法**：**回 Go 真源 + 回 RAG 源码两侧核**（真源 = `Sea-RideTheWind_golang`，冻结只读；go-zero 取 `v1.10.2`）。
> **撞号检查**：`DEV-FAV-API-001` / `DEV-FAV-API-002` **全域唯一**
> （`grep -rhoE "DEV-FAV-[A-Z]+-[0-9]+" docs/Intentional_Deviations/` ⇒ 本域 11 个 `DEV-FAV-*` 编号**各对应一条**偏离；
> `api.md` / `common.md` / `proto.md` 之间的相互引用都指向**同一条**）⇒ **无撞号** ⇒ **不需要** `SD-<编号>@<域>` 别名。

| ID | 结论 | 证据（核对日期 2026-10-29） |
|---|---|---|
| `DEV-FAV-API-001` | ✅ **仍成立** | Go 侧**验签失败**仍写**空 body**：`/Users/pr/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2/rest/handler/authhandler.go:106-122` 的 `unauthorized(...)` 只 `writer.WriteHeader(http.StatusUnauthorized)`，**一个字节都不写**；**黑名单命中**仍是**两字段** JSON（`service/favorite/api/internal/middleware/checkblacklist_middleware.go:39-45`）⇒ RAG 侧**仍是三字段** envelope：`service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalExceptionHandler.java:110-117`（`handleAuthentication` ⇒ 401 + `new Response(e.getCode(), e.getMessage(), null)`）⇒ 差异未变（状态码 401 与业务码 5004 两侧仍一致）<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：✅ **仍成立**（未改判）。两侧依据当场复核过：Go `rest/handler/authhandler.go:121`（`writer.WriteHeader(http.StatusUnauthorized)`，其后**无**任何 `Write`）+ `service/favorite/api/internal/middleware/checkblacklist_middleware.go:41-44`（手写 **两字段** JSON，**无** `data`）· RAG `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalExceptionHandler.java:115-117`（`setStatus(401)` + **三字段** `new Response(e.getCode(), e.getMessage(), null)`）· 核对日期 2026-10-30。 |
| `DEV-FAV-API-002` | ✅ **仍成立** | Go 侧**默认中间件**仍写**空 body**：go-zero v1.10.2 `rest/handler/recoverhandler.go:12-21` 的 `RecoverHandler` 只 `w.WriteHeader(http.StatusInternalServerError)`（**不** `http.Error`）⇒ RAG 侧仍是**500 + JSON envelope**：`service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalExceptionHandler.java:253-261`（`handleAny` ⇒ 500 + `new Response(5001, "系统内部错误", null)`）⇒ 差异未变<br>⚠️ **2026-10-30 真实性复核（favorite+hot lane）**：✅ **仍成立**（未改判）。两侧依据当场复核过：Go `rest/handler/recoverhandler.go:17`（`w.WriteHeader(http.StatusInternalServerError)`，**无** body 写入）· RAG `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalExceptionHandler.java:259-260`（`ResponseEntity.status(500)` + **JSON envelope**）· 核对日期 2026-10-30。 |

> ⚠️ **计数影响**：本域 **2 条全部仍成立** ⇒ **主线更新 `docs/Intentional_Deviations/_INDEX.md` 时本域无需减项**（本 lane 不写共享索引）。
> ⚠️ **一条仍然悬着的对比项（本轮不升级为拍板）**：`DEV-FAV-API-001` 的「反面理由」写「`follow` 域选择了**逐条对齐 Go** ⇒ 本仓两种做法都存在」
> ⇒ **本轮未复核 `follow` 域的现状**（**不在本 lane 的写权范围**）⇒ ⇒ **该条「两域做法不一致」的现状描述按原句保留**，本轮**不**据此改写任何一侧。

---

## DEV-FAV-API-001 · ✅ **已签字** · 401 响应的 body 形态 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| | 内容 |
|---|---|
| **Go 行为** | ① **验签失败**（`rest/handler/authhandler.go:106-122` 的 `unauthorized()`）⇒ `writer.WriteHeader(401)`，**body 为空**（一个字节都不写）。<br>② **黑名单命中**（`api/internal/middleware/checkblacklist_middleware.go:39-45`）⇒ 401 + 手写 `fmt.Sprintf` 的 **两字段** JSON：`{"code":5004,"msg":"未登录或登录已失效"}`（**没有 `data` 字段**）。 |
| **RAG 行为** | 两种情形**统一**成三字段 envelope：`{"code":5004,"msg":"未登录或登录已失效","data":null}`（`FavoriteJwtAuthInterceptor#unauthorized` → `GlobalExceptionHandler#handleAuthentication:102-110`）。<br>🔴 **2026-10-29 真实性复核 —— 上面这个行号指向的是 javadoc，不是方法体（原文保留，不改写）**：实测 `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalExceptionHandler.java` ⇒ `:102` 在**类 javadoc**里、`:110` 是 `@ExceptionHandler(AuthenticationException.class)` 注解 ⇒ **方法体在 `:111-118`**（`public ResponseEntity<Response> handleAuthentication(...)` ⇒ `:115` `response.setStatus(401)` ⇒ `:116-117` `ResponseEntity.status(UNAUTHORIZED).body(new Response(e.getCode(), e.getMessage(), null))` ⇒ **三字段**）。⇒ ⇒ **语义断言正确**（仍是三字段 envelope、仍是 401）⇒ **签字仍成立**；**指针应读作 `:110-118`**（本档上方「2026-10-29 逐条重核」表里写的 `:110-117` 是准确的 ⇒ **两处口径不一致的地方以那张表为准**）。**核对日期 2026-10-29**。 |
| **对齐的部分** | ✅ **HTTP 状态码 401 一致**；✅ **body 里的业务码 5004 一致**；✅ **msg 文案一致**。 |
| **差异** | 仅 **body 的字段集合**：Go 空 body / 两字段；RAG 三字段。 |
| **为什么接受** | ① 与 users / admin / comment 域的既有形态统一（全仓 401 一律走 envelope）；② 空 body 对客户端与排障都不友好，而状态码与业务码已完全对齐，不影响客户端的失败分支判定；③ **属表达层**（envelope 的字段组织），不是 L1 语义。 |
| **⚠️ 反面理由（必须一并记录）** | `follow` 域选择了**逐条对齐 Go**（其 `FollowApiApplication` 注释明写「本域的 401 有**两种 body 形状**，不可收敛」⇒ 保留 Go 的两种形状）⇒ **本仓两种做法都存在**，本条**不是唯一解**，需拍板后与其它域收敛口径。 |
| **什么时候会变成真差异** | 若客户端按 `body.data` 是否存在来区分「验签失败」与「黑名单命中」⇒ 两侧都拿不到区分信息（Go 两者的 `data` 都不存在），**行为一致**；<br>若客户端**只**在 body 非空时解析 ⇒ Go 侧验签失败会解析失败、RAG 侧不会 ⇒ 差异显现。 |
| **守门** | `FavoriteApiMvcPipelineTest`（36 例）覆盖 401 的状态码与 body 形状。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ 签字前的历史说明（保留供追溯）：此前 `GlobalExceptionHandler` / `FavoriteJwtAuthInterceptor` 两处注释都称「属 AGENTS.md **§3.4** 已登记的表达层差异」，但 **§3.4 是 proto 复制那一节**（引用错误，已改正）；且本文件此前为 **0 字节** ⇒ 「已登记」是**假声明**。<br>⚠️ **签字时一并明确的现状**：**follow 域选的是「Go 逐字」、本域选的是「三字段 envelope」** ⇒ **两域做法不一致，但两侧的 HTTP 状态码（401）与业务码（5004）完全一致** ⇒ 按现状签字，**不改任何一方**；若将来要统一口径，**需同时动两个域并重新征签**。 |

---

## DEV-FAV-API-002 · ✅ **已签字** · 500 兜底的 body 形态 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| | 内容 |
|---|---|
| **Go 行为** | go-zero v1.10.2 的**默认中间件** `rest/handler/recoverhandler.go:17` 是 **`w.WriteHeader(http.StatusInternalServerError)`** ⇒ **500 + 空 body + 无 Content-Type**。<br>⚠️ 它挂在**默认链**上（`rest/engine.go:158`：`if ng.conf.Middlewares.Recover { chn = chn.Append(handler.RecoverHandler) }`）⇒ **只要 handler panic 就会走到**。 |
| **RAG 行为** | `GlobalExceptionHandler#handleAny` ⇒ **500 + JSON envelope** `{"code":5001,"msg":"系统内部错误","data":null}` + `Content-Type: application/json; charset=utf-8`。 |
| **实测** | `tools/api-envelope-probe/favorite/`（**真 go-zero 库**）：Go = `OBS\|panic_recover_500\|500\|\|`（状态码 500，**Content-Type 空，body 空**）；RAG = `500\|application/json;charset=utf-8\|{"code":5001,…}` ⇒ **两侧确实不同**。 |
| **🔴 本条同时改正了台账的一处错误结论** | `docs/DRIFT/favorite/api.md` 此前写的是「Go 的 `RecoverHandler` 写 **500 + text/plain**（`http.Error`）」—— **那是错的**（真库不写任何 body）⇒ 已改正。<br>⇒ 此前还写着「favorite 的 Go logic **不会 panic** ⇒ 该分支不可达 ⇒ **不构成行为漂移**」——**也不成立**（默认中间件 ⇒ 可达）⇒ 这是一条**真差异**，故登记在此。 |
| **为什么接受** | ① RAG 侧给了**可解析**的 envelope（Go 侧是**空 body** ⇒ 客户端在 500 时拿不到任何信息）；② 与 users / admin / comment 域的既有形态**一致**（全仓 500 一律走 envelope）⇒ 单独让 favorite 域退回空 body 只会造出**域间不一致**；③ 属**表达层**（同一个 500 状态，body 的组织方式），不改变失败分支的判定。 |
| **什么时候会变成真差异** | 若客户端在 500 时**依赖 body 为空**（例如「5xx ⇒ 不解析 body」）⇒ 两侧都安全（Go 空、RAG 有内容但客户端不解析）⇒ 无影响；<br>若客户端**靠 body 区分 500 的成因** ⇒ RAG 侧多给了信息 ⇒ 客户端行为**分叉**（但这是 RAG 侧更宽，不是更窄）。 |
| **守门** | `tools/api-envelope-probe/favorite/` 的 **OBS 段**：断言的是「两侧**仍然不同**」⇒ 哪天 RAG 侧改成与 Go 一致（或反过来），探针 **exit 1** 提示重新评估，而不是让它静默漂移。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **签字附带的前置动作**：客户端若在 500 时**靠 body 区分成因** ⇒ RAG 侧多给了信息 ⇒ 客户端行为**分叉**（RAG 更宽，不是更窄）⇒ 需告知客户端方；「5xx ⇒ 不解析 body」的客户端**两侧都安全** ⇒ 无需改动。<br>⚠️ **本条同时改正过台账的一处错误结论**（Go 的 `RecoverHandler` 不是「500 + text/plain」而是 **500 + 空 body**）⇒ 那处错误已在 `docs/DRIFT/favorite/api.md` §… 改正。 |

---

## 本档其它空位

- `common.md` ⇒ OBS 两条（不可达的实现层差异），见 [`common.md`](./common.md)。
- `rpc.md` ⇒ U5 / U6（2026-10-23 台账登记的「已实现但未签字」项）——
  ✅ **均已落定**：U5 **撤销**（前提被实测证伪）、U6 **已签字**（= DEV-FAV-RPC-001），
  见 [`rpc.md`](./rpc.md) 的撤销记录与条目。
