# `service/message/message-rpc` 踩坑与元教训

> 配套台账：[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md)（差异本体） +
> [`docs/DRIFT/_cross/message-domain-wide.md`](../_cross/message-domain-wide.md)（§6.7 第二轮）。
> **本文件只写教训**，不重复差异清单。
> 编号命名空间 `PITFALL-MSG-R-*`；最近一次核对日期 **2026-10-24**（`PITFALL-MSG-R-014/015/016` 为本轮新增）。

---

## PIT-MESSAGE-09 「测试全绿」与「一次没起过」可以同时成立 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-001` ⇒ **新号**：`PIT-MESSAGE-09`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「测试全绿」与「一次没起过」可以同时成立 —— 纯 Mockito 单测**不装配 Spring 上下文** ⇒ 该形态今天仍在 ⇒ 本轮凡以「测试全绿」当证据写进台账的状态，仍属无效证据（AGENTS.md §3.3.3 第 ② 条）。
> **依据**：范式 `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageTransactionWiringTest.java`（真 PG 8 例 · 删 `setConversationId` 会红）· 修法落点 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/MessageAtomicService.java:106,138` ⇒ **证据强度：源码行号 + 真 PG 守门在仓**。

**现象**：`message-rpc` 曾有 **21 条测试且全绿**，而这个模块**当时根本启动不了**。
三条「起不来」级缺陷（Bean 名撞车 / Mapper 未注册 / Flyway 未加载）同时存在，
CI 一声不响。

**根因（框架机制层面）**：那 21 条里 **9 条是纯 Mockito 单测**。
Mockito 替换掉 `NotificationMapper` / `ConversationMapper` 这些 Bean 之后：

- Spring 上下文**根本不装配** ⇒ `@MapperScan` 有没有、Bean 名撞不撞，都测不到；
- MyBatis-Plus 的 `TableInfo` 元数据**不初始化** ⇒ lambda wrapper 解析、`typeHandler` 绑定都测不到；
- DataSource / SqlSessionFactory **不存在** ⇒ 自动配置的装配顺序问题测不到；
- Flyway 自动配置**不加载** ⇒ 迁移跑没跑测不到。

**为什么测试抓不到**：它们测的是**「我调的方法返回了什么」**，
而这三类缺陷的形态是**「这个 Bean / 自动配置到底存不存在」**。
按定义，mock 掉数据层的单测对「Bean 存不存在」零信息量。

**下次怎么防**（本仓已落地的形态，照抄即可）：

1. 每个模块接手的第一件事是 **最小 `@SpringBootTest` + 真容器**，不是写业务单测；
2. 容器要真 PG（`Testcontainers` + `postgres:16-alpine`），**不要用 H2** ——
   本模块要验证的正是「唯一约束冲突 ⇒ 整事务回滚」，H2 语义不同，替换等于把要验证的东西换没了；
3. **真 PG 测试必须正反成对**：`rollbackXxx`（造失败、断言回滚）旁边必须有
   `happyPathActuallyPersists`（不造失败、断言真的写进去了）。
   只有前者时，「压根没执行 SQL」也会让它绿。

**⚠️ 2026-10-23 复核**：这条已被本轮实测**确认有效** ——
`MessageTransactionWiringTest` 在真 PG 上 8 例全绿，且真能在删掉 `setConversationId` 时变红。

---

## PIT-MESSAGE-10 「断言 Bean 存在」≠「断言机制生效」——本模块的完整样本 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-002` ⇒ **新号**：`PIT-MESSAGE-10`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「断言 Bean 存在 ≠ 断言机制生效」今天仍在 ⇒ 本轮写「已接线 / 已生效」的结论，必须给**消费点**行号，不能给「Bean 拿得到」这类弱断言。
> **依据**：拦截器侧 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/MessageRpcApplication.java:115`（`@Bean GrpcServerConfigurer rpcInterceptorConfigurer(...)`）+ `:134`（消费 `GoStyleRpcProtectionChain.build(...)`）· 事务侧 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/MessageAtomicService.java:102,130`（`@Transactional`）· 共享层说明 `service/common/src/main/java/com/rag/common/spring/RpcServerInterceptorFactory.java:30-38` ⇒ **证据强度：源码行号**。

**现象**：一次修复涉及**两个**同款陷阱，形态一模一样。

| | 陷阱 A | 陷阱 B |
|---|---|---|
| 配置/代码声称 | `application.yml:143` `observability.rpc-timeout: 2s`，注释还解释了「对齐 go-zero 默认 2000ms」 | 类注释白纸黑字列出 6 个事务方法「放本类上 `@Transactional`」 |
| 实际 | devh starter **从不自动注册**用户提供的 `ServerInterceptor` Bean ⇒ 拦截器从未进入 `ServerBuilder` | 6 个方法全被同类私有 `doXxx` 通过 `this.` 调用 ⇒ **不经代理** ⇒ 注解形同虚设 |
| 是否报错 | **不报错**（Bean 就在容器里，`getBean(ServerInterceptor.class)` 断言一直是绿的） | **不报错**（auto-commit 一切正常） |
| 实际后果 | 2s 超时从未执行 ⇒ 慢查询无上界 | 事务退化成 auto-commit ⇒ 新建会话失败会**残留孤儿会话行** |

**根因（框架机制）**：两者都是 Spring 的**运行期动态装配** ——
`@Bean` 产出对象 ≠ 有人消费它；`@Transactional` 生效**依赖 AOP 代理**，
而代理**只拦截从容器外部进来的调用**，`this.` 调用永远绕过它。

**为什么最容易踩**：这两处的**注释都比实现更完整**。
`createConversationWithMessageTx:385` 的类注释逐条列出了 6 个事务方法，
`application.yml` 的注释解释了取 2s 的依据 —— review 时看到的是「全部对齐」，
没有任何信号提示「一个都没生效」。

**下次怎么防**：

1. 每引入一个框架机制，配一条**能真正触发它**的测试，且断言写在**消费点**：
   拦截器 ⇒ `verify(builder).intercept(same(interceptor))`，**不是** `getBean(...) != null`；
   事务 ⇒ 真 PG 上制造失败、断言另一张表的行被回滚掉，**不是** `hasAnnotation(Transactional.class)`；
2. 自调用是 Java 的默认写法、Spring 的静默失败 —— **多步写事务必须独立成 Bean**
   （本仓 `AdminCreateAtomicService` / `MessageAtomicService` 都是这么来的）；
3. 配一条**结构断言**当不依赖 Docker 的兜底网：反射读 `@Import` 列表、读 `@Transactional` 声明集合，
   删掉就红（本仓 `MessageGrpcInterceptorWiringTest:87-102`、
   `MessageTransactionWiringTest:336-364` 是范式）。

---

## PIT-MESSAGE-11 MyBatis 的 `#{...}` 与 MP 的 `Entity::method` 用**两套**属性名规则 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-003` ⇒ **新号**：`PIT-MESSAGE-11`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 本域两个布尔字段已统一改名 `read` + 显式 `@TableField("is_read")`，lambda 与手写 `#{...}` 两条属性名路径都已有守门 ⇒ 触发条件消除；本轮只改 `docs/**`、不动实体。
> **依据**：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/domain/ConversationMessageEntity.java:64`（`@TableField("is_read")`，字段名 `read`）· 守门 `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageIsReadLambdaWiringTest.java` ⇒ **证据强度：源码行号 + 守门在仓**。

**现象（同一模块内，两个方向各踩一次）**：

```
规则 A —— MyBatis 的 #{propertyName}（手写 SQL 路径）
    字段 isRead + getter isRead() + setter setRead()
    ⇒ Reflector 的属性名是 "read"（剥 is 前缀）
    ⇒ #{item.read} ✅    #{item.isRead} ❌ "There is no getter for property named 'isRead'"
    ⇒ 但若字段名本身就叫 isRead，addFields 会把它补进属性表 ⇒ #{isRead} 又能跑

规则 B —— MyBatis-Plus 的 LambdaXxxWrapper（Entity::method 路径）
    按**字段名**建 TableInfo.propertyMap
    ⇒ 字段 isRead ⇒ propertyMap["isRead"]；而 ::isRead 解析成属性 "read" ⇒ 落空
    ⇒ MybatisPlusException: can not find lambda cache for this property [read]
```

**根因**：MP 解析 `Entity::method` 时按 JavaBean 约定剥 `get`/`is` 前缀拿**属性名**，
再拿属性名去查**以字段名为 key** 的 map。两边约定不一致，且**两套规则都只在运行期炸**。

**为什么这个坑特别贵**：

- 规则 A 的失败形态是 `ReflectionException`，**只在真执行那条 SQL 时**才出现；
- 规则 B 的失败形态是 `MybatisPlusException`，同样**只在解析 lambda 时**才出现；
- 两种都**不会**出现在纯 Mockito 单测的报告里（mock 数据层不装配 MP 元数据）。

**下次怎么防（已落地的形态）**：

1. 布尔字段**一律叫 `read` / `deleted` / `enabled`，不叫 `isRead` / `isDeleted`**，
   显式加 `@TableField("is_read")` 指定列名；getter 可以保留 `isRead()`；
2. 手写 SQL 与 lambda wrapper 的属性名**必须分别验证一次**，
   不能因为「另一条路径已经改对了」就假定这条也对；
3. **同一模块内不要只改一半**。2026-10-23 复核发现：
   `NotificationEntity` 已经改成 `read`，而**同模块的 `ConversationMessageEntity` 还叫 `isRead`**，
   连带 `ConversationMessageMapper.markAllReadByRecipient`（`:82-84`）一旦接线就会炸
   （今天靠「该方法无调用方」侥幸没发作）。
   ⇒ **批量改 N 处时必须配守恒断言**（§3.3），本模块目前**没有**这道网。

---

## PIT-MESSAGE-12 注释里的**默认行为**必须读到消费点，不能只贴 struct 定义行 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-004` ⇒ **新号**：`PIT-MESSAGE-12`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 真源的默认值仍藏在 struct tag 里且**已冻结** ⇒ 本轮凡写「Go 侧默认值是 X」的台账断言，仍须给出**消费点**行号，不能贴 struct 定义行。
> **依据**：Go `service/message/rpc/message.go:34`（`DisableNativeRpcTimeout` 把 Timeout 清 0）· `zrpc/config.go:46`（`Timeout int64` + `json:",default=2000"`）· `zrpc/server.go:164-167`（第二个消费点 `if c.Timeout > 0`）⇒ **证据强度：Go 侧文件行号**。

**现象**：判断「Go 侧 gRPC 超时到底是多少」，只看 `zrpc/config.go` 的 struct 定义是不够的 ——
真正的答案是三个位置的组合：

```
1. zrpc/config.go:46            Timeout int64 `json:",default=2000"`       ← 定义点
2. etc/message.yaml            【没有 Timeout 键】                        ← 配置点
3. message.go:34               DisableNativeRpcTimeout(...) 把 Timeout 清 0 ← 消费点
   zrpc/server.go:164-167      if c.Timeout > 0 { ... }                  ← 第二个消费点
```

**只看到 1 会得出错误结论**「Go 有 2s 原生超时」；
**只看到 3 会得出错误结论**「Go 没有超时」；
正确答案是「**默认值是 2s，但被显式关掉，改由观测拦截器执行**」。

**同类**：判断 gRPC reflection 是否注册，
需要 `ServiceConf.Mode` 的 `json:",default=pro"`（`serviceconf.go:35`）
**加上** `message.yaml` 未配 Mode **再加上** `message.go:40` 的分支条件 —— 三步缺一不可。
默认 `pro` ⇒ 真源**不暴露** reflection。

**下次怎么防**：写「Go 的默认值是 X」这类断言前，
必须能回答**「这个 tag 被哪段控制流消费了」**；贴 `struct 定义行` 不算证据。

---

## PIT-MESSAGE-13 「关掉一个框架开关」不等于「关掉这个框架」 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-005` ⇒ **新号**：`PIT-MESSAGE-13`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 侧「`DisableNativeRpcTimeout` **只清 `Timeout` 一个字段**、其余中间件仍装配」这一事实**不变**（真源冻结） ⇒ 本轮凡评估 RAG 侧保护链覆盖面，仍须逐条对表，不能按「Go 关掉了中间件」的印象。
> **依据**：Go `zrpc/server.go:131-167`（装配消费点）· RAG 侧链定义 `service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java` · 链序钉死在 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/MessageRpcApplication.java:126-134` ⇒ **证据强度：Go 侧 + RAG 侧行号**。

**现象**：`message.go:34` 的一行
`observability.DisableNativeRpcTimeout(&c.RpcServerConf)` 看起来像
「本服务把 go-zero 的服务端中间件都关了，自己接管」。

**实际**（2026-10-23 逐条读 `zrpc/server.go:131-167` 才发现）：它**只**清了 `Timeout` 一个字段。
同一次 `MustNewServer` 仍然装配了：

| 拦截器 | 装配条件 | 默认值来源 | 仍生效？ |
|---|---|---|---|
| Trace | `c.Middlewares.Trace` | `default=true` | ✅ |
| **Recover**（panic → INTERNAL，进程存活） | `c.Middlewares.Recover` | `default=true` | ✅ |
| Stat / Prometheus | 同上 | `default=true` | ✅ |
| **Breaker**（熔断 → UNAVAILABLE） | `c.Middlewares.Breaker` | `default=true` | ✅ |
| **Shedding**（CPU>900 → RESOURCE_EXHAUSTED） | `c.CpuThreshold > 0` | `default=900` | ✅ |
| Timeout | `c.Timeout > 0` | — | ❌ **被显式清零** |

**为什么危险**：Spring 侧只挂了**一个**观测拦截器。
如果照着「Go 关掉了中间件」的印象去做对齐，就会把 recover / 限流 / 熔断**全部漏掉**，
而这三样恰恰是过载与崩溃时的**可观察行为契约**。

**下次怎么防**：迁移一个框架时，**逐条列出「框架会自动装什么」**（读装配函数，不是读配置），
再逐条确认目标侧有没有对应物。这份清单要写进台账，不能留在脑子里。

---

## PIT-MESSAGE-14 Spring Boot 4 把自动配置拆模块后，**依赖在 ≠ 能力在**，且**不报错** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-006` ⇒ **新号**：`PIT-MESSAGE-14`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— message-rpc 缺的 `spring-boot-flyway` 已补；且 **message-rpc 的 `pom.xml` 内 `kafka` 零命中**（本模块根本不引 Kafka）⇒ 本条在 message 域的两个触发条件都不再存在 ⇒ 本轮只改 `docs/**` ⇒ 不触发。
> **依据**：`service/message/message-rpc/pom.xml:168`（`org.springframework.boot:spring-boot-flyway`）· `service/message/message-rpc/pom.xml` 内 `kafka` **零命中**（grep 实测）⇒ **证据强度：pom 文本（静态）**。

**现象**：`message-rpc` 缺 `org.springframework.boot:spring-boot-flyway`，
**启动日志里连一行 Flyway 都没有** —— 不是报错，是根本没被加载。
三张表永不创建，第一次真正执行 SQL 时才炸 `relation "..." does not exist`。

**同款**：`pom.xml` 里 `spring-kafka` 在，但 `spring-boot-kafka`（Boot 4 的自动配置模块）不在
⇒ `@KafkaListener` 不装配（**全仓仍未修，见 AGENTS.md §3.3 末段**）。

**第三种形态（本模块独有，最隐蔽）**：`MybatisPlusAutoConfiguration` 上写着
`@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)` ——
**Boot 3 的包名**。Boot 4 把它挪到了 `org.springframework.boot.jdbc.autoconfigure.…`，
于是这条排序引用**静默悬空**：Spring 找不到该类，
又因为 `com.baomidou.*` 的字母序在 `org.springframework.*` 之前，
MP **先于** DataSource 求值 ⇒ `@ConditionalOnSingleCandidate(DataSource)` 找不到任何 DataSource
⇒ **整个 MyBatis 自动配置不生效**，报错形态是
`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`
（一个看起来像「mybatis 配置写错了」的错误）。

**修法（本模块已落地）**：加一个**空类**作排序桥 ——
`@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = MybatisPlusAutoConfiguration.class)`，
并**必须**在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里登记，
否则它不会被当自动配置加载、排序边也不成立。类体为空是刻意的。

**为什么这条值得单独记**：前两种形态至少还会**报一个错**（或至少少一行日志），
第三种的报错信息**完全指错了方向**。

**下次怎么防**：引入任何 `spring-*-starter` 类能力前，先确认该自动配置
**在 Boot 4 下仍在 `spring-boot-autoconfigure` 里**；被拆走的必须显式补对应模块（版本由 BOM 管）。
**判断「加了没变化」不能看启动日志**（本仓 §3.3 已记同款：注入点 echo 照打、jar 照起、日志零告警）。

---

## PIT-MESSAGE-15 自动配置的排序是一条**有向图**，不是「谁先扫到谁先生效」 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-007` ⇒ **新号**：`PIT-MESSAGE-15`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— MP 自动配置的排序桥已落到**共享层**并登记进 `AutoConfiguration.imports` ⇒ 那条悬空的排序边已消除 ⇒ 本轮只改 `docs/**` ⇒ 不触发。
> **依据**：桥 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` · 登记 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` · 守门 `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java` ⇒ **证据强度：源码行号 + 守门在仓**。

**现象**：单看任何一个自动配置的注解都看不出问题；
只有把「谁声明了 after/before」连成图，才知道最终的求值顺序。
Boot 4 的包名漂移让其中一条边**变成悬空引用**，图就断了。

**下次怎么防**：当启动报「`sqlSessionFactory` / `DataSource` 找不到」而配置看起来没问题时，
**直接看条件求值报告**（`--debug` / `--debug-autoconfigure`）里
`Did not match: ... OnBeanCondition` 的那一段，而不是先怀疑自己的配置写错。
本模块的实测形态：

```
DataSourceAutoConfiguration.PooledDataSourceConfiguration matched:          ← DataSource 本身是好的
MybatisPlusAutoConfiguration:
   Did not match:
      - @ConditionalOnSingleCandidate (types: javax.sql.DataSource)
        did not find any beans (OnBeanCondition)
```

---

## PIT-MESSAGE-16 变异测试会先证明「你自己的新测试也是假绿」 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-008` ⇒ **新号**：`PIT-MESSAGE-16`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「变异测试不是可选项、每条写路径各破坏一次」这条**元规则**今天仍成立（message 域有 4 条写路径：新建 / 追加 / 批量 / 更新）⇒ 本轮若把任何「已修」写进台账，仍须能指出**哪条守门在哪种变异下会红**。⚠️ 本条的**具体实例已闭环**（`setConversationId` 已落进两个 `@Transactional` 方法）。
> **依据**：实例已闭环：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/MessageAtomicService.java:106,138`（`msg.setConversationId(conv.getId())`）· 守门 `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageTransactionWiringTest.java` ⇒ **证据强度：源码行号 + 真 PG 守门在仓**。

**现象**：修完 P0 后做变异验证（把刚改好的代码改回缺陷态，确认新测试变红）。
**第一刀就翻车**：

```
变异：删掉 createOrSendMessageTx 里的 msg.setConversationId(conv.getId())
结果：8 条用例全部照常通过 ⇒ 该分支的 conversation_id 缺陷本来漏网
```

**根因**：新写的 7 条用例**全部只走「会话不存在 → 新建」这条路径**，
`createOrSendMessageTx`（已有会话追加消息）**一条都没覆盖**。
原因不是疏忽，是**构造输入的难度不对称**：走追加分支要先建会话再让**对端**回一条。

**⇒ 元规则**：一个模块的写路径有 N 条（新建 / 追加 / 批量 / 更新），
而测试往往只挑「最容易构造输入」的那条 —— **恰好最需要验证的那条最容易被跳过**。

**下次怎么防**：

1. **变异测试不是可选的收尾，是必须项**；
2. 变异点要覆盖**每一条独立的写路径**，而不只是「刚改的那一行」；
3. 判据：**把每条写路径分别破坏一次，测试必须每条都红**。只红一条 ⇒ 其余路径仍是假绿；
4. ⚠️ **本仓专属陷阱**：每次变异与每次还原都必须 `mvn clean` ——
   增量编译会把变异 class 留在 `target/classes`，导致「还原后复跑」测的不是当前源码，
   标准闭环会得出**假的红灯**，进而得出「测试不稳定 / 修不好」的**假结论**（比不测更坏）；
5. ⚠️ 同源的第二个陷阱：**验证时先看退出码**，再看测试统计。
   用 `grep` 过滤 mvn 输出时，**编译错误行的格式（`[ERROR] /path:[line,col] …`）一条都匹配不上**，
   输出里空空的，看起来像「跑完了、没报错」。

---

## PIT-MESSAGE-17 「分页接口的 `total` 与 `items` 必须成对断言」 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-009` ⇒ **新号**：`PIT-MESSAGE-17`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 本条的两个实例都已消除：① `total` 现在与 `items` **共用同一个带过滤的 query**；② `offset <= 0` 的 guard 已落到共享分页 helper（Go 侧「两段各自有条件」）⇒ 本轮只改 `docs/**`。
> **依据**：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/mapper/NotificationMapper.java:79,88`（`countByRecipient` 内同样带 `if (unreadOnly)`）· `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/mapper/GoStylePagination.java:6,12`（`offset <= 0` 时整段 OFFSET 被跳过，对齐 Go）⇒ **证据强度：源码行号**。

**现象**：`listNotifications(unread_only=true)` 时，列表带了 `is_read=false` 过滤、
**计数没带** ⇒ `total` 恒等于全部条数 ⇒ 前端分页器按它算总页数会多翻空页。
两行相距 7 行、变量名都是同一个 `unreadOnly`，**代码评审里极难一眼看出**。

**根因**：把过滤条件理解成「列表查询条件」而不是「计数条件」——
这在 Spring/MyBatis 里看起来完全合理 ⇒ §26 的 Counterfactual = YES，属典型迁移期漂移。

**下次怎么防**：断言分页接口时 **`total` 与 `items` 必须成对断言**，
且**过滤条件要逐条覆盖到计数**，而不只是列表。
（⇒ 本仓已升级为通用纪律 A，`message-api` / `like-*` / `users-*` 同样适用。）

**同类形态（本轮新增观察）**：Go 是 `if offset > 0 { db.Offset(...) }` —— **负数被静默忽略**；
RAG 写成了无条件拼接 `w.last("LIMIT n OFFSET m")` —— **负数直接进 SQL 变成语法错误**。
⇒ **「Go 用条件分支、RAG 用表达式」这个形态本身就是高危信号**：
凡是 Go 侧带 `if x > 0` 的 guard，RAG 侧就必须有一条对应的 guard，
哪怕当前配置下永远为真。**当前配置下永远为真 ≠ 语义等价。**

---

## PIT-MESSAGE-18 跨组件不变量**无法单侧验完**（Flyway 账本） ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-010` ⇒ **新号**：`PIT-MESSAGE-18`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— message 域这一条已闭环：独立账本表名已配、三进程同库并发实测已跑 ⇒ 本轮只改 `docs/**`。
> **依据**：`service/message/message-rpc/src/main/resources/application.yml:105`（`table: flyway_schema_history_message`）· `:102`（`baseline-version: 0`）· 守门 `tools/difftest/check-flyway-ledger.py`（账本隔离，EXIT=0）· 运行时同库共存 `tools/difftest/check-flyway-concurrent.sh` ⇒ **证据强度：配置行号 + 守门脚本在仓**。

**现象**：`spring.flyway.table: flyway_schema_history_message` 是**共享 schema 级**问题
（`message-rpc` / `like-rpc` / `article-rpc` 同库 `first_db`、同 schema `public`）。
只看本模块，配了 `table:` 就算「已修」。

**2026-10-23 的实测与结论**：

- ✅ **单模块运行时确实生效**：真 PG 启动日志出现
  `Creating Schema History table "public"."flyway_schema_history_message" ...`
  与 `Successfully applied 3 migrations to schema "public", now at version v3`。
  ⇒ **关闭了「运行时是否按该表名建账本」这项 UNKNOWN（对 message-rpc 单侧）**；
- ❌ **三模块同库下不撞**仍然**未验证** ⇒ `UNRESOLVED-MSG-008`：
  必须**三个模块同时连同一个 `first_db`**，`\dt` 确认三本账、且各自 3 条 `success`。

**⇒ 教训**：「静态配置正确」「单侧运行时正确」「跨组件正确」是**三件事**，
第三件只能由**同时起多个组件**的验证来关闭。
**在台账里必须显式写明「本条验到第几层」**，否则后人会把单侧结论当成闭环结论。

---

## PIT-MESSAGE-19 附带的稳健性缺口：`TIMESTAMP` 不自描述瞬时 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-011` ⇒ **新号**：`PIT-MESSAGE-19`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 根治迁移已落地（8 列全改 `TIMESTAMPTZ`），双侧活体 DDL 对拍实测两侧 `data_type` **0 差异** ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/message/message-rpc/src/main/resources/db/migration/V5__message_time_columns_timestamptz.sql`（8 列全部 `TIMESTAMPTZ`）· 对拍脚本 `tools/difftest/run-message-ddl-parity.sh` ⇒ **证据强度：迁移文件在仓 + 对拍脚本在仓**。

**现象**：真源列类型是 `timestamptz`，DSN 钉 `TimeZone=Asia/Shanghai` ⇒ `.Unix()` 与时区无关。
RAG 侧列是 `TIMESTAMP`（无时区），读侧改用 `atZone(ZoneId.systemDefault())` 后**自洽**了 ——
但自洽的前提是**「一个 DB 只由同一时区的 JVM 写」**。

以下三种情形会重新错位：迁移期新旧实例并存且时区不同 / 换时区后回读历史数据 /
同一张库被另一个时区的进程写入。根治需要一条 `V4` 迁移把列改成 `TIMESTAMPTZ`。
**本轮未做该迁移**（行为已正确、`ALTER TABLE` 有锁表风险、且 DDL 尚未被系统性核对）。

> ⚠️ **2026-10-24 第十一轮更正 —— 这条建议**已经过时**：
> **该迁移已由 `V5__message_time_columns_timestamptz.sql` 做完**（8 列全部 `TIMESTAMPTZ`），
> 且第十轮**双侧活体 DDL 对拍**实测两侧 `data_type` **0 差异**。
> ⇒ **不要再照着本条去补 V4 迁移**（`UNRESOLVED-MSG-009` 也已于第十一轮关闭）。
> ⇒ 本条保留为历史留档：**它记录的是「当时为什么没做」以及「为什么后来做了」**。

**教训**：**「读侧修好」与「列类型修好」是两件事**，
只有后者能让数据自带 epoch 信息、不再依赖「部署环境永远一致」这个隐含假设。
台账里必须把这条写清楚，否则「已修」会被读成「彻底解决」。
---

<!-- @new 2026-10-23 23:0x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

## 2026-10-23 第四轮的 4 条元教训

### 1. 🔴 **起不来整个 Go 服务，不代表拿不到 Go 的 schema**

`tools/difftest/check-ddl-static.py` 需要一个装好 Go 侧 schema 的容器，
而 Go 真源起不来（依赖 `etcd:2379`）⇒ message 域的 DDL 对照一直是 `NOT CHECKED`。

⇒ 但**真源的建表行为全部来自一行**：
`service/message/rpc/internal/model/db.go:42` 的 `db.AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`。
用**真源同一个 `go.mod`**（`gorm.io/driver/postgres v1.6.0`）写 80 行最小程序，
struct 与 `data_model.go` 逐字段逐 tag 一致，对真 PG 跑一次即可。
⇒ **产出 55 行 schema dump**，与 RAG 侧三个迁移的 dump 逐行 diff。

⚠️ 纪律：**「拿不到活体证据」时，先问「那个活体到底由哪几行代码构成」**。
本例中被误判为「不可测」的，其实是一行可以被单独调用的函数。
（对照 §3.3「框架机制必须验证实际生效」的同一思路，只是这次的对象是 GORM。）

### 2. 🔴 **列类型差一条，会带着「可空性」和「默认值」一起差**

本轮首次把 NOT NULL / DEFAULT 从「NOT CHECKED」变成「已查」，结果是**三处一起不同**：

```
Go  message_notification.created_at | timestamptz | 可空  | 无默认
RAG message_notification.created_at | timestamp   | NOT NULL | DEFAULT CURRENT_TIMESTAMP
```

⇒ 只改类型（`TIMESTAMP ⇒ TIMESTAMPTZ`）会**留下另两处的差异**，
而另两处同样影响行为（谁写进去、能不能写 null）。
⇒ **纪律**：DDL 对照的判定单位是「列 + 可空 + 默认」三元组，
不是「列类型」。users 域 `users_user` 的同类处置已有先例可循。

### 3. 🔴 **「给 `ADD CONSTRAINT` 加 `IF NOT EXISTS」这条修法是死的 —— 而且很容易被当成显然**

`UNRESOLVED-MSG-005` 一直挂着「给 V2 的 `ALTER TABLE … ADD CONSTRAINT` 加 `IF NOT EXISTS`」。
🔴 实测：**PostgreSQL 16 不支持这个语法**（`ERROR: syntax error at or near "NOT"`）。
支持的是 `CREATE INDEX IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` / `DROP … IF EXISTS`。

⇒ 顺带发现**更根本的一点**：真源的 `uniqueIndex:` tag 建出来的是**唯一索引**
（`pg_indexes` 有行、`pg_constraint` 无行），根本不是约束。
所以正确的修法不是「让约束幂等」，而是**换成索引**（`CREATE UNIQUE INDEX IF NOT EXISTS`）
—— 一次同时解决「对象类型与 Go 一致」和「可幂等重放」两个问题。
⇒ **纪律**：提出修法前，先在**真容器**上验证那个语法/接口存在。

### 4. 🔴 **改已应用的 Flyway 迁移 = 撞校验和**

直觉修法是「直接改 V2 的那一行」。但 V2 已经在若干库上应用过，
改正文会改变 Flyway 的 checksum ⇒ `validate-on-migrate` 直接失败。
⇒ **纪律**：幂等性/对象类型这类修正，一律**新增 `V4`**（`DROP … IF EXISTS` + `CREATE … IF NOT EXISTS`），
让「已跑过」与「没跑过」的库都能收敛到同一状态。
⚠️ 并且要**实测连跑两遍**都 `exit=0` —— 本轮就是这么验的（8 次执行全 0）。

### 5. 🟡 附带：`@EnabledIfDockerAvailable` 的正确处理方式

`MessageTransactionWiringTest` 带着 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`
—— 正是 `component-coverage-matrix.md` 点名的反模式。
**本模块无实际危害**（`message-rpc` 是已配 `RYUK_DISABLED` surefire 注入的模块，本轮实测 `skipped=0`），
但该注解理应删掉：它唯一的作用是把「Docker 不可用」与「Ryuk 镜像拉不下来」
压成同一个 `false`。**本轮未删** —— 它现在无害，而删它会让本轮的测试计数变化混进语义修复里，
不如单独一轮处理干净。

---

<!-- @new 2026-10-23 23:5x 第六轮（V-016 recover）新增 2 条 -->

## PIT-MESSAGE-20 grpc-java 的 unary 响应**必须** `onNext` + `onCompleted` 成对 —— 漏了会**挂死**而不是失败 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-012` ⇒ **新号**：`PIT-MESSAGE-20`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— grpc-java 的 unary 响应「`onNext` + `onCompleted` 必须成对」是**框架硬约束**，不会消失 ⇒ 仍生效；本轮若写任何 gRPC 侧的实现/测试结论，仍须先核这条。
> **依据**：`service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageRpcRecoverInterceptorWiringTest.java`（真 in-process gRPC server · 含 class 级 `@Timeout` 与 stub deadline 两道护栏） ⇒ **证据强度：守门在仓**。
>
> ⚠️ **2026-10-29 真实性复核 · 三个 jar 内指针的可复算证据（**行号不变，只补证据**）**：
> 下方栈帧里的 `ClientCalls.java:717`（`io.grpc.stub.ClientCalls$ThreadlessExecutor.waitAndDrain`）·
> `ClientCalls.java:159`（`io.grpc.stub.ClientCalls.blockingUnaryCall`）· `ServerCalls.java:394`
> （`io.grpc.stub.ServerCalls` 的 `onCompleted`）—— 这三个类**只存在于 grpc-java 的 jar 里**（本仓无同名 `.java`）
> ⇒ 早期版本的本审计工具会把它们报成「🔴 落空」⇒ **那是工具缺第 4 个解析根，不是台账写错**。
> ⇒ **行号一律不改**（改成别的行号会与 jstack 现场对不上）；**证据补成可复算**：
>
> | 指针 | jar（`~/.m2/repository/io/grpc/grpc-stub/`） | 那一行的实际内容 | 与本条现象的对应 |
> |---|---|---|---|
> | `ClientCalls.java:717` | `grpc-stub-1.66.0-sources.jar` ⇒ `io/grpc/stub/ClientCalls.java` | `LockSupport.park(this);` | 对上 jstack 里的 `WAITING (parking)` |
> | `ClientCalls.java:159` | 同上 | `executor.waitAndDrain();` | 对上 `blockingUnaryCall` 那一帧 |
> | `ServerCalls.java:394` | 同上 | `public void onCompleted() {` | 对上本条引用的 `onCompleted()` 代码块 |
>
> ⇒ **本条结论站得住**；新版本审计工具已把它们归入「📦 jar 内第三方源码」单列档（**不计失败**）。
> ⚠️ 注意：**仓内另有 grpc-stub 1.62.2 / 1.63.0** ⇒ 引用时须写明版本（本条以 **1.66.0** 为准）。
> 本条的**仓内可核验证据**仍是上面的守门测试（真 in-process gRPC server 实跑）。
> 核对日期 2026-10-29。

**现象**：本轮新写 `MessageRpcRecoverInterceptorWiringTest`（真 in-process gRPC server），
第一次跑 —— **不报失败、不报错，整轮 surefire 挂死 4 分钟**（只能 `jstack` 找到）。

```
"main" ... WAITING (parking)
  at io.grpc.stub.ClientCalls$ThreadlessExecutor.waitAndDrain(ClientCalls.java:717)
  at io.grpc.stub.ClientCalls.blockingUnaryCall(ClientCalls.java:159)
  at MessageRpcRecoverInterceptorWiringTest.processSurvivesAndServesNextRequest(...:167)
```

**根因**：service impl 只写了 `observer.onNext(resp)`，**没写 `observer.onCompleted()`**。
grpc-java 的 `ServerCalls.java:394`：

```java
@Override
public void onCompleted() {
  call.close(Status.OK, new Metadata());   // ← 只有这里才关 call
  completed = true;
}
```

⇒ `onNext` 只 `call.sendMessage(...)`，**call 永不关闭** ⇒ 客户端 `blockingUnaryCall` 无限等待。

**为什么特别阴**：

1. **不是失败，是挂起** —— CI 上表现为「这个测试类卡住」，而不是「红了」；
   本轮整轮构建因此超时，只能手动 `pkill` surefire。
2. **只在「成功路径」上挂** —— panic 路径因为 recover 主动 `call.close(...)`，
   反而**正常返回** ⇒ 第一条 panic 用例是绿的，挂的是**后面那条「panic 后还能继续服务」**的用例。
3. **生成的 stub 不会提醒你** —— `MessageServiceImplBase` 的抽象方法签名是 `StreamObserver<RespT>`，
   生成的示例代码是 `onNext` + `onCompleted` 成对，但**编译器不强制**。

**⇒ 元规则**：**写 in-process gRPC 测试时，service impl 的 `onNext` 与 `onCompleted` 必须成对出现。**
panic / error 路径靠 recover 兜住时尤其容易漏 —— 因为那条路径自己会 `close`。

**下次怎么防**（本轮已落地两道护栏）：

1. **stub 上加 deadline**：`stub.withDeadlineAfter(10, TimeUnit.SECONDS)`
   ⇒ 漏 `onCompleted` 时会抛 `DEADLINE_EXCEEDED`（**失败**）而不是挂死。
   （`follow` 域的 `FollowGrpcInterceptorWiringTest` 早就在用这招，本域是跟着它学的。）
2. **类级 `@Timeout`**：`@Timeout(value = 60, unit = TimeUnit.SECONDS)`
   ⇒ 连 deadline 都没生效时的最后一道。
3. ⚠️ **别只靠 `assertThrows`**：本轮第一版的 `assertThrows(StatusRuntimeException.class, …)`
   在 panic 路径上是对的，**恰恰因此掩盖了后面那条用例的挂死**。

---

## PIT-MESSAGE-21 判「有没有等价实现」必须 grep **实现**，不是 grep **依赖** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-013` ⇒ **新号**：`PIT-MESSAGE-21`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「判『有没有等价实现』要 grep **实现**、不是 grep 依赖清单」今天仍成立（本仓既定策略就是「不引依赖、自己逐行复刻」）⇒ 本轮凡在台账里写「本仓没有 X / 只能引 Y」的论断，仍必须先 grep 实现。
> **依据**：复刻实现 `service/common/src/main/java/com/rag/common/protection/GoStyleBreaker.java` 与 `service/common/src/main/java/com/rag/common/protection/GoStyleAdaptiveShedder.java` · 守门 `service/common/src/test/java/com/rag/common/protection/GoStyleRpcProtectionChainTest.java:91,152,184` ⇒ **证据强度：源码行号 + 守门在仓**。

**现象**：本轮写 V-016 处置方案时，对 breaker / shedding 下了这个判断：

> 「Java 侧要复刻**只能引 `resilience4j`**（**本仓当前零熔断依赖**，已 grep 全仓确认），
>  而 resilience4j 是固定阈值 + 固定开路时长 ⇒ **算法不同、参数不可能逐字对齐**」

**这个判断是错的** —— 而且是**事实性错误**，写进了台账，险些让用户按错误成本估计拍板。

**实测事实**：

```
service/common/src/main/java/com/rag/common/protection/
  GoStyleBreaker.java          278 行  ← go-zero core/breaker/googlebreaker.go 的逐行复刻
  GoStyleAdaptiveShedder.java  373 行  ← go-zero core/load/adaptiveshedder.go 的判据复刻
  GoStyleBreakerFilter.java    130 行  ← REST 侧的 servlet Filter 壳
  GoStyleCpuSheddingFilter.java 110 行  ← 同上
```

两者的类注释里逐字列了 Go 的常量（`window=10s / buckets=40 / k=1.5 / minK=1.1 / protection=5 /
forcePassDuration=1s`）与 `accept()` 的同构运算顺序，还专门标出「**最容易被顺手改好的三处语义**」
（概率性拒绝 / force-pass 保底 / 只算已完成桶）—— **算法就是同一套，参数逐个照抄**。
并且都有测试（`GoStyleBreakerTest` 8 例、`GoStyleAdaptiveShedderTest` 10 例，本轮实测全绿）。

**根因**：**只查了 `pom.xml` 里有没有 resilience4j，就断言「无等价物」**。
而**本仓的既定策略恰恰是「不引依赖、自己逐行复刻」** ——
`service/common/protection` 整个包（1790 行）就是这么来的。

**⇒ 元规则**：

1. **判「有没有等价物」要 grep 实现，不是 grep 依赖清单。**
   尤其在本仓：**没有某个第三方依赖 ≠ 没有对应能力**，因为能力可能是手写复刻的。
2. **下「成本高 / 不可能对齐」这种结论前，必须把候选实现逐个读完**
   （本轮读了类注释 + API 表面 + 既有测试，三个信息加起来 5 分钟就能推翻那个判断）。
3. ⚠️ **推论**：台账里凡是「只能引 X」「Y 不可能对齐」这类**否定式断言**，
   都应当被视为**高危待验项** —— 它们既没有被机械检查保护，又最容易让用户基于错误前提拍板。

**⇒ 顺带产出**：`DRIFT/message/rpc.md` 的「状态更正 #7」已把那条错误判断连同证据一起更正，
并在 `SD-2026-10-23-07` 里写明「补这两条**不得**引入 resilience4j」。

---

---

<!-- @new 2026-10-24 00:0x 第七轮（V-016 breaker/shedding）新增 3 条 -->

## PIT-MESSAGE-22 串拦截器链时**不能把 `next` 直接透传** —— 那样内层 interceptor 永远不被调用 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-014` ⇒ **新号**：`PIT-MESSAGE-22`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「每加一层拦截器，都要问内层那个 interceptor 的 `interceptCall` 被调到了吗」这条**契约**今天仍成立 —— 共享链 `GoStyleRpcProtectionChain` 会被 10 个 rpc 域共用，任何一层新加都要重验 ⇒ 仍生效。
> **依据**：共享链 `service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java` · 守门 `service/common/src/test/java/com/rag/common/protection/GoStyleRpcProtectionChainTest.java:184`（`buildChainReachesBusinessInterceptor`）⇒ **证据强度：源码行号 + 守门在仓**。

**现象**：给 message-rpc 补服务端熔断/降载时，把链的定义点做进共享模块
（`GoStyleRpcProtectionChain`），第一版把 `breakerThen` / `sheddingThen` 写成：

```java
public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(call, headers, next) {
    return breaker.interceptCall(call, headers, next);   // ❌ next 直接透传
}
```

⇒ **业务 interceptor（超时/trace）永远不会被调用**，而：
- 编译通过、启动通过、上下文正常；
- 熔断/降载**看起来**在工作（它们在链上）；
- **不报任何错**。

**根因**：grpc 的 interceptor 链是**嵌套**关系：
外层拦截器拿到 `ServerCallHandler next`，**必须自己决定**要不要调 `next.startCall(...)`。
把 `next` 透传给内层拦截器，等于**跳过**了中间那一层的 `interceptCall`。
⇒ 正确写法是**手写一层 delegate**（本仓 `recoverThen` 早就是这么写的）：

```java
ServerCallHandler<ReqT,RespT> delegateToInner = new ServerCallHandler<ReqT,RespT>() {
    @Override public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT,RespT> c, Metadata h) {
        return inner.interceptCall(c, h, next);      // ✅ 调 inner，不是透传 next
    }
};
return breaker.interceptCall(call, headers, delegateToInner);
```

**⇒ 元规则**：**每加一层拦截器，都要问「内层那个 interceptor 的 `interceptCall` 被调到了吗」。**
链序的正确性**不能靠读代码确认** —— 本轮就是靠
`GoStyleRpcProtectionChainTest#buildChainReachesBusinessInterceptor` 当场抓到的。

**⇒ 下次怎么防**：写完链就用**一个计数型的假业务 interceptor**跑一遍，
断言它的 `interceptCall` **确实被调到**（本轮那条用例第一版就是红的）。

---

## PIT-MESSAGE-23 拒绝必须关「**本层包装过的** call」—— 关原始 call ⇒ 外层看不到 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-015` ⇒ **新号**：`PIT-MESSAGE-23`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「拒绝必须关本层包装过的 call」这条**契约**今天仍成立（Go 与 grpc-java 的执行模型差异是既成事实）⇒ 仍生效。
> **依据**：守门 `service/common/src/test/java/com/rag/common/protection/GoStyleRpcProtectionChainTest.java:91`（`sheddingRejectionIsVisibleToTheOuterBreaker`）⇒ **证据强度：守门在仓**。

**现象**：降载拦截器拒绝请求时，第一版写 `call.close(...)`（原始 call）。
结果：**外层的熔断器永远开不了路**。

**根因**：熔断器的判据观察点是 `ServerCall#close(status, trailers)`，
它**只在自己包过的那一层**才被触发。外层拦截器包的是 `watched`，
内层拦截器若直接关**原始** call，就**跳过了**外层的 `watched` ⇒
外层熔断器**看不到**这次降载 ⇒ 记不到失败 ⇒ 熔断永远开不了。

**而 Go 侧是能看到的**：Go 的链是**返回值冒泡**，
`UnarySheddingInterceptor` 返回的 `RESOURCE_EXHAUSTED` 会**穿过**外层 `UnaryBreakerInterceptor`
的 `handler(ctx, req)` 调用 ⇒ breaker 的 `doReq` 拿到它 ⇒ 记成失败
（`RESOURCE_EXHAUSTED` 在 Go 的 6 码失败名单里）⇒ **持续降载最终把熔断器打开**。

⇒ 这是 Go 与 grpc-java 的**执行模型差异**，必须显式补上：
**拒绝要关「自己包过的那一层」，让错误自然穿过外层的包装。**

**⚠️ 顺带一条容易漏的**：拒绝路径**不结算 promise** ——
Go 的 `defer` 是在 `Allow()` 成功**之后**才注册的（`sheddinginterceptor.go:24-31` 先 Allow，`:36` 才 defer）
⇒ 拒绝时既不 `Pass()` 也不 `Fail()`。本仓第一版会在拒绝时也走 `close` 的结算分支，
靠一个 `rejected` 标志位纠正。

**⇒ 下次怎么防**：链里每有一个「拒绝型」拦截器，就要有一条用例证明
**外层能观察到它的拒绝**（本轮：`GoStyleRpcProtectionChainTest#sheddingRejectionIsVisibleToTheOuterBreaker`）。

---

## PIT-MESSAGE-24 被 recover 兜住的异常，会让链路看起来「没被调用」 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-016` ⇒ **新号**：`PIT-MESSAGE-24`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「『零交互』有两种成因：链没接上 / 链里抛了异常被 recover 吞了」这条**判读规则**今天仍成立 ⇒ 本轮若以「零交互」为据写任何结论，必须同时给出 recover 的 ERROR 日志判据。
> **依据**：守门 `service/common/src/test/java/com/rag/common/protection/GoStyleRpcProtectionChainTest.java:152`（`recoverIsOutermostSoItCatchesInnerPanics`）⇒ **证据强度：守门在仓**。

**现象**：装配层用例报 `Wanted but not invoked ... there were zero interactions with this mock`
—— 业务 interceptor **一次都没被调到**。

**真因不在业务侧，在链里面**：Mockito 的 `ServerCall` mock 对 `getMethodDescriptor()`
默认返回 **null** ⇒ 链上的熔断器取隔离 key 时 `call.getMethodDescriptor().getFullMethodName()`
抛 **NPE** ⇒ 而这个 NPE 被**最外层的 recover 静默兜住**（关掉调用、返回 `INTERNAL`）
⇒ 现象是「业务 interceptor 零交互」。

**⇒ 元规则**：
1. **「零交互」有两种成因**：① 链没接上；② 链里抛了异常被 recover 吞了。
   **区分办法：看 recover 的 ERROR 日志**（`gRPC handler panic: …` + 栈）——
   有日志 ⇒ 是 ②。
2. ⇒ **测「链路是否接通」时，mock 必须补齐被链上代码用到的 getter**
   （本轮是 `getMethodDescriptor()`）；否则你在测 mock 的默认值，不是测链路。

**⇒ 附带的通用提醒**：**recover 的兜底会把链内异常变成「静默的 INTERNAL」**。
生产上排查「某个 interceptor 好像没生效」时，第一件事是去翻 recover 的 ERROR 日志，
而不是盯着业务日志 —— 业务侧只会看到一个普通的 `INTERNAL`。

---


---

## PIT-MESSAGE-25 `set -euo pipefail` + 命令替换里的 `lsof` ⇒ **脚本静默消失** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-017` ⇒ **新号**：`PIT-MESSAGE-25`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 共享 harness 里那处 `lsof` 管道已兜成 0 ⇒ 触发条件消除；本轮只改 `docs/**`、不动脚本。
> **依据**：`tools/difftest/run.sh:156`（`who="$( { lsof … ; } || true )"`） ⇒ **证据强度：脚本行号**。

**现象**：`tools/difftest/run.sh` 跑着跑着就没了，日志停在某一行，**一个字都不报错**，
进程也没留下，日志文件是空的。

**根因**：`lsof` 在「**没有匹配**」时退出码是 **1**；脚本开头是 `set -euo pipefail`
⇒ 命令替换 `who="$(lsof … | tail | head | awk)"` 的**退出码非零**
⇒ `set -e` 立刻终止整个脚本。
⚠️ 而且它**不报错** —— 赋值失败不是「命令失败」，`set -e` 不打任何东西。

**为什么难查**：① 症状是「没输出」，不像错误；② `head -1` 还会提前关管道
⇒ `lsof` 收到 SIGPIPE（141）⇒ **同源**；③ 退出码非零，但脚本被 harness 记成「正常结束」。

**修法**：`who="$( { lsof … ; } || true )"` —— 把整条管道的退出码兜成 0。

**通用教训**：**`set -e` 下的命令替换会把「探测型命令的非零退出码」升级成「脚本猝死」**。
⇒ 任何 `$( … )` 里放 `grep` / `lsof` / `diff` 这类**「没找到就返回 1」**的命令，
   都必须显式 `|| true`。

---

## PIT-MESSAGE-26 占位符名字**跨模块不统一** ⇒ 一整类模块在测试环境里**根本起不来** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-018` ⇒ **新号**：`PIT-MESSAGE-26`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— harness 现在**同时导出** `POSTGRES_ADDR` 与 `POSTGRES_HOST` 两个别名 ⇒ 7 个 rpc 模块的占位符不再起不来 ⇒ 本轮只改 `docs/**`。
> **依据**：`tools/difftest/run.sh:195`（`export POSTGRES_ADDR=127.0.0.1 POSTGRES_HOST=127.0.0.1 …`） ⇒ **证据强度：脚本行号**。

**现象**：`message-rpc` 在 difftest 环境里报
`java.net.UnknownHostException: postgres` ⇒ Spring 连的是容器里的 `postgres` 主机名，
而对拍环境的 PG 跑在 `127.0.0.1:<端口>`。

**根因**：全仓的 jdbc url 占位符**名字不统一**：

| 占位符 | 用它的模块 |
|---|---|
| `${POSTGRES_ADDR:postgres}` | `user-rpc` / `admin-rpc` |
| `${POSTGRES_HOST:postgres}` | `message-rpc` / `article-rpc` / `comment-rpc` / `favorite-rpc` / `follow-rpc` / `like-rpc` / `task-rpc` |

而 harness 只导出 `POSTGRES_ADDR` ⇒ **7 个 rpc 模块在对拍环境里全部起不来**。

⚠️ **这条的真正意义**：它说明 **「message 域从没被 difftest 覆盖过」不是因为没人写脚本，
而是因为脚本起不来** —— 而「脚本就位」在台账里被当成了「只差跑一次」。

**通用教训**：**「脚本就位」与「脚本跑得起来」是两件事**。
⇒ 共享 harness 里凡是**依赖应用侧环境变量名**的地方，
   必须**按应用侧的写法导出全部别名**，而不是按「我印象里的那个名字」。

---

## PIT-MESSAGE-27 `baseline-on-migrate: true` 会**顶掉 V1** ⇒ 核心表永远建不出来 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-019` ⇒ **新号**：`PIT-MESSAGE-27`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— message-rpc 已显式钉 `baseline-version: 0` ⇒ baseline 打在 V1 之下 ⇒ V1..V5 全部照常执行 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/message/message-rpc/src/main/resources/application.yml:102`（`baseline-version: 0`）· `:105`（独立账本表名）· 迁移从 V1 起步（`db/migration/V1__create_message_notification.sql`） ⇒ **证据强度：配置行号 + 迁移清单**。

**现象**：`message-rpc` 在共享库（非空 schema）里启动失败：
`Migration of schema "public" to version "5 …" failed` ⇒ `relation "message_notification" does not exist`。

**根因**：`baseline-on-migrate: true` + **非空 schema** + **没有自己的账本**
⇒ Flyway 在 **V1** 处打 baseline 标记 ⇒ **V1 的 SQL 从未执行** ⇒ V1 建的那张表不存在
⇒ 后面任何碰它的迁移都失败。

⚠️ **Testcontainers 永远测不出来**：每个用例都是**全新空库** + 只有本模块一个进程
⇒ schema 是空的 ⇒ baseline 根本不触发。

**修法**：`spring.flyway.baseline-version: 0` ⇒ 基线打在 V1 **之下** ⇒ V1..Vn 全部照常执行。
⚠️ **不要**改成 `baseline-on-migrate: false` —— 那会让「非空 schema 且无账本」**直接抛异常退出**，更糟。

**守门手法（在 `@DynamicPropertySource` 里，不是 `@BeforeEach`）**：
先建一张无关表把 schema 弄成非空，再让 Spring 上下文启动（Flyway 才跑）。
⚠️ 放 `@BeforeEach` 无效 —— 那跑在上下文**之后**，Flyway 早就迁移完了。

**通用教训**：**「共享同一个 schema 的多个迁移方」+「各自独立账本」+「baseline-on-migrate」**
= **后启动者那些版本号 ≤ baseline 版本的迁移会被静默顶掉**。
⇒ 独立账本只解决了「账本互相污染」，**没解决「baseline 顶掉迁移」**。

⚠️ **准确表述（2026-10-24 实测更正）**：不是「第一个迁移被顶掉」，而是
「**版本号 ≤ baseline 版本**的迁移被顶掉」——
因为 baseline 默认打在 **version 1** ⇒ 只有 **V1** 会被跳过。
⇒ **是否受害取决于该模块最小的迁移版本号**：
  · `message-rpc` 迁移从 **V1** 起步 ⇒ V1 被顶掉 ⇒ **V5 引用它 ⇒ 启动失败**；
  · `like-rpc` 迁移从 **V1** 起步 ⇒ V1 被顶掉 ⇒ 但 V2..V6 不引用它 ⇒ **能起，运行期缺表**；
  · `article-rpc` 迁移从 **V8** 起步 ⇒ 没有 V1 可顶 ⇒ **完全不受影响**（实测 `ls` 确认只有 V8/V9/V10）。
⇒ **判定方法**：先 `ls` 该模块 `db/migration/` 看**最小版本号**，再比 baseline 版本。

---

## PIT-MESSAGE-28 `mvn package` 成功 ≠ **产物可运行** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-020` ⇒ **新号**：`PIT-MESSAGE-28`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— message-api 的 pom 已补 `<build>` 段与 `spring-boot-maven-plugin`，产物已是可执行 jar ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/message/message-api/pom.xml:169`（`<build>`）+ `:190`（`spring-boot-maven-plugin`）· 守门 `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiJarExecutableWiringTest.java` ⇒ **证据强度：pom 行号 + 守门在仓**。

**现象**：`message-api` 的 jar 只有 **76 KB**（而 `message-rpc` 的可执行 jar 是 **69 MB**）
⇒ `java -jar message-api-*.jar` 报「**中没有主清单属性**」。

**根因**：`service/message/message-api/pom.xml` **完全没有 `<build>` 段**
⇒ 没有 `spring-boot-maven-plugin` 的 repackage ⇒ 打出来的是**普通 library jar**。

⚠️ **为什么测试全绿**：`@SpringBootTest` 从 `target/classes` 起上下文，**根本不碰 jar**
⇒ 「测试全绿 + mvn package 成功」与「产物根本起不来」可以同时成立。

**修法**：补 `<build>` 段（`spring-boot-maven-plugin`）⇒ 产物 71 MB，
`Main-Class: JarLauncher` / `Start-Class: MessageApiApplication`。

**通用教训**：**模块的「可部署性」要单独验一次** —— 至少跑一次 `java -jar <产物>`。
⚠️ 只跑 `mvn package` 只证明了「编译过了」。

---

## PIT-MESSAGE-29 多工作树**共用同一个 `~/.m2`** ⇒ 共享模块的 SNAPSHOT 会被**别人**覆盖 ⇒ 单模块测试随机报 `NoClassDefFoundError` ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-021` ⇒ **新号**：`PIT-MESSAGE-29`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「`~/.m2` 里的 SNAPSHOT 是『最后写入者赢』的共享可变状态」今天**仍然成立** —— AGENTS.md §5.3d 的多工作树并发形态没变 ⇒ 本轮任何 `mvn` 命令仍须带 `-am` 从本工作树源码构建 ⇒ 仍生效。
> **依据**：AGENTS.md §5.3d（波次工作树 G1~G4，均从 `do-my-own-gh` 切 · 并发 `mvn install` 共享 `~/.m2`）· 判别式 `unzip -l ~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar | grep -c GoStyleRpcProtectionChain` ⇒ **证据强度：仓库政策 + 源码路径在仓**。

**现象**：`mvn -o -B -pl service/message/message-rpc test`（**故意不带 `-am`**）
⇒ 几乎所有 `@SpringBootTest` 用例报 `Failed to load ApplicationContext` ⇒ 最内层 cause：

```
Caused by: java.lang.NoClassDefFoundError: com/rag/common/protection/GoStyleRpcProtectionChain
	at com.rag.message.message.rpc.MessageRpcApplication.lambda$rpcInterceptorConfigurer$0(MessageRpcApplication.java:135)
```

⚠️ **迷惑性极强**：那个类**明明就在本工作树的源码里**
（`service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java`），
且同一批用例在 20 分钟前还是**全绿** ⇒ 第一反应必然是「我刚才改坏了」。

**根因**：`~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/` 是**主仓与所有工作树共用**的。
本项目的并发工作流（`docs/Worktree.md`）里，主仓 `do-my-own-gh` 与各域工作树**同时**跑 `mvn install`
⇒ **谁最后 install，谁的版本就占住那个 SNAPSHOT 路径**。

实测样本（2026-10-24 现场）：

| 位置 | `service/common/protection/` 下的类数 | 含 `GoStyleRpcProtectionChain`？ |
|---|---|---|
| 工作树 `align/message` | **12** | ✅ 有（`4aff796` 加的） |
| 主仓 `do-my-own-gh` | **11** | ❌ **没有** |
| `~/.m2` 里的 SNAPSHOT jar（mtime 05:47） | — | ❌ `unzip -l` 计数 = **0** ⇒ 来自主仓那次 install |

⇒ 不带 `-am` 的单模块构建，解析到的 `service/common` 是**主仓那一版** ⇒ 缺类。

**规避**：
1. **单模块测试一律带 `-am`**：`mvn -o -B -pl <module> -am test`
   ⇒ 共享模块从**本工作树源码**重新构建，压根不看 `~/.m2`。
2. ⚠️ **不要**用 `mvn install` 把本工作树的版本装回去当修复 —— **会被别人再次覆盖**，
   只是把「必现」变成「看谁最后 install」。
3. ⚠️ **不要**看到 `NoClassDefFoundError` 就判「代码坏了」⇒ 先查两件事：
   - 该类在**本工作树源码**里在不在？（`ls`）
   - `~/.m2` 那个 SNAPSHOT jar 是**哪一版**？（看 mtime + `unzip -l | grep`）

**判别式（一条命令定性）**：
```bash
unzip -l ~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar \
  | grep -c GoStyleRpcProtectionChain
```
⇒ 计数 **0** 而源码里有 ⇒ 就是这条坑，**不是代码问题** ⇒ 加 `-am` 重跑即可。

**通用教训**：**多工作树并发下，`~/.m2` 里的 SNAPSHOT 是「最后写入者赢」的共享可变状态** ——
它和 `git stash` 一样，是**工作区级状态**，会被并发 agent 互踩（与 `docs/Worktree.md` 禁用 `git stash` 同源）。
⇒ 凡是要验证的行为，**都必须从本工作树源码构建**，不能依赖本地仓库里的 SNAPSHOT。

---

## PIT-MESSAGE-30 「Go 侧有 X 指标」这句话本身**要核到 Go 源码的消费点** —— 否则会照抄出一套 Go 没有的能力 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-022` ⇒ **新号**：`PIT-MESSAGE-30`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「台账里描述 Go 行为的那句话，本身是需要被核实的一手材料」这条**取证纪律**今天仍成立 ⇒ 仍生效。⚠️ 本条的**具体实例已闭环**（`DRIFT-MSG-V-025` 的错误结论已更正、指标未新增）。
> **依据**：实例已闭环：`docs/DRIFT/message/rpc.md` 的 `DRIFT-MSG-V-025`（✅ 已修）· 出处订正见 `service/common/src/main/java/com/rag/common/protection/GoStyleProtectionProperties.java` 的类注释 ⇒ **证据强度：台账状态**。

**背景**：`DRIFT-MSG-V-025` 原记写「Go 侧可以在 `/metrics` 里看到『这个 rpc 降载丢了多少』
『熔断拒了多少』」⇒ 照这句话去实现，结论会是「给 `service/common` 加 micrometer、
暴露 `rag_protection_shedding_total` 之类的 Prometheus 指标」。

**实测 Go 源码（go-zero v1.10.2）后的真相**：

| Go 侧的东西 | 真实暴露方式 | 出处 |
|---|---|---|
| 降载 `total`/`pass`/`drop` | **每分钟一行的日志**（`logx.Statf`）⇒ **不在 Prometheus 里** | `core/load/sheddingstat.go:52-70` |
| 熔断 `markDrop`/`markFailure` | 写进 `b.stat`（`collection.RollingWindow`）⇒ **算法内部状态，根本没对外暴露** | `core/breaker/googlebreaker.go:121-130` |

⇒ **照抄旧结论的后果**：
① 给 20 个组件共享的 `service/common` **加一个 micrometer 依赖**（依赖树影响面）；
② 暴露一个 **Go 侧根本没有**的 Prometheus 指标 ⇒ 运维看板上会出现一个「Go 没有、RAG 有」的指标
⇒ 那是**发明**，不是对齐（违反 §3.2「Go 是唯一基准，不『顺手改好』」）；
③ 而 Go **确实有**的那行日志反而**没做** ⇒ 真正的缺口没补上。

**教训**：**台账里描述 Go 行为的那句话，本身是需要被核实的一手材料**，不是可以直接照抄的需求。
⇒ 凡是「Go 侧有 X」这种断言，必须给出 **Go 的 `文件:行号`（消费点）**；
⇒ 特别地，**「指标」这个词要问清楚它在哪儿**：Prometheus？日志？内存计数？算法内部状态？
—— 这四种的「对应物」完全不同，照错方向会做出一堆用不上的东西。

**同源的另一个坑**：`GoStyleProtectionProperties` 的 javadoc 里把 `CpuThreshold` 的出处写成
`rest/config.go:56`（**REST** 侧的配置）⇒ 而 rpc 路径上真正生效的是
`zrpc/internal/config.go`（值同为 900，结论相同，**但出处标错了**）
⇒ §3.1 要求「行号指向**消费点**」—— REST 的消费点对 rpc 路径不成立。

---

## PIT-MESSAGE-31 **给别的组件挂熔断/降载之前，必须先核它的兜底码口径** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-023` ⇒ **新号**：`PIT-MESSAGE-31`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 的熔断失败判据（6 码白名单）**已冻结不变** ⇒ 「挂 breaker 前必须先核组件兜底码口径」这条今天仍成立 ⇒ 仍生效。
> **依据**：Go `zrpc/internal/codes/accept.go:9-17`（6 码白名单）· `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:110`（`mapToGrpcCode`，`5003` 越界 ⇒ `UNKNOWN`，与 Go 同）⇒ **证据强度：Go 侧 + RAG 侧行号**。

**形态**：熔断的「失败判据」是 **status code**（Go `zrpc/internal/codes/accept.go:9-17` 的 6 码白名单
`{DEADLINE_EXCEEDED, INTERNAL, UNAVAILABLE, DATA_LOSS, UNIMPLEMENTED, RESOURCE_EXHAUSTED}`）。
⇒ **组件的兜底码若与 Go 不一致，挂上 breaker 反而会「把既有漂移激活成新回归」**。

**本轮实测（2026-10-24）逐组件核了两侧**：

| rpc 组件 | Go 的 DB 错误码 | RAG 的 | 挂 breaker 后是否同开路 |
|---|---|---|---|
| `message` | 裸 error ⇒ `UNKNOWN` | `UNKNOWN` | ✅ 两侧都不熔断 |
| `article` | `codes.Internal` | `UNKNOWN` | ⚠️ RAG **熔断更少**（差异**本已存在**，因为 RAG 此前根本没 breaker） |
| `favorite` / `follow` / `user` / `admin` | `codes.Internal` | `INTERNAL` | ✅ 两侧都熔断 |
| `comment` / `like` | `codes.Code(5003)` **越界** ⇒ `UNKNOWN` | `UNKNOWN` | ✅ 两侧都不熔断 |
| `task` | 裸 error ⇒ `UNKNOWN` | `UNKNOWN` | ✅ 两侧都不熔断 |

⚠️ **`comment`/`like` 那两行是本轮最反直觉的发现**：Go 把**业务码**（5003）直接塞进
`status.Error(codes.Code(5003), …)` ⇒ **5003 不是合法 gRPC code（合法 0–16）**
⇒ Go 的 `status.Code(err)` 得到一个**不在 6 码白名单里**的值 ⇒ **不判失败、不熔断**。
⇒ RAG 侧 `CommentGrpcError` / `LikeGrpcError` 早就用 `Status.UNKNOWN` 复刻了这个行为
（它们的类注释里甚至写着「`Status.fromCodeValue(5003)` → code = UNKNOWN」）⇒ **两侧一致**。

**教训**：**「接上一条共享链」不是纯机械改动** ——
链的**判据**（哪些 status 算失败）与**被链观察到的那些 status**（组件自己的错误码口径）必须成对成立。
⇒ 跨域接链之前，先把每个组件的「错误码 → gRPC status」逐条与 Go 对一遍，
再决定这条链对该组件**是否安全**。

---

## PIT-MESSAGE-32 用**字符串下标**断言 XML / YAML 结构 ⇒ 会被**注释里提到的同名字符串**打败 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-024` ⇒ **新号**：`PIT-MESSAGE-32`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 该守门已改成**先剥注释再取下标** ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiJarExecutableWiringTest.java:78`（`stripXmlComments(readPom())`）+ `:101`（`stripXmlComments` 实现）⇒ **证据强度：源码行号**。

**形态**：想断言「pom 的 `<build><plugins>` 里真的有 `spring-boot-maven-plugin`」，
于是写：

```java
int buildIdx  = pom.indexOf("<build>");
int pluginsIdx = pom.indexOf("<plugins>", buildIdx);
int pluginIdx  = pom.indexOf("spring-boot-maven-plugin", buildIdx);
assertThat(pluginIdx).isGreaterThan(pluginsIdx);
```

**实测翻车**（本轮 `MessageApiJarExecutableWiringTest` 第一版就是这么写的）：
那个 pom 的**说明性注释里**就写着「没有 `<build>` 段」「`spring-boot-maven-plugin`」这些词
⇒ `indexOf("<build>")` 先撞到**注释里那一次**（行 157），而不是真正的那次（行 169）
⇒ 断言必红，且**红得莫名其妙**（人看代码会觉得「结构明明是对的」）。

**修法**：**先剥掉注释再取下标**。

```java
private static String stripXmlComments(String xml) {
    return xml.replaceAll("(?s)<!--.*?-->", "");
}
```

**通用教训**：**字符串下标断言 = 在给自己埋「注释一改就假红」的雷**。
⇒ 断言结构优先用**解析器**（XML/YAML parser）；
⇒ 实在要用字符串，也**必须先剥注释**；
⇒ 否则「配置里写了什么」和「配置里**生效**的是什么」会被注释搅在一起
—— 这与 §3.3「配置里没写 ≠ 没开」是**同一个坑的两面**：
**注释里的字面量既可能被当成「没配」，也可能被当成「配了」**。

---

## PIT-MESSAGE-33 变异验证后用 `shutil.copy2` 还原源码 ⇒ **保留旧 mtime** ⇒ maven 增量编译跳过 ⇒ 后续跑的是**变异过的 class** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-025` ⇒ **新号**：`PIT-MESSAGE-33`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 仓内的变异/还原脚本已改用 `copyfile` + `os.utime` 刷 mtime ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`tools/check-ledger-selfcheck.py:2483,2614,2657`（`os.utime(..., None)`，注释标 `PIT-GLOBAL-037`） ⇒ **证据强度：脚本行号**。

**现象（本轮真实踩中）**：
变异验证（改坏代码 → 跑守门 → 还原）跑完之后，接着跑**同模块**的守门，
⇒ `GoStyleSheddingStatTest.droppedRequestCountsDrop` **红了**，报 `drop` 计数是 0；
⇒ 但代码是对的（`stat.incrementDrop()` 明明在 `GoStyleRpcSheddingInterceptor` 里）。

**排查过程**（值得记住，因为它把好几个人带偏）：
① 读生产代码 ⇒ 逻辑没问题；
② 单独跑该用例 ⇒ **绿**；
③ 跑整个测试类 ⇒ **绿**；
④ `mvn clean test` ⇒ **绿**（168 例全过）；
⇒ ⇒ **只有「增量构建 + 跑过变异」这个组合会红** ⇒ 指向**编译产物过期**。

**根因**：
变异验证脚本用 `shutil.copy2(bak, path)` 还原源码 ⇒ **`copy2` 会保留文件的 mtime**
⇒ 还原后的源码 mtime **比变异期间编译出来的 `.class` 还旧**
⇒ `maven-compiler-plugin` 的增量检查（按时间戳）判定「class 已是最新」⇒ **跳过重编译**
⇒ surefire 跑的**是变异过的 class**（少了 `stat.incrementDrop()` 那行）
⇒ 守门报「代码没生效」，而源码其实是对的。

⚠️ **危险等级比一般编译陷阱更高**：
- 往小处：守门**假红** ⇒ 让人以为自己的修复没生效 ⇒ 可能去改本来正确的代码；
- 往大处：如果变异恰好**让某条断言通过**，则守门会**假绿** ⇒ 变异验证的结论直接失效。

**修法（两处都要改）**：
1. **还原源码必须刷 mtime**：
   ```python
   shutil.copyfile(bak, path)     # ⚠️ 不要用 copy2
   os.utime(path, None)           # 还原后给当前 mtime
   ```
2. **变异验证之后、跑其它测试之前，先 clean 或至少 touch 一次被改过的文件**：
   ```bash
   mvn -o -B -pl <module> clean test     # 最省心
   ```

**通用教训**：
**「源码」与「跑起来的字节码」之间隔着一次编译，而那次编译是按时间戳决定要不要做的。**
⇒ 在做**任何**「改坏 / 还原 / 打补丁」式的验证后，
**必须显式确认编译产物是最新的**，否则后面所有的绿灯都不可信。
⇒ 与 §3.3「编译通过 ≠ 机制生效」同源：**编译这一步本身就有静默失效的路径**
（这次是「时间戳判定 ⇒ 不重编」）。

---

## PIT-MESSAGE-34 `pg_isready` **不是**「Postgres 可以用了」的判据 —— 它会在 initdb 的临时服务上**返回 0** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-026` ⇒ **新号**：`PIT-MESSAGE-34`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 就绪判据已换成「真能建库」的那条命令（`psql … select 1`），不再是 `pg_isready` ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`tools/difftest/run-message-ddl-parity.sh:92,97`（`docker exec "$CONTAINER" psql -U admin -d postgres -tAc 'select 1'`） ⇒ **证据强度：脚本行号**。

**场景**：本轮写「双侧活体 DDL 对拍」脚本，第一版用
`docker exec <容器> pg_isready -U admin` 当就绪判据。

**实测翻车**：
```
=== 1. 起 PG（msgddl-pg，端口 55450）===
  PG ready (2s)                      ← pg_isready 返回 0
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed:
       No such file or directory
```

**根因**：
`postgres` 官方镜像的 entrypoint 在 **initdb 阶段**会**临时起一个只监听 unix socket 的服务**
（`listen_addresses=''`）来跑初始化脚本 ⇒ **`pg_isready` 对它返回 0**
⇒ 脚本以为「PG 已就绪」就去 `CREATE DATABASE`
⇒ 而此时那个临时服务**已经被停掉、真服务还没起** ⇒ socket 消失。

⚠️ **危险等级**：
- 往小处：脚本**首跑必挂**，报错信息（`No such file or directory`）指向 socket，
  **完全不像「就绪判据用错了」** ⇒ 容易去查 socket 路径、查挂载、查权限 ⇒ 白查半天；
- 往大处：如果判据写成「重试几次就放弃并继续」，就会在**没有库**的状态下继续跑对拍
  ⇒ 而 `check-ddl-static.py` 的 `docker exec` 失败会抛 `CalledProcessError`
  ⇒ 被 catch 成 `FATAL` + `EXIT=2` ⇒ **这一条还算是安全的**；
  但凡下游有「读不到就当空」的分支，就会变成**假绿**。

**修法（判据必须是「真能做那件事」）**：
```bash
# ❌ 错：pg_isready 只证明「有个 postgres 在接受连接」
docker exec "$CONTAINER" pg_isready -U admin >/dev/null 2>&1

# ✅ 对：判据 = 「我接下来要做的那条命令真的能跑通」
docker exec "$CONTAINER" psql -U admin -d postgres -tAc 'select 1' >/dev/null 2>&1
```
⇒ 且**超时后要显式失败**（不能只是循环结束就往下走）：
```bash
docker exec "$CONTAINER" psql -U admin -d postgres -tAc 'select 1' >/dev/null 2>&1 \
  || { echo "FATAL: PG 60s 内没就绪" >&2; exit 2; }
```

**通用教训**：
**「依赖已就绪」不能用一个与「依赖可用」不同义的探针来判。**
`pg_isready` 判的是「有进程在监听」，而我们真正要的是「**能建库、能建表、能查**」。
⇒ 判据要**直接用**你接下来要执行的那条命令去试。
（与 §3.3「注解存在 ≠ 机制生效」同源：**存在 ≠ 可用**。）

---

## PIT-MESSAGE-35 bash 里 `$VAR` 后面紧跟**中文标点** ⇒ 变量名被吞进去 ⇒ `unbound variable` ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PITFALL-MSG-R-027` ⇒ **新号**：`PIT-MESSAGE-35`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 中文文案里的变量引用已全部改用 `${VAR}` 形式 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`tools/difftest/run-message-ddl-parity.sh:81`（`echo "=== 1. 起 PG（${CONTAINER}，端口 ${PG_PORT}）==="`） ⇒ **证据强度：脚本行号**。

**实测翻车**：
```bash
echo "=== 1. 起 PG（$CONTAINER，端口 $PG_PORT）==="
# tools/difftest/run-message-ddl-parity.sh: line 75: CONTAINER�: unbound variable
```

**根因**：
bash 判定变量名边界用的是 `legal_variable_char()`，
它在**当前 locale 下**对字符调 `isalnum()`（**按字节**）⇒ UTF-8 多字节字符的**首字节**
（`0xE4`–`0xEF` 等）在某些 locale 里被 `isalnum()` 判成**字母数字**
⇒ `$CONTAINER，` 被解析成变量名 `CONTAINER` + 首字节 ⇒ 该变量**从未定义** ⇒ `unbound variable`。

⚠️ **为什么危险（不只是「脚本挂掉」）**：
- 报错信息里那个变量名显示成一个**乱码替换符**（`CONTAINER�`）⇒ **看不出是哪个标点吞的**；
- 若脚本开了 `set -u`（本仓的脚本惯例）⇒ 直接退出；**没开**的话，
  `$CONTAINER` 会变成**空串** ⇒ 后续 `docker rm -f ""` 之类的命令会**静默做错事**
  （本例就会去删一个**名字为空**的容器 ⇒ docker 报错但被 `2>&1` 吞掉）。

**修法**：**中文文案里引用变量一律用 `${VAR}`**：
```bash
# ❌ 错
echo "=== 1. 起 PG（$CONTAINER，端口 $PG_PORT）==="
# ✅ 对
echo "=== 1. 起 PG（${CONTAINER}，端口 ${PG_PORT}）==="
```
⇒ 判据很简单：**变量后面紧跟的不是 ASCII 字符，就加花括号**。

**通用教训**：
**带中文文案的 shell 脚本里，`$` 后面的一切都必须显式闭合。**
这不是「bash 的中文 bug」，而是「**变量名边界依赖 locale**」这一条 Unix 传统的必然结果。
⇒ 同一类问题还有：`$PATH` 后面直接跟 `/` 是安全的，但 `$CMD参数` 会被当成变量 `CMD参数`。

---

## PIT-MESSAGE-36 「RAG 侧**少了**某个响应头 / 响应体」—— **静态读源码极容易判反** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PITFALL-MSG-R-028` ⇒ **新号**：`PIT-MESSAGE-36`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「『缺失类』断言的默认状态应该是 `UNKNOWN`、不是『没有』」这条**判据纪律**今天仍成立 ⇒ 仍生效；本轮凡写「某侧缺 X」的台账结论，仍须先问「X 是不是框架自动产生的」。
> **依据**：反例已闭环（message 域的 404 body / 405 `Allow` 头真容器实测两侧一致）· 真容器脚本 `tools/difftest/exp-http-headers-message.sh` ⇒ **证据强度：脚本在仓**。

**场景**：message 域 api 台账的第一轮审计里，有两行结论是这么写的：

| 台账行 | 首轮（静态读源码）结论 | 真容器实测（2026-10-24 第八轮） |
|---|---|---|
| 404 body | Go `"404 page not found\n"` vs **RAG 空 body** | ✅ **两侧都是 19 字节，完全一致** |
| 405 的 `Allow` 头 | Go 带 `Allow` vs **RAG 无 `Allow`** | ✅ **两侧都是 `Allow: POST`，完全一致** |

⇒ **两行都判反了。** 而且它们是**同一类**：都是「RAG 侧少了点东西」。

**根因**：
「RAG 没实现 X」和「RAG 的客户端收到不到 X」是**两件事**：
- RAG 的 404 body 由 **Spring Boot 的错误页机制**产生（不是业务代码写的）；
- RAG 的 `Allow` 头由 **Spring MVC 的 `DispatcherServlet`** 在 405 分支自动加（**任何 MVC 框架都有**，Go 侧对应的是 go-zero `rest/router/patrouter.go:80` 的 `w.Header().Set(allowHeader, allows)`）；
⇒ **只要 RAG 是 Spring MVC，这些头就是自动有的**，跟业务代码里有没有写**完全无关**。

⚠️ **危险等级比「判断错」更高**：
- 往小处：台账里挂一条**根本不存在**的待办 ⇒ 浪费一轮「修」的工作量，
  而且**修的时候会改本来正确的东西**（比如真去「补一个 `Allow` 头」）；
- 往大处：这类错误结论一旦被抄进**共享层**（如「RAG 少了 `Allow` ⇒ 给所有 api 补」）⇒ **引入新的不一致**。

**判据（怎么避免）**：
1. **凡结论形如「某侧**缺**某个 HTTP 头 / 某个默认响应体」** ⇒ 一律标 `UNKNOWN`，不许直接写进漂移表** ——
   除非有**真容器**证据（`curl -D` dump 两侧）。
2. **静态能判的只有「**多**了什么」**（RAG 多发了一个头）**——
   「多」是业务代码自己写的，静态看得到；「少」往往是**框架自动补的**，静态看不到。
3. **MockMvc 也测不到这一层** ⇒ 必须真容器（`tools/difftest/exp-http-headers-message.sh`）。

> ⚠️ **本仓的同款教训**：
> · §3.3「代码里写了 ≠ 客户端收到」—— 那是**多/少都可能**的方向；
> · 本条是它的**反向同款**：**客户端收到了 ≠ 代码里写了** ⇒
>   「RAG 没写 ⇒ RAG 没有」这个推理链**不成立**。
> · 而 §3.3.2「以『Go: …』措辞出现的断言是高危项」是**同一族**的问题：**把推断当事实写下来**。

**通用教训**：
**「缺失类」断言的默认状态应该是 `UNKNOWN`，不是「没有」。**
⇒ 判断「某侧缺 X」之前，先问一句：**X 是不是框架自动产生的？**
（本仓的 `Allow` / `404 body` / `405` 三条都栽在这一点上。）
