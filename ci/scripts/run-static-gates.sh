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
#     ✅ 纯静态、无外部依赖 → 纳入（tools/difftest/ 7 个 + 产物级 1 个
#                             + ci 自带 1 个 + R4 契约门 1 个 + R10 清单门 1 个
#                             + 触发面一致性门 1 个 + docker_api 自测门 1 个 = **13 道**）
#     ❌ 需要**活体 PG 容器**   → check-ddl-static.py（--container / --go-db / --rag-db）
#                               （⚠️ 2026-10-31 已由 R6 的 `--rag-only` 模式补上
#                                 Java 侧路径 ⇒ 见 ci/contracts/schema/）
#     ❌ 需要 **Go 真源仓库**   → check-surface-parity.py
#                               / check-phasegoal-consistency.py
#                               （⚠️ check-proto-static.sh 已**移出**本组 ——
#                                 它现有 Go-free 的 `--baseline` 模式 ⇒ 已纳入）
#     ❌ 需要**起真实进程**     → check-plaintext400-headers.sh / check-flyway-concurrent.sh
#   把后三类写成"已跑"就是虚构 ⇒ 本阶段只跑真能跑的那批。
#
# 【域级 vs 仓库级】
#   check-jar-executable.sh 支持按模块目录传参 ⇒ 按域收敛（只查本域的 jar）。
#   其余 8 个是全仓扫描（docs/service/tools/仓内 pom 拓扑）⇒ 每个域流水线都跑一遍。
#   单次成本实测在秒级，相对于 Maven + Testcontainers 的分钟级可以忽略；
#   换来的是「任何一次改动都能拿到全仓静态结论」。
#
#   🆕 第 10 道门：check-proto-contract.py（R4 接口契约一致性）
#   🆕 第 11 道门：check-requirements.py（目标要求清单六态）
#   🆕 第 12 道门：verify-triggers.sh --static（触发面：实现 vs 意图）
#   🆕 第 13 道门：check-docker-api-selftest.py（docker_api 镜像判定回归）
#   ⚠️ **它为什么在这里、而不是独立的契约流水线**：
#     R4 是**纯文本逐字节比对**（秒级 · 零额外依赖 · 不需要 Maven/PG/Go）
#     ⇒ 按门禁分类它属于「每次代码变更都应该执行的快速门禁」
#     ⇒ 放进项目级静态守门 ⇒ **每条域流水线都跑一遍**
#       ⇒ 「改任一 proto ⇒ 至少一条相关流水线被触发 ⇒ 该流水线必跑此门」
#       ⇒ 契约漂移是**全局风险**，不该只在某一条流水线里被看见。
#     （对照：R5/R6 是分钟级 × 多域 ⇒ 属于「成本较高、适合独立触发的契约门禁」
#       ⇒ 各自独立成一条流水线。）
#   ⚠️ 它与 R5/R6 的共同点：**不依赖 Go** —— 契约基准已离线提取并固化在
#     ci/contracts/ 下 ⇒ 不拉 Go 仓、不装 Go 工具链、不跑任何 go 命令。
#
# 用法：
#   JAR_MODULES="service/article/article-api service/article/article-rpc" \
#     sh ci/scripts/run-static-gates.sh
set -eu

cd repo

JAR_MODULES="${JAR_MODULES:-}"
fail=0
passed=0
# ⚠️ 豁免统计（2026-10-31 新增）——
#   「白名单/豁免」是**已知未修**的登记项，不是「查过且合规」。
#   ⇒ 汇总必须把它们**单列**，否则 CI 绿会掩盖「其实还有 N 处没修」。
gate_exempt=0
gates_with_exempt=0
GATE_OUT_DIR="$(mktemp -d)"

run_gate() {
  gate_name="$1"
  shift
  printf '\n===== 守门: %s =====\n' "$gate_name"
  # ⚠️ 门输出先落文件再 cat：为了能解析门**自报**的豁免标记行
  #   （行首 `[ci-exempt]` ⇒ 字段 exempted=N / unexempted=M）。
  #   ⇒ 没有这行 ⇒ 该门本次**没有豁免命中**（或该门不报豁免）⇒ 按 0 处理。
  out_file="$GATE_OUT_DIR/${gate_name}.out"
  if "$@" > "$out_file" 2>&1; then
    cat "$out_file"
    printf '✅ 守门通过: %s\n' "$gate_name"
    passed=$((passed + 1))
  else
    cat "$out_file"
    printf '❌ 守门失败: %s\n' "$gate_name"
    fail=$((fail + 1))
  fi
  # 解析豁免标记（⚠️ 按**空格**切 token ⇒ `exempted=` 与 `unexempted=` 不会互相误取）
  ex=$(grep -m1 '^\[ci-exempt\]' "$out_file" 2>/dev/null \
        | tr ' ' '\n' | grep '^exempted=' | cut -d= -f2)
  un=$(grep -m1 '^\[ci-exempt\]' "$out_file" 2>/dev/null \
        | tr ' ' '\n' | grep '^unexempted=' | cut -d= -f2)
  case "$ex" in ''|*[!0-9]*) ex=0 ;; esac
  case "$un" in ''|*[!0-9]*) un=0 ;; esac
  if [ "$ex" -gt 0 ]; then
    gate_exempt=$((gate_exempt + ex))
    gates_with_exempt=$((gates_with_exempt + 1))
    printf '   ↳ 豁免命中 %s 处（该门已登记的已知未修项）\n' "$ex"
  fi
  if [ "$un" -gt 0 ]; then
    printf '   ↳ 未豁免命中 %s 处\n' "$un"
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
# 第 10 道门：R4 接口契约一致性（Proto wire 契约 vs 仓内基准）。
# ⚠️ 三态退出码（0=PASS / 1=FAIL / 2=BLOCKED）⇒ 本步骤在 BLOCKED 时也会红
#   ⇒ 这是**安全方向**：未检查 ≠ 已通过（ci/contracts/README.md 第四节）。
run_gate check-proto-contract.py \
  python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto
# 第 11 道门：**目标要求清单**（2026-10-31 方向纠正的产物）。
# ⚠️ 这道门守的不是「代码风格」，而是「**CI 到底在替谁守什么**」：
#   · 每条目标要求必须能回溯到 Go 原始依据（禁止以 Java 现状反向定义目标）；
#   · APPROVED_DIFFERENCE 必须能**机器回指**到 docs/Intentional_Deviations/ 里**已签字**的条目
#     ⇒ 杜绝「自行把差异列进豁免」；
#   · NOT_IMPLEMENTED / NOT_VERIFIED / BLOCKED **一律不计入 PASS**（PASS 只 = VERIFIED）。
#   ⇒ 本门退出 0 的含义是「无回归 + 结构合规」，**不等于「目标全部达成」**
#     ⇒ 六态分列必须与 PASS 数同时打印（守门自己会打印）。
run_gate check-requirements.py \
  python3 ci/scripts/check-requirements.py
# 第 12 道门：**触发面一致性**（verify-triggers.sh --static）
# ⚠️ 它守的是「**生成器算出来的 paths**」与「**手工推导的意图集合**」是否一致：
#   · 场景表（意图）= 依据 Go/Java pom 依赖图与跨域调用边手工推导的「应该触发谁」；
#   · ci/pipelines/*.yml（实现）= ci/generate-pipelines.py 从 pom 反推算出来的。
#   ⇒ 两者独立 ⇒ 这道门能挡住「改了生成器但没改意图」的静默漂移。
#   ⛔ 它**不连 Concourse** ⇒ 只证「流水线定义内部自洽」；
#      真实触发行为由 ci/verify-triggers.sh（服务端模式）单独验。
run_gate verify-triggers-static \
  bash ci/verify-triggers.sh --static
# 第 13 道门：**docker_api 自测**（R6 起 PG 的唯一通道）
# ⚠️ 2026-10-31 实测抓到的真缺陷：`image_exists` 把 tag 丢了
#   （`GET /images/postgres/json` ⇒ daemon 按 `:latest` 解析）
#   ⇒ R6 在 Concourse Task 内首跑时把「镜像没拉过」报成「查镜像失败」⇒ 全域 BLOCKED。
#   ⇒ 这道门**无外部依赖**（用模拟 daemon 解析规则的假 client）⇒ 秒级、可进静态守门。
run_gate check-docker-api-selftest.py \
  python3 ci/scripts/check-docker-api-selftest.py

if [ -n "$JAR_MODULES" ]; then
  # shellcheck disable=SC2086
  run_gate check-jar-executable.sh \
    bash tools/difftest/check-jar-executable.sh $JAR_MODULES
fi

printf '\n[ci] 静态守门汇总：通过 %s 个，失败 %s 个\n' "$passed" "$fail"
if [ "$gates_with_exempt" -gt 0 ]; then
  # ⚠️ 分级显示：**不得**把「有豁免命中」混进「通过」里一句话带过。
  #   「通过（其中 %s 个门有豁免命中，合计 %s 处）」≠「全部合规」。
  printf '[ci] ↳ 其中 %s 个门存在**豁免命中**，合计 %s 处（已知未修项，**不等于已合规**）\n' \
    "$gates_with_exempt" "$gate_exempt"
fi
if [ "$fail" -ne 0 ]; then
  echo "[ci] ❌ 静态守门未全部通过。"
  exit 1
fi
if [ "$gates_with_exempt" -gt 0 ]; then
  echo "[ci] ✅ 静态守门全部通过（**注意：存在豁免命中项，见上方汇总**）。"
else
  echo "[ci] ✅ 静态守门全部通过（无豁免命中）。"
fi
