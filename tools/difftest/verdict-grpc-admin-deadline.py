#!/usr/bin/env python3
"""🔴 **客户端 deadline 档探针的判定器**（E38 运行期回归网 · `run.sh` 编排用）

═══ 为什么需要这个文件 ═══

`exp-grpc-admin-deadline-probe.sh` 的头部写着判定口径：

```
exit 1 ⟺ 客户端 deadline(短) 档下，两侧 PG 查询存活时长差异 > 1s
exit 0 ⟺ 未观测到该差异
```

⇒ 🔴 **但那段判定从来没被实现** —— 脚本末尾是**无条件 `exit 0`**，
中间的「判定」段只把观测值**打印给人看**，从不比较两侧、也从不红
⇒ ⇒ **这个探针永远不会失败** ⇒ ⇒ **把它接进 `run.sh` 也只是「假闸门」**
（同族 **PIT-GLOBAL-048**：变异判据本身也会假阴性）。

⇒ 本文件 = **把判定从「注释里的承诺」变成「可执行的代码」**：
纯函数、可单测、不依赖任何运行期环境 ⇒ ⇒ **守门可以在 CI 上跑**。

═══ 判定语义 ═══

观测量只有一个：**Δ_end = 取消时刻 − 客户端返回时刻**（epoch ms）
（由探针从 **PG 服务端日志**里取到 ⇒ 取消时刻；减去探针打出的 RPC 返回时刻）

| Δ_end 的含义 | 值 |
|---|---|
| 客户端放弃后，**服务端那条查询还在 PG 上占着多久** | ≈0 ⇒ 查询随客户端一起结束（**继承**了客户端 deadline） |

- **短档**（客户端 deadline < 服务端 2s）：Go 侧（继承）⇒ Δ_end ≈ 0；
  RAG 侧若**不继承** ⇒ 查询要跑到服务端自己的 2s ⇒ Δ_end ≈ 服务端预算 − 客户端预算
  ⇒ ⇒ **两侧差 > 阈值 ⇒ 语义不一致**（E38① 回归 / E38② 仍存在）⇒ **FAIL**
- **长档**（客户端 deadline > 服务端 2s）：两侧都该 ≈ 客户端没提前放弃 ⇒ Δ_end ≈ 0
  ⇒ ⇒ **长档两侧也差很多 ⇒ 环境不可靠**（锁没生效 / 时钟错位 / 查询没到 PG）⇒ **INCONCLUSIVE**

═══ 退出码 ═══

| 码 | 含义 |
|---|---|
| 0 | **PASS** —— 未复现差异（两侧一致 ⇒ RAG 继承了客户端 deadline） |
| 1 | **FAIL** —— 复现了差异（语义不一致）⇒ `run.sh` 必须红 |
| 2 | **INCONCLUSIVE** —— 观测不可靠（缺取消事件 / 对照组也不一致）⇒ 不算通过，也不算失败 |

⚠️ **INCONCLUSIVE 不是 PASS** —— 探针跑挂了却返回 0，那才是真正的假绿。

用法：
    verdict-grpc-admin-deadline.py --go-short-dend 0 --rag-short-dend 1700 \
        --go-long-dend -5 --rag-long-dend 3 [--threshold-ms 1000]
"""
from __future__ import annotations

import argparse
import sys

PASS, FAIL, INCONCLUSIVE = 0, 1, 2


def _f(v: str | None) -> float | None:
    if v is None:
        return None
    v = v.strip()
    if v == "" or v.lower() in ("none", "null", "na", "-"):
        return None
    try:
        return float(v)
    except ValueError:
        return None


def decide(go_short, rag_short, go_long, rag_long, threshold_ms: float):
    """纯判定函数 ⇒ 可直接单测。

    返回 (退出码, 结论标题, 明细行列表)。
    """
    obs = {
        "go_short": go_short,
        "rag_short": rag_short,
        "go_long": go_long,
        "rag_long": rag_long,
    }
    detail: list[str] = []
    for k, v in obs.items():
        detail.append(f"    {k:<10} Δ_end = {('(无观测)' if v is None else f'{v:+.0f} ms')}")

    missing = [k for k, v in obs.items() if v is None]
    if missing:
        return (INCONCLUSIVE, "INCONCLUSIVE —— 观测缺失（该侧没有取消事件 ⇒ 查询压根没到 PG）",
                detail + [f"    缺失：{', '.join(missing)} ⇒ 实验不可判，不算通过"])

    short_diff = abs(rag_short - go_short)
    long_diff = abs(rag_long - go_long)
    detail.append(f"    短档两侧差 = |{rag_short:+.0f} − ({go_short:+.0f})| = {short_diff:.0f} ms")
    detail.append(f"    长档两侧差 = |{rag_long:+.0f} − ({go_long:+.0f})| = {long_diff:.0f} ms")
    detail.append(f"    阈值 = {threshold_ms:.0f} ms")

    if short_diff > threshold_ms:
        return (FAIL,
                "FAIL —— 客户端 deadline 档复现了语义差异"
                "（客户端已放弃，服务端的查询还在 PG 上占着 ⇒ 可能写已提交、客户端收不到）",
                detail + ["    ⇒ E38①（超时继承客户端 deadline）已回归，或 E38②（架构级）仍在 ⇒ 必须修"])
    if long_diff > threshold_ms:
        return (INCONCLUSIVE,
                "INCONCLUSIVE —— 对照档（客户端 deadline > 服务端预算）两侧也不一致"
                "⇒ 环境不可靠（锁没生效 / 时钟错位），本次实验不可判",
                detail + ["    ⇒ 不算通过：先修环境再复跑"])
    return (PASS,
            "PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline）",
            detail + ["    ⇒ E38① 的运行期结论成立"])


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="客户端 deadline 档探针的判定器")
    ap.add_argument("--go-short-dend", default=None, help="Go 侧短档的 Δ_end（ms）")
    ap.add_argument("--rag-short-dend", default=None, help="RAG 侧短档的 Δ_end（ms）")
    ap.add_argument("--go-long-dend", default=None, help="Go 侧长档的 Δ_end（ms）")
    ap.add_argument("--rag-long-dend", default=None, help="RAG 侧长档的 Δ_end（ms）")
    ap.add_argument("--threshold-ms", type=float, default=1000.0, help="判定阈值（默认 1000）")
    args = ap.parse_args(argv)

    code, title, detail = decide(
        _f(args.go_short_dend), _f(args.rag_short_dend),
        _f(args.go_long_dend), _f(args.rag_long_dend),
        args.threshold_ms,
    )
    print("── 判定 ──")
    print(f"  {title}")
    for line in detail:
        print(line)
    print(f"  ⇒ 退出码 {code}（0=PASS / 1=FAIL / 2=INCONCLUSIVE）")
    return code


if __name__ == "__main__":
    sys.exit(main())
