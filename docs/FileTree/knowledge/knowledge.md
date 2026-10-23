# knowledge — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/knowledge`（**单 HTTP 服务，无 rpc 模块**）
> 目标根包：`com.rag.knowledge.knowledge`
> 推导日期：2026-10-23
> 状态：**理论推导，未实施**。本文只产出文件树与职责映射，不含任何代码迁移。

---

## ⚠️ 前置声明：本 Component 不在本项目迁移范围内

`AGENTS.md` §1（2026-10-23 12:25 修订）：本项目搬 **10 个微服务** ——
`article` / `hot` / `like` / `users-user` / `users-admin` / `message` / `comment` / `favorite` / `follow` / `task`。
Go 侧剩余 **3 个微服务**（`knowledge` / `points` / `security`）**不在范围内**。

⚠️ **2026-10-23 12:25 变更**：此前本文件写作「另有 7 个微服务不在范围内」——
`comment` / `favorite` / `follow` / `task` 四域**已升为范围内**（**当前仅 proto 层已搬齐**，
api / rpc / common 仍为空壳 ⇒ 见 [`../../DRIFT/_cross/new-domains-2026-10-23.md`](../../DRIFT/_cross/new-domains-2026-10-23.md)）。

本文是对「若要迁移」的技术推导，**不是待办**，也不改变 §1 的范围决策。若后续要落地，需先由用户重新定义范围。

---

## 12.0 本次推导中改写基线的两个关键发现

在进入七段结构之前，先记录两条**推翻了直觉假设**的发现——它们决定了整棵文件树长什么样。

### 发现 1：Go 的 `logic/` 层是空的，业务语义全在 `model/`

`api/internal/logic/` 的 **76 个文件几乎全是 goctl 脚手架**，每个文件形状相同：

```go
// api/internal/logic/knowledge/list_modules_logic.go:29-35
func (l *ListModulesLogic) ListModules(req *types.ListModulesReq) (resp *types.ListModulesRespEnvelope, err error) {
    result, err := l.svcCtx.Store.ListModules(l.ctx, req.Limit, req.Cursor, true)
    if err != nil { return nil, err }
    return &types.ListModulesRespEnvelope{Code: 200, Msg: "success", Data: result}, nil
}
```

真正的事务、状态机、行锁、幂等、outbox、错误语义**全部在 `api/internal/model/`（62 文件，约 14k 行）**。

⇒ **对映射的含义：Go 的 `logic/` 对应 Java 的 `controller/`，而不是 `service/`。**
若按 `logic/` 文件数规划 Java 文件树，会把领域低估约一个数量级。

### 发现 2：RAG 的 `api/` / `rpc/` 拆分对 knowledge 不成立

RAG 每个域都是 api + rpc 两个 Maven 模块，且有一条**贯穿全仓的硬结构事实**：

- api 模块有 `spring-boot-starter-web`，**没有** mybatis-plus / postgresql；
- rpc 模块有 mybatis-plus / postgresql，**没有** `spring-boot-starter-web`。

knowledge 则是**一个进程同时**具备：HTTP 路由 + 35 张表的直连读写 + 两个后台投递轮询。
仓库里**没有任何一个模块是这个形态**（`@KafkaListener` 与 `@Scheduled` 在 api 模块中出现次数为 **0**）。

拆成 api/rpc 会**凭空引入一条 Go 侧不存在的 gRPC 契约**——那是新增 L1 契约，不是迁移。

⇒ 推导结论：**单 Maven 模块**，包名沿用 RAG 已有词汇（详见 12.5.1 的偏离说明）。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| 区域 | 已落地的包 | 证据 |
|---|---|---|
| api | `advice/` `client/` `config/` `controller/` `dto/` `interceptor/` `metrics/` `service/` | `message-api`、`article-api`、`hot-api`、`like-api`、`users/*-api` |
| rpc | `bootstrap/` `client/` `config/` `domain/` `exception/` `mapper/` `metrics/` `service/` `service/impl/` | `message-rpc`、`article-rpc`、`like-rpc`、`users/*-rpc` |
| **rpc 独有** | `mq/` | `like-rpc/mq/`（`LikeHotEventOutboxRelay` `LikeUpdateKafkaListener`）、`article-rpc/mq/`、`hot-rpc/mq/` |
| 共享 | `com.rag.common.*`（40 类）+ 各域 `*-common` | `service/common/src/main/java/com/rag/common/**` |

### 已确认的框架机制

| 机制 | 证据 |
|---|---|
| Spring Boot **4.0.8** / Java **21** | `pom.xml:6-11`、`:22` |
| Mapper 走 **`@MapperScan` + 注解式原生 SQL**，全仓**零 XML mapper** | `MessageRpcApplication.java:66`；`ConversationMapper.java:41-52,96-105`；`LikeOutboxEventMapper.java:28-72` |
| 动态 SQL 用 `@Select("<script>…")`；**`<>` 会破坏 XML 解析**需 CDATA 孪生常量；**`<where>` 不可用**（基表已带 WHERE） | `ArticlePublicMapper.java:62-85,101-131,106-114` |
| jsonb **必须** `ps.setObject(i, json, Types.OTHER)`，否则 PG 报 `character varying` | `JsonbMapTypeHandler.java:64`（javadoc `:19-36` 记录该失败） |
| `@TableName(autoResultMap = true)` 是 jsonb 读侧前提 | `JsonbMapTypeHandler.java:44-47` |
| **无 MyBatis-Plus 分页拦截器**；分页手工拼 `LIMIT/OFFSET` | `GoStylePagination.java:56-69` |
| gRPC 拦截器**不自动注册**，须 `GrpcServerConfigurer` 显式 `intercept()` | `MessageRpcApplication.java:104-108` |
| api 侧 `MessageController` 与 `MessageAdminController` 按 **JWT key 拆分**，经 `WebMvcConfig` 按路径前缀注册 | `WebMvcConfig.java:54-86` |
| 熔断/限流/降级已由 `com.rag.common.protection` 提供 | `GoStyleBreaker` `GoStyleMaxBytesFilter` `GoStyleCpuSheddingFilter` |
| 日志队列化、丢弃计数、flush 屏障 | `com.rag.common.observability.Observability` + `HttpMiddlewareFilter` |

### 当前 Component 可以直接复用的能力（REUSE 候选）

| 能力 | 复用对象 | 适配度 |
|---|---|---|
| 响应信封类型 | `com.rag.common.response.Response` | ✅ 仅字段形状 |
| 业务异常基类 | `BusinessException` / `AuthenticationException` | ⚠️ 需 knowledge 子类补 499/410 语义 |
| JSON 严格解析 | `GoStyleJsonBodyConverter` / `GoStyleCoercion` | ⚠️ 需补重复键检测 |
| 路径参数归一化 | `GoStylePathNormalizingFilter` | ✅ |
| 404/405 字节形状 | `GoStyleRouterErrorResponses` | ✅ |
| HTTP 可观测 | `HttpMiddlewareFilter` + `Observability` | ⚠️ 见 12.4 的差距 |
| 体积上限 | `GoStyleMaxBytesFilter` | ⚠️ 全局值，knowledge 需要**按端点**不同上限 |
| 调度生命周期 | `@Scheduled` + `@EnableScheduling` | ✅ |

### 需要进一步确认的组织约定（本 Component 特有）

| # | 议题 | 状态 | 本文的处理 |
|---|---|---|---|
| 1 | api/rpc 拆分不适用 | **已决** | 单模块，见 12.5.1 |
| 2 | 成功响应是否包 envelope | **已决** | **不包**。Go 由 logic 直接返回 `XxxEnvelope`；若复用 RAG 的 `GlobalResponseAdvice` 会**双层包裹** |
| 3 | `/v1/knowledge` 下 public 与 admin 路径交错 | **已决** | 两个 Controller 共享前缀，靠 (path, method) 区分；5 组路径**互不相交**，已逐条核对 |
| 4 | 同一前缀下两套 JWT secret | **需装配层设计** | 单个 `KnowledgeJwtAuthInterceptor` 按 handler 解析适用 secret |
| 5 | 主键策略 | **已决，偏离 RAG** | `prefix_UUID` 文本 + `IdType.INPUT`；**不用** snowflake `ASSIGN_ID` |
| 6 | `list_order` 非主键 IDENTITY 列 | **已决，需专门说明** | 见 12.5.4 |
| 7 | `cmd/subjectref-preflight` | **已决** | 不作为服务迁移；其库依赖 `internal/subjectrefturn` 要迁移 |

---

## 12.2 第二部分：Go 原始文件树

```
service/knowledge/
├── api/
│   ├── knowledge.go                          # main：启动、遥测、两个轮询协程、就绪自探
│   ├── etc/knowledge-api.yaml
│   └── internal/
│       ├── handler/
│       │   ├── routes.go                     # 73 条路由，5 个认证边界
│       │   ├── response.go                   # 全局错误信封映射
│       │   ├── knowledge/                    # 38 handler + 2 guard
│       │   ├── worker/                       # 21 handler
│       │   ├── product/                      # 10 handler + tool_request.go
│       │   └── product_v2/                   # 3 handler
│       ├── logic/
│       │   ├── knowledge/                    # 39（薄脚手架）
│       │   ├── worker/                       # 19（薄脚手架）
│       │   ├── product/                      # 14（**唯一有真实编排的一层**）
│       │   └── product_v2/                   # 4（只读投影）
│       ├── model/                            # 37 文件 ≈ 14k 行 ← **真正的领域**
│       │   ├── schema.sql                    # 31 张表
│       │   ├── store.go                      # command() 幂等引擎 + 事件发射
│       │   ├── outbox.go / observability.go / readers.go / pagination.go ...
│       ├── middleware/                       # administrator_middleware.go / worker_middleware.go
│       ├── mqs/                              # outbox.go / wiki_compile_jobs.go（HTTP，非 Kafka）
│       ├── telemetry/                        # http.go / log.go / operation.go / runtime.go
│       ├── object/store.go                   # 内容寻址对象存储
│       ├── svc/service_context.go            # 依赖装配 + 约 20 条启动校验
│       ├── config/config.go
│       └── types/types.go                    # 180 个 DTO
├── cmd/subjectref-preflight/                 # 独立 main：离线迁移校验工具
├── internal/subjectrefturn/keys.go           # 被 api 服务复用
└── scripts/migrate-*.sql                     # 9 个迁移脚本
```

**规模**：非测试 Go 代码 **18,918 行**，约 208 个源文件。

---

## 12.3 第三部分：Go 文件职责分析（按聚合，不按文件）

### 3.1 `handler/routes.go` —— L1 外部契约

5 个 `AddRoutes` 调用，边界互不相交：

| 组 | 前缀 | 中间件 | 条数 |
|---|---|---|---|
| public | `/v1/knowledge` | **无** | 6 |
| admin | `/v1/knowledge` | `WithJwt(Auth.AccessSecret)` + `Administrator` | 33 |
| user(v1) | `/v1/knowledge` | `WithJwt(UserAuth.AccessSecret)` | 10 |
| user(v2) | `/v2/knowledge` | `WithJwt(UserAuth.AccessSecret)` | 3 |
| worker | `/internal/v1/knowledge` | `Worker`（**非 JWT**） | 21 |

### 3.2 `handler/response.go` —— 错误契约（L1）

`ConfigureResponses()` 安装全局错误处理器，`code` **直接等于 HTTP status**：

| 错误 | HTTP | msg |
|---|---|---|
| `identity.ErrInvalidClaim` | 401 | `authenticated user required` |
| `ErrUserNotFound` / `ErrIdentityMismatch` | 403 | `authenticated user unavailable` |
| `ErrUserInactive` | 403 | `authenticated user inactive` |
| `ErrStatusUnavailable` | 503 | `authenticated user status unavailable` |
| gRPC `Unavailable` | 503 | `knowledge service unavailable` |
| `model.ErrInvalid` | 400 | `err.Error()` |
| `model.ErrNotFound` | 404 | `err.Error()` |
| `model.ErrConflict` | 409 | `err.Error()` |
| `model.ErrArtifactUnavailable` | **503**（可重试） | 固定文案 |
| `model.ErrUnavailable` | **410**（永久） | `err.Error()` |
| `DeadlineExceeded` | 504 | — |
| `Canceled` | **499**（非标准） | — |
| 默认 | 500 | `knowledge service unavailable` |

> 🚩 **`ErrUnavailable`(410) 与 `ErrArtifactUnavailable`(503) 是两种不同的"不可用"**：
> 前者=已撤回，永久；后者=内容损坏，可重试。合并会改变客户端重试行为。

### 3.3 `handler/response.go` + `types.go` —— 成功契约（L1）

handler 用 `httpx.OkJsonCtx(ctx, w, resp)`，**没有** `SetOkHandler`。成功体是 **logic 构造的领域类型**：

```go
// api/internal/types/types.go:132-136
type BuildEnvelope struct { Code int `json:"code"`; Msg string `json:"msg"`; Data Build `json:"data"` }
```

且恒为 `{code:200, msg:"success", data:...}`（`list_modules_logic.go:34`）。
⇒ **成功路径已有信封；Java 侧绝不能再包一层。**

分页为 **cursor 式**：`{items:[...], next_cursor:"..."}`，`limit` 由 tag 默认（12/20/50 各异）。

### 3.4 三个 guard —— 严格 JSON 字面量校验（L3）

| 文件 | 体积上限 | 校验内容 |
|---|---|---|
| `wiki_fact_set_request_guard.go` | 768 KiB | 重复键、未知键、逐字段 JSON 类型、嵌套数组元素数（`source_revisions`≤64 / `facts`≤128）、尾随 token |
| `wiki_quality_request_guard.go` | 32 KiB | 13 个白名单键，全部必须为 string，重复/未知/尾随一律拒绝 |
| `product/tool_request.go` | 8192 B | `Content-Type` 必须是 `application/json`；`DisallowUnknownFields`；尾随 token |

> 目的均为：**在 go-zero 类型化解析器丢弃信息之前**拦截多余/重复的 source/fact 声明。

### 3.5 `middleware/` —— 认证与授权（L1/L3）

| 中间件 | 机制 | 失败 |
|---|---|---|
| `AdministratorMiddleware` | 从 `ctx["userId"]` 取值（接受 `string` 与 `json.Number`），比对配置白名单 | **403** `{403,"knowledge administrator required"}` |
| `WorkerMiddleware` | `Authorization: Bearer <token>` 与配置值**常数时间**比较；**配置为空则恒 401（fail-closed）** | **401** `{401,"worker identity required"}` |

### 3.6 `logic/` —— 唯一有真实编排的一层

`product/` 的两个 create 流是核心：

```
解析身份 → 幂等预占 → 起 span → 复核既有结果
  → 抢占租约(lease) → 调上游 BTW → 从库里对账(detached ctx) → 完成 | 失败
```

外部调用：
- **gRPC**：User RPC 解析身份（v1 与 v2 路径不同：v2 **重新实时解析 JWT** 再与库中 v1 行交叉核对）
- **HTTP**：BTW `POST /v1/search/summary`、`POST /v1/search/tools/search`
  - 认证：`X-Sea-Search-Scope: b64url(payload) + "." + b64url(HMAC-SHA256(payload, ScopeKey))`，TTL 120s
  - tools 的 scope 过期取 `min(now+120s, parent.ExpiresAt)`，HTTP 预算被**父对象剩余寿命钳制**
  - 禁止跟随重定向；响应 ≤1 MiB；严格 JSON；**无重试**；任何失败都归一为同一个 opaque 错误

### 3.7 `model/store.go` —— `command()` 幂等引擎（L3 核心）

所有管理端写操作的统一骨架：

1. 校验 key 非空且 ≤200 字节 → `input_hash`
2. `Begin`
3. **`SELECT pg_advisory_xact_lock(hashtextextended(scope||'/'||key,0))`** ← 按幂等键串行化
4. 读 `knowledge_operations` 持久行：hash 相同 ⇒ **重放**；不同 ⇒ `IDEMPOTENCY_CONFLICT`
5. **`set_config('knowledge.operation_id', …, true)`**（事务局部 GUC）供事件继承
6. 执行业务 + 插入幂等行 + `Commit`

⇒ **领域状态 + outbox 行 + 幂等行原子提交。**

### 3.8 `model/` —— 13 组状态机（L2）

模块（`ENABLED/WITHDRAWN`）、Build（`BUILDING→READY/FAILED/CANCELLED/SUPERSEDED`）、Compile（`→ACCEPTED/...`）、发布指针（CAS + 单调 `PointerRevision`）、Product search、Tool 预算计数器、Accepted answer（append-only + ordinal）、Grounding review（advisory lock 单赢）、Reviewer key、Search judgment、Wiki fact judgment、Wiki FactSet。

**锁序铁律：模块行永远第一个加** `FOR UPDATE`。

**「在提交时校验」而非「先校验再写」**：慢外部操作之后，在锁内重校验，拒绝则整个事务回滚（含先前写的 revision/head/outbox）。

### 3.9 `mqs/` —— 投递（L3）

**没有 Kafka**。两条 HTTP 通道读同一张 `knowledge_outbox`：

| 通道 | 传输 | 批 | 单条超时 | 认领 |
|---|---|---|---|---|
| H04 | `POST <Endpoint>`，`Idempotency-Key: <event_id>` | 16 | 10s | `FOR UPDATE SKIP LOCKED LIMIT 1` |
| WikiCompileJobs | `POST/GET/POST /v1/jobs`（loopback） | 16 | 10s | 同上，排除 3 类 compile 事件 |

> 🚩 **HTTP 调用发生在持有行锁的事务内**；收据不匹配 ⇒ 不写 `delivered_at` ⇒ 回滚 ⇒ 下次重发同一 `event_id`。
> 🚩 **无死信、无重试上限、无退避**——永久失败的事件永远 `delivered_at IS NULL`。

### 3.10 `svc/service_context.go` —— 启动校验（L3）

约 **20 条**跨字段 fail-fast 校验，含：密钥非空、SubjectRefV2×`Mode=pro` 冲突、WikiJobs 端点必须 **loopback IP + 路径 `/v1/jobs` + mode∈{dev,test}**、scope 版本交叉约束、scope key ≥32 字节、fast/detailed 预算区间且 `RestConf.Timeout ≥ detailed+5s`、连接池 ≥2、**实时 ping**、对象存储 backend 探测、9 个按开关门控的 schema 探针。

> 这些**没有一条**能被 Spring DI 吸收——它们是真实的 L3 行为。

### 3.11 `telemetry/` + `object/` + `cmd/`

- **telemetry**：45 个**静态注册**的 operation 名（未注册静默改写为 `knowledge.unknown`）；HTTP 中间层用**路由模板**而非原始 path 作 metric label；`/metrics` 显式排除在埋点外；日志队列**溢出即丢、绝不阻塞请求**。
- **object**：内容寻址（`sha256/<hex>`），**每次读都校验哈希**，>16 MiB 或不匹配即失败；无版本/无生命周期/无 GC；**孤儿对象是有意接受的**（写入不在事务回滚范围内）。
- **cmd/subjectref-preflight**：独立 main，`READ ONLY + REPEATABLE READ`，只输出**键的哈希**，退出码 3 表示阻塞。⇒ **不作为服务迁移**；但它依赖的 `internal/subjectrefturn` **要迁移**（api 侧以更严的 `RejectAnyExactDuplicate: true` 调用）。

---

## 12.4 第四部分：职责到 Java 的映射表

### 12.4.1 基础设施与横切

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `svc/service_context.go` 依赖持有与注入 | Spring Bean / 构造器注入 | **ABSORB** | 纯依赖图，无业务语义 |
| `svc` 约 20 条启动校验 | `bootstrap/KnowledgeStartupValidator` | **NEW** | 跨字段 fail-fast 不是 DI 能表达的；`LikeSchemaGate` 是同类先例 |
| `svc` 按开关门控的 9 个 schema 探针 | `bootstrap/KnowledgeSchemaGate` | **NEW** | 同上；`LikeSchemaGate.java:37-55` 形态一致 |
| `knowledge.go` 就绪自探（校验 `X-Knowledge-Instance-ID`） | `bootstrap/KnowledgeReadinessProbe` | **NEW** | 自有探针，无框架等价物 |
| `knowledge.go` 关停顺序（cancel→wait→close→flush） | `@PreDestroy` + `DisposableBean` | **ADAPT** | 语义保留，机制换成容器生命周期 |
| `telemetry/http.go` HTTP 埋点 | `com.rag.common.observability.HttpMiddlewareFilter` | **REUSE**（部分） | 已有 `@Order(HIGHEST_PRECEDENCE+5)` 全局 Filter；但 operation-id / `/metrics` 排除需补 |
| `telemetry/operation.go` 45 个 operation 名注册表 | `metrics/KnowledgeOperations` | **NEW** | 领域特有的名字集合 |
| `telemetry/log.go` 队列化日志 | **缺口** | **UNKNOWN** | RAG 只有 Micrometer，**无日志队列/落盘组件**；见 12.7 风险 |
| `object/store.go` 内容寻址对象存储 | `object/`（端口 + local/s3 两实现） | **NEW** | `article-rpc` 有 MinIO 先例但无内容寻址与读校验 |
| `mqs/outbox.go` H04 投递循环 | `mq/KnowledgeOutboxRelay` | **ADAPT** | 结构同 `LikeHotEventOutboxRelay`；但认领 SQL 与收据校验须逐条照搬 |
| `mqs/wiki_compile_jobs.go` DC Jobs 通道 | `mq/WikiCompileJobRelay` + `mq/WikiCompileJobsClient` | **NEW** | 仓库无任何先例 |
| `config/config.go` + struct tag 默认值 | `config/KnowledgeProperties` | **ADAPT** | go-zero 的 `default=`/`optional` 语义要重述为 `@ConfigurationProperties` 默认值 |
| `rest.RestConf` 内嵌默认值 | `config/KnowledgeProperties` | **ADAPT** | `Timeout`/`MaxBytes`/`CpuThreshold`/`Mode` 均带不可见默认值 |
| `internal/subjectrefturn/keys.go` | `service/SubjectRefTurnKeys` | **NEW** | 被 v2 读路径真实使用 |
| `cmd/subjectref-preflight/*` | **不迁移** | **—** | 离线工具，非服务；见 12.5.6 |

### 12.4.2 HTTP 契约面

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `routes.go` 5 组路由 | `controller/` 5 个 Controller | **SPLIT** | 按认证边界拆（对齐 RAG「一 JWT key 一 Controller」） |
| public 与 admin 同前缀 | `KnowledgePublicController` + `KnowledgeAdminController` 共用 `@RequestMapping("/v1/knowledge")` | **SPLIT** | 5 组路径互不相交，Spring 按 (pattern, method) 解析无歧义 |
| `WithJwt(Auth.AccessSecret)` / `WithJwt(UserAuth.AccessSecret)` | `interceptor/KnowledgeJwtAuthInterceptor` | **ADAPT** | 同一前缀两套 secret ⇒ 必须按 handler 解析适用 secret，不能纯按路径注册 |
| `AdministratorMiddleware` 白名单 | `interceptor/AdministratorAllowlistInterceptor` | **NEW** | 这是**授权**而非认证，RAG 无对应组件 |
| `WorkerMiddleware` 常数时间 token | `interceptor/WorkerTokenInterceptor` | **NEW** | 非 JWT，RAG 无对应组件 |
| `response.go` 错误映射 | `advice/KnowledgeExceptionHandler` | **ADAPT** | 必须逐条复刻 11 条映射（含 499/410） |
| 成功响应信封由 logic 构造 | **不建 ResponseBodyAdvice** | **—** | 复用 RAG 的 `GlobalResponseAdvice` 会**双层包裹** |
| `types.go` 180 个 DTO | `dto/` | **SPLIT** | Req / Path / Resp / Envelope 四重结构需保留 |
| `form:"limit,default=12"` | `dto/` tag + `GoFormValues` | **REUSE** | `article-api/form/GoFormValues`、`hot-api/form/GoFormValues` 已有 |
| 三个 guard 的严格 JSON | `config/KnowledgeStrictJsonModule` | **NEW** | 见 12.5.3，RAG 缺重复键检测 |
| `httpx.Parse` 空 body 语义 | `GoStyleRequest.normalize` | **REUSE** | `users-common/http/GoStyleRequest` |
| `middleware.Actor(ctx["userId"])` | `service/KnowledgeActorResolver` | **NEW** | 审计 actor 提取，且需接受 `string` 与 `json.Number` |

### 12.4.3 领域与持久化（占代码量的绝大部分）

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `model/schema.sql` 31 表 + 迁移脚本 4 表 | `resources/db/migration/V*.sql` | **ADAPT** | 改为 Flyway；必须保留全部 CHECK / 触发器 / 部分索引 |
| `model/*` 实体 | `domain/*Entity` | **ADAPT** | 主键 `prefix_UUID` ⇒ **`IdType.INPUT`**，偏离 RAG 的 `ASSIGN_ID` |
| jsonb 读写（18 列） | `mapper/KnowledgeJsonbTypeHandler` | **NEW** | 必须 `setObject(..., Types.OTHER)`；RAG 已有同款但**不可跨模块直接复用**（`JsonbMapTypeHandler` 是 message 私有） |
| `text` 影子列（`pack_json`/`turn_json`/`event_json`…） | 保持 `String` 字段，**不得**用 jsonb 映射 | **ADAPT** | 字节保真是完整性模型的一部分 |
| `command()` 幂等 + advisory lock + GUC | `service/KnowledgeCommandExecutor` | **NEW** | L3 核心；模板方法，与 `AbstractMessageApiService` 同构思想但不同职责 |
| `emit()` 事件发射 | `service/KnowledgeEventPublisher` | **NEW** | `operation_id` 走 **PG 会话 GUC**，非绑定参数 |
| 13 组状态机 | `service/` 下按聚合分 15 个 Domain Service | **SPLIT** | 对齐 RAG 的「一聚合一 `<Xxx>DomainService`」 |
| 多语句事务边界 | `service/KnowledgeAtomicService`（`@Transactional`） | **NEW** | RAG 同款模式；同类 `this.` 调用会绕过代理导致事务降级 |
| 行锁 / `SKIP LOCKED` / `FOR SHARE` | Mapper 内**显式写 SQL** | **ADAPT** | **不可**用 MyBatis-Plus Wrapper 表达 |
| `listRecords` keyset 分页 | `mapper/KeysetPagination` + `KnowledgePageCursor` | **NEW** | 游标是 base64url JSON，`upper` 冻结集合；与 `GoStylePagination` 的 OFFSET 语义**完全不同** |
| `ListModules`（无 SQL LIMIT） | `KnowledgeModuleMapper` 手写 SQL | **ADAPT** | 不能套用任何分页拦截器 |
| 4 个时间源（app clock / `now()` / `clock_timestamp()` / 客户端字符串） | Mapper SQL 内**逐处保留**原函数 | **ADAPT** | `now()` 与 `clock_timestamp()` 是语义差异，不是风格 |
| sentinel + reasonError + 分类 | `exception/KnowledgeExceptions` + `KnowledgeReasonError` + `KnowledgeErrorClassifier` | **NEW** | 11 类 coded error；`ClassifyError` 驱动遥测 outcome/level |
| `store.Migrate`（可选内嵌 DDL） | Flyway；保留 `KnowledgeSchemaGate` 兜底 | **ADAPT** | Go 默认 `Migrate=false`，schema 必须预置 |

### 12.4.4 外部交互

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `UserRpc identity.UserReader` | `client/UserIdentityClient` | **ADAPT** | 复用 `@GrpcClient` + 既有用户 proto；v1/v2 两个解析入口都要留 |
| BTW summary/tools HTTP | `client/SearchSummaryClient` `SearchToolsClient` | **NEW** | 仓库无 HTTP 下游先例；签名/超时/钳制/严格解析全须复刻 |
| `signSearchScope*` HMAC 签名 | `client/SearchScopeSigner` | **NEW** | 字段顺序本身是契约 |
| v2 投影（丢弃 `tenant_id`） | `service/ProductV2Projector` | **NEW** | 只读投影，不是重写 |
| `verifiedToolSearchEnvelope`（每次读都重验） | `service/ToolSearchService` | **NEW** | 「已 complete 的结果也不得免验」 |

---

## 12.5 第五部分：理论 Java 文件树

### 12.5.1 结构性决策：单模块（对既有 api/rpc 约定的**最小范围偏离**）

**理由（按权重排序）**

1. **Go 侧不存在 gRPC 边界。** 拆 api/rpc 必须新增一条 Go 没有的 wire 契约——那是**新增 L1**，不是迁移。
2. **RAG 的硬结构事实是「api 无 mapper」。** knowledge 的 HTTP 层直连 35 张表；把 mapper 放进 api 模块会打破该事实。
3. **合并成一个进程反而保留了 Go 的真实部署形态。**

**包名全部取自 RAG 已有词汇**，未发明新目录：`controller` `dto` `advice` `interceptor` `client` `config` `metrics` `bootstrap` `domain` `mapper` `service` `exception` `mq` `object`。
其中 `mq/` 在 RAG 中位于 `rpc/` 下（`like-rpc/mq/`），此处提升到模块根；`object/` 在 `article-rpc` 中以 `ArticleObjectStorageClient` 形式存在于 `client/`，本 Component 因有 `local`/`s3` 双实现与端口抽象而独立成包。

**不创建**：`service/impl/`（无 gRPC）、`*-proto` 模块（无 proto）、`*-common` 之外的第三个共享模块。

```
com.rag.knowledge.knowledge/
├── KnowledgeApplication.java                 @SpringBootApplication + @EnableScheduling
│                                            + @MapperScan("...knowledge.mapper")
│                                            + scanBasePackages={本包, com.rag.common}
│
├── advice/
│   └── KnowledgeExceptionHandler.java       @RestControllerAdvice，复刻 response.go 的 11 条映射
│
├── interceptor/
│   ├── KnowledgeJwtAuthInterceptor.java     按 handler 解析适用 secret（admin/user）
│   ├── AdministratorAllowlistInterceptor.java 白名单授权 → 403
│   └── WorkerTokenInterceptor.java          常数时间 Bearer 比对 → 401（空配置 fail-closed）
│
├── controller/
│   ├── KnowledgePublicController.java       GET ×6   @RequestMapping("/v1/knowledge")
│   ├── KnowledgeAdminController.java        ×33（含 PUT/POST/GET）同上前缀
│   ├── ProductKnowledgeController.java      ×10      /v1/knowledge/answer-sessions/**
│   ├── ProductKnowledgeV2Controller.java   ×3       /v2/knowledge/answer-sessions/**
│   └── WorkerKnowledgeController.java       ×21      /internal/v1/knowledge/**
│
├── dto/                                     见 12.5.2
│
├── client/
│   ├── UserIdentityClient.java              @GrpcClient → user-rpc；v1/v2 两个解析入口
│   ├── SearchSummaryClient.java             POST /v1/search/summary
│   ├── SearchToolsClient.java               POST /v1/search/tools/search
│   └── SearchScopeSigner.java               HMAC-SHA256 scope 签名（v1/v2）
│
├── config/
│   ├── KnowledgeProperties.java             @ConfigurationProperties，复述 go-zero tag 默认值
│   ├── WebMvcConfig.java                    三个拦截器按路径组注册
│   ├── KnowledgeGoStyleCoercion.java        Jackson 3 强制类型规则
│   ├── KnowledgeStrictJsonModule.java       重复键 + 尾随 token 检测（三个 guard 的合并）
│   └── KnowledgePerEndpointBodyLimits.java  768KiB / 32KiB / 8192B 的按端点上限
│
├── bootstrap/
│   ├── KnowledgeStartupValidator.java       约 20 条跨字段 fail-fast 校验
│   ├── KnowledgeSchemaGate.java             9 个按开关门控的 schema 探针
│   └── KnowledgeReadinessProbe.java         自探 /metrics + X-Knowledge-Instance-ID
│
├── domain/                                  见 12.5.2
├── mapper/                                  见 12.5.2
├── service/                                 见 12.5.2
├── exception/
│   ├── KnowledgeExceptions.java             Db/Select/Update/Rollback 嵌套异常
│   ├── KnowledgeReasonError.java            11 类 coded error（IDEMPOTENCY_CONFLICT…）
│   └── KnowledgeErrorClassifier.java        sentinel → (code, outcome, level)
│
├── metrics/
│   ├── KnowledgeMetrics.java                operations/commits/duration/in-flight
│   ├── KnowledgeOperations.java             45 个 operation 名注册表
│   └── KnowledgeHttpMetrics.java            route 模板 + method + status_class
│
├── mq/
│   ├── KnowledgeOutboxRelay.java            @Scheduled(fixedRateString) H04 通道
│   ├── KnowledgeOutboxSender.java           HTTPSender + 收据校验
│   ├── WikiCompileJobRelay.java             @Scheduled DC Jobs 通道
│   └── WikiCompileJobsClient.java           Submit/Get/Cancel + 状态→错误映射
│
└── object/
    ├── ObjectStore.java                     端口：Put/Get（内容寻址 + 读校验）
    ├── LocalObjectStore.java                0700 目录 + 临时文件 + fsync + 原子 link
    └── S3ObjectStore.java                   MinIO/S3 + BucketExists 探针
```

### 12.5.2 需要按聚合展开的部分（清单，非逐文件树）

> 这些包内文件数由**聚合与用例**决定，不由 Go 文件数决定。下表给出推导依据与规模量级。

| 包 | 推导依据 | 量级 | 关键边界 |
|---|---|---|---|
| `dto/` | `types.go` 的四重结构（`Req`/`Path`/`Resp`/`Envelope`），73 条路由各有请求与响应 | ~90 | `XxxReq`/`XxxResp` 带 `XxxItem`；`XxxPath` 的 path 段合入 Controller 方法签名；**`XxxEnvelope` 的 code/msg 由 service 填，不由 advice 填** |
| `domain/` | 35 张表。**不是 35 个 Entity**：只读投影可用窄 row 类，jsonb 载荷另立 POJO | ~50 | 载荷 POJO 必须能被**严格** Jackson 绑定；`list_order` 字段只读不插 |
| `mapper/` | 按聚合而非按表；`article-rpc/ArticlePublicMapper`（读写分离接口）是先例 | ~22 | jsonb 手写 `::jsonb` 显式转换；认领/锁/游标 SQL 全部显式；**零 XML** |
| `service/` | 13 组状态机 + 2 条投递 + 3 个外部客户端 | ~20 | 见下表 |

`service/` 明细（每个都有明确状态机或用例依据）：

| 类 | 承载的状态机/用例 | 来源 |
|---|---|---|
| `KnowledgeCommandExecutor` | 幂等 + advisory lock + operation GUC | `store.go:128-179` |
| `KnowledgeEventPublisher` | 事务内 outbox 写入 + 版本分配 | `store.go:197-241` |
| `KnowledgeModuleService` | 模块生命周期 + published/draft 读 | `store.go` / `revisions.go:174-200` |
| `KnowledgeRevisionService` | 不可变修订 + head 指针 | `revisions.go:66-113` |
| `KnowledgeReleaseService` | 发布指针 CAS + 激活 | `releases.go:199-244` |
| `KnowledgeBuildService` | Build 状态机 + fence/lease epoch | `builds.go` |
| `KnowledgeCompileService` | Compile 状态机 + 取代链 | `compiles.go` |
| `KnowledgeWikiQualityService` | 事实判定（assessment×grade 三向耦合） | `wiki_quality_judgments.go` |
| `KnowledgeWikiFactSetService` | FactSet 冻结 + scope 推导 | `wiki_fact_sets.go` / `wiki_fact_set_scope.go` |
| `KnowledgeSearchJudgmentService` | 判定/撤回 + 事件冻结 | `search_judgments.go` |
| `GroundingReviewService` | reviewer key + 单赢 advisory lock | `grounding_reviews.go` / `reviewer_keys.go` |
| `AcceptedAnswerService` | ordinal 分配 + 幂等重放 | `accepted_answers.go` |
| `SearchCitationService` | 引用包验收 + 字节比对 | `citations.go` |
| `ProductSearchService` | 预占/抢占/完成/失败 + BTW 对账 | `product_search_operations.go` |
| `ToolSearchService` | 预算计数器 + 租约 + 读证据 | `tool_operations.go` / `tool_evidence.go` |
| `ProductV2Projector` | v1→v2 只读投影 | `product_v2/projection.go` |
| `KnowledgeAtomicService` | 多语句事务边界（`@Transactional`） | 全域 |
| `KnowledgeApiServiceSupport` | actor 提取、身份解析、DTO 装配 | `middleware.Actor` + 各 logic 样板 |
| `KnowledgeActorResolver` | `ctx["userId"]` 的 `string`/`json.Number` 双形态 | `middleware.go:23-28` |
| `SubjectRefTurnKeys` | turn key 派生（严模式） | `internal/subjectrefturn/keys.go` |

### 12.5.3 三个 guard 在 Java 的落法（RAG 无等价物）

| Go 行为 | Java 必须具备 |
|---|---|
| 拒绝**重复** JSON 键 | `JsonParser.Feature.STRICT_DUPLICATE_DETECTION` |
| 拒绝**未知**键 | `FAIL_ON_UNKNOWN_PROPERTIES=true` |
| 拒绝**尾随** token | `FAIL_ON_TRAILING_TOKENS=true` |
| 逐字段 JSON **字面类型** | 由 DTO setter 显式声明类型，Jackson 强制 |
| 数组元素**上限** | service 内显式计数校验 |
| 按端点**体积上限** | `GoStyleMaxBytesFilter` 只给全局值 ⇒ 需 `KnowledgePerEndpointBodyLimits` |

### 12.5.4 🚩 `list_order`：最容易在迁移中踩空的点

`knowledge_{revisions,releases,builds,compiles}.list_order` 是
`ALTER TABLE … ADD COLUMN IF NOT EXISTS … GENERATED ALWAYS AS IDENTITY`（`schema.sql:125,127,129,131`）——
**非主键的 IDENTITY 列**，Go 侧从不写入、也不在实体结构里。

⇒ 它同时是 **keyset 分页的排序键**（`ORDER BY list_order DESC`）和 **分页游标的上界冻结依据**。

约束：

- 🚩 **MyBatis-Plus 的 `@TableId` 无法表达它**（不是主键）；若误设为 `IdType.AUTO` 会与实际主键冲突。
- 🚩 所有 `@Insert` **必须省略该列**，让数据库赋值；漏写则 `NOT NULL` 违约。
- 🚩 它**不能**被当作普通业务字段做更新。

### 12.5.5 🚩 偏离 RAG 既有约定的三处（已决，非疏漏）

| # | 偏离 | RAG 惯例 | knowledge 必须如此 | 理由 |
|---|---|---|---|---|
| 1 | 主键策略 | `@TableId(type = IdType.ASSIGN_ID)`（snowflake） | `IdType.INPUT` + `KnowledgeIds` 生成 `prefix_UUID` | Go 侧主键全是 `id(prefix) = prefix + "_" + uuid.NewString()`（`store.go:51`），**全表无 bigint 代理键** |
| 2 | 成功响应 advice | 每个 api 模块都有 `GlobalResponseAdvice` 包装 | **不建** | Go 成功体已是 logic 构造的 envelope，复用会双层包裹 |
| 3 | 分页 | `GoStylePagination`（OFFSET） | `KeysetPagination`（游标 + `upper` 冻结） | 两种语义完全不同：Go 的游标是 base64url JSON，冻结的是**集合成员**而非状态 |

### 12.5.6 🚩 `cmd/subjectref-preflight` 不迁移

| 判据 | 证据 |
|---|---|
| 无端口、无 HTTP、无配置 | `main.go` 全文 |
| 契约是**进程退出码**（阻塞时 exit 3） | `main.go:64-66` |
| `READ ONLY + REPEATABLE READ` 并自检 | `preflight.go:79-87` |
| 输出**只有键的哈希**，无 UID/DSN/正文 | `preflight.go:17-33,59-70` |
| 报告用 `O_EXCL` 写、拒绝覆盖 | `main.go:70-84` |

**唯一属于服务迁移的是 `internal/subjectrefturn/keys.go`** —— 它被 api 侧 `product_history_v2.go:135-136` 以更严模式调用。

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `handler/routes.go` | `controller/` 5 个 Controller | **SPLIT** | 73 条路由按 5 个认证边界拆分 |
| `handler/response.go` | `advice/KnowledgeExceptionHandler` | **ADAPT** | 11 条映射逐条复刻；含 499/410 |
| （无对应） | —— | **不建** `GlobalResponseAdvice` | 成功信封由 service 构造，advice 再包即双层 |
| `handler/knowledge/*`（38） | `controller/KnowledgeAdminController` + `KnowledgePublicController` | **MERGE** | goctl 一文件一端点；Java 侧按认证边界合并，职责同族 |
| `handler/knowledge/*_guard.go`（3） | `config/KnowledgeStrictJsonModule` + `KnowledgePerEndpointBodyLimits` | **MERGE** | 三份守卫是同一机制的三个体积档，合并后只有一份定义 |
| `handler/worker/*`（21） | `controller/WorkerKnowledgeController` | **MERGE** | 同上 |
| `handler/product/*`（10）+ `tool_request.go` | `controller/ProductKnowledgeController` | **MERGE** | |
| `handler/product_v2/*`（3） | `controller/ProductKnowledgeV2Controller` | **MERGE** | |
| `logic/knowledge/*`（39） | `service/` 15 个 Domain Service | **SPLIT** | Go 是脚手架；语义来自 `model/`，按聚合重新归位 |
| `logic/worker/*`（19） | 同上 | **SPLIT** | worker 端点复用同一批领域服务 |
| `logic/product/*`（14） | `ProductSearchService` `ToolSearchService` `SearchScopeSigner` `ProductApiSupport` | **SPLIT** | 唯一有真实编排的一层，按职责分到 4 个承载位置 |
| `logic/product_v2/*`（4） | `ProductV2Projector` | **MERGE** | 23 行纯投影 |
| `model/store.go` | `KnowledgeCommandExecutor` + `KnowledgeEventPublisher` + `KnowledgeModuleService` | **SPLIT** | 一个 Go 文件混了幂等引擎、事件发射、模块读写三类职责 |
| `model/schema.sql` + `scripts/migrate-*.sql` | `resources/db/migration/V1..V9` | **ADAPT** | 改为 Flyway；保留全部 CHECK/触发器/部分索引 |
| `model/outbox.go` | `mq/KnowledgeOutboxRelay` + `mapper` 中的认领 SQL | **SPLIT** | 轮询调度与 SQL 认领分离 |
| `model/observability.go`（`observe`/`ClassifyError`） | `KnowledgeErrorClassifier` + 各 Domain Service 的 `observe` 包装 | **SPLIT** | 错误分类是横切职责 |
| `model/pagination.go` | `mapper/KeysetPagination` + `KnowledgePageCursor` | **NEW** | RAG 的 `GoStylePagination` 是 OFFSET 语义，不可复用 |
| `model`（jsonb 读写） | `mapper/KnowledgeJsonbTypeHandler` | **NEW** | 必须 `setObject(..., Types.OTHER)`；跨模块不复用 message 私有实现 |
| `middleware/administrator_middleware.go` | `interceptor/AdministratorAllowlistInterceptor` | **ADAPT** | 授权而非认证，RAG 无对应件 |
| `middleware/worker_middleware.go` | `interceptor/WorkerTokenInterceptor` | **ADAPT** | 非 JWT，RAG 无对应件 |
| `rest.WithJwt`（两套 secret） | `interceptor/KnowledgeJwtAuthInterceptor` | **ADAPT** | 同前缀两套 secret ⇒ 按 handler 解析 |
| `svc/service_context.go` 依赖持有 | Spring 容器 | **ABSORB** | 纯依赖图 |
| `svc/service_context.go` 启动校验（~20 条） | `bootstrap/KnowledgeStartupValidator` | **NEW** | DI 吸收不了 |
| `knowledge.go` 就绪自探 | `bootstrap/KnowledgeReadinessProbe` | **NEW** | |
| `knowledge.go` 关停顺序 | `@PreDestroy` | **ADAPT** | |
| `mqs/outbox.go` | `mq/KnowledgeOutboxRelay` + `KnowledgeOutboxSender` | **ADAPT** | 结构同 `LikeHotEventOutboxRelay` |
| `mqs/wiki_compile_jobs.go` | `mq/WikiCompileJobRelay` + `WikiCompileJobsClient` | **NEW** | 仓库无先例 |
| `telemetry/http.go` | `com.rag.common` 的 `HttpMiddlewareFilter` | **REUSE** | 已有全局 Filter |
| `telemetry/operation.go` | `metrics/KnowledgeOperations` | **NEW** | 45 个领域 operation 名 |
| `telemetry/log.go` | —— | **UNKNOWN** | RAG 无日志队列组件；见 12.7 |
| `object/store.go` | `object/` 三件 | **NEW** | 内容寻址 + 读校验，仓库无等价 |
| `types/types.go` | `dto/`（Req/Path/Resp/Envelope） | **SPLIT** | `Path` 合入 Controller 签名，其余独立 |
| `config/config.go` | `config/KnowledgeProperties` | **ADAPT** | go-zero tag 默认值重述 |
| `internal/subjectrefturn/keys.go` | `service/SubjectRefTurnKeys` | **NEW** | 被 api 真实调用 |
| `cmd/subjectref-preflight/*` | 不迁移 | **—** | 离线工具，见 12.5.6 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### L1 外部契约

| 检查项 | 结果 | 说明 |
|---|---|---|
| 73 条路由 + 5 个认证边界全覆盖 | ✅ | 12.3.1 逐条列出，5 组路径互不相交已核对 |
| 成功信封 `{code,msg,data}` | ✅ | 由 service 构造；**明确不建 advice** |
| 错误信封 `code == HTTP status` | ✅ | 11 条映射逐条复刻，含 499/410/504 |
| JWT claim 与 secret 双轨 | ⚠️ | 已设计按 handler 解析；**装配层需实测** |
| cursor 分页的 `next_cursor` 语义 | ✅ | 游标冻结集合成员，非状态 |
| `form` 默认值（12/20/50 各异） | ✅ | 落在 DTO tag，走 `GoFormValues` |

### L2 业务语义

| 检查项 | 结果 |
|---|---|
| 13 组状态机（转移/前置/非法态处理） | ✅ 12.3.8 |
| 三种并发机制不可互换（行锁 / advisory lock / 两套 fence） | ✅ build 用算术 epoch，compile 用严格单调，product/tool 用不透明 token |
| 「提交时校验」而非「先校验再写」 | ✅ 已写入服务边界要求 |
| 两种"不可用"语义（410 撤回 / 503 损坏） | ✅ 不合并 |
| feature gate 的**两种**失败形态（logic 层 503+`err==nil`；store 层 `ErrUnavailable`） | ✅ |
| 对象存储写入不在事务内 ⇒ 孤儿对象有意接受 | ✅ 不加补偿删除 |

### L3 技术语义

| 检查项 | 结果 |
|---|---|
| 事务内三写原子（领域 + outbox + 幂等） | ✅ `KnowledgeCommandExecutor` |
| `operation_id` 走 PG 会话 GUC | ✅ 非绑定参数，已在映射表标注 |
| `FOR UPDATE SKIP LOCKED` + 持锁发送 | ✅ 认领 SQL 逐条照搬 |
| 无死信 / 无重试上限 / 无退避 | ✅ 显式声明**不新增** |
| 锁序：模块行永远第一个 | ✅ |
| jsonb **不可字节往返** ⇒ text 影子列必需 | ✅ 12.4.3 |
| 严格 JSON（重复键/未知键/尾随） | ✅ 12.5.3 |
| 四个时间源并存（`now()` vs `clock_timestamp()`） | ✅ |
| jsonb 写入必须 `Types.OTHER` | ✅ |
| `list_order` 非主键 IDENTITY | ✅ 12.5.4 |

### 未通过 / 需进一步确认的项（不做"整体符合"的空话）

| # | 问题 | 影响 | 处置 |
|---|---|---|---|
| 1 | **RAG 无日志队列组件。** Go `telemetry/log.go` 的「有界队列 + 溢出即丢 + 丢弃计数 + flush 屏障 + 请求转储剥离」在仓库中无对应实现 | 高 | 标 **UNKNOWN**。二选一：新建模块内组件，或签字接受降级。**不得静默丢弃** |
| 2 | **`/metrics` 的 `X-Knowledge-Instance-ID` 契约**与 `KnowledgeReadinessProbe` 的自探构成闭环；RAG 的 actuator 端点是否可注入自定义响应头**未验证** | 中 | 需装配层实测。RAG 用 actuator + micrometer，与 Go 的 `promhttp.HandlerFor` + 私有 registry 不同 |
| 3 | **45 个 operation 名**的静态注册表无 RAG 对应件 | 中 | 新建，但需与既有 `Observability` 的命名空间对齐 |
| 4 | **无验证注解。** 全仓 `@Valid/@NotNull` 出现 0 次 | 中 | knowledge 沿用手写 `GoStyleRequiredFields`；但三个 guard 的**流式字面量校验**超出该机制能力，需上面的严格 JSON 模块 |
| 5 | **Boot 4 的 `MybatisPlusOrderingBridgeAutoConfiguration`** 必须随新模块一并复制，否则 MP 自动配置静默不匹配 | 中 | 已在树中列为 `config/` 项 |
| 6 | **Flyway 账本表名**需 `spring.flyway.table: flyway_schema_history_knowledge` | 中 | 沿用 §6.3 问题 4 的既定方案 |
| 7 | **Spring profile 必须映射到 go-zero 的 `Mode`**（`dev\|test\|rt\|pre\|pro`） | 中 | `Mode` 驱动**四条**启动闸门；profile 命名不对会静默破坏其中三条 |
| 8 | **`spring-boot-flyway` 依赖**在 Boot 4 下必须显式声明，缺失时迁移静默不执行 | 中 | 构建期检查项 |
| 9 | **同一前缀两套 JWT secret 的装配**未实测 | 中 | 需仿 `MessageApiRouteAuthBoundaryTest` 写**路由鉴权边界**测试 |
| 10 | 单进程同时承载 HTTP + 调度 + 直连 DB，**全仓无先例** | 中 | 本文已给出最小范围偏离方案；落地前应由用户确认 §1 范围决策 |

### 组织合理性

| 检查项 | 结果 |
|---|---|
| 无「仅为对应 Go 文件」而建的 Java 文件 | ✅ 每个新文件都有状态机/机制/外部契约依据 |
| 无「为减少文件数」的不合理合并 | ✅ 38 个 handler 合为 4 个 Controller 是**认证边界**划分，不是凑数 |
| 拆分边界可解释 | ✅ 按聚合与认证边界，不按 Go 目录 |
| 复用对象均已指明 | ✅ 见 12.1 与 12.4 |
| 框架吸收均有真实机制 | ✅ DI / 容器生命周期 / `@Scheduled` / Flyway；**`command()`、行锁、GUC 均未被吸收** |
| 未实现职责未被伪装成框架吸收 | ✅ 5 处 `UNKNOWN`/`ADAPT` 显式标注 |

---

## 附：必须配套的守门测试（推导要求，非本次产物）

按 `AGENTS.md` §3.3「注解存在 ≠ 机制生效」，以下每条都需**真 PG / 真容器**证据：

| 测试 | 守护的不变量 |
|---|---|
| `KnowledgeRouteAuthBoundaryTest` | 5 组路由的认证边界（仿 `MessageApiRouteAuthBoundaryTest`） |
| `KnowledgeCommandIdempotencyTest` | advisory lock + 重放 + `IDEMPOTENCY_CONFLICT` |
| `KnowledgeOutboxRelayTest` | `SKIP LOCKED` 认领、持锁发送、收据不匹配则不盖章 |
| `KnowledgeLeaseFenceTest` | 提交时校验；租约过期回滚同事务内的 revision/head/outbox |
| `KnowledgeJsonbByteFidelityTest` | jsonb 与 text 影子列的分工；严格 JSON 拒绝重复/未知/尾随 |
| `KnowledgeKeysetPaginationTest` | `list_order` 游标 + `upper` 冻结；与 OFFSET 语义区分 |
| `KnowledgeStartupValidatorTest` | 跨字段校验与 `Mode` 闸门 |
| `KnowledgeReadinessProbeWiringTest` | 自探真的打到自己的监听端口并校验实例头 |