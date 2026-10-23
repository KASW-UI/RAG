# `hot-api` 有意偏离台账

> **范围**：`service/hot/hot-api`（Go `service/hot/api/`）。
> **核对日期**：2026-10-23（第三轮修复轮）。
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
