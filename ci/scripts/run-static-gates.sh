#!/bin/sh
# =============================================================================
# ci/scripts/run-static-gates.sh —— 通用静态守门阶段（所有域流水线复用）
#
# 【守什么】
#   本仓 tools/difftest/ 下已有一批**纯静态**（不连活体、不连 Go 仓库）的守门脚本。
#   它们是本仓已有的质量资产，CI 的职责是**让它们每次改动都真的跑一遍**，
#   而不是再造一套新检查。
#   ⚠️ 例外：**第 9 道门**（check-pom-parent-relativepath.py）是 CI 自己在实战中
#      抓到缺陷后补的 ⇒ 它守的是**本仓 pom 拓扑**，tools/difftest/ 下没有对应物。
#      它住在 ci/scripts/ 而不是 tools/difftest/ ⇒ 避免动别人 lane 的共享面。
#
# 【为什么不把全部守门都塞进来】
#   实测分类（2026-10-30）：
#     ✅ 纯静态、无外部依赖 → 纳入（tools/difftest/ 7 个 + 产物级 1 个 + ci 自带 1 个 = 9 道）
#     ❌ 需要**活体 PG 容器**   → check-ddl-static.py（--container / --go-db / --rag-db）
#     ❌ 需要 **Go 真源仓库**   → check-proto-static.sh / check-surface-parity.py
#                               / check-phasegoal-consistency.py
#     ❌ 需要**起真实进程**     → check-plaintext400-headers.sh / check-flyway-concurrent.sh
#   把后三类写成"已跑"就是虚构 ⇒ 本阶段只跑真能跑的那批。
#
# 【域级 vs 仓库级】
#   check-jar-executable.sh 支持按模块目录传参 ⇒ 按域收敛（只查本域的 jar）。
#   其余 8 个是全仓扫描（docs/service/tools/仓内 pom 拓扑）⇒ 每个域流水线都跑一遍。
#   单次成本实测在秒级，相对于 Maven + Testcontainers 的分钟级可以忽略；
#   换来的是「任何一次改动都能拿到全仓静态结论」。
#
# 用法：
#   JAR_MODULES="service/article/article-api service/article/article-rpc" \
#     sh ci/scripts/run-static-gates.sh
set -eu

cd repo

JAR_MODULES="${JAR_MODULES:-}"
fail=0
passed=0

run_gate() {
  gate_name="$1"
  shift
  printf '\n===== 守门: %s =====\n' "$gate_name"
  if "$@"; then
    printf '✅ 守门通过: %s\n' "$gate_name"
    passed=$((passed + 1))
  else
    printf '❌ 守门失败: %s\n' "$gate_name"
    fail=$((fail + 1))
  fi
}

echo "[ci] ============================================================"
echo "[ci] 阶段：静态守门"
echo "[ci] 产物级检查模块: ${JAR_MODULES:-（跳过：本流水线无 api/rpc 可执行 jar）}"
echo "[ci] ============================================================"

# --- 依赖准备 ---------------------------------------------------------------
# check-tomcat-limits.py 与 check-flyway-ledger.py 需要 PyYAML
# （前者自己会打印"❌ 需要 PyYAML：pip3 install pyyaml"并以 rc=2 退出）。
# python:3.12-bookworm 官方镜像**不带** PyYAML ⇒ 在这里按需装一次。
# 装不上 ⇒ 明确失败，而不是让两个守门各报一次看不懂的错。
ensure_pyyaml() {
  if python3 -c 'import yaml' >/dev/null 2>&1; then
    return 0
  fi
  echo "[ci] 安装 PyYAML（部分守门脚本依赖它）..."
  if python3 -m pip install --quiet --disable-pip-version-check pyyaml >/dev/null 2>&1; then
    :
  elif python3 -m pip install --quiet --disable-pip-version-check \
        -i https://pypi.tuna.tsinghua.edu.cn/simple pyyaml >/dev/null 2>&1; then
    :
  else
    echo "[ci] ❌ PyYAML 安装失败（pypi.org 与清华源都不可用）" >&2
    return 1
  fi
  python3 -c 'import yaml' >/dev/null 2>&1
}
ensure_pyyaml || exit 1

run_gate check-no-prod-secrets.py \
  python3 tools/difftest/check-no-prod-secrets.py
run_gate check-no-nul-bytes.py \
  python3 tools/difftest/check-no-nul-bytes.py
run_gate check-datasource-password-form.py \
  python3 tools/difftest/check-datasource-password-form.py
run_gate check-tomcat-limits.py \
  python3 tools/difftest/check-tomcat-limits.py
run_gate check-grpc-interceptor-wiring.py \
  python3 tools/difftest/check-grpc-interceptor-wiring.py
run_gate check-grpc-runtime-classpath.py \
  python3 tools/difftest/check-grpc-runtime-classpath.py
run_gate check-flyway-ledger.py \
  python3 tools/difftest/check-flyway-ledger.py
# 仓内自带的第 9 道门（住 ci/scripts/，因为它守的是**本仓 pom 拓扑**）：
# 每个 pom 的 <parent> 能否被本地 relativePath 正确解析。
# ⚠️ 这道门是 **2026-10-31 由本 CI 实测抓出缺陷后**补的：
#   service/users/admin/admin-api 的父 POM 坐标与默认 relativePath 指向的文件对不上
#   ⇒ 干净环境构建必炸 ⇒ 且**连累全域**（根聚合 pom 被完整解析 ⇒ 所有域一起红）。
#   ⇒ 这类缺陷必须离线拦住，不能等 build 才炸。
run_gate check-pom-parent-relativepath.py \
  python3 ci/scripts/check-pom-parent-relativepath.py

if [ -n "$JAR_MODULES" ]; then
  # shellcheck disable=SC2086
  run_gate check-jar-executable.sh \
    bash tools/difftest/check-jar-executable.sh $JAR_MODULES
fi

printf '\n[ci] 静态守门汇总：通过 %s 个，失败 %s 个\n' "$passed" "$fail"
if [ "$fail" -ne 0 ]; then
  echo "[ci] ❌ 静态守门未全部通过。"
  exit 1
fi
echo "[ci] ✅ 静态守门全部通过。"
