<!-- @new 2026-10-23 · task 域签字偏离档 · 由 `align/task` 工作树产出 -->

# `task-rpc` 有意偏离（已签字）

> 本档记「**明知与 Go 不同、经签字批准**」的偏离。
> **它是决策不是缺陷**：审查时**不重复质疑**，但也**不得「顺手修正」回 Go 行为**（那会制造新漂移）。
> 需要对齐的差异在 [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md)。
>
> **签字日期**：2026-10-23　**基线**：`8422116`

---

## SD-2026-10-23-TASK-01 · PG 连接最大生存时间：Go 的 30 **纳秒** → RAG 的 30 **分钟**

**签字状态**：✅ 已签字（本轮登记）。**性质**：RAG 做了**更合理**的那一侧。

### 两侧取值

| | 值 | 出处 |
|---|---|---|
| Go | **30 纳秒** | `service/task/rpc/internal/svc/service_context.go:45`<br>`sqlDB.SetConnMaxLifetime(time.Duration(c.Postgres.ConnMaxLifetimeMinutes))` |
| Go yaml | `ConnMaxLifetimeMinutes: 30` | `service/task/rpc/etc/task.yaml:53` |
| RAG | **30 分钟**（`max-lifetime: 1800000` ms） | `service/task/task-rpc/src/main/resources/application.yml` |

### 为什么 Go 是 30 纳秒（**笔误，不是设计**）

`c.Postgres.ConnMaxLifetimeMinutes` 的类型是 `int`，值 30。
`time.Duration(30)` 是**把 30 当成纳秒数**的转换 —— 少乘了一个 `* time.Minute`。
字段名 `...Minutes` 已经把单位写在名字里，**缺失的乘法是 Go 代码侧的笔误**。

其运行效果：Go 的 `database/sql` 拿到 `maxLifetime = 30ns`，每次从池里拿连接都会判定它已过期 ⇒
**连接池永不命中**，每次查询都重新建连（在高 QPS 下就是每查询一次 TCP + TLS 握手）。

### 为什么 RAG 不照抄

1. **照抄在 Java 侧根本做不到**：HikariCP 硬性拒绝 `maxLifetime < 30s`
   （否则抛 `IllegalArgumentException: maxLifetime must be greater than 30000ms (30s)`）
   ⇒ 要「1:1 复刻」必须让组件**起不来**。
2. **即便能抄，30ns 的语义是「连接池永不命中」** —— 那是把一个配置笔误变成运行故障，
   属于**改变可观察行为**（连接建立速率、数据库连接数、延迟分布），而不是保留行为。

### 复核要点

- ✅ 与 Go **同类取舍见** `hot-rpc` 的 `SetConnMaxIdleTime` 等条（`docs/DRIFT/hot/rpc.md`）。
- ⚠️ **本条不是「RAG 更安全」而是「RAG 更可用」**：它让 RAG 在高并发下**优于** Go。
  若将来做性能对拍，**这一项预期就是 RAG 更好**，不要据此判定为回归。
- ⚠️ **不要**在别的域看到「Go 某个 `time.Duration(...)` 少乘单位」就照本条推广：
  那要先确认目标框架是否也拒绝该值，以及该值是否真的会导致「池永不命中」。

---

## 附：本域**不是**偏离、但容易被误当成偏离的三项

> 记在这里是为了**防止下一轮把它们当新回归「修回去」**。

### 1. 奖励流 `stream:task:reward` **只写不读**

Go 的消费者 `reward.Worker`（`internal/reward/worker.go`）与
`Init.StartInitReward`（`internal/reward/initReward.go`）**整个文件都是注释**，
`task/rpc/task.go:35` 的 `go Init.StartInitReward(ctx)` 调用**也被注释**了
—— 因为它依赖的 `PointsClient` 在 `service_context.go:18-19` 就被注释掉了。

⇒ **RAG 刻意不补这个消费者**（那会引入对 points 域的依赖，而 points 域不在本项目范围内，AGENTS.md §1）。
⇒ **两侧一致，不是偏离。**

### 2. 奖励事件字段名 `score` 与 commented-out 消费者读的 `add_score` 对不上

Go producer 写 `"score"`（`reward/producer.go:27`）；
Go 那份**被注释掉的** consumer 读 `"add_score"`（`reward/worker.go:120`）。
⇒ RAG **照抄 producer 的 `score`**（`TaskRewardPublisher.enqueue`）。
⇒ **这是 Go 侧现存的字段名不一致，不是 RAG 的偏离。**

### 3. 任务定义是**两份独立硬编码**（`Model.AllTaskDefs` vs sink 的常量块）

Go 侧从未校验两者一致。RAG **同样不合并**（合并会新增一条 Go 没有的一致性约束）——
`TaskDefs` 与 `TaskSinkProperties` 各持一份，见两处类注释「两份真相」。

### 4. 读路径与写路径的 Redis key **本来就不一致**

`GetTask` 读 / 两个 sink 删的是 `task:progress:<uid>`；
两个 sink 写的是另外两个 key（点赞路 `task:progress:<uid>:<taskId>`、
文章路 `task:progress:<taskId>:<uid>:<articleId>`），
幂等标记也分 `task:init`（单数，uid 在前）/ `task:inits`（复数，taskId 在前），
done 标记的段序点赞路 uid 在前、文章路 taskId 在前。

⇒ **RAG 保留了这四处不一致**（`TaskRedisKeys` 类注释逐条列表并写明「不要顺手统一」）。
统一它们会让缓存第一次真正生效 ⇒ 属行为变更，必须先回 Go 侧确认意图。

---

## 相关文件

| 关系 | 文件 |
|---|---|
| 本域 DRIFT | [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md) · [`api.md`](../../../../DRIFT/task/api.md) · [`proto.md`](../../../../DRIFT/task/proto.md) |
| 本域踩坑 | [`../../Pitfalls/task/rpc.md`](../../../../Pitfalls/task/rpc.md) |
| 全仓决策档 | [`_INDEX.md`](./_INDEX.md) · [`_cross/decisions.md`](./_cross/decisions.md) |
---

## SD-2026-10-24-TASK-01 · topic 缺失时：Go **进程退出** vs RAG **服务存活**

**签字状态**：✅ 已签字（2026-10-24 登记）。**性质**：RAG 做了**更可用**的那一侧。

### 两侧取值

| | 值 | 出处 |
|---|---|---|
| Go | 🔴 **进程直接退出** | `initGoKa.go:138-145`：`goka.NewProcessor` 返回错误 ⇒ `log.Fatal(err)`。<br>而该错误的**根因**是 topic 不存在：`goka@v1.1.16 processor.go:1012` → `ensureCopartitioned` → `tm.Partitions(topic)` 失败 ⇒ `return 0, fmt.Errorf("Error fetching partitions for topic %s: %v", ...)`（`processor.go:1035-1040`）⇒ **`EnsureStreamExists` 根本没被调用** ⇒ goka 的「建 topic」路径是死的 |
| RAG | ✅ **服务存活**，listener 持续重试 | Spring Kafka 的 listener 容器在 topic 缺失时只记 WARN 并重试；<br>已有运行证据：`TaskProgressUpsertParityTest` 用 `spring.kafka.bootstrap-servers=127.0.0.1:1`（**完全不可达**）仍能起完整 Spring 上下文并跑绿 15 条 |

**实测**（2026-10-24，真 Kafka，干净 broker，不预建 topic）：

```
2026/10/24 04:58:59 Error fetching partitions for topic task.logs.raw: requested topic was not found
⇒ go-task-rpc 进程退出
```

### 为什么 RAG 不照抄

1. **照抄在容器化部署里是错的**：K8s 里「topic 缺失」通常是**部署顺序/配置漂移**的**短暂**状态
   （运维还没建 topic、或 broker 还没就绪）⇒ Go 的行为是 **CrashLoopBackOff**，
   而 **Spring 的重试恰好能自愈**（topic 一建好，listener 立刻开始消费）。
2. **Go 的 `log.Fatal` 发生在 goka 的 `prepareTopics`**，那是 goka 库的设计取舍（它假设 topic 由外部管）；
   RAG 不引入「组件负责 topic 生命周期」这个契约 ⇒ 也就没有在 topic 缺失时自杀的理由。
3. **本条不是「RAG 更安全」而是「RAG 更可用」**：它让 RAG 在部署顺序异常时**自愈**而非**崩溃重启**。

### 复核要点

- ⚠️ **不要**把本条推广到别的域：那是「goka 的 `prepareTopics` 要求输入 topic 已存在」这个**具体前提**的结论。
- ⚠️ 本条**不涉及** topic 的 config（`cleanup.policy` / `retention.ms`）——
  那一侧两侧**都不管**（Go 侧 `handleConfigMismatch` 是 `Ignore` 分支、**连日志都不打**，
  `goka topic_manager.go:158-167`）⇒ RAG 侧同样不建、不改 topic ⇒ **一致，无差异**。
  详见 [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md) §四·七·一。
