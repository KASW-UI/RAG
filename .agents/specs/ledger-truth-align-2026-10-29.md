# 规约 · 台账结论 ↔ 代码「真实性逐条对齐」phase（2026-10-29）

> **本 phase 的性质**：上一 phase 把台账的**结构**对齐了（状态字段 / 编号 / 计数 / 链接）。
> 本 phase 做的是**更深一层**：**每条结论的「说法」本身**是否与当前代码一致。
> ⇒ **只改 `docs/`**（台账）；**不改产品代码 / 数据库 / 配置 / 测试**。
> ⇒ **代码是真相**：结论说「已修」而代码里没有 ⇒ **改台账**（不是改代码去迎合台账）。

---

## 〇 · 本 phase 的机器判据（`tools/difftest/ledger-claim-audit.py`）

> **非 `check-*` 命名 ⇒ 不计入 §6.3 的 27 守门计数口径**（与 `ledger-status-coverage.py` / `scan-bash-legacy-vars.py` 同例）。

```bash
python3 tools/difftest/ledger-claim-audit.py              # 全量
python3 tools/difftest/ledger-claim-audit.py --domain X   # 单域
python3 tools/difftest/ledger-claim-audit.py --strict     # 落空 ⇒ EXIT=1
```

**四个解析根**（缺一根 ⇒ 误报落空）：`RAG 仓` · `Go 真源` · `go mod cache` · **`~/.m2/repository` 的 `*-sources.jar`**。

> ⚠️ **2026-10-29 实测的三条坑**（都是**工具 bug**，不是台账错）：
> ① **直连路径只查一个根就早退** ⇒ `manage.sh:842` 只命中 RAG 仓那份（160 行）⇒ 误报「行号越界」；
>    真源那份 **1179 行**、842 完全合法 ⇒ **多根必须全查**。
> ② **缺第 4 根** ⇒ 9 处**第三方库源码指针**（Tomcat `Http11InputBuffer.java` / Lettuce `RedisURI.java` /
>    commons-pool2 `GenericObjectPool.java` / grpc `ClientCalls.java`·`ServerCalls.java` /
>    spring-data-redis `LettuceConnectionFactory.java`）被判成「文件找不到」⇒ 实际**全在 m2 的 sources jar 里**。
> ③ **版本漂移** ⇒ m2 里同一 artifact 有多版本（项目 = Boot 4.0.8 ⇒ tomcat 11.x / spring-data-redis 4.0.x / grpc 1.66.0）⇒
>    旧版本同一行号**讲的不是同一件事** ⇒ 工具输出**每个候选版本那一行的实际内容**供人判。

⇒ **现状（2026-10-29）**：905+ 指针 ⇒ **落空 = 2**（`/tmp/probe.sh:3` · `X.java:135`），
其中 **`X.java:135` 是举例说明**（不是证据指针）⇒ 台账侧改写为**不带 `:行号`** 的形态即可。
⚠️ **jar 内第三方源码档 = 单列提示，不计失败**（且**不许改它的行号** —— 实测那些行**真源正确**）。

---

## 一 · 什么算「一条结论」

三目录里**带编号的条目**（每个都有状态）：

| 档 | 条目形态 | 状态取值域 |
|---|---|---|
| `docs/DRIFT/<域>/**` | `DRIFT-XXX-nnn` · `SD-…` · `UNRESOLVED-nnn` · `E-nn` · `S1-…` · `CYCLE-nnn` | ✅ / 🟡 / 🔴 / ⚫ |
| `docs/Pitfalls/<域>/**` | `PIT-…` · `PF-…` · `P-…` | 仍生效 / 已闭环 / 已撤销 / 待核实 |
| `docs/Intentional_Deviations/<域>/**` | `SD-…` · `DEV-…` · `N-n` · `A/C/D-n` | ✅ 仍成立 / ⛔ 已失效·已撤销 / ⏳ 未签字 |

**边界**：
- **「历史快照」行不算结论**（明确标了「本表是某轮快照」的历史表）⇒ 只需在**活状态**处给准。
- **散文式段落不算结论**（没有编号）⇒ 除非它断言了与代码冲突的事实。

---

## 二 · 每条结论要做的事（**三件事，缺一不可**）

### ① 核「被描述的那个行为」现在还在不在

- 判据 = **回 Go 真源**（`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`，**只读**）
  ＋ **回 RAG 源码**（本仓）**两侧**看。
- 两侧不一致 ⇒ 那是**真漂移**（默认 RAG 错）⇒ 台账要记成 🔴。
- **已签字偏离** ⇒ 两侧不一致是**决策不是缺陷** ⇒ 状态是「✅ 仍成立」，不是 🔴。

### ② 核「证据指针」能不能在代码里落到

机器判据（**已落仓，直接跑**）：

```bash
python3 tools/difftest/ledger-claim-audit.py --domain <你的域>
```

- **🔴 落空**（文件找不到 / 行号越界）⇒ **必须修**（改指针，或改结论）。
- **⚠️ 歧义（提示档）** ⇒ **不是缺陷**（裸 basename 指针在仓里有几十个同名文件是常态，限定词写在周边散文里）⇒ **不要为了消歧义去改指针**。
- **🌐 外部仓 / jar 内 class** ⇒ 本机不可核验 ⇒ **在结论里标注这一点**即可，不要当成缺陷。
- ⚠️ **指针能解析 ≠ 结论为真** —— 指针只是「证据的落点」，还要看那一行**是不是断言说的那个东西**
  （台账里 E68 的原始案例就是「行号合法但指错文件」）。

### ③ 核「状态」与事实一致

| 台账现在写 | 代码实际是 | 怎么改 |
|---|---|---|
| ✅ 已修 / 已闭环 | 真的已修 | **不动**（把证据补成可复算的：守门名 / 退出码 / `文件:行号`） |
| ✅ 已修 / 已闭环 | **其实没修** | ⇒ **改判 🔴 / 仍生效**，并写清「原来写成已修是陈旧快照」 |
| 🔴 未修 / 仍生效 | 其实已修 | ⇒ **改判 ✅**，并写清「原值（日期）是陈旧快照」 |
| 🟡 部分 | 部分对 | ⇒ 拆清楚「哪部分闭合、哪部分还开着」（**两格状态不同是有意为之，要显式声明**） |
| 判不了 | 判不了 | ⇒ **待核实**（**不许默认「仍生效」也不许默认「已闭环」**） |

---

## 三 · 改写的硬纪律（照抄上一 phase 的口径）

1. **原值一律保留**，只**追加**订正段：
   > ⚠️ **2026-10-29 真实性复核**：原判 **X** ⇒ **现行态 = Y**。依据：`文件:行号`（两侧）· 核对日期 2026-10-29。
   ⇒ **不许回头改写历史行**（历史被抹掉 ⇒ 读者失去时序）。
2. **只改「当前状态」面**（状态字段 / 依据 / 状态摘要），**差异描述与历史叙述段一律保留**。
3. **一条差异 ≠ 一个缺陷**；**C 类属阶段二暂缓** ⇒ 不因「C 类」把它升级成 🔴。
4. **「跑绿 ≠ 对」**：结论里引用的守门/实测必须是**真实存在**的（文件在、命令能跑）⇒
   引用前**先确认那东西真在仓里**（`ls` / `grep`）。
5. **不编造**：`文件:行号` 必须**当场核过**（文件存在 + 该行确实讲这件事）⇒ 不许照抄别的域的指针。
6. **状态取值域**别混用（见 §一 表）。
7. **「是否影响当前轮次」填「否」时必须写明不适用的具体原因**（Pitfalls 专有）。

---

## 四 · 写权（**严格不重叠**）

| lane | 你能改的文件 |
|---|---|
| W-A | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/users/**` —— ⚠️ **不含** `users/common.md`（那是 G4 的） |
| W-B | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/{follow,task}/**` |
| W-C | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/{message,like}/**` |
| W-D | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/{comment,article}/**` |
| W-E | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/{favorite,hot}/**` |
| W-F | `docs/{DRIFT,Pitfalls,Intentional_Deviations}/{global,common}/**` ＋ `docs/{DRIFT,Pitfalls,Intentional_Deviations}/users/common.md` |

**⛔ 不得碰**：三个 `_INDEX.md` · `docs/DRIFT/_INDEX.md` · `docs/DRIFT/_MANIFEST.json` ·
`docs/DRIFT/_cross/**` · `AGENTS.md` · `docs/PhaseGoal/**` · `docs/Stage1/**` · `docs/RoundPlan/**` ·
**任何 `service/**` · `tools/**`** ⇒ 那些是主线的。
**⛔ 不许执行任何 git 命令**（`git status --short` 是唯一例外）。

---

## 五 · 交付物

1. **你域内每个带编号条目的最终状态**（一张表：编号 · 原状态 · 现行态 · 依据 `文件:行号` · 是否改判）。
2. **改判清单**（哪些从 ✅ 改成 🔴、哪些从 🔴 改成 ✅）—— **每条都要两侧证据**。
3. **指针落空清零**：`ledger-claim-audit.py --domain <域>` ⇒ **落空 0**。
4. **仍未核到的**（判不了的）⇒ 单列一张表，标「待核实」+ 为什么判不了。
5. **回执**：明确写出「**我核了 N 条结论，其中改判 M 条**」—— ⚠️ **不许含糊**，不许把「跑了一遍工具」当成「逐条核过」。

---

## 六 · 禁止事项（踩过坑，别再犯）

- ⛔ **不许把「指针能解析」当成「结论为真」**（E68：行号合法但指错文件）。
- ⛔ **不许把「歧义档」当缺陷去消**（那会让指针变成不可维护的长路径，且不解决真实性问题）。
- ⛔ **不许为了让工具变绿而改产品代码**。
- ⛔ **不许编造行号** ⇒ 没核过就写「待核实」。
- ⛔ **不许动别人域的文件**（并发写同一棵树 ⇒ 事后无法区分归属）。
