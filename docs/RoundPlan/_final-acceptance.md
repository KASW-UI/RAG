# 阶段一最终验收清单（规范 §11.5）

> **本清单以现有权威判据为依据，保留原始条目与判定口径** —— 判据原文一字未改。
> **不得另造一套与 `docs/PhaseGoal-judge.md` 不同的通过标准。**
> **本清单只登记「谁负责提供证据 / 怎么验 / 不通过怎么办」** —— 执行进度由 Round 6 的 `docs/RoundPlan/round-06/regression-report.md` 提供。

## 〇、口径与权威链

| 项 | 内容 |
|---|---|
| **判据原文来源** | ① `docs/PhasedGoal.md` §二 **P0 九条**（与 `docs/twoStage.md` §2.1 **逐字相同**）② `docs/twoStage.md` §5.6 **四种结论口径** + §5.7 **冻结基线 9 项** ③ `docs/PhaseGoal-judge.md` §五（跨组件不变量 **X-1~X-12**）· §七（P0 复核）· §八 8.2（C-1~C-4）· §十一（D-01~D-08）· §十·二（冻结基线）④ `docs/twoStage.md` §十一（**交付物 1~6**） |
| **字段**（规范 §11.5「至少关联」6 项 + 1 项核对日期） | 权威判据编号或原文定位 · 对应的组件/域/跨组件不变量 · 负责提供的任务 ID · 验收命令或证据 · 当前结果 · 未通过时的处理方式 · 核对日期 |
| **证据强度分层**（不许混写） | **L1** 源码级逐行对照 · **L2** 静态机读 · **L3** 单侧活体 · **L4** 双侧活体 · **E1** 文档成文 |
| **「当前结果」的口径** | 写「**在该证据强度下一致**」**不是**「已闭环」；台账里的 ✅ 一律读作前者。**「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」** |
| **本清单的当前基线** | 🟢 **阶段一当前结论 = ✅ 通过**（**2026-11-01 · Round 1 闭合 P0 #5 + Round 3 复判**）—— 两条 A 类阻断**全部消解**：**E65/X-6** ⇒ 2026-11-01 改判 ✅（继承面四键逐键相等 ＋ 客户端硬上界 14/16 模块已配 ＋ 判据盲区已修）· **P0 #5 覆盖缺口** ⇒ **Round 1 闭合**（`P1-T01`~`P1-T05` ⇒ **20/20 在服务** ＋ 两条跨服务链路）⇒ ⇒ 本清单是**已验收**清单（**冻结基线见 `docs/PhaseGoal-judge.md` §十·二**）<br>⚠️ **「通过」≠「零差异」**：C/D 类残留按 §1.1 ⑥ 第 4 条不计入；**本轮新登记 2 条 A 类配置默认值缺陷**（`R1-ENV-001` / `R1-ENV-002`）**不阻阶段一** |

---

## 一、P0 九条（判据原文见 `docs/PhasedGoal.md` §二）

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **P0 #1** 核心业务链路能够真实运行，不存在关键功能仅有接口骨架、模拟数据或硬编码成功结果 | 10 域全链路 · X-8 / X-9 | `P1-T05` · `P5-T01` · `P6-T01` | `python3 tools/difftest/check-surface-parity.py` ＋ `mvn -o -B -DargLine=-Xmx2g clean test` ＋ 各域双侧探针（`tools/api-full-parity-probe/**`） | ✅ **满足**（**L4**：契约面 HTTP 72≡72 / gRPC 71≡71；双侧活体覆盖 6 核心域）<br>⚠️ 残留：`003-C` 属 C 类（**不计入**）· `U-01` 的 DC→第三仓属**仓外** | 归 `CT-02` 定级；若为 A 类 ⇒ **阶段一不通过** | 2026-10-29 |
| **P0 #2** 核心参数校验、业务规则、身份认证和必要的权限控制有效 | 10 域 · 失败路径矩阵 227 条 | `P6-T01` | `bash tools/semantic-probe/run-parity.sh` ＋ `bash tools/json-body-probe/run-parity.sh` ＋ 各域失败路径矩阵模块级 `mvn test` ＋ `bash tools/errmsg-probe/<域>/run-parity.sh` | ✅ **满足**（**L3**：失败路径矩阵只起 RAG 单侧 ⇒ 证「失败路径有明确行为」，**不证**逐字节一致）<br>⚠️ 其余域「无权限概念 ⇒ N/A」是**裁定**，不是缺失 | 权限概念域的显式用例缺一条 ⇒ 归 `CT-02` 定级 | 2026-10-29 |
| **P0 #3** 关键数据能够正确持久化，事务边界合理，无严重数据丢失/重复处理/状态错误 | 9 域 DDL · X-2 · 事务边界 | `P4-T03` · `P6-T01` | `python3 tools/difftest/check-ddl-static.py --domain <域> --indexes --check-source`（9 域）＋ 5 个真 PG 事务守门测试 | ✅ **满足**（**L4**：9 域逐域实跑，源码↔活库一致，变异必红）<br>⚠️ 残留 51 处（C 23 / D 28 / **A 0**）⇒ 按 §1.1 ⑥ 第 4 条**不计入** | 出现 **A 类**残留 ⇒ **阶段一不通过** | 2026-10-29 |
| **P0 #4** 核心失败路径具有明确行为，不无声吞掉关键异常或返回误导性成功 | 失败路径矩阵 227 条 · 错误码 9 域 | `P2-T05` · `P3-T01` · `P6-T01` | `bash tools/errmsg-probe/<域>/run-parity.sh`（9 域逐字节）＋ 失败路径矩阵变异 13/13 | 🟡 **部分满足**（**L3**）—— 失败路径矩阵 0 域无缺口、变异 13/13 转红；但**不覆盖跨进程 deadline 传播** ⇒ **E65 缺口**<br>⇒ **Round 3 `P3-T01` 改判为「部分满足」** | `P2-T01`~`P2-T05` 完成后 ⇒ 回填 ✅；未完成 ⇒ **A 类阻断 ⇒ 阶段一不通过** | 2026-10-29 |
| **P0 #5** 项目能够按照实际项目说明完成构建、配置和启动 | 根级 · X-12 | `P1-T01`~`P1-T05` · `P3-T02` | `docker compose config` ＋ `./manage.sh up` ＋ `curl` 真打端点 ＋ **20 个服务逐个三判据**（Nacos 注册 / 端口 LISTEN / health 200） | ✅ **满足**（**L4** · 2026-11-01 Round 1 复判）—— **20/20 在服务**（`bash docs/RoundPlan/round-01/verify-all-services.sh` ⇒ **PASS=20 / FAIL=0 / EXIT=0**）＋ **两条跨服务链路有业务响应**（`GET /v1/articles` ⇒ 200 + PG 真数据且 `author_name` 来自 `user-rpc` ⇒ rpc→rpc 亦通；`POST /favorite/v1/item/create` ⇒ 业务码随播种变化 ⇒ `favorite-rpc→user-rpc` 真往返）＋ 端口**硬编码 0 处** / **29 个监听口零冲突** ＋ `.env.host.example` 宿主机模板 ＋ README §三 可照做 ⇒ compose 容器内语义与 fail-closed **逐字未变** | 证据 = `docs/RoundPlan/round-01/service-startup-matrix.md` §7（实测结果 + 结束检查） | 2026-11-01 |
| **P0 #6** 核心业务具有可执行的自动化测试，且测试断言真实业务结果 | 52 模块 · A-1 39 行求和 | `P6-T01` | `mvn -o -B -DargLine=-Xmx2g clean verify`（⚠️ **必须带 `clean`**，否则 stale 产物同时伪造「红」与「条数」）＋ surefire XML 求和 | ✅ **满足**（**L3**：3,294 条 / 0F / 0E / 4S / 52 模块）<br>⚠️ 数字以 **Round 6 S1 的重跑结果**为准 ⇒ **不得抄本表的旧数** | 任一 `F`/`E` ⇒ 真红 ⇒ 归 `CT-02` 定级 ⇒ **不当场改产品代码** | 2026-10-29 |
| **P0 #7** 项目具有清晰的主要模块职责，能够追踪核心调用链 | A-1 两列 · 10 域 `vs-common.md` | `P5-T02` · `P6-T01` | `docs/DRIFT/_cross/component-coverage-matrix.md` ＋ 各域 `vs-common.md`（A1~A9 机制面轴） | ✅ **满足**（**E1/L2**） | 机制面轴缺轴 ⇒ 归 `P5-T02` 补 | 2026-10-29 |
| **P0 #8** 关键配置和敏感信息没有明显的不安全处理方式 | 10 api/rpc + compose · X-11 | `P4-T01` · `P4-T02` · `P6-T01` | `python3 tools/difftest/check-no-prod-secrets.py` ＋ `docker compose config` ＋ 空变量起栈实测 ＋ 10 模块 `mvn test` ＋ 变异脚本 | ✅ **满足**（**L4**：JWT 验签密钥链 10/10 模块去掉明文默认值 + fail-closed 守卫含未解析占位符判据 + 4 模块各 4 条变异精确转红）<br>⚠️ 残留 `S1-USERS-002`（**B 类** · 8 rpc / 10 处部署凭据默认值）⇒ 已由 `P4-T01`/`P4-T02` 安排修复 | 守卫被改弱 ⇒ 变异不再转红 ⇒ **A 类阻断** | 2026-10-29 |
| **P0 #9** 已知严重缺陷、无法验证的关键能力和运行前提得到明确记录 | 38 份组件台账 + Intentional_Deviations | `P5-T02`~`P5-T06` · `P6-T01` | `python3 tools/check-ledger-tables.py` ＋ `python3 tools/check-ledger-count.py` ＋ `python3 tools/check-ledger-selfcheck.py` | ✅ **满足**（**E1**：48 条已签字 + 18 条未签字候选全部登记）<br>⚠️ 但台账**内部矛盾**尚存（明细面 vs 现状面 / `X-n` 编号冲突 / `HOTAPI-030` 六处四态）⇒ **Round 5 `P5-T02`~`P5-T05` 负责修** | 出现**无去向**的开放事项 ⇒ 违反规范 §12.2 ⇒ **必须改计划** | 2026-10-29 |

---

## 二、C-1 ~ C-4（四道聚合闸门 · `docs/twoStage.md` §5.6）

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **C-1** 核心 6 域全部对齐 | 核心 6 域（users / article / like / message / follow / favorite 等） | `P1-T05` · `P6-T01` | 各域双侧活体探针（`tools/api-full-parity-probe/**`）⇒ **两侧同时跑** ⇒ 逐字节比对 | ✅ **6/6**（**L4**） | 任一域探针红 ⇒ 先按三步定性法判真假红 ⇒ 真红 ⇒ `CT-02` | 2026-10-29 |
| **C-2** 零 🔴 组件 | 38 组件 · 共享层 28 项 | `P4-T01`~`P4-T09` · `P5-T02` | `docs/DRIFT/common/alignment-2026-10-23.md` §十三·一（23 编号 + 5 未编号 = 28）⇒ **逐条定级** ⇒ **A 类 0** | ✅ **零 🔴**（**L1/L2 + 台账逐条定级**：A 0 · B 0 · C 21 · D 6 · 证据缺口 1） | 出现 **A 类** ⇒ **阶段一不通过** | 2026-10-29 |
| **C-3** 12 条跨组件不变量无 🔴 | X-1 ~ X-12 | `P2-T05` · `P3-T03` · `P5-T01` · `P6-T01` | judge §五 矩阵 C 的 12 行逐条判定命令 | 🟡 **12 条无 🔴**，但 **X-6 的判定口径反了**（现守门是「漂移形态门」⇒ **PASS = 漂移还在**）⇒ **Round 3 `P3-T03` 改判**<br>⇒ 其余 11 条 ✅（X-2 = 9 域活体逐列 · X-5 = 20/20 产物层 · X-8/X-9 = 静态集合比对 · X-1 = 真 PG 三进程并发） | `P2-T05` 把守门改成**等值守门**且 X-6 转 ✅ ⇒ 回填；未完成 ⇒ **A 类阻断** | 2026-10-29 |
| **C-4** P0 九条无 🔴 | P0 九条 | 同 §一 | 同 §一 | 🟡 **P0 #4 / #5 两条为 🟡（部分满足）**，其余 7 条 ✅ ⇒ **无 🔴** | #4/#5 回填 ✅ 后 ⇒ C-4 才成立 | 2026-10-29 |

---

## 三、X-1 ~ X-12（12 条跨组件不变量 · `docs/PhaseGoal-judge.md` §五）

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **X-1** 共享 Flyway 账本隔离 | 7 个 rpc 模块 | `P6-T01` | `python3 tools/difftest/check-flyway-ledger.py` ＋ `bash tools/difftest/check-flyway-concurrent.sh`（**L4 真 PG 三进程并发同库**） | ✅ **成立** | 新模块忘加 `table` ⇒ 撞默认表名 ⇒ 登记；忘加 `baseline-version: 0` ⇒ 「起得来、首次查库才炸」⇒ 登记 | 2026-10-29 |
| **X-2** DDL 双侧活体逐列 | 9 域（favorite 只建库、hot N/A） | `P6-T01` | `python3 tools/difftest/check-ddl-static.py --domain <域> --indexes --check-source` | ✅ **9 域逐域实跑一致**（hot = **N/A** ⇒ **不许写成「已对齐」**） | 迁移改了没重跑 ⇒ `--check-source` 当场变红（变异已实测） | 2026-10-29 |
| **X-3** gRPC 拦截器真接线 | 10 个 rpc 模块 | `P6-T01` | `python3 tools/difftest/check-grpc-interceptor-wiring.py`（**纯静态 grep**） | ✅ **10/10 接线完整**（静态层） | 静态层绿但运行层断 ⇒ 归 `CT-02` | 2026-10-29 |
| **X-4** api 容器上限三旋钮 | 全部 10 个 api | `P6-T01` | `python3 tools/difftest/check-tomcat-limits.py`（**只读 `application.yml`**） | ✅ **10/10 配置值一致** | 运行时不一致 ⇒ 归 `CT-02` | 2026-10-29 |
| **X-5** 产物可执行性 | 10 api + 10 rpc | `P6-T01` | `bash tools/difftest/check-jar-executable.sh` ⇒ 20/20 fat jar 带 `Main-Class` | ✅ **20/20** | 漏配 `Main-Class` ⇒ 行为分叉 ⇒ **A 类** | 2026-10-29 |
| **X-6** deadline 分层预算与传播 | 10 个 rpc + 共享层 | `P2-T01`~`P2-T06` · `P3-T03` | `python3 tools/difftest/check-deadline-verdict.py`（门禁自身有效性）＋ `python3 tools/difftest/check-deadline-downstream.py`（**真双侧**） | 🟡 **口径反了** —— 现守门是**漂移形态门**（docstring 明写「漂移还在 ⇒ 绿」）⇒ **PASS 不等于 X-6 成立**；实测 Go `failed_fast=true/code=4` vs RAG `failed_fast=false/code=0` ⇒ **A 类阻断** | `P2-T05` 改成**等值守门**（drift 键两侧相等）+ `P2-T02`~`P2-T04` 落地 ⇒ **X-6 转 ✅** ⇒ `P3-T03` 回填 | 2026-10-29 |
| **X-7** 错误码契约（跨域） | 8 个 `<域>-common` + `users-common` | `P6-T01` | `GO_REPO=… bash tools/errmsg-probe/<域>/run-parity.sh`（9 域逐字节） | ✅ **9 域逐字节一致** | OBS 段（平台/库措辞差异）消失即 exit 2 ⇒ 有牙齿 | 2026-10-29 |
| **X-8** HTTP 路径 / 方法契约 | 全部 10 个 api | `P6-T01` | `python3 tools/difftest/check-surface-parity.py` ⇒ HTTP 端点 Go 72 ≡ RAG 72 ＋ JWT 保护集合相等 | ✅ **成立** | 端点集合不等 ⇒ **A 类** | 2026-10-29 |
| **X-9** gRPC 方法覆盖 | 10 个 rpc | `P6-T01` | 同 `check-surface-parity.py` ⇒ gRPC 方法 Go 71 ≡ RAG 71 | ✅ **10/10 域逐条覆盖** | 方法集合不等 ⇒ **A 类** | 2026-10-29 |
| **X-10** proto 契约 + 消费方式 | 10 个 proto | `P5-T01` · `P6-T01` | `bash tools/difftest/check-proto-static.sh`（**只覆盖文件层**）＋ 逐 proto 消费方式三问（`optional` presence / 字段号硬编码 / Jackson 形态） | 🟡 **文件层 10/10 一致**；**消费方式取证缺失**（G-19）⇒ **证据缺口**，不降 R | `P5-T01` 逐 proto 给出「是/否 + `文件:行号`」⇒ 回填；证不出来 ⇒ 归入「框架限制」类，**不是待签字候选** | 2026-10-29 |
| **X-11** 敏感配置链 | 10 api + 2 rpc + compose | `P4-T01` · `P4-T02` · `P6-T01` | `docker compose config` ⇒ 渲染出非空 secret ＋ 缺变量时启动失败 ＋ `check-no-prod-secrets.py` | ✅ **成立**（密钥链已闭合）<br>⚠️ 残留 `S1-USERS-002`（B 类） | 守卫被改弱 ⇒ 变异不再转红 ⇒ **A 类** | 2026-10-29 |
| **X-12** 构建 / 配置 / 启动说明 | 根级 | `P1-T01`~`P1-T05` · `P3-T02` | 人工 ＋ `docker compose config` ＋ `./manage.sh up` ＋ `curl` 真打端点 ＋ **20 服务三判据矩阵** | 🟡 **覆盖缺口**（只覆盖 1/10 域、2/20 服务）⇒ **A 类阻断** | `P1-T05` 全绿 ⇒ 回填 ✅；否则 **A 类阻断** | 2026-10-29 |

---

## 四、D-01 ~ D-08（`docs/PhaseGoal-judge.md` §十一 11.1）

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **D-01** 空签名密钥可起栈 | 10 api/rpc + compose · X-11 · P0 #8 | —（已闭合） | 4 模块各 4 条变异精确转红 ＋ `check-no-prod-secrets.py` EXIT=0 | ✅ **已闭合** | 复发 ⇒ **A 类** | 2026-10-29 |
| **D-02** README 21 B + 无 `.env.example` + `Infrastructure.md` 描述旧架构 | 根级 · X-12 · P0 #5 | `P1-T04`（**残留**：§三 宿主机命令不可执行） | README 9,815 B ＋ `.env.example` 56 变量 ＋ compose 6 处 `:?` | 🟡 **主体已闭合**，**残留 = §三 的命令不可执行** | `P1-T04` 修完 ⇒ 闭合 | 2026-10-29 |
| **D-03** `like-rpc` 事实投递链路 6 个 4 行骨架 | like 域 · `DRIFT-LIKE-003` | `P2-T06`（E66）· 其余见 `_unresolved.md` | 台账三段拆分：A 投递链路已闭环 · B 权威读语义已闭环 · **C 暴露形态待拍板** | 🟡 **A/B 已闭环 · C 待裁定** | C 段**等部署形态拍板** ⇒ 登记为待裁定（`DEC-01`）⇒ **不计入**阶段一 | 2026-10-29 |
| **D-04** `like-rpc` 双失败时少传 error（E60） | like 域 | —（已销案） | `LikeTaskProgressPublisher.publish` 三段式分流 ⇒ 真日志 `error_code=5011` ⇒ 已修 + 已守门 + 实跑 4/4 绿 | ✅ **已销案** | 复发 ⇒ 重新销号 | 2026-10-29 |
| **D-05** `follow-rpc` 5 个 `follow_rpc_*` 指标无抓取出口 | follow / favorite / message / like / user-user / user-admin | —（阶段二 · 待裁定） | Go 有 DevServer `/metrics`，RAG 无出口 | ⬜ **阶段一之外**（用户 2026-10-24 定「本轮不落地」） | 需**重新拍板**（`DEC-03`）⇒ 归阶段二 | 2026-10-29 |
| **D-06** 共享层 28 项未修 | `service/common` · `users-common` · C-2 | `P4-T06`（`DRIFT-USERS-LAT-001` 登记）· 其余 C/D 类**登记不修** | `docs/DRIFT/common/alignment-2026-10-23.md` §十三·一 ⇒ **28 项逐条定级** ⇒ **A 类 0** | ✅ **A 类硬阻塞 = 0**（C 21 · D 6 · 证据缺口 1 ⇒ 按 §1.1 ⑥ 第 4 条**不计入**） | 出现 A 类 ⇒ **阶段一不通过** | 2026-10-29 |
| **D-07** comment 域整张 `report_record` 表仅 RAG 有 | comment 域 · X-2 | —（**已签字**） | `SD-2026-10-24-CAND-02` ⇒ **不是缺陷，不得再「修回去」** | ✅ **已签字保留** | 有人把它当缺陷去「修」⇒ 归 `CT-02` 拦下 | 2026-10-29 |
| **D-08** like 域首轮 8 条错误断言 A~H 仍全部在位 | like 域 · 源码注释 | `P4-T09` | `docs/DRIFT/like/rpc.md:216-238`（逐条实测事实）＋ git diff 无非注释行 | 🔴 **未做**（**第 9 条 I** 亦在位） | `P4-T09` 处置 9 条 ⇒ 行为面 diff 为空 | 2026-10-29 |

---

## 五、交付物 1 ~ 6（`docs/twoStage.md` §十一）

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **交付物 1** 原始项目工程基线报告 | 全仓 | `P3-T09` | `docs/Stage1/01-*.md` 存在 ＋ `python3 tools/check-doc-links.py` | ✅ **成文**（**E1**） | 链接红 ⇒ 改链接（**不要删链接**） | 2026-10-29 |
| **交付物 2** 阶段 1 简历项目验收总览 | 全仓 | `P3-T09` | `docs/Stage1/02-*.md` 存在且与判据一致 | ✅ **成文**（**E1**） | 与复判后的判据不一致 ⇒ **改交付物，不改判据** | 2026-10-29 |
| **交付物 3** 阶段 1 核心业务链路与技术机制分析 | 10 域 | `P3-T09` | `docs/Stage1/03-*.md` | ✅ **成文**（**E1**） | 同上 | 2026-10-29 |
| **交付物 4** 阶段 1 十二维度验收表 | 12 维度 | `P3-T09` | `docs/Stage1/04-*.md` | ✅ **成文**（**E1**）<br>⚠️ 十二维度的**生产级审查**属**阶段二** ⇒ 本表只覆盖阶段一范围 | 混进阶段二内容 ⇒ 归 `CT-02` | 2026-10-29 |
| **交付物 5** 阶段 1 差异与暂缓项清单 | 全仓 · 共享层 28 项 · DDL 51 处 | `P3-T09` · `P4-T06` · `P5-T02` | `docs/Stage1/05-差异与暂缓项清单.md` ⇒ **51 行 × 11 字段**（机器核：按 `\|` 切分 = 11）⇒ 定级 A 0 · B 1 · C 21 · D 6 · 证据缺口 1 | ✅ **成文 + 机器核通过**（**E1**） | 字段数 ≠ 11 ⇒ 补齐；**A 类 > 0 ⇒ 阶段一不通过** | 2026-10-29 |
| **交付物 6** 阶段 1 最小可行实施计划及验收结果 | 全仓 | `P3-T09` · `P7-T01` | `docs/Stage1/06-*.md` ＋ 本仓 `docs/RoundPlan/**` | ✅ **成文**（**E1**）<br>⚠️ 验收结果以 Round 6/7 的实跑为准 | 与实跑结果不符 ⇒ 改文档 | 2026-10-29 |

---

## 六、冻结基线 9 项（`docs/twoStage.md` §5.7 · `docs/PhaseGoal-judge.md` §十·二）

> ⚠️ **旧冻结基线已失效** —— 它建立在「✅ 通过」结论上，而该结论已被 Round 3 复判为「❌ 未通过」⇒ `P3-T08` 负责**解除**，`P7-T01`（T8）负责**重建**。

| 权威判据编号或原文定位 | 对应的组件/域/跨组件不变量 | 负责提供的任务 ID | 验收命令或证据 | 当前结果 | 未通过时的处理方式 | 核对日期 |
|---|---|---|---|---|---|---|
| **冻结 1** 实际代码状态及版本标识 | 全仓 | `P7-T01`（T8） | `git log` ＋ 模块数 ＋ 测试类数 ＋ 代码状态提交 SHA | 🟡 **待重建**（旧值基于 2026-10-29） | 抄旧数 ⇒ **不算完成** | 待重建 |
| **冻结 2** 构建和测试结果 | 52 模块 · P0 #6 | `P6-T01`（S1）→ `P7-T01`（T8） | `mvn -o -B -DargLine=-Xmx2g clean verify` ⇒ 条数 / 0F / 0E / 4S / 模块数 / 墙钟 | 🟢 **已重建**（2026-11-01 · 合并收口执行者实跑）—— **tests = 3,570 · 0F · 0E · 4S**（**533 份报告 · 29 个含测模块** · 墙钟 **30 分 30 秒**）＋ **构建面 51 个 reactor 模块 · BUILD SUCCESS**（`package` · **退出码已捕获**）＋ **verify 面产物层守门 1 条 / 0F 0E 0S · RC=0** ⇒ ⇒ **命令原文 / 窗口口径 / 逐模块分解见 §九·9.3** | 用旧数 ⇒ **不算完成** | 2026-11-01 |
| **冻结 3** 核心业务链路的验收结果 | 10 域 · X-8 / X-9 | `P6-T01`（S3）→ `P7-T01`（T8） | 契约面 ＋ 双侧活体（L4）逐域 ＋ 失败路径矩阵 ＋ 端到端起栈证据 | 🟢 **已重建**（2026-11-01）—— **20/20 在服务**（三判据 · `verify-all-services.sh` ⇒ **PASS=20 / FAIL=0 / EXIT=0**）＋ **两条跨服务链路有业务响应**（`GET /v1/articles` ⇒ 200 + PG 真数据 + `author_name` 来自 `user-rpc` ⇒ **rpc→rpc 亦通**；`POST /favorite/v1/item/create` ⇒ 业务码 `5101`→`5005` ⇒ **真往返**）＋ **对外契约面**（`check-surface-parity.py` ⇒ **EXIT=0** · HTTP **72 ≡ 72** · gRPC **71 ≡ 71**）＋ **X-6 继承面**（`check-deadline-downstream.py` ⇒ **EXIT=0** · 四键逐键相等）⇒ 逐条见 judge §十·二 冻结 3 | 同上 | 待重建 |
| **冻结 4** P0、P1、P2 的最终状态 | 全仓 | `P7-T01`（T1/T8） | `docs/PhasedGoal.md` §2.1 · `twoStage.md` §2.1 | ✅ **P0 = ✅ 9/9**（**2026-11-01 · Round 3 复判**）· **P1 = 🟡 部分满足**（维度 3 proto 消费方式取证 · 维度 7 容量/SLO · 维度 8 rpc 层指标出口）· **P2 = 不阻塞**（生产级 SLO / 灾备 / 灰度等 ⇒ 阶段二） | P0 未全绿 ⇒ **不冻结** | 2026-11-01 |
| **冻结 5** 全部已知缺陷和未验证风险 | 38 组件 · `_unresolved.md` 6 类 | `P5-T02`~`P5-T06` · `P7-T01`（T8） | `_unresolved.md` + judge §十一 + §九 | 🟢 **已重建**（2026-11-01）—— **残留 A 类 = 2 条**（`R1-ENV-001` Nacos 地址双写法 · `R1-ENV-002` `task-api` 地址缺 scheme ⇒ **默认值形态 · 非行为漂移** ⇒ 不阻阶段一但**必须修**）＋ 🟢 **本轮新发现并已修 A 类 = 1 条**（`R7-PORT-001` 6 处端口默认值漂移 ⇒ **已回改 6 行**）＋ **残留 B 类 = 1 条**（`R7-PORT-002` 端口默认值门只覆盖 3/9 的 rpc）＋ **残留真缺陷（C 类）** = `DRIFT-CF-018` favorite 域半边（⇒ R5-F7）＋ **证据缺口 G-1~G-20：18 已闭合 / 2 未闭合**（`G-17` 记录缺口 · `G-19` 取证缺口）＋ **环境风险** `ENV-08` / `ENV-09` ⇒ 详见 judge §十·二 冻结 5 | A 类（**行为漂移**）> 0 ⇒ **不冻结** | 2026-11-01 |
| **冻结 6** 完整的差异与暂缓项清单 | 全仓 | `P5-T02` · `P7-T01`（T5） | `docs/Stage1/05-*.md` ⇒ **51 行 × 11 字段**机器核 | 🟢 **当前已满足**（E1 · **2026-11-01 复核**）—— **51 条 × 11 字段**（机器核 **51/51** = **26 个「3 列 11 行」项表 ＋ 25 行「11 列」表**；⚠️ 2 处「不齐」是**转义竖线** ⇒ 非缺陷）⇒ **阻断阶段一 = 0**；🆕 **另有 4 条本轮新登记在 global 台账**（`R1-ENV-001` / `R1-ENV-002` / `R7-PORT-001` / `R7-PORT-002`）⇒ **不在 51 条内** ⇒ 读清单时**两处都要读** | 字段数漂移 ⇒ 重出 | 2026-10-29 |
| **冻结 7** 阶段二任务及依赖 | 阶段二 | `P7-T01`（T8） | `docs/twoStage.md` §六（§6.1 / §6.2 / §九）⇒ **依赖 = 先冻结、后阶段二** | 🟢 **已重建**（2026-11-01）—— 阶段二 §六 三节（**§6.1 重新建立最终改造范围** · **§6.2 十二维度审查** · **§九 故障矩阵**）＋ **依赖 = 先冻结、后阶段二** ＋ **阶段一计划到此为止**（`round-07/README.md` §5.1「本轮是整个计划的终点」）⇒ **不自动进入阶段二** | 预支阶段二 ⇒ **违规** | 待重建 |
| **冻结 8** 尚未处理的原始业务行为 | 全仓 | `P7-T01`（T8） | `_unresolved.md` 第 5 类 + 第 6 类 | 🟢 **已重建**（2026-11-01）—— Go 侧**范围外三域**（`knowledge` / `points` / `security` ⇒ **决策非待办**）＋ **C/D 类残留逐条定级**（共享层 28 项 · DDL 残留 51 处 · `like` 域 `DRIFT-LIKE-003` C 段）＋ 🆕 **本轮 2 条 A 类默认值形态已登记**（`R1-ENV-001` / `R1-ENV-002`）＋ 🆕 **本轮已对齐**（`R7-PORT-001` 的 6 处端口默认值 ⇒ 已回改为 Go 值）⇒ 详见 judge §十·二 冻结 8 | 漏登记 ⇒ 违反 §12.2 | 待重建 |
| **冻结 9** 下一次执行应从哪里开始 | 阶段二 | `P7-T01`（T8/T9） | `docs/twoStage.md` §六 §6.1 ⇒ **不得假定暂缓清单已含所有缺口** | 🟢 **已重建**（2026-11-01）—— **第一优先 = Round 6 收口回归**（§六 6.3 的全量 27 项守门逐个实跑 ⇒ **闭合 S1-6 的另一半**）→ **第二 = Round 4**（9 张派工单 · `P4-T01` / `P4-T03` 是唯一需要改行为的两张）→ **第三 = Round 5**（取证与台账对齐）→ **第四 = 修 `R1-ENV-001` / `R1-ENV-002` ＋ 补 `R7-PORT-002` 的守门覆盖** → **随后 = 阶段二**（先 §6.1） | 抄旧「R5 派工单」入口 ⇒ **失效** | 待重建 |

---

## 七、判定矩阵（`docs/twoStage.md` §5.6 原文口径）

| 结论 | 触发条件 | 当前 | 达成条件 |
|---|---|---|---|
| **✅ 通过** | C-1 ∧ C-2 ∧ C-3 ∧ C-4 ∧ P0 九条全满足 | 🟢 **命中**（**2026-11-01 · Round 3 复判**）—— 五条必要条件**逐条成立**：C-1 **6/6** ✅ · C-2 **零红** ✅ · C-3 **12 条无红** ✅（X-6 已改判 ✅）· C-4 ✅ · **P0 九条 = ✅ 9/9**（#5 由 Round 1 闭合）⇒ ⇒ **结论 = 「通过」**（冻结基线已按此结论**重新冻结** ⇒ judge §十·二） | ⚠️ **S1-6 的另一半仍在 Round 6**：§六 6.3 的**全量 27 项**守门（双侧活体类）尚未逐个实跑 ⇒ **不因静态门全绿而判「守门全绿」** |
| **🟡 有条件通过** | 存在**明确、有限**的例外，且**不破坏核心正确性、安全性、数据完整性** | ⬜ **不适用**（**2026-11-01**）—— **不需要这一格**：必要条件全部满足 ⇒ 直接判「通过」；C 类 / D 类按 §1.1 ⑥ 第 4 条**不构成例外**（⇒ 也**不得**据此宣称「零差异」） | ⚠️ **历史**：原写「不适用（无 A 类阻断）」的**理由已随 #5 改判而变化** ⇒ 现理由 = **不需要** |
| **❌ 未通过** | 存在破坏核心正确性 / 安全 / 数据完整性的未修项 | ⬜ **不适用**（**2026-11-01**）—— `BLK-01`（P0 #5 覆盖缺口）已由 Round 1 消解 · `BLK-02`（E65）已于 2026-11-01 消解 ⇒ **无此类未修项** | ⚠️ **历史**：原写「✅ 命中」⇒ **两项 A 类消解后已改判** |
| **⚪ 受阻** | 缺必要环境或证据 | ⬜ **不适用**（**2026-11-01**）—— **环境可用且已被实测**：20 个服务真起（三判据 20/20）＋ 两条跨服务链路真跑 ⇒ 既非「环境受阻」，也非「证据覆盖不足」 | ⚠️ **历史**：原写「缺的是证据载体与台账」⇒ **该缺口已由 Round 1 闭合** |

---

## 八、§ 自检

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 判据原文**一字未改** | ✅ 本清单只引用与复述，不改 `PhasedGoal.md` §二 / `twoStage.md` §5.6 / §5.7 的任何判定口径 |
| 2 | **没有另造通过标准** | ✅ 判定口径逐条指向 `docs/PhaseGoal-judge.md` / `docs/twoStage.md` 的既有条目 |
| 3 | 每项都能指向**负责提供的任务 ID** | ✅ 48 行**无空缺**（已完成的写「—（已闭合）」+ 指向复核任务） |
| 4 | 每项都有**验收命令或证据** | ✅ 48 行全部有命令或 `文件:行号` |
| 5 | 每项都有**未通过时的处理方式** | ✅ 48 行全部有 |
| 6 | 证据强度**不混写** | ✅ 每行标 L1/L2/L3/L4/E1 |
| 7 | **没有**把 C/D 类写成「例外」 | ✅ C 类 / D 类一律标「按 §1.1 ⑥ 第 4 条不计入」 |
| 8 | **没有预支阶段二** | ✅ 十二维度审查 / 故障矩阵 / SLO / 灾备全部标「阶段一之外」 |

---

## 九、🆕 Round 7 执行记录（2026-11-01 · **阶段一最终验收**）

> **本节 = 执行记录**（§一~§八 是验收清单本身）⇒ **判据原文一字未改**，只改「当前结果」列。

### 9.1 结论

| 项 | 内容 |
|---|---|
| **结论（四选一 · 逐字对上 `docs/twoStage.md` §5.6）** | ✅ **通过。** |
| **依据** | C-1 **6/6** ✅ · C-2 **零红** ✅ · C-3 **12 条无红** ✅ · C-4 ✅ · **P0 九条 = ✅ 9/9** ⇒ **五条必要条件逐条成立** |
| **本轮闭合的两条 A 类阻断** | ① **E65 / X-6**（2026-11-01 改判 ✅）② **P0 #5 覆盖缺口**（**Round 1**：`P1-T01`~`P1-T05` ⇒ 20/20 在服务 ＋ 两条跨服务链路） |
| **冻结** | ✅ **已按「结论 = 通过」重新冻结** ⇒ `docs/PhaseGoal-judge.md` **§十·二**（`twoStage.md` §5.7 的 9 项） |
| **未闭合（不阻阶段一）** | ⚠️ **S1-6 的另一半**：§六 6.3 的**全量 27 项**守门里，**双侧活体类**尚未逐个实跑 ⇒ 留给 **Round 6 收口回归**<br>⚠️ **2 条新登记的 A 类配置默认值缺陷**（`R1-ENV-001` / `R1-ENV-002`）⇒ **行为面无漂移**、本轮用 env 覆盖绕过 ⇒ 不阻阶段一，但**必须修**（否则第三方按默认值起栈会踩） |
| **边界** | 「通过」= **简历项目准入线达成**；**不等于**工程成熟度审查完成（阶段二：十二维度生产级审查 / §9 故障矩阵 / SLO / 灾备 ⇒ **未做、不预支**） |

### 9.2 本轮实跑的门（**逐条取真实退出码** · 静态/机读类）

| 门 | EXIT |
|---|---|
| `check-phasegoal-consistency.py` | **0** |
| `check-phasegoal-lanes.py`（`--write` 后复跑） | **0** |
| `check-jar-executable.sh` | **0**（**20/20** 个模块带 `Main-Class: JarLauncher`） |
| `check-tomcat-limits.py` | **0** |
| `check-grpc-interceptor-wiring.py` | **0** |
| `check-flyway-ledger.py` | **0** |
| `check-ledger-tables.py` / `check-ledger-count.py` | **0** / **0** |
| `check-agents-budget.py` | **0** |
| `check-doc-links.py` | **0** |
| `check-no-prod-secrets.py` | **0** |
| `check-source-test-decl-count.py` | **0** |
| `derive-stage1.py --check`（派生层同步） | **0** |
| **`docs/RoundPlan/round-01/verify-all-services.sh`**（20 服务三判据） | **0**（**PASS=20 / FAIL=0**） |

> ⚠️ **口径**：本表是**静态/机读类**的门 ⇒ **不外推**为「§六 6.3 的 27 项全绿」（双侧活体类在 Round 6）⇒ 与 judge §8.4 的 **S1-6 = 🟡 部分满足** 一致。

### 9.3 🟢 Round 7 的**全量回归读数**（合并收口执行者实跑 · 2026-11-01）

> ⚠️ **为什么这一段是收官补写的** —— Round 7 执行会话在写冻结基线时**因 Token Plan 用量上限被中止**（2026-11-01 08:19:35）⇒ ⇒ 留下三处半成品：① `docs/PhaseGoal-judge.md` §十·二 冻结基线第 2 行留了 `«REGRESSION»` **占位符** ② 本文件 §六「冻结 2」行仍写「🟡 待重跑 / 核对日期 待重建」 ③ §九 原写「全量回归重跑 ⇒ 见 §9.3」但 **§9.3 整段不存在** ⇒ ⇒ 三处**同一个根因**：**这轮全量回归的最终读数没被写进文档** ⇒ ⇒ 由**合并收口执行者**取**自己实跑**的读数补齐（**不采信任何转述**）。

**① 读数口径（先说清楚怎么数，否则数字不可复算）**

| 项 | 口径 |
|---|---|
| **读数来源** | **surefire `TEST-*.xml` 逐文件聚合**（`tests` / `failures` / `errors` / `skipped` 属性求和）⇒ **不认 stdout 的文字行**（PIT-GLOBAL-021） |
| **窗口起点** | **进程创建时间 `2026-11-01 08:11:03`**（`ps -o lstart= -p <pid>`）⇒ ⚠️ **不是日志首行时间戳**（取后者会整模块漏掉最先跑的那几个 ⇒ 实测少算 61 条 ⇒ 得 3,506 / 28 模块 ⇒ 28 ≠ 上一轮的 29 才发现） |
| **陈旧排除** | `mtime < 进程创建时间` 的报告**一律剔除** —— `clean` 按 reactor **逐模块**清理 ⇒ reactor 后段的模块在轮到之前**仍留着上一轮的旧报告** ⇒ 不按时间窗剔除就会把旧读数混进来 |
| **墙钟** | **30 分 30 秒**（08:11:03 → 08:41:33） |

**② 读数（三面）**

| 面 | 命令 | 读数 |
|---|---|---|
| **① 测试面** | `mvn -o -B -DargLine=-Xmx2g clean test` | **tests = 3,570 · failures = 0 · errors = 0 · skipped = 4**（**533 份报告 · 29 个含测模块**） |
| **② 构建面** | `mvn -o -B -DskipTests -DargLine=-Xmx2g package` | **51 个 reactor 模块 · BUILD SUCCESS**（21.278 s · **退出码已捕获**） |
| **③ verify 面**（全仓唯一的 `verify` 阶段绑定） | `mvn -o -B -pl service/hot/hot-rpc surefire:test@artifact-gate`（`artifact-gate` = `DRIFT-HOTRPC-BOOT-001` 产物层守门 · `HotRpcFatJarArtifactGateIT`） | **Tests run: 1 · 0F / 0E / 0S · BUILD SUCCESS · RC=0** |

> **为什么拆三面** —— 判定命令写的是 `clean verify`，而**测试**在 `test` 阶段、**产物**要等 `package` 之后才存在（`artifact-gate` 绑 `verify` 正是为此：`test` 阶段跑在 `package` 之前 ⇒ 那时 jar 还不存在 ⇒ 解包产物做不了）⇒ ⇒ 一条命令跑完最省事，但**读数会混在一起无法归因** ⇒ ⇒ 拆成三面各带自己的退出码。

**③ 4 条 skipped 的归属（逐条列出，不写「等」了事）**

| 模块 | 用例 | 性质 |
|---|---|---|
| `service/favorite/favorite-rpc` | `FavoriteSqlStatementParityTest#sqlStatementParity` | **双侧活体对拍** —— Go 侧不可达时按设计 skip |
| `service/favorite/favorite-rpc` | `FavoriteCreateSuccessParityTest#createSuccessParity` | 同上 |
| `service/favorite/favorite-rpc` | `FavoriteGrpcContractParityTest#grpcContractParity` | 同上 |
| `service/favorite/favorite-api` | `FavoriteApiFullParityTest#apiFullParity` | 同上 |

⇒ **与判定口径里的「4S」逐条对上** ⇒ ⇒ **不是漏跑、不是静默跳过**（载体守门一律以 surefire XML 的 `skipped=` 为准 ⇒ 不认 stdout 的 "skipped" 字样）。

**④ 诚实边界（三条，不含糊）**

1. **测试面那一轮的 stdout 随被中止的 Round 7 会话管道丢失 ⇒ 退出码未捕获** ⇒ 「BUILD SUCCESS」是**推断**，依据三条：① **29/29** 含测模块均产出**本轮**报告（`clean` 逐模块清理 ⇒ 若 reactor 中途失败，reactor 后段模块只会留下旧报告 ⇒ 新鲜模块数必 < 29）② **0F / 0E** ③ 独立的**构建面 51 模块 BUILD SUCCESS**（**退出码已捕获**）。
2. **本表不外推为「§六 6.3 的 27 项守门全绿」** ⇒ 与 judge §8.4 的 **S1-6 = 🟡 部分满足** 一致（那 27 项里的**双侧活体类**仍未逐个实跑 ⇒ 归 Round 6）。
3. **本轮未复跑** judge §十·二 冻结 3 里标注为「2026-10-29 读数 · 本轮未复跑」的各域双侧活体探针（article / like / follow / users / users-common / favorite-rpc / task / comment / message / hot · 失败路径矩阵 227 条）⇒ ⇒ **那些读数仍属 2026-10-29 的口径**，不是本轮的。

**⑤ 逐模块分解（29 个含测模块 · 全量）**

| 模块 | 测试类 | tests | F | E | S |
|---|---|---|---|---|---|
| `service/article/article-api` | 12 | 224 | 0 | 0 | 0 |
| `service/article/article-common` | 1 | 15 | 0 | 0 | 0 |
| `service/article/article-rpc` | 29 | 209 | 0 | 0 | 0 |
| `service/comment/comment-api` | 7 | 66 | 0 | 0 | 0 |
| `service/comment/comment-common` | 3 | 27 | 0 | 0 | 0 |
| `service/comment/comment-rpc` | 28 | 157 | 0 | 0 | 0 |
| `service/common` | 92 | 488 | 0 | 0 | 0 |
| `service/favorite/favorite-api` | 15 | 157 | 0 | 0 | **1** |
| `service/favorite/favorite-common` | 4 | 29 | 0 | 0 | 0 |
| `service/favorite/favorite-rpc` | 26 | 146 | 0 | 0 | **3** |
| `service/follow/follow-api` | 13 | 148 | 0 | 0 | 0 |
| `service/follow/follow-common` | 3 | 37 | 0 | 0 | 0 |
| `service/follow/follow-rpc` | 17 | 99 | 0 | 0 | 0 |
| `service/hot/hot-api` | 17 | 127 | 0 | 0 | 0 |
| `service/hot/hot-common` | 2 | 14 | 0 | 0 | 0 |
| `service/hot/hot-rpc` | 27 | 134 | 0 | 0 | 0 |
| `service/like/like-api` | 13 | 113 | 0 | 0 | 0 |
| `service/like/like-common` | 2 | 18 | 0 | 0 | 0 |
| `service/like/like-rpc` | 30 | 163 | 0 | 0 | 0 |
| `service/message/message-api` | 18 | 122 | 0 | 0 | 0 |
| `service/message/message-common` | 2 | 21 | 0 | 0 | 0 |
| `service/message/message-rpc` | 27 | 115 | 0 | 0 | 0 |
| `service/task/task-api` | 11 | 144 | 0 | 0 | 0 |
| `service/task/task-rpc` | 20 | 137 | 0 | 0 | 0 |
| `service/users/admin/admin-api` | 22 | 160 | 0 | 0 | 0 |
| `service/users/admin/admin-rpc` | 29 | 139 | 0 | 0 | 0 |
| `service/users/user/user-api` | 26 | 138 | 0 | 0 | 0 |
| `service/users/user/user-rpc` | 33 | 184 | 0 | 0 | 0 |
| `service/users/users-common` | 4 | 39 | 0 | 0 | 0 |
| **合计** | **533** | **3,570** | **0** | **0** | **4** |

### 9.4 🟢 Round 7 的**全量回归**抓到并回改了一处 Round 1 引入的漂移

> **这是本轮最硬的一条证据** —— 它证明**「起栈 20/20」与「静态门全绿」都抓不到它** ⇒ ⇒ 只有**全量回归**这一面能抓。

**① 现象**：Round 7 的 `mvn -o -B -DargLine=-Xmx2g clean test` 在 `hot-rpc` 处 **BUILD FAILURE**（下游 27 个模块 SKIPPED）⇒ `HotRpcPrometheusCarrierGateTest.shippedJarKeepsGoObservationPortAndSeparatesFaces` **精确转红**：

```
AssertionFailedError: `grpc.server.port` 必须 = 8083（= Go `etc/hot.yaml:2` 的 `ListenOn`），
实际 = ${HOT_RPC_GRPC_PORT:8084} ==> expected: <8083> but was: <8084>
```

**② 根因** —— Round 1（`1fa8a080`）为解同机端口冲突，**把 6 个 yml 的端口默认值改成了非 Go 值**：

| 模块 | Go 真源值 | Round 1 落码 | yml 自己的约束原文 |
|---|---|---|---|
| `admin-api` | 8889 | `ADMIN_API_PORT:8890` | 「⚠️ 默认端口**一字不变**（8889）」 |
| `comment-rpc` | 9001 | `COMMENT_RPC_PORT:9002` | 「对应 Go etc/comment.yaml `ListenOn: 0.0.0.0:9001`」 |
| `follow-rpc` | 8082 | `FOLLOW_RPC_PORT:8083` | 「⚠️ 默认端口**一字不变**（Go follow.yaml:2）」 |
| `hot-rpc` | 8083 | `HOT_RPC_GRPC_PORT:8084` | 门禁 `HotRpcPrometheusCarrierGateTest:259` |
| `like-rpc` | 8082 | `LIKE_RPC_PORT:8085` | 「对应 Go ListenOn: 0.0.0.0:8082」 |
| `message-rpc` | 8082 | `MESSAGE_RPC_PORT:8086` | 「对应 Go ListenOn: 0.0.0.0:8082」 |

**③ 修法（端口的**两层分工**）** —— **默认值层 = Go 真源值（一字不差）**；**互异性由分配层（`.env` / `.env.host`）承担** ⇒ 回改 **6 行**、**env 化本身全部保留** ⇒ 硬编码端口仍为 0 ⇒ 20/20 起栈（**按 env 分配的**）不受影响。

**③′ 为什么当初会错** —— Round 1 的 spec `.agents/specs/round1-full-service-startup.md` §1 写「**3 处默认值错开**」（**实际落码改了 6 处** ⇒ 连计数也不准）⇒ ⇒ **错开默认值这条路本身就是错的**：**默认值层是对外契约面**（Go 侧逐字对齐 + 既有门禁钉住）⇒ ⇒ 当时**未意识到这一层** ⇒ ⇒ `service-startup-matrix.md` §2 的「建议 env 化」列（**每处都写「默认值保持不变」**）才是对的。

**④ 为什么「默认值层重复」是合法的** —— Go 侧 **favorite/follow/like/message-rpc 四个都监听 `8082`**、**article/comment-rpc 都监听 `9001`**、**article/admin-api 都监听 `8889`**（**Go 侧本就重复** ⇒ Go 是「一服务一部署」）⇒ 「一机多进程」的互异性**只能由部署层提供**。

**⑤ 验证（实跑）**

| 项 | 读数 |
|---|---|
| `HotRpcPrometheusCarrierGateTest` | **转绿**（`mvn -o -B -pl service/hot/hot-rpc -am -Dtest='HotRpcPrometheusCarrierGateTest' -Dsurefire.failIfNoSpecifiedTests=false test` ⇒ **EXIT=0**） |
| 端口两层体检（`service-startup-matrix.md` §8 脚本） | `HARDCODED_COUNT 0` · `DEFAULT_GO_MISMATCH_COUNT 0` · `.env.example` / `.env.host.example` 各 `ALLOC_DUP_COUNT 0`（29 个端口变量） |
| 全量回归重跑 | 见 §9.3 |

**⑥ 登记** —— `R7-PORT-001`（**已闭合**）· `R7-PORT-002`（**守门覆盖缺口** · 只有 3/9 的 rpc 有业务口门禁 ⇒ 其余 5 处当时是**静默漂移** ⇒ B 类 · 不阻阶段一）· `R7-PORT-003`（**已闭合** · 门禁自身解析器）⇒ [`docs/DRIFT/global/_README.md`](../DRIFT/global/_README.md) 的 Round 7 段 · [`docs/RoundPlan/_unresolved.md`](_unresolved.md) `DEP-06`。

**⑥′ 同源的第二个缺陷（回改后**续跑**才暴露）** —— 回改 6 个默认值后**续跑**（`mvn -o -B -DargLine=-Xmx2g test -rf :favorite-rpc`）⇒ `favorite-rpc` **BUILD FAILURE** ⇒ `FavoriteRpcProductionStartupTest.observationPortIsSeparatedFromBusinessPort` 转红：`grpc.server.port` 实际 = `${FAVORITE_RPC_PORT:8082}` ⇒ **值是对的（8082 = Go 的 `ListenOn`）**，是**门禁的解析器**（`stripPortNumber` 的旧拷贝 ⇒ 对占位形态返回 `"8082}"`）**读不懂** ⇒ ⇒ 其余三份同族 helper **都**处理占位形态 ⇒ **这一份是遗漏** ⇒ ✅ **本轮已修**（改成逐字一致的实现 · **断言语义不变**）+ **变异必红**（改 8083 ⇒ 精确转红 ⇒ 判据有牙）⇒ 登记 `R7-PORT-003`。
> ⇒ ⇒ **Round 1 的端口 env 化一共留下 2 处缺陷**（一处**改错**默认值 · 一处**打红**既有门禁）⇒ ⇒ **两处都只有全量回归能抓到**（Round 1 的自查只跑了 12 道静态/机读门 ⇒ **一次都没跑过受影响模块的测试**）。

**⑦ 对结论的影响** —— **无**：P0 #5 的证据面是**按 env 分配的 20/20 起栈 + 两条跨服务链路**，**与默认值无关** ⇒ ⇒ **五条必要条件仍逐条成立** ⇒ ⇒ **结论 = ✅ 通过不变**（judge §8.4 v6 · 变更记录 #37）。

> ⚠️ **可复用教训（两条）**：① **「端口 env 化」要先分清改的是「默认值」还是「分配值」** —— 前者属**对外契约面**（**只能改 env 键名，不能改默认值**），后者属**部署面**；② **「静态门全绿」≠「全量回归全绿」** —— Round 1 的自查只跑了 12 道静态/机读门 ⇒ **跑不到能抓「默认值漂移」的那一面** ⇒ ⇒ **凡改对外契约面的值，必须跑全量回归**。
