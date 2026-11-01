# RAG

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
.env.example                   环境变量样例（**compose 容器内语义**）
.env.host.example              环境变量样例（**宿主机起 jar 语义**）
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

> **分配层（env）上 20 个服务的监听口两两不同**（口径与出处：[`docs/RoundPlan/round-01/service-startup-matrix.md`](docs/RoundPlan/round-01/service-startup-matrix.md) §3.2）。
> **全部可用 env 覆盖**（`${VAR:default}`）⇒ 同机并行改 env 即可，**不用改 yaml**。

| 域 | api 端口 | rpc gRPC 端口 | rpc actuator 口 |
|---|---|---|---|
| users / user | 49900 | 49210 | 9094 |
| users / admin | 8890 | 8081 | 9097 |
| article | 8889 | 9001 | 9092 |
| comment | 8888 | 9002 | — （`web-application-type: none` ⇒ **无 actuator 口**） |
| favorite | 18893 | 8082 | 16062 |
| follow | 8891 | 8083 | 16065 |
| hot | 18897 | 8084 | 19091 |
| like | 18887 | 8085 | 9098 |
| message | 8892 | 8086 | 16067 |
| task | 18886 | 9005 | — （**无 `spring-boot-starter-web` ⇒ 不起 HTTP 口**） |

> 🟢 **端口的**两层分工**（2026-11-01 订正）**：**默认值层**（`application.yml` 的 `${VAR:默认值}`）= **Go 真源的值，一字不差**（Go 侧本就重复：`8082` × 4 / `9001` × 2 / `8889` × 2）；**分配层**（`.env` / `.env.host`）= **上表的互异取值** ⇒ **同机并行靠改 env，不靠改默认值**。
> ⚠️ **改端口请改 env，不要改默认值** —— 默认值属**对外契约面**（Go 侧逐字对齐 + 既有门禁钉住，改了会被门禁判红）。

> ✅ **端口已全部 env 化**（Round 1 / `P1-T03`）：`ADMIN_API_PORT` · `ARTICLE_RPC_PORT` · `COMMENT_RPC_PORT` ·
> `FAVORITE_RPC_PORT` · `FOLLOW_RPC_PORT` · `HOT_RPC_GRPC_PORT` · `LIKE_API_PORT` · `LIKE_RPC_PORT` ·
> `MESSAGE_RPC_PORT` · `TASK_RPC_PORT` · `TASK_RPC_MANAGEMENT_PORT` + 各域 `*_API_PORT` / `*_RPC_PORT`。
> ⚠️ `article-rpc` 的 `9001` 是**默认值**且被 article 对拍探针依赖 ⇒ **改端口请改 env，不要改默认值**。

### 全部 70 个变量

见 [`.env.example`](.env.example)（含每个变量的默认值与是否必填）。

---

## 三、怎么起其余 9 个域（宿主机 jar）

root compose 只编排 **users 域**（infra + user-rpc + user-api）。其余 9 个域**用 jar 直接起**，
基础设施复用同一套 PG / Redis / Nacos / Kafka。

> ⚠️ **两份 env 模板**（变量名完全一致 ⇒ 换部署形态只换 env 文件，**代码零改动**）：
> | 部署形态 | 用哪份 | 地址语义 |
> |---|---|---|
> | compose（容器内） | `.env`（由 `.env.example` 复制） | **容器内 DNS 名**（`postgres` / `redis` / `nacos` …） |
> | 宿主机 `java -jar` | `.env.host`（由 `.env.host.example` 复制） | **`127.0.0.1` + 宿主映射端口**（`49432` / `49380` / `49848` …） |

### 三步（可逐行复制）

```bash
# ── 1) 造宿主机 env（密钥不要用默认值 —— 缺值/空值都故意让启动失败）──────────
cp .env.host.example .env.host
$EDITOR .env.host        # 至少改这 4 个：USER_JWT_SECRET / ADMIN_JWT_SECRET /
                          #               FAVORITE_JWT_SECRET / POSTGRES_PASSWORD
                          # 生成密钥：openssl rand -hex 32
                          # ⚠️ POSTGRES_USER / POSTGRES_DB 必须与 .env 一致（compose 按 .env 建库）

# ── 2) 打 20 个可执行 jar（spring-boot-maven-plugin ⇒ jar 可直接 java -jar）──
mvn -o -B -DskipTests clean package

# ── 3) 按「rpc 先、api 后」的依赖顺序起进程（被调方必须先注册进 Nacos）──────
set -a && . ./.env.host && set +a

# 3a) 先起 10 个 rpc（各自会用 Flyway 自建表，账本表名互不相同 ⇒ 同库共存不撞号）
for d in users/user:user-rpc users/admin:admin-rpc message:message-rpc \
         article:article-rpc comment:comment-rpc like:like-rpc \
         favorite:favorite-rpc follow:follow-rpc task:task-rpc hot:hot-rpc; do
  domain=${d%%:*}; svc=${d##*:}
  mod="service/${domain%%/*}/${svc}"
  [ -d "$mod" ] || mod="service/$domain/$svc"
  nohup java -jar "$mod/target/$svc-0.0.1-SNAPSHOT.jar" >"/tmp/$svc.log" 2>&1 &
  echo "started $svc (pid $!)"
  sleep 5
done

# 3b) 再起 10 个 api
for d in users/user:user-api users/admin:admin-api message:message-api \
         article:article-api comment:comment-api like:like-api \
         favorite:favorite-api follow:follow-api task:task-api hot:hot-api; do
  domain=${d%%:*}; svc=${d##*:}
  mod="service/${domain%%/*}/${svc}"
  [ -d "$mod" ] || mod="service/$domain/$svc"
  nohup java -jar "$mod/target/$svc-0.0.1-SNAPSHOT.jar" >"/tmp/$svc.log" 2>&1 &
  echo "started $svc (pid $!)"
  sleep 3
done
```

> ⚠️ **`user-rpc` / `user-api` 想改成宿主机跑** ⇒ 先 `./manage.sh stop`（或 `docker compose stop user-rpc user-api`），
> 否则 **49900 / 49210 已被 compose 容器占着** ⇒ 起不来。
> ⚠️ **Nacos namespace 必须统一**：`users` 域的 yml 默认是 `sea-rtw`/`USER_GROUP`，其余 9 域是 `public`/`DEFAULT_GROUP`
> ⇒ 宿主机跑全部 20 个服务时，`.env.host` 里**必须**是 `NACOS_NAMESPACE=public` + `NACOS_GROUP=DEFAULT_GROUP`
> ⇒ 否则跨 users 域的 `discovery://` 解析不到（`user-rpc` 注册在 `sea-rtw` ⇒ `article-api` 找不到它）。

### 验证「在服务」（三个判据同时成立）

```bash
bash docs/RoundPlan/round-01/verify-all-services.sh
# 逐个核：① Nacos 服务名可查 ② 端口 LISTEN ③ /actuator/health = 200
# （comment-rpc 是 web-application-type: none ⇒ 无 ③，判据降为 ①②）
```

**起 rpc 之前**：`service/<域>/<域>-rpc` 会用 Flyway **自建表** ⇒ 同一个库里每个模块用**独立账本表**
（`spring.flyway.table: flyway_schema_history_<模块>`）⇒ 多模块同库共存不撞号。

**必需的中间件**（本地已有 `Sea-*` 容器在跑）：Postgres · Redis · Nacos（服务发现）· Kafka（事件总线）· Jaeger（可选）。
⚠️ **Kafka / MinIO / etcd 不在本仓的 compose 里** ⇒ 用兄弟仓 `sea-ridethewind_java` 的同名容器
（宿主 `49992` / `49100`）⇒ 本机没有时要自己起。

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
| 1 | root compose **只编排 users 域** | 其余 9 域按「三、怎么起其余 9 个域」用宿主机 jar 起（**命令块可逐行复制**） |
| 2 | Kafka / MinIO / etcd **不在本仓 compose 里** | 用兄弟仓 `sea-ridethewind_java` 的同名容器（宿主 `49992` / `49100`）⇒ 本机没有要自己起 |
| 3 | users 域与其余 9 域的 **Nacos namespace 默认值不同**（`sea-rtw` vs `public`） | 宿主机跑全部 20 个服务时**必须统一**成 `public`/`DEFAULT_GROUP`（`.env.host` 已写死） |
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
| 端口起不来 `address already in use` | compose 的 `user-rpc` / `user-api` 与宿主机 jar 撞了（49900 / 49210）⇒ `./manage.sh stop`；或改 env 端口 |
| 服务起来了但查不到下游 | Nacos 服务发现没注册上 ⇒ 看 Nacos 控制台 / 该 rpc 的 `NACOS_NAMESPACE`（users 域默认 `sea-rtw`，其余域 `public` ⇒ 必须统一） |
| 端口在容器里通、宿主不通 | `docker exec` 走容器内 socket，与 `-p` 发布**无关** ⇒ 就绪判据要看**宿主端口** |
