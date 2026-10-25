<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25（第十九轮）· PIT-GLOBAL-019 补记第 4 种形态（构建中改源码 ⇒ 绿的是旧字节码）
     （首踩于第十八轮：改上游模块的资源做变异 ⇒ 假阴性） -->

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

### 判据（**五条**，缺一不可）

1. **变异脚本必须先断言「变异确实写进去了」**（例如 `assert old in source` 后再写）⇒
   否则「源文本没命中 ⇒ 什么都没改」也会表现为「零红」。
2. **跑完必须看 `Tests run:` 行**，确认**跑了多少条** ⇒ `Tests run: 0` 或**没有该行** ⇒ 就是「没跑」。
3. **`-Dtest` 的多类名分隔符是逗号**（`,`），**不是** `+`；且加 `-DfailIfNoTests=false` 时
   「没匹配上」会**静默通过** ⇒ ⇒ 这条组合最容易制造假阴性 ⇒ **要么别加 `-DfailIfNoTests=false`，要么手动核对 `Tests run:` 的条数**。
4. 🔴 **同一个 reactor 里，上游模块失败 ⇒ 依赖它的下游模块被 Maven 直接跳过**（2026-10-25 第十六轮实测）
   ⇒ 变异打在 `service/common` 上时，`mvn -pl service/common,service/hot/hot-api -fae`
   只会看到 common 的红，**hot-api 根本没跑** ⇒ 「hot-api 没转红」是**假阴性**。
   ⇒ 修法：① **逐模块单独跑**（先把被变异的上游 `install` 进 `~/.m2`），或 ② 逐模块核对 `Tests run:` 条数。
5. 🔴 **还原**不要用 `git checkout -- <file>`（2026-10-25 第十六轮实测）
   ⇒ 它把文件还原到 **HEAD** ⇒ **本轮尚未提交的改动一起被抹掉**
   ⇒ 本轮实测：共享层文件被回滚 ⇒ hot / article 两域的调用点立刻编译不过 ⇒
   **看起来像「变异把代码改坏了」，其实是还原动作干的**。
   ⇒ 修法：变异前 `cp` 一份**备份副本**，还原时 `cp` 回去（**不要**用 git）。

### 教训

> **「没转红」是一个需要二次确认的信号，不是结论。**
> 在把它写成「守门空转」之前，先问一句：**这次测试到底跑了没有？**

⇒ 第十六轮补一问：**这次变异到底写进去了吗？还原动作有没有顺手抹掉别的东西？**

### 出处

- 踩坑位置：2026-10-25 第十四轮的变异测试 M2（`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤ 之三）
- 补充：2026-10-25 第十六轮的变异脚本（M1 只看到 common 的红 ⇒ 下游被跳过；
  且 `git checkout --` 还原把本轮共享层改动一起抹了 ⇒ 判据 4 / 判据 5 由此而来）
- 关联：`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-013

---

## PIT-GLOBAL-015 🔴 **探针测的层 ≠ 端到端跑的层** ⇒ 「解析层漂移」不一定是「端到端漂移」

### 现象（第十六轮实测）

第十六轮用双侧探针查出「Go 的 `ParseJsonBody` 只读前 8 MiB，而 RAG 整个读进堆里」⇒
按「探针红了 = 端到端行为相反」写进台账 ⇒ **修完跑端到端时，hot 的 9 MiB body 返回的是 413，不是 5001**。

⇒ 回头查才看清层次：

```
go-zero v1.10.2 rest/config.go:53   MaxBytes int64 `json:",default=1048576"`   ← 1 MiB 默认值
go-zero v1.10.2 rest/config.go:21   Middlewares.MaxBytes bool `json:",default=true"`
go-zero v1.10.2 rest/engine.go:163-164
    if ng.conf.Middlewares.MaxBytes {
        chn = chn.Append(handler.MaxBytesHandler(ng.checkedMaxBytes(fr.maxBytes)))
    }                                                  ← ← 挂在**引擎**上 ⇒ 对所有路由生效
```

⇒ Go 的 **MaxBytes 默认 1 MiB 且全局生效** ⇒ 9 MiB 的 body **先被拦成裸 413**
（net/http 的 `requestTooLarge()`）⇒ **根本走不到** `ParseJsonBody`
⇒ ⇒ **端到端那条漂移根本不存在**（两侧都是 413）。

### 根因

**一条请求在 Go 侧要穿过好几道闸**，每道闸都可能**提前把请求挡掉** ⇒ 下游那道闸的行为就**不可达**：

```
Tomcat/Go 的请求保护层顺序（Go 侧）
  MaxConns → Breaker → Shedding(CPU) → Timeout → MaxBytes → … → handler 内的 Parse / ParseJsonBody
                                                              ↑ 8 MiB 的 maxBodyLen 在**这一层之后**
```

⇒ **探针直接调最里层的那道闸**（`httpx.Parse`），**端到端跑的是整条链**
⇒ 两者测的**不是同一层** ⇒ 探针的红**不自动等于**端到端的红。

### 🔴 教训一：**报「端到端漂移」之前，先把「端到端跑的是哪条链」写出来**

- 每一层闸门都要标出：**它的阈值 / 它在链上的位置 / 它后面还有没有闸**；
- 只要**后一道闸的阈值更小**，前一道闸在端到端就**不可达** ⇒ 它的差异只能报在**解析层**。

### 🔴 教训二：**「解析层忠实」仍然值得做，但要如实标注它的可达性**

本轮的处置：
- ✅ **照做**（解析层与 Go 对齐）—— 因为解析层是 Go 规则**真正所在的那一层**，
  而共享层的无上限读本来就是隐患；
- ✅ **同时如实写**：「端到端今天**没有任何域**会因它改变行为」；
- ⇒ **不夸大**：既不写成「端到端漂移」（不实），也不写成「没价值因而不用做」（也不对）。

### 🔴 教训三：**端到端守门与解析层守门要分开写**

- 端到端守门断言的是**整条链**的结果（hot 的 9 MiB ⇒ **413**）；
- 解析层守门断言的是**那一层**的行为（直接调用 `requireParsableJsonBody` ⇒ **5001**）；
- ⇒ 把两者混在一条用例里 ⇒ 就会出现「端到端被上一道闸挡掉 ⇒ 断言永远走不到目标分支」的**假绿**。

### 出处

- 踩坑位置：2026-10-25 第十六轮的探针 J20 与 hot 端到端用例（`docs/DRIFT/global/cross-domain.md` §二·十三 ③）
- 关联：PIT-GLOBAL-011（裸 `ServerCallHandler` 造的慢服务**从不执行** ⇒ 绿但空转）——
  **同一种病**：测试没跑到目标分支，却因为「绿」而被当成有覆盖

---

## PIT-GLOBAL-016 🔴 **共享层新增方法后，单独跑下游模块 ⇒ `NoSuchMethodError`**（不是编译错误！）

### 现象（第十六轮实测）

第十六轮给共享层 `GoStyleJsonBodyConverter` 加了一个重载
`goReadsBody(HttpServletRequest)`，然后**单独**跑下游模块：

```
$ mvn -o test -pl service/article/article-api
⇒ java.lang.NoSuchMethodError: 'boolean com.rag.common.http.GoStyleJsonBodyConverter.goReadsBody(jakarta.servlet.http.HttpServletRequest)'
    at com.rag.article.article.api.config.GoStyleJsonBodyLimitFilter.goReadsJsonBody(GoStyleJsonBodyLimitFilter.java:141)
```

⇒ **编译是过的**（article-api 的源码确实调用了新方法）⇒ **报错发生在运行时**
⇒ 而报错点落在**共享层的调用方** ⇒ 一眼看着像「我的新方法写错了」。

### 根因

**单独跑 `-pl <下游模块>`（不带 `-am`、也不把上游模块放进同一个 reactor）时，
Maven 会从本地仓库 `~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar`
解析依赖** —— 那是**上一次 `install` 留下的旧 jar**，里面没有本轮新加的方法。**

⇒ 症状伪装成「运行时错误」，实际是**classpath 指错了**。
⇒ ⚠️ 危险之处：**下游模块里所有经过该调用点的测试会一起红**
（本轮实测 article-api 的 3 个测试类、21 条用例一起红）⇒ 很容易被误判成「我改坏了整个域」。

### 🔴 教训一：**共享层改了 API ⇒ 跑测试必须把上游模块放进同一个 reactor**

```bash
# ✅ 对：service/common 与下游模块在同一个 reactor ⇒ 用的是本轮的 target/classes
mvn -o test -pl service/common,service/article/article-api -fae

# ❌ 错：下游单独跑 ⇒ 从 ~/.m2 拿旧 jar ⇒ NoSuchMethodError
mvn -o test -pl service/article/article-api
```

⇒ 或者先 `mvn -o install -pl service/common` 把新 jar 装进本地仓库。

### 🔴 教训二：**看到 `NoSuchMethodError` 先查 classpath，不要先改代码**

`NoSuchMethodError` / `NoClassDefFoundError` 这两类**运行时**链接错误，
在「上游模块刚改了 API」的场景下，**九成是 classpath 指旧了**，不是逻辑写错。
⇒ 判据：**同一份代码在「把上游放进同一 reactor」后还红不红** ⇒ 还红才是真问题。

### 出处

- 踩坑位置：2026-10-25 第十六轮的 article-api 测试（`docs/DRIFT/global/cross-domain.md` §二·十三 ⑤）
- 同源记录：`tools/semantic-probe/run-parity.sh` 的 classpath 注释（那里已因 users-common 踩过同一类坑，
  解法一样：**用 `target/classes`，不要用 `~/.m2` 里的 jar**）

---

## PIT-GLOBAL-017 🔴 **用「被测的那个常量」构造测试输入 ⇒ 变异常量时测试跟着变 ⇒ 恒绿**

### 现象（第十六轮实测）

第十六轮给共享层加了 Go 的 `maxBodyLen = 8 MiB`（`GO_MAX_BODY_LEN`），
并写了一条边界守门 `GoStyleJsonBodyMaxBodyLenTest`：

```java
private static final int MAX = GoStyleJsonBodyConverter.GO_MAX_BODY_LEN;   // ← 🔴 错
byte[] body = jsonObjectOfSize(MAX);           // 「正好 8 MiB 的合法 JSON」
assertNotNull(read("application/json", body)); // 「必须能解析成功」
```

⇒ 变异测试把 `GO_MAX_BODY_LEN` 从 `8 * 1024 * 1024` 改成 `1 * 1024 * 1024` ⇒
**这条守门一条都没红** ⇒ common 227 条全绿。

### 根因

**测试的输入是由「被测的那个常量」算出来的** ⇒ 常量一改，测试构造的 body 也跟着缩
⇒ 断言「正好上限的 body 必须能过」在**任何**上限下都成立 ⇒ **恒真**。

⇒ 这不是「覆盖不够」，是**断言恒真** ⇒ 比「没有测试」更危险（它给人「已覆盖」的错觉）。

### 🔴 判据：**输入里出现「被测常量」就要警觉**

- 若断言的是「在阈值 X 处恰好翻转」⇒ **阈值必须是字面值**（带 Go 出处注释），
  **不能**引用实现里的常量；
- 若确实要引用常量（例如断言「实现用的就是这个常量」）⇒ 那条断言本身就是**把常量钉死**的，
  而**边界测试必须另用字面值**；
- ⇒ 两者分工：**字面值守边界 · 常量断言守「实现用的就是这个值」**。

### 🔴 顺带一条：**探针守「判定」，边界测试守「阈值」**

同一轮的另一条实测：把上限改成 1 MiB 后，**双侧探针 20/20 仍绿**
⇒ 因为探针比的是**判定**（读 / 不读、拒绝 / 接受），上限从 8 MiB 改成 1 MiB 时
**两侧都判「拒绝」** ⇒ 判定一致 ⇒ 探针无感。

⇒ ⇒ **限值类差异（「同一个判定、不同阈值」）只能由边界测试守** ⇒ 别指望探针。

### 出处

- 踩坑位置：2026-10-25 第十六轮的变异 M2（`docs/DRIFT/global/cross-domain.md` §二·十三 ⑤·二）
- 同族：`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-011（绿但空转）· §PIT-GLOBAL-014（变异「没转红」的三类假阴性）

---

## PIT-GLOBAL-018 🔴 **变异脚本的两种「假绿」：① 编译不过 ⇒ 测试没跑 ⇒ 读到旧 XML；② 红在更早的断言上 ⇒ 目标断言没被执行到**

> **一句话**：变异测试要证明的是「**目标断言**会红」，
> 而不是「**有一个东西**红了」。
> 下面两条都会在**目标断言没参与**的情况下报出「转红」或「没转红」。

### ① 变异编译不过 ⇒ maven 压根没跑测试 ⇒ 你读到的是**上一轮**的 XML

**实测**（2026-10-25 第十七轮，X-5 的变异 M1）：

```java
// 首版 M1 变异：想去掉 server.intercept(...)
return server -> server;   // 🔴 与 void 函数接口不兼容 ⇒ 编译不过
```

```
[ERROR] 不兼容的类型: lambda 主体与 void 函数接口不兼容
[INFO] BUILD FAILURE
```

⇒ **maven 不会跑任何测试** ⇒ `target/surefire-reports/` 里留着**上一次成功运行**的 XML
⇒ 脚本「读 XML 看 failures」⇒ **读到旧结果 ⇒ 报成 GREEN**
⇒ 而真实情况是「这次一次测试都没跑」。

⚠️ 这个假绿**方向最恶劣**：它会让人写下「**这条守门对本次变异无感**」的结论，
而实际上**守门根本没被问过**。

⇒ **三道闸（缺一不可）**：

| # | 闸 | 为什么 |
|---|---|---|
| 1 | **跑前先删旧 XML**（`find … -name 'TEST-*.xml' -delete`） | 杜绝「读到上一轮结果」 |
| 2 | **看 maven 退出码** | 编译失败 ⇒ 非 0 ⇒ 立刻知道没跑 |
| 3 | **grep `COMPILATION ERROR` 并直接判本次判定无效** | 退出码非 0 也可能是「测试真的红了」⇒ 必须把两种情况分开 |

⇒ **顺带一条**：变异代码**必须能编译**。
「把 `server -> server.intercept(x)` 换成 `server -> server`」这种直觉写法
在**块 lambda**（`Consumer<ServerBuilder<?>>`）下**编译不过** ⇒ 要写成
`server -> { /* 空实现 */ }`。

### ② 红在**更早**的断言上 ⇒ 目标断言**压根没被执行到**

**实测**（同一轮，X-5 的变异 M3 首版）：

| 变异 | 期望 | 实际红在哪 |
|---|---|---|
| 首版：只把 `probe.release.await(30, SECONDS)` ⇒ `await(0, SECONDS)` | 新增的 `assertFalse(probe.completed.get())` 转红 | 🔴 红在**更早**的 `assertThrows(...)` ——「Expected StatusRuntimeException to be thrown, but nothing was thrown」 |

原因：handler 秒完成**并且正常回包** ⇒ 客户端拿到 **OK** ⇒ 压根没有超时可断言
⇒ 流程在 `assertThrows` 就短路了 ⇒ **新增的那条断言一次都没执行**。

⇒ 修正：让 handler **完成但不回包**（去掉 `observer.onNext/onCompleted`）
⇒ 超时照常触发 ⇒ 才红在**目标断言**上。

⇒ **判据**：变异跑完后，**看红的那条断言的失败消息**，
确认它**就是**你要验的那条，而不是它前面某条。

### 出处

- 踩坑位置：2026-10-25 第十七轮的变异 M1 / M3（`docs/DRIFT/global/cross-domain.md` §二·八 ⑤·三）
- 同族：§PIT-GLOBAL-014（变异「没转红」的三类假阴性）· §PIT-GLOBAL-017（用被测常量构造输入 ⇒ 恒绿）

---

## PIT-GLOBAL-019 🔴 **改「上游模块的资源」做变异，而只跑下游 ⇒ 变异到不了运行代码 ⇒ 报成 GREEN（假阴性）**

> **一句话**：变异测试的「没转红」有两种：① **守门真的不灵** ② **变异根本没跑起来**。
> 本条是 ② 的一个**很隐蔽**的变种：改的代码**编译通过**、**测试也跑了**，
> 只是**跑的不是你改的那份**。

### 实测（2026-10-25 第十八轮，B10 的变异 M2）

| 事实 | 后果 |
|---|---|
| `users-common/src/main/resources/db/01_create_user.sql` 是 **`users-common` 的**资源 | 单独跑 `admin-rpc` 时，Maven 从 `~/.m2` 解析**旧的** `users-common` jar |
| 变异改的是 **`users-common` 的源文件** | ⇒ admin-rpc 运行时 classpath 上的 DDL **是旧版本** ⇒ **改动压根到不了运行代码** |
| ⇒ 变异跑完，测试**照样全绿** | ⇒ 报成 **GREEN** ⇒ 若照着这个结论写台账，就会写成「**这条守门对 DDL 回退无感**」⇒ **结论完全错**（守门其实很灵） |

### ⇒ 修法：把上游模块**拉进同一个 reactor**

```bash
# ❌ 错：只跑下游 ⇒ 上游从 ~/.m2 解析旧 jar ⇒ 资源是旧的
mvn -o test -pl service/users/admin/admin-rpc -Dtest='XxxTest'

# ✅ 对：上游进同一个 reactor ⇒ Maven 用 reactor 的 target/classes（含被改的资源）
mvn -o test -pl service/users/users-common,service/users/admin/admin-rpc -Dtest='XxxTest'
```

### ⚠️ 顺带一个 **surefire 参数换代**（本轮实测踩到）

`-DfailIfNoTests=false` **管不住** `-Dtest` 模式 ⇒ 上游模块会因为
「**没匹配到测试**」直接 **BUILD FAILURE**：

```
[ERROR] No tests matching pattern "AdminRpcColumnTypeParityTest" were executed!
        (Set -Dsurefire.failIfNoSpecifiedTests=false to ignore this error.)
```

⇒ 必须加 **`-Dsurefire.failIfNoSpecifiedTests=false`**。

⚠️ **注意这条与 §PIT-GLOBAL-014 是同一族但方向相反**：
PIT-GLOBAL-014 讲的是「`-DfailIfNoTests=false` 会**静默通过** ⇒ 假阴性」
⇒ 本条讲的是「`-DfailIfNoTests=false` **挡不住** `-Dtest` 的失败 ⇒ 直接 BUILD FAILURE」
⇒ **两个参数名要分清**：前者是「没有测试可跑」，后者是「指定的测试没匹配上」。

### ⇒ 判据（怎么知道我的变异到底跑没跑）

1. 变异改的文件**属于哪个模块**？那个模块**在 reactor 里吗**？
2. 跑完**核对测试条数**（`Tests run:`）与**基线**一致吗？
3. 变异是**资源 / DDL / 配置**吗？⇒ 那它**跟着 jar 走**，不是跟着 class 走。

### 🔴 2026-10-25 第十九轮补记：**第 4 种形态** —— 「构建**进行中**我改了源码」⇒ 绿的是**旧字节码**

**现象**（本轮实测）：全仓 `mvn -o test -fae` **正在跑**的时候，我补了一个注解
（`GoStyleTraceparentFilter` 加 `@ConditionalOnWebApplication(SERVLET)`）。
那轮构建 **BUILD SUCCESS · 2191 条全绿** —— 但**跑的不是改完的代码**：

```
10:04:29  service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java   ← 源码
10:03:04  service/common/target/classes/com/rag/common/http/GoStyleTraceparentFilter.class  ← 字节码（更旧！）
```

⇒ `common` 模块在 reactor 里排**第 7 位**（早）⇒ 那轮构建在**我改源码之前**就编译完了 `common`
⇒ **后面 30 个模块跑的都是旧字节码** ⇒ 「全仓绿」这句话当时**是假的**（对这次改动而言）。

**判据（一行）**：

```bash
stat -f "%Sm  %N" -t "%H:%M:%S" \
  service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java \
  service/common/target/classes/com/rag/common/http/GoStyleTraceparentFilter.class
# class 的时间**必须新于** source；否则 ⇒ 那轮构建没覆盖这次改动 ⇒ **重跑**
```

⇒ **通用化**：**「跑绿」≠「跑的是你现在的代码」**。本条把「假绿」的来源从
「测试没跑 / 跑的不是改过的那份 / 读的是旧结果」补到第 4 种：
**「跑的是旧字节码」** —— 而它**最难发现**，因为它不报错、不留痕、**日志完全正常**。

⇒ **纪律**：**构建开始后不要再改源码**；若非改不可 ⇒ **改完必须重跑**，并用上面那行 `stat` 核对。

### 出处

- 踩坑位置：2026-10-25 第十九轮的 T-8 收尾（全仓回归时中途补注解 ⇒ 第一轮全仓绿**不算数** ⇒ 重跑了一遍）
- 首踩位置：2026-10-25 第十八轮的变异 M2（`docs/DRIFT/global/cross-domain.md` §二·十四 ⑥）
- 同族：§PIT-GLOBAL-016（单独跑下游 ⇒ 运行时 `NoSuchMethodError`）· §PIT-GLOBAL-014（surefire 多类名分隔符）· §PIT-GLOBAL-018（编译不过 ⇒ 旧 XML ⇒ 假绿）

---

## PIT-GLOBAL-022 🔴 **抄来的「同形态先例」编号也要复核** —— 指针不是证据，它本身是一条**待核对的断言**

### 现象（2026-10-25 第二十六轮 L5 实测）

`docs/DRIFT/global/_README.md` §七之二 的 **C3** 行原文：

> | **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无） | 刻意保留（同 message 域 `SD-2026-10-23-06` 已签的同形态） |

⇒ 按这个指针去 `docs/Intentional_Deviations/message/rpc.md` 找 `SD-2026-10-23-06`
⇒ 那一条讲的是 **`idx_notification_recipient_read` 的列数（Go 1 列 / RAG 2 列）**，
**与 `DEFAULT CURRENT_TIMESTAMP` 毫无关系** ⇒ **指针是错的**。

### 根因（两条，互相叠加）

1. **台账里的「同形态先例」指针是「抄来的」，不是「当场核对过的」** ——
   它和「Go 侧的结论」一样，**是需要证据的断言**，却因为长得像「引用」而被当成了证据；
2. **编号不可寻址** —— 同一个 `SD-2026-10-23-06` 在 **3 个域各有含义**
   （`like/rpc.md` / `message/rpc.md` / `users/admin/rpc.md`，见
   [`docs/Intentional_Deviations/_INDEX.md`](../../Intentional_Deviations/_INDEX.md) §编号撞号）
   ⇒ **只凭编号检索必然有歧义**，检索命中 ≠ 讲的同一件事。

### 正确做法（三条）

1. **引用任何「先例编号」之前，先打开被引条目核对它的标题** ——
   判据一行：**被引条目的标题必须与当前条目讲的是同一件事**（同一个编号不算）；
2. **引用时写「文件 + 域 + 编号」三键**，不要只写编号；
3. **找不到同形态的已签先例 ⇒ 就写「本条是首次登记」** ——
   **宁可留空，也不要挂一个没核对过的指针**（错的指针比空指针更贵：它会让下一个人以为「有人已经踩过这条坑了」）。

### 核对后的**真实**同形态先例（本轮实际用到的两条）

| 先例 | 位置 | 形态 |
|---|---|---|
| `SD-2026-10-24-04` | `docs/Intentional_Deviations/message/rpc.md:80` | 6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧**多出来的**（Go 侧无默认）✅ **已签字** |
| `SD-2026-10-24-CAND-01` | `docs/Intentional_Deviations/comment/rpc.md:22` | 9 处时间列的 `NOT NULL DEFAULT CURRENT_TIMESTAMP`（RAG 更严）⏳ **未签字候选** |

⇒ ⇒ 本轮登记的 `SD-2026-10-25-CAND-16`（users 域 8 处 DEFAULT）**挂的是上面这两条**，不是 `_README.md` 原先写的那个。

### 教训

**台账里的「同形态先例」指针是待核对的断言，不是证据。**
⇒ 与 AGENTS.md §3.3 的「凡写『与 Go 一致』必须有两侧同时跑的探针」**同源**：
**结论需要证据，指向结论的指针也需要证据。**
⇒ **抄来的指针也要复核** —— 这是「对照纪律」在**台账之间**的延伸：
本项目的漂移台账互相引用极多，**引用链越长，未核对的指针越容易变成事实上的幻觉**。

### 出处

- 首踩位置：2026-10-25 第二十六轮（L5 lane `align/global-signoff`）——
  复核 `docs/DRIFT/global/_README.md` §七之二 C3 行时；
- 连带订正：`docs/DRIFT/global/_README.md` §七之二 C3 行的先例指针
  （**共享文件** ⇒ 补丁写在 [`docs/DRIFT/global/lanes/L5-signoff.md`](../../DRIFT/global/lanes/L5-signoff.md) §五，交整合者）；
- 关联：`docs/Intentional_Deviations/_INDEX.md` §编号撞号 · `docs/Pitfalls/global/common-tails.md` PIT-GLOBAL-001（Go 出处只查项目树会落空）

---

## 变更记录

| 日期 | 动作 |
|---|---|---|
| 2026-10-25 | 🔴 **第二十六轮新增 PIT-GLOBAL-022** —— **抄来的「同形态先例」编号也要复核**：`_README.md` §七之二 C3 行写「同 message 域 `SD-2026-10-23-06` 已签的同形态」，而 message 域的 `SD-2026-10-23-06` 讲的是**索引列数**、与 `DEFAULT CURRENT_TIMESTAMP` **毫无关系** ⇒ 根因 = ① 台账里的「同形态先例」指针是**抄来的**、不是**当场核对过的** ② 编号**不可寻址**（同一个号在 3 个域各有含义）⇒ 纪律：**引用先例前必须打开被引条目核对标题** + **引用写「文件+域+编号」三键** + **找不到同形态先例就写「首次登记」**（错的指针比空指针更贵）⇒ 核对后的真实先例 = `message/rpc.md:80` 的 `SD-2026-10-24-04` + `comment/rpc.md:22` 的 `SD-2026-10-24-CAND-01` |
| 2026-10-25 | 🔴 **第十九轮补记 PIT-GLOBAL-019 的第 4 种形态** —— **「构建进行中我改了源码」⇒ 全仓绿的是「旧字节码」**：全仓 `mvn -o test -fae` 跑到一半时补了个注解（`@ConditionalOnWebApplication`）⇒ 那轮 **BUILD SUCCESS · 2191 条全绿**，但 `common` 模块在 reactor 里排第 7 位（早）⇒ **后面 30 个模块跑的都是改动前的字节码** ⇒ 「全仓绿」当时**是假的**；⇒ 判据一行：`stat` 比对 `target/classes/*.class` 与 `src/main/java/*.java` 的 mtime（class 必须新于 source，否则**重跑**）；⇒ 纪律：**构建开始后不要再改源码**，非改不可 ⇒ **改完必须重跑** + 核对 mtime；⇒ 本条把「假绿」的来源从「测试没跑 / 跑的不是改过的那份 / 读的是旧结果」补到第 4 种，且它**最难发现**（不报错、不留痕、日志全正常） |
| 2026-10-25 | **第十八轮新增 PIT-GLOBAL-019** —— **改「上游模块的资源」（DDL / 配置）做变异，而只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 变异到不了运行代码 ⇒ 报成 **GREEN 假阴性****（本轮 M2 首跑实测：编译通过、测试也跑了、只是跑的不是改过的那份）** ⇒ 修法 = 把上游拉进**同一 reactor**；顺带一个 **surefire 参数换代**：`-DfailIfNoTests=false` **管不住** `-Dtest` 模式（上游模块会直接 BUILD FAILURE）⇒ 要加 **`-Dsurefire.failIfNoSpecifiedTests=false`**（与 PIT-GLOBAL-014 同族但**方向相反**：一个静默通过、一个直接失败）|
| 2026-10-25 | **第十七轮新增 PIT-GLOBAL-018** —— **变异脚本的两种「假绿」**：① **变异编译不过 ⇒ maven 压根没跑测试 ⇒ surefire 目录里留着上一轮的 XML ⇒ 脚本读到旧结果 ⇒ 报成 GREEN**（本轮 M1 首版实测：`server -> server` 与 void 函数接口不兼容）⇒ 假绿方向最恶劣（会让人写下「守门无感」的结论，而守门根本**没被问过**）⇒ 三道闸：**跑前删旧 XML + 看 maven 退出码 + grep `COMPILATION ERROR` 直接判本次无效**；② **红在更早的断言上 ⇒ 目标断言压根没被执行到**（本轮 M3 首版实测：`await(0)` 让 handler 秒完成**并正常回包** ⇒ 客户端拿到 OK ⇒ 短路在 `assertThrows`）⇒ 判据：**看红的那条断言的失败消息，确认它就是目标断言** |
| 2026-10-25 | **第十六轮新增 PIT-GLOBAL-017** —— **用「被测的那个常量」构造测试输入 ⇒ 变异常量时测试跟着变 ⇒ 恒绿**（本轮变异 M2 实测 0 红）⇒ 断言恒真比没测试更危险；含判据（阈值边界**必须用字面值** + 常量断言单独钉住实现）+ 一条探针的能力边界（**探针守「判定」，边界测试守「阈值」**） |
| 2026-10-25 | **第十六轮新增 PIT-GLOBAL-016** —— 共享层新增方法后**单独**跑下游模块 ⇒ **运行时** `NoSuchMethodError`（**不是编译错误**）⇒ 根因是 Maven 从 `~/.m2` 解析**旧的** `common` jar ⇒ 下游 3 个测试类 21 条用例一起红 ⇒ 极易误判成「我改坏了整个域」⇒ 含两条教训：① 跑测试要把上游模块放进**同一个 reactor**（或先 `install`）② 看到 `NoSuchMethodError` / `NoClassDefFoundError` **先查 classpath**，别先改代码 |
| 2026-10-25 | **第十六轮新增 PIT-GLOBAL-015** —— **探针测的层 ≠ 端到端跑的层** ⇒ 探针直接调 `httpx.Parse`（最里层），端到端跑的是整条链 ⇒ Go 的 `MaxBytes`（默认 1 MiB、**引擎级全局生效**）先把 9 MiB body 拦成 413 ⇒ **端到端那条漂移根本不存在**（两侧都是 413）⇒ 差点把「解析层漂移」写成「端到端漂移」；含三条教训：① 报端到端漂移前先把链和每道闸的阈值写出来 ② 解析层忠实仍值得做，但要如实标注可达性 ③ 端到端守门与解析层守门要分开写（混着写会假绿） |
| 2026-10-25 | **第十四轮新增 PIT-GLOBAL-014** —— 变异测试报「没转红」时**先排除「测试压根没跑」**：surefire 的 `-Dtest` 多类名分隔符是**逗号**不是 `+`，配上 `-DfailIfNoTests=false` 会**静默通过** ⇒ 「零红」是假阴性 ⇒ 差点把好守门当成空转删掉；含三条判据（变异脚本先断言写进去了 / 跑完看 `Tests run:` 条数 / 别把「没匹配上」当通过） |
| 2026-10-25 | **第十四轮新增 PIT-GLOBAL-013** —— Go 的 `httpx.SetErrorHandler` 是**逐 api 注册**的 ⇒ 解析失败的 wire 形状**按域而异**（200+信封 vs 400+原文）且信封文案**在域自己的错误码表**里 ⇒ 共享层**不能有单一形状**；含四条教训：① 复刻 Go 前先问「这条行为在 Go 侧是全局的吗」② servlet Filter 抛的异常到不了 advice ⇒ 必须 Interceptor ③ 搬运判据会改变它的位置 ⇒ 域里钉住旧位置的测试会红（是断言过期，不是回归）④ 共享层若会改别的域行为，能用开关规避就规避（favorite 缺省 off ⇒ 零变化） |
| 2026-10-25 | **第十一轮新增 PIT-GLOBAL-012** —— 「per-app 配置」≠「只能逐模块改」⇒ 第九轮把 X-6 误判成 favorite 边界下的永久阻塞 ⇒ 第十一轮找到共享层修法（`Connector.setMaxParameterCount(-1)` + Boot customizer 顺序 + `com.rag.common` 组件扫描 10/10）⇒ **该漂移可修、favorite 零触碰**；含四问判据 + 「共享层会改别的域行为 ⇒ 不能静默」的教训 |
| 2026-10-25 | **第七轮新增 PIT-GLOBAL-011** —— 裸 `ServerCallHandler` 造的「慢服务」**从不执行** ⇒ task + follow 两个域的同名测试**绿但空转**；含探针实测输出、两种裸 handler 的分类判据、修法与变异测试要求 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 PIT-GLOBAL-005（全量构建偶发红的判定三步 + 基线对照的 worktree 做法）· PIT-GLOBAL-006（真源分层的指针） |

---

## PIT-GLOBAL-021 🔴 **变异判定只认 surefire 的 XML —— stdout 的 `-- in` 行在类有 `@DisplayName` 时是「显示名」不是 FQN**

### 现象

2026-10-25 第二十二~二十四轮写变异脚本时，第一版判定正则**只匹配 surefire 打印的全限定名**：

```
[INFO] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0 ... -- in com.rag.like.like.rpc.LikeFactDispatchContractGateTest
```

⇒ 但**类上有 `@DisplayName` 时**，surefire 打印的是**显示名**：

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.825 s -- in 🔴 真 Tomcat：JSON 响应的 Content-Type 必须带 charset=utf-8（Go: application/json; charset=utf-8）
```

⇒ ⇒ **红与绿都读不出来** ⇒ 脚本误报 `NO_RESULT`
⇒ ⇒ 看着像「守门压根没被触达」（其实**红了，只是读不出来**）
⇒ ⇒ 若就此收手 ⇒ **会误删一个好守门**（正是 **PIT-GLOBAL-014** 说的那个坑）。

### 根因

判定用了 **stdout 的 `-- in` 行**做锚点，而那一行的格式**取决于类上有没有 `@DisplayName`**：
- 无 `@DisplayName` ⇒ 打印 **FQN**（`com.rag.like.like.rpc.LikeFactDispatchContractGateTest`）；
- 有 `@DisplayName` ⇒ 打印**显示名**（可能是中文 + emoji + 括号）。

⇒ **判定锚点不能建立在「人写的、可变的」文本上。**

### 正确处置

**判定只认 surefire 的 XML**（逐条 `testcase`）：

```python
# ✅ 判定锚点 = XML 里 testcase 的 classname/name（机器生成的，稳定）
root = ET.parse('target/surefire-reports/TEST-<FQN>.xml').getroot()
for tc in root.iter('testcase'):
    if tc.find('failure') is not None or tc.find('error') is not None:
        failed.append(tc.get('name'))
```

⇒ XML 的文件名与 `testcase/@classname` 都是 **FQN** ⇒ 与 `@DisplayName` 无关 ⇒ **稳定可寻址**。

⚠️ 配套（**PIT-GLOBAL-018** 的三道闸仍然要跑）：
① **跑前删旧 XML**（否则读到上一轮的）；② **看 maven 的退出码**（不是看脚本自己的判定）；
③ **grep `COMPILATION ERROR`**（编译失败 ⇒ 测试压根没跑 ⇒ XML 是旧的 ⇒ 报成绿）。

### 教训

**判定锚点必须选「机器生成且不受人写文本影响」的那一份。**

⇒ 同族：
- **PIT-GLOBAL-018**（变异脚本的两种假绿）——
  那条讲「**没跑**」被当成「**红了**」；本条讲「**跑了但读不出来**」被当成「**没跑**」；
- **PIT-GLOBAL-014**（报「没转红」时先排除「测试压根没跑」）——
  本条是它的**姊妹坑**：先排除「**判定读不出来**」，再排除「测试没跑」。

### 出处

- 首踩位置：2026-10-25 第二十二轮（超时响应撕裂）的**第一版变异脚本** ⇒ 判定行全报 `NO_RESULT`，而 XML 逐条判定是对的；
- 补充确认：第二十四轮（Go 侧活体探针）再次踩到 ⇒ 类上有 `@DisplayName` 的守门一律读不出 FQN；
- 关联：`docs/DRIFT/global/lanes/L1-delivery.md` §十三·13.7（提出）
  · `docs/DRIFT/global/lanes/_PROTOCOL.md`（PIT-GLOBAL-020 / 变异流程）
  · `docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-014 / 018 / 019。

---

## PIT-GLOBAL-026 🔴 **`GlobalOpenTelemetry.set(sdk)` 存的是「包装器」不是 sdk ⇒ 断言 `get() == sdk` 永远不成立**

> 第二十七轮 · **整合者自曝错账 #4**（我自己写出来的坑 ⇒ 记在这里，因为它会骗过每一个想断言「全局 SDK 装对了」的人）。

### 现象

给 slow 轴守门补一条「前提断言」时，写成：

```java
assertSame(testSdk, GlobalOpenTelemetry.get(), "前提不成立：…");
```

⇒ **两条用例全红**，红在「前提不成立」，而**前提其实是成立的** ⇒ 一眼看过去像「生产代码有缺陷」，
其实**是断言写错了**。

### 根因

OTel Java **1.55.0** 的 `GlobalOpenTelemetry.set(...)` 实现：

```java
public static void set(OpenTelemetry openTelemetry) {
  synchronized (mutex) {
    if (globalOpenTelemetry != null) { throw new IllegalStateException("GlobalOpenTelemetry.set has already been called. …"); }
    globalOpenTelemetry = new ObfuscatedOpenTelemetry(openTelemetry);   // ← 存的是**包装器**
    setGlobalCaller = new Throwable();
  }
}
```

⇒ `get()` 返回的是 `new ObfuscatedOpenTelemetry(sdk)` ⇒ **永远不等于 `sdk` 本身**
（包装器的设计目的就是**阻止**调用方用 `==` 去比较底层实例）。

### 正确处置

要比就比 **`TracerProvider`**（包装器会**原样委托**下去 ⇒ 拿到的是同一个实例）：

```java
assertSame(testSdk.getTracerProvider(), GlobalOpenTelemetry.get().getTracerProvider(), "前提不成立：…");
```

### 教训

**「断言某个全局单例是不是我装的那个」之前，先读那个单例的 `set()` 实现 ——
`set()` 存进去的**未必**是你传进去的那个对象**（包装 / 装饰 / 代理 是常见手法）。

⇒ 同族：**「引用相等」类断言在有包装的单例上恒红** ⇒ 看到「引用相等」失败，
第一反应应该是「**是不是包装层**」，而不是「**是不是生产代码坏了**」。

### 出处

- 首踩位置：2026-10-26 第二十七轮整合阶段 · 给 7 份 slow 轴守门补前提断言时；
- 证据：`opentelemetry-api-1.55.0-sources.jar` 的 `io/opentelemetry/api/GlobalOpenTelemetry.java`（`set` / `get`）；
- 关联：`docs/DRIFT/global/lanes/L6-x5gate.md` §7.3（缺口④ 复评 ⇒ 前提是「侥幸成立」）
  · `docs/DRIFT/global/lanes/L9-interrupt.md` §七·五。

---

## PIT-GLOBAL-027 🔴 **守门的「快调用」用例可能因「首调类加载开销」而首调就红 ⇒ 绿是执行顺序的副产品**

> 第二十七轮 · L9 实测撞到 ⇒ **跨 6 条 lane 的共性缺陷**（整合者统一修）。

### 现象

L9 写 `follow-rpc` 的 slow 轴守门时，「快调用必须**没有** `sea.slow`」那条**首调就红**：

```
sea.duration_ms = 138.333   （阈值 100ms）
```

⇒ 原因不是 slow 判定写错了，而是 **JVM 里的第一次 gRPC 调用**要把
protobuf 编解码 / netty 缓冲 / 拦截器链的类**全部加载一遍** ⇒ **这些成本落在 span 的 duration 里**。

### 为什么这条特别阴

第二十六轮之前，**全仓 5 份 slow 轴守门都没有预热**（admin / article / user / message / hot）
⇒ 它们的「快调用」用例之所以是绿的，**只因为 JUnit 默认按声明序执行** ⇒ **慢用例先跑**把类预热了。

⇒ 换句话说：**那些绿是「执行顺序」的副产品，不是守门本身的性质**。
一旦有人调整方法顺序、加一个新用例在前、或换 surefire 的执行顺序 ⇒ 立刻红。

### 正确处置

**每个要断言「快」的守门，在被测调用之前先做一次预热调用**，然后**清空 exporter** 再做被测调用：

```java
// 预热：把这条路径的类加载成本一次性付掉
stub.withDeadlineAfter(10, SECONDS).call(req);
EXPORTER.clear();
// ……再做被测调用
```

⚠️ **预热必须用独立的 probe / 独立的 server**：
若被测路径的「handler 真的被调用过」证据是一次性的 `CountDownLatch` ⇒ 预热会把它消耗掉 ⇒
那条判别式断言**永远为真** ⇒ 守门变空转。

### 教训

**「守门是绿的」必须问一句「它绿得**独立**吗」** ——
凡是用到**执行顺序**才能成立的绿，都是**顺序依赖的假稳定**。

⇒ 自检口径：**把「被测的那个用例」单独跑一遍**（`-Dtest=Class#方法`）⇒ 若单独跑就红 ⇒ 它是顺序依赖的。

### 出处

- 首踩位置：2026-10-26 第二十七轮 L9（`follow-rpc` 的 fast 用例首调 138.333ms）；
- 扩散面：第二十七轮整合者核查发现 **7 份 slow 轴守门里有 5 份没有预热**（admin / article / user / message / hot）⇒ 已统一补；
- 关联：`docs/DRIFT/global/lanes/L9-interrupt.md` §七·五（N2）
  · `docs/DRIFT/global/lanes/L8-slowgate2.md` §7.4（同类取值陷阱）。

---

## PIT-GLOBAL-028 🔴 **slow 阈值的「配置被忽略」是可静默发生的退化 ⇒ 守门的「占用时长」必须 < Go 的兜底阈值**

> 第二十七轮 · L8 发现 ⇒ 影响**所有** slow 轴守门的设计。

### 现象

slow 判定的阈值来自两处：RAG 的 `observability.slow-threshold` 配置，**兜底**是 Go 的
`DefaultSlowThreshold = 500ms`（`observability.go:21`）。

⇒ 若某域把「本域配置」**忽略了**（回退到 500ms 兜底），那么：
- 一个**占用 1000ms** 的慢调用 ⇒ 阈值 500ms ⇒ **照样**会被打上 `sea.slow` ⇒ **任何只看「有没有 `sea.slow`」的守门都抓不住**。

### 正确处置

守门里那个「模拟慢业务」的**占用时长**必须落在**窄区间**：

```
Go 兜底阈值 (500ms)  >  占用时长  >  本域阈值
```

⇒ 这样「阈值被忽略」⇒ 占用时长不再超过阈值 ⇒ **快用例那条会红**（或慢用例那条会红）⇒ 退化被抓住。

实例（第二十七轮的取值）：
- admin / article / comment / like：阈值 100ms · 占用 **400ms**（400 < 500 ⇒ 抓得住）；
- user / message / hot：阈值 200ms · 占用 **300ms**；
- task / follow：阈值 100ms · 占用 **400ms**。

### 教训

**守门里出现的「阈值」类常量，不能只对「被测阈值」敏感 —— 还要对「阈值被换掉」敏感。**
⇒ 用例的取值区间要**卡在两个阈值的中间**，让退化可观察。

### 出处

- 首踩位置：2026-10-26 第二十七轮 L8（把占用时长从「≥500ms」收到 300ms ⇒ 变异 C「忽略本域配置」才抓得住）；
- 关联：`docs/DRIFT/global/lanes/L8-slowgate2.md` §7.4；
- 同族：`docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-027（同样是「守门绿得不独立」）。
