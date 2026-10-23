<!-- @new 2026-10-24 · comment 域 proto 组件：§3.4 效力边界第 1 条「消费方式」首轮验证 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# DRIFT 台账 · `service/comment/comment-proto`

> **核对时间：2026-10-24**
> **真源：** `Sea-RideTheWind_golang/proto/comment.proto`（245 行）
> **本轮性质：** §3.4 已验过「**复制忠实**」（`docs/DRIFT/_cross/new-domains-2026-10-23.md`），
> 本轮补验 §3.4 **效力边界第 1 条**：「**不证明 RAG 侧使用生成代码的方式**与 Go 一致」。
>
> ⚠️ **证据强度**：静态全文 diff + 源码 grep。**未做双侧活体对拍**（wire 层）。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | ✅ **`SEMANTICALLY EQUIVALENT`**（消费方式层） |
| SEMANTIC MISMATCH | **0 条** |
| OBSERVABLE DIFFERENCE | **0 条**（本轮新增） |
| 已知签字偏离 | **1 条**：`SD-2026-10-23-04`（proto `package` 归一）—— **不是缺陷**，见 [`docs/Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) |
| 复制忠实度 | ✅ **245 行 vs 245 行，剥掉 `option` 与 `package` 后逐字节完全一致** |
| `optional` 字段 | **本 proto 零个 `optional`** ⇒ §3.4 效力边界第 1 条的「optional presence 语义」在本域**不适用** |

---

## 1. 复制忠实度（本轮复核）

| 项 | Go 侧 | RAG 侧 | ✅ |
|---|---|---|---|
| 总行数 | 245 | 245 | ✅ |
| `package` | `package pb;`（`:4`） | `package comment;`（`:3`） | ⚠️ **已签字** `SD-2026-10-23-04` |
| `option` 块 | `option go_package = "./pb";`（`:6`） | `option java_package = "com.rag.comment.comment.proto";` + `option java_multiple_files = true;`（`:5-6`） | ✅ 表达层 |
| **行为层**（message 名 / 字段名 / 字段号 / 类型 / 枚举值 / service 与 rpc 签名） | — | — | ✅ **剥掉 `option` 与 `package` 后逐行 diff 为空** |

> ✅ 与 `docs/DRIFT/_cross/new-domains-2026-10-23.md:53` 记的「仅 1 行不同」一致 —— 本轮用全量逐行 diff 复核，**结论不变**。

---

## 2. 🔍 §3.4 效力边界第 1 条逐项验证

§3.4 原文：「**复制忠实 ≠ 消费方式已验**。本条只证明 `.proto` **文件本身**两侧一致，
**不证明 RAG 侧使用生成代码的方式**与 Go 一致（`optional` presence 语义、
字段号是否被某处硬编码覆盖、Jackson/protobuf 配置差异）。」

| 判定点 | 核查方式 | 结论 | ✅ |
|---|---|---|---|
| **`optional` presence 语义** | `grep -n "optional" comment.proto` ⇒ **零命中** | 本 proto **没有任何 `optional` 字段** ⇒ proto3 默认 presence 规则（标量无 presence / message 有 presence）两侧完全同构 ⇒ **该子项不适用** | ✅ |
| **字段号是否被硬编码覆盖** | 全量 grep `getFieldNumber` / `FieldNumber` / 数字字段下标 ⇒ **零命中** | RAG 侧全部走生成代码的具名 setter/getter，**无一处按字段号或下标访问** | ✅ |
| **`hasXxx()` 的使用是否与 Go 一致** | grep `has[A-Z]` ⇒ 2 处：`ArticleRpcClient.java:77` `hasArticle()`、`GetCommentService.java:122` `hasSubject()` | 两处都对应 Go 的 `resp.Article == nil` / `rpcResp.GetSubject() != nil` 判空 ⇒ 语义一致 | ✅ |
| **未知枚举值的透传** | `CommentDomainService.java:310/588/709` 用 `getStateValue()` / `getReasonValue()`；`CommentController` 用 `setActionTypeValue()` | Go 侧是 `int32(in.State)` / `pb.ManageType(req.ActionType)` **纯数值转换**，proto3 下未知值原样上线；protobuf-java 的枚举无法表达未知数值 ⇒ 必须用 `getXxxValue()` 才保得住 | ✅ **RAG 在这里比 Go 更严谨，结果相同** |
| **Jackson / protobuf 配置差异** | 本 proto **不经过 Jackson** —— gRPC 层用 protobuf-java 的 `BinaryMarshaller`；Jackson 只用于 HTTP 层的 `comment-api` DTO（**另一套 DTO，不复用 proto 消息**） | 两条链路各自的序列化器无交叉污染 | ✅ |

---

## 3. 已知签字偏离（非缺陷）

| ID | 内容 | 状态 |
|---|---|---|
| **`SD-2026-10-23-04`** | proto `package` 归一为 `comment` ⇒ gRPC 方法全名 `/comment.CommentService/*` vs Go 的 `/pb.CommentService/*` | ✅ **已签字（2026-10-23，用户指令）** —— **不是缺陷** |

> ⚠️ **读这条时注意**：`SD-2026-10-23-04` 的原文写「本域 api / rpc / common 三个组件仍是空壳 ⇒ 没有任何 RAG 侧 client / server 会去连这个服务」，
> 该前提**已于 2026-10-23 20:2x 被实测推翻**（三个组件均已落地）。
> ⇒ **这条偏离从「无实际影响」升级为「待处理」**：一旦 `comment-api` ↔ `comment-rpc` 走真实 gRPC，
> 方法路径前缀不一致（`/pb.` vs `/comment.`）会**直接导致调用失败**（`UNIMPLEMENTED`）。
>
> 🟥 **本轮的实际状态**：`comment-api` 与 `comment-rpc` **确实在同一套部署里互相调用**
> （`CommentRpcClient.java:42-43` `@GrpcClient("comment-rpc")` ⇒ comment-rpc 的 `CommentServiceGrpcImpl`）。
> ⇒ **两侧 proto 的 `package` 都是 `comment` ⇒ RAG↔RAG 的路径前缀是自洽的**（都是 `/comment.CommentService/*`）。
> ⇒ 结论：**该偏离只在「RAG ↔ Go 真源」跨栈对拍时才成为障碍**，仓内自产自销无影响。
> ⚠️ 但**双侧活体对拍必须显式处理路径前缀映射**（Go 侧同时注册 `pb.CommentService` 别名），
> 否则对拍会直接 `UNIMPLEMENTED` —— 这一点已写进
> [`docs/Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) 的「什么时候会变成真差异」。

---

## 4. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | §3.4 效力边界第 1 条的三个子项**本轮全部验完**（`optional` 不适用 / 无字段号硬编码 / 序列化器无交叉）⇒ **本组件的首轮对照收口** | ✅ 已完成 |
| **U2** | **双侧活体对拍尚未做** —— 前提是 Go 侧 `comment-rpc` 起得来（依赖 `etcd` + Kafka + 真 PG）。做之前必须先解决 `SD-2026-10-23-04` 的路径前缀映射 | ⚠️ 证据缺口 |
| **U3** | `check-proto-static.sh` 尚未覆盖 `comment`（既有台账 `new-domains-2026-10-23.md:125-126` 已列待办）⇒ 本轮的「复制忠实」是一次性快照，**不是可持续结论** | ⚠️ 待办 |

---

## 5. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) | 本组件的**唯一**偏离落点（`SD-2026-10-23-04`，**已签字**） |
| [`docs/DRIFT/_cross/proto-verification.md`](../_cross/proto-verification.md) | §3.4 的全仓级验证 —— **共享文件，合并后由主会话统一更新**（本工作树不碰） |
| [`docs/DRIFT/_cross/new-domains-2026-10-23.md`](../_cross/new-domains-2026-10-23.md) | 四域 proto 层首轮对照 —— **共享文件**，本工作树不碰 |
