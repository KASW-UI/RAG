<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-rpc`

> **核对时间：2026-10-25**
> ⚠️ **本档目前没有「已签字」的条目。**
> 下面记录的是 **2026-10-23 首轮台账登记的两条「已实现但未签字」项**，
> **本轮（2026-10-25）未重新验证其代码** ⇒ 只能作为**签字候选**登记，**不能当作已批准的偏离**。

---

## 候选 1 · 🟡 **待签字 · 本轮未复核** · `ObserveRPC` 的 `result` 标签

| | 内容 |
|---|---|
| **来源** | [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §5 的 **U5**（2026-10-23 登记）。 |
| **Go 行为（待复核）** | Go 侧 **21 处**错误返回因**未赋值命名返回值 `err`**，`ObserveRPC` 把它记成 **`success`**。 |
| **RAG 行为（待复核）** | Java 侧**按真实结果计数**（成功 `success`、异常 `fail`）。 |
| **差异性质** | **指标语义**（可观测数据），非请求/响应行为。 |
| **⚠️ 本轮未复核的理由** | 本轮工作范围是 `favorite-common` / `favorite-api` / `favorite-proto`；`favorite-rpc` 的 §0–§8 结论**未重新验证** ⇒ 上述「Go 21 处 / RAG 真实计数」目前是**转引 2026-10-23 台账的记载**，不是本轮的独立观测。 |
| **签字前必须补** | ① 重跑一遍 Go 侧 `ObserveRPC` 调用点的**穷举统计**（21 处要能逐条列出）；② RAG 侧对应计数的实现位置与判定分支；③ 明确「指标口径变化是否影响告警/看板」。 |
| **状态** | 🟡 **待签字 · 证据待补** ⇒ **不要**在补齐证据前把本条当作「已批准的偏离」引用。 |

---

## 候选 2 · 🟡 **待签字 · 本轮未复核** · `favorite_request_seconds_counter` 的指标名

| | 内容 |
|---|---|
| **来源** | [`../../DRIFT/favorite/rpc.md`](../../DRIFT/favorite/rpc.md) §5 的 **U6**（2026-10-23 登记）。 |
| **Go 行为（待复核）** | Go `client_golang` 的 Counter 名**不加后缀** ⇒ `favorite_request_seconds_counter`。 |
| **RAG 行为（待复核）** | Micrometer 的 Counter **会追加 `_total`** ⇒ 落到 `favorite_request_seconds_counter_total`。 |
| **差异性质** | **指标名**（可观测数据），非业务行为。 |
| **⚠️ 本轮未复核的理由** | 同候选 1。 |
| **签字前必须补** | ① 确认 Micrometer 的实际输出名（含 Prometheus 渲染后的名字）；② 确认是否已有看板/告警依赖旧名（若依赖，改名会造成观测断档）；③ 与候选 1 一并拍板（两条是同一批「指标口径」问题）。 |
| **状态** | 🟡 **待签字 · 证据待补** |

---

## 本档其它空位

- `api.md` ⇒ 401 响应 body 形态（DEV-FAV-API-001），见 [`api.md`](./api.md)。
- `common.md` ⇒ OBS 两条（DEV-FAV-COMMON-001 / -002），见 [`common.md`](./common.md)。
