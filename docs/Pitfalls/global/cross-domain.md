<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · 跨域踩坑档（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：跨域的**方法论级**坑 —— 一次会横跨多个域、或影响所有域的判断失误。

---

## PIT-GLOBAL-005 ⚠️ **全量构建偶发红 ≠ 你的改动坏了**（判定要靠对照实验，不是靠猜）

### 现象（本轮实测）

`mvn -o clean verify`（全量 56 个 reactor 模块）在第 12 个模块 `user-rpc` 红：

```
UserRpcPrimaryKeySourceParityTest.schemaIsReal » IllegalState Failed to load ApplicationContext
Caused by: com.alibaba.nacos.shaded.io.grpc...: Connection refused: /127.0.0.1:1001
```

而本轮的改动只有两处：
① `service/common` 删一个**零调用方**的废弃方法；② `service/users/user/user-rpc` 改一段 **javadoc**。

### ⚠️ 那段 Nacos 栈是**红鲱鱼**

日志里 `Connection refused: /127.0.0.1:1001` 看着像根因，但：

- `127.0.0.1:1001` **不在仓库任何配置里**（全仓 yaml/yml/properties grep 无命中）⇒ 是 Nacos **客户端自身**的端口换算；
- 把它当根因会**误判成「Nacos 没起」** ⇒ 而实际 Nacos 容器（`nacos-standalone`）**在跑**、8848/9848 **在监听**；
- ⚠️ **决定性证据**：重跑时那段 WARN **依然出现**，而整轮**全绿** ⇒ ⇒ 它是**后台告警**，不是致命原因。

⇒ **教训**：`Caused by:` 栈的**最内层**不一定是**致命原因** ——
Nacos 客户端的重连线程打出来的 `StatusRuntimeException` 会混进日志流。
⇒ 判断根因要看**哪条断言真的失败了**，不是看日志里哪段栈最吓人。

### 正确的判定序列（三步，缺一不可）

| 步 | 动作 | 本轮结果 |
|---|---|---|
| ① | **同一条命令重跑**（换窄范围） | `mvn -pl service/users/user/user-rpc -am test` ⇒ **125/125 全绿**（含那个失败类） |
| ② | **基线对照**（另开一个**只读**基线树，跑同一条命令） | 基线 `58e8314` 同命令 ⇒ **4/4 全绿** ⇒ 差异不在代码 |
| ③ | **全量重跑** | `mvn -o clean verify -fae` ⇒ **BUILD SUCCESS**（17:50，56 模块，0 失败） |

⇒ 三步叠起来 ⇒ 第一次失败是**偶发环境问题**（Nacos 注册 / 容器时序），**与改动无关**。

### ⚠️ 为什么必须做 ②「基线对照」

因为**改动本身通常就长得像元凶**。没有基线对照时，人会本能地把红账算到刚改的那几行上
⇒ 结果要么改坏本来正确的代码，要么回退一个其实没问题的改动。

⇒ **对应 AGENTS.md §3.1 的老话**：「不要拿『现有 RAG 代码』当对照基准」——
同理：**不要拿『刚才那次构建』当对照基准**。

### 顺手发现的：**基线对照别用 `git stash`**

AGENTS.md §5.3 禁用 `git stash` 是因为**共享工作区**会被并发 agent 互偷。
⇒ 但「在**自己独占**的工作树里**另开一个**基线 worktree」是安全且更干净的：

```bash
cd <主仓库>
git worktree add /tmp/rag-baseline-check 58e8314     # 只读基线树，不动任何现有工作区
cd /tmp/rag-baseline-check && mvn -o -pl <module> -am test \
    -Dtest=<同一个类> -Dsurefire.failIfNoSpecifiedTests=false
git worktree remove /tmp/rag-baseline-check          # 用完即撤
```

⇒ 优势：**零状态污染**（不动 `stash`、不动主工作区、不动其它域的工作树）。

⚠️ **配套的小坑**：`-Dtest=<类名>` 跑多模块 reactor 时，**上游模块没有同名测试会直接失败**
（`No tests matching pattern ... were executed!`）⇒ 必须加
`-Dsurefire.failIfNoSpecifiedTests=false`。

### 出处

- 踩坑位置：本轮 `align/global` @ `58e8314` 的全量 `mvn clean verify`
- 关联：`docs/DRIFT/global/cycle-deps.md` §1.5（变异测试）、[`common-tails.md`](../../DRIFT/global/common-tails.md) §〇（真源分层）
- 2026-10-25

---

## PIT-GLOBAL-006 ⚠️ **「Go 出处」要分两层，只查项目树必然落空**

⇒ 详细记录见 [`docs/Pitfalls/global/common-tails.md`](common-tails.md) PIT-GLOBAL-001
⇒ 本档只留一句：**凡涉及「请求保护层 / body 解析 / RPC 中间件」的 Go 事实，
去 `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` 找，别在 `Sea-RideTheWind_golang` 里 grep。**

---

## PIT-GLOBAL-011 🔴 **裸 `ServerCallHandler` 造的「慢服务」从不执行** ⇒ 测试绿但**空转**

### 现象

为验证「服务端超时能打断在飞处理」，测试里注册一个「处理函数睡 10 秒」的服务：

```java
ServerCallHandler<Req, Resp> slowHandler = new ServerCallHandler<Req, Resp>() {
    public ServerCall.Listener<Req> startCall(ServerCall<Req, Resp> call, Metadata headers) {
        return new ServerCall.Listener<Req>() {
            @Override public void onHalfClose() {
                Thread.sleep(10_000);          // ← 这行**从未执行过**
                call.sendMessage(resp); call.close(Status.OK, new Metadata());
            }
        };
    }
};
ServerServiceDefinition.builder(svc).addMethod(m, slowHandler).build();
```

⇒ 测试**绿**，断言 `DEADLINE_EXCEEDED` + `elapsed < 3000ms` 都过
⇒ 但它验证的是「服务端能取消一个**从未开始执行**的调用」，
**不是**「服务端超时打断一个**正在执行**的处理」。

### 根因（探针实测，grpc-java 1.63）

```
client got status=DEADLINE_EXCEEDED
RESULT startCall=true  onHalfClose=false  responded=false
```

⇒ grpc-java **会**调用裸 handler 的 `startCall()`
⇒ 但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener`
⇒ ⇒ **注册进去的业务处理函数从未执行**。

⚠️ **为什么「断言照样过」**：客户端还挂着 `withDeadlineAfter(5, SECONDS)`
⇒ 服务端永不响应 ⇒ 客户端自己的 deadline 到点 ⇒ `DEADLINE_EXCEEDED` ⇒ `assertThrows` 通过
⇒ `elapsed < 3000` 由**服务端超时拦截器**取消一个空调用而满足。

### 正确处置

⇒ 造慢服务必须走 grpc-java 的**标准派发入口**：
`io.grpc.stub.ServerCalls.asyncUnaryCall(...)` ⇒ `onHalfClose` 才会被派发
⇒ 并补一条断言「**handler 确实被调用过**」（计数器 / latch）
⇒ 再做**变异测试**：去掉 `server.intercept(...)` ⇒ 客户端 deadline 生效 ⇒ `elapsed ≈ 5000ms` ⇒ 转红。

### ⚠️ 区分「哪些裸 handler 是有意的」

| 形态 | 判定 |
|---|---|
| 裸 handler **注册进 `ServerServiceDefinition` 挂真 server** | 🔴 **本形态** ⇒ 空转 |
| 裸 handler 当**拦截器链的 `next`**（直接调 `startCall()`） | ✅ **不是本形态** —— 拦截器单测的标准写法，**别顺手改** |

⇒ 全仓扫描时**必须按「是否注册进 server」分类**，不能只 grep `new ServerCallHandler`。

### 教训

**「测试绿」要问一句「它测的东西真的发生了吗」。**
⇒ 一个**从不执行的分支**也能让断言全部通过 ——
这与 AGENTS.md §3.3 的「绿测试可能是错误答案的守卫」同源，
只是这次藏得更深：**错的是测试的构造方式，不是测试的期望值。**

### 出处

- 踩坑位置：`service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java:173`
  + `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:149`
- 关联：`docs/DRIFT/global/cross-domain.md` §二·八（X-5）
- 一次性探针：`/tmp/probe-handler/Probe.java`（不入仓）
- 2026-10-25，`align/global`

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **第七轮新增 PIT-GLOBAL-011** —— 裸 `ServerCallHandler` 造的「慢服务」**从不执行** ⇒ task + follow 两个域的同名测试**绿但空转**；含探针实测输出、两种裸 handler 的分类判据、修法与变异测试要求 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 PIT-GLOBAL-005（全量构建偶发红的判定三步 + 基线对照的 worktree 做法）· PIT-GLOBAL-006（真源分层的指针） |
