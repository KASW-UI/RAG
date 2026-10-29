# Round 4 · B / C / D 类残留清零（9 张派工单）

> **本轮目标**：把 Round 5 之前**能独立执行、不依赖新证据**的残留全部清掉 —— B 类 2 张 · C 类 1 张 · D 类 5 张 · 取证 1 张。
> **本轮不执行整改**（规范 §一.6 / §一.7）—— 本文件是**计划**，不是执行记录。

---

## §5.1 本轮目标与结束状态

| 项 | 内容 |
|---|---|
| **处理哪些已知问题** | ① `R5-F1` / `R5-F2`（**B 类**）—— 10 处 datasource 口令 fallback ② `R5-F7`（**C 类**）—— `DRIFT-CF-018` 的 favorite 域半边（进程无法优雅关闭）③ `R5-F9` / `R5-F10` / `R5-F3` / `R5-F6` / `R5-F5`（**D 类**）—— 台账过期数字 · bash 3.2 变量名坑 · 延迟项登记 · 15 处引用指错 · 探针 Nacos 隔离 ④ `R5-F4`（**取证**）—— G-19 proto 消费方式取证 |
| **产生哪些可检查的结果** | ① **10 处** `${POSTGRES_PASSWORD:password}` / `${MINIO_SECRET_KEY:minioadmin}` 归零 ② favorite 域**进程可优雅关闭**（`@PreDestroy` 拿到非 null handle）③ `check-ledger-selfcheck.py` **由红转绿** ④ 全仓 bash 3.2 变量名坑 **0 处** ⑤ `DRIFT-USERS-LAT-001` 带解除条件登记 ⑥ 15 处引用指错**逐条有依据** ⑦ 探针并发跑**互不污染** |
| **为什么是下一轮的必要前提** | ① Round 6 的收口回归要求 **27 个守门全绿** ⇒ `R5-F9` 必须先修（否则 `check-ledger-selfcheck.py` 一直红）② Round 5 的台账对账**不能与本轮的改动撞车**（同一批台账文件）③ Round 7 的最终验收的 P0 #8 残留项（`S1-USERS-002`）依赖 `R5-F1`/`R5-F2` 清零 |
| **本轮不做** | 不改判据（Round 3）· 不改产品代码以外的任何行为面 · **不改已签字偏离**（48 条）· 不改 `tools/difftest/` 的守门清单计数口径 · 不做阶段二 · **不把 18 条未签字候选升级为拍板** |

---

## §5.2 开始条件

| 条件 | 状态 | 证据 |
|---|---|---|
| **Round 3 已完成**（基线复判） | ⚠️ **建议前置** | 本轮**不严格依赖** Round 3（`R5-F9` 除外：它要按复判后的口径改台账）⇒ **可与 Round 3 并行**，但 `P4-T04` 建议等 Round 3 |
| **各域 lane 可写本域台账** | ✅ | `AGENTS.md` §5.3b / §5.3f：**域侧能自修的域侧修；共享层缺陷一律转派 G4** |
| **`tools/<kind>/<域>/` 写权已裁定** | ✅ | 域内探针目录归**对应域 lane**（`check-wave-lane-ownership.py` 白名单已含）⇒ `R5-F5` 落在 `tools/api-full-parity-probe/article/**` = G3 |
| **R5 派工单体系已建立** | ✅ | `docs/RoundPlan/Round-5.md` §三（**该文件已归档为历史**，新编号见 `_migration.md`） |
| **无前置任务** | ✅ | 本轮可与 Round 3 并行 |

> ⚠️ **写权提醒（每条都踩过）**：① `service/common/**` + `service/users/users-common/**` ⇒ **只有 G4 能写**（`R5-F3` 归 G4）② `service/favorite/**` ⇒ **favorite 域对 `global` 零写权**，但 **G3 作为域 lane 可以写 favorite 自己的代码**（`R5-F7` 归 G3）⇒ ⚠️ **共享层改动**必须**记账 + 通知 favorite owner** ③ `docs/DRIFT/task/**` ⇒ **task 域写权面**（`R5-F9` 不越界改）④ `service/hot/**` ⇒ hot 域写权面（`R5-F10` 不越界改）。

---

## §5.3 逐项任务清单

### P4-T01 · 取消各域 rpc 的 datasource 口令 fallback（R5-F1）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T01` |
| **原始编号** | `R5-F1` · `S1-USERS-002` |
| **来源** | `docs/RoundPlan/Round-5.md:51`（历史文件）· `docs/DRIFT/users/stage1-admission.md:107` |
| **当前状态** | 🟡 **B 类 · 已登记未修**（`S1-USERS-002`）—— 落点在**各域自己的 rpc 模块** ⇒ **域侧可自修**（台账原写「归 G4 共享层」是**错的**，已在 judge §七 P0 #8 行订正） |
| **当前事实** | **10 处**：`${POSTGRES_PASSWORD:password}` **× 9**（like / comment / follow / message / favorite / admin / task 各 1 · **article-rpc 3**）＋ `${MINIO_SECRET_KEY:minioadmin}` **× 1**。Go 侧 yaml **无默认值** ⇒ `os.ExpandEnv` 不认 `${VAR:default}` ⇒ 未设时展开为空串 ⇒ **空口令连库 ⇒ fail-fast**；RAG 侧**静默用硬编码口令** ⇒ 两种默认值并存 ⇒ **裸跑时 9 个模块里最多一组能连上** |
| **执行目的** | 消除「同一配置项两种默认值」的分裂（RAG 比 Go 松 ⇒ 属**有意偏离**，需签字 —— 已登记 `S1-USERS-002`） |
| **具体操作** | ① 逐域把 `${POSTGRES_PASSWORD:password}` ⇒ `${POSTGRES_PASSWORD}`（**无默认值**）② `${MINIO_SECRET_KEY:minioadmin}` ⇒ `${MINIO_SECRET_KEY}` ③ ⚠️ **这会让「裸跑」失败**（无 env ⇒ 空口令 ⇒ 连不上）⇒ **测试必须自备环境变量**（`mvn test` 前 `export POSTGRES_PASSWORD=...`）④ 10 处**逐处**改（article-rpc 是 3 处，别只改 1 处） |
| **具体落点** | `service/{like,comment,follow,message,favorite,task,article}/<域>-rpc/src/main/resources/application.yml` · `service/users/admin/admin-rpc/src/main/resources/application.yml` |
| **执行主体** | **各域 lane**（like / message / task ⇒ G2 · article / comment / favorite ⇒ G3 · admin ⇒ G1） |
| **前置条件** | 无 |
| **并行关系** | 各域**可并行**（文件不重叠）⇒ ⚠️ **同模块 `target/` 不能并发** ⇒ 分波次 |
| **写权限** | `service/<域>/**` = 各域 lane；⚠️ **不得**碰 `service/common/**` |
| **验收命令** | ```bash# ① 全仓 fallback 归零（排除 target/）\ngrep -rnE '\$\{(POSTGRES_PASSWORD|MINIO_SECRET_KEY):[^}]' service/ --include='*.yml' --include='*.yaml' | grep -v '/target/'\n# 期望：0 命中\n# ② 各模块测试（必须自备环境变量，否则会连不上）\nexport POSTGRES_PASSWORD=password MINIO_SECRET_KEY=minioadmin\nmvn -o -B -DargLine=-Xmx2g -pl service/<域>/<域>-rpc -am test\n# ③ 密钥门\npython3 tools/difftest/check-no-prod-secrets.py``` |
| **验收证据** | ① `grep` **0 命中** ② 各模块 0F/0E ③ 密钥门 EXIT=0 |
| **完成条件** | **10 处逐处销号**（能逐个指出文件与行号） |
| **失败处理** | 某域测试因缺 env 而红 ⇒ **补 env 变量**（不是改回默认值）⇒ 记录「测试需自备 `POSTGRES_PASSWORD`」 |
| **状态更新** | 各域台账 + `S1-USERS-002`（users 域写权面 ⇒ G1） |
| **后续任务** | `P4-T02` · Round 7（P0 #8 残留项） |

### P4-T02 · 同上，限 `admin-rpc`（R5-F2）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T02` |
| **原始编号** | `R5-F2` · `S1-USERS-002` |
| **来源** | `docs/RoundPlan/Round-5.md:52`（历史） |
| **当前状态** | 🟡 同 `P4-T01`（**限 `admin-rpc`**） |
| **当前事实** | `service/users/user/user-rpc` **无 `application.yml`**（不直连 PG）⇒ **本就不在清单里** ⇒ 本任务实际只覆盖 `service/users/admin/admin-rpc`（**注意路径**：`service/users/admin/admin-rpc/`，不是 `service/users/user/…`） |
| **执行目的** | 同 `P4-T01` |
| **具体操作** | ① 把 `service/users/admin/admin-rpc/src/main/resources/application.yml` 里的 `${POSTGRES_PASSWORD:…}` ⇒ `${POSTGRES_PASSWORD}` ② ⚠️ **复核**：`service/users/user/user-rpc` 确实没有 `application.yml` ⇒ 若有 ⇒ 说明清单过期 ⇒ **补进 `P4-T01`** |
| **具体落点** | `service/users/admin/admin-rpc/src/main/resources/application.yml` |
| **执行主体** | **G1**（users 域） |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01` 可并行 |
| **写权限** | `service/users/admin/**` = G1 |
| **验收命令** | 同 `P4-T01`（限 `service/users/**`） |
| **验收证据** | 同 `P4-T01` |
| **完成条件** | users 域 fallback 归零 |
| **失败处理** | 同 `P4-T01` |
| **状态更新** | 同 `P4-T01` |
| **后续任务** | Round 7 |

### P4-T03 · favorite 域 `DRIFT-CF-018` 半边：进程可优雅关闭（R5-F7）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T03` |
| **原始编号** | `R5-F7` · `DRIFT-CF-018` |
| **来源** | `docs/RoundPlan/Round-5.md:57`（历史）· `docs/DRIFT/global/vs-common.md`（共享层半边已修） |
| **当前状态** | 🔴 **C 类 · 已派单未执行**（共享层半边**已修**，含变异 3 组精确转红；**favorite 域半边仍成立**） |
| **当前事实** | `service/favorite/favorite-rpc/.../FavoriteFactDispatchScheduler.java:119` **仍调阻塞版 `Dispatcher.run(...)`** ⇒ `once=false` 时**句柄永远拿不到** ⇒ `@PreDestroy stop()` 拿到 null ⇒ **进程仍无法优雅关闭** |
| **执行目的** | 共享层已给出「**停止能力归调用方**」的形态（阻塞重载标 `@deprecated` + 提供「调用方先造句柄」的重载）⇒ 域侧要跟上 |
| **具体操作** | ① `Dispatcher.run(...)` ⇒ `Dispatcher.runAsync(...)`（与 `CommentFactDispatchScheduler.java:93` **逐字对齐**）② 补**优雅关闭测试**：开 `favorite.fact.dispatch.enabled=true` ⇒ 发起关闭 ⇒ 断言**进程在 N 秒内退出**且 `@PreDestroy` 拿到**非 null** handle ③ ⚠️ **共享层行为变更要记账** ⇒ 若触碰 `service/common/**` ⇒ **必须同一次提交 + 通知 favorite owner**（favorite 对 global 零写权） |
| **具体落点** | `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteFactDispatchScheduler.java:119` + 对应测试 |
| **执行主体** | **G3**（favorite 域） |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01`/`P4-T02` 可并行 |
| **写权限** | `service/favorite/favorite-rpc/**` = G3；⚠️ `service/common/**` = G4 ⇒ **若需动共享层 ⇒ 必须转 G4 且同一次提交** |
| **验收命令** | ```bashmvn -o -B -DargLine=-Xmx2g -pl service/favorite/favorite-rpc -am test\n# 优雅关闭测试必须存在且绿；且要变异必红（改回 run(…) ⇒ 必须红）``` |
| **验收证据** | ① 优雅关闭测试**存在**且 0F/0E ② 变异必红 ③ 共享层若有改动 ⇒ 记账 + favorite owner 知会记录 |
| **完成条件** | **进程可优雅关闭**（`@PreDestroy` 拿到非 null handle） |
| **失败处理** | 关闭仍挂住 ⇒ 先查是否还有**别的**阻塞点（`Dispatcher` 的其它调用点）⇒ **不要**加大超时掩盖 |
| **状态更新** | `docs/DRIFT/favorite/rpc.md`（域写权面）· `docs/DRIFT/global/vs-common.md`（G4 面，若共享层有改动） |
| **后续任务** | Round 7 |

### P4-T04 · task 域台账两处过期数字（R5-F9）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T04` |
| **原始编号** | `R5-F9` |
| **来源** | `docs/RoundPlan/Round-5.md:59`（历史）· `docs/DRIFT/task/rpc.md:29`（标题表写 134）vs `:1633`/`:1672`（写 137）· `docs/DRIFT/task/vs-common.md:1433`（134）· `docs/DRIFT/task/vs-common.md:1525`（引「task-api 102 条」）vs `docs/DRIFT/task/api.md:30`（**135**） |
| **当前状态** | 🔴 **D 类 · 机器可判 · 当前是全仓唯一红的守门**（`check-ledger-selfcheck.py` ⇒ EXIT=1） |
| **当前事实** | **同一台账两个权威数**：`task/rpc.md:29` 写 **134**（标注「2026-10-27 G2 第八轮实测」），同文件 `:1633`/`:1672` 写 **137**（2026-10-29 G2 收工轮 `clean test` 实测）且明写「**134 是【推算】不是实测**」⇒ **跨文件也矛盾**（`task/vs-common.md:1433` 的 134）。⚠️ **还有一处过期引用**：`task/vs-common.md:1525` 写「task-api 有 **102** 条测试」，而 `task/api.md:30` 现值是 **135** ⇒ 引用已过期。⚠️ **不要**把 `137 − 3 = 134` 当成「推算被证实」（台账自己明写「**不得**做差值归因」） |
| **执行目的** | 守门红 ⇒ Round 6 的收口回归「27 个守门全绿」不可能达成 ⇒ 阶段一验收过不去 |
| **具体操作** | ① `task/rpc.md:29` 标题表 **134 ⇒ 137**（并保留原 134 的历史值并列，**不删**）② `task/rpc.md:1535` 的 `--all` 口径表违规（**裸跑 `check-ddl-static.py` 只查 users 域**，表里却按 `--all` 写）⇒ 改成「裸跑 = users 域 · `--indexes` / `--check-source` 逐域」③ `task/vs-common.md:1433` 的 134 ⇒ 137（跨文件对齐）④ `task/vs-common.md:1525` 的「102」⇒ 135 ⑤ 核对 `task/vs-common.md:1348`/`:1354`/`:1356`/`:1433`/`:1447` 的 134 是**历史值**还是**当前值** ⇒ 逐条判定（口径写死 ⇒ 不许含糊） |
| **具体落点** | `docs/DRIFT/task/rpc.md` · `docs/DRIFT/task/vs-common.md` |
| **执行主体** | **task 域 lane（G2 侧）** |
| **前置条件** | Round 3 完成（要按复判后的口径改） |
| **并行关系** | 与 `P4-T01`/`P4-T03` 可并行（不同文件） |
| **写权限** | `docs/DRIFT/task/**` = **task 域** ⇒ ⚠️ **主线不越界改** |
| **验收命令** | ```bashpython3 tools/check-ledger-selfcheck.py   ; echo "EXIT=$? （期望 0）"\npython3 tools/check-ledger-tables.py      ; echo "EXIT=$?"``` |
| **验收证据** | ① `check-ledger-selfcheck.py` **EXIT=0** ② 表门 EXIT=0 ③ 数字逐条有依据 |
| **完成条件** | **全仓唯一红的守门转绿** |
| **失败处理** | 门仍红 ⇒ 读它的逐条报错 ⇒ **只改它指出的那几处**（不要顺手改别的域） |
| **状态更新** | 即本任务 |
| **后续任务** | Round 6（收口回归）· Round 7 |

### P4-T05 · hot 域探针收尾行的 bash 3.2 变量名坑（R5-F10）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T05` |
| **原始编号** | `R5-F10` |
| **来源** | `docs/RoundPlan/Round-5.md:60`（历史）· `service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh:127` |
| **当前状态** | 🟡 **D 类 · 已派单未执行** |
| **当前事实** | 裸 `$JAVA_MOD）` ⇒ **macOS 自带 bash 3.2 把紧跟 `$VAR` 后的全角右括号算进变量名** ⇒ `set -uo pipefail` ⇒ `JAVA_MOD?: unbound variable` ⇒ **探针在收尾行崩掉**（工作做完了但**收尾输出打不出来、退出码非 0**）⇒ 只在 macOS 上犯 ⇒ 「我这儿是绿的」 |
| **执行目的** | 守门/探针**用错误的姿势失败** ⇒ 绿灯是假的 |
| **具体操作** | ① `$JAVA_MOD）` ⇒ `${JAVA_MOD}`；`$JAVA_GATE）` ⇒ `${JAVA_GATE}` ② 全仓同类扫一遍（本仓已有扫描器 `tools/difftest/scan-bash-legacy-vars.py`，**非 `check-*` ⇒ 不计入 §6.3 的 27 个计数**） |
| **具体落点** | `service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh:127` |
| **执行主体** | **hot 域 lane（G3 侧）** |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01`~`P4-T04` 可并行 |
| **写权限** | `service/hot/**` = hot 域 ⇒ ⚠️ **主线不越界改** |
| **验收命令** | ```bashpython3 tools/difftest/scan-bash-legacy-vars.py   ; echo "EXIT=$? （期望 0）"\nbash service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh ; echo "探针 EXIT=$? （期望 0）"``` |
| **验收证据** | ① 扫描器 **EXIT=0**（全仓 0 处活代码命中）② 探针本体退出码 **0** |
| **完成条件** | 扫描器绿 + 探针跑通 |
| **失败处理** | 扫描器还报 ⇒ 按它给的 `文件:行号` 逐条改（**只改裸 `$VAR` + 非 ASCII 的形态**） |
| **状态更新** | hot 域台账 |
| **后续任务** | Round 6 |

### P4-T06 · 登记 `DRIFT-USERS-LAT-001` + 写解除条件（R5-F3）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T06` |
| **原始编号** | `R5-F3` · `DRIFT-USERS-LAT-001` |
| **来源** | `docs/RoundPlan/Round-5.md:53`（历史）· `docs/DRIFT/users/_shared.md:1278` |
| **当前状态** | 🟡 **P3 / D 类 · 已登记**（`users/_shared.md:1282` 已定级） |
| **当前事实** | RAG 侧 HTTP 响应延迟被共享层的「**500ms 切片等待**」量化（实测 Go `max=319ms` vs RAG `max=2046ms`；**中位数 50 vs 51ms 几乎相同** ⇒ **两侧调用链层数相同**，不是跳数差）⇒ **负载放大时 RAG 比 Go 更早撞 2s/3s 超时** |
| **执行目的** | **登记 + 写解除条件**（**不落码**）⇒ 让后来人知道「什么条件下这条会变成真差异」 |
| **具体操作** | ① 在 `docs/DRIFT/global/vs-common.md`（或 `docs/DRIFT/common/alignment-2026-10-23.md`）登记：现象 + **证据强度**（L1/L3）+ **解除条件**（**空载复跑 `unexpected=0` ⇒ 纯负载敏感；仍超时 ⇒ 才是真差异**）② ⚠️ **不落码**（D 类 = 竞争力项）③ 登记时写明「落点 = 共享层 `service/common`（`HttpMiddleware.java:415-419`）⇒ **G4 写权**」 |
| **具体落点** | `docs/DRIFT/global/vs-common.md` |
| **执行主体** | **G4** |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01`~`P4-T05` 可并行 |
| **写权限** | `docs/DRIFT/global/**` = G4 |
| **验收命令** | ```bashpython3 tools/check-ledger-tables.py     ; echo "EXIT=$?"\npython3 tools/check-ledger-selfcheck.py  ; echo "EXIT=$?"``` |
| **验收证据** | 台账行有「现象 + 证据强度 + 解除条件」三项 |
| **完成条件** | 登记完成且**未落码** |
| **失败处理** | 表门红 ⇒ 按它报的列数修 |
| **状态更新** | 即本任务 |
| **后续任务** | Round 7 |

### P4-T07 · G-12 遗留 15 处「引用指错」逐条定位（R5-F6）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T07` |
| **原始编号** | `R5-F6` · `G-12` 遗留 15 处 |
| **来源** | `docs/RoundPlan/Round-5.md:56`（历史） |
| **当前状态** | 🟡 **D 类 · 已派单未执行** |
| **当前事实** | 15 处「原引用本就指错」—— 基线行本身是**空行 / 重复表头** ⇒ **早于 G-12 就指错**，**不是 G-12 引入的回归** |
| **执行目的** | 引用指错会让读者定位到错的地方 ⇒ 复判时可能拿错证据 |
| **具体操作** | ① 逐处用**上下文关键词**定位作者真正想指的行（**内容锚定，不是猜**）② ⚠️ **无法唯一定位时 ⇒ 宁可保留原样并写明「不可唯一定位」**，**不要瞎改**（台账原话：「瞎改比不改更糟」）③ 每处记录：原引用 + 新引用 + 依据（哪几个词的上下文决定了它） |
| **具体落点** | 各域台账的对应行 |
| **执行主体** | **各域 lane**（按行归属） |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01`~`P4-T06` 可并行 |
| **写权限** | `docs/DRIFT/<域>/**` = 各域 lane |
| **验收命令** | ```bashpython3 tools/check-ledger-selfcheck.py ; echo "EXIT=$?"\npython3 tools/g3-anchor-check/check-anchors.py --all ; echo "EXIT=$?\n# ⚠️ 口径：EXIT=0 可信；EXIT=1 不可信（实测假阳性率 59%）⇒ 报了就逐条回真源核``` |
| **验收证据** | ① 15 处逐条有依据（能说出为什么是这一行）② 不可唯一定位的**显式标注** ③ 锚点门逐条核过 |
| **完成条件** | 15 处**逐条**有结论（改了 or 标注不可定位） |
| **失败处理** | 锚点门报 `FILE_MISSING` ⇒ **先手工回真源核那个文件到底存不存在**（守门的候选选择规则本身是假阳性来源）⇒ 别信「找不到」 |
| **状态更新** | 各域台账 |
| **后续任务** | Round 5（对账）· Round 6 |

### P4-T08 · 探针的 Nacos 隔离（R5-F5）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T08` |
| **原始编号** | `R5-F5` · `PIT-GLOBAL-068` |
| **来源** | `docs/RoundPlan/Round-5.md:55`（历史）· `tools/api-full-parity-probe/article/**` |
| **当前状态** | 🟡 **D 类 · 已派单未执行** |
| **当前事实** | 共享 Nacos 服务发现面让**并发探针互相污染** ⇒ 探针报 `INTERNAL` / `5001` 是**已知串扰**，**不是缺陷** ⇒ 后果：**跑红 ≠ 是缺陷**（会误导下一轮判读） |
| **执行目的** | 让并发探针互不污染 ⇒ 消除一类假红 |
| **具体操作** | ① 探针用**独立的服务名/命名空间**（或独立的 Nacos 端口/实例）② 同一份探针**并发跑两份** ⇒ 互不报 `INTERNAL`/`5001` ③ ⚠️ **不改产品代码**（D 类 = 工具面） |
| **具体落点** | `tools/api-full-parity-probe/article/**`（**域内探针目录 ⇒ G3 写权**） |
| **执行主体** | **G3** |
| **前置条件** | 无 |
| **并行关系** | 与 `P4-T01`~`P4-T07` 可并行 |
| **写权限** | `tools/<kind>/<域>/` = 对应域 lane |
| **验收命令** | ```bash# 两份探针并发跑\na=tools/api-full-parity-probe/article/run.sh & b=tools/api-full-parity-probe/article/run.sh &\nwait $a; echo "A EXIT=$?"; wait $b; echo "B EXIT=$?"\n# 期望：两者都 EXIT=0 且互不报 INTERNAL/5001``` |
| **验收证据** | ① 两份并发**都 EXIT=0** ② 无 `INTERNAL` / `5001` 串扰 |
| **完成条件** | 并发跑**不再互相污染** |
| **失败处理** | 仍串扰 ⇒ 记录「隔离方案不可行 + 替代口径（串行跑 + 把 `INTERNAL`/`5001` 列入已知串扰白名单）」⇒ **不改产品代码** |
| **状态更新** | `docs/Pitfalls/global/**`（G4 面）+ hot/各域台账 |
| **后续任务** | Round 6 |

### P4-T09 · `like` 域源码注释里的 8 条错误断言（D-08）清理

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P4-T09` |
| **原始编号** | `D-08`（`docs/PhaseGoal-judge.md:567` · §十一 11.1「真实实现缺陷 · 必须修」类） |
| **来源** | `docs/DRIFT/like/rpc.md:216-238`（**8 条错误断言 A~H 的完整清单 + 每条的实测事实与出处**）· `docs/DRIFT/like/rpc.md:891`（「首轮 8 条错误断言 · **仍然全部在位**」）· `docs/DRIFT/like/rpc.md:856`（**第 9 条错误断言 I**）· `docs/PhaseGoal-judge.md:567` |
| **当前状态** | 🔴 **未做**。台账明写「**仍然全部在位**（A/B/C/D/E/F/G/H 的原文仍留在源码注释里）」⇒ 且已新增**第 9 条 I**（`V4__create_like_domain_fact_outbox.sql` 的「GORM 对无 size 的整数统一取 bigint」⇒ 已被 `gorm.io/driver/postgres@v1.6.0` 的 `DataTypeOf` 证伪：`int32`（Size=32）返回 `integer`） |
| **当前事实（8 条）** | **A** 「GORM 扫 `created_at` 取 `data.Unix()` ⇒ 秒/毫秒刻度不一致是真实可观察行为」⇒ **GORM 根本没匹配上该列**，`timestamp` 恒 0 ⇒ 整段分析描述的是**不可达路径**（`LikeDomainService.java:374-386,944-951`）· **B** 「Go 的 `Updates` 用 map 形式不会带 `updated_at`」⇒ **会带**（`ConvertToAssignments` map 分支显式追加；真 PG 实测）（`LikeRecordMapper.java:194-200`）· **C** 「`kq.Pusher.Push(ctx, msg)` 不带 key」⇒ **带**（内部即 `PushWithKey(ctx, strconv.FormatInt(time.Now().UnixNano(),10), v)`）（`LikeDomainService.java:1014`、`LikeHotEventOutboxRelay.java:80`）· **D** 「Go `Push` 是同步阻塞写 ⇒ 这里选 acks=1 + 异步」⇒ `NewPusher` 默认挂 `ChunkExecutor`，**只入队即返回**（`LikeDomainService.java:984-985`、`application.yml:91-94`）· **E** 「不做进程内重试……若加 `FixedBackOff` 是新增行为」⇒ `new DefaultErrorHandler(recoverer)` **内部就是 `FixedBackOff(0,9)`**（`LikeUpdateKafkaListener.java:44-52,170-175`）· **F** 「这五个装配点每一条都有对应的 wiring 测试锁死」⇒ **4 个测试类一个都不存在**，模块**零** Spring 上下文测试（`LikeRpcApplication`）· **G** outbox relay 注释「`Push` 不带 key，且用 payload 原文」⇒ 同 C（「用 payload 原文」这半句是对的）· **H** 「Go 侧 etcd 注册失败会让 `MustNewServer` panic ⇒ 进程退出」⇒ **未核实**，标 UNKNOWN（`NacosRegistration.java:91-92`） |
| **阶段一影响** | ⑤ **仅为执行约束 / D 类文档性缺陷**（judge §十一 把它列在「真实实现缺陷」类，但性质是**源码注释里的假断言**）⇒ **不阻阶段一**（不破坏核心正确性 / 安全 / 数据完整性）⇒ 按 §1.1 ⑥ 第 4 条不计入阶段一判定 |
| **执行目的** | 台账原话：「修相邻代码时应**顺手清理**这 8 条注释，否则会让**下一个人沿错误前提继续改**」⇒ A / C / D / E 是同一型错误（只读框架源码或文档就下**否定式或类比式断言**而未实证，且 A / D 描述的代码路径在 Go 侧**根本不可达**）· B 是**照搬 GORM 直觉未验证** · F 是「**把没看过的东西当成看过了**」的又一例（§3.3 同款） |
| **具体操作** | ① 逐条**改写或删除** A~H 的注释（**不是**加一句「其实不对」—— 要改成**实测事实**：A ⇒ 说明该列未被匹配、`timestamp` 恒 0；B ⇒ map 形式会追加 `updated_at`；C/G ⇒ `Push` 内部走 `PushWithKey`；D ⇒ `ChunkExecutor` 异步缓冲；E ⇒ `FixedBackOff(0,9)` 已在；F ⇒ 点名的 4 个测试类**不存在**，**零** Spring 上下文测试）② **H 单独处理**：要么补核实（查 Go 侧 `zrpc.MustNewServer` 的 etcd 失败路径）并写出事实，要么**删掉这条断言**（**不许**留一条 UNKNOWN 的断言在源码里）③ **第 9 条 I** 一并处理（`V4__create_like_domain_fact_outbox.sql` 的注释）④ ⚠️ **只改注释，不改行为**（本条是文档性缺陷 ⇒ 行为变更会越出本任务范围，且会污染行为层证据）⑤ ⚠️ 改完必须回 `docs/DRIFT/like/rpc.md` 把「仍然全部在位」改成销号状态，**并保留原文**（不许删历史行） |
| **具体落点** | `service/like/like-rpc/src/main/java/**`（`LikeDomainService.java` · `LikeRecordMapper.java` · `LikeUpdateKafkaListener.java` · `LikeHotEventOutboxRelay.java` · `NacosRegistration.java` · `LikeRpcApplication.java`）· `service/like/like-rpc/src/main/resources/db/migration/V4__create_like_domain_fact_outbox.sql` · `docs/DRIFT/like/rpc.md` |
| **执行主体** | **G2**（like 域 lane · 域内可自修） |
| **前置条件** | 无（可与 `P4-T01`~`P4-T08` 并行） |
| **并行关系** | 与 `P4-T01`~`P4-T08` 可并行（文件不重叠）⇒ ⚠️ **但同模块 `target/` 不能并发** ⇒ 与 `P4-T01`（like 域的 fallback 销号）**同域同批** |
| **写权限** | `service/like/**` = **G2（like 域 lane）** · `docs/DRIFT/like/**` = **G2** |
| **验收命令** | ```bash# ① 8 条断言的原文逐条不再在位（按断言特征串，逐条）\nfor s in 'data.Unix()' '不会带上' '不带 key' '同步' '不做进程内重试' 'wiring 测试' 'MustNewServer' 'bigint'; do\n  printf '%-24s ' "$s"; grep -rn "$s" service/like/like-rpc/src/main/java service/like/like-rpc/src/main/resources/db/migration 2>/dev/null | grep -v '/target/' | wc -l\ndone\n# ⚠️ 判定：每条要么 0 命中，要么命中的那行已是「修正后的事实陈述」⇒ 人工逐条核\n# ② 行为面零变化（只改注释）⇒ git diff 不得含非注释行\ngit diff --stat service/like/like-rpc/\ngit diff -U0 service/like/like-rpc/ | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' | grep -vE '^[+-]\s*(//|\*|/\*)' | head -20\n# 期望：最后一条命令无输出（⇒ 只改了注释）\n# ③ 域级测试仍绿（注释改动不该影响编译）\nmvn -o -B -DargLine=-Xmx2g -pl service/like/like-rpc -am test``` |
| **验收证据** | ① **A~H + I 共 9 条**逐条有「已改写 / 已删除 / 已补核实」结论 ② **行为面 diff 为空**（git diff 无非注释行）③ 域级测试 0F/0E ④ 台账销号行**保留原文** + 加时间线指针 |
| **完成条件** | **9 条断言全部处置**（A~H + I）⇒ 且**行为面零变化** ⇒ 且台账销号 |
| **失败处理** | 某条**无法核实**（如 H 的 Go 侧 `MustNewServer` etcd 路径查不到）⇒ **删除该断言**（**不许**留 UNKNOWN）⇒ 记「未核实 ⇒ 已删」 |
| **状态更新** | `docs/DRIFT/like/rpc.md`（销号 + 保留原文）· `docs/PhaseGoal-judge.md` §十一 D-08 行（Round 3 的面 ⇒ 证据在 Round 7 `P7-T01` 复核） |
| **后续任务** | Round 5（台账对账）· Round 6 · Round 7 |

> 📌 **本轮不含 `R5-F4`（G-19 proto 消费方式取证）** —— 它属于 **Round 5 的 `P5-T01`**（取证与台账对齐）⇒ **唯一去向**见 `_task-universe.md`。
> 📌 **本轮不含 `P4-T09` 之外的其它新发现** —— 其它项要么已完成，要么已在 Round 1/2/3/5 有唯一去向 ⇒ 见 `_migration.md` 的逐条去向表。

---

## §5.4 具体验收命令（本轮整体）

```bash
cd "/Users/pr/Documents/java&golang/RAG"

# ── ① 口令 fallback 归零 ────────────────────────────────
grep -rnE '\$\{(POSTGRES_PASSWORD|MINIO_SECRET_KEY):[^}]' service/ --include='*.yml' --include='*.yaml' | grep -v '/target/'

# ── ② 各域测试（自备 env）──────────────────────────────
export POSTGRES_PASSWORD=password MINIO_SECRET_KEY=minioadmin
mvn -o -B -DargLine=-Xmx2g -pl service/<域>/<域>-rpc -am test

# ── ③ 台账三门 ─────────────────────────────────────────
python3 tools/check-ledger-selfcheck.py ; echo "EXIT=$?"
python3 tools/check-ledger-tables.py    ; echo "EXIT=$?"
python3 tools/check-doc-links.py        ; echo "EXIT=$?"

# ── ④ bash 3.2 扫描（非 check-* ⇒ 不计入 27）────────────
python3 tools/difftest/scan-bash-legacy-vars.py ; echo "EXIT=$?"

# ── ⑤ 密钥门 ───────────────────────────────────────────
python3 tools/difftest/check-no-prod-secrets.py ; echo "EXIT=$?"

# ── ⑥ proto 静态守门（10 域）───────────────────────────
bash tools/difftest/check-proto-static.sh ; echo "EXIT=$?"
```

---

## §5.5 可客观判定的完成条件

| # | 判据 | 怎么判 |
|---|---|---|
| 1 | 口令 fallback 归零 | `grep` **0 命中**（排除 `target/`） |
| 2 | favorite 进程可优雅关闭 | 优雅关闭测试存在 + 绿 + **变异必红** |
| 3 | **全仓唯一红的守门转绿** | `check-ledger-selfcheck.py` **EXIT=0** |
| 4 | bash 3.2 坑归零 | `scan-bash-legacy-vars.py` EXIT=0（**全仓**） |
| 5 | 延迟项登记完成 | 台账有「现象 + 证据强度 + 解除条件」 |
| 6 | 15 处引用指错逐条有结论 | 每处有依据 or 显式标「不可唯一定位」 |
| 7 | 探针并发互不污染 | 两份并发都 EXIT=0 且无 `INTERNAL`/`5001` |
| 8 | **like 域 9 条假断言清零** | A~H + I 逐条有结论 ＋ **行为面 diff 为空**（git diff 无非注释行） |
| 9 | 台账三门 + 密钥门绿 | 全部 EXIT=0 |
| 10 | **不能算完成的情况** | 把 `INTERNAL`/`5001` 当缺陷去「修」产品代码 · 用 `137−3=134` 当差值归因 · 改回默认值让测试变绿 · 锚点门报了就直接改（不手工回真源核）· **代用户给 18 条未签字候选签字** · 改假断言时**顺手改了行为** |

---

## §5.6 本轮结束检查

- [ ] **P4-T01** 10 处 fallback（跨 8 个域）逐处销号
- [ ] **P4-T02** `admin-rpc` fallback 销号；`user-rpc` 无 yaml 的事实已复核
- [ ] **P4-T03** favorite 优雅关闭（测试 + 变异必红）
- [ ] **P4-T04** task 台账 134⇒137 + `--all` 口径 + 过期引用；**`check-ledger-selfcheck.py` 转绿**
- [ ] **P4-T05** hot 探针收尾行；扫描器 EXIT=0
- [ ] **P4-T06** `DRIFT-USERS-LAT-001` 登记（现象 + 强度 + 解除条件），**未落码**
- [ ] **P4-T07** 15 处引用指错逐条有结论
- [ ] **P4-T08** 探针 Nacos 隔离（并发两份都绿）
- [ ] **P4-T09** like 域 9 条假断言（A~H + I）逐条处置；**行为面 diff 为空**
- [ ] 证据路径：各门禁的真实退出码输出（逐条留档）+ 各域台账的行级改动
- [ ] 原始编号 → 最新状态：`R5-F1`~`R5-F7`/`F9`/`F10` · `S1-USERS-002` · `DRIFT-CF-018`（favorite 半边）· `G-19` · `PIT-GLOBAL-068` · `G-12` 遗留 15 处 · `DRIFT-USERS-LAT-001` · `D-08`
- [ ] 未完成任务及原因：（逐条写）
- [ ] 被阻塞的后续任务：Round 6（收口回归要 27 门全绿）· Round 7
- [ ] 本轮新增问题及阶段一分类：（按 §十 定级 ⇒ `CT-02`）
- [ ] **是否满足进入 Round 5 的条件**：**是** —— 前提是 Round 3 已完成（Round 5 要按复判后的判据对账）
- [ ] ⚠️ **本轮禁止**：改产品代码之外的任何行为面 · 改已签字偏离 · **代用户签字** · 改 `tools/difftest/` 的守门清单计数口径 · 越过域写权面改别人的台账

---

## 防错约束

| PIT 编号 | 出处（规范位置 · 逐条可核） | 规则（摘自该 PIT 标题原文） | 约束绑定的任务 ID |
|---|---|---|---|
| `PIT-GLOBAL-068` | `docs/Pitfalls/global/cross-domain.md:2503` | 🔴 **「跑红」也可能是环境串扰 ⇒ 共享服务发现面会把并发探针互相污染** ⇒ **跑红 ≠ 是缺陷** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-055` | `docs/Pitfalls/global/cross-domain.md:2099` | 🔴 **往台账里插一行 ⇒ 可能把「表格」插塌 ⇒ 而台账的内容一个字都没丢 ⇒ 所有内容层守门都查不出来** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-061` | `docs/Pitfalls/global/cross-domain.md:2310` | 🔴 **「去噪」是那条判据的「计账规则」，不是「放弃检查」** ⇒ 让**另一条判据**失去覆盖 ⇒ 变异样本被相邻行吃掉 ⇒ **假绿** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-030` | `docs/Pitfalls/global/cross-domain.md:995` | 🔴 **守门只扫一半的模块 ⇒ 另一半的缺陷只能靠「手工盘点」发现 ⇒ 手工必漏** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-036` | `docs/Pitfalls/global/cross-domain.md:1220` | Spring 的 `@DynamicPropertySource` **不进** context cache key ⇒ 靠「properties 恰好不同」隔离上下文是**侥幸** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-029` | `docs/Pitfalls/global/cross-domain.md:952` | 🔴 **静态扫描不排除 `target/` ⇒ 构建产物的旧副本被当源码判 ⇒ 门禁恒红** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-031` | `docs/Pitfalls/global/cross-domain.md:1042` | 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-014` | `docs/Pitfalls/global/cross-domain.md:288` | 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门 | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-040` | `docs/Pitfalls/global/cross-domain.md:1425` | 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径） | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-048` | `docs/Pitfalls/global/cross-domain.md:1719` | 🔴 **变异判据本身也会假阴性 ⇒ 用「集合差分」，不要用「关键词匹配」** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-LANE-07` | `docs/Pitfalls/_cross/domain-common-lanes.md:196` | · 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后**守门恒绿**（2026-10-27 02:23 实测） | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
| `PIT-GLOBAL-022` | `docs/Pitfalls/global/cross-domain.md:660` | 🔴 **抄来的「同形态先例」编号也要复核** —— 指针不是证据，它本身是一条**待核对的断言** | 约束本轮任务：`P4-T01` · `P4-T02` · `P4-T03` · `P4-T04` · `P4-T05` · `P4-T06` · `P4-T07` · `P4-T08` |
