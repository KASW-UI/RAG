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

## PIT-GLOBAL-012 🔴 **「这个配置是 per-app 的」≠「只能逐模块改」⇒ 会把可修的漂移误判成域边界阻塞**

### 现象

`DRIFT-HOTAPI-030`（Tomcat 数「参数总数」vs Go 数「非空值个数」）的修法需要
`server.tomcat.max-parameter-count` 改成 `-1`。第九轮查到的实情是：

- 该配置是 **per-app** 的（每个 api 模块一份 `application.yml`，10/10 都配了 `2048`）；
- ⇒ 于是我写下「**修法必然要动 `favorite-api`** ⇒ **在 favorite 边界下永久阻塞**」。

**这句话是错的。** 它只证明了「**逐模块改 yaml**」这一条修法被边界挡住，
**没有证明「不存在别的修法」** ⇒ 于是把一条**可修**的真实漂移，错登记成了「不可修」，
还差点按「认账不改」去登记签字（那就等于把一条本可以修掉的漂移**永久固化**）。

### 根因

**把「配置的绑定方式」当成了「配置的生效方式」。**

`server.tomcat.max-parameter-count` 是 **Spring Boot 属性绑定**到 `ServerProperties`，
再由 Boot 的 `TomcatWebServerFactoryCustomizer` 通过 `ConnectorCustomizer` 设到 Tomcat 的
**连接器**上。但 **Tomcat 的上限本身是一个运行期可写的字段**：

| 环节 | 事实 | 出处 |
|---|---|---|
| 上限字段 | `Connector.maxParameterCount`（默认 `1000`）**有 setter** | Tomcat `catalina/connector/Connector.java:251` / `:538` |
| 每请求取值 | `Request` 每请求从 connector 取值 | Tomcat `catalina/connector/Request.java:148` / `:480` |
| `-1` 的语义 | **不限**（不是「上限 -1」） | Tomcat `tomcat/util/http/Parameters.java:79`（javadoc）+ `:247`（`if (limit > -1 && …)`） |
| Boot 的接线点 | `ConnectorCustomizer` lambda | Boot 4.0.8 `org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer`（javap：`lambda$customizeMaxParameterCount$0(int, Connector)`） |
| Boot 的执行顺序 | `getOrder()` **返回 0** | 同上（javap 实测）⇒ **后注册的 `@Order > 0` 的 customizer 覆盖它** |
| 共享层能否进所有 api 上下文 | ✅ **10/10** | ① 10/10 api 模块 `pom.xml` 依赖 `service/common` ② 10/10 扫 `com.rag.common`（9 个 `@SpringBootApplication(scanBasePackages = {…, "com.rag.common"})`；`task-api` = `@ComponentScan(basePackages = {…, "com.rag.common"})`）<br>⚠️ 本仓**不用** `AutoConfiguration.imports`（见 `service/common/.../protection/GoStyleProtectionConfiguration.java:45-49`）⇒ 靠**组件扫描** ⇒ 共享层加个 Bean/Filter 就在所有 api 上下文里 |

⇒ ⇒ **上限可以在运行期由 `service/common` 统一改掉** ⇒ **10 个 api 模块零代码改动** ⇒
**favorite 零触碰** ⇒ **不存在「边界阻塞」**。

### 判据（下次遇到「X 是 per-app 的，所以只能逐模块改」时，按这四问走）

1. **这个值在框架里最终落到哪个对象/字段上？** —— 落到**运行期可写的对象**上 ⇒ 可能能绕过属性绑定。
2. **框架自己的设置器在什么顺序执行？** —— `getOrder()` 值 + 我们能不能排在它后面（Boot 4 的 `TomcatWebServerFactoryCustomizer` = **0**）。
3. **共享层能不能被所有目标模块自动加载？** —— 本仓靠**组件扫描** ⇒ `com.rag.common` 里的 Bean 对 10 个 api 全部生效 ⇒ **零 api 模块改动**（这与 T-6「改 1 处 ⇒ 10 个 api 全部生效 ⇒ 不碰任何 api 模块的代码」是同一手法）。
4. **「必须改 N 个模块」这个结论，是从「属性绑定是唯一入口」推出来的，还是实测出来的？** —— 前者是**假设** ⇒ 必须实测（§3.3）。

### 教训

> **「修法 A 被边界挡住」≠「这条漂移不可修」。**
> 边界约束的是**写哪些文件**，不是**能不能改变行为** ——
> 共享层（`service/common`）本来就是**跨域行为的唯一合法落点**，
> 而它在 10 个 api 上下文里**自动生效** ⇒ 「碰不到某个域的文件」≠「碰不到那个域的行为」。
>
> ⚠️ **反向也要成立**：共享层会**改变 favorite 的可观察行为**（本次是把它本地的 `5003` 变成 Go 的 `400`）⇒
> **不能静默** ⇒ 必须写进台账并告知该域 owner（global 不碰它的代码，但共享层会改它的行为）。

### 出处

- 踩坑位置：`docs/DRIFT/global/cross-domain.md` §二·十一（X-6）第九轮结论（**已作废**）→ 第十一轮改判
- 关联：`docs/DRIFT/global/cross-domain.md` §二·十一 ③ 之二（共享层修法的逐条取证）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-013 🔴 **Go 的行为常常是「逐 api 注册」的 ⇒ 共享层复刻它时不能假设「Go 这一条是全局一致的」**

### 现象（第十四轮实测）

第十四轮落地 X-6（`DRIFT-HOTAPI-030`）时，第十三轮的修法草案写着「共享 Filter 统一写 Go 的 **400 原文**」。
落地前的取证把这条前提**推翻**了：

| Go 侧是否注册 `httpx.SetErrorHandler` | 域 | 解析失败时的 wire 形状 |
|---|---|---|
| ✅ 注册了 | `hot/api/hotcenter.go:53` · `follow/api/followcenter.go:53` · `like/api/likecenter.go:48` · `message/api/messagecenter.go:53` · `comment/api/commentservice.go:47` | **200** + `{"code":5001,"msg":"<该域文案>","data":null}` |
| ❌ 没注册 | `article/api` · `user/api` · `task/api` · `favorite/api` | **400** + `text/plain` + `nosniff` + Go 原文 |

⚠️ **而且各域 5001 的文案还不一样**：hot / follow / message = `"internal server error"`；like / comment = `"系统内部错误"`。

### 根因

Go 的 `httpx.SetErrorHandler`（go-zero `rest/httpx/responses.go:141-172` 的 `doHandleError`）是
**每个 api 进程各自注册**的**可选钩子** ⇒
「有没有这个钩子」决定了 `err` 走 `http.Error(400)` 还是走「业务信封」。
⇒ ⇒ **同一个 Go 库里，同一类错误，在不同 api 进程里的 wire 形状是不同的。**

### 🔴 教训一：**共享层复刻 Go 时，先问「这条行为在 Go 侧是全局的吗？」**

- ❌ 直觉（本次差点踩）：「Go 的 `httpx.Parse` 失败 ⇒ 400 原文」⇒ 直接在共享层写死。
- ✅ 正确：**先查 Go 侧这个行为是不是「逐进程注册的钩子」** ⇒ 是 ⇒ 共享层**不能有单一形状**
  ⇒ 必须让**域自己**决定怎么渲染。

⇒ **判据**：Go 侧的 `XxxHandler` / `SetXxxHandler` / `WithXxx` 这类**注册式 API**
（`httpx.SetErrorHandler` / `httpx.SetOkHandler` / `rest.WithJwt` / `rest.WithCors` …）
⇒ **逐进程注册 ⇒ 行为按进程而异** ⇒ 复刻它时**必须带一个 per-process 的开关**。

### 🔴 教训二：**servlet `Filter` 抛出的异常到不了 `@RestControllerAdvice`**

本次为了「让域用自己的错误出口渲染信封」，共享层改成**抛异常** ⇒
**Filter 在 DispatcherServlet 之外** ⇒ 抛的异常**不会**被 `@RestControllerAdvice` 接住
⇒ ⇒ 必须做成 Spring MVC 的 **`HandlerInterceptor`**（`preHandle` 位于 `doDispatch` 的 try 块内
⇒ 异常会走 `processHandlerException` ⇒ `ExceptionHandlerExceptionResolver`）。

⇒ **副作用（是修正，不是回归）**：Interceptor **只对匹配到的 handler 生效**
⇒ 未匹配路由（404 / 405）不再触发闸门 ⇒ **与 Go 一致**（Go 的 404 分支不解析 form）。

### 🔴 教训三：**「把本地的判据搬到共享层」会改变它的位置 ⇒ 连锁打穿域里钉住旧位置的测试**

`article-api` 有一条**装配层**守门 `ArticleApiAssemblyWiringTest.effectiveFilterOrderMatchesGoChain`
用 `assertEquals` 钉死了 **Filter 链的完整顺序**（末尾含那个 form 判据 filter）
⇒ 判据从 Filter 变成 Interceptor 后 ⇒ 该断言**过期** ⇒ 变红。

⇒ ⚠️ 这**不是回归**，是**断言过期** ⇒ 按 AGENTS.md §3.3「**先改/删错误断言，再改实现**」处置：
把 Filter 链的期望改成**新结构**，并**另加一条**断言「新的那个 Interceptor **真的进了拦截器链**且排在最前」
⇒ ⇒ 守门强度**不降反升**（从「Filter 链里有一个」升级为「拦截器链里有一个且在最前」）。

### 教训四：**共享层改动若会改别的域的可观察行为 ⇒ 能用开关规避就规避**

本次原方案会**连带改 favorite 的行为**（它本地的判据会先于共享层生效）⇒
最终形态：把「**抬容器上限**」也挂在**同一个 per-process 开关**下 ⇒
`favorite` 缺省 = `off` ⇒ **容器上限也不动、闸门也不装** ⇒ **favorite 行为逐字节不变**
⇒ ⇒ **连「告知该域 owner」这一步都不需要了**。

### 出处

- 踩坑位置：`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤（X-6 第十四轮落地）
- 关联：`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤ 之四（各域可观察行为对照表）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-014 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门

### 现象（第十四轮实测）

对共享层的「非空值计数」做变异（`countNonEmptyValues` 恒返回 0）⇒ 守门**一条都没红**
⇒ 按「§3.3：守门必须做变异测试证明非空转」的纪律，结论应该是「**守门空转**」。

⚠️ **但真因是变异脚本自己写错了**：

```
mvn -Dtest=GoStyleParamLimitGateTest+GoStyleFormLimitEnvelopeGateTest+GoStyleFormLimitModeTest
```

⇒ **surefire 的 `-Dtest` 用逗号分隔，**`+`** 会被当成「类名模式的一部分」
⇒ ⇒ **一个测试类都没匹配上** ⇒ mvn 静默通过（`-DfailIfNoTests=false`）
⇒ ⇒ **「零红」是假阴性**，不是「守门空转」。

改成分隔符正确后重跑 ⇒ **4 条守门转红**（raw 模式的 2049 那条、envelope 模式的 2049 那条、模式分派的 2 条）
⇒ ⇒ 守门**本来就有判别力**。

### 根因

「变异测试」这一步有**两个**独立的失败模式，而它们的现象**长得一模一样**（都是「红测数 = 0」）：

| 失败模式 | 含义 | 处置 |
|---|---|---|
| **守门空转** | 守门**没在**守这条机制 | 补守门 / 改实现 |
| **测试没跑** | 守门**在**守，但这次没被执行到 | 修变异脚本（**不是**改守门） |

⇒ ⇒ 二者必须**先分开**，否则会把好守门当成没用的删掉（而它其实正是在防下一次回归）。

### 判据（三条，缺一不可）

1. **变异脚本必须先断言「变异确实写进去了」**（例如 `assert old in source` 后再写）⇒
   否则「源文本没命中 ⇒ 什么都没改」也会表现为「零红」。
2. **跑完必须看 `Tests run:` 行**，确认**跑了多少条** ⇒ `Tests run: 0` 或**没有该行** ⇒ 就是「没跑」。
3. **`-Dtest` 的多类名分隔符是逗号**（`,`），**不是** `+`；且加 `-DfailIfNoTests=false` 时
   「没匹配上」会**静默通过** ⇒ ⇒ 这条组合最容易制造假阴性 ⇒ **要么别加 `-DfailIfNoTests=false`，要么手动核对 `Tests run:` 的条数**。

### 教训

> **「没转红」是一个需要二次确认的信号，不是结论。**
> 在把它写成「守门空转」之前，先问一句：**这次测试到底跑了没有？**

### 出处

- 踩坑位置：2026-10-25 第十四轮的变异测试 M2（`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤ 之三）
- 关联：`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-013

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **第十四轮新增 PIT-GLOBAL-014** —— 变异测试报「没转红」时**先排除「测试压根没跑」**：surefire 的 `-Dtest` 多类名分隔符是**逗号**不是 `+`，配上 `-DfailIfNoTests=false` 会**静默通过** ⇒ 「零红」是假阴性 ⇒ 差点把好守门当成空转删掉；含三条判据（变异脚本先断言写进去了 / 跑完看 `Tests run:` 条数 / 别把「没匹配上」当通过） |
| 2026-10-25 | **第十四轮新增 PIT-GLOBAL-013** —— Go 的 `httpx.SetErrorHandler` 是**逐 api 注册**的 ⇒ 解析失败的 wire 形状**按域而异**（200+信封 vs 400+原文）且信封文案**在域自己的错误码表**里 ⇒ 共享层**不能有单一形状**；含四条教训：① 复刻 Go 前先问「这条行为在 Go 侧是全局的吗」② servlet Filter 抛的异常到不了 advice ⇒ 必须 Interceptor ③ 搬运判据会改变它的位置 ⇒ 域里钉住旧位置的测试会红（是断言过期，不是回归）④ 共享层若会改别的域行为，能用开关规避就规避（favorite 缺省 off ⇒ 零变化） |
| 2026-10-25 | **第十一轮新增 PIT-GLOBAL-012** —— 「per-app 配置」≠「只能逐模块改」⇒ 第九轮把 X-6 误判成 favorite 边界下的永久阻塞 ⇒ 第十一轮找到共享层修法（`Connector.setMaxParameterCount(-1)` + Boot customizer 顺序 + `com.rag.common` 组件扫描 10/10）⇒ **该漂移可修、favorite 零触碰**；含四问判据 + 「共享层会改别的域行为 ⇒ 不能静默」的教训 |
| 2026-10-25 | **第七轮新增 PIT-GLOBAL-011** —— 裸 `ServerCallHandler` 造的「慢服务」**从不执行** ⇒ task + follow 两个域的同名测试**绿但空转**；含探针实测输出、两种裸 handler 的分类判据、修法与变异测试要求 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 PIT-GLOBAL-005（全量构建偶发红的判定三步 + 基线对照的 worktree 做法）· PIT-GLOBAL-006（真源分层的指针） |
