# Round 1 · 20 个服务起栈依赖矩阵（P1-T01 产出）

> **本文件 = `P1-T01` 的交付物**：把「20 个服务的端口 / 地址 / 中间件 / 依赖」从 20 份 `application.yml` + `.env` + `README` 四处**收成一张表**，并附「在服务」的三判据。
> **取证日期**：**2026-11-01**（本轮实测）· **基线 commit**：`e77941cf`（收官终态轮）
> **口径**：本表只记**配置事实**（谁在哪个端口、连哪个中间件、依赖谁），**不记行为结论**（行为结论归各域台账 · 阶段结论归 `docs/PhaseGoal-judge.md`）。
> **每个取值都带 `文件:行号`** ⇒ 可逐条回源复核。

---

## §0 一句话结论

- **20 个服务里只有 2 个在 compose 里被编排**（`user-api` / `user-rpc`）⇒ 判据覆盖面 = **2/20 服务 · 1/10 域** ⇒ 这就是 **P0 #5 = 🟡** 的根因。
- **端口有 3 组冲突**（gRPC `8082` × 4 · gRPC `9001` × 2 · HTTP `8889` × 2）+ **7 处硬编码端口不可 env 覆盖** + **1 处未声明的默认端口**（`task-rpc` HTTP 走默认 `8080`）。
- **中间件地址在 `.env` / `.env.example` 里全是容器内 DNS 名**（`postgres` / `redis` / `nacos` / `kafka` / `minio` / `jaeger`）+ **容器内端口** ⇒ 宿主机起 jar **解析不了** ⇒ 这是 `P1-T02` 的落点。
- ⚠️ **Nacos namespace 是两套**：users 域 = `sea-rtw` + `USER_GROUP`（yml 默认值）· 其余 9 域 = `public` + `DEFAULT_GROUP` ⇒ **跨 users 域的 `discovery://` 调用解析不到**（详见 §6.2）。

---

## §1 主表：20 个服务的起栈事实

> **列含义**：`HTTP` = Spring `server.port`（api 的业务口 / rpc 的 actuator 口）· `gRPC` = `grpc.server.port` · `Nacos 名` = 注册进 Nacos 的服务名 · `ns/group` = namespace / group · `PG` / `Redis` / `Kafka` / `MinIO` / `OTEL` = 该服务实际用到的中间件（`—` = 不依赖）。
> **env 可覆盖**：`✔` = 已是 `${VAR:default}` 形态 · `✖` = **写死**（不可 env 覆盖）⇒ §2 的工作单。

| # | 服务 | 模块 | HTTP | gRPC | Nacos 名 | ns/group | PG | Redis | Kafka | MinIO | OTEL | 下游（`discovery://`） | env 可覆盖 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `user-api` | `service/users/user/user-api` | `${USER_API_PORT:49900}` `application.yml:62` | — | `user-api` `:7` | `sea-rtw`/`USER_GROUP` `:52-53` | — | `${REDIS_ADDR:redis}:${REDIS_PORT:6379}` `:15-16` | — | — | `${OTEL_ADDR:jaeger}:${OTLP_PORT:4317}` `:195` | `user-rpc` `:112` | ✔ |
| 2 | `user-rpc` | `service/users/user/user-rpc`（**`application.yaml`**） | `${USER_RPC_MANAGEMENT_PORT:9094}` `application.yaml:235` | `${USER_RPC_PORT:49210}` `:141` | `user-rpc` `:6` | `sea-rtw`/`USER_GROUP` `:91-92` | `${POSTGRES_ADDR:postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:first_db}` `:13` | `${REDIS_ADDR:redis}:${REDIS_PORT:6379}` `:53-54` | — | — | `${OTEL_ADDR:jaeger}` `:297` | — | ✔ |
| 3 | `admin-api` | `service/users/admin/admin-api` | `${ADMIN_API_PORT:8889}` `application.yml:65` | — | `admin-api` `:20` | `sea-rtw`/`USER_GROUP` `:55-56` | — | `${REDIS_ADDR:redis}:${REDIS_PORT:6379}` `:28-29` | — | — | `${OTEL_ADDR:jaeger}` `:186` | `admin-rpc` `:94` · `user-rpc` `:128` | ✔ |
| 4 | `admin-rpc` | `service/users/admin/admin-rpc` | `${ADMIN_RPC_MANAGEMENT_PORT:9097}` `application.yml:229` | `${ADMIN_RPC_PORT:8081}` `:133` | `admin-rpc` `:5` | `sea-rtw`/`USER_GROUP` `:95-96` | `${POSTGRES_ADDR:postgres}` `:10` | `${REDIS_ADDR:redis}` `:57-58` | — | — | `${OTEL_ADDR:jaeger}` `:290` | — | ✔ |
| 5 | `message-api` | `service/message/message-api` | `${MESSAGE_API_PORT:8892}` `application.yml:36` | — | `message-api` `:10` | `public`/`DEFAULT_GROUP` `:24-25` | — | — | — | — | `${OTEL_ADDR:jaeger}` `:233` | `message-rpc` `:65` | ✔ |
| 6 | `message-rpc` | `service/message/message-rpc` | `${MESSAGE_RPC_MANAGEMENT_PORT:16067}` `application.yml:178` | **8082** `:219` | `message-rpc` `:11` | `public`/`DEFAULT_GROUP` `:131-133` | `${POSTGRES_HOST:postgres}` `:27` | — | — | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:339` | `user-rpc` `:263` · `admin-rpc` `:270` | **✖ gRPC** |
| 7 | `like-api` | `service/like/like-api` | **18887** `application.yml:33` | — | **`likecenter`** `:50` | `public`/默认 `:55` | — | — | — | — | — | `like-rpc` `:183` | **✖ HTTP** |
| 8 | `like-rpc` | `service/like/like-rpc` | `${LIKE_RPC_MANAGEMENT_PORT:9098}` `application.yml:266` | **8082** `:307` | `like-rpc` `:13` | `public`/`DEFAULT_GROUP` `:220-221` | `${POSTGRES_HOST:postgres}` `:18` | `${REDIS_HOST:redis}` `:109-110` | `${KAFKA_BROKERS:kafka:9092}` `:168` | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:457` | `message-rpc` `:326` | **✖ gRPC** |
| 9 | `task-api` | `service/task/task-api` | `${TASK_API_PORT:18886}` `application.yml:72` | — | `task-api` `:18` | `public`/`DEFAULT_GROUP` `:49-50` | — | — | — | — | `${OTEL_ADDR:jaeger}` `:270` | ⚠️ **静态地址** `${TASK_RPC_ADDRESS:127.0.0.1:9005}` `:113`（**不走 Nacos**） | ✔ |
| 10 | `task-rpc` | `service/task/task-rpc` | ❌ **无 HTTP 口**（**无 `spring-boot-starter-web` 依赖** ⇒ 不起 HTTP 口） | **9005** `:222` | `task-rpc` `:9` | `public`/`DEFAULT_GROUP` `:214-215` | `${POSTGRES_HOST:postgres}` `:14` | `${REDIS_HOST:redis}` `:110-111` | `${KAFKA_BROKERS:kafka:9092}` `:182` | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:343` | — | **✖ HTTP + gRPC** |
| 11 | `article-api` | `service/article/article-api` | `${ARTICLE_API_PORT:8889}` `application.yml:43` | — | `article-api` `:10` | `public`/`DEFAULT_GROUP` `:33-34` | — | — | — | — | `${OTEL_ADDR:jaeger}` `:258` | `article-rpc` `:72` · `user-rpc` `:78` | ✔ |
| 12 | `article-rpc` | `service/article/article-rpc` | `${ARTICLE_RPC_MANAGEMENT_PORT:9092}` `application.yml:163` | **9001** `:237` | `article-rpc` `:11` | `public`/`DEFAULT_GROUP` `:122-123` | `${POSTGRES_HOST:postgres}` `:18` | — | `${KAFKA_BROKERS:kafka:9092}` `:100` | `${MINIO_ENDPOINT:minio:9000}` `:369` | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:423` | `message-rpc` `:279` · ⚠️ `security-rpc` `:272`（**范围外** · `AGENTS.md` §一） | **✖ gRPC** |
| 13 | `comment-api` | `service/comment/comment-api` | `${COMMENT_API_PORT:8888}` `application.yml:31` | — | `comment-api` `:10` | `public`/`DEFAULT_GROUP` `:21-22` | — | — | — | — | `${OTEL_ADDR:jaeger}` `:198` | `comment-rpc` `:59` | ✔ |
| 14 | `comment-rpc` | `service/comment/comment-rpc` | ⚠️ **无 HTTP**（`web-application-type: none` `:39`） | **9001** `:223` | `comment-rpc` `:15` | `public`/`DEFAULT_GROUP` `:216-217` | `${POSTGRES_HOST:postgres}` `:51` | `${REDIS_HOST:redis}` `:134-135` | `${KAFKA_BROKERS:kafka:9092}` `:184` | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:366` | `message-rpc` `:242` · `article-rpc` `:248` | **✖ gRPC** |
| 15 | `favorite-api` | `service/favorite/favorite-api` | `${FAVORITE_API_PORT:18893}` `application.yml:110` | — | `favorite-api` `:24` | `public`/`DEFAULT_GROUP` `:104-105` | — | `${REDIS_ADDR:redis}` `:32-33` | — | — | `${OTEL_ADDR:jaeger}` `:226` | `favorite-rpc` `:139` | ✔ |
| 16 | `favorite-rpc` | `service/favorite/favorite-rpc` | `${FAVORITE_RPC_MANAGEMENT_PORT:16062}` `application.yml:178` | **8082** `:224` | `favorite-rpc` `:17` | `public`/`DEFAULT_GROUP` `:135-136` | `${POSTGRES_HOST:postgres}` `:57` | — | — | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:355` | `user-rpc` `:271` · `article-rpc` `:279` | **✖ gRPC** |
| 17 | `follow-api` | `service/follow/follow-api` | `${FOLLOW_API_PORT:8891}` `application.yml:70` | — | `follow-api` `:13` | `public`/`DEFAULT_GROUP` `:59-60` | — | `${REDIS_HOST:redis}` `:27-28` | — | — | `${OTEL_ADDR:jaeger}` `:290` | `follow-rpc` `:99` | ✔ |
| 18 | `follow-rpc` | `service/follow/follow-rpc` | `${FOLLOW_RPC_MANAGEMENT_PORT:16065}` `application.yml:142` | `${FOLLOW_RPC_PORT:8082}` `:154` | `follow-rpc` `:11` | `public`/`DEFAULT_GROUP` `:107-108` | `${POSTGRES_HOST:postgres}` `:25` | — | — | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:jaeger:4317}` `:292` | `user-rpc` `:174` | ✔（但**默认 8082 与 3 个服务撞**） |
| 19 | `hot-api` | `service/hot/hot-api` | `${HOT_API_PORT:18897}` `application.yml:34` | — | `hot-api` `:14` | `public`/`DEFAULT_GROUP` `:25-26` | — | — | — | — | `${OTEL_ADDR:jaeger}` `:209` | `hot-rpc` `:66` · `article-rpc` `:72` | ✔ |
| 20 | `hot-rpc` | `service/hot/hot-rpc` | `${HOT_RPC_MANAGEMENT_PORT:19091}` `application.yml:185` | `${HOT_RPC_GRPC_PORT:8083}` `:220` | `hot-rpc` `:17` | `public`/`DEFAULT_GROUP` `:127-128` | — | `${REDIS_HOST:redis}` `:24-25` | `${KAFKA_BROKERS:kafka:9092}` `:99` | — | `${OTEL_EXPORTER_OTLP_ENDPOINT:http://jaeger:4317}` `:334` | — | ✔ |

> ⚠️ **`user-rpc` 的配置文件是 `application.yaml`**（不是 `.yml`）⇒ 任何按 `application.yml` 通配的脚本都会**漏掉它**（本轮实测：`find service -name "application*.yml"` 少 1 个模块）。

---

## §2 硬编码端口小表（= `P1-T03` 的工作单）

> **判据**：`application.yml` 里 `port:` 后面是**纯数字** ⇒ 该端口**无法用 env 覆盖** ⇒ 同机并行时无法错开。
> **本轮实测（2026-11-01）共 7 处硬编码 + 1 处未声明默认端口**（计划书原文列了 6 处 ⇒ **多出 `task-rpc` 的 HTTP 默认口**）。
> 🟢 **【2026-11-01 · Round 7 订正】本表即「工作单」，落码曾偏离它 ⇒ 已回改**：本表的「建议 env 化」列**每一处都写明「默认值保持不变」**（`${MESSAGE_RPC_PORT:8082}` / `${COMMENT_RPC_PORT:9001}` / …）⇒ ⇒ **本表本身是对的**；偏离发生在**落码与 spec** —— `.agents/specs/round1-full-service-startup.md` §1 的 `P1-T03` 行写「**3 处默认值错开**」（**实际落码改了 6 处** ⇒ 连 spec 的计数也不准）⇒ ⇒ **错开默认值这条路本身就是错的**（默认值层 = 对外契约面）⇒ ⇒ **已按本表回改**（见 §7.6）。

| # | 文件:行号 | 内容 | 冲突 | 建议 env 化（**默认值保持不变 ⇒ 探针零影响**） |
|---|---|---|---|---|
| H1 | `service/message/message-rpc/src/main/resources/application.yml:219` | `grpc.server.port: 8082` | 与 H2/H3 + `follow-rpc` 四撞 | `${MESSAGE_RPC_PORT:8082}` |
| H2 | `service/like/like-rpc/src/main/resources/application.yml:307` | `grpc.server.port: 8082` | 同上 | `${LIKE_RPC_PORT:8082}` |
| H3 | `service/favorite/favorite-rpc/src/main/resources/application.yml:224` | `grpc.server.port: 8082` | 同上 | `${FAVORITE_RPC_PORT:8082}` |
| H4 | `service/article/article-rpc/src/main/resources/application.yml:237` | `grpc.server.port: 9001` | 与 H5 撞 | `${ARTICLE_RPC_PORT:9001}` |
| H5 | `service/comment/comment-rpc/src/main/resources/application.yml:223` | `grpc.server.port: 9001` | 与 H4 撞 | `${COMMENT_RPC_PORT:9001}` |
| H6 | `service/task/task-rpc/src/main/resources/application.yml:222` | `grpc.server.port: 9005` | 单占（`task-api` 静态指它） | `${TASK_RPC_PORT:9005}` |
| H7 | `service/like/like-api/src/main/resources/application.yml:33` | `server.port: 18887` | 单占 | `${LIKE_API_PORT:18887}` |
| ~~H8~~ | ~~`service/task/task-rpc/.../application.yml`~~ | ~~HTTP 口走 Spring 默认 `8080`~~ | — | ❌ **已撤销**（本轮实测：该模块**无 `spring-boot-starter-web`** ⇒ **根本不起 HTTP 口** ⇒ 不存在 8080 绑定 ⇒ 原判「未声明默认端口」作废；本轮加过的 `server.port` 声明已回滚） |

### §2.1 为什么 env 化**不会**踩坏既有探针（L4 证据链）

Spring Boot 的属性优先级：**命令行参数 > OS 环境变量 > 配置文件** ⇒ **配置文件里的占位符值是最低优先级** ⇒ 探针无论用「命令行参数」还是「env 覆盖」都能盖住。

| 探针 | 覆盖方式 | 证据 |
|---|---|---|
| `tools/api-full-parity-probe/article/run.sh` | **命令行** `--grpc.server.port=` / env `ARTICLE_API_PORT=` | `:336` · `:345` |
| `tools/api-full-parity-probe/follow/run.sh` | env `FOLLOW_RPC_PORT=` | `:435` |
| `tools/api-full-parity-probe/favorite/run.sh` | **命令行** `--grpc.server.port=` / `--server.port=` | `:377` · `:389` |
| `tools/api-full-parity-probe/favorite-rpc/run.sh` | env `GRPC_SERVER_PORT=`（松散绑定 ⇒ 命中 `grpc.server.port`） | `:432` |
| `tools/api-full-parity-probe/like/run.sh` | env `GRPC_SERVER_PORT=` / `SERVER_PORT=` | `:697` · `:719` |
| `tools/api-full-parity-probe/users/run.sh` | env `GRPC_SERVER_PORT=` / `SERVER_PORT=` | `:319` · `:337` |
| `tools/difftest/comment/run_diff.sh` | **命令行** `--grpc.server.port=59001` | `:121` |
| `tools/difftest/flyway-rpc-concurrent.sh` | **命令行** `--grpc.server.port=<端口>` | `:22` |

> ⇒ **结论**：env 化只需**保持 env 键名与默认值不变**（`FOLLOW_RPC_PORT` / `GRPC_SERVER_PORT` 的既有用法不受影响），探针**零影响**。
> ⚠️ **唯一不可改的默认值** = `article-rpc` 的 **9001**（article 探针**不传任何端口参数** ⇒ 它依赖 yml 默认值 ⇒ 见 §3 的取值裁决）。

---

## §3 端口冲突清单与目标端口表

### §3.1 实测到的冲突（2026-11-01）

> ⚠️ **解法在 §3.2 的「分配层」**（env 文件）—— **不在默认值层**（默认值必须逐条 = Go 真源值）。

| 冲突组 | 端口 | 占用者 | 形态 | 解法 |
|---|---|---|---|---|
| **C-1** | gRPC `8082` | `message-rpc` · `like-rpc` · `favorite-rpc` · `follow-rpc` | 3 处硬编码 + 1 处 env | env 分配：`favorite=8082` · `follow=8083` · `like=8085` · `message=8086`（**默认值 4 个都仍是 Go 的 8082**） |
| **C-2** | gRPC `9001` | `article-rpc` · `comment-rpc` | 2 处硬编码 | env 分配：`article=9001` · `comment=9002`（**默认值 2 个都仍是 Go 的 9001**） |
| **C-3** | HTTP `8889` | `admin-api` · `article-api` | 2 处 env（默认值相同） | env 分配：`article=8889` · `admin=8890`（**默认值 2 个都仍是 Go 的 8889**） |

### §3.2 端口的**两层分工**（⚠️ 2026-11-01 · Round 7 订正 —— 原表「改默认值解冲突」**方向错了**，已回改）

| 层 | 承担者 | 规则 | 谁钉住它 |
|---|---|---|---|
| **① 默认值层** | `application.yml` 的 `${VAR:默认值}` | **默认值 = Go 真源的值，一字不差**（Go 侧 `ListenOn` / `Port`） | ✅ 每个 rpc 的 `*PrometheusCarrierGateTest`（例：`HotRpcPrometheusCarrierGateTest:259`「`grpc.server.port` 必须 = 8083（= Go `etc/hot.yaml:2` 的 `ListenOn`）」· 门禁原文明确写「**允许 env 覆盖（探针/部署要换口），但默认值必须一字不差**」） |
| **② 分配层** | `.env`（容器语义）· `.env.host`（宿主语义） | **同一台宿主并行起 20 个服务 ⇒ 监听口互异** | `verify-all-services.sh` 实跑 ⇒ **20/20**（§7.2） |

**为什么冲突不能靠改默认值解** —— Go 侧 **favorite/follow/like/message-rpc 四个都监听 `8082`** · **article/comment-rpc 都监听 `9001`** · **article/admin-api 都监听 `8889`**（**同一批端口在 Go 侧本就重复** ⇒ 因为 Go 是「一服务一部署」，端口按**服务**分配，不按**进程**唯一）⇒ ⇒ 搬成「一机多进程」时，**互异性必须由部署层（env）提供**，**不能由默认值提供** ⇒ ⇒ 改默认值 = **改了对外契约面**（**且已有守门因此变红** —— 见 §7.6）。

| 服务 | Go 真源值 | Go 出处 | RAG yml 默认值 | 端口类 | 分配层取值（`.env` / `.env.host`） |
|---|---|---|---|---|---|
| `user-api` | 8888 | `service/user/user/api/etc/usercenter.yaml:3` | 49900 | HTTP | ⚠️ RAG 侧既有值（**非本轮引入**）· env `USER_API_PORT=49900` |
| `admin-api` | 8889 | `service/user/admin/api/etc/admincenter.yaml:3` | **8889** | HTTP | env `ADMIN_API_PORT=8890`（解 C-3） |
| `article-api` | 8889 | `service/article/api/etc/article-api.yaml:3` | **8889** | HTTP | env `ARTICLE_API_PORT=8889` |
| `comment-api` | 8888 | `service/comment/api/etc/commentservice.yaml:3` | **8888** | HTTP | env `COMMENT_API_PORT=8888` |
| `favorite-api` | 8890 | `service/favorite/api/etc/favorite.yaml:3` | 18893 | HTTP | ⚠️ RAG 侧既有值（**非本轮引入**）· env `FAVORITE_API_PORT=18893` |
| `follow-api` | 8891 | `service/follow/api/etc/followcenter.yaml:3` | **8891** | HTTP | env `FOLLOW_API_PORT=8891` |
| `hot-api` | 8893 | `service/hot/api/etc/hotcenter.yaml:3` | 18897 | HTTP | ⚠️ RAG 侧既有值（**非本轮引入**）· env `HOT_API_PORT=18897` |
| `like-api` | 18887 | `service/like/api/etc/likecenter.yaml:3` | **18887** | HTTP | env `LIKE_API_PORT=18887` |
| `message-api` | 8892 | `service/message/api/etc/messagecenter.yaml:3` | **8892** | HTTP | env `MESSAGE_API_PORT=8892` |
| `task-api` | 18886 | `service/task/api/etc/task.yaml:3` | **18886** | HTTP | env `TASK_API_PORT=18886` |
| `user-rpc` | 9004 | `service/user/user/rpc/etc/user.yaml:2` | 49210 | gRPC | ⚠️ RAG 侧既有值（**非本轮引入**）· env `USER_RPC_PORT=49210`（compose 已占用） |
| `admin-rpc` | 8081 | `service/user/admin/rpc/etc/admin.yaml:2` | **8081** | gRPC | env `ADMIN_RPC_PORT=8081` |
| `article-rpc` | 9001 | `service/article/rpc/etc/article.yaml:2` | **9001** | gRPC | ⚠️ **必须保留**（article 探针依赖默认值）· env `ARTICLE_RPC_PORT=9001` |
| `comment-rpc` | 9001 | `service/comment/rpc/etc/comment.yaml:2` | **9001** | gRPC | env `COMMENT_RPC_PORT=9002`（解 C-2） |
| `favorite-rpc` | 8082 | `service/favorite/rpc/etc/favorite.yaml:2` | **8082** | gRPC | env `FAVORITE_RPC_PORT=8082` |
| `follow-rpc` | 8082 | `service/follow/rpc/etc/follow.yaml:2` | **8082** | gRPC | env `FOLLOW_RPC_PORT=8083`（解 C-1） |
| `hot-rpc` | 8083 | `service/hot/rpc/etc/hot.yaml:2` | **8083** | gRPC | env `HOT_RPC_GRPC_PORT=8084`（解 C-1） |
| `like-rpc` | 8082 | `service/like/rpc/etc/like.yaml:2` | **8082** | gRPC | env `LIKE_RPC_PORT=8085`（解 C-1） |
| `message-rpc` | 8082 | `service/message/rpc/etc/message.yaml:2` | **8082** | gRPC | env `MESSAGE_RPC_PORT=8086`（解 C-1） |
| `task-rpc` | 9005 | `service/task/rpc/etc/task.yaml:2` | **9005** | gRPC | env `TASK_RPC_PORT=9005`｜**无 HTTP 口**（无 web starter） |

**rpc 侧的 actuator 口（`server.port`）**：`user-rpc` 9094 · `admin-rpc` 9097 · `message-rpc` 16067 · `like-rpc` 9098 · `article-rpc` 9092 · `favorite-rpc` 16062 · `follow-rpc` 16065 · `hot-rpc` 19091 · **`task-rpc` 无 HTTP 口（无 web starter · 本轮实测）** · **`comment-rpc` 无 HTTP（`web-application-type: none`）** ⇒ **两两不冲突** ✓（这 9 个口本轮**未改**，且逐条 = Go `DevServer.Port`）

> ⇒ **互异性由分配层保证**：`.env` / `.env.host` 里 **29 个端口变量取值两两不同**（实跑 `ALLOC_DUP_COUNT 0`）—— 10 个 api HTTP + 10 个 rpc gRPC + 9 个 rpc actuator；⚠️ 其中 `TASK_RPC_MANAGEMENT_PORT` 对应的 HTTP 口**实际不起**（该模块无 web starter）⇒ **实际监听口 28 个** ⇒ **实测 20/20 在服务（§7.2）**。
> ⇒ **默认值层不要求互异** —— 它按 Go 真源**逐条一字不差**（含 Go 侧本就重复的 `8082` × 4 / `9001` × 2 / `8889` × 2）。

---

## §4 「在服务」的三判据（`P1-T05` 的验收口径）

> **判据来自 §1 的事实**：① **Nacos 服务名可查**（注册名 **≠** 模块名时以 §1 为准）② **监听端口 LISTEN**（api = HTTP 口 · rpc = gRPC 口）③ **健康端点 200**（`/actuator/health`）。

| # | 服务 | Nacos 服务名 | 判据②端口 | 判据③健康端点 | 备注 |
|---|---|---|---|---|---|
| 1 | `user-api` | `user-api` | 49900 | `http://127.0.0.1:49900/actuator/health` | |
| 2 | `user-rpc` | `user-rpc` | 49210 | ❌ **无健康端点** | ⚠️ 本模块**无 `spring-boot-starter-web`** ⇒ 不起 HTTP 口（compose 亦只发布 gRPC） |
| 3 | `admin-api` | `admin-api` | 8890 | `http://127.0.0.1:8890/actuator/health` | |
| 4 | `admin-rpc` | `admin-rpc` | 8081 | `http://127.0.0.1:9097/actuator/health` | |
| 5 | `message-api` | `message-api` | 8892 | `http://127.0.0.1:8892/actuator/health` | |
| 6 | `message-rpc` | `message-rpc` | 8086 | `http://127.0.0.1:16067/actuator/health` | |
| 7 | `like-api` | ⚠️ **`likecenter`** | 18887 | `http://127.0.0.1:18887/actuator/health` | ⚠️ **注册名 ≠ 模块名**（Go 侧 `likecenter.yaml`） |
| 8 | `like-rpc` | `like-rpc` | 8085 | `http://127.0.0.1:9098/actuator/health` | |
| 9 | `task-api` | `task-api` | 18886 | ❌ **无健康端点** | ⚠️ 本模块**无 `spring-boot-starter-actuator`** ⇒ 无 `/actuator/**` |
| 10 | `task-rpc` | `task-rpc` | 9005 | ❌ **无健康端点** | ⚠️ 本模块**无 `spring-boot-starter-web`** ⇒ 不起 HTTP 口 |
| 11 | `article-api` | `article-api` | 8889 | `http://127.0.0.1:8889/actuator/health` | |
| 12 | `article-rpc` | `article-rpc` | 9001 | `http://127.0.0.1:9092/actuator/health` | |
| 13 | `comment-api` | `comment-api` | 8888 | `http://127.0.0.1:8888/actuator/health` | |
| 14 | `comment-rpc` | `comment-rpc` | 9002 | ❌ **无健康端点** | `web-application-type: none` ⇒ **判据③ 不适用** ⇒ 判据降为 ①+② |

> ⚠️ **4 个服务没有判据③**（`user-rpc` · `task-api` · `task-rpc` · `comment-rpc`）⇒ 逐条已复核 **pom 依赖 + yml** ⇒ **是模块形态决定的，不是漏测** ⇒ 它们的判据降为 ①②（§7.2 的实测结果按此口径）。
| 15 | `favorite-api` | `favorite-api` | 18893 | `http://127.0.0.1:18893/actuator/health` | |
| 16 | `favorite-rpc` | `favorite-rpc` | 8082 | `http://127.0.0.1:16062/actuator/health` | |
| 17 | `follow-api` | `follow-api` | 8891 | `http://127.0.0.1:8891/actuator/health` | |
| 18 | `follow-rpc` | `follow-rpc` | 8083 | `http://127.0.0.1:16065/actuator/health` | |
| 19 | `hot-api` | `hot-api` | 18897 | `http://127.0.0.1:18897/actuator/health` | |
| 20 | `hot-rpc` | `hot-rpc` | 8084 | `http://127.0.0.1:19091/actuator/health` | |

> ⚠️ **Nacos 查询口径**：namespace = `public`（9 域）· `sea-rtw`（users 域，**当前实例不存在** ⇒ 查询用 `namespaceId=` 空串即 public）。
> ⇒ **查询命令**：`curl -s 'http://127.0.0.1:49848/nacos/v1/ns/instance/list?serviceName=<名>&namespaceId=public'`

---

## §5 中间件：容器内地址 ↔ 宿主机地址（`P1-T02` 的落点）

| 变量 | 容器内值（`.env` 现值） | 宿主机值（本轮实测） | 容器 |
|---|---|---|---|
| `POSTGRES_HOST` / `POSTGRES_ADDR` | `postgres` | **`127.0.0.1`** | `Sea-postgres`（宿主 `49432`） |
| `POSTGRES_PORT` | `5432` | **`49432`** | 同上 |
| `POSTGRES_USER` / `POSTGRES_DB` / `POSTGRES_PASSWORD` | `sea` / `sea_user` / `sea` | **同左**（实测 `Sea-postgres` 初始化 env） | 同上 |
| `REDIS_ADDR` / `REDIS_HOST` | `redis` | **`127.0.0.1`** | `Sea-redis`（宿主 `49380`） |
| `REDIS_PORT` | `6379` | **`49380`** | 同上 |
| `NACOS_ADDR` | `nacos` | **`127.0.0.1`** | `Sea-nacos`（宿主 `49848`） |
| `NACOS_PORT` | `8848` | **`49848`** | 同上 |
| `NACOS_NAMESPACE` / `NACOS_GROUP` | `public` / `DEFAULT_GROUP` | **同左**（Nacos 实际只有 `public` 一个 ns） | 同上 |
| `KAFKA_BROKERS` | `kafka:9092` | **`127.0.0.1:49992`** | `Sea-kafka`（EXTERNAL listener 已 advertise `127.0.0.1:49992`） |
| `MINIO_ENDPOINT` | `minio:9000` | **`127.0.0.1:49100`** | `Sea-minio` |
| `OTEL_ADDR` | `jaeger` | **`127.0.0.1`** | `Sea-jaeger` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `jaeger:4317` | **`http://127.0.0.1:49317`** | 同上（宿主 `49317` → 容器 `4317`） |

> ⚠️ **OTEL 的 URL 形态**：`http://127.0.0.1:49317:4317` 是**非法 URL**（`Invalid URL port: "49317:4317"`）⇒ 必须是 `http://127.0.0.1:49317`。
> ⚠️ **`OTEL_ADDR` 与 `OTLP_PORT` 是两个变量**（api 侧用 `http://${OTEL_ADDR}:${OTLP_PORT}`，rpc 侧用 `OTEL_EXPORTER_OTLP_ENDPOINT`）⇒ 宿主机模板里**两个都要给**。

### §5.1 启动必需的密钥（P0 #8 fail-closed ⇒ 缺一个就拒绝启动）

| 变量 | 谁要 | 本机 `.env` 现状 |
|---|---|---|
| `USER_JWT_SECRET` | `user-api` · `user-rpc` · `admin-api`·`admin-rpc`(`ADMIN_`) · `article-api` · `comment-api` · `follow-api` · `like-api` · `message-api` | ✅ 有 |
| `ADMIN_JWT_SECRET` | `admin-api` · `admin-rpc` · `message-api` | ✅ 有 |
| `FAVORITE_JWT_SECRET` | `favorite-api`（`application.yml:174`） | ❌ **缺** ⇒ **必须补** |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `article-rpc`（`${MINIO_SECRET_KEY:minioadmin}` 有默认值 ⇒ 不阻断，但**值要真**） | ✅ 有 |

---

## §6 下游依赖与启动顺序

### §6.1 依赖图（`discovery://` ⇒ 被调方必须先注册进 Nacos）

```
users 域（ns=sea-rtw）:  user-rpc · admin-rpc  ←  被 article-api / message-rpc / favorite-rpc / follow-rpc 以 discovery:// 引用
其余 9 域（ns=public）: message-rpc · like-rpc · task-rpc · article-rpc · comment-rpc · favorite-rpc · follow-rpc · hot-rpc
                          ↑ 被 api 层引用：message-api · like-api · task-api(⚠️静态) · article-api · comment-api · favorite-api · follow-api · hot-api
rpc 之间的 rpc→rpc：message-rpc → user-rpc, admin-rpc · like-rpc → message-rpc · article-rpc → message-rpc(+security-rpc 范围外) · comment-rpc → message-rpc, article-rpc · favorite-rpc → user-rpc, article-rpc · follow-rpc → user-rpc
```

**建议启动顺序**：`PG/Redis/Nacos/Kafka/MinIO`（compose，已在跑）→ **10 个 rpc**（`user-rpc` → `admin-rpc` → `message-rpc` → `article-rpc` → `comment-rpc` → `like-rpc` → `favorite-rpc` → `follow-rpc` → `task-rpc` → `hot-rpc`）→ **10 个 api**。

### §6.2 ⚠️ namespace 陷阱（**两套 ns ⇒ 跨 users 域的 `discovery://` 解析不到**）

| 服务 | yml 默认 ns | yml 默认 group | 事实影响 |
|---|---|---|---|
| `user-api` · `user-rpc` · `admin-api` · `admin-rpc` | `sea-rtw` | `USER_GROUP` | ⚠️ **Nacos 实例里只有 `public`**（实测 `GET /nacos/v1/console/namespaces` ⇒ 只有 `""`/`public`）⇒ 默认值下**注册会失败** |
| 其余 16 个服务 | `public` | `DEFAULT_GROUP` | ✅ |

⇒ **宿主机模板必须显式给 `NACOS_NAMESPACE=public` + `NACOS_GROUP=DEFAULT_GROUP`**（`.env` 现值已经是 `public`/`DEFAULT_GROUP` ⇒ **但 `docker-compose.yaml` 的默认值是 `sea-rtw`/`USER_GROUP`** ⇒ 两者不一致 ⇒ 靠 `.env` 兜住）。
⇒ ⚠️ **一旦两个 ns 都用 `public`/`DEFAULT_GROUP`** ⇒ `discovery:///user-rpc` 才能被其余 9 域解析到 ⇒ **本轮 20/20 起栈与两条跨服务链路都必须用同一套 ns**。

---

## §7 实测结果（`P1-T05` · 2026-11-01）

### §7.1 部署形态（本轮实测的那一套）

| 项 | 值 |
|---|---|
| **中间件** | compose 的 `Sea-postgres` · `Sea-redis` · `Sea-nacos` · `Sea-jaeger` · `Sea-minio` + 兄弟仓的 `Sea-kafka` |
| **应用** | **20 个服务全部以宿主机 `java -jar` 起**（`service/<域>/<域>-{api,rpc}/target/*.jar`）⇒ 相互可达、注册 IP 宿主可路由 |
| **Nacos** | `Sea-nacos`（宿主 `49848`）· namespace `public` · group `DEFAULT_GROUP` ⇒ **20 个服务同一套 ns** |
| **PG** | `Sea-postgres`（宿主 `49432`）· 库 `sea_user` · 用户 `sea` ⇒ ⚠️ **`max_connections` 由 100 提到 500**（见 §7.4-③） |
| **端口** | 全部取 §3.2 的**默认端口**；**唯一例外**：`favorite-rpc` 实际跑 `8087`（本机 `8082` 被外部 Python 服务占用 ⇒ 见 §7.4-②） |
| **Nacos 地址变量** | ⚠️ **两类写法**（见 §7.4-①）⇒ 10 个「FUSED 写法」的服务用 `NACOS_ADDR=127.0.0.1:49848` 启动，其余用 `NACOS_ADDR=127.0.0.1` + `NACOS_PORT=49848` |

### §7.2 三判据实测结果（**20/20 PASS** · `EXIT=0`）

> 判据：① Nacos 服务名可查（`namespaceId=public`）② 监听端口 LISTEN ③ `/actuator/health` = 200
> **复算命令**：`bash docs/RoundPlan/round-01/verify-all-services.sh` ⇒ `EXIT=0`

| # | 服务 | Nacos 名 | 端口 | ①Nacos | ②LISTEN | ③health | 结论 |
|---|---|---|---|---|---|---|---|
| 1 | `user-api` | `user-api` | 49900 | n=1 | Y | 200 | ✅ |
| 2 | `user-rpc` | `user-rpc` | 49210 | n=1 | Y | — | ✅ **无健康端点**：本模块**无 `spring-boot-starter-web`** ⇒ 不起 HTTP 口（compose 亦只发布 gRPC） |
| 3 | `admin-api` | `admin-api` | 8890 | n=1 | Y | 200 | ✅ |
| 4 | `admin-rpc` | `admin-rpc` | 8081 | n=1 | Y | 200 | ✅ |
| 5 | `message-api` | `message-api` | 8892 | n=1 | Y | 200 | ✅ |
| 6 | `message-rpc` | `message-rpc` | 8086 | n=1 | Y | 200 | ✅ |
| 7 | `like-api` | **`likecenter`** | 18887 | n=1 | Y | 200 | ✅ |
| 8 | `like-rpc` | `like-rpc` | 8085 | n=1 | Y | 200 | ✅ |
| 9 | `task-api` | `task-api` | 18886 | n=1 | Y | — | ✅ **无健康端点**：本模块**无 `spring-boot-starter-actuator`** |
| 10 | `task-rpc` | `task-rpc` | 9005 | n=1 | Y | — | ✅ **无健康端点**：本模块**无 `spring-boot-starter-web`** ⇒ 不起 HTTP 口 |
| 11 | `article-api` | `article-api` | 8889 | n=1 | Y | 200 | ✅ |
| 12 | `article-rpc` | `article-rpc` | 9001 | n=1 | Y | 200 | ✅ |
| 13 | `comment-api` | `comment-api` | 8888 | n=1 | Y | 200 | ✅ |
| 14 | `comment-rpc` | `comment-rpc` | 9002 | n=1 | Y | — | ✅ **无健康端点**：`spring.main.web-application-type: none` |
| 15 | `favorite-api` | `favorite-api` | 18893 | n=1 | Y | 200 | ✅ |
| 16 | `favorite-rpc` | `favorite-rpc` | **8087** | n=1 | Y | 200 | ✅ 端口被外部服务占用 ⇒ 运行时改口（默认仍 8082） |
| 17 | `follow-api` | `follow-api` | 8891 | n=1 | Y | 200 | ✅ |
| 18 | `follow-rpc` | `follow-rpc` | 8083 | n=1 | Y | 200 | ✅ |
| 19 | `hot-api` | `hot-api` | 18897 | n=1 | Y | 200 | ✅ |
| 20 | `hot-rpc` | `hot-rpc` | 8084 | n=1 | Y | 200 | ✅ |

**PASS = 20 / FAIL = 0 / TOTAL = 20**
⇒ **4 个服务没有 ③ 是模块形态决定的（逐条已复核依赖与 yml），不是漏测** ⇒ 它们的判据降为 ①②。

🟢 **【2026-11-01 · Round 6 收口轮 · 复测】** —— **本表的三判据在基线 `2d70a4cc` 上复跑仍是 `PASS=20 / FAIL=0 / TOTAL=20`（EXIT=0）**（**20 个服务全部真起 · 宿主机 `java -jar` + `.env.host`**）⇒ **阶段一收口时点未回退**；逐条读数 + 起栈踩坑（**启动顺序竞争** · **8082 被外部进程占用** · **`NACOS_ADDR` 融合写法已由 `R1-ENV-001` 修掉**）+ **真 Tomcat 状态行运行时字节（10/10 无 reason phrase）** ⇒ 详见 [`docs/RoundPlan/round-06/regression-report.md`](../round-06/regression-report.md) **§十**。

### §7.3 两条跨服务链路（**有响应，不是连接失败**）

**链路 A（api → rpc → PG · 含 rpc→rpc）** ——
`GET http://127.0.0.1:8889/v1/articles?page=1&size=5` ⇒ `article-api` → `article-rpc` → PG（并由 `article-rpc` 调 `user-rpc` 取作者名）：

```json
{"code":200,"msg":"OK","data":{"articles":[{"id":"9007199254740993","title":"Round1 链路播种标题",
 "author_id":"1001","author_name":"round1_user","status":2,"view_count":1,
 "ext_info":{"publication_gap":"legacy_revision_missing"}}],"total":1,"page":1,"page_size":20}}
```

> ✅ `author_name: "round1_user"` **来自 `user-rpc`** ⇒ **rpc→rpc（`article-rpc` → `user-rpc`）已打通**，且数据真来自 PG。

**链路 B（api → rpc → rpc）** ——
`POST http://127.0.0.1:18893/favorite/v1/item/create`（`Authorization: Bearer <本地自签 JWT>`）⇒ `favorite-api` → `favorite-rpc` → `user-rpc`：

```json
{"code":5005,"msg":"无权限操作","data":null}
```

> ✅ **业务码随播种变化而变化** ⇒ 证明嵌套调用真的往返了：`user-rpc` 不可达时是 `5001/1015`，`user-rpc` 可达但用户不存在时是 `5101 用户不存在`，**播种 `users_user` 行后**变成 `5005 无权限操作`（推进到权限判定）⇒ **`favorite-rpc` → `user-rpc` 的调用链真实成立**。

**播种数据（本轮为验证链路临时插入，非产品数据）**：
`article` 1 行（`id=9007199254740993` · `status=2`）· `users_user` 1 行（`uid=1001` · `username=round1_user`）。

> ⚠️ **已知边界（不是缺陷）**：对**只有 `article` 行、没有 `article_revision` 指针**的行，单篇详情 `GET /v1/article/{id}` 返回 `5001` ——
> 依据 `ArticlePublicDomainService` 的 legacy 分支：**无 revision ⇒ 正文回落到原 MinIO 对象** ⇒ 手工播种的行没有 MinIO 对象 ⇒ 读失败；
> 而**列表路径不读正文** ⇒ 正常返回（上面的链路 A 即走列表路径）。⇒ **这是播种形态所致，不是起栈缺陷**。

### §7.4 本轮踩到的 4 类问题（**逐条定性** —— 三类环境层、一类配置默认值层）

| # | 现象 | 根因（证据） | 定性 | 本轮处置 |
|---|---|---|---|---|
| ① | **10 个服务注册到了「另一个 Nacos」** ⇒ `Nacos 服务名可查` 判据全红 | 它们的 `server-addr` 是**融合写法** `${NACOS_ADDR:nacos:8848}` ⇒ **忽略 `NACOS_PORT`** ⇒ `NACOS_ADDR=127.0.0.1` ⇒ 连到默认端口 **8848**（= CI 的 `nacos-standalone`）；实测：`Sea-nacos`(49848) 名单 10 个 · `nacos-standalone`(8848) 名单 11 个 | 🔴 **A 类 · 配置默认值缺陷**（**不是环境层**）⇒ 属「同一个 env 变量名有两种互不兼容的格式」⇒ 换环境时静默连错注册中心 | 本轮用 per-process `NACOS_ADDR=127.0.0.1:49848` 覆盖这 10 个 ⇒ 20 个服务归一到同一 Nacos；**yaml 未改** ⇒ 按「本轮不修产品代码」纪律 ⇒ **登记为 CT-02 新问题**，留待统一 |
| ② | `favorite-rpc` 起不来：`Failed to bind to address /0.0.0.0:8082` | `lsof` ⇒ `8082` 被**外部 Python 进程（pid 9391）**占着（本机既有服务，非本仓） | 🟢 **环境层 · 端口占用型假红**（`PIT-GLOBAL-038`）⇒ 非代码问题 | 本轮 `FAVORITE_RPC_PORT=8087`（env 覆盖）⇒ 正是端口 env 化的用途；**默认值仍是 8082** |
| ③ | `favorite-rpc` 起不来：`FATAL: sorry, too many clients already` | 16 个服务同时起 ⇒ 各 rpc 的 Hikari 池下限 5~50 ⇒ 合计 > PG 默认 `max_connections=100` ⇒ 活库实测 `show max_connections` = **100** | 🟢 **环境层 · 容量不足**（PG 默认值 vs 本仓池参数）⇒ 非代码问题 | 本轮 `ALTER SYSTEM SET max_connections = 500` + 重启 PG（**环境侧**，可回滚）⇒ 20 个服务同起稳定 |
| ④ | `task-api` 起不来：`Failed to create channel: task-rpc` ⇒ `Invalid URI syntax: Illegal character in scheme name at index 0: 127.0.0.1:9005` | `task-api` 的 `grpc.client.task-rpc.address` 默认值 `${TASK_RPC_ADDRESS:127.0.0.1:9005}` **缺 scheme** ⇒ devh 要求 `static://` / `discovery://`（仓内所有探针都是 `static://127.0.0.1:<port>`） | 🔴 **A 类 · 配置默认值缺陷**（默认值本身不可用 ⇒ 只在显式给 env 时能起） | 本轮 `TASK_RPC_ADDRESS=static://127.0.0.1:9005`（**该 yml 注释本身就写着「切服务发现只改环境变量」** ⇒ 未改 yaml）⇒ **登记为 CT-02 新问题** |
| ⑤ | `message-rpc` 重启时起不来：`Web server failed to start. Port 16067 was already in use` | 旧进程未退净就起新进程 ⇒ socket 未释放 | 🟢 **环境层 · 端口占用型假红** | 按「轮询到端口空再起」重启即过 |

> ⚠️ **顺带拿到一条 P0 #8 的正面证据**：`user-api` 因我脚本里 `&` 把 env 注入吞掉而缺 `USER_JWT_SECRET` ⇒
> **启动直接失败**（`user-api.user-auth.access-secret 不能为空`）⇒ **fail-closed 密钥守卫在真实起栈中确实生效**（不是「注解存在」）。

### §7.5 结束检查（对照 `round-01/README.md` §5.6）

- [x] **P1-T01** 起栈依赖表 = 20 行（§1）+ 硬编码端口小表（§2）
- [x] **P1-T02** 宿主机模板 `.env.host.example` + compose 零破坏（`docker compose config` EXIT=0）＋ fail-closed 仍生效（缺变量 ⇒ **EXIT=1**）
- [x] **P1-T03** 硬编码端口 = **0**（`HARDCODED_COUNT 0`）· **分配层**（`.env` / `.env.host`）**29 个端口变量取值两两不同**（`ALLOC_DUP_COUNT 0`）· **默认值层**逐条 = Go 真源值（`DEFAULT_GO_MISMATCH_COUNT 0` · 🟢 Round 7 已回改）· 三处端口表（yml 默认 / `.env*` / README）逐条一致
- [x] **P1-T04** README §三 可照做（两段部署形态 + 可复制命令块）· 端口表重写 · 旧项目名 **0 命中** · 链接门/密钥门 EXIT=0
- [x] **P1-T05** **20/20 在服务**（三判据，`EXIT=0`）＋ 两条跨服务链路通（§7.3）
- [x] 证据路径：实测结果落在本文件 §7
- [x] 原始编号 → 最新状态对应：`P0 #5` / `X-12` ⇒ **待 Round 3 改判**（本轮只产出证据，**未改 judge**）
- [x] **未完成任务及原因**：**无**（20/20 达成）；**但新增 2 条 A 类缺陷**（§7.4 ①④）**未在本轮修**（纪律：走 CT-02，不在本轮改产品代码）
- [x] 被阻塞的后续任务：`Round 2` 的 E65 **验收**（需真栈 ⇒ **现已具备**）· `P3-T02`（需活体证据 ⇒ **现已具备**）
- [x] 本轮新增问题及阶段一分类：见 §7.4 ①②（**A 类 · 可构建性**）⇒ **登记入台账 + CT-02**
- [x] **是否满足进入 Round 2 的条件**：**是**（「20/20 在服务」成立）⇒ Round 2 的**验收段**（`P2-T05` / `P2-T06`）可开
- [x] **本轮禁止项自查**：未改 Go 真源 · 未改 `service/common/**` · 未改 `PhaseGoal-judge.md` · **未顺手修起栈时发现的代码问题**（②③④⑤ 全为环境层或走 CT-02）· 未改 `tools/difftest/` 的守门清单计数口径

### §7.6 🟢 【2026-11-01 · Round 7 订正】Round 1 落码引入了 **6 处默认值漂移** ⇒ 已按 Go 值回改

> **发现路径**：Round 7 的**全量回归**（`mvn -o -B -DargLine=-Xmx2g clean test`）在 `hot-rpc` 处 **BUILD FAILURE** ⇒ `HotRpcPrometheusCarrierGateTest.shippedJarKeepsGoObservationPortAndSeparatesFaces` **精确转红** ⇒ ⇒ **是既有守门抓到的，不是新写的判据**。

**① 失败原文**

```
AssertionFailedError: `grpc.server.port` 必须 = 8083（= Go `etc/hot.yaml:2` 的 `ListenOn`），
实际 = ${HOT_RPC_GRPC_PORT:8084} ==> expected: <8083> but was: <8084>
  at ...HotRpcPrometheusCarrierGateTest.shippedJarKeepsGoObservationPortAndSeparatesFaces(:259)
```

**② 根因** —— Round 1（`1fa8a080`）为解端口冲突，**改了 6 个 yml 默认值**；而**默认值层是对外契约面**（Go 侧 `ListenOn` / `Port`），既有门禁钉的就是它：

| 模块 | Go 真源值 | 落码时（`1fa8a080`） | 违反的约束 |
|---|---|---|---|
| `admin-api` | 8889 | `ADMIN_API_PORT:8890` | yml 注释原文：「⚠️ 默认端口**一字不变**（8889）」 |
| `comment-rpc` | 9001 | `COMMENT_RPC_PORT:9002` | yml 注释原文：「对应 Go etc/comment.yaml `ListenOn: 0.0.0.0:9001`」 |
| `follow-rpc` | 8082 | `FOLLOW_RPC_PORT:8083` | yml 注释原文：「⚠️ 默认端口**一字不变**（Go follow.yaml:2 `ListenOn: 0.0.0.0:8082`）」 |
| `hot-rpc` | 8083 | `HOT_RPC_GRPC_PORT:8084` | 门禁 `HotRpcPrometheusCarrierGateTest:259` |
| `like-rpc` | 8082 | `LIKE_RPC_PORT:8085` | yml 注释原文：「对应 Go ListenOn: 0.0.0.0:8082」 |
| `message-rpc` | 8082 | `MESSAGE_RPC_PORT:8086` | yml 注释原文：「对应 Go ListenOn: 0.0.0.0:8082」 |

> ⚠️ **落码偏离了自己的工作单** —— 本文件 §2 的「建议 env 化」列**每一处都写着「默认值保持不变」** ⇒ **工作单是对的，落码偏了** ⇒ 回改等于**回到工作单**。
> ⚠️ **其中 5 处当时**没有**等价门禁**（只有 `hot-rpc` / `favorite-rpc` / `article-rpc` 三个 rpc 有 `*PrometheusCarrierGateTest` 钉业务口）⇒ ⇒ **只有 hot-rpc 这一处会红**；另 5 处是**静默漂移**（已登记，见 §7.6 ④）。

**③ 修法（两层分工）** —— **默认值层 = Go 真源值（一字不差）**；**互异性由分配层（`.env` / `.env.host`）承担** ⇒ 回改 **6 行**（`:223` / `:154` / `:220` / `:307` / `:219` / `:65`），**env 化本身全部保留** ⇒ 硬编码端口仍为 **0**、分配层仍 **30 个监听口两两不同** ⇒ **20/20 在服务不受影响**（§7.2 的 20/20 是**按 env 分配**跑的）。

**④ 为什么 6 处默认值重复是合法的** —— Go 侧 **favorite/follow/like/message-rpc 四个都监听 `8082`**、**article/comment-rpc 都监听 `9001`**、**article/admin-api 都监听 `8889`**（**同一批端口在 Go 侧本就重复** ⇒ Go 是「一服务一部署」）⇒ ⇒ 「一机多进程」的互异性**只能由部署层提供**，**不能由默认值提供** ⇒ 改默认值 = 改对外契约面。

**⑤ 验证**

- `HotRpcPrometheusCarrierGateTest` ⇒ **转绿**（`mvn -o -B -pl service/hot/hot-rpc -am -Dtest='HotRpcPrometheusCarrierGateTest' -Dsurefire.failIfNoSpecifiedTests=false test` ⇒ **EXIT=0**）
- 全量回归重跑 ⇒ 见 §7.6 末尾「回归读数」

**⑥ 遗留（登记，不在本轮扩范围）** —— 另外 **5 处**默认值漂移**当时无等价门禁** ⇒ ⇒ **「默认值 = Go 值」这一不变量目前只有 3/9 的 rpc 有机器守门** ⇒ 登记为**加固项**（见 §7.6 ⑦）。

### §7.6′ 🟢 【2026-11-01 · Round 7 续跑发现】Round 1 的 **env 化**把 `favorite-rpc` 的**既有门禁**打红了

> **发现路径**：回改 6 个默认值后**续跑**（`mvn -o -B -DargLine=-Xmx2g test -rf :favorite-rpc`）⇒ ⇒ `favorite-rpc` 处 **BUILD FAILURE** ⇒ `FavoriteRpcProductionStartupTest.observationPortIsSeparatedFromBusinessPort` 转红。

**① 失败原文**

```
AssertionFailedError: `grpc.server.port` 必须 = 8082（= Go `etc/favorite.yaml:2` 的 `ListenOn`），
实际 = ${FAVORITE_RPC_PORT:8082} ==> expected: <true> but was: <false>
  at ...FavoriteRpcProductionStartupTest.observationPortIsSeparatedFromBusinessPort(:122)
```

**② 根因** —— **不是默认值漂移，是门禁的解析器读不懂占位形态**：`FavoriteRpcProductionStartupTest.java:151` 的 `stripPortNumber` 是**旧拷贝**（只做 `raw.lastIndexOf(':')`）⇒ 对 `${FAVORITE_RPC_PORT:8082}` 返回 **`"8082}"`**（带右花括号）⇒ ⇒ **Round 1 把 `port: 8082` 改成 `port: ${FAVORITE_RPC_PORT:8082}`（`1fa8a080`）后，这个断言必然不等** ⇒ **值是对的（8082 = Go 的 ListenOn）**。

**③ 为什么是「遗漏」不是「设计」** —— 其余三份同族 helper（`article-rpc` · `favorite-rpc` 的 `PrometheusCarrierGateTest` · `hot-rpc` 的 `PrometheusCarrierGateTest`）**都**处理占位形态；且这一份自己的 javadoc 写着「**从 `${ENV:default}` 形态里取出默认端口**（env 覆盖是既有约定）」⇒ **实现与自身声明不符**。

**④ 修法与验证**

| 项 | 读数 |
|---|---|
| 修法 | 改成与其余三份**逐字一致**的实现（**断言语义不变** = 仍要求「默认值一字不差 = Go 的 `ListenOn`」） |
| 修后 | `FavoriteRpcProductionStartupTest` ⇒ **4 条 / 0F / 0E / EXIT=0** |
| **变异必红** | 把默认值改成 `8083` ⇒ **精确转红**（`observationPortIsSeparatedFromBusinessPort:122`）⇒ **判据有牙** |
| 还原 | 变异逐字节还原（`git diff` 该 yml **为空**） |

**⑤ 与 §7.6 的关系** —— **同一个根因的两面**：§7.6 修的是「**改错了**」（默认值偏离 Go）；这里修的是「**门禁读不懂**」（解析器落后于 env 化）⇒ ⇒ **Round 1 的端口 env 化一共留下 2 处缺陷**（一处改错、一处打红既有门禁）⇒ ⇒ **两处都只有全量回归能抓到**（Round 1 的自查只跑了 12 道**静态/机读门** ⇒ **一次都没跑过受影响模块的测试**）。⇒ 登记 `R7-PORT-003`（**已闭合**）。

> ⚠️ **教训（可复用）**：**「端口 env 化」这类改动，改的是「默认值」还是「分配值」必须先分清** —— 前者属**对外契约面**（Go 侧逐字对齐 + 有门禁钉住）⇒ **只能改 env 键名，不能改默认值**；后者属**部署面** ⇒ 可以改。⇒ **本仓的判据原文已经写死了这条**（`HotRpcPrometheusCarrierGateTest`：「允许用 env 覆盖，但**默认值**必须一字不差」）⇒ ⇒ **只要先跑一遍全量回归就能抓到** —— ⚠️ **本轮抓晚了**：Round 1 的自查只跑了 **12 道静态/机读门**，**没跑全量回归** ⇒ ⇒ **「自查门全绿」≠「全量回归全绿」**（§7.4 的 4 类问题都是起栈时发现的 ⇒ **回归面才是唯一能抓到「默认值漂移」的面**）。

---

## §8 复现入口

```bash
cd "/Users/pr/Documents/java&golang/RAG"
# ① 端口**两层体检**（§3.2 的两层分工）
#    层①「默认值层」= Go 真源值（一字不差）· 层②「分配层」= env 里的互异分配
python3 - <<'PY'
import re,collections,pathlib
# Go 真源值（go-zero etc yaml 的 ListenOn / Port）
GO={'ARTICLE_API_PORT':8889,'COMMENT_API_PORT':8888,'FAVORITE_API_PORT':8890,'FOLLOW_API_PORT':8891,
    'HOT_API_PORT':8893,'LIKE_API_PORT':18887,'MESSAGE_API_PORT':8892,'TASK_API_PORT':18886,
    'ADMIN_API_PORT':8889,'USER_API_PORT':8888,
    'ARTICLE_RPC_PORT':9001,'COMMENT_RPC_PORT':9001,'FAVORITE_RPC_PORT':8082,'FOLLOW_RPC_PORT':8082,
    'HOT_RPC_GRPC_PORT':8083,'LIKE_RPC_PORT':8082,'MESSAGE_RPC_PORT':8082,'TASK_RPC_PORT':9005,
    'ADMIN_RPC_PORT':8081,'USER_RPC_PORT':9004}
# RAG 侧**预存**偏离（非 Round 1 引入 · 已登记 · 不在本次口径内）
KNOWN_DEVIATION={'FAVORITE_API_PORT','HOT_API_PORT','USER_API_PORT','USER_RPC_PORT'}
found={}; hardcoded=[]
for p in sorted(pathlib.Path('service').glob('**/src/main/resources/application.y*ml')):
    for i,l in enumerate(p.read_text(errors='replace').splitlines(),1):
        m=re.search(r'^\s+port:\s*\$\{([A-Z_]+):(\d+)\}\s*$',l)
        if m: found[m.group(1)]=(int(m.group(2)),f'{p}:{i}')
        elif re.match(r'^\s*port:\s*\d+\s*$',l): hardcoded.append(f'{p}:{i} {l.strip()}')
for h in hardcoded: print('HARDCODED',h)
print('HARDCODED_COUNT',len(hardcoded))
# 层①：默认值 = Go 值
mis=[]
for var,go in GO.items():
    if var not in found: print('MISSING_VAR',var); continue
    got,loc=found[var]
    if got!=go:
        tag='KNOWN_DEVIATION' if var in KNOWN_DEVIATION else 'GO_MISMATCH'
        (mis if tag=='GO_MISMATCH' else []).append(f'{var} 默认={got} Go={go} @ {loc}')
        print(tag,f'{var} 默认={got} Go={go} @ {loc}')
print('DEFAULT_GO_MISMATCH_COUNT',len(mis))
# 层②：分配层（.env / .env.host）互异
for envf in ('.env.example','.env.host.example'):
    vals=collections.defaultdict(list)
    for l in pathlib.Path(envf).read_text(errors='replace').splitlines():
        m=re.match(r'^([A-Z_]*PORT[A-Z_]*)=(\d+)\s*$',l)
        if m and m.group(1) not in ('POSTGRES_PORT','REDIS_PORT','NACOS_PORT','OTLP_PORT'):
            vals[int(m.group(2))].append(m.group(1))
    dup={p:w for p,w in vals.items() if len(w)>1}
    for p,w in sorted(dup.items()): print('ALLOC_CONFLICT',envf,p,w)
    print(f'{envf} ALLOC_DUP_COUNT',len(dup),'· 端口总数',len(vals))
PY
# ② 中间件宿主地址可达性
curl -s -o /dev/null -w 'nacos=%{http_code}\n' 'http://127.0.0.1:49848/nacos/v1/ns/service/list?pageNo=1&pageSize=1&namespaceId=public'
docker exec Sea-postgres psql -U sea -d sea_user -tAc 'select 1'
```

> ⚠️ **口径提示（防误读）**：**层①出现重复是合法的**（Go 侧本就重复：`8082` × 4 / `9001` × 2 / `8889` × 2）⇒ **不要把「默认值重复」当成冲突**；**冲突判据在层②**（`.env` / `.env.host` 的分配值互异）。
