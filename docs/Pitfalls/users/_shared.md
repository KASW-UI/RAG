<!-- @move AGENTS.md L1692-L1706 · §3.3.3 第四类完整经过：mybatis 前缀写错，注入静默归零（B-1 实验） -->
**第四类的完整经过（2026-10-21，B-1 实验）**：为验证 `setQueryTimeout` 能否缓解 B-1，
`run.sh` 注入 `--mybatis.configuration.default-statement-timeout=2`。**前缀错了** ——
MyBatis-Plus 的前缀是 `mybatis-plus`（`MybatisPlusProperties`），全仓**没有**任何
`@ConfigurationProperties(prefix="mybatis")`。结果：启动无 warn、无报错、jar 正常起、
**PG 阻塞查询照旧存活 4.5s，效果恒为零**。改对前缀后同一实验立刻出效果。

> 绑定链路本身是通的、已逐段核实，**不要**误判成「MP 不支持该属性」：
> `--mybatis-plus.configuration.default-statement-timeout=2`
> → `MybatisPlusProperties$CoreConfiguration.defaultStatementTimeout`（`Integer`，有 getter+setter）
> → `MybatisPlusAutoConfiguration.applyConfiguration()` 调 `CoreConfiguration.applyTo(MybatisConfiguration)` 搬运
> → `MybatisConfiguration.defaultStatementTimeout`
> → MyBatis `BaseStatementHandler` 调 `statement.setQueryTimeout(int)`
> → pgjdbc `StatementCancelTimerTask` → 向 PG 发 **CancelRequest**。
> 另外 `user-rpc/application.yaml` 里 `mybatis-plus.configuration.map-underscore-to-camel-case`
> 一直生效（否则实体映射全崩），本身就是「`configuration` 子块绑定正常」的现成旁证。
<!-- @move AGENTS.md L1790-L1811 · §3.3.3 46 处批量插入：为什么必须配守恒断言 -->
issue #71187874 用脚本往两个 DomainService 插了 46 处
`catch (StatusRuntimeException e) { throw e; }`（user-rpc 17 + admin-rpc 29）。
逐点行为测试只覆盖到 8 个方法，**其余 38 处当时只有「代码写进去了」这一层保证** ——
而「代码存在」正是上一条规则里那个 H2 假绿的形态。

补法：新增两个**结构性元测试**，用 Spring 自带的 `org.springframework.asm`
读 class 文件的异常表，断言每个 `catch(Exception)` handler **前邻**都有一条
`catch(StatusRuntimeException)`：

| 用例 | 覆盖 | 断言 |
|---|---|---|
| `UserDomainServiceCatchStructureTest` | 18 处 catch(Exception) / 18 处放行 | 前邻断言 + 守恒计数 |
| `AdminDomainServiceCatchStructureTest` | 29 处 / 29 处 | 同构副本 |

**两条断言必须成对**：只有前邻断言时，删掉一个 `catch(Exception)` 会让总数"看起来对得上"；
只有计数断言时，新增一处不写放行分支也看不出来。变异测试已实证两者都能变红
（删掉 `login` 的放行分支后，前邻断言报「catch 序列 = [NotFoundException, Exception]」、
计数断言 `expected: 18 but was: 17`）。

⚠️ 扫描必须用**字节码**而非源码：javac 生成的异常表里，同一个 try 的多个 catch 共享
`(from, to)` 且顺序即源码顺序，这才使「前邻」可判定；源码扫描依赖缩进与文本形态。


---

## PIT-USERS-SHARED-001 🔴 固定 gRPC 端口 + 并行会话 ⇒ 整个 ApplicationContext 起不来，用例显示为「errors」而非「端口冲突」

**症状**：新建的 `UserRpcPrimaryKeySourceParityTest` 4 条用例全部报

```
IllegalState: ApplicationContext failure threshold (1) exceeded:
  skipping repeated attempt to load context for [MergedContextConfiguration@2ed9e59b ...]
```

看着像「测试类本身写错了」或「DDL 没建出来」，**实际上一个断言都没执行**。

**真实原因**：`Failed to start bean 'shadedNettyGrpcServerLifecycle'`
→ `Failed to bind to address 0.0.0.0/0.0.0.0:49210` → `BindException: Address already in use`。

`service/users/user/user-rpc/src/main/resources/application.yaml:81` 把 gRPC 端口**钉死**在
`49210`（注释写「修 INFRA-001：9004 → 49210，Infrastructure.md §3 锁定」）。
而本仓库**同时有 8+ 条并行 worktree 会话 + 外部 difftest 容器**，
实测 `lsof -iTCP:49210` 显示占用者是 **`com.docke[r]`** —— 另一个会话的容器栈。

**为什么这条特别阴**：根因是「端口」，
但症状被 Spring 包装成「**上下文加载失败**」，再被 surefire 包装成「**测试 error**」。
⇒ 你会先去怀疑测试代码、Spring 配置、Testcontainers，而**永远不会想到端口**。

**怎么确认**：`lsof -nP -iTCP:<端口> -sTCP:LISTEN` 看占用者是谁。
若是 Docker 且**不是自己的容器** ⇒ 是并发会话，不是自己写错了。
**绝不要去 kill 别人的容器**（§5.3：工作树之间不互相动状态）。

**怎么修**：只关心数据层/装配的测试**根本不需要真的监听端口** ⇒ 用临时端口：

```java
@SpringBootTest(classes = UserRpcApplication.class,
        properties = "grpc.server.port=0")   // 0 = 内核分配，天然不会撞
```

**留下的守门网**：`UserRpcPrimaryKeySourceParityTest` 已如此配置；
本条同时是对「§5.3 并行 worktree 工作流」的一个副作用提示 ——
**固定端口 + 并发会话 = 必然互相假红**，端口类资源应当一律临时化。

> 同类的还有 §3.5 里那句「共享索引文件合并后统一更新」：
> 凡是**多个会话会同时用同一份固定值**的资源（端口、库名、Redis db 号），
> 都要按「会被别人占/被改」来设计，而不是按「只有我在用」。

---

## PIT-USERS-SHARED-002 🔴 Docker 内存配额只有 7.65 GiB ⇒ 小容器也起不来，**别把它当成代码信号**

**症状**：`mvn test` 偶发
```
ContainerLaunch Container startup failed for image redis:7-alpine
```
或 `postgres:16-alpine`。同一份代码、同一个测试，**上一轮绿、这一轮红**，
重试几次又能过 ⇒ 极易误判成「我刚改的东西有问题」。

**真实原因**（实测，不是猜测）：
```bash
docker info   | grep -i "Total Memory"     # → Total Memory: 7.653GiB
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}'
```
本机 Docker Desktop 只分到 **7.653 GiB**；当前跑着约 **30 个容器**，
Kafka ×3（943 + 802 + 770 MiB）、Nacos ×2（943 + 708 MiB）、
Neo4j 399 MiB、Grafana 362 MiB …… 已吃掉大半。
⇒ **连 `redis:7-alpine`（几十 MB）都分不到**。

本轮为这件事连撞 **3 次**，且**不能**去 kill 别人的容器（§5.3：并行 worktree
之间不互相动状态；那些 Kafka/Nacos 属于 Go 侧栈和其他会话）。

**怎么区分「基础设施抖动」与「真回归」**：

| 现象 | 判定 |
|---|---|
| `ContainerLaunch … failed for image <很小的镜像>` | 🔴 基础设施。**先查 docker stats，别碰代码** |
| 用例 `assertion failed` 且断言内容具体 | ✅ 真回归 |
| 同一用例**反复时绿时红**、且红的是启动阶段 | 🔴 基础设施 |

**处置原则：让护网不要依赖容器，除非它真的在测容器**。
本轮据此把 `AvatarHistoryIgnoresBodyTest`（测「Spring 会不会解析 body」）
从真 Redis 全上下文改成 `standaloneSetup` + 真 advice ——
**零容器、4.7 秒**，且判别力不变（变异验证照样红）。
端到端那条仍保留真 Redis，两层并存。

> 这条与 [`PIT-USERS-SHARED-001`](./_shared.md)（固定 gRPC 端口被并行会话占）
> 是同一类问题：**§5.3 的并行 worktree 工作流在「文档里写共享索引」那部分是完备的，
> 但「共享机器资源」这一层没有任何约定** —— 端口、内存、镜像拉取都会互撞。

---

## PIT-USERS-SHARED-003 🔴 探真源 `internal` 包会撞 Go 的 internal 规则 ⇒ 用 `go run -overlay` 虚拟注入，别复制源码

**症状**：想在 `/tmp` 建探针模块、`replace sea-try-go => <真源路径>`，
直接 import 真源的 model 包：

```go
import "sea-try-go/service/user/user/rpc/internal/model"
```
```
main.go:7:2: use of internal package sea-try-go/service/user/user/rpc/internal/model not allowed
```

**为什么会这样**：Go 的 `internal` 规则看的是**导入路径前缀**，不是文件系统的真实位置。
`.../rpc/internal/model` 只能被 `.../rpc/` 下的包导入；
探针模块的包在 `/tmp`，前缀对不上 ⇒ 无论怎么 `replace` 都不通。

**三条路的取舍**：

| 方案 | 结论 |
|---|---|
| 把真源 model 包复制到 `/tmp` 再跑 | ❌ **违反 §3.3 的「不得复制源码重跑」** —— 复制的那份可能已经被人改过，证的是复制品不是真源 |
| 把探针写进真源目录 | ❌ 真源**冻结只读**（§7.1）；且会污染真源工作区 |
| **`go run -overlay`** | ✅ **正解** |

**做法**：用 overlay 把一个**只存在于 `/tmp`** 的探针文件，**虚拟挂载**到真源模块树里：

```json
// /tmp/eiprobe/overlay.json
{"Replace": {
  "<真源>/service/user/user/rpc/internal/zzprobe/main.go":
  "/tmp/eiprobe/zzprobe/main.go"
}}
```
```bash
cd <真源> && CGO_ENABLED=0 \
  go run -overlay /tmp/eiprobe/overlay.json sea-try-go/service/user/user/rpc/internal/zzprobe
```

探针包路径落在 `.../rpc/internal/zzprobe` ⇒ 前缀合法 ⇒ **能 import 真源的 `model` 包**；
而真源目录**磁盘零写入**（overlay 是编译期虚拟映射，`git status` 干净）。

**配套两条**：
1. `CGO_ENABLED=0` —— 走真源测试时必加，否则 `runtime/cgo` 会因缺 `stdlib.h` 报
   `fatal error`，看起来像代码问题其实是工具链问题；
2. 真源自带测试也会被这个手段救活。例如
   `service/user/user/rpc/internal/logic/getuser_status_test.go` 内部
   `db.AutoMigrate(&model.User{})` —— 用 `KNOWLEDGE_TEST_DSN` 指到隔离库即可
   跑通**真源自己的建表路径**，连探针都不用写。

**本轮用它取到的硬证据**（`users` + `user_avatar_history` 全列活体类型）见
[`docs/DRIFT/users/user/rpc.md`](../../DRIFT/users/user/rpc.md) §4 —— 
其中 `extra_info` 为 **`text`** 这一条，此前只是从 `postgres.go` 的 `DataTypeOf`
推导，本轮升级为**建表语句级实证**。

> 这条的通用价值：凡「真源在 `internal/` 下、而探针必须不复制源码」的组合，
> 以前只能放弃取证或退回复制。overlay 把这条路补上了 ——
> **「拿不到证据」从此不再是「判定为等价」的正当理由。**

---

## PIT-USERS-SHARED-004 🔴 改了跨模块的**资源文件**后跑下游测试，**不带 `-am` ⇒ 测的是 `~/.m2` 里的旧副本**

**症状**：改了 `users-common/src/main/resources/db/01_create_user.sql`（DDL），
`target/classes` 里也确认是新的，**但 user-rpc 的集成测试仍报**
```
ERROR: column "extra_info" is of type jsonb but expression is of type character varying
```
且列类型断言直接给出 `expected: <text> but was: <jsonb>`。
—— DDL 文件明明改了，运行时却还是旧的。

**根因**：`mvn -pl service/users/user/user-rpc test` **不带 `-am`** 时，
Maven 不会重建上游模块，`users-common` 是从**本地仓库的已安装 jar** 里解析的：

```bash
# 三处时间戳一比就露馅
ls -l ~/.m2/repository/com/rag/users-common/0.0.1-SNAPSHOT/users-common-0.0.1-SNAPSHOT.jar
unzip -p  ~/.m2/repository/com/rag/users-common/0.0.1-SNAPSHOT/users-common-0.0.1-SNAPSHOT.jar \
      db/01_create_user.sql | grep -n "extra_info  "
# ⇒ 里面还是   extra_info  JSONB      ← 23:10 的旧副本
#   而 src 里已经是 extra_info  TEXT    ← 23:29
```

⚠️ **注意 `target/classes` 是对的也会被骗过**：`compile` 阶段确实刷新了
`users-common/target/classes/db/01_create_user.sql`，但 **user-rpc 的测试运行时
classpath 用的是仓库里的 jar，不是那个目录** ⇒ 「看 `target/classes` 确认过了」这个动作
在这里**给不出任何保证**。

**为什么这条比「测试红了」严重**：它能造出**假绿**。
本轮若守门断言写的是 `assertEquals("jsonb", …)`，对着旧 jar 就会**通过**，
而真机上早已是 text —— 测试绿着，漂移留着。
⇒ 与 §3.3「绿测试可能是错误答案的守卫」同型，但成因在**构建**层而不是断言层。

**处置**：改过**跨模块的资源/配置/DDL**（不只是 `.java`）之后，
跑下游模块测试**一律先重装上游**：

```bash
mvn -o -pl service/users/users-common install -DskipTests
mvn -o -pl service/users/user/user-rpc test
```
或直接 `mvn -o -pl service/users/user/user-rpc -am test`（连带跑上游自己的测试）。

**判定口诀**：报错的列/表/配置**刚改过**，而 `target/classes` 看着是新的 ⇒
**先怀疑 classpath 用的是 `~/.m2` 的旧 jar**，别先去读业务代码。

---

## PIT-USERS-SHARED-005 🔴 二阶后果会**掩盖真凶** —— 看到 503 别急着找「谁短路了」，要找「谁把它喂成 500 的」

**症状**：给 user-api 补 `GoStyleGrpcClientBreakerE2ETest`（客户端熔断的端到端守网），
**首跑就红**：

```
AssertionFailedError: 熔断打开时 Go 走的是**业务码**路径… 实际 body =  ==> expected: <200> but was: <503>
```

⚠️ **503 + 空 body**。第一反应是「入口侧的 `GoStyleBreakerFilter` 提前短路了请求」——
因为 503 空 body 正是 Go `breakerhandler.go:27` / `maxconnshandler.go:34` /
`sheddinghandler.go:40` 三处的**共同形状**，看起来就是「某个闸门开了」。

**真凶完全不在那里**。完整链条是：

| 步 | 发生什么 | 位置 |
|---|---|---|
| ① | 客户端熔断打开 ⇒ 抛 `GoStyleCircuitOpenException`（**刻意不是** `StatusRuntimeException`） | `GoStyleGrpcClientBreakerInterceptor.java:200-206` |
| ② | user-api 的 `GlobalExceptionHandler` **没有**这一支 ⇒ 落进兜底 `handleAny` ⇒ **HTTP 500 + 业务码 5001** | 修前的 `GlobalExceptionHandler` |
| ③ | 入口侧 breaker 的成败判据是 `capturedStatus < 500`（对齐 Go `breakerhandler.go:33`）⇒ **500 被记成失败** | `GoStyleBreakerFilter.java:124-128` |
| ④ | 入口 breaker 累积失败后打开 ⇒ `setStatus(503)` + **空 body** | `GoStyleBreakerFilter.java:110-115` |

⇒ **我们看到的 503 是第 ④ 步的产物，真凶在第 ② 步。**

⚠️ **如果不追这条链，就会去「修入口熔断」**（调阈值、改判据、放宽 500）——
那**既治不了病**，还会**偏离 Go**（Go 的业务错误恒为 HTTP 200，入口 breaker 本就不该因它打开）。

**判定口诀**：**观测到的响应码 ≠ 真凶**。
遇到「某个闸门/阈值被打开」的现象，先问「**它是被什么喂开的**」，
再问「**谁在喂它**」——按 **① 真凶 → ② 传播路径** 的顺序追，
**不要从观测到的那个码倒推**。

**为什么这类漂移能活这么久**：
出问题的机制**本身是好的**（客户端熔断装配正确、拒绝行为正确），
坏的是**它下游的异常映射**——而那部分**从来没有测试**。
⇒ 印证 §3.3.1：**读到「注解在、配置对」不能推出「行为对」**；
必须有一条**从机制入口一直观察到对外响应形状**的端到端网。

**可复用的排查手法**：把 surefire XML 里的**应用日志**按时间顺序读一遍，
用「谁先打印」确定因果顺序。
本轮的关键是这两行**相邻**出现：

```
[grpc-breaker] dropped, circuit open, method=user.UserService/Login
GlobalExceptionHandler : unhandled exception
```

—— 这两行的相邻性就是「熔断异常被兜底吃掉了」的现场证据。
⚠️ **只看断言消息（「expected 200 but was 503」）是追不出这条链的**：
503 只告诉你「最后长这样」，不告诉你「第一件出错的事是什么」。

**处置**：把兜底分支**显式拆细**，让每一类失败都有**专属的映射点**；
再给每一类补一条**断言到对外响应形状**（状态码 + 业务码 + 指标）的网。
⇒ 本轮落地：`@ExceptionHandler(GoStyleCircuitOpenException.class)` 单独一支（与另外 4 个域同款）。

---

## PIT-USERS-SHARED-006 🔴 变异验证脚本把「**没跑**」当成「**红了**」⇒ 三个「全红」全是假的

**症状**：给 `DRIFT-USERAPI-012`（降载 RT 统计口径）写变异验证脚本，
逐个变异 → 跑测试 → 读 surefire 报告 → 判定。
第一版脚本的判定是：

```python
red = "Failures: 0" not in summary          # ← summary 是所有测试类的报告拼起来的
```

三个变异全部输出 **「RED ✅」**，而且**三次都是同一条断言消息**，
看着像护网非常给力。

**真相**：三个变异的 surefire 报告**根本没生成**（`NO_REPORT`）——
测试**一次都没跑**，因为命令行写成了 `-Dtest=A+B`，
而 surefire 要的是**逗号**分隔的 `-Dtest=A,B`。
⇒ 脚本把「报告缺失」当成了「有失败」⇒ **三个「RED」全是假的**。

⚠️ **修好命令之后，判定逻辑又栽了一次**（这次是**反向**的假红）：
改成逗号后测试确实跑了、也确实红了，但脚本判成 **NOT-RED** ——
因为两个测试类合并成一段字符串，其中**通过的那个类仍然带着 `Failures: 0`**，
于是 `"Failures: 0" not in summary` **永远为假**。
⇒ 第三次才把判定改成**按类**统计。

⚠️ **为什么这个坑特别危险**：变异验证的**全部价值**在于
「测试确实跑了、确实红在**断言**上」。一旦「没跑」被混进「红了」，
这一轮的结论**看起来比真结论更强**（3/3 全红），而实际上**一条证据都没有**。
⇒ 比「忘记跑变异」更坏：**忘记跑**会留下空白，**混进去**会留下**假证据**。

**判定口诀**：
1. **「没有报告」与「报告里没有失败」是两件事，必须分开判。**
   凡是「靠报告内容判断红绿」的脚本，都要**先确认报告存在**。
2. **「编译失败」也不是「红」** —— 那是构建红，不是断言红（§3.3 的老规矩）。
3. **多个测试类必须按类统计**，不能在合并字符串里做子串判断。

**处置**：
- 脚本把「报告不存在」单独判成 `NOT-RUN`，**绝不**并入 RED；
- 变异前后各跑一次**基线**（无变异），确认基线是绿的**且报告存在** ——
  基线报告缺失 ⇒ 命令本身写错了，后面所有「RED」全部作废；
- 打印**断言消息原文**（不只是 `Failures: 1`），确认红在**哪一条断言**上
  ⇒ 才知道护网抓的是不是你想抓的那半。

💡 **本轮真正的救命动作**：第一次跑完没有只看脚本打的 `RED ✅`，
而是**往下读了断言消息原文**，发现它每次都指向
「下游总调用次数=41」这类**与本次变异无关**的数字 ⇒ 才察觉报告是空的。
⇒ **脚本的标签是二手证据，断言原文才是一手证据。**
（与 §3.3「绿测试可能是错误答案的守卫」同族：**自动判定也会给错答案**。）
