# `hot-api` 有意偏离台账

> **范围**：`service/hot/hot-api`（Go `service/hot/api/`）。
> **核对日期**：2026-10-24（第四轮日志面 / `CodeError` 对齐轮；SD-HOT-API-001 为第三轮签字）。
> **差异本体**（未签字的缺陷）在 [`../../DRIFT/hot/api.md`](../../DRIFT/hot/api.md)。
> 本文件只登记**明知与 Go 不同、经签字批准**的偏离。
>
> ⚠️ **读法**：对拍看到这里登记的条目变红，**先确认是不是这一条**，不要当新回归去「修」。

| ID | 一句话 | 方向 | 状态 |
|---|---|---|---|
| **SD-HOT-API-001** | `?page=1&page[]=2`（同名 + `[]` 后缀混用）取**确定值**，而 Go 在两个值之间**随机** | RAG 更确定 | ✅ 已签字（2026-10-23） |
| **SD-HOT-API-002** | 客户端**断连**的取消信号不再向 100 次补全传播（超时语义已保住） | RAG 少一个取消源 | ✅ 已留痕（2026-10-23，第二轮） |
| **SD-HOT-API-003** | Go panic 时指标被误记为 `success`；RAG 按客户端实际看到的结果记 `fail` | RAG 更准 | ✅ 已留痕（2026-10-23，第二轮；该路径无代码可达） |
| **SD-HOT-API-004** | 5 个 `hot_api_*` 指标**真的被暴露**（Go 注册了但无端点暴露） | RAG 单向增强 | ✅ 已留痕（2026-10-23，第二轮） |
| **SD-HOT-API-005** | 下游 gRPC 失败时，日志 `error_reason` 用 **grpc-java** 的形态（`"UNAVAILABLE: …"`）而非 grpc-go 的（`"rpc error: code = Unavailable desc = …"`） | 库层形态差异 | 🆕 **待签字**（2026-10-24 第四轮提出，建议批准） |

---

## SD-HOT-API-001 · 重复键混用时取确定值（Go 侧随机）

- **Component**：`GET /hot/v1/articles` 的 query 解析
- **Go 出处**：
  - `rest/httpx/util.go:59-61` — 名字带 `[]` 后缀的**剥掉后缀**再写进 `params[name]`；
    ⇒ `?page=1&page[]=2` 时，**两个 key 写的是同一个 `params["page"]`**，后者**覆盖**前者；
  - `rest/httpx/util.go:36` — `for name, values := range r.Form` ⇒ **map 遍历序随机**
    ⇒ 谁覆盖谁是**随机的**。
- **实测（go-zero v1.10.2 活体探针，同一 URL 连跑 8 次）**：`page=2` 6 次、`page=1` 2 次
  ⇒ **Go 自己的行为是非确定性的**。
- **RAG 形态**：`GoFormValues.firstFormValue` 先查**精确名** `page`，查不到再查 `page[]`
  ⇒ 固定返回 `page=1`。
- **签字理由**：RAG 取的值**落在 Go 的行为包络内**（Go 的两个可能取值之一），
  而**复刻 Go 的随机性**没有任何价值、且会让对拍无法进行。
- **⚠️ 边界**：本条**只**覆盖「同名 + `[]` 后缀**混用**」这一种输入。
  纯重复（`?page=5&page=3`）与纯数组记法（`?page[]=5&page[]=9`）两侧都是**取首值**，**完全一致**。
- **留痕位置**：`GoFormValues.firstFormValue` 的类注释 + `docs/DRIFT/hot/api.md` DRIFT-HOTAPI-011。

---

## SD-HOT-API-005 · 下游 gRPC 失败的日志串用 grpc-java 形态（待签字）

- **Component**：hot-api 业务错误日志的 `error_reason` 字段，在**下游 gRPC 调用失败**时的形态
- **Go 出处**：
  - `service/hot/api/internal/logic/hot/getHotArticlesLogic.go:67` ——
    `logger.LogBusinessErr(l.ctx, hotcommon.ErrorServerCommon, rpcErr)`，
    记的是**下游 gRPC error 本身**（不是 `*CodeError`）；
  - `service/common/logger/logger.go:118-123` —— `errorReason = err.Error()`；
  - ⇒ `error_reason` = **grpc-go** 的 `(*status.Error).Error()` 形态。
- **RAG 形态**：记的是 grpc-java 的 `StatusRuntimeException`
  ⇒ `error_reason` = `err.getMessage()`（`service/common/logger/Logger.java:241-245`）
  = **grpc-java** 的形态。
- **两侧实测**（Go 探针复用真源 `go.mod`/`go.sum` 离线构建；grpc-java 直跑）：

  | | `error_reason` 实际值 | gRPC code 名 |
  |---|---|---|
  | Go（grpc-go） | `"rpc error: code = Unavailable desc = boom"` | `Unavailable`（CamelCase） |
  | RAG（grpc-java） | `"UNAVAILABLE: boom"` | `UNAVAILABLE`（全大写） |

- **签字理由（建议）**：
  1. 形态由**两个 gRPC 库各自决定**，不是 hot 域的代码 ⇒ 复刻要在 hot-api **手拼**前缀并维护
     **code 名大小写映射表** ⇒ **在错误的层**（hot 域凭什么知道 gRPC 错误串的格式？）；
  2. 与 `SD-HOT-RPC-002`（Redis 重试不复刻）**同一个判断**：把库行为搬进业务层，比差异本身更坏；
  3. **可观察影响极小**：`error_reason` 是**日志自由文本**，码值在两侧都另有结构化 `error_code` 字段承载
     （Go `logger.go:152` / RAG `Logger.java:277`）⇒ 语义信息不丢。
- **⚠️ 与 `DRIFT-HOTAPI-016` 的分界**：`016` 是**hot 域自己写的**格式（`errmsg.go:15-17`）⇒ 该修、已修；
  本条是**库写的**格式 ⇒ 不该由 hot 域修。
- **留痕位置**：`docs/DRIFT/hot/api.md` 的 DRIFT-HOTAPI-017 + `HotArticleQueryService.fatal` 的 javadoc。
