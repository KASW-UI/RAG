#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
探针：Go 侧到底哪些字段是必填的。

为什么需要探针而不是只读 types.go
----------------------------------
go-zero 的 mapping 用 `json`/`form` tag 解析，`parseOption` 里只有
`optional` / `optional=x` 会置 Optional=true；`omitempty` **不在选项表里**，
会被静默忽略。所以判据看起来很干净：

    字段必填  ⟺  tag 不含 `,optional`

但这条规则与 AGENTS.md §3.4.3 里**用户已签字的 N-3** 直接冲突：
N-3 说「admin 侧 uid 走 form 绑定，key 缺失时静默保留零值 ⇒ 返回 1003」，
而 tools/difftest 实测 Go 对 admin.getuser 缺 uid 返回的是 **400**。

两者必有一错。本探针只做一件事：用真进程把 Go 的真实行为钉死。

用法：
    python3 probe-go-required-fields.py      # 只探 Go，需要 difftest 的 Go 栈已在跑
"""
import json
import sys
import urllib.error
import urllib.request

GO = {"user": "http://127.0.0.1:18888", "admin": "http://127.0.0.1:18889"}

# Go 出处（service/user/{user,admin}/api/internal/types/types.go，goctl 生成）
# 这里只标 tag，用于跟实测结果对照
GO_TAGS = {
    "user.register":  {"username": "(无)", "password": "(无)", "email": ",omitempty",
                       "extra_info": ",omitempty"},
    "user.login":     {"username": "(无)", "password": "(无)"},
    "user.update":    {"username": ",optional", "password": ",optional",
                       "email": ",optional", "extra_info": ",optional"},
    "user.avatar":    {"avatar_url": "(无)", "content_type": ",omitempty",
                       "size_bytes": ",omitempty"},
    "admin.login":    {"username": "(无)", "password": "(无)"},
    "admin.create":   {"username": "(无)", "password": "(无)", "email": ",omitempty",
                       "invite_code": "(无)", "extra_info": ",omitempty"},
    "admin.getuser":  {"uid": ",string"},
    "admin.getuserlist": {"page": ",default=1", "page_size": ",default=20",
                          "keyword": ",omitempty"},
    "admin.banuser":  {"uid": ",string"},
    "admin.unbanuser": {"uid": ",string"},
    "admin.resetuserpassword": {"uid": ",string"},
    "admin.update":   {"username": ",omitempty", "password": ",omitempty",
                       "email": ",omitempty", "extra_info": ",omitempty"},
    "admin.updateuser": {"uid": ",string", "username": ",omitempty",
                         "password": ",omitempty", "email": ",omitempty",
                         "extra_info": ",omitempty"},
    "admin.delete":   {"uid": ",string"},
}


def http(method, url, body=None, query=None, headers=None):
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    if query:
        from urllib.parse import urlencode
        url += "?" + urlencode(query)
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, "<<%s>>" % e


TOKEN = {"user": None, "admin": None}


def _login(group, path, body):
    st, txt = http("POST", GO[group] + path, body=body)
    try:
        TOKEN[group] = json.loads(txt)["data"]["token"]
    except Exception:
        TOKEN[group] = None
    return TOKEN[group]


def probe(name, group, method, path, full_body=None, full_query=None, drop=None, empty=()):
    """把 full 里 drop 指定的字段删掉（empty 里的字段改成空串），看 Go 收不收。"""
    body = dict(full_body or {})
    query = dict(full_query or {})
    if drop:
        body.pop(drop, None)
        query.pop(drop, None)
    for k in empty:
        if k in body:
            body[k] = ""
        if k in query:
            query[k] = ""
    hdr = {}
    if TOKEN.get(group):
        hdr["Authorization"] = "Bearer " + TOKEN[group]
    st, txt = http(method, GO[group] + path, body=body if body else None,
                   query=query if query else None, headers=hdr)
    raw = txt.strip().replace("\n", " ")[:70]
    # 400 + text/plain 才算「必填校验拒绝」；其它 4xx 也记录下来但不当作必填结论
    verdict = "**必填**" if st == 400 else ("接受" if st < 400 else "HTTP %d" % st)
    print("  %-34s drop=%-14s → %s  %s" % (name, (drop or ",".join(empty) or "-"),
                                            verdict, raw))
    return st


def main():
    print("=" * 100)
    print("Go 侧必填字段实测（判据待验证：必填 ⟺ tag 不含 ,optional）")
    print("=" * 100)

    uid = 4242424242

    # 先准备身份：探针必须带 token，否则全部 401，测不出必填校验
    _login("user", "/usercenter/v1/user/register",
           {"username": "probe_u1", "password": "pw123456",
            "email": "probe_u1@example.com", "extra_info": {"city": "上海"}})
    _login("user", "/usercenter/v1/user/login",
           {"username": "probe_u1", "password": "pw123456"})
    _login("admin", "/admincenter/v1/admin/login",
           {"username": "admin", "password": "admin"})
    print("token 准备完成：user=%s admin=%s"
          % ("有" if TOKEN["user"] else "**无**", "有" if TOKEN["admin"] else "**无**"))
    print("（admin 用 AdminSeederBootstrap 种的 admin/admin；它同时会建 admin_invites）")

    print("\n---- A. 与 N-3 直接冲突的那一条 ----")
    print("N-3（已签字）称：uid 缺失时 Go 静默取 0 值 ⇒ 返回 1003")
    hdr = {"Authorization": "Bearer " + TOKEN["admin"]} if TOKEN["admin"] else {}
    st, txt = http("GET", GO["admin"] + "/admincenter/v1/admin/getuser", headers=hdr)
    print("  admin.getuser  完全不传 uid → HTTP %s  %s" % (st, txt.strip()[:80]))
    st2, txt2 = http("GET", GO["admin"] + "/admincenter/v1/admin/getuser",
                     query={"uid": "abc"}, headers=hdr)
    print("  admin.getuser  uid=abc     → HTTP %s  %s" % (st2, txt2.strip()[:80]))
    st3, txt3 = http("GET", GO["admin"] + "/admincenter/v1/admin/getuser",
                     query={"uid": "0"}, headers=hdr)
    print("  admin.getuser  uid=0       → HTTP %s  %s" % (st3, txt3.strip()[:80]))
    print("  ⇒ 若这里不是 400，N-3 的前提有误。")

    print("\n---- B. user 域 ----")
    probe("user.register", "user", "POST", "/usercenter/v1/user/register",
          {"username": "probe_u1", "password": "pw123456",
           "email": "probe_u1@example.com", "extra_info": {"city": "上海"}}, drop="email")
    probe("user.register", "user", "POST", "/usercenter/v1/user/register",
          {"username": "probe_u1", "password": "pw123456",
           "email": "probe_u1@example.com", "extra_info": {"city": "上海"}}, drop="extra_info")
    probe("user.login", "user", "POST", "/usercenter/v1/user/login",
          {"username": "probe_u1", "password": "pw123456"}, drop="password")
    probe("user.update", "user", "POST", "/usercenter/v1/user/update",
          {"email": "probe_u1@example.com"}, drop="email")
    probe("user.avatar", "user", "POST", "/usercenter/v1/user/avatar",
          {"avatar_url": "https://cdn/p.png", "content_type": "image/png",
           "size_bytes": 10}, drop="content_type")
    probe("user.avatar", "user", "POST", "/usercenter/v1/user/avatar",
          {"avatar_url": "https://cdn/p.png", "content_type": "image/png",
           "size_bytes": 10}, drop="size_bytes")

    print("\n---- C. admin 域 ----")
    probe("admin.getuser", "admin", "GET", "/admincenter/v1/admin/getuser",
          None, {"uid": str(uid)}, drop="uid")
    probe("admin.getuserlist", "admin", "GET", "/admincenter/v1/admin/getuserlist",
          None, {"page": "1", "page_size": "20", "keyword": "probe"}, drop="keyword")
    print("  （下面这条是空串而非缺字段 —— N/A：观察 Go 是否同样拒绝）")
    probe("admin.getuserlist", "admin", "GET", "/admincenter/v1/admin/getuserlist",
          None, {"page": "1", "page_size": "20"}, empty=("keyword",))
    probe("admin.banuser", "admin", "POST", "/admincenter/v1/admin/banuser",
          {"uid": str(uid)}, drop="uid")
    probe("admin.update", "admin", "POST", "/admincenter/v1/admin/update",
          {"username": "admin", "password": "pw123456",
           "email": "probe_admin@example.com"}, drop="username")
    probe("admin.update", "admin", "POST", "/admincenter/v1/admin/update",
          {"username": "admin", "password": "pw123456",
           "email": "probe_admin@example.com"}, drop="password")
    probe("admin.create", "admin", "POST", "/admincenter/v1/admin/create",
          {"username": "probe_a2", "password": "pw123456",
           "email": "probe_a2@example.com", "invite_code": "ADM-X"}, drop="email")

    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())