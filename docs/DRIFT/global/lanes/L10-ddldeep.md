# L10 · `align/global-ddldeep` —— DDL 对照的盲区（**触发器函数体人工核对** + task/message runner 重建）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写** | `tools/difftest/check-ddl-static.py` |
| **可写** | `tools/difftest/ddl*`（新建脚本一律 `ddl` 前缀） |
| **可写（台账）** | `docs/DRIFT/global/lanes/L10-ddldeep.md` |
| **只读** | `service/**`（本 lane **不碰任何生产代码与测试**）· `service/favorite/**`（⛔ 零写权）· `tools/difftest/README.md`（共享说明文件 ⇒ 整合者统一更新） |

**分支**：`align/global-ddldeep` · **工作树**：`../RAG.worktrees/global-ddldeep` · **基线**：`align/global` HEAD

---

## 一、目标

L3 留下的两条盲区：

| # | 事项 | 落点 |
|---|---|---|
| ① | 🔴 **触发器函数体**（`reject_*_mutation()` 的 SQL 文本）**未逐字核对** | `tools/difftest/check-ddl-static.py`（加一个可选维度）+ 人工逐条核对结论 |
| ② | 🔴 `task` / `message` 两域的 DDL runner **未重建**（还能跑、退出码正常，但**不是本轮 runner 的产物**） | `tools/difftest/ddl-parity.sh`（或新建 runner） |

**① 为什么不能靠脚本逐字比对**：函数体是 SQL 文本，**写法不同但语义相同**会被报成差异 ⇒ 反而制造噪音
⇒ L3 的判断是「**需要人工逐条核对**」⇒ 你要做的是：
- ① 先把**函数体**纳入脚本的可比对范围（**结构化**：函数名 + 返回类型 + 参数 + 是否 VOLATILE 等**语义属性**，**不**逐字比 SQL 文本）
- ② 再**人工逐条**核对每个触发器函数体的**语义**（它到底在拒绝什么、Go 侧有没有对应物）⇒ 结论进台账
- ③ 覆盖范围：**like 域 1 个触发器**（`like_domain_fact_identity_immutable`）+ **favorite 域 2 个**（⛔ **只核对不改**，favorite 零写权）

**② runner 重建**：`task` / `message` 两域现在用的是 B3 轮遗留的容器（`taskprobe-postgres` / `msg_ddl_pg`）⇒
把它们改成**本 runner 自己管理的容器**（照 L3 给 `users`/`comment` 做的那样）⇒ 退出码语义与其它域统一。

---

## 三、🔴 样板（**照抄，不要重新发明**）

> ⚠️ **本 lane 不是 rpc 守门 lane** ⇒ 下面 §3.1~§3.3 的三个 rpc 样板（X-5 轴 / slow 轴 / latch 加固）与六条硬性要求
> **对本 lane 不适用**（那是 L6~L9 的）⇒ **不要照抄任何 rpc 测试**。
> ⇒ 本 lane 的真正样板是 **L3 留下的 `tools/difftest/check-ddl-static.py` + `tools/difftest/ddl-parity.sh`**
> （第二十四轮，9 域预设 + `expected_tables` 闸门 + 退出码 3 = N/A）⇒ 做法见 §3.4 与
> [`L3-ddl.md`](L3-ddl.md)（同目录，L3 已合回 `align/global`）。

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

### 3.4 ⚠️ 三个已知事实（自己再核一遍）

- `check-ddl-static.py` 现在有 **9 个域预设**（`users`/`task`/`message`/`admin` + `article`/`like`/`hot`/`follow`/`favorite`）
- ⚠️ **`hot` 域 = N/A**（两侧都没有数据库 ⇒ 脚本给独立退出码 **3**）⇒ 不许用 `EXIT=0` 冒充「已对齐」
- ⚠️ **`favorite` 域只建库、不碰其代码/台账** ⇒ 你对它只能**只读 + 记录**

---

## 八、执行结果（2026-10-26 · L10 执行者）

> **证据强度 = 双侧活体**（真 PG 16，两侧各建一个真库后逐对象比对），**不是源码级**。
> 工具：[`tools/difftest/check-ddl-static.py`](../../../../tools/difftest/check-ddl-static.py) ·
> [`tools/difftest/ddl-parity.sh`](../../../../tools/difftest/ddl-parity.sh)。
> ⚠️ 本 lane **一行生产代码都没改**（`service/**` 零改动）—— 这是**对照 / 守门**任务，不是修复任务。
> ⚠️ **favorite 域零改动**（`service/favorite/**` 与 `docs/*/favorite/**` 未触碰）。

### 8.1 脚本改了什么（逐项 · 只改 2 个文件）

| # | 文件 | 改动 | 为什么 |
|---|---|---|---|
| 1 | `tools/difftest/check-ddl-static.py` | **新增 `QUERY_TRIGGER_FUNC`**（pg_trigger ⋈ pg_proc ⋈ pg_language）：取每个触发器的 **函数名 / 返回类型 / 参数列表 / volatility（VOLATILE\|STABLE\|IMMUTABLE）/ 是否 RETURNS trigger / 语言** | 目标 ①a：**把函数体纳入可比对范围**，但**只比结构化语义属性**、**不逐字比 SQL 文本** |
| 2 | `tools/difftest/check-ddl-static.py` | **新增 `load_trigger_funcs()`** | 同上（取数） |
| 3 | `tools/difftest/check-ddl-static.py` | **新增 `parse_trigger_form()`**：从 `pg_get_triggerdef` 抽 **timing / 事件集 / 粒度（ROW\|STATEMENT）/ 是否 constraint trigger / 有无 WHEN** | 补上一个**既有盲区**：`pg_get_triggerdef` 原来被查出来却**从不比对** ⇒ 「BEFORE 改成 AFTER」这类**语义**差异此前**抓不到**（RAG 侧注释明写「改形式会让 RAISE 的时机不同」） |
| 4 | `tools/difftest/check-ddl-static.py` | **新增 `compare_trigger_funcs()`**：只对**两侧都存在**的触发器比属性；**单边触发器不重复计失败**（已由「触发器集合」维度计过 ⇒ 避免污染退出码） | 防重复计数 ⇒ **退出码语义不被破坏** |
| 5 | `tools/difftest/check-ddl-static.py` | 输出新增两节：「对照 · 触发器形式」/「对照 · 触发器函数体」；`failures += len(form_diffs) + len(func_diffs)` | 让新维度**可观测**且**计入失败** |
| 6 | `tools/difftest/check-ddl-static.py` | **`task` / `message` 两域预设改指 runner 自管容器**：`ddl-task-pg`（`go_task_db`/`rag_task_db`）· `ddl-message-pg`（`go_message_db`/`rag_message_db`），`pg_user=admin` | 目标 ②：原指向 **B3 轮遗留**的 `taskprobe-postgres` / `msg_ddl_pg`（**还能跑、退出码正常，但不是本 runner 的产物**） |
| 7 | `tools/difftest/ddl-parity.sh` | 新增 `task` / `message` 两个域：`port_for`（55568 / 55569）· `domain_config`（探针 + RAG 迁移清单）· 纳入 `all` | 目标 ② |
| 8 | `tools/difftest/ddl-parity.sh` | **新增探针种类 `automigrate`**：task 域**没有** `model.InitDB` —— 真源是在 `service/task/rpc/internal/svc/service_context.go:47` **直接** `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})` ⇒ 探针复刻那**同一行**调用（`internal/Model` 是**大写 M**） | 不复刻 ⇒ 建出来的就不是真源生产那张表（AGENTS.md §3.3.1：推断 ≠ 证据） |

⚠️ **未改**：任何 `service/**`（生产与测试）· `service/favorite/**` · `tools/difftest/README.md`（共享文件 ⇒ 归整合者）。

### 8.2 ✅ 目标 ① · 触发器函数体的**逐条人工核对**（①b / ①c）

> 口径：**逐字**比 SQL 文本**刻意不做**（写法不同但语义相同 ⇒ 制造噪音）⇒ 机器比**结构化属性**，
> **语义**由人逐条读懂。函数体文本两侧**逐字节相同**的结论是用脚本核的（见 §8.2.2）。

#### 8.2.1 触发器核对表（①c 覆盖范围 = like 1 个 + favorite 2 个；另附 2 个上下文对照）

| # | 域 | 触发器名 | 函数名 | **语义：它到底在拒绝什么** | **Go 侧对应物** | **结论** |
|---|---|---|---|---|---|---|
| **1** | `like` | `like_domain_fact_identity_immutable` | `reject_like_domain_fact_identity_change` | `BEFORE UPDATE` **行**触发器：`event_id` / `event_type` / `payload` / `aggregate_id` / `fact_version` / `delivery_envelope` 任一 `IS DISTINCT FROM` ⇒ `RAISE EXCEPTION 'like fact envelope is immutable'`；否则 `RETURN NEW` | **有同名同体**：`service/like/rpc/internal/model/migrations/003_like_fact_delivery.sql:15-32` —— ⚠️ 但该文件**全仓 0 引用**（连测试都不执行；只有 `001`/`002` 被 comment 域的测试脚本引用）⇒ **Go 生产没有这个触发器** | 🟡 **RAG 更严**（已签字族候选 `SD-2026-10-24-CAND-02` 同族）· 两侧函数体**逐字节相同** |
| **2** | `favorite` | `favorite_fact_identity_immutable` | `reject_favorite_fact_identity_change` | `BEFORE UPDATE` **行**触发器：`event_id` / `favorite_id` / `aggregate_version` / `payload` 任一变 ⇒ `RAISE EXCEPTION 'favorite fact envelope is immutable'`；否则 `RETURN NEW` | **有同名同体**：`service/favorite/rpc/internal/model/002_favorite_fact_delivery.sql:10-25` —— ⚠️ 只被 **`_test.go`** 读（`favorite_fact_test.go:65`、`favorite_fact_rpc_test.go:136`）⇒ **Go 生产没有这个触发器** | 🟡 **RAG 更严**（同族）· 函数体**逐字节相同** |
| **3** | `favorite` | `favorite_legacy_marker_immutable` | `reject_favorite_legacy_marker_change` | `BEFORE UPDATE **OR DELETE**` **行**触发器：**无条件** `RAISE EXCEPTION 'favorite legacy fact marker is immutable'`（不 `RETURN NEW` ⇒ 改写被中止） | **有同名同体**：`service/favorite/rpc/internal/model/004_favorite_legacy_fact_marker.sql:18-27` —— ⚠️ 只被 **`_test.go`** 读（`favorite_fact_test.go:79`、`favorite_fact_rpc_test.go:150`）⇒ **Go 生产没有这个触发器**；且 `FavoriteLegacyFactMarker` **不在** Go 的 AutoMigrate 列表（`service/favorite/rpc/internal/model/db.go:33` 只 AutoMigrate `FavoriteFolder`/`FavoriteItem`/`FavoriteFactOutbox`）⇒ **Go 生产连这张表都没有** | 🟡 **RAG 更严**（同族）· 函数体**逐字节相同** |
| A（上下文） | `article` | `article_revision_immutable` | `reject_article_revision_mutation` | `BEFORE UPDATE OR DELETE` **行**触发器：**无条件** `RAISE EXCEPTION 'article revision is immutable'` | **生产必需**：Go `service/article/rpc/internal/model/db.go:61-71` 在 AutoMigrate **之后**检查 `pg_trigger` 里有没有 `article_revision_immutable`，**缺则启动失败** ⇒ 生产侧由**部署前置步骤**建出（`002_community_revision.sql`） | ✅ **两侧都有且语义一致** · 函数体**逐字节相同** |
| C（上下文） | `comment` | `comment_fact_identity_immutable` | `reject_comment_fact_identity_change` | 与 #1 同型（6 列信封） | `service/comment/rpc/internal/model/migrations/003_comment_fact_delivery.sql:15-32` —— ⚠️ **全仓 0 引用** ⇒ Go 生产没有 | 🟡 **RAG 更严**（同族）· 函数体**逐字节相同** |

#### 8.2.2 「函数体逐字节相同」是怎么核的（**不是**目测）

```bash
# 三对函数体（RAG 迁移 vs Go .sql）逐字节比对 ⇒ 三对全部 True
like      : service/like/like-rpc/.../V6__align_like_domain_fact_outbox_with_go.sql
            vs Sea-RideTheWind_golang/service/like/rpc/internal/model/migrations/003_like_fact_delivery.sql
favorite  : service/favorite/favorite-rpc/.../V6__align_favorite_schema_with_go.sql
            vs Sea-RideTheWind_golang/service/favorite/rpc/internal/model/002_favorite_fact_delivery.sql
favorite  : service/favorite/favorite-rpc/.../V6__align_favorite_schema_with_go.sql
            vs Sea-RideTheWind_golang/service/favorite/rpc/internal/model/004_favorite_legacy_fact_marker.sql
⇒ 三个 (完全逐字相同含空白) = True
```

⇒ **函数体的文本没有任何语义漂移**；本轮查的是「**它在 Go 生产里到底存不存在**」（§8.2.3）。

#### 8.2.3 「Go 生产有没有这个触发器」—— 硬证据（**这是本轮最有价值的一条**）

```bash
# ① Go 的**非测试**代码里从不创建任何触发器
grep -rn "CREATE TRIGGER\|CREATE OR REPLACE FUNCTION\|CREATE FUNCTION" --include="*.go" service/ | grep -v "_test.go"
⇒ 0 命中

# ② 每个 Go .sql 的引用面
grep -rn "003_like_fact_delivery"       --include="*.go" --include="*.sh" .   ⇒ 0 命中
grep -rn "002_favorite_fact_delivery"  --include="*.go" --include="*.sh" .   ⇒ 仅 favorite_fact_test.go:65 / favorite_fact_rpc_test.go:136
grep -rn "004_favorite_legacy_fact_marker" --include="*.go" --include="*.sh" . ⇒ 仅 favorite_fact_test.go:79 / favorite_fact_rpc_test.go:150
grep -rn "003_comment_fact_delivery"   --include="*.go" --include="*.sh" .   ⇒ 0 命中
grep -rn "002_community_revision"      --include="*.go" --include="*.sh" .   ⇒ 4 个 _test.go + model/db.go:70（**启动前置检查**）
```

| 域 | Go 侧触发器 | Go 生产**是否**存在 | 依据 |
|---|---|---|---|
| `article` | `article_revision_immutable` | ✅ **存在** | `model/db.go:61-71` 启动时**强制检查**，缺则启动失败 ⇒ 部署前置步骤必做 |
| `like` | `like_domain_fact_identity_immutable` | ❌ **不存在** | `003_like_fact_delivery.sql` 全仓 0 引用；生产只有 `db.go:15` 的 `AutoMigrate`（GORM 不产触发器） |
| `favorite` | `favorite_fact_identity_immutable` | ❌ **不存在** | `002_favorite_fact_delivery.sql` 只被 `_test.go` 读 |
| `favorite` | `favorite_legacy_marker_immutable` | ❌ **不存在** | `004_favorite_legacy_fact_marker.sql` 只被 `_test.go` 读；且该表不在 AutoMigrate 列表 ⇒ **表本身** Go 生产也没有 |
| `comment` | `comment_fact_identity_immutable` | ❌ **不存在** | `003_comment_fact_delivery.sql` 全仓 0 引用 |

#### 8.2.4 「RAG 更严」在**当前写路径**上可观察吗？（⇒ 不可观察）

| 触发器 | 受保护列 | RAG 生产代码实际写的列 | 触发 |
|---|---|---|---|
| #1 like | `event_id`/`event_type`/`payload`/`aggregate_id`/`fact_version`/`delivery_envelope` | `LikeDomainFactOutboxMapper.java:113`（`delivery_status=3`）· `:118`（`delivery_status=2, retry_count+1`）· `:135`（`delivery_status=1, technical_*, delivered_at`）⇒ **一列都不碰** | **永不触发** |
| #2 favorite | `event_id`/`favorite_id`/`aggregate_version`/`payload` | `FavoriteFactOutboxMapper.java:101`（`status=3`）· `:106`（`status=2, retry_count+1`）· `:123`（`status=1, technical_*, delivered_at, updated_at`）⇒ **一列都不碰** | **永不触发** |
| #3 favorite legacy | 整行（UPDATE + DELETE） | `FavoriteLegacyFactMarkerMapper.java` **无任何 `@Insert`/`@Update`/`@Delete`** ⇒ 只读 | **永不触发** |

⇒ 三条都归「🟡 **RAG 更严 ⇒ 当前写路径不可观察**」⇒ 与 L3 对 like 触发器的分类一致；
**favorite 的两条本轮才第一次有「Go 生产没有」的硬证据**（见 §8.7 N-1）。

### 8.3 ✅ 目标 ① · 脚本侧的**结构化语义属性**比对（①a）

**新维度只比这 11 个属性**（**不逐字比 SQL 文本**）：

| 维度 | 属性 | 取值来源 |
|---|---|---|
| **触发器形式** | `timing`（BEFORE/AFTER/INSTEAD OF）· 事件集 · 粒度（ROW/STATEMENT）· 是否 constraint trigger · 有无 `WHEN` | `pg_get_triggerdef` → **解析**成结构化属性 |
| **触发器函数体** | 函数名 · 返回类型 · 参数列表 · `volatility` · 是否 `RETURNS trigger` · 语言 | `pg_proc`（`proname` / `prorettype` / `pg_get_function_arguments` / `provolatile` / `prolang`） |

**实测输出（favorite 域 · 两侧各 2 个触发器）**：

```
对照 · 触发器形式（pg_get_triggerdef → 结构化属性，不比文本）：两侧各 2/2 个，**可比对 2 个**
  （2 个可比对的触发器形式逐条一致 ✓）

对照 · 触发器函数体（pg_proc → 结构化语义属性，**不比 SQL 文本**）：两侧各 2/2 个，**可比对 2 个**
  （2 个可比对的函数属性逐条一致 ✓）
  ✓ favorite_fact_outbox.favorite_fact_identity_immutable
      go  = fn=reject_favorite_fact_identity_change rettype=trigger args=<无> volatility=VOLATILE is_trigger=trigger lang=plpgsql
      rag = fn=reject_favorite_fact_identity_change rettype=trigger args=<无> volatility=VOLATILE is_trigger=trigger lang=plpgsql
  ✓ favorite_legacy_fact_marker.favorite_legacy_marker_immutable
      go  = fn=reject_favorite_legacy_marker_change rettype=trigger args=<无> volatility=VOLATILE is_trigger=trigger lang=plpgsql
      rag = fn=reject_favorite_legacy_marker_change rettype=trigger args=<无> volatility=VOLATILE is_trigger=trigger lang=plpgsql
```

⚠️ **单边触发器只打印、不计失败**（否则与「触发器集合」维度**重复计数** ⇒ 污染退出码语义）。
⚠️ **两侧没有可成对比对的触发器时，输出的是「⚠️ 本维度**什么都没比**（不是「已对齐」）」**
   —— 例如 `like` 域 Go 侧 0 个 / RAG 侧 1 个（生产基线）⇒ 形式与函数属性**都无从比起**
   ⇒ 真正的差异由「触发器集合」维度（仅 RAG 有 ⇒ 计 1 处）承担。
   ⇒ 防止「**跑绿 ≠ 对**」被误读成「本维度已核对」。

### 8.4 ✅ 变异 / 反向注入（证明新维度**非空转**）

> 手法：在**一次性容器**的库里做**反向注入**（改一侧的结构化语义属性）⇒ 看脚本是否**红在目标断言上**。
> 变异脚本放 `/tmp/l10-mutate.sh`（**不进仓**）。

| 变异 | 注入什么 | 注入前退出码 | 注入后退出码 | **红在目标断言上？** |
|---|---|---|---|---|
| **M1** | Go 侧 `ALTER FUNCTION reject_article_revision_mutation() STABLE;`（volatility `VOLATILE→STABLE`） | `0` | `1` | ✅ `⇒ volatility (go=STABLE rag=VOLATILE)` + `实质差异合计 1 处` |
| **M2** | Go 侧 `ALTER FUNCTION ... RENAME TO reject_article_revision_mutation_mutant;`（函数名） | `0` | `1` | ✅ `⇒ 函数名 (go=..._mutant rag=...)` + `实质差异合计 1 处` |
| **M3** | Go 侧把触发器改成 `AFTER UPDATE OR DELETE`（形式） | `0` | `1` | ✅ `⇒ timing (go=AFTER rag=BEFORE)` + `实质差异合计 1 处` |
| **M1 还原** | `ALTER FUNCTION ... VOLATILE;` | `1` | `0` | ✅ 回绿 |
| **M2 还原** | `RENAME` 回去 | `1` | `0` | ✅ 回绿 |
| **M3 还原** | 重建 `BEFORE UPDATE OR DELETE` 触发器 | `1` | `0` | ✅ 回绿 |

**M4 · 脚本级变异（证明「计失败那行」是承重的）** —— 注入 M1 后跑**去掉 `failures += len(form_diffs) + len(func_diffs)` 的副本**：

| 脚本 | 同样的 M1 注入 | 退出码 | 差异是否仍被打印 |
|---|---|---|---|
| 原脚本 | M1 在位 | **1** | 是（`⇒ volatility (go=STABLE rag=VOLATILE)`） |
| 变异副本（`/tmp/check-ddl-static-mutant.py`） | M1 在位 | **0** ⚠️ | **是**（差异照样打印，只是不计失败） |

⇒ 变异副本 **EXIT=0 而差异仍在输出里** ⇒ **正是那行计数让守门变红** ⇒ 新维度**不是空转**，
也说明「**打印出来**」与「**能拦住**」是两件事（本仓 §3.3 的老教训）。

⚠️ **变异跑在一次性容器 `ddl-article-pg` 上** ⇒ 跑完用 `bash tools/difftest/ddl-parity.sh article` 重建回干净状态
（§8.6 的全域复跑**就是重建后的状态** ⇒ 变异已还原，`git diff` 干净）。

### 8.5 ✅ 目标 ② · `task` / `message` 两域的 runner **重建**

| 项 | 之前 | 之后 |
|---|---|---|
| `task` 容器 | `taskprobe-postgres`（**B3 轮遗留**，由 `run-task-http-parity.sh` 拉起，还带 Kafka/Redis 一套） | **`ddl-task-pg`**（`ddl-parity.sh` 自管，端口 55568） |
| `task` 库 | `task_go_db` / `task_rag_db` | **`go_task_db` / `rag_task_db`** |
| `task` Go 侧 schema 来源 | B3 遗留环境的来历不明 | **runner 自建**：探针复刻 `service/task/rpc/internal/svc/service_context.go:47` 的 `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})` |
| `message` 容器 | `msg_ddl_pg`（**B3 轮遗留**，由 `run-message-ddl-parity.sh` 拉起，`pg_user=postgres`） | **`ddl-message-pg`**（端口 55569，`pg_user=admin`） |
| `message` 库 | `go_db` / `rag_db` | **`go_message_db` / `rag_message_db`** |
| `message` Go 侧 schema 来源 | B3 遗留环境的来历不明 | **runner 自建**：探针走真源自己的 `model.InitDB(DBConf)`（`service/message/rpc/internal/model/db.go:20`，内部 `db.go:44` 跑 AutoMigrate） |

**实测退出码（与遗留环境逐条一致 ⇒ 结论没变，只是变成 runner 的产物）**：

| 域 | 遗留环境 | **runner 自管容器** | 差异明细 | 退出码 |
|---|---|---|---|---|
| `task` | `taskprobe-postgres` ⇒ `EXIT=0` | `ddl-task-pg` | 0 处（11 列 / 2 索引 / 2 约束逐条一致；主键名 `task_progress_pkey` ≡ `pk_task_progress` 属**命名形态差，不计失败**） | **0** |
| `message` | `msg_ddl_pg` ⇒ `EXIT=1`（7 处） | `ddl-message-pg` | 同样 7 处（6 处 `DEFAULT CURRENT_TIMESTAMP` 残留 + 索引 `idx_notification_recipient_read` 列不同）⇒ 与 `DRIFT-MSG-V-020` / `SD-2026-10-23-06`（X-10）逐条一致 | **1** |

⇒ **退出码语义与其余 7 域统一**（`0` 一致 / `1` 有实质差异 / `2` 环境不满足 / `3` N/A）。

### 8.6 全域复跑（2026-10-26 · 本 lane 结束时的最终状态 · 9 个域全部是 runner 自管容器）

```
article  EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==
like     EXIT=1 | == 实质差异合计 5 处 ⇒ 退出码 1 ==      # RAG 多 2 CHECK + 1 触发器 + 2 索引（L3 §8.3 #1~#5）
hot      EXIT=3 | ⚠️ 本域两侧都没有业务表 ⇒ **DDL 维度不适用（N/A）**，不是「已对齐」。
follow   EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==
favorite EXIT=1 | == 实质差异合计 4 处 ⇒ 退出码 1 ==      # 2 DEFAULT + 2 RAG 更严 CHECK（L3 §8.3 #6~#9）
users    EXIT=1 | == 实质差异合计 16 处 ⇒ 退出码 1 ==     # 1 可空性 + 8 DEFAULT + 7 索引形态（L3 §8.3 #16~#18）
comment  EXIT=2 | （report_record 仅 RAG 有 ⇒ 已签字 SD-2026-10-24-CAND-02 ⇒ 列集合不一致）
task     EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==      # ✅ 本 lane 重建到 ddl-task-pg
message  EXIT=1 | == 实质差异合计 7 处 ⇒ 退出码 1 ==      # 6 DEFAULT + 1 索引（X-10 已签字）· ✅ 本 lane 重建到 ddl-message-pg
```

| 域 | 容器（本 lane 后） | 退出码 | 差异数 | 与 L3（2026-10-25）基线是否**逐条一致** |
|---|---|---|---|---|
| `article` | `ddl-article-pg` | **0** | 0 | ✅ 一致 |
| `like` | `ddl-like-pg` | **1** | 5 | ✅ 一致 |
| `hot` | `ddl-hot-pg` | **3** | N/A | ✅ 一致 |
| `follow` | `ddl-follow-pg` | **0** | 0 | ✅ 一致 |
| `favorite` | `ddl-favorite-pg` | **1** | 4 | ✅ 一致 |
| `users` | `ddl-users-pg` | **1** | 16 | ✅ 一致 |
| `comment` | `ddl-comment-pg` | **2** | — | ✅ 一致 |
| `task` | **`ddl-task-pg`**（新） | **0** | 0 | ✅ 一致（结论未变，只是换成 runner 自管容器） |
| `message` | **`ddl-message-pg`**（新） | **1** | 7 | ✅ 一致（同上） |

⇒ **新增的两个维度（触发器形式 / 触发器函数体属性）在本轮 9 个域上增加了 0 处差异**
⇒ 退出码与差异数**逐域与 L3 基线一致** ⇒ **退出码语义未被破坏**（`0` 一致 · `1` 有差异 · `2` 环境不满足 · `3` N/A）。


⚠️ **`EXIT=1` ≠ 「有 bug」** —— `like` / `favorite` / `users` / `message` 的 1 是**已登记的已知差异**
（与 [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../_cross/ddl-type-divergences.md) 逐条一致），本轮只是**复现**，不重复登记。
⚠️ **`hot` 仍是 `EXIT=3`（N/A）** —— 两侧都没有数据库 ⇒ **不许**读成「已对齐」。
⚠️ **`comment` 仍是 `EXIT=2`** —— `report_record` 表**仅 RAG 有**（已签字 `SD-2026-10-24-CAND-02`）⇒ 列集合不一致 ⇒ 环境闸门按设计拦下。

### 8.7 🆕 本轮**新发现**（**登记，交整合者路由** ⇒ 不在本 lane 修）

| # | 发现 | 证据 | 落点建议 | 代价 |
|---|---|---|---|---|
| **N-1** | 🔴 **favorite 域的 Go 基线是「**测试夹具**基线」** —— L3 的「触发器 2 ≡ 2」需订正为「**Go 生产 0 个触发器**」 | `ddl-parity.sh` 的 favorite 分支**无条件**把 `001~004` 放进 Go 库；而这 4 份 `.sql` 只被 `_test.go` 读（§8.2.3）⇒ **Go 生产 favorite 有 0 个触发器、且 `favorite_legacy_fact_marker` 表本身也没有** | **favorite 域零写权 ⇒ 本 lane 不动**；建议 favorite 域自己的 lane 把 `001~004` 改成与 `like` 同款的 **`--with-go-sql` 夹具档**（默认走生产 AutoMigrate 基线） | 改 favorite 基线会**改动 L3 的 favorite 数字**（4 处 ⇒ 更多）⇒ 需要 favorite 域 lane 自己重跑并订正台账；本 lane 只登记 |
| **N-2** | 🟡 `comment` 域的 `003_comment_fact_delivery.sql` **全仓 0 引用** ⇒ Go 生产也没有那个触发器 | `grep -rn "003_comment_fact_delivery" --include="*.go" --include="*.sh" .` ⇒ 0 命中 | 与 L3 已有的「comment 的 001~003 是夹具」判定**同向**，本轮补上「**0 引用**」这条硬证据 | 只登记 |
| **N-3** | 🟡 **Go 生产代码从不创建任何触发器**（`CREATE TRIGGER` / `CREATE FUNCTION` 在非测试 Go 代码里 **0 命中**）⇒ 除 `article`（**部署前置步骤** + 启动强制检查）外，Go 生产 schema 的触发器集合为空 | `grep -rn "CREATE TRIGGER\|CREATE OR REPLACE FUNCTION\|CREATE FUNCTION" --include="*.go" service/ \| grep -v "_test.go"` ⇒ 0 命中 | 写进 `docs/DRIFT/_cross/ddl-type-divergences.md` 的「触发器」小节（**共享文件 ⇒ 归整合者**） | 只登记 |
| **N-4** | 🟡 `pg_get_triggerdef` 的文本原来**被查出来却从不比对** ⇒ 「BEFORE 改成 AFTER」这类**语义**差异此前**抓不到**（虽然 `pg_trigger` 集合维度能抓到「单边触发器」） | 本轮已补（§8.1 #3） | — | **已修**（本 lane 内） |

### 8.8 ⚠️ 本轮**没有比 / 没有覆盖**的部分（**不许用「没差异」掩盖「没查」**）

| 项 | 状态 | 原因 / 代价 |
|---|---|---|
| **触发器函数体的逐字 SQL 文本** | ❌ **仍未比**（**刻意**） | 写法不同但语义相同会被报成差异 ⇒ 制造噪音。⇒ **语义只能靠人读**（§8.2 逐条核对），**机器给不了证明** ⇒ **以后新增触发器，这一步不能省** |
| **「两个语义相同但写法不同的函数体」** | ❌ **不可判定** | 本质问题：SQL 语义等价不可判定 ⇒ 只能人工 ⇒ 本 lane 的结论强度 = **人工核对 + 活体属性比对**，不是**机器证明** |
| **函数体覆盖范围** | ⚠️ **人工核对了 5 个**（like 1 · favorite 2 · article 1 · comment 1） | 脚本侧覆盖**全部 9 域**（谁有触发器都比）；**人工**侧只核了这 5 个（= 本仓全部触发器函数） |
| **`pg_get_function_arguments` 里的 `|` 被 `replace()` 抹掉** | ⚠️ **理论缺口** | 本仓 9 个域的触发器函数**全是 0 参** ⇒ 无影响；若将来出现带 `|` 的默认参数表达式，会丢字符 |
| **外键的引用目标 / 分区 / 存储过程 / 物化视图 / COLLATION / `numeric(p,s)` 精度标度** | ❌ **仍未比** | 沿用 L3 §8.6（本仓 9 域都没有这些对象） |
| **favorite 域的 Go 生产基线** | ❌ **未修** | favorite 域**零写权**（§五·4）⇒ 只登记（§8.7 N-1） |
| **`hot` 域** | ⚪ **N/A** | 两侧都没有数据库 ⇒ `EXIT=3`；**不需要任何动作** |
| **`users` / `comment` 两域** | ⚠️ **仍带已知差异** | `users_user.status` 可空性（`DRIFT-USERRPC-V-002`）+ 8 处 `DEFAULT` 残留 + 索引形态差；`comment` 的 `report_record` 已签字 ⇒ 本轮只复现，**不重复登记** |
| **本 lane 之外的「跑绿 ≠ 对」** | ⚠️ 未变 | 脚本 `EXIT=0` 只代表「**两侧没有可报的差异**」⇒ 「**没查的维度**」仍要靠 §8.8 这张表兜底 |

### 8.9 逐条判定表（本 lane 两个目标项）

| # | 目标 | 判定 | 证据 |
|---|---|---|---|
| **①** | 触发器函数体（`reject_*_mutation()` 的 SQL 文本）未逐字核对 | ✅ **已完成** —— ①a 结构化属性进脚本 · ①b 人工逐条语义核对 · ①c like 1 + favorite 2 全覆盖 | §8.2 / §8.3 / §8.4 |
| **②** | `task` / `message` 两域的 DDL runner 未重建 | ✅ **已完成** —— 改用 runner 自管容器，退出码语义与其余域统一 | §8.5 / §8.6 |

⇒ **本 lane 没有产出「要用户拍板」的问题**（用户既定偏好：登记类事项登记完就躺着）⇒ §8.7 的 N-1~N-3 全部是**登记**，不是拍板项。
