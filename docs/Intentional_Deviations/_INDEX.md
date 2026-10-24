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

## 计数（2026-10-25 机械扫描 + 人工去重）

> **口径**：一条 = 一个有**独立编号**的签字条目。
> ① **同一编号在不同域、内容不同 ⇒ 各算一条**（见下方「编号撞号」）；
> ② **同一编号跨文件转录同一件事 ⇒ 只算一条**；
> ③ **已撤销 / 已关闭的不计**。

| 编号族 | 条数 | 状态 |
|---|---|---|
| `SD-` 族（含 `SD-HOT-*` / `SD-USERAPI-*` / `SD-USERSCOMMON-*` / `SD-*-TASK-*` / 早期 `SD-1/2/3`） | **41 条** | ✅ 已签字 |
| `N-x` 族（`users/admin/_shared.md`，2026-10-21 用户拍板） | **3 条**（N-1 / N-4 / N-5） | ✅ 已签字 —— ⚠️ **原「41 条」没把它算进去** |
| `A/C/D-x` 族（`common/_signed-deviations.md`） | **5 条**（A-1 / A-2 / C-1 / D-1 / D-2；B-1 已关闭） | ✅ 已签字 —— ⚠️ **原「41 条」也没算进去** |
| **✅ 已签字合计** | **49 条** | |
| `CAND` 族（`SD-2026-10-24-CAND-01..09` + `SD-2026-10-25-CAND-10`） | **10 条** | ⏳ **未签字** |
| `like/api.md` 的「⏳（候选）」4 条（`SD-2026-10-23-08` / `-09` / `SD-2026-10-24-10` / `-11`） | **4 条** | ⏳ **未签字** —— ⚠️ **编号成 `SD-` 而非 `CAND-` ⇒ 原计数漏掉** |
| **⏳ 未签字候选合计** | **14 条** | |

⚠️ **2026-10-25 订正**：原表只写「41 条（另 10 条 CAND）」—— **两个数都偏低**：
「41」只覆盖了 `SD-` 编号族，漏掉 `N-x` 3 条 + `A/C/D-x` 5 条 ⇒ **实际 49 条**；
「10 条 CAND」漏掉 `like/api.md` 里 4 条标着「⏳（候选）」的条目（它们被编号成 `SD-`）⇒ **实际 14 条**。

⚠️ **读法**：**已签字的偏离是决策不是缺陷** —— 审查时**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
⚠️ **共性**：多数是「**RAG 做了更严/更安全的那一侧**」或「proto `package` 归一（表达层）」⇒ 对拍看到它们变红，**先确认是不是签字项，不要当新回归去「修」**。
⚠️ 逐条以各 `<域>/<组件>.md` 为准；本表只给计数与判据。

---

## 🔴 编号撞号（2026-10-25 扫描发现 · 台账自身的缺陷）

**同一个 `SD-` 编号在不同域指向不同的偏离** ⇒ **编号不可寻址** —— 「查 `SD-2026-10-23-06`」会查出 3 个互不相干的条目。

| 编号 | 撞号处 | 各处内容 |
|---|---|---|
| `SD-2026-10-23-01` | `_cross/signed-deviations.md` / `users/admin/api.md` | **同一件事的两处转录**（admin-api `OperatorId` + 严格校验 claim）⇒ 计数只算 **1** 条 |
| `SD-2026-10-23-06` | `like/rpc.md` / `message/rpc.md` / `users/admin/rpc.md` | 3 条不同：gRPC 业务码载体迁移 / `idx_notification_recipient_read` 列数 / `resetUserPassword` 空守卫 |
| `SD-2026-10-23-07` | `like/rpc.md` / `message/rpc.md`（**已撤销**）/ `users/admin/rpc.md` | 2 条有效 + 1 条撤销 |
| `SD-2026-10-23-08` | `like/api.md`（⏳候选）/ `message/rpc.md` / `users/admin/api.md` | 3 条不同：`Content-Type` / CPU 采样 fail-open / 黑名单 401 业务码 |
| `SD-2026-10-24-01` | `message/rpc.md` / `article/rpc.md` | 2 条不同：索引名 / 全局锁序 |
| `SD-2026-10-24-02` | `message/rpc.md` / `article/rpc.md` | 2 条不同：直方图桶边界 / Kafka 切 broker |

⇒ **修法方向（未实施）**：编号加**域前缀**（如 `SD-MSG-2026-10-24-01`），或改成全局递增。
⚠️ **改编号会破坏既有引用** —— DRIFT 台账、`@see` javadoc、测试注释都按旧号检索 ⇒ 属**跨档改造**（§5.3b：本档只动本档），**本轮只登记不动**。
⚠️ 判据：**引用签字偏离时必须同时给出「文件 + 域」**，不能只写编号。

---

## 已有内容

| 文件 | 内容 |
|---|---|
| [`_cross/signed-deviations.md`](_cross/signed-deviations.md) | 签字偏离主表：HTTP path / HTTP method / 表名（Go `users`·`admins`·`admin_invites`·`user_avatar_history` ↔ RAG `users_user`·`admin_admin`·`admin_invite`·`users_user_avatar_history`），含「造阻塞的脚本必须先证明锁真的加上了」教训 |
| [`common/_signed-deviations.md`](common/_signed-deviations.md) | `service/common` 逐行审计产出的 20 项偏差中**修复后仍存在、无法靠改代码消除**的 5 条（含 A-1 等价域收窄等） |
| 🆕 [`comment/proto.md`](comment/proto.md) | `comment` 域 proto `package` 由 Go 的遗留名 `pb` 归一为 `comment`（**SD-2026-10-23-04**）—— gRPC 方法路径 `/comment.CommentService/*` vs Go 的 `/pb.CommentService/*`；当前无跨栈调用故无实际影响，实现该域时须显式处理 |
| 🆕 [`like/proto.md`](like/proto.md) | `like` 域 proto `package` 由 `pb` 归一为 `like`（**SD-2026-10-23-05**）—— 同类归一，但**该域已实现**：仓内 api↔rpc 自洽（108 例测试全绿，含真实 gRPC 往返）；仅跨栈互调会失效 |

## 📌 10 域并发对齐后的产出（2026-10-25 合并后）

> 合并前本档填充率仅 11%（40 个空坑位）。10 个域并发对齐后，各域**把散落在 DRIFT 里的签字倾向条目收编至此**。
> ⚠️ **判据**：这条差异**要不要「修回去」**？要 ⇒ `DRIFT`；不要（有意）⇒ 本档。

| 域 | 已收编 | 说明 |
|---|---|---|
| `article` | `rpc.md` | — |
| `comment` | `api.md` · `proto.md` · `rpc.md` | 3 份，是本轮收编最多的域 |
| `hot` | `api.md` · `common.md` · `rpc.md` | 3 份 |
| `like` | `api.md` · `proto.md` · `rpc.md` | 3 份 |
| `message` | `api.md` · `rpc.md` | — |
| `task` | `rpc.md` | — |
| `users` | `_shared.md` · `admin/{_shared,api,rpc}.md` · `common.md` · `user/api.md` | 6 份 |
| `common` | `_signed-deviations.md` | — |
| `follow` | 0 份 | 本轮该域未产出签字偏离 |
| `favorite` | 0 份 | 本轮该域**未做**（合并时按要求跳过） |

⚠️ **本档定位已明确（2026-10-25）**：**集中收编** —— 域内发现的签字倾向也收编到本档的 `<域>/<组件>.md`，
不再只放跨域的。原先「集中收编 vs 只放跨域」这个悬而未决的问题就此定案。

## 待填（空文件即坑位）

`article/{api,common}` · `comment/common` · `hot/…` 已全填 · `like/…` 已全填 ·
`message/…` 已全填 · `task/{api,proto}` · `users/admin/…` 已全填 · `users/user/{rpc}` ·
`follow/*` · `favorite/*`（本域本轮未做）·
`_cross/{decisions,go-anchors,message-domain-wide,open-items,status-snapshot-2026-10-22,user-qa-verbatim}`

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

---

## SD-2026-10-23-04 / -05（proto `package` 归一）

> 从 AGENTS.md §〇 搬出。

⚠️ **2026-10-23 12:25 新增第 4 条**（`SD-2026-10-23-04`）—— `comment` 域 proto `package` 归一为 `comment`
   （Go 为遗留名 `pb`）⇒ **gRPC 方法路径前缀不同**（wire 层可见），当前该域 api/rpc 皆空壳故**无实际影响**，
   **实现该域时必须显式处理** ⇒ [`docs/Intentional_Deviations/comment/proto.md`](../../docs/Intentional_Deviations/comment/proto.md)。
   ⚠️ **同日追加第 5 条**（`SD-2026-10-23-05`）—— `like` 域 proto `package` 同样归一为 `like`；
   该域**已实现**但实测**影响面为零**（仓内自产自销 + 对拍探针不走 gRPC 服务名 + `pb.` 硬编码零命中），
   改包名后 **108 例测试全绿** ⇒ [`docs/Intentional_Deviations/like/proto.md`](../../docs/Intentional_Deviations/like/proto.md)。
   ⇒ 两条合起来的效果：**RAG 侧 10 个 proto 的 `package` 全部与域名一致，仓内不再有 `pb` 遗留名**。
