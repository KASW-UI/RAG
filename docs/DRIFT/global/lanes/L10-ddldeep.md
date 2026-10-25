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

**分支**：`align/global-ddldeep` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-ddldeep` · **基线**：`align/global` HEAD

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
