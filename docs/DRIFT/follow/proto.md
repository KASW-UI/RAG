<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-proto 组件级契约对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> **真源**：`Sea-RideTheWind_golang/proto/follow.proto`（**52** 行）
> **被审**：`service/follow/follow-proto/src/main/proto/follow.proto`（**53** 行）
> ⚠️ 差的那 1 行来自 **option 块**：`option go_package = "./pb";`（1 行）
> ⇒ RAG 侧换成 `option java_package` + `option java_multiple_files`（2 行）⇒ **表达层，非行为层**。

## 结论

**`SEMANTICALLY EQUIVALENT` —— 行为层逐行零差异。**

## 对照

| 项 | Go | RAG | 结论 |
|---|---|---|---|
| `package` | `follow` | `follow` | ✅ 一致 |
| `syntax` | `proto3` | `proto3` | ✅ 一致 |
| `option go_package` | `"./pb"` | **已删**（Java 不消费） | ✅ 表达层 |
| `option java_package` | — | `"com.rag.follow.follow.proto"` | ✅ 表达层 |
| `option java_multiple_files` | — | `true` | ✅ 表达层 |
| **message 声明** | 6 个（`RelationReq`/`BaseResp`/`ListReq`/`UserListResp`/`RecommendResp`+嵌套 `RecommendUser`） | 6 个 | ✅ 逐条一致 |
| **字段号** | 15 条 | 15 条 | ✅ 逐条一致 |
| **service `FollowService` + 8 个 rpc** | 8 个 | 8 个 | ✅ 逐条一致（签名与返回类型全同） |
| **行为层 diff**（剥 `option` 行 + **去掉全部首尾空白** + 丢空行） | — | — | ✅ **0 行差异** |

**service 全名**（wire 路径，决定客户端能不能连上）：

```
Go  : /follow.FollowService/Follow        （follow_grpc.pb.go:22-29）
RAG : follow.FollowService                （生成代码 SERVICE_NAME）
```

⇒ 两侧 wire 路径完全一致（实测：RAG follow-rpc 的 client 能调通 Go follow-rpc，反之亦然）。

## 证据

```bash
# 行为层逐行 diff（剥 option / 归一空白 / 丢空行）
diff <(norm Go/follow.proto) <(norm RAG/follow.proto)
→ 0 行差异
```

⚠️ **「归一空白」必须是「去掉全部前导空白」**（`sed 's/^[[:space:]]*//'`），
不能只去一个空格 ——
两侧**嵌套 message 内的字段缩进不同**（Go 4 空格 / RAG 2 空格，见 `RecommendResp.RecommendUser`）：

```
Go  :     int64 target_id = 1;      ← 4 空格
RAG :   int64 target_id = 1;        ← 2 空格
```

⇒ 只去一个前导空格会残留 **2 行差异**，让人误判「proto 不一致」
⇒ **这是纯空白差异，行为层仍是 0 行差异**（本轮复核实测确认）。

> ⚠️ 与 [`_cross/new-domains-2026-10-23.md`](../../_cross/new-domains-2026-10-23.md)
> 的「follow proto 逐行零差异」结论**一致**，本轮复核通过。

> **🧾 台账 ↔ 文件复核（2026-10-24）**：`.proto` 两侧行数、message/字段号/rpc 计数、
> 行为层 diff、生成代码数量**逐条实测复现**；复核中发现 2 处偏差已当场订正
> （Go 侧行数 52 非 53；**证据段的空白归一规则写错**，按原表述复现会残留 2 行差异）。

## 证据强度与效力边界

| 项 | 强度 |
|---|---|
| `.proto` 文件内容 | ✅ **双侧静态全文 diff**（可复现） |
| 代码生成 | ✅ 真构建实测（`follow-proto` 产出 11 个 java + `FollowServiceGrpc`） |
| **wire 层行为** | ✅ **本轮已升级为双侧活体对拍** —— RAG follow-rpc 与 Go follow-rpc **互相直连调通**（见 [`rpc.md`](./rpc.md) 对拍环境）<br>⇒ 不再是「proto 一致推不出 wire 一致」 |

> ⚠️ 本条**不覆盖** `optional` presence 语义 —— follow 域的 proto **没有 `optional` 字段**
> （只有 `users.proto` / `admin.proto` 有）⇒ 该边界在本域**不适用**。

## 备注

- 本组件**无漂移**，**无待办**。
- ⚠️ 与 `comment` 域不同：follow 的 `package` 两边**本来就一样**（都是 `follow`），
  ⇒ 不存在 comment 域那条「`package pb;` → `package comment;`」的签字偏离。
