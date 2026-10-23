# `service/message/message-rpc` 踩坑与元教训

> 配套台账：[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md)（差异本体） +
> [`docs/DRIFT/_cross/message-domain-wide.md`](../_cross/message-domain-wide.md)（§6.7 第二轮）。
> **本文件只写教训**，不重复差异清单。
> 编号命名空间 `PITFALL-MSG-R-*`；核对日期 **2026-10-23**。

---

## PITFALL-MSG-R-001 「测试全绿」与「一次没起过」可以同时成立

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

## PITFALL-MSG-R-002 「断言 Bean 存在」≠「断言机制生效」——本模块的完整样本

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

## PITFALL-MSG-R-003 MyBatis 的 `#{...}` 与 MP 的 `Entity::method` 用**两套**属性名规则

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

## PITFALL-MSG-R-004 注释里的**默认行为**必须读到消费点，不能只贴 struct 定义行

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

## PITFALL-MSG-R-005 「关掉一个框架开关」不等于「关掉这个框架」

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

## PITFALL-MSG-R-006 Spring Boot 4 把自动配置拆模块后，**依赖在 ≠ 能力在**，且**不报错**

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

## PITFALL-MSG-R-007 自动配置的排序是一条**有向图**，不是「谁先扫到谁先生效」

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

## PITFALL-MSG-R-008 变异测试会先证明「你自己的新测试也是假绿」

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

## PITFALL-MSG-R-009 「分页接口的 `total` 与 `items` 必须成对断言」

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

## PITFALL-MSG-R-010 跨组件不变量**无法单侧验完**（Flyway 账本）

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

## PITFALL-MSG-R-011 附带的稳健性缺口：`TIMESTAMP` 不自描述瞬时

**现象**：真源列类型是 `timestamptz`，DSN 钉 `TimeZone=Asia/Shanghai` ⇒ `.Unix()` 与时区无关。
RAG 侧列是 `TIMESTAMP`（无时区），读侧改用 `atZone(ZoneId.systemDefault())` 后**自洽**了 ——
但自洽的前提是**「一个 DB 只由同一时区的 JVM 写」**。

以下三种情形会重新错位：迁移期新旧实例并存且时区不同 / 换时区后回读历史数据 /
同一张库被另一个时区的进程写入。根治需要一条 `V4` 迁移把列改成 `TIMESTAMPTZ`。
**本轮未做该迁移**（行为已正确、`ALTER TABLE` 有锁表风险、且 DDL 尚未被系统性核对）。

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
