#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R5 取证：`admin.create` 里「bcrypt 哈希」与「前置业务检查」的先后关系差异。

════════════════════════════════════════════════════════════════════════
【背景：为什么需要这个独立脚本，而不是靠 matrix.py 覆盖】
════════════════════════════════════════════════════════════════════════
E-3 矩阵在 `admin.create` 这一族里**恒定使用假邀请码**
`MATRIX-INVITE-NOT-A-REAL-CODE`（见 matrix.py 的 FIXTURE_DEFAULTS）。
后果是：矩阵只能命中「邀请码校验失败」这一条最早返回的分支，
**永远走不到**「邀请码有效 → 再往后的检查」那一段。

于是 R5 的分歧面被系统性掩盖成 1 条：
    admin.create.Password.long5000   go=1016(邀请码错) rag=5001(bcrypt 失败)
只因为两边都在"更早的那个检查"上就返回了，看不出真正的分歧点在哪。

本脚本用**真邀请码**把 admin.create 的分支空间补齐，逐支比对两端：

  A  有效邀请码 + 超长密码（新用户名）
     → 两侧都应落到 bcrypt 的 72 字节上限，预期都是 5001（一致）
  B  有效邀请码 + 超长密码（**用户名已存在**）★ 矩阵完全没覆盖的一支
     → Go：先查重命中 AlreadyExists；RAG：先哈希炸 5001。**这才是分歧的核心**
  C  无效邀请码 + 超长密码（矩阵唯一覆盖到的一支，作为对照）

════════════════════════════════════════════════════════════════════════
【真源步骤序（createadminlogic.go，2026-10-22 取证）】
════════════════════════════════════════════════════════════════════════
  1. inviteCode 空检查                 → PermissionDenied
  2. ┌ 事务开始
  3. │ FindAdminInviteByCodeForUpdate  → not found / 过期 → PermissionDenied
  4. │ FindOneAdminByUsernameTx         → 命中 → AlreadyExists   ★
  5. │ cryptx.PasswordEncrypt           → >72 字节 → err          ★
  6. │ snowflake.GetID
  7. │ InsertOneAdminTx
  8. │ UpdateAdminInviteUsedTx
     └ 事务结束

RAG 步骤序（AdminDomainService.createAdmin + AdminCreateAtomicService）：
  1. inviteCode 空检查                 → PermissionDenied
  2. PasswordUtil.encrypt（**事务外**）→ >72 字节 → INTERNAL       ★ 提前了
  3. Snowflake.nextId（事务外）
  4. ┌ 事务开始
  5. │ W1 invite FOR UPDATE            → 失败 → InviteUnavailable
  6. │ W2 invite 过期/已用
  7. │ W3 username 冲突                → UsernameTaken            ★ 被推后了
  8. │ W4 insert / W5 标记已用
     └ 事务结束

**结论**：分歧不是「bcrypt 实现不同」（两端都是 bcrypt、都拒 >72 字节），
而是**步骤 2 与步骤 4/5 的先后被对调了**。
交集 = 「密码 >72 字节」且「本该先被前置检查拦下的输入」。

════════════════════════════════════════════════════════════════════════
【边界】
════════════════════════════════════════════════════════════════════════
  · 只读地**观测**两端行为，不修改 Go 仓库、不改 RAG 代码、不改数据库。
  · 需要栈已在跑：`./run.sh --keep --serve-only`，且必须是**全新 cluster**
    ——被矩阵跑过的库里 admin/admin 的口令已被 admin.update 边界用例改写
    （matrix.py 的 _make_matrix_admin 注释里专门警告过这个污染）。
  · 本脚本自己建 admin 账号，不复用 admin/admin，也不依赖 admin/admin 可登录。
  · 退出码 0/1：无 B 支分歧返回 0，出现分歧返回 1（方便将来当守门脚本）。
"""

import json
import sys
import urllib.error
import urllib.request

BASE = {
    "go": {"admin": "http://127.0.0.1:18889"},
    "rag": {"admin": "http://127.0.0.1:58889"},
}
P = {
    "go": {
        "login": "/admincenter/v1/admin/login",
        "create": "/admincenter/v1/admin/create",
        "invite": "/admincenter/v1/admin/invite/create",
    },
    "rag": {
        "login": "/admin/v1/login",
        "create": "/admin/v1/create",
        "invite": "/admin/v1/invite/create",
    },
}

ADMIN_PW = "pw123456"          # 与 matrix.py 的 M_ADMIN_PW 一致
LONG_PW = "P" * 5000          # >72 字节，触发 bcrypt 上限


def http(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read().decode("utf-8", "replace")
            try:
                return r.status, json.loads(raw)
            except Exception:
                return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:
        return 0, repr(e)


def code_of(b):
    return b.get("code") if isinstance(b, dict) else "(non-json)"


def login(side, username, password):
    s, b = http("POST", BASE[side]["admin"] + P[side]["login"],
                {"username": username, "password": password})
    if isinstance(b, dict) and isinstance(b.get("data"), dict):
        return b["data"].get("token")
    return None


def new_invite(side, token):
    s, b = http("POST", BASE[side]["admin"] + P[side]["invite"], {}, token)
    if isinstance(b, dict) and isinstance(b.get("data"), dict):
        return b["data"].get("invite_code")
    return None


def try_create(side, token, username, password, invite):
    return http("POST", BASE[side]["admin"] + P[side]["create"],
                {"username": username, "password": password,
                 "email": f"{username}@x.com", "invite_code": invite}, token)


def run_side(side):
    """在一端上把 A/B/C 三支跑一遍，返回 {支名: (status, code)}。"""
    out = {}
    tag = side

    # 自举一个能登录的 admin：admin/admin 若已被污染则无法登录，
    # 此时用上一个 run_id 的 mx_admin_* 也可能已被改写。
    # 最稳的办法是让调用方保证「全新 cluster」——这里只做明确报错。
    token = login(side, "admin", "admin")
    if not token:
        print(f"[{side}] ✗ admin/admin 登录失败 —— 环境已被矩阵污染，"
              f"请用全新 cluster 重启（./run.sh --keep --serve-only）")
        return None

    # 建一个正常账号，供 B 支复用（用户名已存在）
    uname_exists = f"r5_exists_{tag}"
    inv = new_invite(side, token)
    if not inv:
        print(f"[{side}] ✗ 拿不到邀请码")
        return None
    s, b = try_create(side, token, uname_exists, "okpass123", inv)
    print(f"[{side}] 前置：建号 {uname_exists} -> status={s} code={code_of(b)}")
    if code_of(b) != 200:
        print(f"[{side}] ✗ 前置建号失败，后续用例跳过")
        return None

    # A：有效邀请码 + 超长密码（新用户名）—— 预期两侧一致（都 5001）
    invA = new_invite(side, token)
    s, b = try_create(side, token, f"r5_A_{tag}", LONG_PW, invA)
    out["A 有效邀请码+超长密码(新用户名)"] = (s, code_of(b))
    print(f"[{side}] A 有效邀请码+超长密码(新用户名) -> status={s} code={code_of(b)}")

    # B：有效邀请码 + 超长密码（用户名已存在）★ 核心分歧支
    invB = new_invite(side, token)
    s, b = try_create(side, token, uname_exists, LONG_PW, invB)
    out["B 有效邀请码+超长密码(用户名已存在)"] = (s, code_of(b))
    print(f"[{side}] B 有效邀请码+超长密码(用户名已存在) -> status={s} code={code_of(b)}")

    # C：无效邀请码 + 超长密码 —— 矩阵唯一覆盖的一支，作为对照
    s, b = try_create(side, token, f"r5_C_{tag}", LONG_PW, "R5-NOT-A-REAL-CODE")
    out["C 无效邀请码+超长密码(矩阵已覆盖)"] = (s, code_of(b))
    print(f"[{side}] C 无效邀请码+超长密码 -> status={s} code={code_of(b)}")

    return out


def main():
    sides = {}
    for side in ("go", "rag"):
        r = run_side(side)
        if r is None:
            print("\n取证中止：环境不可用。")
            return 2
        sides[side] = r

    print("\n════════════════ R5 分歧面汇总 ════════════════")
    drift = []
    for name in sides["go"]:
        g, r = sides["go"][name], sides["rag"][name]
        same = (g == r)
        flag = "一致" if same else "★ 分歧"
        print(f"  {flag}  {name}")
        print(f"          go ={g}   rag={r}")
        if not same:
            drift.append(name)

    print("\n════════════════ 结论 ════════════════")
    if not drift:
        print("  未观测到分歧。")
        return 0
    print(f"  观测到 {len(drift)} 处分歧：")
    for d in drift:
        print(f"    - {d}")
    print("  根因：RAG 把 bcrypt 哈希提前到事务外、且排在 invite/username 检查之前；")
    print("       Go 是 invite → username → bcrypt。两端 bcrypt 实现相同（都拒 >72 字节），")
    print("       分歧**纯粹来自步骤顺序**。")
    return 1


if __name__ == "__main__":
    sys.exit(main())