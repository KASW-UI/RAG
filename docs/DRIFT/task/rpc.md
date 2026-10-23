<!-- @new 2026-10-23 · task 域组件级对照（rpc 先于 api）· 由 `align/task` 工作树产出 -->

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
| 语义等价性 | 🔴 **SEMANTICALLY NOT EQUIVALENT**（首轮即如此，**不是**「基本一致」） |
| 已修 | **3 条**（DRIFT-TASK-RPC-001 / 002 / 003），每条配守门测试 |
| 未修 | 0 条语义漂移；另有 1 条签字偏离（见下）+ 1 条有意偏离待登记 |
| 证据强度 | ✅ **真启动 + 真 PostgreSQL 16 容器**（本域最高档）· 🔴 **无双侧活体对拍**（Go 侧未起，理由见 §五） |
| 测试 | **81 tests / 0 failures / 0 errors / 0 skipped**（其中 15 条真 PG） |

---

## 一、已修（3 条）

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
- ⚠️ `goka` 的 `OffsetOldest` 只作用于**首次**消费无位点的 group；本条结论限于「配置意图一致」，**未做活体验证**。

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

1. 🔴 **无双侧活体对拍**。Go 侧 `task-rpc` 未在本机起过（依赖 etcd + Kafka + Redis + 真 PG 的完整编排）。
   本台账的 ✅ 含义是「**逐行源码对照 + RAG 侧运行时验证**」，不是「两侧跑起来行为相同」。
2. ⚠️ **Kafka / Redis 侧为「装配已验证、行为未活体验证」**。测试里 Kafka 指向 `127.0.0.1:1`、
   Redis 指向 `127.0.0.1:1`，验证的是**容器能装配起来**（这正是 §3.3 要求的），
   **不是**「一条消息真的从 Kafka 流到 Redis」。
   ⇒ 本轮新引入的 Redis `INCR` 路径（DRIFT-TASK-RPC-001）**尚未经真 Redis 验证**，
   守门测试用的是「表现得像 Redis」的内存替身。**这是本轮引入的最大新风险，见 §六。**
3. ⚠️ **DDL 的索引 / 唯一约束 / NOT NULL / DEFAULT 与 GORM `AutoMigrate` 的产出未做逐项对拍**
   （`tools/difftest/check-ddl-static.py` 未覆盖 task 域；且 `AutoMigrate` 只在空表上跑过一次）。
   本台账的 DDL 结论基于**逐条读 GORM 的类型映射规则 + 真 PG 建表结果**，不是与 Go 侧 `AutoMigrate` 的产物对比。
4. ⚠️ `service/common`（被 task-rpc 依赖）的结论来自它自己的台账，本轮**未重审**。

---

## 六、本轮引入的新风险（明写，不藏）

| 风险 | 说明 | 处置建议 |
|---|---|---|
| Redis 变成计数的硬依赖 | 此前计数是纯内存，Redis 挂了只影响进度缓存（会降级回源）；**现在 Redis 挂了会阻塞 goka 计数组**（位点不提交、消息堆积） | 已在类注释写明取舍。若运维不接受，把 `TaskCountStore` 换成本地文件 store（更贴 goka 的 LevelDB）即可，其余代码不用动 |
| Redis key 无 TTL、无上限 | goka 的表会随 Kafka retention 滚动，RAG 的 `task:goka:count:*` **只增不删** | 长期需加保留策略（如按 `like:total` 的 90 天口径定期清理）。本轮**未实现**，登记为待办 |
| 新增了一条 gRPC→Redis 的同步依赖 | 每条 raw 日志一次 `INCR` RTT | 与 Go 的「每条消息一次本地 DB 写 + 定期 Kafka 表 emit」量级相当，判断为可接受 |

---

## 七、待办

1. **task-rpc 的 Redis 活体验证**：起一个真 Redis 容器，把 `TaskUserCountConsumer` → `filtered.User` →
   `UserLikeProgressSink` → `like:total` + `task_progress` 串成一条真链路跑通（本轮只做到装配层）。
2. `task:goka:count:*` 的保留策略（见 §六）。
3. `tools/difftest/check-ddl-static.py` 扩到 task 域（`AutoMigrate` vs Flyway 的列级对照）。
4. `tools/difftest/check-proto-static.sh` 扩到 `task`（照 `user`/`admin` 的写法加 `verdict`）——
   否则「proto 一致」只是一次性快照。
5. Go 侧起 `task-rpc` + `task-api` 做双侧活体对拍（本域可达的最高证据档）。

---

## 八、相关文件

| 关系 | 文件 |
|---|---|
| 本域 api 台账 | [`api.md`](./api.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑（Ryuk 静默 skip / 死配置） | [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md) |
| 四域排序与本域定位 | [`../_cross/component-coverage-matrix.md`](../_cross/component-coverage-matrix.md) |