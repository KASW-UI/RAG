<!-- @new 2026-10-23 · comment 域 proto 层签字偏离（SD-2026-10-23-04） -->

# `comment` 域 proto 层签字偏离（SD-2026-10-23-04）

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

## SD-2026-10-23-04：`comment.proto` 的 proto `package` 归一为 `comment` ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| 项 | 值 |
|---|---|
| 状态 | ✅ 已签字（2026-10-23，用户指令） |
| 组件 | `service/comment/comment-proto` |
| 行为层 | proto `package` 声明（**wire 层可见**，见下） |
| Go 侧 | `package pb;` —— `Sea-RideTheWind_golang/proto/comment.proto:4` |
| RAG 侧 | `package comment;` —— `service/comment/comment-proto/src/main/proto/comment.proto:3` |
| 性质 | **表达层归一 + 已知 wire 路径差异**（不是忘了做，也不是安全收紧） |

### 差异的实际面

proto `package` 决定 **gRPC 方法全名**：

| 侧 | 常量 | 位置 | 实际方法路径 |
|---|---|---|---|
| Go | `ServiceName: "pb.CommentService"` | `service/comment/rpc/pb/comment_grpc.pb.go:316` | `/pb.CommentService/CreateComment` |
| RAG | `SERVICE_NAME = "comment.CommentService"` | `target/generated-sources/protobuf/grpc-java/com/rag/comment/comment/proto/CommentServiceGrpc.java:15` | `/comment.CommentService/CreateComment` |

⇒ **两侧方法路径前缀不同**（`/pb.` vs `/comment.`）。
⚠️ **这不是「纯表达层」**：gRPC 方法路径走 HTTP/2 `:path`，**wire 层可见**。

### 为什么当前无实际影响

🔴 **2026-10-23 20:2x 更正 —— 本节前提已过期**：原文写「`comment` 域的 api / rpc / common 三个组件仍是空壳」，
实测**三个组件均已落地**（`comment-api` 33 类 / 2,838 行 / 22 tests、`comment-common` 4 / 537 / 27、
`comment-rpc` 40 / 7,455 / 46；全量编译 `EXIT=0`）⇒ **「没有任何 RAG 侧 client / server 会去连这个服务」不再成立**。

⇒ **本签字偏离必须从「无实际影响」升级为待处理**：一旦 `comment-api` ↔ `comment-rpc` 走真实 gRPC，
方法路径前缀不一致（`/pb.` vs `/comment.`）会**直接导致调用失败**，不是「对拍时才会发现」。
⚠️ 但这 14 个组件**零台账、尚未做过 Go ↔ RAG 对照** ⇒ 该偏离**到底何时变真，取决于对照结论**，不要在无对照的前提下擅自「修」。
详见 [`../DRIFT/_cross/component-coverage-matrix.md`](../../DRIFT/_cross/component-coverage-matrix.md)。

### 什么时候会变成真差异

一旦实现 `comment-api`（调 rpc）或 `comment-rpc`（对外提供服务），且要求与 Go 侧**双侧活体对拍**，
则方法路径前缀不一致会导致对拍失败（`UNIMPLEMENTED`）。
⇒ 实现该域时**必须**显式处理下列之一：

1. 把 RAG 侧 proto `package` 改回 `pb`（与 Go 同名）；或
2. 在对拍时加**服务名前缀映射**（Go 侧同时注册 `pb.CommentService` 别名）。

⚠️ **本次不选方案 1 的理由**：`pb` 是 Go 侧的遗留包名（`option go_package = "./pb"`），
RAG 侧 `java_package` 已经是 `com.rag.comment.comment.proto`；
proto `package` 归一为域名后，**proto package / java_package / 模块名 / 域名 四者一致**，可读性与可检索性更好。

### ✅ 与 `like` 域的现状（2026-10-23 用户指令追加后**已统一**）

| 域 | proto `package`（RAG） | 与 Go 一致？ | 状态 |
|---|---|---|---|
| `like` | `like` | ❌ 不一致 | **已签字** `SD-2026-10-23-05` ⇒ [`../like/proto.md`](../like/proto.md) |
| `comment` | `comment` | ❌ 不一致 | **已签字** `SD-2026-10-23-04`（本文） |

⚠️ **本节初稿写的是「`like` 保持 `pb`、两者不一致、且 `like` 不得动」** ——
该表述已被用户指令（"like也改成package like"）**推翻**，`like` 已同步归一。

⇒ **现状：RAG 侧 10 个 proto 的 `package` 全部与域名一致**，仓内**不再有 `pb` 遗留名**：

| proto | RAG package | Go package | 一致？ |
|---|---|---|---|
| `article` | `article` | `article` | ✅ |
| `hot` | `hot` | `hot` | ✅ |
| `like` | `like` | `pb` | ❌ 已签字 |
| `message` | `message` | `message` | ✅ |
| `user` → `users` | `user` | `user` | ✅ |
| `admin` | `admin` | `admin` | ✅ |
| `comment` | `comment` | `pb` | ❌ 已签字 |
| `favorite` | `favorite` | `favorite` | ✅ |
| `follow` | `follow` | `follow` | ✅ |
| `task` | `task` | `task` | ✅ |

### 复制忠实度的边界（§3.4）

AGENTS.md §3.4 规定 proto 的改动**严格限定在 option 块与空白层面**。
**本条是唯一一处落在该边界之外的改动**，且已单独签字 ⇒ 属于 §3.4 的**显式例外**，
**不是「§3.4 允许的改动」，也不是「忘了改」**。

其余行为层（message 名 / 字段名 / 字段号 / 类型 / 枚举值 / service 与 rpc 签名）
**逐行一致**：剥掉 `option` 行与空白后逐文件 diff，**仅 `package` 一行不同**（218 行中 217 行相同）。

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-23-04@comment` |
| **撞号检查** | 全仓 `grep -rln SD-2026-10-23-04 docs/` ⇒ **定义只此一处**（`docs/DRIFT/article/proto.md:70` · `docs/DRIFT/favorite/vs-common.md:305` · `docs/DRIFT/comment/{proto,rpc,vs-common}.md` 等**全部是引用**本条）⇒ **无撞号** |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ **签字有效，状态不变**（✅ 已签字） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测** | `service/comment/comment-proto/src/main/proto/comment.proto:3` = **`package comment;`**（`:5-6` = `option java_package` / `option java_multiple_files`） |
| **Go 侧实测** | `Sea-RideTheWind_golang/proto/comment.proto:4` = **`package pb;`**（`:6` = `option go_package = "./pb"`）⇒ ⇒ **两侧 `package` 仍不一致 ⇒ 签字描述的行为仍在** |
| **🔴 订正（行数口径 · 原句保留）** | 本条正文写「（218 行中 217 行相同）」⇒ **2026-10-29 实测两份 proto 都是 245 行**（`wc -l`：Go `proto/comment.proto` = 245 · RAG `service/comment/comment-proto/src/main/proto/comment.proto` = 245）⇒ ⇒ **「218 行」这个行数是旧口径（统计时点不同）**；**结论不变**（剥掉 `option` 行与空白后**仅 `package` 一行不同**），**只订正行数** |
| **⚠️ 效力边界提醒** | AGENTS.md §3.4 规定 proto 的改动**严格限定在 option 块与空白层面** ⇒ 本条是**唯一**落在该边界之外的改动，且**已单独签字** ⇒ **不得「顺手改回去」，也不得「顺手再改别的」** |
