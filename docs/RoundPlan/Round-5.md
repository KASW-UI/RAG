# R5 · 兜底轮（**已触发** · 2026-10-29）

> **触发原因**（模板列的三个触发条件，**命中 ① 与 ③**）：
>
> | 条件 | 本轮 | 依据 |
> |---|---|---|
> | ① R1/R2/R3 的验收命令在跑完后**仍报出未闭合项** | ✅ **命中** | R1 停止点写着「**第三方照 README 起栈实跑通过**」，但该步因并发探针在跑而**推迟到 R4** ⇒ **R1 停止点并非全绿** ⇒ 按 §五 状态列的判据（「一栏写『已完成』⇒ 该轮『停止点』里的每条验收命令都实跑过并全绿」），**R1 不能记「已完成」** |
> | ② T-0.1 选 **B**（25 条逐条修） | ⚪ **未命中** | Jason 2026-10-29 选 **A**（以已签字偏离为主）⇒ 不触发「+2~3 轮」那一条 |
> | ③ R4 复判时发现某条 P0 的证据**仍然不足** | ✅ **命中** | **P0 #5** 长期为 🟡（唯一理由是「按 README 真起一次栈」没做）⇒ 直到 R4 才补上 ⇒ 说明它确实是**证据缺口**而非缺陷 |
>
> ⚠️ **本轮不扩范围**：① 不做阶段二的十二维度生产级审查（§1.3 / §2.2 / §6 / §7）② 不做 §9 故障矩阵 / 生产流量 / SLO / 灾备演练 ③ **一条差异 ≠ 一个缺陷** —— 下列 5 条**全部已定级**，其中 **3 条是 C/D 类 ⇒ 登记不修**。

---

## 一、本轮收录什么（**只收这些，别的一律不收**）

| # | 欠账 | 定级（`twoStage.md` §十） | 归属（写权） | 本轮动作 |
|---|---|---|---|---|
| **R5-1** | **部署凭据默认值** —— 实测 **8 个 rpc 模块 / 10 处**（`${POSTGRES_PASSWORD:password}` × 9 ＋ `${MINIO_SECRET_KEY:minioadmin}` × 1）：like/comment/follow/message/favorite/admin/task 各 1 · article-rpc 3 | 🟡 **B 类**（已登记 **`S1-USERS-002`**） | ⚠️ **归属订正**：落点在**各域自己的 rpc 模块** ⇒ **域内可自修** ⇒ **原写「归 G4 共享层」是错的** | **派回各域 lane**（G1/G2/G3） |
| **R5-2** | **`DRIFT-USERS-LAT-001`** —— RAG 侧 HTTP 响应延迟被共享层的「**500ms 切片等待**」量化（实测 Go `max=319ms` vs RAG `max=2046ms`；中位数 50 vs 51ms 几乎相同 ⇒ **两侧调用链层数相同**，不是跳数差） | **P3 / D 类** | 共享层 `service/common`（`HttpMiddleware.java:415-419`）⇒ **G4** | **登记 + 写解除条件**（**不修**：D 类 = 竞争力，且结论依赖「空载复跑」这一前提） |
| **R5-3** | **G-19** —— proto 的「**消费方式**」取证（`optional` presence / 代码里的字段号硬编码 / Jackson 配置） | **取证缺口**（**不降 R**：§1.3 的 proto 分型口径下，`.proto` 逐字节 diff 已满足 ⑤） | 各域 proto 消费点 | **取证**（**不改行为**） |
| **R5-4** | **PIT-GLOBAL-068** —— 共享 Nacos 服务发现面让**并发探针互相污染** ⇒ 「跑红 ≠ 是缺陷」 | **D 类**（工具面） | 探针工具（`tools/api-full-parity-probe/**`） | **隔离方案**（**不改产品代码**） |
| **R5-6** | **`DRIFT-CF-018` 的 `favorite` 域半边** —— `FavoriteFactDispatchScheduler.java:119` **仍调阻塞版 `Dispatcher.run(...)`** ⇒ `once=false` 时**句柄永远拿不到** ⇒ `@PreDestroy` 拿到 null ⇒ **进程仍无法优雅关闭**（共享层半边已修 ⇒ 这半边是**域内**的） | **P2 / C 类**（不阻阶段一：P0 #1「核心业务链路能够真实运行」**仍成立**） | `service/favorite/favorite-rpc/**`（域内 ⇒ G3） | **修**（一行：`Dispatcher.run` → `Dispatcher.runAsync`，与 comment-rpc 对齐） |
| **R5-5** | **G-12 遗留 15 处**「原引用本就指错」—— 基线行本身是**空行 / 重复表头** ⇒ **早于 G-12 就指错**，**不是 G-12 引入的回归** | **D 类**（文档卫生） | 各域台账 | **逐条定位后修**（**当前不改**：无法唯一定位 ⇒ 瞎改比不改更糟） |

> ⚠️ **`AMBIGUOUS` 1918 条仍不做**（理由同模板：「同名多文件无法静态判定」的**全集** ⇒ 人工判读不现实 ⇒ 只处理与 P0 九条 / 组件必相对齐项相关的那一小部分 ⇒ 其余**登记为阶段二**）。

---

## 二、本轮的动作（模板四条）

1. **分流**：上表 5 条**逐条定级**完毕 ⇒ **A 类 0**（⇒ **无一条阻阶段一**）· **B 类 1**（R5-1）· **D 类 3**（R5-2 / R5-4 / R5-5）· **取证 1**（R5-3）
2. **派工**：见 §三
3. **复判**：见 §四
4. **更新状态**：`_INDEX.md` §五 状态表 + 本文件已写「触发原因 / 实际内容 / 复判结论」

---

## 三、派工单（**写权同 `_INDEX.md` §三**）

| 单号 | 交给 | 做什么 | 落点 | 验收命令 |
|---|---|---|---|---|
| **F-1** | **G2**（like / message / task）＋ **G3**（article / comment / favorite / hot） | 取消本域 rpc 模块的 **datasource 口令 fallback**（`${POSTGRES_PASSWORD:password}` ⇒ 改无默认值的 `${POSTGRES_PASSWORD}`），与 Go 的 `os.ExpandEnv` **空口令 fail-fast** 同形 | 各域 `service/<域>/<域>-rpc/src/main/resources/application.yml` | `grep -rnE '\$\{POSTGRES_PASSWORD:[^}]' service/` ⇒ **0 命中**（排除 `target/`）＋ 各模块 `mvn test` ⇒ 0F/0E |
| **F-2** | **G1**（users） | 同 F-1，但**只限 `admin-rpc`**（`user-rpc` **无 `application.yml`** ⇒ 不直连 PG ⇒ 本就不在清单里） | `service/users/admin/admin-rpc/src/main/resources/application.yml` | 同上（限 `service/users/**`） |
| **F-3** | **G4**（共享层） | `DRIFT-USERS-LAT-001`：**登记 + 写解除条件**（**不落码**） | `docs/DRIFT/global/vs-common.md`（或 `docs/DRIFT/common/alignment-2026-10-23.md`） | 台账有「现象 + 证据强度 + 解除条件（空载复跑 `unexpected=0` ⇒ 纯负载敏感；仍超时 ⇒ 才是真差异）」 |
| **F-4** | 各域（按 proto 归属） | **G-19 取证**：逐 proto 核「消费方式」三问（`optional` presence 有无语义差 / 有无字段号硬编码 / Jackson 配置与 Go 侧是否同形） | 各域台账 + `docs/DRIFT/_cross/proto-verification.md` | 每条有「是/否 + 证据（`文件:行号`）」⇒ **无「未知」** |
| **F-5** | **G3** | 探针的 **Nacos 隔离**（避免共享服务发现面互相污染） | `tools/api-full-parity-probe/article/**` | 同一份探针在**并发跑**时不再互报 `INTERNAL`/`5001` |
| **F-6** | 各域 | **G-12 遗留 15 处**：逐条用**上下文关键词**定位作者真正想指的行 | 各域台账 | 每处的目标行**逐条有依据**（内容锚定，不是猜） |
| **F-7** | **G3** | **`DRIFT-CF-018` 的 favorite 域半边**：`FavoriteFactDispatchScheduler.java:119` 改用 `Dispatcher.runAsync(…)`（与 `CommentFactDispatchScheduler.java:93` 对齐）⇒ 进程可优雅关闭 | `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/fact/FavoriteFactDispatchScheduler.java` | ① 优雅关闭测试：开启 `favorite.fact.dispatch.enabled=true` ⇒ 发起关闭 ⇒ 断言**进程在 N 秒内退出**且 `@PreDestroy` 拿到**非 null** handle ② `mvn -pl service/favorite/favorite-rpc -am test` ⇒ 0F/0E |
| **F-8** | **G4** | **共享层台账的计数/条目订正**（本轮复核发现）：① `docs/DRIFT/common/alignment-2026-10-23.md` §十二·一 的合计表**漏减 `DRIFT-CF-018`**（该条共享层半边已修）⇒ communityfact「15 条一条未动」是**陈旧快照** ⇒ 未修编号 **24 → 23** ② §十三·一 的 **CF-018 行**按「**已修一半**」改写（共享层已修 · favorite 半边仍成立）③ **LOG-003 / LOG-006 各缺「暂缓理由」一列**（表头 11 列 / 这两行只有 10 列）⇒ 补齐 | `docs/DRIFT/common/alignment-2026-10-23.md` | ① 机器核：§十三·一 的**每行都是 11 列**（脚本：按 `|` 切分计数，≠11 即红）② 合计行与 §十三·三 的「23 未修 + 5 未编号 = 28」**自洽** ③ `check-ledger-tables.py` ⇒ EXIT=0 |

---

## 四、复判结论（**R5 跑完后写**）

> ⏳ **待填**（本轮动作完成后回填：① 是否动了基线 ② 生成器是否重跑 ③ R4 验收命令是否重跑 ④ 结论是否仍为「通过」）。

---

## 五、纪律（**沿用模板，不放松**）

- ⚠️ **「一条差异 ≠ 一个缺陷」** —— 先定级再动手 ⇒ 本轮 5 条里 **3 条是 D 类 ⇒ 登记不修** ⇒ **不要为了「清零」去改行为**
- ⚠️ **不要在 R5 里扩到阶段二**（§1.3 / §2.2 / §6 / §7）
- ⚠️ **不要为了 R5 改 `AGENTS.md`**（除非是为了登记 R5 本身 ⇒ 仍须字节中性）
- ⚠️ **写权不得越界**：`service/common/**` ＋ `service/users/users-common/**` **只有 G4 能写**；各域 `service/**` 与 `docs/DRIFT/<域>/**` 归各自 lane；`docs/RoundPlan/**` ＋ `docs/PhaseGoal-judge.md` ＋ 三个 `_INDEX.md` 归**收口执行者**
