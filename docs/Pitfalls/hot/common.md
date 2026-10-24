# Pitfalls · `service/hot/hot-common`

> 领域：`docs/Pitfalls/hot/common.md`（教训本体）
> 差异本体在 [`docs/DRIFT/hot/common.md`](../../DRIFT/hot/common.md)。
> **归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
> **核对日期**：2026-10-24（第十一轮回填）。

## 本组件**没有**专属踩坑条目

> ⚠️ **本文件原先是 0 字节空壳**。按 `docs/Worktree.md` §二·五 的口径「**空文件即坑位** —— 填了就是新坑位，不填就别建空壳文件」，
> **两种做法都合法**：删掉、或写清「为何空」。本次选后者，理由是：
> **文件不存在 = 别的 agent 可能误读成「这个组件从没审过」** ⇒ 显式留痕更安全。

### 为什么本组件的「坑」都归到了别的档

`hot-common` 是**纯值定义 + 纯函数库**（Go 侧只有 `errmsg.go`，121 行）：
**无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存 / 无 Spring 装配层** ⇒
**框架机制静默失效这一类坑在本组件上结构性地不存在**（没有「配了却没生效」的可配面）。

本组件在审计中**确实踩到过**的两件事，都已落到**别的档**：

1. **「日志面整体不等」**（不是「少段前缀」）—— 发生在 **hot-api** 的日志链路上，
   本组件只是**被调用方**（码表 + `CodeError`）⇒ 已修，登记在
   [`../../DRIFT/hot/api.md`](../../DRIFT/hot/api.md) 的 `DRIFT-HOTAPI-016`，
   踩坑机制（`@ResponseBodyAdvice` 二次包裹、`@ExceptionHandler` 返回 `ResponseEntity` 同样被包裹）
   ⇒ 登记在 [`../api.md`](api.md) 的 **P2**。
2. **「台账没回填」导致的假漂移**（原写「advice 仍是空骨架」，实测早已接线）——
   这是**台账维护**问题，不是 hot-common 的问题 ⇒ 已在
   [`../../DRIFT/hot/common.md`](../../DRIFT/hot/common.md) 的边界 #3 **就地更正**。

⇒ **本档的读法**：这里空着是**结论**，不是**待办**。
⚠️ 若日后 hot-common 接入 DB / MQ / Spring 装配，**本档必须重新启用**。
