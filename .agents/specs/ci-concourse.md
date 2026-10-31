# spec: RAG 多流水线 Concourse CI 落地

- 分支: `align/ci-concourse`
- worktree: `/Users/pr/Documents/java&golang/RAG.worktrees/ci-concourse`
- 基准分支: `do-my-own-gh`
- 范围: **只做 CI**（构建 / 测试 / 静态守门 / 结果汇总）；**不做** CD、发布、部署
- 环境基线: 本地 Concourse **8.3.2**（worker runtime = containerd 3.0）、2026-10-30
- 交付文档: [`ci/DELIVERY.md`](../../../ci/DELIVERY.md)

---

## 0. 目标与判定

| 项 | 内容 |
|---|---|
| 目标 | 给 RAG（Spring Boot 多模块仓，10 域 / 38 组件 / 20 个可部署进程）落地**多条独立** CI 流水线 |
| 成功判据 | ① 11 条 pipeline 能被 Concourse 接受并真实跑完一个域的 4 个 step；② 触发面与 pom 依赖图**机器可校验**一致；③ 仓库内**零密钥**；④ 不修改任何业务逻辑/测试代码来迎合 CI |
| 不做 | CD / 制品发布 / K8s 部署；不引入 Jenkins / GitLab CI；不写死任何机器相关的地址 |

## 1. 流水线划分（11 条）

| # | Pipeline | 覆盖模块 |
|---|---|---|
| 1 | `rag-shared-common` | `service/common`、`service/users/users-common` |
| 2 | `rag-users-user` | `service/users/user/*` |
| 3 | `rag-users-admin` | `service/users/admin/*` |
| 4~11 | `rag-article` / `rag-comment` / `rag-favorite` / `rag-follow` / `rag-hot` / `rag-like` / `rag-message` / `rag-task` | 对应域 |

**划分依据**（可复算）：域 = Go 真源的域边界；同域 api/rpc 不拆（共享同域 proto/common，且无制品仓库可传产物）；
users 拆 user/admin（独立部署）；共享层独立成一条（`service/common` 被 20 个模块依赖、`users-common` 被 10 个依赖）。

## 2. 关键设计决策（各附理由）

| 决策 | 理由 |
|---|---|
| **触发面由生成器从 pom 依赖图反推** | 手抄 11 份依赖矩阵 = 11 个各自漂移的副本；漏项**不会报错** ⇒ `ci/generate-pipelines.py --check` 做机器校验 |
| **`ci/**` 进触发面** | git 资源的 `paths` 决定"取哪个 commit 的树"；不在 paths 里 ⇒ 取到的 commit 里**没有 ci/ 目录** ⇒ task 找不到自己的脚本（实测命中） |
| **构建/测试恒带 `-am`** | 跨模块依赖是 reactor 内 SNAPSHOT；不带 `-am` 会拿到 `~/.m2` 里的**陈旧** jar（本仓 `*SharedLayerArtifactFreshnessGateTest` 在守这条） |
| **DinD（task step 开 `privileged`）** | Concourse 8 的 task 配置**没有**挂载声明项（实测二进制里 `container_spec` 零命中）⇒ "挂宿主 docker.sock" 走不通；且本仓 128 个测试文件直接起 Testcontainers 容器 ⇒ 任务容器里必须有 Docker |
| **内层 dockerd 显式 `--bip`** | 内层默认 bridge `172.17.0.0/16` 与外层 docker0 **同段** ⇒ 抢走路由 ⇒ 内层拉镜像 `connection refused`（实测命中） |
| **task 配置内联进 pipeline** | Concourse step 必须 `file:` 或 `config:` 二选一 ⇒ `ci/tasks/*.yml` 是唯一真源，由生成器内联 ⇒ 不会两份漂移 |
| **外部依赖变量化** | `registry`（镜像源）+ `maven_mirror`（Maven 镜像源）两个 pipeline var ⇒ 仓库内零机器相关地址、零密钥 |
| **共享层不做跨流水线制品传递** | 本仓**没有** `distributionManagement` ⇒ 没有制品仓库 ⇒ 每条流水线从源码重编 |

## 3. 文件清单（27 个）

- `ci/pipelines/rag-*.yml`（11）—— **生成物**
- `ci/tasks/{maven-build,maven-test,static-gates,report}.yml`（4）—— 唯一真源
- `ci/scripts/{build,test,testcontainers-env,dind-run,run-static-gates,collect-surefire}.sh`（6）
- `ci/generate-pipelines.py`、`ci/deploy.sh`、`ci/local-up.sh`
- `ci/README.md`（使用/依赖矩阵/触发规则/环境约束）、`ci/DELIVERY.md`（交付文档）

## 4. 验证记录（分层 · 2026-10-30 实测）

| 层级 | 结果 |
|---|---|
| YAML 语法 | ✅ 15 文件 `yaml.safe_load` 通过 |
| `fly validate-pipeline` | ✅ **11/11 "looks good"** |
| `generate-pipelines.py --check` | ✅ EXIT=0 |
| Concourse 服务端落地 | ✅ 8.3.2 起流、worker 注册、**11 条 set-pipeline + unpause 成功** |
| worker 从本地 registry 拉镜像 | ✅ 探针流水线 21s 成功（走 HTTP） |
| task 容器出网 | ✅ DNS / github:443 / 本地 registry 均通 |
| 静态守门在真实 task 镜像内 | ✅ **8/8 全绿** |
| DinD 外壳（真实 privileged） | ✅ 内层 dockerd 起来 + 从本地 registry 拉到 maven 镜像 |
| 完整 4 step 端到端 | 见 `ci/DELIVERY.md` §四.1（含 4 次演练逐次卡点与根因） |

## 5. CI 抓出并已修在 `ci/` 内的 3 个真缺陷

| # | 症状 | 根因 | 修法 |
|---|---|---|---|
| 1 | `repo/ci/scripts/dind-run.sh: No such file` | `ci/**` 不在触发面 ⇒ 取到的 commit 里没有 `ci/` | 触发面加入 `ci/**` |
| 2 | 内层拉镜像 `dial tcp 172.17.0.1:5010: connection refused` | 内层 dockerd 默认网段与外层 docker0 同段 ⇒ 抢路由 | 显式 `--bip` / `--default-address-pool` |
| 3 | `cannot open ci/scripts/build.sh` | 阶段脚本站在 `repo` 的**父目录**执行 | 路径改 `repo/ci/scripts/<stage>.sh` |

## 6. CI 抓出的**仓内**缺陷（本 PR 一并修，1 行）

`service/users/admin/admin-api/pom.xml`：`<parent>` 声明的是 `com.rag:service`，
但 Maven 默认 `relativePath`（`../pom.xml`）指向 `service/users/admin/pom.xml`（坐标是 `admin`）
⇒ 判 "wrong local POM" ⇒ 回落仓库找 `com.rag:service:pom` ⇒ 本仓无制品仓库
⇒ **干净 checkout 构建必炸**（开发机被 `~/.m2` 里的旧安装产物掩盖）。

修法：加一行 `<relativePath>../../pom.xml</relativePath>` ⇒ **坐标与继承内容不变，语义等价**。
全仓 52 个 pom 扫描：**仅此 1 处**（根 pom 那条命中是外部 `spring-boot-starter-parent`，属误报）。

## 7. 边界

- 不修改任何微服务业务逻辑、测试代码或构建系统配置来迎合 CI（§6 是**修缺陷**，不是迎合）。
- YAML 内无任何密码 / Token（GitHub 公开 HTTPS）。
- 不虚构测试结果：所有"通过/全绿"断言都带实测日期与命令；未取得的一律标"未验证"。
