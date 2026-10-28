# Sea-RideTheWind_java

> **Sea-RideTheWind（Go 真源）的 Spring Boot 重构产物。**
> 行为以 Go 真源为准，表达以 Spring 生态为准。
> 真理之源：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（**冻结、只读**）。

本仓把一个 **go-zero + GORM** 的商用架构，用 **Spring Boot 3 + MyBatis-Plus** 重写一遍：
**10 个业务域 / 38 个组件**（api + rpc + common + proto），JWT 鉴权、gRPC 服务发现（Nacos）、
Kafka 事件总线、PG 行级悲观锁与事务边界、OpenTelemetry 链路追踪、Prometheus 指标 —— 全部对着 Go 侧逐字对齐。

---

## 〇、5 分钟跑起来（users 域，root compose 全编排）

### 依赖

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | **21** | `pom.xml` 的 `java.version=21`；Spring Boot 3.x 最低 17 |
| Maven | **3.9+** | 仓库自带 `./mvnw`（Maven 3.9.x）⇒ 不装也行 |
| Docker | **24+** | 要带 Compose v2（`docker compose`，不是 `docker-compose`） |
| Go | 1.25+ | **只有跑真源对拍探针才需要** ⇒ 只跑 RAG 不需要 |

### 三步

```bash
# 1) 造 .env（密钥不要用默认值 —— 缺值/空值都故意让启动失败）
cp .env.example .env
$EDITOR .env            # 至少改这 4 个：USER_JWT_SECRET / ADMIN_JWT_SECRET /
                         #               FAVORITE_JWT_SECRET / POSTGRES_PASSWORD
                         # 生成密钥：openssl rand -hex 32

# 2) 起栈（infra + user-rpc + user-api；jar 在宿主机上用 mvn 打）
./manage.sh up

# 3) 看状态
./manage.sh status
./manage.sh logs user-api
```

`./manage.sh` 的子命令：`up` · `down` · `restart` · `logs [svc]` · `status` · `build` · `clean`。

### 起起来之后

| 入口 | 地址 |
|---|---|
| user-api HTTP | `POST http://localhost:49900/user/v1/login` |
| user-rpc gRPC | `localhost:49210`（服务发现名 `discovery://user-rpc`） |
| Nacos 控制台 | <http://localhost:49848/nacos> |
| Jaeger UI | <http://localhost:49686> |
| Prometheus | <http://localhost:49090> |
| Postgres | `localhost:49432`（user=`admin` / db=`first_db`） |
| Redis | `localhost:49380` |

> ⚠️ **root `docker-compose.yaml` 只编排 users 域**（infra + user-rpc + user-api）。
> 其余 9 个域见「三、怎么起其余 9 个域」。
> ⚠️ compose 用**外部网络** `Sea-TryGo`（`external: true`）—— 基础设施容器（`Sea-postgres` / `Sea-redis` / `Sea-nacos` …）实测就挂在它上面
> ⇒ 换机器时先 `docker network create Sea-TryGo`，或用 `.env` 的 `DOCKER_NETWORK` 指到你已有的网络。

---

## 一、目录结构

```
service/                       业务代码（10 个域）
  users/  user/  admin/        users 域：user-api/user-rpc/user-proto · admin-api/admin-rpc/admin-proto
  users/users-common/          共享库（6 个外域都在用 ⇒ 写权唯一）
  article/ comment/ favorite/ follow/ hot/ like/ message/ task/
      <域>-api/ <域>-rpc/ <域>-common/ <域>-proto/
  common/                      共享层（logger / response / snowflake / observability / protection …）
docs/                          验收与漂移台账（DRIFT / Intentional_Deviations / Pitfalls / PhaseGoal）
tools/                         对拍探针 + 机器守门（difftest / semantic-probe / errmsg-probe / …）
docker-compose.yaml            users 域编排
manage.sh                      users 域生命周期封装
.env.example                   环境变量样例（本文件）
```

---

## 二、配置项（`.env`）

### 🔴 必填 —— 缺了就起不来（这是设计意图）

| 变量 | 谁用 | 说明 |
|---|---|---|
| `USER_JWT_SECRET` | user-api/user-rpc **签发**；article/comment/follow/like/message-api **验签** | ≥32 字节。**签发方与验签方必须一致**，否则验签失败（HTTP 401 + 空 body） |
| `ADMIN_JWT_SECRET` | admin-api / admin-rpc | 管理域独立密钥 |
| `FAVORITE_JWT_SECRET` | favorite-api | 与 `USER_JWT_SECRET` 同值 |
| `POSTGRES_PASSWORD` | 全部 rpc + compose | compose 用 `:?` ⇒ 缺值**拒绝渲染** |

> ⚠️ **Docker Compose 与 Spring 的占位符语法不同，千万别混**：
> Compose 的 `${VAR:?msg}` = 缺值**报错退出**；
> Spring 的 `${VAR:?msg}` = 缺值取**字面量 `?msg`**（用它反而更危险）。
> 本仓 Spring 侧一律用 `${VAR}`（无默认值 ⇒ 绑定期炸）**＋ `@PostConstruct` 兜空串**
> —— 因为 Spring 对「属性存在但值为空串」会解析成空串（**不走默认值也不报错**）⇒ 空密钥必须由代码堵。

### 建议覆盖的部署凭据（默认值在仓里是公开的）

`MINIO_ACCESS_KEY` · `MINIO_SECRET_KEY`（默认 `minioadmin`）· `LIKE_FACT_AUTHORITY_TOKEN` ·
`COMMENT_FACT_AUTHORITY_TOKEN` · `FAVORITE_AUTHORITY_TOKEN` · `DC_PLATFORM_SERVICE_TOKEN` ·
`ADMIN_DEFAULT_PASSWORD`（默认 `123456`）· `REDIS_PASSWORD` · `NACOS_USERNAME` / `NACOS_PASSWORD`

### 连接与端口

| 域 | api 端口 | rpc 端口 |
|---|---|---|
| users / user | 49900 | 49210 (gRPC) |
| users / admin | 8889 | 8081 (gRPC) |
| article | 8889 | 9001 (gRPC) |
| comment | 8888 | 9001 (gRPC) |
| favorite | 18893 | 8082 (gRPC) |
| follow | 8891 | 8082 (gRPC) |
| hot | 18897 | 8083 (gRPC) |
| like | 18887 | 8082 (gRPC) |
| message | 8892 | 8082 (gRPC) |
| task | 18886 | 9005 (gRPC) |

> ⚠️ **多个 rpc 默认都落在 8082** ⇒ 同机并行时**必须错开**（`POSTGRES_*` / `*_RPC_PORT` / `KAFKA_BROKERS` 都要改）。
> ⚠️ `article-rpc` 的 `grpc.server.port` 在 yaml 里是**硬编码 9001**、**不可用 env 覆盖** ⇒ 错端口只能改 yaml。

### 全部 53 个变量

见 [`.env.example`](.env.example)（含每个变量的默认值与是否必填）。

---

## 三、怎么起其余 9 个域

root compose 只编排 users 域。其余域**用 jar 直接起**（基础设施复用同一个 PG / Redis / Nacos / Kafka）：

```bash
# 1) 打可执行 jar（spring-boot-maven-plugin ⇒ jar 可直接 java -jar）
mvn -o -B -DskipTests -pl service/<域>/<域>-api -am package

# 2) 用 .env 里的变量起进程
set -a && . ./.env && set +a
java -jar service/<域>/<域>-api/target/<域>-api-0.0.1-SNAPSHOT.jar

# 或者开发时直接跑：
mvn -o -pl service/<域>/<域>-api spring-boot:run
```

**起 rpc 之前**：`service/<域>/<域>-rpc` 会用 Flyway **自建表** ⇒ 同一个库里每个模块用**独立账本表**
（`spring.flyway.table: flyway_schema_history_<模块>`）⇒ 多模块同库共存不撞号。

**必需的中间件**（本地已有 `Sea-*` 容器在跑）：Postgres · Redis · Nacos（服务发现）· Kafka（事件总线）· Jaeger（可选）。

---

## 四、构建与测试

```bash
# 全量回归（**必须带 clean** ⇒ 否则 stale 产物会同时伪造「红」与「条数」）
mvn -o -B -DargLine=-Xmx2g clean test

# 单模块
mvn -o -B -pl service/like/like-api -am -DargLine=-Xmx2g test

# 全量打包
mvn -o -B -DskipTests clean package
```

### 机器守门（改完代码跑一遍）

```bash
for g in tools/difftest/check-*.py tools/check-ledger-*.py tools/check-agents-budget.py; do
  out=$(python3 "$g" >&2); rc=$?
  printf "%-52s EXIT=%s\n" "$(basename "$g")" "$rc"
done
python3 tools/check-doc-links.py
python3 tools/g3-anchor-check/check-anchors.py --all   # Go 出处引用核验
```

### 双侧活体对拍（Go 真源 vs RAG，两侧同时起 ⇒ 逐字节比对）

```bash
bash tools/api-full-parity-probe/article/run.sh          # 样例：article 域
bash tools/errmsg-probe/run-parity.sh                    # 错误码契约
bash tools/semantic-probe/run-parity.sh                  # 解析语义
bash tools/difftest/run.sh                               # users 域
```

> ⚠️ 对拍探针**必须落仓在 `tools/<kind>/<域>/`** ⇒ `/tmp` 里的脚本**不算证据**。
> ⚠️ **对拍前先清状态** ⇒ 状态没清会把「已签字的偏离」当成新漂移。

---

## 五、已知限制（据实登记，不藏）

| # | 限制 | 状态 |
|---|---|---|
| 1 | root compose **只编排 users 域** | 其余 9 域按「三、怎么起其余 9 个域」手工起 |
| 2 | `article-rpc` 的 gRPC 端口 **9001 硬编码**，不可 env 覆盖 | 错端口只能改 yaml |
| 3 | 多个 rpc 默认端口都落在 **8082** | 同机并行必须错开 |
| 4 | Go 侧把 JWT 密钥**写成字面量**并公开在仓里 | RAG 侧**故意更严**（缺变量 ⇒ 启动失败）⇒ 属**有意偏离**，不是抄错 |
| 5 | 其余 8 个域的**部署凭据默认值**仍在 yaml 里（`MINIO_*` / `*_TOKEN` / `ADMIN_DEFAULT_PASSWORD`） | 已登记 ⇒ 生产必须覆盖（见 `.env.example`） |

> 📖 完整的漂移台账、**已签字的有意偏离**、踩坑档：
> [`docs/DRIFT/_INDEX.md`](docs/DRIFT/_INDEX.md) · [`docs/Intentional_Deviations/_INDEX.md`](docs/Intentional_Deviations/_INDEX.md) · [`docs/Pitfalls/_INDEX.md`](docs/Pitfalls/_INDEX.md)
> 📖 阶段一验收基线：[`docs/PhaseGoal-judge.md`](docs/PhaseGoal-judge.md)

---

## 六、排障

| 症状 | 大概率原因 |
|---|---|
| 启动就报 `access-secret 不能为空` | 变量没设 / 设成空串 ⇒ 查 `.env` 里 `*_JWT_SECRET` |
| compose 报 `required variable USER_JWT_SECRET is missing` | `.env` 里没这个键，或值是空的 ⇒ compose 的 `:?` 在工作 |
| 验签一律 401 + 空 body | **签发方与验签方的密钥不一致**（`USER_JWT_SECRET` ≠ `FAVORITE_JWT_SECRET` 之类） |
| 端口起不来 `address already in use` | 多个 rpc 默认都是 8082 ⇒ 错开 |
| 服务起来了但查不到下游 | Nacos 服务发现没注册上 ⇒ 看 Nacos 控制台 / 该 rpc 的 `NACOS_NAMESPACE` |
| 端口在容器里通、宿主不通 | `docker exec` 走容器内 socket，与 `-p` 发布**无关** ⇒ 就绪判据要看**宿主端口** |
