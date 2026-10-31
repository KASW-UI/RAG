<!-- @move AGENTS.md L591-L733 · §0 请求保护层下沉到 service/common -->
### ✅ 请求保护层**下沉到 `service/common`**：`com.rag.message...protection` → `com.rag.common.protection`（2026-10-22）

> 用户拍板「那扔common」。本节记录**搬了什么**、**为什么**、**搬的过程中实测挖出的三件事**
> （其中一件推翻了我自己写在代码注释里的错误推理）。

#### 搬了什么

| 内容 | 从 | 到 | 理由 |
|---|---|---|---|
| 10 个主类（4 Filter + breaker + shedder + properties + 状态包装 + 熔断异常 + client 拦截器） | `com.rag.message.message.api.protection` | **`com.rag.common.protection`** | Go 这四层是 **go-zero `rest.Server` 的内建行为**，不是 message 域的代码 —— 任何 `rest.MustNewServer` 起的进程都自带 |
| 4 个纯单测 | 同上 | `com.rag.common.protection`（test） | 无 Spring 上下文、无 message 依赖，纯粹搬家 |
| 4 个集成测试 | 同上 | `com.rag.message.message.api`（**留在 message-api**，改包名） | 它们证明的是「message-api 的装配」，不是「这层逻辑对不对」 |

**依据**：搬迁前 grep 确认 10 个类**零 `com.rag.message` / `com.rag.users` 内部 import**；
`service/common` 现有依赖已含 `jakarta.servlet-api`(provided) / `spring-boot-starter` / `spring-web`(optional) / `grpc-stub`。

#### 🔴 搬的过程中实测挖出①：**「私有组件变共享」必须连同它的装配义务一起搬**

下沉后立刻炸了 **`admin-api` 23 条测试全红**：

```
Error creating bean with name 'goStyleBreakerFilter'
  ... Unsatisfied dependency expressed through constructor parameter 0:
  No qualifying bean of type 'com.rag.common.protection.GoStyleProtectionProperties' available
```

根因：`@ConfigurationProperties` 类**不会**被组件扫描自动注册，必须由
`@EnableConfigurationProperties` 声明；而当时**只有 message-api 写了那一行**。
⇒ 修法不是「给每个下游模块补一行」，而是新增 `GoStyleProtectionConfiguration`
（`@Configuration` + `@EnableConfigurationProperties`）**放在同一个包里**，
让「被扫到即生效」。message-api 随即**删掉**了自己那行显式声明。

> **可复用的教训**：组件从私有变共享时，它的装配义务必须一起共享。
> 把装配写成 N 份，漏一份就是一个**启动即崩**的模块 —— 而且崩在下游、看起来与本次改动无关。

#### 🔴 搬的过程中实测挖出②：**我写在 javadoc 里的「顺序」推理是错的**

`GoStyleMaxBytesFilter` 原先写着「注册在 `HIGHEST_PRECEDENCE + 6`（早于 auth 拦截器进入）以保住 413 压过 401」。
**变异测试直接推翻它**：把 `@Order` 改成 `LOWEST_PRECEDENCE` 后，message-api 与 like-api 的
「超限 → 413 而非 401」断言**依然全绿**（且已用 `javap` 确认 jar 里的 `@Order` 确实变成了
`2147483647`，即变异真的进了产物 —— 不是变异没生效）。

**正确机制**：Servlet 的**所有** `Filter` 都包在 `DispatcherServlet` **外面**，而 `HandlerInterceptor`
（JWT 鉴权）跑在其**内**部 ⇒ 两者的先后由**容器结构**决定，**与 order 数值无关**。
`@Order` 的真实作用是**在 Filter 之间排序**（相对 `HttpMiddlewareFilter` 的 `+5`、
相对同包另外三个保护 Filter 的 `+1/+2/+3`），以及对齐 Go 的 native middleware 同序。

⇒ 能真正让该断言变红的变异是**让这层失效**（摘掉 `@Component`），已实测：
**`Status expected:<413> but was:<401>`**。
三处错误表述（`GoStyleMaxBytesFilter` 类 javadoc、两个测试的注释、本文档上一段）已同步改正。

> **元教训（与 §6.7.7 互补）**：变异测试**没红**有两种原因 ——「测试是假绿」与「**变异选错了对象**」。
> 区分它们必须**先验证变异真的进了产物**（本次用 `javap` 读 `~/.m2` 里的 class），
> 再判断归因。**直接跳到「测试有问题」是错的。**

#### 搬的过程中实测挖出③：一条 `@DisplayName` 断言原本指向**不存在的守门脚本**

`MessageApiProtectionMvcTest` 的类 javadoc 写着「响应头那层已由
`tools/difftest/check-protection-layer.sh` 在真容器下守」—— 按 `find` 实测，**该脚本不存在**，
响应头一直由 `MessageApiProtectionRealTomcatTest` 守着。已改正。
⇒ §3.3 的形态：**javadoc 里写「已由 X 守」不等于 X 存在**。

#### 加的两道装配守卫（**诚实边界：都不是补现行 bug**）

实测（`mvn dependency:list -pl service/message/message-rpc`）：该模块依赖树里
**没有** `jakarta.servlet-api`、也没有 `tomcat-embed-core`（只有不含 servlet API 的 `tomcat-embed-el`）；
而四个 rpc 进程又**已经刻意不扫 `com.rag.common`**（原因正是同包下 `HttpMiddlewareFilter` 是 servlet Filter）。
⇒ 今天两者**没有相交**。但那句「不扫 common」写在每个 rpc 进程的注释里，而
`com.rag.common` 恰恰是唯一必须被所有进程共享的包 ⇒ 用条件注解把「这是 HTTP 层」编码进去：

| 类 | 注解 | 覆盖 |
|---|---|---|
| 4 个 Filter | `@ConditionalOnWebApplication(SERVLET)` | 无 servlet 的进程不注册 |
| `GoStyleGrpcClientBreakerInterceptor` | `@ConditionalOnClass({GrpcGlobalClientInterceptor, ManagedChannel})` | 无 devh starter 的进程不注册 |

`service/common` 里 devh client starter 声明为 **`optional=true`**（同 `spring-web` 的理由）：
普通 compile 会把整套 gRPC 客户端栈 + Nacos 传递给每个只需要一张错误码表的纯库模块。

**两道守卫都有变异实证**：摘掉 `@ConditionalOnWebApplication` ⇒
`ProtectionNotRegisteredInNonWebContextTest` 转红（`expected: <false> but was: <true>`）。

#### 一处**部署可见**变更

配置前缀 **`message-api.protection` → `rag.protection`**（共享组件不该归某一业务域所有）。
各服务 yaml 与测试属性覆盖已同步改写。**漏改的后果是回落到默认值，而默认值 == go-zero tag 默认值**
⇒ 不会静默关闭保护层，是刻意选的 fail-safe 方向。

#### ✅ 连带关闭 `like-api` 的 DRIFT-003 / 004 / 005

`like-api` 的 `scanBasePackages` **本来就含 `com.rag.common`** ⇒ 保护层对它**自动生效、零装配代码**。
正因为零装配，**没有任何编译期或启动期信号**告诉使用者「这层在你身上生效了」⇒ 必须补测试，否则
就是一条**从未验证过就生效的行为变更**（§3.3）。新增 `LikeApiProtectionMvcTest`（4 条）：

| 断言 | 覆盖 |
|---|---|
| 四个 Filter Bean 都存在 | 「自动生效」这个前提本身 |
| 超限 + **无 token** → **413** | DRIFT-004（413 压过 401） |
| 未超限 + 无 token → **401** | 上一条不是被无条件短路 |
| 未超限 + 合法 token → **200 + envelope** | 正常路径没被误拒 |

**DRIFT-005（gRPC client breaker）对 like-api 无需任何 Java 改动**：
Go `getlikestatelogic.go:57-63` 的 `status.FromError` 对非 status 错误返回 `ok=false` ⇒
`NewErrCode(ErrorServerCommon)` = **5001**；而 `LikeApiSupport#rpcError` 已经实现了同一条 `ok` 二分
（`extractStatus` 返回 null ⇒ 5001）。⇒ 熔断异常自然落到正确分支。
⚠️ 这与 message-api **不同**：message 域那条路径给 **1015**，所以它需要单列 `@ExceptionHandler`。

#### 测试归属的一处调整：1015 的 msg 断言从 `common` 移到 `message-api`

`GoStyleGrpcClientBreakerInterceptorTest` 原先断言「熔断拒绝 → 1015 + msg `service busy`」，
它 import 了 `message-common` 的码表 ⇒ **会让 `common` 反向依赖业务模块**（common 是所有业务模块的下游）。
拆成两半：`common` 只留**机制**断言（不是 `StatusException` / `StatusRuntimeException`），
**码表映射**那条挪到 `GoStyleGrpcClientBreakerE2ETest`，且在那里是**真运行时**断言
（读真实 envelope 的 `msg`）—— 证据强度高于原来的静态映射断言：
它同时证明「异常被谁接住」「接到哪个码」「文案从哪来」三件事。

#### 实测口径（各模块**隔离单跑**，非全仓汇总）

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **134** | 102 + 32（迁入的 4 个纯单测类） |
| `service/message/message-api` | **70** | 68 + 2（`ProtectionNotRegisteredInNonWebContextTest`） |
| `service/like/like-api` | **51** | 47 + 4（`LikeApiProtectionMvcTest`） |
| `service/users/admin/admin-api` | **113** | 下沉曾致 23 红，已由 `GoStyleProtectionConfiguration` 修复 |
| `service/users/user/user-api` | **63** | 无变化 |
| `service/hot/hot-api` | BUILD SUCCESS | 无变化 |

⚠️ 本次全程有**并行 Maven 会话**在跑，期间出现过一次 `Wire.java` 的 Lombok
「构造器参数个数不符」编译失败 —— 那是 `target/` 被抢占的偶发，与本次改动无关（重跑即消失）。

#### 本轮（404/405 那节）之后仍未关闭

**`like-api` 已登记的漂移至此全部关闭**（003/004/005/006/007/008/010/011 均已处理，009 是被复核推翻的误报）。
最后关闭的 006 见下一节 —— ⚠️ 它顺带推翻了 message-api 那条「两侧都是拒绝」的签字理由。

⚠️ 本节原先还写着「DRIFT-007/008 未修」与「DRIFT-001 取消语义未闭合（`Future.cancel(true)`
**无法中断**阻塞 gRPC 调用，需显式 deadline 透传）」—— **两条都已关闭，而后者那句前提是错的**：

- DRIFT-007/008 → 见下一节（✅ 已修，并顺带挖出 DRIFT-LIKEAPI-011）。
- 取消语义 → 见「取消语义对齐」那节（✅ 已修：池加界 + deadline 透传）。
  ⚠️ 「`Future.cancel(true)` 无法中断阻塞 gRPC 调用」**被实测推翻**：它**能**中断，
  只是产生的 status 是 `CANCELLED` 而非 `DEADLINE_EXCEEDED`。
  ⇒ 原判断描述的现象**根本不存在**，真正的问题是**超时对 client breaker 不可见**。


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


### lane W13

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-003 | D（差异已消失 ⇒ 无待实现内容） | P2 | 否 | Go `service/like/api/likecenter.go:37`（`server := rest.MustNewServer(c.RestConf)` ⇒ Go 的四层保护是 go-zero `rest.Server` 的**内建行为**，不是 like 域自己的代码）＋ `go-zero@v1.10.2/rest/engine.go:105`（`buildChainWithNativeMiddlewares`）＋ `:108`（**之后**才 `appendAuthHandler` ⇒ 鉴权排在链尾）＋ `:164`（`handler.MaxBytesHandler(...)`）＋ `go-zero@v1.10.2/rest/chain/chain.go:38-45`（`Append` 的语义 = 「加在请求流的**最后**」⇒ MaxBytes 在鉴权**之外**）＋ `go-zero@v1.10.2/rest/handler/maxbyteshandler.go:19-22`（`r.ContentLength > n` ⇒ 裸 `w.WriteHeader(413)`、无 body）＋ `service/like/api/internal/handler/routes.go:49`（`rest.WithJwt(...)` ⇒ 6 条路由全部受保护）＋ `service/like/api/internal/logic/like/getlikestatelogic.go:57-63`（`status.FromError(err)` 的 `ok=false` 分支 ⇒ `errmsg.NewErrCode(errmsg.ErrorServerCommon)`）＋ `service/like/common/errmsg/errmsg.go:41`（`ErrorServerCommon = 5001`）vs RAG `service/like/like-api/src/main/java/com/rag/like/like/api/LikeApiApplication.java:75-78`（`scanBasePackages` 含 `com.rag.common` ⇒ 保护层**自动生效、零装配代码**）＋ `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:81-83`（`@ConditionalOnWebApplication(SERVLET)` ＋ `@Component`）＋ `:108-113`（`getContentLengthLong() > maxBytes` ⇒ 裸 413、无 body、只 `setStatus`）＋ `service/common/src/main/java/com/rag/common/protection/GoStyleProtectionConfiguration.java:78-79`（`@Configuration` ＋ `@EnableConfigurationProperties` ⇒ properties Bean 必被注册）＋ `service/like/like-api/src/main/resources/application.yml:151-163`（四层阈值 = Go 的 tag 默认 10000／1048576／900／enabled）＋ `service/like/like-api/src/main/java/com/rag/like/like/api/service/LikeApiSupport.java:177-186`（`rpcError`：`extractStatus` 为 null ⇒ 5001）＋ `:198-206`（`extractStatus` 只认 `StatusException`／`StatusRuntimeException`）＋ `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:302-303`（抛 `GoStyleCircuitOpenException` ⇒ 非 status ⇒ 落 5001 分支）＋ `service/common/src/main/java/com/rag/common/protection/GoStyleCircuitOpenException.java:30`（`extends RuntimeException`）＋ `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiProtectionMvcTest.java:99-102`（四个保护 Filter Bean 在位）＋ `:111`（超限 ⇒ 413 且压过鉴权）⇒ ⇒ **差异已消失**：本轮实跑 `mvn -o -pl service/like/like-api test -Dtest=LikeApiProtectionMvcTest` ⇒ `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（真 Spring 上下文 ＋ 真实 MVC 管线）⇒ 「保护层在 like-api 上自动生效」这个**零装配前提**当场成立；like 域台账 `docs/DRIFT/like/api.md:384-386` 对 `DRIFT-LIKEAPI-003`（请求保护层四层全缺）／`-004`（超限请求优先级 413 而非 401）／`-005`（gRPC client 熔断缺失）记的「✅ 已关（2026-10-22）· **本轮未复审**」本轮**补上复审** ⇒ 三条均已闭合 ⇒ 台账记的「like-api 缺保护层 ⇒ 401 压过 413 ／ 无 client 熔断」这条已不存在 ⇒ 归 D（无待实现内容）；⚠️ **编号撞车**：同一个 `DRIFT-003` 在仓内另有两处 —— `DRIFT/message/api.md:79`（message 域 `,omitempty` vs `,optional` 根因段，本轮核过：Go `service/message/api/internal/types/types.go:18` = `json:"extra,optional"` vs RAG `service/message/message-api/src/main/java/com/rag/message/message/api/dto/AdminSendNotificationReq.java:127`「刻意不对 extra 做显式 null 校验」⇒ 该段已一致）与 `DRIFT/users/user/api.md:35`（「编号说明」段，无待办）⇒ 本行结论按派生层主落点 `DRIFT/common/protection.md:89` 给出 | 2026-10-30 |

### 客户端硬上界的共享层那半（2026-10-31 · 经授权落码）

**结论先行**：共享层的「客户端硬上界」**唯一实现本来就存在** ⇒ ⇒ **域侧只需配一个参数，零代码改动**
⇒ ⇒ 但此前**缺一条运行期证据**（只有属性默认值的断言）⇒ ⇒ 本轮补上，并把「不得再造私有副本」钉成守门。

**一 · 唯一实现（已在本仓，非本轮新增）**
- `service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor.java`
  —— 带 devh 的 `@GrpcGlobalClientInterceptor` ⇒ ⇒ 进全局客户端拦截器 registry ⇒ ⇒ 装在**每个**
  出向 devh 调用上 ⇒ ⇒ 与 Go 的 `zrpc/client.go:70-71`
  （`if c.Timeout > 0 { opts = append(opts, WithTimeout(...)) }`）**同形** ⇒ ⇒ 域侧**零代码改动**。
- 封顶语义 = `min(入向剩余纳秒, 客户端硬上界, 调用点自带 deadline)`（`:196` / `:200-201` / `:208`）
  ⇒ ⇒ 取更早者（Go 的 `context.WithTimeout`）⇒ ⇒ **不是覆盖**。
- 开关 = `rag.protection.outbound-deadline.cap-millis`，**默认 0 = 不启用**
  （`GoStyleProtectionProperties.java:286`）⇒ ⇒ 理由见同文件 `:261`（Go 的 2000ms 是**默认值**且
  各域可覆盖 —— article api 侧 = 70000 ⇒ 写成全局默认会让 article **比 Go 严 35 倍**）
  ⇒ ⇒ **必须逐域配**。

**二 · 本轮新增的两条守门（共享层）**
- `GoStyleOutboundDeadlineClientCapGateTest`（3 形态）—— ① `cap-millis=2000` 且**无入向预算**
  ⇒ 出向调用在 **2000ms** 处 `DEADLINE_EXCEEDED`；② **负控**：`cap-millis=0` ⇒ **挂满**下游 5s
  （⇒ 形态①的绿来自 cap，不是「in-process 自己会超时」）；③ `cap-millis=2000` ＋ 入向预算 300ms
  ⇒ 在 **300ms** 处失败（⇒ 钉的是 **min** 语义，不是覆盖）。
- `GoStyleClientCallTimeoutSingleSourceGateTest`（3 断言）—— ① 域侧**不得**出现第三份
  `GoStyleClientCallTimeout` 私有副本（**棘轮例外集** = 已知的 2 份：comment-rpc / favorite-rpc
  ⇒ 域侧删掉后自动收紧）；② 共享层唯一实现必须在位；③ 它必须是**全局客户端拦截器**且真的读
  `capMillis`（**只认代码行，不认 javadoc** —— 见「三」）。

**三 · 本轮踩到并已修掉的一个「弱判据」（值得记）**
守门②③ 最初写成 `src.contains("@GrpcGlobalClientInterceptor")` ⇒ ⇒ **该类的 javadoc 里也出现过
这个字符串** ⇒ ⇒ 拿掉注解后**判据仍然绿** ⇒ ⇒ 是**变异实测**把它抓出来的（第一次跑 M4 时「没红」）
⇒ ⇒ 改成「**只取代码行**（去注释）再断言」⇒ ⇒ M4 随即转红 ⇒ ⇒ **弱兜底判据会把真缺陷洗成 0**
（AGENTS.md §3.3 的同族教训）。

**四 · 诚实边界（本轮**没有**证明的）**
- **各域的 yml 一个都还没配** ⇒ ⇒ 「域侧只配参数」这条**尚未被任何域真正使用** ⇒ ⇒ 残余面 =
  派工面里那条「零 deadline 类补硬上界」的任务**本体**（域侧 yml ＋ 各域断言）。
- **api 侧另有内联形态的封顶**（`like-api` / `favorite-api` 在 client 类里直接算 min）⇒ ⇒ 它们不声明
  那个类名 ⇒ ⇒ **不在这条守门的检测面** ⇒ ⇒ 属**另一条轴**（api 侧的传播/上界任务）⇒ ⇒ 本轮
  **不假装覆盖**它。
- 守门按**类名**识别副本 ⇒ ⇒ 一份**改了名**的同款实现不在它的视野内 ⇒ ⇒ 那类靠 review 纪律互补。
- 本轮用 devh 的 **in-process** channel factory（与生产同一 `createChannel` 实现）⇒ ⇒ 上游 hop 的
  传输层与生产不同 ⇒ ⇒ 测的是「deadline 到点 ⇒ 立即失败」这个语义，**不依赖传输层**。

### api 侧装配面实测结论（2026-10-31 · G4 lane · 本层自测）

> **触发**：G1 回执 item 5 —— 「api 侧扫描面实测结论」⇒ 本节是本层的**独立复核**（不采信转述）。

#### 结论一句话

**api 侧不需要改启动类就能装上共享层的出向拦截器** ⇒ ⇒ 与 rpc 侧靠 `@Import` **同形** ⇒ ⇒ **「域侧只配参数」两侧都通**。

#### 两侧装配面逐条实测（判据打在**消费点**，非声明点）

| 侧 | 装配手段 | 出处 | 实测读数 |
|---|---|---|---|
| **rpc** | `@Import(GoStyleProtectionConfiguration.class)`（**显式导入**） | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java:92` · `service/comment/comment-rpc/.../CommentRpcApplication.java:74` · `service/favorite/favorite-rpc/.../FavoriteRpcApplication.java:83` · `service/follow/follow-rpc/.../FollowRpcApplication.java:94` · `service/hot/hot-rpc/.../HotRpcApplication.java` | 4+ 个 rpc 域实测同一形态 |
| **api** | `@SpringBootApplication(scanBasePackages = {…, "com.rag.common"})`（**组件扫描**） | `service/users/user/user-api/src/main/java/com/rag/users/user/api/UserApiApplication.java:21-24`（`"com.rag.users.user.api"` ＋ `"com.rag.common"`）· `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/AdminApiApplication.java:23-26`（`"com.rag.users.admin.api"` ＋ `"com.rag.common"`） | 两个 users api 域实测同一形态 |

⇒ ⇒ **两侧的差别只是「谁来登记这个 Bean」**（rpc = 显式 `@Import` · api = 包扫描）⇒ ⇒
**共享层 `GoStyleProtectionConfiguration` 在两侧都能被注册** ⇒ ⇒ **域侧只需配参数（yml），不必改启动类**。

#### ⚠️ 一条**必须写清**的限定：两种装配手段**不是同一件事**

| | rpc 侧 `@Import` | api 侧 `scanBasePackages` |
|---|---|---|
| 粒度 | **单个** Configuration 类 | **整包**（`com.rag.common` 下全部 `@Component` / `@Configuration`） |
| 漏配的代价 | 该 rpc 域**少装**保护链 ⇒ ⇒ 保护链**静默缺层** | 该 api 域**少扫**整个共享层 ⇒ ⇒ 保护层**静默全缺** |
| 可观测性 | 保护链守门会红（`layerNames()` 断言链构成） | 保护层守门会红（Bean 存在性断言） |

⇒ ⇒ **api 侧的漏配面更大**（整包 vs 单类）⇒ ⇒ **api 侧更需要「域侧只配参数」这条路走通** ⇒ ⇒
本节结论对 api 侧的意义比 rpc 侧更大。

#### 🟡 诚实边界（三条，别读宽）

1. **本节是源码级证据（E1）** —— 本轮**没有起真进程**验证「api 进程里出向拦截器真在链上」。
   ⇒ ⇒ 与既有口径一致：**读作「按装配面推导」，不读作「已实测」**。
2. **`scanBasePackages` 只保证「Bean 被注册」，不保证「拦截器被接在链上」** ——
   ⚠️ 这正是 AGENTS.md §3.3.3 的核心形态（「**断言『Bean 存在』≠ 断言『机制生效』**」）⇒ ⇒
   **「api 侧扫描面够用」与「api 侧出向 deadline 真生效」是两个面** ⇒ ⇒
   **后者必须另有运行期证据**（真 devh 上下文 ＋ 真 `@GrpcClient` stub）⇒ ⇒
   本层的运行期守门见 `docs/DRIFT/global/vs-common.md` §4.19.15（G4 `3499249a`）。
3. **本节只覆盖 users 域的两个 api**（`user-api` / `admin-api`，本轮实读的）⇒ ⇒
   **其余 7 个 api 域未逐一实测** ⇒ ⇒ **不得外推成「10 个 api 域全部如此」** ⇒ ⇒
   「其余 api 域的 `scanBasePackages` 是否都含 `com.rag.common`」= **待核**（域侧可自核）。

#### 🏷 一条**纠偏**（G1 自查纠偏的确认）

G1 上一轮曾误判成「api 侧配 `cap-millis` 无效」⇒ ⇒ **本层独立复核结论 = 该误判不成立** ⇒ ⇒
**api 侧配参数是有效的**（装配面走通 ⇒ 参数载体 `GoStyleProtectionProperties` 会被注册 ⇒ 参数会生效）⇒ ⇒
**G1 的自查纠偏成立** ⇒ ⇒ 记此条以免该误判在后续轮次复发。

### 🆕 共享层「客户端硬上界」逐域取值口径（2026-10-31 · G4 lane · 本层逐域实测）

> **触发**：合并收口执行者在本轮回执第六节给出的口径（「取值 = 该域 Go yaml 的显式 `Timeout`；该域 yaml 未显式配置时才用 2000」）⇒ ⇒
> **本层逐域实测后确认该口径成立，并补一条更精确的事实**（见 §二 ②）。

#### 一、口径（**本层可直接引用**）

⇒ ⇒ **逐域配 `rag.protection.outbound-deadline.cap-millis` = 该域 Go yaml 里那个出向 RPC 客户端段的显式 `Timeout`；该段没有显式 `Timeout` ⇒ 才用 2000**（2000 = go-zero `zrpc/config.go:31` 的 `json:",default=2000"`，**不是各域统一值**）。

⚠️ **「2000 是默认值」不等于「各域都该配 2000」** ⇒ ⇒ **逐域配的值可能与 Go 差很多** ⇒ ⇒ **本节给出逐域实测结果**。

#### 二、逐域实测结果（本层逐个 yaml 读出，2026-10-31）

**② 全仓唯一的显式 RPC 客户端 `Timeout` ＝ article 域的 `SecurityConf: Timeout: 10000`** ⇒ ⇒
**其余 12 个 rpc 域的出向 RpcClientConf 段全部没有显式 `Timeout` ⇒ 走 `default=2000`**。

| Go 侧出向段 | 文件:行 | 显式 `Timeout` | ⇒ ⇒ 该域该出向调用应配 |
|---|---|---|---|
| **article-rpc → security-rpc** | `service/article/rpc/etc/article.yaml:90`（`SecurityConf` 段） | **10000** | **10000**（不是 2000） |
| article-rpc → message-rpc | `service/article/rpc/etc/article.yaml`（`MessageRpc` 段） | 无 ⇒ default=2000 | 2000 |
| comment-rpc → message-rpc / article-rpc | `service/comment/rpc/etc/comment.yaml` | 无 ⇒ default=2000 | 2000 |
| favorite-rpc → user-rpc / article-rpc | `service/favorite/rpc/etc/favorite.yaml` | 无 ⇒ default=2000 | 2000 |
| follow-rpc → user-rpc | `service/follow/rpc/etc/follow.yaml` | 无 ⇒ default=2000 | 2000 |
| hot-rpc | `service/hot/rpc/etc/hot.yaml` | 无 ⇒ default=2000 | 2000 |
| like-rpc → message-rpc | `service/like/rpc/etc/like.yaml` | 无 ⇒ default=2000 | 2000 |
| message-rpc → user-rpc / admin-rpc | `service/message/rpc/etc/message.yaml` | 无 ⇒ default=2000 | 2000 |
| task-rpc | `service/task/rpc/etc/task.yaml` | 无 ⇒ default=2000 | 2000 |
| user-rpc / admin-rpc | `service/user/user/rpc/etc/user.yaml` · `service/user/admin/rpc/etc/admin.yaml` | 无 ⇒ default=2000 | 2000 |
| points-rpc / security-rpc | `service/points/rpc/etc/points.yaml` · `service/security/rpc/etc/security.yaml` | 无 ⇒ default=2000 | 2000 |

⚠️ **两条必须写清的排除项（避免派工时配错）**：

1. ⚠️ **like 域的 `Timeout: 1000`（`service/like/rpc/etc/like.yaml:80`）** ＝ **`AntiAvalanche`（防雪崩）段的 `Timeout`**
   ⇒ ⇒ **它不是 RPC 客户端超时** ⇒ ⇒ **不适用本口径** ⇒ ⇒ **like 域按 2000 配**。
2. ⚠️ **各 yaml 顶层的 `Timeout: 70000`（`article.yaml:3`）/ `Timeout: 60000`（`security.yaml:3`）** ＝ **服务端自己的超时**
   ⇒ ⇒ **不是出向客户端上界** ⇒ ⇒ **不适用本口径**。
3. ⚠️ **task 域的 `DialTimeoutMs: 500` / `ReadTimeoutMs: 1000` / `WriteTimeoutMs: 1000`**
   ⇒ ⇒ **是 Kafka/HTTP 侧的连接超时** ⇒ ⇒ **不是 RPC 客户端上界** ⇒ ⇒ **不适用本口径**。

#### 三、⚠️ 配错方向的代价（**比 Go 严** ⇒ 是漂移）

⇒ ⇒ **配 2000 而 Go 是 10000**（article → security-rpc）⇒ ⇒ **比 Go 严 5 倍** ⇒ ⇒
⇒ ⇒ **RAG 会在 Go 还能成功的地方提前超时** ⇒ ⇒ **属「RAG 做了更严那一侧」的有意偏离**（须签字）⇒ ⇒ **不是对齐**。
⇒ ⇒ **api 侧另有一档**：Go 侧 api 的出向客户端上界由 `service/*/api/etc/*.yaml` 决定（article api 侧实测 `Timeout: 70000`）⇒ ⇒ **配 2000 = 比 Go 严 35 倍** ⇒ ⇒ **同样不是对齐**。

#### 四、本节的诚实边界（三条）

1. **本节是源码级逐 yaml 读取（E1）** ⇒ ⇒ **没有起 Go 进程实测「某域出向调用真按这个值超时」** ⇒ ⇒
   **不得外推为「活体验证过」**。
2. **只覆盖 rpc 域的出向段** ⇒ ⇒ **api 侧的逐域取值本节未列**（api 侧 yaml 结构与 rpc 不同，且各域 api 侧的出向客户端段需单独核）⇒ ⇒
   **api 侧的值要单独派工时再核**。
3. **本节只给口径，**不代改任何域的 yml**（域侧写权）⇒ ⇒ **本层零代码改动**。
