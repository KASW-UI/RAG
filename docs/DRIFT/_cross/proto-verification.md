<!-- @move AGENTS.md §3.4 · proto 复制一致性的实测数据与回归网状态 -->

# proto 复制一致性：实测数据与回归网状态

> 从 AGENTS.md §3.4 搬出。**规则与效力边界留在 AGENTS.md §3.4**，本文是它的证据。

---


**实测方法（可复现）**：剥离 `option` 行、注释、缩进、空行后逐文件 `diff`，
**8/10 输出为空；`comment` 与 `like` 各 1 行不同**（均为 proto `package` 归一，
**已签字偏离** `SD-2026-10-23-04` / `SD-2026-10-23-05`
⇒ [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)
· [`../../Intentional_Deviations/like/proto.md`](../../Intentional_Deviations/like/proto.md)）；
`message`+`enum` 声明数与含字段号声明数两侧逐个相等：

| proto | 行为层 diff | message+enum（Go = RAG） | 含字段号声明（Go = RAG） |
|---|---|---|---|
| `admin` | 0 行 | 28 = 28 | 61 = 61 |
| `article` | 0 行 | 14 = 14 | 63 = 63 |
| `hot` | 0 行 | 11 = 11 | 18 = 18 |
| `like` | **1 行（已签字）** | 16 = 16 | 47 = 47 |
| `message` | 0 行 | 22 = 22 | 111 = 111 |
| `user` → `users` | 0 行 | 20 = 20 | 47 = 47 |
| 🆕 `comment` | **1 行（已签字）** | 20 = 20 | 69 = 69 |
| 🆕 `favorite` | 0 行 | 16 = 16 | 38 = 38 |
| 🆕 `follow` | 0 行 | 6 = 6 | 15 = 15 |
| 🆕 `task` | 3 = 3 | 7 = 7 |

⚠️ **最后 4 行的计数口径与前 6 行不同**（前 6 行的正则未记入本文，无从复现）：
新 4 行用 `^\s*(message|enum)\s+\w` 与
`^\s*(optional |repeated |map<)?[a-zA-Z0-9_.<>,\s]+ [a-z_]+ = [0-9]+` 统计。
用同一口径复算前 6 个会得到 `article` 54、`hot` 11、`like` 42、`message` 95、`user` 44、
**两侧仍然相等** ⇒ 差异只出在计数口径，不出在内容。
**真正的主证据是逐行 diff 为空**，计数只是指纹。

⇒ **proto 不是 §3.1 意义上的「翻译点」，它没有可漂移的表达层。**
§3.1 要求覆盖的「proto 字段号、message 名称」两项由此覆盖。

⚠️ **三档回归网状态（勿混为一谈，2026-10-23 实跑）**：

| 档 | proto | 证据 | 能否防回退 |
|---|---|---|---|
| ✅ 有网 | `user` / `admin` | `tools/difftest/check-proto-static.sh` → `EXIT=0`，脚本逐字节比对字段名 / 字段号 / message 名 / rpc 签名 | ✅ 改坏即红 |
| ✅ **已有网（2026-10-29 扩面）** | **全部 10 个 proto** | `tools/difftest/check-proto-static.sh` ⇒ **2026-10-29 扩到 10 个域**（R3·G-6）⇒ 逐字节比对**字段名 / 字段号 / message 名 / rpc 签名**；⚠️ `like` / `comment` 的 proto `package` 行**按签字偏离排除**，但**另钉签字值**（改动仍会红）<br>另：各域还有**专属契约测试**（`ArticleProtoContractTest` / `HotProtoContractTest` / `FavoriteProtoWireParityTest` / `LikeProtoContractTest` …） | ✅ **改坏即红**（2026-10-29 两次变异验证：改字段号 ⇒ 红；改签字 package 值 ⇒ 红） |
| 🚫 **不做（非本项目范围）** | `knowledge` / `points` / `security` | Go 侧 `proto/` 共 12 个，RAG 侧做 10 个；缺的这 3 个**不在范围内**，见 §1「微服务范围」 | 不适用 |

🆕 **2026-10-23 12:25 新增 4 个 proto 的补充事实**：
这 4 个 `*-proto` 模块此前**只有 `pom.xml`**（无 `<dependencies>` / `<build>`），
即便放 `.proto` 也**没有插件消费** ⇒ 与 `article-proto` 曾经的空 jar 同一形态。
本次一并接上与 `article-proto` / `message-proto` 逐行同构的 protoc 插件段，
`mvn package` 实测产出 **88 个 java + 4 个 `*Grpc`**：

| 模块 | 生成 java | `*Grpc` | 落包 |
|---|---|---|---|
| `comment-proto` | 37 | `CommentServiceGrpc` | `com.rag.comment.comment.proto` |
| `favorite-proto` | 33 | `FavoriteServiceGrpc` | `com.rag.favorite.favorite.proto` |
| `follow-proto` | 11 | `FollowServiceGrpc` | `com.rag.follow.follow.proto` |
| `task-proto` | 7 | `TaskServiceGrpc` | `com.rag.task.task.proto` |

⚠️ **`comment` 与 `like` 的 proto `package` 已由 Go 的遗留名 `pb` 归一为域名**
（**已签字偏离** `SD-2026-10-23-04` / `SD-2026-10-23-05`）⇒ 这两个的**行为层 diff 不是 0 行，而是 1 行**。
gRPC 侧后果：RAG 的 `SERVICE_NAME` 分别是 `"comment.CommentService"` / `"like.LikeService"`，
Go 侧是 `"pb.CommentService"` / `"pb.LikeService"`
⇒ 方法路径 `/comment.CommentService/*`、`/like.LikeService/*` vs Go 的 `/pb.CommentService/*`、`/pb.LikeService/*`。
- `comment`：域内 api / rpc **均为空壳、无跨栈调用** ⇒ **无实际影响**；实现该域时必须显式处理。
- `like`：**域已实现**，但实测**影响面为零**（仓内自产自销 + 对拍探针不走 gRPC 服务名 + `pb.` 硬编码零命中）
  ⇒ 改包名后 **108 例测试全绿**（含真实 in-process gRPC 往返 3 例）。

⇒ **现状：RAG 侧 10 个 proto 的 `package` 全部与域名一致，仓内不再有 `pb` 遗留名。**
⇒ **待办**：`check-proto-static.sh` 应扩到剩余 8 个 proto（照 `user` / `admin` 的写法加 `verdict` 即可），
否则这 8 个的「一致」只是一次性快照，不是可持续的结论。
⚠️ **`comment` 与 `like` 的 `verdict` 必须排除 `package` 行**，否则会把已签字偏离判成回退。
