#!/usr/bin/env bash
# =============================================================================
# rag-secrets.sh —— RAG 侧 **fail-closed 密钥**的统一注入（2026-10-30 活体对拍轮新增）
# =============================================================================
#
# 【它解决什么】
#   RAG 的 users 域模块把 JWT 密钥写成了**故意没有默认值**的占位符
#   （P0 #8 的 fail-closed 守卫）：
#     · service/users/user/user-rpc/src/main/resources/application.yaml:148
#         access-secret: ${USER_JWT_SECRET}
#     · service/users/user/user-api/src/main/resources/application.yml
#         ${USER_JWT_SECRET}
#     · service/users/admin/admin-rpc|admin-api/src/main/resources/application.yml
#         ${ADMIN_JWT_SECRET}
#     · service/message/message-api/src/main/resources/application.yml
#         ${USER_JWT_SECRET} + ${ADMIN_JWT_SECRET}
#   ⇒ 占位符解析不出来时**原样留下** ⇒ 守卫抛
#     `IllegalStateException: … 仍是未解析的占位符（${USER_JWT_SECRET}）`
#     ⇒ **进程根本起不来**（`java -jar` 直接退出）。
#
# 【为什么要有这个共享件（而不是每个脚本各写一遍）】
#   🔴 **2026-10-30 活体对拍轮实测**：**12 个**起 RAG java 进程的探针
#      （`exp-*.sh` 9 个 + `run-hot.sh` + `check-jar-executable.sh` + `run.sh`）
#      **全都漏了这两个 env** ⇒ 它们起 RAG 侧进程时**全部起不来**
#      ⇒ 表现为「探针报『RAG 侧超时 / 启动失败』」
#      ⇒ **看起来像产品缺陷，其实是探针自己没给密钥**。
#   ⇒ 改法：**一处定义**（本文件）+ 各脚本 `source` 一行 ⇒ 以后新增探针照抄即可。
#
# 【取值口径】
#   · **随机**生成（`secrets.token_hex(16)`）⇒ 不落仓 · 不复用生产密钥 · 不进 git
#   · 若外部已给（`USER_JWT_SECRET=… bash 某个探针.sh`）⇒ **尊重外部值**，不覆盖
#   ⇒ 与 `tools/api-full-parity-probe/users/run.sh` 的做法一致。
#
# 【用法】
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/rag-secrets.sh"
# =============================================================================

if [ -z "${USER_JWT_SECRET:-}" ]; then
  USER_JWT_SECRET="$(python3 -c 'import secrets;print(secrets.token_hex(16))')"
fi
if [ -z "${ADMIN_JWT_SECRET:-}" ]; then
  ADMIN_JWT_SECRET="$(python3 -c 'import secrets;print(secrets.token_hex(16))')"
fi
export USER_JWT_SECRET ADMIN_JWT_SECRET
