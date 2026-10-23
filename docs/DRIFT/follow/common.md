<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-common 组件级语义对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> **真源**：`Sea-RideTheWind_golang/service/follow/common/errmsg.go`（133 行）
> **被审**：`service/follow/follow-common/`（4 个类 / 558 行 / 37 tests）

## 结论

**`SEMANTICALLY EQUIVALENT` —— 零漂移。** 16 个业务码 + 16 条文案 + 三条错误转换路径逐条一致，
且有 **37 条契约测试**把「与 Go 源码逐条一致」钉死。

## 对照表

| 维度 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| 16 个常量（值 + 顺序） | `errmsg.go:33-53` | `errmsg/FollowErrorCode.java:42-82` | ✅ 逐条一致（含 `CodeServerBusy=1015` 排在 5001 之前这个细节） |
| 16 条 `code → msg` 文案（**整表英文**） | `errmsg.go:55-74` | `errmsg/FollowErrorMessages.java:46-69` | ✅ 逐条一致 |
| 未知码回退 `"FAIL"`（500 号的文案） | `errmsg.go:89-95` | `FollowErrorMessages.java:78-85` | ✅ 一致（**不能**返回 null / 空串 / 码字符串） |
| `GRPCError(code, bizCode)` = 标准 gRPC 码 + description 放文案 | `errmsg.go:97-99` | `FollowGrpcError.java:91-95` | ✅ 一致 |
| `bizCodeByMsg` 反查（排除 200/500，first-write-wins） | `errmsg.go:76-87` | `FollowGrpcError.java:120-133` | ✅ 一致 |
| `BizCodeFromError` 判定顺序（null → 非 status → 文案反查 → switch 兜底） | `errmsg.go:101-133` | `FollowGrpcError.java:156-177` | ✅ 一致 |
| switch 白名单**恰好 6 个**码 | `errmsg.go:118-132` | `FollowGrpcError.java:191-211` | ✅ 一致（`Unavailable`/`DeadlineExceeded`/`Canceled` ⇒ default 1015） |
| `CodeError` 的 `Error()` 文本 `ErrCode:%d, Errmsg:%s` | `errmsg.go:15-17` | `FollowCodeError.java:51-56` | ✅ 一致 |
| `CodeError` JSON 只出 `code`/`msg`（不吐 message/cause/stackTrace） | `errmsg.go:10-13`（Go 只序列化导出字段） | `FollowCodeError.java:39-41`（`@JsonAutoDetect` 关掉继承 getter） | ✅ 一致 |

## 两条**容易踩反**的不对称（本组件已正确处理）

1. **`BizCodeFromError` 只判顶层**（直接类型断言 `err.(*CodeError)`，**不走 cause 链**）
   vs **`followcenter.go` 的 `errors.As` 会穿透 cause 链**
   ⇒ Java 侧对应物必须**一个用 `instanceof`、一个遍历 `getCause()`**，切勿写混。
   实现：`FollowGrpcError.bizCodeFromError`（顶层）vs `FollowCodeError` 的类注释（提示 api 侧遍历 cause 链）。
2. **`status.FromError` 的两段式**：Go 沿 cause 链找到 status 时会把 message 覆盖成
   **外层** `err.Error()` 的文本 ⇒ 文案反查落空。
   ⇒ Java 侧**不能**直接用 `Status.fromThrowable`（它原样保留 description），
   必须按 Go 的两段式手写。实现：`FollowGrpcError.resolveStatus`（`:262-284`）。

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（1 个 Go 文件 ↔ 4 个 Java 文件） | ✅ 双向静态 |
| 契约测试 | ✅ 37 条 / 0 失败（`FollowErrorCodeContractTest` 钉常量表与顺序、<br>`FollowErrorMessagesContractTest` 钉文案表与顺序、<br>`FollowGrpcErrorContractTest` 钉三段转换） |
| 活体对拍 | ✅ **通过** —— follow-api 收到的业务码与 Go 侧逐字节相同（36/36 对拍矩阵里的 5004/5005/5201/5202/5101/5001 全部一致） |

## 备注

- 本组件**无 RPC 依赖**（只依赖 `grpc-api` 的 `Status`/`StatusException`）⇒
  域内唯一的「共享契约」组件，无状态、无配置、无启动风险。
- ⚠️ `follow-common/pom.xml` 里也声明了 `grpc-inprocess`（`test` scope）——
  但本组件**不依赖 devh client starter**，所以**不存在** `follow-rpc` 那种
  「test scope 顶掉 compile 传递依赖」的问题 ⇒ **无需改动**。
