# errmsg-probe · `task` 域：Go ↔ Java 错误契约双侧对拍

> AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 **task 域错误契约层**的落地实现。
> 定位是【回归守门】：任何人改了 task 域的 **gRPC code / description**、
> **HTTP 状态码 / text/plain 文案**、或「**rpc 的错误往上抛时保不保得住 code**」，
> 本脚本立刻 **exit 1**。

---

## 一、真源与被测

| 项 | 值 |
|---|---|
| **Go 真源（rpc 层）** | `Sea-RideTheWind_golang/service/task/rpc/internal/logic/get_task_logic.go`（package `logic`，140 行，**冻结只读**） |
| **Go 真源（api 层）** | `Sea-RideTheWind_golang/service/task/api/`（`types/types.go` · `logic/task/gettasklogic.go` · `handler/task/gettaskhandler.go`） |
| **RAG 被测（rpc 层）** | `service/task/task-rpc` —— `TaskServiceGrpcImpl`（`service/impl/TaskServiceGrpcImpl.java:76-90` 的 `invoke` 是**唯一的错误转换点**）+ `TaskDomainService`（`service/TaskDomainService.java:87-143`） |
| **RAG 被测（api 层）** | `service/task/task-api` —— `TaskController` + `TaskGetService` + `GetTaskReq` + `GlobalExceptionHandler` |
| **Go 真源仓库** | 默认**向上逐层查找**（worktree 在 `RAG.worktrees/<lane>/`，深 2 层 ⇒ 写死「同级兄弟」会指错）；可用 `GO_REPO=/path/to/Sea-RideTheWind_golang` 覆盖 |

⚠️ **本域没有 `-common` 模块**（Go 侧 `service/task` 下只有 `api` 与 `rpc`）
⇒ **没有** like/message 那种「业务码 ↔ gRPC code 转换类」
⇒ **本探针比的不是码值表，而是「gRPC 状态 + description」与「HTTP 状态码 + body」两层**。

---

## 二、怎么跑（复现命令）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-task
bash tools/errmsg-probe/task/run-parity.sh     # ⇒ exit 0（双侧一致）
bash tools/errmsg-probe/task/mutate-parity.sh   # ⇒ exit 1（4 条变异全部精确转红 + 还原后转绿）
```

### 退出码约定（⚠️ 两个脚本**不一样**，别混）

| 脚本 | 0 | 1 | 2 |
|---|---|---|---|
| `run-parity.sh` | ✅ 双侧逐字节一致 | ❌ 有真漂移（diff 段会打印出来） | ⚠️ 环境问题（输入不可见 / 探针跑不起来） |
| `mutate-parity.sh` | ——（**不用**） | ✅ **变异精确转红 = 测试通过** | ❌ 守门失效（某条变异没转红 / 环境问题） |

> ⚠️ **`mutate-parity.sh` 成功时返回的是 `1`，不是 `0`** —— 这是本 lane 派单明确指定的约定。
> 仓内另外 8 个域（`follow` / `message` / `like` / …）的 `mutate-parity.sh` 是「成功 = 0」。
> ⇒ **不要**用 `bash mutate-parity.sh && echo ok` 判成功；请看脚本输出里的
> 「✅ 全部变异精确转红 + 还原后转绿」那一行。

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache
（需含 grpc / gorm / gorm-postgres-driver / go-redis —— **版本从真源 `go.mod` 动态读取**，真源升版本时本脚本自动跟随）。
**不需要** PG / Redis / Kafka / 任何容器（本探针只碰错误契约层，不碰数据面）。

---

## 三、对拍了哪几层

输出格式（两侧必须逐字节相同）：

```
RPCUID|<case>|<grpcCode>|<desc>       4 条  · rpc 层：uid <= 0 ⇒ 领域层抛的 status error
RPCBARE|<case>|<grpcCode>|<desc>      5 条  · rpc 层：裸 error（DB 回源失败）⇒ 兜底翻成 UNKNOWN
API|<case>|<httpStatus>|<body>       34 条  · api 层：一次真实 HTTP 请求的**状态码 + body 逐字节**
OBS|<case>|<value>                    4 条  · 两侧**必然不同**的已知不可达差异（见 §五）
```

| 层 | 判据 | Go 出处（**消费点**） | RAG 出处 |
|---|---|---|---|
| **① RPCUID** | `uid <= 0` ⇒ gRPC code + description **逐字节** | `get_task_logic.go:43-45`（`status.Error(codes.InvalidArgument, "user_id invalid")`） | `TaskDomainService.java:90-94` → 经 `TaskServiceGrpcImpl.invoke:81-83` **透传** |
| **② RPCBARE** | 裸 error（`return nil, err`）⇒ `UNKNOWN` + `err.Error()` **逐字节** | `get_task_logic.go:58-60`（`return nil, err`）+ grpc-go `status.FromError` | `TaskDomainService.java:102` 的异常上抛 → `TaskServiceGrpcImpl.invoke:84-89` 兜底翻 `UNKNOWN` |
| **③ API（解析类）** | `id` 解析失败 ⇒ **400 + text/plain 原文案** | `types.go:16-36`（`UnmarshalJSON`）/ `:38-50`（`Int64`）+ `gettasklogic.go:31-34` + `gettaskhandler.go:21-24` → go-zero `httpx.ErrorCtx` | `GetTaskReq.java:96-98 / 116-135` + `GlobalExceptionHandler.java:221-229 / 290-294` |
| **④ API（rpc 透传类）** | rpc 的错误往上抛时**保不保得住 code** ⇒ HTTP 状态码 + `rpc error: code = X desc = Y` 原文 | `gettasklogic.go:36-41`（`return nil, err`）→ go-zero `errcode.CodeFromGrpcError`（15 分支） | `TaskGetService.java:73`（不捕获）+ `GlobalExceptionHandler.java:86-95 / 101-137`（15 分支逐条复刻） |

⚠️ **gRPC 码名归一**：Go 的 `grpcodes.Code.String()` 是 CamelCase（`InvalidArgument`），
Java 的 `Status.Code.name()` 是 UPPER_SNAKE（`INVALID_ARGUMENT`）⇒ 两侧统一到
**grpc 官方 canonical 形态**（小写 snake_case，module cache
`google.golang.org/grpc@v1.81.1/codes/code_string.go` 的 `canonicalString`）才可比。
⚠️ 归一算法要处理**缩写串**：`"OK"` 不能被朴素的「每个大写前插 `_」`变成 `"o_k"`。

⚠️ **body 转义**：body 里可能有 `\n` / `|` / `\` ⇒ 两侧用**同一套**转义规则压成单行
（`\` → `\\`，`|` → `\|`，LF → `\n`，CR → `\r`）。
⚠️ rpc 层的 **description 同样要转义**（它是错误原文，可以是任意文本）。

---

## 四、CASES 为什么这么选

### 4.1 RPCUID（4 条）—— `uid <= 0` 的四个边界

| 用例 | uid | 期望（两侧） |
|---|---|---|
| `rpc_uid_zero` | `0` | `invalid_argument` / `user_id invalid` |
| `rpc_uid_neg_one` | `-1` | 同上 |
| `rpc_uid_neg_min` | `-9223372036854775808` | 同上（int64 下界） |
| `rpc_uid_neg_big` | `-1234567890123` | 同上 |

⚠️ **只测 `uid <= 0`**：`uid > 0` 会走 Redis HGETALL → 回源查库 ⇒ 需要真 Redis + 真 PG
⇒ 本探针**不起容器**（硬纪律）⇒ 这一段不在本探针范围（由 `docs/DRIFT/task/rpc.md` 的活体证据负责）。

### 4.2 RPCBARE（5 条）—— 裸 error 的**文案**逐字节

| 用例 | error 原文 | 期望（两侧） |
|---|---|---|
| `rpc_bare_simple` | `boom` | `unknown` / `boom` |
| `rpc_bare_pq` | `pq: deadlock detected` | `unknown` / 同文（PG 风格文案） |
| `rpc_bare_empty` | （空串） | `unknown` / （空串） |
| `rpc_bare_unicode` | `用户不存在` | `unknown` / 同文（**非 ASCII 逐字节**） |
| `rpc_bare_has_pipe` | `left|right` | `unknown` / `left\|right`（**含分隔符 ⇒ 逼出转义**） |

⚠️ **Go 侧怎么让 DB 以「受控文案」失败**：给 GORM 塞一个「永远返回受控 error」的
`ConnPool`（`postgres.New(postgres.Config{Conn: pool})` ⇒ dialector 直接采用它，**不连库**；
`gorm.Open` 的自动 ping 只在 ConnPool 实现 `Ping() error` 时才发生 ⇒ **零网络**）。
⇒ 真源走的是**真实的那条代码路径**（Redis 未命中 → 回源查库 → 查库失败 → `return nil, err`），
只是「查库失败」的原因被换成了受控文案。
⚠️ Redis 侧同样用**必然连不上**的 Dialer ⇒ 真源的
「`rerr != nil || len(pm) == 0` ⇒ 一律回源」分支（`get_task_logic.go:51`）被真实走到。

### 4.3 API（34 条）

**（a）解析类 17 条** —— `id` 的每一种非法形态，验「**状态码 + text/plain 原文案**」：

`api_id_missing`（字段缺失）· `api_id_null` · `api_id_other_field`（未知字段）·
`api_id_zero_num` · `api_id_negative_num` · `api_id_zero_str` · `api_id_negative_str` ·
`api_id_abc` · `api_id_empty_str` · `api_id_space_str` · `api_id_float`（`1.5`）·
`api_id_float_whole`（`1.0`）· `api_id_exp`（`1e2`）· `api_id_huge`（超 int64）·
`api_id_bool`（`true`）· `api_id_array` · `api_id_object`

⚠️ 两条**文案不同源**的路径必须分开钉：
`"user_id invalid"` 来自 `types.go:41,46`（`Int64()` 的空/非法/`<=0` 三条失败路径**共用**一个文案）；
`"id must be a string or integer"` 来自 `types.go:35`（`UnmarshalJSON` 的「既非 string 又非 number」）。
⇒ `api_id_bool` / `api_id_array` / `api_id_object` 三条走**后者**，其余走**前者**。

**（b）rpc 透传类 17 条** —— 下游 rpc 返回**受控**的 gRPC 错误，验「**code 有没有被保住**」：

15 条覆盖 go-zero `rest/internal/errcode/grpc.go:12-42` 的**全部 15 个非 OK 分支**
（`CANCELLED`·`UNKNOWN`·`INVALID_ARGUMENT`·`DEADLINE_EXCEEDED`·`NOT_FOUND`·`ALREADY_EXISTS`·
`PERMISSION_DENIED`·`RESOURCE_EXHAUSTED`·`FAILED_PRECONDITION`·`ABORTED`·`OUT_OF_RANGE`·
`UNIMPLEMENTED`·`INTERNAL`·`UNAVAILABLE`·`DATA_LOSS`·`UNAUTHENTICATED`）
+ `api_rpc_padded_uid_ok`（`{"id":"  12  "}` ⇒ TrimSpace 后 uid=12 **合法** ⇒ 进到 rpc ⇒
证「TrimSpace 生效」与「rpc 错误照样透传」两件事）。

⚠️ **OK 码不在本探针范围**：Go 的 `status.Error(codes.OK, …)` **返回 nil** ⇒ 不是错误路径
（Java 侧会造出一个非 nil 的 code=OK 异常）⇒ 两侧语义不同源，不进主段，进 OBS 段。

---

## 五、OBS 段（4 条 · 两侧**必然不同**的已知不可达差异）

⚠️ OBS 段的语义是「**断言差异仍然存在**」：
若哪天两侧变得一致 ⇒ 脚本 **exit 2** 提示「差异已消失，请重新评估并更新台账」。

| case | Go 值 | Java 值 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| `obs_ok_status` | `ok\|` | `ok\|user_id invalid` | Go 的 `status.Error(codes.OK, msg)` **返回 nil**（grpc-go `internal/status/status.go`：`func (s *Status) Err() error { if s.Code() == codes.OK { return nil } }`）⇒ `status.FromError(nil)` ⇒ **没有 description**；Java 的 `Status.OK.withDescription(msg).asRuntimeException()` 造出来的是一个**非 nil**、code=OK、**带 description** 的 `StatusRuntimeException`。**「OK 状态不是错误」是 grpc-go 的 API 形态，grpc-java 没有对应的塌缩** | Go `get_task_logic.go:44`（形态上）；Java `TaskServiceGrpcImpl.java:81-83` |
| `obs_wrapped_status` | `invalid_argument\|outer: rpc error: code = InvalidArgument desc = user_id invalid` | `unknown\|outer: rpc error: code = InvalidArgument desc = user_id invalid` | Go 的 `status.FromError`（grpc-go v1.81.1）**用 `errors.As` 穿透包装**找到内层 status ⇒ **保住 code**；但 **description 被换成外层 error 的全文**（`p.Message = err.Error()`）。Java 的 `TaskServiceGrpcImpl` 只认**直接抛出的** `StatusRuntimeException` ⇒ 包装过就落进 `catch (RuntimeException)` ⇒ **code 退化成 `UNKNOWN`**。**这是 grpc-go 与 grpc-java 的结构性差异** | Go grpc-go `status/status.go` 的 `FromError`；Java `TaskServiceGrpcImpl.java:81-89` |
| `obs_api_empty_body` | `400\|EOF\n` | `400\|Required request body is missing: …\n` | Go 的 `decoder.Decode(&req)` 对空 body 返回 **`io.EOF`**；Spring 侧是 **`HttpRequestBodyRequiredException`** ⇒ 状态码与 Content-Type 一致，**文案逐字不同**（两个运行时的「读不到 body」措辞不同） | Go `gettaskhandler.go:21-24`；Java `GlobalExceptionHandler.java:210-213`（**生产 javadoc 已自登记**） |
| `obs_api_malformed_json` | `400\|unexpected EOF\n` | `400\|JSON parse error: Unexpected end-of-input: …\n` | Go `encoding/json` 的措辞 vs Jackson 的措辞（+ Spring 的 `JSON parse error: ` 前缀）⇒ **状态码与 Content-Type 一致，文案逐字不同**（两个 JSON 库的措辞不同）。**已在台账登记为「全仓 api 域的共性差异」** | Go `gettaskhandler.go:21-24`；Java `GlobalExceptionHandler.java:221-229`；台账 `docs/DRIFT/task/api.md:154-156` |

⚠️ **这四条全部是「构造出来的不可达形态」或「库措辞差异」**：

- `obs_ok_status` / `obs_wrapped_status` 在本域的**真实链路里不可达**
  （领域层只会抛 `codes.InvalidArgument` 或裸 error；api/rpc 两层都**原样透传**，没有一处做包装）
  ⇒ **不构成行为漂移**，但已钉死。
- 🔴 **`obs_wrapped_status` 是一条真实的回归风险**：若将来有人在 api→rpc 之间
  加一层 `try/catch` 把 gRPC 异常**包起来**（`throw new RuntimeException("…", e)`），
  `INVALID_ARGUMENT` 会**当场退化成 `UNKNOWN`** ⇒ 本探针会红。
- `obs_api_empty_body` / `obs_api_malformed_json` 是**库措辞差异**
  ⇒ 已登记为「全仓 api 域的共性差异，**不在本域单独修**」。

---

## 六、白名单（已签字偏离）

| 编号 | 为什么它是签字偏离 | 出处 | 本探针的处置 |
|---|---|---|---|
| **（无）** | —— | —— | —— |

⚠️ **本域白名单 0 条，这是核实过的结论，不是漏写**：

- `docs/Intentional_Deviations/task/rpc.md` 有 **2 条已签字偏离**
  （`SD-2026-10-23-TASK-01` PG 连接最大生存时间 30 纳秒 → 30 分钟；
  `SD-2026-10-24-TASK-01` topic 缺失时 Go 进程退出 vs RAG 服务存活）
  —— **两条都与错误契约无关** ⇒ **不涉及本探针的任何一层**。
- `docs/Intentional_Deviations/task/api.md` 与 `.../proto.md` **均为 0 字节（空文件）**
  ⇒ api / proto 域**无**已签字偏离。
- ⇒ **本探针覆盖的全部 43 条主段断言里，没有一条落在「已签字偏离」上**
  ⇒ 白名单为空 ⇒ **没有任何差异被豁免**（守门无盲区）。

⚠️ §五 的 4 条 OBS 是**跨语言 / 库措辞差异**，登记位置是
`docs/DRIFT/task/api.md` 的「错误响应形状」一节（`:143-156`）与
`GlobalExceptionHandler` 的生产 javadoc（`:205-229`），
**不是** `Intentional_Deviations` 里的签字偏离 ⇒ 走 **OBS 段**（单列 + 差异消失即 exit 2），
**不混进**「已签字白名单」。

---

## 七、变异测试（证明本探针**不是假绿**）

```bash
bash tools/errmsg-probe/task/mutate-parity.sh
```

⚠️ **变异只发生在临时副本上，仓内 `service/**` 与 `tools/errmsg-probe/task/**` 一个字都没改**
（与 `tools/errmsg-probe/hot/mutate-parity.sh` 的「原地改生产代码再还原」不同 ——
原地改即使还原也留下窗口期，且本 lane 的写权不允许改 `service/**`）。

| # | 变异 | 手法 | 观测 |
|---|---|---|---|
| **M1** | **Go 侧复制件**文案漂移 | 造一个「假真源仓」（`go.mod`/`go.sum` + `service/task` 树原样 + **变异版** `get_task_logic.go`），用 `GO_REPO` 指过去 | ✅ 精确转红（exit 1） |
| **M2** | **Java 侧源码副本**文案漂移 | 复制 `TaskDomainService.java` 到临时目录 → 改文案 → `javac` 编**那一个文件** → `PROBE_RPC_CLASSES` 指过去 | ✅ 精确转红（exit 1） |
| **M3** | **输入根探针**硬失败 | `GOMODCACHE` 指向空目录 | ✅ 精确转红（exit 2 · 不得静默降级） |
| **M4** | 还原后 | 真源 + 生产 classpath | ✅ 重新转绿（exit 0） |

### M1 · Go 侧复制件（实测输出）

变异内容：`status.Error(codes.InvalidArgument, "user_id invalid")` → `…"user_id invalid."`

```
变异前 sha256（真源）   = 36b1bef91c9017d5f93437ff4e6612161958c943a50dccd3c9a6861083d15e67
变异后 sha256（副本）   = 981402e6bde1f2b3d9c8eead547d8dcd36449e2fc410689a5b5108c74c39f63d
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-RPCUID|rpc_uid_zero|invalid_argument|user_id invalid.
-RPCUID|rpc_uid_neg_one|invalid_argument|user_id invalid.
-RPCUID|rpc_uid_neg_min|invalid_argument|user_id invalid.
-RPCUID|rpc_uid_neg_big|invalid_argument|user_id invalid.
+RPCUID|rpc_uid_zero|invalid_argument|user_id invalid
+RPCUID|rpc_uid_neg_one|invalid_argument|user_id invalid
+RPCUID|rpc_uid_neg_min|invalid_argument|user_id invalid
+RPCUID|rpc_uid_neg_big|invalid_argument|user_id invalid
```

### M2 · Java 侧源码副本（实测输出）

```
变异前 sha256（生产源码） = a0436142234bf2727384b38c932248fb930feb45dbda409ad606e8783820a455
变异后 sha256（临时副本） = 7a5578ee1767ba57e56c38dd6db7ba60cf63806d93e37ed6e3270ba697a7ca14
⚠️ 生产源码未被触碰
✅ 精确转红（exit 1）
❌ 双侧不一致，diff（- = Go / + = Java）：
-RPCUID|rpc_uid_zero|invalid_argument|user_id invalid
-RPCUID|rpc_uid_neg_one|invalid_argument|user_id invalid
-RPCUID|rpc_uid_neg_min|invalid_argument|user_id invalid
-RPCUID|rpc_uid_neg_big|invalid_argument|user_id invalid
+RPCUID|rpc_uid_zero|invalid_argument|user_id invalid.
+RPCUID|rpc_uid_neg_one|invalid_argument|user_id invalid.
+RPCUID|rpc_uid_neg_min|invalid_argument|user_id invalid.
+RPCUID|rpc_uid_neg_big|invalid_argument|user_id invalid.
```

⇒ **两条变异各自只改动「自己那一侧」的 4 行** ⇒ 守门是**精确**的，不是「整体红一片」。

---

## 八、已知边界（**不覆盖**什么）

1. **只覆盖 `task` 域的「错误契约层」**。HTTP envelope 形状、成功响应体、
   gRPC 服务名、DDL、Kafka、Redis key 一律**不覆盖**。
2. **只覆盖「两侧同时跑」的静态对照** ⇒ 证据强度是 **L1/L2**，**不是**双侧活体对拍。
   「跑绿」应读作「**在上述证据强度下一致**」，不是「已闭环」。
   （`docs/DRIFT/task/api.md` §二·五 的 44 条**活体**对拍是**另一层**的证据。）
3. **Java 侧用 `MockMvcBuilders.standaloneSetup`**（真实 MVC 管线：真实 Controller /
   真实 advice / 真实 DTO + Jackson），**不是** Spring Boot 上下文
   ⇒ **不覆盖**「`TaskApiJsonBodyConfig` 的媒体类型认领」那一层
   （那是 **Content-Type 层**的契约，由 `TaskApiMvcPipelineTest` 与台账负责）。
   本探针所有用例都发 `Content-Type: application/json`。
4. **Go 侧用的是 `httptest.ResponseRecorder`**（真源 handler 真写出来的字节）
   ⇒ 覆盖的是 handler + go-zero `httpx.ErrorCtx` 的**真实**行为，
   但**不是**真 Tomcat / 真 net/http（`http.Error` 的 header 细节由 go-zero 自身保证）。
5. **rpc 层的 `uid > 0` 分支不覆盖**（需要真 Redis + 真 PG，见 §4.1）。
6. **探针不校验「RAG 生产代码是否真的调用了 `TaskServiceGrpcImpl.invoke`」**
   —— 它验的是**契约层**（这两个方法本身的行为），调用点覆盖度由各域台账负责。
7. ⚠️ **本域的错误契约与 like / message 域形状完全不同**（没有业务码表）
   ⇒ **跑通本探针不代表那些域也对齐了**，也不代表本域的成功路径对齐了。
8. `PROBE_RPC_CLASSES` / `PROBE_API_CLASSES` 是变异测试专用的注入点
   （`run-parity.sh` 会把它们原样打印出来），默认走**生产编译产物**
   `service/task/{task-rpc,task-api}/target/classes`。
