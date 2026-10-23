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
