<!-- @new 2026-10-23 · task 域组件级对照（rpc 先于 api）· 由 `align/task` 工作树产出 -->

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部「核对方式」停在首轮对照，而正文已到「四·十」台账复核 + **三轮复核**）⇒ **当前状态一律以文件末尾「四·十「台账 vs 实际」复核（2026-10-24，含三轮复核与复核器自身的变异测试）」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

# `task-rpc` 组件级语义对照台账

> **基线 commit**：`8422116`（`do-my-own-gh`）
> **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/task`，分支 `align/task`
> **核对方式**：Go 侧逐行读完 `service/task/rpc/` 全部源码（2,250 行，排除 `pb/` 生成代码）；
> RAG 侧逐行读完 `service/task/task-rpc/src/main/` 全部 29 个类 + pom + application.yml + 两份 DDL。
> **Go 真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`，go-zero `v1.10.2`、goka `v1.1.16`（版本已核 `go.mod`）。
> **本文件属主**：`align/task` 工作树。共享索引（`docs/DRIFT/_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`）**未触碰**，按 `docs/Worktree.md` §3 留给合并后统一更新。

---

## 〇、结论

| 项 | 结论 |
|---|---|
| 语义等价性 | ✅ **首轮为 🔴 NOT EQUIVALENT（5 条漂移），现已全部修复并复核**（2026-10-24：Kafka 端到端 + HTTP 双侧对拍均 0 DIFF）⚠️ 效力边界见 §五 |
| 已修 | **6 条**（DRIFT-TASK-RPC-001 / 002 / 003 / 004 / 005 / **006**），每条配守门测试 |
| 未修 | **0 条语义漂移**；另有 2 条**已签字偏离**（`SD-2026-10-23-TASK-01` 与 `SD-2026-10-24-TASK-01`，见 §二） |
| 证据强度 | ✅✅✅ **Kafka 端到端活体对拍**（2026-10-23 23:4x，真 Kafka + 真 Redis + 真 PG，两侧同批消息比最终态，见 §四·三）· ✅✅✅ **Kafka 畸形消息活体对拍**（2026-10-24，五个用例，见 §四·五·二）· ✅✅ **gRPC 双向跨语言对拍**（2026-10-24，Java 客户端 + **Go 客户端**各打两端，见 §四·五·一）· ✅ 真启动 + 真 PG 16 容器 |
| DDL 对拍 | ✅ **GORM AutoMigrate 产物 vs Flyway 产物逐列一致**（本轮实测，见 §四·二） |
| ⚠️ 仍未覆盖 | ⓿ **「Kafka 写入 → task-api 读到」这条跨域闭环**未做活体对拍 —— 但两侧的读写 Redis key **本来就不一致**（Go 侧也读不到自己写的缓存），⇒ **不是 RAG 的缺陷**，见 §五 第 7 条；① ⚠️ **goka 的「表 topic」config（`cleanup.policy` / `retention.ms`）未做逐项对照** —— 但已实测「compact 不按时间删」且 Go 侧**不创建** topic（§四·七）⇒ 两侧**都不管** topic 生命周期 ⇒ 一致；② ⚠️ **yaml 里查出了三类「死配置」**（`spring.mybatis-plus.*` / `otel.*` / `spring.jpa.*`，§四·九）—— **零行为影响**（三处逐一核过），但 `otel.*` **与 Go 存在观测差异**（Go 真导出 span，Java 侧 no-op）⇒ **全部是跨域共性**（8~20 个模块同款），本域不单方面修 |
| 测试 | **115 tests / 0 failures / 0 errors / 0 skipped**（**2026-10-27 G2 第二轮实测口径 = `mvn test`**；其中 **17 条真 PG**（`TaskProgressUpsertParityTest` 15 + `TaskRpcSlowThresholdGateTest` 2 · Testcontainers）、**9 条真 Redis**、**4 条真 gRPC server**（`TaskGrpcInterceptorWiringTest` 2 + `TaskRpcSlowThresholdGateTest` 2）；另 2 条**装配层**（mock `ServerBuilder`）+ **4 条产物层**（解包真 fat jar ⇒ 只在 `mvn verify` 阶段跑，`mvn test` 阶段 jar 还没生成）；task-api 另 **97** 条，合计 **208**）<br>🆕 **2026-10-27 订正**：原写「106 / 真 PG 15 / 真 gRPC 2 / 合计 196」⇒ **过期 8 条** —— `align/global` 的 **L12 / L13 / L15** 三个 lane 随 `4f839f9` 合入主干时给 task-rpc 加了 **3 个守门类 / 8 条用例**（`TaskProtectionChainWiringTest` 2 + `TaskRpcFatJarArtifactGateIT` 4 + `TaskRpcSlowThresholdGateTest` 2）⇒ **106 + 8 = 114**（那是 **`mvn verify`** 口径）<br>🆕 **2026-10-27 阶段 2 再订正（口径对齐）**：本行的数字**必须与 `check-ledger-selfcheck.py` 第 3 层的实测口径一致** —— 该层读的是 `target/surefire-reports` ⇒ 即 **`mvn test` 口径** ⇒ **`mvn verify` 才有的 4 条产物层（`TaskRpcFatJarArtifactGateIT`）不在其中** ⇒ 本行 **114 ⇒ 111**（阶段 2 给 `TaskProtectionChainWiringTest` 补了 1 条降载层断言：2 ⇒ 3）⇒ **`mvn verify` 口径 = 111 + 4 = 115**（`mvn test` 阶段 jar 还没生成）<br>⇒ 证据：**surefire XML 实测 14 个测试类 / 115 条 / 0 failures / 0 errors / 0 skipped**（2026-10-27 **G2 第二轮** · 三域六模块全量 `mvn test`：task-api **97** / task-rpc **115** / message-rpc **84** / message-api **91** / like-rpc **111** / like-api **84** ⇒ **BUILD SUCCESS**，0 failures / 0 errors / 0 skipped）<br>🆕 **2026-10-27 G2 第二轮订正（口径对齐）**：阶段 2 写 **111**（13 个测试类）⇒ 本轮新增 `TaskRedisCommandTimeoutGateTest` **4 条**（Redis 命令超时 3s ＋ 连接超时 5s · 见 [DRIFT-TASK-RPC-007](#🔴-drift-task-rpc-007--redis-命令超时未配--落-lettuce-默认-60s--断连时命令挂-60sgo-是-17s-快速失败)）⇒ **111 + 4 = 115**（14 个测试类）⇒ 同族 like-rpc 同步 **108 + 4 = 111**<br>⚠️ **取数口径的坑（本轮实测踩到）**：surefire 的 `-- in <类名>` 行里，**测试类的 `@DisplayName` 含中文时打印的是 DisplayName 而非 FQCN** ⇒ 用 `grep 'in com\.rag\.task\.'` 求和会**漏掉**中文 DisplayName 的那几条 ⇒ 明细求和（213）与 reactor 汇总（226）不符 ⇒ **必须按 reactor 汇总行为准**，或用「按模块段切分后逐类求和」交叉验 ⇒ 本轮两口径已对齐为 like-rpc **111** / task-rpc **115** |

### 〇·一、本组件的守门测试清单（2026-10-24 复核补登）

> ⚠️ 此前台账**只点名了部分测试类**，其余在册但没登记 ⇒ 复核时判定为「覆盖缺口」。
> 本节把 task-rpc 的**全部**测试类登记在此，每个都写清它**锁住哪一条契约**。
> ⚠️ 「**测试全绿**」≠「这些契约被验证过」—— 每个测试类的**触发条件**才是关键
> （例如 `RedisPipelinesRealRedisGateTest` 必须接**真 Redis**，`TaskProgressUpsertParityTest` 必须有 **真 PG**）。

| 测试类 | 锁住的契约 | 关键前提 |
|---|---|---|
| `TaskKafkaOffsetResetContractTest`（5） | **DRIFT-TASK-RPC-006**：六个 listener 的 `auto.offset.reset` 必须是**点分**（连字符被 Kafka 静默忽略）＋ 通用断言（所有 per-listener 键必须属 `ConsumerConfig.configNames()`） | 阳性对照用 Kafka 自己的 `ConsumerConfig` |
| `TaskCountStoreRetentionContractTest`（4） | **保留策略定案**：`task:goka:count:*` **不设 TTL**（加了会造成业务错误） | **真 Redis**（非 mock） |
| `TaskConfigKeyBindingContractTest`（5） | **yaml 键名不得静默失效**：每个键都要有消费方（框架元数据 ∪ `@ConfigurationProperties` 前缀 ∪ `@Value`）＋ 白名单反向纪律 | 框架元数据来自 classpath 各 jar 的 `spring-configuration-metadata.json` |
| `RedisPipelinesRealRedisGateTest`（9） | **DRIFT-TASK-RPC-004/005**：pipeline 语义（**不是** MULTI/EXEC）＋ 错误**向上抛**不吞 | **真 Redis**（非 mock）—— 这是「整条消息链路是死的而 81 条测试全绿」那次的教训 |
| `TaskProgressUpsertParityTest`（15） | **DRIFT-TASK-RPC-003**：两条 upsert 与 Go 的 `AutoMigrate` 产物逐字对齐 | **真 PG** ＋ `@EnabledIfDockerAvailable` ⇒ **无 Docker 时会静默全跳** ⇒ CI 必须有 Docker |
| `TaskServiceGrpcImplWireTest`（4） | **wire 层**：客户端真正收到的 gRPC code + description（Go `get_task_logic.go` 的 `codes.InvalidArgument` / `"user_id invalid"`） | **真 channel**（InProcess）—— 领域层抛对了 ≠ 客户端收到了 |
| `TaskGrpcInterceptorWiringTest`（2）🆕 | **gRPC 拦截器注册链**（Go `task.go:28` `DisableNativeRpcTimeout` ＋ `task.go:44` `AddUnaryInterceptors(NewUnaryServerInterceptor(timeout, SlowThreshold()))`）：**服务端超时真的由容器里的 `GrpcServerConfigurer` 施加到 server 上**（≈300ms 返回 `DEADLINE_EXCEEDED`，而不是等满客户端 5s deadline）＋ 生产 `application.yml` 的 `observability.rpc-timeout` 必须是 **2s**（Go `task.yaml` 未配 ⇒ 走 go-zero `zrpc/config.go` 的 `default=2000`） | **真 gRPC server**（用容器里那个 `GrpcServerConfigurer` 装配一个「睡 10 秒」的服务）＋ **真 PG**<br>⚠️ **只断言「容器里有 `ServerInterceptor` Bean」是假绿** —— devh starter 3.1.0 **从不自动注册**它（common 域已 `javap` 证伪，H2）⇒ 缺 `@Bean GrpcServerConfigurer` 时超时/deadline 传播/span **全部静默消失** |
| `TaskSinkConsumeTest`（19） | 两个 sink 的消费路径（进度写 Redis + `like:cnt` 语义） | mock 领域层 |
| `TaskDomainServiceTest`（19） | `GetTask` 的领域逻辑（合并任务定义、pack/unpack 进度） | mock 数据层 |
| `TaskEventParsingTest`（21） | `LogEvent` 解析（`uid <= 0` / `total <= 0` 的拒绝语义） | 纯函数 |
| `TaskSinkPropertiesBindingTest`（3） | **DRIFT-TASK-RPC-002**：`task.definitions.*` 的 TTL 键**真的绑得上**（`init-ttl` ↔ `initTtl`） | 加载**真实** `application.yml`（非测试专用 yml） |
| `TaskProtectionChainWiringTest`（2）🆕 | **DRIFT-MSG-V-026**：task-rpc 的**熔断 / 降载链**必须真的挂到 gRPC server 上 —— ① `TaskRpcApplication` 必须 `@Import GoStyleProtectionConfiguration`（否则 properties Bean 根本不存在）② 链上**真的有** breaker / shedding 包装层（业务 interceptor 被调到，且拿到的是**包装过的** call） | **mock `ServerBuilder`**（不起真端口）⇒ 断言的是**装配层**（容器里有没有那层包装），**不是**「熔断真的生效」 |
| `TaskRpcFatJarArtifactGateIT`（4）🆕 | **产物层**：解包**真实构建产物**（fat jar）断言它是**可执行的 Spring Boot fat jar**（`MANIFEST.MF` 有 `Main-Class` ＋ `BOOT-INF/lib/` 里有 `grpc-inprocess`）⇒ 挡住两类启动阻塞：打成**普通 library jar**（`java -jar` 报 no main manifest attribute）／`grpc-inprocess` 不在运行时 classpath 上 | ⚠️ **只在 `mvn verify` 阶段跑**（surefire 的**第二个 execution** `artifact-gate`，只挑 `**/*IT.java`；`*IT` 不匹配默认 include）⇒ `mvn test` 阶段 fat jar 还没生成 ⇒ **这条守门在 `test` 阶段等于不存在**（⇒ 复核测试数时必须按 `mvn verify` 口径） |
| `TaskRpcSlowThresholdGateTest`（2）🆕 | **慢阈值**：在飞 400ms > 阈值 100ms 的调用**必须**被打上 `sea.slow=true`；快调用**必须不带** ⇒ 证明这条路径是**真比较出来的**（不是恒真标记） | **真 PG**（Testcontainers `postgres:16-alpine`）＋ **真 gRPC server**（`ServerBuilder.forPort(0)`） |

⚠️ **`TaskProgressUpsertParityTest` 的 15 条在无 Docker 环境下是 `skipped` 而非 `failed`**
⇒ 「全绿」可能意味着「一条没跑」⇒ 复核测试数字时**必须看 `skipped`**，不能只看 `failures=0`。

### 〇·二、本组件的**生产类**清单（2026-10-24 复核补登）

> ⚠️ 此前台账**只登记了测试类**，**生产类**一个都没点名 ⇒ 复核时判定为覆盖缺口。
> 本节把 task-rpc 的**全部生产类**登记在此。
> ⚠️ **登记它们的价值不在于「列全了」**，而在于：这些类里藏着**四个「缺了就起不来」**的
> 启动阻塞项（见下），台账不点名 ⇒ 以后有人重构时**看不到这条风险**。

| 生产类 | 作用 | ⚠️ 缺了会怎样 |
|---|---|---|
| `bootstrap/NacosRegistration` | 显式注册到 Nacos（对应 Go `task.go:37` 的隐式 etcd 注册） | ⚠️ Bean 名与 `NacosServiceRegistryAutoConfiguration` 撞名（`DRIFT-MSG-004`）⇒ `allow-bean-definition-overriding=false` ⇒ **上下文直接起不来** |
| `config/Jackson2ObjectMapperConfiguration` | 提供 **Jackson 2** 的 `ObjectMapper` Bean | ⚠️ Boot 4 默认给的是 **Jackson 3**（`tools.jackson`），包名不同、API 不通用 ⇒ `NoSuchBeanDefinitionException: ObjectMapper` ⇒ **组件起不来**（纯 Mockito 单测照样全绿） |
| `config/MybatisPlusOrderingBridgeAutoConfiguration` | MyBatis-Plus 自动配置**排序**桥接（Boot 4 专用 workaround） | ⚠️ MP 的 `@AutoConfigureAfter` 指向 **Boot 3 的包名** ⇒ Boot 4 下**静默悬空** ⇒ MP 先于 DataSource 求值 ⇒ `@ConditionalOnSingleCandidate(DataSource)` 找不到 bean ⇒ **整个自动配置不生效**（无 SqlSessionFactory） |
| `config/TaskSinkProperties` | `@ConfigurationProperties("task.definitions")` 宿主 | ⚠️ 键名与属性名对不上 ⇒ **静默丢弃**（`DRIFT-TASK-RPC-002`） |
| `config/TaskKafkaTopics` | `@ConfigurationProperties("task.kafka")` 宿主（12 个 topic / group 名） | ⚠️ 同上；且这些 topic 名是**跨域契约**（like-rpc 也在生产其中两个）⇒ 改一个字符就断链 |
| `domain/TaskProgressEntity` | 任务进度实体（`task_progress`） | ⚠️ 只承载**读路径**（`GetTask` 回源）与 `TaskSchemaGate` 的存在性说明；写入一律走显式 SQL |
| `domain/UserLikeCountEntity` | 点赞总数实体（`user_like_count`） | ⚠️ `userId` 在本表是 **String/TEXT**，在 `TaskProgressEntity` 是 **Long/BIGINT** ⇒ **不要「顺手统一」**（统一会让 Go 写的行 Java 查不出来） |
| `mapper/UserLikeCountMapper` + 内部 `UserLikeCountRow` | `user_like_count` 的**唯一**写路径（`batchUpsertGreatest`） | ⚠️ 用**独立 POJO** 而非实体当参数容器（混用会造成「MyBatis-Plus 会接管这条 SQL」的错觉） |
| `mapper/TaskProgressMapper` | `task_progress` 的三段 upsert（**全部写显式 SQL，不用 BaseMapper**） | ⚠️ 复合主键 + `ON CONFLICT` 的列集必须逐条对齐 Go 的 `clause.OnConflict` |
| `mq/GoJson` | Go `encoding/json` 的**类型判定**还原 | ⚠️ Go 侧 `json.Unmarshal` 对类型不符是**报错并保持零值**（不是 Jackson 的宽松行为）⇒ 不用它 ⇒ 类型判定分叉 |
| `mq/TaskTraceAttributes` | 两个 sink 的 OTEL span 属性 | ⚠️ attribute **名字与取值**属于可观察行为（Jaeger 里的 span 结构）；`messaging.destination` 传的是 Go 里写死的**逻辑名**（`user_like_sink` / `article_like_sink`），**不是 topic 名** |
| `service/TaskProgressWriter` | 把 Go 两个 sink 里**逐字相同**的三段 upsert 收敛成唯一实现 | ⚠️ 合并的前提是「除 target 常量与结构体名外完全一致」（已逐条比对） |
| `service/TaskRewardPublisher` | 任务完成奖励事件发布（`XADD` 到 `stream:task:reward`） | ⚠️ **只写不读**：Go 侧 `reward.Worker` 与 `StartInitReward` **整个文件都是注释**（`task.go:35` 的调用也被注释，因其依赖的 `PointsClient` 在 `service_context.go:18-19` 就被注释）⇒ 线上只有堆积的 XADD 记录、**无消费者** ⇒ Java 侧**刻意不补**（那是新增行为） |
| `service/impl/TaskServiceGrpcImpl` | `TaskService` 的 gRPC 入口（对应 Go `internal/server/task_service_server.go`） | ⚠️ Go 的 server 层**没有任何逻辑**（薄转发）⇒ 本类同样保持薄转发 + **唯一的错误转换点**（两类异常两个去向） |

**task-api 侧的两个**（在 `api.md` 覆盖，此处登记以便一屏看全）：

| 生产类 | 作用 | ⚠️ 缺了会怎样 |
|---|---|---|
| `task-api/client/TaskRpcClient` | task-rpc 的 gRPC 客户端（对应 Go `api/internal/svc/servicecontext.go:22-32`） | ⚠️ Go 侧是**直连**（`Target: 127.0.0.1:9005`，无 Etcd 段）⇒ Java 侧默认也直连，切服务发现只改环境变量 |
| `task-rpc/TaskRpcApplication` | rpc 进程的 `@SpringBootApplication`（含 `@ConfigurationPropertiesScan` 与 `@MapperScan`） | ⚠️ **扫描包写错 ⇒ 整个模块装配不起来**（且不报错） |

⚠️ 另有两个**跨域共享**的类不在本模块：`TaskCountStore` / `TaskDomainService` 等已在
§一 各条中点名；`TaskRawLogRouter` / `TaskUserCountConsumer` / `TaskArticleCountConsumer` /
`TaskRawDiagnosticConsumer` / `UserLikeProgressSink` / `ArticleLikeProgressSink` 六个 listener 见 §四·八。

---

## 一、已修（6 条）

### 🔴 DRIFT-TASK-RPC-001 · `TaskCountStore` 内存单例 ⇒ 两个 goka group 串号 + 重启归零

**性质**：SEMANTIC MISMATCH（行为层，两处）

**Go 依据**（goka `v1.1.16`，本条结论不依赖任何推测，全部读到消费点）：

| 事实 | 出处 |
|---|---|
| 默认存储 = **按 group 名分目录的 LevelDB 落盘** | `storage/builders.go:8-17` `DefaultBuilder(path)` → `leveldb.OpenFile(filepath.Join(path, "<topic>.<partition>"))` |
| 该默认值的注入点 | `processor.go:82` `WithStorageBuilder(storage.DefaultBuilder(DefaultProcessorStoragePath(gg.Group())))` |
| 未注入即报错（不会悄悄退化成内存） | `options.go:361-363` `if opt.builders.storage == nil { return fmt.Errorf("StorageBuilder not set") }` |
| Go task **两次独立 NewProcessor** | `initGoKa.go:178-181`（用户路）、`:212-215`（文章路） |
| 计数语义本体 | `initGoKa.go:75-81` / `:92-97`：`cur := ctx.Value(); cur++; ctx.SetValue(cur)` |

**RAG 修复前的状态**：`TaskCountStore` 是单个 `@Component` + 单张 `ConcurrentHashMap`，被 `TaskUserCountConsumer` 与 `TaskArticleCountConsumer` 共用。

⇒ 两处不等价：

1. **跨 group 串号**。用户路 `task.rpc.consumer61` 的 key 是 `user_id`，文章路 `task.rpc.consumer71` 的 key 是 `article_id`
   （`TaskRawLogRouter.route` 分别以 `event.userId()` / `event.articleId()` 为键发出）。
   两个 id 空间会相交：一旦出现 `user_id == "190"` 且 `article_id == "190"`，两条链路的计数互相污染，
   之后 emit 出去的 `cur` 全部错误 ⇒ 下游两个 sink 的达标判定跟着错。
   ⚠️ **该类 javadoc 原本写着「每个消费组各持有一份」—— 注释描述的是代码没有实现的契约**
   （AGENTS.md §3.3.2 的典型：断言无出处/无证据）。
2. **重启归零**。纯进程内存 ⇒ 重启后所有计数器从 0 起算，而 Go 是 LevelDB 落盘、跨重启连续。
   原 javadoc 的论证「重启后从 0 重新累加，与 goka 从文件恢复相比只是重复计数，
   而下游用的是 max(旧值,新值) 与 SETNX 完成幂等，**重复计数不会造成业务错误**」——
   🔴 **经逐条核对，该论证不成立**：
   - `like:total:<uid>` 是**普通 SET**（`UserLikeProgressSink.pipelineSetLikeTotal`），**不是 GREATEST**
     ⇒ 重启后被**更小的值覆盖**；
   - 达标判定 `total >= target(5)` 读的是**计数器**而不是 DB
     ⇒ 用户必须重新攒够 5 次任务才会完成；
   - 仅 `task:done:<uid>:<taskId>` 的 SETNX（90 天 TTL）不受影响，已完成任务不会被重复发奖。

**修法**：`TaskCountStore` 改为 Redis `INCR`，**按 group id 命名空间隔离**：
`task:goka:count:<groupId>:<messageKey>`。两个 consumer 分别传 `topics.getGroupGokaUser()` /
`getGroupGokaArticle()`，与 goka「按 group 名分目录」一一对应。

- **为什么选 Redis 而不是本地文件**：goka 的**权威副本是 Kafka compacted topic `<group>-table`**，
  本地 LevelDB 只是缓存（`goka doc.go` / `storage` 包注释：「a persistent cache for a topic table」）。
  Redis `INCR` 在**原子 / 跨重启 / 跨实例共享**三点上都更贴近那张表，且 Redis 已是本模块硬依赖
  （`application.yml` 的 `spring.data.redis` 对应 Go `LikeRedis`）。
- **失败语义**：Redis 不可用时**抛异常**让位点不提交（`AckMode.RECORD` + `setAckAfterHandle(false)`），
  对应 Go 侧 goka store 写失败时的退避重投；**不**吞掉异常返回 1
  —— 那会把「计数没加上」伪装成「加了一次」，比丢数据更难排查。
- **新 key 是 RAG 侧实现产物**：Go 侧不存在这个 Redis key。刻意**不复用** `TaskRedisKeys` 的
  `task:progress / task:init / task:done / task:inits / like:total` 任何前缀，避免被误读成 Go 也有的业务键。

**守门测试**：`TaskSinkConsumeTest#countStoreIsolatesGroups`
—— 同一个 id 字符串 `"190"` 分别在两个 group 里累加，必须各自从 1 起（修复前会串成 1,2,3,4）。

---

### 🔴 DRIFT-TASK-RPC-002 · `task.definitions` 的四个 TTL 键是**死配置**

**性质**：配置面失效（行为当前正确，但「配了不管事」）

**Go 依据**：TTL 在 Go 侧是**编译期常量**，无配置面
（`like_sink.go:47,188,284,339` 全部 `90*24h`；`article_sink.go:22` `articleDoneTTL = 90*24h+30*24h`；
`article_sink.go:23` `articleInitTTL = 24h`）。

**RAG 修复前的状态**：`application.yml` 把这些常量提到了配置面，并写明理由
（「提到 yaml 只是为了让『改这个值』必须显式可见，而不是藏在代码魔数里」，`application.yml:184-185`）。
但键名与字段名对不上：

| yaml 键 | Java 字段（修复前） | relaxed binding |
|---|---|---|
| `task.definitions.like.init-ttl` | `initTtlDays: long` | `initttl` ≠ `initttldays` ❌ |
| `task.definitions.article.init-ttl` | `initTtlDays: long` | ❌ |
| `task.definitions.article.done-ttl` | `doneTtlDays: long` | ❌ |

且 `@ConfigurationProperties` 的 `ignoreUnknownFields` 默认 `true`
⇒ **对不上的键被静默丢弃，不报错也不生效**。AGENTS.md §3.3.1 那类
「配置面看起来在管事、实际没在管事」。

**实测证据**（`TaskSinkPropertiesBindingTest`，含**阳性对照**）：

```
Tests run: 3, Failures: 1
  ✅ 阳性对照  redis-pipe-max=1234 → 生效        ⇒ 探针有识别能力，不是空过
  ✅ 断言1    生效值 == Go 常量（90d/24h/120d/5000/2000/1000） ⇒ 当前行为正确
  ❌ 断言2    init-ttl=7d 覆盖后实测 PT24H（默认值）⇒ 该键没绑上
```

**修法**：字段类型 `long` → `java.time.Duration`，键名与 yaml 逐字对应
（`init-ttl` / `done-ttl`），默认值仍为 Go 常量。
不是把 Go 的常量变成可配漂移，而是让**已声明的配置面真正生效**。

**守门测试**：`TaskSinkPropertiesBindingTest`
（断言1 = 生效值必须等于 Go 常量；断言2 = 配置面必须是活的；阳性对照 = 键名对得上的键必须绑得上）。

> ⚠️ **备选方案（未采纳，留档）**：另一种做法是**删掉这四个死键、保持常量在代码里**，
> 那更贴近 Go（Go 本来就没有配置面）。未采纳的理由是 `application.yml` 里的那句声明是作者的**明确设计意图**，
> 单方面删掉属于推翻决策；且「配置面可改」与「默认值 == Go 常量」并存时，风险由断言1 兜住。

---

### 🔴 DRIFT-TASK-RPC-003 · 15 条真 PG 用例静默全跳（证据缺失）

**性质**：不是行为漂移，是**证据链断裂** —— 本域唯一能证明「Flyway 真跑 + MyBatis-Plus 真产出
SqlSessionFactory + `ON CONFLICT`/`GREATEST`/`COALESCE` 真按 PG 语义执行」的 15 条用例，
**一条都没跑**，而 reactor 报 `BUILD SUCCESS`。

根因（实测非推测）与修法详见 [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md)。
一句话：`@EnabledIfDockerAvailable` 类级求值要拿 Docker client ⇒ 触发拉 Ryuk 镜像 ⇒
拉不到抛 `ContainerFetchException` ⇒ 被 `DockerAvailableDetector` 的 `catch (Throwable) → false`
压成「Docker is not available」⇒ 整类 disable。`task-rpc` 是**全仓唯一漏配
`TESTCONTAINERS_RYUK_DISABLED` 的容器模块**。

修法照同仓既有写法（`favorite-rpc` / `follow-rpc` 同款 surefire `environmentVariables` 注入）
+ 删除该注解。实测：`tests=15 skipped=0`（此前 `skipped=15`）。

---

### 🔴 DRIFT-TASK-RPC-004 · `RedisPipelines` 把 Go 的 pipeline 当成 MULTI/EXEC 事务 ⇒ **整条消息消费链路是死的**

**性质**：**行为漂移 + 静默失效**。不是「差一点」，是 RAG 侧的 task 消息处理链路**根本不工作**。

**发现方式**：2026-10-23 Kafka 端到端对拍（真 Kafka + 真 Redis + 真 PG，两侧同时灌同一批 raw 日志）。
⚠️ 这个缺陷**不可能**被此前的任何手段发现 —— 详见末尾「为什么 81 条测试全绿」。

#### 症状

RAG 侧日志出现 4 次（生产实测栈）：

```
io.lettuce.core.RedisCommandExecutionException: ERR EXEC without MULTI
  at io.lettuce.core.internal.Future.awaitAll(Futures.java:213)
  at org.springframework.data.redis.connection.lettuce.LettuceConnection.closePipeline(LettuceConnection.java:596)
Caused by: com.rag.task.task.rpc.service.RedisPipelines.exec(RedisPipelines.java:48)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.pipelineSetNx(UserLikeProgressSink.java:322)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.lazyInitTaskIfNeeded(UserLikeProgressSink.java:269)
  at com.rag.task.task.rpc.mq.UserLikeProgressSink.lambda$flushOnce$1(UserLikeProgressSink.java:223)
  at com.rag.common.observability.MqConsumer.traceConsumer(MqConsumer.java:107)
```

#### 根因：Go 的 `Pipeline()` **不是事务**，而 RAG 多发了一个 `EXEC`

| | Go（真源） | RAG（修复前） |
|---|---|---|
| 取 pipeline | `pipe := c.rdb.Pipeline()`（`like_sink.go:167`、`like_sink.go:210`） | `redis.executePipelined(new SessionCallback(){...})` |
| 收尾 | `pipe.Exec(ctx)` | 回调里**再调** `operations.exec()` |
| 是否 MULTI/EXEC | **否** | 是（且 pipeline 模式压根没发过 MULTI） |

Go 侧出处（go-redis v9.20.0，**逐条核过**）：

- `redis.go:1508-1513` `Client.Pipeline()` 的 `exec` 是 `pipelineExecer(c.processPipelineHook)` ⇒ **不包 MULTI/EXEC**；
- `redis.go:1521-1528` `Client.TxPipeline()` 才调 `wrapMultiExec(ctx, cmds)` —— **这才是事务**；
- `pipeline.go:18-20` 源码注释原文：*"Pay attention, that **Pipeline is not a transaction**"*。

Spring 侧出处（spring-data-redis 4.0.7）：

- `LettuceConnection.java:567-575` `openPipeline()` **只置标志位，不发 MULTI**；
- `LettuceConnection.java:688-719` `exec()` 在 `isPipelined()` 分支里直接向服务端发 `EXEC` ⇒ 服务端回 `ERR EXEC without MULTI`；
- `RedisOperations.java:118-128` `executePipelined(SessionCallback)` 的 javadoc 明文：
  *"the callback **cannot** return a non-null value"*、*"SessionCallback#execute(RedisOperations) itself **must return null**"*；
- `RedisTemplate.java:464-467` 框架会**强制**抛 `InvalidDataAccessApiUsageException("Callback cannot return a non-null value…")`。

⇒ 修复前那个 `operations.exec()` **同时**违反了框架契约**和** Go 语义。

#### 🔴 真实杀伤面（不是「报个错」那么轻）

`ERR EXEC without MULTI` 抛在 `lazyInitTaskIfNeeded` 的**第一步 SETNX** 上：

```
SETNX task:init:<uid>:1902  ⇒ 抛
  ⇒ 穿出 lazyInitTaskIfNeeded
  ⇒ 穿出 flushOnce
  ⇒ 被 flushTick catch
  ⇒ **整轮 flush 中止**
```

⇒ `like:total:*`、`task_progress` 写入、任务完成（`task:done:*`）、奖励事件（`stream:task:reward`）
**全部不发生**。也就是说：**RAG 的 task 消息处理链路此前实际是死的** ——
Go 侧同一批消息能正常落库，RAG 侧一条不落。

#### 修法

`RedisPipelines` 两个 helper 统一改成 `executePipelined` + 回调内**不调 `exec()`、返回 `null`**：

- `exec(...)`：**出错抛出**（对应 Go 的 `return err`）；
- `execQuietly(...)`：**吞掉错误**（对应 Go 的 `_, _ = pipe.Exec(ctx)`）。

⚠️ `execQuietly` 修复前用的是 `redis.execute(SessionCallback)` —— 那是**非 pipeline** 路径，
命令**逐条立即执行**，既不批量、又多发一条 `EXEC`。现在统一走 `executePipelined`。

**运行证据**（真 Redis 探针 + 变异测试，见 §四·四）：

| 项 | 修复前 | 修复后 |
|---|---|---|
| `executePipelined` 结果 | 抛 `RedisPipelineException` | `[true,true,true]`，元素类型 `java.lang.Boolean` |
| SETNX 幂等 | 第二次仍抛 | 第二次 `[false,false,false]` |
| 批量性 | 逐条 1719 ms | pipeline 37 ms（**~42x**） |

#### 🔴 为什么 81 条测试全绿

**没有任何一条测试用真 Redis 跑过 pipeline 路径**：

- `TaskSinkConsumeTest` 把 `StringRedisTemplate` 整个 mock 掉，只验内存 delta ⇒ **根本不碰 Redis**；
- `TaskProgressUpsertParityTest` 只验 PG，Redis 指着 `127.0.0.1:1`（不可达）。

⇒ 这是 AGENTS.md §3.3「跑绿 ≠ 对」的又一实例，且比 `article-rpc` 那次更隐蔽：
**测试全绿、编译通过、上下文能启动，但组件的核心功能一行都没执行过**。

**修法**：新增 `RedisPipelinesRealRedisGateTest`（真 Redis 容器，9 条用例，含阳性对照），
并用**变异测试**验证它不是假绿 —— 把 `exec()` 退回 `operations.exec()` 后 **9 条里 5 条报错**。

---

### 🔴 DRIFT-TASK-RPC-005 · `pipelineHSet` 吞掉 Redis 错误，而 Go 是**向上抛**（终态分叉）

**性质**：行为漂移。与 005 同源（都在 Redis pipeline 错误处理上），但**方向相反**，单独开单。

| 路径 | Go（真源） | RAG（修复前） |
|---|---|---|
| `lazyInit` 的 HSET | `like_sink.go:223-225` `if _, err := pipe2.Exec(ctx); err != nil { return err }`<br>`article_sink.go:308-310` 同款 ⇒ **抛** | `RedisPipelines.execQuietly` ⇒ **吞** |
| `GetTask` 的缓存回填 | `get_task_logic.go:73-76` `_, _ = pipe.Exec(ctx) // 回填失败不影响返回` ⇒ **吞** | `execQuietly` ⇒ 吞 ✅ **本来就对** |

**后果**（用户路径 HSET 失败时）：

- Go：`lazyInitTaskIfNeeded` 抛 ⇒ `flushOnce`（`like_sink.go:134-142`）**直接 return** ⇒
  `flushRedis` / `flushPostgres` **都不执行** ⇒ 最终态：**Redis 无进度 Hash、PG 无初始行**；
- RAG（修复前）：吞掉错误 ⇒ 继续走 `progressWriter.lazyInit(records)` ⇒
  最终态：**PG 有初始行、Redis 进度 Hash 缺失**。

⇒ **两侧终态分叉**，且 RAG 侧多写了一半数据。

**修法**：`UserLikeProgressSink.pipelineHSet` 与 `ArticleLikeProgressSink` 的 HSET 改用
`RedisPipelines.exec`（抛），只有 `TaskDomainService.fillProgressCache` 保留 `execQuietly`（吞）——
因为它对应的 Go `get_task_logic.go:76` 明确写着「回填失败不影响返回」。

⚠️ 这条**不是**「统一风格」：用错吞/抛就会分叉，所以两个 helper 的契约写进了 javadoc。

### 🔴 DRIFT-TASK-RPC-006 · per-listener 的 offset-reset 写成了**连字符** ⇒ Kafka 直接忽略 ⇒ **earliest 从未生效**

**性质**：SEMANTIC MISMATCH（行为层，且**上一版台账记的是「配置意图一致」** ⇒ 结论本身就是错的）

**这是 2026-10-24 活体验证「goka 的 OffsetOldest」时查出来的**（§四·八）。

| 路径 | Go（真源） | RAG（修复前） |
|---|---|---|
| goka 路由组 | `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest`（`initGoKa.go:131`）**真实生效** | `@KafkaListener(properties = "auto-offset-reset=earliest")` ⇒ **Kafka 认不出这个键 ⇒ 忽略** ⇒ 实际是全局的 `latest` |
| goka 用户计数 | `sarama.OffsetOldest`（`initGoKa.go:173`） | 同上 |
| goka 文章计数 | `sarama.OffsetOldest`（`initGoKa.go:207`） | 同上 |
| kq 诊断消费者 | 硬编码 `Offset: "latest"`（`initKafkaRaw.go:32`） | `properties = "auto-offset-reset=latest"` ⇒ **同样被忽略** ⇒ 但**碰巧对**（全局默认恰好也是 latest） |
| kq 用户 sink | 硬编码 `Offset: "latest"`（`initUserFilter.go:47`） | 同上（碰巧对） |
| kq 文章 sink | 硬编码 `Offset: "latest"`（`initArticleFilter.go:40`） | 同上（碰巧对） |

**根因（实测证据）**：

```
# RAG 侧 consumer 的 config dump（真 Kafka）：
auto.offset.reset = latest
AbstractConfig : These configurations '[auto-offset-reset]' were supplied but are not used yet.
```

- ⚠️ **Kafka 的合法配置名是点分的 `auto.offset.reset`**（`ConsumerConfig.AUTO_OFFSET_RESET_CONFIG`）。
- ⚠️ **`@KafkaListener(properties = ...)` 不走 Spring Boot 的松散绑定** ⇒ 字符串**原样**传给 Kafka
  ⇒ 连字符形式 `auto-offset-reset` 落进 `ConfigDef.parse` 的 **unused** 分支
  ⇒ **只打一条 info 日志，然后被彻底忽略**。
- ⇒ **同一个语义、两条完全不同的生效路径**：
  - 全局的 `spring.kafka.consumer.auto-offset-reset: latest` **生效**（Boot 松散绑定会转成点分形式）；
  - per-listener 的 `properties = "auto-offset-reset=earliest"` **不生效**。

**后果**：**「consumer group 首次消费、尚无已提交位点」**这一场景下
—— 也就是「服务重启后 group 位点被清空」「新环境首次接入」「运维重置位点」——
**RAG 从队尾开始（历史消息永远不参与计数），Go 从队头开始** ⇒ 行为分叉。

**为什么此前 326 条测试全绿也抓不到**：
⚠️ **所有既有对拍都是「先起消费者、后灌消息」** ⇒ 那时 `latest` 与 `earliest`
**没有可观察差别** ⇒ 绿测试是**假绿**。
⇒ 只有「**先灌消息、再起消费者**」才暴露它 —— 那正是
[`tools/difftest/run-task-offset-oldest.sh`](../../../tools/difftest/run-task-offset-oldest.sh) 做的事。

**修法**：六个 listener 的 `properties` 全部改成**点分**的 `auto.offset.reset`：
goka 三处 `=earliest`、kq 三处 `=latest`。
⚠️ kq 三处虽「碰巧对」，也必须改：否则将来把全局默认改成 earliest，这三处会**静默**变成 earliest。

**守门**：
1. [`TaskKafkaOffsetResetContractTest`](../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/TaskKafkaOffsetResetContractTest.java)
   （5 条）：**阳性对照**用 Kafka 自己的 `ConsumerConfig` 证明「连字符形式确实不被采纳」
   （加了连字符键 vs 完全不给这个键，效果**完全一样**）
   + 六个 listener 的精确断言 + **通用断言**：所有 per-listener property 的键名
   **都必须是 `ConsumerConfig.configNames()` 里认识的合法名** ⇒ 以后写错任何键名都会被立刻抓住。
   ✅ **变异测试已做**：把任一 listener 改回连字符 ⇒ **2 条变红**（报的正是「不是 Kafka 的合法配置名」）⇒ 非假绿。
2. **运行证据**：`run-task-offset-oldest.sh` 的 A2/A4 —— 修复前 FAIL（`<absent>`），修复后 PASS（`5`）⇒ 见 §四·八。

### 🔴 DRIFT-TASK-RPC-007 · Redis 命令超时**未配** ⇒ 落 Lettuce 默认 **60s** ⇒ 断连时命令挂 60s（Go 是 1.7s 快速失败）

**性质**：SEMANTIC MISMATCH（行为层 · **配置面看不出差异** ⇒ 典型「跑绿 ≠ 对」）

**发现时间**：2026-10-27（G2 lane · 复核 G4 的共享层落码知会时顺带查出的**同族口径差**）

**症状**：本组件的 `application.yml` 的 `spring.data.redis` 段**没有** `timeout` 键
⇒ Boot 4 不调 `commandTimeout(...)`（`LettuceConnectionConfiguration#applyProperties:173-175` 只在
`getTimeout() != null` 时才设）⇒ 落到 **`RedisURI` 的默认 timeout = 60s**
（`RedisURI.java:221` "Default timeout: 60 sec" · `:257` `private Duration timeout = DEFAULT_TIMEOUT_DURATION`）
⇒ **一次 Redis 断连会把本组件的整个 RPC 拖到超时**（本组件 `rpc-timeout: 2s`）。

**Go 侧真值（为什么是 3s，而不是 yaml 里写的 1000ms）**：

| 环节 | 事实 | 出处 |
|---|---|---|
| Go 的 `redis.Options` | **只设** `Addr` / `Password` / `DB` ⇒ 不设 `ReadTimeout` / `DialTimeout` | `service/task/rpc/internal/svc/service_context.go:25-30` |
| 真源 yaml 写的值 | `ReadTimeoutMs: 1000` / `DialTimeoutMs: 500` —— **不生效**（见下） | `service/task/rpc/etc/task.yaml:42-44` |
| struct 是否收这些字段 | **不收** —— `LikeRedis struct` 只有 `Addr` / `Pass` / `DB` / `ConsumerName` | `service/task/rpc/internal/config/config.go:32-37` |
| go-zero 的 conf 严格性 | **非严格模式** ⇒ yaml 里有、struct 里没有的字段被**静默忽略** | `core/conf/config.go`（无 `strict` / `ErrorUnused`） |
| 真正生效的 | go-redis **v9.20.0**（真源 `go.mod:15` 锁的就是它）的库默认：**`ReadTimeout = 3s`** · **`DialTimeout = 5s`** | `go-redis@v9.20.0/options.go:370` · `:338-340` |

⇒ **Go 侧真实口径 = 命令超时 3s ＋ 连接超时 5s**（**不是** yaml 里的 1000ms / 500ms）。

**双侧活体实测（L4 · 2026-10-27 · 真 Redis 容器 + 探针 · 同一操作 GET · 同一故障注入）**：

| 侧 / 口径 | 断连后行为 | 实测耗时 |
|---|---|---|
| **Go**（go-redis，与真源同款 `Options`） | 拒连快速失败（5 次 dial 尝试 + 100ms backoff） | **1694 ms** |
| **RAG · 未配 timeout**（修前） | Lettuce 默认 `DisconnectedBehavior.DEFAULT` ⇒ 命令**排队等重连** ⇒ `RedisCommandTimeoutException` | **60021 ms** |
| **RAG · 配 3s**（修后） | 快速失败 | **3 ms**（复跑 2 次：3ms / 2ms） |
| **RAG · 3s + `REJECT_COMMANDS`**（G4 共享层落码后） | `RedisException: Currently not connected. Commands are rejected.` | **1 ms** |

⇒ **60s ≫ 2s** ⇒ 修前一次 Redis 断连即可让本组件的 RPC **必然超时**（而不是像 Go 那样「快速失败 ⇒
域侧 lenient 包装 catch ⇒ 回源 DB ⇒ 请求按时返回」）。
⚠️ 域侧的 lenient 语义**本来就是对的**（`TaskDomainService.java:161-162` / `readProgressCache` 吞异常回源），
**问题只在于「异常来得太晚」**（60s 后才来）⇒ 这也解释了为什么此前的降级测试（Mockito）全绿。

**修法（域内自修 · 写权在 G2）**：
1. `application.yml` 的 `spring.data.redis` 段补 `timeout: 3s`（对齐 Go 的 go-redis 默认 ReadTimeout）
   ＋ `connect-timeout: 5s`（对齐 Go 的默认 DialTimeout；不配会落 Lettuce 默认 **10s**，
   `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10` 秒 ⇒ 与 Go 差 2 倍）。
   ⚠️ 这两行**不是「照抄 yaml」** ⇒ 依据是**库默认**（见上表），yaml 里那两个值根本不生效。
2. 同族一致：like-rpc 原本已有 `timeout: 3s`（✓ 对齐），但**缺** `connect-timeout` ⇒ 一并补 5s。

**守门**：
1. [`TaskRedisCommandTimeoutGateTest`](../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskRedisCommandTimeoutGateTest.java)（4 条）
   ＋ [`LikeRpcRedisTimeoutsGateTest`](../../../service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcRedisTimeoutsGateTest.java)（4 条）
   —— **起真 Spring 上下文**（用真应用类 ⇒ 加载真实 yml）、读**真的** `LettuceConnectionFactory` / `ClientOptions`，
   断言**消费点**：
   - 命令超时 ⇒ `clientConfiguration.getCommandTimeout()`（⇒ `RedisURI.timeout` ⇒ `CommandExpiryWriter.setTimeout`）
   - 连接超时 ⇒ `ClientOptions.getSocketOptions().getConnectTimeout()`（⇒ Boot `LettuceConnectionConfiguration:212-215`）
   - 机制启用 ⇒ `ClientOptions.getTimeoutOptions().isTimeoutCommands()`（Boot `:229` 打开的，不是 Lettuce 默认）
   - **阳性对照** ⇒ `RedisURI.create(...).getTimeout() == 60s`（证明断言在区分「配了」与「没配」）
   ⚠️ **判据必须查消费点、不能查声明点**：`ClientOptions.getTimeoutOptions()` 的 source **恒为**
   `DefaultTimeoutSource`（Boot 只写 `TimeoutOptions.enabled()` ⇒ 启用机制但不给固定时长）
   ⇒ 断言 source 类型是**错的判据**（本轮实测踩过：守门红了但根因是判据写错，不是配置错 ⇒ 已订正）。
2. ✅ **变异测试已做（3 条，全部红在目标断言）**：

   | 变异 | 结果 |
   |---|---|
   | M1：task-rpc 删 `timeout: 3s` | 1 条红 —— `expected: 3S but was: 1M`（`assertThat` 报 1 分钟） |
   | M2：task-rpc 删 `connect-timeout: 5s` | 1 条红 —— `expected: 5S but was: 10S` |
   | M3：like-rpc 删 `connect-timeout: 5s` | 1 条红 —— `expected: 5S but was: 10S` |

**⚠️ 与 G4 共享层落码的关系（订正 G4 知会里的「零域侧改动」）**：

- G4 的 `GoStyleRedisClientAutoConfiguration`（`REJECT_COMMANDS`）**在我域会真命中**，不是「零影响」：
  `mvn dependency:list` 实测 like-rpc / task-rpc **自带** `spring-boot-starter-data-redis`
  ⇒ `lettuce-core:6.8.2` + `spring-boot-data-redis:4.0.8` 都在 classpath 上
  ⇒ `@ConditionalOnClass({LettuceConnectionFactory, ClientOptions, LettuceClientOptionsBuilderCustomizer})` **命中**
  ⇒ 且我域**无自建 `LettuceConnectionFactory`**（`grep` 实测 0 命中）⇒ 走 Boot 自动装配 ⇒ customizer 生效。
  ⇒ **该落码对我域是净收益**（断连 1ms 拒单，替掉 60s 挂起）⇒ 不需要域侧任何动作。
- 但 G4 引的 Go 出处 `core/stores/redis/redisclientmanager.go` **在真源仓里不存在**
  （真源仓无 `core/` 目录）⇒ 那是 **go-zero 依赖源码**的路径
  （`go-zero@v1.10.2/core/stores/redis/redisclientmanager.go`，`:14` `maxRetries = 3` · `:32-40` 构造 `red.NewClient`）
  ⇒ 口径本身成立（`MaxRetries=3` 且不设 Dial/ReadTimeout ⇒ 快速失败），但**引用路径要标注是依赖源码**。

**⚠️ 效力边界（本条的证据强度）**：

- ✅ **L4 双侧活体**：Go 腿与 RAG 腿**同时跑**、同一 Redis 容器、同一故障注入、同一操作（GET）。
- ⚠️ **不是**「生产组件级」活体：探针跑的是**独立进程 + 生产 classpath**，不是 task-rpc 本体
  ⇒ 「本组件 2s 预算下会不会真的被拖到 60s」这一步是**推理**（60s ≫ 2s），不是端到端实测。
- ⚠️ **故障形态**：`docker stop` = 优雅退出（FIN）⇒ 客户端进入 DISCONNECTED（正在重连）⇒ 命令**被排队**
  ⇒ 这正是 60s 挂起的成因。⚠️ 若用 `docker kill`（RST）⇒ Lettuce 立刻失败 ⇒ **分不出** 60s 与 3s
  ⇒ 复现**必须**用优雅断开。

---

## 二、签字偏离与有意偏离

| 编号 | 内容 | 落点 |
|---|---|---|
| `max-lifetime: 1800000` | Go `service_context.go:45` 写 `SetConnMaxLifetime(time.Duration(c.ConnMaxLifetimeMinutes))` —— **漏乘 `* time.Minute`**，生效值是 30 **纳秒**。RAG 落 30 分钟 | 已在本轮登记为 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md) 的 `SD-2026-10-23-TASK-01` |
| 奖励流**只写不读** | Go `reward.Worker` 与 `Init.StartInitReward` **整个文件是注释**、`task.go:35` 调用也被注释 ⇒ 线上 `stream:task:reward` 只堆积无消费者。RAG **刻意不补消费者**（会引入对 points 域的依赖，而 points 不在范围内） | 与 Go 一致，非偏离；但记录在此以免后人「顺手补上」 |
| 奖励事件字段名两端对不上 | Go producer 写 `"score"`（`reward/producer.go:27`）、commented-out 的 consumer 读 `"add_score"`（`reward/worker.go:120`）。RAG **照抄 producer 的 `score`** | 与 Go producer 一致；consumer 在 Go 侧本就不存在 |

---

## 三、逐条对照结论（已核为一致的部分）

> 本节是「查完发现没毛病」的记录，**同样需要证据**，避免下一轮重复查。
> 每条的「✅」含义是：**在本节标注的证据强度下，两侧行为一致**，不是「已闭环」。

### `GetTask`（`get_task_logic.go` ↔ `TaskDomainService`）

| 行为点 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| `uid <= 0` → `InvalidArgument("user_id invalid")` | `get_task_logic.go:43-45` | `TaskDomainService.getTask:90-94` | ✅ 文案逐字一致 |
| 缓存 key `task:progress:<uid>` | `get_task_logic.go:106-108` | `TaskRedisKeys.userProgressCache` | ✅ |
| Redis 报错**也算未命中**（回源） | `get_task_logic.go:51` `rerr != nil \|\| len(pm)==0` | `TaskDomainService.readProgressCache:161-167` catch → 返回 null | ✅ |
| 回填失败**不影响返回** | `get_task_logic.go:76,78` 错误全丢弃 | `fillProgressCache` / `expireQuietly` 均吞异常 | ✅ |
| DB 无行时对**不存在的 key** 设 30s TTL = no-op | `get_task_logic.go:78` | `EMPTY_PROGRESS_CACHE_TTL` + 注释显式标注「不要改写成空标记防穿透」 | ✅ **保留 Go 的无效行为** |
| 回源 SELECT 列集 | `get_task_logic.go:55` `user_id, task_id, status, progress, target` | `TaskProgressMapper.selectByUserId` 逐列一致 | ✅ 无 status 过滤 |
| 打包 `status\|progress\|target` | `get_task_logic.go:115-118` | `packProgress` | ✅ |
| 解析保留**尾部空串** | `strings.Split` 语义 | `split("\\|", -1)` + 显式注释说明为何不能用默认 split | ✅ |
| 段数 ≠ 3 ⇒ 0 | `get_task_logic.go:122-124` | `unpackProgressValue` | ✅ |
| `required_progress` 取自**任务定义**而非 DB `target` | `get_task_logic.go:86-87` 注释 + `:87` | `TaskDefs` + `TaskDomainService:126` | ✅ |
| 两条任务定义（190/1902, 3/5, 中文名与 desc） | `model/taskModel.go:22-25` | `TaskDefs.ALL_TASK_DEFS` | ✅ 逐字一致 |

### gRPC 拦截器注册链（`task.go:28,44` ↔ `TaskRpcApplication` + `RpcServerInterceptorFactory`）

> 🆕 **2026-10-24 20:1x 三次复核补核**：这条契约**此前从未进入本台账**（台账里连
> `GrpcServerConfigurer` / `RpcServerInterceptorFactory` 都没出现过），
> 而 **Go 侧是显式注册的**（行为层，不是表达层）⇒ 属于**必须对照却漏掉**的一类。

| 行为点 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| 关掉 go-zero 自带的 zrpc 服务端超时，**取出**生效 timeout | `service/task/rpc/task.go:28` `rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)` | `TaskRpcApplication` `@Import(ObservabilityProperties.class)` ＋ `application.yml` 的 `observability.rpc-timeout: 2s` | ✅ 口径一致（见下一行） |
| timeout 的**生效值** = **2000ms**（`task.yaml` **未配** Timeout ⇒ 走 go-zero 默认） | `task.yaml` 无 `Timeout` 键 ⇒ go-zero `zrpc/config.go` 的 `Timeout int64 `json:",default=2000"`` | `application.yml:231` `rpc-timeout: 2s` | ✅ **2s = 2s**（`TaskGrpcInterceptorWiringTest` 第 2 条锁死） |
| **显式**把拦截器挂到 server 上（**不是**自动注册） | `service/task/rpc/task.go:44` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))` | `TaskRpcApplication.rpcInterceptorConfigurer`：`server -> server.intercept(rpcUnaryServerInterceptor)` | ✅ **两件套齐**（`@Import(RpcServerInterceptorFactory.class)` ＋ `@Bean GrpcServerConfigurer`） |
| 拦截器带 **timeout ＋ slow threshold** 两个参数 | `observability/rpc.go:25-28` `NewUnaryServerInterceptor(timeout, slowThreshold)`，`slowThreshold <= 0` ⇒ 回退 `DefaultSlowThreshold` | `RpcInterceptor`（`common` 域）：`markTimeout` ＋ `markDurationAndSlow` | ✅ 两个参数都有对应物 |
| 超时把 in-flight 调用打断（**服务端**提前返回 `DEADLINE_EXCEEDED`） | `observability/rpc.go:38-55` `context.WithTimeout` ＋ 独立 goroutine 跑 handler | `RpcInterceptor` 用 `ScheduledExecutorService` 到时 `close(DEADLINE_EXCEEDED)` | ✅ **运行期实测**：≈300ms 返回，不是等满客户端 5s deadline |

⚠️ **这条为什么必须单独守**：devh 的 `grpc-server-spring-boot-starter` 3.1.0 **从不自动注册**
`ServerInterceptor` Bean（common 域已用 `javap` 对 jar 证伪，H2）⇒ **只产出 Bean 而没有
`GrpcServerConfigurer` ⇒ 链路上什么都不会发生，且不报错** ⇒
**「`ctx.getBean(ServerInterceptor.class)` 断言照样是绿的」**（AGENTS.md §3.3 的经典形态）。
⇒ `TaskGrpcInterceptorWiringTest` 起**真 gRPC server** 来测，且已做 ✅ **变异测试**：
摘掉 `server.intercept(...)` ⇒ 用例**红**（耗时从 ≈300ms 退到 **5011ms** = 等满客户端 deadline）
⇒ **证明它不是假绿**。

⚠️ **跨域提醒**：**follow / message / users-user 三域已有同类守门测试，本域此前没有**
⇒ 「别的域有测试」**不等于**「本域有」⇒ 跨域共有的契约，**各域要各自守**（见 `PITFALL-TASK-RPC-33`）。

### 点赞 sink（`like_sink.go` ↔ `UserLikeProgressSink`）

| 行为点 | Go 出处 | 结论 |
|---|---|---|
| key 解析 `ParseInt(key,10,**63**)` | `like_sink.go:83` | ✅ RAG 显式补 `[-2^62, 2^62-1]` 范围检查并在注释里说明「Go 的 bitSize 63 比 int64 窄一半」 |
| `userID <= 0` ⇒ 正常返回（提交位点） | `like_sink.go:87-89` | ✅ |
| val 解析失败**保留默认 1**（不是丢弃） | `like_sink.go:91-96` | ✅ 注释点名「这是本 sink 最容易被写错的一处」 |
| `delta[uid] = max` | `like_sink.go:102` | ✅ `delta.merge(..., Math::max)` |
| flush 分块阈值 **`n > redisPipeMax`**（注意是 `>` 不是 `>=`） | `like_sink.go:286` | ✅ RAG 保留 `>` |
| `exec()` 遍历**整批**而非当前分块 | `like_sink.go:270` `for uid, total := range batch` | ✅ RAG 保留该怪癖（`advanceProgress(batch)`） |
| `like:total:<uid>` 普通 SET + 90d | `like_sink.go:284` | ✅ |
| init key `task:init:<uid>:<taskId>`（**init 单数**、uid 在前）+ 90d | `like_sink.go:186,188` | ✅ |
| done key `task:done:<uid>:<taskId>`（**uid 在前**）+ 90d | `like_sink.go:338` | ✅ |
| 进度 key `task:progress:<uid>:<taskId>` | `like_sink.go:214` | ✅ |
| `!ok`（SETNX 已存在）⇒ 静默返回，后续三步全跳过 | `like_sink.go:340-342` | ✅ |
| **Enqueue 失败 ⇒ `markTaskDoneInDB` 与缓存失效都不执行** | `like_sink.go:356-362` | ✅ RAG 让异常上抛、由 `advanceProgress` 捕获 |
| `user_like_count.user_id` 是 **TEXT**（`task_progress.user_id` 是 BIGINT） | `like_sink.go:18` vs `taskModel.go:29` | ✅ DDL 显式保留该不一致并注明「不要顺手统一」 |
| GREATEST 单调 upsert | `like_sink.go:415-422` | ✅ |
| 缓存失效 DEL `task:progress:<uid>` | `like_sink.go:332-334` | ✅ |

### 文章 sink（`article_sink.go` ↔ `ArticleLikeProgressSink`）

| 行为点 | Go 出处 | 结论 |
|---|---|---|
| `key == ""` ⇒ 正常返回 | `article_sink.go:95-97` | ✅ |
| `articleID == 0` ⇒ **返回 nil（提交位点、安静丢弃）**，且发生在 `parseVal` **之前** | `article_sink.go:99-101` | ✅ 注释点名「漏掉这步会让 key=0 的消息挂到 `delta[0]` 上」 |
| `parseVal`：`cur == 0 ⇒ cur = 1` | `article_sink.go:132-134` | ✅ |
| 类型错误 ⇒ **整条 Unmarshal 失败**（不是取零值） | `article_sink.go:131` | ✅ RAG 先判类型再取值 |
| delta 取 max，**相等时保留旧 user_id** | `article_sink.go:116-120` | ✅ |
| init key `task:inits:<taskId>:<uid>:<articleId>`（**inits 复数、taskId 在前、带 articleId**）+ 24h | `article_sink.go:255-258` | ✅ |
| done key `task:done:<taskId>:<uid>`（**与点赞路段序相反**）+ 120d | `article_sink.go:427-428` | ✅ |
| 进度 key `task:progress:<taskId>:<uid>:<articleId>` | `article_sink.go:436-439` | ✅ |
| **奖励分值传 5**（不是该任务的 target=3） | `article_sink.go:447` | ✅ 注释显式标注「刻意不联动」 |
| HSet 错误**被丢弃**（点赞路是检查后 return） | `article_sink.go:440` `_, _ =` | ✅ RAG try/catch 只记日志 |
| `FlushPostgres` **整段被注释** ⇒ 文章路不写 `user_like_count` | `article_sink.go:176-178` | ✅ RAG `flushOnce` 只有 lazyInit + flushRedis |
| flush 阈值 `n >= redisPipeMax`（与点赞路 `>` 相反） | `article_sink.go:376` | ✅ |
| `FlushRedis` 过滤 `UserID == 0 \|\| total <= 0`（点赞路**没有**这个过滤） | `article_sink.go:365-367` | ✅ 两侧的不对称都保留 |

### Kafka 拓扑（`Init/*.go` ↔ `mq/*`）

| 组 | Go | RAG | offset |
|---|---|---|---|
| 诊断消费者（什么都不做） | `initKafkaRaw.go` group `consumer31`, topic `task.logs.raw` | `TaskRawDiagnosticConsumer` | `latest` ✅ |
| goka 主路由 | `initGoKa.go:123-128` group `consumer41` | `TaskRawLogRouter` | **`earliest`** ✅ |
| goka 用户计数 | `:164-169` group `consumer61` | `TaskUserCountConsumer` | **`earliest`** ✅ |
| goka 文章计数 | `:196-203` group `consumer71` | `TaskArticleCountConsumer` | **`earliest`** ✅ |
| 点赞 sink | `initUserFilter.go` group `consumer81` | `UserLikeProgressSink` | `latest` ✅ |
| 文章 sink | `initArticleFilter.go` group = **`GroupKafkaFilter`** = `consumer101`（**不是** `GroupFilterArticle`） | `ArticleLikeProgressSink` | `latest` ✅ |

- ✅ goka 三处 `OffsetOldest` 与 kq 三处 `"latest"` **方向相反**，RAG 逐 listener 用 `properties` 覆盖，与 Go 一致。
- ✅ `GroupFilterArticle`（`consumer91`）在 Go 侧**无任何读取点**，RAG 只保留配置面、不建 listener。
- ✅ yaml 的 `Offset/Consumers/Processors` 三个字段在 Go 侧**从未被读取**（`Init/*.go` 全部硬编码 `Consumers:1, Processors:1`），
  RAG 刻意不映射，并在注释里写明「映射了会让人误以为并发度是 8」。
- ✅ goka 三个 processor 用 `codec.Int64` 编**十进制 ASCII**（`goka/codec/codec.go:56`）⇒ value 用 `StringDeserializer` 而非 ByteArray，有注释佐证。
- ✅ `kq` 无重试循环 ⇒ RAG 的 `DefaultErrorHandler` 显式 `FixedBackOff(1000, 1)`（maxAttempts=1）+ `ackAfterHandle(false)` + `seekAfterError(false)`。
- 🔴 **`goka` 的 `OffsetOldest` 只作用于「首次消费无位点」的 group** —— 本条**已做活体验证**（2026-10-24，见 §四·八），
  并且**验证过程中查出 RAG 侧一条真漂移**（`auto-offset-reset` 写成了连字符 ⇒ Kafka 直接忽略 ⇒ 实际是 `latest`），
  ⇒ 已修（[DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)）。

### DDL（`AutoMigrate` ↔ Flyway）

- ✅ `task_progress`：`user_id/task_id BIGINT NOT NULL` 复合主键；`status/progress/target` 可空；
  `done_at/created_at/updated_at` **TIMESTAMPTZ**（GORM 在 PG 上把 `time.Time` 映射成 `timestamptz`，不是 `timestamp`）。
- ✅ `user_like_count`：`user_id TEXT NOT NULL` 主键（**与 task_progress 的 BIGINT 不同**，Go 侧本就不一致）。
- ✅ **不加 CHECK、不加额外索引** —— GORM `AutoMigrate` 不会为无 tag 的列生成 CHECK，加了就变成 RAG 侧独有的约束。
- ✅ `TaskDef` 表**不存在**（Go 侧它是代码常量 `model/taskModel.go:22-25`，没进 `AutoMigrate`）。
- ✅ 以上均由真 PG 容器用例 `columnTypesMatchGo` / `compositePrimaryKey` / `flywayCreatedTables` 实测。

---

## 四、真启动证据（§3.3「先让它真的启动一次」）

> 来源：`TaskProgressUpsertParityTest` 的 surefire `system-out`（真 PostgreSQL 16 容器 + 完整 Spring 上下文）。
> **不是**「编译通过」，**不是**「纯 mock 单测全绿」。

| 证据 | 实测输出 |
|---|---|
| 独立 Flyway 账本真建出来 | `Creating Schema History table "public"."flyway_schema_history_task"` |
| **V1 真跑了**（`baseline-version: 0` 修复有效） | `Migrating schema "public" to version "1 - create task progress"` |
| V2 真跑了 | `Migrating schema "public" to version "2 - create user like count"` |
| gRPC server 真监听 | `gRPC Server started, listening on address: 0.0.0.0, port: 34430` |
| 两张表真在位 | `TaskSchemaGate: all 2 task tables present` |
| Kafka listener 真装配（6 个 group 全部在起） | `consumer41/61/71/81/101/31` 的 client 日志 |

> ✅ 这同时为 AGENTS.md §6.3 问题 4 补上 task-rpc 的**运行时**证据：
> `spring.flyway.table: flyway_schema_history_task` 确实生效，与 message-rpc / like-rpc 的账本两两不同。
> ⚠️ **「三模块同库共存」仍未闭环**（本条只证明单模块启动自洽）。

---

## 五、证据边界（必须读，否则会高估本台账）

> 本节是「哪些结论有多硬」的诚实标注。**任何一条 ✅ 都要连同本节一起读**。

1. ✅ **HTTP wire 层双侧活体对拍**（2026-10-23 22:5x）：Go `task-rpc:19005` + `task-api:18886`，
   RAG `task-rpc:59005` + `task-api:58886`，共用 PG 容器 / Redis / Kafka，43 条请求矩阵。
   覆盖：**Redis HGETALL → PG 回源 → packProgress/unpackProgress → 任务定义合并 → gRPC 序列化 → HTTP 响应**。
2. ✅ **gRPC 单组件对拍**（2026-10-23 23:0x）：同 stub 连两个端口，比 **code + description + 载荷**，
   7 条用例全一致 —— 含 **3 组 HTTP 面永远走不到的 `INVALID_ARGUMENT`**（`description` 逐字节为
   `user_id invalid`）。⇒ HTTP 面被抹掉的 gRPC 层语义已单独验过。
   ✅✅ **跨语言方向也补齐了**（2026-10-24，见 §四·五·一）：新增 **Go 客户端 → 两端服务端**
   （真源自己的生成桩、真源零写入），7/7 一致 ⇒ grpc-go ↔ grpc-java 的**双向**互操作都验过
   （原先只有「Java 客户端 → Go 服务端」这一个跨语言方向）。
3. ✅✅ **Kafka 消息链路端到端对拍**（2026-10-23 23:4x）：真 Kafka + 真 Redis + 真 PG，
   同一批 7 条 raw 日志灌两侧 in-topic，比对 **Redis 全 key + 奖励流 + PG 两表 + goka 计数值**
   ⇒ **完全一致**（§四·三）。
   ✅ **畸形消息也已做活体对拍**（2026-10-24，见 §四·五·二）：`LogEvent` 解析失败 / `uid <= 0` /
   `total <= 0` / key 解析失败 / 毒丸之后再来一条合法消息 —— **五个用例两侧行为完全一致**。
   ⇒ 此前「只有源码级对照」的那条缺口**已关闭**。
   ⚠️ 但覆盖面**仍只到「读路径 + 消息消费路径」**：未覆盖**「Kafka 写入 → task-api 读到」这条闭环**
   （见本节第 7 条）—— 那条受限于 Go 侧读写 Redis key 本来就不一致，**不是 RAG 的缺陷**。
4. ✅ **DDL 逐列活体对拍**（§四·二）：GORM `AutoMigrate` 产物 vs Flyway 产物，
   类型 / 可空性 / 复合主键**逐项一致**；仅**主键约束名**不同（`ON CONFLICT` 走列，行为无差异）。
   ⚠️ 索引 / 唯一约束 / CHECK：两侧**一致地没有**（不是漏检）。
5. ⚠️ **Redis 侧现已是「真 Redis 活体」**（DRIFT-TASK-RPC-001 的 `INCR` 路径已由
   §四·三 的端到端对拍 + §四·四 的真 Redis 守门测试双重覆盖）。
   ⚠️ 但 `RedisPipelines` 的**事务/非事务语义**只验到「行为等价于 Go 的非事务 pipeline」，
   **未**验「与 Go 的 `TxPipeline` 等价」—— 因为 Go 侧**根本没用** `TxPipeline`（这是结论，不是缺口）。
6. ⚠️ `service/common`（被 task-rpc 依赖）的结论来自它自己的台账，本轮**未重审**。
7. ⚠️ **Go 侧未跑 `task-api`**：Kafka 对拍只到 `task-rpc`。`task-api` 侧的
   `GetTask` 读路径已由 HTTP 对拍覆盖，但**「Kafka 写入 → task-api 读到」这条闭环未做活体对拍**
   （两侧的 key 形状不一致这件事见 `TaskRedisKeys` 类注释，是 Go 侧既有的读写 key 不一致，**不改**）。
8. ✅ **「首次消费无位点」这一分支已单独活体验证**（2026-10-24，见 §四·八）：
   全新 group + **预灌消息在消费者启动前** ⇒ 两侧的 goka 路都读到队头（计数 = 5、group 位点 = 7），
   两侧的 kq 路都跳过预灌消息（阴性对照）⇒ 且**验证过程中查出并修掉了一条真漂移**
   （[DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)）。
   ⚠️ **这条证据只覆盖「无位点」这一条分支**；「有位点」时两侧都是「从已提交位点继续」（Spring 的
   `AckMode.RECORD` ↔ Go 的 `CommitMessages`），已由 Kafka 端到端对拍（§四·三）与畸形消息对拍（§四·五·二）覆盖。
9. ✅ **goka 表的「保留策略」已用实测定案**（2026-10-24，见 §四·七）：
   `cleanup.policy=compact` 单独使用时 **Kafka 不按时间删**（带阳性对照的对照实验）
   ⇒ Go 的计数**永不丢失** ⇒ RAG 的 `task:goka:count:*` **不设 TTL 就是等价实现**。

---

## 六、本轮引入的新风险（明写，不藏）

| 风险 | 说明 | 处置建议 |
|---|---|---|
| Redis 变成计数的硬依赖 | 此前计数是纯内存，Redis 挂了只影响进度缓存（会降级回源）；**现在 Redis 挂了会阻塞 goka 计数组**（位点不提交、消息堆积） | 已在类注释写明取舍。若运维不接受，把 `TaskCountStore` 换成本地文件 store（更贴 goka 的 LevelDB）即可，其余代码不用动 |
| Redis key 无 TTL、无上限 | 🔴 **原表述的前提是错的**（2026-10-24 实测推翻，见 §四·七·二）：<br>Go 侧的计数表**不随 Kafka retention 滚动** —— goka 的表 topic 只设了 `cleanup.policy=compact`、**没有 `retention.ms`**，且 `compact` 单独使用时 Kafka **不按时间删**（实测）。<br>⇒ **这不是与 Go 的漂移，而是与 Go 一致**：Go 的权威存储是本地 LevelDB（无 TTL、task 域从不调 `Delete`）⇒ **计数永不丢失**。<br>⚠️ 但「Redis 内存无上限」本身仍是真的运维事实（RAG 侧是内存，Go 侧是磁盘） | **不加 TTL** —— 加了会造成**业务错误**（计数到期归零 ⇒ `like:total` 是普通 SET 不是 GREATEST ⇒ 被更小的值覆盖），并制造一条与 Go 的新漂移（§四·七·二）。<br>⇒ 改为「**用真 Redis 守门测试把「不设 TTL」锁成契约**」<br>（`TaskCountStoreRetentionContractTest`，4 条，含阳性对照）。<br>⚠️ 若将来确实要内存有界，**必须先改 Go 侧**或同时改 `like:total` 的写法 ⇒ 那已是行为变更，不在本域单方面做 |
| 新增了一条 gRPC→Redis 的同步依赖 | 每条 raw 日志一次 `INCR` RTT | 与 Go 的「每条消息一次本地 DB 写 + 定期 Kafka 表 emit」量级相当，判断为可接受 |

---

## 七、待办

1. ~~**Kafka 链路的畸形消息活体对拍**~~ ⇒ **2026-10-24 已做**（见 §四·五·二）：
   五个用例（`LogEvent` 解析失败 / `uid <= 0` / `total <= 0` / key 解析失败 / 毒丸后再来一条合法消息）
   **两侧行为完全一致**，`EXIT=0`。
   ⚠️ 顺带**推翻了本仓 javadoc 里一条错误断言**（「Go 会被 kafka-go 的自动提交抵消」）——
   kafka-go 用 `FetchMessage` 时自动提交不生效（`reader.go:811-812`），活体实测也证实
   ⇒ 已改写为源码 + 活体双出处的说法。
2. **「Kafka 写入 → task-api 读到」的闭环对拍**：本轮 Kafka 对拍只到 `task-rpc`；
   `task-api` 的读路径（`task:progress:<uid>`，与 sink 写的 `task:progress:<uid>:<taskId>` **不是同一个 key**）
   只由 HTTP 对拍间接覆盖。这是 Go 侧既有的读写 key 不一致，**不要「顺手统一」**。
3. ~~`task:goka:count:*` 的保留策略（见 §六）—— **无 TTL、只增不删**~~
   ⇒ **2026-10-24 关闭**（见 §四·七）：**结论是「不加 TTL」**。
   ⚠️ 原待办的**前提是错的**（「goka 的表会随 Kafka retention 滚动」）：
   goka 的表 topic 只设 `cleanup.policy=compact`、**没有 `retention.ms`**，
   而 compact 单独使用时 **Kafka 不按时间删**（带阳性对照的对照实验，§四·七·二）⇒
   **Go 的计数表 key 永不按时间过期**（权威存储是本地 LevelDB，默认无 TTL 且从不调 `Delete`）
   ⇒ **RAG 不设 TTL 就是等价实现**，加了反而会造成业务错误（计数归零 ⇒ `like:total` 被更小的值覆盖）。
   ⇒ 改为「**用真 Redis 守门测试把「不设 TTL」锁成契约**」
   （`TaskCountStoreRetentionContractTest`，4 条，含阳性对照 + ✅ 变异测试已做）。
4. ~~`tools/difftest/check-ddl-static.py` 扩到 task 域~~ ⇒ **2026-10-24 已做**：
   脚本改成 `--domain users|task` 参数化；task 域实测
   **11 列逐字一致、类型/长度 0 差异、NOT NULL/DEFAULT 0 差异**，`EXIT=0`。
   ⚠️ 顺带**补上了此前标为 NOT CHECKED 的 NOT NULL / DEFAULT 对照**
   （users 域的台账数字未受影响，那一节是新增的）。
   ⚠️ 另需排除 **Flyway 迁移账本表**（`flyway_schema_history_task`）——
   它是 RAG 侧独有的工具产物，不是业务表，不排除会被判成「列集合不一致」。
5. ~~`tools/difftest/check-proto-static.sh` 扩到 `task`~~ ⇒ **2026-10-24 已做**：
   task 域实测 `EXIT=0`（`task.proto ↔ task.proto` 逐字节一致）。
   ⚠️ task 两侧的 `package task;` **逐字相同** ⇒ **不需要**像 `like` 域那样额外排除 package 行。
   ⚠️ 顺带把该脚本的**退出码**从「恒 0」改成「有差异返回 1」⇒ 现在能当门禁用。
6. **`tools/difftest/parity-task-kafka.py` 纳入 CI**：目前它依赖手工起的 `taskprobe-*` 基建，
   没法在普通 `mvn test` 里跑。可考虑用 Testcontainers 起 Kafka（`apache/kafka` KRaft 镜像）自动化。
   ⚠️ **2026-10-24 已补上另一半**：HTTP 侧现在有一键编排
   [`tools/difftest/run-task-http-parity.sh`](../../../tools/difftest/run-task-http-parity.sh)
   （原先 HTTP 侧只有手工步骤，Kafka 侧才有一键脚本）⇒ 两边的「一条命令复跑」齐了，
   但**都还依赖手工起的基建**，进 CI 仍需 Testcontainers。
7. ⚠️ **`run-task-offset-oldest.sh` 与 `run-task-kafka-e2e.sh` 的关系**：
   前者是**专门为「无位点分支」设计**的（顺序与 E2E **相反**：先灌消息、再起消费者），
   后者验的是「正常消费路径」（先起消费者、再灌消息）。
   ⇒ **两者都要保留**，不要用前者替代后者
   （前者**不**验畸形消息、**不**验 DDL、**不**验 PG 表）。
   ⇒ ⚠️ 前者还会**重建 `taskprobe-*` 容器** ⇒ 会丢弃该容器上的既有数据。
8. ⚠️ **本分支改了 4 个多域共享工具**（合并时留意）：
   `check-proto-static.sh`（退出码语义）、`check-ddl-static.py`（参数化）、
   `check-plaintext400-headers.sh`（加 task 段）、`run.sh`（2 处 bug 修复），
   外加**新增** `docker-compose.task-kafka.yml` 的 `name: taskprobe`（修跨域误删）。
   ⇒ 清单见 [`./_README.md`](./_README.md) 的「共享工具改动清单」。
9. ⚠️ **yaml 的三类「死配置」跨域统一修**（§四·九）：
   - `spring.mybatis-plus.*` → 应为顶层 `mybatis-plus.*`（**8 个 rpc 模块同款**，零行为影响）；
   - `otel.*` → **Boot 4.0.8 完全没有 OTel 自动配置**（`OtelProperties` / `OtelAutoConfiguration`
     命中数 0；Boot 3.2.5 尚有 `OpenTelemetryAutoConfiguration`）⇒ **20 个 yaml 同款**，
     ⚠️ **这一条与 Go 存在观测差异**（Go `serviceconf.go:67 trace.StartAgent` 真的导出 span，
     Java 侧 `GlobalOpenTelemetry.get()` 拿到的是 no-op）⇒ **需要全仓统一接线**，不属 task 域；
   - `spring.jpa.*` → 本模块无 JPA 依赖（**6 个 rpc 模块同款**，零行为影响）。
   ⇒ **本域已把扫描固化成守门测试**（`TaskConfigKeyBindingContractTest`，5 条 + ✅ 三种变异测试），
   ⇒ 新增死配置会被立刻抓住；**已登记的孤儿键是显式台账**（带出处与定性）。

---

## 八、相关文件

| 关系 | 文件 |
|---|---|
| 本域 api 台账 | [`api.md`](./api.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑（Ryuk 静默 skip / 死配置） | [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md) |
| 四域排序与本域定位 | [`../_cross/component-coverage-matrix.md`](../_cross/component-coverage-matrix.md) |

---

## 四·二、DDL 活体对拍：GORM `AutoMigrate` 产物 vs Flyway 产物（2026-10-23 实测）

**这一节直接关闭了上一版台账 §五 的第 3 条证据缺口。**

做法：两侧各连同一个 PG 容器的**不同库**（`task_go_db` 走 Go 的 `AutoMigrate`，
`task_rag_db` 走 RAG 的 Flyway），然后 `\d` 逐列读真实建表结果再对拍。

| 表 | 列 | Go `AutoMigrate` | RAG `Flyway` | 结论 |
|---|---|---|---|---|
| `task_progress` | `user_id` | `bigint not null` | `bigint not null` | ✅ |
| | `task_id` | `bigint not null` | `bigint not null` | ✅ |
| | `status` | `text`（可空） | `text`（可空） | ✅ |
| | `progress` | `bigint` | `bigint` | ✅ |
| | `target` | `bigint` | `bigint` | ✅ |
| | `done_at` | **`timestamp with time zone`** | **`timestamp with time zone`** | ✅ |
| | `created_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | `updated_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | 主键 | `PRIMARY KEY (user_id, task_id)` | `PRIMARY KEY (user_id, task_id)` | ✅ |
| | 约束名 | `task_progress_pkey`（GORM 默认） | `pk_task_progress`（显式命名） | ⚠️ 仅**名字**不同 |

| 表 | 列 | Go | RAG | 结论 |
|---|---|---|---|---|
| `user_like_count` | `user_id` | `text not null` | `text not null` | ✅ |
| | `like_count` | `bigint` | `bigint` | ✅ |
| | `updated_at` | `timestamp with time zone` | `timestamp with time zone` | ✅ |
| | 主键 | `PRIMARY KEY (user_id)` | `PRIMARY KEY (user_id)` | ✅ |

**结论**：✅ **列类型 / 可空性 / 主键构成逐项一致**。
⚠️ 主键**约束名**不同，但 `ON CONFLICT (user_id, task_id)` 走的是**列**而不是约束名 ⇒ 行为无差异。
⇒ 这**印证**了台账里「`time.Time ⇒ TIMESTAMPTZ`」「`string ⇒ TEXT`」「无 tag ⇒ 可空」的判断。

⚠️ 索引 / 唯一约束 / CHECK：两侧**一致地没有**（Go 的 `AutoMigrate` 不为无 tag 列生成 CHECK，
RAG 的迁移脚本刻意不加）⇒ 这一项**已闭合**，**不是「漏检」**。

---

## 四·三、Kafka 端到端对拍：两侧灌同一批 raw 日志，比最终态（2026-10-23 23:4x 实测）

**这一节关闭了上一版台账 §五 的最后一条证据缺口**（上一版原文：「Kafka 消息链路只验到装配，未做端到端消息对拍」）。

### 做法

真 Kafka（KRaft 容器）+ 真 Redis + 真 PG，两侧**完全隔离**地跑同一套栈：

| | Go 真源侧 | RAG 侧 |
|---|---|---|
| Kafka topic | `task.logs.raw*` / `task.logs.filtered.*` | `rag.task.logs.raw*` / `rag.task.logs.filtered.*` |
| consumer group | `task.rpc.consumer31/41/61/71/81/101` | `rag.rpc.consumer31/41/61/71/81/101` |
| Redis | `taskprobe-redis:56380` **db0** | 同容器 **db1** |
| PG | `task_go_db`（GORM `AutoMigrate` 建表） | `task_rag_db`（Flyway 建表） |

同一批 7 条 raw 日志（`LogEvent` JSON，Go `initGoKa.go:19-27` 的字段）**同时**灌两侧的 in-topic：

```
user 5001 × 5 条（article 9001）→ 恰好达到 target=5 ⇒ 走「完成」分支
user 5002 × 2 条（article 9002）→ 未达标 ⇒ 走「doing」分支
```

覆盖了 **用户点赞路 + 文章路** 的两条完整链路（raw → goka 路由 → goka 计数 → filtered → kq → sink → Redis/PG）。

### 结果

**10 个 topic 消息数逐个相等**（各 7 条），两侧消费者都认领了分区，Go 侧 goka 无错误。

| 对拍项 | Go | RAG | 结论 |
|---|---|---|---|
| Redis `like:total:5001` / `:5002` | `5` / `2` | `5` / `2` | ✅ |
| Redis `task:init:5001:1902` / `:5002:1902` | `1` / `1`，TTL 90d | 同 | ✅ |
| Redis `task:inits:190:5001:9001` / `:5002:9002` | `1` / `1`，TTL 24h | 同 | ✅ |
| Redis `task:progress:*`（4 个 Hash） | status/progress/target 全同 | 同 | ✅ |
| Redis `task:done:5001:1902`（90d）/ `task:done:190:5001`（120d） | `1` / `1` | 同 | ✅ |
| Redis `article:like:cnt:9001` / `:9002`（120d） | `5` / `2` | 同 | ✅ |
| Redis `stream:task:reward` | 2 条，字段集 `{reward_id,uid,task_id,ts,score}` | 同 | ✅ **连 `reward_id` 都逐个相同**（1902→`6903`、190→`5191`） |
| PG `task_progress` | 4 行 | 4 行，字段全同 | ✅ |
| PG `user_like_count` | `5001`→5、`5002`→2 | 同 | ✅ |
| goka 计数值（用户路 / 文章路） | compacted table topic 最后值 `5001`→5、`5002`→2、`9001`→5、`9002`→2 | Redis `task:goka:count:*` 同值 | ✅ |

⇒ **✅ 两侧最终可观测状态完全一致**（脚本：`tools/difftest/parity-task-kafka.py`，`EXIT=0`）。

### 三处**必须归一化**的差异（否则全是噪声）

1. **时间戳**：两侧是**两套独立进程**，写入时刻天然不同（实测差 4 秒）。
   Redis Hash 的 `createAt`/`updateAt`/`doneAt` 与奖励流的 `ts` ⇒ 只比「在不在」。
2. **奖励流顺序**：两侧都是**遍历 map**（Go `like_sink.go:270` `for uid, total := range batch`
   —— Go 的 map 序**随机化**；RAG 是 `HashMap` 序）⇒ 事件顺序**两侧都不是契约**，按集合比。
   实测 Go 先 1902 后 190，RAG 先 190 后 1902。
3. **`task:goka:count:*` 是 RAG 独有**：RAG 的 goka 计数在 **Redis**（DRIFT-TASK-RPC-001 的修复），
   Go 的在 **Kafka compacted table topic**（`task.rpc.consumer*-table`）⇒ 两侧 key 集合**必然不等**。
   ⇒ 这几个 key 单列，**拿 RAG 的值去对 Go 的 table topic**（真正该比的是计数值 —— 实测一致）。

### 怎么复现（全部已入仓，一条命令）

```bash
./tools/difftest/run-task-kafka-e2e.sh          # 全流程：编 Go + 编 RAG + 起基建 + 灌消息 + 比对 + 收摊
KEEP_UP=1 ./tools/difftest/run-task-kafka-e2e.sh # 跑完保留服务与容器，便于手查
```

| 文件 | 作用 |
|---|---|
| [`tools/difftest/run-task-kafka-e2e.sh`](../../../tools/difftest/run-task-kafka-e2e.sh) | **编排入口**（把本轮踩过的 6 个坑全部固化在注释里） |
| [`tools/difftest/docker-compose.task-kafka.yml`](../../../tools/difftest/docker-compose.task-kafka.yml) | task 域专用基建（postgres + redis + **kafka** KRaft） |
| [`tools/difftest/initdb-task.sql`](../../../tools/difftest/initdb-task.sql) | 补建 `task_rag_db`（`task_go_db` 由容器 `POSTGRES_DB` 建） |
| [`tools/difftest/parity-task-kafka.py`](../../../tools/difftest/parity-task-kafka.py) | 最终态比对（Redis 全 key + 奖励流 + PG 两表 + goka 计数值） |

⚠️ 脚本里固化了本轮实测出来的**几个反直觉点**，改动前先读它的注释：
① 容器内 Kafka CLI 必须用 **INTERNAL 监听器 `localhost:19092`**；
② 重置 Kafka 必须**重建容器**（删 topic 不删 `__consumer_offsets` 里的已提交位点）；
③ 还必须移走 goka 的**本地 LevelDB 缓存**；
④ 灌消息前必须等**两侧 12 个 consumer group 全部注册** ——
Go 的 kq 消费者 `Offset` 硬编码 `latest`（`initUserFilter.go:47`），
灌早了它会从队尾之后开始 ⇒ **一条都收不到**，
表现极具迷惑性（goka 三段全通、RAG 全通，只有 Go 的 `like:total` 为 0）。

### 🔴 这一轮顺带抓出并修掉了两条真缺陷

对拍不是「跑一遍确认一致」而已 —— 它**抓出了两个此前无人发现的致命缺陷**：
[DRIFT-TASK-RPC-004](#🔴-drift-task-rpc-004--redispipelines-把-go-的-pipeline-当成-multiexec-事务--整条消息消费链路是死的)（整条消息消费链路是死的）
与 [DRIFT-TASK-RPC-005](#🔴-drift-task-rpc-005--pipelinehset-吞掉-redis-错误而-go-是向上抛终态分叉)（吞/抛方向相反导致终态分叉）。

---

## 四·四、RedisPipelines 修复的取证链（探针 + 变异测试）

**为什么单独一节**：AGENTS.md §3.3 要求「机制生效」必须有**运行证据**，
且 §3.3 的变体要求「探针自己也要被验证」。本节把两样都摆出来。

### 探针（真 Redis，带阳性对照）

`/tmp/task-difftest2/probe/RedisPipelineProbe.java`（一次性取证脚本，不入仓）：

| 探针 | 内容 | 结果 |
|---|---|---|
| **P1 阳性对照** | 把**修复前的写法原样**跑一遍（`executePipelined` 内调 `operations.exec()`） | ✅ 确实抛，异常链 = `RedisPipelineException` → `RedisCommandExecutionException: ERR EXEC without MULTI`（与生产实测栈同源） |
| P2 | 修复后的 `executePipelined`，SETNX × 3 | ✅ `[true,true,true]`，元素类型 `java.lang.Boolean`；再来一次 `[false,false,false]`（SETNX 幂等）；TTL 已设 |
| P3 | HSET + EXPIRE 批量路径 | ✅ 字段与 TTL 都写入 |
| P4 | `execQuietly` 候选 | ✅ 不抛异常、数据真落地 |
| P5 | 批量性取证 | ✅ 逐条 `execute` **1574 ms** vs `executePipelined` **37 ms**（**~42x**）⇒ 确实是批量 |

⚠️ P1 的断言一开始写成「顶层 message 含 `EXEC without MULTI`」而**失败** ——
顶层是 Spring 包装的 `RedisPipelineException`，真正的原因在 **cause 链**里。
⇒ 必须沿 cause 链断言，否则阳性对照会变成「假失败」。

### 变异测试（证明守门测试不是假绿）

把 `RedisPipelines.exec` 故意退回 `return operations.exec();` 后重跑
`RedisPipelinesRealRedisGateTest`：**9 条里 5 条报错**（含两条端到端 sink 用例）：

```
execReturnsOrderedBooleans / execResultElementsArePlainBooleans /
execActuallyWritesKeysWithTtl / userSinkFlushOnceWritesEverything /
userSinkCompletesWhenTargetReached
```

⇒ 守门测试**确实能抓住**这个缺陷。恢复修复后 `tests=9 failures=0 skipped=0`。

### 守门测试落点

`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/RedisPipelinesRealRedisGateTest.java`
—— 真 Redis 容器（`redis:7-alpine`）+ 真实 `LettuceConnectionFactory`，9 条用例，
其中两条**直接驱动真实的 `UserLikeProgressSink`**（`onMessage` → `flushOnce`），
断言 `task:init:*` / `task:progress:*` / `like:total:*` / `task:done:*` 全部落地。

⚠️ 配套改了 `TaskDomainServiceTest` 的 3 处打桩点（`execute` → `executePipelined`）。
那 3 处**锁的是旧机制**（而旧机制正是 bug 本身），必须一起改；
但它们真正要守的契约（回填命中的 **key** 与 **TTL**）保持不变。

---

---

## 四·五、跨语言 gRPC 互调 + Kafka 畸形消息活体对拍（2026-10-24 实测）

**这一节关闭了此前两条「只有源码级对照」的缺口**（原 §七 待办第 1 条与 §五 证据边界第 2/3 条）。

### 四·五·一、gRPC **跨语言**互调：Go 客户端 → 两端服务端

**此前只覆盖了一个方向**：仓库里的
[`tools/difftest/GrpcTaskDualPortRunner.java`](../../../tools/difftest/GrpcTaskDualPortRunner.java)
是 **Java 客户端**打两侧（`RAG client → Go server` + `RAG client → RAG server`）
⇒ **缺**「Go 客户端 → RAG 服务端」这一方向。

**为什么这不是同一件事**：两个方向的互操作风险不同 ——
grpc-go 与 grpc-java 的 wire 行为（metadata / 压缩 / deadline / error 传播）、
go-zero 的 zrpc 客户端能不能把 RAG 端的错误码与描述**原样**带回来。

**做法**：在**冻结真源之外**写一个 Go 客户端
（[`tools/difftest/grpcxlang/main.go`](../../../tools/difftest/grpcxlang/main.go)），
用 `go.mod` 的 `replace` 引用真源**自己生成**的桩（`sea-try-go/service/task/rpc/pb`）
⇒ 真源**零写入**，而且用的是真源的桩（不是手抄的 proto）。
⚠️ 真源的 pb 包名是 goctl 的占位符 `package __` ⇒ import 必须带别名（真源自己也这么写）。

**结果（2026-10-24 实测，`EXIT=0`）**：

| 用例 | Go client → Go server | Go client → **RAG** server | 判定 |
|---|---|---|---|
| `uid=0` | `InvalidArgument` / `"user_id invalid"` | `InvalidArgument` / `"user_id invalid"` | ✅ code+description 逐字节一致 |
| `uid=-1` | 同上 | 同上 | ✅ |
| `uid=MinInt64` | 同上 | 同上 | ✅ |
| `uid=MaxInt64` | `OK` | `OK` | ✅ 载荷逐字段一致 |
| `uid=1` | `OK` | `OK` | ✅ |
| `uid=1001` | `OK` | `OK` | ✅ |
| `uid=1003` | `OK` | `OK` | ✅ |

⇒ **7/7 一致，`CODE_MISMATCH=0`、`DESCRIPTION_DIFF=0`、`载荷不一致=0`**。
⇒ 与 Java 版 runner 的 uid 矩阵、载荷渲染格式**完全同款** ⇒ 两份报告可直接并读。

**复现**：
```bash
./tools/difftest/run-task-grpc-xlang.sh     # 需两侧 task-rpc 已起
```

### 四·五·二、Kafka **畸形消息**活体对拍（五个用例）

**此前只有源码级对照**，而源码级对照有个致命盲区：
**「Go 声明的契约」与「Go 实际的行为」不是一回事** ——
本节就是为了把这类差异**量化**出来。

**做法**：往两侧**同一批**注入「坏消息」，比对可观察后果
（consumer group 的 committed offset + Redis 有没有写出副作用）。
脚本：[`tools/difftest/run-task-kafka-malformed.sh`](../../../tools/difftest/run-task-kafka-malformed.sh)。

| 用例 | 注入 | Go 期望（出处） | 实测（Go / RAG） | 判定 |
|---|---|---|---|---|
| **A** `uid <= 0` | filtered.User key=`0` | `return nil` ⇒ **提交位点**、跳过（`like_sink.go:87-89`） | 位点都推进 / `like:total:0` 都不存在 | ✅ |
| **B** `total <= 0` | filtered.User key=`6002` value=`0` | `return nil` ⇒ **提交位点**、跳过（`like_sink.go:97-99`） | 位点都推进 / `like:total:6002` 都不存在 | ✅ |
| **C** `key` 解析失败 | filtered.User key=`not-a-number` | `return err` ⇒ **不提交**（`like_sink.go:83-85`） | 两侧 committed 都**停在基线**（3 → 2s 后 3 → 10s 后 3） | ✅ |
| **D** `LogEvent` 解析失败 | in-topic value=`{not json` | `log.Println(err)` 后**照样 Emit** ⇒ 提交 + 转发（`initGoKa.go:63-69`） | in-topic 位点都推进；raw.User 各 **+1** 条、**key 为空串**、**value 是 raw 原文** | ✅ |
| **E** 毒丸后再来一条合法消息 | filtered.User key=`6003` value=`2` | 两侧都**继续处理下一条**，提交时**跨过**失败那条 | Go 基线 3 → **5**；RAG 基线 3 → **5**；`like:total:6003` 两侧都写出 | ✅ |

⇒ **五个用例两侧行为完全一致，`EXIT=0`**。

#### 🔴 本轮**推翻了一条写在本仓 javadoc 里的断言**（AGENTS.md §3.3.2 的高危项）

`TaskRpcApplication#noCommitErrorHandler` 的 javadoc 此前写着：

> Go 用的 segmentio/kafka-go Reader 带 `CommitInterval: 1s`（`go-queue/kq/queue.go:125,151`），
> 它会周期性自动提交已 fetch 的位点，因此 Go 那句「error ⇒ 不 CommitMessages」
> 在实践中**会被自动提交抵消**（约 1 秒后照样提交）。
> Java 侧 … 是**严格更强**的「失败不提交」。

**这条是错的**，两处独立证据：

1. **kafka-go 自己的文档**（segmentio/kafka-go@v0.4.47 `reader.go:811-812`）：
   > `FetchMessage does not commit offsets automatically when using consumer groups.
   > Use CommitMessages to commit the offset.`

   而 kq 用的**正是** `FetchMessage`（`go-queue/kq/queue.go:264`）
   ⇒ `CommitInterval` 的自动提交路径**根本不生效**。
2. **本节的活体实测**（用例 C）：注入 key 解析失败的消息后，
   **两侧**的 committed offset 都不推进（基线 3，2s 后 3，10s 后 3）
   ⇒ Go 侧**并没有**「被自动提交抵消」。

⇒ **两侧的真实差异只有一处**：失败的那一刻**都**不提交位点，
但**都**会继续处理下一条；下一条成功时提交会**跨过**失败那条
⇒ 失败那条实质被跳过（运行期不重投）。
⇒ 已把该 javadoc 改写为**实测证实的**说法（源码 + 活体双出处）。

#### ⚠️ 本节的副作用（必须知道再跑）

用例 C 会往 `filtered.User` 投「毒丸」（key 解析失败那条），
该分区的消费者会一直重投 ⇒ **跑完两侧栈都需要重启**才能恢复正常。
脚本结尾已写明；重启用 `./tools/difftest/run-task-http-parity.sh`（会重建容器与栈）。

#### ⚠️ 顺带修掉了编排脚本的一个真坑

`run-task-http-parity.sh` 此前 `pkill` 之后只 `sleep 2` ⇒
**JVM 的优雅关闭（Spring close + Hikari 关池 + Kafka 消费者下线）实测要 3~4 秒**
⇒ 新进程 `BindException: Address already in use` ⇒ 整轮对拍白跑，
而症状是「api 一直 503」，很容易误判成应用问题。
⇒ 已改成**轮询等端口释放 + 等端口开始监听**，且进程起不来时**立刻失败并打日志尾部**。

---

## 四·七、goka 表的「保留策略」到底怎么算（2026-10-24 实测）

> **这一节推翻本台账上一版的一条结论**（§六 曾写「goka 的表会随 Kafka retention 滚动」）。
> 结论变了，但**不是**「RAG 有缺陷要修」——恰恰相反：**RAG 现在的做法就是对齐的**。
> ⇒ 这也是本仓 §3.1「不要拿现有 RAG 代码或注释当对照基准」的另一个样本：
> **两边的台账都可能写错，只能靠实测定案。**

### 四·七·一、Go 侧 goka **不创建** topic —— 缺 topic 直接 `log.Fatal`

**这是本轮第一个实测结论**，它决定了「RAG 侧要不要也去管 topic 生命周期」这个问题。

| 步骤 | 出处 | 事实 |
|---|---|---|
| goka 准备 topic 的**第一步** | `goka@v1.1.16 processor.go:1012` → `ensureCopartitioned(tm, gg.copartitioned().Topics())` | 先检查**输入 topic 是否存在** |
| topic 不存在时 | `goka@v1.1.16 processor.go:1035-1040` `ensureCopartitioned` → `tm.Partitions(topic)` 失败即 `return 0, fmt.Errorf("Error fetching partitions for topic %s: %v", ...)` | **直接返回错误** |
| `prepareTopics` 因此中断 | `processor.go:1012-1014` | **`EnsureStreamExists` 根本没被调用** |
| `NewProcessor` 失败 | `initGoKa.go:138-145` | **`log.Fatal(err)` ⇒ 进程退出** |

**实测**（2026-10-24，真 Kafka 容器，干净 broker，**不预先建 topic**）：

```
2026/10/24 04:58:59 start task kafka filter
2026/10/24 04:58:59 Error fetching partitions for topic task.logs.raw: requested topic was not found
⇒ 进程退出（pgrep 查不到 go-task-rpc）
```

⇒ **Go 侧 goka 的「建 topic」代码路径是死的**：它要求**所有输入 topic 已被外部预先创建**，
自己只做「**校验**」（`EnsureStreamExists` 的 config 比对），且 config 不匹配时
**`handleConfigMismatch` 的 default 分支直接 `return nil`、连日志都不打**
（`topic_manager.go:158-167` ⇒ `MismatchBehavior=Ignore`）。

⇒ **两侧的 topic 生命周期都由部署侧（运维 / 脚本）负责，组件本身不管。**
⇒ ⇒ **RAG 侧「不建、不改 topic」是与 Go 一致的做法**；
本轮一度准备新增的「RAG 侧用 `KafkaAdmin` 建/校准 topic」**因此取消** ——
那会制造一条与 Go 的**结构性**不一致（Go 不建，RAG 建）。

> ⚠️ **附带发现的一条真实差异**（已登记为有意偏离）：
> Go 侧 **topic 缺失 ⇒ 进程 `log.Fatal` 退出**；RAG 侧 **topic 缺失 ⇒ listener 持续重试、
> 服务照常起**。见 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md)。

### 四·七·二、`cleanup.policy=compact` **不按时间删** —— 决定性对照实验

**要回答的问题**：Go 的计数表有一份 Kafka compacted topic 副本
（`<group>-table`），它的 config 只设了 `cleanup.policy=compact`、**没有 `retention.ms`**
（`goka@v1.1.16 topic_manager.go:317-326`）。
那么 **compact 策略下 `retention.ms` 会不会把 key 删掉**？
（Kafka 的按时间删除是**按 segment 粒度**的 ⇒ 实验必须让 segment 先滚动。）

**实验**（真 Kafka KRaft，5 个 topic，每个写入都**先验证 `offset > 0`**）：

| topic | `cleanup.policy` | `retention.ms` | `segment.ms` | 45 秒后读回 | 说明 |
|---|---|---|---|---|---|
| `probe.y_short` | **compact** | **1000** | 1000 | **1 条（存活）** | ⭐ **核心**：compact 单独使用**不按时间删** |
| `probe.z_delete` | delete | 1000 | 1000 | **0 条（被删）** | ✅ **阳性对照**：同参数下 delete **确实**被删 ⇒ 机制有效、探针有区分力 |
| `probe.w_default` | delete | 默认 604800000 | 默认 | 1 条（存活） | ✅ 阳性对照 |
| `probe.x_infinite` | compact | -1（无限） | 1000 | 1 条（存活） | ✅ |
| `probe.v_noseg` | compact | 1000 | **默认（不滚动）** | 1 条（存活） | ✅ **segment 粒度对照**：证明 `y_short` 的存活不是因为 segment 没滚动 |

⇒ **`cleanup.policy=compact` 单独使用时，Kafka 不启用按时间删除** ⇒
Go 的 compacted table topic 的 key **不按时间过期**。

### 四·七·三、结论：RAG 的 `task:goka:count:*` **不设 TTL 才是对齐**

把两侧的**权威存储**摆在一起看：

| | Go | RAG |
|---|---|---|
| 权威的计数存储 | **本地 LevelDB**（`goka storage/builders.go:18-26` `leveldb.OpenFile(fp, nil)` ⇒ 默认**无 TTL**） | **Redis**（`TaskCountStore` 的 `INCR`） |
| 何时删 key | `PartitionTable.Delete`（`partition_table.go:622`）**在 task 域从未被调用**（两个 processor 只有 `ctx.Value()`/`ctx.SetValue()`，`initGoKa.go:75-81` / `:92-97`）⇒ **永不删** | **永不删**（无 TTL） |
| Kafka 副本 | compacted topic `<group>-table`，`cleanup.policy=compact`、无 `retention.ms` ⇒ **不按时间过期** | **不存在**（计数直接落 Redis） |
| 副本有人消费吗 | ❌ **全仓无任何读取点**（唯一写它的是 goka 自己） | — |

⇒ **Go 的计数永不丢失；RAG 的计数也永不丢失 ⇒ 等价。**
⇒ ⇒ **给 `task:goka:count:*` 加 TTL 会制造一条与 Go 的新漂移**，而且**造成业务错误**：
计数到期归零 ⇒ `like:total:<uid>` 是**普通 SET**（不是 GREATEST，
`UserLikeProgressSink.pipelineSetLikeTotal`）⇒ 被**更小的值覆盖**；
达标判定 `total >= target` 读的是计数器 ⇒ 用户得重新攒够次数。

⇒ **本轮的交付不是「加 TTL」，而是「把『不设 TTL』锁成契约」**：
新增真 Redis 守门测试
[`TaskCountStoreRetentionContractTest`](../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/TaskCountStoreRetentionContractTest.java)
（4 条，含阳性对照：先证明「读 TTL」这个探针能区分「有 TTL / 无 TTL」，
再断言计数 key 的 `TTL == -1`）⇒ **后人若好心加 TTL，测试立刻变红。**

#### ⚠️ 附带查明的一个 Kafka 事实（顺带解释了 Go 侧一个细节）

Kafka 的 **compacted topic 强制要求消息带 key**：

```
org.apache.kafka.common.InvalidRecordException:
  Compacted topic cannot accept message without key in topic partition probe.x_infinite-0
```

⇒ Go 侧 goka 一直带 `ctx.Key()` emit（`initGoKa.go:81` / `:106`）才写得进去；
⇒ 也从侧面印证 compacted topic 存的是「**key → 最新值**」，与 RAG「一个 key 一个值」同构。


---

## 四·八、goka `OffsetOldest` 活体验证：**查出并修掉一条真漂移**（2026-10-24 实测）

> 这一节关闭了本台账 §三 末条那条「只核到配置意图一致，**未做活体验证**」的缺口。
> ⚠️ **而且验证本身查出了一条真漂移** —— 台账上一版写的「与 Go 一致」**是错的**。
> 脚本：[`tools/difftest/run-task-offset-oldest.sh`](../../../tools/difftest/run-task-offset-oldest.sh)。

### 四·八·一、为什么必须换一种对拍顺序

⚠️ **本域此前所有 Kafka 对拍都是「先起消费者、后灌消息」**
（E2E、畸形消息、HTTP 双侧全是这个顺序）⇒
那种顺序下 `auto.offset.reset=earliest` 与 `=latest`
**没有任何可观察差别**（消息都产生在消费者 join **之后**）
⇒ **「earliest 与 Go 一致」这个结论在所有既有对拍里都是恒真的** —— 它**没被验证过**，只是**被顺序掩盖了**。

⇒ 本次改成的顺序：

```
阶段 B：① 建 topic → ② **预灌消息** → ③ 起消费者（全新 group ⇒ 无已提交位点）
阶段 C：④ 断言（earliest 正向 + latest 阴性对照 + 阳性对照）
阶段 D：⑤ **消费者就位之后**再投递 ⇒ 端到端断言（无竞态）
```

### 四·八·二、结果：**8 条断言全过，失败项 0**

| # | 观测点 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| A1 | Go `task.rpc.consumer61-table`（compacted table topic）key=`5001` 的最后值 | `5` | `5` | ✅ Go goka 用户路 **earliest 生效** |
| **A2** | **RAG Redis db1 `task:goka:count:rag.rpc.consumer61:5001`** | `5` | **`5`** | ✅ **RAG goka 用户路 earliest 生效**（修复前是 `<absent>`） |
| A3 | Go `task.rpc.consumer71-table` key=`9001` | `5` | `5` | ✅ Go 文章路 earliest 生效 |
| **A4** | **RAG Redis db1 `task:goka:count:rag.rpc.consumer71:9001`** | `5` | **`5`** | ✅ **RAG 文章路 earliest 生效**（修复前是 `<absent>`） |
| A5 | 两侧 `like:total:7777`（**预灌**在 filtered.User 的 kq 输入上） | **两侧都不存在** | Go `<absent>` / RAG `<absent>` | ✅ **阴性对照**：kq 的 `latest` 确实跳过了预灌消息 ⇒ 探针**有区分力** |
| A6 | 两侧 `like:total:5001`（端到端） | `10` | Go `10` / RAG `10` | ✅ **预灌 5 + 后灌 5 = 10** ⇒ **预灌那批确实被计入了** |
| A7 | 两侧 `like:total:5002` | `4` | Go `4` / RAG `4` | ✅ 同上（预灌 2 + 后灌 2） |
| A8 | 两侧 `like:total:8888`（**启动后**投递到 filtered.User） | `42` | Go `42` / RAG `42` | ✅ **阳性对照**：kq 路本身正常 ⇒ A5 的「不存在」不是因为 kq 坏了 |

⇒ **A6 的 `10` 是最有说服力的一条**：它是「**预灌的 5 条也被计入了**」的端到端铁证
—— 若 earliest 没生效，计数只会是 5。

⇒ **consumer group 位点**（阴性对照的直接证据）：

| group | CURRENT-OFFSET | LOG-END-OFFSET | 说明 |
|---|---|---|---|
| `task.rpc.consumer61`（Go） | **7** | 7 | ✅ 读了全部 7 条预灌消息 |
| `rag.rpc.consumer61`（RAG） | **7** | 7 | ✅ 同上（**修复前是 0**） |

### 四·八·三、🔴 查出的真漂移：per-listener 的 `auto-offset-reset` **从未生效**

**在「修复前」跑同一套脚本**，结果是 **A1/A3 PASS、A2/A4 FAIL**（`<absent>`）：

```
✓ PASS  [A1] Go goka 用户路 … 实测=5
✗ FAIL  [A2] RAG goka 用户路 … 实测=<absent>
✓ PASS  [A3] Go goka 文章路 … 实测=5
✗ FAIL  [A4] RAG goka 文章路 … 实测=<absent>
```

⇒ **Go 从队头读、RAG 从队尾读** ⇒ 行为分叉。
⇒ 根因与修法见 [DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)。

### 四·八·四、⚠️ 本脚本固化了两个「环境级」坑

1. **端口不能写死**：2026-10-24 实测，**user 域的并发会话**的 `go-user-rpc` 正占着 **19005**
   ⇒ 本脚本的 Go 侧直接 `bind: address already in use` 退出。
   ⇒ 脚本改为**自动挑空闲端口**（从 19005 / 59005 往后找），并生成一份改过 `ListenOn` 的 Go 配置副本。
   ⇒ ⚠️ **绝不 kill 别人的进程** —— 那是别的域的基建。
2. **compose 必须带 `-p taskprobe`**：见 §四·七·一 与
   [`docs/Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) 的 `PITFALL-TASK-RPC-20`。

### 四·八·五、复现

```bash
./tools/difftest/run-task-offset-oldest.sh          # 完整跑一遍
KEEP_UP=1 ./tools/difftest/run-task-offset-oldest.sh # 跑完保留服务与容器
```

⚠️ 脚本会**重建 `taskprobe-*` 三个容器**（⇒ 该容器上的既有数据会被丢弃），
并**只**停自己起的两个进程（按 `$WORK` 目录路径匹配 ⇒ 不会误杀别的域的同名进程）。

## 四·九、🔴 配置键名扫描：yaml 里的「死配置」是怎么被查出来的（2026-10-24 实测）

> 本节是 [DRIFT-TASK-RPC-006](#🔴-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)
> 的**家族性排查**：那一条是 Kafka 侧的键名写错（连字符 vs 点分）⇒ 静默失效。
> 本节把同一失效家族在 **yaml 侧**扫了一遍，并固化成守门测试。

### 四·九·一、扫描方法：三源交叉

一个 yaml 键要算「**有消费方**」，必须落在下面三类之一：

| 源 | 依据 | 覆盖 |
|---|---|---|
| **框架元数据** | classpath 上任意 jar 的 `META-INF/spring-configuration-metadata.json` | Boot 4 每个 starter 都带这个文件 ⇒ 框架键的**权威清单** |
| **自有前缀** | `@ConfigurationProperties(prefix = "...")` | 本域 `task.definitions` / `task.kafka`；跨域共享 `observability` / `rag.protection` |
| **`@Value` 占位符** | 源码里 `@Value("${key}")` | 逐个消费点 |

### 四·九·二、查出的三类孤儿键（**都不是 task 域独有 ⇒ 跨域共性**）

#### ① 🔴 `spring.mybatis-plus.*` —— **前缀层级写错，三个键全是死键**

| 事实 | 出处 |
|---|---|
| MyBatis-Plus 的真实前缀是**顶层** `mybatis-plus` | `MybatisPlusProperties` 的 `@ConfigurationProperties(prefix = "mybatis-plus")`（javap 实测字节码） |
| 框架元数据里也是顶层 | `mybatis-plus-spring-boot-autoconfigure-3.5.17.jar` 的 metadata：`mybatis-plus.configuration.map-underscore-to-camel-case` |
| **RAG 写成了 `spring.mybatis-plus.`** | `application.yml:40-45`（写在 `spring:` 段下）⇒ 层级多了一层 |
| 探针实测：写错的层级**能读到值**、正确层级**读不到** | 用 `Binder` 直接读：`spring.mybatis-plus.configuration.map-underscore-to-camel-case = true`，而 `mybatis-plus.configuration.map-underscore-to-camel-case = null` |
| ⚠️ **全仓 8 个 rpc 模块同款** | article / comment / favorite / follow / like / message / task / admin-rpc，**全部**写在 `spring:` 段下 |

**行为影响：零**（三处逐一核过）：

| 键 | 若真生效会怎样 | 实际（因为不生效） | 是否有差异 |
|---|---|---|---|
| `map-underscore-to-camel-case: true` | 开启驼峰映射 | MyBatis-Plus 的 `MybatisConfiguration` 构造器里**本来就是** `mapUnderscoreToCamelCase = true`（javap 实测字节码：`iconst_1; putfield mapUnderscoreToCamelCase`） | ❌ 无 |
| `global-config.banner: false` | 关掉启动横幅 | 横幅照打（只影响启动日志） | ❌ 无（仅日志噪音） |
| `global-config.db-config.id-type: INPUT` | 不用雪花 ID | task-rpc **全部写显式 SQL、不用 BaseMapper**（`TaskProgressMapper` / `UserLikeCountMapper` 都是 `@Insert`）⇒ `id-type` 无使用点 | ❌ 无 |

⇒ **判定：不是行为漂移，是「配置面是死的」**（运维改 yaml 不报错也不生效，AGENTS.md §3.3.1 同款形态）。
⇒ **不在本域单方面修**：全仓 8 个 rpc 模块同款，单改 task-rpc 会让本域与其它域**不一致**（与 DRIFT-TASK-API-004 同款处置）。

#### ② 🔴 `otel.*` —— **Boot 4 根本没有 OTel 自动配置**（与 Go 有**观测差异**）

| 事实 | 出处 |
|---|---|
| **Boot 4.0.8 全 jar 搜不到任何 `Otel*` 自动配置类** | 遍历 `~/.m2` 下所有 `org/springframework/boot/*/4.0.8/*.jar`，`OtelProperties` / `OtelAutoConfiguration` 命中数 = **0** |
| Boot 3.2.5 至少还有 | `spring-boot-actuator-autoconfigure-3.2.5.jar` 里有 `actuate/autoconfigure/tracing/OpenTelemetryAutoConfiguration` |
| Boot 4 的 actuator-autoconfigure 里 **0 个** tracing 类 | 实测 |
| 本仓**无任何 Java 代码**读 `otel.*` 键 | 全仓 grep `getProperty("otel` / `@Value("${otel` ⇒ 空 |
| 本仓**不注册**全局 OTel SDK | `GlobalOpenTelemetry.set` / `OpenTelemetrySdk.builder` 只出现在 `service/common` 的**测试**与 `communityfact/ProcessLogger`（后者无调用点）⇒ `GlobalOpenTelemetry.get()` 拿到的是 **no-op** 实现 |
| ⚠️ **全仓 20 个 yaml 都有 otel 段** | article / comment / favorite / follow / hot / like / message / task / users 全部 |
| **Go 侧确实导出 span** | `zrpc.MustNewServer(c.RpcServerConf, ...)` → `RpcServerConf` 继承 `ServiceConf` → `core/service/serviceconf.go:67 trace.StartAgent(sc.Telemetry)` ⇒ `Telemetry{Endpoint: jaeger:4317, Batcher: otlpgrpc}` **生效** |

⇒ **判定：存在观测差异**（Go 把 span 发到 jaeger，Java 侧是 no-op tracer）。
⇒ ⚠️ **但这不是 task 域能单方面修的**：① 缺的是 `spring-boot` 的 OTel 自动配置模块（**共享构建基建**）；
② 修复方式必然是**全仓统一**（20 个 yaml + 共同依赖）⇒ 属**跨域**。

#### ③ ⚠️ `spring.jpa.hibernate.ddl-auto` —— 本模块**无 JPA 依赖**

- task-rpc 用 MyBatis-Plus，pom 里**没有** `spring-boot-starter-data-jpa` ⇒ `ddl-auto` 无人绑定。
- **无行为影响**：本就没有 JPA，不存在「自动建表」风险。（⚠️ 但 `spring.jpa.*` 在 6 个 rpc 模块的 yaml 里都有 —— 同款共性。）

#### ④ ⚠️ `task.reward.group-name` —— **两侧都是死配置**（不是漂移）

| 事实 | 出处 |
|---|---|
| Go 侧 `GroupName` 是**写死的常量**，Go yaml 里**根本没有**这个字段 | `service/task/rpc/internal/reward/type.go:10 GroupName = "task-reward-group"` |
| 唯一消费点是**未启动**的 Worker | `reward/worker.go:39,57,87` 用 `GroupName`；而 `task.go:35 //go Init.StartInitReward(ctx)` **被注释**、`initReward.go` **整文件是注释** |
| RAG 侧保留它只为配置面完整 | `application.yml:220` 的注释自己写了「只被 Worker 使用，而 Worker 没有被启动」 |

⇒ **判定：不是漂移**（Go 是「压根没这个字段」，RAG 是「有但不生效」）⇒ 行为一致。

### 四·九·三、固化成守门测试

新增 [`TaskConfigKeyBindingContractTest`](../../../service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskConfigKeyBindingContractTest.java)
（**5 条，全绿**），断言四层：

| # | 断言 | 作用 |
|---|---|---|
| 1 | 框架元数据**真的被读到**（> 500 个属性名 + 三个定点） | 阳性对照：证明扫描器不是空跑 |
| 2 | 扫描器**能**识别孤儿键（不存在的键 / 错层级的键 ⇒ 判为孤儿） | 阳性对照：证明扫描器有识别能力 |
| 3 | **主断言**：yaml 不得出现「无人认领且未登记」的键 | 以后新增死配置会被立刻抓住 |
| 4 | **白名单纪律**：每条登记都必须**真的盖住至少一个孤儿键**，且写明原因 | 防止白名单写成空壳 / 变成「什么都往里塞」的逃生舱 |
| 5 | 自有前缀（`task.*`）的每个键都**必须绑得到** `@ConfigurationProperties` 类的某个属性 | 守住 `initTtlDays ↔ init-ttl` 那类历史缺陷 |

**白名单是「已知孤儿键」的显式台账**（每条带出处与定性），新增死配置必须显式登记才能过
⇒ **静默放过 = 把「配置面看起来在管事、实际没在管事」重新引进来**（AGENTS.md §3.3.1）。

#### ✅ 变异测试（三种都精确命中 ⇒ 非假绿）

| 变异 | 期望 | 实测 |
|---|---|---|
| **① 把 `mybatis-plus` 段移回顶层**（修正前缀） | `spring.mybatis-plus.` 三个键不再是孤儿 ⇒ 白名单纪律断言必须红 | ✅ 红，报「白名单项 'spring.mybatis-plus.' 一个孤儿键都没盖到 ⇒ 该删这条登记」 |
| **② 新增 `spring.kafka.consumer.bogus-typo-key`** | 主断言必须红并点名该键 | ✅ 红，报「出现「无人认领且未登记」的键：[spring.kafka.consumer.bogus-typo-key]」 |
| **③ `init-ttl` → `init-ttl-days`**（复现历史缺陷 `initTtlDays`） | 绑定断言必须红 | ✅ 红，报「键 task.definitions.like.init-ttl-days 在 yaml 里存在，但绑不到 TaskSinkProperties」 |

⚠️ **本测试自己踩过 3 个坑**（第一版 4 条红，其中 3 条是**测试自己的缺陷**、不是配置的）：
1. **元数据分段前缀匹配是错的** —— Boot 4 的 metadata 只列**叶子**属性名（`spring.kafka.consumer` **不存在**），
   若按「逐段前缀命中即放行」⇒ 任何 `spring.kafka.consumer.<随便>` 都被判成「有消费方」⇒ 扫描器形同虚设。
   ⇒ 改为**精确匹配 + 显式登记的 map 型前缀**（`logging.level` / `management.metrics.tags` / `spring.kafka.*.properties`）。
2. **不能用 `binder.bind(prefix + "." + subKey, Target.class)` 判绑定** —— 本域属性是**嵌套**的
   （`TaskSinkProperties.like.initTtl`）⇒ 那样判必然全部「绑不上」。
   ⇒ 改用 `binder.bind(prefix, Bindable.of(Target), BoundPropertiesTrackingBindHandler)`
   记录**真正被绑定的属性名**再比对。
3. **`BoundPropertiesTrackingBindHandler` 的消费者是 `ConfigurationProperty`**（`getName()` 返回
   `ConfigurationPropertyName`，不是 String）⇒ 必须 `.toString()`。
⇒ 详见 [`docs/Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) 的 PITFALL-TASK-RPC-23 ~ 25。

---

## 四·十、「台账 vs 实际」的复核（2026-10-24 实测）

> ⚠️ **为什么需要这一节**
> 台账里的「已修 / 未修 / N 条 / `file:line`」这类断言**过期时不会报错** ——
> 代码被回退了、测试数变了、Go 侧行号漂了，**没有任何东西会报警**。
> AGENTS.md §6 要求「任何『已修 / 未修』断言都必须带**核对日期**，否则按 `UNKNOWN` 处理」
> ⇒ 这一节把那条例外**机械化**：把台账的断言逐条拿去和仓库/Go 真源对拍。

### 四·十·一、复核器：`tools/check-ledger-selfcheck.py`

五层检查（**只读**，不改任何文件）：

| 层 | 检查什么 | 判据 |
|---|---|---|
| 1 | 台账引用的**仓库内路径**是否存在 | 文件存在 |
| 2 | 台账的「已修」断言 → 仓库里**能不能查到对应物** | 代码里能查到（**注释里的反面教材不算**） |
| 3 | 台账的**数字断言**（测试数 / 条数 / 编号连续性 / commit 数） | 与 surefire 报告、git 实测一致 |
| 4 | 台账的 **Go 侧 `file:line`** 是否悬空 | 文件存在 + 行号在范围内（真源仓库 + 第三方库，按**真源 go.mod 的精确版本**） |
| 5 | 仓库里**存在但台账从未点名**的测试类 | 列出（覆盖缺口） |

**实测结果（2026-10-24）**：层 1~4 **全部通过**，层 5 **0 个缺口**（补登记后）。

### 四·十·二、本轮复核查出的 4 处台账缺陷（**已全部修掉**）

| # | 位置 | 台账写的 | 实际 | 性质 |
|---|---|---|---|---|
| 1 | `_README.md` 章节标题 | 「共 **24 个 commit**」 | **31** | 数字过期 |
| 2 | `_README.md` 第三轮/第四轮清单 | commit 栏写着「（本轮）」「（本次）」**占位符** | 应为真实哈希（第二轮起就已改用哈希，**本轮反而回退了**） | 占位符未落实 |
| 3 | `_README.md` 开放项表上方 | 「第四轮又查出…（**§三·第四轮**）」 | 该节在 **rpc.md §四·九**；`_README` 里**没有 §三** | 章节号失准 |
| 4 | rpc.md / api.md | 4 个测试类**在册但从未被台账点名** | `TaskApiMvcPipelineTest` / `TaskApiWiringTest` / `GetTaskReqParsingTest` / `TaskServiceGrpcImplWireTest` | **覆盖缺口** |

⇒ 4 处都已修：① 改成 31；② 第三轮 5 行填 `6fc73e5`/`0ba9c0b`/`8959779`/`a02a326`/`a75fcb9`、
第四轮合并为 1 行 `f3d4cb4`（**原写成 4 行，实际只对应 1 个 commit** —— 会误导读者）；
第二轮两条填 `e5e47f9` / `14b9560`；③ 改成指向 `rpc.md §四·九`；④ 在两个台账各加一节
「**〇·一、本组件的守门测试清单**」，把**全部**测试类逐个登记（写清锁住哪条契约 + 关键前提）。

### 四·十·三、✅ 复核器自身的变异测试（证明它不是假绿）

| 变异 | 期望 | 实测 |
|---|---|---|
| **① 把某个 listener 的 `auto.offset.reset` 改回连字符**（模拟修复被回退） | 第 2 层必须红 | ✅ 红：「**代码里**仍有 1 处 auto-offset-reset ⇒ 修复被回退」 |
| **② 把台账里的测试数 104 改成 99** | 第 3 层必须红 | ✅ 红：「台账 99 / 实测 104」 |
| **③ 把 Go 侧 `initGoKa.go:131` 改成 `:99999`** | 第 4 层必须红 | ✅ 红：「行号超出文件范围」 |

⇒ 三个变异**全部精确命中** ⇒ 该复核器**有区分力**。

### 四·十·四、⚠️ 复核器的**效力边界**（别高估它）

1. ⚠️ 第 2/4 层只保证「**引用不悬空**」与「**断言有对应物**」，
   **不保证「内容完全对得上」** ⇒ 关键断言仍需**人工抽验**。
   （本轮人工抽验了 4 条核心出处：`goka/processor.go:1012` = `ensureCopartitioned`、
   `goka/options.go:361-363` = `StorageBuilder not set`、
   `kafka-go/reader.go:811-812` = `FetchMessage does not commit offsets automatically`、
   `go-queue/kq/queue.go:264` = `consume()` 里的 `FetchMessage` —— **内容全部对得上**。）
2. ⚠️ 第 4 层里「同名文件在多个库都存在」的 **10 条**会单独列出 ⇒ **归属需人工确认**
   （脚本只保证行号在某个候选文件里合法）。
3. ⚠️ 第三方库必须按**真源 `go.mod` 的精确版本**去核对 ——
   ⚠️ **本机 module cache 里当时只有 go-zero v1.10.3，而真源用的是 v1.10.2**
   ⇒ 用错版本核对会得出错误结论（本轮已按 `go.mod` 下载 v1.10.2 / kafka-go v0.4.47 / go-queue v1.2.2）。
4. ⚠️ 第 5 层只覆盖 **task 域**；其它域要复核得各自扩展 `LEDGER_FILES` 与 `CLAIMS`。

### 四·十·四·二、🆕 第二轮复核（2026-10-24 19:25）查出的**生产类覆盖缺口**

> 第一轮复核只查了**测试类**（层 5）⇒ **生产类一个都没点名**。
> 本轮补上第 6 层（生产类覆盖），查出 **12 个**从未被台账点名的生产类，
> 已全部登记到 **§〇·二「本组件的生产类清单」**。

⚠️ **登记它们的价值不在「列全了」**，而在于这些类里藏着 **4 个「缺了就起不来」**的启动阻塞项：

| 启动阻塞项 | 缺了会怎样 |
|---|---|
| `NacosRegistration` | Bean 名与 `NacosServiceRegistryAutoConfiguration` 撞名 ⇒ `allow-bean-definition-overriding=false` ⇒ **上下文直接起不来** |
| `Jackson2ObjectMapperConfiguration` | Boot 4 默认给的是 **Jackson 3**，与注入点的 Jackson 2 包名不同 ⇒ `NoSuchBeanDefinitionException: ObjectMapper` ⇒ **组件起不来**（纯 Mockito 单测照样全绿） |
| `MybatisPlusOrderingBridgeAutoConfiguration` | MP 的 `@AutoConfigureAfter` 指向 **Boot 3 的包名** ⇒ Boot 4 下**静默悬空** ⇒ MP 先于 DataSource 求值 ⇒ `@ConditionalOnSingleCandidate(DataSource)` 找不到 bean ⇒ **整个自动配置不生效** |
| `TaskKafkaTopics` | `@ConfigurationProperties("task.kafka")` 的宿主 ⇒ 键名对不上就**静默丢弃**；且其中两个 topic 名是**跨域契约**（like-rpc 也在生产）⇒ 改一个字符就断链 |

⇒ **台账不点名 = 以后重构时看不到这条风险** ⇒ 这是「最全」的实质含义。

⚠️ 第 6 层的判定要**认两种登记形态**：纯类名（`TaskKafkaTopics`）与带路径的形式
（`config/TaskKafkaTopics`）⇒ 否则自己写的表会被判成「未点名」（本轮踩到）。

### 四·十·四·三、🔴 第三轮复核（2026-10-24 19:48）：查出的 **commit 清单**结构性缺陷

> 前两轮复核查的是「**内容**对不对」；本轮改查「**元信息**对不对」
> ⇒ 查出**台账自己的 commit 清单**有 4 类结构性缺陷。

#### ① 4 类缺陷（全部已修）

| # | 缺陷 | 证据 | 后果 |
|---|---|---|---|
| 1 | **8 行 commit 栏是占位符「（本轮）」** | 第五轮 2 行 + 第六轮 4 行 + 第五轮之二 2 行 | 占位符**既不是哈希、也不是 commit** ⇒ **完全逃过**「commit 覆盖」判据 ⇒ 读者无法定位它们对应哪个 commit |
| 2 | **同一个 commit 被写成多行** | `c73d9cf` **3 行**（散落在第五轮与第六轮两处）/ `c52cbea` **2 行** / `c886406` **4 行** | 读者会把 **3 个 commit 误读成 9 个** ⇒ 清单整体**虚增 6 行**（50 行 vs 实际 44 个 commit） |
| 3 | **2 个 commit 完全没登记** | `79d4aac` / `c886406` | 补登链在 `79d4aac` 处**断掉**（它补登了 `185448c`，自己却没人补） |
| 4 | 🔴 **「第四轮『写成 4 行却只对应 1 个 commit』」缺陷第三次复发** | 第一次：第四轮；第二次：第五轮；第三次：第六轮 —— **都在同一张表**（commit 清单） | ⇒ 「靠人记着别这么写」**必然复发** ⇒ 判据必须**机械化** |

#### ② 为什么「集合覆盖」这条不变式**抓不到**它们

旧判据是「**每个 commit 的哈希是否在台账里出现过**」—— 只问「**在不在**」，不问「**几行**」：

| 缺陷 | 为什么旧判据看不见 |
|---|---|
| 占位符行 | 它**不是哈希** ⇒ 不进「已登记」集合；它**也不是 commit** ⇒ 不进「漏登记」集合 ⇒ **两边都不沾** ⇒ 完全不可见 |
| 同一个 commit 多行 | 哈希**在**表里 ⇒ 「已登记」判定通过 ⇒ **行数是 1 还是 3 不影响判定** |
| 行数 ≠ commit 数 | 集合比较**没有基数** ⇒ 50 行对 44 个 commit 与 44 行对 44 个 commit **判出来一样** |

⇒ 判据从「**集合覆盖**」升级为「**一一对应**」。

#### ③ 复核器的 3 个新判据（第 3 层）

| 判据 | 抓什么 |
|---|---|
| **占位符残留** | 只允许 **1 个**占位行，且必须在清单**末尾**（那是「本轮还没提交」的那一行） |
| **同一个 commit 多行** | 同一哈希出现 >1 次 ⇒ 报「读者会把 1 个 commit 误读成 N 个」 |
| **登记行数守恒** | 登记行数（去重哈希 + 占位）必须与 commit 数对上：提交后 == commit 数，提交前 == commit 数 + 1 |

🔴 **本轮一并补上的另外 2 条「最全性」判据**（在第 3 层，与数字断言并列）：

| 判据 | 抓什么 | 实测 |
|---|---|---|
| **编号连续性** | `PITFALL-TASK-RPC-01~32` 必须**无断号** | ✅ 连续（32 条） |
| **悬空引用** | 台账里引用的 Pitfall / 签字偏离 **ID 必须真有定义** | ✅ 引用的 32 个 Pitfall + 2 个签字偏离全部有定义 |

🔴 **另加两条**（本轮顺手补的判据盲区）：
**登记了不在本分支上的 commit**（陈旧 / 串分支的登记）· **「台账的行提取」只认 commit 表**
（`_README.md` 里还有开放项表 / 共享工具表 / 合并冲突表，首列同样是反引号包的东西 ⇒ 混进来判据粒度就错了；
且 **markdown 允许两张表紧挨着（中间无空行）** ⇒ 按空行切表会把它们并成一张 ⇒ 本轮自检当场抓到）。

#### ④ ✅ 变异测试**进仓**（`--mutation-test`）

| 变异 | 期望 | 结果 |
|---|---|---|
| 清单里多一个「（本轮）」行 | 报「占位符残留」 | ✅ 精确命中 |
| 同一个 commit 写成两行 | 报「被登记了 2 行」 | ✅ 精确命中 |
| 删掉一行登记（非 HEAD） | 报「完全没登记」 | ✅ 精确命中 |
| 登记一个不在本分支上的 commit | 报「陈旧登记」 | ✅ 精确命中 |
| 台账引用了不存在的 Pitfall ID | 报「悬空引用」 | ✅ 精确命中 |
| Pitfall 编号断号 | 报「编号不连续」 | ✅ 精确命中 |
| `api.md` §一 标题条数造假 | 第 3 层报错（**回归**仍有效） | ✅ 精确命中 |
| 复原后 | 第 3 层通过（阳性对照） | ✅ |

⚠️ **写这套变异脚本时自己踩了一个坑（已修）**：第一版按「**改了哪个文件**」自动选判据
⇒ 「改 `_README.md` 却跑清单判据」⇒ 那个变异**根本没被查**⇒ 判据被误判成「假绿」。
⇒ **「改哪里」与「查哪条」是两件事**，必须**逐用例显式指定**。
⇒ 这正是 `PITFALL-TASK-RPC-32` 说的：**判据也要有反向证据，否则「没报错」分不清是「没问题」还是「没查」**。

⚠️ **为什么要进仓**：AGENTS.md 已立「**跑绿 ≠ 对**」的纪律，而「跑绿」本身也要能被证伪
⇒ 判据必须有一条「**把台账弄坏，它就红**」的反向证据 ⇒ 进仓 = 下一任 agent 一条命令复跑。

#### ⑤ 本轮的**元教训**（已进 Pitfalls 档）

| Pitfall | 一句话 |
|---|---|
| `PITFALL-TASK-RPC-31` | 「集合覆盖」不等于「一一对应」—— **占位符**与**重复登记**会同时逃过「每个 commit 都登记过」这条不变式 |
| `PITFALL-TASK-RPC-32` | 「同一个坑在同一处复发三次」= **靠人记不住**的证据 ⇒ 判据必须机械化，且**机械化判据自己要有反向证据** |

#### ⑥ ⚠️ 本轮新增的**效力边界**（固有，查不出来）

**tip commit 的那一行被删掉 ⇒ 查不出来** —— 因为「提交后未登记」与「提交前登记了 tip」两种状态
**结构上完全对称**（都是「去重哈希 = N−1 + 1 个占位」）⇒ 复核器**无法区分**。
⇒ 这不是判据写漏了，是**信息本身不存在**（那一刻的哈希还没出生）⇒ 只能靠「下一轮补登」收敛。

### 四·十·五、复核时踩到的 4 个坑（**都在脚本自己身上**）

1. ⚠️ **禁模式检查必须剥离注释** —— 否则 `auto-offset-reset`（作为反面教材写在注释里）
   会被误判成「修复被回退」。
2. ⚠️ **`go-queue` 不是本仓自研** —— 它是 `github.com/zeromicro/go-queue@v1.2.2`（go-zero 官方队列库），
   索引错了会把它错配到 goka 的同名文件上 ⇒ 报出假的「行号超出」。
3. ⚠️ **shell 里的 `&` 会拆路径** —— `Sea-RideTheWind_golang` 里的 `&` 不加引号会被 bash 拆成两段
   ⇒ 脚本参数变空 ⇒ 报出「107 条全部找不到」的假警报。
4. ⚠️🆕 **markdown 里两张表可以紧挨着（中间没有空行）** —— 按空行切表会把它们**并成一张**
   ⇒ 后一张表的行混进「commit 清单」⇒ 判据的粒度就错了。
   ⇒ **本轮第 0 层自检当场抓到**（样本里故意让两张表紧挨着）⇒ 切表边界必须靠「**分隔行**」，
   不能靠空行；且只认「表头首列 == commit」的表。
