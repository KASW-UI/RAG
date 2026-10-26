# global 第十轮 —— CAND-12 方案⑤「主动探测写」落地 + 签字

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`b75de7f`
> **性质**：**功能落地 + 有意偏离签字**（T-4 资源面从「不修」变成「已修」）

---

## 一、要解决什么

用户 2026-10-25 05:58：「**CAND-12：主动探测写（周期 flush()）（签字偏离）；做完后继续探索完所有未知项**」

⇒ 第八轮已把 `SD-2026-10-25-02` 退回「待签字」，并查证了 6 条替代路径。
⇒ 本轮：按用户拍板**选定方案⑤并实现**，同时把方案⑤带来的偏离**签字认账**。

---

## 二、方案⑤的实现形态（与用户原话「周期 flush()」有一处必要偏差）

| | 决定 | 理由 |
|---|---|---|
| **探针载体** | 🔴 **直接写 socket 的 `HTTP/1.1 103 Early Hints\r\n\r\n`**（28 字节），**不是** `response.flushBuffer()` | `flushBuffer()` 会**提前提交响应** ⇒ 之后 `setContentType`/`setHeader` 被 Tomcat **静默忽略** ⇒ **丢掉 `Content-Type`**（本仓 E-1 同款 bug）⇒ 比本条更严重的回归 |
| **必须 `write` + `flush` 两步** | 🔴 只写不 flush **探不出错误** | Tomcat 的 socket 写缓冲未满时**不真正 syscall**（实测：只 `write` ⇒ 无异常） |
| **取消通道** | worker 线程上 `io.grpc.Context.withCancellation()` + `attach/detach`（grpc-api 1.63 **无 `makeCurrent()`**） | grpc-core 1.63 `ClientCallImpl:363` `context.addListener(this, directExecutor())` ⇒ 取消 Context 即取消在飞 RPC；`ClientCallImpl:119` 在**调用线程**抓 `Context.current()` ⇒ 必须在 worker 上 current |
| **等待方式** | 单次 `await(timeout)` ⇒ **切片等待 + 周期探测** | 总预算按**真实经过时间**重算 ⇒ **不延长不缩短** |
| **探测间隔** | 默认 **500ms**，system property `rag.common.http.disconnect-probe-interval-ms`（下限 50ms） | ⛔ **不得要求任何 api 模块改配置**（`favorite-api` 不碰）⇒ 用常量 + system property，**未动任何 `application.yml`** |
| **两个前置守卫** | 非 HTTP/1.1 ⇒ 跳过；`response.isCommitted()` ⇒ 跳过 | HTTP/2 是二进制帧协议，写 HTTP/1.1 文本会**破坏帧流**；已提交时 Tomcat 正在写，会交错 |

---

## 三、实测数字（真 Tomcat 11.0.24 + 真 in-process gRPC）

| 指标 | 值 |
|---|---|
| **RST → 下游 gRPC 被取消的延迟** | **461ms**（≈ 1 个探测周期；HTTP 超时 8s） |
| **499 定型时刻** | 从「写响应失败时」提前到 **~505ms** |
| **快请求（< 500ms）** | **零探针 ⇒ 零 wire 差异** |
| **慢请求（900ms）** | 客户端先收到 **1** 行 `103 Early Hints` |

---

## 四、偏离（已签字 ⇒ `SD-2026-10-25-04`）

1. **wire 层**：客户端响应流最前面多出 **N 行 `103 Early Hints`**（RFC 9110 要求客户端忽略 ⇒ 业务语义不变，**字节层面确实多了**）
2. **时序层**：**499 的定型时点晚于 Go**（Go = 断连瞬间；RAG = 下一个探测周期）

⇒ ❌ 原 `SD-2026-10-25-02`（「框架限制」）**撤销** —— **问题本身被修掉了** ⇒ 该条从未被签字，也不需再签。

---

## 五、守门（§3.3：真容器 + 变异测试）

`GoStyleClientDisconnectProbeGateTest`（**3 条**）：

1. 🔴 客户端 RST ⇒ **在飞下游 gRPC 被取消**（461ms，远早于 8s 超时）+ HTTP 层很快定型 499
   ⇒ ⚠️ 下游通过 `io.grpc.Context.current().addListener` 观察取消（**不是**靠线程中断）⇒ **断言有判别力**
2. ✅ 客户端不断连 ⇒ body 逐字节相同、**`Content-Length` / `Content-Type` 全保住**
   ⇒ 含**正向对照**（必须先看到 1xx，否则后面断言全空转）
3. 🔒 探针字节**不破坏 HTTP 流**

**变异测试**：把 `probe(...)` 换成 `State.UNAVAILABLE` ⇒ **3 条全红**（核心断言报「实测取消延迟=**-1ms**」= 从未被取消）⇒ **守门非空转**。

⚠️ **既有测试必须同步改一处**：`HttpClientDisconnectRealTomcatTest` 的 body 提取用 `indexOf("\r\n\r\n")`（=「第一个空行」）⇒ 1xx 出现后该假设失效 ⇒ 改为「按报文块跳过 1xx」的解析器 ⇒ **断言强度不变**（仍要求 `Content-Length == body 字节数`）。

---

## 六、改动清单

| # | 文件 | 变更 |
|---|---|---|
| ① | `service/common/.../observability/GoStyleClientDisconnectProbe.java` | **新增**（425 行） |
| ② | `service/common/.../observability/HttpMiddleware.java` | 切片等待 + 周期探测 + gRPC Context 取消 + 499 记录抽出 |
| ③ | `service/common/src/test/.../GoStyleClientDisconnectProbeGateTest.java` | **新增**（489 行 / 3 条） |
| ④ | `service/common/src/test/.../HttpClientDisconnectRealTomcatTest.java` | body 提取改 1xx-aware（断言强度不变） |
| ⑤ | `docs/Intentional_Deviations/global/common-tails.md` | `SD-2026-10-25-02` **撤销** + 新增 **`SD-2026-10-25-04`（已签字）** |
| ⑥ | `docs/DRIFT/global/common-tails.md` · `docs/DRIFT/global/_README.md` | T-4 ③ 改为「资源面已修」+ 残留时序差 |

⚠️ **① 已被上游的 `git add -A` 误卷入 `b75de7f`（提交信息是 docs，内容含 425 行探针类）** ——
内容**逐字节完好**，但提交信息与内容不符。**已推送的历史不重写** ⇒ 本轮提交的信息里明确说明。

---

## 七、验证

- [x] `mvn -o -pl service/common -am test` ⇒ **Tests run: 198, Failures: 0, Errors: 0**
- [x] 变异测试 ⇒ 3 条全红（「取消延迟=-1ms」）
- [x] 既有 `HttpClientDisconnectRealTomcatTest` 3 条仍绿
- [x] `Content-Length` / `Content-Type` 保形实测通过

---

## 八、已知风险

1. **反射链依赖 Tomcat 内部 API** ⇒ 升级可能静默失效 ⇒ ✅ **守卫测试会红**（断言「必须看到 1xx」）⇒ **不会静默降级**；探针本身 fail-open。
2. **裸写绕过 Tomcat `OutputBuffer`** ⇒ 理论上可能与业务裸写交错 ⇒ 本仓 `ObservabilityResponseWriter` 全缓冲式、worker 不碰 socket ⇒ **当前不触发**。
3. **取消延迟 461ms > Go 的 1~3ms** ⇒ 残留时序差 ⇒ 已签字。
