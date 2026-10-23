<!-- @new 2026-10-23 · comment/favorite/follow/task 四域 proto 层首轮对照 -->

# 四域 proto 层首轮对照（2026-10-23 12:25 范围扩展）

> 2026-10-23 12:25，用户指令把 `comment` / `favorite` / `follow` / `task` 四域由「范围外」升为「范围内」。
> 本文件是这 4 个域**首个台账**，覆盖范围仅 **proto 层**（api / rpc / common 尚无实现，无对照对象）。

## 结论速览

| 域 | proto 层 | api / rpc / common | 结论 |
|---|---|---|---|
| `comment` | ✅ 已搬齐，行为层逐行一致（**1 处已签字差异**） | 🔴 空壳（0 行 Java） | ✅ proto 层通过；差异已签字 ⇒ [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) |
| `favorite` | ✅ 已搬齐，**逐行零差异** | 🔴 空壳（0 行 Java） | ✅ proto 层通过 |
| `follow` | ✅ 已搬齐，**逐行零差异** | 🔴 空壳（0 行 Java） | ✅ proto 层通过 |
| `task` | ✅ 已搬齐，**逐行零差异** | 🔴 空壳（0 行 Java） | ✅ proto 层通过 |

⚠️ **四域的 api / rpc / common 共 11 个组件全是只有 `pom.xml` 的空壳** ⇒
**语义对照尚未开始**，这 11 个组件**不在** AGENTS.md §0 前 5 行的任何结论覆盖范围内。
`task` 域**没有 `-common`**（Go 侧同样没有，**不是遗漏**）。

## 复制忠实度实测

**方法**：剥掉 `option` 行、去掉每行首尾空白、丢弃空行后，逐文件 `diff` Go 侧与 RAG 侧。

| proto | 行为层 diff | message+enum 声明（Go = RAG） | 含字段号声明（Go = RAG） |
|---|---|---|---|
| `comment` | **仅 1 行不同**（`package pb;` → `package comment;`，**已签字**） | 20 = 20 | 69 = 69 |
| `favorite` | **0 行** | 16 = 16 | 38 = 38 |
| `follow` | **0 行** | 6 = 6 | 15 = 15 |
| `task` | **0 行** | 3 = 3 | 7 = 7 |

⚠️ **计数口径**：本表用 `^\s*(message|enum)\s+\w` 与
`^\s*(optional |repeated |map<)?[a-zA-Z0-9_.<>,\s]+ [a-z_]+ = [0-9]+`。
用同一口径复算原有 6 个 proto 会得到 `article` 54、`hot` 11、`like` 42、`message` 95、`user` 44
（与 [`proto-verification.md`](./proto-verification.md) 表中的 63 / 18 / 47 / 111 / 47 不同 ——
**那 5 个数用的是另一套未记录的正则**），**两侧仍然相等** ⇒ 差异只出在计数口径，不出在内容。
**主证据是逐行 diff，不是计数。**

## 落地形态（两件事，缺一不可）

### ① `.proto` 文件本身

从 Go 侧 `proto/*.proto` **复制**，非翻译。改动**严格限定在 option 块与空白层面**
（`comment` 的 `package` 归一属**唯一签字例外**，见上表链接）：

| 改动项 | 覆盖 |
|---|---|
| 删 `option go_package` | 4/4 |
| 加 `option java_package` + `java_multiple_files = true` | 4/4 |
| 缩进 4 空格 → 2 空格（仅 `comment` 需改）、首尾空行归一 | 4/4 |

### ② protoc 插件接线（**这一半最容易漏**）

⚠️ **这 4 个 `*-proto` 模块此前只有 `pom.xml`**（`<dependencies>` 与 `<build>` 全空）
⇒ 即便放进 `.proto`，**也没有插件消费它**，产出的是空 jar
—— 与 `article-proto` 曾经的形态**完全相同**（§3.3 的典型反例：
「文件看起来像那么回事」≠「能力存在」）。

本次已接上与 `article-proto` / `message-proto` **逐行同构**的插件段
（`os-maven-plugin` + `protobuf-maven-plugin`（compile + compile-custom）+ `build-helper-maven-plugin` + `maven-enforcer-plugin`），
**不重新设计**：换写法会把「protoc 平台二进制 / enforcer 版本下界」两个已踩过的坑重新踩一遍。

**实测产出**（`mvn -pl service/{comment,favorite,follow,task}/<域>-proto -am package`）：

| 模块 | 生成 java | `*Grpc` | 落包 | `SERVICE_NAME` |
|---|---|---|---|---|
| `comment-proto` | 37 | `CommentServiceGrpc` | `com.rag.comment.comment.proto` | `"comment.CommentService"` ⚠️ 与 Go 的 `"pb.CommentService"` 不同（**已签字**） |
| `favorite-proto` | 33 | `FavoriteServiceGrpc` | `com.rag.favorite.favorite.proto` | `"favorite.FavoriteService"` ✅ 与 Go 一致 |
| `follow-proto` | 11 | `FollowServiceGrpc` | `com.rag.follow.follow.proto` | `"follow.FollowService"` ✅ 与 Go 一致 |
| `task-proto` | 7 | `TaskServiceGrpc` | `com.rag.task.task.proto` | `"task.TaskService"` ✅ 与 Go 一致 |

## 证据强度

| 项 | 强度 |
|---|---|
| proto 文件内容逐行对照 | ✅ **双侧静态全文 diff**（可复现） |
| 生成代码是否真产出 | ✅ **真构建实测**（非「pom 里有依赖」「类上有注解」） |
| wire 层行为 | 🔴 **未验** —— 四域 api / rpc 均为空壳，无双侧活体对拍 |
| `optional` presence 语义 | 🔴 **未验** —— 无消费方代码可验 |

⇒ 本表 ✅ **只覆盖 proto 文件本身 + 代码生成**，
**推不出** wire 层行为一致（§3.4 效力边界第 1 条）。

## 待办

1. **实现四域的 api / rpc / common**（当前 11 个组件为空壳）⇒ 之后才谈得上语义对照。
2. **`comment` 域实现时**，按 [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)
   的「什么时候会变成真差异」一节处理方法路径前缀。
3. **`check-proto-static.sh` 扩到这 4 个**（照 `user` / `admin` 的写法加 `verdict`）
   —— 否则这 4 个的「一致」只是一次性快照，不是可持续的结论。
