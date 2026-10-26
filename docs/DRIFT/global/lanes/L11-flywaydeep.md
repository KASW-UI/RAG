# L11 · `align/global-flywaydeep` —— Flyway 并发验证的**三条边界**（真 rpc 形态 / 迁移失败与回滚 / 压力与随机延迟）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `tools/difftest/check-flyway-concurrent.sh` |
| **可写** | `tools/difftest/flyway*`（新建脚本一律 `flyway` 前缀） |
| **可写** | `tools/difftest/check-flyway-ledger.py` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L11-flywaydeep.md` |
| **只读** | `service/**`（本 lane **不碰任何生产代码与测试**）· `service/favorite/**`（⛔ 零写权）· `tools/difftest/README.md`（共享说明文件 ⇒ 整合者统一更新） |

**分支**：`align/global-flywaydeep` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-flywaydeep` · **基线**：`align/global` HEAD

---

## 一、目标

L4 留下的三条「未覆盖」，全部在**并发验证**这条线上：

| # | 未覆盖 | 本轮要做什么 |
|---|---|---|
| ① | 🔴 起的是**最小 Spring 上下文**（只 import DataSource + Flyway）⇒ **不是「三个真 rpc 服务同时起」那个形态** | 把**真实 rpc 服务**拉起来并发跑（同库）⇒ 至少覆盖 3 个域 |
| ② | 🔴 没测「**迁移中途失败 / 回滚**」也没测「**同一模块起两个副本**」（同账本同表） | ① 人为造一个 V_N 失败 ⇒ 看另一个进程与库的状态；② 同一模块两个副本同账本 |
| ③ | 🔴 并发强度有限：每场景各跑 1 次 ⇒ **没做**多轮压力 / 随机延迟注入 / 慢机器模拟 | 跑 **N 轮** + 注入**随机启动延迟** ⇒ 断言「极端时序下也不会互相踩」 |

⚠️ **①「真 rpc 服务」的边界要说准**：不要求把 Kafka / Nacos / Redis / web 全拉起来（那是另一条线），
但**必须**是**真实的 rpc 进程**（真的起 grpc server + 真的跑 Flyway + 真的连同一个库）
⇒ 而不是「最小 Spring 上下文里手动 new 一个 Flyway」。
⇒ **如果你判断「真 rpc 形态」在本仓无法低成本达成**（例如必须拉 Kafka/Nacos/Redis）⇒ **不要硬凑**
⇒ 写清「**做到哪一步、卡在哪、代价多大**」交整合者。

---

## 三、🔴 样板（**照抄，不要重新发明**）

> ⚠️ **本 lane 不是 rpc 守门 lane** ⇒ 下面 §3.1~§3.3 的三个 rpc 样板（X-5 轴 / slow 轴 / latch 加固）与六条硬性要求
> **对本 lane 不适用**（那是 L6~L9 的）⇒ **不要照抄任何 rpc 测试**。
> ⇒ 本 lane 的真正样板是 **L4 留下的 `tools/difftest/check-flyway-concurrent.sh` + `flyway-concurrent-boot.java` +
> `flyway-concurrent-verify.py`**（第二十四轮，真 PG 三进程并发同库）⇒ 做法见 §3.4 与
> [`L4-flyway.md`](L4-flyway.md)（同目录，L4 已合回 `align/global`）。

> 三个样板是 L2（第二十四轮）建的，**已通过变异验证** ⇒ 直接照抄结构，只换模块相关的名字。

| 样板 | 路径 | 它示范什么 |
|---|---|---|
| **X-5 轴** | `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcGrpcInterceptorWiringTest.java` | **真 server + 真链 + 判别式断言**（服务端超时真的打断了在飞处理） |
| **slow 轴** | `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcSlowThresholdGateTest.java` | **真 OTel SDK + 收集型 exporter** ⇒ 断言导出的 span 上真的有 `sea.slow` |
| **latch 加固版** | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | `sleep` ⇒ `latch` 的改造形态 |

### 3.1 六条硬性要求（**每一条都有血泪，违反就等于守门空转**）

| # | 要求 | 踩过的坑 |
|---|---|---|
| ① | 🔴 **必须用 `ServerCalls.asyncUnaryCall(...)` 注册慢服务**，**不能**用裸 `ServerCallHandler` | grpc-java **不会**向裸 handler 派发 `onHalfClose` ⇒ 「处理函数一直不返回」**从未发生** ⇒ 客户端照样拿到自己的 `DEADLINE_EXCEEDED` ⇒ 只断言 status + 耗时的用例**照样是绿的** ⇒ 那是「服务端能取消一个**空调用**」，不是「服务端超时打断一个**正在执行**的处理」 |
| ② | 🔴 **必须断言「handler 真的被调用过」**（`probe.entered.await(...)`） | 同 ① ⇒ 少了这条断言，① 的问题测不出来 |
| ③ | 🔴 **必须断言「取消发生时它仍在跑」**（`assertFalse(probe.completed.get())`） | 只看「客户端拿到什么 status、耗时多久」在「handler 压根没执行」时**同样成立** |
| ④ | 🔴 **必须用 latch，不能用 `Thread.sleep(10_000)`** | sleep 方案下「取消发生时它还在跑」这条断言**根本写不出来**（等到断言时它早跑完了）⇒ 必须 latch ⇒ `finally` 里**立刻**放行 |
| ⑤ | 🔴 **`GrpcServerConfigurer` 必须按 Bean 名取**：`ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class)` | devh starter 自己还注册了 `compressionServerConfigurer` / `decompressionServerConfigurer` 两个同类型 Bean ⇒ `ctx.getBean(GrpcServerConfigurer.class)` 会抛 `NoUniqueBeanDefinitionException` |
| ⑥ | 🔴 **channel target 必须显式写 `dns:///` scheme** | Nacos discovery 在全局 `NameResolverRegistry` 里注册了高优先级 provider ⇒ 会把**任何无 scheme 的 target** 当作 serviceId 去 Nacos 拉实例 |

### 3.2 `@SpringBootTest` 的必备 properties（照抄样板）

```
"grpc.server.port=0",                              // ⚠️ GrpcServerLifecycle 没有 @ConditionalOnProperty ⇒ 实测写 false 仍会起 server
"spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",  // NacosRegistration 捕获异常只记日志不中断启动
"management.otlp.tracing.export.enabled=false",    // OTLP 指向不可达端点 ⇒ 只在后台重试刷日志
"logging.level.com.alibaba.nacos=WARN",
"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",   // 绕过 Redis：Lettuce 连接懒建
"rag.protection.cpu-shedding.enabled=false"        // ⚠️ 关掉 CPU 自适应降载，否则链上的 shedding 层会先返回 RESOURCE_EXHAUSTED，把判据盖掉 ⇒ 变成 flaky
```

### 3.3 slow 轴的额外要求

- **必须装真 OTel SDK**：`GlobalOpenTelemetry.resetForTest()` + `GlobalOpenTelemetry.set(OpenTelemetrySdk...SimpleSpanProcessor.create(EXPORTER))`
  ⇒ ⚠️ **必须在 Spring 上下文创建之前装**（`@BeforeAll`）—— `RpcInterceptor` 在 **Bean 创建期**就取了 Tracer（`RpcInterceptor.java:154`）
- **两条断言缺一不可**：慢的那条（耗时 > 阈值 ⇒ `sea.slow` == true）· 快的那条（耗时 ≪ 阈值 ⇒ **`sea.slow` 不存在**）⇒ 证明这条路径是**真比较**出来的，不是恒为 true
- span 属性键：`com.rag.common.observability.ObservabilityConstants.ATTR_SLOW` / `ATTR_DURATION`

## 四、🔴 本轮纪律（**违反即作废**）

| # | 纪律 | 说明 |
|---|---|---|
| 1 | **生产代码零改动** | 本轮是**守门修复**，不是行为修复 ⇒ `service/*/src/main/**` **一行都不许改** |
| 2 | **不改 `service/common`** | 共享层只有 `align/global` 能改 ⇒ 若发现必须动它 ⇒ **停下来**写进台账，交整合者 |
| 3 | **不碰 favorite** | `service/favorite/**` 与 `docs/*/favorite/**` **零改动**（本轮各 lane 都不涉及） |
| 4 | **每条守门必须做变异** | 变异必须**转红在目标断言上** ⇒ 证明守门非空转 |
| 5 | **变异只在隔离工作树跑** | 不在 lane 自己的工作树里跑（PIT-GLOBAL-020）⇒ 提交前 `git diff` 逐个确认变异已还原 |
| 6 | **变异判定只认 surefire XML** | stdout 的 `-- in` 行在类有 `@DisplayName` 时是**显示名**不是 FQN ⇒ 判定**逐条 `testcase`**（PIT-GLOBAL-021） |
| 7 | **三道闸** | 跑前删旧 `TEST-*.xml` + 看 maven 退出码 + grep `COMPILATION ERROR`（PIT-GLOBAL-018） |
| 8 | **改了上游模块就带 `-am`** | 否则测的是 `~/.m2` 里的旧 jar（PIT-GLOBAL-019） |
| 9 | **不 `git add -A`** | 提交前 `git diff` 逐个确认，只 `git add` 自己改过的文件 |
| 10 | **容器名/库名按域区分** | 本仓有**并行跑 Testcontainers 容器起不来**的先例（hot 域 Pitfall）⇒ 若容器起不来 ⇒ **先判定为环境问题并重试一次**；仍失败 ⇒ **停下来报告**，**不要改代码迁就** |

## 五、验收标准

- [ ] 每条守门**真跑过**（`mvn -o test -pl <模块> -am -Dtest='<类>'`），且 **skipped = 0**
- [ ] 每条守门**都做过变异**且**红在目标断言上**（贴出变异前后的 surefire XML 对比）
- [ ] 生产代码 **零改动**（`git diff --stat` 可验）
- [ ] `service/common` **零改动**
- [ ] favorite **零改动**
- [ ] 台账写进**本 lane 自己的文件**（`docs/DRIFT/global/lanes/<本文件>`），含：逐条判定表 + 变异结果 + **未覆盖的部分**
- [ ] **未覆盖 / 未穷尽**的部分要**显式列出**（不许写「已全部对齐」）
- [ ] 提交前 `git diff` 确认变异已还原；工作树干净

## 六、交付物

1. 新建/修改的**测试文件**（照 §三 的样板）
2. **变异脚本**（放 `/tmp`，不进仓）+ 变异前后的 surefire XML 对比摘录
3. **本 lane 台账**：`docs/DRIFT/global/lanes/<本文件>` 末尾追加「逐条判定表 + 变异结果 + 缺口清单 + 未覆盖部分」
4. **提交**（中文、结构化多段提交信息）—— **不 push、不 merge**，交整合者

### 3.4 ⚠️ 四个已知事实（自己再核一遍）

- 7 个 rpc 模块的 DSN 默认值**全部是同一个 `first_db`** ⇒ 同库共存是**常态**不是边缘场景
- `article-rpc` 用**默认账本表名** `flyway_schema_history` ⇒ 5 个场景里它是唯一使用者（没撞）⇒ 但「**将来新增模块忘配 `table` 就直接撞上它**」这条残留风险**仍在**（L4-02）
- Flyway 的 **advisory lock 按「账本表名」算 key** ⇒ 独立账本表名**顺带把迁移锁也隔离了**（L4-03）
- ⛔ **`favorite-rpc` 不在其中**：缺 `spring.flyway.baseline-version: 0` ⇒ 故障形态是「**起得来、首次查库才炸**」⇒ **只记账不修**（favorite 零写权）

---

## 七、✅ 本 lane 实跑结论（2026-10-26 · 第二十七轮 L11）

> 本节是**台账**（§一~§六 是任务书）。全部结论都来自**真跑**（真 PG 16 容器 + 真 rpc 进程），
> 日志留在 `/tmp/l11-run-*.log`（不入仓），脚本入库可复现。

### 7.0 交付物

| 文件 | 作用 |
|---|---|
| `tools/difftest/flyway-rpc-boot.java`（**新**） | **真 rpc 进程驱动**：`--app=<该模块自己的 *RpcApplication>` ⇒ 起**真 Spring Boot 上下文**（真 gRPC server + 真 Kafka + 真 Redis + 真 Flyway + 真连库），**不是** L4 那个「最小上下文」。另有 `--start-at`（墙钟栅栏）· `--extra-locations`（注入失败迁移）· `--grpc-port`（启动后 **TCP 连通性探测**）· 其余 `--k=v` 一律落 **system property**（优先级高于 application.yml ⇒ 不改任何生产 yaml） |
| `tools/difftest/flyway-rpc-concurrent.sh`（**新**） | 编排 + **命名断言判定器**：S1~S4 四场景 + `--rounds=N`（边界 ③）+ `--mutate-shared-ledger`（**反向注入**）+ `--keep`。退出码 `0`=全绿 / `1`=有红断言 / `2`=环境不满足 |

**复现**：

```bash
bash tools/difftest/flyway-rpc-concurrent.sh --rounds=2                    # 正常跑（应 EXIT=0）
bash tools/difftest/flyway-rpc-concurrent.sh --mutate-shared-ledger --rounds=1   # 反向注入（应 EXIT=1）
```

### 7.1 三条边界逐条判定

| 边界 | 判定 | 实测证据 |
|---|---|---|
| ① **真 rpc 进程**并发同库（≥3 域） | ✅ **做到**（但 `message` 需绕开一处已登记缺陷） | `follow ‖ task ‖ message` 三个**真 rpc 进程**（各自的 `*RpcApplication` + 真 gRPC server + 真 Flyway + 真连**同一个** PG 16 库）。**并发是真的**：脚本用 **advisory-lock 屏障**证明三者**同时**卡在 `pg_try_advisory_xact_lock(<key>)` 上（`pg_stat_activity` 实测可见），放锁后才同时进入迁移。三个进程 `ok=true`、`gRPC监听=true`（TCP 探测 + 日志 `gRPC Server started` 双证据）。⚠️ `message` 需用**启动参数**绕开两处 `discovery://` 两斜杠（§7.5 新发现 1）⇒ **那处缺陷仍在**；`follow` / `task` **零绕开** |
| ② **迁移中途失败 / 回滚** | ✅ **做到** | 用 `filesystem:` location 注入必然失败的 `V900__l11_injected_failure.sql`（**仓库里 0 个 .sql 被改**）⇒ follow 的真 rpc 进程迁移失败（`FlywayMigrateException` / SQLSTATE `42883` / `function l11_this_function_does_not_exist() does not exist`）⇒ **进程起不来**（`flywayInitializer` → `sqlSessionTemplate` → `followBlockTransactionService` 依赖链断）。**同库的另一个进程（task）完全不受影响**：`ok=true`、2 条 SQL 全 SUCCESS、gRPC 在监听。失败迁移建的 `l11_injected_marker` 表**零残留**（DDL 迁移在事务里 ⇒ 回滚干净） |
| ② **同一模块两个副本**（同账本同表） | ✅ **做到** | follow × 2 并发同库 ⇒ 两个副本都 `ok=true`，账本**行数 = 2（不是 4）** ⇒ 没有重复应用；`follow_relation` 表数 = 1（不是 2） |
| ③ **N 轮压力 + 随机启动延迟** | ✅ **做到** | `--rounds=N`：每轮一个**新库** + 每域一个 **0~4000ms 随机启动偏移**（墙钟栅栏不同 ⇒ 极端时序）⇒ 每轮三域全部 `ok`，账本独立 / 表建出 / 账本行数 三条断言全绿 |

### 7.2 「真 rpc 进程」不是靠嘴说的 —— 证据链

| 证据 | 怎么取到的 |
|---|---|
| 起的是**模块自己的 `*RpcApplication`**（不是最小上下文） | 驱动的 `--app=` 参数 ⇒ `SpringApplicationBuilder(appClass)` ⇒ 扫本模块全部 Bean |
| **真 gRPC server 起来了** | ① 启动后 **TCP 连通性探测** `127.0.0.1:<port>` ⇒ `grpcServerListening=true`；② 进程日志里有 `n.d.b.g.s.s.GrpcServerLifecycle : gRPC Server started, listening on address: 0.0.0.0, port: <port>` |
| **真 Flyway 跑了** | 进程日志里的 Flyway INFO（`Migrating schema` / `Successfully applied N migrations`）+ 驱动回读的**生效配置**（`effectiveTable` / `effectiveBaselineVersion` / `effectiveJdbcUrl` / 每条迁移的 `state`） |
| **真连同一个库** | 三个进程 `effectiveJdbcUrl` 指向**同一个** PG 16 容器的同一个库 |
| **并发是真的**（不是墙钟凑巧） | 外部预先占住三个账本各自的 advisory lock ⇒ 三进程**同时**卡在 `pg_try_advisory_xact_lock` ⇒ `pg_stat_activity` 实测可见 ⇒ 才放锁 |

### 7.3 判定器 = **命名断言**（守门必须「可红」）

| 断言 | 判什么 |
|---|---|
| `A0-屏障生效` | 观察到进程真的停在 `pg_try_advisory_xact_lock` 上（否则「并发」只是凑巧） |
| `A1-进程启动` | 该域的进程 `ok=true` |
| `A2-gRPC-监听` | TCP 探测通 **且** 日志里有 `gRPC Server started` |
| `A3-表建出` | 该域迁移脚本里的表**全部**建出来了（从 `db/migration/*.sql` 里解析 `CREATE TABLE` 名单，逐个查 `information_schema`） |
| `A4-账本一致` | 账本行数 == 该域的迁移条数（SQL + BASELINE）⇒ 没有重复应用 / 没有被覆盖 |
| `A5-账本独立` | 生效账本表名 == 该域**自己的**独立账本（`flyway_schema_history_<模块>`） |
| `A6-无残留` | 失败迁移不留残表 |

### 7.4 反向注入（证明守门**非空转**）

- **注入什么**：S1 里把 `message` 与 `task` 的 `spring.flyway.table` **改成同一个** `flyway_schema_history_shared`
  （**用启动参数注入，不改任何生产 yaml**）⇒ 复现 AGENTS.md §6.3 问题 4 的**原始病根**（两模块共用一本账 ⇒ 后启动者把自己的迁移判为「已应用」而静默跳过）。
- **正常跑**：`--rounds=1` ⇒ **EXIT=0**，命名断言全绿（`--rounds=2` 那跑同样 **EXIT=0**，13 个进程结果文件全 SUCCESS）。
- **注入跑**：`--mutate-shared-ledger --rounds=1` ⇒ **EXIT=1**，红条（见 §7.4.1）。

### 7.5 本 lane 新发现（**只登记，不修** —— 本 lane 无 `service/**` 写权）

> 登记口径：每条都带**可复现的实测证据**；**没有一条**在本 lane 被修掉。

#### 🔴 新发现 1 —— `discovery://<name>`（**两斜杠**）⇒ 真 rpc 进程**起不来**（连 Flyway 都跑不到）

| 项 | 内容 |
|---|---|
| 涉及 | **5/7** 个 `*-rpc` 的 `application.yml`：`message`（`user-rpc` / `admin-rpc`）· `like`（`message-rpc`）· `article`（`security-rpc` / `message-rpc`）· `comment`（`message-rpc` / `article-rpc`）· `favorite`（`user-rpc` / `article-rpc`）。**只有 `follow-rpc` 写的是三斜杠** `discovery:///user-rpc` |
| 症状（实测两次：fat jar + classpath 各一次） | `java.lang.IllegalArgumentException: Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://admin-rpc'` |
| 栈 | `net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory.newNameResolver(:82)` ← `io.grpc.internal.ManagedChannelImpl.<init>(:638)` ← `net.devh.boot.grpc.client.inject.GrpcClientBeanPostProcessor.initGrpClientConstructorInjections(:102)` |
| 后果 | 炸在 **context refresh 阶段**（`grpcClientBeanPostProcessor` 的 `@PostConstruct`）⇒ **Flyway 根本跑不到** ⇒ 该进程**到不了迁移这一步**。**与 Nacos 在不在线无关**（URI 校验发生在任何网络调用之前） |
| 本 lane 的处置 | **只绕开，不修**：启动参数 `--grpc.client.X.address=discovery:///X` 把地址改回三斜杠（system property 优先级高于 yaml ⇒ **不改任何生产 yaml**）⇒ **缺陷仍在** |
| 备注 | `follow-rpc` 的 yaml 里已经把这个坑写成注释（2026-10-24 真启动实测修正过），**其余 5 个模块没同步** ⇒ 「只有 follow 修好了」这件事本身也是一条待办 |

#### 🔴 新发现 2 —— `like-rpc` / `article-rpc` 的 **fat jar 缺 `grpc-inprocess`** ⇒ 真启动炸

| 项 | 内容 |
|---|---|
| 症状 | `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`（devh 的 `InProcessOrAlternativeChannelFactory` 建 channel 时要它）⇒ `Failed to create channel: message-rpc` |
| 根因 | pom 里 `io.grpc:grpc-inprocess` 是 **test scope** ⇒ 不进 fat jar |
| 本 lane 的处置 | classpath 模式显式补 `~/.m2` 里的 jar（**不改 pom**）⇒ 这两个域**用 classpath 模式能起** |

#### 🔴 新发现 3 —— `task-rpc` 的 jar **没有 `Main-Class`** ⇒ `java -jar` 起不了

| 项 | 内容 |
|---|---|
| 症状 | `java -jar task-rpc-0.0.1-SNAPSHOT.jar` ⇒ 「中没有主清单属性」⇒ 该模块**没法用 fat jar 起**（其它 6 个模块的 jar 都有） |
| 本 lane 的处置 | classpath 模式（`target/classes` + 依赖）起 ⇒ 起得来（真 gRPC server + 真 Flyway，实测 `ok=true`） |

#### 🟡 新发现 4 —— 共享库**并发**启动 ⇒ 后启动者的账本会多一条 `BASELINE(0)`

| 项 | 内容 |
|---|---|
| 现象 | 各模块 yaml 都是 `baseline-on-migrate: true` + `baseline-version: 0`。当某模块启动时 **schema 已经非空**（因为**别的域**先迁完了）⇒ Flyway 在 version 0 处补一条 **BASELINE** 记录 |
| 实测证据 | S2 里 follow 的账本 = **3 行**（BASELINE(0) + V1 + V2），而 follow 自己的迁移只有 **2** 条 |
| 影响 | **没有跳过任何迁移**（V1/V2 都执行、`follow_relation` 建出来了）⇒ **不影响正确性**；但「账本行数 == 迁移条数」这条朴素断言在**并发下会误红** ⇒ 判定器的 A4 期望值已改成「SQL + BASELINE」 |
| 关联 | 这是 `baseline-on-migrate` 在**共享库**里的固有语义（顺序启动时也会出现，不是并发特有）⇒ 但**只有并发压测才看得到** ⇒ 单域测试测不出来 |

#### 🟡 新发现 5 —— postgres 镜像初始化期的「临时 server」会把刚建的库丢掉

| 项 | 内容 |
|---|---|
| 症状 | `pg_isready` 在 initdb 的**临时实例**上也是通的 ⇒ 那时 `CREATE DATABASE` 建出来的库**会被丢掉**（临时实例停掉后正式实例用自己的 datadir）⇒ 三个进程报 `FATAL: database "l11_s1" does not exist` |
| 处置 | 等容器日志出现 `PostgreSQL init process complete` + 建完库**回查 `pg_database`** 确认还在（两者都做了） |

#### 📌 新发现 6 —— `favorite-rpc` 的形态（本 lane **只读观察，零改动**）

| 项 | 内容 |
|---|---|
| 既有结论（L4-01） | `favorite-rpc` **缺 `spring.flyway.baseline-version: 0`**（yaml 只有 `enabled` / `baseline-on-migrate` / `locations` / `schemas` / `table`，**没有 `baseline-version`**）⇒ 共享库非空 schema 下 V1 被 baseline 跳过 ⇒ 形态是「**起得来、首次查库才炸**」（`relation "favorite_folder" does not exist`），**不是「起不来」** |
| 本 lane 的**只读**补充 | 它的 `grpc.client.*.address` 同样是**两斜杠**（`discovery://user-rpc` / `discovery://article-rpc`）⇒ 与**新发现 1** 同款 ⇒ **即使把 baseline 修了，它仍然起不来**（在 grpc client 阶段先炸 ⇒ 到不了 Flyway ⇒ 连「首次查库才炸」都到不了） |
| 本 lane 的动作 | **零改动、零进程**（favorite 域零触碰）⇒ 只做 yaml 层面的只读核对 |

### 7.6 本 lane 自己的踩坑（写下来给下一个跑这个脚本的人）

| # | 坑 | 现象 / 处置 |
|---|---|---|
| 1 | 🔴 **bash + UTF-8**：`$var` 后面紧跟**全角标点**（`：` `（` `）`）⇒ bash 会把全角字符吞进**变量名** ⇒ `unbound variable` 脚本当场退出 | 一律写 `${var}`（脚本已全量改过） |
| 2 | 🔴 `python3 -c "…"` 里带单/双引号的代码 ⇒ 括号少一个就 `SyntaxError` ⇒ 断言静默变红/变绿 | 改成 `python3 - "$file" <<'PY' … PY'` heredoc（脚本已改） |
| 3 | 🔴 `pg_advisory_unlock` **必须由持锁的那个会话**执行 ⇒ 从别的会话调会报 `you don't own a lock of type ExclusiveLock` | 改成 `pg_terminate_backend(pid)` 杀掉持锁的 psql 会话（连接断开 ⇒ 锁自动释放） |
| 4 | 🔴 **屏障的持锁时长**要留够（机器上还有别的 lane 在跑 ⇒ JVM 冷启动可能几十秒） | `hold_lock` 从 180s 提到 **600s**；探针从「每轮 3 次 docker exec」改成「一条 SQL 查全部锁」 |
| 5 | ⚠️ **Flyway 的 advisory lock 是「按账本表名」算 key** ⇒ **共用账本 = 共用锁**（这正是反向注入能复现病根的机制） | 已在 `locknum_of()` 里按 `LOCK_MAGIC_NUM(77431708279161) + javaStringHashCode("\"public\".\"<表名>\"")` 算，实测校准过 |

### 7.7 未覆盖 / 未穷尽（**明确写出来，不许写「已全部对齐」**）

| # | 没覆盖到 | 卡在哪 / 代价多大 |
|---|---|---|
| 1 | 🔴 **只覆盖 3 个域**（follow / task / message），**没覆盖 7 个域** | `like` / `article`（缺 `grpc-inprocess`）、`comment`（两斜杠）、`favorite`（两斜杠 + 缺 baseline-version）**用当前生产 yaml 起不来** ⇒ 要覆盖它们必须**先修生产代码**（本 lane 无写权）⇒ 代价 = 一次跨 lane 的行为修复 |
| 2 | ⚠️ **依赖全是「指向不可达地址」的**：Nacos `127.0.0.1:1`、Kafka `127.0.0.1:9092`（连不上）、Redis `127.0.0.1` | 本 lane 只验 **Flyway 迁移面**，**不验**「Kafka/Nacos/Redis 真在线时的启动面」⇒ 真实依赖在线时是否还有别的启动阻塞，**本 lane 没资格说** |
| 3 | ⚠️ **只测了「迁移 SQL 报错 ⇒ 事务回滚」**，**没测「迁移中途进程被 SIGKILL」** | 后者会留下**半截状态**（PG 事务会回滚，但 DDL 之外的副作用不在本 lane 的判据内）⇒ 需要单独设计一个 kill 时机注入 |
| 4 | ⚠️ **没测 PG 侧异常**：服务端重启 / 网络抖动 / 锁超时（Flyway `lock-retry-count` 默认 50 次后放弃） | 需要在迁移进行中动容器 ⇒ 脚本还没这个能力（代价：加一个「迁移中途 docker restart」的场景） |
| 5 | ⚠️ **随机延迟只覆盖启动偏移 0~4000ms** | 没覆盖「极慢机器」（迁移本身耗时差 10 倍）⇒ `at + dly` 的栅栏只能对齐**开始**时刻，对齐不了**结束**时刻 |
| 6 | 🔴 **本 lane 一句「与 Go 一致」都没说** | 这是**并发形态**验证，不是 Go↔RAG 对照 ⇒ 「Go 侧并发行为如何」**没查**（Go 侧是 GORM `AutoMigrate`，**没有账本、没有 advisory lock** ⇒ 这套并发语义是 **RAG 独有**的） |
| 7 | ⚠️ **判定器是脚本，不是 CI 里的 JUnit 守门** | `tools/difftest/flyway-rpc-concurrent.sh` **不会被 `mvn test` 触发** ⇒ 要么整合者把它接进某个 CI 入口，要么它就只是「一条命令」⇒ 本 lane 不擅自改 CI 配置（不在白名单） |

#### 7.4.1 反向注入的**前后对比**（同一份脚本，只改启动参数）

| 跑法 | 命令 | 退出码 | 红条 |
|---|---|---|---|
| **正常跑** | `bash tools/difftest/flyway-rpc-concurrent.sh --rounds=1` | **EXIT=0** | **无** ——「✅ 所有命名断言（A1/A2/A3/A4/A5/A6）都绿」 |
| **正常跑（加大轮数）** | `bash tools/difftest/flyway-rpc-concurrent.sh --rounds=2` | **EXIT=0** | **无** —— 13 个进程结果文件全 SUCCESS |
| **反向注入** | `bash tools/difftest/flyway-rpc-concurrent.sh --mutate-shared-ledger --rounds=1` | **EXIT=1** | 见下 |

**注入跑里 S1 的原始输出**（截取，2026-10-26 02:18~02:20 的那一跑）：

```
[L11] S1 三域真 rpc 进程并发同库（follow ‖ task ‖ message）
[L11]   ⚠️ 反向注入模式：message 与 task 的账本表被改成同一个 flyway_schema_history_shared
[L11]   屏障：task    账本表=flyway_schema_history_shared ⇒ advisory locknum=77432958440453
[L11]   屏障：message 账本表=flyway_schema_history_shared ⇒ advisory locknum=77432958440453   ← 同一个 key ⇒ 同一把锁
[L11]   ✅ 三个进程**同时**卡在 Flyway 的 advisory lock 上 ⇒ 屏障生效
[L11]     ✅ follow     ok 账本=flyway_schema_history_follow   SQL=2 条 gRPC监听=True
[L11]     ✅ task       ok 账本=flyway_schema_history_shared   SQL=5 条 gRPC监听=True   ← 账本被换掉了
[L11]     ✅ message    ok 账本=flyway_schema_history_shared   SQL=5 条 gRPC监听=True
[L11]   ❌ 断言红：S1 A3-表建出 —— task：表没建出来 ⇒ task_progress user_like_count
```

**共享账本里的权威内容**（`--keep` 保留容器后直接查库，`l11_s1` 库）：

```
 installed_rank | version |             description             | type | success
----------------+---------+-------------------------------------+------+---------
              1 | 1       | create message notification         | SQL  | t
              2 | 2       | create message conversation         | SQL  | t
              3 | 3       | create message conversation message | SQL  | t
              4 | 4       | conversation pair unique index      | SQL  | t
              5 | 5       | message time columns timestamptz    | SQL  | t

public 下的表： block_relation / follow_relation / message_conversation /
                message_conversation_message / message_notification
                （+ 两个账本表）
                ⛔ **task_progress / user_like_count 不在** ⇒ task 的表压根没建出来
```

**红条（判定器确实红了）**：

| 断言 | 内容 | 为什么红（= 病根形态） |
|---|---|---|
| `A3-表建出` | `task：表没建出来 ⇒ task_progress user_like_count` | **task 的表压根没建出来** ⇒ 这正是 AGENTS.md §6.3 问题 4 描述的形态 |
| 汇总里的额外检查 | `task: 有非 SUCCESS 的迁移 [('3','FUTURE_SUCCESS'),('4','FUTURE_SUCCESS'),('5','FUTURE_SUCCESS')]` | **task 的账本里出现了 message 的 V3/V4/V5** ⇒ 两模块的迁移记录**互相污染**（task 把 message 的 V3~V5 当成自己的「未来迁移」） |

#### 7.4.2 ⚠️ 反向注入实测出来的**病根机制**（比台账原文更细）

- Flyway 认定「已应用」是**按版本号**认的 ⇒ 共用一本账时，后启动者**看到同名版本号（V1/V2）就把自己要迁的 V1/V2 判成「已应用」** ⇒ **静默跳过**（`ignoreMigrationPatterns` 默认 `*:future` 把高版本那几条也一并放过）⇒ **它的表压根没建出来**。
- 而**它的进程照样起得来**（本轮 `task`：`ok=true`、gRPC 在监听）⇒ **故障形态是「起得来、首次查库才炸」**（`relation "task_progress" does not exist`）
  ⇒ 与 `favorite` 缺 `baseline-version` 的形态**同类**（L4-01）。
- **谁是输家不固定**（取决于谁先抢到那把共享的 advisory lock）：
  | 跑次 | 输家 | 输家的形态 | 判定器红在哪 |
  |---|---|---|---|
  | 注入跑 #1（02:11） | `message` | **进程起不来**（校验失败 ⇒ `messageAtomicService` 建不出来） | `A1-进程启动` + `A2-gRPC-监听` + `A3-表建出` |
  | 注入跑 #2（02:20） | `task` | **进程起得来，但表没建出来** | `A3-表建出` |
  ⇒ **两种形态判定器都抓得住**（A1 或 A3）⇒ 这就是「守门非空转」的证据。

⇒ **守门非空转已证**：同一份脚本，正常跑 `EXIT=0`、注入跑 `EXIT=1`，且**红在目标断言上**（表没建出来 / 账本被换掉 / 迁移被污染）。

### 7.8 结论一句话

> **三条边界本 lane 全部做到**：① 真 rpc 进程（真 gRPC server + 真 Flyway + 真连同一个库）**3 域并发同库**，且用 advisory-lock 屏障**证明了并发是真的**；
> ② 迁移中途失败（真进程起不来 + 零残留 + 另一个进程完全不受影响）与同模块两副本（账本 2 行不是 4 行）**都有实测证据**；
> ③ N 轮 + 随机启动延迟**跑完了**。
> ⚠️ 但**这不等于「所有域都对齐了」**：另外 4 个域（`like` / `article` / `comment` / `favorite`）用**当前生产 yaml 起不来**（见 §7.5 新发现 1~3），
> 本 lane 也**一句「与 Go 一致」都没说**（并发语义是 RAG 独有的，Go 侧是 GORM `AutoMigrate`，**没有账本、没有 advisory lock**）。

### 7.9 写权与纪律自证

| 纪律 | 自证方式 | 结果 |
|---|---|---|
| **生产代码零改动** | `git status --porcelain` ⇒ 只有 3 个文件：`tools/difftest/flyway-rpc-boot.java`（新）· `tools/difftest/flyway-rpc-concurrent.sh`（新）· `docs/DRIFT/global/lanes/L11-flywaydeep.md`（本台账） | ✅ `service/**` **零改动** |
| **不改 `service/common`** | 同上 | ✅ 零改动 |
| **favorite 零改动** | 同上（`service/favorite/**` 与 `docs/*/favorite/**` **连读都没写**）⇒ 另有一处**只读**核对（favorite 的 yaml 缺 `baseline-version` + 两斜杠地址）已登记在 §7.5 新发现 6 | ✅ 零改动 |
| **机器守门** | `python3 tools/difftest/check-global-lane-ownership.py` | ✅ **EXIT=0**（`L11-flywaydeep [在跑] 白名单展开 8 个文件` · 「在跑的 6 条 lane 写权范围两两不重叠」·「favorite 域零写权」） |
| **不 `git add -A`** | 提交前逐个 `git add` 自己改的 3 个文件 | ✅ |
| **变异不留在工作树** | 反向注入**不走改文件**，只走**启动参数**（`--spring.flyway.table=…`）⇒ 工作树里**没有任何变异残留** | ✅（`git diff` 只有新增文件，没有生产代码 diff） |
| **不 push / 不 merge** | 本次提交**只落在 `align/global-flywaydeep`** | ✅ |

### 7.10 交付与复现

```bash
# 正常跑（应 EXIT=0）
bash tools/difftest/flyway-rpc-concurrent.sh --rounds=2

# 反向注入（应 EXIT=1，红在 A1/A3 上）
bash tools/difftest/flyway-rpc-concurrent.sh --mutate-shared-ledger --rounds=1

# 保留容器便于现场看
bash tools/difftest/flyway-rpc-concurrent.sh --keep
```

**环境要求**：Docker（真 PG 16 容器）· JDK 21 · Maven（**离线 `-o`**）· `~/.m2` 里已有各模块 `0.0.1-SNAPSHOT` 产物
（脚本用 **classpath 模式**（`target/classes` + `dependency:build-classpath` 解析出的依赖）起进程 ⇒ **不需要先 `mvn package`**，
但 `target/classes` 必须是**当次源码编译**出来的 ⇒ 跑之前建议 `mvn -o -q -pl <模块> -am -DskipTests compile`）。
