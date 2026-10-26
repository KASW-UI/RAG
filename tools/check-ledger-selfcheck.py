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
    # ⚠️ users 域（2026-10-24 合并 users-user 版时并入 —— 原 users 版硬编码这 6 份，
    #    现统一走 --domain 参数，与 task 域同一条路径、同一个退出码语义）。
    'users': [
        'docs/DRIFT/users/user/api.md',
        'docs/DRIFT/users/user/rpc.md',
        'docs/DRIFT/users/_shared.md',
    ],
    'users-admin': [
        'docs/DRIFT/users/admin/api.md',
        'docs/DRIFT/users/admin/rpc.md',
        'docs/DRIFT/users/admin/_shared.md',
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

def repo_java_files(root, domain_dir):
    """列出**工作区**里的 Java 文件（**已跟踪 + 未跟踪**）。

    ⚠️ 只用 `git ls-files` ⇒ **新加的、还没 `git add` 的文件完全看不见**
    ⇒ 「台账 vs 实际」比的是 **git 索引**，不是**工作区**
    ⇒ 刚写完的测试类/生产类在提交前**根本不会被覆盖检查看到**（本轮实测踩到：
    新加的 `TaskGrpcInterceptorWiringTest` 当时没被第 5 层数进去）
    ⇒ ⇒ 必须把**未跟踪文件**也算进来。
    """
    tracked = git(root, 'ls-files', domain_dir).splitlines()
    untracked = git(root, 'ls-files', '--others', '--exclude-standard', domain_dir).splitlines()
    seen, out = set(), []
    for f in tracked + untracked:
        if f not in seen:
            seen.add(f)
            out.append(f)
    return out


def _repo_files(root):
    """列出仓内**已跟踪 + 未跟踪**的文件（工作区视角，与第 5/6 层一致）。"""
    tracked = git(root, 'ls-files').splitlines()
    untracked = git(root, 'ls-files', '--others', '--exclude-standard').splitlines()
    seen, out = set(), []
    for f in tracked + untracked:
        if f not in seen:
            seen.add(f)
            out.append(f)
    return out


def _go_service_files(go_root):
    """列出 **Go 真源仓** `service/` 下的相对路径（用于识别「Go 侧路径引用」）。"""
    out = set()
    base = os.path.join(go_root, 'service')
    if not os.path.isdir(base):
        return out
    for dp, _, fns in os.walk(base):
        for fn in fns:
            out.add(os.path.relpath(os.path.join(dp, fn), go_root))
    return out


def _mk_index(repo_files, go_files=()):
    """建「路径可解析性」索引。

    - `repo_files` : 本仓（已跟踪 + 未跟踪）文件
    - `go_files`   : **Go 真源仓**里 `service/` 下的相对路径
    - `top_dirs`   : 本仓 `service/` 下的**顶层目录**（`service/<X>/`）
    """
    top_dirs = set()
    for f in repo_files:
        segs = f.split('/')
        if segs[0] == 'service' and len(segs) >= 2:
            top_dirs.add(segs[1])
    return {'repo_files': list(repo_files), 'go_files': set(go_files), 'top_dirs': top_dirs}


# N13 · 台账里**对该文件的状态叙述**（已删除 / 不存在 / 0 字节占位 …）
#     ⇒ 这类引用是**历史记述**，不是「指向一个缺失文件的悬空引用」
N13_NARRATION = re.compile(
    r'(删除|已删除|不存在|已移除|被删|不再存在|不建|未建|不创建|0\s*字节|错放|空文件|占位)')


def _narration_in_lines(lines, ln, window=2, path=None):
    """引用所在行**及邻行**（±window）里有没有「**该文件**的状态叙述」。

    ⚠️ **为什么要邻行而不是同行**：台账常把叙述放在**上一行**再列路径，例如
        `docs/DRIFT/_cross/human-only-status.md:16-18` ——
        第 16 行写「以下 3 条**被登记为已存在、实际不存在**」，
        第 17~18 行才列三个 `HUMAN_ONLY.MD` 路径 ⇒ 只看同行会漏判。

    🔴 **但邻行窗口会「张冠李戴」**（本轮实测踩到）：邻行里若有一条**别的**叙述
    （比如上一行在讲「某个文件已删除」），就会被误当成**本引用**的叙述
    ⇒ ⇒ **把真实缺陷吃掉**。实测：global 台账 E43 行引用
    `tools/check-ddl-static.py`（真实缺陷），而**上一行 E42** 含「删除」二字
    ⇒ 该真实缺陷被静默 ⇒ ⇒ **必须要求「叙述指向同一个文件」**。

    ⇒ **归属判定**（本轮实测踩出来的，见下）：
      **A · 同行**：叙述词与本路径在**同一行**，且**该行的第一个路径引用就是本路径**
        ⇒ 命中。⚠️ 「必须是第一个」是本轮实测逼出来的：台账的**汇总行**会把
        **多个互不相干的事实**挤进一行（例如 global 时间线行里既有
        「users-domain.md 临时档已删除」又有「like 域的 check-ddl-static.py 路径写错」）
        ⇒ 若只判「同行含该路径」，那条**真实缺陷**会被别人的「删除」二字吃掉。
      **B · 邻行**：窗口内存在一行**完全不含任何路径引用**的**纯叙述行**
        ⇒ 命中（台账常写「以下 3 条实际不存在：」再在下一行列路径）。

    `path=None` 时退化为「只看有没有叙述词」（供不需要区分归属的场合使用）。

    返回 (是否命中, 命中的行号（1-based））。
    """
    if not lines:
        return False, 0
    for i in range(max(0, ln - window), min(len(lines), ln + window + 1)):
        line = lines[i]
        if not N13_NARRATION.search(line):
            continue
        if path is None:
            return True, i + 1
        if i == ln:
            # A · 同行 ⇒ 该行的**第一个**路径引用必须就是本路径
            others = PATH_RE.findall(line)
            if others and others[0] == path:
                return True, i + 1
            continue
        # B · 邻行 ⇒ 必须是**不含任何路径**的纯叙述行
        if not PATH_RE.findall(line):
            return True, i + 1
    return False, 0


def _has_contiguous_subseq(hay, needle):
    """`hay` 的段序列里是否**连续相邻**地包含 `needle` 段序列。"""
    n = len(needle)
    if n == 0:
        return True
    for i in range(len(hay) - n + 1):
        if hay[i:i + n] == needle:
            return True
    return False


def _is_simplified_path(p, idx):
    """判断一条「仓内路径引用」是不是**台账里的简写** ⇒ 不该判 MISSING。

    ⚠️ **第 1 层此前完全没有去噪**（其它层有 255+ 条去噪）
    ⇒ 而台账里大量用**省略写法**指代文件 ⇒ 全被误判成「引用了不存在的文件」
    ⇒ ⇒ 工具的信号被噪声淹没（users-user 域实测：60 处「不一致」里 59 处是这类）。

    返回 **(是否去噪, 规则编号, 原因)**；不去噪则 (False, '', '')。
    ⇒ **必须带规则编号** —— 否则两条规则**互相掩护**时，去掉其中一条**抓不到**
      （本轮实测：N9 被 N12 掩护 ⇒ 「抽掉 N9」这个变异首跑是绿的 ⇒ 变异无效）。

    **N8 · 省略写法** —— 路径里含 `...`（省略中间目录）：
        `service/common/.../spring/HttpMiddlewareFilter.java`
        `service/users/user/user-api/.../config/WebMvcConfig.java`
      ⇒ 台账故意省略中间层级（读者按前缀也能定位）⇒ 不是精确路径引用
      ⇒ ⚠️ `PATH_RE` 的字符类 `[A-Za-z0-9_./-]` **含 `.`** ⇒ `...` 会被完整匹配出来
        ⇒ 不去噪就必然判 MISSING。

    **N9 · 裸文件名** —— 只有 `service/<Name>.java` 两段（没有模块目录）：
        `service/UserRegisterService.java`
        `service/UserLoginService.java`
      ⇒ 台账只写了「域根目录 + 文件名」⇒ 不是精确路径引用
      ⇒ ⚠️ **只针对 `service/`**：真实路径至少是 `service/<域>/<模块>/…`
        ⇒ 而 `docs/xxx.md` / `tools/x/y.sh` 的两段式是**真实路径** ⇒ 不去噪。

    **N10 · 短路径** —— 省略了 `src/main/java/com/rag/<pkg>/` 之类中间前缀：
        `service/common/observability/RpcInterceptor.java`
          → 真实 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
        `service/article/article-rpc/application.yml`
          → 真实 `service/article/article-rpc/src/main/resources/application.yml`
      ⇒ 判定方式**不看形状、只问「能不能落到某个真实文件上」**：
        ① 取 p 的**中段**（第 3 段起、到倒数第 2 段止；两段式为空）；
        ② 在仓内找**basename 相同**、且路径段序列**连续相邻**包含该中段的文件；
        ③ 命中 ⇒ 判定为简写（并打印命中的真实文件，供人工复核）。
      ⇒ **中段过滤是关键**：没有它，`service/users/users-common/HUMAN_ONLY.MD`
        会因「仓内另有 4 份 HUMAN_ONLY.MD」而被误去噪 —— 而那 4 份**都不在
        users-common 下** ⇒ 该文件**确实不存在** ⇒ 是真实悬空引用，必须报出来 ✓

    **N11 · Go 侧路径** —— 该路径在 **Go 真源仓**里存在：
        `service/comment/rpc/internal/model/test-community-postgres.sh`
      ⇒ 这是**引用 Go 真源**（Go 的目录布局是 `service/<域>/rpc/internal/…`，
        RAG 是 `service/<域>/<模块>/src/main/java/…` ⇒ 两者不撞）
      ⇒ ⇒ 它**本来就不该**在本仓存在 ⇒ 不该判 MISSING。

    **N12 · 顶层目录不在本仓** —— `service/<X>/` 这个**顶层目录在本仓压根不存在**：
        `service/demo/demo-rpc/pom.xml`（`service/demo/` **从未进过版本库**，
        那是 L15 lane 的**合成 fixture** `/tmp/l15-fixture` 里的假模块）
      ⇒ **逻辑上它不可能是本仓任何形态的路径**（短路径 / 省略写法的顶层目录必须存在）
      ⇒ ⇒ 它指向**仓外**（合成 fixture / 外部仓 / 历史快照）⇒ 不该判 MISSING。

    ⚠️ **本规则不许把「真实缺失」也吃掉**（反例均已进第 0 层自检作阴性对照）：
        ① `tools/difftest/check-protection-layer.sh` —— 脚本**在仓库历史里从未存在过**
           ⇒ 非 `service/` 前缀 ⇒ 不匹配任何规则 ⇒ 仍然报 MISSING ✓
        ② `docs/DRIFT/global/_INDEX.md` —— 真实路径是 `docs/DRIFT/_INDEX.md`
           ⇒ 非 `service/` 前缀 ⇒ 仍报 MISSING ✓（这是台账写错目录，不是简写）
        ③ `service/users/users-common/HUMAN_ONLY.MD` —— 中段 `users-common`
           在任何 HUMAN_ONLY.MD 的路径里都**不出现** ⇒ 仍报 MISSING ✓
        ④ `service/common/src/main/java/com/rag/common/observability/LikeRpcClient.java`
           —— basename 相同的那份在 **like 域**，中段含 `observability` ⇒ 对不上
           ⇒ 仍报 MISSING ✓（真·跨域错引）
    """
    if '...' in p:
        return True, 'N8', 'N8 省略写法（`...` 省略中间目录），非精确路径引用'
    if p.startswith('service/') and p.count('/') == 1 and p.endswith('.java'):
        return True, 'N9', 'N9 裸文件名（只写了域根目录 + 文件名），非精确路径引用'

    # N11 · Go 侧路径（存在性以 **Go 真源仓** 为准）
    if p in idx['go_files']:
        return True, 'N11', 'N11 Go 侧路径（在 Go 真源仓存在 ⇒ 本仓本就不该有）'

    # N12 · 顶层目录不在本仓 ⇒ 必是仓外引用
    if p.startswith('service/'):
        segs = p.split('/')
        if len(segs) >= 2 and segs[1] not in idx['top_dirs']:
            return True, 'N12', (f'N12 仓外引用（本仓无 `service/{segs[1]}/` 这个顶层目录 ⇒ '
                                 f'不可能是本仓任何形态的路径）')

    # N10 · 短路径（只在 `service/` 下；`docs/` 与 `tools/` 的两段/三段式一律**不**去噪）
    if p.startswith('service/') and p.count('/') >= 2:
        segs = p.split('/')
        middle = segs[2:-1]
        base = segs[-1]
        hits = []
        for f in idx['repo_files']:
            if os.path.basename(f) != base:
                continue
            if _has_contiguous_subseq(f.split('/'), middle):
                hits.append(f)
        if hits:
            shown = ', '.join(sorted(hits)[:3])
            more = f' 等 {len(hits)} 处' if len(hits) > 3 else ''
            return True, 'N10', f'N10 短路径（省略中间层级）⇒ 落到 {shown}{more}'

    return False, '', ''


def layer1_paths(root, texts, go_root=DEFAULT_GO_ROOT):
    print('=' * 78)
    print('第 1 层 · 台账引用的仓库内路径是否存在')
    print('=' * 78)
    refs = {}
    for f, t in texts.items():
        for m in PATH_RE.finditer(t):
            refs.setdefault(m.group(0), set()).add(f)
    # 记下每个引用的**所在行**（N13 需要「邻行叙述」）
    hits = {}
    for f, t in texts.items():
        for m in PATH_RE.finditer(t):
            hits.setdefault(m.group(0), []).append((f, t.count('\n', 0, m.start())))
    idx = _mk_index(_repo_files(root), _go_service_files(go_root))
    missing, denoised = [], []
    for p, srcs in refs.items():
        if os.path.exists(os.path.join(root, p)):
            continue
        is_simple, _rule, why = _is_simplified_path(p, idx)
        if not is_simple:
            # N13 · 台账里对**该文件的状态叙述**（已删除 / 不存在 / 0 字节占位 …）
            #      ⇒ 历史记述，不是悬空引用
            narr_src, narr_line = '', 0
            for sf, ln in hits.get(p, []):
                lines = read(os.path.join(root, sf)).splitlines()
                ok, at = _narration_in_lines(lines, ln, path=p)
                if ok:
                    narr_src, narr_line = sf, at
                    break
            if narr_src:
                is_simple = True
                why = (f'N13 历史记述（同行/邻行有「状态叙述」⇒ 已记录该文件的状态：'
                       f'{narr_src}:{narr_line}）')
        if is_simple:
            denoised.append((p, sorted(srcs), why))
        else:
            missing.append((p, sorted(srcs)))
    print(f'  台账引用路径 {len(refs)} 个 ⇒ 缺失 {len(missing)} 个')
    if denoised:
        print(f'  🔇 已去噪（不计 FAIL）: {len(denoised)} 条')
        for p, srcs, why in denoised:
            print(f'     · {p}   ({why})')
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


# --------------------------------------------------------------------
# commit 清单的「一一对应」检查（**纯函数** ⇒ 第 0 层自检可以直接验证它有区分力）
# --------------------------------------------------------------------
# ⚠️ 为什么不能只做「集合覆盖」（"每个 commit 都在台账里出现过"）：
#    那条不变式**看不出**下面三类问题 ——
#    ① 台账里写的是**占位符**（`（本轮）`）而不是哈希 ⇒ 它既不算「已登记」也不算「漏登记」
#       ⇒ **完全逃过**集合覆盖检查（2026-10-24 实测：8 行占位符一路绿灯，直到第三次复核才被抓出来）
#    ② **同一个 commit 被写成多行** ⇒ 读者会把 1 个 commit 误读成 N 个
#       （2026-10-24 实测：`c73d9cf` 3 行、`c52cbea` 2 行、`c886406` 4 行）
#    ③ 登记**行数**与 commit **个数**不守恒 ⇒ 多写行 / 少写行都看不出来
#    ⇒ 判据从「集合覆盖」升级为「**一一对应**」。
HASH_RE = re.compile(r'^[0-9a-f]{7,40}$')
PLACEHOLDER = '（本轮）'


def _first_cell(line):
    return line.strip().strip('|').split('|')[0].strip()


def _is_sep(line):
    return set(line.replace('|', '').replace('-', '').replace(' ', '')) <= {''}


def commit_manifest_rows(text):
    """抽出 **commit 清单表** 的数据行（首列）。

    ⚠️ **只认「表头首列 == commit」的表** —— `_README.md` 里还有开放项表 / 共享工具表 /
    合并冲突表，它们的首列同样是反引号包的东西；混进来会让「登记表」和「普通表」混淆
    ⇒ **判据的粒度就错了**（本轮实测踩到：普通表首列被当成登记项）。

    ⚠️ **表格的边界必须靠「分隔行」切，不能靠空行** —— markdown 允许两张表**紧挨着**
    （中间没有空行）；按空行切会把它们并成一张 ⇒ 后面那张表的行会混进清单。
    """
    lines = text.split('\n')
    seps = [i for i, l in enumerate(lines)
            if l.startswith('|') and _is_sep(l)]
    rows = []
    for idx, j in enumerate(seps):
        if j == 0 or not lines[j - 1].startswith('|'):
            continue
        if _first_cell(lines[j - 1]).strip('`').lower() != 'commit':
            continue
        # 本表正文到「下一张表的表头」为止（表头在下一条分隔行的前一行）
        end = (seps[idx + 1] - 1) if idx + 1 < len(seps) else len(lines)
        for line in lines[j + 1:end]:
            if line.startswith('|') and not _is_sep(line):
                rows.append(_first_cell(line).strip().strip('`'))
    return rows


def check_commit_manifest(hashes, head, rows):
    """台账 commit 清单 vs 分支 commit 列表。

    返回 (problems, notes)：problems = 人类可读的缺陷描述（空 ⇒ 通过）。
    ⚠️ 纯函数（不碰磁盘）⇒ 可以用**人造样本**验证它有区分力（第 0 层自检）。
    """
    problems, notes = [], []
    hash_rows = [r for r in rows if HASH_RE.match(r)]
    ph_rows = [r for r in rows if PLACEHOLDER in r]
    # ⚠️ 一律按 **7 位前缀**比较：台账里可能写 7 位、git 也可能给 7 位，长度不一致会误判
    keys = [r[:7] for r in hash_rows]
    dedup = set(keys)
    hashes7 = {h[:7] for h in hashes}
    head7 = head[:7]

    # ① 占位符残留：只允许 1 个（那是「刚写完、还没提交」的这一轮），且必须在清单末尾
    if len(ph_rows) > 1:
        problems.append(f'commit 清单里有 **{len(ph_rows)} 个占位符行**（只允许 1 个）'
                        f'⇒ 读者无法知道它们各自对应哪个 commit')
    if len(ph_rows) == 1 and rows and rows[-1] != ph_rows[0]:
        problems.append('占位符行**不在 commit 清单的末尾** ⇒ 它不是「本轮」那一行')
    if not ph_rows:
        notes.append('没有占位符行（全部用真实哈希登记）')

    # ② 同一个 commit 被写成多行
    for h in sorted({k for k in keys if keys.count(k) > 1}):
        problems.append(f'commit `{h}` 被登记了 **{keys.count(h)} 行**'
                        f' ⇒ 读者会把 1 个 commit 误读成 {keys.count(h)} 个')

    # ③ 漏登记：只允许 HEAD 那一个未登记（它此刻还没有哈希）
    missing = sorted(hashes7 - dedup)
    for h in missing:
        if h != head7:
            problems.append(f'commit `{h}` **完全没登记**（既不在清单里，也不是 HEAD）')

    # ④ 登记了不在本分支上的 commit（陈旧 / 串分支的登记）
    for h in sorted(dedup - hashes7):
        problems.append(f'commit 清单里登记了 `{h}`，但它**不在本分支上** ⇒ 陈旧登记')

    # ⑤ 守恒：登记行数（去重哈希 + 占位）必须与 commit 数对上
    #    提交后：HEAD 还没登记 ⇒ 行数 == commit 数
    #    提交前：HEAD 已登记 + 1 个占位 ⇒ 行数 == commit 数 + 1
    total_rows = len(dedup) + len(ph_rows)
    expected = len(hashes7) + (1 if head7 in dedup else 0)
    if total_rows != expected:
        problems.append(f'commit 清单**行数不守恒**：登记 {total_rows} 行'
                        f'（去重哈希 {len(dedup)} + 占位 {len(ph_rows)}），'
                        f'但分支有 {len(hashes7)} 个 commit ⇒ 期望 {expected} 行')

    if head7 in dedup:
        state = (f'提交前（HEAD 已登记 ⇒ 本次提交将产生第 {len(hashes7) + 1} 个哈希）')
    else:
        state = '提交后（HEAD 尚未登记 ⇒ 等下一次补登）'
    notes.append(f'分支 {len(hashes7)} 个 commit / 登记 {total_rows} 行'
                 f'（去重哈希 {len(dedup)} + 占位 {len(ph_rows)}）· '
                 f'状态：{state}')
    return problems, notes


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

    # ⚠️ **api 域要单独查** —— §〇 的「已修 N 条」与 §一 的实际条数必须对得上
    #    （本轮实测抓到过：api.md §〇 写「已修 3 条」而 §一 标题写「已修（2 条）」，
    #      而 §一 下面实际有 4 条 ⇒ **标题与内容不符**）
    api_md = read(os.path.join(root, 'docs/DRIFT/task/api.md'))
    if api_md:
        m_fixed = re.search(r'已修 \| \*\*(\d+) 条\*\*（DRIFT-TASK-API', api_md)
        # ⚠️ 标题的措辞会变（「已修（N 条）」/「逐条对照（N 条：…）」）
        #    ⇒ 只抓「## 一、…（N 条」里的那个 N，**不要**锚定具体措辞
        m_sec = re.search(r'^## 一、[^\n（]*（(\d+) 条', api_md, re.M)
        m_items = len(re.findall(r'^### .*DRIFT-TASK-API-\d{3}', api_md, re.M))
        if m_sec and m_items:
            good = int(m_sec.group(1)) == m_items
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} api.md §一 标题条数: 声明 {m_sec.group(1)} / 实际小节 {m_items}')
        elif m_items:
            print(f'  ⚠️ api.md §一 标题里没解析出条数（实际小节 {m_items} 个）⇒ 标题措辞可能又变了')
        if m_fixed:
            ids_api = sorted(set(int(x) for x in re.findall(r'DRIFT-TASK-API-(\d{3})', api_md)))
            if ids_api:
                good = ids_api == list(range(1, len(ids_api) + 1))
                bad += 0 if good else 1
                print(f'  {"✅" if good else "❌"} DRIFT-TASK-API 编号连续: {ids_api}')

    # ── 三档台账的**编号连续性**与**悬空引用**（「最全」维度）──
    #    ⚠️ 这两项查的是「台账之间**互相引得到**」⇒ 引用一个不存在的编号 = 读者点进去是空的
    all_text = '\n'.join(texts.values())
    pit_text = read(os.path.join(root, 'docs/Pitfalls/task/rpc.md'))
    if pit_text:
        # ⚠️ Pitfall 的编号是**两位**（`PITFALL-TASK-RPC-01`），不是 DRIFT 的三位
        #    ⇒ 正则用 `\d{2,3}`，且「已定义」直接取**文件里出现过的字符串**
        #    （不按序号重建 —— 重建会在位数不一致时错判）
        ids = sorted(set(int(x) for x in re.findall(r'PITFALL-TASK-RPC-(\d{2,3})', pit_text)))
        good = bool(ids) and ids == list(range(1, len(ids) + 1))
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} PITFALL-TASK-RPC 编号连续: '
              f'{ids[0]}~{ids[-1]}（{len(ids)} 条）')
        defined = set(re.findall(r'PITFALL-TASK-RPC-\d{2,3}', pit_text))
        refs = set(re.findall(r'PITFALL-TASK-RPC-\d{2,3}', all_text))
        dangling = sorted(refs - defined)
        if dangling:
            bad += 1
            print(f'  ❌ 台账引用了**不存在**的 Pitfall: {dangling}')
        else:
            print(f'  ✅ 台账引用的 {len(refs)} 个 Pitfall ID 全部有定义')
    sd_text = read(os.path.join(root, 'docs/Intentional_Deviations/task/rpc.md'))
    if sd_text:
        defined = set(re.findall(r'SD-\d{4}-\d{2}-\d{2}-TASK-\d{2}', sd_text))
        refs = set(re.findall(r'SD-\d{4}-\d{2}-\d{2}-TASK-\d{2}', all_text))
        dangling = sorted(refs - defined)
        if dangling:
            bad += 1
            print(f'  ❌ 台账引用了**不存在**的签字偏离: {dangling}')
        else:
            print(f'  ✅ 台账引用的 {len(refs)} 个签字偏离 ID 全部有定义'
                  f'（偏离档共定义 {len(defined)} 条）')

    for name, r in (('task-rpc', rpc), ('task-api', api)):
        if r:
            good = r['failures'] == 0 and r['errors'] == 0 and r['skipped'] == 0
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} {name} failures/errors/skipped 全 0')
            if not good:
                print(f'       ⚠️ skipped={r["skipped"]} ⇒ 「全绿」可能意味着「没跑」')

    # commit 清单的「**一一对应**」检查（不是比对写死的计数 —— 那个必然自指滞后）
    #   不变式：台账 commit 清单的**每一行**对应**恰好一个**分支 commit
    #   ⇒ 漏登记 / 占位符残留 / 同一个 commit 多行 / 行数不守恒 —— 四类都立刻被抓到
    branch = git(root, 'rev-parse', '--abbrev-ref', 'HEAD')
    merge_base = git(root, 'merge-base', 'HEAD', 'do-my-own-gh')
    if merge_base:
        hashes = git(root, 'rev-list', '--abbrev-commit', '--no-merges',
                     f'{merge_base}..HEAD').splitlines()
        hashes = [h.strip() for h in hashes if h.strip()]
        head = git(root, 'rev-parse', '--short=7', 'HEAD')
        readme = read(os.path.join(root, 'docs/DRIFT/task/_README.md'))
        rows = commit_manifest_rows(readme)
        problems, notes = check_commit_manifest(hashes, head, rows)
        for n in notes:
            print(f'  ℹ️  {n}')
        if problems:
            bad += len(problems)
            print(f'  ❌ commit 清单有 {len(problems)} 处缺陷'
                  f'（分支 {branch}，共 {len(hashes)} 个 commit）：')
            for p in problems:
                print(f'      · {p}')
        else:
            print(f'  ✅ commit 清单与分支**一一对应**'
                  f'（{len(hashes)} 个 commit = 登记 {len(rows)} 行）')
    else:
        print('  ⚠️  未找到主干分叉点 ⇒ 跳过 commit 覆盖检查')
    return bad


# ──────────────────────────────────────────────────────────────────────────
# Go 侧出处的**精确定位**通道（2026-10-26 第三十四轮 · favorite 域实测带出）
# ──────────────────────────────────────────────────────────────────────────

def _modcache_escape(mod):
    """Go module cache 的**路径转义**：大写字母 → `!` + 小写。

    ⚠️ 不转义就找不到带大写字母的模块 —— 实测 `github.com/IBM/sarama` 的真实路径是
    `github.com/!i!b!m/sarama` ⇒ ⇒ **手写表里带大写的条目从来没被索引到**（静默漏检）。
    """
    return re.sub(r'[A-Z]', lambda m: '!' + m.group(0).lower(), mod)


def _go_mod_requires(go_root):
    """从真源 `go.mod` 解析 (module, version) 列表。

    ⚠️ **为什么权威必须是 `go.mod` 而不是手写表**：手写表只覆盖写表时想到的模块
    ⇒ favorite 域实测：`gorm.io/gorm@v1.31.1/schema/naming.go` 与
    `prometheus/client_golang` 都不在表里 ⇒ 被判「文件在真源与全部第三方库里都找不到」
    ⇒ **假 FAIL** ⇒ ⇒ 权威取真源自己的 `go.mod`（`// indirect` 的也要）。
    """
    text = read(os.path.join(go_root, 'go.mod'))
    out = []
    for m in re.finditer(r'^\s*([a-zA-Z0-9._~/-]+\.[a-zA-Z0-9._~/-]+)\s+(v[^\s/]+)',
                         text, re.M):
        out.append((m.group(1), m.group(2)))
    return out


def _detect_goroot():
    """探测 Go **标准库**的 GOROOT。

    ⚠️ favorite 域实测：`net/http/server.go:2458`、`src/net/url/url.go:1001` 这类引用
    在 `<GOROOT>/src` 里 ⇒ 索引不含标准库 ⇒ **假 FAIL**。
    """
    cands = []
    env = os.environ.get('GOROOT')
    if env:
        cands.append(env)
    cands += sorted(glob.glob(os.path.expanduser('~/sdk/go*')), reverse=True)
    cands += ['/usr/local/go', '/opt/homebrew/opt/go/libexec', '/usr/lib/go']
    for c in cands:
        if c and os.path.isdir(os.path.join(c, 'src')):
            return c
    return None


def _direct_module_candidate(path, gomodcache):
    """`module@version/...` 前缀 ⇒ **直接在 module cache 里找**（最精确）。

    ⚠️ 为什么要这条通道：层 4 的索引**按 basename** 建（`idx[basename]`）
    ⇒ 写成完整模块路径（`gorm.io/gorm@v1.31.1/schema/naming.go`）反而**解析不到**
    （basename 是 `naming.go`，而索引里没有这个模块）⇒ 假 FAIL。
    ⇒ 带版本前缀 ⇒ 路径**唯一** ⇒ 直接拼出来即可 ⇒ 顺带**消除跨库同名歧义**。
    """
    m = re.match(r'^([a-zA-Z0-9._~/-]+\.[a-zA-Z0-9._~/-]+)@(v[^\s/]+)/(.+)$', path)
    if not m:
        return None
    mod, ver, rest = m.groups()
    return os.path.join(gomodcache, _modcache_escape(mod) + '@' + ver, rest)


def _stdlib_candidate(path, goroot):
    """Go **标准库**的引用 ⇒ `<GOROOT>/src/<path>`（带 `src/` 前缀的写法也认）。"""
    if not goroot:
        return None
    base = path[4:] if path.startswith('src/') else path
    return os.path.join(goroot, 'src', base)


def _source_context(root, hits, window=1):
    """拼接台账里**引用所在行 ±window** 的上下文（供第 4 层的 N5/N7 去噪判据用）。

    🔴 **必须是行级局部，不能用「整个文件的前 2000 字」** ——
    favorite 域实测（2026-10-26 第三十四轮）：旧写法取来源文件的**前 2000 字**，
    而 `_is_noise_ref` 拿它做**整文件共用**的判定
    ⇒ **文件里任意一处**出现「见 §」「已登记」「DRIFT-xxx」「删除」等词，
    ⇒ **该文件的所有引用都被判为噪声**
    ⇒ 实测 favorite 12 份台账的 **181** 条引用里 **178 条被去噪**（只剩 3 条不悬空），
    ⇒ 而那**唯一的一条真缺陷**（`serviceconf.go:83-90` 行号越界，该文件共 89 行）
    ⇒ **被 N7 吃掉了**
    ⇒ ⇒ **假阴性**：门禁最该抓的东西被噪声淹没。
    ⇒ 改成**引用所在行 ±1** 后：真缺陷立刻暴露，
    而「台账在记述历史纠错」（如「补成 `xxx.go:280-282` ⇒ **越界**」—— 记的是**当时**的错）
    这类**紧邻**的记述仍被正确去噪。
    ⇒ 实测（favorite 12 份 / 181 条）：**不悬空 3 → 158** · **已去噪 178 → 22**
    ⇒ **FAIL 0 → 1**（多出来的 1 条 = 上面那条**真缺陷**，它终于浮出来了）。

    ⚠️ **必须按 (文件, 行号) 排序拼接** —— 顺序敏感 ⇒ set/dict 迭代序随 hash 随机化漂移
    （同族坑 **PIT-GLOBAL-039**：遍历 `set` 做判定 ⇒ 门禁输出不可复现）。

    @param hits  {台账文件: {该文件里出现引用的行号集合}}
    """
    out = []
    for sf in sorted(hits):
        lines = read(os.path.join(root, sf)).splitlines()
        for ln in sorted(hits[sf]):
            lo = max(0, ln - 1 - window)
            hi = min(len(lines), ln + window)
            out.append('\n'.join(lines[lo:hi]))
    return '\n'.join(out)


def layer4_golines(root, texts, go_root):
    print('=' * 78)
    print('第 4 层 · Go 侧 file:line 出处是否悬空')
    print('=' * 78)
    if not os.path.isdir(go_root):
        print(f'  ⚠️  Go 真源不存在: {go_root} ⇒ 跳过（其余层不受影响）')
        return 0

    gomodcache = os.path.expanduser('~/go/pkg/mod')
    idx = {}
    # ⚠️ **权威 = 真源 `go.mod`**（第三十四轮 · favorite 域实测带出）：
    #    手写表覆盖不到 `gorm.io/gorm` / `prometheus/client_golang` 等 ⇒ 假 FAIL
    roots = []
    seen_root_paths = set()
    for mod, ver in _go_mod_requires(go_root):
        base = os.path.join(gomodcache, _modcache_escape(mod) + '@' + ver)
        if base in seen_root_paths:
            continue
        seen_root_paths.add(base)
        roots.append((mod, base))
    # 手写表只作**兜底**（go.mod 里没有的条目）
    for lib, mv in THIRD_PARTY.items():
        mod, ver = mv.split('@', 1)
        base = os.path.join(gomodcache, _modcache_escape(mod) + '@' + ver)
        if base in seen_root_paths:
            # ⚠️ **必须去重** —— go.mod 与手写表指向**同一个**库时，
            #    同一份文件会被登记两次 ⇒ 「同名文件跨库歧义」里同一个库出现两次
            #    （实测：`core/service/serviceconf.go ∈ ['github.com/zeromicro/go-zero', 'go-zero']`）
            continue
        seen_root_paths.add(base)
        roots.append((lib, base))
    roots.append(('真源', os.path.join(go_root, 'service')))
    for lib, base in roots:
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if fn.endswith('.go'):
                    idx.setdefault(fn, []).append((lib, os.path.join(dp, fn)))

    # 🔴 **Go 标准库也要能定位**（favorite 域实测：`net/http/server.go:2458` 在
    #    `<GOROOT>/src` 里 ⇒ 索引不含标准库 ⇒ 假 FAIL）
    goroot = _detect_goroot()

    refs = {}
    for f, t in texts.items():
        for m in REF_RE.finditer(t):
            key = (m.group(1), int(m.group(2)), int(m.group(3)) if m.group(3) else int(m.group(2)))
            # ⚠️ **必须记住「引用出现在台账的第几行」** —— 第 4 层的 N5/N7 去噪
            #    要按**行级局部**取上下文（否则文件里任意一处噪声词会吃掉全文件的引用）
            ln = t.count('\n', 0, m.start()) + 1
            refs.setdefault(key, {}).setdefault(f, set()).add(ln)

    ok, problems, ambiguous, noises = 0, [], [], []
    for (path, s, e), hits in sorted(refs.items()):
        srcs = sorted(hits)
        # ── 先过去噪规则（N1/N5/N6/N7）：已知噪声不计 FAIL
        # ⚠️ **上下文是「引用所在行 ±1」的行级局部**（第三十四轮改）
        #    ⇒ 旧写法（来源文件的前 2000 字、整文件共用）会让「文件里任意一处噪声词」
        #    把**该文件的所有引用**一起去噪 ⇒ 实测 favorite 域 12 份台账 181 条里
        #    178 条被去噪（只剩 3 条不悬空）、唯一那条真缺陷被吃掉 ⇒ **假阴性**
        # ⚠️ **`_source_context` 内部必须按 (文件, 行号) 排序** —— 顺序敏感 ⇒
        #    迭代序随 Python 字符串 hash 随机化**每次进程都不同**
        #    ⇒ **同一条 file:line 引用每次运行落进不同分类桶**
        #      （N5 / N7 / 跨库歧义 / MISSING）。
        #    实测（2026-10-26）：同一份仓库状态连跑 3 次，层 4 的
        #    「引用不悬空 / 已去噪 / 跨库歧义」三个计数分别是
        #    (20,105,3) / (13,112,2) / (14,111,1) ⇒ **门禁输出不可复现**；
        #    `PYTHONHASHSEED=0` 连跑 3 次完全一致 ⇒ 坐实是 hash 随机化。
        src_text = _source_context(root, hits)
        is_noise, why = _is_noise_ref(path, s, e, src_text)
        if is_noise:
            noises.append((path, s, e, why, srcs))
            continue
        # ── 精确定位通道 ①：`module@version/...` ⇒ 直接在 module cache 里找
        #    （basename 索引解析不到完整模块路径 ⇒ 假 FAIL；直接定位顺带消除同名歧义）
        direct = _direct_module_candidate(path, gomodcache)
        # ── 精确定位通道 ②：Go **标准库** ⇒ `<GOROOT>/src/...`
        if direct is None:
            direct = _stdlib_candidate(path, goroot)
        if direct is not None and os.path.isfile(direct):
            n = sum(1 for _ in open(direct, encoding='utf-8', errors='replace'))
            if s <= n and e <= n:
                ok += 1
            else:
                problems.append((path, s, e, '行号超出文件范围', srcs))
            continue
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
    if noises:
        print(f'  🔇 已去噪（不计 FAIL）: {len(noises)} 条')
        for path, s, e, why, srcs in noises:
            print(f'      {path}:{s}{"-" + str(e) if e != s else ""}  ({why})   ← {", ".join(srcs)}')
    if ambiguous:
        print(f'  ⚠️ 同名文件跨库歧义（归属需人工确认）: {len(ambiguous)} 条')
        for path, s, e, libs in ambiguous:
            print(f'      {path}:{s}{"-" + str(e) if e != s else ""}  ∈ {libs}')
    print('  ⚠️ 「不悬空」≠「内容对得上」⇒ 关键断言仍需人工抽验。')
    return len(problems)


# ──────────────────────────────────────────────────────────────────────────
# 去噪规则（来自 users-user 版实测踩出，勿删）
#   N1. 框架注解名（`SpringBootTest` / `WebMvcTest` …）**不是**仓内测试类 ⇒ 排除
#   N5. 「**删除** xxx.java」「该类**不存在**」是历史记述 ⇒ 不是「引用了一个不存在的文件」
#   N6. 临时探针脚本（`tmp/…`）本就不在仓内 ⇒ 记 WARN 不记 FAIL
#   N7. 台账**自陈**不存在的类（已记档并说明原因）⇒ 记 OK 并附出处，不算 FAIL
#   （N2 Go 侧路径 / N3 模块缓存 / N4 basename 冲突 —— 本层的 idx 已实现）
# ──────────────────────────────────────────────────────────────────────────
NOISE_TESTCLASS = re.compile(
    r'^(SpringBootTest|WebMvcTest|DataJpaTest|ExtendWith|Test|Tag|Suite|SpringExtension|'
    r'ParameterizedTest|TestFactory|RepeatedTest|TestInstance|TestMethodOrder|Disabled)\b')

# N5：台账里「删除/不存在/已移除」的历史记述 —— 该引用不是「引用了一个不存在的文件」
DELETED_NARRATION = re.compile(
    r'(删除|已删除|不存在|已移除|移除后|被删|不再存在|未找到该类)')

# N6：临时探针脚本本就不在仓内
TMP_PROBE = re.compile(r'(^|/)(tmp|temp|\.tmp)[/_-]')

# N7：台账自陈不存在的类（记档并说明原因）⇒ 不算 FAIL
SELF_DOCUMENTED = re.compile(
    r'(该测试类在仓内不存在|仓内不存在|已记档|已登记|见 §|见 本节|DRIFT-[A-Z]+-\d+)')

# N14（2026-10-26 第三十四轮 · favorite 域实测带出）：台账在记「**当时补的那个（错的）值**」
#   ⇒ 那是**历史记述**，不是「现在还引用着一个不存在的出处」⇒ 不算 FAIL。
#   ⚠️ **词表必须窄** —— favorite 域实测两种形态要分开：
#     ① 「补成 `create_favorite_logic.go:280-282` ⇒ **越界**（该文件仅 170 行）」⇒ **历史记述** ⇒ 去噪
#     ② 「抓出 **1 处行号真错**（`core/service/serviceconf.go:83-90` ⇒ 该文件共 89 行 ⇒ 应为 `:83-89`）」
#        ⇒ **真缺陷**（虽然同行也给了正确值）⇒ **必须报出**
#   ⇒ ⇒ 「越界」「行号」「应为」「该文件共/仅 N 行」等词**都不能进词表**（会把 ② 一起吃掉 ⇒ 假阴性）
#   ⇒ ⇒ 只认**明确的更正动词**：「补成」「更正为」「改正为」「改正命名」「真源里是」
CORRECTION_NARRATION = re.compile(
    r'(补成|更正为|改正为|改正命名|真源里是|真实路径是)')


def _is_noise_ref(path, s, e, src_text):
    """判断一条 file:line 引用是否属于「已知噪声」⇒ 不计 FAIL。返回 (是否噪声, 原因)"""
    base = os.path.basename(path)
    # N1：框架注解名被当成测试类引用
    if NOISE_TESTCLASS.match(base):
        return True, 'N1 框架注解名，非仓内测试类'
    # N6：临时探针脚本
    if TMP_PROBE.search(path):
        return True, 'N6 临时探针脚本，不在仓内'
    # N5 / N7：需要台账原文上下文，在调用侧判定（这里只做文本级快筛）
    # ⚠️ **不再截断** —— 上下文已是**行级局部**（引用所在行 ±1）⇒ 截断会切掉邻行
    #    （第三十四轮改；旧写法的文件级前缀是「假阴性」的根源）
    if DELETED_NARRATION.search(src_text):
        return True, 'N5 台账记述「删除/不存在」，非悬空引用'
    if SELF_DOCUMENTED.search(src_text):
        return True, 'N7 台账已自陈并记档'
    # N14：台账在记「当时补的那个（错的）值」⇒ 历史记述
    if CORRECTION_NARRATION.search(src_text):
        return True, 'N14 台账记述的是「当时补的那个值」，非当前悬空引用'
    return False, ''


def layer5_unmentioned(root, texts):
    print('=' * 78)
    print('第 5 层 · 仓库里存在但台账从未点名的测试类（覆盖缺口）')
    print('=' * 78)
    mentioned = set()
    for t in texts.values():
        for m in CLASS_RE.finditer(t):
            mentioned.add(m.group(1))
    files = [f for f in repo_java_files(root, 'service/task')
             if f.endswith('.java') and '/src/test/' in f]
    unmentioned = []
    for f in files:
        src = read(os.path.join(root, f))
        # ⚠️ **不只查 class** —— `interface` / `enum` / `@interface` 同样是生产类型，
        #    漏掉它们 ⇒ 静默漏登记（本轮实测：task 域的 2 个 Mapper 接口正属这一类；
        #    碰巧已被台账点名 ⇒ 无实际后果，**但判据的盲区本身要修** ——
        #    换域 / 加类型就会漏）
        m = re.search(r'^\s*(?:public\s+)?(?:static\s+)?(?:final\s+)?'
                      r'(?:class|interface|enum|@interface)\s+([A-Z][A-Za-z0-9]*)', src, re.M)
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

    # ②′ 层 1（2026-10-26 新增）：**路径简写的去噪判据必须两头都对**
    #     ⇒ 阳性：真·简写必须被去噪（否则台账噪声淹没信号 ⇒ 门禁形同虚设）
    #     ⇒ 阴性：真·缺失必须**仍被报出**（否则门禁被吃掉 ⇒ 绿灯不可信）
    #     ⚠️ 用**人造文件清单**做对照 ⇒ 与本仓实际内容无关 ⇒ 任何分支上都成立
    _fake_files = [
        'service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java',
        'service/article/article-rpc/src/main/resources/application.yml',
        'service/users/user/user-api/HUMAN_ONLY.MD',
        # ⚠️ 必须有这一条 —— 否则 `service/comment/` 不在「顶层目录」表里 ⇒
        #    **N12 会接手去噪** ⇒ 「抽掉 N11」这个变异就**抓不到**了
        #    （本轮实测：M4 首跑未命中 ⇒ 变异无效 ⇒ 对照必须能单独锁住每条规则）
        'service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentDomainService.java',
    ]
    _fake_go = ['service/comment/rpc/internal/model/test-community-postgres.sh']
    _fake_idx = _mk_index(_fake_files, _fake_go)
    # ⚠️ **必须断言「规则编号」而不只是「是否去噪」** ——
    #    否则两条规则**互相掩护**时，去掉其中一条**抓不到**
    #    （本轮实测：N9 被 N12 掩护 ⇒ 「抽掉 N9」这个变异首跑是绿的 ⇒ 变异无效）
    _l1_cases = [
        ('service/common/.../observability/RpcInterceptor.java', True, 'N8', 'N8 省略写法'),
        ('service/UserRegisterService.java', True, 'N9', 'N9 裸文件名'),
        ('service/common/observability/RpcInterceptor.java', True, 'N10', 'N10 短路径'),
        ('service/article/article-rpc/application.yml', True, 'N10', 'N10 短路径'),
        ('service/comment/rpc/internal/model/test-community-postgres.sh', True, 'N11', 'N11 Go 侧路径'),
        ('service/demo/demo-rpc/pom.xml', True, 'N12', 'N12 仓外引用（顶层目录不在本仓）'),
        ('service/common/src/main/java/com/rag/common/observability/LikeRpcClient.java',
         False, '', '同名文件在别的域（like）⇒ 真·缺失'),
        ('service/users/users-common/HUMAN_ONLY.MD', False, '', '该文件确实不存在'),
        ('service/common/observability/NoSuchClass.java', False, '', '仓内无此文件'),
        ('tools/difftest/check-protection-layer.sh', False, '', '非 service/ 前缀 ⇒ 一律不去噪'),
        ('docs/DRIFT/global/_INDEX.md', False, '', '非 service/ 前缀 ⇒ 目录写错要报出来'),
    ]
    for _p, _want, _rule, _why in _l1_cases:
        _got, _got_rule, _reason = _is_simplified_path(_p, _fake_idx)
        if _got == _want and _got_rule == _rule:
            print(f'    ✅ 层 1 简写判据正确（{"去噪 " + _got_rule if _want else "报缺"}）：{_why}')
        else:
            print(f'    ❌ 层 1 简写判据错判：{_p} —— 期望'
                  f'{"去噪 " + _rule if _want else "报缺"}，实得'
                  f'{"去噪 " + _got_rule if _got else "报缺"}（{_why}）')
            bad += 1

    # ②′′ 层 1（2026-10-26 新增）：**「状态叙述」邻行判定**必须两头都对
    #     ⇒ 台账常把叙述写在**上一行**再列路径 ⇒ 邻行窗口 ±2
    _nl = [
        '以下 3 条**被登记为已存在、实际不存在**：',
        '`service/users/users-common/HUMAN_ONLY.MD`、',
        '`service/message/message-rpc/HUMAN_ONLY.MD`。',
    ]
    # ⚠️ **必须覆盖「张冠李戴」这个阴性对照**（本轮实测踩到）：
    #    邻行里若讲的是**别的**文件的状态，不能当成本引用的叙述
    #    ⇒ 否则**真实缺陷被静默**（实测：global 台账 E43 行引用的真实缺陷
    #    被上一行 E42 的「删除」二字吃掉）。
    _wrong = [
        '| **E42** | ✅ 已修 —— ① `docs/DRIFT/global/_INDEX.md` ×2 ⇒ **已删除** |',
        '| **E43** | 引用 `tools/check-ddl-static.py` ⇒ 真实是 `tools/difftest/check-ddl-static.py` |',
    ]
    # 🔴 **密集行**：一行挤了**多个互不相干的事实** ⇒ 叙述词必须归属**第一个**路径
    _dense = [
        '| **E42** | ✅ `docs/DRIFT/global/_INDEX.md` 已修 |',
        '| **E43** | ④ `docs/Intentional_Deviations/users/_shared.md` 临时落点已迁回并**删除** '
        '⑤ like 域的 `tools/check-ddl-static.py` 路径写错 |',
    ]
    _n13_cases = [
        # 邻行纯叙述行（不含任何路径）⇒ 命中
        (_nl, 1, True, '邻行是纯叙述行 ⇒ 命中', 'service/users/users-common/HUMAN_ONLY.MD'),
        (_nl, 2, True, '邻行是纯叙述行 ⇒ 命中', 'service/message/message-api/HUMAN_ONLY.MD'),
        # 同行 + 本路径是该行**第一个**路径 ⇒ 命中
        (['`tools/difftest/check-protection-layer.sh` 在真容器下守 —— 该脚本**不存在**'], 0, True,
         '同行 + 本路径是首个路径 ⇒ 命中', 'tools/difftest/check-protection-layer.sh'),
        # 同行 + 本路径**不是**第一个 ⇒ 不命中（密集行里的别人的叙述）
        (_dense, 1, False, '同行但本路径不是第一个 ⇒ 不得命中', 'tools/check-ddl-static.py'),
        # 邻行讲的是**别的**文件（该行含别的路径）⇒ 不命中
        (_wrong, 1, False, '邻行讲的是别的文件 ⇒ 不得命中', 'tools/check-ddl-static.py'),
        # 无叙述 ⇒ 不命中
        (['一条正常引用 `service/common/Foo.java`'], 0, False, '无状态叙述', 'service/common/Foo.java'),
    ]
    for _lines, _ln, _want, _why, _p in _n13_cases:
        _got, _at = _narration_in_lines(_lines, _ln, path=_p)
        if _got == _want:
            print(f'    ✅ 层 1 状态叙述判定正确（{"命中" if _want else "不命中"}）：{_why}')
        else:
            print(f'    ❌ 层 1 状态叙述判定错判：期望{"命中" if _want else "不命中"}（{_why}）')
            bad += 1

    # ②″ 层 4（2026-10-26 新增）：来源上下文拼接必须**与 set 迭代顺序无关**
    #     ⇒ 否则同一条 file:line 每次运行落进不同分类桶 ⇒ 门禁输出不可复现
    #     ⚠️ 断言**白盒契约**：拼接结果 == 按 sorted(srcs) 逐个拼接
    # ⚠️ **必须用「逆序 list」而不是 set** ——
    #    set 的迭代序在**同一进程内**也不保证等于 sorted 序（字符串 hash 随机化）
    #    ⇒ 原写法（拿 set 比 sorted 拼接）是 **flaky 对照**：有时红、有时绿
    #    ⇒ 变异「抽掉 sorted()」**有时抓不到** ⇒ 对照必须**确定性**地区分。
    # ⚠️ **第三十四轮起上下文是「行级局部」** ⇒ 白盒契约改成：
    #    **同一组 (文件, 行号) ⇒ 无论 dict 的插入顺序如何，输出必须逐字节相同**
    #    （旧契约是「按 sorted(srcs) 拼接整文件前 2000 字」）
    _hits_a = {'docs/DRIFT/task/rpc.md': {10}, 'docs/DRIFT/task/api.md': {5}}
    _hits_b = {'docs/DRIFT/task/api.md': {5}, 'docs/DRIFT/task/rpc.md': {10}}
    _ctx_a = _source_context(root, _hits_a)
    _ctx_b = _source_context(root, _hits_b)
    if _ctx_a == _ctx_b and _ctx_a:
        print('    ✅ 层 4 的来源上下文按 (文件, 行号) 排序拼接（输出可复现）')
    else:
        print('    ❌ 层 4 的来源上下文未按 (文件, 行号) 排序 ⇒ 输出随 hash 随机化漂移')
        bad += 1

    # ②‴ 层 4（2026-10-26 第三十四轮 · favorite 域实测带出）：**去噪必须是行级局部**
    #     ⇒ 阳性：文件**别处**有噪声词 ⇒ 不得把远处的引用一起去噪
    #            （否则「文件里任意一处噪声词吃掉全文件引用」⇒ 真缺陷被淹没 ⇒ 假阴性）
    #     ⇒ 阴性：**同一行**的噪声词 ⇒ 必须去噪（否则台账的历史纠错记述变成假 FAIL）
    #     ⚠️ 用**人造临时文件**做对照 ⇒ 与本仓实际内容无关 ⇒ 任何分支上都成立
    import tempfile
    with tempfile.TemporaryDirectory() as _td:
        _far = os.path.join(_td, 'docs/DRIFT/x/far.md')
        _near = os.path.join(_td, 'docs/DRIFT/x/near.md')
        os.makedirs(os.path.dirname(_far))
        with open(_far, 'w', encoding='utf-8') as f:
            f.write('第 1 行：台账开头提到「见 §」\n'
                    '第 2 行：中间内容\n'
                    '第 3 行：中间内容\n'
                    '第 4 行：中间内容\n'
                    '第 5 行：中间内容\n'
                    '第 6 行：中间内容\n'
                    '第 7 行：中间内容\n'
                    '第 8 行：中间内容\n'
                    '第 9 行：中间内容\n'
                    '第 10 行：引用 `a/b.go:12` 的正常引用\n')
        with open(_near, 'w', encoding='utf-8') as f:
            f.write('第 1 行：引用 `c/d.go:7`，该类**已删除**\n')
        _far_ctx = _source_context(_td, {'docs/DRIFT/x/far.md': {10}})
        _near_ctx = _source_context(_td, {'docs/DRIFT/x/near.md': {1}})
    _n_far, _r_far = _is_noise_ref('a/b.go', 12, 12, _far_ctx)
    _n_near, _r_near = _is_noise_ref('c/d.go', 7, 7, _near_ctx)
    if not _n_far:
        print('    ✅ 层 4 去噪是**行级局部**：别处的噪声词不会吃掉远处的引用')
    else:
        print(f'    ❌ 层 4 去噪是**文件级**：别处的噪声词把真缺陷一起吃掉了（{_r_far}）')
        bad += 1
    if _n_near:
        print('    ✅ 层 4 去噪能吃到**同行**的历史记述（台账自陈已删除）')
    else:
        print('    ❌ 层 4 去噪吃不到同行的历史记述 ⇒ 历史纠错会变成假 FAIL')
        bad += 1

    # ②′ 层 4：**N14「更正记述」**必须两头都对
    #     ⇒ 阳性：「补成 X ⇒ 越界」这类**历史记述**必须去噪（否则台账里全是噪声）
    #     ⇒ 阴性：「抓出 1 处行号真错（X ⇒ 应为 Y）」这类**真缺陷**必须**仍被报出**
    #        （⚠️ 若把「越界」「应为」「该文件共/仅 N 行」放进词表 ⇒ 这条会被吃掉 ⇒ 假阴性）
    _n14_pos = '| **E-d** | 补成 `create_favorite_logic.go:280-282` ⇒ **越界**（该文件仅 170 行） |'
    _n14_neg = ('| 第十八轮 | 抓出 **1 处行号真错**（`core/service/serviceconf.go:83-90` '
                '⇒ 该文件共 89 行 ⇒ 应为 `:83-89`） |')
    _n_pos, _r_pos = _is_noise_ref('create_favorite_logic.go', 280, 282, _n14_pos)
    _n_neg, _r_neg = _is_noise_ref('core/service/serviceconf.go', 83, 90, _n14_neg)
    if _n_pos:
        print('    ✅ 层 4 的 N14 能吃到「补成 X ⇒ 越界」这类历史记述')
    else:
        print('    ❌ 层 4 的 N14 吃不到历史记述 ⇒ 台账噪声淹没信号')
        bad += 1
    if not _n_neg:
        print('    ✅ 层 4 的 N14 不会吃掉「抓出 1 处行号真错」这类真缺陷')
    else:
        print(f'    ❌ 层 4 的 N14 把真缺陷一起吃掉了（{_r_neg}）⇒ 假阴性')
        bad += 1

    # ②⁗ 层 4：第三方库清单必须**从真源 go.mod 动态解析**（手写表覆盖不到 ⇒ 假 FAIL）
    with tempfile.TemporaryDirectory() as _td:
        with open(os.path.join(_td, 'go.mod'), 'w', encoding='utf-8') as f:
            f.write('module sea-try-go\n\ngo 1.21\n\nrequire (\n\tgithub.com/zeromicro/go-zero v1.10.2\n\tgorm.io/gorm v1.31.1 // indirect\n)\n')
        _mods = _go_mod_requires(_td)
    if ('gorm.io/gorm', 'v1.31.1') in _mods and ('github.com/zeromicro/go-zero', 'v1.10.2') in _mods:
        print('    ✅ 层 4 的第三方库清单能从 go.mod 动态解析（含 indirect）')
    else:
        print('    ❌ 层 4 的第三方库清单没解析 go.mod ⇒ 不在表里的模块一律假 FAIL')
        bad += 1
    if not any(_m == 'example.com/nope' for _m, _v in _mods):
        print('    ✅ 层 4 的第三方库清单不会凭空造模块')
    else:
        print('    ❌ 层 4 的第三方库清单凭空造了模块')
        bad += 1

    # ②⁘ 层 4：`module@version/...` 必须能**直接定位**到 module cache 里的文件
    #     ⇒ basename 索引解析不到完整模块路径 ⇒ 假 FAIL
    with tempfile.TemporaryDirectory() as _td:
        _cache = os.path.join(_td, 'pkg/mod')
        _f = os.path.join(_cache, 'gorm.io/gorm@v1.31.1/schema/naming.go')
        os.makedirs(os.path.dirname(_f))
        with open(_f, 'w', encoding='utf-8') as fh:
            fh.write('package schema\n')
        _c = _direct_module_candidate('gorm.io/gorm@v1.31.1/schema/naming.go', _cache)
        _c_none = _direct_module_candidate('schema/naming.go', _cache)
        # ⚠️ **isfile 必须在这里查** —— `TemporaryDirectory` 退出后目录已被删 ⇒
        #    跑到 `with` 外面再查 ⇒ 恒为 False ⇒ **对照自己写错**（本轮真踩到）
        _c_hit = bool(_c) and os.path.isfile(_c)
    if _c_hit:
        print('    ✅ 层 4 能把 `module@version/...` 直接定位到 module cache 里的文件')
    else:
        print('    ❌ 层 4 解析不了 `module@version/...` ⇒ 假 FAIL')
        bad += 1
    if _c_none is None:
        print('    ✅ 层 4 不会把普通路径硬拼成 module cache 路径')
    else:
        print('    ❌ 层 4 把普通路径硬拼成 module cache 路径了')
        bad += 1
    if _modcache_escape('github.com/IBM/sarama') == 'github.com/!i!b!m/sarama':
        print('    ✅ 层 4 的 module cache 路径转义正确（大写字母 → !小写）')
    else:
        print('    ❌ 层 4 的 module cache 路径转义不对 ⇒ 带大写的模块永远找不到')
        bad += 1

    # ②⁙ 层 4：Go **标准库**的引用必须能定位（`<GOROOT>/src/...`）
    with tempfile.TemporaryDirectory() as _td:
        _goroot = os.path.join(_td, 'go')
        _f = os.path.join(_goroot, 'src/net/http/server.go')
        os.makedirs(os.path.dirname(_f))
        with open(_f, 'w', encoding='utf-8') as fh:
            fh.write('package http\n')
        _c1 = _stdlib_candidate('net/http/server.go', _goroot)
        _c2 = _stdlib_candidate('src/net/url/url.go', _goroot)
        _c1_hit = bool(_c1) and os.path.isfile(_c1)
    if _c1_hit:
        print('    ✅ 层 4 能定位 Go 标准库引用（<GOROOT>/src/...）')
    else:
        print('    ❌ 层 4 定位不了 Go 标准库引用 ⇒ 假 FAIL')
        bad += 1
    if _c2 and _c2.endswith(os.path.join('src', 'net', 'url', 'url.go')):
        print('    ✅ 层 4 能定位带 `src/` 前缀的标准库引用')
    else:
        print('    ❌ 层 4 定位不了带 `src/` 前缀的标准库引用')
        bad += 1

    # ③ 层 3：commit 覆盖检查必须能识别「未登记的 commit」（阳性对照）
    #    ⇒ 判据：一个**虚构的**哈希不应被判为已登记
    def registered(hashes_in_ledger, h):
        return h in hashes_in_ledger or h[:7] in hashes_in_ledger
    ledger_hashes = {'abc1234'}
    if not registered(ledger_hashes, 'deadbee') and registered(ledger_hashes, 'abc1234'):
        print('    ✅ 层 3 的 commit 覆盖判定有区分力（未登记的能被抓到）')
    else:
        print('    ❌ 层 3 的 commit 覆盖判定无区分力')
        bad += 1


    # ③′ 层 3（新）：commit 清单「一一对应」判据 —— 四类缺陷必须都能被抓到
    #    ⚠️ 用**人造样本**做阳性对照：正常样本必须**不报**，坏样本必须**报**
    #    （否则「全绿」只是「什么都没查」—— 本轮实测：8 行占位符一路绿灯）
    H = ['aaa1111', 'bbb2222', 'ccc3333']
    HEAD = 'ccc3333'
    p_good, _ = check_commit_manifest(H, HEAD, ['aaa1111', 'bbb2222', '（本轮）'])
    if p_good:
        print(f'    ❌ 层 3 新判据**误报**：正常样本被判有缺陷 ⇒ {p_good}')
        bad += 1
    else:
        print('    ✅ 层 3 新判据不误报（正常样本通过）')
    for label, rows in [
        ('占位符残留', ['aaa1111', '（本轮）', '（本轮）']),
        ('同一个 commit 多行', ['aaa1111', 'aaa1111', '（本轮）']),
        ('登记行数不守恒', ['aaa1111', 'bbb2222']),
        ('漏登记（非 HEAD）', ['bbb2222', '（本轮）']),
    ]:
        p, _ = check_commit_manifest(H, HEAD, rows)
        if p:
            print(f'    ✅ 层 3 新判据能抓到「{label}」')
        else:
            print(f'    ❌ 层 3 新判据**抓不到**「{label}」⇒ 变异测试没意义')
            bad += 1

    # ③″ commit 清单的**行提取**必须只认 commit 表（普通表的首列不是登记项）
    #    ⚠️ 样本里两张表**故意紧挨着**（中间无空行）—— markdown 允许这样，
    #    按空行切表会把它们并成一张 ⇒ 后面那张表的行混进清单（本轮实测踩到）
    #    另加一张「普通表首列恰好是哈希」的表 ⇒ 长得像登记项，但不是登记项
    sample_doc = ('| commit | 内容 |\n|---|---|\n| `aaa1111` | x |\n'
                   '| 维度 | A |\n|---|---|\n| `tools/x.py` | y |\n'
                   '\n'
                   '| 工具 | 说明 |\n|---|---|\n| `bbb2222` | 普通表里的哈希样首列 |\n')
    got = commit_manifest_rows(sample_doc)
    if got == ['aaa1111']:
        print('    ✅ commit 清单行提取只认 commit 表（普通表首列未被误收）')
    else:
        print(f'    ❌ commit 清单行提取把普通表也收了 ⇒ {got}')
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

    # ⑤ 层 6：生产类型的判定必须**认 interface / enum**（本轮补的判据盲区）
    probe = 'public interface FooProbe {}\n'
    m = re.search(r'^\s*(?:public\s+)?(?:static\s+)?(?:final\s+)?'
                  r'(?:class|interface|enum|@interface)\s+([A-Z][A-Za-z0-9]*)', probe, re.M)
    if m and m.group(1) == 'FooProbe':
        print('    ✅ 层 6 的生产类型判定认 interface/enum（不只认 class）')
    else:
        print('    ❌ 层 6 的生产类型判定漏了 interface/enum ⇒ 会静默漏登记')
        bad += 1

    # ⑥ 层 5/6：文件清单必须**含未跟踪文件**（否则刚写完的类在提交前不可见）
    probe_dir = 'service/task'
    tracked = set(git(root, 'ls-files', probe_dir).splitlines())
    untracked = set(git(root, 'ls-files', '--others', '--exclude-standard',
                        probe_dir).splitlines())
    allf = set(repo_java_files(root, probe_dir))
    if allf == tracked | untracked:
        print(f'    ✅ 层 5/6 的文件清单含**未跟踪**文件'
              f'（已跟踪 {len(tracked)} + 未跟踪 {len(untracked)}）')
    else:
        print('    ❌ 层 5/6 的文件清单漏了未跟踪文件 ⇒ 刚写的类在提交前不可见')
        bad += 1

    print(f'  ⇒ 自检{"通过" if not bad else "失败"}')
    return bad


def layer6_unmentioned_production(root, texts):
    """台账是否点名了本域**生产类**（不只是测试类）。

    ⚠️ 上一轮只查了测试类 ⇒ **生产类一个都没点名**（`TaskKafkaTopics` 这种
    `@ConfigurationProperties` 的宿主都在其中）⇒ 而生产类里藏着四个
    「缺了就起不来」的启动阻塞项 ⇒ 台账不点名 = 以后重构时看不到这条风险。
    """
    print('=' * 78)
    print('第 6 层 · 台账从未点名的**生产类**（覆盖缺口）')
    print('=' * 78)
    mentioned = set()
    for t in texts.values():
        # ⚠️ 两种登记形态都要认：① 纯类名 `TaskKafkaTopics`
        #    ② 带路径的形式 `bootstrap/NacosRegistration`（路径末段即类名）
        for m in re.findall(r'`([A-Za-z0-9_./-]+)`', t):
            mentioned.add(m.rsplit('/', 1)[-1].rsplit('.', 1)[-1])
    files = [f for f in repo_java_files(root, 'service/task')
             if f.endswith('.java') and '/src/main/java/' in f]
    unmentioned = []
    for f in files:
        src = read(os.path.join(root, f))
        m = re.search(r'^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Z][A-Za-z0-9]*)', src, re.M)
        if m and m.group(1) not in mentioned:
            unmentioned.append((m.group(1), f))
    print(f'  仓库内 task 域生产类 {len(files)} 个 ⇒ 台账未点名 {len(unmentioned)} 个')
    for c, f in unmentioned:
        print(f'    ⚠️  {c}')
        print(f'         {f}')
    if not unmentioned:
        print('    ✅ 全部已登记')
    return len(unmentioned)


# ====================================================================
# 变异测试：故意破坏台账 ⇒ 复核器必须**精确**抓到
# --------------------------------------------------------------------
# ⚠️ 为什么要进仓：AGENTS.md 已立「**跑绿 ≠ 对**」的纪律，而「跑绿」本身也要能被证伪。
#    ⇒ 判据必须有一条「**把台账弄坏，它就红**」的反向证据；否则「全绿」只是「什么都没查」。
#    ⇒ 本函数进仓 = 下一任 agent 可以一条命令复跑，不必再手写脚本。
# ====================================================================

def _manifest_last_placeholder_line(text):
    """commit 清单里**最后一个占位行**的下标（变异要插在它之后才算「在清单内」）。"""
    idxs = [i for i, l in enumerate(text.split('\n')) if l.startswith('| `（本轮）` |')]
    return max(idxs) if idxs else -1


def _insert_before_last_placeholder(text, row):
    """插在**最后一个占位行之前** —— 这样占位行仍留在清单末尾，
    ⇒ 变异只触发**目标**判据，不会被「占位符不在末尾」这条伴生信号盖住。"""
    lines = text.split('\n')
    i = _manifest_last_placeholder_line(text)
    lines.insert(i, row)
    return '\n'.join(lines)


def run_mutation_tests(root):
    """跑一遍变异测试。返回失败数（0 = 全部精确命中）。"""
    import contextlib
    import io

    print('=' * 78)
    print('变异测试 · 故意破坏台账，复核器必须**精确**抓到')
    print('=' * 78)
    readme = os.path.join(root, 'docs/DRIFT/task/_README.md')
    api = os.path.join(root, 'docs/DRIFT/task/api.md')
    orig_readme, orig_api = read(readme), read(api)
    bad = 0

    # ⚠️ **阳性对照的基线必须在任何变异之前取** ——
    #    口径 = **「复原后的计数 == 变异前的计数」**，**不是**「复原后必须 == 0」。
    #    🔴 **为什么不能要求 == 0**：第 3 层含**分支相关**判据（commit 清单覆盖 =
    #       本分支的 commit 数 vs 台账登记行数）⇒ 在**非 task 分支**上恒不为 0
    #       ⇒ 本轮实测（`align/global`，89 个 commit）恒报 134 处 ⇒ **阳性对照恒红**
    #       ⇒ ⇒ **整个 `--mutation-test` 在任何别的分支上都不可用**
    #       ⇒ ⇒ 判据可逆性的正确口径是「**回到变异前**」，不是「== 0」。
    buf0 = io.StringIO()
    with contextlib.redirect_stdout(buf0):
        baseline_layer3 = layer3_numbers(root, ledger_texts(root, 'task'))

    merge_base = git(root, 'merge-base', 'HEAD', 'do-my-own-gh')
    hashes = [h.strip() for h in git(root, 'rev-list', '--abbrev-commit',
                                      '--no-merges', f'{merge_base}..HEAD').splitlines() if h.strip()]
    head = git(root, 'rev-parse', '--short=7', 'HEAD')

    def manifest_problems():
        rows = commit_manifest_rows(read(readme))
        return check_commit_manifest(hashes, head, rows)[0]

    def layer3_problems():
        """跑整层第 3 层（覆盖编号连续性 / 悬空引用这类**跨文件**判据）。"""
        import contextlib
        import io as _io
        buf = _io.StringIO()
        with contextlib.redirect_stdout(buf):
            n = layer3_numbers(root, ledger_texts(root, 'task'))
        out = buf.getvalue()
        ps = []
        for line in out.split('\n'):
            if line.strip().startswith('❌'):
                ps.append(line.strip().lstrip('❌ ').strip())
        return ps

    pits = os.path.join(root, 'docs/Pitfalls/task/rpc.md')
    orig_pits = read(pits)

    # ⚠️ 每个用例**显式指定**用哪条判据 —— 「改哪个文件」与「查哪条判据」是**两件事**：
    #    上一版按文件自动选判据 ⇒ 「改 `_README.md` 却跑清单判据」⇒ 变异**根本没被查**
    #    ⇒ 判据被误判成假绿（这正是本轮要根治的那类错误）。
    MANIFEST, LAYER3 = 'manifest', 'layer3'
    cases = [
        ('占位符残留（清单里多一个「（本轮）」行）', readme,
         lambda t: _insert_before_last_placeholder(
             t, '| `（本轮）` | 变异样本：多出来的占位行 |'),
         lambda ps: any('占位符行' in p for p in ps), MANIFEST),
        ('同一个 commit 被写成两行', readme,
         lambda t: _insert_before_last_placeholder(
             t, '| `c886406` | 变异样本：同一个 commit 的第二行 |'),
         lambda ps: any('被登记了' in p for p in ps), MANIFEST),
        ('删掉一行登记（非 HEAD）', readme,
         lambda t: re.sub(r'^\| `185448c`.*\n', '', t, count=1, flags=re.M),
         lambda ps: any('完全没登记' in p for p in ps), MANIFEST),
        ('登记了一个不在本分支上的 commit', readme,
         lambda t: _insert_before_last_placeholder(
             t, '| `deadbee` | 变异样本：本分支上没有的 commit |'),
         lambda ps: any('不在本分支上' in p for p in ps), MANIFEST),
        ('台账引用了不存在的 Pitfall ID', readme,
         # ⚠️ 追加到**文件末尾**（不是表格行）⇒ 只影响「悬空引用」判据，不影响清单判据
         lambda t: t.rstrip('\n') + '\n\n（变异样本：引用 `PITFALL-TASK-RPC-99`）\n',
         lambda ps: any('不存在' in p for p in ps), LAYER3),
        ('Pitfall 编号断号', pits,
         lambda t: t.replace('PITFALL-TASK-RPC-32', 'PITFALL-TASK-RPC-33'),
         lambda ps: any('编号连续' in p for p in ps), LAYER3),
    ]

    try:
        for label, path, mutate, expect, which in cases:
            base = read(path)
            try:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(mutate(base))
                problems = (manifest_problems() if which == MANIFEST
                            else layer3_problems())
            finally:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(base)
            hit = next((p for p in problems if expect([p])), None)
            if hit:
                print(f'  ✅ 变异「{label}」⇒ 被精确抓到：{hit[:80]}')
            else:
                print(f'  ❌ 变异「{label}」**没被抓到** ⇒ 这一条判据是假绿'
                      f'（实际报出：{problems}）')
                bad += 1

        # 回归：第 3 层的 api 域断言（改标题条数 ⇒ 必须红）
        try:
            with open(api, 'w', encoding='utf-8') as f:
                f.write(re.sub(r'^## 一、([^\n（]*)（\d+ 条', r'## 一、\1（2 条',
                               orig_api, count=1, flags=re.M))
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                n = layer3_numbers(root, ledger_texts(root, 'task'))
            if n > 0:
                print('  ✅ 变异「api.md §一 标题条数造假」⇒ 第 3 层报错（回归仍有效）')
            else:
                print('  ❌ 变异「api.md §一 标题条数造假」**没被抓到** ⇒ 回归已失效')
                bad += 1
        finally:
            with open(api, 'w', encoding='utf-8') as f:
                f.write(orig_api)

        # 阳性对照：复原后必须**回到变异前**的计数（不是「== 0」—— 见上方口径说明）
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            n = layer3_numbers(root, ledger_texts(root, 'task'))
        if n == baseline_layer3:
            print(f'  ✅ 复原后第 3 层回到变异前的 {n} 处（阳性对照：变异可逆、判据不误报）')
        else:
            print(f'  ❌ 复原后第 3 层报 {n} 处 ≠ 变异前的 {baseline_layer3} 处 ⇒ 变异没复原干净')
            bad += 1
    finally:
        with open(readme, 'w', encoding='utf-8') as f:
            f.write(orig_readme)
        with open(api, 'w', encoding='utf-8') as f:
            f.write(orig_api)
        with open(pits, 'w', encoding='utf-8') as f:
            f.write(orig_pits)
    print(f'  ⇒ 变异测试{"全部精确命中" if not bad else f"有 {bad} 处没命中"}')
    return bad


# ====================================================================

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--domain', default='task')
    ap.add_argument('--go-root', default=DEFAULT_GO_ROOT)
    ap.add_argument('--repo-root', default=None)
    ap.add_argument('--mutation-test', action='store_true',
                    help='只跑变异测试（故意破坏台账 ⇒ 必须被抓到）')
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
    if args.mutation_test:
        bad += run_mutation_tests(root)
        print()
        print('=' * 78)
        print('✅ 变异测试全部精确命中' if not bad else f'❌ 变异测试有 {bad} 处没命中')
        print('=' * 78)
        return 1 if bad else 0

    bad += layer0_selfcheck(root)
    bad += layer1_paths(root, texts, args.go_root)
    bad += layer2_claims(root)
    bad += layer3_numbers(root, texts)
    bad += layer4_golines(root, texts, args.go_root)
    unmentioned = layer5_unmentioned(root, texts)
    unmentioned_prod = layer6_unmentioned_production(root, texts)

    print()
    print('=' * 78)
    if bad:
        print(f'❌ 复核结果：{bad} 处不一致（层 0~4）')
    else:
        print('✅ 复核结果：层 0~4 全部通过')
    if unmentioned or unmentioned_prod:
        print(f'⚠️  另有 {unmentioned} 个**测试类** + {unmentioned_prod} 个**生产类**'
              f'台账未点名（层 5·6，不算失败）')
    print('=' * 78)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
