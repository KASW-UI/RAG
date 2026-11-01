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

### ⏳ SD-2026-10-23-08（候选）：响应 `Content-Type` 不带 `charset=utf-8` ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销**

> **唯一别名**：`SD-2026-10-23-08@like`（🔴 **撞号事实**：同一编号在 [`docs/Intentional_Deviations/message/rpc.md`](../message/rpc.md)（CPU fail-open）与 [`docs/Intentional_Deviations/users/admin/api.md`](../users/admin/api.md)（黑名单 401 业务码）指向**三条完全不同**的偏离 ⇒ 跨域撞号 ⇒ 本条以 **`SD-2026-10-23-08@like`** 为准）
> **签字复核（核对日期 2026-10-29）**：**已失效（签字描述的行为已不存在）⇒ 不计入计数** —— 共享层 T-6 已给**全部 10 个 api** 的 JSON 响应补上 `charset=utf-8` ⇒ 「RAG 侧响应 `Content-Type` **不带** charset」这个行为**今天已不存在**。**现在是什么样**：RAG 侧返回 `Content-Type: application/json;charset=utf-8`（**分号后无空格**），Go 侧是 `application/json; charset=utf-8`（**分号后有空格**）⇒ **剩下的只是「分号后有没有空格」**（属**框架限制**，与 `DRIFT-MSGAPI-012` 同款，已在 DRIFT 台账单列）⇒ **本条应从「⏳ 待签字」中剔除**⇒ **索引计数由主线更新**（本 lane 不写共享索引）
> **依据**：`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（常量 `GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）+ `:151`（消费点 `response.getHeaders().set(HttpHeaders.CONTENT_TYPE, GO_JSON_CONTENT_TYPE)`）· 生效链 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2` → `service/like/like-api/pom.xml:63`（共享层 `common` 依赖）· 真 Tomcat 守门 `GoStyleJsonContentTypeCharsetTomcatTest` ⇒ **证据强度：源码行号 + 装配链 + 真 Tomcat 守门在仓**。


| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（台账 §7.2 建议登记，尚未获批）<br>⚠️ **2026-10-29 真实性复核**：**现行态 = ⛔ 已失效·已撤销**（原值保留）—— 共享层 T-6 已给全部 10 个 api 的 JSON 响应补上 `charset=utf-8` ⇒ 「RAG 侧响应 `Content-Type` **不带** charset」这个被签字描述的行为**今天已不存在** ⇒ ⇒ **本条已从「⏳ 待签字」中剔除，不计入签字数** · 核对日期 2026-10-29 |
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

### ⏳ SD-2026-10-23-09（候选）：worker 池饱和时的**裸 503** ｜ **状态（核对日期 2026-10-29）：⏳ 未签字**

> **唯一别名**：`SD-2026-10-23-09@like`（⚠️ **撞号事实**：`SD-2026-10-23-09` 在 [`docs/Intentional_Deviations/common/deadline.md`](../common/deadline.md) 只是**引用本条**（不是另一条偏离）⇒ **不构成跨域撞号**；为唯一定位仍加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立（候选仍待签字）** —— 被描述的行为**今天仍存在**：worker 池饱和时仍抛 `RejectedExecutionException` 并被转成**裸 503**（无业务码、无 envelope） · **现行态 = ⏳ 未签字**（本条是**候选**、用户尚未拍板 ⇒ 不计入已签字计数）
> **依据**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:154`（`static final int MAX_HTTP_WORKERS = 512`）+ `:369`（`catch (RejectedExecutionException saturated)`）+ `:193`（注释「饱和 ⇒ 抛 RejectedExecutionException，由调用点转成**裸 503**」）⇒ **证据强度：源码行号（静态）**。


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

### ⏳ SD-2026-10-24-10（候选）：单次 RPC deadline 的**毫秒粒度**取整 ｜ **状态（核对日期 2026-10-29）：⏳ 未签字**

> **唯一别名**：`SD-2026-10-24-10@like`（无跨域撞号（该编号只出现在本文件）⇒ 为唯一定位仍加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立（候选仍待签字）** —— 被描述的行为**今天仍存在**：`GoStyleRequestDeadline` 的公开 API 仍只暴露**毫秒**粒度，而 Go 的 `context` 是**纳秒** ⇒ 两侧的 deadline 传播精度仍不同 · **现行态 = ⏳ 未签字**（本条是**候选**、用户尚未拍板 ⇒ 不计入已签字计数）
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:136`（类注释：「**ms 粒度**：`remainingMillis()` 是毫秒精度（Go 的 ctx 是纳秒）」）+ `:154`（`GoStyleRequestDeadline.remainingMillis()`）· 共享层 `service/common/.../GoStyleRequestDeadline.java`（公开 API 只有 `isPresent()` / `remainingMillis()`，**只读**）⇒ **证据强度：源码行号（静态）**。


| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（第五轮修复时提出，尚未获批） |
| 原台账编号 | `DRIFT-LIKEAPI-017` 的**精度取舍**部分（该条的**主体漂移已修**，这里只留取整这一处） |
| 组件 | `service/like/like-api`（全部 6 个端点的下游调用） |
| 性质 | **框架限制**（共享组件 `GoStyleRequestDeadline` 的**只读 API（`remainingMillis()`）只有毫秒粒度** —— ⚠️ **不是「整个公开 API 只有毫秒」**：`applyTo(stub)` 是**纳秒**的（`GoStyleRequestDeadline.java:110-120` ⇒ `stub.withDeadlineAfter(remainingNanos, TimeUnit.NANOSECONDS)`）；**真实限制**在**只读那一侧**：`applyTo` **没有「上界」概念** ⇒ **表达不了 `min(上限, 剩余)`**（`LikeRpcClient.java:108-121`）） |

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


### ⏳ SD-2026-10-24-11（候选）：网关层拒绝计数 —— **Java 侧比 Go 侧多暴露一个指标** ｜ **状态（核对日期 2026-10-29）：⏳ 未签字**

> **唯一别名**：`SD-2026-10-24-11@like`（无跨域撞号（该编号只出现在本文件）⇒ 为唯一定位仍加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立（候选仍待签字）** —— 被描述的行为**今天仍存在**：RAG 侧仍多暴露一个 Go 侧没有的 Prometheus 指标 `like_api_reject_total`（且 Prometheus 渲染成 `like_api_reject_total_total`） · **现行态 = ⏳ 未签字**（本条是**候选**、用户尚未拍板 ⇒ 不计入已签字计数）
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/metrics/LikeApiMetrics.java:75`（`Counter.builder("like_api_reject_total")`）+ `:17-20`（注释说明渲染形态）⇒ **证据强度：源码行号（静态）**。


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
| SD-2026-10-23-05@like | `like.proto` 的 proto `package` 由 `pb` 归一为 `like`（见 [`proto.md`](proto.md)） |

## ✅ 已签字（like-rpc 侧，见 [`rpc.md`](rpc.md)）

| 编号 | 内容 | 性质 |
|---|---|---|
| SD-2026-10-23-06@like | gRPC 业务码从 `grpc-status` 迁移到自定义 trailer `rag-like-biz-code` | **框架限制** |
| SD-2026-10-23-07@like | Nacos 注册失败时**不退出进程**（Go 是 `os.Exit(1)`） | **有意偏离** |

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W7

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| SD-2026-10-24-11 | C | P3 | 否 | Go `service/like/api/internal/metrics/metrics.go:20`（`prometheus.Register(ApiRejectCount)`，**在 `InitMetrics()` 内** `:17`）＋ `service/like/api/likecenter.go:28`（`main()` 里**没有** `metrics.InitMetrics()` 调用 ⇒ 全 `service/like/api` 树内该函数**只有定义、无调用点** ⇒ 该 CounterVec **从未注册进 Prometheus 默认 registry** ⇒ Go 的 `:9099/metrics` **看不到** `like_api_reject_total`；⚠️ 对照：follow/hot/message 的 api main 都调了，如 `service/follow/api/followcenter.go:33` ⇒ like 域是**唯一漏调**）。RAG `service/like/like-api/src/main/java/com/rag/like/like/api/metrics/LikeApiMetrics.java:75`（`Counter.builder("like_api_reject_total")`）＋ `:79`（`.register(registry)` ⇒ **声明即注册** ⇒ `/actuator/prometheus` 能看到）⇒ ⇒ **差异仍成立**（Java 侧比 Go 侧**多暴露一个指标**）。**C**：本条在 `docs/Intentional_Deviations/like/api.md` 里仍是 **⏳ 未签字候选**（核对日期 2026-10-29）⇒ **尚未成为决策** ⇒ 按问题定级、**不归 D**；处置 = 阶段二走签字登记（Go 侧是漏调 ⇒ 修回去 = 倒退），差异本身不影响任何业务判定 ⇒ **P3**、不阻断阶段一 | 2026-10-30 |
| `SD-2026-10-23-09` | C（阶段 2 最终工程建设项 · 未签字候选 ⇒ **不得自动归 D**） | P2 | 否 | Go `service/like/api/**` 树内 `maxConns`／`MaxConns`／`Semaphore`／`worker` 命中数 = **0**（2026-10-30 G2 实测 `grep -rn`）⇒ Go 侧每请求一个 goroutine（初始栈 2KB）、**无上限** ⇒ **不存在**「worker 池饱和」这个事件；⚠️ 对照：Go 侧**确实有**「过载 ⇒ 拒绝」这条语义，只是载体不同 —— `service/like/api/likecenter.go` 用 go-zero `rest.WithMiddlewares` 的限流/熔断中间件拒绝，**不是**线程池饱和。RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:154`（`static final int MAX_HTTP_WORKERS = 512`）＋ `:181`（池按 512 建）＋ `:193`（注释「饱和 ⇒ 抛 `RejectedExecutionException`，由调用点转成**裸 503**」）＋ `:369`（`catch (RejectedExecutionException saturated)` ⇒ 容量拒绝路径）⇒ ⇒ **差异仍成立**（RAG 侧存在 Go 侧**结构上不存在**的「worker 池饱和 ⇒ 裸 503（无 body、无 Content-Type、无业务码、无 envelope）」这个可观察面）；⚠️ **但两侧的「过载 ⇒ 拒绝」大方向同向** ⇒ RAG 多出来的是**拒绝的载体与形状**，不是「Go 会接受而 RAG 会拒绝」⇒ ⇒ **不改回去 ⇒ 不属 DRIFT**（§5.3b 判据）⇒ 按 §10 **按问题本身定级**：本条在 `docs/Intentional_Deviations/like/api.md` 里仍是 **⏳ 未签字候选**（核对日期 2026-10-29）⇒ **尚未成为决策** ⇒ 按纪律 5「已签字偏离 ⇒ 是决策不是缺陷」的**反面** ⇒ **不得自动归 D** ⇒ 归 **C**；**P2**（只在「高并发 ＋ 下游卡死」这个窄窗口可观察；触发窗口窄 —— 512 远大于 Tomcat 默认 `max-threads`（200）⇒ 正常流量不该触发；且**不影响任何业务判定**、不涉及数据一致性或安全边界 ⇒ **不命中阶段一 P0 九条** ⇒ **不阻断阶段一**）；**处置 = 阶段二走签字登记**（把它从「候选」升为「决策」）⇒ **本轮不推拍板、不改代码**（§5.3b「登记的**登记**本身就是交付物」⇒ 不把已登记条目打包推给用户逐条拍板） | 2026-10-30 |

## 阶段归属定级附录（2026-11-01 · G2 lane · 观测口形态面 · **D 类 · 不改**）

> 本节由 G2 lane（`align/g2-message-like-task`）追加 · **只追加，不改任何既有结论**。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层的**两个扫描器**都不读它（保留区机制）⇒ **零投影影响**。
> ⚠️ **口径由 hub（g4）判**（2026-11-01）：**不改 + 登记为 D 类残余差异**。

### 观测口形态面登记（**D 类 · 不改** · 部署与可观察性形态）

| 项 | 值 |
|---|---|
| 性质 | **部署与可观察性形态**（不进入业务行为契约）⇒ AGENTS.md §3「差异只允许出现在表达层」⇒ **本来就是表达层** ⇒ **不是行为漂移** |
| 判定 | **D 类 · 不改**（口径由 hub（g4）判 · 2026-11-01） |
| 差异面 | ① **路径**：Go `/metrics`·`/healthz` ↔ RAG `/actuator/prometheus`·`/actuator/health`（Actuator 固定前缀）<br>② **端口**（**仅 api 侧**）：Go api 的 devserver 是**独立端口** ↔ RAG api **无独立 `management.server.port`** ⇒ Actuator 挂**业务端口** ⇒ **端口也不同** |
| rpc 侧 | **端口逐域一致** ⇒ 抓取侧**只改 path** ⇒ **观测能力不缺** ⇒ 路径差异 = Spring Actuator 的**表达层形态** |
| api 侧 | **端口 + path 两样都要改** ⇒ 改它 = 改**部署形态** ⇒ 跨 **8+ 个 api 组件** ⇒ **单域不得擅改**（否则域间不一致）⇒ ⇒ 归**阶段二** |
| 落点（形态面行） | `docs/DRIFT/like/vs-common.md` 文末「阶段归属定级附录（2026-11-01 · G2 收官轮 · 观测口「端口 + 路径」形态面登记）」节（**保留区 ⇒ 零投影影响**） |
| 证据强度 | **E3**（Go 侧 `文件:行号` 逐行 + RAG 侧 yml 定位 + 全仓 `management.server.port` 真配置 **0 处**实测）· **核对日期 2026-11-01** |

**逐组件矩阵**：见 [`docs/DRIFT/like/vs-common.md`](../../DRIFT/like/vs-common.md) 文末
「阶段归属定级附录（2026-11-01 · G2 收官轮 · 观测口「端口 + 路径」形态面登记）」节。
· 核对日期 2026-11-01
