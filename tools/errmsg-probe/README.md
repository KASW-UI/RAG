# errmsg-probe：错误码契约的 Go ↔ Java 双侧对拍

AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在**错误码契约**这一层的落地实现。

> 凡写「与 Go 一致」，必须有两侧同时跑的探针（不是只跑 RAG）。

## 域索引（**一域一目录** · 口径见 `docs/Worktree.md` §T-0.6）

| 域 | 探针目录 | Go 真源 | RAG 被测侧 | 本目录自己的 README |
|---|---|---|---|---|
| **article** | `run-parity.sh`（**根级件** · 历史目录） | `service/article/common/errmsg.go` | `article-common` 的 `ErrorCode` | 本文件（下方正文即它的说明）|
| **favorite** | `favorite/` | `service/favorite/common/errmsg.go` | `favorite-common` 的 errmsg 三件套 | [`favorite/README.md`](favorite/README.md) |
| **hot** | `hot/` | `service/hot/common/errmsg.go` | `hot-common` 的 errmsg 两件套 | [`hot/README.md`](hot/README.md) |
| **like** | `like/` | `service/like/common/errmsg/errmsg.go` | `like-common` 的 errmsg | [`like/README.md`](like/README.md) |
| **users** | `users/` | `service/user/common/errmsg/errmsg.go`（⚠️ Go 侧是 `user` 不是 `users`）| `users-common` 的 `ErrorCode` | [`users/README.md`](users/README.md) |
| **comment** | `comment/` | `service/comment/common/errmsg/errmsg.go` | `comment-common` 的 errmsg | [`comment/README.md`](comment/README.md) |
| **follow** | `follow/` | `service/follow/common/errmsg.go` | `follow-common` 的 errmsg | [`follow/README.md`](follow/README.md) |
| **message** | `message/` | `service/message/common/errmsg.go` | `message-common` 的 errmsg | [`message/README.md`](message/README.md) |
| **task** | `task/` | `service/task/rpc/internal/logic/get_task_logic.go`（⚠️ **本域无 `-common`、无码值表** ⇒ 比的是 **gRPC 码 + description 文案**）| `task-rpc` 的 `TaskServiceGrpcImpl` / `TaskDomainService` | 见目录内 README |

⚠️ **各域的层次数 / case 数 / 白名单条数 / OBS 条数** ⇒ **看该域自己的 README**，**本表不复制**（复制就会漂）。
⚠️ **各域真源路径**形如 `errmsg.go` vs `errmsg/errmsg.go`（**有无子目录**）**逐域不同** ⇒ 以各域 README / 脚本头部为准。

## 🔴 退出码约定：**两套并存**，别把「1」一律当失败

| 脚本族 | `run-parity.sh` | `mutate-parity.sh` |
|---|---|---|
| **article（根级）** | `0` = 双侧逐字节一致 · `1` = 有漂移 · `2` = 环境问题 | ——（根级件**无** mutate 脚本）|
| **hot / comment / follow / message** | `0` = 双侧一致 · `1` = 有漂移 · `2` = 环境问题 | **`0` = 全部变异精确转红（= 测试通过）** · `1` = 有变异没转红（守门失效）· `2` = 环境问题 |
| **like / users** | `0` = 双侧一致 · `1` = 有漂移 · `2` = 环境问题 | **`1` = 变异全部精确转红（= 测试通过）** · `2` = 守门失效 / 环境问题 |

⇒ **跑 `mutate-parity.sh` 之前先看该脚本头部的「退出码」段** ⇒ 把 `1` 当失败会误判。

## 为什么需要它

**任何纯 Java 侧的契约测试，对 Go 都只能靠「人工抄录」建立联系。**
它能证明「改动 Java 时立刻变红」（回归守门），但**不能**证明「Java 等于 Go」。

⇒ 分工：**探针锚定真源，契约测试守住不回退。**

## 怎么跑（article 根级件）

```bash
bash tools/errmsg-probe/run-parity.sh
echo $?          # 0 = 双侧逐字节一致，1 = 有漂移，2 = 环境问题
```

**其余各域** ⇒ 进对应目录跑同名脚本，例：

```bash
bash tools/errmsg-probe/message/run-parity.sh
bash tools/errmsg-probe/message/mutate-parity.sh   # 变异必红
```

Go 真源默认取 RAG 的**同级兄弟目录** `../Sea-RideTheWind_golang`；可用环境变量覆盖：

```bash
GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/run-parity.sh
```

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）。
**不需要 Go module cache** —— 探针跑在临时模块里、零外部依赖 ⇒ `GOPROXY=off` 也能跑。

**它没有被挂进 `mvn test`**：需要 Go toolchain，进 CI 等于给流水线引入新依赖。
它是独立脚本，由人（或后续 agent）在改了**对应域**的码表之后手动跑。

## 结构（**根级 article 域**；其余各域同名文件**同构**，语义见各域 README）

| 文件 | 角色 |
|---|---|
| `go/main.go` | Go 半边：输出规范化 dump，并做 Go 侧 `const` 块 ≡ `codeMsg` 表的**自检** |
| `go/errmsg_probe.go` | **同包探针**：被复制到临时模块里与真源同包编译，因此能读**未导出**的 `codeMsg` 表 |
| `java/Probe.java` | RAG 半边：跑 **article-common 的真实编译产物 `ErrorCode`**，自己不持有任何码值/文案 |
| `run-parity.sh` | 校验真源 sha256 → 搭临时模块 → 跑两侧 → 比对 sha256 + diff → 不一致则 exit 1 |
| `mutate-parity.sh`（**各域目录**）| **变异必红**：改坏被测对象 ⇒ 探针必须**精确指出那一条变了** ⇒ 还原后重新转绿 |

## Go 半边为什么是「复制真源」而不是「import 真源」

1. Go 侧「真源不接受写入」⇒ 探针不能进 Go 仓；
2. 跨包 import 拿不到未导出的 `codeMsg` ⇒ 只能验 `GetErrMsg` 的输出，
   验不到「表里有没有多余 / 缺失的条目」；
3. 临时模块零外部依赖 ⇒ 不需要 module cache、不需要联网。

**代价与对策**：`errmsg.go` 只有 45 行、零 import、无任何状态，
「逐字节复制到临时模块再编译」与「在原模块里编译」产出完全相同的语义。
`run-parity.sh` 用 **sha256 校验复制件与真源逐字节相同**，不匹配直接 exit 2。

## 输出格式（两侧必须逐字节相同）

```
CONST|<code>              码值集合（升序）
TABLE|<code>|<msg>        码 → 文案（升序），取值走对外函数而非直接读表
FALLBACK|<code>|<msg>     未登记码的回落结果（顺序 = cases 文件顺序）
```

article 域当前 **49 行**：`CONST 15 + TABLE 15 + FALLBACK 19`，
双侧 sha256 均为 `cad5cb4f064ddc91829a81bafa3999da066f1dd589c353d4fbaf63f4084d1134`。

## FALLBACK 的 19 个样本为什么这么选

⚠️ 走的是**跨域撞车**样本：这些码值在 users / like 域是真实存在的业务码，
但在 article 域**没有登记** ⇒ 两侧都必须回落到 `"FAIL"`。

```
1016/1017/1018  users: 管理员邀请码错误 / 请求参数错误 / 头像历史不存在
2001/2002        like:  输入有误 / 类型转换错误
3001             users:  积分余量不足   · like: 构建 Outbox 消息载荷失败
5007-5012        users:  JSON 序列化 / 反序列化 / Kafka / 延时消息 / Redis
```

⚠️ **int 边界值只能取两侧都放得下的**：Go 的 `int` 是 64 位、Java 的 `int` 是 32 位，
跨语言比较必须用 32 位范围内的值，否则比的不是同一个输入。

## 已知边界

- ⚠️ **根级 `run-parity.sh` 只覆盖 article 域**。**要验其它域 ⇒ 跑该域自己的脚本**（见上方「域索引」）。
  各域码表彼此独立（且存在**同号不同义**）⇒ 跑通一域**不代表**其它域也对齐了。
- **验的是码表，不是 HTTP 边界**。Go api 层把 `errmsg.GetErrMsg(code)` 写进 envelope 的 `msg` 字段、
  **HTTP 恒 200**；这一层的等价性要等 `article-api` 落地后补 MVC 管线测试才能证。
- **Go 的 rpc 侧从不取文案** —— 它只把码当 `error_code` 日志字段用（`logger.LogBusinessErr`），
  所以 rpc 日志里没有文案是**正常**的，不是查表失效。
- **OBS 段 ≠ 漂移**：两侧**必然不同**的平台形态差异（如 grpc-go 的 `status.Error(OK,…)` 返回 nil、
  Go 把业务码写进 `grpc-status` 数值而 grpc-java 只能发 17 个标准码）登记在 **OBS 段**，
  断言的是「**差异仍然存在**」⇒ 哪天差异消失了反而 `exit 2`（提示该更新台账）。
  **已签字偏离**才进**白名单**；**未签字的差异不得塞进白名单**。
