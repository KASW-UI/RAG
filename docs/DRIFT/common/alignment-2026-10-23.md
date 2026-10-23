<!-- @new 2026-10-23 22:5x–23:1x · align/common 工作树产出 · 基线 commit 8422116 -->

# `service/common` 组件级重对齐（2026-10-23）

> **产出方**：`align/common` 工作树（`RAG.worktrees/common`），基线 commit **`8422116`**（与 `do-my-own-gh` 同点）。
> **核对时间**：2026-10-23 22:49–23:10（真实执行：`mvn -o -pl service/common test` ×4 轮 + `javap` ×2 + `find`/`grep` 全仓扫描）。
> **本文件回答**：`service/common` 台账标「✅ 审计完成」之后，还剩什么没对齐。
>
> ⚠️ **本文件不改写既有 4 份台账**（`audit.md` / `protection.md` / `http.md` / `deadline.md`），
> 只做**增量**：新发现 + 对既有结论的更正。真源为 Go：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/common/`。

---

## 〇、本轮一句话结论

**台账「✅ 审计完成」的覆盖面不成立。** 该结论来自 2026-10-21 那轮，
而那轮自己写下了一句被后续忽视的话：

> 「本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**」

本轮按 §3.5「common 不是可选项」重做组件级对照，实测结果：

| 范围 | 结论 |
|---|---|
| 5 个可比包（`communityfact` / `logger` / `observability` / `response` / `snowflake`） | 🔴 **3 个 NOT EQUIVALENT**，1 个（`response`）**逐条等价** |
| §3.3 机制接线（`spring/*`） | 🔴 1 条**可证伪的假框架断言**（已修）；2 条常见担忧**已证伪** |
| 跨组件不变量 | 🔴 `communityfact` 消费者两侧不一致（见 §四） |
| 已修 | **3 条 SEMANTIC MISMATCH**（均配回归测试，147 tests / 0 failures） |
| 已记录未修 | **20 条**（含 1 条 `UNRESOLVED`） |

---

## 一、本轮修掉的 3 条（全部带回归测试）

### ✅ DRIFT-COMMON-001 —— gRPC 超时路径的 span 永不结束（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `service/common/observability/rpc.go:68-71`：<br>`case <-timeoutCtx.Done(): err := status.Error(codes.DeadlineExceeded, …); MarkTimeout(ctx, err, …); return nil, err` |
| **RAG 落点** | `RpcInterceptor.java:192`（修前）——超时调度器关的是**原始 `call`**；而打标与 `span.end()` 只发生在 `:204-241` 的 `wrappedCall.close()` 里 |
| **机制** | `wrappedCall` 是 `ForwardingServerCall.SimpleForwardingServerCall`，原始 `call` 是它的**委托目标**。关原始 call ⇒ **完全绕过** span 打标与 `span.end()` |
| **可观察面** | 客户端能看到 `DEADLINE_EXCEEDED` ✅，但**遥测里这次超时完全不存在**：span 永不结束 ⇒ 永不导出 ⇒ 既无 `sea.timeout=true`，也持续占内存 |

**为什么两边 `MarkTimeout` / `markTimeout` 本身没有差异**（差点误判的地方）：
`Observability.markTimeout`（`Observability.java:115-127`）与 Go `MarkTimeout`
（`observability.go:108-129`）**逐条对称，且都不 `span.End()`**。
Go 这样做是对的——它的 span 生命周期归 otel 插桩所有，拦截器只加属性。
**RAG 多做了「自己建 span、自己 end span」**（`:163-166` + `:239`），
却没在自己新增的 timeout 分支上补齐这份职责 ⇒ 典型的**无出处 idiomomatization**（§3.2）。

**修法**：超时调度器改为关闭 `wrappedCall`（经 `AtomicReference` 中转，因 `wrappedCall` 在其后才创建），
从而与业务 `close` **共用同一条打标出口**，并复用 M6 已有的 `spanFinalized` CAS 保证只执行一次。

**证据（变异→红→修复→绿，实测）**：

```
新增 service/common/src/test/java/com/rag/common/observability/RpcInterceptorTimeoutSpanTest.java
  ① 修前：assertThat(spans).isNotEmpty()  →  FAILURE（exporter 返回 0 个 span）
  ② 修后：同一测试 →  PASS
  ③ service/common 全量：tests=147 failures=0 errors=0 skipped=0（原 146）
```

⚠️ 该测试**不是**「断言 Bean 存在」。它装真 OTel SDK + 真 `InMemorySpanExporter`，
驱动真实 `ServerInterceptor`，只把 gRPC 传输层换成桩 ⇒ 被驱动的调度器 / `wrappedCall` / span 代码全部真实执行。

---

### ✅ DRIFT-COMMON-002 —— `validateReceipt` 遇缺失 `received_at` 抛 NPE（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `communityfact/wire.go:148-149`：`received, err := time.Parse(time.RFC3339Nano, receipt.ReceivedAt); return received.UTC(), err == nil` ⇒ `""` 走 `err != nil` 分支，返回 `(零值, false)`，**不抛** |
| **RAG 落点** | `Wire.java:351-356`（修前）：`try { Instant.parse(receipt.receivedAt) } catch (DateTimeParseException e) { return false }` |
| **机制** | `Instant.parse(null)` 抛 **`NullPointerException`**，不在 `DateTimeParseException` 的 catch 范围内 ⇒ 异常向上冒泡。`Wire.java:343-348` 的守卫块对另外 **7 个**字段都做了 null 防御，**唯独漏了 `receivedAt`** |
| **可观察面** | DataCenter 返回缺 `received_at` 的 receipt 时：Go 判「receipt 不合格」，RAG 抛异常炸穿调用链 |

**修法**：`receivedAt == null || isEmpty()` 前置返回 `Result(null, false)`。

---

### ✅ DRIFT-COMMON-003 —— `AuthorityUnavailable` 判定前先走到根因，404 误降级成 503（`SEMANTIC MISMATCH`）

| | |
|---|---|
| **Go 出处** | `communityfact/authority_http.go:58`：`if errors.Is(err, ErrAuthorityUnavailable)` ⇒ `errors.Is` **从 `err` 出发沿 Unwrap 链逐个比对** |
| **RAG 落点** | `AuthorityHandler.java:195-198`（修前）：`Throwable cause = unwrapCause(ex); if (isAuthorityUnavailable(cause))` |
| **机制** | `unwrapCause`（`:83-89`）一路走到链的**最深**元素；而 `isAuthorityUnavailable`（`:69-78`）**本身已经会沿链遍历**。预遍历的唯一效果是**把中间层的元素全部丢弃** |
| **触发条件** | `AuthorityUnavailableException` 有 `(String, Throwable)` 构造器（`:61-63`，public）⇒ 它自己带 cause 时就位于链的**中间**，被跳过 |
| **可观察面** | 🔴 **HTTP 状态码 404 → 503，客户端直接可见** |

**修法**：删除预遍历，直接 `isAuthorityUnavailable(ex)`；
并**删除已成为死代码的 `unwrapCause`**（留着等于给后人留一份可复发的错误映射 —— 同 `open-items` P3 删 `codeFromRPCError` 的理由）。

---

## 二、§3.3 机制接线复核（本轮独立自查）

### 🔴 已修：共享类 javadoc 里的**可证伪假框架断言**

`RpcServerInterceptorFactory.java:14-16`（修前）断言：

> 「grpc-spring-boot-starter 会**自动**把所有 `ServerInterceptor` 类型的 Bean 注册到 gRPC server」

这正是 **H2 缺陷的认知源头** —— 当年正是因为信了这句，`@Import` 被误判为「已修」。
**本轮用 `javap` 独立复核**（不采信台账证词），`3.1.0.RELEASE` 实测：

```
javap GrpcServerFactoryAutoConfiguration
  → shadedNettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
  → nettyGrpcServerFactory(…同上…)
  → inProcessGrpcServerFactory(…同上…)          ← 三个工厂方法全无 ServerInterceptor 形参
javap GrpcServerConfigurer
  → interface GrpcServerConfigurer extends java.util.function.Consumer<ServerBuilder<?>>
```

⇒ **断言为假，已改写为中性描述 + 附 javap 证据**，并写明「每个 rpc 模块要配两件、缺任一件静默失效」。

**顺带纠正的 3 处 Go 出处错误**（§3.1 要求每个 Go 事实带可核对的行号）：
原 javadoc 写的 `service/user/rpc/user.go` 与 `service/user/rpc/admin.go` **两个路径都不存在**，
且漏列 4 个注册点。实测范围内 **10 个** Go rpc 服务**全部**显式注册
（`DisableNativeRpcTimeout` 行 → `AddUnaryInterceptors` 行）：

| 服务 | 行 | 服务 | 行 |
|---|---|---|---|
| `service/user/user/rpc/user.go` | 32 → 43 | `service/user/admin/rpc/admin.go` | 33 → 43 |
| `service/article/rpc/article.go` | 36 → 51 | `service/task/rpc/task.go` | 28 → 44 |
| `service/hot/rpc/hot.go` | 32 → 42 | `service/like/rpc/like.go` | 30 → 41 |
| `service/message/rpc/message.go` | 34 → 44 | `service/comment/rpc/comment.go` | 33 → 54 |
| `service/follow/rpc/follow.go` | 34 → 44 | `service/favorite/rpc/favorite.go` | 40 → 49 |

（另有范围外的 `service/security/rpc/security.go:43`。）

**H2 现状复核：✅ 已闭环。** 实测 **10 个** rpc 模块**全部**有 `@Bean GrpcServerConfigurer`
（`user-rpc` / `admin-rpc` + 8 个新建域）。⚠️ 早前一次 grep 用 `head -10` 截断，
曾误判 `article` / `task` 未注册 —— **已复核为假**，两者都注册。

### ✅ 已证伪：台账担心的「`ObservabilityProperties` 双 Bean」不成立

`audit.md:26` 写「同时带 `@Component` + `@ConfigurationProperties`…会产生两个同名类型 Bean」。
实测**两条注册路径互斥**：

| 模块类型 | 注册方式 | 实测 |
|---|---|---|
| **10 个 api** | `@SpringBootApplication(scanBasePackages = {…, "com.rag.common"})` | 组件扫描拾取 `@Component` |
| **10 个 rpc** | 裸 `@SpringBootApplication`（**不扫** `com.rag.common`，避免拖进 servlet 类） | 一律 `@Import` |

同一模块只走一条 ⇒ 不存在同类型双 Bean。**`@Component` 必须保留**（api 路径靠它）。
⚠️ `audit.md:26` 那句「统一走 `@Import` 路径」表述不准确，建议后续更正（本轮未改该文件）。

### ✅ 已证伪：`task-api` 可能没扫到 `com.rag.common` 的担忧

`TaskApiApplication` 的 `@SpringBootApplication` 是**裸注解**，一度可疑；
实测它同时带 `@ComponentScan(basePackages = {…, "com.rag.common"}, excludeFilters = {…})`，
且类 javadoc 把「注解属性不合并、`scanBasePackages` 会悄悄退回」这个坑写清楚了 ⇒ **正确**。

---

## 三、🔴 DRIFT-COMMON-LOG-001：日志 KV 字段在**生产**被丢弃（`SEMANTIC MISMATCH`）

**这是本轮杠杆最大的一条，且差点被自己的证据推翻两次。**

| | |
|---|---|
| **Go 出处** | `logger/logger.go:148-157` 组 8 个 `logx.LogField` → `:168` `logx.WithContext(ctx).Errorw("business_error", fields...)`；go-zero `Encoding` 默认 **json**（`core/logx/config.go:16`），各服务 yaml 未设 ⇒ 逐条落盘 |
| **RAG 落点** | `Logger.java:272-281` `slf4j.atError().setMessage("business_error").addKeyValue("service",…)…` —— 字段**进了** `ILoggingEvent.getKeyValuePairs()` |

**关键分水岭 = 运行环境，两条读数都真实、但不矛盾**：

| 环境 | pattern 来源 | 含 `%kvp`？ | 字段可见？ |
|---|---|---|---|
| `service/common` 纯单测（无 Boot、无 `logback.xml`） | logback **1.5.38** `BasicConfigurator` 默认 | ✅ **含** | **看得见** |
| **真实 Spring Boot 应用** | Boot **4.0.8** `logging/logback/defaults.xml` | ❌ **不含** | **看不见** |

**Boot 4.0.8 默认（从 jar 内解出，非凭记忆）：**
```
CONSOLE_LOG_PATTERN = %clr(%d{…}){faint} %clr(%5p) %clr(%PID:-){magenta}
                      %clr(--- [app][thread]){faint}%clr(%-40.40logger{39}){cyan}
                      %clr(:){faint} %m%n%wEx
```
⇒ **无 `%kvp`**；结构化输出需显式 opt-in `CONSOLE_LOG_STRUCTURED_FORMAT`（默认为空）。

**全仓取证**：`find **/logback*.xml` = **0 命中**；20 个 `application.yml` 的 `logging:` 块**只有 `level:`，无 `pattern`**；
`service/common/pom.xml:84-87` 无 `logstash-logback-encoder`。

**实测输出长这样（单测里，字段在）**：
```
ERROR logger-test-omits-err -service="logger-test-omits-err" event_id="2113…"
  error_code="400" file_line="LoggerTest.java:108" call_chain="…" error_reason="boom"- business_error
```

**可观察面**：Go 每条 `business_error` 有 8~10 个 JSON key；RAG 生产侧只剩 `ERROR … : business_error` 一行文本。
⇒ **日志采集 / 告警 / 排障全线失能**，且 RAG 侧没有任何测试会发现（单测环境反而看得见）。

**未修 —— 需要你拍板**：修法是给 `service/common` 加 `logback-spring.xml`（含 `%kvp`）或统一 `logging.pattern.*`。
⚠️ 这是**跨 20 个模块的共享日志格式变更**，影响面远超 `service/common`，本轮**只诊断未实施**。
按 AGENTS.md §3.3，落地时**必须**配一条真启动的守门测试断言 stdout 含 `call_chain=`，
否则改完无法证明生效（这正是本条自己踩的坑）。

---

## 四、跨组件不变量：`communityfact` 消费者两侧不一致 🔴

**这条不属于任何单个组件，按 §3.5 不塞进组件台账，单列于此并需知会对应域。**

台账 D-2（`_signed-deviations.md`）称「RAG 侧**零消费者**」—— **该结论已过期**（无核对日期，按 §6.2 本就该按 `UNKNOWN` 处理）。
2026-10-23 20:2x 落地的 comment / favorite / follow 三域接了它：

| 域 | Go 侧引用 `communityfact` | RAG 侧引用 |
|---|---|---|
| `like` | ✅ **6 个文件**（`cmd/fact-authority/main.go`、`cmd/fact-dispatch/main.go`、`internal/model/fact_delivery.go` 等） | 🔴 **0**（仅 `LikeDomainFactService.java:29` 一处 javadoc 提及） |
| `comment` | ✅ 6 | ✅ 4 |
| `favorite` | 🔴 0 | 🔴 **10** |
| `article` | 🔴 0 | 🔴 1 |

⇒ **`like-rpc` 缺了 Go 侧的整套事实链路装配**（authority 查询端 + dispatch 进程 + delivery model）；
`favorite-rpc` / `article-rpc` 多出了 Go 侧没有的用法。
**归属**：`align/like` 与 `align/favorite` 工作树。本文件只登记，不越界修。

**连带影响**：D-2 原本判 `communityfact` 为「代码已对齐但零运行证据 ⇒ 不算行为对齐」。
现在它成了 **15 个文件、跨 3 个域**的关键路径 ⇒ 那句 `UNRESOLVED` 的**风险等级大幅上升**。

---

## 五、已记录未修（`communityfact` · 20 条中的其余）

> 每条均为**读 Go 原文 line-by-line** 得出，Go 事实一律带 `文件:行号`。完整覆盖清单见本次三路子审计报告。

### `SEMANTIC MISMATCH`（5 条）

| # | 项 | Go 出处 | 现状 |
|---|---|---|---|
| DRIFT-CF-001 | `newProcessLogger` 内强制 `validate()`，Go 不校验；空值本应输出 `"unresolved"` | `logging.go:43-67` vs `ProcessLogger.java:150-151` | 未修。⚠️ **跨文件**：需同步改 `favorite-rpc/FavoriteFactProcessLogging.java:76-84` 的 try/catch 降级 |
| DRIFT-CF-002 | `AuthorityHandlerFactory` 走 3 参重载拿**伪造 logger**，Go 用进程信封 logger ⇒ 6 个 metadata 键无保证 | `authority_http.go` + `fact-authority/main.go:29,71` vs `AuthorityHandler.java:106-108` | 未修（建议改 4 参） |
| DRIFT-CF-005 | Dispatcher 的 10s 超时**只发线程中断** + **单线程池** ⇒ 一次挂死永久卡死；Go 的 ctx 取消会释放 goroutine | `process.go:28-30` + `wire.go:97` vs `Dispatcher.java:122,127` + `:96` | 未修。建议改 `HttpRequest.timeout(...)`（与 B-1 同款定性：`setQueryTimeout` 而非 interrupt） |
| DRIFT-CF-006 | 节奏语义：Go `time.NewTicker(1s)` **创建即起算**，RAG **每批后再等满 1s** | `process.go:41,56-60` vs `Dispatcher.java:198-201` | 未修。稳态吞吐两侧不同量级 |
| DRIFT-CF-008 | authority 日志 trace 关联：Go 用 handler ctx，RAG 建 span 后**从不 `makeCurrent()`** ⇒ 三条日志通常无 `trace_id` | `authority_http.go:30-37` + `logging.go:75-81` vs `AuthorityHandler.java:137-143,253` | 未修 |

### `OBSERVABLE DIFFERENCE`（8 条）

| # | 项 | Go 出处 | 现状 |
|---|---|---|---|
| DRIFT-CF-003 | authority 响应体：Go `json.NewEncoder().Encode` **追加尾 `\n`** 且 `escapeHTML` 默认开；RAG 两者都没有 ⇒ **字节不同** | `authority_http.go:72` vs `AuthorityHandler.java:223,239` | 未修（需先定「以哪侧为真」） |
| DRIFT-CF-009 | `deliveryErrorCode`：Go `errors.Is` 只认哨兵**实例**；RAG 按**类型**判 ⇒ `error_code` 取值范围更宽 | `process.go:64-69` + `wire.go:35` vs `Dispatcher.java:288-302` | 未修（`Dispatcher.java:279-286` 的 javadoc 自称「与 `errors.Is` 同构」，失准） |
| DRIFT-CF-011 | OBS-r3 信封 JSON 键顺序**与 Go 相反**（metadata 块前移、trace 块前移到 KV 之前） | `logging.go:50-58,61-65,78` vs `ProcessLogger.java:246-269` | 未修。`ProcessLogger.java:55` 注释称「严格保证顺序」说明是**刻意**选择 ⇒ 应走签字偏离 |
| DRIFT-CF-012 | 时间戳精度：Go **纳秒** / RAG **毫秒** | `logging.go:53` vs `ProcessLogger.java:85-98` | 未修 |
| DRIFT-CF-013 | token 长度阈值：Go `len()` 是 **UTF-8 字节数**，RAG `String.length()` 是 **UTF-16 码元** | `wire.go:87` + `authority_http.go:24` vs `Wire.java:191` + `AuthorityHandler.java:98` | 未修（非 ASCII token 时阈值不等） |
| DRIFT-CF-014 | 畸形 endpoint 报错形态：Go **统一文案**；RAG `URI.create` 直接抛 `IllegalArgumentException` | `wire.go:84-89` vs `Wire.java:194` | 未修 |
| DRIFT-CF-015 | receipt 解码错误少一层 `decode DataCenter event receipt: ` 前缀 | `wire.go:116` vs `Wire.java:240` | 未修 |
| DRIFT-CF-017 | 缺 `InstallFrameworkLogger` 等价物（框架日志未并入 OBS-r3，键未改名，`log_source` 永远只有 `"application"`） | `logging.go:104-135` vs `ProcessLogger.java:46-47` | 未修。⚠️ 是否真触发取决于各域 logback 配置 |

### `UNRESOLVED`（1 条）

| # | 项 | 为什么判不了 |
|---|---|---|
| DRIFT-CF-004 | `Event.payload` 序列化路径不同：Go `json.RawMessage` **逐字输出**；RAG `Object` 由 Jackson 按**运行期类型**重序列化（键序 / 数字字面量 / 重复键全丢） | 取决于 `favorite-rpc` / `comment-rpc` 实际怎么填 `payload`。**不在 `common` 包内可判定**，须随那两域的审计一起定级 |

---

## 六、`logger` / `response` / `snowflake` 结论

### `response` —— ✅ `SEMANTICALLY EQUIVALENT`（逐条等价）

`code` / `msg` / `data` 三个字段的数字类型、键名、**字段顺序**、无 `omitempty` 全部一致；`null` 两侧同样输出。
唯一差异是 `Response.java:3` 一个**未使用的 import**（无行为影响）。

### `logger` / `snowflake` —— 🔴 `SEMANTICALLY NOT EQUIVALENT`

| # | 项 | 类型 | Go 出处 |
|---|---|---|---|
| DRIFT-LOG-001 | KV 字段生产侧全丢 | MISMATCH | `logger.go:148-157,168` — **见 §三，已单独展开** |
| DRIFT-LOG-002 | `call_chain` 比 Go **少一帧**：Go 尾部恒有 `logger.LogXxx` 包装帧（`runtime.Callers` skip 语义），RAG 跳过 | MISMATCH | `logger.go:139` + `:320-323`（⚠️ Go 侧 `logger.go:139` 自己的注释描述的是 skip=4 的语义，**Go 注释与实现不符**） |
| DRIFT-LOG-003 | 生产代码**从不调 `Logger.init`** ⇒ 三个静态方法必抛 `IllegalStateException`；且 `serviceName` 来源与 Go 的 `c.Name` 是不同取值体系 | MISMATCH | `logger.go:63-70`（Go 侧 15 处 main 都调 `logger.Init`） |
| DRIFT-LOG-004 | `Init` 失败后可恢复性相反：Go `sync.Once` 中毒（失败即永久失效），RAG 可重试成功 | MISMATCH | `logger.go:63-70` vs `Logger.java:171-181` |
| DRIFT-LOG-005 | Go 自动附加 `caller` / `trace` / `span`，RAG 全无（MDC 也不在默认 pattern 里） | OBSERVABLE | go-zero `richlogger.go:210,217-225` |
| DRIFT-LOG-006 | Go `Mode: file` 落盘 + 滚动；RAG 只写 stdout | OBSERVABLE | 各服务 yaml |
| DRIFT-LOG-007 | `error_reason` 空 message 兜底：Go 恒取 `Error()`（得 `""`），RAG 退 `toString()`（得类全名） | OBSERVABLE | `logger.go:118-125` vs `Logger.java:243` |
| DRIFT-SF-001 | **时钟源不同**：Go `time.Since(n.epoch)` 走**单调时钟**（回拨不可达）；RAG `System.currentTimeMillis()` 走**墙钟**（NTP / `setSystemTime` / 虚拟机快照恢复都会倒退）⇒ 位布局与回绕逻辑逐条一致，但「无回拨保护」在两侧的**实际概率不在一个量级**，RAG 一旦回拨重合即产**重复 ID** | MISMATCH | `bwmarrin/snowflake@v0.3.0/snowflake.go:126,139` vs `Snowflake.java:242` |
| DRIFT-SF-002 | workerId 多出 Go 没有的 `snowflake.worker-id` 配置档（且已确认该档是**活的**：`article-api` 扫 `com.rag.common`） | OBSERVABLE | `snowflake.go:53-59,62-84` vs `SnowflakeBean.java:59-78` |
| DRIFT-SF-003 | 初始化时机不同（Go 懒加载于首次 `GetID`；RAG 容器 refresh 期）+ `SNOWFLAKE_NODE_ID` **空白容错相反**（Go `Atoi` 不 trim，RAG `trim()`） | OBSERVABLE | `snowflake.go:22-42,56` vs `Snowflake.java:128-142,285` |

**签字偏离现状复核**（台账要求）：

- **A-1（`logFatal` 抛 `FatalError` 而非 `os.Exit(1)`）—— ✅ 仍成立**。关闭条件（全仓出现「catch 后继续运行」）**未触发**：
  全仓 7 处 `catch (FatalError)` 全是 `@ExceptionHandler` 转发/rethrow，无一处吞掉后继续。
  ⚠️ 但 `FatalErrorNotSwallowedTest` **在 worktree 里不存在**（只在 spec 被提及）⇒ **关闭条件目前靠 7 处人工 rethrow 维持，无守门测试**。
- **A-2（`call_chain` 过滤）—— ✅ 仍成立**，且可更精确：Go 的 `"github.com/zeromicro/go-zero/"` 前缀**本来就匹配不上**
  （`isFiltered` 消费的是 `getFuncName` 产出的**短名**；Go 自己的 `logger_test.go:189` 绕过 `getFuncName` 直接喂全路径，是同义反复）。
  ⚠️ **DRIFT-LOG-002 与 A-2 必须一起修**：RAG 多出的第 10 条 `com.rag.common.logger.` 会滤掉 logger 自身帧，
  而 Go 保留 `logger.LogXxx` 尾帧 —— 改一处不改另一处会产生新的不一致。

---

## 七、本轮**没有**覆盖的范围（诚实边界）

1. **`protection/`（13 文件）与 `http/`（2 文件）本轮未做 Go 对照** ——
   这两个包在 Go 侧**没有对应文件**，它们是 go-zero `rest.Server` 内建中间件（breaker / shedder / maxConns / maxBytes）
   与 `httpx` 路由错误字节形状的 Spring 表达层。
   ⇒ 对齐基准应是 **go-zero v1.10.2 库源码**，不是本仓 `Sea-RideTheWind_golang`。属独立一轮工作。
2. **`persistence/`（2 文件）未审** —— B-1 的缓解手段（`setQueryTimeout` 注入 + `57014` 翻译），已签字关闭，但未做变异验证。
3. **`observability` 子审计的完整覆盖清单本轮未收齐** —— 本轮在该包上**独立发现并修掉**了 DRIFT-COMMON-001，
   其余条目（`HttpMiddleware` / `MqConsumer` / `GoStyleRequestDeadline` 的逐函数对照）待补。
4. **全程无 Go ↔ RAG 双侧活体对拍** —— 本轮最强证据是「真 OTel SDK + 真 span exporter 驱动真实拦截器」+
   `javap` 字节码 + 全仓配置扫描。**仍未达到 §3.3 理想态**（真 PG / 真 Redis / 双栈对拍）。
5. **`service/common` 自身无启动入口** —— 它是库模块，20 个测试类中仅 `ObservabilityWiringTest` 装配 Spring 上下文，
   且那 4 条用例**全是断言 Bean 存在**。⚠️ 它自己宣称「验证 H1/H2 的机制确实生效」，
   而 `grpcInterceptorBeanExists` 恰恰是 H2（starter 不用该 Bean）**按定义抓不到**的形状 —— **为执行 §3.3 而写的测试本身违反了 §3.3**。
   ⇒ 本轮用 `RpcInterceptorTimeoutSpanTest` 补上了「断言运行时后果」这一半。
6. **`DRIFT-LOG-003` 的 `serviceName` 取值对照表未逐模块拉**（Go `c.Name` ↔ RAG `spring.application.name`，20 个模块）。
7. **Boot 4 是否改变了 `%kvp` 默认渲染** —— §三 已从 4.0.8 的 `defaults.xml` 直接取证为「不含」，
   但**未在真实启动的应用里抓一次 stdout** 闭环（§3.3 要求运行证据，本条只有配置层 + jar 内证据）。

---

## 八、效力边界

- 本文件所有断言的**基线是 commit `8422116`**。若 `do-my-own-gh` 已前进，须 `git merge do-my-own-gh` 后重新核对（§5.3.4）。
- 「已修」= **有回归测试且测试红→绿可复现**；**不等于**已在真实部署环境验证。
- 「未修」条目均为**读 Go 原文得出**，未经运行时对拍 ⇒ 一律读作「**在该证据强度下不一致**」。
- 本文件**不改** `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`（§5.3 共享面，合并后由主会话统一更新）。