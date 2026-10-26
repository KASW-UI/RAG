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

---

## PIT-USERS-SHARED-007 🔴 对拍脚本**不清状态** ⇒ 第二次跑出来的「漂移」是假的

**症状**：给 `user-rpc` 补 9 个 RPC 的双侧活体对拍（`tools/difftest/exp-difftest-grpc-user9.sh`）。
第一轮跑出 43 场景 / **6 处**差异，其中 2 处看起来是**货真价实的新产品漂移**：

| 场景 | Go | RAG | 看着像 |
|---|---|---|---|
| `Register` 空用户名 | `OK` | `ALREADY_EXISTS`「用户名已存在」 | RAG 误判重名 |
| `Register` 空邮箱 | `OK` | `INTERNAL`「DB更新失败」 | RAG 写库失败 |

⇒ 差点就按「RAG 错」去改产品代码了。

**真相：两侧状态不对称。**
- Go 侧表名 `users`、RAG 侧表名 `users_user`，两边都有 `UNIQUE(username)` / `UNIQUE(email)`；
- 本对拍里**「空用户名」「空邮箱」两个场景的输入不是按 run 唯一的**（每次都是 `''`）；
- ⇒ 上一轮跑完留下的 `username=''` / `email=''` 行，会让下一轮在**留着残留的那一侧**撞 UNIQUE，
  而**没有残留的那一侧照常成功** ⇒ 凭空多出一处「RAG 错」。

**决定性的一步**：**两侧同时清空后复跑** ⇒ 那 2 处变成 ✓，
43 场景 / 4 处差异（只剩 Go 侧乱码文案，见 `docs/DRIFT/users/user/rpc.md` §9.1.4）。

⚠️ **更一般的教训**：
1. **「两侧跑同一套输入」不蕴含「两侧初始状态相同」。** 状态是**第三个变量**。
   对拍脚本必须**显式**把两侧状态对齐（清库 / 同一份种子数据），
   否则你比较的是「初始状态 + 代码」的综合差异，而不是代码差异。
2. **假漂移比漏漂移更危险**：漏漂移只留下空白，假漂移会**留下一个看起来很扎实的结论**
   （「RAG 在空用户名上误判重名」，还带着完整的场景名和 hex）——
   照着它去改产品，就是**把正确的代码改错**。
3. **清状态的责任要放在「执行体自己」身上，不能只放在编排脚本里。**
   编排脚本的 `DROP DATABASE` 只保护「按脚本跑」这一条路径；
   而实际调试最常见的用法是「**复用已起好的栈，只重跑执行体**」——
   那条路径下状态一路累积 ⇒ 第二次跑出来的差异是假的。
   ⇒ 本轮把清理移进了 runner 自己的 `resetState()`（跑前清空两侧业务表）。

**判定口诀**：
> **看到「某一侧在某个边界输入上失败、另一侧成功」时，先问一句：两侧的库现在是不是同一个初始状态？**
> 尤其当那个边界输入**恰好是 UNIQUE 列的极值**（空串、空 map、0）时。

**处置**：
- `tools/difftest/grpc-user9-dual-port-runner.java` 增加 `resetState()`：
  跑前用 JDBC 清空**两侧自己的**业务表（`users` / `user_avatar_history` / `users_user` / `users_user_avatar_history`），
  并在输出里打印清掉多少行（**「清掉 0 行」本身就是一条信息**）；
- 编排脚本把库名 / 端口透传给 runner（`-Dgo.db.url` / `-Drag.db.url`），
  否则 runner 会去清**别的库**（或根本清不到）⇒ 又变成假漂移；
- 提供 `-Dcleanup=false` 逃生门，但**在输出里明确警告「会生假漂移」**。

---

## PIT-USERS-SHARED-008 🔴 bash + UTF-8 locale ⇒ **全角括号会被算进变量名**

**症状**：`exp-difftest-grpc-user9.sh` 跑到第 4 步直接挂：

```
==> [4/5] 起两套 user-rpc（Go :$GO_PORT / RAG :$RAG_PORT）
exp-difftest-grpc-user9.sh: line 127: RAG_PORT）: unbound variable
```

**根因**：在 `C.UTF-8` locale 下，bash 把**全角右括号 `）`（U+FF09）算作标识符字符**，
于是 `"$RAG_PORT）"` 里的变量名被解析成 **`RAG_PORT）`**（带全角括号）⇒ 找不到 ⇒ `unbound variable`。

**为什么危险**：这不是「脚本跑不起来」这种一眼可见的错。
本轮它第一次出现时，报错信息里的全角字符在终端里几乎看不出来（`RAG_PORT）: unbound variable`），
很容易被误读成「脚本里少定义了个变量」或「环境变量没传进去」⇒ **往错的方向查**。

**扫描办法**（本轮用的）：找出「`$变量` 后面紧跟非 ASCII 字节」的位置。
```bash
python3 - <<'PY'
import re
b = open("exp-difftest-grpc-user9.sh","rb").read().decode("utf-8")
for i, line in enumerate(b.split("\n"), 1):
    for m in re.finditer(r"\$[A-Za-z_][A-Za-z0-9_]*", line):
        j = m.end()
        if j < len(line) and ord(line[j]) > 127:
            print(i, m.group(0), repr(line[j]), line.strip()[:80])
PY
PY
```
⇒ 本轮在本脚本里查出 **3 处**（`$a（`、`$RAG_PORT）`、`$RC）`），全部改成 `${VAR}` 形式。

⚠️ **同款隐患在共享脚本 `tools/difftest/run.sh` 里也有 3 处**（L51 / L162 / L212）——
本轮**故意没改**：那个脚本**正被 admin 域会话使用中**，而 bash 是**增量读脚本**的，
改一个正在跑的脚本会把对方的执行流搞坏 ⇒ 留到合并时统一处理。

**处置**：
- **所有变量引用一律写 `${VAR}`**（哪怕后面跟的是 ASCII 标点）——
  这条规矩零成本，且彻底绕开「什么算标识符字符」的实现差异；
- 中文注释里**紧贴变量**是触发条件 ⇒ 若必须紧贴，把全角标点改成半角或加空格。

---

## PIT-USERS-SHARED-009 🔴 共享的 difftest 容器会被**别的会话回收** ⇒ 对拍脚本要自带容器

**症状**：`exp-difftest-grpc-user9.sh` 第一次跑成功，几分钟后重跑就挂：

```
Error response from daemon: No such container: difftest-postgres
```

查 `docker ps` 发现：`difftest-postgres` / `difftest-redis` **已经不在 `docker ps -a` 里**（不是停止，是**被删除**），
而本会话自建的 `uu_go_db` / `uu_rag_db` **两个库一起消失**（容器重建 ⇒ 卷没了），
**正在跑的两套栈也一起没了**（Go 进程被收走，RAG 进程因连接池失效而失联）。

**根因**：`difftest-postgres` / `difftest-redis` 是**共享资源**，由 `tools/difftest/run.sh` 管理，
本机同时有多个会话在跑（用户域 / admin 域 / message 域 / task 域……）。
在 Docker 内存只有 **7.65 GiB** 的机器上，**任何一个会话都可能为了腾资源把它们删掉**。

⚠️ **更要命的是「删容器」与「重启容器」的观感完全不同**：
- **重启**：容器还在，数据卷还在 ⇒ 库还在，但**进程全没了**（本轮实测：容器 `Up 14 seconds`，
  我的 Go 进程被收走、admin 会话的栈也被收走）；
- **删除 + 重建**：**库也没了**（本轮实测：`uu_go_db` / `uu_rag_db` 直接 `database does not exist`）。
⇒ **两种情况都会让「跑到一半的对拍」消失**，而且没有任何本地报错指向真正的原因。

**处置**：
- 对拍脚本**自带容器**（`uudifftest-postgres` :55434 / `uudifftest-redis` :56382），
  脚本自己 `ensure_container`（不存在就建、存在但停了就启）+ `wait_pg`（真正能连上再继续，
  **容器 healthy ≠ psql 可用**）；
- 端口**逐个避开**共享栈：`19005 / 59211 / 19195 / 55434 / 56382`。
  ⚠️ **DevServer 端口会撞**：本轮实测 `19095` 被共享栈里的 Go user-api 占了 ⇒ 本脚本改用 `19195`；
- **成本可忽略**：PG 约 40 MiB、Redis 约 9 MiB（`docker stats` 实测），
  对比「跑到一半环境被别人收走」的代价，**完全值得**；
- **仍然不碰共享的 `go_db` / `rag_db`** —— 那是别的会话正在用的库，
  **自己建库**比「清理共享库」安全得多。

---

## PIT-USERS-SHARED-010 🔴 「端口在监听」≠「这是我刚起的进程」⇒ `wait_port` 会**误判 OK**

**症状**：跑「两个 rpc 并发对同一个空库启动」的对拍脚本（`exp-difftest-concurrent-schema.sh`），
第 2 轮出现一个**自相矛盾**的结果：

```
  OK   go  user-rpc 已监听 :19005
  OK   go  admin-rpc 已监听 :19015
  OK   rag user-rpc 已监听 :59211
  OK   rag admin-rpc 已监听 :59311
  ── 两侧启动是否有致命错误 ──
    rag-admin-rpc.log: 致命错误 0 处
    rag-user-rpc.log: 致命错误 0 处
  Go  侧表数 = 4，RAG 侧表数 = 0        ← 🔴 进程都起来了，库里却一张表都没有
```

**根因（三层叠加）**：
1. 上一轮结束时 `cleanup` 只 `kill` 了进程，**没有等它真的退出**（JVM 要几百毫秒才退）；
2. 于是下一轮 `wait_port` 看到的是**上一轮的旧进程**还在监听 ⇒ **误判「已启动」**；
3. 本轮的新 JVM 绑同一个端口 ⇒ **绑端口失败** ⇒ **本轮没有人跑 schema init** ⇒ 空库一张表都没有。

⇒ 而「致命错误 0 处」也是假的：**旧进程一直活着，它当然没有致命错误**。

⚠️ **为什么它特别难查**：所有信号都指向「产品有问题」（进程起来了但表没建 ⇒ 像是 DDL 静默失效）。
真正的原因是**上一次的残留**。若照着它去查 `SchemaInitializer`，会查错方向。

⚠️ **同一类问题的第二个面**：`DROP DATABASE IF EXISTS db` 在**还有连接**时会报
`database is being accessed by other users`，而脚本里它常被 `|| true` 吞掉 ⇒
随后 `CREATE DATABASE` 才失败 ⇒ **报出来的错指向错误的行**（本轮实测第 3 轮整轮无输出就是这个）。

**判定口诀**：
> **`lsof` 看到端口在监听，只能证明「有个进程在听」，不能证明「是我刚起的那个」。**
> 凡是要「起进程 → 断言它干了某件事」的脚本，**必须先确认端口是空的**。

**处置（三处一起改，缺一个还会再踩）**：
1. `cleanup` 里 `kill` 之后**逐个 `wait`**，等进程真的退出；
2. 起之前先 `wait_port_free`（确认端口**没有**监听者），而不是直接 `wait_port`；
3. 重建库用 `DROP DATABASE IF EXISTS db **WITH (FORCE)**`（PG 13+）——
   直接踢掉现有连接，不受残留进程影响。

**复测**：修完连跑 3 轮，Go 侧 / RAG 侧**全部 4 张表、0 致命错误** ⇒ 异常消失。

💡 **本轮真正的救命动作**：不是去读 `SchemaInitializer` 的代码，而是**问「这轮和上一轮有什么不同」** ——
上一轮是**第一次**跑（没有残留），那 1 轮是好的 ⇒ 差异必然在「轮次之间」⇒ 而轮次之间只差「残留」。
⇒ **复现失败时，先做「第一次 vs 第 N 次」的对照**，比直接读实现快得多。

---

## PIT-USERS-SHARED-011 🔴 函数名里的「Must」**不构成证据** —— Go 的 struct tag 默认值会**静默改写**行为

**症状**：台账里有一条写了好几轮的结论：

> 「Go 侧 `servicecontext.go:29` 的 `redis.MustNewRedis(c.BizRedis)` 在 `MustNewServer` **之前**
> ⇒ Redis 不可用时**启动即失败**；RAG 是懒连接 ⇒ **启动成功** ⇒ **两侧不一致**（CASE-35 记 `NO`）。」

**这条结论是错的。** 2026-10-24 的活体实验（把两侧 Redis 都指到死端口再起进程）实测：
**Go 侧照样起来了**（日志原文 `Starting rpc server at 0.0.0.0:19005...`）。

**根因**：
```go
// go-zero v1.10.2 core/stores/redis/conf.go
RedisConf struct {
    Host     string
    Type     string `json:",default=node,options=node|cluster"`
    ...
    NonBlock bool   `json:",default=true"`      // ← ⚠️ 默认就是 true
}
// core/stores/redis/redis.go:140-147
rds := newRedis(conf.Host, opts...)
if !conf.NonBlock {                     // ← 默认 true ⇒ **整个连接检查被跳过**
    if err := rds.checkConnection(conf.PingTimeout); err != nil { return nil, ... }
}
```
⇒ **`MustNewRedis` 默认根本不去 ping Redis** ⇒ Redis 不可达**不会**让 Go 启动失败。

⚠️ **旧结论错在哪（两个具体的错）**：
1. **只读了调用点，没读被调方的默认行为** ——
   `servicecontext.go:29` 的**位置**（在 `MustNewServer` 之前）是对的，
   但「`MustNewRedis` 会失败」这件事**依赖 `RedisConf` 的默认值**，而那个默认值在
   **另一个包的 struct tag 里**（`core/stores/redis/conf.go`），不在真源仓库里；
2. **被函数名骗了** —— `MustNewRedis` 里的 "Must" 让人以为「必须连上才会返回」，
   而它只是「**出错就 `logx.Must` 退出**」；**出不出错取决于调用参数**，而参数带默认值。

⇒ 这正是 AGENTS.md §3.3.1 那条纪律的**反面**，值得单独强调：
> **「配置里没写」≠「没开」** —— 在 Go 侧，**默认值藏在 struct tag 里**，
> 而这里 `NonBlock` 的默认是 `true`（= **跳过**检查）。
> ⇒ 「Go 侧的行为」要读到**消费点 + 被消费对象的默认值**两处，才算读全。

⚠️ **更一般的形态**（本仓不止这一处）：
Go 的 `conf` 包会按 struct tag 施加默认值（`default=` / `options=`），
所以**「yaml 里没出现的字段」不等于「零值」**。
凡是要断言 Go 某个配置项的**生效值**，**必须去读那个 struct 的 tag**，
不能拿「yaml 里没写 ⇒ 取零值」当推理前提。

**处置**：
- 台账 CASE-35 由 `NO` 改为 `YES（一致）`；`UNRESOLVED-USERRPC-004` 关闭（§9.3）；
- 顺带**少了一条假漂移**（原先被记成 OBSERVABLE DIFFERENCE 的那条，**实际上不存在**）；
- 新增活体实验脚本 `tools/difftest/exp-difftest-redis-failfast.sh`
  （把两侧 Redis 都指到**死端口**再起进程 —— **不用停容器**，零副作用，不影响别的会话）。

💡 **本轮真正的救命动作**：不是去读 go-zero 的源码，而是**先把进程起起来看它到底起没起来** ——
静态读出来的「Must ⇒ 必失败」和活体结果冲突时，**先信活体**，再去查「我漏读了哪个默认值」。
⇒ **一条「看起来很确定」的结论，越顺手越要拿实验去撞它。**

---

## PIT-USERS-SHARED-012 🔴 故障注入的「**断连形态**」决定测不测得到目标分支 —— `container.stop()` 发 RST ⇒ **假守卫**

> 2026-10-24 第九轮（user-api 的 `DRIFT-USERAPI-014` 守门网）实测。

### 现象

要守的是一条**只在「断连」态才发生**的差异：
Lettuce 的 `DisconnectedBehavior` 决定「断连期间的命令」是**排队**还是**立即拒**。

第一版守门网用 `container.stop()` 注入故障 ⇒ **变异验证时测试照样绿**
（把 `REJECT_COMMANDS` 改回 `DEFAULT` 也测不出来）⇒ 守卫是**假的**。

### 根因（用时间曲线探针打到 Lettuce 层的异常原文）

| 配置 | Lettuce 层异常 |
|---|---|
| `REJECT_COMMANDS` | `RedisException: Currently not connected. Commands are rejected.` |
| `DEFAULT` | `RedisException: java.net.SocketException: Connection reset` |

⇒ `container.stop()` 让容器被**强杀** ⇒ 连接收到的是 **RST**
⇒ Lettuce 往 socket 写命令时**立刻失败** ⇒ **根本走不到** `disconnectedBehavior` 那个分支
⇒ 两种配置的耗时都是 **1~13ms** ⇒ **分不出**。

而线上真实场景是 `docker stop` ⇒ Redis **优雅退出** ⇒ 连接收到的是 **FIN**
⇒ Lettuce 进入 **DISCONNECTED（正在重连）** 状态 ⇒ 命令被**排队**
⇒ 这才是 `DEFAULT` 会挂满 `commandTimeout(3s)` 的那条路径。

### 处置

守门网改用**可控 TCP 透传代理**（`UserApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy`）：
故障时**先关监听口**（⇒ 重连被 connection refused 挡掉）
⇒ **再对已建立的连接半关闭 `shutdownOutput()`**（⇒ 客户端收到 FIN，而不是 RST）。
⇒ 变异后耗时实测 **3017ms**，与 live 实测的 3020ms 吻合 ⇒ 守卫成立。

⚠️ **顺序不能反**：先关监听口才能保证「断连期间重连一定失败」，
否则 Lettuce 会在 FIN 到达前就重连成功 ⇒ 又测不到排队分支。
⚠️ **只能用 `shutdownOutput()`，不能用 `close()`**：socket 上若还有未读数据，
`close()` 会发 **RST** ⇒ 又退回成「Connection reset」那条不经过该配置的路径。

### 教训

> **「把服务弄挂」不是故障注入的全部 —— 挂的方式决定客户端走哪条分支。**
> ⇒ 断言「客户端在 X 状态下的行为」时，**先确认故障注入真的把客户端推进了 X 状态**
> （办法：把**客户端库层的异常原文**打出来，别只看 HTTP 状态码）。

---

## PIT-USERS-SHARED-013 🔴 只测「有效 token」的 200 是**假绿** —— 必须有「**被拉黑 token**」对照组

> 2026-10-24 第九轮实测。

要证明的是「Redis 故障 ⇒ **fail-open 放行**」。
但只用**有效 token**探测时，「两侧都返回 200」有**两种**可能：

- (a) 真的 fail-open（要证明的）；
- (b) **两侧根本没在查黑名单**（查了但功能是坏的 / 没挂上）—— **假绿**。

⇒ 必须加对照组：**先 `logout` 把 token 拉黑**（两侧的 rpc logout 都写
`user:jwt_blacklist:<token>`），再拿**同一个 token** 探测：

| Redis 状态 | 期望 | 证明什么 |
|---|---|---|
| 正常 | **401** | 黑名单机制**真在跑**（对照组成立） |
| 断连 | **200** | 故障时被 **fail-open 放行**（不是 401 误拦） |

⚠️ 单看 200 **无意义**；**401/200 这一对组合**才是完整证据。

---

## PIT-USERS-SHARED-014 🔴 「暖机」探针不能用「**返回 200**」判 —— 断连秒拒也是 200

> 2026-10-24 第九轮实测。

Redis 停过之后要让两侧客户端**重新连上**，否则下一轮测的是「**两侧连接状态不同**」而不是「Redis 慢」。

但**用有效 token 当暖机探针是无效的**：
Lettuce 断连时会**秒拒**命令（fail-open ⇒ **200**），
与「连着且正常」在**状态码上完全一样** ⇒ **区分不了**。

⇒ **必须用「被拉黑 token」当暖机探针**：拿到 **401** 才说明
「客户端真的连上了 Redis **且查到了黑名单**」。

---

## PIT-USERS-SHARED-015 🔴 串行探测会把**故障窗口**吃光 ⇒ 第二侧测到的是「正常」

> 2026-10-24 第九轮实测（首轮与第二轮都踩到）。

`CLIENT PAUSE 4000` 的窗口是**全局**的。脚本里若两侧**串行**探测：

```
Go  探测：t=2 起 → 阻塞 2.19s → t=4.19 返回（PAUSE 窗口被它耗尽）
RAG 探测：t=4.19 起 → PAUSE 早已结束 → 0.042s 返回「正常」
```

⇒ 第二侧拿到的是**假数据**（看起来「完全不受故障影响」）。
⚠️ 迷惑性极强：RAG 侧日志里**没有任何** Redis 失败告警 —— 那正是「没踩到故障」的证据。

⇒ **两侧探测必须并行**（两个 curl 放后台同时发起，再 `wait` 各自的 PID），
且 PAUSE 窗口要足够长（两侧同时落在窗口里）。
⚠️ **只能 `wait "$具体PID"`**：裸 `wait` 会把 Go/RAG 服务进程也一起等 ⇒ 脚本永远卡死。

---

## PIT-USERS-SHARED-016 🔴 `surefire-reports/*.xml` 里有**陈旧报告** ⇒ 求和会**多算**测试数

> 2026-10-24 第九轮实测。

统计回归测试数时直接对 `target/surefire-reports/*.xml` 求和，得到 **102 / 129**，
而 mvn 自己的输出是 **101 / 125** ⇒ 差 1 和 4。

差异来源是**目录里的陈旧 XML**：
本轮删掉的临时探针（`TmpRedisTimingProbe.xml`，1 条）
与**上一轮遗留**的旧报告（`UserRpcJsonbAndAvatarProjectionTest.xml`，Oct 23，4 条）。

⇒ **以 mvn 输出的 `Tests run:` 为准**；
确需按 XML 统计时，先按**修改时间**过滤出本轮的那一批，别把历史残留算进来。

⚠️ 这条与 `PIT-USERS-SHARED-006` 同源：**自动化统计给出的数字，必须能对上一个独立来源**
（这里就是 mvn 的汇总行），否则「多算」会一路传到台账里变成假数据。

---

## PIT-USERS-SHARED-017 🔴 **代码注释**给了与实现矛盾的安全保证 ⇒ 审阅者据此漏掉真实缺陷

> 2026-10-24 用户提问（`DRIFT-USERAPI-013` 归属）触发的复核，实测。

### 现象

`service/common/src/main/java/com/rag/common/protection/GoStyleBreakerFilter.java:69-71` 的类注释：

> 「为什么 breaker 实例不做无界缓存：key 由 method+path 构成，基数等于路由数
> （本模块 13 条，**含未匹配路径不会建 key**），无界增长的现实风险为零。」

而实现（**同文件**）：
- `:86` `breakerFor()` → `breakers.computeIfAbsent(routeKey(request), GoStyleBreaker::new)`
- `:109` `doFilter` → 对**每一个到达 filter 的请求**都调 `breakerFor(...)`

⇒ Filter 跑在 `DispatcherServlet` **外面**，此时还不知道路由匹不匹配
⇒ **未匹配路径照样建 key** ⇒ 注释里那句「不会建 key」是**假的**。

### 为什么危险

注释不是「多写了一句废话」，而是**给出了一个错误的安全保证**：
⇒ 审阅者读到「无界增长的现实风险为零」会**直接跳过**这条检查
⇒ 而台账里（`docs/DRIFT/users/user/api.md` 第八轮 §7 ②）其实**已经**正确记录了
「未匹配路径也建 key」—— 但**代码注释**与台账**互相矛盾**
⇒ 谁先读到哪个，就会得出相反的结论。

### 教训

> **台账说「有问题」+ 代码注释说「没问题」⇒ 以代码为准，并立刻修注释。**
> ⚠️ 这与 `PIT-USERS-SHARED-011`（函数名里的「Must」不构成证据）同源：
> **「写了什么」不构成证据，「执行点实际怎么走」才构成证据。**
> ⇒ 审阅共享层时，凡遇到「**为什么不会有问题**」这类注释，一律**回到消费点验证**，
> 特别当注释在论证「**资源有界**」「**不会泄漏**」「**风险为零**」时 ——
> **这类断言最需要证据，也最容易是笔误。**

### 处置

- 本轮**只记录，未改代码**（`service/common` 属共享层，改动影响 10 个 api 服务 ⇒ 交由用户拍板）。
- 待办：① 修 `GoStyleBreakerFilter:69-71` 的错误注释；
  ② 给 `trackedRoutes()` 补一条**键基数断言**（目前该钩子在测试目录**零引用** ⇒ 键空间无界**无回归网**）。

---

## PIT-USERS-SHARED-018 🔴 「mock 没生效」在**有超时的链路**上是个不可靠的结论 —— 先看耗时与 Content-Type 两个指纹

> 2026-10-24 写 `UserApiProtectionMvcTest`（user-api 保护层守网）时实测踩到。

### 现象

新写的守网测试第一条就红，症状**看起来**直指 mock：

```text
UserApiProtectionMvcTest.normalRequestReachesBusinessLogic
  Status expected:<200> but was:<503>          （耗时 3.3s）
日志：IdentityResolver$ErrIdentityMismatchException: user RPC is nil: authenticated user RPC identity mismatch
```

⇒ 第一反应必然是「`@MockitoBean` 注入的 `UserRpcClient` 与服务里持有的不是同一个实例」
⇒ 于是去写诊断测试比对 `identityHashCode`（**结果三个全是同一个实例**，stub 也一致可见）
⇒ 于是又怀疑「mock 被 reset 了」⇒ 于是去查 `MockReset` 的语义（**也是对的**）。

⇒ **三次推理，三次都「有证据」，三次都不在真因上。**

### 真因

**测试类没有 Redis。** 鉴权链要查黑名单 ⇒ 首次连接失败被
`spring.data.redis.timeout: 3s` 卡住 ⇒ **3.31s** ⇒ 撞上 3s HTTP 预算
⇒ `HttpMiddleware` 的**超时分支**先返回 503 ⇒ 测试方法结束、mock 被 reset ⇒
**还在 worker 线程上跑的请求**继续往下走，此时 `userRpc()` 已是 null
⇒ 才出现那句「user RPC is nil」—— **它是第 1、2 步的次生症状，不是病因**。

### 三个可复用的指纹（下次先看这三个，再怀疑 mock）

| 指纹 | 读法 | 本次实测 |
|---|---|---|
| **请求耗时** | ≈3s ⇒ 撞了 HTTP 预算 ⇒ 与「mock / 装配」无关 | 3.3s |
| **响应 `Content-Type`** | `text/plain;charset=utf-8` ⇒ **超时分支**写的（裸 503 的 MaxConns 分支**不带** Content-Type）⇒ 503 的**来源分支**可判 | `text/plain;charset=utf-8` |
| **日志线程名** | `obs-http-worker` ⇒ 请求在 **worker 线程**上跑 ⇒ 可能**晚于**测试方法结束 ⇒ 才解释「stub 明明生效却拿到 null」 | `[obs-http-worker]` |

⇒ 第 2 条尤其通用：**同是 503，来源分支不同则形状不同**
（超时分支有 body 与 Content-Type，MaxConns 分支是裸 503 无 body）。
⇒ 以前一直靠「body 是不是 Request Timeout」猜，现在有确定的指纹了。

### 教训

> **「mock 没生效」是一个需要**排除**的假设，不是第一个假设。**
> ⇒ 判据：链路里**有没有超时预算**？
> - **没有**超时 ⇒ 「mock 没生效」很可能是真的；
> - **有**超时 ⇒ 先量耗时、看 Content-Type、确认线程名，**再**去怀疑 mock。
>
> ⚠️ 推论一：**测试必须自带它的真依赖**。
> 基础设施缺失（这里是没有 Redis）不会让测试「失败得更有信息量」，
> 而会把**被测变量整个换掉**——测的已经不是「保护层有没有生效」，而是「Redis 挂了会怎样」。
> ⇒ 判据：写之前先问「**这条链路依赖什么外部设施？它到位了吗？**」
> （本模块先例：`UserApiMvcPipelineTest` 用 Testcontainers 起真 Redis；本类现在也这么做。）
>
> ⚠️ 推论二：`@MockitoBean` 默认 `MockReset.AFTER` ⇒ **测试方法一结束，mock 就被 reset**
> ⇒ 任何**跨方法残留的在途请求**（后台线程、异步任务）都会在「mock 已 reset」的时点继续执行。
> ⇒ 判据：① stubbing 放 `@BeforeEach`（不放在方法体）；
> ② 用到后台线程的用例，**必须在 `finally` 里等它真正结束**（本类用 `Future.get` 等）。

### 处置

- `UserApiProtectionMvcTest`：① 接 Testcontainers 真 Redis；② stubbing 移到 `@BeforeEach`；
  ③ MaxConns 那条用 `Future.get` 等后台请求收尾。
  ⇒ 6/6 全绿 + 4 个变异全部按预期变红（见 `docs/DRIFT/users/user/api.md` §8 守门清单）。
- ⚠️ **这次误诊顺带撞出一条真发现**（经双侧活体实测后定型）：
  「Redis **从未连上**」不是一个形态，是**两个**，行为完全不同：
  - **端口拒连**（`127.0.0.1:<死端口>`）⇒ 两侧都 200 fail-open（Go 1.79s / RAG 0.57s）
    ⇒ **无漂移**（`DRIFT-USERAPI-014` 的修法已覆盖该形态）；
  - **主机名解析失败**（`redis` = **生产默认值** `${REDIS_ADDR:redis}`）⇒
    Go **恒 200**（1.81~1.92s）／RAG **200·503 摇摆**（2.93~3.03s，恰好卡在 3s 预算边缘）
    ⇒ **真漂移**（`DRIFT-USERAPI-015`）。

  ⇒ 已立 `DRIFT-USERAPI-015`；复现脚本 `tools/difftest/exp-difftest-redis-neverup.sh`
  （`REDIS_HOST=127.0.0.1` 与 `REDIS_HOST=redis` 各跑一轮）。
  ⇒ ⚠️ **教训再加一条**：「Redis 挂了」不是一个形态 ——
  **拒连 / 解析失败 / 很慢 / 连上后断连**是四个，各走不同的客户端状态机，
  ⇒ **「外部依赖故障」类对照，必须按「故障形态」逐个测，不能测一个就宣布对齐。**

---

## PIT-USERS-SHARED-019 🔴 机器复核工具的「路径存在性」层会**误判** ⇒ 先分类再动手，别为迎合工具改台账

> 2026-10-26 · 跨域修法（`global` 域销 V-007）后，用 `tools/check-ledger-selfcheck.py --domain users`
> 复核本域台账，报出 **60 处不一致** —— 实测 **59 处是误判**，只有 1 处是真问题。

### ⚠️ 我第一版的归因**错了**（`global` 域复核后更正，此处保留原文教训）

我第一版写的是：「**新版工具在换域时丢了这些去噪规则**」⇒ ❌ **不成立**。
`global` 域按「先复核前提再登记」复核后给出准确归因：

> 去噪**一直在跑**（第 2/4/5/6 层共 255+ 条）；
> 真正缺陷是**第 1 层（台账引用的仓库内路径是否存在）一条去噪都没有**。
> 旧版工具把「去噪」混在同一个检查里（旧版只有 4 类检查），
> 新版把层拆细后，**第 1 层没继承那些去噪** ⇒ 于是台账里大量用省略写法指代文件，
> 全被误判成「引用了不存在的文件」⇒ **信号被噪声淹没**。

⇒ **教训的教训**：**「工具 A 丢了规则」这种归因，不能靠「我记得旧版有这条规则」下** ——
必须**去核实新版那条路径上规则还在不在**。我这次是拿「旧版的行为」去推「新版丢了」，
**两边都没验证**。⇒ 这与本档 `PIT-USERS-SHARED-011`（函数名里的「Must」不构成证据）、
`017`（代码注释给了错误的安全保证）**同源**：**「我以为」不构成证据。**

### 四类误判（都不是台账事实错误）

| # | 形态 | 例子 | 为什么是误判 |
|---|---|---|---|
| ① | **路径省略写法**（含 `...`） | `service/common/.../spring/HttpMiddlewareFilter.java` | 本域台账的**惯用简写**（同段里还有完整路径做锚点）⇒ 工具要能还原 `...` |
| ② | **裸文件名**（无包路径） | `service/IdentityResolver.java` | `service/` 是省略的包前缀（`com.rag.users.user.api.service`）⇒ 需按「同名文件消歧」处理 |
| ③ | **「该文件不存在」的记述**被当成引用 | `tools/difftest/check-protection-layer.sh` | 那是**说明「脚本不存在」的历史记述**，不是引用（该脚本在**仓库全部历史里从未存在过**，`git log --all --diff-filter=A/D` 实测 0 命中）|
| ④ | **第三方库出处的路径少了一段** | `gorm@v1.31.1` 下的根 `migrator.go` | modcache 里存的是 `gorm.io/gorm@v1.31.1/**migrator/**migrator.go` ⇒ 工具按短路径拼不出真实文件 ⇒ 找不到<br>⚠️ **2026-10-26 订正**：本行初稿写的是「包前缀不匹配（`gorm.io/` 那段）」—— **不完整**：实际有**两处**缺（`gorm.io/` 模块前缀 **+** `migrator/` 子目录），且 **行号全对** ⇒ 详见下方「第 4 层那 3 条」|

### ✅ 工具侧已修（`global` 域收口，commit `bb680a3`）

第 1 层新增 **N8~N13 六条去噪**（N8 省略写法 · N9 裸文件名 · N10 短路径（靠 basename 相同 + 中段连续相邻**落到真实文件**判定，不靠形状猜）· N11 Go 侧路径 · N12 顶层目录不在本仓 · N13 台账对该文件的**状态叙述**（已删除 / 不存在 / 0 字节占位））。
⇒ 效果（**2026-10-26 复测**，工具 `bb680a3` + `a4f3029`）：users/user 域第 1 层 **52 引用 ⇒ 缺失 0**；users-admin **71 引用 ⇒ 缺失 0**；task **37 引用 ⇒ 缺失 0**（无回归）。
⚠️ **但只有第 1 层** —— 第 2/3/4 层**至今没做域适配**（见下）。
⚠️ **N13 的词表是「认词」的**：台账写「查不到 / 悬空 / 预期状态」这类**同义但不在词表里**的措辞 ⇒ 工具**认不出** ⇒ 仍报缺失
⇒ ⇒ **台账要写状态叙述时，措辞得落在词表内**（「不存在 / 已删除 / 0 字节 / 占位…」）⇒ 这条**约束已报 global 补词**。

### 我们域用新版工具的**剩余项**（2026-10-26 实测 ⇒ 逐层定如下）

> ⚠️ **本节初稿把第 3 层归因为「commit 清单判据是 task 域特化」—— 只对了一半。**
> 复核后补上**更决定性的第二重根因**，并把第 4 层从「工具缺口」改判为「**域内引用有歧义 ⇒ 本域已修**」。

| 层 | 条数 | **实测根因** | 处置 |
|---|---|---|---|
| 第 1 层 | **0** | ✅ **2026-10-26 归零**：原先那 1 条（`RpcInterceptorTimeoutDescriptionGateTest.java`，`global` 域守门类、**主干未合回**时路径悬空 —— 📌 **合回与否已拍板 2026-10-26：暂不合回 ⇒ 不是「忘了合」**）**被工具的 N13「状态叙述」正确去噪**。<br>⚠️ 但**不是一次就中的**：本条原先写作「主干上**查不到** ⇒ 路径**悬空**是**预期状态**」⇒ **这三个词都不在 N13 的叙述词表里** ⇒ 工具认不出 ⇒ 仍报缺失 ⇒ 改写成含「**不存在**」的措辞后才归零。⇒ **工具侧根因（词表缺词）已报 global** | ✅ 已归零（改写措辞）<br>⚠️ **改写不是「迎合工具」**：同一句话语义不变，只是把「查不到 / 悬空」换成工具词表里的「不存在」⇒ **读者可读性不变**，而机器复核**真的**少一条噪声<br>🔴 **2026-10-26 第三十九轮补记**：这条「缺失」报告同时也是**跨分支陷阱的产物**（合回前主仓代码仍旧串）⇒ **别把它读成「守门没写」** ⇒ 详见 **PIT-USERS-SHARED-023** |
| 第 3 层 | 45 | **双重硬编码**（详见下方「🔴 第 3 层的真实根因」）⇒ **该层在主分支上恒产出 45 条假阳性**，且那 45 条登记**属 task 域台账**、**不属我域** | 不改台账；已回信 global（附复现命令）<br>⇒ ~~**2026-10-26 仍未修**（global 后续提交 `a4f3029` / `d52031e` 修的是第 1 层与第 4 层）~~ ⇒ 🔴 **2026-10-26 已由 `global` 的 `35a7b8e` 修掉**（详见下方「🔴 2026-10-26 更新」）|
| 第 4 层 | **0** | ✅ **2026-10-26 归零**（`global` 的 **`d52031e`**「go.mod 权威化」）：第 4 层的第三方库清单改为**从真源 `go.mod` 动态解析**（含 `// indirect`）⇒ `gorm.io/gorm` 等不再被误判 | ✅ **两侧都成立**：工具侧已修（权威源从手写表换成真源 `go.mod`）<br>✅ 本域侧也已把 3 处引用补全为全路径（`gorm.io/gorm@v1.31.1/...`）—— 那是**消歧**（modcache 里 `migrator.go` ≥4 个同名）而非迎合工具；**行号全部核对无误**（`:124` = `HasTable` 判断、`:309` = `tx.Exec`、`:235-252` = AutoUpdateTime 那 18 行）|

### 🔴 第 3 层的真实根因：`--domain` **只作用于第 1 层**

**实测对照**（同一支工具、同一份主仓台账，只换 `--domain`）：

| `--domain` | 第 1 层 | 第 2 层 | 第 3 层 | 第 4 层 |
|---|---|---|---|---|
| `task` | 37 引用 ⇒ 缺失 0 | 10/10 OK（断言**全是 task 域**）| 实测 `task-rpc`/`task-api` · commit 清单 45 处缺陷 | — |
| `users` | **50** 引用 ⇒ 缺失 1 | **10/10 OK（断言还是 task 域）** | **实测还是 `task-rpc`/`task-api`** · **还是 45 处** | — |
| `users-admin` | **71** 引用 ⇒ 缺失 0 | **10/10 OK（断言还是 task 域）** | **实测还是 `task-rpc`/`task-api`** · **还是 45 处** | — |

⇒ **第 1 层随域变（37 / 50 / 71）；第 2/3 层三个域输出完全相同 ⇒ 域筛选没传到这两层。**

**根因（读工具源码定位到具体行）**：

1. **台账路径写死** —— `tools/check-ledger-selfcheck.py` 第 3 层里：
   `readme = read(os.path.join(root, 'docs/DRIFT/task/_README.md'))`
   ⇒ **无论 `--domain` 是什么，第 3 层都只读 task 域的 README**。
   ⇒ 顺带查明：那 45 条哈希**全部落在 `docs/DRIFT/task/rpc.md` 与 `docs/DRIFT/task/_README.md`** ⇒ **不是 users 域的登记**。
2. **窗口算法在主分支上恒空** ——
   `merge_base = git(root, 'merge-base', 'HEAD', 'do-my-own-gh')`
   `hashes = git(root, 'rev-list', ..., f'{merge_base}..HEAD')`
   ⇒ 在主分支上 `HEAD == do-my-own-gh` ⇒ `merge-base(HEAD, do-my-own-gh) == HEAD`
   ⇒ **`rev-list HEAD..HEAD` 恒为空 ⇒ `hashes = []` ⇒ 登记的 44 个哈希全部落进「不在本分支上」**（44 条）
   ＋ 行数不守恒 1 条 ⇒ **合计 45**。
   ⚠️ 实测反证：那 45 个哈希**确实在**本地 `do-my-own-gh` 上（`git merge-base --is-ancestor <sha> do-my-own-gh` 全部为真）
   ⇒ **工具的「陈旧登记」判定是错的**。
   ⇒ 为什么 `global` 那边跑出来是「0 → 0」？因为 `global` 的 cwd/root 在 `align/global` 工作树 ⇒ `HEAD != do-my-own-gh` ⇒ 窗口非空 ⇒ 恰好不撞这个坑。

⇒ **结论：这 45 条是工具缺陷，不是台账缺陷，且不属 users 域。**

### 🔴 2026-10-26 更新：**这个坑已被 `global` 填了**（`35a7b8e`）

`global` 域的 **`35a7b8e`「共享复核工具两缺陷修复：域适配 + commit 清单口径」**已修掉上面两条根因：

| 原根因 | 现在的行为 |
|---|---|
| 台账路径写死 `docs/DRIFT/task/_README.md` | 第 2/3 层**先看该域台账的格式**，再决定判据 ⇒ 对 `users` 域输出「**本层不适用**」而不是套用 task 域的判据 |
| 窗口 `merge-base(HEAD, do-my-own-gh)..HEAD` 在主分支上恒空 | 同上（commit 清单口径改为**按域取**，不再拿 `do-my-own-gh` 当公共祖先）|

⇒ 实测（用 global 工作树里的工具 + `--repo-root` 指回主仓）：
**层 0~4 全部通过**；第 1 层 54⇒0；第 4 层 288 条去重 ⇒ **235 条不悬空** + 53 条去噪。

⚠️ **两条纪律（这一节的真正收获）**：

1. **「本层不适用」≠「通过」。** 工具自己明写「⚠️ 不是『通过』，是『没查』」
   ⇒ 本域第 2/3 层的判据**至今仍缺**（本域台账的「commit 清单」根本不是 task 域那个格式）
   ⇒ **不能拿「工具全绿」当「本域已复核」**。
2. **本仓那份工具是旧版 ⇒ 别照着它的输出改台账。**
   直接跑 `tools/check-ledger-selfcheck.py`（`7a01b51`）会得到 **60 条「不一致」**，
   拆开是 **45（第 3 层）+ 12（第 1 层 N8/N9/N13 去噪前的）+ 3（第 4 层 go.mod 权威化前的）**
   ⇒ **全是旧版误判**，新版同口径**全绿**。
   ⇒ **复跑前先确认工具版本**：`git log --oneline -- tools/check-ledger-selfcheck.py`。
   ⇒ 正确姿势（不改本仓那份、避免与其他会话抢同一文件）：
   ```bash
   python3 "<global worktree>/tools/check-ledger-selfcheck.py" \
       --domain users --repo-root "/Users/pr/Documents/java&golang/RAG"
   ```

⇒ **逐层根因与复现命令 ⇒ 本节上文；本域台账侧的同步回写 ⇒ [`docs/DRIFT/users/_shared.md`](../../../DRIFT/users/_shared.md) 的「2026-10-26 复测」小节。**

> ⚠️ **这里还有一个更普适的坑**：`merge-base(HEAD, do-my-own-gh)..HEAD` 这个窗口算法，
> **只有在「HEAD 是一个从 do-my-own-gh 分出去的域工作树」时才有意义**；
> 一旦在主干上跑（或在已合回主干的分支上跑），窗口必然为空 ⇒ **该层必然全红**。
> ⇒ 工具 owner 若要修，窗口应改为「**台账里登记的最后一个哈希 → HEAD**」或「该域台账自述的分支」，
> 而不是拿 `do-my-own-gh` 当公共祖先。

### 第 4 层那 3 条：**行号全对，路径少了一段**（本域已修）

| 台账初稿（短路径） | 实际正确出处（已逐行核对内容） |
|---|---|
| `gorm@v1.31.1` 下的**根** `migrator.go`（引到 `tx.Exec` 那行） | `gorm.io/gorm@v1.31.1/**migrator/**migrator.go:309` = `err = tx.Exec(createTableSQL, values...).Error` ✔（`createTableSQL = "CREATE TABLE ? ("` 定义在同文件 `:225`）|
| **裸** `migrator.go`（引到 `HasTable` 先查那行） | `gorm.io/gorm@v1.31.1/**migrator/**migrator.go:124` = `if !queryTx.Migrator().HasTable(value) {` ✔（`HasTable` 本体在 `:333`）|
| `gorm@v1.31.1/callbacks/update.go:235-252`（缺 `gorm.io/` 模块前缀）→ `gorm.io/gorm@v1.31.1/callbacks/update.go:235-252` ✔（AutoUpdateTime 无条件进 SET 的那 18 行，内容逐行对上）|

⚠️ **为什么工具找不到（根因，比「路径少了一段」更具体）**：
`tools/check-ledger-selfcheck.py` 的第 4 层靠一张 **`THIRD_PARTY` 映射表**把台账里的 `模块@版本/路径` 还原成 modcache 路径，
而那张表**只有 6 项**（`goka` / `kafka-go` / `go-zero` / `go-queue` / `go-redis` / `sarama`）——
**`gorm` 不在里面**（尽管表头注释写着「版本取自**真源 go.mod**」，而真源 go.mod **第 28 行**明写 `gorm.io/gorm v1.31.1`），
且 `gorm.io` 是**带斜杠的模块路径**、不是 `go-redis` 那种单段名 ⇒ 即使补表也要单独处理。
⇒ **所以任何 gorm 出处都会被判「在真源与全部第三方库里都找不到」** —— 这是**工具侧缺口**，不是台账缺陷。
⇒ ✅ 本域 3 处已把引用写成全路径（消歧，**不是为工具变绿**）；工具侧补 `gorm` 映射后这 3 条才会消失。
⚠️ 顺带：该表还漏了真源 go.mod 里的 `gorm.io/driver/postgres` · `golang-jwt/jwt/v4` · `jackc/pgx/v5` ·
`prometheus/client_golang` · `go.opentelemetry.io/otel*` · `minio-go/v7` · `bluemonday` · `snowflake` · `ahocorasick` 等
⇒ **凡引用这些库的台账，同层都会假红。**

⚠️ **为什么工具找不到**：modcache 里 **`migrator.go`（根文件，111 行）与 `migrator/migrator.go`（1038 行）是两个不同文件**，
且 `gorm@v1.31.1` / `gorm@v1.31.2` **各一份** ⇒ 短路径**天然有歧义** ⇒ 工具按 basename 命中根文件后，行号也超范围（111 < 124 / 309）。
⇒ **修法不是「让工具去猜」，而是把引用写全**（`docs/DRIFT/users/user/rpc.md:1332` 本来就是这么写的 ⇒ 域内写法当时并不统一）。
⇒ ✅ 本域 3 处已补全（2026-10-26）。

### 唯一一处**真问题**（当时）

**销账记录引用了「在本条记录时主干上还不存在」的路径** ——
`global` 域的修法当时**仍在它的工作区（未提交、未合入主干）** ⇒ 我回写销账时引了它
⇒ 在主干上就是**悬空**。⇒ 处置：在销账记录里**显式标注**「合并前查不到 ⇒ 悬空是预期状态」。

🔴 **2026-10-26 第三十九轮续写（处置已升级）**：Jason 拍板 **`align/global` 与 `align/favorite` 暂不合回 `do-my-own-gh`**
⇒ ⇒ 「待合回」**不是**「忘了合」⇒ ⇒ 三域统一措辞：
> 「已提交并推双远端（`<commit>`），**待合回 `do-my-own-gh`**；📌 合回与否已拍板（2026-10-26）：暂不合回 ⇒ **维持「待合回」不是「忘了合」**」

⇒ **本域已把 V-007 及其 5 处指针（§4.4 · §4 矩阵 · §7 销账表 · §8.1 提交清单 · §8 CASE-39 · §11 挂起行）全部改成这一句**
⇒ **代码零改动**（V-007 的实现在 `global` 域 `service/common`，本域只有台账写权）。
⚠️ **但这条处置只解决了「措辞」** ⇒ 同一情形还有**第二个坑**（「主仓还是旧串 ≠ 修复没做对」）⇒ 见 **PIT-USERS-SHARED-023**。

### 纪律

> **机器复核的输出是「待分类的候选」，不是「待修的缺陷清单」。**
> ⇒ 动手改台账前，先把 ❌ 分成「**工具解析缺口**」与「**台账事实错误**」两类；
> ⇒ **绝不能为了让工具变绿而改台账**（那是把台账改成迎合工具 ⇒ 反而制造新的失真）。
> ⇒ 真缺陷的判据只有一条：**台账说的与仓里实际的不一致**。

---

## PIT-USERS-SHARED-020 🔴 收方给的「commit SHA 清单」要**逐个核实主题**，不能整包照抄

> 2026-10-26 · V-007 销账回写时，`global` 域回了一串 SHA，我直接照抄进台账的「提交（SHA）」行。
> 事后自查时逐个 `git show --stat` ⇒ **其中一个 SHA 的主题与台账写的完全不是一回事**。

### 症状

台账里写的是：

> 「**E30**：`49808ad`（实现）+ `22a9111`（**台账/守门/探针收口**）」

⇒ 读起来是「E30 有两个提交，一个实现、一个收口」。

### 实际

| SHA | `git show --stat` 的真实内容 | 与台账写的对得上吗 |
|---|---|---|
| `49808ad` | 「A 类 3 条落地 —— E30（RPC 超时文案）· T-14 · T-15③」，**20 文件 / +1200 行**，含 `RpcInterceptor.java`、守门类、`tools/semantic-probe/rpc-timeout/` | ✅ 对得上 |
| `22a9111` | 「docs(global): **订正 E28** —— favorite 的两斜杠 owner 已修」，**1 文件 / 1 行**（`docs/DRIFT/global/_README.md`）| ❌ **完全无关** |

再查「谁新增了守门类 / 探针」：

```bash
git log --oneline --all --diff-filter=A -- "*RpcInterceptorTimeoutDescriptionGateTest.java"
git log --oneline --all --diff-filter=A -- "tools/semantic-probe/rpc-timeout/*"
```

⇒ **两者都是 `49808ad`** ⇒ **E30 只有一个提交**。

### 为什么会错

我把对方消息里的 SHA **当成一个「成套交付清单」**来读 ⇒ 自动脑补出「实现一个 + 收口一个」的结构。
⇒ 实际上对方那条消息里**混着别的议题的 SHA**（`22a9111` 是 E28/favorite 的订正），
**它不属于 E30 这条销账**，却被我顺手编进了 E30 的行里。

### 纪律

> **SHA 是一条「可验证的事实」，不是一个「标签」。**
> ⇒ 凡是把 SHA 写进台账，**必须逐个 `git show --stat <sha>` 看过主题**，
> 确认它改的文件与该条销账**真的对得上**，再落笔。
> ⇒ 尤其当一个销账涉及**多个议题**（global 的单次提交常常打包多条）时：
> **别把整串 SHA 挂到同一条销账下** —— 要按 `commit --stat` 的**文件清单**反查归属。

### 与既有踩坑的关系

同源三条，都是「**拿一个未核实的二手陈述当证据**」：
- `PIT-USERS-SHARED-011` —— 函数名里的「Must」不构成证据
- `PIT-USERS-SHARED-017` —— 代码注释给了错误的安全保证
- `PIT-USERS-SHARED-019` —— 「新版工具丢了规则」是我的归因错误（靠「我记得旧版有」下结论）

⇒ 归拢成一句：**「对方说的」和「我以为的」都不是证据；只有「我打开看过的那一行」是。**

---

## PIT-USERS-SHARED-021 🔴 装配层**覆盖缺口**：「声明点写着」不等于「消费点真装了」—— 而且**同一个机制往往有两个独立的失效面**

> 2026-10-26 · `users/admin` 域回信指出本域 **B-1 的放行守卫只挡 SQL 路径**
> （用 `javap` 读字节码得出 `QueryTimeoutException` / `RedisConnectionFailureException` 的继承链
> **都不是** `io.grpc.StatusRuntimeException`）⇒ 本域自查发现**同源的另一处**。

### 症状

`UserRpcApplication.java:47-68` 上白纸黑字写着：

```java
@Import({
    ...
    DeadlineQueryTimeoutInjector.class,
    SqlTimeoutStatusTranslator.class,
})
```

⇒ 看上去「B-1 缓解已就位」。
**但全仓 `grep getInterceptors` 零命中** ⇒ **没有任何测试断言这两个 Bean 真进了 MyBatis 的插件链**。

### 实际

**这不是缺陷，是覆盖缺口** —— 端到端实测（2026-10-21 E-4）已证明链**事实上是通的**
（查询滞留 4303ms → **176ms**，与 Go 的 182ms 同量级）⇒ 拦截器**确实装上了**。

⚠️ **但那条端到端证据是一次性实测，不是常驻断言** ⇒ 谁在某个提交里摘掉 `@Import`，
**测试全绿、线上才炸**。链断掉的后果是 **wire 级漂移**：

```
缺翻译器 ⇒ PG 的 57014 不翻成 DEADLINE_EXCEEDED
         ⇒ 落进业务层 catch (Exception e)
         ⇒ 被改写成 INTERNAL「DB查询失败」
         ⇒ api 侧 mapRPCStatusToCode 渲染 5001，而 Go 是 1015（errmsg.go:6）
```

### 为什么会错

三层，每层都值得单独记住：

**① 「声明点」被当成了「消费点」的证据。**
`@Import` 只保证「Bean 存在」；「MyBatis-Plus 收没收到它」是**运行期**的事。
⇒ 正确姿势是读**消费点**：MP 3.5.17 的 `MybatisPlusAutoConfiguration` 构造器收
`ObjectProvider<Interceptor[]>` ⇒ 最终形态 = `sqlSessionFactory.getConfiguration().getInterceptors()`。
⚠️ **也不能 new 一个 `Configuration` 自己看** —— 那样验的是「注解写对了」，不是「本进程真装上了」。

**② 同一个机制有**两个独立的失效面**，而它们的表现完全一样。**
B-1 这条链的失效面：
- **面 A（本条）**：翻译器**不在链里** ⇒ 57014 不翻译；
- **面 B（`DRIFT-USERRPC-V-012`）**：异常**到了业务层但类型不对** ⇒ Redis 路径抛的
  `RedisConnectionFailureException` / `QueryTimeoutException` **压根不是** `StatusRuntimeException`
  ⇒ `catch (StatusRuntimeException e) { throw e; }` 这个守卫**永远不命中**。

⇒ 两者都表现为「超时被当成数据库故障、渲染 5001」，但**修法完全不同、守网也不能互相替代**：
面 A 的守网是「插件链里有没有它」，面 B 的守网是「异常到达时是不是那一类」。

**③ 断言本身可能恒绿。**
读**注解元数据**的断言（挂载点）如果判据写松 —— 比如只查「有没有 `@Intercepts`」而不查挂在哪个方法上 ——
它会**恒绿**，等于没有守门，而且**比没有测试更危险**（绿测试会让人以为守住了）。
⇒ 必须配**负控**：造一枚**形状错误**的输入（故意挂在 `StatementHandler.prepare` 上的变异体），
跑**同一条判据**，要求它**必须拒绝**。

### 纪律

1. **凡看到「X 已实施并端到端验证」，先把它隐含的前提挖出来。**
   本例的前提是「拦截器真在链里」—— 这个前提**从未被断言过**，只有一次性端到端证据兜着。
2. **装配层断言必须读消费点**：`sqlSessionFactory.getConfiguration().getInterceptors()`，
   **不能**读 `@Import` 那行，**不能** new 一个 `Configuration` 自己看。
3. **一个机制有几个独立的失效面，就要几条独立的守网**；
   补了一条**不代表**另一条也被守住了（面 A ≠ 面 B）。
4. **断言读元数据时必须配负控**，证明它有分辨力，而不是恰好为真。
5. **别照抄对方的「已验」**：`users/admin` 域做了同款 2 条 + 变异 M1；
   本域多做了 M2（另一个拦截器）与 M3（负控）⇒ 抄之前**自己跑一遍**。

### 与既有踩坑的关系

- `docs/Pitfalls/users/admin/rpc.md` 的 **P-4**（`@Import` 只让 Bean 存在）—— 本条是它
  **在 MyBatis 插件链这个新消费点上的跨域实例**（跨到 `users/admin` 域同一个 `service/common` 机制上）。
- `PIT-USERS-SHARED-017`（代码注释给了错误的安全保证）—— 本条同源：
  「`@Import` 里写了」这句**代码事实**被读成了「**机制已生效**」这个**运行期结论**。
- `PIT-USERS-SHARED-006`（变异验证脚本把「没跑」当成「红了」）—— 本条是它的正面版本：
  变异必须**能编译 + 测试变红**，且**只红目标那条**才算精确命中。
- `PIT-USERS-SHARED-019`（机器复核的误判）—— 同族的「证据分层」意识：
  **一次性实测 ≠ 常驻断言**。

⇒ 归拢成一句：**「声明点」是代码事实，「消费点」才是机制生效；两者之间隔着一个没人看过的接缝。**

---

## PIT-USERS-SHARED-022 🔴 台账里**引述一个「已知是错的路径」**时，工具对它有两种失效形态 —— 哪一种是**假阴性**

> 2026-10-26 · `global` 域第三十七轮（`45067ce`）把它作为**方向性提醒**给了我们域，
> 而**我们域正好命中**（自查出 4 处），当场改掉。

### 现象

台账要引述一个**明知是错的路径**（比如「这条销账引用了 `global` 域的守门类，但那个文件主干上还没有」）。
写法有两种，结果**都不对，但坏的程度差很多**：

| 写法 | 工具的表现 | 坏在哪 |
|---|---|---|
| ① 写成**活路径形态**（带 `service/` `tools/` `docs/` 前缀） | **把它报出来**（第 1 层 MISSING） | **噪声** —— 报的是「这个路径不存在」，可读、可忽略 |
| ② 写成**与叙述词同行**（同一行里写着「主干上查不到 / 悬空 / 是预期状态」…） | **把它吃掉**（N13 去噪） | 🔴 **假阴性** —— 一个**真缺陷**（路径写错）被当成「历史记述」静默放过 |

⇒ **② 危险得多**：① 只是吵，② 是**骗**。

### 实际

`global` 域这轮**自己踩到了第 ② 条**：写 E61 条目时引述了一条错路径，且与叙述词同行
⇒ **like 域的一条真缺陷（E43）被静默**（缺失数 1 → 0）
⇒ 靠**改动前后 diff 当场抓住**才改回。

⇒ **我们域的 4 处**（同型）：`RpcInterceptorTimeoutDescriptionGateTest.java`（global 域守门类，主干未合回）
· `tools/semantic-probe/rpc-timeout/*`（global 域探针，主干未合回）
· `check-protection-layer.sh`（全仓从未存在）—— 后者在 api.md + _shared.md 共 3 处。

### 为什么会错

工具的 N13 去噪靠**同行叙述词**判断「这个路径引用是历史记述、不是真引用」
⇒ 错路径若**恰好与叙述词同行**，就被误判成历史记述 ⇒ **静默消失**。
⇒ 也就是说：**去噪规则本身没有「这条路径到底存不存在」的二次确认** —— 它靠措辞猜。

### 纪律

> **引述一个「已知是错的路径」时，一律用「不带前缀的短形式」（只写文件名）。**

依据（我们实测过工具源码，不是推断）：
- `PATH_RE = (?:service|tools|docs)/[A-Za-z0-9_./-]+\.(?:java|md|py|sh|yml|MD|json)`
  ⇒ **裸文件名不被提取** ⇒ 既不会被当路径引用（零噪声），也不会被误当真缺陷；
- `CLASS_RE` **只用于层 5**（仓库里存在、但台账从未点名的测试类）
  ⇒ 裸类名**不产生 FAIL** ⇒ 也不会因「类在台账、文件不在」而被误报。

⚠️ **配套的一条（同一问题的另一面）**：**活路径**若用 `...` 省略中间目录（N8 写法），
工具**永久不可解析** ⇒ 路径写错了**也没有任何机制能发现**
🔴 **2026-10-26 晚订正**：这句**已被工具的新行为作废** —— global 的新版工具
**把简写正确识别为「简写」并放过** ⇒ 那批 MISSING 是**旧版工具的口径问题**、不是路径写错
⇒ ⇒ **展开 N8 的真正理由**改为：「**展开 = 从「不被检查」变成「被检查」**」
⇒ ⚠️ **教训**：写「永久/永远/绝不」这类**绝对措辞**前，先确认它不会被工具行为的变化推翻
⇒ ⇒ **活路径一律展开为完整路径**（先例：admin 域把 33 处 N8 全部展开）。

⇒ 两条合起来是一句：**「错的路径写短，对的路径写全」。**

### 与既有踩坑的关系

- `PIT-USERS-SHARED-019`（机器复核的四类误判）—— 本条是它的**第五类**，且是**最危险的一类**：
  前四类是「误报」（吵），本类是「漏报」（骗）。
- `PIT-USERS-SHARED-020`（对方给的 SHA 要逐个核实）—— 同族的「**别拿二手陈述当事实**」：
  那条讲「别照抄对方的清单」，本条讲「**引用错的东西时，先想清楚工具会怎么看你写它**」。
- `PIT-USERS-SHARED-017`（注释给了错误的安全保证）—— 本条是它在**机器复核侧的孪生**：
  「我这么写不会出事」是错的，**得先问工具会怎么解读我这么写**。

---

## PIT-USERS-SHARED-023 🔴 修法**在别的分支、尚未合回**时，主仓上的旧串是**分支差异**，不是「修复没做对」

> 2026-10-26 · `global` 域第三十七 / 三十九轮（`45067ce` / `85c4588`）转达的**跨域陷阱**，
> 而**我们域是它的第一个受害者**（第 37 轮机器复核实测）。

### 现象

修法落在**别的分支**（`align/global`）上，**尚未合回 `do-my-own-gh`** ⇒ ⇒
在**主仓**上跑任何验证，看到的仍是**修前的旧串**。

本域的两个实例（**两个都不是缺陷**）：

| 现象 | 真因 |
|---|---|
| 第 1 层机器复核报「`RpcInterceptorTimeoutDescriptionGateTest.java` 缺失」 | 守门类由 `49808ad` 新增、在 `align/global` 上，**主干上没有** ⇒ **分支差异**，不是「守门没写」 |
| 在主仓跑 CASE-39 / §11「挂起」形态，RAG 侧仍是 `rpc timeout after 2000ms` | E30 修复（`49808ad`）**未合回** ⇒ **合回前必然如此** |

### 为什么危险

这两条都**长得像缺陷**，而**修法本身在另一分支上、已经是对的** ⇒ ⇒ 人会自然地：
① 认为「上一轮说修好了，结果没修对」⇒ **去把已经修对的代码改回去**；
② 认为「守门没写」⇒ **去重写一遍守门**（⇒ 落进 `service/common`，而那是 `global` 域的**单写权** ⇒ 写权冲突）。

⇒ 代价是**回退一个已完成的修复**，或**制造跨域写权冲突**。

### 纪律

> **凡「修复在别的分支、尚未合回」，在主仓上看到的旧串一律按「分支差异」读，不按「回归 / 没修对」读。**

判据（三条**都要**满足，才算「分支差异」）：
1. 该修复的**提交 SHA 存在**，且 `git branch -r --contains <sha>` 显示**两个远端都含**它；
2. 该 SHA **不在** `do-my-own-gh` 的祖先链上（`git merge-base --is-ancestor <sha> do-my-own-gh` 为否）；
3. 主仓上的现象与**修前**行为**逐字一致**（不是「另一种坏法」）。

⚠️ **第 3 条最容易被跳过**：若主仓上的现象**与修前不一致**，那就不是分支差异，是**真回归** ⇒ 要另开条目，不能用本条兜底。

### 与既有踩坑的关系

- `PIT-USERS-SHARED-019`（机器复核的四类误判）—— 本条是它的**第六类**，且落在**读法层**：
  前几类是「工具误报 ⇒ 台账要跟着改」，本类是「**工具报的是真事实，但归因错了**」⇒ **台账没错，读法错**。
- `PIT-USERS-SHARED-020`（收方给的 SHA 要逐个核实）—— 本条是它的**下游**：那条教「别照抄对方的清单」，
  本条教「**照抄（核实）之后，主仓上的旧串不构成打脸**」。
- `PIT-USERS-SHARED-022`（引述错路径的两种失效形态）—— 本条解释了**为什么那条「缺失」报告之前会被当成噪声吃掉**：
  它与叙述词同行 ⇒ 被 N13 去噪 ⇒ 差点连「它其实是个真报告」也一起丢掉。

---

## PIT-USERS-SHARED-024 🔴 「先验一个候选修法」时，判据是**两侧是否仍同形**，不是「RAG 侧有没有变好」

> 2026-10-26 · 为 `DRIFT-USERAPI-015` 做**候选②（`spring.data.redis.connect-timeout=1s`）先验**时踩到。
> ⇒ 同一条里还带出两个更小的坑：**负缓存污染先验** · **「不可复现」≠「不存在」**。

### 症状

台账上记着一条「RAG 200/503 摇摆 vs Go 恒 200」的漂移，候选修法写的是「压连接超时 ⇒ RAG 稳定 fail-open 200 ⇒ 与 Go 同形」。
**照着「与 Go 同形」这句话去改，改完一测**：RAG 确实变 200 了 ✅，Go 呢？**Go 还是 503**。
⇒ ⇒ **候选修法把一条差异换成了方向相反的另一条** —— 它**变好了看**，但**离 Go 更远**。

实测三变体（`FORM=blackhole`，IP 直指 SYN 被丢 ⇒ 连接建立确定性卡住）：

| 变体 | code | 耗时 |
|---|---|---|
| Go api | **503 ×4** | 3.002 / 3.002 / 3.001 / 3.002 s |
| RAG api（现配置） | **503 ×4** | 3.010 / 3.003 / 3.002 / 3.002 s |
| RAG api +connect-timeout=1s | **200 ×4** | 1.711 / 1.012 / 1.011 / 1.012 s |

⇒ **现配置下两侧本来就同形（都 503）** ⇒ 候选②**凭空造出**一条反向差异。

### 为什么会错

「与 Go 同形」这句话是在**另一个形态**下写的（那次 Go 是 200），换到「卡住」形态后 **Go 自己变成了 503** ⇒
⇒ **基线形态一变，同形判据就变** ⇒ 候选修法的「收益」是**形态相关**的。
⇒ 而台账里那句「同形」是**从一次观测抄下来的结论**，不是**在每个形态上都验过的性质**。

### 纪律

> **先验一个候选修法时，判据只能是「改完之后**两侧**是否仍然同形」，而不是「RAG 侧是否变好/变稳」。**
> ⇒ 且**必须覆盖至少两个形态**：候选修法常常在 A 形态同形、在 B 形态反向。

⇒ 附带的第二个纪律：**判「同形」时，先分别记下两侧在**该形态**下的基线**（Go 是什么、RAG 是什么），
再判断改完之后两侧还同不同形 ⇒ **不要拿别的形态的基线来判**。

### 附带坑一：**先验的三个变体不要共用同一个故障目标名**（负缓存污染 ⇒ 无区分度）

第一轮先验时，三个变体**共用 `redis` 这一个主机名** ⇒ 结果**三个变体全 200** ⇒ **零区分度**，差点被读成「候选②无效」。
事后查明：本机此前已反复解析 `redis` 失败 ⇒ **DNS 负缓存命中** ⇒ 解析**立即**失败 ⇒ 根本没走到「命令排队等 commandTimeout」那段。
⇒ **修正**：**每个变体各用全新随机主机名**（保证都是「首次解析、无负缓存」）⇒ 复跑才有意义。
⇒ ⚠️ 同源提醒：任何**依赖 DNS 负缓存状态**的故障注入，先验/复现**每次都要换名字**。

### 附带坑二：**「复现不出来」≠「不存在」** ⇒ 只能降级定性，不能销账

原始观测（RAG 3.03 / 2.93 / 3.01s）要求一个**未写进台账的环境条件**：
**Java 侧的连接建立/DNS 解析卡住 ≥3s，而 Go 侧在 3s 内拿到确定失败**。
⇒ 今天的实测里两侧都快（~0.1~0.36s）⇒ 条件不成立 ⇒ 复现不出来。
⇒ ⚠️ **正确处置是「降级为『条件性成立 · 证据不足』」**（并写清成立条件缺哪一维），
**不是**「已证伪 ⇒ 销账」—— 那会把一个**可能真实存在**的窄窗口直接抹掉。
⇒ 同条还牵出一个**必须订正的旧断言**：Go 侧的失败耗时**不是「稳定 1.8s」**，而是**两段式**
（前 3 轮 ~1.75~1.80s ⇒ 连接池记住失败后 **0.10 / 0.07 / 0.002s**）⇒
当年「三轮 1.81/1.90/1.92s **稳定**」里的「稳定」是**误读**（只测了 3 轮、没测到稳态）
⇒ 同仓脚本头早就写了「⚠️ 必须分次探测：首个请求 ≠ 稳态」，**那条纪律当年没被套到 Go 侧的结论上**。

### 与既有踩坑的关系

- `PIT-USERS-SHARED-019`（机器复核的四类误判）—— 本条是它在**修法侧**的孪生：那四类是「工具误报 ⇒ 台账被改坏」，
  本条是「**台账里的『同形』是一次观测** ⇒ 照它改会改坏」。
- `PIT-USERS-SHARED-011`（「Must」不构成证据）—— 同族：**写法上的断言（"与 Go 同形"）不构成证据**，
  必须落到**具体形态 + 具体两侧数值**上。
- `PIT-USERS-SHARED-013`（只测「有效 token」的 200 是假绿）—— 同源的对照组思维：
  **每个形态都要有 Go 侧的基线**，否则无从判断同形。
