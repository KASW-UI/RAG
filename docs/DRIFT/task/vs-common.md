<!-- @new 2026-10-27 · `task-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `task-common` lane 台账 —— 「task ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（⚠️ **task 域没有 `-common` 模块**（Go 侧同样没有，**不是遗漏**）⇒ 本 lane 的「域 ↔ `service/common`」语义最纯粹）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面一律不写**（合并后由整合者统一更新）

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
| `DRIFT-MSG-V-026` | 熔断 / 降载链必须**真挂到** gRPC server | **拆两笔**：① **装配面 = 域侧**（`TaskRpcApplication` 是域内文件）② **链的可断言性 = 共享层**（`GoStyleRpcProtectionChain`） | 🟡 **部分修**（旧快照「守门已立」**掩盖了一个缺口**）—— ① `@Import GoStyleProtectionConfiguration` **属实**：`TaskRpcApplication.java:113` ＋ `@Bean GrpcServerConfigurer` `:130-139`（**唯一把 interceptor 挂上 server 的入口**）② 链上**确有** breaker：`GoStyleRpcProtectionChain.java:72` → `GoStyleRpcBreakerInterceptor.java:140-154` ③ 🔴 **shedding 未被守门证明** —— `TaskProtectionChainWiringTest.java:80-81` **主动 `props.getCpuShedding().setEnabled(false)`** ⇒ 该测试**只证明 breaker**，而 DisplayName 写的是「breaker/shedding」 | [`rpc.md`](rpc.md) |
| `TaskRpcFatJarArtifactGateIT`（4 条） | 产物层守门 | 域侧构建 | ✅ **已立**（本轮只读源码，未跑构建 ⇒ 「已立」而非「已验证」） | `task-rpc/pom.xml:283-286`（`spring-boot-maven-plugin`，不写 `<mainClass>`⇒自动探测）+ `:207-211`（`grpc-inprocess` **scope=compile**，非 test）+ `:336-347`（surefire 第二 execution 绑 `verify`）；测试 `TaskRpcFatJarArtifactGateIT.java:74-143` | [`rpc.md`](rpc.md) |
| `TaskRpcSlowThresholdGateTest`（2 条） | slow 轴守门 | `service/common`（配置读取） | ✅ **已立**（真 server + 真链 + 真 OTel SDK + 收集型 exporter） | `TaskRpcSlowThresholdGateTest.java:267-275`（取容器里的 `rpcInterceptorConfigurer` + `ServerBuilder.forPort(0)` 真起）+ `:305`/`:312`（判别式断言）· 消费点 `RpcServerInterceptorFactory.java:77-79`；⚠️ 它同样关掉了降载（`:148`） | [`rpc.md`](rpc.md) |
| ✅ `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地**；本域 `mode` **阶段 2 已由 `raw` 改为 `"off"`** ⇒ 与「Go 侧本域根本走不到 form 解析」**同向** ✅（守门 `TaskApiFormLimitReachabilityTest` 5/5 绿 + 变异 4 红）<br>⚠️ **改用 `off` 的判据不是「错误形状」而是「语义可达性」** ⇒ 原判据（按「Go 有没有注册 `SetErrorHandler`」决定 raw/envelope）**只回答「拒绝成什么形状」、没回答「该不该拒绝」** ⇒ 该缺口已作为 **FB-02** 派给共享层；🔴 **`off` 在 YAML 里必须加引号**（YAML 1.1 → 布尔 false ⇒ 上下文起不来）⇒ **FB-05** | Go 侧证据：`service/task/api/**` 内 `httpx.Parse` / `ParseForm` / `GetFormValues` **命中数 = 0**（本轮实测）⇒ `gettaskhandler.go:19` 裸 `json.NewDecoder(r.Body)`；路由只有一条（`routes.go:16-25`：`POST /task/get`）⇒ Go 侧 `r.Form` 从不被求值 ⇒ `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）与 `util.go:48-53` 的计数**均不可达** | [`api.md`](api.md) |

⇒ **§1 四条 ⇒ 0 条「新派单」**（3 条已立/已落地 + 1 条拆两笔）⇒ 其中拆出的**共享层半笔**见 §4 **FB-03**。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**2026-10-27 核对**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① server：`task-rpc/application.yml:231` `rpc-timeout: 2s` → 消费点 `RpcServerInterceptorFactory.java:80-81` ② 链挂载 `TaskRpcApplication.java:138` ③ HTTP：`task-api/application.yml:127` `http-timeout: 3s` → `HttpMiddlewareFilter.java:52-57` ④ client deadline：`TaskRpcClient.java:105-111`（`min(2000ms, 剩余)`）⑤ SQL 取消 `TaskRpcApplication.java:92-93` ⑥ **Redis 超时**：`task-rpc/application.yml` `spring.data.redis.timeout: 3s` ＋ `connect-timeout: 5s` → 消费点 `RedisURI.timeout`（`CommandExpiryWriter:160`）＋ `ClientOptions.socketOptions.connectTimeout`（Boot `LettuceConnectionConfiguration:212-215`）⑦ **Redis 连接池**：`task-rpc/application.yml` `spring.data.redis.lettuce.pool.{enabled,max-active,max-idle,min-idle}` ＋ `task-rpc/pom.xml` 的 `commons-pool2` → 消费点 Boot 4 `LettuceConnectionConfiguration:267-271`（`getPoolConfig` ⇒ `:269 setMaxTotal` / `:270 setMaxIdle` / `:271 setMinIdle`）＋ `DataRedisConnectionConfiguration:195-198`（`isPoolEnabled`） | ✅ 一致（三个超时：rpc 2s / http 3s / client 2s，逐个对齐 Go 的 struct tag 默认值）<br>🔴 2026-10-27：**3s 预算的作用域** —— ✅ 一致且**天然不适用**「验签是否计入预算」这个问题：Go 侧 `service/task/api/internal/handler/routes.go` 的路由表（**1 条** `/task/get`）**不带** `rest.WithJwt` ⇒ **task-api 没有鉴权** ⇒ 没有「验签」这件事 ⇒ users 域那条 `DRIFT-USERAPI-009②` 对本域**不适用**（不是「已修」，是「不适用」）<br>🔴 2026-10-27（G2 第二轮）：**Redis 命令/连接超时原先未配** ⇒ 落 Lettuce 默认（命令 **60s** / 连接 **10s**）⇒ Go 侧真值是 go-redis v9.20.0 库默认（命令 **3s** / 连接 **5s**）⇒ **已修**（补 `timeout: 3s` ＋ `connect-timeout: 5s`）⇒ 双侧活体实测：Go 1694ms 快速失败 vs RAG 修前 60021ms 挂起 ⇒ 详见 [DRIFT-TASK-RPC-007](rpc.md#🔴-drift-task-rpc-007--redis-命令超时未配--落-lettuce-默认-60s--断连时命令挂-60sgo-是-17s-快速失败)<br>🆕 **2026-10-27（G2 第三轮）：Redis 连接池维度** —— 原状 = **无池**（Go 侧 go-redis 默认**有池** ⇒ **形态漂移**）⇒ **已修**（加 `commons-pool2` ＋ `lettuce.pool.enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0`）⇒ 守门 **⑤⑥**（**读真装配**，不读 yml）＋ **三条变异验证**（红在目标断言）⇒ 详见 [DRIFT-TASK-RPC-008](rpc.md#🔴-drift-task-rpc-008新登记--2026-10-27redis-连接池维度未对齐--形态漂移-rag-无池-vs-go-有池) | L4（Go 腿活体探针已把「Go 的 3s 预算**不含**验签」钉死；task 侧因其无验签 ⇒ 该子问题在 task 域**恒成立**；Redis 超时轴 = **双侧活体对拍** L4；🆕 Redis **连接池**轴 = **L1 ＋ 装配级 + 变异**（⚠️ 未做双侧并发活体对拍 ⇒ **不写 L4**）） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | ① `@Import`：`TaskRpcApplication.java:83-115`（`RpcServerInterceptorFactory`:87 / `ObservabilityProperties`:88 / `GoStyleProtectionConfiguration`:**113**）② `@Bean GrpcServerConfigurer` `:130-139` ③ 链定义 `GoStyleRpcProtectionChain.java:61-78`（`:65` shedding 分支 / `:72` breaker 分支）④ breaker 包装点 `GoStyleRpcBreakerInterceptor.java:140-154` | ✅ **一致**（相对序 recover→breaker→shedding→业务 与 Go `zrpc/server.go:144-167` 一致；本域 yaml 无 `rag.protection` 段 ⇒ 走默认 `enabled=true`/`900` ⇒ Go 的 `CpuThreshold>0` 等价成立）<br>✅ **守门已补齐两层**（`fad4c63`）：`TaskProtectionChainWiringTest` 由 **1 条链层断言**扩到 **2 条** —— ① 「关降载 ⇒ 拿到的 call 归熔断层所有」② 「不关降载 ⇒ 拿到的 call 归降载层所有」⇒ 原 DisplayName「breaker/shedding」是**假断言**（`:80-81` 主动关降载）⇒ 已订正<br>🟡 **共享层的「链层构成」只读入口（FB-03）已由 G4 落码**（`compose(props)` / `layerNames(chain)`，G4 提交 `182fc32`）但**未合入本树** ⇒ 本轮引用它会让本模块**编译不过** ⇒ 改用等价的「包装层归属」判据顶上，**合并后**按 §5.3e 重跑收口回归时升级 | L1 | 2026-10-27 |
| **A3** | HTTP 保护链 | ① 扫描面 `TaskApiApplication.java:78-91`（`basePackages` 含 `com.rag.common`，**排除** `GoStyleJsonBodyConverterConfig`:88-90）② maxConns `GoStyleMaxConnsFilter.java:95-97,128` ③ breaker `GoStyleBreakerFilter.java:76-78,116` ④ shedding `GoStyleCpuSheddingFilter.java:62-64,117` ⑤ maxBytes `GoStyleMaxBytesFilter.java:82-84,112` ⑥ form 闸门 `GoStyleFormValueLimitInterceptor.java:80-97` + 注册 `GoStyleFormLimitWebMvcConfig.java:41-43`；mode=**"off"**（**阶段 2 已改**，原 `raw`）← `task-api/application.yml:213` ⑦ Tomcat 上限 `GoStyleTomcatParameterLimitCustomizer.java:95,106-118` ⑧ Content-Type 判据 `TaskApiJsonBodyConfig.java:83-97,117-119` ⑨ **路径归一缺** | ✅ **一致**（四道闸门 + 拒绝形状 503/503/503/413 与 Go 一致；🟢 **form 闸门已按「语义可达性」关掉** ⇒ 本域不再多出 Go 没有的拒绝（守门 `TaskApiFormLimitReachabilityTest` 5/5 绿 + 变异 4 红）；🟢 **共享层的判据已由 G4 落码**（FB-02 · G4 `182fc32`：新增 `SEMANTIC_REACHABILITY` 逐域取证表，**task 行 = `Mode.OFF`** ⇒ 与本域 `mode: "off"` **同向** ⇒ 域侧与共享层**不会互相打架**；🟡 **路径归一共享层已落码但未合入本树**（FB-01 · G4 `182fc32`）⇒ 合并后本域需**真容器探针**复核才能把「路径归一」也升 ✅；🔴 **`off` 在 YAML 里必须加引号**（YAML 1.1 → 布尔 false）⇒ §4 **FB-05**） | L1（+ 域侧守门 L4） | 2026-10-27 |
| **A4** | 错误码 / envelope | ① 无信封 `TaskController.java:87-90`（直接返回 `GetTaskResp`）② gRPC→HTTP `GlobalExceptionHandler.java:86-95,101-137`（17 码 + default 500）③ 兜底 = **400** `:290-294` ④ 裸响应体 `:305-311`（`text/plain; charset=utf-8` + `nosniff` + 尾 `\n`）⑤ 404/405 `:254-258,267-274` → `GoStyleRouterErrorResponses` | ✅ 一致（本域**未注册** `SetErrorHandler` ⇒ 形状 = 真状态码 + text/plain，**不是** 200 + 信封 ⇒ 与 like/message/hot 域刻意不同，与 Go 同） | L1 | 2026-10-27 |
| **A5** | 端口治理 | ① api `task-api/application.yml:72` `port: ${TASK_API_PORT:18886}` ② rpc `task-rpc/application.yml:139` `grpc.server.port: 9005` ③ client 寻址 `task-api/application.yml:113` `127.0.0.1:9005`（`negotiation-type: plaintext`:114） | ✅ 一致（与 Go `task/api/etc/task.yaml:3` / `task/rpc/etc/task.yaml:2` / `:6` 逐条对齐；⚠️ 本域下游**不走服务发现**，与 Go 一致）<br>✅ **阶段 2 已治理测试端口段**：本域 **2 处** `RANDOM_PORT`（`TaskApiContentTypeTest.java:62` + 本波新增的 `TaskApiFormLimitReachabilityTest.java:70`）⇒ **`DEFINED_PORT`** + 本域专用段 **21800–21899**（`service/task/task-api/src/test/java/com/rag/task/task/api/support/RealTomcatPort.java` · favorite 同款复制）+ 每类 1 条端口段守门 `bindsToDedicatedPortSegment` | L1（+ 端口段断言 L3） | 2026-10-27 |
| **A6** | errmsg 契约 | — | **N/A**（Go 的 task 域**无** errmsg 模块：`service/task/**` 全目录 `errmsg` 命中 0 ⇒ 错误文案只有两条字面量，已在 A4 内核） | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/task/task-proto/src/main/proto/task.proto:7-25` · 消费点 `TaskServiceGrpcImpl.java:48` | ✅ 一致（逐行 diff：message 3=3、字段 7=7、字段号/名/类型全同、service/rpc 名全同；差异**仅** option 块） | L1 | 2026-10-27 |
| **A8** | DDL + 账本 | ① `baseline-version: 0`：`task-rpc/application.yml:76` ② 独立账本表名 `:79` `flyway_schema_history_task`（`locations`:77）③ DDL `V1__create_task_progress.sql:23-41`（BIGINT/TEXT/TIMESTAMPTZ）④ `V2__create_user_like_count.sql:17-25`（`user_id TEXT`）⑤ 兜底 `TaskSchemaGate.java:41-43,51-52` | ✅ 一致（时间列 **TIMESTAMPTZ**；`user_like_count.user_id` TEXT vs `task_progress.user_id` BIGINT 的**不一致被刻意保留**；账本表名与 message/like/article/comment/favorite/follow 六家两两不同 ⇒ `check-flyway-ledger.py` **EXIT=0**） | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | ① `Traceparent`：`GoStyleTraceparentFilter.java:188-191`（`@Order(+3)`）+ `:270-283`（Extract→Start→**Inject 在业务前**）⇒ 本域经 `TaskApiApplication.java:78-82` 扫到 ② 全局 provider `AutoConfiguration.imports:1` → `GoStyleOtelAutoConfiguration.java:51-52` ③ span 属性 `ObservabilityConstants.java:81,87,99`（`sea.timeout`/`sea.slow`/`sea.duration_ms`）④ `audit.*`：**本域无**（Go 侧 `service/task/**` 命中 0） | ✅ 一致（`audit.*` = **N/A**）；`Traceparent` 注入位置与头名 canonical 化与 Go 同形（Go `rest/handler/tracehandler.go:61`） | L1 / L3 | 2026-10-27 |

> **契约面单列（不在 A1~A9 内）**：Kafka topic `task.logs.filtered.User` / `task.logs.filtered.Article` 是**跨域契约** —— 生产方 = **`like-rpc`**（Go `service/like/rpc/etc/like.yaml:92,99`；RAG `service/like/like-rpc/.../application.yml:222,224`），消费方 = task-rpc 两个 sink（`task-rpc/.../application.yml:160-161` `out-user-topic`/`out-article-topic`）⇒ **两侧 topic 字面量一致**（L1）⇒ 断链风险 = 改名 ⇒ **须 like / task 同批改**。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **A3 form 闸门在本域的开关值** | 原 `task-api/application.yml:176-178` `mode: raw` ⇒ 触发 `GoStyleFormValueLimitInterceptor.java:86,95` | ✅ **阶段 2 已落码**：`mode: raw` ⇒ **`mode: "off"`**（`task-api/application.yml:213`）<br>⚠️ **必须加引号**：YAML 1.1（SnakeYAML）把**未加引号的** `off` 解析成**布尔 false** ⇒ `@Value` 拿到 "false" ⇒ 共享层 `parse` 判非法 ⇒ **上下文直接起不来**（实测报错 `非法的 rag.http.go-form-limit.mode 值：false`）⇒ ⇒ 共享层已派单 **FB-05**（`parse` 是否接受 `false`）<br>⚠️ **改前的前置已逐条核实**：① `GoStyleFormValueLimitInterceptor.preHandle` 的 OFF 分支直接 return；② `GoStyleTomcatParameterLimitCustomizer.customize` 的 OFF 分支**直接 return** ⇒ **不动**容器上限 ⇒ Tomcat 保留 yaml 的 2048；③ 本域**从不调 `getParameter*`**（控制器只有 `@RequestBody`，没有 `@RequestParam`）⇒ Tomcat 惰性解析 ⇒ **不可达 ⇒ 无影响**<br>✅ **域内守门** `TaskApiFormLimitReachabilityTest.java`（**真 Tomcat** + `HttpURLConnection`，5 条）：① 生效模式必须是 **OFF**（**直接**断言 `GoStyleFormValueLimitInterceptor#mode()` —— 光断言「返回 200」区分不了「关掉」与「根本没装」）；② >2048 个非空 query 值 ⇒ 200；③ `?a=%zz` ⇒ 200；④ `?a=1;b=2` ⇒ 200；⑤ 阳性对照（无 query ⇒ 200 + 正常 task 载荷）<br>✅ **变异验证**：把 yaml 改回 `mode: raw` ⇒ **4 条红**（含 400 + Go 原文 `too many form values, error: …`）⇒ 非假绿<br>🆕 **2026-10-27 G4 回执 ⇒ 本条与共享层那一半已闭环**：G4 落码的 `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` 逐域表里，**task 行 = `Mode.OFF`**（理由与本域取证逐字一致：`httpx.Parse / ParseForm / GetFormValues` 命中数 = 0 ⇒ `r.Form` 从不被求值）⇒ ⇒ **本域的 `mode: "off"` 与共享层的语义答案一致** ⇒ 不存在「域侧改了、共享层表里还写着别的」这种互相打架<br>⚠️ 共享层 `REGISTERED_DEVIATIONS` 里**只有 favorite 一条** ⇒ **task 不在偏离清单里** ⇒ 本域这条是**真对齐**、不是「已登记的例外」 |
| D-2 | **`TaskProtectionChainWiringTest` 的覆盖面** | `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskProtectionChainWiringTest.java:80-81`（`props.getCpuShedding().setEnabled(false)`）+ `:116-119`（`isNotSameAs`）⇒ **只证明 breaker** | ① DisplayName / 注释改为「断言 **breaker** 在链上」② **补一条 shedding 断言**（不关降载，断言业务 interceptor 拿到的是**第二层**包装 call）③ 若共享层提供「链层构成」查询入口（FB-03），优先用它断言两层<br>✅ **阶段 2 已落码**（`fad4c63`）：① DisplayName / 注释订正为「断言**熔断**在链上」② 新增 `sheddingLayerWrapsTheBusinessInterceptor`（**不关降载** ⇒ 断言业务 interceptor 拿到的 call 是**降载层**包出来的）③ 判据选「**包装层归属**」而非「降载统计增量」—— 后者是**进程级单例**且每分钟上报会**清零** ⇒ 增量断言有 ~1e-5/次的**假红**概率 ⇒ **守门不能带随机性**<br>⚠️ 断言用 `getEnclosingClass()` 而非 `getDeclaringClass()`（后者对**匿名类**返回 null）<br>✅ **变异验证**：摘掉降载层 ⇒ **1 failure**（红在 `sheddingLayerWrapsTheBusinessInterceptor:150`）；摘掉熔断层 ⇒ **1 failure**（红在 `breakerLayerWrapsTheBusinessInterceptor:118`）⇒ 两次都**只红 1 条**（另一条为阴性对照仍绿）⇒ 非假绿 |
| D-3 | `baseline-version: 0` / 独立账本表名 | `task-rpc/application.yml:76,79` | **无动作**（已核，属实） |
| D-4 | Kafka 跨域 topic `task.logs.filtered.*` | `task-rpc/application.yml:160-161` ＝ `like-rpc/application.yml:222,224` | **无动作**（字面量一致）；改动须 like/task **同批** |
| D-5 | `task-rpc` 的 Kafka listener 容器 | `TaskRpcApplication.java:186-193`（同名 Bean 覆盖自动配置，自带回填 `consumerFactory`）+ `:202-216`（不 seek / 不 commit / `maxAttempts=1`） | **无动作**（已核） |
| D-6 | 🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删** | 本域持有 `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/task/task-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:33` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避 ⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑 `task-rpc` 的真上下文守门 ⇒ 判据 = `SqlSessionFactory` 在 + `@Import` 的 `DeadlineQueryTimeoutInjector` **真在执行链上**<br>⇒ 两座桥同向 ⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② **本域删除爆炸半径 = 2 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行）—— 本域**无任何测试引用该类名**（`grep` 实测）⇒ **「连带处理 javadoc」在本域为零**（类自己的 javadoc 随文件消失）③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `task/rpc.md:69` / `:1289`（把桥登记为基础设施事实）在删除后**一并订正**（**同域写权内**） |

| D-7 | **A5 测试端口段** —— 本域 **2 处**真容器测试类用 `RANDOM_PORT` | `TaskApiContentTypeTest.java:62` · `TaskApiFormLimitReachabilityTest.java:70`（**本波新增的那一个也算**）⇒ 落 OS 临时端口段 49152–65535（**Docker 随机发布端口同段**） | ✅ **阶段 2 已落码**：新增 `service/task/task-api/src/test/java/com/rag/task/task/api/support/RealTomcatPort.java`（**21800–21899**）+ 2 个测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **选段依据**：避开 favorite 的 **21300–21399**、favorite 探针的 **21400–21599**、favorite 脚手架的 **21950–21999**、OS 临时段 **49152–65535** ⇒ 取 **21800–21899**（like **21600–21699** / message **21700–21799**）<br>⚠️ **为什么各域各复制一份**：`service/common` **不发布 test-jar** ⇒ 业务模块 test scope 拿不到共享层测试类 ⇒ 只能复制（与 favorite 域既定做法一致）<br>⚠️ **守门到底拦什么（本轮实测厘清）**：**改回 `RANDOM_PORT` 不会红** —— `@DynamicPropertySource` 的 `server.port` **优先级高于** `webEnvironment` ⇒ 端口**仍**落在本域段内 ⇒ 那种改法其实无害（只是白写了 `RANDOM_PORT`）⇒ 守门真正拦的是 **① 端口注入被拿掉**（⇒ 落回 `task-api/application.yml` 的 **18886** ＝ 生产端口）与 **② `pick()` 返回段外端口** |

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
| **② Go 侧出处** | `go-zero@v1.10.2/zrpc/server.go:144-167`（Recover `:148-150` / Breaker `:157-159` / Shedding `:160-163` / Timeout `:164-167`）⇒ Go 侧**四个拦截器无条件按序装上**（各自由 `zrpc/internal/config.go:21,25` 与 `zrpc/config.go:47` 的默认值决定是否**生效**，但**都在链上**） |
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
| **② Go 侧出处** | Go 侧**没有这个开关**（`rest/config.go` 无 form-limit 旋钮；`maxFormParamCount = 2048` 是 `rest/httpx/util.go:15` 的常量）⇒ OFF 在 Go 侧的等价物就是「**什么都不做**」⇒ ⇒ **RAG 的 OFF 语义本身是对的，问题只在「配置值 → 代码」的传递** |
| **③ RAG 侧现状** | `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:56`（`PROPERTY`）· `:82-98`（`parse`：只认 `off`/`raw`/`envelope`，其余抛）· `GoStyleFormValueLimitInterceptor.java:69-73`（构造期 `@Value` + `parse`）· `GoStyleTomcatParameterLimitCustomizer.java:99-104`（同一把尺子） |
| **④ 证据强度** | **L4（真上下文启动失败实测 · 2026-10-27）**：`非法的 rag.http.go-form-limit.mode 值：false（只允许 off / raw / envelope）` ⇒ **值是 `false` 而不是 `off`** ⇒ YAML 1.1 布尔化的实证；加引号写 `mode: "off"` ⇒ 同一份代码、同一份上下文 ⇒ 正常启动（已实测） |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「域侧按文档写 `mode: off` 就能关闸门，且不会把进程搞挂」**。两条都建议做：① `parse` 额外接受 **`"false"`**（YAML 1.1 的 `off` ⇒ `false`）⚠️ **只加这一个别名**、不要放宽成「任意非法值都当 OFF」（那会重新引入「静默兜底 = 以为装上了其实没装」，`parse` 的 javadoc 明确反对）② 在 `PROPERTY` 的 javadoc / 类注释里注明「YAML 里必须加引号」 |
| **⑥ 影响面** | 共享层 `GoStyleFormLimitMode` ⇒ 被 **10 个 api 全依赖**（两处消费）⇒ **当前受害域 = 0**（全仓 9 个域**无任何域**写字面量 `off` ⇒ favorite 域靠**「整段不写」**拿到 OFF ⇒ 行为零变化）⇒ **未来受害面 = 任何想关闸门的域** |
| **⑦ 是否阻塞本波** | **否**（G2 的 task-api 已用 `mode: "off"` 绕开 ⇒ 本轮不依赖 G4 这次改动 ⇒ 可独立提交）；**但建议尽快修** —— 否则下一个「想关闸门」的域会直接撞上启动失败，且错误信息里的「值：false」极易被误读成「配置写错了值」 |
| **⑧ 🟢 G4 最终回执（2026-10-27 · `c15dfeb` + `e7d14f3`）—— 本单已闭环** | **共享层已落码**：① `GoStyleFormLimitMode.parse` **新增精确别名 `"false"` ⇒ OFF**（**只加这一个**、不放宽成「任意非法值都当 OFF」—— 守门 ⑤ 钉住 `bogus`/`true`/`on`/`yes`/`no`/`n` **仍抛异常**）② `PROPERTY` javadoc 写明「YAML 里 `off` **必须加引号**」＋ YAML 1.1 布尔字面量全表 ③ 异常消息改成自解释（`只允许 off \| false \| raw \| envelope；注意 YAML 里写 off 必须加引号`）④ **新增守门 5 条**（含「生产同款 `YamlPropertySourceLoader` 端到端」＋「真 servlet 上下文 + 未加引号的 yaml 也能起」）⑤ 共享层第九次全量 = **52 模块 / 2470 条 / 0 failures / 0 errors / 4 skipped / 20:22 min**<br>⇒ ⇒ **本单销号**（共享层那半已闭环 ⇒ 待合并后由整合者统一更新共享索引）<br>⇒ ⇒ **对本域零影响**：本域写的是 `mode: "off"`（**带引号**）⇒ ⇒ **加引号在改动前后都是正确写法** ⇒ **不依赖 G4 这次改动** ⇒ 合并前后都安全<br>⇒ ⚠️ **合并后仍要复跑本域 5 条 form 闸门守门**（`TaskApiFormLimitReachabilityTest`）⇒ 确认 `parse` 的新分支**没有改变 OFF 分支的行为**（G4 自测：⑤ 钉住非法值仍抛、`off`/`raw`/`envelope`/null/空串**原语义一字未改** ⇒ 预计全绿 ⇒ **但「预计」不算数 ⇒ 合并后实测**）<br>⇒ 🟢 **G4 对本单前提的独立核实（⑥ 的「当前受害域 = 0」）**：9 个 api 实测 ⇒ `article-api`/`user-api`/`admin-api`/**`task-api`** = `raw` · `comment-api`/`follow-api`/`hot-api`/`like-api`/`message-api` = `envelope` · **`favorite-api` 无 `go-form-limit` 段**（⇒ 缺省 OFF ⇒ 零影响）⇒ **全仓唯一的「未加引号 `mode: off`」是 G4 守门自己那段测试 yaml** ⇒ 与本域取证一致 |

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
| **⑦ 是否阻塞本波** | **否**（G4 已明确「本轮不落码、提请立项」⇒ 本域**只登记**）<br>⚠️ 但**本域已单独修掉一条相邻的、更严重的漂移**（Redis **命令/连接超时**未配 ⇒ 断连挂 60s）⇒ 见 [DRIFT-TASK-RPC-007](rpc.md#🔴-drift-task-rpc-007--redis-命令超时未配--落-lettuce-默认-60s--断连时命令挂-60sgo-是-17s-快速失败) |

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
   ⇒ ⚠️ **若仍红 ⇒ 不是新回归，是毒化未被覆盖 ⇒ 直接找 G4**
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
   ⇒ 配套：新 source 在**本请求没有 deadline** 时必须回落到 **`getClientConfiguration().getCommandTimeout()`（本域 = 3s）** ⇒ 否则命令失去 3s 上限（断连从「3s 快速失败」退化为「无限等待」）⇒ ⇒ **这条已经写进本域台账的 E66 硬约束段（已独立核实并订正其机制表述）**

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

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 2 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **4 条全部重核，1 条「守门已立」被订正为「部分修」**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 `4786eb5` / D-2 `fad4c63` / D-7 = 端口段治理，**task-api 19/19 绿**）
- [x] §4 的派单**登记**（本文件 §4 **FB-01 / FB-02 / FB-03 / FB-05**）＋ **实时轨已发** `session send` 给 `G4Common`（FB-05 位 15）
- [x] **共享面不回写**（合并后由整合者统一更新）

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

1. ~~**FB-02 的 RAG 侧后果（400 vs 200）未跑**~~ ⇒ ✅ **阶段 2 已补 RAG 侧**：`TaskApiFormLimitReachabilityTest`（真 Tomcat）覆盖 3 条 query 变体（>2048 非空值 / `?a=%zz` / `?a=1;b=2`）⇒ 现状全部 **200**（= Go 侧语义，因 Go 侧从不解析 query）⇒ **域侧已闭环**。<br>⚠️ **仍缺 Go 侧活体** ⇒ 这 3 条变体在 Go 真源上的对应响应**未实测** ⇒ 按 §3.3「无双侧活体不得写 L4」⇒ A3 轴整体仍不得标 L4。
2. **FB-01 的「RAG 对尾斜杠返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）；article 域的等价断言见 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiRouteAuthBoundaryTest.java:221`。
3. **A2 的 shedding 在链上**无任何守门证明（现存守门主动关掉降载）⇒ 这是本轮最实的一个「**跑绿 ≠ 对**」形态。
4. **task-rpc 侧 `MqConsumer` / `TraceConsumer`** 是否在两个 sink 上都包了 —— 本轮只读源码未见对应调用点，**未排除也未证实** ⇒ 属 A9 的遗漏面。
5. **未核项**：共享层 `service/common` 的既有结论（`docs/DRIFT/common/{protection,http,deadline,audit}.md`）本轮**未逐条复核** ⇒ 本域只负责「本域是否真接上」，「共享层本身对不对」沿用共享层台账。
