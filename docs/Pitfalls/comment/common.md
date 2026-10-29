# Pitfalls · `service/comment/comment-common`

> 领域：`docs/Pitfalls/comment/common.md`（教训本体）
> 差异本体在 [`docs/DRIFT/comment/common.md`](../../DRIFT/comment/common.md)。
> **核对日期**：2026-10-29（本轮逐条回填 · 台账对齐 phase）。

## 本组件**没有**专属踩坑条目

> ⚠️ **本文件原先是 0 字节空壳**。按 `docs/Worktree.md` §二·五 的口径「**空文件即坑位** —— 填了就是新坑位，不填就别建空壳文件」，
> **两种做法都合法**：删掉、或写清「为何空」。本次选后者，理由是：
> **文件不存在 = 别的 agent 可能误读成「这个组件从没审过」** ⇒ 显式留痕更安全。
>
> **本轮（2026-10-29）已核**：
> `find service/comment/comment-common/src/main -type f` ⇒ **4 个文件 / 563 行**
> （`errmsg/CommentCodeError.java` · `errmsg/CommentErrorMessages.java` ·
> `errmsg/CommentGrpcError.java` · `errmsg/CommentErrorCode.java`）；
> Go 侧对应件 = `service/comment/common/errmsg/errmsg.go`（**1 个文件 / 103 行**）。

### 为什么本组件的「坑」都归到了别的档

`comment-common` 是**纯值定义 + 一个错误通道的编码端**：
**无 DB / 无 RPC 调用 / 无 MQ / 无鉴权 / 无事务 / 无缓存 / 无 Spring 装配层** ⇒
**框架机制静默失效这一类坑在本组件上结构性地不存在**（没有「配了却没生效」的可配面）。

本组件在审计中**确实踩到过**的两件事，都已落到**别的档**：

1. **跨模块自定义 trailer 通道的「两端只实现一端」** —— 通道的**生产端**就在本组件
   （`service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/CommentGrpcError.java:72` 定义 key
   · `:141` 写 trailer），
   而**消费端**在 `comment-api` ⇒ 缺陷落在 `comment-api` ⇒ 已修（`CommentApiSupport.java:210` 改调
   `bizCodeFromError`）· 登记在 [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) 的 **MISMATCH-API-01**，
   踩坑机制 ⇒ 登记在 [`api.md`](api.md) 的 **PIT-COMMENT-01**（旧号 `PIT-COMMENT-API-01`）。
2. **「proto `package` 归一」**（`pb` → `comment`）—— 属**表达层**、已签字 ⇒
   登记在 [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)
   的 `SD-2026-10-23-04` ⇒ **不是坑**，不重复登记。

⇒ **本档的读法**：这里空着是**结论**，不是**待办**。
⚠️ 若日后 comment-common 接入 DB / MQ / Spring 装配，**本档必须重新启用**。
