# `_cross/open-items.md` —— 跨域**开放项**相关的踩坑（跨域档）

> **本文件此前是 0 字节占位**（2026-10-29 台账对齐轮补上）。**不编造坑位**。

## 用途

登记**跨域开放项**（任何域都还没认领的搁置漂移）在推进过程中**踩到的坑** —— 例如：
「开放项没人认领 ⇒ 越拖越久」「开放项的归属判断错了 ⇒ 落到了错的域」。

## 已核范围（核对日期 2026-10-29）

- 跨域开放项本体 = [`../../DRIFT/_cross/open-items.md`](../../DRIFT/_cross/open-items.md)
  ⇒ 其中的 `P2`（`DRIFT-HOTAPI-030`）**已于 2026-10-29 收敛为 ✅ 已落地**（共享层修法 · favorite 零触碰）
  —— ⚠️ **2026-10-29 真实性复核：原写「收敛为 ✅ 已落地」⇒ 现行态 = 🟡 部分闭合**
  （**主轴** 2049~10000 且非空值 ≤2048 **已闭合**；**残留窗口 = 恰好 10001 段** ——
  共享层 `MAX_PARAMETER_COUNT = 10001` **至今未改 10000** ⇒ Go 拒 / RAG 放行；
  依据 `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170`
  ＋ Go SDK `net/url/url.go:1001` `defaultMaxParams = 10000`）
  ⇒ **本文件与 `docs/DRIFT/_cross/open-items.md:15` 的口径必须一致**（两处以前分叉成 ✅ / 🟡）。
  —— 🔴 **2026-10-29 主线收口（逐域两侧复核）⇒ 再订正：残留格不是「恰好 10001 段」，而是「task 域 >10001 段」**
  ⇒ 「恰好 10001 段」已闭合（8+favorite 应用层按 Go 段数判据拒；task 两侧同放行）
  ⇒ 残留 = **task 域 >10001 段**（Go 放行 / RAG 容器 400+HTML ⇒ RAG 更严）⇒ **活体证据缺失 ⇒ 待核实**
  ⇒ 逐格依据见 `docs/DRIFT/_cross/open-items.md:15` 的「主线收口」订正块。
- 全仓 `docs/Pitfalls/**` 中**专属于「开放项管理」**的独立条目 = **0 条** ⇒ 本文件**没有需要另行登记的坑位**

## 内容在哪（指针）

| 内容 | 位置 |
|---|---|
| **跨域开放项本体** | [`../../DRIFT/_cross/open-items.md`](../../DRIFT/_cross/open-items.md) |
| **「跨域登记 ≠ 域内认领」类坑**（域内版） | [`../hot/rpc.md`](../../Pitfalls/hot/rpc.md) 等域台账 |
| **收口域 `global` 的分档协议**（跨微服务 + 共享层影响的搁置漂移全部归它） | [`../../DRIFT/global/_README.md`](../../DRIFT/global/_README.md) |

## 状态

- **状态**：⚪ **无独立登记条目**
- **核对日期**：**2026-10-29**
- **是否影响当前轮次**：**否** —— 开放项的推进纪律由 `docs/DRIFT/global/`（收口域）承担
