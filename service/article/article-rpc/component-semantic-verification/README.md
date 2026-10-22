# article-rpc 组件级语义一致性验证

> 只读验证。**本次未修改任何源码 / 配置 / pom / 测试。**
> 验证方向：**Go → Spring Boot**。Go = 行为基准，Spring Boot = 待验证的迁移实现。
> 唯一问题：相同输入 / 相同环境 / 相同依赖状态下，两侧**可观察行为**是否一致。

## 0. 结论

# ❌ SEMANTICALLY **NOT EQUIVALENT**

不是「结构没对齐」级别的未等价，而是**已上线可观察行为不一致**，且其中 3 条落在**默认调用路径**上。

| 判据 | 值 |
|---|---|
| Go 参考仓库 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/article/rpc` |
| Spring Boot 目标 | `/Users/pr/Documents/java&golang/RAG/service/article/article-rpc` |
| Go 真源可否活体起 | **否**（依赖 `etcd:2379`，本机不可用）⇒ 无双侧对拍 |
| 依赖活体对拍才能定论的条目 | 已全部移入 [`unresolved.md`](unresolved.md) |

## 1. 差异计数

| 分类 | 条数 | 说明 |
|---|---:|---|
| `EQUIVALENT` | 62 | 矩阵中判定一致的行 |
| `IMPLEMENTATION DIFFERENCE` | 7 | 实现不同、语义相同（可接受） |
| `OBSERVABLE DIFFERENCE` | 6 | 外部能观察到 |
| `SEMANTIC MISMATCH` | 6 | 改变系统应该如何行为 |
| `UNKNOWN` | 4 | 证据不足，不猜 |
| **真差异合计** | **16** | 12 条可观察 + 6 条语义错配（重叠计入 O/M 两栏的按矩阵计） |

`HIGH-RISK DRIFT`：**4 条**（S-1、S-2、S-3、S-5），见
[`semantic-drifts.md`](semantic-drifts.md) 的「反事实测试」栏。

## 2. 六条 SEMANTIC MISMATCH 速览

| # | 组件 / operation | 一句话 |
|---|---|---|
| **S-1** | 公开读视图 · `getPublicArticle(incrView=true)` | UPDATE 被包在 `readOnly=true` 事务里 ⇒ **每次调用必失败** |
| **S-2** | RPC 服务端 · `ListArticles`（作者视角） | `sort_by` 为空时排序方向**反了**（Go 恒 DESC，RAG 走 proto 默认 ASC） |
| **S-3** | MQ 消费者 · 失败位点契约 | Go 的 `ForceCommit` **默认 true ⇒ 失败也提交位点**；RAG 反其道 ⇒ 重投契约相反 |
| **S-4** | MQ 消费者 · 并发度 | Go `Processors` **默认 8** ⇒ 8 个并发处理协程；RAG `concurrency=1` 串行 |
| **S-5** | 外部客户端占位 · content security | 两个 gRPC 客户端是**空文件**，唯一实现恒抛异常 ⇒ **审核链路无法完成** |
| **S-6** | 配置与生命周期 · SQL 超时映射 | `SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被 `invoke` 降级成 `UNKNOWN` ⇒ **业务码 5001 → 1015** |

## 3. 六条 OBSERVABLE DIFFERENCE 速览

| # | 位置 | 一句话 |
|---|---|---|
| **O-1** | Update/Delete 的 not-found 描述 | `"record not found"` → `"article not found: <id>"`（码相同，文案不同） |
| **O-2** | metrics 组件 | Go 的 `article_service` 命名空间前缀在 RAG 里**声明但未使用** ⇒ 指标名全部改变 |
| **O-3** | GetArticle 作者视角 | DB 失败时**多记一个** `minio_request_errors_total{get}`（Go 只记日志） |
| **O-4** | 发布成功通知 | `extra.author_id` 从原串改成**解析后的数字串** |
| **O-5** | DDL 迁移 | `spring.flyway.table` 未设 ⇒ 三个模块共用一本 `flyway_schema_history` |
| **O-6** | gRPC client 超时 | message-rpc deadline 配 3s，Go 默认是 **2000ms**；yml 注释引错了文件行 |

## 4. 既有 79 条测试的判定

**这些测试守的是「RAG 现在的行为」，不构成语义等价的证明。** 本次取证发现其中三类问题：

1. **完全未覆盖**（不是锁错，是没测）
   - 作者视角排序：没有任何测试传过 `sortBy`，`desc` 一律用 `anyBoolean()`
     ⇒ S-2 在 79 条全绿下长期存在。
   - `readOnly=true` 事务里的 UPDATE：`ArticlePublicDomainServiceViewTest` 全 Mockito
     （不经过代理 ⇒ 事务根本不生效）⇒ S-1 同理。
   - Kafka 失败位点契约：只断言了「`seekAfterError=false`」，没有对照 Go 的 `ForceCommit` 默认值
     ⇒ S-3 反而被断言**锁死**。
2. **锁错了期望值**
   - `ArticleSpringContextWiringTest.java:238` 断言 `processor.isSeekAfterError() == false`，
     javadoc 写「Go 的『返回 error ⇒ 不确认』」。该 Go 结论**不成立**
     （`kq/config.go:24` `ForceCommit default=true`）⇒ 这条绿测试会给修复设障。
   - `ArticleMapper.java:147-152` 的注释把 S-2 归类为「Go 的实现瑕疵，不是有意行为」——
     按 AGENTS.md §1.1，Go 的行为（含瑕疵）就是基准，**偏离必须登记为 signed deviation，
     不能靠注释合理化**。
3. **锁死了未完成态**
   - `ArticleSpringContextWiringTest.java:250-266` 断言 content security 端口
     `isInstanceOf(UnavailableContentSecurityClient.class)` ⇒ 一条**绿测试锁住了功能缺失**（S-5）。

## 5. 文档索引

| 文件 | 内容 |
|---|---|
| [`component-contract.md`](component-contract.md) | 8 个组件的共同语义边界（跨组件不变量） |
| [`go-semantic-model.md`](go-semantic-model.md) | Go 侧 8 组件的逐 operation 语义模型 |
| [`springboot-semantic-model.md`](springboot-semantic-model.md) | Spring Boot 侧，**结构与 Go 逐条对齐** |
| [`equivalence-matrix.md`](equivalence-matrix.md) | 核心矩阵：Operation × Semantic Dimension × Go × Java × Result × Evidence |
| [`semantic-drifts.md`](semantic-drifts.md) | 16 条真差异，含反事实测试与 HIGH-RISK 标记 |
| [`test-cases.md`](test-cases.md) | CASE-001…CASE-032 |
| [`unresolved.md`](unresolved.md) | 4 项已查代码但无法定论 |

## 6. 证据纪律声明

- 每条断言带 `文件:行号`，行号指向**消费点 / 判定点**，不是声明点。
- Go 侧事实一律回 `Sea-RideTheWind_golang` 原文或 `go.mod` 锁定的依赖源码
  （`go-zero v1.10.2` / `go-queue v1.2.2`，实际可读缓存为 `v1.10.3` / `v1.2.2`），
  **不采信 RAG 侧注释或 javadoc 作为基准**——本次已抓到 4 处 RAG 注释的 Go 事实错误。
- 未凭注解名称断定机制生效：S-1 一路追到 **pgjdbc 字节码**
  （`QueryExecutorImpl` 的 `beginReadOnlyTransactionQuery = "BEGIN READ ONLY"`）
  才敢下结论。
- 「配置里没写」≠「没开」：S-3 / S-4 / O-6 三条正是靠**读到 struct tag 的 default** 才成立的。