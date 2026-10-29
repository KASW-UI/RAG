# `tools/ledger-derive/` —— 三目录台账 → **阶段一施工单派生层**

> **一句话**：把 `docs/DRIFT` + `docs/Intentional_Deviations` + `docs/Pitfalls`
> 这三本**事实与证据层**台账，投影成一层**可当施工单用**的派生表
> （`ID → P0-P3 × A-D × 是否阻断阶段一 → 对最终工程目标的影响 → 排期指针 → 证据缺口`）。

| 文件 | 作用 |
|---|---|
| [`derive-stage1.py`](derive-stage1.py) | **生成器 + 守门**（同一脚本，`--check` 模式做漂移检测） |
| [`excluded-id-prefixes.txt`](excluded-id-prefixes.txt) | **显式排除清单**（制表符分隔：`<前缀>\t<理由>`）—— 口径变更留痕，不藏在代码里 |
| [`overlay-scope.txt`](overlay-scope.txt) | **U5-O 定级的适用域**（制表符分隔：`<编号>\t<域1>,<域2>`）—— **由 [`gen-overlay-scope.py`](gen-overlay-scope.py) 从派工清单生成**，不猜 |
| [`identity-groups.txt`](identity-groups.txt) | **同源关系表**（制表符分隔：`<编号A>\t<域A>\t<编号B>\t<域B>\t<关系>\t<来源>`）—— **例外清单**：这两**节点**是**同一件事** ⇒ 仍合并成一条。**节点 = (编号, 所属域)** ⇒ 支持「同编号跨域」与「不同编号别名（长/短编号）」两类 |
| [`gen-overlay-scope.py`](gen-overlay-scope.py) | 由派工清单（`docs/DRIFT/_cross/grading/_lanes.json`）**重生成** `overlay-scope.txt`（`--check` 只校验不写盘） |
| [`gen-identity-groups.py`](gen-identity-groups.py) | 由**人工同源判定**（`docs/DRIFT/_cross/grading/_samesrc-*.md`）**重生成** `identity-groups.txt`（`--check` 只校验不写盘）—— 本体位置**机械反查**（`resolve_pointer`），不手填编号与域 |

## 用法

```bash
cd "$(git rev-parse --show-toplevel)"
python3 tools/ledger-derive/derive-stage1.py            # 重新生成产物
python3 tools/ledger-derive/derive-stage1.py --check    # 守门：源台账改了而派生层没重生成 ⇒ EXIT=1
python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计，不写文件
python3 tools/ledger-derive/gen-overlay-scope.py --check  # 守门：域适配表与派工清单不一致 ⇒ EXIT=1
python3 tools/ledger-derive/gen-identity-groups.py --check  # 守门：同源关系表与人工判定不一致 ⇒ EXIT=1
```

**产物**（在 `docs/DRIFT/_cross/`）：
- [`stage1-derivation.md`](../docs/DRIFT/_cross/stage1-derivation.md) —— 人读层：口径 + 规则表 + 施工单视图 + 真缺口清单 + 全量表
- [`stage1-derivation.tsv`](../docs/DRIFT/_cross/stage1-derivation.tsv) —— 机器层：全量问题项 × 16 列

## ⚠️ 条目身份 = **（编号, 所属域目录）**（2026-10-30 用户拍板）

台账用**裸编号**（`E1` / `E2` / `L-4` / `T-6`…，**没有域前缀**）⇒ **同一个编号在不同域通常指的不是同一件事**
⇒ 按裸编号合并会把一个域的状态/优先级/阶段归属**继承**到另一个域 ⇒ **派单会派到不存在的事上**
（实测：4 条假缺口 + 1 条 **A 类被降级成 B 类**）。

⇒ **三条配套规则**（缺一不可）：

1. **判定按域拆开** —— 合并键 = `(编号, 域)`；同域内仍按「最新核对日期」归一历史快照。
2. **指标按编号级聚合** —— 「未定级」「真缺口」「已闭合」都**按编号只算一次**
   ⇒ 否则同一编号在 N 个域各一行会被数成 N 条 ⇒ 指标虚增。
3. **覆盖层按域限定** —— U5-O（人工定级）用 `overlay-scope.txt`；U5-0（台账表格里的历史定级）用
   **那张表所在文件的域** ⇒ 否则一个域的定级会套到另一个域的同名条目上。
   ⇒ **例外** = `identity-groups.txt`（同源关系表）⇒ 那批**节点**是**同一件事的跨域/别名登记**
   ⇒ 仍合并成一条（用**并查集**归组 ⇒ 组内**域集合**写进合并键）。

⇒ ⚠️ **同源关系的来源是「人工逐条判定」，不是机器推断**：
判定表在 `docs/DRIFT/_cross/grading/_samesrc-W*.md`（三档：`同一件事` / `不同的事` / `无法判定`）
⇒ **「不同的事」「无法判定」一律不建边** ⇒ 派生层**不猜** ⇒ 保持拆开并在派生层 §4.6 露出。
⇒ ⚠️ **第 5 列「本体位置」的路径序位有语义**：**第 1 个 = 本体**；其余 = 引用方 ⇒ 只在**反查编号相同**时建边。
⚠️ **判定表本身被改动 ⇒ `gen-identity-groups.py --check` 会红** ⇒ 关系表**不允许手改**。

## 三条纪律（写在代码里，不是写在文档里）

1. **派生层不重新裁定任何结论** —— 每个派生值都带 `rules` 规则号，可回源到 `文件:行号`。
   **派生不出就写 `—`（待人工定级），绝不猜。**
2. **「跑绿 ≠ 对」** —— U3 的「A 活体」只证明**那条探针的断言**成立，
   **不证明本条目结论的全量成立**。
   ⇒ 且 **A 档带探针的真实退出码**（`A 活体 · 绿(0)` / `红(1)` / `环境(2)` / `混(0/1/2)`）——
   实测 130 条 A 档里**只有 99 条是绿的**，其余 31 条的证据本身是红/环境/混合
   ⇒ **只写「A 活体」会让人误以为这条已被证实**。
3. **「指针能解析」≠「结论为真」** —— 本层只读台账，**不读产品代码**；
   它是台账的**投影**，不是代码的事实。

## ⚠️ 两个自噬陷阱（已修，勿回退）

本工具扫的是 `docs/DRIFT/**` 等目录，而**它自己的产物就在 `docs/DRIFT/_cross/`** ⇒
**产物会被当成输入扫回去** ⇒ 凭空多出上千条「条目」，且 `--check` 会**自我否定**（恒红）。
⇒ 两处扫描（`scan_ledgers` / `scan_grading_tables`）**都必须**跳过 `SELF_ARTIFACTS`。

## ⚠️ 口径边界（别把它当「全部问题」）

- 宽口径扫描会**认出旧 `ID_RE` 认不出的整族编号**（`MISMATCH-API-*` / `OP-*` / `T-*` / `N-*` /
  `CD-*` / `ADJ-*` / `E60` 这类「字母+数字」形态）⇒ **「落空 0」类结论的覆盖面小于表面**。
- **`CASE-*` 被排除**（测试场景矩阵行 = 证据层，不是差异条目）⇒ 差异条目会引用 `CASE-*` 作证据。
- **「未排期」≠「不重要」** —— 它只说明**当前没有任何任务/未解决项指向它**。
