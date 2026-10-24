# `hot-common` 有意偏离台账

> **范围**：`service/hot/hot-common`（Go `service/hot/common/errmsg.go`）。
> **归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
> **核对日期**：2026-10-24（第十一轮回填）。

## 本组件**没有**需要单独登记的签字偏离

> ⚠️ **本文件原先是 0 字节空壳**。按 `docs/Worktree.md` §二·五 的口径「**空文件即坑位** —— 填了就是新坑位，不填就别建空壳文件」，
> **两种做法都合法**：删掉、或写清「为何空」。本次选后者，理由是：
> **文件不存在 = 别的 agent 可能误读成「这个组件从没审过」** ⇒ 显式留痕更安全。

### 结论与依据

`hot-common` 与 Go 真源 `service/hot/common/errmsg.go`（121 行，纯值定义 + 纯函数库）的对照结论是
**语义等价**：11 个码值、11 条文案、**声明顺序 = Go 书写顺序**、未知码回落 `"FAIL"`、
双射与文案两两互异（Go `bizCodeByMsg` 的正确性前提）—— **双侧活体对拍 sha256 逐字节一致** `f23fe570…`。

⇒ **无「明知与 Go 不同」的差异 ⇒ 本档无条目。**
完整证据（4 层验证 + 变异验证 11/11 全红）⇒ [`../../DRIFT/hot/common.md`](../../DRIFT/hot/common.md)。

### 两条**看起来像偏离、实际不是**的项（登记在此，避免下次被误当新漂移）

1. **「有意不迁的 4 个符号」**（`NewErrCodeMsg` / `GRPCError` / `BizCodeFromError` / `bizCodeByMsg`）——
   **不是偏离，是死代码不迁**：这 4 个符号在 Go 侧 `service/hot` 全域**零调用点**、hot-rpc 对本文件**零引用**
   ⇒ **它们在 Go 侧本身就是死代码**。迁过来只会制造无调用方的结构。
   ⚠️ **边界**：日后 hot 若接上 gRPC 消费方，**照 Go 的判定顺序**补 `HotGrpcError`（照 `MessageGrpcError` 的形状），
   **不要临时另写一套**；判定顺序已完整留档在 `FileTree/hot/common.md §12.3`。
2. **「库层差异：下游 gRPC 错误的 `error_reason` 形态」**（grpc-go `"rpc error: code = Unavailable desc = …"`
   vs grpc-java `"UNAVAILABLE: …"`）——
   **这一条不归本档**，它归属 **hot-api** 档（`error_reason` 是 hot-api 的业务日志字段，码表不在本组件）
   ⇒ 已签字：**[`../api.md`](api.md) 的 `SD-HOT-API-005`**（2026-10-24 第六轮，用户拍板「不复刻」）。
   ⚠️ 另有一条**同族但语义不同**的：**hot-api 自己写的** `CodeError.Error()` 格式 ⇒ 那条**已修**
   （[`../../DRIFT/hot/api.md`](../../DRIFT/hot/api.md) 的 `DRIFT-HOTAPI-016`），**不是签字偏离**。

⇒ **本档的读法**：这里空着是**结论**，不是**待办**。
