#!/bin/sh
# =============================================================================
# ci/scripts/collect-surefire.sh —— 通用结果汇总阶段（所有域流水线复用）
#
# 【为什么统计要读 TEST-*.xml 而不是 *.txt】
#   surefire 同时产出两种：TEST-<类>.xml（权威，机读）与 <类>.txt（人读摘要）。
#   实测过二者会**不一致**：某个类的 .txt 写 "Tests run: 0" 而 XML 里是真跑的
#   N 条用例 ⇒ 按 .txt 求和会得出"少了 N 条"的假回归（进而去修没坏的地方）。
#   ⇒ 本仓一律以 XML 的 tests= 属性求和为准。
#
# 【产出什么】
#   ① 每个模块的用例数 / 失败数 / 错误数 / 跳过数
#   ② 失败用例清单（类名 + 用例名 + 消息首行）
#   ③ 存在失败/错误 ⇒ 退出码 1 ⇒ Concourse 上这一步红 ⇒ 构建失败
#
# 【注意】
#   输入的 repo 目录里带着上一步 test 产生的 target/surefire-reports/
#   （靠 task 的 outputs 传递）⇒ 本步骤读的是**本次构建**的报告，
#   不是宿主上残留的旧报告。
set -eu

cd repo

if ! command -v python3 >/dev/null 2>&1; then
  echo "[ci] 错误：镜像里没有 python3，无法汇总 surefire 报告。" >&2
  exit 2
fi

python3 - <<'PYEOF'
import glob
import os
import sys
import xml.etree.ElementTree as ET

modules = {}
failed_cases = []

for path in sorted(glob.glob("service/**/target/surefire-reports/TEST-*.xml", recursive=True)):
    try:
        root = ET.parse(path).getroot()
    except Exception as exc:  # noqa: BLE001
        print(f"[ci] ⚠️  报告解析失败 {path}: {exc}")
        continue
    rel = path.split("/target/")[0]
    agg = modules.setdefault(rel, {"tests": 0, "failures": 0, "errors": 0, "skipped": 0})
    for key in ("tests", "failures", "errors", "skipped"):
        try:
            agg[key] += int(root.get(key, 0) or 0)
        except ValueError:
            pass
    for case in root.findall("testcase"):
        for tag in ("failure", "error"):
            node = case.find(tag)
            if node is not None:
                msg = (node.get("message") or node.get("type") or "").strip().splitlines()
                failed_cases.append(
                    (root.get("name", "?"), case.get("name", "?"), msg[0] if msg else "")
                )
                break

total = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
print("[ci] ============================================================")
print("[ci] 阶段：结果汇总（数据源 = surefire TEST-*.xml）")
print("[ci] ============================================================")
print(f"[ci] {'模块':<52}{'用例':>7}{'失败':>7}{'错误':>7}{'跳过':>7}")
for mod in sorted(modules):
    a = modules[mod]
    for key in total:
        total[key] += a[key]
    print(
        f"[ci] {mod:<52}{a['tests']:>7}{a['failures']:>7}{a['errors']:>7}{a['skipped']:>7}"
    )
print(f"[ci] {'合计':<52}{total['tests']:>7}{total['failures']:>7}{total['errors']:>7}{total['skipped']:>7}")

if not modules:
    print("[ci] ❌ 没有任何 surefire 报告 ⇒ 测试阶段没有真正执行过。")
    sys.exit(1)

if failed_cases:
    print(f"[ci] ❌ 失败用例 {len(failed_cases)} 条：")
    for cls, name, msg in failed_cases[:60]:
        print(f"[ci]   - {cls} :: {name}")
        if msg:
            print(f"[ci]       {msg[:300]}")
    if len(failed_cases) > 60:
        print(f"[ci]   ...（另有 {len(failed_cases) - 60} 条未列出）")
    sys.exit(1)

print(f"[ci] ✅ 全部 {total['tests']} 条用例通过。")
PYEOF
