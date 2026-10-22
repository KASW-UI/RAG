# 未决项（已查代码但无法定论）

> 纪律：拿不准就写 `UNKNOWN`，**不猜**。
> 每条含：问题 / 已查到哪一步 / 卡在哪 / 需要什么才能定论 / 若不处理的风险。

---

## U-1 🔴 双侧活体对拍不可得

| 栏 | 内容 |
|---|---|
| **问题** | 本文所有结论均来自**静态取证 + 框架/驱动层字节码取证**，没有任何一条由双侧活体探针确认。 |
| **已查到** | Go 真源启动依赖 `etcd:2379`（`etc/article.yaml` `Etcd.Hosts: [etcd:2379]`），本机无法拉起 ⇒ Go 侧服务不可运行。 |
| **卡在哪** | 无法同时观察两侧在同一输入下的 status code / description / 消息顺序 / DB 落库结果。 |
| **需要** | 一个可用的 etcd（或用 `Endpoints` 直连绕过发现），或双侧各自的 record/replay 测试夹具。 |
| **影响哪些结论** | 主要是**错误的严重级**，不是错误的存在性：S-1（已追到 pgjdbc 字节码，机制链完整）、S-3（已追到 `kq/config.go:24` + `queue.go:235`）、O-6（已追到 `zrpc/config.go:31`）三条的存在性证据充分，但「实际触发时的表现」仍缺运行时确认。 |
| **风险** | 中。若某个框架在特定版本下改变了默认（如 pgjdbc 未来把 `readOnlyMode` 语义改掉），S-1 的严重级会变，但 `@Transactional(readOnly=true)` 内含写操作这件事本身仍是错的。 |

---

## U-2 锁顺序反转 / 死锁风险（未证实）

| 栏 | 内容 |
|---|---|
| **问题** | `UpdateArticle` 分支 A 与 `DeleteArticle` 的**行锁获取顺序相反**。 |
| **Go** | 分支 A：`RunInTx` 闭包内先 `RetractPublicationTx`（**publication 行 `FOR UPDATE`**，`community_article_event.go:113-114`）→ `UpdateTx`（`UPDATE article` 取行锁）→ `CreateTx`。<br>`DeleteArticle`：`RunInTx` 闭包内先 `RetractPublicationTx`（同一把 publication 锁）→ `CreateTx` → `DeleteTx`。<br>⇒ **两条路径都先拿 publication 锁** ⇒ 天然串行化在同一把锁上。 |
| **Java** | 分支 A：`ArticleAtomicService.retractAndUpdate`（`:112-125`）**不重取 article** ⇒ publication → article。<br>`DeleteArticle`：`ArticleAtomicService.retractAndDelete`（`:138-160`）**先 `selectByIdForUpdate(articleId)`**（`:142`）⇒ article → publication。<br>⇒ **顺序相反**。 |
| **卡在哪** | 需要一个**真 PG + 两连接**的并发用例才能确认是否真的触发 PG 的 `40P01 deadlock_detected`。**静态推演不足以断言「必然死锁」**：PG 的死锁检测器会在其中一条语句上中止其中一方，客户端看到的可能是业务错误而不是挂死。 |
| **需要** | CASE-021（见 `test-cases.md`）。 |
| **风险** | 中。在「同一篇文章被同时降级与删除」这一窄窗口内，两侧可能表现不同（Go 串行、Java 可能有一方报 `40P01`）。`40P01` 会让对应 RPC 返回 `UNKNOWN`（1015）而不是成功。 |
| **注意** | RAG 的注释（`ArticleAtomicService.java:140-141`）把事务内重取解释为「保证行锁与后续写入在同一快照」，但**没有论证锁顺序**。 |

---

## U-3 `@TableLogic` 缺席导致 MP `updateById` 不带软删条件

| 栏 | 内容 |
|---|---|
| **问题** | Go 的 GORM 对 `Save`/`Updates` 会自动追加 `deleted_at IS NULL`；MyBatis-Plus 的 `updateById` **不会**（实体上刻意没有 `@TableLogic`，`ArticleEntity.java:145-149`）。 |
| **已查到** | 所有 `articleMapper.updateById(...)` 调用点：<br>· `ArticleDomainService.java:333,345`（update 分支 B/C）—— 前面刚 `selectById` 过非删行<br>· `ArticleAtomicService.java:119,281,476,496` —— 前面刚 `selectByIdForUpdate` 取到行<br>· `ArticleReviewConsumer.java:271`（拒稿）—— 前面刚 `selectById` 过 |
| **卡在哪** | **当前无任何可观察差异**（调用点都先确认了行未删）。但这是一条**隐式不变量**（"每个 `updateById` 前必须已经确认行未删"），没有被任何机制强制。将来新增一个调用点时不会有任何信号。 |
| **需要** | 要么显式接受并登记为 signed deviation，要么在 `updateById` 上加显式 `deleted_at IS NULL`（会改变 MP 生成的方法），要么补一条「所有 updateById 调用点必须前置非删校验」的守门断言。 |
| **风险** | 低（当前），但属于「靠注释维持的约束」。 |

---

## U-4 `spring.jackson.time-zone` 不生效

| 栏 | 内容 |
|---|---|
| **问题** | `application.yml:231-238` 用一整段注释宣称「显式对齐 Go DSN 的 `TimeZone=Asia/Shanghai`」，但 `config/Jackson2ObjectMapperConfiguration.java:55-60` 自定义了 `ObjectMapper`（`JsonMapper.builder().disable(FAIL_ON_UNKNOWN_PROPERTIES).build()`）。Boot 的 `JacksonAutoConfiguration` 对 `ObjectMapper` 是 `@ConditionalOnMissingBean` ⇒ **不会**再套用 `Jackson2ObjectMapperBuilder`，`spring.jackson.*` 全部**不生效**。 |
| **卡在哪** | 有两层问题：<br>① **该键确实不生效**（这一点可以确定）。<br>② **它是否需要生效** —— `spring.jackson.time-zone` 本来也**不是** JVM 时区、也不是 JDBC 会话时区。即使生效，注释宣称的「对齐 Go DSN 时区」也达不到。 |
| **对可观察行为的影响（已初步分析，未双侧确认）** | 文章域的时间列都是 `timestamptz`（`V8__create_article.sql:36-38`）；写入用 `OffsetDateTime.now(ZoneOffset.UTC)`，读取后一律经 `.toInstant().toEpochMilli()` 归一（`ArticleDomainService.java:244-245`、`ArticlePublicDomainService.java:252-253,271-272`）⇒ **Instant 层面与 JVM 时区无关**。域事件时间戳由手写 `RFC3339_NANO` 格式化（`ArticleAtomicService.java:68-69`），不经 Jackson。 |
| **需要** | 双侧各插入一条记录，回读 `create_time` / `published_at` 并比对毫秒值（Go 与 Java 各跑一次，JVM 分别置于 UTC 与 Asia/Shanghai）。 |
| **风险** | 低。但**配置意图与实际不符**这件事本身应登记：要么删掉这个键并改注释，要么改成真正生效的写法（`-Duser.timezone` 或 JDBC URL 参数）。这与 AGENTS.md 已登记的 DRIFT-MSG-V-007 是同族问题。 |

---

## 附：本次明确**排除**的怀疑项（查过，确认不是问题）

记录在此以免后续重复怀疑。

| 怀疑 | 结论 | 证据 |
|---|---|---|
| `@Select("<script>")` 里裸 `<>` 会让 XML 解析失败 | **已规避** | `ArticlePublicMapper.java:85` 提供 `PUBLIC_ARTICLE_PREDICATE_CDATA`，`:101-124,144-184` 的 script 版本全部走 CDATA |
| `@TableName(autoResultMap=true)` 被 `@Select` 覆盖后 typeHandler 静默失效 | **已规避** | 结果映射声明在 `ArticleMapper.java:43-48` 的 `selectById` 上，其余自定义查询全部 `@ResultMap("articleEntityResultMap")`（`:81,185-186`）；`ArticlePublicMapper` 全部引用全限定 id |
| `DefaultErrorHandler` 默认 9 次零延迟重试 | **已规避** | `ArticleRpcApplication.java:171` `new FixedBackOff(0L, 0L)` |
| `seekAfterError=true` 会在 recover 后无条件 commit `offset+1` | **已规避**（但代价是引入了 S-3） | `ArticleRpcApplication.java:173` `setSeekAfterError(false)` |
| `@Transactional` self-invocation 静默失效 | **已规避** | 多步事务全在独立 Bean `ArticleAtomicService` 上（`:112,138,244,316`）；内部 `publishApprovedRevision` / `markSyncFailed` 刻意 package-private 且不加 `@Transactional`（`:425-430,485-487`） |
| Kafka listener 的 SpEL `#{@beanName}` 早于 `@ConfigurationPropertiesScan` 求值 | **已规避** | 两个 listener 都用 `${}` 占位符（`ArticleReviewConsumer.java:107-108`、`ArticleSyncResultConsumer.java:79-80`） |
| `ArticleAtomicService` 不是 AOP 代理 ⇒ `@Transactional` 静默失效 | **已断言** | `ArticleSpringContextWiringTest.java:178-185`（`AopUtils.isAopProxy`） |
| Boot 4 拆模块后缺 `spring-boot-kafka` / `spring-boot-flyway` | **已补依赖** | pom `:125`（`spring-boot-kafka`）、`:165`（`spring-boot-flyway`）；`ArticleSpringContextWiringTest.java:116-145` 验表确实存在 |
| Nacos Bean 名撞车 | **已用显式名** | `NacosRegistration.java:36` `@Component("articleRpcNacosRegistration")`；`ArticleSpringContextWiringTest.java:272-280` |
| MinIO Java SDK 不校验对象存在性导致失败时点不同 | **已补 HEAD 探测** | `ArticleObjectStorageClient.java:226-236` |
| content security 兜底实现 fail-open | **否，fail-closed** | `UnavailableContentSecurityClient.java:62-75` 两个方法无条件抛；`ArticleSpringContextWiringTest.java:261-265` 断言抛异常 |