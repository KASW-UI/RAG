#!/usr/bin/env python3
"""台账「表格行完整性」守门（global 域自用 + 共享索引）

> ⚠️ **这个脚本解决什么问题**
> 往台账里**插一行**时，如果落点落在**表格块内部**，或者用**长文本替换**把上一行的
> **行首**吃掉 ⇒ 那一行就变成「**没有行首的内容行**」⇒ GFM 把它当作上一行的
> **lazy continuation** ⇒ **表格结构静默塌陷**：
> 内容一个字没丢、没有任何报错、但渲染出来是错的（该行被并进上一行的单元格）。
>
> **实测（2026-10-26 · global 域自查）**：`docs/DRIFT/global/_README.md` 的时间线表
> 有 **3 行**（第三十七/三十八/三十九轮）、`docs/DRIFT/_INDEX.md` 的域表内有 **3 行**
> （第三十六/三十七/三十八轮）落成了这种形态 ⇒ 连续 **3 轮**（第 36/37/38 轮）都没人发现
> ⇒ **破坏者是落账脚本本身**：每轮都用「长文本 `replace`」插新行 ⇒ 把**上一行的行首**覆盖掉。
>
> ⚠️ **为什么这是个独立缺陷类**：它**不违反任何内容断言** ⇒
> 第 1~4 层的台账复核器（`tools/check-ledger-selfcheck.py`）**查不出来**
> （它看的是「引用悬不悬空 / 断言有没有对应物」）⇒ ⇒ **必须单开一个结构层守门**。

## 判据

**表格块内部不得出现「无行首的内容行」**。

具体地：把一个文件里**连续的、不以 `|` 开头且非空**的行看作一个「运行块」；
若该运行块**紧前面一行是 `|` 开头**（表格行）**且紧后面一行也是 `|` 开头** ⇒
⇒ 这个运行块**落在表格块内部** ⇒ 🔴 **缺陷**。

- 之所以用「运行块」而不是「单行」：一轮的破坏往往**一次吃掉多行**（实测 3 行）。
- 之所以要求「前后都是 `|` 行」：**GFM 里空行才终结表格块** ⇒
  中间有空行隔开的两个表格不会互相误判。
- 之所以不要求行首缩进：实测两种形态里有一种（`· **第NN轮：…`）**没有缩进**。

## 边界（别误用）

- ⚠️ 本脚本**只管结构**，**不管内容**：它查不出「写错了内容」，那是 `check-ledger-selfcheck.py` 的活。
- ⚠️ **默认扫描面 = 三个台账目录全量**（2026-10-30 扩围）⇒
  **任何域的台账有同样形态，本门禁都会变红**（原口径只扫 `global` 域自己写的台账 ⇒
  `users`/`follow`/`task` 等域**结构坑无守门** ⇒ follow+task lane 报的缺口）。
  `--all` 与默认面等价（实测 `docs` 全量 EXIT=0）。
- ⚠️ 本脚本**只读**，不改任何文件。

## 扫描范围（默认 · 2026-10-30 扩围后）

| 路径 | 为什么在范围内 |
|---|---|
| `docs/DRIFT/**` | 全部域的漂移台账 |
| `docs/Pitfalls/**` | 全部域的踩坑台账 |
| `docs/Intentional_Deviations/**` | 签字偏离档 |
| `docs/DRIFT/_INDEX.md` · `docs/Pitfalls/_INDEX.md` | 两个共享索引 |

## 用法

```bash
python3 tools/check-ledger-tables.py              # 默认范围 ⇒ EXIT=0 才算合规
python3 tools/check-ledger-tables.py --all        # 全仓扫（盘点用）
python3 tools/check-ledger-tables.py --mutation-test   # 自证非空转（内建变异）
```

退出码：0 = 无违规；1 = 有违规（**可当门禁用**）。
"""
import argparse
import io
import os
import shutil
import sys
import tempfile

# ── 默认扫描范围（**三个台账目录全量** + 两个共享索引）────────────────────
# ⚠️ **2026-10-30 扩围（follow+task lane 报的缺口）**：原默认只扫 `global` 域自己写的台账
#    ⇒ `docs/DRIFT/{users,follow,task}/**` · `docs/Pitfalls/{users,follow,task}/**`
#    ⇒ **结构坑（规约 §〇之二 的 ①标题嵌可变状态 ②标题被并进上一行表格末格
#      ③表格末格被拆行 ④同一条目内标题状态与表格状态行自相矛盾）在本域无任何守门**
#    ⇒ 「别的域坏了不会让本门禁变红」在本 phase（**三目录全量真实性重核**）已不成立
#    ⇒ **默认面扩到三个台账目录全量**（`--all` 的实测仍是 EXIT=0 ⇒ 无存量违规）。
DEFAULT_SCAN = [
    'docs/DRIFT',
    'docs/Pitfalls',
    'docs/Intentional_Deviations',
    'docs/DRIFT/_INDEX.md',
    'docs/Pitfalls/_INDEX.md',
]


def find_violations(lines):
    """返回 [(起始行号(1基), 结束行号(1基), 首行文本)] —— 表格块内部的无行首内容行。"""
    out = []
    n = len(lines)
    i = 0
    while i < n:
        if lines[i].startswith('|') or lines[i].strip() == '':
            i += 1
            continue
        a = i
        while i < n and lines[i].strip() != '' and not lines[i].startswith('|'):
            i += 1
        b = i - 1
        prev_is_row = a - 1 >= 0 and lines[a - 1].startswith('|')
        next_is_row = i < n and lines[i].startswith('|')
        if prev_is_row and next_is_row:
            out.append((a + 1, b + 1, lines[a]))
    return out


def iter_md_files(root, targets):
    for t in targets:
        p = os.path.join(root, t)
        if os.path.isfile(p):
            yield p
        elif os.path.isdir(p):
            for dp, dn, fns in os.walk(p):
                dn[:] = [d for d in dn if d not in ('.git', 'target', '__pycache__')]
                for fn in sorted(fns):
                    if fn.endswith('.md'):
                        yield os.path.join(dp, fn)


def scan(root, targets):
    """返回 [(相对路径, 起始行, 结束行, 文本)]"""
    hits = []
    for path in iter_md_files(root, targets):
        rel = os.path.relpath(path, root)
        lines = io.open(path, encoding='utf-8').read().split('\n')
        for a, b, txt in find_violations(lines):
            hits.append((rel, a, b, txt))
    return hits


# ── 变异测试：自证本门禁**不是空转** ──────────────────────────────────────
def run_mutation_test(root):
    """三条变异：
      M1 阳性：把某条时间线行的**行首**吃掉（复刻第 36~39 轮的真实破坏）
             ⇒ 门禁**必须报出来**，且报的行号就是那一行
      M2 阳性：把一行普通内容**插进表格块内部**（复刻「落点落进表格」）
             ⇒ 门禁**必须报出来**
      M3 阴性：原样不动 ⇒ 门禁**必须一条都不报**（否则说明它在乱报）
    """
    bad = 0
    with tempfile.TemporaryDirectory() as td:
        base = os.path.join(td, 'repo')
        for t in DEFAULT_SCAN:
            s = os.path.join(root, t)
            d = os.path.join(base, t)
            if os.path.isdir(s):
                shutil.copytree(s, d)
            else:
                os.makedirs(os.path.dirname(d), exist_ok=True)
                shutil.copy2(s, d)

        ledger = os.path.join(base, 'docs/DRIFT/global/_README.md')
        raw = io.open(ledger, encoding='utf-8').read()
        lines = raw.split('\n')

        # 找一条**带日期行首**的时间线行当靶子（保证变异打在「行首」这个形态上）
        target = None
        for idx, l in enumerate(lines):
            if l.startswith('| 2026-10-26 |') and '**第' in l:
                target = idx
                break
        if target is None:
            print('  ❌ 变异前置失败：在 _README.md 里找不到「带日期行首」的时间线行')
            return 1

        # ── M3 阴性对照（先跑：原样必须干净）──────────────────────────
        hits = scan(base, DEFAULT_SCAN)
        if hits:
            print(f'  ❌ M3 阴性对照失败：原样的台账本该 0 违规，却报 {len(hits)} 条')
            for h in hits[:3]:
                print(f'       {h[0]}:{h[1]}')
            bad += 1
        else:
            print('  ✅ M3 阴性对照：原样台账 0 违规（门禁不会乱报）')

        # ── M1 阳性：吃掉时间线行的行首 ────────────────────────────────
        mutated = list(lines)
        mutated[target] = mutated[target][len('| 2026-10-26 | '):]
        io.open(ledger, 'w', encoding='utf-8').write('\n'.join(mutated))
        hits = scan(base, DEFAULT_SCAN)
        rel = os.path.relpath(ledger, base)
        exact = [h for h in hits if h[0] == rel and h[1] <= target + 1 <= h[2]]
        if len(hits) == 1 and exact:
            print(f'  ✅ M1 阳性：吃掉行首 ⇒ 门禁**精确报了那一行**'
                  f'（{rel}:{target + 1}）')
        else:
            print(f'  ❌ M1 阳性失败：吃掉行首后门禁报 {len(hits)} 条（应为 1 且命中 {target + 1}）')
            for h in hits[:3]:
                print(f'       {h[0]}:{h[1]}-{h[2]}')
            bad += 1

        # ── M2 阳性：把一行内容插进表格块内部 ──────────────────────────
        mutated = list(lines)
        idx2 = None
        for idx, l in enumerate(mutated):
            if l.startswith('| 2026-10-26 |') and '**第' in l:
                idx2 = idx
                break
        mutated.insert(idx2 + 1, '· **某轮临时说明**：这行落进了表格块内部')
        io.open(ledger, 'w', encoding='utf-8').write('\n'.join(mutated))
        hits = scan(base, DEFAULT_SCAN)
        rel = os.path.relpath(ledger, base)
        exact = [h for h in hits if h[0] == rel and h[1] <= idx2 + 2 <= h[2]]
        if len(hits) == 1 and exact:
            print(f'  ✅ M2 阳性：内容落进表格块内部 ⇒ 门禁**精确报了那一行**'
                  f'（{rel}:{idx2 + 2}）')
        else:
            print(f'  ❌ M2 阳性失败：插入后门禁报 {len(hits)} 条（应为 1 且命中 {idx2 + 2}）')
            for h in hits[:3]:
                print(f'       {h[0]}:{h[1]}-{h[2]}')
            bad += 1

    return bad


def main():
    ap = argparse.ArgumentParser(description='台账表格行完整性守门')
    ap.add_argument('--root', default='.', help='仓库根目录')
    ap.add_argument('--all', action='store_true', help='全仓扫（默认只扫 global 自己的台账）')
    ap.add_argument('--mutation-test', action='store_true', help='自证非空转')
    args = ap.parse_args()

    if args.mutation_test:
        print('── 变异测试（自证门禁非空转）──')
        bad = run_mutation_test(args.root)
        if bad:
            print(f'\n⇒ ❌ 变异测试失败（{bad} 条）')
            return 1
        print('\n⇒ ✅ 变异测试全部通过（门禁非空转，且红在目标行上）')
        return 0

    targets = ['docs'] if args.all else DEFAULT_SCAN
    hits = scan(args.root, targets)
    print(f'扫描范围：{", ".join(targets)}')
    if not hits:
        print('✅ 台账表格结构完好：表格块内部没有任何「无行首的内容行」')
        return 0
    print(f'\n🔴 发现 {len(hits)} 处「表格块内部的无行首内容行」'
          f'（⇒ 渲染时会被并进上一行的单元格 ⇒ 表格结构静默塌陷）：\n')
    for rel, a, b, txt in hits:
        rng = f'{a}' if a == b else f'{a}-{b}'
        print(f'  ❌ {rel}:{rng}')
        print(f'     {txt[:110]}')
    return 1


if __name__ == '__main__':
    sys.exit(main())
