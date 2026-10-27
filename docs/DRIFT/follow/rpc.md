<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-rpc 组件级语义对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`（`align/follow` 切出点）
> **真源**：`Sea-RideTheWind_golang/service/follow/rpc/`（冻结，只读）
> **被审**：`service/follow/follow-rpc/`
> **对照方式**：Go 侧 21 个源文件逐行读 + **双侧活体对拍**（Go follow-rpc 与 RAG follow-rpc 同跑，共用同一 PG；api 层 52/52 见 api.md）

## 结论

**首轮组件级对照：业务语义层 `SEMANTICALLY EQUIVALENT`（双侧活体对拍 36/36 逐字节一致；第二轮补边界/鉴权 16 条后共 **52/52**）**，
但**发现 3 条硬启动阻塞**（服务在修复前**根本起不来**，而测试全绿）。

| # | 漂移 | 级别 | 状态 |
|---|---|---|---|
| DRIFT-FOLLOW-002 | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修 |
| DRIFT-FOLLOW-003 | `discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修 |
| DRIFT-FOLLOW-004 | `server-addr` 硬编码 `:8848`、忽略 `NACOS_PORT`，与 follow-api 语义冲突 ⇒ api/rpc 连到两台 Nacos | 运行时故障（业务码塌成 1015） | ✅ 已修 |
| DRIFT-FOLLOW-006 | 5 个 `follow_rpc_*` 指标**无任何抓取出口**（Go 有 DevServer `/metrics`，RAG 的 follow-rpc 是纯 gRPC 进程） | **可观察（抓取侧）** | 🔴 **未修**（用户 2026-10-24 定：**本轮不落地**，转「全仓 rpc 层统一方案」交主会话拍板） |

> ⚠️ **三条都发生在「测试全绿」的状态下** —— 修复前 63 条测试 0 失败（现为 68 条），
> 而 `java -jar` 起不来。根因见 [`docs/Pitfalls/follow/rpc.md`](../../Pitfalls/follow/rpc.md)。

## DRIFT-FOLLOW-002 🔴 运行时 classpath 缺 `grpc-inprocess`（启动阻塞）

**现象**

```
java.lang.NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder
  at net.devh.boot.grpc.client.channelfactory.AbstractChannelFactory.newManagedChannel
  ...
Caused by: java.lang.IllegalStateException: Failed to create channel: user-rpc
  at net.devh.boot.grpc.client.inject.GrpcClientBeanPostProcessor.processInjectionPoint
⇒ org.springframework.beans.factory.BeanCreationException: '@GrpcClient constructor injection, parameter index=0'
⇒ Application run failed
```

**根因（Maven「最近者优先」）**

1. `net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE` **自身就传递依赖**
   `io.grpc:grpc-inprocess`（`compile` scope）—— 见其 pom 的 `<dependencies>` 段。
   它带的 `net.devh.boot.grpc.client.channelfactory.InProcessChannelFactory` 类常量池里
   硬引用 `io.grpc.inprocess.InProcessChannelBuilder`。
2. `service/follow/follow-rpc/pom.xml` **又**显式声明了 `grpc-inprocess` ——
   直接声明永远压过传递声明，于是本模块的 `<scope>test</scope>`
   **把 starter 的 compile scope 传递依赖顶掉了**。
3. `grpc-core:1.63.0` **不含** `io/grpc/inprocess/*`（实测 `unzip -l | grep -c` = 0）；
   该类只在 `grpc-inprocess:1.63.0` 里。
   ⇒ 运行时 fat jar 上没有这个类 ⇒ 链接 `InProcessChannelFactory` 失败。

**证据**

```bash
# 修前
mvn -pl service/follow/follow-rpc dependency:tree -Dincludes=io.grpc:grpc-inprocess
  └─ io.grpc:grpc-inprocess:jar:1.63.0:test          ← 运行时没有
# 修后
  └─ io.grpc:grpc-inprocess:jar:1.63.0:compile        ← 运行时有了
```

**修法**：`service/follow/follow-rpc/pom.xml` 去掉该依赖的 `<scope>test</scope>`（回到默认 compile）。
根 pom 的 `dependencyManagement` 不管 `grpc-inprocess`，所以 `<version>${grpc.version}</version>` 保留。

> ⚠️ **同款 scope 缺陷仍在域外**：`article-rpc` / `favorite-rpc` / `like-rpc` / `task-rpc`
> 都把 `grpc-inprocess` 写成 `test` scope。本工作树**不碰域外文件**（并发写会撞）
> ⇒ 登记为**跨域待办**，待主会话派给对应域的工作树：
> 把那 4 个模块的 `grpc-inprocess` 去掉 `<scope>test</scope>`（其余不动）。

## DRIFT-FOLLOW-003 🔴 `discovery:` URI 必须是三斜杠（启动阻塞）

**现象**

```
java.lang.IllegalArgumentException: Incorrectly formatted target uri;
  expected: 'discovery:[//]/<service-name>'; but was 'discovery://user-rpc'
  at net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory.newNameResolver
```

**根因（devh 的判据是 `uri.getPath()`）**

`DiscoveryClientResolverFactory#newNameResolver` 的字节码逻辑：
`path == null || path.length() <= 1 || !path.startsWith("/")` ⇒ 抛异常。

| 写法 | URI 解析 | `getPath()` | 结果 |
|---|---|---|---|
| `discovery:///user-rpc` | authority=`""` | `"/user-rpc"` | ✅ |
| `discovery://user-rpc` | authority=`"user-rpc"` | `""`（长度 0） | ❌ |

⇒ 两斜杠写法把服务名解析成了 **authority**，path 留空。

**修法**：`service/follow/follow-rpc/src/main/resources/application.yml:123`
`discovery://user-rpc` → `discovery:///user-rpc`。

> ⚠️ **同款缺陷仍在域外**：`article-rpc` / `comment-rpc` / `favorite-rpc` / `like-rpc` /
> `message-rpc` 的 grpc client 地址**全是两斜杠**；而**全部 api 模块都是三斜杠**。
> ⇒ 这是「rpc 层单方面的写法偏差」，本域已修，域外 5 个模块待修。

### 🛡️ 防回归网：`FollowRpcProductionAddressTest`（2026-10-24 补录，commit `d50750e`）

> ⚠️ 2026-10-24 复核发现：这条**守门测试此前没进台账** ⇒ 「已修」缺证据链 ⇒ 现补录。

`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcProductionAddressTest.java`
（**3 条用例** + helper 内 1 条交叉核对断言）守的是 **DRIFT-FOLLOW-003 这类「编译期看不出、测试期测不到、但能让服务起不来」**的缺陷：

| 用例 | 断言 |
|---|---|
| `productionAddressUsesThreeSlashes` | **生产 `application.yml`**（不是 test 资源）里的 grpc client 地址 == `discovery:///user-rpc`（三斜杠） |
| `devhNameResolverAcceptsProductionAddressAndRejectsTwoSlash` | 把该地址喂给 **devh 真正的消费点**（`DiscoveryClientResolverFactory#newNameResolver`）⇒ **不抛**；换成两斜杠 ⇒ **必须抛** `IllegalArgumentException` 且消息含 `Incorrectly formatted target uri` |
| `productionAddressResolvesToExpectedServiceName` | 该地址解析出的服务名 == `user-rpc`（与 `NacosRegistration` 的注册名一致） |
| （helper `productionAddress()` 内的交叉核对） | `target/classes` 里**打包的副本**与源码树里的地址一致 ⇒ 防止「上次构建产物是旧的，测试结论不可信」 |

> 🔑 **为什么必须是「真消费点」**：AGENTS.md §3.3「不得凭注解/配置名称断定机制已启用」。
> 光断言字符串等于 `"discovery:///user-rpc"` 只能证明**代码写成了这样**；
> **调用 devh 的解析器并断言两斜杠被拒**，才证明**这条地址在运行时真的能被消费**。
> ⇒ 这是 §3.3「两侧同时跑的探针」的单侧最小形态。

## DRIFT-FOLLOW-004 🔴 `NACOS_ADDR` 在 api / rpc 两侧语义不一致

**现象**：修完 002/003 后，follow-api 报
`DiscoveryClientNameResolver: No servers found for follow-rpc`，
而 follow-rpc 自己日志是 `NacosRegistration registered: service=follow-rpc ...`。

**根因**：同域两个模块对**同一个环境变量名**给了**两种语义**：

| 模块 | 原写法 | 语义 |
|---|---|---|
| `follow-api` | `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` | `NACOS_ADDR`=host，`NACOS_PORT`=port |
| `follow-rpc` | `${NACOS_ADDR:nacos:8848}` | `NACOS_ADDR`=**host:port**，`NACOS_PORT` 被忽略 |

⇒ 任何按 api 侧语义部署的环境（`NACOS_ADDR=host` + `NACOS_PORT=port`）下，
**rpc 连的是 `host:8848`、api 连的是 `host:port`** ⇒ 两个组件连**两台 Nacos**
⇒ rpc 注册在 A、api 在 B 找它 ⇒ 恒 "No servers found" ⇒ 所有业务码塌成 1015。

**修法**：`follow-rpc/src/main/resources/application.yml:95` 改为
`${NACOS_ADDR:nacos}:${NACOS_PORT:8848}`，与 follow-api 同一套变量语义。

> ⚠️ 这个缺陷**两个 yaml 各自看都"对"**，只有放在一起才冲突 —— 属于 §3.3「配置里没写 ≠ 没开」的变体。

## 业务语义层：零漂移（逐条对照 + 活体对拍）

| 维度 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| 8 个 RPC 方法与返回类型 | `rpc/internal/server/followserviceserver.go:26-64` | `service/impl/FollowServiceGrpcImpl.java:65-103` | ✅ 一致 |
| 校验：user/target ≤ 0 ⇒ 5003；self ⇒ 5201 | `rpc/internal/logic/helper.go:27-35` | `service/FollowRpcSupport.java:137-146` | ✅ 一致 |
| 列表归一化三段序（offset→limit→截断） | `helper.go:37-56` | `FollowRpcSupport.java:182-202` | ✅ 一致 |
| `ensureUserExists` 三分支（5101/5001/5101） | `helper.go:81-94` | `service/FollowUserExistenceService.java:70-102` | ✅ 一致 |
| Unfollow/Unblock **不**校验存在性 | `unfollowlogic.go:31-51` / `unblocklogic.go:31-51` | `FollowDomainService.java:163-264` | ✅ 一致 |
| 只有 Follow 做拉黑检查（5202） | `followlogic.go:48-58` | `FollowDomainService.java:135-142` | ✅ 一致 |
| Block = 插 block + **双向**删 follow，单事务 | `model/follow.go:33-54` | `service/FollowBlockTransactionService.java:70-77` | ✅ 一致（独立 Bean，避开 `@Transactional` self-invocation 静默失效） |
| LIMIT/OFFSET **条件拼接**（`offset>0` / `limit>0` 才出现） | `model/follow.go:79-87` 等 6 处 | `mapper/GoStyleLimitOffset.java:69-82` | ✅ 一致 |
| 三个列表的过滤列/投影列 | `follow.go:77-144` | `mapper/FollowRelationMapper.java` / `BlockRelationMapper.java` | ✅ 一致（follower 列表是 `WHERE target_id` 取 `user_id`） |
| 推荐算法 6 条硬语义（提前返回/三重过滤/expanded 防环/末层不展开/升序/offset 越界返空） | `getrecommendationslogic.go:62-182` | `service/FollowRecommendationService.java:100-197` | ✅ 一致 |
| 指标口径（4 写方法 2 指标 / 4 读方法 1 指标） | 8 个 logic 的 `defer` | `FollowDomainService.java` 各方法 finally | ✅ 一致 |

### DDL：与 GORM 产物**逐字节一致**（三重确认）

**方法**：把 Go 的 `model/data_model.go` 两个 struct **逐字复制**到独立临时 module，
调 `db.AutoMigrate(&FollowRelation{}, &BlockRelation{})`（与 `model/db.go:42` 同款调用）
生成 **GORM 真实产物**；再让 RAG follow-rpc 的 Flyway 脚本在**同一个 PG** 上建表；
然后逐行 dump `information_schema.columns` + `pg_indexes` 对比。

| 检查项 | 结果 |
|---|---|
| 列类型（`bigint` / `bigint` / `bigint` / `timestamptz` ×2） | ✅ 一致 |
| `NOT NULL`（user_id / target_id）、可空（create_time / update_time） | ✅ 一致 |
| `id` 默认值 `nextval('..._id_seq')`（GORM 整数 PK ⇒ 序列） | ✅ 一致 |
| 索引：`{table}_pkey` / `idx_{table}_user_id` / `idx_{table}_target_id` / `uk_follow_pair` / `uk_block_pair` | ✅ 名字与定义**逐字一致** |
| **反向验证**：让 Go 的 `AutoMigrate` 再跑一遍 RAG 建的表 | ✅ **零改动**（双向兼容） |

> ⚠️ AGENTS.md §0 记的「索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED」
> —— **follow 域这一项本轮已补齐**，方法即上表。

### Flyway 账本隔离（§6.3 问题 4）

`follow-rpc/src/main/resources/application.yml:79`
`spring.flyway.table: flyway_schema_history_follow` ✅ 已隔离。
真启动日志实测：`Creating Schema History table "public"."flyway_schema_history_follow"`，
`first_db` 里最终只有 `block_relation` / `follow_relation` / `flyway_schema_history_follow` 三张表
⇒ 与 `article-rpc`（默认账本）**不撞号**。

## DRIFT-FOLLOW-006 🔴 follow-rpc 的 5 个指标**无任何抓取出口**

**现象（U1：抓取实测）**

| | 抓取地址 | 结果 |
|---|---|---|
| **Go** | `http://<host>:16065/metrics` | ✅ 127 行 `follow_rpc_*` series（5 个指标全部可抓） |
| **RAG** | —— | ❌ **进程只有 1 个 LISTEN socket**（18282，gRPC）⇒ **没有任何 HTTP 出口** |

**Go 侧的出口从哪来（逐行）**

- `service/follow/rpc/etc/follow.yaml:20-27`：`DevServer.Enabled: true` / `Port: 16065` / `MetricsPath: /metrics`
- `go-zero@v1.10.2/zrpc/server.go:63`：`c.SetUp()`
- ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:77`：`devserver.StartAgent(sc.DevServer)`
- ⇒ `go-zero@v1.10.2/internal/devserver/server.go:79-87`：起一个独立 HTTP server，`:45-47`
  把 `promhttp.Handler()` 挂在 `MetricsPath` 上。

⇒ **Go 的 follow-rpc 是「纯 gRPC 进程 + 一个独立的小 HTTP 观测端口」**。

**RAG 侧为什么没有**

`follow-rpc` 是 `WebApplicationType.NONE` 的纯 gRPC 进程（classpath 上只有
`spring-boot-web-server` 这个**抽象**，没有 servlet 容器）⇒
`spring-boot-starter-actuator` 的 **web endpoint 没有载体**：
`management.endpoints.web.exposure.include: prometheus` 这行配置**不产生任何效果**。

**实测证据**

```
$ lsof -nP -a -p <rag-follow-rpc-pid> -iTCP -sTCP:LISTEN
java    88963   pr  55u  IPv6 ...  TCP *:18282 (LISTEN)      ← 只有 gRPC 端口
$ curl http://127.0.0.1:18282/actuator/prometheus
curl: (1) Received HTTP/0.9 when not allowed                 ← 那不是 HTTP
```

⇒ **5 个指标（`request_total` / `request_duration_seconds` / `relation_action_total` /
`db_error_total` / `recommendation_size`）在 RAG 侧一个都抓不到** ——
其中 `recommendation_size` 是**唯一**能看出「本次推荐返回了几条」的指标。

⚠️ **不是「指标没上报」**：RAG 侧的 meter **确实在注册、确实在累加**
（`FollowRpcMetrics#currentRecommendationSize()` 读回值，
`FollowRecommendationServiceTest` 断言了 3 个上报点）——
**缺的是「出口」，不是「数据」**。

**修法选项（**2026-10-24 用户定：本轮不落地，转全仓 rpc 层统一方案交主会话拍板**）**

| 方案 | 做法 | 代价 |
|---|---|---|
| A. 仿 Go 的 DevServer | 在 `follow-rpc` 起一个独立的**小 HTTP server**（如 `com.sun.net.httpserver.HttpServer`），只挂 `/metrics`（+ `/healthz`），端口与 Go 一样用 16065 | 要新写代码 + 开新端口；但**不改变进程形态**、不引 servlet 容器 ⇒ 最贴近 Go |
| B. Spring Boot 管理端口 | 加 `spring-boot-starter-web`，`management.server.port=16065` | 进程变成 servlet web 应用 ⇒ **部署形态变化**（还要决定主应用监听哪个端口） |
| C. Pushgateway | 改成主动推送 | **不是 scrape 语义** ⇒ 与 Go 不等价 ⇒ 不推荐 |

### ⚠️ 全仓调查结论（2026-10-24，DRIFT-006 的判定依据）

**「哪个服务用了 B / C」这个问题，答案不是「有」，而是「**只有 api 层用了 B，rpc 层一个都没有**」。**

| | `spring-boot-starter-web` | 进程形态 | `/actuator/prometheus` 实际出口 |
|---|---|---|---|
| **8 个 api 模块** | **8/8 有** | servlet 应用 | ✅ **全部走 B**（actuator 挂主端口）—— B 是 api 层的**既定形态** |
| **8 个 rpc 模块** | **0/8 有**（只有 `spring-boot-web-server` 抽象，供 nacos-discovery 反射引用） | 纯 gRPC 进程 | ❌ **零出口**（actuator 的 web endpoint 没有载体） |

- ⚠️ **全仓无 Pushgateway** ⇒ **C 不是既有形态**，无需考虑。
- `hot-rpc` 写得最直白：`management.server.port: -1` + `management.endpoints.prometheus.enabled: false`，
  注释自述「纯 gRPC 进程没有 web 容器 ⇒ `/actuator/prometheus` 没有暴露路径，
  **将来若要 scrape，需引入 spring-boot-starter-web 或独立 sidecar**」
  ⇒ 仓库里**两个选项都已被承认**，但都还没做。
- ⚠️ **「有 starter-web 就能起容器」是错的**：`comment/favorite/like/message/task-rpc` 的 pom 里
  只有 `spring-boot-web-server`（抽象），`message-rpc` 的 pom 注释甚至明写
  「**message-rpc 不引 spring-boot-starter-web**」⇒ 起步**没有 servlet 容器**。

### ⚠️ Go 侧也不是所有 rpc 都有 DevServer（这一条决定了「谁才算漂移」）

| Go 侧 rpc | DevServer | 端口 | RAG 侧现状 | 判定 |
|---|---|---|---|---|
| `follow/rpc` | ✅ | **16065** | ❌ 零出口 | 🔴 **漂移（本条）** |
| `favorite/rpc` | ✅ | 16062 | ❌ 零出口 | 🔴 同款缺口（**其它域**） |
| `message/rpc` | ✅ | 16067 | ❌ 零出口 | 🔴 同款缺口（**其它域**） |
| `like/rpc` | ✅ | 9098 | ❌ 零出口 | 🔴 同款缺口（**其它域**） |
| `user/user/rpc` | ✅ | 9094 | ❌ 零出口 | 🔴 同款缺口（**其它域**） |
| `user/admin/rpc` | ✅ | 9097 | ❌ 零出口 | 🔴 同款缺口（**其它域**） |
| `article/rpc`、`comment/rpc`、`hot/rpc`、`task/rpc` | ❌ **无 DevServer 段** | — | ❌ 零出口 | ⚠️ **不是漂移**（Go 侧同样抓不到） |

⇒ **「Go 有、RAG 无」一共 6 处**，follow 是其中之一；
⇒ **另外 4 个 rpc（article/comment/hot/task）在 Go 侧也没有指标出口** ⇒ RAG 侧同样没有 **不构成漂移**
（⚠️ 但 RAG 侧仍在 Micrometer 里注册了 meter —— 那是「有数据无出口」，与 follow 一样，见下）。

### ⚠️ 为什么「A 才是与 Go 形态等价的」

Go 的 DevServer **本身就是方案 A**（`go-zero@v1.10.2/internal/devserver/server.go:67-76`）：

```go
threading.GoSafe(func() {
    addr := fmt.Sprintf("%s:%d", s.config.Host, s.config.Port)
    logx.Infof("Starting dev http server at %s", addr)
    if err := http.ListenAndServe(addr, s.server); err != nil { logx.Error(err) }
})
```

⇒ 就是一个 **`http.ListenAndServe` + `http.ServeMux` 的独立小 HTTP server**，
只挂 `/`（路由清单）、`HealthPath`、`MetricsPath`、`/debug/pprof/*`（`server.go:35-59`），
**没有 servlet 容器、没有业务路由、不参与主进程的服务注册**。

⇒ 所以：
- **A** = 与 Go 形态**等价**（独立小 HTTP server、端口 16065 逐字对齐）；
- **B** = 往纯 gRPC 进程里塞一个 **Go 侧不存在的 Tomcat** ⇒ 形态反而**不等价**；
- B 的真正价值是「与 api 层的既定形态一致」—— 但 api 层是 servlet 应用，
  **它与 rpc 层不可比**（api 的主端口本来就要监听，rpc 的主端口是 gRPC）。

### 结论与派单

⇒ **本轮不落地**（用户 2026-10-24 定）：DRIFT-006 留在台账，
转为「**全仓 rpc 层指标出口的统一方案**」交主会话拍板 ——
因为这已经是**跨 10 个 rpc 模块的部署形态决策**，不该由 follow 域单方面落地。

⇒ **跨域待办（交主会话派单）**：下面 5 个 rpc 模块的「Go 有 DevServer、RAG 零出口」同款缺口，
修法与 follow 域一致（先定 A/B，再统一铺开）：

| # | 组件 | Go 的 DevServer 端口 | 归属域 |
|---|---|---|---|
| 1 | `favorite-rpc` | 16062 | favorite |
| 2 | `message-rpc` | 16067 | message |
| 3 | `like-rpc` | 9098 | like |
| 4 | `user-user-rpc` | 9094 | users |
| 5 | `user-admin-rpc` | 9097 | users |

⚠️ **注意端口冲突**：`like-rpc` / `message-rpc` / `favorite-rpc` / `user-*-rpc` 目前的
`grpc.server.port` 有**多个都写着 8082**（`favorite-rpc` / `like-rpc` / `message-rpc` / `follow-rpc`）
⇒ 统一方案必须**一并把 DevServer 端口纳入端口规划**，否则会撞。

⚠️ 另外：`article-rpc` / `comment-rpc` / `hot-rpc` / `task-rpc` 在 Go 侧**没有** DevServer
⇒ 若统一方案选择「所有 rpc 都给出口」，这 4 个属于**新增可观测面**（Go 侧没有），
应单独拍板，不要和「补齐 Go 已有出口」混为一谈。

**顺带修掉的一处**：`FollowRpcMetrics` 的直方图原来用 `publishPercentiles()`
（**分位数**开关，Prometheus 文本格式**不支持 quantile** ⇒ 对抓取端是无效配置）
⇒ 已改成 `publishPercentileHistogram()`，**这样等出口补上时格式就已经是对的**
（详见 `docs/DRIFT/follow/api.md` 的 DRIFT-FOLLOW-007）。

> **🧾 第二轮复核（2026-10-24 · 最全性）**：补录「逐文件对照索引」——
> 此前「逐条对照」这条断言**无法从台账里核对**
> （`FollowRpcApplication`、`UserRpcClient`、`FollowRpcProperties`、
> `MybatisPlusOrderingBridgeAutoConfiguration`、两个 Entity、4 个 `errmsg` 类
> 共 **10 个类从未出现过**）⇒ 现每个源文件都有落点；
> **实测覆盖率：Java 侧 21/21、Go 侧 22/22**（含 goctl 生成物与 Go 侧死代码的说明）。
> **🧾 台账 ↔ 代码复核（2026-10-24）**：本文件的每条状态断言已逐条回代码/真源核对 ——
> 「✅ 已修」3 条（002/003/004）**逐条在代码里坐实**；
> 「🔴 未修」1 条（006，follow-rpc 指标无出口）**仍然成立**；
> 另：api.md 的 DRIFT-FOLLOW-008（MaxConns 仍是 `service/common` 里的全局 Semaphore）经复核**仍成立**；
> 全部 `file:line` 引用**逐条命中**；
> 复核中发现 4 处表述偏差已当场订正（对拍条数、Go 源文件数、测试条数、探针清单）。

## 逐文件对照索引（21 个 Java ↔ 22 个 Go，**全量无遗漏**）

> ⚠️ 2026-10-24 复核补录：同 api.md，「逐条对照」这条断言此前**无法从台账里核对**
> （`FollowRpcApplication`、`UserRpcClient`、`FollowRpcProperties`、
> `MybatisPlusOrderingBridgeAutoConfiguration`、两个 Entity、4 个 `errmsg` 类
> 共 **10 个类从未出现过**）⇒ 现补全索引。

| # | RAG 源文件（follow-rpc 17 + follow-common 4） | Go 对应物（follow/rpc 21 + follow/common 1） | 承重点 | 结论 |
|---|---|---|---|---|
| 1 | `rpc/FollowRpcApplication.java` | `follow.go`（`main()`） | Go main 的五步逐条对应（**无 ticker/cron**） | ✅ |
| 2 | `rpc/bootstrap/NacosRegistration.java` | `follow.go` → `zrpc.MustNewServer` → `go-zero@v1.10.2/zrpc/server.go:63 SetUp()` | 注册名 `follow-rpc`；Go 侧由 zrpc 自动注册 | ✅ |
| 3 | `rpc/client/UserRpcClient.java` | `internal/svc/servicecontext.go` 的 `UserRpc` + `internal/logic/helper.go:81-94` | `GetUser` 的三分支判定 | ✅ |
| 4 | `rpc/config/FollowRpcProperties.java` | `internal/config/config.go`（struct tag 默认值） | `ListConf` / `RecommendConf`；**`default=20` 与 `default=50` 是两个独立常量** | ✅ |
| 5 | `rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **无 Go 对应物** | ⚠️ **表达层**：刻意为空，只建立自动配置排序边（`after=DataSource`、`before=MybatisPlus`），**不注册任何 bean** ⇒ 无行为影响 | ✅ |
| 6 | `rpc/domain/FollowRelationEntity.java` | `internal/model/data_model.go` `FollowRelation` | `@TableName("follow_relation")`；主键 / `autoCreateTime` 的差异已在 DDL 节说明 | ✅ |
| 7 | `rpc/domain/BlockRelationEntity.java` | `internal/model/data_model.go` `BlockRelation` | **两张表刻意不复用同一个 Entity**（Go 侧也是两个 struct + 两个 `TableName()`） | ✅ |
| 8 | `rpc/mapper/FollowRelationMapper.java` | `internal/model/follow.go` 的 follow 查询 | 过滤列 / 投影列 | ✅ |
| 9 | `rpc/mapper/BlockRelationMapper.java` | `internal/model/follow.go` 的 block 查询 | 过滤列 / 投影列 | ✅ |
| 10 | `rpc/mapper/GoStyleLimitOffset.java` | `internal/model/follow.go:79-87` 等 **6 处** | **条件拼接**：`offset>0` / `limit>0` 才出现 LIMIT/OFFSET | ✅ |
| 11 | `rpc/metrics/FollowRpcMetrics.java` | `internal/metrics/metrics.go` | 4 写方法 2 指标 / 4 读方法 1 指标；**直方图桶**（DRIFT-007） | ✅ |
| 12 | `rpc/service/FollowBlockTransactionService.java` | `internal/model/follow.go:33-54` `CreateBlockAndCleanup` | 插 block + 双向删 follow **单事务**（独立 Bean，避开 `@Transactional` self-invocation 静默失效） | ✅ |
| 13 | `rpc/service/FollowDomainService.java` | `internal/logic/{follow,unfollow,block,unblock}logic.go` | 校验分支、存在性校验、动作方法 | ✅ |
| 14 | `rpc/service/FollowRecommendationService.java` | `internal/logic/getrecommendationslogic.go` | 推荐算法 6 条硬语义（提前返回/三重过滤/expanded 防环/末层不展开/升序/offset 越界返空） | ✅ |
| 15 | `rpc/service/FollowRpcSupport.java` | `internal/logic/helper.go` | 校验（5003/5201）；列表归一化三段序（offset→limit→截断） | ✅ |
| 16 | `rpc/service/FollowUserExistenceService.java` | `internal/logic/helper.go:81-94` `ensureUserExists` | 三分支（5101/5001/5101） | ✅ |
| 17 | `rpc/service/impl/FollowServiceGrpcImpl.java` | `internal/server/followserviceserver.go:26-64` | 8 个 RPC 方法与返回类型逐条一致 | ✅ |
| 18 | `common/errmsg/FollowErrorCode.java` | `service/follow/common/errmsg.go` | 错误码常量表 + 顺序 | ✅ |
| 19 | `common/errmsg/FollowErrorMessages.java` | 同上 | 文案表 + 顺序 | ✅ |
| 20 | `common/errmsg/FollowCodeError.java` | 同上 | Go `CodeError` 的承载（code + msg） | ✅ |
| 21 | `common/errmsg/FollowGrpcError.java` | 同上 + go-zero `zrpc/internal/codes` | gRPC 码 ↔ 业务码的**三段转换** | ✅ |

**Go 侧其余 5 个文件的落点**（未单列进上表，因为它们不是「一对一对应某个 Java 类」）：

| Go 文件 | 落点 | 说明 |
|---|---|---|
| `followservice/followservice.go` | [`proto.md`](./proto.md) ＋ api 侧的 `client/FollowRpcClient.java`（**见 [`api.md` 行 #6](./api.md)，不是本表的 #6**） | **goctl 生成的 client 包装层**（`FollowService` interface + `NewFollowService`）⇒ 对应 RAG 的 `follow-proto` 生成 stub + api 侧 `FollowRpcClient`；**生成物，非手写语义**。⚠️ 它虽然**物理位置**在 `follow/rpc/` 目录下，但**语义上是给 api 侧调 rpc 用的客户端**，不是 rpc 服务端的一部分 ⇒ 不算 rpc 模块的对应物 |
| `internal/model/db.go` | `follow-rpc/src/main/resources/application.yml`（datasource 段）＋ `db/migration/V1,V2` | **建库与连接参数**（此前只在本文件 DDL 段被顺带提及，**未进索引** ⇒ 2026-10-24 复核补录）：DSN 的 `sslmode=`（← Go 的 `Mode` 字段）＋ **硬编码 `TimeZone=Asia/Shanghai`**（会话时区，影响 `timestamptz` 读出值）；`SetMaxIdleConns(10)` / `SetMaxOpenConns(100)` ⇒ RAG 侧 `hikari.minimum-idle:10` / `maximum-pool-size:100`；`AutoMigrate(&FollowRelation{}, &BlockRelation{})` ⇒ RAG 侧 **Flyway `V1/V2` + 独立账本 `flyway_schema_history_follow`** |
| `internal/model/vars.go` | **无对应物（有意不复制）** | Go 侧一个**无消费者的死变量** `ErrorNotFound`（`errors.New("record not found")`）⇒ RAG 侧不复制死代码 |
| `pb/follow.pb.go` / `pb/follow_grpc.pb.go` | [`proto.md`](./proto.md) | **protoc 生成物**；行为层的对照在 `.proto` 源文件层完成（逐行 diff 0 差异） |

> ⚠️ **2026-10-24 复核订正**：本表此前**误把 Go `api/internal/handler/follow/` 下 8 个 handler 算进 rpc 侧**，
> 且引用的「表 #5 `controller/FollowController.java`」是 **`api.md` 的行号、本表无此行** ⇒ 跨文档串行号。
> 根因：8 个 handler 物理上属于 **Go `follow/api`**（HTTP 层），**已在 [`api.md` 行 #5](./api.md) 单独列出**。
> ⇒ 本表改为只收 rpc 侧真实剩余的 5 个文件（`followservice.go` / `db.go` / `vars.go` / `pb`×2），
> 账目因此对上：**行 #1–#21 覆盖 17 个 Go 文件**（16 个 rpc 侧 + `common/errmsg.go`）**+ 本表 5 个 = 22
> = Go `follow/rpc` 21 + `follow/common` 1**（`find` 实测，非估算）。

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（**22 个 Go 文件 ↔ 21 个 Java 文件**） | ✅ 双向静态（**逐文件索引见上节，可 grep 自证**） |
| 真启动（`java -jar` 真 PG + 真 Nacos） | ✅ **真启动**，非 test classpath |
| DDL vs GORM 产物 | ✅ **双侧真产物逐行对比** |
| gRPC 行为 | ✅ **双侧活体对拍**（Go follow-rpc 与 RAG follow-rpc 同跑，52/52 逐字节一致） |
| 指标口径 | ⚠️ **无法对拍**（Go 侧抓到 127 行 `follow_rpc_*`；**RAG 侧零抓取出口** ⇒ 取不到值可比）⇒ 已登记 **DRIFT-FOLLOW-006** |

## 对拍环境（可复现）

| 组件 | Go 侧 | RAG 侧 |
|---|---|---|
| api | `:18893`（`/tmp/go-follow-api`，自有 yaml） | `:18895` |
| rpc | `:18092`（`/tmp/go-follow-rpc`） | `:18082` |
| PG | 同一个 `127.0.0.1:55450/first_db`（两边**共用**，⇒ 数据行级可比） | 同左 |
| 下游 user-rpc | 隔离 stub（`/tmp/userstub`，实现 Go 真源 `user.UserService/GetUser`，恒 `found=true`） | 同左（同一进程，两边都调它） |
| 服务发现 | etcd `127.0.0.1:49319` | Nacos `127.0.0.1:8848` |

探针脚本：`/tmp/probe.sh`（协议层 20 条）+ `/tmp/probe_data.sh`（数据路径 16 条）
+ `/tmp/probe_ext.sh`（边界/鉴权 16 条）⇒ **合计 52/52**。

> ⚠️ Go 侧二进制用 `CGO_ENABLED=0 go build -o /tmp/...` 构建（**不写入冻结仓库**），
> 配置用 `/tmp/gofollow/*.yaml` 覆盖（Go 的 `-f` 参数支持自定义配置文件路径）。

## 待办

1. **域外**：4 个 rpc 模块的 `grpc-inprocess` test scope、5 个 rpc 模块的 `discovery://` 两斜杠
   ⇒ 建议由各域工作树分别修，或由主会话统一修。
2. **共享模块**：`service/common` 的 `ObservabilityLifecycle` destroy 路径引用
   `jakarta.servlet.Filter`，而 follow-rpc 运行时 classpath **无 servlet-api**
   ⇒ 进程关闭时抛 `NoClassDefFoundError`（不影响启动，但会污染关闭日志）
   ⇒ 属 `service/common` 职责，**未在本工作树改动**。
3. ~~**指标数值**未做双侧对拍~~ ⇒ ✅ **已做**（2026-10-24，U1）：
   Go 侧抓到 127 行 `follow_rpc_*`；RAG 侧**零出口** ⇒ 登记为 **DRIFT-FOLLOW-006**。
4. **DRIFT-FOLLOW-006 的修法已转为「全仓 rpc 层统一方案」交主会话拍板**（用户 2026-10-24 定）。
   ⇒ 本工作树**不落地代码**；调查结论（rpc 层 0/8 有出口、B 只在 api 层是既定形态、C 无先例、
   Go 侧只有 6 个 rpc 有 DevServer、Go 的 DevServer 本身即方案 A）见该条目。
5. **跨域派单（5 处同款缺口）**：`favorite-rpc`(16062) / `message-rpc`(16067) / `like-rpc`(9098) /
   `user-user-rpc`(9094) / `user-admin-rpc`(9097) —— Go 有 DevServer、RAG 零出口。
   ⇒ 连同端口规划一起交主会话（本域不得改域外文件）。
6. 🔴 **共享索引已过时 ⇒ 交主会话更新（本工作树不得改共享文件）** ——
   `AGENTS.md:32` 的域表仍写「`comment`/`favorite`/`follow`/`task` **14 个组件全部零台账**」，
   `docs/DRIFT/_INDEX.md:32` 仍写 follow「**仅 proto 层**」「14 个组件零台账、对照尚未开始」，
   `docs/DRIFT/_INDEX.md:85` 仍把 follow 算作「仅 proto 层」。
   ⇒ **与事实不符**（2026-10-24 实测：follow 域 4 个组件全部完成组件级对照，
   9 条 DRIFT + 15 条 PITFALL + 逐文件索引）⇒ 需主会话在合并时统一刷新这三处。
   ⚠️ `tools/drift-move.py verify` 另报 4 处缺失，**全在其它域**
   （`like/rpc.md`、`message/rpc.md`、`message/api.md`、`_cross/fix-round-2026-10-23.md`）
   ⇒ **follow 域 4 份 DRIFT 台账本身无缺失**，域外那 4 处同样交主会话。
7. **新增全仓门禁 + 本域守门测试**（2026-10-24，commit `d50750e`）：
   - 门禁脚本 `tools/difftest/check-grpc-runtime-classpath.py` ——
     守住「`grpc-inprocess` 不得是 test scope」与「`discovery:` 必须三斜杠」两类
     **编译期看不出、测试期测不到、但能让服务起不来**的缺陷；
     域外已知的 13 处登记在 `tools/difftest/baselines/grpc-runtime-classpath.txt`。
   - 守门测试 `FollowRpcProductionAddressTest` ——
     把生产地址喂给 **devh 真正的消费点**并断言两斜杠写法被拒（详见 DRIFT-003 节）。
     ⇒ **门禁脚本管「源码写法」，守门测试管「运行时能不能被消费」**，两者互补、缺一不可。
     ✅ **2026-10-24 复跑**：`Tests run: 3, Failures: 0, Errors: 0`（`BUILD SUCCESS`）。
