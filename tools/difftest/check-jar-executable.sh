#!/usr/bin/env bash
# =============================================================================
# 守门：**每个 api + rpc 模块的 jar 必须是可执行的 Spring Boot fat jar**
#
# 缺陷形态（DRIFT-MSG-V-028 的同款，2026-10-24 在 message 域首次实测到）：
#   pom 里**没有 <build> 段** ⇒ 没有 spring-boot-maven-plugin 的 repackage
#   ⇒ 打出来的是**普通 library jar**（实测 24~68 KB）
#   ⇒ `java -jar xxx.jar` 直接报「中没有主清单属性」（no main manifest attribute）
#   ⇒ **该模块从来没有被打成过可部署产物**
#
# ⚠️ 为什么这道门有存在价值（§3.3）：
#   「mvn package 成功」「测试全绿」「上下文能起」**都证明不了产物可运行** ——
#   这三者全部发生在**容器/测试环境**里，从来没碰过 jar 本身。
#   message-api 的这个缺陷潜伏了很久就是这样：每一轮验证都在容器里跑。
#
# ⚠️ **2026-10-26（L15）：rpc 模块纳入枚举 —— 存量缺口**
#   本脚本原先只枚举 `*-api` ⇒ **10 个 rpc 模块的产物可执行性从来没有被任何守门查过**
#   ⇒ 第 27 轮 L11 **手工**查 pom 时**漏报了 hot-rpc**（手工必漏 ⇒ 这正是要机器守门的理由）。
#   实测（改前）：`hot-rpc`（58 KB）与 `task-rpc`（83 KB）**无 Main-Class** ⇒ `java -jar` 起不来。
#   ⚠️ 这 2 个模块的 pom 由 L13 / L14 修 ⇒ **本脚本在它们修好之前对它们报红是正确的**
#      （守门该报的必须报；见 docs/DRIFT/global/lanes/L15-rpcboot-tools.md）。
#
# ⚠️ **`service/common` 刻意不在枚举里**：
#   它既不是 `*-api` 也不是 `*-rpc` ⇒ **是 library 模块**（被别的模块依赖）
#   ⇒ **不得**给它加 `spring-boot-maven-plugin` 的 repackage ⇒ 否则打出的 jar 变成 fat jar
#   ⇒ 会**污染下游依赖**（依赖方拿到一个内嵌了全部依赖的胖 jar）。
#   ⇒ 下一个人若要扩大枚举范围，**请保持 `service/common` 在外**。
#
# 用法：
#   bash tools/difftest/check-jar-executable.sh                  # 检查全部 api + rpc 模块
#   bash tools/difftest/check-jar-executable.sh service/like/like-api    # 只查指定模块
#   bash tools/difftest/check-jar-executable.sh service/task/task-rpc    # 只查指定 rpc 模块
#   bash tools/difftest/check-jar-executable.sh --allow-unbuilt  # 允许「未构建」跳过（**不计入判绿**）
#
# 退出码：0 = 全部已检查且可执行；1 = 至少一个模块的 jar 不可执行（不可部署）**或** 至少一个模块未构建
# =============================================================================
set -uo pipefail
# 🔴 RAG 侧 **fail-closed 密钥**（P0 #8）：占位符解析不出来 ⇒ 进程起不来
#    ⇒ 探针「起 RAG 侧」这一步必挂 ⇒ 看起来像产品缺陷，其实是探针自己没给密钥。
#    （2026-10-30 活体对拍轮实测 · 12 个探针全中招 ⇒ 收敛到这一个共享件）
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-secrets.sh"


ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# 🔴 **未构建 ⇒ 跳过且不计失败** 是本脚本原先的假绿口子（G-10 · 2026-10-29 实测）：
#   实跑**只检查了 5/20 个模块**却返回「全部 api + rpc 模块的 jar 均可执行 ✓」＋ EXIT=0
#   ⇒ ⇒ 「没查」被读成「查过且没问题」⇒ ⇒ **正是「零命中是假信号 / 跑绿 ≠ 判据活着」同族**。
#   ⇒ 修法（两条一起做）：① **永远打印「已检查 N/M」**；② **未构建 ⇒ 判红**（除非显式 `--allow-unbuilt`）。
ALLOW_UNBUILT=0
if [[ "${1:-}" == "--allow-unbuilt" ]]; then
  ALLOW_UNBUILT=1
  shift
fi

# 全部 api + rpc 模块（含 users 域的二级路径）
# ⚠️ **不要**把 `service/common` 加进来 —— 它是 library 模块，repackage 会污染下游依赖（见上）。
if [[ $# -gt 0 ]]; then
  MODULES=("$@")
else
  MODULES=()
  while IFS= read -r d; do MODULES+=("$d"); done < <(
    ls -d service/*/*-api service/*/*-rpc service/users/*/*-api service/users/*/*-rpc 2>/dev/null | sort
  )
fi

fail=0
checked=0
unbuilt=0
total="${#MODULES[@]}"
printf '检查 %d 个 api + rpc 模块的产物可执行性\n' "$total"
for d in "${MODULES[@]}"; do
  jar="$(ls "$d"/target/*.jar 2>/dev/null | grep -v -- '-original' | head -1)"
  if [[ -z "$jar" ]]; then
    unbuilt=$((unbuilt + 1))
    if [[ "$ALLOW_UNBUILT" -eq 1 ]]; then
      printf '  ⚠️  %-42s 没有产物（未构建？）—— **未检查**（--allow-unbuilt）\n' "$d"
    else
      printf '  ❌  %-42s 没有产物（未构建？）—— **未检查 ⇒ 不算通过**\n' "$d"
      fail=1
    fi
    continue
  fi
  checked=$((checked + 1))
  main="$(unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | grep -E '^Main-Class:' | head -1)"
  size="$(du -h "$jar" | cut -f1)"
  if [[ "$main" == *"JarLauncher"* || "$main" == *"Start-Class"* ]]; then
    printf '  ✅  %-42s %-6s %s\n' "$d" "$size" "$main"
  else
    printf '  ❌  %-42s %-6s **没有 Main-Class ⇒ java -jar 起不来**\n' "$d" "$size"
    fail=1
  fi
done

# 🔴 **「已检查 N/M」必须打印出来** ⇒ ⇒ 读者不会把「没查」读成「查过且没问题」。
printf '已检查 %d/%d 个模块（未构建 %d 个）\n' "$checked" "$total" "$unbuilt"

if [[ $fail -ne 0 ]]; then
  cat <<'EOF'

⇒ 修法（照 message-api 的 V-028，DRIFT-MSG-V-028）：
    在该模块 pom.xml 里补 <build> 段：
        <build>
          <plugins>
            <plugin>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
          </plugins>
        </build>
   ⚠️ surefire 的 Ryuk 关闭段按各模块既有情况保留（user-api / admin-api 已有同款）。
   ⚠️ 改完必须重跑本脚本确认 Main-Class 变成 JarLauncher —— 只看「mvn package 成功」不算数。
EOF
  exit 1
fi

if [[ "$unbuilt" -eq 0 ]]; then
  # 🔴 2026-10-29（R4 轮）修：这里原来写的是裸 `$total）` / `$checked/$total）` ——
  #    **macOS 自带 bash 3.2 会把紧跟在 `$VAR` 后的多字节字符（此处是全角右括号 `）`）
  #    算进变量名** ⇒ 变量名变成 `total\xef\xbc\x89` ⇒ `set -u` ⇒
  #    `total?: unbound variable` ⇒ **脚本在最后一行崩掉 ⇒ 明明 20/20 全过却 EXIT=1**
  #    ⇒ **假红**（与 `tools/api-full-parity-probe/users/` README 记的同类坑同源）。
  #    修法 = **一律用 `${VAR}` 显式界定变量名边界**。
  echo "全部 ${total} 个 api + rpc 模块的 jar 均可执行 ✓（已检查 ${checked}/${total}）"
else
  echo "已检查的 ${checked} 个模块 jar 均可执行 ✓；⚠️ **另有 ${unbuilt} 个模块未构建 ⇒ 未检查**（不是「已通过」）"
fi
exit 0
