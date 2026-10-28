# Infrastructure — Go 版基线 vs Java 版（本项目）实际使用

> 一句话：Java 版基础设施 1:1 对齐 Go 版（按 `AGENTS.md §4.1`）；唯一硬决策是**服务注册中心 etcd → Nacos**；端口全部重映射到 49xxx/18xxx/19xxx 避开常用端口。

本文档对照 Go 版（基线 / "ground truth"）与 Java 版（本项目）实际使用的两套基础设施，说明"用了什么 / 怎么用 / 哪里差异"。所有判断以 `docker-compose.yaml` / `application.yml` / `.env` / `manage.sh` 为唯一依据，不参考设计文档的描述差异。

ps：`这是理论上的java基础设施组成，尚未具体配置`

> 🔴 **2026-10-29 现状订正（R1·T-1.1 · P0 #5）**
> 本文件此前描述的是 **2026-10-09** 的状态（当时只有 users 域、其余 8 个域是 pom 骨架）
> ⇒ 现已 **10 个域 / 38 个组件全部实装** ⇒ ⇒ **§3 / §4 / §6 / §7 已按实测现状重写**。
> ⚠️ 旧版 §6 的变量表（`SYSTEM_DEFAULT_PASSWORD` / `USER_ADMIN_DEFAULT_BOOTSTRAP_ENABLED` /
> `NEO4J_AUTH_*` / `GRAFANA_ROOT_URL` …）是**旧 Go 栈**的变量名，RAG 侧**没有** ⇒ 已整表替换。
> ⚠️ **本文件在仓库根**（`Infrastructure.md`），**不在** `docs/FileTree/` 下
> ⇒ `docs/FileTree/Infrastructure.md` **这个路径从来不存在**（P0 #5 台账里的路径写错了）。
> 📖 权威的当前状态 ⇒ [`docs/PhaseGoal-judge.md`](docs/PhaseGoal-judge.md) §二/§三/§八；
> 📖 怎么起 ⇒ [`README.md`](README.md)。
---

## 1. 基础设施对照总表

| 组件 | Go 版（基线） | Java 版（编排） | Java 版进程实际接入 | 差异点 |
|---|---|---|---|---|
| **服务发现 / 配置中心** | `etcd` v3.5.25 :2379 | `nacos` v2.3.2-slim :49848 (HTTP) / :50848 (gRPC) | user-rpc / admin-rpc / api-gateway 全部 Nacos discovery + config | ❗ **唯一硬替换**：etcd → Nacos（AGENTS.md §4.1） |
| **服务发现（保留）** | （etcd 唯一） | `etcd` v3.5.25 :49319 | Milvus 元数据仍依赖 etcd | Java 版同时保留 etcd，供 Milvus 用 |
| **关系数据库** | `postgres:latest` :5432 | `postgres:latest` :49432 → 5432 | user-rpc / admin-rpc 用 `${POSTGRES_ADDR:localhost}:49432/sea_user` | Host 端口重映射 |
| **缓存** | `redis:latest` :6379 (`--appendonly yes`) | `redis:latest` :49380 → 6379 | user-rpc / admin-rpc 用 Spring Data Redis (Lettuce) | Host 端口重映射 |
| **异步队列** | `beanstalkd` ×2 :11300 | `beanstalkd` ×2 :49300 / :49301 → 11300 | **当前未实装**：Go 版哪个域在用也未标注 | 镜像沿用 |
| **图数据库** | `neo4j:latest` :7474/7687 | `neo4j:latest` :49474/49687/49004 | **当前未实装**（knowledge 域未翻译） | 镜像沿用 |
| **对象存储** | `minio/minio:RELEASE.2024-12-18T13-15-44Z` :9000/9001 | `minio/minio:latest` :49100/49101 → 9000/9001 | **当前未实装**（article 域未翻译；user 域确认不接 MinIO） | 镜像沿用，版本号松绑到 latest |
| **向量数据库** | `milvusdb/milvus:v2.6.8` :19530 | `milvusdb/milvus:v2.6.8` :49530 / :49091 | **当前未实装**（knowledge 域未翻译） | 镜像版本号一致；仍依赖 etcd + MinIO |
| **消息流** | `apache/kafka:latest` :9092/9093 (KRaft) | `apache/kafka:latest` :49992 → 9093 (KRaft) | **当前未实装**（hot / points 域未翻译） | Host 端口重映射；**客户端层** Java 用 `spring-kafka` 替换 Go 端 `sarama`，**Kafka 服务端不动**（详见 §5.6）|
| **Prometheus** | `prom/prometheus:latest` :9090 | `prom/prometheus:latest` :49090 → 9090 | user-rpc / admin-rpc / api-gateway 暴露 `/actuator/prometheus` | Host 端口重映射 |
| **Grafana** | `grafana/grafana:latest` :3000 | `grafana/grafana:latest` :49000 → 3000 | 通过 `prometheus` 数据源连入 | Host 端口重映射 |
| **Jaeger** | `jaegertracing/all-in-one:latest` :16686 + OTLP 4317/4318 | 同镜像 :49686 / 49831(UDP) / 49832(UDP) / 49268 / 49250 / 49317(OTLP gRPC) / 49318(OTLP HTTP) | user-rpc / admin-rpc / api-gateway 用 OTLP gRPC :49317 | 完全沿用 |
| **node-exporter** | `prom/node-exporter:latest` :9100 | — | ❗ **Java 版编排未包含** | 与 Go 版差异（待补） |
| **cAdvisor** | `gcr.io/cadvisor/cadvisor:latest` :8080 | — | ❗ **Java 版编排未包含** | 与 Go 版差异（待补） |
| **Filebeat** | `elastic/filebeat:8.13.4` | — | ❗ **Java 版编排未包含** | 与 Go 版差异（待补） |
| **postgres-exporter** | `quay.io/prometheuscommunity/postgres-exporter:latest` :9187 | 同镜像 | 已编排，未配置 prometheus scrape job | 沿用 |
| **redis-exporter** | `oliver006/redis_exporter:latest` :9121 | 同镜像 | 同上 | 沿用 |
| **kafka-exporter** | `danielqsj/kafka-exporter:latest` :9308 | 同镜像 | 同上 | 沿用 |

---

## 2. 端口映射速查

**为什么重映射**：Java 版编排把所有 host 端口都搬到 49xxx / 18xxx / 19xxx 段，避开 `3000`、`5432`、`6379`、`9000` 等开发常用端口，避免与本机已装服务冲突。

| 用途 | 容器内端口 | Java 版 host 端口 | Go 版 host 端口 |
|---|---|---|---|
| **PostgreSQL** | 5432 | **49432** | 5432 |
| **Redis** | 6379 | **49380** | 6379 |
| **Beanstalkd1** | 11300 | **49300** | 11300 |
| **Beanstalkd2** | 11300 | **49301** | 11300 |
| **Neo4j HTTP** | 7474 | **49474** | 7474 |
| **Neo4j Bolt** | 7687 | **49687** | 7687 |
| **Neo4j Routing** | 2004 | **49004** | 2004 |
| **Kafka EXTERNAL** | 9093 | **49992** | 9093 |
| **MinIO API** | 9000 | **49100** | 9000 |
| **MinIO Console** | 9001 | **49101** | 9001 |
| **Milvus** | 19530 | **49530** | 19530 |
| **Milvus health** | 9091 | **49091** | — |
| **Prometheus** | 9090 | **49090** | 9090 |
| **Grafana** | 3000 | **49000** | 3000 |
| **Jaeger UI** | 16686 | **49686** | 16686 |
| **Jaeger Thrift (UDP)** | 6831/6832 | **49831 / 49832** | 6831/6832 |
| **Jaeger HTTP** | — | **49268 / 49250** | — |
| **Jaeger OTLP gRPC** | 4317 | **49317** | 4317 |
| **Jaeger OTLP HTTP** | 4318 | **49318** | 4318 |
| **etcd** | 2379 | **49319** | 2379 |
| **Nacos HTTP** | 8848 | **49848** | — |
| **Nacos gRPC** | 9848 | **50848** | — |

> Spring Boot `application.yml` 用 `${POSTGRES_ADDR:localhost}:${POSTGRES_PORT:49432}` 这种语法，把这些重映射后的端口作为默认值带进容器。

---

## 3. Java 版业务进程端口分配（🔴 2026-10-29 按实测现状订正）

> **旧版这段写的是「16 个服务里只有 3 个能跑、其余 13 个是 pom 骨架」** ⇒ 那是 **2026-10-09** 的状态
> ⇒ 之后 10 个域全部实装完毕（**38 个组件**）⇒ ⇒ 本表已按 **2026-10-29 实测**重写。
> ⚠️ 端口取自各模块 `src/main/resources/application.y*ml` 的**实际**配置，不是预占号段。

| 服务 | Maven 模块 | 端口 | 协议 | 状态 |
|---|---|---|---|---|
| `user-api` | `service/users/user/user-api` | **49900** | HTTP | ✅ 实装 · **root compose 编排** |
| `user-rpc` | `service/users/user/user-rpc` | **49210** | gRPC | ✅ 实装 · **root compose 编排** |
| `admin-api` | `service/users/admin/admin-api` | **8889** | HTTP | ✅ 实装（compose 未编排） |
| `admin-rpc` | `service/users/admin/admin-rpc` | **8081** | gRPC | ✅ 实装 |
| `article-api` | `service/article/article-api` | **8889** | HTTP | ✅ 实装 |
| `article-rpc` | `service/article/article-rpc` | **9001**（⚠️ yaml **硬编码**，不可 env 覆盖） | gRPC | ✅ 实装 |
| `comment-api` | `service/comment/comment-api` | **8888** | HTTP | ✅ 实装 |
| `comment-rpc` | `service/comment/comment-rpc` | **9001** | gRPC | ✅ 实装 |
| `favorite-api` | `service/favorite/favorite-api` | **18893** | HTTP | ✅ 实装 |
| `favorite-rpc` | `service/favorite/favorite-rpc` | **8082** | gRPC | ✅ 实装 |
| `follow-api` | `service/follow/follow-api` | **8891** | HTTP | ✅ 实装 |
| `follow-rpc` | `service/follow/follow-rpc` | **8082**（`FOLLOW_RPC_PORT`） | gRPC | ✅ 实装 |
| `hot-api` | `service/hot/hot-api` | **18897** | HTTP | ✅ 实装 |
| `hot-rpc` | `service/hot/hot-rpc` | **8083** | gRPC | ✅ 实装 |
| `like-api` | `service/like/like-api` | **18887** | HTTP | ✅ 实装 |
| `like-rpc` | `service/like/like-rpc` | **8082** | gRPC | ✅ 实装 |
| `message-api` | `service/message/message-api` | **8892** | HTTP | ✅ 实装 |
| `message-rpc` | `service/message/message-rpc` | **8082** | gRPC | ✅ 实装 |
| `task-api` | `service/task/task-api` | **18886** | HTTP | ✅ 实装 |
| `task-rpc` | `service/task/task-rpc` | **9005** | gRPC | ✅ 实装 |

⚠️ **端口冲突（实测）**：`article-rpc` 与 `comment-rpc` 都用 **9001**；`favorite-rpc` / `follow-rpc` /
`like-rpc` / `message-rpc` 都用 **8082** ⇒ **同机并行必须错开**（改各自的 `*_RPC_PORT` env；
`article-rpc` 的 9001 除外 ⇒ 只能改 yaml）。
⚠️ `article-api`(8889) 与 `admin-api`(8889) 也撞 ⇒ 同机并行同理。

> 📖 端口与各域的完整对照 ⇒ [`docs/PhaseGoal-judge.md`](docs/PhaseGoal-judge.md) §二 矩阵 A-1。
> 📖 怎么起 ⇒ [`README.md`](README.md) §三。

---

## 4. Java 端实装状态（🔴 2026-10-29 按实测现状订正）

> **旧版这张表把 Kafka / MinIO / Beanstalkd / Neo4j / Milvus 全写「❌ Java 进程未直连」**
> ⇒ 那是 **2026-10-09**（只有 users 域）的状态 ⇒ 之后 10 个域实装完毕 ⇒ 下表已订正。

| 组件 | 编排 | 进程接入（基础设施客户端） | 业务实装（哪个域在用） |
|---|---|---|---|
| **PostgreSQL** | ✅ | ✅ 9 个 rpc | ✅ user / admin / article / comment / favorite / follow / like / message / task |
| **Redis** | ✅ | ✅ comment / favorite / follow / hot / like / task / user / admin | ✅ 用户域黑名单 + 多个域的缓存 |
| **Nacos** | ✅ | ✅ 全部 api + rpc | ✅ 全域（注册 + 配置；**etcd → Nacos 是唯一硬替换**） |
| **etcd** | ✅ | ❌ Java 业务进程**不直连** | ⚠️ **仅 Milvus 元数据用**（保留 Go 侧 etcd 链）· ⚠️ 真源对拍时 Go 侧要用它 |
| **Kafka** | ✅ | ✅ article / comment / favorite / hot / like / task | ✅ hot（热度流）/ like（事实投递）/ task（进度） |
| **MinIO** | ✅ | ✅ article-rpc | ✅ article（图片上传 / 下载） |
| **Beanstalkd ×2** | ✅ | ❌ | ⚠️ Go 侧 task 用的延迟队列；Java 侧 task 走 Kafka |
| **Neo4j** | ✅ | ❌ | ⚠️ **knowledge 域范围外**（不是待办） |
| **Milvus** | ✅ | ❌ | ⚠️ **knowledge 域范围外**（不是待办） |
| **Prometheus** | ✅ | ✅ scrape `user-api`（`/actuator/prometheus`） | ✅ 用户域 |
| **Grafana** | ✅ | ✅ | ✅ |
| **Jaeger (OTLP gRPC)** | ✅ | ✅ 全部 api + rpc 走 `${OTEL_ADDR}` / `${OTEL_EXPORTER_OTLP_ENDPOINT}` | ✅ 全域 |
| **postgres / redis / kafka exporter** | ✅ | ❌ prometheus scrape job 未配置 | ⚠️ |

> 注：`❌ 进程接入` = 当前没有任何 Spring Boot 进程持有该客户端。
> ⚠️ **knowledge / points / security 三个域在 Go 侧就存在、但**不在 RAG 的范围内**（AGENTS.md §一）
> ⇒ ⇒ **Neo4j / Milvus 的「未接入」不是待办**。

---

## 5. 与 Go 版的具体差异

### 5.1 唯一硬决策：etcd → Nacos（AGENTS.md §4.1）

- **Go 版**：`zrpc.MustNewServer` 自动把 `key: user.rpc` 注册到 etcd；跨域调用方 `zrpc.MustNewClient` 通过 etcd 解析地址
- **Java 版**：`spring-cloud-starter-alibaba-nacos-discovery` 把 `user-rpc` 注册到 Nacos namespace `sea-rtw`、group `USER_GROUP`；跨域客户端用 `ManagedChannelBuilder` + Nacos `NameResolver` 解析
- **Java 版同时保留 etcd 容器**：因为 Milvus 仍依赖 etcd 做元数据存储；Java 端业务进程**不直连 etcd**

### 5.2 端口重映射

- 所有 host 端口搬到 49xxx（基础设施）+ 18xxx（业务 HTTP）+ 19xxx（业务 RPC）+ 49xxx（gateway）四段
- 容器内端口保持与 Go 版一致（5432/6379/9092/9093/9000 等）
- Spring Boot 用 `${POSTGRES_ADDR:localhost}:${POSTGRES_PORT:49432}` 这种语法默认带重映射后的值

### 5.3 Java 版独有的进程拓扑

按 AGENTS.md §4.1 + §4.3：

```
┌─ api-gateway (Spring Cloud Gateway :49290)
│     路由 12 个域的 HTTP 入口 + JWT 鉴权 + Sentinel 限流
▼
┌─ user-rpc 进程（同进程双 server）
│     ┌─ Spring Web :49200 (HTTP/JSON, JsonProtoCodec ↔ protobuf Message)
│     └─ grpc-java :49210 (gRPC, 供 follow/message/admin 跨进程调用)
▼
┌─ admin-rpc 进程（同进程双 server）
│     ┌─ Spring Web :49202
│     └─ grpc-java :49211
```

Go 版是 `user/api` + `user/rpc` + `admin/api` + `admin/rpc` **四个独立进程**；Java 版把 user 的 api+rpc 合并到 user-rpc（HTTP+gRPC 同进程双端口），admin 同理。

### 5.4 镜像版本策略

| 组件 | Go 版镜像标签 | Java 版镜像标签 | 差异 |
|---|---|---|---|
| Milvus | `v2.6.8` | `v2.6.8` | 完全一致 |
| PostgreSQL | `latest` | `latest` | 完全一致 |
| Redis | `latest` | `latest` | 完全一致 |
| Kafka | `latest` | `latest` | 完全一致 |
| Neo4j | `latest` | `latest` | 完全一致 |
| Prometheus | `latest` | `latest` | 完全一致 |
| Grafana | `latest` | `latest` | 完全一致 |
| Jaeger | `latest` | `latest` | 完全一致 |
| MinIO | `RELEASE.2024-12-18T13-15-44Z` | `latest` | ⚠️ **Java 版松绑到 latest**，与 Go 版固定 tag 不一致 |
| Etcd | `v3.5.25` | `v3.5.25` | 完全一致 |
| Beanstalkd | `latest` | `latest` | 完全一致 |

### 5.5 exporters / node-exporter / cAdvisor / Filebeat

- Go 版编排里有 7 类辅助采集器；Java 版只保留 postgres-exporter / redis-exporter / kafka-exporter 三个 Prometheus exporter
- node-exporter / cAdvisor / Filebeat 三件**Java 版编排里没有**——与 Go 版差异，是否要补需要明确

### 5.6 客户端层 vs 服务层（以 Kafka 为例的边界说明）

这是本项目最容易产生误解的地方，必须显式区分**两层**：

| 层 | 含义 | 类比 |
|---|---|---|
| **服务端** | docker-compose 里跑的那个进程；客户端用 wire protocol 跟它通信 | MySQL 数据库本身 |
| **客户端库** | Spring Boot / Go 进程持有的 SDK；通过它去调服务端 | JDBC / MyBatis-Plus / GORM / sarama |

**本项目（Java 版）两层的归属**：

| 组件 | 服务端（docker-compose 里） | Go 端客户端 | Java 端客户端 |
|---|---|---|---|
| **PostgreSQL** | `postgres:latest`（不变） | GORM | MyBatis-Plus |
| **Redis** | `redis:latest`（不变） | go-redis | Spring Data Redis (Lettuce) |
| **Kafka** | `apache/kafka:latest`（不变） | sarama | **spring-kafka** |
| **Neo4j** | `neo4j:latest`（不变） | Neo4j Go driver | Spring Data Neo4j |
| **Prometheus** | `prom/prometheus:latest`（不变） | client_golang | Micrometer + Spring Boot Actuator |
| **Jaeger** | `jaegertracing/all-in-one:latest`（不变） | OpenTelemetry SDK | OpenTelemetry Java SDK |
| **Snowflake ID** | — | bwmarrin/snowflake | 自实现 `SnowflakeIdGenerator` |
| **服务发现** | `etcd` → **`nacos-server`**（**整组替换**） | go-zero `zrpc` Etcd resolver | spring-cloud-starter-alibaba-nacos |

**关键边界**：

- ⚠️ **本项目只有 `etcd → Nacos` 是"服务端 + 客户端整组替换"**——按 AGENTS.md §4.1 决策
- ⚠️ **其它都是"客户端库换皮"，服务端本身不动**——比如 Kafka 服务端仍是 `apache/kafka:latest`，换的是 Go 端 `sarama` ↔ Java 端 `spring-kafka` 这一层客户端
- ⚠️ **容器镜像按 AGENTS.md §4.2 沿用 Go 版同名同版本**：PostgreSQL / Redis / Kafka / Neo4j / Prometheus / Grafana / Jaeger / MinIO / Milvus 都沿用（MinIO 例外：Java 版松绑到 `latest`，见 §5.4）

**为什么这件事容易被混淆**：

- "Kafka" 这个词同时被用来指**服务端**（broker）和**整个生态**（客户端 + 服务端）
- "Spring Kafka" 是 Spring 风格的**客户端库**，但名字带 "Kafka" 容易被误读成"Spring 版本的 Kafka 服务"
- 实际关系：`spring-kafka` 内部依赖原生 `kafka-clients`（Apache 官方 Java 客户端），`kafka-clients` 调的是 `apache/kafka` broker 的 wire protocol
- 跟 MyBatis-Plus 替 GORM 但 MySQL 服务不变 是同一类事

**结论**：本项目里"Kafka → Spring Kafka"这种说法不准确——准确说法是 **"Kafka 服务端沿用 Go 版；Go 端 sarama 客户端 → Java 端 spring-kafka 客户端"**。

---

## 6. 关键环境变量（🔴 2026-10-29 按现状订正）

> **旧版这张表写的是 Go 版 `.env` 的变量**（`SYSTEM_DEFAULT_PASSWORD` / `USER_ADMIN_DEFAULT_BOOTSTRAP_ENABLED` /
> `GRAFANA_ROOT_URL` / `NEO4J_AUTH_*` …）⇒ 那些是**旧 Go 栈**的变量名，RAG 侧**没有**。
> ⇒ 下表按 **2026-10-29 实测的 RAG 配置面**（各模块 `application.y*ml` 的 `${VAR[:default]}` 全量扫描，**53 个变量**）重写。
> 📖 全量样例（含每个变量的默认值）⇒ [`.env.example`](.env.example)。

### 6.1 🔴 必填 —— 缺了就起不来（**这是设计意图**）

| 变量 | 类型 | 用途 | 缺值时 |
|---|---|---|---|
| `USER_JWT_SECRET` | [APP-ONLY] | 用户域 JWT 签名（**签发方** user-api/user-rpc）；article/comment/follow/like/message-api 的**验签**密钥必须与它一致 | Spring 绑定期炸 ⇒ **启动失败** |
| `ADMIN_JWT_SECRET` | [APP-ONLY] | 管理域 JWT 签名 | 同上 |
| `FAVORITE_JWT_SECRET` | [APP-ONLY] | favorite-api 的验签密钥 | 同上 |
| `POSTGRES_PASSWORD` | [BOTH] | DB 凭据 | compose `:?` ⇒ **拒绝渲染** |

### 6.2 建议覆盖的部署凭据（**默认值在仓里是公开的 ⇒ 生产必须改**）

| 变量 | 默认值 | 风险 |
|---|---|---|
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `minioadmin` | 官方公开默认值 ⇒ 谁都能连上桶 |
| `LIKE_FACT_AUTHORITY_TOKEN` / `COMMENT_FACT_AUTHORITY_TOKEN` / `FAVORITE_AUTHORITY_TOKEN` | 空 | **不校验调用方身份** |
| `DC_PLATFORM_SERVICE_TOKEN` | 空 | 事件回调**不验签** |
| `ADMIN_DEFAULT_PASSWORD` | `123456` | 默认管理员密码 |
| `REDIS_PASSWORD` | 空 | Redis 无认证 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 空 | Nacos standalone 无 auth |

### 6.3 连接 / 端口（默认值适合本地 compose）

| 变量 | 默认值 | 用途 |
|---|---|---|
| `POSTGRES_HOST` / `ADDR` / `PORT` / `USER` / `DB` | `postgres` / `postgres` / `5432` / `admin` / `first_db` | DB 连接（容器内 DNS 名） |
| `REDIS_HOST` / `ADDR` / `PORT` | `redis` / `redis` / `6379` | 缓存连接 |
| `NACOS_ADDR` / `PORT` / `NAMESPACE` / `GROUP` | `nacos` / `8848` / `public` / `DEFAULT_GROUP` | 注册中心 |
| `KAFKA_BROKERS` | `kafka:9092` | 事件总线（5 个域在用） |
| `MINIO_ENDPOINT` / `MINIO_PUBLIC_BASE_URL` | `minio:9000` / `http://localhost:39000` | 对象存储 |
| `OTEL_ADDR` / `OTEL_EXPORTER_OTLP_ENDPOINT` / `OTLP_PORT` | `jaeger` / `jaeger:4317` / `4317` | 链路追踪 |
| `*_API_PORT` / `*_RPC_PORT` | 见 §3 | 业务端口 |
| `USER_JWT_EXPIRE` / `ADMIN_JWT_EXPIRE` / `FAVORITE_JWT_EXPIRE` | `31536000` / `2592000` / `2592000` | token 有效期（秒） |
| `SNOWFLAKE_NODE_ID` | `1` | Snowflake 节点位，**集群内唯一** |
| `CONTAINER_PREFIX` / `DOCKER_NETWORK` | `Sea` / `Sea-TryGo` | 编排（`DOCKER_NETWORK` 是 **external** ⇒ 须已存在） |

---

## 7. 关键警告（与 AGENTS.md / README 一致 · 2026-10-29 订正）

- 🔴 **JWT 密钥类变量缺值 ⇒ 启动失败，这是设计意图** —— 不是「忘了配」的事故，而是**安全闸门**：
  - **没设** ⇒ Spring 的 placeholder 不可解析 ⇒ 绑定期直接炸；
  - **设成空串** ⇒ Spring 会把空串当合法值（`PropertyPlaceholderHelper`：**不走默认值、也不报错**）
    ⇒ ⇒ 由各域 `*ApiProperties#verifyAccessSecret()` 的 `@PostConstruct` 校验兜住 ⇒ 抛 ⇒ 启动失败。
  - ⚠️ **空密钥 = HMAC 空密钥 = 谁都能伪造 token** ⇒ 宁可起不来。
- 🔴 **Compose 与 Spring 的占位符语法不同，千万别混**：
  - Compose `${VAR:?msg}` = 缺值**报错退出**（✅ 我们要的）；
  - Spring `${VAR:?msg}` = 缺值取**字面量 `?msg`**（❌ 更危险）⇒ Spring 侧一律 `${VAR}`（无默认值）。
- ⚠️ **Nacos standalone 默认无 auth**：生产要开 auth 需同时改服务端与各域 client。
- ⚠️ **MinIO 镜像 Java 版松绑到 latest**：可能与 Go 版固定 tag 漂移 ⇒ 生产建议固定 tag。
- ⚠️ **`POSTGRES_PASSWORD` 必须改**：compose 用 `:?` ⇒ 不改就**起不来**。
- ⚠️ **JWT 密钥 ≥ 32 bytes**，且**签发方与验签方必须一致** ⇒ 改一处要改全部验签方。
- ⚠️ **Snowflake `node_id` 集群内唯一** ⇒ 多副本时每副本需机器唯一。
- ⚠️ **Go 版 etcd 仍保留**：因为 Milvus 元数据依赖；**Java 业务进程不直连 etcd**
  ⇒ 但**真源对拍时 Go 侧要用**（`Sea-etcd` 已映射到宿主 `49319`）。
- ⚠️ **端口撞车**：多个 rpc 默认都落在 **8082** / **9001** ⇒ 同机并行必须错开（见 §3）。
- ⚠️ **root compose 只编排 users 域** ⇒ 其余 9 域按 [`README.md`](README.md) §三 手工起。

---

## 8. 文档交叉索引

| 关注点 | 路径 |
|---|---|
| Go 版基础设施清单（基线） | `../元语总结（人工）/人工输入给ai的模版/基础设施/golang.md` |
| Java 版基础设施替代决策（L1-L4） | `../元语总结（人工）/人工输入给ai的模版/基础设施/java.md` |
| 项目定位 / 技术栈 / 接口契约 | `AGENTS.md` |
| 当前代码实装状态 | `README.md` |
| Java 版 docker-compose 编排 | `docker-compose.yaml` |
| Spring Boot 进程入口（user / admin / gateway） | `service/user-rpc/src/main/resources/application.yml` + `service/admin-rpc/src/main/resources/application.yml` + `api-gateway/src/main/resources/application.yml` |
| 端口分配 / 部署脚本 | `manage.sh`（`get_service_port` 第 48-67 行） |
| 环境变量 | `.env` |
| Prometheus 抓取配置 | `prometheus/settings.yml` |