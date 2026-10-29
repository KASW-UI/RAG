<!-- @new 2026-10-23 · like 域 proto 层签字偏离（SD-2026-10-23-05） -->

# `like` 域 proto 层签字偏离（SD-2026-10-23-05）

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

## SD-2026-10-23-05：`like.proto` 的 proto `package` 由 `pb` 归一为 `like`

> **唯一别名**：`SD-2026-10-23-05@like`（⚠️ **撞号事实**：同一编号在 `docs/Intentional_Deviations/like/api.md` 与 `.../like/rpc.md` 各被**引用一次**（指向**同一条**偏离，**正本在本文件**）⇒ 域内三处引用**不构成撞号**；但为跨域唯一定位仍加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效，继续计入计数** —— 被签字描述的行为**今天仍存在**：RAG 侧 `like.proto` 的 proto `package` 仍是 `like`，Go 侧仍是 `pb` ⇒ 差异未消除（**表达层**归一，不改 message 名 / 字段号 / 字段名 / 枚举值）
> **依据**：RAG `service/like/like-proto/src/main/proto/like.proto`（proto `package like;`）· Go `service/like/rpc/etcd/like.proto`（proto `package pb;`）⇒ **证据强度：两侧源码行号（静态）**。


| 项 | 值 |
|---|---|
| 状态 | ✅ 已签字（2026-10-23，用户指令，与 `comment` 同批） |
| 组件 | `service/like/like-proto` |
| 行为层 | proto `package` 声明（**wire 层可见**，见下） |
| Go 侧 | `package pb;` —— `Sea-RideTheWind_golang/proto/like.proto:3`；生成物 `ServiceName: "pb.LikeService"`（`service/like/rpc/pb/like_grpc.pb.go`） |
| RAG 侧 | `package like;` —— `service/like/like-proto/src/main/proto/like.proto:3`；生成物 `SERVICE_NAME = "like.LikeService"` |
| 性质 | **表达层归一 + 已知 wire 路径差异**（不是忘了做，也不是安全收紧） |

### 差异的实际面

| 侧 | 常量 | 实际方法路径 |
|---|---|---|
| Go | `ServiceName: "pb.LikeService"` | `/pb.LikeService/*` |
| RAG | `SERVICE_NAME = "like.LikeService"` | `/like.LikeService/*` |

⚠️ **这不是「纯表达层」**：gRPC 方法路径走 HTTP/2 `:path`，**wire 层可见**。

### 影响面逐条评估（**为什么现在安全**）

| # | 依赖点 | 结论 | 依据 |
|---|---|---|---|
| 1 | RAG 仓内 `like-api` → `like-rpc` | ✅ **无影响** | `LikeRpcClient` 用 `@GrpcClient("like-rpc")`（Nacos 寻址），**两端 stub 由同一份 `.proto` 再生成** ⇒ 方法路径两侧同时变为 `/like.LikeService/*`，自洽 |
| 2 | `like-api` 的 **56 条双侧活体对拍结论** | ✅ **无影响** | 对拍探针 **不经过 gRPC 服务名**：Go 侧 `/tmp/likeprobe/main.go` 明确「**不含业务逻辑（无 like-rpc）**、不含 HTTP 层」，只测输入解释与出参字节；RAG 侧用 `MockMvcBuilders.standaloneSetup` + 生产 `com.rag.like.like.proto` 生成类 —— **Java 类名来自 `java_package`，不随 proto `package` 变** |
| 3 | RAG 代码里的 `pb.` 硬编码 | ✅ **不存在** | 全仓扫描 `service/`：`pb.LikeService` / `"pb.` / `/pb/` 在 `*.java`/`*.xml`/`*.yaml`/`*.sql` 中**零命中**；唯二命中是 `LikeActionService.java:32,74` 的 javadoc，描述的是 **Go 侧**命名，仍正确 |
| 4 | **跨栈 gRPC 互调**（Go 的 like-rpc ⇄ RAG 的 like-rpc） | ⚠️ **会失效** | 方法路径前缀不同（`/pb.` vs `/like.`）⇒ 跨栈调用得 `UNIMPLEMENTED`。**当前仓内不存在这种调用**；若将来要做跨栈对拍，必须显式加**服务名前缀映射**或把一侧改回 `pb` |

### 为什么可以归一（与 `comment` 同理由）

Go 侧的 `pb` 是**遗留包名**（`option go_package = "./pb"`）；RAG 侧 `java_package` 已经是 `com.rag.like.like.proto`。
归一后 **proto package / java_package / 模块名 / 域名 四者一致**，可读性与可检索性更好。

⚠️ **归一后 RAG 侧 10 个 proto 的 package 全部与域名一致**
（`article` / `hot` / `like` / `message` / `user` / `admin` / `comment` / `favorite` / `follow` / `task`）
⇒ 仓内**不再有 `pb` 遗留名**，`favorite` / `follow` / `task` / `comment` 四域本就与 Go 一致（`favorite` / `follow` / `task`），
`comment` / `like` 两域为**已签字例外**。

### 复制忠实度的边界（§3.4）

AGENTS.md §3.4 规定 proto 的改动**严格限定在 option 块与空白层面**。
**本条是唯一一处落在该边界之外的改动**，且已单独签字 ⇒ 属于 §3.4 的**显式例外**，
**不是「§3.4 允许的改动」，也不是「忘了改」**。

其余行为层（message 名 / 字段名 / 字段号 / 类型 / 枚举值 / service 与 rpc 签名）**逐行一致**。

### 回归网

改包名后**实测跑绿**（`mvn -pl service/like/like-proto,service/like/like-api,service/like/like-rpc -am package`）：

| 模块 | 测试类 | 例数 | failures / errors |
|---|---|---|---|
| `like-api` | 5 | 62 | **0 / 0** |
| `like-rpc` | 6 | 46 | **0 / 0** |
| **合计** | 11 | **108** | **0 / 0** |

其中 `LikeServiceGrpcImplWireTest`（3 例）是**真实 in-process gRPC 往返**
（`LikeServiceGrpc.bindService(...)` + `newBlockingStub(channel).getLikeCount(...)`）⇒
**证明改的是「两端同时变」的自洽改名，而不是「单端偏移」**。

⚠️ `tools/difftest/check-proto-static.sh` 若扩到 `like`，其 `verdict` **必须排除 `package` 行**，
否则会把本条已签字偏离判成回退。
