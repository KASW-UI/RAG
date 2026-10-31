# 现有 CI 与 Go 原始项目目标一致性审查报告

> **审查对象**：`ci/`（86 个文件 · **审查时点** · 不含本报告自身 · 13 条流水线 · 6 个 Task · 13 道静态守门 · 3 族契约基准 · 1 份目标要求清单）
> **目标来源**：Go 原始项目 `Sea-RideTheWind_golang`（**只读 · 冻结 · 真理之源**）
> **审查日期**：2026-10-31 · **审查性质**：只读审查（本轮未修改任何 Pipeline / Task / 脚本 / 测试 / 业务代码）
> **审查基线**：`align/ci-concourse` @ `2c524a34`
>
> ⚠️ **读数时效**：本报告成稿后主干继续前移（`2c524a34` → `cb0f10d8`）⇒ 报告内的所有读数以**成稿时的树**为准；成稿后本 lane 又落的口径订正见 [`ci/DELIVERY.md`](DELIVERY.md) §4.5 **B16 / B17**（`U+FFFD` 身份口径 + 流水线 pin 的远端指针）。

---

## 〇、审查方法与自限

### 0.1 审查口径（与建设方向一致）

| 角色 | 定位 | 本报告怎么用 |
|---|---|---|
| **Go 原始项目** | **WHAT 来源**（参考基准，只读） | 一切"应该是什么"的判据都来自 Go 源码 |
| **Java 项目** | **唯一 HOW 载体 + 唯一 CI 执行对象** | 只判定"Java 是否实现了 WHAT" |
| **CI** | **裁判** | 判定"CI 是否能可靠地检出 Java 未达 WHAT" |

### 0.2 三条不可让步的判据

1. ⛔ **禁止以 Java 现状反向定义目标** —— Java 现状 / 模块结构 / 测试数量 / Proto / 错误码 / Schema 都不得直接当正确标准。
2. ⛔ **PASS 不是预期结果** —— 必须有 Java 侧实现 + 有效验证证据；未实现 / 未覆盖 / 脚本没真执行 ⇒ 不得判 PASS。**允许最终出现大量 FAIL / NOT_VERIFIED / BLOCKED**。
3. ⛔ **不得为让 CI 变绿而降低 Go 要求、缩小范围、删目标要求或加豁免**。

### 0.3 证据强度分级

| 级 | 含义 |
|---|---|
| **L4 活体实测** | 本轮在真实环境跑过命令并读到输出 |
| **L3 源码级** | 逐行读过两侧源码得出结论，未在活体环境跑 |
| **L2 推导** | 基于结构 / 命名 / 规则的推断，**需后续验证** |

### 0.4 本轮实跑记录（L4）

```bash
# 全部在 ci-concourse 工作树（HEAD=2c524a34）执行
python3 ci/scripts/check-requirements.py                       # EXIT=0 · PASS=23 · FAIL=0 · 缺口=18
bash    ci/scripts/check-errmsg-contract.sh                     # EXIT=0 · PASS 9 / PENDING 0
python3 ci/scripts/check-requirements-mutations.py              # 10/10
python3 ci/scripts/check-errmsg-contract-mutations.py          # 11/11
python3 ci/scripts/check-schema-contract.py --all              # PASS 8 / NOT_RUN 1
bash    ci/verify-triggers.sh --static                         # 7/7
python3 ci/generate-pipelines.py --check                       # EXIT=0
python3 ci/scripts/extract-errmsg-authority.py --check         # EXIT=0 · 10/10 域与 Go 真源逐字节一致
python3 tools/difftest/check-surface-parity.py                 # EXIT=0 · HTTP 72/72 · gRPC 71/71
docker run --rm -v "$PWD:/build/repo" -w /build python:3.12-bookworm \
  sh repo/ci/scripts/run-static-gates.sh                       # EXIT=0 · 13 道全绿
```

> ⚠️ `run-static-gates.sh` 必须在**纯 clone** 下跑（工作树会带 `.DS_Store` / `__pycache__`，影响门内扫描面）。
> ⚠️ `check-surface-parity.py` / `extract-errmsg-authority.py --check` **需要 Go 真源仓** ⇒ **本地核验用**，**不得进 CI**（见 §六·P0-2）。

---

## 一、总体结论

### 🔶 **部分符合**

> **CI 的"方向"是对的，"覆盖面"不够。**
> 具体地说：
> - ✅ **方向符合**：目标要求清单的每一条都带 `go_basis`（Go 侧路径），守门规则 R3 把"没有 Go 依据的条目不允许存在"落成了机器判据；R4/R5/R6 三个契约族都做到了"**Go 派生基准 + Java 侧消费点实测**"，且 Go 权威段经独立复算确认与 Go 真源逐字节一致。
> - ❌ **覆盖不足**：Go 定义的行为级要求（状态机 / 幂等 / 错误码传播 / schema 约束 / presence 语义 / 调用图 / 对外契约面）**几乎全部未验证**（清单 46 条里 23 条 VERIFIED，18 条 NOT_VERIFIED）。
> - 🔴 **结构性缺口**：**唯一能验证对外契约面（HTTP 路由/方法 + gRPC 方法面）的脚本，因为需要 Go 仓而被系统性注释出所有流水线**；**基准与 Go 的对应关系在 CI 内不可验证**；**目标要求清单本身遗漏了"HTTP 对外契约面"这一整个维度**，而覆盖守门 R10 只看三个基准目录 ⇒ 这个遗漏对门禁不可见。

### 1.1 一句话定性

**这套 CI 现在是一套合格的「提交不回归门」，但还不是一套「迁移达标门」。**
它能可靠地挡住"Java 侧代码改了导致契约基线不一致"，但**挡不住"Java 侧的行为从未被验证过"**——而后者才是本次建设方向的核心命题。

---

## 二、目标要求覆盖矩阵

> **读法**：一行 = 一个目标要求维度。列 = "Go 说什么（WHAT）→ Java 做了什么（HOW）→ CI 怎么判 → 判得对不对 → 缺什么"。

### 2.1 主矩阵（9 个维度）

| 维度 | Go 侧源码证据（WHAT） | Java 侧当前实现（HOW） | CI 检查方式 | 是否**真正**验证目标语义 | 缺失 / 偏差 |
|---|---|---|---|---|---|
| **proto_contract**（10 条） | `proto/*.proto` 12 份（`article.proto:7-14` 状态枚举 · `user.proto:12` `optional int64 status = 6` · `like.proto:8-18` 动作枚举） | 10 份 `<域>-proto/src/main/proto/*.proto`（`users.proto` 为 Go `user.proto` 改名） | 第 10 道门 `check-proto-contract.py --baseline ci/contracts/proto`（`run-static-gates.sh:153`）；基准逐字节比对 + `--with-descriptor` 语义层复核 | ⚠️ **部分**：验证的是"Java `.proto` == 冻结基准"，而基准经独立复算确认确为 Go 派生（`--check` EXIT=0）⇒ **文件层可信**；但**推不出生成代码一致、推不出消费点语义** | 生成代码 `FIELD_NUMBER` 一致性 / `optional` presence 语义 / 字段号硬编码 / Jackson 映射 —— 4 条全部 NOT_VERIFIED（`REQ-PROTO-ALL-002~005`） |
| **proto_consumption**（4 条） | `proto/article.proto:40,42,69-75`（`optional` 字段清单）· `proto/user.proto:12`（presence 是身份解析前提） | 各域 `*ServiceGrpcImpl` + DTO 映射 + `*ProtoContractTest`（硬编码期望值，非本门） | ❌ **无门** | ❌ **否** | 4 条全 NOT_VERIFIED。**注意：仓内已有 `*ProtoContractTest` 是 Maven 测试、期望值硬编码，不是 CI 的 WHAT 对照** |
| **errmsg_contract**（11 条） | `service/article/common/errmsg/errmsg.go:3-19` · `service/user/common/errmsg/errmsg.go:4-36` · `service/task/rpc/internal/logic/get_task_logic.go:44`（task 域**无码表**，权威 = 逐调用点） | 8 个 `*ErrorCode` + `*ErrorMessages` 类；task 域内联（与 Go 对称） | 独立流水线 `rag-contract-errmsg`（`contract-check` job → `contract-errmsg` task）；Go 权威段**双向**比对（Go 有 Java 无 / Java 有 Go 无 / 同码异文案 三种漂移均 FAIL） | ⚠️ **部分且较强**：权威段确为 Go 派生（本地 `--check` 10/10 逐字节一致）；差异**四态**机器自证（`approved` 回指 `docs/Intentional_Deviations/` 查不到即 FAIL · `closed` 回指 `docs/DRIFT/` · `technical` 必须带 Go `文件:行号`） | `comment` / `like` 判 APPROVED_DIFFERENCE（已签 `SD-2026-10-31-02` / `-01`）⇒ **不计入 PASS**；`follow` / `message` / `task-rpc` / `task-api` 4 域仍 NOT_VERIFIED |
| **errmsg_propagation**（2 条） | `service/like/api/internal/logic/like/likeactionlogic.go:63-66`（靠 message 文本还原业务码）· `service/comment/api/internal/logic/comment/createcommentlogic.go:52-55` | `*GrpcError.bizCodeFromError`（like / comment 方向刻意相反，各有论证） | ❌ **无门** | ❌ **否** | 2 条全 NOT_VERIFIED。**且 `rag-contract-errmsg` 的触发 paths 只含 `<域>-common/.../errmsg/**`，不含任何 logic 目录 ⇒ 改错误码用法根本不触发契约门** |
| **schema_contract**（9 条） | `service/*/rpc/internal/model/*.go` 的 GORM struct + tag（`uniqueIndex` / `not null` / `default:0`） | Flyway `V*.sql` + `users-common` 的 `SchemaInitializer` 幂等重放 | 独立流水线 `rag-contract-schema`；活体 PG 重放迁移 → 逐列比对基准 `must_match`（类型 / 长度 / 可空性 / DEFAULT / 唯一约束列元组） | ⚠️ **部分**：活体对拍（不是读 SQL 文件）⇒ 证据强度高；基准由 Go GORM `AutoMigrate` **活体**提取（读源码推不出索引名/可空性/DEFAULT） | `users` 域 NOT_VERIFIED（`users_user.status` 可空性 + DEFAULT 残留）；`hot` 域 NOT_APPLICABLE（两侧皆无业务表 ⇒ **不是「已对齐」**） |
| **schema_constraint**（3 条） | GORM tag 中的必填 / 唯一 / 默认值 | 同上 | ❌ **无门** | ❌ **否** | CHECK 约束 / 触发器 全 NOT_VERIFIED；外键两侧皆无 ⇒ NOT_APPLICABLE |
| **runtime_behavior**（5 条） | `service/common/observability/http.go:21,84-86`（客户端断开 = 499）· `:30-34`（原生超时禁用）· `service/common/observability/rpc.go:38,68-71`（gRPC 超时 = DeadlineExceeded）· 10 个域的状态机（`users.status` / `article.status` / `like.state` / `comment.state` / `conversation.status`） | `service/common` 的 GoStyle* 系列（熔断 / deadline / traceparent / span / metrics）+ 各域 `*DomainService` | 仅 2 条 VERIFIED：Tomcat 表单段数上限（第 4 道门 `check-tomcat-limits.py`）+ deadline 探针判定器接线（`check-deadline-verdict.py`） | ⚠️ **2/5**；且这两条验的是"验证手段本身"与"参数上限"，**不是行为语义** | deadline/超时传播、JWT claim presence、业务状态机、异步投递语义 —— **4 条全 NOT_VERIFIED** |
| **callgraph**（1 条） | 11 条真实调用边（`service/*/rpc/internal/svc/service_context.go`） | Java 16 条边（Maven compile/runtime scope） | ❌ **无门** | ❌ **否** | NOT_VERIFIED。`check-surface-parity.py` 查的是**契约面**，不是**调用图** |
| **engineering_constraint**（1 条） | `service/common`（共享层，被 10 域依赖） | `tools/difftest/verdict-grpc-admin-deadline.py` | `check-deadline-verdict.py`（静态门） | ✅ **是**（验的是"判定器与接线真的有效"——**验验证手段本身**，是罕见且正确的自证型门） | — |

### 2.2 🔴 **清单里根本没有的维度**（本轮新发现 · P0 级）

| 缺失的目标维度 | Go 侧事实 | Java 侧 | CI 侧 | 后果 |
|---|---|---|---|---|
| **HTTP 对外契约面**（路由 + 方法 + 端点集合） | Go 侧 `routes.go` 共 **72 个端点**（`service/*/api/internal/handler/routes.go`），带 JWT / 公开 / 上传三段分组 | Spring `@RestController` + `@RequestMapping` | ❌ 无门；`check-surface-parity.py` 在 **11 条流水线 + 静态门 task 里全被注释掉**（`ci/tasks/static-gates.yml:27` · `ci/pipelines/rag-like.yml:218` 等） | **Go 定义的对外契约面这一整块目标，在 CI 里完全不存在** |
| **gRPC service 全名 / 方法路径** | Go `ServiceName: "pb.LikeService"` ⇒ `/pb.LikeService/*` | Java `SERVICE_NAME = "like.LikeService"` ⇒ `/like.LikeService/*` | ❌ 无门（`grep -rn "SERVICE_NAME\|ServiceName\|method path\|/pb\." ci/scripts/ tools/difftest/*.py` ⇒ **零命中**） | 跨栈互调会 UNIMPLEMENTED，CI 不会红 |

> ⚠️ **为什么守门看不见这个遗漏**：`check-requirements.py` 的 R10 覆盖守门只遍历 `proto_contract` / `errmsg_contract` / `schema_contract` 三个基准目录（`ci/scripts/check-requirements.py:298-300`）⇒ 清单里没有 HTTP 面这一维，**既不会被要求、也不会被判为覆盖缺口**。

### 2.3 清单读数（46 条 · L4 实跑）

| 状态 | 条数 | 计入 PASS | 分布 |
|---|---|---|---|
| ✅ VERIFIED | **23** | ✅ 是（**且只有它**） | proto_contract 10 · errmsg_contract 4 · schema_contract 7 · runtime_behavior 1 · engineering_constraint 1 |
| ❌ FAIL | 0 | — | — |
| ⚠️ NOT_VERIFIED | **18** | ❌ 否 | proto_consumption 4 · errmsg_contract 4 · schema_contract 1 · schema_constraint 2 · runtime_behavior 4 · callgraph 1 · errmsg_propagation 2 |
| 🏷 APPROVED_DIFFERENCE | 3 | ❌ 否 | `REQ-ERRMSG-COMMENT-001`（`SD-2026-10-31-02`）· `REQ-ERRMSG-LIKE-001`（`SD-2026-10-31-01`）· `REQ-ERRMSG-LIKE-002`（wire 载体） |
| ➖ NOT_APPLICABLE | 2 | ❌ 否 | `REQ-SCHEMA-HOT-001`（无业务表）· `REQ-SCHEMA-ALL-004`（外键两侧皆无） |

> ✅ **守门纪律成立**：`NOT_IMPLEMENTED` / `NOT_VERIFIED` / `BLOCKED` 一律不计入 PASS（`check-requirements.py:82`）；PASS 只 = VERIFIED。
> ⚠️ **但**：NOT_VERIFIED **不阻断任何流水线** ⇒ §六·P1-1。

---

## 三、R1–R9 逐项审查（`ci/BASELINE.md` §四）

> **R1–R9 = CI 需求基线**（"CI 必须守住什么"）；**R10 = 目标要求清单门**（本轮新增）。
> ⚠️ **编号撞车提醒**：`ci/requirements/README.md` §六 的 R1–R11 是**清单守门规则**，与此处 R1–R10 **不是同一套编号**。下文每处都注明出处。

### R1 干净可重复构建（项目级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | Go 侧无制品仓库、依赖走 module cache ⇒ 隐含"干净环境可构建" |
| **Java 现状（HOW）** | 52 个 pom、无 `distributionManagement` ⇒ 依赖全在 reactor 内 SNAPSHOT ⇒ 不带 `-am` 会拿到 `~/.m2` 里的陈旧 jar |
| **CI 实现** | `maven-build` task（`ci/tasks/maven-build.yml`）· `ci/scripts/build.sh` · 父 POM 拓扑由第 9 道门 `check-pom-parent-relativepath.py` 守 |
| **判定** | ✅ **符合**。第 9 道门是**仓级拓扑守门**，不是形式检查 |
| **缺口** | ⚠️ 干净环境（空 `~/.m2`）由 task 容器保证，但**无门断言 `~/.m2` 真的为空** ⇒ 依赖「镜像天然干净」这一未被验证的前提 |

### R2 单元测试可执行且结论可判定（项目级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | Go 侧**无全仓测试门**（message / follow / task / admin = 0 个测试文件）⇒ Java 不能继承这个缺口 |
| **Java 现状（HOW）** | 585 个测试文件 / 30 个模块 |
| **CI 实现** | `maven-test` task + `ci/scripts/collect-surefire.sh`（按 `TEST-*.xml` 统计，**不读 `.txt` 摘要**——实测二者会不一致）· `report` task |
| **判定** | ✅ **符合**。**2026-10-31 已补两条断言**（模块报告缺失 / 模块 0 条用例 ⇒ 原为假绿）⇒ 假绿已闭合 |
| **缺口** | ⚠️ surefire XML 存在即算"有结论"；**未断言"用例数 > 0 且全部被采信"**（Failsafe / 集成测试分模块报告是否被统计，未在门内断言） |

### R3 集成测试的活体依赖（服务级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G7 —— compose 起 18 个基础设施服务（PG / Redis / Kafka / Nacos…）⇒ 基础设施是真容器 |
| **Java 现状（HOW）** | 126 个测试文件直接 `new PostgreSQLContainer/GenericContainer` 且**不探测 Docker 可用性** |
| **CI 实现** | 任务容器 `docker:27-dind` 起内层 dockerd ⇒ 内层容器连内层 daemon（`ci/scripts/dind-run.sh`）· `ci/scripts/testcontainers-env.sh` |
| **判定** | ✅ **符合**（DinD 实测 PG 5.21s 起来，JDBC 连到内层 bridge 网关） |
| **缺口** | 🔴 **`RealGrpcPort.isFree()` 的 macOS/BSD-only 判据**（同时持通配 + 回环两个 socket ⇒ Linux 必 `EADDRINUSE`）⇒ 波及 **21 个文件 / 9 个域** ⇒ **修好前除共享层外的域流水线都会在 test 步红**。这是**域侧测试代码写权**，非 CI 缺陷，但它是"迁移验收"路上最大的实质阻塞 |

### R4 接口契约一致性（项目级 + 服务级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G3 —— proto 是跨服务唯一契约面；Go 12 份（`proto/`），产物入库 |
| **Java 现状（HOW）** | 10 份 proto **逐条复制**自 Go（只改 `option` 块 + `user.proto`→`users.proto` 改名 + 注释） |
| **CI 实现** | 第 10 道门 `check-proto-contract.py --baseline ci/contracts/proto`（`run-static-gates.sh:153`）· 基准 `ci/contracts/proto/*.proto`（10 份）· 域表 `DOMAINS`（`check-proto-contract.py:76-179`）含 `pkg` / `go_pkg` / `pkg_why` / `ruling` |
| **判定** | ⚠️ **部分符合**。**①层（`.proto` 文件）✅ 已进 CI 且 10/10 一致**；**②层（消费方式）❌ 未覆盖**（清单自陈 `REQ-PROTO-ALL-002~005` NOT_VERIFIED） |
| **关键缺口** | ① **基准的 `ruling`（签字回指）只是注释文本，CI 不解析** ⇒ 签字被撤销后 R4 仍会强制执行该偏离（对比：R5 的 deviations 段**是**机器可校验的 ⇒ 两族口径不齐）<br>② **`package` 行是契约项**（`normalize_proto(keep_package=True)`）⇒ R4 确实守住了已签的 `comment` / `like` package 归一 —— 但它守的是"Java == 基准"，**不是"Java == Go"**<br>③ **proto `package` 决定 gRPC 方法全名** ⇒ 归一后 wire 路径变了（§六·P0-3） |

### R5 错误码契约一致性（项目级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G4 —— 8 份 `errmsg.go` 是 wire 契约；**task 域无码表** ⇒ 权威 = 逐调用点（`service/task/rpc/internal/logic/get_task_logic.go:44` · `service/task/api/internal/types/types.go:35-46`） |
| **Java 现状（HOW）** | 8 个 `*ErrorCode` + `*ErrorMessages`；task 域内联（与 Go 对称） |
| **CI 实现** | 独立流水线 `rag-contract-errmsg`（`contract-check` → `contract-errmsg` task）· 检查器 `ci/scripts/check-errmsg-contract.sh` · 基准 `ci/contracts/errmsg/*.txt`（10 份，两段式）· 退出码四态 `0/1/2/3` |
| **判定** | ✅ **本轮最强的一条门**。理由：<br>① 权威段**确为 Go 派生**（本轮本地 `--check` 复算：10/10 域逐字节一致，EXIT=0）<br>② 差异**四态**机器自证（`check-errmsg-contract.sh:506-528`）<br>③ 变异套件 **11/11** 红在目标断言 |
| **缺口** | ① **`technical` 13 条差异仅凭「Go 侧形态不可达」判定，无活体反证**（如 `like` 域 `AntiBrush` 配置存在但代码零消费 ⇒ 靠 grep 而非运行证明）<br>② **`pending` 语义只作用于"域"，不作用于"条目"** ⇒ 一个域里只要有一条 pending，整个域不计入 PASS（保守，可接受）<br>③ 🔴 **触发面不含 logic 目录**（§六·P1-2）<br>④ ⚠️ **Java 侧探针是独立实现**（`tools/errmsg-probe/<域>/java/Probe.java`）⇒ 若探针本身漏读某个错误码，比对双方都少 ⇒ 不报漂移（探针与 Java 生产码的耦合度未见守门） |

### R6 数据库 Schema 一致性（服务级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G5 —— schema 真相在 Go 的 model struct（GORM `AutoMigrate`） |
| **Java 现状（HOW）** | 37 份 Flyway SQL + `users-common` 的 `SchemaInitializer` 幂等重放 |
| **CI 实现** | 独立流水线 `rag-contract-schema` · `ci/scripts/check-schema-contract.py` → `tools/difftest/check-ddl-static.py --rag-only --semantic-baseline` · 基准 `ci/contracts/schema/*.json`（9 域） |
| **CI 环境编排** | `docker:27-dind` → 内层 `python:3.12-bookworm` → `tools/difftest/docker_api.py`（纯标准库 Docker Engine API over unix socket）—— **不需要 Maven / JDK** |
| **判定** | ⚠️ **部分符合**。活体对拍（不是读 SQL 文件）⇒ 证据强度高；三态退出码（PASS / FAIL / **BLOCKED** / **NOT_RUN**）把"环境挂了"与"漂移了"分得干净 ✅ |
| **缺口** | ① 🔴 **`users` 域 NOT_VERIFIED 未被阻断**（`users_user.status` 可空性 + DEFAULT 残留 22 处）⇒ **CI 绿，但 users 域 schema 未达标**<br>② **`hot` 域 NOT_RUN**（两侧皆无业务表）⇒ 正确地**没有**写成"已对齐" ✅<br>③ 🔴 **基准无 `--check` 模式**（`extract-schema-baseline.py` 只有 `--all --out-dir`）⇒ R6 的基准↔Go 对应关系**连本地都无法机器复算**（R5 有 `--check` ⇒ 两族不对称）<br>④ `known_residues_not_enforced` 段（CHECK / 触发器 / 非唯一索引）登记了未签字差异，但**无门消费** |

### R7 产物可执行性（服务级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G1 —— 每个服务一个可运行二进制 / 镜像 |
| **Java 现状（HOW）** | 可执行 fat jar（`Main-Class: JarLauncher`） |
| **CI 实现** | 第 13 道门 `check-jar-executable.sh`（`run-static-gates.sh:184`，需 `JAR_MODULES`）· 脚本**自己注明** `service/common` 是 library 模块、刻意不在枚举里 |
| **判定** | ✅ **符合**（"未构建 ⇒ 判红"，不允许"没查"被读成"查过"） |
| **缺口** | ⚠️ 该门**依赖 `maven-build` 的 `outputs` 把 jar 传到守门步**；产物传递链一旦断，该门会以"未检查"形式通过（脚本有"已检查 N/N"计数，但**N=0 时是否判红**需复核） |

### R8 公共组件变更的影响面传播（项目级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G2 —— `service/common` 被 10 个服务 import ⇒ 改它波及全部 |
| **Java 现状（HOW）** | `service/common` 被 10 域依赖；`users/users-common` 被 8 域依赖；各 `*-proto` 被下游依赖 |
| **CI 实现** | 资源层 `paths` 过滤（不触发的域连 build 都不排）· 触发面由 `ci/generate-pipelines.py` 从 **Maven 依赖图**反推（不手写）· `--check` 机器校验漂移 · 第 12 道门 `verify-triggers.sh --static`（实现 vs 意图） |
| **判定** | ✅ **符合，且是本 CI 最扎实的一块**。静态 7/7 + 服务端实跑 7/7 精确集合一致（真实 commit ⇒ 真实 build）· **2026-10-31 已加固**：上游依赖模块的祖先聚合 pom 也纳入 |
| **缺口** | ⚠️ **触发面 ≠ 契约门触发面**：`rag-contract-errmsg` 的 paths 是**手写**的窄集合，与 `generate-pipelines.py` 的依赖图口径**不同源** ⇒ 两套触发面可能漂移（第 12 道门只校后者） |

### R9 配置与密钥面（项目级）

| 项 | 内容 |
|---|---|
| **Go 出处（WHAT）** | G6 —— 根 `.env` + `etc/*.yaml`，密钥不进版本库 |
| **Java 现状（HOW）** | `application.yml` + profile |
| **CI 实现** | 第 1/2/3 道门：`check-no-prod-secrets.py` · `check-no-nul-bytes.py` · `check-datasource-password-form.py`（**口令占位符形态在 Go `os.ExpandEnv` 下可解析 ⇒ 不得带默认值**） |
| **判定** | ✅ **符合**。**2026-10-31 已分级汇总**：豁免命中数单列，不混进 PASS |
| **缺口** | ⚠️ Go 侧 `service/user/user/rpc/etc/user.yaml:33-35` 的 `AccessSecret` 是**硬编码在版本库里的真 secret** ⇒ Java 侧"无生产密钥"的口径与 Go 侧现实不一致（Go 那边本身就是坏的）—— 属 Go 侧缺陷，不构成 Java 漂移，但**基线文档不该把 Go 的现状当成"好榜样"引用** |

### R10 目标要求清单（项目级 · 本轮新增）

| 项 | 内容 |
|---|---|
| **为什么要它** | R4/R5/R6 虽然都标称"以 Go 为基准"，但**没有任何机器机制**保证"基准真的是 Go 派生的" ⇒ 于是"先读 Java 实现 → 整理成标准 → 让 CI 检查它是否符合这份标准"这条路畅通无阻（`ci/BASELINE.md` §4.3.1） |
| **CI 实现** | 第 11 道门 `check-requirements.py`（`run-static-gates.sh:163`）· 规则 R1–R11（`check-requirements.py:196-317`）· 变异套件 `check-requirements-mutations.py` **10/10 红在目标规则** |
| **判定** | ✅ **符合，且是本方向最关键的机制**。三条纪律落成机器判据：<br>① **R3** `go_basis` 必填 ⇒ 没有 Go 依据的条目**不允许存在**<br>② **R6** `APPROVED_DIFFERENCE` 的 `deviation.id` 必须能在 `docs/Intentional_Deviations/` 查到**且分类为「已签字」** ⇒ 杜绝自行豁免<br>③ **PASS 只 = VERIFIED**（`NOT_PASS_STATUSES`）⇒ 未达成的目标不得伪装成通过 |
| **缺口** | ① 🔴 **R10 覆盖守门只遍历三个基准目录**（`:298-300`）⇒ **清单里缺一整个维度（HTTP 契约面）不会被发现**（§二·2.2）<br>② 🔴 **NOT_VERIFIED 不阻断任何流水线** ⇒ 18 条行为要求长期未验证，CI 仍全绿（§六·P1-1）<br>③ ⚠️ R8 只校验"VERIFIED 要求挂的 pipeline / task 真实存在"（`:273-282`）⇒ **不校验"该 task 真的跑了这条要求"**（契约门真的被触发过吗？靠触发面人工保证）<br>④ ⚠️ 规则 R5 只校验 `verify_command` **形态**与脚本路径存在（`:222-243`）⇒ 一个 `echo PASS` 也能过形态校验（变异 M5 覆盖的是"空壳"，非"任意脚本"） |

---

## 四、问题清单（P0 / P1 / P2）

### 🔴 P0-1 · 对外契约面（HTTP 路由/方法 + gRPC 方法面）在 CI 里**完全不存在**，且唯一能查它的脚本被系统性注释掉

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/tasks/static-gates.yml:27`（`check-surface-parity.py` 被注释）· `ci/pipelines/rag-{article,comment,favorite,follow,hot,like,message,task,users-user,users-admin}.yml` 各 `:2xx`（共 11 处同样注释）· 脚本本体 `tools/difftest/check-surface-parity.py` · 清单 `ci/requirements/requirements.json`（**无 HTTP 面维度**） |
| **当前行为** | 13 道静态门里**没有**契约面门；`check-surface-parity.py` 在所有流水线里被注释（脚本头部自陈"需要 Go 真源仓库 ⇒ 不在本组"）；清单 46 条里**没有一条**要求 HTTP 路由/方法一致 |
| **为何不符** | Go 的 `routes.go`（**72 个端点**）与 `proto` 的 rpc 集合是**对外契约面的 WHAT**；Java 侧只有 `@RestController` / `*ImplBase`，**没有机器可读的基准可比** ⇒ "Go 定 WHAT" 在这一整个维度上**落不了地**。根因是方向纠正带来的固有代价：**Go 不进 CI ⇒ 需要 Go 仓的检查天然无法进 CI**，而这一维恰好**没有走"离线冻结基准"这条路**（proto/errmsg/schema 走了） |
| **会漏掉什么** | 任意一个端点被改路径 / 改方法 / 漏实现 / 多实现，**CI 都不会红**。users / admin 两域已签字的 method 归一（Go GET/DELETE 混合 → Java 统一 POST）一旦被"顺手改回"，也不报警。⚠️ 实测：**今天 Java 侧是对的**（`check-surface-parity.py` 本地实跑 EXIT=0 · HTTP **72/72** · gRPC **71/71**）⇒ 这是**CI 覆盖缺口**，不是 Java 缺陷 |
| **应达到什么结果** | ① 把 Go 的 **72 个 HTTP 端点（method + path + 归一化前缀）+ 71 个 gRPC 方法 + service 全名集合**一次性离线提取为 `ci/contracts/surface/<域>.json`（与 proto/errmsg/schema 同一办法：离线提取 → 人工裁定 → 固化 → CI 只读基准 + Java 侧）⇒ **不引 Go 进 CI，又恢复覆盖**；② 清单新增 `http_surface` / `grpc_surface` 两个维度并登记 `go_basis`；③ R10 覆盖守门扩展到 `surface/` 目录 ⇒ **今后再缺维度会被当场抓住** |

### 🔴 P0-2 · 契约基准与 Go 真源的对应关系，在 CI 内**不可验证** ⇒ 基准可被单方面改成"Java 现状"后自证

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/scripts/extract-errmsg-authority.py:48`（`--check` 模式**存在**）· `tools/difftest/extract-schema-baseline.py:477-480`（**无 `--check`**）· `ci/contracts/README.md` §三（"审查：基准变更走 PR，review 时对照来源文件逐条核"）· `ci/scripts/check-proto-contract.py:507-532`（`go_pkg` / `pkg_why` / `ruling` **仅在提取期使用**） |
| **当前行为** | R4/R5/R6 基准全是**冻结文件**；CI 只做"Java 侧 ↔ 基准"比对；**没有任何门重算基准**。`extract-errmsg-authority.py --check` 在 CI 里**零引用**（`grep -rn "extract-errmsg-authority" ci/pipelines/ ci/tasks/ ci/scripts/run-static-gates.sh` ⇒ 零命中）；schema 提取器**连 `--check` 模式都没有** ⇒ R6 的基准↔Go 对应关系**连本地都无法机器复算** |
| **为何不符** | 建设方向要求"**Go 原始项目提供的业务语义和契约要求没有因为语言迁移而丢失**"。当 WHAT 的载体是一份**仓内可改文件**、且唯一保证是**人工 PR review** 时，CI **结构上无法**发现"基准被单方面改成 Java 现状"——而这**正是新方向明令禁止的"反向定义"**。R5 已把 Go 源文件 sha256 写进基准头（`ci/contracts/errmsg/like.txt:12`）⇒ **指纹在文件里，但没人校验它** |
| **会漏掉什么** | ① 有人把基准改成 Java 现状 ⇒ CI 全绿，且**清单里的 VERIFIED 条目照样计入 PASS**（最危险的一种假绿）；② Go 侧语义变了而基准没跟上 ⇒ 漂移长期不被发现；③ R4 的 `ruling`（签字 id）只是注释文本 ⇒ 签字被撤销后 R4 仍强制执行该偏离 |
| **应达到什么结果** | ① 给 `extract-schema-baseline.py` 补 `--check`（与 errmsg 对称）；② 把两个提取器的 `--check` 挂到一条**"基准复核"任务**（手动触发 + 基准变更 PR 必跑）；③ 让 CI **校验基准头里记录的 Go 源 sha256 与登记值一致**（sha256 已在文件里，只需加一道比对）⇒ 这样"基准漂移"与"Go 漂移"都能机器发现；④ R4 的 `ruling` 改成**机器可校验字段**（对齐 R5 的 deviations 段口径） |

### 🔴 P0-3 · `like` 域的 proto package wire 差异**既没解决也没任何守门**（`comment` 域已解决 ⇒ 同一条已签字偏离两域处置不一致）

| 字段 | 内容 |
|---|---|
| **文件位置** | Go `proto/like.proto:3` = `package pb;` · Go `proto/comment.proto:4` = `package pb;` · Java `service/like/like-proto/src/main/proto/like.proto:3` = `package like;` · 签字 `docs/Intentional_Deviations/like/proto.md:11`（`SD-2026-10-23-05`）/ `docs/Intentional_Deviations/comment/proto.md:11`（`SD-2026-10-23-04`）· **对照实现**：`service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/impl/PbCommentServiceAlias.java` + `PbCommentServiceAliasGateTest.java:114-147`（**已在 `maven-test` 步 ⇒ 进 CI**） |
| **当前行为** | `comment-rpc` 注册了 **Go 兼容别名** `pb.CommentService`（守门测试断言"Go 的方法全名 `/pb.CommentService/GetComment` 必须打到实现"）；**`like-rpc` 没有对应实现**（`grep -rn "PbLikeServiceAlias\|pb.LikeService" service/like/` ⇒ 只命中 `LikeProtoContractTest.java:61` 的 javadoc）⇒ like-rpc 只服务 `/like.LikeService/*` |
| **为何不符** | ① 签字档自陈的安全条件是"**当前仓内不存在跨栈调用 ⇒ 现在安全**"——但**这个安全条件本身没有被任何机器判据锁住**（CI 零覆盖 method path）；② 更关键：**同一条已签字偏离，`comment` 域已被工程化解决（别名 + 守门测试），`like` 域没有** ⇒ 两个域的处置**不一致**，而签字档里的措辞完全相同 |
| **会漏掉什么** | 迁移期双栈并存、或将来任何 Go↔Java 跨栈调用 ⇒ **like 域直接 UNIMPLEMENTED**，且 CI 不会红。（`comment` 域不会，因为有别名） |
| **应达到什么结果** | ① `like-rpc` 补 `PbLikeServiceAlias` + 同型守门测试（**照搬 comment 现成实现**即可，零设计成本）；② CI 增一道「**gRPC service 全名 / 方法路径**」门：断言 RAG 侧注册的 service 全名集合 == 基准（基准含 Go 侧 `pb.*` 别名要求），且基准的 `pb.*` 要求与 `docs/Intentional_Deviations/` 的签字条目**机器回指** |

### 🟡 P1-1 · 行为类维度全部 NOT_VERIFIED，且 **NOT_VERIFIED 不阻断任何流水线** ⇒ CI 是"提交不回归门"，不是"迁移达标门"

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/requirements/requirements.json`（18 条 NOT_VERIFIED）· `ci/scripts/check-requirements.py:82`（`NOT_PASS_STATUSES`）· `:28`（`GAPS_ONLY = 3`）· `:396-403`（`main` 的默认退出码） |
| **当前行为** | 清单门默认 EXIT=0；只有**结构违规**（R1–R9 规则被破）或**某条 VERIFIED 声明复验失败**才红；`NOT_VERIFIED` 只被**打印**，不阻断。⇒ **今天把 18 条行为要求全部写死不动，CI 依然全绿** |
| **为何不符** | 方向验收标准写明"**缺少基准、检查未执行、环境异常时，不允许伪装成 PASS**"——清单守住了"不计入 PASS"这一半 ✅，但**另一半没守住**：没有任何门会因为"行为层长期未验证"而阻断合并 ⇒ **绿灯的含义被稀释成"没有回归"而非"目标达成"** |
| **会漏掉什么** | Java 侧引入任何**行为级**回归：状态机错、错误码传播错、schema 约束缺失、`optional` presence 语义错、MQ 投递语义错、调用图漂移 ⇒ **全部不被检出** |
| **应达到什么结果** | ① 把**迁移验收门禁**与**提交门禁**显式分成两个口径（本报告 §五 已给出区分）；② 行为层覆盖率做成**可见且单调**的指标（`NOT_VERIFIED` 条数**只许降不许升**，涨了即红）；③ 在 CI 产物与报告中**显式打印"迁移验收未通过（N 项未验证）"** ⇒ 任何"全绿 = 达标"的误读被就地掐断 |

### 🟡 P1-2 · `rag-contract-errmsg` 的触发面**不含业务逻辑目录** ⇒ 改错误码用法根本不触发契约门

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/pipelines/rag-contract-errmsg.yml`（`paths`：`<域>-common/.../errmsg/**` · `tools/errmsg-probe/**` · `service/task/**` · `ci/contracts/errmsg/**` · `ci/**`）· 对照 `ci/pipelines/rag-like.yml`（`paths`：`service/like/**`） |
| **当前行为** | 改 `service/like/like-rpc/.../service/LikeActionLogic.java` ⇒ 触发 `rag-like`（宽）但**不触发** `rag-contract-errmsg`（窄）⇒ **R5 契约门不跑** |
| **为何不符** | R5 守的是"错误码集合与文案"（常量定义层）⇒ 窄触发面对它**是对的**；但目标要求里最难的 `REQ-ERRMSG-ALL-002`（**错误码与业务场景的对应关系**）恰恰落在**未触发**的目录 ⇒ 守门永远够不着 |
| **会漏掉什么** | 把某处业务场景的错误码从 A 改成 B（**常量与文案都没变**）⇒ 契约门不跑、清单那条也永远是 NOT_VERIFIED ⇒ **漂移零阻力** |
| **应达到什么结果** | ① 契约流水线 paths 至少覆盖"可能改变错误码用法"的代码面（或）② 新增一条专门的「**错误码用法**」静态门：扫 `status.Error` / `ErrorCode.` 引用点形成**调用点清单**并与 Go 侧逐调用点基准对照（Go 侧 task 域已有 `GOCALL` 段的先例可复用） |

### 🟡 P1-3 · `check-surface-parity.py` 对签字域**整体豁免** ⇒ 掩盖未批准漂移

| 字段 | 内容 |
|---|---|
| **文件位置** | `tools/difftest/check-surface-parity.py:333-338` |
| **当前行为** | `users` / `admin` 两域只要存在任何差异，就整体列入"🟡 已签字偏离域（**不计失败**）"，**只报计数**（Go=9 / RAG=9 / 差异 9+9），**不分类** |
| **为何不符** | 签字的是**两项具体事实**：path 前缀（`/usercenter/v1`→`/user/v1`）与 method（GET/DELETE 混合 → 统一 POST）（`docs/Intentional_Deviations/_cross/signed-deviations.md:15-16` · `docs/Intentional_Deviations/users/admin/_shared.md:20-21`）——**不是"该域任何差异"** |
| **会漏掉什么** | `users` / `admin` 域将来**新增任何一条**端点级漂移（改路径 / 改方法 / 多一个端点 / 少一个端点）都会被并入"已签字计数"而不报警 |
| **应达到什么结果** | 按**路径前缀 / HTTP method / 端点集合**三个维度**分别**分类；只有与签字条目**逐条对应**的才算已批准；其余一律报红 |

### 🟡 P1-4 · R6 schema 基准的"未确认差异"段**没有任何门在消费** ⇒ 已知 schema 漂移可以长期挂着

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/contracts/schema/users.json`（`unconfirmed` 段 · `known_residues_not_enforced` 段）· `ci/requirements/requirements.json`（`REQ-SCHEMA-USERS-001` = NOT_VERIFIED）· `tools/difftest/extract-schema-baseline.py:477-480`（无 `--check`） |
| **当前行为** | 基准里登记了"未确认差异"（`users_user.status` 可空性 / DEFAULT 残留 / CHECK / 触发器 / 非唯一索引），检查器**不据此阻断**；`REQ-SCHEMA-USERS-001` 判 NOT_VERIFIED ⇒ CI 绿 |
| **为何不符** | R6 的判定标准明确要求区分 ①业务语义必须保持 ②Java 技术栈合理差异 ③已确认允许 ④**尚未确认**——④应当**可见且逐步收敛**，当前只是文字 |
| **会漏掉什么** | `users` 域的 schema 漂移（**Go 侧 `status` 可空性有默认值、RAG 侧没有**）永远不会被 CI 拦住；`DEFAULT CURRENT_TIMESTAMP` 残留 22 处同理 |
| **应达到什么结果** | 未确认差异要么在清单里**逐条登记成带台账 id 的条目**（照搬 R5 的 `deviations` 段口径：状态四态 + 依据回指），要么被门阻断 |

### 🟡 P1-5 · 流水线 pin 的是 **branch 而非 commit** ⇒ "CI 里跑绿"不可归因到具体代码状态

| 字段 | 内容 |
|---|---|
| **文件位置** | `ci/generate-pipelines.py:61`（`GIT_BRANCH_VAR = "((git_branch))"`）· `:452` · `:573`（`source: branch: ((git_branch))`） |
| **当前行为** | git 资源的 `source` 是 `branch`；13 条流水线**全 paused**；远端 `origin/do-my-own-gh` tip = `a2820125`（**连 `ci/` 目录都不存在**）；本地主干比远端 **ahead 1016** 笔 |
| **为何不符** | "本地跑绿"与"CI 里跑绿"读的是**两棵树**（本地工作树 vs 流水线 pin 的远端 tip）⇒ 任何"某次 CI 验证通过"的陈述若不指明 commit 都是**不可归因的**；且解暂停重跑**验不到**销账后的状态 |
| **会漏掉什么** | 本地改动从未进过 CI 却可能被当作"CI 已覆盖"；已发生的契约变更**至今未在 Concourse 里跑过**（R5 签字后只做了本机复跑） |
| **应达到什么结果** | 验收 / 审计场景 pin 到**具体 commit**（`tag:`）；或至少在 CI 产物里**打印并记录所跑的 commit sha**，使"跑绿"可归因 |

### ⚪ P2 · 杂项与一致性

| # | 问题 | 位置 | 应达到 |
|---|---|---|---|
| **P2-1** | `.DS_Store` × 4 + `__pycache__/*.pyc` 混进 CI 资产目录（86 个文件里 7 个是垃圾）⇒ `run-static-gates.sh` 的扫描面被污染（需纯 clone 才能跑） | `ci/.DS_Store` · `ci/{pipelines,scripts,tasks}/.DS_Store` · `ci/scripts/__pycache__/` | 加 `.gitignore` 规则；门内扫描面显式排除 |
| **P2-2** | R4 基准的签字回指是**注释文本**，R5 的是**机器可校验字段** ⇒ 两族口径不齐 | `ci/contracts/proto/like.proto:3`（`# 裁定依据：SD-...`）vs `ci/contracts/errmsg/like.txt`（`DEVIATION\|...\|approved\|<id>\|<出处>`） | R4 也改成结构化 deviations 段（对齐 R5） |
| **P2-3** | `ci/BASELINE.md` §4.3.3 / §九 的数字是**手工维护**，且仍留有"9 条需求 R1~R9""11 道静态门"等旧数（实为 13 道门 / R1~R10） | `ci/BASELINE.md:407,594,597` | 数字从清单与门清单**自动生成**（`tools/check-ledger-count.py` 已有先例） |
| **P2-4** | `coverage.unregistered_gap_ledgers` 登记了 `docs/DRIFT/common/alignment-2026-10-23.md` 的 **28 项未修**，但那 28 项**本身没有变成清单条目** ⇒ 不可计数、不可追踪 | `ci/requirements/requirements.json`（`coverage` 段） | 每一条落成清单条目（状态 NOT_IMPLEMENTED / NOT_VERIFIED + 台账指针） |

---

## 五、迁移完整性与 CI 门禁

### 5.1 六态归类（46 条 + 本轮新发现）

| 归类 | 条数 / 数量 | 内容 |
|---|---|---|
| **✅ 已实现已验证** | **23 条清单项**（+ 若干清单外但有 CI 门的项） | proto 文件契约 10 域（第 10 道门）· errmsg 契约 article/favorite/hot/users 4 域（`rag-contract-errmsg`）· schema 契约 7 域（`rag-contract-schema`）· Tomcat 表单段数上限 · deadline 探针判定器接线<br>**清单外但有门**：HTTP 契约面（**本地**实跑绿，但**不在 CI** ⇒ 归入 P0-1）· gRPC 方法面（同上） |
| **🟡 已实现未验证** | **9 类**（子代理只读盘点 + 本轮核） | ① HTTP 契约面 72 端点 + gRPC 方法面 71 个（本地实跑绿，**CI 无门**）② `users` 域**码表**无逐条对照 Go 的守门测试（`*ErrorCodeContractTest` 类族覆盖 article/comment/favorite/follow/hot/like **6 域**；message 有等价命名的 `MessageErrorMessagesTest` + `MessageGrpcErrorTest`；users 只有 `UserLogoutErrorCodeParityTest` 覆盖单个登出码 ⇒ **31 个码值无逐条守门**）③ `favorite` 状态转换 + fact delivery 无专属守门 ④ `hot` 缺 `*RpcProductionAddressTest` ⑤ `follow` / `task` metrics 指标名无对拍 ⑥ `task` gRPC status→业务码映射无契约测试 ⑦ Go 侧**零调用点**的前提（如 `like` 域 `AntiBrush` / `hot` 域 `GRPCError`）无运行证据 ⑧ `users` 域 DDL 走 `SchemaInitializer`（无 Flyway）⇒ 无列级对拍 ⑨ 清单里 **13 条非契约基线类**的行为级条目（proto_consumption 4 · errmsg_propagation 2 · schema_constraint 2 · runtime_behavior 4 · callgraph 1） |
| **⛔ 未实现**（Go 有、Java 没有） | **1 条真实 + 1 条对等缺口** | **① `article` 的 security-rpc 两个真实客户端**（Go `service/article/rpc/internal/mqs/article_consumer.go:154-172,253` 真实调 `SanitizeContent` / `DetectImageAd`；Java `service/article/article-rpc/.../client/SecurityRpcClient.java:3` 与 `ImageSecurityRpcClient.java:3` **仅一行注释占位**）⇒ ⚠️ **因 `security` 域范围外 ⇒ 按 AGENTS.md §一「不是待办」**；**但运行时后果是 Java 侧 article 审核链路恒失败（文章永远卡 REVIEWING）** ⇒ **不得按 PASS 统计**<br>② **`like` 域的 `pb.LikeService` 跨栈别名**（`comment` 域已有）⇒ P0-3<br>③ `task-common` / `task` 域码表：**Go 侧同样没有** ⇒ **对等缺口，不是迁移缺口** |
| **🔁 语义等价**（实现方式不同、语义保持） | **5 类** | 建表机制（GORM `AutoMigrate` ↔ Flyway + 幂等 `SchemaInitializer`）· 熔断/限流（**Go 侧零消费**：`AntiBrush`/`HotSpot` 配置存在但 `ServiceContext` 未装配 ⇒ Java 不实现反而对齐）· 分页三派形态（page/page_size ↔ offset/limit ↔ cursor/limit）· 响应信封（`{code,msg,data}` 三字段、`data` 无 `omitempty`）· 时间语义跨域不一致（`comment` 域显式 `TimeZone=Asia/Shanghai`，users/article 无）——后者**两侧本来就不一致**，不是 Java 引入的漂移 |
| **🏷 未批准偏差** | **0 条（本轮新发现）** | ⚠️ **重点**：本轮逐条核过三个最像缺陷的项，**全部已签** ——<br>① `users`/`admin` 的 HTTP **method** 归一（Go GET/DELETE 混合 → Java 统一 POST）⇒ 已签（`docs/Intentional_Deviations/_cross/signed-deviations.md:16` · `docs/Intentional_Deviations/users/admin/_shared.md:21`）<br>② `comment`/`like` 的 proto `package` 归一 ⇒ 已签（`SD-2026-10-23-04` / `-05`）<br>③ `like`/`comment` 的业务码 wire 载体 ⇒ 已签（`SD-2026-10-31-01` / `-02`）<br>🔴 **但**：P1-3 的整体豁免机制意味着 `users`/`admin` 域**新增**漂移的**可见性为零** ⇒ 「未批准偏差」的**当前数 = 0 是门禁覆盖不足的产物，不是保证** |
| **❓ 证据不足** | **5 类** | ① `hot` 域 schema = NOT_RUN（两侧皆无业务表）② `users` 域 schema = NOT_VERIFIED ③ Go `task` 域**无码表** ⇒ 权威 = 逐调用点（已用 `GOCALL` 段处理，但文案以 `*` 占位 ⇒ 运行期相关不可逐字比对）④ R5 的 13 条 `technical` 差异仅凭"Go 侧不可达"判定 ⑤ Go 侧 `follow` / `task` 的 metrics 文件未定位 |

### 5.2 提交门禁 vs 迁移验收门禁（**本报告最关键的结构性结论**）

| 维度 | **提交门禁**（每次提交是否达标） | **迁移验收门禁**（整个 Java 项目是否达到 Go 的目标） |
|---|---|---|
| **构成** | 13 道静态门 · `maven-build` · `maven-test` · `report` · 2 条契约流水线（`rag-contract-errmsg` / `rag-contract-schema`） | 🔴 **目前不存在** |
| **触发** | 按 Maven 依赖图反推的 paths（`generate-pipelines.py` + 第 12 道门自校验） | — |
| **能检出** | ① 代码改了导致契约基线不一致 ② 测试失败 ③ 产物不可执行 ④ 密钥/NUL/口令形态 ⑤ 触发面漂移 ⑥ 清单结构违规 / 签字回指断裂 | 🔴 **几乎不能检出**：行为级要求（状态机 / 幂等 / 错误码传播 / schema 约束 / presence 语义 / 调用图 / 对外契约面）**全部未被验证** |
| **当前读数** | 全绿（13/13 静态门 · PASS 23 · 契约 9/8 · 触发面 7/7 · 变异 10/10 + 11/11） | **23/46 VERIFIED · 18 NOT_VERIFIED · 1 条真实未实现（范围外）** |
| **结论** | ✅ **可交付** | 🔴 **不可交付** —— 当前**没有任何一条流水线会因为"行为层未验证"而红** |

> ⇒ **"CI 全绿"目前只证明"没有回归"，不证明"迁移达标"。**
> ⇒ 这是本次审查最重要的判断，也是"Go 定 WHAT / Java 定 HOW / CI 验是否做到"这条方向在**当前实现**上**尚未闭合**的确切位置。

---

## 六、整改建议（**有先后顺序**）

> 排序依据 = **依赖关系**（后项依赖前项的产物）+ **投入产出比**。

### 第 0 步 · 先把审查结论固化（**今天就能做，零代码风险**）

1. 把本报告登记进 `docs/DRIFT/_cross/`（**由收口执行者落，本 lane 不写 SHARED 面**），并把 §五 的「提交门禁 vs 迁移验收门禁」两口径写进 `ci/README.md` ⇒ **先把"全绿 ≠ 达标"这句话钉在 CI 自己的文档里**。
2. 把「迁移验收未通过（18 项未验证）」做成 CI 产物的**固定输出行**（不改判定逻辑，只加打印）⇒ 消除"全绿 = 达标"的误读。

### 第 1 步 · 补「WHAT 的机器可读载体」：新增 `ci/contracts/surface/`

> **这是 P0-1 的正解，也是后续 3 步的地基。**

3. 离线提取 Go 的 **HTTP 端点面（method + path + 归一化前缀 + JWT 保护面）+ gRPC 方法集合 + gRPC service 全名集合** → `ci/contracts/surface/<域>.json`（沿用 proto/errmsg/schema 的四步法：提取 → 人工裁定 → 固化 → CI 只读基准）。
4. 新增第 14 道门 `check-surface-contract.py`（**只读基准 + Java 侧** ⇒ 不引 Go 进 CI）：比对 `@RequestMapping` / `@*Mapping` 与 `*ServiceGrpc.*ImplBase` 覆写方法集合。
5. 清单新增 `http_surface` / `grpc_surface` 两个维度并逐条登记 `go_basis`；**同步扩展 R10 覆盖守门到 `surface/`** ⇒ 今后再缺维度会被当场抓住。
6. 把签字域的豁免改成**按 (路径前缀 / method / 端点集合) 三类分别对照签字条目** ⇒ 顺手闭合 **P1-3**。

### 第 2 步 · 补「基准可复算」：让基准漂移可被机器发现

7. 给 `tools/difftest/extract-schema-baseline.py` 补 `--check`（与 errmsg 对称）⇒ 闭合 **P0-2** 的一半。
8. 把 `extract-errmsg-authority.py --check` + 新的 schema/surface `--check` 挂到一条「**基准复核**」任务：手动触发 + **任何 `ci/contracts/**` 变更的 PR 必跑**（不依赖 Go 仓在线可达 ⇒ 只在 PR 侧跑，**不违反"Go 不进 CI"**）。
9. 让 CI 校验基准头里记录的 **Go 源 sha256** 与登记值一致 ⇒ 指纹已经在文件里，只需加一道比对。
10. R4 基准加**结构化 deviations 段**（对齐 R5 口径）⇒ 闭合 **P2-2**。

### 第 3 步 · 补「像 `like` 域这样的 wire 路径缺口」：统一两域处置

11. `like-rpc` 补 `PbLikeServiceAlias` + 同型守门测试（**照搬 `comment` 域现成实现**）⇒ 闭合 **P0-3** 的实体部分。
12. 第 14 道门里加「**gRPC service 全名 / 方法路径**」判据：断言 RAG 侧注册的全名集合 == 基准（基准含 Go 侧 `pb.*` 别名要求），且与签字条目机器回指 ⇒ 闭合 **P0-3** 的守门部分。

### 第 4 步 · 把「迁移验收门禁」真正建起来

13. 把 18 条 NOT_VERIFIED 逐条拆成四类：**可立即验证的**（补判据）· **需 Java 先实现的** · **需用户签字的** · **范围外的** ⇒ 每条给出可复算判据或明确的处置结论。
14. 把**行为层覆盖率**做成单调指标：`NOT_VERIFIED` 条数**只许降不许升**，涨了即红 ⇒ 闭合 **P1-1**。
15. 建立**迁移验收流水线**（独立于 11 条日常流水线）：聚合 14 道门 + 清单 + 契约三门 ⇒ 产出一份"迁移验收报告"（含 **未验证项清单**）⇒ 这是"Go 定 WHAT"闭环的最后一环。

### 第 5 步 · 补触发面与可归因性

16. `rag-contract-errmsg` 的 paths 补上"可能改变错误码用法"的代码面；或新增「错误码用法」静态门（扫调用点，形成调用点清单，与 Go 侧逐调用点基准对照）⇒ 闭合 **P1-2**。
17. R6 基准的 `unconfirmed` 段逐条落成清单条目（带台账 id）⇒ 闭合 **P1-4**。
18. 流水线 pin 从 branch 改 commit（或至少在 CI 产物里记录所跑 commit sha）⇒ 闭合 **P1-5**；并**解暂停重跑一次**，把"契约门在 Concourse 里跑绿"从"本机跑绿"升级为"CI 里跑绿"（含 R5 签字后的重跑）。

### 第 6 步 · 杂项

19. 清 `.DS_Store` / `__pycache__` + `.gitignore`（P2-1）· `BASELINE.md` 数字改自动生成（P2-3）· `coverage` 的 28 项未修落成清单条目（P2-4）。

### 并行可做（**不依赖上面任何一步**）

20. 🔴 **`RealGrpcPort.isFree()` 的 Linux-only 缺陷**（R3）—— 波及 21 个文件 / 9 个域 ⇒ **修好前除共享层外的域流水线在 test 步必红** ⇒ 这是"迁移验收"路上最大的**实质阻塞**，属域侧测试代码写权，**应立即派单**。
21. `users` 域补**码表级**错误码契约测试（`*ErrorCodeContractTest` 类族已有 article/comment/favorite/follow/hot/like **6 域**；message 有等价命名版本；users 只覆盖了单个登出码 ⇒ 31 个码值无逐条守门 ⇒ 最廉价的一处补网）。
22. `hot` 域补 `*RpcProductionAddressTest`（该类族已有 article/comment/favorite/follow/like/message **6 域**，hot / users / task 缺）；`favorite` 补状态转换 / fact-delivery 守门。

---

## 七、结论：现在这套 CI 能否可靠地判断 Java 中间产物是否符合 Go 原始项目的目标要求？

> ### **不能。**
>
> ### 原因（三条，按严重性排序）
>
> **① 它把"WHAT"这一层的一半留在了仓外，因而无从判断。**
> Go 定义的**对外契约面**（72 个 HTTP 端点 + 71 个 gRPC 方法 + service 全名集合）**在整个 CI 里不存在**——不是"没查"，是**连要求都没登记**（清单 46 条里没有这一维，而覆盖守门 R10 只看三个基准目录 ⇒ 不可见）；唯一能查它的 `check-surface-parity.py` 因为需要 Go 仓而被注释出全部 13 条流水线。**方向要求"Go 定 WHAT"是对的，但 WHAT 的机器可读载体只做了 proto / 错误码 / Schema 三块 ⇒ 剩下的 Go 语义（行为层 18 条 + 对外契约面）在 CI 的视野之外。**
>
> **② 它验证的是"Java 符合冻结基准"，不是"Java 符合 Go"。**
> 三族契约基准确实是 Go 派生的（R5 已用独立复算确认 10/10 逐字节一致），但 **CI 内没有任何门能重算基准**：`extract-errmsg-authority.py --check` 存在却不进 CI，`extract-schema-baseline.py` 连 `--check` 都没有，R4 的签字回指只是注释文本。⇒ **基准可被单方面改成"Java 现状"后自证全绿**，而这正是新方向明令禁止的"反向定义"。**冻结基准换来了"CI 不依赖 Go"，代价是失去了"CI 能发现基准自身漂移"——这是本设计的固有代价，必须被显式承认为缺口而不是被当作已解决。**
>
> **③ 它的绿灯含义被稀释了：现在是一套"提交不回归门"，不是一套"迁移达标门"。**
> 23/46 VERIFIED 里几乎全是**契约基线类**（proto 文件 / 错误码 / schema），**行为类维度（状态机 / 幂等 / 错误码传播 / schema 约束 / presence 语义 / 调用图）全部 NOT_VERIFIED**，而 **NOT_VERIFIED 不阻断任何流水线**（`check-requirements.py` 只在结构违规或 VERIFIED 复验失败时才红）。⇒ **今天把 18 条未验证要求全部写死不动，CI 依然全绿**；任何行为级回归（状态机错、错误码传播错、presence 语义错、schema 约束缺失）都**不被检出**。此外还有两条结构性洞：`like` 域的 proto package wire 差异**既没解决（`comment` 域已解决）也没任何守门**（跨栈互调会 UNIMPLEMENTED 而 CI 不红）；流水线 pin 的是 branch 而非 commit ⇒ **"CI 里跑绿"本身不可归因**。
>
> ### 一句话收口
>
> **这套 CI 已经做到"不回归"，还没做到"达标"。**
> 它值得保留（13 道门 + 3 族契约 + 目标要求清单 + 六态口径 + 变异自证，方向全部正确、工程质量扎实、没有任何一处为了让 CI 变绿而降低 Go 要求）；
> **补上 §六 第 1~4 步（surface 基准 → 基准可复算 → wire 路径补齐 → 迁移验收门禁）之后，它才有资格回答那个问题。**
> 在那之前，任何"CI 全绿 = 迁移达标"的表述都是**不成立的**。

---

## 附录 A · 本报告用到的关键证据索引

| 主题 | 位置 |
|---|---|
| CI 资产清单 | `ci/`（86 文件）· `ci/pipelines/*.yml`（13）· `ci/tasks/*.yml`（6）· `ci/scripts/run-static-gates.sh:128-186`（13 道门） |
| 目标要求清单 | `ci/requirements/requirements.json`（46 条）· `ci/requirements/README.md:99-111`（R1–R11 规则）· `ci/scripts/check-requirements.py:196-317`（规则实现）· `:82`（PASS 口径）· `:298-300`（R10 覆盖面） |
| R4 proto 基准 | `ci/contracts/proto/*.proto`（10）· `ci/scripts/check-proto-contract.py:76-179`（域表）· `:191-204`（归一化）· `:507-532`（提取期 package 校验） |
| R5 errmsg 基准 | `ci/contracts/errmsg/*.txt`（10，两段式）· `ci/scripts/check-errmsg-contract.sh:309`（四态）· `:506-528`（自证豁免）· `ci/scripts/extract-errmsg-authority.py:48`（`--check`） |
| R6 schema 基准 | `ci/contracts/schema/*.json`（9）· `ci/scripts/check-schema-contract.py:20,226` · `tools/difftest/check-ddl-static.py:117` · `tools/difftest/extract-schema-baseline.py:477-480`（**无 `--check`**） |
| 契约面（缺口） | `tools/difftest/check-surface-parity.py:333-338`（整体豁免）· `ci/tasks/static-gates.yml:27`（注释）· `ci/pipelines/rag-*.yml`（11 处注释） |
| proto package wire 差异 | Go `proto/{like,comment}.proto:3,4` · Java `service/{like,comment}/*-proto/src/main/proto/*.proto:3` · 签字 `docs/Intentional_Deviations/{like,comment}/proto.md:11` · 别名实现 `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/impl/PbCommentServiceAlias.java` + `PbCommentServiceAliasGateTest.java:114-147` |
| 已签字的 method 归一 | `docs/Intentional_Deviations/_cross/signed-deviations.md:15-16` · `docs/Intentional_Deviations/users/admin/_shared.md:20-21` |
| Go 侧事实源 | `service/*/api/internal/handler/routes.go`（HTTP）· `proto/*.proto`（rpc）· `service/*/common/errmsg*.go`（码表）· `service/*/rpc/internal/model/*.go`（schema）· `service/*/rpc/etc/*.yaml`（topic / 端口 / 密钥）· `service/common/observability/*`（超时 / 499 / span） |
| Java 侧实现 | `service/common/.../GoStyle*`（共享层）· 各域 `*DomainService` / `*AtomicService` · `service/*/*-common/.../errmsg/*`（8 个 `*ErrorCode`）· `service/users/users-common/.../ddl/SchemaInitializer.java` |
| 触发面 | `ci/generate-pipelines.py:61,452,573`（branch pin）· `ci/pipelines/rag-contract-errmsg.yml`（窄 paths）· `ci/verify-triggers.sh`（第 12 道门） |

## 附录 B · 审查边界声明

- 本轮**只读**：未修改任何 Pipeline / Task / 脚本 / 测试 / 业务代码；唯一新建文件是本报告（`ci/CONSISTENCY-AUDIT-2026-10-31.md`）；仓外临时脚本 `/tmp/proto-semantic.py`（proto 语义骨架独立比对器，**不入仓**）。
- 本轮**未 push、未建 PR**（沿用既定约束）。
- 13 条 Concourse 流水线**全 paused**；`rag-contract-errmsg` / `rag-contract-schema` 的 Task 内实跑记录**沿用已有实测值**，本轮未重跑。
- `check-surface-parity.py` 与 `extract-errmsg-authority.py --check` 的实跑是**本地只读核验**（需 Go 真源仓）⇒ **不得进入 CI**；报告引用它们是为了说明"Java 侧当前是对的、但 CI 看不住"。
- Go 真源仓（`Sea-RideTheWind_golang`）**全程只读**，未做任何修改。
