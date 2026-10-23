#!/usr/bin/env python3
"""台账 vs 实际 · 复核器（task 域）

> ⚠️ **这个脚本解决什么问题**
> 台账里的「已修 / 未修 / N 条 / file:line」这类断言**过期时不会报错** ——
> 代码被回退了、台账里的测试数变了、Go 侧的行号漂了，**都没有任何东西会报警**。
> AGENTS.md §6 要求「任何『已修 / 未修』断言都必须带核对日期，否则按 UNKNOWN 处理」，
> 本脚本就是那条纪律的**机械化**：把台账的断言逐条拿去和仓库/Go 真源对拍。

## 五层检查

| 层 | 检查什么 | 判据 |
|---|---|---|
| 1 | 台账引用的**仓库内路径**是否存在 | 文件存在 |
| 2 | 台账的「已修」断言 → 仓库里**能不能查到对应物** | 代码里能查到（**注释里的反面教材不算**） |
| 3 | 台账的**数字断言**（测试数 / 条数 / 编号连续性） | 与 surefire 报告、git 实测一致 |
| 4 | 台账的 **Go 侧 file:line 出处**是否悬空 | 文件存在 + 行号在范围内（真源仓库 + 第三方库，按真源 go.mod 的精确版本） |
| 5 | 仓库里**存在但台账从未点名**的测试类 | 列出（覆盖缺口） |

## 边界（重要，别误用）

- ⚠️ 第 2/4 层只保证「**引用不悬空**」与「**断言有对应物**」，
  **不保证「内容完全对得上」** ⇒ 关键断言仍需**人工抽验**。
- ⚠️ 第 4 层里「同名文件在多个库都存在」的条目会单独列出 ⇒ 归属需人工确认。
- ⚠️ 本脚本**只读**，不改任何文件。

## 用法

```bash
python3 tools/difftest/check-ledger-selfcheck.py                 # 默认：task 域
python3 tools/difftest/check-ledger-selfcheck.py --domain task
python3 tools/difftest/check-ledger-selfcheck.py --go-root /path/to/Sea-RideTheWind_golang
```

退出码：0 = 全部通过；1 = 有不一致（**可当门禁用**）。
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

# ====================================================================
# 配置
# ====================================================================

DEFAULT_GO_ROOT = '/Users/pr/Documents/java&golang/Sea-RideTheWind_golang'

LEDGER_FILES = {
    'task': [
        'docs/DRIFT/task/rpc.md',
        'docs/DRIFT/task/api.md',
        'docs/DRIFT/task/proto.md',
        'docs/DRIFT/task/_README.md',
        'docs/Pitfalls/task/rpc.md',
        'docs/Intentional_Deviations/task/rpc.md',
    ],
}

# 第三方库：版本取自**真源 go.mod**（不是本地碰巧有的版本）
THIRD_PARTY = {
    'goka': 'github.com/lovoo/goka@v1.1.16',
    'kafka-go': 'github.com/segmentio/kafka-go@v0.4.47',
    'go-zero': 'github.com/zeromicro/go-zero@v1.10.2',
    'go-queue': 'github.com/zeromicro/go-queue@v1.2.2',
    'go-redis': 'github.com/redis/go-redis/v9@v9.20.0',
    'sarama': 'github.com/IBM/sarama@v1.43.3',
}

# 台账断言 → 仓库里应当存在的证据（路径、必须出现的正则、最少出现次数、禁止出现的正则）
CLAIMS = [
    ('DRIFT-TASK-RPC-006 已修：六个 listener 用**点分** auto.offset.reset',
     'service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq',
     r'auto\.offset\.reset', 6, r'auto-offset-reset'),
    ('DRIFT-TASK-RPC-001 已修：TaskCountStore 走 Redis（不是内存单例）',
     'service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskCountStore.java',
     r'Redis|StringRedisTemplate', 1, None),
    ('DRIFT-TASK-RPC-002 已修：TaskSinkProperties 用 Duration 承接 init-ttl',
     'service/task/task-rpc/src/main/java/com/rag/task/task/rpc/config/TaskSinkProperties.java',
     r'Duration\s+initTtl', 1, None),
    ('DRIFT-TASK-RPC-005 已修：pipelineHSet 向上抛 Redis 错误（不吞）',
     'service/task/task-rpc/src/main/java/com/rag/task/task/rpc/service/RedisPipelines.java',
     r'throw|Exception', 1, None),
    ('保留策略定案：TaskCountStore **不设 TTL**',
     'service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskCountStore.java',
     r'无 TTL|不设 TTL|no TTL|never expire', 1, None),
    ('新增守门测试 TaskConfigKeyBindingContractTest',
     'service/task/task-rpc/src/test/java/com/rag/task/task/rpc/config/TaskConfigKeyBindingContractTest.java',
     r'class TaskConfigKeyBindingContractTest', 1, None),
    ('OffsetOldest 对拍脚本存在且含「先灌消息」阶段',
     'tools/difftest/run-task-offset-oldest.sh',
     r'灌|prefill|pre-?load|phase.?b', 1, None),
    ('compose 项目名隔离 name: taskprobe（修跨域误删）',
     'tools/difftest/docker-compose.task-kafka.yml',
     r'name:\s*taskprobe', 1, None),
    ('Flyway 独立账本表名 flyway_schema_history_task',
     'service/task/task-rpc/src/main/resources/application.yml',
     r'flyway_schema_history_task', 1, None),
    ('Flyway baseline-version: 0（否则 V1 被静默跳过）',
     'service/task/task-rpc/src/main/resources/application.yml',
     r'baseline-version:\s*0', 1, None),
]

PATH_RE = re.compile(r'(?:service|tools|docs)/[A-Za-z0-9_./-]+\.(?:java|md|py|sh|yml|MD|json)')
CLASS_RE = re.compile(r'`([A-Z][A-Za-z0-9]*(?:Test|ContractTest|GateTest))\b')
REF_RE = re.compile(r'`?([A-Za-z0-9_][A-Za-z0-9_./-]*\.go):(\d+)(?:-(\d+))?`?')


# ====================================================================
# 工具
# ====================================================================

def repo_root():
    """从本文件位置往上找 pom.xml ⇒ 仓库根。"""
    d = os.path.dirname(os.path.abspath(__file__))
    for _ in range(6):
        if os.path.exists(os.path.join(d, 'pom.xml')):
            return d
        d = os.path.dirname(d)
    return os.getcwd()


def strip_comments(text):
    """剥离 Java 注释 —— 让「禁模式」检查只看**真实代码**。

    ⚠️ 必需：`auto-offset-reset` 在源码里**只应作为反面教材出现在注释里**
    （说明「连字符形式会被 Kafka 忽略」）。不剥离就会误判成「修复被回退」。
    """
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    return text


def read(path):
    try:
        return open(path, encoding='utf-8').read()
    except Exception:
        return ''


def ledger_texts(root, domain):
    out = {}
    for f in LEDGER_FILES.get(domain, []):
        p = os.path.join(root, f)
        if os.path.exists(p):
            out[f] = read(p)
    return out


def surefire_counts(root, module):
    d = os.path.join(root, 'service/task', module, 'target/surefire-reports')
    if not os.path.isdir(d):
        return None
    t = f = e = s = 0
    for p in glob.glob(os.path.join(d, '*.xml')):
        r = ET.parse(p).getroot()
        t += int(r.get('tests', 0)); f += int(r.get('failures', 0))
        e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
    return dict(tests=t, failures=f, errors=e, skipped=s)


def git(root, *args):
    r = subprocess.run(['git'] + list(args), cwd=root, capture_output=True, text=True)
    return r.stdout.strip()


# ====================================================================
# 五层检查
# ====================================================================

def layer1_paths(root, texts):
    print('=' * 78)
    print('第 1 层 · 台账引用的仓库内路径是否存在')
    print('=' * 78)
    refs = {}
    for f, t in texts.items():
        for m in PATH_RE.finditer(t):
            refs.setdefault(m.group(0), set()).add(f)
    missing = [(p, sorted(srcs)) for p, srcs in refs.items()
               if not os.path.exists(os.path.join(root, p))]
    print(f'  台账引用路径 {len(refs)} 个 ⇒ 缺失 {len(missing)} 个')
    for p, srcs in missing:
        print(f'    ❌ MISSING  {p}   ← {", ".join(srcs)}')
    if not missing:
        print('    ✅ 全部存在')
    return len(missing)


def layer2_claims(root):
    print('=' * 78)
    print('第 2 层 · 台账「已修」断言 → 仓库里能不能查到对应物')
    print('=' * 78)
    bad = 0
    for desc, path, must_re, min_count, forbid_re in CLAIMS:
        target = os.path.join(root, path)
        files = []
        if os.path.isdir(target):
            for dp, _, fns in os.walk(target):
                files += [os.path.join(dp, f) for f in fns if f.endswith('.java')]
        elif os.path.exists(target):
            files = [target]
        if not files:
            print(f'    ❌ NO-PATH  {desc}')
            bad += 1
            continue
        raw, code = '', ''
        for f in files:
            r = read(f)
            raw += r
            code += strip_comments(r)
        n = len(re.findall(must_re, raw))
        if n < min_count:
            print(f'    ❌ STALE    {desc}  (期望 ≥{min_count} 处 {must_re}，实际 {n})')
            bad += 1
            continue
        if forbid_re:
            hits = len(re.findall(forbid_re, code))
            if hits:
                print(f'    ❌ STALE    {desc}  (**代码里**仍有 {hits} 处 {forbid_re} ⇒ 修复被回退)')
                bad += 1
                continue
            in_comment = len(re.findall(forbid_re, raw))
            print(f'    ✅ OK       {desc}  (代码 0 处 / 注释 {in_comment} 处[反面教材])')
            continue
        print(f'    ✅ OK       {desc}  (匹配 {n} 处)')
    print(f'  ⇒ {len(CLAIMS) - bad}/{len(CLAIMS)} 条断言有对应物')
    return bad


def layer3_numbers(root, texts):
    print('=' * 78)
    print('第 3 层 · 数字断言（实测 vs 台账）')
    print('=' * 78)
    rpc = surefire_counts(root, 'task-rpc')
    api = surefire_counts(root, 'task-api')
    txt = ''.join(texts.values())
    bad = 0

    if rpc:
        print(f'  实测 task-rpc: {rpc}')
    if api:
        print(f'  实测 task-api: {api}')

    m = re.search(r'\*\*(\d+) tests / 0 failures / 0 errors / 0 skipped\*\*', txt)
    claimed = int(m.group(1)) if m else None
    actual = rpc['tests'] if rpc else None
    if claimed is not None and actual is not None:
        good = claimed == actual
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} rpc.md 测试数: 台账 {claimed} / 实测 {actual}')

    a = re.search(r'已修 \| \*\*(\d+) 条\*\*（DRIFT-TASK-RPC', txt)
    b = re.search(r'## 一、已修（(\d+) 条）', txt)
    if a and b:
        good = a.group(1) == b.group(1)
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} 已修条数两处一致: §〇={a.group(1)} §一标题={b.group(1)}')

    ids = sorted(set(int(x) for x in re.findall(r'DRIFT-TASK-RPC-(\d{3})', txt)))
    if ids:
        good = ids == list(range(1, len(ids) + 1))
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} DRIFT-TASK-RPC 编号连续: {ids}')

    for name, r in (('task-rpc', rpc), ('task-api', api)):
        if r:
            good = r['failures'] == 0 and r['errors'] == 0 and r['skipped'] == 0
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} {name} failures/errors/skipped 全 0')
            if not good:
                print(f'       ⚠️ skipped={r["skipped"]} ⇒ 「全绿」可能意味着「没跑」')

    # commit 数与台账标题声明（对**分支基线**计数，不是 HEAD^）
    # ⚠️ 只认**章节标题行**（以 # 开头）—— 否则会误匹配到「历史描述」里引用的旧数字
    #    （本轮就踩到：台账正文里写着「① 章节标题「24 个 commit」过期（实际 31）」）
    m = re.search(r'^#.*共 \*\*(\d+) 个 commit\*\*', txt, re.M)
    if m:
        claimed_commits = int(m.group(1))
        branch = git(root, 'rev-parse', '--abbrev-ref', 'HEAD')
        # 基线取「与主干分叉点」⇒ 与台账口径一致
        merge_base = git(root, 'merge-base', 'HEAD', 'do-my-own-gh')
        if merge_base:
            n = int(git(root, 'rev-list', '--count', f'{merge_base}..HEAD') or 0)
            good = claimed_commits == n
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} 台账标题的 commit 数: 声明 {claimed_commits} / 实测 {n}'
                  f'（分支 {branch}，基线 {merge_base[:7]}）')
        else:
            print(f'  ℹ️  台账标题声明 commit 数 {claimed_commits}（未找到主干分叉点，跳过比对）')
    return bad


def layer4_golines(root, texts, go_root):
    print('=' * 78)
    print('第 4 层 · Go 侧 file:line 出处是否悬空')
    print('=' * 78)
    if not os.path.isdir(go_root):
        print(f'  ⚠️  Go 真源不存在: {go_root} ⇒ 跳过（其余层不受影响）')
        return 0

    gomodcache = os.path.expanduser('~/go/pkg/mod')
    idx = {}
    roots = [(lib, os.path.join(gomodcache, ver)) for lib, ver in THIRD_PARTY.items()]
    roots.append(('真源', os.path.join(go_root, 'service')))
    for lib, base in roots:
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if fn.endswith('.go'):
                    idx.setdefault(fn, []).append((lib, os.path.join(dp, fn)))

    refs = {}
    for f, t in texts.items():
        for m in REF_RE.finditer(t):
            key = (m.group(1), int(m.group(2)), int(m.group(3)) if m.group(3) else int(m.group(2)))
            refs.setdefault(key, set()).add(f)

    ok, problems, ambiguous = 0, [], []
    for (path, s, e), srcs in sorted(refs.items()):
        cands = idx.get(os.path.basename(path), [])
        if not cands:
            problems.append((path, s, e, '文件在真源与全部第三方库里都找不到', sorted(srcs)))
            continue
        matched = []
        for lib, full in cands:
            try:
                n = sum(1 for _ in open(full, encoding='utf-8', errors='replace'))
            except Exception:
                continue
            if s <= n and e <= n:
                matched.append(lib)
        if not matched:
            problems.append((path, s, e, '行号超出文件范围', sorted(srcs)))
            continue
        ok += 1
        if len(set(matched)) > 1:
            ambiguous.append((path, s, e, sorted(set(matched))))

    print(f'  台账里 file:line 引用（去重）{len(refs)} 条')
    print(f'  ✅ 引用不悬空: {ok} 条')
    for path, s, e, why, srcs in problems:
        print(f'    ❌ {path}:{s}{"-" + str(e) if e != s else ""}  ({why})   ← {", ".join(srcs)}')
    if ambiguous:
        print(f'  ⚠️ 同名文件跨库歧义（归属需人工确认）: {len(ambiguous)} 条')
        for path, s, e, libs in ambiguous:
            print(f'      {path}:{s}{"-" + str(e) if e != s else ""}  ∈ {libs}')
    print('  ⚠️ 「不悬空」≠「内容对得上」⇒ 关键断言仍需人工抽验。')
    return len(problems)


def layer5_unmentioned(root, texts):
    print('=' * 78)
    print('第 5 层 · 仓库里存在但台账从未点名的测试类（覆盖缺口）')
    print('=' * 78)
    mentioned = set()
    for t in texts.values():
        for m in CLASS_RE.finditer(t):
            mentioned.add(m.group(1))
    files = [f for f in git(root, 'ls-files', 'service/task').splitlines()
             if f.endswith('.java') and '/src/test/' in f]
    unmentioned = []
    for f in files:
        src = read(os.path.join(root, f))
        m = re.search(r'^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Z][A-Za-z0-9]*)', src, re.M)
        if m and m.group(1) not in mentioned:
            unmentioned.append((m.group(1), f))
    print(f'  仓库内 task 域测试类 {len(files)} 个 ⇒ 台账未点名 {len(unmentioned)} 个')
    for c, f in unmentioned:
        print(f'    ⚠️  {c}')
        print(f'         {f}')
    if not unmentioned:
        print('    ✅ 全部已登记')
    return len(unmentioned)


def layer0_selfcheck(root):
    """复核器**自检**：证明各层判定函数有区分力（否则「全绿」可能只是「什么都没查」）。"""
    print('=' * 78)
    print('第 0 层 · 复核器自检（阳性对照）')
    print('=' * 78)
    bad = 0

    # ① strip_comments 必须能区分「注释里的反面教材」与「代码里的真缺陷」
    sample = '// 反面教材 auto-offset-reset=earliest\nString p = "auto.offset.reset=earliest";'
    code = strip_comments(sample)
    if re.search(r'auto-offset-reset', code):
        print('    ❌ strip_comments 没剥离行注释 ⇒ 禁模式检查会误报')
        bad += 1
    else:
        print('    ✅ strip_comments 剥离了注释里的反面教材')
    if not re.search(r'auto\.offset\.reset', code):
        print('    ❌ strip_comments 把代码也剥掉了 ⇒ 断言检查会全空')
        bad += 1
    else:
        print('    ✅ strip_comments 保留了真实代码')

    # ② 层 1：存在性判定
    if os.path.exists(__file__):
        print('    ✅ 层 1 存在性判定可用（自身文件命中）')
    else:
        print('    ❌ 层 1 存在性判定异常')
        bad += 1

    # ③ 层 3：数字断言的正则必须锚定标题行（否则会误匹配正文里引用的旧值）
    #    ⇒ 判据：**锚定版**要命中标题、**非锚定版**要能命中正文（那正是它要避免的误匹配）
    #    ⇒ 真正要断言的是：锚定版**不**命中正文里的旧值
    anchored = re.compile(r'^#.*共 \*\*(\d+) 个 commit\*\*', re.M)
    body_old = '正文里提到 共 **24 个 commit**'
    if anchored.search('# 标题 共 **31 个 commit**') and not anchored.search(body_old):
        print('    ✅ 层 3 的 commit 数正则已锚定标题行（不会误匹配正文引用的旧值）')
    else:
        print('    ❌ 层 3 的 commit 数正则未锚定 ⇒ 会把「引用旧值」当成「声明旧值」')
        bad += 1

    # ④ 层 4：行号越界必须被判为问题（用一个**越界**样本验证判定函数）
    def line_in_range(path, s, e):
        try:
            n = sum(1 for _ in open(path, encoding='utf-8', errors='replace'))
        except Exception:
            return False
        return s <= n and e <= n
    sample_go = None
    for cand in ('/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/task/rpc/internal/Init/initGoKa.go',
                 os.path.join(root, 'service/task/task-rpc/pom.xml')):
        if os.path.exists(cand) and cand.endswith('.go'):
            sample_go = cand
            break
    if sample_go and not line_in_range(sample_go, 999999, 999999):
        print('    ✅ 层 4 行号越界判定可用（越界样本被判为越界）')
    else:
        print('    ⚠️ 层 4 行号越界判定无法自检（未找到 Go 样本文件）—— 不算失败')

    print(f'  ⇒ 自检{"通过" if not bad else "失败"}')
    return bad


# ====================================================================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--domain', default='task')
    ap.add_argument('--go-root', default=DEFAULT_GO_ROOT)
    ap.add_argument('--repo-root', default=None)
    args = ap.parse_args()

    root = args.repo_root or repo_root()
    print(f'仓库根：{root}')
    print(f'Go 真源：{args.go_root}')
    print()

    texts = ledger_texts(root, args.domain)
    if not texts:
        print(f'❌ 找不到 domain={args.domain} 的台账文件')
        return 1
    print(f'台账文件 {len(texts)} 个：{", ".join(sorted(texts))}')
    print()

    bad = 0
    bad += layer0_selfcheck(root)
    bad += layer1_paths(root, texts)
    bad += layer2_claims(root)
    bad += layer3_numbers(root, texts)
    bad += layer4_golines(root, texts, args.go_root)
    unmentioned = layer5_unmentioned(root, texts)

    print()
    print('=' * 78)
    if bad:
        print(f'❌ 复核结果：{bad} 处不一致（层 0~4）')
    else:
        print('✅ 复核结果：层 0~4 全部通过')
    if unmentioned:
        print(f'⚠️  另有 {unmentioned} 个测试类台账未点名（层 5，不算失败）')
    print('=' * 78)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
