<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow 域踩坑档（为什么会踩）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> 本档只记「**为什么会这样**」，具体漂移条目在 [`docs/DRIFT/follow/`](../../DRIFT/follow/)。

> 🆕 **2026-10-29 台账对齐（W-B lane · follow + task 域）**
>
> - **编号归一**：本档条目的**旧号** `PITFALL-FOLLOW-NNN` **原样保留**在每条的「旧号」行里；**新号** = `PIT-FOLLOW-NN`（与旧号后三位一一对应）⇒
>   ⚠️ **旧号是检索用的历史标识** —— 仓内其它文档（DRIFT / Stage1 / 共享层 javadoc / `tools/`）按**旧号**检索，本档**两套号都在** ⇒ 旧号**不可删**。
> - **每条补三字段**：**状态** · **核对日期（2026-10-29）** · **是否影响当前轮次**（填「否」时必须写明**不适用的具体原因**）。
> - **状态判定口径**（本档统一）：
>   - **已闭环** = 该坑在**本域的具体触发点已被消除**（代码 / 配置 / 脚本已改）**且有证据指针**（`文件:行号` 或守门的实测条数）⇒ **残留的通用教训不改变这个判定**（通用教训留在正文里）。
>   - **仍生效** = 该坑的**触发条件本身**仍在（框架 / 语言 / 真源的事实，或本域的编排脚本仍未改）⇒ 今天照旧做法还会踩。
>   - **已撤销** = 用户 / 决策撤销。**待核实** = 判不了（须写清缺什么）⇒ **不许默认任何一侧**。
> - ⚠️ **状态标记取值域**：`✅` / `🟡` / `🔴` / `⚫`（与仓内既有取值域一致）。

## PIT-FOLLOW-001 · 【已闭环】 测试把生产地址覆盖成 `static://` ⇒ 生产配置从未被跑过

> **旧号**：`PITFALL-FOLLOW-001` ⇒ **新号**：`PIT-FOLLOW-001`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 生产地址已被独立守门钉住（不再靠「测试覆盖生产配置」来暴露问题）⇒ 本轮只核台账、不改配置 ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcProductionAddressTest.java:87-95` 断言生产 yml 必须是 `discovery:///user-rpc`（本轮 surefire 实测 **t=3 f=0 e=0 s=0**） ＋ `service/follow/follow-rpc/src/main/resources/application.yml:163` `address: discovery:///user-rpc`

**症状**：修复前 63 条测试全绿（现为 68 条）、`mvn package` 通过、Spring 上下文在测试里能起来，
但 `java -jar` **起不来**（连续两条启动阻塞）。

**根因**：`service/follow/follow-rpc/src/test/java/.../FollowRpcWiringTest.java:69`
与 `FollowGrpcInterceptorWiringTest.java:63` 显式覆盖了地址：

```java
"grpc.client.user-rpc.address=static://127.0.0.1:1"   // ← 测试专用
```

注释写得很清楚：「⚠️ 必须把 @GrpcClient 的 discovery:// 覆盖成 static://，否则上下文起不来」。

⇒ 于是**生产的 `discovery:///user-rpc` 这条地址，在任何测试里都没有被解析过一次**。
同理，生产端口 8082、生产 Nacos、生产 PG 也都没被测试碰过。

**代价**：本轮的两条启动阻塞（见 [`DRIFT/follow/rpc.md`](../../DRIFT/follow/rpc.md)）
**一条都测不出来** —— 它们只会在「用生产 classpath + 生产配置启动」时暴露。

**教训（可推广）**

1. ⚠️ **测试里覆盖掉生产配置，等于把那段配置从测试覆盖里挖掉** ——
   覆盖得越多，测试的代表性越差，而「测试全绿」的信号越强、越误导。
2. ⚠️ **「上下文能起来」不等于「服务能起来」**（AGENTS.md §3.3 已有此条）。
   本例更进一步：**测试上下文能起来，生产起不来**。
3. ⇒ **每个模块接手的第一件事仍是「先让它真的启动一次」**（AGENTS.md §0）。
   本轮实测就是靠 `java -jar` + 真 PG + 真 Nacos 才挖出这两条。

**怎么补网**：给每个组件补一条**真启动**的守门测试（或至少一条 CI 步骤），
用**生产 classpath**（`spring-boot:run` / `java -jar`）+ **生产配置**跑，
断言「进程起得来且端口在听」。mock 一切的单测**证明不了这件事**。

---

## PIT-FOLLOW-002 · 【已闭环】 `test` scope 会顶掉 starter 的 `compile` 传递依赖

> **旧号**：`PITFALL-FOLLOW-002` ⇒ **新号**：`PIT-FOLLOW-002`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 依赖作用域已是 compile ⇒ 运行时 classpath 缺陷的触发条件已消除 ⇒ 本轮只核台账、不改 pom ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-rpc/pom.xml:256-260` 的 `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）；同文件 `:236-254` 的注释明写「scope 必须是 compile（默认），不能是 test」

**症状**：`NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`
⇒ `UserRpcClient` Bean 创建失败 ⇒ **整个服务起不来**。

**根因**：Maven 的「**最近者优先**」——

```
net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE
  └─ io.grpc:grpc-inprocess:1.63.0  (compile)        ← starter 自己就带着

service/follow/follow-rpc/pom.xml
  └─ io.grpc:grpc-inprocess:1.63.0  (test)            ← 直接声明，永远压过传递声明
```

⇒ 运行时 classpath 上 `grpc-inprocess` **整个消失**。

**为什么测试测不出来**：测试跑在 **test classpath** 上，`grpc-inprocess` 本来就在。
⇒ **测试 classpath 与运行时 classpath 的差集，正好就是缺陷所在**。

**判据**：`grpc-core:1.63.0` **不含** `io/grpc/inprocess/*`（实测 `unzip -l | grep -c` = 0），
该类只在 `grpc-inprocess` 里 —— 所以它是**硬依赖**，不是可选优化。

**教训**

1. ⚠️ **`<scope>test</scope>` 不是「只影响测试」**：它会**顶掉**同名依赖的传递作用域。
   写 test scope 前先问：这个 artifact 在**运行时**是不是必需的？
2. ⚠️ 排查 classpath 缺失的快捷手法：
   ```bash
   mvn -pl <module> dependency:tree -Dincludes=<groupId>:<artifactId>   # 看 scope
   mvn -pl <module> dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -DincludeScope=runtime
   ```
3. ⚠️ **域外同款**：4 个 rpc 模块仍是 `test` scope ⇒ 建议全仓统一排查。

---

## PIT-FOLLOW-003 · 【已闭环】 devh 的 `discovery:` URI 必须**三斜杠**

> **旧号**：`PITFALL-FOLLOW-003` ⇒ **新号**：`PIT-FOLLOW-003`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— URI 已是三斜杠 ⇒ devh 的 `uri.getPath()` 判据不再触发 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-rpc/src/main/resources/application.yml:163` `address: discovery:///user-rpc` ＋ `FollowRpcProductionAddressTest.java:84-95`（三斜杠断言 · 本轮实测 t=3）

**症状**：
```
java.lang.IllegalArgumentException: Incorrectly formatted target uri;
  expected: 'discovery:[//]/<service-name>'; but was 'discovery://user-rpc'
```

**根因**：`net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory#newNameResolver`
判的是 **`uri.getPath()`**：`path == null || path.length() <= 1 || !path.startsWith("/")` ⇒ 抛错。

| 写法 | `getPath()` | 结果 |
|---|---|---|
| `discovery:///user-rpc` | `"/user-rpc"` | ✅ |
| `discovery://user-rpc` | `""`（服务名被解析成 authority） | ❌ |

**教训**

1. ⚠️ **两斜杠不是「少写一个斜杠」**：它把服务名搬到了 authority 位置，path 留空。
2. ⚠️ **本仓现状**：**所有 api 模块都是三斜杠**，**所有 rpc 模块都是两斜杠** ——
   说明这是 rpc 层单方面的写法偏差，不是"哪种都行"。
3. ⇒ 判断依据必须是**框架源码**（这里是 devh 的字节码），不能凭直觉。

---

## PIT-FOLLOW-004 · 【已闭环】 同一域两个模块对**同一个环境变量**给出两种语义

> **旧号**：`PITFALL-FOLLOW-004` ⇒ **新号**：`PIT-FOLLOW-004`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 两个模块的 `NACOS_ADDR` 语义已一致 ⇒ 不存在「两台 Nacos」的触发条件 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-api/src/main/resources/application.yml:58` 与 `service/follow/follow-rpc/src/main/resources/application.yml:95` **两侧同为** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}`（`NACOS_PORT` 两侧都被消费）

**症状**：follow-rpc 自己日志 `NacosRegistration registered: service=follow-rpc ...`，
但 follow-api 一直报 `No servers found for follow-rpc`。

**根因**：

| 模块 | 写法 | `NACOS_ADDR` 的语义 | `NACOS_PORT` |
|---|---|---|---|
| `follow-api` | `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` | host | **用** |
| `follow-rpc` | `${NACOS_ADDR:nacos:8848}` | **host:port** | **忽略** |

⇒ 任何按 api 侧语义部署的环境下，**rpc 连 `host:8848`、api 连 `host:port`**
⇒ 两个组件连**两台 Nacos** ⇒ rpc 注册在 A、api 在 B 找它 ⇒ 所有业务码塌成 **1015 service busy**。

**教训**

1. ⚠️ **两个 yaml 各自看都"对"，只有放在一起才冲突** ——
   这类缺陷**不可能靠单文件 review 发现**。
2. ⇒ 同一个域（或同一套部署）的所有组件，**环境变量契约必须集中定义**（例如根 `application.yml` + 共享配置），
   而不是每个模块各写各的默认值。
3. ⚠️ **「注册成功」的日志不等于「对端能发现」** —— 必须**从消费方**验证一次。

---

## PIT-FOLLOW-005 · 【已闭环】 共享 `service/common` 在纯 gRPC 进程里缺 servlet-api

> **旧号**：`PITFALL-FOLLOW-005` ⇒ **新号**：`PIT-FOLLOW-005`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 共享层已把 HTTP 池回收挪进 `@ConditionalOnWebApplication(SERVLET)` 的独立 Bean ⇒ 纯 gRPC 进程的关闭路径不再加载 servlet 类 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:36`（rpc 进程不扫 `com.rag.common`） ＋ `:85-94`（destroy 只碰 `RpcInterceptor`，HTTP 池归 `HttpPoolLifecycle`） ＋ 守门 `service/common/src/test/java/com/rag/common/spring/HttpPoolShutdownWithoutServletGateTest.java` 存在

**症状**：follow-rpc **启动失败后的关闭过程**里抛

```
WARN ... Destroy method on bean with name 'com.rag.common.spring.ObservabilityLifecycle'
      threw an exception: java.lang.NoClassDefFoundError: jakarta/servlet/Filter
```

**根因**：`service/common` 的 `ObservabilityLifecycle` 的 destroy 路径引用了
`jakarta.servlet.Filter`，而 **follow-rpc 的运行时 classpath 里没有 servlet-api**
（它是无 Web 容器进程，只引了 `spring-boot-web-server` 供 nacos-discovery 反射用）。

**影响**：**不影响启动**，只在进程关闭路径上抛 ⇒ 污染关闭日志、掩盖真正的关闭原因。

**状态**：🔴 **未修** —— `service/common` 是**共享模块**（被 16 个组件依赖），
本工作树**不碰域外/共享文件** ⇒ 登记为**跨域待办**，待主会话派单。

> ⚠️ **2026-10-29 真实性复核**：上面这段「**状态**：🔴 **未修**」与「**影响**」段是 **2026-10-24 的原始取证**，保留不改写；
> **当前状态读上方「状态」行 = 【已闭环】**（共享层 2026-10-27 已落码）。
> **复核依据（当场核过）**：共享层 `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:53-54` = `@ConditionalOnWebApplication(type = SERVLET)` + `class HttpPoolLifecycle` ＋ `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:85-94` = 「destroy **只碰** `RpcInterceptor`；HTTP 池改由 `HttpPoolLifecycle` 关」⇒ ⇒ **纯 gRPC 进程的关闭路径已不再加载 servlet 类** ⇒ 本条**维持【已闭环】**。
> **附带（域侧前提也已变）**：`service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web`（2026-10-29 补，起因 = 指标出口）⇒ ⇒ 「**follow-rpc 的运行时 classpath 里没有 servlet-api**」这句话**对 follow-rpc 已不再成立**（⇒ 域侧触发条件消失；**共享层那半边的修法价值仍在** —— 其它仍无 servlet 容器的 rpc 组件依旧受益）。

---

## PIT-FOLLOW-013 · 【仍生效】 shell 的两处**词法**陷阱：`&` 未加引号 / `$var` 紧跟多字节字符

> **旧号**：`PITFALL-FOLLOW-013` ⇒ **新号**：`PIT-FOLLOW-013`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 这是 **bash 3.2 的词法事实**（本机 `bash --version` = 3.2.57）⇒ 触发条件永远存在 ⇒ 但本轮**不改任何脚本**（脚本不在本 lane 写权内）⇒ 不影响本轮台账产出
> **证据**：本文件正文实测记录（`N=12; echo "$N，x"` ⇒ `x`；`${N}，x` ⇒ `12，x`） ＋ 同源条目 `docs/Pitfalls/task/rpc.md` 的 `PIT-TASK-15` / `PIT-TASK-21`

**症状 1：路径里的 `&` 被当成后台操作符**

```
$ cd /Users/pr/Documents/java&golang/RAG.worktrees/follow
/bin/bash: line 0: cd: /Users/pr/Documents/java: No such file or directory
/bin/bash: golang/RAG.worktrees/follow: No such file or directory
```

⇒ bash 把 `&` 当**后台操作符** ⇒ 命令被切成两半：
`cd /Users/pr/Documents/java`（后台执行）+ `golang/RAG.worktrees/follow`（另一条命令）。
本仓路径含 `&` ⇒ **每条涉及仓库路径的命令都必须加引号**。

**症状 2：`$var` 紧跟多字节字符 ⇒ 值被吞进变量名**

```bash
$ N=12; echo "$N，x"
x                 ← 值没了，逗号也没了
$ N=12; echo "$N x"
12 x              ← 中间有空格就没事
$ N=12; echo "${N}，x"
12，x             ← 花括号写法正确
```

⇒ bash 3.2 把 `$N` 后面紧跟的**多字节字符**当作**变量名的一部分** ⇒ `N，` 成了一个不存在的变量名
⇒ **值直接丢失**（`set -u` 下还会直接报 `unbound variable` ——
本轮的探针脚本就是这样在最后一行挂掉的：`line 51: N?: unbound variable`）。

**教训**

1. ⚠️ **变量后面紧跟任何非标识符字符（尤其是中文/全角标点）时，一律写 `${var}`**。
2. ⚠️ **含 `&`、`;`、`(`、`)`、`$` 的路径必须整体加引号**：`cd "...java&golang/..."`。
3. ⚠️ 这两条与 PITFALL-FOLLOW-010（探针假绿）、PITFALL-FOLLOW-011（装置形态选错）同源：
   **「看起来在跑、其实跑的不是你以为的东西」—— 假绿比红贵。**

---

## PIT-FOLLOW-014 · 【仍生效】 真源自身就有竞态：**检查黑名单**与**写入关注**不在同一事务

> **旧号**：`PITFALL-FOLLOW-014` ⇒ **新号**：`PIT-FOLLOW-014`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`⚠️`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 缺陷在**冻结真源（Go）**里 ⇒ RAG 侧无从修 ⇒ 修它 = 改真源行为 ⇒ 属**产品决策**（需用户拍板）⇒ 不在本轮 scope ⇒ 不影响本轮台账产出
> **证据**：Go `service/follow/rpc/internal/logic/followlogic.go:48`（`ExistsAnyBlock` SELECT）与 `:60`（`CreateFollow` INSERT）**不在同一事务**；对照 `service/follow/rpc/internal/logic/blocklogic.go:48`（单事务）

**现象（U8 并发实测，各 40 轮）**

```
Go  : 破窗（block 与 follow 并存）37/40
RAG : 破窗（block 与 follow 并存）32/40
```

**根因（Go 侧）**

- `service/follow/rpc/internal/logic/followlogic.go:48`：`ExistsAnyBlock`（**SELECT**）
- 同文件 `:60`：`CreateFollow`（**INSERT**）
  ⇒ **两句不在同一个事务里** ⇒ 检查与写入之间存在窗口。
- 而 `service/follow/rpc/internal/logic/blocklogic.go:48` 的 `CreateBlockAndCleanup`
  是**单事务**（`model/follow.go:33-54`：`conn.WithContext(ctx).Transaction(...)`，
  插 block + 双向删 follow 原子完成）。

⇒ 破窗路径：T1 的 SELECT 早于 T2 的 INSERT，而 T1 的 INSERT 晚于 T2 的 DELETE
⇒ **block 与 follow 同时存在**，违反「拉黑 ⇒ 不可关注」的不变式。

**结论**：**两侧都有这个缺陷**（Go 37/40、RAG 32/40，同一量级）
⇒ **这不是 RAG 的漂移**，是**真源的设计缺陷**。
按 AGENTS.md 的判据（「RAG 与 Go 不一致 ⇒ 漂移」）⇒ **不进 DRIFT 档**，进本档。

**教训**

1. ⚠️ **1:1 复刻会把真源的缺陷一起复刻** —— 这正是「以 Go 为唯一真源」的代价，
   必须**显式记录**，否则后来人会把「两边都错」当成「RAG 对齐得好」。
2. ⚠️ **要证明「不是漂移」，必须两侧同口径压测**：
   本轮把并发窗口做成两侧**完全相同**的装置（同一 PG、同一对 user/target、同一并发方式），
   才敢说「共有缺陷」。
   **只测 RAG 一侧 ⇒ 只能证明 RAG 有问题，不能证明 Go 没问题**（AGENTS.md §3.3）。
3. ⚠️ 修不修是**产品决策**（要修就得两侧都改 ⇒ 属于「改真源行为」，需用户拍板），
   **不在对齐 scope 内**。

---

## 🆕 **2026-10-29 真实性复核回执**（W-B lane · follow 域 · 本文件）

| 编号 | 状态（本轮核后） | 依据（核对日期 2026-10-29） | 改判 |
|---|---|---|---|
| `PIT-FOLLOW-001` | 已闭环（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:163` = `address: discovery:///user-rpc`（**生产地址是三斜杠**）＋ 守门 `…/FollowRpcProductionAddressTest.java:87-95` 存在 ⇒ 「测试把生产地址覆盖成 `static://`」的形态已由**读生产 yml 的守门**堵住 | 否 |
| `PIT-FOLLOW-002` | 已闭环（不变） | RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）⇒ 运行期 classpath 有该类 | 否（**跨域子结论已订正**：域外 4 个模块的 `test` scope 现已不存在） |
| `PIT-FOLLOW-003` | 已闭环（不变） | 同上 `application.yml:163` = `discovery:///user-rpc`（**三斜杠**） | 否 |
| `PIT-FOLLOW-004` | 已闭环（不变） | RAG `service/follow/follow-api/…/application.yml:58` ＋ `service/follow/follow-rpc/…/application.yml:95` = **同一套** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ 两端语义一致 | 否 |
| `PIT-FOLLOW-005` | 已闭环（不变） | 共享层 `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:53-54` + `ObservabilityLifecycle.java:85-94` ⇒ 关闭路径不再加载 servlet 类（**正文订正见本条上方**） | 否 |
| `PIT-FOLLOW-013` | 仍生效（不变） | 属**shell 词法**形态 ⇒ 与代码无关；本轮未复现 | 否 |
| `PIT-FOLLOW-014` | 仍生效（不变） | 属**真源自身**的竞态（Go `service/follow/api/internal/middleware/checkblacklistmiddleware.go` 的「检查黑名单」与「写入关注」不同事务）⇒ 真源冻结 ⇒ 形态仍在 | 否 |

### 仍未核到（**待核实**）

| 编号 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `PIT-FOLLOW-013` 的**触发条件复现** | 需真实多字节 shell 场景 | 未复现；状态沿用 2026-10-29 对齐 lane 的结论 |
