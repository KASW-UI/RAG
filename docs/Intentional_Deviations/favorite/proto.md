<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-proto`

> **核对时间：2026-10-25**
> 🆕 **核对时间：2026-10-29**（台账对齐 phase · W-E lane 重核）—— **结论不变**（本域仍无条目）。
> ⚠️ 本文件登记的是「两侧确实不同、且判定为不必修」的差异。
> ⇒ 每条都必须能回答：**「为什么不修回去？」**

---

## 本域暂无条目

> ✅ **本域暂无已登记条目**（核对日期 **2026-10-29** · 已核 `grep -rnE '^## ' docs/Intentional_Deviations/favorite/proto.md` ⇒ **无编号条目**）。
> ⚠️ 本条**不是**「没审过」，而是「审过、结论是空」—— 理由见下（与 2026-10-25 的原结论**一致**，本轮只刷新核对日期，未新增/删除任何条目）。

`favorite-proto` 当前**没有**登记任何有意偏离。

**为什么不是「漏了」**：本组件的职责就是把 Go 的 `proto/favorite.proto` **原样复制**过来
（AGENTS.md §3.4：改动严格限定在 option 块与空白层面）。
⇒ 它**没有独立的表达层**可供选择 ⇒ 一旦两侧出现差异，
**要么是复制失真（属缺陷，去修）**，**要么是消费方式不同（属漂移，去对齐）**
—— 两者都**不是**「明知不同却不修」的偏离。

⇒ 本域的结论（复制逐条一致 + 消费方式逐条清点）见
[`../../DRIFT/favorite/proto.md`](../../DRIFT/favorite/proto.md)。

---

## 本档其它空位

- `api.md` ⇒ 401 响应 body 形态（DEV-FAV-API-001），见 [`api.md`](./api.md)。
- `common.md` ⇒ OBS 两条（DEV-FAV-COMMON-001 / -002），见 [`common.md`](./common.md)。
- `rpc.md` ⇒ DEV-FAV-RPC-001 ~ -004（DEV-FAV-RPC-005 **已撤销**），见 [`rpc.md`](./rpc.md)。
