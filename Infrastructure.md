# Infrastructure — Go 版基线 vs Java 版（本项目）实际使用

> 一句话：Java 版基础设施 1:1 对齐 Go 版（按 `AGENTS.md §4.1`）；唯一硬决策是**服务注册中心 etcd → Nacos**；端口全部重映射到 49xxx/18xxx/19xxx 避开常用端口。

本文档对照 Go 版（基线 / "ground truth"）与 Java 版（本项目）实际使用的两套基础设施，说明"用了什么 / 怎么用 / 哪里差异"。所有判断以 `docker-compose.yaml` / `application.yml` / `.env` / `manage.sh` 为唯一依据，不参考设计文档的描述差异。

ps：`这是理论上的java基础设施组成，尚未具体配置`
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

## 3. Java 版业务进程端口分配

来自 `manage.sh get_service_port()`（bash 3.2 兼容写法）。**注意：16 个服务里只有 3 个 Spring Boot 进程能真正跑起来；其它 13 个端口已经预先分配，但服务未实装**。

| 服务名 | Maven 模块 | 端口 | 协议 | 状态 |
|---|---|---|---|---|
| `api-gateway` | `api-gateway/` | **49290** | HTTP（Spring Cloud Gateway） | ✅ 实装（JWT 鉴权是 pass-through 占位） |
| `user-rpc` | `service/user-rpc/` | **49200** (HTTP) + **49210** (gRPC) | Spring Web + grpc-java | ✅ 实装，9 endpoint 全跑通 |
| `admin-rpc` | `service/admin-rpc/` | **49202** (HTTP) + **49211** (gRPC) | 同上 | ✅ 实装，13 endpoint |
| `user-api` | `service/user-api/` | 49900 | HTTP | ❌ 未实装（独立 API 进程在 Java 版已被合并到 user-rpc 进程的 HTTP 入口） |
| `admin-api` | `service/admin-api/` | 49900 | HTTP | ❌ 未实装（同上，合并到 admin-rpc 进程） |
| `article` | `service/article/` | 18889 | HTTP | ❌ 未实装（仅 pom 骨架） |
| `comment` | `service/comment/` | 18891 | HTTP | ❌ 未实装 |
| `favorite` | `service/favorite/` | 18893 | HTTP | ❌ 未实装 |
| `follow` | `service/follow/` | 18895 | HTTP | ❌ 未实装 |
| `hot` | `service/hot/` | 18897 | HTTP | ❌ 未实装 |
| `knowledge` | `service/knowledge/` | 18089 | HTTP | ❌ 未实装 |
| `like` | `service/like/` | 18801 | HTTP | ❌ 未实装 |
| `message` | `service/message/` | 18803 | HTTP | ❌ 未实装 |
| `points` | `service/points/` | 19006 | RPC | ❌ 未实装 |
| `security` | `service/security/` | 19008 | RPC | ❌ 未实装 |
| `task` | `service/task/` | 18809 | HTTP | ❌ 未实装 |

> 端口分配从 18889 / 18891 / 18893... 这种增量是 Go 版 manage.sh 1179 行的 1:1 复刻；Java 版只是先按这个号段预占，等具体域实装时再校验。

---

## 4. Java 端实装状态（按"编排 / 进程接入 / 业务实装"三档）

| 组件 | 编排 | 进程接入（基础设施客户端） | 业务实装（哪个域在用） |
|---|---|---|---|
| **PostgreSQL** | ✅ | ✅ user-rpc + admin-rpc | ✅ user / admin |
| **Redis** | ✅ | ✅ user-rpc + admin-rpc | ✅ user（黑名单）/ admin |
| **Nacos** | ✅ | ✅ user-rpc + admin-rpc + api-gateway | ✅ user / admin / gateway（注册 + 配置） |
| **etcd** | ✅ | ❌ Java 进程未直连 | ⚠️ **仅 Milvus 元数据用**（保留 Go 版 etcd 链） |
| **Kafka** | ✅ | ❌ | ❌（hot / points 域待翻译） |
| **MinIO** | ✅ | ❌ | ❌（article 域待翻译；user 域不接） |
| **Beanstalkd ×2** | ✅ | ❌ | ❌（task 域待翻译） |
| **Neo4j** | ✅ | ❌ | ❌（knowledge 域待翻译） |
| **Milvus** | ✅ | ❌ | ❌（knowledge 域待翻译） |
| **Prometheus** | ✅ | ✅ scrape `user-rpc:49200/actuator/prometheus` + `admin-rpc:49202` + `api-gateway:49290` | ✅ user / admin / gateway |
| **Grafana** | ✅ | ✅ | ✅（dashboard 待实装） |
| **Jaeger (OTLP gRPC)** | ✅ | ✅ user-rpc / admin-rpc / api-gateway 走 `${OTEL_ADDR:localhost}:49317` | ✅ |
| **postgres-exporter** | ✅ | ❌ prometheus scrape job 未配置 | ⚠️ |
| **redis-exporter** | ✅ | ❌ 同上 | ⚠️ |
| **kafka-exporter** | ✅ | ❌ 同上 | ⚠️ |

> 注：`❌ 进程接入` 意思是当前没有任何 Spring Boot 进程持有该客户端（`pom.xml` 里没有对应 starter 依赖）。等具体域翻译时再补。

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

## 6. 关键环境变量（来自 `.env`）

按 `.env` 顶部注释，标注 `[BOTH]` = 容器 + Spring Boot 都读，`[DOCKER-ONLY]` = 仅容器，`[APP-ONLY]` = 仅 Spring Boot：

| 变量 | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `POSTGRES_ADDR / PORT` | [BOTH] | `localhost` / `49432` | DB 连接 |
| `POSTGRES_USER / PASSWORD / DB` | [BOTH] | `sea` / `sea` / `sea_user` | DB 凭据 |
| `REDIS_ADDR / PORT` | [BOTH] | `localhost` / `49380` | 缓存连接 |
| `REDIS_PASSWORD` | [BOTH] | 空 | 缓存密码（默认无） |
| `NACOS_ADDR / PORT` | [BOTH] | `localhost` / `49848` | 注册中心 |
| `NACOS_GRPC_PORT` | [BOTH] | `50848` | Nacos 2.x gRPC（主端口 + 1000） |
| `NACOS_NAMESPACE` | [BOTH] | `sea-rtw` | 命名空间 |
| `NACOS_GROUP` | [BOTH] | `USER_GROUP` | 分组 |
| `NACOS_USERNAME / PASSWORD` | [BOTH] | `sea` / `trygo` | 对应 NACOS_AUTH_IDENTITY_KEY / VALUE |
| `KAFKA_BROKERS` | [DOCKER-ONLY] | `localhost:49992` | Kafka bootstrap（host 端口） |
| `MINIO_ACCESS_KEY / SECRET_KEY` | [DOCKER-ONLY] | `minioadmin` / `minioadmin` | MinIO 凭据 |
| `NEO4J_AUTH_USER / PASSWORD` | [DOCKER-ONLY] | `neo4j` / `CHANGE_ME_NEO4J` | Neo4j 凭据（**生产必须改**） |
| `OTEL_ADDR / OTLP_PORT` | [BOTH] | `localhost` / `49317` | Jaeger OTLP gRPC |
| `USER_JWT_SECRET` | [APP-ONLY] | 64 hex chars | user JWT 签名（**≥ 32 bytes**，生产改） |
| `ADMIN_JWT_SECRET` | [APP-ONLY] | 64 hex chars | admin JWT 签名（**≥ 32 bytes**，生产改） |
| `SNOWFLAKE_NODE_ID` | [APP-ONLY] | `1`（user-rpc）/ `2`（admin-rpc） | Snowflake 节点位，**集群内唯一** |
| `SYSTEM_DEFAULT_PASSWORD` | [APP-ONLY] | `123456` | admin ResetUserPassword 还原目标（生产改） |
| `USER_ADMIN_DEFAULT_BOOTSTRAP_ENABLED` | [APP-ONLY] | `true` | 启动时建 admin/admin（生产改 false） |
| `GRAFANA_ROOT_URL` | [DOCKER-ONLY] | `http://localhost:49000/api/admin/grafana/` | Grafana 反代路径 |
| `GF_SECURITY_ADMIN_USER / PASSWORD` | [DOCKER-ONLY] | `admin` / `CHANGE_ME_GRAFANA` | Grafana 凭据（**生产改**） |
| `DOCKER_VOLUME_DIRECTORY` | [DOCKER-ONLY] | `.` | volumes 落到 `./volumes` |
| `CONTAINER_PREFIX` | [DOCKER-ONLY] | `Sea` | 容器名前缀 |

---

## 7. 关键警告（与 AGENTS.md / README 一致）

- ⚠️ **Nacos standalone 默认无 auth**：生产想开 auth 需要解开 `docker-compose.yaml` 第 36-40 行的注释，并配套 client 端 token
- ⚠️ **MinIO 镜像 Java 版松绑到 latest**：可能与 Go 版固定 tag 漂移，影响 Milvus 兼容（R-004 已知）；生产建议固定 tag
- ⚠️ **POSTGRES_PASSWORD 必须改**：`.env` 提示 `set POSTGRES_PASSWORD`，否则 `docker compose` 起不来
- ⚠️ **JWT 双密钥必须各 ≥ 32 bytes**：`USER_JWT_SECRET` / `ADMIN_JWT_SECRET` 弱 → token 可伪造
- ⚠️ **Snowflake node_id 集群内唯一**：user-rpc 用 1，admin-rpc 用 2；多副本时每副本需机器唯一
- ⚠️ **Go 版 etcd 仍保留**：因为 Milvus 元数据依赖；Java 业务进程**不直连 etcd**

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