# L2 · `align/global-rpcgate` —— CD-5 残留（rpc 拦截器守门复查 + slow threshold 独立守门）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **A3 的残留**做完。第十七轮已修 `task` + `follow` 两域，但台账明写两条残留：

1. 🟡 **Go 的 slow threshold 分支仍无独立守门**（超时阈值是另一条路径，与「在飞处理被打断」不是同一条）。
2. 🟡 **其余 8 个 rpc 域的同名测试未逐一复查** ⇒ 「还有没有别的空转形态」**未穷尽**。

### 本 lane 的复查范围（精确到文件）

全仓 `*GrpcInterceptorWiringTest` 共 5 份，`task` + `follow` 已做 ⇒ **剩 3 份**：

| 文件 | 状态 |
|---|---|
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/GrpcInterceptorWiringTest.java` | ⬜ 待查 |
| `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageGrpcInterceptorWiringTest.java` | ⬜ 待查 |
| `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | ⬜ 待查 |

⚠️ `article-rpc` / `like-rpc` / `comment-rpc` / `admin-rpc` / `favorite-rpc` **没有这个测试类**
⇒ 「8 个域」里的其余几个域要么用别的类名、要么**根本没测** ⇒ **后者要登记为缺口**（这本身就是发现）。

## 二、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| 机制取证（为什么裸 `ServerCallHandler` 不被 dispatch） | `docs/DRIFT/global/cross-domain.md` | **§二·八**（CD-5 落地那节，含 ① 机制 / ② 为什么测试空转 / ④ 修法 / ⑤ 落地） |
| 波及面扫描结果 | `docs/DRIFT/global/cross-domain.md` | §二·八 ③ |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **A3** 行 |
| 坑 | `docs/Pitfalls/global/cross-domain.md` | **PIT-GLOBAL-018**（变异脚本坑）· 其它相关 |
| 已修的两个域的**样板** | `service/task/task-rpc/src/test/.../TaskGrpcInterceptorWiringTest.java`<br>`service/follow/follow-rpc/src/test/.../FollowGrpcInterceptorWiringTest.java` | 逐字读 ⇒ 这就是目标形态 |
| Go 真源 | `Sea-RideTheWind_golang` 的 grpc interceptor 注册处 + 超时阈值设置处 | 逐字读 |

## 三、判据（什么算「空转」）

一个 rpc 域的拦截器守门，若**只**断言「超时 ⇒ 抛 `DEADLINE_EXCEEDED`」，则它可能是空转的 ⇔
**它可能根本没走 grpc-java 的标准派发入口**，于是超时机制压根没参与 ⇒ 绿得毫无意义。

⇒ **判别式断言**（这三条是「真的在跑」的证据）：
1. 处理函数**确实被调用过**（`entered` 计数 / latch）
2. 取消发生时**它仍在跑**（不是已经返回了才收到取消）
3. 异常是**从服务端派发链路**出来的，不是客户端自己造的

## 四、写权范围（白名单）

**只写这些**：
- `service/users/user/user-rpc/src/test/**`
- `service/message/message-rpc/src/test/**`
- `service/hot/hot-rpc/src/test/**`
- `docs/DRIFT/global/lanes/L2-rpcgate.md`（本文件）

⚠️ **本 lane 是「守门修复」，不是行为修复** ⇒ **生产代码一行都不该动**。
若发现必须动生产代码才能写出那三条断言 ⇒ **停下来**，把理由写进本文件，交整合者决策。

**⛔ 禁区**：
- `service/common/**` · `service/like/**` · `service/comment/**`（L1 的）· `service/favorite/**`（零写权）
- `service/{article,task,follow}/**`（已修域 / 别的域）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **3 个域**逐个判定：`空转 / 正常 / 缺测试` ⇒ 写成一张表。
2. 若是空转 ⇒ 照 `task`/`follow` 的样板改（换 `ServerCalls.asyncUnaryCall(...)` + 补两条断言 + `sleep` 换 latch）。
3. **slow threshold 分支的独立守门**（至少选 1 个域做样板，⚠️ 选一个**不与上面 3 个域重叠**的，避免与 L1/L2 自己撞）。
4. **每个改动都做过变异**（M1 去 `server.intercept` ⇒ 变红在目标断言上）。
5. 台账：本文件末尾追加「逐域判定表 + 变异结果 + 缺口清单」。

## 六、验收标准

- [ ] `mvn -o -pl service/users/user/user-rpc,service/message/message-rpc,service/hot/hot-rpc -am test` 全绿，**且 `skipped = 0`**
- [ ] 每个改过的守门**都做过变异**，且**红在目标断言上**
- [ ] 明确写出「8 个域」的完整结论（哪些有测试、哪些没有、哪些空转）
- [ ] **未覆盖 / 未穷尽**的部分要显式列出（不许写「已全部对齐」）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **PIT-GLOBAL-018** | 变异脚本首版写成**编译不过**的表达式 lambda ⇒ maven **压根没跑测试** ⇒ surefire 里留着上一轮 XML ⇒ **报成假绿**。⇒ 必须三道闸：**跑前删旧 XML + 看 maven 退出码 + 查 `COMPILATION ERROR`** |
| **变异红在更早的断言** | M3「完成但**不回包**」首版 ⇒ handler 秒完成并正常回包 ⇒ 客户端拿到 OK ⇒ 红在**更早**的 `assertThrows` 上 ⇒ **目标断言压根没被执行到** ⇒ 变异必须**红在目标断言** |
| **`sleep(10_000)` 方案** | 两条新断言（「handler 被调用过」「取消时仍在跑」）在 sleep 方案下**根本写不出来** ⇒ 必须换 latch（`probe.release.await(30, SECONDS)`） |
| **上游资源不进 reactor** | 改上游模块的资源只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 必须 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false` |

---

# 附录 · 第二十三轮（`align/global-rpcgate`）—— CD-5 残留收尾：逐域判定 + 变异结果 + 缺口清单

> 本节是 **L2 lane 自己**的台账片段（2026-10-25 追加）。
> ⚠️ 只写 L2 的结论；**跨域收口由整合者** fold 进 `docs/DRIFT/global/cross-domain.md`（本 lane 不写那 4 个规范台账）。
> ⚠️ **生产代码零改动**（4 个模块的 `src/main` 一行没动）⇒ 本轮是「守门修复」，不是行为修复。

## A. 本轮做了什么（一句话）

把任务书 §一 的两条残留做完：
**① 其余 rpc 域的同名测试逐个复查（10 个 rpc 组件全表）；② slow threshold 分支的独立守门。**
⇒ 3 个待查域（users-user / message / hot）逐个判定 ⇒ **2 个域补真 server 守门**（user-rpc / message-rpc）
⇒ **hot 域原地加固**（latch 取代 `sleep` + 补两条判别式断言）⇒ **admin-rpc 补 slow threshold 独立守门**
⇒ **8 条变异全部转红，且红在目标断言上**。

## B. 逐域判定表（3 个待查域）

**判定口径**（沿用任务书 §三）：
- **空转** = 守门只断言「超时 ⇒ `DEADLINE_EXCEEDED`」，而该断言在「handler 压根没跑」时**同样成立**；
- **缺测试** = 该轴**根本没有**运行期证据；
- **守门不严** = 当前实现正确，但**退化（退回裸 handler）不会让它红** ⇒ 守门没有防退化能力。

| 域（组件） | 测试类 | 改造前形态（行号 = `git show HEAD:<file>`） | 判定 | 本轮处置 |
|---|---|---|---|---|
| users / **user-rpc** | `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/GrpcInterceptorWiringTest.java` | **纯 Mockito**：`mock(ServerBuilder.class)`（:70）+ `mock(ServerCall.class)`（:81）+ `mock(ServerCallHandler.class)`（:82）⇒ 只证明链的**拼装代码**；**无真 server、无任何超时断言** | 🔴 **缺测试**（CD-5 轴零运行期证据；§3.3「纯 mock 单测不算」） | ✅ **新增** `UserRpcGrpcInterceptorWiringTest`（真 server + `asyncUnaryCall` + 两条判别式断言） |
| message / **message-rpc** | `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageGrpcInterceptorWiringTest.java` | 同款纯 mock（:95 / :106 / :107） | 🔴 **缺测试** | ✅ **新增** `MessageRpcTimeoutGateTest`（同构） |
| hot / **hot-rpc** | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | 真 server + `io.grpc.stub.ServerCalls.asyncUnaryCall`（:247）⇒ **handler 真的跑**；但用 `Thread.sleep(10_000)`（:256）且**没有**「handler 被调用过」/「取消时仍在跑」两条断言 | 🟡 **守门不严**（**不是**空转：handler 确实执行；但 **M2 变异下仍全绿** ⇒ 退化无人拦） | ✅ 原地加固：latch 取代 sleep + 补两条断言 |

⇒ ⚠️ **三个域都不是 task/follow 那种「绿但空转」**：
user-rpc / message-rpc 是**缺测试**（连那条会空转的用例都没有）；hot-rpc 的用例**当前不空转**，只是**守不住退化**。

## C. 机制：把「裸 handler 不被 dispatch」讲到底（带 `文件:行号`）

⚠️ `cross-domain.md` §二·八 ① 写的是「grpc-java **不会**向裸 handler 派发 `onHalfClose`」。
本轮把**真正的机制**查清了（grpc-java **1.63** 源码，`~/.m2/.../grpc-core-1.63.0-sources.jar` 解包）：

| 环节 | 出处（jar 内路径） | 事实 |
|---|---|---|
| 标准派发入口**先 request** | `io/grpc/stub/ServerCalls.java:134` | `UnaryServerCallHandler.startCall` 里 `call.request(2);` |
| request 才让 deframer 消费请求体 | `io/grpc/internal/MessageDeframer.java:156-163`、`:260-285` | `request()` ⇒ `pendingDeliveries += n; deliver();`；`deliver()` 的循环条件是 `pendingDeliveries > 0` ⇒ **没人 request ⇒ 请求体一直留在 `unprocessed`** |
| 请求体没被消费 ⇒ deframer **不关闭** | `io/grpc/internal/MessageDeframer.java:188-196`、`:249-255` | `closeWhenComplete()`：只有 `isStalled()`（= `unprocessed.readableBytes() == 0`）为真才 `close()`，否则只置 `closeWhenComplete = true` |
| deframer 不关闭 ⇒ **half-close 永不派发** | `io/grpc/internal/MessageDeframer.java:234` → `io/grpc/internal/AbstractServerStream.java:238-251` → `io/grpc/internal/ServerImpl.java:846-865` | `deframerClosed()` → `listener.halfClosed()` → 应用层 `onHalfClose()` |

⇒ **更准的判别式**（比「裸 handler 不会被 dispatch」可操作）：
> **注册进真 server 的裸 `ServerCallHandler` 若没有调 `call.request(...)` ⇒ 其 `onHalfClose` 永远不会被派发 ⇒ 方法体一次都不执行。**
> ⇒ 「造慢服务」必须走 `ServerCalls.asyncUnaryCall(...)`（内部 `call.request(2)`），**或**裸 handler 里显式 `call.request(2)`。

⇒ ⚠️ **按这条判别式复查全仓，「裸 handler 注册进真 server」只剩 1 处**：
`service/common/src/test/java/com/rag/common/observability/FakeOtlpCollector.java:60-77`
—— 它**调了 `call.request(2)`（:63）** ⇒ **不是**本形态 ⇒ 不受影响（它是 OTLP **接收端**，不涉及超时）。
⇒ **「还有没有别的空转形态」这一问，本轮已穷尽**（扫描口径见 §F 末）。

## D. 变异结果（8 条，全部转红且**红在目标断言上**）

三道闸（PIT-GLOBAL-018）：① 跑前删旧 surefire XML ② 看 maven 退出码 ③ grep `COMPILATION ERROR`。

| # | 域 | 变异 | 红点（目标断言） | 实测 |
|---|---|---|---|---|
| **M1** | hot-rpc | 注释掉 `configurer.accept(builder)`（**测试内**改，等价于生产侧 `server.intercept(...)` 被删） | `assertTrue(elapsedMillis < 3_000, "必须在服务端超时（300ms）附近返回…")` | 🔴 RED，实测耗时 **5019ms** |
| **M2** | hot-rpc | `asyncUnaryCall` → **裸** `ServerCallHandler`（**不调 `call.request`**） | `assertTrue(probe.entered.await(2, SECONDS), "handler 必须**真的被调用过**…")` | 🔴 RED（2 条用例同时红：另一条红在 `probe.entered.await` 的同款断言上） |
| **M1** | user-rpc | 同 M1 | 同上（耗时断言） | 🔴 RED，**5012ms** |
| **M2** | user-rpc | 同 M2 | `entered` 断言 | 🔴 RED |
| **M1** | message-rpc | 同 M1 | 同上（耗时断言） | 🔴 RED，**5013ms** |
| **M2** | message-rpc | 同 M2 | `entered` 断言 | 🔴 RED |
| **S1** | admin-rpc | `observability.slow-threshold=100ms` → `10s` | `assertEquals(Boolean.TRUE, span.getAttributes().get(ATTR_SLOW), "耗时 400ms > 阈值 100ms ⇒ 必须打 sea.slow=true…")` | 🔴 RED（span 属性实测 `sea.duration_ms=401.752`、**无 `sea.slow`**） |
| **S2** | admin-rpc | 「快」用例的 handler 也睡 400ms | `assertNull(span.getAttributes().get(ATTR_SLOW), "耗时远小于阈值 100ms ⇒ 不该打 sea.slow…")` | 🔴 RED |

⚠️ **首版 hot M2 变异作废**（PIT-GLOBAL-018 **再踩一次**）：
脚本只替换了 `ServerCalls.asyncUnaryCall(` 这段文本，而 hot 用的是**全限定名** `io.grpc.stub.ServerCalls.asyncUnaryCall(`
⇒ 拼出 `io.grpc.stub.new ServerCallHandler<…>` ⇒ **编译不过** ⇒ maven 压根没跑测试 ⇒ surefire 里留着上一轮 XML ⇒ **报成假绿**。
⇒ 被「闸③ grep `COMPILATION ERROR`」当场抓住（`HotGrpcInterceptorWiringTest.java:[290,19] 找不到符号`）⇒ 修脚本后**重跑**才是有效 RED。

⚠️ **M1 改的是测试、不是生产代码**：把 `configurer.accept(builder)` 注释掉，与「生产侧 `server.intercept(...)` 没挂上」**效果完全一致**，
但**不碰生产代码** ⇒ 本轮 `src/main` 零改动。

## E. slow threshold 分支的独立守门（做在 **admin-rpc**）

**为什么选 admin-rpc**（任务书要求「选一个与那 3 个域不重叠的」）：
排除 `task` / `follow` / `article`（本 lane ⛔ 禁区：已修域 / 别的域）、
排除 `like` / `comment`（L1 的写权）/ `favorite`（全域零写权）
⇒ **admin-rpc** 是唯一「**不重叠 + 无 lane 拥有 + 不在禁区**」的 rpc 组件（`_PROTOCOL.md` §三 lane 清单里 admin-rpc **无人拥有**）。
⚠️ **这是本 lane 白名单外的一处扩展**（白名单只列了 user-rpc / message-rpc / hot-rpc 三个测试目录）⇒ 理由如上 ⇒ **请整合者复核**。

**判据（Go 逐字对照）**：

| RAG 侧 | Go 侧（真源，冻结只读） |
|---|---|
| `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:233-234`：`close(OK)` ⇒ `markDurationAndSlow(otelCtx, duration, finalSlow, attrs)` | `service/common/observability/rpc.go:86-89`：`err == nil` ⇒ `MarkDurationAndSlow(ctx, duration, slowThreshold, attrs...)` |
| `service/common/src/main/java/com/rag/common/observability/Observability.java:143-146`：`slowThreshold > 0 && duration > slowThreshold` ⇒ 打 `sea.slow=true` | `service/common/observability/observability.go:140-142`：`slowThreshold > 0 && duration > slowThreshold` ⇒ `attribute.Bool(AttrSlow, true)` |
| 阈值来源：`ObservabilityProperties.slowThreshold` → `service/common/src/main/java/com/rag/common/spring/RpcServerInterceptorFactory.java:77-79` | `service/common/observability/observability.go:41-55` `SlowThreshold()`（env var → 兜底 `DefaultSlowThreshold = 500ms`，`observability.go:21`） |

**守门形态**（`service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcSlowThresholdGateTest.java`）：
真 gRPC server + **容器里那条真链**（recover → breaker → shedding → 业务观测拦截器）+ 真 OTel SDK + 收集型 exporter
⇒ 断言**导出的 span 上真的有 `sea.slow`**：
1. `slowCallMustBeMarkedSlow`：handler 睡 400ms > 阈值 100ms ⇒ `sea.slow == true`，且 `sea.duration_ms` 存在；
2. `fastCallMustNotBeMarkedSlow`：handler 立即返回 ⇒ `sea.slow` **不存在** ⇒ 证明这条路径是**真比较**出来的，不是恒真。

⚠️ **踩坑（已修）**：span 名 = `MethodDescriptor#getFullMethodName()` = **`admin.AdminService/GetUser`（无前导斜杠）**
⇒ 首版按 `"/admin.AdminService/GetUser"` 写 ⇒ 两个用例都红在**更早**的「找不到 span」断言上
⇒ 修正后才得到**有效红点**（与任务书 §七「变异红在更早的断言」同款坑）。

## F. 「8 个 rpc 域」的完整结论（本轮复查后的快照）

⚠️ 范围内的 rpc 组件共 **10 个**（`article` / `comment` / `favorite` / `follow` / `hot` / `like` / `message` / `task` / `users-user` / `users-admin`）；
§二·八 说的「8 个」= 10 减去第十七轮已修的 `task` + `follow`。

| rpc 组件 | `*GrpcInterceptorWiringTest` | 真 server 超时守门 | 判定 | 本轮处置 |
|---|---|---|---|---|
| users / user-rpc | `GrpcInterceptorWiringTest`（纯 mock） | ❌ → ✅ | 缺测试 | 新增 `UserRpcGrpcInterceptorWiringTest` |
| message-rpc | `MessageGrpcInterceptorWiringTest`（纯 mock） | ❌ → ✅ | 缺测试 | 新增 `MessageRpcTimeoutGateTest` |
| hot-rpc | `HotGrpcInterceptorWiringTest`（真 server） | ✅（改造前**守门不严**） | 守门不严 | 原地加固（latch + 两条断言） |
| task-rpc | `TaskGrpcInterceptorWiringTest` | ✅ | 正常（第十七轮） | 未动（禁区） |
| follow-rpc | `FollowGrpcInterceptorWiringTest` | ✅ | 正常（第十七轮） | 未动（禁区） |
| users / admin-rpc | ❌ 无（只有纯 mock 的 `AdminProtectionChainWiringTest`） | ❌ | 缺测试（CD-5 轴） | 本轮只补了 **slow threshold 轴**（**不是** CD-5 轴）⇒ CD-5 轴仍缺，登记为缺口 |
| article-rpc | ❌ 无（只有纯 mock 的 `ArticleProtectionChainWiringTest`） | ❌ | 缺测试 | 未动（禁区） |
| comment-rpc | ❌ 无（只有纯 mock 的 `CommentProtectionChainWiringTest`） | ❌ | 缺测试 | 未动（L1 的） |
| like-rpc | ❌ 无（只有纯 mock 的 `LikeProtectionChainWiringTest`；台账 F 条已登记 `LikeGrpcInterceptorWiringTest` 不存在） | ❌ | 缺测试 | 未动（L1 的） |
| favorite-rpc | ❌ 无（只有纯 mock 的 `FavoriteProtectionChainWiringTest`） | ❌ | 缺测试 | 未动（favorite 零写权） |

⇒ **结论**：10 个 rpc 组件里，现在**只有 5 个**（user-rpc / message-rpc / hot-rpc / task-rpc / follow-rpc）有「真 server + 真超时」的运行期守门；
其余 5 个（admin-rpc / article-rpc / comment-rpc / like-rpc / favorite-rpc）**仍然只有纯 mock 的链拼装断言**
⇒ 按 §3.3，它们**不构成**「拦截器真的生效」的证据。

**「空转形态」的扫描口径（本轮用的判别式，两条同时满足才算）**：
① 裸 `ServerCallHandler`（不是 `ServerCalls.asyncUnaryCall`）**注册进真 server**（`ServerServiceDefinition.addMethod` + 挂到 `ServerBuilder`）；
② 该 handler **没有调 `call.request(...)`** ⇒ 其 `onHalfClose` 永不派发。
⇒ 全仓扫描（`grep -rn "new ServerCallHandler\|implements ServerCallHandler"`（6 个文件 / 14 处）
+ `ServerCalls.asyncUnaryCall` 交叉核对）：
**满足①②的只有 task / follow / hot 三处**（前两处第十七轮已修，hot 本轮加固）；
`service/common` 的 5 个文件命中全是**拦截器链单测里的 `next` 桩 / 生产链包装**（直接调 `startCall()`，不注册进 server）⇒ 不属本形态；
`FakeOtlpCollector` 虽注册进真 server，但**调了 `call.request(2)`** ⇒ 不属本形态。
⇒ **本形态已穷尽**。

## G. 缺口清单（**未证成 / 未覆盖**，不要读成「已全部对齐」）

1. **5 个 rpc 组件的 CD-5 轴仍缺真 server 守门**：admin-rpc / article-rpc / comment-rpc / like-rpc / favorite-rpc。
   本轮只补了 admin-rpc 的 **slow threshold 轴**（**不是** CD-5 轴）⇒ **CD-5 在全仓并未穷尽**。
2. **slow threshold 只做了 1 个域的样板**（admin-rpc）⇒ 其余 9 个 rpc 组件的 slow 判定**没有逐域证据**。
   （缓解事实：slow 判定只在共享层 `RpcInterceptor` 一处实现 ⇒ 一处样板可覆盖大部分风险，但**不等于**逐域已验。）
3. **没有做 Go ↔ RAG 双侧活体对拍**。本轮是 **RAG 侧守门修复**（把「没在验的」改成「在验」），
   **不涉及** wire 层差异 ⇒ 不涉及 proto / DDL / 错误码 ⇒ 也**不能**据此宣称 Go↔RAG 已对齐。
4. **未动的域**：article / comment / like / favorite / task / follow（禁区或别的 lane）⇒ 本轮**没有**声称它们已对齐。
5. **「在飞处理被打断」这一维只验到「取消发生时它仍在跑」**（latch + `completed` 标记），
   **没有**验「handler 线程被 interrupt / ctx 被取消」⇒ 本轮**不声称** RAG 侧能像 Go 的 `ctx.Done()` 那样**主动打断** handler 线程
   （那是 `RpcInterceptor` javadoc 自陈的 DRIFT-018 框架限制，本轮**未改**）。
6. **hot-rpc 第二条用例**（deadline 传播）改造后用的是**与第一条同款的 latch 慢服务**（各自独立的 probe）⇒ 它验的是
   「`DeadlineState` 在 handler 线程上有值」+「取消时 handler 仍在跑」，**没有**新增别的语义。
7. **admin-rpc 的 slow threshold 守门依赖「`GlobalOpenTelemetry` 在 Spring 上下文 refresh 之前被替换」**
   （`@BeforeAll` 早于上下文创建）⇒ 与 `AdminSpanWiringTest` 同款前提；
   ⚠️ 若将来 Boot 的 OTel 自动配置被接上（`opentelemetry-sdk-extension-autoconfigure` 进依赖），该前提会失效 ⇒ **需要重新评估**（本轮**未**处理）。
8. **未跑全量 `mvn clean verify`** —— 本轮只跑了**改动到的 4 个模块**（+ `-am` 上游）
   ⇒ 全仓是否仍全绿**未由本轮证明**（整合者合并后需跑全量，见 `_PROTOCOL.md` §五·4）。

## H. 本轮改动清单（**生产代码零改动**）

| 文件 | 改动 |
|---|---|
| `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | `Thread.sleep(10_000)` → latch（`entered` / `release` / `completed`）；两条用例各补「handler 真的被调用过」+「取消发生时它仍在跑」；`obs.onNext` 加 try/catch（调用已被关闭时不再抛） |
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcGrpcInterceptorWiringTest.java` | **新增**：真 server + `asyncUnaryCall` + 两条判别式断言 + 生产 `rpc-timeout: 2s` 断言 |
| `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageRpcTimeoutGateTest.java` | **新增**：同上（message 域同构） |
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcSlowThresholdGateTest.java` | **新增**：slow threshold 独立守门（真链 + 真 OTel SDK + 导出 span 断言） |

⚠️ **写权说明**：后两个文件分别在 `service/users/user/user-rpc` 与 `service/message/message-rpc`（**白名单内**）；
**`service/users/admin/admin-rpc/src/test/**` 在本 lane 白名单之外** ⇒ 扩展理由见 §E ⇒ **请整合者复核是否接受**。
