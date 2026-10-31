#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
**R6 · 数据库 Schema 契约门**（CI 入口）⇒ 纯 Java 侧，**不需要 Go 仓 / Go 工具链**。

════════════════════════════════════════════════════════════════════════
【它做什么】
════════════════════════════════════════════════════════════════════════
每个域：
  ① 在**干净 PostgreSQL** 上按序重放该域的 Flyway / SchemaInitializer 迁移脚本
     ⇒ 这一步同时回答「**迁移是否完整**」（缺文件 / 语法错 / 依赖顺序错 ⇒ 红）；
  ② 把重放出来的实际 schema 与 `ci/contracts/schema/<域>.json`
     （**离线一次性**从 Go 真源提取的语义约束基准）逐项比对
     ⇒ 比对口径完全由基准里的 `must_match` 决定（类型 / 长度 / 可空性 /
       关键 DEFAULT / 唯一约束），**不比对**索引命名形态 / 序列名 / CHECK / 触发器。

════════════════════════════════════════════════════════════════════════
【为什么不自己实现比对逻辑】
════════════════════════════════════════════════════════════════════════
比对逻辑**只有一份**：`tools/difftest/check-ddl-static.py --rag-only
--semantic-baseline <file>`（本脚本**调用**它，不复制它）⇒
不会出现「两份检查器各自漂移」。

⚠️ 本脚本与 `tools/difftest/check-ddl-static.py` 的分工（**不是功能重叠**）：
   · `check-ddl-static.py` = **比对引擎**（读库 + 与基准逐项比）
   · 本脚本           = **CI 编排**（起 PG / 重放迁移 / 调引擎 / 汇总四态结论）
   ⚠️ `tools/difftest/ddl-parity.sh`（**双侧**活体对拍）**不需要 Go 侧 ⇒ 本门不用它**
      —— 本门**只重放 Java 侧的 SQL**。

════════════════════════════════════════════════════════════════════════
【四态结论与退出码】
════════════════════════════════════════════════════════════════════════
   PASS      0  与基准一致（检查确实执行了）
   FAIL      1  **迁移成功执行了、但产出的 schema 与基准不一致**（契约漂移）
   BLOCKED   2  **检查没做成**：环境不满足（PG 起不来 / 容器被杀 / 连不上库）
              / 迁移执行失败 ⇒ **不得计入 PASS**
   NOT_RUN   3  本域 DDL 维度不适用（hot 域无数据库）⇒ **不是「已对齐」**

   整体退出码：任一 FAIL ⇒ 1；否则任一 BLOCKED ⇒ 2；否则 0。
   ⚠️ NOT_RUN **不**影响整体退出码（它是 N/A，不是失败），但**必须显式打印**。

⚠️ **BLOCKED 与 FAIL 的分界（2026-10-31 宿主实跑发现的分类缺陷，已修）**
   曾出现：PG 容器被 OOM kill（psql 返 `exit status 137`）⇒ 容器中途死掉
   ⇒ 旧实现把它判成 **FAIL（契约漂移）** ⇒ 维护者会去查代码，而真因在环境。
   ⇒ 现在**严格分开**：
     · **基础设施类**（容器不在运行 / 被杀 / 连不上 / 建库删库失败）⇒ **BLOCKED**
     · **迁移执行失败**（psql 报 SQL 错误）⇒ **BLOCKED**
       （「检查没做成」是事实；同时会打印 psql 原文，
         并显式提示「这可能是迁移脚本本身的缺陷，请看 psql 原文」）
     · **比对出差异**（迁移跑完了、schema 与基准不一致）⇒ **FAIL**
   ⇒ 只有「确实跑了、确实不一致」才叫漂移。

⚠️ **容器自愈**：9 个域串行复用同一个 PG 容器 ⇒ 某一域把容器搞挂会让
   **后续域连环误判** ⇒ 每域开始前检查容器健康，**不健康就重建** ⇒
   单域故障不再传染。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    python3 ci/scripts/check-schema-contract.py --all
    python3 ci/scripts/check-schema-contract.py --domain users --domain hot
    python3 ci/scripts/check-schema-contract.py --all --keep     # 留容器便于排查
"""
import argparse
import importlib.util
import json
import os
import subprocess
import sys
import time

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools", "difftest")
BASELINE_DIR = os.path.join(REPO_ROOT, "ci", "contracts", "schema")

sys.path.insert(0, TOOLS_DIR)
_spec = importlib.util.spec_from_file_location(
    "check_ddl_static", os.path.join(TOOLS_DIR, "check-ddl-static.py"))
cds = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cds)
import docker_api  # noqa: E402  （CLI 优先 / API 兜底）

DEFAULT_IMAGE = "postgres:16-alpine"
PG_USER = "admin"
PG_PASS = "admin"

VERDICT = {0: "PASS", 1: "FAIL", 2: "BLOCKED", 3: "NOT_RUN"}

# ── 基础设施类错误 vs SQL 层错误 ─────────────────────────────────────────
# ⚠️ 这条分界是 2026-10-31 宿主实跑踩出来的：PG 容器被 OOM kill 时
#    psql 返 `exit status 137` ⇒ 旧实现判成 FAIL（契约漂移）⇒
#    维护者去查代码，真因在环境 ⇒ 违反「环境阻塞不得伪装成漂移」的纪律。
INFRA_MARKERS = (
    "is not running", "the database system is shut down",
    "container is not running", "connection refused", "could not connect",
    "terminating connection", "server closed the connection",
    "the system is shutting down", "no response", "cannot connect",
    "exit status 137", "exit status 143", "killed", "out of memory",
    "OOM", "is a directory", "no such file or directory",
    "daemon is not running", "permission denied",
)


def classify_failure(rc, err, out=""):
    """⇒ "infra" | "sql"

    · **infra** = 基础设施类（容器被杀 / 连不上 / 建库失败）⇒ **BLOCKED**
    · **sql**   = PG 拒绝了这条 SQL（迁移脚本本身有问题）⇒ **BLOCKED**
      （同时会打印 psql 原文并提示「这可能是迁移脚本的缺陷」）
    """
    text = f"{err}\n{out}"
    low = text.lower()
    for m in INFRA_MARKERS:
        if m.lower() in low:
            return "infra"
    # psql 自身的错误形态：ERROR: / FATAL: / 语法错误 ⇒ SQL 层
    if "ERROR:" in text or "FATAL:" in text or "syntax error" in low:
        return "sql"
    return "infra"



def log(msg):
    print(msg, flush=True)


def start_pg(client, container, image, keep=False):
    """⇒ (ok, message, container_id)"""
    if client.container_exists(container):
        log(f"[r6] 容器 {container} 已存在 ⇒ 先删掉（**每次都从干净容器开始**）")
        client.remove_container(container)
    try:
        if not client.image_exists(image):
            log(f"[r6] 拉镜像 {image} …")
            ok, msg = client.pull_image(image)
            if not ok:
                return False, f"拉镜像失败：{msg}", None
    except docker_api.DockerError as e:
        return False, f"查镜像失败：{e}", None
    try:
        cid = client.create_container(container, image, env={
            "POSTGRES_USER": PG_USER, "POSTGRES_PASSWORD": PG_PASS,
            "POSTGRES_DB": "postgres"})
        client.start_container(cid)
    except docker_api.DockerError as e:
        return False, f"起容器失败：{e}", None
    ok, msg = client.wait_postgres(cid, user=PG_USER, db="postgres", timeout=90)
    if not ok:
        return False, f"PG 未就绪：{msg}", cid
    return True, "PG ready", cid


def container_healthy(client, cid):
    """⇒ 容器是否还活着且 PG 还能应答（用于**域间自愈**）"""
    if not cid:
        return False
    try:
        rc, _, _ = docker_api.docker_exec(
            cid, ["pg_isready", "-U", PG_USER, "-d", "postgres"])
        return rc == 0
    except Exception:
        return False


def psql(client, cid, db, sql, stdin_data=None):
    argv = ["psql", "-U", PG_USER, "-d", db, "-v", "ON_ERROR_STOP=1"]
    if stdin_data is not None:
        argv += ["-f", "-"]
    return docker_api.docker_exec(cid, argv, stdin_data=stdin_data)


def replay_domain(client, cid, domain):
    """⇒ (status, 说明行列表, db)
    status ∈ {"ok", "infra", "sql"}
      · "infra" = 基础设施类失败（容器被杀 / 连不上 / 建库删库失败）⇒ BLOCKED
      · "sql"   = 迁移 SQL 被 PG 拒绝（迁移脚本本身的问题）⇒ BLOCKED
      · "ok"    = 迁移全部执行成功 ⇒ 可以进比对
    """
    sqls = cds.discover_rag_sql(domain)
    db = f"rag_{domain}_db"
    lines = []
    # ⚠️ DROP/CREATE 用 -c（不进脚本），避免脚本模式下的多语句事务语义
    for sql in (f"DROP DATABASE IF EXISTS {db};", f"CREATE DATABASE {db};"):
        rc, out, err = docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                  "-c", sql])
        if rc != 0:
            kind = classify_failure(rc, err, out)
            lines.append(f"🔴 {'建库/删库失败' if 'CREATE' in sql else '删旧库失败'}"
                         f"（**基础设施类** ⇒ BLOCKED，不是契约漂移）")
            for line in (err or out).strip().splitlines()[:6]:
                lines.append(f"    {line}")
            return kind, lines, db

    if not sqls:
        return "ok", [f"本域**没有迁移脚本**（{domain} 域无数据库）"], db

    for rel in sqls:
        path = os.path.join(REPO_ROOT, rel)
        with open(path, encoding="utf-8", errors="replace") as f:
            content = f.read()
        rc, out, err = psql(client, cid, db, None, stdin_data=content)
        if rc != 0:
            kind = classify_failure(rc, err, out)
            if kind == "infra":
                lines.append(f"🔴 迁移脚本**执行中断**（**基础设施类** ⇒ BLOCKED）：{rel}")
                for line in (err or out).strip().splitlines()[:8]:
                    lines.append(f"    {line}")
            else:
                lines.append(f"🔴 迁移脚本**执行失败**：{rel}")
                lines.append("   ⚠️ 这是 **SQL 层错误**（PG 拒绝了这条语句）"
                             "⇒ **迁移脚本本身很可能有缺陷**（不是环境问题）"
                             "⇒ 但「检查没做完」是事实 ⇒ 判 **BLOCKED**")
                for line in (err or out).strip().splitlines()[:12]:
                    lines.append(f"    {line}")
            return kind, lines, db
        lines.append(f"  ✔ {rel}")
    return "ok", lines, db



def run_checker(domain, container, db, baseline):
    """⇒ (退出码, 输出) —— 调用**比对引擎**（不复制比对逻辑）"""
    cmd = [sys.executable,
           os.path.join(TOOLS_DIR, "check-ddl-static.py"),
           "--domain", domain, "--rag-only",
           "--semantic-baseline", baseline,
           "--container", container, "--rag-db", db]
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def first_failure_reason(out):
    """⇒ 失败原因的第一行（给汇总表用）"""
    lines = out.splitlines()
    for i, ln in enumerate(lines):
        if "🔴 与基准不一致" in ln:
            for nxt in lines[i + 1:]:
                s = nxt.strip()
                if s.startswith("·"):
                    return s.lstrip("· ").strip()
            return ln.strip()
    for ln in lines:
        if "🔴" in ln:
            return ln.strip()
    return ""


def main():
    ap = argparse.ArgumentParser(description="R6 · 数据库 Schema 契约门（CI 入口）")
    ap.add_argument("--domain", action="append", default=[],
                    help="要查的域（可重复）；不给 ⇒ 全部 9 个域")
    ap.add_argument("--all", action="store_true", help="9 个域全查")
    ap.add_argument("--image", default=DEFAULT_IMAGE, help="PG 镜像")
    ap.add_argument("--container", default="ci-schema-pg", help="PG 容器名")
    ap.add_argument("--keep", action="store_true", help="跑完保留容器（排查用）")
    ap.add_argument("--report", default=None, help="把结论写成 JSON 报告")
    args = ap.parse_args()

    domains = args.domain or sorted(cds.DOMAINS)
    if args.all:
        domains = sorted(cds.DOMAINS)

    log("[r6] ============================================================")
    log("[r6] R6 · 数据库 Schema 契约门（**纯 Java 侧**：无 Go 仓 / 无 Go 工具链）")
    log(f"[r6] 域：{' '.join(domains)}")
    log(f"[r6] PG 镜像：{args.image}   容器：{args.container}")
    log("[r6] ============================================================")

    client = docker_api.DockerClient()
    if not client.ping():
        log("[r6] ❌ Docker daemon 不可达（DOCKER_HOST="
            f"{client.host}）⇒ 环境不满足 ⇒ **BLOCKED**")
        log("[r6]    （CI 形态：DinD 的内层 daemon，socket 由 ci/scripts/dind-run.sh 挂进来）")
        return 2

    ok, msg, cid = start_pg(client, args.container, args.image, args.keep)
    if not ok:
        log(f"[r6] ❌ {msg} ⇒ 全部域 **BLOCKED**（不是「通过」）")
        return 2
    log(f"[r6] {msg}（容器 {args.container}，cid {cid[:12]}）")

    results = []
    current_container = args.container
    rebuild_seq = 0
    try:
        for domain in domains:
            baseline = os.path.join(BASELINE_DIR, f"{domain}.json")
            if not os.path.exists(baseline):
                log(f"\n===== 域 {domain} · BLOCKED =====")
                log(f"[r6] ❌ 语义基准不存在：{baseline} ⇒ 检查没做成")
                results.append({"domain": domain, "verdict": "BLOCKED",
                                "exit_code": 2, "reason": "基准缺失"})
                continue

            # ---- ⭐ N/A 短路：**不依赖数据库** ----------------------------
            # hot 域：Go 侧无 gorm、RAG 侧无 migration ⇒ DDL 维度**不适用**
            # ⇒ NOT_RUN(3)（「本域不适用」，**不是** BLOCKED、也不是「已对齐」）
            # ⚠️ 判据要**两个都成立**：域预设没有预期业务表 **且** 仓里没有迁移脚本
            #    ⇒ 将来谁给 hot 加了迁移，本短路自动失效（回到正常检查）。
            expected = cds.DOMAINS[domain].get("expected_tables")
            has_sql = bool(cds.discover_rag_sql(domain))
            if expected is not None and not expected and not has_sql:
                log(f"\n===== 域 {domain} · NOT_RUN =====")
                log(f"[r6] ⚠️ 本域**没有业务表**（域预设无预期表 + 仓内无迁移脚本）"
                    "⇒ **DDL 维度不适用（N/A）**")
                log("[r6]    ⚠️ 「本域无对象可比」**不是**「DDL 已对齐」")
                results.append({"domain": domain, "verdict": "NOT_RUN",
                                "exit_code": 3, "reason": "DDL 维度不适用（N/A）"})
                continue

            # ---- 域间**容器自愈**：上一域把容器搞挂 ⇒ 这里重建 ------------
            if not container_healthy(client, cid):
                log(f"[r6] ⚠️ PG 容器不健康（上一域可能把它搞挂了）⇒ **重建容器**"
                    "（单域故障不传染后续域）")
                try:
                    client.remove_container(args.container)
                except Exception:
                    pass
                # ⚠️ **换一个新名字**重建：旧容器可能还在「停止中」⇒
                #    同名 create 会 409（实测踩到）⇒ 用递增后缀避开。
                rebuild_seq += 1
                current_container = f"{args.container}-r{rebuild_seq}"
                ok, msg, cid = start_pg(client, current_container, args.image,
                                        args.keep)
                if not ok:
                    log(f"[r6] ❌ {msg} ⇒ 本域 **BLOCKED**")
                    results.append({"domain": domain, "verdict": "BLOCKED",
                                    "exit_code": 2, "reason": "PG 起不来"})
                    continue

            log(f"\n===== 域 {domain} =====")
            # ⚠️ **任何意外异常都归 BLOCKED**，绝不崩在 Traceback 上：
            #   「检查没做成」与「检查做成且发现漂移」是**两回事**
            #   ⇒ 崩掉 ⇒ 退出码非 0 ⇒ 读数会被误判成 FAIL（契约漂移）⇒ 假红。
            #   ⇒ 这里显式兜住 ⇒ 判 BLOCKED（退出码 2）⇒ 不计入 PASS。
            try:
                status, lines, db = replay_domain(client, cid, domain)
            except Exception as exc:  # noqa: BLE001
                log(f"[r6] 🔴 本域执行时抛异常 ⇒ **BLOCKED**（环境/工具层问题，不是契约漂移）")
                log(f"    {type(exc).__name__}: {exc}")
                results.append({"domain": domain, "verdict": "BLOCKED",
                                "exit_code": 2,
                                "reason": f"执行时抛异常（{type(exc).__name__}）"})
                continue
            for ln in lines:
                log(ln)
            if status != "ok":
                log(f"[r6] ⚠️ 域 {domain} ⇒ **BLOCKED**（检查没做成，"
                    f"**不是**契约漂移）")
                results.append({"domain": domain, "verdict": "BLOCKED",
                                "exit_code": 2,
                                "reason": "迁移执行失败（环境或迁移本身坏）"})
                continue

            try:
                rc, out = run_checker(domain, current_container, db, baseline)
            except Exception as exc:  # noqa: BLE001
                log(f"[r6] 🔴 比对引擎抛异常 ⇒ **BLOCKED**（不是契约漂移）")
                log(f"    {type(exc).__name__}: {exc}")
                results.append({"domain": domain, "verdict": "BLOCKED",
                                "exit_code": 2,
                                "reason": f"比对引擎抛异常（{type(exc).__name__}）"})
                continue
            log(out.rstrip())
            verdict = VERDICT.get(rc, "BLOCKED")
            reason = first_failure_reason(out) if rc == 1 else ""
            log(f"[r6] 域 {domain} ⇒ **{verdict}**（比对引擎退出码 {rc}）")
            results.append({"domain": domain, "verdict": verdict, "exit_code": rc,
                            "reason": reason})
    finally:
        if not args.keep:
            for name in {args.container, current_container} | {
                    f"{args.container}-r{i}" for i in range(1, rebuild_seq + 1)}:
                try:
                    client.remove_container(name)
                except Exception:
                    pass

    # ---- 汇总 ------------------------------------------------------------
    log("\n[r6] ============================================================")
    log("[r6] R6 汇总")
    log("[r6] --------------------------------------------------------")
    log(f"{'域':<10} {'结论':<10} {'退出码':<8} 说明")
    for r in results:
        log(f"{r['domain']:<10} {r['verdict']:<10} {r['exit_code']:<8} "
            f"{r.get('reason') or ''}")
    n_pass = sum(1 for r in results if r["verdict"] == "PASS")
    n_fail = sum(1 for r in results if r["verdict"] == "FAIL")
    n_block = sum(1 for r in results if r["verdict"] == "BLOCKED")
    n_na = sum(1 for r in results if r["verdict"] == "NOT_RUN")
    log("[r6] --------------------------------------------------------")
    log(f"[r6] PASS={n_pass}  FAIL={n_fail}  BLOCKED={n_block}  NOT_RUN={n_na}")
    # ⚠️ 四类**分列**：NOT_RUN 不得混进 PASS，也不得混进 FAIL
    def _names(v):
        return "、".join(r["domain"] for r in results if r["verdict"] == v) or "（无）"
    log(f"[r6]   ✅ PASS      （{n_pass}）：{_names('PASS')}")
    log(f"[r6]   ❌ FAIL      （{n_fail}）：{_names('FAIL')}"
        "   ← **迁移跑完了、schema 与基准不一致** ⇒ 契约漂移")
    log(f"[r6]   ⛔ BLOCKED   （{n_block}）：{_names('BLOCKED')}"
        "   ← **检查没做成**（环境 / 迁移执行失败）⇒ **不得计入 PASS**")
    log(f"[r6]   ⚠️ NOT_RUN   （{n_na}）：{_names('NOT_RUN')}"
        "   ← **本域该维度不适用**（如 hot 域无数据库）⇒ **不是「已对齐」**")
    if n_block:
        for r in results:
            if r["verdict"] == "BLOCKED":
                log(f"[r6]      · {r['domain']}：{r.get('reason') or '（未记录原因）'}")
    if n_fail:
        for r in results:
            if r["verdict"] == "FAIL":
                log(f"[r6]      · {r['domain']}：{r.get('reason') or ''}")

    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({"results": results,
                       "summary": {"pass": n_pass, "fail": n_fail,
                                   "blocked": n_block, "not_run": n_na}},
                      f, ensure_ascii=False, indent=2)
            f.write("\n")

    if n_fail:
        log("[r6] ❌ 有域未通过 ⇒ 退出码 1")
        return 1
    if n_block:
        log("[r6] ❌ 有域 BLOCKED（检查没做成）⇒ 退出码 2")
        return 2
    log("[r6] ✅ 全部域通过（NOT_RUN 域除外）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
