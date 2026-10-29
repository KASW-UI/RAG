#!/usr/bin/env python3
"""users-common 双侧对拍比较器 —— 判定「Go == RAG == expected.txt」。

用法：
    python3 compare.py <go.out> <rag.out> <expected.txt>

输入格式（两侧探针各产出一份）：
    每行 `case_id<TAB>value`

expected.txt 格式（每行一条，字段用 `|` 分隔；**value 里允许出现 `|`** ⇒ 用 maxsplit 解析）：
    CASE|<case_id>|<side>|<value>      side ∈ BOTH / GO / RAG
    REG|<case_id>|<go_value>|<rag_value>   已登记的**两侧不同**（键序 / 签字偏离 / 潜伏缺陷）

判定规则（**故意很严**，避免「自己跟自己比」）：
  ① CASE+BOTH：两侧的值必须都等于冻结值。
  ② CASE+GO ：只有 Go 侧产出，且等于冻结值；RAG 侧**不得**产出这个 id。
  ③ CASE+RAG：同上（反之）。
  ④ REG     ：两侧的值必须分别等于各自冻结值（**允许两侧不同**，但**两侧都要被钉住**）。
  ⑤ 探针产出但 expected.txt 没登记的 id ⇒ **失败**（防止「新 case 悄悄溜过去」）。
  ⑥ expected.txt 登记了但探针没产出的 id ⇒ **失败**（防止「期望值与实现脱钩」）。

退出码：0 = 全部通过；1 = 有差异；2 = 输入文件有问题。
"""

import sys


def die(msg, code=2):
    print(f"❌ {msg}", file=sys.stderr)
    sys.exit(code)


def load_out(path):
    """读一侧的探针输出 ⇒ {case_id: value}。"""
    out = {}
    try:
        with open(path, encoding="utf-8") as f:
            for lineno, line in enumerate(f, 1):
                line = line.rstrip("\n")
                if not line:
                    continue
                if "\t" not in line:
                    die(f"{path}:{lineno} 不是 `id<TAB>value` 形状：{line[:80]!r}")
                cid, value = line.split("\t", 1)
                if cid in out and out[cid] != value:
                    die(f"{path}:{lineno} case {cid!r} 出现两次且值不同")
                out[cid] = value
    except FileNotFoundError:
        die(f"找不到探针输出：{path}")
    return out


def load_expected(path):
    cases = []
    regs = []
    try:
        with open(path, encoding="utf-8") as f:
            for lineno, line in enumerate(f, 1):
                line = line.rstrip("\n")
                if not line or line.startswith("#"):
                    continue
                parts = line.split("|", 3)
                if parts[0] == "CASE":
                    if len(parts) != 4:
                        die(f"{path}:{lineno} CASE 记录字段数不对：{line!r}")
                    if parts[2] not in ("BOTH", "GO", "RAG"):
                        die(f"{path}:{lineno} side 必须是 BOTH/GO/RAG：{parts[2]!r}")
                    cases.append((parts[1], parts[2], parts[3], lineno))
                elif parts[0] == "REG":
                    if len(parts) != 4:
                        die(f"{path}:{lineno} REG 记录字段数不对：{line!r}")
                    regs.append((parts[1], parts[2], parts[3], lineno))
                else:
                    die(f"{path}:{lineno} 未知记录类型 {parts[0]!r}")
    except FileNotFoundError:
        die(f"找不到冻结的期望值：{path}")
    return cases, regs


def main():
    if len(sys.argv) != 4:
        die("用法：compare.py <go.out> <rag.out> <expected.txt>")
    go_path, rag_path, expected_path = sys.argv[1:4]

    go = load_out(go_path)
    rag = load_out(rag_path)
    cases, regs = load_expected(expected_path)

    covered = set()
    failed = 0
    equal_count = 0
    side_only_count = 0
    reg_count = 0

    print("== 逐 case 判定（Go == RAG == expected.txt） ==")
    for cid, side, value, lineno in cases:
        covered.add(cid)
        if side == "BOTH":
            g, r = go.get(cid), rag.get(cid)
            if g == value and r == value:
                equal_count += 1
            else:
                failed += 1
                print(f"   ❌ {cid}  (expected.txt:{lineno})")
                print(f"      期望 : {value!r}")
                print(f"      Go   : {g!r}")
                print(f"      RAG  : {r!r}")
        elif side == "GO":
            g = go.get(cid)
            if g == value and cid not in rag:
                side_only_count += 1
            else:
                failed += 1
                print(f"   ❌ {cid}  (expected.txt:{lineno}, side=GO)")
                print(f"      期望(Go) : {value!r}")
                print(f"      Go       : {g!r}")
                print(f"      RAG 侧不应产出该 id：{'产出' if cid in rag else '未产出'}")
        else:  # RAG
            r = rag.get(cid)
            if r == value and cid not in go:
                side_only_count += 1
            else:
                failed += 1
                print(f"   ❌ {cid}  (expected.txt:{lineno}, side=RAG)")
                print(f"      期望(RAG): {value!r}")
                print(f"      RAG      : {r!r}")
                print(f"      Go 侧不应产出该 id：{'产出' if cid in go else '未产出'}")

    for cid, gv, rv, lineno in regs:
        covered.add(cid)
        g, r = go.get(cid), rag.get(cid)
        reg_count += 1
        if g == gv and r == rv:
            print(f"   ⚠️  {cid}  **已登记的两侧不同**（两侧值均已钉住）")
            print(f"      Go  : {g!r}")
            print(f"      RAG : {r!r}")
        else:
            failed += 1
            print(f"   ❌ {cid}  (expected.txt:{lineno}, REG)")
            print(f"      期望 Go  : {gv!r}")
            print(f"      实际 Go  : {g!r}")
            print(f"      期望 RAG : {rv!r}")
            print(f"      实际 RAG : {r!r}")

    # ⑤ 未冻结的 case（探针产出但 expected.txt 没登记）
    unfrozen = sorted((set(go) | set(rag)) - covered)
    for cid in unfrozen:
        failed += 1
        print(f"   ❌ {cid}  **未在 expected.txt 冻结**（探针产出了新 case）")
        print(f"      Go  : {go.get(cid)!r}")
        print(f"      RAG : {rag.get(cid)!r}")

    # ⑥ 期望值与实现脱钩（expected.txt 登记了但探针没产出）
    missing = sorted(covered - (set(go) | set(rag)))
    for cid in missing:
        failed += 1
        print(f"   ❌ {cid}  **expected.txt 里有，但探针没产出**")

    print()
    print(f"   两侧逐字节相同的 case : {equal_count}")
    print(f"   单侧专属的 case       : {side_only_count}")
    print(f"   已登记的两侧不同       : {reg_count}")
    print(f"   失败                  : {failed}")
    if failed:
        print("❌ 对拍不通过")
        sys.exit(1)
    print("✅ Go == RAG == expected.txt（逐字节）")


if __name__ == "__main__":
    main()
