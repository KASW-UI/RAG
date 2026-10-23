<!-- @new 2026-10-23 · task 域踩坑档 · 由 `align/task` 工作树产出 -->

# `task-rpc` 踩坑记录

> 记「**为什么会这样**」，不是记「哪条不一样」。不一致本身在 [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md)。

---

## 🔴 PITFALL-TASK-RPC-01 · `@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」，15 条真 PG 用例静默全跳

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-003`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-003--15-条真-pg-用例静默全跳证据缺失)
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

## 🔴 PITFALL-TASK-RPC-02 · relaxed binding 键名对不上 ⇒ 配置键被静默丢弃

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-002`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-002--taskdefinitions-的四个-ttl-键是死配置)

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

## ⚠️ PITFALL-TASK-RPC-03 · 注释描述的是「代码没实现的契约」

> 对应的 DRIFT 条目：[`DRIFT-TASK-RPC-001`](../../../../DRIFT/task/rpc.md#-drift-task-rpc-001--taskcountstore-内存单例--两个-goka-group-串号--重启归零)

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
| 本域 DRIFT | [`../../DRIFT/task/rpc.md`](../../../../DRIFT/task/rpc.md) · [`api.md`](../../../../DRIFT/task/api.md) |
| 全仓共性踩坑 | [`../../Pitfalls/_cross/testing-pitfalls.md`](../../_cross/testing-pitfalls.md) · [`../../Pitfalls/_cross/framework-mechanism-evidence.md`](../../_cross/framework-mechanism-evidence.md) |

---

## 🔴 PITFALL-TASK-RPC-04 · 探针自己也会撒谎：Python `urllib` 会自动补 `Content-Type`

> 场景：2026-10-23 `task` 域双侧活体对拍（见 [`../../DRIFT/task/api.md`](../../../../DRIFT/task/api.md) §二·五）

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

## ⚠️ PITFALL-TASK-RPC-05 · 对拍「红了 42/43」可能全是 header 格式，别淹没真正的差异

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
