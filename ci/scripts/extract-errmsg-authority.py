#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
**R5 错误码基准的「Go 真源派生」提取器** ⇒ 把基准的权威内容从 Java 现状里剥离出来。

════════════════════════════════════════════════════════════════════════
【为什么有这个脚本】
════════════════════════════════════════════════════════════════════════
R5 的基准（`ci/contracts/errmsg/*.txt`）过去是**「Java 侧探针的 dump」** ⇒
权威内容其实是「Java 当前实现」，Go 只出现在文件头的注释里 ⇒ **方向反了**
（先读 Java → 整理成标准 → 让 CI 检查 Java 是否符合这份标准）。

本脚本把基准的权威内容改成**从 Go 真源机械提取**：
  · `const` 块里的**全部**错误码常量（名字 + 数值）
  · `codeMsg` 表里的**全部**条目（码 → 文案）
  · `GetErrMsg` 的**回落规则**（未知码 ⇒ 回落到 `codeMsg[Error]`）

⚠️ **task 域没有码表**（Go 的 task 服务在调用点内联 `status.Error(codes.X, "...")`）
⇒ 它的权威 = **逐调用点的错误码与文案**（`GOCALL` 段）⇒ **不假装有码表**。

════════════════════════════════════════════════════════════════════════
【可复算性】
════════════════════════════════════════════════════════════════════════
  · 同样的输入（同一份 Go 真源）⇒ 同样的输出（逐字节）⇒ `--check` 可当守门用
  · 输出里记录 Go 源文件的 **sha256** 与 **行号** ⇒ 基准与真源是否同步可机器复核
  · **只读** Go 真源：脚本不写 Go 仓任何一个字节

════════════════════════════════════════════════════════════════════════
【输出格式（`# >>> go-authority >>>` 段的正文）】
════════════════════════════════════════════════════════════════════════
  GOCONST|<code>|<Go 常量名>       —— const 块里的全部错误码常量
  GOTABLE|<code>|<文案>            —— codeMsg 表里的全部条目
  GOFALLBACK|<文案>                —— GetErrMsg 对未知码的回落文案（= codeMsg[Error]）
  GOCALL|<调用点>|<Java 段>|<键前缀>|<码/状态>|<文案>
                                   —— **仅 task 域**：逐调用点的错误码与文案
                                      （文案 = `*` ⇒ 运行期相关，不可逐字比对）

════════════════════════════════════════════════════════════════════════
【用法】
════════════════════════════════════════════════════════════════════════
  # 打印某域的 go-authority 正文
  python3 ci/scripts/extract-errmsg-authority.py --emit like

  # 打印全部域（供逐字节比对）
  python3 ci/scripts/extract-errmsg-authority.py --emit-all

  # 独立复算：把提取结果与仓内基准的 go-authority 段逐字节比对
  python3 ci/scripts/extract-errmsg-authority.py --check

  # 机器可读
  python3 ci/scripts/extract-errmsg-authority.py --json

  # Go 真源位置（默认 = 仓库同级的 Sea-RideTheWind_golang）
  --go-repo PATH / 环境变量 GO_TRUTH_REPO
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONTRACTS_DIR = os.path.join(REPO_ROOT, "ci", "contracts", "errmsg")

DEFAULT_GO_REPO = os.environ.get("GO_TRUTH_REPO") or os.path.join(
    os.path.dirname(os.path.dirname(REPO_ROOT)), "Sea-RideTheWind_golang")

# ── 域规格表 ────────────────────────────────────────────────────────────────
# kind=table    ⇒ Go 侧有 const 块 + codeMsg 表 + GetErrMsg 回落
# kind=callsite ⇒ Go 侧**没有码表** ⇒ 权威 = 逐调用点
# -----------------------------------------------------------------------------
DOMAINS: dict[str, dict] = {
    "article": {
        "kind": "table",
        "go_file": "service/article/common/errmsg/errmsg.go",
        "baseline": "article.txt",
    },
    "comment": {
        "kind": "table",
        "go_file": "service/comment/common/errmsg/errmsg.go",
        "baseline": "comment.txt",
    },
    "favorite": {
        "kind": "table",
        "go_file": "service/favorite/common/errmsg.go",
        "baseline": "favorite.txt",
    },
    "follow": {
        "kind": "table",
        "go_file": "service/follow/common/errmsg.go",
        "baseline": "follow.txt",
    },
    "hot": {
        "kind": "table",
        "go_file": "service/hot/common/errmsg.go",
        "baseline": "hot.txt",
    },
    "like": {
        "kind": "table",
        "go_file": "service/like/common/errmsg/errmsg.go",
        "baseline": "like.txt",
    },
    "message": {
        "kind": "table",
        "go_file": "service/message/common/errmsg.go",
        "baseline": "message.txt",
    },
    "users": {
        "kind": "table",
        "go_file": "service/user/common/errmsg/errmsg.go",
        "baseline": "users.txt",
    },
    "task-rpc": {
        "kind": "callsite",
        "go_file": "service/task/rpc/internal/logic/get_task_logic.go",
        "baseline": "task-rpc.txt",
        "callsites": [
            # (行号, 必须出现在该行的片段, Java 段, 键前缀, 码/状态, 文案)
            (44, 'status.Error(codes.InvalidArgument, "user_id invalid")',
             "RPCUID", "rpc_uid_", "invalid_argument", "user_id invalid"),
            (59, "return nil, err",
             "RPCBARE", "rpc_bare_", "unknown", "*"),
        ],
    },
    "task-api": {
        "kind": "callsite",
        "go_file": "service/task/api/internal/types/types.go",
        "baseline": "task-api.txt",
        "callsites": [
            (35, 'errors.New("id must be a string or integer")',
             "API", "api_id_", "400", "id must be a string or integer"),
            (41, 'errors.New("user_id invalid")',
             "API", "api_id_", "400", "user_id invalid"),
            (46, 'errors.New("user_id invalid")',
             "API", "api_id_", "400", "user_id invalid"),
        ],
        # handler 的 decode 错误出口（文案由 JSON 库决定 ⇒ 运行期相关 ⇒ `*`）
        "extra_callsites": [
            ("service/task/api/internal/handler/task/gettaskhandler.go", 22,
             "httpx.ErrorCtx", "OBS", "obs_api_", "400", "*"),
        ],
    },
}

# ── Go 源码解析 ──────────────────────────────────────────────────────────────
CONST_RE = re.compile(r"^\s*([A-Za-z_]\w*)\s*=\s*(-?\d+)\s*(?://.*)?$")
MAP_ENTRY_RE = re.compile(r'^\s*([A-Za-z_]\w*)\s*:\s*"((?:[^"\\]|\\.)*)"\s*,?\s*(?://.*)?$')

_GO_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "'": "'", "0": "\0"}


def go_unescape(s: str) -> str:
    """Go 字符串字面量反转义（**不**用 unicode_escape ⇒ 否则中文会被 Latin-1 化）"""
    if "\\" not in s:
        return s
    out, i = [], 0
    while i < len(s):
        ch = s[i]
        if ch != "\\" or i + 1 >= len(s):
            out.append(ch)
            i += 1
            continue
        nxt = s[i + 1]
        if nxt == "u":
            out.append(chr(int(s[i + 2:i + 6], 16)))
            i += 6
            continue
        if nxt == "x":
            out.append(chr(int(s[i + 2:i + 4], 16)))
            i += 4
            continue
        out.append(_GO_ESCAPES.get(nxt, nxt))
        i += 2
    return "".join(out)


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        h.update(f.read())
    return h.hexdigest()


def parse_go_table(path: str) -> dict:
    """⇒ {consts: {name: (code, line)}, table: {code: (msg, line)}, fallback: msg,
           ranges: {...}}"""
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    consts: dict[str, tuple[int, int]] = {}
    table: dict[int, tuple[str, int]] = {}
    ranges: dict[str, str] = {}
    fallback = None
    fallback_line = None
    geterrmsg_line = None

    # ---- const 块 ----
    in_const = False
    const_start = const_end = None
    for i, line in enumerate(lines, start=1):
        if not in_const and re.match(r"^const\s*\($", line):
            in_const = True
            const_start = i
            continue
        if in_const:
            if line.strip() == ")":
                in_const = False
                const_end = i
                break
            m = CONST_RE.match(line)
            if m:
                consts[m.group(1)] = (int(m.group(2)), i)
    if const_start is None:
        raise ValueError(f"在 {path} 里找不到 const 块")
    if const_end is None:
        raise ValueError(f"在 {path} 里的 const 块没有闭合")
    ranges["const"] = f"{const_start}-{const_end}"

    # ---- codeMsg 表 ----
    in_map = False
    map_start = map_end = None
    for i, line in enumerate(lines, start=1):
        if not in_map and re.match(r"^var\s+codeMsg\s*=\s*map\[int\]string\s*\{", line):
            in_map = True
            map_start = i
            continue
        if in_map:
            if line.strip() == "}":
                in_map = False
                map_end = i
                break
            m = MAP_ENTRY_RE.match(line)
            if m:
                name, raw = m.group(1), m.group(2)
                if name not in consts:
                    raise ValueError(f"{path}:{i} 的 codeMsg 键 {name} 不是已知的 const")
                code = consts[name][0]
                msg = go_unescape(raw)
                table[code] = (msg, i)
    if map_start is None:
        raise ValueError(f"在 {path} 里找不到 codeMsg 表")
    if map_end is None:
        raise ValueError(f"在 {path} 里的 codeMsg 表没有闭合")
    ranges["table"] = f"{map_start}-{map_end}"

    # ---- GetErrMsg 回落 ----
    for i, line in enumerate(lines, start=1):
        if line.startswith("func GetErrMsg("):
            geterrmsg_line = i
            break
    if geterrmsg_line is None:
        raise ValueError(f"在 {path} 里找不到 GetErrMsg")
    body = "\n".join(lines[geterrmsg_line - 1:geterrmsg_line + 8])
    if "codeMsg[Error]" not in body:
        raise ValueError(f"{path}:{geterrmsg_line} 的 GetErrMsg 没有回落到 codeMsg[Error]")
    m = re.search(r"return\s+codeMsg\[Error\]", body)
    if not m:
        raise ValueError(f"{path}:{geterrmsg_line} 的 GetErrMsg 回落形态无法识别")
    fallback_line = geterrmsg_line + body[:m.start()].count("\n")
    if 500 not in table:
        raise ValueError(f"{path} 的 codeMsg 里没有 Error(500) ⇒ 无法确定回落文案")
    fallback = table[500][0]
    ranges["geterrmsg"] = f"{geterrmsg_line}-{geterrmsg_line + len(body.splitlines()) - 1}"

    return {
        "consts": consts,
        "table": table,
        "fallback": fallback,
        "ranges": ranges,
        "sha256": sha256_file(path),
    }


def parse_go_callsites(spec: dict, go_repo: str) -> list[dict]:
    """⇒ 逐调用点的权威值（每条都回源核对「该行确实含预期片段」）"""
    out = []
    sites = [(spec["go_file"], *c) for c in spec.get("callsites", [])]
    for extra in spec.get("extra_callsites", []):
        sites.append((extra[0], extra[1], extra[2], extra[3], extra[4], extra[5], extra[6]))
    for site in sites:
        rel, line_no, snippet, segment, prefix, value, msg = site
        path = os.path.join(go_repo, rel)
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        if line_no > len(lines):
            raise ValueError(f"{rel}:{line_no} 越界（文件只有 {len(lines)} 行）")
        actual = lines[line_no - 1]
        if snippet not in actual:
            raise ValueError(
                f"{rel}:{line_no} 不含预期片段 {snippet!r}（实际：{actual.strip()!r}）"
                " ⇒ Go 真源已变 ⇒ 必须重新裁定")
        out.append({
            "site": f"{rel}:{line_no}",
            "segment": segment,
            "prefix": prefix,
            "value": value,
            "msg": msg,
            "line": actual.strip(),
        })
    return out


def authority_lines(domain: str, go_repo: str) -> tuple[list[str], dict]:
    """⇒ (go-authority 正文行, 元信息)"""
    spec = DOMAINS[domain]
    path = os.path.join(go_repo, spec["go_file"])
    if not os.path.isfile(path):
        raise FileNotFoundError(path)
    meta: dict = {
        "domain": domain,
        "kind": spec["kind"],
        "go_file": spec["go_file"],
        "sha256": sha256_file(path),
        "baseline": spec["baseline"],
    }
    lines: list[str] = []
    if spec["kind"] == "table":
        parsed = parse_go_table(path)
        meta["ranges"] = parsed["ranges"]
        meta["fallback"] = parsed["fallback"]
        for name, (code, ln) in sorted(parsed["consts"].items(), key=lambda kv: (kv[1][0], kv[0])):
            lines.append(f"GOCONST|{code}|{name}")
        for code, (msg, ln) in sorted(parsed["table"].items()):
            lines.append(f"GOTABLE|{code}|{msg}")
        lines.append(f"GOFALLBACK|{parsed['fallback']}")
        meta["n_const"] = len(parsed["consts"])
        meta["n_table"] = len(parsed["table"])
    else:
        sites = parse_go_callsites(spec, go_repo)
        meta["ranges"] = {s["site"]: s["line"] for s in sites}
        for s in sites:
            lines.append(
                f"GOCALL|{s['site']}|{s['segment']}|{s['prefix']}|{s['value']}|{s['msg']}")
        meta["n_callsites"] = len(sites)
    return lines, meta


# ── 基准读取 ────────────────────────────────────────────────────────────────
def baseline_authority(path: str) -> list[str]:
    """⇒ 基准文件里 `# >>> go-authority >>>` 段的正文行"""
    marker = "# >>> go-authority >>>"
    out, inside = [], False
    with open(path, encoding="utf-8") as f:
        for line in f.read().splitlines():
            if line.startswith(marker):
                inside = True
                continue
            if inside:
                if line.startswith("# >>>"):
                    break
                if line.strip():
                    out.append(line)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="从 Go 真源提取 R5 错误码权威值")
    ap.add_argument("--go-repo", default=DEFAULT_GO_REPO, help="Go 真源仓路径")
    ap.add_argument("--emit", metavar="DOMAIN", help="打印某域的 go-authority 正文")
    ap.add_argument("--emit-all", action="store_true", help="打印全部域（带域分隔）")
    ap.add_argument("--check", action="store_true", help="与仓内基准逐字节比对")
    ap.add_argument("--json", action="store_true", help="机器可读输出")
    args = ap.parse_args()

    go_repo = os.path.abspath(os.path.expanduser(args.go_repo))
    if not os.path.isdir(go_repo):
        print(f"[extract] ❌ Go 真源仓不存在：{go_repo} ⇒ BLOCKED（不是漂移）",
              file=sys.stderr)
        return 2

    targets = ([args.emit] if args.emit else list(DOMAINS.keys()))
    if args.emit and args.emit not in DOMAINS:
        print(f"[extract] ❌ 未知域：{args.emit}", file=sys.stderr)
        return 2

    results = {}
    try:
        for d in targets:
            lines, meta = authority_lines(d, go_repo)
            results[d] = {"lines": lines, "meta": meta}
    except (FileNotFoundError, ValueError) as e:
        print(f"[extract] ❌ 提取失败：{e} ⇒ BLOCKED（不是漂移）", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(
            {d: {"meta": r["meta"], "lines": r["lines"]} for d, r in results.items()},
            ensure_ascii=False, indent=1))
        return 0

    if args.check:
        bad = []
        for d, r in results.items():
            base = os.path.join(CONTRACTS_DIR, DOMAINS[d]["baseline"])
            if not os.path.isfile(base):
                print(f"[extract] ❌ {d}：基准文件缺失：{base}")
                bad.append(d)
                continue
            got = baseline_authority(base)
            want = r["lines"]
            if got != want:
                print(f"[extract] ❌ {d}：基准的 go-authority 段与 Go 真源**不一致**")
                only_base = [x for x in got if x not in want]
                only_go = [x for x in want if x not in got]
                for x in only_base[:8]:
                    print(f"[extract]    基准多出：{x}")
                for x in only_go[:8]:
                    print(f"[extract]    Go 多出：{x}")
                bad.append(d)
            else:
                print(f"[extract] ✅ {d}：{len(want)} 行与 Go 真源逐字节一致"
                      f"（{r['meta']['go_file']} sha256={r['meta']['sha256'][:16]}…）")
        if bad:
            print(f"[extract] ❌ {len(bad)} 个域的基准与 Go 真源漂移：{' '.join(bad)}")
            return 1
        print("[extract] ✅ 全部域：基准 = Go 真源派生 ⇒ 复算通过")
        return 0

    if args.emit_all:
        for d, r in results.items():
            print(f"# ==== {d} ====")
            for ln in r["lines"]:
                print(ln)
        return 0

    for ln in results[targets[0]]["lines"]:
        print(ln)
    return 0


if __name__ == "__main__":
    sys.exit(main())
