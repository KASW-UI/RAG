# `follow-common` 踩坑档（为什么会踩）

> **本域暂无已登记坑位**（核对日期 2026-10-29 · 已核 `grep -cE 'PITFALL-FOLLOW|PIT-FOLLOW' docs/DRIFT/follow/common.md` ⇒ **0**；
> 且 `grep -cE '^## PIT-' docs/Pitfalls/follow/{api,rpc}.md` ⇒ **9 + 7 = 16** 条全部落在 `api.md` / `rpc.md` 两个组件上 ⇒ **本文件无独立条目**）。
>
> ⚠️ **不是「本组件没有陷阱」** —— `docs/DRIFT/follow/common.md` 的「两条**容易踩反**的不对称」**是**真实陷阱
> （① `BizCodeFromError` 只判顶层**不走 cause 链** vs Go 侧 `errors.As` 会穿透；
> ② `status.FromError` 的**两段式** ⇒ Go 会用外层 `err.Error()` 覆盖 message ⇒ Java 侧不能直接用 `Status.fromThrowable`），
> 但**本组件已正确处理**（`FollowGrpcError.bizCodeFromError` 顶层判定 / `FollowGrpcError.resolveStatus` 两段式手写）
> **且有 37 条契约测试钉住** ⇒ ⇒ **记在 DRIFT 明细面更合适**（那边是逐条对照表）⇒ **本档不重复登记**。
>
> **若将来本组件真的踩坑**（例如 37 条契约测试被改弱、或 `resolveStatus` 被「简化」成 `Status.fromThrowable`）⇒ **在本文件登记一条 `PIT-FOLLOW-NN`**，并同步更新 [`docs/DRIFT/follow/common.md`](../../DRIFT/follow/common.md)。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/common.md`](../../DRIFT/follow/common.md)
