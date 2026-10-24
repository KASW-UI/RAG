<!-- @new 2026-10-23 · like 域 api 层签字偏离 -->

# `like` 域 api 层签字偏离

> 归类：**「有意偏离」+「表述差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

---

## ⏳ 待签字（2026-10-23 修复轮 + 2026-10-24 第五轮提出，**用户尚未确认**）

> ⚠️ **本节四条尚未获得用户签字**，故**不计入**「已签字偏离」总数。
> 保留在此是为了让下一轮复核**不会把它们当成新发现重新提一遍**。
> 处置依据见 [`../../DRIFT/like/api.md`](../../DRIFT/like/api.md) 的 `DRIFT-LIKEAPI-021 / -022`、
> 第五轮章节的「017 精度取舍」，以及**台账审计**章节 §2.1（网关层拒绝计数的暴露面）。

### ⏳ SD-2026-10-23-08（候选）：响应 `Content-Type` 不带 `charset=utf-8`

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（台账 §7.2 建议登记，尚未获批） |
| 原台账编号 | DRIFT-LIKEAPI-021（OBSERVABLE DIFFERENCE，🟢 LOW） |
| 组件 | `service/like/like-api`（6 个端点的成功与业务错误响应） |
| 性质 | **表述差异**（台账已判定语义等价） |

**差异**

| 侧 | `Content-Type` |
|---|---|
| Go | `application/json; charset=utf-8`（分号后**有**空格） |
| RAG | `application/json`（**无** charset 参数） |

**为什么不修**
按 RFC 9110，`application/json` 的 charset 参数**默认即 UTF-8** ⇒ 省略与显式声明 `utf-8`
**语义等价**；且省略后的 OWS 按规范不显著。
⚠️ 强行对齐会变成「与容器（Tomcat）对抗」—— `StringHttpMessageConverter` 的产出格式由容器决定。

**影响面**
仅当客户端做**字节级**的 Content-Type 断言时才会红。
⚠️ 本仓库现有测试用 `contentTypeCompatibleWith` / `MediaType.parse` 比对，**不受影响**。

**若将来出现按原始字符串比对的客户端** ⇒ 需要重新评估（那时它就不再是「表述差异」了）。

---

### ⏳ SD-2026-10-23-09（候选）：worker 池饱和时的**裸 503**

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（台账 §7.2 建议登记，尚未获批） |
| 原台账编号 | DRIFT-LIKEAPI-022（OBSERVABLE DIFFERENCE，🟢 LOW） |
| 组件 | `service/like/like-api`（全部 6 个端点） |
| 性质 | **有意偏离**（RAG 独有的实现能力，Go 侧**不存在对应物**） |

**差异**

| 侧 | 行为 |
|---|---|
| Go | 每请求一个 goroutine（初始栈 2KB）、**无上限** ⇒ **不存在**「worker 池饱和」这个事件 |
| RAG | `HttpMiddleware` worker 池上限 **512**（`MAX_HTTP_WORKERS`）；饱和 ⇒ `RejectedExecutionException` ⇒ **裸 503，无 body、无 Content-Type** |

⚠️ 形状刻意与「超时 503」不同 —— 超时那条**有 body**，这条没有。

**为什么保留**
① 触发窗口窄：512 远大于 Tomcat 默认 `max-threads`（200），正常流量不该触发；
② `HttpMiddleware` 的源码注释已自陈这是「『拿不到 ctx 传播』这件事的兜底，
**不是 Go 语义的一部分**」—— 即它是**为框架限制而生的兜底**，不是对 Go 的误解。

**代价（必须随本条一起读）**
高并发 + 下游卡死时，RAG 会出现 Go 不会出现的裸 503。

---

### ⏳ SD-2026-10-24-10（候选）：单次 RPC deadline 的**毫秒粒度**取整

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（第五轮修复时提出，尚未获批） |
| 原台账编号 | `DRIFT-LIKEAPI-017` 的**精度取舍**部分（该条的**主体漂移已修**，这里只留取整这一处） |
| 组件 | `service/like/like-api`（全部 6 个端点的下游调用） |
| 性质 | **框架限制**（共享组件 `GoStyleRequestDeadline` 的公开 API 只暴露毫秒） |

**差异**

| 侧 | 单次 RPC 的有效 deadline |
|---|---|
| Go | `min(2000ms, 3000ms − 已耗时)`，其中「已耗时」是**纳秒**精度（Go 的 `context` 用纳秒） |
| RAG | `min(2000ms, remainingMillis())` —— `remainingMillis()` 是**毫秒**精度（向下取整） |

⇒ 两侧有效值最多相差 **&lt; 1ms**。

**为什么不修（改成纳秒精度也不难，但没必要）**

要拿到纳秒精度，只能改走 `GoStyleRequestDeadline.applyTo(stub)` 再把
`stub.getCallOptions().getDeadline()` **读回来** —— 即「先套一个 deadline，再读它」，
多一层间接、且依赖 `Deadline.UNKNOWN` 的语义。
⚠️ 而 `GoStyleRequestDeadline` 是**共享组件**（6 个域共用）⇒ 为一个 **&lt;1ms** 的取整去改它，
收益不抵跨域协调成本。

**影响面（必须随本条一起读）**

**不可观察**：两侧在**同一个毫秒窗口**内都以 `DEADLINE_EXCEEDED` 收场
（Go 的 ctx 到期与 RAG 的取整后到期相差 &lt;1ms）⇒
客户端看到的**错误码、状态码、文案完全一致**。
⇒ 唯一能测出差异的方式是**在服务端精确到纳秒地记录 deadline 到达时刻**，
而那已经超出「可观察行为」的范畴。

**若将来出现按纳秒比对的探针** ⇒ 需要重新评估（那时它就不再是「框架限制」了）。

---

---


### ⏳ SD-2026-10-24-11（候选）：网关层拒绝计数 —— **Java 侧比 Go 侧多暴露一个指标**

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（2026-10-24 台账审计时提出，尚未获批） |
| 原台账编号 | **无**（此前台账**完全没有提到** `LikeApiMetrics`）—— 是审计补录时挖出来的 |
| 组件 | `service/like/like-api`（`metrics/LikeApiMetrics`） |
| 性质 | **有意偏离（单向增强）** —— Java 的**指标可见面比 Go 大**，不是缺陷 |

**差异**

| 侧 | `like_api_reject_total` 这个指标 |
|---|---|
| Go | `service/like/api/internal/metrics/metrics.go:20` 的 `prometheus.Register(ApiRejectCount)` **从未被执行** —— `service/like/api/likecenter.go` 的 `main()` **没有调用** `metrics.InitMetrics()` ⇒ 该 CounterVec **从未注册进 Prometheus 默认 registry** ⇒ Go 端 `:9099/metrics` **根本看不到**这个指标（`WithLabelValues(...).Inc()` 仍能自增，只是没被暴露） |
| RAG | Micrometer `Counter.builder("like_api_reject_total").register(registry)` ⇒ **声明即注册** ⇒ `/actuator/prometheus` **能看到** |

**为什么保留（不修）**

① **不改任何业务判定** —— 只是可观测面变大；
② **修回去 = 倒退** —— 把指标藏起来对运维没有任何好处；
③ **Go 那一侧是漏调**（`follow/api` `message/api` `hot/api` `user/api` `admin/api` **都调了** `InitMetrics()`，**唯独 like 域没调**）⇒
**是 Go 侧的缺陷，不是 RAG 侧的偏差**；按「不要擅自修正 Go 的行为」的纪律，RAG 侧**照 Go 的语义实现计数**，但**不照抄 Go 的漏调**。

**⚠️ 边界：两侧其余部分是完全一致的（已逐条核对到消费点）**

| 维度 | 结论 |
|---|---|
| 指标 key | ✅ 一致（Prometheus 两侧都渲染成 `like_api_reject_total_total`） |
| 计数点位 | ✅ **4/4 一致** —— Go 的 `getlikestate` / `likeaction` / `getuserlikelist` / `getusertotallike`；`getlikecount` / `gettargetlikerlist` **两侧都不埋**（RAG 侧 `LikeCountService` / `TargetLikerListService` **刻意不读 uid**） |
| `route` 标签 | ✅ 一致 —— Go 4 处**全部硬编码** `"/like/likeaction"`（含 3 处与实际端点不符的**错误值**），RAG 侧用 `ROUTE_LABEL_GO_HARDCODED` **刻意保留**这个错误 |
| `reason` 标签 | ✅ 一致（`token_invalid` / `token_parse_error`） |

**若将来 Go 侧补上 `InitMetrics()`** ⇒ 两侧暴露面自然一致，本条自动失效 ⇒ 应在 Go 侧修复后**重新评估并关闭本条**。


---

## ✅ 已签字（同域）

| 编号 | 内容 |
|---|---|
| SD-2026-10-23-05 | `like.proto` 的 proto `package` 由 `pb` 归一为 `like`（见 [`proto.md`](proto.md)） |

## ✅ 已签字（like-rpc 侧，见 [`rpc.md`](rpc.md)）

| 编号 | 内容 | 性质 |
|---|---|---|
| SD-2026-10-23-06 | gRPC 业务码从 `grpc-status` 迁移到自定义 trailer `rag-like-biz-code` | **框架限制** |
| SD-2026-10-23-07 | Nacos 注册失败时**不退出进程**（Go 是 `os.Exit(1)`） | **有意偏离** |