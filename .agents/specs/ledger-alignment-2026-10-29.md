# spec · 台账对齐（2026-10-29）

> **目标**：把 `docs/DRIFT` · `docs/Intentional_Deviations` · `docs/PhaseGoal` · `docs/Pitfalls`
> 四个台账目录的**每一条状态断言**对齐到**当前项目的实际状态**。
> **本 phase 只改台账**（docs），**不改任何产品代码 / 数据库 / 配置 / 测试**。
> **真源 = Go**（`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`，冻结只读）⇒ 两侧不一致时**默认 RAG 错**。

---

## 〇 · 硬纪律（每条都可能被守门/人工复核抓到）

| # | 纪律 |
|---|---|
| **D1** | **只写分配给你的文件**（见 §二）。**别的文件一个字都不许碰** —— 包括 `service/**`、`pom.xml`、`docker-compose.yaml`、`AGENTS.md`、`docs/RoundPlan/**`、`docs/PhaseGoal/**`、`docs/Stage1/**` |
| **D2** | **共享索引不写**：`docs/DRIFT/_INDEX.md`、`docs/DRIFT/_MANIFEST.json`、`docs/Pitfalls/_INDEX.md`、`docs/Intentional_Deviations/_INDEX.md`、`docs/Pitfalls/_cross/**` ⇒ **主线统一更新**（你只改自己域的子目录） |
| **D3** | **每条状态断言必须带「核对日期（2026-10-29）+ 证据指针」** —— 证据指针 = `文件:行号`（RAG 或 Go 侧）或**守门的真实退出码**。**没有证据的状态一律不许写** |
| **D4** | **判定不了 ⇒ 标「待核实」+ 写清缺什么** ⇒ **不许默认「仍生效」也不许默认「已闭环」** |
| **D5** | **保留原值 + 加时间线指针**（历史不抹掉）⇒ 改写既有行时**原内容保留**，把新结论追加在后面并标「**2026-10-29 对齐：…**」 |
| **D6** | **状态取值域只用** `✅` / `🟡` / `🔴` / `⚫`（与仓内既有取值域一致） |
| **D7** | **不造数字**：凡是引用「N 条 / N 处 / N 个」的，必须**当场数一遍**；数不出来就写「待核实」 |
| **D8** | **Go 真源只读**（不许改、不许写）。RAG 侧**只读源码**，**不许改任何 `.java` / `.sql` / `.yml`** |
| **D9** | **提交信息用 `git commit -F <file>`**（提交信息里的反引号会被 bash 当命令替换） |
| **D10** | **不推远端**。只在自己的工作树里提交，**由主线合并** |

---

## 一 · 三类台账各自的判定口径

### 1. `docs/DRIFT/<域>/**` —— 「明细面 vs 现状面」收敛

同一域里存在两类文件：

- **明细面**：`api.md` / `rpc.md` / `common.md` / `proto.md` —— 逐条差异的展开
- **现状面**：`vs-common.md` / `stage1-admission.md` —— 对 A1~A9 九条机制面轴的逐轴结论

**目标**：**同一个编号在同域内只有一种状态**。

**做法**：
1. 把本域明细面与现状面的**每一条编号**拉成一张对照表（编号 · 明细面状态 · 现状面状态 · 判定 · 证据）
2. **以实测为准**判定每个编号的当前状态 —— **不是以任何一面的快照为准**
3. 结论**同时回填两面**（收敛到单一状态）
4. 每次改写标注「**核对日期 + 依据**」
5. ⚠️ **只改「当前状态」**；**差异描述 / 证据段一律保留**

**已核实的全域问题（各 lane 只需核自己域那份）**：
- `DRIFT-HOTAPI-030` 全域六处四态（`_cross/open-items.md:15` 🔴 · `global/_README.md:134` 🟡 · `global/cross-domain.md:26` + `AGENTS.md:142` + `global/vs-common.md:34` ✅ · `hot/api.md:1237`/`:2170` 🔴 · `favorite/api.md:1085` 🔴）⇒ **权威态 = ✅ 已落地**（共享层修法 · favorite 零触碰）⇒ 跨域台账由主线统一改，**你只改自己域那份**
- `like/api.md:43` ✅ vs `:468` 🔴（**同编号同文件两态**）
- `message/api.md:912` 🔴 / `:1528` 🟡 vs `message/vs-common.md:38`/`:40` ✅
- `task/rpc.md:29` 134 vs `:1633` 137 · `task/vs-common.md:1433` 134 · `:1525` 102 vs `task/api.md:30` 135
- `users/stage1-admission.md:98`/`:99`/`:105`/`:130`/`:137`/`:548`/`:549` —— 「README 只有 7 字节」「`.gitignore` 无 `.env`」「仓内既无 `.env` 也无模板」**三条事实全部已被 2026-10-29 R4 轮推翻**（README 现为完整说明书 · `.env.example` 新建 · `.gitignore` 实有 3 条规则）⇒ **陈旧事实必须订正**
- `favorite/api.md:1085` 🔴 vs 同文件 `:305`/`:716-717` ✅（同文件两态）

### 2. `docs/Pitfalls/<域>/**` —— 每条补状态

**每条 Pitfall 补三个字段**：

| 字段 | 取值 | 要求 |
|---|---|---|
| **状态** | `仍生效` / `已闭环` / `已撤销` / `待核实` | **`已闭环` 必须附证据指针**（哪个守门 / 哪次变异 / 哪个文件被删） |
| **核对日期** | `2026-10-29` | 必填 |
| **是否影响当前轮次** | 是 / 否 | **填「否」时必须写明不适用的具体原因**（规范 §12.3 第四种落点）—— **不许留空** |

**判定语义**：
- **仍生效** = 该坑描述的错误做法**今天仍可能发生**（触发条件仍存在）⇒ 必须同时落进**对应轮次的「防错约束」**
- **已闭环** = 触发条件**已被消除**且**有证据**
- **已撤销** = 用户/决策撤销
- **待核实** = 判不了（D4）

**编号规范**（本域内统一）：
- 新号 = `PIT-<域大写>-<两位序号>`（例：`PIT-FAV-01`）
- **旧号原样保留**（不许改写历史行），在条目里加一行「**旧号**：`XXX` ⇒ **新号**：`PIT-FAV-01`」做对照
- ⚠️ **仓内现有 20 种编号形态并存**（`PIT-GLOBAL-*` / `PIT-FAV-*` / `PIT-LIKEAPI-*` / `PITFALL-FOLLOW-*` / `PF-ARTICLE-RPC-*` / `P-HOTRPC-*` / `P-MSGAPI-*` / `PIT-LANE-*` / `PIT-USERSCOMMON-*` / `PIT-USERS-SHARED-*` / `PIT-COMMENT-*` / `PIT-FAV-COMMON|PROTO|RPC` / `PIT-LIKE-RPC` / `P-ADMINAPI` / `P-HOTAPI` / `P-HOTRPC` / `PITFALL-MSG-R` …）⇒ **本域统一即可**

**0 字节文件** ⇒ 二选一：① 确有坑位 ⇒ 补内容 ② 确实没有 ⇒ 写一行「**本域暂无已登记坑位**（核对日期 2026-10-29 · 已核 `<命令>`）」

### 3. `docs/Intentional_Deviations/<域>/**` —— 逐条重核签字是否仍成立

**判定口径**（`docs/Intentional_Deviations/_INDEX.md:34`）：
> 「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」

**每条要做三件事**：
1. **核「被签字描述的那个行为现在还存在吗」**（回 Go 真源 + 回 RAG 源码两侧看）
   - **仍存在** ⇒ 签字有效，状态不变，**核对日期刷新**
   - **已不存在** ⇒ 标「**已失效（签字描述的行为已不存在）**」+ 写明**现在是什么样** + 指出该从计数中剔除（**由主线更新索引计数**）
   - **描述不完整**（签字只覆盖了一部分事实）⇒ **保留原描述 + 追加订正**，不抹掉原文
2. **撞号检查**：同一 `SD-` 编号若在本域指向**多条**不同偏离 ⇒ 给每条加**唯一别名** `SD-<编号>@<域>`，并在条目里写明撞号事实
3. **已撤销 / 已关闭**的条目 ⇒ 核对「撤销的那个行为**现在**是不是真的没了」

**⚠️ 已知的一处描述不完整（示例，示范做法）**：
`docs/Intentional_Deviations/comment/rpc.md:67` 的 `SD-2026-10-24-CAND-02` 写「`report_record` 表在 RAG 侧存在，Go 侧**根本不存在**」——
实测：Go 侧 `service/comment/rpc/internal/model/data_model.go:55-68` **有** `ReportRecord` 结构体（8 列）+ `TableName()`；
`service/comment/rpc/internal/model/db.go:15` 的 `AutoMigrate` 名单**不含** `ReportRecord` ⇒ Go 侧**确实不建表** ⇒ **DB 层签字成立**；
但 Go 侧**有业务写入逻辑**（`service/comment/rpc/internal/logic/report_comment_logic.go:69` ＋ `model/comment.go:242` 的 `InsertReport` + `OnConflict DoNothing`）
⇒ ⇒ **签字描述不完整**（不是「根本不存在」，而是「有 model ＋ 有业务逻辑，只是 `AutoMigrate` 漏了它」）⇒ **保留原句 + 追加订正**。

---

## 二 · 写权分工（文件零重叠）

| lane | 工作树 | 负责的台账文件 |
|---|---|---|
| **G1** | `../RAG.worktrees/g1-users-follow` | `docs/DRIFT/users/{_shared.md, admin/**, user/**, stage1-admission.md, vs-common.md}` · `docs/DRIFT/follow/**` · `docs/Pitfalls/users/{_shared.md, admin/**, user/**}` · `docs/Pitfalls/follow/**` · `docs/Intentional_Deviations/users/{_shared.md, admin/**, user/**}` · `docs/Intentional_Deviations/follow/**` |
| **G2** | `../RAG.worktrees/g2-message-like-task` | `docs/DRIFT/{message,like,task}/**` · `docs/Pitfalls/{message,like,task}/**` · `docs/Intentional_Deviations/{message,like,task}/**` |
| **G3** | `../RAG.worktrees/g3-article-comment-favorite-hot` | `docs/DRIFT/{article,comment,favorite,hot}/**` · `docs/Pitfalls/{article,comment,favorite,hot}/**` · `docs/Intentional_Deviations/{article,comment,favorite,hot}/**` |
| **G4** | `../RAG.worktrees/g4-service-common` | `docs/DRIFT/{global,common}/**` · `docs/DRIFT/users/common.md` · `docs/Pitfalls/{global,common}/**` · `docs/Pitfalls/users/common.md` · `docs/Intentional_Deviations/{global,common}/**` · `docs/Intentional_Deviations/users/common.md` |
| **主线** | 主仓 | `docs/DRIFT/_INDEX.md` · `docs/DRIFT/_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Pitfalls/_INDEX.md` · `docs/Pitfalls/_cross/**` · `docs/Intentional_Deviations/_INDEX.md` · `AGENTS.md` · `docs/PhaseGoal/**` · `docs/Stage1/**` · `docs/RoundPlan/**` |

⚠️ **`docs/DRIFT/users/common.md`（users-common）与 `docs/DRIFT/common/**`（共享层）= G4**（AGENTS.md §5.3f：共享层写权唯一）⇒ **G1 不许碰**。

---

## 三 · 交付物

1. **本 lane 的台账改动**（只在自己域的目录里）
2. **一份 lane 自检回执**（写在回复里，不要落仓）：改动文件清单 · 各台账的条目数与状态分布 · 机器自检的真实退出码 · **判为「待核实」的条目清单 + 缺什么**
3. **一次提交**（`git commit -F <file>`，**只提交自己域的台账文件**）

## 四 · 自检（每个 lane 跑，退出码要贴进回执）

```bash
cd <你的工作树>
python3 tools/check-doc-links.py;                  echo "links EXIT=$?"
python3 tools/check-ledger-count.py;               echo "count EXIT=$?"
python3 tools/check-ledger-tables.py;              echo "tables EXIT=$?"
# 本域 Pitfalls 的状态覆盖率（必须 100%）
tot=$(grep -rhoE '^#+ *P[A-Za-z0-9_-]*-[0-9]+' docs/Pitfalls/<你的域>/ --include='*.md' | wc -l)
has=$(grep -rhoE '^#+ *P[A-Za-z0-9_-]*-[0-9]+.*(仍生效|已闭环|已撤销|待核实)' docs/Pitfalls/<你的域>/ --include='*.md' | wc -l)
printf '有状态 %s / 总数 %s\n' "$has" "$tot"        # 期望 has == tot
# 0 字节文件必须归零
find docs/Pitfalls/<你的域> docs/Intentional_Deviations/<你的域> -name '*.md' -size 0 | wc -l   # 期望 0
git status --short                                   # 期望只列出自己域的文件
```

⚠️ **退出码必须在失败不短路的上下文里取**（`out=$(cmd); rc=$?`），**不要**用 `cmd | tail` 的退出码。
