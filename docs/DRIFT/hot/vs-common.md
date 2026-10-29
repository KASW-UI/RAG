<!-- @new 2026-10-27 · `hot-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）**  · **2026-10-27 03:0x G3 阶段 1 复核轮：§1 状态复核 ＋ §2 九轴逐轴填实 ＋ §3 自修项 ＋ §4 派单项（全部带核对日期与证据强度）** -->

# `hot-common` lane 台账 —— 「hot ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/hot/hot-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `hot-common`（**原第 5/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot` |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/hot/{hot-api, hot-rpc, hot-common, hot-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) |
| **写权** | ✅ 可写**本域** `service/hot/**` ＋ `docs/DRIFT/hot/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |
| **本轮复核口径** | **核对日期 2026-10-27** · 阶段 1（**全只读对账**，不落任何代码）· 真源 = `Sea-RideTheWind_golang`（`go.mod:17` 锁 go-zero **v1.10.2**）· **证据强度上限 L3**（本域**无**域级双侧活体探针 —— 仓内 `tools/*-probe/` 的域级实例**只覆盖 favorite**） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取 + 同日复核**）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-HOTAPI-024` | Content-Type charset（共享层 `GoStyleJsonContentTypeAdvice`） | `service/common` | ✅ **已修**（global T-6）⇒ 10 个 api 全生效 | [`api.md`](api.md) |
| `DRIFT-HOTAPI-014` | 跨域移交 `service/common` 的条目 | `service/common` | 🟡 **跨域移交**（nosniff 已实现） | [`api.md`](api.md) |
| ~~`DRIFT-HOTRPC-004`~~ ⇒ ✅ **订正为 `U4`** | client breaker 是否真在链上并会因超时打开（`zrpc/internal/client.go:112-113` ＋ `breakerinterceptor.go:13-18`） | `service/common`（装配层） | ⚠️ **本轮订正编号（2026-10-27）**：权威面里这条是 **`U4`**（`hot/api.md` §9 Unresolved 表的「`U4` \| client breaker 是否真在链上并会因超时打开？」行）⇒ **原记的 `DRIFT-HOTRPC-004` 是另一条**（`hot/rpc.md:350`：**Redis 客户端重试** —— go-redis `MaxRetries` 默认 **3** vs Lettuce **无重试**）⇒ **撞号** ⇒ 已改用 `U4`。<br>✅ **已闭合**（第十轮）—— ⚠️ **本轮复核：该闭合指的是 `hot-api` 侧**（域内有 `GoStyleGrpcClientBreakerWiringTest` ＋ `GoStyleGrpcClientBreakerE2ETest`，均在 `service/hot/hot-api/src/test/**`）⇒ **`hot-rpc`（纯 gRPC 进程）侧仍缺** ⇒ **不是同一件事**，见 §2 的 A2 与 §4-G4-1 | [`api.md`](api.md)（`U4`） |
| `DRIFT-HOTRPC-004`（**另条，勿混**） | Redis 客户端**重试**差异：go-redis `MaxRetries` 默认 **3** vs Lettuce **无重试** | `service/hot/hot-rpc`（Redis 配置） | 🟡 **已登记**（`hot/rpc.md:350` 权威面）—— **本轮新补记**，只为标明「**与上面的 `U4` 是两件事**」⇒ 防止后续按错编号销账 | [`rpc.md`](rpc.md) |
| `D14` / `D25` / `D27` | 三条**移交 `service/common`** 的条目 | `service/common` | ✅ **已全部闭合**（2026-10-26 域内未闭合项已清空） | [`api.md`](api.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地**（global 第十四轮）<br>✅ **本域侧已验**（核对日期 **2026-10-27**）：`hot-api/.../application.yml:141` = `mode: envelope` ⇒ **与 Go 同向**（Go 侧 `service/hot/api/hotcenter.go:53` **注册了** `httpx.SetErrorHandler` ⇒ 解析失败走业务信封 ⇒ **200 + `{code:5001}`**） | [`api.md`](api.md) |
| （域侧 · 非编号） | `hot-rpc` 缺 `baseline-version: 0` | — | ✅ **N/A（不适用）**（核对日期 **2026-10-27** 实测）：`hot-rpc/.../application.yml` **无 `flyway:` 段** ⇒ hot 域**两侧都没有数据库**（Go 侧全域无 gorm，只有 Redis ＋ Kafka）⇒ **本维度无对象可比**，不是缺陷 | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ ✅ **2026-10-27 复核轮已就地订正/确认 6 条**（**`U4` 的编号订正**（原误记 `DRIFT-HOTRPC-004` ⇒ 实为 Redis 重试条）＋ `U4` 的**范围澄清**（闭的是 `hot-api` 侧）＋ `DRIFT-HOTAPI-030` 本域侧已验 ＋ `baseline-version` **判为 N/A**）。
> ⇒ **教训**：同一编号在 **api 侧与 rpc 侧是两条不同的东西** ⇒ 「已闭合」必须带**范围**，否则会掩盖另一侧的缺口。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体** · 核对日期 **2026-10-27**）

> 📌 **本轴的取证方式（与另三域不同，留痕备查）** —— hot 域的取证子任务**两次都因 TPM 限流失败**
> （首次 `rate limit exceeded(TPM) (1039)`；重派后长时间零输出 ⇒ 已取消）⇒ **本表的 hot 结论全部由主会话逐条独立复核**，未采信任何子任务输出。
> 复核命令与结果（核对日期 **2026-10-27**）：
> | 断言 | 复核方式 | 结果 |
> |---|---|---|
> | **A7** proto 行为层零差异 | `diff <(Go 侧 hot.proto 过滤 option/package/注释/空行) <(RAG 侧同名过滤)` | ✅ **零差异**（diff 无输出） |
> | **A9** Go 侧 hot 域无业务 span | `grep -rn "otel.Tracer" / "spanBuilder" $GO_REPO/service/hot` | ✅ **各 0 命中**（只有 `server.Start()` / `serviceGroup.Start()` 误匹配） |
> | **A8** hot-rpc 无库 | `grep -n flyway hot-rpc/.../application.yml` ＋ `ls -d hot-rpc/src/main/resources/db/migration` | ✅ **flyway 行数 0** ＋ **迁移目录不存在** |
> | **A2** hot-rpc 不扫 `com.rag.common` | `grep -n "@SpringBootApplication" HotRpcApplication.java` | ✅ **第 61 行是裸 `@SpringBootApplication`**（无 `scanBasePackages`） |

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | 服务端：`hot-rpc/.../application.yml:138` `observability.rpc-timeout: 2s` ＋ `HotRpcApplication.java:105-108`（`server.intercept(GoStyleRpcProtectionChain.build(...))`）；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:75-82`；api→rpc 出向：`hot-api/.../client/GoStyleGrpcCallDeadline.java:60-63`（`min(2000ms, 剩余预算)`）＋ `client/ArticleRpcClient.java` | ✅ **一致** —— Go `service/hot/rpc/etc/hot.yaml` **未配 `Timeout`** ⇒ 走 go-zero default **2000ms**（`zrpc/config.go:46`）⇒ 两侧同为 **2s**；Go 侧 `hot/rpc/hot.go:32` `DisableNativeRpcTimeout` ＋ `:42` `AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))` ⇒ **超时唯一执行者**，RAG 侧同构（`rpcInterceptorConfigurer` → `server.intercept(...)`）。<br>ℹ️ **出向 gRPC 口径**：Go `context.WithTimeout` 的「取父子较早者」语义 ≡ RAG 的 `min(2000ms, 剩余预算)`（`GoStyleGrpcCallDeadline.java:60-63`；⚠️ 预算耗尽时给 **1ms** 而非原样返回，对应 Go ctx 到期即失败）。<br>ℹ️ **`hot-rpc` 无出向 gRPC 调用**（import 面实测：`service/hot/hot-rpc/**` **无 client 类** ⇒ 该域只消费 Kafka ＋ 写 Redis）⇒ rpc 侧出向维度 **N/A**<br>🆕 **⑦ Redis 连接池轴（2026-10-27 G3 落码）** —— 落点：`hot-rpc/src/main/resources/application.yml:89-95`（`spring.data.redis.lettuce.pool`：`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / **`max-wait: 4s`**）＋ `hot-rpc/pom.xml:117-120`（`commons-pool2`）⇒ 消费点 = Boot 4 `LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig`（`setMaxTotal/setMaxIdle/setMinIdle/setMaxWait`）＋ `DataRedisConnectionConfiguration#isPoolEnabled`（`enabled ?? COMMONS_POOL2_AVAILABLE`）⇒ **判定 = 🔴→✅ 形态对齐（Go 有池 ⇒ RAG 也有池）**：Go `service/hot/rpc/internal/svc/service_context.go:17-20` `redis.NewClient(&redis.Options{Addr, Password})` ⇒ `PoolSize` 未设 ⇒ `go-redis@v9.20.0/options.go:350-351` `= 10 × GOMAXPROCS` ⇒ **Go 有池**；原 RAG 侧**无 pool 段 ＋ pom 无 `commons-pool2`** ⇒ **无池**（共享单连接）⇒ **形态漂移**<br>🔴 **本轮新查出的一条（全仓此前无人登记）：「池耗尽等待上限」** —— Go `go-redis@v9.20.0/internal/pool/pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)` ⇒ `PoolTimeout` = `ReadTimeout+1s` = **4s**；RAG 侧不配 `max-wait` ⇒ commons-pool2 `BaseObjectPoolConfig:59,68` `DEFAULT_MAX_WAIT = -1ms` ⇒ `GenericObjectPool:306` `negativeDuration ? idleObjects.takeFirst()` ⇒ **无限等** ⇒ 且 Lettuce 的 3s 命令超时**此时尚未起算**（`CommandExpiryWriter.write()` 才起算，而借连接在写命令之前）⇒ ⇒ **光加池反而比 Go 更糟** ⇒ ⇒ `max-wait: 4s` 是**必配项**<br>⚠️ **`max-idle` 的口径订正**：Go 侧 `MaxIdleConns` **未设** ⇒ `options.go:224-228` **无 default** ⇒ 0 ⇒ 消费点 `internal/pool/pool.go:1257` ⇒ **0 = 无上限** ⇒ ⇒ 本域取 16 是**表达层**取值（**比 Go 更严**，只影响空闲连接回收，**不影响命令结果**）⇒ **不是**「go-redis 无对应项」<br>🆕 **⑧ Redis 断连行为轴（2026-10-27 G3 · 活体 A/B）** —— 落点：**共享层** `service/common/.../redis/GoStyleRedisClientAutoConfiguration` 的 `REJECT_COMMANDS`（域内**零副本** ⇒ 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内一旦自己声明一个，共享层就退让** ⇒ ⇒ **这条的写权全在共享层**）⇒ **判定 = 🔴→✅ 断连快速失败**：Go 侧 go-redis `MaxRetries` 默认 3 ＋ 重试退避 ⇒ **断连后第一条命令 1669–1808 ms 失败**（4 轮实测）⇒ RAG 侧若用 Lettuce 默认 `DEFAULT` ⇒ **排队到 3s 命令超时**（实测 **3007 ms**）⇒ ⇒ **恰好撞 2s 服务端超时 ⇒ RPC 先超时 ⇒ 客户端看到 1015「服务繁忙」**；配了 `REJECT_COMMANDS` ⇒ ⇒ **1 ms 拒单**（实测）⇒ ⇒ **不撞服务端超时** ⇒ ⇒ **与 Go 的「快速失败」语义等价**（Go 的重试面另属他档，Lettuce 无重试 ⇒ **不混**）⇒ 守门 `HotRedisDisconnectFastRejectGateTest` **2 条**（① customizer 在场且**没被遮蔽** ② 消费点读到 `REJECT_COMMANDS` ③ 活体 A/B） | **池轴（⑦）= L3**（Go 侧 L1 逐行对照 ＋ RAG 侧**真装配**运行证据 ＋ **5 条变异全精确转红**；Go 侧无同时跑探针 ⇒ **不是 L4**；⚠️ **并发活体对拍**原记的阻塞理由「机器级 Docker 端口转发故障」**已于 2026-10-27 19:2x 实测不成立** ⇒ 现在只差「Go 侧同时跑的探针」）<br>**断连轴（⑧）= L4（RAG 腿，仓内可复现）＋ L4-一次性（Go 腿，仓外探针）** ⇒ ⇒ ⚠️ **不是「仓内可复现的双侧活体对拍」** ⇒ ⇒ Go 探针**未入仓**（`tools/**` 属 G4 写权面 ⇒ 已派单）⇒ ⚠️ **仍待验：断连时 gRPC 响应层客户端到底看到什么**（要真发一次 gRPC 调用）⇒ ⚠️ **本轴测的是「池里有空闲连接」形态**，「池耗尽」形态由 ⑦ 的 `max-wait` 覆盖 ⇒ **两条不混** | 2026-10-27（🆕 本轮订正 ⑧） |
| **A2** | gRPC 拦截器链 | `HotRpcApplication.java:61`（`@SpringBootApplication`）`:63-68`（`@Import` 4 类：`RpcServerInterceptorFactory` / `ObservabilityProperties` / `Jackson2ObjectMapperConfiguration` / `GoStyleProtectionConfiguration`）`:105-108`（`@Bean GrpcServerConfigurer` ⇒ `server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps))`）；链序 `service/common/.../protection/GoStyleRpcProtectionChain.java:61-78` | 🔴→✅ **服务端链一致** —— Go `zrpc/server.go:144-163`（Trace→Recover→Stat→Prometheus→Breaker→Shedding→Timeout 逐条按 `c.Middlewares.*` 装配；注册序「append 到尾部 ⇒ 先注册的最外层」，`zrpc/internal/server.go:58`）⇒ RAG recover→breaker→shedding→业务 ＋ 阈值（`cpuThreshold=900`／`cpuSource=SYSTEM`）≡ `zrpc/config.go:47`；Go 侧 native timeout 被 `DisableNativeRpcTimeout` 置 0 ⇒ RAG 不挂 native timeout 也一致。<br>✅ **client 侧 = N/A（本域无出向 gRPC 调用 ⇒ 无熔断可缺）** —— ⚠️ **本轮订正**：原稿此处记 🔴「`hot-rpc` 拿不到 client breaker ⇒ 出向调用无熔断」，**但该 🔴 属过报** ⇒ 实测（核对日期 **2026-10-27**）：`hot-rpc` 出向面**全扫 0 命中** —— `@GrpcClient` = **0** · `ManagedChannel` = **0** · `newBlockingStub` = **0** · `RestTemplate`/`WebClient`/`FeignClient` = **0** · **无 `client/` 目录**（`HotRpcApplication.java` / `bootstrap` / `config` / `domain` / `metrics` / `mq` / `service` 七项）⇒ 唯一出向是 **Redis**（`StringRedisTemplate`/`RedisTemplate` 各 10）＋ Kafka 消费 ⇒ **没有 gRPC 出向调用 ⇒ client breaker 无处可施 ⇒ 本域 N/A**。Go 侧同向（`grep -rn "zrpc.NewClient" $GO_REPO/service/hot` = **0**；唯一的 `zrpc.MustNewClient` 在 **hot 的 api 侧** `service/hot/api/internal/svc/serviceContext.go:31-32`）⇒ **两侧一致**。⚠️ **能力仍在共享层缺位**（`GoStyleGrpcClientBreakerInterceptor` 靠组件扫描 ⇒ rpc 进程扫不到）⇒ **该缺口对 hot 域不可观测，但对 article/comment/favorite 三域真实存在** ⇒ 已作为**全域共性**正式单派给 G4（**单 2**）⇒ hot 域**不单独重复登记** | **L3**（链序 ＋ `@Import`/`@Bean` 装配面）<br>（client 侧的 N/A 判定为 **L1**：否定事实可静态判定 ⇒ 出向面 7 类符号全扫 0 命中） | 2026-10-27 |
| **A3** | HTTP 保护链 | 共享层 `service/common/.../protection/{GoStyleMaxConnsFilter,GoStyleBreakerFilter,GoStyleCpuSheddingFilter,GoStyleMaxBytesFilter}.java`（靠 `hot-api` 的 `@SpringBootApplication(scanBasePackages={…})` 含 `com.rag.common` ⇒ 注册）＋ 共享层 `service/common/.../http/{GoStyleFormValueLimitInterceptor,GoStyleFormLimitWebMvcConfig,GoStyleTomcatParameterLimitCustomizer}.java`；域侧 `hot-api/.../application.yml:138-141`（`mode: envelope`）＋ `rag.protection.*` | ✅ **一致** —— `mode: envelope` **同向**：Go 侧 `service/hot/api/hotcenter.go:53` **注册了** `httpx.SetErrorHandler`（⇒ 解析失败 ⇒ **200 + 业务信封**）⇒ RAG `envelope` 档同形；hot 的 5001 文案 = `"internal server error"`（Go `service/hot/common/errmsg.go:50` `ErrorServerCommon: "internal server error"`）⇒ 两侧文案也同。<br>✅ 四层阈值与 Go tag 默认逐条同值（`rest/config.go:52-56`：`MaxConns=10000` / `MaxBytes=1048576` / `CpuThreshold=900`）＋ **拒绝形状**同为**裸状态码 + 空 body**（Go：`breakerhandler.go:18-42` 裸 503 · `maxconnshandler.go:12` 裸 503 · `maxbyteshandler.go:10` 裸 413 · `sheddinghandler.go:22` 裸 503） | **L3**（yaml ＋ 装配面；Filter 实现体未逐行读） | 2026-10-27 |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude`）＋ `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`（404 = `text/plain; charset=utf-8` ＋ `nosniff` ＋ `"404 page not found\n"`；405 裸状态码 ＋ `Allow`）；域侧 `hot-api/.../advice/GlobalResponseAdvice.java` ＋ `GlobalExceptionHandler.java` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（`Code/Msg/Data` 三字段**无 omitempty**）⇒ `data:null` 一定出现；Go `hot/api/hotcenter.go:45` `httpx.SetOkHandler` ＋ `:53` `httpx.SetErrorHandler` ⇒ **成功与失败都走业务信封、恒 HTTP 200** ≡ RAG（`GlobalResponseAdvice` 包成 `{"code":200,"msg":"OK","data":…}`）；Go handler `getHotArticlesHandler.go:18-19`（`httpx.Parse` 失败 ⇒ `httpx.ErrorCtx`）**被 SetErrorHandler 接管** ⇒ **200 + `{code:5001}`** ≡ RAG `envelope` 档。404/405 裸响应形状 ≡ Go `rest/router/patrouter.go:73,79-82` | **L1**（两侧逐行对照） | 2026-10-27 |
| **A5** | 端口治理 | `hot-api/.../application.yml:34` `server.port: ${HOT_API_PORT:18897}` ＋ `hot-rpc/.../application.yml:85` `grpc.server.port: ${HOT_RPC_GRPC_PORT:8083}` | 🟡 **OBS（部署形态迁移）** —— ✅ **gRPC 8083 与 Go 一致**（Go `service/hot/rpc/etc/hot.yaml:2` `ListenOn: 0.0.0.0:8083`）；⚠️ **Go 是三副本**（`hot.yaml`/`hot2.yaml`/`hot3.yaml` = **8083/8084/8085**）⇒ **RAG 收敛为「同一部署 × 副本」，端口走环境变量**（`HotRpcApplication` 的 yaml 注释明写「Go 三副本是 8083/8084/8085；Java 侧收敛为同一部署 × 副本，端口走环境变量」）⇒ **属部署形态差异，不是行为漂移**。<br>🟡 **HTTP 18897 ≠ Go 8893**（Go `service/hot/api/etc/hotcenter.yaml` `Port: 8893`）⇒ 属 `Infrastructure.md §3` 给 hot 域预占的号段（**已记录的部署口径差异**）；yaml 注释明写「原值 8893 可用 `HOT_API_PORT` 传回」。<br>⚠️ **`hot-rpc` 的 gRPC 端口未在 `Infrastructure.md §3` 预占**（该文件目前只给 hot 预占了 18897/HTTP）⇒ 部署侧待办 | **L3**（yaml 静态；未起进程验绑定） | 2026-10-27 |
| **A6** | errmsg 契约 | `hot-common/.../errmsg/HotErrorCode.java:36`（`public enum HotErrorCode`）＋ `HotErrorMessages.java`；**域内契约测试** `HotErrorCodeContractTest#declarationOrderMatchesGoSourceOrder`（锁死**声明顺序**＝Go const 块顺序）＋ `HotErrorMessagesContractTest#hotCodesKeepTheirOwnMeaningAgainstOtherDomains`（钉死 hot 的 5002/5003/5004 **不与他域同名码混义**） | ✅ **一致** —— Go `service/hot/common/errmsg.go:33-46`（const：`Success=200` / `Error=500` / `CodeServerBusy=1015` / `ErrorServerCommon=5001` / `ErrorNotFound=5002` / `ErrorInvalidParam=5003` / `ErrorUnauthorized=5004` / `ErrorForbidden=5005` / `ErrorAlreadyExist=5006` / `ErrorDbSelect=5007` / `ErrorDbUpdate=5008`）＋ `:48-62`（`codeMsg`：`OK` / `FAIL` / `service busy` / `internal server error` / `record not found` / `invalid parameter` / `unauthorized` / `forbidden` / `already exists` / `database query failed` / `database update failed`）⇒ RAG 侧 `HotErrorCode` 的枚举项（`SUCCESS(200)` / `ERROR(500)` / `CODE_SERVER_BUSY(1015)` / `ERROR_SERVER_COMMON(5001)` / `ERROR_NOT_FOUND(5002)` / `ERROR_INVALID_PARAM(5003)` / `ERROR_UNAUTHORIZED(5004)` …）**逐条对应**，且**声明顺序被测试锁死** ⇒ 比裸顺序对照更强 | **L3**（枚举 ＋ 契约测试源码；未跑双侧探针）⇒ 📌 本域**有契约测试但无双侧探针** ⇒ 可复制 `tools/errmsg-probe/` 骨架升 L4 | 2026-10-27 |
| **A7** | proto 契约 | `service/hot/hot-proto/src/main/proto/hot.proto` | ✅ **一致** —— 与 Go `proto/hot.proto` 做**行为层 diff**（排除 `option` / `package` / 注释 / 空行）⇒ **零差异**（diff 无输出）⇒ message 名 / 字段名 / 字段号 / 类型 / 枚举值 / rpc 签名全部一致；唯一差异在 option/包名块（表达层：删 `option go_package`、加 `java_package` ＋ `java_multiple_files`）⇒ 属 AGENTS.md §3.4 说的「复制粘贴不是翻译、但改动限定在 option 块与空白层面」的可接受范围。<br>🔴 **但无机器回归网**：`tools/difftest/check-proto-static.sh` **只覆盖 `user`/`admin`/`task`** ⇒ **hot 的 proto 契约无自动守门**（AGENTS.md §3.4：10 个 proto 中 8 个无回归网）⇒ **域侧可自修**<br>📌 **🔴 2026-10-29 · G3 订正（本行原写「无机器回归网」＝ **过期**）** —— 同一份文件 **§3 的 H-1 行**（`2026-10-27` 阶段 2）已落码 **`HotProtoContractTest`**（域内文件 ⇒ **在版本控制内**，`git ls-files` 命中 `service/hot/hot-rpc/src/test/resources/proto-contract/{generate.py,hot-proto-contract.expected.txt}`）⇒ ⇒ 「**无自动守门**」这句话是**落码前写的、未回填** ⇒ ⇒ 与 §3 的 H-1 **自相矛盾**（本条即基线 §九 **G-15**）⇒ **以 H-1 为准**：<br>· ✅ **字段级守门已有**：比对的是**生成码的描述符**（`Descriptors.FileDescriptor` ⇒ 真正参与 wire 编码的那份契约），**不是** `.proto` 文本；期望值由 `generate.py` **从 Go 真源 `proto/hot.proto` 生成并冻结**（真源 sha256 记录在案）⇒ **不是手抄的**；<br>· ✅ **契约面守门已有**：`check-surface-parity.py --domain hot` ⇒ **HTTP 端点 Go=1 ≡ RAG=1 · gRPC 方法 Go=5 ≡ RAG=5**（⚠️ **必须显式带 `GO_REPO`** 才指得到真源 —— 见 §5 的「共享面缺陷 S-1」）；<br>· ⚠️ **仍未覆盖**：**wire 字节级**（H-3 ⇒ `tools/**` 共享面 ⇒ 已登记）· **消费方式**（`optional` presence / 字段号硬编码 / Jackson 配置 ⇒ 基线 §九 **G-19**，10 个 proto 全部未取证）。 | **L4**（域内 `HotProtoContractTest`：生成码描述符 ↔ 由真源脚本生成并冻结的基准 ⇒ 字段名/字段号/类型/`repeated`/枚举/rpc 签名；＋ 契约面守门 `check-surface-parity.py --domain hot` ⇒ HTTP 1≡1 · gRPC 5≡5；＋ 🆕 **wire 字节级双侧对拍** `tools/proto-wire-probe/hot/` ⇒ Go 真源生成码 == `expected.txt` == RAG 生成码 **逐字节一致**（13 条用例）＋ 变异 **3/3 精确转红**）⇒ ✅ 满足基线 §1.1 的 **R4 ⑦⑧** | 2026-10-29 · G3 订正（原记 2026-10-27） |
| **A8** | DDL 时间列 / 类型 | — | ✅ **N/A（本维度无对象可比）** —— Go 侧 `service/hot` **全域无 gorm**（只有 Redis ＋ Kafka）；RAG 侧 `hot-rpc` **无 `db/migration` 目录**、`hot-rpc/.../application.yml` **无 `flyway:` 段** ⇒ **两侧都是 0 张表**。<br>⚠️ **「两侧都是 0 张表」不等于「DDL 已对齐」** —— 结论是「**本维度无对象可比**」（与 `check-ddl-static.py --domain hot` 的 `EXIT=3` 口径一致：「⚠️ 本域两侧都没有业务表 ⇒ DDL 维度不适用（N/A），不是『已对齐』」）⇒ **本轴不得写成「已对齐」** | **L3**（目录 ＋ yaml 静态实测） | 2026-10-27 |
| **A9** | trace / otel | 共享层 `service/common/.../observability/ObservabilityConstants.java:57-116`（`sea.*` 属性）＋ `RpcInterceptor.java:207-336`（SERVER span ＋ `rpc.system`/`rpc.method`/`rpc.grpc.status_code`）＋ `service/common/.../http/GoStyleTraceparentFilter.java:188-191`（`@Component` ＋ `@Order(HIGHEST_PRECEDENCE+3)`）；Kafka 消费侧 `hot-rpc/.../mq/HotEventConsumer.java:98-99`（`@KafkaListener`）＋ 共享层 `MqConsumer.java:71-123`（SpanKind.CONSUMER ＋ attrs ＋ `markTimeout/markSystemError/markDurationAndSlow`） | ✅ **一致** —— 共享层的 span 属性与 `Traceparent` 传播**已就位**；⚠️ **关键差异点已核清**：**Go 侧 hot 域「无业务 span」**（实测：`service/hot/**` 全域 grep `otel.Tracer` / `spanBuilder` / `Start(` **零命中**，只有 `server.Start()` / `serviceGroup.Start()` 的误匹配）⇒ **两侧都无业务 span ⇒ 不是漂移**（与 comment/favorite 域不同 —— 那两域 Go 侧有 `audit.*`/`biz.*` 业务 span ⇒ 属真实缺口，见那两域台账）。<br>✅ `Traceparent` 传播 ≡ Go `rest/handler/tracehandler.go:47-63`（Extract→Start→Inject 到响应头→next）＋ `rest/engine.go:245-251`（404 也挂 TraceHandler）。<br>ℹ️ **属性名是 `sea.*`（共享层通用前缀），不是 `audit.*`** —— `audit.*` 只存在于 users/admin 域 ⇒ **本轴的「`audit.*`」措辞对 hot = N/A**（见 §4 的共享面订正项） | **L3**（Go 侧 L1 ＋ RAG 侧装配面；否定事实「Go 侧无业务 span」可静态判定） | 2026-10-27 |

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **H-1** | ✅ **补 proto 契约回归网（2026-10-27 阶段 2 已落码）** | **新增** `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoContractTest.java` ＋ `src/test/resources/proto-contract/{hot-proto-contract.expected.txt, generate.py}` | **已落码**（域内文件，**未改共享脚本**）：<br>① **基准由脚本从 Go 真源生成** —— `generate.py` 解析 `Sea-RideTheWind_golang/proto/hot.proto` ⇒ 冻结成 `hot-proto-contract.expected.txt`（真源 sha256 `4ede71009039756b…`）⇒ **期望值不是手抄的**；<br>② **比对「生成码的描述符」而不是「.proto 文本」**（读 `Descriptors.FileDescriptor` ⇒ 拿到真正参与 wire 编码的那份契约）；<br>③ 钉住：package · **11 个 message 的字段名/字段号/类型** · `repeated`（`items` / `articles`）· **5 个 rpc 的方法名与出入参**（`HotService`）。<br>⇒ **实测绿**：`mvn -o -pl service/hot/hot-rpc test -Dtest=HotProtoContractTest` ⇒ **EXIT=0**（1 条用例）。<br>⚠️ **覆盖边界**：不覆盖 gRPC 帧 / HTTP2 帧；**字节级**对拍另需 wire 探针（favorite 域有 `tools/proto-wire-probe/favorite/`，hot 域**没有** ⇒ 属 `tools/**` 共享面 ⇒ 已登记，见 §3-H-3）<br>⚠️ **放在 hot-rpc 而不是 hot-proto**：`hot-proto` 模块**一行 junit 依赖都没有** ⇒ 往那儿加测试要先改 pom（与 favorite/article 域同款理由） |
| **H-2** | ✅ **已闭合（2026-10-29 · G3 域侧落码）** —— A6 升 L4：**错误码契约的双侧逐字节证据已拿到** | `tools/errmsg-probe/hot/`（**域内落码**） | ✅ **已落码 ＋ 已取证**：落仓 `tools/errmsg-probe/hot/`（`run-parity.sh` + `mutate-parity.sh` + `go/errmsg_probe.go` + `go/main.go` + `java/Probe.java` + `README.md`）⇒ 对拍 **EXIT=0**（Go / Java sha256 均 `bc1db3bf4c9090ce100a2bc76ec6e932f47ecb236c4379ee4fb1129686481bb3` · 34 行 = CONST 11 + TABLE 11 + FALLBACK 12）⇒ 变异 **4/4 精确转红** ＋ 还原后重新转绿 ⇒ **非假绿** ⇒ 证据强度 **L4** ⇒ ⇒ **`hot-common` 的 R 判定由 R3 升 R4**（基线 §九 **G-5 可撤**）⇒ 详见 §4 的 **G4-18** 行 ＋ [`common.md`](common.md) §「2026-10-29 · G3 阶段 1 回填：**G-5 闭合**」<br>⚠️ **归属订正（2026-10-29）**：本行原写「`tools/**` 是**共享面**（不在 G3 写权面）⇒ 本项**不是「域侧自修」**」⇒ **该归属判断已作废** —— 按 AGENTS.md §5.3f「**共享判据 = 依赖度数，不是目录位置**」：`tools/errmsg-probe/hot/` **只被 hot 域引用** ⇒ **真私有** ⇒ ⇒ **G3 域侧落码合法**（写权守门 `check-wave-lane-ownership.py` 的 `SHARED` 清单里只有 `tools/difftest/README.md` 一条） |
| **H-3** | ✅ **已闭合（2026-10-29 · G3 域侧落码）** —— A7 升 L4：proto **消息体** wire 字节双侧对拍已拿到 | `tools/proto-wire-probe/hot/`（**域内落码**） | 同款骨架（favorite 已有）⇒ 拿 wire 字节级证据<br>⚠️ **归属订正（2026-10-29）**：本行原写「`tools/**` 是**共享面**（不在 G3 写权面）⇒ G3 只登记不落码」⇒ **该归属判断同样作废** —— 按 AGENTS.md §5.3f：`tools/proto-wire-probe/hot/` **只被 hot 域引用** ⇒ **真私有** ⇒ ⇒ **G3 域侧落码合法**（⇒ 本项**不再是「登记给收口执行者」**，而是**域侧自修项**）<br>✅ **已落码 ＋ 已取证**（2026-10-29 · G3 · Jason 批准开工后）：落仓 `tools/proto-wire-probe/hot/`（`run.sh` + `mutate-wire.sh` + `go/main.go` + `expected.txt` + `README.md`）＋ Java 侧 `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoWireParityTest.java`（**7 例**）⇒ 对拍 **EXIT=0**（Go 真源生成码 == `expected.txt` == RAG 生成码，**13 条用例逐字节一致**；真源 `hot.pb.go` sha256 `c5b28a8415151ca3…`）⇒ 变异 **3/3 精确转红**（M1 `expected.txt` 被改 ⇒ Go 侧 diff exit 1 · M2 Go 探针取值被改 ⇒ Go 侧 diff exit 1 · M3 **RAG 侧 `hot.proto` 字段号被改** ⇒ Java 侧断言 exit 2）＋ 还原后重新转绿 ⇒ **非假绿** ⇒ 证据强度 **L4** ⇒ ⇒ **`hot-proto` 的 R 判定由 R3 升 R4**（基线 §1.1 的 **R4 ⑦⑧** ⇒ **双侧同时运行的探针对拍通过且在版本控制内可复算** ＋ **有变异验证记录**）⇒ ⇒ 登记给**合并收口执行者**改基线（`docs/PhaseGoal-judge.md` §四 A-2 的 `hot-proto` 行 = **SHARED 面**，本 lane 不碰）<br>⚠️ **为什么这次能落码**（与上一轮的差别）：上一轮是**停止令**要求「做完手上这件事就停下、不扩范围」⇒ 本轮 Jason 已明确批准开工（调度员 §三 line 93「批量批准开工」把 H-A1 列入 G3 的候选任务）⇒ ⇒ **停止令已解除** ⇒ ⇒ 且归属早已按 §5.3f 订正为**域侧自修项**（`tools/proto-wire-probe/hot/` 只被 hot 域引用 ⇒ 真私有 ⇒ G3 可写）<br>✅ **现状（2026-10-29 更新）**：hot 域的 A7 证据 = **descriptor 级 L3**（`HotProtoContractTest` ＋ 真源生成的冻结基准，sha256 `4ede71009039756b9df11d1e02251ef88199ec7447cf2764f4e8de540685d3b6`）＋ **JSON 字段序的 wire 字节级已在第五轮双侧活体逐字节相同**（`api.md` D15 / CASE-15d）＋ 🆕 **proto 消息体字节的双侧对拍**（`tools/proto-wire-probe/hot/`）⇒ ⇒ **A7 = L4**<br>⚠️ **仍未覆盖**（**不是本探针能覆盖的**）：**gRPC 帧 / HTTP2 帧**（需两侧同时起服务）· **proto 的服务定义**（`HotService` 4 个 rpc 方法名/路径 ⇒ 由 `check-proto-static.sh` 负责）· **proto 的消费方式**（`optional` presence / 字段号硬编码 / Jackson 配置 ⇒ 基线 §九 **G-19**，**10 个 proto 全部**未取证） |
| **H-4** | 🟡 **部署侧**：`hot-rpc` 的 gRPC 端口未在 `Infrastructure.md §3` 预占 | 部署配置（**非本仓写权面**） | `hot-rpc/.../application.yml:83-85` 的注释自记「Infrastructure.md §3 目前只给 hot 预占了 18897(HTTP)，**gRPC 端口未分配**」⇒ **记录 + 上报**，非代码缺陷 |
| **H-5** | ✅ A3/A4/A5/A9 —— **无需改** | — | 四轴实测一致（端口差异属已记录的部署口径；Go 侧 hot 无业务 span ⇒ 无需补埋点 ⇒ **补了反而是新增 Go 没有的行为**） |

| **H-6** | ✅ **本域不涉及排序桥下沉** | — | `hot-rpc` **本来就没有** `MybatisPlusOrderingBridgeAutoConfiguration`（实测：**无 flyway 段、无 MP 依赖 ⇒ 两侧都无库**）⇒ 共享层桥对 hot 域是 **N/A**（排序边因无 MP 被自动忽略 ⇒ 无害）⇒ **本域无落码项** |
| **H-7** | ✅ **N/A —— hot-api 两侧都没有鉴权 ⇒ 无「验签被计入 3s 预算」这条漂移**（**不落码**，登记以防后人误派） | — | **本轮随 G1 的跨域推广同步一并核**：① **Go 侧**：`service/hot/api/internal/handler/routes.go` **只有一个** `AddRoutes` 块且**不带** `rest.WithJwt`（实测块1 `WithJwt=False`，路径 `/articles`，前缀 `/hot/v1`）⇒ **Go 的 hot-api 公开可读**；② **RAG 侧**：`service/hot/hot-api` **没有 `WebMvcConfig`、没有 JWT 拦截器**（实测：全模块无 `jwt`/`blacklist` 相关类；其 `HotArticleController` 的 javadoc 明写「引入 `JwtAuthInterceptor` 会让『匿名可拉热榜』变成『必须带 token』」⇒ **刻意保持匿名**）⇒ ⇒ **两侧一致 ⇒ 不存在「验签」这个环节 ⇒ 本条 N/A**。⚠️ **别把「RAG 没有鉴权」当成漂移**：热榜本就该匿名读（Go 侧同样不挂 `rest.WithJwt`）⇒ **G1 的推广清单里含 `hot-api`，但对 hot 域不适用** ⇒ 已在同批回复 G1 说明 ⇒ **本域不落码** |
| **H-8** | ✅ **hot-rpc 的 Redis TCP 连接超时**（Go `DialTimeout` 5s vs Lettuce 默认 **10s** ⇒ 漂移 2 倍） | `service/hot/hot-rpc/src/main/resources/application.yml:39` ＋ **新增** `src/test/java/com/rag/hot/hot/rpc/config/HotRpcRedisTimeoutContractTest.java` | **已落码（域侧自修，2026-10-27 · commit `0c6295c`）**：yml 加 `connect-timeout: 5s`。<br>**Go 出处（消费点）**：`service/hot/rpc/internal/svc/service_context.go:17-19` `redis.NewClient(&redis.Options{Addr: c.RedisConf.Host, Password: c.RedisConf.Pass})` ⇒ **不设 DialTimeout** → go-redis v9.20.0 `options.go:338-340` ⇒ **5s**。<br>**RAG 侧**：Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31 ⇒ **只有** `spring.data.redis.connect-timeout` 非空时才设 `SocketOptions.connectTimeout` ⇒ 不配 ⇒ Lettuce `DEFAULT_CONNECT_TIMEOUT = 10s`；且 `spring.data.redis.timeout` **只**喂命令超时（Boot 2/3 才是共用）。<br>**为什么是域侧、不派 G4**：共享层 `GoStyleRedisClientAutoConfiguration` **不碰** `socketOptions` ⇒ 连接超时由域内 yml 决定。<br>**守门**：**真容器**（真实 yml → 真 `LettuceConnectionFactory` → 断言**消费点**）**1/1 绿 ＋ 变异 3/3 精确变红**。🔴 **可观察性最强**：hot-rpc 是**纯 gRPC（无 HTTP 预算兜底）**，且 Redis 路径就是 `get_hot_articles_logic.go:43` 的 `ZRevRangeWithScores`（Top-K 读）⇒ 差异**裸露**。<br>📌 **G4 派单回执（2026-10-28 · G3）**：G4 第七封信把本条列为 **R6**（「你域的 yml 没有 `connect-timeout` ⇒ 连接超时 10s ⇒ 比 Go 的 5s 松 2 倍」）⇒ ⇒ **该派单在本域已过期**：本条落码（`0c6295c`，2026-10-27）**早于**派单 ⇒ ⇒ **不重做、不重派**。⇒ ⇒ **复验（2026-10-28）**：守门 `HotRpcRedisTimeoutContractTest`（**真容器** ⇒ 真 yml ⇒ 真 `LettuceConnectionFactory` ⇒ 断言**消费点** `ClientOptions.getSocketOptions().getConnectTimeout()` == 5s ＋ `getCommandTimeout()` == 3s）**绿** ＋ **变异 3/3 精确变红**（`expected: 5S but was: 10S`）⇒ ⇒ **判据活着 ⇒ 差异不存在**。⚠️ **版本差（免得下一轮再踩）**：G4 测的是 **Boot 4.0.3 / lettuce-core 6.1.8** ⇒ ⇒ **本域树上是 Boot 4.0.8 / lettuce-core 6.8.2.RELEASE** ⇒ ⇒ **两个版本都验过**（lettuce `SocketOptions` 静态块 `ldc2_w long 10l → Duration.ofSeconds` ⇒ 默认 10s ✔；Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` offset 9→31 = `getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` ⇒ **只在属性非空时才设** ✔）⇒ ⇒ **结论在两版本都成立 ⇒ ⇒ 是基线过期，不是版本差异**。 |
| **H-9** | ✅ **hot-rpc 的 Redis 连接池形态 + 池耗尽等待上限**（Go **有池** vs RAG **无池**；池耗尽 Go **4s** 失败 vs Lettuce 默认**无限等**） | `service/hot/hot-rpc/src/main/resources/application.yml:89-95` ＋ `service/hot/hot-rpc/pom.xml:117-120` ＋ **新增** `src/test/java/com/rag/hot/hot/rpc/config/HotRpcRedisPoolContractTest.java` | **已落码（域侧自修 · 2026-10-27 · G3）**：yml 加 `spring.data.redis.lettuce.pool`（`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / **`max-wait: 4s`**）＋ pom 加 `commons-pool2`（不写 `<version>`，BOM 管 ⇒ 实测 **2.12.1**）。<br>**Go 出处（消费点）**：`service/hot/rpc/internal/svc/service_context.go:17-20` `redis.NewClient(&redis.Options{Addr, Password})` ⇒ `PoolSize` 未设 ⇒ `go-redis@v9.20.0/options.go:350-351` `= 10 × GOMAXPROCS` ⇒ **Go 有池**；`MinIdleConns` 未设 ⇒ `options.go:218-222` 无 default ⇒ **0**（⇒ 本域 `min-idle: 0` 是**精确对齐**，**不是**别域的 4）；`PoolTimeout` 未设 ⇒ `options.go:380-386` `= ReadTimeout+1s` = **4s** ⇒ 消费点 `internal/pool/pool.go:1113`。<br>**为什么是域侧、不派 G4**：共享层 `GoStyleRedisClientAutoConfiguration` **只设 `disconnectedBehavior(REJECT_COMMANDS)`** ⇒ **不碰 `pool.*` / 不碰 `timeoutOptions`** ⇒ 池形态完全由域内 yml ＋ 域内 classpath 决定。<br>**守门**：**4 条**（`HotRpcRedisPoolContractTest`）—— 起**真** `ApplicationContextRunner` ＋ Boot **真** `DataRedisAutoConfiguration` ⇒ 断言**真装配**的 `LettucePoolingClientConfiguration.getPoolConfig()`（**不读 yml 文本**）⇒ ④ 含**阳性对照**（commons-pool2 自己的默认 `maxWait` = -1ms）。<br>**变异 5 条全部精确转红**：`enabled→false`（3 红）/ `max-active→65`（1 红）/ `min-idle→1`（1 红）/ **删 `max-wait`（1 红，`expected: 4S but was: -0.001S` ⇒ 实测到「无限等」）** / **抽掉 pom 的 `commons-pool2`（1 红，`NoClassDefFoundError: org/apache/commons/pool2/impl/GenericObjectPoolConfig` ⇒ 启动期直接失败）**。〔📌 **2026-10-28 G3 订正（取证路径 + 该条证据强度升级）**：最后一条的**结论是对的**，但**它记的取证方法跑不出这个结果** —— 本守门类**直接 `import org.apache.commons.pool2.impl.GenericObjectPoolConfig`**（`HotRpcRedisPoolContractTest.java:8`）⇒ ⇒ 抽依赖 ⇒ **test-compile 先失败** ⇒ ⇒ 它**不是**「守门识别运行时退化」的证据 ⇒ ⇒ **本轮已用「应用启动探针」重验并把该条升级为 L4**：把池守门挪走 ＋ 抽 pom 依赖 ＋ 跑**真 Spring 上下文** `HotRpcSpringContextWiringTest`（`@SpringBootTest(webEnvironment = NONE)`）⇒ ⇒ **8 errors** ＋ `Caused by: java.lang.NoClassDefFoundError: org/apache/commons/pool2/impl/GenericObjectPoolConfig` ＋ `Caused by: ClassNotFoundException: org.apache.commons.pool2.impl.GenericObjectPoolConfig` ⇒ 链路 = `redisConnectionFactory`（`LettuceConnectionConfiguration`）⇒ `Factory method 'redisConnectionFactory' threw exception with message: org/apache/commons/pool2/impl/GenericObjectPoolConfig` ⇒ ⇒ **启动期硬失败**（**不是**「静默变无池」）⇒ ⇒ **原结论原样保留，只订正取证路径**（2026-10-28 00:0x · G3 实测）⇒ 同款探针已用于 favorite-api ⇒ 详见 [`favorite/api.md` §19.6](../favorite/api.md#196-变异验证4-条--2026-10-28-000x--本轮现跑) ＋ [§19](../favorite/api.md#19-✅-favorite-api-的-redis-连接池形态已对齐-go无池--有池-2026-10-28g3)。〕<br>⚠️ **证据强度 L3**（Go 侧 L1 逐行 ＋ RAG 侧真装配运行证据）⇒ **不是 L4**（Go 侧无同时跑探针；⚠️ **并发活体对拍**原记的阻塞理由「机器级 Docker 端口转发故障」**已于 2026-10-27 19:2x 实测不成立** ⇒ 现在只差「Go 侧同时跑的探针」）。<br>⚠️ **残留**：① `max-active: 64` vs Go 的 `10×核数`（本机 100）⇒ **性能面**数量级差异；② `max-idle: 16` vs Go 的**无上限** ⇒ 表达层取值（比 Go 更严）；③ Go 的 `MaxActiveConns` 未设 ⇒ **Go 无总量硬上限**，RAG 的 64 是表达层硬上限；④ **`DRIFT-HOTRPC-004` 的重试分布**未与本条一起活体验证（§十.3 要求「复开时必须与重试那条一起验」⇒ 本轮只验了三个位）⇒ **仍挂起**。<br>📌 **跨域发现（同机制，不止本域）**：`comment-rpc`（本域）`min-idle: 4` 应为 **0** ＋ **缺 `max-wait`**；`task-rpc` / `like-rpc`（G2 域）**缺 `max-wait`**；`favorite-api`（本域）无池 ⇒ **已通报 G2 / G4**。 |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 🟡 **共享层 U+FFFD 反馈单只登记一次** ⇒ 见 [`comment/vs-common.md`](../../DRIFT/comment/vs-common.md) §4-**G4-4**（`service/common` 两个测试类，各丢 1 个汉字，纯注释）⇒ **本域不重复派单**。

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。
> **回执规格（2026-10-27 双方议定 · G4 已写入 `docs/DRIFT/global/vs-common.md` §4.1，commit `6e3e612`）**：回执 **第 4 项 = 「字段本身写全」** ⇒ ① 跑没跑 ② **未跑时把理由写进第 4 项字段本身**（不写在正文里）③ 跑了则给 **模块数 / 用例数 / failures / errors / skipped / 耗时** ④ **三门禁结果**。> ⚠️ 另外三项不变：**① commit 号 ② 逐个 `文件:行号` 改动清单 ③ 触及的 A1~A9 轴 + Go 出处**。
> ⚠️ **落点必须独立核准**（不要照抄台账快照）。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-1** | ✅ **共享层能力缺口真实存在，但 `hot` 域本身 = N/A** ⇒ 本条**已按全域共性正式派单**，hot 域**不单独重复登记** | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` ＋ `GoStyleProtectionConfiguration.java:51-53` | **现象（共享层）**：该拦截器是 `@Component` ＋ `@GrpcGlobalClientInterceptor` ⇒ **只能组件扫描**；而 **rpc 进程刻意不扫 `com.rag.common`**（`HotRpcApplication.java:61` 无 `scanBasePackages`；同文件 javadoc 明写「不能扫，HttpMiddlewareFilter 是 servlet Filter，纯 gRPC 进程全包扫描会把 web 类拖进来」）⇒ rpc 侧**永远拿不到 client breaker**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）⇒ **共享层能力缺口成立**。<br>⚠️ **但 hot 域 = N/A（本轮订正）**：`hot-rpc` **无任何 gRPC 出向调用**（`@GrpcClient`/`ManagedChannel`/`newBlockingStub` 全 0 ＋ 无 `client/` 目录 ⇒ 见 §2-A2 的全扫证据）⇒ **hot 域不可观测**。<br>**实测影响面（核对日期 2026-10-27 · 已订正）**：**G3 域内 3/4 真实命中** —— `article-rpc`（`@GrpcClient` = **5**）· `comment-rpc`（= **2**）· `favorite-rpc`（= **2**）⇒ 这三个域的出向调用**确实无熔断**；**`hot-rpc`（= 0）⇒ N/A** ⇒ 原稿「G3 域内 4/4 命中」**属过报，已更正**（并已向 G4 发更正）。⇒ **建议**：共享层提供一个 **rpc 进程可用的装配入口**（改为可被 `@Import` 引入，或由 `RpcServerInterceptorFactory` 一并产出 client 侧拦截器 —— ⚠️ **后者落点 100% 在 `service/common`**，因实测 **10/10 rpc 组件已 `@Import` `RpcServerInterceptorFactory`** ⇒ 前者需改 10 个**域侧** Application 类 ⇒ **越 G4 写权**），并补一条「rpc 侧出向调用确实经过 breaker」的**装配层**测试（AGENTS.md §3.3.3：注解存在 ≠ 机制生效）。**是否阻塞本波**：否 |
| **G4-2** | 🟡 **A9 轴定义的措辞缺陷**（共享面 `_cross`） | `docs/DRIFT/_cross/domain-common-lanes.md` 的 A9 行 | 轴定义写「span 属性 **`audit.*`**」⇒ 实测：**共享层 = `sea.*`**（`ObservabilityConstants.java:57-116`）· **admin = `audit.*`** · **favorite = `biz.*`** · **hot 域两侧都无业务 span** ⇒ 轴定义应按域区分 ⇒ **共享面文件，G3 不写** ⇒ 已请 G4 登记进 `global/vs-common.md` 的建议项段 |
| **G4-3** | ℹ️ **A8 = N/A 的登记口径** | `docs/DRIFT/_cross/ddl-type-divergences.md` | 本域「两侧 0 张表 ⇒ DDL 维度 N/A」⇒ 建议在跨域 DDL 汇总里**把 hot 显式标为 N/A**（而非「已对齐」）⇒ 共享面 ⇒ 合并收口执行者/G4 处理 |
| **G4-18** | ✅ **已由 G3 域侧闭合**（2026-10-29）—— 探针落仓 `tools/errmsg-probe/hot/` ⇒ 对拍 **EXIT=0**（双侧 sha256 一致）· 变异 **4/4 转红** ⇒ ⇒ **`hot-common` 的 R4 成立**（基线 §九 **G-5** 可撤）｜ 原记录：🟡 `hot-common` 的双侧对拍探针在 `/tmp` ⇒ 不可复算 | **现象**：基线 §九 G-5 写「`hot-common` 的对拍探针在 `/tmp`（`f23fe570…`）⇒ **不可复算**」⇒ ⇒ 本轮实测：`/tmp` 下**已无**该探针 ⇒ ⇒ 「声明的结论」今天**既跑不出来、也没了载体**。<br>**Go 侧 `文件:行号`**：`service/hot/common/errmsg.go:33-46`（const 块：11 个码）＋ `:48-62`（`codeMsg` 文案表）<br>**RAG 侧现状**：`service/hot/hot-common/src/main/java/com/rag/hot/hot/common/errmsg/HotErrorCode.java` ＋ `HotErrorMessages.java` ＋ **域内契约测试** `HotErrorCodeContractTest`（**期望值逐条抄自 Go `errmsg.go:34-46`**，断言 11 个码 + **声明顺序** + 重复）＋ `HotErrorMessagesContractTest` ⇒ ⇒ **域内已有可复算的字段级断言**；缺的是**双侧逐字节**那一层<br>**证据强度**：**L3**（域内契约测试的期望值有 Go 出处且**在版本控制内**）＋ **E4\***（双侧逐字节的那一层 = 声明值，探针不可复算 ⇒ **不可复算**）<br>**建议修法**：按 `tools/errmsg-probe/favorite/run-parity.sh` 的骨架复制一份 `tools/errmsg-probe/hot/run-parity.sh`（Go 腿直调真源 `errmsg.go` + Java 腿跑 `HotErrorCodeContractTest` 的同一份期望表 ⇒ 逐字节 diff）⇒ ⇒ **原判「落点 `tools/**` = 共享面 ⇒ G3 不写 ⇒ 登记给 G4 / 合并收口执行者」已订正**（2026-10-29）：按 AGENTS.md §5.3f「**共享判据 = 依赖度数，不是目录位置**」，`tools/errmsg-probe/hot/` **只被 hot 域引用** ⇒ **真私有** ⇒ ⇒ G3 域侧落码（写权守门 `check-wave-lane-ownership.py` 的 `SHARED` 清单里也只有 `tools/difftest/README.md` 一条）<br>**影响面**：`hot-common` 的 R 判定（R3 ⇒ R4 的唯一缺口）<br>**是否阻塞本波**：**否**（基线已按 R3 计入 ⇒ 不阻断阶段一）｜ **✅ 2026-10-29 闭合**：落仓 `tools/errmsg-probe/hot/`（`run-parity.sh` + `mutate-parity.sh` + `go/errmsg_probe.go` + `go/main.go` + `java/Probe.java` + `README.md`）⇒ 对拍 **EXIT=0**（Go / Java sha256 均 `bc1db3bf4c9090ce100a2bc76ec6e932f47ecb236c4379ee4fb1129686481bb3` · 34 行 = CONST 11 + TABLE 11 + FALLBACK 12）· 变异 **4/4 精确转红**（M1 码值 · M2 文案 · M3 回落语义 · M4 表多一条）+ 还原后重新转绿 ⇒ **非假绿** ⇒ 证据强度 **L4** ⇒ 详见 [`common.md`](common.md) §「2026-10-29 · G3 阶段 1 回填：**G-5 闭合**」 |
| **G4-19** | 🟡 **共享脚本 `check-surface-parity.py` 在工作树里指错 Go 真源**（**跨 G1~G4 全域**）—— hot 域是**最先撞上**的那个（它会**直接 EXIT=1**） | **现象**：`python3 tools/difftest/check-surface-parity.py --domain hot`（在**工作树**里）⇒ 报「🔴 Go routes.go 不存在: …/RAG.worktrees/Sea-RideTheWind_golang/…」⇒ **EXIT=1** ⇒ ⇒ 且脚本把「找不到 Go 仓」当成「Go 侧 0 个端点」⇒ ⇒ 对别的域可能变成 **0 vs 0 的假绿**。<br>**Go 侧 `文件:行号`**：**不适用**（RAG 侧共享脚本缺陷）<br>**RAG 侧现状**：`tools/difftest/check-surface-parity.py:69-70`（`GO_REPO` 默认 = `dirname(REPO)/Sea-RideTheWind_golang` ⇒ **工作树里恒指错**）<br>**证据强度**：**L4**（实测：`hot` EXIT=1；显式带 `GO_REPO` ⇒ **EXIT=0**，**HTTP Go=1 ≡ RAG=1**（Go 侧 JWT 保护 0 条 ⇒ 两侧都匿名）· **gRPC 方法 Go=5 ≡ RAG=5**）<br>**建议修法**：找不到 Go 仓 ⇒ **显式报错退出**；默认路径改为**向上搜索**；`docs/PhaseGoal/g{1..4}.md §5` 的收口命令注明带 `GO_REPO`<br>**影响面**：G1~G4 全域的 X-8 / X-9 判定命令<br>**是否阻塞本波**：**否**（G3 已用显式 `GO_REPO` 取得 hot 域真结论）<br>📌 **完整反馈单见 article 域的 G4-10**（同一份，G3 一次登记，不在四域重复）｜ **📌 2026-10-29 补记：同族缺陷已确认第 3 例** —— G3 本轮新写的 `tools/errmsg-probe/hot/run-parity.sh` **初版也踩了同一个坑**（`GO_REPO` 默认写成「RAG 的同级兄弟目录」⇒ 在 worktree 里解析成 `RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在** ⇒ 探针 exit 2）⇒ ⇒ **已按「向上逐层找 6 层」改掉**（与 `tools/ddl-parity-probe/favorite/run.sh` 的 `default_go_repo` 同款）⇒ ⇒ 该族缺陷**不是单点**，是**「任何自己解析真源路径的新脚本都会重犯」** ⇒ ⇒ 建议 G4 的修法**必须落到「共享解析函数」**而不是逐个脚本改，否则下一个新脚本还会重犯 |

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）—— ✅ **2026-10-27 阶段 2 已完成**：`mvn -o -pl service/hot/hot-rpc,... -am test` ⇒ **hot-rpc 96 条 / 0 failures / 0 errors / 0 skipped**（含 `HotRpcJarExecutableWiringTest`（**产物可执行性**）＋ `HotGrpcInterceptorWiringTest`（**真 gRPC server**）＋ `HotRpcSpringContextWiringTest`（**真 Spring 上下文**））。⚠️ **必须带 `-am`**（否则拿旧共享层跑，见文末「本轮踩坑」）。
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（**2026-10-27**）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 🟡 **部分达成（2026-10-29 订正）**：✅ **H-2 已闭合** —— `tools/errmsg-probe/hot/run-parity.sh` **EXIT=0**（A6 错误码契约 ⇒ **L4**）＋ 变异 **4/4 精确转红** ⇒ `hot-common` 升 **R4**（基线 §九 **G-5 可撤**）；✅ **H-3 已闭合** —— proto **消息体字节**那一层的双侧对拍已落码（`tools/proto-wire-probe/hot/`，**EXIT=0** ＋ 变异 **3/3 精确转红**，见 §3-H-3）⇒ ⇒ **本域 A6 = L4 · A7 = L4** ⇒ ⇒ **两个探针都在版本控制内、现在可复算** ⇒ ⇒ 满足基线 §1.1 的 **R4 ⑦⑧**⇒ **不再**是「本域无现成域级探针」
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）
- [x] §3 的域侧修法**落码 + 守门**（含变异验证）—— 🟡 **阶段 2 落码部分已完成**（口径：`mvn -o -pl service/hot/hot-rpc,... -am test` ⇒ hot-rpc **96 / 0 failures**）：
  - **H-1** ✅ proto 契约回归网（新增 `HotProtoContractTest` ＋ 域内 `generate.py` ＋ 冻结 `expected.txt`，**真源 sha256 `4ede71009039756b…`** ⇒ 1/1 绿）
  - ✅ **H-2**（A6 升 L4：复制 errmsg 探针骨架到 `hot/`）＝ **2026-10-29 已落码 ＋ 已取证**（`tools/errmsg-probe/hot/`，对拍 EXIT=0 ＋ 变异 4/4 转红 ⇒ **L4**）· ✅ **H-3**（A7 升 L4：proto wire 探针）＝ **2026-10-29 已落码 ＋ 已取证**（`tools/proto-wire-probe/hot/`，对拍 EXIT=0 ＋ 变异 3/3 转红 ⇒ **L4**；⚠️ 归属按 §5.3f：**hot-only 探针目录 = 真私有 ⇒ G3 域侧落码合法**）· **H-4**（`Infrastructure.md §3` 未预占 `hot-rpc` gRPC 端口）＝ **非本仓写权** ⇒ 只登记
- [x] §4 的派单**已走实时轨**（`session send` → `G4Common` ＋ 本表正式轨，两轨一致）：**单 2** ＝ ✅ G4-1（client breaker 在 rpc 侧无装配路径 —— **全域共性**，请 G4 收口去重；⚠️ **hot 域本身 = N/A**：无 gRPC 出向调用 ⇒ 缺口对 hot 不可观测）—— 7 项反馈单齐全
  - 🟡 G4-2（A9 轴措辞）/ G4-3（A8 = N/A 的登记口径）＝ **登记/转合并收口执行者项**，已随单 1 消息同批发给 G4（**非 7 项反馈单**）
  - 🔴 `docs/DRIFT/global/vs-common.md` ＋ `global/_README.md` §七之二属 **SHARED 面 ⇒ G3 不写**（AGENTS.md §5.3e）⇒ 由 G4 / 合并收口执行者落
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）—— ✅ **本轮实测（2026-10-28 · G3，域侧自修 · §1e）**：`check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=1**（索引 **315** vs 本树实测 **332**，差 **+17** ⇒ **合并收口执行者**统一 `--fix` ⇒ ⛔ **各 lane 不各自 `--fix`**）· `_MANIFEST.json` **不收录** `vs-common.md`（`grep -c` = **0**）⇒ ⇒ **本域无需改共享面** ⇒ ⇒ ⚠️ **措辞订正**：「合并收口执行者」⇒ 改称「**合并收口执行者**」

> ⚠️🔴 **本轮踩坑（四域共用 · 已在 G4 同步）——「单模块跑法」会拿 `~/.m2` 里的旧共享层做测试**
> `mvn -o -pl <模块> test`（**不带 `-am`**）⇒ 共享层从 **~/.m2** 取 ⇒ 本地那份 `service/common` jar 是 **E62 修前**旧版 ⇒ 域内 SQL deadline 守门会以「毫秒精度失效 ＋ 过期后仍执行 SQL」的形态**假红**（comment-rpc 3 条 / favorite-rpc 1 条）⇒ ⇒ **不是漂移**。
> ⇒ **纪律：单模块跑域内测试一律加 `-am`**（或先 `mvn install` 共享层）⇒ **跑红也 ≠ 坏**（与「跑绿 ≠ 对」同族）。
> ⇒ **全文与跨域影响面见** [`docs/DRIFT/article/vs-common.md`](../article/vs-common.md) §5 的「本轮踩坑」条。

> ℹ️ **G4 回执核账（2026-10-27 · 编号澄清已对齐）**：G4 提示「`DRIFT-HOTRPC-004` 闭的是 **hot-api** 侧、不是 hot-rpc 侧；client breaker 那条的权威编号是 **`U4`**」⇒ **我方阶段 1 已就地订正**（§1 表第 ① 行已改为 `U4` ＋ 标注「该闭合指的是 hot-api 侧 ⇒ hot-rpc 侧仍缺」；`DRIFT-HOTRPC-004` 另列为「Redis 客户端重试」条并标「勿混」）⇒ **两边已一致，G4 无需处理**。

---

## 6. 🔴 域内未闭合项清单（2026-10-29 · G3 域内收敛自查 A · 全量盘点）

> **盘点口径**：把本域**全部**台账（`api.md` / `rpc.md` / `common.md` / `proto.md` ＋ 本文件）里
> 所有「未闭合 / 开放 / 待办 / 未修 / 仍 OPEN」条目**逐条**取出 ⇒ 定状态
> （**已闭 / 仍开 / 范围外 / 归属他方**）＋ 是否阻塞阶段 1 ＋ 证据指针。
> ⚠️ **本清单是「现状面」**；各轮的「判定变化」表是**历史记录**（AGENTS.md §6.3 状态表纪律）。
> ⚠️ **登记类事项不进本清单**：已进 `docs/Intentional_Deviations/` 的候选（🏷 候选，建议签）
> **按 AGENTS.md §5.3c「登记完就躺着，不升级为拍板」** ⇒ 不重复列出、不推拍板。

| # | 项 | 状态 | 归属 | 阻塞阶段 1？ | 证据 |
|---|---|---|---|---|---|
| **H-A1** | **A7 升 L4：proto 消息体的 wire 字节双侧对拍探针** | ✅ **已落码 ＋ 已取证**（2026-10-29 · G3 · Jason 批准开工后）—— `tools/proto-wire-probe/hot/`（`run.sh` + `mutate-wire.sh` + `go/main.go` + `expected.txt` + `README.md`）＋ Java 侧 `HotProtoWireParityTest`（7 例）⇒ 对拍 **EXIT=0** ＋ 变异 **3/3 精确转红** ⇒ **A7 = L4** ⇒ `hot-proto` **R3 → R4**（登记给收口执行者改基线） | 🟢 **G3（本域）** —— ⚠️ **归属已订正（2026-10-29）**：原判「`tools/**` 是共享面 ⇒ 不在 G3 写权面」**已作废** ⇒ 按 AGENTS.md §5.3f「**共享判据 = 依赖度数**」⇒ `tools/proto-wire-probe/hot/` 只被 hot 域引用 ⇒ **真私有 ⇒ 域侧落码合法** ⇒ ⇒ **本项是域侧自修项**，不是「登记给收口执行者」项 | 否 | §3 `H-3` 行 · [`proto.md` §3 `H-3`](proto.md)<br>✅ **本轮已照 favorite 的 `tools/proto-wire-probe/favorite/{run.sh,go/main.go}` 骨架复制并落码**（hot.proto **无 `optional` / 无 `map`** ⇒ 探针形态比 favorite 更简单 ⇒ **13 条用例**）<br>⚠️ **本项比 favorite 多做了一件事**：新增 `mutate-wire.sh`（favorite 侧**没有**变异脚本）⇒ 因为「新增的守门必须配能真正触发它的测试」（AGENTS.md §3.3.3）⇒ 三条变异分别打**链的三段**（`expected.txt` · Go 探针程序 · **RAG 侧 proto 字段号**）⇒ **M3 最重要**：它证明 Java 侧真的在拿字节跟 Go 真源比，不是在「自己跟自己」 |
| **H-A2** | **`DRIFT-HOTAPI-030` 后续格**（判定权口径差 1：总「值」数 vs Go 的非空值数） | 🔴 **仍 OPEN** | 🔴 **global / G4**（共享层修法；本域已在第十三轮把容器旋钮对齐） | 否 | [`api.md` §「残余」行](api.md) · [`favorite/vs-common.md` §4](../favorite/vs-common.md)（同一条，跨域） |
| **H-A3** | **`DRIFT-HOTAPI-031` 超长请求行**（Go **431 + `text/plain`** vs RAG **400 + Tomcat HTML** ⇒ Tomcat 在**连接器层**就拒 ⇒ **框架限制**） | 🟡 **未修（框架限制）** ⇒ **已登记为 `SD-2026-10-25-CAND-10`** ⇒ 按 §5.3c **登记类 ⇒ 不再重复列出、不推拍板** | ⬜ **登记即定案**（`docs/Intentional_Deviations/hot/api.md:332`） | 否 | [`api.md` §「残余 ②」](api.md) |

⇒ **hot 域的「域内可动」未闭合项 = H-A1 一条**（H-A2 属 global，H-A3 已登记为签字候选）⇒
⇒ **本域三组件的**域内**语义漂移清单为空**（与 `rpc.md:932` 的结论一致），**剩下的只是取证缺口**。

---

## 7. 🔴 Go 出处引用漂移自查（2026-10-29 · G3 域内收敛自查 B）

> **为什么这是缺陷而不是笔误**：以「Go: …」措辞写在 javadoc / `@DisplayName` / 断言消息里的断言
> **改一行不会让任何测试变红** ⇒ ⇒ **绿测试可能守着一个错的事实**（AGENTS.md §3.3.2）。
> ⚠️ **守门本体见 [`article/vs-common.md` §7.1](../article/vs-common.md)（四域共用，G3 域侧落码）**
> ⇒ 本节只列**本域**的订正项。

| # | 落点 | 原引用 | 订正为 | 缺陷类型 |
|---|---|---|---|---|
| 1 | `hot-api/.../client/ArticleRpcClient.java:27` | `serviceContext.go:104` | `service/hot/api/internal/svc/serviceContext.go:20-22` | **行号越界**（该文件**仅 58 行**）＋ **域段缺** |
| 2 | `hot-api/.../controller/HotArticleController.java:70` | `getHotArticlesHandler.go:15-32` | `getHotArticlesHandler.go:15-31` | **范围末尾越界**（全文 **31 行**） |
| 3 | `hot-api/.../form/GoFormValues.java:96` ＋ `HotApiGoParseParityTest.java:42,145` ＋ `HotApiRealTomcatGateTest.java` | `core/mapping/jsonunmarshaler.go:45-53` | `core/mapping/jsonunmarshaler.go:45-52` | **行号错**（**3+1 处**；go-zero v1.10.2 的该函数止于 `:52`） |
| 4 | `hot-api/.../exception/HotCodeErrorLogParityTest.java:41` | `service/hot/common/logger/logger.go:118-123` | `service/**common**/logger/logger.go:118-123` | **域段错**（`service/hot/common` 下**只有 `errmsg.go`** ⇒ 没有 `logger.go`） |
| 5 | `hot-api/.../HotApiRealTomcatGateTest.java:87,119` | `prometheus/client_golang@**v1.21.1**/prometheus/histogram.go:271` | `prometheus/client_golang@**v1.23.2**/prometheus/histogram.go:271` | **版本过期**（真源 `go.mod` pin 的是 **v1.23.2**） |
| 6 | `hot-api` **6 处**（`HotApiGoParseParityTest` / `HotArticleHydratorTest` / `HotArticleQueryServiceTest` / `HotRankPaginatorTest`）＋ `CapturingClientInterceptor.java:23` | `Go _test.go:NN` | `Go **getHotArticlesLogic_test.go**:NN` | **出处缺文件名** ⇒ **不可核** ⇒ 补全为真源 `service/hot/api/internal/logic/hot/getHotArticlesLogic_test.go`（184 行） |
| 7 | `hot-rpc/.../mq/HotEventConsumerDropTest.java:174` | `test/cmd/producer.go:18-23` | `service/hot/test/cmd/producer/producer.go:14-19` | **域段缺**（实际是 4 字段 struct 在 `:14-19`） |

⇒ **本域 8 类（15 处）已订正（零行为变更：全是注释 / `@DisplayName`）** ⇒ 本域守门实测 **EXIT=0**（`LINE_OOB` / `FILE_MISSING` / `EMPTY_LINE` / `VERSION_MISMATCH` **全 0**）。
⇒ ⇒ **hot 域是本轮 16 类缺陷里命中最多的一域**（8/16）⇒ 与「`hot` 域注释密度最高」的形态一致。

---

## 8. 🔴 台账内部矛盾 / 过期结论订正（2026-10-29 · G3 域内收敛自查 C）

> **扫描口径**：把本域台账里**同一个 ID 在不同行**同时出现「已闭」与「仍开」措辞的**候选**全部取出
> ⇒ 再逐条**人读**核实 ⇒ 只保留**前提真的失效**的。
> ⚠️ **本轮踩到的第一版误报**：扫描器最初**没有排除「同一行里『修复前 → 修复后』两列」** ⇒ ⇒ 把 18 条
> **表格内的正常对照**误判成矛盾 ⇒ ⇒ 改成「**只比不同行**」后候选降到 3 条 ⇒ **2 条是「未闭合项」字样误命中**
> ⇒ ⇒ **真矛盾 = 下面 5 条**（**hot 域是四域里命中最多的**）。

| # | 落点 | 矛盾 | 订正 |
|---|---|---|---|
| 1 | [`api.md` D15 行](api.md) | 「**未闭合 / 已知差异**」表里 **D15 仍写「🟡 已知取舍，需登记」** ⇒ 但同文件 `:742` / `:817` 早已写「✅ 已修（`@JsonPropertyOrder` + `HotApiGoWireOrderTest`，去掉注解即变红）」⇒ ⇒ **状态列没回填** ⇒ 读者会以为 D15 还开着 | ✅ **订正为「已修（2026-10-24 第五轮）」** ＋ 补修法与守门出处（`HotArticlesResp:48` / `HotArticleCard:84` ＋ `HotApiGoWireOrderTest` ＋ 第五轮双侧活体**逐字节相同**）⇒ ⇒ **不需要登记为偏离** |
| 2 | [`api.md` D14 行](api.md) | 备注止于第五轮「**已移交 common 域**」⇒ 实际 **2026-10-26 已订正为「已实现」**（共享层 `HttpMiddleware.java:728` ＋ 本域真 Tomcat 断言）⇒ ⇒ 状态列停在旧轮次 | ✅ **订正为「已实现（2026-10-26）」** ＋ 补 **wire 级 L4** 证据（`HotApiRealTomcatGateTest:229-230`）＋ 补 Go 侧出处说明（Go 全仓零命中 ⇒ nosniff 来自 `net/http` 的 `http.Error` 自动加头） |
| 3 | [`api.md` D9 行](api.md) | 状态列写「⚠️ 静态确认（2026-10-23）」，而**同一行的备注**已是「✅ 活体（2026-10-24 第八轮）：三闸双侧活体坐实」⇒ ⇒ **同一行自相矛盾** | ✅ 状态列订正为「✅ **已闭合（第八轮，2026-10-24）** —— 三闸双侧活体坐实 + CpuShedding 平台不可达（**非缺陷**）」 |
| 4 | 本文件 §3 `H-2` 行 ＋ §5 交付物清单 | **H-2 已在 §4 的 `G4-18` 行闭合**（探针落仓 ＋ EXIT=0 ＋ 变异 4/4 转红）⇒ 但 **§3 的 `H-2` 行仍写 🟡「待做」** ＋ **§5 仍写「本域无现成域级探针」** ⇒ ⇒ **同一文件内三处互相打架** | ✅ 三处一并订正（`H-2` → ✅ 已闭合；§5 → 🟡 部分达成：**A6 = L4**（errmsg 探针）＋ **A7 = L3**（descriptor 级）） |
| 5 | 本文件 §3 `H-2` / `H-3` 行的**归属** | 原写「`tools/**` 是**共享面** ⇒ 不在 G3 写权面 ⇒ 登记给收口执行者」⇒ ⇒ 与 AGENTS.md §5.3f「**共享判据 = 依赖度数，不是目录位置**」**直接冲突**（hot-only 目录 = **真私有** ⇒ G3 域侧落码合法）⇒ ⇒ **归属判断错了** | ✅ 两行归属一并订正（`H-2` 已据此落码 ⇒ 证据强度 **L4**；`H-3` 据此改为**域侧自修项**，本轮按**停止令**未落码 ⇒ 仍列 🟡 开放项） |

⇒ **hot 域真矛盾 = 5 条，已全部订正**。

---

## 9. 🟢 新立 `T-2.7`：**hot 域独立失败路径矩阵**（**1 个 HTTP 端点 × 6 类失败维度** ＋ **5 个 gRPC 方法**）⇒ 基线 §九 `G-13` 在本域**闭合**（2026-10-29 · R2 lane · `align/r2-article-hot`）

### 9.1 起因（基线原文）

`docs/PhaseGoal-judge.md` P0 **#2** 的判据：
> 「存在失败路径处理」**推不出**「参数校验 / 业务规则 / 认证 / 权限四项都有效」；且失败路径**域覆盖不全**

＋ §九 **`G-13`**：「**4 域无独立失败路径矩阵**（`article` / `follow` / `task` / `hot`）」⇒ 🟡 **否**。
⇒ **hot 是这 4 域之一** ⇒ 本轮补齐。

### 9.2 落点（4 个文件 · **生产代码零改动**）

| 文件 | 动作 | 内容 |
|---|---|---|
| `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/support/RealTomcatPort.java` | **新增** | 真 Tomcat 的端口选取 ＋ **双地址** bind 自检 · 段 **18146–18150** |
| `service/hot/hot-api/src/test/java/com/rag/hot/hot/api/HotApiFailurePathMatrixTest.java` | **新增** | **20 条**：1 端点 × 6 类失败维度 ＋ 边界值 ＋ 路由层 |
| `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotRpcFailurePathMatrixTest.java` | **新增** | **8 条**：**5 个 gRPC 方法**逐个（**真 gRPC server ＋ 真 client**，断言的是**线上字节**） |
| `service/hot/hot-api/src/test/mutate/mutate-failure-matrix.sh` | **新增** | **5 条变异**逐条精确转红 ＋ 还原后转绿（§9.6） |

⇒ ⚠️ **hot 域生产代码零改动** ⇒ ⇒ **不构成 Go 对照项**（与本档 §3 同款口径）。

### 9.3 为什么 hot-rpc 那份要起**真 gRPC server**

已有的 `HotServiceGrpcImplErrorMappingTest` 只在 **observer 回调层**断言 ⇒ 它证明不了「这个 status 能不能**穿过 netty 传输层**到达客户端」。
⇒ 本类起一个**真 gRPC server**（`RealGrpcPort` 段 `22500–22579`）＋ **真 `ManagedChannel`** 调用 ⇒ 断言的是**线上字节**。

⚠️ **端口段为什么不取 global 段表的 `22500–22599`**：该段已被 **hot-rpc 整段吃满**（`hot-rpc/.../support/RealGrpcPort.java:91-94` 占 `22500–22579` ＋ `RealGrpcPortTest.java:48-51` 占 `22580–22599` 作守门脚手架）⇒ **段内无切片** ⇒ hot-api 的真 Tomcat 取 lane 分配的 `18146–18150`。📌 global 段表（`docs/DRIFT/global/vs-common.md` §4.10.19.3 · **G4 写权**）里 hot 域那一行仍是「预分配 22500–22599」⇒ **需 G4 在收口时补记本段** ⇒ **本域只在自己的台账登记，不改共享文件**。

### 9.4 矩阵（期望值逐条带 Go `文件:行号`）

**① hot-api（`GET /hot/v1/articles` · 真 Tomcat）**

| 维度 | Go 出处 | 形态 | 条数 |
|---|---|---|---|
| **认证（零鉴权）** | `api/internal/handler/routes.go:16-25`（`AddRoutes` **只有** `WithPrefix`，**没有** `WithJwt` / `WithCors` / `WithMaxBytes`） | 无 token / 乱 token ⇒ **进业务**（**不得 401/403**） | 2 |
| **参数校验** | `getHotArticlesHandler.go:18-20`（`httpx.Parse` 失败 ⇒ `httpx.ErrorCtx`）＋ `hotcenter.go:53-66`（**注册了** `SetErrorHandler`） | `page=abc` / `page_size=abc` ⇒ **HTTP 200 + `{code:5001,msg:"internal server error"}`**（**不是 400**）＋ **RPC 完全未被调用** | 3 |
| **边界值** | `getHotArticlesLogic.go:172-190`（`normalizePagination`）· `:196`（`paginate` 的 **int32 乘法**） | `page≤0` ⇒ 回落 **1**；`page_size≤0` ⇒ 回落 **20**；`page_size>50` ⇒ **夹取 50**；`page=1000000` ⇒ **int32 溢出 ⇒ 空页（不是错误）**；同名多值 ⇒ **取第一个**（**不是 400**） | 4 + 1 + 1 + 1 + 1 |
| **下游 fatal** | `getHotArticlesLogic.go:65-69`（rpc 出错）· `:70-75`（`hotResp == nil`） | 两条都是 **fatal** ⇒ **200 + `{code:5001}`** | 2 |
| **下游 best-effort** | `getHotArticlesLogic.go:137-142`（RPC 出错 ⇒ skip）· `:143-147`（文章缺失 ⇒ skip）· `:150-154`（未发布 ⇒ skip）· `:127-130`（空 `article_id` ⇒ skip） | **只跳过该条**，其余照常返回 ⇒ **200 + `{code:200}`**，`total` 相应减少（**不得**整包 5001） | 3 |
| **路由层** | `rest/router/patrouter.go:73`（未注册路径）· `:79-82`（方法不匹配） | 404 ⇒ **`404 page not found\n` + text/plain + nosniff**；405 ⇒ **裸状态码 + `Allow: GET` + 空 body + 无 Content-Type** | 2 |

**② hot-rpc（5 个 gRPC 方法 · 真 gRPC server）**

| 方法 | Go 出处 | 期望（**线上字节**） | 条数 |
|---|---|---|---|
| `GetHotArticles` | `get_hot_articles_logic.go:44-51` | 正常 ⇒ items 原样下发；**Redis 失败 ⇒ `UNKNOWN`**（Go **裸 error** 的落地码，`status.FromError` 返回 ok=false ⇒ **不是 INTERNAL**）＋ description 是原始错误文本 | 2 |
| `LikeArticle` | `like_article_logic.go:44` | **`UNIMPLEMENTED`** ＋ 精确文案 **`LikeArticle not implemented yet`** | 1 |
| `CoinArticle` | `coin_article_logic.go:36` | **`UNIMPLEMENTED`** ＋ 精确文案 **`CoinArticle not implemented yet`** | 1 |
| `ShareArticle` | `share_article_logic.go:36` | **`UNIMPLEMENTED`** ＋ 精确文案 **`ShareArticle not implemented yet`** | 1 |
| `CommentArticle` | `comment_article_logic.go:30` | **`success = true`**（**唯一不对称的那个** ⇒ 统一成 UNIMPLEMENTED 就是行为变更） | 1 |
| 未注册方法 | `hot_service_server.go:12`（内嵌 `pb.UnimplementedHotServiceServer`） | **`UNIMPLEMENTED`** | 1 |

### 9.5 🔴 本域最容易被「统一风格」抹掉的**三处不对称**

| # | 不对称 | Go 出处 | 后果 |
|---|---|---|---|
| ① | **hot-api 注册了 `httpx.SetErrorHandler`** ⇒ **解析失败 = 200 + `{code:5001}`**（article-api 是 400 text/plain） | `hotcenter.go:53-66` | 照抄 article 的判据 ⇒ 本域 200 变 400 |
| ② | **fatal 与 best-effort 方向相反**：hot-rpc 出错 ⇒ 整包 5001；article-rpc 出错 ⇒ **只跳过该条** | `getHotArticlesLogic.go:65-75` vs `:137-142` | 统一成「都抛异常」⇒ 「article-rpc 抖一下」从「少一条」变成「整包失败」—— **可用性级别**的漂移 |
| ③ | **4 个互动方法里 3 个 UNIMPLEMENTED ＋ 1 个 `success=true`** | `like/coin/share_article_logic.go` vs `comment_article_logic.go:30` | 统一风格 ⇒ 客户端从「拿到成功」变成「拿到错误码」 |

### 9.6 变异验证（**5/5 精确转红** ＋ 还原后转绿 ⇒ 网有牙）

`service/hot/hot-api/src/test/mutate/mutate-failure-matrix.sh`（**还原用「备份 + 复制回来」，绝不用 `git checkout --`；**`rm -rf` 在本机会被回收站守卫整体拒绝**）。

**判定口径比「跑一遍红了」严格**：**红的方法集合必须与预期**完全相等**（多红一条 = 判据之间有意外耦合；少红一条 = 这条断言是摆设）**。

| # | 变异（改**生产代码**） | 预期红的方法集合 | 实测 |
|---|---|---|---|
| **H1** | `HotRankPaginator.MAX_PAGE_SIZE` **50 ⇒ 51** | `pageSizeIsClampedTo50` | ✅ 一致 |
| **H2** | `HotRankPaginator.DEFAULT_PAGE` **1 ⇒ 2** | `nonPositivePaginationFallsBackToDefault` | ✅ 一致 |
| **H3** | `HotArticleQueryService#fatal` 的业务码 **5001 ⇒ 5002** | `hotRpcNilResponseIsFatal` ＋ `hotRpcUnavailableIsFatal` | ✅ 一致 |
| **H4** | `HotArticleHydrator` 的「**未发布 ⇒ skip**」闸门**反相**（`!= PUBLISHED` ⇒ `== UNSPECIFIED`）⇒ 草稿漏进热榜 | `unpublishedOrMissingArticleIsSkipped` | ✅ 一致 |
| **H5** | `InteractionStubService.LIKE_UNIMPLEMENTED_MSG` 文案**逐字改错** | `likeArticleIsUnimplemented` | ✅ 一致 |

⇒ **实跑**：`bash service/hot/hot-api/src/test/mutate/mutate-failure-matrix.sh` ⇒ **exit 0**（5/5 精确转红 ＋ 还原后两条 `exit 0`）。

⚠️ **第一版 H4 打偏了（已订正，保留记录 —— 这一段本身就是「变异没打中」的现场）**：

| 轮次 | 变异写法 | 实测 | 根因 |
|---|---|---|---|
| ① | 把「补全失败 ⇒ `return null`（skip）」改成「`throw e`（整包冒泡）」 | **红集合为空**（变异**没打中**） | `HotArticleHydrator#hydrate` 的 `catch (RuntimeException e)`（`:127-134`）会把任务体抛出的异常**吞掉**（Go 侧 `wg.Wait()` 同样不会失败）⇒ **「补全失败 ⇒ 整包失败」在当前实现里结构上不可达** |
| ② | 把状态闸门反相写成 `ArticleStatus.UNSPECIFIED` | **编译失败**（`找不到符号: 变量 UNSPECIFIED`） | proto 的 `ARTICLE_STATUS_UNSPECIFIED` ⇒ protoc-java 的 Java 常量名是 **`ArticleStatus.ARTICLE_STATUS_UNSPECIFIED`**，不是 `UNSPECIFIED` |
| ③（**采纳**） | `!= PUBLISHED` ⇒ `== ARTICLE_STATUS_UNSPECIFIED` | ✅ **精确转红**（`unpublishedOrMissingArticleIsSkipped`） | 打在**真正决定输出内容的那一处** |

⇒ 📌 **这本身是一条有用的结论**：hot 域的 best-effort 有**两层**防护（内层 skip ＋ 外层吞异常）⇒ 变异必须打在**可观察的那一层**。
⇒ 教训对应 `docs/Pitfalls/article/api.md` **P-21**：「仍全绿」的第一嫌疑是「**变异没打中**」；另加一条：**枚举常量名要按生成码核对**（proto 的 `ARTICLE_STATUS_UNSPECIFIED` ⇒ Java 的 `ARTICLE_STATUS_UNSPECIFIED`，**不是** `UNSPECIFIED`）。

### 9.7 🟡 上报项：**`:59`（A7 行）与 `:67`（H-1 行）的自相矛盾 —— 裁定与不改写**

| 项 | 内容 |
|---|---|
| **现象** | 本文件 `:59`（**A7 行**）的前半句写「🔴 **但无机器回归网**：`tools/difftest/check-proto-static.sh` **只覆盖 `user`/`admin`/`task`** ⇒ **hot 的 proto 契约无自动守门**」；而 `:67`（**H-1 行**）写「✅ **补 proto 契约回归网（2026-10-27 阶段 2 已落码）**」⇒ ⇒ **同一文件两行互相打架** |
| **本轮核实（逐条实测）** | ① `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoContractTest.java` **存在**；② `.../HotProtoWireParityTest.java` **存在**；③ `git ls-files` 命中 `service/hot/hot-rpc/src/test/resources/proto-contract/{generate.py,hot-proto-contract.expected.txt}` ⇒ **守门在版本控制内**（不是 `/tmp` 摆设）；④ `tools/proto-wire-probe/hot/`（`run.sh` ＋ `mutate-wire.sh` ＋ `expected.txt` ＋ `go/main.go`）**存在** |
| **裁定** | **`:67`（H-1）为准** ⇒ hot 的 proto 契约**确实有**自动守门（descriptor 级 `HotProtoContractTest` ＋ wire 字节级 `HotProtoWireParityTest` ＋ `tools/proto-wire-probe/hot/`）⇒ **A7 的证据强度按 L4 计**（与 §8 第 4 行「A7 = L3（descriptor 级）」的**旧口径**也不一致 ⇒ 一并由收口执行者核） |
| **本轮处置** | ⛔ **不改写 `:59` / `:67` 原文** —— 两行都带**别的 lane 的落码记录与证据强度列** ⇒ 改写会掩盖「谁在何时落了什么」⇒ ⇒ **只在本节登记裁定 + 证据** ⇒ **由收口执行者**在合并收口时统一回填 |
| **归属** | **共享面（`docs/DRIFT/hot/**` 虽在 G3 写权，但这两行的改写牵涉「跨 lane 的证据强度口径」）⇒ 收口执行者** |

### 9.8 效力边界（如实登记，不夸大）

| 边界 | 内容 |
|---|---|
| **证据强度** | **L3 / R3**（期望值逐条抄自 Go 源码 ＋ **真 Tomcat / 真 gRPC server** 自动化断言 ＋ **变异 5/5 精确转红**）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥ |
| ⛔ **不是 R4** | R4 要求**双侧活体对拍 ＋ 变异** ⇒ 本类**只有**变异，**没有**双侧活体 ⇒ ⇒ **不得**据此宣称「与 Go 逐字节一致」 |
| ⚠️ **P0 #2 仍不是 ✅** | `task` 域**仍无**独立失败路径矩阵（`T-2.8` · **G2**）⇒ 本条只闭合 **hot 域**（+ 本轮 article 域）⇒ **不因本条把 P0 #2 判成 ✅** |
| ⚠️ **hot-rpc 的 Redis 客户端行为未覆盖** | `HotRpcFailurePathMatrixTest` 用**替身**让数据层失败（要验的是「失败 ⇒ UNKNOWN」这条**映射**）⇒ ⇒ **不声称**覆盖 Redis 客户端自身行为 |
| ⚠️ **全局端口段表待 G4 补记** | 见 §9.3 ⇒ **共享文件 ⇒ 本域不擅自改** |

### 9.9 实跑记录（**真实输出** · 2026-10-29）

| 命令 | 结果 |
|---|---|
| `mvn -o -B -pl service/hot/hot-api -DargLine=-Xmx2g test` | **`Tests run: 116, Failures: 0, Errors: 0, Skipped: 0`** · **EXIT=0**（其中本轮新增 `HotApiFailurePathMatrixTest` = **20 条**，`Tests run: 20, Failures: 0`）|
| `mvn -o -B -pl service/hot/hot-rpc -DargLine=-Xmx2g test` | **`Tests run: 129, Failures: 0, Errors: 0, Skipped: 0`** · **EXIT=0**（其中本轮新增 `HotRpcFailurePathMatrixTest` = **8 条**，`Tests run: 8, Failures: 0`）|
| `bash service/hot/hot-api/src/test/mutate/mutate-failure-matrix.sh` | **EXIT=0**（**5/5 精确转红** ＋ 还原后 hot-api / hot-rpc 两条 `exit 0`）|
| `python3 tools/difftest/check-wave-lane-ownership.py` | g3 lane **零违规**（剩余 3 项违规属 **g1 / g2** 的探针目录缺失 ⇒ **不是本 lane**）|

⚠️ **本轮**生产代码**零改动** ⇒ ⇒ **不构成 Go 对照项**（与本档 §3 同款口径）。

### 9.10 ⚠️ **本轮的栈形态 ＋ 串扰排查口径**（2026-10-29 主线同步后补记）

| 项 | 结论 |
|---|---|
| **是否起双侧栈** | **否** —— 本轮只起 **RAG 单侧**：`hot-api` 的**真 Tomcat**（`DEFINED_PORT` ＋ 段 `18146–18150`）＋ `hot-rpc` 的**真 gRPC server**（段 `22500–22579`）＋ **打桩的 gRPC 客户端** ⇒ ⇒ **Go 真源进程一次都没起** ⇒ ⇒ 本轮**不是**双侧活体对拍 ⇒ 证据强度按 **R3**（§9.8）计，**不得**升 R4 |
| **是否共享 Nacos** | **否** —— ① `hot-rpc` / `article-rpc` 客户端地址全部写死 `static://127.0.0.1:65535`（**不走服务发现**）；② `spring.cloud.service-registry.auto-registration.enabled=false`（**不注册**）；③ `spring.cloud.nacos.discovery.server-addr=127.0.0.1:1`（**死口**，不是宿主口 `49848`）⇒ ⇒ **本轮完全不接触共享 Nacos** ⇒ ⇒ **不受「共享 Nacos 里存在同 serviceName 的陈旧/他栈实例」那种串扰影响** |
| **是否观测到 `INTERNAL` / `code=5001` / 熔断 failure** | **观测到的全部是测试自己打的桩**，逐条核对：① `hotRpcUnavailableIsFatal` 刻意让 hot-rpc 抛 `UNAVAILABLE` ⇒ 期望 5001；② `hotRpcNilResponseIsFatal` 刻意让 hot-rpc 返 `null` ⇒ 期望 5001；③ `unparsableQueryIs200With5001` 的 5001 来自**解析失败**这条被测路径；④ `HotServiceGrpcImpl -- [Hot] unhandled error: Redis …` 是 `redisFailureMapsToUnknownNotInternal` 的**输入**，**输出**是 `UNKNOWN` ⇒ ⇒ **没有一条是环境串扰**。三个模块全量套件 **210 / 116 / 129 条全绿 · EXIT=0**；两轮变异脚本**可重复**（article **6/6**、hot **5/5**，两次跑出**同一组**红集合）⇒ ⇒ **无并发串扰迹象** |
| ⚠️ **反向纪律（AGENTS.md §3.3）** | **跑红 ≠ 是缺陷** ⇒ 本轮 hot 侧第一版 H4 变异**红集合为空**时，先判「**变异没打中**」（§9.6 的三轮记录）而不是判「产品有缺陷」⇒ ⇒ 与主线 2026-10-29 记录的那次 `INTERNAL` 偶发**同一纪律** |

---

## 开工前置回执

> ⚠️ **本节是 AGENTS.md §五·3b-0 第 ③ 步的「开工前置回执」**（模板 ⇒ `docs/Worktree.md` §二·五）⇒
> **本 lane 的每个域台账都要有这一节**（收口时才能一眼看出「这个域开工前到底读没读」）。
> ⚠️ **回执人不得自批**：⑤ 由**调度员**在 Jason 批准后代填；本节**不构成开工批准**（**已代填**：Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」· 消息 id `182054` ⇒ 见本节末尾 ⑤ 行）。

【开工前置回执 · G3 · 2026-10-29】

**① 任务范围**（本 lane = `article` + `comment` + `favorite` + `hot`，**阶段 1**）：

- 候选任务（**回执落笔时：待 Jason 批准，本轮只确认、不动手**；⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182054`）⇒ **已获批开工**）：
  1. **H-A1** —— hot proto **消息体** wire 字节的双侧对拍探针落码（`tools/proto-wire-probe/hot/`，照 favorite 域 `tools/proto-wire-probe/favorite/{run.sh,go/main.go}` 骨架复制）。
  2. **A-1** —— `article` 域**双侧活体对拍**（Go 侧需 `etcd` 宿主端口映射；实测 `Sea-etcd` 的 `49319→2379` 转发已在跑 ⇒ **`judge` §九 `G-3` 写「⚫ 受阻（宿主端口未映射）」的前提已过期**）。
  3. **F-1** —— `favorite-rpc` **整域**双侧活体对拍（Go 侧 `etcd` / Kafka 仍不可拉起 ⇒ 环境受限）。
- **不做**（明确边界）：
  - **阶段 2**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 · §9 故障矩阵 · 生产流量 / SLO）⇒ 收口执行者的事，**不预支**。
  - **共享层** `service/common/**` ＋ `service/users/users-common/**` ⇒ **只有 G4 能写**，本 lane 只读。
  - **共享索引面**：`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g3.md`（**机器生成物**）⇒ 不写。
  - **根级文件**（`docker-compose.yaml` / 根 `README.md` / `.env.example`）⇒ **无人拥有 ⇒ 归「合并收口执行者」**（根因并入 G1 的 `S1-USERS-001`，**不另开编号**）。
  - **已登记的签字偏离候选**（`docs/Intentional_Deviations/` 的 `CAND` 族）⇒ 按 AGENTS.md §5.3c「登记完就躺着，**不升级为拍板**」⇒ 不重复质疑、不推拍板。

**② 已读四档**（**具体路径 ＋ 读到的关键结论**）：

- `docs/DRIFT/article/api.md` ⇒ 权威现状面 = **§7 状态表（`:556`）+ §9 UNRESOLVED（`:878`）**（文件头 `:17-24` 有读取指针表；⚠️ 组件判定行 `:26` 是**首轮**判定，**不可直接引用** —— 见 `PhaseGoal-judge.md:104-108`「5 份台账头已被正文推翻」）；§8.3（`:858`）四条「待补探针」经核对 **3 条早已消解**（`DRIFT-001/002/003` 已修 + 守门在位）⇒ 本轮已改写为「活状态」表；`UNRESOLVED-002`（`:886`）跨组件、仍缺跨栈活体。
- `docs/DRIFT/article/rpc.md` ⇒ 权威面 = §7（`:343`）+ §9（`:654`）；**`S-5`（`:95` / `:318`）content security 被 security 域阻塞 ⇒ 外部客户端恒抛、审核链路不可用 ⇒ ⛔ 本项目内不可闭环**；`U-3`（`:658` 真 Kafka 失败位点）· `U-4`（`:659`）· `U-9`（`:664`）· `U-4'`（`:666`）**四项取证缺口仍 OPEN**；「跨域待办」（`:1271` / `:1789`）= 其它域 `*SchemaGate` 同款写法 ⇒ **只登记不修**。
- `docs/DRIFT/article/common.md` ⇒ ✅ Go↔Java **sha256 逐字节一致**（`cad5cb4f…`，49 行，`:50`）+ 契约测试 15 条（`:51`）+ 变异 **8/8 全红**（`:52`）⇒ **无编号未决项**；⚠️ `:22` / `:74-77` 的「消费侧仍是空骨架 / 零生产调用方」描述**已被现状推翻**（覆盖矩阵 `:40-41` 记 `article-api` 41 文件 5759 LOC）⇒ **一条未被本文件自身订正的过期描述**（见 ③ 待批准项 #5）。
- `docs/DRIFT/article/proto.md` ⇒ ✅ 行为层一致、**唯一差异是 option 块**（`:51-62`）；§3.4 三项效力边界（presence / 字段号硬编码 / Jackson）**逐条已验**（`:23-27`）；`P-U-1` 跨栈 gRPC 活体**仍 OPEN**（`:175`）。
- `docs/DRIFT/article/vs-common.md` ⇒ §2 **9 条机制面轴 A1~A9 逐轴结论**（`:36-49`，核对日期 2026-10-27）；§6 **域内未闭合项清单**（`:345-363`，本轮新增）⇒ 域内可动 = **A-1 / A-2 两条**，均不阻塞阶段一；§4 派单仍挂 `G4-2`（MP 自动收集无运行证据，**阻塞本波** `:276`）· `G4-10`（`check-surface-parity.py` 路径指错 ⇒ **假红/假绿** `:296`）· `G4-11`（双侧活体受阻前提已过期 `:297`）。
- `docs/DRIFT/comment/api.md` ⇒ §5 未决 `U1~U11`（`:330-345`）：**`U4`（`:337`）共享台账 `_cross/new-domains-2026-10-23.md:43-45` 的「熔断缺失」结论已被实测推翻 ⇒ 🔴 需收口执行者更正共享台账**；`U10`（`:343`）机制待查（**可观察行为已定**）；判定行 `:24` 注明 3 条 MISMATCH 全修 ⇒ 当前无「必须对齐」分歧项。
- `docs/DRIFT/comment/rpc.md` ⇒ §5 `U1~U16`（`:566-585`）：**`U15`（`:584`）= global `E65` 出向 gRPC 不继承 deadline ⇒ 待修法拍板 + 待共享层** · **`U16`（`:585`）= `E66` Redis 不感知 deadline ⇒ 待共享层**（域侧不擅自引入第三种载体）· `U13`（`:582`）跨栈 `grpc-status:1012` **协议层不可修** · `U10`（`:579`）**销账待 global 合回** · `U4`（`:573`）Go 侧 `t.Skip` 真源缺口 · `U11`（`:580`）Go 侧潜伏缺陷；⚠️ `:1146` 诚实边界：**本域新守门只到「编译通过」、测试未重跑 ⇒ 不得宣布「comment 守门已绿」**。
- `docs/DRIFT/comment/common.md` ⇒ ✅ **SEMANTICALLY EQUIVALENT**（错误码契约层，`:20-33`），MISMATCH 0 条、OBS 1 条；§4 三条 `U1/U2/U3` 全闭（`:123-125`）；⚠️ 同表 `:110`（🔴 RAG 现状）与 `:112`（🟢 2026-10-24 已修复并活体验证）**两行口径不同 ⇒ 读 `:110` 会误判为仍断**。
- `docs/DRIFT/comment/proto.md` ⇒ ✅ 消费方式层 EQUIVALENT（`:15-24`），`U4` 跨栈业务码不可传输 = 协议层（`:112`）。
- `docs/DRIFT/comment/vs-common.md` ⇒ §2 九轴（`:39-51`）＋ §6 未闭合清单（`:207-228`，本轮新增）⇒ 域内可动 = **C-5 / C-7**；§4.2 拍板结论（`:110-119`）：`C-3`/`U15` 方向 = **走 global 统一载体**，本域不擅自选边；`:126-137` `:9091` metrics 暴露面 = **登记类、不占拍板额度**。
- `docs/DRIFT/favorite/api.md` ⇒ §24 **P0 #8 域侧已修**（yaml 去明文默认值 ＋ `@PostConstruct verifyAccessSecret()` ＋ test scope `application.properties` ＋ **3 例真 `ApplicationContextRunner`**）⇒ **域侧半边闭合**；根级半边（compose `:?` ＋ 根 README `.env.example`）⇒ **归合并收口执行者**（`:1550-1579`）；`U9`（`:316-325`）Go 16062 DevServer HTTP 面 ⇒ **待拍板**；405 多一个 `Content-Type` ⇒ 落点 `service/common`（`:25`）。
- `docs/DRIFT/favorite/rpc.md` ⇒ §9.3 **`U7`（`:268`）rpc 层整域双侧活体 = 本域最大缺口**（错误码契约层已双侧对拍，但**那是 `favorite-common`，不是 rpc 层**）；`U1~U6` 逐条复核结论（`:260-268`）；§10-D1 3s deadline 已修（复用 `DeadlineQueryTimeoutInjector`）。
- `docs/DRIFT/favorite/common.md` / `favorite/proto.md` ⇒ 无未闭合项；proto 行为层 **0 行差异**。
- `docs/DRIFT/favorite/vs-common.md` ⇒ §2 九轴；§4「仍未闭合的下一格」= **`DRIFT-HOTAPI-030` 后续格**（总段数 > Go 的 10000 段上限，**显式登记不假装闭合**）；§5 第 0 步「组件真的启动一次」**仍未完成**（DDL 探针是 `psql` 直跑迁移脚本，**全程不启 Java 进程** ⇒ 证明的是 DDL 一致，不是「组件启动过」）。
- `docs/DRIFT/hot/api.md` ⇒ 「未闭合 / 已知差异」表（`:119-206`）`D1~D24`；本轮**订正三处过期状态列**：`D9`（状态列停在「静态确认」而同行备注已是「活体坐实」）· `D14`（停在「已移交 common 域」，实际 2026-10-26 已实现 ⇒ wire 级 L4）· **`D15`（写「🟡 需登记」但早已「✅ 已修」）**；§6 残余 `DRIFT-HOTAPI-031`（`:1238`，已登记 `SD-2026-10-25-CAND-10` ⇒ **登记类**）。
- `docs/DRIFT/hot/rpc.md` ⇒ 域内未闭合项清单为空（`:932`）；`DRIFT-HOTRPC-005`（已取证未签字）· `DRIFT-HOTRPC-006`（保持不修、已登记）。
- `docs/DRIFT/hot/common.md` ⇒ 无未闭合差异（`:73`）；§「2026-10-29 · G3 阶段 1 回填：**G-5 闭合**」= 错误码探针落仓 `tools/errmsg-probe/hot/`（**EXIT=0** ＋ 变异 **4/4** 精确转红）⇒ **`hot-common` 的 R 判定由 R3 升 R4** ⇒ `judge` §九 `G-5` **可撤**。
- `docs/DRIFT/hot/proto.md` ⇒ §3 未决：**`H-3`**（proto 消息体 wire 探针，仍开）· **`G-19`**（proto **消费方式**未取证：`optional` presence / 字段号硬编码 / Jackson 配置）· `G-15`·`G-18`（台账矛盾与位置不统一）**本轮已订正**。
- `docs/DRIFT/hot/vs-common.md` ⇒ §2 九轴；§3 `H-1`（proto 契约网已落码，基准由真源脚本生成、sha256 `4ede7100…`）· **`H-2` 已闭合**（本轮）· **`H-3` 归属已订正**（按 §5.3f，hot-only 探针目录 = **真私有 ⇒ G3 可写**）；§5 交付物清单。
- `docs/DRIFT/_cross/open-items.md` ⇒ §6.2 已知未修按优先级：**`DRIFT-HOTAPI-030`**（`:15`，2049~10000 参数区间两侧不一致，**跨域待拍板**）· **`DRIFT-HOTAPI-031`**（`:16`，超长请求行 400 vs 431 ⇒ 候选 `SD-2026-10-25-CAND-10` **未签字**）· DDL **9 域已做**（`hot` = **N/A** ⇒ **不许写成「已对齐」**）· users 域 8 处 `DEFAULT CURRENT_TIMESTAMP` 残留（`SD-2026-10-25-CAND-16` 未签）· `DRIFT-INFRA-001`（`:59-93`）Flyway 账本撞号（配置层已修；**`FlywayLedgerIsolationTest` 全仓不存在** ⇒ 文档误称其存在）；⚠️ `:65` 的「article-rpc 按指示未动」**仍是 2026-10-23 的旧状态**。
- `docs/DRIFT/_cross/component-coverage-matrix.md` ⇒ **「当前态」节（`:23-78`）才是现状**，历史节（2026-10-23 快照）只作追溯（`:5-14`）；⚠️ 其「当前态」节是 **2026-10-25** ⇒ `article-api` 残留 `-007`（`:40`）已被 `article/api.md:696` 推翻、计数 86 已被 **102** 推翻 ⇒ **共享面落后于域台账**；仍有效的是「**rpc 必须先于 api**」的排序约束（`:13-14`）。
- `docs/DRIFT/_cross/proto-verification.md` ⇒ 三档回归网：**user/admin 有 `check-proto-static.sh`**；其余 8 个「只有一次性实测」⇒ 但 `:45` 的「脚本一个都没覆盖」**已过期**（实际已覆盖 5 个）⇒ 共享面待收口订正；`comment`/`like` 的 `package` 归一**已签字**。
- `docs/DRIFT/_cross/domain-common-lanes.md` ⇒ **A1~A9 九条机制面轴的定义**（`:77-85`）＋ 写权边界（§3）＋ 跨 2+ 域未闭合项（§4）⇒ 我域的逐轴结论写在各域 `vs-common.md` §2。
- `docs/Intentional_Deviations/article/rpc.md` ⇒ 已签 **2 条**：`SD-2026-10-24-01`（`:16` 全局锁序统一 `article → publication`）· `SD-2026-10-24-02`（`:147` Kafka 四通道不能逐通道切 broker）⇒ 倾向 = **全局一致性优先于逐字复刻**（两处都写了「为什么**不修回去**」）；`article/{api,common}.md` = **0 字节空档**（非漏写）。
- `docs/Intentional_Deviations/comment/` ⇒ 已签 5 条：`SD-2026-10-23-04`（proto `package` 归一，`proto.md:11`）· `CAND-02`（`report_record` 表 Go 侧不存在，`rpc.md:67`）· `CAND-06`（Go 的 `manage` 清位 SQL **必然报错**，`rpc.md:202`）· `CAND-07`（HS384/HS512 Go 接受 RAG 拒，`api.md:79`）· `CAND-08`（保护层覆盖范围 RAG 更严，`api.md:127`）；**未签字候选 3 条**：`CAND-01`（9 处 `NOT NULL DEFAULT`，`rpc.md:22`）· `CAND-03`（authority/dispatch 默认关闭，`rpc.md:169`）· `CAND-09`（DDL 更严，`rpc.md:121`）⇒ **登记类，不推拍板**；`CAND-04` ⛔ **前提已被实测推翻 ⇒ 作废**（`api.md:18`）。
- `docs/Intentional_Deviations/favorite/` ⇒ 已签 **9 条**（`DEV-FAV-API-001/002` · `DEV-FAV-COMMON-001/002` · `DEV-FAV-RPC-001/003/004/006/007`）⇒ 倾向 = **表达层/部署面为主**，仅 1 条是「RAG 更严」（`DEV-FAV-RPC-007` 的 CHECK）；**已撤销 2 条**（`DEV-FAV-RPC-002` / `-005` ⇒ 前提被逐行核对证伪）⇒ **登记 ≠ 签字的前提必须先复核**；`favorite/proto.md:11`「本域暂无条目」。
- `docs/Intentional_Deviations/hot/` ⇒ 已签 **17 条**（`SD-HOT-RPC-001~004` + `SD-1/2/3` + `SD-HOT-API-001~005/007~010`）⇒ 倾向 = **「RAG 更稳 / 更保守 / 单向增强」占多数**；`SD-HOT-API-006` ⛔ 2026-10-25 已撤销（`api.md:22`）；**未签字候选 1 条** = `SD-2026-10-25-CAND-10`（`api.md:332`）⇒ 登记类；`hot/common.md:7`「本组件无单独登记的签字偏离」＋ `:22-34` 两条「看起来像偏离、实际不是」。
- `docs/Intentional_Deviations/_INDEX.md` ⇒ **CAND 族共 14 条未签字** ⇒ 按 §5.3c **不升级为拍板**；⚠️ `SD-` 编号**存在跨域撞号** ⇒ 引用必须给「**文件 + 域**」，不能只写编号。
- `docs/PhaseGoal/g3.md` ⇒ **lane 判定 🟡**（16 组件：✅15 · 🟡1 · 🔴0）；**差距 = `article-rpc` 一个组件**；**§2.1「本 lane 自修」只有 `article-rpc` 一行**（`S-5` + `U-3/U-4/U-9/U-4′`）；**§2.2 转派 G4** = comment-rpc `D8/D9/U13` · favorite-api `U9/405/B2` · favorite-rpc `U7/U9/E65`；**§3 P0 主责 = #4**，协作 #1/#2/#3/#6/#8，不涉及 #5/#7/#9；§5 收口验收命令全可复算。
- `docs/PhaseGoal-judge.md` ⇒ **§1.3** ✅ 判据 = `R ≥ R3` **且** 归属本组件的必相对齐项 = 0 **且** 有**可复算**判定命令；**§1.5 判定权威层模型**（`:93-108`）= **L0 台账「条目级最新状态」是唯一权威** ⇒ **不得引用台账头的「组件判定」行**；**§8.4 阶段一准入 = ❌ 未通过**，三类硬阻断 = **X-11 密钥链（P0 #8）** · **X-12 项目说明（P0 #5）** · like-rpc `DRIFT-LIKE-003` + global `E60` + 共享层 **28 项（A 类 0）**（🔴 **2026-10-29 主线订正**：原写「27 条」⇒ 共享层基数已订正为「**28 项未修 · A 类 0**」⇒ 本树 `docs/DRIFT/global/vs-common.md` 尚未合入该订正 ⇒ **合并后自动一致**）；**§九 证据缺口**：`G-3`（article 双侧活体 0 条，⚫ **可解除**）· `G-5`（hot-common 探针在 `/tmp` ⇒ **本轮已闭合，可撤**）· `G-6` / `G-7` / `G-13`（4 域无失败路径矩阵，含 **article + hot**）· `G-15` / `G-18`（**本轮已订正**）· **`G-19`**（proto 消费方式未取证，10 个 proto 全部）· `G-20`（共享面 `:45` 已过期）。
- `docs/PhasedGoal.md` ⇒ `:38-52` **P0 九条**（准入）· `:54-68` **P1**（至少 2~3 个机制形成闭环）· `:70-78` **P2**（生产级 SLO / 容量规划 / 灾备 / 灰度 / 穷尽故障组合 ⇒ **不阻塞**）；`:26-36` 明确「本节是语言描述，**可判定版在 `PhaseGoal-judge.md`**」。
- `docs/twoStage.md` ⇒ **§4 阶段 0 事实基线**（`:258-310`：事实调查 / 三条核心链路 / 差异基线 9 字段，**必须保留到阶段 2 验收完成**）· **§5.3 差异与暂缓项 11 项必填**（`:359-379`，明写「不得只写『后续优化』」）· **§5.4 改造记录 9 项必填**（`:381-397`，「必须根据计划**实际修改**项目，而不是只输出建议」）· **§10 阶段归属 A/B/C/D**（`:941-975`：**A 类 = 阶段 1 准入阻断项**；**P0–P3 是严重度、A–D 是阶段归属，两者不能互相替代**）。
- `docs/Pitfalls/article/api.md` ⇒ **P-1**（`:11`「没有 error handler ⇒ 一律 400」只走了一半推理）· **P-4**（`:141`「注解存在 ≠ 机制生效」）· **P-5**（`:171`「代码里写了 ≠ 客户端收到」）· **P-15**（`:516` 变异模块没重编 ⇒ **假的仍全绿**）· **P-21**（`:789`「仍全绿」的第一嫌疑是「**变异没打中**」）· **P-22**（`:830`「**javadoc 写了 Go 出处是待核实的断言，不是证据**」）。
- `docs/Pitfalls/article/rpc.md` ⇒ 16 条 `PF-ARTICLE-RPC-001~016`：`007`（`:244` javadoc 里写的「锁死用例」可能根本不存在）· `008`（`:290` 改的是 bean 读的是 Environment ⇒ 不会变红的假绿）· `016`（`:875` `EOFException` 出现在 `PGStream.receiveChar` ⇒ **不是「PG 挂了」**）。
- `docs/Pitfalls/comment/` ⇒ **PIT-COMMENT-RPC-02**（`:69`「**javadoc 里『对应 Go xxx.go:行号』的断言本身可能与真源相反**」）· `PIT-COMMENT-API-03`（`:101`「**自己的 javadoc 会成为『证据』**」）· `PIT-COMMENT-RPC-05`（`:192`「因错误原因而绿」的假守卫）· `PIT-COMMENT-RPC-13`（`:563`「某域把异常改写了」这类登记**必须逐条核 Go 侧**）。
- `docs/Pitfalls/favorite/` ⇒ **PIT-FAV-API-001**（`:11`「javadoc 里的『Go: …』断言会变成**免检通道**」）· `PIT-FAV-API-007`（`:227`「读源码判定」不是「已验」）· `PIT-FAV-API-008`（`:271` `RANDOM_PORT` 挑的端口可能根本不是你的）· `PIT-FAV-API-017`（`:836`「全仓 grep 零命中」⇒ 模式选错时零命中什么也不证明）· **PIT-FAV-RPC-010**（`:384` 注释里的 Go 断言会「指错行」—— **没有任何测试会红**）· `PIT-FAV-RPC-030`（`:1338` 新写了带 Go 锚点的文件 ⇒ **必须跑「管它的那个门禁」**）· `PIT-FAV-RPC-033`（`:1488` 引用 `文件:行号` 前先确认该符号在**本分支**存在）· `PIT-FAV-RPC-037`（`:1714`「盘点归零」的口径外还有一层从未被机检覆盖）。
- `docs/Pitfalls/hot/api.md` ⇒ **P8**（`:296` 读 go-zero 源码前先确认版本 —— 同一台机器的 module cache 里有三个版本）· **P15**（`:512`「变异没红」的第一反应应是「我的变异脚本错了」）· **P16**（`:561` MockMvc 不是「真 Tomcat」）· **P19**（`:674` 单模块跑会用 `~/.m2` 的 jar）· `P-HOTAPI-25`（`:965` `git add -A` 撞上后台正在改工作区的任务 ⇒ **变异代码被提交进 HEAD**）；`docs/Pitfalls/hot/rpc.md` ⇒ **P-HOTRPC-19**（`:623` 变异打在 `target/classes` 会被 `mvn test` 的资源拷贝覆盖 ⇒ 守门**假绿**）· `P-HOTRPC-22`（`:724` **Boot 2/3 的行号在 Boot 4 上对不上**）· `P-HOTRPC-16`（`:519`「缺陷在别的域的台账里」**跨域登记 ≠ 域内认领**）。
- `docs/Pitfalls/_cross/` ⇒ `domain-common-lanes.md` **PIT-LANE-01~07**（`:17` lane 基点早于脚手架那次提交 ⇒ 回并撞 add/add · `:59`「改一处受益全域」**不是**「可以并行改」⇒ 共享层只有一条写权 · `:168` 守门登记了一条**已不存在**的 lane = 恒绿假绿 · `:196` 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后守门恒绿）· `testing-pitfalls.md`（`:35` 绿测试可能是「错误答案的守卫」⇒ 修漂移**先改/删错误断言，再改实现**；`:55` **陈旧 surefire 报告会静默抬高测试计数**；`:108` 守门的**排除清单是架构假设** ⇒ 架构一变就假阳性）· `flyway-ledger-collision.md`（`:5` 唯一例外 = `favorite-rpc` 缺 `baseline-version: 0` ⇒ 只记账不修）· `framework-mechanism-evidence.md`（`:3` 四类**静默失效**的实证：`@Transactional` self-invocation / `@ResponseBodyAdvice` 二次包裹 / `@TableField(typeHandler)` 丢类型信息 / 命令行注入的属性前缀写错）· `fix-round-lessons.md`（`:3-110` 11 条元教训；`:64` **第一轮+第二轮 8 条里 6 条是「跑绿 ≠ 对」**的证据）。

**③ 待批准项**（需 Jason 拍板；**回执人不得自批**）：

1. 🔴 **候选任务与权威基线不一致（本轮最需要裁定的一条）** —— `docs/PhaseGoal/g3.md` **§2.1「本 lane 自修」只有 `article-rpc` 一行**（`S-5` + `U-3/U-4/U-9/U-4′`），而调度员给的候选三条（H-A1 / A-1 / F-1）**一条都不在 §2.1 里**；反过来 §2.1 那 5 项在候选任务里**一条也没有** ⇒ ⇒ **两处对不上**。⇒ **本 lane 不自行改任务范围**，请裁定以哪一份为准（另注：`S-5` 的阻塞源在 Go 侧 `security` 域 ⇒ 按 AGENTS.md §一「`knowledge`/`points`/`security` **范围外 ⇒ 不是待办**」⇒ **`S-5` 本 lane 内不可闭环**）。
2. **H-A1 是否本轮做** —— 归属已按 §5.3f 订正为**域侧自修项**（`tools/proto-wire-probe/hot/` 只被 hot 域引用 ⇒ 真私有）；成本低（hot.proto **无 `optional` / 无 `map`** ⇒ 形态比 favorite 简单）。
3. **A-1 的前提已变** —— `judge` §九 `G-3` 写「⚫ 受阻（宿主端口未映射）」，但实测 `Sea-etcd` 的 `49319→2379` **已在跑** ⇒ **已不是「环境不可得」**，而是「需给 Go 真源副本配 `etcd:2379`（真源 yaml 写死、冻结不可改）」⇒ 是否本轮补跑请裁定。
4. **F-1 环境受限** —— Go 侧 `etcd` / Kafka 仍不可拉起 ⇒ 需裁定「是否仍尝试 / 是否维持登记为最大缺口」。
5. 🟡 **读档时发现的新增台账缺陷（本轮不修，待批准后处理）** —— ① `docs/DRIFT/article/common.md:22` 与 `:74-77` 仍称「消费侧是空骨架 / 本模块零生产调用方」⇒ **与现状冲突**（覆盖矩阵 `:40-41` 记 41 文件 / 5759 LOC）⇒ **未被本文件自身订正**；② `docs/DRIFT/comment/common.md:110` 与 `:112` 同表两行口径相反 ⇒ 读 `:110` 会误判为仍断；③ `docs/DRIFT/_cross/open-items.md:65` 的「article-rpc 按指示未动」仍是 **2026-10-23** 旧状态 ⇒ 与 `article/vs-common.md:319`（`L-1` 补 `baseline-version: 0`）冲突（⇒ **共享面，不在 G3 写权面**）。
6. **登记类事项不占拍板额度** —— `CAND` 族 14 条（`_INDEX.md`）+ `hot` 的 `SD-HOT-API-001~005/007~010` 等已签字项 + `SD-2026-10-25-CAND-10` ⇒ 按 §5.3c **不推拍板、不重复质疑** ⇒ 本回执**无相关待批准项**。
7. ℹ️ **共享面已过期项（登记 ⇒ 归合并收口执行者）** —— `docs/DRIFT/_cross/component-coverage-matrix.md` 当前态节（2026-10-25）落后于域台账 · `docs/DRIFT/_cross/proto-verification.md:45` 仍写「脚本一个都没覆盖」 · `docs/DRIFT/_cross/open-items.md:65` 旧状态 ⇒ **三处均属 SHARED 面 ⇒ 不在 G3 写权面**。

**④ 等批准** ⇒ **批准后才开工**（**回执落笔时**本轮除本回执小节外不写任何代码、不改任何台账；已读动作仅限只读：四档台账 + `_cross` + 阶段标准 + 回执模板）⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182054`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

**⑤ 批准记录：Jason · 2026-10-29 03:44 · 批准范围：本 lane 回执 ① 所列候选任务 **H-A1**（hot proto 消息体 wire 探针落码）+ **A-1**（article 域双侧活体对拍）+ **F-1**（favorite-rpc 整域双侧活体对拍）· **G4 反馈单 1 张**（7 项，含补守门 `PINNED` 两行 `gorm.io/driver/postgres: v1.6.0` + `github.com/lovoo/goka: v1.1.16`）· **自有写权面 2 处台账过期描述**（`article/common.md:22` / `:74-77`、`comment/common.md:110` vs `:112`）**　← 由**调度员代填**（依据：Jason 于 2026-10-29 03:44 在**本 lane 会话内**明示「继续」· 消息 id `182054`；**回执人不得自批**· **代填 ≠ 代批准**）
