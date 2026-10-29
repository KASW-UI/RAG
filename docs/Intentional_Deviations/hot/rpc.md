# `hot-rpc` 有意偏离台账

> **范围**：`service/hot/hot-rpc`（Go `service/hot/rpc/` + `service/hot/heavykeeper/`）。
> **归属**：`align/hot` 工作树产出（`RAG.worktrees/hot`）；基线 = `do-my-own-gh` @ `8422116` 之后的分支点。
> **核对日期**：2026-10-24（**第十一轮回填**；第五轮 = UNR-1 闭合后复核；SD-HOT-RPC-001..004 为第三/四轮签字）。
> **差异本体**（未签字的缺陷）在 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md)。
> 本文件只登记**明知与 Go 不同、经签字批准**的偏离。
>
> ⚠️ **读法**：对拍看到这里登记的条目变红，**先确认是不是这一条**，不要当新回归去「修」。
>
> 🟥 **2026-10-24 第十一轮回填**：本档**自第十一轮起无新增条目**（第十一轮是取证轮，不是签字轮）
> ⇒ 汇总表**仍然完整**，无需补行。但**核对日期**此前停在第五轮 ⇒ 已更新。
> ⚠️ 表中 `SD-1 / SD-2 / SD-3` 是**第一轮的旧编号**（2026-10-22 首批签字），
> 与 `SD-HOT-RPC-001..004` 是**两批不同的条目**，**不是同一批的两种写法** ⇒ 编号不连续是**历史事实**，不是漏号。

| ID | 一句话 | 方向 | 状态 |
|---|---|---|---|
| **SD-HOT-RPC-001** | 消费路径的非预期异常**降级成 drop**，不崩进程（Go 会崩） | RAG 更稳 | ✅ 已签字（2026-10-23） |
| **SD-HOT-RPC-002** | Redis 命令**不做** go-redis 式的 3 次自动重试 | RAG 少一次自愈（⚠️ 第五轮复核：**双向**差异） | ✅ 已签字（2026-10-23）+ 第五轮复核批注 |
| **SD-HOT-RPC-003** | 容器并发度 `concurrency=1`（Go 是**每分区一个 goroutine**） | RAG 单实例下串行消费 | ✅ 已签字（2026-10-24） |
| **SD-HOT-RPC-004** | 注册中心注册失败 ⇒ **进程存活 + 健康检查报 NOT_SERVING**（Go 是 panic 退出） | RAG 更稳 + 探针可见 | ✅ 已签字（2026-10-24） |
| **SD-1**（第一轮） | 分区撤销路径的锁升级：Go 持写锁再取读锁 ⇒ **必然自死锁**；RAG 改为「锁内取引用 → 锁外落盘」 | RAG 能跑 | ✅ 已签字（2026-10-22） |
| **SD-2**（第一轮） | Redis ZSet member 非字符串时**跳过**（Go 是裸类型断言 ⇒ panic） | RAG 不崩 | ✅ 复核通过（2026-10-23，等级可下调为 IMPLEMENTATION DIFFERENCE） |
| **SD-3**（第一轮） | Redis 脚本报错被**误报成「lock lost」**（Go 的 `result, _ :=`） | 两边一样错 | ✅ 复核通过（2026-10-23，照抄 Go） |

> ⚠️ **与 hot-api 档的不对称（仍然存在，本轮未动）**：
> hot-api 的注册失败是「**启动失败**」（`SD-HOT-API-008`），hot-rpc 是「**存活 + NOT_SERVING**」（`SD-HOT-RPC-004`）
> ⇒ 两侧可用性口径不一致，改动需 `service/common` 的 `HealthIndicator` 能力 ⇒ **跨域**。

## 🆕 2026-10-29 逐条重核结论（台账对齐 phase · W-E lane）

> **判定口径**：`docs/Intentional_Deviations/_INDEX.md:34` ——「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
> **方法**：**回 Go 真源 + 回 RAG 源码两侧核**（真源 = `Sea-RideTheWind_golang`，冻结只读；go-zero 取 `v1.10.2`；go-redis 取 `v9.20.0`）。
> **撞号检查**：`SD-HOT-RPC-001..004` 与第一轮旧编号 `SD-1` / `SD-2` / `SD-3` **全域唯一**
> （`grep -rn "SD-1\b\|SD-2\b\|SD-3\b" docs/Intentional_Deviations/` ⇒ **只有本文件**命中，`_INDEX.md` 里只是把它们列进族名）
> ⇒ ⇒ **本域 7 条编号互不撞号、也不与其它域撞号** ⇒ **不需要** `SD-<编号>@<域>` 别名。

| ID | 结论 | 证据（核对日期 2026-10-29） |
|---|---|---|
| `SD-HOT-RPC-001` | ✅ **仍成立** | RAG 侧仍**不外抛**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:118-123` 仍是 `try { return consumeMessage(...); } catch (RuntimeException e) { return e; }` ⇒ 消费异常**降级成 drop**；Go 侧 `service/hot/rpc/internal/mqs/hot_handler.go:105-107` + `service/common/observability/mq.go:30`（**裸** `err := fn(consumeCtx)`，**无 recover**）⇒ panic 直接崩进程 ⇒ 差异未变 |
| `SD-HOT-RPC-002` | ✅ **仍成立** | Go 侧仍默认重试 3 次：go-redis `v9.20.0` `options.go` 的 `init()`（**消费点**）`if opt.MaxRetries == 0 { opt.MaxRetries = 3 }`；RAG 侧 Lettuce 仍**无命令级重试入口**（Spring Data Redis 4.0.7 的 `connection/lettuce` 包内 `RetryPolicy` 引用数 = 0；Lettuce 6.8.2 jar 内**无** `io/lettuce/core/retry` 包）⇒ 差异未变<br>📌 **2026-10-29 真实性复核 · 补成可复算（不改写上面那句）**：Go 侧默认值的**消费点**在 `~/go/pkg/mod/github.com/redis/go-redis/v9@v9.20.0/options.go:393-397`（**真源 pin**：`Sea-RideTheWind_golang/go.mod:15` `github.com/redis/go-redis/v9 v9.20.0`）—— 实际形态是 **`switch`**（不是上面写的 `if`）：`case -1: opt.MaxRetries = 0` / `case 0: opt.MaxRetries = 3` ⇒ ⇒ **「未设置 ⇒ 默认 3」的语义逐字成立** ⇒ **差异未变、签字仍成立**；⚠️ 只是**上面那句的代码形态写法不准（`if` vs `switch`）** ⇒ 行号已补为 `:393-397`（**语义正确、形态表述需按此读**）。**核对日期 2026-10-29** |
| `SD-HOT-RPC-003` | ✅ **仍成立** | RAG 侧 `@KafkaListener` 仍**无** `concurrency` 属性 ⇒ 取默认 **1**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:98-100`（`@KafkaListener(topics=…, groupId=…, containerFactory=…)`，**无** `concurrency=`）；Go 侧仍是 sarama **每分区一个 goroutine** ⇒ 单实例多分区时两侧不等价、3 副本部署时等价 ⇒ 差异未变 |
| `SD-HOT-RPC-004` | ✅ **仍成立** | RAG 侧仍**存活 + NOT_SERVING**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/bootstrap/NacosRegistration.java:125-126`（注册失败 ⇒ `LOG.error` + `markHealth(ServingStatus.NOT_SERVING)`）+ `:165` `markHealth(...)`；Go 侧仍是 `zrpc/server.go:88-93` 的 `panic(err)` ⇒ 进程退出 ⇒ 差异未变 |
| `SD-1` | ✅ **仍成立** | RAG 侧仍是「**锁内取引用 → 锁外 Redis I/O**」：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotPartitionSketchRegistry.java:136-149` `detachWithinWriteLock(...)`（写锁内建引用、锁外落盘）+ `:155-169` 独立的读锁方法 ⇒ 没有在持写锁时再取读锁 ⇒ Go 的**必然自死锁**未被搬过来 ⇒ 差异未变 |
| `SD-2` | ✅ **仍成立** | RAG 侧仍**跳过**非字符串 member：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotRedisSnapshotStore.java:219-221`（`if (!(member instanceof String articleId)) { … continue; }`）；Go 侧仍是**裸类型断言** `scoreMap[z.Member.(string)]` ⇒ panic ⇒ 差异未变 |
| `SD-3` | ✅ **仍成立** | Go 侧仍**误报成「lock lost」**：`service/hot/rpc/internal/mqs/hot_handler.go:311-315`（`result, _ := renewScript.Run(...).Int()` ⇒ **错误被丢弃** ⇒ result=0 ⇒ 走 `logx.Errorf("[Lock] %s lost lock")` 分支）；RAG 侧**照抄**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotMergeScheduler.java:184`（「这是 Go 既有缺陷，`hot_handler.go:311` 原样如此」）+ `:210` `LOG.error("[Lock] {} lost lock", instanceId)` ⇒ **两边一样错** ⇒ 差异未变 |

> ⚠️ **计数影响**：本域 **7 条全部仍成立** ⇒ **不计入「已失效」** ⇒ **主线更新 `docs/Intentional_Deviations/_INDEX.md` 时本域无需减项**（本 lane 不写共享索引）。

---

## SD-HOT-RPC-001 · 消费异常降级成 drop（Go 会崩进程） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：`HotEventConsumer.onMessage` → `consumeMessage`
- **Go 出处**：
  - `service/hot/rpc/internal/mqs/hot_handler.go:105-107` — `observability.TraceConsumer(sess.Context(), ..., func(ctx) error { return h.consumeMessage(...) })`
  - `service/common/observability/mq.go:30` — `TraceConsumer` 是**裸的** `err := fn(consumeCtx)`，**没有 recover**
  - sarama 的 claim 消费 goroutine **也没有 recover** ⇒ panic 直接崩进程
- **RAG 形态**：`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/mq/HotEventConsumer.java:116-124`
  ```java
  try { return consumeMessage(partitionId, value); }
  catch (RuntimeException e) { return e; }   // 降级成返回值
  ```
- **差异**：同一段逻辑崩不崩。
- **可观察影响**：sketch 层异常下，**Go 会重启并重投，RAG 会静默丢事件**；
  与「Kafka 位点提交节奏」差异叠加 ⇒ 同一场景下两侧热榜**必然不同**。
- **签字理由**：崩进程不是想要的行为；且 RAG 的 drop 契约（坏消息永久丢弃、位点照常推进）
  是**整个消费面 L1 语义的基础**（`hot_handler.go:108` 的无条件 `MarkMessage`），
  把「异常处理」改成「崩溃」会让位点停在原地 ⇒ 变成无限重投，**比丢事件更糟**。
- **⚠️ 必须同时知道的代价**：这条偏离正是 **DRIFT-HOTRPC-001（桶位算错）长期无症状**的放大器 ——
  消费者吞掉一切 ⇒ 算错只表现为「热榜内容与真源不同」，没有任何错误信号。
  ⇒ 修算法时必须**同时**保留一条独立的、能暴露异常的测试
  （本轮用 `HeavyKeeperGoParityTest` 的对拍 fixture 承担这件事）。
- **留痕位置**：`HotEventConsumer` 类注释「🚨 本类的第一语义：永不外抛」小节 + 本条。

## SD-HOT-RPC-002 · Redis 命令不做 3 次自动重试 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：全部 Redis 命令（`HotRedisSnapshotStore` / `HotMergeScheduler` / `HotRankingQueryService`）
- **Go 出处**：
  - `service/hot/rpc/internal/svc/service_context.go:17-20` — `redis.NewClient(&redis.Options{Addr, Password})`，**只给两项**
  - go-redis v9.20.0 `options.go` 的 `init()` — `if opt.MaxRetries == 0 { opt.MaxRetries = 3 }`（**消费点**）
  ⇒ 网络错误 / 超时会**自动重试最多 3 次**，**不报错、不进 `fail` 分支**。
- **RAG 形态**：Lettuce **0 次**重试（Spring Data Redis 4.0.7 + Lettuce 6.8.2）。
- **⚠️ 为什么「不可复刻」（本轮取证）**：
  1. Spring Data Redis **4.0.7** 的 `connection/lettuce` 包内 **`RetryPolicy` 引用数 = 0**
     ⇒ **Spring 侧没有暴露 Lettuce 命令级重试策略的配置入口**；
  2. Lettuce **6.8.2** 的 jar 内**没有 `io/lettuce/core/retry` 包**
     （顶层包只有 `annotations/api/cluster/codec/.../protocol/resource/search/sentinel/support`）
     ⇒ 命令级重试策略**不是 Lettuce 6.8 的 API 面**。
  ⇒ 要对齐只能**在应用层自己写重试** ⇒ 那是把重试放到**错误的层**：
  会改变 drop 语义、指标口径（`redis_sync_total{status}` 何时 +1）与失败时机。
- **签字理由**：与其造一条「重试放在错误层」的伪等价，不如把差异**显式化**并登记。
- **可观察影响**：
  1. `article_hot_redis_sync_total{status}` 的值**不可跨实现直接比较**；
  2. 弱网下 RAG 的热榜刷新率**低于** Go；
  3. 查询面：Go 重试 3 次仍失败才返回 `UNKNOWN`，RAG 第一次失败就返回 `UNKNOWN`
     ⇒ **客户端看到的失败时机不同**。
- ⚠️ **第五轮复核批注（2026-10-24 · UNR-1 闭合）**：**签字口径不变**（Lettuce 无重试入口仍是事实 ⇒ 仍然不复刻），
  但 **DRIFT-HOTRPC-004 的严重度 MEDIUM → LOW**，且**新增第 4 条可观察影响**：
  4. ⚠️ **Go 会重发「非幂等」的 rename 脚本** ⇒ 若首次已在服务端执行成功、但回复在回程丢失，
     重试再执行时 `EXISTS shadow == 0` ⇒ 脚本返回 `0`
     ⇒ Go `hot_handler.go:236-238` **记 `fail`** ⇒ **实际 RENAME 已成功、指标却记 fail**（指标误计）。
     **RAG 不产生该形态**（Lettuce 无重试 ⇒ 脚本只执行一次）。
  ⇒ 也就是说这条偏离是**双向**的：**Go 更稳**（网络错自动重试）+ **Go 更不稳**（非幂等脚本被重发）。
  ⚠️ 证据强度：**真 Redis 实测 + go-redis v9.20.0 依赖源码逐行**（**不是**双侧活体对拍），
  详见 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md) 第五轮章节。
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 DRIFT-HOTRPC-004 条目。

---

## SD-HOT-RPC-003 · 容器并发度 `concurrency=1`（Go 是每分区一个 goroutine） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：Kafka 消费并发度（`@KafkaListener` 的 `concurrency`）
- **Go 出处**：
  - `service/hot/rpc/internal/mqs/hot_handler.go:101-111` —— `ConsumeClaim` 是 **claim 级**的循环；
  - sarama 的 `consumerGroupSession.consume()` **每个 partition claim 起一个 goroutine**
    ⇒ 单进程持有 N 个分区时，**N 路并行消费**。
- **RAG 形态**：`@KafkaListener` 默认 `concurrency=1` ⇒ **单消费者线程串行轮询**它持有的全部分区。
  （真 Kafka 实测：生产容器独占 3 个分区 ⇒ `HotPartitionSketchRegistry.size() == 3`，事件处理串行。）
- **差异何时可见**（**这是本条的关键，不能简单写成「不等价」**）：
  | 部署形态 | Go | RAG | 是否等价 |
  |---|---|---|---|
  | **单实例**（1 JVM 持 3 个分区） | 3 路并行 | 1 路串行 | ❌ **不等价** |
  | **实际部署「同一部署 × 3 副本」**（3 JVM 同组、RoundRobin ⇒ 每 JVM 恰好 1 个分区） | 每 JVM 1 路 | 每 JVM 1 路 | ✅ **等价** |
- **同分区内顺序保证**：两侧都是分区有序 ⇒ **一致**（这条不受影响）。
- **签字理由**：用户 2026-10-24 拍板「保持 + 签字」。
  理由：① 实际部署形态下等价，改 `concurrency` **改不到实际行为**；
  ② `concurrency` 是**静态值**，而分区数会随部署变化 ⇒ 绑死 `partition-count` 反而引入新的耦合；
  ③ 跨分区并行度只影响吞吐，不改热榜内容语义。
- **⚠️ 残留风险**：若将来出现**单实例部署**（或副本数 < 分区数），并行度差异会显现
  ⇒ 届时应重新评估，而不是默认本条永远成立。
- **可观察影响**：低（吞吐 / 跨分区并行度）。
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 DRIFT-HOTRPC-009 条目。

---

## SD-HOT-RPC-004 · 注册失败 ⇒ 进程存活 + 健康检查 NOT_SERVING（Go 是 panic 退出） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：服务注册（etcd / Nacos）
- **Go 出处**（逐条读到**消费点**）：
  1. `service/hot/rpc/hot.go:74` —— `serviceGroup.Start()`；
  2. `zrpc/server.go:88-93` —— `Start()` 里 `panic(err)` ⇒ **进程退出**；
  3. `zrpc/internal/rpcpubserver.go:48-53` —— `keepAliveServer.Start` **先**跑 `registerEtcd()`，失败即 return error；
  4. `core/discov/publisher.go` —— `KeepAlive()` → `doRegister()` → `GetConn(endpoints)`，etcd 连不上即 error。
  ⚠️ **边界**：`MustNewServer` 构造期**不连** etcd（只有 `Start()` 才注册）；
  ⚠️ **运行期**掉线走 `doKeepAlive()` ⇒ `logc.Errorf` + 重试，**不退出** ⇒ 只有「首次注册失败」才 panic。
- **RAG 形态**：`NacosRegistration.register()` 捕获 `NacosException | RuntimeException` ⇒
  **只打 ERROR、进程继续存活**。
- **差异**：
  | | Go | RAG |
  |---|---|---|
  | 首次注册失败 | **进程退出**（探针**连不上**） | **进程存活**（探针**连得上**） |
  | 探针结果 | 失败 | **必须**失败 —— 由 gRPC health 状态表达 |
- **签字理由**：用户 2026-10-24 拍板「保持 + 签字 + 补『注册失败 ⇒ 健康检查 NOT_READY』」。
  理由：**崩进程不是想要的语义**（注册中心抖动不该让整个副本反复重启），
  但「进程活着却注册不上」若**探针还是绿的**，就成了**假活** ——
  进程在、端口在、服务发现里没有它 ⇒ 上游按端口判断健康就会误判为可用。
  ⇒ 两条合起来才是完整语义：**进程不死，但探针必须看得见失败**。
- **✅ 配套实现（本轮同批落地）**：
  - `NacosRegistration.markHealth(...)` ⇒ 注册失败时
    `HealthStatusManager.setStatus(SERVICE_NAME_ALL_SERVICES, NOT_SERVING)`；
  - 走 **grpc 标准 health service**，与 Go 侧**同构**：
    Go = `google.golang.org/grpc/health.Server`（`zrpc/internal/server.go:36-38` +
    `zrpc/internal/rpcserver.go:61-63` 的 `RegisterHealthServer` + `Resume`），
    RAG = `io.grpc.protobuf.services.HealthStatusManager`（devh starter 的
    `GrpcHealthServiceAutoConfiguration`，条件 `grpc.server.health-service-enabled` 默认 true）；
  - **两侧都只登记整体状态**（service 名 = `""`）⇒ 查具体服务名一律 `NOT_FOUND`，
    **刻意不**多登记 `"hot-rpc"`，以免造出 Go 侧没有的行为。
- **守门测试**：`NacosRegistrationHealthGateTest`（2 例）——
  ① 注册失败 ⇒ 用 `HealthGrpc` 走**真实 gRPC health 协议**查整体状态 ⇒ `NOT_SERVING`；
  ② 查具体服务名 ⇒ `NOT_FOUND`（证明没有多登记条目）。
  **变异验证**：抽掉 `markHealth(NOT_SERVING)` ⇒ 用例变红（`expected: <NOT_SERVING> but was: <SERVING>`）。
- **可观察影响**：**低且是修复方向**（把「假活」变成「探针可见的失败」）——
  唯一残留差异是失败时 gRPC 端口仍可连、方法仍可调（Go 是直接断连）。
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 DRIFT-HOTRPC-010 条目 + `NacosRegistration` 的 javadoc。

---

## SD-1 ✅ 已签字（第一轮，2026-10-22）· 分区撤销路径的**锁升级**：Go 必然自死锁，RAG 改「锁内取引用 → 锁外落盘」 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> 🟥 **2026-10-24 第十一轮回填**：本条原先**只有汇总表一行、正文缺章节** ⇒ 已补齐。
> ⚠️ **编号说明**：`SD-1 / SD-2 / SD-3` 是**第一轮的旧编号**，与 `SD-HOT-RPC-001..004` 是**两批不同条目**，不是同一批的两种写法。

- **Component**：`HotPartitionSketchRegistry` 的分区撤销（revoke）路径
- **Go 出处**：`hot_handler.go:132` `hkMu.Lock()` → `:136` `syncToRedis` → `:196` `hkMu.RLock()`
  ⇒ Go 的 `sync.RWMutex` **不可重入** ⇒ **第一次 rebalance 撤销分区即必然自死锁**，
  `defer Unlock` 永不执行，`delete` 从不执行 ⇒ **分片快照从未落盘过**
- **RAG 形态**：改用 Go 另外两个调用点已有的形态——**锁内取引用 → 锁外 Redis I/O**
- **签字理由**：**照抄会复现 Go 的死锁**；且 Java 的 `ReentrantReadWriteLock` **允许**写锁持有者再取读锁
  ⇒ 直译会把 Go 的死锁**悄悄变成「能正常跑」** ⇒ 两套实现行为不一致且**零告警**
  ⇒ **这类差异不能靠 review 结构发现，只能靠「先复现 Go 的行为」再决定怎么写**
- **可观察影响**：Go 侧该路径**实际不可达**（必然死锁）⇒ 差异无客户端可见后果，但**必须留痕**
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 **G-HOT-001** + `FileTree/hot/rpc.md §12.5`

---

## SD-2 ✅ 复核通过（2026-10-23）· Redis ZSet member 非字符串时**跳过**（Go 是裸类型断言 ⇒ panic） ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：`HotRedisSnapshotStore` 的 ZSet member 解析
- **Go 出处**：`hot_handler.go:370` `scoreMap[z.Member.(string)] += …` ——
  **裸类型断言**，member 非字符串直接 **panic**；且该代码在 `periodicMerge` goroutine 内，
  **不在 sarama 的 recover 范围内 ⇒ 崩进程**
  （⚠️ **同文件 `:275` 与 `get_hot_articles_logic.go:55` 都有 `, ok` 保护** ⇒ **Go 自身三处不一致**）
- **RAG 形态**：改为**跳过**该 member
- **复核结论**：2026-10-23 复核**通过**，且等级可下调为 `IMPLEMENTATION DIFFERENCE`
  （Go 的裸断言在**其自身另外两个调用点**都做了保护 ⇒ 属 Go 侧的历史不一致，不是契约）
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 **G-HOT-002**

---

## SD-3 ✅ 复核通过（2026-10-23，照抄 Go）· Redis 脚本报错被**误报成「lock lost」** ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

- **Component**：`HotRedisSnapshotStore` 的 Lua 脚本执行结果判定
- **Go 出处**：`hot_handler.go:311` / `:408` `result, _ := …Int()` ——
  Redis **脚本执行失败**也被报成「lock lost」⇒ **排障指向错误方向**
- **RAG 形态**：**照抄**（AGENTS.md §15：不擅自修复 Go 行为）
- **复核结论**：2026-10-23 复核**通过**；「两边一样错」是**刻意保留**的行为，
  已在代码注释与测试里留痕 ⇒ **不得**被当新漂移「顺手修回」
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 **G-HOT-003**
