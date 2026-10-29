<!-- @new 2026-10-23 · like 域 rpc 层签字偏离（修复轮定性） -->

# `like` 域 rpc 层签字偏离

> 归类：**「框架限制」+「有意偏离」两类**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「已判定不必/不能对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

---

## SD-2026-10-23-06：gRPC 业务码的载体迁移（`grpc-status` → 自定义 trailer）

> **唯一别名**：`SD-2026-10-23-06@like`（🔴 **撞号事实**：同一编号在 [`docs/Intentional_Deviations/message/rpc.md`](../message/rpc.md)（索引列数）与 [`docs/Intentional_Deviations/users/admin/rpc.md`](../users/admin/rpc.md)（`resetUserPassword` 空守卫）指向**三条完全不同**的偏离 ⇒ 跨域撞号 ⇒ 本条以 **`SD-2026-10-23-06@like`** 为准）
> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效，继续计入计数** —— 被签字描述的行为**今天仍存在**：RAG 侧业务码仍走**自定义 trailer** `rag-like-biz-code`，不是 `grpc-status`
> **依据**：`service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:78`（`BIZ_CODE_TRAILER_KEY = "rag-like-biz-code"`）+ `:140`（写 trailer）+ `:110`（`mapToGrpcCode`）⇒ **证据强度：源码行号（静态）**。


| 项 | 值 |
|---|---|
| 状态 | ✅ 已签字（2026-10-23，修复轮定性） |
| 组件 | `service/like/like-common` + `like-rpc` + `like-api` |
| 原台账编号 | **DRIFT-LIKE-002**（SEMANTIC MISMATCH，🔴） |
| 现定性 | **框架限制（grpc-java 无法构造越界 grpc-status）**，**不是**实现缺陷 |
| 性质 | 平台强制；RAG 侧已取「等价信息保全 + 载体迁移」的**最优可行方案** |

### 为什么不能对齐（框架限制的证据）

grpc-api **1.63.0**（本仓 `pom.xml` 的 `<grpc.version>`）实测：

| 证据 | 位置 | 结论 |
|---|---|---|
| `Code` 枚举只有 17 个成员 | `io/grpc/Status.java:66` `OK(0)` … `:216` `UNAUTHENTICATED(16)` | 合法值仅 **0~16** |
| 越界码一律降级 | `Status.java:305-310` `fromCodeValue`：`if (codeValue < 0 \|\| codeValue >= STATUS_LIST.size()) return UNKNOWN.withDescription("Unknown code " + codeValue)` | `5003` ⇒ `UNKNOWN(2)` + **`desc` 被覆盖** |
| 无法从 int 构造 | `Status.java:349` `fromCode(Code)` 只接受枚举 | 连编译都过不了 |
| 构造函数是 private | `Status.java:442,446` `private Status(Code)` / `private Status(Code, String, Throwable)` | 没有旁路 |

而 Go 侧是 **无校验的强转**：

```go
// Sea-RideTheWind_golang/service/like/common/errmsg/errmsg.go:33-35
func NewGrpcErr(code int, msg string) error { return status.Error(codes.Code(code), msg) }
```

grpc-go 的 `codes.Code` 是 `int32` 命名类型、**不做范围校验**，服务端写 trailer 时
`strconv.Itoa(int(s.Code()))` ⇒ 线上真实字节就是 `grpc-status: 5003`。
⇒ **Go 能发，grpc-java 不能发**，属平台能力差，不是谁写错了。

### RAG 侧做了什么（已实施，不要改）

`like-common/…/LikeGrpcError.java`：

| Go 放在哪 | RAG 放在哪 | 可否复原 |
|---|---|---|
| `grpc-status` 里的业务码 | 自定义 trailer `rag-like-biz-code`（ASCII 数值） | ✅ **无损**（`LikeGrpcErrorTest#bizCodeTrailerRoundTripsLosslessly`） |
| `description` 里的文案 | 仍放 `description`，**逐字不变** | ✅ **无损** |
| gRPC 标准码 | 只用于「错误类别」，且**刻意避开熔断失败名单** | 见下 |

⚠️ **标准码选型不是随意的**：`mapToGrpcCode` 把 `ERROR_INPUT_WRONG(2001)` → `INVALID_ARGUMENT(3)`，
其余 → `UNKNOWN(2)`。**绝不能用 `INTERNAL`/`UNAVAILABLE` 等** ——
go-zero 客户端的 `codes.Acceptable` 把 `DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted`
6 个码记为**失败**，而 Go like-rpc 返回的 2001/5003/5012 **一个都不在那 6 个里** ⇒ 业务错误一律被记为 Accept、不参与熔断。
用 `INTERNAL` 会让「DB 查询失败 / Redis 更新失败」这类**正常业务错误**全部计成熔断失败 ⇒ **熔断误开**（那是新增行为，不是对齐）。
由 `LikeGrpcErrorTest#businessCodesAreNeverMappedToBreakerFailureCodes` 锁死。

### 净差异的实际影响面（**只有一个**）

| 组合 | 结果 |
|---|---|
| **RAG like-api → RAG like-rpc** | ✅ **完全无损**：trailer 优先读（`bizCodeFromError` 第 1 优先级）⇒ 业务码 + 文案原样还原 |
| **Go like-api → RAG like-rpc**（灰度期） | 🔴 **退化**：Go 只读 `int(st.Code())`（`likeactionlogic.go:57-64`），读不到 trailer ⇒ envelope `code` 变 **2 或 3**；`msg` 因 `GetErrMsg(2/3)` 落表外而回落 `"FAIL"` |

⇒ **灰度期的硬约束（不是代码能解决的）**：**不要让 Go like-api 调到 RAG like-rpc**。
若将来必须跨栈调用，需要显式做「服务名/业务码前缀映射」或在 Go 侧补读 trailer。

⚠️ 这条**不是**「RAG 更严/更安全」那一类（SD-2026-10-23-01/02/03）——
它是**能力缺失导致的载体迁移**，信息本身没丢，丢的只是「Go like-api 恰好认的那个载体」。

---

## SD-2026-10-23-07：Nacos 注册失败时**不退出进程**（有意偏离）

> **唯一别名**：`SD-2026-10-23-07@like`（🔴 **撞号事实**：同一编号在 [`docs/Intentional_Deviations/message/rpc.md`](../message/rpc.md)（**已撤销**的 breaker/shedding）与 [`docs/Intentional_Deviations/users/admin/rpc.md`](../users/admin/rpc.md)（gRPC description 中文）指向**三条完全不同**的偏离 ⇒ 跨域撞号 ⇒ 本条以 **`SD-2026-10-23-07@like`** 为准）
> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效，继续计入计数** —— 被签字描述的行为**今天仍存在**：Nacos 注册失败仍被 `catch` 住并**只记日志、不退出进程**
> **依据**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/bootstrap/NacosRegistration.java:90`（`catch (NacosException e)`）+ `:106`（`LOG.error("NacosRegistration.register failed for {}: {}", …)`，**之后不再 `exit`**）⇒ **证据强度：源码行号（静态）**。


| 项 | 值 |
|---|---|
| 状态 | ✅ 已签字（2026-10-23，修复轮把错误断言 H 修正后定性） |
| 组件 | `service/like/like-rpc`（`bootstrap/NacosRegistration`） |
| 性质 | **有意偏离**：Go 侧失败即退出，RAG 侧记日志继续 |

### Go 侧行为（已复核到调用点，纠正原「panic」说法）

```go
like.go:34                             s := zrpc.MustNewServer(c.RpcServerConf, …)
zrpc/server.go:23-27                   server, err := NewServer(...); logx.Must(err); return server
zrpc/server.go:41-46                   HasEtcd ⇒ internal.NewRpcPubServer(...); err ⇒ return nil, err
core/logx/logs.go:232-242              Must(err) ⇒ 打印堆栈 ⇒ if ExitOnFatal.True() { os.Exit(1) }
core/logx/vars.go:72                   ExitOnFatal = syncx.ForAtomicBool(true)   ← 默认 true
```

⇒ **进程确实退出**，但走的是 **`os.Exit(1)`**，**不是 panic**。
（`panic` 是 `ExitOnFatal=false` 时的次生路径：那时 `MustNewServer` 返回 nil server，调用方后续解引用才炸。）
⚠️ 原注释写「Go 侧 etcd 注册失败会让 `MustNewServer` panic」—— **结论对、机制错**，已改。

### 为什么有意偏离

Go 侧 gRPC server 与「必须能被调用方发现」是**绑死在同一件事**的。
RAG 侧单 JVM 里 gRPC 已起来，**为一次注册失败杀掉整个进程**会让
「Nacos 抖动 = 全站点点赞不可用」—— 比 Go 更糟。

### ⚠️ 代价（必须随本偏离一起读，不能只读「更安全」那半句）

注册失败时本进程**不会**出现在服务发现里 ⇒

- 调用方拿不到本节点，**新流量不会被打进来**；
- 只有**已建立的连接**与 in-flight 请求继续；
- 该节点实际上变成了「只能服务存量连接」的僵尸节点，需靠健康检查/重启摘除。

⇒ 这是**「可用性优先于一致性」的取舍，不是「无差异」**。

---

## 与本档其它条目的关系

| 编号 | 内容 | 与本文件 |
|---|---|---|
| SD-2026-10-23-05@like | `like.proto` 的 proto `package` 由 `pb` 归一为 `like` | 独立（见 [`proto.md`](proto.md)） |
| SD-2026-10-23-06@like | gRPC 业务码载体迁移 | 本文件 |
| SD-2026-10-23-07@like | Nacos 注册失败不退出 | 本文件 |

⚠️ `like` 域**没有** SD-2026-10-23-01/02/03（那三条属 `message` / `comment` / `like` 之外的批次，见 [`../_INDEX.md`](../_INDEX.md)）。