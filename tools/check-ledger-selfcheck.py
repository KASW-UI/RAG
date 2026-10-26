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
| 4+ | ④的**补充判据**：锚点文件里**找不到**台账断言的那个实体 | 「行号合法」≠「指对了文件」⇒ 提示级（**不计 FAIL**） |
| 5 | 仓库里**存在但台账从未点名**的测试类 | 列出（覆盖缺口） |

## 边界（重要，别误用）

- ⚠️ 第 2/4 层只保证「**引用不悬空**」与「**断言有对应物**」，
  **不保证「内容完全对得上」** ⇒ 关键断言仍需**人工抽验**。
- ⚠️ 第 4 层里「同名文件在多个库都存在」的条目会单独列出 ⇒ 归属需人工确认。
- ⚠️ **第 4+ 层（补充判据）是「提示级」**：它问的是
  「台账说这个锚点处是符号 X；X 在真源里**唯一声明**过；但 X **不在**这个锚点文件里」
  ⇒ 命中 ⇒ **锚点很可能指错了文件**（favorite 域实测的「证据是假的」那一类）
  ⇒ 但判据靠**从自然语言里猜**台账在说哪个实体 ⇒ **只提示、不失败**。
  ⇒ 已用**正例 + 阴性对照**两条变异证明它非空转（见 `--mutation-test`）。
- ⚠️ 本脚本**只读**，不改任何文件。

## 用法

```bash
python3 tools/check-ledger-selfcheck.py                 # 默认：task 域
python3 tools/check-ledger-selfcheck.py --domain task
python3 tools/check-ledger-selfcheck.py --go-root /path/to/Sea-RideTheWind_golang
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
    # ⚠️ comment 域**刻意不加**（2026-10-26 实测后决定，不加）：
    #   ① global 域第四十八轮给本脚本加的 `--entity-all` 模式已扫 `docs/**/*.md`
    #      ⇒ **本来就覆盖** `docs/FileTree/comment/*`（2026-10-26 那两条塌陷就是它扫出来的）
    #      ⇒ 再登记进 LEDGER_FILES 是**重复覆盖**；
    #   ② 本脚本**对 comment 域结构不适配**（实测 `--domain comment` ⇒ **64 处假阳性**）：
    #      层 3 找 commit 清单依赖 `align/comment` 分支（该分支已合入并删除）⇒ 45 处；
    #      层 1 按字面路径找，认不了台账里的省略路径（`...`）与简写路径 ⇒ 13 处；
    #      层 2/5/6 硬编码 task 域 ⇒ 与本域无关。
    #   ⇒ 加进去 = 把守门变成**假红噪音源**（§3.3「守门不能是错误答案的守卫」）。
    #   ⇒ 需要扫本域时用：python3 tools/check-ledger-selfcheck.py --entity-all
}

# ──────────────────────────────────────────────────────────────────────────
# **域配置**（2026-10-26 第三十五轮 · users/user 域实测带出）
# ──────────────────────────────────────────────────────────────────────────
# 🔴 **为什么需要这张表**：第 2/3 层原先把「已修断言清单 / 台账路径 / surefire 模块名 /
#    漂移编号前缀 / commit 清单表」**全部写死成 task 域**
#    ⇒ `--domain users` 的第 2/3 层输出与 `--domain task` **完全相同**
#    ⇒ 读者会把 **task 域的缺陷当成自己域的** ⇒ **结论不可用**
#    （users/user 域实测：三个 `--domain` 的第 2 层都是「10/10 OK（断言全是 task 域）」、
#      第 3 层都是「实测 task-rpc / task-api」⇒ 域筛选**根本没传到这两层**）
#
# ⇒ 口径：某个域**没有**配置 ⇒ 该层**明确打印「⏭ 不适用于本域」并跳过**
#    （**不是「通过」** —— 沉默地跳过同样会让人以为查过了）。
#
# ⚠️ 本表只登记**本仓真实存在**的域配置；users / users-admin 域的台账格式
#    （`DRIFT-USERRPC-V-001` 这类前缀、无「## 一、已修（N 条）」标题、无 commit 清单表）
#    与 task 域**没有共同点** ⇒ ⇒ 它们的第 2/3 层判据**尚未定义** ⇒ 本轮先显式跳过。
DOMAIN_CFG = {
    'task': {
        # 第 2 层的「已修断言」清单（只覆盖 task 域）
        'claims': 'task',
        # 第 3 层要读的三份台账
        'readme': 'docs/DRIFT/task/_README.md',      # commit 清单表 + 域自述分支
        'api_md': 'docs/DRIFT/task/api.md',
        'pit_md': 'docs/Pitfalls/task/rpc.md',
        'dev_md': 'docs/Intentional_Deviations/task/rpc.md',
        # surefire 实测模块（rpc 侧、api 侧）
        'modules': [('task-rpc', 'rpc'), ('task-api', 'api')],
        # 该域的 Java 源码根（surefire 实测路径要用 ⇒ 不能写死 service/task）
        'java_root': 'service/task',
        # 台账里的编号前缀
        'drift_prefix': 'DRIFT-TASK-RPC',
        'api_prefix': 'DRIFT-TASK-API',
        'pit_prefix': 'PITFALL-TASK-RPC',
        'dev_prefix': r'SD-\d{4}-\d{2}-\d{2}-TASK-\d{2}',
    },
}

# 第三方库：版本取自**真源 go.mod**（不是本地碰巧有的版本）
# ⚠️ **这张表只作兜底** —— 权威是**真源 `go.mod`**（`_go_mod_requires()` 动态解析，
#    含 `// indirect`）⇒ 详见第三十四轮 E51。users/user 域实测：真源 go.mod 第 28 行
#    明写 `gorm.io/gorm v1.31.1`（**直接依赖**）⇒ 而这张表**没有 gorm**
#    ⇒ 凡引用 gorm 出处的台账**一律判「在真源与全部第三方库里都找不到」** ⇒ **假 FAIL**。
#    （`gorm.io` 是**带斜杠的模块路径**，不是 `go-redis` 那种单段名 ⇒ 补表时须单独处理。）
# 第 5/6 层要扫的**域内 Java 源码根**（与 `DOMAIN_CFG` **分开** ——
#   后者还管第 2/3 层的**台账格式**（已修断言清单 + commit 清单表）⇒ 别的域的台账格式
#   与 task 域没有共同点 ⇒ 不能靠加一条 `DOMAIN_CFG` 就把它们放进来）
# 🔴 **第四十三轮实测（users/admin 域 owner 回执带出）**：第 5/6 层原来**写死 `service/task`**
#    ⇒ 别的域跑它 ⇒ 输出的是 **task 域的类**（实测 `--domain users-admin` 打出 20 个测试类
#    + 36 个生产类，**全是 task 的**）⇒ ⇒ 别的域 owner 会误读成「我们域的覆盖缺口」
#    ⇒ ⇒ **必须按域取根**，且**未定义的域显式跳过**（口径与第 2/3 层一致：
#    **「不适用」≠「通过」** —— 见第 2/3 层的注释与自检 ⑤′）
JAVA_ROOTS = {
    'task': ['service/task'],
    'users': ['service/users/user'],
    'users-admin': ['service/users/admin'],
}

THIRD_PARTY = {
    'goka': 'github.com/lovoo/goka@v1.1.16',
    'kafka-go': 'github.com/segmentio/kafka-go@v0.4.47',
    'go-zero': 'github.com/zeromicro/go-zero@v1.10.2',
    'go-queue': 'github.com/zeromicro/go-queue@v1.2.2',
    'go-redis': 'github.com/redis/go-redis/v9@v9.20.0',
    'sarama': 'github.com/IBM/sarama@v1.43.3',
}

# 第 2 层的「已修断言」清单 —— **按域索引**
# ⚠️ 原来只有一个**写死 task 域**的列表 ⇒ 别的域跑这一层，拿到的是 **task 域的结论**。
CLAIMS_BY_DOMAIN = {}   # 由下方 CLAIMS 定义后填充（见 CLAIMS_BY_DOMAIN 的构造处）

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

# 🔴 **第 2 层的断言清单必须按域索引**（第三十五轮 · users/user 域实测带出）——
#    原来只有一个**写死 task 域**的列表 ⇒ 别的域跑第 2 层 ⇒ 拿到的是 **task 域的结论**
#    ⇒ 读者会把 task 域的缺陷当成自己域的 ⇒ **结论不可用**。
#    ⇒ 某个域**没有**清单 ⇒ 该层**明确打印「⏭ 不适用于本域」并跳过**（**不是「通过」**）。
CLAIMS_BY_DOMAIN['task'] = CLAIMS

PATH_RE = re.compile(r'(?:service|tools|docs)/[A-Za-z0-9_./-]+\.(?:java|md|py|sh|yml|MD|json)')
# ⚠️ **2026-10-27 补 `[a-z]IT` 这一支**（`TaskRpcFatJarArtifactGateIT` 这类 **`*IT.java`**）：
#    原来只认 `Test` / `ContractTest` / `GateTest` 结尾 ⇒ **`*IT` 命名的测试类**（surefire 第二个
#    execution `artifact-gate` 绑 `verify` 阶段、只挑 `**/*IT.java` 的**产物层守门**）
#    **永远无法被判定为「已点名」** ⇒ 第 5 层把「台账已登记」误报成「覆盖缺口」⇒ **台账怎么改都消不掉**。
#    ⚠️ 用 `[a-z]IT` 而不是 `IT`：`LIMIT` / `WAIT` 这类**全大写词**不该被当成类名
#    （`[a-z]` 要求 `IT` 前面紧邻一个小写字母 ⇒ 只有 CamelCase 的 `…GateIT` 才命中）。
CLASS_RE = re.compile(r'`([A-Z][A-Za-z0-9]*(?:Test|ContractTest|GateTest|[a-z]IT))\b')
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


def surefire_counts(root, module, java_root='service/task'):
    # ⚠️ **不能写死 `service/task`**（第四十三轮）：调用方（第 3 层）已按域取配置了 ⇒
    #    这里再写死 ⇒ 一旦给别的域加 `DOMAIN_CFG` 条目 ⇒ 读的还是 task 的 surefire 报告。
    d = os.path.join(root, java_root, module, 'target/surefire-reports')
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


# ====================================================================
# N13 · 台账里**对该文件的状态叙述**（已删除 / 不存在 / 0 字节占位 …）
#     ⇒ 这类引用是**历史记述**，不是「指向一个缺失文件的悬空引用」
# ====================================================================
# 🔴 **词表必须窄**（PIT-GLOBAL-040 形态⑤ 的同族教训）：
#    **宽词表会把真缺陷一起吃掉** ⇒ 假阴性比假阳性危险得多。
#    ⇒ 下面**分三层**，每层都必须**自带「这确实是状态叙述」的语义**，不靠上下文猜。
#
# ── A 层：存在性词 —— 已删除 / 不存在 / 0 字节占位 … ──
#    （19 轮台账实战验证过，**本轮不动**）
N13_NARRATION = re.compile(
    r'(删除|已删除|不存在|已移除|被删|不再存在|不建|未建|不创建|0\s*字节|错放|空文件|占位)')

# ── B 层：「落地态」措辞 —— 自带语义：**这个文件还没落到主干 / 还没合入** ──
#    ⇒ 真缺陷（路径写错 / 笔误）**不会**这样描述 ⇒ 可以安全进表。
N13_LANDING = re.compile(
    r'(待合入|未合入|尚未合入|还没合入|待合回|未合回|尚未合回'
    r'|主干未合|主仓未合|尚未存在|还没存在|尚未落地)')

# ── C 层：「免责」措辞 —— 自带语义：**作者明说这不是错误** ──
#    ⇒ 这是 users/user 域 2026-10-26 报的「悬空 / 查不到 / 预期状态」**唯一安全的写法**：
#       裸词「查不到 / 悬空」**单独出现不算**（真缺陷恰恰这么写），
#       必须带上「是预期状态 / 不是台账事实错误」这类**免责半句**才算。
N13_DISCLAIM = re.compile(
    r'(是预期状态|属预期状态|符合预期'
    r'|不是(?:台账)?(?:事实)?(?:错误|缺陷)|非(?:台账)?事实错误'
    r'|属正常(?:状态|现象))')

# 🔴 **护栏**：行里出现这些 ⇒ 作者在讲「**引用本身错了**」⇒ **B/C 层一律不生效**。
#    （否则「`X.java` 路径写错 ⇒ 待合入主干」这种混合行会把真缺陷吃掉 ⇒ 假阴性。）
#    ⚠️ 护栏**只挡 B/C 层**，**不挡 A 层** —— A 层是既有的、已验证的行为，不在本轮改动范围。
N13_DEFECT_MARK = re.compile(
    r'(路径(?:写错|错了|错误)|拼错|笔误|行号真错|应为|应当是)')


def _is_n13_narration(line):
    """一行文本是不是「**对该文件的状态叙述**」。

    - **A 层**（存在性词）直接判定；
    - **B / C 层**（落地态 / 免责）**先过护栏** —— 行里带「路径写错」这类
      **缺陷标记**时一律不算（宁可报缺，也不吃真缺陷）。
    """
    if N13_NARRATION.search(line):
        return True
    if N13_DEFECT_MARK.search(line):
        return False
    return bool(N13_LANDING.search(line) or N13_DISCLAIM.search(line))


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

    ⚠️ **叙述词本身分三层**（见 `_is_n13_narration`）：A 层（存在性词）直接判定；
    B 层（落地态）/ C 层（免责）**先过缺陷标记护栏** —— 行里出现「路径写错」这类
    **缺陷标记**时，B/C 层**一律不生效**（宁可报缺，也不吃真缺陷）。

    返回 (是否命中, 命中的行号（1-based）)。
    """
    if not lines:
        return False, 0
    for i in range(max(0, ln - window), min(len(lines), ln + window + 1)):
        line = lines[i]
        if not _is_n13_narration(line):
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


def layer2_claims(root, domain):
    print('=' * 78)
    print('第 2 层 · 台账「已修」断言 → 仓库里能不能查到对应物')
    print('=' * 78)
    # 🔴 **必须按域取断言清单**（第三十五轮 · users/user 域实测带出）——
    #    原来这一层**完全没有域参数** ⇒ `--domain users` 跑出来的第 2 层
    #    **仍然是 task 域的结论**（实测：三个域的输出都是「10/10 OK（断言全是 task 域）」）
    #    ⇒ 读者会把 **task 域的缺陷当成自己域的** ⇒ **结论不可用**。
    #    ⇒ 某个域**没有**清单 ⇒ 明确打印「⏭ 不适用于本域」并跳过（**不是「通过」**）。
    claims = CLAIMS_BY_DOMAIN.get(domain)
    if not claims:
        print(f'  ⏭ 本层的「已修断言」清单只覆盖 {sorted(CLAIMS_BY_DOMAIN)} 域'
              f' ⇒ **{domain} 域不适用**（⚠️ 不是「通过」—— 是「没查」）')
        return 0
    bad = 0
    for desc, path, must_re, min_count, forbid_re in claims:
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
    print(f'  ⇒ {len(claims) - bad}/{len(claims)} 条断言有对应物')
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


def _git_rc(root, *args):
    """git 命令的**退出码**（不取输出）"""
    return subprocess.run(['git'] + list(args), cwd=root,
                          capture_output=True).returncode


def _commit_exists(root, h):
    """该哈希是不是仓库里**真实存在**的 commit"""
    return _git_rc(root, 'cat-file', '-e', f'{h}^{{commit}}') == 0


def _repo_topo_order(root):
    """仓库里所有 commit 的拓扑序（**一次** git 调用 ⇒ 452 个 commit 约 70ms）。

    ⚠️ `--topo-order` 输出是**新 → 旧** ⇒ 下标越大 = 越老。
    """
    return [h[:7] for h in
            git(root, 'rev-list', '--topo-order', '--all').splitlines()]


def manifest_chain(root, keys):
    """登记的 commit 哈希 ⇒ 它们在 git 里的**祖先链**。

    返回 `(oldest, newest, span_keys)`；不构成一条链时返回 `(None, None, None)`。

    🔴 **为什么不能用「与主干的公共祖先」当窗口**（第三十五轮 · users/user 域实测带出）——
    原口径是 `merge-base HEAD do-my-own-gh` ⇒ **主干上 `HEAD == do-my-own-gh`**
    ⇒ 公共祖先 == HEAD ⇒ `rev-list HEAD..HEAD` = **空** ⇒ 44 个登记哈希**全部**被判
    「不在本分支上」+ 1 条行数不守恒 = **45 处假阳性** ⇒ ⇒ **一个恒红的守门等于没有守门**。
    ⇒ **改成「登记的哈希自己构成的链」** ⇒ **不依赖任何分支** ⇒ 跨分支一致。

    @param keys  登记的哈希（7 位前缀）列表
    """
    keys = [k[:7] for k in keys if k]
    if not keys:
        return None, None, None
    order = _repo_topo_order(root)
    pos = {h: i for i, h in enumerate(order)}
    if any(k not in pos for k in keys):
        return None, None, None          # 有哈希不在这份拓扑序里（不存在 / 悬空）
    # `--topo-order` 是**新 → 旧** ⇒ 下标最大 = 最老
    oldest = max(keys, key=lambda k: pos[k])
    newest = min(keys, key=lambda k: pos[k])
    # ⚠️ `--no-merges` 与原窗口口径保持一致（实测 task 域区间内无 merge ⇒ 两种口径同值）
    span = git(root, 'rev-list', '--abbrev-commit', '--no-merges',
               f'{oldest}^..{newest}').splitlines()
    if not span:
        # oldest 是根提交（没有父）⇒ `^..` 退化 ⇒ 退回 `..` 并把 oldest 补回去
        span = [oldest] + git(root, 'rev-list', '--abbrev-commit', '--no-merges',
                               f'{oldest}..{newest}').splitlines()
    return oldest, newest, {s[:7] for s in span}


def check_manifest_integrity(root, rows):
    """台账 commit 清单的**域无关**判据（**不依赖任何分支口径**）。

    ① 占位符残留（只允许 1 个，且必须在末尾）
    ② 同一个 commit 被写成多行
    ③ 登记的哈希在仓库里**必须存在**（抓「台账引述了一个不存在的 SHA」）
    ④ 登记的哈希必须构成**连续祖先链**（区间内零漏零多 ⇒ 抓「跳号漏登」与「多登」）

    返回 (problems, notes)。
    """
    problems, notes = [], []
    hash_rows = [r for r in rows if HASH_RE.match(r)]
    ph_rows = [r for r in rows if PLACEHOLDER in r]
    keys = [r[:7] for r in hash_rows]
    dedup = set(keys)

    # ① 占位符残留
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

    # ③ 登记的哈希必须真实存在
    if dedup:
        ghost = sorted(h for h in dedup if not _commit_exists(root, h))
        if ghost:
            problems.append(f'commit 清单里登记了 **{len(ghost)} 个仓库里不存在的哈希**'
                            f'（读者点进去是空的）: {ghost[:5]}')
        else:
            notes.append(f'登记的 {len(dedup)} 个哈希在仓库里**全部存在**')

    # ④ 登记的哈希必须构成连续祖先链
    if dedup:
        old, new, span = manifest_chain(root, sorted(dedup))
        if old is None:
            problems.append('登记的哈希**无法构成一条祖先链**'
                            '（有哈希不在这份仓库的拓扑序里 ⇒ 登记了不存在的 commit）')
        else:
            missing = sorted(span - dedup)
            extra = sorted(dedup - span)
            if missing or extra:
                problems.append(f'登记的 commit **不构成连续链**：区间 `{old}..{new}` '
                                f'共 {len(span)} 个 commit，登记 {len(dedup)} 个 '
                                f'⇒ 漏登 {len(missing)} 个 / 多登 {len(extra)} 个'
                                + (f'（漏登示例 {missing[:3]}）' if missing else '')
                                + (f'（多登示例 {extra[:3]}）' if extra else '')
                                + '（⚠️ 若该域台账登记的是**多条分支**的 commit '
                                  '⇒ 需按分支拆开登记，否则本判据不适用）')
            else:
                notes.append(f'登记的 {len(dedup)} 个 commit 构成**连续链**'
                             f'（`{old}..{new}` 区间零漏零多）')
    return problems, notes


def check_manifest_unregistered(hashes, keys):
    """「分支上有、台账没登记」的 commit（**分支相关** ⇒ 只在域与分支自洽时启用）。

    返回 (problems, notes)。纯函数 ⇒ 可用人造样本验证它有区分力。
    """
    problems, notes = [], []
    dedup = {k[:7] for k in keys}
    missing = sorted({h[:7] for h in hashes} - dedup)
    for h in missing:
        problems.append(f'commit `{h}` **完全没登记**（既不在清单里，也不是 HEAD）')
    if not missing:
        notes.append(f'窗口内 {len({h[:7] for h in hashes})} 个 commit 全部已登记')
    return problems, notes


def layer3_numbers(root, texts, domain):
    print('=' * 78)
    print('第 3 层 · 数字断言（实测 vs 台账）')
    print('=' * 78)
    # 🔴 **必须按域取配置**（第三十五轮 · users/user 域实测带出）——
    #    原来这一层把「surefire 模块名 / 台账路径 / 编号前缀 / commit 清单表」**全写死成 task 域**
    #    ⇒ `--domain users` 跑出来的第 3 层**仍然是 task 域的结论**
    #    ⇒ 读者会把 task 域的缺陷当成自己域的 ⇒ **结论不可用**。
    cfg = DOMAIN_CFG.get(domain)
    if cfg is None:
        print(f'  ⏭ 本层的判据**尚未为 `{domain}` 域定义**（台账格式不同：'
              f'编号前缀 / 章节结构 / commit 清单表都不一样）')
        print(f'     ⇒ **{domain} 域本层不适用**（⚠️ 不是「通过」—— 是「没查」）')
        print(f'     ⇒ 已定义的域：{sorted(DOMAIN_CFG)}')
        return 0
    modules = cfg['modules']
    java_root = cfg.get('java_root', 'service/task')
    rpc = surefire_counts(root, modules[0][0], java_root)
    api = surefire_counts(root, modules[1][0], java_root)
    txt = ''.join(texts.values())
    bad = 0

    if rpc:
        print(f'  实测 {modules[0][0]}: {rpc}')
    if api:
        print(f'  实测 {modules[1][0]}: {api}')

    m = re.search(r'\*\*(\d+) tests / 0 failures / 0 errors / 0 skipped\*\*', txt)
    claimed = int(m.group(1)) if m else None
    actual = rpc['tests'] if rpc else None
    if claimed is not None and actual is not None:
        good = claimed == actual
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} rpc.md 测试数: 台账 {claimed} / 实测 {actual}')

    a = re.search(r'已修 \| \*\*(\d+) 条\*\*（' + cfg['drift_prefix'], txt)
    b = re.search(r'## 一、已修（(\d+) 条）', txt)
    if a and b:
        good = a.group(1) == b.group(1)
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} 已修条数两处一致: §〇={a.group(1)} §一标题={b.group(1)}')

    ids = sorted(set(int(x) for x in re.findall(cfg['drift_prefix'] + r'-(\d{3})', txt)))
    if ids:
        good = ids == list(range(1, len(ids) + 1))
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} {cfg["drift_prefix"]} 编号连续: {ids}')

    # ⚠️ **api 域要单独查** —— §〇 的「已修 N 条」与 §一 的实际条数必须对得上
    #    （本轮实测抓到过：api.md §〇 写「已修 3 条」而 §一 标题写「已修（2 条）」，
    #      而 §一 下面实际有 4 条 ⇒ **标题与内容不符**）
    api_md = read(os.path.join(root, cfg['api_md']))
    if api_md:
        m_fixed = re.search(r'已修 \| \*\*(\d+) 条\*\*（' + cfg['api_prefix'], api_md)
        # ⚠️ 标题的措辞会变（「已修（N 条）」/「逐条对照（N 条：…）」）
        #    ⇒ 只抓「## 一、…（N 条」里的那个 N，**不要**锚定具体措辞
        m_sec = re.search(r'^## 一、[^\n（]*（(\d+) 条', api_md, re.M)
        m_items = len(re.findall(r'^### .*' + cfg['api_prefix'] + r'-\d{3}', api_md, re.M))
        if m_sec and m_items:
            good = int(m_sec.group(1)) == m_items
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} api.md §一 标题条数: 声明 {m_sec.group(1)} / 实际小节 {m_items}')
        elif m_items:
            print(f'  ⚠️ api.md §一 标题里没解析出条数（实际小节 {m_items} 个）⇒ 标题措辞可能又变了')
        if m_fixed:
            ids_api = sorted(set(int(x) for x in re.findall(cfg['api_prefix'] + r'-(\d{3})', api_md)))
            if ids_api:
                good = ids_api == list(range(1, len(ids_api) + 1))
                bad += 0 if good else 1
                print(f'  {"✅" if good else "❌"} {cfg["api_prefix"]} 编号连续: {ids_api}')

    # ── 三档台账的**编号连续性**与**悬空引用**（「最全」维度）──
    #    ⚠️ 这两项查的是「台账之间**互相引得到**」⇒ 引用一个不存在的编号 = 读者点进去是空的
    all_text = '\n'.join(texts.values())
    pit_text = read(os.path.join(root, cfg['pit_md']))
    if pit_text:
        # ⚠️ Pitfall 的编号是**两位**（`PITFALL-TASK-RPC-01`），不是 DRIFT 的三位
        #    ⇒ 正则用 `\d{2,3}`，且「已定义」直接取**文件里出现过的字符串**
        #    （不按序号重建 —— 重建会在位数不一致时错判）
        ids = sorted(set(int(x) for x in re.findall(cfg['pit_prefix'] + r'-(\d{2,3})', pit_text)))
        good = bool(ids) and ids == list(range(1, len(ids) + 1))
        bad += 0 if good else 1
        print(f'  {"✅" if good else "❌"} {cfg["pit_prefix"]} 编号连续: '
              f'{ids[0]}~{ids[-1]}（{len(ids)} 条）')
        defined = set(re.findall(cfg['pit_prefix'] + r'-\d{2,3}', pit_text))
        refs = set(re.findall(cfg['pit_prefix'] + r'-\d{2,3}', all_text))
        dangling = sorted(refs - defined)
        if dangling:
            bad += 1
            print(f'  ❌ 台账引用了**不存在**的 Pitfall: {dangling}')
        else:
            print(f'  ✅ 台账引用的 {len(refs)} 个 Pitfall ID 全部有定义')
    sd_text = read(os.path.join(root, cfg['dev_md']))
    if sd_text:
        defined = set(re.findall(cfg['dev_prefix'], sd_text))
        refs = set(re.findall(cfg['dev_prefix'], all_text))
        dangling = sorted(refs - defined)
        if dangling:
            bad += 1
            print(f'  ❌ 台账引用了**不存在**的签字偏离: {dangling}')
        else:
            print(f'  ✅ 台账引用的 {len(refs)} 个签字偏离 ID 全部有定义'
                  f'（偏离档共定义 {len(defined)} 条）')

    for name, r in ((modules[0][0], rpc), (modules[1][0], api)):
        if r:
            good = r['failures'] == 0 and r['errors'] == 0 and r['skipped'] == 0
            bad += 0 if good else 1
            print(f'  {"✅" if good else "❌"} {name} failures/errors/skipped 全 0')
            if not good:
                print(f'       ⚠️ skipped={r["skipped"]} ⇒ 「全绿」可能意味着「没跑」')

    # ── commit 清单检查：**拆成「域无关」与「分支相关」两段**（第三十五轮）──
    #   🔴 **原口径的根因**：`merge_base = git(root,'merge-base','HEAD','do-my-own-gh')`
    #      ⇒ **主干上 `HEAD == do-my-own-gh`** ⇒ 公共祖先 == HEAD ⇒ 窗口 = **空**
    #      ⇒ 44 个登记哈希**全部**被判「不在本分支上」+ 1 条行数不守恒 = **45 处假阳性**
    #      ⇒ 而在 `align/global` 上窗口非空 ⇒ 变成**反向**的 141 处假阳性
    #      ⇒ ⇒ **一个恒红 / 恒漂的门禁等于没有门禁**。
    #   ✅ **新口径**：
    #      ① **域无关**（任何分支都跑）：占位符残留 / 同一 commit 多行 /
    #         登记的哈希**必须存在** / 登记的哈希必须构成**连续祖先链**
    #      ② **分支相关**（只在「当前分支 == 台账自述的分支」时启用）：漏登记
    #         ⇒ 因为「别的域的 commit 该不该被本域登记」**是台账作者的口径，工具判不了**
    branch = git(root, 'rev-parse', '--abbrev-ref', 'HEAD')
    readme = read(os.path.join(root, cfg['readme']))
    rows = commit_manifest_rows(readme)
    if not rows:
        print(f'  ℹ️  {cfg["readme"]} 里**没有 commit 清单表** ⇒ commit 清单检查不适用')
    else:
        problems, notes = check_manifest_integrity(root, rows)
        for n in notes:
            print(f'  ℹ️  {n}')

        # ── ② 分支相关判据：只在「当前分支 == 台账自述的分支」时启用 ──
        m_branch = re.search(r'分支\s*`([^`]+)`', readme)
        declared = m_branch.group(1) if m_branch else None
        if declared and declared != branch:
            print(f'  ⏭ 台账自述的分支是 `{declared}`，当前分支是 `{branch}`'
                  f' ⇒ **漏登记检查不适用**（⚠️ 不是「通过」—— 在别人的分支上，'
                  f'别的域的 commit 本来就不该被本域登记）')
        elif not declared:
            print(f'  ⏭ 台账里**没有自述分支** ⇒ 漏登记检查不适用（⚠️ 不是「通过」）')
        else:
            old, new, span = manifest_chain(root, sorted(
                {r[:7] for r in rows if HASH_RE.match(r)}))
            if old is None or new is None:
                print('  ⏭ 登记的哈希无法构成链 ⇒ 漏登记检查不适用')
            else:
                window = [h.strip() for h in git(
                    root, 'rev-list', '--abbrev-commit', '--no-merges',
                    f'{new}..HEAD').splitlines() if h.strip()]
                p2, n2 = check_manifest_unregistered(
                    window, [r[:7] for r in rows if HASH_RE.match(r)])
                for n in n2:
                    print(f'  ℹ️  {n}')
                problems += p2

        if problems:
            bad += len(problems)
            print(f'  ❌ commit 清单有 {len(problems)} 处缺陷（分支 {branch}）：')
            for p in problems:
                print(f'      · {p}')
        else:
            print(f'  ✅ commit 清单检查通过（分支 {branch}，登记 {len(rows)} 行）')
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


# ── 标准库 **basename** 索引（懒建 + 进程内缓存）────────────────────────────
_STDLIB_INDEX_CACHE = {}


def _stdlib_index(goroot):
    """`<GOROOT>/src` 下的「basename → 完整路径」索引。

    🔴 **为什么需要这条通道（2026-10-26 第四十八轮实测带出）**：
       `_stdlib_candidate` 只能解析**带包目录**的写法（`strconv/quote.go` ⇒
       `<GOROOT>/src/strconv/quote.go`）⇒ 台账里写成**裸 basename**
       （`quote.go:522-563` / `context.go:625`）时**必然解析不到**
       ⇒ 落到「文件在真源与全部第三方库里都找不到」⇒ **假 FAIL**。
       实测：article 域 `docs/DRIFT/article/api.md` 有 6 处裸 `quote.go:NNN`，
             common 域 `docs/DRIFT/common/alignment-2026-10-23.md` 有 1 处裸 `context.go:625`
             ⇒ 全部是**假 FAIL**（文件确实存在，只是没带包目录）。

    ⚠️ **必须排除 `cmd/**`** —— 那是 Go **工具链自己的源码**（`cmd/go/internal/...`、
       `cmd/vendor/...`），**不是用户代码能 import 的标准库** ⇒ 计进去会凭空造出
       「同名文件跨库歧义」（实测 stdlib 里 `context.go` 有 7 个，排除 `cmd/**` 后剩 3 个：
       `context/` · `go/types/` · `html/template/`）。
    """
    if not goroot:
        return {}
    if goroot in _STDLIB_INDEX_CACHE:
        return _STDLIB_INDEX_CACHE[goroot]
    idx = {}
    src_root = os.path.join(goroot, 'src')
    if os.path.isdir(src_root):
        for dp, _, fns in os.walk(src_root):
            rel_dir = os.path.relpath(dp, src_root)
            if rel_dir == 'cmd' or rel_dir.startswith('cmd' + os.sep):
                continue
            for fn in fns:
                if fn.endswith('.go'):
                    idx.setdefault(fn, []).append(os.path.join(dp, fn))
    _STDLIB_INDEX_CACHE[goroot] = idx
    return idx


def _file_line_count(path):
    """文件行数（读不出来就当 0 行 ⇒ 行号必然越界）。"""
    try:
        with open(path, encoding='utf-8', errors='replace') as fh:
            return sum(1 for _ in fh)
    except Exception:
        return 0


def _external_anchor(path, go_root):
    """这条锚点**不可能**属于 Go 真源 ⇒ 极可能指向**真源之外的另一个仓库**。

    🔴 **为什么要有这条通道**（2026-10-26 第四十八轮实测带出）：
       like 域台账里有一批 `service/async/rpc/...` 的锚点，而**冻结仓里根本没有
       `service/async/` 这个目录** ⇒ 那些锚点指向的是**第三个仓库**
       （like 域台账自己写明了：`Evidence（`Sea-BreakTheWaves` 侧 = 下游消费者）`）
       ⇒ ⇒ 而 `Sea-BreakTheWaves` **本机不存在** ⇒ ⇒ **任何工具都无法核验它们**
       ⇒ ⇒ 报成「文件在真源与全部第三方库里都找不到」= **假 FAIL**
       ⇒ ⇒ 而**假 FAIL 比不报更坏**（别的域会以为自己的台账坏了，而那不是他们的责任）。

    ⇒ **判据**：路径在 `service/` 下 ⇒ 取它的**首段目录名** ⇒ 该目录在真源里**不存在**
      ⇒ ⇒ 它**不可能**是真源锚点 ⇒ ⇒ 归入「外部仓库锚点」桶（**不计 FAIL**，但**要列出**）。
    ⇒ ⚠️ **边界**：只动**首段目录不存在**的情况 ⇒ 首段写错（`asnyc`）也会落进来
      ⇒ ⇒ 所以这个桶**必须列出来**（提示级）⇒ 不能静默吞掉。
    """
    parts = path.split('/')
    if len(parts) < 3 or parts[0] != 'service':
        return False
    first = parts[1]
    if not first:
        return False
    return not os.path.isdir(os.path.join(go_root, 'service', first))


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


# ── 第 4 层补充判据：锚点文件里「找不到」台账断言提到的那个实体 ──────────
#
# 🔴 **为什么要有这一层**（favorite 域第二十二轮实测带出 · 2026-10-26）：
#   既有第 4 层只判「文件存在 + 行号在范围内」
#   ⇒ ⇒ **「行号在范围内」≠「锚点指对了文件」**
#   ⇒ ⇒ 一条指向**另一个文件**、但行号恰好合法的锚点 ⇒ **既有层判它「不悬空」⇒ 放过去**
#   ⇒ ⇒ 而此时台账里那句话的**证据是假的** —— 比没写锚点更危险。
#   favorite 域实测：20 个 Go 锚点逐条回真源核对 ⇒ 18 条正确 · **2 条错锚点**
#   （其一：写成 logic 层的 `delete_favorite_folder_logic.go:336-378`，
#     但该文件只有 96 行 ⇒ 实际指的是 model 层的 `favorite_fact.go:336-378`（`DeleteFolderCascade`）
#     ⇒ 断言语义对、锚点指错文件）。
#
# ⚠️ **本层是「提示级」（不计 FAIL）**：判据要靠「从自然语言里猜台账说的是哪个实体」
#   ⇒ 天然有假阳性 ⇒ 只提示、不失败。
# ⚠️ **候选窗口必须窄**：global 的台账单元格里**一行可能有 10 KB**
#   ⇒ 用「整行」做候选提取 ⇒ 引入大量无关符号 ⇒ 判据永远不触发（等于没有）。
#   ⇒ 只取「引用位置 ±width 字符」。

# 出现频率极高、不能当「实体指纹」的词（Go 关键字 / 通用局部变量名）
_ENTITY_STOPWORDS = frozenset('''
    ctx err nil func return error string int int64 int32 bool byte rune
    true false new make len cap copy append panic recover defer go select
    case default switch range var const type interface struct map chan
    import package break continue else fallthrough goto package init
    timeout cancel done chan errs req resp res data buf msg value result
    size count index idx key name path file line num total start end
'''.split())

# 「有辨识度的符号」：**必须含「小写段 → 大写」的边界，且小写段至少 2 个字母**
#   ⇒ `GetTaskHandler` ✔ · `appendAuthHandler` ✔ · `completeLikeGT5` ✔
#   ⇒ 全大写缩写（`HTTP` / `OTel`）✘（实测：它们在真源里也到处被声明
#     ⇒ 放进候选 ⇒ 假阳性）
#   ⚠️ 「至少 2 个小写字母」这一条是第三轮收紧补上的 ——
#     只要求「有边界」的话，`HTTP` 会被拆成 `H` + `TTP` ⇒ 仍然匹配 ⇒ 假阳性。
_DISTINCTIVE_RE = re.compile(r'^[A-Za-z_][a-z]{2,}[A-Z][A-Za-z0-9]*$')


def _local_windows(root, hits, path, s, e, width=45):
    """取「引用所在位置 ±width 字符」的文本窗口（同一引用的多个出现点合并）。

    ⚠️ **不用整行** —— global 的台账单元格一行可达 10 KB
    ⇒ 整行做候选提取会引入大量无关符号 ⇒ 判据永远不触发。
    ⚠️ **窗口必须窄（45 字符，实测校准）** —— 台账里常出现
       「`a.go:55` 的 X | Y | **消费点**：`Z(...)`」这种**一行多事实**的表格行
    ⇒ 窗口宽 ⇒ 把**别处**的符号也算成「锚点处的实体」⇒ 假阳性。
    """
    pat = re.compile(re.escape(path) + r':(\d+)(?:-(\d+))?')
    out = []
    for sf in sorted(hits):
        lines = read(os.path.join(root, sf)).splitlines()
        for ln in sorted(hits[sf]):
            if ln - 1 >= len(lines):
                continue
            line = lines[ln - 1]
            for m in pat.finditer(line):
                ms = int(m.group(1))
                me = int(m.group(2)) if m.group(2) else ms
                if (ms, me) != (s, e):
                    continue
                lo = max(0, m.start() - width)
                hi = min(len(line), m.end() + width)
                out.append(line[lo:hi])
    return '\n'.join(out)


# 「断言包含」的措辞标记 —— 🔴 **台账必须「断言这个符号在这个文件里」才参与判定**
#   否则「一句话里同时出现多个锚点和多个符号、且它们分属不同文件」的**清单式写法**
#   会持续假阳性（实测：「`a.go:95,99,103`；…；`completeLikeGT5` 的 `SetNX` 未命中早退」
#   ⇒ `completeLikeGT5` 属于另一个文件，但那句话**没有**断言它在前一个锚点文件里）。
_CONTAINMENT_MARK = re.compile(
    r'(实现|处的|里的|中的|声明|定义|定义在|写在|位于)')


def _entity_candidates(text):
    """从台账窗口里提取「断言提到的实体」候选（Go 符号名）。

    ⇒ 只认**反引号包裹**的片段（那是台账引用代码/符号的惯用写法）
    ⇒ 🔴 **带点（`pkg.Symbol`）的片段一律跳过** ——
      实测（users / users-admin 域）：台账常写
      「`rest/engine.go:154-156` 的原生 `TimeoutHandler` 被 `observability.DisableNativeRestTimeout`」
      ⇒ 带点的那个**明确属于另一个包** ⇒ 它是**跨文件对照**，不是「锚点处的实体」
      ⇒ 取最后一段当候选 ⇒ **必假阳性**。
    ⇒ 片段里若含 `:` `/` `\\` ⇒ 是路径 / file:line / URL ⇒ 跳过
    ⇒ ⚠️ **只保留「台账断言了包含关系」的候选**（见 `_CONTAINMENT_MARK`）
      —— 窗口里出现一个符号 ≠ 台账说它在这个锚点文件里。
    """
    cands = []
    for span_m in re.finditer(r'`([^`]+)`', text):
        span = span_m.group(1)
        if ':' in span or '/' in span or '\\' in span:
            continue
        if '.' in span:
            # ⚠️ **带点的限定名 ⇒ 跨文件/跨包引用** ⇒ 不作为「锚点处的实体」
            continue
        if span.endswith(('.go', '.java', '.md', '.json', '.yml', '.yaml', '.xml')):
            continue
        for m in re.finditer(r'[A-Za-z_][A-Za-z0-9_]*', span):
            seg = m.group(0)
            if len(seg) < 4 or seg in _ENTITY_STOPWORDS:
                continue
            # ⚠️ **必须是有辨识度的符号**（实测收紧过程）：
            #   第一版（只看「长度 ≥ 4」）⇒ 假阳性 ~90%：普通英文词
            #   （`enabled` / `offset`）只要在真源某处被声明过就会被判成「指错文件」。
            #   第二版（要求首字母大写）⇒ 漏掉 `appendAuthHandler` 这类**小写开头的 Go 函数名**。
            #   第三版（要求有「小写段→大写」边界）⇒ 漏掉全大写缩写？⇒ 再收紧为
            #   「小写段至少 2 个字母」⇒ `HTTP` / `OTel` 全部出局。
            # ⇒ 现在的口径：**小写段 ≥2 字母 + 紧接一个大写字母**
            #   （`GetTaskHandler` ✔ · `appendAuthHandler` ✔ · `completeLikeGT5` ✔ · `HTTP` ✘）
            if not _DISTINCTIVE_RE.match(seg):
                continue
            # ⚠️ **必须「台账断言了包含关系」** —— 符号前 24 字符里要有包含措辞
            #   （「实现」「处的」「里的」「中的」「声明」「定义」…）
            #   🔴 **必须用绝对位置** —— `m.start()` 是**片段内**偏移，
            #      直接拿去切 `text` ⇒ 切到的是**窗口开头** ⇒ 判据恒不成立（实测踩过）
            pos = span_m.start(1) + m.start()
            pre = text[max(0, pos - 24):pos]
            if not _CONTAINMENT_MARK.search(pre):
                continue
            if seg not in cands:
                cands.append(seg)
    return cands


def _symbol_index(go_root, limit_dirs=('service',)):
    """真源里的「声明符号 → 文件列表」索引（**懒建**：只有真的要用才建）。

    ⇒ 只收**声明处**的符号（func / type / 单行 var / const），
      不是全文所有标识符 ⇒ 索引小、且「这个符号在哪个文件声明过」才有意义。
    """
    idx = {}
    decl = re.compile(
        r'^\s*(?:func\s+(?:\([^)]*\)\s*)?|type\s+|var\s+|const\s+)([A-Za-z_][A-Za-z0-9_]*)',
        re.M)
    for sub in limit_dirs:
        base = os.path.join(go_root, sub)
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if not fn.endswith('.go'):
                    continue
                full = os.path.join(dp, fn)
                rel = os.path.relpath(full, go_root)
                for m in decl.finditer(read(full)):
                    idx.setdefault(m.group(1), set()).add(rel)
    return idx


def layer4_entity_check(root, resolved, go_root):
    """第 4 层补充判据：锚点文件里**找不到**台账断言提到的那个实体。

    🔴 **收紧后的判据（第一版太宽 ⇒ 假阳性 ~90% ⇒ 不可用）**：
       只在**候选实体是 Go 真源里真实「声明过」的符号**时才参与判定。
       ⇒ 于是这些**自动出局**（不是 Go 声明 ⇒ 不归这一层管）：
         · **Java 类名**（`TaskDomainService` / `TaskController` / `TaskApiWiringTest`）——
           交叉栈台账里「Go 侧 X ⇔ Java 侧 Y」的对照写法极常见；
         · **非代码词**（`XADD` / `yaml` / `Java` / `Boot` / `OTel`）；
         · **字段名 / 局部变量名**（`taskID` / `articleDoneTTL` 这类若只是被引用、
           并非在本文件声明 ⇒ 不构成「指错文件」的证据）。
       ⇒ 判据收紧成：**「台账说这个锚点处是符号 X；X 在真源里被声明过（且声明处唯一）；
         但 X 不在这个锚点文件里」⇒ 锚点很可能指错了文件。**

    @param resolved  [(路径, 起行, 止行, [候选文件路径], [台账文件], {台账文件: 行号集合})]
    @return [(路径, 起行, 止行, 缺失候选, 实际所在文件列表, 台账文件列表)]
    """
    hints = []
    sym_idx = None
    for path, s, e, files, srcs, hits in resolved:
        if not files:
            continue
        win = _local_windows(root, hits, path, s, e)
        if not win:
            continue
        cands = _entity_candidates(win)
        if not cands:
            continue
        if sym_idx is None:
            sym_idx = _symbol_index(go_root)
        # ── 只保留「真源里真实声明过、且声明处唯一」的候选 ──
        strong = [c for c in cands if len(sym_idx.get(c, ())) == 1]
        if not strong:
            continue
        blob = '\n'.join(read(f) for f in files)
        missing = [c for c in strong if c not in blob]
        # ⚠️ **必须「全部缺失」才提示** —— 只要有一个候选在文件里 ⇒ 锚点大概率是对的
        if not missing or len(missing) != len(strong):
            continue
        where = ['%s（实际在 %s）' % (c, sorted(sym_idx[c])[0]) for c in missing]
        hints.append((path, s, e, missing, where, srcs))
    return hints


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
    # ⚠️ **标准库 basename 索引单独建，不并进 `idx`** ——
    #    并进去 ⇒ 标准库同名文件会与真源/第三方库的同名文件**混在一个池子**里
    #    ⇒ 「跨库歧义」的口径被污染（既有计数会变）⇒ 必须**独立成池**。
    std_idx = _stdlib_index(goroot)

    refs = {}
    for f, t in texts.items():
        for m in REF_RE.finditer(t):
            key = (m.group(1), int(m.group(2)), int(m.group(3)) if m.group(3) else int(m.group(2)))
            # ⚠️ **必须记住「引用出现在台账的第几行」** —— 第 4 层的 N5/N7 去噪
            #    要按**行级局部**取上下文（否则文件里任意一处噪声词会吃掉全文件的引用）
            ln = t.count('\n', 0, m.start()) + 1
            refs.setdefault(key, {}).setdefault(f, set()).add(ln)

    ok, problems, ambiguous, noises = 0, [], [], []
    external = []          # ← 外部仓库锚点（真源 `service/` 下无该首段目录 ⇒ 不可核验）
    resolved = []   # ← 第 4 层补充判据用：(路径, 起行, 止行, [候选文件], [台账文件])
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
            # ⚠️ **噪声引用也要继续解析文件** —— 第 4 层的**补充判据**问的是
            #    「这条锚点指错了文件吗」，与「这条引用是否悬空」是**两个问题**
            #    ⇒ 噪声只该影响「不悬空」那套计数，**不该让补充判据失去覆盖**
            #    （实测：不这样改 ⇒ 注入在文件末尾的变异样本被相邻行的噪声词去噪
            #      ⇒ 补充判据**根本没看到它** ⇒ 变异假绿）
            noise_only = True
        else:
            noise_only = False
        # ── 精确定位通道 ①：`module@version/...` ⇒ 直接在 module cache 里找
        #    （basename 索引解析不到完整模块路径 ⇒ 假 FAIL；直接定位顺带消除同名歧义）
        direct = _direct_module_candidate(path, gomodcache)
        # ── 精确定位通道 ②：Go **标准库** ⇒ `<GOROOT>/src/...`
        if direct is None:
            direct = _stdlib_candidate(path, goroot)
        if direct is not None and os.path.isfile(direct):
            n = sum(1 for _ in open(direct, encoding='utf-8', errors='replace'))
            if s <= n and e <= n:
                resolved.append((path, s, e, [direct], srcs, hits))
                if not noise_only:
                    ok += 1
            elif not noise_only:
                problems.append((path, s, e, '行号超出文件范围', srcs))
            continue
        cands = idx.get(os.path.basename(path), [])
        matched = []
        matched_paths = []
        for lib, full in cands:
            n = _file_line_count(full)
            if s <= n and e <= n:
                matched.append(lib)
                matched_paths.append(full)
        # ── 标准库 basename 通道（第四十八轮）────────────────────────────
        #   真源 / 第三方库里**没有同名文件**时，去 `<GOROOT>/src` 里按 basename 找
        #   （台账常写裸 `quote.go:522-563` 而不是 `strconv/quote.go:522-563`）。
        #   ⚠️ **只在「模块池没有命中」时启用** —— 否则会给「真源/第三方库已唯一命中」
        #      的引用凭空加上标准库候选 ⇒ 凭空制造「跨库歧义」⇒ 改变既有计数口径。
        std_cands = []
        if not matched:
            std_cands = std_idx.get(os.path.basename(path), [])
            for full in std_cands:
                n = _file_line_count(full)
                if s <= n and e <= n:
                    matched.append('标准库:' + os.path.relpath(full, goroot))
                    matched_paths.append(full)
        if not matched:
            if not noise_only:
                if _external_anchor(path, go_root):
                    # 🌐 **外部仓库锚点** ⇒ 不计 FAIL，但要**列出来**（提示级）
                    external.append((path, s, e, sorted(srcs)))
                else:
                    why = '文件在真源与全部第三方库里都找不到'
                    if std_cands:
                        longest = max(_file_line_count(f) for f in std_cands)
                        why = ('行号超出文件范围（标准库有 %d 个同名文件，最长 %d 行）'
                               % (len(std_cands), longest))
                    elif cands:
                        why = '行号超出文件范围'
                    problems.append((path, s, e, why, sorted(srcs)))
            continue
        resolved.append((path, s, e, matched_paths, srcs, hits))
        if not noise_only:
            ok += 1
            # ⚠️ **歧义清单只统计非噪声引用** —— 与改动前口径保持一致
            #    （噪声引用仍会进 `resolved` ⇒ 补充判据照样覆盖它们
            #      ⇒ 但**不改变**「不悬空 / 已去噪 / 跨库歧义」三个既有计数）
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
    if external:
        print(f'  🌐 外部仓库锚点（真源 `service/` 下无该首段目录 ⇒ **本机不可核验** · 不计 FAIL）: '
              f'{len(external)} 条')
        print('     ⇒ 这些锚点指向**真源之外的另一个仓库**（like 域 = `Sea-BreakTheWaves`，本机不存在）')
        print('     ⇒ ⇒ **不是台账写错** ⇒ 但也**永远不可核验** ⇒ 需要该仓的 owner 说明它的出处')
        for path, s, e, srcs in external:
            print(f'      {path}:{s}{"-" + str(e) if e != s else ""}   ← {", ".join(srcs)}')
    # ── 第 4 层**补充判据**：锚点文件里找不到台账断言提到的那个实体 ──
    entity_hints = layer4_entity_check(root, resolved, go_root)
    if entity_hints:
        print(f'  ⚠️ 锚点文件里**找不到**断言提到的实体（提示级 · 不计 FAIL）: {len(entity_hints)} 条')
        print('     ⇒ 「行号在范围内」≠「锚点指对了文件」⇒ 这类锚点的证据是假的（比没写锚点更危险）')
        for path, s, e, missing, where, srcs in entity_hints:
            print(f'      {path}:{s}{"-" + str(e) if e != s else ""}  缺失实体: {", ".join(missing)}   ← {", ".join(srcs)}')
            for w in where:
                print(f'         ⇒ 该符号实际在 {w}')
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


def layer5_unmentioned(root, texts, domain):
    print('=' * 78)
    print('第 5 层 · 仓库里存在但台账从未点名的测试类（覆盖缺口）')
    print('=' * 78)
    # 🔴 **必须按域取源码根**（第四十三轮）：原来**写死 `service/task`**
    #    ⇒ 别的域跑这一层 ⇒ 输出的是 **task 域的类** ⇒ 别的域 owner 会误读成自己域的缺口
    #    ⇒ 未定义的域 ⇒ **显式跳过**（口径与第 2/3 层一致：**「不适用」≠「通过」**）
    roots = JAVA_ROOTS.get(domain)
    if roots is None:
        print(f'  ⏭ 本层的判据**尚未为 `{domain}` 域定义**（不知道该域的 Java 源码根）')
        print(f'     ⇒ **{domain} 域本层不适用**（⚠️ 不是「通过」—— 是「没查」）')
        print(f'     ⇒ 已定义的域：{sorted(JAVA_ROOTS)}')
        return 0
    mentioned = set()
    for t in texts.values():
        for m in CLASS_RE.finditer(t):
            mentioned.add(m.group(1))
    files = [f for r in roots for f in repo_java_files(root, r)
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
    print(f'  仓库内 {domain} 域测试类 {len(files)} 个 ⇒ 台账未点名 {len(unmentioned)} 个')
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

    # ②⁻ 层 1（2026-10-26 第三十七轮 · E61 · users/user 域实测带出）：N13 **新增 B/C 层**
    #     必须**两头都对** ——
    #     阳性①「待合入主干」：**落地态**措辞 ⇒ 命中
    #           （users/user 域报的「词表缺词」根因就在这一类 —— 作者写「还没合进主干」
    #            工具认不出 ⇒ 正常中文措辞写状态叙述仍被误报成悬空引用）
    #     阳性②「路径悬空是预期状态，不是台账事实错误」⇒ **免责**措辞 ⇒ 命中
    #           （⚠️ 这正是 users/user 域那条 `RpcInterceptorTimeoutDescriptionGateTest.java`
    #            被误报的**原文措辞** ⇒ 修完这行不再需要改写台账）
    #     阴性① 裸「查不到 / 悬空」**单独出现** ⇒ **不得**命中
    #           （⚠️ 真缺陷（路径写错）恰恰就是这么写的 ⇒ 裸词进表 = 假阴性）
    #     阴性② 行里带「路径写错」标记 ⇒ **B/C 层一律不生效**（护栏有牙）
    #     阴性③ C 层措辞出现在**密集行**里、但本路径不是该行第一个路径 ⇒ 不得命中
    _p13 = 'service/common/src/test/java/com/rag/common/observability/E61GateTest.java'
    _n13b_cases = [
        ([f'| 守门 | `{_p13}`（真 Netty server）<br>⚠️ 该文件**待合入主干** |'], 0, True,
         'B 层「待合入主干」⇒ 落地态措辞命中', _p13),
        ([f'| 守门 | `{_p13}`<br>⚠️ 主干上**查不到** ⇒ 路径**悬空**是**预期状态**，'
          f'不是台账事实错误 |'], 0, True,
         'C 层「是预期状态 / 不是台账事实错误」⇒ 免责措辞命中', _p13),
        (['| **E-d** | `tools/difftest/check-protection-layer.sh` 主干上查不到 |'], 0, False,
         '裸「查不到」单独出现 ⇒ 不得命中（真缺陷正是这么写的）',
         'tools/difftest/check-protection-layer.sh'),
        (['| **E-d** | `tools/difftest/check-protection-layer.sh` 路径写错 ⇒ 待合入主干 |'], 0, False,
         '带「路径写错」标记 ⇒ B/C 层不生效（护栏有牙）',
         'tools/difftest/check-protection-layer.sh'),
        ([f'| **E42** | ✅ `docs/DRIFT/global/_INDEX.md` 已修 | ④ `{_p13}` 路径写错 '
          f'⇒ 悬空是预期状态 |'], 1, False,
         'C 层措辞在密集行里、但本路径不是该行第一个路径 ⇒ 不得命中', _p13),
    ]
    for _lines, _ln, _want, _why, _p in _n13b_cases:
        _got, _at = _narration_in_lines(_lines, _ln, path=_p)
        if _got == _want:
            print(f'    ✅ 层 1 新增 B/C 层判定正确（{"命中" if _want else "不命中"}）：{_why}')
        else:
            print(f'    ❌ 层 1 新增 B/C 层判定错判：期望{"命中" if _want else "不命中"}（{_why}）')
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


    # ③′ 层 3（第三十五轮重写）：commit 清单判据 —— **域无关**与**分支相关**两段都要有区分力
    #    ⚠️ 域无关判据需要**真 git**（算链）⇒ 用**真台账 + 真仓库**做对照
    _readme_text = read(os.path.join(root, 'docs/DRIFT/task/_README.md'))
    _rows = commit_manifest_rows(_readme_text)
    _hash_rows = [r for r in _rows if HASH_RE.match(r)]
    _p, _n = check_manifest_integrity(root, _rows)
    if not _p:
        print(f'    ✅ 层 3 的 commit 清单**域无关判据**不误报'
              f'（真台账 {len(_rows)} 行通过）')
    else:
        print(f'    ❌ 层 3 的 commit 清单域无关判据**误报** ⇒ {_p}')
        bad += 1
    # ① 占位符残留（多一个）
    _p1, _ = check_manifest_integrity(root, _rows + ['（本轮）'])
    if any('占位符行' in x for x in _p1):
        print('    ✅ 层 3 能抓到「占位符残留」')
    else:
        print(f'    ❌ 层 3 **抓不到**「占位符残留」⇒ 变异测试没意义（{_p1}）')
        bad += 1
    # ② 同一个 commit 被写成多行
    if _hash_rows:
        _p2, _ = check_manifest_integrity(root, _rows + [_hash_rows[0]])
        if any('被登记了' in x for x in _p2):
            print('    ✅ 层 3 能抓到「同一个 commit 多行」')
        else:
            print(f'    ❌ 层 3 **抓不到**「同一个 commit 多行」（{_p2}）')
            bad += 1
    # ③ 登记了一个**仓库里不存在**的哈希
    _p3, _ = check_manifest_integrity(root, _rows + ['deadbee'])
    if any('不存在的哈希' in x for x in _p3):
        print('    ✅ 层 3 能抓到「登记了不存在的 commit」')
    else:
        print(f'    ❌ 层 3 **抓不到**「登记了不存在的 commit」（{_p3}）')
        bad += 1
    # ④ 链断裂（删掉链**中间**的一行 ⇒ 区间里就多出一个没登记的 commit）
    if len(_hash_rows) >= 3:
        _mid = _hash_rows[len(_hash_rows) // 2]
        _rows_no_mid = [r for r in _rows if r != _mid]
        _p4, _ = check_manifest_integrity(root, _rows_no_mid)
        if any('不构成连续链' in x for x in _p4):
            print('    ✅ 层 3 能抓到「链断裂（跳号漏登）」')
        else:
            print(f'    ❌ 层 3 **抓不到**「链断裂」（{_p4}）')
            bad += 1
    # ⑤ 分支相关段：漏登记（纯函数）
    _p5, _ = check_manifest_unregistered(['aaa1111', 'bbb2222'], ['aaa1111'])
    if any('完全没登记' in x for x in _p5):
        print('    ✅ 层 3 能抓到「漏登记」')
    else:
        print(f'    ❌ 层 3 **抓不到**「漏登记」（{_p5}）')
        bad += 1
    _p6, _ = check_manifest_unregistered(['aaa1111'], ['aaa1111'])
    if not _p6:
        print('    ✅ 层 3 的漏登记判据不误报（已登记的不报）')
    else:
        print(f'    ❌ 层 3 的漏登记判据**误报** ⇒ {_p6}')
        bad += 1

    # ⑤′ 层 2/3 的**域适配**（第三十五轮 · users/user 域实测带出）：
    #     别的域跑这两层 ⇒ 必须**显式跳过**，而不是拿 task 域的结论去报
    #     （users/user 域实测：三个 `--domain` 的第 2/3 层输出**完全相同**
    #      ⇒ 域筛选**根本没传到这两层** ⇒ 读者会把 task 域的缺陷当成自己域的）
    import contextlib as _ctx35
    import io as _io35
    _buf = _io35.StringIO()
    with _ctx35.redirect_stdout(_buf):
        _b2 = layer2_claims(root, 'users')
    if _b2 == 0 and '本层的「已修断言」清单只覆盖' in _buf.getvalue():
        print('    ✅ 层 2 在**没有断言清单**的域上显式跳过（不拿别的域的结论去报）')
    else:
        print('    ❌ 层 2 在别的域上**没有显式跳过** ⇒ 报的是别的域的结论')
        bad += 1
    _buf = _io35.StringIO()
    with _ctx35.redirect_stdout(_buf):
        _b3 = layer3_numbers(root, ledger_texts(root, 'users'), 'users')
    if _b3 == 0 and '本层的判据**尚未为' in _buf.getvalue():
        print('    ✅ 层 3 在**没有域配置**的域上显式跳过（不拿别的域的结论去报）')
    else:
        print('    ❌ 层 3 在别的域上**没有显式跳过** ⇒ 报的是别的域的结论')
        bad += 1

    # ⑤‴ 层 5/6 的**域适配**（第四十三轮 · users/admin 域 owner 回执带出）：
    #     这两层原来**写死 `service/task`** ⇒ 别的域跑它 ⇒ 打出的是 **task 域的类**
    #     ⇒ 别的域 owner 会误读成「我们域有 N 个覆盖缺口」
    #     ⇒ 必须：**按域取根** + **未定义的域显式跳过**
    _buf5 = _io35.StringIO()
    with _ctx35.redirect_stdout(_buf5):
        layer5_unmentioned(root, ledger_texts(root, 'users'), 'users')
    _o5 = _buf5.getvalue()
    if 'service/users/user' in _o5 and 'service/task' not in _o5 and 'service/users/admin' not in _o5:
        print('    ✅ 层 5 按域取源码根（users 域只扫 service/users/user，不碰 task / users-admin）')
    else:
        print('    ❌ 层 5 **没按域取源码根** ⇒ 别的域会拿到别的域的类 ⇒ 结论会被误读')
        bad += 1
    _buf6 = _io35.StringIO()
    with _ctx35.redirect_stdout(_buf6):
        layer6_unmentioned_production(root, ledger_texts(root, 'users-admin'), 'users-admin')
    _o6 = _buf6.getvalue()
    if 'service/users/admin' in _o6 and 'service/task' not in _o6 and 'service/users/user' not in _o6:
        print('    ✅ 层 6 按域取源码根（users-admin 域只扫 service/users/admin，不碰 task / users）')
    else:
        print('    ❌ 层 6 **没按域取源码根** ⇒ 别的域会拿到别的域的类 ⇒ 结论会被误读')
        bad += 1
    _buf7 = _io35.StringIO()
    with _ctx35.redirect_stdout(_buf7):
        _b56 = layer5_unmentioned(root, {}, 'probe-undefined-domain')
    if _b56 == 0 and '本层不适用' in _buf7.getvalue():
        print('    ✅ 层 5 在**没有域配置**的域上显式跳过（不拿别的域的类去报）')
    else:
        print('    ❌ 层 5 在别的域上**没有显式跳过** ⇒ 报的是别的域的类')
        bad += 1

    # ⑤″ 层 3 的**链判据必须能处理「不在当前分支上」的 commit**（第三十五轮 · E56）
    #     ⇒ 旧口径（与主干的公共祖先）正是栽在这一点上：**主干上窗口恒空** ⇒ 45 处假阳性
    _off = [h.strip() for h in git(root, 'rev-list', '--abbrev-commit',
                                    'do-my-own-gh', '--not', 'HEAD').splitlines()
            if h.strip()]
    if _off:
        _x = _off[0]
        _parent = git(root, 'rev-parse', '--short=7', f'{_x}^').strip()
        _o, _n, _sp = manifest_chain(root, [_parent, _x])
        if _o is not None and _n == _x and len(_sp) == 2:
            print('    ✅ 层 3 的链判据能处理**不在当前分支上**的 commit（不依赖分支口径）')
        else:
            print(f'    ❌ 层 3 的链判据**依赖当前分支** ⇒ 不在本分支上的 commit 判不出来'
                  f'（{_parent} / {_x}）')
            bad += 1
    else:
        print('    ⚠️ 找不到「不在当前分支上」的 commit ⇒ 该项无法自检（不算失败）')

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


def layer6_unmentioned_production(root, texts, domain):
    """台账是否点名了本域**生产类**（不只是测试类）。

    ⚠️ 上一轮只查了测试类 ⇒ **生产类一个都没点名**（`TaskKafkaTopics` 这种
    `@ConfigurationProperties` 的宿主都在其中）⇒ 而生产类里藏着四个
    「缺了就起不来」的启动阻塞项 ⇒ 台账不点名 = 以后重构时看不到这条风险。
    """
    print('=' * 78)
    print('第 6 层 · 台账从未点名的**生产类**（覆盖缺口）')
    print('=' * 78)
    # 🔴 **同第 5 层**：原来**写死 `service/task`** ⇒ 别的域跑它 ⇒ 报的是 task 的类
    roots = JAVA_ROOTS.get(domain)
    if roots is None:
        print(f'  ⏭ 本层的判据**尚未为 `{domain}` 域定义**（不知道该域的 Java 源码根）')
        print(f'     ⇒ **{domain} 域本层不适用**（⚠️ 不是「通过」—— 是「没查」）')
        print(f'     ⇒ 已定义的域：{sorted(JAVA_ROOTS)}')
        return 0
    mentioned = set()
    for t in texts.values():
        # ⚠️ 两种登记形态都要认：① 纯类名 `TaskKafkaTopics`
        #    ② 带路径的形式 `bootstrap/NacosRegistration`（路径末段即类名）
        for m in re.findall(r'`([A-Za-z0-9_./-]+)`', t):
            mentioned.add(m.rsplit('/', 1)[-1].rsplit('.', 1)[-1])
    files = [f for r in roots for f in repo_java_files(root, r)
             if f.endswith('.java') and '/src/main/java/' in f]
    unmentioned = []
    for f in files:
        src = read(os.path.join(root, f))
        m = re.search(r'^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Z][A-Za-z0-9]*)', src, re.M)
        if m and m.group(1) not in mentioned:
            unmentioned.append((m.group(1), f))
    print(f'  仓库内 {domain} 域生产类 {len(files)} 个 ⇒ 台账未点名 {len(unmentioned)} 个')
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
        baseline_layer3 = layer3_numbers(root, ledger_texts(root, 'task'), 'task')

    def manifest_problems():
        """**域无关**判据（不依赖分支 ⇒ 任何分支上都有效）。"""
        rows = commit_manifest_rows(read(readme))
        return check_manifest_integrity(root, rows)[0]

    def layer3_problems():
        """跑整层第 3 层（覆盖编号连续性 / 悬空引用这类**跨文件**判据）。"""
        import contextlib
        import io as _io
        buf = _io.StringIO()
        with contextlib.redirect_stdout(buf):
            n = layer3_numbers(root, ledger_texts(root, 'task'), 'task')
        out = buf.getvalue()
        ps = []
        for line in out.split('\n'):
            if line.strip().startswith('❌'):
                ps.append(line.strip().lstrip('❌ ').strip())
        return ps

    def layer4_entity_problems():
        """跑第 4 层的**补充判据**（锚点文件里找不到断言实体）⇒ 返回提示行。

        ⚠️ **只取「锚点文件里找不到」那一段** —— 其余输出（第 4 层本体）
        与本判据无关 ⇒ 不能混进来（否则「变异没被抓到」会被别的行掩盖）。
        """
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            layer4_golines(root, ledger_texts(root, 'task'), DEFAULT_GO_ROOT)
        out = buf.getvalue().split('\n')
        ps, in_block = [], False
        for line in out:
            if '锚点文件里**找不到**' in line:
                in_block = True
                continue
            if in_block:
                if line.strip().startswith('⚠️ 「不悬空」'):
                    break
                if line.strip().startswith('⚠️') or not line.strip():
                    break
                ps.append(line.strip())
        return ps

    pits = os.path.join(root, 'docs/Pitfalls/task/rpc.md')
    orig_pits = read(pits)

    rpc = os.path.join(root, 'docs/DRIFT/task/rpc.md')
    orig_rpc = read(rpc)

    def layer1_missing_paths():
        """跑第 1 层，返回 **MISSING 行**（用于第 1 层专用变异）。"""
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            layer1_paths(root, ledger_texts(root, 'task'))
        return [ln for ln in buf.getvalue().split('\n') if '❌ MISSING' in ln]

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
        # ⚠️ 「删掉一行登记」在**别人分支上**抓不到（漏登记是分支相关判据）
        #    ⇒ 改用它的**分支无关等价物**：删掉链**中间**的一行 ⇒ 区间里就多出一个
        #    没登记的 commit ⇒ 被「不构成连续链」抓到（域无关判据 ⇒ 任何分支都有效）
        ('删掉链中间的一行登记', readme,
         lambda t: re.sub(r'^\| `185448c`.*\n', '', t, count=1, flags=re.M),
         lambda ps: any('不构成连续链' in p for p in ps), MANIFEST),
        ('登记了一个仓库里不存在的 commit', readme,
         lambda t: _insert_before_last_placeholder(
             t, '| `deadbee` | 变异样本：仓库里不存在的 commit |'),
         lambda ps: any('不存在的哈希' in p for p in ps), MANIFEST),
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

        # ── 第 4 层**补充判据**专用变异（favorite 域第二十二轮实测带出 · 2026-10-26）──
        #   正例：注入一个「**行号合法但指错文件**」的锚点
        #     台账说 `service/task/api/task.go:10` 处是 `GetTaskHandler`
        #     而 `GetTaskHandler` 在真源里**只声明在**
        #     `service/task/api/internal/handler/task/gettaskhandler.go`（34 行）
        #     ⇒ `service/task/api/task.go` 有 38 行 ⇒ **行号完全合法**
        #     ⇒ **既有第 4 层判它「不悬空」⇒ 放过去**
        #   ⇒ 只有**补充判据**能抓到 ⇒ 这正是 favorite 域报的「证据是假的」那一类
        #   ⚠️ 注入的行**必须不被去噪规则吃掉**（N5/N7 词表）⇒ 措辞里避开
        #     「删除 / 不存在 / 已登记 / 见 § / DRIFT-xxx」
        #   ⚠️ 符号必须**在全真源里唯一声明**（否则「声明处唯一」这条判据会把它挡掉）
        _ent_cases = [
            ('锚点指错文件（行号合法 ⇒ 旧判据判它不悬空）',
             '（变异样本：`service/task/api/task.go:10` 处的 `GetTaskHandler`）',
             True),
            # ── 阴性对照：同一符号指**正确的文件** ⇒ **必须不报** ──
            ('阴性对照：同一符号指正确的文件 ⇒ 必须不报',
             '（变异样本：`service/task/api/internal/handler/task/gettaskhandler.go:10` '
             '处的 `GetTaskHandler`）',
             False),
        ]
        for _label, _row, _must_report in _ent_cases:
            try:
                with open(rpc, 'w', encoding='utf-8') as f:
                    f.write(orig_rpc.rstrip('\n') + '\n' + _row + '\n')
                _ents = layer4_entity_problems()
            finally:
                with open(rpc, 'w', encoding='utf-8') as f:
                    f.write(orig_rpc)
                os.utime(rpc, None)   # PIT-GLOBAL-037：还原后必须 touch
            _reported = any('GetTaskHandler' in m for m in _ents)
            if _reported == _must_report:
                print(f'  ✅ 变异「第 4 层补充判据 · {_label}」⇒ '
                      + ('被精确抓到' if _must_report else '正确地不报'))
            else:
                print(f'  ❌ 变异「第 4 层补充判据 · {_label}」'
                      + ('**没被抓到** ⇒ 这一条判据是假绿' if _must_report
                         else '**误报了** ⇒ 判据太宽（假阳性）')
                      + f'（实际报出：{_ents}）')
                bad += 1

        # ── 标准库 basename 通道专用变异（第四十八轮）──────────────────────
        #   变异 = **关掉通道**（`std_idx = _stdlib_index(goroot)` ⇒ `std_idx = {}`）
        #   期望 = 台账里那 **13 条标准库裸 basename** 引用必须重新变红
        #   ⚠️ **变异打在代码上**（工具源码），不是打在台账上（PIT-GLOBAL-040）
        #   ⚠️ **绝不落盘改共享工具** —— 共享工具是**别的域**的守门
        #      ⇒ 万一测试进程被杀，磁盘上的工具不能留半截状态
        #      ⇒ 用 `exec` 在**内存**里跑一份变异后的工具（PIT-GLOBAL-041）
        _stdlib_refs = ('quote.go:522-563', 'url.go:1001', 'context.go:625')
        _tool_path = os.path.join(root, 'tools/check-ledger-selfcheck.py')
        _tool_src = read(_tool_path)
        _mut_src = _tool_src.replace('std_idx = _stdlib_index(goroot)', 'std_idx = {}')
        if _mut_src == _tool_src:
            print('  ❌ 变异「关掉标准库 basename 通道」⇒ **变异点找不到** ⇒ 该判据是假绿')
            bad += 1
        else:
            _ns = {'__file__': _tool_path, '__name__': '_mutant_stdlib_channel'}
            exec(compile(_mut_src, _tool_path, 'exec'), _ns)
            _buf = io.StringIO()
            with contextlib.redirect_stdout(_buf):
                _ns['layer4_golines'](root, _ns['all_ledger_texts'](root),
                                      DEFAULT_GO_ROOT)
            _mut_out = _buf.getvalue()
            # ⚠️ **2026-10-27 改成只认「❌ 失败行」**（原来只判「输出里有没有这个子串」）：
            #    favorite 域台账（2026-10-27 合入主干）里带了 `src/net/url/url.go:1001` 这条
            #    **N7「台账已自陈并记档」的豁免引用** ⇒ 它出现在**通过行**里
            #    ⇒ 朴素子串匹配把「**通过**」误当成「**报了**」⇒ 阴性对照恒红 ⇒ 变异测试假失败。
            #    ⇒ 判据必须是「该引用出现在**失败行**里」，而不是「出现在输出里」。
            _mut_fail_lines = [ln for ln in _mut_out.splitlines()
                               if ln.strip().startswith('❌')]
            _stayed_ok = [r for r in _stdlib_refs
                          if not any(r in ln for ln in _mut_fail_lines)]
            if not _stayed_ok:
                print('  ✅ 变异「关掉标准库 basename 通道」⇒ 13 条标准库引用**全部重新变红**'
                      f'（抽样：{", ".join(_stdlib_refs)}）')
            else:
                print('  ❌ 变异「关掉标准库 basename 通道」**没被抓到** ⇒ 该判据是假绿'
                      f'（仍不报：{_stayed_ok}）')
                bad += 1
            # 阴性对照：**未变异**时这 3 条必须**不出现在失败行里**（否则是恒红）
            _buf2 = io.StringIO()
            with contextlib.redirect_stdout(_buf2):
                layer4_golines(root, all_ledger_texts(root), DEFAULT_GO_ROOT)
            _leak = [r for r in _stdlib_refs
                     if any(r in ln and ln.strip().startswith('❌')
                            for ln in _buf2.getvalue().splitlines())]
            if not _leak:
                print('  ✅ 阴性对照：未变异时那 3 条标准库引用**不报**（判据不是恒红）')
            else:
                print(f'  ❌ 阴性对照：未变异时仍报出 {_leak} ⇒ 判据恒红')
                bad += 1

        # ── 外部仓库锚点通道专用变异（第四十八轮）──────────────────────
        #   变异 = **让 `_external_anchor` 恒返回 False**（⇒ 外部仓库锚点不再被识别）
        #   期望 = like 域那 3 条 `service/async/**` 锚点必须重新变成 FAIL
        _ext_refs = ('service/async/rpc/cmd/worker/favorite.go:183-186',
                     'service/async/rpc/internal/app/community_authority_binder.go:46-62')
        _mut_ext_src = _tool_src.replace(
            "    return not os.path.isdir(os.path.join(go_root, 'service', first))",
            "    return False")
        if _mut_ext_src == _tool_src:
            print('  ❌ 变异「关掉外部仓库锚点通道」⇒ **变异点找不到** ⇒ 该判据是假绿')
            bad += 1
        else:
            _ns2 = {'__file__': _tool_path, '__name__': '_mutant_external_anchor'}
            exec(compile(_mut_ext_src, _tool_path, 'exec'), _ns2)
            _buf3 = io.StringIO()
            with contextlib.redirect_stdout(_buf3):
                _ns2['layer4_golines'](root, _ns2['all_ledger_texts'](root),
                                       DEFAULT_GO_ROOT)
            _mut3 = _buf3.getvalue()
            _still_ext = [r for r in _ext_refs if r not in _mut3]
            if not _still_ext:
                print('  ✅ 变异「关掉外部仓库锚点通道」⇒ like 域 3 条跨仓库锚点'
                      '**全部重新变红**（抽样：service/async/…）')
            else:
                print('  ❌ 变异「关掉外部仓库锚点通道」**没被抓到** ⇒ 该判据是假绿'
                      f'（仍不报：{_still_ext}）')
                bad += 1
            # 阴性对照：**未变异**时这 2 条必须**不**出现在 ❌ 清单里
            _buf4 = io.StringIO()
            with contextlib.redirect_stdout(_buf4):
                layer4_golines(root, all_ledger_texts(root), DEFAULT_GO_ROOT)
            _clean4 = _buf4.getvalue()
            _leak4 = [r for r in _ext_refs
                      if any(r in ln and ln.strip().startswith('❌')
                             for ln in _clean4.split('\n'))]
            if not _leak4:
                print('  ✅ 阴性对照：未变异时那 2 条跨仓库锚点**不是 FAIL**（判据不是恒红）')
            else:
                print(f'  ❌ 阴性对照：未变异时仍报成 FAIL {_leak4} ⇒ 判据恒红')
                bad += 1

        # ── 第 1 层专用变异（E61 · 2026-10-26 第三十七轮 · users/user 域实测带出）──
        #   变异 9/10/11：在 task 台账里**新增一条指向仓内不存在路径的引用**，
        #     ⇒ 「落地态 / 免责」措辞 ⇒ 第 1 层**必须去噪**（不得报 MISSING）
        #     ⇒ 同一行改成「路径写错」⇒ 第 1 层**必须报出来**（证明护栏有牙）
        #   ⚠️ **为什么必须做这一组**：层 0 的对照只验**判定函数**；
        #     这一组验**端到端**（真的插进台账 → 真的跑第 1 层 → 真的看报不报）。
        #   ⚠️ **对照口径**：阳性看「**有没有报**」，不是「报了几条」⇒
        #     否则别的路径变化会掩盖这条判据的失效。
        _sent = ('service/task/task-rpc/src/test/java/com/rag/task/task/rpc/'
                 'E61NoSuchGateTest.java')
        _narr_cases = [
            ('落地态措辞 ⇒ 必须去噪',
             f'| **变异样本（E61-a）** | `{_sent}` 待合入主干 |', False),
            ('免责措辞（users/user 域原文）⇒ 必须去噪',
             f'| **变异样本（E61-b）** | `{_sent}`<br>⚠️ 主干上**查不到** ⇒ '
             f'路径**悬空**是**预期状态**，不是台账事实错误 |', False),
            ('带「路径写错」标记 ⇒ 必须报出来（护栏有牙）',
             f'| **变异样本（E61-c）** | `{_sent}` 路径写错 ⇒ 待合入主干 |', True),
        ]
        for _label, _row, _must_report in _narr_cases:
            try:
                with open(rpc, 'w', encoding='utf-8') as f:
                    f.write(orig_rpc.rstrip('\n') + '\n' + _row + '\n')
                _miss = layer1_missing_paths()
            finally:
                with open(rpc, 'w', encoding='utf-8') as f:
                    f.write(orig_rpc)
                os.utime(rpc, None)   # PIT-GLOBAL-037：还原后必须 touch（避免 mtime 陈旧）
            _reported = any(_sent in m for m in _miss)
            if _reported == _must_report:
                print(f'  ✅ 变异「第 1 层 N13 · {_label}」⇒ '
                      f'{"被精确抓到" if _must_report else "被正确去噪"}')
            else:
                print(f'  ❌ 变异「第 1 层 N13 · {_label}」**判错**'
                      f'（期望{"必须报出" if _must_report else "必须去噪"}，'
                      f'实际{"报了" if _reported else "没报"}）⇒ 该判据是假绿')
                bad += 1

        # 回归：第 3 层的 api 域断言（改标题条数 ⇒ 必须红）

        try:
            with open(api, 'w', encoding='utf-8') as f:
                f.write(re.sub(r'^## 一、([^\n（]*)（\d+ 条', r'## 一、\1（2 条',
                               orig_api, count=1, flags=re.M))
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                n = layer3_numbers(root, ledger_texts(root, 'task'), 'task')
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
            n = layer3_numbers(root, ledger_texts(root, 'task'), 'task')
        if n == baseline_layer3:
            print(f'  ✅ 复原后第 3 层回到变异前的 {n} 处（阳性对照：变异可逆、判据不误报）')
        else:
            print(f'  ❌ 复原后第 3 层报 {n} 处 ≠ 变异前的 {baseline_layer3} 处 ⇒ 变异没复原干净')
            bad += 1
    finally:
        for _p, _t in ((readme, orig_readme), (api, orig_api),
                       (pits, orig_pits), (rpc, orig_rpc)):
            with open(_p, 'w', encoding='utf-8') as f:
                f.write(_t)
            os.utime(_p, None)      # PIT-GLOBAL-037：还原后必须 touch
    print(f'  ⇒ 变异测试{"全部精确命中" if not bad else f"有 {bad} 处没命中"}')
    return bad


# ====================================================================

def all_ledger_texts(root):
    """**全仓**台账文本（`docs/**/*.md`）⇒ 供「全仓补充判据」用。

    🔴 **为什么要扫全仓**（第四十七轮实测）：
       `LEDGER_FILES` 只配了 task / users / users-admin 三个域
       ⇒ ⇒ **global 自己写的台账（`docs/DRIFT/global/_README.md`）根本不在扫描范围里**
       ⇒ ⇒ **我自己的 Go 锚点一直没被检查过**（真空缺）。
       ⇒ ⇒ 而第 4+ 层是**提示级** ⇒ 扫全仓**不会**让任何域变红 ⇒ 安全。
    """
    out = {}
    base = os.path.join(root, 'docs')
    for dp, _, fns in os.walk(base):
        for fn in fns:
            if fn.endswith('.md'):
                full = os.path.join(dp, fn)
                rel = os.path.relpath(full, root)
                out[rel] = read(full)
    return out


def layer4_entity_all(root, go_root):
    """**全仓**跑第 4 层（只为拿到「补充判据」的命中 + 既有 L4 的 FAIL）⇒ 返回 (命中列表, FAIL 行, 外部锚点行)。

    ⚠️ **只用于提示与转达** ⇒ 本函数**不改变**主流程的退出码
       ⇒ 别的域的历史锚点问题**不会**让本工具变红（那不是 global 的写权范围）。
    ⚠️ **命中数 ≠ 捕获行数** —— 捕获块里每条命中占 **2 行**
       （命中行 + 「⇒ 该符号实际在 …」详情行），前面还有 1 行说明
       ⇒ ⇒ **必须按「含『缺失实体:』的行」计数**（实测踩过：把行数当命中数 ⇒ 报成 3 条，实际 1 条）。
    """
    import contextlib
    import io as _io
    buf = _io.StringIO()
    with contextlib.redirect_stdout(buf):
        layer4_golines(root, all_ledger_texts(root), go_root)
    out = buf.getvalue().split('\n')
    hits, fails, cur = [], [], None
    externals = []
    in_block = False
    in_ext = False
    for line in out:
        if '锚点文件里**找不到**' in line:
            in_block = True
            continue
        if in_block:
            s = line.strip()
            if not s:
                continue
            if s.startswith('⚠️'):
                in_block = False
                continue
            if '缺失实体:' in s:
                cur = [s]
                hits.append(cur)
            elif cur is not None:
                cur.append(s)
            continue
        if line.strip().startswith('🌐'):
            in_ext = True
            continue
        if in_ext:
            s = line.strip()
            if not s:
                continue
            if s.startswith('⚠️'):
                in_ext = False
                continue
            # ⚠️ **只收「引用行」**（形如 `path.go:12-34`）—— 说明行以 `⇒` 开头 ⇒ 跳过
            #    （否则说明行会被算进「条数」⇒ 报数与实数对不上 ⇒ 同 PIT-GLOBAL-066）
            if re.match(r'^\S+:\d+', s):
                externals.append(s)
            continue
        if line.strip().startswith('❌'):
            fails.append(line.strip())
    return hits, fails, externals


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--domain', default='task')
    ap.add_argument('--go-root', default=DEFAULT_GO_ROOT)
    ap.add_argument('--repo-root', default=None)
    ap.add_argument('--mutation-test', action='store_true',
                    help='只跑变异测试（故意破坏台账 ⇒ 必须被抓到）')
    ap.add_argument('--entity-all', action='store_true',
                    help='额外跑**全仓**补充判据（所有 docs/**/*.md · 提示级 · 不计入退出码）')
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
    bad += layer2_claims(root, args.domain)
    bad += layer3_numbers(root, texts, args.domain)
    bad += layer4_golines(root, texts, args.go_root)
    if args.entity_all:
        # ── 全仓补充判据（**提示级 · 不计入退出码**）──────────────────────
        # 🔴 **为什么要单独扫全仓**：`LEDGER_FILES` 只配了三个域
        #    ⇒ **global 自己写的台账根本不在扫描范围里** ⇒ 我自己的 Go 锚点一直没被检查过。
        print()
        print('=' * 78)
        print('全仓补充判据（**所有** docs/**/*.md · 提示级 · 不计入退出码）')
        print('=' * 78)
        _ent, _fails, _ext = layer4_entity_all(root, args.go_root)
        if _ent:
            print(f'  ⚠️ 锚点文件里**找不到**断言提到的实体: {len(_ent)} 条')
            for grp in _ent:
                for l in grp:
                    print('      ' + l)
        else:
            print('  ✅ 全仓 0 条命中')
        if _fails:
            print(f'  ⚠️ 另有 {len(_fails)} 条**既有第 4 层 FAIL**（跨域）⇒ '
                  f'**不是 global 的写权范围** ⇒ 只记账 + 转达 owner')
            for l in _fails:
                print('      ' + l)
        if _ext:
            print(f'  🌐 另有 {len(_ext)} 条**外部仓库锚点**（真源 `service/` 下无该首段目录 ⇒ '
                  f'本机不可核验 ⇒ **不是台账写错**）')
            for l in _ext:
                print('      ' + l)
    unmentioned = layer5_unmentioned(root, texts, args.domain)
    unmentioned_prod = layer6_unmentioned_production(root, texts, args.domain)

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
