<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-api`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/api`）
> ⚠️ **本文件是「明知与 Go 不同」的登记册** —— 每条都是**决策**，不是缺陷。
> ⇒ **不要**把这里的差异当新回归去「顺手修回 Go 行为」（AGENTS.md §0 的共性提醒）。

---

## DEV-FAV-API-001 · 🟡 **待签字** · 401 响应的 body 形态

| | 内容 |
|---|---|
| **Go 行为** | ① **验签失败**（`rest/handler/authhandler.go:106-122` 的 `unauthorized()`）⇒ `writer.WriteHeader(401)`，**body 为空**（一个字节都不写）。<br>② **黑名单命中**（`api/internal/middleware/checkblacklist_middleware.go:39-45`）⇒ 401 + 手写 `fmt.Sprintf` 的 **两字段** JSON：`{"code":5004,"msg":"未登录或登录已失效"}`（**没有 `data` 字段**）。 |
| **RAG 行为** | 两种情形**统一**成三字段 envelope：`{"code":5004,"msg":"未登录或登录已失效","data":null}`（`FavoriteJwtAuthInterceptor#unauthorized` → `GlobalExceptionHandler#handleAuthentication:102-110`）。 |
| **对齐的部分** | ✅ **HTTP 状态码 401 一致**；✅ **body 里的业务码 5004 一致**；✅ **msg 文案一致**。 |
| **差异** | 仅 **body 的字段集合**：Go 空 body / 两字段；RAG 三字段。 |
| **为什么接受** | ① 与 users / admin / comment 域的既有形态统一（全仓 401 一律走 envelope）；② 空 body 对客户端与排障都不友好，而状态码与业务码已完全对齐，不影响客户端的失败分支判定；③ **属表达层**（envelope 的字段组织），不是 L1 语义。 |
| **⚠️ 反面理由（必须一并记录）** | `follow` 域选择了**逐条对齐 Go**（其 `FollowApiApplication` 注释明写「本域的 401 有**两种 body 形状**，不可收敛」⇒ 保留 Go 的两种形状）⇒ **本仓两种做法都存在**，本条**不是唯一解**，需拍板后与其它域收敛口径。 |
| **什么时候会变成真差异** | 若客户端按 `body.data` 是否存在来区分「验签失败」与「黑名单命中」⇒ 两侧都拿不到区分信息（Go 两者的 `data` 都不存在），**行为一致**；<br>若客户端**只**在 body 非空时解析 ⇒ Go 侧验签失败会解析失败、RAG 侧不会 ⇒ 差异显现。 |
| **守门** | `FavoriteApiMvcPipelineTest`（36 例）覆盖 401 的状态码与 body 形状。 |
| **状态** | 🟡 **待签字** —— 此前 `GlobalExceptionHandler` / `FavoriteJwtAuthInterceptor` 两处注释都称「属 AGENTS.md **§3.4** 已登记的表达层差异」，但 **§3.4 是 proto 复制那一节**（引用错误，已改正）；且本文件此前为 **0 字节** ⇒ 「已登记」是**假声明**。 |

---

## 本档其它空位

- `common.md` ⇒ OBS 两条（不可达的实现层差异），见 [`common.md`](./common.md)。
- `rpc.md` ⇒ U5 / U6（2026-10-23 台账登记的「已实现但未签字」项），
  本轮**未重新验证** ⇒ 见 [`rpc.md`](./rpc.md) 的标注。
