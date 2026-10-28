# errmsg-probe：错误码契约的 Go ↔ Java 双侧对拍

AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在**错误码契约**这一层的落地实现。

> 凡写「与 Go 一致」，必须有两侧同时跑的探针（不是只跑 RAG）。
> 本探针验的是：**article 域的错误码值 / 文案 / 未知码回落语义**。

## 为什么需要它

**任何纯 Java 侧的契约测试，对 Go 都只能靠「人工抄录」建立联系。**
它能证明「改动 Java 时立刻变红」（回归守门），但**不能**证明「Java 等于 Go」。

⇒ 分工：**探针锚定真源，契约测试守住不回退。**

## 怎么跑

```bash
bash tools/errmsg-probe/run-parity.sh
echo $?          # 0 = 双侧逐字节一致，1 = 有漂移，2 = 环境问题
```

Go 真源默认取 RAG 的**同级兄弟目录** `../Sea-RideTheWind_golang`；可用环境变量覆盖：

```bash
GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/run-parity.sh
```

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）。
**不需要 Go module cache** —— 探针跑在临时模块里、零外部依赖 ⇒ `GOPROXY=off` 也能跑。

**它没有被挂进 `mvn test`**：需要 Go toolchain，进 CI 等于给流水线引入新依赖。
它是独立脚本，由人（或后续 agent）在改了 article 码表之后手动跑。

## 结构

| 文件 | 角色 |
|---|---|
| `go/main.go` | Go 半边：输出规范化 dump，并做 Go 侧 `const` 块 ≡ `codeMsg` 表的**自检** |
| `go/errmsg_probe.go` | **同包探针**：被复制到临时模块里与真源同包编译，因此能读**未导出**的 `codeMsg` 表 |
| `java/Probe.java` | RAG 半边：跑 **article-common 的真实编译产物 `ErrorCode`**，自己不持有任何码值/文案 |
| `run-parity.sh` | 校验真源 sha256 → 搭临时模块 → 跑两侧 → 比对 sha256 + diff → 不一致则 exit 1 |

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

- **只覆盖 article 域**。users / like / hot 域的码表各自独立（且与 article **同号不同义**），
  跑本探针**不代表**那些域也对齐了。
- **验的是码表，不是 HTTP 边界**。Go api 层把 `errmsg.GetErrMsg(code)` 写进 envelope 的 `msg` 字段、
  **HTTP 恒 200**；这一层的等价性要等 `article-api` 落地后补 MVC 管线测试才能证。
- **Go 的 rpc 侧从不取文案** —— 它只把码当 `error_code` 日志字段用（`logger.LogBusinessErr`），
  所以 rpc 日志里没有文案是**正常**的，不是查表失效。
