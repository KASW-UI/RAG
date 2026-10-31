# RAG · Concourse CI · 交付文档

> 本文档是 `ci/` 目录的**最终交付说明**，对应提示词 `微服务ci.md` 的阶段五。
> 范围严格限定在 CI：构建、测试、静态守门、结果汇总。
> **不含** CD / 生产发布 / Kubernetes 部署。
>
> **PR**：<https://github.com/KASW-UI/RAG/pull/2>（分支 `align/ci-concourse` → 基准 `do-my-own-gh`）
>
> 事实基线：2026-10-30，本地 Concourse **8.3.2**（`/Users/pr/concourse-local`），
> worker 运行时 = **containerd 3.0**（Concourse 8 已无 docker runtime）。

---

## 一、架构说明

### 1.1 整体形态

```
┌──────────────────────────────────────────────────────────────────────┐
│  本地 Concourse 8.3.2（http://localhost:8080，team=main，worker=containerd） │
└──────────────────────────────────────────────────────────────────────┘
        │  ① git 资源（paths 过滤 ⇒ 版本=一个 commit）
        ▼
┌──────────────────────────────────────────────────────────────────────┐
│  11 条流水线：rag-shared-common + rag-users-{user,admin} + 8 个域     │
│  每条 = 1 job × 4 step：                                              │
│      maven-build → maven-test → static-gates → report                 │
└──────────────────────────────────────────────────────────────────────┘
        │  ② task step（privileged ⇒ DinD）
        ▼
┌──────────────────────────────────────────────────────────────────────┐
│  任务容器（image = docker:27-dind）                                    │
│    └─ 内层 dockerd（unix:///var/run/docker.sock）                       │
│         └─ 嵌套构建容器（image = maven:3.9.11-eclipse-temurin-21）     │
│              └─ mvn -pl <本域模块> -am …                              │
│                   └─ Testcontainers → 内层 daemon 的 PG/Redis/Kafka    │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.2 为什么是 DinD 而不是"挂宿主 docker.sock"

Concourse 8 的 task 配置**没有任意挂载声明项**（实测：`concourse` 二进制里
`container_spec` 零命中、`container_limits` 有命中 ⇒ 该 schema 不存在挂载项），
worker 的可用环境变量里也没有任何"挂宿主路径"的开关
⇒ "wormhole"（把宿主 `docker.sock` 挂进任务容器）**在 Concourse 8 上做不到**。

⇒ 唯一官方支持的形态是 **DinD**：`task step` 开 `privileged: true`，
在任务容器里起内层 `dockerd`，再在**内层 daemon** 上跑嵌套构建容器
（这正是 Testcontainers 官方文档给的 Concourse 示例形态）。

**顺带的好处**：内层 daemon 把固定宿主口（如 22760）发布在**任务容器自己的 netns** 里
⇒ 本仓按域互斥的固定端口段（22700–22999）落在**每个任务容器各自的网络命名空间**
⇒ **11 条流水线并行也不会互相抢端口**（比宿主共享端口更安全）。

### 1.3 三层"共享"与各自的失效方式

| 层 | 共享什么 | 失效方式 | 本方案对策 |
|---|---|---|---|
| **Concourse 资源层** | `git` 资源 + `paths` 过滤 | 路径漏项 ⇒ **静默**失去保护 | 触发面**由 pom 依赖图反推** + `--check` 漂移检测 |
| **构建层** | `service/common` / `users-common` / 跨域 proto | 共享层坏了 ⇒ 依赖它的域也该红 | 每条流水线 `mvn -pl <本域> -am` **从源码重编**（无制品仓库可传） |
| **执行层** | 共享的 `ci/tasks/*.yml` + `ci/scripts/*.sh` | 脚本改动 ⇒ 所有流水线行为变 | `ci/**` **进触发面** ⇒ 脚本改了立刻全量重跑 |

### 1.4 四个"变量化"的外部依赖（仓库里不写死任何机器地址）

| pipeline var | 默认 | 作用 |
|---|---|---|
| `registry` | `docker.m.daocloud.io` | 镜像源主机（`host` 或 `host:port`）。**带端口 ⇒ 本地 registry** ⇒ 内层 dockerd 按 HTTP + insecure 处理 |
| `maven_mirror` | 空（走默认 Central） | Maven 镜像源。**非空 ⇒ 优先查它，404 再回退 Central**（用「额外仓库」而非 `<mirror>`，避免把 Central 顶掉后无退路） |
| `git_uri` | `https://github.com/KASW-UI/RAG.git` | git 资源地址。**参数化而非写死** ⇒ 触发面验证可以用**真实服务端 + 真实 commit**做（需要一个可推送的远端 ⇒ 本地演练仓），而不必改流水线定义 |
| `git_branch` | `do-my-own-gh` | git 资源分支。同上 |

> ⚠️ `git_uri` / `git_branch` 是 2026-10-31 新增的两个 var。
> **为什么必须参数化**：任务书第五阶段·3 要求「变更触发验证 ⇒ 必须记录实际触发结果，
> 不要只根据 YAML 推断」。而验证需要一个**能 push 的远端** ⇒ 只能让流水线的
> git 资源在部署时指向本地演练仓 ⇒ uri/branch 写死就做不到。
> ⇒ 生产部署传 GitHub 地址（`ci/deploy.sh` 的默认值），演练时用
> `CONCOURSE_GIT_URI` / `CONCOURSE_GIT_BRANCH` 覆盖。

---

## 二、文件清单（**80 个文件**，含本文档）

> 计数口径（2026-10-31 实测）：`ci/` 下 **77** 个（排除 `.DS_Store` 与 `__pycache__`）
> + `tools/difftest/` 下本轮涉及的 **3** 个 = **80**。
> 分目录：`ci/` 7 · `ci/contracts/` 1 + 基准 29（errmsg 10 / proto 10 / schema 9）
> · `ci/pipelines/` 13 · `ci/requirements/` 2 · `ci/scripts/` 19 · `ci/tasks/` 6。

| 路径 | 职责 | 真源？ |
|---|---|---|
| `ci/pipelines/rag-*.yml`（11 个日常 + 2 个契约） | 各域流水线定义（4 step）/ 契约流水线定义 | ⛔ **生成物**，不要手改 |
| `ci/tasks/maven-build.yml` | 构建阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/maven-test.yml` | 测试阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/static-gates.yml` | 静态守门阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/report.yml` | 结果汇总阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/contract-errmsg.yml` | **R5 契约门** task 配置 | ✅ 唯一真源 |
| `ci/tasks/contract-schema.yml` | **R6 契约门** task 配置 | ✅ 唯一真源 |
| `ci/scripts/build.sh` | 嵌套容器内 `mvn package -DskipTests` | ✅ |
| `ci/scripts/test.sh` | 嵌套容器内 `mvn test`（先 source Testcontainers 环境） | ✅ |
| `ci/scripts/testcontainers-env.sh` | Docker 可达性前置断言 + host override + Ryuk 关闭 | ✅ |
| `ci/scripts/dind-run.sh` | DinD 外壳：起内层 dockerd → 跑嵌套构建容器（支持**第二个内层阶段** · 共用同一 bind mount） | ✅ |
| `ci/scripts/run-static-gates.sh` | 按序跑静态守门并汇总（**豁免命中数单列**，不混进 PASS） | ✅ |
| `ci/scripts/check-pom-parent-relativepath.py` | **第 9 道门**：每个 pom 的 `<parent>` 能否被本地 `relativePath` 正确解析（见 §六.2） | ✅ |
| `ci/scripts/collect-surefire.sh` | 读 `TEST-*.xml` 汇总用例数与失败清单；**并判「该在而不在」与「跑了但 0 条用例」**（见 §四.5） | ✅ |
| `ci/scripts/check-proto-contract.py` | **R4 契约门**：Java 侧 `.proto` ↔ `ci/contracts/proto/` 基准逐字节比对（唯一归一化实现） | ✅ |
| `ci/scripts/check-errmsg-contract.sh` | **R5 契约门**：**纯 Java 半边**探针 ↔ `ci/contracts/errmsg/` 基准比对 | ✅ |
| `ci/scripts/check-errmsg-contract-mutations.py` | R5 门禁的**变异测试**（证明非假绿） | ✅ |
| `ci/scripts/check-schema-contract.py` | **R6 契约门**：活体 PG 重放 Flyway → 逐列比对 `ci/contracts/schema/` 基准 | ✅ |
| `ci/scripts/check-schema-contract-mutations.py` | R6 门禁的**变异测试**（含故障注入：容器被杀 ⇒ 必须判 BLOCKED 而非 FAIL） | ✅ |
| `ci/scripts/check-requirements.py` | **第 11 道门**：目标要求清单守门（11 条规则 + 六态汇总） | ✅ |
| `ci/scripts/check-requirements-mutations.py` | 清单守门的**变异测试**（10 条变异全部红在目标规则） | ✅ |
| `ci/scripts/check-docker-api-selftest.py` | **第 13 道门**：`docker_api` 的**无依赖**回归自测（11 条断言：镜像 tag 判定 + exec 劫持线格式） | ✅ |
| `ci/scripts/extract-errmsg-authority.py` | **一次性离线**提取：从 Go 侧 `errmsg.go` ⇒ 产出 R5 的 Go 权威段 | ✅ |
| `ci/scripts/contract-errmsg.sh` | R5 **内层阶段①**（maven 镜像）：编译 + 跑探针 ⇒ 只产出 Java 侧 dump | ✅ |
| `ci/scripts/contract-errmsg-compare.sh` | R5 **内层阶段②**（python 镜像）：读 dump ⇄ Go 真源基准 ⇒ 出 PASS/FAIL/PENDING | ✅ |
| `ci/scripts/contract-schema.sh` | R6 在 DinD 内层容器里的阶段脚本（内层用 `python:3.12-bookworm`，**不需要 Maven/JDK**） | ✅ |
| `ci/scripts/verify-triggers.sh` | 触发面验证：`--static`（实现 vs 意图 · 7 场景 · **第 12 道门**）/ 服务端模式（真实 build 读数） | ✅ |
| `ci/contracts/README.md` | **契约基准的治理约定**：来源 / 裁定 / 版本 / 审查 / 五态失败语义 | ✅ |
| `ci/contracts/proto/*.proto`（10 份） | **R4 契约基准**（从 Go 真源一次性离线提取 + 人工裁定） | ✅ 基准 |
| `ci/contracts/errmsg/*.txt`（10 份） | **R5 契约基准**（Go 侧权威值 + 差异逐条裁定） | ✅ 基准 |
| `ci/contracts/schema/*.json`（9 份） | **R6 语义约束基准**（Go GORM 活体提取：必须保持 + 已批准差异 + 未确认差异） | ✅ 基准 |
| `ci/requirements/requirements.json` | **目标要求清单**（46 条：Go 依据 / Java 实现 / 验证方法 / 六态 / 差异批准 / 归属 Pipeline+Task / 证据） | ✅ **单一真源** |
| `ci/requirements/README.md` | 清单治理约定（字段 / 维度 / 规则 / 变更流程 / 当前状态） | ✅ |
| `ci/generate-pipelines.py` | 从 pom 依赖图生成 11 条日常 + 2 条契约流水线；`--check` 校验漂移 | ✅ |
| `ci/deploy.sh` | 校验 + `set-pipeline`（注入 4 个 var）+ `unpause-pipeline`（幂等） | ✅ |
| `ci/local-up.sh` | 本地环境一键起流（load tar → 可选本地 registry → 起 Concourse → 注册流水线 → 可选触发） | ✅ |
| `ci/README.md` | 使用说明 / 依赖矩阵 / 触发规则 / 环境约束 | ✅ |
| `ci/DELIVERY.md` | 本文档 | ✅ |
| `ci/BASELINE.md` | **CI 需求基线**：以 Go 原版为参照，列出 Java CI 应验证什么（R1~R10）+ 微服务级矩阵 + 触发需求 + 接入条件与验收标准 + 待定事项 | ✅ |
| `tools/difftest/docker_api.py` | **纯标准库** Docker Engine API over unix socket（CI 镜像**没有 docker CLI** ⇒ 绕开它） | ✅ |
| `tools/difftest/extract-schema-baseline.py` | **一次性离线**提取：从 Go GORM `AutoMigrate` 活体 + Java Flyway 活体 ⇒ 产出 R6 基准 | ✅ |
| `tools/difftest/schema-baseline-deviations.json` | R6 差异的**人工裁定表**（approved / unconfirmed 分列） | ✅ |


> **为什么 task 配置要内联进 pipeline**
> Concourse 的 pipeline step **必须**带 `file:` 或 `config:` 二选一，不能引用独立任务文件
> （实测 `fly validate-pipeline` 报 `must specify either 'file:' or 'config:'`）。
> ⇒ `ci/tasks/*.yml` 是唯一真源，由生成器**内联**进 11 条 pipeline ⇒ 不会两份漂移。
> ⇒ 这些文件同时也是 `fly execute -c ci/tasks/maven-build.yml` 一次性调试用的。

---

## 三、执行流程

### 3.1 单条流水线的 4 个 step

| step | image | privileged | 做什么 | 失败意味着 |
|---|---|---|---|---|
| `maven-build` | `((registry))/library/docker:27-dind` | ✅ | `mvn -pl <本域模块> -am -DskipTests package` | **构建失败**（编译期错误） |
| `maven-test` | 同上 | ✅ | `mvn -pl <本域模块> -am test` | **测试失败**（用例红了） |
| `static-gates` | `((registry))/library/python` | ❌ | 8 个纯静态守门 + 产物级 jar 可执行性检查 | **静态门失败** |
| `report` | 同上 | ❌ | 汇总 surefire 报告 | 有失败/错误用例 ⇒ 退出码 1 |

构建与测试**拆成两个 step** ⇒ Concourse 上"构建失败"与"测试失败"是两个不同状态，
失败发生在哪个阶段一眼可见（提示词 §三.6 的明确要求）。

### 3.2 为什么构建/测试恒带 `-am`

本仓所有跨模块依赖（`common` / `users-common` / `*-proto`）都是 **reactor 内的 SNAPSHOT**，
不带 `-am` 时 Maven 会去 `~/.m2` 取**陈旧**的 jar。本仓自带
`*SharedLayerArtifactFreshnessGateTest` 就是在守这条（类注释写明：单模块构建会拿到
2026-10-27 的旧 common ⇒ fat jar 里嵌旧共享层 ⇒ Boot 4 下进程启动即挂）。

代价（已知且接受）：`-am` 会连带跑依赖模块的测试。

### 3.3 静态守门的取舍（实测分类）

| 纳入 | 排除（**写成"已跑"就是虚构**） |
|---|---|
| `check-no-prod-secrets.py`、`check-no-nul-bytes.py`、`check-datasource-password-form.py`、`check-tomcat-limits.py`、`check-grpc-interceptor-wiring.py`、`check-grpc-runtime-classpath.py`、`check-flyway-ledger.py` + 产物级 `check-jar-executable.sh` | `check-ddl-static.py`（需活体 PG）· `check-proto-static.sh` / `check-surface-parity.py` / `check-phasegoal-consistency.py`（需 Go 真源仓库）· `check-plaintext400-headers.sh` / `check-flyway-concurrent.sh`（需起真实进程） |

---

## 四、验证记录（**按证据强度分层**，2026-10-30）

| 层级 | 结论 | 依据 |
|---|---|---|
| YAML 语法 | ✅ | 15 个文件 `yaml.safe_load` 通过 |
| **Concourse 配置校验** | ✅ | `fly validate-pipeline -c ci/pipelines/*.yml` → **11/11 "looks good"** |
| 生成物一致性 | ✅ | `python3 ci/generate-pipelines.py --check` → EXIT=0 |
| **Concourse 服务端落地** | ✅ | 本地 8.3.2 起流成功（`/api/v1/info` 返回 `{"version":"8.3.2","worker_version":"3.0"}`）；worker `22f904bc9cc6 running 3.0` 已注册 |
| **11 条流水线注册** | ✅ | `ci/deploy.sh` 执行完毕；`fly pipelines` 列出 11 条 `rag-*`；配置已按最新生成物重新部署（2026-10-31 06:32）|
| canonical 流水线当前 **paused** | 📌 | 本 PR 合并前必须 pause —— `ci/` 还没进 `do-my-own-gh` ⇒ 别的 lane 推 commit 就会触发必红的 build ⇒ 是噪声不是信号；合并后再 unpause |
| **worker 从本地 registry 拉镜像** | ✅ | 探针流水线 21s 成功，层下载进度可见 ⇒ **containerd 能走 HTTP 拉本地 registry** |
| **task 容器出网** | ✅ | 探针：DNS(8.8.8.8 / 223.5.5.5) OK、`github.com:443` OK、`172.17.0.1:5010`（本地 registry）OK；`10.80.0.1:5010` FAIL（预期，网关无此服务） |
| **静态守门在真实 task 镜像内** | ✅ | `docker run python:3.12-bookworm sh ci/scripts/run-static-gates.sh` → **8/8 全绿**（第 9 道门加入后为 **9/9**；且已在**真 CI 容器**内实测通过 ⇒ 见 §四.1） |
| **DinD 外壳（真实 privileged）** | ✅ | 内层 `dockerd` 起来 → 从本地 registry 拉到 `maven:3.9.11-eclipse-temurin-21` → Maven 构建启动 |
| **Testcontainers 在 DinD 里真跑** | ✅ | 真 CI 日志：`Container postgres:16-alpine is starting` → `started in PT5.213380655S` → `JDBC URL: jdbc:postgresql://10.250.0.1:32769/mp_probe` ⇒ **内层 daemon 起 PG + 嵌套容器按内层 bridge 网关连上** |
| 构建命令正确性 | ✅ | 宿主实测：`mvn -o -pl service/article/article-api,service/article/article-rpc -am -DskipTests package` → BUILD SUCCESS（14 模块 / 8.6s）；`mvn -o -pl service/common -am clean test` → 405/405 全绿（1:12） |
| **完整 4 step build 跑绿** | ✅ | **共享层流水线演练 build #2：`succeeded`，3m40s** ⇒ build ✅ / test ✅ / 静态守门 8 过 0 挂 ✅ / 汇总 **484 条用例全通过**（service/common 445 + users-common 39，0 失败 0 错误 1 跳过）⇒ 详见 §四.1 |

### 4.1 演练历程：每一次失败都定位到根因（**2026-10-30 ~ 10-31，共 8 次**）

⚠️ **说明**：演练用**本地 git 服务**（`git://172.17.0.1:5012/gitd-rag.git`，分支 `ci-verify`），
内容 = 本地 HEAD + `ci/` 全量 + 本 PR 的 pom 修复 ⇒ 目的是**把流水线机制本身**跑到绿，
而不是验证 GitHub 链路（那条链路的可达性单独实测过，见 §五）。

| # | 流水线 | 结果 | 卡在哪 | 定位到的根因 | 处置 |
|---|---|---|---|---|---|
| 1 | article | ❌ | `repo/ci/scripts/dind-run.sh: No such file` | **`ci/**` 不在触发面** ⇒ 取到的 commit 里还没有 `ci/` 目录 | 生成器 `ROOT_GLOBS` 加 `ci/**` |
| 2 | article | ❌ | `dial tcp 172.17.0.1:5010: connection refused` | **内层 dockerd 默认 bridge `172.17.0.0/16` 与外层 docker0 同段** ⇒ 抢走路由 | 显式 `--bip 10.250.0.1/16 --default-address-pool base=10.250.0.0/16,size=24` |
| 3 | article | ❌ | `cannot open ci/scripts/build.sh` | 嵌套容器里脚本路径缺 `repo/` 前缀（`build.sh` 内部是 `cd repo`） | 改为 `sh repo/ci/scripts/<stage>.sh` |
| 4 | article | ❌ | `Non-resolvable parent POM … com.rag:service:pom` | **仓内真实缺陷**（§六.1） | ✅ 已修（随本 PR） |
| 5 | article | ⏸ | 宿主磁盘 100% 满 ⇒ Docker 内容库**块级 EIO** ⇒ ATC 500、worker 拉不到镜像 | **宿主环境**（不是 CI 逻辑）—— 这也是此前反复出现的"daemon 卡死"的真根因 | 清盘 + 重启 Docker Desktop（磁盘恢复 66 GiB） |
| 6 | article | ❌ | `Non-resolvable parent POM …` **仍在** | ⚠️ **我第一版修法写错了**：`<relativePath>../../pom.xml` 解析到 `service/users/pom.xml`（artifactId=`users`）≠ 声明的 `service` ⇒ 坐标仍不符 | 改为 **`../../../pom.xml`**（= `service/pom.xml`）⇒ 并补第 9 道静态守门（§六.2） |
| 7 | article | ❌ | `Docker daemon 不可达（DOCKER_HOST=unix:///var/run/docker.sock）` | ⚠️ **探针自己错了**：嵌套镜像（maven / Debian）里**没有 docker 客户端** ⇒ `docker info` 必然失败 ⇒ 报了个误导性的错 | 探针改用 `curl --unix-socket … /_ping`（与 Testcontainers 同一条路）；网关自发现改读 `/proc/net/route` |
| 8 | shared | ❌ | 静态守门 `8 过 1 挂`：`service/common 没有产物（未构建？）` | ⚠️ **CI 自己的设计缺陷**：① jar 只在 `package` 阶段产出而 `mvn test` 不产 jar，且 `maven-build` **没声明 outputs** ⇒ 产物传不到守门步骤；② 共享层把 **library 模块**也当 `JAR_MODULES` 传了 | `maven-build` 补 `outputs`；生成器只把 `*-api`/`*-rpc` 放进 `JAR_MODULES` ⇒ 共享层留空 |
| 9 | shared | ✅ | —— | **完整 4 step 全绿**：build ✅ / test ✅ / 静态守门 **8 过 0 挂** ✅ / 汇总 **484 条用例全通过**（service/common 445 + users-common 39；0 失败 0 错误 1 跳过），全程 **3m40s** | —— |

**这一栏最有价值的地方**：8 次失败里，**每一次都定位到了根因，没有一次是"看起来像环境问题就重试"** ——
其中 5 次是 CI 自身的设计缺陷（#1 #2 #3 #7 #8），2 次是仓内真实缺陷（#4 #6），1 次是宿主环境（#5）。

**另一条更重要的线：演练还暴露了 article 域在 Linux 上的真实红灯**（不是 CI 的问题）——
article 演练（build #4）的 test 阶段：`service/common` 445 ✅、`users-common` 39 ✅、`article-common` 15 ✅，
但 **`article-rpc` 188 条里 6 failures + 67 errors** ⇒ 根因是 §六.3 的 Linux-only 端口探测判据。
⇒ **CI 如实报红**（这正是它该做的）⇒ 修法交回各域 lane。

### 4.2 演练与 canonical 的差异（必须说清）

演练用的是**本地 git 服务**（`git://172.17.0.1:5012/gitd-rag.git`，分支 `ci-verify`），
内容 = 本地 HEAD + `ci/` 目录 + 一处演练专用的 pom 修复。
canonical 流水线指向 `https://github.com/KASW-UI/RAG.git` 的 `do-my-own-gh` 分支。

⇒ 演练验证的是**流水线机制本身**（取源/触发面/DinD/构建/守门/汇总）；
**未验证**的是 GitHub 链路上的完整绿灯 —— 该链路的可达性已单独实测通过
（`github.com:443` 从 task 容器内 TCP 连通），但**克隆极慢**（见 §五）。

### 4.3 2026-10-31（方向纠正轮）的验证记录

> ⚠️ **本轮的方向纠正改变了「什么算合格」** ⇒ 下面的读数分两类：
> **A** = 代码层实测（可复算）；**B** = Concourse Task 内实跑（证据强度最高）。

| # | 结论 | 层级 | 依据 |
|---|---|---|---|
| A1 | **目标要求清单守门**：结构合规 + 六态分列正确 ⇒ EXIT=0 | A | `python3 ci/scripts/check-requirements.py` ⇒ PASS=23 / FAIL=1 / NOT_VERIFIED=19 / APPROVED_DIFFERENCE=1 / NOT_APPLICABLE=2 |
| A2 | **清单守门不是假绿**：**10/10 变异红在目标规则**，且**真清单字节未变** | A | `python3 ci/scripts/check-requirements-mutations.py` ⇒ M1(R3 反向定义入口) · M2(R2 取值域) · M3/R6(**回指未签字**) · M4(R6 回指不存在) · M5/R5(空壳 PASS) · M6(R5 脚本不存在) · M7(R7 藏差异) · M8(R8 没人执行) · M9(R10 覆盖缺口) · M10(R1 重复) |
| A3 | 🔴 **变异测试抓出守门自身的一个缺陷**并已修 | A | 首轮 10 条变异「全部退出码非 0」，但**红在崩溃**（守门对仓外路径 `relative_to(ROOT)` 抛 ValueError）⇒ 读数会被误判成「门禁有牙」⇒ 已修 + 变异脚本改为**同时断言「红在目标规则」且「无 Traceback」** |
| A4 | **R6 契约门**正向跑通 | A | `python3 ci/scripts/check-schema-contract.py --all`（宿主）⇒ 9 域逐列比对 |
| A5 | **R6 三态分类修正**已落地 | A | 基础设施类失败（容器被杀 · psql `exit status 137`）⇒ **BLOCKED(2) 而非 FAIL(1)**；`hot` 域 ⇒ **NOT_RUN(3)**；逐域容器自愈（换名重建 ⇒ 单域故障不传染后续域） |
| A6 | **R4 契约门**：10 类变异全转红 | A | `python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto` ⇒ PASS 10 / FAIL 0 / BLOCKED 0；变异含改字段号 / 改类型 / 删 message / 改 package / Java 侧 proto 缺失 / 基准缺失 / 基准目录有未登记文件 ⇒ 基准缺失正确判 **BLOCKED(2)** |
| A7 | **R5 契约门**（初版）正向跑通 + 6 类变异全转红 | A | `bash ci/scripts/check-errmsg-contract.sh` ⇒ PASS 9；变异含改码值 / 改文案 / **新增错误码枚举常量（CONST+TABLE 两行都新增 ⇒ 不漏）** / 改 OBS case 名 / 基准缺失(BLOCKED) / Maven 编译失败(BLOCKED) |
| A8 | 🔴 **R5 初版基准是「Java 现状」⇒ 违反「禁止反向定义」** ⇒ **已返工完成** | A | `ci/contracts/errmsg/*.txt` 的 dump 段实为 **Java 侧 dump**，Go 值只在注释里 ⇒ 已返工为 **Go 派生基准**（`# >>> go-authority >>>` 段：`GOCONST`/`GOTABLE`/`GOFALLBACK` + Go `文件:行号` + sha256）+ `# >>> deviations >>>` 差异四态段（9 字段 `DEVIATION|…`） |
| A8b | ✅ **R5 返工后的正向读数**：**PASS 7 / FAIL 0 / BLOCKED 0 / PENDING 2**（域 `comment`·`like`）⇒ **EXIT=3**<br>🆕 **2026-10-31 用户签字后**：**PASS 9 / FAIL 0 / BLOCKED 0 / PENDING 0** ⇒ **EXIT=0**（🟢 绿） | A | `bash ci/scripts/check-errmsg-contract.sh` ⇒ 差异台账分列 **approved 38 / closed 3 / technical 13 / pending 0**（pending 清零 ⇒ 8 条差异已全部销项：4+3 转 `approved` · 1 转 `closed`）⇒ 裁定与依据见 `ci/requirements/README.md` §8.2 |
| A8c | ✅ **R5 门禁变异测试 11/11 红在目标断言**，且**真源字节全部恢复** | A | `python3 ci/scripts/check-errmsg-contract-mutations.py` ⇒ M1 改码值 · M2 删码 · M3 改文案 · M4 改回落 · M5 缺 authority 段(BLOCKED) · M6 DEVIATION 字段损坏(BLOCKED) · **M7 pending→假 approved(自证豁免)⇒FAIL** · M8 approved 依据文件越界⇒FAIL · M9 基准缺失(BLOCKED) · M10 探针编译失败(BLOCKED) · **M11 Java 侧真改一个文案 ⇒ FAIL** |
| A8d | ⚠️ 变异过程**抓出两个变异脚本缺陷**（**不是门禁缺陷**） | A | ① `article` 域探针在 `tools/errmsg-probe/java/Probe.java`（**无 `article/` 子目录**）⇒ 路径写错 ⇒ 脚本崩；② Java 侧变异命中**测试文件**（`rglob` 顺序）⇒ 改的不是 `target/classes` 的来源 ⇒ 门禁当然绿 ⇒ **已限定 `src/main/**`** ⇒ 单验 M11：影子仓改 `SUCCESS(200,"OK")` ⇒ 门禁判 `DRIFT\|UNREGISTERED\|TABLE\|200\|Go=OK / Java=OK-漂移了` ⇒ **FAIL** ⇒ **门禁确实能抓 Java 侧漂移** |
| A9 | **生成物一致 + 13 条流水线全部通过 Concourse 校验** | A | `python3 ci/generate-pipelines.py --check` ⇒ EXIT=0（11 日常 + 2 契约）；`fly validate-pipeline` ⇒ 13/13 |
| A10 | **R6 的 CI 环境编排**打通（实测日志） | B | `rag-contract-schema` build 1420 日志：内层 `dockerd` 就绪 → 从本地 registry 拉到 `python:3.12-bookworm` → 内层容器启动 ⇒ **编排链路成立**；该次失败**不是**环境问题，而是 `check-schema-contract.py` **尚未提交** ⇒ 已在 `5707a16a` 补上 |
| A11 | **R5 契约门在 Concourse Task 内逐域跑通** | B | `rag-contract-errmsg` build 1375 日志：`article ✅ 49 行` · `comment ✅ 140 行` · `favorite ✅ 110 行` ⇒ 逐域判定链路成立（冷 m2 下 9 域编译耗时较长） |
| A13 | ✅ **第 11 道门（目标要求清单）在真实 CI 镜像内跑通** | B | `docker run -v $PWD:/build/repo -w /build python:3.12-bookworm sh repo/ci/scripts/run-static-gates.sh` ⇒ 第 10 道门（R4）**PASS 10 / FAIL 0 / BLOCKED 0** + 第 11 道门**PASS=23 / FAIL=1 / NOT_VERIFIED=19** ⇒ 两道门均绿 |
| A14 | 🔴 **宿主磁盘 100% 满 ⇒ Docker containerd `meta.db` I/O error ⇒ daemon 挂起** | B | `/System/Volumes/Data` 一度只剩 **139 MiB** ⇒ `docker ps` 无限挂起、Concourse worker 拉不动镜像 ⇒ **这是环境问题（BLOCKED），不是代码回归**；清理 `/private/tmp` 探针残留（26G→13G）+ 重启 Docker Desktop 后部分恢复 |
| A12 | 🔴 **踩坑：流水线全部处于 paused ⇒ build 永远 pending** | B | `fly paused-pipelines` 显示 15 条全部 `paused=yes`（`paused_by=test`，06:32 暂停；`fly set-pipeline` **保留暂停态** ⇒ 15:43 重部署后仍是暂停）⇒ **build 卡在 pending 不动**，不是调度器坏了 ⇒ 排查耗时 ⇒ **恢复办法：`fly unpause-pipeline`**（收尾时会把 canonical 重新置回 paused） |

> ⚠️ **A10/A11 的证据边界**：它们证明的是「**流水线机制 + 门禁脚本能在 Concourse Task 内真实执行**」，
> **不等于**「全链路绿」——`rag-contract-schema` 的**首轮**结论是 **失败**（脚本缺失），**修复后需重跑确认**。

### 4.4 🆕 2026-10-31（收尾轮）：契约门在 Concourse Task 内的**完整实跑** + 实跑抓出的 3 个真缺陷

> 本节的读数**全部是 B 层**（Concourse Task 内实跑）⇒ **证据强度最高**。
> ⚠️ 本轮的核心结论：**「在 Task 内真跑一遍」抓出了 3 个此前所有本地/静态验证都发现不了的缺陷**
> ⇒ 再次印证 AGENTS.md §3.3「跑绿 ≠ 对」。

| # | 结论 | 层级 | 依据 |
|---|---|---|---|
| B1 | ✅ **R6 Schema 契约门在 Concourse Task 内完整跑通** | B | `rag-contract-schema` **build 5 = succeeded（1m6s）** ⇒ **PASS 8 / FAIL 0 / BLOCKED 0 / NOT_RUN 1**（`hot` = DDL 维度不适用 ⇒ **不是「已对齐」**）⇒ 9 个域逐列比对链路成立 ⇒ **Java-only、无 Go 仓、无 Go 工具链** |
| B2 | 🔴 **实跑抓出缺陷①：`docker_api.image_exists` 把 tag 丢了** | B | 症状：R6 首跑报 `GET /images/postgres/json ⇒ HTTP 404 ⇒ 查镜像失败` ⇒ **9 域全 BLOCKED**（明明 registry 里有 `library/postgres:16-alpine`）。<br>根因：`GET /images/{name}/json` **省略 tag** ⇒ daemon 按 `:latest` 解析 ⇒ `postgres:16-alpine` 被误判。<br>⚠️ **同源第二个面**：若 `:latest` 恰存在则**假阳性** True ⇒ 跳过拉镜像 ⇒ 随后「起容器失败」。<br>修法：按 `name:tag` 完整引用查 + **404 返回 False**（正常答案）而非抛异常。⇒ 已加**第 13 道静态门** `ci/scripts/check-docker-api-selftest.py`（11 条断言） |
| B3 | 🔴 **实跑抓出缺陷②：exec-with-stdin 的线格式错了 ⇒ daemon 根本不消费 stdin** | B | 症状：R6 第二个域起 PG 成功，但 `psql -f -` 永远等不到 EOF ⇒ exec 一直 Running ⇒ 表现成「超时」（**根因离症状很远**）。<br>根因：我方写法是 `POST /exec/{id}/start` + **JSON 请求体** ⇒ daemon 走 **200 + chunked 普通响应**路径 ⇒ **不接 stdin**；docker CLI 的写法是**无请求体 + 参数走 query + `Connection: Upgrade`** ⇒ daemon 才回 **101 劫持**。<br>⚠️ 叠加第二个坑：101 之后 `http.client` 会把 `conn.sock` 置 None ⇒ 旧实现 `conn.sock.sendall` ⇒ `AttributeError` ⇒ **整个 R6 崩在 Traceback**（而不是判 BLOCKED）。<br>修法：改为**手写原始 HTTP**（自建 socket / 自读响应头 / 裸流多路复用解码）+ R6 检查器把**任何意外异常归 BLOCKED(2)**。⇒ 真机对拍：`out='DONE\n23\n'`（与 docker CLI 完全一致）；自测扩到 11 条断言，**变异（退回带 JSON 体的写法）⇒ 3 条断言转红**（红在断言、无 Traceback），真源字节已恢复 |
| B4 | 🔴 **实跑抓出缺陷③：CI 的 maven 镜像里没有 python3 ⇒ R5 的比对逻辑跑不了** | B | 症状：`ci/scripts/check-errmsg-contract.sh: line 239: python3: command not found` ⇒ **每个域都被记成 BLOCKED**。<br>根因：R5 需要**两种互斥运行时** —— 跑 Java 侧探针要 **JDK+Maven**（探针 import 的是各域**编译产物**里的 `ErrorCode`），做基准比对要 **python3**；而 `maven:3.9.11-eclipse-temurin-21`（Ubuntu 24.04）**没有 python3**。<br>修法：R5 拆成**两个内层容器**（顺序执行、共用同一 bind mount）：内层① maven 出 Java 侧 dump → 内层② python 做比对。⇒ dump 目录放 `$PWD/errmsg-dump`（**不在 `repo/` 里** ⇒ 不进 task outputs ⇒ 不跨 build 残留）；dump 阶段**不记账**（还没比对 ⇒ 无从判 PASS/FAIL）⇒ 杜绝「没比对就当通过」 |
| B5 | 🔴 **实跑抓出缺陷④：`dependency:build-classpath` 单独跑 ⇒ 干净环境解析不到上游模块** | B | 症状：R5 的 `task` 域报 `Could not find artifact com.rag:task-proto:jar:0.0.1-SNAPSHOT` ⇒ 该域 BLOCKED（看着像依赖问题，**其实是 reactor 没编译**）。<br>根因：`mvn -pl … -am dependency:build-classpath` **单独跑** ⇒ reactor 里上游模块（`task-proto`/`common`）没有编译产物 ⇒ Maven 只能从本地仓库找它们的 jar ⇒ 干净环境（CI 的 `.m2` 缓存里没有已安装的 `com.rag:*`）必然失败。<br>修法：① `compile` 与 `dependency:build-classpath` **同一次 mvn 调用** ⇒ reactor 内直接用上游的 `target/classes`；② `-Dmdep.outputFile` 改**相对路径** `target/ci-cp.txt` ⇒ 每个 reactor 模块写自己 basedir 下的文件（绝对路径会互相覆盖 ⇒ 拿到的不是目标模块的 classpath）。<br>本地实证：造了一个**去掉 `com/rag`** 的 `.m2` 仓库视图模拟干净环境 ⇒ task 域 dump 产出成功；全 9 域 dump ⇒ compare ⇒ **PASS 7 / FAIL 0 / BLOCKED 0 / PENDING 2（EXIT=3）**（**签字前读数**）⇒ 🆕 **2026-10-31 用户签字后复跑 ⇒ PASS 9 / FAIL 0 / BLOCKED 0 / PENDING 0（EXIT=0）** |
| B6 | ✅ **静态守门在真实 CI 镜像内 12/12 通过**（含新增的第 12/13 道门） | B | `docker run -v <clone>:/build/repo -w /build python:3.12-bookworm sh repo/ci/scripts/run-static-gates.sh` ⇒ **12 道全绿 / 0 失败**。<br>⚠️ **一个重要的台架陷阱**：从 **git worktree** 挂进容器跑 ⇒ 前 3 道门报「没收集到任何文件 ⇒ 无法判定」⇒ 因为它们用 `git ls-files` 收集文件，而 worktree 的 `.git` 是**指针文件**、主仓 worktrees 目录没挂进容器 ⇒ **不是门禁缺陷，是台架缺陷**；从**纯 clone**（= Concourse 的实际形态）跑 ⇒ 12/12 全绿 |
| B7 | ✅ **触发面静态一致性 7/7 通过**（精确集合比对，非模糊的「有触发就算」） | A/B | `bash ci/verify-triggers.sh --static` ⇒ 从生成器产出的 `paths` 算出触发集合，与**手工推导的意图集合**逐条精确比对 ⇒ **7/7 一致**。<br>⇒ 新增**第 12 道静态门** ⇒ 生成器漂移会被当场抓住。<br>附带确认了一条**可被检查的性质**：`service/common/**` 的改动**不会**触发两条契约流水线（契约门只听 `ci/contracts/**` 与各域 `errmsg`/`db/migration` 路径）⇒ 此前是「没人验证过的假设」，现在是**断言** |
| B8 | 🔴 **宿主磁盘 100% 满的根因定位与处置** | B | `/System/Volumes/Data` 一度只剩 **216 MiB** ⇒ Docker containerd 内容库块级 EIO ⇒ daemon 挂死 / Concourse 整栈退出（**连续崩 3 次**）。<br>定位：**元凶是 141 个 Docker 卷**（Concourse 的 build/worker 卷 + 各 lane 的探针残留）⇒ `docker volume prune` 一次回收 **133.9 GB** ⇒ 可用空间 216 MiB → **120 GiB** ⇒ daemon 与 Concourse 全部恢复。<br>⇒ 结论：**这不是「环境时好时坏」，是可定位、可清理的具体占用**；记此条是为了下次不必重新踩一遍 |
| B9 | ✅ **触发面在真实服务端验证通过：7/7 精确集合一致** | B | `bash ci/verify-triggers-server.sh` ⇒ **EXIT=0**。<br>读数（**真实 commit ⇒ 真实 build**，逐条精确集合比对）：<br>· 改 `service/common/**` ⇒ **11 条日常**触发、**2 条契约不触发**<br>· 改 `service/users/users-common/**` ⇒ 9 条（**不含 `rag-task`**）<br>· 改 `users/user` proto ⇒ 6 条<br>· 改 `article` proto ⇒ 4 条（`article`/`comment`/`favorite`/`hot`）<br>· 改 `service/users/pom.xml`（祖先 pom）⇒ 9 条<br>· 改 `ci/**` ⇒ **全部 13 条**<br>· 改 `docs/**` ⇒ **无**（0 条）<br>⇒ 与**静态**口径（`verify-triggers.sh --static`，7/7）**逐条一致** ⇒ 静态门（#12）站得住 |
| B10 | 🔴 **服务端触发面验证踩出的三个测量陷阱**（都不是门禁缺陷） | B | ① **暂停的 job 不产生 build** —— 13 条 job 全 `pause-job` 后推 3 个 probe commit ⇒ **零 build**（调度器对暂停的 job 连资源检测都不做）⇒ 「暂停省算力」会让触发面**完全不可观测** ⇒ 改用**轻量版流水线**（`paths` 逐字节照抄 + 空任务 ⇒ 零算力、语义等价）。<br>② **「等第一个 build 出现」会测出假的不符** —— 13 条流水线各有自己的 git resource 实例 ⇒ 检查时刻散开 ⇒ 等到第一个就快照会得到**残缺**集合，且**上一场景的 build 会串场**（实测读数整体错位一格）⇒ 改成等集合**稳定**（≥90s 且连续 30s 无变化，上限 240s）。<br>③ **`check_every: 15s` 会让 git 资源的 check 反复失败** —— ATC 日志里 `check-step.finished` 与 `run.finish.failed` 成对出现 ⇒ 资源状态 `failed` ⇒ **零 build** ⇒ 去掉后资源 `succeeded`、轻量 build `succeeded`（6s）⇒ **测量靠「等稳定」，不靠调资源间隔** |


### 4.5 🆕 2026-10-31（签字落码轮）：R5 的 8 条 `pending` 销项 + **五条事实订正 + 一条方法论**

> 本节记的是**用户裁定落地**这一轮，以及**本轮核出的五条与「读数 / 断言 / 口径」有关的事实订正**。
> ⚠️ **订正不靠改写历史**（共享分支的历史改写代价大于收益）⇒ 事实订正一律**记在台账里**。
> 📌 **订正索引**：**B12**（代提交的「停摆」误判）· **B13**（「主干红」误读）· **B14**（`BLOCKED` 瞬时值判读）·
> **B16**（`U+FFFD` 五种身份）· **B17**（「解暂停就能重跑」前提不完整）⇒ 另 **B15**（变异「靶子消失」方法论）· **B11**（本轮读数）。

| # | 结论 | 层级 | 依据 |
|---|---|---|---|
| B11 | ✅ **R5 的 8 条 `pending` 全部销项 ⇒ 契约门由「有意为红」转绿** | A | 用户裁定（2026-10-31）：**「comment 3 条 + like 4 条签、foreign_plain 降级为 closed」**。<br>落笔：签字档新增 **`SD-2026-10-31-01`**（like · `docs/Intentional_Deviations/like/rpc.md`）与 **`SD-2026-10-31-02`**（comment · `docs/Intentional_Deviations/comment/common.md`）⇒ 基准 `like.txt` 4 条 → `approved` + `foreign_plain` → `closed`（依据 `D-7` · **比较点错配**）；`comment.txt` 3 条 → `approved`。<br>读数：**PASS 9 / FAIL 0 / BLOCKED 0 / PENDING 0 · EXIT=0**（原 EXIT=3）；差异台账 approved 31→**38** · closed 2→**3** · technical 13 · pending 8→**0**。<br>清单侧：**PASS 仍 = 23**（`APPROVED_DIFFERENCE` 不计入）· **FAIL 1→0** · 缺口 **19→18**。<br>⇒ ⚠️ **签字 = 承认并接受差异 ≠ 证明两侧一致** ⇒ PASS **不会**因签字而上升。<br>⚠️ **Task 内重跑尚未执行，且「解暂停就能重跑」是错的**：canonical 的 git resource **pin 的是 branch（不是 commit）** ⇒ 跑的是**远端 tip**；实测**两个远端的 tip 都没有 `ci/` 目录**（`origin` = `a2820125`（10-27 02:35）· **`upstream` = `a3884104`（10-30 02:17）⇒ 流水线 canonical 实际 pin 的是 `upstream`**）⇒ **连 `ci/contracts/errmsg/` 目录都不存在** ⇒ ⇒ 现在解暂停重跑，R5 门**连基准都读不到** ⇒ 必然不是 PASS 9 ⇒ ⇒ 正确顺序：**先 push（需用户授权）→ 再解暂停 → 再重跑** |
| B12 | 🔴 **事实订正①：`615cf933` 提交信息里的「CI lane 停摆」不成立** | — | 该笔写「CI lane 于 19:49:22 收到用户裁定后即遇 Token Plan 配额上限（42212）⇒ 工作树停在未提交状态 ⇒ 由收口执行者代落」⇒ **CI lane 未停摆**，裁定后一直在完整落码 ⇒ 该笔内容**全部来自 CI lane 工作树**（含两个变异套件的修复）⇒ **无内容损失**。<br>⇒ 该笔**已合入主干** ⇒ **不改写历史** ⇒ 订正记在此处。<br>⇒ **教训**：「**工作树有未提交改动**」≠「**那个 agent 停摆了**」⇒ 并发写权下**代提交前必须先确认对方是否真的在动**（查工作树 mtime / 会话活跃度），否则会**把在产的活儿记成停摆** |
| B13 | 🔴 **事实订正②：「主干红」在本 lane 的读数不成立** | — | CI lane 在**自己落后主干 250+ 笔的分支树**上读到 `tools/check-ledger-selfcheck.py` **EXIT=1**（task 域 Go 侧 import 映射）⇒ 在**主干树上实测 EXIT=0**（层 0~4 全过）⇒ ⇒ **「lane 报的主干红」在主干不成立**。<br>⇒ 纪律：**凡要报「主干红」，必须先在主干树上复跑**；且**不要用 `git stash`**（会偷并发 agent 的改动）⇒ 用**只读**方式（另开 clone / `git worktree`）复核 |
| B14 | 🟡 **判读纪律：`BLOCKED` 可能是**瞬时**环境值 ⇒ 重跑才定性** | — | 收口执行者首次跑 R5 门时出现 **1 次 `BLOCKED(article)`** ⇒ 实测是**容器启动竞态**的瞬时值 ⇒ **重跑即 PASS 9** ⇒ 记为**环境性瞬时值、不记为缺陷**。<br>⇒ 与既有纪律一致：**环境问题绝不能报成 FAIL**；且**反过来也不能默认当噪声忽略** ⇒ 判据 = **重跑一次**：仍 BLOCKED ⇒ 才是环境侧缺陷；复现 PASS ⇒ 瞬时值（**须记录**） |
| B15 | 🔴 **方法论：变异套件的「靶子消失」会被读成「守门抓不到」** | A | 签字后变异抓取率掉了（10/10→**9/10**、11/11→**10/11**）⇒ 根因**不是守门变弱**，而是**靶子没了**：<br>· `check-requirements-mutations.py` **M7** 打的是「抹掉现有 **FAIL** 条目的 `gap_note`」，而 R7 规则只对 `status=FAIL` 生效 ⇒ 签字后 FAIL 清零 ⇒ **变异落空**（守门返回 0 ⇒ 看起来像「抓不到」）；<br>· `check-errmsg-contract-mutations.py` **M7** 打的是「**pending** → approved」⇒ pending 清零 ⇒ 同类落空。<br>修法（**只改变异脚本、不动守门规则**）：M7 改为**自建靶子**（把一条 `NOT_VERIFIED` 改成 `FAIL` 且删 `gap_note` / 退回挑 `approved` 并换成**查不到的** id）⇒ 回到 **10/10** 与 **11/11**。<br>⇒ 🆕 两个套件都加了「**变异必须真的改写了内容**」自证 ⇒ **落空**单独记为「**变异落空**」，不再与「守门抓不到」混为一谈。<br>⇒ **可复用**：任何「打靶式」自证（变异 / 探针 / 断言）**都要先确认靶子还在** ⇒ 否则「自证失败」会被归因成「守门有洞」，修错方向 |
| B16 | 🔴 **事实订正③：`U+FFFD` 有**五种**身份 ⇒ 只改第 ① 类（真乱码）＋第 ⑤ 类（改描述性写法）** | — | 本 lane 曾报「主干 `ci/**` 与 `tools/**` **零 U+FFFD** ⇒ 乱码只在 docs 侧」⇒ ⚠️ **该推论不成立**：那句话只覆盖了 `ci/`+`tools/` 两个目录，却推到了全仓。<br>**全仓实测**（当时的快照读数 —— ⚠️ **已被 G1 的乱码修复改变 ⇒ 当前值见下方「当前实测」**）：**9 个文件 / 25 个 U+FFFD** —— docs 侧 6 个文件（users 域台账 4 个 + Pitfalls 2 个）＋ **Java 侧 3 个文件**（`ArticleApiMvcPipelineTest.java` ×2 · `HotProtoWireParityTest.java` ×1 · `UserApiGoStyleBodyContentTypeTest.java` ×1）。<br>⇒ **`U+FFFD` 有五种身份**（⚠️ 口径对齐**当前主干** `docs/Pitfalls/_cross/testing-pitfalls.md` **M-38** —— 已从三类演进为**五类**）：① **真乱码**（裸的、未登记的）② **被登记的样本**（台账在**登记乱码本身** —— 如 `docs/DRIFT/users/user/api.md` 的「2026-10-27 第十四轮附记」乱码登记表，已有「重建 + 核对依据」两列）③ **故意的夹具 / 被测对象**（断言字面量里**含 U+FFFD** 的被测对象；`// U+FFFD` 明写的测试夹具）④ **故意保留的记录 / 引文**（引的是**终端输出**或**用户输入原文** ⇒ 改了 = 篡改记录）⑤ **文档引文里嵌入的坏字节**（写文档时把**真字节**带进正文 ⇒ 处置 = **改描述性写法**，不是还原原字）。<br>⇒ ⇒ **只改第 ① 类**（真乱码）＋ 第 ⑤ 类（**只改写法**、不改字节语义）⇒ **动了 ②③④ 就毁掉登记、引用与守门语义** ⇒ ⚠️ **「全仓扫 U+FFFD ⇒ 逐处替换」这个直觉是错的**。<br>⇒ **当前实测**（2026-10-31 23:39 · 当前树 · **逐处人工定性**）：**8 个文件 / 22 个 U+FFFD / 15 处（语义位置）** —— ① **真乱码 1 处 / 3 个**（`service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoWireParityTest.java:174` —— 3×FFFD = 1 个完整汉字被替换，**只在 `@DisplayName` 里 ⇒ 不影响任何断言**）② **被登记的样本 3 处 / 8 个**（`docs/DRIFT/users/user/api.md:3622`(3) + `:3623`(2) + `docs/DRIFT/users/user/rpc.md:2614`(3)）③ **夹具 / 被测对象 3 处 / 3 个**（`ArticleApiMvcPipelineTest.java:1143`「`// U+FFFD`」· `:1159` `indexOf` · `UserApiGoStyleBodyContentTypeTest.java:377` `assertFalse(...contains(...))`）④ **保留的记录 / 引文 3 处 / 3 个**（`docs/Pitfalls/users/user/rpc.md:183`「末字已不可恢复」· `docs/Pitfalls/message/rpc.md:1074` / `:1084` 的 `CONTAINER` 终端输出引文）⑤ **文档引文里嵌入的坏字节 5 处 / 5 个**（`docs/DRIFT/users/user/api.md:3627` / `:3628` · `docs/DRIFT/users/user/rpc.md:2616` · `docs/DRIFT/users/vs-common.md:1428` / `:1681` —— 写文档时把真字节带进正文 ⇒ **改描述性写法**）。<br>⇒ ⚠️ **与 M-38 读数的三处口径差异（事实记录 · M-38 在 `docs/Pitfalls/` = SHARED 面 ⇒ 本 lane 只记差异、不改它）**：(a) M-38 的「**9 个文件 / 25 个 / 16 处**」是 **G1 修掉 `docs/DRIFT/users/_shared.md:1268` 那处真乱码之前**的快照 ⇒ 当前 = **8 个文件 / 22 个 / 15 处**；(b) M-38 的 per-class 列把 `HotProtoWireParityTest.java:174` 的 3 个**同时计入 ① 与 ③** ⇒ **③ 应为 3 处 / 3 个**；(c) M-38 的 ⑤ 类写「当前仓内 **0 字节**」⇒ **实测仍有 5 处**（上表 ⑤）⇒ 那一处处置尚未做完。<br>⇒ **教训**：**局部扫描不能推全仓结论** ⇒ 报「全仓无 X」前必须**真的全仓扫**；且**台账里的计数带核对日期** ⇒ 主干前进了，读数就会变（本次即从 9/25/16 变为 8/22/15）。|
| B17 | 🔴 **事实订正④：「解暂停就能重跑」是错的 —— 流水线 pin 的是 branch** | A | 本 lane 多处写「Task 内重跑待**合入 + 解暂停**后补跑」⇒ ⚠️ **前提不完整**。<br>实测 canonical 的 git resource：`uri = https://github.com/KASW-UI/RAG.git` · `branch = do-my-own-gh` ⇒ **pin 的是 branch（不是 commit）** ⇒ 跑的是**远端 tip**。<br>⚠️ **指针精确化（2026-10-31 23:39 订正）**：canonical 的 git resource **实读 Concourse 上流水线配置** = `uri = https://github.com/KASW-UI/RAG.git` · `branch = do-my-own-gh` ⇒ 该 GitHub 仓在本地对应远端是 **`upstream`**（`ssh://git@ssh.github.com:443/KASW-UI/RAG.git`）**不是** `origin`。<br>实测**两个远端的 tip 都没有 `ci/` 目录** —— ① **`origin`**（CodeArts 镜像 `huawei-codearts:…/rag.git`）tip = `a2820125`（**10-27 02:35**）② **`upstream`**（**GitHub `KASW-UI/RAG.git` ⇒ 流水线 canonical 实际 pin 的就是它**）tip = `a3884104`（**10-30 02:17**）⇒ `git ls-tree origin/do-my-own-gh:ci/` 与 `git ls-tree upstream/do-my-own-gh:ci/` **均报 `Not a valid object name`**⇒ **连 `ci/contracts/errmsg/like.txt` 都没有** ⇒ ⇒ **现在解暂停重跑，R5 门连基准都读不到** ⇒ 必然**不是** PASS 9（预期 BLOCKED/FAIL）。<br>⇒ 本地主干**两个远端都未推**（2026-10-31 23:39 实测）：`origin` **ahead 1045 笔** / **`upstream` ahead 445 笔**⇒ ⚠️ **push 目标 = `upstream`**（本分支跟踪的是 `upstream/do-my-own-gh` ⇒ `git push` 默认就推它）⇒ **别只推 origin**（推了 origin，流水线仍读不到基准）。<br>⇒ ⇒ **正确顺序：先 push（需用户授权）→ 再解暂停 → 再重跑**；且若要**确定性**复现，应把 pipeline 的 git source **pin 到具体 commit**（`tag:`）而不是 branch。<br>⇒ **教训**：**「本地跑绿」与「CI 里跑绿」读的是两棵树**（本地工作树 vs 流水线 pin 的远端 tip）⇒ 报「CI 里已跑绿」前必须核对**流水线 pin 的那个 commit**。<br>📌 **时点标注（2026-10-31 23:44 · push 完成后 —— 上面「两个远端的 tip 都没有 `ci/`」是**推前**状态，勿读成现状）**：合并收口执行者已把 `do-my-own-gh` 推到 **`upstream`**（GitHub `KASW-UI/RAG.git`）⇒ 远端 tip = **`925432e3`**（2026-10-31 23:44 · `merge(ci): 合入 CI lane 口径订正轮`）⇒ 实测该 commit 上 **`ci/` 目录已存在**（`BASELINE.md` / `DELIVERY.md` / `README.md` / `contracts/` / `deploy.sh` / `generate-pipelines.py` / `pipelines/` / `requirements/` / `scripts/` / `tasks/` / `verify-triggers-server.sh`）⇒ 且 **`ci/contracts/errmsg/` 的 10 个域基准文件全部就位**（article / comment / favorite / follow / hot / like / message / task-api / task-rpc / users）⇒ ⇒ **「连基准都读不到 ⇒ 必然不是 PASS 9」那个前提已闭环** ⇒ 解暂停后 R5 门**有基准可读**。⚠️ **但「推上去」≠「CI 里跑绿」**：① **13 条流水线刚解暂停**，canonical 的 git resource **从未成功 check 过**（版本数 = 0）⇒ 正在做**首次全量 clone**⇒ 而本机 GitHub 克隆**极慢**（§五 实测 `--filter=blob:none` 3.2 MB 元数据 **2m19s / 19 KiB/s** ⇒ 「git 资源的 check 时好时坏」）⇒ **首次 check 可能很久**；② 流水线 **pin 的是 branch** ⇒ 跑的是「跑那一刻的远端 tip」⇒ 报「CI 里跑绿」时**必须带上 build 号 + 当时 pin 的 commit**。⇒ 📌 **本条的后续读数（build 号 / pin 的 commit / 各门 PASS 计数）待首轮 build 跑完后回填** |

---

## 五、环境前置与实测约束（本机，2026-10-30）

| 约束 | 实测 | 影响与对策 |
|---|---|---|
| 直连 Docker Hub | `auth.docker.io` / `registry-1.docker.io` **15s 超时无响应** | ⇒ 镜像名一律带镜像源前缀；worker 镜像需人工下载 |
| 镜像源可用性 | `docker.m.daocloud.io` 代理 `library/*` ✅；`docker.1ms.run` 代理全量但 blob CDN 会 EOF ⚠️；非 `library/*` 命名空间 daocloud **403** | ⇒ 本地 registry 里要按**真实仓库路径**推（`library/postgres` 与 `apache/kafka` 不同） |
| **GitHub 克隆极慢** | `git clone --bare --filter=blob:none`（3.2 MB 元数据）**2m19s，均速 19 KiB/s** ⇒ git 资源的 check **时好时坏** | ⇒ 本地验证改用本地 git 服务（同一份 clone **13.7s**）；canonical 仍指向 GitHub（真实环境带宽正常时没问题） |
| **Maven Central 慢** | 115 KB/s（整棵依赖树几十分钟） | ⇒ `maven_mirror` 变量；本地静态镜像实测 **77 MB/s** |
| worker 运行时 | **containerd 3.0**（Concourse 8 无 docker runtime） | ⇒ task 镜像由 containerd 拉；本地 registry 走 **HTTP** 已被实测打通 |
| 资源类型镜像 | Concourse 8 的资源类型是 **rootfs.tgz 打包**（wolfi-base + git 二进制），**不从 Docker Hub 拉** | ⇒ 消除了 `alpine/git` 这条 Docker Hub 依赖 |
| macOS socket 路径 | 实际在 `~/.docker/run/docker.sock` | ⇒ DinD 形态不依赖它 |
| **DinD 内层网段** | 内层 dockerd 默认 `172.17.0.0/16` 与外层 docker0 同段 ⇒ 抢路由 | ⇒ 显式 `--bip 10.250.0.1/16 --default-address-pool base=10.250.0.0/16,size=24`（`DIND_BIP`/`DIND_POOL` 可覆盖） |
| **宿主磁盘** | `/System/Volumes/Data` 一度 **100% 满（剩 216 MiB）** ⇒ Docker containerd 内容库块级 **I/O error** ⇒ daemon 卡死 / ATC 500 | ⇒ **这是所有"daemon 卡死"的真根因**；CI 侧已把可调项全部变量化，但**磁盘是宿主环境前提**，需先腾空间。<br>🆕 **2026-10-31 已定位元凶并处置**：**141 个 Docker 卷**（Concourse 的 build/worker 卷 + 各 lane 探针残留）⇒ `docker volume prune` 一次回收 **133.9 GB** ⇒ 可用空间 216 MiB → **120 GiB** ⇒ daemon 与 Concourse 全部恢复。<br>⇒ 结论：**这不是「环境时好时坏」，是可定位、可清理的具体占用** ⇒ 下次不必重新踩一遍 |
| **暂停的 job 不产生 build** | 13 条流水线的 job 全部 `pause-job` 后，推送 3 个 probe commit ⇒ **一条 build 都没产生**（调度器对暂停的 job 连资源检测都不做） | ⇒ 「暂停流水线省算力」会让**触发面完全不可观测** ⇒ 触发面服务端验证改用**轻量版流水线**（`paths` 逐字节照抄 + 空任务）⇒ 零算力且语义等价 |
| **CI 镜像缺 python3** | `maven:3.9.11-eclipse-temurin-21` = Ubuntu 24.04 ⇒ `command -v python3` ⇒ MISSING | ⇒ R5 拆成**两个内层容器**（maven 出 dump / python 比对）；R6 的内层镜像**本来就是** python ⇒ 不受影响 |
| Docker Desktop 稳定性 | 磁盘满期间 daemon 无响应、内容库块级 EIO；腾出 3.1 GiB + 重启后仍需进一步修复 | ⇒ 属宿主环境问题；CI 侧已把可调项全部变量化 |

---

## 六、CI 抓出的**仓内真实缺陷**（已随本 PR 修复）

### 6.1 `service/users/admin/admin-api/pom.xml` 父 POM 无法在干净环境解析

**现象**（CI 内，Maven 扫描全模块时）：

```
[FATAL] Non-resolvable parent POM for com.rag:admin-api:0.0.1-SNAPSHOT:
        The following artifacts could not be resolved: com.rag:service:pom:0.0.1-SNAPSHOT (absent)
        and 'parent.relativePath' points at wrong local POM @ line 6, column 13
```

**根因**（可复算）：

| 事实 | 值 |
|---|---|
| `service/users/admin/pom.xml` | artifactId = `admin`，parent = `users`，**`<modules>` 登记了 `admin-api`** |
| `admin-rpc` 的 parent | `com.rag:admin` ⇒ 默认 `relativePath`（`../pom.xml`）正好命中，✅ |
| `admin-api` 的 parent | `com.rag:service` ⇒ 默认 `relativePath`（`../pom.xml`）指向 `service/users/admin/pom.xml`，**坐标是 `admin` ≠ `service`** ⇒ Maven 判 "wrong local POM" ⇒ 回落仓库找 `com.rag:service:pom` |
| 而本仓**没有制品仓库** | ⇒ `com.rag:service:pom:0.0.1-SNAPSHOT` 在干净 `~/.m2` 里不存在 ⇒ FATAL |

**为什么开发机不报错**：`~/.m2/repository/com/rag/service/0.0.1-SNAPSHOT/` 里躺着**旧的已安装 POM**
⇒ 父 POM 从本地仓库解析成功 ⇒ 掩盖了 `relativePath` 的问题。
⇒ **CI 的第一个作用就是这个：把"靠本机热缓存才成立的构建"变成"干净 checkout 也能成立"**。

**最小修法**（**坐标不变 ⇒ 继承内容不变 ⇒ 语义等价**，只改"去哪里找父 POM"）：

```xml
<parent>
    <groupId>com.rag</groupId>
    <artifactId>service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../../pom.xml</relativePath>   <!-- service/pom.xml -->
</parent>
```

⚠️ **路径必须逐层数对（这个坑本 PR 自己踩过一次）** ——
`admin-api` 的目录是 `service/users/admin/admin-api/`，往上数三层才是 `service/`：

| relativePath | 解析到 | artifactId | 判定 |
|---|---|---|---|
| `../pom.xml`（Maven 默认） | `service/users/admin/pom.xml` | `admin` | ❌ 坐标不符 ⇒ 原始缺陷 |
| `../../pom.xml` | `service/users/pom.xml` | `users` | ❌ **坐标仍不符** ⇒ 本 PR 第一版就写成了这样 |
| `../../../pom.xml` | `service/pom.xml` | `service` | ✅ |

⇒ **"离得更近的那份 pom" ≠ "坐标对的那份 pom"**；
⇒ 判断依据只有一条：**那份文件的 groupId/artifactId 是否等于声明的 parent**。

**全仓扫描结论**：52 个 pom 里**只有这 1 处**真缺陷
（根 pom 那条命中是外部 `spring-boot-starter-parent` 父 POM，且它的
`<relativePath></relativePath>` 是**空串** ⇒ Maven 跳过本地查找 ⇒ 合法，不是缺陷）。

✅ **已随本 PR 修复**（commit `fix(pom): admin-api 父 POM relativePath 改为 ../../../pom.xml`）——
坐标与继承内容都不变 ⇒ 语义等价；不改它 ⇒ **所有域的 CI 都会红**。

### 6.2 为此新增的第 9 道静态守门

`ci/scripts/check-pom-parent-relativepath.py` —— **离线**扫全仓 pom，
按 Maven 的实际规则判定每个 `<parent>` 能否被本地 `relativePath` 正确解析：

| 情形 | Maven 行为 | 守门判定 |
|---|---|---|
| `<relativePath>` 缺省 | 默认 `../pom.xml` | 按 `../pom.xml` 判定 |
| `<relativePath></relativePath>`（空串） | **跳过本地查找**，从仓库解析 | **合法，不判失败** |
| relativePath 解析到目录 | 追加 `pom.xml` | 同 Maven |
| 目标文件不存在 | 回落仓库解析 | 不致命 ⇒ 只告警 |
| 目标文件坐标与声明的 parent 不符 | **致命**：`parent.relativePath points at wrong local POM` | **判失败** |

**变异验证**（证明它不是假绿）：

| 树上的状态 | 守门退出码 |
|---|---|
| 修复后（`../../../pom.xml`） | `0` ✅ |
| 变异成 `../../pom.xml`（本 PR 的第一版错解） | `1` ✅ 抓住 |
| 删掉 `relativePath`（回到 Maven 默认） | `1` ✅ 抓住 |

⇒ 它**同时**能抓住原始缺陷和"修错"的修复 ⇒ 值这道门。
住 `ci/scripts/` 而不是 `tools/difftest/` ⇒ 不写别人 lane 的共享面。

### 6.3 端口探测判据**只在 macOS/BSD 成立** ⇒ Linux CI 上恒判「端口段被占满」

**现象**（CI 内，article 域 test 阶段）：

```
java.lang.IllegalStateException: 真进程测试端口段 22400-22479 已被占满（本机并行会话太多？）
    ⇒ 换一段端口再试
```

⇒ 抛出点是 `RealGrpcPort.pick()`；它遍历 22400–22479 逐个 `isFree(port)`，
   80 个全判「不可用」⇒ 整段被弃用 ⇒ 报「已被占满」。

**根因**（`RealGrpcPort.isFree()` 的实现）：

```java
try (ServerSocket wildcard = new ServerSocket();      // ①
     ServerSocket specific = new ServerSocket()) {
    wildcard.setReuseAddress(true);
    wildcard.bind(new InetSocketAddress(port), 1);      // ② 绑 0.0.0.0:P
    specific.setReuseAddress(true);
    specific.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);  // ③ 绑 127.0.0.1:P
    return true;
}
```

② 与 ③ **同时持有**（两个 socket 都在 try 里）。
「两个地址都要探」这个**意图是对的**（注释也写明了：通配地址被别人占着 ⇒ 真进程起不来），
但「同时持有两个 socket」这个**实现只在 BSD/macOS 成立**：

| 平台 | ② 绑完再 ③ 绑 127.0.0.1:P | 结果 |
|---|---|---|
| macOS / BSD | 允许（`SO_REUSEADDR` + 更具体地址覆盖通配地址） | ✅ 判「可用」 |
| **Linux** | `EADDRINUSE`（通配地址已覆盖该端口，Linux 不允许再绑更具体地址） | ❌ **恒判「不可用」** |

**实测证据**（同一份探针代码，两个平台）：

```
$ docker run --rm -v /tmp/IsFreeProbe.java:/p.java --entrypoint java \
      maven:3.9.11-eclipse-temurin-21 /p.java
  首个异常: java.net.BindException: Address already in use
  os=Linux  段内可绑端口数 = 0 / 80        ← Linux：80 个端口全部判"被占"

$ java /tmp/IsFreeProbe.java
  os=Mac OS X  段内可绑端口数 = 80 / 80   ← macOS：全段可用
```

⇒ **开发机（macOS）永远看不见这个缺陷** ⇒ 这类缺陷只有真 CI 才抓得到。

**波及范围**（全仓实测扫描，`wildcard.bind` + `specific.bind` 同款序列）：
**21 个文件、覆盖 9 个域** —— article / comment / favorite / follow / hot / like / message / task / users(admin+user)，
每个域的 `RealGrpcPort.java` 与 `RealTomcatPort.java` 都是同款。
⇒ 凡是依赖"真进程占固定端口"的守门类测试，在 Linux CI 上**必然红**。

（另有 16 个文件用的是**安全形态** —— 只做单次 bind 探测、探完即关，
 例如 message/like/task 的 `GrpcTestPort.java` ⇒ 那些域不受影响。）

**建议修法（⚠️ 本 PR 未改 —— 属各域 lane 的测试代码写权，不在 CI lane 范围内）**：
把两个探针**改成先后进行**（探完即关），而不是同时持有：

```java
static boolean isFree(int port) {
    if (!bindOk(port, false)) { return false; }   // ① 0.0.0.0:P 能不能绑
    if (!bindOk(port, true))  { return false; }   // ② 127.0.0.1:P 能不能绑
    return true;
}
```

- 语义**不变**：仍然要求「通配地址」与「回环地址」都没被第三方持有；
- 两个平台都成立（Linux 上 ① 的 socket 已关闭，② 自然能绑）；
- 代价：两次探测之间有一个极小的竞态窗口（第三方可能在窗口内抢口）——
  相比"Linux 上整段不可用"是**严格改善**。

**为什么 CI 不自己绕过**
- 绕过的三种做法（改端口段 / 跳过这些测试类 / 在 CI 里打补丁）**都是把缺陷藏起来**：
  ① 改端口段 ⇒ 绕开的是**判据本身坏了**这件事；
  ② 跳过测试类 ⇒ 那些守门从此**没人跑**，且"绿"是假的；
  ③ 在 CI 侧打补丁 ⇒ 改的是别人 lane 的文件 ⇒ 越权，且两份实现必然漂移。
- ⇒ **正确姿势是：让 CI 如实报红，把缺陷交回它的主人（各域 lane）**。
  本仓的规矩也是"测试要如实报告项目的坏，不得因为跑不通就去改被测对象"。

---

## 七、维护说明

### 7.1 新增一个微服务（域）

1. 在 `service/<新域>/` 建 `<域>-proto` / `<域>-common` / `<域>-rpc` / `<域>-api`，并在 `service/pom.xml` 登记。
2. 在 `ci/generate-pipelines.py` 的 `DOMAINS` 加一行：
   ```python
   ("rag-<新域>", "service/<新域>",
    ["service/<新域>/<新域>-api", "service/<新域>/<新域>-rpc"], "<新域> 域（<新域>-api + <新域>-rpc）"),
   ```
3. `python3 ci/generate-pipelines.py`（paths 由 pom 反推，**不用手写**）→ `--check` → `bash ci/deploy.sh`。

### 7.2 改了 pom 的模块依赖后

```bash
python3 ci/generate-pipelines.py          # 重生成
python3 ci/generate-pipelines.py --check  # 校验（应为 EXIT=0）
```

### 7.3 改了 `ci/` 里的任何东西

`ci/**` 在触发面里 ⇒ 11 条流水线会全部重跑。
若改了 `ci/tasks/*.yml`，**必须**重跑生成器（task 配置是内联进 pipeline 的）。

### 7.3b 🆕 改了「目标要求清单」或「契约基准」之后

| 改了什么 | 必须再跑 | 为什么 |
|---|---|---|
| `ci/requirements/requirements.json` | `python3 ci/scripts/check-requirements.py` + `check-requirements-mutations.py` | 清单的结构与六态口径由守门保证；变异测试保证**守门不是假绿** |
| `ci/contracts/errmsg/*.txt` | `bash ci/scripts/check-errmsg-contract.sh` + `check-errmsg-contract-mutations.py` | 基准变了 ⇒ 必须重判差异四态 |
| `ci/contracts/schema/*.json` | `python3 ci/scripts/check-schema-contract.py --all` + `check-schema-contract-mutations.py` | 同上 |
| `ci/contracts/proto/*.proto` | `python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto` | 同上 |
| `ci/scripts/dind-run.sh`（改了内层阶段编排） | `bash ci/verify-triggers-server.sh` | 触发面是**服务端**性质 ⇒ 只做静态检查不够 |
| `ci/pipelines/*.yml`（或 pom 依赖） | `python3 ci/generate-pipelines.py --check` + `bash ci/verify-triggers.sh --static` | 生成器与**意图集合**必须一致 |

> ⚠️ **签字流程（唯一的「让红变绿」的正当途径）**：
> 差异要转正 ⇒ **先**在 `docs/Intentional_Deviations/` 登记并签字 ⇒ **再**把基准文件里对应那条
> `DEVIATION|…` 的状态从 `pending` 改成 `approved` 并填上依据 id ⇒ **再**把清单里该要求的
> 状态改成 `APPROVED_DIFFERENCE` 并填 `deviation.id` ⇒ 守门会**机器回指**该 id（查不到 ⇒ FAIL）。
> ⇒ ⛔ **不要**为了让 build 变绿而改 Java 业务代码；⛔ **不要**自行把 `pending` 改成 `approved`
> 而不签字 —— 守门会拦（变异测试 M7/M8 就是专门测这个）。

### 7.4 常见故障与定位

| 症状 | 先查 |
|---|---|
| task 报 `repo/ci/scripts/... not found` | 触发面缺 `ci/**` ⇒ 取到的 commit 里没有 `ci/` |
| 内层 daemon 拉镜像 `connection refused` | 内层 dockerd 的 bridge 与外层同网段 ⇒ 查 `--bip` |
| 内层 daemon 拉镜像 404 | registry 里仓库路径不对（`library/postgres` vs `apache/kafka`） |
| **R6 报「查镜像失败」** | `docker_api.image_exists` 丢了 tag ⇒ daemon 按 `:latest` 解析 ⇒ 查 registry 里有没有该 tag（§4.4 B2） |
| **R6 迁移卡住 / exec 一直 Running** | exec-with-stdin 的**线格式**不对 ⇒ daemon 不消费 stdin ⇒ 必须照抄 docker CLI 的写法（§4.4 B3） |
| **R5 报 `python3: command not found`** | CI 的 maven 镜像**没有 python3** ⇒ 必须走**两个内层阶段**（§4.4 B4） |
| **R5 某域报 `Could not find artifact com.rag:…`** | `dependency:build-classpath` 单独跑 ⇒ 上游模块没编译 ⇒ 必须与 `compile` 同一次 mvn 调用（§4.4 B5） |
| `fly set-pipeline` 报变量未定义 | `registry` / `maven_mirror` 两个 var 都要给（`ci/deploy.sh` 会给） |
| 构建报 `Non-resolvable parent POM` | 仓内 pom 的 `relativePath` 缺陷（§六） |
| git 资源 check 反复失败 | 看 GitHub 链路带宽（§五）⇒ 换本地 git 服务 |
| **build 永远 pending** | 流水线或 job 被暂停 ⇒ `fly unpause-pipeline` / `fly unpause-job`（**暂停的 job 不会产生 build**，连资源检测都不做 ⇒ 触发面会完全不可观测） |

### 7.5 边界声明

- 只做 CI，**不做** CD / 发布 / 部署。
- **不修改**任何微服务业务逻辑、测试代码或构建系统配置来迎合 CI。
- 不引入 Jenkins / GitLab CI 等其它 CI 系统。
- YAML 里**没有任何**密码 / Token（GitHub 公开 HTTPS）。
- 不虚构测试结果：所有"通过/全绿"的断言都标了实测日期与命令。
