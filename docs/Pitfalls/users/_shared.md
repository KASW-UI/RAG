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
