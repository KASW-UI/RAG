<!-- @new 2026-10-27 · `task-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `task-common` lane 台账 —— 「task ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（⚠️ **task 域没有 `-common` 模块**（Go 侧同样没有，**不是遗漏**）⇒ 本 lane 的「域 ↔ `service/common`」语义最纯粹）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面一律不写**（合并后由整合者统一更新）

> 📌 **术语订正（2026-10-28 · G2 自修 · 依 G1 通报）**：「**整合者**」是**已废称**
> —— `docs/Worktree.md` §1e 明写「『整合者』不是一个存在的角色」⇒ ⇒ 它会诱导 lane 去找一个**不存在的收件人**。
> ⇒ **现行角色词 = 「合并收口执行者」**（`docs/Worktree.md` §1e 表：它是一个**步骤**，不是一个常驻会话 ⇒ 没有任何会话长期占着这个位置）。
> ⇒ ⇒ **本文件正文的「整合者」一律按「合并收口执行者」理解**；下方**历史条目里的原值保留不改写**（留档纪律）。

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `task-common`（原第 8/10 条） |
| **现分支 / 工作树** | **`align/g2-message-like-task`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g2-message-like-task` |
| 基点 | **`do-my-own-gh` @ `a282012`** |
| 本域模块 | `service/task/{task-api, task-rpc, task-proto}`（⚠️ **无 `task-common`**） |
| 域侧台账 | [`rpc.md`](rpc.md) · [`api.md`](api.md) · [`proto.md`](proto.md) · [`_README.md`](_README.md) |
| **写权** | ✅ 可写**本域** `service/task/**` ＋ `docs/DRIFT/task/**`；🔴 **`service/common/**` + `service/users/users-common/**` 只读** |
| **本轮核对日期** | **2026-10-27**（§1 全表 + §2 全表） |
| **证据强度上限** | **L1** + **L3**；**本轮未同时跑两侧进程 ⇒ 全篇无 L4** |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 逐条重核**）

> **落点一律「读消费点」判定，不照抄旧列**。

| 编号 | 摘要 | 落点（**读消费点后判定**） | 状态（**2026-10-27 核对**） | 证据 | 域台账 |
|---|---|---|---|---|---|
| `DRIFT-MSG-V-026` | 熔断 / 降载链必须**真挂到** gRPC server | **拆两笔**：① **装配面 = 域侧**（`TaskRpcApplication` 是域内文件）② **链的可断言性 = 共享层**（`GoStyleRpcProtectionChain`） | 🟡 **部分修**（旧快照「守门已立」**掩盖了一个缺口**）—— ① `@Import GoStyleProtectionConfiguration` **属实**：`TaskRpcApplication.java:113` ＋ `@Bean GrpcServerConfigurer` `:130-139`（**唯一把 interceptor 挂上 server 的入口**）② 链上**确有** breaker：`GoStyleRpcProtectionChain.java:72` → `GoStyleRpcBreakerInterceptor.java:140-154` ③ 🔴 **shedding 未被守门证明** —— `TaskProtectionChainWiringTest.java:80-81` **主动 `props.getCpuShedding().setEnabled(false)`** ⇒ 该测试**只证明 breaker**，而 DisplayName 写的是「breaker/shedding」 | [`rpc.md`](rpc.md) <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| `TaskRpcFatJarArtifactGateTest`（4 条） | 产物层守门 | 域侧构建 | ✅ **已立**（本轮只读源码，未跑构建 ⇒ 「已立」而非「已验证」） | `task-rpc/pom.xml:283-286`（`spring-boot-maven-plugin`，不写 `<mainClass>`⇒自动探测）+ `:207-211`（`grpc-inprocess` **scope=compile**，非 test）+ `:336-347`（surefire 第二 execution 绑 `verify`）；测试 `TaskRpcFatJarArtifactGateTest.java:74-143` | [`rpc.md`](rpc.md) |
| `TaskRpcSlowThresholdGateTest`（2 条） | slow 轴守门 | `service/common`（配置读取） | ✅ **已立**（真 server + 真链 + 真 OTel SDK + 收集型 exporter） | `TaskRpcSlowThresholdGateTest.java:267-275`（取容器里的 `rpcInterceptorConfigurer` + `ServerBuilder.forPort(0)` 真起）+ `:305`/`:312`（判别式断言）· 消费点 `RpcServerInterceptorFactory.java:77-79`；⚠️ 它同样关掉了降载（`:148`） | [`rpc.md`](rpc.md) |
| ✅ `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（CD-6） | ✅ **已落地**；本域 `mode` **阶段 2 已由 `raw` 改为 `"off"`** ⇒ 与「Go 侧本域根本走不到 form 解析」**同向** ✅（守门 `TaskApiFormLimitReachabilityTest` 5/5 绿 + 变异 4 红）<br>⚠️ **改用 `off` 的判据不是「错误形状」而是「语义可达性」** ⇒ 原判据（按「Go 有没有注册 `SetErrorHandler`」决定 raw/envelope）**只回答「拒绝成什么形状」、没回答「该不该拒绝」** ⇒ 该缺口已作为 **FB-02** 派给共享层；🔴 **`off` 在 YAML 里必须加引号**（YAML 1.1 → 布尔 false ⇒ 上下文起不来）⇒ **FB-05** | Go 侧证据：`service/task/api/**` 内 `httpx.Parse` / `ParseForm` / `GetFormValues` **命中数 = 0**（本轮实测）⇒ `gettaskhandler.go:19` 裸 `json.NewDecoder(r.Body)`；路由只有一条（`routes.go:16-25`：`POST /task/get`）⇒ Go 侧 `r.Form` 从不被求值 ⇒ `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）与 `util.go:48-53` 的计数**均不可达** | [`api.md`](api.md) |

⇒ **§1 四条 ⇒ 0 条「新派单」**（3 条已立/已落地 + 1 条拆两笔）⇒ 其中拆出的**共享层半笔**见 §4 **FB-03**。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**2026-10-27 核对**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① server：`task-rpc/application.yml:231` `rpc-timeout: 2s` → 消费点 `RpcServerInterceptorFactory.java:80-81` ② 链挂载 `TaskRpcApplication.java:138` ③ HTTP：`task-api/application.yml:127` `http-timeout: 3s` → `HttpMiddlewareFilter.java:52-57` ④ client deadline：【订正 2026-10-31 · 第二来源收敛轮】`TaskRpcClient.java:112`（`getTask()` 直接用**裸 stub** ⇒ 域侧**零**上界代码）〔原写 `TaskRpcClient.java:105-111`（`min(2000ms, 剩余)`）—— 该域侧副本**已于 2026-10-31 删除**〕⑤ SQL 取消 `TaskRpcApplication.java:92-93` ⑥ **Redis 超时**：`task-rpc/application.yml` `spring.data.redis.timeout: 3s` ＋ `connect-timeout: 5s` → 消费点 `RedisURI.timeout`（`CommandExpiryWriter:160`）＋ `ClientOptions.socketOptions.connectTimeout`（Boot `LettuceConnectionConfiguration:212-215`）⑦ **Redis 连接池**：`task-rpc/application.yml` `spring.data.redis.lettuce.pool.{enabled,max-active,max-idle,min-idle}` ＋ `task-rpc/pom.xml` 的 `commons-pool2` → 消费点 Boot 4 `LettuceConnectionConfiguration:267-271`（`getPoolConfig` ⇒ `:269 setMaxTotal` / `:270 setMaxIdle` / `:271 setMinIdle`）＋ `DataRedisConnectionConfiguration:195-198`（`isPoolEnabled`） | ✅ 一致（三个超时：rpc 2s / http 3s / client 2s，逐个对齐 Go 的 struct tag 默认值）<br>🔴 2026-10-27：**3s 预算的作用域** —— ✅ 一致且**天然不适用**「验签是否计入预算」这个问题：Go 侧 `service/task/api/internal/handler/routes.go` 的路由表（**1 条** `/task/get`）**不带** `rest.WithJwt` ⇒ **task-api 没有鉴权** ⇒ 没有「验签」这件事 ⇒ users 域那条 `DRIFT-USERAPI-009②` 对本域**不适用**（不是「已修」，是「不适用」）<br>🔴 2026-10-27（G2 第二轮）：**Redis 命令/连接超时原先未配** ⇒ 落 Lettuce 默认（命令 **60s** / 连接 **10s**）⇒ Go 侧真值是 go-redis v9.20.0 库默认（命令 **3s** / 连接 **5s**）⇒ **已修**（补 `timeout: 3s` ＋ `connect-timeout: 5s`）⇒ 双侧活体实测：Go 1694ms 快速失败 vs RAG 修前 60021ms 挂起 ⇒ 详见 [DRIFT-TASK-RPC-007](rpc.md#✅-drift-task-rpc-007--redis-命令超时未配--落-lettuce-默认-60s--断连时命令挂-60sgo-是-17s-快速失败)<br>🆕 **2026-10-27（G2 第三轮）：Redis 连接池维度** —— 原状 = **无池**（Go 侧 go-redis 默认**有池** ⇒ **形态漂移**）⇒ **已修**（加 `commons-pool2` ＋ `lettuce.pool.enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0`）⇒ 守门 **⑤⑥**（**读真装配**，不读 yml）＋ **三条变异验证**（红在目标断言）⇒ 详见 [DRIFT-TASK-RPC-008](rpc.md#✅-drift-task-rpc-008新登记--2026-10-27redis-连接池维度未对齐--形态漂移rag-无池-vs-go-有池) | L4（Go 腿活体探针已把「Go 的 3s 预算**不含**验签」钉死；task 侧因其无验签 ⇒ 该子问题在 task 域**恒成立**；Redis 超时轴 = **双侧活体对拍** L4；🆕 Redis **连接池**轴 = **L1 ＋ 装配级 + 变异**（⚠️ 未做双侧并发活体对拍 ⇒ **不写 L4**）） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | ① `@Import`：`TaskRpcApplication.java:83-115`（`RpcServerInterceptorFactory`:87 / `ObservabilityProperties`:88 / `GoStyleProtectionConfiguration`:**113**）② `@Bean GrpcServerConfigurer` `:130-139` ③ 链定义 `GoStyleRpcProtectionChain.java:61-78`（`:65` shedding 分支 / `:72` breaker 分支）④ breaker 包装点 `GoStyleRpcBreakerInterceptor.java:140-154` | ✅ **一致**（相对序 recover→breaker→shedding→业务 与 Go `go-zero@v1.10.2/zrpc/server.go:144-167` 一致；本域 yaml 无 `rag.protection` 段 ⇒ 走默认 `enabled=true`/`900` ⇒ Go 的 `CpuThreshold>0` 等价成立）<br>✅ **守门已补齐两层**（`fad4c63`）：`TaskProtectionChainWiringTest` 由 **1 条链层断言**扩到 **2 条** —— ① 「关降载 ⇒ 拿到的 call 归熔断层所有」② 「不关降载 ⇒ 拿到的 call 归降载层所有」⇒ 原 DisplayName「breaker/shedding」是**假断言**（`:80-81` 主动关降载）⇒ 已订正<br>🟡 **共享层的「链层构成」只读入口（FB-03）已由 G4 落码**（`compose(props)` / `layerNames(chain)`，G4 提交 `182fc32`）但**未合入本树** ⇒ 本轮引用它会让本模块**编译不过** ⇒ 改用等价的「包装层归属」判据顶上，**合并后**按 §5.3e 重跑收口回归时升级<br>🆕 **2026-10-27 第四轮：已按 §5.3e 升级完毕（合并 G4 共享层后）** —— 上面的「未合入本树 / 合并后升级」是**合并前**的记述、**不是现状** ⇒ ⇒ 合并 `align/g4-service-common`（merge commit `37816ca` · G4 要求的**整支合并**协议、**不 cherry-pick**）后，`compose(props)` / `layerNames(chain)` 已在本树 ⇒ ⇒ **`TaskProtectionChainWiringTest` 新增 2 条共享层原生判据**：① 「链的层名列表 == `[recover, breaker, shedding]`」**且**「**实际装上的链 == 配置推出的构成**」（`layerNames(chain)` == `compose(props).layerNames()`）② 「关掉降载 ⇒ 层名列表变成 `[recover, breaker]`」（证明降载层是**条件装的**、不是恒在）⇒ ⇒ **旧的「包装层归属」两条保留不删**（它证「那些层真的包住了调用」，层名证不了；层名证「有哪些层 + 层序」，包装归属证不了 ⇒ ⇒ **互补而非替代**）⇒ ⇒ **本域 A2 轴由 L1 升为 L2（运行期 · 守门 + 变异验证）** | **L2（运行期）** | 2026-10-27 |
| **A3** | HTTP 保护链 | ① 扫描面 `TaskApiApplication.java:78-91`（`basePackages` 含 `com.rag.common`，**排除** `GoStyleJsonBodyConverterConfig`:88-90）② maxConns `GoStyleMaxConnsFilter.java:95-97,128` ③ breaker `GoStyleBreakerFilter.java:76-78,116` ④ shedding `GoStyleCpuSheddingFilter.java:62-64,117` ⑤ maxBytes `GoStyleMaxBytesFilter.java:82-84,112` ⑥ form 闸门 `GoStyleFormValueLimitInterceptor.java:80-97` + 注册 `GoStyleFormLimitWebMvcConfig.java:41-43`；mode=**"off"**（**阶段 2 已改**，原 `raw`）← `task-api/application.yml:213` ⑦ Tomcat 上限 `GoStyleTomcatParameterLimitCustomizer.java:95,106-118` ⑧ Content-Type 判据 `TaskApiJsonBodyConfig.java:83-97,117-119` ⑨ **路径归一缺** | ✅ **一致**（四道闸门 + 拒绝形状 503/503/503/413 与 Go 一致；🟢 **form 闸门已按「语义可达性」关掉** ⇒ 本域不再多出 Go 没有的拒绝（守门 `TaskApiFormLimitReachabilityTest` 5/5 绿 + 变异 4 红）；🟢 **共享层的判据已由 G4 落码**（FB-02 · G4 `182fc32`：新增 `SEMANTIC_REACHABILITY` 逐域取证表，**task 行 = `Mode.OFF`** ⇒ 与本域 `mode: "off"` **同向** ⇒ 域侧与共享层**不会互相打架**；✅ **路径归一：共享层已落码（FB-01 · G4 `182fc32`）且已合入本树** ⇒ ⇒ **本域已跑真容器探针**（第十轮 · A/B 对照组 + 本域独立变异验证）⇒ ⇒ **「路径归一」这一格 = RAG 腿 L4**；🔴 **`off` 在 YAML 里必须加引号**（YAML 1.1 → 布尔 false）⇒ §4 **FB-05**）<br>🆕 **2026-10-27（G4 `2f3d5c2` 订正 + G2 复核）**：G4 上一轮判「本域改 `off` ⇒ 运行期容器上限掉回 2048 ⇒ **比 Go 更严**」⇒ ⇒ **已被本域实测否掉**（G4 已自行订正并撤销该提醒）⇒ ⇒ **本域结论：OFF ⇒ 运行期 2048 对 task 域无害**（本域控制器只有 `@RequestBody`、从不调 `getParameter*` ⇒ Tomcat 参数解析惰性 ⇒ 2048 **不可达**）⇒ ⇒ 与 G4 现在的 A3 轴行**同向** ⇒ ⇒ **两侧无冲突**<br>⇒ ⇒ **教训（已写进本域 D-1 与 yml 注释）**：「**运行期上限不同**」≠「**行为不同**」⇒ 判定漂移必须再问一句「**这个上限在该域的代码路径上可达吗**」<br>🆕 **2026-10-27（G2 补 Go 侧活体）**：**form 闸门这一格的 Go 侧证据已从 L3 升到 L4** —— Go 真源 `task-api` 实起（18886）⇒ **`POST /taskcenter/v1/task/get` ＋ 2100 个非空 query 值 ⇒ 响应 503（下游 task-rpc 未起）⇒ 不是 400 + `too many form values`** ⇒ ⇒ **Go 侧闸门不触发 = 活体坐实**（与源码 `gettaskhandler.go:19` 走 `json.NewDecoder(r.Body)` 互相印证）⇒ ⇒ **两侧在「form 闸门可达性」这一轴上一致** ⇒ ⚠️ **但 A3 轴整体仍不标 L4**：Go 侧给的是 503 ⇒ 「**两侧响应逐字节一致**」那一格**未闭合**（需 Go `task-rpc` 起着）⇒ 详见 §6 第 1 条 | L1（+ 域侧守门 L4 · Go 腿 form 闸门一格 L4） | 2026-10-27 |
| **A4** | 错误码 / envelope | ① 无信封 `TaskController.java:87-90`（直接返回 `GetTaskResp`）② gRPC→HTTP `GlobalExceptionHandler.java:86-95,101-137`（17 码 + default 500）③ 兜底 = **400** `:290-294` ④ 裸响应体 `:305-311`（`text/plain; charset=utf-8` + `nosniff` + 尾 `\n`）⑤ 404/405 `:254-258,267-274` → `GoStyleRouterErrorResponses` | ✅ 一致（本域**未注册** `SetErrorHandler` ⇒ 形状 = 真状态码 + text/plain，**不是** 200 + 信封 ⇒ 与 like/message/hot 域刻意不同，与 Go 同） | L1 | 2026-10-27 |
| **A5** | 端口治理 | ① api `task-api/application.yml:72` `port: ${TASK_API_PORT:18886}` ② rpc `task-rpc/application.yml:139` `grpc.server.port: 9005` ③ client 寻址 `task-api/application.yml:113` `127.0.0.1:9005`（`negotiation-type: plaintext`:114） | ✅ 一致（与 Go `task/api/etc/task.yaml:3` / `task/rpc/etc/task.yaml:2` / `:6` 逐条对齐；⚠️ 本域下游**不走服务发现**，与 Go 一致）<br>✅ **阶段 2 已治理测试端口段**：本域 **2 处** `RANDOM_PORT`（`TaskApiContentTypeTest.java:62` + 本波新增的 `TaskApiFormLimitReachabilityTest.java:70`）⇒ **`DEFINED_PORT`** + 本域专用段 **21800–21899**（`service/task/task-api/src/test/java/com/rag/task/task/api/support/RealTomcatPort.java` · favorite 同款复制）+ 每类 1 条端口段守门 `bindsToDedicatedPortSegment` | L1（+ 端口段断言 L3） | 2026-10-27 |
| **A6** | errmsg 契约 | — | **N/A**（Go 的 task 域**无** errmsg 模块：`service/task/**` 全目录 `errmsg` 命中 0 ⇒ 错误文案只有两条字面量，已在 A4 内核） | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/task/task-proto/src/main/proto/task.proto:7-25` · 消费点 `TaskServiceGrpcImpl.java:48` | ✅ 一致（逐行 diff：message 3=3、字段 7=7、字段号/名/类型全同、service/rpc 名全同；差异**仅** option 块） | L1 | 2026-10-27 |
| **A8** | DDL + 账本 | ① `baseline-version: 0`：`task-rpc/application.yml:76` ② 独立账本表名 `:79` `flyway_schema_history_task`（`locations`:77）③ DDL `V1__create_task_progress.sql:23-41`（BIGINT/TEXT/TIMESTAMPTZ）④ `V2__create_user_like_count.sql:17-25`（`user_id TEXT`）⑤ 兜底 `TaskSchemaGate.java:41-43,51-52` | ✅ 一致（时间列 **TIMESTAMPTZ**；`user_like_count.user_id` TEXT vs `task_progress.user_id` BIGINT 的**不一致被刻意保留**；账本表名与 message/like/article/comment/favorite/follow 六家两两不同 ⇒ `check-flyway-ledger.py` **EXIT=0**） | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | ① `Traceparent`：`GoStyleTraceparentFilter.java:188-191`（`@Order(+3)`）+ `:270-283`（Extract→Start→**Inject 在业务前**）⇒ 本域经 `TaskApiApplication.java:78-82` 扫到 ② 全局 provider `AutoConfiguration.imports:1` → `GoStyleOtelAutoConfiguration.java:51-52` ③ span 属性 `ObservabilityConstants.java:81,87,99`（`sea.timeout`/`sea.slow`/`sea.duration_ms`）④ `audit.*`：**本域无**（Go 侧 `service/task/**` 命中 0）⑤ 🆕 **MQ 路径**：两个 sink 各 2 处 `MqConsumer.traceConsumer`（`UserLikeProgressSink:135/:228` + `ArticleLikeProgressSink:150/:241`） | ✅ 一致（`audit.*` = **N/A**）；`Traceparent` 注入位置与头名 canonical 化与 Go 同形（Go `go-zero@v1.10.2/rest/handler/tracehandler.go:61`）<br>🆕 **2026-10-27（G2 独立核实 · 销 §6 第 4 条）**：**MQ 路径已逐条查消费点** ⇒ **6 个 Kafka 消费点与 Go 逐条对上**（Go `Init/*.go` 的 6 处 `NewQueue`/`DefineGroup` ⇒ RAG 6 个 `@KafkaListener`，group 与 topic **全对**；`GroupFilterArticle` 在 Go 侧确实无消费点、RAG 侧 yml 如实标注「保留仅为配置面完整」⇒ **两侧同款**）⇒ ⇒ **两个 sink 都包了 `MqConsumer.traceConsumer`** ⇒ ⇒ **A9 的 MQ 面销号**<br>⚠️ **同轮的两个判据错（记下来免得下次再踩）**：Go 侧 mq 目录叫 **`internal/sink/`**（不叫 `mq/`）· yaml 键是**大驼峰** `OutUserTopic`（`service/task/rpc/etc/task.yaml:20-21`）⇒ ⇒ **一次 find/grep 零命中 ≠ 事实为零** | L1 / L3 | 2026-10-27 |

> **契约面单列（不在 A1~A9 内）**：Kafka topic `task.logs.filtered.User` / `task.logs.filtered.Article` 是**跨域契约** —— 生产方 = **`like-rpc`**（Go `service/like/rpc/etc/like.yaml:92,99`；RAG `service/like/like-rpc/.../application.yml:222,224`），消费方 = task-rpc 两个 sink（`task-rpc/.../application.yml:160-161` `out-user-topic`/`out-article-topic`）⇒ **两侧 topic 字面量一致**（L1）⇒ 断链风险 = 改名 ⇒ **须 like / task 同批改**。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **A3 form 闸门在本域的开关值** | 原 `task-api/application.yml:176-178` `mode: raw` ⇒ 触发 `GoStyleFormValueLimitInterceptor.java:86,95` | ✅ **阶段 2 已落码**：`mode: raw` ⇒ **`mode: "off"`**（`task-api/application.yml:213`）<br>⚠️ **必须加引号**：YAML 1.1（SnakeYAML）把**未加引号的** `off` 解析成**布尔 false** ⇒ `@Value` 拿到 "false" ⇒ 共享层 `parse` 判非法 ⇒ **上下文直接起不来**（实测报错 `非法的 rag.http.go-form-limit.mode 值：false`）⇒ ⇒ 共享层已派单 **FB-05**（`parse` 是否接受 `false`）<br>⚠️ **改前的前置已逐条核实**：① `GoStyleFormValueLimitInterceptor.preHandle` 的 OFF 分支直接 return；② `GoStyleTomcatParameterLimitCustomizer.customize` 的 OFF 分支**直接 return** ⇒ **不动**容器上限 ⇒ Tomcat 保留 yaml 的 2048；③ 本域**从不调 `getParameter*`**（控制器只有 `@RequestBody`，没有 `@RequestParam`）⇒ Tomcat 惰性解析 ⇒ **不可达 ⇒ 无影响**<br>🆕 **2026-10-27（G4 知会后 · 连锁效应已独立复核）**：G4 指出「改 `mode: off` ⇒ ① 共享层**不再**把容器上限抬到 10001 ⇒ **运行期上限落回 yaml 的 2048**；而 Go 侧 task 域 `r.Form` 从不求值 ⇒ Go 只有 `net/url` 的 `defaultMaxParams = 10000`（**Go 1.25 `net/url/url.go:1001`**，真源 go.mod = `go 1.25.11`）⇒ ⇒ **2048 比 Go 更严**」⇒ **本域独立复核 = 逐条成立**：① 共享层 `GoStyleTomcatParameterLimitCustomizer#customize`（G4 树实测）⇒ `mode == OFF` 分支**直接 `return`**、非 OFF 才 `setMaxParameterCount(MAX_PARAMETER_COUNT = 10001)`；② 本域 `@RequestParam` / `@RequestPart` / `getParameter*` **命中数 = 0**（`src/main/java` 全目录）⇒ Tomcat **永不解析 query** ⇒ **2048 够不着** ⇒ ⇒ **两侧都不可达 ⇒ 无可观察差异**⇒ ⇒ **处置：登记为「有界残留 · 当前不可达」**（**不是漏项**）⇒ ✅ **已有网钉住这条不可达性**：`TaskApiFormLimitReachabilityTest`（**真 Tomcat**）的「**>2048 个非空 query 值 ⇒ 200**」⇒ ⇒ **若哪天本域开始读 query 参数 ⇒ 该守门会红** ⇒ ⇒ 届时须把容器上限提到 **10001**（对齐 Go 的 net/url 段数上限）⇒ 🆕 **本域 yml 注释已同步订正**：`task-api/application.yml` 里原写「本属性**同时**决定两件事…① 把容器上限抬到 10001」⇒ 那是**非 OFF 域**才成立 ⇒ **已改成「只在 mode ≠ OFF 时」** ＋ 补上「本域 OFF ⇒ 停在 2048 ＋ 不可达 ＋ 守门钉住」这三段<br>✅ **域内守门** `TaskApiFormLimitReachabilityTest.java`（**真 Tomcat** + `HttpURLConnection`，5 条）：① 生效模式必须是 **OFF**（**直接**断言 `GoStyleFormValueLimitInterceptor#mode()` —— 光断言「返回 200」区分不了「关掉」与「根本没装」）；② >2048 个非空 query 值 ⇒ 200；③ `?a=%zz` ⇒ 200；④ `?a=1;b=2` ⇒ 200；⑤ 阳性对照（无 query ⇒ 200 + 正常 task 载荷）<br>✅ **变异验证**：把 yaml 改回 `mode: raw` ⇒ **4 条红**（含 400 + Go 原文 `too many form values, error: …`）⇒ 非假绿<br>🆕 **2026-10-27 G4 回执 ⇒ 本条与共享层那一半已闭环**：G4 落码的 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 逐域表里，**task 行 = `Mode.OFF`**（理由与本域取证逐字一致：`httpx.Parse / ParseForm / GetFormValues` 命中数 = 0 ⇒ `r.Form` 从不被求值）⇒ ⇒ **本域的 `mode: "off"` 与共享层的语义答案一致** ⇒ 不存在「域侧改了、共享层表里还写着别的」这种互相打架<br>⚠️ 共享层 `REGISTERED_DEVIATIONS` 里**只有 favorite 一条** ⇒ **task 不在偏离清单里** ⇒ 本域这条是**真对齐**、不是「已登记的例外**<br>🆕 **订正见文末「D-1 订正」节**（2026-10-27 13:1x）：G4 `cd893d1` 把「容器上限抬升」与「闸门有无」**解耦** ⇒ ⇒ **「OFF ⇒ 停在 2048 ⇒ 比 Go 更严」那一面已被消除** ⇒ ⇒ 残留**形态翻转**为「更松 1（10001 vs Go 10000）· 仍不可达」⇒ ⇒ **原值保留不改写** |
| D-2 | **`TaskProtectionChainWiringTest` 的覆盖面** | `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskProtectionChainWiringTest.java:80-81`（`props.getCpuShedding().setEnabled(false)`）+ `:116-119`（`isNotSameAs`）⇒ **只证明 breaker** | ① DisplayName / 注释改为「断言 **breaker** 在链上」② **补一条 shedding 断言**（不关降载，断言业务 interceptor 拿到的是**第二层**包装 call）③ 若共享层提供「链层构成」查询入口（FB-03），优先用它断言两层<br>✅ **阶段 2 已落码**（`fad4c63`）：① DisplayName / 注释订正为「断言**熔断**在链上」② 新增 `sheddingLayerWrapsTheBusinessInterceptor`（**不关降载** ⇒ 断言业务 interceptor 拿到的 call 是**降载层**包出来的）③ 判据选「**包装层归属**」而非「降载统计增量」—— 后者是**进程级单例**且每分钟上报会**清零** ⇒ 增量断言有 ~1e-5/次的**假红**概率 ⇒ **守门不能带随机性**<br>⚠️ 断言用 `getEnclosingClass()` 而非 `getDeclaringClass()`（后者对**匿名类**返回 null）<br>✅ **变异验证**：摘掉降载层 ⇒ **1 failure**（红在 `sheddingLayerWrapsTheBusinessInterceptor:150`）；摘掉熔断层 ⇒ **1 failure**（红在 `breakerLayerWrapsTheBusinessInterceptor:118`）⇒ 两次都**只红 1 条**（另一条为阴性对照仍绿）⇒ 非假绿 |
| D-3 | `baseline-version: 0` / 独立账本表名 | `task-rpc/application.yml:76,79` | **无动作**（已核，属实） |
| D-4 | Kafka 跨域 topic `task.logs.filtered.*` | `task-rpc/application.yml:160-161` ＝ `like-rpc/application.yml:222,224` | **无动作**（字面量一致）；改动须 like/task **同批** |
| D-5 | `task-rpc` 的 Kafka listener 容器 | `TaskRpcApplication.java:186-193`（同名 Bean 覆盖自动配置，自带回填 `consumerFactory`）+ `:202-216`（不 seek / 不 commit / `maxAttempts=1`） | **无动作**（已核） |
| D-6 | 🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删**<br>⚠️ **2026-10-30 真实性复核（follow+task lane · 状态订正）**：原判「⛔ 带闸门 · 尚未删」**已不成立** —— 本轮实测 `find service/task -name 'MybatisPlusOrderingBridgeAutoConfiguration.java'` ⇒ **零命中**（域侧桥**已删**）；`service/task/task-rpc/src/main/resources/META-INF/spring/` **目录已不存在**（⇒ 注册行 `AutoConfiguration.imports` **一并删掉**）＋ 共享层 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` **在位**⇒ ⇒ **闸门条件已满足、删除已发生** ⇒ **本条现行态 = ✅ 已完成（原表描述是 2026-10-27 的陈旧快照）**；⚠️ **原指针已失效**（两个被指文件**已不存在** ⇒ 不是「指错行」而是「指错已删文件」）⇒ **现行落点 = 共享层 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java`**。核对日期 2026-10-30 | 本域持有 `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/task/task-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:33` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避 ⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑 `task-rpc` 的真上下文守门 ⇒ 判据 = `SqlSessionFactory` 在 + `@Import` 的 `DeadlineQueryTimeoutInjector` **真在执行链上**<br>⇒ 两座桥同向 ⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② **本域删除爆炸半径 = 2 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行）—— 本域**无任何测试引用该类名**（`grep` 实测）⇒ **「连带处理 javadoc」在本域为零**（类自己的 javadoc 随文件消失）③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `task/rpc.md:69` / `:1289`（把桥登记为基础设施事实）在删除后**一并订正**（**同域写权内**） | <br>🟢 **2026-10-27 第十七轮：闸门已满足 ⇒ 本域排序桥已收编（详见本文件「第十七轮 · 域侧排序桥收编到共享层」段）** ⇒ ⇒ **本条原写的「共享层 `service/common` 命中数 = 0」已过期**（那是 03:3x 时的状态；共享层那份 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` ＋ 其 `AutoConfiguration.imports` **第 4 行** 已在我树里）⇒ ⇒ **闸门解除** ⇒ ⇒ ⚠️ **本条 ⑤ 里的行号已漂移**（`task/rpc.md:69`/`:1289` ⇒ **实为 `:70`/`:1416`**）⇒ ⇒ **那两处已按「保留原值 + 加订正注记」订正**（行号属事实性指针 ⇒ 纠正指针 + 留注记）⇒ ⇒ ⚠️ **但收编揭出一条**跨 lane **的构建路径缺陷**（单模块构建的 fat jar 会起不来）⇒ ⇒ **本域尤其要注意：本域没有进程级 fat jar 守门 ⇒ 收编后测试全绿 ≠ 产物没问题** ⇒ ⇒ 详见该段 ②③④ |

| D-7 | **A5 测试端口段** —— 本域 **2 处**真容器测试类用 `RANDOM_PORT` | `TaskApiContentTypeTest.java:62` · `TaskApiFormLimitReachabilityTest.java:70`（**本波新增的那一个也算**）⇒ 落 OS 临时端口段 49152–65535（**Docker 随机发布端口同段**） | ✅ **阶段 2 已落码**：新增 `service/task/task-api/src/test/java/com/rag/task/task/api/support/RealTomcatPort.java`（**21800–21899**）+ 2 个测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **选段依据**：避开 favorite 的 **21300–21399**、favorite 探针的 **21400–21599**、favorite 脚手架的 **21950–21999**、OS 临时段 **49152–65535** ⇒ 取 **21800–21899**（like **21600–21699** / message **21700–21799**）<br>⚠️ **为什么各域各复制一份**：`service/common` **不发布 test-jar** ⇒ 业务模块 test scope 拿不到共享层测试类 ⇒ 只能复制（与 favorite 域既定做法一致）<br>⚠️ **守门到底拦什么（本轮实测厘清）**：**改回 `RANDOM_PORT` 不会红** —— `@DynamicPropertySource` 的 `server.port` **优先级高于** `webEnvironment` ⇒ 端口**仍**落在本域段内 ⇒ 那种改法其实无害（只是白写了 `RANDOM_PORT`）⇒ 守门真正拦的是 **① 端口注入被拿掉**（⇒ 落回 `task-api/application.yml` 的 **18886** ＝ 生产端口）与 **② `pick()` 返回段外端口** |<br>🟢 **2026-10-27（G2 独立复核 · G4 端口段表落地后）**：**实测复核通过** —— ① `service/task/task-api/src/test/java/com/rag/task/task/api/support/RealTomcatPort.java:69` `FIRST = 21800` / `:72` `LAST = 21899`（与 G4 的测量**逐条一致**；G4 测量基线 = `9ca4b2c`）② **本域真 `RANDOM_PORT` 注解 = 0**（`grep webEnvironment | grep RANDOM_PORT` 在 task 下命中 **4 条**，**逐条看全是 javadoc / 断言文本里的「有人把 webEnvironment 改回 RANDOM_PORT」** ⇒ **不是注解**）③ 🟢 **端口段表已闭合**（G4 `46f67bf` · 台账 §4.10.19）⇒ ⇒ **本条 D-7 = 已销号**<br>⚠️ **统计口径（写给下一个复核者）**：数「真 RANDOM_PORT」**必须只数 `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` 真注解**，**不数 javadoc / 字符串里的提及**（本域就是被提及命中的典型 ⇒ **文件数 ≠ 注解数**）⇒ ⇒ **「提到」与「用着」是两件事** |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

### 🆕 FB-01 · A3「路径归一」在共享层缺位（**9 个 api 缺 Go 的 `path.Clean` 归一**）

| 项 | 内容 |
|---|---|
| **① 现象** | Go 侧 router 对路径做 `path.Clean` ⇒ 尾斜杠 / 双斜杠 / `.` / `..` 变体命中同一路由；RAG task-api **无归一** ⇒ 变体落到 404（⚠️ 「返 404」是**推断**，需真容器探针确认） |
| **② Go 侧出处** | `go-zero@v1.10.2/rest/router/patrouter.go:48`（`cleanPath := path.Clean(reqPath)`）+ `:60`（`reqPath := path.Clean(r.URL.Path)`）⇒ **两行已实测真实存在**<br>⇒ 语义证据：`docs/DRIFT/article/api.md:849`（CASE-25 尾斜杠 ⇒ **Go 200**） |
| **③ RAG 侧现状** | `service/common/src/main/**` 下 `GoStylePathNormalizingFilter` **命中数 = 0**（本轮 `find` 实测）⇒ 全仓唯一实现 = `service/article/article-api/.../config/GoStylePathNormalizingFilter.java`（**域内类**）⇒ task-api 的 `TaskApiApplication.java:78-91` 扫 `com.rag.common` 时**扫不到它** |
| **④ 证据强度** | **L1**（Go 源码行号 + RAG 文件缺失，均可复算）<br>🟢 **Go 侧已升级为 L4 活体**（2026-10-27 · G4Common 实测）：go-zero **真 server** + curl 探针 ⇒ `/v1/x` · `/v1/x/` · `/v1//x` · `/v1/x/../x` · `/v1/./x` **五种变体全部 200** ⇒ **Go 的 `path.Clean` 归一已活体坐实**<br>⚠️ **RAG 侧后果**仍待 G4 落码后的真 Tomcat 守门 ⇒ 本条整体**暂不标 L4** |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「所有 api 的请求路径都按 Go 的 `path.Clean` 归一后再匹配」** ⇒ 归一器落在**共享层 `service/common/http/`**（A3 轴定义的落点即 `protection/* + http/*`）⇒ **不得**按域各写一份（article-api 那份是历史遗留的域内副本）<br>⇒ 归一语义 = Go `path/clean.go`，且必须排在读路径的 Filter 之前（article 现取 `HIGHEST_PRECEDENCE+2`，须在 `GoStyleTraceparentFilter(+3)` 之前）<br>⇒ 连带：`GoStyleTraceparentFilter.java:173-176` 的注释把 `GoStylePathNormalizingFilter` 当**共享层**类讨论 ⇒ 归一器进共享层后该引用自动成立；否则须改成「article-api 私有、共享层没有」 |
| **⑥ 影响面** | 共享层 `service/common/http/*` ⇒ 被 **10 个 api 全依赖**；**实际缺口 9 个**（article 已有等价实现）⇒ 跨域引用域数 = **9** |
| **⑦ 是否阻塞本波** | **否**（不阻塞本波台账；阻塞 A3 结案） |

> 📌 **共享层落点（按**符号**记，不钉行号）** —— G4 树 `182fc32` 的行号**合并后会位移** ⇒ 本域台账只记符号，避免合并后行号失准：
> - **FB-01**：`service/common/.../http/GoStylePathNormalizingFilter.java`（`@Order(HIGHEST_PRECEDENCE+2)` + `goPathClean`）＋ `GoStylePathNormalizingAutoConfiguration.java`（唯一注册点）＋ `AutoConfiguration.imports` +1 行
> - **FB-02**：`service/common/.../http/GoStyleFormLimitMode.java`（`SEMANTIC_REACHABILITY` ＋ `REGISTERED_DEVIATIONS` ＋ `parse` 行为不变）＋ `GoStyleTomcatParameterLimitCustomizer.java` ＋ `GoStyleFormValues.java`（「开不开」与「拒成什么形状」两条独立的轴）
> - **FB-03**：`service/common/.../protection/GoStyleRpcProtectionChain.java`（`Layered` ＋ `Composition` ＋ `compose(props)` ＋ `layerNames(chain)` ＋ `build` 改为消费 `compose` ⇒ 条件与顺序逐字不变）
> ⇒ **共享层侧回归（G4 自测）**：全量 `mvn -B clean verify`（仓库根，**不是**只跑「看起来受影响」的域）⇒ **52 个模块 / 2442 条 / 0 failures / 0 errors / 4 skipped**（4 条 skip 全是 favorite 的对拍探针自动 skip）/ 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：上行的 **2442** 是**第六次**全量的数字 ⇒ **第七次全量 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ 多出的 15 条 = G4 后续落的守门（含 `ObservationPoolSelfHealingGateTest` 等）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径） ⇒ 变异验证：FB-01 撤掉归一层的自动配置注册 ⇒ **4 failures**；FB-03 摘掉降载层的 `Layered` 标记 ⇒ **5 failures** ⇒ 均红在目标断言
> ⇒ ⚠️ **本域的三域回归与之重叠过**（我 04:45 看到 G4 的 surefire 进程 ⇒ 机器负载高、我的回归变慢）⇒ 已在台账登记「错峰」共识
> ✅ **G4 回执（2026-10-27 · `182fc32`）**：共享层已落码 `GoStylePathNormalizingFilter`（**字节级逐字移植** Go `path.Clean`）+ `GoStylePathNormalizingAutoConfiguration`（唯一注册点）⇒ 🟢 **Go 侧已 L4 活体坐实**（真 go-zero rest server + curl：5 种路径变体全 200）⇒ **共享层那半已闭环**<br>⚠️ **但仍未合入本树** ⇒ **本域 A3 暂不整体升 ✅** ⇒ 合并后本域只需跑**真 Tomcat 探针**复核即可<br>⚠️ **跨域协调项（G3 的活）**：article-api 的**域内副本** `service/article/article-api/.../config/GoStylePathNormalizingFilter.java` 仍在 ⇒ G4 用「按 Bean 名去重」保证 article-api 的守门仍绿 ⇒ **由 G3 删**（不在本域写权内） |

### 🆕 FB-02 · A3 form 闸门的开关判据缺「语义可达性」维度（本域多出一条 Go 没有的拒绝）

| 项 | 内容 |
|---|---|
| **① 现象** | `POST /task/get?a=1&a=2…（2049 个非空值）`、`?a=%zz`、`?a=1;b=2` ⇒ **RAG 返 400 text/plain**，**Go 返 200**（业务正常执行）。Go 的 2048 / 10000 两道上限在 task 域**不可达**（不调 `httpx.Parse`），RAG 却强制执行。⚠️ 本域 `max-http-request-header-size: 1028KB`（`task-api/.../application.yml:88`）⇒ 该窗口**确实可达** |
| **② Go 侧出处** | `Sea-RideTheWind_golang/service/task/api/internal/handler/task/gettaskhandler.go:19`（`json.NewDecoder(r.Body)` + `UseNumber()`，**不调 `httpx.Parse`**）<br>⇒ `service/task/api/**` 内 `httpx.Parse` / `ParseForm` / `GetFormValues` **命中数 = 0**（本轮实测）<br>⇒ `service/task/api/internal/handler/routes.go:16-25`（全域**唯一**一条路由 `POST /task/get`）⇒ **Go 侧 `r.Form` 从不被求值**<br>⇒ `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）与 `:48-53` 的计数**均不可达**；`:23` 的 `r.ParseForm()` 亦不执行 ⇒ `net/url` 的 10000 段上限同样不可达 |
| **③ RAG 侧现状** | `service/common/src/main/java/com/rag/common/http/GoStyleFormValueLimitInterceptor.java:86`（`goParseViolation(request.getQueryString())`）→ `:95` RAW 分支写 400<br>⇒ 判据 `GoStyleFormValues.java:220-240`（① 10000 段 `:225` ② net/url 解析错 `:230-233` ③ 2048 非空值 `:235-238`）<br>⇒ 注册 `GoStyleFormLimitWebMvcConfig.java:41-43`；模式来源 `GoStyleFormLimitMode.java:56,81-97`；本域取值 `service/task/task-api/src/main/resources/application.yml:176-178`（`mode: raw`） |
| **④ 证据强度** | **L1**（两侧源码逐字对照；Go 侧 0 命中 + RAG 侧消费点均已实测）；**未跑双侧活体** ⇒ 不得标 L4 |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「该域的 form 闸门按『Go 侧是否真的解析 form/query』决定开否」**：Go 侧 handler 不调 `httpx.Parse` ⇒ **该域的 2048/10000 上限在 Go 侧不可达** ⇒ **Y = 该域闸门不生效**（等价 `mode: off`）⇒ RAG 与 Go 同为「不因 query 而拒」<br>⇒ **共享层当前把 `off` 的唯一依据写成「favorite 不在写权范围」**（`GoStyleFormLimitMode.java:41-49`）⇒ **须把判据从「写权范围」扩成「语义可达性」**，否则下一个裸 decoder 域会重复踩<br>⇒ ⚠️ **域侧那半由 G2 自修**（task-api 一行 yaml + 3 条守门 ⇒ D-1）⇒ 本单只含**共享层的判据**部分 |
| **⑥ 影响面** | 判据与 `GoStyleFormValues` / `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer` / `GoStyleFormLimitMode` 全部在 `service/common/http/*` ⇒ 被 **10 个 api 全依赖**<br>⇒ **当前实际受影响的域** = 用裸 decoder（不调 `httpx.Parse`）的 api —— **task（本单）**；其余 9 域受共享层改动的连带影响 ⇒ 跨域引用域数 = **10** |
| **⑦ 是否阻塞本波** | **否**（不阻塞本波台账）；但**阻塞 task-api 的 A3 结案**（A3 在此之前只能记 🟡） |

> 📌 **共享层落点（按**符号**记，不钉行号）** —— G4 树 `182fc32` 的行号**合并后会位移** ⇒ 本域台账只记符号，避免合并后行号失准：
> - **FB-01**：`service/common/.../http/GoStylePathNormalizingFilter.java`（`@Order(HIGHEST_PRECEDENCE+2)` + `goPathClean`）＋ `GoStylePathNormalizingAutoConfiguration.java`（唯一注册点）＋ `AutoConfiguration.imports` +1 行
> - **FB-02**：`service/common/.../http/GoStyleFormLimitMode.java`（`SEMANTIC_REACHABILITY` ＋ `REGISTERED_DEVIATIONS` ＋ `parse` 行为不变）＋ `GoStyleTomcatParameterLimitCustomizer.java` ＋ `GoStyleFormValues.java`（「开不开」与「拒成什么形状」两条独立的轴）
> - **FB-03**：`service/common/.../protection/GoStyleRpcProtectionChain.java`（`Layered` ＋ `Composition` ＋ `compose(props)` ＋ `layerNames(chain)` ＋ `build` 改为消费 `compose` ⇒ 条件与顺序逐字不变）
> ⇒ **共享层侧回归（G4 自测）**：全量 `mvn -B clean verify`（仓库根，**不是**只跑「看起来受影响」的域）⇒ **52 个模块 / 2442 条 / 0 failures / 0 errors / 4 skipped**（4 条 skip 全是 favorite 的对拍探针自动 skip）/ 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：上行的 **2442** 是**第六次**全量的数字 ⇒ **第七次全量 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ 多出的 15 条 = G4 后续落的守门（含 `ObservationPoolSelfHealingGateTest` 等）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径） ⇒ 变异验证：FB-01 撤掉归一层的自动配置注册 ⇒ **4 failures**；FB-03 摘掉降载层的 `Layered` 标记 ⇒ **5 failures** ⇒ 均红在目标断言
> ⇒ ⚠️ **本域的三域回归与之重叠过**（我 04:45 看到 G4 的 surefire 进程 ⇒ 机器负载高、我的回归变慢）⇒ 已在台账登记「错峰」共识
> ✅ **G4 回执（2026-10-27 · `182fc32`）**：**本单已闭环**（零行为改动）—— 共享层把判据从「写权范围」改成「**语义可达性**」+ 落一张 **10 个 api 域逐域取证表**（每行带 Go 侧 `文件:行号` / 命中数）⇒ 判据**只有一份**且可被机器核对 ⇒ 「下一个裸 decoder 域不会重复踩」有了机制<br>🟢 **本域那半（task 的 `mode: "off"`）已在 `4786eb5` 落码**，且**与共享层表里的 task 行（`Mode.OFF`）同向** ⇒ 两侧一致、无需再动<br>⚠️ **favorite 的偏离已由共享层登记**（`REGISTERED_DEVIATIONS`，写清可观察差异：Go 400+text/plain vs RAG 400+Tomcat HTML，且 Tomcat 计**参数总数**、Go 计**非空值**）⇒ **不在本域写权内 ⇒ 本域不动** |

### 🆕 FB-03 · A2 保护链缺「层构成」只读查询入口 ⇒ 域级守门无法分别断言 breaker / shedding

| 项 | 内容 |
|---|---|
| **① 现象** | `GoStyleRpcProtectionChain.build` 的链构成由 **两个条件分支**决定（`:65` shedding 需 `enabled && maxCpuPermille>0`；`:72` breaker 需 `enabled`）⇒ **域级守门无法分别断言「链上有没有 shedding」**：域内测试若关掉降载（`TaskProtectionChainWiringTest.java:80-81` 就是这么干的）就断了断言 ⇒ **守门名义上写「breaker/shedding」，实际只证明 breaker**（本轮新发现） |
| **② Go 侧出处** | `go-zero@v1.10.2/zrpc/server.go:144-167`（Recover `:148-150` / Breaker `:157-159` / Shedding `:160-163` / Timeout `:164-167`）⇒ Go 侧**四个拦截器无条件按序装上**（各自由 `go-zero@v1.10.2/zrpc/internal/config.go:21,25` 与 `go-zero@v1.10.2/zrpc/config.go:47` 的默认值决定是否**生效**，但**都在链上**） |
| **③ RAG 侧现状** | `service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java:61-78`（`build`）—— 链构成**不可从外部查询**；域级守门只能靠「关掉降载 / 关掉熔断」来间接判断 ⇒ 断言被迫退化 |
| **④ 证据强度** | **L1**（两侧源码逐字对照） |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「保护链的每一层是否在链上，可被域级守门**直接**断言」** ⇒ 共享层暴露一个**只读**的「链层构成」查询入口（例如 `GoStyleRpcProtectionChain` 暴露实际构造出的拦截器列表 / 层名）⇒ 各域守门据此**分别**断言 breaker 与 shedding 两层<br>⇒ ⚠️ **零行为改动**（只读入口，不改链的装配逻辑）⇒ 不影响任何域的运行时行为 |
| **⑥ 影响面** | 共享层 `service/common/protection/*` ⇒ 被 **10 个 rpc 组件**依赖（`service/common` 依赖度数 20）⇒ 跨域引用域数 = **9**（全部 rpc 域） |
| **⑦ 是否阻塞本波** | **否**（属守门能力缺口；task 域的 shedding 断言可先按 D-2 的最小版做） |

> 📌 **共享层落点（按**符号**记，不钉行号）** —— G4 树 `182fc32` 的行号**合并后会位移** ⇒ 本域台账只记符号，避免合并后行号失准：
> - **FB-01**：`service/common/.../http/GoStylePathNormalizingFilter.java`（`@Order(HIGHEST_PRECEDENCE+2)` + `goPathClean`）＋ `GoStylePathNormalizingAutoConfiguration.java`（唯一注册点）＋ `AutoConfiguration.imports` +1 行
> - **FB-02**：`service/common/.../http/GoStyleFormLimitMode.java`（`SEMANTIC_REACHABILITY` ＋ `REGISTERED_DEVIATIONS` ＋ `parse` 行为不变）＋ `GoStyleTomcatParameterLimitCustomizer.java` ＋ `GoStyleFormValues.java`（「开不开」与「拒成什么形状」两条独立的轴）
> - **FB-03**：`service/common/.../protection/GoStyleRpcProtectionChain.java`（`Layered` ＋ `Composition` ＋ `compose(props)` ＋ `layerNames(chain)` ＋ `build` 改为消费 `compose` ⇒ 条件与顺序逐字不变）
> ⇒ **共享层侧回归（G4 自测）**：全量 `mvn -B clean verify`（仓库根，**不是**只跑「看起来受影响」的域）⇒ **52 个模块 / 2442 条 / 0 failures / 0 errors / 4 skipped**（4 条 skip 全是 favorite 的对拍探针自动 skip）/ 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：上行的 **2442** 是**第六次**全量的数字 ⇒ **第七次全量 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ 多出的 15 条 = G4 后续落的守门（含 `ObservationPoolSelfHealingGateTest` 等）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径） ⇒ 变异验证：FB-01 撤掉归一层的自动配置注册 ⇒ **4 failures**；FB-03 摘掉降载层的 `Layered` 标记 ⇒ **5 failures** ⇒ 均红在目标断言
> ⇒ ⚠️ **本域的三域回归与之重叠过**（我 04:45 看到 G4 的 surefire 进程 ⇒ 机器负载高、我的回归变慢）⇒ 已在台账登记「错峰」共识
> ✅ **G4 回执（2026-10-27 · `182fc32`）**：共享层已落码 `Layered` 标记 + `Composition` + `compose(props)`（**唯一**构成计算点，`build` 内部消费它）+ `layerNames(chain)`（观测已构造出的链）⇒ **零行为改动**（条件与顺序逐字不变）⇒ 配套守门 7 条，变异（摘掉降载层的 `Layered` 标记）**5 failures**<br>⚠️ **本树尚未合入** ⇒ 本域守门 D-2 改用**等价**判据（「包装层归属」，见 §2/A2）⇒ **合并后**按 §5.3e 重跑收口回归时**升级为 `layerNames(chain)` 断言** |

### 🆕 FB-05 · A3 form 闸门开关值 `off` 在 YAML 里**写不出来**（YAML 1.1 把 `off` 解析成布尔 false）

> ⚠️ **本单是我落 task 域 D-1 时踩出来的**，不是推演：**未加引号的 `mode: off` 让 task-api 的上下文直接起不来**。

| 项 | 内容 |
|---|---|
| **① 现象** | 共享层 `GoStyleFormLimitMode.parse` 的合法取值是 `off` / `raw` / `envelope`（文档/注释/各域 yaml 说明都这么写）⇒ **但 YAML 1.1（SnakeYAML）把未加引号的 `off` 解析成布尔 `false`** ⇒ `@Value("${rag.http.go-form-limit.mode}")` 拿到字符串 **"false"** ⇒ `parse` 落 default ⇒ 抛 `IllegalArgumentException` ⇒ **构造期 BeanCreationException ⇒ 进程起不来** ⇒ ⇒ **文档里那个合法取值 `off`，在 YAML 里根本写不出来**（除非加引号） |
| **② Go 侧出处** | Go 侧**没有这个开关**（`go-zero@v1.10.2/rest/config.go` 无 form-limit 旋钮；`maxFormParamCount = 2048` 是 `go-zero@v1.10.2/rest/httpx/util.go:15` 的常量）⇒ OFF 在 Go 侧的等价物就是「**什么都不做**」⇒ ⇒ **RAG 的 OFF 语义本身是对的，问题只在「配置值 → 代码」的传递** |
| **③ RAG 侧现状** | `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:56`（`PROPERTY`）· `:82-98`（`parse`：只认 `off`/`raw`/`envelope`，其余抛）· `GoStyleFormValueLimitInterceptor.java:69-73`（构造期 `@Value` + `parse`）· `GoStyleTomcatParameterLimitCustomizer.java:99-104`（同一把尺子） |
| **④ 证据强度** | **L4（真上下文启动失败实测 · 2026-10-27）**：`非法的 rag.http.go-form-limit.mode 值：false（只允许 off / raw / envelope）` ⇒ **值是 `false` 而不是 `off`** ⇒ YAML 1.1 布尔化的实证；加引号写 `mode: "off"` ⇒ 同一份代码、同一份上下文 ⇒ 正常启动（已实测） |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「域侧按文档写 `mode: off` 就能关闸门，且不会把进程搞挂」**（⚠️ 此处是 **G4 的原始建议措辞（2026-10-27 早）** ⇒ 🆕 **FB-05 落码后的当前口径**：**两种写法都 work** —— `mode: "off"`（**推荐**，加引号）或 `mode: false`；⇒ **本域用的是加引号那种**（`application.yml:213` 取值 + `:209` 注释「域内写法的正确形式是 `mode: "off"`」）⇒ ⇒ **加引号在改动前后都正确**）。两条都建议做：① `parse` 额外接受 **`"false"`**（YAML 1.1 的 `off` ⇒ `false`）⚠️ **只加这一个别名**、不要放宽成「任意非法值都当 OFF」（那会重新引入「静默兜底 = 以为装上了其实没装」，`parse` 的 javadoc 明确反对）② 在 `PROPERTY` 的 javadoc / 类注释里注明「YAML 里必须加引号」 |
| **⑥ 影响面** | 共享层 `GoStyleFormLimitMode` ⇒ 被 **10 个 api 全依赖**（两处消费）⇒ **当前受害域 = 0**（全仓 9 个域**无任何域**写字面量 `off` ⇒ favorite 域靠**「整段不写」**拿到 OFF ⇒ 行为零变化）⇒ **未来受害面 = 任何想关闸门的域** |
| **⑦ 是否阻塞本波** | **否**（G2 的 task-api 已用 `mode: "off"` 绕开 ⇒ 本轮不依赖 G4 这次改动 ⇒ 可独立提交）；**但建议尽快修** —— 否则下一个「想关闸门」的域会直接撞上启动失败，且错误信息里的「值：false」极易被误读成「配置写错了值」 |
| **⑧ 🟢 G4 最终回执（2026-10-27 · `c15dfeb` + `e7d14f3`）—— 本单已闭环** | **共享层已落码**：① `GoStyleFormLimitMode.parse` **新增精确别名 `"false"` ⇒ OFF**（**只加这一个**、不放宽成「任意非法值都当 OFF」—— 守门 ⑤ 钉住 `bogus`/`true`/`on`/`yes`/`no`/`n` **仍抛异常**）② `PROPERTY` javadoc 写明「YAML 里 `off` **必须加引号**」＋ YAML 1.1 布尔字面量全表 ③ 异常消息改成自解释（`只允许 off \| false \| raw \| envelope；注意 YAML 里写 off 必须加引号`）④ **新增守门 5 条**（含「生产同款 `YamlPropertySourceLoader` 端到端」＋「真 servlet 上下文 + 未加引号的 yaml 也能起」）⑤ 共享层第九次全量 = **52 模块 / 2470 条 / 0 failures / 0 errors / 4 skipped / 20:22 min**<br>⇒ ⇒ **本单销号**（共享层那半已闭环 ⇒ 待合并后由整合者统一更新共享索引）<br>⇒ ⇒ **对本域零影响**：本域写的是 `mode: "off"`（**带引号**）⇒ ⇒ **加引号在改动前后都是正确写法** ⇒ **不依赖 G4 这次改动** ⇒ 合并前后都安全<br>⇒ ⚠️ **合并后仍要复跑本域 5 条 form 闸门守门**（`TaskApiFormLimitReachabilityTest`）⇒ 确认 `parse` 的新分支**没有改变 OFF 分支的行为**（G4 自测：⑤ 钉住非法值仍抛、`off`/`raw`/`envelope`/null/空串**原语义一字未改** ⇒ 预计全绿 ⇒ **但「预计」不算数 ⇒ 合并后实测**）<br>⇒ 🟢 **G4 对本单前提的独立核实（⑥ 的「当前受害域 = 0」）**：9 个 api 实测 ⇒ `article-api`/`user-api`/`admin-api`/**`task-api`** = `raw` · `comment-api`/`follow-api`/`hot-api`/`like-api`/`message-api` = `envelope` · **`favorite-api` 无 `go-form-limit` 段**（⇒ 缺省 OFF ⇒ 零影响）⇒ **全仓唯一的「未加引号 `mode: off`」是 G4 守门自己那段测试 yaml** ⇒ 与本域取证一致 |
| **⑨ 🟢 G4 ⑧「只有你改过的那份 task-api 需要订正」—— 对我树**不适用** ⇒ 本域零待订正** | G4 ⑧ 写「你在改值时把注释写成了 `mode: off`」⇒ **我树实况不是这样**（本轮实测）：<br>· `service/task/task-api/src/main/resources/application.yml:213` **取值** = `mode: "off"`（**带引号**）<br>· 同文件 `:209` **注释** = 「域内写法的正确形式是 `mode: "off"`（带引号 ⇒ 保持字符串）」<br>· `grep -rn "mode: off" service/ --include=*.yml --include=*.java`（排除 `target/`）⇒ **零命中** ⇒ ⇒ **我域源码/配置里不存在「注释裸写 `mode: off`」这件事** ⇒ ⇒ **本域无待订正项**（避免整合者按 G4 ⑧ 再排一次工）<br>⇒ ⚠️ 唯一残留：台账**叙述**里出现过「未加引号的 `mode: off`」这种措辞（描述**病症**用，非配置片段）⇒ 已在本表 ⑤ 加注「当前口径（两种写法都 work · 推荐加引号）」 |

### 🆕 FB-06 · A1「Redis 调用不感知 deadline」在 task 域**成立**（global 台账 **E66**）⇒ 附本域调用点清单

> ⚠️ **本单不是推演**：G4 在共享层知会里点名「E66 需要各域 owner 参与」⇒ G2 逐条核了本域的 Redis 调用点。
> 本单**只登记**（落点在 `service/common` = G4 写权）⇒ 域侧**不落码**。

| 项 | 内容 |
|---|---|
| **① 现象** | 本域 Redis 调用**不感知请求 deadline** ⇒ deadline 到点后缓存读写**照跑不误**（含写）⇒ 与 Go 的 ctx-aware 语义不同形 |
| **② Go 侧出处（消费点）** | ① `service/task/rpc/internal/logic/get_task_logic.go:49` `l.svcCtx.Rdb.HGetAll(ctx, pkey).Result()` —— `ctx := l.ctx`（**gRPC 请求 ctx，带 deadline**）⇒ 到点即止<br>② 同文件 `:73` `pipe := l.svcCtx.Rdb.Pipeline()` ＋ `:78` `_ = l.svcCtx.Rdb.Expire(ctx, pkey, 30*time.Second).Err()` ⇒ **回填也带 ctx** |
| **③ RAG 侧现状（本域）** | ① `service/task/task-rpc/.../service/TaskDomainService.java:153` `redis.opsForHash().entries(cacheKey)`（对应 Go 的 HGetAll）<br>② 同文件 `:180` `RedisPipelines.execQuietly(redis, ops -> { ops.opsForHash().putAll(...); ops.expire(...); })`（回填）<br>③ 同文件 `:193` `redis.expire(cacheKey, ttl)`<br>⇒ **全部无 ctx / 无 deadline 载体** ⇒ 落在 `service/common` 的 `GoStyleRequestDeadline` / `RpcInterceptor.DeadlineState` **都查不到**（`grep` 实测：这两个载体在 rpc 侧的绑定点是 `RpcInterceptor:364/455` 的 `DeadlineState.bindToCurrent` ⇒ **本域可以查 `DeadlineState.remainingMillis()`** ⇒ 但**当前一处都没查**） |
| **④ 证据强度** | **L1（源码级 · 两侧逐条对照）** ⚠️ **未经双侧活体验证**（global 台账 E66 现有强度同此）⇒ 本域**不主张** L4 |
| **⑤ 建议修法（共享层先落 · 域侧再跟）** | 共享层：提供「当前请求剩余 deadline」的统一载体（rpc 侧 = `RpcInterceptor.DeadlineState`，它**已经在** handler 线程绑定 ⇒ **零新增机制**）<br>域侧（本域）：在 Redis 调用点把 `DeadlineState.remainingMillis()` 绑进命令超时<br>⚠️ **落点顺序**：若 G4 在共享层做「Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline」（G4 知会里说的路线）⇒ **域侧零改动** ⇒ 本域只需复核；若是「各域调用点自己查载体」⇒ 本域需改 3 处 |
| **⑥ 影响面** | 本域 = **task-rpc 的 3 处请求路径调用点**（上表③①②③）<br>⚠️ **MQ sink 的 Redis 调用**（`UserLikeProgressSink` / `ArticleLikeProgressSink` / `TaskCountStore` / `TaskRewardPublisher`）**不属 E66 域** —— Go 侧它们的 ctx 是 **Kafka 消费 ctx**（`service/task/rpc/internal/sink/like_sink.go:110-128` 的 `loop(ctx)`）⇒ **本来就没有请求 deadline** ⇒ 两侧同形 ⇒ **不修**（避免造出「比 Go 更严」的新漂移） |
| **⑦ 是否阻塞本波** | **否**（G4 已明确「本轮不落码、提请立项」⇒ 本域**只登记**）<br>⚠️ 但**本域已单独修掉一条相邻的、更严重的漂移**（Redis **命令/连接超时**未配 ⇒ 断连挂 60s）⇒ 见 [DRIFT-TASK-RPC-007](rpc.md#✅-drift-task-rpc-007--redis-命令超时未配--落-lettuce-默认-60s--断连时命令挂-60sgo-是-17s-快速失败) |

## 4·五、🟢 **G4 正式回执（4 项规格）· 2026-10-27 · `align/g4-service-common` @ `5564c27`**

> 📌 **本节是 G4 那张「4 项规格」回执的域侧归档**（AGENTS.md §5.3b：产出**必写回**本域 §4）。
> ⚠️ **本节不销任何单** —— 销号须**合并后**按 §5.3e 重跑收口回归才成立（见末「合并后必做动作」）。

### ① HEAD

`align/g4-service-common` @ **`5564c27`**（基线 `a282012` 之上 **30** 提交 · 工作区干净 · **未 push**）

### ② 改动文件清单（逐个 `文件:行号`）

- `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:92` ＋ 类级 javadoc `:40-90`
- `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:50` ＋ `:60`
- `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:88` / `:122` / `:137` / `:181-182`
- `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:163` / `:175` / `:209`
- `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:70-84` / `:106-127` / `:129`
- `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:31`
- 守门新增 5 文件：`…/spring/HttpPoolShutdownWithoutServletGateTest.java` · `…/spring/HttpShutdownProbeWithoutServlet.java` ·
  `…/precedence/GoStyleRedisClientOptionsPrecedenceGateTest.java` · `…/precedence/DomainLikeRedisDisconnectedBehaviorConfig.java` ·
  `…/observability/ObservationPoolSelfHealingGateTest.java`
- 台账：`docs/DRIFT/global/vs-common.md`

### ③ 触及的 A 轴 ＋ Go 出处

- **A3**：`HttpMiddleware.java:1089`（Go 出处 `service/common/observability/http.go`）；
  `ObservabilityLifecycle` 的 Go 出处 = `service/follow/rpc/follow.go:45` ＋ `:3-21`（零 web 类型）
- **A1**：`RpcInterceptor.java:623`（Go 出处 `service/common/observability/rpc.go:38`）
- **A4**：`SqlTimeoutStatusTranslator.java:31`（Go 出处 `getuserlistlogic.go:44-48` · `selectavatarlogic.go:59-60` ·
  `errmsg/errmsg.go:6`/`:41` · `observability.go:146-158`）

### ④ 回归口径与结果

`mvn -B clean verify`（全量）⇒ **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块** · **2457 条** ·
**failures 0 / errors 0 / skipped 4** · **20:28 min**
⇒ 双侧探针同批次复跑：`run-parity.sh` **EXIT=0 · 56 例**（一致 54 / 不一致 0 / 已签字偏离 2）·
`run-rpc-timeout-parity.sh` **EXIT=0 · 3 例**
⇒ 三门禁同批次复跑：**0 / 0 / 0**

⚠️ **口径提醒**：**2457 条是 G4 树（52 模块）的数字** ⇒ **不是**本域的数字
⇒ 本域三域六模块的数字见本文件各自的「测试」行（口径 = `mvn test`）⇒ **两者不可混用**。

### 🟢 我域三张单 + 澄清的逐条状态

| 单 | 状态 | 落码 commit |
|---|---|---|
| **FB-01**（A3 路径归一进共享层） | 🟢 **已落** | `9690b04` |
| **FB-02**（form 闸门判据改「语义可达性」） | 🟢 **已落** | `182fc32` |
| **FB-03**（保护链「层构成」只读入口） | 🟢 **已落** | `182fc32` |
| **澄清第 1 步**（form 判据措辞） | 🟢 **已落且与本域要求逐条一致** | `182fc32` |
| **E66**（Redis 写不绑 deadline） | 🔴 **仍 OPEN · 本轮转立项** | — |

⇒ **G4 未越界**（按本域给的边界）：共享层**没有**为 task 写特例 · **没有**碰 favorite 的任何东西 ·
本域建议项 3 的对拍脚本落点 = `tools/difftest/**`（无 lane 拥有的共享面）⇒ **只登记，不擅自改** ✅

### 📊 **G4 第八次全量口径**（2026-10-27 · 09:57:42 → 10:18:03）· G2 **用 surefire XML 独立复核**

| 项 | 值 |
|---|---|
| 结果 | **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块** · **2461 条** · **0 失败 / 0 errors / 4 skipped** · **20:21 min** |
| 本域在 **G4 树**里的口径 | `task-api` **90** · `task-rpc` **114** ⇒ **本域 2 模块 = 204**（全树逐项可复算：204 + message 域 193 + like 域 195 = **592**） |

⚠️ **口径隔离**：**204 = G4 树口径** ⇒ **不含**本域尚未合入的守门（D-1 的 form 闸门守门 5 条 · Redis 超时守门 **6 条**（🆕 2026-10-27 本轮 +2：Redis **连接池** ⑤⑥）等）⇒ **本域自己的口径另计** ⇒ 两个数不可混用。
⚠️ **取数字只用 surefire 的 `TEST-*.xml`**（**不要**按日志 `[INFO] Building` 分段聚合 ⇒ 本轮据此把 `task-rpc` 算错成 110，真实 114）。
⇒ 完整明细与判据见 [`like/vs-common.md` §4·七](../like/vs-common.md)（本轮唯一出处）。

### 📥 **FB-04 对本域 = N/A**（`853767b` · 2026-10-27 · G2 **独立取证**，未采信转述）

| 项 | 独立取证 |
|---|---|
| **Go 侧** | `grep -rn "SpanFromContext" service/task --include=*.go`（排除 `_test.go`）= **0 命中** ⇒ 本域 Go 侧**不依赖**「业务代码取当前 span」语义 |
| **RAG 侧** | `grep -rn "Span.current()" service/task`（排除 `target/` 与测试）= **0 命中** ⇒ ⇒ **两侧都是 0** ⇒ RAG **没有凭空多出** span 用法 ⇒ **无漂移** |
| **结论** | FB-04（`onHalfClose` 里把 `otelCtx.makeCurrent()` 的 scope 包住 handler 执行期）**只影响会读「当前 span」的域** ⇒ ⇒ 本域 **N/A** ⇒ 合并后**零行为变更** · **域侧零改动** · **无待办** |
| **HTTP 侧也不涉及** | G4 回执第 ⑤ 条第 3 点说「HTTP 侧 `HttpMiddleware.java:332` **已有** `makeCurrent()` ⇒ 本次修法**只落 gRPC 侧**」⇒ ⚠️ 但**本域 api 侧 `Span.current()` = 0 命中** ⇒ ⇒ 那条 HTTP 侧能力**对本域同样无作用** ⇒ **仍是 N/A** |

### 🔴 合并后我域**必做**动作（收口回归 · §5.3e）

⚠️ **本域当前仍用「等价判据」顶着**（因为 G4 的落码**未合入本树**）⇒ 合并后必须：

1. **复跑本域 3 处 `DEADLINE_EXCEEDED` 断言**（`LikeRpcGrpcInterceptorWiringTest:178` ·
   `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4）
   ⇒ 原因 = G4 `7488b3d` 修的「**进程级静态池**终止即永久」缺陷**本域同构**
   （`ObservabilityLifecycle` 被本域三个 rpc 组件全部 `@Import` ⇒ 每个 Spring 上下文关闭都毒化池）
   ⇒ 合并后池**自愈**（`AtomicReference` + CAS 重建）⇒ 这三处应从「**侥幸绿**」变「**真绿**」
   ⇒ ⚠️ **若仍红 ⇒ 不是新回归，是毒化未被覆盖 ⇒ 直接找 G4**<br>   🔴 **合并顺序硬约束（G1 预告 · G2 独立核实，2026-10-27）**：`3b573fb`（修「关闭路径触发 web 类加载」）**不能单独合入** ⇒ 它让 `ObservabilityLifecycle.shutdown()` **真的执行** ⇒ 若 `7488b3d`（池自愈）**未同时在位** ⇒ 池「终止即永久」⇒ 同模块**后续** Spring 上下文被毒化 ⇒ 拦截器返回 **`INTERNAL`** 而非 `DEADLINE_EXCEEDED` ⇒ **由绿转红**<br>   ⇒ ⇒ **我域实测暴露面**：task-rpc **4** 个 `@SpringBootTest` 上下文（like-rpc 9 / message-rpc 10） ⇒ ⇒ **本域三个 rpc 组件全部同构**<br>   ⇒ ⇒ ⚠️ **G4 树自身的落地顺序与 G1 给的「分批顺序」相反**（G2 实测 `git log` 倒序）：`3b573fb` 在**第 21 位（更早）**、`7488b3d` 在**第 17 位（更晚）**⇒ ⇒ **整批合并 G4 分支 ⇒ 最终态两条都在 ⇒ 约束天然满足** ⇒ ⇒ **但「只挑部分 commit」⇒ 必须带上 `7488b3d`**，否则本条会红 ⇒ ⇒ **别把红判成「域侧回归」**<br>   ⇒ ⇒ ⚠️ **另有一层「绿是因为没走到」**：修 `3b573fb` 之前，纯 gRPC 进程的 `@PreDestroy` 先抛 `NoClassDefFoundError` ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 静态池**侥幸存活** ⇒ ⇒ **前几轮全量一直绿不是「本来就对」，是缺陷把保护罩挡住了**（G1 提出 · G2 采信）<br>   ⇒ ⇒ ⚠️ **订正本条的类名**：本条原写「3 处 `LikeRpcGrpcInterceptorWiringTest:178` · `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4」⇒ **那是 like 域的类名，被误抄进本域** ⇒ ⇒ **task 域的对应断言在 `TaskGrpcInterceptorWiringTest:155`**（`assertEquals(Status.Code.DEADLINE_EXCEEDED, …)`）⇒ ⇒ **类名须按域重新定位，引用前先核**<br>   🟡 **2026-10-27 12:5x 实测撞过一次红 · 根因已定位 · 不是域侧回归**：本轮 8 模块域级回归跑到 message-rpc 时 `MessageRpcTimeoutGateTest` 报 **`Tests run: 2, Failures: 0, Errors: 2`** ⇒ 最内层根因 = `java.net.BindException: Address already in use` on **端口 64118** ⇒ ⇒ 64118 落在 **OS 临时段 49152–65535**（**不是**本域专用段 21700–21799）⇒ ⇒ **根因 = 两个 lane 同时跑 maven ⇒ 撞端口**（`ps` 当时显示 **G1 树**在跑）⇒ ⇒ **独占重跑 message-rpc = 84 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** ⇒ ⇒ **确认：不是代码缺陷、不是域侧回归、也不是「静态池毒化」那条路径**<br>   ⇒ ⇒ **教训（本域留档）**：**「起跑前查一次 ps」是不够的** ⇒ ⇒ **别的 lane 可以在你跑的过程中起 maven** ⇒ ⇒ ⇒ **必须双向错峰**：① 起跑前查真实占用者（已做）② **我也要给别人发错峰知会**（⚠️ **本轮之前我只给 G4 发过，没给 G1 发 ⇒ 这次撞车就是那个漏洞的代价**）③ 撞车后**先看最内层 `Caused by`**（端口 / 绑定 / 超时 ⇒ 环境；断言 / 状态码 ⇒ 代码）⇒ ⇒ **别看到「由绿转红」就判回归** |
2. **复核共享层的 `REJECT_COMMANDS` 在本域生效**（`GoStyleRedisClientAutoConfiguration`）
   ⇒ 本域 like-rpc / task-rpc **自带** `spring-boot-starter-data-redis` ⇒ `@ConditionalOnClass` 命中
   ⇒ 且本域**零域内副本** ⇒ **共享层那份生效** ⇒ 断连即拒单 ⇒ 不再撞 `rpc-timeout: 2s`
   - ⚠️ **口径订正（2026-10-27 · G2 自查）**：本条原先写「断连即拒单（**实测 1ms**）」⇒ **那个 1ms 是我域上轮探针在「手动配 `REJECT_COMMANDS`」条件下**测得的（[`task/rpc.md`](rpc.md) 对拍表已标注「G4 共享层落码后」）⇒ ⇒ 它证明的是「**该配置项有效**」，**不是**「**共享层那份已在本域生效**」—— **共享层那份尚未合入本树** ⇒ ⇒ 本条的**真实验收** = 合并后在本域**不配任何域内配置**、让共享层那份生效 ⇒ 再跑一次**断连活体** ⇒ 量首条命令耗时。
   - ⚠️ **探针配方已成跨域共识**（G3 2026-10-27 确认会用同款配方补它域的断连活体 ⇒ 勿各自另造）：**先连上 → 静默等待（期间不发命令，否则 watchdog 重连会掩盖排队窗口）→ 外部 `docker stop`（优雅 FIN，不是 `kill` 的 RST）→ 量第一条命令挂多久**。
   ⇒ ⚠️ **顺带确认「遮蔽契约」**：本域**不得**新建 `RedisDisconnectedBehaviorConfig` 之类的域内副本
   （那会遮蔽共享层 ⇒ E66 等后续修正在本域**静默失效**）
   - 🆕 **2026-10-27 第三轮追加（Redis 连接池守门 ⑤⑥）**：G4 那份 `GoStyleRedisClientAutoConfiguration` 的 customizer **只调 `disconnectedBehavior(REJECT_COMMANDS)`**（G4 树实测 `GoStyleRedisClientAutoConfiguration.java:129-131` ⇒ **不调 `timeoutOptions(...)`、不碰池**）⇒ ⇒ 本域 task-rpc 的池守门 ⑤⑥（`redisPoolIsEnabled` / `redisPoolParametersMatchGoBaseline`）**理论上不受影响** ⇒ ⚠️ **「理论上」不算数** ⇒ 合并后必须**复跑**（task-rpc 6 条 ＋ like-rpc 4 条）⇒ 若 ⑤/⑥ 红 ⇒ 说明共享层或 Boot 装配路径碰了池 ⇒ 属**新回归** ⇒ 直接找 G4。
3. **A3 升 ✅ 的前置**：共享层的 `GoStylePathNormalizingFilter` 合并后 ⇒ 跑**真 Tomcat 探针**复核
   ⇒ 本域 A3 才能整体升 ✅（Go 侧已 L4 活体坐实：5 种路径变体全 200）
4. 🆕 **E66 落码后：本域 Redis 守门 ① 必须仍绿**（`TaskRedisCommandTimeoutGateTest` 的 `commandTimeoutMechanismIsEnabled`）
   ⇒ E66 的落点会**整体替换** `ClientOptions.timeoutOptions` 那份 default source ⇒ 若新实现不含「命令超时」语义 ⇒ ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 **true**）会红
   ⇒ ⚠️ ① 红 = **新回归**（不是「E66 故意关掉了超时」）⇒ **直接找 G4**
   ⇒ 📌 **订正（2026-10-28 · G4 告知 + 我域独立复核）—— 这条信号【不存在】**
      ⇒ **`isTimeoutCommands()` 恒为 true**：Lettuce 的 `TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **两个机制位被同一次调用同时打开** ⇒ ⇒ **单独不构成「时长会被施加」的判据** ⇒ ⇒ **它永远不会红**（我域守门 `TaskRedisCommandTimeoutGateTest` 的注释 `:107-116` 早已写明这一点）。
      ⇒ ⇒ **E66 落码后若有红测试，必须红在真网 = ③（`RedisURI.timeout == 3s`）**（消费点 = `LettuceConnectionFactory` 取**连接级 timeout**）⇒ ⇒ **A/B（机制位）只能当形态断言**。
      ⇒ ⇒ ⇒ **别把「① 没红」当成「E66 没关超时」** ⇒ ⇒ **原措辞保留供追溯**。
   ⇒ 📌 **变异方法约束（G4 2026-10-28 告知 · E66 落码时的「assertion-direction 变异」必读）**
      ⇒ ⇒ **A/B（机制位）在 Boot 默认装配下【恒真】** ⇒ ⇒ **变异点必须选在「装配面」（共享层 customizer 那一侧 —— 它一旦调 `timeoutOptions(...)` 就会整体替换 `enabled()` 那份 default source），【不能】选在「断言面」**。
      ⇒ ⇒ ⚠️ **但共享层另有一条守门 ③**（`GoStyleRedisClientOptionsGateTest`「命令超时必须**未被**本层改动」）⇒ ⇒ **若变异把整个 `TimeoutOptions` 换掉 ⇒ ③ 也会红** ⇒ ⇒ ⇒ **「恰好 1 条红」的判据会被破坏**（会被误读成「两条网一起塌」）⇒ ⇒ ⇒ **必须用「只翻那一位」的最小翻转**（如让 `enabled()` 少一位），**不要**用「另一个 `TimeoutOptions` 整体替换」。
      ⇒ ⇒ ⇒ **本条是「变异怎么翻」的硬约束，不是结论** ⇒ ⇒ **E66 未落码 ⇒ 本轮不执行**（等 Jason 拍板）。
   ⇒ 配套：新 source 在**本请求没有 deadline** 时必须回落到 **`getClientConfiguration().getCommandTimeout()`（本域 = 3s）** ⇒ 否则命令失去 3s 上限（断连从「3s 快速失败」退化为「无限等待」）⇒ ⇒ **这条已经写进本域台账的 E66 硬约束段（已独立核实并订正其机制表述）**
5. 🆕 **两个「合并后必红」的共享门禁**（2026-10-27 第四轮**实测** ⇒ **写权在 G4 ⇒ 已派单** · 编号 `FB-S1`/`FB-S2`）
   ⚠️ **编号说明**：已发给 G4 的消息里把这两条写成 **FB-06 / FB-07** ⇒ 但**本域 §4 里 FB-06 指的是 E66 那条** ⇒ ⇒ **撞名** ⇒ **以本台账的 `FB-S*` 为准**（`S` = 跨域共享门禁）⇒ G4 侧按**内容**识别。
   - 🔴 **FB-S1 · `tools/difftest/check-tomcat-limits.py`**：我树 **EXIT=1**；主干 `do-my-own-gh` 与 G4 树 **EXIT=0**
     ⇒ 报错原文：`🔴 service/task/task-api 缺 ④ rag.http.go-form-limit.mode：实际 'off'，应为 'raw'`
     ⇒ **根因 = 期望表 stale，不是本域 yml 错**：`tools/difftest/check-tomcat-limits.py:149` 的 `EXPECTED_FORM_LIMIT_MODE` 写死 `"service/task/task-api": "raw"` ⇒ 比对是纯字符串 `gate_ok = (mode == expected_mode)`（`:265-271`）
     ⇒ ⇒ **期望值应为 `off` 的独立证据**：**G4 自己的语义表就这么写** —— `GoStyleFormLimitMode.java:219`（G4 树）`new DomainReachability("service/task/task-api", false, false, Mode.OFF, "task/api/**：httpx.Parse / ParseForm / GetFormValues 命中数 = 0 …")`；同文件 javadoc `:280` 举例明写「task 域 yaml 声明 raw，而语义答案是 OFF」⇒ ⇒ ⇒ **域侧改 `mode: "off"` 正是按共享层判据走** ⇒ ⇒ **守门表与共享层表直接打架**
     ⇒ ⇒ **守门想抓的漂移在 task 域不存在**：Go 侧 `gettaskhandler.go:19` 走 `json.NewDecoder(r.Body)` ⇒ `httpx.Parse` / `r.Form` 从不求值 ⇒ Go 的 httpx 2048「非空值」判据从不生效；RAG 侧本域 `@RequestParam` / `getParameter*` 命中 = 0 ⇒ Tomcat 永不解析 query ⇒ **两侧都不可达** ⇒ 不存在「Go 放行而 RAG 被拒」⇒ ⇒ 守门报错文案那句「缺它 ⇒ 容器上限不会被抬到 10001 ⇒ Tomcat 会先于应用层拒绝」**对 task 域不成立**
     ⇒ 🔴 **危害具体化**：守门红 ⇒ **会推动下一个人把 task-api 改回 `raw`** ⇒ **重新引入误拒**（>2048 个非空 query 被 400 + HTML 页拒，而 Go 照常受理）⇒ ⇒ 而本域**已有网钉住不可达性**：`TaskApiFormLimitReachabilityTest` 的「>2048 个非空 query 值 ⇒ 200」（真 Tomcat）⇒ ⇒ **若哪天本域开始读 query 参数，该守门会红** ⇒ ⇒ 届时才需把容器上限提到 **10001**
     ⇒ ⇒ **合并后处置纪律**：该门禁若仍红 ⇒ **不要改本域 yml** ⇒ 是 **G4 的期望表**要改（最小：`:149` ⇒ `"off"`；更稳：期望值改从 `SEMANTIC_REACHABILITY.semanticMode` 取 ⇒ 单一真源 ⇒ 以后不再 stale）
   - 🔴 **FB-S2 · `tools/difftest/check-wave-coupling.py`**：我树 **EXIT=1**（6 条 `common_radiation` 漂移）；主干与 G4 树 **EXIT=0**
     ⇒ **根因 = 快照过期，不是代码回退**：`docs/DRIFT/_cross/cross-domain-coupling.md`（**744 行 · 三棵树逐字相同**）的 `<!-- wave-coupling-snapshot -->` 块**未含 CD-6(task) 与 A9(like) 的接入** ⇒ ⇒ **文档三棵树一致 + 主干/G4 树绿 ⇒ 差异全部来自域侧接线代码**
     ⇒ 6 条漂移：`GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` / `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor`（**None → `["task"]`**）· `GoStyleGrpcClientBreakerInterceptor`（**增 `like`**）· `MqConsumer`（**增 `like`**）
     ⇒ ⇒ **task 那 4 条全部来自 `src/test/` 的 import**（`TaskProtectionChainWiringTest` / `TaskApiFormLimitReachabilityTest`）⇒ **task 生产代码对这几个类的 import = 0**（守门 `scan_common_radiation` 用 `service/**/src/**/*.java` ⇒ 含 test）
     ⇒ ⇒ **修法在 global 写权 ⇒ G4**：用 `python3 tools/difftest/check-wave-coupling.py --print` 的输出更新快照块（目标值已取到 = `/tmp/g2-coupling-target.json`）
6. 🆕 **合并 G4 共享层落码时，`task-api` 的 yml 可能被覆盖回 `mode: raw`**（2026-10-27 **实测**）
   ⇒ G4 树 `service/task/task-api/src/main/resources/application.yml:177-178` 仍是 `mode: raw`（+ 一条**用错判据轴**的注释：拿「有没有注册 `httpx.SetErrorHandler`」（**轴② 拒绝形状**）来定「开不开」（**轴①**）；而轴①在 task 域的判据是「上限不可达 ⇒ OFF」⇒ **与 G4 自己的 `SEMANTIC_REACHABILITY` 自相矛盾**）
   ⇒ ⇒ **合并风险** = 以 G4 树版本覆盖 ⇒ 本域 D-1 的 `mode: "off"` 被回退 ⇒ ① 上条 FB-S1 的期望值又要跟着变；② 域侧回到「上限可达但闸门开着」的错配形态
   ⇒ ⇒ **合并清单里必须显式写一行**：`service/task/task-api/src/main/resources/application.yml` **以 G2 分支版本为准**（或合并后按 `SEMANTIC_REACHABILITY` 的 OFF 复核一次）
7. 🆕 **本条（FB-S1 / FB-S2 / FB-S3）的派单状态**（2026-10-27 11:59 复核）
   ⇒ ✅ **已发 G4**（`session send` · 入队 id `14683` · 2026-10-27 上午）⇒ ⚠️ **截至 G4 11:59 回信，该条仍在 G4 队列里（未消费）** ⇒ ⇒ G4 本次回信（纠正 + 回执摘要）消费的是**更早**的消息 ⇒ ⇒ **不重发**（重发会重复处理同一份内容）⇒ ⇒ **兜底 = 本台账已完整登记三条的现象/根因/证据/修法/影响面** ⇒ ⇒ 即使队列继续滞留，**整合者从本台账即可拿到全部所需信息**
   ⇒ 🟢 **G4 11:59 回执已确认的一侧**：**① 纠正**（上一轮「task 改 off ⇒ 比 Go 更严」是错判 ⇒ 域侧 `mode: "off"` 落码正确、**不需要任何额外动作** ⇒ G4 已在 `2f3d5c2` 订正并撤销提醒 ⇒ 与本域 `60add43` 的独立复核**结论同向**）；**② FB-04 / FB-05 回执摘要**（`853767b` / `c15dfeb` ⇒ **task 域 FB-05 销号** · **like 域零影响**）；**③ 端口段表**已汇总登记进 `docs/DRIFT/global/vs-common.md`（含本域 **21800–21899**）⇒ **本域零待办**；**④ 工具口径缺口**（`check-ledger-selfcheck.py` 的 `CLASS_RE` 认不出支撑类）⇒ G4 只登记 + 转整合者（**提示级 · 不影响退出码** ⇒ 本域零动作）；**⑤ 错峰**（G4 第十次全量 11:39:21 收工 ⇒ G4 让出大回归窗口）
   ⇒ ✅ **`60add43` 的 yml 注释改动的验证**（趁 G4 让出的窗口跑）：`mvn -o -pl service/task/task-api -am -DskipTests install` ＋ `mvn -o -pl service/task/task-api test` ⇒ **97 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS · 35.186 s** ⇒ 与第四轮 **97** 条**逐条口径一致** ⇒ ⇒ **真 Tomcat 起得来**（日志里端口段 **21801** 仍在用 ⇒ 端口治理未受影响）⇒ ⇒ **注释改动零行为影响**（确认）
   ⇒ ⚠️ **口径纪律**：本次是**单模块**回归（`-am` 只装上游、不跑上游测试）⇒ **不是域级全量** ⇒ ⇒ 数字**不得**与第四轮的六模块 598 / 八模块 637 相加或并列引用

⚠️ **E66 的硬约束（G4 已探明，落地前必须解决）**：
Boot 4 `LettuceConnectionConfiguration.createClientOptions` 的字节码顺序 =
**offset 146 先写 `timeoutOptions` → offset 151 才应用 customizer**
⇒ ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖既有那份 `TimeoutOptions`**
⇒ ⇒ 落码前必须先解决「**保留既有命令超时**」
⇒ 🆕 **本域提供的实测数据**：命令超时**压根不挂在 `ClientOptions` 上**
（`javap` 实测 `io.lettuce.core.ClientOptions` **没有** `getCommandTimeout()`，命中数 = **0**）
⇒ ⇒ customizer 里要读回的是 **`getClientConfiguration().getCommandTimeout()`**（**不是** yml、**不是** `getTimeoutOptions()`）
⇒ ⇒ 且本域的**域侧调用点清单**已备好（like-rpc **11 处** / task-rpc **3 处** · 见本文件 §4 的 FB-05 / FB-06）

🟢 **2026-10-27 G2 独立核实（`javap -c` ＋ 源码双证）—— 结论成立，但「机制表述」有一处不准 ⇒ 订正如下**
- ✅ **顺序与偏移全部复现**：Boot 4.0.8 `spring-boot-data-redis` 的 `LettuceConnectionConfiguration.createClientOptions` 反汇编 ⇒ `142-149` `invokestatic TimeoutOptions.enabled()` → `146` `ClientOptions$Builder.timeoutOptions` → `150-151` `ObjectProvider.orderedStream()` → `157` `invokedynamic`（`lambda$createClientOptions$0`）→ `162` `forEach` → `168` `build()` ⇒ ⇒ **offset 146 / 151 与 G4 所述逐位吻合** ⇒ ⇒ **「customizer 再调 `timeoutOptions(...)` 会覆盖前一份」成立** ✔
- 🔴 **订正 1（机制表述不准）**：G4 原句写「offset 146 先写 `timeoutOptions`（**来自 `spring.data.redis.timeout`**）」⇒ **不对** ⇒ 源码 `LettuceConnectionConfiguration.java:229` = `builder.timeoutOptions(TimeoutOptions.enabled());` ⇒ **入参是硬编码默认** ⇒ **`spring.data.redis.timeout` 压根不进 `ClientOptions.timeoutOptions`** ⇒ 它走 `:173-174` `builder.commandTimeout(getProperties().getTimeout())` ⇒ ⇒ 落 `getClientConfiguration().getCommandTimeout()` ⇒ **RedisURI.timeout**（= 本域守门 ③ 读的那个值）
- ⇒ ⇒ **对修法的影响 = 无**（结论与处方都成立）⇒ 但**新增一条实现约束**：customizer 的 `TimeoutSource` 会**整体替换** `TimeoutOptions.enabled()` 那份 default source ⇒ ⇒ **无 deadline 时必须回落到 `getCommandTimeout()`（本域 = 3s）** ⇒ 否则**命令失去 3s 上限**（断连从「3s 快速失败」退化为「无限等待」）⇒ ⇒ **本域守门 ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 true）就是钉这条的网** ⇒ 🔴 **E66 落码后若 ① 变红 ⇒ 属新回归 ⇒ 直接找 G4**

### 🔴 第四轮追加（2026-10-27 13:0x · G2 独立取证）· 两个共享门禁的**根因归属坐实** ＋ **跨树行号引用纪律**

#### ① FB-S1 的根因 = **G4 树内部三方矛盾**（不是本域 yml 错）⇒ 守门期望值 stale 的铁证

把 G4 树（`align/g4-service-common` @ **`cec7131`**）里三处并排看，**互不相容**：

| # | 位置（G4 树 `cec7131`） | 内容 | 指向 |
|---|---|---|---|
| 1 | `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:237` | `new DomainReachability("service/task/task-api", false, false, Mode.OFF, "task/api/**：httpx.Parse / ParseForm / GetFormValues 命中数 = 0（gettaskhandler.go:19 用 json.NewDecoder(r.Body) + UseNumber，只 import 了 httpx 但只用 httpx.ErrorCtx）⇒ r.Form 从不被求值 ⇒ 两道上限不可达 ⇒ 语义答案是 off")` | **权威语义 = OFF** |
| 2 | `service/task/task-api/src/main/resources/application.yml:176-178` | `# Go 侧 task/api **没有** httpx.SetErrorHandler ⇒ 同 article ⇒ **400 + Go 原文** ⇒ mode: raw` ＋ `mode: raw` | **现状 = raw**（与 1 自相矛盾） |
| 3 | `tools/difftest/check-tomcat-limits.py:149` | `EXPECTED_FORM_LIMIT_MODE` 把 `"service/task/task-api"` 写死为 **`"raw"`** | **守门期望 = raw**（固化的是 2 的现状，不是 1 的语义） |

⇒ ⇒ **权威源（1）说 OFF，而现状（2）与守门（3）都说 raw** ⇒ ⇒ **守门红 = 守门的期望值 stale**（AGENTS.md §3.3 的「绿/红」纪律：守门红先问「期望值的权威来源是什么」⇒ 权威来源 = `SEMANTIC_REACHABILITY`，而它与硬编码表直接打架 ⇒ **守门错**）
⇒ ⇒ **G4 树那行 yml 注释的推理漏了一条判据**：它只用了**轴②（拒成什么形状）**（「没注册 `SetErrorHandler` ⇒ 400 + 原文」）来定**轴①（开不开）**⇒ ⇒ 而轴①在 task 域的判据是「**上限不可达 ⇒ OFF**」（`GoStyleFormLimitMode` 的 javadoc `:36-52` 明列「`Mode.OFF` ⇔ 域自捕获 **或** 上限不可达」）⇒ ⇒ **那行注释本身就 stale** ⇒ ⇒ 合并取本域版本（`mode: "off"` ＋ 订正注释）即修正
⇒ ⇒ **处置纪律（不变，重申）**：该门禁若仍红 ⇒ **不改本域 yml** ⇒ 改 G4 的期望表（最小：`:149` ⇒ `"off"`；更稳：期望值改从 `SEMANTIC_REACHABILITY.semanticMode` 取 ⇒ **单一真源** ⇒ 以后不再 stale）

#### ② ⚠️ 新纪律：**跨树引用必须带 commit id**（本条是本轮实测踩出来的）

⚠️ **本台账原引 G4 树的行号已过期**（G4 树继续前移 ⇒ 行号漂移）：

| 原引（本台账 §4·五 ①） | **G4 树 `cec7131` 实际行号** | 漂移 |
|---|---|---|
| `GoStyleFormLimitMode.java:219`（task 行） | **`:237`** | **+18** |
| `GoStyleFormLimitMode.java:280`（javadoc「task 域 yaml 声明 raw，而语义答案是 OFF」） | **`:298`** | **+18** |

⇒ ⇒ **同一批受影响**：原引的 article/comment/favorite/follow/like/message 各行（`:191/:194/:197/:207/:213/:216`）⇒ **现为 `:209/:212/:215/:225/:231/:234`**（`SEMANTIC_REACHABILITY` 列表起点 `:208` · record 定义 `:166` · `selfConsistent()` `:187` · `parse()` `:334` · 合法别名 `off`/`false`/`raw`/`envelope` = `:340/:342/:346/:348` · 非法值抛错 `:351`）
⇒ ⇒ ⇒ **规则**：AGENTS.md §3.3.1 要求「行号指向**消费点**」⇒ **跨树场景还要加一句**：**行号只在指定的 commit 上有效** ⇒ ⇒ **凡引他树 `文件:行号`，必须同时写出该树的 commit id**（本条已按此改写：全部改为「G4 树 `cec7131`」）
⇒ ⇒ ⇒ **同族**：与「从别的域抄过来的**类名**会过期 ⇒ 引用前按域重新定位」同源 ⇒ ⇒ **这次过期的是**「**行号**」⇒ ⇒ **凡跨树引 `文件:行号` ⇒ 一律先在该树的**当前 commit** 上重新定位**

#### ③ FB-S2 的 6 条漂移：**MAIN / TEST 分类**（全量逐条定位 · 脚本化实证）

用与守门同口径的扫描（`import` 判定 · `service/**/src/**/*.java`）逐类定位 ⇒ **6 条里 5 条是纯 `src/test/` 引用，只有 1 条是生产引用**：

| 共享类 | 漂移 | 引用方（本域） | 层级 |
|---|---|---|---|
| `GoStyleFormLimitMode` | `None` → `["task"]` | `task-api/…/TaskApiFormLimitReachabilityTest.java` | **TEST** |
| `GoStyleFormValueLimitInterceptor` | `None` → `["task"]` | 同上 | **TEST** |
| `GoStyleRpcBreakerInterceptor` | `None` → `["task"]` | `task-rpc/…/TaskProtectionChainWiringTest.java` | **TEST** |
| `GoStyleRpcSheddingInterceptor` | `None` → `["task"]` | 同上 | **TEST** |
| `GoStyleGrpcClientBreakerInterceptor` | 增 `like` | `like-api/…/LikeApiGrpcClientBreakerE2ETest.java` | **TEST** |
| `MqConsumer` | 增 `like` | `like-rpc/…/mq/LikeUpdateKafkaListener.java`（**`src/main`**）＋ `like-rpc/…/LikeSpansAttributeGateTest.java` | **MAIN ＋ TEST** |

⇒ ⇒ **task 生产代码对前 4 个共享类的 `import` = 0**（与本台账上一轮结论一致 · 这次用脚本逐条坐实）
⇒ ⇒ 🔴 **给 G4 更新快照时的语义提醒**：**守门的 `scan_common_radiation` 把测试 import 与生产 import 视为等价**（扫 `service/**/src/**/*.java` ⇒ 含 `src/test`）⇒ ⇒ 若直接贴 `--print` 的输出 ⇒ 快照会写成「**task 域引用了 `GoStyleFormLimitMode` 等 4 个共享类**」⇒ ⇒ **读者会以为共享层改这 4 个类会波及 task 的生产路径** ⇒ ⇒ **实际只是「本域有一条测试在验证这个共享类」** ⇒ ⇒ **高估共享层爆炸半径**
⇒ ⇒ ⇒ **建议（二选一，由 G4 定）**：① **改口径**：`scan_common_radiation` 分 **MAIN / TEST 两轴**记录 ⇒ 快照里能看出「生产依赖」与「测试引用」的差别（**根治**：共享层爆炸半径的语义本来就指生产依赖）② **保持单轴但加标注**：快照生成时保留层级标记 ⇒ 读者不会误读
⇒ ⇒ ⇒ ⚠️ **无论 G4 选哪种**：**本域不会回退这些测试**（它们分别是 A3「不可达性」与 A2「保护链构成」的守门网）⇒ ⇒ ⇒ **修法只能落在「快照/口径」侧 ⇒ 不能靠删域侧 import 消漂移**

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 2 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **4 条全部重核，1 条「守门已立」被订正为「部分修」**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 `4786eb5` / D-2 `fad4c63` / D-7 = 端口段治理，**task-api 19/19 绿**）
- [x] §4 的派单**登记**（本文件 §4 **FB-01 / FB-02 / FB-03 / FB-05**）＋ **实时轨已发** `session send` 给 `G4Common`（FB-05 位 15）
- [x] **共享面不回写**（合并后由**合并收口执行者**统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

6. 🆕 **共享层「进程级静态池」缺陷在我域同构 ⇒ 合并后必须复核**（2026-10-27 · G4 `7488b3d` 触发我做静态自查）：
   - **G4 修的缺陷**：共享层的 `HttpMiddleware.HTTP_EXECUTOR` / `RpcInterceptor.TIMEOUT_SCHEDULER` / `RpcInterceptor.HANDLE_EXECUTOR`
     是 **`static final`** ⇒ `shutdown()` **永久终止** ⇒ surefire 一个模块一个 JVM ⇒ 模块内**多个 Spring 上下文**
     ⇒ 前一个上下文关闭后 `schedule()` / `submit()` 被拒 ⇒ 拦截器返回 **`INTERNAL`** 而不是 `DEADLINE_EXCEEDED`。
   - **触发钩子**：`service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:52-56`
     （`@Component` + `@PreDestroy` ⇒ 调 `HttpMiddleware.shutdown()` + `RpcInterceptor.shutdown()`）
     ⇒ ⚠️ **我域三个 rpc 组件全部 `@Import` 了它**
     （`LikeRpcApplication.java:91` · `TaskRpcApplication.java:89` · `MessageRpcApplication.java:77`）
     ⇒ ⇒ **我域同构，不是「别的域才有」**。
   - **我域的暴露面（实测）**：`LikeRpcGrpcInterceptorWiringTest:178` 断言 `DEADLINE_EXCEEDED`
     （另 `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4）
     ⇒ 且 like-rpc 有 **9 个** `@SpringBootTest`（properties 互不相同 ⇒ 9 个独立上下文）
     ⇒ 按 surefire 报告写入时间还原，该断言所在类排**第 7 位** ⇒ 其前已有 5 个 Spring 上下文跑过。
   - **G4 的修法**：`AtomicReference` + 取用时 `isShutdown()` 检出即 **CAS 重建**（自愈）⇒ 合并后**毒化自动恢复**。
   - ⇒ **我域动作 = 0**（域侧无自建静态池 ⇒ 只经共享层受影响 ⇒ 合并即受益）
   - ⚠️ **但「合并前我域的绿是真是侥幸」我未实证** —— 需**合并后**复跑本域这三处 `DEADLINE_EXCEEDED` 断言
     ⇒ **收口回归必查项**（这正是 G4 那句「绿可能只是没走到」）。

1. ~~**FB-02 的 RAG 侧后果（400 vs 200）未跑**~~ ⇒ ✅ **阶段 2 已补 RAG 侧**：`TaskApiFormLimitReachabilityTest`（真 Tomcat）覆盖 3 条 query 变体（>2048 非空值 / `?a=%zz` / `?a=1;b=2`）⇒ 现状全部 **200**（= Go 侧语义，因 Go 侧从不解析 query）⇒ **域侧已闭环**。<br>🆕 **2026-10-27（G2 补 Go 侧活体 ⇒ 本条销号）**：**Go 真源侧已实测** ⇒ **2026-10-27 12:0x** 起 Go 侧 `task-api`（`CGO_ENABLED=0 GOPROXY=off go build` ⇒ `/tmp/g2-go-task-api` ⇒ `nohup … -f etc/task.yaml &` ⇒ 监听 **18886**）⇒ 探针 `POST /taskcenter/v1/task/get` ＋ body `{"id":"5001"}` ＋ **2100 个非空 query 值** ⇒ ⇒ **响应 = 503**（`rpc error: code = Unavailable … dial tcp 127.0.0.1:9005: connect: connection refused`）⇒ ⇒ **不是 400 + `too many form values`** ⇒ 且与「3 个非空值」对照**同状态** ⇒ ⇒ **Go 侧 form 闸门不可达 = L4 活体坐实**（与 Go 侧 `gettaskhandler.go:19` 走 `json.NewDecoder(r.Body)`、`httpx.Parse` 从不求值**互相印证**）<br>⚠️ **口径（不可写成「Go 侧 200」）**：Go 侧给的是 **503**（下游 Go `task-rpc` 未起 ⇒ gRPC Unavailable）⇒ ⇒ **闭合的是「form 闸门在 Go 侧不触发」这一条判据轴** ⇒ ⇒ **「两侧响应逐字节一致」仍未闭合**（需 Go `task-rpc` 起着才能比形状）⇒ ⇒ A3 轴整体**仍不得标 L4**（§3.3.3：无双侧活体不得写 L4 —— 本条只把「Go 侧闸门不触发」这一格补上）<br>⚠️ **探针本身的两个坑（写下来免得下次重踩）**：① **真实路由带 prefix** —— Go 侧 `routes.go:24` 是 `rest.WithPrefix("/taskcenter/v1")` ⇒ 路径是 **`/taskcenter/v1/task/get`**，**不是** `/task/get`；② **探针必须自带守恒断言** —— 第一版探针打错路径 ⇒ 四个用例**全 404** ⇒ 而「与对照同状态」这条判据会**平凡通过** ⇒ ⇒ 已加断言「基线不得是 404/405」（`ROUTE_REACHABLE`）⇒ 修正路径后才拿到 503 ⇒ ⇒ **教训：探针先证明自己走到了被测路径，否则判据是空的**（「绿是因为没走到」）<br>⚠️ **探针是易失文件**（`/tmp/g2-go-form-probe.py` · `/tmp/g2-go-form-probe.json`）⇒ ⇒ **配方记在这里**：Go 侧在 `service/task/api` 下 `CGO_ENABLED=0 GOPROXY=off go build -o /tmp/g2-go-task-api .` ⇒ `nohup /tmp/g2-go-task-api -f etc/task.yaml &` ⇒ 端口 **18886** ⇒ 真实路径 **`POST /taskcenter/v1/task/get`** ⇒ body `{"id":"5001"}` ⇒ query 形态 `k0=v0&k1=v1&…`（**2100 个非空值**）⇒ 判据 = **不是 400 + `too many form values`** 且 **与少值对照同状态** |
2. **FB-01 的「RAG 对尾斜杠返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）；article 域的等价断言见 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiRouteAuthBoundaryTest.java:221`。
3. ~~**A2 的 shedding 在链上**无任何守门证明（现存守门主动关掉降载）⇒ 这是本轮最实的一个「**跑绿 ≠ 对**」形态。~~ ⇒ ✅ **2026-10-27 订正：本条已过期（阶段 2 `fad4c63` 已补）** —— 原写「无任何守门证明」指的是**旧守门**（`TaskProtectionChainWiringTest:80-81` **主动关掉降载** ⇒ 拿到的 call 永远归熔断层所有 ⇒ **分不出 shedding 是否在链上**）⇒ ⇒ 现守门已扩到 **2 条链层断言**：① 「关降载 ⇒ 拿到的 call 归熔断层所有」② 「**不关降载** ⇒ 拿到的 call 归降载层所有」⇒ ⇒ **现在能分别断言 breaker / shedding 两层**（判据选「**包装层归属**」而非「降载统计增量」—— 后者是**进程级单例**且每分钟上报会**清零** ⇒ 增量断言有 ~1e-5/次的**假红**概率 ⇒ **守门不能带随机性**）⇒ ⇒ **并做过变异验证**（摘掉降载层 ⇒ **1 failure** 红在 `sheddingLayerWrapsTheBusinessInterceptor:150`；摘掉熔断层 ⇒ **1 failure** 红在 `breakerLayerWrapsTheBusinessInterceptor:118` ⇒ 两次都**只红 1 条**、另一条阴性对照仍绿 ⇒ **非假绿**）⇒ ⇒ **本条销号**<br>⚠️ **留痕**：共享层的「链层构成」只读入口（FB-03）**已由 G4 落码**（`compose(props)` / `layerNames(chain)` · G4 `182fc32`）但**未合入本树** ⇒ ⇒ 本轮引用它会让本模块**编译不过** ⇒ ⇒ 域侧改用等价的「包装层归属」判据顶上 ⇒ ⇒ **合并后**应升级为直接断言 `layerNames(chain)`（= 更贴共享层原生判据）
      ⇒ 📌 **订正（2026-10-28 · G4 问「FB-01/FB-03 升级 `layerNames` 断言」时逐条复核）—— 本条【已完成】，原措辞保留供追溯**
      ⇒ ⇒ **升级提交 = `4dd2d1a`**（`feat(task,like,message): G2 · 合并 G4 共享层后按 §5.3e 升级 FB-03 域侧守门 ⇒ 三域 A2 轴 L1⇒L2`）⇒ ⇒ **三域守门现在都直接断言 `GoStyleRpcProtectionChain.layerNames(chain)`**：message `MessageProtectionChainWiringTest:97/:104/:108/:121` · like `LikeProtectionChainWiringTest:127/:134/:138/:151` · task `TaskProtectionChainWiringTest:182/:189/:193/:206`（含**一致性断言** `layerNames(chain) == compose(props).layerNames()`）。
      ⇒ ⇒ ⇒ 上面那句「域侧改用等价的『包装层归属』判据顶上」**只描述升级前的临时形态** ⇒ ⇒ **现状 = 已用共享层原生判据** ⇒ ⇒ **G4 的这项请求对我域是【已完成的旧请求】**。 |
4. ✅ **`task-rpc` 侧 `MqConsumer` / `TraceConsumer`** 是否在两个 sink 上都包了 —— ✅ **2026-10-27 已核实（结论：都包了）**：原写「本轮只读源码未见对应调用点，**未排除也未证实**」⇒ ⇒ **本轮逐条查消费点后证实**：<br>· **`UserLikeProgressSink`**：`import com.rag.common.observability.MqConsumer`（`:3`）⇒ `MqConsumer.traceConsumer(...)` **2 处**（`:135` / `:228`）<br>· **`ArticleLikeProgressSink`**：同 import（`:4`）⇒ `MqConsumer.traceConsumer(...)` **2 处**（`:150` / `:241`）<br>⇒ ⇒ **两个 sink 都包了** ⇒ ⇒ **A9 的这个遗漏面销号**<br>⚠️ **同轮附带核了两条「看着像漂移、其实不是」的**（**都是我的判据错，不是代码错**）：<br>① 🔴 **Go 侧 task 域的 mq 目录我一次 `find -path "*mq*"` 零命中 ⇒ 差点断言「Go 侧没有 sink」** ⇒ ⇒ **实际 Go 侧目录叫 `service/task/rpc/internal/sink/`**（`like_sink.go` / `article_sink.go`）⇒ ⇒ **目录名判据错** ⇒ ⇒ 教训：**一次 find 零命中 ≠ 事实为零**（先怀疑判据，再怀疑对象）<br>② 🔴 **Go 侧 yaml 我一次 `grep "out-user-topic"` 零命中 ⇒ 差点断言「Go 侧没这两个 topic」** ⇒ ⇒ **Go 侧是大驼峰 `OutUserTopic` / `OutArticleTopic`**（`service/task/rpc/etc/task.yaml:20-21`）⇒ ⇒ **命名风格判据错**<br>⇒ ⇒ **改正后的完整核对（6 个消费点逐条对上）**：Go `initKafkaRaw.go:32`(`GroupKafkaRaw`/`InTopic`) ⇒ RAG `TaskRawDiagnosticConsumer` · Go `initGoKa.go:124`(`GroupGoKa`/`InTopic`) ⇒ RAG `TaskRawLogRouter` · Go `initGoKa.go:165`(`GroupGoKaUser`/`RawUserTopic`) ⇒ RAG `TaskUserCountConsumer` · Go `initGoKa.go`(`GroupGoKaArticle`/`RawArticleTopic`) ⇒ RAG `TaskArticleCountConsumer` · Go `initUserFilter.go:30`(`GroupFilterUser`/`OutUserTopic`) ⇒ RAG `UserLikeProgressSink` · Go `initArticleFilter.go:30`(`GroupKafkaFilter`/`OutArticleTopic`) ⇒ RAG `ArticleLikeProgressSink` ⇒ ⇒ **6/6 一致**；且 `GroupFilterArticle`(`task.rpc.consumer91`) 在 **Go 侧确实无消费点**、RAG 侧 yml 也如实标注「保留仅为配置面完整」⇒ **两侧同款** ✔<br>③ 🔴 **per-listener 的 `auto.offset.reset`：Go 侧 goka 三处 `sarama.OffsetOldest`（`initGoKa.go:131/:173/:207`）vs RAG 侧实际 `latest`？** ⇒ ⇒ **不成立** —— RAG 现状**已是点分形式**且**真生效**：3 个 goka 路 = `auto.offset.reset=earliest`（`TaskRawLogRouter:83` / `TaskUserCountConsumer:84` / `TaskArticleCountConsumer:80`）· 3 个 kq 路 = `auto.offset.reset=latest`（`TaskRawDiagnosticConsumer:54` / `UserLikeProgressSink:129` / `ArticleLikeProgressSink:144`）⇒ ⇒ **与 Go 逐条同向**（goka 三处 `OffsetOldest` / kq 三处硬编码 `Offset: "latest"`）⇒ ⇒ 该条**早已闭环**（`DRIFT-TASK-RPC-006` · 2026-10-24 查出连字符 `auto-offset-reset` 被 Kafka 忽略 ⇒ 已修 ⇒ **活体验证 8 条全过**）⇒ ⚠️ **listener 注释里那句「此前这里写的 earliest 从未生效」是「修复前」的记述、不是现状** ⇒ ⇒ **注释里的历史记述 ≠ 现状**（必须查实际配置）
5. **未核项**：共享层 `service/common` 的既有结论（`docs/DRIFT/common/{protection,http,deadline,audit}.md`）本轮**未逐条复核** ⇒ 本域只负责「本域是否真接上」，「共享层本身对不对」沿用共享层台账。<br>6. 🟢 **E65（= global U15 · 出向 gRPC 不继承请求 deadline）—— 本域 = 不适用**（2026-10-27 独立核实 · G3 知会 + Jason 拍板方向）<br>· **本域跨域出向 gRPC = 0 处** ⇒ ⇒ **E65 在本域不成立**（不是「已修」，是「不适用」）⇒ 逐条实测依据：本域唯一的出向 gRPC client 是 `service/task/task-api/src/main/java/com/rag/task/task/api/client/TaskRpcClient.java`（**task-api → task-rpc = 本域内**）⇒ 而本域**没有**「rpc → 别的域」的出向口（unlike `like-rpc → message-rpc` / `message-rpc → user-rpc` / `message-rpc → admin-rpc` ⇒ **那 3 处才是 E65 的落点**）<br>· ⚠️ **区分**：`TaskRpcClient` 的 `min(DEFAULT_CALL_TIMEOUT_MILLIS, remaining)` 是**本域内** api→rpc 的 deadline 传递（阶段 2 已落）⇒ **与 E65 无关** ⇒ 别把它当成 E65 的修法或现状<br>· ⇒ ⇒ **Jason 拍板的方向（走 global 统一载体 `GoStyleRequestDeadline.applyTo(stub)`）⇒ 本轮不改** 对本域**无影响** ⇒ 本域**零动作**、零待办<br>· 🟢 **2026-10-27 第十四轮订正与加固（结论不变 · 但补上「为什么 N/A」与「将来变了会怎样」）** —— **N/A 判定维持**（本域**跨域出向 gRPC = 0 处** ⇒ E65 的**出向**不成立）⇒ 但把根因链记在本域台账里，免得下一个复核者**只看结论不看机制**：<br>　① **共享层根因（本域源码级复核 · 与 like/message 两域同一机制）**：出向载体 `GoStyleRequestDeadline` 的 ThreadLocal **只**由 `service/common/.../observability/HttpMiddleware.java:324` 的 `begin()` 写入 ⇒ 而 **纯 gRPC 进程里 `HttpMiddleware` 从不加载**（共享层书面证明：`service/common/.../spring/ObservabilityLifecycle.java:57-58`「纯 gRPC 进程里 `HttpMiddleware` **从不加载**（没有 servlet filter 要注册）」· `:69-70`「rpc 侧的 HTTP 池**从未被创建过**」）⇒ ⇒ **任何 rpc→rpc 的出向 `applyTo` 恒 no-op** ⇒ ⇒ **共享层那半的修法 = 服务端把 `effectiveTimeoutMs` 写进这个载体**（取值 = min(入站剩余, 服务端配置)；该值在 `service/common/.../observability/RpcInterceptor.java:293` `Math.min(serverBudgetMs, clientDeadlineRemainingMs())` **已经算好了**，只是绑在 `DeadlineState` 上、与出向载体不通）⇒ ⇒ 落点 = `service/common/**` ⇒ **G4 写权面**<br>　② **本域 task-api → task-rpc 为何不属 E65**：`service/task/task-api/src/main/java/com/rag/task/task/api/client/TaskRpcClient.java:106-109` 的 `withDeadline` 读 `GoStyleRequestDeadline.remainingMillis()` ⇒ ⇒ **task-api 是 HTTP 进程 ⇒ 该 ThreadLocal 已被 `HttpMiddleware.begin()` 写入 ⇒ 取得到值 ⇒ 有效** ⇒ ⇒ **这是「本域内 api→rpc」且载体可用** ⇒ ⇒ **既不属 E65、也不是「已修 E65」**（是**不适用**）<br>　③ ⚠️ **将来变了会怎样（防「不外推」的反向陷阱）**：若本域**新增**任何「rpc → 别域」的出向 gRPC ⇒ ⇒ **照抄 api 侧的 `applyTo` 一行会是假修复**（恒 no-op）⇒ ⇒ 必须**与共享层那半同一次提交** ⇒ ⇒ **本条 N/A 不可外推到别的域，也不可被读成「task 域已具备 deadline 传播能力」**<br>· **N/A 判定维持**（**本域跨域出向 gRPC = 0 处**）⇒ ⇒ **本条仍不构成任何待办**<br>7. 🔴 **命令超时的守门判据偏弱（G1 探针发现 · G2 独立字节码核实 ⇒ 比 G1 再精化一层）**<br>· **G1 的探针读数（转述）**：`TimeoutOptions.isTimeoutCommands()`=true · `isApplyConnectionTimeout()`=true · `getSource()`=`DefaultTimeoutSource` · `LettuceConnectionFactory.getTimeout()`=3000ms（连接级 ⇒ RedisURI.timeout）⇒ ⇒ G1 判「`isTimeoutCommands()` 不是有效判据、应改断言 `isApplyConnectionTimeout()`」<br>· 🟢 **G2 独立核实（字节码级，不采信转述）**：① `TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **`timeoutCommands` 与 `applyConnectionTimeout` 两个位被同时打开** ⇒ ⇒ **在 Boot 4 默认装配下这两个断言单独都是恒真** ⇒ ⇒ ⚠️ **所以「改断言 `isApplyConnectionTimeout()`」并不能把弱判据变成强判据** ⇒ ⇒ **两条合起来才等于 `enabled()` 的全貌**（任一位缺 ⇒ Boot 装配被改）② `DefaultTimeoutSource.getTimeout(...)` = **恒返回 -1**（`ldc2_w -1l`）⇒ ⇒ **`source` 这一路给不出任何时长** ⇒ ⇒ 时长**只能**走 `applyConnectionTimeout` 分支从**连接级 timeout**（RedisURI.timeout）取 ⇒ 与 G1 的读数**一致**<br>· ⇒ ⇒ **本域完整判据链 = 四条合起来**：**A** `isTimeoutCommands()`（机制位①）＋ **B** `isApplyConnectionTimeout()`（机制位②）＋ **C** `getSource()` 恒 -1（⇒ 时长只能走连接级分支）＋ **D** `RedisURI.timeout` = 3s（**时长出口** · 本域守门 ③ **已断言**）⇒ ⇒ **A+B 抓「Boot 装配路径变了」，D 抓「时长值错了」** ⇒ ⇒ **任何单条都不充分**<br>· ✅ **域侧补强动作已落码（2026-10-27 12:3x · **已编译 + 已变异验证** · 证据见本节末）**：本域守门 ① 原先只有 **A** ⇒ ⇒ **已补 B**（`isApplyConnectionTimeout()`）⇒ ⇒ 但**必须在注释里写明「A+B 才等于 enabled() 全貌、单独恒真」** ⇒ ⇒ **不得**把它写成「时长会被施加」的判据（那会重演「绿是因为没走到」）<br>· ⚠️ **边界（与 G1 同一判断）**：被测机制（Boot 的 `TimeoutOptions.enabled()` 接线）**不在域侧写权面** ⇒ ⇒ **无法从域侧做「机制变异」** ⇒ ⇒ 这条只能是**回归网**，**不是**「我已证伪过该机制」⇒ ⇒ 变异只能做「断言方向变异」（断言写反 ⇒ 必红 ⇒ 证明断言在读运行期值）<br>· ⚠️ **另一条边界（G1 提醒 · 我域已核）**：`LettuceConnectionFactory.getTimeout()`（连接级那个出口）G2 **未独立复核**（它在 `AbstractRedisClientFactory` 里，非 `LettuceConnectionFactory` 本体）⇒ ⇒ 本域**已独立复核过的时长出口是 `RedisURI.timeout`**（守门 ③）⇒ ⇒ 引用时不要把两者混用<br>7. 🔴 **我的一条「转移单」是假转移（G4 独立核实并拦截 · G2 采信 · 2026-10-27 12:4x）** ⇒ **自我订正**：我曾据「Go 侧该域有 `rest.WithJwt` ⇒ 适用」就推断「article-api / comment-api / favorite-api 也欠修 009 式的『JWT 验签落在 HTTP 预算内』」，并准备请 G4 转发 ⇒ ⇒ **G4 已独立核实：那三域的 RAG 侧早已修好**（`GoStyleJwtAuthFilter` 的 `@Order(HIGHEST_PRECEDENCE + 6)` ⇒ 验签在预算起点**之前**）⇒ ⇒ **「Go 侧适用」≠「RAG 侧还欠修」** ⇒ ⇒ **我的推断把『适用』悄悄换成了『欠修』** ⇒ ⇒ **该转移单作废**（**我未发出**，已在 G3 队列侧核过 ⇒ 无需撤回；G4 的预防性拦截件已入 G3 队列）<br>⇒ ⇒ **教训（本域留档）**：**真源侧有该行为 ⇒ 只说明「适用」，不说明「RAG 侧还没修」** ⇒ ⇒ 判定「谁欠修」**必须同时读 RAG 侧的当前状态**（`@Order` / 现有实现）⇒ ⇒ **「适用」与「欠修」是两个独立判断，合并成一个就是假转移** ⇒ ⇒ **转派前必须先读被转域的现状**<br>⇒ ⇒ ⚠️ **另一处不精确（G4 指出）**：那三域**不是**都在 3s 预算上 —— article-api 是 **70s**、comment-api / favorite-api 是 3s ⇒ ⇒ **槽位相同（+6 在预算起点 +7 之前）≠ 预算时长相同** ⇒ ⇒ 引用时**槽位与时长要分两轴写**<br>⇒ ⇒ 🟡 **仍属我域的残留（G4 指出 · 由整合者处理）**：那三个拦截器都保留「Filter attribute 缺失时就地验签」的 inline fallback ⇒ ⇒ 那条路径把验签放回预算内 ⇒ **我域无同类实现**（我域 task-api 无鉴权 ⇒ `DRIFT-USERAPI-009②` 对我域**不适用**）⇒ ⇒ **不属我域待办**<br>🟢 **「机制位②」断言已落码 + 已编译 + 已变异验证（2026-10-27 12:5x）** ⇒ **上一行「等待编译与变异验证」的状态已销账**：<br>· **落码**：`ab0d782` ⇒ task-rpc 与 like-rpc 的 Redis 超时守门 ① 各补一条 `isApplyConnectionTimeout()` 断言（断言消息写明「只覆盖第二位 · 与 ① 合起来才等于 `enabled()` 全貌 · **单条恒真** · **不是**『时长会被施加』的判据 · 时长由 ② 断言」）<br>· **编译**：✅ 通过（`mvn -o -pl … -am -DskipTests install`）<br>· **守门跑绿**：task-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** · like-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**<br>· **变异验证（断言方向变异）**：把新断言的 `isTrue()` 改成 `isFalse()` ⇒ ⇒ **两域各红 1 条**，且**红在目标断言** `commandTimeoutMechanismIsEnabled`（task-rpc `TaskRedisCommandTimeoutGateTest` · like-rpc `LikeRpcRedisTimeoutsGateTest`）⇒ ⇒ **恰好 1 条红**（另一条阴性对照仍绿）⇒ ⇒ **非假绿** ⇒ ⇒ 还原后复跑**两域 6/0/0/0 绿**⇒ ⇒ **sha256 与提交版逐字节一致**（还原无残留）<br>· ⚠️ **诚实的边界（不变）**：被测机制（Boot 的 `TimeoutOptions.enabled()` 接线）**不在域侧写权面** ⇒ ⇒ **无法做「机制变异」** ⇒ ⇒ 本条是**回归网**，**不是**「我已证伪过该机制」⇒ ⇒ 上面那次变异证明的是「断言在读运行期值」<br>· ⚠️ **判据自身的一条踩坑**（记下来免得下次重踩）：第一次变异我用 `grep -E "…|expected|but was"` 抓输出 ⇒ ⇒ **被 Kafka 的 `sasl.oauthbearer.expected.audience` 配置行占满 `head` 配额** ⇒ ⇒ **「红没红」根本没被看到**（既不能算红、也不能算绿）⇒ ⇒ 改成只抓 `Tests run:` / `<<< FAILURE` / `BUILD` 后才拿到真相 ⇒ ⇒ **抓取模式本身也会骗人 ⇒ 变异验证的判据必须精确到「失败计数」与「失败用例名」** |

### 🟢 **8 模块域级回归 · 完整数字**（2026-10-27 13:00:44 → 13:07:38 · 独占跑 · `MVN_EXIT=0` · `BUILD SUCCESS` · 6:47 min）

**本轮首次拿到 G2 三域 8 模块的完整数字**（此前只有 message-rpc 独占 84 条与两个守门各 6 条）：

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/message/message-api` | 98 | 0 | 0 | 0 |
| `service/message/message-rpc` | 84 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 89 | 0 | 0 | 0 |
| `service/like/like-rpc` | 113 | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 97 | 0 | 0 | 0 |
| `service/task/task-rpc` | 117 | 0 | 0 | 0 |
| **合计** | **637** | **0** | **0** | **0** |

⚠️ **口径纪律（三条，随数字一起留档）**：
1. **权威口径 = 本轮 mtime 切的 surefire `TEST-*.xml` 逐文件求和**（起跑标记 epoch `1793077244`）⇒ **不用**「按 `[INFO] Building <module>` 分段聚合」⇒ ⇒ **637 与上一轮「八模块 637」口径一致**（上一轮 message-rpc 有 2 errors ⇒ 本轮同一批用例**由红转绿** ⇒ 归因 = 上一轮是**端口撞车**（64118 落在 OS 临时段）⇒ **不是代码缺陷**）
2. 🔴 **排掉 1 份孤儿报告**：`service/like/like-rpc/target/surefire-reports/TEST-com.rag.like.like.rpc.LikeRpcServerSpanCurrentProbeTest.xml`（mtime 早于起跑标记）⇒ **源文件已不在树里**（`find service/like/like-rpc/src/test -name '*Probe*.java'` **零命中** ⇒ 该探针类**从未进过 git** ⇒ 是「建在模块里的本地探针、源删了报告没删」）⇒ ⇒ **like-rpc 报告 20 份 / 测试类 19 个 ⇒ 差 1 = 这份孤儿** ⇒ ⇒ **本域不会回退任何测试来消它** ⇒ ⇒ **教训（复用 G1 同款）**：**探针要么放 `/tmp`，要么连报告一起删** ⇒ ⇒ **「两口径交叉验」遇到差值先查「哪个口径读的是残留」**
3. ⚠️ **独占跑**：起跑前 `pgrep -f 'plexus.classworlds.launcher.Launcher'` = **0**（G1 的 6 模块回归 12:52:27 起、约 13:0x 收工 ⇒ 我 13:00:44 才起）⇒ ⇒ **零端口撞车** ⇒ ⇒ **本轮 637 全绿不含任何环境噪声**（对比：上一轮同口径因撞车红 2 条）

⇒ ⇒ **本轮数字的用途边界**：这是**我域当前代码态**的回归网 ⇒ ⇒ **不是**「合并 G4 共享层后」的结论 ⇒ ⇒ **合并后必须重跑**（§「合并后我域必做」第 1/2/4 条）⇒ ⇒ **本轮数字不升级任何轴的证据强度**（跑绿 ≠ 对）

### 🔴 **D-1 订正**（2026-10-27 13:1x · G4 `cd893d1` 触发）· 「OFF ⇒ 停在 2048 ⇒ 比 Go 更严」那一面**已被共享层消除**

**触发**：G4 于 **13:08:38** 落 `cd893d1`「fix(shared): 容器上限的抬升与闸门的有无**解耦**（G3 反馈 (b)）」⇒ **共享层把两件独立的事拆开了**：

- `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java`（**G4 树 `cd893d1`**）⇒ **删掉** `if (mode == OFF) { return; }` ⇒ `:207` `connector -> connector.setMaxParameterCount(MAX_PARAMETER_COUNT)` ⇒ ⇒ **容器上限无条件抬到 10001**
- ⇒ ⇒ **OFF 的语义收窄为「只关应用层的 Go 风格判定」**（`GoStyleFormLimitMode.Mode.OFF` 的 javadoc 已同步订正 ⇒ 「不装闸门（但容器上限照抬）」）

⇒ ⇒ **对本域 D-1 的影响**：原登记的「**有界残留 · 当前不可达**」= 「OFF ⇒ 运行期上限停在 yaml **2048** ⇒ **比 Go 的 `net/url` 10000 更严**」⇒ ⇒ **这一面已不存在** ⇒ ⇒ **合并 G4 后本域容器上限 = 10001（与 Go 同量级）**

⇒ ⇒ **残留改写为「反向」形态（原值保留，勿当已消失）**：

| 维度 | 原登记（合并前 · 本树现状） | 合并 G4 `cd893d1` 后 |
|---|---|---|
| 运行期容器上限 | **2048**（yaml） | **10001**（无条件抬升） |
| 与 Go 的关系 | **比 Go 更严**（2048 < 10000） | **比 Go 松 1**（10001 > 10000） |
| 可达性 | 不可达（本域零 `getParameter*`） | **仍不可达**（同上 ⇒ Tomcat 惰性解析 ⇒ 上限不被触发） |
| ⇒ 结论 | 有界残留 | **有界残留（形态相反）** ⇒ 若本域将来开始读 query 参数 ⇒ RAG 放行到 **10001** 而 Go 在 **10000** 就拒 ⇒ **差 1** ⇒ 届时才需处理 |

⇒ ⇒ ✅ **守门仍有效且更贴切**：`TaskApiFormLimitReachabilityTest`（真 Tomcat）三条 query 变体（**>2048** 个非空值 / `?a=%zz` / `?a=1;b=2`）⇒ **合并后仍应全 200**（2049 < 10001 ⇒ 连上限都不触发）⇒ ⇒ **合并后必须复跑这条守门**：若变红 ⇒ 说明 Tomcat 真的开始解析参数 ⇒ ⇒ 立即查本域是否新增了 `getParameter*` / `@RequestParam`
⇒ ⇒ ⚠️ **本域 yml 注释已同步改成 merge-conditional**（`service/task/task-api/src/main/resources/application.yml`）⇒ ② 段现在写明「**本树现状 = 停在 2048**」与「**合并 G4 `cd893d1` 后 = 无条件抬到 10001**」两态 ⇒ ⇒ **两态下都不可达 ⇒ 行为无差异** ⇒ ⇒ **合并清单里这一行要写「`task-api/application.yml` 以 G2 树版本为准 ＋ 合并后核对 ② 段描述」**

⇒ ⇒ 🟢 **FB-S1 仍然成立**（`cd893d1` **没有**动 `tools/difftest/check-tomcat-limits.py:149`）⇒ ⇒ 期望表**仍**把 task-api 写死 `"raw"` ⇒ ⇒ **守门红的原因不变**（期望值 stale · 权威语义 = OFF）⇒ ⇒ **改守门，不改本域 yml** ⇒ ⇒ **本条的判据链在 `cd893d1` 之后仍然成立**（权威行仍是 `GoStyleFormLimitMode.java:237` = `Mode.OFF` · task 行**未变**）

⇒ ⇒ 📌 **一条方法论留档（跨层改动会改写域侧结论）**：**共享层的一次解耦，足以把域侧的「有界残留」从「更严」翻成「更松」** ⇒ ⇒ ⇒ **域侧台账里凡写了「运行期上限 / 阈值」类结论，都必须标「依赖共享层哪个版本」** ⇒ ⇒ ⇒ **共享层一改 ⇒ 域侧这条要重核**（不是「重写」⇒ 是**重核**）⇒ ⇒ ⇒ **与「配置里没写 ≠ 没开」同族** —— **读到的那个值，可能不是系统会吃到的那个值**

### 🟢 **交叉验证**（2026-10-27 13:3x）· G4 新增的共享层守门与本域守门**同向互证**

G4 的 `cd893d1` 同时新增了一个**共享层**守门：`service/common/src/test/java/com/rag/common/http/GoStyleParamLimitOffModeGateTest.java`（**G4 树 `cd893d1`** · 290 行）⇒ ⇒ **它的判据与本域的守门正好互为两侧**：

| 侧 | 文件 | 断言 | 判据轴 |
|---|---|---|---|
| **共享层（G4）** | `GoStyleParamLimitOffModeGateTest`（`:120-127`） | **off 域的运行期 connector 参数上限 == 10001**（「抬上限与 mode 解耦」） | 容器层 |
| **共享层（G4）** | 同上（`:141`） | 「off 域：容器上限 10001 ⇒ **2049 个参数不该被容器拒**」 | 应用层 |
| **共享层（G4）** | 同上（`:154-157`） | 「off ⇒ 容器里那个闸门 bean 确实是 `Mode.OFF`，且 **2049 个非空值原样放行**」 | 应用层 |
| **本域（G2）** | `TaskApiFormLimitReachabilityTest` | 「**>2048** 个非空 query 值 ⇒ **200**」 | 应用层（真 Tomcat） |

⇒ ⇒ 🔴 **G4 的守门 javadoc 明确点名了本域**（`cd893d1` 版 `:36` / `:56-57`）：「不装 ⇒ 域自捕获（**favorite**）/ **上限不可达（task）**」·「**上限不可达**（task：Go 侧 `r.Form` 从不求值）⇒ ⇒ Tomcat 不该比 Go **更早**拒 ⇒ 容器上限也该抬到 10001」
⇒ ⇒ ⇒ **两侧判据同向、证据互证** ⇒ ⇒ **合并后本域守门与共享层守门应当同时绿** ⇒ ⇒ **零判据冲突风险**（不是「各写各的、恰好不冲突」，是**同一个 Go 侧事实的两侧表达**）
⇒ ⇒ ⇒ 📌 **G4 的 javadoc 把旧行为（OFF ⇒ 停在 2048）称为「误伤」**，并点名 **task（上限不可达）** 与 **favorite（域自捕获）** 两种 OFF 情形 ⇒ ⇒ **与本域 D-1 订正的方向完全一致**（本域订正：旧残留那一面「比 Go 更严」已被共享层消除）

⇒ ⇒ 🟢 **另一条前提复核（本域台账 §「合并后我域必做」第 2 条的 ⑤⑥）**：G4 新树里 `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:131` **仍只** `builder -> builder.disconnectedBehavior(...)` ⇒ ⇒ **不碰 `timeoutOptions`、不碰池** ⇒ ⇒ **本域「池守门 ⑤⑥ 不受共享层收编影响」的前提在新树里仍成立**（`cd893d1` 只改了那个文件的 3 行 javadoc「三份→五份」）⇒ ⇒ **本域 Redis 侧无需新增动作**

⇒ ⇒ 📌 **顺带一条「我自己的路径引用也会过期」的实例**：本条复核里我第一次按 `service/common/src/main/java/com/rag/common/spring/GoStyleRedisClientAutoConfiguration.java` 去查 ⇒ **零命中** ⇒ 实际路径是 **`.../common/redis/`**（不是 `.../common/spring/`）⇒ ⇒ **同 §「跨树引用必须带 commit id」** —— 路径也会漂 ⇒ ⇒ **跨树引用要连路径一起在该 commit 上重新定位**

### 🟢 **task-api 活体验证**（2026-10-27 13:17:53 → 13:18:24）· yml 注释改动**零行为影响**

**验什么**：`service/task/task-api/src/main/resources/application.yml` 的 ② 段注释被改成 **merge-conditional**（本树现状 = 停在 2048 / 合并 G4 `cd893d1` 后 = 无条件抬到 10001）⇒ ⇒ **必须证明「注释改动没碰坏配置解析、进程仍起得来、form 守门仍全绿」**。

| 口径 | 值 |
|---|---|
| 命令 | `mvn -o -B -pl service/task/task-api test`（**模块级**） |
| 结果 | **Tests run: 97, Failures: 0, Errors: 0, Skipped: 0** · **BUILD SUCCESS** · 36.832 s |
| 数字口径 | 本轮 mtime 切的 surefire XML 求和（起跑标记 epoch `1793078266`）⇒ **0 stale 报告**（与 8 模块那轮同一口径） |
| 真 Tomcat | 起在 **21801**（本域专用段 **21800–21899**）⇒ **端口治理未受影响**（未落进 OS 临时段） |
| 配置解析 | `rag.http.go-form-limit.mode` = **字符串 `'off'`**（Python yaml 解析确认 · 非 YAML 1.1 的布尔 `False`）⇒ **起得来即证明** |

**`TaskApiFormLimitReachabilityTest` 6 条逐条全绿**（真 Tomcat + `HttpURLConnection`）：
`bindsToDedicatedPortSegment` · `formLimitGateMustBeOff`（**直接**断言 `GoStyleFormLimitInterceptor#mode() == Mode.OFF` ⇒ 区分「关掉」与「根本没装」）· `overLimitNonEmptyQueryValuesAreAccepted`（**>2048 个非空 query 值 ⇒ 200**）· `malformedPercentEncodingQueryIsAccepted`（`?a=%zz`）· `semicolonSeparatedQueryIsAccepted`（`?a=1;b=2`）· `normalRequestStillWorks`（阳性对照）

⚠️ **窗口与口径纪律（两条，随数字一起留档）**：
- **窗口**：G4 13:09 起第 12 次全量（ETA ~13:31）⇒ 按其知会只跑**模块级 `-pl`** ⇒ ⇒ **全量并发会被 OOM-kill（EXIT=137）** ⇒ ⇒ 与 8 模块那轮的 637 **不是同一口径**（单模块 vs 8 模块）⇒ ⇒ **不可相加、不可并列引用**
- ⇒ ⇒ **合并 G4 后仍需复跑**：本次只证明了**本树（含旧版共享层）**的形态 ⇒ **合并后的形态（容器上限 10001）要由收口回归验证**（本守门在两种形态下都应全 200 ⇒ 若合并后变红 ⇒ 说明 Tomcat 真开始解析参数 ⇒ 查本域是否新增 `getParameter*`）

### 🔴 第四轮（2026-10-27 13:2x~13:4x）· 合并 G4 共享层 + **FB-03 域侧守门升级** ⇒ 本域 A2 轴 **L1 ⇒ L2**

#### ① 合并（G4 要求的协议 · 整支合并、**不 cherry-pick**）

- `git merge align/g4-service-common` ⇒ **merge commit `37816ca`** ⇒ **零冲突**（合并前已用 `git merge-tree --write-tree` 预演：`main←G2` / `main←G4` / **`G4←G2`** 三者 EXIT=0）
- ⇒ ⇒ **天然满足 §5.3e 的合并顺序硬约束**：`3b573fb`（让 `ObservabilityLifecycle.shutdown()` **真的执行**）与 `7488b3d`（进程级静态池改为**按需重建**）**同批到位**（`git merge-base --is-ancestor` 逐个核对 = YES）⇒ ⇒ **不会**出现「池终止即永久 ⇒ 拦截器返 `INTERNAL` 而非 `DEADLINE_EXCEEDED` ⇒ 由绿转红」
- ⇒ 带入共享层 API `GoStyleRpcProtectionChain.compose(props)` / `layerNames(chain)`（G4 `182fc32`）⇒ ⇒ **FB-03 的域侧升级条件满足**
- ⇒ 带入 `cd893d1`：容器上限抬升与闸门有无**解耦**（OFF 域也抬到 10001）⇒ ⇒ 本域「停在 2048 ⇒ 比 Go 更严」那一面已消除（见上文「D-1 订正」）
- ⚠️ **口径纪律（合并后必须改口径）**：合并后**本分支相对主干的 diff 里含 `service/common/**`** ⇒ 但那些 commit 的**作者是 G4** ⇒ ⇒ **本 lane 没有写共享层**（写权仍归 G4）⇒ ⇒ 「共享面零触碰」这条自证在合并后要改述为「**本 lane 未新增、未修改任何共享层 commit**」⇒ ⇒ **整合者看 diff 时不要以为 G2 动了共享层**

#### ② 域侧守门升级（FB-03）—— 本域 `TaskProtectionChainWiringTest`：**3 条 ⇒ 5 条**

- **新增 ①**「链的层名列表 == `[recover, breaker, shedding]`」**且**「**实际装上的链 == 配置推出的构成**」（`layerNames(chain)` == `compose(props).layerNames()`）
  ⇒ 这条**一次同时钉住**「有 breaker」「有 shedding」「层序 = recover→breaker→shedding」（Go `go-zero@v1.10.2/zrpc/server.go:144-167`）
  ⇒ 而「实际链 == 配置推出的构成」能抓住「**配置说三层、实际只装两层**」这类**装配与配置脱节**的漂移 ⇒ ⇒ **旧判据完全覆盖不到**
- **新增 ②**「关掉降载 ⇒ 层名列表变成 `[recover, breaker]`」⇒ ⇒ 证明**降载层是条件装的、不是恒在**（Go `go-zero@v1.10.2/zrpc/server.go:160-163`：`CpuThreshold > 0` 才挂）⇒ ⇒ 旧判据要靠「**关掉降载来换取确定性**」，新判据**不必关**
- ⇒ ⇒ **旧的两条「包装层归属」保留不删** ⇒ ⇒ **互补而非替代**：层名证「有哪些层 + 层序」，包装归属证「那些层**真的包住了调用**」
- ⇒ ⇒ **本域 A2 轴：L1（源码）⇒ L2（运行期 · 守门 + 变异验证）**

#### ③ 变异验证（判据非假绿）

- 变异方式 = **断言方向变异**（把期望层名改成 `["recover","breaker"]`）⇒ ⚠️ **被测机制在共享层、不在本 lane 写权** ⇒ ⇒ 只能做方向变异（证明**断言读的是运行期真值**，不是常量）⇒ ⇒ **这条是「回归网」的证据，不是「已证伪该机制」**
- 结果：**三个模块各红且只红 1 条**，红点**精确**在 `chainLayerNamesMatchComposedChain`（task `:187` · like `:132` · message `:102`）⇒ ⇒ **其余全部阴性对照仍绿**（含「关掉降载 ⇒ `[recover, breaker]`」那条 —— 它本就该绿）⇒ ⇒ **非假绿**
- ⇒ 还原后复跑：task **5** / like **4** / message **5** · **0 failures / 0 errors / 0 skipped** · BUILD SUCCESS
- ⚠️ **还原脚本的判据坑（我自己踩到）**：变异后文件里有 **2 处** `containsExactly("recover", "breaker")`（被我改错的那处 + 条件装那条本来的）⇒ ⇒ 「按字面量全局替换」的还原脚本会**断言失败并中止**（文件停在变异态）⇒ ⇒ **还原必须按「位置/上下文」精确替换**（本轮改用「替换第一处 + 校验替换后全文只剩 1 处 FULL」）

#### ④ 数字订正

- **task-rpc**：`mvn test` 口径 **117 ⇒ 119**（守门 +2 条）⇒ ⚠️ **`mvn verify` 口径 = 119 + 4（产物层 IT）= 123（推算，本轮未跑 `verify`）**
- ⇒ 🟢 **这条 stale 是被守门自己抓到的**：`check-ledger-selfcheck.py` **层 3（数字断言）**红 ⇒「台账 117 / 实测 119」⇒ ⇒ **这正是那个守门存在的意义**（台账数字过期时它会报警，而不是等人工发现）⇒ ⇒ **合并 G4 后凡测试数有变 ⇒ 必须同步台账，否则层 3 变红**

#### ⑤ 🟢 **合并后回归（实测 · 2026-10-27 13:31:32 → 13:39:12 · 7:39 min · `MVN_EXIT=0` · BUILD SUCCESS）**

reactor = **`service/common` + G2 三域 8 模块**（共 **9** 个）⇒ ⇒ **为什么必须把 `service/common` 放进 reactor**：`layerNames` / `compose` 只存在于**合并后的共享层源码**里，而 `~/.m2` 里还是旧 jar ⇒ ⇒ 若只跑 8 个域模块 ⇒ 它们会编译到**旧**共享层 ⇒ **编译不过** ⇒ ⇒ **替代方案是 `install` 共享层 jar 到 `~/.m2`** ⇒ ⇒ **我拒绝这么做**（那会改变别的 lane 正在解析的依赖 ⇒ 尤其 G4 当时正在跑第 12 次全量）⇒ ⇒ **只在 reactor 内解决**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**合并进来的共享层**） | 324 | 0 | 0 | 0 |
| `service/message/message-api` | 98 | 0 | 0 | 0 |
| `service/message/message-rpc` | **89** | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 89 | 0 | 0 | 0 |
| `service/like/like-rpc` | **115** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 97 | 0 | 0 | 0 |
| `service/task/task-rpc` | **119** | 0 | 0 | 0 |
| **合计** | **970** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 646**（合并前同口径 **637** ⇒ **+9** = task +2 ＋ like +2 ＋ message +5 ⇒ ⇒ **逐条对得上本轮新增的用例**）⇒ ⇒ **共享层自身 324 条全绿** ⇒ ⇒ **合并进来的共享层在我树里是绿的**（不是「合进来就假定它对」）
⇒ ⇒ ⚠️ **口径纪律**：① 数字取自**本轮 mtime 切的 surefire XML**（起跑标记 epoch `1793079092`）⇒ **0 stale 报告**（like-rpc 那 1 份孤儿 `LikeRpcServerSpanCurrentProbeTest` 被正确剔除 ⇒ 见上文口径段）② **并发事故披露**：起跑瞬间 **G3 与 G4 也同时起了 maven** ⇒ **三路并发** ⇒ 我的占用者检查**确实打印出了**那两个树，但被我紧跟的一句写死 echo「(空 = 可起跑)」**盖住** ⇒ ⇒ **我没有看到自己打的字就起了跑** ⇒ ⇒ 幸而机器 24 GB、`memory_pressure` 42% free、0 throttled page ⇒ **三路都跑完、无 OOM**（G4 的第 12 次全量正常完成、其台账已落「第十二次全量口径」）⇒ ⇒ **但这是运气不是纪律** ⇒ ⇒ **已落 memory：占用者检查的输出不许被任何写死的结论句盖住**

### 🔴 **C7 的硬前提：收口回归必须在【所有 lane 合入主干之后】跑，不能用任何 lane 的 lane 级全量代替**（2026-10-27 13:4x · 采信 G1 的实测并**独立复核**）

⇒ **G1 的实测依据**（G1 树 `b7ff946` · `docs/DRIFT/users/vs-common.md` §13.3）：G4 第八次全量里 **G1 域 6 个组件 = 605** 条，而 **G1 树 `fa608f3` 实测 = 667** 条（差 **62**）⇒ ⇒ 根因 = **G4 的树里没有 G1 的任何提交**（7 笔全 `NOT_IN_G4`）⇒ ⇒ **那份全量的工作区 ≠ 任何单域的当前态** ⇒ ⇒ **这不是「谁多跑了测试」，是「代码态不同」**

⇒ ⇒ **三条硬前提**（C7 执行时逐条对照；**我域本轮实测值已填**）：

| # | 前提 | 判据（可机械核） | 本域当前实测（2026-10-27 13:4x） |
|---|---|---|---|
| 1 | 收口回归的代码态必须包含**所有 lane** 的提交 | 回归前 `git rev-list --left-right --count do-my-own-gh...HEAD` ⇒ **落后数 = 0**；并逐 lane 核「该 lane 的 tip 已是祖先」 | ✅ **本树 = `0 104`**（左 0 落后 / 右 104 领先）<br>🔴 **但四个 lane 的 tip 全部尚未入主干**：G1 `b7ff946` / G2 `2e6e027` / G3 `a1e9271` / G4 `dcd0706` ⇒「已入主干 = **NO**」⇒ ⇒ **C7 现在还跑不了**（前置未满足） |
| 2 | **不得**引用任何 lane 的 lane 级全量数字当作**本域 / 波次**证据 | 每个 lane 的全量都在**自己的树**上跑 ⇒ 别的 lane 的提交不在里面 | 本域的 **646**（域侧 8 模块）/ **970**（含 `service/common`）**只能当「G2 树 × (G2 三域 ＋ 合并进来的共享层)」的证据** |
| 3 | 本域数字只有两个来源可采信 | ① **本域树上的本域回归** ② **所有 lane 合入后的收口回归**（C7，尚未跑） | ① = **646** / 970（口径见上）；② = **未跑** |

⇒ ⇒ ⚠️ **对「646 / 970」的诚实定位**：它是 **G2 树 × (G2 三域 ＋ 合并进来的共享层)** 的数字，**不是**波次最终数字 ⇒ ⇒ 它能证明「**我域那半在我树上是绿的**」，**不能**证明「**波次是绿的**」⇒ ⇒ **别把「谁的数字大」当判据**（605 vs 667 的差是**代码态不同**）

⇒ ⇒ 🔴 **一条对我域尤其要紧的补充**（G1 13:2x 实测 + 我本轮独立核实）：**我树已合并 G4 分支（`37816ca`）⇒ 我树里有共享层的 Redis 收编 / 池自愈 / span current；而 G1 树里一个都没有**（G1 树 `service/common` = 主干基线）⇒ ⇒ **同一波次里，不同 lane 的树的内容差异会越来越大** ⇒ ⇒ **汇合点只能是 `do-my-own-gh`（主干）** ⇒ ⇒ **任何 lane 的「全量」都不能代表其他 lane 的树** ⇒ ⇒ **我域的合并后收口回归必须在「所有 lane 入主干之后」跑**

⇒ ⇒ 📌 **本域台账里所有「本轮实测 N 条」的定位统一改述为**：**「G2 树 × (G2 三域 ＋ 共享层) 的 lane 级数字」** ⇒ ⇒ **它们不升级任何轴的波次级证据强度**（跑绿 ≠ 对 ⇒ 同一条纪律的第二个层级）

⇒ ⇒ 📌 **顺带确认 U+FFFD 的合并归属**（G1 的全表 20 份 / 51 处 与我域结论**逐条一致**）：我域那 **3 份**（`LikeOutboxEventEntity.java:17` · `GoStyleGrpcClientBreakerE2ETest.java:43` · `MessageRpcProductionAddressTest.java:96`）⇒ **合并时取 G2 树版本**（我树字节级 0 命中）⇒ ⇒ **与「共享层那 2 份取 G4 树 / users 那 3 份取 G1 树」同规则** ⇒ ⇒ **合并后请再跑一次全仓字节级扫描**（不是 grep）
⇒ ⇒ 📌 **补一条「计数单位」纪律（G1 的表头不闭合 ⇒ 我按表体逐类加回去）**：G1 全表给的「**处**」= **字节命中数**（**1 个 U+FFFD 字符 = 3 字节**）⇒ ⇒ 我域那 3 份 = **3 字符 = 9 字节**（与我树实测 9 个字节偏移完全一致）⇒ ⇒ **闭合账（按文件）**：**13 份真损坏**（G4 2 + **G2 3** + G3 5 + G1 3）＋ **4 份故意**（`UserApiGoStyleBodyContentTypeTest` / `ArticleApiMvcPipelineTest` / `docs/Pitfalls/users/user/rpc.md` / `docs/Pitfalls/message/rpc.md`）＋ **3 份无人拥有**（`tools/errmsg-probe/README.md` / `tools/sql-statement-probe/favorite/compare.py` / `component-semantic-verification/springboot-semantic-model.md`）= **20 份文件** ✔（与 G1 的「20 份」吻合）⇒ ⇒ **按字节**：34 + 6 + 11 = **51 字节 = 17 个 U+FFFD 字符** ✔ ⇒ ⇒ ⚠️ **G1 表头写的「9 份真损坏」与表体 13 行不闭合 ⇒ 正确值是 13** ⇒ ⇒ **分类计数必须能加回去**（加不回去 ⇒ 说明分类边界有漏）⇒ ⇒ **建议表头写「20 份文件 / 51 字节 = 17 个 U+FFFD 字符」并标明单位**（否则「份」与「处」两个单位混用）
⇒ ⇒ 📌 **「无人拥有」那 3 份的处置模式（我的建议，供整合者拍板）**：**owner 域出修法 + 整合者执行** ⇒ ⇒ 理由：① **内容归属** = `tools/sql-statement-probe/favorite/` 整个目录只服务 **favorite 域**（其 README 写明「favorite 域」· 补上 favorite 域四层验证的最后一层）⇒ ⇒ **内容属 G3**；② **路径归属** = `tools/**` **不在任何 lane 的写权白名单**（实测 `check-wave-lane-ownership.py` 的白名单里没有它）⇒ ⇒ **路径属整合者** ⇒ ⇒ **两者不冲突、也不是二选一** ⇒ ⇒ **别让 G3 去改一个它改不了的文件**（那会变成「有 owner、无写权」的悬空项）⇒ ⇒ **同理**：`tools/errmsg-probe/README.md` 与根目录 `component-semantic-verification/springboot-semantic-model.md` ⇒ ⇒ **三份统一交整合者 / global 拍板**
⇒ ⇒ 📌 **「各 lane 认领自己那几份」的原则（不代发）**：G1 那 3 份 = G1 树、G2 那 3 份 = G2 树、G3 那 5 份 = G3 树、G4 那 2 份 = G4 树 ⇒ ⇒ **owner 自己认领自己的那几份**（**代发者不是 owner** ⇒ 与「假转移单」同族）⇒ ⇒ **只有「无人拥有」那 3 份 + 全仓总表需要整合者持有** ⇒ ⇒ **一份权威表 + 合并后逐文件复扫**（不是逐份数命中数 ⇒ 「份数相同 ≠ 同一批文件」）



**⑧ 🟢 口径修正（第十七轮收口实测）：`mvn test` 与 `mvn verify` **不是同一个口径**
- 我域 9 模块里**只有 `task-rpc` 有 `*IT` 类**（`TaskRpcFatJarArtifactGateTest`）⇒ ⇒ 它**只在 `verify` 阶段跑**（task-rpc pom 里第二个 surefire execution，绑 `verify`）⇒ ⇒ **第十六轮那个 995 是 `mvn test` 口径** ⇒ ⇒ 同一 9 模块的 **`test` + `verify` 口径 = 995 + 5 = 1000**。
- **实测**：`mvn -o -B -DargLine=-Xmx2g -am -pl service/task/task-rpc verify` ⇒ **EXIT=0 · BUILD SUCCESS** ⇒ task-rpc = **121（`test` 阶段）+ 5（`verify` 阶段）= 126** / 0f 0e 0s。
- ⇒ ⇒ **台账里的数字继续按 `mvn test` 口径写**（`check-ledger-selfcheck.py` 第 3 层读 `target/surefire-reports` ⇒ 那就是 `test` 阶段口径）⇒ ⇒ **不要把 126 写进 `rpc.md:29` 的那一格** ⇒ ⇒ 但那一格「**4 条产物层**」的半句要订正成 **5 条**（本轮 +1）。

**⑨ ⚠️ 调用姿势的坑（我自己踩了一次，必须记）**：`-Dtest=...` 会**覆盖 surefire 的 includes** ⇒ ⇒ `*IT` 类被拉到 **`test` 阶段**跑 ⇒ ⇒ 而产物层守门要在 `package` 之后才有 jar ⇒ ⇒ **5 条全红**（失败消息 = 「`target` 下没有 `*.jar` 产物」）⇒ ⇒ **这是调用姿势错，不是产品缺陷** ⇒ ⇒ **正确调用 = `mvn verify` 且**不带** `-Dtest`**。
- ⇒ ⇒ **同一机制的第二条证据**：拿 `~/.m2` 的陈旧 `common` jar 去构建 task-rpc ⇒ ⇒ **测试编译就挂**（`TaskProtectionChainWiringTest` 报「找不到符号：`GoStyleRpcProtectionChain.layerNames(...)` / `.compose(...)`」）⇒ ⇒ **这反过来独立印证了「单模块构建吃的是陈旧 `common` jar」**（那条陈旧包是 12:00 的，缺后来新增的 API）。

**⑩ ✅ 新守门的变异验证（task IT 那条）**
- like 那条用「去掉 `-am` ＋ 删旧 fat jar 强制重建」变异 ⇒ **3 条恰红 1 条**（红在 `:147`）。
- **task 这条不能走同一条路**（见 ⑨ 的编译失败）⇒ ⇒ 改用**产物 zip 手术**：把 fat jar 里 `BOOT-INF/lib/common-0.0.1-SNAPSHOT.jar` 换成 `~/.m2` 那份陈旧包 ⇒ ⇒ 实测**两边 sha256 逐字节相同**（`45f41836d945347e5bed288e9573c06d`）⇒ ⇒ 它的 `imports` 只有 **2 行**、排序桥 **0 行** ⇒ ⇒ 再**单独**跑那条 IT（`mvn surefire:test -Dtest='TaskRpcFatJarArtifactGateTest'` —— **不跑生命周期** ⇒ ⇒ 手术结果不会被 `package` 覆盖）⇒ ⇒ **5 条里恰红 1 条**，红在**目标断言** `fatJarMustCarrySharedLayerOrderingBridge:205`，失败消息 = 「内嵌的 `BOOT-INF/lib/common-0.0.1-SNAPSHOT.jar` 的 `AutoConfiguration.imports` 里【没有】共享层排序桥」⇒ ⇒ **恰好 1 条红**、另 4 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原（换回 reactor 的 jar，排序桥行数回到 1）后复跑 ⇒ **5 条全绿**。

**⑪ 🔴 残留登记（本轮两次实测红 ⇒ 都判为**负载敏感型时序 flake**，但必须留痕）**
- **① 本域 `TaskRedisDisconnectFastRejectGateTest` ③ B 组（对照组）**：一次全量跑里实测红 —— `Expecting actual: 0L to be greater than or equal to: 2000L` ⇒ ⇒ **隔离复跑 3 次全绿**（A=3/1/1ms · B=3023/3005/3042ms）⇒ ⇒ 判为**瞬态**。⚠️ 机制（留给下轮）：B 组依赖「客户端**还没**处理到 FIN」这个**竞态窗口** ⇒ FIN 若先被处理 ⇒ 连接是「已关闭」而不是「断连中」⇒ ⇒ 命令**立刻失败**（0ms）而不是排队到 3s 命令超时 ⇒ ⇒ **该对照组断言不是确定性的** ⇒ ⇒ **本轮只登记、不改断言**（改断言让它绿 = 掩盖现象）。
  - ✅ 该测试**自起自销**临时容器（`g2-task-redis-reject-*` / `g2-task-redis-default-*`，`finally` 里 `docker rm -f`）⇒ ⇒ **不碰任何共享容器** ⇒ ⇒ **不是跨 lane 干扰源**（本轮实测 `docker ps`：`difftest5k-redis` 等共享容器全部 `Up`，且 `PING` → `+PONG`）。
- **② 共享层 `RpcInterceptorClientDeadlineGateTest.effectiveTimeoutMustInheritClientDeadline`**：一次全量跑里实测红（`[handler 必须真的被调用过（否则测的不是超时，是空调用）]`）⇒ ⇒ **隔离复跑仍红** ⇒ ⇒ **不是负载偶发**；机制 = 客户端 deadline 只有 **100 ms**（`CLIENT_DEADLINE_MS = 100`，服务端预算 `SERVER_TIMEOUT_MS = 300`）⇒ ⇒ 机器一忙，客户端 deadline 先到 ⇒ ⇒ 服务端拦截器链**提前中止** ⇒ ⇒ handler 从没被调用 ⇒ ⇒ **共享层的「预算过紧」守门** ⇒ ⇒ 落点 `service/common/**` ⇒ ⇒ **非我写权面 ⇒ 只报 G4**（本轮已报）。
  - ⚠️ **代价（对我域的实际影响）**：`service/common` 在我域每次 `-am` 回归的 reactor 里 ⇒ ⇒ **共享层这条一红，我域的 `clean test` 就整条链断在我域之前** ⇒ ⇒ 本轮实测因此**连跑两次没拿到绿的 task-rpc 报告集** ⇒ ⇒ ⇒ **「我域全绿」的前置条件包含「共享层在当前机器负载下能绿」** ⇒ ⇒ 这一格登记在**共享层名下**（owner = G4），我域**不自修**。
---

#### 🆕 第六轮（2026-10-27 13:46–13:58）—— **go-zero 库内路径统一加前缀**（G1 提出的同一件事，我域自查后落码）

> **触发**：G1 在它域把裸库内路径统一成 `go-zero@v1.10.2/<path>`（142 处 / 11 份文件）⇒ ⇒ **我域自查发现同类**（本域 **23 处裸库内路径 / 3 份（api.md 11 ＋ rpc.md 4 ＋ vs-common.md 8） 份台账**）⇒ ⇒ **本轮按同一约定落码**（纯表达层、零行为影响）。

**① 改写结果（实测 · `git` 前后对比，不是推算）**
- 改写前：本域台账 **裸库内路径 23 处** ＋ **已带前缀 5 处**
- 改写后：**裸库内路径 = 0** ＋ **已带前缀 28 处** ⇒ ⇒ **新增前缀 23 处**（23 处裸路径 ＋ 1 处新增全路径引用）
- **纯替换**：**路径改写那部分** = **140 insertions = 140 deletions** ⇒ ⇒ 零行增删（整笔提交还含本段台账文字 ⇒ 那是新增行、不属改写）
- ⚠️ **计数口径**：**整文件正则**（不是逐行 `grep`）⇒ 跨行的路径引用只有整文件口径数得到

**② 判据（实测，不是转述 G1 的说法）**
- 🔴 **订正 G1 的一处表述**：G1 写「真源仓顶层 = **只有 `service/`**」⇒ ⇒ **实测不准确** —— 真源顶层实测 **16 项**（`service` · `api` · `proto` · `doc` · `grafana` · `prometheus` · `go.mod` · `docker-compose.yaml` …）⇒ ⇒ 但 **G1 的核心判据成立**：真源仓里 **`core` / `rest` / `zrpc` / `gateway` / `mcp` 五个首段全部零命中**（`find -maxdepth 2 -type d` 实测）⇒ ⇒ **「首段 ∈ 五者 ⇒ 必是库内路径」成立** ⇒ ⇒ **我按【判据】办，不按【表述】办**
- ✅ **版本锁定**：真源 `go.mod:17` = `github.com/zeromicro/go-zero v1.10.2` ⇒ ⇒ 前缀写 `v1.10.2` 不是自选
- ✅ **不是只按约定改，是逐条验证过**：go-zero v1.10.2 的**模块缓存就在本机**（`~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）⇒ ⇒ 本域 23 条**逐条核「文件存在 + 行号在范围内」** ⇒ ⇒ **23 条通过**、**1 条越界** ⇒ ⇒ 越界那条已订正（见 ④）
- ✅ **内容抽验（3 条，逐行读过，不是只看行号在范围内）**：`go-zero@v1.10.2/zrpc/server.go:144-167` = `setupUnaryInterceptors`（recover:149 / breaker:158 / shedding:160-163 ⇒ **与我台账的链序结论逐字吻合**）· `go-zero@v1.10.2/rest/httpx/util.go:15` = `maxFormParamCount = 2048`（⇒ 与我域「两侧都不可达」的判据吻合）· `go-zero@v1.10.2/core/breaker/googlebreaker.go:52-79` = `accept()`

**③ `internal/` 那一类：逐处判归属 ⇒ 1 处全部是真源仓组件内路径 ⇒ 一处不加前缀**
- `internal/` 是**唯一两个仓都有的顶层目录** ⇒ 裸写无法机械区分 ⇒ ⇒ 本域按三问判：① `<libdir>/internal/<rest>` 在模块缓存里存在吗？② 否则真源仓里有同后缀文件吗？③ 否则未决 ⇒ ⇒ 实测 **库内 0 / 真源 1 / 未决 0** ⇒ ⇒ **全部是真源仓**（如 `internal/logic/helper.go` · `internal/errcode/grpc.go` · `internal/model/message.go`）⇒ ⇒ **语境已含组件，不构成误读 ⇒ 不动**
- ⚠️ **附带辨析**：像 `go-zero@v1.10.2/zrpc/internal/codes/accept.go` 这种**已带父目录段**的 ⇒ 它本来就命中「首段 ∈ 五者」⇒ **已在前缀规则内** ⇒ **不是「裸 `internal/`」那一类**

**④ 顺带订正 3 处引用缺陷（结论不变、指针订正；保留原值 + 订正注记）**
- 原写 **`go-zero@v1.10.2/rest/handler/breakerhandler.go` 的区间 19–44** ⇒ ⇒ **该文件只有 42 行** ⇒ 上界 **越界** ⇒ ⇒ 订正为 **19–41**（函数体 18–41）
- `breakerhandler.go:39` ⇒ ⇒ `:39` 实为 `next.ServeHTTP(cw, r)`；「`cw.Code < 500 ⇒ Accept()`」的真身在 **`:33`**（`cw.Code < http.StatusInternalServerError`）＋ **`:34`**（`promise.Accept()`），`:36` 是 `promise.Reject(...)` ⇒ ⇒ 订正为 **`go-zero@v1.10.2/rest/handler/breakerhandler.go:33-34`** ⇒ ⇒ **结论本身成立，只是指错了行**
- 另 1 处**既有双引用**（外层散文已写 `go-zero@v1.10.2`、内层代码块又是裸路径）⇒ 改写后会变双前缀 ⇒ ⇒ **收敛成一处**

**⑤ 门禁前后逐项相同 ⇒ 零行为影响（实测）**
- `tools/check-ledger-selfcheck.py`：**EXIT=0** · 台账 file:line 引用 **134** 条 · 引用不悬空 **114** · 已去噪 **20** · 跨库歧义 **29** ⇒ ⇒ **改写前后这四个数字完全一致** ⇒ ⇒ 改写没有动到门禁的任何判定
- ⚠️ **口径边界（别把门禁的覆盖当成本域的证据）**：门禁第 4 层的文件集 = `ledger_texts()`（`:243`）→ `LEDGER_FILES.get(domain)` ⇒ ⇒ **默认运行只扫 `LEDGER_FILES['task']` 的 6 份台账** ⇒ ⇒ 覆盖我域的 **`task/rpc.md` ＋ `task/api.md`** ⇒ ⇒ **message / like 两域的台账【不在】该层扫描集内** ⇒ ⇒ 那两域「引用可解析」的证据是**我自己逐条对模块缓存验证**（{BARE} 条逐条核过），**不是门禁给的** ⇒ ⇒ 别把「门禁绿」当成「三域都被门禁核过」
- 其余 3 个标准门禁（`check-ledger-count` · `check-wave-lane-ownership` · `check-flyway-ledger`）**EXIT=0**；写权面 **340 文件字节级 U+FFFD = 0 命中**
- ⚠️ **两个共享门禁仍红**（`check-tomcat-limits.py` · `check-wave-coupling.py`）⇒ **与本轮无关**（`tools/**` 不在我写权）⇒ 仍等 G4

**④-2 🔴 顺带发现【同源缺陷在别的域也存在】⇒ 派单（owner 域自修，我不代改）**
- 我域修掉的那处越界引用，**G3 的 hot 域有一份同源的**：`docs/DRIFT/hot/api.md:1697` ＋ `:1746` 同样写 **`go-zero@v1.10.2/rest/handler/breakerhandler.go` 的区间 19–44** ⇒ ⇒ **global 已在 `docs/DRIFT/global/_README.md:569` 把它登记为 hot 域漂移**（“🔴 **行号越界** —— go-zero `go-zero@v1.10.2/rest/handler/breakerhandler.go` 只有 42 行”）⇒ ⇒ **⇒ 说明这条是跨域同源缺陷，不是本域独有**
- ⇒ ⇒ **我域那份此前【未登记】**（本轮自修：`:19-44` → `:19-41`，另加订正注记）⇒ ⇒ **hot 域那份按 global 的登记走**（owner = G3）⇒ ⇒ **我不代改**（代发者不是 owner ⇒ 同「假转移单」）
- ⇒ ⇒ **顺带**：global 的登记里用的是 **裸 basename**（**原引用** `breakerhandler.go:19-44`）⇒ ⇒ 加上前缀后**越界判定会更准**（裸 basename 靠 basename 索引，可能匹配到别的库同名文件）

**⑥ 🔴 遗留一整类：裸 basename 引用（本域 184 处）⇒ ⇒ 【不机械改】+ 登记**
- 本域裸 basename（形如 `errmsg.go:117-132`）共 **184 处**，分三类（实测）：**库内唯一**（basename 在 go-zero 里唯一）· **库内同名多候选**（如 `config.go` 在库里有 **14** 个同名 · `unmarshaler.go` **3** 个 · `types.go` **2** 个）· **真源仓 / 标准库**（如 `errmsg.go` · `sendchatmessagelogic.go` ⇒ 真源仓里同名）
- ⇒ ⇒ **不能机械改**：机械改会把「真源仓的 `message.go`」误标成「库内的 `go-zero@v1.10.2/core/trace/message.go`」⇒ ⇒ **正确处置 = 登记 + 派单**，不是无脑替换
- ⇒ ⇒ **复开触发条件**：当 G4 把门禁的「精确定位通道」修通（见 ⑦）⇒ ⇒ 那时才有**机器可判**的消歧依据 ⇒ ⇒ 到那时本条才可批量收敛

**⑦ 🔴 工具侧发现（`tools/**` = G4 写权）：门禁第 4 层的「精确定位通道 ① `module@version/...`」从台账文本【永远不可达】**
- 门禁 `:1442-1444` 的注释明写「`module@version/...` ⇒ 直接在 module cache 里找（basename 索引解析不到完整模块路径 ⇒ 假 FAIL；直接定位顺带消除同名歧义）」⇒ ⇒ **设计意图与本轮改写完全一致**
- 🔴 **但它从台账文本进不去**（探针实测 · `/tmp/g2-probe-precise-channel.py`）：`REF_RE`（`:208`）的字符类 `[A-Za-z0-9_./-]` **不含 `@`** ⇒ ⇒ `go-zero@v1.10.2/rest/httpx/util.go:15` 被截断成 **`v1.10.2/rest/httpx/util.go`** ⇒ ⇒ `_direct_module_candidate`（`:1034`）返回 **None** ⇒ ⇒ **短式与全式前缀都触发不到**
- ⇒ ⇒ **根因 = 提取器与解析器的契约不一致**（解析器要 `module@version/path`，提取器却在 `@` 处断掉）⇒ ⇒ **该通道实为死代码**（工具自测 `:1882` 是**直调函数、绕过 `REF_RE`** ⇒ 所以自测过、真跑不通）
- ⇒ ⇒ **修法在 G4**：`REF_RE` 字符类加 `@` ⇒ ⇒ 但**加了之后能触发的必须是全式** `github.com/zeromicro/go-zero@v1.10.2/<path>`（`_direct_module_candidate` 的正则要求模块段**含 `.`** ⇒ 短式 `go-zero` 不满足）⇒ ⇒ **两种写法并存 ⇒ 口径归 G4 / global 拍板**
- ⚠️ **我域的选择**：本轮用**短式** `go-zero@v1.10.2/<path>` ⇒ 理由 = 与 global 台账**既有写法同形**（`docs/DRIFT/global/common-tails.md:51` 等实测）⇒ ⇒ **跨域一致性优先** ⇒ ⇒ **不擅自改跨域约定**

**⑧ 本域当前状态**：落码前 HEAD = `align/g2-message-like-task` @ `05ac555` · **落后 0 / 领先 106** ⇒ ⇒ 本轮提交后 = **领先 107** · 4 个标准门禁 EXIT=0 · 写权面 340 文件 U+FFFD = 0

---

#### 🆕 第七轮（2026-10-27 14:1x）—— **§5.3e「合并后必做」清单的销号**（本域 （task 域））

**① 销号：项①（3 处 `DEADLINE_EXCEEDED` 断言）与 项③（Redis 池守门 ⑤⑥）**
- ⇒ ⇒ **这两项已被 13:31–13:39 的合并后 9 模块回归覆盖并通过**（`MVN_EXIT=0` · **970 条 / 0 failures / 0 errors / 0 skipped**）⇒ ⇒ 覆盖本域的：**task-rpc 119**（含 `TaskGrpcInterceptorWiringTest` 的 `DEADLINE_EXCEEDED` 断言 ＋ 池守门 ⑤⑥ 6 条）· **like-rpc 115**（含 `LikeRpcGrpcInterceptorWiringTest` ＋ 池守门 4 条）· **message-rpc 89**（含 `MessageRpcTimeoutGateTest`）
- ⇒ ⇒ ⚠️ **口径**：那是**我域树 × (三域 ＋ 共享层)** 的 **lane 级**回归 ⇒ ⇒ **不等于 C7 波末收口回归**（C7 的三条硬前提仍未满足：四个 lane tip 全部未入主干）⇒ ⇒ **这两项在本域侧销号，波次级仍待 C7**

**② 销号：项⑧（合并后全仓字节级 U+FFFD 复扫）**
- **本轮全仓复扫（源码 + target/ 全扫）**：**4103 份文件** ⇒ **18 份含 U+FFFD / 60 字节**；**剔除 `target/` 构建产物** ⇒ **15 份 / 38 字节**
- ⇒ ⇒ **差额已逐条解释**（不是「漏了」）：
  - **3 份 / 22 字节** = `target/` 里的 **3 个 jar**（like-rpc 8 ＋ task-api 6 ＋ task-rpc 8）⇒ ⇒ 它们的构建时间是 **12:00 / 12:48**，**早于**我域那 3 个源文件的修复 ⇒ ⇒ **是旧构建产物，不是当前源码缺陷**（那 3 个源文件现测 **FFFD = 0**）
  - **2 字节** = `docs/Pitfalls/message/rpc.md:911` ＋ `:921` ⇒ ⇒ **故意保留的故障样本**（`:911` 是真实报错文本，其中变量名后跟了一个 **U+FFFD 替换符**（本行**不复制**那个字符，只描述）；`:921` 是解释「那个乱码替换符」）⇒ ⇒ **不是真损坏**
- ⇒ ⇒ **本域真损坏 = 0**（我域写权面 **340 文件字节级 = 0 命中**）
- ⚠️ **跨树口径纪律**：G1 全表给的「20 份 / 51 字节」是**G1 树**的数字 ⇒ ⇒ **跨树不可直接比**（同一批文件在四棵树里各不相同 ⇒ 「份数相同 ≠ 同一批文件」）⇒ ⇒ **本树口径 = 15 份 / 38 字节（源级）**

**③ 仍未销号（与本轮无关，等 G4）**
- **项②**（共享层 `REJECT_COMMANDS` 断连活体对拍）：**本轮未做**（需 docker ＋ 断连窗口配方）⇒ ⇒ 仍是开放项
- **项④**（E66 落码后守门 ① 仍绿）：**E66 未落码** ⇒ ⇒ N/A（等落码）
- **项⑤**（A3 升 ✅ 的真 Tomcat 探针）：✅ **已做**（第十轮 · 真 Tomcat ＋ A/B 对照组 ＋ 本域独立变异验证）
- **两个共享门禁**（`check-tomcat-limits.py` · `check-wave-coupling.py`）仍红 ⇒ ⇒ **`tools/**` 非本 lane 写权 ⇒ 仍等 G4**（本轮新增证据：那三个门禁依据文件在**我树 vs G4 最新 tip `c814689`** 之间**逐字节相同** ⇒ ⇒ G4 树里它们绿**是因为不含我域改动**，不是「已修」）

---

#### 🆕 第八轮（2026-10-27 14:3x）—— **§5.3e 项② 销号：共享层那份 `REJECT_COMMANDS` 在本域【真的生效】**（活体断连 A/B）

**① 这条补的是哪一个缺口（别当成「新做一遍」）**
- 我域上轮已做过一次**真 Redis 容器**的断连实测（记在 `LikeRpcRedisTimeoutsGateTest` 的 javadoc 对拍表里）：
  Go 侧 go-redis **拒连快速失败 = 1694 ms** · RAG 侧未配 timeout **排队等重连 = 60021 ms** · RAG 侧配 3s **快速失败 = 3 ms**
- ⇒ ⇒ **但那次的条件是「手动配 `REJECT_COMMANDS`」** ⇒ ⇒ 它证明的是「**该配置项有效**」，
  **不是**「**共享层那份已在本域生效**」⇒ ⇒ **本条补的就是这一格**

**② 新守门 `TaskRedisDisconnectFastRejectGateTest`（2 条）**
- **① 共享层那份 customizer 在场且【没被域内副本遮蔽】**：
  `getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` 的 **size == 1** ＋ bean 名含
  **`goStyleRedisClientOptionsCustomizer`** ⇒ ⇒ 共享层那份带 `@ConditionalOnMissingBean`
  ⇒ ⇒ **size==1 ＋ 名字对得上**两条合起来才证明「用的就是共享层那份、**没有域内副本遮蔽**」
- **② 断连行为真被设成 `REJECT_COMMANDS`**：**消费点** =
  `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()`
  ⇒ ⇒ **不是读配置项**（那是「声明」），也不是读 `ClientConfiguration`
- **③ 断连活体 A/B**（真 docker redis 容器）：连上 → PING → **静默等待（期间不发命令，否则 watchdog 重连会掩盖排队窗口）**
  → `docker stop`（优雅 FIN，不是 kill 的 RST）→ 量**第一条命令**的耗时
  - **A 组 = 共享层产出的那套 ClientOptions** ⇒ **实测 0 ms**
  - **B 组 = 同一套、只把断连行为换成 Lettuce 默认的 `DEFAULT`** ⇒ **实测 3004 ms**（排队到 3s 命令超时）
  - ⇒ ⇒ **A/B 唯一差别就是那条机制** ⇒ ⇒ **这才是「机制是承重的」的证据**，而不是「反正都快」

**③ 变异验证（红在目标断言）**
- 变异 = **把 A 组也换成 `DEFAULT`**（模拟「共享层那份没生效」）⇒ ⇒ 比「改断言值」更强
- 结果：**2 条里只红 1 条**，红点**精确**在 `disconnectIsFastRejectedWithSharedLayerOptions:201`；
  且探针打印出 **A=3005 ms / B=3003 ms** ⇒ ⇒ **测量确实跟着机制走**（不是碰巧）
- ⇒ ⇒ 还原**逐字节一致**（md5 `2fa201c2…` 前后相同）⇒ ⇒ 复跑 **2 条绿**（A=0 ms / B=3009 ms）

**④ 数字：Task-rpc 119 ⇒ 121（+2 = 本条 2 条）**
- 实测：`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/task/rpc test`
  ⇒ **121 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**（mtime 切分口径 **15 份报告 / 121 条**
  ⇒ **与 maven 汇总行相等** ✔）
- ⚠️ **`service/common` 必须在 reactor 里** —— `~/.m2` 的 `common` jar 是**旧**的（不含 FB-04 / 不含共享层的最新装配）⇒ ⇒ 只能**在 reactor 内**解决，**不 `install`**（那会改变别的 lane 正在解析的依赖）

**⑤ ⚠️ 一条「不外推」的纪律（本轮两个域各做了一遍）**
- like 域与 task 域**各跑了自己的活体 A/B**，**没有**拿一个域的结论去覆盖另一个域
  ⇒ ⇒ 理由：**域上下文不同 ⇒ customizer 数量可能不同 ⇒ 行为可能不同** ⇒ ⇒ 「共享层那个 bean 一样」**推不出**「两个域的行为一样」
- ⇒ ⇒ **全仓实测**：`LettuceClientOptionsBuilderCustomizer` 的**域内副本只有 1 处** —— 在 **users 域**
  （`service/users/user/user-api/.../RedisDisconnectedBehaviorConfig.java:103`）⇒ ⇒ **我三域零遮蔽副本** ✔<br>🆕 **2026-10-27 第十八轮订正（G1 [1] 回信）**：G1 已在他们的 `312a09b`**删掉全部 5 份域内副本** ⇒ ⇒ 「users 域那份会遮蔽共享层」这个前提**在 G1 侧已不成立** ⇒ ⇒ **但我树未合入 `312a09b`** ⇒ ⇒ 我树里那份**仍在**（实测 `find service/users service/follow -name RedisDisconnectedBehaviorConfig.java` = 1 命中）⇒ ⇒ **本条陈述对我树仍成立** ⇒ ⇒ 合并后该前提自动消失 ⇒ ⇒ **结论不变**（我三域零遮蔽副本 ✔）。
  ⇒ ⇒ （users 域那份**会遮蔽**共享层 ⇒ 那是 G1 域的事 ⇒ 本域不代改）

---

## 4·九、🔵 **第九轮（2026-10-27 17:1x）· G1 来的「波次级通用判据」⇒ 本域侧逐条处置**（G2 全部**独立取证**，不采信转述）

### ① 🟡 **命令超时判据联动**（**同意 G1 的口径；但「联动」的真实机制与 G1 写的不一样** —— 已从 Boot 4.0.8 **源码**读出）

- **两域守门现状**（实测）：like 域 `LikeRpcRedisTimeoutsGateTest` **6 条**（①②③④ 超时相关 ＋ ⑤⑥ 池）· task 域 `TaskRedisCommandTimeoutGateTest` **6 条**（同结构）⇒ ⇒ **超时相关 = 各 4 条，合计 8 条**（与 G1 口径一致）· **两域合计 12 条**
- **判据链两域均已落地**（**不是「没做」**）：
  - like ① `isTimeoutCommands()` ＋ `isApplyConnectionTimeout()`（两个机制位合起来 = `TimeoutOptions.enabled()` 的全貌）· ② `getClientConfiguration().getCommandTimeout() == 3s`（消费点）· ④ **阳性对照** `RedisURI.create(...)` 默认 `getTimeout() == 60s`（证明断言有识别能力）
  - task 同构：① 两机制位 · ② `socketOptions.connectTimeout == 5s` · ③ `RedisURI.timeout == 3s` · ④ 60s 阳性对照
- ✅ **同意 G1 的口径**：`isTimeoutCommands()` **单独**不构成「时长会被施加」的判据 ⇒ 本域两份守门已把它定位成「机制位①」· **时长另由「消费点」那条覆盖** ⇒ ⇒ **不是「恒真 ⇒ 无效」，而是「单独不足」** ⇒ 两腿合起来 = 完整链
- 🔴 **但「联动」的真实风险面不是 G1 写的那条** —— G2 **从 Boot 4.0.8 源码**独立读出装配顺序（`~/.m2/.../spring-boot-data-redis-4.0.8-sources.jar` ⇒ `LettuceConnectionConfiguration`）：
  - `:208-210` `createClientOptions(ObjectProvider<LettuceClientOptionsBuilderCustomizer> clientConfigurationBuilderCustomizers, …)`
  - `:211` `ClientOptions.Builder builder = initializeClientOptionsBuilder();`
  - `:212-215` `connectTimeout` ⇒ `builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())`
  - `:229` `builder.timeoutOptions(TimeoutOptions.enabled());`
  - **`:230` `clientConfigurationBuilderCustomizers.orderedStream().forEach(c -> c.customize(builder));` ⇒ customizer 在最后执行**
  - ⇒ ⇒ `ClientOptions.Builder` 的 `timeoutOptions(...)` / `socketOptions(...)` 是**替换**语义 ⇒ 共享层那份 customizer 一旦调它们 ⇒ **整体覆盖** Boot 刚设的值
  - ⇒ ⇒ **可达性分级**（本域判据的真实风险面）：

    | 本域判据 | 落点 | 共享层 customizer 能否覆盖 |
    |---|---|---|
    | 命令超时 3s | `LettuceClientConfiguration.Builder.commandTimeout`（`:174`）⇒ `getCommandTimeout()` ⇒ `RedisURI.timeout` | **不能**（不是同一个 builder） |
    | 连接超时 5s | `ClientOptions.Builder.socketOptions`（`:213-214`） | **能**（`:230` 在其后） |
    | 机制位 `timeoutOptions.enabled()` | `ClientOptions.Builder.timeoutOptions`（`:229`） | **能**（`:230` 在其后 ⇒ 覆盖 ⇒ `applyConnectionTimeout` 位丢失） |

  - ⇒ ⇒ **联动条款（登记 · 跨域）**：**共享层 `GoStyleRedisClientAutoConfiguration` 若未来要碰 `timeoutOptions(...)` / `socketOptions(...)`（E66「Redis 命令感知 deadline」正是这条路）⇒ 必须与受影响域的守门同一次提交一起改** ⇒ ⇒ 否则 like-rpc / task-rpc 的 ①②③ 与连接超时 5s 会**同时**变红 ⇒ ⇒ **触发条件 = 共享层 customizer 新增上述调用**

### ② ✅ **`-pl` 不带 `-am` ⇒ classpath 上是 `~/.m2` 的旧 `com.rag:common`**（G1 实测踩到）—— **本域已规避**

- G1 的实测现象：`-pl <域内模块>`（**不带 `-am`**）⇒ reactor 里**没有** `service/common` ⇒ `com.rag:common` **从本地仓库解析** ⇒ 那个 jar 是**主干代码态** ⇒ ⇒ **共享层改动根本没进过测试 classpath** ⇒ ⇒ 「跑绿」是**假绿**
- ✅ **本域全部回归命令都显式把 `service/common` 放进 `-pl`** ⇒ 等效于带 `-am`（用**本树源码**）⇒ ⇒ **本域不受该坑影响**
  - 实测命令形如：`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/like/like-rpc test`
  - ⚠️ **不 `install`** 共享层 jar 到 `~/.m2`（那会改变别的 lane 正在解析的依赖）
- ⚠️ **口径纪律**：`service/common` 在 reactor 里 ⇒ **reactor 总数 ≠ 本域数**（共享层自己的 **324** 条会进汇总）⇒ ⇒ **本域数字按本域模块的 surefire 目录**统计（`service/{message,like,task}/**/target/surefire-reports/TEST-*.xml`）⇒ ⇒ **不把 reactor 总数当本域数**

### ③ 🟢 **G1 [3]「连带删 `@Import` 不够」—— 本域实测「不适用」**

- G1 的坑：守门用 `@SpringBootConfiguration`（**不含** `@EnableAutoConfiguration`）⇒ **自动配置清单不会被读** ⇒ 单纯删 `@Import` ⇒ 容器里 customizer = 0 ⇒ ⇒ **守门静默测不到任何断连策略**（零报错）
- ✅ **本域实测不适用**：`service/{message,like,task}/**` 内 **`@SpringBootConfiguration` = 0 命中** ⇒ ⇒ 本域**没有**「最小上下文」守门 ⇒ ⇒ 该坑不适用
- ✅ **本域两个新 Redis 守门用的是真应用类**：`@SpringBootTest(classes = LikeRpcApplication.class)` / `TaskRpcApplication.class`（**真 `@SpringBootApplication`** ⇒ 含 `@EnableAutoConfiguration` ⇒ **清单会被读**）⇒ ⇒ 且已用 **`size()==1` ＋ bean 名含 `goStyleRedisClientOptionsCustomizer`** 双重断言坐实「共享层那份确实在容器里」⇒ ⇒ **即使清单没被读，断言也会红** ⇒ ⇒ **不会静默测不到**

### ④ ⚠️ **U+FFFD 跨树数字不可直接比**（G1 合并后全仓 = **23 份 / 64 处**）

- G1 报：合并后全仓 **23 份 / 64 处**（原 20 份 / 51 处）⇒ 增量来自 G1 台账**引述了坏字节**（把「真损坏」现场当证据写进台账）⇒ ⇒ G1 域真损坏 = 0
- **G2 本树口径**：全仓 **4103 份**扫描 ⇒ 含 `target/` **18 份 / 60 字节** · 剔 `target/` **15 份 / 38 字节**（源级）
- ⇒ ⇒ **两个口径（份数 / 处数）与构成都不同** ⇒ ⇒ **复扫要比「文件清单」而不是比数字** ⇒ ⇒ **本域写权面 340 文件 = 0 命中**

### ⑤ ✅ **`application.yaml` 不改名（G1 (b)）—— 本域同意，走「台账登记 ＋ 复开触发条件」**

- **同意 G1 的三条理由**：收益仅消除 **1 个**命名误判点 / 成本 ≈ **42 处**引用且其中 3 处 ＋ `docs/Pitfalls/users/_shared.md` 2 处**不在任何 lane 白名单** / 扩展名是**纯表达层、零语义收益**
- **本域现状**：`service/{message,like,task}/**/src/main/resources/application.yml`（`.yml`）⇒ ⇒ 本轮**零改动**
- **复开触发条件**（本域侧登记）：当出现「**按扩展名分派的机器判定**」成为某个门禁 / 脚本的**正确性前提**时（例如某个守门开始区分 `application.yml` 与 `application.yaml`）⇒ ⇒ 届时重开该议题

---

---

## 4·十、🟢 **第十轮（2026-10-27 17:3x）· §5.3e 项⑤ 销号 —— A3「路径归一」的**真 Tomcat** 探针**（共享层那层已在本树 ⇒ 本域自己补上探针）

### ① 交付物（**本域自己的**，不外推）
- `TaskApiPathNormalizationRealTomcatTest`（**3 条**）· `TaskApiPathNormalizationControlTest`（**2 条**）⇒ ⇒ **A/B 两类**
- ⚠️ **不外推**：like / message 两域**各做了一遍自己的 A/B** ⇒ ⇒ **三域各 5 条 = 15 条**（不是「一个域跑一遍、三域通用」）

### ② 判据（三段，每段都落到消费点）
① **共享层那份在、且没被域内同名 Bean 遮蔽** ⇒ `context.containsBean("goStylePathNormalizingFilter")` ＋ 按名取到的**类型**必须是 `com.rag.common.http.GoStylePathNormalizingFilter` ＋ `@Order` = `HIGHEST_PRECEDENCE + 2`
② **真 Tomcat ＋ 真请求** ⇒ `DEFINED_PORT` ＋ 本域专用端口段 `21800-21899`（`RealTomcatPort`）＋ **`HttpURLConnection`**
   - ⚠️ **为什么不能用 `java.net.http`**：它的 `URI` 构造器**自己就规范化路径**（`URI.create("…/a/../b")` ⇒ `…/b`）⇒ ⇒ 变体在**发出去之前**就被抹平 ⇒ ⇒ 测到的会是「规范路径」⇒ ⇒ **探针失效**
   - ⚠️ **`java.net.URL` 不校验也不规范化** ⇒ 请求行里原样带上变体（与 Go 的 `net/http` 同形：`r.URL.Path` 拿到的是客户端发的**原样**路径）
③ **变体 ⇒ 与规范路径同状态、且 ≠ 404**（`404` = 压根没匹配到路由）

### ③ 实测矩阵（**三域一致**）
| 域 | 规范路径 | 4 种变体（**A 组**：归一层在） | 4 种变体（**B 组**：归一层缺席） |
|---|---|---|---|
| task-api | 200 | **200 ×4** | **404 ×4** |
| like-api | 401 | **401 ×4** | **404 ×4** |
| message-api | 401 | **401 ×4** | **404 ×4** |

- 4 种变体 = **尾斜杠** · **重复斜杠** · **`.` 段** · **`..` 段**
- ⇒ ⇒ **Tomcat / Spring 自己不会归一这四种形态** ⇒ ⇒ **共享层那层是全部四种变体的承重件**（不是「Tomcat 顺手干了」）
- ⇒ ⇒ **B 组 = 同一份域服务 ＋ 同一通真实请求，唯一差别是「那层机制在不在」**（`spring.autoconfigure.exclude=…GoStylePathNormalizingAutoConfiguration`）⇒ ⇒ **A 组的绿不是空断言**

### ④ 变异验证（**三域各做一遍**，红在目标断言）
- 变异 = 在域内放一个**同名但什么都不做**的 Filter Bean（`@Component("goStylePathNormalizingFilter")`）⇒ ⇒ 共享层那份被 `@ConditionalOnMissingBean(name=…)` **跳过**（= **遮蔽**，不是叠加）
- ⇒ ⇒ **这不是人造的失效形态**：users 域的「域内副本遮蔽共享层那份」就是同款形态 ⇒ ⇒ 变异复现的是**真实隐患**
- 结果（三域**完全一致**）：**3 条里红 2 条**、**`Errors = 0`** ⇒ ⇒ **上下文正常启动**（不是启动期炸）⇒ 红点精确：
  - `sharedLayerNormalizingFilterIsPresent`（实测类型 = 域内影子 Bean）
  - `pathVariantsReachTheSameRoute`（变体 404）
  - **仍绿 1 条** = 规范路径照通 ⇒ ⇒ 坏的只有归一，不是路由本身
- ⇒ ⇒ 还原**逐字节一致**（三个 md5 与变异前完全相同）⇒ ⇒ 复跑全绿

### ⑤ 🔴 变异暴露的一条**设计缺陷**（已修 · 值得留档）
- 第一版用 `@Autowired GoStylePathNormalizingFilter`（**具体类型**）⇒ ⇒ 遮蔽时**上下文启动期就炸**（`NoSuchBeanDefinitionException`）⇒ ⇒ 守门会「**红在启动**」而不是「红在断言」⇒ ⇒ 看不出是哪条判据挂了 ⇒ ⇒ 无法区分「压根没这层」与「这层不对」
- ⇒ ⇒ **改成按 Bean 名 ＋ 断言类型**（`containsBean` ＋ `getBean` ＋ `instanceof`）⇒ ⇒ 遮蔽现在**红在断言上**（实测三域一致）
- ⇒ ⇒ **可复用的判据**：**守门的前置断言不要用「注入具体类型」**，否则一旦机制缺席，失败点会前移到容器启动期 ⇒ ⇒ **看不出是哪条判据挂了**

### ⑥ 与 Go 的对照口径（**不外推 · 不整体写 L4**）
- **Go 出处**：`rest/router/patrouter.go:48`（注册时 Clean）· `:60`（匹配时 Clean）⇒ ⇒ 变体命中同一条路由
- ⚠️ **Go 侧的 L4 活体证据是共享层那一份**（真 go-zero `rest` server ＋ curl：5 种变体全 200 · 2026-10-27）⇒ ⇒ **那不是本域独立跑的 Go 腿**
- ⇒ ⇒ **本轮补的是「RAG 腿」**：本域上下文里**那层真的承重**（真容器 ＋ A/B ＋ 变异验证）⇒ ⇒ **A3 轴的「路径归一」这一格 = RAG 腿 L4**；**Go 腿 = 共享层那一次的活体（不是本域独立跑的）** ⇒ ⇒ 台账按此口径记，**不整体升 L4**

### ⑦ §5.3e 清单更新
| 项 | 状态 |
|---|---|
| ⑤ A3 升 ✅ 的真 Tomcat 探针 | ✅ **本域侧销号**（本轮：真 Tomcat ＋ A/B 对照组 ＋ 三域各自变异验证） |

---

### 🟢 **第十一轮 · 合并后 9 模块回归（刷新 · 2026-10-27 17:55 → 18:0x）**（**上一张 970 的表是 13:31–13:39 那次的历史记录 ⇒ 原值保留不改写**）

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**合并进来的共享层**） | 324 | 0 | 0 | 0 |
| `service/message/message-api` | **103** | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | **94** | 0 | 0 | 0 |
| `service/like/like-rpc` | **120** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | **102** | 0 | 0 | 0 |
| `service/task/task-rpc` | **121** | 0 | 0 | 0 |
| **合计** | **992** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 668**（相对 970 那次的 646 ⇒ **+22**）⇒ ⇒ **逐条对得上本轮新增的用例**：
**like-rpc +5**（115→120 · A9 端到端 3 条 ＋ Redis 断连 2 条）· **task-rpc +2**（119→121 · Redis 断连 2 条）· **三域 api 各 +5**（第十轮 A3 探针：message-api 98→103 · like-api 89→94 · task-api 97→102）⇒ ⇒ **22 = 5 + 2 + 5 + 5 + 5** ✔
⇒ ⇒ **口径**：surefire XML **mtime 切**求和（**156 份报告**）**== maven 9 模块汇总行**（**逐字相等**）⇒ ⇒ **0 stale 报告**
⇒ ⇒ ⚠️ **本轮踩到的一个「stale 产物污染计数」的实例（值得留档）**：第十轮做**变异验证**时跑出了 **task-api failures = 2** 的报告 ⇒ ⇒ `check-ledger-selfcheck.py` 第 3 层当场报「❌ task-api failures/errors/skipped 全 0」⇒ ⇒ **根因不是代码坏了，是变异跑的失败报告还留在 `target/surefire-reports/`** ⇒ ⇒ 本轮全量回归把 task-api 重跑一遍 ⇒ ⇒ **failures 归 0、门禁恢复绿** ⇒ ⇒ ⇒ **判据**：门禁读的是 `target/` 里的报告 ⇒ ⇒ **任何一次带 `-Dtest=` 的定向跑都会改写/污染它** ⇒ ⇒ **跑门禁前必须有一次覆盖该模块的全量跑**（否则门禁报的是「上一次跑剩的产物」，不是当前代码态）

### 🟢 **第十六轮 · C7 波末 9 模块回归（实测 · 2026-10-27 18:4x → 19:0x）** —— **把「995 是推算」升级为实测**

| 模块 | tests | failures | errors | skipped | 报告数 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | **327** | 0 | 0 | 0 | 62 |
| `service/message/message-api` | 103 | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 | 20 |
| `service/message/message-common` | 21 | 0 | 0 | 0 | 2 |
| `service/like/like-api` | 94 | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | 120 | 0 | 0 | 0 | 21 |
| `service/like/like-common` | 18 | 0 | 0 | 0 | 2 |
| `service/task/task-api` | **102** | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | **121** | 0 | 0 | 0 | 15 |
| **合计** | **995** | **0** | **0** | **0** | **157** |

⇒ ⇒ **域侧 8 模块 = 668**（与第十一轮**逐条相同**）＋ **共享层 324 ⇒ 327**（+3 = G4 的新机器门禁 `GoStyleRedisClientShadowingGateTest` 3 条）⇒ ⇒ **9 模块总计 995** ⇒ ⇒ ✅ **第十一轮那句「995 是推算，不是实测」** ⇒ ⇒ **本轮实测证实该推算值** ⇒ ⇒ **995 从此是实测值**（**上一张 992 的表原值保留不改写**）
⇒ ⇒ **口径（比上一轮更严，三条）**：① **命令带 `-am` + `clean`** ⇒ `mvn -o -B -DargLine=-Xmx2g -am -pl <9 模块> clean test` ⇒ ⇒ **reactor 实跑 23 个模块**（`-am` 自动带入 `users-common` / `user` / `user-proto` / `message` / `message-proto` / `like` / `like-proto` / `task` / `task-proto`）⇒ ⇒ ⚠️ **这是必须的**：`service/message/message-api/pom.xml:55` 依赖 **`users-common`**（**不在我 9 模块表里**）⇒ ⇒ **不带 `-am` ⇒ 它从 `~/.m2` 解析旧 jar ⇒ 测的不是本树代码**（`mvn -pl` 不带 `-am` 的经典陷阱）② **权威口径 = 按本域模块的 surefire 目录逐文件求和**（`TEST-*.xml` 的 `testsuite` 属性）⇒ **逐模块 == maven 汇总行**（**9/9 逐字相等**）③ **`clean` 不可省** ⇒ 删过源/资源的模块会留 stale `target/classes` ⇒ 门禁与计数都会读到残留
⇒ ⇒ **157 份报告全部本轮生成**（`clean` 已清空旧目录 ⇒ **0 stale 报告**）
⇒ ⇒ ⚠️ **两个共享门禁仍红（非本轮引入 · 非我写权面）**：`check-tomcat-limits.py`（期望表把 task-api 写死 `raw`，而权威语义表 `GoStyleFormLimitMode.java:237` = `Mode.OFF` ⇒ **期望表与权威表自相矛盾**）· `check-wave-coupling.py`（**9 项漂移** ⇒ 快照须在四 lane 全合后由整合者重生成）⇒ ⇒ **两条都不是我域能修的**

### 🔴 第十七轮 · 域侧排序桥收编到共享层 ＋ **它揭出来的构建路径缺陷（单模块构建的 fat jar 起不来）**（2026-10-27）

**① 闸门已满足 ⇒ 收编落码（删 2 件）**
- 共享层那份**已在我的树里**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 注册行 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` **第 4 行** ⇒ ⇒ 本文件 D-6 原写的「共享层 `service/common` 命中数 = 0」**已过期**（那是 03:3x 时的状态）⇒ ⇒ **闸门解除**。
- 删掉：`service/task/task-rpc/src/main/java/com/rag/task/task/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/task/task-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（**该文件只含排序桥这一行 ⇒ 整份随类一起删**）。
- ⚠️ **合并清单条目（写给整合者）**：我域这 3 个 rpc 模块的 `imports` 都是**「只含一行」**形态 ⇒ ⇒ **整份文件随类一起删**；而 G3 的 `article-rpc` 是**「2 行」**形态 ⇒ ⇒ **只删第 1 行、文件保留** ⇒ ⇒ **两种形态不能一刀切**。
- 🟢 **删前我域自己做的核验（不采信转述）**：① 两份桥**都不注册任何 bean**（类体为空）⇒ ⇒ **无 bean 名遮蔽**；且声明的是**同向**排序边 ⇒ ⇒ **多一个同向节点、无环、功能等价** ⇒ ⇒ **删除 = 去重，不是修 bug** ⇒ ⇒ **A1/A9 等轴的证据强度不因本次删除而降级**（⚠️ global 台账 `:1159` 用「被遮蔽」措辞 ⇒ 我域实测语义是「**同向并存、功能等价**」⇒ 措辞差异已报 G4）② **主代码零 import** ③ **测试里只有 javadoc / 断言文本** ⇒ ⇒ **断言的是「效果」而不是「类在不在」**。

**② 🔴🔴 收编当场揭出来的真缺陷：单模块构建的 fat jar 起不来（跨 lane 的地雷）**
- **现象（首撞在 like 域）**：收编后跑 like-rpc ⇒ `LikeRpcRuntimeBootGateTest.realFatJarProcessBootsAndListens` **红** ⇒ 真 fat jar 的 gRPC 端口没监听 ⇒ 进程日志尾部 = `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` ⇒ ⇒ **不是 flake、不是端口撞车**（判据 = 那条 MP 异常）。
- **根因（三层，逐层实测）**：① **域内桥以前在 `BOOT-INF/classes` 里** ⇒ ⇒ **不管内嵌的 `common` jar 多旧，排序边都成立** ⇒ ⇒ **域内副本长期掩盖着下面这条**；② **构建 fat jar 的命令不带 `-am`** ⇒ ⇒ `service/common` 不在 reactor 里 ⇒ ⇒ 从 **`~/.m2`** 取 jar ⇒ ⇒ 实测 `~/.m2/.../common-0.0.1-SNAPSHOT.jar`（**12:00 的旧包**）的 `AutoConfiguration.imports` 里**排序桥行数 = 0** ⇒ ⇒ **排序桥不在 fat jar 里**；③ ⇒ ⇒ MP 的 `@AutoConfigureAfter`（**Boot 3 包名**）在 Boot 4 下**悬空** ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒ ⇒ `@ConditionalOnSingleCandidate(DataSource)` 失败 ⇒ ⇒ **不产 `SqlSessionFactory`** ⇒ ⇒ **进程启动即挂**。
- **⇒⇒⇒ 通用结论**：**「域内桥一删 ⇒ 单模块构建的 fat jar 起不来」** ⇒ ⇒ ⇒ **凡有进程级 fat jar 守门的域，收编都会红**（G3 的 comment-rpc / article-rpc / favorite-rpc 都带 `*RuntimeBootGateTest`）⇒ ⇒ ⇒ **已回信点名**。

**③ ⚠️ 本域的产物层守门有一个「解包但不起进程 ⇒ 看不见排序边」的缺口 ⇒ 本轮已补**
- 本域 `TaskRpcFatJarArtifactGateTest` **只解包**（看 manifest 与 `BOOT-INF/lib` 有没有某个 artifact）**、不起进程** ⇒ ⇒ **它抓不到上面那个缺陷**（症状要到 like 域的进程级守门才暴露）⇒ ⇒ ⇒ **所以这条只能本域自己守：别域的进程级守门兜不住本域的产物面**
- ⇒ ⇒ **本轮已补一条同款断言** `TaskRpcFatJarArtifactGateTest.fatJarMustCarrySharedLayerOrderingBridge` ⇒ ⇒ 断言**内嵌的 common jar 的 `AutoConfiguration.imports` 里含共享层排序桥** ⇒ ⇒ **缺口已闭** ⇒ ⇒ **期望值用独立字面量**（共享层那个全限定名，**不从被测代码取常量**）⇒ ⇒ 共享层改包名时如实转红。
- ⇒ ⇒ **同理 like 域也加了** `LikeRpcArtifactClasspathGateTest.fatJarCarriesSharedLayerOrderingBridge` ⇒ ⇒ **两条守门形态不同：like 那条自己构建 fat jar（连带验证 `-am` 修法），task 这条只解包（补的是「看不见排序边」的缺口）**

**④ 修法与验证（like 域做，因为只有它有进程级守门能证）**
- `LikeRpcArtifactClasspathGateTest.buildFatJar()` 的嵌套命令 **加 `-am`** ⇒ ⇒ reactor 先产出当轮的 `service/common/target/common-*.jar` ⇒ ⇒ fat jar 内嵌的就是它（**实测：内嵌 jar 的 `imports` 第 4 行 = 共享层排序桥**）⇒ ⇒ **真 fat jar 随即能起**。
- **验证（可复算）**：修后 `mvn -o -B -DargLine=-Xmx2g -am -pl service/like/like-rpc test -Dtest='LikeRpcArtifactClasspathGateTest,LikeRpcRuntimeBootGateTest,LikeRpcSpringContextWiringTest'` ⇒ ⇒ **13 条 / 0f 0e 0s · BUILD SUCCESS** ⇒ ⇒ **变异验证**：去掉 `-am` ＋ 删旧 fat jar 强制重建 ⇒ ⇒ **3 条里红 1 条**，**红在目标断言** `fatJarCarriesSharedLayerOrderingBridge:147` ⇒ ⇒ **恰好 1 条红**、另 2 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`c3d547e7d5d8706c`）。

**⑤ 🟢 顺带复核：`task-api` 的 `mode: "off"` 是对的，`check-tomcat-limits.py` 的期望表是错的（这次有真源级证据）**
- **Go 侧真源**：`service/task/api/internal/handler/task/*.go` 的 `GetTaskHandler` ⇒ **`json.NewDecoder(r.Body).Decode(&req)`** ⇒ ⇒ **不是** go-zero 惯用的 `httpx.Parse(r, &req)` ⇒ ⇒ **零 form / query 解析** ⇒ ⇒ **`r.ParseForm()` 从不被调** ⇒ ⇒ **Go 侧 form 值闸门根本不可达** ⇒ ⇒ **Go 侧也不存在 form 段数上限** ⇒ ⇒ **RAG 侧 `mode: "off"`（不装 form 值闸门）与 Go 同形** ✔
- ⇒ ⇒ **这直接判定了那条红门禁的归属**：`check-tomcat-limits.py` 要求 task-api = `raw` ⇒ ⇒ **但 Go 侧不可达 ⇒ 装了反而是「比 Go 更严」的新漂移** ⇒ ⇒ **期望表错**（权威语义表 `GoStyleFormLimitMode.java:237` = `Mode.OFF` 与真源一致）⇒ ⇒ **修法 = 改门禁期望表（`tools/difftest/**`，非我写权面）**，**不是**改域侧配置。
- ⚠️ **残留那一面仍在（轴 ② 判定权口径）**：共享层把容器上限**无条件**抬到 10001 ⇒ ⇒ 而 Go 侧 task-api **连 form 都不解析** ⇒ ⇒ Go 侧**没有**任何参数个数上限 ⇒ ⇒ RAG 侧 Tomcat 的 10001 仍**比 Go 严** ⇒ ⇒ 这不是 `mode` 能解决的（`mode` 只管「装不装闸门」）⇒ ⇒ 属**跨域待拍板**那一格 ⇒ ⇒ **本域登记为残留、不自修**。

**⑥ 一条更深的教训**
- **「域侧副本」不只是冗余 —— 它可能在无意中充当「共享层落码前」的承重件** ⇒ ⇒ ⇒ **这正是本次缺陷的根因**
- ⇒ ⇒ **判据式** ⇒ ⇒ **收编共享层能力前，先问「这份副本在 fat jar / 独立构建路径上是不是唯一的承重件」** ⇒ ⇒ ⇒⇒ **本次就是：域内副本在 `BOOT-INF/classes` 里 ⇒ 它才是那条构建路径上唯一的承重件 ⇒ 删掉它之后，共享层那份在「测试 classpath」（reactor 的 `classes`）里带着，但在「产物」（fat jar 内嵌的 jar）里未必带着 ⇒ ⇒ 两个路径必须分别验**

**⑦ 🟢 池形态判据的「依赖面」：G3 提出「classpath 探测 ⇒ 共享层加传递依赖 ⇒ 形态静默翻转」⇒ ⇒ 我域复核结论**不同**（别照抄）**
- G3 的判据本身**成立**（共享层 `service/common` **不带** `commons-pool2`；`DataRedisConnectionConfiguration#isPoolEnabled` 在 `enabled == null` 时**回落到 classpath 探测**）⇒ ⇒ **对「没显式配 `enabled`」的域**成立。
- **我域显式配了** ⇒ ⇒ **classpath 回落那条对本域不成立**：`service/task/task-rpc/src/main/resources/application.yml:138-142`（`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0`）⇒ ⇒ **「共享层加一个传递依赖 ⇒ 本域池形态静默翻转」对本域**不成立**。
- ⚠️ **本域的风险是「反过来」的，而且更硬**：`enabled: true` ⇒ Boot 必走 `getPoolConfig` ⇒ ⇒ **`commons-pool2` 是启动期硬前提**（缺它 ⇒ `NoClassDefFoundError: org/apache/commons/pool2/impl/GenericObjectPoolConfig`）⇒ ⇒ **不是「静默翻转」，是「起不来」** ⇒ ⇒ **对域侧更安全（fail-fast）** ⇒ ⇒ 这一点已写在 `service/task/task-rpc/pom.xml:123-138` 的注释里（含「Boot 4 的 starter 不再传递 commons-pool2 ⇒ 只能由应用自己声明」+ like-rpc 2026-10-23 的事故实证）。
- ⇒ ⇒ **但「依赖面」这条纪律本域照样标，且要分两轴写**：**轴 ①「池开关」** ⇒ 本域**不依赖**共享层 dependency 状态（`enabled` 显式）⇒ ⇒ **轴 ②「参数值」** ⇒ 本域**依赖**共享层（E66 那条联动条款：共享层 customizer 若碰 `timeoutOptions(...)` / `socketOptions(...)` ⇒ 覆盖本域的值）⇒ ⇒ ⇒ **两轴各自盯住：轴 ① 盯共享层的 dependency 声明，轴 ② 盯共享层 customizer 的取值**


### 🔴 第十八轮 · Redis 池 `max-wait` 对齐（Go 的 `PoolTimeout`）—— **池耗尽时无限等 ⇒ 比 Go 更糟**（2026-10-27）

**① 缺陷本体（真缺陷）**
- **现象**：池耗尽（连接全在用）时，**借连接这一步没有任何等待上限** ⇒ ⇒ **无限挂起**（不是「等 3s」）。
- **Go 侧（本域自己推，不照抄）**：本域 Go 走 **go-redis 直连** ⇒ `service/task/rpc/internal/svc/service_context.go:25-29` `redis.NewClient(&redis.Options{Addr, Password, DB})` ⇒ **只设 3 个字段** ⇒ `ReadTimeout`/`PoolTimeout` **都没设** ⇒ ⇒ 落 go-redis v9.20.0 库默认（`options.go` `case 0: opt.ReadTimeout = 3 * time.Second` ⇒ **3s**；`options.go:380-386` ⇒ **`PoolTimeout = ReadTimeout + 1s = 4s`**）⇒ ⇒ 消费点 `internal/pool/pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)` ⇒ ⇒ **Go 侧池耗尽 ⇒ 4s 后失败**。
- **RAG 侧为何更糟（字节级实测）**：commons-pool2 2.12.1 `BaseObjectPoolConfig` 的 **`DEFAULT_MAX_WAIT_MILLIS = -1l`**（`javap -constants`）⇒ 消费点 `GenericObjectPool.borrowObject` 读 `getMaxWaitDuration()`（`javap -c` 实测：两个调用点 ＋ 分支到 **`takeFirst()`（无参 = 永久阻塞）** 与 `pollFirst(Duration)`）⇒ ⇒ **负值 ⇒ 永久阻塞** ⇒ ⇒ **无限等**。
- ⚠️ **Lettuce 的 3s 命令超时救不了场**：计时器在 `CommandExpiryWriter.write(command)` 才起算，而**借连接发生在写命令之前** ⇒ ⇒ 池耗尽时那个 3s **根本没开始**。

**② 落码（1 个文件）**
- `service/task/task-rpc/src/main/resources/application.yml` 的 `spring.data.redis.lettuce.pool` ⇒ 加 **`max-wait: 4s`**（对齐 Go 的 `PoolTimeout`）。
- ⚠️ **不动 `min-idle`**：本域 = **0**（go-redis 直连，`MinIdleConns` 未设 ⇒ 无 default ⇒ 0）⇒ **精确对齐**；⚠️ **G3 消息里「like `min-idle: 4`」与我树不符**（like 早就是 8）⇒ ⇒ **两域的 `min-idle` 都未改**。

**③ 守门 + 变异验证（恰好 1 条红，红在目标断言）**
- 🆕 `TaskRedisCommandTimeoutGateTest.redisPoolMaxWaitMatchesGoPoolTimeout`（新增断言 ⑦）⇒ 本类 **6 ⇒ 7 条**。
- 变异 = 抽掉 `max-wait: 4s` ⇒ **7 条恰红 1 条**（红在 `:287`）⇒ 另 6 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`b8f17b429abc9121827f61bd`）⇒ ⇒ 复跑 **7 / 0f 0e 0s**。

**④ 🟢 顺带订正两处「我域自己写错的引用」**
- **`MaxIdleConns` 不是「无对应项」**：go-redis v9.20.0 `options.go:224-228` **有**该字段（注释写明 `default: 0`）⇒ 消费点 `internal/pool/pool.go:1257` `if p.cfg.MaxIdleConns == 0 || p.idleConnsLen.Load() < p.cfg.MaxIdleConns` ⇒ ⇒ **Go 取值 = 0 = 无上限** ⇒ ⇒ **正确表述 = 「有对应项，Go 取值 = 0（无上限）」** ⇒ ⇒ **`max-idle: 16` 重新归类 = 「比 Go 更严」的**可登记残留**（只影响空闲连接回收时机，**不影响命令结果**）** ⇒ ⇒ 原写「Go 侧无对应项 ⇒ 纯表达层取值」**不准确**，已订正（yml 注释 + 守门断言消息 + 本文件）。
- **`getPoolConfig` 的引用路径**：Boot 4.0.8 的 `LettuceConnectionConfiguration`（外层类）**没有** `getPoolConfig` 方法（实测 `javap` 方法清单）⇒ ⇒ **真实消费点是内部类** `LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig(DataRedisProperties$Pool)` ⇒ ⇒ 原引「`:267-271`」是 **Boot 2/3 旧路径**，在 Boot 4.0.8 上**对不上** ⇒ ⇒ **一律改按「类名 + 方法名」引用**。

**⑤ ⚠️ 口径影响（数字要跟着改 · 第十八轮**实测**）**
- 本域 **+1 条**守门 ⇒ ⇒ **task-rpc 121 ⇒ 122**（`mvn test` 口径）。
- 🆕 **第十八轮实测（9 模块全量 `clean test` · 带 `-am` · BUILD SUCCESS · 9:52 min）**：

| 模块 | tests | failures | errors | skipped | 报告数 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | 327 | 0 | 0 | 0 | 62 |
| `service/message/message-api` | 103 | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 | 20 |
| `service/message/message-common` | 21 | 0 | 0 | 0 | 2 |
| `service/like/like-api` | 94 | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | **122** | 0 | 0 | 0 | 21 |
| `service/like/like-common` | 18 | 0 | 0 | 0 | 2 |
| `service/task/task-api` | 102 | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | **122** | 0 | 0 | 0 | 15 |
| **合计** | **998** | **0** | **0** | **0** | **157** |

  ⇒ ⇒ **域侧 8 模块 = 671** ＋ 共享层 **327** ⇒ ⇒ **9 模块 `mvn test` 口径 = 998** ⇒ ⇒ **`test`+`verify` 口径 = 998 + 5（task 的产物层 IT）= 1003**。
- 🔴 **订正我本轮自己写错的推算**：上面原写「9 模块 `mvn test` 口径 995 ⇒ 997」⇒ ⇒ **实测是 998（差 +1）** ⇒ ⇒ **那个 997 是推算、且推算错了** ⇒ ⇒ **根因（已实测定位，不是玄学）**：**第十七轮**新增的 `LikeRpcArtifactClasspathGateTest.fatJarCarriesSharedLayerOrderingBridge` 落在 **`test` 阶段**（类名 `*Test` ⇒ surefire 默认 includes 命中）⇒ ⇒ **like-rpc +1**（实测复核：`git show 016e95a:…/LikeRpcArtifactClasspathGateTest.java` 的 `@Test` 数 = **2** ⇒ HEAD = **3** ⇒ +1 ✔）⇒ ⇒ **第十七轮结束时真实值已是 996**，而第十七轮**只重跑了 `task-rpc`**（121）、**沿用了第十六轮的 9 模块总数 995** ⇒ ⇒ **基线在第十七轮就悄悄过期了**（未被发现）⇒ ⇒ 第十八轮 +2（两域池断言 ⑦）⇒ ⇒ **996 + 2 = 998** ✔
  ⇒ ⇒ **教训（写死）**：**「只重跑受影响模块 + 沿用上轮总数」会让总数悄悄过期** ⇒ ⇒ 跨轮改动落在 **`test` 阶段的类**上时，要么**重跑 9 模块全量**，要么把总数标成「**已过期**」⇒ ⇒ 本轮能抓出来，靠的是**逐模块 XML 求和 == maven 汇总行**的**双口径交叉验算**（单靠「我加了 2 条 ⇒ 997」这个推算**推不出来**）。
  ⇒ ⇒ **原值保留不改写**：第十六/十七轮台账里的 **995 / 1000 原值保留**，以本条订正为准。

### 🟢 第十九轮 · 并入 G4 终版 `d546f45`（共享层 1xx 探针「每请求上界」）⇒ **9 模块全量实测 1001**（2026-10-27）

**① 合并（4 笔 · 零冲突）**
- 提交 **`0cd1e3b`**（`merge(g4)`，父提交 = `900e2c4` + `d546f45`）⇒ ⇒ `git merge-tree --write-tree` 预演**零冲突**。
- 并入 4 笔：`15cf93a`（**fix(common)**：1xx 探针行数设「每请求上界」—— `GoStyleClientDisconnectProbe` 的 `MAX_PROBE_LINES_PROPERTY` / `DEFAULT_MAX_PROBE_LINES = 4` / `probe()` 预算闸门 / 新增 `State.BUDGET_EXHAUSTED` ＋ `HttpMiddleware` 的 `BUDGET_EXHAUSTED` 分支，**等待语义不变**）· `8379748` / `12942e2`（global 台账 §4.10.30~§4.10.34）· `d546f45`（**纯 javadoc**：`GoStyleTomcatParameterLimitCustomizer` 过期 javadoc 订正 —— **我域第十七轮报的那条，G4 已在共享层自修**）。
- ⚠️ **`15cf93a` 改了生产代码** ⇒ ⇒ **合并后必须重跑全量**（G4 原话：收口回归窗口在合并前）⇒ ⇒ 本轮紧接一次 9 模块 `clean test`。
- ⚠️ **写权零越界**：并入的 6 个文件全在 `service/common/**` ＋ `docs/DRIFT/global/vs-common.md` ⇒ **均为 G4 的写权面**（本 lane 只读不改）。

**② 实测（9 模块全量 `clean test` · 带 `-am` · BUILD SUCCESS · 13:02 min）**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**共享层**） | **330** | 0 | 0 | 0 |
| `service/message/message-api` | 103 | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 94 | 0 | 0 | 0 |
| `service/like/like-rpc` | 122 | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 102 | 0 | 0 | 0 |
| `service/task/task-rpc` | 122 | 0 | 0 | 0 |
| **合计** | **1001** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 671**（与第十八轮**一字不差**）＋ 共享层 **327 ⇒ 330**（**+3** = G4 的 `GoStyleClientDisconnectProbeBudgetGateTest` 3 条）⇒ ⇒ **9 模块 `mvn test` 口径 998 ⇒ 1001** ⇒ ⇒ **`test`+`verify` 口径 = 1001 + 5（task 的产物层 IT）= 1006**。
⇒ ⇒ **口径**：逐模块 surefire `TEST-*.xml` 的 `testsuite` 属性求和 **== maven 模块汇总行**（**9/9 逐字相等**）⇒ ⇒ **0 stale 报告**（`clean` 已清空旧目录）。
⇒ ⇒ ⚠️ **共享层 330 与 G4 终版回执里的数字逐字相同** ⇒ ⇒ **两侧对同一份共享层的条数认知一致** ✔

**③ G4 点名要我做的三项 —— 逐条复跑，全部绿**

| 项 | G4 的原话 | 我域实测 |
|---|---|---|
| **FB-01 / FB-03 升级到 `layerNames` 断言** | 「升级那些守门到 `layerNames` 断言 ＋ 按 §5.3e 重跑收口回归」 | ✅ **我域三域早就在用 `layerNames` / `compose`**（`LikeProtectionChainWiringTest.java:127/:134/:138/:151` · `MessageProtectionChainWiringTest.java:97/:104/:108/:121` · `TaskProtectionChainWiringTest.java:182/:189/:193/:206`）⇒ ⇒ **无需改任何断言、无需发失败断言** ⇒ 本轮实测 like **4 条** / message **5 条** / task **5 条** 全绿 |
| **我域 3 处 `DEADLINE_EXCEEDED` 断言合并后复跑** | 「须在合并 `7488b3d` 后复跑」 | ✅ **合并条件早已满足**：`7488b3d` / `3b573fb` **早就在我树**（`git merge-base --is-ancestor` 两笔都 ✔）⇒ ⇒ 本轮实测：`MessageRpcTimeoutGateTest` **2 条 / 0f 0e 0s**（20.1s）· `LikeRpcGrpcInterceptorWiringTest` **3 条 / 0f 0e 0s**（12.0s）· `TaskGrpcInterceptorWiringTest` **2 条 / 0f 0e 0s**（19.5s） |
| **池守门 ⑤⑥ 合并后实测复跑** | 「安排在收口回归之后」 | ✅ 本轮实测：`TaskRedisCommandTimeoutGateTest` **7 条 / 0f 0e 0s**（含第十八轮新增的断言 ⑦ `max-wait`）· `LikeRpcRedisTimeoutsGateTest` **7 条 / 0f 0e 0s** ⇒ ⇒ **G4 ⑥ 的预判「customizer 只调 `disconnectedBehavior(REJECT_COMMANDS)`、不碰池 ⇒ 池守门不受影响」被实测证实** |

**④ like 域 A9 端到端 E2E —— G4 说「必须在合并后补」，我域实测是「**已存在 ＋ 合并后复跑通过**」**
- G4 §4.10.30 的判断是「合并后 like 域 `LikeSpans.java:242/:246`（两个 `attr(...)` 重载）落在受益面上 ⇒ 由 no-op 变真生效 ⇒ **预期行为变更，非回归**」⇒ ⇒ **与我的结论一致**。
- ⚠️ **但「补」这个动作在我域是「复跑」，不是「新建」** —— `LikeA9GrpcSpanEndToEndTest`（**3 条**）在**第七轮**就随 FB-04（`853767b`，合并 `37816ca`）入本树了 ⇒ ⇒ **本轮合并后复跑 = 3 条 / 0f 0e 0s（2.16s）** ✔
  · ① 真 gRPC 链路 ⇒ 域侧 span 属性落在**服务端 span** 上（`query.target_type` = `article` · `query.batch_size` = 2L）
  · ② **对照组**（复刻 FB-04 之前的行为、同一份域服务、同一通真实调用）⇒ 属性**必须丢**（`assertNull`）
  · ③ **护栏** ⇒ 共享层那个拦截器真的在链路上（否则前两条测的是别的拦截器）
- ⇒ ⇒ 配套的 `LikeSpansAttributeGateTest`（契约级 · 真 OTel SDK + 真 exporter）**11 条 / 0f 0e 0s**。
- ⇒ ⇒ **本域未被这次合并改动 ⇒ 域侧不需要新增任何代码**（FB-04 的落码在共享层，域侧只是**受益**）。

**⑤ 1xx 上界这条对我域的影响面 = 零**
- 实测我域 `1xx` / `interim` / `Too many interim` **命中数 = 0**（排除注释后）⇒ ⇒ 改动落在共享层探针的**行数预算**上、**不改 HTTP 语义**（G4 给的 Go 出处 `Sea-RideTheWind_golang/service/common/observability/http.go:80-88` ⇒ **零 Go 语义变更**）⇒ ⇒ **我域零直接影响**。
- 共享层那条新守门 `GoStyleClientDisconnectProbeBudgetGateTest` 在我树本次产物里 **3 条 / 0f 0e 0s**（2.9s）✔

**⑥ 本域受影响面（逐条）**
- **task 域**：本域**零代码改动** ⇒ ⇒ 本次合并对我域**只有共享层那条 1xx 上界**（影响面实测为 0）。
- 复跑实证：`TaskRedisCommandTimeoutGateTest` **7 条 / 0f 0e 0s**（含第十八轮新增的断言 ⑦ `max-wait` = 4s）· `TaskGrpcInterceptorWiringTest` **2 条 / 0f 0e 0s**（含 `DEADLINE_EXCEEDED` 断言）· `TaskProtectionChainWiringTest` **5 条 / 0f 0e 0s**（**`layerNames` / `compose` 断言已就位** ⇒ FB-01/FB-03 **无需改任何断言**）。
- ⇒ ⇒ **task-rpc 本轮条数 = 122（未变）**；共享层 327 ⇒ 330 ⇒ 9 模块 998 ⇒ **1001**。

**⑦ 原值保留不改写**
第十八轮的 **998 / 671 原值保留**，以本条订正为准（**当前权威值 = 1001 / 671**）⇒ ⇒ **域侧 8 模块的 671 在两轮之间一字未变** ⇒ ⇒ 变的只有共享层（+3）。

---

### 🔴 第二十轮 · Redis 池维度的**双侧活体对拍**（真 Redis ＋ 真 go-redis 客户端）⇒ **池轴 L3 ⇒ L4**（2026-10-27）

**① 为什么本轮要补这一层（缺口在哪）**
- 第十八轮（池 `max-wait: 4s`）证明的是「**配置层**对」—— 守门 ⑦ 读的是 **Boot 装配出来的那份 `poolConfig`**（`LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig`）⇒ ⇒ 它**不能**证明「**运行期**真按这个上限在跑」⇒ ⇒ 本轮补的就是后半段：**真的把池占满，量第 N+1 条等了多久**。
- ⇒ ⇒ **本条取代第十八轮台账里「⚠️ 未做双侧并发活体对拍 ⇒ 不写 L4」的效力边界**；第十八轮的**结论（补 `max-wait: 4s`）不变**，变的是**证据强度**与**参照量级**。

**② 落码（2 个新测试类 · 各 3 条 · 零生产代码改动）**
| 模块 | 文件 | 条数 |
|---|---|---|
| task-rpc | `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskRedisPoolExhaustionP99GateTest.java` | **3** |
| like-rpc | `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRedisPoolExhaustionP99GateTest.java` | **3** |
- 构造 = **真 Redis 容器**（`redis:7-alpine`）＋ **真 Spring 上下文**（真应用类 ⇒ 加载本模块**真** `application.yml`）＋ **真装配**的 `LettuceConnectionFactory`。
- 判据全部是**运行期读数**（Redis 服务端 `connected_clients` ＋ 实测等待毫秒），**不是**配置值。
- ⚠️ **两域各建一条（不共用）**：两个模块的 `application.yml` 是**各自**生效的 ⇒ ⇒ 「task 域量到 X」**推不出**「like 域也是 X」⇒ ⇒ **不外推**。

**③ 三条活体读数（RAG 腿 · 两域各自实测）**

| 断言 | task 域 | like 域 |
|---|---|---|
| ① 普通命令走不走池 | `connected_clients` **before=1 / after=1 / delta=0** | **before=8 / after=8 / delta=0** |
| ② pipeline 走不走池 | 占位 63 条 ⇒ **before=1 / after=64 / delta=63** | 占位 63 条 ⇒ **before=8 / after=64 / delta=56** |
| ③ 池耗尽 ⇒ 等连接上限 | **`wait_elapsed_ms = 4004`**（复跑 4005） | **`wait_elapsed_ms = 4003`** |

- ③ 的异常链（两域同形）：`RedisConnectionFailureException → PoolException → NoSuchElementException: Timeout waiting for idle object, borrowMaxWaitDuration=PT3.999998S` ⇒ ⇒ **单次池等待上限 = 4s（真行为读数，不是配置值）**。
- 机制（源码级）：`LettuceConnectionFactory.java:132` `shareNativeConnection = true`（默认）＋ Boot 4.0.8 `LettuceConnectionConfiguration` **零处** `setShareNativeConnection` ⇒ `SharedConnection.getConnection()` 只持**一条** ⇒ ⇒ **普通命令走共享连接、池不参与**；只有 `LettuceConnection.openPipeline()` → `getOrCreateDedicatedConnection()` → `doGetAsyncDedicatedConnection()` 才**从池借**。

**④ Go 腿活体读数**（探针 `/tmp/g2poolp99b/probe/main.go` · **用真源的 `go.mod`/`go.sum` 离线构建** · `CGO_ENABLED=0` ⇒ 不引入真源之外的依赖）：生效 options = `PoolSize=64`（显式设以对齐 RAG `max-active`）· **`PoolTimeout = 4s`** · `ReadTimeout = 3s` · `MaxActiveConns = 0` ⇒ hold 30s ＋ 并发 80 ⇒ ⇒ **16 个等待者全部 `ErrPoolTimeout`** ⇒ ⇒ **p50=16086ms / p99=16135ms / min=16053 / max=16135**（= 4 次尝试 × 4s ＋ 退避）。

**⑤ 活体对拍表**

| 量 | Go | RAG | 判定 |
|---|---|---|---|
| **单次池等待上限** | **4s** | **4.003–4.005s** | ✅ **对齐** |
| **命令整体耗时**（池耗尽时） | **≈16.1s**（4 次重试） | **4.005s**（一次就失败） | 🟢 **RAG 更严**（更快失败） |

⇒ ⇒ ⇒ **漂移方向 = 「RAG 做了更严/更快的那一侧」** ⇒ ⇒ 修后定性 = **对齐（且更严）**。

**⑥ 三个受控实验（每个都带回滚 ＋ sha256 逐字节校验）**

| 实验 | 做法 | 实测 | 回滚校验 |
|---|---|---|---|
| **A（归因）** | like yml `min-idle: 8 → 0` | 借前基线 **8 ⇒ 1**、净增量 **56 ⇒ 63** ⇒ **`min-idle` 真的在运行期预热出对应条数的连接** | sha256 `9b406dca745a5906ab40a522` 逐字节一致 ✔ |
| **B（like 变异）** | 抽掉 `max-wait: 4s` | **`wait=8004ms / timedOut=true`** ⇒ **3 条恰红 1 条**（红在 `timedOut` 断言）⇒ 另 2 条仍绿 ⇒ **非假绿** | sha256 逐字节一致 ✔ |
| **C（task 变异）** | 抽掉 `max-wait: 4s` | **`wait=8002ms / timedOut=true`** ⇒ **3 条恰红 1 条** | sha256 `b8f17b429abc9121827f61bd` 逐字节一致 ✔ |

⇒ ⇒ **实验 A 是额外收获**：like 域 `min-idle: 8`（= Go 的 `MinIdleConns = 8`）从「配置层」升级成 **L4 活体证据**。

**⑦ ⚠️ 三条订正（**原值保留不改写**，以本条为准）**
1. **Go 侧的参照量级**：第十八轮写的「Go 侧池耗尽 ⇒ **4s 后失败**」**不准确** ⇒ go-redis v9.20.0 `error.go` 的 `shouldRetry` 对 `ErrPoolTimeout` **无条件 `return true`**（注释 `// connection pool timeout, increase retries. #3289`）⇒ `MaxRetries = 3` ⇒ ⇒ **共 4 次尝试** ⇒ ⇒ **Go 侧真实语义 = 「单次池等待 4s ＋ 命令整体 ≈16.1s 后失败」**（活体 p99 = 16135ms）⇒ ⇒ **「4s」是单次借连接的上限、不是命令整体的上限**（**漂移方向不变**）。
2. **RAG 侧的池只治理 pipeline/事务路径** ⇒ ⇒ 「无限等」只可能发生在**真正借连接的路径**上 ⇒ ⇒ **task 域有**那条路径（`service/task/task-rpc/src/main/java/com/rag/task/task/rpc/service/RedisPipelines.java:92` 与 `:121` 两处 `redis.executePipelined(new SessionCallback<Object>(){…})`）⇒ ⇒ **task 域修前漂移可达 ⇒ 真缺陷成立**；**like 域零 pipeline ⇒ 定性降级**（见 like 域那份）；**message 域零 Redis ⇒ 本轴不适用**。
3. **按域不同源，不外推** ⇒ ⇒ 本轮的「池真被用」结论**只对 task 域成立**。

**⑧ 🟡 探针自身的 3 个缺陷（自曝 · 已修 · 留档）**
1. **首版漏了 `grpc.server.port=0`** ⇒ gRPC server 走固定端口（task 9005 / like **8082**）⇒ 端口被占时**上下文起不来**、整类红、且**不是**被测行为红 —— **by like 那条在 8082 上暴露**（本机 8082 被桌面运行时的 Python 进程占着）⇒ ⇒ 已给两条探针都补上。
2. **② 的断言写错了量** —— 原写「净增量 ≥ 占位数」⇒ 但**池里已有的空闲连接会被复用** ⇒ 净增量可以小于占位数（like 就是 56 < 63）⇒ ⇒ 改成「服务端连接数必须**长到池的上限**」＋ 前置「借前基线 < 占位数」（防空过）。
3. **② 失败时不释放占位** ⇒ 池被占满 ⇒ **③ 会在准备阶段就 4s 超时** ⇒ 报错落在**准备代码**上 ⇒ 「红在别处」⇒ ⇒ 变异验证失效 ⇒ ⇒ 释放动作移到 `@AfterEach`（断言失败也会跑）。

**⑨ 回归实测（9 模块 `clean test` · 带 `-am` · BUILD SUCCESS · 12:01 min）**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**共享层**） | **330** | 0 | 0 | 0 |
| `service/message/message-api` | 103 | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 94 | 0 | 0 | 0 |
| `service/like/like-rpc` | **125** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 102 | 0 | 0 | 0 |
| `service/task/task-rpc` | **125** | 0 | 0 | 0 |
| **合计** | **1007** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 677**（上轮 671 ⇒ **+6** = 两条探针各 3 条）＋ 共享层 **330（未变）** ⇒ ⇒ **9 模块 `mvn test` 口径 1001 ⇒ 1007**。
⇒ ⇒ **口径**：逐模块 surefire `TEST-*.xml` 的 `testsuite` 属性求和 **== maven 模块汇总行**（**9/9 逐字相等**）⇒ ⇒ **0 stale 报告**（`clean` 已清空旧目录）。
⇒ ⇒ ⚠️ **本轮踩到的口径坑（写死）**：**定向跑（`-Dtest=`）会改写该模块的 surefire 报告** ⇒ ⇒ 两条探针跑完后**必须**补一次覆盖该模块的**全量**跑 ⇒ ⇒ 否则报告集只剩探针那一类 ⇒ ⇒ **凡以 `target/` 为输入的判据，必须绑定「本次运行的产物」**。

**⑩ 原值保留不改写**
第十九轮的 **1001 / 671 原值保留**，以本条为准（**当前权威值 = 1007 / 677**）⇒ ⇒ **共享层 330 两轮之间一字未变** ⇒ ⇒ 变的只有域侧（+6 = 两条探针）。

---

### 🟢 第二十一轮 · Testcontainers 宿主口（**层 2**）落码 —— **回答 G4 那个未实测项**（2026-10-27）

**① 起因（G4 §4.10.39 ② 分配段号 ＋ 明确要求「先探针、不能照抄」）**
- G4 把 `22700–22999` 划为「**Testcontainers 宿主口段**」，**按域分子段**：**task 域 = `22820–22849`**。
- 根因：Testcontainers 的**默认随机宿主口**落在 **OS 临时段 `49152–65535`**（= Docker published 段）⇒ ⇒ 与 `tools/difftest/ddl-parity.sh` 的固定口 `55560–55569` **同属一个池子** ⇒ ⇒ 全局段表把该段标为**禁占** ⇒ ⇒ **真容器测试不得继续用随机口**。
- ⚠️ G4 同一格里**明写了两层「未实测」**：① `PostgreSQLContainer` **不能直接换成** `FixedHostPortGenericContainer`（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态 = **原容器 ＋ `withCreateContainerCmdModifier(...)` 显式绑口**；② 「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」未实测 ⇒ ⇒ **域侧落地必须先写一条探针验证，不能照抄**。

**② 🔴 「两层口径」在我域的真实含义（本轮第一次把两层说清）**

| 层 | 是什么 | 载体 | 本域段 | 本轮之前的状态 |
|---|---|---|---|---|
| **层 1** | **api 侧 Tomcat 监听端口**（`@SpringBootTest(webEnvironment = DEFINED_PORT)`） | `RealTomcatPort` | task 域另有一段（见 §2/A5 行） | ✅ **早已治理**（阶段 2 · D-7 已销号） |
| **层 2** | **rpc 侧 Testcontainers 容器宿主口**（`PostgreSQLContainer` / `GenericContainer`） | 🆕 `RealContainerPort` | **`22820–22849`** | 🔴 **整个域没人管** —— **6 个容器类**全部用随机口 ⇒ ⇒ **都落在 OS 临时段** |

⇒ ⇒ **本轮补的是层 2 的缺口**（不是重复治理层 1）⇒ ⇒ **两层都要守**：层 1 已有 `bindsToDedicatedPortSegment`，层 2 本轮新增。

**③ 落码（6 个容器类 ＋ 3 份支撑类 ＋ 6 个守门类 · **零生产代码改动**）**

| 项 | 内容 |
|---|---|
| **被绑的容器类** | task 域 **6 个**：PG **3** 个 ＋ Redis **3** 个 ⇒ 全部钉在本域段内的**独占**固定宿主口 |
| **支撑类** | `service/task-rpc/src/test/java/com/rag/task/task/rpc/support/RealContainerPort.java` ⇒ 段边界常量 ＋ `withFixedHostPort(container, hostPort, containerPort)` ＋ **端口越段直接抛异常**（拒绝落码） |
| **机制层守门**（真容器） | `taskPostgresFixedHostPortGateTest`（3 条）⇒ 钉「**映射口 == 固定口**」＋「固定口真在监听」＋「固定口上确实是 PG（`SELECT 1` 成功）」 |
| **覆盖层守门**（源扫 · 无容器 · 秒级） | `taskContainerFixedHostPortSegmentGateTest`（1 条）⇒ ① 凡声明容器的类**必须**有 `FIXED_HOST_PORT` ② **必须**在本域段内 ③ **全域不重号** ④ 容器数 **≠ 0**（防空过） |
| **端口分配方式** | **逐类写死**（不是轮询）⇒ surefire **每个 fork 一个独立 JVM** ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一端口 ⇒ ⇒ 撞车 |

**④ 🔴 直接回答 G4 的未实测项（**实测，不是推断**）**

> G4 §4.10.39 ② 的原问句：「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」

### **实测结论：会。**

- `taskPostgresFixedHostPortGateTest`（**真容器**）实测：① `getMappedPort(5432)` **== 22840**（就是那个固定口，**不是**随机口、**不是** 0）② 那个固定宿主口**真在监听**（从宿主机 TCP 连得上）③ 那个口上**确实是 PG**（真 JDBC 连接 ＋ `SELECT 1` 成功）。
⇒ ⇒ **形态两层都验完了**：形态 = 原容器 ＋ `withCreateContainerCmdModifier`（`FixedHostPortGenericContainer` **确实不能混用** —— 实测 `PostgreSQLContainer` 的父链是 `JdbcDatabaseContainer` → `GenericContainer`，与它**无继承关系**）；行为 = **映射口就是那个固定口**。

**⑤ 🔴 实现里踩到的三个坑（写死，给下一个域）**
1. **必须「先摘随机绑定、再加固定绑定」** —— 只加不摘 ⇒ Docker 会**同时看到两条同容器端口的绑定** ⇒ 行为不确定。做法：`HostConfig.getPortBindings()` 取出全部 ⇒ **`removeIf` 掉「容器端口 == 5432/6379」的那条**（Testcontainers 默认给的是随机宿主口）⇒ 再 `add` 一条 `new PortBinding(Ports.Binding.bindPort(hostPort), ExposedPort.tcp(containerPort))` ⇒ 最后 `hc.withPortBindings(bindings)`。
2. **泛型写法会编译不过** —— `public static <T extends GenericContainer<T>> T withFixedHostPort(T container, …)` ⇒ 传进去的是 `PostgreSQLContainer<?>`（**通配符捕获**）⇒ 编译器报「推论变量 T 具有不兼容的上限」⇒ ⇒ **改成非泛型签名**：`public static void withFixedHostPort(GenericContainer<?> container, int hostPort, int containerPort)`。
3. 🔴 **javadoc 里写 `src/test/java/**/*.java` 会提前闭合注释块** —— `*/` 出现在注释内部 ⇒ ⇒ 注释提前结束 ⇒ ⇒ 后面的**全角括号等中文被当代码解析** ⇒ ⇒ javac 报「**非法字符**」⇒ ⇒ 而**报错位置在几十行之后**（不是出问题的那一行）⇒ ⇒ **看起来像「编码坏了」，实际是注释块被截断**。修法：写成 `{@code src/test/java} 递归下的全部 {@code .java} 文件`（避开 `*/` 序列）。

**⑥ 回归实测（本轮）**
| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（共享层） | **330** | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/message/message-rpc` | **93** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 |
| `service/task/task-rpc` | **129** | 0 | 0 | 0 |

⇒ ⇒ 三模块 `clean test`（带 `-am`）⇒ **BUILD SUCCESS · 7:41 min** ⇒ ⇒ 本域 `task-rpc` = **129**（上轮 125 ⇒ **+4** = 机制层 3 条 ＋ 覆盖层 1 条）⇒ ⇒ 共享层 `service/common` = **330（未变）** ⇒ ⇒ ⚠️ **口径**：本轮只跑了 3 个 rpc 模块 ＋ 它们的依赖（`-am`）⇒ ⇒ **9 模块口径尚未重测** ⇒ ⇒ **本域之外的模块条数一律沿用上一轮实测值，不做推算**。

**⑦ ⚠️ 口径提醒（写给下一个复核者）**
- **数「真容器类」要数 `new PostgreSQLContainer<>` / `new GenericContainer<>` 的**声明**，**不要**把 import / javadoc / 泛型里的类型名算进去（与「数真 `RANDOM_PORT` 注解、不数提及」同款纪律）。
- **端口守门是源扫型**：它核的是「**源码上有没有绑**」；机制层才是「**运行期绑没绑成**」⇒ ⇒ **两层不重复、缺一不可**。

**⑧ 原值保留不改写**
本轮**未改动**任何生产代码 ⇒ ⇒ 前面各轮的行为结论与条数口径**一律不变**；本轮新增的只有**测试面**（6 个容器类的绑定 ＋ 6 个守门类 ＋ 3 份支撑类）⇒ ⇒ **域侧条数的变化**见 §⑥ 的实测。

---

### 🟢 第二十一轮 · 附 · **C7 收口复跑回执**（并入 G4 尖端 `a38ad75` 之后）（2026-10-28）

**① 合并**
- 父提交 = `98e458e`（第二十一轮：Testcontainers 宿主口层 2 落码）＋ **`a38ad75`**（G4 尖端）⇒ 提交 **`47a2b82`**。
- `git merge-tree --write-tree` **预演零冲突**（退出 0 · 单一 tree 哈希）⇒ ⇒ 实际合并**零冲突**。
- ⚠️ **G4 尖端在本轮内仍在移动**（`2643f91` → `96b40df` → `766e7dc` → `a38ad75`）⇒ ⇒ 按 G4 §4.10.54 的口径「**merge【分支尖端】而非固定 commit 号**（固定号天然会过期）」执行 ⇒ ⇒ **波末收口时仍需再 merge 一次当时的前沿**。

**② 🔴 一条被实测推翻的预测（「条数不变，仍 330」）**
- G4 §4.10.41 的 C7 义务写的是：「`e966a67` = **仅 test 面** ⇒ 复跑 `service/common` ＋ 耦合最紧的 1 个域模块（**条数不变，仍 330**）」。
- ⚠️ **实测：共享层的**生产代码**也变了** —— `git diff d546f45 a38ad75 -- service/common/src/main` ⇒ `GoStyleClientDisconnectProbe.java`（**+59**）＋ `ObservabilityLifecycle.java`（**+10**）⇒ ⇒ **「仅 test 面」这个前提不成立**（那只对 `e966a67` 这一笔成立，**对整段待并区间不成立**）⇒ ⇒ **条数确实变了**。
- ⇒ ⇒ **实测 `service/common` = 331**（上轮 330 ⇒ **+1**）⇒ ⇒ **「330」这个数已过期**，以本条为准（**330 原值保留不改写**）。

**③ C7 复跑实测（`service/common` ＋ `task-rpc` · 带 `-am` · BUILD SUCCESS · 2:55 min）**

| 模块 | tests | failures | errors | skipped | 与上轮比 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | **331** | 0 | 0 | 0 | **330 ⇒ 331（+1）** |
| `service/task/task-rpc` | **129** | 0 | 0 | 0 | 未变 |

**④ 四个台账门禁（**必须用合并后的新版** ⇒ `tools/check-ledger-selfcheck.py` 本轮被改了 +33 行）**

| 门禁 | 结果 |
|---|---|
| `check-ledger-tables.py` | **EXIT=0** |
| `check-ledger-selfcheck.py`（**新版**） | **EXIT=0** |
| `check-ledger-count.py` | **EXIT=0** |
| `check-doc-links.py` | **EXIT=0** |

⇒ ⇒ ⚠️ **一条纪律（本轮实测）**：**门禁脚本本身也会被合并改版** ⇒ ⇒ 「上轮门禁绿」**不能**当「这轮门禁绿」的证据 ⇒ ⇒ **合并后必须用**新**的门禁重跑**（我上一轮的 5/5 绿是**旧版**脚本给的 ⇒ 不算数）。

**⑤ ⚠️ 9 模块口径：仍**不可推算**
- 本轮只跑了 `service/common` ＋ `service/task/task-rpc` 两个模块（＋ 依赖）⇒ ⇒ **其余 7 个模块的条数本轮未重测** ⇒ ⇒ **9 模块合计不做推算、不写进台账** ⇒ ⇒ 权威值仍只能来自「**四 lane 全合后的一次回归**」。
- ⚠️ **域不同 ⇒ 数字不可相加**：G1 域侧 ＋ G3 域侧 ＋ 我域侧 ＋ 共享层 **≠** 任何单域的 9 模块口径。

---

## 4·十二、🟢 **第十二轮（2026-10-27 18:4x）· 合并 G4 的 13 笔（零冲突）⇒ 新机器门禁在本树为绿 ＋ 全仓 U+FFFD 复扫下降 ＋ 两个红门禁的根因定位到行**

### ① 合并（`5636164`）

- **冲突面 0**（`git merge-tree --write-tree` 预演确认）⇒ ⇒ **未由本 lane 在 `tools/**` 或 `docs/DRIFT/global/**` 里写任何内容**（写权零越界）
- 带入 13 笔，关键三件：
  - `22dc67e` `test(common)`：**`GoStyleRedisClientShadowingGateTest`** —— 把「不得新建域内 Redis 副本」从**台账禁令**落成**机器门禁**（3 条断言，各带专属变异）
  - `cc6e38e` / `f68a9cd`：**修掉本轮全仓扫描里那 3 份「无人拥有」的 U+FFFD 真损坏**（`tools/errmsg-probe/README.md` · `tools/sql-statement-probe/favorite/compare.py` · `component-semantic-verification/springboot-semantic-model.md`）
  - `6cb0a60`：订正 FB-04 javadoc 的计数口径（我方反馈单 FB-04）· `69769ca`：§4.10.27 Redis 超时跨域全表（我方「命令超时联动」发现已推广）
- **共享层生产代码**：仅 `service/common/.../RpcInterceptor.java` 的 **javadoc 订正**（+19/−2，**零行为变更**）⇒ ⇒ **本域 like 域 A9 端到端探针的前提不受影响**

### ② 🟢 新机器门禁**在本树真跑过且为绿**（不是「读源码推断」）

- 实测：`mvn -o -B -DargLine=-Xmx2g -pl service/common test` ⇒ **327 / 0f 0e 0s**（324 + **3** = G4 那 3 条）· **BUILD SUCCESS**
- surefire 报告里三条用例**都在**：`scannerSeesTheRealRepository` · `noDomainSideShadowingCopyExists` · `scannerActuallyReportsACopy`
- **我三域 `src/main/java` 的 `LettuceClientOptionsBuilderCustomizer` 命中数 = 0** ⇒ ⇒ 我域那两处引用在 `src/test/java`（`LikeRedisDisconnectFastRejectGateTest` · `TaskRedisDisconnectFastRejectGateTest`）⇒ ⇒ **门禁的扫描范围（只扫 `src/main/java`）天然排除** ✔
- 白名单那份（users 域 `RedisDisconnectedBehaviorConfig.java`）在本树**仍存在** ⇒ ⇒ 门禁的 ③(b) 通过 ⇒ ⇒ 与「自适配」设计一致（G1 删掉后那半条自动为空）<br>🆕 **2026-10-27 第十八轮订正（G1 [1] 回信 · 第二处）**：G1 已在 `312a09b` **删掉全部 5 份域内副本** ⇒ ⇒ **「在本树仍存在」只对我当前树成立**（我树未合 `312a09b`；实测仍有 1 命中）⇒ ⇒ **合并后这半条会自动为空** ⇒ ⇒ 门禁的**自适配**设计不受影响（③(b) 仍通过）⇒ ⇒ **结论不变**。

### ③ 🟢 全仓字节级 U+FFFD 复扫（**合并后**）：**15 份 / 38 字节 ⇒ 12 份 / 27 字节**（−3 份 / −11 字节）

- 消失的正是我此前登记为「**无人拥有**」的那 3 份（见本文件「无人拥有」条目的订正注记）⇒ ⇒ **那张清单可以清空**
- ⇒ ⇒ 剩下 12 份里**本域写权面 = 0**（`docs/Pitfalls/message/rpc.md` 的 2 字节是**故意保留**的故障样本）

### ④ 🔴 两个红门禁的根因（**定位到行**，修法在 G4 侧）

- **① `check-tomcat-limits.py`**：它把 task-api 期望写死 `raw`，而**共享层自己的权威语义表**写的是 OFF ——
  `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:237`
  ⇒ `new DomainReachability("service/task/task-api", false, false, Mode.OFF, …)`
  ⇒ ⇒ **门禁的期望表与权威表自相矛盾** ⇒ ⇒ 修法：**①** 期望表改 `off`；**②**（更耐漂移）门禁改成**读** `GoStyleFormLimitMode`
- **② `check-wave-coupling.py`**：漂移 6 项 ⇒ **9 项**，且 **9 项全部可归因到本域三域的组件引用**（**不是代码错，是文档快照落后于本域合并**）：
  - `GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` ⇒ **+task**
  - `GoStyleGrpcClientBreakerInterceptor` / `MqConsumer` ⇒ **+like**
  - `GoStylePathNormalizingFilter` ⇒ **+like +message +task**（共享层落码 ＋ 本域第十轮探针引用）
  - `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor` ⇒ **+message +task**
  - `GoStyleRpcProtectionChain` ⇒ **+message** · `RpcInterceptor` ⇒ **+like**
  - ⚠️ **但这条不能在任何单棵 lane 树上重生成快照**（每棵树都缺别家的提交）⇒ ⇒ **必须在四 lane 全合入后（= C7 窗口）重生成** ⇒ ⇒ 提前改只会改出**第二份过期快照**
  - 📌 **口径提示**：门禁的 `scan_common_radiation` 扫 `service/**/src/**/*.java` ⇒ ⇒ **含 `src/test/java`** ⇒ ⇒ 本域第四轮加的三域链层守门测试也进了辐射集 ⇒ ⇒ 快照条目天然比「只看生产源码」多（**判据口径如此，不是多算**）

### ⑤ ⚠️ 数字口径（**推算 ≠ 实测**）

- 合并后共享层 **324 ⇒ 327**（+3 = G4 的新机器门禁）⇒ ⇒ **9 模块合计预计 992 ⇒ 995** ⇒ ⇒ **本轮未重跑 9 模块** ⇒ ⇒ **995 是推算，不是实测** ⇒ ⇒ 本树 9 模块全量的下一个权威口径 = **C7**

### 🟢 第二十二轮 · **台账「文件:行号」引用全量核对**（用 G3 的核对器扫本域）⇒ **0 处真缺陷** ＋ 并入 G4 尖端 `b79779e`（2026-10-28）

**① 并入 G4 尖端**
- 父提交 = `caba808`（第二十一轮附 · C7 回执）＋ **`b79779e`**（G4 尖端）⇒ 提交 **`4f9b326`**。
- `git merge-tree --write-tree` **预演零冲突** ⇒ ⇒ 实合并**零冲突**。
- G4 那 2 笔新提交（`c0ca71e` §4.10.57 · `b79779e` §4.10.58）**只改 `docs/DRIFT/global/vs-common.md`** ⇒ ⇒ **零代码改动** ⇒ ⇒ 回归数字不受影响。
- 合并后 **6 个台账门禁全绿**（`check-ledger-tables` · `check-ledger-selfcheck` · `check-ledger-count` · `check-doc-links` · `check-wave-lane-ownership` · `check-flyway-ledger` ⇒ EXIT=0 ×6）。

**② 🔴 自查：用 G3 的核对器全量核本域台账的「文件:行号」引用**
- 工具 = G3 的 `/tmp/g3_sweep_refs3.py`（**同一台机器 ⇒ 可直接跑**）⇒ ⇒ 我把**源根**与**扫描域**改成我三域后跑（脚本仍留在 `/tmp`，**未进仓** —— `tools/**` 是 G4 写权面 ⇒ ⇒ **我不落码**）。
- **本域 674 处「文件:行号」引用逐条核「文件存在 + 行号在范围内」** ⇒ ⇒ **首轮报 5 处** ⇒ ⇒ **逐条复核后：真缺陷 0 处**。
- ⚠️ **这条是「自查」不是「等别人报」** —— 同类缺陷（`breakerhandler.go` 越界）**我自己域内也曾存在**（本文件第二十一轮段记着「原写区间 19–44 ⇒ 订正为 19–41」）⇒ ⇒ **同一个错误我自己犯过 ⇒ 就不能指望别人替我发现第二遍**。

**③ 首轮 3 处的逐条定性（全部是「转述 global 缺陷登记的原文」这一类误报）**

| # | 位置 | 首轮判定 | 复核结论 |
|---|---|---|---|
| 1 | `docs/DRIFT/message/vs-common.md:366` | RANGE-OUT（`breakerhandler.go` **42 行** ⇒ `44` 越界）| 🟢 **误报** —— 该行是**转述 global 那条缺陷登记的原文**（global `_README.md:576` 登记 hot 域漂移时**必须引述缺陷原样**）⇒ ⇒ **引用属实**（global 至今仍写 `breakerhandler.go:19-44`）⇒ ⇒ 已在该行加「**原引用**」标记（G3 核对器的排除约定）⇒ ⇒ 重跑**清零** |
| 2 | `docs/DRIFT/like/vs-common.md:556` | 同上 | 🟢 同上（已加标记）|
| 3 | `docs/DRIFT/task/vs-common.md:593` | 同上 | 🟢 同上（已加标记）|

**④ ✅ 重跑结果：0 findings**
- 补齐源根 + 加「原引用」标记后重跑 ⇒ ⇒ **`checked: 672` · `skipped: 320` · `by_kind: {}` · `findings: 0`**（**含本段自身的引用** ⇒ ⇒ 追加本段后复跑仍是 0）。
- ⇒ ⇒ **本域台账的「文件:行号」引用 ⇒ 零越界、零不存在**。

**⑤ ⚠️ 两条要转出去的事（都不在我写权面）**
- **`Sea-BreakTheWaves` 必须作为源根进核对器** —— 该仓在本机于 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`（⚠️ **不在** `java&golang/` 下）⇒ ⇒ 缺这个根 ⇒ ⇒ **任何引用下游消费者仓的域都会被误判成「文件不存在」** ⇒ ⇒ 这条已转给 G3 / G4（G3 已给 G4 派单要落 `tools/ledger-refcheck/` ⇒ ⇒ **这是那个派单必须补的一项**）。
- **`docs/DRIFT/global/_README.md:576`** 那行登记用的**裸 basename `breakerhandler.go:19-44`** 是**缺陷原样引述**（合法）⇒ ⇒ 我三域的转述已加「原引用」标记 ⇒ ⇒ **不要**把它当漂移去「修」。

- **本域无「指向下游消费者仓」的引用** ⇒ ⇒ 本域首轮 **3 处**全部是「转述 global 缺陷登记的原文」这一类误报 ⇒ ⇒ 但**缺 `Sea-BreakTheWaves` 源根这件事仍要转出去**（本域现在没有依赖它的引用，**不代表别的域没有** ⇒ ⇒ like 域就有 2 处）。

### 🔴 第二十二轮附三 · **收口回归的触发条件已变**（两个条件须【同时】满足 · 缺一不可）

- **① G4 的 §4.10.59 落码必须先提交** —— 实测（2026-10-28 02:3x）G4 树 HEAD **仍是 `5c2e3a5`**（`5c2e3a5..HEAD` 为空）⇒ ⇒ **那笔落码还在它的工作区未提交**（`GoStyleRpcSheddingInterceptor.java` 生产 +50 行 · `GoStyleRpcProtectionChainTest.java` 非密闭配方 · `GoStyleRpcSheddingInterceptorTest.java` **+1** 条新断言）⇒ ⇒ **我合不进它** ⇒ ⇒ 现在起 9 模块 `clean test` 会跑在**旧共享层**上 ⇒ ⇒ **那个数必然与 G4 修完后的数对不上 ⇒ ⇒ 白跑**。
- **② 独占 maven 窗口** —— 此刻 G1 树 / G3 树 / G4 树 / 主仓各有一条 maven 在跑 ⇒ ⇒ 并发全量会 OOM-kill（EXIT=137）⇒ ⇒ 不并发起跑。
- ⇒ ⇒ **「窗口空」≠「可以起跑」** ⇒ ⇒ **两个条件同时满足才起** ⇒ ⇒ 已报 G4，请其落码提交后知会我。
- ⚠️ **共享层条数会再动**：C7 实测 **331** ⇒ 按 G4 那笔 diff ⇒ **推算 332** ⇒ ⇒ **这是推算、不是实测** ⇒ ⇒ **「共享层 331」降级为临时值**（我此前给 G1/G3 的通报同理）⇒ ⇒ **别当最终基线**。
- 🟢 **落码本身我核过（不采信转述）**：生产侧把 RT 测量抽成 `measuredRtMs(long startNanos)` 且改用 **`/ 1_000_000D`（浮点）** ⇒ ⇒ 与 Go `adaptiveshedder.go:284-287` 的 `float64 + math.Ceil` **逐字等价** ✔ ⇒ ⇒ 「**抽出来是为了让它有网**」这个理由成立（调用点那一截断原先确实无任何断言覆盖）⇒ ⇒ **本轮零域侧落码、零域侧动作**。

---

---

### 🟢 第二十三轮 · **波末收口回归实测：9 模块 = 1022（域侧 689 ＋ 共享层 333）** —— 两个前置条件已【同时】满足

**① 两个前置条件的解除（都实测过，不是转述）**
- **① G4 的 §4.10.59 落码已提交** ⇒ ⇒ `d212323`（`fix(common): 降载 RT 的【调用点】整型截断 ⇒ 与 Go 的「浮点毫秒 + ceil」对齐 + 两处都有网`）⇒ ⇒ G4 树工作树 clean ⇒ ⇒ **我已并入**（`adfdf30`；`git merge-tree --write-tree` 预演**零冲突**）⇒ ⇒ **7 条门禁全绿**（EXIT=0 × 7）· 台账「文件:行号」引用核对 **0 findings**（`checked 672 / skipped 320`）。
- **② 独占 maven 窗口** ⇒ ⇒ 起跑前实测：**只有 G3 树在跑**（G4 树 / 主仓 / 我树都没在跑）⇒ ⇒ **窗口空** ⇒ ⇒ ⇒ **两个条件同时满足** ⇒ ⇒ 本轮起跑。

**② 回归命令与口径（写死，后续复用）**
```
mvn -o -B -DargLine=-Xmx2g -am -pl service/common,service/message/message-api,service/message/message-rpc,service/message/message-common,service/like/like-api,service/like/like-rpc,service/like/like-common,service/task/task-api,service/task/task-rpc clean test
```
- **必须是 `clean test`（不是 `verify`）** —— `service/task/task-rpc/pom.xml:364-376` 把 `*IT` 绑在 `verify` 阶段的第二个 surefire execution 上 ⇒ ⇒ `verify` 的合计会混进 `*IT` ⇒ ⇒ 本轮两口径（含 IT / 不含 IT）**都是 1022** ⇒ ⇒ `clean test` 下两者重合 ✔
- **必须带 `-am`** —— 否则 reactor 不含 `service/common` ⇒ ⇒ `com.rag:common` 从 `~/.m2` 解析 ⇒ ⇒ **跑的是旧 jar，不是你以为的那份共享层**（G1 已踩过，见 G4 台账 §4.10.29.2）。

**③ 逐模块实测（本轮 · BUILD SUCCESS · 14:47 min · 2026-10-28 02:55:03 → 03:09:52）**

| 模块 | 条数 | F | E | S | 相对第二十轮 |
|---|---|---|---|---|---|
| `service/common`（共享层） | **333** | 0 | 0 | 0 | 330 ⇒ **+3** |
| `service/message/message-api` | **103** | 0 | 0 | 0 | 103 ⇒ 0 |
| `service/message/message-rpc` | **93** | 0 | 0 | 0 | 89 ⇒ **+4** |
| `service/message/message-common` | **21** | 0 | 0 | 0 | 21 ⇒ 0 |
| `service/like/like-api` | **94** | 0 | 0 | 0 | 94 ⇒ 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 | 125 ⇒ **+4** |
| `service/like/like-common` | **18** | 0 | 0 | 0 | 18 ⇒ 0 |
| `service/task/task-api` | **102** | 0 | 0 | 0 | 102 ⇒ 0 |
| `service/task/task-rpc` | **129** | 0 | 0 | 0 | 125 ⇒ **+4** |
| **域侧 8 模块小计** | **689** | 0 | 0 | 0 | 677 ⇒ **+12** |
| **9 模块合计** | **1022** | **0** | **0** | **0** | 1007 ⇒ **+15** |

**④ 交叉核对（两条独立路径 · 逐字相等）**
- **XML 侧**：9 个目标模块 `target/surefire-reports/TEST-*.xml` 的 `<testsuite>` 属性**逐模块求和** ⇒ ⇒ **1022**。
- **maven 侧**：模块级 `Results:` 汇总行合计 ⇒ ⇒ **1061** ⇒ ⇒ **差 39 = `service/users/users-common`**（`-am` 带出的**非目标**模块，**不属收口口径**）⇒ ⇒ **1022 + 39 = 1061 ⇒ 逐字相等** ✔
- ⚠️ **一个本轮踩到的坑**：第一版核对正则把**每类用例行**（`Tests run: N, … -- in <class>`）也当成模块级汇总行 ⇒ ⇒ 得 2122（= 1061 × 2）⇒ ⇒ **过滤条件必须是 `-- in`（双横线）** ⇒ ⇒ 否则会拿到一个「看起来对、其实重复计数」的数。
- ⚠️ **收口口径只算 9 个目标模块** —— `-am` 会把 `users-common` / `admin-proto` 等依赖闭包带进 reactor（实测 reactor 共 **23** 个模块）⇒ ⇒ **拿 reactor 全量当收口数字会得 1061，不是 1022**。

**⑤ 增量的归因（每一处都有出处）**
- **域侧 677 ⇒ 689（+12）** ⇒ ⇒ **全部来自第二十一轮**新增的固定宿主口守门断言：`TaskPostgresFixedHostPortGateTest`（机制层 3 条）＋ `TaskContainerFixedHostPortSegmentGateTest`（覆盖层 1 条）= **+4** ⇒ ⇒ message 域 +4 · like 域 +4 ⇒ ⇒ **12** ⇒ ⇒ ⇒ **⇒ 相对第二十一轮的域侧实测值，本轮 = 0 变化**（第二十一轮台账已记 task-rpc = **129** / message-rpc = **93** / like-rpc = **129** ⇒ 本轮逐条对上 ✔）。
- **共享层 330 ⇒ 333（+3）** ⇒ ⇒ 其中 C7 复跑已到 **331**（第二十二轮）⇒ ⇒ **本轮 +2** ⇒ ⇒ **全部来自 G4 `d212323` 新增的 2 条守卫**（`rtMeasurementKeepsFractionalMilliseconds` · `callSiteMustPassFractionalMillisecondsToShedder`）⇒ ⇒ ⇒ **⇒ 与 G4 自己 commit message 里写的「331 + 本次新增 2 条 = 333」逐字一致** ✔（**独立复核，非采信转述**）。

**⑥ 原值保留不改写**
第二十轮的 **1007 / 677 / 330**、第二十一轮的域侧 **689**、第二十二轮的 **331** 与推算值 **332** —— **全部原样保留**；本段的 **1022 / 689 / 333** 为**当前权威值**。

**⑦ 判绿红只看两项（本轮日志里全是噪音）**
本轮日志里满屏 `logger-test-fatal` / `otel exporter 装配失败` / `kafka NetworkClient` / `nacos register FAILED` 等 **ERROR 级日志行** ⇒ ⇒ **那些不是失败** ⇒ ⇒ **唯一判据 = surefire 的 `Failures`/`Errors`/`Skipped`（0 / 0 / 0）＋ mvn 退出码（EXIT=0）＋ `BUILD SUCCESS`** ✔

**⑧ ℹ️ 本域 U+FFFD = 0**
我域（message / like / task）源码里的 **9 处真乱码已修并提交** ⇒ ⇒ **本树已归零** ⇒ ⇒ ⚠️ 但**「不许改」名单 19 处**（题材/被测对象 6 ＋ G1 域台账证据表 13）必须**显式保护** ⇒ ⇒ 详见给 G4 / G1 / G3 的三封通报（**已发出**）。

---

### 🟡 第二十四轮 · ⚠️ 「task-rpc 测试数 = 129」这格**已过期**，待一次 `clean test` 全量复跑后替换

**① 代码态变了（2026-10-28 · G3 转派配方 ⇒ 我域自修 · 提交 `83d691a`）**
- 产物层守门 `TaskRpcFatJarArtifactGateIT` ⇒ **改名为 `TaskRpcFatJarArtifactGateTest`** ⇒ ⇒ 它**不再只在 `verify` 阶段跑** ⇒ ⇒ **改由测试自己构建 fat jar**（命令带 `-am`）⇒ ⇒ **进 surefire 默认 `test` 阶段 ⇒ 进收口口径**。
- 同批：`isStale` 已把**共享层**（`service/common/src/main` 全量 ＋ `service/common/pom.xml`）算进「新鲜度」输入集 ⇒ ⇒ 避免「共享层改了而本模块没改」时**不重建** ⇒ ⇒ 守门读到**陈旧内嵌 jar**（⇒ 假绿：掩盖共享层的破坏 / 假红：报错方向被带偏）。
- ⇒ ⇒ **我域 9 模块现在没有任何 `*IT` 类** ⇒ ⇒ **`mvn test` 与 `mvn verify` 两个口径对我域已重合** ⇒ ⇒ 第十七轮 ⑧ 那条「两个口径差 5 条」的结论**对我域已作废**（原值保留，见下）。

**② 条数影响：129 ⇒ 预期 134（+5）—— ⚠️ 这是【推算】，不是实测**
- 🔴 **门禁现在报的「实测 134」不能当权威数**：实测它的来源 = **上一轮全量回归留下的 129 条旧报告（mtime 2026-10-28 03:07~03:09）＋ 我后来一次定向跑新增的 5 条**（`TaskRpcFatJarArtifactGateTest.xml`）⇒ ⇒ **孤儿/混合报告** ⇒ ⇒ ⇒ **⇒ 权威值必须来自一次 `clean test` 全量跑**（`clean` 会清掉全部旧报告）⇒ ⇒ **待 maven 窗口空时复跑实测**。
- ⇒ ⇒ 本段**不写数字**（纪律：只跑部分模块 ⇒ 不做推算、不写进台账）。

> 📌 **【实测已到 · 原值保留】**（2026-10-28 10:04）：本段那个「预期 134（+5）」**已被一次 `clean test` 全量实测闭合**
> ⇒ ⇒ **实测 `task-rpc` = 134**（与推算逐字相等）⇒ ⇒ **129 / 134 并列**（129 原样保留，134 为当前权威值）
> ⇒ ⇒ 详见下文「🟢 第二十五轮 · 波末收口回归实测：9 模块 = 1030」③。

**③ 原值保留不改写**
**129**（第十七轮起沿用）**原样保留**；本段只说明它**已过期**，实测后由新值替换（**原值届时也不删，改为并列**）。

**④ 写权与归属**
本段全部落在 G2 写权面（`service/task/**` ＋ `docs/DRIFT/task/**`）⇒ ⇒ **不涉共享层、不涉 G4 面**。
⚠️ **G4 面仍有 2 处旧类名未改**（`docs/DRIFT/global/lanes/L13-rpcboot-message-task.md` · `docs/DRIFT/global/_README.md`）⇒ ⇒ **属 G4 写权 ⇒ 已通知 G4，本 lane 不越权改**。

### 🔴 第二十五轮 · **本域 5 个 rpc 测试类的监听口撞 Docker 发布口 ⇒ 已修**（根因与完整证据链见 `message/vs-common.md` 第二十五轮）

**① 现象（波级 · 本域侧）**
第二十四轮收口回归在 `message-rpc` 段报 `java.net.BindException: Address already in use`（`/0.0.0.0:49992`）
⇒ **该 fork 第一个 gRPC 监听就撞口** ⇒ ⇒ 整批 context 起不来 ⇒ ⇒ 表现为「被测行为坏了」，**实为被测环境起不来**。

**② 本域受影响面（实测复算 · 口径 = 非注释行 ＋ 只算测试类）**
`service/task/task-rpc` 写 `grpc.server.port=0` 的**真实测试类 = 5**：
`TaskGrpcInterceptorWiringTest` · `TaskRedisDisconnectFastRejectGateTest` · `TaskRpcSlowThresholdGateTest` ·
`TaskRedisPoolExhaustionP99GateTest` · `TaskProgressUpsertParityTest`。

**③ 修法（已落码 · `e9c3946`）**
新增 `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/support/GrpcTestPort.java`（96 行）
＋ 上述 **5 个**类各注入 `registry.add("grpc.server.port", GrpcTestPort::freePort)`。
⇒ 📌 `@DynamicPropertySource` 优先级高于 `@SpringBootTest(properties)` ⇒ 内联的 `=0` **保留作回退**。
⇒ 📌 **不用固定口**（G4 波末全量与各域收口回归会并发跑同一批 rpc 模块 ⇒ 固定口 ⇒ 两棵树互撞）
⇒ ⇒ **不新增端口段、不动 `22700–22999` 段表** ⇒ 与 G4 的段表裁定**无冲突**。

**④ 证据**
- 本域 5 个类在本轮 9 模块 `clean test` 中**全部绿**（`task-rpc` 段 F/E/S = 0/0/0）
- 整轮 `Address already in use` 命中数 = **0**
- 波级隐患面（已通报 G1 / G3 / G4）：全仓 **67** 个真实测试类写 `grpc.server.port=0`；我域 3 个 rpc 共 **21** 个已修
  ⇒ ⇒ **仍待修 46**（article 8 · comment 2 · favorite 8 · follow 3 · hot 5 · admin 11 · user 9）
  ⇒ ⚠️ 我第一版统计说「70」是**错的**（多算了 3 个 `GrpcTestPort` helper 的 javadoc 文字）⇒ **原值保留不改写**，详见 `message/vs-common.md` 第二十五轮 ⑤。

### 🟢 第二十五轮 · **波末收口回归实测：9 模块 = 1030（域侧 697 ＋ 共享层 333）· 全绿**（2026-10-28 10:04）

**① 口径与出处**
- 跑在 **`b5910dc`**（工作树干净）⇒ ⇒ **本轮数只对这个 commit 成立**。
- 命令：`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test`（**`clean test` 不是 `verify`**、**必须带 `-am`**）。
- 日志 `/tmp/g2-r29-regression.log` · 跑批脚本 `/tmp/g2-r29-run.sh`（**含三道起跑前体检**）。

**② 本域三个模块的实测**

| 模块 | 条数 | F | E | S | 相对第二十三轮 |
|---|---|---|---|---|---|
| `service/task/task-api` | **102** | 0 | 0 | 0 | 102 ⇒ 0 |
| `service/task/task-rpc` | **134** | 0 | 0 | 0 | 129 ⇒ **+5** |

**③ ⇒ ⇒ 第二十四轮那个「推算」现在被【实测】闭合了**
- 第二十四轮 ② 写的是「**129 ⇒ 预期 134（+5）—— ⚠️ 这是【推算】，不是实测**」，
  且当时明确「**本段不写数字**」（纪律：只跑部分模块 ⇒ 不做推算、不写进台账）。
- ⇒ ⇒ 本轮**一次 `clean test` 全量**（`clean` ⇒ 旧报告全清 ⇒ **无孤儿/混合报告**）实测
  **`task-rpc` = 134** ⇒ ⇒ **推算值与实测值逐字相等** ✔
- ⇒ ⇒ 按第二十四轮 ③ 立的规矩（「**原值届时也不删，改为并列**」）⇒ ⇒
  **129 与 134 并列**：**129** = 第十七轮起沿用的旧值（**原样保留**）· **134** = **当前权威值**。

**④ 域侧小计与增量归因**
- 域侧 8 模块小计 = **697**（第二十三轮 689 ⇒ **+8**）⇒ ⇒ **+8 全部来自第二十四轮的落码**：
  **message-rpc +3**（新增产物层守门 `MessageRpcArtifactClasspathGateTest` 的 3 条）＋ **task-rpc +5**
  （`TaskRpcFatJarArtifactGateTest` 由 `*IT` 改名 ⇒ **进 `clean test` 口径**）⇒ ⇒ **like 域 +0 · task 域 +5** ✔

**⑤ 交叉核对**
- XML 侧（9 个目标模块 `<testsuite>` 属性求和）= **1030**；maven 模块级汇总行 = **1069**
  ⇒ ⇒ **差 39 = `service/users/users-common`**（`-am` 带出的**非目标**模块，不属收口口径）⇒ ⇒ **1030 + 39 = 1069 逐字相等** ✔
- ✅ **含 IT / 不含 IT 两口径都是 1030** ⇒ ⇒ **我域已无 `*IT` 类** ⇒ ⇒ `test` 与 `verify` 两口径**重合** ✔
  （全仓 `TEST-*IT.xml` 实测 = **0 个** ⇒ ⇒ 第二十四轮 ① 那条「我域 9 模块现在没有任何 `*IT` 类」**已被实测确认**）

### 🔴 第二十五轮附二 · **本域 2 处 `ServerBuilder.forPort(0)`（程序内起 server · OS 盲选口）⇒ 已修**（完整机制见 `message/vs-common.md` 第二十五轮附二）

**① 现象（波级 · 本域侧）**
r30 复跑在 `message-rpc` 段报 `http2 exception` ⇒ `Http2Exception: First received frame was not SETTINGS. Hex dump: 2d45525220`
⇒ ⇒ **`2d45525220` = ASCII `-ERR ` = Redis 的错误前缀** ⇒ ⇒ 客户端连上了 **`Sea-redis`**（发布口 **49380**）
⇒ ⇒ macOS 临时段 `49152–65535` ⇒ ⇒ **49380 就在临时段里** ⇒ ⇒ 与 Docker published 段**同池**。
⇒ ⇒ ⚠️ **与本域那条 `grpc.server.port=0` 是同一族、两个形态**：① 那条是 `BindException`（bind 直接失败）；
② 这条是**协议错**（bind 成功但连到隔壁）⇒ ⇒ **② 更隐蔽**。

**② 本域受影响面（实测 · 只算真实代码）**
`service/task/task-rpc` 的 `forPort(0)` = **2 处 / 2 个类**：`TaskRpcSlowThresholdGateTest`（1）· `TaskGrpcInterceptorWiringTest`（1）。

**③ 修法（已落码）**
2 处 ⇒ `ServerBuilder.forPort(GrpcTestPort.freePort())`（每处带【为什么不能盲选】的注释留痕）。
⇒ 🔴 **为什么探测式能挡住**：探测用 `new ServerSocket().bind(new InetSocketAddress(port))` ⇒ **绑 wildcard（0.0.0.0）**
⇒ ⇒ Docker 代理占着 wildcard ⇒ ⇒ **EADDRINUSE** ⇒ ⇒ 探测拒绝该口；而 Netty 那个 server 只绑了 IPv6 ⇒ ⇒ 盲选会穿过去。

**④ 波级面**
全仓 `forPort(0)` 真实代码处 = **34**（含我域 9）⇒ ⇒ **我域 9 处已修 ⇒ 剩 25**：
G4 共享层 **5** · G3 **11**（article 3 · comment 4 · hot 4）· G1 **9**（follow 2 · admin-rpc 3 · user-rpc 3 · admin-api 1）。
⇒ 📌 **共享层那 5 处属 G4 写权** ⇒ ⇒ **本 lane 只通报、不落码**。

### 🔴 第二十七轮 · **我自己的「起跑前体检」有三道判据是坏的** —— 探针恒假 / 自检判据自相矛盾 / 体检顺序反了（2026-10-28 12:1x）

⇒ 📌 **完整经过与逐条证据在 [`message/vs-common.md` 第二十七轮](../message/vs-common.md)**（同一批脚本，同一个问题）⇒ ⇒ 本节只记**与本域直接相关的三条**。

**① 本域的「等窗口」防线此前是摆设**
旧探针 = `ps -Ao comm=,args= | awk '$1 ~ /^java$/'` ⇒ **macOS 的 `comm=` 给全路径 `/usr/bin/java`** ⇒ `/^java$/` 恒不匹配
⇒ ⇒ 探针**恒返回 0** ⇒ ⇒ **r29 / r30 / r31 / r32 四轮跑批从未真正检查过窗口**（各轮 wait.log 逐字写着 `WINDOW-FREE after 1 polls` ⇒ 第一次轮询就判「空」）⇒ ⇒ **本域那几次 1030 的实测仍成立**（都是全绿实测），
但「跑之前确认过没撞车」这句**当时不成立** ⇒ ⇒ **原值保留不改写**，本条即订正。
⇒ ⇒ ⚠️ **【订正注记 · 2026-10-28 12:3x】**：本条先前写「**r29 / r31 / r32 三轮**」⇒ ⇒ **应为四轮**（r30 同款形态）⇒ ⇒ 原措辞保留在本注记里。
⇒ ⇒ ⚠️ **r28 那轮不受影响**（它用 `ps -Ao args=` 形态 ⇒ wait.log 逐字记录 33 次 `busy(real=N)` 后才等到空）。
✅ 已修：探针 v2 ＋ **正样自检**（`base=3 pos=4 after=3`）＋ **顺序修正**（先等窗口净、再查孤儿 ⇒ 避免把**活容器**误判成孤儿）。

**② U+FFFD：本域源文件 0 处**
字节级实测（HEAD `36e117c` · 2026-10-28 12:12 · 剔 `target/`）⇒ **12 份 / 27 处** = 题材 6 ＋ G3 13 ＋ G1 8 ⇒ ⇒ **本域（task）0 处** ✔
⇒ ⚠️ 本域 fat jar（`task-rpc/target/task-rpc-0.0.1-SNAPSHOT.jar`）含 **8 处** ⇒ **只是源文件的镜像，不新增问题**。

**③ 口径澄清（含 / 剔 `target/`）**
含 `target/` = **15 份 / 49 处** · 剔 `target/` = **12 份 / 27 处** · **含 − 剔 = 3 份 / 22 处**
⇒ ⇒ 与 G4 报的那对数（18/60 与 15/38）**差值逐字相同（3 份 / 22）** ⇒ ⇒ **口径本来就一致**，差的是【快照时刻】
⇒ ⇒ **真正要统一的是「报告必须带【快照时刻 + 口径】」**。

**④ 🔴 「数被读成两个数」的实例：XML 侧被【定向跑】污染，日志侧没被污染**
本轮用同一份 r32 日志复跑聚合脚本 ⇒ **XML 侧 1034 / 日志侧 1030**（差 4）⇒ ⇒ 根因 = **A3 探针的定向跑重写了 `message-api` 的 surefire 报告**（XML 侧多那 4 条），
而**日志的模块级汇总行不可变**（直查 r32 日志：`message-api` 逐字写着 **103**）⇒ ⇒ **r32 的 1030 成立**（A3 测试文件 11:02:44 才提交，晚于 r32）。
⇒ ⇒ **纪律**：权威数只认【日志模块级汇总行】；XML 侧只在「本轮 `clean` 之后」才有意义 ⇒ ⇒ **收口态必须是 `clean test`**。
⇒ ⇒ 完整经过 ⇒ [`message/vs-common.md` 第二十七轮 ⑦](../message/vs-common.md)。

### 🟢 第二十八轮 · **波末收口回归实测：9 模块 = 1034**（域侧 701 ＋ 共享层 333）· **三口径逐字相等**（2026-10-28 12:4x）

**① 实测**：`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test` ⇒ **BUILD SUCCESS** · **MVN_EXIT=0** · 10 min 58 s · 2026-10-28 12:33:37 → 2026-10-28 12:44:35 · HEAD `3423390` ⇒ ⇒ 0 次 `Address already in use` / 0 次 `port is already allocated` / 0 次 `http2 exception` ✔

**② 逐模块（9 个目标模块）**
| 模块 | 模块级汇总行 | 逐类求和 | XML 求和 | 逐类行数 | F/E/S |
|---|---|---|---|---|---|
| shared | **333** | **333** | **333** | 63 | 0 / 0 / 0 |
| message-api | **107** | **107** | **107** | 16 | 0 / 0 / 0 |
| message-rpc | **96** | **96** | **96** | 23 | 0 / 0 / 0 |
| message-common | **21** | **21** | **21** | 2 | 0 / 0 / 0 |
| like-api | **94** | **94** | **94** | 11 | 0 / 0 / 0 |
| like-rpc | **129** | **129** | **129** | 24 | 0 / 0 / 0 |
| like-common | **18** | **18** | **18** | 2 | 0 / 0 / 0 |
| task-api | **102** | **102** | **102** | 9 | 0 / 0 / 0 |
| task-rpc | **134** | **134** | **134** | 19 | 0 / 0 / 0 |

> 🆕 **2026-10-29 对齐（W-B lane · 台账对齐）—— 上表是 **2026-10-28 12:4x r33 的 9 模块 `clean test` 快照**，**原值一律保留不改写**（改它等于伪造一次带日期的实测记录 ⇒ 也会连带把别的域的行改坏）。⇒ **本表里 `task` 两行已过期**：
>
> | 模块 | 本表（2026-10-28 r33 快照） | **当前权威值（2026-10-29 实测 · `mvn test` 口径）** | 依据 |
> |---|---|---|---|
> | `task-api` | **102** / 9 个测试类 | **135** / 10 个测试类 | `mvn -o -B -pl service/task/task-api test` ⇒ **BUILD SUCCESS** · surefire XML 求和 **135** · maven 模块汇总行 **135** · 增量 +33 = `TaskApiFailurePathMatrixTest`（2026-10-29 R2 新增） |
> | `task-rpc` | **134** / 19 个测试类 | **137** / 20 个测试类 | `mvn -o -B -pl service/task/task-rpc test` ⇒ **BUILD SUCCESS** · surefire XML 求和 **137** · maven 模块汇总行 **137** · 增量 +3 = `TaskRpcKafkaListenerRegistryGateTest`（2026-10-29 G2 收工轮新增） |
>
> ⇒ **本域当前权威值 = task-rpc 137 + task-api 135 = 合计 272**（**0 failures / 0 errors / 0 skipped**）。⚠️ **本表的合计 1034 与 `shared` / `message` / `like` 各行**属**其它域的快照** ⇒ **本 lane 不动**（写权外）⇒ **主线合流后需重跑一次 9 模块 `clean test` 才能得到新的合计**（本轮**不做推算**）。

**③ 三口径交叉核对（本轮新增第三口径）**
| 口径 | 怎么取 | 结果 |
|---|---|---|
| ① 日志**逐类用例行**求和 | 按 `Building <artifactId>` 分段求和每条 `-- in <类>` | **1034** |
| ② 日志**模块级汇总行** | 同段内那条**不带** `-- in` 的 `Tests run: N` | **1034** |
| ③ **surefire XML** `testsuite` 求和 | 9 个目标模块 `target/surefire-reports/TEST-*.xml` | **1034** |

⇒ ⇒ **三者逐字相等** ✔ ⇒ ⇒ 9 目标模块 ＋ `users-common` 39 = **1073** = maven 模块级合计 ✔
⚠️ **权威数只认【日志模块级汇总行】**；XML 侧只在「本轮 `clean` 之后」才有意义 ⇒ ⇒ **收口态必须是 `clean test`**（详见 `message/vs-common.md` 第二十七轮 ⑦）。

**④ 增量归因**：r29 / r31 / r32 = **1030** ⇒ **r33 = 1034** ⇒ ⇒ **+4 全部来自 A3 新增的 4 条真 Tomcat 探针**（message-api 103 ⇒ 107）⇒ ⇒ 其余 8 个模块**逐字未变** ✔

**⑤ 🔴 本域实缺陷已修（写权面内）**：`docs/DRIFT/task/rpc.md` 的标题测试数 **129 ⇒ 134**（实测 134 · 三口径相等）⇒ ⇒ `check-ledger-selfcheck.py --domain task` 由红转绿。
⚠️ 连带订正：旧注记里「**`verify` 口径 = 129 + 5 = 134**」**已过期** —— 本轮 `clean test` 里 `TaskRpcFatJarArtifactGateTest` **5 条真跑了**（日志逐类行可见）⇒ ⇒ **134 本身就是 `mvn test` 口径** ⇒ ⇒ 原值保留不改写。

---

## 开工前置回执

> **落点**：`align/g2-message-like-task` 工作树 · **基线 commit** `d877b82` · **日期** 2026-10-29
> ⚠️ **本节是「读完了」的回执** ⇒ **回执落笔时尚未获批 ⇒ 本 lane 不得开工**（`AGENTS.md` §五·3b-0 ④「经用户批准」＋「调度员不得代批准」）⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182056`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。
> ⚠️ **三份 `vs-common.md` 的本节内容逐字相同**（模板要求「会话 + 台账各一份，**同一份内容**」）

【开工前置回执 · G2 · 2026-10-29】

**① 任务范围**

本 lane 本轮**候选任务 = 修 §8.7 登记的 G2 侧 6 条出处真缺陷**（1 条 `gorm@v1.31.2` 版本写错 ＋ 4 条 `processor.go:82` ＋ 1 条 `like/common/errmsg.go` 路径不精确）⇒ 全部落在 `service/{like,task}/**` 的**注释面**（不改任何断言 ⇒ 不影响产品行为）。

**不做**：① 共享面（`service/common/**` ＋ `service/users/users-common/**`）⇒ 缺陷走反馈单转 G4；② `tools/**`（`tools/g3-anchor-check/` 归 G3）；③ `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g2.md` / `AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_cross/**` / 根 `docker-compose.yml`；④ **阶段二不碰、不预支**；⑤ **回执落笔时本轮只出回执，那 6 条一行不动**；⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182056`）⇒ **已获批开工**。

**② 已读四档（本 lane 面）**

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `SD-2026-10-23-03`（`affected`：Go 返 `int64` 数字 / RAG 出字符串）**已签字** ⇒ 对拍必红、**不得单方面改回数字**；`message/rpc.md` 的 **E59 已销案**（DB 失败原样上抛 ⇒ `UNKNOWN`＋1015 ＝ Go）；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4（7 项格式 × 4 条），`-012` 属容器层框架限制**不可修**；`common.md`/`proto.md` = `CodeError` Jackson 序列化**无断言** ＋ **台账未见变异验证** ⇒ R4 不成立 |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12（本轮 73 处出处订正）：**`DRIFT-LIKE-003` 拆三段** = A 投递链路已闭环 ＋ B 权威读语义 Java 化（`LikeAuthorityLookupService` 407 行 · 守门 11/11）＋ **C 暴露形态待拍板**；**`E60` 已销案**（`LikeTaskProgressPublisher.publish` 内三段式分流 ⇒ 真日志 `error_code=5011`）；**`L-U11` 已闭合**（`LikeRpcGrpcInterceptorWiringTest` 3/3）；**`L-U9` 定性为取证缺口**（非必相对齐项 ⇒ 不阻塞组件 ✅）；`like/api.md`：`404 body` ＝ **19** 字节（`printf '404 page not found\n' \| wc -c`）⇒ `DRIFT-LIKE-API-404-BODYLEN`（P3）＋ 同族「字节数注释漂移」三域扫描 = **零残留**；`like/proto.md`（本轮新建）：`LikeProtoContractTest` 4/4，期望值由 `generate.py` 从 Go 真源 `proto/like.proto`（sha256 `347301db…8a447e`）生成 |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`TaskRpcKafkaListenerRegistryGateTest` 3/3（listener **真进容器 registry**，非「方法存在」）；`DRIFT-TASK-RPC-008` 已落码（`:449` 池启用 ＋ 池参数 64/16/0）；`task/api.md`：`DRIFT-TASK-API-004`（charset/空白）＝ **全域共性**，不在本域单修 |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/api.md`：**`SD-2026-10-23-03`**（`affected` 类型）＋ `:28-34` 记着「`DRIFT/message/api.md` §7 曾把 `-009` 列为未修 ⇒ 已改正为签字」；`message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引**名**不同（列相同）· `-02` 直方图桶**零交集**（**两侧都不是「对的那个」**）· `-03` `errors.As` 穿透 vs `instanceof` 不穿透 · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧**多出来的** · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`）⇒ **wire 层可见**（`/pb.LikeService/*` vs `/like.LikeService/*`）⇒ 本轮 `LikeProtoContractTest` 据此排除 `package` 行比对，但**单独钉住 RAG package=`like`**；`like/api.md`：**4 条待签字候选**（`SD-2026-10-23-08` charset · `-09` 裸 503 · `SD-2026-10-24-10` 毫秒粒度 · `-11` 多暴露一个指标）⇒ **是候选不是决策**，既别当缺陷也别当签字；`like/rpc.md`：**`SD-2026-10-23-06`**（gRPC 业务码迁到自定义 trailer `rag-like-biz-code`，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出进程**，Go 是 `os.Exit(1)`）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（PG 连接最大生存时间：Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 服务存活）⇒ 另有 **4 项「不是偏离、但容易被误当成偏离」**（奖励流 `stream:task:reward` 只写不读 · `score` vs `add_score` · 任务定义是**两份独立硬编码** · 读/写路径 Redis key **本来就不一致**）；`task/api.md` ＋ `task/proto.md` = **0 字节坑位**（勿建空壳） |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `judge:200`（`like-rpc`）＝ **🟡**（`R2`/`E3`），开放项仍写 `DRIFT-LIKE-003`「6 个 4 行骨架 ⇒ UNRESOLVED」＋ `E60`「真漂移待接手」⇒ ⚠️ **这两条已被本 lane 处置** ⇒ **基线推理链对 G2 已不成立 ⇒ 须收口按新状态重读**；`judge:395-396`/`:408`/`:416` 把二者列为「**未通过** ⇒ 业务正确性未修」，`:416` 明写「③ 销 `like-rpc` 的 `DRIFT-LIKE-003` / `E60`」⇒ **收口的销号动作正是这一项**；`judge:495-496`（§十 阶段归属）＝ `D-03` 003 归 like 域（需拍板）· `D-04` E60 归 like 域；`g2.md:21-31` ＝ 11 组件矩阵（9 ✅ ＋ `like-proto`/`like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:58-63`（P0 分摊）＝ **P0-1** 本 lane 主责（like 域投递链路 ＋ 双侧活体探针**不在版本控制内** ⇒ 「真实运行＋行为等价」**未取证**）· **P0-3** `check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引/唯一约束/触发器**至今没有机器核过** · **P0-6** 全量回归 2860 条 / 0F / 0E / **4S** ⇒ 必须带 `clean`；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10 ⇒ `follow`/`like`/`hot` 连静态守门都没有；**消费方式在 10 个 proto 上全部未取证**」⇒ ⇒ 本轮 `LikeProtoContractTest` 正是 like 侧这条的落仓入口（**只覆盖 like-proto 一个**） |
| `docs/PhasedGoal.md` | `:38-52`（P0 九条） | **P0-1**「核心业务链路**真实运行**，不存在关键功能仅有接口骨架」· **P0-4**「核心失败路径有明确行为，不无声吞异常」· **P0-6**「核心业务有可执行自动化测试且断言真实业务结果」· **P0-9**「已知严重缺陷、**无法验证的关键能力和运行前提**得到明确记录」⇒ ⇒ `003-C` 与 `L-U9` 属 P0-1/P0-9 的「运行前提」面 ⇒ 台账必须写「**未取证**」而不是「已对齐」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· §10（`:941-976`） | §4 ＝ 阶段 0「项目事实收集与基线建立」（4.1 事实调查 / 4.2 核心业务链路 / 4.3 差异基线）⇒ 本 lane 台账就是 4.3 的载体；§5.3 ＝ 差异与暂缓项**每项至少 11 条**（编号/原始契约/原始行为/阶段一当前行为/具体差异/暂缓理由/影响/是否阻断/阶段二恢复方案/阶段二测试场景/证据缺口）⇒ **「不得只写『后续优化』」** ⇒ 本 lane 台账的 11 项格式即依此；§5.4 ＝ 每次改造记录 **9 条**（改前/问题/改后/理由/变更文件/测试场景与断言/执行结果/未解决/对阶段二影响）⇒ ⇒「**必须实际修改项目，而不是只输出建议**」；§10 ＝ **P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险** ⇒ ⇒ 「出处行号错」按 **P2 · B** 登记，与 `DRIFT-LIKE-003-C`（形态待拍板）分开 |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `P-MSGAPI-001`（同组件同形态**第二次**「绿测试守卫错误答案」）· `P-MSGAPI-004`（Go 对 **map 元素**的类型判据与**顶层标量字段**不是同一段代码）· `P-MSGAPI-007`（否定式断言 ＋「默认值藏在 tag 里」）· `P-MSGAPI-008`（「Bean 存在 / 注解在 / 依赖有」≠「机制生效」）；`PITFALL-MSG-R-001`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-004`（注释里的**默认行为**必须读到**消费点**）· `-006`（Boot 4 拆模块后**依赖在 ≠ 能力在，且不报错**）· `-010`（跨组件不变量**无法单侧验完** ＝ Flyway 账本）· `-013`（判「有没有等价实现」必须 grep **实现**） |
| | `like/{api,rpc}.md` | `PIT-LIKEAPI-001`（go-zero 解析层**不对称**，只学一半必然漏）· `-005`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `-006`（**同一个文件里会自相矛盾**）· `-010`/`-011`（直接单测判据函数 ＝ 永远绿的回归网 / **恒真断言 ＝ 主动制造假绿**）；`like/rpc.md`：`P-05`（「**注释里写着 Go 的行为**」≠ Go 的行为）· `P-07`（只跑 RAG 侧探针**只能证明「代码写成了这样」**）· `PIT-LIKE-RPC-013`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-014`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PITFALL-TASK-RPC-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **15 条真 PG 用例静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**，整条链路静默死掉）· `-07`（81 条测试**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · 变异测试是另一半）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享面修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**）· `open-items.md` · `message-domain-wide.md` |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `component-coverage-matrix.md` · `cross-domain-coupling.md` · `ddl-type-divergences.md` · `go-anchors.md` · `decisions.md` · `open-items.md` · `domain-common-lanes.md` | `component-coverage-matrix.md:60-70` ＝ G2 三域 11 组件的「台账 ↔ 双侧活体对拍」矩阵；`:84-87` 明确「**台账齐 ≠ 已对齐**」＋「8 个 `*-proto` 只有 §3.4 的**复制忠实** ⇒ 消费方式未验」；`cross-domain-coupling.md` ＝ G1~G4 波次表 ＋ **共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）＋ 依赖 DAG；`ddl-type-divergences.md` ＝ 类型/长度 **9 域 0 差异**，`hot` ＝ **N/A** ⇒ 不许写成「已对齐」；`go-anchors.md` ＝ 路由/事务/行锁/身份/JWT/建表 + message 域 9 个 RPC 的锚点；`decisions.md` ＝ 问题 1「**直接丢掉 Flyway**，users 域改 `SchemaInitializer` 幂等重放」＋ 问题 4「**每模块独立账本表**」 |

**③ 待批准项**

1. **候选清单的 6 条里，我域内核对后只有 2 条是真错**（另 4 条 `processor.go:82` 是**守门假阳性** ⇒ 一行都不用改）⇒ 详见下一节 ⇒ **若按原清单派 6 条工单，会有 4 条返工**。
2. **`DRIFT-LIKE-003-C`**（权威读暴露形态：Go 是**独立进程** `service/like/rpc/cmd/fact-authority/main.go`（⚠️ **2026-10-30 真实性复核（follow+task lane · 指针补全）**：原写 `cmd/fact-authority/main.go` 是**缩略写法** ⇒ Go 真源**没有顶层 `cmd/` 目录**，真实路径是 `service/like/rpc/cmd/fact-authority/main.go`（`find` 实测）⇒ **现行指针已补全**；核对日期 2026-10-30）（`LIKE_DATABASE_URL` ＋ `LIKE_FACT_AUTHORITY_LISTEN` ＋ `LIKE_FACT_AUTHORITY_TOKEN`，token ≥ 32），RAG 侧无 HTTP 端点）⇒ 落地需改 **2 处共享面**（新建模块要动 `check-wave-lane-ownership.py` 白名单 ＋ 根 `docker-compose.yml`）⇒ **G2 不可写** ⇒ 形态决策（**独立进程** vs 同进程独立 HTTP 端口）**须拍板**。
3. **`docs/PhaseGoal-judge.md:200`/`:202`**（`like-rpc` / `like-proto` 仍 🟡）＋ `:395-396`/`:408`/`:416`（把 `E60`/`003` 列为「未通过」）⇒ **该文件是 4 lane 共用唯一数据源 ⇒ G2 不写** ⇒ 须收口按新状态重读 ＋ 销号。
4. **`tools/g3-anchor-check/` 的 `PINNED` 缺 `gorm.io/driver/postgres v1.6.0` ＋ `github.com/lovoo/goka v1.1.16`** ⇒ 守门假阳性率 **52%** ⇒ 补 `PINNED` 的落点属 G3 私有目录（**可安全补**，不触发 §6.3 守门计数漂移）；但若要把该守门**搬进 `tools/difftest/` 提升为全仓守门** ⇒ **必须同时改 `docs/PhaseGoal-judge.md` §6.3**，否则立刻制造 **B14**。

**④ 等批准 ⇒ 批准后才开工**（回执落笔时 ⑤ 留空，由**调度员代填** —— 回执人**不得自批**）⇒ **已获批**：Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」（消息 id `182056`）⇒ **⑤ 已由调度员代填**（见本节末尾 ⑤ 行）。

**⑤ 批准记录：Jason · 2026-10-29 03:44 · 批准范围：本 lane 回执 ① 所列候选任务（修 §8.7 登记的 G2 侧出处真缺陷）—— §8.7 实测口径 = **2 条**（1 版本写错 + 1 路径不精确）；回执 ① 所列另 4 条 `processor.go:82` 经调度员合并树实测为**守门假阳性**（真身是 goka 的 `processor.go:82`，非空且正是注释声称内容）⇒ 不改**　← 由**调度员代填**（依据：Jason 于 2026-10-29 03:44 在**本 lane 会话内**明示「继续」· 消息 id `182056`；**回执人不得自批**· **代填 ≠ 代批准**）

---

### ⚠️ 候选任务界定与我域内实情不符（调度员点名要写的地方）

| # | 候选清单条目 | 清单定性 | **我域内实测（逐条回真源核）** | 判定 |
|---|---|---|---|---|
| 1 | `gorm@v1.31.2` 版本写错<br>`service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeDomainFactSchemaParityTest.java:38` | 真错 —— 引到**另一份内容不同的副本** | 引用原文 = `{@code gorm@v1.31.2/schema/field.go:330-341}`（给 int32 算 `Size = 32`）；真源 `go.mod:28` pin 的是 **`gorm.io/gorm v1.31.1`** ⇒ **版本号确实是错的**。但实测 `gorm.io/gorm@v1.31.1` = **1004 行** / `v1.31.2` = **1011 行**，而**两版的 `:330-341` 内容逐字相同**（`case reflect.Int32, reflect.Uint32: field.Size = 32`）⇒ ⇒ **行号不用动，只需改版本号**；实际风险是「引用了**非真源副本** ⇒ 未来 v1.31.1 演进时这行会漂」 | ✅ **真错（定性需精确化）** —— 修法 = **只改版本号，不动行号** |
| 2 | **4 条** `processor.go:82` 指向空行<br>`TaskArticleCountConsumer.java:91` · `TaskCountStore.java:32` · `TaskUserCountConsumer.java:91` · `TaskSinkConsumeTest.java:312`（全在 task-rpc） | 真错 —— 落在 **go-redis v9.20.0 `push/processor.go`** 的空行上 | 4 条引用的原文分别是 `goka processor.go:82` 与 `goka@v1.1.16 processor.go:82` ⇒ **指向的是 goka，不是 go-redis**。实测真身 `~/go/pkg/mod/github.com/lovoo/goka@v1.1.16/processor.go`（**1072 行**）的 `:82` ＝ `WithStorageBuilder(storage.DefaultBuilder(DefaultProcessorStoragePath(gg.Group()))),` ⇒ **非空，且正是注释声称的「按 group 名分 LevelDB 目录」**。而 `go-redis@v9.20.0/push/processor.go:82` 确是空行 —— **但那是另一个文件**（go-redis 的 push 订阅处理器，与 goka 无关） | 🔴 **假阳性 × 4** —— **真身引用是对的，一行都不用改**；根因 ＝ 守门 `PINNED` 缺 `github.com/lovoo/goka v1.1.16` ⇒ 回退裸文件名匹配 ⇒ **撞上 go-redis 的同名 `processor.go`**（与 `docs/CLOSEOUT-2026-10-29.md` §8.7 里 `postgres.go` / `goka` 两簇**同款根因**） |
| 3 | `like/common/errmsg.go` 路径不精确<br>`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeTaskProgressPublisher.java:141` | 真错 —— 真身是 `service/like/common/errmsg/errmsg.go`（少写了 `errmsg/`） | 引用原文 = `Go like/common/errmsg.go:51 ErrorRedisSelect = 5011`；真身 `Sea/service/like/common/errmsg/errmsg.go:51` ＝ `ErrorRedisSelect = 5011` ⇒ **行号正确、路径少一段目录**；同域另 4 处（`LikeErrorMessages.java:9` · `LikeErrorCode.java:4` · `LikeGrpcError.java:10` · `CodeError.java:7`）**都已写对完整路径** ⇒ **域内自相矛盾** | ✅ **真错** —— 修法 = **补 `errmsg/` 段，行号不动** |

⇒ **净结论**：G2 侧 6 条候选里**只有 2 条是真错**（① 版本号 · ③ 路径）⇒ **另 4 条经核对是守门假阳性 ⇒ 一行都不用改** ⇒ ⇒ **建议：候选清单收敛为 2 条**；并把「**补 `PINNED`（`gorm.io/driver/postgres v1.6.0` ＋ `github.com/lovoo/goka v1.1.16`）**」作为**独立的一件事**处理（落点属 G3 私有目录 `tools/g3-anchor-check/`）。

⚠️ **这 4 条的假阳性判定本身也留痕**：它们与本 lane 上一轮在 `docs/DRIFT/like/rpc.md` §12.2 坐实的「**多候选任一通过 ⇒ 假绿**」是**同一个根因的两面** —— 上次是「**真身不满足、干扰项满足 ⇒ 判过**」（假绿），这次是「**真身满足、干扰项不满足 ⇒ 报错**」（假错）⇒ ⇒ **同一个候选解析缺陷，两个方向都会出错** ⇒ ⇒ **候选解析必须按域消歧 ＋ 命中后必须回真源读那一行**（该结论已由调度员转述进 `docs/CLOSEOUT-2026-10-29.md` §8.3；本回执只做**我域内的逐条核对**）。

---

# 2026-10-29 · R2 · task 域**失败路径矩阵**落仓（`TaskApiFailurePathMatrixTest` · 33 用例）＋ **变异验证**

> **归属**：R2 lane（`align/r2-like-task` 工作树）· **核对日期** 2026-10-29
> **写权**：✅ `service/task/**` ＋ `docs/DRIFT/task/**`（共享层 `service/common/**` **只读**）
> **证据强度**：**R3（源码级逐条对照 Go 出处）＋ E4（真 Tomcat 容器里的真 HTTP 往返）**
> ⚠️ **本节不是「双侧活体」** —— 期望值取自 **Go 出处**（逐条列在下表），**不是**同一时刻两侧进程并跑的产物
> ⇒ ⇒ **不得**把本节读成 L4；本域的双侧活体是 `docs/DRIFT/task/api.md` §二·五 的 **44 条**（2026-10-24，本轮未重跑）。

## ① 为什么做（缺口定位）

| 项 | 事实 |
|---|---|
| 端点数 | **1 个** —— Go `service/task/api/internal/handler/routes.go:15-25` 只有 `POST /taskcenter/v1/task/get`（**无 `WithJwt`**）⇒ ⇒ **task 域的行为面几乎全是失败路径** |
| 既有守门 | `task-api` 有 **135** 条测试（`api.md:30`）<br>🆕 **2026-10-29 对齐（W-B lane）**：**原值保留不改写** —— 原写的 **102** 是 **2026-10-29 R2 之前**的值；R2 新增 `TaskApiFailurePathMatrixTest` **33** 条 ⇒ **102 + 33 = 135** ⇒ **2026-10-29 实测 = 135**（`mvn -o -B -pl service/task/task-api test` ⇒ BUILD SUCCESS · 10 个测试类 · 135 / 0F / 0E / 0S）⇒ 但**没有一条**按「端点 × 失败维度」铺开的矩阵 ⇒ 失败路径是**散点**覆盖，**没有一处能一眼看出「这个端点的失败面覆盖到哪了」** |
| 既有双侧对拍 | `api.md` §二·五 的 44 条双侧活体在 `tools/difftest/`（**仓外脚本**）⇒ ⇒ **不是版本控制内的回归网** ⇒ 「跑绿与否」取决于人记不记得跑 |
| **本轮落点** | 把失败路径**固化进模块测试**（`service/task/task-api/src/test/java/com/rag/task/task/api/TaskApiFailurePathMatrixTest.java`）⇒ ⇒ `mvn test` 就会跑 ⇒ **回归网进版本控制** |

## ② 矩阵（**1 端点 × 6 失败维度 · 33 用例**）

| 维度 | 用例数 | 断言的真实值 | Go 出处（**逐条读消费点**） |
|---|---|---|---|
| **① 参数缺失 / 非法** | **9** | 400 + `text/plain` + `user_id invalid\n` / `id must be a string or integer\n`（**且 RPC 完全不被调用**） | `service/task/api/internal/types/types.go:16-50`（`UnmarshalJSON` 两段判定 `:18-21` / `:38-50`）· `gettaskhandler.go:21-24`（空 body ⇒ `io.EOF`、畸形 JSON ⇒ Decode 失败 ⇒ **两侧文案不同 ⇒ 只断言状态码**） |
| **② 边界值** | **8** | 400 + `user_id invalid\n`（`0` / `-1` / `1.5` / `1.0` / 超 int64 上下界；数字与**字符串**两种写法各算一条） | `types.go:45-47`（`uid <= 0` 一律拒）· `:45-46`（`ParseInt` 失败 / 溢出） |
| **③ body 形态** | **2**（含在①内） | 同上：空 body 400、畸形 JSON 400 | `gettaskhandler.go:19`（裸 `json.NewDecoder(r.Body)` ⇒ **不校验 Content-Type**）· `:21-24` |
| **④ 下游 gRPC 失败** | **9** | **真实 HTTP 状态码**：`UNAUTHENTICATED`⇒401 · `ALREADY_EXISTS`⇒409 · `RESOURCE_EXHAUSTED`⇒429 · `INTERNAL`⇒500 · `DEADLINE_EXCEEDED`⇒504 等（`go-zero@v1.10.2/rest/internal/errcode/grpc.go:12-42` 的 **15 分支映射**逐条）＋ **grpc-go 的错误原文** `rpc error: code = X desc = Y` | `errcode/grpc.go:12-42` · `httpx/responses.go:145-159`（**本域未注册 `httpx.SetErrorHandler`** ⇒ 走默认分支 ⇒ 非 gRPC 错误 ⇒ **400** 不是 500） |
| **⑤ 路由层** | **2** | 未注册路径 ⇒ **404 + 有 body** 的 `"404 page not found\n"` + `text/plain` + `nosniff`；方法不对 ⇒ **405 + 裸状态码**（**无 body、无 Content-Type**） | `go-zero@v1.10.2/rest/patrouter.go:73` / `:79-82` |
| **⑥ 鉴权** | **2** | **零鉴权**：不带 `Authorization` 头 ⇒ **200**；带一个**乱写的** `Authorization` 头 ⇒ **200**（Go 根本不读这个头） | `routes.go:15-25`（**没有 `WithJwt`**） |
| **＋ 业务语义** | **2** | 「查无任务」⇒ **200 + `{"tasks":[]}`**（Go **没有 not-found 这个概念**）；`uid = Long.MAX_VALUE` ⇒ **放行**且 RPC 收到同一个值 | `gettaskhandler.go`（成功路径恒 200）· `types.go:45`（`ParseInt` 上界之内） |
| **＋ 端口段守门** | **1** | 真容器必须绑到本域专用端口段 `21800-21899`（`RANDOM_PORT` 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段） | —（本仓约定，见 `support/RealTomcatPort.java`） |

⇒ **合计 33 条**（9 + 8 + 9 + 2 + 2 + 2 + 1）。

## ③ 实跑结果（**真实命令 + 真实输出** · 2026-10-29）

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r2-like-task"
mvn -o -B -pl service/task/task-api -am -DargLine=-Xmx2g \
    -Dtest=TaskApiFailurePathMatrixTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.693 s -- in com.rag.task.task.api.TaskApiFailurePathMatrixTest
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

⇒ **EXIT=0** · reactor：`task-api SUCCESS [19.386 s]` · `Total time: 24.782 s`。

### ③·一 **模块级回归**（新增 33 条后，**task-api 全量**）

```bash
mvn -o -B -pl service/task/task-api -am -DargLine=-Xmx2g -DfailIfNoTests=false test
```

```
[INFO] Tests run: 135, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

⇒ **EXIT=0** · **135 = 原 102 + 本轮 33** ⇒ ⇒ **本轮新增没有打破任何既有守门**。
⇒ `docs/DRIFT/task/api.md:30` 的条数已同步改成 **135**（原文 102 保留在括号里作历史口径）。

## ④ 变异验证（AGENTS.md §3.3.3 · **证明矩阵不是假绿**）

脚本落仓：`service/task/task-api/src/test/probe/failure-path-matrix-mutation/run.sh`（**仓内** ⇒ 可复算；落点沿用本仓既有惯例 `service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh`）

```bash
bash service/task/task-api/src/test/probe/failure-path-matrix-mutation/run.sh
```

| 变异 | 手法 | 实测结果 |
|---|---|---|
| **基线** | 未变异 | `Tests run: 33, Failures: 0` · `BUILD SUCCESS` ⇒ **exit 0** |
| **T-M1** | 改 RAG 的 `GetTaskReq.USER_ID_INVALID` 文案（`"user_id invalid"` ⇒ `"user_id invalid MUTATED"`） | 🔴 **精确转红**：`Tests run: 33, Failures: 12, Errors: 0` · `BUILD FAILURE` · exit 1<br>红在 `expected: <user_id invalid> but was: <user_id invalid MUTATED>` |
| **T-M2** | 改 RAG 的 `X-Content-Type-Options` 值（`"nosniff"` ⇒ `"sniff"`） | 🔴 **精确转红**：`Tests run: 33, Failures: 18, Errors: 0` · `BUILD FAILURE` · exit 1<br>红在 `Go 的 http.Error 写死 X-Content-Type-Options: nosniff ==> expected: <nosniff> but was: <sniff>` |
| **还原** | 复制回备份 | `Tests run: 33, Failures: 0` · `BUILD SUCCESS` ⇒ **exit 0** |

⇒ **脚本最终 EXIT=0**（两条变异都精确转红 + 还原后转绿）⇒ ⇒ **「文案」与「响应头」这两类断言都真有牙齿**。
⚠️ 还原一律用「文件备份 + 复制回来」，**不用 `git checkout --`**（本 lane 改动未提交 ⇒ `checkout` 会一起回滚）。

## ⑤ 🟡 `DRIFT-TASK-API-004` 的**复验**（**仍开放 · 容器层不可修 · 不在本域单独修**）

> 🆕 **2026-10-29 对齐（W-B lane · 台账对齐）** —— 本标题原**无状态标记**（台账留痕，保留不改写）⇒ **补上 `🟡`** ⇒ 与 [`api.md:510`](api.md) 的 `### 🟡 DRIFT-TASK-API-004` **收敛到同一状态：🟡 未修（全域共性 · 容器层不可修 · 不在本域单独修）** ⇒ **核对日期 2026-10-29**。**依据**：`docs/DRIFT/task/_README.md:184`（「**五域共性** · ❌ 不在本域单独修」） ＋ `docs/DRIFT/task/rpc.md:1293`（同款处置）＋ `api.md:27` 的 §结论表。

| 项 | 本轮实测 |
|---|---|
| 现象 | Go `text/plain; charset=utf-8`（**分号后有空格**） vs RAG `text/plain;charset=utf-8`（**无空格**） |
| 本轮处理 | 矩阵**断言当前实际值**（`text/plain;charset=utf-8`）⇒ **不把已登记偏差变成永久假红**；断言旁注明指向 `DRIFT-TASK-API-004` / `FB-G2-04` |
| 结论 | **仍开放**。根因 = **Tomcat 的 `Response.setContentType(String)` 把 Content-Type 解析成 MediaType 再序列化** ⇒ **吃掉空格**；`setHeader("Content-Type", …)` 在 Tomcat 内部也被转手调成 `setContentType` ⇒ ⇒ **没有任何写法能产出带空格的 Content-Type** ⇒ ⇒ **容器层限制 ⇒ 不可修** ⇒ 与 like/message 等域**同款共性** ⇒ **不在本域单独修**（单改会造成本域与其它域不一致） |
| 复验证据 | 本轮矩阵 **33 条全部断言了 nosniff 与 Content-Type 媒体类型**（T-M2 变异 ⇒ **18 条转红** ⇒ 说明这层断言**确实在生效**） |

## ⑥ 边界（**本矩阵不覆盖什么** —— 必须一起读）

1. **不是双侧活体** ⇒ 期望值是「Go 出处的字面值」，不是「同一时刻两侧进程的产物」⇒ ⇒ **不写 L4**（双侧活体见 `api.md` §二·五 的 44 条，2026-10-24）。
2. **下游 gRPC 全部是 `@MockitoBean` 桩** ⇒ 维度④ 验的是「**RAG 侧把 gRPC 状态码映射成 HTTP 状态码**」这条**映射面**，**不验**真实下游的可用性 ⇒ ⇒ 与「双侧活体」不冲突、也不互相替代。
3. **空 body / 畸形 JSON 两条只断言状态码**（400），**不比对文案** ⇒ 理由 = Go `encoding/json` 与 Jackson 的解析错误措辞不同（`api.md` §二·五 已登记为**全域共性**）⇒ **不是漏测，是有意不锁**。
4. **路径变体**（`/task/get` vs `/taskcenter/v1/task/get` 等）**不在本矩阵** ⇒ 由本域既有 `TaskApiPathNormalizationRealTomcatTest`（真 Tomcat）＋ `TaskApiPathNormalizationControlTest`（A/B 对照组）守。
5. **本矩阵只锁 HTTP 面的失败路径** ⇒ **不覆盖** task-rpc 侧（Kafka / Redis / PG 断连等）⇒ 那部分见 [`rpc.md`](rpc.md) 与 `docs/DRIFT/task/vs-common.md` §4·十二 之后的各轮。

<!-- @new 2026-10-29 · `align/r3-ddl-message-like-task` · RoundPlan `docs/RoundPlan/Round-3.md` **T-3.1 的 G2 侧（`task` 域）** · 基线 = `do-my-own-gh` @ `d10227c5` -->

## DDL 逐域机器核（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G2 lane · `task` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，但结论只落在 `docs/PhaseGoal-judge.md` §七 ⇒ **域级证据链断了一节** ⇒ 本节把 `task` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不修任何差异**（RoundPlan T-3.1）。
> ⚠️ **本节零改动 `service/**`** —— `service/task/**` 由别的 lane 在改 ⇒ 变异测试走**逐字节相同的临时副本**（见 §T.5）⇒ 提交后 `git diff --stat -- service/` = **0 行**。
> ⚠️ **`task` 域不是 N/A** —— N/A 的是 **`hot`**（两侧无数据库）⇒ `task` 域**两侧都有真库**（Go 2 张表 / RAG 2 张表）。
> ⚠️ **`task` 域没有 `-common` 模块**（Go 侧同样没有，**不是遗漏**）⇒ 本节**不含**任何「域 ↔ `service/common`」的结论（那属于本文件其余各节）。

### T.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:24:0x–14:24:51 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task` · `align/r3-ddl-message-like-task`<br>⚠️ **实跑基点 = `do-my-own-gh` @ `d10227c5`**（工作树先 `git reset --hard do-my-own-gh` 对齐主线，再跑）⇒ **实跑结论与提交基点同源** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本**只复制**副本，从不写入 |
| 容器 / 库 | `ddl-task-pg`（`postgres:16-alpine` · 端口 55568）· Go 库 `go_task_db` · RAG 库 `rag_task_db` |
| Go 侧 schema 来源 | ⚠️ **本域与其它域不同**：真源**没有** `model.InitDB` ⇒ 它是在 `service/task/rpc/internal/svc/service_context.go:47` **直接**调 `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})` ⇒ 探针走 **`automigrate` 档**，**复刻那同一行调用**（同一份 struct tag + 同一个 gorm 版本）⇒ 建出来的表就是真源生产那张 |
| Go struct 出处 | `service/task/rpc/internal/Model/taskModel.go:28-37`（`TaskProgress`）· `:39-41`（`TableName()` → `task_progress`）· `:43-47`（`UserLikeCount`）· `:49-51`（`TableName()` → `user_like_count`）<br>⚠️ **Model 包的路径是 `internal/Model`（大写 M）** —— 与其它域的 `internal/model` 不同 |
| RAG 侧 schema 来源 | 仓内 `service/task/task-rpc/src/main/resources/db/migration/`（**Flyway**）**2 份**：`V1__create_task_progress.sql` · `V2__create_user_like_count.sql` |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 差异数是**原始值**，没有被白名单压过 |
| 两侧表集合 | Go `go_task_db` = {`task_progress`, `user_like_count`} · RAG `rag_task_db` = **同 2 张** ⇒ 逐条一致 |
| **基线选择** | **本域无歧义** —— Go 侧**没有** `internal/model/migrations/` 目录 ⇒ **不存在「夹具 vs 生产」的两档问题**（对比 `like` 域，见 [`docs/DRIFT/like/vs-common.md`](../like/vs-common.md) 同名节） |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
bash tools/difftest/ddl-parity.sh task
```

⇒ **EXIT=0** —— 末行 `== 实质差异合计 0 处 ⇒ 退出码 0 ==`；runner 行 `=== 对拍退出码: 0 ===`
⇒ Go 探针打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 `V1__create_task_progress.sql` → `V2__create_user_like_count.sql`

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain task --indexes --check-source
```

⇒ **EXIT=0**（**0 处**）—— **与 ① 同数同因** ⇒ 两条路径互为复算（① 是「建库 + 对拍」，② 是「只对拍已建好的库」）

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain task --print-rag-sql
```

⇒ **EXIT=0**，输出 **2 行**，与 ① 实际执行的 2 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**

### T.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（`--check-source`） | 2 张表 | ✅ **0 差异** —— 仓里的 `.sql` 在**干净临时库** `rag_task_db_srccheck` 上原样重放后，与活库**逐项一致** | 0 |
| 2 | **列类型 / 长度** | 11 列 ↔ 11 列（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「逐字相同 11 列」 | 0 |
| 3 | **可空性（NOT NULL）** | 11 列 | ✅ **0 差异** —— 脚本原话「两侧完全一致 ✓」 | 0 |
| 4 | **DEFAULT** | 11 列 | ✅ **0 差异** —— 脚本原话「两侧完全一致 ✓」 | 0 |
| 5 | **索引**（`pg_indexes`） | Go 2 / RAG 2 | ✅ **0 处实质差异** —— 脚本原话「两侧索引定义逐条一致 ✓」（2 处 = **命名形态差** ⇒ 语义等价，不计失败） | 0 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go 2 / RAG 2 | ✅ **0 处实质差异** —— 脚本原话「逐条一致 0 行 ✓」＋「两侧约束集合完全一致 ✓」（2 处 = **命名形态差** ⇒ 语义等价，不计失败） | 0 |
| 7 | **触发器**（`pg_trigger`） | 两侧各 0 | ⚪ **两侧都没有触发器 ⇒ 本维度「无对象可比」** —— ⚠️ **这不是「触发器已对齐」** | 0 |
| 8 | **触发器形式 / 触发器函数体** | 两侧各 0 | ⚪ **同上 ⇒ 无对象可比** —— 脚本原话「两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」）」 | 0 |
| | **合计** | | | **0** |

> ⚠️ **读法**：本域 DDL 的**行为层**（类型 / 长度 / 可空性 / DEFAULT / 索引 / 唯一约束）在 **8 个维度里 6 个为 0** ⇒ **本域无 DDL 行为漂移** ⇒ **残留 0 处 ⇒ 无需定级**。
> ⚠️ **维度 7/8 不是「通过」而是「无对象」** —— Go 侧 `task` 域**根本没有触发器**（真源 `service/task/**` 全域无 `CREATE TRIGGER`）⇒ 本域的触发器语义**没有可核对象**。

### T.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 11 列逐字相同（0 处差异）**

| 表 | 列 | Go 侧类型 | RAG 侧类型 | 判定 |
|---|---|---|---|---|
| `task_progress` | `user_id` / `task_id` / `progress` / `target` | `bigint` | `bigint` | ✅ 逐字相同（Go `int64` ⇒ GORM 无 size tag ⇒ 取 `bigint`，`taskModel.go:29,30,32,33`） |
| `task_progress` | `status` | `text` | `text` | ✅ 逐字相同（Go `string` 无 size tag ⇒ `text`，`taskModel.go:31`） |
| `task_progress` | `done_at` / `created_at` / `updated_at` | `timestamp with time zone` | 同左 | ✅ 逐字相同（`time.Time` / `*time.Time` ⇒ PG 上 `timestamptz`） |
| `user_like_count` | `user_id` | `text` | `text` | ✅ 逐字相同 —— ⚠️ **本表 `user_id` 是 `TEXT`、`task_progress.user_id` 是 `BIGINT`** ⇒ 这是 Go 的现状（`TaskProgress.UserID int64` vs `UserLikeCount.UserID string`，`taskModel.go:29` vs `:44`）⇒ **两侧都保持原样，未「顺手统一」** |
| `user_like_count` | `like_count` | `bigint` | `bigint` | ✅ 逐字相同（`int64`） |
| `user_like_count` | `updated_at` | `timestamp with time zone` | 同左 | ✅ 逐字相同 |

⇒ 脚本汇总行：`类型/长度差异 0 处：` + `逐字相同 11 列。`

**② 可空性（NOT NULL）—— 0 处差异** ⇒ 脚本原话 `（两侧完全一致 ✓）`
⇒ 抽样：`task_progress.user_id` / `task_id` 两侧均 `NOT NULL`（Go tag `primary_key`）· `task_progress.status` / `progress` / `target` / `done_at` 两侧**均可空**（Go tag 里**没有一个字段带 `not null`** ⇒ 与 `V1__create_task_progress.sql:11` 的注释「没有一个字段带 `not null` tag ⇒ 除主键外全部可空」一致）· `user_like_count.user_id` 两侧均 `NOT NULL`。

**③ DEFAULT —— 0 处差异** ⇒ 脚本原话 `（两侧完全一致 ✓）`
⇒ 抽样：`task_progress` 8 列与 `user_like_count` 3 列**两侧 DEFAULT 全为 `<NULL>`** ⇒ ⇒ 本域**没有**「RAG 多 `CURRENT_TIMESTAMP`」那类残差（与 `message` 域 6 处 / `users` 域 8 处**不同族**）。

**④ 源码 ↔ 活库 —— 0 差异（本轮新增维度）**

- 目录扫描得到 **2 份**迁移脚本 ⇒ **清单由目录扫描产生**。
- 在**干净临时库** `rag_task_db_srccheck` 上**原样重放** ⇒ 与活库逐项对比：**表集合 2 ↔ 2** · **列集合 0 差异** · **列属性 0 差异** · **可空性 / DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `task` 域为零**。

**⑤ 索引 —— 2 ↔ 2，逐条一致（0 处实质差异）**

| 语义等价配对 | 唯一性 | 列 |
|---|---|---|
| `task_progress_pkey` ≡ `pk_task_progress` | unique | `(user_id, task_id)` |
| `user_like_count_pkey` ≡ `pk_user_like_count` | unique | `(user_id)` |

⇒ 脚本原话：「（两侧索引定义逐条一致 ✓）」＋「语义等价（仅命名形态差，**不计失败**）2 处」。
⇒ **语义影响为零**：PG 层面的唯一性判定、`23505` 报错、`ON CONFLICT` 仲裁完全相同；差别只是 `pg_indexes.indexname` / `pg_constraint.conname` 的**字符串**（Go 侧是 GORM 的 `TableName()_pkey`，RAG 侧迁移脚本写的是 `pk_<表名>`）。

**⑥ 约束 —— 2 ↔ 2，集合完全一致（0 处实质差异）**

| 语义等价配对 | contype | 定义 |
|---|---|---|
| `task_progress.task_progress_pkey` ≡ `task_progress.pk_task_progress` | p | `PRIMARY KEY (user_id, task_id)` |
| `user_like_count.user_like_count_pkey` ≡ `user_like_count.pk_user_like_count` | p | `PRIMARY KEY (user_id)` |

⇒ 脚本原话：「逐条一致 0 行 ✓」＋「（两侧约束集合完全一致 ✓）」。
⇒ ⚠️ 脚本这一行「**逐条一致 0 行**」是**因为两行的 `conname` 字符串不同**（都落进「语义等价」分支）⇒ **不是「一个约束都没比」** —— 两个约束**都比过了**，只是名字形态不同。

### T.4 残留 —— **0 处** ⇒ 无需定级

| 项 | 结论 |
|---|---|
| 残留条数 | **0 处** |
| 定级 | **无** —— `twoStage.md` §十 的 **A / B / C / D** 四个阶段归属**只对「存在的差异」定级** ⇒ 本域**没有任何差异需要定级** ⇒ **不硬套一个「D 类」来凑数** |
| 已签字项复核 | 本域 `docs/Intentional_Deviations/task/` 下有 3 份档（`api.md` / `proto.md` / `rpc.md`）⇒ 逐条核对：**其中没有一条是 DDL / schema 层面的偏离** ⇒ ⇒ **本域的 0 处残留与签字档无交叉**，不涉及「已签字项要不要重复质疑」的问题 |
| 与主线记录的交叉 | 主线 `docs/DRIFT/global/lanes/L3-ddl.md` §8.3 把 `task` 记为**「0 差异」**（该表 13~16 行是 `article`/`follow`/`hot`/`users`，`task` 未单列差异行）⇒ 本轮**独立实跑复现了同一结论**，且**多核了 `--check-source` 那一层**（主线未在共享面登记本域的源码↔活库结果） |

### T.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/task/**` 由**别的 lane 在改** ⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**路线：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ 脚本内的 `REPO_ROOT` 与仓内布局一致 ⇒ 它读到的**仍是仓里那份 `.sql` 的同一份字节**，只是不在仓里写），**逐字节校验 sha256 相同**，再在**副本**上做变异。
> ⚠️ **本域是三域里基线最干净的一个（0 差异）** ⇒ 变异判据**最硬**：**总退出码必须 0 → 1 → 0**，而不只是「差异数 +1」。

**变异：`task_progress.progress` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 / 动作 | 结果 |
|---|---|---|
| 建临时树 | `cp` 脚本 + `cp` 该域 **2 份** `.sql` ⇒ `/tmp/g2-mut/task/{tools/difftest,service/task/task-rpc/src/main/resources/db/migration}` | sha256 **2/2 逐字节 SAME**（见下表） |
| 基线 | `python3 /tmp/g2-mut/task/tools/difftest/check-ddl-static.py --domain task --indexes --check-source` | **EXIT=0**（**0** 处）· 源码↔活库 = `✅ 重放结果与活库逐项一致` |
| 变异 | 副本 `V1__create_task_progress.sql:31`：`progress    BIGINT,` → `progress    INTEGER,`（改**一个列的类型**，命中目标行 **1** 处） | 变异前后该行已回显核对（`before`/`after` 两行均打印在运行日志里） |
| 变异后重跑 | 同上命令 | **EXIT=1**（**1** 处）· 源码↔活库 **转红**：<br>`🔴 列属性不同：task_progress.progress  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 删掉临时树里的 `service/` 后**重新拷贝**原件 | sha256 **2/2 逐字节 SAME** |
| 还原后重跑 | 同上命令 | **EXIT=0**（**0** 处 · **回到基线**）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **2 份全部 SAME**（sha256 原值见下）⇒ 逐字节回到变异前 |
| **收尾** | `rm -rf` 整棵临时树 | `exists=no` ⇒ **工作树与 `/tmp` 均无临时副本残留** |

**sha256 对照（变异前 = 还原后，两次完全相同）**

| 文件 | sha256（副本 = 原件，两次一致） |
|---|---|
| `V1__create_task_progress.sql` | `fde86b7f1fc27c6c3109610a9007f3fad7436557659350f0f0d5723b6a65d881` |
| `V2__create_user_like_count.sql` | `0dbf8b8bf428278ac8318101595bdd2d340013e0deb5df172ed3a9c8eb5effd8` |
| `check-ddl-static.py`（脚本副本） | `cc87da8dfde81eb11dd160150299369e6012d3eb8d815e44ede02a5c517a6ebb` |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`task_progress.progress`），且**总退出码 0 → 1 → 0** ⇒ 守门**不是假绿**；还原后**精确回到基线** ⇒ 无残留。
⇒ 本域是三域里**唯一能直接用「总退出码」判变异**的（`message` / `like` 基线本身是 EXIT=1 ⇒ 只能看差异数变化）。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
git status --short          # ⇒ 空
git diff --stat -- service/ # ⇒ 0 行
```

### T.6 人工补查（脚本**不查**的维度 · **人工闭合，非脚本原生结论**）

> ⚠️ `tools/difftest/check-ddl-static.py` 的「约束」维度**只覆盖主键 / 唯一类**；**CHECK（`contype='c'`）、外键（`contype='f'`）、分区（`relkind='p'`）、存储过程 / 自定义函数（`pg_proc`）** 都需要**另查** ⇒ 下表是**本轮人工补查**的结果，**明确标注为人工闭合，不冒充脚本原生结论**。

| 维度 | Go 侧 `go_task_db` | RAG 侧 `rag_task_db` | 人工闭合结论 |
|---|---|---|---|
| **CHECK**（`pg_constraint` where `contype='c'`） | 2 行，**但 `conrelid` 为 0** ⇒ PG **内建域**约束（`cardinal_number_domain_check` / `yes_or_no_check`），**不是用户表约束** | **同样 2 行、逐字相同** | ✅ **用户表 CHECK：两侧各 0 ⇒ 无对象可比**（内建域约束两侧**逐字一致**，不构成差异） |
| **外键**（`contype='f'`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无外键** |
| **分区**（`pg_class.relkind='p'` / `relispartition`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无分区表** |
| **存储过程 / 自定义函数**（`pg_proc`，排除扩展与 `pg_catalog`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无用户自定义例程** |
| **触发器**（`pg_trigger`，排除 internal） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无对象可比**（与 §T.2 维度 7/8 同源） |

⇒ **「无对象可比」≠「已对齐」**：以上 5 个维度**两侧都没有可比对象**，本轮**未核**（无对象可核），**不写成「已对齐」**。
⇒ ⚠️ 与 `like` 域**同款事实**（RAG 迁移脚本 `V1:43-46` 与 `V2:27-28` 都明写「不额外加 CHECK 约束 / 不加索引」）⇒ ⇒ **两侧都不加 CHECK** 是**有意设计**，不是漏写。

### T.7 证据强度 · 效力边界 · 未能核到的部分

| 项 | 结论 |
|---|---|
| 证据强度 | **E4（真 PG 双侧活体）** —— 两侧各建一个真库（Go 侧跑**真源自己的那一行** `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`，RAG 侧按序执行 `V1`~`V2`），逐条 dump `pg_indexes` / `pg_constraint` / `pg_trigger` / `information_schema.columns`；**外加 `--check-source` 的「源码重放 ↔ 活库」真库对照** |
| 变异验证 | ✅ **已做**（§T.5）⇒ 守门有牙齿，**不是假绿**（本域是三域里**唯一**能用「总退出码 0→1→0」直接判变异的） |
| **效力边界 ①** | 本节结论只覆盖 **`task` 域 2 张表**；**`task` 域没有 `-common` 模块**（Go 侧同样没有）⇒ 本节**不含**任何共享层结论 |
| **效力边界 ②** | 结论覆盖的是**当前迁移脚本 + 当前 Go struct tag** ⇒ 改了任一侧即**失效** ⇒ 需重跑 `ddl-parity.sh task` |
| **效力边界 ③** | **`--check-source` 的「源码 ↔ 活库」维度与 Go 侧无关** ⇒ 它只保证「仓里的 `.sql` 与活库没分叉」，**不代表**活库与 Go 一致（那是前 6 个维度的事） |
| **效力边界 ④** | ⚠️ **Go 侧 `task_def` 表不在本节范围** —— 真源**没有** `task_def` 表（它是 `model/taskModel.go:22-25` 的**代码常量** `AllTaskDefs`）⇒ ⇒ **本节「2 张表」两侧一致**正是正确结果，**不是漏比** |
| **未能核到 ①** | **触发器维度（维度 7/8）**：两侧各 0 个触发器 ⇒ **无对象可比 ⇒ 本维度未核**（不是「已对齐」） |
| **未能核到 ②** | **`--check-source` 不检查 CHECK / 外键 / 分区 / 存储过程** ⇒ §T.6 是**人工补查**（`contype in ('c','f')` / `relkind='p'` / `pg_proc` 三条手工 SQL）⇒ **非脚本原生结论**，但**已人工闭合** |
| **未能核到 ③** | **未做「同一次写入两侧落库后读回」的活体数据对拍**（本节是 **schema 层**对照）⇒ 数据层的等价性另由 [`docs/DRIFT/task/api.md`](api.md) §二·五 的 44 条双侧活体承担 ⇒ **本节不重复声称数据层已对齐** |
| **未能核到 ④** | 本节**未核** RAG 侧 Flyway 的**运行时**行为（账本表 `flyway_schema_history_task` 的落库、多进程同库共存）⇒ 那属 §6.3 问题 4 的**共享层**范围，本节**不越界**、也**不重复声称**已闭环 |

### T.8 本节边界（本节**不**声称什么）

1. **不修任何差异** —— 本轮只**登记**（RoundPlan T-3.1）；本域本轮**无差异可登记**。
2. **不声称触发器已对齐** —— 两侧各 0 ⇒ 「无对象可比」。
3. **不声称数据层已对齐** —— 本节是 **schema 层**。
4. **不声称共享层已闭环** —— Flyway 账本隔离属 `global` 收口域（§6.3 问题 4）⇒ **本 lane 不写、不重复声称**。
5. **不写共享面** —— `docs/DRIFT/_cross/**` · `docs/DRIFT/global/**` · `docs/PhaseGoal-judge.md` · `docs/RoundPlan/**` **本 lane 一律不写**（合并后由**合并收口执行者**统一更新）。

---

## 开工前置回执（2026-10-30 轮 · 派生层 `wave` 列落地后的第一轮）

> **落点**：`align/g2-message-like-task` 工作树 · **基线 commit** `bdac3607`（= `do-my-own-gh` @ `bdac3607` · 施工单加 `wave` 列那次）· **日期** 2026-10-30
> ⚠️ **三份 `vs-common.md` 的本节内容逐字相同**（模板要求「会话 + 台账各一份，**同一份内容**」）
> ⚠️ **本节是「读完了」的回执** ⇒ **回执落笔时尚未获批 ⇒ 本 lane 不得开工**（`AGENTS.md` §五·3b-0 ④「经用户批准」＋「调度员不得代批准」）⇒ **Jason 于 2026-10-30 在本 lane 会话内明示「开始工作」** ⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

【开工前置回执 · G2 · 2026-10-30】

**① 任务范围**

本 lane 本轮任务 = **按派生层施工单 `wave=G2` 派工面（281 条）取本 lane 可动的部分**，逐条**回真源复核**后落码/销号：

| # | 条目 | 派生层判定 | 本轮处置 |
|---|---|---|---|
| 1 | `DRIFT-MSG-V-032` | **B 类 · 🟡 部分闭合**（**G2 唯一一条 A/B 类状态开放项**）· 缺口 =「无可复算指针」 | **落码加固**（`service/message/message-rpc/src/test/**` ⇒ G2 写权面内） |
| 2 | `SD-04` / `DRIFT-MSG-V-027` / `DRIFT-MSG-V-020` | **D 类 · 差异已消失**（台账未销账） | **回源复核 ⇒ 回写台账销号**（原值保留） |
| 3 | `D-2`（like）/ `D-6`（like）/ `D-4`（message） | **D 类 · 差异已消失** | **回源复核 ⇒ 回写台账销号** |
| 4 | `SD-2026-10-23-09`（like） | **⏳ 未签字 · 未定级**（派生层 `stage=—`） | **复核前提成立 ⇒ 按 §10 定级为 C 类**（阶段 2 承接）⇒ **不推拍板、不改代码** |

**不做**：① 共享面（`service/common/**` ＋ `service/users/users-common/**`）⇒ **只读**；缺陷一律**登记反馈单转 G4**（§5.3e 7 项）② `tools/**`（`tools/difftest/**` 与派生层产物**不属任何 lane** ⇒ 归合并收口执行者）③ **共享索引类文件**（`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md`）④ **阶段 2 不碰、不预支**（`twoStage.md` §1.3 / §2.2 / §6 / §7）⑤ **本轮只做「阶段一范围内 + 本域能自修」的活** ⇒ C/D 类只登记不定实现。

**② 已读四档（本 lane 面）**

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `SD-2026-10-23-03`（`affected`：Go 返 `int64` 数字 / RAG 出字符串）**已签字** ⇒ 对拍必红、**不得单方面改回数字**；`message/rpc.md` 的 **E59 已销案**（DB 失败原样上抛 ⇒ `UNKNOWN`＋1015 ＝ Go）；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4；`-012` 属容器层框架限制**不可修**；**`DRIFT-MSG-V-032` = 本轮唯一 A/B 类开放项**（`:2801`，登记形态「🟡 登记（不改）」） |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12：`DRIFT-LIKE-003` 拆三段 = A 投递链路已闭环 ＋ B 权威读语义 Java 化 ＋ **C 暴露形态待拍板**；**E60 已销案**；`L-U11` 已闭合；`L-U9` = 取证缺口（非必相对齐项）；`like/api.md`：`404 body` ＝ **19** 字节；`like/proto.md`：`LikeProtoContractTest` 4/4 |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`TaskRpcKafkaListenerRegistryGateTest` 3/3（listener **真进容器 registry**）；`DRIFT-TASK-RPC-008` 已落码；`task/api.md`：`DRIFT-TASK-API-004`（charset/空白）＝ **全域共性**，不在本域单修 |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/api.md`：**`SD-2026-10-23-03`**（`affected` 类型）＋「`-009` 已改正为签字」；`message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引名不同 · `-02` 直方图桶零交集 · `-03` `errors.As` vs `instanceof` · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的 · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`，**wire 层可见**）；`like/api.md`：**`SD-2026-10-23-08` 已失效·已撤销**（共享层 T-6 已补 charset）＋ **`SD-2026-10-23-09` 仍成立·⏳ 未签字**（裸 503）＋ `SD-2026-10-24-10`（毫秒粒度）＋ `SD-2026-10-24-11`；`like/rpc.md`：**`SD-2026-10-23-06`**（业务码迁到 trailer `rag-like-biz-code`，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出**）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 存活）＋ 4 项「不是偏离、但容易被误当成偏离」；`task/api.md` ＋ `task/proto.md` = **0 字节坑位**（勿建空壳） |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `g2.md:21-31` = 11 组件矩阵（10 ✅ ＋ `like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:24` = `like-rpc` 的 `DRIFT-LIKE-003` **仅剩 C 暴露形态待拍板**；`g2.md:41`（§2.1 自修清单）＝ **本 lane 自修项只有 `like-rpc` 一条**；`g2.md:47`（§2.2 转派 G4）＝ message-api `-010`/`-011`/`-013`；`g2.md:57-62`（P0 分摊）＝ **P0-1 本 lane 主责**（like 域事实投递链路 ＋ 双侧活体探针**不在版本控制内** ⇒ 未取证）· **P0-3** `check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引/唯一约束/触发器**至今没有机器核过** · **P0-6** 全量回归 2860 条 / 0F / 0E / 4S ⇒ **必须带 `clean`**；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10」 |
| `docs/PhasedGoal.md` | `:38-52`（P0 九条） | **P0-1**「核心业务链路**真实运行**」· **P0-4**「核心失败路径有明确行为」· **P0-6**「测试断言真实业务结果」· **P0-9**「已知缺陷、**无法验证的关键能力和运行前提**得到明确记录」⇒ ⇒ `003-C` 与 `L-U9` 属 P0-1/P0-9 的「运行前提」面 ⇒ 台账必须写「**未取证**」而不是「已对齐」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· §10（`:941-976`） | §5.3 = 差异与暂缓项**每项至少 11 条**（编号/原始契约/原始行为/阶段一当前行为/具体差异/暂缓理由/影响/是否阻断/阶段二恢复方案/阶段二测试场景/证据缺口）⇒ **「不得只写『后续优化』」**；§5.4 = 每次改造记录 **9 条**（改前/问题/改后/理由/变更文件/测试场景与断言/执行结果/未解决/对阶段二影响）⇒ ⇒「**必须实际修改项目，而不是只输出建议**」；§10 = **P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险** |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `PIT-MESSAGE-01`（同组件同形态**第二次**「绿测试守卫错误答案」）· `-02`（Jackson `FAIL_ON_NULL_FOR_PRIMITIVES` 默认 true）· `PIT-MESSAGE-09`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-10`（「断言 Bean 存在」≠「断言机制生效」）· `-12`（注释里的**默认行为**必须读到**消费点**） |
| | `like/{api,rpc}.md` | `PIT-LIKE-01`（go-zero 解析层**不对称**，只学一半必然漏）· `PIT-LIKE-05`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `PIT-LIKE-06`（**同一个文件里会自相矛盾**）· `PIT-LIKE-RPC-013`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-14`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PIT-TASK-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **15 条真 PG 用例静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**）· `-07`（81 条测试**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · **变异测试是另一半**）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享面修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**） |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `stage1-derivation.md` ＋ `.tsv` · `component-coverage-matrix.md` · `cross-domain-coupling.md` · `domain-common-lanes.md` · `ddl-type-divergences.md` · `go-anchors.md` · `decisions.md` | **施工单加 `wave` 列**（`bdac3607`）⇒ **本 lane 的派工面 = `wave=G2` 共 281 条**；§4.0 波次分布 = G2 **状态开放 77 条 / 其中 A/B 类 0 条**（⇒ **无漏排**）；§4.1 A/B 类真缺口 = **空表** ⇒ ⇒ **本轮 G2 的真活 = ① 唯一一条 A/B 类开放项（`DRIFT-MSG-V-032`）② §4.4 ④ 档的台账销账 ③ 1 条未定级的 ⏳ 未签字候选**；`cross-domain-coupling.md` = G1~G4 波次表 ＋ **共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）＋ 依赖 DAG；`ddl-type-divergences.md` = 类型/长度 **9 域 0 差异**，`hot` = **N/A** ⇒ 不许写成「已对齐」；`decisions.md` = 问题 1「**直接丢掉 Flyway**」＋ 问题 4「**每模块独立账本表**」 |

**③ 待批准项**

1. **无**（本轮范围已由 Jason 在本 lane 会话内明示「开始工作」批准 ⇒ 无需额外拍板）。
   ⚠️ **本轮刻意不做、留给阶段二或收口的**：① `DRIFT-LIKE-003-C`（like 域权威读**暴露形态**：Go 是**独立进程** `cmd/fact-authority/main.go`；RAG 侧无 HTTP 端点 ⇒ 落地需动**共享面** ⇒ **G2 不可写** ⇒ **形态决策须拍板**）② `D-7`（like 域 status 错误 ⇒ 业务码映射与 Go 不同类 ⇒ 阶段二做一次**双侧活体定值**再决定）③ `SD-2026-10-23-09` 的**签字**（本轮只定级为 C，**不推拍板**）④ 其它模块的同款「发完就 `shutdownNow()`」写法（**阶段二**同族项）。

**④ 等批准 ⇒ 批准后才开工**

**⑤ 批准记录：Jason · 2026-10-30 10:31 · 批准范围：本回执 ① 所列 G2 任务（`wave=G2` 派工面内的阶段一范围条目：`DRIFT-MSG-V-032` 落码 ＋ 5 条已闭合项销号 ＋ `SD-2026-10-23-09` 定级为 C）—— 依据：Jason 于 2026-10-30 在**本 lane 会话内**明示「开始工作」**　← 由**调度员代填**（**代填 ≠ 代批准**；回执人不得自批）**

**⑥ 本轮 §3.3 回源复核的实测记录（**派工前按 `basis` 抽查，不照抄标题**）**

| 条目 | 回源实测 | 结论 |
|---|---|---|
| `SD-04`（§⑥-A 复核行） | Go `proto/message.proto:3` = `package message;` ＋ `:5` = `option go_package = "./pb";` ＋ `service/message/rpc/pb/message_grpc.pb.go:383` = `ServiceName: "message.MessageService",` vs RAG `message-proto/src/main/proto/message.proto:3` = `package message;` | ✅ **两侧 proto package 本来都是 `message`** ⇒ 本域**从未引入**该偏离 ⇒ 派生层把标题里的**交叉引用**当成了**登记** ⇒ **差异已消失 ⇒ 销号** |
| `DRIFT-MSG-V-027`（§⑥-B 复核行） | Go `service/message/rpc/internal/model/db.go:42` = `db.AutoMigrate(...)` ⇒ **Go 侧无版本化迁移账本** vs RAG `message-rpc/src/main/resources/application.yml:66` `baseline-on-migrate: true` ＋ **`:94` `baseline-version: 0`** ＋ `:105` `table: flyway_schema_history_message` ＋ 守门 `MessageFlywaySharedSchemaWiringTest.java:164`/`:179`/`:187` | ✅ **V1 不会被 baseline 跳过**（baseline 打在 version 0）⇒ **差异已消失 ⇒ 销号** |
| `DRIFT-MSG-V-020`（§⑥-C 复核行） | Go `db.go:22` DSN 钉 `TimeZone=Asia/Shanghai` ＋ `:42` `AutoMigrate` vs RAG `MessageRpcSupport.java:107`/`:110` `toEpochSecond(OffsetDateTime)`（**与时区无关**）＋ `V5__message_time_columns_timestamptz.sql:2` | ✅ **两侧 0 差异** ⇒ **销号** |
| `D-2`（like） | RAG `LikeSpans.java` **存在**（12298 B）＋ `service/like/*/src/main` 内 `setAttribute` **11 处命中** ＋ 守门 `LikeSpansAttributeGateTest.java` ＋ `LikeA9GrpcSpanEndToEndTest.java` **均在仓** ＋ 共享层 `RpcInterceptor.java:467-480`（`makeCurrent()` 包住 handler ⇒ 执行期 `Span.current()` **就是** server span） | ✅ **域侧落码 ＋ 共享层 FB-04 落码**两侧闭合 ⇒ **差异已消失 ⇒ 销号** |
| `D-6`（like）/ `D-4`（message） | 域侧 `config/MybatisPlusOrderingBridgeAutoConfiguration.java` **两个域都已不在**（`find` 命中 = 0）＋ 共享层 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` **在** ⇒ ⇒ **闸门已开、域侧桥已收编** | ✅ **差异已消失 ⇒ 销号** |
| `SD-2026-10-23-09`（§⑥-D 复核行） | Go `service/like/api/**` 内 `maxConns`/`Semaphore`/`worker` 命中 = **0** ⇒ Go 侧**无 worker 池概念** vs RAG `HttpMiddleware.java:154` `MAX_HTTP_WORKERS = 512` ＋ `:369` `catch (RejectedExecutionException saturated)` | ✅ **前提成立**（行为仍在）⇒ 但两侧「过载 ⇒ 拒绝」**大方向同向** ⇒ RAG 多的是**载体与形状** ⇒ **不改回去 ⇒ 不属 DRIFT** ⇒ 按 §10 **按问题本身定级 = C**（未签字候选**不得自动归 D**）⇒ **不推拍板** |
| `DRIFT-MSG-V-032`（§⑥-E 复核行） | Go `service/common/observability/observability.go:41` = `func SlowThreshold() time.Duration`（阈值真源）＋ `service/message/rpc/message.go:44` = `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))`（打标唯一装配点）⇒ **Go 侧无任何对应测试** ⇒ **本条不是 Go↔RAG 行为差异** vs RAG 守门 `MessageRpcSlowThresholdGateTest.java:280`（用例仍在）＋ `:324`（`warmUp()` —— ⚠️ **台账记的 `:318` 已漂移**）＋ `:349`（`callAndGetSpan` —— 仍准） | ✅ **缺口真实存在**（`:340-346` warm-up「发一次就 `shutdownNow()`」＋ **无重试**）⇒ **落码加固**（域内可自修）⇒ **落点 = `service/message/message-rpc/src/test/**`** |


---

## 开工前置回执（2026-10-30 轮 · rpc 域指标抓取载体）

> ⚠️ **本轮性质**：**派工指定的载体补齐**（Jason 2026-10-30 明示批准「这波就补载体」）⇒ **④ 已由调度方转达满足**；但 **①②③ 仍须本 lane 自己做完**（AGENTS.md §5.3b-0）⇒ **回执人不得自批**。

### ① 本轮任务界定（定任务）

| 项 | 内容 |
|---|---|
| 派工来源 | 调度会话 `mvs_d218b18e499340d9b6f31e2097e0e420` 转达 · Jason 2026-10-30 明示批准「这波就补载体」 |
| 任务本体 | 给 **rpc 域补「指标抓取载体」**：Go 侧 `DevServer` 起**独立端口**出 Prometheus 文本；RAG 侧对应的载体能力（`spring-boot-starter-web` ＋ Actuator ＋ `server.port`）在 message-rpc / like-rpc **缺席** ⇒ **指标注册了但抓不到** |
| 端口口径 | `message-rpc` = **16067**（Go `service/message/rpc/etc/message.yaml:22`）· `like-rpc` = **9098**（Go `service/like/rpc/etc/like.yaml:22`）· `task-rpc` = **不补**（Go 侧 `service/task/rpc/etc/task.yaml` **无 `DevServer` 段** ⇒ 两侧同形 ⇒ 补了反而是**偏离**） |
| 模板 | `service/follow/follow-rpc/pom.xml:165-217` ＋ `service/follow/follow-rpc/src/main/resources/application.yml`（**照抄，不自创**）⇒ 该域 2026-10-29 已落地同款载体（台账 `docs/DRIFT/follow/rpc.md:351-488`） |
| 本波不做 | ① **共享层 `service/common/**` 一行不碰**（G4 写权面）② **不加任何 opt-out 属性**（共享层正在裁「rpc 域观测口要不要挂 Go 的 rest 中间件」⇒ 合并窗口由收口执行者统一加）③ 不碰其它域 ④ 不 push / 不开 PR ⑤ `like-api` / `message-api` / `task-api` **不在本波**（派工只点了两个 rpc 组件） |

### ② 已读四档（本 lane 面）

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `message/rpc.md`：**`DRIFT-MSG-V-032` 已于上一轮落码销号**；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4；`-012` 属容器层框架限制**不可修**；**本轮新增关注面 = 指标抓取出口**（`message/rpc.md:873`「与 Go 的 `prometheus` 客户端**不是同一套** ⇒ 属产品能力」） |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12：`DRIFT-LIKE-003` 拆三段 = A 投递链路已闭环 ＋ B 权威读语义 Java 化 ＋ **C 暴露形态待拍板**（**注意：C 段讲的是「事实权威读的 HTTP 暴露形态」，与本轮「Actuator 观测口」是**不同轴**，不冲突**）；**`U-06`**（Go `DevServer` 端点形态：端口 / 路径 / pprof 三层都不同）**已在 `like/api.md:1744` 定级为 C / P2**，其「全仓统一方案」结论指向 G4 ⇒ **本波只做 rpc 两个组件** |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`DRIFT-TASK-RPC-008` 已落码；**Go 侧 `service/task/rpc/etc/task.yaml` 无 `DevServer` 段** ⇒ **本波明确不补**（同形 ⇒ 补了才是偏离） |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引名不同 · `-02` 直方图桶零交集 · `-03` `errors.As` vs `instanceof` · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的 · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`，**wire 层可见**）；`like/api.md`：**`SD-2026-10-23-08` 已失效·已撤销** ＋ **`SD-2026-10-23-09` 仍成立·⏳ 未签字**（裸 503，**上一轮已定级 C**）＋ `SD-2026-10-24-10` ＋ `SD-2026-10-24-11`；`like/rpc.md`：**`SD-2026-10-23-06`**（业务码迁到 trailer，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出**）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 存活）＋ 4 项「不是偏离、但容易被误当成偏离」 |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `g2.md:21-31` = 11 组件矩阵（10 ✅ ＋ `like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:41`（§2.1 自修清单）＝ **本 lane 自修项只有 `like-rpc` 一条**；`g2.md:47`（§2.2 转派 G4）＝ message-api `-010`/`-011`/`-013`；`g2.md:57-62`（P0 分摊）＝ **P0-1 本 lane 主责** · **P0-6** 全量回归 ⇒ **必须带 `clean`**；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· **§十**（`:941-976`）· 维度 8（`:754-768`） | §5.3 = 差异与暂缓项**每项至少 11 条**（**不得只写「后续优化」**）；§5.4 = 每次改造记录 **9 条**（**必须实际修改项目，而不是只输出建议**）；**§十 = P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险**；**维度 8**（日志/指标/追踪）明写「**仅发现依赖或监控面板模板不能证明可观测性完整**」⇒ ⇒ **本轮「补载体」正落这一条** |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `PIT-MESSAGE-01`（同组件同形态**第二次**「绿测试守卫错误答案」）· `-02`（Jackson `FAIL_ON_NULL_FOR_PRIMITIVES` 默认 true）· `-09`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-10`（「断言 Bean 存在」≠「断言机制生效」）· `-12`（注释里的**默认行为**必须读到**消费点**）· **`-37`（上一轮新登记：回执小节被派生层当成条目 ⇒ 凭空多出条目）** |
| | `like/{api,rpc}.md` | `PIT-LIKE-01`（go-zero 解析层**不对称**，只学一半必然漏）· `-05`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `-06`（**同一个文件里会自相矛盾**）· `-13`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-14`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PIT-TASK-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**）· `-07`（**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · **变异测试是另一半**）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享层修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**） |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `stage1-derivation.md` ＋ `.tsv` · `component-coverage-matrix.md` · `cross-domain-coupling.md` · `domain-common-lanes.md` · `grading/W*.md` | 派生层产物 = **派工面**（`wave` 列 G1~G4）⇒ 本波条目落 **`wave=G2`**；**共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）⇒ ⇒ **本波一律只读**；`grading/W28.md`（follow 域 `E2` 定级 **D** ⇒「**同款 5 处（favorite-rpc / message-rpc / like-rpc / user-rpc / admin-rpc）属别的域，各域各判**」）＋ `grading/W1.md`（follow 域 `E31` 定级 **D**）⇒ ⇒ **本波按「各域各判」办，只判 message-rpc / like-rpc** |
| `docs/DRIFT/follow/`（**模板参照域**） | `follow/rpc.md:351-488` ＋ `follow/rpc.md:20` | **落码清单 4 行**（starter-web ＋ `server.port` ＋ `register-enabled: false` ＋ 桶边界）＋ **残余 5 条全部 P3 / D 类**（暴露路径不同 · 载体不同 · `le` 文本 · `_max` 增项 · **Go 有 `/debug/pprof/*` 而 RAG 无**）⇒ ⇒ **本波沿用同一口径登记残余项，不新造偏离** |

### ③ 待批准项

1. **无**（本轮范围已由 Jason 明示批准「这波就补载体」⇒ 无需额外拍板）。
   ⚠️ **本轮刻意不做、留给收口执行者或阶段二的**：① **共享层「rpc 域观测口要不要挂 Go 的 rest 中间件」的取舍**（共享层 3 个 `@ConditionalOnWebApplication(SERVLET)` 自动配置会因本波补载体而**首次在 rpc 进程激活** ⇒ 属共享层 ⇒ **G4 / 收口执行者**；**本波不加任何 opt-out 属性**）② `DRIFT-LIKE-003-C`（like 域事实权威读的**暴露形态**，**不同轴**）③ `D-7`（like 域 status 错误 ⇒ 业务码映射与 Go 不同类）④ `SD-2026-10-23-09` 的**签字**（只定级为 C，**不推拍板**）⑤ **Go 侧 `/debug/pprof/*` 的对应物**（RAG Actuator 无 pprof endpoint ⇒ 按 follow 域口径列 **P3 / D** ⇒ 不修）。

### ④ 等批准 ⇒ 批准后才开工

### ⑤ 批准记录：Jason · 2026-10-30 · 批准范围：**给 `message-rpc`（16067）与 `like-rpc`（9098）补「指标抓取载体」**（`task-rpc` 不补）—— 依据：调度会话转达 Jason 2026-10-30 明示批准「这波就补载体」**　← 由**调度员代填**（**代填 ≠ 代批准**；回执人不得自批）**

### ⑥ 本轮 §3.3 回源复核的实测记录（**派工前按 `basis` 抽查，不照抄标题**）

| 回源对象 | 本轮自读实测 | 结论 |
|---|---|---|
| Go 侧 yaml（message） | `service/message/rpc/etc/message.yaml:19-26` = `DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16067, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true, EnablePprof: true}` | 端口 **16067** 确认（**一字不差**） |
| Go 侧 yaml（like） | `service/like/rpc/etc/like.yaml:19-26` = 同款，`Port: 9098` | 端口 **9098** 确认（**一字不差**） |
| Go 侧 yaml（task） | `service/task/rpc/etc/task.yaml`（实测 1-40 行）= `Name` / `ListenOn` / `Etcd` / `Telemetry` / `Kafka` / `LikeRedis` … ⇒ **无 `DevServer` 段** | ⇒ **`task-rpc` 不补载体**（两侧同形；补了才是**偏离**） |
| Go 侧**消费点**（不是声明点） | `go-zero@v1.10.2/zrpc/config.go:39` = `RpcServerConf struct { service.ServiceConf ... }` ⇒ rpc 侧内嵌 `ServiceConf` ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:40` = `DevServer DevServerConfig \`json:",optional"\`` ⇒ ⇒ `go-zero@v1.10.2/zrpc/server.go:63` = `if err = c.SetUp(); err != nil {` ⇒ ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:77` = `devserver.StartAgent(sc.DevServer)` | **rpc 侧确实会起 DevServer** ⇒ **不是死配置**（消费点在框架内、仓内 grep 不到读取点） |
| Go 侧起法细节 | `go-zero@v1.10.2/internal/devserver/server.go:79-81` = `StartAgent` → `if !c.Enabled { return }`；`:82-86` = `once.Do(...)`（**进程内只起一次**）；`:44` = `HealthPath`；`:47-50` = `EnableMetrics` ⇒ `prometheus.Enable()` ＋ `handleFunc(MetricsPath, promhttp.Handler().ServeHTTP)`；`:53-59` = pprof 五条路由；`:67-76` = `StartAsync` ⇒ **`:73` = `http.ListenAndServe(addr, s.server)`** ⇒ **独立端口、不是主端口**；`internal/devserver/config.go:5-13` = `Port` 默认 6060 / `MetricsPath` 默认 `/metrics` / `HealthPath` 默认 `/healthz` | 端口与路径**默认值**与真源 yaml 一致 ⇒ RAG 侧取同端口、只改路径 |
| RAG 侧现状（message-rpc） | `service/message/message-rpc/pom.xml:171-179` = 第 5 段**已有** `spring-boot-starter-actuator` ＋ `micrometer-registry-prometheus`，**无** `spring-boot-starter-web`；`service/message/message-rpc/src/main/resources/application.yml:185-205` **已有** `management.endpoints.web.exposure.include: health,info,prometheus,metrics` ＋ `endpoint.prometheus.enabled: true` ＋ `prometheus.metrics.export.enabled: true`；**全文件无 `server.port`** | ⇒ **`management.*` 是死配置**（classpath 无 `jakarta.servlet.Servlet` ⇒ `WebApplicationType=NONE` ⇒ 那一族自动配置**根本不加载**，**不报错**）⇒ 与 follow 域**同款缺陷、同款修法** |
| RAG 侧现状（like-rpc） | `service/like/like-rpc/pom.xml:229-237` = 第 7 段**已有** actuator ＋ micrometer-prometheus，**无** starter-web；`service/like/like-rpc/src/main/resources/application.yml:302-320` **已有** 同款 `management.*`；**全文件无 `server.port`** | 同上 |
| 🔴 **补 web starter 的连带风险（本轮自读发现 · follow 域已踩过）** | `service/message/message-rpc/src/main/resources/application.yml:107-114` ＋ `service/like/like-rpc/src/main/resources/application.yml:197-201` = `spring.cloud.nacos.discovery` 段**均无** `register-enabled: false`（而 `service/follow/follow-rpc/src/main/resources/application.yml:112` **有**）⇒ 补上 servlet 容器后，`NacosAutoServiceRegistration` 会注册**观测口**（16067 / 9098）⇒ 同一服务名下出现**两个端口的实例** ⇒ 调用方按 Nacos 给的地址连到一个只提供 `/actuator/` 的 HTTP 口 ⇒ **gRPC 链路直接断**（业务码塌成 1015） | ⇒ **必须同时补 `register-enabled: false`**（本域 yml 改动 ⇒ **在 G2 写权内**）⇒ 本模块注册是**手写**的（`bootstrap/NacosRegistration.java` 用 `GrpcServerProperties.getPort()`）⇒ 与 Go `zrpc.MustNewServer` 注册 `ListenOn` 的语义一致 |
| 共享层激活面（**只读 · G4 写权面**） | `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` = 6 行 ⇒ 其中 **3 行是 `@ConditionalOnWebApplication(SERVLET)`**：`GoStyleJsonContentTypeAdvice` · `GoStylePathNormalizingAutoConfiguration` · `GoStyleBreakerRouteTemplateAutoConfiguration` ⇒ ⇒ 补 servlet 容器后它们**在 rpc 进程首次激活** | ⇒ **Go 的 rpc 侧 `zrpc.MustNewServer` 一个 rest 中间件都没挂** ⇒ **「rpc 域观测口要不要挂 Go 的 rest 中间件」是共享层决策** ⇒ ⇒ **本波只登记、不加任何 opt-out 属性**（合并窗口由收口执行者统一加） |
| 共享层激活面的**实际影响**（本轮自读） | `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java` 的 `doFilter`：**已 Clean 的路径直接 `chain.doFilter(request, resp)` 原样下传**（`/actuator/prometheus` 本身已 Clean）⇒ **不影响**该端点；`GoStyleJsonContentTypeAdvice` 只在「响应体是 JSON」时改 Content-Type ⇒ Prometheus 文本（`text/plain; version=0.0.4`）**不受影响** | ⇒ 本波补载体**不会**因这两个自动配置而抓不到指标 ⇒ **实测以守门为准**（§3.3：推演不算证据） |

**⇒ 本轮结论（④ 之外的部分）**：**① 已定任务** · **② 四档已读** · **③ 无待批准项** · **⑥ 回源已逐条自读**（Go 侧 yaml + go-zero 框架消费点 + RAG 侧现状 + 共享层激活面）⇒ ⇒ **可以落码**。


## 🆕 2026-10-31 · G2 lane · 出向客户端硬上界（cap）轴 —— 域侧落地与证据

> 本节是本域 **A1 轴**（超时 / deadline）在「**出向 gRPC 客户端硬上界**」这一格上的落点记录。
> ⚠️ **本节不声称**：端到端超时表现已做双侧活体对拍（那需要真下游 ⇒ 见「诚实边界」）。

| 项 | 内容 |
|---|---|
| **① 现象** | 本域 api 侧的出向 gRPC 调用**没有共享层的客户端硬上界** —— devh 注入的是**裸 stub**（`task-api/TaskRpcClient.java:68` 的 `@GrpcClient("task-rpc")`）⇒ **不继承**入向预算、**也没有**自身上界 ⇒ ⇒ 下游挂死时**可无限期挂起** ⇒ 与 Go 的 **2000ms 恒上界直接相反** |
| **② Go 侧出处** | ① `go-zero@v1.10.2/zrpc/config.go:31` `Timeout int64 json:",default=2000"` ⇒ **默认 2000ms**；② 本域 Go 侧 `service/task/api/etc/task.yaml:5` `TaskRpc:`（`Target: 127.0.0.1:9005` ＋ `NonBlock: true`）**无显式 Timeout** ⇒ ⇒ **恒 2000ms**；③ **消费点** `go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25` `ctx, cancel := context.WithTimeout(ctx, t)` ⇒ ⇒ 取更早者 |
| **③ RAG 侧现状（本轮改前）** | **零** `rag.protection.outbound-deadline.cap-millis` 配置 ⇒ ⇒ 共享层 `GoStyleProtectionProperties.OutboundDeadline.capMillis` 取**默认值 0**（`GoStyleProtectionProperties.java:286`）⇒ ⇒ 共享层 `GoStyleOutboundDeadlineInterceptor.effectiveCallOptions`（`:187`）里的 cap 分支**不生效**（`capMillis > 0` 才入 min）⇒ ⇒ **出向零上界**<br>⚠️ **但 `task-api` 另有一份域侧既有上界**（见 ⑥） |
| **④ 本轮修法（域侧只配参数 · 生产代码零改动）** | **仅** `task-api/application.yml` 加 `rag.protection.outbound-deadline.cap-millis: ${TASK_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}` ⇒ ⇒ **封顶逻辑仍唯一由共享层承担**（`@GrpcGlobalClientInterceptor`）⇒ ⇒ 域侧**不新增**私有 helper、**不新增** `withDeadlineAfter` <br>🔴 **【订正 2026-10-31 · 第二来源收敛轮】** 标题里的「生产代码零改动」**已不成立** —— 该域的域侧副本**已删除**（见 ⑥ 行）⇒ ⇒ 本域现状 = **只配参数、零上界代码** |
| **④ 之二 · 为什么 `task-rpc` 不配（两侧同形 ⇒ 配了是死配置）** | ① Go 侧 `service/task/rpc/etc/task.yaml` **零 `Rpc:` 段**（实测全文 1-40 行：`Name` / `ListenOn` / `Etcd` / `Telemetry` / `Kafka` / `LikeRedis` … ⇒ **没有任何出向客户端配置**）；② RAG 侧 `service/task/task-rpc/src/main/java` 里的 `@GrpcClient` **命中数 = 0**，且该 yml **无 `grpc.client` 段** ⇒ ⇒ **两侧都无出向 devh 调用** ⇒ ⇒ 配 cap 只会是一段**永远不被读取的死配置** ⇒ ⇒ 与本域既有立场（`rag.side: rpc` 载体同理：Go 侧无 `DevServer` 段 ⇒ RAG 侧不补）一致 ⇒ ⇒ **不配**，**不是漏配** |
| **⑤ 守门（6 条判据）** | 新增 `TaskApiOutboundDeadlineCapGateTest`（**7** 条）⇒ **7 条 / 0 failures / 0 errors / 0 skipped** <br>🔴 **【订正 2026-10-31 · 第二来源收敛轮】** 收敛后 **9 条 / 0 failures / 0 errors / 0 skipped**⇒ ⇒ 判据从**六条**扩为**八条**：新增「⑦ **生产装配面**」与「④b **放宽方向**」（读数取 surefire XML 的 `tests=` / `skipped=`，**不看** stdout 的 BUILD SUCCESS）<br>六条判据 = ① **配置面** ② **绑定面** ③ **运行期**（**真 in-process gRPC server**，下游**挂起 8s 后才正常返回** ⇒ 判据不靠 `@Timeout` 包装；裸 stub 断**失败码** ＋ 耗时窗口，域 client 断「**按时**返回／抛出、不挂死」）④ **env 覆盖** ⑤ **min 语义**（注入 300ms 入向预算 ⇒ 必须在 300ms 处失败）⑥ **形态面**（本域 `src/main/java` 的**代码行**不得**新增**写死上界的实现；javadoc / 行注释不算） |
| **⑥ 本域既有事实：api 侧有一份域侧上界（第二来源）** | `task-api/TaskRpcClient.java:66` `static final long DEFAULT_CALL_TIMEOUT_MILLIS = 2000L;` ＋ `:110` `return stub.withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);` ⇒ ⇒ 这是**域侧的第二套上界来源**（写死常量 2000）⇒ ⇒ **本轮不删**（协调员要求零代码改动）⇒ ⇒ **如实登记为第二来源**<br>⇒ ⇒ **连带后果**：`task-api` 的第 ④ 条只能取**收紧**方向（cap 覆盖成 1000 ⇒ min(1000, 2000) = 1000 ⇒ 可分辨）⇒ ⇒ 「**放宽**」这一维在 `task-api` **结构性不可测** ⇒ ⇒ 「域侧无副本」改由第 ⑤ 条 ⑥ 的**点位级棘轮**承担 ⇒ ⇒ **该维在本域尚未闭合**，待后续收敛（删域侧副本）后改回放宽方向取证 <br>🔴 **【订正 2026-10-31 · 第二来源收敛轮 · 推翻本行结论】** 本行的**前提已被本轮推翻** ⇒ ⇒ **那份域侧副本已于 2026-10-31 删除**：<br>　① **删除点**：`task-api/TaskRpcClient.java` 的 `getTask()`（现 `:112`）**不再**套 deadline ⇒ 直接用**裸 stub**；连同那个 `withDeadline(...)` 私有 helper 一并删除 ⇒ ⇒ **域侧零上界代码**（常量 `DEFAULT_CALL_TIMEOUT_MILLIS` **保留**，但**不再是运行时上界来源**，只作断言期望值）<br>　② **删除理由（二条）**：(a) 副本把上界**写死**在 `CallOptions` 里 ⇒ 共享层再取 min 时**只压小不压大** ⇒ ⇒ 「上界可 env 覆盖」在**放宽方向**上被架空；(b) 共享层的出向 deadline 拦截器（带 `@GrpcGlobalClientInterceptor`）**已在同一位置**做同样的事 ⇒ ⇒ 删掉内联后**入向预算的传播不丢**（且它用**纳秒**精度，比原先域侧的毫秒精度更贴近 Go 的 ctx）<br>　③ **连带后果（推翻本行末句）**：「放宽」这一维**已闭合** ⇒ ⇒ `task-api` 现**同时**有 **④ 收紧**（证「键被读到」）＋ **④b 放宽**（证「域侧无副本」）两条 ⇒ ⇒ ⑥ 的基线**归零**（零命中形态）⇒ ⇒ **不再依赖「点位级豁免清单」** ⇒ ⇒ **共享层侧对应的例外集条目同时失效** ⇒ ⇒ **需共享层那半（唯一写权方）同步剪枝**，否则主线会红一次「例外已失效」 |
| **⑦ 变异验证（红在目标断言）** | **M-E**（在 `task-api/TaskRpcClient.java` **已登记的那一处旁边**再注入第二处 `withDeadlineAfter`）⇒ **1 红 / 7** —— ⑥ 的**点位级棘轮**报「命中分布 = `{TaskRpcClient.java=2}`，而已登记的基线 = `{TaskRpcClient.java=1}`」⇒ ⇒ **同文件内新增第二处也被抓住**（这正是「豁免必须按点位登记、不能按文件登记」的理由）⇒ ⇒ 变异代码**已全部还原**（`git diff` 复核：生产代码零改动，只剩 yml 的 5 处配置） <br>🔴 **【订正 2026-10-31 · 第二来源收敛轮】** 该变异的**基线已归零**（域侧副本已删 ⇒ ⑥ 改成**零命中**形态）⇒ ⇒ 同一变异现在报「命中分布 = `{TaskRpcClient.java=1}`，而已登记的基线 = `{}`」⇒ ⇒ **红得比之前更直接**（不再依赖「同文件内第二处」这个前提）⇒ ⇒ 且**新增的 ④b 放宽方向**同时红（实测停在 ~2000ms ⇒ 证明域侧副本确实压着上界） |
| **⑧ 诚实边界** | ① 本守门**不起**真实 task-rpc、**不连** Nacos ⇒ 证的是「**本域配的上界真被共享层拦截器读到、且真在出向调用上生效**」，**不**证端到端超时表现；② 本守门**不**带生产链路上的 `GoStyleGrpcClientBreakerInterceptor`；③ **未评估 ≠ 通过** —— 本节全部读数均取自 surefire XML，且变异红在目标断言 |

> 🟢 **补充说明（三点 · 2026-10-31 · 派工复核后补记）**
> ① **第 ④ 条的两条方向分工（别混为一谈）** —— **收紧方向**（cap 覆盖成 1000 ⇒ 收口落在 1000）证的是「**域 yml 的那个键真被读到了**」：共享层 `GoStyleProtectionProperties.OutboundDeadline.capMillis` 的**默认值 = 0（= 不启用）**（`GoStyleProtectionProperties.java:286`）⇒ ⇒ 默认态**根本没有上界** ⇒ ⇒ 若键没被读到，就不会出现 1000ms 的收口 ⇒ ⇒ **默认 0 ⇒ 调小可分辨**（这条对**所有**域都成立）；而**放宽方向**（cap 覆盖成 4000 ⇒ 本该收在 4000）证的是「**域侧没有写死 2000 的副本**」：本域 yml 已配 ⇒ **运行时 cap 已是 2000（不是 0）** ⇒ ⇒ 若域侧另有写死 2000 ⇒ `min(4000, 2000) = 2000` ⇒ ⇒ **调小方向对「写死副本」结构性不可见**（min 只压小不压大）⇒ ⇒ **两个方向测的不是同一件事** ⇒ ⇒ 本域取**放宽方向**（本域零副本 ⇒ 要证「无副本」）· 而 `like-api` / `task-api` 取**收紧方向**（两域各有域侧既有副本 ⇒ 「放宽」维结构性不可测 ⇒ 该维改由第 ⑥ 条的点位级棘轮承担）⇒ ⇒ **两条判据各自要证的那件事，必须分开写**。<br>🔴 **【订正 2026-10-31 · 第二来源收敛轮】** 后半句**已过期** —— `like-api` / `task-api` 的域侧副本**已删除** ⇒ ⇒ 两域现在**同时**有**收紧**（④）与**放宽**（④b）两条 ⇒ ⇒ 零副本的那几个模块（`like-rpc` / `message-rpc` / `message-api`）的**放宽**方向**一条就同时证两件事**（因为「键没被读到」⇒ cap 取默认 0 ⇒ 出向挂满 8s 后**成功返回** ⇒ 断言会红）⇒ ⇒ **两条判据各自要证的那件事，必须分开写**。
> ② **全量回归读数（与守门读数分开记 · 两个口径勿混读）** —— 三域八模块 = **833 条 / 0 failures / 0 errors / 0 skipped**（message-api 122 · message-rpc 115 · message-common 21 · like-api 113 · like-rpc 163 · like-common 18 · task-api 144 · task-rpc 137；统计以各模块 surefire XML 的 `tests=` 求和为准，**不看** stdout 的 BUILD SUCCESS）⇒ ⇒ 守门读数（本轮新增的 37 条）是**其中一个子集**，两者不可互相替代。<br>🔴 **【订正 2026-10-31 · 第二来源收敛轮】** 全量回归读数由 **826** 改为 **833**（+7）—— **+7 就是本轮新增的判据条数**（`message-rpc` ＋⑦ = 10 · `message-api` ＋⑦ = 8 · `like-rpc` ＋⑦ = 8 · `like-api` ＋⑦ ＋④b = 9 · `task-api` ＋⑦ ＋④b = 9）⇒ ⇒ 逐模块口径以 surefire XML 的 `tests=` 求和为准。<br>⚠️ **共享层那一格（`service/common`）另计**：**480 条 / 1 failure / 0 errors / 0 skipped** —— 那 1 条红 = `GoStyleClientCallTimeoutSingleSourceGateTest` 的**例外失效检测**（它自己报「🟢 **例外已失效** ⇒ 请把对应条目从 `KNOWN_INLINE_CAP_CALL_SITES` 里剪掉」）⇒ ⇒ **它是本轮收敛传导到共享层的预期信号、不是回归** ⇒ ⇒ 但**该门归共享层写权**（G4 唯一）⇒ ⇒ **已转派 G4 剪枝** ⇒ ⇒ **「全量回归全绿」这个口径要等 G4 剪完那一笔才算成立**。
> ③ **与共享层守门的分工（不是重复造轮子）** —— 共享层已有 `GoStyleOutboundDeadlineClientCapGateTest`（3 形态：cap 生效 / **负控** cap=0 / min 语义）＋ `GoStyleClientCallTimeoutSingleSourceGateTest`（域侧**不得**出现第三份上界实现）⇒ ⇒ 它们证的是「**共享层的机制本身**生效」；本域这批守门证的是「**本域 yml 的那个键真被读到了、且本域的出向调用真被它收口**」⇒ ⇒ **互补，不重叠** ⇒ ⇒ 且本域第 ⑥ 条（域内代码行不得出现写死上界的实现）正是共享层棘轮**判据④（内联形态）在域侧的落点**。

## 🆕 2026-10-31 · G2 lane · 第二来源收敛轮 —— 域侧副本删除后的落点与证据

> 本节是上一节（`2026-10-31 · G2 lane · 出向客户端硬上界（cap）轴`）的**后续**：上一节记的是「**只配参数**」；
> 本节记的是「**把域侧那份副本也删掉**」⇒ ⇒ 落点从「域侧零上界代码」变成「**域侧只剩一个参数**」。
> ⚠️ **本节不声称**：端到端超时表现已做双侧活体对拍。

| 项 | 内容 |
|---|---|
| **落点（生产代码）** | `service/task/task-api/src/main/java/com/rag/task/task/api/client/TaskRpcClient.java` —— `getTask()`（`:112`）**不再**套 deadline ⇒ 直接用**裸 stub**；那个 `withDeadline(...)` 私有 helper（含其 javadoc）**一并删除**；常量 `DEFAULT_CALL_TIMEOUT_MILLIS`（`:91`）**保留**，但**不再是运行时上界来源**，只作断言期望值（守门核对「yml 绑出的值 == 本常量 == 2000」） |
| **为什么可以删（语义等价论证）** | ① **Go 侧形态 = 单源**：`go-zero@v1.10.2/zrpc/config.go:31` `Timeout int64 json:",default=2000"` ⇒ 默认 2000ms；`go-zero@v1.10.2/zrpc/client.go:70-71` ⇒ 上界加在 **client 的 CallOptions** 上 ⇒ ⇒ **每个出向调用都带、且只有一个来源**<br>② **共享层已在同一位置做同样的事**：`GoStyleOutboundDeadlineInterceptor` 带 devh 的 `@GrpcGlobalClientInterceptor` ⇒ 装在**每个**出向 devh 调用上 ⇒ 其 `effectiveCallOptions` 取 **min(入向剩余纳秒, cap-millis, 调用点自带 deadline)** ⇒ ⇒ **入向预算的传播一并由它承担** ⇒ ⇒ 删掉域侧内联**传播不丢**（且它用**纳秒**精度，比原先域侧的毫秒精度更贴近 Go 的 ctx）<br>③ **删掉之后反而更接近 Go**：域侧副本把上界**写死**在 `CallOptions` 里 ⇒ 共享层再取 min 时**只压小不压大** ⇒ ⇒ 「上界可 env 覆盖」在**放宽方向**上被架空 ⇒ ⇒ **那份副本本身就是偏离 Go 的一层** |
| **守门（判据从六条扩为八条）** | `TaskApiOutboundDeadlineCapGateTest` **9 条 / 0 failures / 0 errors / 0 skipped**（surefire XML 口径）⇒ ⇒ 判据从**六条**扩为**八条**<br>新增两条 = ④b **放宽方向**（cap 覆盖成 4000 ⇒ 断言耗时 ≥ 3000ms ⇒ **专抓「域侧写死副本」**）＋ ⑦ **生产装配面**（反射读本域启动类的**真实扫描声明** ＋ 真上下文 ＋ **本域磁盘那份 yml** ⇒ ⇒ 断言拦截器成 Bean、cap 经**生产绑定路径**绑出 2000、且 devh 的收集器真的收它）<br>⚠️ ⑥ 形态面从「点位级棘轮（基线 = 1）」改成**「零命中棘轮（基线 = 0）」**<br>⚠️ ④ / ④b 两条判据都**走域 client（生产路径）**（不是裸 stub）⇒ ⇒ 域侧那份副本就装在 `getTask()` 里 ⇒ ⇒ 走裸 stub 的话它**结构性不可见**<br>⚠️ **本组件原先没有同类既有守门**（其余测试只 mock client）⇒ ⇒ 删副本后的「域 client 层不挂死」这一格由本轮 ③ 的第二条（走 `getTask()` 的真调用）接管 |
| **跨写权面耦合（🔴 必须共享层那半同步剪枝）** | 共享层 `GoStyleClientCallTimeoutSingleSourceGateTest` 的判据 ④（点位级棘轮）在本轮**红了** —— 它的 `KNOWN_INLINE_CAP_CALL_SITES` 里那两条**已失效**（域侧点位已被修好）⇒ ⇒ 共享层那个门自己报「🟢 **例外已失效** ⇒ 请剪掉」⇒ ⇒ **该门归共享层写权**（G4 唯一）⇒ ⇒ **已转派 G4 剪枝**（反馈单 7 项已发）⇒ ⇒ ⚠️ **合并时机**：若在 G4 剪完前合并，主线会红**一次**（红在「例外已失效」这一条 ⇒ **是预期信号、不是回归**） |<br>🔴 **【2026-10-31 16:0x 追加 · A-1 守门这条耦合，与上一条同源但不同门】** ⇒ ⇒ 本 lane 把 M2 判据**从「空转」改成真生效**（`docs/PhaseGoal-judge.md:117` 的总数声明行改成正则可匹配形态 `**全仓源声明测试方法数** = **3,300**` ⇒ ⇒ `check-source-test-decl-count.py` 的 M2 从「跳过」变成「真比对」）⇒ ⇒ **实测（临时树真合并 G2+G3）**：合并树 `check-source-test-decl-count` **EXIT=1**（A-1 列求和 **3300** vs 源声明求和 **3314**）＋ `check-phasegoal-consistency` **EXIT=1** ⇒ ⇒ **M2 激活 = 跨 lane 的全局总数断言被打开**⇒ ⇒ ⚠️ **根因不在本 lane**：那 14 条差额来自 G3 的 `049ffb66`（`comment-api` +4 / `favorite-api` +3 / `hot-api` +7）**未回填 A-1 的 `tests` 列** ⇒ ⇒ 已在 **G3 分支上单独实测到同一现象** ⇒ ⇒ **不是合并引入的**⇒ ⇒ ⚠️ **本 lane 分支上必须保持 3,300**（写 3,314 会让本 lane 自己的门立刻红 ⇒ ⇒ 3,314 是**合并后**的实测值）⇒ ⇒ **最终值归合并收口时写** ⇒ ⇒ **已回执协调员**（该行归本 lane 修 ⇒ 本 lane 已在自己分支写好自洽值）<br>🔴 **【2026-10-31 16:3x 订正 · 上面那句「归合并收口时写」已被后续事实取代 ⇒ ⇒ 现在由本 lane 直接写】** ⇒ ⇒ **合并已经发生**：主干已含 G2 第三波（`22423962`）＋ G3 的 A-1 回填（`713896ff`）＋ G4 的例外集批剪 4 条（`55bdd673`）＋ G1 第三波（`fd6c9830`）⇒ ⇒ **合并后实测源声明求和 = 3,314** ⇒ ⇒ ⇒ ⇒ 而 A-1 的 `tests` 列**逐行**已经全绿（**M1 列求和 3314 == 源声明求和 3314** ⇒ ⇒ G3 那三行回填已生效）⇒ ⇒ **只剩 M2 一条** ⇒ ⇒ ⇒ ⇒ **本 lane 把该行从 `**3,300**` 改成 `**3,314**`**（同一行就地替换 · **零行位移** · 文件行数 728 → 728）⇒ ⇒ **实测 `check-source-test-decl-count` EXIT=0**（M1+M2 全过）⇒ ⇒ ⚠️ **为什么这次该本 lane 直接写**：① 该行的**形态**是本 lane 在 `4b7b3a9c` 里改的（M2 激活）⇒ ⇒ **写权归本 lane**；② **合并已完成** ⇒ ⇒ 3,314 已是**终值**（不再是「合并后的预测值」）⇒ ⇒ **不存在「哪个 lane 写」的歧义**⇒ ⇒ ⚠️ **上一条曾把它写成「3,314 是合并后的实测值 ⇒ 合并前不要写」** ⇒ ⇒ **那条现在仍然成立**（本 lane 合并前那棵树确实是 3300）⇒ ⇒ **本条是它的后续**：合并落地 ⇒ ⇒ 数字变终值 ⇒ ⇒ 本 lane 落码⇒ ⇒ ⚠️ **M2 只被 `check-source-test-decl-count.py` 读**（已 grep 全 `tools/**` ⇒ 无第二处依赖）⇒ ⇒ 影响面 = 1 道门 ⇒ ⇒ **合并时机**：若在收口改总数前合并 ⇒ ⇒ 主线会红**一次**（红在 M2「文档声明 ≠ 实测」⇒ ⇒ **是预期信号、不是回归**） |<br>🔴 **【2026-10-31 16:2x 追加 · 本 lane 自己引入的一处守门误报，已修】** ⇒ ⇒ 合并主干后 `check-wave-coupling.py` 报 **1 项** `common_radiation` 漂移（`GoStyleProtectionConfiguration` 多出 `message`）⇒ ⇒ **归因（本 lane 的错）**：该门用 **import 判定**扫辐射度（`tools/difftest/check-wave-coupling.py:190` `re.findall(r"^import\s+...")`）⇒ ⇒ **全限定名引用看不见**⇒ ⇒ 而 `message` 域的**生产代码**用的是**全限定名**（`MessageRpcApplication.java:96` `com.rag.common.protection.GoStyleProtectionConfiguration.class` ⇒ 该引用**早于 10-24** 就在 ⇒ ⇒ **不是本 lane 引入**）⇒ ⇒ **真正让该门看见 `message` 的，是本 lane 新增的两个守门类**（`MessageRpcOutboundDeadlineCapGateTest` / `MessageApiOutboundDeadlineCapGateTest` 用 `import` 引用它）⇒ ⇒ ⇒ ⇒ **修法**：把那两处改成**全限定名**引用（与生产代码形态一致）⇒ ⇒ 删 import 语句 + `ctx.register(...)` / `imports.contains(...)` 改全限定名 ⇒ ⇒ **零语义变化**（同一 class 对象）⇒ ⇒ **实测**：`check-wave-coupling.py` ⇒ **EXIT=0 / PASS**（6 组全过）＋ `mvn compile` **EXIT=0** ＋ 两个守门类真跑 **18 条 / 0F / 0E / 0S**（message-rpc 10 + message-api 8）⇒ ⇒ ⇒ **判据的盲区本身仍在**（import 判定 ⇒ 全限定名引用**系统性漏计** ⇒ ⇒ 辐射度**偏小**）⇒ ⇒ **该盲区属共享面**（`tools/**`）⇒ ⇒ **本 lane 不改工具** ⇒ ⇒ **只报告** ⇒ ⇒ 已回执协调员/G4 |
| **变异验证（红在目标断言）** | **M-H**（在 `task-api/TaskRpcClient.getTask()` 把域侧那份写死 2000ms 的副本**加回来**：`.withDeadlineAfter(2_000L, TimeUnit.MILLISECONDS)`）⇒ **2 红 / 9**、**0 errors** —— ① **④b** 红在「实测耗时 = **2006ms**，必须 ≥ 3000ms」⇒ ⇒ 正是「域侧还压着一份写死 2000ms 的副本」；② **⑥** 红在「命中分布 = `{TaskRpcClient.java=1}`，而已登记的基线 = `{}`」⇒ ⇒ **两条全部红在目标断言**（**不是**红在容器/端口/`@Timeout`）⇒ ⇒ 变异代码**已还原**（`git diff` 复核：生产代码回到收敛后的形态） |
| **诚实边界** | ① 本节全部读数取自 surefire XML，**不看** stdout 的 BUILD SUCCESS；② 变异红**均在目标断言**（④b 停在 ~2000ms、⑥ 报「命中分布 ≠ 基线」），**不是**红在容器/端口/`@Timeout`；③ ⑦ 证的是「**装配面 ＋ 绑定面**」，**不**证「本域进程连得上 Nacos / 真 task-rpc 起得来」；④ 「零副本」这件事有**两条独立的运行期证据**：域侧 ⑥ 零命中棘轮 ＋ ④b 放宽方向（另有共享层棘轮，**待 G4 剪完**才算第三条） |
