# 阶段一任务全集（规范 §3.1 · 12 字段）

> **本文件是「现存任务全集」的完整映射**，不是自由提出的改进清单。
> 每条记录的来源都是当前仓内的真实台账 / 权威判据 / 既有执行规则。
> **轮次编排见 [`_INDEX.md`](_INDEX.md)；逐轮执行细节见 `round-01/` ~ `round-07/`。**

## 〇、编号与口径

| 项 | 口径 |
|---|---|
| **计划任务 ID** | `P<轮>-T<序号>`（例 `P1-T01`）。**新编号空间**，与旧计划 `T-0.x` / `T-1.x` / `R5-Fn` **不冲突**。**共 37 条**：`P1` 5 · `P2` 6 · `P3` 9 · `P4` 9 · `P5` 6 · `P6` 1 · `P7` 1 |
| **原始编号** | 原样保留 `DRIFT-*` / `PIT-*` / `SD-*` / `U-*` / `G-*` / `E*` / `D-*` / `R5-*` / `T-x.x` / `S1-USERS-*` / `X-n` / `A1~A9` 等；**无独立编号的写「原文无独立编号」** |
| **阶段一影响** | ① **阻断阶段一**（A 类）② 缺验收证据 ③ 环境受阻 ④ 允许暂缓 ⑤ 仅为执行约束 ⑥ 不属于阶段一 |
| **状态判据** | 以**条目级最新状态**为准（`PhaseGoal-judge.md` §1.5 L0）；旧快照不覆盖后续证据 |
| **本轮性质** | **只重构计划，不执行整改**（规范 §一.6 / §一.7）⇒ 本表中的「所需动作」**全部尚未执行** |

> ⚠️ **本表的「当前状态」列里有若干条是「台账写的是 A，但实测/实测证据显示是 B」** ⇒ 已逐条标注，不静默采信台账。

---

## 一、A 类 · 阻断阶段一（2 条）

### P1-T01 · 全服务起栈（P0 #5 闭合）

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P1-T01` |
| **原始编号** | `P0 #5`（`docs/PhasedGoal.md` §二）；`X-12`（`PhaseGoal-judge.md` §五）；`DRIFT-HOTAPI-030` 无关（不列） |
| **原始来源** | `docs/PhasedGoal.md:46`（P0 #5 原文）· `docs/PhaseGoal-judge.md:259`（X-12 行）· `docs/PhaseGoal-judge.md:436`（§8.4 第 2 类硬阻断）· `README.md:127-147`（§三「怎么起其余 9 个域」）· `.env.example:88-114` |
| **当前状态** | 台账写「✅ 已闭合（2026-10-29）」⇒ **实测证伪** ⇒ 按 Jason 2026-10-29 裁定 **复判为 A 类阻断** |
| **当前事实** | ① README §三 承诺「其余 9 个域」用 `set -a && . ./.env && set +a; java -jar …` 起；② `.env`/`.env.example` 的中间件地址是**容器内 DNS 名**（`nacos` / `postgres` / `redis` / `kafka:9092` / `minio:9000` / `jaeger:4317`）⇒ 宿主机**不可解析**；③ 实测照 README 原文起 `article-api` ⇒ `nacos registry … serverAddr='nacos:8848'` → `ErrCode:-401 Client not connected` → `Application run failed`（进程退出）；④ 只用 shell 覆盖地址 + 错开端口 ⇒ **article-api 正常启动**：`Tomcat started on port 8899` + `nacos registry, DEFAULT_GROUP article-api 10.133.78.244:8899 register finished` + `GET /actuator/health` ⇒ `200 {"status":"UP"}` ⇒ **缺陷面 = 地址 + 端口两类，不是代码问题**；⑤ **端口撞车**：`ADMIN_API_PORT=8889` **=** `ARTICLE_API_PORT=8889` · favorite-rpc / like-rpc / message-rpc **三个硬编码 8082**（+ follow-rpc 可配 8082）· **article-rpc 与 comment-rpc 都硬编码 9001** · **like-api 硬编码 18887**（`.env` 里无此键）；⑥ **README 与 `.env` 自相矛盾**：`.env.example:88` 注释自己写着「宿主机跑用 127.0.0.1」，而 README §三 的命令直接吃容器内值；⑦ `.env.example:2` 的标题仍写 **`Sea-RideTheWind_java`**（旧项目名）⇒ 文档不一致；⑧ **判定覆盖面 = 10 个域里的 1 个（users）、20 个服务里的 2 个** |
| **阶段一影响** | **① 阻断阶段一** —— P0 #5 主语是「**项目**」不是「某个域」；`README` 就是「实际项目说明」 |
| **所需动作** | ① 给宿主机起 jar 提供**可解析的中间件地址**（容器内值与宿主机值两套，不改 compose 的容器内语义）② **错开全部端口**，并把 4 处**硬编码端口 env 化** ③ 订正 README §三 + 端口表 + `.env.example` 标题 ④ **真起 20 个服务，逐个确认「在服务」**（Nacos 注册 + 端口监听 + health），不是「进程存活」 ⑤ 打通至少一条 **rpc→rpc** 与一条 **api→rpc→PG** 链路 |
| **前置条件** | RAG 栈在跑（**已具备**：`Sea-postgres` 49432 / `Sea-redis` 49380 / `Sea-nacos` 49848 / `Sea-jaeger` 49317 / `Sea-kafka` 49992 / `Sea-minio` 49100） |
| **验证方式** | 逐服务：① `lsof -nP -iTCP:<port> -sTCP:LISTEN` 有监听 ② Nacos 控制台 / `GET /nacos/v1/ns/instance/list` 能查到该服务名 ③ 该服务的健康端点 200；跨服务：`grpcurl` 打一次 rpc / `curl` 打一次 api |
| **完成条件** | **20 个服务全部「在服务」**（三个判据同时满足）＋ 端口表零冲突 ＋ README 第三节被第三方照做能起 ＋ **P0 #5 与 X-12 复判为 ✅ 并带活体证据** |
| **关联项** | `P3-T02`（P0 #5 行改判）· `P3-T03`（X-6 行）· `P3-T08`（变更记录）· `PIT-GLOBAL-038`（端口占用型假红）· `PIT-GLOBAL-032`（跑红也可能是假红 ⇒ 三步定性法） |
| **计划归属** | **Round 1**（`P1-T01` ~ `P1-T05`） |

---

### P1-T02 · 提供宿主机可解析的中间件地址（容器内值与宿主机值两套）

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P1-T02` |
| **原始编号** | `P0 #5` 的子项（原文无独立编号） |
| **原始来源** | `.env.example:88-114`（`POSTGRES_HOST` / `POSTGRES_ADDR` / `REDIS_ADDR` / `REDIS_HOST` / `NACOS_ADDR` / `NACOS_PORT` / `KAFKA_BROKERS` / `MINIO_ENDPOINT` / `MINIO_PUBLIC_BASE_URL` / `OTEL_ADDR` / `OTEL_EXPORTER_OTLP_ENDPOINT` / `OTLP_PORT`）· `README.md:136` · `docker-compose.yaml` |
| **当前状态** | 🔴 **未做**。`.env` / `.env.example` 里这些值**全是容器内 DNS 名** ⇒ 宿主机起 jar 时 `nacos` / `postgres` / `redis` / `kafka` / `minio` / `jaeger` **解析不了** |
| **当前事实** | 实测：照 README §三 原文起 `article-api` ⇒ `nacos registry … serverAddr='nacos:8848'` → `ErrCode:-401 Client not connected` → `Application run failed`（**进程退出**）；只用 shell 覆盖地址 ⇒ article-api **正常启动**（`Tomcat started on port 8899` + Nacos 注册成功 + health 200）⇒ **缺陷面 = 地址 + 端口两类，不是代码问题** |
| **阶段一影响** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） |
| **所需动作** | ① 选一种结构（**推荐 A**：新增 `.env.host.example` 宿主机版，`.env.example` 容器内值**一字不改** ⇒ compose 零风险）② 逐个变量给出**宿主机值**（`127.0.0.1` + 宿主映射端口）③ ⚠️ **OTEL 的 URL 形态要验**：`http://127.0.0.1:49317:4317` ⇒ `Invalid URL port: "49317:4317"`（导出失败**不阻断启动**）⇒ 正确形态 `http://127.0.0.1:4317` ④ README §三 改成「compose 用 `.env`；宿主机起 jar 用 `.env.host`」两段 |
| **前置条件** | `P1-T01` |
| **验证方式** | ① 宿主机模板里**零个**容器内 DNS 名 ② `docker compose config` EXIT=0 ③ 缺必填变量时 compose **仍拒绝渲染**（P0 #8 / X-11 的 fail-closed 不能被弄坏） |
| **完成条件** | **两种部署形态各自有可直接照抄的命令**，且 compose 的容器内语义与 fail-closed **逐字未变** |
| **关联项** | `P1-T03`（端口）· `P1-T04`（README）· `P1-T05`（真起）· `PIT-GLOBAL-038` |
| **计划归属** | **Round 1** |

---

### P1-T03 · 端口错开 + 硬编码端口 env 化

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P1-T03` |
| **原始编号** | `P0 #5` 的子项（原文无独立编号） |
| **原始来源** | `.env.example:122-146` · `service/article/article-rpc/src/main/resources/application.yml:117`（`port: 9001`）· `service/comment/comment-rpc/.../application.yml:209`（`port: 9001`）· `service/favorite/favorite-rpc/.../application.yml:124`（`port: 8082`）· `service/like/like-rpc/.../application.yml:208`（`port: 8082`）· `service/message/message-rpc/.../application.yml:120`（`port: 8082`）· `service/like/like-api/.../application.yml:33`（`port: 18887`）· `README.md:105-119` |
| **当前状态** | 🔴 **未做**。① `ADMIN_API_PORT=8889` **=** `ARTICLE_API_PORT=8889`（`.env.example:124` vs `:128`）② favorite-rpc / like-rpc / message-rpc **三处 yaml 硬编码 8082**（不可 env 覆盖）③ **article-rpc 与 comment-rpc 都硬编码 9001** ④ **like-api 硬编码 18887**（`.env` 里**没有** `LIKE_API_PORT` 键）⑤ README 端口表把 favorite / follow / like / message 四个 rpc 都写成 8082 |
| **当前事实** | 20 个服务**同机并行** ⇒ 端口必须两两不同；硬编码端口在同机并行时**无法错开** ⇒ 必须 env 化 |
| **阶段一影响** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） |
| **所需动作** | ① 选一套**互不冲突**的端口（保留现有值优先，最小改动）② 把 **6 处硬编码端口 env 化**（`grpc.server.port: 9001` ⇒ `${ARTICLE_RPC_PORT:9001}` 等）③ `like-api` 的 `server.port: 18887` ⇒ `${LIKE_API_PORT:18887}` ④ 同步 README 端口表 ⑤ ⚠️ `hot-rpc` 的 `management.server.port: -1`（禁用 HTTP server）⇒ 无 management 端口冲突；`follow-rpc` 的 management 端口是 `${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ⇒ 单独占一个 |
| **前置条件** | `P1-T01`（端口表） |
| **验证方式** | ① 硬编码端口清单**归零**（脚本判定 `HARDCODED` 无输出）② `CONFLICT` 无输出 ③ 每个端口归属唯一 |
| **完成条件** | **20 个服务的监听端口两两不同** ＋ **6 处硬编码端口全部 env 化** ＋ `.env` / `.env.example` / README 三处端口表**逐条一致** |
| **关联项** | `P1-T02`（地址）· `P1-T05`（真起）· `PIT-GLOBAL-038`（端口占用型假红） |
| **计划归属** | **Round 1** |

---

### P1-T04 · 订正 README §三 与 `.env.example` 标题

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P1-T04` |
| **原始编号** | `P0 #5` · `D-02`（`docs/PhaseGoal-judge.md:561`） |
| **原始来源** | `README.md:105-119`（端口表）· `README.md:127-147`（§三）· `.env.example:2`（标题写 `Sea-RideTheWind_java`）· `README.md:13` |
| **当前状态** | 🟡 **部分完成**：README 已是 9,815 B 的照做说明（D-02 已闭合），但**§三 的宿主机命令不可执行** ⇒ D-02 的**残留**是「能读不能跑」 |
| **当前事实** | ① README §三 承诺「其余 9 个域」用 `set -a && . ./.env && set +a; java -jar …` 起，但 `.env` 的中间件地址是容器内 DNS 名 ⇒ 宿主机**跑不起来** ② `.env.example:88` 的注释**自己写着**「宿主机跑用 127.0.0.1」⇒ README 与 `.env` 自相矛盾 ③ `.env.example:2` 标题仍写**旧项目名** |
| **阶段一影响** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） |
| **所需动作** | ① README §三 改成两段（「起 users 域（compose）」+「起其余 9 个域（宿主机 jar）」）② 端口表按 `P1-T03` 的新端口**重写**（含 20 个服务 + 各自「就绪判据」）③ §五「已知限制」表第 1 行保留但**补一句**「其余 9 域按 §三 的宿主机命令起」④ `.env.example:2` 标题改 `RAG` ⑤ ⚠️ **不要**在 README 里写「已通过」「全部对齐」之类结论句（结论归 judge） |
| **前置条件** | `P1-T02` · `P1-T03` |
| **验证方式** | ① `python3 tools/check-doc-links.py` EXIT=0 ② `python3 tools/difftest/check-no-prod-secrets.py` EXIT=0 ③ 旧项目名 0 命中 ④ README §三 的命令块**逐行可复制** |
| **完成条件** | **第三方只读 README §三 就能起起其余 9 个域**（不依赖本轮之外的隐含知识） |
| **关联项** | `P1-T02` · `P1-T03` · `P1-T05` |
| **计划归属** | **Round 1** |

---

### P1-T05 · 真起 20 个服务，逐个确认「在服务」

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P1-T05` |
| **原始编号** | `P0 #5` · `X-12` |
| **原始来源** | `docs/PhaseGoal-judge.md:436`（v7 上调依据）· `README.md:127-147` |
| **当前状态** | 🔴 **未做**（users 域 2 个服务已通过 `./manage.sh up` 起过 ⇒ 覆盖 **2/20**） |
| **当前事实** | 判据要求「**项目**能按说明启动」⇒ 必须**真的把 20 个服务起起来**，且每个都要证明**在服务**而不是**进程活着**；判定覆盖面 = **10 个域里的 1 个（users）、20 个服务里的 2 个** |
| **阶段一影响** | ① **阻断阶段一**（A 类） |
| **所需动作** | ① 按 `P1-T01` 的**下游依赖顺序**起 ② 每起一个 ⇒ **三判据同时查**：Nacos 服务名可查 + 端口 LISTEN + 健康端点 200 ③ ⚠️ **起进程必须传值**（`${...}` 占位符未解析 ⇒ 进程启动即抛 `IllegalStateException`）④ ⚠️ **起进程用 `exec`**（`( cd X && cmd ) &` 的 `$!` 是子 shell PID ⇒ 收尾按**端口**复核）⑤ 收尾按**端口轮询**直到空 ⑥ **打通至少一条 rpc→rpc** 与 **一条 api→rpc→PG** 链路 |
| **前置条件** | `P1-T02` ∧ `P1-T03` ∧ `P1-T04` |
| **验证方式** | 逐服务：`lsof -nP -iTCP:<port> -sTCP:LISTEN` + `GET /nacos/v1/ns/instance/list` + health 端点 200；跨服务：`grpcurl` 打一次 rpc / `curl` 打一次 api |
| **完成条件** | **20/20 在服务**（三判据同时满足）＋ 两条跨服务链路打通 ⇒ **P0 #5 与 X-12 的证据就位** |
| **关联项** | `P2-T01`（E65 验收需要真栈）· `P3-T02`（P0 #5 行改判需要证据）· `PIT-GLOBAL-038` · `PIT-GLOBAL-032` |
| **计划归属** | **Round 1** |

---

### P2-T01 · E65 —— 出向 gRPC 不继承请求 deadline（Go 立刻失败 / RAG 无限挂住）

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T01` |
| **原始编号** | `E65` · `E38②`（窄化后的第③层残余）· `X-6`（`PhaseGoal-judge.md` §五）· `P0 #4` · comment 域 `D8` · like 域 `U15` · favorite 域 `E65` |
| **原始来源** | `docs/DRIFT/global/vs-common.md:30`（🔴 OPEN）· `:46`（完整重核 · 21 stub / 30 处零 deadline）· `:63`（A1 轴「共享层侧 = **半开**」）· `docs/DRIFT/global/_README.md:391-392`（双侧活体探针坐实 + Go 真源逐行取证）· `docs/DRIFT/_cross/domain-common-lanes.md:131` · `docs/DRIFT/comment/rpc.md:166`（`D8`）· `docs/DRIFT/like/vs-common.md:174` · `docs/PhaseGoal-judge.md:259`（X-6 行）· `docs/PhaseGoal-judge.md:189` / `:235`（E65 仅出现在「仍开放项」列） |
| **当前状态** | global 台账 **🔴 OPEN**；judge **无 A/B/C/D 定级**、**不在「共享层 28 项」桶里**（那 23 编号 = `DRIFT-COMMON` 0 + communityfact 14 + logger 7 + snowflake 2）；`E66` 在 judge 里**出现 0 次** ⇒ 按 Jason 2026-10-29 裁定 **定为 A 类（阶段一阻断）** |
| **当前事实** | ① **今天实跑** `python3 tools/difftest/check-deadline-downstream.py` ⇒ **EXIT=0**，输出：`Go {failed_fast:true, code:4, outgoing_carrier:present_expired, sql_carrier:present_expired}` ⇔ `RAG {failed_fast:false, code:0, outgoing_carrier:absent, sql_carrier:present_expired}` ⇒ **Go 侧 deadline 到点 ⇒ 出向调用立刻失败（code=4 = DEADLINE_EXCEEDED）；RAG 侧出向调用完全没有 deadline ⇒ 挂住、永不返回** ② ⚠️ **该守门的 PASS 语义是「漂移还在」**（`check-deadline-downstream.py` docstring ③：「drift 键是**已登记的漂移形态** ⇒ 漂移还在 ⇒ 绿；形态变了（**被修好**）⇒ **红**」）⇒ **不能拿它的 PASS 证明 X-6 成立** ③ **X-6 的判据原文**：「下游 deadline = `min(客户端 deadline, 服务端 timeout)`；SQL 取消时刻 == deadline 时刻」⇒ RAG 侧「下游 deadline」不成立 ④ **改造面 = 21 个出向 stub 类 / 30 处零 deadline 调用点**，其中 **11 个类连上界都没有**（`message-rpc/UserRpcClient.java:42` · `message-rpc/AdminRpcClient.java:58`/`:92` · `like-rpc/MessageRpcClient.java:85` · `follow-rpc/UserRpcClient.java:50` · `user-api/UserRpcClient.java:144` · `admin-api/AdminRpcClient.java:174` 等）⇒ Go 侧全部有 **2000ms 硬上界**（`go-zero@v1.10.2` `zrpc/config.go:31` `Timeout int64 json:",default=2000"`）⑤ 根因一句话：**链上有两个互不相通的载体** —— `RpcInterceptor.DeadlineState`（服务端 / rpc 线程）vs `GoStyleRequestDeadline`（出向 stub / api 线程）；`GoStyleRequestDeadline.begin` 在全仓生产代码中**只出现 1 次**（`service/common/.../observability/HttpMiddleware.java:324`）⇒ **rpc 进程无 HTTP 入口 ⇒ 出向 stub 永远看不到入向 deadline** ⑥ 候选修法已探明（**不需要架构级改造、不需要 interrupt**）：共享层在 `onHalfClose` 里把有效期限也绑进 `GoStyleRequestDeadline` ＋ 各域裸 stub 调用点改用 `applyTo` ⑦ ⚠️ **改造面数字被订正过两次**（20/7 → 21/30）⇒ **执行时必须重数** |
| **阶段一影响** | **① 阻断阶段一** —— 命中 `P0 #4`「核心失败路径具有明确行为，不会无声吞掉关键异常」⇒ RAG 侧出向调用**无限期挂起**；且 `P0 #4` 的现有证据（失败路径矩阵 227 条）测的是「端点 × 失败维度」，**不覆盖跨进程 deadline 传播** |
| **所需动作** | ① **重数改造面**（21 stub / 30 处 ⇒ 逐条 `文件:行号` + Go 对照）② 共享层：`RpcInterceptor.onHalfClose` 把有效期限绑进 `GoStyleRequestDeadline` ③ 各域裸 stub 调用点改 `applyTo(stub)` ④ **11 个零上界类补 2000ms 硬上界**（与 Go 的 `default=2000` 同形）⑤ **守门改造**：`check-deadline-downstream.py` 从「漂移形态守门」改为「等值守门」（drift 键两侧必须相等）＋ `mutate-deadline-downstream.py` 变异重做（变异必红）⑥ **双侧活体复跑**（Go 与 RAG 的 `failed_fast` / `code` / `outgoing_carrier` 必须一致） |
| **前置条件** | 无（不依赖 Round 1 的**实现**；仅**验收**需要起栈）⇒ ⚠️ **写权冲突**：`service/common/**` = G4 单写权 ＋ 裸 stub 调用点分属 comment / like / message ⇒ **必须同一次提交**（`docs/DRIFT/global/lanes/_PROTOCOL.md` C1 + `cross-domain-coupling.md:282`） |
| **验证方式** | ① `python3 tools/difftest/check-deadline-downstream.py` ⇒ 改后 **drift 键两侧相等**（不再钉形态）② `python3 tools/difftest/mutate-deadline-downstream.py` ⇒ 变异**精确转红** ③ `bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ EXIT=0 ④ 双侧活体 `tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh` |
| **完成条件** | **两侧 `failed_fast` / `code` / `outgoing_carrier` 逐项相等**（Go 侧为基准）＋ 21 stub / 30 处逐条销号 ＋ 变异必红 ＋ 全量回归 0F/0E |
| **关联项** | `P2-T02`（E66 同轮）· `P3-T01`（P0 #4 行改判）· `P3-T03`（X-6 行改判）· `P4-T04`（C-2/C-3 聚合）· `PIT-GLOBAL-054`（探针必须交 `Δ_start=`/`Δ_end_`，否则 `exit 5`）· `PIT-GLOBAL-040`（变异无效的五种形态）· `PIT-GLOBAL-048`（集合差分，不用关键词匹配） |
| **计划归属** | **Round 2**（`P2-T01` ~ `P2-T06`） |

---

### P2-T02 · E66 —— Redis 调用不绑 deadline

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T02` |
| **原始编号** | `E66` · `DRIFT-ARPC-010`（= E66）· `DRIFT-USERAPI-013` 相邻 · like 域 `FB-05` · task 域 `FB-06` · `comment/rpc.md` `D9` |
| **原始来源** | `docs/DRIFT/global/vs-common.md:31`（🔴 OPEN）· `:47`（路线已探明 + Boot 4 `LettuceConnectionConfiguration` 字节码顺序硬约束）· `docs/DRIFT/global/_README.md:392` · `docs/DRIFT/_cross/domain-common-lanes.md:132`（**两侧同编号 ⇒ 销账别当两条**）· `docs/DRIFT/like/vs-common.md:109-119`（FB-05 · L1 源码级 · 20 处带 ctx vs 11 处无 ctx）· `docs/DRIFT/task/vs-common.md:148`/`:212`（FB-06 · 本域 3 处调用点）· `docs/DRIFT/users/admin/rpc.md:3000`（`DRIFT-ARPC-010`） |
| **当前状态** | 🔴 **OPEN · 本轮转立项**；**无 A/B/C/D 定级**；**judge 里出现 0 次** |
| **当前事实** | ① **RAG 侧 `service/common` 里没有任何 Redis 期限载体**（`DeadlineState` / `GoStyleRequestDeadline` 都不覆盖 Redis）⇒ `comment-rpc/service/CommentCacheService.java` 的全部 `redis.opsForValue()…`（**含写** `:279` `set`）**零 deadline 检查** ② **Go 侧全部 ctx-aware**（`service/comment/rpc/internal/cache/*.go` · `like-rpc` `BizRedis.*Ctx(l.ctx, …)` · `hot-rpc` `RedisClient.ZRevRangeWithScores(l.ctx, …)` · `task-rpc` `Rdb.HGetAll(ctx, …)`）⇒ **到点即止** ③ 与 E65 **同源**（同一个根因：RAG 没有 ctx 传播 ⇒ 非 SQL 的 I/O 全部不感知 deadline）④ ⚠️ **证据强度只有 L1**（源码级逐条对照 · 未经双侧活体）⑤ 处方已探明：Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ **零域侧改动**；⚠️ **硬约束**：Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序 = offset 146 **先**写 `timeoutOptions`（且写的是**硬编码 `TimeoutOptions.enabled()`**，**不是** `spring.data.redis.timeout`）→ offset 151 **才**应用 customizer ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置** ⇒ 归因已订正但**顺序结论不变** ⑥ **两侧同编号 ⇒ 销账别当两条**（`E66` = `DRIFT-ARPC-010`） |
| **阶段一影响** | **② 缺验收证据**（证据只有 L1）⇒ 按 Jason 2026-10-29 裁定：**与 E65 同轮立项，但先补双侧活体证据再定级** |
| **所需动作** | ① **先补双侧活体证据**（探针须落在 `tools/<kind>/<域>/`，须有 `Δ_start=`/`Δ_end_` 自证、变异必红）② 依证据定级（A / B / C）③ 若定 A/B ⇒ 同 E65 轮落地（共享层 Lettuce 期限载体 + 硬约束规避）④ 若定 C ⇒ 登记进交付物 5 暂缓清单（11 字段齐全） |
| **前置条件** | `P2-T01` 的共享层载体已就位（否则 Redis 载体无处挂靠） |
| **验证方式** | 双侧活体探针（Go 侧走真源 Redis 调用，RAG 侧走生产类）＋ 变异必红 ＋ 全量回归 |
| **完成条件** | **有 L4 或明确记为 L1 的定级结论**（`两Stage.md` §十 的 A/B/C/D + 严重程度）＋ 落地或登记，二选一必须写明 |
| **关联项** | `P2-T01` · `P7-T01`（最终验收） |
| **计划归属** | **Round 2**（`P2-T02`） |

---

### P2-T03 · 各域裸 stub 调用点改用 `applyTo(stub)`

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T03` |
| **原始编号** | `E65`（域侧半边）· comment 域 `D8` · like 域 `U15` |
| **原始来源** | `docs/DRIFT/global/vs-common.md:46`（逐条 `文件:行号` 清单）· `docs/DRIFT/global/_README.md:391` · `docs/DRIFT/comment/rpc.md:166` |
| **当前状态** | 🔴 **未做**。已知样例：`comment-rpc/client/ArticleRpcClient.java:65`（裸 stub）· `like-rpc/client/MessageRpcClient.java:85` · `message-rpc/client/UserRpcClient.java:42` · `follow-rpc/client/UserRpcClient.java:50`（注释明说「没有在此处设 deadline」）· `favorite-rpc/client/{UserRpcClient.java:45, ArticleRpcClient.java:50}`（走 `applyTo` ⇒ gRPC 路径 no-op ⇒ **`P2-T02` 修好后自动生效**） |
| **当前事实** | `applyTo` 只在**载体里**有值时才有意义 ⇒ 调用点必须**统一走同一个载体**（不能一半 `applyTo` 一半手搓 `remainingMillis()`）⇒ 否则**两套语义并存**，修一半 = 混进表述差异 |
| **阶段一影响** | ① **阻断阶段一**（A 类 · E65 的域侧半边） |
| **所需动作** | ① 把**所有**出向 stub 调用点**统一**改成 `stub.applyTo(deadlineState)` 形态 ② **清掉手搓 `remainingMillis()` 的那批**（`like-api/LikeRpcClient.java:138` · `comment-api/CommentRpcClient.java:97` · `task-api/TaskRpcClient.java:106` · `article-api/UserRpcClient.java:83`）③ ⚠️ **不要把 `hot-api/HotArticleQueryService.java:102` 算进来** —— 那是**本地查询**读剩余预算，**不是出向 stub** ④ 每处改完补一条断言（**断 deadline 值本身**，不是断「调用了某个方法」） |
| **前置条件** | `P2-T01` ∧ `P2-T02`（载体先就位） |
| **验证方式** | ① 手搓 `remainingMillis()` 命中 **0 条**（或逐条有「不是出向 stub」的说明）② 四域测试 0F/0E ③ 每处改动的**断言变异必红** |
| **完成条件** | 全仓**出向 stub 只有一种 deadline 载体**，且每个调用点有断言 |
| **关联项** | `P2-T04`（同域同批）· `P2-T05`（守门）· `PIT-GLOBAL-040`（变异五形态） |
| **计划归属** | **Round 2** |

---

### P2-T04 · 11 个「零 deadline」类补 2000ms 硬上界

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T04` |
| **原始编号** | `E65`（零上界子集）· `E38②` |
| **原始来源** | `docs/DRIFT/global/vs-common.md:46`（11 类 / 30 调用点清单 + Go 的 `zrpc/config.go:31` 对照）· `docs/DRIFT/global/_README.md:391` |
| **当前状态** | 🔴 **未做**。已点名：`message-rpc/client/UserRpcClient.java:42`（裸注入 stub）· `message-rpc/client/AdminRpcClient.java:58`/`:92` · `like-rpc/client/MessageRpcClient.java:85` · `follow-rpc/client/UserRpcClient.java:50` · `user-api/client/UserRpcClient.java:144` · `admin-api/client/AdminRpcClient.java:174` |
| **当前事实** | ⚠️ 「11 个」这个数**本身被订正过**（原写 9 ⇒ 后订正 11）⇒ 以 `P2-T01` 的重数为准。「零 deadline」比「不继承请求预算」**更严重** —— **连上界都没有** ⇒ 可无限期挂起 ⇒ 与 Go 的 2000ms 硬上界**直接相反** |
| **阶段一影响** | ① **阻断阶段一**（A 类） |
| **所需动作** | ① 对 `P2-T01` 标为「③ 裸 stub / 零上界」的**每一个类**：补一个**默认 2000ms** 的出向上界（与 Go `zrpc/config.go:31` 的 `Timeout int64 json:",default=2000"` 同形）② 上界**可 env 覆盖** ③ 补断言：**即使入向没有 deadline**，出向也必须带 ≤2000ms 的上界 ④ ⚠️ **`devh 3.1.0.RELEASE` 没有 `deadline` 这个属性** ⇒ `grpc.client.*.deadline` 是**空配** ⇒ **不要**靠它配（台账里那 2 处交叉证据已失效） |
| **前置条件** | `P2-T01` |
| **验证方式** | ① 「零上界类数」= **0**（脚本判定）② 每个类有断言断「即使无入向 deadline，出向也带 ≤2000ms」③ 变异必红（把上界去掉 ⇒ 断言红） |
| **完成条件** | **零 deadline 的类 = 0**（本轮最硬的一条量化判据） |
| **关联项** | `P2-T03`（同域同批）· `P2-T05` |
| **计划归属** | **Round 2** |

---

### P2-T05 · 守门改造：漂移形态守门 → 等值守门（+ 变异重做）

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T05` |
| **原始编号** | `E65` · `E48` · `E62`（守门族） |
| **原始来源** | `tools/difftest/check-deadline-downstream.py`（docstring ③ 明写「漂移还在 ⇒ 绿」）· `tools/difftest/mutate-deadline-downstream.py` · `docs/DRIFT/global/vs-common.md`（A1 轴） |
| **当前状态** | 🔴 **未做**。当前门 = **漂移形态守门**（钉住「漂移必须长成这个样子」）⇒ 修好 E65 后它**必然转红** |
| **当前事实** | 实跑 `python3 tools/difftest/check-deadline-downstream.py` ⇒ **EXIT=0** ⇒ 输出 `Go {failed_fast:true, code:4, outgoing_carrier:present_expired, sql_carrier:present_expired}` vs `RAG {failed_fast:false, code:0, outgoing_carrier:absent, sql_carrier:present_expired}` ⇒ **坐实 E65**：Go 到点立刻失败、RAG 出向调用**无 deadline 上界 ⇒ 无限挂住** ⇒ ⚠️ **PASS = 漂移还在** ⇒ **不能拿它的 PASS 证明 X-6 成立** |
| **阶段一影响** | ① **阻断阶段一**（A 类 · X-6 的判定口径） |
| **所需动作** | ① drift 键（`failed_fast` / `code` / `outgoing_carrier`）的判据从「与已登记形态一致」改成「**两侧相等**」；parity 键（`sql_carrier`）保持不变 ② **保留**「两侧都真跑过」的自证（交 `Δ_start=`/`Δ_end_`，不足即 `exit 5`）③ 变异重做：M1 = 去掉 `onHalfClose` 的载体绑定 ⇒ 必红 `outgoing_carrier`；M2 = 给下游调用加 deadline ⇒ 必红 `failed_fast` + `code` ④ ⚠️ **变异判定用「集合差分」不用「关键词匹配」** ⑤ ⚠️ **变异必须打在源码上**（`src/main/**`），不能打 `target/classes` ⑥ ⚠️ **变异脚本自身要能编译**（编译不过 ⇒ 测试没跑 ⇒ 读旧 XML ⇒ **假绿**） |
| **前置条件** | `P2-T02` ∧ `P2-T03` ∧ `P2-T04` |
| **验证方式** | ① 等值门 EXIT=0 ② 变异 **2/2 精确转红**（红的方法集合必须与预期**完全相等**）③ §六 6.3 的命令计数**未变** |
| **完成条件** | **守门与「X-6 成立」同义** ⇒ 漂移复现时红、漂移修好时绿 |
| **关联项** | `P3-T03`（X-6 行改判）· `CT-01`（预期会红 ⇒ 触发条件 ③）· `PIT-GLOBAL-048` · `PIT-GLOBAL-018` |
| **计划归属** | **Round 2** |

---

### P2-T06 · E66：先补双侧活体证据，再定级，再落地或登记

| 字段 | 内容 |
|---|---|
| **计划任务 ID** | `P2-T06` |
| **原始编号** | `E66` · `DRIFT-ARPC-010`（**同一条，不要当两条**）· `DRIFT-USERAPI-013`（相邻）· comment `D9` · like `FB-05` · task `FB-06` |
| **原始来源** | `docs/DRIFT/global/vs-common.md:31`/`:47` · `docs/DRIFT/global/_README.md:392` · `docs/DRIFT/_cross/domain-common-lanes.md:132` · `docs/DRIFT/like/vs-common.md:109-119`（FB-05）· `docs/DRIFT/task/vs-common.md:148`/`:212`（FB-06）· `docs/DRIFT/users/admin/rpc.md:3000` |
| **当前状态** | 🔴 **OPEN · 转立项**；**证据仅 L1**（源码级逐条对照）；**无阶段归属定级**；**judge 里出现 0 次** |
| **当前事实** | ① RAG 侧 `service/common` 里**没有任何 Redis 期限载体** ⇒ `comment-rpc/service/CommentCacheService.java` 的全部 `redis.opsForValue()…`（**含写** `:279` `set`）**零 deadline 检查** ② Go 侧**全部 ctx-aware** ⇒ 到点即止 ③ 与 E65 **同源**（RAG 没有 ctx 传播 ⇒ 非 SQL 的 I/O 全部不感知 deadline）④ 处方已探明：Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ **零域侧改动**；⚠️ **硬约束**：Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序 = offset 146 **先**写 `timeoutOptions`（**硬编码 `TimeoutOptions.enabled()`**，**不是** `spring.data.redis.timeout`）→ offset 151 **才**应用 customizer ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置** |
| **阶段一影响** | **② 缺验收证据** ⇒ 按 Jason 2026-10-29 裁定：**与 E65 同轮立项，但先补双侧活体证据再定级** |
| **所需动作** | ① **先取证**：做双侧活体探针（Go 侧走真源 Redis 调用路径，RAG 侧走**生产类** + 真 Redis）⇒ 落 `tools/<kind>/<域>/`（⚠️ **必须落仓**，`/tmp` 不算证据）② 探针必须**自证跑过**（`Δ_start=`/`Δ_end_`）+ **变异必红** ③ 依证据按 `twoStage.md` §十 定级（A/B/C/D + P0~P3）④ 若定 A/B ⇒ 落地（Lettuce 期限载体 + **规避 Boot 4 字节码顺序硬约束** ⇒ 必须自证「`spring.data.redis.timeout` 仍生效」）⑤ 若定 C ⇒ 登记进交付物 5 暂缓清单（**11 字段齐全**）⑥ **销账注意**：`E66` 与 `DRIFT-ARPC-010` 是**同一条** ⇒ 一条销号，**别当两条** |
| **前置条件** | `P2-T02`（载体已就位 ⇒ Redis 期限才有地方挂） |
| **验证方式** | ① 探针**在版本控制内**（`git ls-files tools/`）② 变异精确转红 ③ **定级结论写进台账**（含 A/B/C/D + 严重程度 + 依据 + 日期）④ 若落地 ⇒ 「运维 timeout 未被覆盖」的断言存在 |
| **完成条件** | **E66 有明确定级结论**，且「落地」或「登记」**二选一必须写明**（不能既不落地也不登记） |
| **关联项** | `P3-T01`（P0 #4 行改判要写 E66 的结论）· `P7-T01` |
| **计划归属** | **Round 2** |

---

## 二、B 类 · 基线复判订正（9 条 → Round 3）

| 计划 ID | 原始编号 | 原始来源 | 当前状态 | 当前事实 | 阶段一影响 | 所需动作 | 前置条件 | 验证方式 | 完成条件 | 关联项 | 计划归属 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `P3-T01` | `P0 #4` | `PhaseGoal-judge.md:374` | ✅ 满足（待改判） | 证据 = 失败路径矩阵 227 条（**不覆盖跨进程 deadline 传播**） | ① 阻断 | 改判为「**部分满足**」并写明 E65 缺口；证据强度分层不变 | `P2-T01` | `check-phasegoal-consistency.py` EXIT=0 | P0 #4 行含 E65 缺口与证据边界 | `P2-T01`·`P4-T04`·`P7-T01` | Round 3 |
| `P3-T02` | `P0 #5` | `PhaseGoal-judge.md:436` | ✅ 满足（待改判） | 证据 = `./manage.sh up` + users 域端点（覆盖 1/10 域） | ① 阻断 | 改判为「**部分满足**」并写明覆盖缺口；待 Round 1 跑通后回填 ✅ | `P1-T01` | 同上 + 真起栈证据 | P0 #5 行的覆盖范围写实 | `P1-T01`·`P7-T01` | Round 3 |
| `P3-T03` | `X-6` | `PhaseGoal-judge.md:259` | ✅ 探针 PASS（待改判） | PASS 语义 = 「漂移形态未变」⇒ 漂移还在 | ① 阻断 | 改判为 🔴/🟡 并注明判定命令的语义边界；待 `P2-T01` 完成后回填 ✅ | `P2-T01` | 同上 | X-6 行的状态与判据一致 | `P2-T01`·`P4-T04` | Round 3 |
| `P3-T04` | `C-2` / `C-3` | `PhaseGoal-judge.md:413-414` | ✅ 满足（待改判） | 依赖 X-6 与 28 项桶 | ① 阻断 | 按 `P3-T03` 结果重算 C-2 / C-3 | `P3-T03` | 同上 | C-2 / C-3 行与 X-6 一致 | `P3-T03` | Round 3 |
| `P3-T05` | `§8.3` | `PhaseGoal-judge.md:417-424` | 「通过」（待改判） | 四结论表的「有条件通过 ⬜ 不适用」行要改 | ① 阻断 | 按 `P3-T04` 结果改四结论 | `P3-T04` | 同上 | 四结论行与聚合一致 | `P3-T04` | Round 3 |
| `P3-T06` | `§8.4` | `PhaseGoal-judge.md:426-454` | ✅ 阶段一通过（待改判） | S1-1~S1-6 六条判据逐条要重判 | ① 阻断 | 改结论 + 逐条重判 S1-1~S1-6 + 补 S1-1/S1-4 的证据边界 | `P3-T05` | 同上 | §8.4 与 §8.3 一致 | `P3-T05` | Round 3 |
| `P3-T07` | `D-02` | `PhaseGoal-judge.md:561` | ✅ 已闭合（**格内仍写「P0 #5 仍为 🟡」⇒ 内部矛盾**） | 同一行里同时有「已闭合」与「仍为 🟡」 | ⑤ 仅执行约束（文档卫生） | 订正该格的过期断言 | 无 | `check-ledger-selfcheck.py` | 该格无自相矛盾表述 | `P3-T02` | Round 3 |
| `P3-T08` | `§十` / `§十·二` | `PhaseGoal-judge.md:488-551` | 变更记录 34 条 / 冻结基线 9 项 | 冻结基线（2026-10-29）建立在「通过」结论上 | ① 阻断 | 追加 v5 变更记录（两条 A 类阻断的发现与定级）＋ **解除** 冻结并写明解除原因与日期 | `P3-T06` | `check-phasegoal-consistency.py` | 变更记录与结论同步；冻结状态明确 | `P3-T06`·`P7-T01` | Round 3 |
| `P3-T09` | 生成器 + 交付物 2/6 | `tools/difftest/check-phasegoal-lanes.py` · `docs/Stage1/02-*.md:111` · `docs/Stage1/06-*.md:169` | 生成器 4 份矩阵与基线一致；**Stage1 交付物 2/6 仍写「✅ 阶段一通过」** | 交付物 2/6 是权威基线的下游产物 | ① 阻断 | 改基线 ⇒ 重跑生成器 `--write` + 校验；订正 Stage1 交付物 2 与 6 的结论句 | `P3-T06` | `check-phasegoal-lanes.py` EXIT=0 + `check-phasegoal-consistency.py` EXIT=0 + `check-doc-links.py` EXIT=0 | 4 份 lane 矩阵与基线一致；交付物 2/6 与 §8.4 一致 | `P3-T06` | Round 3 |

---

## 三、C/D 类 · 残留清零（9 条 → Round 4）

| 计划 ID | 原始编号 | 原始来源 | 当前状态 | 当前事实 | 阶段一影响 | 所需动作 | 前置条件 | 验证方式 | 完成条件 | 关联项 | 计划归属 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `P4-T01` | `R5-F1` · `S1-USERS-002` | `docs/RoundPlan/Round-5.md:51` · `docs/DRIFT/users/stage1-admission.md:107` | 🟡 B 类 · 已登记未修 | **10 处** `${POSTGRES_PASSWORD:password}` × 9 ＋ `${MINIO_SECRET_KEY:minioadmin}` × 1；落点在**各域自己的 rpc 模块** ⇒ **域侧可自修**（原写「归 G4」是错的） | ④ 允许暂缓（B 类 ⇒ 不计入） | 各域取消 datasource 口令 fallback（改无默认值的 `${POSTGRES_PASSWORD}`，与 Go 的 `os.ExpandEnv` 空口令 fail-fast 同形） | 无（域内可自修） | `grep -rnE '\$\{POSTGRES_PASSWORD:[^}]' service/` ⇒ **0 命中**（排除 `target/`）＋ 各模块 `mvn test` 0F/0E | 10 处逐处销号 | `P4-T02` | Round 4 |
| `P4-T02` | `R5-F2` · `S1-USERS-002` | `docs/RoundPlan/Round-5.md:52` | 同上（限 `admin-rpc`） | `user-rpc` **无 `application.yml`** ⇒ 本就不在清单 | ④ 允许暂缓 | 同 `P4-T01`，只限 `service/users/admin/admin-rpc/` | 无 | 同上（限 `service/users/**`） | 同上 | `P4-T01` | Round 4 |
| `P4-T03` | `R5-F7` · `DRIFT-CF-018` | `docs/RoundPlan/Round-5.md:57` · `docs/DRIFT/global/vs-common.md`（共享层半边已修） | 🔴 C 类 · 已派单未执行 | `FavoriteFactDispatchScheduler.java:119` **仍调阻塞版 `Dispatcher.run(...)`** ⇒ `once=false` 时**句柄永远拿不到** ⇒ `@PreDestroy` 拿到 null ⇒ **进程无法优雅关闭** | ④ 允许暂缓（不阻 P0 #1） | 改用 `Dispatcher.runAsync(…)`（与 `CommentFactDispatchScheduler.java:93` 对齐） | 无 | 优雅关闭测试：开 `favorite.fact.dispatch.enabled=true` ⇒ 发起关闭 ⇒ 断言**进程在 N 秒内退出**且 `@PreDestroy` 拿到**非 null** handle ＋ `mvn -pl service/favorite/favorite-rpc -am test` 0F/0E | 进程可优雅关闭 | ⚠️ `favorite` 域对 `global` 零写权 ⇒ 域侧自修 + 共享层变更需记账 | Round 4 |
| `P4-T04` | `R5-F9` | `docs/RoundPlan/Round-5.md:59` · `docs/DRIFT/task/rpc.md:29` vs `:1633`/`:1672` | 🔴 D 类 · **机器可判** | **同一台账两个权威数**：`task/rpc.md:29`（标题表）写 **134**，同文件 `:1633`/`:1672` 写 **137**（2026-10-29 G2 收工轮 `clean test` 实测）且明写「**134 是【推算】不是实测**」⇒ 跨文件亦矛盾（`task/vs-common.md:1433` 的 134） | ④ 允许暂缓（文档卫生） | ① 标题表改 137 ② `:1535` 的 `--all` 口径表违规（**裸跑只查 users 域**）改成「裸跑 = users 域 · `--indexes`/`--check-source` 逐域」③ `task/vs-common.md:1525` 引用的「task-api 有 102 条」已过期（`task/api.md:30` 现值 **135**） | 无 | `python3 tools/check-ledger-selfcheck.py` ⇒ EXIT=0 | 该守门由红转绿 | ⚠️ **task 域写权面 ⇒ 不越界改** | Round 4 |
| `P4-T05` | `R5-F10` | `docs/RoundPlan/Round-5.md:60` · `service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh:127` | 🟡 D 类 · 已派单未执行 | 裸 `$JAVA_MOD）` ⇒ macOS bash 3.2 把紧跟的全角右括号算进变量名 ⇒ `set -uo pipefail` ⇒ **收尾行崩**（工作做完但退出码非 0） | ④ 允许暂缓（工具面） | 改 `${JAVA_MOD}` / `${JAVA_GATE}` | 无 | `python3 tools/difftest/scan-bash-legacy-vars.py` ⇒ EXIT=0 ＋ 探针本体跑通退出码 0 | 全仓 0 处活代码命中 | ⚠️ **hot 域写权面 ⇒ 不越界改** | Round 4 |
| `P4-T06` | `R5-F3` · `DRIFT-USERS-LAT-001` | `docs/RoundPlan/Round-5.md:53` · `docs/DRIFT/users/_shared.md:1278` | 🟡 P3 / D 类 · 已登记 | RAG 侧 HTTP 响应延迟被共享层「500ms 切片等待」量化（Go `max=319ms` vs RAG `max=2046ms`；中位数 50 vs 51ms 几乎相同 ⇒ **两侧调用链层数相同**，不是跳数差） | ④ 允许暂缓 | **登记 + 写解除条件**（不落码）：空载复跑 `unexpected=0` ⇒ 纯负载敏感；仍超时 ⇒ 才是真差异 | 无 | 台账有「现象 + 证据强度 + 解除条件」三项 | 台账登记完成 | ⚠️ **G4 写权面** | Round 4 |
| `P4-T07` | `R5-F6` · `G-12` 遗留 15 处 | `docs/RoundPlan/Round-5.md:56` | 🟡 D 类 · 已派单未执行 | 15 处「原引用本就指错」（基线行本身是空行/重复表头）⇒ **早于 G-12 就指错**，不是 G-12 引入的回归 | ④ 允许暂缓 | 逐条用**上下文关键词**定位作者真正想指的行（**内容锚定，不是猜**） | 无 | 每处的目标行**逐条有依据**（能说出为什么是这一行） | 15 处逐条销号 | ⚠️ 各域台账 = 域写权面 | Round 4 |
| `P4-T08` | `R5-F5` · `PIT-GLOBAL-068` | `docs/RoundPlan/Round-5.md:55` | 🟡 D 类 · 已派单未执行 | 共享 Nacos 服务发现面让**并发探针互相污染** ⇒ 报 `INTERNAL`/`5001` 是**已知串扰**，不是缺陷 | ④ 允许暂缓（工具面） | 探针的 **Nacos 隔离**（同一份探针并发跑时不再互报 `INTERNAL`/`5001`） | 无 | 并发跑两份探针 ⇒ 互不污染 | 隔离生效 | `tools/api-full-parity-probe/article/**` = 域内探针目录 | Round 4 |
| `P4-T09` | `D-08` | `docs/PhaseGoal-judge.md:567`（§十一 11.1「真实实现缺陷」类）· `docs/DRIFT/like/rpc.md:216-238`（8 条错误断言 A~H 的完整清单 + 每条实测事实与出处）· `docs/DRIFT/like/rpc.md:891`（「仍然全部在位」）· `docs/DRIFT/like/rpc.md:856`（**第 9 条错误断言 I**） | 🔴 **未做** · D 类 | like 域源码注释里的 **9 条假断言**（A~H + I）全部在位：A「GORM 扫 `created_at` 取 `data.Unix()`」⇒ **GORM 根本没匹配上该列**、`timestamp` 恒 0 ⇒ 描述的是**不可达路径**；B「map 形式不带 `updated_at`」⇒ **会带**（真 PG 实测）；C/G「`Push` 不带 key」⇒ **带**（内部即 `PushWithKey`）；D「Go `Push` 同步阻塞」⇒ `ChunkExecutor` **异步缓冲**；E「不做进程内重试」⇒ `DefaultErrorHandler` **内部就是 `FixedBackOff(0,9)`**；F「五个装配点都有 wiring 测试」⇒ **4 个测试类一个都不存在**、模块**零** Spring 上下文测试；H「etcd 注册失败会 panic」⇒ **未核实**标 UNKNOWN；I「GORM 对无 size 的整数统一取 bigint」⇒ `DataTypeOf` 对 `int32` 返回 **`integer`** | ④ 允许暂缓（**文档性缺陷** · 不破坏核心正确性/安全/数据完整性）⇒ 按 §1.1 ⑥ 第 4 条**不计入** | 逐条**改写或删除** A~H 的注释（改成**实测事实**）；H 要么补核实、要么**删掉**（**不许**留 UNKNOWN 的断言在源码里）；I 一并处理；⚠️ **只改注释，不改行为** | 无（可与 `P4-T01`~`P4-T08` 并行） | ① 9 条逐条有「已改写 / 已删除 / 已补核实」结论 ② **行为面 diff 为空**（`git diff` 无非注释行）③ `mvn -pl service/like/like-rpc -am test` 0F/0E | **9 条断言全部处置** ＋ **行为面零变化** ＋ 台账销号（**保留原文**） | `service/like/**` = G2（like 域 lane）· `docs/DRIFT/like/**` = G2 | Round 4 |

---

## 四、D 类 · 取证与台账对齐（6 条 → Round 5）

| 计划 ID | 原始编号 | 原始来源 | 当前状态 | 当前事实 | 阶段一影响 | 所需动作 | 前置条件 | 验证方式 | 完成条件 | 关联项 | 计划归属 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `P5-T01` | `R5-F4` · `G-19` | `docs/RoundPlan/Round-5.md:54` · `PhaseGoal-judge.md:483` | 🟡 取证缺口 · 不降 R | proto 的「**消费方式**」未取证（`optional` presence / 代码里的字段号硬编码 / Jackson 配置）⇒ `docs/DRIFT/_cross/proto-verification.md:40-46` 的「三档回归网」**只覆盖文件层** ⇒ **10 个 proto 上全部成立** | ② 缺验收证据 | 逐 proto 核三问 ⇒ 每条给「是/否 + 证据（`文件:行号`）」⇒ **无「未知」** | `P3-T06`（知道复判后哪些要取证） | 各域台账 + `docs/DRIFT/_cross/proto-verification.md` | 10 个 proto 逐个有结论 | `G-19` | Round 5 |
| `P5-T02` | 原文无独立编号（各域台账冲突对） | `docs/DRIFT/like/api.md:468` vs `like/vs-common.md:35` · `message/api.md:912` vs `message/vs-common.md:38` · `message/api.md:1528` vs `message/vs-common.md:40` · `users/stage1-admission.md:91`（`S1-USERS-001`）与 `:123`（`S1-USERS-003`） | 🔴 台账**内部矛盾** | **各域「明细面（api.md/rpc.md）」与「现状面（vs-common.md）」大面积不同步**：明细面标 🔴 OPEN，现状面标 ✅ 已修；`S1-USERS-001`（README 7 字节 ⇒ A 类阻断）早已被 R1 修掉（现 9,815 B）仍标 🔴 OPEN；`S1-USERS-003`（`.env` 不在 `.gitignore`）早已修好（`.gitignore:46` 有 `.env`）仍标 🔴 OPEN | ⑤ 仅执行约束（台账质量）**但直接影响下一轮复判的输入** | 逐条对账：**以实测为准**回填状态，并给每条改写标注「核对日期 + 依据」 | `P3-T06` | `check-ledger-selfcheck.py` + `check-ledger-tables.py` + 逐条人工核 | 每个冲突对**收敛到单一状态**且带日期 | ⚠️ **各域台账 = 域写权面 ⇒ 各域 lane 自修** | Round 5 |
| `P5-T03` | 原文无独立编号（编号体系冲突） | `docs/DRIFT/global/cross-domain.md:21-29`（X-1~X-10）vs `PhaseGoal-judge.md` §五（X-1~X-12） | 🔴 编号不可寻址 | **两套 `X-` 编号语义不同**：X-2 / X-5 / X-7 在两套里指向**完全不同的不变量** ⇒ 「查 X-5」不可寻址 | ⑤ 仅执行约束 | 把 `global/cross-domain.md` 的 X-1~X-10 **重编号为 `CD-1~CD-10`**（保留原 X-n 对照列）⇒ 权威源 = `PhaseGoal-judge.md` §五 | `P3-T06` | `check-doc-links.py` + 引用点逐条改 | 全仓无「裸 X-n 引用」指向 `cross-domain.md` | ⚠️ **`docs/DRIFT/global/**` = G4 写权面** | Round 5 |
| `P5-T04` | `DRIFT-HOTAPI-030` | `docs/DRIFT/_cross/open-items.md:15`（🔴 未修·待拍板）· `docs/DRIFT/global/_README.md:134`（🟡 未修·可修）· `docs/DRIFT/global/cross-domain.md:26`（✅ 第十四轮已落地）· `AGENTS.md:142`（✅ 已落地） | 🔴 **同一格三处三态** | 权威态 = **✅ 已落地**（共享层修法 · favorite 零触碰 · 真容器 + 真 MVC + 真 advice + 变异测试）⇒ `open-items.md` 与 `_README.md:134` 是 **10-25 的陈旧快照** | ⑤ 仅执行约束 | 把 `open-items.md:15` 与 `_README.md:134` 对齐到权威态，并保留订正时间线指针 | `P3-T06` | `check-ledger-selfcheck.py` | 三处状态一致 | ⚠️ `docs/DRIFT/_cross/**` + `global/**` = 主线/G4 | Round 5 |
| `P5-T05` | `PIT-*`（56+ 条 global + 各域） | `docs/Pitfalls/**` | 🔴 **状态字段普遍缺失** | 抽取显示大量 Pitfall「**文件未标状态**」⇒ 无法区分「仍生效」与「已闭环」⇒ 按规范 §2.3「已解决的历史问题应作为防错约束」仍可当约束用，但**台账不可审计** | ⑤ 仅执行约束 | 给每条 Pitfall 补「状态 + 核对日期 + 是否仍影响当前轮次」三字段 | `P3-T06` | `python3 tools/check-ledger-count.py`（计数口径） | 每条有状态；计数与索引一致 | ⚠️ **各域 Pitfalls = 域写权面；global = G4** | Round 5 |
| `P5-T06` | 原文无独立编号（写死数字清单） | 见 `_cross/cross-domain-coupling.md:139-147`（2411）· `global/_README.md:318/338/347/348/383/886-888`（2263/2301/2305/2303/2305/2310/2314）· `component-coverage-matrix.md:78`（28）· `_INDEX.md:29`（23+5=28）· `ddl-type-divergences.md:178`（51）· `cross-domain-coupling.md:224`（315 vs 335） | 🟡 漂移源 | 台账里**几十处写死数字**（回归条数 / 未修条数 / DDL 残留数 / 组件数），任何一轮改动都会让它们漂移 ⇒ 已是 B14 类问题的温床 | ⑤ 仅执行约束 | 产出一份**「写死数字清单」**（文件:行号 + 数字 + 它统计什么 + 谁负责维护），并在改动这些数字的任务里写明「同次必须订正哪几处」 | `P3-T06` | 人工核对 + `check-ledger-count.py` | 清单成文并被后续任务引用 | 主线 | Round 5 |

---

## 五、收口回归与最终验收（2 条）

| 计划 ID | 原始编号 | 原始来源 | 当前状态 | 当前事实 | 阶段一影响 | 所需动作 | 前置条件 | 验证方式 | 完成条件 | 关联项 | 计划归属 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `P6-T01` | 收口回归 | `PhaseGoal-judge.md` §六 6.3（27 项命令）· `docs/RoundPlan/_legacy-index.md` §一 复算命令 | 上次 26 绿 1 红 | 上次实跑：`mvn -o -B -DargLine=-Xmx2g clean verify` ⇒ **3,294 条 / 0F / 0E / 4S / 52 模块 / 28:40 min**；27 守门 **26 绿 1 红**（红 = `check-ledger-selfcheck.py` ⇒ task 台账 134 vs 137 ⇒ `P4-T04`） | ① 阻断（是验收的前置） | 全量回归 + **27 个守门逐个取真实退出码** + 10 域双侧探针 + 生成器 `--write` + 校验 | `P1-T01` ∧ `P2-T01` ∧ Round 4 ∧ Round 5 全部完成 | 见 §十二 的 6 组交叉检查 | **全部 EXIT=0**（或红项已按 `P4-T04` 等销号） | `P7-T01` | Round 6 |
| `P7-T01` | 阶段一最终验收 + 重新冻结基线 | `PhaseGoal-judge.md` §八 8.3/8.4 · §十·二（`twoStage.md` §5.7 的 9 项）· §十一 D-01~D-08 | 上次「✅ 通过」 | 判据：P0 九条 + C-1~C-4 + 12 条跨组件不变量 + 交付物 1~6 + 冻结基线 9 项 | ① 阻断（终点判定） | 逐条核对判据（**保留原始条目与判定口径，不另造标准**）⇒ 出结论；若不通过 ⇒ 回条件触发轮 | `P6-T01` 全绿 | 见 `_final-acceptance.md` | **全部必要准入条件满足 ⇒ 宣布阶段一通过 ⇒ 立即冻结基线 ⇒ 计划结束（不进阶段二）** | 全部 | Round 7 |

---

## 六、条件触发机制（不是正常轮次）

| 编号 | 触发条件 | 处置范围 | 重新验收 |
|---|---|---|---|
| **CT-01** | ① 任一守门返回非 0 且**不是**已知假红 ② Round 1 的起栈出现**新**启动失败 ③ `P2-T01` 落地后 `check-deadline-downstream.py` 因「漂移被修好」而转红（**预期会红 ⇒ 必须重写门禁，不是回归**） | **只处理实际阻断验收的事项及其必要依赖**；不做范围外清理 | 重跑受影响的验收 + `P6-T01` 的收口回归 |
| **CT-02** | 复判过程中发现**此前未登记**的问题 | 按 `twoStage.md` §十 定级 + 记录「来源 / 证据 / 影响的判据 / 所需决策」⇒ **不得直接升级为新的强制整改任务** | 视影响面重跑对应验收 |

> ⚠️ **条件触发轮不是兜底轮**：它不预装任务清单，只有触发时才生成；生成后仍然受「只处理实际阻断项」约束。

---

## 七、覆盖率自检（规范 §12.2 的可追溯性）

| 来源 | 条目量 | 去向 |
|---|---|---|
| 旧 RoundPlan 编号任务 | **40 条**（`T-0.1`~`T-0.7` / `T-1.1`~`T-1.7` / `T-2.1`~`T-2.11` / `T-3.1`~`T-3.7` / `T-4.1`~`T-4.8`） | 逐条进 [`_migration.md`](_migration.md) |
| 旧 R5 欠账 + 派工单 | **8 + 10**（`R5-1`~`R5-8` / `R5-F1`~`R5-F10`） | `R5-F1`~`R5-F7`/`F9`/`F10` ⇒ `P4-T01`~`P4-T08`；`R5-F8` ⇒ 已完成（历史）；`R5-1`~`R5-6` ⇒ 归入对应 `P4-*` 或已由 `P1`/`P2` 覆盖 |
| **judge §十一 D-01~D-08** | **8 条** | `D-08` ⇒ `P4-T09`；`D-01`/`D-04` ⇒ 已闭合；`D-02` 残留 ⇒ `P1-T04`；`D-03` ⇒ `DEC-01`（待裁定）；`D-05` ⇒ `DEC-03`（阶段二）；`D-06` ⇒ `P4-T06` + 共享层 28 项逐条定级；`D-07` ⇒ 已签字 `SD-2026-10-24-CAND-02` ⇒ **不得再修** |
| 跨组件不变量 | **12 条**（`X-1`~`X-12`） | `X-5`/`X-6`/`X-12` ⇒ `P1-T01`/`P2-T01`/`P3-*`；其余 9 条 ⇒ 已成立（复判时逐条复核） |
| 证据缺口 | **20 条**（`G-1`~`G-20`） | `G-19` ⇒ `P5-T01`（+ `P2-T05` 守门改造）· `G-17` ⇒ **记录缺口**（`like-common` 专属台账）⇒ 登记在 `_unresolved.md` 第 2 类 `GAP-02` ⇒ **阶段一之外**；其余 18 条 ⇒ 已闭合（历史） |
| Intentional_Deviations | **48 条已签字 + 18 条未签字候选** | 已签字 ⇒ **保护项**（写进各轮「不得顺手修回 Go 行为」约束）；18 条候选 ⇒ **不升级拍板**，登记在 `_unresolved.md` 的第 4 类 |
| Pitfalls | **56+ 条 global + 各域** | 逐条转成**执行防错约束**，见 [`_dependencies.md`](_dependencies.md) **§4.0 附表**（各轮 PIT → 任务 ID 对账）与各轮「防错约束」小节 |
| DDL 残留 | **51 处**（C 23 / D 28 / A 0） | 不计入阶段一（§1.1 ⑥ 第 4 条）⇒ 登记在 `_unresolved.md` 第 5 类 |
