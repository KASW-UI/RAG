<!-- @move AGENTS.md L1841-L1855 · §3.3.3 Boot 4 把 Kafka 自动配置搬出 autoconfigure ⇒ @KafkaListener 静默失效 -->
Spring Boot 4 把 Kafka 自动配置**整体搬出了 `spring-boot-autoconfigure`**
（实测 `spring-boot-autoconfigure-4.0.8.jar` 内 Kafka 相关类数量 = **0**），
改由独立模块 **`org.springframework.boot:spring-boot-kafka`** 提供，包名也从
`org.springframework.boot.autoconfigure.kafka.*` 移到 `org.springframework.boot.kafka.autoconfigure.*`。

⇒ 只引 `org.springframework.kafka:spring-kafka` 时：
**没有 `ConsumerFactory` Bean、没有 `KafkaListenerAnnotationBeanPostProcessor`**
⇒ `@KafkaListener` 被**完全静默忽略**：不报错、上下文照常启动、容器正常起来、**一行消息都不消费**。

这比 §3.3 已有的 H2（`@Component` ≠ 自动注册，表现为 Bean 不存在）**更难发现**：
H2 至少还有「类存在但 Bean 不存在」这条线索；本条是**连类都不在 classpath 上**，
而源码里那个 `@KafkaListener` 看起来完全正常。


> ⚠️ 本案例对应的纪律条文（「凡用 `@KafkaListener` 必须有装配层测试」等三条强制）
> **留在 AGENTS.md §3.3**，此处只留证据。

---
---

# like-rpc 专属教训（2026-10-23 组件级复核追加）

> 上面的 Boot 4 Kafka 案例是**通用机制**说明。本节记录**在 like-rpc 上真实踩到的**坑，
> 每条写成「现象 → 根因（框架机制）→ 为什么测试抓不到 → 下次怎么防」。
> 全部来自**第一次真正启动这个应用**（真 PG 16 + 真 Redis 7 + 真 Kafka + 真 Nacos）。
> 详细对照见 [`docs/DRIFT/like/rpc.md`](../../DRIFT/like/rpc.md) 第二轮。

---

## P-01 「静默失效」在本组件上其实会**硬失败** —— 通用案例必须按组件复核 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-01` ⇒ **新号**：`PIT-LIKE-17`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— like-rpc 已显式补 `spring-boot-kafka`（Boot 4 把 Kafka 自动配置拆成独立模块）⇒ 「缺模块 ⇒ 缺 `KafkaTemplate` Bean ⇒ 上下文起不来」这个触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/pom.xml:158`（`org.springframework.boot:spring-boot-kafka`）+ `:143`（`spring-kafka`） ⇒ **证据强度：pom 行号**。

| | 内容 |
|---|---|
| **现象** | Boot 4 把 Kafka 自动配置搬出 `spring-boot-autoconfigure`（实测 `spring-boot-autoconfigure-4.0.8.jar` 内 Kafka 相关类 = **0**），改由独立模块 `org.springframework.boot:spring-boot-kafka` 提供。`like-rpc/pom.xml` 只引了 `org.springframework.kafka:spring-kafka` ⇒ `KafkaAutoConfiguration` / `KafkaAnnotationDrivenConfiguration` **都不在 classpath 上**。<br>**实测结果不是「静默不消费」，而是 `Spring 上下文直接启动失败`：**<br>`NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.kafka.core.KafkaTemplate<String, String>' available` |
| **根因（框架机制）** | 通用案例说「`@KafkaListener` 被完全静默忽略」——**那是对「只写注解、别处不引用 Kafka Bean」的组件而言**。<br>like-rpc 恰好把 Kafka Bean 当**必需**依赖注入：<br>① `LikeHotEventOutboxRelay:42` 构造参数 `KafkaTemplate`；<br>② `LikeDomainService.KafkaLikeMsgPublisher:990` 构造参数 `KafkaTemplate`；<br>③ `LikeTaskProgressPublisher:48` 构造参数 `KafkaTemplate`；<br>④ `LikeRpcApplication:113` 的 `kafkaListenerContainerFactory(ConsumerFactory)` —— `ConsumerFactory` 同样由 `KafkaAutoConfiguration` 提供。<br>⇒ **缺模块 ⇒ 缺 Bean 定义 ⇒ 构造注入失败 ⇒ refresh 失败**。机制是「静默」的，**后果不是**。 |
| **为什么测试抓不到** | 31 条测试**全是纯 Mockito 或纯 in-process gRPC**，**零** `@SpringBootTest` / `ApplicationContextRunner`。它们连 Bean 定义都不解析 ⇒ 「`KafkaTemplate` 这个 Bean 存不存在」这个问题**根本没被问过**。首轮已把这条记成 DRIFT-LIKE-006（UNKNOWN），本轮才第一次有答案。 |
| **下次怎么防** | ① **先让模块起一次**（哪怕 `ApplicationContextRunner` 空跑）——「能启动」是所有语义验证的前置条件，不是可选项；<br>② 凡用 `@KafkaListener` / `KafkaTemplate` / `ConsumerFactory`，**先 `mvn dependency:tree` 确认 `spring-boot-kafka` 在不在**，别看「`spring-kafka` 在」就以为完事（`spring-kafka-4.0.7.pom` 对 `spring-boot-kafka` 引用数 = **0**）；<br>③ 装配层测试要断言 **「listener 进了容器的 registry」**（`KafkaListenerEndpointRegistry` 存在且 container 数 > 0），不是「listener 方法存在」。 |

## P-02 Boot 4 拆模块后，**第三方 starter 的 `@AutoConfigureAfter` 会静默失效** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-02` ⇒ **新号**：`PIT-LIKE-18`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 第三方 starter 的悬空 `@AutoConfigureAfter` 已被**共享层排序桥**接住 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：桥 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` · 登记 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` · 守门 `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java` ⇒ **证据强度：源码行号 + 守门在仓**。

| | 内容 |
|---|---|
| **现象** | `mybatis-plus-spring-boot3-starter:3.5.17` 是 **Boot 3 专用** starter。它的 `MybatisPlusAutoConfiguration` 上写着 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)`；而 **Boot 4.0.8 已把该类搬到 `org.springframework.boot.jdbc.autoconfigure`（`spring-boot-jdbc` 模块）**，旧 FQN 在全部 Boot 4.0.8 jar 里**已不存在**。<br>⇒ 排序保证丢失 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 在 DataSource Bean 注册**之前**求值 ⇒ **不匹配** ⇒ 自动配置被**静默跳过**，**一行错误都没有**。<br>症状：`Error creating bean with name 'likeOutboxEventMapper' … Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` |
| **根因（框架机制）** | `@AutoConfigureAfter` 里写的是**类名字符串**，Boot 用 ASM 元数据排序、**不加载类** ⇒ 目标类不存在时**不报错、不告警**，只是排序约束消失。后果是**被依赖方先于依赖方求值**，条件自然不成立。 |
| **为什么测试抓不到** | 纯 Mockito 单测**根本不会加载自动配置** ⇒ 条件求值时序永远看不到。`--debug` 的 ConditionEvaluationReport 是唯一能看到真相的地方（`MybatisPlusAutoConfiguration: Did not match: - @ConditionalOnSingleCandidate (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans`）。 |
| **下次怎么防** | ① 升级到 Boot 大版本后，**逐个检查第三方 starter 的 `@AutoConfigureAfter/@AutoConfigureBefore` 目标类是否还在**；<br>② 启动失败时**第一时间跑 `--debug`** 看 ConditionEvaluationReport，不要只看最后一个异常；<br>③ 「类在 classpath 上」≠「自动配置生效」——必须看 **ConditionEvaluationReport 的 Did not match 原因**。 |

## P-03 Boot 4 的 JSON 默认切到 Jackson 3，**Jackson 2 没有自动配置了** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-03` ⇒ **新号**：`PIT-LIKE-19`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— like-rpc 现在**自己声明** Jackson 2 的 `ObjectMapper` Bean ⇒ 「没有任何自动配置能产出 Jackson 2 Bean」这个触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/Jackson2ObjectMapperConfiguration.java:4,20` ⇒ **证据强度：源码行号**。

| | 内容 |
|---|---|
| **现象** | like-rpc 有 5 个类注入 `com.fasterxml.jackson.databind.ObjectMapper`（Jackson 2）。启动报：`No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available`。 |
| **根因（框架机制）** | Boot 4 把 JSON 自动配置搬进 `org.springframework.boot:spring-boot-jackson`，而它的 `JacksonAutoConfiguration` 构造的是 **Jackson 3** 的 `tools.jackson.databind.ObjectMapper`（见 `spring-boot-jackson-4.0.8-sources.jar` 的 import 段）。**Boot 4 根本没有 `spring-boot-starter-json`**（本地仓库只有 2.5.14 / 3.2.5 / 3.5.11）⇒ **没有任何自动配置能产出 Jackson 2 的 `ObjectMapper` Bean**。<br>⚠️ 陷阱：加了 `spring-boot-jackson` **也解决不了** —— 它给的是 Jackson 3 的 Bean，类型不匹配。 |
| **为什么测试抓不到** | 测试里注入的 `ObjectMapper` 是**测试自己 new 的**或 Mockito mock ⇒ 从不经过自动配置。 |
| **下次怎么防** | ① **Boot 4 升级专项检查表**里加一行：「本模块用的是 Jackson 2 还是 Jackson 3？」——两者的**默认行为不同**（HTML 转义、未知字段策略、`@JsonPropertyOrder` 都要重验）；<br>② 若继续用 Jackson 2 ⇒ **必须自己声明 `@Bean ObjectMapper`**（并显式配 `FAIL_ON_UNKNOWN_PROPERTIES` 等与 Go 对齐的策略）；<br>③ 跨语言哈希（如 `payload_hash`）依赖序列化字节 ⇒ 换库前先跑**双侧字节对拍**，不要靠「读代码看着一样」。 |

## P-04 服务发现 URI 的**斜杠个数**是硬约束，而且**没有编译期检查** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-04` ⇒ **新号**：`PIT-LIKE-20`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— like-rpc 的 `discovery:` 地址已改成**三斜杠** ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/main/resources/application.yml:214,217,218`（「地址必须是三斜杠 `discovery:///message-rpc`」）⇒ **证据强度：配置行号**。

| | 内容 |
|---|---|
| **现象** | `like-rpc/application.yml:132` 写 `address: discovery://message-rpc` ⇒ 启动报：`IllegalArgumentException: Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://message-rpc'`（devh starter 3.1.0 `DiscoveryClientResolverFactory:82`） |
| **根因（框架机制）** | devh 的 discovery URI 语法是 `discovery://[authority]/<service-name>` ⇒ **服务名必须在 path 段**，即**三斜杠** `discovery:///message-rpc`。<br>⚠️ 本仓**其它模块全是对的**（`admin-api:56,65,71`、`like-api:104`、`hot-api:53,59`、`message-api:52`）⇒ 这类错误是**逐模块复制粘贴时漏改**的高发点。 |
| **为什么测试抓不到** | gRPC client 的 channel 是在**Bean 创建期**建的，而纯 Mockito 单测**从不创建 channel**。 |
| **下次怎么防** | ① 复制 `grpc.client.*.address` 时**逐条核对斜杠个数**；<br>② 加一条**静态校验脚本**：扫全仓 `discovery:` 地址，断言**一律三斜杠**（可挂进 `tools/difftest/`）；<br>③ ⚠️ 同款两斜杠还出现在 `service/message/message-rpc/application.yml:84,89` 与 `service/article/article-rpc/application.yml:110,116` ⇒ **这三个组件各自开单子，不要只改 like-rpc**。 |

## P-05 「注释里写着 Go 的行为」**不等于** Go 的行为 —— 又一条 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-05` ⇒ **新号**：`PIT-LIKE-21`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 迁移脚本已按 Go 侧真实类型对齐（不再照抄那条错的 GORM 断言）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql`（按 Go 侧 `integer` / `smallint` 对齐）· 原始错断言见本条正文 ⇒ **证据强度：迁移文件在仓**。

| | 内容 |
|---|---|
| **现象** | `V4__create_like_domain_fact_outbox.sql` 的注释断言：「GORM 对『无 size 的整数』统一取 **bigint**（不是 integer / smallint）」。**实测是错的**：GORM 对 `int32`（`field.Size = 32`）产出 **`integer`**。<br>消费点：`gorm.io/driver/postgres@v1.6.0/postgres.go` 的 `DataTypeOf` —— `size<=16→smallint`、`size<=32→integer`、否则 `bigint`；`gorm.io/gorm@v1.31.1/schema/field.go:338-339` 把 `int32` 的 Size 设为 32。<br>⇒ 迁移脚本照着这条错断言把 `delivery_status` / `retry_count` 建成了 `BIGINT`，与 Go 的 `integer`（AutoMigrate）/ `smallint`（Go 手写迁移）**三方不一致**。 |
| **根因（框架机制）** | GORM 的整数→SQL 类型映射**取决于 `field.Size`**，而 `Size` 又取决于 **Go 的具体整数类型**（`int32`→32、`int64`→64），**不是**「有没有 type tag」。tag 只决定「是否显式指定」。 |
| **为什么测试抓不到** | DDL 不在单测覆盖范围；「列类型对不对」只有**真 PG 读列类型**才看得见。 |
| **下次怎么防** | ① 涉及「GORM 会把 X 映射成什么类型」时，**必须去读 `driver/postgres` 的 `DataTypeOf` 消费点**，不能凭直觉，也不能贴 struct 定义行当证据（AGENTS.md §3.3.1）；<br>② 跨语言共表时，把「列类型 + CHECK + 触发器 + 部分索引」列成**逐项 checklist**（Go 手写迁移里的约束常常**不**来自 struct tag）；<br>③ 本项目已有先例：`like_record` 的 `created_at` / `CreateTime` 列名错配（DRIFT-LIKE-001）——**同一张表被两种语言共用时，schema 必须是显式契约，不是「各自 AutoMigrate 的副产物」**。 |

## P-06 真启动一次的成本 ≪ 想象，收益 ≫ 想象 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-06` ⇒ **新号**：`PIT-LIKE-22`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「先让它真的启动一次」今天仍成立，且**本轮尤甚** —— 本轮要改的两域台账里有大量「机制是否生效」的断言 ⇒ 若不真起一次容器，本轮写下的 ✅ 仍可能是假绿 ⇒ **是**。
> **依据**：AGENTS.md §0 第 3 条（「每个模块接手的第一件事仍是『先让它真的启动一次』」）· 本域已落地形态 `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcSpringContextWiringTest.java` ⇒ **证据强度：仓库政策 + 守门在仓**。

| | 内容 |
|---|---|
| **现象** | like-rpc 此前**从未被启动过一次**（首轮 DRIFT-LIKE-006）。本轮第一次启动，**连着撞出 4 个独立硬阻塞**（P-01 ~ P-04），每个都是「上下文起不来」级别。 |
| **根因（框架机制）** | 这不是巧合：**Boot 3 → Boot 4 的模块化迁移**会同时打中「自动配置类搬家」「starter 排序失效」「默认 JSON 库换代」「服务发现 URI 语法」四条线。任何一条都足以让应用**起不来**，而**四条都发生在同一个模块上**，因为这个模块**依赖面最广**（Redis + PG + Kafka + gRPC + Nacos + Jackson + MyBatis）。 |
| **为什么测试抓不到** | 31 条绿测试**没有一条**走 Spring 上下文 ⇒ 它们测的是**业务方法**，不是**应用**。 |
| **下次怎么防** | ① **「先让它真的启动一次」**（AGENTS.md §0 第三类未闭环项）应该排在**任何业务单测之前**；<br>② 启动验证的成本极低：`mvn dependency:build-classpath` + `java -cp ... Main` + 真 PG/Redis 容器，**本轮全程 ≈ 15 分钟**，却换来 4 个 CRITICAL；<br>③ 断言要分层：**类在不在 classpath（`Class.forName`）** → **Bean 在不在容器（`getBean`）** → **机制有没有真生效（registry / 列类型 / 实际写入）**。本轮用的就是这三层。 |

## P-07 只跑 RAG 侧探针，**只能证明「代码写成了这样」** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-07` ⇒ **新号**：`PIT-LIKE-23`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「只跑 RAG 侧探针只能证明『代码写成了这样』」今天仍成立 ⇒ 本轮凡给 DRIFT 条目写 ✅，若只核 RAG 侧源码就必须标「证据强度：单侧」而非「已闭环」⇒ **是**。
> **依据**：AGENTS.md §3.3.3 第 ② 条（「凡写『与 Go 一致』必须有两侧同时跑的探针」） ⇒ **证据强度：仓库政策**。

| | 内容 |
|---|---|
| **现象** | 本轮全部新结论都是 **RAG 侧单侧证据**（真容器启动 / classpath 探针 / 真 PG）。Go 侧本轮**没有**起活体探针。 |
| **根因** | AGENTS.md 硬约束 3：**只跑 RAG 侧的探针不算数** —— 它证明不了「Go 是这样」。 |
| **这次的处理** | 凡 Go 侧结论一律引用**消费点行号**（含 go-zero / go-queue / gorm **依赖源码**的消费点，例如 `kq/pusher.go:123-125`、`driver/postgres` 的 `DataTypeOf`、`gorm/callbacks/update.go:201,238`），并在 §9 逐条标注哪些结论**只到静态强度**（U4 / U5 / U11 / U12 / U15）。 |
| **下次怎么防** | 跨语言组件的验证**必须排期成对**：Go 侧探针 + RAG 侧探针**同时**跑同一组输入，比对**输出字节**（不是「结构看起来一样」）。`payload_hash` / `occurred_at` 这类**字节级契约**尤其不能只靠读代码。 |

---

# 修复轮追加（2026-10-23 · 9 条漂移修复）

> 原文保留；以下为 2026-10-23 修复轮新增的踩坑记录。

---

## P-08：`ForceCommit=true` 让「报错重投」变成「静默丢弃」 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-08` ⇒ **新号**：`PIT-LIKE-24`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 侧 `ForceCommit` 默认 `true`（失败消息照样提交位点 ⇒ 永久丢弃）是**真源自身的事实**且真源已冻结 ⇒ 今天仍成立；本轮凡写 like 域的「重投 / 丢弃」结论，仍须引 Go 消费点。
> **依据**：Go `kq/queue.go:230-243`（消费点）+ `kq/config.go:24`（`ForceCommit bool` + `json:",default=true"`）· RAG 侧已显式 `FixedBackOff(0, 0)`：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeUpdateKafkaListener.java:86` ⇒ **证据强度：Go 侧 + RAG 侧行号**。

### 现象

对照 Go 侧 Kafka 消费循环时，直觉是「返回 error ⇒ 不确认位点 ⇒ 会重投」。
源码注释（Go `internal/model/like.go:164-179` 的 inbox 幂等第 ② 层）也写着
「`payload_hash` 不同 ⇒ **报错重投**（不跳过）」。
**两边都指向"会重投"，于是没人去核对那个开关。**

### 真源（消费点）

```go
// go-queue@v1.2.2/kq/queue.go:230-243
if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
    if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }  // 默认只 logc.Errorf（:362-366）
    if !q.c.ForceCommit { continue }                            // ⚠️ default=true ⇒ 不 continue
}
if err := q.consumer.CommitMessages(ctx, msg); err != nil { … } // ⚠️ 照样提交
```

- `kq/config.go:24` → `ForceCommit bool \`json:",default=true"\``
- like 侧 `internal/mq/etc/mq.yaml` **没有覆盖**它 ⇒ 生效值 `true`

⇒ **失败消息被提交位点、永久丢弃**，且循环继续下一条。
⇒ **注释所述的「重投」从未发生** —— 这是**真源自身的缺陷**（意图与实现不符）。

### 为什么这么容易踩

「返回 error ⇒ 不确认」是 **Kafka 客户端的常见心智模型**，但那个模型的前提是
「消费循环不会继续 commit」。kq 用一个**默认开启**的 `ForceCommit` 把它翻转了，
而这个默认值既不在 yaml 里、名字也不像「丢弃」——**藏得极深**。

### 怎么不重犯

1. **读到消费点，不读到声明点**：`ForceCommit` 的声明在 `config.go:24`，
   真正决定控制流的是 `queue.go:235` 那句 `if !q.c.ForceCommit`（AGENTS.md §3.3.1）。
2. **「重投 / 丢弃」这类结论，一律追问「位点最后有没有被推进」**，
   而不是看函数返回了什么。返回值与位点是**两件事**。
3. 怀疑某个默认值时，**先查消费它的 yaml 有没有覆盖**（Go 的默认值大量来自 struct tag，
   yaml 常常一个字段都不写）。

---

## P-09：注解写得「像开关」，其实是个 no-op ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-09` ⇒ **新号**：`PIT-LIKE-25`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 那 3 处**误导性残留**的 `@JsonIgnoreProperties(ignoreUnknown = false)` 已删 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeEventContracts.java:28-35`（「这里**没有** `@JsonIgnoreProperties`，是刻意的（2026-10-23 修正）」）⇒ **证据强度：源码行号**。

### 现象

台账把 DRIFT-LIKE-007 判为「未知字段从『忽略』变『丢弃』」，依据是源码里的
`@JsonIgnoreProperties(ignoreUnknown = false)`。
**看起来就是"这里配了严格模式"，于是没人去问：这个值是不是默认值？**

### 真源

1. `ignoreUnknown = false` 就是 Jackson 的**默认值** —— 注解不含任何"开启严格"的信息；
2. 类级 `@JsonIgnoreProperties` **无法覆盖**全局的
   `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`；
3. 真正决定行为的是 `Jackson2ObjectMapperConfiguration.java:91` 的 `.disable(FAIL_ON_UNKNOWN_PROPERTIES)`；
4. **绿测早就写对了**：`LikeRpcSpringContextWiringTest#unknownFieldsAreIgnoredLikeGoEncodingJson`
   断言「带 `extra_field` 的消息解析成功」且**一直是绿的**。

⇒ 漂移**不成立**；那 3 处注解是**误导性残留**，比没有更坏（它让人以为这里配过严格模式）。

### 怎么不重犯

- **看到「配置型注解/配置项」先问一句「它的默认值是不是就是当前值」** ——
  是 ⇒ 它是 no-op，删掉比留着好（留着会持续误导下一个读者）。
- **台账与绿测冲突时，优先相信能跑出来的那条**。
  本例中「测试是绿的」是唯一的事实，其余全是推理。

---

## P-10：框架的「默认构造」里藏着一个你没写的数字 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-10` ⇒ **新号**：`PIT-LIKE-26`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 框架默认构造里那个「没写的数字」已被**显式**写出来（`FixedBackOff(0, 0)`）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeUpdateKafkaListener.java:78,81,86`（类注释记 `SeekUtils.DEFAULT_BACK_OFF = FixedBackOff(0, 9)` 并显式传 `FixedBackOff(0, 0)`） ⇒ **证据强度：源码行号**。

### 现象

`new DefaultErrorHandler(recoverer)` 看起来就是「配个日志回调」。
**没读构造链** ⇒ 一直以为"没有重试逻辑"，而 javadoc 里也这么写。

### 真源（`spring-kafka` 4.0.7 sources jar）

```
DefaultErrorHandler.java:81-83   public DefaultErrorHandler(ConsumerRecordRecoverer r) { this(r, SeekUtils.DEFAULT_BACK_OFF); }
SeekUtils.java:57                public static final int DEFAULT_MAX_FAILURES = 10;
SeekUtils.java:63                public static final FixedBackOff DEFAULT_BACK_OFF = new FixedBackOff(0, DEFAULT_MAX_FAILURES - 1);
```

⇒ 单参构造**内建 9 次进程内重试**；失败消息会被 seek 回原位并**真的重跑 listener 9 次**。

### 为什么这么容易踩

重试次数不是写在业务代码里的，而是**藏在框架的默认构造链里**。
从调用侧看，`new DefaultErrorHandler(lambda)` 只有一个参数，**没有任何数字可见**。

### 怎么不重犯

- **凡引入带「错误处理器 / 重试策略」的框架 API，必须读过它的默认构造**，
  并**显式**把关键数字写出来（哪怕就是 `FixedBackOff(0, 0)`）。
- 判定「它到底重试几次」不能看 javadoc，要看 `DEFAULT_*` 常量的**定义 + 消费点**（§3.3.1）。
- ⚠️ 注意 `DefaultErrorHandler` 重试耗尽后 `FailedRecordTracker.recovered` **返回 `true`**
  ⇒ `SeekUtils.doSeeks` **不 seek** ⇒ **并不会卡死分区**。
  「重试 = 卡死」是合理直觉，但在这里**是错的** —— 结论必须顺着
  `recovered → doSeeks → seekOrRecover` 三段读到底，中途停下就会得出相反结论。

---

## P-11：兼容层本身可能就是那个 bug（守门测试当场抓到） ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-11` ⇒ **新号**：`PIT-LIKE-27`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 「不做回退」这个决定已被**钉进测试**（旧键不再参与判定）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-rpc/src/test/java/com/rag/like/like/rpc/service/LikeGoAlignmentTest.java:209`（`legacyTaskPushersSwitchIsNoLongerConsulted`）· `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/LikeKafkaTopics.java:77,122,168` ⇒ **证据强度：源码行号 + 守门在仓**。

### 现象

把一个开关拆成两个细分开关后，我顺手加了「旧键回退兼容」，
让存量配置不至于静默失效。**看起来是好意。**

守门测试立刻红了：`expected: <false> but was: <true>`。

### 根因：不是笔误，是设计不可行

两个细分开关的默认值**都是 `true`**，而 Spring 的绑定**无法区分**
「用户没配」与「用户显式配成了 `true`」⇒
任何回退逻辑都会把「显式只开一路」误判成「都没配过」⇒ 行为**恰好相反**。

⇒ 最终**不做回退**：yml 键直接换成两个，旧字段标 `@Deprecated` 且**不参与判定**，
并把「不做回退」这个决定**钉进测试**（`legacyTaskPushersSwitchIsNoLongerConsulted`），
避免下一个人出于善意再把回退加回来。

### 元教训（比这个 bug 本身重要）

⚠️ **「兼容层」是高危区**：它看起来总是对的，却经常制造**静默**的行为翻转
—— 本例中它会让「只开一路」变成「两路都开」，而**没有任何日志、没有任何报错**。

⇒ 写兼容层前先问：**「未配置」与「配置成默认值」我能区分吗？**
不能 ⇒ **不要写兼容层**，宁可改配置键并让失效可见。

⚠️ 另一面：**没有测试的错误不是「错误答案」，而是「根本不存在的断言」** ——
这是 §3.3.3「绿测试可能是错误答案的守卫」的**反向**版本。
本例若没有那条测试，这段代码会以「兼容层」的姿态长期留存，且**永远不会被发现**。

---

# 2026-10-24 第五轮新增踩坑（L-U11 补 `@EnableScheduling` 守门网）

## PIT-LIKE-RPC-012 —— 🔴 **「Bean 存在」在 `@EnableScheduling` 这类机制上，是最像真的假绿** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKE-RPC-012` ⇒ **新号**：`PIT-LIKE-28`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「摘掉 `@EnableScheduling` 之后 Bean 还在、只是不工作 ⇒ 只有『机制生效』级别的断言能抓」这条**性质**今天仍成立（任何 `@EnableScheduling` / `@Scheduled` 类机制都一样）⇒ 仍生效；本轮若给 like 域写装配类断言，仍须按「摘掉它之后会怎样」分层设计。
> **依据**：本域已落地：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/LikeRpcApplication.java:82`（`@EnableScheduling`）· 守门 `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcSpringContextWiringTest.java` ⇒ **证据强度：源码行号 + 守门在仓**。

### 现象

`DRIFT-LIKE-006` 把 like-rpc 的真实装配缺口缩到 2 处，其中一处是：
「缺 `@EnableScheduling` ⇒ outbox relay 静默不跑，事件永远积压」。

### 为什么它比别的坑更隐蔽

`LikeHotEventOutboxRelay` 是 `@Component` ⇒ **摘掉 `@EnableScheduling` 之后它照样被创建、照样能注入**。
所以任何形如

```java
assertNotNull(context.getBean(LikeHotEventOutboxRelay.class));   // ❌ 恒绿
```

的断言，**在缺陷存在时也是绿的**。

而缺 `@EnableScheduling` 的真实后果是：**ticker 不再触发 ⇒ 热点事件永远积压**，
启动日志**零告警、零报错** —— 这是 §3.3.3「静默失效」里最安静的一种形态。

### 本轮锁的是两件更强的事

1. **装配层**：`ScheduledTaskHolder` **真的持有**那个 `@Scheduled` 任务
   （识别用 `ScheduledMethodRunnable.toString()` = `"<声明类全名>.<方法名>"`）。
2. **行为层**：`Task.getLastExecutionOutcome().status()` 从 `NONE`（从没跑过）变成 `SUCCESS`
   —— 即**它真的被执行了**。

⚠️ 断言用的是 `SUCCESS` 而不只是「≠ `NONE`」：
**「ticker 在跑但每轮都抛异常」是另一个缺陷，不能算通过**
（relay 的循环体自己 `catch` 住异常只记 `WARN`，不抛出 ⇒ 那种失败在日志里很轻）。

### 元教训

⚠️ **「装配缺口」类缺陷要按「摘掉它之后会怎样」来设计断言**：
- 摘掉之后**上下文起不来** ⇒ 任何 `@SpringBootTest` 都会红（容易发现）
- 摘掉之后**Bean 还在、只是不工作** ⇒ 只有「机制生效」级别的断言能抓（难）

⇒ 后者才是 §3.3.3 说的「**注解存在 ≠ 机制生效**」。
本条与错误断言 F（`LikeRpcApplication` 里那句「每一条都有对应的 wiring 测试锁死」）
是**同一个洞的两面**：断言 F 是对**覆盖范围**的错报，本条是那个洞本身。

---

# 2026-10-24 台账审计新增踩坑

## PIT-LIKE-RPC-013 —— 🔴 **「错误断言已清理」这句话，本身也是一条需要被核的断言** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKE-RPC-013` ⇒ **新号**：`PIT-LIKE-29`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「勾『已清理』时手要真的打开那个文件」这条**台账纪律**今天仍成立 ⇒ 仍生效；本轮给 DRIFT 条目改状态时，同属「清单勾了、文件没改」的高发区 ⇒ **是**。
> **依据**：AGENTS.md §3.3.3 第 ③ 条（「脚本批量改 N 处必须配覆盖全部 N 处的守恒断言」） ⇒ **证据强度：仓库政策**。

### 现象

台账 §3「错误断言清理（A~I 全部 + 新增 4 处）」把 **M** 列为**已清理**：
> | **M** | `application.yml` kafka consumer 段 | 「持久层失败则**不提交**（retry）」 | 错：Go `ForceCommit=true` ⇒ **照样提交**（§2.1） |

2026-10-24 的台账审计一查 —— **`application.yml` 里那三句原文还在**，
且其中两句的方向与 Go **完全相反**。

### 为什么会被漏掉

第三轮修了 9 条漂移，其中 **004** 的修法是「显式 `FixedBackOff(0, 0)`」，
配套改了**两处 javadoc**（`LikeRpcApplication` 的 J/K）——
但 **`application.yml` 里还有第三处同款断言**（M），没人回头核。
⇒ 台账勾「已清理」时，勾的是**记忆里的清单**，不是**文件里的事实**。

### 代价（这条断言是**方向反的**）

`application.yml` 写着「持久层失败则**不提交**（retry）」⇒
读者会以为 RAG 侧失败会**重投** ⇒ 而 Go 的 `ForceCommit=true` 是**照样提交（丢弃）** ⇒
**两侧语义相反的结论被写进了配置文件的注释里**，且被台账记成「已修」。

### 元教训

⚠️ **「错误断言清理」是一张需要逐条对着文件核的清单**，不是一次性的动作 ——
**代码改了、清单勾了、文件没改**，是三者最容易脱节的一种。

⇒ **判据**：勾「已清理」时，手要真的打开那个文件、看到那行字被改过。
⚠️ 「我记得我改了三处」和「三处都被改了」之间，隔着一次 grep。

⇒ 这也是本轮审计为什么把「机器扫行号引用 + 逐条比对编号」当成必做动作：
**行号会位移、清单会凭印象勾、只有机器扫 + 逐条核能兜住。**

---

## PIT-LIKE-RPC-014 —— 🔴 **共享组件的 javadoc 与各域的 javadoc 可以「互相矛盾」，而且两边都言之凿凿** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKE-RPC-014` ⇒ **新号**：`PIT-LIKE-30`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「共享组件的 javadoc 是所有域的公共知识、写错一次影响全部调用方」今天仍成立 ⇒ 仍生效；本轮凡引用共享层机制（如 `RpcServerInterceptorFactory`）的结论，仍须核到第三方源码。
> **依据**：`service/common/src/main/java/com/rag/common/spring/RpcServerInterceptorFactory.java:15,30-38`（javadoc 已改正）· `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/LikeRpcApplication.java:115`（消费点） ⇒ **证据强度：源码行号**。

### 现象

`service/common/.../RpcServerInterceptorFactory.java` 写：

> 「grpc-spring-boot-starter **会自动把所有 `ServerInterceptor` 类型的 Bean 注册到 gRPC server**，
> 业务模块无需手写 `grpcServer.use(...)`」

而 `service/like/like-rpc/.../LikeRpcApplication.java` 写：

> 「devh starter **不会**自动把用户提供的 `ServerInterceptor` 挂到 server 上。
> 没有它，超时 / deadline 传播 / gRPC span **全部静默消失**，而 Bean 断言照样是绿的。」

**两句直接矛盾**，而且都写得很笃定。

### 实测谁对（读 devh 源码，不是看文档）

| 机制 | 出处 | 收集什么 |
|---|---|---|
| `GlobalServerInterceptorRegistry.initServerInterceptors()` | `GlobalServerInterceptorRegistry.java:79-87` | 只遍历 `GlobalServerInterceptorConfigurer` Bean |
| `AnnotationGlobalServerInterceptorConfigurer.getServerInterceptorBeans()` | 同包 `:57-60` | `getBeansWithAnnotation(GrpcGlobalServerInterceptor.class)` ⇒ **只认带注解的** |
| `@GrpcService(interceptors=…)` | `AnnotationGrpcServiceDiscoverer.java:91` | 另一条显式路径 |

⇒ **普通 `ServerInterceptor` Bean 不会被自动注册** ⇒ **like-rpc 那句对，common 那句错**
（common 漏了 `@GrpcGlobalServerInterceptor` 这个前提）。
⚠️ 佐证：全仓 `grep -rn "GrpcGlobalServerInterceptor" service/` **零命中** ⇒ common 那句话**在本仓从未成立过**。

### 危害

common 那句若被下一个人当真 ⇒ **各域的 `rpcInterceptorConfigurer` 会被当成冗余删掉** ⇒
超时 / deadline 传播 / gRPC span 在**所有 10 个 rpc 域**静默消失、且**零报错**。

### 元教训

⚠️ **共享组件的 javadoc 是「所有域的公共知识」** ——
它写错一次，影响面是**全部调用方**，而不是一个域。
⇒ 共享组件里的每一句机制描述，都该被当作**跨域契约**来审。

⚠️ **两处互相矛盾的注释，本身就是最强的告警信号** ——
比任何一处单看像错的都更值得追。⇒ 看到矛盾时，**去读第三方源码**（这里是 devh 的
`GlobalServerInterceptorRegistry`），而不是「挑一个更可信的」。
