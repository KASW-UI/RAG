# `hot-rpc` 有意偏离台账

> **范围**：`service/hot/hot-rpc`（Go `service/hot/rpc/` + `service/hot/heavykeeper/`）。
> **核对日期**：2026-10-23（第三轮修复轮）。
> **差异本体**（未签字的缺陷）在 [`../../DRIFT/hot/rpc.md`](../../DRIFT/hot/rpc.md)。
> 本文件只登记**明知与 Go 不同、经签字批准**的偏离。
>
> ⚠️ **读法**：对拍看到这里登记的条目变红，**先确认是不是这一条**，不要当新回归去「修」。

| ID | 一句话 | 方向 | 状态 |
|---|---|---|---|
| **SD-HOT-RPC-001** | 消费路径的非预期异常**降级成 drop**，不崩进程（Go 会崩） | RAG 更稳 | ✅ 已签字（2026-10-23） |
| **SD-HOT-RPC-002** | Redis 命令**不做** go-redis 式的 3 次自动重试 | RAG 少一次自愈 | ✅ 已签字（2026-10-23） |
| **SD-1**（第一轮） | 分区撤销路径的锁升级：Go 持写锁再取读锁 ⇒ **必然自死锁**；RAG 改为「锁内取引用 → 锁外落盘」 | RAG 能跑 | ✅ 已签字（2026-10-22） |
| **SD-2**（第一轮） | Redis ZSet member 非字符串时**跳过**（Go 是裸类型断言 ⇒ panic） | RAG 不崩 | ✅ 复核通过（2026-10-23，等级可下调为 IMPLEMENTATION DIFFERENCE） |
| **SD-3**（第一轮） | Redis 脚本报错被**误报成「lock lost」**（Go 的 `result, _ :=`） | 两边一样错 | ✅ 复核通过（2026-10-23，照抄 Go） |

---

## SD-HOT-RPC-001 · 消费异常降级成 drop（Go 会崩进程）

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

## SD-HOT-RPC-002 · Redis 命令不做 3 次自动重试

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
- **留痕位置**：`docs/DRIFT/hot/rpc.md` 的 DRIFT-HOTRPC-004 条目。
