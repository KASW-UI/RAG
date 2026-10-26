# global 第十七轮 —— X-5 落地：task + follow 的「造慢服务」测试换 `ServerCalls.asyncUnaryCall` + 补「handler 真被调用过」断言

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`bf5fba7`
> **性质**：**跨域测试真缺陷修复（task + follow 两个 rpc 域）+ 变异测试 + 台账**
> **本轮不碰**：`favorite` 域（**本轮完全无交集** ⇒ 零改动）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二·八（X-5，2026-10-25 第七轮确证）：

> grpc-java 1.63 **会**调裸 `ServerCallHandler` 的 `startCall()`，
> 但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener`
> ⇒ 两个域的「造慢服务」测试里「睡 10 秒」**从未发生**
> ⇒ 客户端拿到的永远是**自己**的 5s deadline
> ⇒ 原断言 `elapsedMillis < 3_000` 验的是「服务端能取消一个**空调用**」，
> 而不是它 DisplayName 声称的「服务端超时打断一个**正在执行**的处理」。

第七轮只取证、未改代码（改法要同时动 task + follow 两个域 ⇒ 单独一轮落地）。

⚠️ **这是 AGENTS.md §3.3 点名的形态**：**绿测试可能是错误答案的守卫**。

---

## 二、🔴 修法前先复测机制（不能只信上一轮的探针）

第十六轮的方法论：**台账里写的机制，重新拿探针实测一遍**。

一次性探针（`/tmp/dispatch-probe/DispatchProbe.java`，grpc-java 1.63 **真 server** + **生成代码**的方法描述符）：

```
① 裸 ServerCallHandler      => startCall=true   onHalfClose=false   client=DEADLINE_EXCEEDED
② ServerCalls.asyncUnaryCall => methodRan=true                        client=OK
```

⇒ 结论与第七轮一致，且补充一条**签名前缀**：`ServerCalls.asyncUnaryCall` 在 1.63 有
**单参重载** `asyncUnaryCall(UnaryMethod<ReqT,RespT>)`（**不带** `MethodDescriptor`）
⇒ 改造时**不能**照抄网上常见的双参写法。

---

## 三、改了什么

### ① `service/task/task-rpc/.../TaskGrpcInterceptorWiringTest.java`
### ② `service/follow/follow-rpc/.../FollowGrpcInterceptorWiringTest.java`

两个文件**同构** ⇒ 改动逐条相同：

| # | 动作 | 目的 |
|---|---|---|
| ① | 裸 `ServerCallHandler`（手写 `startCall` + `onHalfClose`） ⇒ **`ServerCalls.asyncUnaryCall((req, observer) -> {...})`** | 让 grpc-java 走**标准派发入口** ⇒ `onHalfClose` 被派发 ⇒ 处理函数**真的执行** |
| ② | `Thread.sleep(10_000)` ⇒ **`probe.release.await(30, SECONDS)`**（latch） | ① 测试可在 `finally` 里**立刻**放行，不必等满 10 秒；② 能**精确**观测「取消发生时它还在跑」 |
| ③ | **新增断言**「`probe.entered.await(2, SECONDS)` 为 true」 | 补上原来缺的**那一维**：handler **确实被调用过** |
| ④ | **新增断言**「`probe.completed.get()` 为 false」 | 补上**「取消发生在处理执行中途」**这一维 |
| ⑤ | `finally` 里加 `probe.release.countDown()` | 放行 handler，别让它挂在 30s 上拖慢测试 |

⚠️ **不改成 `sleep`**：latch 方案能同时满足「可观测」与「可收尾」，
`sleep` 只能满足后者，且让 ③ ④ 两条断言**测不出来**（等到断言时它早就跑完了）。

### ③ 🔴 一条**没有**改的东西：超时语义本身

**生产代码一行没动**（`TaskRpcApplication` / `FollowRpcApplication` 的
`server -> server.intercept(GoStyleRpcProtectionChain.build(...))` 原样保留）：

- 机制是**真的**（第七轮 + 本轮探针双重复测）；
- 本轮修的是**测试没在验它**，不是**它没生效**。

---

## 四、变异测试（证明新守门非空转）

| # | 变异 | 期望 | 实测 |
|---|---|---|---|
| **M1** | 去掉 `server.intercept(...)`（两个域的 `rpcInterceptorConfigurer` 改成块 lambda 空实现） | 🔴 RED（`elapsed ≈ 5000ms`，只有客户端 deadline 生效） | ✅ **RED** —— task `5010ms` / follow `5009ms` |
| **M2** | 把 `asyncUnaryCall` 换回**裸** `ServerCallHandler` | 🔴 RED（**新增**的 `entered` 断言转红） | ✅ **RED** —— 红在「handler 必须**真的被调用过**」 |
| **M3** | handler 在超时前**完成但不回包**（`await(0)` + 去掉 `observer.onNext/onCompleted`） | 🔴 RED（**新增**的 `completed` 断言转红） | ✅ **RED** —— 红在「取消发生时 handler 必须**仍在跑**」 |

### 🔴 M1 首跑是**假绿** —— 抓到一条新的踩坑

首版 M1 变异写成 `return server -> server;` ⇒ **编译不过**
（`不兼容的类型: lambda 主体与 void 函数接口不兼容`）
⇒ **maven 不会跑测试** ⇒ surefire 目录里留着**上一轮**的 XML
⇒ 脚本读到旧 XML ⇒ **报成 GREEN**。

⇒ 若不是加了「跑前先删旧 XML + 检查 maven 退出码 + 检查是否 COMPILATION ERROR」三道闸，
本轮会**带着一个假绿结论**提交。

⇒ 这条已进 `docs/Pitfalls/global/cross-domain.md`（新增 **PIT-GLOBAL-018**）。

### 🔴 M3 首版红在**错误的地方**

首版 M3 只把 `await(30s)` 改成 `await(0)` ⇒ handler 秒完成**并正常回包**
⇒ 客户端拿到 **OK** ⇒ 短路在 `assertThrows` ⇒ **新增的 `completed` 断言压根没执行到**
⇒ 等于没验。改成「完成但**不回包**」⇒ 超时照常触发 ⇒ 才红在**目标断言**上。

⇒ **判据**：变异跑完要看**红的那条断言的失败消息**，确认它**就是**目标断言。

---

## 五、Go 出处汇总

| 事实 | 出处 |
|---|---|
| Go 侧**显式**注册拦截器（带 timeout 与 slow threshold 两参） | `Sea-RideTheWind_golang/service/task/rpc/task.go:44`（follow 同款） |
| Go 侧 timeout 值 = go-zero `zrpc/config.go` 的 `Timeout int64 \`json:",default=2000"\`` = 2000ms | `service/task/rpc/task.go:28` + `task.yaml` 未配 `Timeout` |

> ℹ️ 本轮**不涉及** wire 层 / proto / DDL ⇒ **无 Go 侧行为漂移**，
> 修的是「RAG 的测试没有在验 Go 要求的那件事」。
> ⚠️ 但**这正是 §3.1 禁止的反面**：拿「测试是绿的」当「行为是对的」⇒
> 本轮证明绿测试**守的是空调用**。

---

## 六、验证清单

- [x] `mvn -o test -pl service/task/task-rpc,service/follow/follow-rpc -Dtest='TaskGrpcInterceptorWiringTest,FollowGrpcInterceptorWiringTest'` ⇒ **4 条全绿**
- [x] 变异 M1 ⇒ task / follow **各 1 条转红**（`elapsed 5010ms` / `5009ms`）
- [x] 变异 M2 ⇒ task / follow **各 1 条转红**（**新增**的 `entered` 断言）
- [x] 变异 M3 ⇒ task / follow **各 1 条转红**（**新增**的 `completed` 断言）
- [x] 两模块**全量**测试全绿：`task-rpc` **108 条** + `follow-rpc` **72 条** = **180 条**，0 失败 0 错误
- [x] 变更后 `git diff` 复核：两个域**只有测试文件**改动，**生产代码零改动**

### ⚠️ 本轮**没有**验证的（不声称已证明）

1. **Go 侧同一场景的活体对拍** —— 本轮是 **RAG 侧自洽性**修复（测试没在验它 ⇒ 改成在验它），
   **不是** Go↔RAG 的 wire 层差异 ⇒ **不涉及** proto / DDL / 错误码。
2. **Go 的 slow threshold 分支** —— Go 侧 `observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold())`
   带**两个**参数；两个域的链里那条阈值**仍无独立守门**。
3. **其余 8 个 rpc 域的同名测试** —— 本轮**未逐一复查**
   ⇒ 「还有没有别的空转形态」**未穷尽**。

---

## 七、📌 待维护者合并到**共享索引**（global 不写，§5.3b）

| 位置 | 要加什么 |
|---|---|
| `AGENTS.md` §0 域表 `global` 行 | X-5 由「🔴 已确证」⇒ 改为「✅ 第十七轮已落地（task + follow）」 |
| `docs/DRIFT/_INDEX.md` | global 域的台账变更记录指针 |

⚠️ **favorite 域零改动** ⇒ 本轮**无需**通知 favorite owner。
