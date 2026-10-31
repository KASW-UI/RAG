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
#   ./check-proto-static.sh                       # 默认比对 Go 真源 proto（**本地**用）
#   GO_PROTO_DIR=... ./check-proto-static.sh      # 自定义 Go proto 路径
#   ./check-proto-static.sh --go-proto-dir DIR    # 同上（显式写法）
#   ./check-proto-static.sh --baseline DIR        # 比对**契约基准**（**CI 模式**）
#
# ⚠️ **两条模式的分工**（`ci/contracts/README.md` §一 办法 C）：
#   · `--go-proto-dir`：**本地**跟 Go 真源现场比 ⇒ 需要本机有 Go 仓 ⇒ **CI 不得用**
#     （Go 仓不是 CI 的执行对象；CI 不得依赖它在线可访问）。
#   · `--baseline`：跟 `ci/contracts/proto/` 下**已固化的契约基准**比 ⇒ **CI 用这条**
#     ⇒ CI 镜像**不需要 Go 工具链**、**不需要 Go 仓**、**不跑任何 go 命令**。

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

GO_PROTO_DIR="${GO_PROTO_DIR:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto}"

# ── 参数解析 ──────────────────────────────────────────────────────────────
# `--baseline`  ⇒ 交给 ci/scripts/check-proto-contract.py（契约基准模式，三态退出码）
# `--go-proto-dir` ⇒ 走本脚本下半段的「跟 Go 现场比」路径（行为与改造前完全一致）
BASELINE_DIR=""
WITH_DESCRIPTOR=""
ROOT_OVERRIDE=""
BASELINE_REQUESTED="no"
while [ $# -gt 0 ]; do
  case "$1" in
    --baseline)
      BASELINE_REQUESTED="yes"
      BASELINE_DIR="${2:-}"
      shift 2 || shift
      ;;
    --baseline=*)
      BASELINE_REQUESTED="yes"
      BASELINE_DIR="${1#--baseline=}"
      shift
      ;;
    --with-descriptor)
      WITH_DESCRIPTOR="--with-descriptor"
      shift
      ;;
    --go-proto-dir)
      GO_PROTO_DIR="${2:-}"
      shift 2 || shift
      ;;
    --go-proto-dir=*)
      GO_PROTO_DIR="${1#--go-proto-dir=}"
      shift
      ;;
    --root)
      ROOT_OVERRIDE="${2:-}"
      shift 2 || shift
      ;;
    --root=*)
      ROOT_OVERRIDE="${1#--root=}"
      shift
      ;;
    -h|--help)
      sed -n '1,40p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      echo "用法：$0 [--go-proto-dir DIR | --baseline DIR [--with-descriptor]]" >&2
      exit 2
      ;;
  esac
done

# ── 契约基准模式（CI 用）────────────────────────────────────────────────
# 退出码：0=PASS / 1=FAIL / 2=BLOCKED（见 ci/contracts/README.md §四）
# ⚠️ 显式要求了 `--baseline` 却没给目录 ⇒ **报错退出**，**不得**静默退回「跟 Go 比」模式
#    （静默换判据 = 用错误的姿势「通过」）。
if [ "$BASELINE_REQUESTED" = "yes" ] && [ -z "$BASELINE_DIR" ]; then
  echo "--baseline 需要一个目录参数（契约基准目录）" >&2
  echo "用法：$0 --baseline ci/contracts/proto" >&2
  exit 2
fi
if [ -n "$BASELINE_DIR" ]; then
  ROOT_ARG=""
  if [ -n "$ROOT_OVERRIDE" ]; then
    ROOT_ARG="--root ${ROOT_OVERRIDE}"
  fi
  # shellcheck disable=SC2086
  exec python3 "$ROOT/ci/scripts/check-proto-contract.py" --baseline "$BASELINE_DIR" ${ROOT_ARG} ${WITH_DESCRIPTOR}
fi

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
#
# 🔴 归一化只有**一份实现**（2026-10-31 收口）：
#    实现在 `ci/scripts/check-proto-contract.py` 的 `normalize_proto()`，
#    本函数通过 `--normalize` 子命令**调用**它 ⇒ 本脚本的「跟 Go 现场比」路径
#    与 CI 的「跟契约基准比」路径**用同一份归一化** ⇒ 两处不会漂移。
#    （改造前这里内嵌过一份 python 归一化，与 ci/scripts 侧各写一份 ⇒ 收口掉。）
#    ⚠️ 顺带修掉一个隐患：内嵌实现用 `open(f).read()`（**locale 默认编码**），
#       而 proto 源里含中文注释 ⇒ 在 LANG=C 的镜像上会抛 UnicodeDecodeError；
#       现在统一按 **utf-8** 读。
#
# 🔴 归一化会**删掉 `package` 行** —— **这是已签字偏离的明文要求**，不是「放过漂移」：
#   · `like`   ⇒ SD-2026-10-23-05（`docs/Intentional_Deviations/like/proto.md`）
#   · `comment`⇒ SD-2026-10-23-04（`docs/Intentional_Deviations/comment/proto.md`）
#   两域的 Go 侧是遗留包名 `package pb;`，RAG 侧归一成域名（`like` / `comment`）
#   ⇒ **gRPC 方法路径前缀不同**（`/pb.LikeService/*` vs `/like.LikeService/*`）⇒ wire 层可见。
#   签字文件原文：「`check-proto-static.sh` 若扩到 `like`，其 `verdict` **必须排除 `package` 行**，
#   否则会把本条已签字偏离判成回退」⇒ 本行照此办理。
#   ⚠️ **排除 ≠ 静默忽略** ⇒ 排除的同时**单独钉住** RAG 侧的 package 必须是签字值
#   （见下面的 `pkg_pin` 判定）⇒ 偏离被**钉成「签字偏离」**，改动它仍会转红。
#   （这条规则的实现现在住在 `ci/scripts/check-proto-contract.py` 的 `normalize_proto()`；
#     契约基准模式（`--baseline`）下则**保留** package 行 —— 基准里已钉住裁定值，
#     package 是契约项 ⇒ 逐字节参与比对。）

normalize() {
  local file=$1
  python3 "$ROOT/ci/scripts/check-proto-contract.py" --normalize "$file"
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

# 🔴 G-6（2026-10-29 · R3·T-3.3）：本守门原先只覆盖 **4 个域**（user / admin / task / comment）
#    ⇒ **`follow` / `like` / `hot` / `article` / `message` / `favorite` 连静态守门都没有**
#    ⇒ 「proto 漂了**不会红**」⇒ 现扩到 **10 个域**（= RAG 侧全部 proto 组件）。
#
# ⚠️ **`points` / `security` 不在范围内** —— Go 侧**范围内外**（AGENTS.md §一：Go 侧这三个域范围外
#    ⇒ 不是待办）⇒ 本表**不列**它们 ⇒ **不是「漏了」，是「不在范围内」**。
#
# ⚠️ `user.proto` 在 RAG 侧改名 `users.proto`（AGENTS.md §3.4：**唯一**允许的改名）⇒ 表里显式标注。
DOMAINS=(
  "user|users|service/users/user/user-proto/src/main/proto/users.proto|user.proto ↔ users.proto（RAG 侧改名）"
  "admin|admin|service/users/admin/admin-proto/src/main/proto/admin.proto|admin.proto ↔ admin.proto"
  "task|task|service/task/task-proto/src/main/proto/task.proto|task.proto ↔ task.proto"
  "comment|comment|service/comment/comment-proto/src/main/proto/comment.proto|comment.proto ↔ comment.proto"
  "favorite|favorite|service/favorite/favorite-proto/src/main/proto/favorite.proto|favorite.proto ↔ favorite.proto"
  "follow|follow|service/follow/follow-proto/src/main/proto/follow.proto|follow.proto ↔ follow.proto"
  "hot|hot|service/hot/hot-proto/src/main/proto/hot.proto|hot.proto ↔ hot.proto"
  "like|like|service/like/like-proto/src/main/proto/like.proto|like.proto ↔ like.proto"
  "message|message|service/message/message-proto/src/main/proto/message.proto|message.proto ↔ message.proto"
  "article|article|service/article/article-proto/src/main/proto/article.proto|article.proto ↔ article.proto"
)

# 🔴 **钉住 RAG 侧的 proto `package`** —— 「排除 package 行」≠「放过 package 漂移」。
#    每条都带**签字出处**（无出处 ⇒ 不许豁免）。
#    · `like` / `comment` ⇒ **已签字偏离**（Go 侧 `pb` 是遗留包名 ⇒ 归一为域名）⇒ 钉住**签字值**
#    · 其余 8 域 ⇒ 两侧本就逐字相同 ⇒ 钉住**与 Go 一致**这个事实（防有人把它改回 `pb`）
pkg_pin() {
  local rag_file=$1 expect=$2 why=$3
  local got
  got="$(grep -m1 -E '^[[:space:]]*package[[:space:]]+[A-Za-z0-9_.]+[[:space:]]*;' "$rag_file" 2>/dev/null | sed -E 's/.*package[[:space:]]+([A-Za-z0-9_.]+)[[:space:]]*;.*/\1/')"
  if [[ -z "$got" ]]; then
    echo "  ✗ proto package 钉住失败：$(basename "$(dirname "$rag_file")")/$(basename "$rag_file") 读不到 package 行"
    FAILED=1
  elif [[ "$got" == "$expect" ]]; then
    echo "  ✓ package = ${got}（${why}）"
  else
    echo "  ✗ package = ${got}，**应为 ${expect}**（${why}）⇒ 改动会破坏该域的 gRPC 方法路径前缀"
    FAILED=1
  fi
}

echo "== proto wire 契约逐字节对照（10 个域）=="
for row in "${DOMAINS[@]}"; do
  IFS='|' read -r _go _rag _rag_path _label <<< "$row"
  go_file="$GO_PROTO_DIR/${_go}.proto"
  rag_file="$ROOT/${_rag_path}"
  echo "== ${_go} =="
  if [[ ! -f "$go_file" || ! -f "$rag_file" ]]; then
    # 🔴 2026-10-29（R4 轮）修：这里原来写的是裸 `$rag_file）` ——
    #    **macOS 自带 bash 3.2 会把紧跟在 `$VAR` 后的多字节字符（全角右括号 `）`）
    #    算进变量名** ⇒ 变量名变成 `rag_file\xef\xbc\x89` ⇒ `set -u` ⇒
    #    `rag_file?: unbound variable` ⇒ **在「文件缺失」这条错误路径上直接崩掉**，
    #    报错信息根本打不出来 ⇒ 守门**用错误的姿势失败**。
    #    修法 = **一律用 `${VAR}` 显式界定变量名边界**（同 `check-jar-executable.sh` 的同类修法）。
    echo "  ✗ ${_label}: 文件缺失（go=${go_file} rag=${rag_file}）"
    FAILED=1
    continue
  fi
  verdict "$go_file" "$rag_file" "$_label"
  case "$_go" in
    like)    pkg_pin "$rag_file" "like"    "SD-2026-10-23-05 已签字偏离 · Go 侧的 pb 是遗留包名" ;;
    comment) pkg_pin "$rag_file" "comment" "SD-2026-10-23-04 已签字偏离 · Go 侧的 pb 是遗留包名" ;;
    *)       pkg_pin "$rag_file" "$_go"    "两侧逐字相同（Go 侧即此值）" ;;
  esac
done

cat <<'NOTE'
NOTE:
  覆盖 10 个域（= RAG 侧全部 proto 组件）；`points` / `security` **不在范围内**（Go 侧范围外）。
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
echo "✓ 全部 ${#DOMAINS[@]} 个域的 proto wire 契约逐字节一致"
