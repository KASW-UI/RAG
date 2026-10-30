#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-mutation-anchor-liveness.py —— **变异脚本的锚点存活守门**（只读型）。

══════ 背景（2026-10-31 · 合并收口执行者）══════

`tools/difftest/mutate-*.py` 里有一族**变异脚本**：它们改生产码 / 探针 ⇒ 再跑守门
⇒ 期望守门**精确转红** ⇒ 用来证明「守门不是假绿」。

⚠️ **但这些脚本本身没有守门** ⇒ ⇒ 有一条洞：
**变异脚本会随生产代码演进而「空转」** —— 它的旧串在新代码里**命中 0 处**
⇒ ⇒ **变异根本没发生** ⇒ ⇒ 守门当然不会红 ⇒ ⇒ **「变异转红」这个证据变成空的**
⇒ ⇒ 而**脚本照样 EXIT=0**（它自己就判「锚点命中数 ≠ 1 ⇒ 报错」，
可**没人跑它** ⇒ ⇒ 于是「判据失去判别力」这件事**没有任何机器信号**）。

**实证（RAG 仓 · `mutate-deadline-downstream.py` 的注释里已记三轮）**：
① 第一轮：旧串 `DeadlineState.bindToCurrent(deadlineEpochMs)` 被生产代码改成
   `bindRemainingNanos(...)` ⇒ 锚点命中 **0 处** ⇒ M1 空转；
② 第二轮：改到 `RpcInterceptor.handle` 的 worker 绑定点 ⇒ 锚点命中了，
   **但探针仍绿** ⇒ 根因 = **观测点与绑定点不在同一条路径**；
③ 第三轮：G4 把 `onHalfClose` 的绑定改成 `bindAbsoluteNanos(...)` ⇒ 锚点**再次**命中 0 处
⇒ ⇒ **三轮都是「变异空转 ⇒ 判据失去判别力」**。

══════ 本守门怎么判（**只读**，不注入任何代码）══════

对每个 `mutate-*.py` 的每条变异：

  ① **目标文件可解析且唯一** —— 否则变异会打空。
  ② **存在一组相邻字符串对，其中恰有一个出现在目标文件里** —— 否则 = **死锚点**
     ⇒ 「旧串命中 0 处 ⇒ 变异空转」⇒ 🔴。
  ③ **那个出现在文件里的串，命中数必须恰好 = 1** ——
     命中 **0 处** = 空转（上条）；命中 **> 1 处** = 变异会改多处 ⇒ 判据不精确 ⇒ 🔴。
  ④ **期望红键集合不得为空** —— 空集 = 「期望什么都不会红」⇒ 判据真空 ⇒ 🔴。
  ⑤ **期望红键必须在行为键词表里**（仅对有词表来源的脚本）——
     拼错的键 ⇒ 探针输出的解析永远匹配不到 ⇒ 判据空转 ⇒ 🔴。

⚠️ **本门禁的边界**：它判的是「**变异脚本此刻还是不是活的**」，
**不是**「变异会不会精确转红」（那要真跑变异脚本 ⇒ 它会注入改动到生产码
⇒ 不宜在共享工作树常驻跑 ⇒ ⇒ 那一步仍靠人工/CI 独立容器）。

用法：python3 tools/difftest/check-mutation-anchor-liveness.py
退出码：0 = 通过；1 = 不通过
"""
import ast
import glob
import io
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
DIFTEST = os.path.join(ROOT, 'tools', 'difftest')

# ── 行为键词表来源：脚本 → (守门脚本相对路径, 键变量名) ─────────────────────
KEY_VOCAB_SOURCES = {
    'mutate-deadline-downstream.py': 'check-deadline-downstream.py',
}


def load_module_constants(path):
    """取模块级字符串常量（供 AST 里的 Name 求值）。"""
    src = io.open(path, encoding='utf-8').read()
    tree = ast.parse(src)
    ns = {}
    for node in tree.body:
        if (isinstance(node, ast.Assign)
                and isinstance(node.value, ast.Constant)
                and isinstance(node.value.value, str)):
            for t in node.targets:
                if isinstance(t, ast.Name):
                    ns[t.id] = node.value.value
    return src, tree, ns


def eval_el(node, ns):
    """把一个 AST 表达式求值成 Python 值（只支持常量 / 字符串常量名 / 集合 / 元组 / 列表）。"""
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.Name):
        if node.id in ns:
            return ns[node.id]
        return '<Name:%s>' % node.id
    if isinstance(node, ast.Set):
        return set(eval_el(e, ns) for e in node.elts)
    if isinstance(node, ast.Dict):
        return dict((eval_el(k, ns), eval_el(v, ns)) for k, v in zip(node.keys, node.values))
    if isinstance(node, (ast.Tuple, ast.List)):
        return [eval_el(e, ns) for e in node.elts]
    return '<%s>' % type(node).__name__


def extract_mutations(path):
    """返回 (mutations, error)。mutations = [ (idx, [元素值...]) ]"""
    src, tree, ns = load_module_constants(path)
    for node in tree.body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], 'id', '') == 'MUTATIONS':
            muts = []
            for i, el in enumerate(node.value.elts):
                muts.append((i, [eval_el(x, ns) for x in el.elts]))
            return muts, None
    return None, '没有 MUTATIONS 赋值'


def resolve_path(elem, scope=None):
    """把一个字符串元素解析成仓内文件路径（唯一解）。返回 (path, how) 或 (None, why)。

    `scope` = 模块标签（如 `hot-api`）⇒ 用于**按模块限定**文件名搜索
    ⇒ 否则同名文件在多个模块下会命中多处（如 `GoFormValues.java` 同时存在于
    hot-api 与 article-api）⇒ 解析就不唯一了。
    """
    if not isinstance(elem, str) or '/' not in elem:
        return None, '不是路径形态'
    direct = os.path.join(ROOT, elem)
    if os.path.isfile(direct):
        return direct, '直接命中'
    base = os.path.basename(elem)
    hits = []
    if scope:
        # ⚠️ 限定到模块目录内
        for pat in (os.path.join(ROOT, 'service', scope, '**', base),
                    os.path.join(ROOT, 'service', '**', scope, '**', base)):
            hits += glob.glob(pat, recursive=True)
    else:
        for top in ('service', 'tools', 'docs'):
            hits += glob.glob(os.path.join(ROOT, top, '**', base), recursive=True)
    hits = sorted(set(h for h in hits if os.path.isfile(h)))
    if len(hits) == 1:
        return hits[0], ('模块 %s 内按文件名唯一命中' % scope if scope else '按文件名唯一命中')
    if len(hits) > 1:
        return None, '按文件名命中 %d 处（不唯一）' % len(hits)
    return None, '按文件名零命中'


def module_scope(values):
    """从元组里找模块标签（短 token，且 `service/**/<token>` 是个真目录）。"""
    for v in values:
        if not isinstance(v, str) or '/' in v or '.' in v or len(v) > 16:
            continue
        for cand in glob.glob(os.path.join(ROOT, 'service', '**', v), recursive=True):
            if os.path.isdir(cand):
                return v
    return None


def is_noise_token(v, scope):
    """判定某个字符串元素是不是「干扰 token」（不是代码锚点）。

    ① 变异编号（`M01` / `R06` / `HC02` / `E48`…）
    ② 模块标签（`hot-api` / `hot-rpc` / `hot-common`…）
    ⇒ ⇒ 这两类**可能恰好在文件里出现**（例如模块标签 `hot-rpc` 在
    HotEventConsumer.java 里出现 2 次）⇒ ⇒ 若不排除，邻接对会被它们抢走
    ⇒ ⇒ 报出「锚点命中 2 处」的**假阳性**。
    """
    if not isinstance(v, str):
        return True
    if scope and v == scope:
        return True
    return bool(re.match(r'^(?:M|R|HC|E|X|MU)?\d{1,3}[A-Za-z]?$', v.strip()))


def find_anchor_pair(values, target_text, path_elem, scope=None):
    """在元组里找相邻字符串对；返回 (old, new, hits) 或 (None, None, why)。

    ⚠️ **候选对的取舍规则（三条，缺一不可）**：
      ① **相邻**（只认元组里紧挨着的两个字符串元素）；
      ② 排除**路径元素**与**干扰 token**（变异编号 / 模块标签）；
      ③ **「恰有一个出现」优先**；若**两个都出现** ⇒ ⇒ 取**命中数为 1** 的那个当锚点
        （这正是变异脚本自己 `count(old)==1` 的口径）⇒ ⇒ 两者都命中 1 次时取**更长**的
        （更具体 ⇒ 更可能是真锚点；「新串是旧串子串」的情形由此覆盖）。
    ⇒ ⇒ 候选对里再取「**锚点最长**」的一对（最具体 ⇒ 最可能是真锚点）。
    """
    strs = [(i, v) for i, v in enumerate(values) if isinstance(v, str)]
    best = None          # (anchor, other, hits)
    ambiguous = None
    for (ia, a), (ib, b) in zip(strs, strs[1:]):
        if ia != ib - 1:
            continue          # 只看**相邻**的字符串元素
        if a == path_elem or b == path_elem:
            continue          # 排除路径元素
        if is_noise_token(a, scope) and is_noise_token(b, scope):
            continue
        if not a and not b:
            continue
        if not a or not b:
            # ⚠️ 「新串为空」是合法变异（= 删掉那段代码）⇒ ⇒ 只要求锚点侧非空
            a, b = (a, b) if a else (b, a)
        if a == b:
            continue
        ca, cb = target_text.count(a), target_text.count(b)
        if ca > 0 and cb == 0:
            cand = (a, b, ca)
        elif cb > 0 and ca == 0:
            cand = (b, a, cb)
        elif ca > 0 and cb > 0:
            # 两个都出现 ⇒ 取**命中数为 1** 的那个（= 变异脚本自己的口径）
            if ca == 1 and cb != 1:
                cand = (a, b, ca)
            elif cb == 1 and ca != 1:
                cand = (b, a, cb)
            elif ca == 1 and cb == 1:
                # 都是 1 ⇒ 取更长的（更具体 ⇒ 更可能是真锚点）
                cand = (a, b, ca) if len(a) >= len(b) else (b, a, cb)
            else:
                ambiguous = (a, b, 'ambiguous:%d/%d' % (ca, cb))
                continue
        else:
            continue
        if len(cand[0]) < 8 or is_noise_token(cand[0], scope):
            continue          # 排除过短的「锚点候选」与干扰 token
        if best is None or len(cand[0]) > len(best[0]):
            best = cand
    if best is None:
        if ambiguous:
            return ambiguous[0], ambiguous[1], ambiguous[2]
        return None, None, '没有相邻串对呈现「恰有一个出现在文件里」'
    return best[0], best[1], best[2]


def key_vocabulary(mutate_name):
    """取行为键词表（若该脚本有词表来源）。返回 set 或 None。"""
    src_name = KEY_VOCAB_SOURCES.get(mutate_name)
    if not src_name:
        return None
    path = os.path.join(DIFTEST, src_name)
    if not os.path.isfile(path):
        return None
    src, tree, ns = load_module_constants(path)
    keys = set()
    for node in tree.body:
        if isinstance(node, ast.Assign):
            name = getattr(node.targets[0], 'id', '')
            if name == 'PARITY_KEYS':
                v = eval_el(node.value, ns)
                if isinstance(v, (list, tuple)):
                    keys |= set(v)
            elif name == 'EXPECTED_DRIFT':
                v = eval_el(node.value, ns)
                if isinstance(v, dict):
                    keys |= set(v.keys())
    return keys or None


def main():
    scripts = sorted(glob.glob(os.path.join(DIFTEST, 'mutate-*.py')))
    if not scripts:
        print('❌ 一个 mutate-*.py 都没找到 ⇒ 本守门无从判断')
        return 1

    problems = []
    covered = 0
    uncovered = []

    for sp in scripts:
        name = os.path.basename(sp)
        muts, err = extract_mutations(sp)
        if muts is None:
            uncovered.append('%s（%s ⇒ 形态不同，不在本门覆盖面）' % (name, err))
            continue
        vocab = key_vocabulary(name)
        for idx, values in muts:
            covered += 1
            label = values[0] if isinstance(values[0], str) else '#%d' % idx
            scope = module_scope(values)
            # ① 目标文件
            targets = []
            for v in values:
                p, how = resolve_path(v, scope)
                if p:
                    targets.append((p, how, v))
            if len(targets) != 1:
                problems.append('%s %s：目标文件解析出 %d 个（应为 1）' % (name, label, len(targets)))
                continue
            target, how, raw = targets[0]
            text = io.open(target, encoding='utf-8').read()
            # ②③ 锚点存活
            old, new, hits = find_anchor_pair(values, text, raw, scope)
            if old is None:
                problems.append(
                    '%s %s：🔴 **锚点命中 0 处 ⇒ 变异脚本自身的前置条件（`count(old)==1`）不满足**'
                    '⇒ ⇒ 该条变异**此刻跑不起来** ⇒ ⇒ 「变异转红」这个证据拿不到'
                    '（目标 %s；%s）' % (name, label, os.path.relpath(target, ROOT), hits))
                continue
            if isinstance(hits, str):      # ambiguous
                problems.append(
                    '%s %s：🔴 **旧串与新串在文件里都命中**（%s）⇒ 判据不精确'
                    '（目标 %s）' % (name, label, hits, os.path.relpath(target, ROOT)))
                continue
            if hits != 1:
                problems.append(
                    '%s %s：🔴 **锚点命中 %d 处（应为 1 —— 变异脚本自身要求恰好 1 处）**'
                    '⇒ ⇒ 该条变异**此刻跑不起来** ⇒ ⇒ 「变异转红」这个证据拿不到'
                    '（目标 %s）' % (name, label, hits, os.path.relpath(target, ROOT)))
                continue
            # ④ 期望红键集合不得为空
            expected = None
            for v in reversed(values):
                if isinstance(v, (set, dict)):
                    expected = v
                    break
                if isinstance(v, list) and v and all(isinstance(x, str) for x in v):
                    expected = set(v)
                    break
            if expected is not None and len(expected) == 0:
                problems.append(
                    '%s %s：🔴 **期望红键集合为空** ⇒ 「期望什么都不会红」⇒ 判据真空'
                    % (name, label))
                continue
            # ⑤ 期望红键必须在词表里
            if vocab and expected:
                bad = set(expected) - vocab
                if bad:
                    problems.append(
                        '%s %s：🔴 **期望红键 %s 不在行为键词表里** ⇒ 探针输出永远匹配不到 ⇒ 判据空转'
                        % (name, label, sorted(bad)))
                    continue
            print('  ✔ %-28s %-14s 锚点命中 1 处 · 目标 %s（%s）'
                  % (name, label, os.path.relpath(target, ROOT), how))

    print()
    print('── 覆盖面 ──')
    print('   覆盖变异条数 = %d（脚本 %d 个）' % (covered, len(scripts) - len(uncovered)))
    for u in uncovered:
        print('   ⚠️  %s' % u)

    print()
    if problems:
        print('🔴 **发现 %d 条锚点问题**：' % len(problems))
        for p in problems:
            print('   ❌ %s' % p)
        print()
        print('⇒ ⇒ **这些变异此刻是空的** ⇒ ⇒ 「变异转红」这个证据不成立')
        print('⇒ ⇒ 修法 = **重定基**：把旧串改成当前生产代码里**恰好命中 1 处**的那段'
              '（并同步更新期望红键集）')
        return 1
    print('✅ 所有变异锚点均**恰好命中 1 处** ⇒ ⇒ 没有「变异空转 ⇒ 判据失去判别力」的洞')
    return 0


if __name__ == '__main__':
    sys.exit(main())
