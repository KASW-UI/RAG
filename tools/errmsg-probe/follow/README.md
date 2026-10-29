# errmsg-probe · `follow` 域：Go ↔ Java 错误码契约双侧对拍

> AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 **follow 域错误码契约层**的落地实现。
> 定位是【回归守门】：任何人改了 follow 的码值 / 文案 / 未知码回落语义 / wire 形态 /
> 业务码反查语义，本脚本立刻 **exit 1**。

---

## 一、真源与被测

| 项 | 值 |
|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/service/follow/common/errmsg.go`（package `common`，133 行，**冻结只读**） |
| **真源 sha256** | `09a442c1363efa9072b733b11c6dff8b1fd45891cd23b14316dfd56ec3cda27c`（`run-parity.sh` 每次跑都会重新算并与复制件比对，**不一致 ⇒ exit 2**） |
| **RAG 被测模块** | `service/follow/follow-common`（mvn `-pl` 路径） |
| **RAG 被测类** | `com.rag.follow.follow.common.errmsg.FollowErrorCode`（枚举 16 码，`:42-82`）<br>`…FollowErrorMessages`（码→文案表 16 条 + `getMsg`，`:37-85`；`MSG_BY_CODE` `:44`）<br>`…FollowCodeError`（业务错误载体，`:41-79`；`newCode` `:59`、`newCodeMsg` `:64`）<br>`…FollowGrpcError`（gRPC 双向转换，`:62-304`；`grpcError` `:91`、`BIZ_CODE_BY_MSG` `:120`、`bizCodeFromError` `:156`、`resolveStatus` `:262`、`mapStatusCodeToBizCode` `:191`） |

⚠️ **真源路径以实际代码为准**：follow 域是 `service/follow/common/errmsg.go`（**没有** `errmsg/` 子目录），
与 comment 域的 `service/comment/common/errmsg/errmsg.go`（**有**子目录）形态不同。

---

## 二、怎么跑（复现命令）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-a
bash tools/errmsg-probe/follow/run-parity.sh    # ⇒ exit 0
bash tools/errmsg-probe/follow/mutate-parity.sh  # ⇒ exit 0（内部 4 条变异全部精确转红）
```

退出码：`0` = 双侧逐字节一致 · `1` = 有真漂移 · `2` = 环境问题（输入不可见 / 探针跑不起来）。

Go 真源默认**向上逐层查找**（worktree 在 `RAG.worktrees/<lane>/`，深 2 层 ⇒ 写死「同级兄弟」会指错）；
可用 `GO_REPO=/path/to/Sea-RideTheWind_golang` 覆盖。

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache（需含 grpc v1.81.1）。
**不需要** PG / Kafka / Redis / 任何容器。

---

## 三、对拍了哪几层

输出格式（两侧必须逐字节相同）：

```
CONST|<code>                                          16 条
TABLE|<code>|<msg>                                    16 条
FALLBACK|<code>|<msg>                                 18 条
CODEERR|<code>|<errText>                              16 条
WIRE|<bizCode>|<grpcCode>|<desc>                      16 条
WIREX|<grpcCode>|<bizCode>|<grpcCode>|<desc>          24 条（8 个 grpc 码 × 3 个业务码）
BIZCODE|<case>|<code>                                 23 条
OBS|<case>|<value>                                     3 条
```

| 层 | 判据 | Go 出处（**消费点**） | RAG 出处 |
|---|---|---|---|
| **① CONST** | `const` 块登记的码值集合逐条相等 | `errmsg.go:33-53` | `FollowErrorCode.java:46-82` |
| **② TABLE** | 每个码的文案逐字节相等（本域**整表英文**） | `errmsg.go:55-74` `codeMsg` + `:89-95` `GetErrMsg` | `FollowErrorMessages.java:49-67` + `:78-85` |
| **③ FALLBACK** | 未登记码 ⇒ 两侧**同样**回落到 `"FAIL"`（码值 + 文案都比） | `errmsg.go:89-95` `if !ok { return codeMsg[Error] }` | `FollowErrorMessages.java:78-85` |
| **④ CODEERR** | `NewErrCode(code)` 的 `Error()` 文本逐字节相等 | `errmsg.go:15-17` + `:19-24` | `FollowCodeError.java:53` + `:59-61` |
| **⑤ WIRE** | 每个业务码经 `GRPCError` 构造出的 wire 形态：**业务码在 description、不在 grpc-status** | `errmsg.go:97-99` `status.Error(grpcCode, GetErrMsg(bizCode))` | `FollowGrpcError.java:91-95` |
| **⑥ WIREX** | 8 个 grpc 码 × 3 个业务码的 wire 矩阵（grpc 码**原样透传** + description = 表文案） | `errmsg.go:97-99` | `FollowGrpcError.java:91-95` |
| **⑦ BIZCODE** | `BizCodeFromError` 的还原矩阵 23 例（含「文案反查优先于 gRPC 码类别」） | `errmsg.go:101-133` | `FollowGrpcError.java:156-177` + `:191-211` |
| **⑧ OBS** | 3 条**必然不同**的实现层差异（见 §五） | `errmsg.go:101-133` | `FollowGrpcError.java:156-177` |

⚠️ **gRPC 码名归一**：Go 的 `grpcodes.Code.String()` 是 CamelCase（`InvalidArgument`），
Java 的 `Status.Code.name()` 是 UPPER_SNAKE（`INVALID_ARGUMENT`）⇒ 两侧统一到
**grpc 官方 canonical 形态**（小写 snake_case，module cache
`google.golang.org/grpc@v1.81.1/codes/code_string.go` 的 `canonicalString`）才可比。

⚠️ **⑤⑥⑥⑦ 的输入是「文案反查表」的真实条目**（如 `"user not found"`），
不是手抄的期望输出 ⇒ 期望码由**真源自己算**（探针不持有任何码值常量）。

---

## 四、CASES（18 个回落样本）为什么这么选

⚠️ 原则：**必须是本域未登记、但在别的域真实存在**的码 ⇒ 才测得到「回落」这条语义。

| 样本 | 在别的域是什么 | 为什么选它 |
|---|---|---|
| `0` / `-1` / `1` / `999` | 通用边界 | 经典哨兵值 + 非业务码 |
| `1001` | comment / users：用户名已存在 | 跨域撞车（低号段） |
| `1012` / `1013` | comment：评论区不存在 / 评论不存在 | 跨域撞车，**紧邻** follow 的 1015 ⇒ 最容易「顺手登记错」 |
| `2001` | comment：输入有误 | 跨域撞车（另一个号段） |
| `3001` | users：积分余量不足 · like：构建 outbox 失败 | 跨域撞车 |
| `5009` / `5010` | comment：Kafka 消息发送失败 / 延时消息发送失败 | 跨域撞车，**紧邻** follow 的 5008 ⇒ 上边界外侧 |
| `5012` | hot / users 号段 | 号段边界 |
| `5102` | message：管理员不存在 | 跨域撞车，**紧邻** follow 的 5101 ⇒ 最容易漏 |
| `5205` / `5206` / `5207` | message：只有管理员能初始化该会话 / 首条私聊过长 / 不支持的发送者角色 | 跨域撞车，**紧邻** follow 的 5204 ⇒ 上边界外侧 |
| `9999` | 无归属的高位码 | 「随便一个未登记码」 |
| `2147483647` / `-2147483648` | int32 边界 | ⚠️ **Go 的 `int` 是 64 位、Java 的 `int` 是 32 位** ⇒ 只用两侧都放得下的值 |

⚠️ 本域已登记集合 = `{200, 500, 1015, 5001~5008, 5101, 5201~5204}` ⇒ 上表**没有一个**落在里面。

---

## 五、OBS 段（3 条 · 两侧**必然不同**的已知不可达差异）

⚠️ OBS 段的语义是「**断言差异仍然存在**」：
若哪天两侧变得一致 ⇒ 脚本 **exit 2** 提示「差异已消失，请重新评估并更新台账」。

| case | Go 值 | Java 值 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| `obs_ok_status` | `200` | `5001` | Go 的 `status.Error(codes.OK, msg)` **返回 nil**（grpc-go `internal/status/status.go`：`func (s *Status) Err() error { if s.Code() == codes.OK { return nil } }`）⇒ `BizCodeFromError(nil)` ⇒ `Success`(200)；Java 侧 `Status.fromCode(OK)...asException()` 造出来的是一个 **code=OK 的非 nil `StatusException`** ⇒ 走反查/兜底 ⇒ 5001。**「OK 状态不是错误」是 grpc-go 的 API 形态，grpc-java 没有对应的塌缩** | Go `errmsg.go:101-103`；Java `FollowGrpcError.java:157-159` |
| `obs_custom_grpcstatus` | `5101` | `1015` | grpc-go 的 `status.FromError` 认「**顶层实现了 `GRPCStatus() *Status` 的任意 error**」（module cache `google.golang.org/grpc@v1.81.1/status/status.go` 的 `type grpcstatus interface{ GRPCStatus() *Status }` 顶层断言）⇒ 文案反查命中 ⇒ 5101；Java 侧 `resolveStatus` **只认** `StatusException` / `StatusRuntimeException` 两个具体类 ⇒ 认不出 ⇒ 兜底 1015 | Go `errmsg.go:106`；Java `FollowGrpcError.java:262-295`（`statusOf` 的两个 `instanceof`） |
| `obs_wrapped_outer_msg` | `5002` | `5101` | Go 的 `fmt.Errorf("%w")` 的 `Error()` = `"外层文本: 内层文本"`，内层是 status error 时内层文本恒为 `"rpc error: code = ... desc = ..."` ⇒ **文案反查必然落空** ⇒ switch 兜底 5002；Java 的 `Throwable#getMessage()` **只有外层文本** ⇒ 反查**能命中** ⇒ 5101。**这是 Go 的 `fmt.Errorf` 文本格式 vs Java 的 `getMessage()` 的结构性差异** | Go `errmsg.go:106` + grpc-go `status/status.go` 的 `p.Message = err.Error()`；Java `FollowGrpcError.java:255-260`（**生产 javadoc 已自登记为「残留的跨语言差异」**） |

⚠️ **这三条全部是「构造出来的不可达形态」**：
follow 域的真实链路恒走 `status.FromError` 的**第①级（顶层直接命中）**分支
（`FollowGrpcError#resolveStatus` 的 javadoc `:245-253` 已核实：4 个 client interceptor + 4 个 server interceptor
全部原样透传，**没有一个**做 `fmt.Errorf("%w")` 包装）⇒ **不构成行为漂移**，但必须钉死。

---

## 六、白名单（已签字偏离）

| 编号 | 为什么它是签字偏离 | 出处 | 本探针的处置 |
|---|---|---|---|
| **（无）** | —— | —— | —— |

⚠️ **本域白名单 0 条，这是核实过的结论，不是漏写**：

- `docs/Intentional_Deviations/follow/{common,api,rpc,proto}.md` **全部为 0 字节（空文件）**
  ⇒ follow 域**无**已签字偏离（`docs/Intentional_Deviations/_INDEX.md` 的「已收编」表里 follow 域 = **0 份**，
  「待填（空文件即坑位）」表里列着 `follow/*` ⇒ 本域这一格仍是**坑位**）。
- §五 的 3 条 OBS 是**跨语言 / 平台 API 形态差异**，登记位置是
  `docs/DRIFT/follow/common.md` 的「**两条容易踩反的不对称**」第 2 条
  + `FollowGrpcError#resolveStatus` 的 javadoc（`:255-260`），
  **不是** `Intentional_Deviations` 里的签字偏离 ⇒ 走 **OBS 段**（单列 + 差异消失即 exit 2），
  **不混进**「已签字白名单」。

---

## 七、变异测试（证明本探针**不是假绿**）

```bash
bash tools/errmsg-probe/follow/mutate-parity.sh
```

⚠️ **变异只发生在临时副本上，仓内 `service/**` 与 `tools/errmsg-probe/follow/**` 一个字都没改**
（与 `tools/errmsg-probe/hot/mutate-parity.sh` 的「原地改生产代码再还原」不同 ——
原地改即使还原也留下窗口期，且本 lane 的写权不允许改 `service/**`）。

| # | 变异 | 手法 | 观测 |
|---|---|---|---|
| **M1** | **Go 侧复制件**文案漂移 | 造一个「假真源仓」（`go.mod`/`go.sum` 原样 + **变异版** `errmsg.go`），用 `GO_REPO` 指过去 | ✅ 精确转红（exit 1） |
| **M2** | **Java 侧源码副本**文案漂移 | 复制模块源码到临时目录 → 改文案 → `javac` 编副本 → `PROBE_JAVA_CLASSES` 指过去 | ✅ 精确转红（exit 1） |
| **M3** | **输入根探针**硬失败 | `GOMODCACHE` 指向空目录 | ✅ 精确转红（exit 2 · 不得静默降级） |
| **M4** | 还原后 | 真源 + 生产 classpath | ✅ 重新转绿（exit 0） |

### M1 · Go 侧复制件（实测输出）

变异内容：`"database query failed"` → `"database query failed."`

```
变异前 sha256（真源）   = 09a442c1363efa9072b733b11c6dff8b1fd45891cd23b14316dfd56ec3cda27c
变异后 sha256（副本）   = ae8589c8f736c2262006c8abd09baefd365f12e7c1ad5e7e46feb8eb9ac69012
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5007|database query failed.
+TABLE|5007|database query failed
-CODEERR|5007|ErrCode:5007, Errmsg:database query failed.
+CODEERR|5007|ErrCode:5007, Errmsg:database query failed
-WIRE|5007|internal|database query failed.
+WIRE|5007|internal|database query failed
```

### M2 · Java 侧源码副本（实测输出）

```
变异前 sha256（生产源码） = 493a2535dcf77bfe53972b8e51596ea54b0a6f40f570025fcd49bc0bb3dabd07
变异后 sha256（临时副本） = 730eb6700300c30b37750135410defc1f3fde197a50eb41b4df6f06f7970bc52
⚠️ 生产源码未被触碰（变异只发生在 <临时目录>/mut-src）
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5007|database query failed
+TABLE|5007|database query failed.
-CODEERR|5007|ErrCode:5007, Errmsg:database query failed
+CODEERR|5007|ErrCode:5007, Errmsg:database query failed.
-WIRE|5007|internal|database query failed
+WIRE|5007|internal|database query failed.
```

---

## 八、已知边界（**不覆盖**什么）

1. **只覆盖 `follow-common` 的错误码契约层**。HTTP envelope 形状、gRPC 服务名、DDL 均**不覆盖**。
2. **只覆盖「两侧同时跑」的静态对照** ⇒ 证据强度是 **L1/L2**，**不是**双侧活体对拍。
   「跑绿」应读作「**在上述证据强度下一致**」，不是「已闭环」。
   （`docs/DRIFT/follow/common.md` 的活体证据是 api 层 52/52 条 —— 那是**另一层**的证据。）
3. **OBS 段的三条是「不可达形态」**（见 §五 末段）⇒ 不构成行为漂移，但已钉死。
4. **探针不校验「RAG 生产代码是否真的调用了 `grpcError`/`bizCodeFromError`**」** ——
   它验的是**契约层**（这两个方法本身的行为），调用点覆盖度由各域台账负责。
5. **`PROBE_JAVA_CLASSES` 是变异测试专用的注入点**（`run-parity.sh` 会把它原样打印出来），
   默认走**生产编译产物** `service/follow/follow-common/target/classes`。
6. ⚠️ **本域码值与 hot / favorite 域高度重叠但含义不同**（follow 的 5001 = `"internal server error"`，
   favorite 的 5001 = `"系统内部错误"`）⇒ **跑本探针不代表那些域也对齐了**。
