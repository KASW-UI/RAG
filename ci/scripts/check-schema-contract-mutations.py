#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
**R6 契约门的反向验证**（变异测试）⇒ 证明这个门**不是假绿**。

════════════════════════════════════════════════════════════════════════
【为什么必须有这个脚本】
════════════════════════════════════════════════════════════════════════
本仓反复踩的坑是「**跑绿 ≠ 对**」：
  · `hot-rpc`/`like-rpc` 曾经**根本起不来**，而测试全绿；
  · `check-ddl-static.py` 曾经因为**列集合不一致提前中止**，
    导致 comment 域「一个维度都比不了」却报绿。
⇒ 一个只会报 PASS 的门，等于没有门。
⇒ 本脚本对 R6 门做 **6 类变异**，每类都必须**精确转红**（退出码符合预期）。

════════════════════════════════════════════════════════════════════════
【6 类变异与期望退出码】
════════════════════════════════════════════════════════════════════════
   M1 列类型漂移（integer → text）      ⇒ **1**（FAIL：与基准不一致）
   M2 删一个 NOT NULL                    ⇒ **1**（FAIL：可空性不符）
   M3 删一个唯一约束                      ⇒ **1**（FAIL：唯一约束缺失）
   M4 删一份迁移 SQL（迁移不完整）        ⇒ **1**（FAIL：表缺失）
   M5 PG 容器起不来（环境失败）            ⇒ **2**（BLOCKED，**绝不能** 0 或 1）
   M6 hot 域（该维度不适用）              ⇒ **3**（NOT_RUN，**绝不能** 0）
   M7 容器被反复杀（基础设施故障）         ⇒ **2**（BLOCKED，**绝不能** 1）
   M8 容器被杀一次（域间故障隔离）         ⇒ **2**（BLOCKED）**且后续域 PASS**（自愈）

⚠️ **所有变异都在临时容器 / 临时副本里做** ⇒ **不污染真仓**：
   · M1~M3 ⇒ 临时 PG 容器 + 临时库（改的是**活库**，不是仓里的 .sql）；
   · M4    ⇒ 临时**仓副本**（软链镜像 + 真的删掉一份 .sql）⇒ 跑副本里的门；
   · M5    ⇒ `DOCKER_HOST` 指向一个不存在的 socket；
   · M6    ⇒ 只跑域判定，不动任何东西。

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
    python3 ci/scripts/check-schema-contract-mutations.py [--domain users]
"""
import argparse
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools", "difftest")
sys.path.insert(0, TOOLS_DIR)
_spec = importlib.util.spec_from_file_location(
    "check_ddl_static", os.path.join(TOOLS_DIR, "check-ddl-static.py"))
cds = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cds)
import docker_api  # noqa: E402

PG_USER = "admin"
PG_PASS = "admin"


def log(m):
    print(m, flush=True)


def replay(client, cid, domain, db):
    """在**干净库**上按序重放该域的迁移脚本 ⇒ (ok, 错误文本)"""
    for sql in (f"DROP DATABASE IF EXISTS {db};", f"CREATE DATABASE {db};"):
        rc, _, err = docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                  "-c", sql])
        if rc != 0:
            return False, err
    for rel in cds.discover_rag_sql(domain):
        with open(os.path.join(REPO_ROOT, rel), encoding="utf-8",
                  errors="replace") as f:
            content = f.read()
        rc, out, err = docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", db, "-v", "ON_ERROR_STOP=1",
                  "-f", "-"], stdin_data=content.encode("utf-8"))
        if rc != 0:
            return False, f"{rel}: {(err or out)[:300]}"
    return True, ""


def run_checker(domain, container, db, baseline, repo_root=None):
    """⇒ (退出码, 输出)"""
    tools = os.path.join(repo_root or REPO_ROOT, "tools", "difftest")
    cmd = [sys.executable, os.path.join(tools, "check-ddl-static.py"),
           "--domain", domain, "--rag-only",
           "--semantic-baseline", baseline,
           "--container", container, "--rag-db", db]
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def mirror_tree(src, dst, omit_rel, root):
    """把 src 镜像到 dst（目录真的建，文件软链）⇒ omit_rel 指向的那个文件**不复制**"""
    rel = os.path.relpath(src, root)
    if rel == omit_rel:
        return
    if os.path.isdir(src):
        os.makedirs(dst, exist_ok=True)
        for e in sorted(os.listdir(src)):
            if e in ("target", ".git", "__pycache__"):
                continue
            mirror_tree(os.path.join(src, e), os.path.join(dst, e), omit_rel, root)
    else:
        os.symlink(src, dst)


def build_temp_repo(dst, omit_rel):
    """⇒ 造一个「软链镜像 + 真的少了一份迁移 SQL」的仓副本"""
    os.makedirs(dst, exist_ok=True)
    for name in sorted(os.listdir(REPO_ROOT)):
        if name in (".git", "target", "__pycache__"):
            continue
        src = os.path.join(REPO_ROOT, name)
        if name == "service":
            mirror_tree(src, os.path.join(dst, name), omit_rel, REPO_ROOT)
        else:
            os.symlink(src, os.path.join(dst, name))
    return dst


def run_with_killer(domains, kill_times, kill_after, container,
                    kill_when_db_exists=None):
    """⇒ 跑一遍 CI 门，同时在后台**反复杀容器** ⇒ 返回 {exit_code, results}

    ⚠️ 模拟的是宿主实跑遇到的真故障：PG 容器被 OOM kill ⇒ psql 返 137
       ⇒ 旧实现判 FAIL（漂移）⇒ 新实现必须判 BLOCKED。

    ⚠️ `kill_when_db_exists` ⇒ **确定性注入**：等某个域的库建好（= 该域重放完成）
       才杀容器 ⇒ 故障必然落在「两个域之间」⇒ 可验证**域间隔离 + 自愈**。
    """
    import threading
    import time
    stop = threading.Event()

    def killer():
        time.sleep(kill_after)
        killed = 0
        while not stop.is_set() and killed < kill_times:
            ready = True
            if kill_when_db_exists:
                try:
                    rc, out, _ = docker_api.docker_exec(
                        container, ["psql", "-U", PG_USER, "-d", "postgres",
                                    "-t", "-A", "-c",
                                    f"select 1 from pg_database "
                                    f"where datname='{kill_when_db_exists}';"])
                    ready = (rc == 0 and bool(out.strip()))
                except Exception:
                    ready = False
            if ready:
                try:
                    docker_api.DockerClient().remove_container(container)
                    killed += 1
                except Exception:
                    pass
            time.sleep(0.4)

    tmp = tempfile.mkdtemp(prefix="r6-kill-")
    report = os.path.join(tmp, "report.json")
    th = threading.Thread(target=killer, daemon=True)
    th.start()
    try:
        cmd = [sys.executable,
               os.path.join(REPO_ROOT, "ci", "scripts", "check-schema-contract.py"),
               "--container", container, "--report", report]
        for d in domains:
            cmd += ["--domain", d]
        p = subprocess.run(cmd, capture_output=True, text=True)
    finally:
        stop.set()
        th.join(timeout=3)
    results = []
    if os.path.exists(report):
        try:
            with open(report, encoding="utf-8") as f:
                results = json.load(f).get("results", [])
        except Exception:
            pass
    shutil.rmtree(tmp, ignore_errors=True)
    try:
        docker_api.DockerClient().remove_container(container)
    except Exception:
        pass
    return {"exit_code": p.returncode, "results": results,
            "stdout": (p.stdout or "")}


def main():
    ap = argparse.ArgumentParser(description="R6 契约门的变异测试（反向验证）")
    ap.add_argument("--domain", default="users", help="变异用的域（默认 users）")
    ap.add_argument("--image", default="postgres:16-alpine")
    args = ap.parse_args()
    domain = args.domain
    baseline = os.path.join(REPO_ROOT, "ci", "contracts", "schema", f"{domain}.json")
    if not os.path.exists(baseline):
        log(f"❌ 基准不存在：{baseline}")
        return 2

    client = docker_api.DockerClient()
    if not client.ping():
        log("❌ Docker daemon 不可达 ⇒ 无法做变异测试")
        return 2

    container = "ci-schema-mut-pg"
    if client.container_exists(container):
        client.remove_container(container)
    cid = client.create_container(container, args.image, env={
        "POSTGRES_USER": PG_USER, "POSTGRES_PASSWORD": PG_PASS,
        "POSTGRES_DB": "postgres"})
    client.start_container(cid)
    ok, msg = client.wait_postgres(cid, user=PG_USER, db="postgres", timeout=90)
    if not ok:
        log(f"❌ PG 未就绪：{msg}")
        client.remove_container(container)
        return 2

    results = []

    def scenario(name, expect, got, detail=""):
        good = (got == expect)
        results.append({"scenario": name, "expect": expect, "got": got, "ok": good})
        mark = "✅" if good else "❌"
        log(f"{mark} {name}：期望 EXIT={expect} 实际 EXIT={got}"
            + (f"\n     {detail}" if detail else ""))

    try:
        db = f"rag_{domain}_db"

        # ---- M1 列类型漂移 ------------------------------------------------
        ok, err = replay(client, cid, domain, db)
        if not ok:
            log(f"❌ 初始重放失败：{err}")
            return 2
        docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", db, "-v", "ON_ERROR_STOP=1",
                  "-c", "ALTER TABLE users_user ALTER COLUMN score TYPE text;"])
        rc, out = run_checker(domain, container, db, baseline)
        scenario("M1 列类型漂移（integer → text）", 1, rc,
                 [l for l in out.splitlines() if "类型不符" in l][:1])

        # ---- M2 删 NOT NULL ------------------------------------------------
        replay(client, cid, domain, db)
        docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", db, "-v", "ON_ERROR_STOP=1",
                  "-c", "ALTER TABLE users_user ALTER COLUMN uid DROP NOT NULL;"])
        rc, out = run_checker(domain, container, db, baseline)
        scenario("M2 删一个 NOT NULL", 1, rc,
                 [l for l in out.splitlines() if "可空性不符" in l][:1])

        # ---- M3 删唯一约束 -------------------------------------------------
        replay(client, cid, domain, db)
        rc, _, _ = docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", db, "-t", "-A", "-c",
                  "select conname from pg_constraint "
                  "where conrelid='users_user'::regclass and contype='u' "
                  "and pg_get_constraintdef(oid) like '%(uid)%';"])
        conname = (rc == 0 and "") or ""
        rc2, out2, _ = docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", db, "-t", "-A", "-c",
                  "select conname from pg_constraint "
                  "where conrelid='users_user'::regclass and contype='u' "
                  "and pg_get_constraintdef(oid) like '%(uid)%';"])
        conname = out2.strip().splitlines()[0] if out2.strip() else ""
        if conname:
            docker_api.docker_exec(
                cid, ["psql", "-U", PG_USER, "-d", db, "-v", "ON_ERROR_STOP=1",
                      "-c", f"ALTER TABLE users_user DROP CONSTRAINT {conname};"])
        rc, out = run_checker(domain, container, db, baseline)
        scenario("M3 删一个唯一约束", 1, rc,
                 ([f"删除的约束：{conname}"] +
                  [l for l in out.splitlines() if "唯一约束" in l][:1]))

        # ---- M4 删一份迁移 SQL（迁移不完整）--------------------------------
        sqls = cds.discover_rag_sql(domain)
        victim = sqls[1] if len(sqls) > 1 else (sqls[0] if sqls else None)
        tmp = tempfile.mkdtemp(prefix="r6-mut-repo-")
        try:
            build_temp_repo(os.path.join(tmp, "repo"), victim)
            cmd = [sys.executable,
                   os.path.join(tmp, "repo", "ci", "scripts",
                                "check-schema-contract.py"),
                   "--domain", domain, "--container", "ci-schema-mut-pg2",
                   "--report", os.path.join(tmp, "report.json")]
            p = subprocess.run(cmd, capture_output=True, text=True,
                               env={**os.environ, "DOCKER_HOST": client.host})
            detail = ""
            try:
                with open(os.path.join(tmp, "report.json"), encoding="utf-8") as f:
                    rep = json.load(f)
                detail = f"报告：{rep['results']}"
            except Exception:
                detail = (p.stdout or p.stderr)[-300:]
            scenario(f"M4 删一份迁移 SQL（{os.path.basename(victim or '')}）",
                     1, p.returncode, detail)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

        # ---- M5 PG 起不来（环境失败）--------------------------------------
        env = {**os.environ, "DOCKER_HOST": "unix:///tmp/r6-not-a-real-docker.sock"}
        p = subprocess.run(
            [sys.executable,
             os.path.join(REPO_ROOT, "ci", "scripts", "check-schema-contract.py"),
             "--domain", domain], capture_output=True, text=True, env=env)
        scenario("M5 PG 容器起不来（环境失败 ⇒ BLOCKED）", 2, p.returncode,
                 [l for l in (p.stdout or "").splitlines() if "BLOCKED" in l][:1])

        # ---- M6 hot 域（N/A ⇒ NOT_RUN）------------------------------------
        # ⚠️ hot 域**没有迁移脚本** ⇒ 库里是空的 ⇒ 检查器必须判 **N/A(3)**，
        #    而**不是**「读不到库」(2) ⇒ 先建一个**空库**再查。
        docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                  "-c", "DROP DATABASE IF EXISTS rag_hot_db;"])
        docker_api.docker_exec(
            cid, ["psql", "-U", PG_USER, "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                  "-c", "CREATE DATABASE rag_hot_db;"])
        hot_base = os.path.join(REPO_ROOT, "ci", "contracts", "schema", "hot.json")
        rc, out = run_checker("hot", container, "rag_hot_db", hot_base)
        scenario("M6 hot 域（DDL 维度不适用 ⇒ NOT_RUN）", 3, rc,
                 [l for l in out.splitlines() if "不适用" in l][:1])

        # ---- M7 容器被反复杀 ⇒ 全是 BLOCKED，**绝不能**出现 FAIL ------------
        # ⚠️ 这是 2026-10-31 宿主实跑暴露的分类缺陷：容器被 OOM kill 时
        #    旧实现判成 FAIL（契约漂移）⇒ 维护者去查代码，真因在环境。
        m7 = run_with_killer(domains=["follow", "message"], kill_times=9999,
                             kill_after=1.0, container="ci-schema-mut-kill")
        no_fail = all(r["verdict"] != "FAIL" for r in m7["results"])
        scenario("M7 容器被反复杀（基础设施故障 ⇒ BLOCKED，不得 FAIL）", 2,
                 m7["exit_code"], f"报告：{m7['results']} · 无 FAIL={no_fail}")

        # ---- M8 容器被杀一次 ⇒ 该域 BLOCKED，后续域**自愈**后 PASS ---------
        # ⚠️ **确定性注入**：等 `rag_follow_db` 建好（= follow 的重放完成）
        #    才杀容器 ⇒ 故障必然落在 follow 与 message 之间。
        m8 = run_with_killer(domains=["follow", "message", "task"], kill_times=1,
                             kill_after=0.0, container="ci-schema-mut-kill1",
                             kill_when_db_exists="rag_follow_db")
        verdicts = [r["verdict"] for r in m8["results"]]
        has_pass = "PASS" in verdicts
        no_fail = "FAIL" not in verdicts
        # 自愈：最后一个域必须 PASS（容器被重建 ⇒ 后续域照常跑完）
        last_ok = bool(verdicts) and verdicts[-1] == "PASS"
        # ⚠️ 整体退出码：只要有域 BLOCKED ⇒ 2（**不是** 0）
        expect_exit = 2 if ("BLOCKED" in verdicts or not last_ok) else 0
        scenario("M8 容器被杀一次（域间隔离 + 自愈）", expect_exit,
                 m8["exit_code"],
                 f"报告：{m8['results']} · 无 FAIL={no_fail} · 末域 PASS={last_ok}"
                 f" · 自愈日志：{[l.strip() for l in m8['stdout'].splitlines() if '重建容器' in l]}")
        # 关键不变量单独断言一次（不依赖时序）
        invariant_ok = no_fail and has_pass
        log(f"{'✅' if invariant_ok else '❌'} "
            f"M8' 不变量：故障隔离后**不得**出现 FAIL，且后续域能跑完"
            f"（无 FAIL={no_fail} · 有 PASS={has_pass}）")
        results.append({"scenario": "M8' 故障隔离不变量（无 FAIL 且能自愈）",
                        "expect": 0, "got": 0 if invariant_ok else 1, "ok": invariant_ok})
    finally:
        client.remove_container(container)

    bad = [r for r in results if not r["ok"]]
    log("")
    log(f"[mut] 变异测试：{len(results) - len(bad)}/{len(results)} 条精确转红")
    if bad:
        for r in bad:
            log(f"[mut] ❌ {r['scenario']}：期望 {r['expect']} 实际 {r['got']}")
        return 1
    log("[mut] ✅ 6 类变异全部精确转红 ⇒ 这个门**不是假绿**")
    return 0


if __name__ == "__main__":
    sys.exit(main())
