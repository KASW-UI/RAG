<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25（第十九轮）· PIT-GLOBAL-019 补记第 4 种形态（构建中改源码 ⇒ 绿的是旧字节码）
     （首踩于第十八轮：改上游模块的资源做变异 ⇒ 假阴性） -->

# global · 跨域踩坑档（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：跨域的**方法论级**坑 —— 一次会横跨多个域、或影响所有域的判断失误。

---

## PIT-GLOBAL-005 ⚠️ **全量构建偶发红 ≠ 你的改动坏了**（判定要靠对照实验，不是靠猜）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 「跑红 ≠ 坏」的三步定性是本轮读任何守门退出码的前置 ⇒ 判据本身仍适用。
> **旧号**：`PIT-GLOBAL-005` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-05`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确的判定序列（三步）＋ `docs/DRIFT/global/vs-common.md` §4.11（`common` 整模块绿 330/0/0/0 ⇒ flake 不复现）

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

## PIT-GLOBAL-006 ⚠️ **「Go 出处」要分两层，只查项目树必然落空**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— Go 出处分 L1 项目层 / L2 go-zero 框架层两层 ⇒ 本轮每条 Go 出处仍须标层。
> **旧号**：`PIT-GLOBAL-006` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-06`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/DRIFT/global/cross-domain.md` §一·1.1（L1/L2 两层表）

⇒ 详细记录见 [`docs/Pitfalls/global/common-tails.md`](common-tails.md) PIT-GLOBAL-001
⇒ 本档只留一句：**凡涉及「请求保护层 / body 解析 / RPC 中间件」的 Go 事实，
去 `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` 找，别在 `Sea-RideTheWind_golang` 里 grep。**

---

## PIT-GLOBAL-011 🔴 **裸 `ServerCallHandler` 造的「慢服务」从不执行** ⇒ 测试绿但**空转**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— task / follow 两域已换标准派发入口，但 admin / article / comment / like / favorite **仍只有纯 mock** ⇒ 触发条件仍在。
> **旧号**：`PIT-GLOBAL-011` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-11`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/DRIFT/global/_README.md` §五「裸 `ServerCallHandler`」行（⛔ 5 域仍只有纯 mock）＋ 本条 §正确处置

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
- 关联：`docs/DRIFT/global/cross-domain.md` §二·八（CD-5）
- 一次性探针：`/tmp/probe-handler/Probe.java`（不入仓）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-012 🔴 **「这个配置是 per-app 的」≠「只能逐模块改」⇒ 会把可修的漂移误判成域边界阻塞**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 「per-app 配置 ⇒ 只能逐模块改」这个推断在本轮判 favorite 边界时仍会被用。
> **旧号**：`PIT-GLOBAL-012` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-12`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/DRIFT/global/_README.md` §三 T-1 行（共享层修法 `Connector.setMaxParameterCount`）

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

- 踩坑位置：`docs/DRIFT/global/cross-domain.md` §二·十一（CD-6）第九轮结论（**已作废**）→ 第十一轮改判
- 关联：`docs/DRIFT/global/cross-domain.md` §二·十一 ③ 之二（共享层修法的逐条取证）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-013 🔴 **Go 的行为常常是「逐 api 注册」的 ⇒ 共享层复刻它时不能假设「Go 这一条是全局一致的」**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— Go 的 `httpx.SetErrorHandler` 仍是**逐 api 注册** ⇒ 共享层复刻前仍须逐域查。
> **旧号**：`PIT-GLOBAL-013` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-13`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/DRIFT/global/cross-domain.md` §二·十一（各域注册表）

### 现象（第十四轮实测）

第十四轮落地 CD-6（`DRIFT-HOTAPI-030`）时，第十三轮的修法草案写着「共享 Filter 统一写 Go 的 **400 原文**」。
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

- 踩坑位置：`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤（CD-6 第十四轮落地）
- 关联：`docs/DRIFT/global/cross-domain.md` §二·十一 ⑤ 之四（各域可观察行为对照表）
- 2026-10-25，`align/global`

---

## PIT-GLOBAL-014 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮以守门退出码作证据 ⇒「没转红」必须先排除「测试压根没跑」。
> **旧号**：`PIT-GLOBAL-014` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-14`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §判据 1~5

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

## PIT-GLOBAL-015 🔴 **探针测的层 ≠ 端到端跑的层** ⇒ 「解析层漂移」不一定是「端到端漂移」  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮把 E65 的结论建立在**探针**读数上 ⇒「探针测的层 ≠ 端到端跑的层」直接适用。
> **旧号**：`PIT-GLOBAL-015` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-15`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §教训一~三 ＋ `docs/PhaseGoal-judge.md` §五 X-6（「PASS 的含义是漂移形态仍在」）

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

## PIT-GLOBAL-016 🔴 **共享层新增方法后，单独跑下游模块 ⇒ `NoSuchMethodError`**（不是编译错误！）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮只改台账文档、**不跑 maven** ⇒「单独跑下游模块」这个触发动作本轮不发生。
> **旧号**：`PIT-GLOBAL-016` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-16`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §根因（`~/.m2` 旧 jar）

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

## PIT-GLOBAL-017 🔴 **用「被测的那个常量」构造测试输入 ⇒ 变异常量时测试跟着变 ⇒ 恒绿**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不做变异** ⇒「用被测的那个常量构造测试输入」本轮不发生。
> **旧号**：`PIT-GLOBAL-017` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-17`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §判据

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

## PIT-GLOBAL-018 🔴 **变异脚本的两种「假绿」：① 编译不过 ⇒ 测试没跑 ⇒ 读到旧 XML；② 红在更早的断言上 ⇒ 目标断言没被执行到**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮引用 E48 / E49 等守门的历史变异结论 ⇒「红在更早的断言上 ⇒ 目标断言没被执行到」仍需排除。
> **旧号**：`PIT-GLOBAL-018` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-18`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §① / §②

> **一句话**：变异测试要证明的是「**目标断言**会红」，
> 而不是「**有一个东西**红了」。
> 下面两条都会在**目标断言没参与**的情况下报出「转红」或「没转红」。

### ① 变异编译不过 ⇒ maven 压根没跑测试 ⇒ 你读到的是**上一轮**的 XML

**实测**（2026-10-25 第十七轮，CD-5 的变异 M1）：

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

**实测**（同一轮，CD-5 的变异 M3 首版）：

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

## PIT-GLOBAL-019 🔴 **改「上游模块的资源」做变异，而只跑下游 ⇒ 变异到不了运行代码 ⇒ 报成 GREEN（假阴性）**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不做变异** ⇒「改上游模块的资源做变异」本轮不发生。
> **旧号**：`PIT-GLOBAL-019` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-19`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

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

## PIT-GLOBAL-022 🔴 **抄来的「同形态先例」编号也要复核** —— 指针不是证据，它本身是一条**待核对的断言**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要重核签字偏离的「同形态先例指针」⇒ 抄来的编号必须打开核对。
> **旧号**：`PIT-GLOBAL-022` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-22`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确做法 1~3

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
| 2026-10-25 | **第十一轮新增 PIT-GLOBAL-012** —— 「per-app 配置」≠「只能逐模块改」⇒ 第九轮把 CD-6 误判成 favorite 边界下的永久阻塞 ⇒ 第十一轮找到共享层修法（`Connector.setMaxParameterCount(-1)` + Boot customizer 顺序 + `com.rag.common` 组件扫描 10/10）⇒ **该漂移可修、favorite 零触碰**；含四问判据 + 「共享层会改别的域行为 ⇒ 不能静默」的教训 |
| 2026-10-25 | **第七轮新增 PIT-GLOBAL-011** —— 裸 `ServerCallHandler` 造的「慢服务」**从不执行** ⇒ task + follow 两个域的同名测试**绿但空转**；含探针实测输出、两种裸 handler 的分类判据、修法与变异测试要求 |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 PIT-GLOBAL-005（全量构建偶发红的判定三步 + 基线对照的 worktree 做法）· PIT-GLOBAL-006（真源分层的指针） |

---

## PIT-GLOBAL-020 🔴 **变异只在隔离工作树跑 ⇒ 共享工作树里跑变异 ⇒ 变异态可能被提交进主线**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**只改台账文档**、**不做变异**、**不执行任何 git 写操作**（提交由主线统一做）⇒ 该坑的触发动作（变异 + 提交）本轮不发生。
> **旧号**：`PIT-GLOBAL-020` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-20`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/DRIFT/global/lanes/_PROTOCOL.md:125`（§3.5 第 2 条「每条 lane 一个工作树 ⇒ 变异在自己的工作树里跑 ⇒ 提交前 `git diff` 逐个确认变异已还原」）＋ `docs/DRIFT/global/lanes/L1-delivery.md:208`（**实证**：本轮已因该坑误提交过一次 ⇒ `5c3b424` 修回）。

### 现象

在**共享工作树**（含别的域会话的在途编辑）里做变异 ⇒ 变异态与他人改动混在一起 ⇒
提交时把**变异态一并带进主线**。

### 实证

`docs/DRIFT/global/lanes/L1-delivery.md:208` 原文：

> （PIT-GLOBAL-020 的直接要求；本轮已因该坑误提交过一次 ⇒ `5c3b424` 修回）。

⇒ 该坑**已经真实造成过一次主线污染** ⇒ 不是理论风险。

### 正确做法

1. 🔴 **变异只在 lane 自己的工作树里跑**（`docs/DRIFT/global/lanes/_PROTOCOL.md:125`）；
2. 🔴 **提交前 `git diff` 逐个确认变异已还原**（同上行）;
3. 🔴 **不要 `git add -A`**（会扫进别人的在途编辑 ⇒ 见 `docs/DRIFT/global/lanes/L1-delivery.md:9`）。

### 教训

> **共享工作树 = 共享状态** ⇒ 在里面做「临时改动」就是把「临时」变成了「别人的问题」。
> ⇒ 与 `docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-032（「回归跑着的时候不要在别的进程里跑同一个 reactor 的构建」）同族：
> **两个进程同时写同一批产物 ⇒ 出来的结论互不负责。**

### 出处

- 首踩位置：2026-10-25 第二十六轮（`align/global-signoff` lane）⇒ 误提交 `5c3b424` ⇒ 修回；
- 规范落点：`docs/DRIFT/global/lanes/_PROTOCOL.md:125`；
- 引用面（实测 7 处引用本编号，此前**本档缺条目** ⇒ 编号悬空）：`docs/RoundPlan/round-02/README.md:272` · `docs/DRIFT/global/lanes/L1-delivery.md:9` / `:142` / `:173` / `:208` / `:251` / `:367` · `docs/DRIFT/global/lanes/L7-slowgate.md:85` · `docs/DRIFT/global/lanes/L10-ddldeep.md:95` · `docs/DRIFT/global/lanes/L12-rpcboot-article-comment-like.md:121`；
- ⚠️ **2026-10-29 对齐补记**：本条**此前只被引用、没有条目**（`PIT-GLOBAL-001..068` 序列里 **020 缺号**）⇒ 本轮按引用面补齐条目 ⇒ **编号不再悬空**。

---

## PIT-GLOBAL-021 🔴 **变异判定只认 surefire 的 XML —— stdout 的 `-- in` 行在类有 `@DisplayName` 时是「显示名」不是 FQN**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮引用守门结论时按 XML 判定的口径仍有效。
> **旧号**：`PIT-GLOBAL-021` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-21`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置

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

## PIT-GLOBAL-026 🔴 **`GlobalOpenTelemetry.set(sdk)` 存的是「包装器」不是 sdk ⇒ 断言 `get() == sdk` 永远不成立**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写 slow 轴守门 ⇒ OTel 单例包装器这条断言本轮不写。
> **旧号**：`PIT-GLOBAL-026` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-26`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §根因（`GlobalOpenTelemetry.set` 存 `ObfuscatedOpenTelemetry`）

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

## PIT-GLOBAL-027 🔴 **守门的「快调用」用例可能因「首调类加载开销」而首调就红 ⇒ 绿是执行顺序的副产品**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不新增 slow 轴守门 ⇒「首调类加载开销」本轮不触发。
> **旧号**：`PIT-GLOBAL-027` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-27`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §现象

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

## PIT-GLOBAL-028 🔴 **slow 阈值的「配置被忽略」是可静默发生的退化 ⇒ 守门的「占用时长」必须 < Go 的兜底阈值**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不设计 slow 阈值取值 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-028` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-28`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §正确处置

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

---

## PIT-GLOBAL-029 🔴 **静态扫描不排除 `target/` ⇒ 构建产物的旧副本被当源码判 ⇒ 门禁恒红**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写静态扫描守门 ⇒ 本轮不触发。**但**本轮**读** `check-grpc-runtime-classpath.py` 的既有结论 ⇒ 该脚本已排除 `target/`（`_skip_target()`）。
> **旧号**：`PIT-GLOBAL-029` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-29`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/difftest/check-grpc-runtime-classpath.py:107`（`_skip_target`）＋ 本条 §修法

**症状**：`tools/difftest/check-grpc-runtime-classpath.py` 长期 `EXIT=1`，报「9 处未登记违规」——
但那 9 条**全部**指向 `service/*/*-rpc/**target**/classes/application.yml`，
**没有一条**指向 `src/main/resources/`。

**根因**：`iter_ymls()` 用 `os.walk(service/)` 遍历，**没有排除 `target/`** ⇒
上一次构建留下的 `target/classes/application.yml`（**内容是旧的**）被当成源码扫了一遍
⇒ 同一处违规在 `src` 与 `target` 各算一次 ⇒ baseline 的键（源码路径）**永远匹配不上** ⇒ 恒红。

**实测**：脚本会扫到 40 个 `application.yml`，其中 **20 个在 `target/` 下**（不该扫）、20 个在源码树下。
排除 `target/` 后，源码树里的违规是 **9 处**，与 baseline 的 9 条 yml 记录**逐条对应**。

### 为什么这条比「门禁红了」更严重

**一个恒红的守门等于没有守门** —— 久了所有人都会忽略它。
而且它**掩盖了真信号**：真出现一条新违规时，红的输出里混着 9 条 `target/` 的假货 ⇒ **没人会去分辨**。

### 与既有教训的关系

**同源**：**PIT-GLOBAL-019**（构建进行中改源码 ⇒ 绿的是**旧字节码**）。
两者是同一件事的两面：**判据的时点 ≠ 判据对象**。
⇒ 守门的输入必须**只来自声明层**（源码树），**不能混入构建产物**。

### 修法

`iter_ymls()` / `iter_poms()` 排除 `target/`（⚠️ 用**路径分段**判定，不要用子串匹配 ——
`/target/` 子串会误伤名字里含 `target` 的目录）。
⇒ 排除后 `EXIT` 从 **1 变 0**，13 条已知项仍只打印不失败。

### 教训

**守门扫源码树时，必须显式排除构建产物目录。**
⇒ 凡新增静态扫描类守门，第一件事就是问「**我的遍历会不会走进 `target/`**」。

### 出处

- 2026-10-26 第二十八轮 L15（**存量缺陷**，脚本是 2026-10-24 follow 域建的 ⇒ **不是本轮引入**）；
- 关联：`docs/DRIFT/global/lanes/L15-rpcboot-tools.md` §二；
- 同族：**PIT-GLOBAL-019**（旧字节码）。

---

## PIT-GLOBAL-030 🔴 **守门只扫一半的模块 ⇒ 另一半的缺陷只能靠「手工盘点」发现 ⇒ 手工必漏**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要读 `check-jar-executable.sh` 的「20/20」结论 ⇒「守门的覆盖范围本身就是缺陷面」仍需核对。
> **旧号**：`PIT-GLOBAL-030` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-30`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/difftest/check-jar-executable.sh:59`（枚举已含 `service/*/*-rpc` ＋ `service/users/*/*-rpc`）

**症状**：第 27 轮 L11 挖出「`task-rpc` 的 jar 没有 `Main-Class`」，并标为 🔴「重大新发现」。
但**逐个解包 10 个 rpc 模块的产物**后，实际是 **2 个**：

```
hot-rpc    |  58,873 B | <<无 Main-Class>>   ← L11 没报
task-rpc   |  83,827 B | <<无 Main-Class>>   ← L11 报了
（其余 8 个 rpc 都有 Main-Class）
```

**根因**：`tools/difftest/check-jar-executable.sh` 的模块枚举是
`ls -d service/*/*-api service/users/*/*-api` ⇒ **只扫 api**
⇒ **10 个 rpc 模块的产物可执行性从来没有被任何守门查过**
⇒ L11 只能**手工**逐个看 pom ⇒ **手工必漏**（漏了 hot-rpc）。

### 元教训

**「一个人手工查了 10 个」≠「这 10 个都查过了」** —— 手工盘点的覆盖率**不可证**。
⇒ 发现一个缺陷后，必须问「**同类缺陷在别处还有几个？**」，
而**回答这个问题只能靠守门，不能靠再手工查一遍**。

### 判据

**守门的「覆盖范围」本身就是缺陷面。**
一个只覆盖 api 的守门 ⇒ rpc 的产物层**无人看守** ⇒ 那不是「暂时没问题」，是「**没在看**」。

### 修法

- 枚举纳入 `*-rpc`（⇒ 一次覆盖 **20** 个模块）⇒ 守门立刻**精确报出** hot-rpc / task-rpc 两个
  ⇒ 与修复范围完全一致（**这就是「守门找出了缺陷」的闭环证据**）；
- ⚠️ `service/common` **刻意不入枚举**（它是 **library** 模块，被 10 个微服务依赖
  ⇒ 给它 repackage 会**污染下游依赖**）⇒ 枚举要显式注释这一点，防止下一个人「顺手加上去」。

### 教训

**加一个守门时，「它覆盖哪些」和「它不覆盖哪些」同样要写进注释。**
⇒ 不写清楚 ⇒ 半年后有人以为「产物可执行性已经被守门管住了」。

### 出处

- 2026-10-26 第二十八轮 L14（发现 hot-rpc 同款缺陷）+ L15（补上守门缺口）；
- 关联：`docs/DRIFT/global/lanes/L14-rpcboot-hot.md` §二 · [`L15-rpcboot-tools.md`](../../DRIFT/global/lanes/L15-rpcboot-tools.md) §二；
- 同族：**PIT-GLOBAL-029**（守门自身的缺陷 ⇒ 恒红 / 覆盖不全）。

---

## PIT-GLOBAL-031 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不写守门断言** ⇒「断言串也出现在注释里 ⇒ 恒真」本轮不触发。
> **旧号**：`PIT-GLOBAL-031` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-31`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**症状**（L14）：`HotRpcJarExecutableWiringTest.pomMustDeclareSpringBootMavenPlugin`
断言 pom 里有 `spring-boot-maven-plugin`。
**M1 变异把真正的 plugin 块删掉后，这条断言居然是绿的。**

**根因**：该 pom 的**说明性注释里就写着**「spring-boot-maven-plugin」这个字符串
⇒ 删掉真声明，**注释里的字面量照样让 `contains()` 通过** ⇒ 这条守门**恒真**。

⚠️ **最讽刺的地方**：该测试类自己的 javadoc 里**已经预警过**这件事
（「注释里的字面量会打败结构断言」）⇒ **预警写对了，执行没做全** ——
只有**两条**断言做了剥注释，**漏了**这条。

**另一个同族实例**（L13）：`noTwoSlashDiscoveryAddressRemains` 要扫 yaml 找两斜杠地址，
而**修法注释里**恰好写着 `discovery://user-rpc` 这个字面量 ⇒ 同样会误报/恒真
⇒ 改法：**先剥 YAML 注释**再扫。

### 修法

```java
private static String stripXmlComments(String xml) {
    return xml.replaceAll("(?s)<!--.*?-->", "");   // XML
}
// YAML：逐行剥掉 # 之后的内容（注意别误伤 URL 里的 # 与引号内的 #）
```

⇒ 复跑 M1：那条断言**也红了** ⇒ 守门不再恒真；还原后全绿。

### 教训

**「字符串包含」类断言面对「同一字符串也出现在注释里」的文件时，必须先剥注释。**
⇒ 更一般的说法：**守门的判据必须与被测对象「不是同一层的东西」** ——
判据在**结构**上，噪声在**文本**上 ⇒ 拿文本判结构 ⇒ 必然被文本里的同名字符串欺骗。

⚠️ **推论**：凡守门断言的字符串**恰好也是修复说明里会写的东西**（类名、配置项名、错误消息）⇒
**必须假定它会出现在注释里** ⇒ 剥注释不是「加固」，是**必要前置**。

⇒ 另一条同源纪律：**变异测试是发现「恒真」的唯一手段** ——
本条缺陷在守门全绿时**完全看不出来**（绿得毫无破绽），只有把它变坏才知道它根本没在守。

### 🆕 第四十一轮补记：**字面量不止会出现在「注释」里** —— 也会因为**在代码里出现多次**而恒真

🔴 实测（第四十一轮 · E48 守门）：两次踩到**同一个坑的另一个方向** ——
① 探针**头部注释**里写了「`exit 3 = 栈未就绪`」⇒ 守门那句「探针在栈未就绪时 **fail-fast**」
用的是**整文件搜字面量** `exit 3` ⇒ ⇒ **注释里的那份也让它通过** ⇒ 变异 M7（把代码里的 `exit 3` 换成 `:`）**抓不到** ⇒ **判据恒真**；
② 补完「表不存在」分支后，探针里**有两处** `return 4` ⇒ 守门那句「前提不成立 ⇒ fail-fast」同样用整文件搜 `return 4`
⇒ ⇒ 变异打在第一处时**字面量仍在** ⇒ **抓不到** ⇒ **判据恒真**。

⇒ ✅ **修法（与本条原义同源 ⇒ 本条扩展到「多形态」）**：
**判据必须锚到「代码结构 / 具体分支」，不能锚到「整文件字面量」** ——
① 「栈未就绪 fail-fast」⇒ 锚 `READY -ne 1 … exit 3` 这段**结构**（不是字面量）；
② 「前提不成立 fail-fast」⇒ 分别锚**锁分支**（`granted` 判定块内）与**表分支**（各自的 `return N`）。

⚠️ **连带一条变异侧的纪律（本轮当场踩到）**：**变异本身也可能打在注释上** ⇒
「打完之后文件变了」**不等于**「可执行语义变了」⇒ ⇒ **变异脚本必须自检「锚点所在行不是注释行」**，
否则会得到一个**误导性的「守门没抓到」**（其实守门是对的，是变异没变异）。
⇒ 本轮已把这条自检加进变异脚本（锚点起始行以 `#` 开头 ⇒ 直接报错，不混过去）。

### 出处

- 2026-10-26 第二十八轮 L14（整合者修）+ L13（子进程自己发现并修了同族问题）；
- 关联：`docs/DRIFT/global/lanes/L14-rpcboot-hot.md` §8.3.1 · [`L13-rpcboot-message-task.md`](../../DRIFT/global/lanes/L13-rpcboot-message-task.md) §7.5；
- 同族：**PIT-GLOBAL-026**（断言 `get() == sdk` 恒红）、**PIT-GLOBAL-030**（守门覆盖不全 ⇒ 手工盘点必漏）。

---

## PIT-GLOBAL-032 🔴 **「跑红」也可能是假红**：两类非代码原因（构建竞态 / 环境时序）—— 必须单独复跑定性  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮读多个守门退出码 ⇒「跑红也可能是假红」必须先排除。
> **旧号**：`PIT-GLOBAL-032` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-32`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律 1~3

**症状**：第二十八轮全仓回归 `BUILD FAILURE`：
- `user-rpc` **9 个测试类 · 51 个 error** ⇒ 全部 `Failed to load ApplicationContext`
  ⇒ 最深层 `Caused by: java.lang.NoClassDefFoundError: com/rag/users/user/proto/UserServiceGrpc`
- `CommentRpcRuntimeBootGateTest` **1 个 failure** ⇒ 「日志里没有 devh 的『gRPC Server started』」

**两类根因（都不是代码缺陷）**：

| # | 根因 | 判据 | 复跑结果 |
|---|---|---|---|
| ① | **构建竞态**（PIT-GLOBAL-019 的老形态） | 回归跑到 `user-proto`（10/52）时**重新编译了 1 个 proto 文件**；而**同一时刻**整合者正在另一个工作树跑 `mvn -o package`（重建 message-rpc）⇒ **两个 Maven 进程同时写 `user-proto/target/`** ⇒ `user-rpc` 编译时拿到的是**半成品**的 classes ⇒ 运行时 `NoClassDefFoundError` | 单独跑 user-rpc ⇒ **6/6 全绿** |
| ② | **环境时序** | `CommentRpcRuntimeBootGateTest` 要真 Kafka + 真 PG；回归那一刻 broker 不可用（日志停在 Kafka consumer 配置阶段） | 单独跑 ⇒ **1/1 全绿** |

⚠️ **根因 ① 的元教训**：
**「回归跑着的时候，不要在别的进程里跑同一个 reactor 的构建」** ——
两个 Maven 进程会同时写 `target/` ⇒ 产物可能半成品 ⇒ 报出来的错误**指向完全无关的模块**。
⇒ **回归期间只做文档工作**，任何构建都等回归结束。

⚠️ **根因 ② 的元教训**：
**依赖真 broker / 真 DB 的守门，其失败可能是环境而非缺陷** ⇒
**必须单独复跑**才能定性 ⇒ **「跑红」不等于「坏」**（与「跑绿 ≠ 对」是对称的纪律）。

### 纪律

**全仓回归红了，先做三件事，顺序不能颠倒**：
1. **看根因的最深层 `Caused by`** —— 不要看最外层的 `Failed to load ApplicationContext`（那只是**症状**）
2. **单独复跑那个模块/那个类** —— 绿 ⇒ 环境/竞态；红 ⇒ 才是真缺陷
3. **查这个模块本轮是否被改过** —— 没改 + 单独跑绿 ⇒ **几乎必然是环境**（本轮 user 域零改动）

⇒ **不要**因为「回归红了」就立刻去改代码 —— 那会把**环境问题修成代码改动**，
而真缺陷还留在原地。

### 出处

- 2026-10-26 第二十八轮全仓回归（第一次：`regression-full8.log` · `BUILD FAILURE`）
  ⇒ 第二次（干净的 `regression-full9.log`）用于坐实；
- 同族：**PIT-GLOBAL-019**（构建进行中改源码 ⇒ 绿的是旧字节码）、**PIT-GLOBAL-027**（守门的绿必须问「绿得独立吗」）。

---

## PIT-GLOBAL-033 —— 守门的**期望值**可能就落在**已登记的偏离范围**里 ⇒ 修一半 = 把「表述差异」混进「行为漂移」  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮重核签字偏离 ⇒「守门的期望值可能落在已登记的偏离范围里」（如 C-1）正是本轮要查的形态。
> **旧号**：`PIT-GLOBAL-033` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-33`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律 1~3

**症状**：修 E30 时，**守门先绿了 wire 面**（description 对齐 ✅），但**遥测面红了**：

```
expected: "rpc error: code = DeadlineExceeded desc = context deadline exceeded"
 but was: "rpc error: code = DEADLINE_EXCEEDED desc = context deadline exceeded"
```

⇒ 看起来像「还没修完」⇒ 很想把 `Code.name()` 改成 CamelCase。

**真相**：`docs/Intentional_Deviations/common/_signed-deviations.md` **C 类 C-1** 早就登记了这件事：
「gRPC Java 的 `Code.name()` 返回 `NOT_FOUND`，Go 的 `codes.Code.String()` 返回 `NotFound`
…… 仅常量表示不同，**不做臆造映射**」⇒ **已登记 ⇒ 不得顺手改**（AGENTS.md §0：「先确认是不是签字项，不要当新回归去『修』」）。

**根因**：一条漂移往往**横跨两个维度**（这里是 **description** 与 **code 名**），
而**只有一个维度被登记过** ⇒ 只看代码差异看不出「哪一半能动」。

**纪律**：
1. 🔴 **动手改共享层之前，先 `grep` 一次 `docs/Intentional_Deviations/`** —— 按**文件名 + 方法名**搜，不只搜关键词
   （本例搜的是 `goStyleStatusMessage`，命中 `common/_signed-deviations.md` 的 C-1）；
2. 把漂移**按维度拆开**，逐维度判定「已登记 / 未登记」⇒ **只动未登记的那一半**；
3. 🔴 **守门要把「已登记的那一半」钉住**（断言 code 段 = `DEADLINE_EXCEEDED`）⇒
   **否则后人会把整条当成新漂移改掉** ⇒ 那就违反了「已登记偏离不得顺手修正回旧行为」。

⇒ **本轮的最终形态**：E30 修完后，**剩下的差异恰好等于 C-1** ⇒ 边界干净、可核对。

**出处**：2026-10-26 第三十轮 E30（守门实测抓出，不是推测）。

---

## PIT-GLOBAL-034 —— grpc-java 的 `fullMethodName` **不带前导斜杠**（手搓服务时踩）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不手搓 gRPC 服务 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-034` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-34`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**症状**：自建 `ServerServiceDefinition` 时，grpc 直接抛：

```
Method name should be prefixed with service name and separated with '/'.
Expected service name: 'probe.Timeout'. Actual fully qualifed method name: '/probe.Timeout/Hang'
```

**根因**（`javap` 读 `grpc-api-1.63.0.jar`）：
- `MethodDescriptor.extractFullServiceName` 用的是 **`lastIndexOf('/')`** 再 `substring(0, idx)`
  ⇒ 对 `"/probe.Timeout/Hang"` 会截出 **`"/probe.Timeout"`（带斜杠）**
  ⇒ 与 `ServerServiceDefinition.Builder.addMethod` 里的 `serviceName.equals(method.getServiceName())` 比对**必然不等**；
- 而 grpc-java **自己生成的** proto 代码是 `SERVICE_NAME + '/' + methodName`
  （`ArticleServiceGrpc` 里 `generateFullMethodName`）⇒ **`"article.ArticleService/GetArticle"`，不带前导斜杠**。

**纪律**：手搓 gRPC 服务时，`fullMethodName` = **`服务名 + '/' + 方法名`（无前导斜杠）**。
⇒ 与 Go 侧不同（grpc-go 用 `"/service/method"`，且 gRPC 的 HTTP/2 path 规范**带**斜杠）⇒ **两侧探针里各写各的，别互相抄**。

**出处**：2026-10-26 第三十轮 E30 探针（`tools/semantic-probe/rpc-timeout/`）开发期踩到。

---

## PIT-GLOBAL-035 —— Go 的 `select` 有多个分支 ⇒ 探针的 handler **必须永不返回**，否则**探针是随机的**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写 Go 半边探针 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-035` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-35`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**症状**：Go 半边探针第一次跑，客户端拿到 **`code=OK`** 且**立即返回**（2ms）⇒ 看起来「RPC 根本没进服务端」。

**根因**：Go 真源 `rpc.go:59-72` 的 `select` 有**三个**分支（`done` / `panicChan` / `timeoutCtx.Done()`）。
若探针的 handler 写成「等 `ctx.Done()` 就返回 `ctx.Err()`」⇒
**timeout 到点时 `done` 与 `timeoutCtx.Done()` 同时就绪** ⇒ **Go 的 select 随机选**
⇒ 走 `done` 分支时返回的是 `context.DeadlineExceeded`（**不是 status error**）
⇒ 客户端拿到 **`code=Unknown`**（而不是 `DEADLINE_EXCEEDED`）⇒ **探针变成随机**。

**纪律**：探针的「慢 handler」要**模拟真实的慢业务** ⇒ **一直不返回**（不是「到点就返回错误」）。
⇒ 这样只有 timeout 分支就绪 ⇒ **确定性**。

**出处**：2026-10-26 第三十轮 E30 探针（已写进 `tools/semantic-probe/rpc-timeout/go/main.go` 的注释里）。

---

## PIT-GLOBAL-036 —— Spring 的 `@DynamicPropertySource` **不进** context cache key ⇒ 靠「properties 恰好不同」隔离上下文是**侥幸**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不新增 slow 轴守门 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-036` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-36`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**问题**：8 份 slow 轴守门都依赖「`GlobalOpenTelemetry` 在 Spring 上下文 refresh **之前**被换成本测试的 SDK」。
⇒ 而这个前提**只靠「没人先 set」成立**（`GoStyleOtelAgent` 有「已 set 就放弃」分支）。

**根因（实测）**：
- Spring 的 **context cache key 包含 `properties`**（`@SpringBootTest(properties=...)`）⇒ 两类只要 `properties` 不同就**不共享**；
- 但 **`@DynamicPropertySource` 提供的值不进 key** ⇒ 它的值**对上下文隔离毫无贡献**；
- ⇒ **只要某个慢守门的 `properties` 与同模块另一个 `@SpringBootTest` 恰好相同** ⇒ 两者**共享同一个 Spring 上下文**
  ⇒ 本类 `@BeforeAll` 装的那套 OTel SDK **装不进那个已存在的上下文**
  ⇒ 上下文创建时拦截器取走的是**别的测试**的 `Tracer`
  ⇒ 本类 exporter 收不到 span ⇒ 用例以「**找不到 span**」这种**看不懂的形态**变红。

**实测结论**：**今天没真的共享** —— 7 个模块内各 `@SpringBootTest` 的 `classes` 相同，但 `properties` 行数各不相同（8~113 行）⇒ 键必不同。
⇒ **这正是「侥幸成立」**：谁改一下 properties 就可能撞上。

**纪律**：
1. 🟢 **给守门加一个本类专属、别的类不可能撞上的 property**（本轮：`rag.test.gate.context-key=<域>-slow-threshold`）
   ⇒ 把「侥幸」变成**结构保证**（零生产代码改动）；
2. 🟡 **`ApplicationContextInitializer` 装 SDK（建议②）防不住这个失效形态** ——
   **缓存的上下文不会重新 refresh** ⇒ initializer 不会重跑 ⇒ SDK 仍然装不进去
   ⇒ 真正的防线是 **①（专属键）**；
3. 🔴 根治（建议④：共享层注入 `Tracer` 而非取全局）**横跨 10 个域且会碰 favorite**（global 零写权）⇒ 属架构级改造。

**出处**：2026-10-26 第三十轮 T-15 ③（global 台账 `common-tails.md` T-15 第三十轮处置）。

---

## PIT-GLOBAL-037 —— 🔴 还原变异时用 `shutil.copy2` ⇒ **mtime 被保留** ⇒ Maven 跳过重编译 ⇒ 跑的是**旧字节码**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不做变异、不还原变异** ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-037` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-37`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

> **一句话**：变异测试里「还原」这一步本身可能让**后续的验证跑的不是你还原后的代码** ⇒
> 表现为「还原了却仍然红」或「还原了却仍然绿」⇒ **两个方向都会骗人**。

### 实测（2026-10-26 第三十一轮，E38 的变异 MUT-E38）

变异注入：

```python
shutil.copy2(src, bak)          # 备份（copy2 会连 mtime 一起复制）
s = open(src, encoding='utf-8').read()
s = s.replace(old, new, 1)       # 注入变异
open(src, 'w', encoding='utf-8').write(s)
```

⇒ 跑测试 ⇒ **守门如期变红**（1 条，红在目标断言；探针也 EXIT=1）⇒ 变异有效 ✓

还原：

```python
shutil.copy2(bak, src)          # ⚠️⚠️ 罪魁祸首：copy2 把备份的 mtime 一起写回
os.unlink(bak)
```

⇒ `copy2` 是「复制**并保留**元数据（mtime / mode）」⇒ 还原后的源码 mtime
= **变异前**的 mtime ⇒ 而 `target/classes` 里躺的是**变异后编译**的 class（mtime 更新）
⇒ ⇒ **Maven 的增量编译判定「class 比 source 新」⇒ 跳过重编译**
⇒ ⇒ 复跑守门时，跑的**仍然是变异后的字节码**。

### 症状（极具迷惑性）

还原后复跑 ⇒ **守门仍红**（实际值 299ms = 服务端预算 300ms ⇒ 正是变异的值）
⇒ 第一反应是「还原失败 / 改动没写进去」⇒ 于是去查源码 ⇒ **源码是对的**
⇒ 再查一次 ⇒ 更困惑。

### 判据（怎么确认是「旧字节码」而不是「还原失败」）

1. `grep` 源码 ⇒ **改对了**（这能排除「还原失败」）；
2. 但**测试结果仍与变异时一致** ⇒ ⇒ **强烈指向「跑的不是这份源码」**；
3. 删掉 `target/classes` 或 `touch` 源文件 ⇒ 复跑 ⇒ 结果立刻变 ⇒ **实锤**。

### 纪律

- 🔴 **还原后必须让 mtime 前进**：`touch` 源文件（或用 `shutil.copyfile` + `os.utime` 显式设当前时间）
  ⇒ 才能让 Maven 判定「需要重编译」；
- 🟢 **更稳的做法**：还原时用 `shutil.copyfile`（**不**保留 mtime）或直接 `open(...,'w').write(bak内容)`
  ⇒ 写入动作天然把 mtime 推到当前；
- 🔴 **同族**：**PIT-GLOBAL-019**（改上游模块资源却只跑下游 ⇒ 变异到不了运行代码）
  —— 同一条主线（「跑的不是你改的那份」），但机制不同：
  - 019 = **跨模块 reactor 范围**问题（改上游没把上游拉进同一个 reactor）；
  - 037 = **同模块内**，还原操作破坏了增量编译的 **mtime 判定**。
- ⇒ **推论**：「变异转红」只能证明**变异时**那份代码被跑了；**还原后必须再跑一次且结果翻转**，
  否则不能声称「已还原」。

**出处**：2026-10-26 第三十一轮 E38 ① 的变异 MUT-E38（还原后复跑仍红 ⇒ 差点误判成「还原失败」）。

---

## PIT-GLOBAL-038 🔴 **端口占用型假红**：`BindException` ⇒ 先查「谁占着那个端口」，再查「是不是随机端口」  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不跑真容器 ⇒ `BindException` 型假红本轮不触发。
> **旧号**：`PIT-GLOBAL-038` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-38`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §三步定性

> **一句话**：回归里出现 `Failed to bind to address 0.0.0.0/0.0.0.0:<port>` ⇒
> **不要**立刻怀疑自己的改动 ⇒ 先做三件事定性。

### 实测（2026-10-26 第三十一轮，全仓回归）

第 31 轮全仓回归 `mvn -o test -fae` ⇒ **BUILD FAILURE**，唯一失败：
`UserRpcGrpcInterceptorWiringTest`（2 errors）⇒ cause 链末端是：

```
Caused by: java.io.IOException: Failed to bind to address 0.0.0.0/0.0.0.0:33060
Caused by: java.net.BindException: Address already in use
  at io.grpc.netty.shaded.io.grpc.netty.NettyServer.start(NettyServer.java:333)
```

### 三步定性（按这个顺序，别跳）

| 步 | 做什么 | 本轮结论 |
|---|---|---|
| ① **单独复跑** | `mvn -o -pl <模块> -am test -Dtest='<类>' -Dsurefire.failIfNoSpecifiedTests=false` | 🟢 **×3 全绿** ⇒ ⇒ **假红**（**PIT-GLOBAL-032** 的「环境时序」那一类）⇒ 与本轮改动无关 |
| ② **查端口被谁占** | `lsof -nP -iTCP:<port> -sTCP:LISTEN` | 本轮：**`mysqld`**（PID 58635，**已跑 148 天**）⇒ ⇒ **这个占用是长期事实**，不是「刚才谁起的」 |
| ③ **抓「它实际绑了哪个端口」** | 跑测试时用 `netstat -an` 快照差集 | 本轮：实际绑 **62356 / 62412** ⇒ ⇒ **是随机端口**（macOS ephemeral 49152~65535）⇒ **`grpc.server.port=0` 生效** |

### 🔴 第 ③ 步推出的**关键结论**（也是本条最有价值的地方）

失败那次绑的是 **33060** ⇒ **33060 不在随机端口范围内**（49152~65535）
⇒ ⇒ **那次 `grpc.server.port` 根本不是 0** ⇒ ⇒ **存在一个当时生效的、把端口改成 33060 的外部来源**。

⇒ 而排查过：仓库配置（`*.yaml`/`*.yml`/`*.properties`/根 `pom.xml`）**全仓 0 命中 33060** ·
环境变量（`env | grep -i grpc`）**无** · Nacos config（`localhost:8848` 在线）**配置列表为空** ·
Nacos naming（`user-rpc` 注册实例 = **19210**）⇒ ⇒ **不是这四类来源**。

### 🔴 真正的环境背景：本机**有别的域会话在跑东西**

- Nacos naming 上 **`user-rpc` 有两个注册实例，端口 19210** ⇒ 查出出处：**follow 域对拍实验留下的常驻隔离 stub**
  （`docs/DRIFT/follow/api.md:496` 记着「隔离 stub `/tmp/userstub`（`:19210`）」）⇒ **别的域会话留了常驻进程**；
- ⇒ 这类常驻进程 + 别的会话**同时在跑 Maven 测试** ⇒ 就是「端口/环境时序」型假红的土壤。

### 纪律

1. 🔴 **看到 `BindException` 先按上面三步定性**，**不要**直接改代码；
2. 🔴 **用「实际绑了哪个端口」反推 `port=0` 是否生效** ——
   本轮若没做这一步，会误以为「port=0 ⇒ 随机端口 ⇒ 不可能撞 33060 ⇒ 是我的改动导致」；
3. 🟡 **端口号本身能反推来源**：`33060` = MySQL **X Protocol** 默认端口（`mysqld` 监听）
   ⇒ 看到 5 位端口先 `lsof` 看是不是系统服务 ⇒ 系统服务占着 ⇒ **任何想绑它的进程都会失败**；
4. 🟢 **回归数字的统计口径**：`[ERROR] Tests run: …` 的模块汇总行**不匹配** `[INFO] Tests run: …`
   ⇒ 只 grep `[INFO]` 会**漏掉失败模块的用例数** ⇒ 本轮模块口径 2176 vs XML 口径 2305（差 129 = 失败模块）⇒
   **两个口径必须同时看**（差值本身就是失败模块的规模）。

**出处**：2026-10-26 第三十一轮全仓回归（`UNRESOLVED-ARPC-002` 那条的 users/user 侧守门）·
同族 **PIT-GLOBAL-032**（「跑红也可能是假红」）· 本条是它的**具体形态 + 三步定性法**。

---

## PIT-GLOBAL-039 🔴 **遍历 `set` 做判定 ⇒ 门禁输出不可复现**（Python 字符串 hash 随机化）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要读 `check-ledger-selfcheck.py` 的分层计数作证据 ⇒「输出不可复现」仍需排除。
> **旧号**：`PIT-GLOBAL-039` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-39`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/check-ledger-selfcheck.py:598` / `:600`（已 `sorted(srcs)`）＋ 本条 §修法 1

> **一句话**：Python 里 `set` 的**迭代顺序**在**每个进程之间都可能不同**
> （字符串 hash 默认随机化）⇒ 只要**判定逻辑依赖 `set` 的遍历顺序**，
> ⇒ **同一份仓库状态，两次运行的结论就可能不一样** ⇒ ⇒ **「红」无法复现 · 「绿」无法被信任**。

### 实测（2026-10-26 第三十二轮 · 复核器 `tools/check-ledger-selfcheck.py` 第 4 层）

第 4 层的判定片段（**问题就在这个 `for` 上**）：

```python
refs.setdefault(key, set()).add(f)      # ← srcs 是 set
...
src_text = ''
for sf in srcs:                          # ← 🔴 顺序随进程变
    src_text += open(...).read()[:2000]
is_noise, why = _is_noise_ref(path, s, e, src_text)   # ← 正则命中与否取决于 src_text 的**顺序**
```

⇒ `src_text` 的**拼接顺序**随进程变 ⇒ `_is_noise_ref` 的正则（`N5` 删除叙述 / `N7` 台账自陈）
命中与否就变 ⇒ **同一条 `file:line` 引用**每次运行落进**不同分类桶**。

### 实测证据

| 运行 | 「引用不悬空」 | 「已去噪（不计 FAIL）」 | 「跨库歧义」 |
|---|---|---|---|
| 第 1 次 | 20 | 105 | 3 |
| 第 2 次 | 13 | 112 | 2 |
| 第 3 次 | 14 | 111 | 1 |

⇒ **同一份仓库、同一份台账、同一份工具** ⇒ 三个计数**每次都不一样**。
⇒ 🔴 **总判定（`❌ 复核结果：134 处不一致`）在本轮恰好稳定** ⇒ **别因此以为没事** ——
「总数稳定」只是**本轮这几个引用恰好没跨桶**，**不是**「判定是确定的」。

### 三步定性法（与 **PIT-GLOBAL-038** 同款：先定性，别急着改代码）

| 步 | 做什么 | 本轮结论 |
|---|---|---|
| ① **同一份代码连跑 N 次** | `for i in 1 2 3; do python3 tool.py > out$i.txt; done` ⇒ `diff` | 🟢 **三次互不相同** ⇒ 坐实「输出不稳定」 |
| ② **固定 hash 种子再跑** | `PYTHONHASHSEED=0 python3 tool.py` × 3 | 🟢 **三次完全一致** ⇒ ⇒ **根因 = hash 随机化**（不是数据、不是时序、不是并发） |
| ③ **定位到具体的 `set` 遍历** | `grep -n "for .* in .*:"` 逐个看被遍历的是不是 `set` | 🟢 `for sf in srcs:` ⇒ **就是它** |

### 修法与守门

1. ✅ **所有「拿 `set` 拼上下文/做判定」的地方一律 `sorted(...)`** ——
   本轮抽成 `_source_context(root, srcs)` helper，**排序藏在 helper 里**（调用点不用记得排）；
2. 🟢 **守门进第 0 层自检**（阳性对照）—— ⚠️ **对照必须确定性**：
   - ❌ **不能用 set 比**：`set` 的迭代序在**同一进程内**也不保证等于 `sorted` 序
     ⇒ 「拿 set 比 sorted 拼接」这个对照**本身是 flaky**（有时红、有时绿）
     ⇒ ⇒ **变异「抽掉 `sorted()`」有时抓不到**；
   - ✅ **要用「逆序 list」**：传一个**故意不是字典序**的 list ⇒
     有 `sorted` ⇒ 输出 = 字典序拼接；无 `sorted` ⇒ 输出 = 传入序 ⇒ **确定性可区分**；
3. 🟢 **验收判据**：修后**连跑 3 次输出逐字节一致**（本轮三个域各 3 次 ✅）。

⚠️ **推论（比本条本身更重要）**：**任何「跑绿」的门禁，如果它的输出不可复现，那个绿就是运气。**
⇒ ⇒ **验收门禁时必须先问「它连跑两次结果一样吗」**（⇒ 强化 **PIT-GLOBAL-032**「跑红也可能是假红」的**对称面**）。

**出处**：2026-10-26 第三十二轮（`E41` · 复核器第 4 层）·
同族 **PIT-GLOBAL-032**（跑红可能是假红）· 本条是它的**「跑绿可能是运气」**那一面 ·
**PIT-GLOBAL-038**（假红的三步定性法 · 本条共用同一套定性步骤）。

---

## PIT-GLOBAL-040 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不做变异** ⇒「变异后是红的 ≠ 变异有效」本轮不触发。
> **旧号**：`PIT-GLOBAL-040` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-40`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

> **一句话**：「变异跑完是红的」**不等于**「变异有效」——
> **红可能红在别的地方**，也可能是**对照本身不可靠**。

### 实测（2026-10-26 第三十二轮 · 第 1 层去噪规则的 9 条变异）

本轮给复核器第 1 层加了 **6 条去噪规则**（N8~N13），每条都配了第 0 层对照。首跑结果：

| 变异 | 期望被抓住 | 首跑实际 | 形态 |
|---|---|---|---|
| **M1** 抽掉 N10（短路径不再去噪） | ✅ | ✅ 红在目标断言 | — |
| **M2** 抽掉 N10 的「中段过滤」 | ✅ | ✅ 红在目标断言 | — |
| **M3** 抽掉 `sorted(srcs)` | ✅ | ✅ | — |
| **M4** 抽掉 N11（Go 侧路径不再去噪） | ✅ | ❌ **绿** | 🔴 **形态①：规则被掩护** |
| **M5** 抽掉 N12（仓外引用不再去噪） | ✅ | ✅ | — |
| **M6** 抽掉 N13 的邻行窗口 | ✅ | ✅ | — |
| **M7** 抽掉 N8 | ✅ | ✅ | — |
| **M8** 抽掉 N9 | ✅ | ❌ **绿** | 🔴 **形态①：规则被掩护** |
| **M9** 抽掉 N13 的叙述词表 | ✅ | ✅ | — |

⇒ 修完形态① 后复跑 ⇒ **9/9 全被抓住**。

### 形态① **规则被掩护**（本轮实测 2 次）

**症状**：去掉规则 A 后，对照**仍然绿** ⇒ 因为**规则 B 会接手**同一批输入。

- **M8**：N9 = 「`service/<Name>.java` 两段式 ⇒ 裸文件名」；去掉 N9 后
  `service/UserRegisterService.java` 仍被 **N12**（「`service/<X>/` 顶层目录不在本仓」）去噪
  ⇒ ⇒ **绿** ⇒ **变异无效**。
- **M4**：N11 = 「Go 侧路径」；去掉 N11 后 `service/comment/rpc/internal/...` 仍被 N12 去噪
  （合成样本里**没有** `service/comment/` 这个顶层目录）⇒ ⇒ **绿**。

**根因**：**两条规则的判定域有交集** ⇒ 去掉一条，另一条兜住 ⇒ 对照验不到「那条规则在起作用」。

**修法**：
1. ✅ **去噪函数必须返回「规则编号」**，对照要断言**编号**而不只是「是否去噪」；
2. ✅ **合成样本要保证每条规则都能被单独触发**（本轮：给样本补一个
   `service/comment/...` 文件 ⇒ `comment` 进了顶层目录表 ⇒ **N12 不再能接手 N11 的输入**）。

### 形态② **对照 flaky**（本轮实测 1 次）

**症状**：对照时而红时而绿 ⇒ 变异**有时抓得到、有时抓不到**。

- **M3 首跑**：「层 4 的来源上下文按 srcs 排序拼接」这个对照，用的是
  **set** 与 **`sorted(set)`** 的拼接结果比较 ⇒ ⚠️ **set 的迭代序在同进程内也不保证等于 sorted 序**
  ⇒ ⇒ 对照**本身随机**。

**修法**：对照必须**确定性可区分** ⇒ 用**逆序 list**（见 **PIT-GLOBAL-039** 守门第 2 条）。

### 形态③ **变异打在错误的层**

**症状**：变异改了代码，但**被测断言根本不走那条路径** ⇒ 绿。
（例：第 1 层的去噪只影响**输出分类**，不影响**退出码** ⇒ 只断言退出码的对照抓不到它。）

**修法**：变异后要**同时看「输出内容」和「退出码」**；本轮的做法是
**把守门放进工具自身的第 0 层自检** ⇒ 直接断言「哪一条对照红了」⇒ **红在目标断言上**是可核验的。

### 形态④ **阳性对照本身依赖分支** ⇒ 在别的分支上**恒红**（本轮实测）

**症状**：`--mutation-test` 在 `align/task` 上绿，在 `align/global` 上**永远红** ⇒
「自检失败」被当成「台账有问题」，其实是**自检自己的口径有问题**。

**根因**：阳性对照写的是「**复原后第 3 层必须 == 0**」，而第 3 层含**分支相关**判据
（**commit 清单覆盖** = 本分支的 commit 数 vs 台账登记行数）⇒ 非 task 分支上恒不为 0。

**修法**：阳性对照的口径 = **「复原后的计数 == 变异前的计数」**（判据可逆 + 不误报），
基线在**任何变异之前**取 ⇒ ⇒ **自检变成与分支无关** ⇒ 任何分支都能用。

**教训**：**门禁的「阳性对照」本身也要在目标环境上先跑一遍** ——
一个在别的分支上恒红的守门，和没有守门一样（甚至更糟：它会让人以为台账坏了）。

### 形态⑤ **台账里引述「曾写错的路径」用了活引用形态** ⇒ 工具把它当成真缺陷重新报出来

**症状**：一条「我已修好这些路径错误」的台账记录，**自己把自己修的缺陷又报了出来**。

**实测（2026-10-26 第三十二轮）**：global 台账的 E42 行**原样写出了曾写错的完整路径**
（其中两处指向不存在的位置）⇒ 复核工具扫到这些路径 ⇒ 「引用了不存在的文件」⇒ **报缺**。

**根因**：工具分不清「这条路径是**活引用**（读者会照着去找）」还是「这条路径是**历史记述**（我记着它曾经长这样）」——
两者在文本里**长得一模一样**。

**修法（台账侧）**：引述一个「曾写错的路径」时**只写文件名 / 不带前缀的短形式**
（`LikeRpcClient.java`、`_cross.md`）⇒ 工具的路径正则（前缀必须是 `service|tools|docs/`）**匹配不到**
⇒ ⇒ 不会被当成活引用。
⇒ ⚠️ 反过来：**这要求台账作者知道工具的正则口径** ⇒ ⇒ **工具的行为必须写进台账纪律**（否则下一个人还会踩）。

### 纪律

1. 🔴 **「变异后是红的」≠「变异有效」** ⇒ 必须看**红在哪一条断言**；
2. 🔴 **每条规则都要能被抓到** ⇒ 对照断言**规则编号**（不是布尔）；
3. 🔴 **对照必须确定性** ⇒ 禁止用 `set` 做顺序敏感的对照；
4. 🔴 **阳性对照不能依赖分支**（形态④）⇒ 口径 = 「复原后 == 变异前」⇒ 自检与分支无关；
5. 🔴 **台账里引述「曾写错的路径」只能用非活引用形态**（形态⑤）⇒ 否则工具会把已修的缺陷重新报出来；
6. 🟢 **验收判据**：本轮 **11/11 变异全部被抓住，且逐条红在目标断言上**；`--mutation-test` **EXIT=0**。

**出处**：2026-10-26 第三十二轮（`E40` · 复核器第 1 层去噪 + 第 0 层对照）·
同族 **PIT-GLOBAL-018**（变异三道闸）/ **PIT-GLOBAL-019**（变异作用域）/ **PIT-GLOBAL-020**（变异只准在隔离工作树跑）·
**PIT-GLOBAL-021**（变异判定只认 surefire XML）· 本条补的是「**变异自身的有效性**」这一面。

## PIT-GLOBAL-041 🔴 **断言「剩余时间原样传递」时，一次性初始化开销会把预算吃掉 ⇒ 测试恒红**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写「剩余时间原样传递」断言 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-041` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-41`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**现象（2026-10-26 第三十三轮实测）**：给 `DeadlineQueryTimeoutInjector` 写「剩余毫秒数**原样**传给驱动」的断言，
绑定 `deadline = now + 1234` ⇒ 断言「收到的毫秒数 ∈ (1000, 1234]」⇒ **实际收到 822** ⇒ 每次跑都一样 ⇒ **不是 flaky，是稳定偏差**。

**根因**：**算 deadline 与调被测对象之间，隔着两笔一次性开销**：
1. **Mockito 首次建 mock**（ByteBuddy 生成类）≈ **400ms**；
2. **首次建动态代理类**（`Proxy.newProxyInstance`）≈ **数百 ms**。

⇒ 两者都发生在「算 deadline → 调被测对象」之间 ⇒ **剩余预算被凭空吃掉 400ms 上下** ⇒ 断言必然红。
⇒ ⚠️ 最阴的地方：**它看起来像「实现算错了」**（实现确实少传了 400ms！）⇒ 会把人引去改**生产代码**。

**修法（测试侧）**：
1. 🔴 **先建对象、后算 deadline** —— 把 mock / 代理 / 连接的创建全部排在 `deadline = now + X` **之前**；
2. 🟢 **断言用区间而不是等值**（「∈ (X-200, X]」）⇒ 容忍正常的执行耗时；
3. 🟢 **一次性的初始化开销要显式预热**，别指望测试顺序恰好对（测试顺序不保证）。

### 纪律

1. 🔴 **任何断言「剩余时间 / 剩余预算 / 剩余配额原样传递」的测试** ⇒ 必须先排除一次性开销（mock 生成 / 代理生成 / 类加载 / 连接池首建 / 容器首启）；
2. 🔴 **看到「稳定偏差」先问「中间有没有一次性开销」** ⇒ 再去怀疑实现；
3. 🟢 **判据**：把断言改成区间后仍红 ⇒ 那才是实现的锅。

**出处**：2026-10-26 第三十三轮（`E46` 的 `service/common` 单测）·
同族 **PIT-GLOBAL-038**（跑红先定性再改代码）。

## PIT-GLOBAL-042 🔴 **驱动扩展 API 只挂在实现类上 ⇒ 反射探测；且「0」在超时语义里是「无上限」不是「立即」**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写驱动能力探测 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-042` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-42`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**问题（2026-10-26 第三十三轮）**：要拿到**毫秒级**的 SQL 超时，pgjdbc 的 `setQueryTimeoutMs(long)` 挂在
**实现类** `org.postgresql.jdbc.PgStatement` 上 ⇒ 而 **`org.postgresql.PGStatement` 这个接口没有它**（实测 `javap`：
接口只有 `getLastOID` / `setUseServerPrepare` / `isUseServerPrepare` / `setPrepareThreshold` / `getPrepareThreshold` / `setAdaptiveFetch` / `getAdaptiveFetch`）。
⇒ ⇒ **写 `((PGStatement) stmt).setQueryTimeoutMs(...)` 编译不过** ⇒ 而 `service/common` 是 10 个 api + 10 个 rpc 共享的库
⇒ **不该把驱动实现类编进它的 API 面**。

**修法**：**按方法名反射探测**（`stmt.getClass().getMethod("setQueryTimeoutMs", long.class)`）+ **按类缓存结果（含否定结果）**
⇒ 探测不到 ⇒ **退回秒级**（`setQueryTimeout(int)`）+ **记一次 WARN**（不静默！）⇒ 共享库零新依赖、驱动无关。

⚠️🔴 **同一条 API 里的第二个坑：`0` 的语义是「无上限」** ——
pgjdbc 的 `PgStatement.startTimer()` 是 `if (timeout == 0) { return; }` ⇒ **`setQueryTimeoutMs(0)` = 永不超时**
⇒ 而 `DeadlineState.remainingMillis()` 会把负值**夹到 0**（`Math.max(0L, d - now)`）⇒
⇒ **「已过期」与「还剩 0ms」在数值上都是 0** ⇒ **若把 0 直接传给驱动 ⇒ 那条 SQL 会无限制跑**
⇒ ⇒ 必须先把「≤ 0」单独分支处理（**本轮的处理 = 立即失败**，与 Go 的 `ctx.Err()` 语义同款）。

### 纪律

1. 🔴 **共享库要用驱动的扩展能力 ⇒ 反射 + 缓存 + 降级 + WARN**，不把驱动实现类写进 API 面；
2. 🔴 **超时类 API 的「0」几乎都是「无上限」** ⇒ 传值前先把「≤ 0」单独分支，**绝不能把 0 直接传下去**；
3. 🟢 **降级路径必须有守卫测试**（本轮：`driverWithoutMsSupportFallsBackToSeconds` + `hugeRemainingIsTreatedAsNoDeadline`）。

**出处**：2026-10-26 第三十三轮（`E46` 的 `DeadlineQueryTimeoutInjector` 改写）·
机制证据：pgjdbc `42.7.13` 源码 `PgStatement.java:1040-1047`（`startTimer` / `addTimerTask(cancelTask, timeout)`）
+ `PgStatement.java:616-646`（`setQueryTimeout` → `setQueryTimeoutMs`）· 版本由 Boot 4.0.8 的 `spring-boot-dependencies` 管理（`postgresql.version=42.7.13`）。

## PIT-GLOBAL-043 🔴 **Spring 的异常翻译层会换掉异常链的形态 ⇒ 断言必须选对层**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写真 PG 守门 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-043` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-43`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**现象（2026-10-26 第三十三轮实测）**：真 PG 守门里断言「SQL 被取消 ⇒ 状态码 = `DEADLINE_EXCEEDED`」⇒ 实际拿到
**`org.springframework.dao.DataIntegrityViolationException`** ⇒ `Status.fromThrowable(...)` 返回 **UNKNOWN**。

**根因（两层，都不是被测对象的错）**：
1. **mybatis-spring 的 `MyBatisExceptionTranslator`** 会把 `PersistenceException` 翻译成 Spring 的 `DataAccessException`
   （本次是 `DataIntegrityViolationException`）⇒ **异常链的形态被换掉**（`SQLState` 仍可在 cause 链里找到，但顶层类型变了）；
2. **断言写在了「翻译之后」** ⇒ 而翻译之后看到的是 Spring 的异常类型 ⇒ ⇒ `Status.fromThrowable` 找不到 gRPC 状态。

⇒ ⚠️ 最容易误判成「拦截器没生效」或「翻译器没生效」⇒ 实际是**断言选错了层**。

**修法**：
- 🔴 **要断言 wire 行为 ⇒ 断言 gRPC status**（`Status.fromThrowable(t).getCode()`）—— 这是**跨层稳定**的那个量；
- 🟢 **要断言驱动层 ⇒ 就断言 SQLState**（`57014`），但要**在翻译之前**的层断言；
- 🟢 **守门失败时打印整条 cause 链**（类名 + message + SQLState）⇒ 一次就能定位是「哪一层改的形态」。

### 纪律

1. 🔴 **断言前先问「这一层有没有异常翻译器」**（Spring `MyBatisExceptionTranslator` / MyBatis-Plus 的同类层）；
2. 🔴 **跨层断言只认 wire 契约**（gRPC status / HTTP 码），不认中间层的异常类型；
3. 🟢 **失败信息必须带整条 cause 链**（本轮 `describeChain`）⇒ 否则定位成本 = 重跑一次。

**出处**：2026-10-26 第三十三轮（`E46` 的真 PG 守门 `CommentSqlDeadlinePrecisionGateTest`）·
同族 **PIT-GLOBAL-031**（断言前先剥注释 / 断言别落在恒真处）· **PIT-GLOBAL-038**（跑红先定性）。

## PIT-GLOBAL-044 🔴 **注入进上下文的 `AGENTS.md` 快照可能落后于磁盘上的文件 ⇒ 别拿快照当现状**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要写「某域台账是不是空的 / 某条登记是否还在」这类事实 ⇒「快照 ≠ 磁盘」直接适用。
> **旧号**：`PIT-GLOBAL-044` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-44`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法 1（`wc -c docs/DRIFT/<域>/*.md`）

**现象（2026-10-26 第三十三轮）**：global 写台账时写了一句「**favorite 域台账 0 字节 ⇒ 从未被审过 ⇒ 不能替它下结论**」
⇒ 这句话是**从上下文里注入的 `AGENTS.md` 快照**抄来的（快照的 favorite 行写着「🔴 api/common/proto 台账 **0 字节**（从未对照）· ⚠️ 2026-10-25 本轮未做」）。

**favorite owner 回执纠正**：**不成立** —— favorite 的 **12 份台账全部非零**、合计 ≈ **430 KB**
（最大一份 `docs/DRIFT/favorite/rpc.md` **213,799 字节**），此后已做 **19 轮**对照。
⇒ 而**磁盘上的** `AGENTS.md` 那一行**早已更新**为「✅ **2026-10-26 核对**：第十六轮已收口 ⇒ 域内可修漂移/未知项 **均 0**」
⇒ ⇒ **快照 ≠ 磁盘** ⇒ 我引用的是**过期的快照**。

**为什么危险**：这种错误**方向是「把已完成的域说成没审过」** ⇒
① 会让别的域**重开已经闭掉的结论**（favorite 已闭的 DDL / 错误码 / 事务语义三层会被当成待办）；
② 更糟：**它披着「谨慎」的外衣**（写着「不能替它下结论」）⇒ 读起来像是保守的免责声明，**实际上是把过期事实写进了台账**。

**修法**：
1. 🔴 **凡是要写「某域审过没有 / 台账是不是空的」这类事实 ⇒ 回磁盘读那个文件**（`sed -n '27p' AGENTS.md` / `wc -c docs/DRIFT/<域>/*.md`），**不要引用上下文里的快照**；
2. 🔴 **别把「我看到的版本」写成「事实」** ⇒ 要写就带核对日期 + 出处（文件 + 行号）；
3. 🟢 **被别的域纠正时，当场订正 + 把错因写成坑**（本条就是这次的落点）。

### 纪律

1. 🔴 **上下文里的项目政策文件 = 快照，不是真源** ⇒ 涉及具体事实（某域状态、某个数字、某条登记）⇒ **一律回磁盘核**；
2. 🔴 **台账里写「某域没审过」之前 ⇒ 先 `wc -c` 那个域的台账文件**；
3. 🟢 **判据**：本轮的错误表述是「favorite 域台账 0 字节」⇒ 若回磁盘 `wc -c docs/DRIFT/favorite/*.md` ⇒ 全部非零 ⇒ 一眼可证。

**出处**：2026-10-26 第三十三轮（favorite owner 的跨域实测回执）·
同族 **PIT-GLOBAL-038**（先定性再改）/ **PIT-GLOBAL-045**（见下条：跨域回执要自己核）。

## PIT-GLOBAL-045 🔴 **跨域回执要自己核；写进代码注释里的数字也要实测 —— 不能凭印象**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮动作**就是逐条重核签字偏离** ⇒「跨域回执要自己核」正是本轮的工作方式。
> **旧号**：`PIT-GLOBAL-045` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-45`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法 1

**现象（2026-10-26 第三十四轮）**：**favorite 域 owner** 给出 **4 条**工具缺陷反馈（层 4 的去噪 / 第三方库清单 /
完整模块路径反查 / 标准库定位）⇒ 按 AGENTS.md §5.3b「**先复核前提再登记**」逐条复核 ⇒ 🟢 **4 条全部成立**（且实测复现）
⇒ ⇒ **跨域 owner 的回执是高质量的**（本轮一次都没打折）⇒ 但**这不等于可以直接照抄**。

**同轮的另一半**：本轮把结论**写进工具源码的注释**时，写下了「favorite 12 份台账的 **178** 条引用里
**173** 条被去噪」⇒ 🔴 **这个数字是凭印象写的** ⇒ 实测是「**181** 条引用里 **178** 条被去噪」
⇒ ⇒ **注释里的数字会变成下一轮的事实来源** ⇒ 写错就会一路传下去（而它还是**注释** ⇒ 没人会想去核）。

**为什么危险**：
1. 🔴 **回执可能被转述** —— 别人的回执是**二手事实** ⇒ 不核就登记 ⇒ 登记了错的东西 ⇒ 台账可信度受损；
2. 🔴 **「实测」和「推断」在注释里长得一模一样** —— 数字后面不带「实测」二字 ⇒ 读的人**分不出来**。

**修法**：
1. 🔴 **跨域回执 ⇒ 逐条自己复核再登记**（本轮 4 条全部复现 ⇒ 才登记为 E50~E53）；
2. 🔴 **任何要写进代码注释 / 台账的具体数字 ⇒ 先跑一次命令量出来**（本轮 = 同一脚本跑「修复前 / 修复后」两遍）；
3. 🟢 **数字旁边标出处**（「实测」/「文档写的是」/「推算」）。

### 纪律

1. 🔴 **跨域回执 = 待核事实，不是已确认事实** ⇒ 复核 ⇒ 再登记；
2. 🔴 **写进代码注释的数字同样受「先量再写」约束** —— 注释会被后来人当事实读；
3. 🟢 **判据**：本轮的错误表述是「178 条 / 173 条」⇒ 跑一遍脚本 ⇒ 「181 条 / 178 条」⇒ 一眼可证。

**出处**：2026-10-26 第三十四轮（favorite owner 的 4 条跨域实测反馈 + 本轮注释数字订正）·
同族 **PIT-GLOBAL-038**（跑红先定性再改代码）/ **PIT-GLOBAL-044**（快照 ≠ 磁盘）。

## PIT-GLOBAL-046 🔴 **判据的输入必须是「行级局部」⇒ 文件级前缀 = 假阴性**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不改复核器判据 ⇒「判据输入必须行级局部」本轮不触发。
> **旧号**：`PIT-GLOBAL-046` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-46`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**现象（2026-10-26 第三十四轮）**：层 4 的去噪判据 `_is_noise_ref` 要看「这条引用**附近**有没有『已删除 / 不存在』这类叙述」
⇒ 但喂给它的上下文是 **来源文件的整份内容的前 2000 字** ⇒ 🔴 **整文件共用一份判据**。

**后果（favorite 域 12 份台账实测）**：
- **181** 条引用里 **178** 条被判成噪声（只剩 **3** 条真去核）⇒ **FAIL = 0**；
- 而**唯一那条真缺陷**（`core/service/serviceconf.go:83-90` 行号越界，该文件共 89 行）**被 N7 吃掉了**
  ⇒ ⇒ **假阴性**：**门禁最该抓的东西被噪声淹没**，而门禁显示「全绿」。

**为什么危险**：假阴性**比没有门禁更危险** —— 它给出「已核对」的**假保证** ⇒ 别的域照着「全绿」就往下走。

**修法**：
1. 🔴 **上下文按「引用所在行 ±N」取**（本轮 N=1）⇒ 与被判定对象**同处一行**的叙述才算数；
2. 🔴 **叙述必须归属同一个文件**（同族 **PIT-GLOBAL-040** 形态④⑤：同行取首个路径、邻行须为纯叙述行）；
3. 🟢 **配套**：去噪词表**必须窄** —— 「补成 / 更正为 / 改正为 / 改正命名 / 真源里是 / 真实路径是」这类**明确更正动词**可入；
   🔴 但「越界 / 行号 / 应为 / 该文件共 N 行」**一律不入** ⇒ 否则「抓出 1 处行号真错」这类**真缺陷**会被吃掉。

### 纪律

1. 🔴 **判据的输入要「贴着被判定对象」取** ⇒ 别拿「整文件 / 前 N 字 / 全量拼接」当输入；
2. 🔴 **一个判据被多处共用 ⇒ 一处的噪声会污染全部** ⇒ 判据必须**局部**；
3. 🟢 **守门**：正负**两条对照**都要进第 0 层（阳性 = 该去的噪要去掉 · 阴性 = 别处的噪声不许吃掉远处引用）⇒
   用**人造临时文件** ⇒ 与本仓内容无关 ⇒ 任何分支上都成立。

**出处**：2026-10-26 第三十四轮（`tools/check-ledger-selfcheck.py` 的 `_source_context` 从「文件级前 2000 字」改成「引用所在行 ±1」· E50）·
同族 **PIT-GLOBAL-040**（变异无效的形态）/ **PIT-GLOBAL-039**（遍历序 ⇒ 输出不可复现）。

## PIT-GLOBAL-047 🔴 **「文件在哪」的定位通道必须齐全；权威清单必须来自真源的 manifest ⇒ 缺一条就是假 FAIL**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不扩展复核器的定位通道 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-047` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-47`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**现象（2026-10-26 第三十四轮）**：层 4 要判断「台账里这条 `file:line` 在真源里还悬不悬空」⇒ 靠一张索引 ⇒ 🔴 **三条通道各缺一截**：

| 缺的通道 | 症状 | 修法 |
|---|---|---|
| **第三方库清单是手写表** | `gorm.io/gorm@v1.31.1/schema/naming.go` / `prometheus/client_golang` ⇒ 「在真源与全部第三方库里都找不到」⇒ **假 FAIL** | **权威 = 真源自己的 `go.mod`**（**`// indirect` 的也要**）⇒ 手写表只作兜底 ⇒ roots **按路径去重** |
| **索引按 basename 建** | 写成完整模块路径 `gorm.io/gorm@v1.31.1/schema/naming.go` **反而解析不到** ⇒ users 域 **3 条假 FAIL** | 加 **`module@version/...` 精确定位通道**（路径唯一 ⇒ 直接拼出来 ⇒ 顺带消除跨库同名歧义） |
| **索引不含 Go 标准库** | `net/http/server.go:2458`、`src/net/url/url.go:1001` ⇒ **必然假 FAIL** | 加 **`<GOROOT>/src/...` 通道**（`_detect_goroot()` 探测 `$GOROOT` → `~/sdk/go*` → `/usr/local/go` 等 ⇒ **要 `src/` 子目录存在**才算数） |

🔴 **附带一条静默漏检**：Go module cache 的路径**大写字母要转义**（`github.com/IBM/sarama` ⇒ `github.com/!i!b!m/sarama`）
⇒ **此前带大写的条目从来没被索引到** ⇒ 不是「漏检」⇒ 是「**从未参与判定**」。

**为什么危险**：假 FAIL 会**逼人把真缺陷当噪声去掉**（本轮实测：favorite 台账里那条真缺陷**就是被 N7 吃掉的**）
⇒ ⇒ **判据放宽 ⇒ 真缺陷静默** ⇒ 比「门禁红」更难发现。

**修法**：🔴 **清单类事实的权威只能是「被检查对象的 manifest」**（`go.mod` / `pom.xml` / `package.json`），
手写表**只作兜底**；🔴 **定位通道要覆盖「被引用对象可能来自哪里」的每一类**（真源 / 第三方 / 标准库 / 工具链自带）；
🟢 每加一条通道 ⇒ 同时加**一条「不会被误用」的反向对照**（本轮：「不会把普通路径硬拼成 module cache 路径」）。

### 纪律

1. 🔴 **别手写「依赖清单」** ⇒ 从真源 manifest 动态解析（**别忘了 `// indirect`**）；
2. 🔴 **索引结构决定了哪些路径形式查不到** ⇒ 索引按 basename 建 ⇒ **完整模块路径必然查不到** ⇒ 要补精确定位通道；
3. 🔴 **标准库不在依赖清单里** ⇒ 引用标准库的台账行**必然**假 FAIL；
4. 🟢 **路径转义规则要显式实现**（Go module cache：大写 → `!` + 小写）。

**出处**：2026-10-26 第三十四轮（`tools/check-ledger-selfcheck.py` 新增 `_go_mod_requires` / `_modcache_escape` /
`_direct_module_candidate` / `_detect_goroot` / `_stdlib_candidate` · E51~E53）·
同族 **PIT-GLOBAL-046**（判据输入要局部）/ **PIT-GLOBAL-045**（先量再写）。

## PIT-GLOBAL-048 🔴 **变异判据本身也会假阴性 ⇒ 用「集合差分」，不要用「关键词匹配」**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不做变异** ⇒「变异判据本身也会假阴性」本轮不触发。
> **旧号**：`PIT-GLOBAL-048` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-48`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**现象（2026-10-26 第三十四轮）**：本轮给工具写变异验证时，判定口径写成「**目标断言的措辞是否出现在输出里**」
⇒ M2 首跑报「目标断言红：**0/1**」⇒ 但工具**确实红了** ⇒ 🔴 **是我的判据写错了，不是变异无效**。

**根因**：同一条断言在 ✅ 分支和 ❌ 分支是**两套措辞**
（✅「层 4 的第三方库清单**能从 go.mod 动态解析**（含 indirect）」vs ❌「层 4 的第三方库清单**没解析 go.mod** ⇒ 不在表里的模块一律假 FAIL」）
⇒ 用 ✅ 的措辞去匹配 ❌ 的行 ⇒ **必然匹配不上** ⇒ ⇒ **假阴性**（会让人以为「变异没生效 ⇒ 对照是恒真的」⇒ **白查一轮**）。

**修法**：
1. 🔴 **判据 = 集合差分**：**变异后新增的「红行集合」必须恰好等于「目标红行集合」**（不是「关键词出现与否」）；
2. 🔴 **还要同时检查「变异前的红行集合」没有被改动**（`removed` 必须为空）⇒ 防「变异反而让既有红行消失」；
3. 🔴 **同时检查「应该仍绿的断言」仍绿**（`must_stay_green`）⇒ 防「连累」；
4. 🟢 **还原后必须「输出逐字节相同」**（不只是计数相同）⇒ 阳性对照。

### 纪律

1. 🔴 **别用「文案关键词」当变异判据** ⇒ ✅/❌ 两侧措辞天然不同；
2. 🔴 **判据要能区分「目标红」与「连累红」** ⇒ 用集合差分；
3. 🟢 **判据本身也要被审** ⇒ 本轮是首跑「0/1」才暴露出来 ⇒ **首跑红得反常时，先怀疑判据**。

**形态②（2026-10-26 第三十五轮 · 本轮又踩到一次）**：变异 harness 里的**汇总布尔**漏并
⇒ M6/M7 **两条没命中**（目标断言红 0/1）⇒ 但脚本末尾仍打印「✅ 8 条变异全部精确命中」
⇒ 🔴 **harness 自己假绿** ⇒ 差点把两条**无效的变异**当成有效证据写进台账。
⇒ ⇒ 纪律：**每条变异的 `ok` 必须并进 `all_ok`** ⇒ 且**「没命中」必须让脚本退出码非零**
⇒ ⚠️ 同轮还踩了形态③：**断言的匹配串必须用「专属短语」** —— 原本写 `'不适用' in 输出`，
结果被**另一处**的措辞（「漏登记检查**不适用**」）蹭到 ⇒ 变异后断言仍绿 ⇒ **又是假阴性**
⇒ ⇒ 断言要匹配**该断言独有的措辞**（本轮改成 `'本层的判据**尚未为'`）。

**出处**：2026-10-26 第三十四轮（本轮 4 条变异的判定 harness 从「关键词匹配」改成「集合差分」· M2 首跑假阴性）·
**形态②③ 出处**：2026-10-26 第三十五轮（8 条变异 harness 的汇总布尔漏并 + 断言匹配串被蹭到）·
同族 **PIT-GLOBAL-040**（变异无效的五种形态）/ **PIT-GLOBAL-031**（断言落在恒真处）/ **PIT-GLOBAL-037**（还原后必须 touch）。

## PIT-GLOBAL-049 🔴 **加了 `--domain` 参数 ≠ 每层都接住了 ⇒ 某一层没接住 ⇒ 输出的是别的域的结论**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮若引用 `check-ledger-selfcheck.py` 的分层结论 ⇒ 必须确认那一层接住了 `--domain`。
> **旧号**：`PIT-GLOBAL-049` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-49`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/check-ledger-selfcheck.py:198`（`CLAIMS_BY_DOMAIN['task']`）＋ `:613`（`layer2_claims(root, domain)`）

**现象（2026-10-26 第三十五轮 · users/user 域实测带出）**：`tools/check-ledger-selfcheck.py` 有 `--domain` 参数
⇒ 读者理所当然地以为「跑哪个域就查哪个域」⇒ 🟢 但**实测**：同一支工具、同一份主仓台账，只换 `--domain`：

| `--domain` | 第 1 层 | 第 2 层 | 第 3 层 |
|---|---|---|---|
| `task` | 37 引用 ⇒ 缺失 0 | 10/10 OK（断言**全是 task 域**）| 实测 `task-rpc`/`task-api` |
| `users` | **54** ⇒ 缺失 0 | **10/10 OK（断言还是 task 域）** | **实测还是 `task-rpc`/`task-api`** |
| `users-admin` | **71** ⇒ 缺失 0 | **10/10 OK（断言还是 task 域）** | **实测还是 `task-rpc`/`task-api`** |

⇒ **第 1 层随域变**（37 / 54 / 71）⇒ **第 2/3 层三个域输出完全相同** ⇒ **域筛选根本没传到这两层**。

**根因**：第 2 层 `layer2_claims(root)` **根本没有域参数**（`CLAIMS` 写死 task 域的 10 条断言）；
第 3 层把 **surefire 模块名** · **台账路径** · **编号前缀**（`DRIFT-TASK-RPC` 这类）· **commit 清单表** **全写死 task 域**。

**为什么危险**：它比「查不到」**更坏** —— 它给出的是**错的**结论 ⇒
读者会把 **task 域的缺陷当成自己域的**（例如 users 域 owner 看到「45 处 commit 清单缺陷」，
其实那 45 条**全都落在 task 域的台账里**，与 users 域毫无关系）。

**修法**：
1. 🔴 **域相关的一切（路径 / 模块名 / 编号前缀 / 断言清单）都要进「域配置表」**，不许散落在代码里；
2. 🔴 **某个域没有配置 ⇒ 明确打印「⏭ 不适用于本域（⚠️ 不是「通过」—— 是「没查」）」并跳过**
   —— ⚠️ **沉默地跳过**同样会让人以为查过了 ⇒ 必须**出声**；
3. 🟢 **守门**：层 0 加一条对照 —— 在**没有配置的域**上跑，该层必须**显式跳过**。

### 纪律

1. 🔴 **加了参数 ≠ 每层都接住** ⇒ 验收要**逐层对照**（同一份台账、只换参数 ⇒ 看每层输出**变没变**）；
2. 🔴 **「没查到」和「不适用」必须分开说** ⇒ 不适用要**出声**（「⏭ … 域不适用」）；
3. 🟢 **台账格式差异大的域 ⇒ 不要硬套同一套判据**（users 域的编号前缀是 `DRIFT-USERRPC-V-001`，
   与 task 域的 `DRIFT-TASK-RPC-001` **没有共同点** ⇒ 宁可先跳过，别编造判据）。

**出处**：2026-10-26 第三十五轮（users/user 域 owner 的跨域实测回执 ⇒ `E55`）·
同族 **PIT-GLOBAL-046**（判据输入要局部）/ **PIT-GLOBAL-047**（定位通道要齐全）。

## PIT-GLOBAL-050 🔴 **「与主干的公共祖先」当窗口 ⇒ 在主干上恒空 ⇒ 门禁恒红**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不校验 commit 清单链 ⇒ 本轮不触发。**但**本轮**读** `vs-common.md` 的 commit 清单 ⇒ 该层已改用「登记物自构成的链」。
> **旧号**：`PIT-GLOBAL-050` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-50`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/check-ledger-selfcheck.py:734`（`manifest_chain`）＋ 本条 §修法 1

**现象（2026-10-26 第三十五轮 · users/user 域实测带出）**：第 3 层要校验「台账的 commit 清单 vs 分支的 commit 列表」，
窗口取的是 `git merge-base HEAD do-my-own-gh` ⇒ 🔴 **主干上 `HEAD == do-my-own-gh`** ⇒ 公共祖先 == HEAD
⇒ `rev-list HEAD..HEAD` = **空** ⇒ 44 个登记哈希**全部**被判「不在本分支上」+ 1 条行数不守恒
⇒ **恒红 45 处** ⇒ ⇒ **一个恒红的守门等于没有守门**（没人会看它的结论）。

⚠️ 更巧的是：**换个分支就变成反向的假阳性** —— 在 `align/global` 上窗口非空 ⇒ 变成 **141 处**
（那 141 条说的是「global 分支的 97 个 commit 没被 task 域的台账登记」⇒ **本来就该不登记**）。

**为什么危险**：假阳性会**逼人把真缺陷当噪声去掉**（同族 **PIT-GLOBAL-046**）；
而**恒红**的门禁则会让整个工具的输出**被整体忽略** ⇒ 连带它真正抓到的真缺陷也一起被忽略。

**修法**：
1. 🔴 **窗口不能依赖「当前分支 vs 主干」** ⇒ 改成「**登记物自己构成的链**」——
   取登记哈希在 `git rev-list --topo-order --all` 里的两端 ⇒ 区间内**零漏零多**才算通过
   （实测：主仓上 task 域登记的 44 个哈希**恰好**构成连续链 `e5948c9..c886406` = 44 个 commit ⇒ **跨分支一致**）；
2. 🔴 **分支相关的判据（漏登记）只能在「当前分支 == 台账自述的分支」时启用**
   ⇒ 口径**取自台账自述**（`docs/DRIFT/task/_README.md` 写着「分支 `align/task`」）⇒ **不猜**；
3. 🟢 **域无关的硬判据永远跑**：占位符残留 · 同一 commit 多行 · **登记的哈希必须真实存在** · **必须构成连续链**。

### 纪律

1. 🔴 **别拿「与主干的差集」当检查窗口** —— 它在**主干上恒空**、在**别的分支上反向**；
2. 🔴 **窗口要由被检查物自己定义**（登记的哈希构成的链）⇒ 跨分支一致；
3. 🟢 **「该域台账与当前分支不同源」⇒ 打印「不适用」并跳过**（不是「通过」）。

**出处**：2026-10-26 第三十五轮（`E56`）·
同族 **PIT-GLOBAL-047**（定位通道要齐全）/ **PIT-GLOBAL-030**（**一个恒红的守门等于没有守门**）。

## PIT-GLOBAL-051 🔴 **登记「某域把异常改写了」之前 ⇒ 必须逐条核 Go 侧 ⇒ 否则会让本来正确的域去改本来正确的代码**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮重核「Go 侧行为」类断言 ⇒「登记前必须核到 Go 行号」直接适用。
> **旧号**：`PIT-GLOBAL-051` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-51`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律 1

**现象（2026-10-26 第三十六轮）**：第三十三轮登记 **E47**「业务层把『SQL 被 deadline 取消』改写/吞掉」
⇒ 依据**只是 RAG 侧扫出来的 catch 形态**（24 处宽 catch + 3 处静默降级）⇒ 🟡 **没有核 Go 侧的对应行号**。

**第三十六轮逐条核 Go 侧 ⇒ 定性方向相反**：
- 🔴 **改写型域（comment / like / follow / article）的 Go 侧同样改写** ⇒ ⇒ **那 21 处与 Go 一致 ⇒ 销案**
  （Go 出处举例：`comment/rpc/internal/logic/report_comment_logic.go:79-92` · `delete_comment_logic.go:76-77` ·
  `update_subject_state_logic.go:79-80` · `like/rpc/internal/logic/getlikecountlogic.go:103-107` ·
  `follow/rpc/internal/logic/followlogic.go:51-52` · `article/rpc/internal/logic/create_article_logic.go:111-112`）
- 🟡 **user-rpc / admin-rpc 方向相反** —— Go 改写成 `codes.Internal` + "DB查询失败"
  （`user/user/rpc/internal/logic/getuserlogic.go:41` · `user/admin/rpc/internal/logic/getuserlogic.go:38` 等），
  而 RAG **原样上抛 DEADLINE_EXCEEDED** ⇒ ⇒ **RAG 这一侧更准确** ⇒ 走**有意偏离候选**，**不是「RAG 错」**
- 🔴 **message 域又相反** —— Go **原样返回 err**（`markconversationreadlogic.go:59-62`），
  而 RAG 改写成 `INTERNAL + ERROR_DB_UPDATE` ⇒ ⇒ **RAG 这一侧更糊** ⇒ 才是要修的那一侧

**为什么危险**：这类错误的杀伤力**不在台账**，而在**域 owner 照着台账去改代码** ⇒
**把本来正确的 catch 改坏** ⇒ **制造新漂移** ⇒ 而且是在「守门已绿」的方向上制造 ⇒ **比不登记更坏**。

**修法**：
1. 🔴 **「某域把异常改写了」这种判断 ⇒ 必须逐条核 Go 侧对应行号**（不是核形态，是核**那一行**）；
2. 🔴 **判「漂移方向」时必须问一句「Go 那边改的还是原样抛的」** ⇒ 两侧可能**都改**、**都原样**、或**各行不同**；
3. 🟢 **best-effort 降级（catch 后返回缓存值 / 忽略写入）也要核** —— 本轮 3 处「静默降级」里 **2 处与 Go 同款**（销案），
   1 处是**少传 error**（不是降级问题，是**错误信号丢了**）。

### 纪律

1. 🔴 **只读盘点的结论（"X 域有 N 处 Y"）⇒ 登记前必须抽核**（**至少每域抽 1~2 条到 Go 行号**）；
2. 🔴 **「RAG 做了更严/更准的那一侧」⇒ 不是「RAG 错」** ⇒ 走**有意偏离候选**（🏷 候选，建议签），
   **不得「顺手修正回旧行为」**（AGENTS.md §5.3b）；
3. 🟢 **台账登记要写清「哪些已核、哪些待核」** ⇒ 别让「只读盘点」冒充「已对照」。

**出处**：2026-10-26 第三十六轮（**E47 的全面订正** · 逐条核 Go 行号 ⇒ 21 处销案 / 3 处真漂移 / 2 处静默降级销案）·
同族 **PIT-GLOBAL-045**（先复核前提再登记）/ **PIT-GLOBAL-038**（跑红先定性）/ **PIT-GLOBAL-031**（注释说的 ≠ 代码做的）。

---

## PIT-GLOBAL-052 🔴 **去噪词表的宽窄是个两难**：窄 ⇒ 逼台账迎合工具 · 宽 ⇒ 吃真缺陷 ⇒ 新词必须**自带语义**或**语境搭配**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不改去噪词表 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-052` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-52`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律

**现象（2026-10-26 第三十七轮）**：**users/user 域 owner 的跨域实测回执**报「N13 词表缺词（悬空 / 查不到 / 预期状态 / 尚未存在 / 待合入）」⇒ 复核 ⇒ **成立**：
第 1 层的「状态叙述」判据（N13）只有**一个扁平存在性词表**
（`删除|已删除|不存在|已移除|被删|不再存在|不建|未建|不创建|0 字节|错放|空文件|占位`）⇒
作者**照常写中文**（「主干上查不到 ⇒ 路径悬空是预期状态」）⇒ **工具认不出** ⇒ **报成悬空引用**。

**真正的伤害不在多报一条，而在于它逼出了错误动作**：
users/user 域当时唯一的出路是**把台账措辞改写成工具词表里的词**（把「查不到 / 悬空」换成「不存在」）⇒
他们在 `docs/DRIFT/users/_shared.md` 与 `docs/Pitfalls/users/_shared.md` 里**自己记了这次改写**
（「为去噪能做而做的改写」+「工具侧的根因已报 global」）⇒ ⇒ **台账是给人读的，却被迫去适配机器的词表**。

**而另一头更危险**：**裸词不能直接进表** —— **真缺陷恰恰就是这么写的**：
「`X.java` 路径写错 ⇒ 主干上查不到」「`Y.go` 悬空」**都是真缺陷的描述** ⇒
裸词进表 = **假阴性**（门禁最该抓的东西被吃掉）⇒ 同族 **PIT-GLOBAL-040** 形态⑤
（favorite 的 `core/service/serviceconf.go:83-90` 就是这么被宽词表吃掉的）。

### 修法（三层 + 一道护栏）

| 层 | 词表 | 为什么安全 |
|---|---|---|
| **A 层** | 存在性词（删除 / 不存在 / 0 字节占位 …） | 19 轮台账实战验证过 ⇒ **不动** |
| **B 层** | **落地态**：`待合入`/`未合入`/`尚未合入`/`待合回`/`主干未合`/`尚未存在`/`尚未落地` | **自带语义**：在说「这个文件还没落到主干」⇒ **真缺陷不会这么写** |
| **C 层** | **免责**：`是预期状态`/`属预期状态`/`符合预期`/`不是台账事实错误`/`不是缺陷`/`属正常状态` | **自带语义**：作者**明说这不是错误** |
| 🔴 **护栏** | 行里出现「路径写错 / 拼错 / 笔误 / 行号真错 / 应为」⇒ **B/C 层一律不生效** | 行在讲「**引用本身错了**」⇒ 宁可**报缺**，也不吃真缺陷 |

⇒ **裸词「查不到 / 悬空」只靠 C 层的免责半句才生效** ⇒
**users/user 域的原文措辞（「主干上查不到 ⇒ 路径悬空是预期状态，不是台账事实错误」）现在直接被认得出** ⇒ **不必再改写台账**。

### 守门（两条，缺一不可）

1. 🟢 **层 0 的对照**：**阳性 2**（B 层「待合入主干」/ C 层「是预期状态 · 不是台账事实错误」⇒ 命中）
   + **阴性 3**（**裸「查不到」单独出现 ⇒ 不得命中** / **带「路径写错」标记 ⇒ B/C 层不生效** /
   **C 层措辞在密集行里但本路径不是该行第一个路径 ⇒ 不得命中**）；
2. 🔴 **端到端变异**：层 0 只验**判定函数** ⇒ 还得**真把一行插进台账**再跑**整层**看报不报
   （本轮 M9/M10/M11：落地态措辞**必须去噪** / 免责措辞**必须去噪** / 带「路径写错」**必须报出**）
   ⇒ 否则「**函数对、接线错**」照样假绿。

### 本轮自己踩到的**第二面**：台账里引述「错路径」⇒ **两个方向都要防**

**第三十七轮写台账时当场踩到**：本轮的 E61 条目里，为了说明「零回归」，写了一句
「（真缺陷 `check-ddl-static.py` 两条口径**原样仍在报**）」⇒
🔴 **那一行同时含 B/C 层叙述词**（`待合入` / `是预期状态` / `不是台账事实错误` …）
⇒ 且**那个错路径是该行的第一个路径** ⇒ ⇒ **like 域那条真缺陷（E43 · 路径笔误）被静默**
⇒ 全仓第 1 层缺失数从 **1 掉到 0**（**方向错了**：本该「没多吃」，结果「多吃了一条真缺陷」）。

⇒ 🟢 **被「前后 diff」当场抓住**（改动前后的 MISSING 集合一比就露馅）⇒ 改回**短形式**即恢复。

⇒ 🔴 **但这一面比 E42 记的那一面更危险**：
- E42 记的是：把错路径写成**活引用形态** ⇒ 工具**把它报出来** ⇒ **噪声**（可忍）；
- 本轮记的是：把错路径写成**与叙述词同行** ⇒ 工具**把它吃掉** ⇒ **假阴性**（**不可忍**）。

⇒ ⇒ **统一口径：台账里引述「曾写错的路径」⇒ 一律用「不带前缀的短形式」**（只写文件名）
⇒ ⇒ **两个方向都不会被工具误判**。

⚠️ **同时否决了一条工具侧方案（留档，免得下一轮重做）**：曾考虑加「**叙述词必须与路径同子句**」这条工具规则
⇒ 🔴 **实测会误杀 users/user 域那条合法叙述**（路径与叙述词之间隔着 `⇒` 与 `<br>`）
⇒ ⇒ **这一面归台账写法，不归工具** ⇒ 不做。

### 纪律

1. 🔴 **别让工具的词表反向定义台账的措辞** —— 台账是**给人读的**；工具认不出 ⇒ **该改工具**，不是改台账；
2. 🔴 **词表扩展只能走「自带语义」或「语境搭配」** ⇒ 裸词（「查不到」「悬空」「预期状态」）**一律不入表**；
3. 🟢 **扩词表之后必须做**「**前后 diff**」（本轮：主仓 157 份台账 ⇒ 缺失 **2 → 2** · 去噪 **113 → 113**；
   `align/global` 172 份 ⇒ 缺失 **1 → 1** · 去噪 **153 → 153** ⇒ **零回归**）
   ⇒ 「没多吃」是**可证的**，不是「应该没问题」；
4. 🟢 **护栏只挡新层、不挡旧层** —— 旧层是**已验证的行为**，改动它要另开一轮。

**出处**：2026-10-26 第三十七轮（**E61** · N13 拆三层 + 缺陷标记护栏 · 5 条层 0 对照 + 3 条端到端变异 · 前后 diff 零回归）·
同族 **PIT-GLOBAL-040** 形态⑤（宽词表吃真缺陷）/ **PIT-GLOBAL-046**（判据的输入必须行级局部）/ **PIT-GLOBAL-048**（变异判据本身也会假阴性）。

---

## PIT-GLOBAL-053 🔴 **跨域回执里的「文件:行号」必须在**回执方自己的分支**上复核** —— 别处的同名文件可能是**另一个版本** ⇒ 照抄行号 = 没核  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮**逐条重核**跨域签字 ⇒ 引用的 `文件:行号` 必须在**本分支**上重新定位。
> **旧号**：`PIT-GLOBAL-053` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-53`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律 1~3

**现象（2026-10-26 第三十八轮）**：favorite owner 回执订正「毫秒精度对 favorite 零影响」的**理由**（结论不变），
并给了两处坐标：`FavoriteAuthorityEndpointConfig.java:135/276`（`GO_LOOKUP_TIMEOUT_MILLIS = 3_000` +
`bindToCurrent(now + 3_000)`）与 `RpcInterceptor.java:170-172`（per-call deadline = `now + 2s`）。

⇒ 🔴 **global 把它当「已核」写进了回信** ⇒ 但在**自己的分支** `align/global` 上核 ⇒
**`FavoriteAuthorityEndpointConfig.java` 里根本没有 `GO_LOOKUP_TIMEOUT_MILLIS` / `bindToCurrent`**
⇒ 那条 **authority 读面只存在于 favorite 分支** ⇒ ⇒ **同一个路径在两个分支上是两个不同的文件**。

⇒ 📌 **更硬的一条证据**：`service/common` 在两个分支上也是**不同版本** ——
per-call deadline 在 `align/global` 上是 `RpcInterceptor.java:232-237`
（`effectiveTimeoutMs = min(serverBudgetMs, clientDeadlineRemainingMs())`），
在 `align/favorite` 上是 `:170-172` ⇒ ⇒ **行号本身也会漂**。

**为什么危险**：① 回信里的 `文件:行号` 是**最像证据的东西** ⇒ 读者默认你核过 ⇒
② **照抄行号 = 把「对方分支的事实」写成「主干的事实」** ⇒ ⇒ **比不写更坏**（假证据比没证据更难拆穿）。

**纪律**：
1. 🔴 **跨域回执里出现的 `文件:行号` ⇒ 必须在**回执方自己的分支**上重新定位**
   （他们给的是**他们分支**的坐标 ⇒ 你要的是**你分支**的坐标 ⇒ 两者都要）；
2. 🔴 **本分支上不存在那个文件/符号 ⇒ 必须明确写「该结论只在对方分支成立」**
   ⇒ 别把它写成全仓事实；
3. 🟢 **引用别人分支的行号 ⇒ 必须标注分支名**（`align/favorite` 的 `X.java:135`）⇒ 不标注 = 读者会当成主干坐标；
4. 🟢 **台账里写「某域做了什么」⇒ 顺手核一次「那个域自己的分支上是什么版本」**
   ⇒ 本轮就是这么发现 **favorite 第二十轮把 SQL 语句层也做了**（`align/favorite` @ `d871647`），
   而 global 台账里那句「favorite 的 SQL 语句层未做」**已过期**。

**出处**：2026-10-26 第三十八轮（favorite owner 回执的**理由**订正 ⇒ global 独立复核 ⇒ 结论不变、理由写准 ·
E46 / E47 单元格补记 + **AGENTS.md 的 favorite 行订正**）·
同族 **PIT-GLOBAL-045**（跨域回执要自己核）/ **PIT-GLOBAL-051**（登记前必须核到行号）/ **PIT-GLOBAL-044**（快照 ≠ 磁盘）。

---

## PIT-GLOBAL-054 🔴 **「接进编排」≠「有了闸门」** —— 六种假绿（判定只写在注释里 / 退出码是意外得来的 / 守门分支短路 / 端口与就绪不跟随约定 / **施加侧空转** / **阴性观测误诊**）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮以守门退出码作证据 ⇒「接进编排 ≠ 有了闸门」仍需排除。
> **旧号**：`PIT-GLOBAL-054` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-54`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §纪律 1~2

**背景（2026-10-26 第三十九~四十轮 · E48）**：`exp-grpc-admin-deadline-probe.sh` 的头部写着
「`exit 1 ⟺ 客户端 deadline(短) 档下，两侧 PG 查询存活时长差异 > 1s`」，
`run.sh` 与 `diff.py` **都不调它** ⇒ 第三十三轮登记为「E48 · 没进编排 ⇒ A 类 · 下一轮做」。

**复核时才发现三层缺口，比「只差接线」深得多**：

### 形态①：判定**只写在注释里** ⇒ 探针**永远不会红**

🔴 实测：脚本尾部是**无条件 `exit 0`**；中间的「判定」段只把观测值**打印给人看**，
**从不比较两侧、也从不返回失败码** ⇒ ⇒ **这个探针永远不会失败**
⇒ ⇒ **把它接进 `run.sh` 只会得到一道「假闸门」**（比没有闸门更坏：它给出「已覆盖」的假保证）
⇒ 同族 **PIT-GLOBAL-048**（变异判据本身也会假阴性）。

⇒ ✅ **修法**：把判定**抽成可单测的纯函数**（`verdict-grpc-admin-deadline.py`）+ 探针**调用并透传退出码**；
⇒ ⚠️ **三档退出码**：`0=PASS` / `1=FAIL` / `2=INCONCLUSIVE` ⇒ ⇒ **INCONCLUSIVE 不是 PASS**
（观测缺失、对照组也不一致 ⇒ **实验不可判** ⇒ 不能算通过）。

### 形态②：`$VAR` 后面紧跟**多字节字符** ⇒ bash 把它**并进变量名** ⇒ 脚本提前退出 ⇒ **退出码类测试会因错误原因而绿**

🔴 实测（写 wrapper 时）：`echo "… = $RC（0=PASS …）"` ⇒ bash 找的变量名是 **`RC（`** ⇒
`set -u` 下 **unbound variable** ⇒ 脚本**在打印退出码之前就退出 1**
⇒ ⇒ **「假探针 exit 1 ⇒ 闸门 exit 1（退出码透传）」这条测试是绿的 —— 但绿得莫名其妙**：
它证明的是「脚本崩了也是 1」，**不是**「退出码被透传了」⇒ ⇒ **假绿**。

⇒ ✅ **修法三件**：
1. **凡 `$VAR` 后面紧跟中文/全角标点 ⇒ 一律写 `${VAR}`**（探针头部早就记着这个坑 ⇒ 本轮**自己又踩了一次**）；
2. 🔴 **守门必须「标记文本 + 退出码」双断言** ⇒ 只看退出码 ⇒ **任何提前崩溃都是 1**；
3. 🟢 **加一条 lint**：逐文件扫「`$VAR` + 非 ASCII」⇒ 本轮 lint **顺带抓出另外 2 处同类真 bug**
   （探针的 `$VERDICT_RC（` · `run.sh` 的 `$DRC）`）⇒ ⇒ **lint 的产出大于它要防的那一处**。

### 形态③：守门**自己的分支短路** ⇒ 覆盖缺口（变异只红 1 条 ⇒ 其余断言**根本没跑**）

🔴 实测（变异 M3：删掉 `run.sh` 里的闸门调用）：守门原本写成
「闸门缺失 ⇒ 只报『没调用』这一条」⇒ ⇒ **顺序断言与 4 条 env 断言被 `else` 分支跳过**
⇒ ⇒ 变异**只红 1 条** ⇒ ⇒ **看起来「精确命中」，实际是覆盖缺口**。

⇒ ✅ **修法**：**每个分支都要跑满全部断言**（缺失时也**逐条报红**，而不是短路）
⇒ ⇒ 修完后同一变异**红 6 条**（`E48-11~16`）⇒ ⇒ **这才是「精确」**。

### 形态④：**端口 / 就绪判定不跟随编排约定** ⇒ 并发时**连到别人的栈**、栈没起来还照样往下跑

🔴 实测（第四十轮真跑）：`run.sh` 用 `DIFTEST_PORT_OFFSET` 算端口，而探针的端口**写死 18081/58081**
⇒ 多域并发时探针**连到别人的栈** ⇒ 实验结论作废（首跑直接撞端口预检失败）；
⇒ 且**栈没起来时旧写法无条件打印「✓ 就绪」** ⇒ 带着无效观测往下跑 ⇒ **假绿**。

⇒ ✅ **修法**：① 端口**必须由偏移推导**（`$((18081 + OFFSET))`）⇒ 与 run.sh 同一套坐标；
② **栈未就绪 ⇒ `exit 3` fail-fast**（等待上限到了仍不通就退出，并提示换偏移）⇒ 不带着无效观测往下跑。

### 形态⑤：**施加侧与观测侧不是同一个对象** ⇒ 仪器**空转** ⇒ 观测全无效（而结论写得很笃定）

🔴 实测（第四十一轮真跑）：探针里有两处指向 PG 容器的地方 ——
- **观测侧**（采样 PG 服务端日志里的取消时刻）用 `$PG_CONTAINER`（跟随 `DIFTEST_CONTAINER_PREFIX`）；
- **施加侧**（后台 `LOCK TABLE … IN ACCESS EXCLUSIVE MODE`）却**写死** `difftest-postgres`。

⇒ 带前缀跑（`DIFTEST_CONTAINER_PREFIX=difftest5k`）时 ⇒ **观测侧指向 `difftest5k-postgres`、
施加侧指向一个根本不存在的容器** ⇒ `docker exec` 立即失败（stderr 又被 `>/dev/null 2>&1` 吞掉）
⇒ ⇒ **锁从未生效** ⇒ 登录查询**根本没被阻塞**、秒回** ⇒ ⇒ **自然不会有取消事件**
⇒ ⇒ 两侧都报「无取消事件」⇒ 判定器给 `INCONCLUSIVE`（**不算假绿**，但**结论是误诊**）。

🔴 **最扎人的一点**：探针**头部第 70 行早就写对了这条警告**
（「写死 `difftest-postgres` 会在第二个实例里**锁错容器**」）⇒ ⇒
**注释里的警告 ≠ 代码里的约束** ⇒ **「读过注释」不能代替「读过代码」**（§3.1 的同源坑）。

🔴 **次生缺陷**：脚本里**有** `pg_lock_rows` / `pg_lock_count` 两个锁相关函数，
**却从未被调用**（定义了但没人用 ⇒ 看起来「有自检」）⇒ ⇒ **仪器坏了没人知道**。

⇒ ✅ **修法三件**：
1. **施加侧与观测侧必须同容器** ⇒ 锁命令一律用 `"$PG_CONTAINER"`；
2. 🔴 **前提必须自证**：起锁后查 **pg_locks**（**持有者视角**：`granted` 的 `AccessExclusiveLock`）
   ⇒ ⚠️ **不能用 `pg_lock_count`**（它数的是**等锁会话**⇒「没有等锁者」既可能是
   「锁生效但查询还没来」，也可能是「锁压根没生效」⇒ **不可判定**）；
3. 🔴 **前提不成立 ⇒ fail-fast**（新增 `exit 4`）⇒ 不带着无效观测跑完四档再报一个「不可判」。

### 形态⑥：**阴性观测断言成因** ⇒ 把「仪器坏了」写成「查询没到 PG」⇒ **误诊**

🔴 实测（第四十一轮）：探针的阴性分支原文是
「⚠️ **没有任何取消事件** ⇒ 这一侧**根本没向 PG 发出那条查询**」
⇒ 这句话在**锁确实生效**时是**错的**：查询**确实到过 PG**、**确实被阻塞过**，
只是**没被取消**（= 超时机制没生效）⇒ ⇒ **真因被说反了**。
🔴 同一个误诊在**判定器**里也有（`INCONCLUSIVE —— 观测缺失（该侧没有取消事件 ⇒ 查询压根没到 PG）`）
⇒ 而判定器**只看观测值** ⇒ 它**无权**区分成因 ⇒ ⇒ **越权下结论**。

⇒ ✅ **修法**：**「没观测到」至少有两种成因**（① 查询没到 PG ② 查询到了但没被取消），
「前提未成立」是第三种 ⇒ ⇒ 阴性文案**必须列举成因**、**不得断言**；
⇒ ⚠️ **守门判据要分得清「断言」与「列举」** —— 正确写法（`① … ② …`）**仍然含有**「查询压根没到 PG」
这几个字 ⇒ 禁「整句」会**误伤正确写法** ⇒ ⇒ 改用**断言式模式**（`是/就是/⇒` + 该短语）来判。

### 纪律

1. 🔴 **把一个脚本「接进编排」之前 ⇒ 先确认它**真的会红** ⇒ 判据是「**故意破坏 ⇒ 它必须失败**」，
   **不是**「它的注释里写着它会失败」；
2. 🔴 **凡用退出码当信号 ⇒ 必须能区分「按预期失败」与「意外崩溃」** ⇒
   **断言标记文本**（或输出里的特征串）+ 退出码 ⇒ 二者缺一就是假绿；
3. 🟢 **守门的每条断言要有稳定 ID** ⇒ 变异测试才能**按 ID 比对红行集合**（而不是「包含某条关键词」）
   ⇒ ⇒ 「红得对不对」才可判定；
4. 🟢 **每条变异要钉住「期望红行集合 == 实际红行集合」** ⇒ 少红 ⇒ 覆盖缺口；多红 ⇒ 判据串味；
5. 🔴 **凡是「先施加条件、再观测结果」的实验 ⇒ 施加侧与观测侧必须指向同一个对象**
   （容器/库/表/进程）⇒ 两者**各自独立推导**，**不许一个跟一个**（跟对了也不代表另一个对）；
6. 🔴 **施加条件之后必须自证「条件真的生效了」**（**持有者视角**，不是被阻塞者视角）
   ⇒ 不成立就 **fail-fast** ⇒ **不许**带着无效观测跑完全程再报一个「不可判」；
7. 🔴 **阴性观测不得断言成因** —— 「没观测到」要**列举**可能成因（至少两种）；
   判定器这类**只看观测值**的组件**无权**断言成因；
8. 🔴 **同一文件里「同一字面量出现多次」⇒ 判据会恒真** —— 第四十一轮实测踩到两次：
   ① 探针头部注释里出现 `exit 3` ⇒ 把「栈未就绪 fail-fast」那条断言变成**恒真**
   （变异 M7 抓不到）；② 补完「表不存在」分支后文件里有**两处** `return 4` ⇒
   「前提不成立 ⇒ fail-fast」那条断言也变**恒真** ⇒ ⇒ **判据必须锚到分支/代码结构，不能锚到整文件字面量**；
9. 🔴 **对外引用某条漂移的「修法描述」之前 ⇒ 必须先看它有没有被后续条目修正过** ——
   第四十二轮实测踩到：我第四十一轮的回信里引用了 E38② 的修法（「handler 挪受控 executor + 超时 interrupt」），
   而 **E46 早已实测修正过两点**（量级被高估 · **interrupt 单独做不足** ⇒ pgjdbc 不响应线程中断 ⇒
   必须配「取消正在跑的 JDBC 语句」）⇒ ⇒ **回执方当场纠正** ⇒ ⇒ **照旧版做会白做**
   ⇒ ⇒ **台账里同一问题的描述可能分散在多条**（本例：E38 讲架构、E46 讲机制与量级）
   ⇒ ⇒ **发出去之前先在台账里搜一遍这个问题的所有条目**；且**被纠正后要把订正镜像回原条目**，
   否则「只看 E38 的人」照样会照旧描述做；
10. 🔴 **复核一份跨域回执 = 「复现它」+「查它跑的是哪一版」** —— 不是「采信 / 否定」二选一
   —— 第四十三轮实测踩到：users/admin 域 owner 报告「共享复核工具的第 2/3/4 层对非 task 域是误报或零信息量」
   ⇒ 🟢 **复核结论：该报告描述的是第三十二轮旧版**（他们自己写了 `bb680a3`）⇒
   **当前版第 2/3 层已显式跳过**（E55）· **第 4 层有真实信息**（实测 200 条引用 ⇒ 164 条已验证 + 36 条带原因去噪）
   ⇒ ⇒ **但当前版仍有一处成立、且他们没提**：**第 5/6 层写死 `service/task`** ⇒ 别的域跑它 ⇒ 打出的是 **task 的类**
   ⇒ ⇒ **「否定一条回执」也可能漏掉它旁边的真缺陷** ⇒ ⇒ **必须自己跑一遍当前版**，
   而且**要逐条核对回执里提到的版本号 / SHA**（他们引的 4 个 SHA 全部核实通过 ⇒ 他们的**行号与事实**可信，
   **但他们跑的工具版本**不是当前版 ⇒ **两件事要分开判**）；
11. 🔴 **「退出码 0」≠「脚本真跑过」** ⇒ **「脚本什么都没干就退 0」也是 0** ⇒ **假闸门**
   —— 第四十四轮实测带出（users/admin 域 owner 建议）：编排层不能只断言「脚本退出码 = 0」，
   要断言「**它真的跑过并报出了观测值**」（出处：AGENTS.md §3.3 2026-10-21 补「判断有没有真生效不能看启动日志，
   唯一可靠的判据是**运行时证据**」）⇒ ⇒ **落地为：探针声称 PASS（退出码 0）时，闸门要求它的输出里真的有 `Δ_start=` 与 `Δ_end=`**，
   证据不足 ⇒ **`exit 5`（没真跑·假闸门）**；探针**非 0 时不要求**（它已经红了 ⇒ 不该用证据去掩盖它的失败码）
   ⇒ ⇒ **纪律：凡用退出码当信号 ⇒ 必须再问一句「它到底跑了吗」**
   ⇒ 💡 **落地时的一个附带效应**：第三步判据让**两条既有变异**的预期红行集合**变长**
   （「wrapper 吞掉退出码」多红一条 · 「bash 变量名陷阱」多红一条）⇒ ⇒ **判据变强 ⇒ 变异抓到的更多 ⇒ 不是退化**

**出处**：2026-10-26 第三十九~四十一轮（**E48 落地** · 判定器 + 编排接入 + 30 条断言的守门 + 15 条变异全精确命中）·
同族 **PIT-GLOBAL-048**（变异判据本身也会假阴性）/ **PIT-GLOBAL-040**（「变异后是红的」≠「变异有效」）/
**PIT-GLOBAL-019**（上游改动必须同 reactor）/ **PIT-GLOBAL-037**（还原后必须 touch）/
**PIT-GLOBAL-031**（断言串若也出现在被测文件注释里 ⇒ 恒真）。

## PIT-GLOBAL-055 🔴 **往台账里插一行 ⇒ 可能把「表格」插塌 ⇒ 而台账的内容一个字都没丢 ⇒ 所有内容层守门都查不出来**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮**要往台账的表格里插行** ⇒「插一行可能把表格插塌」正是本轮的操作风险。
> **旧号**：`PIT-GLOBAL-055` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-55`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/check-ledger-tables.py`（结构层守门在位）＋ 本条 §修法 3

**现象（2026-10-26 第四十四轮 · global 域自查）**：复核本轮 diff 时发现
`docs/DRIFT/global/_README.md` 的**时间线表**里有 **3 行**（第三十七/三十八/三十九轮）
渲染成了「没有行首的续行」⇒ 再查 `docs/DRIFT/_INDEX.md` ⇒ **域表内部**还有 **3 行**
（第三十六/三十七/三十八轮）落成了同一形态 ⇒ **合计 6 处**。

**两副面孔（同一个根因的两副面孔）**：
- 🔴 **形态①「行首被吃掉」**：`docs/DRIFT/global/_README.md` 的第三十七/三十八/三十九轮三行
  ⇒ 它们的 `| 2026-10-26 | ✅ **第NN轮（…` **整段行首不见了** ⇒ 只剩 ` ⇒ 暂不合回 …` 这种半句。
- 🔴 **形态②「落点落进表格块内部」**：`docs/DRIFT/_INDEX.md` 的第三十六/三十七/三十八轮三条 bullet
  ⇒ 它们被插在 `global` 表行**之后** ⇒ **落在域表块内部** ⇒ GFM 把它当 `global` 行的
  **lazy continuation** ⇒ 那一行的状态单元格被污染。

**根因（不是手滑 ⇒ 是落账脚本的写法）**：🔴 **每一轮都用「长文本 `replace`」往台账里插一行**
⇒ 替换的目标串**恰好是上一行的行首** ⇒ ⇒ **把上一行的行首覆盖掉**（形态①）；
或者插入点算错 ⇒ **落点落进表格块内部**（形态②）。
⇒ **实证**：三个破坏者分别是 `697f547`（第三十六轮）· `85c4588`（第三十九轮）· `dc3770d`（第四十轮）
⇒ ⇒ **连续三轮、每轮破一次** ⇒ 而**前三轮都没人发现**。

**为什么没人发现（这才是它值得单开一条的原因）**：
- ⚠️ **内容一个字没丢** ⇒ 读台账的人**仍然读得到全部信息** ⇒ 肉眼扫不出异常；
- ⚠️ **它不违反任何内容断言** ⇒ `tools/check-ledger-selfcheck.py` 的第 1~4 层
  （引用悬不悬空 / 断言有没有对应物 / 数字对不对 / Go 侧 file:line 在不在范围内）
  **全都查不出来** —— 那些层看的是**内容**，**不看结构**；
- ⚠️ **Git diff 也只会显示「那一行变短了」** ⇒ 若不逐行看，很容易当成「作者把行压短了」。

**修法（三件）**：
1. ✅ **还原必须可验证**：从 git 历史取回**行首完好**的那一版 ⇒ 拼回后**与原文逐字节比对**
   （本轮 3 行：1346 / 2118 / 3336 B，**逐字节一致**）⇒ ⇒ **不是「看着对了」**；
2. ✅ **搬移必须整行**：形态②用「整行搬移 + 断言行数不变 + 断言内容集合不变」⇒ ⇒ **证明是纯搬移**；
3. ✅ **结构层要单开守门**（内容层查不出来的东西，只能新开一层查）⇒
   **`tools/check-ledger-tables.py`**（判据：**表格块内部不得出现「无行首的内容行」**）。

**守门自带变异（证明非空转，且红在目标行上）**：
| 变异 | 手法 | 期望 |
|---|---|---|
| **M1 阳性** | 吃掉一条时间线行的行首（**复刻形态①的真实破坏**） | 必须**精确报出那一行** |
| **M2 阳性** | 把一行内容插进表格块内部（**复刻形态②**） | 必须**精确报出那一行** |
| **M3 阴性对照** | 原样不动 | 必须**一条都不报**（否则说明它在乱报） |

**边界**：
- ⚠️ 默认**只扫 global 域自己写的台账**（`docs/DRIFT/global/` · `docs/Pitfalls/global/` ·
  `docs/Intentional_Deviations/` · 两个共享索引）⇒ **别的域的文件即使有同样形态，也不会让本门禁变红**。
- 🔴 **`--all` 全仓扫 ⇒ 实测另有 10 处**，全在 **`docs/FileTree/comment/common.md`**（comment 域的文件，
  **形态不同**：长单元格被拆成多物理行）⇒ **不在 global 写权内** ⇒ **记账 + 转达 owner**，**global 不改**。

**教训（可复用的判据）**：⇒ **「台账的内容没丢」≠「台账是对的」** ⇒
⇒ **结构层必须单开守门**（内容层查不出来的缺陷，只能新开一层查）⇒
⇒ 同族 **PIT-GLOBAL-054**（守门本身的假绿形态）/ **PIT-GLOBAL-040**（变异必须打在代码上）/ **PIT-GLOBAL-031**（判据不能恒真）。

**出处**：2026-10-26 第四十四轮（global 域自查 ⇒ 发现 6 处 ⇒ 还原 + 新增结构层守门 + 内建 3 条变异）。

## PIT-GLOBAL-056 🔴 **守门用的中间件形态必须与生产一致** —— 「真 PG」≠「真生产路径」：**无池的 DataSource** 让反射探测成功 ⇒ **守门绿、生产坏**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写真 PG 守门 ⇒「守门形态 ≠ 生产形态」本轮不触发。
> **旧号**：`PIT-GLOBAL-056` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-56`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**现象（2026-10-26 第四十一轮发现 · 第四十五轮定位并修）**：**E62** ——
RAG 侧明明实现了「毫秒级 SQL 超时」（pgjdbc 的 `setQueryTimeoutMs`），
**生产日志**却打出：`JDBC 驱动 com.zaxxer.hikari.pool.HikariProxyPreparedStatement 不支持毫秒级 queryTimeout ⇒ 退回秒级向上取整`
⇒ ⇒ **生产路径上根本没走毫秒级** ⇒ 取消时刻最晚比 deadline 晚 999ms。

**根因（两段，第二段才是关键）**：
- ① **探测目标错**：按 `statement.getClass()` 探测 ⇒ 生产拿到的是 **HikariCP 的代理类**
  ⇒ 代理类**只实现 `java.sql.Statement`** ⇒ **驱动私有的毫秒级方法不在代理的接口面上** ⇒ 探测**必然失败**
  （这条是 E62 的主体，单独记为 **PIT-GLOBAL-057**）。
- ② 🔴 **守门测的不是生产路径**：当时的守门用 `DriverManagerDataSource` ⇒ **没有池**
  ⇒ 拿到的**就是** pgjdbc 的实现类 ⇒ 反射**成功** ⇒ ⇒ **守门绿、生产坏**。

**为什么第②点特别值得单开一条**：
> **「真 PG」不等于「真生产路径」。**
> 守门跑在**真数据库**上，只证明了「驱动与数据库之间的时序对」；
> **它没有证明「生产拿到的那个对象」长什么样**。
> ⇒ 而这一层（**连接池代理**）恰恰是**生产的默认形态**（Spring Boot 默认 HikariCP），
> ⇒ 而守门为了「简单」往往就用无池的 `DriverManagerDataSource` ⇒ ⇒ **守门与生产分道扬镳**。

**修法**：
1. ✅ **守门必须跑在生产的中间件形态上**：把 `DriverManagerDataSource` 换成 **真 `HikariDataSource`**（真池）
   ⇒ ⇒ 「E46 守门第一次跑在生产形态上」；
2. ✅ **加一条自证断言**：守门里断言「我拿到的 Statement **确实是池代理**」
   （`captured.getClass().getName()` 含 `Hikari`）⇒ ⇒ **一旦有人把 DataSource 换回无池，守门立刻红**
   ⇒ ⇒ 守门**自己**不再能被自己改坏（否则下次又会退回去）；
3. ✅ **变异证明**：**M2 专门把守门换回无池** ⇒ 必须红 ⇒ ⇒ 证明这条自证**有牙**。

**边界（别过度推广）**：⚠️ 「守门要用真池」**不是**「所有守门都要真池」
⇒ 判据是：**守门断言的那个性质，会不会因为「生产多了一层中间件」而变** ⇒ 会 ⇒ 就必须用生产形态。
（纯逻辑断言（字符串解析、错误码）用无池 DataSource 完全没问题。）

**教训**：⇒ **「用了真 PG / 真容器」只是必要条件，不是充分条件** ⇒
⇒ **要问的是「生产拿到的东西，和守门拿到的东西，是同一个东西吗」** ⇒
⇒ 同族 **PIT-GLOBAL-040**（「变异后是红的」≠「变异有效」）/ **PIT-GLOBAL-048**（变异判据本身也会假阴性）/ **PIT-GLOBAL-057**（代理类把驱动方法藏起来了）

**出处**：2026-10-26 第四十一~四十五轮（**E62** · 第四十一轮发现 · 第四十五轮定位根因② + 修 + 变异）。

## PIT-GLOBAL-057 🔴 **按 `getClass()` 反射探测 ⇒ 拿到的可能是「池的代理」⇒ 探测必然失败** ⇒ 能力探测必须**先 unwrap 到驱动实现类**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不写驱动能力探测 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-057` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-57`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**现象**：**E62** 的主体 —— 明明驱动**支持**某个能力（pgjdbc ≥ 42.7 的 `setQueryTimeoutMs(long)`），
按 `obj.getClass().getMethod(...)` 探测却**永远失败** ⇒ 静默退回降级路径 ⇒ **行为降级、但没有任何报错**。

**为什么必然失败（实测 API 面）**：
- `org.postgresql.PGStatement`（**接口**）⇒ **没有** `setQueryTimeoutMs`（`javap` 实测）
- `org.postgresql.jdbc.PgStatement`（**实现类**，public）⇒ **有** `public void setQueryTimeoutMs(long)`
- 生产拿到的 `Statement` ⇒ `com.zaxxer.hikari.pool.HikariProxyPreparedStatement`
  （**Javassist 运行时生成的 final 类**）⇒ **只实现 `java.sql.Statement`** ⇒ **代理类上根本没有那个方法**
  ⇒ ⇒ 按代理类探测 ⇒ **必然失败**（而且是**静默**的：只是「探测不到 ⇒ 走降级」）。

**修法（不把驱动类编进共享库的 API 面）**：
1. ✅ **先 unwrap**：`java.sql.Statement` 本身就 `extends java.sql.Wrapper`
   ⇒ 用 **JDK 标准接口** `unwrap(Statement.class)` 剥到驱动实现类
   ⇒ ⇒ **不需要**把 pgjdbc 的类写进 `service/common` 的 API 面（这正是原设计约束）；
2. ✅ **实测链怎么走**：Hikari `ProxyStatement.unwrap`（源码 247-257 行）`if (iface.isInstance(delegate)) return delegate;`
   ⇒ **一跳**就到 delegate；pgjdbc `PgStatement.unwrap`（源码 1224-1229 行）`return this` ⇒ **链自然终止**
   ⇒ ⇒ 另加**跳数上限**兜底（防病态实现死循环）；
3. ✅ **剥了不会破坏池的记账**（这一条要**实测**才能下结论）：Hikari 的 `ProxyStatement` / `ProxyPreparedStatement`
   里**完全没有** `setQueryTimeout` / `setQueryTimeoutMs` ⇒ 这些方法由 Javassist 生成的代理**直接委派**给 delegate
   ⇒ ⇒ **在 delegate 上调 `setQueryTimeoutMs` 与在代理上调 `setQueryTimeout` 对驱动是同一件事**。

**配套的断言技巧（可复用）**：💡 pgjdbc 的 `setQueryTimeout(int s)` 内部就是 `setQueryTimeoutMs(s * 1000L)`（源码 616-618 行）
⇒ ⇒ **`getQueryTimeoutMs()` 能精确区分「毫秒路径」与「秒级路径」**
⇒ ⇒ 守门可以**直接读驱动语句上的超时值**来断言「毫秒数真的送到了驱动」，而不是靠时序间接推断。

**教训**：⇒ **「运行时类型」不等于「能力的承载者」** ⇒
⇒ 任何「按类型找能力」的探测（反射 / 接口断言 / SPI 查找）都要先问一句：
**「生产拿到的这个对象，是能力真正的承载者吗？」（代理 / 装饰器 / 动态代理 都可能不是）**

**出处**：2026-10-26 第四十一~四十五轮（**E62**）。

## PIT-GLOBAL-058 🔴 **「就绪判定 = 端口在监听」会被上一轮遗留的进程满足** ⇒ 实验在**环境重建中途**开跑 ⇒ 观测无效  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不跑真容器 ⇒「就绪判定 = 端口在监听」本轮不触发。
> **旧号**：`PIT-GLOBAL-058` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-58`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**现象（2026-10-26 第四十五轮实测）**：探针**第二次**跑时 ⇒ **exit 4（前提未成立）**
⇒ 症状 `FATAL: terminating connection due to administrator command / server closed the connection unexpectedly`
⇒ 探针**不假绿**（fail-fast 生效 ⇒ 没产出假结论）但**白跑一轮**。

**根因三段（每段都得凑齐才出这个症状）**：
1. **残留**：探针的 cleanup 只杀 `SERVE_PID`（= 起栈脚本）⇒ 而起栈脚本带 `--keep` 时它的 trap **不收摊**
   ⇒ ⇒ **栈进程永远残留**；
2. **就绪判据太弱**：探针的「就绪」判据是**端口在监听** ⇒ **残留的旧栈也能满足它** ⇒ 探针以为就绪、立刻开跑；
3. **环境正在被换**：起栈脚本的第一步是 `docker compose down -v` + `up -d`（**删卷重建数据库**）
   ⇒ ⇒ 实验在**环境重建中途**开跑 ⇒ 加锁那条 SQL 的后端被换掉 ⇒ **前提不成立**。

**修法**：⇒ 起栈**之前**先按 **本 harness 自己的产物目录**（`$RUN_DIR/bin/`）**精确清掉残留栈**
⇒ ⇒ 路径限定 ⇒ **不碰别的域的栈**（多域并发时安全）⇒ ⇒ 之后「端口在监听」才真的代表「**本轮**的栈」。

**守门**：**顺序也要钉**（静态断言：清理动作必须在起栈**之前**）+ **变异**（把清理改成空操作 ⇒ 必须红）。

**教训**：⇒ **「就绪」的定义必须是「**本轮**要用的那个东西就绪」，不是「某个端口在监听」** ⇒
⇒ ⇒ 凡是要**重跑**的实验工具，**每次开跑前必须先把上一轮的东西清干净** ⇒
⇒ 同族 **PIT-GLOBAL-054**（守门自身的假绿形态）/ **PIT-GLOBAL-019**（同 reactor 纪律）

**出处**：2026-10-26 第四十五轮（**E64**）。

---

## PIT-GLOBAL-059 🔴 **一次 grep 空结果 ⇒ 下了「根本没有调用」的反向结论 ⇒ 而这个结论会一路带进台账与拍板建议**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要写「Go 侧有没有 X」这类**否定性**结论 ⇒「两次独立检索都空才允许写不存在」直接适用。
> **旧号**：`PIT-GLOBAL-059` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-59`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §教训

**症状**：2026-10-26 第四十六轮查「Go 侧 rpc handler 路径有没有出向 rpc 调用」⇒
第一次 grep 的模式是 `zrpc.MustNewClient\|\.Call(ctx\|RpcClient` ⇒ **0 命中** ⇒
于是写下结论「**Go 的 rpc handler 路径里根本没有出向 rpc 调用** ⇒ ⇒ **RAG 的「出向调用无 deadline」没有 Go 对照基准 ⇒ 不是漂移**」。

🔴 **而这个结论是错的**。第二次换前缀扫（`svcCtx\.[A-Z][A-Za-z]*\.`）⇒ **5 处命中**：
- `service/favorite/rpc/internal/logic/helper.go:98`（`svcCtx.UserRpc.GetUser(depCtx, …)`）
- `service/favorite/rpc/internal/logic/helper.go:133`（`svcCtx.ArticleRpc.GetArticle(depCtx, …)`）
- `service/follow/rpc/internal/logic/helper.go:82` · `service/like/rpc/internal/logic/likeactionlogic.go:380` · `service/message/rpc/internal/logic/helper.go:144`
- 另有 `service/comment/rpc/internal/logic/subject_fallback.go:18`（`articleRPC.GetArticle(ctx, …)` —— **客户端是函数参数**，连 `svcCtx.` 前缀都没有 ⇒ **任何基于前缀的 grep 都会漏**）

⇒ ⇒ **结论整个反转**：Go 的出向调用**全部吃 ctx** ⇒ **继承请求 deadline** ⇒ ⇒ **RAG 侧是真漂移**（⇒ 登记为 **E65**）。

**根因（两条）**：
1. **模式选得太窄**：Go 的写法有多种形状 —— 字段访问（`svcCtx.XxxRpc.`）· **函数参数**（`articleRPC.`）· **结构体字段**（`l.svcCtx.XxxRpc.`）⇒ **单前缀必漏**；
2. 🔴 **把「一次空结果」当成了「不存在」** ⇒ 而**搜索未命中不证明不存在**（这是检索的基本纪律，但在「我在找证据支持一个结论」的压力下最容易破）。

**教训**：
- ⇒ **同一问题至少用两个独立角度扫**（不同前缀 / 不同目录 / 结构化解析），**两次都空**才允许写「不存在」；
- ⇒ **凡是「不存在 ⇒ 所以不是漂移」这种否定性结论**，进台账前必须**至少两次独立检索都为空**；
- ⇒ **空结果的 grep 模式本身要写进台账**（否则下一个人会重复同一个错误）。

**同族**：**PIT-GLOBAL-057**（能力探测拿到的可能不是能力的真正承载者）· **PIT-GLOBAL-053**（跨域回执必须标明核的是哪一版 ref）

**出处**：2026-10-26 第四十六轮（**E38② 重估** ⇒ 由此挖出 **E65**）。

---

## PIT-GLOBAL-060 🔴 **「漂移形态守门」若只钉「两侧不相等」⇒ 漂移被修好后守门仍绿 ⇒ 守门反而变成「不许修」的锁**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— **E65 的结论正是从「漂移形态守门」的 PASS 读出来的** ⇒ 该坑直接适用。
> **旧号**：`PIT-GLOBAL-060` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-60`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`docs/PhaseGoal-judge.md` §五 X-6（「PASS ⇒ 漂移还在 ⇒ 判绿 ⇒ 不能写已对齐」）

**症状**：第四十六轮要给「下游调用不继承 deadline」这条**已确认的漂移**做守门。
若照搬既有的**等值守门**形态（两侧必须相等）⇒ 这条守门**从第一天起就是红的** ⇒ ⇒
**一个恒红的守门等于没有守门**（同 **PIT-GLOBAL-056** 的第 3 段形态）。

⇒ 但**直接放弃守门也不对** ⇒ ⇒ 漂移被修好的那一天，**没有任何东西会提醒「该回写台账了」**。

**修法**：**漂移形态守门**（不是等值守门）⇒ **按 key 分两类**：
| 键类 | 判据 | 含义 |
|---|---|---|
| **parity 键** | 两侧**必须相等** | 证明「这一面**已经**对齐」（例：`sql_carrier`）⇒ 漂移**不在**这一面 |
| **drift 键** | 必须是**已登记的形态**（Go=什么、RAG=什么，**两个值都钉**） | 证明「漂移**长成**登记的样子」⇒ **谁把漂移修好了 ⇒ 门禁就红 ⇒ 逼着人回写台账** |

⚠️ **drift 键必须钉「期望值对」而不是「只要不相等就行」** ⇒ 否则漂移形态从
「Go=true/RAG=false」变成「Go=true/RAG=true（修好了）」时，判据仍然满足 ⇒ **守门不红**。

⚠️ **配套两条**：
1. 🔴 **编排层必须能自证「两侧都真跑过」** ⇒ 探针的**原始输出行必须被打印出来**
   （本轮实测：原始行只写进临时文件 ⇒ 编排层**看不到** ⇒ 「探针只产出 0 条 PROBE 行」这种误判会被放过）
   ⇒ 同族 **PIT-GLOBAL-052**（「退出码 0」≠「脚本真跑过」）；
2. 🔴 **漂移键的期望值必须有台账出处** ⇒ 无出处的期望值 = 无出处的断言。

**教训**：⇒ **守门的形态必须匹配被判据的性质** ⇒ 「已确认但暂不修的漂移」要的是**形态守门**，
不是等值守门、也不是不守 ⇒ ⇒ **且守门要能回答「漂移被修好了吗」**。

**出处**：2026-10-26 第四十六轮（**E65** 守门 `tools/difftest/check-deadline-downstream.py` + 变异 2 条全精确命中）。


---

## PIT-GLOBAL-061 🔴 **「去噪」是那条判据的「计账规则」，不是「放弃检查」** ⇒ 让**另一条判据**失去覆盖 ⇒ 变异样本被相邻行吃掉 ⇒ **假绿**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不改复核器的去噪逻辑 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-061` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-61`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**症状**：2026-10-26 第四十七轮给共享复核器第 4 层加「补充判据」（锚点指错文件）⇒
把「**正例**变异样本」注入到 task 域台账**文件末尾** ⇒ ⇒ **补充判据一条都没报** ⇒ **判据被误判成假绿**。

**根因（两段，都很朴素）**：
1. **注入行紧邻的上一行**含噪声词（`已登记` / `删除` / `见 §` …）⇒
   第 4 层的去噪判据看的是「**引用所在行 ±1**」的**行级局部** ⇒ ⇒ **整条注入的引用被去噪**；
2. 🔴 **被去噪的引用在代码里是 `continue` 掉的** ⇒ ⇒ **它根本没进「补充判据」那一层的数据流**
   ⇒ ⇒ 补充判据**从未看见过它**。

⇒ ⇒ **「这条引用是否悬空」与「这条锚点是否指错文件」是「两个问题」**
⇒ ⇒ **去噪只该影响前者的计数，不该让后者失去覆盖**。

**修法**：去噪命中时**不再 `continue`** ⇒ 改成「记进噪声桶，但**继续解析文件**」
⇒ ⇒ 该引用照样进入补充判据的数据流 ⇒ 而「不悬空 / 已去噪 / 跨库歧义」三个**既有计数口径保持不变**
（实测：改动前后 105 / 20 / 24 完全一致 ⇒ **没有回归**）。

**教训**：⇒ **判据之间是正交的** ⇒ 「A 判据为了不误报而放过某条输入」**不等于**「B 判据也不必看它」
⇒ ⇒ 加新判据时，**先确认老判据的「放过」路径有没有把输入整个截断掉**。

**同族**：**PIT-GLOBAL-054**（守门自身的假绿形态）· **PIT-GLOBAL-048**（变异判据本身也会假阴性）

**出处**：2026-10-26 第四十七轮（**E68**）。

---

## PIT-GLOBAL-062 🔴 **从自然语言里抽「实体」做判据 ⇒ 必须过三道收紧** ⇒ 否则假阳性 ~90% ⇒ 判据直接不可用  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不从自然语言抽实体做判据 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-062` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-62`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §教训

**症状**：2026-10-26 第四十七轮实现「锚点文件里找不到台账断言的那个实体」⇒
第一版上线即在 users / users-admin 域报出 4 条 ⇒ **逐条复核 ⇒ 4 条全是假阳性**。

**四个假阳性来源（逐个都要堵）**：
1. 🔴 **限定名的最后一段** —— 台账常写
   「`rest/engine.go:154-156` 的原生 `TimeoutHandler` 被 `observability.DisableNativeRestTimeout`」
   ⇒ **带点的那个明确属于另一个包** ⇒ 它是**跨文件对照**，不是「锚点处的实体」
   ⇒ ⇒ **取最后一段当候选 ⇒ 必假阳性** ⇒ **带点的片段必须整体跳过**；
2. 🔴 **窗口太宽** —— 台账常**一行多事实**（「`a.go:55` 的 X | Y | **消费点**：`Z(...)`」）
   ⇒ 窗口宽 ⇒ 把**别处**的符号算成「锚点处的实体」⇒ ⇒ **窗口收到 ±45 字符**；
3. 🔴 **符号没有区分度** —— 普通英文词（`enabled` / `offset`）与全大写缩写（`HTTP`）
   **只要在真源某处被声明过**就会被判成「指错文件」
   ⇒ ⇒ **要求符号含「小写段 → 大写」的边界**（`GetTaskHandler` ✔ · `appendAuthHandler` ✔ · `HTTP` ✘）;
4. 🔴 **「声明处唯一」这条判据会把跨域同名符号挡掉** —— 例如 `RegisterHandlers` 在真源 **11 个** `routes.go` 里都声明过
   ⇒ ⇒ **唯一性要在「全真源」范围上算**，不是「在某个子目录里唯一」。

**修完的实测**：task / users / users-admin **三域各 0 条命中**（收紧前 users 2 条 + users-admin 2 条**全是假阳性**）
⇒ 且**变异正例仍被抓到**、**阴性对照仍不报** ⇒ **判据非空转且无假阳性**。

**教训**：⇒ **「提示级」不是「可以乱报」的借口** —— 一个 90% 假阳性的提示，读者会**整体忽略**
⇒ ⇒ **提示级判据也要过「正例 + 阴性对照」两条变异**，并**逐条人工复核首轮命中**。
⇒ ⇒ **首轮命中必须人工复核**才能定稿 —— 不复核就上线，等于把噪声塞进别人的工作流。

**同族**：**PIT-GLOBAL-052**（去噪词表的宽窄是个两难）· **PIT-GLOBAL-061**（去噪不该让另一条判据失去覆盖）

**出处**：2026-10-26 第四十七轮（**E68**）。

## PIT-GLOBAL-063 🔴 **解析器只认「自己索引过的位置」⇒ 索引外的合法锚点一律报「文件找不到」** ⇒ **假 FAIL** ⇒ 比不报更坏  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要核「引用的 Go `文件:行号` 是否悬空」⇒「解析器只认自己索引过的位置」仍需排除。
> **旧号**：`PIT-GLOBAL-063` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-63`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法 ①②

**症状**：2026-10-26 第四十八轮用新加的全仓扫描模式跑出 **25 条**跨域第 4 层 FAIL
⇒ **逐条人工复核** ⇒ **16 条是假 FAIL**（13 条 Go 标准库裸 basename + 3 条真源之外的第三个仓库）。

**两个来源**：
1. 🔴 **Go 标准库**：`_stdlib_candidate` 只能把**带包目录**的写法
   （`strconv/quote.go`）拼成 `<GOROOT>/src/strconv/quote.go`
   ⇒ 台账写**裸 basename**（`quote.go:522-563`）⇒ **必然解析不到**
   ⇒ 落到「文件在真源与全部第三方库里都找不到」
   ⇒ （article 域 `quote.go` 6 条 + `url.go` 6 条 · common 域 `context.go` 1 条）；
2. 🔴 **真源之外的仓库**：like 域台账引用 `service/async/rpc/...`（第三个仓库
   `Sea-BreakTheWaves`，**本机不存在**）⇒ 冻结仓里**根本没有 `service/async/` 这个目录**
   ⇒ ⇒ **任何工具都无法核验** ⇒ 报成「文件找不到」= **假 FAIL**。

**修法**：补**两条通道** ——
① **标准库 basename 通道**（`<GOROOT>/src` 下按 basename 建索引，
   **排除 `cmd/**`**：那是 Go 工具链自己的源码，不是用户能 import 的标准库）
   ⇒ ⚠️ **只在「模块池没有命中」时启用**（否则会给「已唯一命中」的引用凭空加候选
   ⇒ 污染「跨库歧义」口径 ⇒ 三个既有计数逐项变化）；
② **外部仓库锚点通道**（路径在 `service/` 下但**首段目录在真源里不存在**
   ⇒ 它**不可能**是真源锚点 ⇒ 归「外部仓库锚点」桶：**不计 FAIL 但必须列出**）。

**实测**：跨域 FAIL **25 → 9 条**；三个既有计数 **逐项未变**（task 105 / 20 / 24 ·
users 227 / 50 / 79 · users-admin 164 / 36 / 58 · A/B 逐项一致）。

**教训**：⇒ **「解析器只认一种写法 / 只知道自己索引过的位置」= 半个解析器**
⇒ ⇒ 凡台账里会出现的**同一文件的多种写法**（带目录 / 裸 basename / `src/` 前缀 /
`module@version/` / **另一个仓库**）⇒ **每一种都要能解析**
⇒ ⇒ **假 FAIL 比不报更坏** —— 别的域会以为自己的台账坏了，而那不是他们的责任。

**同族**：**PIT-GLOBAL-060**（守门自身的形态缺陷）· **PIT-GLOBAL-048**（变异判据本身也会假阴性）

**出处**：2026-10-26 第四十八轮（**E70**）。

---

## PIT-GLOBAL-064 🔴 **共享门禁的「扫描范围」漏掉了门禁作者自己的台账** ⇒ **真空缺** ⇒ 「我的锚点都核过」这句话不成立  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮改的就是 **global / common 域自己的台账** ⇒「我写的台账有没有被我的工具检查过」直接适用。
> **旧号**：`PIT-GLOBAL-064` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-64`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：`tools/check-ledger-selfcheck.py` 的 `--entity-all` 全仓扫描模式（2026-10-26 补）＋ 本条 §修法

**症状**：2026-10-26 第四十八轮做全仓扫描时**自查**发现：
`tools/check-ledger-selfcheck.py` 的 `LEDGER_FILES` **只配了 task / users / users-admin 三个域**
⇒ ⇒ **`docs/DRIFT/global/_README.md`（global 自己写的台账）根本不在扫描范围里**
⇒ ⇒ **global 自己台账里的 Go 锚点从未被检查过** ⇒ ⇒ 而台账里写的是「全部锚点都核过」。

**修法**：新增 `--entity-all` **全仓扫描模式**（扫 `docs/**/*.md` ⇒ **202 个文件**）
⇒ **提示级 · 不计入退出码**（理由：别的域的历史锚点问题**不该**让 global 的工具变红
⇒ 那不是 global 的写权范围）。

**教训**：⇒ **「我写的台账有没有被我的工具检查过」是一个必须单独回答的问题**
⇒ ⇒ 任何**扫描范围按域配置**的门禁 ⇒ 必须回答「**作者自己的域在不在里面**」
⇒ 不在 ⇒ 那是**真空缺**，不是「范围外」。

**同族**：**PIT-GLOBAL-054**（守门自身的假绿形态）· **PIT-GLOBAL-063**

**出处**：2026-10-26 第四十八轮（**E69**）。

---

## PIT-GLOBAL-065 🔴 **从「片段」里取的位置必须换算成「绝对位置」** ⇒ 否则「上下文判据」**恒不成立** ⇒ 判据从未生效  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不抽实体判据 ⇒「片段内偏移 ≠ 绝对位置」本轮不触发。
> **旧号**：`PIT-GLOBAL-065` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-65`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**症状**：`_entity_candidates` 里要判「台账是否断言了包含关系」⇒ 取符号**前 24 字符**查措辞
⇒ 代码写的是 `text[max(0, pos - 24):pos]`，而 `pos = m.start()`
⇒ 但 `m` 是在**反引号片段内**匹配的 ⇒ `m.start()` 是**片段内偏移**
⇒ 切到的是**窗口开头** ⇒ 「包含措辞」检查**恒不成立** ⇒ ⇒ **该判据从未真正生效**。

**修法**：`pos = span_m.start(1) + m.start()`（片段起点 + 片段内偏移 = 绝对位置）。

**教训**：⇒ **两段式正则嵌套 ⇒ 位置坐标系必须显式换算**
⇒ ⇒ 「正则里再套正则」时，内层的 `start()` **默认不是绝对位置**
⇒ ⇒ 且**恒不成立的判据不会报错**（它只是永远沉默）⇒ **必须用变异证明它真的会触发**。

**同族**：**PIT-GLOBAL-048**（变异判据本身也会假阴性）· **PIT-GLOBAL-062**

**出处**：2026-10-26 第四十八轮（**E69**）。

---

## PIT-GLOBAL-066 🔴 **「命中数」≠「输出行数」** ⇒ 把行数当命中数 ⇒ 报数翻倍  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 本轮要报「本域有多少条状态」这类计数 ⇒「命中数 ≠ 输出行数」需排除。
> **旧号**：`PIT-GLOBAL-066` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-66`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**症状**：`layer4_entity_all` 报「锚点文件里找不到断言提到的实体: **3 条**」
⇒ **实际只有 1 条命中** ⇒ 原因：捕获块里**每条命中占 2 行**（命中行 + 「⇒ 该符号实际在 …」详情行）
⇒ 把**行数**当成了**命中数**。

**修法**：改为**按含「缺失实体:」的行分组计数**。
⚠️ 同款问题在**外部锚点桶**又出现一次（说明行以 `⇒` 开头 ⇒ 被算进「条数」）
⇒ 修法：**只收形如 `path.go:12-34` 的引用行**。

**教训**：⇒ **任何「解析出来的命中」的计数口径 ⇒ 必须按「命中实体」计数，不能按「输出行」计数**
⇒ ⇒ **输出格式一改，计数就错** ⇒ ⇒ 计数口径要**独立于输出格式**钉住。

**同族**：**PIT-GLOBAL-039**（遍历 `set` 做判定 ⇒ 输出不可复现）· **PIT-GLOBAL-062**

**出处**：2026-10-26 第四十八轮（**E69**）。

---

## PIT-GLOBAL-067 🔴 **守门的「展开函数」混用绝对路径与相对路径 ⇒ 两条断言一直是假绿**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮不改 lane 白名单 / 展开函数 ⇒ 本轮不触发。
> **旧号**：`PIT-GLOBAL-067` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-67`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §修法

**症状**：给 `tools/difftest/check-global-lane-ownership.py` 登记一条新 lane
（`global-common`，白名单含 `service/common/src/**`）后，注入一条变异 lane
（白名单**直接写** `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`）
⇒ **守门仍报 `EXIT=0`** ⇒ 「写权重叠」根本没被发现。

**真实原因**：`expand()` 的两个分支返回了**不同形态**的路径：

| 分支 | 返回 |
|---|---|
| 目录通配 `a/b/**` | **绝对路径**（`glob(REPO / …)`） |
| 直接文件 | **相对路径**（`{pattern}`） |

⇒ 两边**永不相交** ⇒ 交集恒为空 ⇒
① 断言 1「在跑 lane 白名单两两不重叠」对「一个写目录 + 一个写目录内文件」**恒不报**；
② 断言 2「共享文件无人拥有」对**目录通配**的 lane **恒不报**
（`shared in expanded[lane]` 是拿相对串去比绝对串）。

**为什么这么久没发现**：`ACTIVE` 长期为空 ⇒ 断言 1 的两两比较**一次都没执行过**；
断言 2 里那 10 个 `SHARED` 条目只被**直接文件**形式的 lane 命中过 ⇒ 也从没暴露。
⇒ **2026-10-27 登记 `global-common` 才第一次点亮这条代码路径** ⇒ 变异当场抓出。

**修法**：新增 `_rel()`，**所有分支统一返回「仓库相对 + POSIX 分隔符」**。

⇒ 修后各 lane 的「白名单展开 N 个文件」**计数一个都没变**（只改了形态）⇒ 无副作用；
⇒ 变异 A（第二条 lane 直接写目录内文件）⇒ `EXIT=1` 且**精确点名**该文件；
⇒ 变异 B（第二条 lane 用目录通配吃共享文件）⇒ `EXIT=1`，**5 项违规**（1 重叠 + 4 共享文件占用）。

**教训**：
⇒ **「守门在跑」≠「守门在判」** —— 一条从未被执行到的断言分支，与不存在的断言**没有区别**；
⇒ ⇒ **新增登记 ⇒ 必须同时做一次变异**（否则新登记的可能只是一道**假绿灯**）；
⇒ ⇒ **集合运算前必须统一元素形态**（路径形态 / 大小写 / 分隔符）⇒ 否则交集恒空，且**静默**。

**同族**：**PIT-GLOBAL-046**（判据的输入必须是「行级局部」⇒ 文件级前缀 = 假阴性）·
**PIT-GLOBAL-048**（变异判据本身也会假阴性）· **PIT-GLOBAL-029**（静态扫描不排除 `target/` ⇒ 恒红）

**出处**：2026-10-27（登记 10 条 `<域>-common` lane 时首次点亮 `ACTIVE` 路径）。

---

## PIT-GLOBAL-068 🔴 **「跑红」也可能是环境串扰 ⇒ 共享服务发现面会把并发探针互相污染** ⇒ **跑红 ≠ 是缺陷**  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— **本轮 E65 的证据来自共享 Nacos 面的探针** ⇒「跑红 ≠ 是缺陷」的对偶面直接适用（本条讲的是「跑红」，E65 是「探针 PASS ⇒ 漂移仍在」，两侧合起来才成立）。
> **旧号**：`PIT-GLOBAL-068` ⇒ **新号（两位序号形态）**：`PIT-GLOBAL-68`（**同一条**；原号原样保留，以免破坏仓内既有引用）。
> **证据**：本条 §判别式 ①②③ ＋ `docs/PhaseGoal-judge.md` §五 X-6

**症状**：2026-10-29 主线在主树独立复跑 `tools/api-full-parity-probe/article/run.sh`
（G3 lane 落的 article 双侧活体探针）⇒ **同一个脚本、同一份代码、同��台机器**，结果**交替出现**：

| 跑次 | 时刻 | 并发 agent 在跑？ | EXIT | 现象 |
|---|---|---|---|---|
| 1 | 08:35:50 | 是 | **0** | ✅ 双侧逐字节一致（3 场景全对）|
| 2 | 08:39:23 | 是 | **1** | 🔴 红在这一行（见下）|
| 3 | 08:4x | 是 | **0** | ✅ 双侧逐字节一致 |
| 4 | 08:4x | 是 | **0** | ✅ 双侧逐字节一致 |

⇒ **3 绿 / 1 红**，且**唯一那次红与并发 agent 在跑的时刻重合** ⇒ **不是确定性缺陷**。

红的那次日志（关键两行）：

```
[grpc-breaker] failure, method=article.ArticleService/GetArticle, code=INTERNAL
BusinessException code=5001 msg=系统内部错误
```

**真实原因**：`article-api` 通过 **Nacos 服务发现**找 `article-rpc`；而 **Nacos 是宿主共享的**
（`127.0.0.1:49848`）⇒ 并发跑其它 agent 的探针时，**同一个 `serviceName` 下可能存在
陈旧实例 / 别的栈的实例** ⇒ 客户端按 Nacos 返回的**实例列表**去连 ⇒ 打到已死或**不属于本轮**的实例
⇒ `INTERNAL` ⇒ 熔断 ⇒ `5001` ⇒ 与冻结期望值不符 ⇒ **探针红**。

⇒ ⚠️ **日志里的铁证**：红的那次 `article-api` **同时发现了 `article-rpc` 和 `user-rpc`**
（`user-rpc` 是**别的 agent** 的栈）⇒ **共享发现面已被污染**。

**怎么证伪 / 怎么确认**（三条判别式，缺一不可）：
① **看日志里「发现到的 serviceName 列表」** —— 出现**本轮不该发现的** serviceName ⇒ 共享面被污染；
② **把「同一脚本 + 同一代码」连续跑 N 次** —— 红绿交替 ⇒ **不是确定性缺陷**（确定性缺陷会稳定红）；
③ **查共享 Nacos 里该 serviceName 的实例数与 IP** —— 确认是否有他栈实例 / 陈旧实例。

⇒ **必须同时排除「代码回归」**（否则会把环境问题误判成回归，或反过来）：
本次合入的 `service/common` 改动对 `RpcInterceptor` / `GoStyleRecoverInterceptor` / `GoStyleAdaptiveShedder`
**全是 javadoc 引用修正（无行为变更）**，且**合入前后各跑过绿** ⇒ 排除。

**留下的守门网 / 修法**：
- 探针应当**独占发现面**（起专属 Nacos / 用独立 group / 按实例 IP 精确寻址）
  ⇒ 否则「跑红」**不能**直接判成「代码缺陷」；
- **结论写法纪律**：凡用**共享中间件**（Nacos / PG / Redis / Kafka）跑出来的红，
  必须在报告里写明「共享 ⇒ 已排除串扰」+ 附判别式 ①②③ 的证据；
- ⚠️ **本条的真正价值是它的对偶面**：AGENTS.md §3.3 只写了「**跑绿 ≠ 对**」，
  本条补上「**跑红 ≠ 是缺陷**」⇒ **两条合起来 ⇒ 任何单次颜色都不能直接当结论**。

**同族**：**PIT-GLOBAL-058**（「就绪判定 = 端口在监听」会被上一轮遗留的进程满足 ⇒ 实验在**环境重建中途**开跑 ⇒ 观测无效）·
**PIT-GLOBAL-066**（「命中数」≠「输出行数」⇒ 报数翻倍）·
**PIT-GLOBAL-067**（守门的「展开函数」混用绝对/相对路径 ⇒ 交集恒空 ⇒ **假绿** ⇒ 与本条同属「**环境/解析面把结论染歪**」族）。

**出处**：2026-10-29 08:35~08:45（R3·C-2 复判时，主线独立复验 G3 lane 的 article 双侧活体探针时踩到）。
