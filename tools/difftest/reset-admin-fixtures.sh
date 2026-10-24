#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  reset-admin-fixtures — 把 admin 域对拍用到的**两侧夹具**复位到初始态
# ════════════════════════════════════════════════════════════════════════
#
# 【为什么需要它】
#   `tools/difftest/diff.py --scenario admin` **不是幂等的**，跑完会留下两处改动：
#     ① `admin.update` 会把 admin 的**密码**改成 'newadmin123'
#        ⇒ 下一轮 `admin.login`（用 admin/admin）直接 1002「用户名或密码错误」
#           ⇒ 后面 18 个场景全部被带成假 DIFF（实测 2026-10-24 踩过）。
#     ② `admin.create` 每次都用**固定**用户名 'dt_admin2'
#        ⇒ 第二轮会撞「用户名已存在」(Go 1016 / RAG 1001)。
#
# 【两侧的密码都是 bcrypt】
#   Go   : service/user/common/cryptx/password.go:8
#          `bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)`（cost=10）
#   RAG  : AdminSeederBootstrap → `PasswordUtil.encrypt(...)`（Spring BCrypt，cost=10）
#   ⇒ 一份 bcrypt("admin") 的哈希两边都能验过 ⇒ 可以用同一份哈希复位两侧。
#   ⚠️ Python 的 bcrypt 默认吐 `$2b$`，Go 的 x/crypto/bcrypt 与 Spring 的
#      BCryptPasswordEncoder **都接受** `$2b$` ⇒ 可以直接用。
#
# 【不碰什么】
#   只改 admin 域的**夹具行**，不动 users / message 的数据
#   ⇒ 用户域与 message 域的场景仍按它们自己的前置条件跑。
#
# 用法：
#   bash tools/difftest/reset-admin-fixtures.sh
set -euo pipefail

PG_CONTAINER="difftest-postgres"
DB_USER="admin"
DEFAULT_PW="admin"
RESET_USER="admin"          # 被复位密码的 admin 用户名
FIXTURE_USER="dt_admin2"    # admin.create 场景用的固定用户名

if ! docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
  echo "找不到容器 $PG_CONTAINER —— 请先起 difftest 栈（tools/difftest/run.sh）" >&2
  exit 2
fi

HASH=$(python3 - << 'PY'
import bcrypt
print(bcrypt.hashpw(b"admin", bcrypt.gensalt(rounds=10)).decode())
PY
)
echo "bcrypt(admin) = $HASH"

# ── Go 侧：go_db.admins ────────────────────────────────────────────────
docker exec -i "$PG_CONTAINER" psql -U "$DB_USER" -d go_db -v ON_ERROR_STOP=1 -q <<SQL
UPDATE admins SET password = '${HASH}' WHERE username = '${RESET_USER}';
DELETE FROM admins WHERE username = '${FIXTURE_USER}';
SQL
echo "  ✓ go_db.admins：密码复位 + 清理 ${FIXTURE_USER}"

# ── RAG 侧：rag_db.admin_admin ─────────────────────────────────────────
docker exec -i "$PG_CONTAINER" psql -U "$DB_USER" -d rag_db -v ON_ERROR_STOP=1 -q <<SQL
UPDATE admin_admin SET password = '${HASH}' WHERE username = '${RESET_USER}';
DELETE FROM admin_admin WHERE username = '${FIXTURE_USER}';
SQL
echo "  ✓ rag_db.admin_admin：密码复位 + 清理 ${FIXTURE_USER}"

# ── Redis 黑名单 ───────────────────────────────────────────────────────
# ⚠️ **必须一起清**：黑名单键是 `admin:jwt_blacklist:<token>`，而 token 由
#   `admin.logout` 场景写入 ⇒ 上轮 logout 的 token 会留在 Redis 里。
#   ⇒ 只复位数据库密码是不够的：下一轮虽然能登录成功，但**旧的**黑名单仍在，
#     一旦新 token 恰好与旧的命中（同一秒内签发 + 同一 payload ⇒ 完全相同的 token），
#     就会表现为「刚登录就被判黑名单」。
#   实测踩过（2026-10-24）：不清 Redis 时 getself 直接 1006「TOKEN已过期」。
redis_keys=$(docker exec difftest-redis redis-cli --scan --pattern 'admin:jwt_blacklist:*' | wc -l | tr -d ' ')
if [ "$redis_keys" != "0" ]; then
  docker exec difftest-redis sh -c "redis-cli --scan --pattern 'admin:jwt_blacklist:*' | xargs -r redis-cli DEL >/dev/null"
  echo "  ✓ Redis：清掉 ${redis_keys} 个 admin:jwt_blacklist:* 键"
else
  echo "  ✓ Redis：黑名单本来就是空的"
fi

echo "夹具已复位 ⇒ 可以重跑：python3 tools/difftest/diff.py --scenario admin"
