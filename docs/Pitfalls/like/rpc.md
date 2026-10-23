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

## P-01 「静默失效」在本组件上其实会**硬失败** —— 通用案例必须按组件复核

| | 内容 |
|---|---|
| **现象** | Boot 4 把 Kafka 自动配置搬出 `spring-boot-autoconfigure`（实测 `spring-boot-autoconfigure-4.0.8.jar` 内 Kafka 相关类 = **0**），改由独立模块 `org.springframework.boot:spring-boot-kafka` 提供。`like-rpc/pom.xml` 只引了 `org.springframework.kafka:spring-kafka` ⇒ `KafkaAutoConfiguration` / `KafkaAnnotationDrivenConfiguration` **都不在 classpath 上**。<br>**实测结果不是「静默不消费」，而是 `Spring 上下文直接启动失败`：**<br>`NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.kafka.core.KafkaTemplate<String, String>' available` |
| **根因（框架机制）** | 通用案例说「`@KafkaListener` 被完全静默忽略」——**那是对「只写注解、别处不引用 Kafka Bean」的组件而言**。<br>like-rpc 恰好把 Kafka Bean 当**必需**依赖注入：<br>① `LikeHotEventOutboxRelay:42` 构造参数 `KafkaTemplate`；<br>② `LikeDomainService.KafkaLikeMsgPublisher:990` 构造参数 `KafkaTemplate`；<br>③ `LikeTaskProgressPublisher:48` 构造参数 `KafkaTemplate`；<br>④ `LikeRpcApplication:113` 的 `kafkaListenerContainerFactory(ConsumerFactory)` —— `ConsumerFactory` 同样由 `KafkaAutoConfiguration` 提供。<br>⇒ **缺模块 ⇒ 缺 Bean 定义 ⇒ 构造注入失败 ⇒ refresh 失败**。机制是「静默」的，**后果不是**。 |
| **为什么测试抓不到** | 31 条测试**全是纯 Mockito 或纯 in-process gRPC**，**零** `@SpringBootTest` / `ApplicationContextRunner`。它们连 Bean 定义都不解析 ⇒ 「`KafkaTemplate` 这个 Bean 存不存在」这个问题**根本没被问过**。首轮已把这条记成 DRIFT-LIKE-006（UNKNOWN），本轮才第一次有答案。 |
| **下次怎么防** | ① **先让模块起一次**（哪怕 `ApplicationContextRunner` 空跑）——「能启动」是所有语义验证的前置条件，不是可选项；<br>② 凡用 `@KafkaListener` / `KafkaTemplate` / `ConsumerFactory`，**先 `mvn dependency:tree` 确认 `spring-boot-kafka` 在不在**，别看「`spring-kafka` 在」就以为完事（`spring-kafka-4.0.7.pom` 对 `spring-boot-kafka` 引用数 = **0**）；<br>③ 装配层测试要断言 **「listener 进了容器的 registry」**（`KafkaListenerEndpointRegistry` 存在且 container 数 > 0），不是「listener 方法存在」。 |

## P-02 Boot 4 拆模块后，**第三方 starter 的 `@AutoConfigureAfter` 会静默失效**

| | 内容 |
|---|---|
| **现象** | `mybatis-plus-spring-boot3-starter:3.5.17` 是 **Boot 3 专用** starter。它的 `MybatisPlusAutoConfiguration` 上写着 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)`；而 **Boot 4.0.8 已把该类搬到 `org.springframework.boot.jdbc.autoconfigure`（`spring-boot-jdbc` 模块）**，旧 FQN 在全部 Boot 4.0.8 jar 里**已不存在**。<br>⇒ 排序保证丢失 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 在 DataSource Bean 注册**之前**求值 ⇒ **不匹配** ⇒ 自动配置被**静默跳过**，**一行错误都没有**。<br>症状：`Error creating bean with name 'likeOutboxEventMapper' … Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` |
| **根因（框架机制）** | `@AutoConfigureAfter` 里写的是**类名字符串**，Boot 用 ASM 元数据排序、**不加载类** ⇒ 目标类不存在时**不报错、不告警**，只是排序约束消失。后果是**被依赖方先于依赖方求值**，条件自然不成立。 |
| **为什么测试抓不到** | 纯 Mockito 单测**根本不会加载自动配置** ⇒ 条件求值时序永远看不到。`--debug` 的 ConditionEvaluationReport 是唯一能看到真相的地方（`MybatisPlusAutoConfiguration: Did not match: - @ConditionalOnSingleCandidate (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans`）。 |
| **下次怎么防** | ① 升级到 Boot 大版本后，**逐个检查第三方 starter 的 `@AutoConfigureAfter/@AutoConfigureBefore` 目标类是否还在**；<br>② 启动失败时**第一时间跑 `--debug`** 看 ConditionEvaluationReport，不要只看最后一个异常；<br>③ 「类在 classpath 上」≠「自动配置生效」——必须看 **ConditionEvaluationReport 的 Did not match 原因**。 |

## P-03 Boot 4 的 JSON 默认切到 Jackson 3，**Jackson 2 没有自动配置了**

| | 内容 |
|---|---|
| **现象** | like-rpc 有 5 个类注入 `com.fasterxml.jackson.databind.ObjectMapper`（Jackson 2）。启动报：`No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available`。 |
| **根因（框架机制）** | Boot 4 把 JSON 自动配置搬进 `org.springframework.boot:spring-boot-jackson`，而它的 `JacksonAutoConfiguration` 构造的是 **Jackson 3** 的 `tools.jackson.databind.ObjectMapper`（见 `spring-boot-jackson-4.0.8-sources.jar` 的 import 段）。**Boot 4 根本没有 `spring-boot-starter-json`**（本地仓库只有 2.5.14 / 3.2.5 / 3.5.11）⇒ **没有任何自动配置能产出 Jackson 2 的 `ObjectMapper` Bean**。<br>⚠️ 陷阱：加了 `spring-boot-jackson` **也解决不了** —— 它给的是 Jackson 3 的 Bean，类型不匹配。 |
| **为什么测试抓不到** | 测试里注入的 `ObjectMapper` 是**测试自己 new 的**或 Mockito mock ⇒ 从不经过自动配置。 |
| **下次怎么防** | ① **Boot 4 升级专项检查表**里加一行：「本模块用的是 Jackson 2 还是 Jackson 3？」——两者的**默认行为不同**（HTML 转义、未知字段策略、`@JsonPropertyOrder` 都要重验）；<br>② 若继续用 Jackson 2 ⇒ **必须自己声明 `@Bean ObjectMapper`**（并显式配 `FAIL_ON_UNKNOWN_PROPERTIES` 等与 Go 对齐的策略）；<br>③ 跨语言哈希（如 `payload_hash`）依赖序列化字节 ⇒ 换库前先跑**双侧字节对拍**，不要靠「读代码看着一样」。 |

## P-04 服务发现 URI 的**斜杠个数**是硬约束，而且**没有编译期检查**

| | 内容 |
|---|---|
| **现象** | `like-rpc/application.yml:132` 写 `address: discovery://message-rpc` ⇒ 启动报：`IllegalArgumentException: Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://message-rpc'`（devh starter 3.1.0 `DiscoveryClientResolverFactory:82`） |
| **根因（框架机制）** | devh 的 discovery URI 语法是 `discovery://[authority]/<service-name>` ⇒ **服务名必须在 path 段**，即**三斜杠** `discovery:///message-rpc`。<br>⚠️ 本仓**其它模块全是对的**（`admin-api:56,65,71`、`like-api:104`、`hot-api:53,59`、`message-api:52`）⇒ 这类错误是**逐模块复制粘贴时漏改**的高发点。 |
| **为什么测试抓不到** | gRPC client 的 channel 是在**Bean 创建期**建的，而纯 Mockito 单测**从不创建 channel**。 |
| **下次怎么防** | ① 复制 `grpc.client.*.address` 时**逐条核对斜杠个数**；<br>② 加一条**静态校验脚本**：扫全仓 `discovery:` 地址，断言**一律三斜杠**（可挂进 `tools/difftest/`）；<br>③ ⚠️ 同款两斜杠还出现在 `service/message/message-rpc/application.yml:84,89` 与 `service/article/article-rpc/application.yml:110,116` ⇒ **这三个组件各自开单子，不要只改 like-rpc**。 |

## P-05 「注释里写着 Go 的行为」**不等于** Go 的行为 —— 又一条

| | 内容 |
|---|---|
| **现象** | `V4__create_like_domain_fact_outbox.sql` 的注释断言：「GORM 对『无 size 的整数』统一取 **bigint**（不是 integer / smallint）」。**实测是错的**：GORM 对 `int32`（`field.Size = 32`）产出 **`integer`**。<br>消费点：`gorm.io/driver/postgres@v1.6.0/postgres.go` 的 `DataTypeOf` —— `size<=16→smallint`、`size<=32→integer`、否则 `bigint`；`gorm.io/gorm@v1.31.1/schema/field.go:338-339` 把 `int32` 的 Size 设为 32。<br>⇒ 迁移脚本照着这条错断言把 `delivery_status` / `retry_count` 建成了 `BIGINT`，与 Go 的 `integer`（AutoMigrate）/ `smallint`（Go 手写迁移）**三方不一致**。 |
| **根因（框架机制）** | GORM 的整数→SQL 类型映射**取决于 `field.Size`**，而 `Size` 又取决于 **Go 的具体整数类型**（`int32`→32、`int64`→64），**不是**「有没有 type tag」。tag 只决定「是否显式指定」。 |
| **为什么测试抓不到** | DDL 不在单测覆盖范围；「列类型对不对」只有**真 PG 读列类型**才看得见。 |
| **下次怎么防** | ① 涉及「GORM 会把 X 映射成什么类型」时，**必须去读 `driver/postgres` 的 `DataTypeOf` 消费点**，不能凭直觉，也不能贴 struct 定义行当证据（AGENTS.md §3.3.1）；<br>② 跨语言共表时，把「列类型 + CHECK + 触发器 + 部分索引」列成**逐项 checklist**（Go 手写迁移里的约束常常**不**来自 struct tag）；<br>③ 本项目已有先例：`like_record` 的 `created_at` / `CreateTime` 列名错配（DRIFT-LIKE-001）——**同一张表被两种语言共用时，schema 必须是显式契约，不是「各自 AutoMigrate 的副产物」**。 |

## P-06 真启动一次的成本 ≪ 想象，收益 ≫ 想象

| | 内容 |
|---|---|
| **现象** | like-rpc 此前**从未被启动过一次**（首轮 DRIFT-LIKE-006）。本轮第一次启动，**连着撞出 4 个独立硬阻塞**（P-01 ~ P-04），每个都是「上下文起不来」级别。 |
| **根因（框架机制）** | 这不是巧合：**Boot 3 → Boot 4 的模块化迁移**会同时打中「自动配置类搬家」「starter 排序失效」「默认 JSON 库换代」「服务发现 URI 语法」四条线。任何一条都足以让应用**起不来**，而**四条都发生在同一个模块上**，因为这个模块**依赖面最广**（Redis + PG + Kafka + gRPC + Nacos + Jackson + MyBatis）。 |
| **为什么测试抓不到** | 31 条绿测试**没有一条**走 Spring 上下文 ⇒ 它们测的是**业务方法**，不是**应用**。 |
| **下次怎么防** | ① **「先让它真的启动一次」**（AGENTS.md §0 第三类未闭环项）应该排在**任何业务单测之前**；<br>② 启动验证的成本极低：`mvn dependency:build-classpath` + `java -cp ... Main` + 真 PG/Redis 容器，**本轮全程 ≈ 15 分钟**，却换来 4 个 CRITICAL；<br>③ 断言要分层：**类在不在 classpath（`Class.forName`）** → **Bean 在不在容器（`getBean`）** → **机制有没有真生效（registry / 列类型 / 实际写入）**。本轮用的就是这三层。 |

## P-07 只跑 RAG 侧探针，**只能证明「代码写成了这样」**

| | 内容 |
|---|---|
| **现象** | 本轮全部新结论都是 **RAG 侧单侧证据**（真容器启动 / classpath 探针 / 真 PG）。Go 侧本轮**没有**起活体探针。 |
| **根因** | AGENTS.md 硬约束 3：**只跑 RAG 侧的探针不算数** —— 它证明不了「Go 是这样」。 |
| **这次的处理** | 凡 Go 侧结论一律引用**消费点行号**（含 go-zero / go-queue / gorm **依赖源码**的消费点，例如 `kq/pusher.go:123-125`、`driver/postgres` 的 `DataTypeOf`、`gorm/callbacks/update.go:201,238`），并在 §9 逐条标注哪些结论**只到静态强度**（U4 / U5 / U11 / U12 / U15）。 |
| **下次怎么防** | 跨语言组件的验证**必须排期成对**：Go 侧探针 + RAG 侧探针**同时**跑同一组输入，比对**输出字节**（不是「结构看起来一样」）。`payload_hash` / `occurred_at` 这类**字节级契约**尤其不能只靠读代码。 |

