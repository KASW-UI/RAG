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
| DRIFT-FOLLOW-002 | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修<br>⚠️ **2026-10-30 复核（follow+task lane · 补指针）**：RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）⇒ **现行态 = ✅ 已修（不变）**；⚠️ **跨域那半边**（域外 4 模块仍是 `test` scope）**已于 2026-10-29 订正**，本轮逐模块复核**属实**（全仓已无 `test` scope）；核对日期 2026-10-30 |
| DRIFT-FOLLOW-003 | `discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败 | **启动阻塞（硬失败）** | ✅ 已修<br>⚠️ **2026-10-30 复核（follow+task lane · 补指针 + 子结论订正）**：RAG `service/follow/follow-rpc/src/main/resources/application.yml:163` = `address: discovery:///user-rpc`（**三斜杠**）⇒ **现行态 = ✅ 已修（不变）**；⚠️ **「域外 5 个模块仍是两斜杠 ⇒ 待修」已陈旧**（本轮逐模块实测：全仓已无两斜杠）⇒ **该跨域待办已销账**；核对日期 2026-10-30 |
| DRIFT-FOLLOW-004 | `server-addr` 硬编码 `:8848`、忽略 `NACOS_PORT`，与 follow-api 语义冲突 ⇒ api/rpc 连到两台 Nacos | 运行时故障（业务码塌成 1015） | ✅ 已修<br>⚠️ **2026-10-30 复核（follow+task lane · 补指针）**：RAG `service/follow/follow-api/src/main/resources/application.yml:58` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:95` = **同一串** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ **现行态 = ✅ 已修（不变）**；核对日期 2026-10-30 |
| DRIFT-FOLLOW-006 | 5 个 `follow_rpc_*` 指标**无任何抓取出口**（Go 有 DevServer `/metrics`，RAG 的 follow-rpc 是纯 gRPC 进程） | **可观察（抓取侧）** | ✅ **已修（2026-10-29 · 编号 `S1-FOLLOW-001`）** —— 出口已落地（Tomcat + Actuator，**端口 16065 与 Go 一字不差**）⇒ 双侧 series 集合**逐条一致**（仅余 **P3/D 类**的 8 条 `_max` 增项）⇒ 详见下方 §「DRIFT-FOLLOW-006 的落地（2026-10-29）」<br>⚠️ **2026-10-30 复核（follow+task lane · 补指针）**：RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:131` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ＋ `:112` = `register-enabled: false` · Go `service/follow/rpc/etc/follow.yaml:23` = `Port: 16065` ⇒ **现行态 = ✅ 已落地（不变）**；核对日期 2026-10-30 <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|

> ⚠️ **三条都发生在「测试全绿」的状态下** —— 修复前 63 条测试 0 失败（**原写「现为 68 条」，已过期** —— 🆕 **2026-10-29 实测 = 86 条**：`mvn -o -B -pl service/follow/follow-rpc test` ⇒ surefire XML `testsuite` 求和 = **86** / 0 failures / 0 errors / 0 skipped · **14 个测试类**），
> 而 `java -jar` 起不来。根因见 [`docs/Pitfalls/follow/rpc.md`](../../Pitfalls/follow/rpc.md)。

## DRIFT-FOLLOW-002 ✅ 运行时 classpath 缺 `grpc-inprocess`（启动阻塞）

> 🆕 **2026-10-29 对齐（W-B lane · 台账对齐）** —— **原状态标记 `🔴`（2026-10-24 台账留痕，保留不改写）** ⇒
> **现状态：✅ 已修**（与同文件 §结论表 `:17` 的 `✅ 已修` 收敛到同一状态）。
> **依据**：`service/follow/follow-rpc/pom.xml:256-260` 的 `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）
> ＋ 同文件 `:236-254` 注释明写「scope 必须是 compile（默认），不能是 test」⇒ **L1 源码 + 运行时 classpath 装配**。

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
>
> ⚠️ **2026-10-29 真实性复核（跨域那半边已陈旧）**：原判「**那 4 个模块仍是 `test` scope** ⇒ 跨域待办」⇒ **现行态 = 跨域那半边已不成立**（域外模块已各自落码）。
> **依据（本轮逐模块实测 `grep -A3 "grpc-inprocess" <module>/pom.xml`）**：`service/article/article-rpc/pom.xml` **无 `<scope>`**（⇒ compile 默认）· `service/comment/comment-rpc/pom.xml` **无 `<scope>`** · `service/like/like-rpc/pom.xml` **无 `<scope>`** · `service/message/message-rpc/pom.xml` **无 `<scope>`** · `service/task/task-rpc/pom.xml` **无 `<scope>`** ⇒ ⇒ **全仓已无 `grpc-inprocess` 的 `test` scope**；`service/favorite/favorite-rpc/pom.xml` = `<scope>runtime</scope>`（**不是 `test`** ⇒ 运行期 classpath 上有该类 ⇒ 也不构成「运行时缺类」）。
> ⇒ **原句是 2026-10-24 的陈旧快照**，**只对本域（follow-rpc）成立**（本域 `:256-260` 已无 `<scope>test</scope>` ⇒ ✅ 已修，见上方「依据」行）。

## DRIFT-FOLLOW-003 ✅ `discovery:` URI 必须是三斜杠（启动阻塞）

> 🆕 **2026-10-29 对齐（W-B lane · 台账对齐）** —— **原状态标记 `🔴`（2026-10-24 台账留痕，保留不改写）** ⇒
> **现状态：✅ 已修**（与同文件 §结论表 `:18` 的 `✅ 已修` 收敛到同一状态）。
> **依据**：`service/follow/follow-rpc/src/main/resources/application.yml:163` `address: discovery:///user-rpc`（**三斜杠**）
> ＋ 守门 `FollowRpcProductionAddressTest.java:87-95`（本轮 surefire 实测 **t=3 f=0 e=0 s=0**）⇒ **L1 源码 + L2 守门**。

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
>
> ⚠️ **2026-10-30 真实性复核（follow+task lane · 跨域子结论已陈旧）**：上面这段「域外 5 个模块待修」**已不成立**。
> **依据（本轮逐模块实测 `grep -rn 'address: discovery:' service/*/*/src/main/resources/application.yml`）**：
> `service/article/article-rpc/src/main/resources/application.yml:144` = `discovery:///security-rpc` · `:150` = `discovery:///message-rpc` ·
> `service/comment/comment-rpc/src/main/resources/application.yml:228` = `discovery:///message-rpc` · `:234` = `discovery:///article-rpc` ·
> `service/favorite/favorite-rpc/src/main/resources/application.yml:171` = `discovery:///user-rpc` · `:179` = `discovery:///article-rpc` ·
> `service/like/like-rpc/src/main/resources/application.yml:227` = `discovery:///message-rpc` ·
> `service/message/message-rpc/src/main/resources/application.yml:164` = `discovery:///user-rpc` · `:171` = `discovery:///admin-rpc`
> ⇒ ⇒ **全仓已无两斜杠写法**（本域 `service/follow/follow-rpc/src/main/resources/application.yml:163` 亦为三斜杠）。
> ⇒ **原句是 2026-10-24 的陈旧快照** ⇒ **本条主状态不变（✅ 已修）**，只是「域外待修」这半边**已销账** ⇒ **不再作为跨域待办**。
> ⇒ 核对日期 2026-10-30。

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

## DRIFT-FOLLOW-004 ✅ `NACOS_ADDR` 在 api / rpc 两侧语义不一致
> ⚠️ **2026-10-30 真实性复核（follow+task lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修（不变）**。逐条依据见本文件「逐条结论表」。

> 🆕 **2026-10-29 对齐（W-B lane · 台账对齐）** —— **原状态标记 `🔴`（2026-10-24 台账留痕，保留不改写）** ⇒
> **现状态：✅ 已修**（与同文件 §结论表 `:19` 的 `✅ 已修` 收敛到同一状态）。
> **依据**：`service/follow/follow-api/src/main/resources/application.yml:58` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:95`
> **两侧同为** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ **L1 源码**。

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

## DRIFT-FOLLOW-006 ✅ follow-rpc 的 5 个指标**无任何抓取出口**（🔴 2026-10-24 记录 → ✅ **2026-10-29 已落地**，详见下方 §「DRIFT-FOLLOW-006 的落地」）

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
| **10 个 rpc 模块** | ⚠️ **【2026-10-31 订正 · 合并收口执行者】原写「**3/10 有**（`follow-rpc` ＋ `user-user-rpc` / `user-admin-rpc`；其余 7 个只有 `spring-boot-web-server` 抽象）」= **已作废** ⇒ ⇒ **8/10 有**（article / favorite / follow / hot / like / message / `user-user-rpc` / `user-admin-rpc`）· **2/10 无**（`comment-rpc` / `task-rpc`）⇒ ⇒ ⚠️ **判据已升级**（不再是「依赖树里有 tomcat」）⇒ ⇒ **载体面 = 部署 classpath 能让 `WebApplicationType.deduceFromClasspath()` 推出 SERVLET ＋ 未被 `web-application-type: none` 关掉 ＋ 真有容器实现** ⇒ ⇒ 逐格见 `docs/DRIFT/global/vs-common.md` §4.19.23 | 纯 gRPC 进程（⚠️ 那 8 个已带 servlet 载体） | ❌ **2/10 无 actuator web 端点载体**（⚠️ **2026-10-31 再订正**：原写「8 个 rpc 模块 0/8」→ 后写「9/10 无 web 载体」→ 现 **2/10 无 web 载体**；⚠️ 判据要点：**载体判据 = 部署产物级**（`dependency:list` ＋ `WebApplicationType` 指示类 ＋ 产物 `BOOT-INF/lib`），**不是 grep pom** —— `service/common/pom.xml:361` 的 web starter 是 **`test` scope ⇒ 不传递** ⇒ 但父子继承下「grep 不到」≠「没有」⇒ ⇒ 必须查依赖树；⚠️ **本表是「载体层」口径；另有一层「配置层」**（⚠️ **2026-10-31 订正**：`management:` 段的三重关闭**已全部修好** ⇒ ⇒ **配置面 = 10/10**，不是「3 个被关」）⇒ ⇒ **两个层都要看，只看配置层会得出「10 个有出口」的假结论**（`exposure.include` 写了但没有载体 ⇒ 抓取方连不上 HTTP）⇒ ⇒ **完整论证见 `docs/DRIFT/follow/vs-common.md` 末尾的对应小节**；⚠️ 边界：**10/10 都有 actuator 依赖**，且 10/10 都**未配 `management.endpoints.jmx.exposure`** ⇒ ⇒ JMX 侧走默认暴露 ⇒ ⇒ 本行的「零出口」严格指 **actuator 的 web endpoint 无载体**，不是「进程完全没有可观测出口」） |

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
| `user/user/rpc` | ✅ | 9094 | ✅ **已开**（2026-10-30 载体落地） | — 同款缺口**已闭合** |
| `user/admin/rpc` | ✅ | 9097 | ✅ **已开**（2026-10-30 载体落地） | — 同款缺口**已闭合** |
| `article/rpc`、`comment/rpc`、`hot/rpc`、`task/rpc` | ❌ **无 DevServer 段** | — | ❌ 零出口 | ⚠️ **不是漂移**（Go 侧同样抓不到 —— 精确原因：这 4 个域的 Go yaml **没有 DevServer 段** ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:40` 的 `DevServer` 字段带 `optional` ⇒ **整段缺失 ⇒ 零值 ⇒ `Enabled=false` ⇒ 不起 DevServer** ⇒ ⇒ **不是「Go 侧没这个端口」，而是「Go 侧根本没起」**） |

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
（详见 `docs/DRIFT/follow/api.md` 的 DRIFT-FOLLOW-007）。 **活体证据（本条 = 抓取侧）**：本条目点名的活体探针实测 ⇒ `tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）⇒ ⇒ **本节证据强度 = A 活体 · 绿(0)**（⚠️ **2026-10-31 补记 · 合并收口执行者**：本节此前**只写现象与修法、没写活体探针** ⇒ ⇒ 派生层按「取最新核对日期的那一条」仲裁时 ⇒ 本节（2026-10-31）会挤掉主登记行（2026-10-30）⇒ ⇒ ⇒ **该条被误判成「B 静态」** ⇒ ⇒⇒ **本节补上它自己的活体证据** ⇒ ⇒⇒ **不是「降级」，是「把漏写的证据补回来」**）。

## 🟢 DRIFT-FOLLOW-006 的落地（2026-10-29 · 编号 `S1-FOLLOW-001` · G1 lane · follow 域）

### 一句话结论

**RAG 侧现在真抓得到 `follow_rpc_*`**，且在**同一批请求**下与 Go 侧 DevServer 的
**series 集合逐条一致** —— 仅余 **8 条 `_max` 增项**（P3 / D 类，已登记，不修）。

⚠️ **「已改」≠「已验证」**：本节全部结论由**双侧活体对拍**得出（L4），
不是「代码写成了这样」。

### 落码清单（改了哪几行 + 为什么）

| # | 文件 | 改动 | 为什么（Go 出处 / 机制） |
|---|---|---|---|
| 1 | `service/follow/follow-rpc/pom.xml` | 加 `spring-boot-starter-web`（**版本由 `spring-boot-dependencies` BOM 管 ⇒ 不写 `<version>`**） | `spring-boot-starter-actuator` **只给 endpoint + 自动配置，不给 servlet 容器** ⇒ classpath 上没有 `jakarta.servlet.Servlet` ⇒ `WebApplicationType` 推导为 `NONE` ⇒ `management.endpoints.web.*` 这一族配置**一直是死配置**（**不报错、不生效**）⇒ 补 servlet 容器才有载体。**Boot 4 拆模块核对**：`WebEndpointAutoConfiguration`（含 `WebEndpointServletConfiguration`）**仍在** `spring-boot-actuator-autoconfigure:4.0.8` ✔；`PrometheusScrapeEndpoint` / `PrometheusMetricsExportAutoConfiguration` 在 `spring-boot-micrometer-metrics` ✔ ⇒ **唯一缺的就是 servlet 容器 + Spring MVC** ⇒ `spring-boot-starter-web`（= `spring-boot-starter-tomcat` + `spring-boot-webmvc` + `spring-boot-http-converter`）。**与本仓 8 个 api 模块的既有写法一致** |
| 2 | `service/follow/follow-rpc/src/main/resources/application.yml` | 加 `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` | Go `service/follow/rpc/etc/follow.yaml:20-27` `DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16065, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true}` ⇒ ⇒ **端口一字不差**（只有路径不同） |
| 3 | 同上 | 加 `spring.cloud.nacos.discovery.register-enabled: false` | 自动注册（`NacosAutoServiceRegistration`）注册的是 **web 容器端口** ⇒ 引入 web 容器后 Nacos 里会出现「follow-rpc → **16065**」的**错端口实例** ⇒ `follow-api` 按它连 ⇒ 连到一个只提供 `/actuator/` 的 HTTP 口 ⇒ **gRPC 链路直接断**（业务码塌成 1015）。本模块的注册是**手写**的（`bootstrap/NacosRegistration.java:128` 用 `GrpcServerProperties.getPort()`）⇒ 与 Go `zrpc.MustNewServer` 注册 `ListenOn` 的语义一致 ⇒ 自动注册必须关掉，否则**同一服务名下会有两个端口的实例** |
| 4 | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/metrics/FollowRpcMetrics.java` | 直方图改用 **`serviceLevelObjectives(...)`** 逐个钉 Go 的 `DefBuckets`（**不再用 `publishPercentileHistogram()`**） | 见下方「桶边界」一节 |

### 🔴 桶边界：这一条**不是**「表达层差异」，是**语义差异**（已修）

**现象（实测）**：RAG 侧原先的 `_bucket` 边界与 Go 的 `prometheus.DefBuckets` **零重合**。

| | 桶边界 | 每 method 的 `_bucket` 条数 |
|---|---|---|
| **Go** | `prometheus.DefBuckets` = `.005 .01 .025 .05 .1 .25 .5 1 2.5 5 10`（11 个）＋ `+Inf`（`client_golang@v1.23.2/prometheus/histogram.go:271`；由 `service/follow/rpc/internal/metrics/metrics.go:23` 的 `Buckets: prometheus.DefBuckets` 消费） | **12** |
| **RAG（修前）** | Micrometer 的 **HdrHistogram 派生的 68 个**（`0.001` / `0.001048576` / … / `30.0`）＋ `+Inf` | **69** |

⇒ 同一段 PromQL（`histogram_quantile(0.95, sum(rate(follow_rpc_request_duration_seconds_bucket[5m])) by (le))`）
在两侧**给出不同的分位数** ⇒ 监控语义不等价 ⇒ **不是表达层差异**。

**根因**：只写 `publishPercentileHistogram()` ⇒ Micrometer 用**它自己的**默认桶集。

**修法**：只用 `serviceLevelObjectives(...)`（SLO 直方图）⇒ **以 SLO 边界为桶边界**。
⚠️ **不能再加 `publishPercentileHistogram()`** —— 实测 Micrometer 会把 SLO 边界
**叠加**到它自己的 68 个桶上（⇒ 79 个桶）⇒ 反而更不接近 Go。
⚠️ **Boot 4 已删掉 `management.metrics.distribution.buckets.<meter>`**
（`MetricsProperties.Distribution`（`spring-boot-micrometer-metrics:4.0.8`）只有
`percentilesHistogram` / `percentiles` / `slo` / `minimumExpectedValue` /
`maximumExpectedValue` / `expiry` / `bufferLength`，**没有 `buckets`**）
⇒ 桶边界**只能**在代码侧钉。

**修后实测**：

```
RAG : 0.005 0.01 0.025 0.05 0.1 0.25 0.5 1.0 2.5 5.0 10.0 +Inf
Go  : 0.005 0.01 0.025 0.05 0.1 0.25 0.5 1   2.5 5   10  +Inf
```

⇒ **边界逐个相同**；只剩 `1`/`1.0`、`5`/`5.0`、`10`/`10.0` 的**文本**差异
（Java `double` 的 `toString`）⇒ Prometheus 的 `le` 按浮点解析 ⇒ **语义等价**
⇒ 登记为 **P3 / D 类**（已登记，不修；探针按**浮点数值**归一化 `le` 后比对）。

> ⚠️ **原 javadoc 的断言是错的**（已当场订正）：原文写「`publishPercentileHistogram()`
> ……其桶边界与 Go 的 `prometheus.DefBuckets` **逐个相同**」——
> **实测零重合** ⇒ 属 AGENTS.md §3.2 说的「无出处 idiomatization」的反面：
> **有出处、但断言与事实不符** ⇒ 本次按实测订正。

### 双侧活体对拍结果（**L4**）

探针：**`tools/metrics-probe/follow/run.sh`**（可复算，`EXIT=0` ⇒ 守门通过）
⇒ 两侧**各起一套**（Go follow-rpc + Go user-rpc + RAG follow-rpc + **两个隔离 PG**），
**同一份** gRPC 客户端（pb 从冻结真源现拷）打**同一批**请求。

**请求矩阵 28 步（两侧逐条比对，全部一致）**：8 个方法各一次**成功** + 8 个方法各一次
**业务失败**（`user_id=0` / `target_id=0` / 自关系 / 目标用户不存在）
+ **10 步故障注入**（改名表制造真 DB 错误 ⇒ 覆盖 `db_error_total` 的 (action, type) 维度）。

**指标族行数（实测）**：

| 指标族 | Go | RAG | 判定 |
|---|---|---|---|
| `follow_rpc_request_total` | 16 | 16 | ✅ 一致 |
| `follow_rpc_relation_action_total` | 8 | 8 | ✅ 一致 |
| `follow_rpc_db_error_total` | 10 | 10 | ✅ 一致 |
| `follow_rpc_recommendation_size` | 1 | 1 | ✅ 一致 |
| `follow_rpc_request_duration_seconds_bucket` | 96 | 96 | ✅ 一致（桶边界已对齐） |
| `follow_rpc_request_duration_seconds_count` | 8 | 8 | ✅ 一致 |
| `follow_rpc_request_duration_seconds_sum` | 8 | 8 | ✅ 一致（**值不比**，取决于真实耗时） |
| `follow_rpc_request_duration_seconds_max` | 0 | 8 | 🟡 **RAG 多 8 条**（见下） |
| **合计 `follow_rpc_*` 行数** | **147** | **155** | — |

**series 集合差异（逐条）**：

```
仅 Go 有：  （0 条）
仅 RAG 有：  （8 条）
  follow_rpc_request_duration_seconds_max{method=Block}
  follow_rpc_request_duration_seconds_max{method=Follow}
  follow_rpc_request_duration_seconds_max{method=GetBlockList}
  follow_rpc_request_duration_seconds_max{method=GetFollowList}
  follow_rpc_request_duration_seconds_max{method=GetFollowerList}
  follow_rpc_request_duration_seconds_max{method=GetRecommendations}
  follow_rpc_request_duration_seconds_max{method=Unblock}
  follow_rpc_request_duration_seconds_max{method=Unfollow}
值差异：    （0 条）—— 计数型 / gauge 型的**值**两侧逐条相等
```

### 残余差异登记（按 `docs/twoStage.md` §10 定级）

| # | 差异 | 严重度 | 阶段归属 | 理由 |
|---|---|---|---|---|
| 1 | **RAG 多 8 条 `…_seconds_max`** | **P3** | **D 类**（可不实现 / 不适用） | Micrometer 的 Timer 默认导出滚动窗口 max；Go 的 `client_golang` Histogram **不导出 `_max`** ⇒ **纯增项** ⇒ 不影响 Go 侧任何 PromQL 的语义（PromQL 按指标名 + 标签取值，多出来的 series 只是多一个可选指标）⇒ **不修**（Micrometer 1.16 无「关掉 Timer max」的开关） |
| 2 | **暴露路径不同**（Go `/metrics` + `/healthz` ↔ RAG `/actuator/prometheus` + `/actuator/health`） | **P3** | **D 类** | **端口一字不差（都是 16065）**；路径是 Actuator 的固定前缀，与本仓 8 个 api 模块的既定形态一致 ⇒ 抓取配置**改路径不改端口** ⇒ 表达层差异 |
| 3 | **载体不同**（Go = `http.ListenAndServe` 的小 server，无 servlet；RAG = Tomcat，有 servlet 但**无任何业务 HTTP 路由**） | **P3** | **D 类** | 对外可观测面（独立端口 + Prometheus 文本）一致；载体不同属框架表达差异 ⇒ **不修** |
| 4 | **`le` 文本 `1` vs `1.0` / `5` vs `5.0` / `10` vs `10.0`** | **P3** | **D 类** | Prometheus 按浮点解析 `le` ⇒ 同一个桶 ⇒ 语义等价 ⇒ 不修 |
| 5 | Go DevServer 另有 `/debug/pprof/*`（`follow.yaml:27` `EnablePprof: true`） | **P3** | **D 类** | RAG 侧 Actuator 无 pprof endpoint ⇒ **新增可观测面缺失**；不影响指标可比性 ⇒ 不修 |

⚠️ **「一条差异 ≠ 一个缺陷」**：上表 5 条**全部是 D 类**（已分析确认可不实现），
**没有一条是 A/B/C 类** ⇒ **DRIFT-FOLLOW-006 本体（零抓取出口）已闭环**。

### 证据强度 / 核对日期

| 项 | 强度 | 日期 |
|---|---|---|
| 抓取出口存在性 | ✅ **L4 双侧活体**（真进程 + 真 PG + 真 Nacos + 真 HTTP 抓取） | 2026-10-29 |
| series 集合逐条比对 | ✅ **L4 双侧活体**（28 步请求矩阵 + 10 步故障注入，两侧响应逐字节一致） | 2026-10-29 |
| 计数型 / gauge 型的值比对 | ✅ **L4**（两侧**同一批请求** ⇒ 值逐条相等，实测 0 差异） | 2026-10-29 |
| 桶边界对齐 | ✅ **L4**（两侧 `le` 边界逐个比对，按浮点归一化） | 2026-10-29 |
| 变异必红 | ✅ **L4**（见下） | 2026-10-29 |

**变异必红（两处，均已还原干净）**：

| 变异 | 现象 | 结果 |
|---|---|---|
| ① **改实现**：摘掉 `serviceLevelObjectives(...)`（`FollowRpcMetrics.java`） | RAG 侧 `_bucket` **一条都不剩**（无 SLO 也无 `publishPercentileHistogram` ⇒ 不出直方图）⇒ `仅 Go 有 = 96` | ✅ **转红（`EXIT=1`）**，报「期望 8 条 / 实际 104 条」 |
| ② **改期望值**：往 `expected-diff.txt` 加一条不存在的差异 | 实际差异与冻结期望不再逐条相同 | ✅ **转红（`EXIT=1`）**，报「- 期望有、实际无：`follow_rpc_db_error_total{action=mutated,type=mutated}`」 |

⇒ 还原后复跑 **两次 `EXIT=0`**（绿）。

### 解除条件（本条**已解除**）

- ✅ **出口存在** ⇒ RAG 侧真起 follow-rpc 就能抓到 `follow_rpc_*`。
- ✅ **series 集合可比** ⇒ 两侧同一批请求 ⇒ 集合逐条一致（残余 5 条 D 类已登记）。
- ✅ **有可复算的守门** ⇒ `tools/metrics-probe/follow/run.sh`（`EXIT=0` 绿 / `EXIT=1` 红 / `EXIT=2` 环境问题）。
- ✅ **残余差异已登记** ⇒ 上表 5 条全部 **D 类**，无 A/B/C 类待办。

⇒ **DRIFT-FOLLOW-006 关闭（CLOSED）**。

### 顺带确认的两件事（**不是**本条漂移）

1. **`service/common` 的 `ObservabilityLifecycle` 关闭路径引用 `jakarta.servlet.Filter`**
   （`rpc.md` 待办第 2 条）⇒ **本轮引入 servlet 容器后，follow-rpc 运行时 classpath 上
   有了 servlet-api ⇒ 该 `NoClassDefFoundError` 在 follow-rpc 上**不再复现** ⇒
   **但共享层代码未动**（**G4 写权**）⇒ 其它仍无 servlet 的 rpc 组件**仍会踩** ⇒
   **登记为给 G4 的信息，不在本条结案**。
2. **`management.server.port` 未单独设置** ⇒ Actuator 挂在**主 web 端口**上
   （本模块唯一的 HTTP 监听）⇒ 与 Go 的 DevServer「独立端口」**形态等价**
   （Go 的主端口是 gRPC，DevServer 是另一个口；RAG 的主 gRPC 端口与 web 端口分离，
   web 端口 = 观测端口 16065）⇒ 两者都是「**gRPC 业务口 + 另一个观测口**」。

### 跨域待办（**仍归主会话 / 其它域**，本工作树不碰）

上表 §「全仓调查结论」列的另外 5 个 rpc 组件
（`favorite-rpc` 16062 / `message-rpc` 16067 / `like-rpc` 9098 /
`user-user-rpc` 9094 / `user-admin-rpc` 9097）
**仍是「Go 有 DevServer、RAG 零出口」** ⇒ 本轮**只落 follow 域**（⚠️ **2026-10-30 订正**：`user-user-rpc` / `user-admin-rpc` 的这一格已由 G1 lane 的载体落地闭合 ⇒ ⇒ ⚠️ **【2026-10-31 订正 · 合并收口执行者】现为 **8/10 有载体**（原写「3/10」已作废；逐格见 `docs/DRIFT/global/vs-common.md` §4.19.23））
⇒ 跨域派单**保持原样**（本工作树不得改域外文件）。

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

## 逐文件对照索引（**20 个 Java** ↔ 22 个 Go，**全量无遗漏**）

> ⚠️ 2026-10-24 复核补录：同 api.md，「逐条对照」这条断言此前**无法从台账里核对**
> （`FollowRpcApplication`、`UserRpcClient`、`FollowRpcProperties`、
> `MybatisPlusOrderingBridgeAutoConfiguration`、两个 Entity、4 个 `errmsg` 类
> 共 **10 个类从未出现过**）⇒ 现补全索引。
>
> 🧾 **2026-10-27 订正（G1 lane · MP 排序桥收编）**：原第 5 行
> `rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` **已删除** ——
> 域内副本已收编到共享层 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`
> （经 `service/common` 的 `AutoConfiguration.imports` 注册 ⇒ 对本模块同样生效，
> 因为 Boot 的 `ImportCandidates` 用 `ClassLoader.getResources`（**复数**）读 classpath 上
> **全部**同名资源）⇒ ⇒ Java 侧 **21 → 20** 个文件，本表**原 #6~#21 顺次前移为 #5~#20**。
> 该桥是**表达层**（刻意为空、不注册任何 bean、只建立 `DataSource → 桥 → MybatisPlus` 排序边）
> ⇒ **删除它不产生任何行为差**。⚠️ 上文「共 10 个类从未出现过」那段是**2026-10-24 的历史陈述**，
> 保留不改；它列举的 `MybatisPlusOrderingBridgeAutoConfiguration` **在 2026-10-24 确实存在**。

| # | RAG 源文件（follow-rpc **16** + follow-common 4） | Go 对应物（follow/rpc 21 + follow/common 1） | 承重点 | 结论 |
|---|---|---|---|---|
| 1 | `rpc/FollowRpcApplication.java` | `follow.go`（`main()`） | Go main 的五步逐条对应（**无 ticker/cron**） | ✅ |
| 2 | `rpc/bootstrap/NacosRegistration.java` | `follow.go` → `zrpc.MustNewServer` → `go-zero@v1.10.2/zrpc/server.go:63 SetUp()` | 注册名 `follow-rpc`；Go 侧由 zrpc 自动注册 | ✅ |
| 3 | `rpc/client/UserRpcClient.java` | `internal/svc/servicecontext.go` 的 `UserRpc` + `internal/logic/helper.go:81-94` | `GetUser` 的三分支判定 | ✅ |
| 4 | `rpc/config/FollowRpcProperties.java` | `internal/config/config.go`（struct tag 默认值） | `ListConf` / `RecommendConf`；**`default=20` 与 `default=50` 是两个独立常量** | ✅ |
| 5 | `rpc/domain/FollowRelationEntity.java` | `internal/model/data_model.go` `FollowRelation` | `@TableName("follow_relation")`；主键 / `autoCreateTime` 的差异已在 DDL 节说明 | ✅ |
| 6 | `rpc/domain/BlockRelationEntity.java` | `internal/model/data_model.go` `BlockRelation` | **两张表刻意不复用同一个 Entity**（Go 侧也是两个 struct + 两个 `TableName()`） | ✅ |
| 7 | `rpc/mapper/FollowRelationMapper.java` | `internal/model/follow.go` 的 follow 查询 | 过滤列 / 投影列 | ✅ |
| 8 | `rpc/mapper/BlockRelationMapper.java` | `internal/model/follow.go` 的 block 查询 | 过滤列 / 投影列 | ✅ |
| 9 | `rpc/mapper/GoStyleLimitOffset.java` | `internal/model/follow.go:79-87` 等 **6 处** | **条件拼接**：`offset>0` / `limit>0` 才出现 LIMIT/OFFSET | ✅ |
| 10 | `rpc/metrics/FollowRpcMetrics.java` | `internal/metrics/metrics.go` | 4 写方法 2 指标 / 4 读方法 1 指标；**直方图桶**（DRIFT-007） | ✅ |
| 11 | `rpc/service/FollowBlockTransactionService.java` | `internal/model/follow.go:33-54` `CreateBlockAndCleanup` | 插 block + 双向删 follow **单事务**（独立 Bean，避开 `@Transactional` self-invocation 静默失效） | ✅ |
| 12 | `rpc/service/FollowDomainService.java` | `internal/logic/{follow,unfollow,block,unblock}logic.go` | 校验分支、存在性校验、动作方法 | ✅ |
| 13 | `rpc/service/FollowRecommendationService.java` | `internal/logic/getrecommendationslogic.go` | 推荐算法 6 条硬语义（提前返回/三重过滤/expanded 防环/末层不展开/升序/offset 越界返空） | ✅ |
| 14 | `rpc/service/FollowRpcSupport.java` | `internal/logic/helper.go` | 校验（5003/5201）；列表归一化三段序（offset→limit→截断） | ✅ |
| 15 | `rpc/service/FollowUserExistenceService.java` | `internal/logic/helper.go:81-94` `ensureUserExists` | 三分支（5101/5001/5101） | ✅ |
| 16 | `rpc/service/impl/FollowServiceGrpcImpl.java` | `internal/server/followserviceserver.go:26-64` | 8 个 RPC 方法与返回类型逐条一致 | ✅ |
| 17 | `common/errmsg/FollowErrorCode.java` | `service/follow/common/errmsg.go` | 错误码常量表 + 顺序 | ✅ |
| 18 | `common/errmsg/FollowErrorMessages.java` | 同上 | 文案表 + 顺序 | ✅ |
| 19 | `common/errmsg/FollowCodeError.java` | 同上 | Go `CodeError` 的承载（code + msg） | ✅ |
| 20 | `common/errmsg/FollowGrpcError.java` | 同上 + go-zero `zrpc/internal/codes` | gRPC 码 ↔ 业务码的**三段转换** | ✅ |

**Go 侧其余 5 个文件的落点**（未单列进上表，因为它们不是「一对一对应某个 Java 类」）：

| Go 文件 | 落点 | 说明 |
|---|---|---|
| `followservice/followservice.go` | [`proto.md`](./proto.md) ＋ api 侧的 `client/FollowRpcClient.java`（**见 [`api.md` 行 #6](./api.md) = `client/FollowRpcClient.java`；本表的 #6 是 `rpc/domain/BlockRelationEntity.java`，两者不是同一行**） | **goctl 生成的 client 包装层**（`FollowService` interface + `NewFollowService`）⇒ 对应 RAG 的 `follow-proto` 生成 stub + api 侧 `FollowRpcClient`；**生成物，非手写语义**。⚠️ 它虽然**物理位置**在 `follow/rpc/` 目录下，但**语义上是给 api 侧调 rpc 用的客户端**，不是 rpc 服务端的一部分 ⇒ 不算 rpc 模块的对应物 |
| `internal/model/db.go` | `follow-rpc/src/main/resources/application.yml`（datasource 段）＋ `db/migration/V1,V2` | **建库与连接参数**（此前只在本文件 DDL 段被顺带提及，**未进索引** ⇒ 2026-10-24 复核补录）：DSN 的 `sslmode=`（← Go 的 `Mode` 字段）＋ **硬编码 `TimeZone=Asia/Shanghai`**（会话时区，影响 `timestamptz` 读出值）；`SetMaxIdleConns(10)` / `SetMaxOpenConns(100)` ⇒ RAG 侧 `hikari.minimum-idle:10` / `maximum-pool-size:100`；`AutoMigrate(&FollowRelation{}, &BlockRelation{})` ⇒ RAG 侧 **Flyway `V1/V2` + 独立账本 `flyway_schema_history_follow`** |
| `internal/model/vars.go` | **无对应物（有意不复制）** | Go 侧一个**无消费者的死变量** `ErrorNotFound`（`errors.New("record not found")`）⇒ RAG 侧不复制死代码 |
| `pb/follow.pb.go` / `pb/follow_grpc.pb.go` | [`proto.md`](./proto.md) | **protoc 生成物**；行为层的对照在 `.proto` 源文件层完成（逐行 diff 0 差异） |

> ⚠️ **2026-10-24 复核订正**：本表此前**误把 Go `api/internal/handler/follow/` 下 8 个 handler 算进 rpc 侧**，
> 且引用的「表 #5 `controller/FollowController.java`」是 **`api.md` 的行号、本表无此行** ⇒ 跨文档串行号。
> 根因：8 个 handler 物理上属于 **Go `follow/api`**（HTTP 层），**已在 [`api.md` 行 #5](./api.md) 单独列出**。
> ⇒ 本表改为只收 rpc 侧真实剩余的 5 个文件（`followservice.go` / `db.go` / `vars.go` / `pb`×2），
> 账目因此对上：**行 #1–#20 覆盖 17 个 Go 文件**（16 个 rpc 侧 + `common/errmsg.go`）**+ 本表 5 个 = 22
> = Go `follow/rpc` 21 + `follow/common` 1**（`find` 实测，非估算）。
>
> 🧾 **2026-10-27 订正（G1 lane · MP 排序桥收编）**：本表原第 5 行
> （`rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`，**无 Go 对应物**）已删除 ⇒
> Java 侧 21 → **20** 个文件 ⇒ **行 #1–#21 改为行 #1–#20**，
> 但**Go 侧计数不变**（删掉的那行本来就没有 Go 对应物 ⇒ 仍是 17 个 Go 文件）。

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（**22 个 Go 文件 ↔ 21 个 Java 文件**） | ✅ 双向静态（**逐文件索引见上节，可 grep 自证**） |
| 真启动（`java -jar` 真 PG + 真 Nacos） | ✅ **真启动**，非 test classpath |
| DDL vs GORM 产物 | ✅ **双侧真产物逐行对比** |
| gRPC 行为 | ✅ **双侧活体对拍**（Go follow-rpc 与 RAG follow-rpc 同跑，52/52 逐字节一致） |
| 指标口径（**series 集合 + 计数型/gauge 型的值**） | ✅ **L4 双侧活体**（2026-10-29 落地后：Go 147 行 / RAG 155 行 `follow_rpc_*`，**series 集合逐条一致**，残余 8 条 `_max` 增项已登记 P3/D）⇒ 探针 `tools/metrics-probe/follow/run.sh` |

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
3. ~~**指标数值**未做双侧对拍~~ ⇒ ✅ **已做**（2026-10-24，U1 → **2026-10-29 落地**）：
   2026-10-24 只做到「Go 侧抓到 127 行 `follow_rpc_*`；RAG 侧**零出口**」；
   **2026-10-29（`S1-FOLLOW-001`）** 已补齐出口并完成**双侧 series 集合逐条比对**
   （Go 147 行 / RAG 155 行，**仅余 8 条 `_max` 增项** ⇒ P3/D 类）⇒ **DRIFT-FOLLOW-006 关闭**。
4. ~~**DRIFT-FOLLOW-006 的修法已转为「全仓 rpc 层统一方案」交主会话拍板**~~ ⇒
   ✅ **已落地（用户重新批准 · 2026-10-29 · 编号 `S1-FOLLOW-001`）**：
   本轮**只落 follow 域**（`follow-rpc` 补 `spring-boot-starter-web` + 观测端口 16065 +
   关掉 Nacos 自动注册 + 直方图桶边界对齐 Go 的 `DefBuckets`）⇒
   **域内结论、逐行比对、差异定级、探针、变异必红**见
   §「DRIFT-FOLLOW-006 的落地（2026-10-29）」。
   ⚠️ **全仓统一方案仍未做**：另外 5 个 rpc 组件（`favorite-rpc` / `message-rpc` / `like-rpc` /
   `user-user-rpc` / `user-admin-rpc`）**当时**是「Go 有 DevServer、RAG 零出口」**（⚠️ `user-user-rpc` / `user-admin-rpc` 两格已于 2026-10-30 由 G1 lane 载体落地闭合）**
   ⇒ **跨域派单保持原样**（本工作树不得改域外文件）。
   ⚠️ 本轮**选的是「方案 B 变体」**（Tomcat 承载 Actuator，**观测端口与 Go 同为 16065**），
   而非台账里列的「方案 A」（`com.sun.net.httpserver` 小 server）：
   ⇒ 原因与代价已登记在 §「残余差异登记」的第 2/3 条（**D 类**）。
5. **跨域派单（5 处同款缺口）**：`favorite-rpc`(16062) / `message-rpc`(16067) / `like-rpc`(9098) /
   `user-user-rpc`(9094) / `user-admin-rpc`(9097) —— Go 有 DevServer、RAG 零出口（⚠️ 后两者已于 2026-10-30 由 G1 lane 载体落地闭合 ⇒ 上表该两行已就地订正）。
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


---

## 🔴 `DRIFT-FOLLOW-013` · **「OS 盲选口」族** · 本组件 **2 处**（2026-10-28 · G1 lane）

> ⚠️ **2026-10-29 真实性复核**：原判 **🔴（未修）** ⇒ **现行态 = ✅ 已修（2026-10-28 落码 · 本轮复核属实）**。
> **依据（当场核过）**：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:115` = `ServerBuilder.forPort(RealGrpcPort.pick());`（**不再是 `forPort(0)`**）＋ `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcSlowThresholdGateTest.java:274` = 同款（**不再是 `forPort(0)`**）＋ `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/support/RealGrpcPort.java:66,69` = `FIRST = 22320` / `LAST = 22339`（**域内 rpc 专用段**，与 api 段的 22300–22319 不重叠）⇒ ⇒ 「🔴」是**「2026-10-28 新立时」的陈旧快照**，正文表格里的「已改」与本条结论一致。
> ⇒ **标题的 🔴 标记保留不改写**（历史留痕），**当前状态读本订正段**。

> 📌 **机制 / 本机实测（Q3·Q6·Q7·Q8）/ 守门细节 / 效力边界 ⇒ [`docs/DRIFT/users/vs-common.md` §65](../users/vs-common.md)**
> ⇒ 本条只记**本组件那一份**（不抄机制表，避免两处漂移）。

| 族 | 位置（**改造后**行号） | 状态 |
|---|---|---|
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `FollowGrpcInterceptorWiringTest.java:115` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `FollowRpcSlowThresholdGateTest.java:274` | 已改 |

- **危害**：`forPort(0)` / `new ServerSocket(0)` / `RANDOM_PORT` 三种写法都是**让 OS 从临时端口段（49152–65535）里盲选** ⇒ ⇒ 与 Docker 随机发布口**同段**
  ⇒ ⇒ 邻居占**更具体地址** `127.0.0.1:P` 时我方**通配绑定照样成功**（实测：两个监听者并存）⇒ ⇒ 客户端被抢 ⇒ **静默假红**（看着像被测代码炸了，其实压根没被请求到）。
- **修法**：全部改走**域内私有** `support/RealGrpcPort`（`support/RealTomcatPort`）⇒ **段**：`22320–22339`（共享层预分配的 follow 域段 22300–22399 的子段）⇒ 段内挑口 ＋ **双地址 bind 探针**（只探通配**检不出**「更具体的占用者」）。
- **段来源**：共享层已预分配给本域的段（`docs/DRIFT/global/vs-common.md` §4.10.19.3）⇒ **共享层台账零改动**（写权归 G4）。
- **守门**：域级 `BlindPortGateTest`（落在 **user-rpc**，`service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java`）**5 条** ⇒ 一并覆盖本组件 ⇒ **本组件不单独建守门**（避免两份规则漂移）。
- **边界**：**本组件生产代码零改动**（全部落在 `src/test/java`）⇒ ⇒ **不构成 Go 对照项**（Go 侧无对应物 ⇒ 无对照义务）。


---

## 🆕 **2026-10-30 真实性复核回执**（follow+task lane · 「台账结论 ↔ 代码」逐条复核 · follow 域 · `rpc.md`）

> **口径**：同 [`api.md`](api.md) 的 2026-10-30 回执（Go 真源 + RAG 两侧逐条核 · 指针逐条核到「那一行确实讲这件事」）。
> **原值一律保留**；本轮**未改判任何一条**，只做**子结论订正 + 指针补齐**。
> **机器判据**：`python3 tools/difftest/ledger-claim-audit.py --domain follow --strict` ⇒ **🔴 落空 = 0**。

### 一、逐条结论表（编号 · 落点 · 原状态 → 现行态 · 两侧依据 · 是否改判）

| 编号 | 落点 | 原状态 → 现行态 | 依据（两侧 `文件:行号` · 核对日期 2026-10-30） | 改判 |
|---|---|---|---|---|
| `DRIFT-FOLLOW-002` | §结论表 `:17` | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）＋ `:236`（注释明写「scope 必须是 compile（默认），不能是 test」）⇒ **运行期 classpath 有该类** ⇒ **本域成立**；⚠️ 跨域那半边（域外 4 个模块仍是 `test` scope）**2026-10-29 已订正**，本轮复核**属实**：逐模块 `grep -A4 grpc-inprocess` ⇒ `article-rpc` / `like-rpc` / `task-rpc` = **无 `<scope>`**、`favorite-rpc` = `<scope>runtime</scope>`（**不是 `test`**）⇒ **全仓已无 `grpc-inprocess` 的 `test` scope** ⇒ **跨域待办已销账** | 否（**子结论订正已在本条正文**）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-003` | §结论表 `:18` | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:163` = `address: discovery:///user-rpc`（**本轮重核：该行确实讲这件事**）＋ 守门 `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcProductionAddressTest.java:87` = `@DisplayName("生产 application.yml 的 grpc client 地址必须是 discovery:///user-rpc（三斜杠）")` ⇒ **「两斜杠」形态已消除** | 否（**子结论订正**：域外 5 个模块「全是两斜杠」**已陈旧** ⇒ 已在本条正文追加订正段）（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-003` | §标题 `:79` | ✅ 已修 → ✅ 已修（不变） | 同上（根因段「devh 判据 = `uri.getPath()`」**不因代码未变而失效**）⇒ 结论仍成立 | 否（**子结论订正**：见本条正文追加段）（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-004` | §结论表 `:19` | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/resources/application.yml:58` = `server-addr: ${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ＋ RAG `service/follow/follow-rpc/src/main/resources/application.yml:95` = **同一串** ⇒ **两端必指同一台 Nacos**（本轮逐行重核两行内容一致） | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-004` | §标题 `:132` | ✅ 已修 → ✅ 已修（不变） | 同上（根因段「同域两个模块对同一环境变量给出两种语义」**不因代码未变而失效**）⇒ 结论仍成立 | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-006` | §结论表 `:20` | ✅ 已修（2026-10-29 · 编号 `S1-FOLLOW-001`）→ ✅ 已落地（不变） | RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web`（**给了 Actuator 一个 servlet 载体**）＋ RAG `service/follow/follow-rpc/src/main/resources/application.yml:131` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ＋ `:112` = `register-enabled: false`（**关掉自动注册 ⇒ 避免 Nacos 里出现错端口实例**）· Go `service/follow/rpc/etc/follow.yaml:23` = `Port: 16065` ⇒ **端口一字不差** ＋ 探针 `tools/metrics-probe/follow/run.sh:355` = 就绪判据 = **真抓到 `/actuator/prometheus`**（`:361` = 未就绪即 `die`）⇒ **抓取出口已在** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |

### 二、本轮新增/订正的证据指针

| 落点 | 原指针 | 现行指针 | 说明 |
|---|---|---|---|
| §`DRIFT-FOLLOW-003` 的「同款缺陷仍在域外」段 | 「域外 5 个模块 grpc 地址全是两斜杠 ⇒ 待修」 | 逐模块现行地址：`service/article/article-rpc/src/main/resources/application.yml:144,150` · `service/comment/comment-rpc/src/main/resources/application.yml:228,234` · `service/favorite/favorite-rpc/src/main/resources/application.yml:171,179` · `service/like/like-rpc/src/main/resources/application.yml:227` · `service/message/message-rpc/src/main/resources/application.yml:164,171` | **陈旧快照** ⇒ 全仓已无两斜杠 ⇒ 跨域待办销账 |

### 三、仍未核到（**待核实**）

| 项 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `DRIFT-FOLLOW-006` 的**双侧 series 逐条一致**（含 8 条 `_max` 增项） | 需**真 Go follow-rpc + 真 RAG follow-rpc 同时跑**并各抓一次 `/metrics` vs `/actuator/prometheus` ⇒ 本轮**未起进程** | 已核**配置层**（端口 / `register-enabled` / starter-web / 探针就绪判据）⇒ 状态**沿用 2026-10-29 结论**，本轮**未独立改判** |
| `DRIFT-FOLLOW-002` 的**真启动复验**（`NoClassDefFoundError` 未复现） | 需**真容器起 `follow-rpc`** ⇒ 本轮**未起** | 已核 **pom 的 scope**（L1 源码）⇒ 未独立改判 |

## DRIFT-FOLLOW-014 ✅ 出向 user-rpc **缺 2s 客户端超时**（2026-10-30 · G1 lane · 域侧自修 · 已落码）

> **证据强度 = L3**（Go ↔ RAG 源码逐条对照 ＋ 本域**真跑**守门 ＋ **变异实测**）
> ⚠️ **未做双侧活体对拍** ⇒ **不标 L4** ⇒ 「已修」读作「**在 L3 强度下一致**」，不是已闭环（AGENTS.md §〇）。

### 一、现象

Go 侧 `follow-rpc → user-rpc` 的出向调用**恒带 2s 客户端超时**；RAG 侧 `devh` 注入的是**裸 stub**
（**无 call deadline**）⇒ ⇒ **缺「客户端侧」收口**：失败只能由**服务端**拦截器兜底
⇒ ⇒ 失败**归因**与**收口时刻**都可能与 Go 不同。

### 二、Go 侧出处（逐条 · 本轮亲自读过）

| # | 出处 | 事实 |
|---|---|---|
| G1 | `go-zero@v1.10.2/zrpc/config.go:31` | `RpcClientConf.Timeout int64 `json:",default=2000"` ` ⇒ **默认 2000ms** |
| G2 | `go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25` | `ctx, cancel := context.WithTimeout(ctx, t)` ⇒ **取更早者** ⇒ 出向 deadline = min(入站 ctx 剩余, 2s) |
| G3 | `service/follow/rpc/etc/follow.yaml:37-41` | `UserRpc:` 段**只有 `Etcd`**、**无 `Timeout`** ⇒ ⇒ 走 G1 默认值 |
| G4 | `service/follow/rpc/internal/logic/helper.go:82` | `svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: userID})` ⇒ 出向调用**直接透传 handler 的 ctx**（无 `depCtx`、无 otel 派生 ⇒ 该行曾被台账误描述，已订正） |
| G5 | `service/follow/rpc/internal/logic/helper.go:84-89` | `st, ok := status.FromError(err)`；`NotFound` → `GRPCError(codes.NotFound, ErrorUserNotFound)`；**其余一切（含 `DeadlineExceeded`）→ `GRPCError(codes.Internal, ErrorServerCommon)`** |
| G6 | `service/follow/common/errmsg.go:38` / `:47` | `ErrorServerCommon = 5001` ／ `ErrorUserNotFound = 5101` |
| G7 | `go-zero@v1.10.2/zrpc/config.go:46` ＋ `service/follow/rpc/follow.go:34` ＋ `:44` ＋ `service/common/observability/rpc.go:38` | **服务端 2s 的确切出处**（2026-10-30 14:4x 实测补）：`zrpc/config.go:46` = `Timeout int64 `json:",default=2000"` ⇒ **服务端默认也是 2000ms**；`follow.go:34` = `DisableNativeRpcTimeout(&c.RpcServerConf)` ⇒ 读 2s 并把 go-zero **原生**超时关掉（`rpc.go:19-23` 把 `conf.Timeout = 0`）；`follow.go:44` = `AddUnaryInterceptors(NewUnaryServerInterceptor(rpcTimeout, …))` ⇒ **自定义**拦截器接管；`rpc.go:38` = `context.WithTimeout(ctx, timeout)` ⇒ **`handler(timeoutCtx, req)` 拿到的 ctx 带 2s deadline** ⇒ ⇒ **G4 的 handler ctx 确实带 2s** ⇒ ⇒ **数值等价、机制不同**（原生被关、自定义接管） |

⇒ ⇒ **Go 的失败在客户端侧被观测 ⇒ 下游拿到的是 `INTERNAL` + 业务码 `5001`**。

### 三、⚠️ 差异的准确边界（不要读宽）

RAG 侧下游 `user-rpc` **自己**带服务端 2s 超时
（`service/users/user/user-rpc/src/main/resources/application.yaml:209` = `rpc-timeout: 2s`；
接线在位由 `python3 tools/difftest/check-grpc-interceptor-wiring.py` = **EXIT=0** 实测确认）⇒ ⇒

| 形态 | Go（真源） | RAG **修前** | RAG **修后** |
|---|---|---|---|
| **① 请求到达下游、业务挂死** | 客户端 2s 收口 ⇒ `helper.go:84-89` ⇒ **`INTERNAL` + 5001** | **服务端** 2s 收口 ⇒ `DEADLINE_EXCEEDED` ⇒ `FollowUserExistenceService.java:86-91` 分支② ⇒ **`INTERNAL` + 5001** | 同左 ⇒ **与 Go 同形** |
| **② 请求没到达下游**（进程不可达 / 网络黑洞 / 连接建好但无响应） | 客户端 2s 收口 ⇒ **`INTERNAL` + 5001** | **无任何客户端收口** ⇒ 只能等**服务端**兜底（`service/follow/follow-rpc/src/main/resources/application.yml:247` = `rpc-timeout: 2s`，管的是**入站**调用）⇒ ⇒ **形态②才是差异** | 客户端 2s 收口 ⇒ **`INTERNAL` + 5001** ⇒ **与 Go 同形** |

⇒ ⇒ **本条要修的是形态②**（下游看到的 status 与业务码都不同），**不是**形态①。

### 四、RAG 侧修前 / 修后

| | 修前 | 修后（2026-10-30 · G1 lane） |
|---|---|---|
| 出向 stub | `devh` 注入的裸 stub ⇒ **无 call deadline** | `capByClientTimeout(stub)` ⇒ **2s 客户端 deadline**<br>⚠️ **2026-10-31 订正 · 两半分工（别读成「applyTo 是冗余」）**：出向 deadline = min(**请求剩余**, **2s 客户端上界**) —— 「请求剩余」那半由**共享层** `GoStyleOutboundDeadlineInterceptor` 提供（它是 `@GrpcGlobalClientInterceptor` ⇒ 接在 devh channel 上 ⇒ **裸 stub 也继承**）⇒ ⇒ 本域的 `capByClientTimeout(stub)` 刻意**只**做「2s 客户端上界」那半（**不调** `applyTo`）⇒ ⇒ ⇒ 两半叠加 = min(请求剩余, 2s) ⇒ 与 Go 的 `context.WithTimeout(ctx, 2s)` 同形 ⇒ ⇒ ⇒ **域侧这处不是冗余**（共享层「客户端硬上界」**默认不启用**：`GoStyleProtectionProperties.OutboundDeadline#getCapMillis()` **默认 0**） |
| 旧注释 | `UserRpcClient` 的 javadoc 写「**没有**在此处设 deadline ⇒ 与 Go 一致：Go 的 `zrpc.MustNewClient` 也只靠 client interceptor 兜底」⇒ ⇒ **只说对了一半**（漏了「那个 client interceptor **就是** 2s 客户端超时」这一半） | **已订正**（原文保留在类注释里，标注「已订正」并给出完整论证） |
| 落码 | — | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/UserRpcClient.java`（新增 `CLIENT_TIMEOUT_MILLIS = 2000L` ＋ 包可见 `capByClientTimeout` ＋ `getUser` 改走它） |

### 五、守门网 ＋ 变异实测（AGENTS.md §3.3.3）

- **守门**：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/client/UserRpcClientDeadlineCapTest.java`（**3 条**）
  - `clientTimeoutConstantMatchesGoDefault` —— 常量 == 2000ms（对齐 G1）
  - `capProducesRealTwoSecondDeadline` —— **真**构造 `UserServiceBlockingStub` ＋ **真**读回 `CallOptions.getDeadline()`（非文本断言）
  - `downstreamHangs_clientTimesOutOnItsOwnDeadline` —— **真发一次调用**：起 **in-process 真实 gRPC server**，其 `GetUser` **故意不响应** ⇒ 客户端**真的阻塞** ⇒ 只能靠**自己套的 2s deadline** 收口 ⇒ 断言拿到 **`DEADLINE_EXCEEDED`**（Go 侧 ctx 到期同样是 `DeadlineExceeded`）⇒ 且实测耗时 ≈2s（防「秒失败」被误判成「超时生效」）
- **变异实测（M4）**：把 `getUser` 的 `capByClientTimeout(userStub)` 改成 `userStub` ⇒ **用例挂死 ⇒ `@Timeout(30s)` 触发 ⇒ 红** ⇒ ⇒ 证明「`DEADLINE_EXCEEDED` **确实是那 2s cap 造成的**」，不是别的东西兜的底 ⇒ 还原后**复绿**（实测 2.094s / 3 条全绿）。

### 六、边界（诚实声明）

- ⚠️ **未做双侧活体对拍** ⇒ 本条**不是 L4**。
- ⚠️ 守门**不**起真实 user-rpc、**不**连 Nacos、**不**带生产链路上的 `GoStyleGrpcClientBreakerInterceptor` ⇒ 证的是「**follow-rpc 自己套的客户端 deadline 真在生效**」，**不**证「整条生产链路的端到端超时表现」。
- ⚠️ **rpc 进程没有入站 ctx 预算载体**：`GoStyleRequestDeadline.begin` 的**生产**调用点全仓**只有 1 处** = `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324` ⇒ **只在 api 进程** ⇒ **rpc 进程恒 no-op** ⇒ ⇒ 这里**只有「2s 上界」这一维** ⇒ ⇒ 与 Go 的 min(服务端 2s, 客户端 2s) **同值**。
- ⚠️ **共享层那半边不在本条**：rpc 进程要拿到「入站预算」需 `GoStyleRequestDeadline` 有作用域化 API ⇒ 落点 `service/common` ⇒ **G4 写权** ⇒ 已随 `E65` 反馈单转派。
- ✅ **共享层那半边已于 2026-10-30 由 G4 落码并提交**（commit **`3d5b4924`** · 分支 `align/g4-service-common` · 未 push/未开 PR）：`RpcInterceptor` 的**绝对时刻只算一次**（入口处，`:311-312`）⇒ 两个载体（`DeadlineState` ＋ `GoStyleRequestDeadline`）**共用同一时刻**（⇒ 不会出现「SQL 已过期但出向还活着」）＋ 新增 `GoStyleOutboundDeadlineInterceptor`（`service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java`）⇒ ⇒ **与本条的域侧半边（2s 客户端上界）叠加后仍是 min** ⇒ ⇒ **与 Go 的 `context.WithTimeout(ctx, 2s)` 仍同形** ⇒ ⇒ **两条不冲突**。
  - ⚠️ **叠加语义的坐标（G1 已逐行独立核实，不是采信转述）**：`GoStyleOutboundDeadlineInterceptor.java:151-154`（请求载体剩余 → min）＋ `:157-159`（`cap-millis` → min，**默认 0 = 不启用** ⇒ **users/follow 无需为此开 yaml**）＋ **`:162-166`（调用点自带的 deadline → `Math.min`，**不覆盖**）** ⇒ ⇒ **我域侧那 2s 不会被共享层的 cap 顶掉** ⇒ ⇒ **域侧那层保留，不撤**。
  - ⚠️ **服务端侧同形（同样已核实）**：`RpcInterceptor.java:302-303`（服务端预算 = `timeout` = 2s）＋ **`:304`（`effectiveTimeoutNanos = Math.min(serverBudgetNanos, clientDeadlineRemainingNanos())` ⇒ min(服务端 2s, 客户端剩余)**）＋ `:317`（三项皆无 ⇒ 不绑载体）⇒ ⇒ 与 Go 的服务端 `context.WithTimeout(ctx, timeout)`（父 ctx 已带客户端 deadline ⇒ 取更早者）**同形** ⇒ ⇒ **本条「形态① 两侧同形」的结论被进一步加强**。
  - ⚠️ **回跑口径（写死，别踩）**：G1 **不能**在自己的工作树合入 `3d5b4924`（`service/common/**` **不在 G1 写权面** ⇒ 合入会让 `check-wave-lane-ownership.py` 判越界）⇒ ⇒ **本条的 3 个域侧守门复跑必须由合并收口执行者在主干上做**（已随交接单上报）。
- ⚠️ **Go 侧客户端 2s 与服务端 2s 是两个独立字段**（`zrpc/config.go:31` 与 `:46`，都默认 2000，**互不联动**）⇒ ⇒ **不可互推**（已同步 G4，建议其 P2-T04 判据把两个数分开写）。
---

> ⚠️ **以下内容原位于本文件中部 —— 2026-10-30（G1 lane）移到末尾**
> 理由 = **零行位移**：`docs/RoundPlan/_unresolved.md` 与 `docs/DRIFT/_cross/grading/*` 里有指向本文件的
> **硬编码 `文件:行号` 指针** ⇒ ⇒ 派生层按**纯行号算术**解析 ⇒ ⇒ 中部插一行 ⇒ 指针解析到别的条目
> ⇒ ⇒ 「排期指针 / 证据缺口组」的成员被静默改写 ⇒ ⇒ **派工面被改掉**。
> ⇒ ⇒ 纪律：**新增内容一律放文件末尾**；要改中部 ⇒ 用「**就地替换**」（行数不变）而不是「插入新行」。
> ⇒ ⇒ 判据：`python3 tools/ledger-derive/check-pointer-consistency.py`（派生层输入完整性棘轮）。

| DRIFT-FOLLOW-014 | 出向 user-rpc **缺 2s 客户端超时** ⇒ 失败只能由**服务端**兜底 ⇒ 失败**归因**与**收口时刻**与 Go 不同 | **可观察（失败归因）** | ✅ **已修（2026-10-30 · G1 lane · 域侧自修）** —— 客户端侧 2s deadline 已补（`service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/UserRpcClient.java:75`）⇒ 守门 `UserRpcClientDeadlineCapTest`（**3 条**，含**真发一次调用**）＋ **变异 M4 实测红**（拿掉 cap ⇒ 挂死 ⇒ `@Timeout` 触发）⇒ 证据强度 **L3**（未做双侧活体对拍 ⇒ **不是 L4**）⇒ 详见下方 §「DRIFT-FOLLOW-014」|

## 载体守门补网（2026-10-31 · G1 lane）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选 ⇒ 也不劫持任何既有编号的 `domain` / `wave` / `basis`。
> ⚠️ **本小节不重登记** ⇒ 本体仍是本文件既有的 `DRIFT-FOLLOW-006`（`:20`）⇒ ⇒ **销账时不要当新条目**。
> ⚠️ **本小节只登记「守门」这一件事** —— 载体本身（`pom.xml:216` 的 `spring-boot-starter-web` ＋ `application.yml:142` 的 `server.port` ＋ `:260` 的 `include`）是 2026-10-29 落地的，**本轮没动**。

**一、为什么这条守门比新增 user-rpc / admin-rpc 的两条更该先有**

- follow-rpc 是本域**唯一早就存在载体**的 rpc 模块（`DRIFT-FOLLOW-006` · 2026-10-29 落地）⇒ ⇒ 但它此前**零守门** ⇒ ⇒ 「唯一已存在的载体却没有网」⇒ ⇒ 载体哪天掉了 / 暴露面被谁改坏 / 路径不再是抓取口，都**没人拦得住**
- ⇒ ⇒ 本轮补的就是这张网（**不改载体实现**，只加守门 + 端口设施）

**二、落码清单（域侧自修 · 全部在 G1 写权面）**

| 落点 | 文件与行号 | 内容 |
|---|---|---|
| 载体守门 | `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcPrometheusCarrierGateTest.java` | **6 条**：载体存在 / 暴露面真抓得到 / 指标名与 Go 同形 / 标签集与 Go 同形 / **api 侧中间件不在容器** / **rag.side=rpc 已声明**（新增 2 条见文末小节） |
| 端口设施 | `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/support/RealTomcatPort.java` | 域内固定段 **22360–22364**（禁 OS 盲选口；与本模块 gRPC 段 `22320–22339` 及 follow-api 的 Tomcat 段 `22340–22359` 均不重叠） |

**三、实测抓到的真实输出（本轮真跑，逐字记录）**

- `follow_rpc_request_total{method="Follow",result="success"}` ⇒ 与 Go `service/follow/rpc/internal/metrics/metrics.go:13-17` 的 `Subsystem:"follow_rpc"` + `Name:"request_total"` + `labels=[method,result]` **逐字同形**（且 Micrometer **没有**再追加一个 `_total`）
- `follow_rpc_request_duration_seconds_bucket{method="Follow",le="0.005"}` 起，桶边界为 `.005 .01 .025 .05 .1 .25 .5 1.0 2.5 5.0 10.0` + `+Inf` ⇒ 与 Go 侧 `prometheus.DefBuckets`（`client_golang@v1.23.2/prometheus/histogram.go:271`，由 `metrics.go:23` 的 `Buckets: prometheus.DefBuckets` 消费）**逐条同形**
- `follow_rpc_recommendation_size`（gauge，无 label）⇒ 与 Go `metrics.go:39-42` 同形
- ⚠️ **RAG 多出来的**：`follow_rpc_request_duration_seconds_max` ⇒ Go 侧没有这条 ⇒ 属**已登记的 P3/D 类残余**（本文件既有「8 条 `_max` 增项」那条），**不在本轮范围**

**四、实测踩到的两个坑（写下来免得下一个人再踩）**

- 坑一：follow-rpc 用的是 **Flyway**（users 域两个 rpc 用 SchemaInitializer，故那边没这问题）⇒ ⇒ `FlywayAutoConfiguration` 会在上下文启动时**真连 PG** ⇒ ⇒ 本机没有 `postgres` 主机 ⇒ `UnknownHostException` ⇒ ⇒ 载体守门必须**排除** `FlywayAutoConfiguration`（⚠️ 这**不改变**生产行为，生产仍会跑迁移）
- 坑二：devh 的 `GrpcServerFactoryAutoConfiguration` 的 `@ConditionalOnClass` 认 shaded Netty ⇒ ⇒ follow-rpc 的 classpath 上有（它比 users 域两个 rpc 多了 `grpc-client-spring-boot-starter`）⇒ ⇒ 排除 `GrpcServerAutoConfiguration` **还不够** ⇒ ⇒ **还得排除它**（否则 `GrpcServiceDiscoverer` 缺失 ⇒ 上下文起不来 ⇒ 4 条用例全 Error）

**五、变异实测（判别力）**

- 变异 M1：`include` 里去掉 `prometheus` ⇒ **3 条转红**（端点 404 ＋ 指标缺失）
- 变异 M2：pom 里去掉 `spring-boot-starter-web` ⇒ **4 条转 Error**（上下文起不来）
- 变异 M3：写回 `management.server.port: -1` ⇒ **3 条转红**
- ⇒ ⇒ 三个变异**各自**精确转红、还原后复绿 ⇒ ⇒ **守门不是假绿**

**六、证据强度与诚实边界**

- 证据强度 **L3**（真 Tomcat ＋ 真 HTTP 抓取 ＋ 真 Prometheus 文本 ＋ 真指标名/标签/桶边界核对）—— **不是 L4**
- ⚠️ **未做**「业务启动类在真 PG/Redis 下真起一遍」⇒ ⇒ **L4 活体验证仍缺**（需 Docker；本机 Docker 全停）
- ⚠️ **未做** Go 侧真进程的同请求抓取比对（本文件 `:20` 记的那次活体对拍 `tools/metrics-probe/follow/run.sh` EXIT=0 是 2026-10-30 那一轮，与本轮守门是**两件事**）
- ⚠️ 守门用**独立最小上下文**（**无 `@ComponentScan`**）⇒ ⇒ 不被 PG/Redis 绑架 ⇒ ⇒ 测的正是「载体 + 暴露面 + 指标定义」三件事
- ✅ **「13 个 api 侧 web Bean 不在容器里」已全部落**（**2026-10-31 · 合并主干后**）：10 个组件扫描类 ＋ 3 个自动配置面（`GoStyleJsonContentTypeAdvice` / `GoStylePathNormalizingAutoConfiguration` / `GoStyleBreakerRouteTemplateAutoConfiguration`）⇒ ⇒ 判据按**结果**写「不在容器」⇒ ⇒ **两侧都绿** ⇒ ⇒ 那 3 个自动配置面此前「交不了」的原因是**本分支基线尚未含**共享层的 `@ConditionalOnApiSide`（`53bc05ec`）⇒ ⇒ **合并主干后即可写** ⇒ ⇒ 原写「跨分支不稳定 ⇒ 不给恒红守门」**已过期**（见文末小节）

**七、端口段表需扩（共享层写权面 ⇒ 已登记转派）**

- 本轮新增的 Tomcat 子段：`user-rpc` **22290–22294** · `admin-rpc` **22295–22299** · `follow-rpc` **22360–22364**
- ⇒ ⇒ users 域的**脚手架段**（域级守门 `BlindPortGateTest` ⑤ 造「被占」样本用）原为 `22280–22299` ⇒ ⇒ 与前两个子段**重叠** ⇒ ⇒ 已就地收窄为 **`22280–22289`**
- ⇒ ⇒ 该收窄**属共享层段表**（`docs/DRIFT/global/vs-common.md` §4.10.19.3）⇒ ⇒ **G4 写权面** ⇒ ⇒ **已登记并转派**，本域只按已分配号取，**不改共享层台账**
- ⇒ ⇒ 域级守门 `BlindPortGateTest` ② 的「端口类个数」期望值已如实从 **11 改为 12**（新增 follow-rpc 的 Tomcat 口）⇒ ⇒ **是扩覆盖面，不是削弱判据**

---

## 载体运行期守门扩面：rpc 侧隔离（新增 2 条）

**一、为什么这条现在才必须断言（背景）**

- 本域 rpc 原先是**纯 gRPC 进程** ⇒ classpath 上**没有** spring-webmvc ⇒ ⇒ 那 10 类 api 侧中间件**连类都加载不了**（`NoClassDefFoundError`）⇒ ⇒ 「不在容器」当时是**免费的**
- 载体任务给 rpc 加了 `spring-boot-starter-web` ⇒ ⇒ classpath 上**有了** webmvc ⇒ ⇒ 这 10 类**技术上可以活** ⇒ ⇒ 「它们仍然不在」才变成一个**需要守门的事实**

**二、新增 2 条（判据按「结果」写，不按「机制」写）**

| 用例 | 判据 | 为什么这个写法跨分支稳 |
|---|---|---|
| `apiSideMiddlewareIsAbsentFromRpcContainer` | **13 个** api 侧 web Bean 的 `getBeanNamesForType` **全为空** | **本树已含** `@ConditionalOnApiSide`（`53bc05ec`）＋ `rag.side: rpc` ⇒ ⇒ 13 类**分两批挡**（10 个扫描类靠域侧约定 ＋ 3 个自动配置面靠 `@ConditionalOnApiSide`）⇒ ⇒ **两侧同机制、结果相同** ⇒ ⇒ 断言「结果」⇒ ⇒ 两侧都绿 ⇒ ⇒ **且有牙**（变异 `rag.side → api` ⇒ 精确转红，见文末小节） |
| `rpcSideFlagIsDeclared` | 读**域内真实 yml**（本测试**不自带** `rag.side`）⇒ 值 = `rpc` | 防「那行被删 / 被改成 api」；✅ **合并主干后它已有消费者**（共享层 `@ConditionalOnApiSide` = `@ConditionalOnProperty(name="rag.side", havingValue="api")` ⇒ `53bc05ec`）⇒ ⇒ 这行 yml **从此是必做项**，不再是防御性配置 |

被断言的 **13 类** = **10 个组件扫描类**（`@Component` / `@Configuration`）：`GoStyleFormLimitWebMvcConfig` · `GoStyleFormValueLimitInterceptor` · `GoStyleJsonBodyConverterConfig` · `GoStyleTomcatParameterLimitCustomizer` · `GoStyleTraceparentFilter` · `GoStyleBreakerFilter` · `GoStyleCpuSheddingFilter` · `GoStyleMaxBytesFilter` · `GoStyleMaxConnsFilter` · `HttpMiddlewareFilter` ＋ **3 个自动配置面**（走 `AutoConfiguration.imports` ⇒ 与组件扫描解耦）：`GoStyleJsonContentTypeAdvice` · `GoStylePathNormalizingAutoConfiguration` · `GoStyleBreakerRouteTemplateAutoConfiguration`

**三、Go 出处（逐行读过）**

- `service/follow/rpc/follow.go:37` ⇒ `s := zrpc.MustNewServer(c.RpcServerConf, ...)`
- ⇒ ⇒ Go 侧 rpc 是 `zrpc.MustNewServer` ⇒ ⇒ **零 rest 中间件**（`rest.With*` 全在 api 侧 `service/<域>/api/internal/handler/routes.go`）⇒ ⇒ RAG 侧 rpc 容器里**不得出现** api 侧中间件

**四、跨分支差异（同一句断言、两种机制、两种含义）**

- **本树已含** `@ConditionalOnApiSide`（`53bc05ec`，**2026-10-31 合并主干后**）＋ `rag.side: rpc`（`e86ec93b`）⇒ ⇒ **13 类全部不注册** ⇒ ⇒ rpc 侧 web 容器上**零个 api 侧中间件** ⇒ ⇒ **载体任务没有引入偏离**，与 Go 的 `zrpc.MustNewServer` 同形
- **13 类分两批挡**（两批机制不同、结果相同）：
  - 10 个**组件扫描类** ⇒ 靠**域侧约定**不活（`FollowRpcApplication` 的 `@ComponentScan` 刻意不扫 `com.rag.common`，启动类注释写明不能加）⇒ 不注册 —— **第一道**
  - 3 个**自动配置面** ⇒ 走 `AutoConfiguration.imports` ⇒ ⇒ 「不扫 `com.rag.common`」**挡不住**它们 ⇒ ⇒ 靠 `@ConditionalOnApiSide` ＋ `rag.side: rpc` 挡 —— **第二道** ⇒ ⇒ 两道叠加 ⇒ ⇒ 「13 类全不在」这一条**跨分支稳定**（主干与本树同机制）⇒ ⇒ 原写「跨分支不稳定 ⇒ 不给恒红守门」**已过期** ⇒ ⇒ **守门已落**（见文末小节）
- ⇒ ✅ **该边界已消解**：合并主干后本树与主干**同机制**（13 类全不注册）⇒ ⇒ **载体功能结论**与**「中间件洁净度」结论都可外推** ⇒ ⇒ ⚠️ 仅保留一条边界：读数依赖 Docker 容器（条数口径见文末小节）

**五、那 3 个面是谁（自读主干 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，共 7 项）**

- `GoStyleJsonContentTypeAdvice` —— `@AutoConfiguration` ＋ `@ConditionalOnWebApplication(SERVLET)`
- `GoStylePathNormalizingAutoConfiguration` —— 同上
- `GoStyleBreakerRouteTemplateAutoConfiguration` —— `@AutoConfiguration` ＋ `@ConditionalOnClass(RequestMappingHandlerMapping)`
- ⚠️ **前两个的开关就是「有没有 web 容器」⇒ ⇒ 载体任务正是把它们从「不活」翻成「活」的那个开关**（第三个靠 classpath 有没有 spring-webmvc ⇒ 同一个开关）
- ⇒ ⇒ **曾登记为「载体引入的行为变化」**：Go 侧 rpc 的 16065 是 go-zero DevServer（`devserver.StartAgent` ⇒ 独立端口 `http.ListenAndServe`，**不跑 rest 中间件链**）⇒ ⇒ RAG 侧 rpc 的 web 容器上会多挂 3 层 api 侧中间件 ⇒ ⇒ ⚠️ **2026-10-31 合并主干后该登记已消解**：共享层的 `@ConditionalOnApiSide`（`53bc05ec`）＋ `rag.side: rpc` ⇒ ⇒ **这 3 个面在 rpc 侧也不注册** ⇒ ⇒ **不是偏离** ⇒ ⇒ **本域不自行签字，也无需登记**

**六、变异实测（合并主干前 · 三域同款实现 ⇒ 同款判据；实测在 user-rpc 单跑完成，follow-rpc 跑同一批断言全绿；⚠️ 其中第 3 条在合并主干后已过期 ⇒ 见文末小节）**

| 变异 | 实测结果 | 读法 |
|---|---|---|
| `@Import` 一个**有依赖**的 api 侧类（`GoStyleBreakerFilter`） | **6 条全 Error**（上下文起不来：`No qualifying bean of type 'GoStyleProtectionProperties'`） | ⇒ **api 侧中间件一旦进 rpc 容器，rpc 域直接起不来** ⇒ 比「断言红」更硬 |
| `@Import` 一个**无依赖**的 api 侧类（`GoStyleTraceparentFilter`） | **6 条里 1 条 Failure / 0 Error**（唯一红的是 `apiSideMiddlewareIsAbsentFromRpcContainer`） | ⇒ 新断言**有牙**：**精确转红**，不是上下文挂掉 |
| （负面实测）把 yml 的 `rag.side` 改成 `api` | `rpcSideFlagIsDeclared` **红**；`apiSideMiddlewareIsAbsentFromRpcContainer` **仍绿** | ⇒ **实测坐实边界**：**合并主干前**本分支上护栏是「不扫 common」不是 `rag.side` ⇒ ⇒ **这条类断言在合并主干前抓不到 `rag.side` 回归**；⚠️ **合并主干后已过期**（同一变异现在**类断言也红** ⇒ 有牙）⇒ ⇒ **现读数见文末小节** |

⇒ ⇒ 三个变异**全部还原**（`git diff` 只剩 3 个测试文件的 +105 行新增，三个 yml 均无 diff）⇒ ⇒ ⚠️ **合并主干后重测的第四个变异（三域同跑）也已还原**（见文末小节）

**七、回归（2026-10-31 · 三域同跑）**

- ⚠️ **口径订正（2026-10-31 · 合并主干后复跑）**：上面那个 **379** 是 **surefire XML 读数**，**随 Docker 的故障形态变化** ⇒ ⇒ **不能当覆盖面用**（详见文末小节）⇒ ⇒ **覆盖面口径 = 源声明测试方法数**：基线 `7bc9df3c` **445** ⇒ 载体扩面 `e61360d9` **451**（**+6** = 三域各 2 条，数目严丝合缝）⇒ ⇒ 合并主干 `27fb2a6c` **仍是 451** ⇒ ⇒ **一条未丢**（三域测试类 32+29+17 = **78 类**，全程未变）
- ⇒ ⇒ 证据强度 **L3**（真容器 + 真 HTTP 抓取已验；真 PG/Redis 那层**未跑** ⇒ 到不了 L4）

---

## 合并主干后的判别力实测 ＋ 回归条数口径订正

**一、这一节解决两件事**

- ① **判别力**：「13 类 api 侧 web Bean 全不在容器里」这条断言，在合并主干（`27fb2a6c`）后**到底有没有牙**？⇒ ⇒ 实测（第四个变异）
- ② **条数口径**：上文那个 **379**（以及本轮中途观察到的 **299**）**能不能当覆盖面用**？⇒ ⇒ **不能** ⇒ ⇒ 订正

**二、第四个变异：`rag.side: rpc` → `api`（三域同跑，2026-10-31 02:0x–02:1x）**

| 变异 | 实测结果 | 读法 |
|---|---|---|
| 三域 yml 的 `rag.side` 改成 `api` | **三域各 `Tests run: 6, Failures: 2, Errors: 0`** —— ① `rpcSideFlagIsDeclared` 红（`expected: "rpc" but was: "api"`）② `apiSideMiddlewareIsAbsentFromRpcContainer` 红（`Expecting empty but was: ["com.rag.common.http.GoStyleJsonContentTypeAdvice"]`） | ⇒ ⇒ **与合并主干前那个负面实测结论相反**：合并前该变异只让「配置断言」红、**类断言仍绿** ⇒ ⇒ 合并后**类断言也红** ⇒ ⇒ 「13 类全不在」**有牙**（不是恒绿）⇒ ⇒ 且 **0 Error** ⇒ ⇒ 红在**目标断言**，不是上下文起不来 |

- ⇒ ⇒ **机制侧的旁证**（共享层，非本域）：`GoStyleApiSideMiddlewareActivationGateTest` **5 条全绿**（形态③ vs 形态⑤ **只差 `rag.side` 一个变量**）⇒ ⇒ `@ConditionalOnApiSide` 这个**元注解真生效**（AGENTS.md §3.3：注解存在 ≠ 机制生效）
- ⇒ ⇒ **三个 yml 全部 `git checkout` 还原**（`side: rpc`）⇒ ⇒ 还原后复跑 **三域各 6 条全绿**

**三、⚠️ 同一个变异、两个结论 —— 判别力不能跨基线继承**

| 基线 | `rag.side → api` 的实测 | 结论 |
|---|---|---|
| 合并主干**前**（本分支基线无 `@ConditionalOnApiSide`） | 只有配置断言红；类断言**仍绿** | ⇒ 当时的边界**准确**：类断言抓不到 `rag.side` 回归 |
| 合并主干**后**（本树已含 `53bc05ec`） | 配置断言 **＋ 类断言** 都红 | ⇒ 当时的边界**已过期**：类断言**有牙** |

- ⇒ ⇒ **教训**：同一句断言的判别力**取决于基线** ⇒ ⇒ 「这条断言有没有牙」**不能跨基线继承** ⇒ ⇒ 必须**在当前基线上重测**（本轮已重测）

**四、⚠️ 回归条数口径订正（上文那个「379 条」不能当覆盖面用）**

- **覆盖面口径（环境无关，可跨 commit 比）= 源声明测试方法数**：`7bc9df3c`（基线）**445** ⇒ `e61360d9`（载体守门扩面）**451**（**+6** = 三域各 2 条，数目严丝合缝）⇒ ⇒ `27fb2a6c`（合并主干后）**仍是 451** ⇒ ⇒ **一条未丢**（三域测试类 32+29+17 = **78 类**，全程未变）
- **执行条数口径（环境相关）**：本轮 Docker 恢复后复跑 = **三域合计 417 条**（user-rpc **181** ＋ admin-rpc **139** ＋ follow-rpc **97**）· **Failures = 0** · **Errors = 0** · **Skipped = 0** ⇒ ⇒ **BUILD SUCCESS**
- ⚠️ **为什么之前会看到 379 / 299 两个数**：容器在 `@BeforeAll` 起不来 ⇒ ⇒ **整类 N 个方法折叠成 1 条 error** ⇒ ⇒ 总条数**变小** ⇒ ⇒ **两个读数都是「折叠后」的读数，口径不同 ⇒ 不可相减**
- ✅ **交叉验证**：把折叠掉的类按**源方法数**展开 ⇒ 得 **417**（181 / 139 / 97）⇒ ⇒ 与 Docker 恢复后的**真实读数逐条吻合** ⇒ ⇒ **折叠假说成立**（不是「丢了 80 条测试」）
- ⚠️ **教训**：「测试条数下降」这个现象**先分清是「覆盖面变小」还是「读数折叠」** ⇒ ⇒ 判据 = **源声明方法数**（与环境无关）＋ **XML 读数**（与环境相关）**两个数一起报** ⇒ ⇒ 只报一个数会误判

**五、⚠️ Docker 故障的根因（不是「整个停了」，是「存储层损坏」）**

- 现象：容器类**类级 error**，消息是 `Can't get Docker image: RemoteDockerImage(imageName=postgres:16-alpine, …)` ⇒ ⇒ **拉镜像阶段**失败
- 根因：Docker Desktop 的 containerd content store **blob I/O 错误** —— `blob sha256:16bc… expected at /var/lib/desktop-containerd/daemon/io.containerd.content.v1.content/blobs/sha256/16bc…: open …: **input/output error**` ⇒ ⇒ **宿主磁盘 94% 满**（当时剩 763 MiB）⇒ ⇒ 写不进 blob ⇒ ⇒ 镜像起不来
- ⚠️ **与旧记的消息不是同一阶段**：旧记 `Previous attempts to find a Docker environment failed` = **守护进程完全不可达**；本轮 = **守护进程活着、镜像拉不动** ⇒ ⇒ 「Docker 整个停了」这句**不够准** ⇒ ⇒ 应写成「Docker 存储层因磁盘满损坏」
- ✅ **已自愈**：磁盘从 763 MiB 回到 **65 GiB**（15%）⇒ ⇒ Docker 守护进程 API 恢复 200（29.2.1 · 62 容器）⇒ ⇒ **本轮复跑全部转绿** ⇒ ⇒ 全反应堆 **0 Failures / 0 Errors**
- ⚠️ 共享层那 1 个 Error（`MybatisPlusInterceptorChainGateTest`）= **时序artifact**（它在 02:13 跑，Docker 尚未恢复）⇒ ⇒ 单独复跑 **3 条全绿** ⇒ ⇒ **已销号**（共享层写权面，本域只报不改）

**六、✅ L4 活体验证（本轮达成；上文那些「未做」里的一部分现在做完了）**

- **三域业务启动类在真 PG/Redis 下真起了一遍**：user-rpc **Tomcat 22290** · admin-rpc **Tomcat 22295** · follow-rpc **Tomcat 22360** ⇒ ⇒ 三个口都落在**域内固定段**（222xx / 223xx）⇒ ⇒ 本轮只证「**在真 PG/Redis 下起得来**」
- 本轮**真容器启动 35 次**（`postgres:16-alpine` / `redis:7-alpine`）⇒ ⇒ 三域含容器的用例**本轮真跑且全绿**（此前是「Docker 停 ⇒ 类级 error ⇒ 折叠」）
- ⚠️ **仍未覆盖（不得写成已闭环）**：① 域侧「logout 走的是共享层那个 Redis 期限代理」的**装配接线**（域侧 `AdminLogoutDeadlineHandoffTest` 用 **Mockito 替换数据层** ⇒ ⇒ **只证控制流，不证代理接线**）② 真 Redis 上「**预算耗尽 ⇒ 写不发生**」的**活体**行为（共享层的 `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest` 本轮 **3 条全 skipped** ⇒ ⇒ 需要外部 Redis 地址）③ **RAG↔Go 双侧活体对拍**
- ⇒ ⇒ 故 **E66 那格（admin 域）仍是「机制已落 · 活体验证未做」** ⇒ ⇒ **不改**
