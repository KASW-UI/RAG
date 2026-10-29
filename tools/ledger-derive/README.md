# `tools/ledger-derive/` —— 三目录台账 → **阶段一施工单派生层**

> **一句话**：把 `docs/DRIFT` + `docs/Intentional_Deviations` + `docs/Pitfalls`
> 这三本**事实与证据层**台账，投影成一层**可当施工单用**的派生表
> （`ID → P0-P3 × A-D × 是否阻断阶段一 → 对最终工程目标的影响 → 排期指针 → 证据缺口`）。

| 文件 | 作用 |
|---|---|
| [`derive-stage1.py`](derive-stage1.py) | **生成器 + 守门**（同一脚本，`--check` 模式做漂移检测） |
| [`excluded-id-prefixes.txt`](excluded-id-prefixes.txt) | **显式排除清单**（制表符分隔：`<前缀>\t<理由>`）—— 口径变更留痕，不藏在代码里 |

## 用法

```bash
cd "$(git rev-parse --show-toplevel)"
python3 tools/ledger-derive/derive-stage1.py            # 重新生成产物
python3 tools/ledger-derive/derive-stage1.py --check    # 守门：源台账改了而派生层没重生成 ⇒ EXIT=1
python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计，不写文件
```

**产物**（在 `docs/DRIFT/_cross/`）：
- [`stage1-derivation.md`](../docs/DRIFT/_cross/stage1-derivation.md) —— 人读层：口径 + 规则表 + 施工单视图 + 真缺口清单 + 全量表
- [`stage1-derivation.tsv`](../docs/DRIFT/_cross/stage1-derivation.tsv) —— 机器层：全量问题项 × 15 列

## 三条纪律（写在代码里，不是写在文档里）

1. **派生层不重新裁定任何结论** —— 每个派生值都带 `rules` 规则号，可回源到 `文件:行号`。
   **派生不出就写 `—`（待人工定级），绝不猜。**
2. **「跑绿 ≠ 对」** —— U3 的「A 活体」只证明**那条探针的断言**成立，
   **不证明本条目结论的全量成立**。
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
