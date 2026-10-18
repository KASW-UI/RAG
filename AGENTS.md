# AGENTS.md: RAG 项目政策与当前重构状态

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **2026-10-18 重写**：原文件是从 vLLM 模板继承的、含大量与本项目无关的内容（GGUF、ModelRegistry、FusedChain 等）。本次重写只保留 RAG 实际需要的政策与本次重构的当前状态。

## 一、项目身份与三轨策略

RAG 仓库路径：`/Users/pr/Documents/java&golang/RAG`。

它是 Sea-RideTheWind 系列的第三个形态，三个项目各司其职：

| 项目 | 路径 | 角色 | 实现风格 |
|---|---|---|---|
| Sea-RideTheWind_golang | `~/Documents/java&golang/Sea-RideTheWind_golang` | **真理之源**（Go 实现） | go-zero + GORM |
| Sea-RideTheWind_java | `~/Documents/GitHub/Sea-RideTheWind_java` | 1:1 Go 镜像（Java 实现） | JPA + flat `internal/{logic,model,server,svc}` |
| **RAG（本仓库）** | `~/Documents/java&golang/RAG` | **理论 Java 树（目标架构）** | **MyBatis-Plus + domain/mapper/service 分层 + Flyway + Bootstrap 抽出** |

**RAG 不再是 Sea-RideTheWind_golang 的 1:1 镜像**——它追求的是 Java 圈常见的 Spring 风格 idiom。这是项目差异化的核心。

在 2026-10-18 之前，RAG 的 `service/users` 与 `Sea-RideTheWind_java/service/users` **字节一致**（同一份代码复制）。从 2026-10-18 起两条线分叉：

- **Sea-RideTheWind_java**：保持不变，仍为 Go 1:1 镜像，保留 JPA + Logic/Server 风格。
- **RAG**：开始按各子模块 `HUMAN_ONLY.MD` 的"Java 理论文件树"重构。

## 二、HUMAN_ONLY.MD：设计目标，不是现状

`service/users/{user-rpc,user-api,admin-rpc,admin-api,users-common}` 每个子模块各有一份 `HUMAN_ONLY.MD`，包含两部分：

1. **文件阅读顺序与职责**：描述当前实现下每个文件的角色（这一部分反映现状）。
2. **Java 理论文件树**：描述目标架构——`domain/Entity + mapper/Mapper + service/DomainService + service/impl/GrpcImpl + bootstrap/* + resources/db/migration/*.sql`。

Sea-RideTheWind_java 的代码**从未遵循第 2 部分**——`HUMAN_ONLY.MD` 是 aspirational design，从未被代码落地过。RAG 的本次重构就是把这个目标架构真正实现一遍。

## 三、当前重构（service/users → 理论 Java 树）

### 3.1 锁定的决策（来自用户问答，2026-10-18）

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper 接口 `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService`（admin-rpc 是 1 个 `AdminDomainService`） | HUMAN_ONLY.MD 描述的是单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口（user-rpc：2 个；admin-rpc：3 个） | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + Flyway `V*__create_*.sql` DDL | HUMAN_ONLY.MD 列出这两个文件，且当前项目根本没有 Flyway |

### 3.2 5 阶段范围

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **Phase 0** | RAG 仓库建 worktree、提交基线、4 个 issue、4 份 spec（`RAG/.agents/specs/<slug>.md`） | 4 个 issue + 4 份 spec 落仓 |
| **Phase 1（pilot）** | `user-rpc` 落地完整 pattern：JPA→MyBatis-Plus + DataModel→2 Mapper + 9 Logic 合 1 + SnowflakeBootstrap + Flyway V1/V2 | `mvn -pl user-rpc -am package` 通过 + 既有 LogicTest 改完跑绿 |
| **Phase 2** | `user-api`：HTTP 层（controller / dto / interceptor / advice / client），不引入 Mapper | `mvn -pl user-api -am package` 通过 |
| **Phase 3** | `admin-rpc`：平行 user-rpc，13 用例合 1 `AdminDomainService` + 3 Mapper + Flyway V3 | `mvn -pl admin-rpc -am package` 通过 |
| **Phase 4** | `admin-api`：平行 user-api，13 Service + 2 client（admin-rpc + user-rpc） | `mvn -pl admin-api -am package` 通过 |
| **Phase 5** | 全量 `mvn clean package` + docker-compose 起来跑 gRPC 端到端 | 全绿 |

每阶段独立 stop point：跑绿后停下等用户 review，再决定继续。

### 3.3 用户问答原话（备查，避免后续被覆盖）

| 问题 | 用户答案 |
|---|---|
| ORM 这一层怎么处理？ | 严格按 HUMAN_ONLY：JPA → MyBatis-Plus（重写数据层） |
| Logic 类怎么处理？ | 严格按 HUMAN_ONLY：9 合 1 |
| DataModel.java 怎么映射？ | 严格按 HUMAN_ONLY：拆成两个 Mapper（仅在选 MyBatis-Plus 时适用） |
| SnowflakeBootstrap 与 Flyway 脚本要不要落地？ | 落地：从 UserRpcApplication 抽 SnowflakeBootstrap + 引入 Flyway + 写两份 DDL |
| 全量 5 阶段怎么走？ | 你先修改 `/Users/pr/Documents/java&golang/RAG/AGENTS.md`，说明是什么情况 |
| 重构以什么形式落到 RAG 仓库？ | 先等下（暂停，等本文件落地后再议） |

## 四、用户协作规则（精简）

1. **每个 phase 一个 PR**。PR 自带 spec（`RAG/.agents/specs/<slug>.md`），提交前 spec 已落仓。
2. **issue 在 PR 之前**。每个 phase 起手先在 RAG 仓库开 issue 跟踪，PR body 引用 issue。
3. **worktree + 分支**。每个 phase 在独立 worktree 里做，分支名 `row/users-refactor-<module>`。
4. **不可越界**：不动 `Sea-RideTheWind_java`、不动 `Sea-RideTheWind_golang`。本次重构仅限 RAG 仓库。
5. **Go 是真理之源**。所有 proto 字段号、错误码、JWT claims、表结构、HTTP path、Kafka topic 等语义细节以 `Sea-RideTheWind_golang` 为准——RAG 不偏离 Go 行为，只重写 Java 表达方式。
6. **session 语言**：用户用中文；技术术语、代码、文件路径保留英文。
7. **review**：每个 PR 走完本地 `mvn -pl <module> -am package` + 全量 `mvn clean verify` 后，等待用户或独立 review 才能合并。

## 五、当前状态（截至 2026-10-18 22:00）

- Phase 0-5：**全部未启动**
- 本文件（`AGENTS.md` 重写）是 Phase 0 的第一步
- **下一步待用户确认**：
  - Phase 推进形式（每个 phase 一个 worktree + PR？还是单 worktree 多 phase commit？）
  - 是否需要先输出 Phase 1 的完整 spec + 新文件预览供 review，再允许动手

## 六、引用清单

- **4 份模块 HUMAN_ONLY.MD（设计目标）**：
  - `service/users/user/user-rpc/HUMAN_ONLY.MD`
  - `service/users/user/user-api/HUMAN_ONLY.MD`
  - `service/users/admin/admin-rpc/HUMAN_ONLY.MD`
  - `service/users/admin/admin-api/HUMAN_ONLY.MD`
- **1 份通用规范**：`service/users/users-common/HUMAN_ONLY.MD`
- **参考基线（Go 真理之源）**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`
- **保持不变的镜像**：`/Users/pr/Documents/GitHub/Sea-RideTheWind_java`
- **本仓库根目录**：`/Users/pr/Documents/java&golang/RAG`