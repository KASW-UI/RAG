# `like` 域错误码契约探针（Go ↔ Java 双侧逐字节对拍）

> **定位：回归守门。** 任何人改了 `like` 域的**码值 / 文案 / 未知码回落语义 / 业务码载体语义**，
> 本探针立刻变红（`run-parity.sh` → exit 1）。
> ⚠️ **「跑绿」≠「对」** ⇒ 本目录另带 `mutate-parity.sh`（变异测试）证明它**不是假绿**。

---

## 1. 真源与被测对象

| 项 | 值 |
|---|---|
| Go 真源（**只读、冻结**） | `Sea-RideTheWind_golang/service/like/common/errmsg/errmsg.go` |
| 真源 sha256 | `d76ba95cc5767002b7781aab52ac88be2d07bf905c08a8fe54c0a1e334e3f8d1` |
| 真源包名 | `package errmsg`（⚠️ 与 `favorite` 版不同：那份是 `package common`） |
| 被测 RAG 类 | `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/` |
| ↳ 码表（枚举） | `LikeErrorCode.java:18-97`（29 个码；`getCode()` 在 `:95`） |
| ↳ 文案表 | `LikeErrorMessages.java:37-74`（`MSG_BY_CODE` 静态块）；`getMsg(int)` 在 `:82-88` |
| ↳ wire 载体 | `LikeGrpcError.java:138-144`（`grpcError`）、`:170-200`（`bizCodeFromError`）、`:110-115`（`mapToGrpcCode`）、`:250-258`（`extractStatus`） |
| ↳ 错误载体 | `CodeError.java:33-48`（`Error()` 格式 = Go `errmsg.go:15-17`） |
| RAG 模块（`mvn -pl`） | `service/like/like-common` |
| 探针半边 | `go/errmsg_probe.go` + `go/main.go`（Go）· `java/Probe.java`（Java） |

### Go 出处 ↔ RAG 出处（语义等价论证，AGENTS.md §3.2）

| 层 | Go 出处 | RAG 出处 | 等价性怎么被证明 |
|---|---|---|---|
| 常量表 | `errmsg.go:37-67`（`const` 块，29 个码） | `LikeErrorCode.java:18-97` | 探针**同包编译**真源并现算 const 块的码集合 ⇒ 与枚举逐条比对（`CONST|`，升序） |
| 码→文案 | `errmsg.go:69-99`（`var codeMsg`）+ `:101-107`（`GetErrMsg`） | `LikeErrorMessages.java:37-74` + `:82-88` | `TABLE|`，29 条中文文案**逐字节**比对（UTF-8） |
| 未知码回落 | `errmsg.go:101-107`（未登记 ⇒ `codeMsg[Error]` = `"FAIL"`） | `LikeErrorMessages.java:82-88`（未登记 ⇒ `MSG_BY_CODE.get(ERROR)` = `"FAIL"`） | `FALLBACK|`，22 个跨域撞车码**码值 + 文案**一起比 |
| 文案载体 | `errmsg.go:33-35`（`NewGrpcErr` ⇒ 文案进 description） | `LikeGrpcError.java:138-144`（`grpcError` ⇒ 文案进 description） | `WIRE|`，29 条 description 逐字节比对 |
| 业务码载体 | `errmsg.go:33-35`（业务码进 **grpc-status 数值**） | `LikeGrpcError.java:138-144`（业务码进 **自定义 trailer**） | ⚠️ **两侧必然不同** ⇒ `WIRECODE|` 走**白名单**（§4） |
| 业务码还原 | `service/like/api/internal/logic/like/likeactionlogic.go:63-68`（`status.FromError` ⇒ `int(st.Code())`；否则 5001） | `LikeGrpcError.java:170-200`（`bizCodeFromError`） | `BIZCODE|`，本域自己的错误 5 条**必须还原出同一业务码**；非本域错误 5 条两侧形态本就不同 ⇒ 白名单（§4） |

---

## 2. 对拍了哪几层

探针两侧各输出一份**规范化 dump**，脚本逐字节 `diff`：

```
CONST|<code>                  29 行 —— 码值集合（升序）
TABLE|<code>|<msg>            29 行 —— 码→文案（中文逐字节）
FALLBACK|<code>|<msg>         22 行 —— 未知码回落（码值 + 文案）
WIRE|<bizCode>|<description>  29 行 —— 文案载体（description 逐字节）
WIRECODE|<bizCode>|<carrier>  29 行 —— 业务码载体（**白名单豁免**）
BIZCODE|<case>|<code>         10 行 —— 业务码还原矩阵（5 条豁免）
OBS|<case>|<value>             3 行 —— 两侧必然不同的语义差异（§3）
```

主比对流（`CONST + TABLE + FALLBACK + WIRE + BIZCODE`）= **114 行**
（29 CONST + 29 TABLE + 22 FALLBACK + 29 WIRE + 5 BIZCODE；`WIRECODE` 29 条与 `BIZCODE` 的 5 条 `foreign_*` 被白名单豁免），
双侧 sha256 相同 ⇒ `16b66a970afdcee473e1f8b524271fb2d3bcb7b4c35d837f33000808793ea107`。

⚠️ **探针自己不持有任何码值/文案常量** —— Go 半边只调 `GetErrMsg` / `NewGrpcErr`，
Java 半边只调 `LikeErrorCode` / `LikeErrorMessages` / `LikeGrpcError`
⇒ 不会出现「拿 RAG 的值验 RAG 的值」的必然假绿（AGENTS.md §3.3）。

---

## 3. OBS 段（两侧**必然不同**的已知不可达差异）

OBS 段**不参与**逐字节对拍，但脚本会**断言差异仍然存在**：
每条 case 的 Go 值 ≠ Java 值（否则 ⇒ 差异消失 ⇒ exit 2 逼人更新台账），
且两侧整段的 sha256 被**冻结**（差异**变形** ⇒ 同样 exit 2）。

| case | Go 值 | Java 值 | 为什么两侧必然不同 |
|---|---|---|---|
| `obs_nil_error` | `5001` | `200` | Go like-api 的还原步骤在 `if err != nil` **守卫之内**（`likeactionlogic.go:63-68`）⇒ nil 走不到 `status.FromError` ⇒ 落到兜底码 `ErrorServerCommon`(5001)；RAG 的 `bizCodeFromError(null)` 显式返回 `SUCCESS`(200)（`LikeGrpcError.java:170-173`，防御性取值）。⇒ 该分支在 Go 侧**不可达**，差异不构成行为漂移。 |
| `obs_ok_status` | `5001` | `1015` | Go 的 `status.Error(codes.OK, msg)` **返回 nil**（grpc-go 行为）⇒ 与 nil 同形 ⇒ 兜底 5001；RAG 侧 `Status.fromCode(OK).asException()` 得到一个 `code=OK` 的 `StatusException`（非 nil）⇒ 走反查/兜底 ⇒ `CODE_SERVER_BUSY`(1015)。⚠️ Java 半边必须用 `asException()`（`asRuntimeException()` 对 OK 会抛 `IllegalStateException`）。 |
| `obs_custom_grpcstatus` | `13` | `1015` | Go 的 `status.FromError` 认「顶层实现 `GRPCStatus()`」的错误 ⇒ ok=true ⇒ 还原出原始 gRPC 码 `INTERNAL`=13；RAG 的 `extractStatus` 只认 `StatusException` / `StatusRuntimeException` 两个具体类（`LikeGrpcError.java:250-258`）⇒ 认不出 ⇒ 兜底 1015。 |

> 冻结指纹：`Go OBS sha256 = 32912e876a4a7f0b02afc6536a3e42d5fb730d89c5a15f1b6f7264c1bd47bc3a` ·
> `Java OBS sha256 = 11b9bbb62707392ffaf84523397bc8df18d90b035b64198eb61dcc9310f071a0`。

---

## 4. 白名单（**逐条**豁免，每条带类别 + 理由 + 出处）

白名单是**假绿源** ⇒ 脚本对每条豁免做三重自检：
① 该 key 两侧都必须命中记录（写错 key ⇒ exit 2）；
② 两侧命中的记录集合必须一致；
③ **差异必须仍然存在**（差异消失 ⇒ 豁免失效 ⇒ exit 2 ⇒ 逼人更新台账，而不是让它静默漂移）。

| # | 豁免 key | 类别 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| 1 | `WIRECODE\|*`（29 条） | **SIGNED**（已签字·框架限制） | **gRPC 业务码载体迁移**：Go 的 `codes.Code` 是 `int32` 命名类型、**不做范围校验** ⇒ 业务码以**数值**形式进 `grpc-status`（线上真实字节 `grpc-status: 5003`）；grpc-java 的 `Status.Code` 只有 17 个成员（合法值 0~16）、`Status.fromCode(int)` 公开 API 不存在 ⇒ **无法构造越界码** ⇒ RAG 把业务码迁到自定义 trailer `rag-like-biz-code`，文案仍逐字留在 description。⇒ **业务码与文案对客户端仍然无损**，丢的只是「Go 恰好认的那个载体」。 | Go `service/like/common/errmsg/errmsg.go:33-35`；RAG `LikeGrpcError.java:78-82`（trailer key）与 `:138-144`；签字档 [`docs/Intentional_Deviations/like/rpc.md`](../../../docs/Intentional_Deviations/like/rpc.md) 的 **SD-2026-10-23-06** |
| 2 | `BIZCODE\|foreign_internal` | DRIFT-REGISTERED（已登记·待拍板） | 下游发来的、**不是本域业务码**的错误：Go like-api 直接把原始 gRPC 码当业务码（`INTERNAL` ⇒ **13**）；RAG 的 `mapGrpcCodeToBizCode` 把 `INTERNAL` 落 `default` ⇒ **1015**。 | [`docs/DRIFT/like/vs-common.md`](../../../docs/DRIFT/like/vs-common.md) 的 **D-7** |
| 3 | `BIZCODE\|foreign_notfound` | DRIFT-REGISTERED | 同上：Go ⇒ **5**（`NOT_FOUND`），RAG ⇒ **1015**。 | 同上（D-7） |
| 4 | `BIZCODE\|foreign_invalid_argument` | DRIFT-REGISTERED | 同上：Go ⇒ **3**，RAG 的 `mapGrpcCodeToBizCode` 映到 **2001**。 | 同上（D-7） |
| 5 | `BIZCODE\|foreign_unauthenticated` | DRIFT-REGISTERED | 同上：Go ⇒ **16**，RAG 映到 **1009**。 | 同上（D-7） |
| 6 | `BIZCODE\|foreign_plain` | DRIFT-REGISTERED | **非 status** 错误（Go 侧还包含 go-zero 熔断打开的 `breaker.ErrServiceUnavailable`）：Go 兜底 `errmsg.ErrorServerCommon` = **5001**；RAG 的 `bizCodeFromError` 末尾兜底 `CODE_SERVER_BUSY` = **1015**。 | 同上（D-7） |

> ⚠️ **本域没有「整域豁免」**：6 条豁免逐条对应具体记录 / 具体 case，理由与出处各自不同。
> ⚠️ **#2~#6 是「已登记未拍板」的差异（DRIFT），不是已签字偏离** —— 类别列写明了这一点，
> 免得后来人把它们当成「已批准的不一致」。

---

## 5. CASES 清单（22 个未登记码）为什么这么选

`FALLBACK|` 层测的是**未知码回落语义**（Go `errmsg.go:101-107` ⇒ `"FAIL"`）。
样本来源是**跨域撞车码** —— 这些码值在别的域是**真实存在的业务码**，但在 `like` 域**没有登记**
⇒ 两侧都必须回落到 `"FAIL"`：

| 样本 | 来源域 | 在源域的语义 | 为什么有价值 |
|---|---|---|---|
| `1012` / `1013` | `users`(`ErrorRedisUpdate`) / `comment`(`ErrorCommentNotExist`) | 真实业务码 | **近邻码**：`users` 有 1012、`like` 没有 ⇒ 只差一位，最容易被人「顺手补上」 |
| `1016` / `1017` / `1018` | `users`（邀请码 / 请求参数 / 头像历史） | 真实业务码 | `users` 域的用户类码段 |
| `2002` | `like` 隔壁的 `like` 自身 2002 之外的用户域码 | `like` 登记了 2001/2002 | ⚠️ `2002` 在 `users` 域**未登记**（`users` 只到 2001 之前的号段）⇒ 是跨域撞车样本 |
| `3002` / `3003` | `users`（积分重试超限 / 积分超时） | 真实业务码 | `like` 登记了 3001（Outbox）但**没有** 3002/3003 ⇒ 号段相邻 |
| `5101`~`5103` | `favorite` / `follow` / `message`（用户不存在 / 已存在 / 密码错） | 真实业务码 | 5xxx 段的跨域撞车 |
| `5201` / `5203` / `5204` | `favorite` / `follow` / `message` | 真实业务码 | 5xxx 段 |
| `5301` / `5304` | `favorite`（收藏记录不存在 / 收藏暂不可撤回） | 真实业务码 | 5xxx 段 |
| `0` / `-1` / `1` / `999` | 人工构造 | — | 边界/平凡值：最容易漏的回落分支 |
| `2147483647` / `-2147483648` | 人工构造 | — | **int32 边界**：Go 的 `int` 是 64 位、Java 的 `int` 是 32 位 ⇒ 只取两侧都放得下的值（`-2147483648` 在 Go 里是 `int`、在 Java 里是 `int` 最小值，两侧都能装下） |

> ⚠️ **不能用本域已登记的码当回落样本**（`like` 的 2001/2002/3001/5011/5012 等已登记 ⇒ 测的是「已登记码」而不是「回落语义」）。

---

## 6. 变异测试（证明**不是假绿**）

`mutate-parity.sh` 把 **RAG 侧**的类**复制到临时目录** ⇒ 在**副本**上改坏 ⇒ 编译成「影子类」目录
⇒ **前置**到探针的 classpath ⇒ 跑对拍 ⇒ 必须**精确转红**（exit 1）。

> ⚠️ 为什么用影子类而不是直接改仓内源码（`hot` 版 `mutate-parity.sh` 的做法）：
> ① 本 lane 的写权**不含** `service/**`（生产代码一个字都不能动）；② 变异窗口内仓里是坏代码，
> 进程被杀就留下残留；③ 影子类只在临时目录存在 ⇒ **仓内零残留**，可反复跑、可并发跑。
> 影子类与真实类**同 FQN** ⇒ classpath 前置即覆盖 ⇒ 变异对象仍然是「RAG 侧实现」。

### 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-b
bash tools/errmsg-probe/like/mutate-parity.sh ; echo "EXIT=$?"
```

### 实测输出（2026-10-29，exit 1 = 变异测试通过）

```
== 基线（未变异）必须转绿 ==
   ✅ 基线绿（exit 0）
   classpath 已取（长度 2579）

== M1 文案漂移：「查询数据库失败」→「查询数据库失败（变异）」==
   ✅ 精确转红（exit 1），红色输出明确指出「查询数据库失败（变异）」
      --- 红色输出关键行 ---
      | 21:+TABLE|5003|查询数据库失败（变异）
      | 30:+WIRE|5003|查询数据库失败（变异）

== M2 码值漂移：ERROR_DB_SELECT(5003) → (5013) ==
   ✅ 精确转红（exit 1），红色输出明确指出「5013」
      --- 红色输出关键行 ---
      | 28:+CONST|5013
      | 44:+TABLE|5013|查询数据库失败
      | 60:+WIRE|5013|查询数据库失败

== M3 回落语义漂移：未知码回落 "FAIL" → "" ==
   ✅ 精确转红（exit 1），红色输出明确指出「-FALLBACK|0|FAIL」
      --- 红色输出关键行 ---
      | 20:-FALLBACK|0|FAIL

✅ 三条变异全部精确转红 ⇒ 探针非假绿（本脚本按约定以 exit 1 表示『变异测试通过』）
```

> ⚠️ **退出码约定（与仓内 `hot` 版不同，按本轮任务书要求）**：
> `mutate-parity.sh` **exit 1 = 全部变异精确转红（测试通过）**；exit 2 = 基线不绿 / 有变异没转红（守门是假绿）/ 环境问题。
> ⇒ **跑 `mutate-parity.sh` 时不要把 exit 1 当成失败。**

### 变异前后的 sha256

| 变异 | 变异前（Go/Java 主比对流 sha256） | 变异后 |
|---|---|---|
| M1 文案 | `16b66a970afdcee473e1f8b524271fb2d3bcb7b4c35d837f33000808793ea107`（两侧同） | 两侧不等 ⇒ diff 出 `TABLE\|5003` 与 `WIRE\|5003` 两行 |
| M2 码值 | 同上 | 两侧不等 ⇒ diff 出 `CONST\|5013` / `TABLE\|5013` / `WIRE\|5013` |
| M3 回落 | 同上 | 两侧不等 ⇒ 22 条 `FALLBACK\|` 全部不同 |

（脚本每次都会打印当次 Go / Java 两侧的 sha256 —— 上表左列是基线值，右列是「两侧不等」的定性结论；
变异后的具体 sha256 每次运行都不同，因为临时目录路径会进 diff 的文件头。）

---

## 7. 复现命令（对拍本体）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-b
bash tools/errmsg-probe/like/run-parity.sh ; echo "EXIT=$?"
```

实测（2026-10-29）：

```
   真源 sha256 = d76ba95cc5767002b7781aab52ac88be2d07bf905c08a8fe54c0a1e334e3f8d1  ✓ 与复制件一致
   真源 go.mod 的 grpc 版本 = v1.81.1
   module cache 输入根探针通过（.../cache/download/google.golang.org/grpc/@v/v1.81.1.mod / .../grpc@v1.81.1/codes/codes.go）
...
   Go   sha256 = 16b66a970afdcee473e1f8b524271fb2d3bcb7b4c35d837f33000808793ea107
   Java sha256 = 16b66a970afdcee473e1f8b524271fb2d3bcb7b4c35d837f33000808793ea107
   行数 = 114（CONST + TABLE + FALLBACK + WIRE + BIZCODE）
...
✅ 双侧逐字节一致（sha256 相同，diff 无输出）；白名单 6 条豁免项差异仍在；OBS 段 obs_custom_grpcstatus obs_nil_error obs_ok_status 差异仍在登记状态
```

**依赖**：`mvn`（离线 `-o`）、JDK 21+、Go toolchain ≥1.25、本地 Go module cache（需含 `google.golang.org/grpc@v1.81.1`）。
**不需要** PG / Kafka / Redis / 任何容器。
**环境变量**：`GO_REPO=<真源路径>`（默认向上 4 层搜索 `Sea-RideTheWind_golang`）。

---

## 8. 本探针**没有**覆盖的部分（写明以免被当成「全对」）

| 未覆盖面 | 为什么 | 出处 |
|---|---|---|
| `CodeError` 的 JSON 序列化 / `Error()` 字符串格式 | 仓内已有 `LikeErrorContractTest` 逐字节断言（抄写期望值那侧）；本探针测的是**两侧现算现比**，两者互补、不重复 | `service/like/like-common/src/test/java/.../LikeErrorContractTest.java` |
| HTTP envelope 形状（`code`/`msg` 字段名、状态码选择） | 属 **api 组件**的契约，不是 errmsg 契约 | — |
| `like-api` 的 HTTP 路径 / 方法 / Content-Type | 同上 | — |
| proto `package` 归一（`pb.LikeService` ⇒ `like.LikeService`） | **proto 层**偏离，与错误码无关 | `docs/Intentional_Deviations/like/proto.md`（SD-2026-10-23-05） |
| Go 侧 rpc 里**硬编码**的 description（如 `NewGrpcErr(ErrorDbSelect, "DB查询失败")`） | 那不是码表文案（码表里是 `"查询数据库失败"`）⇒ 本探针的 `WIRE|` 层用**码表文案**作为输入；硬编码文案属 rpc 组件的契约 | `service/like/rpc/internal/logic/like/*.go` |
