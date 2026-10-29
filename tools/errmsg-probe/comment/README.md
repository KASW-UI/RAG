# errmsg-probe · `comment` 域：Go ↔ Java 错误码契约双侧对拍

> AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 **comment 域错误码契约层**的落地实现。
> 定位是【回归守门】：任何人改了 comment 的码值 / 文案 / 未知码回落语义 / wire 形态，
> 本脚本立刻 **exit 1**。

---

## 一、真源与被测

| 项 | 值 |
|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/service/comment/common/errmsg/errmsg.go`（package `errmsg`，103 行，**冻结只读**） |
| **真源 sha256** | `9820df5a62a2d6cd8823830fa0d000e51e147b8e8b6bf8874a4c862b5ff95ae0`（`run-parity.sh` 每次跑都会重新算并与复制件比对，**不一致 ⇒ exit 2**） |
| **RAG 被测模块** | `service/comment/comment-common`（mvn `-pl` 路径） |
| **RAG 被测类** | `com.rag.comment.comment.common.errmsg.CommentErrorCode`（枚举 27 码，`:29-104`）<br>`…CommentErrorMessages`（码→文案表 27 条 + `getMsg`，`:31-89`）<br>`…CommentCodeError`（业务错误载体，`:48-100`；`newCode` `:66`、`newCodeMsg` `:71`、`findIn` `:83`）<br>`…CommentGrpcError`（gRPC 出口，`:59-259`；`BIZ_CODE_TRAILER_KEY` `:72`、`mapToGrpcCode` `:116`、`grpcError` `:139`、`bizCodeFromError` `:194`） |

⚠️ **真源路径以实际代码为准**：本域是 `service/comment/common/errmsg/errmsg.go`（**有** `errmsg/` 子目录），
与 follow / message 域的 `service/<域>/common/errmsg.go`（**没有**子目录）形态不同。

---

## 二、怎么跑（复现命令）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-a
bash tools/errmsg-probe/comment/run-parity.sh    # ⇒ exit 0
bash tools/errmsg-probe/comment/mutate-parity.sh  # ⇒ exit 0（内部 4 条变异全部精确转红）
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
CONST|<code>                                27 条
TABLE|<code>|<msg>                          27 条
FALLBACK|<code>|<msg>                       19 条
CODEERR|<code>|<errText>                    27 条
CODEERRMSG|<code>|<msg>|<errText>           18 条
WIRE|<bizCode>|<desc>                       18 条
OBS|<case>|<bizCode>|<wireGrpcStatusValue>|<wireGrpcStatusName>|<desc>|<recoveredBizCode>   3 条
```

| 层 | 判据 | Go 出处（**消费点**） | RAG 出处 |
|---|---|---|---|
| **① CONST** | `const` 块登记的码值集合逐条相等 | `errmsg.go:37-65` | `CommentErrorCode.java:33-104` |
| **② TABLE** | 每个码的文案逐字节相等（**中文逐字节**） | `errmsg.go:67-95` `codeMsg` + `:97-103` `GetErrMsg` | `CommentErrorMessages.java:43-74` + `:83-89` `getMsg` |
| **③ FALLBACK** | 未登记码 ⇒ 两侧**同样**回落到 `"FAIL"`（码值 + 文案都比） | `errmsg.go:97-103` `if !ok { return codeMsg[Error] }` | `CommentErrorMessages.java:83-89` |
| **④ CODEERR** | `NewErrCode(code)` 的 `Error()` 文本逐字节相等（运维 grep 依赖） | `errmsg.go:15-17` `fmt.Sprintf("ErrCode:%d, Errmsg:%s", …)` + `:19-24` | `CommentCodeError.java:60` + `:66-68` |
| **⑤ CODEERRMSG** | `NewErrCodeMsg(code, msg)` **用传入值、不查表** | `errmsg.go:26-31` | `CommentCodeError.java:71-73` |
| **⑥ WIRE** | wire 上 **description 的文案**逐字节相等 | `errmsg.go:33-35` `NewGrpcErr` → `status.Error(codes.Code(code), msg)` | `CommentGrpcError.java:139-145` `grpcError` |
| **⑦ OBS** | 业务码的 **wire 载体**（见 §五） | `errmsg.go:33-35` | `CommentGrpcError.java:72` / `:116-121` |

⚠️ **⑤⑥ 的输入用的是 Go comment-rpc 的 18 个「生产调用点」真实文案**，不是表里的理想值。
例：`ErrorDbSelect` 的**表**文案是「查询数据库失败」（`errmsg.go:73`），而 rpc 层传的是「**DB查询失败**」
（`create_comment_logic.go:89`）⇒ 两侧都保留了这个差值（`docs/DRIFT/comment/common.md` §2.2 有登记）。
⇒ 这两层验的是**线上真实链路**上的「码 → 文案」组合，而不是查表结果。

---

## 四、CASES（19 个回落样本）为什么这么选

⚠️ 原则：**必须是本域未登记、但在别的域真实存在**的码 ⇒ 才测得到「回落」这条语义
（拿本域已登记的码当样本 ⇒ 测的是「已登记码」，白测）。

| 样本 | 在别的域是什么 | 为什么选它 |
|---|---|---|
| `0` / `-1` / `1` / `999` | 通用边界 | 经典哨兵值 + 非业务码 |
| `1014` | comment 域**自己**的相邻空位（1012/1013 已登记） | 本域内部的边界样本：域内相邻码最容易漏登记 |
| `1016` / `1017` / `1018` | users：管理员邀请码错误 / 请求参数错误 / 头像历史不存在 | 跨域撞车，且**紧邻**本域的 1011~1013 ⇒ 最容易「顺手登记错」 |
| `2002` | like：类型转换错误 | 跨域撞车（与本域 2001 相邻） |
| `3001` | users：积分余量不足 · like：构建 outbox 失败 | 跨域撞车（另一号段） |
| `5000` | hot 域通用错误码的起点 | 号段边界样本（本域 5001 起 ⇒ 5000 必须回落） |
| `5011` / `5012` | comment 只登记到 5010 | 本域**上边界**外侧 ⇒ 回落 |
| `5101` | follow / message：用户不存在 | 跨域撞车（另一号段） |
| `5201` | follow：自关系 · message：自会话 | 跨域撞车 |
| `5204` | favorite / follow：拉黑关系不存在 | 跨域撞车 |
| `5301` | favorite：收藏记录不存在 | 跨域撞车 |
| `9999` | 无归属的高位码 | 「随便一个未登记码」 |
| `2147483647` / `-2147483648` | int32 边界 | ⚠️ **Go 的 `int` 是 64 位、Java 的 `int` 是 32 位** ⇒ 只用两侧都放得下的值，否则比的不是同一个输入 |

⚠️ 本域已登记集合 = `{200, 500, 1015, 5001~5010, 1001~1013, 2001}` ⇒ 上表**没有一个**落在里面。

---

## 五、OBS 段（3 条 · 两侧**必然不同**的已知不可达差异）

⚠️ OBS 段的语义是「**断言差异仍然存在**」：
若哪天两侧变得一致 ⇒ 脚本 **exit 2** 提示「差异已消失，请重新评估并更新台账」——
**逼人更新，而不是让它静默漂移**。

| case | Go 值 | Java 值 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| `obs_wire_grpcstatus_1012` | `1012\|1012\|code(1012)\|评论区不存在\|1012` | `1012\|2\|unknown\|评论区不存在\|1012` | Go 把业务码**直接写进 grpc-status**（`codes.Code(1012)`）⇒ 线上真实字节是 `grpc-status: 1012`；grpc-java **只能发 17 个标准码（0-16）**，公开 API 里没有任何构造任意码值的入口 ⇒ 业务码被挪到**自定义 trailer** `rag-comment-biz-code` | Go `errmsg.go:33-35`；Java `CommentGrpcError.java:32-57`（自记为平台强制偏离）+ `:72` + `:116-121` |
| `obs_wire_grpcstatus_2001` | `2001\|2001\|code(2001)\|目标类型和ID不能为空\|2001` | `2001\|3\|invalid_argument\|目标类型和ID不能为空\|2001` | 同上；`mapToGrpcCode` 把 2001 映成 `INVALID_ARGUMENT(3)`、其余映成 `UNKNOWN(2)` | 同上 + `CommentGrpcError.java:116-121` |
| `obs_wire_grpcstatus_5003` | `5003\|5003\|code(5003)\|DB查询失败\|5003` | `5003\|2\|unknown\|DB查询失败\|5003` | 同上 | 同上 |

⚠️ **每条 OBS 的第 1 / 5 / 6 格两侧相同、第 4 格不同** ⇒ 差异被**限制在「grpc-status 字段的数值」这一格**：
业务码（1012 / 2001 / 5003）与文案都**两侧无损**（Java 侧从 trailer 取回业务码）。
⇒ 这正是台账 `docs/DRIFT/comment/common.md` **§3-D1** 登记的那一条。

⚠️ **Go 侧那两格是怎么算出来的**：
`int(st.Code())` —— 因为 Go comment-api 的消费点就是这么写的
（`service/comment/api/internal/logic/comment/createcommentlogic.go:52-55` 等 6 处：
`st, ok := status.FromError(err); if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }`）。
⇒ 探针复刻的是**消费点**，不是探针自己发明的规则。

---

## 六、白名单（已签字偏离）

| 编号 | 为什么它是签字偏离 | 出处 | 本探针的处置 |
|---|---|---|---|
| **（无）** | —— | —— | —— |

⚠️ **本域白名单 0 条，这是核实过的结论，不是漏写**：

- `docs/Intentional_Deviations/comment/common.md` = **0 字节（空文件）** ⇒ comment-common **无**已签字偏离。
- `docs/Intentional_Deviations/comment/{api,rpc,proto}.md` 里的已签字条目
  （`SD-2026-10-23-04` proto package 归一、`CAND-07` HS384/512 拒绝、`CAND-08` 未注册路径 413、
  `CAND-05` Bearer 长度边界）**全部与错误码契约层无关** ⇒ **不进白名单**
  （白名单若「整域豁免」就是假绿源 ⇒ 这里只列**逐条**理由，不做粗粒度豁免）。
- §五 的 3 条 OBS 是 **`docs/DRIFT/comment/common.md` §3-D1 登记的 OBSERVABLE DIFFERENCE**，
  **不是** `Intentional_Deviations` 里的签字偏离 ⇒ 它们走 **OBS 段**（单列 + 差异消失即 exit 2），
  **不混进**「已签字白名单」。

---

## 七、变异测试（证明本探针**不是假绿**）

```bash
bash tools/errmsg-probe/comment/mutate-parity.sh
```

⚠️ **变异只发生在临时副本上，仓内 `service/**` 与 `tools/errmsg-probe/comment/**` 一个字都没改**
（与 `tools/errmsg-probe/hot/mutate-parity.sh` 的「原地改生产代码再还原」不同 ——
原地改即使还原也留下窗口期，且本 lane 的写权不允许改 `service/**`）。

| # | 变异 | 手法 | 观测 |
|---|---|---|---|
| **M1** | **Go 侧复制件**文案漂移 | 造一个「假真源仓」（`go.mod`/`go.sum` 原样 + **变异版** `errmsg.go`），用 `GO_REPO` 指过去 | ✅ 精确转红（exit 1） |
| **M2** | **Java 侧源码副本**文案漂移 | 复制模块源码到临时目录 → 改文案 → `javac` 编副本 → `PROBE_JAVA_CLASSES` 指过去 | ✅ 精确转红（exit 1） |
| **M3** | **输入根探针**硬失败 | `GOMODCACHE` 指向空目录 | ✅ 精确转红（exit 2 · 不得静默降级） |
| **M4** | 还原后 | 真源 + 生产 classpath | ✅ 重新转绿（exit 0） |

### M1 · Go 侧复制件（实测输出）

变异内容：`"查询数据库失败"` → `"查询数据库失败。"`

```
变异前 sha256（真源）   = 9820df5a62a2d6cd8823830fa0d000e51e147b8e8b6bf8874a4c862b5ff95ae0
变异后 sha256（副本）   = 6944d7bcbaaec76fcaf2aa026d454ea3abe26da56e2c271dfe65f9fb5a54204e
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5003|查询数据库失败。
+TABLE|5003|查询数据库失败
-CODEERR|5003|ErrCode:5003, Errmsg:查询数据库失败。
+CODEERR|5003|ErrCode:5003, Errmsg:查询数据库失败
```

### M2 · Java 侧源码副本（实测输出）

```
变异前 sha256（生产源码） = 4e9a9625df1c0f6d95e227ba46a1325dc3df57276210414c0199ea08703f2020
变异后 sha256（临时副本） = 467b8f2ef48848ce13ff95a7170def100887f37aef31e19a3caaec505061c3ea
⚠️ 生产源码未被触碰（变异只发生在 <临时目录>/mut-src）
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-TABLE|5003|查询数据库失败
+TABLE|5003|查询数据库失败。
-CODEERR|5003|ErrCode:5003, Errmsg:查询数据库失败
+CODEERR|5003|ErrCode:5003, Errmsg:查询数据库失败。
```

---

## 八、已知边界（**不覆盖**什么）

1. **只覆盖 `comment-common` 的错误码契约层**。HTTP envelope 形状、gRPC 服务名、DDL 均**不覆盖**
   （那些由各域台账与别的探针负责）。
2. **只覆盖「两侧同时跑」的静态对照** ⇒ 证据强度是 **L1/L2**，**不是**双侧活体对拍。
   「跑绿」应读作「**在上述证据强度下一致**」，不是「已闭环」。
3. **OBS 段的三条差异是「不可达形态」**：comment 域真实链路恒走「业务码在 grpc-status」这一条，
   RAG 侧的对应通道（自定义 trailer）在**仓内 api↔rpc**链路上已闭合
   （`docs/DRIFT/comment/common.md` §3-D1 的「🟢 通道现状」行）。
   ⚠️ **方向不对称**（RAG-rpc → Go-api 的业务码传不过去）是**协议层不可修**，
   登记在 `docs/DRIFT/comment/proto.md` §3.1 ⇒ 不在本探针范围。
4. **探针不校验「RAG 生产代码是否真的调用了 `grpcError`**」** ——
   它验的是**契约层**（`grpcError` 本身的行为），调用点覆盖度由各域台账负责。
5. **`PROBE_JAVA_CLASSES` 是变异测试专用的注入点**（`run-parity.sh` 会把它原样打印出来），
   默认走**生产编译产物** `service/comment/comment-common/target/classes`。
