#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""出向 gRPC stub 普查 —— 「出向 stub 类 / 出向 RPC 调用点 / 零 per-call 载体调用点」三个数的**可复算**实现。

【为什么需要这个脚本】
台账曾同时并存「30 处 / 27 处 / 10 处 / 9 处 / 11 类 / 7 类」等多个读数（见
[`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §4.19.54 第四节）。
根因不是「数错了」而是**每次数的口径不同，却都写成「实测数」**。
⇒ 本脚本把口径**写死**（见下）并输出 **TSV**，使任何一个数都能被第三方重跑复算。

【口径（定死 · 2026-10-31 · G4 lane）】
① **出向 stub 类** = 含**真实** `@GrpcClient("...")` 注入的域侧 client 类
   （骨架占位类不算 —— 例如 `article-rpc` 的 `SecurityRpcClient` / `ImageSecurityRpcClient`
     只在注释里写了 `@GrpcClient`，无注入 ⇒ 全仓 23 个 `*RpcClient.java` 里只有 21 个算数）
② **出向 RPC 调用点** = **一条出向语句**（一行）= 一次可能真正发出的出向 RPC
   载体形态共 4 种，全部计入：
     A. `clientVar.provider().rpcMethod(...)`      —— provider 模式
     B. `clientVar.businessMethod(...)`            —— client 类直接暴露业务出口（内部发 RPC）
     C. `localProvider().rpcMethod(...)`           —— 本地/继承的二级 provider（如 follow-api 的 `rpc()`）
     D. `injectedField.rpcMethod(...)`             —— 类内直连注入的 stub 字段
   ⇒ **provider 本身不算调用点**（`rpc()` 只是取 stub，RPC 在其后）
   ⇒ **同一行只算 1 次**（A 路与 C 路可能指向同一条语句 ⇒ 按「文件 + 行」去重）
③ **per-call 载体**分三档（这是台账此前把两种来源混在一起的地方）：
   · **共享层载体** = `GoStyleRequestDeadline.applyTo(...)` / `GoStyleGrpcCallDeadline.apply(...)`
   · **域侧自带 cap** = 域内出现 `withDeadlineAfter(...)` ⇒ **违反共享层判据④**（例外集）
   · **零载体** = 两者皆无 ⇒ 靠共享层 `GoStyleOutboundDeadlineInterceptor` 的组件级
     `cap-millis` / `cap-millis-by-service` 兜底（行为层不缺上界，缺的是**表达层形态**）

【用法】
    cd <仓库根> && python3 docs/DRIFT/common/outbound-stub-census.py
    ⇒ 打印清单 + 三个数；并落 `docs/DRIFT/common/outbound-stub-census.tsv`
"""
import io
import os
import re
import glob
import collections

# 仓库根 = 本脚本上溯两层（docs/DRIFT/common/ → 根）
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
SVC = os.path.join(ROOT, 'service')
# Go 真源（用于取 proto rpc 方法名全集）
GO_ROOT = '/Users/pr/Documents/java&golang/Sea-RideTheWind_golang'


def strip_java(src):
    """剥掉行注释 / 块注释；**保留字符串字面量内容**（`@GrpcClient("user-rpc")` 的字面量是判据的一部分）。"""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                out[i] = ' '
                i += 1
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            while i < n and not (src[i] == '*' and i + 1 < n and src[i + 1] == '/'):
                if src[i] != '\n':
                    out[i] = ' '
                i += 1
            for _ in range(2):
                if i < n:
                    out[i] = ' '
                    i += 1
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == '\\':
                    i += 2
                    continue
                i += 1
            i += 1
        else:
            i += 1
    return ''.join(out)


def jfiles(root):
    for dp, dn, fns in os.walk(root):
        if '/target/' in dp or '/src/test/' in dp:
            continue
        for fn in fns:
            if fn.endswith('.java'):
                yield os.path.join(dp, fn)


def load_rpc_names():
    """proto 的 rpc 方法名全集（Go 真源 + RAG 侧）⇒ Java stub 的 lowerCamel 形态。"""
    names = set()
    for root in (ROOT, GO_ROOT):
        if not os.path.isdir(root):
            continue
        for p in glob.glob(root + '/**/*.proto', recursive=True):
            try:
                txt = io.open(p, encoding='utf-8', errors='replace').read()
            except OSError:
                continue
            for m in re.finditer(r'\brpc\s+([A-Za-z0-9_]+)\s*\(', txt):
                n = m.group(1)
                names.add(n)
                names.add(n[0].lower() + n[1:])
    return names


SHARED_CARRIER = ('GoStyleRequestDeadline.applyTo', 'GoStyleGrpcCallDeadline.apply')
DOMAIN_CAP = ('withDeadlineAfter',)


def carrier_of(body):
    """⇒ 'shared' | 'domain' | 'none'"""
    for pat in SHARED_CARRIER:
        if pat in body:
            return 'shared'
    for pat in DOMAIN_CAP:
        if pat in body:
            return 'domain'
    return 'none'


def main():
    rpc_names = load_rpc_names()
    files = {f: strip_java(io.open(f, encoding='utf-8').read()) for f in jfiles(SVC)}

    # ---- provider 方法名全仓清单（返回 BlockingStub 的无参方法）----
    prov_defs = collections.defaultdict(list)
    for f, code in files.items():
        for pm in re.finditer(
                r'\b(?:public|protected|private)\s+([A-Za-z0-9_.\[\]<> ]*BlockingStub)\s+([A-Za-z0-9_]+)\s*\(\s*\)\s*\{',
                code):
            depth, j = 1, pm.end()
            while j < len(code) and depth > 0:
                if code[j] == '{':
                    depth += 1
                elif code[j] == '}':
                    depth -= 1
                j += 1
            body = code[pm.end():j]
            direct = [m.group(1) for m in re.finditer(r'\.([A-Za-z0-9_]+)\s*\(', body)
                      if m.group(1) in rpc_names]
            prov_defs[pm.group(2)].append((os.path.relpath(f, ROOT), body, bool(direct)))

    # provider 载体沿链继承：rpc() → followRpc() → applyTo
    _cache = {}

    def prov_carrier(name, depth=0):
        if depth > 5:
            return 'none'
        if name in _cache:
            return _cache[name]
        _cache[name] = 'none'          # 环保护
        defs = prov_defs.get(name)
        if not defs:
            return 'none'
        for _, body, _ in defs:
            c = carrier_of(body)
            if c != 'none':
                _cache[name] = c
                return c
        for _, body, _ in defs:
            for mm in re.finditer(r'return\s+[A-Za-z0-9_]+\s*\.\s*([A-Za-z0-9_]+)\s*\(', body):
                c = prov_carrier(mm.group(1), depth + 1)
                if c != 'none':
                    _cache[name] = c
                    return c
        return 'none'

    uniq_prov_names = {n for n, d in prov_defs.items() if len(set(x[0] for x in d)) == 1}

    # ---- client 类表 ----
    GRPC_RE = re.compile(r'@GrpcClient\s*\(\s*"([^"]+)"\s*\)')
    clients = []
    for f, code in files.items():
        if not GRPC_RE.search(code):
            continue
        rel = os.path.relpath(f, ROOT)
        pkg = re.search(r'^package\s+([\w.]+);', code, re.M).group(1)
        cls = os.path.basename(f)[:-5]
        fields = []
        for m in GRPC_RE.finditer(code):
            fm = re.search(r'[A-Za-z0-9_.]*BlockingStub\s+([A-Za-z0-9_]+)\s*[;=,)]',
                           code[m.end():m.end() + 260])
            if fm:
                fields.append(fm.group(1))
        if not fields:
            cm = re.search(r'@GrpcClient\s*\(\s*"[^"]+"\s*\)\s*(?:final\s+)?[A-Za-z0-9_.]*BlockingStub\s+([A-Za-z0-9_]+)',
                           code)
            if cm:
                fields.append(cm.group(1))
        methods = {}
        for pm in re.finditer(r'\bpublic\s+([A-Za-z0-9_.\[\]<> ]+?)\s+([A-Za-z0-9_]+)\s*\(([^)]*)\)\s*\{', code):
            rtype, name = pm.group(1).strip(), pm.group(2)
            depth, j = 1, pm.end()
            while j < len(code) and depth > 0:
                if code[j] == '{':
                    depth += 1
                elif code[j] == '}':
                    depth -= 1
                j += 1
            body = code[pm.end():j]
            methods[name] = dict(rtype=rtype, body=body, carrier=carrier_of(body),
                                 direct_rpc=[m.group(1) for m in re.finditer(r'\.([A-Za-z0-9_]+)\s*\(', body)
                                             if m.group(1) in rpc_names],
                                 is_provider='BlockingStub' in rtype)
        clients.append(dict(pkg=pkg, cls=cls, path=rel, comp=rel.split('/src/main/')[0],
                            fields=fields, methods=methods, code=code))

    for c in clients:
        for key, mi in c['methods'].items():
            mi['indirect'] = [n for n in c['methods']
                              if n != key and re.search(r'\b' + re.escape(n) + r'\s*\(', mi['body'])]
            mi['outlet'] = bool(mi['direct_rpc']) or bool(mi['indirect'])

    def eff(c, name, depth=0):
        mi = c['methods'].get(name)
        if mi is None or depth > 5:
            return 'none'
        if mi['direct_rpc']:
            return mi['carrier']
        for n in mi['indirect']:
            sub = c['methods'].get(n)
            if sub and sub['direct_rpc']:
                return sub['carrier']
        return mi['carrier']

    for c in clients:
        for key, mi in c['methods'].items():
            mi['eff'] = eff(c, key)

    # ---- 扫调用点（四条形态）----
    rows = []
    for f, code in files.items():
        rel = os.path.relpath(f, ROOT)
        comp = rel.split('/src/main/')[0]
        # C：本地 / 继承 provider
        for name in uniq_prov_names:
            car = prov_carrier(name)
            for m in re.finditer(r'\b' + re.escape(name) + r'\s*\(\s*\)\s*\.\s*([A-Za-z0-9_]+)\s*\(', code):
                if m.group(1) not in rpc_names:
                    continue
                rows.append((comp, rel, code[:m.start()].count('\n') + 1,
                             '%s().%s()' % (name, m.group(1)), car, 'C-本地/继承provider'))
        # A1：类内直连注入字段
        for c in clients:
            if os.path.abspath(f) != os.path.abspath(os.path.join(ROOT, c['path'])):
                continue
            for fname in c['fields']:
                for m in re.finditer(r'\b' + re.escape(fname) + r'\s*\.\s*([A-Za-z0-9_]+)\s*\(', c['code']):
                    if m.group(1) in rpc_names:
                        rows.append((comp, c['path'], c['code'][:m.start()].count('\n') + 1,
                                     '%s.%s()' % (fname, m.group(1)), 'none', 'A1-类内直连'))
        # A2 / B1：clientVar.provider() / clientVar.业务出口()
        for c in clients:
            imp = re.compile(r'import\s+' + re.escape(c['pkg']) + r'\.' + re.escape(c['cls']) + r'\s*;')
            if not imp.search(code):
                continue
            vars_ = set(re.findall(re.escape(c['cls']) + r'\s+([A-Za-z0-9_]+)\s*[;,)]', code))
            for v in vars_:
                for mname, mi in c['methods'].items():
                    if not (mi['outlet'] or mi['is_provider']):
                        continue
                    for m in re.finditer(r'\b' + re.escape(v) + r'\s*\.\s*' + re.escape(mname) + r'\s*\(', code):
                        line = code[:m.start()].count('\n') + 1
                        if mi['outlet'] and not mi['is_provider']:
                            rows.append((comp, rel, line, '%s.%s()' % (v, mname),
                                         mi['eff'], 'B1-直调出向出口'))
                        else:
                            tail = code[m.end():m.end() + 240]
                            mr = re.match(r'\s*\)\s*\.\s*([A-Za-z0-9_]+)\s*\(', tail)
                            if mr and mr.group(1) in rpc_names:
                                rows.append((comp, rel, line,
                                             '%s.%s().%s()' % (v, mname, mr.group(1)),
                                             mi['eff'], 'A2-provider→RPC'))

    # 去重：一行 = 一条出向语句；同一条里取「最具体」的载体判定
    rank = {'none': 0, 'shared': 1, 'domain': 1}
    best = {}
    for r in rows:
        k = (r[1], r[2])
        prev = best.get(k)
        if prev is None or rank[r[4]] > rank[prev[4]]:
            best[k] = r
    rows = sorted(best.values(), key=lambda x: (x[0], x[1], x[2]))

    # ---- 输出 ----
    tsv = os.path.join(HERE, 'outbound-stub-census.tsv')
    with io.open(tsv, 'w', encoding='utf-8') as tf:
        tf.write('comp\trel\tline\toutlet\tcarrier\tkind\n')
        for r in rows:
            tf.write('%s\t%s\t%d\t%s\t%s\t%s\n' % (r[0], r[1], r[2], r[3], r[4], r[5]))

    cnt = collections.Counter(r[4] for r in rows)
    print('① 出向 stub 类                = %d' % len(clients))
    print('② 出向 RPC 调用点              = %d' % len(rows))
    print('③ 载体：共享层 %d · 域侧自带cap %d · 零载体 %d'
          % (cnt['shared'], cnt['domain'], cnt['none']))
    print('   ⇒【零 per-call 载体】= %d' % cnt['none'])
    print('清单已落：%s' % os.path.relpath(tsv, ROOT))

    agg = collections.defaultdict(collections.Counter)
    for r in rows:
        agg[r[0]]['t'] += 1
        agg[r[0]][r[4]] += 1
    print()
    print('%-40s %6s %6s %8s %6s' % ('组件', '调用点', '共享层', '域侧cap', '零载体'))
    for comp in sorted(agg):
        a = agg[comp]
        print('  %-38s %6d %6d %8d %6d' % (comp, a['t'], a['shared'], a['domain'], a['none']))


if __name__ == '__main__':
    main()
