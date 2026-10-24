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
