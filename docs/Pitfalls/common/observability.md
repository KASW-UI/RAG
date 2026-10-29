<!-- @new 2026-10-23 · align/common 工作树产出 · 基线 commit 8422116 -->

# 「跑绿」与「看得见」都可能骗人：两个 `service/common` 的实测样本

> **产出方**：`align/common` 工作树，基线 commit **`8422116`**。
> 逐条漂移记录在 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md)。
> 本文件只回答「**为什么会踩**」。

---

## PIT-COMMON-09 · 坑 1：同一份代码，两个环境，两种可见性  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不跑容器、不比配置** ⇒ 「同一份代码两个环境两种可见性」本轮不触发。
> **旧号**：`坑 1` ⇒ **新号**：`PIT-COMMON-09`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留在标题里**）。
> **证据**：本条 §根因（默认值随宿主而变）

### 现象

`Logger` 用 SLF4J 2.x 的 `addKeyValue()` 打结构化字段（`event_id` / `call_chain` / `error_code` / …）。
在 `service/common` 的单测输出里，这些字段**全都在**：

```
ERROR logger-test-omits-err -service="logger-test-omits-err" event_id="2113647903641030656"
  trace_id="unknown" error_code="400" file_line="LoggerTest.java:108"
  call_path="…" call_chain="…" error_reason="boom"- business_error
```

而在**真实 Spring Boot 应用**里，它们**一个都不会出现** —— 只剩一行 `ERROR … : business_error`。

### 根因（两级，都不是「配置没写对」，是「默认值随宿主而变」）

| 环境 | pattern 从哪来 | 含 `%kvp`？ |
|---|---|---|
| `service/common` 纯单测（无 Spring Boot、无 `logback.xml`） | logback **1.5.38** `BasicConfigurator` 自身默认 | ✅ 含 |
| 真实 Spring Boot 应用 | Boot **4.0.8** jar 内 `logging/logback/defaults.xml` 的 `CONSOLE_LOG_PATTERN` | ❌ **不含** |

Boot 4.0.8 默认（从 jar 解出）：

```
%clr(%d{…}){faint} %clr(%5p) %clr(%PID:-){magenta} %clr(--- [app][thread]){faint}
%clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx
```

⇒ `KeyValuePair` 只挂在 `ILoggingEvent.getKeyValuePairs()` 上，**没有 `%kvp` 就等于没写**。
结构化输出在 Boot 里是**显式 opt-in**（`CONSOLE_LOG_STRUCTURED_FORMAT`，默认空）。

### 为什么这个坑特别贵

1. **单测环境正好是那个「能看见」的环境** ⇒ 越依赖单测，越确信它没问题。
2. 出问题的是**日志采集 / 告警 / 排障**，而这些**没有任何测试会红**。
3. 「全仓没有 `logback*.xml`」这个事实**同时**支持两个相反结论 ——
   「所以没人配置过」和「所以走的是默认」。必须知道**谁的默认**才能判。

### 纪律

> **断言「某机制生效」时，先问「在哪个环境里生效」。**
> 库的默认值 ≠ 框架的默认值 ≠ 应用的配置。**三者必须分别取证。**

取证顺序（成本递增、结论递增可靠）：
1. 读依赖 jar 里的默认配置（`unzip` + 看 `defaults.xml`），**不要凭记忆**；
2. 读应用自己的配置文件；
3. **真启动抓 stdout** —— 前两步都闭合了也还不够。

本条只做到第 1、2 步，**第 3 步未做** ⇒ 台账里如实记为「配置层 + jar 内证据，运行证据缺」。

---

## PIT-COMMON-10 · 坑 2：测试类名与 javadoc 都在说「已验证机制」，实际只验证了「Bean 在」  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮读守门结论时，「测试类名说已验证机制」**不等于**机制生效 ⇒ 直接适用。
> **旧号**：`坑 2` ⇒ **新号**：`PIT-COMMON-10`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留在标题里**）。
> **证据**：`docs/Intentional_Deviations/common/_signed-deviations.md` §B-1 补记末段（「gRPC `ServerInterceptor` 从未挂到 server 上 ⇒ `DeadlineState` 恒未设置 ⇒ **DRIFT-018 的 24 处守卫此前全部是死代码**」）＋ 本条 §现象

### 现象

`ObservabilityWiringTest` 的类 javadoc 写着：

> 「§3.3 强制测试：**真正装配 Spring 上下文**，验证 H1 / H2 的机制确实生效」

而它的 4 条用例是：

| 用例 | 实际断言 |
|---|---|
| `timeoutPropertiesAreBound` | 属性绑定成了 3s / 2s —— ✅ 这条**有效** |
| `grpcInterceptorBeanExists` | `ctx.getBean(ServerInterceptor.class) != null` |
| `httpFilterBeanExists` | `ctx.getBeanNamesForType(...)` 有 1 个 |
| `lifecycleBeanExists` | 同上 |

**问题**：H2 缺陷的内容恰恰是「`ServerInterceptor` Bean 在容器里，但 starter 不用它」。
⇒ `grpcInterceptorBeanExists` 在 **H2 存在与不存在两种实现下都通过** —— 这是**结构性假绿**。

### 为什么会出现

这个类是为了抓 H2 才写的；写它的人（也就是前一轮）**抓到了「Bean 没注册」那一半**，
**没抓到「框架没接管」那一半**。H2 的两半长得几乎一样（都是「链路没通」），
但只有一半能被「Bean 存在」这类断言看见。

### 纪律

> **测试名、类 javadoc、断言三者必须一致。**
> javadoc 声称的强度，就是别人（和未来的你）会依赖的强度。
>
> 具体到框架机制：断言「对象进入了框架的接管点」（`verify(builder).intercept(...)`）
> **弱于**断言「运行时的可观察后果」（客户端 ≈2s 返回 `{"code":1015}`）。
> 能断言后果就不要退而断言接管点。

本轮按后半条补了 `RpcInterceptorTimeoutSpanTest`：
装真 OTel SDK + 真 `InMemorySpanExporter`，驱动真实拦截器，
断言**超时后 span 真的被导出且带 `sea.timeout=true`** ——
修前它转红（导出 0 个 span），修后转绿。

---

## PIT-COMMON-11 · 坑 3：修「框架不接管」时，只改了一半  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不改任何框架接管点** ⇒ 「修『框架不接管』时只改了一半」本轮不触发。
> **旧号**：`坑 3` ⇒ **新号**：`PIT-COMMON-11`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留在标题里**）。
> **证据**：本条 §现象

### 现象

H2 当年的结论是「`@Import` 只让 Bean 存在，starter 不接管」。
各 rpc 模块后来都补了 `@Bean GrpcServerConfigurer` —— **那半修对了**。
但**共享库自己的 javadoc 至今仍写着**：

> 「grpc-spring-boot-starter 会**自动**把所有 `ServerInterceptor` 类型的 Bean 注册到 gRPC server」

而这句话**就是当年误判的源头**。只要它还在，任何人 review 到这段都会以为「有 `@Import` 就够了」。

同一次修法还留下了 3 处 Go 出处错误（路径写成不存在的 `service/user/rpc/user.go` 等），
以及 1 处把 `message-rpc` 误列为「全包扫描 `com.rag.common`」的模块（它实际是 `@Import` 路线）。

### 纪律

> **证伪一条框架断言时，把结论写回产生它的地方。**
> 只修「症状」（给各模块补 Bean）而不修「信源」（共享库的注释），
> 等于把同一个陷阱留给下一个人 —— 而且这次下一个人不会知道这是个已证伪的结论。
>
> 同理，**§3.2 要求的 Go 出处必须真的可核对**：路径写错会让 review 的人查不到，
> 于是这条「有出处」的记录实际上等于没有出处。

本轮用 `javap` 直接打掉了它（不是采信台账证词）：

```
javap GrpcServerFactoryAutoConfiguration
  → shadedNettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
  → nettyGrpcServerFactory(…同上…)
  → inProcessGrpcServerFactory(…同上…)      ← 三个方法全无 ServerInterceptor 形参
```

---

## PIT-COMMON-12 · 坑 4：`head -N` 把「没找到」变成了「不存在」  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要写「Go 侧有没有 X」这类**否定性**结论 ⇒ 「`head -N` 把『没找到』变成『不存在』」直接适用。
> **旧号**：`坑 4` ⇒ **新号**：`PIT-COMMON-12`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留在标题里**）。
> **证据**：同族实例：`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-059（2026-10-26 实测：第一次 grep 空结果 ⇒ 下了反向结论 ⇒ **结论整个反转** ⇒ 由此挖出 `E65`）

本轮查「哪些 Go rpc 服务注册了 `NewUnaryServerInterceptor`」时，命令输出被 `head -10` 截断，
`article` 与 `task` 恰好落在截断线外，我据此写下「这两个没注册」。
**复核时两者都在**（`article.go:36→51`、`task.go:28→44`）。

### 纪律

> **截断的输出不能支撑「不存在」这类否定断言。**
> 需要下否定结论时，用专门的不利查询重新跑一次（`grep -c` / `grep -l` 全量），并明确写出计数。

### 🔴 2026-10-24 补：这条坑的**同类事故发生在本项目自己的台账里**

**首版 `docs/DRIFT/common/alignment-2026-10-23.md` 写的是「H2 现状复核：✅ 已闭环，
实测 10 个 rpc 模块**全部**有 `@Bean GrpcServerConfigurer`」——那句话是假的。**

- 根因**一模一样**：当时的 `grep GrpcServerConfigurer` 输出**被桌面端截断**
  （工具返回里明写了 `desktop grep output truncated`），我没察觉就据此外推「全部都有」。
- 讽刺之处：**这条坑正是我自己在同一批产物里写下的**（本节），
  而我在写台账时**没有回头核对**自己刚写下的纪律 ⇒ **坑档写下来了不等于不会犯**。
- 代价：漏掉了 `hot-rpc` 缺 `GrpcServerConfigurer` 这个**真实缺陷**
  （⇒ H2 形态在 `hot-rpc` 上复发：无 gRPC span、无 deadline 传播、
  `checkDeadline()` 全是死代码、`DeadlineQueryTimeoutInjector` 不给 SQL 设 queryTimeout）。
  详见 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md) 的 DRIFT-COMMON-004。

### 是怎么发现的：**复验脚本的计数与预期不符**

发现它不是靠重读台账，而是靠 2026-10-24 复验时的一个**「预期外」**：

```
复验脚本输出：命中 9 / 20
而台账写的是「10 个 rpc 模块全部有」
```

⇒ **凡是台账里带具体数字的断言，都必须有一条能重跑的复验命令。**
数字对不上时，**先怀疑台账**（本次就是台账错，代码是对的）。

### 根治：把「数命中文件」换成「跑一条检查」

同一个晚上新增的 [`tools/difftest/check-grpc-interceptor-wiring.py`](../../../tools/difftest/check-grpc-interceptor-wiring.py)
逐个 rpc 进程断言「① `@Import(RpcServerInterceptorFactory)` 与 ② `@Bean GrpcServerConfigurer` + `.intercept(` **同时具备**」，
当前 **`EXIT=1`，精确指向 `hot-rpc`**。

⇒ **凡是「每个消费方都要做 X」这类契约，都应该有一条可跑的检查**，
而不是让人去数 grep 命中几个文件 —— **计数是人最容易出错、且错了不会报错的环节。**