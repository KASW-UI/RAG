# RAG · Concourse CI

> 本目录是 RAG 仓（Spring Boot 重构产物）的 **持续集成** 定义。
> 范围严格限定在 CI：构建、测试、静态守门、结果汇总。
> **不含** CD / 生产发布 / Kubernetes 部署 —— 那不在本项目范围内。

---

## 快速开始

```bash
# 一次性：本地 Concourse 起流（load 镜像 tar → 起环境 → 注册 11 条流水线）
bash ci/local-up.sh                 # 公网镜像源
bash ci/local-up.sh --registry      # 额外起本地 registry（公网源不稳时用）
bash ci/local-up.sh --trigger rag-task   # 注册完顺带触发一条并跟踪日志

# 只看环境状态
bash ci/local-up.sh --status

# 只做校验（不需要服务端）
bash ci/deploy.sh --validate-only
```

Web UI：`http://localhost:8080`（`test` / `test`）

---

## 一、微服务与 Pipeline 对应关系

RAG 是 Maven 多模块仓：**10 个域 / 38 个组件 / 20 个可独立部署的 Spring Boot 进程**。
Pipeline 按**域**划分，不按组件划分 —— 理由见 §三。

| # | Pipeline | 覆盖模块 | 负责的可部署进程 |
|---|---|---|---|
| 1 | `rag-shared-common` | `service/common`、`service/users/users-common` | ——（library，无可执行 jar） |
| 2 | `rag-users-user` | `service/users/user/*` | `user-api`、`user-rpc` |
| 3 | `rag-users-admin` | `service/users/admin/*` | `admin-api`、`admin-rpc` |
| 4 | `rag-article` | `service/article/*` | `article-api`、`article-rpc` |
| 5 | `rag-comment` | `service/comment/*` | `comment-api`、`comment-rpc` |
| 6 | `rag-favorite` | `service/favorite/*` | `favorite-api`、`favorite-rpc` |
| 7 | `rag-follow` | `service/follow/*` | `follow-api`、`follow-rpc` |
| 8 | `rag-hot` | `service/hot/*` | `hot-api`、`hot-rpc` |
| 9 | `rag-like` | `service/like/*` | `like-api`、`like-rpc` |
| 10 | `rag-message` | `service/message/*` | `message-api`、`message-rpc` |
| 11 | `rag-task` | `service/task/*` | `task-api`、`task-rpc` |

**划分依据（三条，都可复算）**

1. **域 = Go 真源的域边界**。Go 侧 `service/<域>/{api,rpc}` 是独立 main 包，
   RAG 侧同名目录是独立 Spring Boot 应用 ⇒ 一一对应。
2. **同域的 api / rpc 不拆成两条**。两者由同一个 `<域>-proto` 定义契约、
   被同一个 `<域>-common` 供码，拆开会逼出"契约由谁负责"的额外问题，
   而收益只是少跑半个域的构建 ⇒ 不拆。
3. **users 域拆成 user / admin 两条**。这俩是**独立部署**的服务
   （各有 main 类与端口），且 Go 侧也是两组独立进程 ⇒ 拆开能让 admin 的改动
   不重建 user。
4. **共享层单独一条**。`service/common` 被**全部 20 个模块**依赖，
   `service/users/users-common` 被 **10 个模块**依赖 ⇒ 它们不是任何单一域的私有物。

---

## 二、依赖矩阵（触发的真正依据）

从 pom 的 `<dependencies>` 反查（不是按目录名猜）：

| 共享模块 | 消费者数 | 被哪些域消费 |
|---|---|---|
| `service/common` | **20** | **全部 10 个域** |
| `service/users/users-common` | **10** | article / comment / favorite / follow / like / message 的 **api** + user / admin 的全部 4 个 |
| `user-proto` | 7 | article-api、favorite-rpc、follow-rpc、message-rpc、user-api、user-rpc、admin-api |
| `admin-proto` | 3 | admin-api、admin-rpc、**message-rpc** |
| `message-proto` | 5 | **article-rpc、comment-rpc、like-rpc**、message-api、message-rpc |
| `article-proto` | 5 | article-api/rpc、**comment-rpc、favorite-rpc、hot-api** |

⇒ **跨域依赖是真实存在的**：`message-proto` 改动会打断 article / comment / like 三个域。
⇒ **只监听本域目录会漏掉这层影响**，而漏项**不会报错**，只会在某次改动后悄悄失去保护。

---

## 三、公共模块的处理方式

| 类别 | 处理 | 理由 |
|---|---|---|
| **共享层**（`service/common`、`users-common`） | 独立成 `rag-shared-common`；**并且**出现在**每个**依赖它的域的触发面里 | 它是多域依赖 ⇒ 一处改动要同时触发所有受影响域 |
| **跨域 proto**（`message-proto`、`article-proto`、`user-proto`、`admin-proto`） | 出现在**消费方**域的触发面里（不是生产者域） | 契约是"被消费"的 ⇒ 改生产者打断消费者 |
| **域内 proto / common** | 只在本域触发面里 | 只被本域引用 |
| **制品传递** | **不做** | 本仓**没有** `distributionManagement` ⇒ 没有制品仓库 ⇒ 跨流水线无法靠 Maven 仓库传产物 |
| ⇒ 共享代码怎么进下游 | 每个流水线用 `mvn -pl <本域> -am` **从源码重编** | 唯一的可靠路径 |

---

## 四、变更触发规则

Concourse 的 `git` 资源**原生支持 `paths` 过滤**（官方 monorepo 指南）⇒
「只触发该触发的」是框架能力，不是自研机制。

每条流水线的 `paths` = ① 本域 + ② 本域**编译期消费的**外部模块 + ③ 本域用到的共享层
+ ④ 祖先聚合 pom + ⑤ 工具链（`pom.xml` / `service/pom.xml` / `mvnw` / `.mvn/**` / **`ci/**`**）。

| 改动落点 | 触发的流水线 |
|---|---|
| `service/article/**` | `rag-article` |
| `service/message/message-proto/**` | `rag-message` + `rag-article` + `rag-comment` + `rag-like` |
| `service/article/article-proto/**` | `rag-article` + `rag-comment` + `rag-favorite` + `rag-hot` |
| `service/users/user/user-proto/**` | `rag-users-user` + `rag-users-admin` + `rag-article` + `rag-favorite` + `rag-follow` + `rag-message` |
| `service/users/admin/admin-proto/**` | `rag-users-admin` + `rag-message` |
| `service/common/**` | **全部 11 条** |
| `service/users/users-common/**` | 除 `rag-task`、`rag-hot` 外的 **9 条**（这俩不用它） |
| `pom.xml` / `service/pom.xml` / `mvnw` / `.mvn/**` | **全部 11 条**（工具链变更 = 全量重跑） |
| **`ci/**`** | **全部 11 条**（CI 自身脚本变更 = 全量重跑） |

⚠️ **`ci/**` 必须在触发面里，而且它不只是"顺带加上"**（实测 2026-10-30 命中）：
git 资源的 `paths` 决定"**取哪个 commit 的树**"。若 `ci/**` 不在 paths 里，
检出版本会停在"最后一个改了业务代码的 commit" —— 那个 commit 里**还没有 ci/ 目录**
⇒ task 里的 `repo/ci/scripts/dind-run.sh` 不存在 ⇒ 任务直接失败。
换句话说：**`ci/**` 不在触发面 ⇒ CI 连自己的脚本都取不到**。

⚠️ **触发面不是手写的**：`ci/generate-pipelines.py` 从 pom 依赖图反推，
`--check` 会校验「生成物 vs 依赖图」是否一致 ⇒ 不会出现"改了 pom 忘了改 paths"。

---

## 五、每条流水线的执行流程

```
get repo (trigger: true)   ← git 资源，paths 过滤 ⇒ 只在相关改动时产生新版本
  │
  ├─ step 1  maven-build     privileged   mvn -pl <本域模块> -am -DskipTests package
  ├─ step 2  maven-test      privileged   mvn -pl <本域模块> -am test
  ├─ step 3  static-gates                9 个静态守门 + 产物级 jar 可执行性检查
  └─ step 4  report                      汇总 surefire 报告（读 TEST-*.xml）
```

**各阶段具体在做什么**

| 阶段 | 做什么 | 关键设计 |
|---|---|---|
| 源码获取 | 从 `https://github.com/KASW-UI/RAG.git` 的 `do-my-own-gh` 分支取代码 | 公开 HTTPS ⇒ **YAML 里零密钥**；`paths` 过滤 ⇒ 版本明确（每个资源版本 = 一个 commit） |
| 变更判断 | 由 `git` 资源的 `paths` 在**资源层**完成 ⇒ 不触发的域连 build 都不排 | 不是"进 job 再判断"，是"根本不产生版本" |
| 环境准备 | DinD：任务容器（privileged）里起内层 `dockerd` ⇒ 再在内层 daemon 上跑嵌套构建容器 | 本仓 128 个测试文件直接起 Testcontainers 容器，任务容器里必须有 Docker；而 Concourse 8 的 task 配置**没有**挂载声明项 ⇒ wormhole 走不通 ⇒ 只能用 DinD |
| 构建 | `mvn -pl <本域模块> -am -DskipTests package` | `-am` **必须带**：跨模块依赖是 reactor 内 SNAPSHOT，不带 `-am` 会拿到 `~/.m2` 里的**陈旧** jar（本仓 `*SharedLayerArtifactFreshnessGateTest` 就在守这条） |
| 测试 | `mvn -pl <本域模块> -am test` | 同上；`-am` 会连带跑依赖模块的测试 —— 已知代价，理由见 `ci/scripts/test.sh` |
| 静态守门 | 8 个复用本仓已有资产（`tools/difftest/`）+ 1 个 CI 自带（`ci/scripts/check-pom-parent-relativepath.py`）+ `check-jar-executable.sh`（按域传模块目录） | 前 8 个不新造检查；第 9 个是 CI 实战抓出缺陷后补的（见 `ci/DELIVERY.md` §六.2） |
| 结果输出 | 逐模块用例数 / 失败数 / 错误数 / 跳过数 + 失败用例清单 + 本次 commit | 一律读 surefire 的 `TEST-*.xml`（权威），**不读** `*.txt` 摘要（实测二者会不一致） |

---

## 六、文件清单

| 文件 | 职责 |
|---|---|
| `ci/pipelines/rag-*.yml`（11 个） | 各域流水线定义。**由生成器产出**，不要手改 |
| `ci/tasks/maven-build.yml` | 构建阶段的 task 配置（**唯一真源**） |
| `ci/tasks/maven-test.yml` | 测试阶段的 task 配置 |
| `ci/tasks/static-gates.yml` | 静态守门阶段的 task 配置 |
| `ci/tasks/report.yml` | 结果汇总阶段的 task 配置 |
| `ci/scripts/build.sh` | 在嵌套构建容器里跑 `mvn package -DskipTests` |
| `ci/scripts/test.sh` | 在嵌套构建容器里跑 `mvn test`（先 source Testcontainers 环境） |
| `ci/scripts/testcontainers-env.sh` | Testcontainers 环境：Docker 前置断言 + host override + Ryuk 关闭 |
| `ci/scripts/dind-run.sh` | DinD 外壳：起内层 `dockerd` → 在其上跑嵌套构建容器 |
| `ci/scripts/run-static-gates.sh` | 按序跑静态守门，汇总通过/失败 |
| `ci/scripts/check-pom-parent-relativepath.py` | **第 9 道门**：每个 pom 的 `<parent>` 能否被本地 `relativePath` 正确解析（离线；见 `ci/DELIVERY.md` §六.2） |
| `ci/scripts/collect-surefire.sh` | 读 `TEST-*.xml` 汇总用例数与失败清单 |
| `ci/generate-pipelines.py` | 从 pom 依赖图生成 11 条 pipeline；`--check` 校验漂移 |
| `ci/deploy.sh` | 校验 + `set-pipeline`（注入 `registry` 变量）+ `unpause-pipeline`（幂等） |
| `ci/local-up.sh` | **本地环境一键起流**：load 人工下载的镜像 tar → （可选）起本地 registry → 起 Concourse → `fly login` → 注册 11 条流水线 → （可选）触发 build |

> **为什么 task 配置要内联进 pipeline**
> Concourse 的 pipeline step **必须**带 `file:` 或 `config:` 二选一，
> 不能引用独立的任务文件（实测 `fly validate-pipeline` 报
> `must specify either 'file:' or 'config:'`）。
> ⇒ `ci/tasks/*.yml` 是**唯一真源**，由生成器**内联**进 11 条 pipeline ⇒ 不会两份漂移。
> ⇒ 这些文件同时也是 `fly execute -c ci/tasks/maven-build.yml` 一次性调试用的。

---

## 七、后续维护

### 新增一个微服务（域）时

1. 在 `service/<新域>/` 下建 `<域>-proto` / `<域>-common` / `<域>-rpc` / `<域>-api` 四个模块，
   并在 `service/pom.xml` 的 `<modules>` 里登记。
2. 在 `ci/generate-pipelines.py` 的 `DOMAINS` 里加一行：
   ```python
   ("rag-<新域>", "service/<新域>",
    ["service/<新域>/<新域>-api", "service/<新域>/<新域>-rpc"], "<新域> 域（<新域>-api + <新域>-rpc）"),
   ```
3. 跑 `python3 ci/generate-pipelines.py` ⇒ 生成 pipeline（paths 由 pom 反推，**不用手写**）。
4. `python3 ci/generate-pipelines.py --check` ⇒ 确认一致；`bash ci/deploy.sh` ⇒ 注册。

### 修改公共模块时，哪些 CI 会重跑

看 §四的表。**规则**：改了 `service/common/**` ⇒ 全部 11 条；
改了 `service/users/users-common/**` ⇒ 除 task / hot 外的 9 条；
改了某个 `<域>-proto` ⇒ 该域 + 所有**编译期消费**它的域。

### 修改某个微服务时，哪些 CI 会触发

只有**该域**那条（除非它同时改动了共享层或被消费的 proto）。

### 手动触发某条 Pipeline

```bash
fly -t concourse trigger-job -j rag-article/build-and-test --watch
```

### 查看某次 Build 的日志

```bash
fly -t concourse builds -j rag-article/build-and-test          # 列出历史
fly -t concourse watch -j rag-article/build-and-test -b 3        # 看第 3 次
fly -t concourse intercept -j rag-article/build-and-test -s maven-test   # 进容器
```

### 改了 pom 的模块依赖后

```bash
python3 ci/generate-pipelines.py          # 重生成
python3 ci/generate-pipelines.py --check  # 校验（CI 也跑这一条）
```

---

## 八、验证状态（务必区分层级 · 2026-10-30 实测）

| 层级 | 状态 | 依据 |
|---|---|---|
| YAML 语法 | ✅ | 15 个文件全部 `yaml.safe_load` 通过 |
| **Concourse 配置检查** | ✅ | `fly validate-pipeline -c ci/pipelines/*.yml` → **11/11 "looks good"**（客户端侧校验，不需要服务端） |
| 生成物一致性 | ✅ | `python3 ci/generate-pipelines.py --check` → EXIT=0 |
| 构建命令正确性 | ✅ | 宿主机实测：`mvn -o -pl service/article/article-api,service/article/article-rpc -am -DskipTests package` → BUILD SUCCESS（14 模块 / 8.6s）；`mvn -o -pl service/common -am clean test` → 405/405 全绿（1:12） |
| **静态守门在真实 task 镜像内** | ✅ | `docker run python:3.12-bookworm sh ci/scripts/run-static-gates.sh` → **8/8 全绿**；加入第 9 道门后为 **9/9**，且已在**真 CI 容器**内实测通过 |
| **Concourse 服务端落地** | ✅ | 本地 **8.3.2** 起流成功（`/api/v1/info` → `{"version":"8.3.2","worker_version":"3.0"}`）；worker `22f904bc9cc6 running 3.0` 已注册 |
| **11 条流水线注册** | ✅ | `ci/deploy.sh` 跑完；`fly pipelines` 列出 11 条 `rag-*`（配置已按最新生成物重新部署）|
| **canonical 流水线当前是 paused** | 📌 | 本 PR **合并前**它们必须 pause：`ci/` 还没进 `do-my-own-gh` ⇒ 别的 lane 一推 commit 就会触发 build 并在第一步就红（`repo/ci/scripts/dind-run.sh: No such file`）⇒ 是噪声不是信号。**合并后**一条命令恢复：`for p in $(fly -t concourse pipelines -a \| awk 'NR>1 && $2=="no" {print $1}'); do fly -t concourse unpause-pipeline -p $p; done` |
| **worker 从本地 registry 拉镜像** | ✅ | 探针流水线 **21s 成功**，层下载进度可见 ⇒ **containerd 走 HTTP 拉本地 registry 可行**（Concourse 8 的 containerd 2.4.2 不接受根级 `[registry]` 段，但 Concourse 自身的拉取路径能处理 HTTP registry） |
| **task 容器出网** | ✅ | 探针：DNS(8.8.8.8 / 223.5.5.5) OK、`github.com:443` OK、`172.17.0.1:5010`（本地 registry）OK；`10.80.0.1:5010` FAIL（预期，网关无此服务） |
| **DinD 外壳（真实 privileged）** | ✅ | 内层 `dockerd` 起来 → 从本地 registry 拉到 `maven:3.9.11-eclipse-temurin-21` → 进入 Maven 构建 |
| **Testcontainers 在 DinD 里真跑** | ✅ | 真 CI 日志：`Container postgres:16-alpine is starting` → `started in PT5.21S` → `JDBC URL: jdbc:postgresql://10.250.0.1:32769/mp_probe` ⇒ 内层 daemon 起 PG + 嵌套容器按**内层 bridge 网关**连上 |
| **完整 4 step build 跑绿** | ✅ | **共享层流水线演练 build #2：`succeeded`（3m40s）** ⇒ build ✅ / test ✅ / 静态守门 **8 过 0 挂** ✅ / 汇总 **484 条用例全通过**（service/common 445 + users-common 39；0 失败 0 错误 1 跳过）⇒ 详见 `ci/DELIVERY.md` §四.1（含 8 次演练逐次的卡点与根因） |

> 「配置校验通过」**不等于**「CI 跑通了」—— 但两者现在**都有实测绿灯**：
> 前者是 11/11 `fly validate-pipeline` + `--check` EXIT=0；后者是**真 build 跑完 4 个 step 全绿**。
> ⚠️ **绿灯的范围要说清**：绿的是**共享层流水线**（`service/common` + `service/users/users-common`）；
> article 域的 test 阶段**如实报红**（`article-rpc` 188 条里 6 failures + 67 errors）⇒ 根因是仓内
> **Linux-only 的端口探测判据**（macOS 开发机看不见）⇒ 属各域 lane 的测试代码，本 PR **未擅自修改**，
> 详见 `ci/DELIVERY.md` §六.3。

### 8.1 CI 在演练中抓出的真实缺陷（均已修在 `ci/` 内）

| # | 缺陷 | 症状 | 根因 | 修法 |
|---|---|---|---|---|
| 1 | **`ci/**` 不在触发面** | task 报 `repo/ci/scripts/dind-run.sh: No such file` | git 资源的 `paths` 决定"取哪个 commit 的树"；不在 paths 里 ⇒ 取到的 commit 里还没有 `ci/` 目录 | 触发面加入 `ci/**`（生成器 `ROOT_GLOBS`） |
| 2 | **DinD 内层网段与外层同段** | 内层拉镜像 `dial tcp 172.17.0.1:5010: connection refused` | 内层 dockerd 默认 `172.17.0.0/16` 与外层 docker0 同段 ⇒ 抢走路由 | 显式 `--bip 10.250.0.1/16 --default-address-pool base=10.250.0.0/16,size=24` |
| 3 | **嵌套容器里脚本路径缺 `repo/` 前缀** | `cannot open ci/scripts/build.sh` | `build.sh` / `test.sh` 内部是 `cd repo`，即站在 `repo` 的父目录执行 | 改为 `sh repo/ci/scripts/<stage>.sh` |

| 4 | **探针用了嵌套镜像里没有的 docker CLI** | test 阶段报「Docker daemon 不可达」，但 daemon 明明是好的 | 探针用 `docker info`，而嵌套构建容器是 `maven:3.9.11-eclipse-temurin-21`（Debian）⇒ **镜像里没有 docker 客户端**（`command -v docker` → MISSING）⇒ 探针必然失败 ⇒ 报了个误导性的错 | 探针改成 `curl --unix-socket <sock> http://localhost/_ping`（**与 Testcontainers 走的同一条路**）；网关自发现改读 `/proc/net/route`；错误信息区分三种情形 |

⚠️ 另有两个**仓内**缺陷，**不属 `ci/` 范围**，处理方式见 `ci/DELIVERY.md` §六：

| 仓内缺陷 | 影响面 | 本 PR 的处置 |
|---|---|---|
| `service/users/admin/admin-api/pom.xml` 的父 POM `relativePath` 指向坐标不符的那份 pom | **所有域**的构建（根聚合 pom 被完整解析 ⇒ 一处坏、全域红） | ✅ **已修**（1 行，坐标与继承内容不变 ⇒ 语义等价）⇒ 不修则 CI 全红 |
| `RealGrpcPort.isFree()` 的「同时持有通配 + 回环两个 socket」**只在 macOS/BSD 成立** | **21 个文件 / 9 个域**的「真进程占固定端口」类守门测试，在 **Linux CI 上必然红**（实测 Linux 0/80 可用、macOS 80/80） | 🟡 **未改**（属各域 lane 的**测试代码**写权）⇒ 建议修法见 `ci/DELIVERY.md` §六.3 |

---

## 九、环境前置（本机网络约束，实测 2026-10-30）

| 约束 | 实测 | 影响 |
|---|---|---|
| 直连 Docker Hub | `auth.docker.io` / `registry-1.docker.io` **15s 超时无响应** | ⇒ 所有镜像名必须显式带镜像源前缀 |
| 可用镜像源 | `docker.m.daocloud.io`（代理 `library/*`）✅；`docker.1ms.run`（代理全量，但 blob CDN `cloudfront-docker-cf.mrs.1ms.run` 会 EOF）⚠️ | ⇒ task 镜像用 daocloud 前缀；`concourse/*` 命名空间 daocloud 不代理（403）⇒ worker 镜像需另行下载 |
| **资源类型不依赖 Docker Hub** | Concourse 8 的资源类型是 **`rootfs.tgz` 打包**（wolfi-base + git 二进制），不从 registry 拉 | ⇒ 消除了 `alpine/git` 这条 Docker Hub 依赖 |
| **GitHub 克隆极慢** | `git clone --bare --filter=blob:none`（3.2 MB 元数据）**2m19s，均速 19 KiB/s** | ⇒ git 资源的 check **时好时坏**；本地验证改用本地 git 服务（同一份 clone **13.7s**） |
| **Maven Central 慢** | 115 KB/s | ⇒ `maven_mirror` 变量；本地静态镜像实测 **77 MB/s** |
| Testcontainers 镜像 | 测试代码用**裸名**（`postgres:16-alpine` / `redis:7-alpine` / `apache/kafka:3.7.2`）⇒ 内层 dockerd 必须配 `registry-mirrors` | ⇒ `ci/scripts/dind-run.sh` 就地生成 `/etc/docker/daemon.json`；⚠️ registry 里要按**真实仓库路径**推：`library/postgres` ✅ 但 `apache/kafka` **不是** `library/apache-kafka` |
| **DinD 内层网段** | 内层 dockerd 默认 `172.17.0.0/16` 与外层 docker0 **同段** ⇒ 抢走路由 | ⇒ 显式 `--bip 10.250.0.1/16 --default-address-pool base=10.250.0.0/16,size=24`（`DIND_BIP` / `DIND_POOL` 可覆盖） |
| macOS socket 路径 | 实际在 `~/.docker/run/docker.sock`（`/var/run/docker.sock` 不存在） | ⇒ DinD 形态不依赖它（内层 daemon 自己起） |
| 端口 5000 | 被 macOS ControlCenter 占用 | ⇒ 本地 registry 用 **5010** |
| **宿主磁盘** | `/System/Volumes/Data` 一度 **100% 满（剩 617 MiB）** ⇒ Docker containerd 内容库块级 I/O error（`blob … expected at …: input/output error`） | ⇒ **所有"daemon 卡死"的真根因**；跑 CI 前先确认磁盘有余量（腾空间 → 重启 Docker Desktop → 内容库若仍 EIO 需进一步修复） |

**需要的镜像**

| 镜像 | 用途 | 谁来拉 |
|---|---|---|
| `concourse/concourse:8.3.2` | worker | ⚠️ 需人工下载（当前镜像源拉不动） |
| `library/docker:27-dind` | 任务容器（DinD 外壳） | ✅ 已就绪 |
| `library/maven:3.9.11-eclipse-temurin-21` | 嵌套构建容器（**必须 glibc 版**：protoc 原生二进制是 glibc 链接的，alpine 的 musl 跑不了） | ✅ 已就绪 |
| `library/python:3.12-bookworm` | 静态守门 / 报告 task | ✅ 已就绪 |
| `library/postgres:16-alpine` · `library/redis:7-alpine` · `apache/kafka:3.7.2` | Testcontainers 用的容器镜像 | ✅ 已就绪（注意 kafka 的路径不是 `library/`） |

**切换到本地 registry（当公网镜像源持续不可用时）**

镜像地址写在 `ci/tasks/*.yml` 的 `image_resource.source.repository` 里，形如
`((registry))/library/docker` ⇒ **镜像源是一个 Concourse pipeline 变量**，
仓库里不写死任何机器相关的地址：

| `registry` 变量的值 | 含义 |
|---|---|
| `docker.m.daocloud.io` | 公网镜像源（默认） |
| `<docker0 网关>:5010` | 本地 registry（**带端口** ⇒ 内层 DinD daemon 按 HTTP + insecure 处理，并把它当首选 mirror） |

```bash
# 一键完成（load tar → 起 registry 并推镜像 → 起 Concourse → 注册流水线）
bash ci/local-up.sh --registry

# 或手动：
docker run -d --name ci-registry -p 5010:5000 --restart unless-stopped registry:2
docker load -i ~/Downloads/maven-3.9.11.tar
docker tag maven:3.9.11-eclipse-temurin-21 127.0.0.1:5010/library/maven:3.9.11-eclipse-temurin-21
docker push 127.0.0.1:5010/library/maven:3.9.11-eclipse-temurin-21
CONCOURSE_REGISTRY=172.17.0.1:5010 bash ci/deploy.sh
```

⚠️ **两处都要用同一个地址**：
- **worker's containerd** 拉 task 镜像（docker:27-dind / python）⇒ 要能被 worker 容器解析到；
- **内层 DinD daemon** 拉嵌套构建镜像与 Testcontainers 镜像（postgres / redis / kafka）⇒ 要能被任务容器解析到。
⇒ 宿主 docker bridge 的网关地址（`docker network inspect bridge` 取到的那个，本机是 `172.17.0.1`）对两者都可达 ⇒ 优先用它。
⇒ 本地 registry 模式下，**裸名镜像**（`postgres:16-alpine` 等）靠 registry 的 mirror 机制
落到 `/v2/library/postgres/...` ⇒ 所以上面要推 `library/postgres:16-alpine` 这类路径，
`ci/local-up.sh` 已经代劳；⚠️ **非官方镜像（如 `apache/kafka`）按原路径查** ⇒ 别推成 `library/apache-kafka`。

**Maven 镜像源（`maven_mirror` 变量）**

留空 = 走默认 Central；填内网 Nexus / 本地静态镜像则**优先查它，404 再回退 Central**
（用「额外仓库」而不是 `<mirror>` ⇒ 不把 Central 顶掉后无退路）。
生效方式：`ci/scripts/dind-run.sh` 把 `settings.xml` 写进 `$PWD/.m2`
⇒ 嵌套容器的 `/root/.m2` 正是从 `$PWD/.m2` bind 进去的 ⇒ Maven 自动生效
（不改 mvn 命令行，也不改仓内 pom）。

```bash
# 本地静态镜像（把宿主 ~/.m2/repository 当只读 HTTP 仓库伺服）
docker run -d --name ci-m2mirror -p 5013:8000 -v "$HOME/.m2/repository:/repo:ro" \
  -w /repo python:3.12-bookworm python3 -m http.server 8000 --bind 0.0.0.0
CONCOURSE_REGISTRY=172.17.0.1:5010 CONCOURSE_MAVEN_MIRROR=http://172.17.0.1:5013 bash ci/deploy.sh
```

---

## 十、交付文档

完整交付说明（架构 / 文件清单 / 执行流程 / **分层验证记录** / 环境实测 / 维护说明）
见 [`ci/DELIVERY.md`](DELIVERY.md)。

---

## 十一、边界声明

- 只做 CI，**不做** CD / 发布 / 部署。
- **不修改**任何微服务业务逻辑、测试代码或构建系统配置来迎合 CI。
- 不引入 Jenkins / GitLab CI 等其它 CI 系统。
- YAML 里**没有任何**密码 / Token（GitHub 公开 HTTPS + 测试自带凭据）。
- 不虚构测试结果：所有"通过/全绿"的断言都标了实测日期与命令。
