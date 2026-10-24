<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow 域踩坑档（为什么会踩）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> 本档只记「**为什么会这样**」，具体漂移条目在 [`docs/DRIFT/follow/`](../../DRIFT/follow/)。

## PITFALL-FOLLOW-001 🔴🔴 测试把生产地址覆盖成 `static://` ⇒ 生产配置从未被跑过

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

## PITFALL-FOLLOW-002 🔴 `test` scope 会顶掉 starter 的 `compile` 传递依赖

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

## PITFALL-FOLLOW-003 🔴 devh 的 `discovery:` URI 必须**三斜杠**

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

## PITFALL-FOLLOW-004 🔴 同一域两个模块对**同一个环境变量**给出两种语义

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

## PITFALL-FOLLOW-005 🔴 共享 `service/common` 在纯 gRPC 进程里缺 servlet-api

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

---

## PITFALL-FOLLOW-013 ⚠️ shell 的两处**词法**陷阱：`&` 未加引号 / `$var` 紧跟多字节字符

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

## PITFALL-FOLLOW-014 ⚠️ 真源自身就有竞态：**检查黑名单**与**写入关注**不在同一事务

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
