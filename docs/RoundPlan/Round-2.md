# R2 · 活体证据补齐（**最重的一轮**）

> **目标**：**P0 #1 / #2 / #4** 三条 🟡 → ✅。
> ⚠️ 这三条**不是「实现缺陷」类，是「证据不足」类**（`PhaseGoal-judge.md` §九）⇒ **要补的是可复算的活体证据，不是改代码**。
> **前置**：R0（T-0.4 覆盖清单钉死 + T-0.5 tools 写权）。**后继**：R4。

---

## ⚠️ 本轮的三条铁律（每条都踩过坑）

1. **探针必须落仓** —— `/tmp` 里的脚本**不算证据**（`/tmp/likeprobe/` `/tmp/commonprobe/` `/tmp/probe.sh` 全部已消失 ⇒ 域级活体证据一次归零）。
2. **对拍前先「清状态」** —— users 域 43 场景里那 4 处差异**全是已签字 N-2**（状态没清 ⇒ 假漂移 ⇒ 白跑一轮）。
3. **对拍装置自己会骗人**（PIT-FOLLOW-016：端口撞车 / 时钟跳变 / 启动时序 / 端口未释放）⇒ **探针自身要被验**（⇒ 变异测试：故意破坏一次 ⇒ 探针必须红）。

---

## T-2.1 🔴【G3】A-1 —— `article` 域双侧活体对拍

**为什么它是硬阻断**：`article` 域当前 **0 条**双侧活体 ⇒ 域判定 R2/🟡 ⇒ **C-1 核心 6 域只有 5/6** ⇒ **S1-2 不成立**。

**前提已就绪**：`Sea-etcd` **49319→2379** 转发已在跑 ⇒ `PhaseGoal-judge.md` §九 G-3 写的「⚫ 受阻（宿主端口未映射）」**前提已过期** ⇒ G-3 应销号，不是「受阻」。
（实测：Go rpc 已挪到 **9002**、RAG 侧 rpc 硬编码 **9001** ⇒ 同机双侧并行必须错端口；RAG 侧 `application.yml` 的 `grpc.server.port` **硬编码 9001、不可 env 覆盖** ⇒ 端口错开只改 Go 侧副本。）

**要做的**：Go 侧 `article-api`/`article-rpc` 与 RAG 侧同时起 ⇒ **两个独立 PG 容器**（RAG 侧 Flyway 自建 schema）⇒ **播种同一行** ⇒ 对比 `GET /v1/articles` + `GET /v1/article/:id` 的**逐字段/逐字节**输出 ⇒ 结论入台账。

- **落点**：`tools/<kind>/article/`（按 T-0.6 口径）· `docs/DRIFT/article/vs-common.md` §6
- **写权**：**G3**（探针目录写权按 T-0.5 裁定）
- **验收**：① 两侧同时跑的输出逐字节比对记录在案 ② **探针变异测试**：故意改一侧 ⇒ 探针必红 ③ `PhaseGoal-judge.md` G-3 销号 + `article` 域 R 档重算
- ⚠️ **不要**用空库当证据（「两侧都空」分不出「相同」与「都空」）⇒ **必须播种同一行**

---

## T-2.2【G3】H-A1 —— hot proto 消息体 wire 字节双侧探针落码

**做法**：照 favorite 域骨架复制 ⇒ `tools/proto-wire-probe/favorite/{run.sh,go/main.go}` ⇒ hot 版。

- **落点**：`tools/proto-wire-probe/hot/`
- **写权**：**G3**（`tools/**` 按 T-0.5 裁定）
- **验收**：hot 域 wire 字节双侧逐字节一致 + **变异必红** + 结论入 `docs/DRIFT/hot/vs-common.md`

---

## T-2.3【G3】F-1 —— `favorite-rpc` 整域双侧活体对拍

**现状**：Go 侧 `etcd` / Kafka **仍拉不起来** ⇒ 环境受限。

- **落点**：`tools/<kind>/favorite-rpc/` + `docs/DRIFT/favorite/vs-common.md`
- **写权**：**G3**
- **验收**：① 跑得起来 ⇒ 双侧活体结论入台账 ② **跑不起来 ⇒ 写「受阻 + 可解除条件」（缺什么、起什么命令）** ⇒ ⚠️ **绝不写「已对齐」**
- ⚠️ `favorite` 域已有 10 探针（api 层）+ `favorite-proto` wire 探针 ⇒ 本条是 **rpc 层**补齐 ⇒ 别与已有证据混记

---

## T-2.4 🔴【G2】like 域事实投递链路复算 + `DRIFT-LIKE-003` / `E60` 销案

**现状**：G2 已落码（`46ae663`：`E60`/`E59` 销案 + 003 authority 侧 Java 化 + proto 契约网 + Kafka listener 装配守门）⇒ **代码已修**；
但 `PhaseGoal-judge.md:200/:202` 仍写 `like-rpc` / `like-proto` 🟡，`:395-396`/`:408`/`:416` 仍把 `E60`/`003` 列为「未通过」⇒ **判据未更新**。

**要做的**：
1. **重建并落仓** like 域双侧探针（`/tmp/likeprobe/{main.go,RagProbe.java,NullElem.java}` **已消失** ⇒ G-2）⇒ ⚠️ **须先复核修复仍在**（「已修 ≠ 已验证」）
2. 复算事实投递链路（`DRIFT-LIKE-003`）与双失败少传 error（`E60`）⇒ 确认销案成立
3. **把销案结论写进基线**（`PhaseGoal-judge.md` §一 B5/B6/B7 由主线改 ⇒ G2 只提供复算证据）

- **落点**：`tools/<kind>/like/` · `docs/DRIFT/like/vs-common.md` · `docs/DRIFT/like/rpc.md` §12.2
- **写权**：**G2**（域侧）
- **验收**：① 探针**在仓内**（`git ls-files tools/ | grep like`）② 变异必红 ③ `E60` / `E59` / `DRIFT-LIKE-003` 三条销案结论各带**证据强度**

---

## T-2.5【G1】follow 域双侧探针落仓（G-1）

**现状**：台账「**52/52 逐字节一致**」依赖 `/tmp/probe.sh` / `probe_data.sh` / `probe_ext.sh` / `cmp.sh` / `u3d_probe.sh` / `u3c_probe.sh` / `blackhole3.py`（**全在 /tmp** ⇒ 未进版本控制）⇒ 台账自己写明「故障注入装置应从 `/tmp` 提升为仓库资产 —— **本轮未做**」。

- **落点**：`tools/difftest/follow/`（或按 T-0.6 统一口径）
- **写权**：**G1**（`tools/**` 按 T-0.5 裁定）
- **验收**：① 脚本**在仓内**（`git ls-files` 可见）② `follow-api` / `follow-rpc` 的 R 档可复算（声明 → 可复算）③ **变异必红**
- ⚠️ **FAILING-010**：探针用**固定 `-o` 文件** ⇒ 请求失败时读到的是**上一次的残留 body** ⇒ 落仓前先修这个

---

## T-2.6【G1】users-common 176 条活体探针落仓（G-4）

**现状**：`/tmp/commonprobe/**` **已消失** ⇒ 176 条活体证据不可复算。

- **落点**：`tools/<kind>/users-common/`
- **写权**：**G1**（⚠️ `service/users/users-common/**` 的**代码**归 G4，但**探针**在 `tools/**` ⇒ 按 T-0.5）
- **验收**：探针在仓内 + 复算通过 + 变异必红 + `users-common` 的 R 档从「声明」升级为「可复算」

---

## T-2.7【G3】`article` + `hot` 域独立失败路径矩阵（G-13 剩 2 域）

**判据要求**（P0 #2 原话）：「**每端点 ≥1 条失败路径测试**」只能证明「存在失败路径处理」，**不能**证明「四项都有效」⇒ 矩阵要**按端点 × 失败维度**铺开。

- **落点**：`docs/DRIFT/article/vs-common.md` · `docs/DRIFT/hot/vs-common.md` + 对应模块测试
- **写权**：**G3**
- **验收**：每个端点 ≥1 条**真实断言到错误码/状态码**的失败路径 + 变异必红
- ⚠️ `article` 是**失败路径最密集**的域（下载 / 上传 / 审核 / S-5）⇒ 不要只做 4 条代表

---

## T-2.8【G2】`task` 域独立失败路径矩阵（G-13 剩 1 域）

- **落点**：`docs/DRIFT/task/vs-common.md` + `service/task/**` 测试
- **写权**：**G2**
- **验收**：同 T-2.7

---

## T-2.9【G4】共享层 `breaker-probe` 覆盖域复核

**理由**：P0 #4 把 `shared-layer` 列在已覆盖域内 ⇒ **只需复核，不需新建**（⚠️ 复核结论要落到台账，否则等于没复核）。

- **落点**：`docs/DRIFT/global/vs-common.md`
- **写权**：**G4**
- **验收**：`bash tools/breaker-probe/run-parity.sh` 实跑 + 三档口径结论入台账

---

## T-2.10【主线】基线订正：P0 #4 覆盖清单 + §九 证据缺口销号

- **落点**：`docs/PhaseGoal-judge.md` §七 P0 #4 行 · §九 G-1 / G-2 / G-3 / G-4
- **写权**：**主线**
- **验收**：`python3 tools/difftest/check-phasegoal-consistency.py` ⇒ **EXIT=0**；§九 相应条目标「已闭合」并带证据强度与日期
- ⚠️ **「已改」≠「已验证」**：销号必须写**证据强度**（双侧活体 = E4）⇒ 空口销号 = 假绿

---

## T-2.11【裁定】P0 #2 的「权限控制显式用例」是不是 N/A

**现象**：P0 #2 要补「① 4 个域的失败路径矩阵 ② 权限控制的**显式**用例（**admin 域有，其余域无「权限」概念**）」⇒ ② 这条按其自身描述就是 **N/A**（其余域无权限概念）⇒ **须裁定**：是「其余域不适用」还是「其余域也要造权限用例」。

- **落点**：`docs/PhaseGoal-judge.md` §七 P0 #2 行（**主线写**）
- **验收**：P0 #2 的两条待补项**逐条有结论**（做了 / N/A + 理由）⇒ 不留「待定」

---

## R2 停止点

- ☐ **P0 #1 ✅**（like 事实投递有活体复算 + article 双侧活体 ≥1 条可比对场景 + **播种过数据**）
- ☐ **P0 #2 ✅**（3 域失败路径矩阵补齐 + T-2.11 裁定落地）
- ☐ **P0 #4 ✅**（覆盖清单 = 实跑清单 + article 域有无失败路径探针有明确结论）
- ☐ `article` 域 R 档升 R3+ ⇒ **C-1 核心 6 域 6/6**（**S1-2 成立**）
- ☐ §九 G-1 / G-2 / G-3 / G-4 **销号**（各带证据强度 + 日期）
- ☐ 所有新探针**在仓内**（`git ls-files tools/ | grep -E 'article|like|follow|users-common|hot|favorite'`）且**变异必红**
- ☐ `check-phasegoal-consistency.py` ⇒ EXIT=0

⚠️ **R2 跑出来的新欠账一律登记到 R5，不要当场扩范围**（本轮只做「让三条 P0 变绿」所必需的事）。
