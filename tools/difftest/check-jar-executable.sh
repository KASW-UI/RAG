#!/usr/bin/env bash
# =============================================================================
# 守门：**每个 api 模块的 jar 必须是可执行的 Spring Boot fat jar**
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
# 用法：
#   bash tools/difftest/check-jar-executable.sh            # 检查全部 api 模块
#   bash tools/difftest/check-jar-executable.sh service/like/like-api   # 只查指定模块
#
# 退出码：0 = 全部可执行；1 = 至少一个模块的 jar 不可执行（不可部署）
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# 全部 api 模块（含 users 域的二级路径）
if [[ $# -gt 0 ]]; then
  MODULES=("$@")
else
  MODULES=()
  while IFS= read -r d; do MODULES+=("$d"); done < <(
    ls -d service/*/*-api service/users/*/*-api 2>/dev/null | sort
  )
fi

fail=0
printf '检查 %d 个 api 模块的产物可执行性\n' "${#MODULES[@]}"
for d in "${MODULES[@]}"; do
  jar="$(ls "$d"/target/*.jar 2>/dev/null | grep -v -- '-original' | head -1)"
  if [[ -z "$jar" ]]; then
    printf '  ⚠️  %-42s 没有产物（未构建？）\n' "$d"
    continue
  fi
  main="$(unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r' | grep -E '^Main-Class:' | head -1)"
  size="$(du -h "$jar" | cut -f1)"
  if [[ "$main" == *"JarLauncher"* || "$main" == *"Start-Class"* ]]; then
    printf '  ✅  %-42s %-6s %s\n' "$d" "$size" "$main"
  else
    printf '  ❌  %-42s %-6s **没有 Main-Class ⇒ java -jar 起不来**\n' "$d" "$size"
    fail=1
  fi
done

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

echo "全部 api 模块的 jar 均可执行 ✓"
exit 0
