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
| **A1** | 超时 / deadline | ① server：`task-rpc/application.yml:231` `rpc-timeout: 2s` → 消费点 `RpcServerInterceptorFactory.java:80-81` ② 链挂载 `TaskRpcApplication.java:138` ③ HTTP：`task-api/application.yml:127` `http-timeout: 3s` → `HttpMiddlewareFilter.java:52-57` ④ client deadline：`TaskRpcClient.java:105-111`（`min(2000ms, 剩余)`）⑤ SQL 取消 `TaskRpcApplication.java:92-93` | ✅ 一致（三个超时：rpc 2s / http 3s / client 2s，逐个对齐 Go 的 struct tag 默认值） | L1 | 2026-10-27 |
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

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 2 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **4 条全部重核，1 条「守门已立」被订正为「部分修」**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 `4786eb5` / D-2 `fad4c63` / D-7 = 端口段治理，**task-api 19/19 绿**）
- [x] §4 的派单**登记**（本文件 §4 **FB-01 / FB-02 / FB-03 / FB-05**）＋ **实时轨已发** `session send` 给 `G4Common`（FB-05 位 15）
- [x] **共享面不回写**（合并后由整合者统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

1. ~~**FB-02 的 RAG 侧后果（400 vs 200）未跑**~~ ⇒ ✅ **阶段 2 已补 RAG 侧**：`TaskApiFormLimitReachabilityTest`（真 Tomcat）覆盖 3 条 query 变体（>2048 非空值 / `?a=%zz` / `?a=1;b=2`）⇒ 现状全部 **200**（= Go 侧语义，因 Go 侧从不解析 query）⇒ **域侧已闭环**。<br>⚠️ **仍缺 Go 侧活体** ⇒ 这 3 条变体在 Go 真源上的对应响应**未实测** ⇒ 按 §3.3「无双侧活体不得写 L4」⇒ A3 轴整体仍不得标 L4。
2. **FB-01 的「RAG 对尾斜杠返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）；article 域的等价断言见 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiRouteAuthBoundaryTest.java:221`。
3. **A2 的 shedding 在链上**无任何守门证明（现存守门主动关掉降载）⇒ 这是本轮最实的一个「**跑绿 ≠ 对**」形态。
4. **task-rpc 侧 `MqConsumer` / `TraceConsumer`** 是否在两个 sink 上都包了 —— 本轮只读源码未见对应调用点，**未排除也未证实** ⇒ 属 A9 的遗漏面。
5. **未核项**：共享层 `service/common` 的既有结论（`docs/DRIFT/common/{protection,http,deadline,audit}.md`）本轮**未逐条复核** ⇒ 本域只负责「本域是否真接上」，「共享层本身对不对」沿用共享层台账。
