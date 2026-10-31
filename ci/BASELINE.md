# RAG · Java 微服务 CI 需求基线

> **以 Go 原版为主要参考**，分析 Java 微服务项目**应该验证什么**，
> 再由 Java 的真实实现决定**怎么验证**。
> 编制日期：**2026-10-31** · 编制范围：Go 原版 `Sea-RideTheWind_golang`（只读，真理之源）+ Java 侧 `RAG` 当前实现。

---

## 〇、本文档的定位与自我约束

### 0.1 它是什么 / 不是什么

| 是 | 不是 |
|---|---|
| **需求基线**：一条条「CI 必须守住什么」的要求 + 每条要求的**接入条件与验收标准** | ❌ 不是 Pipeline 实现（实现见 `ci/DELIVERY.md`） |
| **Go → Java 的映射**：Go 侧有/没有的能力 ⇒ Java 侧该有/该补什么 | ❌ 不是把 Go 的构建命令照抄给 Java |
| **现状盘点**：Java 侧此刻**已具备 / 未具备**什么 | ❌ 不是「Java 已经具备」的自证（未迁移的一律写未迁移） |

### 0.2 证据强度口径（每条断言都标）

| 标记 | 含义 |
|---|---|
| 🟢 **实测** | 本次真的跑过命令 / 数过文件 / 读过源码，附出处 |
| 🟡 **源码级** | 读过源文件得出结论，但**未在活体环境跑过** |
| ⚪ **推断** | 基于结构/命名/规则的推断，**需后续验证** |

### 0.3 本文档明确不做的事（对应任务限制）

- ❌ 不生成 Pipeline YAML（实现阶段的事；本文件只给「粒度依据」与「触发面依据」）
- ❌ 不把 Go 的构建命令当 Java 的构建命令
- ❌ 不按 Go 的目录结构决定 Java 的流水线数量
- ❌ 不假设两侧测试结构一致（🟢 实测：**结论相反**，见 §2.2）
- ❌ 不把未迁移的能力描述成 Java 已具备
- ❌ 不为 CI 修改 Go 或 Java 的业务实现
- ❌ 不涉及 CD / 部署 / 发布

---

## 一、Go 原版：微服务划分与依赖关系（交付 1）

### 1.1 服务清单（🟢 实测）

Go 原版 `service/` 下 13 个目录，其中 **10 个是可运行的应用服务**，3 个是范围外：

| Go 服务 | 形态 | Go 侧目录 | 范围 |
|---|---|---|---|
| article | 应用 | `service/article/{api,common,rpc}` | ✅ 在范围内 |
| comment | 应用 | `service/comment/{api,common,rpc}` | ✅ |
| favorite | 应用 | `service/favorite/{api,common,rpc}` | ✅ |
| follow | 应用 | `service/follow/{api,common,rpc}` | ✅ |
| hot | 应用 | `service/hot/{api,common,heavykeeper,rpc,test}` | ✅ |
| like | 应用 | `service/like/{api,common,rpc}` | ✅ |
| message | 应用 | `service/message/{api,common,rpc}` | ✅ |
| task | 应用 | `service/task/{api,rpc,filebeat.yml}` | ✅ |
| user | 应用 | `service/user/{user,admin,common}`（**user 与 admin 是两个应用**） | ✅ |
| — | 共享层 | `service/common/{communityfact,logger,observability,response,snowflake}` | ✅ 共享层 |
| knowledge | 应用 | `service/knowledge/...` | ❌ **范围外** |
| points | 应用 | `service/points/rpc` | ❌ **范围外** |
| security | 应用 | `service/security/rpc` | ❌ **范围外** |

> 🟢 10 个应用服务的清单与 `service.sh` 的 `APP_SERVICES` 数组**逐条一致**
> （`service.sh:47` 起：article / comment / like / follow / favorite / message / task / user / admin / hot）。
> ⇒ **users 域在 Go 侧本来就是两个进程**（user / admin），Java 侧拆 `user-api` / `admin-api` 是**忠实对应**，不是 Java 自行加的切分。

### 1.2 跨服务调用图（🟢 实测 · 解析 Go 源码 import，排除注释行与测试文件）

```
article   ──▶ message      article   ──▶ security ※范围外
comment   ──▶ article      comment   ──▶ message
favorite  ──▶ article      favorite  ──▶ user/user
follow    ──▶ user/user
like      ──▶ message
message   ──▶ user/admin   message   ──▶ user/user
task      ──▶ points ※范围外
```

**共 11 条真实调用边。**

⚠️ **一个必须写进基线的精度陷阱**：朴素的 `grep 'sea-try-go/service/...'` 会**多算 2 条**——
`task → user` 与 `hot → user` 只出现在**被注释掉的 import**（`//userpb "sea-try-go/service/user/rpc/pb"`）里
⇒ **注释掉的 import 不是依赖** ⇒ 按 grep 结果建触发面 ⇒ 会造出两条永远不该触发的边。

### 1.3 公共组件（🟢 实测）

| 层次 | Go 侧 | 被谁用 | Java 侧对应 |
|---|---|---|---|
| **项目级共享层** | `service/common/`（5 个包：communityfact / logger / observability / response / snowflake） | **全部 10 个应用服务** | `service/common`（单一 Maven 模块） |
| **域内共享件** | `<域>/common/` —— 实际内容**几乎只有 `errmsg.go`**（8 个域）；`user/common` 多了 `cryptx` + `jwt`；`comment/rpc/common/types` | 仅本域 | `<域>-common`（**独立 Maven 模块**）+ `users/users-common` |
| **接口契约** | `proto/*.proto`（12 份） | 跨域共享 | `<域>/<域>-proto`（**独立 Maven 模块**，10 份） |

⇒ **结构性差异（重要）**：Go 的 `common/` 只是**源码目录**，不是独立构建单元；
Java 把它们提升成了**独立 Maven 模块** ⇒ **Java 侧有 Go 侧没有的编译/测试边界**。
⇒ 这就是为什么「Java 的流水线粒度不能由 Go 的目录结构决定」：**粒度依据是 Maven 模块图，不是 Go 目录。**

### 1.4 Proto / API 定义与代码生成产物（🟢 实测）

| 项 | Go 侧 | Java 侧 |
|---|---|---|
| proto 文件 | `proto/` 下 **12 份**（admin / article / comment / favorite / follow / hot / like / message / **points**※ / **security**※ / task / user） | `<域>/<域>-proto/src/main/proto/` 下 **10 份** |
| 生成方式 | `goctl rpc protoc … --go_out --go-grpc_out --zrpc_out --style=go_zero`（见 `proto/readme.md`） | `protobuf-maven-plugin`（`protobuf.version=3.25.5`） |
| 生成产物 | **入库**：`service/*/rpc/pb/*.pb.go` 共 **25 个** | 入库：`<域>-proto` 模块的 Java 产物 |
| HTTP 接口定义 | `api/*.api`（go-zero DSL，**11 份**） | Spring MVC `@RestController`（**11 个类**，分布在 10 个 api 模块）+ **27 个 `@RestControllerAdvice`** |

⚠️ **Go 侧 proto 的两个坑（Java 侧已规避，值得写进基线）**：
1. `points.proto` **一份编到两处**（`service/user/user/rpc/pb/` 与 `service/points/rpc/pb/`）⇒ 同契约双份产物，容易漂移。
2. `security` 的产物落在**异常嵌套路径** `pb/sea-try-go/service/security/rpc/pb/` ⇒ `go_package` 没归一。
   ⇒ Java 侧统一在 `option` 块写 `java_package` + `java_multiple_files` ⇒ 这两条坑不再存在。

---

## 二、Go 原版：现有构建与测试能力清单（交付 2）

### 2.1 构建能力（🟢 实测）

| 维度 | 事实 |
|---|---|
| **CI 配置** | **完全没有** —— 无 `.github/workflows`、无 `.gitlab-ci.yml`、无 `.drone.yml`、无 Jenkinsfile、**无 Makefile** |
| 构建入口 | `service.sh`（bash）+ `manage.sh` + `docker-compose.yaml` + 单个 `dockerfile` |
| 依赖管理 | Go modules（`go.mod`：`module sea-try-go`，`go 1.25.11`）⇒ 单一模块、多包 |
| 打包形态 | `go build` → 独立二进制 → Docker 镜像 → compose 起进程 |
| 基础设施 | compose 起 **18 个基础设施服务**（etcd / postgres / redis / beanstalkd×2 / neo4j / kafka / minio / milvus / prometheus / grafana / jaeger / filebeat …） |
| 配置注入 | 根 `.env`（`service.sh` 的 `load_root_env`）+ 每服务 `etc/*.yaml` |

⇒ **关键结论**：Go 原版**没有 CI 可供"照抄"**。
⇒ 所以「Go 原版值得保留的 CI 要求」**只能从 Go 的构建/测试/接口约定里提取**，而不是从 Go 的 CI 文件里抄 —— 这正是本基线的立足点。

### 2.2 测试能力分布（🟢 实测 · **两侧结论相反**）

| 服务 | Go 测试文件 | Go 源文件 | Java 测试文件 |
|---|---|---|---|
| article | 6 | 51 | 43 |
| comment | 3 | 61 | 39 |
| favorite | **12** | 62 | 51 |
| follow | **0** | 46 | 39 |
| hot | 2 | 28 | 50 |
| like | 4 | 55 | 48 |
| message | **0** | 55 | 51 |
| task | **0** | 26 | 34 |
| user（user+admin） | 3 | 114 | 66 + 57 |
| admin（单列） | **0** | 60 | （同上 57） |
| common（共享层） | 3 | 14 | 103 + users-common 4 |
| knowledge※ | 65 | 276 | — 范围外 |
| **合计（范围内）** | **33**（+7 个孤儿文件） | — | **585** |

🟢 **Go 侧 105 个 `_test.go` 里，65 个在范围外的 `knowledge`；范围内真正挂在服务上的是 33 个**，
另有 **7 个孤儿测试**在 `service/user/rpc/internal/logic/`（全仓无任何引用 ⇒ 见 AGENTS.md §7.3）。

🟢 **Java 侧 585 个测试文件，分布在 30 个模块，14 个模块用 Testcontainers。**

⇒ **两侧测试结构完全不可类比**（范围内 33 : 585 ≈ 1 : 18）。
⇒ **Go 侧的"零测试服务"（message / follow / task / admin）在 Java 侧都有 34~57 个测试文件**
⇒ 基线结论：**Java 的 CI 测试门必须比 Go 严**，且**不能按 Go 的测试分布来配门**。

### 2.3 验收 / 压测能力（🟢 实测）

| 形态 | Go 侧 | Java 侧 |
|---|---|---|
| 负载/压测 | `service/hot/test/cmd/loadtest/loadtest.go`（**唯一一处**，且是 `main` 程序不是 `_test.go` ⇒ **不会被 `go test` 跑到**） | ❌ 无对应 |
| 端到端 | 无独立 E2E 层（靠 `knowledge/api/internal` 的真实 HTTP 测试，但那属范围外） | 有 `*E2ETest` / `*EndToEndTest` 若干（like / message / favorite / common），但**不是"验收套件"形态** |
| 真实 HTTP + fixture | `knowledge/api/internal/testenv` + `knowledge/testdata`（范围外） | ❌ 无 fixture 目录形态 |

⇒ **Go 侧真正的"验收/压测"能力极少，且大部分在范围外** ⇒
   **Java 的验收层在 Go 原版里没有可对照的样板** ⇒ 列入待定（§八·2）。

### 2.4 Go 原版"值得保留的验证要求"（从上述事实提取，7 条）

| # | 要求 | Go 侧依据 | 在 Java 侧对应什么 |
|---|---|---|---|
| G1 | **每个服务能独立构建出可运行产物** | compose 里每个服务一个镜像 | Java = 可执行 fat jar（`Main-Class: JarLauncher`） |
| G2 | **公共层被所有服务共用 ⇒ 它的变更必须波及全部服务** | `service/common` 被 10 个服务 import | Java = `service/common` 被 10 域依赖 |
| G3 | **跨服务只靠 proto 契约** | `rpc/pb/*.pb.go` + 11 条调用边 | Java = `<域>-proto` 模块 |
| G4 | **错误码是跨服务契约的一部分** | 8 份 `errmsg.go` | Java = 8 个 `*ErrorCode` 类 |
| G5 | **schema 真相在模型定义里** | GORM `AutoMigrate(&Model{})`（每个域 `rpc/internal/model/db.go`） | Java = Flyway 37 份 SQL / `SchemaInitializer` |
| G6 | **配置与密钥不进版本库** | 根 `.env` + `etc/*.yaml` | Java = `application.yml` + profile |
| G7 | **基础设施依赖是真实的（PG/Redis/Kafka/Nacos…）** | compose 起 18 个基础设施服务 | Java = Testcontainers 起真容器 |

---

## 三、Java 侧现状盘点（用于判定"接入条件"）

### 3.1 模块与测试（🟢 实测）

- **52 个 pom**（根 + `service` + 10 个域聚合 + 18 个 `*-common`/`*-proto` + 20 个 `*-api`/`*-rpc`）。
- **585 个测试文件**，30 个模块；**14 个模块用 Testcontainers**（users/admin 14、article 14、users/user 13、comment 13、message 12、like 12、favorite 11、task 9、users/user-api 8、admin-api 5、hot 5、follow-rpc 4、follow-api 2、favorite-api 2、common 2）。
- **10 份业务 proto**（Go 12 − points※ − security※ = 10 ✔；其中 Go 的 `user.proto` 在 Java 侧改名 `users.proto`）。
- **8 个 `*ErrorCode` 类**（与 Go 的 8 份 `errmsg.go` 一一对应 ✔）。
- **37 份 Flyway SQL** + `service/users/users-common/.../SchemaInitializer.java`（幂等重放机制）。
- **已有守门资产 100+ 个脚本**在 `tools/difftest/`（`check-*` / `mutate-*` / `probe-*` / `parity-*` / `exp-*`）。
  ⚠️ 其中**绝大多数需要活体 PG / Go 真源仓库 / 真实进程** ⇒ **它们定义的是"未来要满足的条件"，不是"现在就能进 CI 的门"**。

### 3.2 Java 侧跨域依赖图（🟢 实测 · Maven compile/runtime scope，30 条边，去掉 `common` 后 16 条）

```
article      ──▶ message, users/user
comment      ──▶ article, message
favorite     ──▶ article, users/user
follow       ──▶ users/user
hot          ──▶ article
like         ──▶ message
message      ──▶ users/admin, users/user
users/admin  ──▶ users/user
```

### 3.3 两侧调用图对照（🟢 实测 · **差异即待定项**）

| Go 边 | Java 对应 | 判定 |
|---|---|---|
| article→message / comment→article / comment→message / favorite→article / favorite→user / follow→user / like→message / message→admin / message→user | **逐条对应** ✔ | 一致 |
| article→security※ / task→points※ | 无 | **范围外 ⇒ N/A，不是待办** |
| — | **article→users/user** | ⚠️ Java 多一条 ⇒ **行为差异 or 迁移未完成？** |
| — | **hot→article** | ⚠️ Java 多一条 ⇒ 同上 |
| — | **users/admin→users/user** | ⚠️ Java 多一条 ⇒ 同上 |

### 3.4 两侧建表机制（🟢 实测）

| | Go | Java |
|---|---|---|
| 机制 | GORM `AutoMigrate(&Model{})`（启动即建表） | Flyway 版本化 SQL（`db/migration/V*.sql`）+ users-common 的 `SchemaInitializer` 幂等重放 |
| 真相位置 | **Go 源码里的 model struct** | **SQL 文件** |
| ⇒ CI 校验的对象 | "Java 的列 == Go 的 struct 字段" | 同上（列级对照） |

---

## 四、Java CI 验证能力需求清单（交付 3）

> 每条需求给出：**Go 出处（为什么需要它）** / **Java 现状** / **判定标准（怎么算已具备 CI 接入条件）**。
> **粒度标注**：`项目级` = 每个域流水线都要跑；`服务级` = 只在本域跑。

### R1 干净可重复构建（项目级 · 🟢 已有）

- **Go 出处**：Go 侧无制品仓库、依赖走 module cache ⇒ 隐含要求「干净环境可构建」。
- **Java 现状**：52 个 pom，**无 `distributionManagement`** ⇒ 依赖全在 reactor 内 SNAPSHOT
  ⇒ 不带 `-am` 会拿到 `~/.m2` 里的**陈旧 jar**（仓内 `*SharedLayerArtifactFreshnessGateTest` 就在守这条）。
- **判定标准**：**空 `~/.m2` + 干净 checkout** 下 `mvn -pl <域模块> -am -DskipTests package` 成功。
- **已具备？** ✅ 是。⚠️ 但**本 PR 修之前不成立**：`admin-api` 的父 POM `relativePath` 指向坐标不符的文件
  ⇒ 干净环境必炸（开发机被 `~/.m2` 旧产物掩盖）⇒ **这是 CI 的第一个真实收获**。

### R2 单元测试可执行且结论可判定（项目级 · 🟢 已有）

- **Go 出处**：Go 侧**没有全仓测试门**（message/follow/task/admin = 0 个测试文件）⇒ Java 不能继承这个缺口。
- **Java 现状**：585 个测试文件 / 30 个模块。
- **判定标准**：测试结果有**机器可读产物**（surefire `TEST-*.xml`），任一失败/错误 ⇒ 该步红。
- **已具备？** ✅ 是（`ci/scripts/collect-surefire.sh` 已按 XML 统计，不读 `.txt` 摘要 —— 实测二者会不一致）。

### R3 集成测试的活体依赖（服务级 · 🟢 已有 · ⚠️ 有一处 Linux-only 缺陷）

- **Go 出处**：G7 —— 基础设施是真容器。
- **Java 现状**：**126 个测试文件**（`src/test/**` 里引用 testcontainers 的文件数）直接 `new PostgreSQLContainer/GenericContainer` 且**不探测 Docker 可用性**；
  镜像为裸名（`postgres:16-alpine` / `redis:7-alpine` / `apache/kafka:3.7.2`）。
- **判定标准**：CI 提供**Docker API 可达**的守护进程（DinD）+ **固定端口段**（避免与 OS 临时端口段 49152–65535 相撞）
  + 镜像可从镜像源拉到。
- **已具备？** ✅ 是（DinD 已实测：PG 5.21s 起来，JDBC 连到内层 bridge 网关）。
  🔴 **但**：`RealGrpcPort.isFree()` 的「同时持有通配+回环两个 socket」**只在 macOS/BSD 成立**（Linux 必 `EADDRINUSE`）
  ⇒ 波及 **21 个文件 / 9 个域** ⇒ 修好前**除共享层外的域流水线都会在 test 步红**。属各域 lane 的测试代码写权。

### R4 接口契约一致性（项目级 + 服务级 · 🟢 已有 · ⚠️ 消费方式未验）

- **Go 出处**：G3 —— proto 是跨服务唯一契约面；12 份 proto，产物入库。
- **Java 现状**：10 份 proto **逐条复制**自 Go（改 `option` 块 + `user.proto`→`users.proto` 改名 + 注释）
  ⇒ message 名 / 字段名 / 字段号 / 类型 / 枚举值 **10/10 一致**（仓内 `docs/DRIFT/_cross/proto-verification.md`）。
- **判定标准**：① `.proto` 文件与 Go 逐字段一致；② **RAG 侧消费方式**也一致（`optional` presence / 字段号硬编码 / Jackson 差异）。
- **已具备？** 🟡 **部分** —— ① 已验；② **只验了文件本身**，没验消费方式 ⇒ **接入条件 = 「消费方式逐条核过」**。
  ⚠️ 而且 **CI 里目前没有 proto 对照门**（`check-proto-static.sh` 需要 Go 真源仓库 ⇒ 被排除在 CI 外）⇒ 列入 §八·3。

### R5 错误码契约一致性（项目级 · 🟢 已有工具 · ⚠️ 未进 CI）

- **Go 出处**：G4 —— 8 份 `errmsg.go`，错误码是 wire 契约。
- **Java 现状**：8 个 `*ErrorCode` 类；已有 `tools/errmsg-probe/<域>/run-parity.sh` 做**双侧逐字节对拍**。
- **判定标准**：每个 Java 错误码的 code 与 message 与 Go 侧逐条一致。
- **已具备？** 🟡 工具已在，**未进 CI**（需要 Go 真源仓库）。

### R6 数据库 schema 一致性（服务级 · 🟢 已有工具 · ⚠️ 未进 CI）

- **Go 出处**：G5 —— schema 真相在 Go 的 model struct（`AutoMigrate`）。
- **Java 现状**：37 份 Flyway SQL；已有 `tools/difftest/check-ddl-static.py`（列级：类型/可空性/DEFAULT/索引）
  + 活体对拍（`--container/--go-db/--rag-db`）。
- **判定标准**：Java 表的列级属性与 Go 侧 model 逐列一致；**类型/长度差异必须为 0**。
- **已具备？** 🟡 工具已在，**未进 CI**（需活体 PG + Go 侧 DB）。

### R7 产物可执行性（服务级 · 🟢 已有 · ⚠️ 需修 CI 传递链）

- **Go 出处**：G1 —— 每个服务一个可运行二进制/镜像。
- **Java 现状**：`tools/difftest/check-jar-executable.sh` 已守「api/rpc 的 jar 必须 `Main-Class: JarLauncher`」
  （脚本自己注明：`service/common` 是 library 模块，**刻意不在枚举里**）。
- **判定标准**：本域 api/rpc 的 `target/*.jar` 是可执行 fat jar；**未构建 ⇒ 判红**（该脚本已按此设计，不允许「没查」被读成「查过」）。
- **已具备？** 🟡 门本身已具备；**CI 传递链本 PR 才修好**（jar 只在 `package` 产出，`mvn test` 不产 jar
  ⇒ `maven-build` 必须声明 `outputs`）。

### R8 公共组件变更的影响面传播（项目级 · 🟢 已有）

- **Go 出处**：G2 —— `service/common` 被 10 个服务 import ⇒ 改它波及全部。
- **Java 现状**：`service/common` 被 10 域依赖；`users/users-common` 被 8 域依赖；各 `*-proto` 被下游依赖。
- **判定标准**：改 X ⇒ **受影响域的流水线必须被触发**（资源层过滤，不是"进 job 再判断"）。
- **已具备？** ✅ 是（`ci/generate-pipelines.py` 从 **pom 依赖图**反推触发面，`--check` 机器校验漂移）。

### R9 配置与密钥面（项目级 · 🟢 已有）

- **Go 出处**：G6 —— 根 `.env` + `etc/*.yaml`，密钥不进版本库。
- **Java 现状**：`application.yml` + profile；已有 `check-no-prod-secrets.py`、`check-datasource-password-form.py`、
  `check-nul-bytes.py` 等。
- **判定标准**：仓库内无生产密钥；口令占位符形态在 Go 侧 `os.ExpandEnv` 下可解析（**不得带默认值**）。
- **已具备？** ✅ 是（已进 CI 静态门）。

### 4.1 需求 → 粒度汇总

| 需求 | 粒度 | 现状 | 已进 CI？ |
|---|---|---|---|
| R1 干净构建 | 项目级 | ✅ | ✅（build 步） |
| R2 单测可判定 | 项目级 | ✅ | ✅（test + report 步） |
| R3 集成测试活体依赖 | 服务级 | ✅（🔴 一处 Linux-only 缺陷） | ✅（DinD） |
| R4 接口契约 | 项目级 + 服务级 | 🟡 复制已验，消费方式未验 | ❌（需 Go 真源） |
| R5 错误码契约 | 项目级 | 🟡 工具已在 | ❌（需 Go 真源） |
| R6 schema 一致性 | 服务级 | 🟡 工具已在 | ❌（需活体 PG + Go 侧 DB） |
| R7 产物可执行 | 服务级 | 🟡 门已在，传递链本 PR 修 | ✅（static-gates 步） |
| R8 影响面传播 | 项目级 | ✅ | ✅（git 资源 paths） |
| R9 配置与密钥 | 项目级 | ✅ | ✅（static-gates 步） |

### 4.2 项目级 vs 服务级（交付 5 的前置）

| 类别 | 内容 | 归属 |
|---|---|---|
| **项目级**（每个域流水线都跑） | R2 的兜底、R4 的仓级一致性、R5、R8、R9 + 8 道静态守门（密钥/NUL/口令形态/Tomcat 上限/gRPC 拦截器接线/gRPC 运行时 classpath/Flyway 账本/**pom 父引用**） | 共享层 + CI 自身 |
| **服务级**（只在本域跑） | R3（本域 Testcontainers 组合与端口段）、R6（本域 DDL）、R7（本域 api/rpc jar）、R4 的本域 proto | 各域 |

---

## 五、微服务级 CI 需求矩阵（交付 4）

| 域 | Go 服务 | Go 跨服务依赖 | Java 模块 | Java 测试文件 | 本域 proto | 本域特有 CI 需求 |
|---|---|---|---|---|---|---|
| article | `service/article` | → message, security※ | `article-{api,common,proto,rpc}` | 43 | `article.proto` | R3 PG + R6 DDL + R7 jar×2 |
| comment | `service/comment` | → article, message | `comment-{api,common,proto,rpc}` | 39 | `comment.proto` | R3 PG + R6 DDL + R7 jar×2 |
| favorite | `service/favorite` | → article, user | `favorite-{api,common,proto,rpc}` | 51 | `favorite.proto` | R3 PG + R6 DDL + R7 jar×2 |
| follow | `service/follow` | → user | `follow-{api,common,proto,rpc}` | 39 | `follow.proto` | R3 PG + R6 DDL + R7 jar×2 |
| hot | `service/hot` | （无） | `hot-{api,common,proto,rpc}` | 50 | `hot.proto` | R3 + R6 DDL + R7 jar×2（**Go 侧有压测入口，Java 无** ⇒ §八·2） |
| like | `service/like` | → message | `like-{api,common,proto,rpc}` | 48 | `like.proto` | R3 PG + R6 DDL + R7 jar×2 |
| message | `service/message` | → admin, user | `message-{api,common,proto,rpc}` | 51 | `message.proto` | R3 PG+Kafka + R6 DDL + R7 jar×2 |
| task | `service/task` | → points※ | `task-{api,proto,rpc}` | 34 | `task.proto` | R3 PG+Kafka + R6 DDL + R7 jar×2（**无 `task-common`**：Go 侧同样没有，不是遗漏） |
| users / user | `service/user/user` | — | `users/user/{user-api,user-proto,user-rpc}` + `users-common` | 66 | `users.proto`（Go `user.proto` 改名） | R3 PG+Redis + R6 DDL + R7 jar×2 |
| users / admin | `service/user/admin` | — | `users/admin/{admin-api,admin-proto,admin-rpc}` | 57 | `admin.proto` | R3 PG + R6 DDL + R7 jar×2（**Go 侧 admin 测试 = 0**） |
| **共享层** | `service/common` | 被 10 域依赖 | `service/common` + `users/users-common` | 103 + 4 | — | R2 兜底 + R7 **不适用**（library 模块，无可执行 jar ⇒ 产物门跳过） |

> **矩阵读法**：一行 = 一个域；列 = 「Go 侧是什么 / Java 侧是什么 / CI 必须为它做什么」。
> **"Go 跨服务依赖"列**直接决定该域的**触发面**（§六）。

---

## 六、公共模块与跨服务依赖的 CI 触发需求（交付 5）

### 6.1 变更源 → 必须触发的域

| 变更源 | 必须触发的流水线 | 依据 |
|---|---|---|
| `service/common/**` | **全部 10 域 + 共享层** | G2：Go 的 `service/common` 被 10 服务 import；Java 的 `service/common` 被 10 域依赖（§3.2 实测） |
| `service/users/users-common/**` | users/user、users/admin、**article、comment、favorite、follow、like、message**（8 域） | §3.2 实测：8 个域依赖它 |
| `service/<域>/<域>-proto/**` | 依赖该 proto 的域（§3.2 的 16 条边） | G3：proto 是跨服务唯一契约面 |
| `pom.xml` / `service/pom.xml` | **全部** | 模块清单/版本变更波及 reactor |
| `service/<域>/**`（域内） | 本域 | — |
| `ci/**` | **全部** | CI 自身脚本变更 ⇒ 全部重跑 |

### 6.2 触发面的实现口径（🟢 已有）

- 触发面在**资源层**（git 资源的 `paths`）完成 ⇒ **不触发的域连 build 都不排**。
- 触发面**由 pom 依赖图反推**（`ci/generate-pipelines.py`），**不手写** ⇒ `--check` 机器校验漂移。
- ⚠️ **不能按 Go 目录结构决定**：Go 的 `common/` 是目录，Java 的 `<域>-common` 是模块
  ⇒ **粒度依据 = Maven 模块图**（这也是为什么 pipeline 数量不由 Go 的 13 个目录决定）。

### 6.3 一条必须写进基线的精度陷阱（🟢 实测踩到）

构建触发面时，若用 `grep 'sea-try-go/service/...'` 这类**朴素文本匹配**扫 Go 侧依赖图，
会把**被注释掉的 import** 也算成依赖 ⇒ 实测**多出 2 条**（`task→user`、`hot→user`）
⇒ ⇒ 按错误依赖图建触发面 ⇒ **多触发**（噪声）或**判错影响面**（漏触发则更糟）。
⇒ **口径**：依赖边必须由**可编译的 import 图**得出，且**排除注释行**。

---

## 七、Java 微服务接入 CI 的条件与验收标准（交付 6）

### 7.1 域级接入条件（一个域要能独立挂进 CI，必须同时满足）

| # | 条件 | 判定方式 | 现状 |
|---|---|---|---|
| C1 | 模块能被 `-pl` 单独选中，且依赖在 reactor 内可解 | `mvn -pl <域模块> -am -DskipTests package` 在**空 `~/.m2`** 下成功 | ✅（本 PR 修了 `admin-api` 后成立） |
| C2 | 父 POM 可在干净环境解析 | 跑 `ci/scripts/check-pom-parent-relativepath.py` ⇒ EXIT=0 | ✅（本 PR 修 + 新增第 9 道门） |
| C3 | 测试在干净环境有结论 | `mvn -pl <域模块> -am test` 产出 surefire XML，失败可读 | ✅（⚠️ R3 的 Linux-only 缺陷会拦住 9 个域） |
| C4 | 集成测试的活体依赖在 CI 内可达 | CI 内 `curl --unix-socket …/_ping` 有响应 + 镜像可拉 | ✅（DinD 实测） |
| C5 | 产物是可执行 fat jar | `check-jar-executable.sh <域 api> <域 rpc>` ⇒ EXIT=0 且「已检查 N/N」 | ✅（⚠️ 需 jar 传得到守门步骤 ⇒ 本 PR 修了 outputs） |
| C6 | 端口段不被占用 | 域内固定端口段可用 | 🔴 **当前不满足**（Linux-only 判据 ⇒ 整段被判「占满」）⇒ 修完即满足 |
| C7 | 触发面覆盖该域的全部上游依赖 | `--check` EXIT=0 + 人工核对一条边 | ✅ |
| C8 | 域内**无**生产密钥 / 无机器地址写死 | `check-no-prod-secrets.py` + `check-datasource-password-form.py` EXIT=0 | ✅ |

### 7.2 验收标准（怎么判定「一个域已正确接入 CI」）

1. 该域流水线在**空缓存 + 空 `~/.m2`** 的环境里跑完 4 步且**绿**；
2. 构建产物可执行（`java -jar` 起得来），且**产物级守门的「已检查 N/N」里 N = 本域 api+rpc 数**；
3. 测试报告能按模块列出用例/失败/错误/跳过，**失败即红**；
4. **故意制造一次失败**（改一个断言）⇒ 流水线必须红且**失败点落在正确的那一步**（不是"红在对的地方"而是"红在正确的地方"）；
5. 改一个上游依赖（如 `<域>-proto`）⇒ 该域流水线**必须被触发**；改一个无关目录 ⇒ **必须不被触发**；
6. 流水线定义与 `pom` 依赖图**无漂移**（`--check` EXIT=0）。

---

## 八、当前还不能确定、必须等 Java 实际实现后才能决定的事项（交付 7）

| # | 待定项 | 为什么现在定不了 | 需要谁/什么来定 |
|---|---|---|---|
| 1 | **Java 多出的 3 条跨域依赖**（article→users/user、hot→article、users/admin→users/user）在 Go 侧不存在 | 🟢 实测确认差异存在，但**无法从依赖图判断它是"行为等价的新写法"还是"迁移未完成"** | 域 lane 对照 Go 源码逐条核 |
| 2 | **Go 的压测入口**（`service/hot/test/cmd/loadtest`）在 Java 侧无对应 | Go 侧它是 `main` 程序（**不被 `go test` 跑到**）⇒ 不是"CI 门"，是"手工工具" ⇒ 是否要进 CI 无先例 | 产品/架构决策：压测属 CI 还是性能平台 |
| 3 | **R4/R5/R6 三条契约门进不了 CI** | 它们需要 **Go 真源仓库 / 活体 PG + Go 侧 DB** ⇒ 而 CI 仓是 Java 仓、GitHub 公开源 ⇒ 要么在 CI 侧挂 Go 仓（需密钥或公开镜像），要么做成**发布前的离线对拍**（现状） | 决策：契约对拍放 CI 还是放独立门禁 |
| 4 | **Go 的 `api/*.api`（HTTP 契约）没有 Java 侧的机器可读对应物** | Go 用 go-zero DSL 声明 HTTP 路径/方法；Java 用 `@RestController` ⇒ 路径/方法/响应体的一致性**目前只能靠人工/散点检查** | 需要先决定 Java 侧的 HTTP 契约源是什么（注解扫描？OpenAPI？） |
| 5 | **Go 侧零测试服务（message/follow/task/admin）在 Java 侧有 34~57 个测试文件** | 这些 Java 测试**超出了 Go 原版的验证基线** ⇒ 它们守的是"Java 侧行为"，不是"Go 语义" ⇒ 二者不是同一件事 | 需区分「语义对照测试」与「Java 侧新增守门」两类 |
| 6 | **Java 的 `<域>-common` 独立模块是否应各有自己的流水线** | Go 侧 `common/` 只是目录 ⇒ 没有先例。Java 侧拆成独立模块后，它有自己的编译与测试边界 | 决策：粒度按 Maven 模块，还是按"域"打包 |
| 7 | **R4 的"消费方式一致性"逐条核过没有** | 已核的是 `.proto` **文件本身**（10/10 一致）⇒ 「RAG 怎么消费」未核（`optional` presence / 字段号硬编码 / Jackson 差异） | 需逐条核（AGENTS.md §3.4 的效力边界） |
| 8 | **126 个 Testcontainers 测试文件的固定端口段与真实进程端口段** | 两套端口段表并存（22700–22999 容器宿主口 / 22400–22479 真进程口）⇒ 在 CI 的 DinD 网络命名空间里如何隔离，**取决于 §7.1-C6 的缺陷修法** | 域 lane 修 `isFree()` 后再定 |
| 9 | **Go 的 `knowledge`（65 个测试，占 Go 侧 62%）其测试形态是否应被 Java 借鉴** | 范围外 ⇒ 不是待办；但它是 Go 侧唯一的"真实 HTTP + fixture"验收样板 | 产品决策：Java 要不要建验收层 |

---

## 九、结论：Go 定「验证什么」，Java 定「怎么验证」

1. **Go 原版没有 CI**（🟢 无任何 CI 配置、无 Makefile）⇒ 「值得保留的 CI 要求」**只能从它的构建/测试/接口约定里提取**（§2.4 的 G1~G7）。
2. **两侧测试结构不可类比**（范围内 33 : 585）⇒ **Java 的 CI 门必须比 Go 严**，且**不能按 Go 的测试分布配门**（§2.2）。
3. **流水线粒度的依据是 Maven 模块图，不是 Go 目录结构**（§1.3 / §6.2）——Go 的 `common/` 是目录，Java 的是模块。
4. **9 条需求（R1~R9）**中，**R1/R2/R3/R7/R8/R9 已具备并可进 CI**；**R4/R5/R6 三条契约门因需要 Go 真源/活体 DB 而进不了 CI**（§八·3）⇒ 这是**当前最大的 CI 能力缺口**。
5. **Java 侧迁移现状比 Go 厚得多**（585 测试 / 100+ 守门脚本 / 10 proto / 8 错误码类）⇒ CI 的主要工作量**不在"把门建起来"，而在"把已有的门接进去"**。
6. **8 条待定项**（§八）里最关键的两条：**① Java 多出的 3 条跨域依赖**（决定触发面对不对）与
   **② 契约门进不了 CI**（决定 CI 到底能守住多少）。

---

## 附：本基线的数据出处（可复算）

```bash
G="/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"; R="/Users/pr/Documents/java&golang/RAG"

# Go 侧：服务划分 / proto / api / 测试分布 / 公共组件
ls -1 "$G/service"; ls -1 "$G/proto"/*.proto; ls -1 "$G/api"/*.api
find "$G" -name '*_test.go' | wc -l                      # 105
find "$G/service" -path '*/rpc/*' -name '*.go' -exec grep -h '^\s*[A-Za-z_]*\s*"sea-try-go/service/' {} +   # 调用边（排除注释行）

# Java 侧：模块 / 测试 / proto / 依赖图
find "$R/service" -name pom.xml | wc -l                   # 52
find "$R/service" -path '*/src/test/*' -name '*.java' | wc -l   # 585
find "$R/service" -path '*/src/main/proto/*' -name '*.proto'    # 10
```

> ⚠️ 本基线的**数据是 2026-10-31 的快照**。Go 仓已冻结（只读）⇒ Go 侧数据稳定；
> Java 仓仍在多 lane 并行演进 ⇒ **Java 侧数字会变** ⇒ 复核时以命令为准，不要以本文数字为准。
