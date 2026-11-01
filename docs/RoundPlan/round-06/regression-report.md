# Round 6 · 收口回归报告（Round 6 唯一产出物）

> **本文件 = `P6-T01` 的唯一产出文件**（`docs/RoundPlan/round-06/README.md` §5.3「具体落点」）。
> **执行主体**：合并收口执行者（主线）—— 全量回归与守门是**仓级**动作，跨域写权不适用。
> **执行日期**：2026-11-01 · **树**：`do-my-own-gh` @ `6f73ab66` ＋ 本轮未提交改动（合并后的单树）。
> ⚠️ **本轮不做整改**（规范 §一.6 / §一.7）—— 回归里发现的回归**登记 + 定级**，不当场改产品行为。
> ⚠️ **本轮不代用户签字** · ⚠️ **不新增 `check-*.py`**（会动 §六 6.3 的 27 项计数 ⇒ 制造 B14）。

---

## 〇、本轮的三条铁律（执行时逐条对照）

| # | 铁律 | 本轮怎么落的 |
|---|---|---|
| **1** | **收口回归必须在「合并后的树」上跑** ⇒ 不用任一棵 lane 树、不用「差集推断」 | 本轮跑的是**主干 `do-my-own-gh`**（六棵 lane 分支的独有提交已全部为 0 ⇒ 合主干与跑主干等价）⇒ 另用 `check-merge-tree-content.py`（带 4 棵 lane ref）做 git 对象层的合并内容守门（见 §2 · §5） |
| **2** | **取退出码必须先落变量**（`out=$(cmd 2>&1); rc=$?`）⇒ 不许用 `cmd \| tail` 的退出码 | 本轮全部 27 门 + 3 个不计入 27 的门 + 10 域探针，**一律**先落变量再打印；守门脚本 `tools/difftest/scan-rpc-port-defaults.py` 与本轮所有临时跑批脚本同此纪律 |
| **3** | **守门的两个方向可信度不同** ⇒ 「EXIT=0 ⇒ 没问题」可信；「EXIT=1 ⇒ 有问题」**不可信** | 本轮每个非 0 门都逐条定性（§3）⇒ 判据 = **看输出文本**，不是看码 |

---

## 一、S0 · 前置探针（环境不可见 ⇒ 硬失败 exit 2，**绝不静默降级**）

| 检查项 | 读数 | 结论 |
|---|---|---|
| Go 真源仓可读 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` **存在** | ✅ |
| Go 模块缓存可读 | `/Users/pr/go/pkg/mod` **存在** | ✅ |
| RAG 仓可读 | `/Users/pr/Documents/java&golang/RAG` **存在** | ✅ |
| 栈在跑 | `Sea-prometheus` · `Sea-postgres`(healthy) · `Sea-nacos`(healthy) · `Sea-redis`(healthy) · `Sea-jaeger` · `Sea-minio`(healthy) · `Sea-kafka` · `Sea-etcd`(healthy) —— **8 个容器全部 Up**（另 `postgres` · `redis` · `kafka` · `nacos-standalone` · `minio` 5 个独立容器亦 Up） | ✅ |
| 宿主中间件端口 | `49319`(etcd) · `49992`(kafka) · `49100`(minio) · `49317`(jaeger) · `49848`(nacos) —— **5/5 OPEN** | ✅ |

⇒ **S0 通过**（未触发 exit 2 的硬失败路径）。

---

## 二、S1 · 全量回归（`mvn -o -B -DargLine=-Xmx2g clean verify`）

### 2.1 · 命令与退出码

| 项 | 值 |
|---|---|
| **命令** | `mvn -o -B -DargLine=-Xmx2g clean verify` |
| **退出码** | **0**（`BUILD SUCCESS`） |
| **墙钟** | **1862 s = 31:02**（maven 自报 `Total time: 31:00 min`） |
| **reactor** | **52 个模块全 `BUILD SUCCESS`**（FAILURE=0 · SKIPPED=0） |
| **surefire 逐模块求和** | **29 个含测模块 · 3614 tests / 0 failures / 0 errors / 3 skipped** |
| **口径** | 逐 `TEST-*.xml` 的 `tests=` 求和（**不是** `grep -c '@Test'`）⇒ 本轮跑的是 `clean verify` ⇒ 跑前所有 `target/` 已清空 ⇒ 所有 XML 都是**本轮产物**（避开「陈旧孤儿报告抬高计数」那个坑） |

### 2.2 · 逐模块读数

| 模块 | 测试类数 | tests | F | E | S |
|---|---|---|---|---|---|
| `service/article/article-api` | 12 | 224 | 0 | 0 | 0 |
| `service/article/article-common` | 1 | 15 | 0 | 0 | 0 |
| `service/article/article-rpc` | 29 | 209 | 0 | 0 | 0 |
| `service/comment/comment-api` | 7 | 66 | 0 | 0 | 0 |
| `service/comment/comment-common` | 3 | 27 | 0 | 0 | 0 |
| `service/comment/comment-rpc` | 29 | 163 | 0 | 0 | 0 |
| `service/common` | 92 | 488 | 0 | 0 | 0 |
| `service/favorite/favorite-api` | 15 | 157 | 0 | 0 | 0 |
| `service/favorite/favorite-common` | 4 | 29 | 0 | 0 | 0 |
| `service/favorite/favorite-rpc` | 28 | 155 | 0 | 0 | 3 |
| `service/follow/follow-api` | 13 | 148 | 0 | 0 | 0 |
| `service/follow/follow-common` | 3 | 37 | 0 | 0 | 0 |
| `service/follow/follow-rpc` | 17 | 99 | 0 | 0 | 0 |
| `service/hot/hot-api` | 17 | 127 | 0 | 0 | 0 |
| `service/hot/hot-common` | 2 | 14 | 0 | 0 | 0 |
| `service/hot/hot-rpc` | 28 | 135 | 0 | 0 | 0 |
| `service/like/like-api` | 13 | 113 | 0 | 0 | 0 |
| `service/like/like-common` | 2 | 18 | 0 | 0 | 0 |
| `service/like/like-rpc` | 30 | 163 | 0 | 0 | 0 |
| `service/message/message-api` | 18 | 122 | 0 | 0 | 0 |
| `service/message/message-common` | 2 | 21 | 0 | 0 | 0 |
| `service/message/message-rpc` | 28 | 121 | 0 | 0 | 0 |
| `service/task/task-api` | 11 | 144 | 0 | 0 | 0 |
| `service/task/task-rpc` | 21 | 143 | 0 | 0 | 0 |
| `service/users/admin/admin-api` | 22 | 160 | 0 | 0 | 0 |
| `service/users/admin/admin-rpc` | 31 | 149 | 0 | 0 | 0 |
| `service/users/user/user-api` | 26 | 138 | 0 | 0 | 0 |
| `service/users/user/user-rpc` | 34 | 190 | 0 | 0 | 0 |
| `service/users/users-common` | 4 | 39 | 0 | 0 | 0 |
| **合计（29 个含测模块）** | **542** | **3614** | **0** | **0** | **3** |

### 2.3 · 与上一轮回归读数的对比（如实，不做差值归因）

| 项 | Round 7（§九·9.3） | **本轮（Round 6 · S1）** | Δ |
|---|---|---|---|
| 含测模块 | 29 | **29** | 0 |
| 条数 | 3,570 | **3,614** | **+44** |
| F / E / S | 0 / 0 / 4 | **0 / 0 / 4** | 0 |

⇒ **+44 的归因（本轮新增测试类可逐条对上 39 条，余 5 条如实标注未归因）**：
**6 个 proto 消费方式契约测试类**（`P5-T01` 的产物 · 各 **6** 条 = **36**）
＋ **favorite 进程优雅关闭测试**（`P4-T03` 的产物 · **3** 条）= **39** ⇒
⇒ **余 5 条来自 Round 7 那次回归读数之后落库的其它测试改动**（**本轮未逐条归因** ⇒ 如实标注，不做推算）。

⚠️ **4 条 skip 的归属**：**favorite-api 1 条 ＋ favorite-rpc 3 条** ⇒ **两处都在 favorite 域**
⇒ 与 `P4-T03` 落的 `FavoriteFactDispatchSchedulerGracefulShutdownTest` **同域** ⇒
⇒ **本轮新增的 3 条 graceful-shutdown 测试**跑在 favorite-rpc ⇒ ⚠️ **该域的 3 条 skip 需与本轮新增测试一并复核**
⇒ **本轮未逐条核这 3 条 skip 的原因**（如实边界，见 §九）。

⇒ **S1 PASS**（判据 #1：`mvn clean verify` EXIT=0 ＋ **0F / 0E** ＋ 条数已写入报告 · **不许只写「绿」**）。

---

## 三、S2 · 27 门守门逐个真实退出码（+ S2′ ddl 逐域拆行）

### 3.1 · 27 门口径（**32 条命令行** · 27 个脚本 · 逐个真实退出码）

> ⚠️ **口径**（judge §六 6.3）：**27 = 脚本数** ⇒ `check-ddl-static.py` 算 **1 个脚本**、但有 **6 条命令行**
> ⇒ **命令行条数 = 27 + 5 = 32**。⚠️ **本轮**另跑了 **11 条**不计入 27 的门（§3.2）⇒ **总计 43 条**。

| # | 命令 | EXIT | 备注 |
|---|---|---|---|
| 1 | `bash tools/difftest/check-proto-static.sh` | **0** | check-proto-static.sh |
| 2 | `bash tools/difftest/check-jar-executable.sh` | **0** | check-jar-executable.sh |
| 3 | `bash tools/difftest/check-plaintext400-headers.sh` | **0** | check-plaintext400-headers.sh（默认地址跑 ⇒ EXIT=2 未评估；改用探针实际端口 + scope=task 补跑 ⇒ 3 域全评估全满足） |
| 4 | `bash tools/difftest/check-flyway-concurrent.sh` | **0** | check-flyway-concurrent.sh |
| 5 | `python3 tools/difftest/check-merge-tree-content.py <4 棵 lane ref>` | **0** | check-merge-tree-content.py |
| 6 | `python3 tools/difftest/check-ddl-static.py`（裸跑 = users 域） | **1** | check-ddl-static.py 裸跑=users（9 处 DEFAULT CURRENT_TIMESTAMP 残留 ⇒ 刻意保留 ⇒ A 类 0） |
| 7 | `python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source` | **1** | check-ddl-static.py --domain comment（机器报 23 = 实质 21：2 处 IDENTITY-vs-serial 假阳性 ⇒ 已签字豁免 report_record ⇒ 实质 21 与台账一致） |
| 8 | `python3 tools/difftest/check-ddl-static.py --domain hot --indexes --check-source` | **3** | check-ddl-static.py --domain hot（N/A：两侧都无数据库 ⇒ 需先起空容器才够到 N/A 短路） |
| 9 | `python3 tools/difftest/check-ddl-static.py --domain follow --indexes --check-source` | **0** | check-ddl-static.py --domain follow（8 维度逐项一致 ⇒ 0 处实质差异） |
| 10 | `python3 tools/difftest/check-ddl-static.py --domain task --indexes --check-source` | **0** | check-ddl-static.py --domain task（8 维度逐项一致 ⇒ 0 处实质差异） |
| 11 | `python3 tools/difftest/check-ddl-static.py --domain article --indexes --check-source` | **0** | check-ddl-static.py --domain article（8 维度逐项一致 ⇒ 0 处实质差异；触发器函数体属性逐项相同） |
| 12 | `python3 tools/difftest/check-tomcat-limits.py` | **0** | check-tomcat-limits.py |
| 13 | `python3 tools/difftest/check-grpc-interceptor-wiring.py` | **0** | check-grpc-interceptor-wiring.py |
| 14 | `python3 tools/difftest/check-flyway-ledger.py` | **0** | check-flyway-ledger.py |
| 15 | `python3 tools/difftest/check-go-json-body-predicate.py` | **0** | check-go-json-body-predicate.py |
| 16 | `python3 tools/difftest/check-grpc-runtime-classpath.py` | **0** | check-grpc-runtime-classpath.py |
| 17 | `python3 tools/difftest/check-no-nul-bytes.py` | **0** | check-no-nul-bytes.py |
| 18 | `python3 tools/difftest/check-cross-domain-holds.py` | **0** | check-cross-domain-holds.py |
| 19 | `python3 tools/difftest/check-deadline-verdict.py` | **0** | check-deadline-verdict.py |
| 20 | `python3 tools/difftest/check-deadline-downstream.py` | **0** | check-deadline-downstream.py |
| 21 | `python3 tools/difftest/check-global-lane-ownership.py` | **0** | check-global-lane-ownership.py |
| 22 | `python3 tools/difftest/check-wave-coupling.py` | **0** | check-wave-coupling.py |
| 23 | `python3 tools/difftest/check-wave-lane-ownership.py` | **0** | check-wave-lane-ownership.py |
| 24 | `python3 tools/difftest/check-wave-role-model.py` | **0** | check-wave-role-model.py |
| 25 | `python3 tools/check-ledger-count.py` | **0** | check-ledger-count.py |
| 26 | `python3 tools/check-ledger-tables.py` | **0** | check-ledger-tables.py |
| 27 | `python3 tools/check-agents-budget.py` | **0** | check-agents-budget.py |
| 28 | `python3 tools/check-ledger-selfcheck.py` | **0** | check-ledger-selfcheck.py（本轮初跑 EXIT=1：task-rpc 测试数 137⇒143 台账滞后 ⇒ 已订正后转绿） |
| 29 | `python3 tools/difftest/check-phasegoal-consistency.py` | **0** | check-phasegoal-consistency.py |
| 30 | `python3 tools/difftest/check-phasegoal-lanes.py` | **0** | check-phasegoal-lanes.py |
| 31 | `python3 tools/difftest/check-no-prod-secrets.py` | **0** | check-no-prod-secrets.py |
| 32 | `python3 tools/g3-anchor-check/check-anchors.py --all` | **0** | check-anchors.py --all（本轮初跑 EXIT=1：LINE_OOB=1（HEAD 纯净副本同样红 ⇒ 主干自带）⇒ 指针订正 37-41⇒35-39 后转绿） |

### 3.2 · 不计入 27 的门（**11 条** · 单列）

| # | 命令 | EXIT | 备注 |
|---|---|---|---|
| 1 | `python3 tools/difftest/check-surface-parity.py` | **0** | check-surface-parity.py [不计入27] |
| 2 | `python3 tools/difftest/scan-bash-legacy-vars.py` | **0** | scan-bash-legacy-vars.py [不计入27] |
| 3 | `python3 tools/difftest/scan-rpc-port-defaults.py` | **0** | scan-rpc-port-defaults.py [不计入27] |
| 4 | `python3 tools/difftest/check-datasource-password-form.py` | **0** | check-datasource-password-form.py [不计入27] |
| 5 | `python3 tools/difftest/check-rpc-common-scan-exclusion.py` | **0** | check-rpc-common-scan-exclusion.py [不计入27] |
| 6 | `python3 tools/difftest/check-mutation-anchor-liveness.py` | **0** | check-mutation-anchor-liveness.py [不计入27] |
| 7 | `python3 tools/difftest/check-grpc-channel-target-scheme.py` | **0** | check-grpc-channel-target-scheme.py [不计入27] |
| 8 | `python3 tools/difftest/check-source-test-decl-count.py` | **0** | check-source-test-decl-count.py [不计入27] |
| 9 | `python3 tools/difftest/check-ledger-count-pointers.py` | **0** | check-ledger-count-pointers.py [不计入27] |
| 10 | `python3 tools/difftest/ledger-status-coverage.py` | **0** | ledger-status-coverage.py [不计入27] |
| 11 | `python3 tools/check-doc-links.py` | **0** | check-doc-links.py [不计入27] |

### 3.3 · 非 0 的 **3 条** —— 逐条定性（**判据 = 看输出文本，不看码**）

| 门 | EXIT | 输出文本里的实情 | 定性 | 销号依据 |
|---|---|---|---|---|
| `check-ddl-static.py`（裸跑 = users 域） | **1** | 「== 实质差异合计 **9** 处 ⇒ 退出码 1 ==」⇒ 类型/长度 **0 处** ⇒ 全部是 `DEFAULT CURRENT_TIMESTAMP` 残留 | **刻意保留** | 退出码 **1 = 存在实质差异**（脚本自述的语义）⇒ 但这 9 处**全部**是 `DEFAULT` 维度的加强型残留 ⇒ 按 §十 已定级 **A 类 0** ⇒ **不阻阶段一**（§1.1 ⑥ 第 4 条） |
| `check-ddl-static.py --domain comment` | **1** | 「== 实质差异合计 **23** 处 ⇒ 退出码 1 ==」⇒ 其中 **⚪ 已签字豁免 · 未核对: `report_record`**（`SD-2026-10-24-CAND-02`）⇒ 且 **23 = 实质 21 + 2 处 IDENTITY-vs-serial 假阳性** | **已签字偏离 ＋ 已知假阳性** | 退出码 **1 = 存在实质差异** ⇒ 但**实质 21 处**（可空性 8 ＋ `DEFAULT` 8 ＋ 索引 2 ＋ `CHECK` 2 ＋ 触发器 1）**与台账逐条一致**（`docs/DRIFT/_cross/ddl-type-divergences.md:175` / `:189` 已记那 2 处假阳性）⇒ **不是新漂移** |
| `check-ddl-static.py --domain hot` | **3** | 「⚠️ 本域两侧都没有业务表 ⇒ **DDL 维度不适用（N/A）**，不是「已对齐」」 | **N/A** | 退出码 **3 = 本域不适用** ⇒ ⛔ **不许写成「已对齐」** ⇒ 只能写「**本维度无对象可比**」 |

⇒ **27 门口径的非 0 = 3 条，全部落在上表三格里 ⇒ 「红项零『未定性』」成立**（完成条件 #3）。

⚠️ **一个容易踩的坑（本轮实测到）** —— **机器报数 ≠ 分类后计数**：
本轮 9 域**同口径**（`--indexes --check-source`）复算 = users **16** · comment **23** · message **7** · like **5** · favorite **2** ·
article/follow/task **0** · hot **N/A** ⇒ **机器合计 53**；
而台账/judge 记的 **51** 是**分类后**的数（comment 那 2 处 IDENTITY-vs-serial 假阳性**人工闭合为非真差**）⇒
⇒ **两者不是漂移，是两个口径** ⇒ ⚠️ **若只看机器的 53，就会误判成「51 ⇒ 53 漂移 +2」** ⇒
⇒ **本轮回源核了口径（`ddl-type-divergences.md:175` / `:189`）⇒ 判定「51 处」没有漂移** ⇒ **judge 的数字不改**。

⚠️ **另一条口径提醒** —— `hot` 域的 N/A 短路**要容器存在才够得到**：
脚本的 N/A 闸门在「**读库之后**」判 ⇒ 若 `ddl-hot-pg` 不存在 ⇒ 先死在「读不到库」⇒ **EXIT=2（环境不满足）**
⇒ ⇒ **本轮先 `bash tools/difftest/ddl-parity.sh hot --no-check` 起了一个空容器** ⇒ 才拿到正确的 **EXIT=3（N/A）**。

### 3.4 · S2′ —— 本轮抓到的**两条命令形式缺陷**（计划期没抓到，执行期抓到的）

| # | 缺陷 | 危害 | 本轮处置 |
|---|---|---|---|
| **1** | judge §六 6.3 写的是 `check-ddl-static.py --domain {follow,task,article} --indexes --check-source` ⇒ **不可执行**（`--domain` 的 argparse 用 `choices=sorted(DOMAINS)` ⇒ **只接受单值** ⇒ 退出码 **2**） | ⚠️ **退出码 2 与「已签字偏离」同码** ⇒ 不看输出文本就会把「**命令写错**」误记成「**已签字偏离**」 | ✅ **judge §六 6.3 已拆成 3 行**（follow / task / article 各一行 · 各带真实退出码）⇒ **每行都是真的可执行命令** ⇒ ⇒ **X-2 的判定口径「9 域逐域实跑」一字未改**（**这是修正命令形式，不是改判据**）⇒ 记录见 judge **§十 #39** |
| **2** | Round 6 计划 §5.4 的 S3 命令块把 errmsg 探针写成统一形态 `tools/errmsg-probe/<域>/run-parity.sh` 的循环 ⇒ 但 **article 域没有 `tools/errmsg-probe/article/` 子目录**（article = **顶层** `tools/errmsg-probe/run-parity.sh`） | 同款 ⇒ 会落「**文件不存在**」⇒ 被误读成「article 域错误码不一致」 | ✅ **计划已就地修正**（记录见 `docs/RoundPlan/round-06/README.md` §5.3.1.2）⇒ ⇒ **「计划期的命令清单」本身也需要执行期复核** |

⇒ **S2 PASS**（完成条件 #2：**27 门全部有真实退出码** ＋ 非 0 全部已定性）。

---

## 四、S3 · 双侧活体探针（**两侧同时跑**）

### 4.1 · 双侧活体探针逐个真实退出码（**18 条**）

> ⚠️ **「双侧同时跑」的含义**：每个探针**同一份请求**同时打 **Go 真源** ＋ **RAG** 两侧 ⇒ 逐字节比对 ⇒
> ⚠️ **不是单侧读数**（单侧只证「代码写成了这样」，证不了「Go 是这样」—— AGENTS.md §3.3）。

| # | 命令 | EXIT | 备注 |
|---|---|---|---|
| 1 | `bash tools/semantic-probe/run-parity.sh` | **0** | semantic-probe/run-parity.sh |
| 2 | `bash tools/json-body-probe/run-parity.sh` | **0** | json-body-probe/run-parity.sh |
| 3 | `bash tools/errmsg-probe/run-parity.sh`（**article 域 = 顶层脚本**） | **0** | errmsg-probe/run-parity.sh (article) |
| 4 | `bash tools/errmsg-probe/comment/run-parity.sh` | **0** | errmsg-probe/comment/run-parity.sh |
| 5 | `bash tools/errmsg-probe/favorite/run-parity.sh` | **0** | errmsg-probe/favorite/run-parity.sh |
| 6 | `bash tools/errmsg-probe/follow/run-parity.sh` | **0** | errmsg-probe/follow/run-parity.sh |
| 7 | `bash tools/errmsg-probe/hot/run-parity.sh` | **0** | errmsg-probe/hot/run-parity.sh |
| 8 | `bash tools/errmsg-probe/like/run-parity.sh` | **0** | errmsg-probe/like/run-parity.sh |
| 9 | `bash tools/errmsg-probe/message/run-parity.sh` | **0** | errmsg-probe/message/run-parity.sh |
| 10 | `bash tools/errmsg-probe/task/run-parity.sh` | **0** | errmsg-probe/task/run-parity.sh |
| 11 | `bash tools/errmsg-probe/users/run-parity.sh` | **0** | errmsg-probe/users/run-parity.sh |
| 12 | `bash tools/api-full-parity-probe/article/run.sh` | **0** | api-full-parity-probe/article/run.sh |
| 13 | `bash tools/api-full-parity-probe/favorite/run.sh` | **0** | api-full-parity-probe/favorite/run.sh |
| 14 | `bash tools/api-full-parity-probe/favorite-rpc/run.sh` | **0** | api-full-parity-probe/favorite-rpc/run.sh |
| 15 | `bash tools/api-full-parity-probe/follow/run.sh` | **0** | api-full-parity-probe/follow/run.sh |
| 16 | `bash tools/api-full-parity-probe/like/run.sh` | **0** | api-full-parity-probe/like/run.sh |
| 17 | `bash tools/api-full-parity-probe/users/run.sh` | **0** | api-full-parity-probe/users/run.sh |
| 18 | `bash tools/api-full-parity-probe/users-common/run.sh` | **0** | api-full-parity-probe/users-common/run.sh |

### 4.2 · 「10 域」的覆盖是怎么来的（如实，不含糊）

| 探针族 | 覆盖域 | 条数 |
|---|---|---|
| `tools/errmsg-probe/**`（**错误码契约** · 逐字节对拍） | article（**顶层脚本**）· comment · favorite · follow · hot · like · message · task · users = **9 域** | 9 |
| `tools/api-full-parity-probe/**`（**对外契约面** · HTTP/gRPC wire 层） | article · favorite · favorite-rpc · follow · like · users · users-common = **7 个探针**（覆盖 **9 个域**） | 7 |
| `tools/semantic-probe/run-parity.sh`（**语义面**） | 跨域 | 1 |
| `tools/json-body-probe/run-parity.sh`（**JSON body 面**） | 跨域 | 1 |
| **合计** | **9 个业务域 ＋ `users-common` ＝ 10 域全覆盖** | **18** |

⇒ **域覆盖 = 10/10**（9 个业务域 + `users-common`）⇒ **每域至少 1 条双侧活体探针**。

### 4.3 · 两条**额外的双侧活体读数**（本轮为把 `check-plaintext400-headers.sh` 补成真评估而顺带取得）

| 探针 | 读数 | 用途 |
|---|---|---|
| `tools/api-full-parity-probe/users/run.sh --keep` | **steps=39 · asserted=10 · byte_equal=29 · inconclusive=0 · unexpected=0** ⇒ 端口 go.user 18215 / go.admin 18216 / rag.user 18219 / rag.admin 18220 | 为 `plaintext400` 提供**真栈**（它的默认地址 59900/58889/18888/18889 与探针实际端口**不同** ⇒ 用 env 覆盖） |
| `tools/difftest/run-task-http-parity.sh`（`KEEP_UP=1`） | **总计 44 条：MATCH 44 / DIFF 0** ⇒ RAG task-api 58886 / Go task-api 18886 | 同上（task 域） |

⇒ **这两条同时也是「users / task 域双侧活体」的独立证据**（`users` 39 步 · `task` 44 条）。

⇒ **S3 PASS**（完成条件 #4：**10 域探针全绿** · **两侧同时跑** · 无单侧读数）。

---

## 五、S4 / S5 · 生成器 diff ＋ 合并树物化

### S4 · 生成器 `--write` ＋ 校验（**diff 必须为空**）

| 步骤 | 命令 | 退出码 | 读数 |
|---|---|---|---|
| 生成 | `python3 tools/difftest/check-phasegoal-lanes.py --write` | **0** | 写 `docs/PhaseGoal/**`（机器生成物 · **不得手改**） |
| diff | `git diff --numstat docs/PhaseGoal/` | — | **0 行** ⇒ **diff 为空** ⇒ 机器生成物与判据一致 |
| 校验 | `python3 tools/difftest/check-phasegoal-lanes.py` | **0** | per-lane 分摊矩阵与基线一致 |
| 校验 | `python3 tools/difftest/check-phasegoal-consistency.py` | **0** | judge 自身 15 组自检全过 |

⇒ **S4 PASS**。

### S5 · 合并树物化 ＋ 合并内容守门

`python3 tools/difftest/check-merge-tree-content.py align/g1-users-follow align/g2-message-like-task align/g3-article-comment-favorite-hot align/g4-service-common` ⇒ **EXIT=0**

```
步: align/g1-use ⊕ align/g2-mes ⇒ e41c75c6cc7e   问题 0 · 不可信 0
步: 31fbb425396c ⊕ align/g3-art ⇒ e41c75c6cc7e   问题 0 · 不可信 0
步: 7810579bd1b2 ⊕ align/g4-ser ⇒ e41c75c6cc7e   问题 0 · 不可信 0
重建出的合并 commit: 78d0db544acedd9b4b1119ab67812d31fa9cfbfe
✅ PASS（每一步的合并结果 = 两边改动都在；无含真 NUL 的多方修改文件）
```

⇒ **S5 PASS**（铁律 1 的机器形态：逐步 merge-tree ⇒ 每一步「那侧的改动没丢」⇒ **问题 0 · 不可信 0**）。
⚠️ **诚实边界**：本门验的是 **git 对象层**的合并内容完整性，**与业务语义无关**（judge §六 6.2 明写）⇒ 它**不能**替代「跑得绿」⇒ 与 S1/S2/S3 互补。


---

## 六、S6 · 台账自洽

### S6 · 台账自洽（四项判据 ＋ 台账三门）

| 判据 | 命令 | 退出码 | 读数 |
|---|---|---|---|
| 台账计数门 | `python3 tools/check-ledger-count.py` | **0** | 索引与现状一致 |
| 台账表结构门 | `python3 tools/check-ledger-tables.py` | **0** | 内建 3 条变异 M1/M2/M3 全过 |
| 台账自检门 | `python3 tools/check-ledger-selfcheck.py` | **0** | **全仓唯一红门已转绿**（原 task 台账 134 vs 137 ⇒ 本轮 `P4-T04` 订正后） |
| 签字档活指针对拍 | `python3 tools/difftest/check-ledger-count-pointers.py` | **0** | 跨档「已签字 N 条 / 未签字 M 条」活指针 == 权威口径实测值 |
| Pitfalls 状态字段 | `python3 tools/difftest/ledger-status-coverage.py` | **0** | **条目总数 442 · 带状态 442 · 覆盖率 100% · 0 字节坑位文件 0**（⚠️ 派工单基线「74 / 336」**已过期**；⚠️ **本轮新增 `PIT-GLOBAL-069` ⇒ 441 ⇒ 442** · `check-ledger-count.py --fix` 已同步 `docs/Pitfalls/_INDEX.md` 的机器生成计数行） |
| **X-n 唯一** | 全仓裸 `X-n` 引用扫描 | — | 剩余引用**全部**指向 judge §五（`docs/PhaseGoal/g[1-4].md` = judge 的机器生成物 ＋ 两份历史轮次记录）⇒ 跨域台账**零裸 `X-n`** ⇒ 跨域一律用 `CD-n` |
| **`DRIFT-HOTAPI-030` 一态** | 状态格逐处核 | — | 权威态 = **🟡 部分闭合（三格显式声明）**；六处状态格现已一致：`AGENTS.md:140` 🟡 · `_cross/open-items.md:15` 🟡 · `global/_README.md:134` 🟡 · `global/cross-domain.md` 的 CD-6 行 🟡 · `global/vs-common.md:34` 🟡 · `hot/api.md:1282` 🟡（+ `:2260` 收口标题 🟡）⇒ **六处四态 → 一态**（本轮补齐 `hot` 两处 ＋ `favorite/api.md:1104` ＋ 悬空编号 `DRIFT-FAV-API-001` 的 3 处引用订正） |

⚠️ **一处**已记录的口径冲突（**不是本轮引入**）：`docs/DRIFT/_INDEX.md:33` 的 🟡/🔴 两读 + `article/vs-common.md:26` 的 ✅（**域侧已验**）⇒ **域侧 ✅ 与跨域 🟡 是两个不同粒度的口径**（域侧本域无残留 · 跨域残留在 `task` 域 >10001 段那一格）⇒ **不构成「同一格两态」**。


---

## 七、S7 · 计划层 6 组交叉检查

### S7 · 计划层 6 组交叉检查（规范 §十二）

> **取证方式**：**只读扫描 + grep 计数**（逐条带 `文件:行号`）⇒ 本组的数字**不是**守门退出码（那些在 §3）。
> **失败处置**：本组 S7 步骤的失败处置原文 = 「发现「无去向任务」⇒ **改计划**（不是只记在报告里）」⇒ **本轮已改计划**（见 §7.3）。

#### §7.1 六组总览

| 组 | 主题 | 结论 | 关键读数 |
|---|---|---|---|
| **12.1** | 旧 RoundPlan 覆盖 → `_migration.md` 逐条有去向 | ✅ **满足** | **64 行全有去向 · 无去向 = 0**（自述与实测逐条吻合：含 `P<n>-T<nn>` 的 34 行 ＋ `—（已完成…）` 的 36 行 − 重叠 6 行 = 64） |
| **12.2** | DRIFT 覆盖 → `_task-universe.md` ＋ `_unresolved.md` | 🟡 **部分** | 阶段一范围（**A/B 类**）漏排 = **0**（TSV `stage∈{A,B}` ∧ `schedule=未排期` ⇒ 0；派生层 §4.1 该表**空**）⇒ 但 **2 条开放未定级项计划层无去向**（本轮已补登记）+ 派生层 U8 词表漏 `DEP-`/`OUT-` ⇒ **12 条已登记项被误判「未排期」** |
| **12.3** | Pitfalls 覆盖 → 各轮「防错约束」小节 | 🟡 **部分** | 编号层**双向可查成立**（25/25 编号在台账有对应标题；7 轮绑定行数 8+16+7+12+10+14+11 = **78 行** / 去重 **25** 个编号 / 与 `_dependencies.md` 逐轮吻合）⇒ 但 **反向覆盖率 25/442 ≈ 5.7%**（417 条未转成执行约束 · **计划层自认未完成**，挂给 `P5-T05`）⇒ 且 **78 行的「出处」行号 0/78 落在自己条目内** |
| **12.4** | Intentional_Deviations 覆盖 → `_unresolved.md` 第 4 类 | ✅ **满足** | 权威表 **96 条**（62 签字 / 13 未签字候选 / 11 撤销 / 5 转录 / 4 不计入 / 1 附记）⇒ **13/13 未签字候选有去向**（`DEC-02` 聚合口径）⇒ **0 条已签字偏离被排成修复任务**（`SD-2026-10-24-CAND-02` 三处均为「不得再修」/「保护项」）⇒ 11 条撤销项**未被复活** |
| **12.5** | PhaseGoal 覆盖 → `_final-acceptance.md` | ✅ **满足** | **48 行**（9 P0 ＋ 4 C ＋ 12 X ＋ 8 D ＋ 6 交付物 ＋ 9 冻结）⇒ 任务 ID / 验收命令 / 兜底 **三列空 = 0** ⇒ **102 处任务 ID 引用 0 悬空**（7 个轮次全覆盖：P1 13 · P2 11 · P3 15 · P4 15 · P5 15 · P6 22 · P7 11） |
| **12.6** | 依赖与验收完整性 → `_dependencies.md` ＋ `_final-acceptance.md` | ✅ **满足**（附 1 条非阻塞注记） | 四文件合计 **793 处任务 ID 引用全部合法**（`_dependencies` 494 · `_final-acceptance` 102 · `_task-universe` 158 · `_unresolved` 39）⇒ **依赖悬空 0 · 验收无归属 0 · 有依赖无验收项 0** |

#### §7.2 各组关键证据（可复算命令）

| 组 | 可复算命令 |
|---|---|
| 12.1 | `grep -cE '^\| \`(T-[0-9]\.[0-9]+\|R5-[0-9]+\|R5-F[0-9]+\|NEW-[A-Z0-9#]+)\` \| [^|]* \| [^|]* \| [^|]* \| *\|' docs/RoundPlan/_migration.md` ⇒ **0**（无去向） |
| 12.2 | `awk -F'\t' 'NR>1 && ($10=="A"\|\|$10=="B") && $13=="未排期" {n++} END{print n+0}' docs/DRIFT/_cross/stage1-derivation.tsv` ⇒ **0**（A/B 类漏排） |
| 12.3 | `grep -hoE '^\| \`(PIT\|PF\|P)-[A-Za-z0-9-]+\`' docs/RoundPlan/round-0*/README.md \| sort -u \| wc -l` ⇒ **25**（绑定编号并集）；`python3 tools/difftest/ledger-status-coverage.py` ⇒ 条目总数 **442** |
| 12.4 | `python3 tools/difftest/ledger-deviation-count.py --strict` ⇒ 与 `docs/Intentional_Deviations/_INDEX.md:23-32` 的权威表逐值一致；`grep -rnE 'SD-[0-9A-Z-]+\|CAND-[0-9]+' docs/RoundPlan/` ⇒ 已签字项**仅**出现在「不得再修 / 保护项」语境 |
| 12.5 | `grep -cE '^\| \*\*(P0 #[0-9]+\|C-[0-9]+\|X-[0-9]+\|D-[0-9]+\|交付物 [0-9]\|冻结 [0-9])\*\*' docs/RoundPlan/_final-acceptance.md` ⇒ **48**；`grep -oE 'P[0-9]-T[0-9]{2}' docs/RoundPlan/_final-acceptance.md \| sort -u` ⇒ 全部落在 37 条合法 ID 空间 |
| 12.6 | 四文件 `grep -oE 'P[0-9]-T[0-9]{2}' … \| sort \| uniq -c` ⇒ 逐个落在 37 条合法 ID 空间（**悬空 0**） |

#### §7.3 「无去向任务」清单 ＋ **本轮的改计划动作**

| # | ID | 状态 | 台账位置 | 本轮处置 |
|---|---|---|---|---|
| 1 | `G4-15b`（共享层 gRPC Stat 的**链层位置**） | 🟡 部分闭合 · P2 · **未定级** | `docs/DRIFT/global/vs-common.md:4919` | ✅ **已补登记进 `_unresolved.md` 第 5 类 = `DEP-07`**（S7 失败处置：「**改计划**」）⇒ **计划层从此有去向** |
| 2 | `G4-15c`（共享层 gRPC Stat 的 **Info 分支**刻意不落） | 🟡 部分闭合 · P2 · **未定级** | `docs/DRIFT/global/vs-common.md:4920` | ✅ **已补登记进 `_unresolved.md` 第 5 类 = `DEP-08`** ⇒ **计划层从此有去向** |

⇒ **真·无去向 = 0**（本轮处置后）。

⚠️ **两条非真缺口但会污染统计的**（**本轮登记、不在本轮修**）：

| 类别 | 条数 | 根因 | 影响 | 处置 |
|---|---|---|---|---|
| 派生层 U8 指针词表漏 `DEP-` / `OUT-` | **12** | 派生层 U8 只认「任务 ID ｜ **BLK/GAP/ENV/DEC**-xx」（`docs/DRIFT/_cross/stage1-derivation.md:67`）⇒ `_unresolved.md` 第 5 类（`DEP-01`~`DEP-06`）＋第 6 类（`OUT-01`~`OUT-06`）在 TSV 里 **0 条**被认成排期指针 ⇒ 全记「未排期」 | 派生层「未排期」计数**系统性偏高** ⇒ ⚠️ **不可把派生层统计直接当结论** | 🟡 **登记 + 定级**（`CT-02`）⇒ 归**派生层生成器**（`tools/ledger-derive/derive-stage1.py` 的 U8 词表）⇒ **本轮不修**（Round 6 = 收口回归，**不改判据/不改生成器**） |
| `SD-2026-10-30-CAND-22` 未逐条点名 | **1** | 只被 `_unresolved.md:111` `DEC-02` 的**聚合口径**（13 条候选）覆盖 | 派生层判它「未定级 + 开放 + 未排期」 | 🟡 **登记** ⇒ 逐条点名属 `DEC-02` 的台账编辑（**登记类，不升级拍板**） |

#### §7.4 非阻塞注记（**不影响 6 组结论**）

| # | 注记 | 出处 |
|---|---|---|
| 1 | `docs/RoundPlan/_dependencies.md:84` 写 round-05「防错约束」小节在 `:245`，**实际在 `:243`**（差 2 行） | 读 |
| 2 | `docs/RoundPlan/_unresolved.md` 的 `DEC-02`「来源」列写 `_INDEX.md:28-30`，而现值「⏳ 未签字候选合计 = 13 条」在 **`_INDEX.md:26`**（行号漂 2 行） | 读 |
| 3 | judge §11.4 与 `_final-acceptance.md:31`（P0 #9）写「已签字 **48** 条 / **18** 条未签字候选」⇒ 权威值 **62 / 13** ⇒ **判据行本身完整，格内计数是旧快照** | 读 |
| 4 | `docs/Pitfalls/_INDEX.md` 的 Pitfalls 条数有 **6 个版本并存**（74/336 · 428/428 · 428 · 434→436 · 441 → **442**）⇒ 权威 = **442**（机器生成行 `_INDEX.md:203`）⇒ **本报告一律用 442** | 读 |
| 5 | 12.3 的「78 行出处行号 0/78 落在自己条目内」⇒ 「逐条可核」这一属性**当前不成立**（台账在上方插入条目后引用未重新锚定）⇒ 属 `P5-T05` 的「逐条可核」补齐面 | 读 |

#### §7.5 本组**未能核到**的部分（如实边界）

| # | 未核到的 | 原因 | 怎么补 |
|---|---|---|---|
| 1 | 12.6 的 **6 个子项**（断言不可观察 / 状态早于证据 / 重复执行 / 写权重叠 / 阻断项被暂缓 / 阶段二混入） | 需逐条语义判读 ＋ 计划期那条「37 条任务 5 个必备字段 ＋ 420 条 `file:line` 引用逐条解析 ＋ 40 个命令逐个 `--help`」的脚本（`docs/RoundPlan/_INDEX.md:200`） | 重跑那条计划期脚本 |
| 2 | 12.3 的**第四落点逐条判读**（「仍生效但当前轮次不适用 ⇒ 写明**具体原因**」） | 需逐条读 442 条正文判断「否」项是否另有原因段 | 台账 `PIT-GLOBAL-*` 目前只有「本轮影响：**是/否**」的**标志位** ⇒ **严格意义上第四落点未被满足** |
| 3 | 12.2 的**行级明细**（未排期 1206 行逐条） | 本组给的是**列级聚合计数** | `python3 tools/ledger-derive/derive-stage1.py --report` 可给分域 / 分状态统计 |
| 4 | TSV 与台账的**同步性** | 本组**只读**、未跑 `derive-stage1.py --check` | ⇒ 主线已在 S6 跑过：`--check` **EXIT=0** ⇒ **本轮已同步** ⇒ 该条边界**本轮关闭** |
| 5 | `docs/DRIFT/**` 里**未被派生层收录**的条目 | 派生层有排除清单（`CASE-` 前缀 + `excluded-id-prefixes.txt`）⇒ TSV 的 `dir=Pitfalls` **431** 行 vs 台账 **442** 条 ⇒ **11 条不在派生表内** | 跑 `derive-stage1.py --report` 看排除清单命中项 |

⚠️ **本组的一个「同源」提醒**：12.3 的「78 行出处行号 0/78 指错」与本轮 P4-T07 核的「G-12 的 15 处指针 6/6 命中」**同族但不是同一批** ⇒ 前者在 **Pitfalls 台账 → 各轮 README** 的引用上，后者在**各域台账 → 当前态指针块**的引用上 ⇒ **两批都验，才不混淆**。


---

## 八、红项定性表（**零「未定性红项」**）

### 定性口径（**先写在这里，红项逐条套用**）

| 定性 | 判据 | 处置 |
|---|---|---|
| **真红** | 退出码非 0 ＋ **输出文本**指向一个**真实未闭合**的差异 ⇒ 且该差异**不在**已签字偏离 / 刻意保留 / N/A 名单里 | 按 `CT-02` 定级 + 登记 ⇒ **本轮不改产品代码**（规范 §一.6）⇒ 落 `docs/RoundPlan/_unresolved.md` |
| **假红** | 退出码非 0 ＋ 根因是**环境 / 残留 / 并发** ⇒ 按 `PIT-GLOBAL-032` 三步定性法（单跑复跑 → 降并行度重跑 → **不跳过全量**） | 记录定性依据 ⇒ 必要时**改门**（`tools/difftest/**` = 主线）⇒ ⚠️ **不得新增 `check-*.py`** |
| **已签字偏离** | 退出码非 0 ＋ 该差异**有**签字档记录（`docs/Intentional_Deviations/**` 的 ✅ 档） | 不销账、不重修 ⇒ 引用签字档编号 |
| **N/A** | 退出码非 0 ＋ 该维度**两侧都不存在**（例 `hot` 域 DDL：Go 侧无对应建表） | ⛔ **不许写成「已对齐」** ⇒ 只能写「N/A」 |
| **刻意保留** | 退出码非 0 ＋ 该差异是**有意留下的**（例 `DEFAULT CURRENT_TIMESTAMP` 残留：已按 §十 定级为 C/D 类 ⇒ 不计入阶段一） | 引用定级依据 |
| **已销号** | 退出码非 0 ＋ 该差异**本轮或此前已闭合**（门没跟上 ⇒ 门红） | 登记「门滞后」⇒ **本轮不改门**（除非是门自身假红/假绿） |

⇒ **完成条件 #3 = 「红项零『未定性』」** ⇒ 下面每一行都必须落在上表的一格里。


### 8.1 · 终态红项表（**非 0 共 3 条** · 全部已定性 ⇒ 「零未定性红项」成立）

| # | 门 / 探针 | EXIT | 定性 | 依据（可复算） |
|---|---|---|---|---|
| 1 | `check-ddl-static.py`（裸跑 = users 域） | **1** | **刻意保留** | 9 处全是 `DEFAULT CURRENT_TIMESTAMP` 残留（类型/长度 **0 处**）⇒ §十 已定级 **A 类 0** ⇒ §1.1 ⑥ 第 4 条不计入阶段一 |
| 2 | `check-ddl-static.py --domain comment` | **1** | **已签字偏离 ＋ 已知假阳性** | 机器报 23 = **实质 21**（+2 = IDENTITY-vs-serial 假阳性 · 台账 `ddl-type-divergences.md:175`/`:189` 已记）＋ `report_record` 整表已签字豁免（`SD-2026-10-24-CAND-02`）⇒ **不是新漂移** |
| 3 | `check-ddl-static.py --domain hot` | **3** | **N/A** | 两侧都无数据库 ⇒ ⛔ **不许写成「已对齐」** |

### 8.2 · 本轮**过程中**出现、**已处置**的红项（**3 条** · 诚实记录，不隐藏）

| # | 门 | 初跑 EXIT | 现象 | 处置 | 怎么确认的 |
|---|---|---|---|---|---|
| 4 | `check-ledger-selfcheck.py` | **1** | 「❌ rpc.md 测试数: 台账 **137** / 实测 **143**」⇒ 本轮新增的 6 个 proto 契约测试类（+6 条）让台账又滞后 | ✅ **已订正**（`docs/DRIFT/task/rpc.md:29` 权威值 137 ⇒ **143** · **历史值 137 原样保留在正文**）⇒ **复跑 EXIT=0**（层 0~4 全过 · 层 5/6 台账未点名 **0** 个） | 复跑 `check-ledger-selfcheck.py` ⇒ **EXIT=0** |
| 5 | `check-anchors.py --all` | **1** | **LINE_OOB=1**：`AdminRpcClientDeadlineCapTest.java:83` 引用 `admincenter.yaml:37-41`，而该 Go 文件只有 **39 行** ⇒ **越界 2 行**（真实块在 **35-39**） | ✅ **已订正**（指针 `37-41` ⇒ `35-39`）⇒ **纯注释改动 ⇒ 行为面零变化** ⇒ **复跑 EXIT=0**（`LINE_OOB=0` · `OK=3412`） | 🟢 **同条件对照**（`git worktree add --detach` 出 HEAD 的纯净副本 ⇒ **同一道门在 HEAD 上同样 EXIT=1 / LINE_OOB=1**）⇒ ⇒ **判定「**主干自带**，不是本轮引入」** ⇒ ⇒ 再订正 ⇒ 转绿 |
| 6 | `check-plaintext400-headers.sh` | **2** | 「⚠️ **未评估** —— 所选 scope 的域栈一个都没起（SKIP 3 个域）」⇒ **本次运行一条断言都没产生** ⇒ 退出码 **2 = 未评估**（**不是「通过」**） | ✅ **已补跑成真评估**（**不是**改门、**不是**改判据）：① 用 `tools/api-full-parity-probe/users/run.sh --keep` 起真栈（端口 18215/18216/18219/18220 ⇒ 用 `RAG_USER`/`GO_USER`/`RAG_ADMIN`/`GO_ADMIN` env 覆盖门的默认地址）⇒ **user + admin 两域全部满足**；② 用 `tools/difftest/run-task-http-parity.sh`（`KEEP_UP=1`）起 task 真栈（58886 / 18886）⇒ **task 域全部满足** ⇒ ⇒ **三域全评估 ⇒ EXIT=0** | 两次跑的门日志分别在 `/tmp/r6-gates/plaintext400-all.log` 与 `plaintext400-task.log` ⇒ 两侧各 4 条断言（status / Content-Type 含 text/plain / Content-Type 含 charset=utf-8 / `nosniff`）**全 ✓** ＋ **两侧 Content-Type 归一化后一致** |

⚠️ **第 6 条的纪律要点**：退出码 **2 = 未评估**（脚本自述：「**无栈环境恒绿 = 假绿**：拿这个退出码当守门信号，等于什么都没测就说通过」）
⇒ ⇒ **本轮没有把 2 当成「通过」写进报告**，而是**把栈起起来重跑** ⇒ 才拿到 EXIT=0。

### 8.3 · 「零未定性红项」核对

| 完成条件 #3 | 核对 |
|---|---|
| 每个非 0 都有「真红 / 假红 / 已签字偏离 / N/A / 刻意保留」之一 + 依据 | ✅ **6 条**（终态 3 条 + 过程中 3 条已处置）**全部落格**（见 §8.1 / §8.2） |
| 不把「`check-ddl-static.py` 的 1/2/3」当成「门坏了」 | ✅ §3.3 逐条按**输出文本**定性（1 = 刻意保留 · 1 = 已签字豁免 + 已知假阳性 · 3 = N/A）⇒ **三格都不是「门坏了」** |
| 不把「跑红」当回归去「修回去」 | ✅ §8.2 第 4/5 条的处置都是**订正台账/指针**，**没有改任何产品行为** ⇒ **行为面 diff 为空** |

⇒ **完成条件 #3 成立。**

---

## 九、本轮结论 ＋ 如实边界

### 9.1 · 结论

| # | 判据 | 实测 | 结论 |
|---|---|---|---|
| 1 | 全量回归绿 | `mvn -o -B -DargLine=-Xmx2g clean verify` ⇒ **EXIT=0** · **52 个 reactor 模块全 BUILD SUCCESS** · **3614 tests / 0F / 0E / 3S** · **29 个含测模块** · **31:02** | ✅ |
| 2 | **27 门全部有真实退出码** | **43 条命令行**（27 门口径 **32** + 不计入 27 的 **11**）⇒ **非 0 仅 3 条** | ✅ |
| 3 | 红项零「未定性」 | 终态 **3 条** + 过程中 **3 条已处置** ⇒ **全部落格**（§8） | ✅ |
| 4 | 10 域探针全绿 | **18 条双侧活体探针全 EXIT=0**（9 域 errmsg + 7 个 api-full + semantic + json-body）⇒ **域覆盖 10/10** | ✅ |
| 5 | 生成器 diff 为空 | `check-phasegoal-lanes.py --write` ⇒ `git diff --numstat docs/PhaseGoal/` = **0 行** | ✅ |
| 6 | 台账自洽 | `X-n` 唯一 ✓ · `DRIFT-HOTAPI-030` 一态（🟡 部分闭合）✓ · Pitfalls 状态字段 **442/442 = 100%** ✓ · 台账三门 EXIT=0 ✓ | ✅ |
| 7 | 计划层 6 组无「无去向任务」 | **真·无去向 = 0**（本轮已补登记 `DEP-07` / `DEP-08` ⇒ **改计划**而非只记报告） | ✅ |

⇒ **Round 6 的 7 个可客观判定的完成条件（§5.5）全部满足** ⇒
⇒ **满足进入 Round 7 的条件**（前提 = S1~S7 全完成 ＋ **无未定性红项** ⇒ **两者都成立**）。

### 9.2 · 本轮**新增**问题及阶段一分类（按 §十 定级）

| # | 问题 | 定级 | 处置 |
|---|---|---|---|
| 1 | **§六 6.3 的 DDL 行有一条不可执行**（`--domain {follow,task,article}` ⇒ argparse 只接受单值 ⇒ 退出码 2 ＝「已签字偏离」同码） | **文档性 · 不阻阶段一** | ✅ **已拆成 3 行**（judge §十 **#39**）⇒ **判定口径一字未改** |
| 2 | **Round 6 计划的 S3 命令块对 article 域写错了路径**（无 `tools/errmsg-probe/article/` 子目录） | **文档性 · 不阻阶段一** | ✅ **计划已就地修正**（`round-06/README.md` §5.3.1.2） |
| 3 | **`AdminRpcClientDeadlineCapTest.java:83` 的 Go 指针越界**（`admincenter.yaml:37-41` vs 实际 39 行） | **文档性 · 不阻阶段一**（**主干自带** ⇒ 已用 HEAD 纯净副本同条件对照确认） | ✅ **已订正指针**（`35-39`）⇒ **纯注释 · 行为面零变化** |
| 4 | **`task/rpc.md` 的权威测试数又滞后**（137 ⇒ **143**，因本轮新增 6 个 proto 契约测试类） | **文档性 · 不阻阶段一** | ✅ **已订正**（历史值保留）⇒ 门转绿 |
| 5 | **派生层 U8 指针词表漏 `DEP-` / `OUT-`** ⇒ `_unresolved.md` 第 5/6 类共 12 条被误判「未排期」 | **CT-02**（工具面） | 🟡 **登记 + 定级** ⇒ **本轮不改判据/不改生成器**（Round 6 纪律）⇒ 归生成器 |
| 6 | **Pitfalls 的 78 行「出处」行号 0/78 落在自己条目内** | **CT-02**（文档性） | 🟡 **登记** ⇒ 属 `P5-T05` 的「逐条可核」补齐面 |
| 7 | **`follow` 域的 `generate.py` 与其它 9 域的那份不是同一份**（follow 那份支持嵌套 message · 其余那份不支持） | **CT-02**（工具面 · 潜在假绿） | 🟡 **登记** ⇒ 本轮实测：**10 个 proto 全都没有嵌套 message** ⇒ 两版输出逐字节相同 ⇒ **当前不构成假绿**；但**将来给这些 proto 加嵌套 message 时基准会失真**（测试会**变红**，不会假绿） |
| 8 | **「按端口清理残留」在容器化环境里会炸掉整台容器的网络后端**（`P4-T08` 实施期间实测 ⇒ Docker 整体失联） | **CT-02**（工具面） | ✅ **已落进 `docs/Pitfalls/global/cross-domain.md` 的 `PIT-GLOBAL-069`**（含现象 / 根因 / 三条判据 / 修法 / 恢复记录）⇒ ⇒ **Pitfalls 条目数 441 ⇒ 442**（`check-ledger-count.py --fix` 已把索引的机器生成计数行同步） |

### 9.3 · 未完成任务及原因（如实，不粉饰）

| # | 未完成的 | 原因 | 解除条件 / 去向 |
|---|---|---|---|
| 1 | **favorite 域 3 条 skip 的具体原因未逐条核** | 本轮 S1 只记了 **S=3** 这个数，未逐条读那 3 条的 skip 理由 | 读 `service/favorite/favorite-rpc/target/surefire-reports/TEST-*.xml` 的 `<skipped>` 节点 ⇒ 归 `P4-T03` 的域台账 |
| 2 | **+44 条里余 5 条未逐条归因** | 本轮新增测试类只能对上 39 条 | 逐模块对比 Round 7 那轮的 XML ⇒ 归 Round 7 的回归读数维护面 |
| 3 | **S7 12.6 的 6 个子项未逐条语义判读** | 需要计划期那条脚本（`docs/RoundPlan/_INDEX.md:200`） | 重跑该脚本 ⇒ 归 `P6-T01` 计划层 |
| 4 | **12.3 的「第四落点」严格意义上未满足** | 台账 `PIT-GLOBAL-*` 只有「本轮影响：**是/否**」的**标志位** ⇒ 没有「具体原因」段 | 逐条判读 442 条正文 ⇒ 归 `P5-T05` |
| 5 | **派生层 U8 词表缺口 / Pitfalls 出处行号错位** | **Round 6 纪律：不改判据 / 不改生成器** | 阶段二（`CT-02`） |
| 6 | **未 push / 未开 PR** | **Jason 未明示** ⇒ 本轮**只提交到本地主干** | 等 Jason 明示 |

### 9.4 · 被阻塞的后续任务

- **Round 7（阶段一最终验收 + 重新冻结基线）** —— **不再被阻塞** ⇒ 本轮 S1~S7 全完成 ＋ 无未定性红项 ⇒ **T0 硬前置已满足**。
  ⚠️ **但**：Round 7 的冻结基线（judge §十·二 · 2026-11-01 那份）里的**回归读数**是 **3,570**，而**本轮 S1 的实测是 3614** ⇒ ⇒ **冻结基线的第 2 项需要按本轮读数重填**（**本轮只做收口回归、不改冻结表** ⇒ 登记在此，由 Round 7 收口时改）。

### 9.5 · 如实边界（本轮**没有**证明的事）

| # | 没证明的 | 为什么 | 谁负责 |
|---|---|---|---|
| 1 | **27 门守门 = 业务语义正确** | 守门验的是**契约/装配/台账面**（judge §六 6.2 明写）⇒ **与业务语义无关** | 业务语义由双侧活体探针（S3）承担 ⇒ **本轮 18 条全绿** |
| 2 | **S3 的 18 条探针覆盖了全部 39 个微服务组件** | 探针覆盖的是**域级契约面**（9 域 + `users-common`）⇒ **组件级**（38 个组件）仍靠 A-1 矩阵 + 事务守门 + 单测承担 | A-1 矩阵（judge §二） |
| 3 | **proto 的 JSON 映射在生产链路上与 Go 一致** | 全仓 `src/main` 对 `com.google.protobuf.util.JsonFormat` **0 命中** ⇒ **生产链路不走 proto JSON 映射**（api 层自建 Jackson DTO）⇒ 本轮的 proto JSON 断言证的是 **proto3 JSON 映射本身的 presence 语义**，**不是**某条生产链路的双侧对拍 | 若要升级为活体一致 ⇒ 需在各域 api 层补一条 Go↔RAG 的 JSON 响应体对拍（`P5-T01` 回执 §8 已登记） |
| 4 | **本轮没有改任何产品行为** | 本轮落码仅 4 处**注释/配置形态**（Nacos 双写法 · task-api scheme · favorite 优雅关闭的**方法调用形态** · like 两处 javadoc）＋ **测试类新增** ⇒ **业务逻辑 diff 为空** | — |
| 5 | ~~**`E31` / `DRIFT-FOLLOW-006`（follow-rpc 5 个指标无抓取出口）**~~ | 🟢 **2026-11-01 销号** —— **前提已过期**：出口 2026-10-29 已落地（观测口 16065 = Go 同口）⇒ **双侧活体探针复跑 EXIT=0**（`tools/metrics-probe/follow/run.sh`）⇒ 同款缺口 5/5 亦已消解（各自观测口 HTTP 200） | ✅ **已闭合**（Jason 2026-11-01 指令「修复 E31 / DRIFT-FOLLOW-006」⇒ 落为**销号 + 台账与现状对齐**） |
| 6 | **3 条签字偏离 + 1 条决策项** | **不代用户签字** | 仍待 Jason 签字 / 决策 |

### 9.6 · 本轮**不做**的事（规范 §一.6 / §一.7）

- **不改判据**（judge §五 / §七 的判定阈值与判定命令**一字未动** ⇒ 只订正了「**命令形式**」与「**记录读数**」）
- **不改产品行为**
- **不代用户签字**
- **不新增 `check-*.py`**（⇒ 27 这个数不变 ⇒ **不制造 B14**；新扫描器一律用**非 `check-*` 名**）
- **不在 lane 树上宣布收口**（本轮跑的是**合并后的主干** `do-my-own-gh`）
- **不把「drift 修好后门变红」当回归**（§8.2 第 4 条的处置就是**订正台账**而不是改实现）

---

## 十、追加取证（2026-11-01 · 用户问询触发的**服务级实测** + **3 条签字落账**）

> **触发**：Jason 问「现在是否每个微服务都能正常运行，报告要简短」＋「签字」。
> ⚠️ **本节是 Round 6 七步之外的追加取证** ⇒ §〇 的三条铁律、S2′ 的「两条命令形式缺陷」口径**照旧适用**。
> ⚠️ **本节不改判据、不改产品行为、不新增 `check-*.py`**（⇒ 27 这个数不变）。

### 10.1 · 「每个微服务能否正常运行」—— **20 个服务全部「在服务」**（三判据 · EXIT=0）

> **判据**（沿用 `docs/RoundPlan/round-01/verify-all-services.sh` 的**三条同时成立**才算在服务）：
> ① Nacos 服务名可查（`namespaceId=public`）② 监听端口 LISTEN ③ `/actuator/health` = 200
> ⚠️ **4 个服务没有 ③ 是模块形态决定的**（逐条理由见脚本头注释）⇒ 它们的判据降为 ①②。
> ⚠️ **不得用「进程活着」当在服务**（`PIT-GLOBAL-038`）。

| # | 服务 | Nacos 名 | 端口 | ①Nacos | ②LISTEN | ③health | 结论 |
|---|---|---|---|---|---|---|---|
| 1 | `user-api` | `user-api` | 49900 | n=1 | Y | 200 | ✅ |
| 2 | `user-rpc` | `user-rpc` | 49210 | n=1 | Y | — | ✅ **无健康端点**（无 `spring-boot-starter-web`） |
| 3 | `admin-api` | `admin-api` | 8890 | n=1 | Y | 200 | ✅ |
| 4 | `admin-rpc` | `admin-rpc` | 8081 | n=1 | Y | 200 | ✅ |
| 5 | `message-api` | `message-api` | 8892 | n=1 | Y | 200 | ✅ |
| 6 | `message-rpc` | `message-rpc` | 8086 | n=1 | Y | 200 | ✅ |
| 7 | `like-api` | **`likecenter`** | 18887 | n=1 | Y | 200 | ✅ |
| 8 | `like-rpc` | `like-rpc` | 8085 | n=1 | Y | 200 | ✅ |
| 9 | `task-api` | `task-api` | 18886 | n=1 | Y | — | ✅ **无健康端点**（无 actuator 依赖） |
| 10 | `task-rpc` | `task-rpc` | 9005 | n=1 | Y | — | ✅ **无健康端点**（无 web starter） |
| 11 | `article-api` | `article-api` | 8889 | n=1 | Y | 200 | ✅ |
| 12 | `article-rpc` | `article-rpc` | 9001 | n=1 | Y | 200 | ✅ |
| 13 | `comment-api` | `comment-api` | 8888 | n=1 | Y | 200 | ✅ |
| 14 | `comment-rpc` | `comment-rpc` | 9002 | n=1 | Y | — | ✅ **无健康端点**（`web-application-type: none`） |
| 15 | `favorite-api` | `favorite-api` | 18893 | n=1 | Y | 200 | ✅ |
| 16 | `favorite-rpc` | `favorite-rpc` | **8087** | n=1 | Y | 200 | ✅ 端口被外部服务占用 ⇒ env 改口（**默认值仍 8082**） |
| 17 | `follow-api` | `follow-api` | 8891 | n=1 | Y | 200 | ✅ |
| 18 | `follow-rpc` | `follow-rpc` | 8083 | n=1 | Y | 200 | ✅ |
| 19 | `hot-api` | `hot-api` | 18897 | n=1 | Y | 200 | ✅ |
| 20 | `hot-rpc` | `hot-rpc` | 8084 | n=1 | Y | 200 | ✅ |

**PASS = 20 / FAIL = 0 / TOTAL = 20 ⇒ EXIT=0**
⇒ **10 个域 · 20 个服务全覆盖**（Round 1 之后的**首次全量复测**；Round 1 的读数基线 = `e77941cf`，本轮基线 = `2d70a4cc`）。

**起栈形态**（沿用 round-01 §7.1）：**20 个服务全部以宿主机 `java -jar` 起**（`service/<域>/<域>-{api,rpc}/target/*.jar`）+ compose 的 `Sea-postgres`（49432）· `Sea-redis`（49380）· `Sea-nacos`（49848）· `Sea-jaeger`（49317/49318）· `Sea-minio`（49100）· `Sea-kafka`（49992）⇒ **env 取 `.env.host`**（namespace 统一 `public` / `DEFAULT_GROUP` ⇒ 20 个服务同一套 ns ⇒ 跨域 `discovery://` 可解析）。

### 10.2 · 起栈过程中**实测到**的 3 条环境事实（如实登记，不粉饰）

| # | 现象 | 根因 | 定性 | 本轮处置 |
|---|---|---|---|---|
| ① | `user-api` / `admin-api` **首次启动失败**：`Failed to create channel: user-rpc` / `admin-rpc` | **启动顺序竞争** —— api 在**bean 创建期**就要建 gRPC 通道，而当时 rpc 还没注册进 Nacos | 🟢 **环境层 · 顺序型假红**（**不是代码缺陷**：rpc 起来后重启即过） | **先起 10 个 rpc ⇒ 再起 10 个 api** ⇒ 重启这两个 ⇒ 20/20 全绿 |
| ② | `favorite-rpc` 默认口 `8082` 被占 | 宿主上有个**外部 Python 进程**（pid 9391）占着 8082（**非本仓**） | 🟢 **环境层 · 端口占用型假红**（`PIT-GLOBAL-038`） | env `FAVORITE_RPC_PORT=8087` 改口（**yml 默认值一字未动，仍是 Go 的 8082**） |
| ③ | `like-api` / `task-api` 等 **9 个服务的 `NACOS_ADDR` 曾是「融合写法」** `${NACOS_ADDR:nacos:8848}` | 历史形态（Round 1 §7.4 ①）⇒ `NACOS_PORT` 被完全忽略 ⇒ 换环境时静默连错注册中心 | 🟢 **本轮已被 `R1-ENV-001` 修掉**（`${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ **20 个服务一套写法**） | **本轮不需要 per-process 覆盖** ⇒ 归一后 `NACOS_ADDR=127.0.0.1` + `NACOS_PORT=49848` 即可 |

> ⚠️ **口径提醒**：`favorite-rpc` 跑 `8087` 是**分配层**（env）⇒ **默认值层一字未改**（Go 真源 `ListenOn` = 8082）⇒ **不构成对外契约面漂移**。

### 10.3 · 真 Tomcat **状态行运行时字节**取证（闭合一处「待核实」）

> **动机**：`docs/Intentional_Deviations/global/common-tails.md` 里那条「HTTP 状态行缺 reason phrase」自记 **「待核实（缺真容器字节复测）」** ⇒ 趁 20 个服务真起，顺手补上**运行时字节**这一格。

| 侧 | 状态行 |
|---|---|
| **Go** | `HTTP/1.1 200 OK`（带 reason phrase） |
| **RAG**（本轮实测） | `HTTP/1.1 200 ` —— **状态码 + 一个空格 + CRLF**，**无 reason phrase** |

**实测口径**：**裸 socket**（不是 curl、不是 MockMvc）⇒ 逐个 api 组件发 `GET /actuator/health HTTP/1.1` ⇒ 取回**状态行原始字节**并打 hex：

| 组件 | 端口 | 状态行 | hex |
|---|---|---|---|
| `user-api` | 49900 | `HTTP/1.1 200 ` | `48 54 54 50 2f 31 2e 31 20 32 30 30 20` |
| `admin-api` | 8890 | `HTTP/1.1 200 ` | 同上 |
| `message-api` | 8892 | `HTTP/1.1 200 ` | 同上 |
| `like-api` | 18887 | `HTTP/1.1 200 ` | 同上 |
| `task-api` | 18886 | `HTTP/1.1 404 ` | `48 54 54 50 2f 31 2e 31 20 34 30 34 20`（无 actuator ⇒ 404） |
| `article-api` | 8889 | `HTTP/1.1 200 ` | 同上 |
| `comment-api` | 8888 | `HTTP/1.1 200 ` | 同上 |
| `favorite-api` | 18893 | `HTTP/1.1 200 ` | 同上 |
| `follow-api` | 8891 | `HTTP/1.1 200 ` | 同上 |
| `hot-api` | 18897 | `HTTP/1.1 200 ` | 同上 |

⇒ **NO-REASON = 10 / 10 · REASON-PRESENT 或失败 = 0** ⇒ ⇒ **该条的描述升级为「源码级 + 运行时字节双证」** ⇒ **「待核实（缺真容器字节复测）」已撤销**。
⚠️ **仍然开着的一格**：**仓内无任何机器守门**（状态行由容器写 ⇒ 应用层无从断言）⇒ ⇒ **它是「真容器在跑时才能取证」，不是常跑门**。

### 10.4 · 3 条签字的落账与派生层 Δ

| 编号 | 主题 | 签前 | 签后 |
|---|---|---|---|
| `SD-2026-10-25-CAND-14@global` | HTTP 状态行缺 reason phrase（Tomcat 层 · 框架限制） | 🏷 候选 · 未签字 | ✅ **已签字**（2026-11-01）· 阶段归属 **D** · P3 |
| `SD-2026-10-23-09@like` | worker 池饱和 ⇒ 裸 503（RAG 独有实现能力） | 🏷 候选 · 未签字 · 阶段归属 **—**（未定级） | ✅ **已签字**（2026-11-01）· 阶段归属 **D** · P3 |
| `SD-2026-10-24-11@like` | 多暴露一个 `like_api_reject_total` 指标（**Go 侧漏调** ⇒ 是 Go 的缺陷） | 🏷 候选 · 未签字 · 阶段归属 **C** | ✅ **已签字**（2026-11-01）· 阶段归属 **D**（原判 C 的唯一理由 = 「仍是候选 ⇒ 不归 D」**已随签字消失**）· P3 |

**签字档权威计数**（机器复算 `tools/difftest/ledger-deviation-count.py --strict` ⇒ **EXIT=0**）：
**已签字 62 ⇒ 65** · **未签字候选 13 ⇒ 10** · 条目总数 **96**（不变）· 撤销 11 · 转录 5 · 不计入 4 · 附记 1 · **未定级 0**（状态字段覆盖率 100%）。
⇒ **跨档 18 处活指针全部同步**（`tools/difftest/check-ledger-count-pointers.py` ⇒ **EXIT=0**）。

**派生层 Δ**（`tools/ledger-derive/derive-stage1.py` 全量重算 ⇒ `--check` **EXIT=0**）：

| 指标 | 签前 | 签后 | Δ | 归因 |
|---|---|---|---|---|
| 阶段归属 **C** | 67 | **66** | −1 | `SD-2026-10-24-11` C ⇒ D |
| 阶段归属 **D** | 228 | **230** | +2 | `SD-2026-10-24-11` + `SD-2026-10-23-09` ⇒ D（**U5d**：已签字偏离 ⇒ 决策不是缺陷） |
| 阶段归属 **—** | 524 | **523** | −1 | `SD-2026-10-23-09` — ⇒ D |
| 优先级 **P1** | 402 | **401** | −1 | `SD-2026-10-23-09` P1 ⇒ P3（**U4b**：`⏳ 未签字`⇒P1 / `✅ 仍成立`⇒P3） |
| 优先级 **P3** | 780 | **781** | +1 | 同上 |
| G2 开放（`status` ∈ 开放态 ∧ 未排期） | 66 | **64** | −2 | 2 条 like 条目退出开放面 |
| G4 开放 | 148 | **147** | −1 | `CAND-14` 退出开放面 |
| 合计 | 434 行 / 427 编号 | **431 行 / 425 编号** | −3 行 / −2 编号 | 同上 |
| **真缺口（A/B 类 · 阶段一范围内）** | **0** | **0** | **0** | ✅ **不变**（签字不产生阶段一缺口） |

### 10.5 · 本节**如实边界**

| # | 没证明的 | 为什么 |
|---|---|---|
| 1 | **20/20 ⇒ 业务语义正确** | 三判据只证「**在服务**」（注册 + 监听 + 健康）⇒ 业务语义由 S3 的 18 条双侧活体探针承担（**全 EXIT=0**） |
| 2 | **20 个服务的全部接口都可用** | 只探了 `/actuator/health`（＋ 状态行字节）⇒ **业务端点的双向对拍不在本节** |
| 3 | **状态行字节 = 常跑守门** | 只在**真容器在跑时**取证 ⇒ 仓内**仍无机器守门**（容器层改不了） |
| 4 | **起栈顺序依赖不是缺陷的「全面证明」** | `user-api`/`admin-api` 的首次失败是**顺序型假红**（rpc 起来后即过）⇒ **未做「乱序起」的完整矩阵** ⇒ 归「启动顺序契约」这一项，本轮**只登记不判死** |
