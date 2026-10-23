<!-- @new 2026-10-23 · comment/favorite/follow/task 四域 proto 层首轮对照 -->

# 四域 proto 层首轮对照（2026-10-23 12:25 范围扩展）

> 2026-10-23 12:25，用户指令把 `comment` / `favorite` / `follow` / `task` 四域由「范围外」升为「范围内」。
> 本文件是这 4 个域**首个台账**，首版覆盖范围仅 **proto 层**。
> **2026-10-23 19:20 更新**：api / rpc / common 开始落地（并发会话 + `comment-api` 迁移），
> 结论速览表已按实测刷新；proto 层结论不变。
>
> 🔴 **2026-10-23 20:2x 更新 —— 下表「api / rpc / common」三列已整体作废。**
> 一次真实执行（全量 `mvn -o -T1C install` `EXIT=0` + `mvn -o -T1C test` `BUILD SUCCESS`）实测：
> **四域的 api / rpc / common 代码已全部落地，38 个组件零空骨架** ——
> 「`favorite` / `follow` 的 api+rpc 仍 0 行」「`comment-rpc` 40 类里 17 个仍空」「11 个组件是空壳」
> **全部是 19:20 的过期快照**。组件级状态以
> [`component-coverage-matrix.md`](./component-coverage-matrix.md) 为准。

## 结论速览

**核对时间：2026-10-23 19:20**（行数/测试数为实测；⚠️ **仅 `comment-api` 由本仓跑过 `verify`**，
`task` / `favorite` / `follow` 是并发会话在写，本文件只登记其**存在性**，不背书其行为等价性）

| 域 | proto 层 | api / rpc / common（**20:2x 实测，原 19:20 列作废**） | 结论 |
|---|---|---|---|
| `comment` | ✅ 已搬齐，行为层逐行一致（**1 处已签字差异**） | ✅ **全部落地**：`api` 33 类 / 2,838 行 / 22 tests、`common` 4 / 537 / 27、`rpc` 40 / 7,455 / 46（原「17 类仍空 / 0 test」作废） | proto 层通过；差异已签字 ⇒ [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md) |
| `favorite` | ✅ 已搬齐，**逐行零差异** | ✅ **全部落地**：`common` 3 / 453 / 25、`api` 31 / 2,920 / 69、`rpc` 36 / 7,057 / 56（原「api/rpc 多数类空、均 0 test」作废；`rpc` 另有专属台账） | proto 层通过 |
| `follow` | ✅ 已搬齐，**逐行零差异** | ✅ **全部落地**：`common` 4 / 558 / 37、`api` 27 / 2,850 / 49、`rpc` 17 / 2,655 / 65（原「api/rpc 全空 0 行」作废） | proto 层通过 |
| `task` | ✅ 已搬齐，**逐行零差异** | ✅ **全部落地**：`api` 9 / 1,233 / 78、`rpc` 29 / 4,015 / 77 ⚠️ **其中 15 条静默 skip**（`@EnabledIfDockerAvailable`，见矩阵「绿灯底下的空洞」） | proto 层通过 |

⚠️ **「有代码」≠「已做语义对照」**：上表的 api/rpc 列记录的是**实现存在性**，
**不是** §3.5 意义上的组件级对照结论。
🔴 **20:2x 更正**：这 4 域**只有 `favorite-rpc` 有台账**（190 行，见 [`../favorite/rpc.md`](../favorite/rpc.md)）；
**其余 13 个组件零台账**，对照尚未开始 ⇒ 逐组件状态见 [`component-coverage-matrix.md`](./component-coverage-matrix.md)。
`task` 域**没有 `-common`**（Go 侧同样没有，**不是遗漏**）。

### `comment-api` 的证据边界（2026-10-23 19:20）

- ✅ **已跑**：`mvn -pl service/comment/comment-api -am verify` → BUILD SUCCESS，
  comment-api **22/22** + 上游 users-common 11 / common 146 / comment-common 27 全绿。
- ⚠️ **证据强度上限 = 「真实 MVC 管线 + mock gRPC 边界」**。**未做双侧活体对拍** ——
  Go 侧 `comment/api` 起依赖 `comment-rpc` + etcd + 真 PG，而 RAG 的 `comment-rpc` 尚未实现。
- ⇒ 台账里的 22 条绿测试**只能证明「RAG 按 Go 的源码描述实现了自己」**，
  **不能证明「Go 真跑起来就是这个行为」**（AGENTS.md §3.3 的双侧探针要求）。
- 已登记**未修**：gRPC client 熔断缺失（Go `zrpc` `Breaker` 默认 `true`）——
  修它必须先逐条核对 `comment-rpc` 返回的 gRPC code 落在 `codes.Acceptable` 的哪六个，
  否则会把业务错误算成失败，那是**新增行为**。

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

1. ~~**实现四域的 api / rpc / common**~~ —— ✅ **已全部完成（20:2x 实测）**：38 组件零空骨架、
   全量编译 `EXIT=0`、全量测试 `BUILD SUCCESS`。🔴 **下一步改为「做语义对照」**，排序见
   [`component-coverage-matrix.md`](./component-coverage-matrix.md)（硬约束：**rpc 先于 api**）。
1b. 🔴 **救活 `task-rpc` 的 15 条静默 skip**（`TaskProgressUpsertParityTest`）——
   ✅ **根因已定性**：`task-rpc` 是**全仓唯一漏配 `RYUK_DISABLED` surefire 注入的容器模块**，
   而 `@EnabledIfDockerAvailable` 在类级求值时要去拿 Docker client ⇒ 触发拉 Ryuk 镜像 ⇒
   `ContainerFetchException` 被 `DockerAvailableDetector` 的 `catch (Throwable)→false` 压成
   「Docker is not available」⇒ 整类静默 disable。⚠️ **本条只诊断未实施** ⇒
   详见 [`component-coverage-matrix.md`](./component-coverage-matrix.md)。
2. **`comment-rpc` 落地后**：`comment-api` 才具备双侧活体对拍条件（当前只有 mock gRPC 边界）。
3. **gRPC client 熔断**（`comment-api` 已登记未修，见上「证据边界」一节）。
4. **`comment` 域实现时**，按 [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)
   的「什么时候会变成真差异」一节处理方法路径前缀。
5. **`check-proto-static.sh` 扩到这 4 个**（照 `user` / `admin` 的写法加 `verdict`）
   —— 否则这 4 个的「一致」只是一次性快照，不是可持续的结论。
