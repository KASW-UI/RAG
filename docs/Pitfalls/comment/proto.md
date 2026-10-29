# Pitfalls · `service/comment/comment-proto`

> 领域：`docs/Pitfalls/comment/proto.md`（教训本体）
> 差异本体在 [`docs/DRIFT/comment/proto.md`](../../DRIFT/comment/proto.md)。
> **核对日期**：2026-10-29（本轮逐条回填 · 台账对齐 phase）。

## 本组件**没有**专属踩坑条目

> ⚠️ **本文件原先是 0 字节空壳**。按 `docs/Worktree.md` §二·五 的口径「**空文件即坑位** —— 填了就是新坑位，不填就别建空壳文件」，
> **两种做法都合法**：删掉、或写清「为何空」。本次选后者，理由是：
> **文件不存在 = 别的 agent 可能误读成「这个组件从没审过」** ⇒ 显式留痕更安全。
>
> **本轮（2026-10-29）已核**：
> `find service/comment/comment-proto/src/main -type f` ⇒ **1 个文件**
> （`src/main/proto/comment.proto`，**245 行**）；
> Go 侧对应件 = `proto/comment.proto`（**245 行**）⇒ **行数逐条相同**。

### 为什么本组件的「坑」都归到了别的档

本组件是**纯 IDL**：**无 Java 逻辑 / 无 DB / 无 RPC 调用 / 无 MQ / 无鉴权 / 无事务 / 无 Spring 装配层**
⇒ **框架机制静默失效这一类坑在本组件上结构性地不存在**。

本组件唯一的**已知非逐字复制点**已登记在**别的档**：

- **`proto package` 归一**（Go `package pb;` ⇒ RAG `package comment;`）—— 属**表达层**、**已签字**
  ⇒ 登记在 [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)
  的 **`SD-2026-10-23-04`**（明细与「为什么当前无实际影响 / 什么时候会变成真差异」在该文件里）
  ⇒ **不是坑**，不重复登记。
- 其余行为层（message 名 / 字段名 / 字段号 / 类型 / 枚举值 / service 与 rpc 签名）**逐行一致**
  ⇒ AGENTS.md §3.4 的复制忠实度结论见 `docs/DRIFT/_cross/proto-verification.md`
  （**跨域面 ⇒ 由主线维护，本 lane 不写**）。

⇒ **本档的读法**：这里空着是**结论**，不是**待办**。
⚠️ 若日后本组件引入 protoc 插件的额外行为（例如自定义生成选项 / 校验插件），**本档必须重新启用**。
