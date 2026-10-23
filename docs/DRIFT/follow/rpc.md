<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-rpc 组件级语义对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`（`align/follow` 切出点）
> **真源**：`Sea-RideTheWind_golang/service/follow/rpc/`（冻结，只读）
> **被审**：`service/follow/follow-rpc/`
> **对照方式**：Go 侧 19 个源文件逐行读 + **双侧活体对拍**（Go follow-rpc 与 RAG follow-rpc 同跑，共用同一 PG）

## 结论

**首轮组件级对照：业务语义层 `SEMANTICALLY EQUIVALENT`（双侧活体对拍 36/36 逐字节一致）**，
但**发现 3 条硬启动阻塞**（服务在修复前**根本起不来**，而测试全绿）。

| # | 漂移 | 级别 | 状态 |
|---|---|---|---|
| DRIFT-FOLLOW-002 | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修 |
| DRIFT-FOLLOW-003 | `discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修 |
| DRIFT-FOLLOW-004 | `server-addr` 硬编码 `:8848`、忽略 `NACOS_PORT`，与 follow-api 语义冲突 ⇒ api/rpc 连到两台 Nacos | 运行时故障（业务码塌成 1015） | ✅ 已修 |

> ⚠️ **三条都发生在「测试全绿」的状态下** —— 63 条测试 0 失败，
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

**修法**：`service/follow/follow-rpc/src/main/resources/application.yml:81`
`discovery://user-rpc` → `discovery:///user-rpc`。

> ⚠️ **同款缺陷仍在域外**：`article-rpc` / `comment-rpc` / `favorite-rpc` / `like-rpc` /
> `message-rpc` 的 grpc client 地址**全是两斜杠**；而**全部 api 模块都是三斜杠**。
> ⇒ 这是「rpc 层单方面的写法偏差」，本域已修，域外 5 个模块待修。

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

**修法**：`follow-rpc/src/main/resources/application.yml:65` 改为
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

`follow-rpc/src/main/resources/application.yml:59`
`spring.flyway.table: flyway_schema_history_follow` ✅ 已隔离。
真启动日志实测：`Creating Schema History table "public"."flyway_schema_history_follow"`，
`first_db` 里最终只有 `block_relation` / `follow_relation` / `flyway_schema_history_follow` 三张表
⇒ 与 `article-rpc`（默认账本）**不撞号**。

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（19 个 Go 文件 ↔ 16 个 Java 文件） | ✅ 双向静态 |
| 真启动（`java -jar` 真 PG + 真 Nacos） | ✅ **真启动**，非 test classpath |
| DDL vs GORM 产物 | ✅ **双侧真产物逐行对比** |
| gRPC 行为 | ✅ **双侧活体对拍**（Go follow-rpc 与 RAG follow-rpc 同跑，36/36 逐字节一致） |
| 指标口径 | ⚠️ **未对拍**（Prometheus 指标名/label 已逐条对照，数值未双侧比） |

## 对拍环境（可复现）

| 组件 | Go 侧 | RAG 侧 |
|---|---|---|
| api | `:18893`（`/tmp/go-follow-api`，自有 yaml） | `:18895` |
| rpc | `:18092`（`/tmp/go-follow-rpc`） | `:18082` |
| PG | 同一个 `127.0.0.1:55450/first_db`（两边**共用**，⇒ 数据行级可比） | 同左 |
| 下游 user-rpc | 隔离 stub（`/tmp/userstub`，实现 Go 真源 `user.UserService/GetUser`，恒 `found=true`） | 同左（同一进程，两边都调它） |
| 服务发现 | etcd `127.0.0.1:49319` | Nacos `127.0.0.1:8848` |

探针脚本：`/tmp/probe.sh`（协议层 20 条）+ `/tmp/probe_data.sh`（数据路径 16 条）。

> ⚠️ Go 侧二进制用 `CGO_ENABLED=0 go build -o /tmp/...` 构建（**不写入冻结仓库**），
> 配置用 `/tmp/gofollow/*.yaml` 覆盖（Go 的 `-f` 参数支持自定义配置文件路径）。

## 待办

1. **域外**：4 个 rpc 模块的 `grpc-inprocess` test scope、5 个 rpc 模块的 `discovery://` 两斜杠
   ⇒ 建议由各域工作树分别修，或由主会话统一修。
2. **共享模块**：`service/common` 的 `ObservabilityLifecycle` destroy 路径引用
   `jakarta.servlet.Filter`，而 follow-rpc 运行时 classpath **无 servlet-api**
   ⇒ 进程关闭时抛 `NoClassDefFoundError`（不影响启动，但会污染关闭日志）
   ⇒ 属 `service/common` 职责，**未在本工作树改动**。
3. **指标数值**未做双侧对拍（Prometheus 抓取比对）。
