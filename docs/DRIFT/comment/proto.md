<!-- @new 2026-10-24 · comment 域 proto 组件：§3.4 效力边界第 1 条「消费方式」首轮验证 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# DRIFT 台账 · `service/comment/comment-proto`

> **核对时间：2026-10-24**
> **真源：** `Sea-RideTheWind_golang/proto/comment.proto`（245 行）
> **本轮性质：** §3.4 已验过「**复制忠实**」（`docs/DRIFT/_cross/new-domains-2026-10-23.md`），
> 本轮补验 §3.4 **效力边界第 1 条**：「**不证明 RAG 侧使用生成代码的方式**与 Go 一致」。
>
> ⚠️ **证据强度**：静态全文 diff + 源码 grep **＋ 双侧活体对拍**（wire 层，2026-10-24 补上 ⇒ 见 §3.1）。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | ✅ **`SEMANTICALLY EQUIVALENT`**（消费方式层）—— ⚠️ **但跨栈路径前缀有差异**（见 §3.1 / §4） |
| SEMANTIC MISMATCH | **0 条** |
| OBSERVABLE DIFFERENCE | **0 条待处置** —— ⚠️ 口径澄清（2026-10-26）：§3.1 的「跨栈路径前缀差异」**不算新增的待处置差异** ⇒ 它是**已签字** `SD-2026-10-23-04`（proto `package` 归一）的**表现**，处置方式是**只增不改**（新增 `PbCommentServiceAlias` 别名）⇒ **已闭环**（残留面见 U4 / `rpc.md` U12·U13） |
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
| **`SD-2026-10-23-04`** | proto `package` 归一为 `comment` ⇒ gRPC 方法全名 `/comment.CommentService/*` vs Go 的 `/pb.CommentService/*` | ✅ **已签字（2026-10-23，用户指令）** —— **不是缺陷**<br>⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已签字** ⇒ **现行态 = ✅ 已签字（判定不变）** · 依据：RAG `service/comment/comment-proto/src/main/proto/comment.proto:3`（`package comment;`）· Go `proto/comment.proto:4`（`package pb;`）· 签字档 `docs/Intentional_Deviations/comment/proto.md:11`（`SD-2026-10-23-04` · 状态 **✅ 仍成立** · 核对日期 2026-10-29）· 跨栈兜底 `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/impl/PbCommentServiceAlias.java:53-54`（`@GrpcService` ＋ `implements BindableService` ⇒ 另注册 `/pb.CommentService/*`）· 核对日期 2026-10-30 ⇒ ⇒ **两侧 `package` 仍不一致 ⇒ 偏离对象未消失 ⇒ 签字仍有效** ⇒ **不改判**（⚠️ 「仓内自产自销无影响」这一判断**仍然成立**：RAG↔RAG 两侧都是 `/comment.`） |

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

## 3.1 🔴 跨栈 gRPC 路径前缀：`/pb.` vs `/comment.`（未知项 #5 的处置）

| 项 | 值 |
|---|---|
| **Go 侧** | `proto/comment.proto:4` `package pb;` ⇒ 方法全名 **`/pb.CommentService/*`** |
| **RAG 侧** | `proto/comment.proto:3` `package comment;` ⇒ 方法全名 **`/comment.CommentService/*`** |
| **已签字偏离** | `SD-2026-10-23-04`（proto `package` 归一）⇒ **不得推翻**（Go 侧 `package pb;` 是仓内自产自销的命名，RAG 侧归一后**仓内自洽**） |
| **问题** | 跨栈（Go 客户端 ↔ RAG 服务端）时，路径前缀不一致 ⇒ **直接 `UNIMPLEMENTED`** |
| **✅ 处置（只增不改）** | **新增** `service/comment/comment-rpc/.../service/impl/PbCommentServiceAlias.java`：`@GrpcService` + 实现 `BindableService`，用 `MethodDescriptor.toBuilder().setFullMethodName()` 把方法名改写成 `/pb.CommentService/*`，并**复用 `getServerCallHandler()`**（⇒ **同一份实现**，不复制逻辑）⇒ **两条路径都通**。<br>⚠️ **`/comment.` 原样保留** ⇒ RAG↔RAG 不受影响 ⇒ **不推翻** `SD-2026-10-23-04` |
| **🧪 守门网** | **新增** `PbCommentServiceAliasGateTest`（**6 例，真 in-process gRPC server**）：两条路径都通、指向**同一实现**、方法集合一一对应、**未注册方法仍 `UNIMPLEMENTED`** |
| **🔴 伴生阻塞（不可修）** | `io.grpc.Status` **只有 17 个常量** ⇒ grpc-java **发不出** `grpc-status: 1012` ⇒ RAG 的业务码放在**自定义 trailer** 里 ⇒ **Go 侧读不到** ⇒ **RAG→Go 方向**的业务码**必然退化成 `UNKNOWN(2)`**。<br>⇒ **结论：混跑必须同栈配对**（Go↔Go、RAG↔RAG），`PbCommentServiceAlias` **只是兜底**（解决「方法找不到」，解决不了「错误码传不过去」） |

> ⚠️ **这就是「半修」**：路径通了，**错误码仍然不通**。
> ⇒ 任何「让 Go 侧调 RAG 的 comment-rpc」的方案都**不能**指望业务码能透传
> ⇒ **RAG→Go 方向**必须**按 `UNKNOWN(2)` 处理**（⇒ 端到端业务语义会退化成「系统内部错误」）。

> 🟢 **⚠️ 但方向是「不对称」的 —— 这一点必须说准，否则会误判**（2026-10-24 订正）：
>
> | 方向 | 业务码能否透传 | 机制 |
> |---|---|---|
> | **Go-rpc → RAG-api** | 🟢 **能**（降级为「从 description 文本捞」） | Go 发 `grpc-status: 1012` ⇒ grpc-java 把它映射成 `UNKNOWN(2)` 并把 description 改成 `"Unknown code 1012"` ⇒ `CommentGrpcError.bizCodeFromError` 的**第①优先级**正是捞这个文本 ⇒ **码能捞回来** |
> | **RAG-rpc → Go-api** | 🔴 **不能** | RAG 侧 grpc-java **发不出** `grpc-status: 1012` ⇒ 只能放自定义 trailer ⇒ **Go 的 `status.FromError` 只读 `grpc-status`，看不见 trailer** ⇒ 端到端只剩 `UNKNOWN(2)` |
>
> ⇒ ⚠️ **「跨栈业务码不可传输」只对 RAG→Go 成立**；Go→RAG 是**通的**（靠 description 文本兜底）。
> ⇒ 这也是 `CommentGrpcError.bizCodeFromError` 判定优先级里**第①条**存在的理由。

---

## 4. 未决 / 待办

| # | 事项 | 类型 |
|---|---|---|
| **U1** | §3.4 效力边界第 1 条的三个子项**本轮全部验完**（`optional` 不适用 / 无字段号硬编码 / 序列化器无交叉）⇒ **本组件的首轮对照收口** | ✅ 已完成 |
| **U2** | ~~双侧活体对拍尚未做~~ ⇒ ✅ **2026-10-24 已做**：Go 真源 + RAG 四进程同跑，**23 步**矩阵逐字段差分 ⇒ wire 层已验。**路径前缀**按 §3.1 处理 | ✅ 已闭环 |
| **U4** | 🔴 **跨栈业务码不可传输**（协议层限制，不可修）：`io.grpc.Status` **只有 17 个常量** ⇒ grpc-java **发不出** `grpc-status: 1012` ⇒ RAG 的业务码在**自定义 trailer** 里 ⇒ **Go 侧读不到** ⇒ 跨栈错误码必然退化成 `UNKNOWN(2)` ⇒ **混跑必须同栈配对**，`PbCommentServiceAlias` 只是兜底 | 🔴 协议层限制（**非本域可修**） |
| **U3** | ~~`check-proto-static.sh` 尚未覆盖 `comment` ⇒ 「复制忠实」只是一次性快照~~ | ✅ **已闭环**（2026-10-24）：新增**域内**守门脚本 `tools/difftest/comment/check-proto-parity.py`（**不碰共享的 `tools/difftest/check-proto-static.sh`**）⇒ 剥掉 option/package + **归一化空白**后逐行比对（§3.4 允许缩进与空行统一）＋ 断言 package 仍归一（`SD-2026-10-23-04`）+ 断言 RAG 侧无残留 `go_package`。🧬 **变异实测**：改字段号 ⇒ `EXIT=1`；加回 `go_package` ⇒ `EXIT=1`；恢复 ⇒ `EXIT=0` |

---

## 5. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) | 本组件的**唯一**偏离落点（`SD-2026-10-23-04`，**已签字**） |
| [`docs/DRIFT/_cross/proto-verification.md`](../_cross/proto-verification.md) | §3.4 的全仓级验证 —— **共享文件，合并后由主会话统一更新**（本工作树不碰） |
| [`docs/DRIFT/_cross/new-domains-2026-10-23.md`](../_cross/new-domains-2026-10-23.md) | 四域 proto 层首轮对照 —— **共享文件**，本工作树不碰 |
