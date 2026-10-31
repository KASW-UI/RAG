# spec: Round 1 · 全服务起栈（闭合 P0 #5 证据覆盖缺口）

- 分支: `do-my-own-gh`（主线 · **合并收口执行者**）
- 基准提交: `e77941cf`（收官终态轮）
- 计划书: [`docs/RoundPlan/round-01/README.md`](../../docs/RoundPlan/round-01/README.md)（`P1-T01`~`P1-T05`）
- 交付物: [`docs/RoundPlan/round-01/service-startup-matrix.md`](../../docs/RoundPlan/round-01/service-startup-matrix.md)（§7 = 实测结果）· [`docs/RoundPlan/round-01/verify-all-services.sh`](../../docs/RoundPlan/round-01/verify-all-services.sh)
- 关联判据: `P0 #5` / `X-12`（`docs/PhaseGoal-judge.md` §七 · §五）· `docs/twoStage.md` §5.6 / §5.7

---

## 0. 目标与判定

| 项 | 内容 |
|---|---|
| **目标** | 把「**项目**能按实际项目说明完成构建、配置和启动」从 **1/10 域、2/20 服务** 变成 **10 域、20 个服务全覆盖**（**补证据，不改产品行为**） |
| **成功判据** | ① **20/20 在服务**（三判据：Nacos 服务名可查 + 端口 LISTEN + `/actuator/health`=200）② 两条跨服务链路**有业务响应**（一条 api→rpc→PG、一条 rpc→rpc）③ 20 个服务的**监听口两两不同**、**零硬编码端口** ④ **README §三 可照做**（第三方只读即可起其余 9 域）⑤ **compose 的容器内语义与 fail-closed 逐字未变** |
| **不做** | 改 Go 真源 · 改 `service/common/**`（G4 的面）· 改 `PhaseGoal-judge.md` 的**判据原文**（只改状态列）· **顺手修起栈时发现的代码问题**（⇒ 走 CT-02 登记）· 改 `tools/difftest/` 的守门清单计数口径 |
| **不做（阶段二）** | 生产级十二维度审查 · §9 故障矩阵 · SLO / 灾备演练 |

---

## 1. 逐任务落点

| 任务 | 落点 | 状态 |
|---|---|---|
| **P1-T01** 起栈依赖表 | `docs/RoundPlan/round-01/service-startup-matrix.md` §1（20 行 × 9 项 · 每行带 `文件:行号`）＋ §2 硬编码端口小表 ＋ §4 三判据表 | ✅ |
| **P1-T02** 宿主机地址模板 | **新建** `.env.host.example`（`127.0.0.1` + 宿主映射端口）· `.env.example`（compose 容器内语义）**一字未改** | ✅ |
| **P1-T03** 端口 env 化 + 零冲突 | **11 处** `application.yml`（7 处硬编码 + `task-rpc` 的补声明（后撤销）+ 3 处默认值错开）⇒ **硬编码 0 处** · **29 个监听口零冲突** | ✅ |
| **P1-T04** README 可照做 | `README.md` 标题 / §二 端口表 / §三（两段部署形态 + 可复制命令块）/ §五 已知限制 / §六 排障 · `.env.example` 标题（旧项目名清零） | ✅ |
| **P1-T05** 20/20 在服务 | `docs/RoundPlan/round-01/verify-all-services.sh` ⇒ **PASS=20 / FAIL=0 / EXIT=0** | ✅ |

---

## 2. 关键取舍（**为什么这么改**）

1. **端口 env 化保留默认值** —— Spring Boot 的优先级是「命令行 > OS env > 配置文件」⇒ **配置文件里的占位符值是最低优先级** ⇒ 探针（无论用命令行参数还是 env 覆盖）都能盖住 ⇒ ⇒ **env 化对既有 L4 探针零影响**（逐条证据见矩阵 §2.1）。
   ⇒ 唯一必须保留的默认值 = **`article-rpc` 的 9001**（article 探针**不传任何端口参数** ⇒ 它依赖 yml 默认值）。
2. **Nacos 地址不动 yaml** —— 10 个服务的 `server-addr` 是**融合写法**（`${NACOS_ADDR:nacos:8848}` ⇒ 忽略 `NACOS_PORT`）⇒ 换环境时静默连到默认 8848 ⇒ 属**A 类配置默认值缺陷** ⇒ 按纪律**本轮不改**，用 per-process env 覆盖绕过 ⇒ **登记为 `R1-ENV-001`**（`docs/DRIFT/global/_README.md`）。
3. **`task-api` 的地址默认值不动** —— 缺 devh 要求的 scheme ⇒ 属**A 类配置默认值缺陷** ⇒ **登记为 `R1-ENV-002`**，本轮用 env 覆盖绕过。
4. **compose 语义零改动** —— 只新增 `.env.host.example`（方案 A：两份模板）⇒ `docker compose config` EXIT=0 且 **缺变量仍 EXIT=1**（P0 #8 的 fail-closed 未被破坏）。
   ⚠️ 验收命令的订正：计划书写的 `env -u USER_JWT_SECRET docker compose config` 是**假阴性判据**（compose 会自动读 `.env`）⇒ 真测要用 `--env-file /dev/null`。

---

## 3. 验收（**逐条实跑 · 取真实退出码**）

```bash
cd "/Users/pr/Documents/java&golang/RAG"
# ① 20 个服务在服务（三判据）
bash docs/RoundPlan/round-01/verify-all-services.sh          # ⇒ PASS=20 / FAIL=0 / EXIT=0
# ② compose 未被破坏 + fail-closed 仍在
docker compose config >/dev/null; echo "compose EXIT=$?"      # ⇒ 0
env -u USER_JWT_SECRET docker compose --env-file /dev/null config >/dev/null 2>&1; echo "缺变量 EXIT=$?"   # ⇒ 1
# ③ 端口：硬编码 0 + 监听口零冲突
python3 tools/difftest/check-tomcat-limits.py                 # ⇒ 0
# ④ 文档门 / 密钥门 / 一致性门
python3 tools/check-doc-links.py; python3 tools/difftest/check-no-prod-secrets.py
python3 tools/difftest/check-phasegoal-consistency.py         # ⇒ 0
python3 tools/ledger-derive/derive-stage1.py --check          # ⇒ 0
```

**跨服务链路（原始输出留证）**：矩阵 §7.3。

---

## 4. 本轮新增登记（**登记即交付 · 不升级拍板**）

| 编号 | 内容 | 落点 |
|---|---|---|
| `R1-ENV-001` | Nacos 地址**两种写法并存**（10 个服务忽略 `NACOS_PORT`） | `docs/DRIFT/global/_README.md` |
| `R1-ENV-002` | `task-api` 的 gRPC 客户端地址默认值**缺 scheme** | `docs/DRIFT/global/_README.md` |
| `ENV-08` | 本机 `8082` 被**外部** Python 服务占用（端口占用型假红） | `docs/RoundPlan/_unresolved.md` |
| `ENV-09` | PG `max_connections=100` 装不下 20 个服务（容量前提） | `docs/RoundPlan/_unresolved.md` |

⇒ 四条**均不阻阶段一**（本轮用 env 覆盖 / 环境侧处置即达成 20/20）⇒ 阶段一判定见 `docs/PhaseGoal-judge.md` §8.4。

---

## 5. 风险与回滚

| 风险 | 处置 |
|---|---|
| 端口默认值改动影响既有探针 | 已逐条复核（矩阵 §2.1）⇒ 探针走 env/命令行覆盖 ⇒ **零影响**；`article-rpc` 的 9001 **刻意保留** |
| `.env.host` 含真密钥 | `.env.host` **已 gitignore**（不入版本控制）⇒ 仓内只有 `.env.host.example`（占位符）⇒ 密钥门 EXIT=0 |
| PG `max_connections` 被改（环境侧） | `ALTER SYSTEM SET max_connections = 500` ⇒ 可用 `ALTER SYSTEM RESET max_connections` + 重启回滚 |
| 20 个服务常驻占用宿主端口 | 收尾按 `docs/RoundPlan/round-01/verify-all-services.sh` 的端口表逐个停；**端口释放有延迟** ⇒ 必须轮询到空（`PIT-GLOBAL-038`） |
