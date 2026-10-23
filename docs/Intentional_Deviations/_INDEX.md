# Intentional Deviations 台账总索引

> **这里记「明知与 Go 不同、且经过签字批准」的偏离。**
> 与 [`../DRIFT/_INDEX.md`](../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，Intentional_Deviations 记「决定不对齐的差异」。**

## ⚠️ 读之前先记住两条

1. **这是决策，不是缺陷。** 审查时**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
2. **⚠️ 本文件混合三类性质不同的差异，review 时不可混谈**（原文已声明）：
   ① **有意偏离** = 主动选择与 Go 不同（须签字）
   ② **框架限制** = 想对齐但语言/框架做不到
   ③ **表述差异** = 语义已对齐、仅字面不同

## 已有内容

| 文件 | 内容 |
|---|---|
| [`_cross/signed-deviations.md`](_cross/signed-deviations.md) | 签字偏离主表：HTTP path / HTTP method / 表名（Go `users`·`admins`·`admin_invites`·`user_avatar_history` ↔ RAG `users_user`·`admin_admin`·`admin_invite`·`users_user_avatar_history`），含「造阻塞的脚本必须先证明锁真的加上了」教训 |
| [`common/_signed-deviations.md`](common/_signed-deviations.md) | `service/common` 逐行审计产出的 20 项偏差中**修复后仍存在、无法靠改代码消除**的 5 条（含 A-1 等价域收窄等） |
| 🆕 [`comment/proto.md`](comment/proto.md) | `comment` 域 proto `package` 由 Go 的遗留名 `pb` 归一为 `comment`（**SD-2026-10-23-04**）—— gRPC 方法路径 `/comment.CommentService/*` vs Go 的 `/pb.CommentService/*`；当前无跨栈调用故无实际影响，实现该域时须显式处理 |
| 🆕 [`like/proto.md`](like/proto.md) | `like` 域 proto `package` 由 `pb` 归一为 `like`（**SD-2026-10-23-05**）—— 同类归一，但**该域已实现**：仓内 api↔rpc 自洽（108 例测试全绿，含真实 gRPC 往返）；仅跨栈互调会失效 |

## 待填（空文件即坑位）

其余 22 个文件与 `docs/DRIFT` 同构，**当前为空**。域内台账中已出现签字倾向的条目
（如 `like/rpc.md` 的 DRIFT-LIKE-001、`article/rpc.md` 的 6 条 `OBSERVABLE DIFFERENCE`）
尚未收编至此 —— 收编与否取决于本档案定位：**集中收编** vs **只放跨域的、域内仍留 DRIFT**。

## 搬移来源

两文件均为 AGENTS.md 原 **§3.4「已知偏离（有意为之，需留档）」**（含 §3.4.1 `service/common` 差异），
2026-10-23 独立成档。正文为原样字节拷贝，首行 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 保留溯源；
逐段台账见 [`../DRIFT/_MANIFEST.json`](../DRIFT/_MANIFEST.json)。

---

## SD-2026-10-23-01/02/03（2026-10-23 修复轮新增签字偏离）

> 从 AGENTS.md §〇 搬出。

⚠️ **2026-10-23 修复轮新增 3 条签字偏离**（`SD-2026-10-23-01/02/03`）：
   `admin-api` 的 `OperatorId`（Go 恒 0 且不校验 claim vs RAG 严格校验）、
   `user-api` 的 `uid`（Go 两套解析语义 vs RAG 统一拒绝）、
   `message-api` 的 `affected`（Go `int64` 无 `,string` vs RAG 输出字符串）。
   **三条共性：RAG 做了更严/更安全的那一侧，不是忘了做。**
   ⇒ 对拍看到这三条变红，先确认是不是这三条，**不要当新回归去「修」**。

## SD-2026-10-23-04（2026-10-23 12:25 范围扩展时新增）

> 详见 [`comment/proto.md`](comment/proto.md)。

⚠️ **`comment` 域 proto `package` 归一为 `comment`**（Go 侧为遗留名 `pb`）——
   **本条共性与 01/02/03 不同：不是「RAG 更严/更安全」，而是表达层归一（proto package / java_package / 模块名 / 域名 四者一致）**。
   代价是 **gRPC 方法路径前缀不同**（`/comment.CommentService/*` vs `/pb.CommentService/*`）——
   **wire 层可见**，但当前 `comment` 域 api / rpc 均为空壳、无跨栈调用 ⇒ **现在无实际影响**。
   ⚠️ **实现该域时必须显式处理**（改回 `pb`，或加服务名前缀映射），否则双侧活体对拍会 `UNIMPLEMENTED`。

## SD-2026-10-23-05（2026-10-23 用户指令追加：proto package 全域归一）

> 详见 [`like/proto.md`](like/proto.md)。

⚠️ **`like` 域 proto `package` 由 `pb` 归一为 `like`**（同 SD-04 的理由）。
   **该域已实现且做过双侧活体对拍**，但实测**影响面为零**：
   ① 仓内 `like-api` ⇄ `like-rpc` 是同源再生成 ⇒ 自洽；
   ② 56 条双侧活体对拍走的是 **HTTP 输入解释/响应字节**（Go 侧探针明确「不含业务逻辑（无 like-rpc）」）⇒ **不经过 gRPC 服务名**；
   ③ RAG 代码里 `pb.` 硬编码**零命中**（唯二命中是描述 Go 侧命名的 javadoc）。
   ⇒ 改包名后实测 **108 例测试全绿**（`like-api` 62 + `like-rpc` 46，含**真实 in-process gRPC 往返** 3 例）。
   ⚠️ **唯一失效面**：跨栈 gRPC 互调（Go like-rpc ⇄ RAG like-rpc）—— 当前不存在这种调用。
