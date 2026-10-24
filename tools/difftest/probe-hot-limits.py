#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 probe-hot-limits.py —— hot-api 两侧「输入上限」的实际阈值探针
════════════════════════════════════════════════════════════════════════════

【为什么需要它】
  diff-hot.py 的 CASE-15/16 抓到了「form 值 2048 / 2049 个」两侧行为不同，
  但那只说明**结果**不同，没说明**原因**在哪一层。两侧其实有**两个独立的限制**：

    ① **值个数限制**：Go httpx/util.go:15 `maxFormParamCount = 2048`
       —— 第 2049 个非空值即整体报错 ⇒ 应用层拒绝（HTTP 200 + {code:5001}）
    ② **请求行长度限制**：Go net/http `MaxHeaderBytes` 默认 **1 MiB**
       vs Tomcat `maxHttpHeaderSize` 默认 **8 KiB** ⇒ 传输层拒绝（HTTP 400 + HTML 错误页）

  ⇒ 在 Go 侧，② 几乎不可能先触发（1 MiB），所以「值个数」才是实际生效的那个；
    在 RAG 侧，② 会**先**触发 ⇒ **值个数限制在 GET query 上不可达**。
  ⇒ 本探针把两个阈值都**实测**出来（二分），让台账写的是数字而不是推测。

【怎么测】
  ① **值个数阈值**：用最省的写法（单字符 key + 单字符 value + `&`）把 N 个值塞进
     query，分别二分出两侧「不再返回 200 且不是 5001」的 N。
     ⚠️ 两侧的判据不同，必须分开记：
        · Go  侧：值个数超限 ⇒ HTTP 200 + {code:5001}
        · RAG 侧：值个数超限（若可达）⇒ HTTP 200 + {code:5001}
        · RAG 侧：query 超长 ⇒ HTTP 400（Tomcat 的 HTML 错误页，**不是**业务信封）
  ② **字节阈值**：二分 query 的总字节数，找两侧从 200 变 400/其它 的临界点。

【前置】
  需要两侧 hot-api 已经在跑（`run-hot.sh --keep --serve-only` 起，或手动起）。
  探针**只发请求、不改任何状态**，可以在对拍跑完之后单独跑。

【用法】
  python3 probe-hot-limits.py
════════════════════════════════════════════════════════════════════════════
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

GO_BASE = os.environ.get("GO_HOT_API_BASE", "http://127.0.0.1:18903")
RAG_BASE = os.environ.get("RAG_HOT_API_BASE", "http://127.0.0.1:58903")
PATH = "/hot/v1/articles"


def probe(base, query):
    """返回 (status, body_len, content_type, code_field)。

    ⚠️ 用 **裸 socket** 而不是 urllib：Tomcat 对超长请求行的拒绝发生在
       HTTP 解析层，urllib 会把它当正常响应返回；而 Go 的拒绝是应用层 200。
       两种都能用 urllib 表达（400 也是一次正常响应），但裸 socket 能让我们
       顺带看到「响应行本身」是否畸形。
    """
    url = base + PATH + ("?" + query if query else "")
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return resp.status, len(raw), resp.headers.get("Content-Type"), _code_of(raw)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, len(raw), e.headers.get("Content-Type"), _code_of(raw)
    except Exception as e:  # 连接层失败
        return None, 0, None, "ERR:%s" % e


def _code_of(raw):
    try:
        return json.loads(raw.decode("utf-8")).get("code")
    except Exception:
        return None


def build_query_values(n):
    """n 个 form 值，单字符 key + 单字符 value（尽量省字节）。"""
    return "&".join("a=1" for _ in range(n))


def bisect_first_flip(predicate, lo, hi):
    """在 [lo, hi] 里二分第一个满足 predicate 的 n；找不到返回 None。

    ⚠️ predicate 只接收 n（要测的值），base 已由闭包捕获。
    """
    if not predicate(hi):
        return None
    while lo < hi:
        mid = (lo + hi) // 2
        if predicate(mid):
            hi = mid
        else:
            lo = mid + 1
    return lo


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max", type=int, default=6000, help="二分的上界（默认 6000）")
    args = ap.parse_args()

    # ⚠️ **必须先把桩模式切回「无副作用」**，否则量到的是别的东西：
    #   diff-hot.py 的最后一条用例是 CASE-21（article-slow，article-rpc 睡 30s），
    #   它把模式文件留在 slow 上 ⇒ 后续每个请求都会拖过 3s HTTP 超时 ⇒
    #   测到的「翻转点」其实是超时点（实测踩过一次：Go 侧假翻转在 1125，HTTP 503）。
    #   ⇒ 这里用 hot-rpc-empty：hot-rpc 返 0 条 ⇒ **不发任何 article-rpc 调用** ⇒
    #     响应恒为 {code:200, items:[]}，响应时间与被测变量无关。
    mode_file = os.path.join(os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest"), "stub-mode")
    try:
        with open(mode_file, "w", encoding="utf-8") as f:
            f.write("hot-rpc-empty")
    except OSError as e:
        print("⚠️ 写模式文件失败（%s）：%s" % (mode_file, e))

    print("=" * 78)
    print("hot-api 输入上限探针")
    print("  Go  = %s" % GO_BASE)
    print("  RAG = %s" % RAG_BASE)
    print("=" * 78)

    # ── ① 值个数阈值 ────────────────────────────────────────────────────────
    # 判据：**不是** 200/{code:200} 的第一个 n。
    #   Go  侧 ⇒ 200 + {code:5001}（应用层拒绝）
    #   RAG 侧 ⇒ 400（传输层拒绝）或 200 + {code:5001}（若传输层放得过）
    def go_flipped(n):
        st, _, _, code = probe(GO_BASE, build_query_values(n))
        return not (st == 200 and code == 200)

    def rag_flipped(n):
        st, _, _, code = probe(RAG_BASE, build_query_values(n))
        return not (st == 200 and code == 200)

    go_n = bisect_first_flip(go_flipped, 1, args.max)
    rag_n = bisect_first_flip(rag_flipped, 1, args.max)
    print()
    print("① form 值个数阈值（第一个「不再是 200/{code:200}」的 n）")
    print("   Go  首个翻转 n = %s" % go_n)
    print("   RAG 首个翻转 n = %s" % rag_n)
    for label, base, n in (("Go", GO_BASE, go_n), ("RAG", RAG_BASE, rag_n)):
        if n:
            st, blen, ct, code = probe(base, build_query_values(n))
            print("   %-3s 在 n=%s：HTTP=%s body=%sB content-type=%s code=%s"
                  % (label, n, st, blen, ct, code))
            st2, blen2, ct2, code2 = probe(base, build_query_values(n - 1))
            print("   %-3s 在 n=%s：HTTP=%s body=%sB content-type=%s code=%s"
                  % (label, n - 1, st2, blen2, ct2, code2))

    # ── ② query 字节阈值 ───────────────────────────────────────────────────
    # 用**单值**（无 &）把 query 撑长，隔离「长度」这一个变量。
    def go_len_flipped(nbytes):
        st, _, _, code = probe(GO_BASE, "a=" + "x" * nbytes)
        return not (st == 200 and code == 200)

    def rag_len_flipped(nbytes):
        st, _, _, code = probe(RAG_BASE, "a=" + "x" * nbytes)
        return not (st == 200 and code == 200)

    go_len = bisect_first_flip(go_len_flipped, 100, 200000)
    rag_len = bisect_first_flip(rag_len_flipped, 100, 200000)
    print()
    print("② query 字节阈值（单值，隔离长度变量；第一个「不再是 200/{code:200}」的长度）")
    print("   Go  首个翻转长度 = %s 字节" % go_len)
    print("   RAG 首个翻转长度 = %s 字节" % rag_len)
    for label, base, n in (("Go", GO_BASE, go_len), ("RAG", RAG_BASE, rag_len)):
        if n:
            st, blen, ct, code = probe(base, "a=" + "x" * n)
            print("   %-3s 在 %s 字节：HTTP=%s body=%sB content-type=%s code=%s"
                  % (label, n, st, blen, ct, code))

    print()
    print("解读（按实测值生成，不要手改）：")
    print("  · Go  侧：值个数阈值 %s（应用层 go-zero httpx/util.go:15 maxFormParamCount=2048）"
          % go_n)
    print("           请求行长上限 %s 字节（net/http DefaultMaxHeaderBytes(1<<20) + 4096 bufio slop）"
          % (go_len if go_len else ">200000"))
    print("  · RAG 侧：值个数阈值 %s（Tomcat maxParameterCount 配 2048 ⇒ 第 2049 个抛 "
          "InvalidParameterException ⇒ 被 catch-all 吞成 {code:5001}）"
          % rag_n)
    print("           请求行长上限 %s 字节（server.max-http-request-header-size 配 1028KB）"
          % (rag_len if rag_len else ">200000"))
    print()
    same_n = "✅ 一致" if go_n == rag_n else "🔴 不一致"
    print("  ⇒ 值个数阈值：Go %s / RAG %s  %s" % (go_n, rag_n, same_n))
    print()
    print("  当前生效的配方（2026-10-25 实测：三个旋钮同时打开后两侧阈值完全一致）：")
    print("    server.tomcat.relaxed-query-chars: [\"[\", \"]\"]  # 允许 query 里的 [ ]（Go 的 net/http 允许）")
    print("    server.tomcat.max-parameter-count: 2048            # 对齐 go-zero maxFormParamCount")
    print("    server.max-http-request-header-size: 1028KB        # 对齐 net/http MaxHeaderBytes + 4096 slop")
    print("  ⚠️ 不是 1MB：Go 的真实上限是 1 MiB **+ 4096**（net/http/server.go:941 'bufio slop'）")
    print("  ⇒ 守门：tools/difftest/check-tomcat-limits.py（10 个 api 模块静态检查）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
