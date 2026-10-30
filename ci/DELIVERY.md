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

### 1.4 三个"变量化"的外部依赖（仓库里不写死任何机器地址）

| pipeline var | 默认 | 作用 |
|---|---|---|
| `registry` | `docker.m.daocloud.io` | 镜像源主机（`host` 或 `host:port`）。**带端口 ⇒ 本地 registry** ⇒ 内层 dockerd 按 HTTP + insecure 处理 |
| `maven_mirror` | 空（走默认 Central） | Maven 镜像源。**非空 ⇒ 优先查它，404 再回退 Central**（用「额外仓库」而非 `<mirror>`，避免把 Central 顶掉后无退路） |

---

## 二、文件清单（27 个文件 + 本文档）

| 路径 | 职责 | 真源？ |
|---|---|---|
| `ci/pipelines/rag-*.yml`（11 个） | 各域流水线定义（4 step） | ⛔ **生成物**，不要手改 |
| `ci/tasks/maven-build.yml` | 构建阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/maven-test.yml` | 测试阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/static-gates.yml` | 静态守门阶段 task 配置 | ✅ 唯一真源 |
| `ci/tasks/report.yml` | 结果汇总阶段 task 配置 | ✅ 唯一真源 |
| `ci/scripts/build.sh` | 嵌套容器内 `mvn package -DskipTests` | ✅ |
| `ci/scripts/test.sh` | 嵌套容器内 `mvn test`（先 source Testcontainers 环境） | ✅ |
| `ci/scripts/testcontainers-env.sh` | Docker 可达性前置断言 + host override + Ryuk 关闭 | ✅ |
| `ci/scripts/dind-run.sh` | DinD 外壳：起内层 dockerd → 跑嵌套构建容器 | ✅ |
| `ci/scripts/run-static-gates.sh` | 按序跑静态守门并汇总 | ✅ |
| `ci/scripts/collect-surefire.sh` | 读 `TEST-*.xml` 汇总用例数与失败清单 | ✅ |
| `ci/generate-pipelines.py` | 从 pom 依赖图生成 11 条 pipeline；`--check` 校验漂移 | ✅ |
| `ci/deploy.sh` | 校验 + `set-pipeline`（注入两个 var）+ `unpause-pipeline`（幂等） | ✅ |
| `ci/local-up.sh` | 本地环境一键起流（load tar → 可选本地 registry → 起 Concourse → 注册流水线 → 可选触发） | ✅ |
| `ci/README.md` | 使用说明 / 依赖矩阵 / 触发规则 / 环境约束 | ✅ |
| `ci/DELIVERY.md` | 本文档 | ✅ |

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
| **11 条流水线注册** | ✅ | `ci/deploy.sh` 执行完毕；`fly pipelines` 列出 11 条 `rag-*`（含 unpause） |
| **worker 从本地 registry 拉镜像** | ✅ | 探针流水线 21s 成功，层下载进度可见 ⇒ **containerd 能走 HTTP 拉本地 registry** |
| **task 容器出网** | ✅ | 探针：DNS(8.8.8.8 / 223.5.5.5) OK、`github.com:443` OK、`172.17.0.1:5010`（本地 registry）OK；`10.80.0.1:5010` FAIL（预期，网关无此服务） |
| **静态守门在真实 task 镜像内** | ✅ | `docker run python:3.12-bookworm sh ci/scripts/run-static-gates.sh` → **8/8 全绿** |
| **DinD 外壳（真实 privileged）** | ✅ | 内层 `dockerd` 起来 → 从本地 registry 拉到 `maven:3.9.11-eclipse-temurin-21` → Maven 构建启动 |
| 构建命令正确性 | ✅ | 宿主实测：`mvn -o -pl service/article/article-api,service/article/article-rpc -am -DskipTests package` → BUILD SUCCESS（14 模块 / 8.6s）；`mvn -o -pl service/common -am clean test` → 405/405 全绿（1:12） |
| **完整 4 step build 跑绿** | 🟡 **本机未跑完** | 见 §四.1 |

### 4.1 未跑完的那一项：原因与边界

**卡点（两层，都不是 CI 逻辑问题）**：

1. **宿主磁盘 100% 满** ⇒ `/System/Volumes/Data` 只剩约 1 GiB
   ⇒ Docker Desktop 的 containerd 内容库出现**块级 I/O error**
   （`blob sha256:… expected at /var/lib/desktop-containerd/…: input/output error`）
   ⇒ ATC 返 500、worker 拉不到镜像、`docker logs` 本身都读不出来。
   **这就是此前反复出现的"daemon 卡死"的真正根因**（包括本次 CI 开工前的那一次）。
2. 磁盘腾出 3.1 GiB 并重启 Docker Desktop 后，**内容库仍有 EIO**（`docker run` 读镜像 blob 失败）
   ⇒ 需要 Docker 侧的进一步修复（重建 VM 磁盘 / 清理损坏内容），属**宿主环境**操作，未擅自执行。

⇒ 完整 4 step 的端到端绿灯**在本机未取得**。

**已取得的等价证据**：演练 build 的日志逐段证明了
「git 取源 → DinD 起内层 daemon → 内层从本地 registry 拉到 maven 镜像 → 进入 Maven 构建」
这段链路是通的（见下表）。

| 演练 build | 结果 | 卡在哪 | 定位到的根因 |
|---|---|---|---|
| #1 | ❌ | `repo/ci/scripts/dind-run.sh: No such file` | **`ci/**` 不在触发面** ⇒ 取到的 commit 里还没有 `ci/` 目录 |
| #2 | ❌ | `dial tcp 172.17.0.1:5010: connection refused` | **内层 dockerd 默认 bridge `172.17.0.0/16` 与外层 docker0 同段** ⇒ 抢走路由 |
| #3 | ❌ | `Non-resolvable parent POM … com.rag:service:pom` | **仓内真实缺陷**（见 §六），非 CI 自身问题 |
| #4 | ⏸ | 宿主磁盘满 ⇒ Docker 内容库块级 EIO | 宿主环境问题（非 CI 逻辑） |

### 4.2 演练与 canonical 的差异（必须说清）

演练用的是**本地 git 服务**（`git://172.17.0.1:5012/gitd-rag.git`，分支 `ci-verify`），
内容 = 本地 HEAD + `ci/` 目录 + 一处演练专用的 pom 修复。
canonical 流水线指向 `https://github.com/KASW-UI/RAG.git` 的 `do-my-own-gh` 分支。

⇒ 演练验证的是**流水线机制本身**（取源/触发面/DinD/构建/守门/汇总）；
**未验证**的是 GitHub 链路上的完整绿灯 —— 该链路的可达性已单独实测通过
（`github.com:443` 从 task 容器内 TCP 连通），但**克隆极慢**（见 §五）。

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
| **宿主磁盘** | `/System/Volumes/Data` 一度 **100% 满（剩 617 MiB）** ⇒ Docker containerd 内容库块级 **I/O error** ⇒ daemon 卡死 / ATC 500 | ⇒ **这是所有"daemon 卡死"的真根因**；CI 侧已把可调项全部变量化，但**磁盘是宿主环境前提**，需先腾空间 |
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

### 7.4 常见故障与定位

| 症状 | 先查 |
|---|---|
| task 报 `repo/ci/scripts/... not found` | 触发面缺 `ci/**` ⇒ 取到的 commit 里没有 `ci/` |
| 内层 daemon 拉镜像 `connection refused` | 内层 dockerd 的 bridge 与外层同网段 ⇒ 查 `--bip` |
| 内层 daemon 拉镜像 404 | registry 里仓库路径不对（`library/postgres` vs `apache/kafka`） |
| `fly set-pipeline` 报变量未定义 | `registry` / `maven_mirror` 两个 var 都要给（`ci/deploy.sh` 会给） |
| 构建报 `Non-resolvable parent POM` | 仓内 pom 的 `relativePath` 缺陷（§六） |
| git 资源 check 反复失败 | 看 GitHub 链路带宽（§五）⇒ 换本地 git 服务 |

### 7.5 边界声明

- 只做 CI，**不做** CD / 发布 / 部署。
- **不修改**任何微服务业务逻辑、测试代码或构建系统配置来迎合 CI。
- 不引入 Jenkins / GitLab CI 等其它 CI 系统。
- YAML 里**没有任何**密码 / Token（GitHub 公开 HTTPS）。
- 不虚构测试结果：所有"通过/全绿"的断言都标了实测日期与命令。
