#!/usr/bin/env bash
# ============================================================================
# 探针基建守门网 · **端口选取 / 端口归属** · favorite 域
# ----------------------------------------------------------------------------
# 背景 ⇒ `docs/Pitfalls/favorite/api.md` PIT-FAV-API-008：
#   OS 随机端口在**临时端口段 49152–65535** ⇒ 正是 **Docker 随机发布容器端口**的段；
#   macOS 上 `127.0.0.1:P` 与 `0.0.0.0:P` **能共存**（SO_REUSEADDR）
#   ⇒ 探针客户端连 `127.0.0.1:P` 会**命中更具体的那个**（实测那次打到了 PostgreSQL）
#   ⇒ 探针侧比 JUnit 侧更危险：`wait_port` **只验「连得上」**
#   ⇒ **连到别人的服务也算就绪** ⇒ 探针结论**整个不可信**（而不只是报错）。
#
# ⇒ 修法：专用端口段（21400–21599，避开临时端口段）+ **双绑定自检**
#   （`0.0.0.0:P` **且** `127.0.0.1:P` 都要能绑 ⇒ 任何第三方持有者都被排除）。
#
# 本守门网**从各探针的 run.sh 里原样抽出那段 python**（不重写 ⇒ 测的就是探针实际跑的那份），
# 然后跑 4 个场景：
#   ① 裸调                 ⇒ 返回一个**在段内**的端口
#   ② 带「已用端口」再调   ⇒ 返回**互不相同**的端口（否则两个服务抢同一个端口）
#   ③ 有人占着 127.0.0.1:P ⇒ **必须跳过 P**（Docker 发布端口那种占用）
#   ④ 有人占着 0.0.0.0:P  ⇒ **必须跳过 P**（另一个 Tomcat 占通配地址那种占用）
#
# 用法：bash tools/probe-gate/favorite/check-port-ownership.sh
# 退出码：0 = 每个探针的端口选取都守规矩；1 = 有探针判错。
#
# ⚠️ **域作用域**：本文件只管 favorite 的探针；别的域若有同款端口选取，
#    需各自复制本守门网 ⇒ 归 `global` 收口域登记。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[probe-gate]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }

PROBES=(
  "api-full-parity-probe"
  "grpc-contract-probe"
)

DRIVER="$(mktemp)"
cat > "${DRIVER}" <<'DRIVER_PY'
import contextlib, io, socket, sys

body_path = sys.argv[1]
src = open(body_path, encoding="utf-8").read()


def pick(used=()):
    """按探针真实用法调用那段 python（改 sys.argv + 捕获 stdout）。"""
    old = sys.argv
    sys.argv = ["-"] + [str(x) for x in used]
    buf = io.StringIO()
    err = None
    try:
        with contextlib.redirect_stdout(buf):
            exec(compile(src, body_path, "exec"), {"__name__": "__main__"})
    except SystemExit as e:
        err = str(e)
    finally:
        sys.argv = old
    out = buf.getvalue().strip()
    return (int(out) if out else None), err


fails = 0


def report(ok_flag, msg):
    global fails
    if ok_flag:
        print("OK:" + msg)
    else:
        fails += 1
        print("BAD:" + msg)


# ① 裸调 ⇒ 必须落在专用端口段内
p1, err = pick()
report(p1 is not None and 21400 <= p1 <= 21599,
       "裸调返回 %s（须落在 21400-21599）%s" % (p1, "" if err is None else "err=" + err))

# ② 带「已用端口」再调 ⇒ 必须换一个
p2, err = pick([p1] if p1 else [])
report(p2 is not None and p2 != p1, "排除已用端口 %s 后返回 %s（须 ≠ %s）" % (p1, p2, p1))

# ③ 有人占着 127.0.0.1:21400（Docker 发布端口那种）⇒ 必须跳过
hold = socket.socket()
hold.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
hold.bind(("127.0.0.1", 21400))
hold.listen(1)
p3, err = pick()
hold.close()
report(p3 is not None and p3 != 21400, "127.0.0.1:21400 被占时返回 %s（须 ≠ 21400）" % p3)

# ④ 有人占着 0.0.0.0:21400（另一个 Tomcat 占通配地址那种）⇒ 必须跳过
hold2 = socket.socket()
hold2.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
hold2.bind(("0.0.0.0", 21400))
hold2.listen(1)
p4, err = pick()
hold2.close()
report(p4 is not None and p4 != 21400, "0.0.0.0:21400 被占时返回 %s（须 ≠ 21400）" % p4)

sys.exit(1 if fails else 0)
DRIVER_PY

FAILED=0
for p in "${PROBES[@]}"; do
  SRC="${RAG_ROOT}/tools/${p}/favorite/run.sh"
  say "检查 ${p}"
  if [ ! -f "${SRC}" ]; then
    bad "${p}: 找不到 ${SRC}"; FAILED=$((FAILED+1)); continue
  fi
  # ⚠️ 原样抽出探针里**第一段** python（`<<'PY'` … `PY`），**不重写**
  #    （run.sh 里有好几段 python heredoc ⇒ 必须只取第一段，取到后面那段就全错）
  BODYFILE="$(mktemp)"
  awk -v marker="<<'PY'" '
        index($0, marker) && !flag { flag = 1; next }
        /^PY$/ && flag { exit }
        flag { print }
      ' "${SRC}" > "${BODYFILE}"
  if [ ! -s "${BODYFILE}" ]; then
    bad "${p}: run.sh 里没抽到端口选取的 python 段（实现改了 ⇒ 这里要跟着改）"
    FAILED=$((FAILED+1)); rm -f "${BODYFILE}"; continue
  fi
  OUT="$(python3 "${DRIVER}" "${BODYFILE}" 2>&1)"
  RC=$?
  rm -f "${BODYFILE}"
  while IFS= read -r line; do
    case "${line}" in
      OK:*)  ok "${p}: ${line#OK:}" ;;
      BAD:*) bad "${p}: ${line#BAD:}" ;;
      *)     bad "${p}: 端口选取自测输出异常：${line}" ;;
    esac
  done <<< "${OUT}"
  if [ "${RC}" -ne 0 ]; then
    FAILED=$((FAILED+1))
  fi
done
rm -f "${DRIVER}"

echo
if [ "${FAILED}" -eq 0 ]; then
  ok "端口选取守门网通过：${#PROBES[@]} 个探针的端口选取都守规矩"
  exit 0
fi
bad "端口选取守门网失败：${FAILED} 项"
exit 1
