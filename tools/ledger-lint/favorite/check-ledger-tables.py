#!/usr/bin/env python3
"""favorite 域台账「表格行完整性」守门（**结构层**，只读）

> **来源**：global 域第四十四轮（`76c3b26`）新登记的结构层缺陷类，原始守门是
> `tools/check-ledger-tables.py`（**global 单写权**）。本文件是**本域收窄版**：
> 判据与判定逻辑同源，**扫描范围收窄到 favorite 域由本 owner 写的台账**，
> **变异靶子也换成自己的台账**（原版的 `--mutation-test` 硬编码 global 自己的
> `docs/DRIFT/global/_README.md` ⇒ 在本分支上直接跑会「变异前置失败」）。

## 判据

**表格块内部不得出现「无行首的内容行」**。

具体地：把一个文件里**连续的、不以 `|` 开头且非空**的行看作一个「运行块」；
若该运行块**紧前面一行是 `|` 开头**（表格行）**且紧后面一行也是 `|` 开头** ⇒
⇒ 这个运行块**落在表格块内部** ⇒ 🔴 **缺陷**。

- 用「运行块」而不是「单行」：一轮破坏往往**一次吃掉多行**。
- 要求「前后都是 `|` 行」：GFM 里**空行**才终结表格块 ⇒ 中间有空行隔开的两个表格不会互相误判。
- 不要求行首缩进：实测两种形态里有一种**没有缩进**。

## 为什么本域需要它

- **内容层守门查不出结构缺陷**：`check-ledger-selfcheck.py` 第 1~4 层看的是
  「引用悬不悬空 / 断言有没有对应物 / 数字对不对」⇒ **不看表格结构**。
- **本域台账是机器编辑的高频产物**（每轮插行、改表）⇒ 而 global 实测的破坏手法正是
  「用长文本 `replace` 插一行 ⇒ 替换目标串恰好是上一行的行首」⇒
  **连续三轮每轮破一次，前三轮都没人发现**（内容一个字没丢 ⇒ 肉眼扫不出）。
- ⇒ 本域把这一层**搬进自己的门禁**（同型做法：`check-ledger-selfcheck.py` 的 ⑤⑥ 两层）。

## 扫描范围（**只扫本 owner 写的文件**）

| 路径 | 为什么在范围内 |
|---|---|
| `docs/DRIFT/favorite/rpc.md` | 本域漂移台账（本 owner 写） |
| `docs/Pitfalls/favorite/rpc.md` | 本域踩坑档（本 owner 写） |
| `docs/Intentional_Deviations/favorite/rpc.md` | 本域签字偏离档（本 owner 写） |

⚠️ **明确排除**（**不是不关心，是不归我处置**）：

| 路径 | 为什么排除 |
|---|---|
| `docs/{DRIFT,Pitfalls,Intentional_Deviations}/favorite/api.md` | **并发会话**负责（favorite-api 域）⇒ 它的缺陷由它自己处置 |
| `docs/{DRIFT,Pitfalls}/favorite/{common,proto}.md` | 属于 favorite-common / favorite-proto **组件**，不在本表结论内 |
| `docs/FileTree/**` | 理论文件树（**用户维护**，提交信息 `add sth`）⇒ 不在本域台账写权内 |

⇒ ⇒ 本守门**不会**因为别的域/别的组件的文件变红。

## 用法

```bash
python3 tools/ledger-lint/favorite/check-ledger-tables.py              # 默认范围 ⇒ EXIT=0 才算合规
python3 tools/ledger-lint/favorite/check-ledger-tables.py --mutation-test   # 自证非空转（内建变异）
```

退出码：0 = 无违规；1 = 有违规。

⚠️ 本脚本**只读**，不改任何文件（`--mutation-test` 也只在**临时副本**上动手）。
"""
import argparse
import io
import os
import shutil
import sys
import tempfile

# ── 本域扫描范围（只含本 owner 写的台账）──────────────────────────────────
SCAN_TARGETS = [
    'docs/DRIFT/favorite/rpc.md',
    'docs/Pitfalls/favorite/rpc.md',
    'docs/Intentional_Deviations/favorite/rpc.md',
]

# ── 变异靶子（本域台账里的一条「时间线表格行」）────────────────────────────
MUTATION_LEDGER = 'docs/DRIFT/favorite/rpc.md'


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
      M1 阳性：把一条**时间线表格行**的行首吃掉（复刻 global 第四十一轮那类真实破坏）
             ⇒ 门禁**必须报出来**，且报的行号就是那一行
      M2 阳性：把一行内容**插进表格块内部**（复刻「落点落进表格」）
             ⇒ 门禁**必须报出来**
      M3 阴性：原样不动 ⇒ 门禁**必须一条都不报**（否则说明它在乱报）
    """
    bad = 0
    with tempfile.TemporaryDirectory() as td:
        base = os.path.join(td, 'repo')
        for t in SCAN_TARGETS:
            s = os.path.join(root, t)
            d = os.path.join(base, t)
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.copy2(s, d)

        ledger = os.path.join(base, MUTATION_LEDGER)
        raw = io.open(ledger, encoding='utf-8').read()
        lines = raw.split('\n')

        # 找一条**带行首**的时间线表格行当靶子（保证变异打在「行首」这个形态上）
        target = None
        for idx, l in enumerate(lines):
            if l.startswith('| 第') and '轮' in l:
                target = idx
                break
        if target is None:
            print(f'  ❌ 变异前置失败：在 {MUTATION_LEDGER} 里找不到「带行首」的时间线表格行')
            return 1

        # ── M3 阴性对照（先跑：原样必须干净）──────────────────────────
        hits = scan(base, SCAN_TARGETS)
        if hits:
            print(f'  ❌ M3 阴性对照失败：原样的台账本该 0 违规，却报 {len(hits)} 条')
            for h in hits[:3]:
                print(f'       {h[0]}:{h[1]}')
            bad += 1
        else:
            print('  ✅ M3 阴性对照：原样台账 0 违规（门禁不会乱报）')

        # ── M1 阳性：吃掉时间线行的行首 ────────────────────────────────
        mutated = list(lines)
        mutated[target] = mutated[target][2:]
        io.open(ledger, 'w', encoding='utf-8').write('\n'.join(mutated))
        hits = scan(base, SCAN_TARGETS)
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
            if l.startswith('| 第') and '轮' in l:
                idx2 = idx
                break
        mutated.insert(idx2 + 1, '· **某轮临时说明**：这行落进了表格块内部')
        io.open(ledger, 'w', encoding='utf-8').write('\n'.join(mutated))
        hits = scan(base, SCAN_TARGETS)
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
    ap = argparse.ArgumentParser(description='favorite 域台账表格行完整性守门')
    ap.add_argument('--root', default='.', help='仓库根目录')
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

    hits = scan(args.root, SCAN_TARGETS)
    print('扫描范围：' + ', '.join(SCAN_TARGETS))
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
