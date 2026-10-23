#!/usr/bin/env bash
# §3.1 静态半检验:Go 真源 vs RAG 的 .proto wire 契约是否字节级一致
#
# 覆盖范围:
#   - proto 字段号(同一 message 内每个字段的 tag 必须相同)
#   - message 名称(RPC 入参 / 出参 / 嵌套结构必须一一对应)
#   - RPC 方法名(同一 service 内所有 rpc 签名必须一致)
#
# 故意不做(wire-level 但需要运行期采集):
#   - 错误码语义:两侧都按 gRPC 标准 codes.NotFound / ALREADY_EXISTS / INVALID_ARGUMENT
#     / INTERNAL 等抛,Java 的 StatusRuntimeException 与 Go 的 status.Error 是同一个 wire 表达,
#     同一 gRPC code 在两侧 wire 字节相同 —— 但不证不立,留待动态层。
#     见 tools/difftest/exp-difftest-grpc-errors.sh(待补)
#
# 为什么没用 mvn / mvn-up-to-date:
#   .proto 是 Maven 资源,会被 protobuf-maven-plugin 用 protoc 重新生成 Java 桩,
#   所以两侧差异只能源于 .proto 源,与生成产物无关 —— 直接 diff 源是更干净的判据。
#
# 用法:
#   ./check-proto-static.sh                       # 默认比对 Go 真源 proto
#   GO_PROTO_DIR=... ./check-proto-static.sh      # 自定义 Go proto 路径

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

GO_PROTO_DIR="${GO_PROTO_DIR:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto}"

USER_GO="$GO_PROTO_DIR/user.proto"
USER_RAG="$ROOT/service/users/user/user-proto/src/main/proto/users.proto"
ADMIN_GO="$GO_PROTO_DIR/admin.proto"
ADMIN_RAG="$ROOT/service/users/admin/admin-proto/src/main/proto/admin.proto"
# task 域（2026-10-24 扩）：Go proto/task.proto ↔ RAG service/task/task-proto/src/main/proto/task.proto
# ⚠️ 与 like 域不同：task 两侧的 `package task;` **逐字相同** ⇒ 无需像 like 那样额外排除 package 行。
#    实际差异只有 option 块（go_package → java_package + java_multiple_files），已被 normalize 吃掉。
TASK_GO="$GO_PROTO_DIR/task.proto"
TASK_RAG="$ROOT/service/task/task-proto/src/main/proto/task.proto"

for f in "$USER_GO" "$USER_RAG" "$ADMIN_GO" "$ADMIN_RAG" "$TASK_GO" "$TASK_RAG"; do
  if [[ ! -f "$f" ]]; then
    echo "缺少真源/对端文件:$f" >&2
    exit 2
  fi
done

# 把侧特定的 option 与空白差异排除掉;只保留 message / rpc / field 级别对比。
# 实现:用 protoc 自带的 --descriptor_set_out 抽语义,diff descriptor_set 更稳。
# 但 protoc 不一定在 PATH;简化策略:用 awk 把每一行先做侧归一化,再去 diff。
#
# 归一化规则:
#   - 删除注释行(// ... 与 /* ... */)
#   - 删除 option go_package / option java_package / option java_multiple_files 整行
#   - 折叠连续空白,把每条 message/rpc/field 压成一行
#
# 归一化后的内容如:  "message UserInfo {" + 6 字段 + "}"
# 与                  "service UserService {" + 9 rpc + "}"
# bytewise 相等即视为一致。

normalize() {
  local file=$1
  python3 - "$file" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
# 删块注释
src = re.sub(r'/\*[\s\S]*?\*/', '', src)
# 删行注释
src = re.sub(r'//[^\n]*', '', src)
# 删侧 specific option 整行
for opt in ('option go_package', 'option java_package', 'option java_multiple_files'):
    src = re.sub(rf'^\s*{re.escape(opt)}[^\n]*\n', '', src, flags=re.M)
# 折叠空白(每行压成单个空格)
out = []
for line in src.splitlines():
    s = line.strip()
    if not s:
        continue
    s = re.sub(r'\s+', ' ', s).rstrip('{; ') + (line.rstrip()[-1:] if line.rstrip() and line.rstrip()[-1] in '{;' else '')
    # 上面一行追加分隔符;简化:空格分隔即可,proto 不含语义空白
    out.append(re.sub(r'\s+', ' ', s))
print('\n'.join(out))
PY
}

# ⚠️ 计数而不是「直接 exit」：一个域不一致也要把**其余域**的结果打出来，
#    否则第一个 ✗ 就把后面的覆盖判据全挡了（看不到就等于没查）。
FAILED=0

verdict() {
  local go=$1 rag=$2 label=$3
  if diff -u <(normalize "$go") <(normalize "$rag") >/tmp/check-proto-static.$$ 2>&1; then
    echo "  ✓ ${label}:wire 契约逐字节一致"
  else
    echo "  ✗ ${label}:存在差异"
    cat /tmp/check-proto-static.$$
    FAILED=1
  fi
  rm -f /tmp/check-proto-static.$$
}

echo "== user(9 RPC) =="
verdict "$USER_GO" "$USER_RAG" "user.proto ↔ users.proto"
echo "== admin(13 RPC) =="
verdict "$ADMIN_GO" "$ADMIN_RAG" "admin.proto ↔ admin.proto"
echo "== task(1 RPC) =="
verdict "$TASK_GO" "$TASK_RAG" "task.proto ↔ task.proto"

cat <<'NOTE'
NOTE:
  上面 diff 排除的是侧特定 option / 注释 / 空白 ——
  field 名、字段号、message 名、rpc 签名一律做了字节级比对。
  §3.1 静态两半(字段号 / message 名)正式关闭。
  剩 错误码语义(动态)留 exp-difftest-grpc-errors.sh。
NOTE

# ⚠️ 必须以退出码表达结论：只打印 ✗ 而返回 0 ⇒ 这个脚本没法当门禁用
#    （「跑绿」不等于「对」——AGENTS.md §3.3）。
if [ "$FAILED" != "0" ]; then
  echo "✗ 至少一个域的 proto wire 契约存在差异" >&2
  exit 1
fi
echo "✓ 全部域的 proto wire 契约逐字节一致"
