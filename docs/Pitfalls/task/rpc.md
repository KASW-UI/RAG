<!-- @new 2026-10-23 · task 域踩坑档 · 由 `align/task` 工作树产出 -->

# `task-rpc` 踩坑记录

> 记「**为什么会这样**」，不是记「哪条不一样」。不一致本身在 [`../../DRIFT/task/rpc.md`](../../DRIFT/task/rpc.md)。

> 🆕 **2026-10-29 台账对齐（W-B lane · follow + task 域）**
>
> - **编号归一**：本档条目的**旧号** `PITFALL-TASK-RPC-NN` **原样保留**在每条的「旧号」行里；**新号** = `PIT-TASK-NN`（与旧号后两位一一对应）。
>   ⚠️ **一处例外**：旧号 `PITFALL-TASK-RPC-15·补`（与 `-15` 同主题的**补充条目**，旧形态是**同文件内重复编号** —— 已登记在
>   [`docs/Pitfalls/_INDEX.md` 的「编号撞号」表](../_INDEX.md)）⇒ **新号取 `PIT-TASK-34`** ⇒ 该撞号在本档**已消解**。
>   ⚠️ **旧号是检索用的历史标识** —— 仓内其它文档（DRIFT / 共享层 / `tools/check-ledger-selfcheck.py` 的 `pit_prefix`）按**旧号**检索 ⇒ 旧号**不可删**。
> - **每条补三字段**：**状态** · **核对日期（2026-10-29）** · **是否影响当前轮次**（填「否」时必须写明**不适用的具体原因**）。
> - **状态判定口径**（本档统一）：
>   - **已闭环** = 该坑在**本域的具体触发点已被消除**（代码 / 配置 / 脚本已改）**且有证据指针**（`文件:行号` 或守门的实测条数）⇒ **残留的通用教训不改变这个判定**（通用教训留在正文里）。
>   - **仍生效** = 该坑的**触发条件本身**仍在（框架 / 语言 / 真源的事实，或本域的编排脚本仍未改）⇒ 今天照旧做法还会踩。
>   - **已撤销** = 用户 / 决策撤销。**待核实** = 判不了（须写清缺什么）⇒ **不许默认任何一侧**。
> - ⚠️ **状态标记取值域**：`✅` / `🟡` / `🔴` / `⚫`（与仓内既有取值域一致）。

---

## PIT-TASK-01 · 【已闭环】 `@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」，15 条真 PG 用例静默全跳

> **旧号**：`PITFALL-TASK-RPC-01` ⇒ **新号**：`PIT-TASK-01`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 注解已删 + Ryuk 已禁用 ⇒ 「静默全跳」的触发条件已消除 ⇒ 本轮只核台账、不改 pom ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/pom.xml:336-341` surefire `environmentVariables` 注入 `TESTCONTAINERS_RYUK_DISABLED=true` ＋ 本轮 surefire 实测 `TaskProgressUpsertParityTest` **t=15 f=0 e=0 s=0**（skipped=0 ⇒ 真 PG 15 条真跑了）

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-003`](../../DRIFT/task/rpc.md#✅-drift-task-rpc-003--15-条真-pg-用例静默全跳证据缺失)
> **核对日期**：2026-10-23　**基线**：`8422116`

### 现象

`TaskProgressUpsertParityTest` 的 **15 条全部 `skipped`**，surefire XML 里原因字面写着：

```xml
<skipped message="Docker is not available"/>
```

而 **Docker 明明可用** —— 同一次构建里另外三个真起容器的测试类全部正常执行：

| 类 | 结果 |
|---|---|
| `FavoriteSchemaParityTest` | `tests=13 skipped=0` ✅ |
| `CommentSchemaPgGateTest` | `tests=18 skipped=0` ✅ |
| `FollowRpcWiringTest` | `tests=20 skipped=0` ✅ |
| `TaskProgressUpsertParityTest` | `tests=15 skipped=15` 🔴 **静默全跳** |

**唯一差别**：只有它加了 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`。

### 🔴 为什么危险（三个因素叠加）

1. **类名带 `Parity`** —— 读报告的人极容易把「有个叫 Parity 的测试类」误读成「已做 Go 对照」，而它一条都没跑；
2. **reactor 报 `BUILD SUCCESS`** —— 没有任何红灯提示你少跑了 15 条；
3. **它测的正是 AGENTS.md §3.3 点名的盲区** —— 该类自己的注释写着：
   > 「这一步不能省：验证 Flyway 是否真跑 + MyBatis-Plus 是否真产出 SqlSessionFactory
   > —— 这两条都是『编译通过 + 纯 mock 单测全绿』永远发现不了的装配问题」

### 根因（每一环都实测过，不是推测）

```
@EnabledIfDockerAvailable 在【类级别】求值 ⇒ 早于任何测试方法就要拿 Docker client
  └─► testcontainers-junit-jupiter:2.0.5 → DockerAvailableDetector.isDockerAvailable()
        └─► DockerClientFactory.instance().client()
              └─► client() 会【顺带确保 Ryuk 资源回收容器可用】
                    └─► 本机拉不到 testcontainers/ryuk:0.14.0 ⇒ ContainerFetchException
                          └─► DockerAvailableDetector 的实现是 catch (Throwable) { return false }
                                └─► 「基础设施拉镜像失败」与「Docker 真不可用」被压成同一个 false
                                      └─► 整类 disabled ⇒ 15 条一条不跑，reactor 照报 BUILD SUCCESS
```

最小探针（裸 `java`，非 surefire 环境）实测：

```java
DockerClientFactory.instance().client();
// ❌ org.testcontainers.containers.ContainerFetchException :
//    Can't get Docker image: RemoteDockerImage(imageName=testcontainers/ryuk:0.14.0)
```

**为什么别的 Testcontainers 类没事**：它们不加这个注解，容器是**懒启动**的 ⇒ 判定阶段不触发 Ryuk。
`task-rpc` 是**全仓唯一漏配 `RYUK_DISABLED` 的容器模块**
（`article-rpc` / `comment-rpc` / `favorite-api` / `favorite-rpc` / `follow-rpc` /
`like-rpc` / `message-rpc` / `admin-api` / `admin-rpc` / `user-api` / `user-rpc` 全都配了）。

### 修法（照同仓既有写法，不重新设计）

1. `task-rpc/pom.xml` 补 surefire `environmentVariables` 注入
   `<TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>`，
   与 `favorite-rpc` / `follow-rpc` **同款**；
   ⚠️ **不要换写法**：各模块 pom 内已有注释说明「`testcontainers.properties` 与 `-D` 均无效」——
   Testcontainers 2.x 的 `ResourceReaper` **只读环境变量**；
2. 删除 `TaskProgressUpsertParityTest` 的 `@EnabledIfDockerAvailable`
   —— Ryuk 已禁用时该注解再无作用，留着只会继续掩盖失败。

**实测**：`tests=15 skipped=0`（此前 `skipped=15`）；全模块 `Tests run: 81, Skipped: 0`。

### 🔴 元教训（比这个 bug 本身更重要）

1. **`@EnabledIfDockerAvailable` 的 `message` 不可信** —— 它把两类完全不同的失败压成同一句话，
   **不要拿它当诊断依据**（本例的真因是「拉不到 ryuk 镜像」，不是「没有 Docker」）。
2. **新模块加 `@Testcontainers` 时若漏配 Ryuk，会静默 disable 而不是报错** ——
   且**恰好是唯一负责真 PG 对照的那个类**。
3. **「跑绿」必须额外断言 `skipped 数 = 0`**。「`tests` 全绿」**不构成**「真 PG 已验」的证据 ——
   上一条教训是 H2 假绿（`message-rpc` 的 `,string` 漂移被绿测试锁死），
   这一条是**假跳**：测试根本没跑，报告却比跑绿还干净。

---

## PIT-TASK-02 · 【已闭环】 relaxed binding 键名对不上 ⇒ 配置键被静默丢弃

> **旧号**：`PITFALL-TASK-RPC-02` ⇒ **新号**：`PIT-TASK-02`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 键名与字段已逐字对齐 ⇒ 「配置里写了也不生效」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskSinkPropertiesBindingTest.java`（本轮 surefire 实测 **t=3 f=0 e=0 s=0**）守「生效值 == Go 常量」＋「配置面是活的」

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-002`](../../DRIFT/task/rpc.md#✅-drift-task-rpc-002--taskdefinitions-的四个-ttl-键是死配置)

### 现象

`application.yml` 写着 `init-ttl: 90d`，改它**不报错、也不生效**。

### 根因

Spring Boot 的 relaxed binding 要求「配置键去掉分隔符 == JavaBean 属性名去掉分隔符」：

| yaml | 字段 | 规范化后 |
|---|---|---|
| `init-ttl` | `initTtlDays` | `initttl` vs `initttldays` ❌ |
| `init-ttl` | `initTtl`（修好后） | `initttl` vs `initttl` ✅ |

而 `@ConfigurationProperties` 的 `ignoreUnknownFields` 默认 `true`
⇒ **对不上的键被静默丢弃**。这是 AGENTS.md §3.3.1「配置里没写 ≠ 没开」的**加强版**：
这里是「**配置里写了也不生效**」。

### 为什么当时没人发现

- **默认值恰好等于 Go 的常量**：90d / 24h / 120d 三条默认值都正确 ⇒ 行为看不出问题；
- yaml 上方还写着「提到 yaml 只是为了让改这个值必须显式可见」——
  **注释在描述一个不存在的能力**（§3.2 的「无出处 idiomatization」：回答不了「这段比 Go 多做了什么」）。

### 抓它的方法（可复用）

**不要靠读代码判断**，写一个探针，且**必须带阳性对照**：

```java
// ✅ 阳性对照：键名对得上的键必须绑得上（否则结论不能归因于键名）
when(ops.increment(anyString())).thenAnswer(...)   // redis-pipe-max → 1234 生效
// ❌ 目标键：init-ttl → 7d 覆盖后实测仍是默认值
```

⚠️ 没有阳性对照时，「断言通过」可能只是因为**探针根本没绑上任何东西**（空过）。
本次实况：阳性对照 ✅ 通过、`init-ttl` ❌ 失败 ⇒ 结论才成立。

### 修法

字段类型 `long` → `java.time.Duration`，键名与 yaml 逐字对应；默认值保持 Go 常量。
配 `TaskSinkPropertiesBindingTest` 长期守着「生效值 == Go 常量」+「配置面是活的」两条。

---

## PIT-TASK-03 · 【已闭环】 注释描述的是「代码没实现的契约」

> **旧号**：`PITFALL-TASK-RPC-03` ⇒ **新号**：`PIT-TASK-03`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— javadoc 已改写为「现状 + 此前行为」双段 ⇒ 「注释描述没实现的契约」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskCountStore.java:26-40`（「🔴🔴 2026-10-23 修正：本类此前是『内存单例』」—— 注释现在描述**已实现**的按 group 命名空间隔离，不再把「应有设计」写成「现状」）

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-001`](../../DRIFT/task/rpc.md#✅-drift-task-rpc-001--taskcountstore-内存单例--两个-goka-group-串号--重启归零)

`TaskCountStore` 的 javadoc 原文写着：

> 「**每个消费组各持有一份**：Go 侧三个 group 是三个独立的 goka processor，存储互不共享。」

而类里只有**一个字段** `private final Map<String, Long> counters`，被两个 consumer 共用。

⚠️ **注释描述的是应该有的设计，不是代码的实际行为**。
这类注释比没有注释更危险：它让 review 的人**以为已经核对过**。

**判据**：凡是「Go 出处」型断言（§3.2 三段式、§3.3.2 的 `Go: …` 措辞），
都必须有一条**能在运行时失败**的测试兜底。注释里的「已经对上了」不算证据。

---

## 相关文件

| 关系 | 文件 |
|---|---|
| 本域 DRIFT | [`../../DRIFT/task/rpc.md`](../../DRIFT/task/rpc.md) · [`api.md`](../../DRIFT/task/api.md) |
| 全仓共性踩坑 | [`../../Pitfalls/_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md) · [`../../Pitfalls/_cross/framework-mechanism-evidence.md`](../_cross/framework-mechanism-evidence.md) |

---

## PIT-TASK-04 · 【仍生效】 探针自己也会撒谎：Python `urllib` 会自动补 `Content-Type`

> **旧号**：`PITFALL-TASK-RPC-04` ⇒ **新号**：`PIT-TASK-04`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— Python `urllib` 的「自动补 `Content-Type`」是**库的事实** ⇒ 触发条件永远存在 ⇒ 但本轮**不改探针脚本**（脚本不在本 lane 写权内）⇒ 不影响本轮台账产出
> **证据**：`tools/difftest/parity-task.py` 本轮实测仍含 `urllib`（grep 命中）⇒ 换新的对拍脚本时同样会踩

> 场景：2026-10-23 `task` 域双侧活体对拍（见 [`../../DRIFT/task/api.md`](../../DRIFT/task/api.md) §二·五）

### 现象

第一版对拍脚本想测「POST **不带** `Content-Type`」，结果两侧都返回 200，**看起来一致**。
换成 `curl -H 'Content-Type:'` 复验才发现：RAG 侧在 `application/x-www-form-urlencoded`
下**返回 400**，Go 返回 200 —— 一条真实差异差点被放过。

### 根因

```python
req = urllib.request.Request(url, data=b'{"id":1001}', method="POST")
# 没有显式 add_header("Content-Type")
# ⇒ urllib **自动**补上 Content-Type: application/x-www-form-urlencoded
```

⇒ 测的其实是「带 form-urlencoded」，不是「不带 Content-Type」。**用例名与被测行为不符。**

### 为什么会漏掉

脚本里明明写了 `content_type=None` 分支「不发这个头」，
但 HTTP 客户端在**更下面一层**补上了 —— 探针断言的是「我没设这个 header」，
而线上真正发生的是「客户端帮我设了一个我没预期的 header」。

### 元教训

1. **探针必须对「我以为由我控制的东西」做端到端验证** ——
   用 `urllib` 这类会自动补默认值的客户端时，要么显式覆盖，要么用 `curl` 这种可控的。
2. **和 `positive control` 是同一条纪律的两面**：
   - 阳性对照回答「探针能不能**看见**差异」（见 PITFALL-TASK-RPC-02）；
   - 本条回答「探针是不是在**测你以为在测的东西**」。
   两者都要做，缺一个结论就不可信。
3. ⚠️ 这与 `tools/difftest/README.md §4.1`（把 404 当就绪）同源：
   **「探针返回了东西」不等于「探针测的是那件事」。**

---

## PIT-TASK-05 · 【仍生效】 对拍「红了 42/43」可能全是 header 格式，别淹没真正的差异

> **旧号**：`PITFALL-TASK-RPC-05` ⇒ **新号**：`PIT-TASK-05`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「格式噪声掩盖真差异」是**对拍方法论** ⇒ 永远可能发生 ⇒ 但本轮**不跑双侧对拍** ⇒ 不影响本轮产出
> **证据**：`tools/difftest/diff.py` 的 `Normalizer`（本轮实测存在）—— 归一化这一步**仍然必须做**，且做完也不能草率收工

> 场景：2026-10-23 `task` 域对拍首跑：`MATCH 1 / DIFF 42`

### 现象

43 条用例里 42 条标红，逐条看下去会发现：
**40 条只差 `Content-Type` 的 charset 参数与分号后空格，2 条差状态码，body 43/43 逐字节一致。**

- Go：`application/json; charset=utf-8`（分号后**有空格**）
- RAG：`application/json`（无 charset）/ `text/plain;charset=utf-8`（**无空格**）

### 教训

1. **对拍的「红」必须分类再看**：状态码 / 响应体 / 业务 header / 纯格式，
   只有前三类值得开单子；最后一类是**全域共性**（别的 api 域也一样），
   单独修会造成「本域与其它域不一致」的新问题。
2. `tools/difftest/diff.py` 里的 `Normalizer` 就是干这个的 ——
   **先把 header 归一，再看还剩什么**。没有这一步，42 条噪声会把 2 条真差异埋掉。
3. ⚠️ 反过来也成立：**不要因为「大部分红的是格式」就草率收工** ——
   本轮那 2 条状态码差异恰恰就是从这堆噪声里筛出来的。

---

## PIT-TASK-06 · 【已闭环】 Go 的 `Pipeline()` 不是事务，Spring 的 `executePipelined` 也不开 MULTI —— **两边都理解错了，于是整条链路静默死掉**

> **旧号**：`PITFALL-TASK-RPC-06` ⇒ **新号**：`PIT-TASK-06`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 回调里已不再调 `operations.exec()` ⇒ 「整条链路静默死掉」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/main/java/com/rag/task/task/rpc/service/RedisPipelines.java:98`（「这里**绝不能**再调 `operations.exec()`」）＋ `:92`/`:121` 用 `executePipelined(SessionCallback)` ＋ 守门 `RedisPipelinesRealRedisGateTest`（本轮 surefire 实测 **t=9 f=0 e=0 s=0** · 真 Redis）

> 场景：2026-10-23 Kafka 端到端对拍首跑。**整个 task 消息处理链路此前是死的**，而 81 条测试全绿。

### 现象

RAG 侧日志（4 次）：

```
io.lettuce.core.RedisCommandExecutionException: ERR EXEC without MULTI
  at io.lettuce.core.internal.Future.awaitAll(Futures.java:213)
  at org.springframework.data.redis.connection.lettuce.LettuceConnection.closePipeline(LettuceConnection.java:596)
Caused by: com.rag.task.task.rpc.service.RedisPipelines.exec(RedisPipelines.java:48)
  at UserLikeProgressSink.pipelineSetNx(UserLikeProgressSink.java:322)
```

### 根因（三方语义对不上）

| | 事实 | 出处 |
|---|---|---|
| Go 的 `Pipeline()` | **不是事务**，只是批量 flush | go-redis v9.20.0 `redis.go:1508-1513`（`exec: pipelineExecer(processPipelineHook)`）；`redis.go:1521-1528` 的 `TxPipeline()` 才 `wrapMultiExec`；`pipeline.go:18-20` 注释原文 *"Pipeline is not a transaction"* |
| Go 的 sink | 用的是 `c.rdb.Pipeline()` | `like_sink.go:167`、`like_sink.go:210` |
| Spring 的 `executePipelined` | **不开 MULTI**（只置标志位） | `LettuceConnection.java:567-575` `openPipeline()` |
| Spring 的 `exec()` | pipeline 模式下**直接发 EXEC** | `LettuceConnection.java:688-719` ⇒ 服务端回 `ERR EXEC without MULTI` |
| Spring 的框架契约 | 回调**必须返回 null** | `RedisOperations.java:118-128` javadoc；`RedisTemplate.java:464-467` 强制抛异常 |

⇒ RAG 在 `executePipelined` 的回调里**多调了一次 `operations.exec()``，
把「Go 的批量 flush」误当成「MULTI/EXEC 事务」⇒ 服务端直接拒绝。

### 为什么杀伤面这么大

`ERR EXEC without MULTI` 抛在 `lazyInitTaskIfNeeded` 的**第一步 SETNX** 上：

```
SETNX 抛 ⇒ 穿出 lazyInit ⇒ 穿出 flushOnce ⇒ 被 flushTick catch ⇒ **整轮 flush 中止**
```

⇒ `like:total:*`、`task_progress`、任务完成、奖励事件**全部不发生**。
**组件「跑着」「健康」「测试全绿」，但核心功能一行都没执行过。**

### 教训

1. **「两边的同名概念」不保证语义相同**：`Pipeline()` 在 Go 与 Spring 里都叫 pipeline，
   但 Go 的**不是**事务、Spring 的**也不开** MULTI —— 真正的对应物是
   Spring 的 `executePipelined`（且**回调必须返回 null**）。
2. **框架的 javadoc 契约要当约束读**，不是当建议读。
   `RedisOperations.java:118-128` 已经把「must return null」写在脸上。
3. **「出错 ⇒ 抛」与「出错 ⇒ 吞」在 Go 里逐点不同**，不能统一处理：
   `like_sink.go:223-225` 抛，`get_task_logic.go:73-76` 吞（注释写着「回填失败不影响返回」）。
   统一成一种 ⇒ 终态分叉（见 DRIFT-TASK-RPC-005）。

---

## PIT-TASK-07 · 【已闭环】 81 条测试**零真 Redis 覆盖** ⇒「跑绿」证明不了 Redis 路径

> **旧号**：`PITFALL-TASK-RPC-07` ⇒ **新号**：`PIT-TASK-07`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 真 Redis 覆盖已从 0 条补到 4 类 21 条 ⇒ 「跑绿证明不了 Redis 路径」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：本轮 surefire 实测（真 Redis）：`RedisPipelinesRealRedisGateTest` **9** ＋ `TaskRedisCommandTimeoutGateTest` **7** ＋ `TaskRedisPoolExhaustionP99GateTest` **3** ＋ `TaskRedisDisconnectFastRejectGateTest` **2** = **21 条**

### 现象

`RedisPipelines.exec` 里那个让整条链路死掉的 `operations.exec()`，
在 81 条测试全绿的情况下长期存在。拆开看这 81 条覆盖了什么：

| 测试类 | 条数 | 碰 Redis 了吗 |
|---|---|---|
| `TaskSinkConsumeTest` | 19 | ❌ 把 `StringRedisTemplate` **整个 mock 掉**，只验内存 delta |
| `TaskProgressUpsertParityTest` | 15 | ❌ 只验 PG，Redis 指着 `127.0.0.1:1`（不可达） |
| `TaskEventParsingTest` | 21 | ❌ 纯解析 |
| `TaskDomainServiceTest` | 19 | ❌ mock `StringRedisTemplate`，且**连方法名都断言了** |
| 其余 | 7 | ❌ |

⇒ **没有任何一条测试让 Redis pipeline 真的跑过一条命令。**

### 教训

1. **「有 Redis 依赖」≠「有 Redis 覆盖」**：要数的是
   **有几条用例让真 Redis 收到过命令**，不是 pom 里有没有 `spring-boot-starter-data-redis`。
2. **mock 掉的东西不会告诉你它坏没坏**。本例中 mock 反而**固化**了错误的机制
   （`verify(redis).execute(cap.capture())` 断言的正是那个 bug 机制）。
3. ⇒ 修的时候要**同时**改实现和那 3 处锁错机制的断言，并说清为什么改。
4. **变异测试是验「守门测试不是假绿」的唯一硬手段**：
   本轮把 `exec()` 退回 `operations.exec()` 后，9 条里 **5 条报错**（含两条端到端 sink 用例），
   证明守门测试真的能抓住它。

---

## PIT-TASK-08 · 【仍生效】 Redis 的 `SET` 是**类型无关**的 ⇒ 断言会「因为错误的原因而通过」

> **旧号**：`PITFALL-TASK-RPC-08` ⇒ **新号**：`PIT-TASK-08`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— Redis 的 `SET` **类型无关**是**Redis 事实** ⇒ 写「错误路径断言」时仍可能选一个不触发错误的错误源 ⇒ 但**本域的断言已按正确姿势改写** ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mq/RedisPipelinesRealRedisGateTest.java` 的 `execPropagatesCommandError`（用 **string 类型 key + `HGET`** 造错误源 ⇒ 注释已写明正确姿势）

> 场景：2026-10-23 写真 Redis 守门测试时踩到。

### 现象

想造一个「命令级错误」，用「对 hash 类型的 key 做 `SET`」当错误源。
结果**不抛异常** —— 而且**非 pipeline 的对照组也不抛**。

### 根因

Redis 的 `SET` **会覆盖任何类型的 key**（它是类型无关的写入）。
WRONGTYPE 只发生在「用某类型的读/改命令去动另一种类型的 key」上
（对 string key 做 `HGET` 才是 WRONGTYPE）。

### 为什么危险

这条断言当时**通过了** —— 但它通过的原因是「错误源根本没触发错误」，
**不是**因为「实现正确」。⇒ 这是 AGENTS.md §3.3 说的
「绿测试可能是错误答案的守卫」的又一个变体。

### 教训

1. **阳性对照的对照组也要有**：本例是「非 pipeline 同样操作也不抛」暴露了前提错误。
   ⇒ 写错误路径的断言时，**必须先确认这个错误在直连模式下也会发生**。
2. 正确做法：对 **string** 类型的 key 做 `HGET`（已写进
   `RedisPipelinesRealRedisGateTest.execPropagatesCommandError` 的注释里）。

---

## PIT-TASK-09 · 【仍生效】 删 Kafka topic **不会**删 `__consumer_offsets` 里的已提交位点

> **旧号**：`PITFALL-TASK-RPC-09` ⇒ **新号**：`PIT-TASK-09`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 已提交位点存在 `__consumer_offsets` 是**Kafka 事实** ⇒ 删 topic 不会清位点 ⇒ 永远可能发生 ⇒ 但本轮**不跑 Kafka 对拍** ⇒ 不影响本轮产出
> **证据**：Go `service/task/rpc/internal/mq/initGoKa.go:134-135` `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest` 仍在 ⇒ 「`OffsetOldest` 只在无已提交位点时生效」的前提未变

### 现象

为了重置对拍状态，删掉全部 topic 再重建、再灌同样的 7 条消息：

```
task.logs.raw          task.logs.raw:0:7   ← 消息在
task.logs.raw.User     task.logs.raw.User:0:0   ← 路由处理器一条都没发出
```

RAG 侧却完全正常（`rag.task.logs.raw.User:0:7`）。

### 根因

- 已提交位点存在 `__consumer_offsets`，键是 **group + topic + partition**。
  **topic 同名 ⇒ 位点还在**（哪怕 topic 已删重建）。
- Go 的 goka 用 `cfg.Consumer.Offsets.Initial = sarama.OffsetOldest`
  （`initGoKa.go:134-135` 等三处），但 `OffsetOldest` **只在没有已提交位点时生效**。
  有旧位点（比如 9）就从 9 开始 ⇒ 新 topic 只有 7 条（offset 0-6）⇒ **整段被跳过**。
- RAG 侧侥幸没事：`@KafkaListener` 用 `auto-offset-reset=latest`，
  而消息是**启动之后**才灌的 ⇒ 起点就是队尾 ⇒ 全部收到。

⇒ 一侧全收、一侧全不收 —— **典型的「假差异」**，会让人误判成 RAG 的 bug。

### 附带坑：goka 的本地 LevelDB 缓存

goka 自己会报：

```
kafka tells us there's no message in the topic, but our cache has one.
The table might be gone. Try to delete your local cache!
Topic task.rpc.consumer71-table, partition 0, hwm 0, local offset 23
```

缓存路径 = `/private/tmp/goka/processor/<group>/<topic>.<partition>`（goka `storage/builders.go:18-21`）。
⇒ **删 topic 必须连本地缓存一起清**（用 `mv` 挪走，不要动整个 `/private/tmp/goka`——
别的会话可能也在用）。

### 教训

1. **Kafka 的「重置」= topic + consumer group 位点 + 客户端本地缓存，三样都要清**。
   最省事、最不会漏的做法：**重建整个 Kafka 容器**（本轮就这么干的）。
2. **不对称的重置会造出假差异** ⇒ 两侧的重置必须**同样彻底**。

---

## PIT-TASK-10 · 【仍生效】 容器内 Kafka CLI 必须用 **INTERNAL** 监听器

> **旧号**：`PITFALL-TASK-RPC-10` ⇒ **新号**：`PIT-TASK-10`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— `advertised.listeners` 与新镜像的 CLI 包名是**镜像/中间件事实** ⇒ 永远可能再踩 ⇒ 但本轮**不跑容器内 Kafka CLI** ⇒ 不影响本轮产出
> **证据**：`tools/difftest/docker-compose.task-kafka.yml` 的 `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:59992,INTERNAL://localhost:19092`（本轮实测仍在） ⇒ 容器内必须用 INTERNAL 监听器的结论未变

### 现象

`kafka-topics.sh --bootstrap-server localhost:9092` 在容器内**一直重连**：

```
WARN [AdminClient clientId=adminclient-1] Connection to node 1 (/127.0.0.1:59992) could not be established.
```

`kafka-get-offsets.sh` 则直接 `ClassNotFoundException: kafka.tools.GetOffsetShell`（包名已变）。

### 根因

容器 env：

```
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:59992,INTERNAL://localhost:19092
```

`127.0.0.1:59992` 是**宿主机**的端口映射。容器内的客户端 bootstrap 到 `localhost:9092` 后，
broker 回头告知 `127.0.0.1:59992` ⇒ 在容器自己的 netns 里这个端口**没人监听** ⇒ 永远连不上。

⇒ 容器内要用 `--bootstrap-server localhost:19092`（INTERNAL 监听器，advertised 为 `localhost:19092`）。
**宿主机上的客户端不受影响**（宿主机视角 `59992 → 容器 9092` 是通的）——
所以「服务能连上、CLI 连不上」是**预期**的，不是环境坏了。

### 教训

1. **`advertised.listeners` 决定的是「客户端拿到什么地址」**，不是「broker 监听什么」。
   同理适用于数据库、gRPC 等任何有 advertised 地址的中间件。
2. 新版 `apache/kafka` 镜像里 `kafka.tools.GetOffsetShell` 已改名 ⇒ 用
   **`kafka-get-offsets.sh`**；`--property` 也已 deprecated ⇒ 用 `--formatter-property`。
3. ⚠️ **「offset 查询失败」不等于「offset 是 0」**。本轮最初就是
   `GetOffsetShell` 报错 ⇒ 输出为空 ⇒ 被误读成「消息没灌进去」，
   实际 offset 是 16。**测量手段失败时，先怀疑测量手段。**

---

## PIT-TASK-11 · 【仍生效】 方法签名对了 ≠ 方法会被调到；而且**调用时机**比签名更致命

> **旧号**：`PITFALL-TASK-RPC-11` ⇒ **新号**：`PIT-TASK-11`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「消费点在更早环节 ⇒ 这一层改不动」是**框架事实**（body 在 `EmptyBodyCheckingHttpInputMessage` 构造期就被消费）⇒ 永远可能再踩 ⇒ 但**本域的缺陷已修并有守门** ⇒ 不影响本轮产出
> **证据**：守门 `service/task/task-api/src/test/java/com/rag/task/task/api/TaskApiContentTypeTest.java`（本轮 surefire 实测 **t=13 f=0 e=0 s=0**）

### 现象

修 DRIFT-TASK-API-003（form-urlencoded 的 POST 被 RAG 拒、Go 收）时，
第一版修法选在**转换器层**：在 `ContentTypeAgnosticJsonBodyConverter` 里覆盖
`read(ResolvableType, HttpInputMessage, Map)`，form POST 时改传一个直读
`HttpServletRequest.getInputStream()` 的 `HttpInputMessage`。

装上后**一点效果都没有**：form-urlencoded 仍然 400。

### 根因（两层）

**第一层：拿不到原始 request。** 转换器拿到的 `HttpInputMessage` 是
`EmptyBodyCheckingHttpInputMessage`（spring-webmvc 7.0.9
`AbstractMessageConverterMethodArgumentResolver.java:355-380`），
**不是** `ServletServerHttpRequest` ⇒ `inputMessage instanceof ServletServerHttpRequest`
**永远 false** ⇒ 绕开分支从不执行。

**第二层（更要命）：body 在转换器被调用之前就已经被换掉了。**
`EmptyBodyCheckingHttpInputMessage` 的**构造器**就调了一次 `inputMessage.getBody()`
⇒ 那一次调用走的是 `ServletServerHttpRequest.getBody()`（spring-web 7.0.9 `:227-234`）的
「form POST ⇒ 从表单参数重建 body」分支 ⇒ **原始 body 此刻已经没了**。
⇒ 转换器层是**事后诸葛**，再怎么改也拿不回原始字节。

⚠️ 而 `read(ResolvableType, HttpInputMessage, Map)` 这个签名**本身是对的** ——
Boot 4 / Jackson 3 的 `AbstractJacksonHttpMessageConverter extends AbstractSmartHttpMessageConverter`
（`:88`），所以 MVC 走 **SMART 分支**，确实调它（`:313`）。
⇒ **签名没错、时机错了** —— 只看「方法会不会被调」这一层，根本看不出问题在哪。

### 教训

1. **要判「某一层能不能修这个 bug」，先问「这个 bug 的消费点在不在这一层之后」。**
   消费点在**更早**的环节 ⇒ 这一层再怎么改都是徒劳。
2. **修不动的时候，先去读那一层的源码**（本轮读的是 spring-web / spring-webmvc 的
   `-sources.jar`，不是猜）。本轮一开始就在猜「是 Tomcat 连接器层还是 Spring 层」，
   读源码后 20 分钟就定位到了「`EmptyBodyCheckingHttpInputMessage` 构造期就消费」。
3. ⚠️ **`instanceof` 判不出来的东西，多半是「拿错了对象」而不是「类型不对」** ——
   判 false 时别急着改判据，先问「我以为传进来的是什么」。

---

## PIT-TASK-12 · 【仍生效】 配置项**名字写错 = 静默失效**，而且用它「排除 Spring 侧」会得出**假阴性**

> **旧号**：`PITFALL-TASK-RPC-12` ⇒ **新号**：`PIT-TASK-12`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「属性名写错 ⇒ 静默失效」是**Boot 的通病** ⇒ 永远可能再踩 ⇒ 但本域已加了「直接读 `Environment` 断言属性真被解析」的守门 ⇒ 不影响本轮产出
> **证据**：`TaskApiContentTypeTest.java`（本轮 surefire 实测 t=13）—— 断言 `spring.servlet.multipart.enabled` 真被解析成目标值、且 `spring.mvc.servlet.multipart.enabled` 不存在

### 现象

DRIFT-TASK-API-003 上一版台账写着：

> 已实测**排除** Spring 侧：加 `--spring.mvc.formcontent.filter.enabled=false` 与
> `--spring.mvc.servlet.multipart.enabled=false` 后行为**完全不变**。

本轮实测：**`spring.mvc.servlet.multipart.enabled` 这个属性名是错的**。
Boot 4 的 `MultipartAutoConfiguration` 上是
`@ConditionalOnBooleanProperty(name = "spring.servlet.multipart.enabled", matchIfMissing = true)`
（在 `spring-boot-servlet-4.0.8.jar` 里）⇒ **没有 `mvc.`**。

⇒ 写错名字 ⇒ **没人读那个属性** ⇒ 开关根本没关上 ⇒ 「行为完全不变」是必然的。

### 为什么这条特别危险

它不是「改错没生效」这么简单 —— 它被用来**排除一个方向**：
「实测排除 Spring 侧 ⇒ 剩余消费者在 Tomcat 连接器层」
⇒ **一个写错的属性名，制造了一条错误的根因结论**，并据此否掉了真正可修的方案。

### 教训

1. **「加了开关行为没变」要区分两种情况**：
   (a) 开关生效了、确实不是它 ⇒ 结论成立；
   (b) **开关压根没生效**（名字写错 / 拼错 / 放错层级）⇒ **结论无效**。
   ⇒ 用它做排除法之前，**必须先单独证明开关生效**：
   读 `Environment` 断言属性真的被解析成目标值，或看启动日志里的条件装配结果。
2. ⚠️ **Spring Boot 的属性名不能靠直觉拼**：`spring.mvc.*` 是 Spring Boot 2/3 的形状，
   Boot 4 拆模块后不少 `mvc.*` 已经挪走（`spring.servlet.multipart.*` 就是一例）。
   ⇒ 拿不准就**去 jar 里看那个 `@ConditionalOn*Property` 的 name**。
3. ⇒ 本轮据此加了一条守门断言：直接读 `Environment` 断言
   `spring.servlet.multipart.enabled == "false"`，并断言
   `spring.mvc.servlet.multipart.enabled` **不存在**（防止有人改回错名字）。

---

## PIT-TASK-13 · 【仍生效】 探针里的一句 `getInputStream().available()`，**自己触发了**它要测的那个行为

> **旧号**：`PITFALL-TASK-RPC-13` ⇒ **新号**：`PIT-TASK-13`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— Tomcat 对 form POST 的**惰性解析**是**容器事实** ⇒ 「探针自己触发了被测行为」永远可能再发生 ⇒ 但**本域的探针已整个删掉**、换成真 Tomcat 守门 ⇒ 不影响本轮产出
> **证据**：守门 `TaskApiContentTypeTest.java`（真 Tomcat · 本轮实测 t=13）—— 探针 filter 已不在仓内

### 现象

同一个 form-urlencoded 请求，两次观测到的**错误文案不一样**：

| 观测方式 | 错误文案 |
|---|---|
| 探针里挂了「读 `available()`」的 filter | `Required request body is missing` |
| 不挂任何 filter | `JSON parse error: Unexpected character ('%' (code 37))` |

⇒ 看起来像「行为不稳定」，实际是**探针自己改变了被测行为**。

### 根因

Tomcat 对 `application/x-www-form-urlencoded` 的 POST **惰性解析表单**：
只有当代码调用 `request.getParameter*()` 时才去读 body。
而**探针 filter 里那句 `req.getInputStream().available()`** ——
在 Tomcat 上，它会触发输入流的准备 ⇒ 表单参数被解析 ⇒ **body 提前被消费**。
⇒ 之后 Spring 再去 `getParameterMap()` 时，参数表已经是空的 ⇒
重建出来的 body 也是空的 ⇒ 报「Required request body is missing」。

⚠️ 而 `available()` 在 Tomcat 上**只报缓冲区字节数**，不等于剩余量 ——
拿它当「body 还在不在」的信号，结论本身就是错的。

### 教训

1. **探针本身是观测的一部分** —— 它的每一次调用都可能改变被测系统。
   ⇒ 「加个 filter 看看 body 还在不在」这类做法要先问：
   **我这个调用会不会触发解析/消费？**
2. ⚠️ **`available()` 不是「剩余可读字节数」**（Tomcat 上尤其如此）。
   要判断 body 有没有被消费，看 `getContentLength()` + 实际读到的字节数，
   或者**别读，只看下游有没有拿到完整内容**。
3. ⇒ 本轮把这条探针**整个删掉**，换成「真 Tomcat + 真实业务请求 + 断言状态码与响应体」的守门测试。

---

## PIT-TASK-14 · 【已闭环】 对拍两侧的 Redis **db 编号**要按「这一轮在测什么」来定，不是照抄别的脚本

> **旧号**：`PITFALL-TASK-RPC-14` ⇒ **新号**：`PIT-TASK-14`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 两个脚本的 Redis db 约定已分别写进脚本本身（隔离 / 共用）⇒ 「照抄配置」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/difftest/run-task-kafka-e2e.sh:179` `--spring.data.redis.database=1`（**隔离**） ＋ `tools/difftest/run-task-http-parity.sh:213` `--spring.data.redis.database=0`（**共用**）—— 两个脚本各自写明理由

### 现象

HTTP 对拍矩阵里，**Redis 缓存命中路径**的 3 条用例两侧不一致：
Go 读出 `completionProgress` = 2 / -7 / 99，RAG **一律 0**。
⇒ 第一反应是「RAG 的缓存解析有漂移」。

### 根因

**是编排脚本配错了，不是 RAG 的漂移。**

- 矩阵脚本用 `redis-cli`（**默认 db0**）手工 `HSET task:progress:<uid>`；
- Go 的 `task-rpc.yaml` 里 `LikeRedis.DB: 0` ⇒ Go 读 db0 ✔；
- 而本轮新写的编排脚本照抄了 **Kafka E2E 脚本**的 `--spring.data.redis.database=1`
  ⇒ RAG 读 db1 ⇒ **读了个空库** ⇒ 回落 0。

⇒ **两个脚本的 Redis 隔离需求正好相反**：

| 脚本 | 场景 | 两侧 Redis | 为什么 |
|---|---|---|---|
| `run-task-kafka-e2e.sh` | Kafka 消息链路 | **隔离**（Go db0 / RAG db1） | 两侧 rpc **同时往 Redis 写**，必须隔离，否则互相污染 |
| `run-task-http-parity.sh` | HTTP 读路径 | **共用**（都 db0） | 要测的正是「**同一段 Redis 值**两侧解析结果是否一致」，必须读同一份 |

### 教训

1. **照抄配置前先问「这一轮在测什么」** —— 同样的参数在不同场景下要求**相反**。
2. **「矩阵里某条用例两侧不一致」的第一嫌疑是探针配置**，不是被测代码。
   ⇒ 先确认两侧读的是**同一份状态**，再谈漂移。
3. ⇒ 本轮在编排脚本里把这条写成了**带理由的注释**（不是只写「改成 0」），
   免得下一个人再照抄 Kafka 脚本。

---

## PIT-TASK-15 · 【仍生效】 bash 里 `$var` 紧跟**多字节字符**，变量名会被吃掉首字节

> **旧号**：`PITFALL-TASK-RPC-15` ⇒ **新号**：`PIT-TASK-15`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— bash 3.2 把**多字节字符首字节**并进变量名是**shell 事实** ⇒ 永远可能再踩 ⇒ 但本轮**不改脚本**（脚本不在本 lane 写权内）⇒ 不影响本轮台账产出
> **证据**：本文件正文的实测记录（`N=12; echo "$N，x"` ⇒ `x`）＋ 同源条目 `PIT-TASK-21`（全角标点） ＋ 域外同款 `docs/Pitfalls/follow/rpc.md` 的 `PIT-FOLLOW-013`

### 现象

编排脚本跑到一半炸了：

```
line 128: g?: unbound variable
```

源码是：

```bash
echo "  ✓ 移走 goka 本地缓存 $g（⑤）"
```

### 根因

bash 的变量名只认 `[A-Za-z_][A-Za-z0-9_]*`，但 **`$g` 后面紧跟的 `（`（U+FF08，
UTF-8 首字节 `0xEF`）会被算进变量名** ⇒ 变量名变成 `g\xef` ⇒ 未绑定变量。

⚠️ **同样的写法在既有的 Kafka 脚本里没事**，因为那里是 `"... $g"` —— `$g` 后面是**引号**，
不是多字节字符 ⇒ 一眼看不出差别。

### 教训

1. **凡是多字节字符（中文/全角标点）紧跟 `$var`，一律写 `${var}`。**
   ⇒ 已用一条正则把仓内脚本扫了一遍（`\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7f])`），
   本轮只命中 1 处。
2. ⇒ **脚本里凡是会打印中文的变量，一律用 `${}` 显式界定边界** —— 别赌 bash 的解析。

---

## PIT-TASK-16 · 【已闭环】 `pkill` 之后只 `sleep 2` ⇒ 新进程抢不到端口，而症状是「应用 503」

> **旧号**：`PITFALL-TASK-RPC-16` ⇒ **新号**：`PIT-TASK-16`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 编排脚本已改成「轮询等端口空闲 / 等端口开始监听」⇒ 「旧进程抢不到端口」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/difftest/run-task-http-parity.sh:78` `wait_port_free()` ＋ `:89` `wait_port_listen()` ＋ `:183` 「端口没释放，放弃」＋ `:190`/`:193`/`:227` 三处调用

### 现象

跑 HTTP 对拍编排脚本时，**RAG api 一直返回 503**（`grpcCode=UNAVAILABLE http=503 desc=io exception`），
就绪探针轮询 120 秒仍不通。看起来像「应用起不来 / gRPC 客户端连不上」。

### 真因

**RAG task-rpc 根本没起来**，日志里是：
```
Caused by: java.net.BindException: Address already in use
```
⇒ 上一轮 `KEEP_UP=1` 留下的旧 rpc 进程**还在监听 59005**。

⚠️ 编排脚本第 5 步确实 `pkill` 了，但之后只 `sleep 2` ⇒
**JVM 的优雅关闭要 3~4 秒**（Spring 上下文 close → Hikari 关池 → Kafka 消费者下线 →
`ObservabilityLifecycle` 的 destroy 回调）⇒ 新进程在旧进程还没放端口时就去 bind ⇒ 失败。

实测时间线：旧 rpc **03:56:47** 才关完（`HikariPool-1 - Shutdown completed`），
新 rpc **03:56:46** 就去 bind 了 ⇒ 差了 1 秒就撞上。

### 为什么这个坑特别贵

**症状离病根很远**：报出来的是「api 503」，而病根是「rpc 端口没释放」。
中间还隔着一层「api 依赖 rpc」⇒ 极易误判成
「RAG 的 gRPC 客户端配错了」「服务发现没通」「上一轮的改动把 rpc 弄坏了」。

### 教训

1. **停服务后要等端口真的释放，不能只 sleep 固定秒数** ——
   ⇒ 编排脚本改成 `wait_port_free()`（轮询 `lsof -iTCP:<port> -sTCP:LISTEN`）。
2. **起服务后要等端口真的开始监听，并盯住进程是否已死** ——
   ⇒ `wait_port_listen()`，且一旦日志里出现 `Application run failed` / `BindException`
   就**立刻失败并打出日志尾部**，而不是让下游探针空转两分钟。
3. ⇒ 本条已固化进
   [`tools/difftest/run-task-http-parity.sh`](../../../tools/difftest/run-task-http-parity.sh)
   的注释与函数里（`wait_port_free` / `wait_port_listen`）。

---

## PIT-TASK-34 · 【仍生效】 bash 的 `$var` 紧跟多字节字符：**实测规则**（本轮又踩了一次）

> **旧号**：`PITFALL-TASK-RPC-15·补` ⇒ **新号**：`PIT-TASK-34`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— bash 3.2 的变量名解析规则不会变 ⇒ 触发条件永远存在 ⇒ 但本轮**不改脚本** ⇒ 不影响本轮台账产出
> **证据**：本文件正文的**实测规则表**（`set -u` 开 ⇒ 硬失败 `unbound variable`；`set -u` 关 ⇒ 静默展开成空串且首字节被吞） ＋ 机械检查正则（`grep -nP '\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7f])'`）

> 补充 2026-10-24：这条写完的**当天**又踩了一次（写 Nacos 对拍脚本时），
> 而且踩的还是同一种 ⇒ 值得把规则说准。

### 实测规则（macOS 自带 bash 3.2.57）

| 场景 | 行为 |
|---|---|
| `set -u` **开** | 🔴 **硬失败**：`xxx?: unbound variable`（变量名被并上了多字节字符的首字节） |
| `set -u` **关** | ⚠️ **静默展开成空串**（该多字节字符的首字节被吞掉，后面几个字节照原样输出 ⇒ 屏幕上是乱码） |

实测：
```bash
a=1; echo "$a（后面"      # ⇒ "??后面"（`（` 的首字节被吞，后两个字节成了乱码）
NACOS_NAME=xyz; echo "$NACOS_NAME，"   # ⇒ "??"
```
⇒ **不是「显示损坏」那么轻**：`set -u` 下是硬失败，没有 `set -u` 时**变量直接变成空串**。

### 为什么会漏掉

**同样的写法在同一个文件里可能一处没事、一处有事** ——
取决于紧跟的是哪个多字节字符、以及**前面有没有别的 `$var`**：
本轮既有脚本 `run.sh` 里就有两处同款潜伏 bug（`$a（…`、`$j，…`），
都在**错误分支**上（平时走不到）⇒ 一直没暴露。

### 教训

1. **凡是多字节字符（中文/全角标点）紧跟 `$var`，一律写 `${var}`。**
2. ⇒ 扫描用正则（**排除注释行**）：
   ```bash
   grep -nP '\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7f])' tools/difftest/*.sh
   ```
   本轮用它扫出 4 处（自己新写的）+ 2 处（既有 `run.sh`）⇒ **既有那两处也一并修了**
   （`run.sh` 有 `set -euo pipefail` ⇒ 那两条错误分支是**硬失败**）。

---

## PIT-TASK-17 · 【仍生效】 Nacos 2.x 客户端按「HTTP 端口 + 1000」推算 gRPC 端口 ⇒ 映射错了会让**整个应用起不来**

> **旧号**：`PITFALL-TASK-RPC-17` ⇒ **新号**：`PIT-TASK-17`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「Nacos 2.x 客户端按 HTTP 端口 + 1000 推算 gRPC 端口」是**客户端事实** ⇒ 永远可能再踩 ⇒ 但脚本已把端口关系写死并注释 ⇒ 不影响本轮产出
> **证据**：`tools/difftest/run-task-nacos-discovery.sh:36-42`（`NACOS_GRPC=50849  # = NACOS_HTTP + 1000，不能乱填`）＋ `:81` 端口映射

### 现象

把 `task-api` 注册到真 Nacos 时，**应用启动失败**：

```
ERROR c.a.c.n.registry.NacosServiceRegistry : nacos registry, task-api register failed...
ErrCode:-401, ErrMsg:Client not connected, current status:STARTING
...
Caused by: java.lang.IllegalStateException: Application run failed
```

### 根因

Nacos **2.x 的客户端用 gRPC 注册**（nacos-client 3.x 的 `NamingGrpcClientProxy`），
而它**按「HTTP 端口 + 1000」推算 gRPC 端口** ⇒ 宿主机必须把
`HTTP端口+1000` 映射到容器的 `9848`。

本轮把 HTTP 映射到 `49849`、gRPC 映射到 `50949`（随手写的）⇒
客户端去找 `49849+1000 = 50849` ⇒ 连不上 ⇒ 注册失败；
又因为 `failFast=true` ⇒ **整个 ApplicationContext 启动失败**。

### 为什么这个坑贵

**症状是「app 起不来」，病根是「端口映射少算了一个 1000」** ——
中间还隔着「注册失败」这层 ⇒ 极易误判成
「Nacos 版本不对」「客户端与服务端 gRPC 版本不兼容」「网络不通」。

### 教训

1. **Nacos 2.x 的宿主机端口映射必须是 `HTTP` 与 `HTTP+1000` 两条**，
   且 gRPC 那条**不能随手填**。
2. ⇒ 已在
   [`tools/difftest/run-task-nacos-discovery.sh`](../../../tools/difftest/run-task-nacos-discovery.sh)
   的变量定义处写死注释（NACOS_GRPC=NACOS_HTTP+1000，不能乱填）。
3. ⚠️ 附带一条：**Nacos 的 readiness 端点返回 `OK`，不是 `UP`** ——
   探针写 `grep -q UP` 会永远等不到（实测空转 120 秒才失败）。


---

## PIT-TASK-18 · 【已闭环】 `@KafkaListener(properties=…)` **不走 Boot 松散绑定** ⇒ 连字符写法被 Kafka 静默忽略 ⇒ 「earliest 从未生效」而 326 条测试全绿

> **旧号**：`PITFALL-TASK-RPC-18` ⇒ **新号**：`PIT-TASK-18`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 六个 listener 全部改成点分键名 ⇒ 「连字符被 Kafka 静默忽略」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskUserCountConsumer.java:80` / `TaskArticleCountConsumer.java:80` / `TaskRawDiagnosticConsumer.java:54` —— 三处均为 `properties = "auto.offset.reset=..."` ＋ 守门 `TaskKafkaOffsetResetContractTest`（本轮 surefire 实测 **t=5 f=0 e=0 s=0**）

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-006`](../../DRIFT/task/rpc.md#✅-drift-task-rpc-006--per-listener-的-offset-reset-写成了连字符--kafka-直接忽略--earliest-从未生效)

### 现象

台账上一版写的是「goka 三处 `OffsetOldest` 与 kq 三处 `"latest"` 方向相反，
**RAG 逐 listener 用 `properties` 覆盖，与 Go 一致**」—— 而且**旁边就挂着一条待办**：
「goka 的 `OffsetOldest` 只作用于首次消费无位点的 group；本条结论限于**配置意图一致**，
**未做活体验证**」。

⇒ 2026-10-24 真去验了，发现**「配置意图一致」本身就是错的**：

```
# RAG 侧 consumer 的 config dump（真 Kafka）：
auto.offset.reset = latest
AbstractConfig : These configurations '[auto-offset-reset]' were supplied but are not used yet.
```

### 根因（两层）

1. **Kafka 的合法配置名是点分的 `auto.offset.reset`**
   （`org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG`）。
2. ⚠️ **`@KafkaListener(properties = "auto-offset-reset=earliest")` 不走 Spring Boot 的松散绑定**
   ⇒ 这个字符串**原样**塞进 consumer 的 props ⇒ Kafka 走
   `ConfigDef.parse` 的 **unused** 分支 ⇒ **只打一条 info 日志，然后彻底忽略**。

⇒ 同一个语义、**两条完全不同的生效路径**：

| 写法 | 是否生效 | 原因 |
|---|---|---|
| `spring.kafka.consumer.auto-offset-reset: latest`（yaml） | ✅ **生效** | Boot 松散绑定会转成 `auto.offset.reset` |
| `@KafkaListener(properties = "auto-offset-reset=earliest")` | ❌ **被忽略** | 原样传递，Kafka 不认连字符 |

⇒ **两个 listener 写的值一个生效一个不生效，肉眼看不出来** ——
`latest` 那三处「碰巧对」（全局默认恰好也是 latest），`earliest` 那三处**从未生效**。

### 为什么 326 条测试全绿也抓不到

⚠️ **所有既有对拍都是「先起消费者、后灌消息」** ⇒
那种顺序下 `latest` 与 `earliest` **没有任何可观察差别**（消息都产生在消费者 join 之后）
⇒ 绿测试是**假绿**。
⇒ 只有「**先灌消息、再起消费者**」（全新 group、无已提交位点）才暴露它。

### 修法与守门

1. 六个 listener 全部改成**点分**的 `auto.offset.reset`。
2. 新增 `TaskKafkaOffsetResetContractTest`（5 条）：
   - **阳性对照**：用 Kafka 自己的 `ConsumerConfig` 证明「连字符形式确实不被采纳」
     （加了连字符键 vs 完全不给这个键，生效值**完全一样**）；
   - 六个 listener 的精确断言；
   - ⭐ **通用断言**：所有 per-listener property 的键名**都必须是 `ConsumerConfig.configNames()` 认识的合法名**
     ⇒ 以后写错任何键名都会被立刻抓住。
   - ✅ **变异测试已做**（改回连字符 ⇒ 2 条变红）⇒ 非假绿。
3. ⭐ **抓它的方法（可复用）**：**把「先灌消息、再起消费者」做成一条对拍**。
   凡是要验「初始位点 / 首次消费 / 无位点回落」这类语义，
   **必须让消息早于消费者存在**，否则测的是「消费新消息」而已。

### 元教训

> **「配置意图」与「配置生效」是两件事**，而 Java 生态里这条缝特别大：
> yaml 那条走 Boot 松散绑定（**认连字符**），
> `@KafkaListener(properties=…)` 那条不走（**只认点分**）。
> ⇒ 同一个键名，**两种写法，一个生效一个静默失效**。
> ⇒ 写「与 Go 一致」之前，先问一句：**这个值到 Kafka 手里的时候还是不是它？**
> ⇒ 而且**静默失效不报错**（Kafka 只打一条 info）⇒ 绿测试天然测不出来。

---

## PIT-TASK-19 · 【仍生效】 探针自己会撒谎：**同一个实验连翻三次车**，前两次的「结论」全是假的

> **旧号**：`PITFALL-TASK-RPC-19` ⇒ **新号**：`PIT-TASK-19`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「探针的观测值 ≠ 被观测对象的状态」是**方法论** ⇒ 永远可能再踩 ⇒ 但本轮**不跑 Kafka topic 实验** ⇒ 不影响本轮产出
> **证据**：本文件正文的三次翻车记录（v1 producer 输出被 `>/dev/null 2>&1` 吞掉 / v2 五个 topic 全是 compact / v3 compacted topic 拒收无 key 消息）

### 现象

要回答「Kafka 的 `cleanup.policy=compact` 会不会按 `retention.ms` 删消息」，
写了个探针，**第一次跑出来的结论是「会删」** ⇒ 于是差点据此给 `task:goka:count:*` 加 TTL。

### 三次翻车

| 版本 | 观测 | 我以为的结论 | 真相 |
|---|---|---|---|
| v1 | `compact`=0 条、`delete`=0 条、`plain`=1 条 | 「compact 也按时间删」 | ⭐ **producer 写失败被 `>/dev/null 2>&1` 吞了**。compact/delete/plain 是**按顺序**写的 ⇒ broker 刚重建还没 ready ⇒ **先写的两个失败、最后那个成功** ⇒ 「0 条」是「没写进去」，不是「被删了」 |
| v2 | 全部 0 条 | 「全被删了」 | 五个 topic **全是 compact** ⇒ 全部写失败 ⇒ 全 0 |
| v3 | 全部 0 条 + 报错 | 「探针坏了」 | ⭐ **Kafka 拒绝 compacted topic 上没有 key 的消息**：`InvalidRecordException: Compacted topic cannot accept message without key` ⇒ 我用 `echo` 写的消息**没有 key** ⇒ broker 直接拒收 |

### 真正的原因（v4 才跑对）

| topic | `cleanup.policy` | `retention.ms` | `segment.ms` | 读回 | 含义 |
|---|---|---|---|---|---|
| `y_short` | **compact** | 1000 | 1000 | **1 条（存活）** | ⭐ compact 单独使用**不按时间删** |
| `z_delete` | delete | 1000 | 1000 | **0 条（被删）** | ✅ **阳性对照**：同参数下 delete 确实被删 ⇒ 机制有效、探针有区分力 |
| `w_default` | delete | 默认 | 默认 | 1 条 | ✅ 阳性对照 |
| `x_infinite` | compact | -1 | 1000 | 1 条 | ✅ |
| `v_noseg` | compact | 1000 | **默认（不滚动）** | 1 条 | ✅ **segment 粒度对照**：证明 `y_short` 存活不是因为 segment 没滚动 |

⇒ **结论：compact 单独使用时 Kafka 不启用按时间删除** ⇒ Go 的 compacted table 的 key 不按时间过期。

### 教训（这才是可复用的部分）

1. ⭐ **每一步的产出都要验**：写完消息立刻 `kafka-get-offsets.sh` 确认 `offset > 0`，
   **写不进去就重试，重试还不行就直接判「本次实验作废」并退出** ——
   **绝不能把「没写进去」当成「被删了」**。
2. ⭐ **不要吞 producer 的输出**。`>/dev/null 2>&1` 会把「写失败」伪装成「数据消失」。
3. ⭐ **阳性对照是硬要求**：必须有「同参数、不同 `cleanup.policy`」的那一条，
   才能证明「探针分得出被删与没被删」。
4. ⭐ **Kafka 的 compacted topic 强制要求消息带 key** —— 这条解释了
   **为什么 Go 的 goka 一直带 `ctx.Key()` emit**（`initGoKa.go:81`/`:106`）。
5. ⚠️ **Kafka 的按时间删除是按 segment 粒度的** ⇒ 测它必须让 segment 先滚动
   （`segment.ms` 调小），否则单条消息在活跃 segment 里永远不会被删。

> 📌 **这条与本仓已有的 PITFALL-04（Python urllib 自动补 Content-Type）是同一类**：
> **探针的观测值不等于被观测对象的状态** —— 中间隔着探针自己的行为。

---

## PIT-TASK-20 · 【已闭环】 compose 的**项目名从目录名推导** ⇒ 同目录的两个 compose 文件撞成同一个 project ⇒ `up -d` 会**删掉别的域的容器**

> **旧号**：`PITFALL-TASK-RPC-20` ⇒ **新号**：`PIT-TASK-20`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— compose 文件已显式声明独立项目名 ⇒ 「同目录两个 compose 撞成同一个 project」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/difftest/docker-compose.task-kafka.yml:39` `name: taskprobe` ＋ `:36-38` 注释（「不是装饰 · 是项目名」） ＋ `:44`/`:63` 容器名 `taskprobe-postgres` / `taskprobe-redis`

### 现象

在 `tools/difftest/` 下执行
`docker compose -f docker-compose.task-kafka.yml up -d`，
输出里出现了：

```
Container difftest-postgres Recreate
Container difftest-redis Recreate
```

⇒ 而 `difftest-postgres` / `difftest-redis` **不是我的** —— 它们是
**user 域**的基建（同目录的 `tools/difftest/docker-compose.yml`）。
⇒ 执行完后那两个容器**消失了**。

### 根因

**docker compose 的项目名默认从 compose 文件所在目录名推导**。
本仓 `tools/difftest/` 下有**两个** compose 文件：

| 文件 | 用途 | 目录 ⇒ 项目名 | 容器名 |
|---|---|---|---|
| `docker-compose.yml` | **user 域**基建 | `difftest` | `difftest-postgres` / `difftest-redis` |
| `docker-compose.task-kafka.yml` | **task 域**基建 | `difftest` | `taskprobe-postgres` / `taskprobe-redis` |

⇒ **两个文件的 project 名都是 `difftest`** ⇒ compose 把对方的服务当成
「同 project 的同名服务」⇒ **`up -d` = Recreate = 删掉重建**；
`down -v` 更狠 ⇒ **还会把对方的数据卷一起删掉**。

⇒ 而且是**双向**的：user 域的 `run.sh` 跑 `down -v` 同样会把 task 域的容器与卷删掉。

### 修法

在 task 域的 compose 文件里**显式声明项目名**：

```yaml
name: taskprobe          # ← 不是装饰，是「项目名」
```

⇒ 两个 project 彻底分开，两个方向的 `up` / `down -v` / `rm` 都只影响自己的。

### 教训

1. ⭐ **同一个目录里不要放第二个 compose 文件而不改项目名**。
2. ⭐ 判据：**`docker compose ls` 看 project 名** ——
   两个文件同名目录 ⇒ 同 project ⇒ 会互相 Recreate。
3. ⚠️ 脚本里**显式带 `-p <project>`** 是第二道保险
   （本仓 task 域的脚本已统一带 `-p taskprobe`）。
4. ⚠️ **误删后能不能恢复**：user 域的 compose **只有 bind mount、没有命名卷**
   ⇒ 重建容器即重跑 `initdb.sql` ⇒ **可完全恢复**（本次已恢复并验证 healthy）。
   ⚠️ 但**如果对方用了命名卷，`down -v` 就不可逆了** ⇒ 所以要**先查 volume 再动手**。

---

## PIT-TASK-21 · 【仍生效】 bash 里 `$var` 紧跟**全角标点** ⇒ 标点被算进变量名 ⇒ `set -u` 下直接报错

> **旧号**：`PITFALL-TASK-RPC-21` ⇒ **新号**：`PIT-TASK-21`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— bash 把**全角标点**并进变量名是**shell 事实** ⇒ 永远可能再踩 ⇒ 但本轮**不改脚本** ⇒ 不影响本轮台账产出
> **证据**：本文件正文的实测记录（`retention-probe.sh: line 71: off?: unbound variable`）＋ 机械检查正则（`\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7f])`） ＋ 同源条目 `PIT-TASK-15`

### 现象

自己写的探针脚本报：

```
retention-probe.sh: line 71: off?: unbound variable
```

⇒ 那一行是 `echo "  ✓ $t 写入成功（offset=$off，第 $attempt 次）"` ——
**`$off` 后面紧跟一个全角逗号**。

### 根因

bash 的变量名解析会把**非 ASCII 字节**也算进名字 ⇒ `$off，` 被当成一个叫 `off，` 的变量。
⇒ 在 `set -u`（`set -euo pipefail` 的一部分）下 ⇒ **unbound variable 硬失败**。

### 修法

**一律写 `${var}`**：`offset=${off}`。

### 教训

1. ⭐ 这是**本仓已踩过两次的同一个坑**：`tools/difftest/run.sh` 里就有同款
   （上一轮修过 2 处），本轮在一次性探针脚本里**又踩了一次**。
2. ⭐ **中文注释/文案多的脚本里特别容易犯** —— 因为中文标点满天飞。
3. ⭐ 机械检查（本次用的）：

   ```bash
   python3 - <<'PY'
   import re, io, sys
   pat = re.compile(r'\$([A-Za-z_][A-Za-z0-9_]*)(?=[^\x00-\x7f])')
   for p in sys.argv[1:]:
       hits = pat.findall(io.open(p, encoding="utf-8").read())
       print(p, hits or "OK")
   PY
   ```

   ⇒ 本轮把 task 域**全部 7 个脚本**扫了一遍：只有一处命中，且**在注释里**（无害）。
4. ⚠️ `bash -n` **查不出这个错**（语法合法，是运行期语义错）⇒ 只能靠上面那把尺子。

---

## PIT-TASK-22 · 【已闭环】 「先起消费者、后灌消息」的对拍**测不出** `earliest` / `latest` 的差别 ⇒ 全绿是假绿

> **旧号**：`PITFALL-TASK-RPC-22` ⇒ **新号**：`PIT-TASK-22`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「先灌消息、再起消费者」已做成落仓探针 ⇒ 该对拍顺序的缺口已补 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/difftest/run-task-offset-oldest.sh`（阶段 B「先灌消息」→ 阶段 C「再起消费者」· 本轮实测文件存在） ＋ 守门 `TaskKafkaOffsetResetContractTest`（t=5）

### 现象

2026-10-24 之前，本域所有 Kafka 对拍（E2E、畸形消息、HTTP 双侧）**都是**：
**先起两侧栈 → 等消费者认领分区 → 再灌消息**。

⇒ 在这种顺序下，`auto.offset.reset=earliest` 与 `=latest`
**没有任何可观察差别**：消息都产生在消费者 join **之后**，两种起点都能消费到。

⇒ 于是「goka 三处 earliest / kq 三处 latest 与 Go 一致」这个结论，
**在所有既有对拍里都是恒真的** ⇒ **它不是被验证过的，它是被这个顺序掩盖的**。

### 教训

1. ⭐ **要验「初始位点」语义，对拍的顺序必须反过来**：
   **先灌消息 → 再起消费者**（且 consumer group 必须是全新的 ⇒ 无已提交位点）。
2. ⭐ 这类语义（`auto.offset.reset` / `Offsets.Initial` / sarama 的 `OffsetOldest`）
   **只在「无位点」这一条分支上生效** ⇒ 对拍必须精确命中那条分支。
3. ⭐ **「测试全绿」与「这条语义被验证过」是两回事** ——
   顺序不对时，绿测试**测不到**它（AGENTS.md §3.3「跑绿 ≠ 对」的又一实例）。
4. ⇒ 落地：`tools/difftest/run-task-offset-oldest.sh`
   —— 阶段 B「先灌消息」→ 阶段 C「再起消费者」，并配**阴性对照**（kq 必须跳过预灌消息）
   与**阳性对照**（启动后投递的消息必须被消费）⇒ 探针**有区分力**。

---

## PIT-TASK-23 · 【已闭环】 「按段前缀匹配元数据」会让配置键扫描器**形同虚设**

> **旧号**：`PITFALL-TASK-RPC-23` ⇒ **新号**：`PIT-TASK-23`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 元数据匹配已改成**精确名** + 阳性对照 ⇒ 「扫描器形同虚设」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskConfigKeyBindingContractTest.java:171-178`（阳性对照 · 框架元数据必须真的被读到）＋ `:195-208`（阳性对照 · 扫描器必须能识别「无人认领的键」） —— 本轮 surefire 实测 **t=5 f=0 e=0 s=0**

### 现象

写「yaml 键是否有消费方」的扫描器时，第一版把框架元数据（`spring-configuration-metadata.json`）
按**分段前缀**匹配：只要 `spring`、`spring.kafka`、`spring.kafka.consumer` 里有任意一段命中元数据，
就认为该键**任意深度**的后代都有消费方。

⇒ 结果：`spring.kafka.consumer.this-key-does-not-exist` 也被判成「有消费方」
⇒ **扫描器抓不到任何孤儿键**，测试形同虚设。

### 根因

**Boot 4 的元数据只列叶子属性名**，父节点**根本不存在**：

```
'spring'                      in metadata: False
'spring.kafka'                in metadata: False
'spring.kafka.consumer'       in metadata: False
'spring.kafka.consumer.auto-offset-reset'  in metadata: True
```

⇒ 「父节点在元数据里」这件事**不成立** ⇒ 按段前缀匹配等于「无条件放行」。

### 教训

1. ⭐ 元数据匹配必须**精确名**；真正「任意子键」的只有 **map 型属性**，
   且那些在元数据里也只列**父节点**（如 `logging.level`、`management.metrics.tags`）
   ⇒ 这类必须**显式登记成 map 型前缀**，不能靠猜。
2. ⭐ **扫描器自己必须被阳性对照验证**：造一个**故意不存在**的键，断言它被判为孤儿。
   没有这一步，扫描器可能「什么都没扫到」而测试全绿。
3. ⇒ 落地：`TaskConfigKeyBindingContractTest` 的断言 1（阳性对照：元数据真被读到）
   与断言 2（阳性对照：扫描器能识别孤儿键）。

---

## PIT-TASK-24 · 【已闭环】 用 `binder.bind(prefix + "." + subKey, Target.class)` 判「绑得上」**必然误判**

> **旧号**：`PITFALL-TASK-RPC-24` ⇒ **新号**：`PIT-TASK-24`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 判定已改用 `BoundPropertiesTrackingBindHandler`（记录**真正被绑定的属性名**）⇒ 「必然误判」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`TaskConfigKeyBindingContractTest.java:195-208`（阳性对照先证明判定逻辑本身是对的，再让它去判别的键） ＋ `:274-296`（自有前缀的每个键都必须在 `@ConfigurationProperties` 类上找得到）

### 现象

想验证「yaml 里这个键能不能绑到某个属性上」，第一版写成：

```java
binder.bind(prefix + "." + subKey, Target.class).isBound()
```

⇒ 对 `task.definitions.article.done-ttl` 这种**嵌套**属性，**永远返回 false** ——
即便 `TaskSinkProperties.Article` 里**确实有** `doneTtl` 字段。

⇒ 第一版测试直接报「`task.definitions.article.done-ttl` 绑不到 TaskSinkProperties」，
**看起来像配置有缺陷，实际是判定逻辑错了**。

### 根因

`Target.class` 是**根类**；`prefix + "." + subKey` 要求 Target 自身有**扁平的**属性名。
而本域的属性是嵌套的（`TaskSinkProperties` → `like` / `article` → `initTtl`）
⇒ 必须**先绑整个前缀**，再按嵌套结构走。

### 教训

1. ⭐ 判「某个 yaml 键是否被绑上」的正确姿势：
   `binder.bind(prefix, Bindable.of(Target), new BoundPropertiesTrackingBindHandler(names::add))`
   ⇒ handler 会记录**真正被绑定的属性名**，再拿 yaml 的键去比对
   （比对时按 relaxed binding 口径归一化：去 `-`/`_` + 小写）。
2. ⭐ Boot 4 的 `Binder.bind` **不接受裸 `Class`**，要 `Bindable.of(target)`。
3. ⭐ **`BoundPropertiesTrackingBindHandler` 的消费者是 `ConfigurationProperty`**
   （`getName()` 返回 `ConfigurationPropertyName`，**不是 String**）⇒ 必须 `.toString()`。
4. ⭐ **「判定逻辑报错」与「被测对象有缺陷」要分开** —— 先证明判定逻辑本身是对的
   （用一个**已知正确**的键做阳性对照），再让它去判别的键。
   本例若没有阳性对照，就会把「测试写错」当成「配置有缺陷」⇒ **修错方向**。

---

## PIT-TASK-25 · 【已闭环】 「白名单」必须带**反向纪律**，否则会变成逃生舱

> **旧号**：`PITFALL-TASK-RPC-25` ⇒ **新号**：`PIT-TASK-25`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 白名单纪律已落成断言 + 变异验证 ⇒ 「白名单退化成逃生舱」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`TaskConfigKeyBindingContractTest.java:246-264`（白名单纪律 · 每条登记的前缀都必须真的盖住至少一个孤儿键 ＋ 原因不得为空）

### 现象

配置键扫描器必然需要一张「已知孤儿键」白名单（否则框架特有的键会一直误报）。

⚠️ **只登记不校验**的话，白名单会退化成「什么都往里塞 ⇒ 测试永远绿」的逃生舱。

### 教训

1. ⭐ 白名单每一条都必须**真的盖住至少一个当前的孤儿键**
   ⇒ 孤儿键消失了（被修好了）就必须**删掉这条登记**，否则登记会变成永久性的例外。
2. ⭐ 每条登记**必须写明原因与出处**（空原因 = 没查过）。
3. ⭐ 新增 yaml 键若无人认领 ⇒ **必须显式登记才能过**
   ⇒ 「静默放过」正是把「配置面看起来在管事、实际没在管事」重新引进来（AGENTS.md §3.3.1）。
4. ⇒ 落地：`TaskConfigKeyBindingContractTest` 的断言 4（白名单纪律）。
   ✅ 变异测试「把 `mybatis-plus` 段移回顶层」⇒ 该条登记立刻失效 ⇒ **测试变红**
   ⇒ 证明白名单纪律**真的在生效**，不是摆设。

---

## PIT-TASK-26 · 【仍生效】 台账的「已修」断言**过期时不会报错** ⇒ 必须能机械复核

> **旧号**：`PITFALL-TASK-RPC-26` ⇒ **新号**：`PIT-TASK-26`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— **本轮（台账对齐）就是这条的直接应用场景** —— 本 lane 正在把各域台账的「已修 / 未修」断言逐条对齐到实测状态 ⇒ ⇒ 本条**影响本轮**；但它**不要求本轮改产品代码**，只要求每条断言都带「核对日期 + 证据指针」⇒ 本档每条已按此落地
> **证据**：机械化复核器 `tools/check-ledger-selfcheck.py`（第 2 层「已修断言」/ 第 3 层「数字断言」/ 第 5·6 层「覆盖缺口」） ＋ 本档每条的「核对日期 2026-10-29 · 证据」三字段即该纪律的落地形态

### 现象

2026-10-24 做「台账 vs 实际」复核，发现 task 域台账的**内容**基本可信
（114 条 Go 侧 `file:line` 出处**全部不悬空**，4 条核心断言人工抽验内容对得上），
**但元信息过期了 4 处**：

| 台账写的 | 实际 |
|---|---|
| 「共 **24 个 commit**」 | **31** |
| commit 栏写「（本轮）」「（本次）」**占位符** | 应为真实哈希（第二轮起就已改用哈希 ⇒ **本轮反而回退**） |
| 引用「§三·第四轮」 | 该节在 **rpc.md §四·九**，`_README` 里**没有 §三** |
| 4 个测试类**在册但从未被台账点名** | `TaskApiMvcPipelineTest` / `TaskApiWiringTest` / `GetTaskReqParsingTest` / `TaskServiceGrpcImplWireTest` |

⚠️ **这四处都不会让任何测试变红** —— 代码是好的、测试是绿的，只有**台账**在骗人。

### 教训

1. ⭐ AGENTS.md §6 的「**任何『已修 / 未修』断言都必须带核对日期，否则按 UNKNOWN 处理**」
   只是一条**要求**，没有**机制** ⇒ 必须把复核**机械化**：
   `tools/check-ledger-selfcheck.py`（五层，见 rpc.md §四·十）。
2. ⭐ 复核器**自己也要被变异测试验证**，否则它可能「什么都没查出来」而全绿。
   （本轮三种变异全部精确命中：修复被回退 / 台账数字造假 / Go 侧行号越界。）
3. ⭐ ⚠️ **禁模式检查必须剥离注释** —— `auto-offset-reset` 在源码里**只应作为反面教材出现在注释里**
   （说明「连字符会被 Kafka 忽略」），不剥离就会误判成「修复被回退」。
4. ⭐ ⚠️ **第三方库要按真源 `go.mod` 的精确版本核对** ——
   本机 module cache 里当时只有 **go-zero v1.10.3**，而真源用的是 **v1.10.2**
   ⇒ 用错版本核对会得出错误结论。
5. ⭐ ⚠️ **shell 里的 `&` 会拆路径** —— `Sea-RideTheWind_golang` 的 `&` 不加引号会被 bash 拆成两段
   ⇒ 脚本参数变空 ⇒ 报出「107 条全部找不到」的**假警报**。
   ⇒ 写这类脚本时，**先确认参数真的传进去了**再解读结果。
6. ⭐ 「**台账可信**」与「**台账的元信息可信**」是两件事 ——
   本轮前者成立、后者不成立 ⇒ 复核要**分层**做，不能因为「内容没问题」就放过元信息。
7. ⭐ ⚠️ **复核器自己也会误报** —— 本轮最后一步：脚本用「共 N 个 commit」做正则，
   结果**匹配到了台账正文里引用的旧数字**（那行正是在说「标题的 24 已过期，实际 31」）
   ⇒ 报了个假的「声明 24 / 实测 31」。
   ⇒ 教训：**正则要锚定位置**（只认章节标题行 `^#`），否则会把「**引用**旧值」当成「**声明**旧值」。
   ⇒ 同理，任何「扫描台账找过期断言」的脚本都可能**把自己的说明文字当成被检对象**。

---

## PIT-TASK-27 · 【仍生效】 台账里「**写死计数**」必然滞后 ⇒ 改查**更强的不变式**

> **旧号**：`PITFALL-TASK-RPC-27` ⇒ **新号**：`PIT-TASK-27`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— **本轮 task 域的测试条数就是「写死计数」** —— 实测前 `docs/DRIFT/task/rpc.md:29` 写 134、`:1633` 写 137、`docs/DRIFT/task/vs-common.md:1433` 写 134、`:1525` 写 102、`docs/DRIFT/task/api.md:30` 写 135，**四处互相矛盾** ⇒ ⇒ 本条**直接影响本轮**；本轮已把四处收敛到**实测值**并标注口径与日期（判据从「写死数字」升级为「实测 + 口径标注」）
> **证据**：本轮实测：`mvn -o -B -DargLine=-Xmx2g -pl service/task/task-api,service/task/task-rpc test` ⇒ **BUILD SUCCESS** · task-rpc **137** / task-api **135**（surefire XML `testsuite` 求和，与 maven 模块汇总行逐字相等）

### 现象

复核器第 3 层原本是「比对台账标题里写死的 commit 数 vs `git rev-list --count` 实测值」。

⚠️ 实测立刻暴露一个**自指问题**：台账写 31 ⇒ 提交「改台账」这个 commit 之后实测变成 **32**
⇒ 台账**又过期了** ⇒ 于是「改台账」这个提交永远让检查变红 ⇒ **变成噪声 ⇒ 没人看**。

### 教训

1. ⭐ **写死的计数类断言不要放在会被自己修改的台账里** ——
   改为「**指向 git 本身**」（`git rev-list --count 8422116..HEAD`），或干脆不写数字。
2. ⭐ 换成**更强的不变式**：**分支上的每个 commit 都必须在台账的 commit 清单里出现过**
   ⇒ 漏登记会立刻被抓到，且**不自指**（改台账的 commit 本身登记完就闭合了）。
3. ⭐ ⚠️ 但「允许 1 个未登记」要**限定是 HEAD** ——
   否则真漏登记**恰好 1 个**时会被放过。⇒ 判定必须是「**未登记的那 1 个 == HEAD**」才算占位。

---

## PIT-TASK-28 · 【仍生效】 用纯文本找「台账登记了什么」会**把正文引用当成登记**

> **旧号**：`PITFALL-TASK-RPC-28` ⇒ **新号**：`PIT-TASK-28`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「用纯文本找登记会把正文引用当登记」是**扫描脚本的方法论缺陷** ⇒ 本轮**不动 `tools/check-ledger-selfcheck.py`**（工具不在本 lane 写权内）⇒ 不影响本轮台账产出
> **证据**：`tools/check-ledger-selfcheck.py:669-671`（注释已把该坑写死：占位符既不算「已登记」也不算「漏登记」） ＋ `:783-790`（判据已按「只认表格行」锚定形态）

### 现象

实现「每个 commit 都已登记」时，第一版是**全文搜哈希**（`re.findall(r'`([0-9a-f]{7,40})`', readme)`）。

⚠️ 实测：把 `b0350ea` 的**表格行**抹掉，检查**居然还是绿的** ——
因为 `b0350ea` 这个哈希**在正文的说明文字里也出现过**
（「由复核器第 3 层的『commit 覆盖』不变式抓出」那一段）。
⇒ **正文引用**被当成了**清单登记**。

### 教训

1. ⭐ 判定「某项**是否被登记**」必须**锚定登记的固定形态**，
   不能靠「字符串出现过没有」：
   本例的登记形态是**表格行** ⇒ 只认 `^\|\s*`([0-9a-f]{7,40})``。
2. ⭐ ⚠️ 这与 PITFALL-TASK-RPC-27 是**同一类错误的两个面**：
   **「写死计数」会自指滞后**（PITFALL-27），
   **「文本匹配找登记」会把引用当登记**（本条）
   ⇒ 共同教训：**扫描台账的脚本必须区分「声明」与「引用」**。
   ⇒ 脚本自己也可能踩坑：本轮这两条都是**先写脚本、跑变异测试才发现**的
   ⇒ **扫描类脚本必须配变异测试**（否则「全绿」只说明它没报错）。

---

## PIT-TASK-29 · 【仍生效】 「允许 ≤1 个例外」的判定会**漏过**真正的漏登记

> **旧号**：`PITFALL-TASK-RPC-29` ⇒ **新号**：`PIT-TASK-29`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 「允许 ≤1 个例外」的判定形态仍可能漏过真正的漏登记 ⇒ 但本轮**不动该工具**⇒ 不影响本轮台账产出
> **证据**：`tools/check-ledger-selfcheck.py:788`（「占位符行**不在 commit 清单的末尾** ⇒ 它不是『本轮』那一行」）—— 判据已限定到具体对象（末尾占位行）而非计数

### 现象

「commit 覆盖」不变式最初写成：**允许 1 个未登记**（因为本轮「（本轮）」占位在提交那一刻才有哈希），
且判定条件是 `len(missing) > 1` 才报错。

⚠️ 实测立刻暴露：**连续快速提交**时，一次补登只解决一个 ——
前一个还没登记就又提交了新的 ⇒ `missing` 变成 2 个 ⇒ **反而报错了**；
而当 `missing` 恰好是 1 个**且不是 HEAD** 时（真正的漏登记）⇒ **被放过**。

### 教训

1. ⭐ 例外要**限定到具体对象**，不能只数个数：
   **只允许 HEAD 那一个未登记**，其余一律是漏登记的历史 commit ⇒ 必须报出来。
2. ⭐ ⚠️ **「允许 N 个例外」的写法要特别小心** —— 它把「例外」和「漏报」混成了同一个计数。
   正确形态是「**白名单式**：只放过某个特定对象」。
3. ⭐ 这与 PITFALL-TASK-RPC-27/28 是同一条主线：
   **写死计数会自指滞后**、**纯文本匹配会把引用当声明**、**数例外会漏过真正的漏报**
   ⇒ 共同教训：判定要**锚定到具体对象与具体形态**，不能靠计数。

---

## PIT-TASK-30 · 【已闭环】 「覆盖缺口」只查**测试类**不够 —— **生产类**里藏着启动阻塞项

> **旧号**：`PITFALL-TASK-RPC-30` ⇒ **新号**：`PIT-TASK-30`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 复核器已扩到「测试类 + 生产类」两层 ⇒ 「只查测试类」的覆盖缺口已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/check-ledger-selfcheck.py:1676` `layer5_unmentioned`（测试类）＋ `:2261` `layer6_unmentioned_production`（生产类） ＋ `:1698`（「不只查 class」· `interface` / `enum` / `@interface` 同样算生产类型）

### 现象

2026-10-24 第一轮「台账 vs 实际」复核加了第 5 层「仓库里存在但台账从未点名的**测试类**」，
查出 4 个测试类漏登记 ⇒ 补完就以为覆盖齐了。

⚠️ 13 小时后（19:25）第二轮复核加第 6 层查**生产类** ⇒ **12 个生产类从未被台账点名**，
其中 **4 个是「缺了就起不来」的启动阻塞项**：

| 类 | 缺了会怎样 |
|---|---|
| `NacosRegistration` | Bean 名撞名 ⇒ 上下文起不来 |
| `Jackson2ObjectMapperConfiguration` | Boot 4 给的是 Jackson 3 ⇒ `NoSuchBeanDefinitionException` ⇒ 组件起不来 |
| `MybatisPlusOrderingBridgeAutoConfiguration` | MP 的 `@AutoConfigureAfter` 指向 Boot 3 包名 ⇒ 静默悬空 ⇒ 整个自动配置不生效 |
| `TaskKafkaTopics` | `@ConfigurationProperties` 宿主 ⇒ 键名对不上就静默丢弃；且含**跨域契约**的 topic 名 |

### 教训

1. ⭐ 「**台账 vs 实际**」的覆盖缺口要查**两类对象**：**测试类**（验契约）**+ 生产类**（验风险）。
   只查测试类 ⇒ **生产代码里的启动阻塞项一个都进不了台账** ⇒ 以后重构时看不到那条风险。
2. ⭐ ⚠️ **生产类比测试类更值得登记** —— 测试类挂了会红；
   而上面 4 个生产类缺了，**在纯 Mockito 单测下全绿**（AGENTS.md §3.3 同款形态）。
3. ⭐ 第 6 层的判定要**认两种登记形态**（纯类名 / 带路径）⇒ 否则自己写的表会被判成「未点名」。
4. ⭐ ⇒ 呼应 PITFALL-TASK-RPC-26：**台账的「已修 / 未修」断言必须能被机械复核**，
   而「机械复核」要覆盖**内容**（类名 / 路径 / 行号 / 计数），
   **不只是「有没有说过这句话」**。

---

## PIT-TASK-31 · 【已闭环】 「集合覆盖」不等于「一一对应」—— **占位符**与**重复登记**会同时逃过「每个 commit 都登记过」这条不变式

> **旧号**：`PITFALL-TASK-RPC-31` ⇒ **新号**：`PIT-TASK-31`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 判据已从「集合覆盖」升级为「一一对应」（占位符残留 / 同一个 commit 多行 / 行数守恒）⇒ 触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/check-ledger-selfcheck.py:770-792`（① 占位符残留（只允许 1 个且必须在末尾）② 同一个 commit 被写成多行） ＋ `:963`（三档判据在**域无关**分支下也跑）

> **核对日期**：2026-10-24　**基线**：`8422116`

### 现象

台账的 commit 清单有一条不变式：「**分支上的每个 commit 都必须在台账里出现过**」，
复核器第 3 层就是查它。**它连续三轮都是绿的** —— 但同一张表里其实有 4 类结构性缺陷：

| 缺陷 | 实测 |
|---|---|
| **8 行 commit 栏是占位符「（本轮）」** | 第五轮 2 行 + 第六轮 4 行 + 第五轮之二 2 行 |
| **同一个 commit 被写成多行** | `c73d9cf` **3 行**（散落在两张表里）/ `c52cbea` **2 行** / `c886406` **4 行** |
| **2 个 commit 完全没登记** | `79d4aac` / `c886406` |
| 清单整体 | **50 行 vs 实际 44 个 commit** ⇒ **虚增 6 行** |

⇒ 读者打开台账，会把 **3 个 commit 误读成 9 个**，且**找不到** `79d4aac`。

### 为什么集合覆盖抓不到

| 缺陷 | 为什么旧判据看不见 |
|---|---|
| 占位符行 | 它**不是哈希** ⇒ 不进「已登记」集合；它**也不是 commit** ⇒ 不进「漏登记」集合 ⇒ **两边都不沾** ⇒ 完全不可见 |
| 同一个 commit 多行 | 哈希**在**表里 ⇒ 「已登记」判定通过 ⇒ **行数是 1 还是 3 不影响判定** |
| 行数 ≠ commit 数 | 集合比较**没有基数** ⇒ 50 行对 44 与 44 行对 44 **判出来一样** |

### 教训

1. ⭐ **「每个 X 都出现过」这类不变式只保证「不漏」，不保证「不多」** ——
   「多写」（同一个 X 写多行）、「变形」（写成占位符而非标识符）**都在判定的盲区里**。
   ⇒ 判据要从「**集合覆盖**」升级为「**一一对应**」：**基数 + 形态 + 位置**三样都要查。
2. ⭐ ⚠️ **占位符是最隐蔽的一种「未登记」** —— 它看起来像一行登记（有内容、有格式），
   但**没有标识符** ⇒ 对「按标识符查」的判据来说，它**既不算登记也不算漏登记** ⇒ **静默通过**。
   ⇒ 凡是用「标识符」做判据的机制，**必须同时给占位符一条单独的判据**。
3. ⭐ ⭐ **「同一个 X 写成多行」的危害不是台账脏，是**它让读者对**整张清单**的计数失去信任** ——
   一旦知道「一行不一定是一个 commit」，整张表的所有数字都要重新数。
4. ⭐ ⇒ 呼应 PITFALL-TASK-RPC-26/30：**台账的断言必须能被机械复核**；
   而「机械复核」要覆盖**内容**（类名 / 路径 / 行号 / 计数），
   **还得覆盖「形态」**（这一行到底登记了几个东西）。

---

## PIT-TASK-32 · 【已闭环】 「同一个坑在同一处复发三次」= **靠人记不住**的证据 ⇒ 判据必须机械化，且**机械化判据自己要有反向证据**

> **旧号**：`PITFALL-TASK-RPC-32` ⇒ **新号**：`PIT-TASK-32`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 机械化判据已进仓且自带反向证据（自检 + 变异测试）⇒ 「靠人记不住」的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/check-ledger-selfcheck.py:1723` `layer0_selfcheck`（阳性对照）＋ `:2059+` `--mutation-test` 段（变异样本：引用一个**仓内不存在的**旧号 `PITFALL-TASK-RPC-NN` / 把 `-32` 改成 `-33` ⇒ 判据须精确命中）<br>⚠️ **本行的写法坑（2026-10-29 对齐时当场踩到 · 即 `PIT-TASK-28` 本人）**：证据行里**若写出一个字面编号**（哪怕只是举例说「工具会构造一个不存在的编号」），复核器的第 3 层「编号连续性」判据是**全文正则扫** ⇒ ⇒ **它会把那个举例当成本档已定义的编号** ⇒ ⇒ **本档里凡提到旧号，一律只写本档真实存在的那 34 个**，不写假号 ⇒ **证据行用 `NN` 占位**。

> **核对日期**：2026-10-24　**基线**：`8422116`

### 现象

AGENTS.md 早就记过一个坑：**台账的 commit 清单「写成 4 行却只对应 1 个 commit」⇒ 会误导读者**。

而本轮查出它在**同一张表**上**复发了三次**：

| 次数 | 位置 | 形态 |
|---|---|---|
| 第一次 | 第四轮 | 写成 4 行 / 只对应 1 个 commit（当时已修） |
| 第二次 | 第五轮 | `c73d9cf` 写成 3 行 + `c52cbea` 写成 2 行 |
| 第三次 | 第六轮 | `c886406` 写成 4 行（全占位符） |

⇒ 前两次都是**靠人写台账时记得「一个 commit 一行」**，两次都**忘了**。

### 教训

1. ⭐ ⭐ **「同一个坑在同一处复发」不是「粗心」，是「靠自觉的机制必然失效」的证据** ——
   第一次修的是**那次的具体写法**，没有修**那个让人能写错的机制** ⇒ 下次换个 commit 照样复发。
   ⇒ 正确做法：**把机制封死**（本轮 = 复核器查「同一个 commit 多行 / 占位符残留 / 行数守恒」），
   而不是**再写一条纪律**。
2. ⭐ **判据要「可复跑」，不能只是「本轮查过」** —— 本轮把变异测试**进仓**
   （`tools/check-ledger-selfcheck.py --mutation-test`）⇒ 下一任 agent 一条命令就能复跑，
   而不用再手写脚本。
3. ⭐ ⭐ **机械化判据自己也要有反向证据** —— 「复核器说没问题」与「复核器坏了」在输出上**长得一样**。
   ⇒ 必须有 **① 阳性对照**（人为造坏样本，判据要红 —— 第 0 层自检）
   ＋ **② 变异测试**（在**真实**台账上弄坏，判据要精确命中）。
   ⇒ 本轮实测：**自检当场抓到了判据自己的一个 bug**（相邻无空行的两张表被并成一张）
   ⇒ 这就是「判据也要被证伪」的价值：**不是假设它对，是让它有机会错**。
4. ⭐ ⇒ 呼应 PITFALL-TASK-RPC-26/29/31：写死计数会自指滞后、纯文本匹配会把引用当声明、
   数例外会漏过真正的漏报、集合覆盖看不出「多写」⇒ 共同教训：
   **判定要锚定到具体对象与具体形态，且必须带反向证据。**

---

## PIT-TASK-33 · 【已闭环】 「别的域有守门测试」**不等于**「本域有」—— 跨域共有的契约，**每个域都要各自守一次**

> **旧号**：`PITFALL-TASK-RPC-33` ⇒ **新号**：`PIT-TASK-33`
> **原标题标记**（2026-10-23/24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本域已自己守了「gRPC 拦截器注册链」这条跨域共有契约 ⇒ 触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java`（本轮 surefire 实测 **t=2 f=0 e=0 s=0** · 真 gRPC server + 真 PG）

> **核对日期**：2026-10-24　**基线**：`8422116`

### 现象

2026-10-24 三次复核（查「台账是否最新、最全」）时查出：
**task-rpc 的「gRPC 拦截器注册链」这条契约，在本域台账里从未出现过** ——
台账里连 `GrpcServerConfigurer` / `RpcServerInterceptorFactory` 这两个名字都没有。

而它**不是**「表达层」的事，是**行为层**的：

| 侧 | 事实 |
|---|---|
| Go | `service/task/rpc/task.go:28` `DisableNativeRpcTimeout` ＋ `:44` `s.AddUnaryInterceptors(NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))` —— **显式注册** |
| RAG | `@Import(RpcServerInterceptorFactory.class)` ＋ `@Bean GrpcServerConfigurer` —— **两件套齐** ⇒ 行为其实是对齐的 |
| 差距 | **台账零记录 ＋ 本域零守门测试** ⇒ 以后谁动这一行，**没有任何东西会响** |

⚠️ 而 `follow` / `message` / `users-user` **三域早就有**同类守门测试
（`FollowGrpcInterceptorWiringTest` / `MessageGrpcInterceptorWiringTest` / `GrpcInterceptorWiringTest`）——
⇒ **人容易以为「这事有人在管」**，其实**只有那三个域在管**。

### 教训

1. ⭐ ⭐ **「别的域有」不能推出「本域有」** ——
   跨域共有的契约（拦截器注册、账本隔离、错误码回落…）**每接一个域就要重新守一次**：
   别的域的测试**不会**因为本域也用了同一个类就自动覆盖到本域。
2. ⭐ ⭐ **共享实现类 ⇒ 共享风险，但风险不共享** ——
   `RpcServerInterceptorFactory` 在 `common` 域，**每个 rpc 模块各自 `@Import` 各自装配** ⇒
   **漏了 `@Bean GrpcServerConfigurer` 的域，是那个域自己的洞** ⇒ common 域修好了也不代表各域都接上了。
3. ⭐ **台账的覆盖清单要按「**契约**」列，不能按「**类名**」列** ——
   本轮台账的覆盖清单是「16 个测试类 + 40 个生产类逐个点名」，
   **却整条漏掉了「拦截器注册链」** ⇒ 因为它是**横跨 3 个类**的契约，
   **没有一个类的名字能代表它** ⇒ ⇒ 覆盖清单要能按**契约**反查。
4. ⭐ ⇒ 呼应 PITFALL-TASK-RPC-30（「只查测试类不够，生产类里藏着启动阻塞项」）：
   **测试类与生产类都列全了，仍可能漏掉「由多个类共同构成的契约」** ⇒ 第三种覆盖维度：**契约**。
5. ⭐ ⇒ 而这类契约一旦漏掉，**发现它的往往不是静态对照，而是「别处有、本处没有」这种横向对照**
   ⇒ 本轮能发现它，靠的是 `service/common` 已被**另一个域**审过并改了
   （common 域的 H2 结论直接点名了 `service/task/rpc/task.go`）⇒
   **跨域审计的产物，会变成别的域的输入** ⇒ 别的域改了共享面，**要回扫一遍依赖它的那几个域**。
