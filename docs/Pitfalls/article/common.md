# Pitfalls · `service/article/article-common`

> 领域：`docs/Pitfalls/article/common.md`（教训本体）
> 差异本体在 [`docs/DRIFT/article/common.md`](../../DRIFT/article/common.md)。
> **核对日期**：2026-10-29（本轮逐条回填 · 台账对齐 phase）。

## 本组件**没有**专属踩坑条目

> ⚠️ **本文件原先是 0 字节空壳**。按 `docs/Worktree.md` §二·五 的口径「**空文件即坑位** —— 填了就是新坑位，不填就别建空壳文件」，
> **两种做法都合法**：删掉、或写清「为何空」。本次选后者，理由是：
> **文件不存在 = 别的 agent 可能误读成「这个组件从没审过」** ⇒ 显式留痕更安全。
>
> **本轮（2026-10-29）已核**：
> `find service/article/article-common/src/main -type f` ⇒ **1 个文件 / 256 行**
> （`errmsg/ErrorCode.java`）；
> Go 侧对应件 = `service/article/common/errmsg/errmsg.go`（**1 个文件 / 45 行**）。

### 为什么本组件的「坑」都归到了别的档

`article-common` 是**纯错误码表**（Go 侧同样是 1 个 `errmsg.go`）：
**无 DB / 无 RPC 调用 / 无 MQ / 无鉴权 / 无事务 / 无缓存 / 无 Spring 装配层** ⇒
**框架机制静默失效这一类坑在本组件上结构性地不存在**（没有「配了却没生效」的可配面）。

⇒ **本档的读法**：这里空着是**结论**，不是**待办**。
⚠️ 若日后 article-common 接入 DB / MQ / Spring 装配，**本档必须重新启用**。
