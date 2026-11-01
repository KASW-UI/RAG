# Round 1 · 全服务起栈（闭合 P0 #5）

> **本轮目标**：把「**项目**能够按照实际项目说明完成构建、配置和启动」从 **1/10 域、2/20 个服务** 变成 **10 域、20 个服务全覆盖**。
> **本轮不执行整改**（规范 §一.6 / §一.7）—— 本文件是**计划**，不是执行记录。

---

## §5.1 本轮目标与结束状态

| 项 | 内容 |
|---|---|
| **处理哪些已知问题** | ① P0 #5 的**覆盖缺口**（判据主语是「项目」，证据只覆盖 users 域）② `.env` / `.env.example` 的中间件地址是**容器内 DNS 名**（宿主机不可解析）③ 端口**互相撞车**（`ADMIN_API_PORT` = `ARTICLE_API_PORT` = 8889；favorite-rpc / like-rpc / message-rpc **三处硬编码 8082**；**article-rpc 与 comment-rpc 都硬编码 9001**；**like-api 硬编码 18887** 且 `.env` 无此键）④ README §三 与 `.env` **自相矛盾**（`.env.example:88` 注释自己写着「宿主机跑用 127.0.0.1」，README §三 的命令却直接吃容器内值）⑤ `.env.example:2` 标题仍写旧项目名 `Sea-RideTheWind_java` |
| **产生哪些可检查的结果** | ① 一份**起栈依赖表**（20 个服务 × 地址/端口/中间件/就绪判据，逐条落盘）② 一套**宿主机可解析**的地址与**零冲突**的端口表 ③ README §三 被改成**第三方照做得起** ④ **20 个服务逐个「在服务」**（Nacos 注册 + 端口监听 + 健康端点 200，三条同时满足）⑤ 至少一条 **rpc→rpc** 与一条 **api→rpc→PG** 链路打通 |
| **为什么是下一轮的必要前提** | ① `Round 2` 的 **E65 验收**必须在真栈上跑双侧活体（否则「两侧都挂住」测不出来）② `Round 3` 的 P0 #5 行改判要有活体证据才能写 ✅ ③ `Round 6` 的收口回归要复算这条链路 ④ `Round 7` 的最终验收的 P0 #5 判据靠它 |
| **本轮不做** | 不改 Go 真源（冻结只读）· 不动共享层 `service/common/**`（那是 G4 的面，且属于 Round 2）· 不做阶段二任何事 · 不改 `PhaseGoal-judge.md`（那是 Round 3 的面） |

---

## §5.2 开始条件

| 条件 | 状态 | 证据 / 解除方式 |
|---|---|---|
| **RAG 栈在跑**（postgres / redis / nacos / jaeger / kafka / minio / etcd 可用） | ✅ **已满足** | `Sea-postgres` 49432 · `Sea-redis` 49380 · `Sea-nacos` 49848 · `Sea-jaeger` 49317 · `Sea-kafka` 49992 · `Sea-minio` 49100 · `Sea-etcd` 49319 —— **均为兄弟仓 `sea-ridethewind_java` 项目遗留的同名容器**（RAG 的 compose 只编排 users 域 7 个服务）⇒ ⚠️ **两个仓的 compose 容器名 + 端口 100% 冲突，本来就互斥** |
| **20 个可执行 jar 已构建** | ✅ **已满足** | `ls service/*/*/target/*.jar` ⇒ 32 个（20 个 api/rpc 可执行 + 10 个 proto + 2 个 common）· `bash tools/difftest/check-jar-executable.sh` ⇒ **20/20** |
| **构建产物存在且是当前 HEAD** | ⚠️ **需确认** | `bash tools/difftest/check-jar-executable.sh`（未构建即判红 · 永远打印「已检查 N/M」）⇒ **先跑一次**；若红 ⇒ 先 `mvn -o -B -DskipTests clean package` |
| **端口空闲** | ⚠️ **需确认** | 逐个 `lsof -nP -iTCP:<port> -sTCP:LISTEN`；⚠️ **容器 `docker rm -f` 后宿主口仍可能被 docker-proxy 占住** ⇒ 必须**轮询到空**才起下一个（`PIT-GLOBAL-038`） |
| **无前置任务** | ✅ | 本轮是计划的第一轮 |

> ⚠️ **若前置不满足**：本轮**不得开始**（`P1-T03` 要改的端口表依赖「当前谁占着哪个口」的事实）。记录方式：写进本轮的**结束检查**「未完成任务及原因」。

---

## §5.3 逐项任务清单

### P1-T01 · 产出「20 个服务起栈依赖表」

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P1-T01` |
| **原始编号** | `P0 #5` · `X-12`（`PhaseGoal-judge.md` §五） |
| **来源** | `docs/PhasedGoal.md:46` · `docs/PhaseGoal-judge.md:259`/`:436` · `README.md:127-147` · `docker-compose.yaml`（root）· 20 个 `service/<域>/<域>-{api,rpc}/src/main/resources/application.yml` |
| **当前状态** | ✅ **已完成**（2026-11-01 · 落码提交 `1fa8a080`）—— 落点 `docs/RoundPlan/round-01/service-startup-matrix.md`：**20 行 × 9 项**依赖表（每行带 `文件:行号`）＋ 硬编码端口小表 ＋ 三判据表 ＋ 依赖图 ＋ §5 宿主机地址对照 ＋ §7 实测结果 |
| **执行目的** | 端口与地址是**跨 20 个模块的事实**，散在 20 个 yaml + `.env` + README 四处 ⇒ 不先收成一张表，端口冲突只能靠撞出来 |
| **具体操作** | ① 逐个读 20 个模块的 `application.yml`，抽出：HTTP 端口、gRPC 端口、management 端口、**Nacos 地址**、**PG 地址**、**Redis 地址**、**Kafka brokers**、**MinIO endpoint**、**OTEL endpoint**、下游 `discovery://` 目标 ② 每个值标注 **可 env 覆盖 / 硬编码** ③ 标出**下游依赖**（A 依赖 B ⇒ B 必须先起）④ 落成一张表（落点见下）⑤ **每个服务写明「在服务」的三个判据**：Nacos 服务名 + 监听端口 + 健康端点路径 |
| **具体落点** | 新建 `docs/RoundPlan/round-01/service-startup-matrix.md`（**本轮内新建**；⚠️ `docs/RoundPlan/**` = 收口执行者写权面） |
| **执行主体** | **合并收口执行者（主线）** |
| **前置条件** | 无 |
| **并行关系** | 与 `P1-T02`/`P1-T03` **不可并行**（后两者要改的东西正是本表列的）⇒ 本任务**必须先完成** |
| **写权限** | 只写 `docs/RoundPlan/round-01/**`（主线面）⇒ **不碰**任何 `service/**` 与各域台账 |
| **验收命令** | ```bash# 表内每行必须能被下面这条命令验证「端口真的存在」\nfor p in $(grep -oE '^\| [a-z-]+-(api\|rpc) \| [0-9]+' docs/RoundPlan/round-01/service-startup-matrix.md | awk '{print $NF}' | sort -u); do\n  grep -rn "port: $p" service/*/*/src/main/resources/application.yml | head -1\ndone``` |
| **验收证据** | ① 20 个服务**逐个一行**（HTTP 端口 / gRPC 端口 / Nacos 名 / PG / Redis / Kafka / MinIO / OTEL / 三个就绪判据 / 下游依赖 / env 可覆盖性）② 每一行都有 `文件:行号` 出处 ③ 「硬编码端口」单列成一张小表（**这就是 `P1-T03` 的工作单**） |
| **完成条件** | 表内 **20 行齐全**，每行 9 项无空缺，且「硬编码端口」小表 ≥ 4 行（favorite-rpc 8082 / like-rpc 8082 / message-rpc 8082 / article-rpc 9001 / comment-rpc 9001 / like-api 18887） |
| **失败处理** | 某个模块的 `application.yml` 找不到端口 ⇒ 记「**未声明端口 ⇒ 走默认值**」并去 `application.yml` 全文找默认；仍找不到 ⇒ 该服务标「**启动依赖待查**」并**阻塞 `P1-T05` 对该服务的验收**，但**不阻塞其余 19 个** |
| **状态更新** | 无（不改台账；本表是计划产物） |
| **后续任务** | `P1-T02`（地址）、`P1-T03`（端口）、`P1-T05`（真起） |

### P1-T02 · 提供宿主机可解析的中间件地址（容器内值与宿主机值两套）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P1-T02` |
| **原始编号** | `P0 #5`（原文无独立编号 ⇒ 见 `P1-T01`） |
| **来源** | `.env.example:88-114`（`POSTGRES_HOST` / `POSTGRES_ADDR` / `REDIS_ADDR` / `REDIS_HOST` / `NACOS_ADDR` / `NACOS_PORT` / `KAFKA_BROKERS` / `MINIO_ENDPOINT` / `MINIO_PUBLIC_BASE_URL` / `OTEL_ADDR` / `OTEL_EXPORTER_OTLP_ENDPOINT` / `OTLP_PORT`）· `README.md:136` · `docker-compose.yaml` |
| **当前状态** | ✅ **已完成**（2026-11-01 · 落码提交 `1fa8a080`）—— 新建 **`.env.host.example`**（宿主机语义 `127.0.0.1` + 宿主映射端口）⇒ `.env.example`（容器内语义）**一字未改** ⇒ `docker compose config` **EXIT=0** · 缺变量 ⇒ **EXIT=1**（fail-closed 未破） |
| **执行目的** | P0 #5 的「实际项目说明」必须给出**真能跑**的地址；compose 用的容器内名**不能被改**（改了 compose 就崩） |
| **具体操作** | ① 选一种结构并写进 `README.md`（**推荐 A**；执行者可另定，只要满足完成条件）：<br>**A（推荐）· 两份模板** ⇒ 新增 `.env.host.example`（**宿主机版**：地址全 `127.0.0.1` + 宿主映射端口）；`.env.example` 保持容器内值**一字不改** ⇒ compose 零风险<br>**B · 单模板双段** ⇒ `.env.example` 里加 `HOST_POSTGRES_HOST=127.0.0.1` 一族，compose 仍读 `POSTGRES_HOST` ⇒ 宿主机起 jar 时 `export HOST_POSTGRES_HOST=…; export POSTGRES_HOST=$HOST_POSTGRES_HOST`<br>**C · 单模板改值** ⇒ `.env.example` 改宿主机值 + compose 里改用 `${POSTGRES_HOST_INTERNAL:-postgres}` 一族 ⇒ ⚠️ **改动面最大**，compose 要动 ⇒ 不推荐<br>② 逐个变量给出**宿主机值**：`NACOS_ADDR=127.0.0.1` `NACOS_PORT=49848` `POSTGRES_HOST=127.0.0.1` `POSTGRES_PORT=49432` `POSTGRES_ADDR=127.0.0.1` `REDIS_HOST=127.0.0.1` `REDIS_ADDR=127.0.0.1` `REDIS_PORT=49380` `KAFKA_BROKERS=127.0.0.1:49992` `MINIO_ENDPOINT=127.0.0.1:49100` `OTEL_ADDR=127.0.0.1` `OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:49317:4317` `OTLP_PORT=4317`<br>③ ⚠️ **OTEL 的 URL 形态要验**：实测 `http://127.0.0.1:49317:4317` ⇒ `Invalid URL port: "49317:4317"` ⇒ **OTEL 导出失败但不阻断启动**（日志明写「trace 导出不可用（服务照常启动）」）⇒ 正确形态取 `http://127.0.0.1:4317`（**用宿主映射端口 49317 ⇒ 容器内 4317**）<br>④ README §三 的命令改成「compose 用 `.env`；宿主机起 jar 用 `.env.host`」两段 |
| **具体落点** | `.env.host.example`（**新建**）· `.env.example`（**仅按方案 A/B 需要时改注释**；⚠️ `.env` 本身**已 gitignore**，不进版本控制）· `README.md` §二/§三 |
| **执行主体** | **合并收口执行者（主线）** —— 根级文件**无人拥有**（`AGENTS.md` §5.3b 口径） |
| **前置条件** | `P1-T01` |
| **并行关系** | 与 `P1-T03` **可并行**（一个改地址、一个改端口；`.env` 变量名不重叠 ⇒ **但同文件 `.env` ⇒ 须分两次提交或同一次提交内分区块**） |
| **写权限** | 根级 `.env.example` / `.env.host.example` / `README.md` = **主线**；⚠️ **不得改 `docker-compose.yaml`**（除方案 C，且方案 C 不推荐） |
| **验收命令** | ```bash# ① 宿主机模板的地址必须全部可解析（不允许出现容器内 DNS 名）\ngrep -nE '^(NACOS_ADDR|POSTGRES_HOST|POSTGRES_ADDR|REDIS_HOST|REDIS_ADDR|KAFKA_BROKERS|MINIO_ENDPOINT|OTEL_ADDR|OTEL_EXPORTER_OTLP_ENDPOINT)=' .env.host.example \\\n  | grep -vE '=(127\\.0\\.1|localhost)(:[0-9]+)?$' && echo "❌ 仍有非本机地址" || echo "✅ 全部本机"\n# ② compose 的容器内语义未被破坏\ndocker compose config >/dev/null && echo "compose OK"\n# ③ compose 缺变量仍然拒绝渲染（fail-closed 不能被这次改动弄坏）\nenv -u USER_JWT_SECRET docker compose config >/dev/null 2>&1; echo "缺变量 EXIT=$? （期望非 0）"``` |
| **验收证据** | ① 宿主机模板里**零个**容器内 DNS 名 ② `docker compose config` EXIT=0 ③ 缺必填变量时 compose **仍然拒绝渲染**（P0 #8 / X-11 的 fail-closed **不能被这次改动弄坏**） |
| **完成条件** | **两种部署形态各自有可直接照抄的命令**，且 compose 的容器内语义与 fail-closed **逐字未变** |
| **失败处理** | 改完 compose 不再渲染 ⇒ **立刻回滚 compose 部分**（根级文件无备份 ⇒ 改前先 `cp docker-compose.yaml /tmp/docker-compose.yaml.bak`）⇒ 只保留 `.env.host.example` 方案 |
| **状态更新** | 无 |
| **后续任务** | `P1-T05`（真起） |

### P1-T03 · 端口错开 + 硬编码端口 env 化

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P1-T03` |
| **原始编号** | 原文无独立编号（`P0 #5` 的子项） |
| **来源** | `.env.example:122-146` · `service/article/article-rpc/src/main/resources/application.yml:117`（`port: 9001`）· `service/comment/comment-rpc/.../application.yml:209`（`port: 9001`）· `service/favorite/favorite-rpc/.../application.yml:124`（`port: 8082`）· `service/like/like-rpc/.../application.yml:208`（`port: 8082`）· `service/message/message-rpc/.../application.yml:120`（`port: 8082`）· `service/like/like-api/.../application.yml:33`（`port: 18887`）· `README.md:105-119`（端口表） |
| **当前状态** | ✅ **已完成**（2026-11-01 · 落码提交 `1fa8a080`）—— **11 处 yml 端口 env 化** ⇒ **硬编码 7 处 ⇒ 0**；🟢 **Round 7 订正（端口两层分工）**：**默认值层 = Go 真源值**（`DEFAULT_GO_MISMATCH_COUNT 0`）· **分配层 = env 互异**（`.env` / `.env.host` 各 **29 个端口变量 · 0 重复**）⇒ ⚠️ **Round 1 落码曾把 6 个默认值改偏**（`R7-PORT-001`）**并打红 `favorite-rpc` 门禁**（`R7-PORT-003`）⇒ **Round 7 已回改 6 行 + 已修门禁**；保留 `article-rpc` 默认 9001（article 探针依赖）· `task-rpc` 的 `server.port` 补声明后**已回滚**（该模块无 web starter ⇒ 无 HTTP 口） |
| **执行目的** | 20 个服务**同机并行** ⇒ 端口必须两两不同；硬编码端口在同机并行时**无法错开** ⇒ 必须 env 化 |
| **具体操作** | ① 选一套**互不冲突**的端口（保留现有值优先，最小改动）：<br>`user-api` 49900 · `admin-api` **8890**（改）· `article-api` 8889 · `comment-api` 8888 · `favorite-api` 18893 · `follow-api` 8891 · `hot-api` 18897 · `like-api` **18887**（env 化）· `message-api` 8892 · `task-api` 18886<br>gRPC：`user-rpc` 49210 · `admin-rpc` 8081 · `article-rpc` **9001**（env 化）· `comment-rpc` **9002**（env 化）· `favorite-rpc` **8082**（env 化）· `follow-rpc` **8083**（env 化）· `hot-rpc` **8084**（env 化）· `like-rpc` **8085**（env 化）· `message-rpc` **8086**（env 化）· `task-rpc` **9005**（env 化）<br>② **把 6 处硬编码端口 env 化**：`grpc.server.port: 9001` ⇒ `${ARTICLE_RPC_PORT:9001}` 等 ⇒ **同时给 `.env` / `.env.example` 补上对应键**<br>③ `like-api` 的 `server.port: 18887` ⇒ `${LIKE_API_PORT:18887}`<br>④ 同步 README 的端口表<br>⑤ ⚠️ **注意 `hot-rpc` 的 `management.server.port: -1`**（禁用 HTTP server）⇒ 无 management 端口冲突问题；`follow-rpc` 的 management 端口是 `${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ⇒ 单独占一个 |
| **具体落点** | 6 个域的 `service/<域>/<域>-{api,rpc}/src/main/resources/application.yml`（**各域写权面**）· `.env` / `.env.example` / `README.md`（主线面） |
| **执行主体** | **各域 lane**（`article` / `comment` / `favorite` / `follow` / `hot` / `like` / `message` / `task` 各 1 处 + `users` 1 处）＋ **主线** 统一 `.env` / README |
| **前置条件** | `P1-T01`（端口表） |
| **并行关系** | 各域之间**可并行**（文件不重叠）⇒ ⚠️ **但每个域的 `target/` 不能并发** ⇒ **按域分波次串行**（每波 1~2 个域），或每域用独立工作树（`docs/DRIFT/global/lanes/_PROTOCOL.md` 硬规则 2 + 拆分纪律 1） |
| **写权限** | `service/<域>/**` = **各域 lane** · `.env` / `.env.example` / `README.md` = **主线** |
| **验收命令** | ```bash# ① 全仓不得再有「不可 env 覆盖」的 api/rpc 端口\ngrep -rnE '^\s+port: [0-9]+$' service/*/*/src/main/resources/application.yml | grep -v 'port: -1'\n# 期望：每一条都能对上本轮改过的 env 化项；若仍有残留 ⇒ 列出并逐条处理\n# ② 端口表零冲突（机器核）\npython3 - <<'PY'\nimport re,collections,pathlib\nvals=collections.defaultdict(list)\nfor p in pathlib.Path('service').glob('*/*/src/main/resources/application.yml'):\n    for i,l in enumerate(p.read_text(errors='replace').splitlines(),1):\n        m=re.search(r'^\\s+port:\\s*\\$\\{([A-Z_]+):(\\d+)\\}',l)\n        if m: vals[int(m.group(2))].append(f'{p}:{i} {m.group(1)}')\n        elif re.match(r'^\\s+port:\\s*\\d+$',l): print('HARDCODED',p,i,l.strip())\nfor port,who in sorted(vals.items()):\n    if len(who)>1: print('CONFLICT',port,who)\nPY``` |
| **验收证据** | ① 硬编码端口清单**归零**（`HARDCODED` 无输出）② `CONFLICT` 无输出 ③ 每个端口的归属唯一且能说出是哪个服务 |
| **完成条件** | **20 个服务的监听端口两两不同** ＋ **6 处硬编码端口全部 env 化** ＋ `.env` / `.env.example` / README 三处端口表**逐条一致** |
| **失败处理** | 某域 lane 未交付 ⇒ **其余域不受影响**，但 `P1-T05` 对该服务标「未起（缺端口 env 化）」⇒ 记入结束检查的「未完成任务及原因」；**不得**为了让端口表好看而把两个服务塞回同一个端口 |
| **状态更新** | 各域台账不必因端口 env 化而改（**不是行为漂移**）⇒ 但**若某模块原先没有端口变量**，需在域台账登记一条「新增 env 化端口」 |
| **后续任务** | `P1-T05`（真起） |

### P1-T04 · 订正 README §三 与 `.env.example` 标题

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P1-T04` |
| **原始编号** | `P0 #5` · `D-02`（`PhaseGoal-judge.md:561`） |
| **来源** | `README.md:105-119`（端口表）· `README.md:127-147`（§三）· `.env.example:2`（标题写 `Sea-RideTheWind_java`）· `README.md:13`（「〇、5 分钟跑起来（users 域…）」） |
| **当前状态** | ✅ **已完成**（2026-11-01 · 落码提交 `1fa8a080`）—— README 标题 `# RAG` · §二 端口表 · §三 **两段部署形态**（可复制命令块）· §五 已知限制 · §六 排障 ⇒ `.env.example` 旧项目名 **0 命中** · `check-doc-links.py` / `check-no-prod-secrets.py` **EXIT=0** |
| **执行目的** | P0 #5 的判据是「按照**实际项目说明**」⇒ 说明本身必须可执行；`.env.example` 标题写错项目名会让第三方以为这是另一个仓的模板 |
| **具体操作** | ① README §三 改成两段：**「起 users 域（compose）」** 与 **「起其余 9 个域（宿主机 jar）」**，后者给出**可直接复制的完整命令块**（含 `set -a && . ./.env.host && set +a` 与逐服务启动行）② README 端口表按 `P1-T03` 的新端口**重写**（含 20 个服务 + 各自「就绪判据」）③ README §五「已知限制」表的第 1 行（compose 只编排 users 域）保留，但**补一句**「其余 9 域按 §三 的宿主机命令起」④ `.env.example:2` 的标题 `Sea-RideTheWind_java` ⇒ 改 `RAG`（或项目真名）⑤ ⚠️ **不要**在 README 里写「已通过」「全部对齐」之类结论句（结论归 judge） |
| **具体落点** | `README.md` · `.env.example` |
| **执行主体** | **合并收口执行者（主线）** |
| **前置条件** | `P1-T02`（命令要用宿主机模板）· `P1-T03`（端口表） |
| **并行关系** | 依赖前两项 ⇒ **串行在后** |
| **写权限** | 根级 = **主线** |
| **验收命令** | ```bashpython3 tools/check-doc-links.py          # 链接门\npython3 tools/difftest/check-no-prod-secrets.py   # 示例文件不含真密钥\ngrep -n 'Sea-RideTheWind_java' .env.example README.md && echo "❌ 旧项目名残留" || echo "✅ 无旧项目名"``` |
| **验收证据** | ① 链接门 EXIT=0 ② 密钥门 EXIT=0 ③ 旧项目名 0 命中 ④ README §三 的命令块**逐行可复制** |
| **完成条件** | **第三方只读 README §三 就能起起其余 9 个域**（不依赖本轮之外的隐含知识） |
| **失败处理** | 链接门红 ⇒ 改链接（**不要**删链接）；密钥门红 ⇒ 换占位符形态 |
| **状态更新** | `PhaseGoal-judge.md` 的 D-02 行**本轮不改**（judge 是 Round 3 的面）⇒ 只在 Round 3 的证据里带 |
| **后续任务** | `P1-T05` |

### P1-T05 · 真起 20 个服务，逐个确认「在服务」

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P1-T05` |
| **原始编号** | `P0 #5` · `X-12` |
| **来源** | `PhaseGoal-judge.md:436`（v7 上调依据）· `README.md:127-147` |
| **当前状态** | ✅ **已完成**（2026-11-01 · 落码提交 `1fa8a080`）—— 新建 `docs/RoundPlan/round-01/verify-all-services.sh` ⇒ **PASS=20 / FAIL=0 / EXIT=0**；**两条跨服务链路有业务响应**：`GET /v1/articles` ⇒ 200 + PG 真数据且 `author_name` 来自 `user-rpc` ⇒ **rpc→rpc 亦通**；`POST /favorite/v1/item/create` ⇒ 业务码随播种从 `5101` → `5005` 变化 ⇒ **`favorite-rpc→user-rpc` 真往返** |
| **执行目的** | 判据要求「**项目**能按说明启动」⇒ 必须**真的把 20 个服务起起来**，且每个都要证明**在服务**而不是**进程活着** |
| **具体操作** | ① 按 `P1-T01` 的**下游依赖顺序**起（被依赖的先起）② 每起一个 ⇒ 三个判据同时查：**Nacos 服务名可查** + **端口 LISTEN** + **健康端点 200** ③ ⚠️ **起进程必须传值**（`${...}` 占位符未解析 ⇒ 进程启动即抛 `IllegalStateException` ⇒ **fail-closed 守卫**）④ ⚠️ **起进程用 `exec`**（`( cd X && cmd ) &` 的 `$!` 是子 shell PID ⇒ 收尾按**端口**复核，不按 PID）⑤ 收尾按**端口轮询**直到空（`docker rm -f` 后宿主口仍可能被 docker-proxy 占住）⑥ **打通至少一条 rpc→rpc**（如 `comment-rpc` → `article-rpc`）与 **一条 api→rpc→PG**（如 `article-api` → `article-rpc` → PG） |
| **具体落点** | 20 个 `service/<域>/<域>-{api,rpc}/target/*.jar`（**只启动，不改代码**）· 起栈记录落 `docs/RoundPlan/round-01/service-startup-matrix.md` 的「实测结果」列 |
| **执行主体** | **合并收口执行者（主线）**（起进程不涉及写权） |
| **前置条件** | `P1-T02` ∧ `P1-T03` ∧ `P1-T04` |
| **并行关系** | 依赖前四项 ⇒ **串行在后**；服务之间按依赖顺序，**无依赖者可并行起** |
| **写权限** | 只写 `docs/RoundPlan/round-01/**` 的实测列 ⇒ **不改任何产品代码** |
| **验收命令** | ```bash# ① 20 个服务逐个：Nacos 注册 + 端口监听 + 健康 200\nfor svc in <从 service-startup-matrix.md 读出 20 个服务名>; do\n  port=$(grep -A5 "^| $svc " docs/RoundPlan/round-01/service-startup-matrix.md | grep -oE '\\| [0-9]{4,5} ' | head -1 | tr -d '| ')\n  printf '%-22s port=%-6s ' "$svc" "$port"\n  lsof -nP -iTCP:$port -sTCP:LISTEN >/dev/null 2>&1 && printf 'LISTEN ' || printf 'NO-LISTEN '\n  curl -s -o /dev/null -w 'health=%{http_code}\\n' --max-time 5 http://127.0.0.1:$port/actuator/health\ndone\n# ② Nacos 侧：20 个服务名都能查到\ncurl -s 'http://127.0.0.1:49848/nacos/v1/ns/instance/list?serviceName=<service>' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d.get("hosts",[])))'\n# ③ 跨服务链路（按实际选一条）\ngrpcurl -plaintext 127.0.0.1:<rpc-port> list | head -3``` |
| **验收证据** | ① **20 行**「服务名 · 端口 · LISTEN · health 200 · Nacos 实例数 ≥1」② 跨服务调用**有响应**（不是连接失败）③ **失败的服务逐个写明失败原因**（**不得**写「已通过」） |
| **完成条件** | **20/20 在服务**（三个判据同时满足）＋ 两条跨服务链路打通 ⇒ **P0 #5 与 X-12 的证据就位** |
| **失败处理** | 某个服务起不来 ⇒ ① **先按 `PIT-GLOBAL-032` 的三步定性法**判定「真失败 / 假红」：单跑复跑 → 降并行度重跑 → **不要跳过全量** ② 假红（端口占用 / 构建竞态 / 环境时序）⇒ 修环境后重试 ③ 真失败 ⇒ 记「服务名 + 失败原因 + 根因位置」⇒ **按 `CT-02` 走**：定级 + 记录来源/证据/判据/所需决策 ⇒ **不得当场改产品代码**（规范 §一.6） |
| **状态更新** | 本轮**不改** `PhaseGoal-judge.md`（Round 3 的面）⇒ 实测结果先落在 `round-01/service-startup-matrix.md` |
| **后续任务** | `Round 2`（E65 验收需要真栈）· `P3-T02`（P0 #5 行改判需要证据） |

---

## §5.4 具体验收命令（本轮整体）

> ⚠️ **取退出码必须先落变量**（`out=$(cmd); rc=$?`）⇒ **不要**用 `cmd | tail` 的退出码（管道末段）。

```bash
cd "/Users/pr/Documents/java&golang/RAG"

# ── ① 构建产物（20/20）──────────────────────────────────────
bash tools/difftest/check-jar-executable.sh          # 未构建即判红 · 永远打印「已检查 N/M」

# ── ② compose 未被这次改动弄坏（容器内语义 + fail-closed）────
docker compose config >/dev/null && echo "compose OK"
env -u USER_JWT_SECRET docker compose config >/dev/null 2>&1; echo "缺变量 EXIT=$? （期望非 0）"

# ── ③ 硬编码端口归零 + 端口零冲突 ─────────────────────────
python3 - <<'PY'
import re,collections,pathlib
vals=collections.defaultdict(list); bad=[]
for p in pathlib.Path('service').glob('*/*/src/main/resources/application.yml'):
    for i,l in enumerate(p.read_text(errors='replace').splitlines(),1):
        m=re.search(r'^\s+port:\s*\$\{([A-Z_]+):(\d+)\}',l)
        if m: vals[int(m.group(2))].append(f'{p}:{i} {m.group(1)}')
        elif re.match(r'^\s+port:\s*\d+$',l) and 'port: -1' not in l: bad.append(f'{p}:{i} {l.strip()}')
for b in bad: print('HARDCODED',b)
for port,who in sorted(vals.items()):
    if len(who)>1: print('CONFLICT',port,who)
print('HARDCODED_COUNT',len(bad))
PY

# ── ④ 20 个服务「在服务」（三判据）────────────────────────
bash docs/RoundPlan/round-01/verify-all-services.sh   # 本轮产出：读矩阵逐个核 Nacos+LISTEN+health

# ── ⑤ 文档与密钥门 ───────────────────────────────────────
python3 tools/check-doc-links.py
python3 tools/difftest/check-no-prod-secrets.py

# ── ⑥ 端口占用型假红排查 ─────────────────────────────────
lsof -nP -iTCP:<port> -sTCP:LISTEN        # 起不来时先查谁占着（PIT-GLOBAL-038）
```

---

## §5.5 可客观判定的完成条件

| # | 判据 | 怎么判 |
|---|---|---|
| 1 | 起栈依赖表齐全 | `docs/RoundPlan/round-01/service-startup-matrix.md` 有 **20 行**，每行 9 项无空缺 |
| 2 | 硬编码端口归零 | 上面的 python 脚本 `HARDCODED_COUNT` = **0** |
| 3 | 端口零冲突 | 同上脚本 **无 `CONFLICT` 输出** |
| 4 | 宿主机地址可解析 | `.env.host.example` 里 **0 个**容器内 DNS 名 |
| 5 | compose 未被破坏 | `docker compose config` EXIT=0 ＋ 缺变量时**非 0** |
| 6 | **20/20 在服务** | 每个服务：Nacos 实例 ≥1 ＋ 端口 LISTEN ＋ health **200** |
| 7 | 跨服务链路通 | 至少 1 条 rpc→rpc ＋ 1 条 api→rpc→PG **有响应** |
| 8 | README 可照做 | 第三方只读 §三 就能起；旧项目名 **0 命中** |
| 9 | 文档门绿 | `check-doc-links.py` EXIT=0 · `check-no-prod-secrets.py` EXIT=0 |
| 10 | **不能算完成的情况** | 任一服务「进程活着但不在服务」· 任一服务靠 `ps` 判存活 · 把失败服务写成「已通过」· 只起了 users 域就宣称 P0 #5 闭合 |

---

## §5.6 本轮结束检查

- [ ] **P1-T01** 起栈依赖表 = 20 行 + 硬编码端口小表
- [ ] **P1-T02** 宿主机模板 + compose 零破坏 + fail-closed 仍生效
- [ ] **P1-T03** 硬编码端口 = 0 · 端口零冲突 · 三处端口表一致
- [ ] **P1-T04** README §三 可照做 · 端口表重写 · 旧项目名 0 命中
- [ ] **P1-T05** **20/20 在服务**（三判据）＋ 两条跨服务链路通
- [ ] 证据路径：实测结果写在 `docs/RoundPlan/round-01/service-startup-matrix.md` 的「实测结果」列（**逐服务三判据的原始输出**）
- [ ] 原始编号 → 最新状态对应：`P0 #5` / `X-12` ⇒ **待 Round 3 改判**（本轮只产出证据，**不改 judge**）
- [ ] 未完成任务及原因：（逐条写；**不得**留空）
- [ ] 被阻塞的后续任务：`Round 2` 的 E65 **验收**（需真栈）；`P3-T02`（需活体证据）
- [ ] 本轮新增问题及阶段一分类：（按 `twoStage.md` §十 定级；**未登记的问题不得直接变成整改任务** ⇒ 走 `CT-02`）
- [ ] **是否满足进入 Round 2 的条件**：**是** —— 前提是「20/20 在服务」成立；若不成立，**Round 2 的实现任务（`P2-T01` 的前 4 项）仍可并行推进**，但**其验收不得开始**
- [ ] ⚠️ **本轮禁止**：改 Go 真源 · 改 `service/common/**`（G4 的面）· 改 `PhaseGoal-judge.md`（Round 3 的面）· 顺手修任何「起栈时发现的代码问题」（⇒ 走 `CT-02`）· 改 `tools/difftest/` 的守门清单计数口径（会动 §6.3 的 27 个计数 ⇒ 制造 B14）

---

## 防错约束（Pitfalls 转执行约束，保留原编号与出处）

| PIT 编号 | 出处（规范位置 · 逐条可核） | 规则（摘自该 PIT 标题原文） | 约束绑定的任务 ID |
|---|---|---|---|
| `PIT-GLOBAL-038` | `docs/Pitfalls/global/cross-domain.md:1307` | 🔴 **端口占用型假红**：`BindException` ⇒ 先查「谁占着那个端口」，再查「是不是随机端口」 | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-032` | `docs/Pitfalls/global/cross-domain.md:1108` | 🔴 **「跑红」也可能是假红**：两类非代码原因（构建竞态 / 环境时序）—— 必须单独复跑定性 | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-029` | `docs/Pitfalls/global/cross-domain.md:952` | 🔴 **静态扫描不排除 `target/` ⇒ 构建产物的旧副本被当源码判 ⇒ 门禁恒红** | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-054` | `docs/Pitfalls/global/cross-domain.md:1962` | 🔴 **「接进编排」≠「有了闸门」** —— 六种假绿（判定只写在注释里 / 退出码是意外得来的 / 守门分支短路 / 端口与就绪不跟随约定 / **施加侧空转** / **阴性观测误诊**） | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-035` | `docs/Pitfalls/global/cross-domain.md:1203` | Go 的 `select` 有多个分支 ⇒ 探针的 handler **必须永不返回**，否则**探针是随机的** | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-030` | `docs/Pitfalls/global/cross-domain.md:995` | 🔴 **守门只扫一半的模块 ⇒ 另一半的缺陷只能靠「手工盘点」发现 ⇒ 手工必漏** | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-040` | `docs/Pitfalls/global/cross-domain.md:1425` | 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径） | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
| `PIT-GLOBAL-031` | `docs/Pitfalls/global/cross-domain.md:1042` | 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来** | 约束本轮任务：`P1-T01` · `P1-T02` · `P1-T03` · `P1-T04` · `P1-T05` |
