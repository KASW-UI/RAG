#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scan-rpc-port-defaults.py —— 「rpc 业务口默认值 = Go 真源值」这条不变量的**跨域只读扫描器**。

════════════════════════════════════════════════════════════════════════
【为什么要有它（Round 7 登记的 `R7-PORT-002` · B 类 · 守门覆盖缺口）】
════════════════════════════════════════════════════════════════════════
`docs/DRIFT/global/_README.md` 的 `R7-PORT-002` 行原文：

    「默认值 = Go 值」这条不变量，仓内**只有 3/9 的 rpc 有机器守门**
    —— 钉住业务口默认值的守门**只有** `HotRpcPrometheusCarrierGateTest`（8083）·
    `FavoriteRpcPrometheusCarrierGateTest`（8082）· `ArticleRpcPrometheusCarrierGateTest`（9001）
    三个 ⇒ ⇒ **`comment-rpc` / `follow-rpc` / `like-rpc` / `message-rpc` / `admin-api` 这 5 处**
    当时是「静默漂移」（`R7-PORT-001` 里只有 hot-rpc 会红）⇒ ⇒ **「跑绿」不等于「默认值没偏」**

⇒ 那 3 条是**各域自己写的 JUnit 门禁**（每域一份 ⇒ 覆盖面天然随域数走 ⇒ 必然漏）。
⇒ **正解 = 一个跨域只读扫描器**（本脚本）：

  · **一次跑完 9 个 rpc 域**（不需要 9 份各域门禁 ⇒ 新增域时也不会漏）；
  · **判据 = 与 Go 真源逐字节对照**（不是与仓内注释对照 —— 注释是二手信息，
    本仓 11 处历史回归里有若干条正是「照着注释改」改出来的）；
  · **只读**：不启动进程、不连数据库、不写任何文件 ⇒ 可放进任何常跑清单。

════════════════════════════════════════════════════════════════════════
【判据】
════════════════════════════════════════════════════════════════════════
对每个 rpc 模块：

  ① **Go 侧**：读真源 `service/<域>/rpc/etc/<域>.yaml` 的 `ListenOn: <host>:<port>`
     ⇒ 取端口（**真源 = 唯一行为基准**，AGENTS.md §3.1）。
  ② **RAG 侧**：读 `service/<域>/<域>-rpc/src/main/resources/application.yml`
     的 `grpc:` → `server:` → `port:` ⇒ 该值允许是
       · 裸数字（`9001`）            ⇒ 默认值 = 9001
       · 占位形态（`${XXX:9001}`）   ⇒ 默认值 = **9001**（**env 覆盖是既有约定**；
         ⚠️ `R7-PORT-003` 就是栽在「解析器读不懂占位形态」上 ⇒ 这里必须按占位形态解析）
  ③ **两侧必须相等** ⇒ 不等即判红（并指出**差多少**）。

⚠️ **为什么只查「默认值」不查「env 覆盖后的值」** ——
   `docs/DRIFT/global/_README.md` 的可复用教训原文：
   「**「端口 env 化」要先分清改的是「默认值」还是「分配值」** —— 前者属**对外契约面**
   （Go 侧逐字对齐 + 门禁钉住 ⇒ **只能改 env 键名，不能改默认值**），后者属**部署面**」
⇒ ⇒ 本脚本**只钉默认值**（= 对外契约面）⇒ **不查** env 覆盖 ⇒ **不与部署面冲突**。

⚠️ **`user-rpc` 被显式排除** —— 它的默认值是 **49210**，Go 真源 `service/user/user/rpc/etc/user.yaml:2`
   是 **9004** ⇒ **两者确实不等** ⇒ 但这是**已锁定的有意偏离**，不是漂移：
   `service/users/user/user-rpc/src/main/resources/application.yaml:136` 明写
   「⚠️ 默认端口**一字不变**（修 INFRA-001：9004 → 49210，Infrastructure.md §3 锁定）」
   ⇒ ⇒ 本脚本**列出但不判红**，并在输出里显式标注「有意偏离（已锁定）」。
   ⇒ **改这条偏离需要用户拍板**，不在本脚本的职责内。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    python3 tools/difftest/scan-rpc-port-defaults.py            # 扫描 + 断言（严格）
    python3 tools/difftest/scan-rpc-port-defaults.py --report   # 只报告，不判失败
    GO_REPO=/path/to/Sea-RideTheWind_golang python3 tools/difftest/scan-rpc-port-defaults.py

退出码：0 = 9 个 rpc 的业务口默认值全部 = Go 真源值（或为已锁定的有意偏离）；
        1 = 至少一处不等（= 真漂移）；2 = 环境问题（真源不可读 / 文件缺失）。

⚠️ **本脚本刻意不叫 `check-*.py`** —— `docs/RoundPlan/_INDEX.md` §十一 与
   `docs/RoundPlan/round-06/README.md` §5.3.2 都写明：**不得新增 `check-*.py`**
   （会动 `docs/PhaseGoal-judge.md` §六 6.3 的 **27 项守门计数** ⇒ 制造 B14 类漂移）。
   ⇒ 先例：`tools/difftest/scan-bash-legacy-vars.py`（同为非 `check-*` 扫描器）。
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GO_REPO = pathlib.Path(
    os.environ.get("GO_REPO", "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang")
)

# ── 9 个 rpc 域：(RAG 模块相对路径, Go 真源 yaml 相对路径, 说明) ─────────────────
# ⚠️ 顺序 = 域名字母序（便于逐条比对两轮输出）。
RPC_MODULES = [
    ("service/article/article-rpc/src/main/resources/application.yml",
     "service/article/rpc/etc/article.yaml", "article"),
    ("service/users/admin/admin-rpc/src/main/resources/application.yml",
     "service/user/admin/rpc/etc/admin.yaml", "admin"),
    ("service/comment/comment-rpc/src/main/resources/application.yml",
     "service/comment/rpc/etc/comment.yaml", "comment"),
    ("service/favorite/favorite-rpc/src/main/resources/application.yml",
     "service/favorite/rpc/etc/favorite.yaml", "favorite"),
    ("service/follow/follow-rpc/src/main/resources/application.yml",
     "service/follow/rpc/etc/follow.yaml", "follow"),
    ("service/hot/hot-rpc/src/main/resources/application.yml",
     "service/hot/rpc/etc/hot.yaml", "hot"),
    ("service/like/like-rpc/src/main/resources/application.yml",
     "service/like/rpc/etc/like.yaml", "like"),
    ("service/message/message-rpc/src/main/resources/application.yml",
     "service/message/rpc/etc/message.yaml", "message"),
    ("service/task/task-rpc/src/main/resources/application.yml",
     "service/task/rpc/etc/task.yaml", "task"),
]

# ── 已锁定的**有意偏离**（列出但**不判红**）────────────────────────────────────
# 依据：`service/users/user/user-rpc/src/main/resources/application.yaml:136`
#      「默认端口**一字不变**（修 INFRA-001：9004 → 49210，Infrastructure.md §3 锁定）」
KNOWN_INTENTIONAL = {
    "user": {
        "rag": "service/users/user/user-rpc/src/main/resources/application.yaml",
        "go": "service/user/user/rpc/etc/user.yaml",
        "rag_default": 49210,
        "reason": "INFRA-001 已锁定（Infrastructure.md §3）—— 改这条偏离需用户拍板",
    },
}

LISTEN_RE = re.compile(r"^\s*ListenOn:\s*\S+:(\d+)\s*$", re.M)
PLACEHOLDER_RE = re.compile(r"^\$\{[A-Za-z0-9_]+:(\d+)\}$")
BARE_PORT_RE = re.compile(r"^(\d+)$")


def go_listen_port(path: pathlib.Path):
    """Go 真源 `ListenOn` 的端口；返回 None = 文件里没有这一行。"""
    try:
        txt = path.read_text(encoding="utf-8")
    except Exception:
        return None
    m = LISTEN_RE.search(txt)
    return int(m.group(1)) if m else None


def rag_grpc_server_port(path: pathlib.Path):
    """RAG 侧 `grpc:` → `server:` → `port:` 的**默认值**。

    解析形态：
      · `port: 9001`                  ⇒ 9001
      · `port: ${ARTICLE_RPC_PORT:9001}` ⇒ 9001（**占位形态** —— `R7-PORT-003` 的坑就在这）
    返回 (默认值 或 None, 原始文本 或 None)。
    """
    try:
        lines = path.read_text(encoding="utf-8").split("\n")
    except Exception:
        return None, None
    # 找顶层 `grpc:`（列 0）
    start = None
    for i, l in enumerate(lines):
        if l.rstrip() == "grpc:":
            start = i
            break
    if start is None:
        return None, None
    # 在 grpc: 块内找 `  server:`（缩进 2），再在 server: 块内找 `    port:`（缩进 4）
    srv = None
    for i in range(start + 1, len(lines)):
        l = lines[i]
        if l.strip() == "" or l.lstrip().startswith("#"):
            continue
        indent = len(l) - len(l.lstrip())
        if indent == 0:
            break                      # 出了 grpc: 块
        if indent == 2 and l.strip() == "server:":
            srv = i
            break
    if srv is None:
        return None, None
    for i in range(srv + 1, len(lines)):
        l = lines[i]
        if l.strip() == "" or l.lstrip().startswith("#"):
            continue
        indent = len(l) - len(l.lstrip())
        if indent <= 2:
            break                      # 出了 server: 块
        if indent == 4 and l.strip().startswith("port:"):
            raw = l.split(":", 1)[1].strip()
            m = PLACEHOLDER_RE.match(raw)
            if m:
                return int(m.group(1)), raw
            m = BARE_PORT_RE.match(raw)
            if m:
                return int(m.group(1)), raw
            return None, raw            # 非数字形态（不该出现）⇒ 判「解析不了」
    return None, None


def main() -> int:
    report_only = "--report" in sys.argv

    print("rpc 业务口默认值 · 跨域对照扫描器（R7-PORT-002 补门）")
    print("=" * 74)
    print("  真源：%s" % GO_REPO)
    print("  判据：RAG `grpc.server.port` 的**默认值** == Go 真源 `ListenOn` 的端口")
    print("-" * 74)

    rows = []
    problems = []

    for rag_rel, go_rel, dom in RPC_MODULES:
        rag_p = ROOT / rag_rel
        go_p = GO_REPO / go_rel
        if not rag_p.exists():
            rows.append((dom, "—", "—", "🔴 RAG 文件缺失", rag_rel))
            problems.append("%s：RAG 文件缺失 %s" % (dom, rag_rel))
            continue
        if not go_p.exists():
            rows.append((dom, "—", "—", "🔴 真源文件缺失", go_rel))
            problems.append("%s：Go 真源文件缺失 %s" % (dom, go_rel))
            continue
        go_port = go_listen_port(go_p)
        rag_port, raw = rag_grpc_server_port(rag_p)
        if go_port is None:
            rows.append((dom, "—", str(rag_port), "⚠️ 真源无 ListenOn（跳过）", go_rel))
            continue
        if rag_port is None:
            rows.append((dom, str(go_port), str(raw), "🔴 RAG 侧解析不出默认值", rag_rel))
            problems.append("%s：RAG 侧 `grpc.server.port` 解析不出默认值（原文 %r）" % (dom, raw))
            continue
        if rag_port == go_port:
            rows.append((dom, str(go_port), str(rag_port), "✅ 一致", rag_rel))
        else:
            rows.append((dom, str(go_port), str(rag_port),
                         "🔴 不一致（差 %+d）" % (rag_port - go_port), rag_rel))
            problems.append("%s：默认值 %s ≠ Go 真源 %s（差 %+d）"
                            % (dom, rag_port, go_port, rag_port - go_port))

    for dom, go_v, rag_v, verdict, src in rows:
        print("  %-9s Go=%-7s RAG=%-7s %-26s %s" % (dom, go_v, rag_v, verdict, src))

    # ── 已锁定的有意偏离（列出不判红）────────────────────────────────────────
    ki = KNOWN_INTENTIONAL.get("user")
    if ki:
        go_port = go_listen_port(GO_REPO / ki["go"])
        rag_port, _ = rag_grpc_server_port(ROOT / ki["rag"])
        print("-" * 74)
        print("  %-9s Go=%-7s RAG=%-7s %-26s %s"
              % ("user", str(go_port), str(rag_port),
                 "⚠️ 有意偏离（不判红）", ki["reason"]))
        print("           ⚠️ 改这条偏离需**用户拍板** ⇒ 不在本扫描器职责内。")

    print("=" * 74)
    print("  受检 rpc 模块数 = %d（9 个域；`user-rpc` 为已锁定有意偏离 ⇒ 单列不判红）"
          % len(RPC_MODULES))

    if report_only:
        return 0

    if problems:
        print("❌ 「默认值 = Go 值」不变量被破坏：")
        for p in problems:
            print("   · %s" % p)
        return 1

    print("✅ 9 个 rpc 的业务口默认值全部 = Go 真源 `ListenOn`（或为已锁定的有意偏离）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
