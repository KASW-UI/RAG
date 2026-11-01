# spec: Round 7 · 端口「默认值层」回改（修 Round 1 引入的 6 处对外契约面漂移）

- 分支: `do-my-own-gh`（主线 · **合并收口执行者**）
- 基准提交: `1fa8a080`（Round 1 落码 · **本 spec 就是为修它的缺陷而写**）
- 计划书: [`docs/RoundPlan/round-07/README.md`](../../docs/RoundPlan/round-07/README.md)（`P7-T01` · T1~T9）· 缺陷细节 = [`docs/RoundPlan/round-01/service-startup-matrix.md`](../../docs/RoundPlan/round-01/service-startup-matrix.md) **§7.6**
- 交付物: 同上 §2 / §3.2 / §7.5 / §7.6 / §8（口径订正）· [`docs/RoundPlan/_final-acceptance.md`](../../docs/RoundPlan/_final-acceptance.md) §9.4 · [`docs/DRIFT/global/_README.md`](../../docs/DRIFT/global/_README.md) Round 7 段（`R7-PORT-001` / `R7-PORT-002`）
- 关联判据: `docs/twoStage.md` §5.6 / §5.7 · `docs/PhaseGoal-judge.md` §十 变更记录 **#37 / #38**

---

## 0. 目标与判定

| 项 | 内容 |
|---|---|
| **目标** | 把 Round 1 引入的 **6 处端口默认值漂移**（默认值 ≠ Go 真源值）**按 Go 值回改**，并把端口口径固化为**两层分工**（**默认值层 = Go 值 · 分配层 = env 互异**） |
| **发现路径** | Round 7 的**全量回归**（`mvn -o -B -DargLine=-Xmx2g clean test`）在 `hot-rpc` 处 **BUILD FAILURE** ⇒ 既有守门 `HotRpcPrometheusCarrierGateTest.shippedJarKeepsGoObservationPortAndSeparatesFaces` **精确转红**（`grpc.server.port` 实际 `${HOT_RPC_GRPC_PORT:8084}` · 必须 `8083`） |
| **成功判据** | ① `HotRpcPrometheusCarrierGateTest` **转绿**（EXIT=0）② 端口两层体检：`HARDCODED_COUNT 0` · `DEFAULT_GO_MISMATCH_COUNT 0` · `.env.example` / `.env.host.example` 各 `ALLOC_DUP_COUNT 0` ③ **全量回归 BUILD SUCCESS** ④ **阶段一结论不变**（P0 #5 的证据面是**按 env 分配的** 20/20 起栈 ⇒ 与默认值无关） |
| **不做** | 改 Go 真源 · 改 `service/common/**`（G4 的面）· 改 `PhaseGoal-judge.md` 的**判据原文**（只改状态列 / 变更记录）· 改 `tools/difftest/` 的守门清单计数口径（⇒ **不新增 `check-*.py`**）· **不顺手补 5 处缺失的守门**（⇒ 登记为 `R7-PORT-002` + `DEP-06`）· 改 `service/common/**` 的共享层 |
| **不做（阶段二）** | 生产级十二维度审查 · §9 故障矩阵 · SLO / 灾备演练 |

---

## 1. 落码（**6 行 · 零位移 · 只改默认值**）

| # | 文件:行号 | 改前 | 改后 | Go 真源出处 |
|---|---|---|---|---|
| 1 | `service/users/admin/admin-api/src/main/resources/application.yml:65` | `${ADMIN_API_PORT:8890}` | `${ADMIN_API_PORT:8889}` | `service/user/admin/api/etc/admincenter.yaml:3` = 8889 |
| 2 | `service/comment/comment-rpc/src/main/resources/application.yml:223` | `${COMMENT_RPC_PORT:9002}` | `${COMMENT_RPC_PORT:9001}` | `service/comment/rpc/etc/comment.yaml:2` = 9001 |
| 3 | `service/follow/follow-rpc/src/main/resources/application.yml:154` | `${FOLLOW_RPC_PORT:8083}` | `${FOLLOW_RPC_PORT:8082}` | `service/follow/rpc/etc/follow.yaml:2` = 8082 |
| 4 | `service/hot/hot-rpc/src/main/resources/application.yml:220` | `${HOT_RPC_GRPC_PORT:8084}` | `${HOT_RPC_GRPC_PORT:8083}` | `service/hot/rpc/etc/hot.yaml:2` = 8083 |
| 5 | `service/like/like-rpc/src/main/resources/application.yml:307` | `${LIKE_RPC_PORT:8085}` | `${LIKE_RPC_PORT:8082}` | `service/like/rpc/etc/like.yaml:2` = 8082 |
| 6 | `service/message/message-rpc/src/main/resources/application.yml:219` | `${MESSAGE_RPC_PORT:8086}` | `${MESSAGE_RPC_PORT:8082}` | `service/message/rpc/etc/message.yaml:2` = 8082 |

> ✅ **env 化本身全部保留**（`${VAR:默认值}` 形态不变）⇒ **硬编码端口仍为 0**。
> ✅ **分配层不动**（`.env.example` / `.env.host.example` 的取值全部保持）⇒ **同机并行仍零冲突** ⇒ **20/20 起栈不受影响**。

## 1′ 同源的第二个缺陷：`favorite-rpc` 门禁的**解析器**读不懂占位形态（**已修**）

| 项 | 内容 |
|---|---|
| **现象** | 续跑（`-rf :favorite-rpc`）⇒ `FavoriteRpcProductionStartupTest.observationPortIsSeparatedFromBusinessPort` 转红：`grpc.server.port` 实际 = `${FAVORITE_RPC_PORT:8082}` ⇒ **值是对的（8082 = Go 的 `ListenOn`）** |
| **根因** | `FavoriteRpcProductionStartupTest.java:151` 的 `stripPortNumber` 是**旧拷贝**（只做 `raw.lastIndexOf(':')`）⇒ 对占位形态返回 **`"8082}"`** ⇒ ⇒ **Round 1 的 env 化**（`port: 8082` → `port: ${FAVORITE_RPC_PORT:8082}`）**把这个门禁打红了** |
| **为何是「遗漏」不是「设计」** | 其余三份同族 helper（`article-rpc` · `favorite-rpc` 的 `PrometheusCarrierGateTest` · `hot-rpc` 的 `PrometheusCarrierGateTest`）**都**处理占位形态；且这一份自己的 javadoc 写着「**从 `${ENV:default}` 形态里取出默认端口**（env 覆盖是既有约定）」⇒ **实现与自身声明不符** |
| **修法** | 改成与其余三份**逐字一致**的实现 ⇒ **断言语义不变**（仍要求「默认值一字不差 = Go 的 `ListenOn`」） |
| **验证** | ① 修后 ⇒ **4 条 / 0F / 0E / EXIT=0** ② **变异必红**：默认值改 `8083` ⇒ 精确转红（`observationPortIsSeparatedFromBusinessPort:122`）⇒ **判据有牙** ③ 还原后 `git diff` 该 yml **为空**（逐字节一致） |
| **登记** | `R7-PORT-003`（**已闭合** · `docs/DRIFT/global/_README.md`） |

---

## 2. 端口的**两层分工**（本轮固化的口径）

| 层 | 承担者 | 规则 | 守门 |
|---|---|---|---|
| **① 默认值层** | `application.yml` 的 `${VAR:默认值}` | **默认值 = Go 真源值，一字不差**（Go 侧本就重复：`8082` × 4 / `9001` × 2 / `8889` × 2） | 各 rpc 的 `*PrometheusCarrierGateTest`（**仅 3/9 覆盖** ⇒ `R7-PORT-002`） |
| **② 分配层** | `.env`（容器语义）· `.env.host`（宿主语义） | **同机并行 20 个服务 ⇒ 监听口互异**（29 个端口变量 · 0 重复） | `verify-all-services.sh`（20/20） |

> ⇒ **改端口请改 env，不要改默认值** —— 默认值属**对外契约面**。

---

## 3. 文档订正（口径统一）

| 文件 | 改了什么 |
|---|---|
| `docs/RoundPlan/round-01/service-startup-matrix.md` | §2 加「落码偏离工作单」订正 · **§3.1/§3.2 改为「两层分工」**（含 19 行 Go 出处表）· §7.5 P1-T03 口径 · **§7.6 新增**（发现 / 根因 / 修法 / 验证 / 教训）· **§8 复现脚本改为两层体检** |
| `README.md` | 端口表加「两层分工」说明 · **task 行的 actuator 口 `8080` 订正为「—」（无 web starter ⇒ 不起 HTTP 口）** |
| `docs/RoundPlan/round-01/README.md` | 5 条任务的「当前状态」从「🔴 未做 / 🟡 部分完成」订正为实际完成态 |
| `docs/RoundPlan/_INDEX.md` | 顶部结论行（❌ ⇒ ✅）· 当前状态行 · **新增「轮次执行状态」表**（7 轮逐轮实测核对） |
| `docs/RoundPlan/_final-acceptance.md` | **§9.4 新增**（回归面发现与回改 · 验证读数 · 两条教训） |
| `docs/DRIFT/global/_README.md` | **Round 7 段新增**（`R7-PORT-001` 已闭合 · `R7-PORT-002` 守门覆盖缺口） |
| `docs/RoundPlan/_unresolved.md` | **`DEP-06` 新增**（守门覆盖缺口 ⇒ 不阻阶段一） |
| `docs/PhaseGoal-judge.md` | §十 变更记录 **#37**（发现与回改）· **#38**（重新冻结）· **§十·二 整表重写** |

---

## 4. 验收命令（**逐条取真实退出码**）

```bash
cd "/Users/pr/Documents/java&golang/RAG"
# ① 触发过红的那条门（必须转绿）
mvn -o -B -pl service/hot/hot-rpc -am \
    -Dtest='HotRpcPrometheusCarrierGateTest' -Dsurefire.failIfNoSpecifiedTests=false test
# ② 全量回归（本轮唯一的权威构建面读数）
mvn -o -B -DargLine=-Xmx2g clean test
# ③ 端口两层体检（service-startup-matrix.md §8 脚本）
# ④ 台账 / 派生层 / 文档门
python3 tools/ledger-derive/derive-stage1.py --check   ; echo "derive EXIT=$?"
python3 tools/check-ledger-tables.py                   ; echo "tables EXIT=$?"
python3 tools/check-ledger-count.py                    ; echo "count EXIT=$?"
python3 tools/check-ledger-selfcheck.py                ; echo "selfcheck EXIT=$?"
python3 tools/check-agents-budget.py                   ; echo "budget EXIT=$?"
python3 tools/check-doc-links.py                        ; echo "links EXIT=$?"
python3 tools/difftest/check-phasegoal-consistency.py  ; echo "consistency EXIT=$?"
python3 tools/difftest/check-phasegoal-lanes.py --write ; git diff --stat docs/PhaseGoal/   # 必须为空
```

---

## 5. 自查清单

- [x] **只改默认值**（6 行 · 零位移）⇒ env 化保留 · 硬编码仍 0
- [x] **Go 出处逐条**（19 行表 · 每行带 `文件:行号`）
- [x] **不新增 `check-*.py`**（`R7-PORT-002` 只登记不落码）
- [x] **不改判据原文**（judge 只改状态列 / 变更记录 / §十·二）
- [x] **不改 `tools/difftest/` 计数口径**
- [x] **登记即交付**（`R7-PORT-001` / `R7-PORT-002` · `DEP-06`）⇒ **不升级拍板**
- [x] **未来日期 = 0**（`check-agents-budget.py` EXIT=0）
- [x] **生成物同步**（`docs/PhaseGoal/` diff 为空 · 派生层 `--check` EXIT=0）

---

## 6. 与本 spec 无关的**已知遗留**（**不修，只登记**）

| 项 | 去向 |
|---|---|
| `R1-ENV-001` Nacos 地址双写法（10 个服务忽略 `NACOS_PORT`） | `docs/DRIFT/global/_README.md` ⇒ **Round 4/5 面** |
| `R1-ENV-002` `task-api` 的 gRPC 客户端地址默认值缺 scheme | 同上 |
| `R7-PORT-002` 端口默认值门只覆盖 3/9 的 rpc | `DEP-06` ⇒ **Round 4/5 面** |
| §六 6.3 的 **27 项守门**里双侧活体类未逐个实跑 | **Round 6 收口回归**（⇒ S1-6 的另一半） |
