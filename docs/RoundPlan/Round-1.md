> ⚠️ **历史计划 · 已归档** —— 当前权威入口 = [`_INDEX.md`](_INDEX.md)（新计划 · 7 个正常轮次 · Round 1~7）。
> **本文件原位保留、一字未改**（保住 12+ 处入链）⇒ 仅加此横幅。
> ⚠️ **不要按本文件执行任务** —— 本轮里的 `T-<n>.<m>` / `R5-F<n>` / `R5-<n>` 的去向逐条见 [`_migration.md`](_migration.md)。
> ⚠️ 本文件里的「✅ 已完成 / 阶段一通过」是**历史状态**；当前阶段一结论 = **❌ 未通过**（Round 3 复判：P0 #5 覆盖缺口 + E65 两个 A 类阻断）。
# R1 · 硬阻断清零

> **目标**：**P0 #5**（构建说明）与 **P0 #8**（敏感信息）两条 🔴 清零；**出处真欠账 25 条**归零。
> **前置**：R0 的 T-0.1（口径）/ T-0.2（批准）/ T-0.5（tools 写权）已决。**后继**：R4（矩阵重算要吃这一轮的结果）。

---

## T-1.1 🔴【主线】X-12 —— README 21 字节 + 无 `.env.example`（**P0 #5**）

**现状（实测 2026-10-29 04:1x）**：`README.md` = **21 字节**；`.env.example` **不存在** ⇒ P0 #5 🔴 不满足 ⇒ X-12 🔴 ⇒ C-3 / C-4 不满足。

**要做的**：
1. `README.md` 重写为「**能照着做**」的构建/配置/启动说明（③ 段：依赖 · 配置项（含必填 env 表）· 启动顺序与端口）⇒ 内容必须**逐条对着真实 compose / 真实 yaml**写，不得照抄 `docs/FileTree/Infrastructure.md`（那份描述的是**旧架构**）
2. 新建 `.env.example`（**全量必填变量 + 默认值 + 哪些必须 `:?`**）
3. `docs/FileTree/Infrastructure.md` 订正为现状（⚠️ 它是**共享面** ⇒ 主线写）

- **落点**：根 `README.md` · 根 `.env.example` · `docs/FileTree/Infrastructure.md`
- **写权**：**合并收口执行者**（根级文件**无人拥有** ⇒ CLOSEOUT §二·C1）
- **验收**：
  ```bash
  docker compose config >/dev/null && echo "compose OK"      # EXIT=0
  python3 tools/check-doc-links.py                              # EXIT=0
  ```
  ⇒ **外加一条人工判据**：**第三方只读 README 就能起栈**（本轮内做一次「照 README 起」的实跑 ⇒ 记进 `docs/CLOSEOUT-2026-10-29.md`）
- ⚠️ **不要**把这件事并进 G1 的 `S1-USERS-001` 另开编号 —— 根因已并入，**一个根因一个编号**

---

## T-1.2【主线】根 `docker-compose.yml` 的必填变量校验

**现状**：judge.md P0 #8 记「compose `:146`/`:193` 缺 `:?`」⇒ 本轮需**实测复核**这两处是否仍缺（10-29 03:0x G4 已改三域 api 的 yaml 默认密钥，但**根 compose 未见同批改动**）。

- **落点**：根 `docker-compose.yml`
- **写权**：**主线**（根级）
- **验收**：`docker compose config` 在**不设**必填变量时报错、在**设了**时正常渲染 ⇒ 两条命令都留证

---

## T-1.3 🔴【G2】P0 #8 残留 1 处明文验签密钥

**现状（实测 2026-10-29 04:1x，全仓扫 `service/*/*/src/main/resources/application.yml`）**：
明文默认值**只剩 1 处** ⇒

```
service/like/like-api/src/main/resources/application.yml:62:    access-secret: ae0536f9-6450-4606-8e13-5a19ed505da0
```

（其余 9 处已在 `45e0af4` 三域那批里改掉 ⇒ **复核 9 处确无遗漏**即可）

**要做的**：改为**缺变量即启动失败**（`${LIKE_API_ACCESS_SECRET:?...}`）⇒ 与已改的三域**同款形态**。

- **落点**：`service/like/like-api/src/main/resources/application.yml:62`
- **写权**：**G2**（域侧自修）
- **验收**：
  ```bash
  grep -rnE '(secret|signing|jwt)[^:]*:[[:space:]]*[A-Za-z0-9]{6,}' --include='application.yml' service/*/*/src/main/resources/ \
    | grep -viE '\$\{|placeholder|change-me' ; echo "残留=$?"     # 期望 0 命中
  ```
  ⇒ **外加**：不设变量起 like-api ⇒ **启动失败**（不是静默用默认值）

---

## T-1.4【G1】25 条真欠账的 G1 侧 **23 条**

**清单与修法**（`CLOSEOUT-2026-10-29.md` §8.7 逐条表 ⇒ **不在本文复制** ⇒ 修法只有三类）：

| 分类 | 条数 | 落点 | 修法 |
|---|---|---|---|
| **VERSION_MISMATCH**（引 `gorm@v1.31.2`，真源 `go.mod:28` pin **v1.31.1**） | **15** | `service/users/**`（user-rpc 12 + admin-rpc 3） | **多数只改版本号、行号不动**（13 条区间在两版逐字相同）⚠️ `callbacks/update.go:243-259` **两版不同** ⇒ v1.31.1 对应 `:238-254`（3 条，全在 admin-rpc） |
| **LINE_OOB**（`schema/field.go:1009`，v1.31.1 只有 **1004 行**） | **5** | `service/users/user/user-rpc/**` | 重新锚定到 **`:1002`**（`field.NewValuePool = poolInitializer(...)`） |
| **路径不精确 / 指向不存在** | **3** | `service/users/user/admin-rpc/**` ×2 + `service/users/user/admin-api/**` ×1 | ① 多写了 `admin/` ② 守门没报、G1 人工发现的同型第二处 ③ **admin-api 那条先按 T-0.3 裁定**，不许盲修 |

- **写权**：**G1**（域侧自修）
- **验收**：
  ```bash
  python3 tools/g3-anchor-check/check-anchors.py --all        # 全仓
  # 判据：G1 域相关簇「待查」归零；⚠️ EXIT=1 不可信（假阳性 59%）⇒ 必须逐条回真源核
  ```
  ⇒ **外加**：**零行为变更**（本组全是注释 / `@DisplayName` / 断言消息里的引用 ⇒ **不改任何断言** ⇒ 测试条数与结果必须**完全不变**）
- ⚠️ **版本号写错是「在错的副本下看起来合法」**（`schema/field.go:1009` 在 v1.31.2 合法、在 v1.31.1 越界）⇒ **先钉版本再判行号**，顺序反了会漏

---

## T-1.5【G2】25 条真欠账的 G2 侧 **2 条**

- ① `service/like/like-rpc/**`：**1 条版本号**（`gorm@v1.31.2` ⇒ v1.31.1）
- ② `service/like/like-common/**`：**1 条路径**（`like/common/errmsg.go` ⇒ 真身 `service/like/common/errmsg/errmsg.go`，107 行，`:51` = `ErrorRedisSelect = 5011` ⇒ **少写了 `errmsg/`**）

- **写权**：**G2**（域侧自修）
- **验收**：同 T-1.4（`check-anchors.py --all` 在 G2 域归零）⇒ **零行为变更**
- ⚠️ **不要**顺手动另外 4 条 `processor.go:82` ⇒ 已核实是**守门假阳性**（真身 `goka@v1.1.16/processor.go:82` 非空且正是注释声称的那句）⇒ 动了就是返工

---

## T-1.6【主线】补 `PINNED` 两行 ⇒ 守门假阳性归零

**现状**：`tools/g3-anchor-check/check-anchors.py` 的 `PINNED` **缺**两条真源 `go.mod` 已 pin 的版本 ⇒ 解析不到版本前缀路径 ⇒ 回退裸文件名匹配 ⇒ **撞同名不同文件** ⇒ 假阳性 29 条。

**要做的**：加 `gorm.io/driver/postgres: v1.6.0` + `github.com/lovoo/goka: v1.1.16` ⇒ 重跑 `--all`。

- **落点**：`tools/g3-anchor-check/check-anchors.py`（**已随 G3 合入主干** ⇒ 主线补；**G4 零落码** ⇒ 避免 `add/add` 撞车，PIT-LANE-01）
- **写权**：**主线**（该目录不在任何 lane 白名单 ⇒ 见 T-0.5）
- **验收**：
  ```bash
  python3 tools/g3-anchor-check/check-anchors.py --all
  ```
  ⇒ **假阳性 29 条归零**；剩余「待查」= **25 条真欠账**（R1·T-1.4/T-1.5 修完 ⇒ 归零）
- ⚠️ **不搬进 `tools/difftest/`**（搬移必须同改 `PhaseGoal-judge.md` §6.3 ⇒ 制造 B14）⇒ 本轮**只补 `PINNED`，不搬移**

---

## T-1.7【G1】T-0.3 裁定的 admin-api 出处修法落地

- **落点**：`service/users/user/admin-api/**` + `docs/DRIFT/users/vs-common.md`
- **写权**：**G1**
- **验收**：引用指向**真实存在**的 Go 入口 + `check-anchors.py --all` 该簇归零

---

## R1 停止点

- ☐ **P0 #5** ✅（README + `.env.example` + Infrastructure.md + 第三方照 README 起栈实跑通过）
- ☐ **P0 #8** ✅（全仓 yml 明文默认值 **0 命中** + 不设变量起栈**失败** + compose `:?` 生效）
- ☐ **出处真欠账 25 条 → 0**（`check-anchors.py --all` 逐条回真源核过 ⇒ 剩余待查 0）
- ☐ **零行为变更**证明：全量回归条数与结果与 R1 前**完全一致**
- ☐ `check-doc-links.py` / `check-wave-lane-ownership.py` / `check-agents-budget.py` ⇒ 全 EXIT=0

⚠️ **R1 结束时仍不算阶段一完成** —— P0 #1 / #2 / #3 / #4 四条 🟡 要 R2 / R3 解决。
