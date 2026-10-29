# DRIFT 台账 · `service/users/admin/admin-rpc`

> 🟥 **当前态指针（2026-10-29 · R4·T-4.2（G-12）回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部表仍写「核对日期：2026-10-23」＋「最终判定：🔴 SEMANTICALLY NOT EQUIVALENT（6 条 OBSERVABLE DIFFERENCE，0 条 SEMANTIC MISMATCH）」，而正文已到 2026-10-27 第五十轮双向合流后的批量订正）⇒ **当前状态一律以 §11 一节为准**：「11. 🟢🔴 **2026-10-26 第五十轮双向合流后** —— 前提变了 ⇒ 本域台账一批断言必须重写」（其「11.3 ✅ **本域台账的批量订正（合回前提兑现）**」＝ 25 处「当前状态」断言的新权威口径）⇒ ⚠️ 「10. 最终判定（spec §39 · 18 项 checklist）」的判定框是**合流前**口径 ⇒ **不要**读作当前结论；最新一轮见「12. 2026-10-27：Redis 在 **rpc 侧**的同机制不同形态 ⇒ `DRIFT-ARPC-013`（**已修** · 域内自闭环）」，末尾最新一条见「🔴 `DRIFT-ARPC-014` · **「OS 盲选口」族** · 本组件 **5 处**（2026-10-28 · G1 lane）」。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

> **⚠️ 2026-10-23 起本文件不再是纯导航页。**
> 2026-10-23 完成了本组件（Go `service/user/admin/rpc` ↔ RAG `service/users/admin/admin-rpc`）
> 的**逐组件语义一致性验证**，按 spec §31-b 追加了 §1–§10 十二节正式台账。
> 下方「历史导航页」一节是 2026-10-21 的原文，**一字未改**，保留作索引。
> 历史 `DRIFT-001..010` / `A-01` / `A-02` / `DRIFT-FRAMEWORK-001` 条目**仍在
> [`_shared.md`](_shared.md) 未动**，本文件的新编号一律走 `DRIFT-ARPC-*` 前缀
> （沿用仓库既有的 `DRIFT-LIKE-*` / `DRIFT-MSG-*` / `DRIFT-MSGAPI-*` 组件前缀约定），
> 与共享台账零冲突。

---

# 正式台账（2026-10-23 组件级语义验证）

| | |
|---|---|
| **组件** | `admin-rpc`（13 个管理 RPC 的服务端实现） |
| **Go 侧** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/user/admin/rpc`（27 个文件） |
| **RAG 侧** | `/Users/pr/Documents/java&golang/RAG/service/users/admin/admin-rpc`（22 个 main 文件 + 11 个测试类） |
| **核对日期** | **2026-10-23** |
| **验证方法论** | `.agents/specs/semantic-verification-prompt.md` 全 40 章节 + 本项目 8 条硬约束 |
| **最终判定** | 🔴 **SEMANTICALLY NOT EQUIVALENT**（6 条 OBSERVABLE DIFFERENCE，0 条 SEMANTIC MISMATCH） |

## 证据强度声明（§3.3 / 硬约束 1）

本台账每条结论都标注证据强度。**强度从弱到强**：

| 标签 | 含义 | 本轮是否产出该强度证据 |
|---|---|---|
| **双侧活体对拍** | Go 栈与 RAG 栈**同时起**、同输入、同依赖，逐字段比对 | ❌ **本轮零条**。admin-rpc 的 gRPC 层没有对过；§6.2.3 A-U2 记的 39 步对拍是 **admin-api 的 HTTP wire 层**，不能当 admin-rpc 的 gRPC 证据 |
| **真 PG + 真容器（单侧）** | 真实 PostgreSQL 16 + 真实 Spring 容器，只跑 RAG | ✅ 29 条（5 个容器测试类） |
| **真 PG schema 对照（双侧）** | 同一个 PG 实例里 `go_db`（GORM AutoMigrate 建）与 `rag_db`（RAG DDL 建）并列读 `information_schema` | ✅ 1 条（`check-ddl-static.py`，覆盖 35 列） |
| **静态源码 + 框架源码消费点** | 读 Go/RAG 双方源码，且 Go 侧结论追到**消费该 tag 的控制流** | ✅ 其余全部 |
| **纯 Mockito / 反射结构断言** | 不装配 Spring、不执行 SQL | ✅ 50 条（**不构成 `EQUIVALENT` 证据**，硬约束 2） |

> ⚠️ **本轮没有任何一条结论是双侧活体对拍。** 因此本台账里所有 `EQUIVALENT` 判定
> 的强度上限是「单侧真 PG」或「静态源码 + 框架消费点」，**不是**已闭环。
> 见 §9 `UNRESOLVED-ARPC-001`。

## 本轮实跑的命令与观察结果

| 命令 | 结果 |
|---|---|
| `mvn -pl service/users/admin/admin-rpc -am -o test`（**单模块隔离跑**，遵 AGENTS.md「并发构建期间不采信全仓数字」） | **BUILD SUCCESS**，11 个测试类 **79 条 / 0 失败 / 0 错误 / 0 跳过**。其中 **29 条**跑在真实 PostgreSQL 16 容器上（`AdminCreatePasswordOrderRegressionTest` 9 + `AdminRpcConcurrencyWiringTest` 2 + `AdminRpcPaginationGormSemanticsTest` 7 + `AdminRpcTransactionWiringTest` 4 + `AdminRpcContainerWiringTest` 7），**Skipped=0**（`@EnabledIfDockerAvailable` 确实放行，没有被静默跳过） |
| `tools/difftest/check-proto-static.sh` | `EXIT=0`；`admin(13 RPC)` → 「✓ admin.proto ↔ admin.proto：wire 契约逐字节一致」 |
| `tools/difftest/check-ddl-static.py` | 输出 **12 处列类型差异**（本组件占 7 处）；脚本 `EXIT=0`（表名映射无误、列集合一致） |
| `tools/difftest/check-flyway-ledger.py` | `EXIT=0`；**扫描到 3 个启用 Flyway 的模块，不含 admin-rpc** |
| `docker exec -i difftest-postgres psql -d go_db -c "… information_schema.columns …"` | 直接读到 `admin_invites.expires_at / used_at / create_time / update_time` 与 `admins.create_time / update_time` 均为 `timestamp with time zone`，`admins.extra_info` 为 `text` |

---

## 1. Component Contract（spec §4 + §5）

```
Component:  admin-rpc —— 管理员对 users_user / admin_admin / admin_invite 三张表的网关服务
Purpose:    给 admin-api 提供 13 个管理用例的 gRPC 服务端实现
Public API: AdminService（13 rpc，proto/admin.proto:155-169，逐字与 Go 一致）
Inputs:     proto3 message（13 个 Req）；**本组件不做任何鉴权**，uid / operator_id 一律信任入参
Outputs:    proto3 message（12 个 Resp）或 io.grpc.StatusRuntimeException
Dependencies:
            PostgreSQL（3 张表）        ← Go svc.ServiceContext.AdminModel
            Redis（仅 Logout 写黑名单）  ← Go svc.ServiceContext.BizRedis
            Snowflake（uid 生成）
            OTel（span）                ← Go 逐 Logic 手写 tracer.Start
State:      无进程内状态（无 cache、无内存态）
Side Effects:
            6 条写路径（createAdmin / createInviteCode / updateUser / updateSelf /
            deleteUser / banUser / unBanUser / resetUserPassword）+ 启动期建表与种子账号
Failure Model: 全部映射为 gRPC Status；非 StatusRuntimeException 由 AdminServiceGrpcImpl.invoke 兜成 INTERNAL
Success 意味着: Resp 正常返回（**注意 Go 侧 13 个 Resp 里有 8 个带 success 字段，其中 1 个 Go 从不置 true，见 DRIFT-ARPC-002**）
Failure 意味着: gRPC 错误码 + description；api 层再翻译成业务错误码
```

**边界（AGENTS.md §6.3 问题 2，2026-10-23 独立复核）**：

```
✅ 复核结论：Go 侧 admin/rpc 零出向 gRPC 依赖 —— 约定成立，不是漂移。

证据（Go 消费点）：
  · svc/servicecontext.go:21-25   ServiceContext 只有 Config / AdminModel / BizRedis 三个字段
  · admin.go:36                  唯一的 zrpc 入口是 zrpc.MustNewServer（服务端）
  · 全模块 grep "MustNewClient"  → 零命中
  · adminservice/adminservice.go:67  NewAdminService(cli) 是 goctl 生成物，模块内零调用

证据（RAG 侧）：
  · admin-rpc/src/main/java/**  grep "GrpcClient|BlockingStub|FutureStub|discovery://"  → 零命中
    （唯一命中是 AdminRpcApplication.java:37 的一句 javadoc 提及）

⇒ 两侧同为「直连 SQL，不转发给 user-rpc」。RAG 现状与 Go 一致。
```

---

## 2. GO-SEMANTIC-MODEL（spec §7）

> 逐 OP 摘要。完整 18 字段模板见 spec §6；这里给语义顺序 + 证据行号。

```
OP-CREATE-ADMIN  CreateAdmin（createadminlogic.go:39-126）
  Input:   username / password / email / extra_info(map) / invite_code
  Pre:     invite_code 非空，否则 PermissionDenied（:48-51，**在事务外**）
  Proc:    1) trim username(:44) + trim inviteCode(:45)
           2) m.conn.Transaction(fn)（:54）—— 事务闭包内 5 步：
              W1 FindAdminInviteByCodeForUpdate（:55 → model/admin.go:131
                 clause.Locking{Strength:"UPDATE"} ⇒ SELECT ... FOR UPDATE）
              W2 invite.UsedAt != nil || !invite.ExpiresAt.After(now) ⇒ PermissionDenied（:65-68）
              W3 FindOneAdminByUsernameTx 命中 ⇒ AlreadyExists（:70-77）
              W4 cryptx.PasswordEncrypt（:79-82）  ← **在 W1/W2 之后**
              W5 snowflake.GetID（:84-87） → InsertOneAdminTx（:96）→ IsUniqueViolation ⇒ AlreadyExists
              W6 UpdateAdminInviteUsedTx（:105）
  Err:     非 PermissionDenied/AlreadyExists 一律 Internal「DB添加失败」（:110-120）
  Result:  {uid}
  SideFX:  admin_admin +1 行、admin_invite 标 used、**三张表的 update_time 由 GORM autoUpdateTime 推进**

OP-CREATE-INVITE-CODE  CreateInviteCode（createinvitecodelogic.go:44-102）
  1) FindOneAdminByUid(operator_id) ⇒ NotFound / Internal（:50-61）
  2) expiresAt = now + 10min（:63，TTL 常量 :26）
  3) 最多 3 次循环（:64）："ADM-" + hex(8 随机字节) 大写（:104-110）→ InsertOneAdminInvite
     unique violation ⇒ continue（:87-89）；其他 DB 错 ⇒ Internal；3 次仍冲突 ⇒ Internal（:97-101）

OP-LOGIN  Login（loginlogic.go:35-66）
  FindOneAdminByUsername ⇒ NotFound「用户不存在」/ Internal「DB查询失败」
  cryptx.CheckPassword 恒 false-ify 任何错误（含 hash 畸形，cryptx/password.go:15-18）
  ⇒ 错密码与坏 hash 同为 Unauthenticated「密码错误」

OP-LOGOUT  Logout（logoutlogic.go:31-46）
  token 空 ⇒ InvalidArgument；Redis Setex("admin:jwt_blacklist:"+token, "banned", AccessExpire)
  失败 ⇒ Internal「注销失败,Redis写入异常」；成功 ⇒ **空 LogoutResp{}（success 恒 false）**

OP-GET-USER / GET-SELF  单行查，NotFound/Internal；GetSelf 的 email 走 stringValue(nil⇒"")（helper.go:13-18）

OP-GET-USER-LIST  GetUserList（getuserlistlogic.go:30-54 → model/admin.go:148-164）
  1) keyword **不 trim**，len>0 ⇒ LIKE '%kw%' OR email LIKE（:152-155）
  2) Count 走同一 Where（:156）
  3) offset := (page-1)*pageSize；Order("id desc").Offset(int(offset)).Limit(int(pageSize))（:159-160）
     ⇒ GORM v1.31.1 clause/limit.go 的 Limit.Build 静默抑制：LIMIT 只在 ≥0 生成、OFFSET 只在 >0 生成
  4) list := make([]*pb.UserInfo, 0)（:38）⇒ 空结果在 wire 上与「无 repeated」同形

OP-UPDATE-USER  UpdateUser（updateuserlogic.go:35-113）
  查重(43) → username 非空则查冲突、existUser.Uid != in.Uid ⇒ AlreadyExists(57-67)
  → 逐字段「非空才 set」构造 toUpdate（55-86）→ Updates(toUpdate)（model/admin.go:58）
  → 重读（95）→ {user}

OP-UPDATE-SELF  UpdateSelf（updateselflogic.go:33-99）
  查 uid(34) → username 冲突检查(44-52) → toUpdate（email 走 **optionalString** 会 trim、空白⇒nil，:68）
  → Updates → 重读 → {success:true, admin}

OP-BAN / UNBAN  纯 UpdateUserStatusByUid；RowsAffected==0 ⇒ NotFound（model/admin.go:103-109）
OP-DELETE  DeleteOneUserByUid；RowsAffected==0 ⇒ NotFound「用户%d不存在」（model/admin.go:49-55）
OP-RESET-PASSWORD  查 uid ⇒ NotFound；bcrypt(**Config.System.DefaultPassword**) ⇒ 失败则 Internal；
  UpdateUserPasswordByUid **不检查 RowsAffected**（model/admin.go:111-114）

OP-SEEDER  NewServiceContext 内 logx.Must(ensureDefaultAdmin)（servicecontext.go:34-38-74）
  已存在 ⇒ no-op；不存在 ⇒ bcrypt(常量 "admin"，**不读配置**) + GetID + Insert
  unique violation ⇒ 重读一次，存在就 OK，否则抛原异常

OP-SCHEMA  InitDB 里 db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})（model/db.go:15）
  ⇒ 建 3 张表：admins / admin_invites / users（表名由 data_model.go:16-50 显式指定）
```

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（spec §7 / §34）

> 与 §2 **完全相同的结构**（这是 spec §34 的目的：防止两边结构不同导致漏维度）。

```
OP-CREATE-ADMIN  AdminDomainService.createAdmin / doCreateAdmin（AdminDomainService.java:457-545）
  Pre:     invite trim 后非空，否则 PERMISSION_DENIED（:474-477，事务外）—— 与 Go 同位置 ✔
  事务外:  Snowflake.nextId()（:509）—— ⚠️ Go 在事务内 W5（顺序差异，见 §4 矩阵）
  跨 Bean: createAtomicService.createAdminAtomic(..., passwordIsPlaintext=true)（:525-526）
           ⇒ 独立 Bean，@Transactional 代理生效
  AdminCreateAtomicService.createAdminAtomic（:110-205）
    W1 selectOne(eq code).last("FOR UPDATE")（:116-120）⇒ 与 Go 的 clause.Locking 等价
       null ⇒ InviteUnavailableException（:121-123）
       usedAt != null || !expiresAt.isAfter(now) ⇒ InviteUnavailable（:132-135）
    W2 selectOne(eq username) 命中 ⇒ UsernameTakenException（:137-143）
    W3 PasswordUtil.encrypt（:153-167）  ← **在 W1/W2 之后**（R5 修复）
    W4 adminMapper.insert（:178）
    W5 标 invite used：set used_by_uid + used_at（:180-184）
  Err:     InviteUnavailable ⇒ PERMISSION_DENIED；UsernameTaken ⇒ ALREADY_EXISTS；
           isUniqueViolation ⇒ ALREADY_EXISTS；其余 ⇒ INTERNAL「DB添加失败」（:531-541）
  SideFX:  ⚠️ **此句是 2026-10-23 首轮对照时的状态** —— 当时 6 条写路径**都不推进** `update_time`；
           `FIX-ARPC-001`（同轮修复）已给全部 UPDATE 补上 `update_time = CURRENT_TIMESTAMP`
           ⇒ **现已推进**（`grep -c` 实测 2026-10-27：admin-rpc **6 处** = AdminDomainService 5 + AdminCreateAtomicService 1）
           ⇒ 详见 §1.2「FIX-ARPC-001」；残留「参数全空时 Go 不发 UPDATE」边界见该节「已知残留分歧」

OP-CREATE-INVITE-CODE  AdminDomainService.createInviteCode / doCreateInviteCode（:572-654）
  结构与 Go 同：校验 operator → TTL 10min（常量 `:103` / 用点 `:605`）→ 3 次循环（`:607`）→ unique violation continue（`:633-636`）
  → 生成 "ADM-" + HexFormat 大写（`:656-660`）

OP-LOGIN  :197-242  —— 三态 + PasswordUtil.checkDetailed（MALFORMED_HASH 也报「密码错误」，对齐 Go）

OP-LOGOUT  :258-277  —— 同 key 前缀、TTL 取配置；成功返回 true 但
  AdminServiceGrpcImpl:85 构造 LogoutResp.newBuilder().build() ⇒ success 恒 false ✔ 与 Go 一致

OP-GET-USER / GET-SELF  :760-794 / :290-320

OP-GET-USER-LIST  :796-902
  keyword **不 trim**（:803-804）✔；COUNT 独立查询（:837-844）；
  offset := (page-1)*pageSize 用 long 复刻 int64 回绕（:832）；
  goStylePagination（:904-921）逐字镜像 GORM clause/limit.go 的 LIMIT/OFFSET 抑制规则

OP-UPDATE-USER  :940-1074  —— email **不 trim**（:1011-1014）✔ 与 Go :81-83 一致
OP-UPDATE-SELF  :322-433  —— email **trim 且空白跳过**（:379-385）✔ 与 Go optionalString 一致
OP-BAN/UNBAN/DELETE/RESET-PASSWORD  :672-1205  —— 含 RowsAffected 语义与 Go 逐条一致

OP-SEEDER  AdminSeederBootstrap.ensureDefaultAdmin（:109-159）
  密码取常量 AdminDomainService.DEFAULT_ADMIN_PASSWORD="admin"（:121），**不读配置** ✔（DRIFT-001 已修）
  unique violation ⇒ 重读（:138-152）✔ 与 Go servicecontext.go:62-70 同款
  @DependsOn("adminSchemaBootstrap")（:89）保证建表先于种子

OP-SCHEMA  AdminSchemaBootstrap.initSchema（:51-64）
  → users-common SchemaInitializer.applyAll(connection, "admin-rpc")
  ⇒ 幂等重放 **4** 份 DDL（01/02/03/04），含 users-common/src/main/resources/db/02_create_user_avatar_history.sql
  —— Go 只 AutoMigrate 3 张表（见 DRIFT-ARPC-005）
  + pg_advisory_lock(8675309) 串行化并发建表（SchemaInitializer.java:91,115）—— Go 无此保护（增强，非漂移）
```

---

## 4. Equivalence Matrix（spec §24 + §35）

> Result 只用 5 个枚举值：`EQUIVALENT` / `IMPLEMENTATION DIFFERENCE` / `OBSERVABLE DIFFERENCE` / `SEMANTIC MISMATCH` / `UNKNOWN`

| Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|
| CreateAdmin | 事务边界（4 步原子） | `m.conn.Transaction`（createadminlogic.go:54） | 独立 Bean `@Transactional`（AdminCreateAtomicService.java:110） | **EQUIVALENT** | 真 PG：`AdminRpcTransactionWiringTest` 4 条，W3 撞唯一 ⇒ invite 未被标 used（0 skipped） |
| CreateAdmin | **事务代理生效** | 无 AOP | 跨 Bean 调用（`AdminDomainService.doCreateAdmin` → `AdminCreateAtomicService.createAdminAtomic`，`AdminDomainService.java:525`） | **EQUIVALENT** | 真 PG 容器测试；结构断言 `AdminUpdateSelfAndCreateAdminRegressionTest:158-180` |
| CreateAdmin | **invite 行悲观锁** | `clause.Locking{Strength:"UPDATE"}`（admin.go:131） | `.last("FOR UPDATE")`（AdminCreateAtomicService.java:119） | **EQUIVALENT** | 真 PG：`AdminRpcConcurrencyWiringTest` N=8 ⇒ 1 OK + 7 拒（0 skipped） |
| CreateAdmin | 步骤序（invite→username→bcrypt→uid→insert→标 used） | createadminlogic.go:55,70,79,84,96,105 | `AdminCreateAtomicService.java:116,138,156,178,181`（W1 select / W2 select / bcrypt / W3 insert / W4 标 used） | **EQUIVALENT** | 真 PG：`AdminCreatePasswordOrderRegressionTest` 5 条 R5-A/B/C/C2/C3 |
| CreateAdmin | 空 inviteCode → PermissionDenied（事务外） | :48-51 | `AdminDomainService.createAdmin` 的 inviteCode 非空校验（`AdminDomainService.doCreateAdmin` 的 inviteCode 非空校验，`AdminDomainService.java:474-476`）—— **在 `createAdminAtomic`（事务）之前** ⇒ 事务外 | **EQUIVALENT** | 静态源码 |
| CreateAdmin | 空 username 不拦截 | 无校验（:44 只 trim） | 无校验（`AdminDomainService.doCreateAdmin` 只 trim，`AdminDomainService.java:485`，DRIFT-008） | **EQUIVALENT** | `AdminSemanticDriftRegressionTest:352` |
| CreateAdmin | **uid 生成位置** | 事务内 W5（:84） | 事务外（`AdminDomainService.doCreateAdmin` 的 `uid = Snowflake.nextId()`，`AdminDomainService.java:506-509`） | **IMPLEMENTATION DIFFERENCE** | 静态源码；uid 值本就随时间变，不构成可观测差异 |
| CreateAdmin | **update_time 推进** | GORM autoUpdateTime 必进 SET | 🟢 **已推进**（`FIX-ARPC-001`：`update_time = CURRENT_TIMESTAMP`，admin-rpc **6 处**） | **EQUIVALENT**（主路径）<br>⚠️ 残留边界：参数全空时 Go 不发 UPDATE、RAG 仍发一条只推进 `update_time` 的 UPDATE ⇒ 该边界仍 **OBSERVABLE DIFFERENCE**（`FIX-ARPC-001` §「已知残留分歧」，**未修**） | 🟢 **2026-10-27 订正**：原写「永不推进 / OBSERVABLE DIFFERENCE」⇒ **已过期** —— `FIX-ARPC-001`（2026-10-23 修复轮）已修（见 §1.2）⇒ ⚠️ **本表原先写的是修前状态**。<br>证据强度：**静态源码·RAG 侧**（`grep -c` 实测 2026-10-27 = 6；AdminDomainService 5 + AdminCreateAtomicService 1）· **双侧活体对拍未做** |
| CreateInviteCode | TTL=10min / 前缀 ADM- / 3 次重试 | createinvitecodelogic.go:26,64,109 | `AdminDomainService.java:103/106/109`（`INVITE_CODE_TTL` / `INVITE_CODE_MAX_RETRY` / `INVITE_CODE_PREFIX`；用点 `:605` / `:607` / `:659`） | **EQUIVALENT** | 静态源码 + 3 条 Mockito 单测 |
| Login | 三态 + 密码错 vs hash 畸形同码 | loginlogic.go:43-60；cryptx/password.go:15-18 | `AdminDomainService.doLogin`（`AdminDomainService.java:205-242`：NOT_FOUND `:212` · 密码三态 `:230-237`） | **EQUIVALENT** | 静态源码 + `AdminDomainServiceTest` |
| Logout | 黑名单 key/TTL/失败码 | logoutlogic.go:36-42 | `AdminDomainService.logout`（`AdminDomainService.java:258-277`：Redis 写 `:267` · B-1 守卫 `:268-271` · INTERNAL `:274`） | **EQUIVALENT** | 静态源码 |
| Logout | **success 字段** | false（空 Resp） | false（空 Builder） | **EQUIVALENT** | AdminServiceGrpcImpl.java:85 |
| GetUserList | keyword 不 trim | admin.go:152-155 | `AdminDomainService.getUserList` 的 keyword 判定（`AdminDomainService.java:803-804`） | **EQUIVALENT** | 真 PG：`AdminSemanticDriftRegressionTest` 2 条 + 容器测试 1 条 |
| GetUserList | LIKE 双重包裹 | `%kw%` | `%%kw%%`（MP 拼接） | **IMPLEMENTATION DIFFERENCE** | 真 PG（A-U3 已在 §6.2.3 关闭） |
| GetUserList | **负 offset/limit 抑制** | GORM clause/limit.go | `AdminDomainService.goStylePagination`（`AdminDomainService.java:904`；调用点 `:833`） | **EQUIVALENT** | 真 PG：`AdminRpcPaginationGormSemanticsTest` 7 条 |
| GetUserList | 空结果 wire 形状 | `make([]…,0)` | 无 addList | **EQUIVALENT** | 静态源码（proto3 repeated 两侧都零字节） |
| UpdateUser | 逐字段「非空才 set」 | updateuserlogic.go:56-86 | `AdminDomainService.doUpdateUser` 的逐字段 set 块（`AdminDomainService.java:973-1040`：username `:994` · password `:1000` · email `:1013`） | **EQUIVALENT** | 静态源码 |
| UpdateUser | email **不** trim | :81-83 | `AdminDomainService.updateUser` 的 email set（`AdminDomainService.java:1013`）—— **对比 `updateSelf` 的 `:385` 是 `email.trim()`** ⇒ 两侧 updateUser 都不 trim | **EQUIVALENT** | 静态源码 |
| UpdateUser | **success 字段（wire）** | **false**（未 set） | **true** | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-002 |
| UpdateUser | 重读后返回 | :95-113 | `AdminDomainService.updateUser` 的重读（`AdminDomainService.java:1051-1054`） | **EQUIVALENT** | 静态源码 |
| UpdateSelf | email trim + 空白跳过 | updateselflogic.go:67-69 | `AdminDomainService.updateSelf` 的 email trim（`AdminDomainService.java:379-385`） | **EQUIVALENT** | `AdminUpdateSelfAndCreateAdminRegressionTest` 3 条 |
| BanUser/UnBanUser | RowsAffected==0 ⇒ NotFound | admin.go:103-109 | `AdminDomainService.doBanUser` 的 RowsAffected 判定（`AdminDomainService.java:698-701`） | **EQUIVALENT** | 静态源码 |
| DeleteUser | NotFound 文本「用户%d不存在」 | deleteuserlogic.go:48 | `AdminDomainService.doDeleteUser` 的 NotFound 抛出（`AdminDomainService.java:1100`） | **EQUIVALENT** | 静态源码（格式串逐字一致） |
| ResetUserPassword | 不检查 RowsAffected | admin.go:111-114 | `AdminDomainService.doResetUserPassword`（`AdminDomainService.java:1186`「W4 update（不检查 RowsAffected）」） | **EQUIVALENT** | 静态源码 |
| ResetUserPassword | **default-password 为空** | bcrypt("") **成功** ⇒ 设空密码哈希 | 直接 `Internal「密码加密失败」`（`AdminDomainService.doResetUserPassword` 的 W3 段，`AdminDomainService.java:1165-1171`） | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-003 |
| Seeder | 密码来源 | 常量 "admin"（servicecontext.go:18） | 常量（`AdminDomainService.DEFAULT_ADMIN_PASSWORD`，`AdminDomainService.java:131`） | **EQUIVALENT** | 真 PG：`AdminRpcContainerWiringTest` 2 条 |
| Seeder | 并发启动幂等 | servicecontext.go:62-70 | AdminSeederBootstrap.java:138-152 | **EQUIVALENT** | 真 PG：容器测试「重复执行 seeder 不产生重复账号」 |
| Seeder | 相对 gRPC server 的时机 | MustNewServer **之前**（admin.go:34 vs :36） | `@PostConstruct`，早于 `SmartLifecycle.start()` | **EQUIVALENT** | 真 PG：容器测试「建表先于 seeder」+ DRIFT-010 已修 |
| Schema | **建表覆盖面** | 3 张（db.go:15） | **4 张**（SchemaInitializer.SCRIPTS） | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-005 |
| Schema | **时间列类型** | `timestamptz` | `timestamptz` | **EQUIVALENT** | 🟢 **2026-10-26 订正**：原写 RAG 侧 `timestamp`（无时区）⇒ **已过期** —— RAG 侧 DDL 早就是 `TIMESTAMPTZ`（`03_create_admin.sql` / `04_create_admin_invite.sql`，且带 `ALTER … TYPE TIMESTAMPTZ USING … AT TIME ZONE 'UTC'` 幂等重放）⇒ 本域 §「DRIFT-ARPC-004 ①② 已修」已修（见 §1.2）⇒ ⚠️ **本表原先写的是修前状态** |
| Schema | extra_info 列类型 | `text`（存 JSON 串） | `text` | **EQUIVALENT** | 🟢 **2026-10-26 订正**：原写 RAG 侧 `jsonb` ⇒ **已过期** —— DDL 侧早已是 `TEXT`（`03_create_admin.sql:58`）⇒ **两侧列类型一致**。<br>🔴 **但「写路径参数绑定」那一格（`admin-rpc` 的 `UserEntity.extra_info` 走 `JsonbTypeHandler` ⇒ jsonb 绑定）** ⇒ 🟢 **2026-10-26 第五十轮合流后已闭合**：global 的 B10（`ff80214`）在 `align/global` 上把它删掉（`JsonbTypeHandler` 整体删除 + 改走 `JsonTextTypeHandler` ⇒ `ps.setString`）⇒ ⇒ **主干上已生效**（实测：主干 `admin-rpc/.../mapper/` 下**已无** `JsonbTypeHandler.java`，只剩 `JsonTextTypeHandler.java`）⇒ ⇒ **本格现为完全 EQUIVALENT**<br>🟢 **连带**：users/**user** 域的 `DRIFT-USERRPC-V-003` 的**代码那一格**（落在本域源码树里的残留）也随之闭合 —— 他们已独立核实并回写（`0cf5077`） |
| Schema | 建表并发保护 | 无（AutoMigrate 裸跑） | `pg_advisory_lock` | **IMPLEMENTATION DIFFERENCE**（增强） | SchemaInitializer.java:91,115 |
| Flyway | 是否启用 | 不适用（Go 无 Flyway） | pom 无 flyway 三件套；`application.yml` 无 `spring.flyway.*` | **EQUIVALENT** | `check-flyway-ledger.py` EXIT=0 且不含 admin-rpc |
| 鉴权 | 是否在本组件鉴权 | **不鉴权**，信任入参 uid/operator_id | 同 | **EQUIVALENT** | 全模块无 JWT/session 校验代码 |
| 超时 | 服务端超时 | 2000ms（`go-zero@v1.10.2/zrpc/config.go` struct tag default）→ `DisableNativeRpcTimeout` → 拦截器 | `observability.rpc-timeout: 2s` → `RpcInterceptor` 调度 close | **EQUIVALENT**（服务端侧） | 静态源码；`AdminDeadlinePassthroughTest` 8 条 |
| 超时 | **是否 min 客户端 deadline** | 继承（`context.WithTimeout(parent,2s)`，rpc.go:38） | 只算自己 2s（`now + timeout`） | **UNKNOWN** | UNRESOLVED-ARPC-002 |
| 在途 SQL 取消 | 超时后 PG 侧阻塞查询存活时长 | 378ms | 4455ms | **OBSERVABLE DIFFERENCE** | 已台账（B-1 / E-4，跨组件，本组件只是消费方） |
| Metrics | 指标名与标签 | `admin_rpc_login_total` / `admin_rpc_high_risk_action_logal`（含 typo） | 同名同 typo（AdminRpcMetrics.java 头注释） | **EQUIVALENT** | 静态源码 |
| proto | wire 契约 | `proto/admin.proto` | RAG 侧副本 | **EQUIVALENT** | `check-proto-static.sh` EXIT=0，逐字节 |

---

## 5. External Interaction & Error Flow（spec §8 + §10）

### 5.1 错误映射全表（逐条对齐，无一例外）

| 触发条件 | Go（code / desc） | RAG（code / desc） | Result |
|---|---|---|---|
| Login 查不到 admin | NotFound / 用户不存在 | NOT_FOUND / 用户不存在 | EQUIVALENT |
| Login DB 错 | Internal / DB查询失败 | INTERNAL / DB查询失败 | EQUIVALENT |
| Login 密码错或 hash 畸形 | Unauthenticated / 密码错误 | UNAUTHENTICATED / 密码错误 | EQUIVALENT |
| Logout token 空 | InvalidArgument / Token不能为空 | INVALID_ARGUMENT / Token不能为空 | EQUIVALENT |
| Logout Redis 错 | Internal / 注销失败,Redis写入异常 | INTERNAL / 同 | EQUIVALENT |
| GetSelf / GetUser 查不到 | NotFound / 用户不存在 | 同 | EQUIVALENT |
| UpdateSelf username 被占 | AlreadyExists / 该用户名已被使用 | 同 | EQUIVALENT |
| CreateAdmin invite 空/错/过期/已用 | PermissionDenied / 管理员邀请码错误 | 同 | EQUIVALENT |
| CreateAdmin username 冲突 | AlreadyExists / 用户名已存在 | 同 | EQUIVALENT |
| CreateAdmin 其它 | Internal / DB添加失败 | 同 | EQUIVALENT |
| CreateInviteCode operator 查不到 | NotFound / 用户不存在 | 同 | EQUIVALENT |
| Ban/UnBan/Delete 目标不存在 | NotFound / 用户不存在（Delete 为「用户%d不存在」） | 同 | EQUIVALENT |
| ResetUserPassword bcrypt 失败 | Internal / 密码加密失败 | 同 | EQUIVALENT |

**Error propagation**：Go 每个分支显式 `return nil, status.Error(...)`；RAG 每个分支抛 `StatusRuntimeException`，由 `AdminServiceGrpcImpl.invoke`（:289-303）原样透传、非 Status 兜底 INTERNAL。⇒ **EQUIVALENT**。

**Error → Fallback 分支**：Go 全是 `Terminate`，RAG 全是 `Terminate`。唯一 `Continue` 路径是 CreateInviteCode 的 unique violation 重试，两侧都 `continue` 不埋点（Go :87-89 无埋点；RAG :536-539 有注释说明「不埋点，对应 Go 端 continue」）。⇒ **EQUIVALENT**。

### 5.2 外部交互

| External System | Operation | 语义关键点 | Result |
|---|---|---|---|
| PostgreSQL | SELECT/UPDATE/DELETE/INSERT | `model.ErrorNotFound` sentinel ↔ Java `NotFoundException`；PG 23505 ↔ `AdminExceptions.isUniqueViolation`（:66-81，沿 cause 链找 SQLException.getSQLState） | **IMPLEMENTATION DIFFERENCE** |
| PostgreSQL | `SELECT ... FOR UPDATE` | 两侧都锁 invite 单行 | **EQUIVALENT**（真 PG 实测） |
| Redis | `SETEX key "banned" 2592000` | key 必须是 `admin:jwt_blacklist:`（与 user-rpc 的 `user:jwt_blacklist:` 区分，D-006） | **EQUIVALENT** |
| Snowflake | nextId | Go 在事务内、RAG 在事务外 | **IMPLEMENTATION DIFFERENCE** |

---

## 6. 逐维度结论（spec §12–§21）

| § | 维度 | 结论 |
|---|---|---|
| §12 | **State Semantics** | invite 三态（未用/已用/过期）与 user.status 二态（0/1）两侧状态机逐条一致。`create_time`/`update_time` 的**写入来源**不同（见 §6 Config 与 DRIFT-ARPC-001/004），但本组件不读这两列 ⇒ 状态机本身 **EQUIVALENT** |
| §13 | **Cache Semantics** | 本组件**无缓存**（N/A）。唯一的 Redis 使用是 Logout 的黑名单写，两侧都是「写-only、无读回、无 DB fallback」。唯一 TTL 差异：`application.yml` 给 Lettuce 配了 `timeout: 3s`，Go `redis.RedisConf` 未配超时 ⇒ 依赖默认值。**无证据判定**，记入 UNRESOLVED-ARPC-003 |
| §14 | **Auth/Authz** | **两侧都不在本组件鉴权**，一律信任入参 `uid` / `operator_id`。JWT 校验全在 admin-api（不在本组件范围）⇒ **EQUIVALENT**。⚠️ 这也意味着 N-4（`operator_id` 恒 0 vs 真实 uid）**在本组件不可观测** —— 它只影响 OTel span 属性，而 RAG 的 admin-rpc **完全没有手写 span**（见下） |
| §15 | **Transaction Semantics** | 只有 CreateAdmin 有事务，4 步原子、失败全回滚。其余 12 个用例两侧**都无事务**（Go 无 `Transaction` 包裹；RAG 主源码 `@Transactional` 只出现在 AdminCreateAtomicService 的两个重载上，无第三处）⇒ **EQUIVALENT**（真 PG 实测） |
| §16 | **Concurrency Semantics** | 邀请码一次性：两侧都靠 invite 行 `FOR UPDATE` + username UNIQUE 兜底；实测 8 并发 → 1 成功 ⇒ **SEMANTICALLY EQUIVALENT / IMPLEMENTATION DIFFERENT**（GORM clause vs `LambdaQueryWrapper.last`）。并发 seeder 两侧都靠 UNIQUE + 重读 ⇒ **EQUIVALENT** |
| §17 | **Timeout Semantics** | 服务端 2s 一致。⚠️ scope 有一处未定：是否 min 客户端 deadline（UNRESOLVED-ARPC-002）—— 🔴 **2026-10-26 订正**：该子项已由 `align/global` @ `af416d9` 修（`min(服务端预算, 客户端 deadline 剩余)`）+ 第三十三轮毫秒精度（`d20b377`）⇒ 按推导 `Δ_end` ≈ 0 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】；**机制层**（业务线程不被取消）仍未做、**非 SQL 路径**仍有差（`DRIFT-ARPC-010`）⇒ **未实测**（探针未进编排 ⇒ global **E48**）。⚠️ 在途 SQL 取消时长 4455ms vs 378ms（B-1/E-4，跨组件） |
| §18 | **Retry Semantics** | 全仓唯一的重试是 CreateInviteCode 的 3 次唯一冲突重试；触发条件、次数、退避（无）、最终失败（Internal 同一 desc）全部一致 ⇒ **EQUIVALENT** |
| §19 | **Serialization** | proto wire 逐字节一致（`check-proto-static.sh` EXIT=0，13 rpc）；proto3 无 `optional` ⇒ 无 presence 语义差异；`null` vs `""` vs 缺失三态在 13 个 message 上逐条对齐（`AdminServiceGrpcImpl:265-297`：`toProtoAdmin` + `toProtoUser`）。**唯一差异是 `UpdateUserResp.success`**（DRIFT-ARPC-002）与 `extra_info` 列类型（DRIFT-ARPC-004） |
| §20 | **Configuration** | `AdminAuth.AccessExpire=2592000` ✓；`System.DefaultPassword="123456"` ✓；`AccessSecret` 两侧在 rpc 侧**都未使用**（token 由 admin-api 签发）✓；`DataSource` ✓；`AdminAuth` 的 15 字节 secret 只影响 admin-api（N-5，本组件外）。**唯一分歧是空值容忍**（DRIFT-ARPC-003） |
| §21 | **Lifecycle** | Go：`InitDB(AutoMigrate)` → `ensureDefaultAdmin(logx.Must)` → `MustNewServer` → `AddUnaryInterceptors` → `Start`。RAG：`AdminSchemaBootstrap.@PostConstruct` → `AdminSeederBootstrap.@PostConstruct`（`@DependsOn` 显式）→ gRPC server（`SmartLifecycle.start`）。**次序一致** ✓。差异：建表张数（DRIFT-ARPC-005）+ RAG 多了 `pg_advisory_lock` |
| §26 | **Counterfactual（反事实）** | 6 条新发现里，**DRIFT-ARPC-001（update_time）** 与 **DRIFT-ARPC-002（success）** 命中 §26 的 HIGH-RISK 判据：一个不看 Go 源码、只按 Spring/MyBatis-Plus 惯例写的实现，**必然**会写成 RAG 现在这样（MP 默认不管 autoUpdateTime；proto 成功就填 true）。⇒ **HIGH-RISK DRIFT** |

---

## 7. Semantic Drifts（spec §27 + §36）

> ⚠️ **本节 = 2026-10-23 首轮对照时的**原始发现记录**（第 998 行明示「原始记录一字未删」）**。
> ⇒ 这里的 **Java 行号是当时的快照**，本域源码此后多次改动（`FIX-ARPC-001` 等）⇒ **多数已失效**。
> ⇒ **不要**按本节行号去跳代码；**现行位置**见 §11.8 映射表（§11.8 = 2026-10-27 逐条实测订正）。
> ⇒ 本节的**判定与结论**不因行号失效而失效（结论另有 §1.2 的修复轮承接）—— 失效的只是**定位**。

### DRIFT-ARPC-001 —— `update_time` 永不推进（所有写路径）

```
Component:   admin-rpc（涉及 admin_admin / admin_invite / users_user 三张表）
Operation:   createAdmin(W5) / updateUser / updateSelf / banUser / unBanUser /
             resetUserPassword  —— 共 6 条写路径
Category:    Transaction / State Semantics（写路径副作用）
Risk:        HIGH（§26 反事实：MP 默认不管 autoUpdateTime，不看 Go 源码就会写成这样）

Go Behavior:
  三张表的结构体都带 `gorm:"...;autoUpdateTime"`：
    data_model.go:13  Admin.UpdateTime
    data_model.go:28  AdminInvite.UpdateTime
    data_model.go:45  User.UpdateTime
  消费链（**读到控制流，不是声明行**）：
    gorm@v1.31.1/schema/field.go:304   AUTOUPDATETIME tag → field.AutoUpdateTime
    gorm@v1.31.1/callbacks/update.go:276-295  **struct 形态** Updates()：
        if !stmt.SkipHooks && field.AutoUpdateTime > 0 { isZero = false }
        ⇒ update_time **必然**进 SET，与业务字段是否为零值无关
    gorm@v1.31.1/callbacks/update.go:237-254  **map 形态** Update("status",…) / Update("password",…)：
        if field.AutoUpdateTime > 0 && value[字段]==nil { set = append(set, …update_time=now) }
        ⇒ banUser / unBanUser / resetUserPassword 也推进
  ⇒ Go 侧**任何一次成功 UPDATE 都推进 update_time**。

Java Behavior:
  三个 Entity 都把 update_time 标成永不参与写入：
    AdminEntity.java:65       updateStrategy = FieldStrategy.NEVER
    AdminInviteEntity.java:64  updateStrategy = FieldStrategy.NEVER
    UserEntity.java:71         updateStrategy = FieldStrategy.NEVER
    ⇒ ⚠️ **以上三行是 2026-10-23 的快照 · 行号已失效**（现行：`AdminEntity.java:95` ·
    `AdminInviteEntity.java:96` · `UserEntity.java:163`）⇒ **见 §11.8.2 映射表**
  且所有更新都用 LambdaUpdateWrapper 动态 set（AdminDomainService.java:342,876,880,
  578,610,995；AdminCreateAtomicService.java:174-175），**没有任何一处 set update_time**。
  ⇒ RAG 侧 update_time 自建表起**永不推进**。

Difference:
  同一次 updateUser / banUser 请求落地后，update_time 的值两侧不同
  （Go = 应用时钟的当次写入时刻；RAG = 建表那一刻的 DB 默认值，永不变）。

Observable Impact:
  - 任何 SELECT ... WHERE order_by(update_time) / 「最近更新过」筛选，两侧结果不同。
  - 本轮 grep 全仓：admin-rpc / admin-api / user-rpc 主源码**无人读** users_user.update_time
    或 admin_admin.update_time（唯一读者是 AvatarDomainService.java:71 读
    users_user_avatar_history.create_time，属 user-rpc 且是另一张表）
    ⇒ **当前无 RAG 消费者**，端到端 HTTP 行为暂不可见。
  - 但它同时是一份**写错的审计数据**：管理员改密/封禁的时间戳停在建表时刻。

Why It Matters:
  ① 它是**已修过的同款漂移**在另一模块的漏修：user-rpc 侧的 `user-rpc/.../mapper/UserMapper.java:81-127` 已按
     **完全相同的 Go 出处**修过（DRIFT-001，javadoc 直引 `autoUpdateTime` tag），
     修法是 `wrapper.setSql("update_time = CURRENT_TIMESTAMP")`，
     并有回归测试 `UserRpcJsonTextAndAvatarProjectionTest:113-148` 钉住 SET 子句必须含该列。
     **admin-rpc 没有同步修** —— 这正是「按组件粒度逐个审计」的漏网形态。
  ② DRIFT-001 当时的修法注释写的是「依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发」，
     那条注释已被证伪并改成显式 setSql。admin-rpc 若照抄会引入同一个错。

Classification: OBSERVABLE DIFFERENCE（DB 状态外部可观察；业务判定不依赖它，故不上调 MISMATCH）
Evidence:      静态源码 + GORM 框架源码消费点 + 仓库内同款修复的交叉引用
Must Preserve: 每条成功 UPDATE 都把 update_time 推到当次写入时刻
Recommended:   按 `user-rpc/.../mapper/UserMapper.java:127` 的同款手法，在 6 条写路径的 wrapper 上补
               `setSql("update_time = CURRENT_TIMESTAMP")`，并加一条「SET 子句必须含
               update_time」的守恒断言（照 UserRpcJsonTextAndAvatarProjectionTest 的写法）。
```

> #### ⚠️ 2026-10-26 范围补充：本条**只覆盖 UPDATE 路径**，`create_time` 在 **INSERT 路径**上**不在本条范围内**
>
> 本条的 6 条写路径**全是 UPDATE**。而 **INSERT 路径的时间列是另一回事**，两侧**机制不同**：
>
> | | `create_time` 在 INSERT 时怎么来 | 依据 |
> |---|---|---|
> | **Go** | **GORM 应用层填值** | `data_model.go:12` `Admin.CreateTime` 带 `gorm:"column:create_time;autoCreateTime"` ⇒ `callbacks/create.go` 在 insert 前填 `time.Now()`（**应用容器时钟**） |
> | **RAG** | **DB 层 DEFAULT** | `AdminEntity.java:92` `create_time` 是 `insertStrategy = FieldStrategy.NEVER` ⇒ MP **不写该列** ⇒ 落到 DDL 的 `create_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP`（`03_create_admin.sql:59`）⇒ **DB 服务器时钟** |
>
> ⇒ **两侧都有值，但来源不同**（应用时钟 vs DB 时钟）⇒ 这与本条的 `update_time` **不是同一种严重性**：
> - 本条（`update_time`）：RAG 侧**值冻结在建表那一刻** ⇒ **明显不同**（OBSERVABLE）；
> - 本补充（`create_time`）：两侧都是「插入时自动填当前时间」⇒ 差异只剩**时钟源与时区**偏差
>   ⇒ 同一台宿主机上的容器（应用与 PG 共用同一内核时钟）⇒ **实际不可观察**。
>
> ⚠️ **为什么必须单独写出来**：本条标题写的是「`update_time` 永不推进（**所有写路径**）」，
> 而**所有写路径**这个措辞会让人以为 INSERT 路径也被覆盖了 ⇒ **实际没有**。
> ⇒ 修本条时**不要**顺手把 `create_time` 的 INSERT 路径一起改（那是另一个判断），
> **也别**因为本条被修了就认为 `create_time` 也被证明了。
>
> **关联**：`users_user` 侧（user 域）的 `create_time` 是**另一张表、另一套结论** ——
> user 半边两侧都显式写该列 ⇒ 不可观察（见 `users/_shared.md` ADJ-04；
> **admin 侧对 `users_user` 没有 INSERT** ⇒ 该结论对 admin 侧亦成立 ⇒ 已答，见 `admin/_shared.md` §5 U-7）。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **OBSERVABLE DIFFERENCE · Risk HIGH（2026-10-23 修复轮 §FIX-ARPC-001 已修）** ⇒ **现行态 = ✅ 维持「已修」**（**5 处写路径的 `setSql` 全部在位**）。
> 依据（两侧，当场核过）：**RAG**（5 处落点逐个核过，全是 `setSql("update_time = CURRENT_TIMESTAMP")`）：`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:406`（`updateSelf`）＋ `:687`（`banUser`）＋ `:729`（`unBanUser`）＋ `:1036`（`updateUser`）＋ `:1191`（`resetUserPassword`）⇒ **「RAG 侧不推进」这半边已消失** · **Go** `service/user/admin/rpc/internal/model/data_model.go:12-13` / `:27-28` / `:44-45`（`gorm:"column:update_time;autoUpdateTime"` ⇒ **声明点**）＋ 消费点 `gorm@v1.31.2/callbacks/update.go:243-259` / `:279-281` ⇒ **Go 侧仍无条件推进**。⇒ **两侧现在同形 ⇒ 本条已闭合**。⚠️ **本条标题写「所有写路径」而正文限定「全是 UPDATE」**（INSERT 路径的 `create_time` 是另一套结论）⇒ **该措辞边界仍按原样保留**。⚠️ **本轮未复跑真 PG 实测** ⇒ 按 AGENTS.md §0 读作「在该证据强度下一致」。核对日期 2026-10-30。

### DRIFT-ARPC-002 —— `UpdateUserResp.success` 在 wire 上两侧相反

```
Component:   admin-rpc
Operation:   UpdateUser（仅此一个）
Category:    Serialization / Error Identity（Result 语义）
Risk:        HIGH（§26 反事实：proto 成功就填 true 是本能写法）

Go Behavior:
  proto/admin.proto:119-122  UpdateUserResp { bool success = 1; UserInfo user = 2; }
  updateuserlogic.go:105-113  return &pb.UpdateUserResp{ User: … }  ← **没有 Success 字段**
  ⇒ 成功时 wire 上 success = **false**（proto3 默认值，不占字节）
  ⇒ 对照：Go 另外 7 个带 success 的 Resp（Logout/Delete/Reset/Ban/UnBan/UpdateSelf）
    都显式写了 Success: true，**只有 UpdateUser 漏写**。

Java Behavior:
  AdminServiceGrpcImpl.java:219  ← ⚠️ **历史值·已失效**（2026-10-23 快照）
    return UpdateUserResp.newBuilder().setSuccess(true).setUser(toProtoUser(user)).build();
    ⇒ ⚠️ **这行代码已被删除**（`FIX-ARPC-002` 去掉了 `setSuccess(true)`）
    ⇒ **现行**：构造点在 `AdminServiceGrpcImpl.java:234`；`DRIFT-ARPC-002` 的修法见 §1.2
  ⇒ 成功时 wire 上 success = **true**

Difference:
  直接调用 admin-rpc gRPC 的客户端，在成功路径上读到 success=true（RAG）/ false（Go）。

Observable Impact:
  - **admin-api 不读它**：AdminUpdateUserService.java:93 硬编码 `resp.setSuccess(true)`
    （Go 侧 admin/api/internal/logic/admin/updateuserlogic.go 末尾同样硬编码 `Success: true`）
    ⇒ 端到端 HTTP 响应两侧都是 `{"success":true,...}`，**不受影响**。
  - 受影响的是 §3.5 里那条尚未审的跨组件不变量「client 契约」：任何绕过 admin-api
    直连 admin-rpc 的调用方（未来的运维工具 / 灰度双写探针）都会看到不同值。

Business Impact:
  低（当前无消费者）；但它是 wire 契约不一致，属 §3.1 明确要求覆盖的「proto 字段语义」项。

Why It Matters:
  这是 §1.1 那 11 处历史回归里「`success` 字段语义反转」的**镜像形态** —— 同一个字段，
  上一次是 RAG 少填（api 层把它透传成 false），这一次是 Go 少填、RAG 多填。
  按 §1 政策「出现行为差异时默认判定 RAG 错」，本条需**用户签字**才能保留 RAG 现状。

Classification: OBSERVABLE DIFFERENCE
Evidence:      静态源码 + proto 定义行号（两侧逐字）
Must Preserve: 决策要显式化：要么签字「RAG 修掉了 Go 的漏写」，要么把 RAG 改成 false
Recommended:   交用户裁决 + 补一条断言该字段的回归测试（当前**零测试**覆盖它）
```
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **OBSERVABLE DIFFERENCE · Risk HIGH（2026-10-23 修复轮 §2 已修 ⇒ 对齐 Go）** ⇒ **现行态 = ✅ 维持「已修」**（**RAG 侧已不再 `setSuccess(true)`**）。
> 依据（两侧，当场核过）：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminServiceGrpcImpl.java:219`（`// 🔴 2026-10-23：去掉 setSuccess(true)，对齐 Go（DRIFT-ARPC-002）。` ⇒ **注释与修法在位**）＋ `:234` = `return UpdateUserResp.newBuilder().setUser(toProtoUser(user)).build();`（**构造点** ⇒ **只 set 了 `user`，没 set `success`**）· **Go** `service/user/admin/rpc/internal/logic/updateuserlogic.go:105-113`（`return &pb.UpdateUserResp{ User: … }` ⇒ **也没 set `Success`**）⇒ **两侧 wire 一致（`success` 都是 proto3 默认 `false`）**。⇒ **HTTP 面仍两侧都是 `{"success":true}`**（api 层硬编码，RAG `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/AdminUpdateUserService.java:99` = `resp.setSuccess(true);`）⇒ **原判「HTTP 面不受影响」仍成立**。核对日期 2026-10-30。

### DRIFT-ARPC-003 —— `system.default-password` 为空时行为分歧

```
Component:   admin-rpc / ResetUserPassword
Category:    Configuration + Error Semantics
Risk:        LOW（需显式把配置项置空才触发）

Go:    resetuserpasswordlogic.go:56  cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)
       cryptx/password.go:8          bcrypt.GenerateFromPassword([]byte(""), DefaultCost)
       ⇒ 空串**合法**，Go 会把「空密码的哈希」写进 users_user，并返回 Success=true

Java:  AdminDomainService.java:976-979  ← ⚠️ **历史值·已失效**（2026-10-23 快照）
       ⇒ **现行**：`AdminDomainService.java:1165-1171`（`doResetUserPassword` 的 W3 段）
       if (defaultPassword == null || defaultPassword.isEmpty()) {
           metrics…; throw Status.INTERNAL.withDescription("密码加密失败")… }
       ⇒ 直接 Internal（api 层 5001），且 update_time / password 都不动

Default 配置下不可达：Go etc/admin.yaml:41 DefaultPassword="123456"；
RAG application.yml:80 ${ADMIN_DEFAULT_PASSWORD:123456}。
但 `ADMIN_DEFAULT_PASSWORD=""` 这个环境变量会让 Spring 绑到空串（占位符默认值只在未设置时生效）。

Classification: OBSERVABLE DIFFERENCE（配置触发）
Evidence:      静态源码 + Go bcrypt 语义（空密码合法）
Must Preserve: 决策显式化 —— 这是 RAG 多出来的一道保护，属 §3.2「无出处 idiomatization」，
               需要么补 Go 出处、要么删掉
Recommended:   保留该守卫但补 §3.2 javadoc 标注「Go 侧无此校验，这是有意收紧」，
               或直接删掉以逐字对齐 Go
```
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **OBSERVABLE DIFFERENCE · Risk LOW（已签字 `SD-2026-10-23-06`）** ⇒ **现行态 = ✅ 维持「✅ 仍成立（签字有效）」**（**RAG 侧的守卫仍在**）。
> 依据（两侧，当场核过）：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:1150`（`String defaultPassword = properties.getSystem().getDefaultPassword();`）＋ `:1151`（`if (defaultPassword == null || defaultPassword.isEmpty()) {` ⇒ ⇒ 抛 INTERNAL「密码加密失败」）⇒ **「RAG 多一道守卫」这半边仍在** · **Go** `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56`（**无空值判断** ⇒ 空串交给 bcrypt ⇒ 成功）⇒ **「Go 无此守卫」这半边仍在**。⇒ **差异确实存在 ⇒ 已签字 ⇒ 是决策不是缺陷**。核对日期 2026-10-30。

### DRIFT-ARPC-004 —— DDL 列类型：时间列 `timestamptz` vs `timestamp`、`extra_info` `text` vs `jsonb`

```
Component:   admin-rpc / 全部持久化路径
Category:    Configuration（表结构）+ Serialization（extra_info）
Risk:        MEDIUM

Go（真 PG 实测，go_db 由 GORM AutoMigrate 建）：
  SELECT table_name, column_name, data_type FROM information_schema.columns …
    admin_invites.expires_at  | timestamp with time zone
    admin_invites.used_at     | timestamp with time zone
    admin_invites.create_time | timestamp with time zone
    admin_invites.update_time | timestamp with time zone
    admins.create_time        | timestamp with time zone
    admins.update_time        | timestamp with time zone
    admins.extra_info         | text
RAG（rag_db 由 users-common/db/*.sql 建）：
    admin_invite.expires_at / used_at / create_time / update_time | timestamp without time zone
    admin_admin.create_time / update_time                        | timestamp without time zone
    admin_admin.extra_info                                      | jsonb

Business Impact 分三条：

① extra_info（jsonb vs text）
   Go：`serializer:json` 把 map 序列化后写入 text 列。
   RAG：写入 jsonb 列（JsonbTypeHandler.java:59 用 ps.setObject(..., Types.OTHER)）。
   ⚠️ **历史值·已失效**（2026-10-23 快照）：`JsonbTypeHandler.java` **已整体删除**（global B10，`ff80214`，2026-10-25）
   ⇒ **现行**：`AdminEntity.extra_info` 走 `JsonTextTypeHandler`（`ps.setString`，text 绑定）
   ⇒ 现行调用点：`AdminDomainService.java:403`（updateSelf）/ `:1033`（updateUser）
   ⇒ **本条（jsonb vs text）已于 2026-10-26 闭合**，见 §4 表「Schema · extra_info 列类型」行
   边界后果：值里含 `\u0000` 或非法 UTF-8 字节时，PG jsonb 拒收（22P02 / unsupported
   Unicode escape sequence），Go 的 text 列照收 ⇒ Go 成功、RAG 5001。
   ⚠️ 未实测（本轮未跑该边界），记 UNRESOLVED-ARPC-004 的验证步骤。

② expires_at / used_at（无时区）
   Go：timestamptz + time.Time ⇒ 绝对时刻比较，**与时区无关**。
   RAG：timestamp(无时区) + LocalDateTime ⇒ **墙钟比较**。
   RAG 侧写入（AdminDomainService.java:509-510）与读取比较（AdminCreateAtomicService.java:124-127）
   都在同一 JVM 的同一时区里取「本地墙钟」，因此**单一时区部署内判定结果完全一致**
   ⇒ 邀请码 10 分钟有效期规则本身 EQUIVALENT。
   ⚠️ 但前置条件是「同一部署内所有 admin-rpc 实例 JVM 时区一致」；跨时区多实例、或
   该行曾被另一时区的实例写过，判定就会偏一个时区差。与 DRIFT-MSG-V-007（message 域时间列）
   同型，AGENTS.md §0 第 2 类未关闭项之一。

③ create_time / update_time
   Go：应用时钟写入（GORM autoCreateTime / autoUpdateTime）。
   RAG：列被标 NEVER 不参与写入，落 DB 默认值 `CURRENT_TIMESTAMP`；由于列是**无时区**类型，
   PG 取的是**数据库服务器本地时区**的墙钟。
   ⇒ 若 JVM 时区 ≠ PG 服务器时区，两侧 create_time 相差一个时区差。
   本组件无人读这两列（见 DRIFT-ARPC-001 的影响分析），故当前不可观测。

Classification: OBSERVABLE DIFFERENCE（12 处中的 7 处属本组件；另 5 处属 users_user /
             users_user_avatar_history，需与 user-rpc 台账合并判读）
Evidence:      真 PG schema 对照（`tools/difftest/check-ddl-static.py` + 直接 psql 查询，
               同一 PG 实例内 go_db / rag_db 并列）
Must Preserve: ① 的 jsonb 边界行为、② 的有效期判定、③ 的时钟来源，逐条由用户裁决
Recommended:   时间列统一改成 `timestamptz`（比改 Java 侧改成 timestamptz 成本低，
               因为 Java 侧已经全部用 LocalDateTime，改 DDL 一处即可让两侧语义同构）
```
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **OBSERVABLE DIFFERENCE · Risk MEDIUM（2026-10-23 修复轮 §1 已修 ①②）** ⇒ **现行态 = ✅ 维持「已修」**（**① `extra_info` 已是 `TEXT`；② 时间列已是 `timestamptz`**）。
> 依据（两侧，当场核过）：**RAG ①** `service/users/users-common/src/main/resources/db/03_create_admin.sql:58` = `extra_info  TEXT,`（**DDL 消费点**）＋ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/AdminEntity.java:89` = `@TableField(value = "extra_info", typeHandler = com.rag.users.admin.rpc.mapper.JsonTextTypeHandler.class)` ⇒ **类型处理器已换成写 JSON 串**（`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/mapper/JsonTextTypeHandler.java` ✅ **在位**）＋ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:394`（`// 🔴 2026-10-23：**去掉了 CAST(... AS jsonb)**（DRIFT-ARPC-004①）。`）· **Go ①** `service/user/admin/rpc/internal/model/data_model.go:11`（`ExtraInfo map[string]string `gorm:"column:extra_info;serializer:json"``）⇒ **text 列** · **RAG ②** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/bootstrap/AdminSchemaBootstrap.java:55` = `SchemaInitializer.applyAll(connection, "admin-rpc");` ⇒ **DDL 由幂等重放器执行 ⇒ 时间列已是 `timestamptz`** · **Go ②** `service/user/admin/rpc/internal/model/data_model.go:12-13` / `:27-28`（`time.Time` 无 type tag ⇒ postgres driver 生成 `timestamptz`）。⇒ **两侧已同形 ⇒ 本条已闭合**。核对日期 2026-10-30。

### DRIFT-ARPC-005 —— 启动期建表覆盖面：Go 3 张 vs RAG 4 张

```
Component:   admin-rpc / Lifecycle
Category:    Lifecycle Side Effect
Risk:        LOW

Go: model/db.go:15   db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})
    ⇒ 建 3 张：admins / admin_invites / users
RAG: SchemaInitializer.java:63-68  SCRIPTS = {01_create_user, 02_create_user_avatar_history,
      03_create_admin, 04_create_admin_invite}
    ⇒ 建 4 张：**多建 users_user_avatar_history**，Go admin-rpc 不建这张表

Observable Impact:
  - admin-rpc 单进程在空库上启动后，PG 里会多出一张 Go 侧不存在的表。
  - 该表由 user-rpc 正常使用，admin-rpc 多建它不会破坏任何东西（全部 CREATE … IF NOT EXISTS）。
  - 但它让「admin-rpc 能单独起一个完整 users 域」的印象成立 —— 与 Go 的能力边界不同。

Why It Matters:
  这是 SchemaInitializer「一套脚本给两个进程共用」的设计代价，属可接受的实现差异，
  但必须台账化：日后若有人按「Go 建 3 张」去核对 DDL 覆盖面，会误以为少了一张。

Classification: OBSERVABLE DIFFERENCE（低风险）
Evidence:      静态源码两侧逐行
Recommended:   在 AdminSchemaBootstrap 的 javadoc 里明确写「本模块比 Go 多重放
               02_create_user_avatar_history.sql，是共用脚本集的设计结果」
```
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **已关闭为「IMPLEMENTATION DIFFERENCE，不是缺陷」** ⇒ **现行态 = ✅ 维持「已关闭（非缺陷）」**（**RAG 侧仍重放全部 4 份**）。
> 依据（两侧，当场核过）：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/bootstrap/AdminSchemaBootstrap.java:55` = `SchemaInitializer.applyAll(connection, "admin-rpc");` ⇒ **重放的是 users 域全套 DDL（含 `users_user_avatar_history`）⇒ 4 张** · **Go** `service/user/admin/rpc/internal/model/db.go:15` = `err = db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` ⇒ **3 张**。⇒ **「3 vs 4」这个计数差仍在** ⇒ **按原判：实现差异，不是缺陷**（全部 `CREATE … IF NOT EXISTS` ⇒ 多建一张不破坏任何东西）。核对日期 2026-10-30。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/run.sh` ⇒ **EXIT=0**（2026-10-30 03:29:45 · 137s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」） ⏐ PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ==> 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

### DRIFT-ARPC-006 —— 无 gRPC span：Go 逐 Logic 手写 `tracer.Start`，RAG 侧业务方法无 span 属性 —— ✅ **已修（2026-10-23 实施轮 §2 · 8 处 span）**

> 🟢 **2026-10-29 收敛订正（同一编号只有一种状态）**：本条标题**原文不带状态**，而同一文件 `:1377` / `:1588` 两处状态表均记 **「✅ 已修（8 处，非 13 处）」**，`vs-common.md:35` 亦记 **✅ 已修** ⇒ **本轮以「已修」为准**并把状态**写进标题**（原文一律保留）。
> ⚠️ **配套订正**：同文件 `:1871` 那行「ARPC-006 零手写 span │ 需 `service/common` 的 OTel 装配，不在本域」是**首轮快照、已被推翻** —— 实施轮已改为**在 `admin-rpc` 内**补齐（`admin-rpc` 自己的 `pom.xml` 已引 `opentelemetry-bom`，`GlobalOpenTelemetry.getTracer` 与 Go `otel.Tracer` 同一套 API）⇒ **不动 `service/common`**。

```
Component:   admin-rpc / 全部 13 个用例（可观测性副作用）
Category:    External Interaction（Telemetry）
Risk:        LOW-MEDIUM

Go: 7 个 logic 文件在方法入口显式开 span 并打业务属性：
      createadminlogic.go:40-46   "Action-Admin-CreateAdmin" + audit.admin_username
      updateuserlogic.go:36-42    "Action-Admin-UpdateUser" + audit.operator_id / audit.target_uid
      createinvitecodelogic.go:45-48  + audit.operator_id
      banuserlogic.go:35-41 / unbanuserlogic.go:35-41 / deleteuserlogic.go:35-41
      resetuserpasswordlogic.go:36-42
    ⇒ 这些 span 属性是**唯一**能观测 N-4（operator_id 恒 0 vs 真实 uid）的地方。

RAG: AdminServiceGrpcImpl 与 AdminDomainService **零手写 span**；
     只有 service/common 的 RpcInterceptor 会开一个 method 名的 server span
     （`RpcInterceptor` 里 `tracer.startSpan` 那一处），**不含 audit.* 属性**。<br>     ⚠️ 原写 `RpcInterceptor.java:162-165`，2026-10-26 改为**符号名** —— `service/common` 正被 `align/global` 持续改动，行号会漂。
     AdminDomainService 字段里只有 log + metrics（类注释 :57-58 明确说明「admin 侧没有 span 上下文」）。

Observable Impact:
  - `audit.operator_id` / `audit.target_uid` / `audit.admin_username` 三个属性在 RAG 侧**不存在**。
  - N-4 签字条目写的是「该字段只写进 OTel span 的 audit.operator_id 属性；恒 0 会让审计日志
    失去操作人信息」—— 现状是 RAG 侧**一个 audit 属性都没有**，比签字时假想的「恒 0」更弱。
  - 这直接削弱 §6.3 已签字偏离 N-4 的原始理由（可观测性数据）。

Classification: OBSERVABLE DIFFERENCE
Evidence:      静态源码两侧逐行（Go 7 处 tracer.Start vs RAG 0 处）
Recommended:   要么在 AdminDomainService 补 OTel span（对齐 Go 的 audit.* 属性），
               要么更新 N-4 的签字文本，把「RAG 侧无 audit span」写进去
```

---
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡（2026-10-23 实施轮 §2 已修 · 8 处 span）** ⇒ **现行态 = ✅ 维持「已修」**（**`audit.*` 属性与 `audit.operator_id` 常量在位**）。
> 依据（两侧，当场核过）：**RAG** `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminSpans.java:103` = `public static final String ATTR_OPERATOR_ID = "audit.operator_id";`（**属性常量在位**；同文件 `:26`-`:40` 的 javadoc 表逐条列出 8 个 logic 的 `audit.*` 属性）· **Go** `service/user/admin/rpc/internal/logic/`（**逐 Logic 手写 `tracer.Start`** ⇒ 8 个 logic）⇒ **两侧都产出 `audit.*` 属性** ⇒ **「RAG 侧零 span」这半边已消失**。⇒ **本条已闭合**（⚠️ **计数订正保留**：Go 是 **8 个 logic** 有 span，不是 13 —— 13 是 attribute 条数）。核对日期 2026-10-30。

## 8. Test Cases（spec §29 + §30）

> 下面这些是**本轮实跑**（2026-10-23，`mvn -pl service/users/admin/admin-rpc -am -o test`）的
> RAG 侧行为记录。⚠️ **全部是单侧** —— Go 侧未同时起，按硬约束 3 它们不是对照证据。

| CASE | 输入 / 环境 | Go（源码推导，未实跑） | RAG（实跑观察） | Equivalent |
|---|---|---|---|---|
| CASE-01 正常成功 | 有效 invite + 正常密码 | 建号成功返回 uid | 5 条 R5 测试全绿，含「密码列存的是 bcrypt hash」 | YES（单侧） |
| CASE-02 空输入 | inviteCode=" " | PermissionDenied | PERMISSION_DENIED | YES |
| CASE-02b 空输入 | username=" "（空串） | 照常进事务并建号 | 照常进事务（DRIFT-008 测试） | YES |
| CASE-03 不存在 | GetUser(不存在的 uid) | NotFound | NOT_FOUND（`AdminDomainServiceDeadlineTest`） | YES |
| CASE-04 重复 | 同一 inviteCode 消费第 2 次 | PermissionDenied | InviteUnavailableException（R5-C2） | YES |
| CASE-04b 重复 | 同 username 并发 8 路 | UNIQUE 兜底，1 行 | 1 OK + 7 失败，最终 1 行 | YES |
| CASE-05 外部依赖失败 | Redis 写黑名单失败 | Internal「注销失败,Redis写入异常」 | 静态一致，**未实跑**（无 Redis 容器测试） | UNKNOWN |
| CASE-06 超时 | deadline 已过期 | 拦截器 DEADLINE_EXCEEDED | `AdminDeadlinePassthroughTest` 8 条 + `checkDeadline` 2 条 | YES（单侧） |
| CASE-07 Retry | CreateInviteCode 连续 3 次唯一冲突 | Internal「管理员邀请码生成失败」 | 静态一致，未构造该场景 | UNKNOWN |
| CASE-08/09 Cache | — | 本组件无缓存 | N/A | N/A |
| CASE-10 权限失败 | 无 operator 校验 | 不鉴权 | 不鉴权 | YES |
| CASE-11 并发调用 | 8 线程抢同一 invite | 行级锁串行化 | **1 OK + 7 REJ，0 OTH，invite.used_by_uid 唯一** | YES |
| CASE-12 重复调用 | seeder 跑两次 | 幂等 | 「重复执行 seeder 不产生重复账号」绿 | YES |
| CASE-13 部分操作成功 | W3 失败后 W4 是否已跑 | 不允许 | invite 的 used_at/used_by_uid 仍为 NULL | YES |
| CASE-14 Transaction Rollback | W2 username 冲突 | 全回滚 | 全回滚（4 条 rollback 测试） | YES |
| CASE-15 Serialization Edge | extra_info 含 NUL / 超长 username | Go 写成功 | **未测** | UNKNOWN |
| CASE-15b 序列化边界 | page=0 / page=-1 / page_size=-5 / page=int64max | GORM 抑制 → 200 | 7 条真 PG 测试全绿，SQL 形状与 Go 一致 | YES |
| CASE-16 边界输入 | system.default-password="" | 设空密码哈希，Success=true | INTERNAL 5001 | **NO**（DRIFT-ARPC-003） |
| CASE-17 外部状态 | updateUser 后读 update_time | = 当次写入时刻 | = 建表时刻（不变） | **NO**（DRIFT-ARPC-001） |
| CASE-18 外部状态 | updateUser 响应体 success | false | true | **NO**（DRIFT-ARPC-002） |

---

## 9. Unresolved（spec §37）

> §37 要求此节**不得为空**。

### UNRESOLVED-ARPC-001 —— 本轮没有任何双侧活体对拍证据

```
Question:   admin-rpc 的 13 个 RPC 在两侧同时跑、同一输入、同一依赖时是否逐条等价？
Go Evidence:    Go 源码全部读过（§2），proto wire 逐字节一致（check-proto-static.sh EXIT=0），
                DDL 列类型在真 PG 上并列读过（check-ddl-static.py）
Java Evidence:  真 PG 容器测试 29 条绿；其余 50 条是 Mockito / 反射结构断言
Why Unknown:    §6.2.3 A-U2 记的「39 步对拍」是 **admin-api 的 HTTP wire 层**，
                不是 admin-rpc 的 gRPC 层；本轮也没有补 gRPC 层对拍。
                硬约束 3：只跑 RAG 侧的探针不算数。
Impact:         本台账所有 EQUIVALENT 判定的强度上限是「单侧真 PG」/「静态源码 + 框架消费点」，
                **不能**被读成「已闭环」。
Required Confirm: 跑 tools/difftest/exp-difftest-grpc-errors.sh（该脚本此前只在 message 域跑过），
                对 admin 的 13 个 rpc 补一轮双侧 gRPC 对拍。
```

> ✅ **2026-10-23 22:4x「台账 vs 实际」复核：本条已关闭（本条长期未销号，属台账病灶）。**
>
> **关闭依据**：
> 1. 「2026-10-23 审计轮 §0」已实跑 `tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up`
>    ⇒ **18 个用例**的 gRPC 层双侧活体对拍，`cases=18 / code_mismatch=0 / payload_diff=0`；
> 2. 「修复轮 §7.3」复跑同一脚本，数字**逐项相同**。
>
> **⚠️ 效力边界（关闭 ≠ 全覆盖）**：那 18 个用例**只覆盖错误路径**
> （`*.notfound` / 空 invite / 坏 invite），成功路径只有 `login.ok` / `logout.ok` /
> `getself.ok` / `getuserlist.empty`。⇒ 结论应读作
> 「**13 个 RPC 的错误路径已双侧活体对拍**」，不是「13 个 RPC 全部闭环」。
> 成功路径的已知缺口见「修复轮 §7.4」（`updateuser.ok` 缺失）。
>
> **本条长期挂着不销号这件事本身是个病灶**：Required Confirm 写的是
> 「跑 `exp-difftest-grpc-errors.sh`」—— 而审计轮**已经跑了**（用的是新增的
> `-admin-` 变体），却没人回来把本条销掉。
> ⇒ 与 AGENTS.md §6.3「状态表纪律」同源：**未核对 ⇒ 按 UNKNOWN 处理**，
> 而 UNKNOWN 条目不销号，下一轮 agent 会重复投入。
> **本轮起：每轮结束必须回头销 UNRESOLVED。**
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已关闭（2026-10-23 22:4x · `exp-difftest-grpc-admin-errors.sh` 18 用例）** ⇒ **现行态 = ✅ 维持已关闭**（**对拍脚本与执行体都还在**）。
> 依据（当场核过）：**RAG/工具** `tools/difftest/exp-difftest-grpc-admin-errors.sh` ✅ **存在**（**编排脚本**）＋ `tools/difftest/GrpcAdminDualPortRunner.java` ✅ **存在**（**双端口执行体**）。⚠️ **效力边界仍在**：那 18 个用例**只覆盖错误路径**（成功路径只有 `login.ok` / `logout.ok` / `getself.ok` / `getuserlist.empty`）⇒ **「13 个 RPC 的错误路径已双侧活体对拍」成立，「13 个 RPC 全部闭环」不成立**。⇒ **本条维持关闭**（⚠️ **本轮未复跑该脚本** ⇒ 数字 `cases=18 / code_mismatch=0 / payload_diff=0` 沿用上一轮 ⇒ 按 §0 读作「在该证据强度下一致」）。核对日期 2026-10-30。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-admin-errors.sh` ⇒ **EXIT=0**（2026-10-30 04:30:58 · 6s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [1/4] 编译 admin-proto 桩 + 计算 classpath ⏐ ==> [2/4] 编译 GrpcAdminDualPortRunner ⏐ ==> [3/4] 种 probe user（go_db.users / rag_db.users_user） ⏐ ==> [4/4] 跑实验(写入 grpc-admin-results.txt) ⏐ code_mismatch>0 ⇒ 语义级差异，exit 1 ⏐ des；<br>`tools/difftest/exp-difftest-grpc-admin-errors.sh` ⇒ **EXIT=0**（2026-10-30 04:30:58 · 6s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [1/4] 编译 admin-proto 桩 + 计算 classpath ⏐ ==> [2/4] 编译 GrpcAdminDualPortRunner ⏐ ==> [3/4] 种 probe user（go_db.users / rag_db.users_user） ⏐ ==> [4/4] 跑实验(写入 grpc-admin-results.txt) ⏐ code_mismatch>0 ⇒ 语义级差异，exit 1 ⏐ des。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

### UNRESOLVED-ARPC-002 —— 超时是否 min 客户端 deadline

```
Question:   客户端带一个比 2s 更短的 grpc-timeout 时，两侧是否都按客户端的更早时刻返回？
Go Evidence:    service/common/observability/rpc.go:38
                timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
                ⇒ 父 ctx（已含客户端 deadline）**被继承**，客户端更早 ⇒ 提前取消
Java Evidence:  service/common 的 RpcInterceptor —— deadlineEpochMs 的计算：
                deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()
                ⇒ **只算服务端自己的 2s**，没有读 Context.current().getDeadline()
Why Unknown:    语义上确有差异，但「客户端 deadline 是否被 gRPC 库单独处理到 handler 之前」
                需要双侧活体对拍才能定性；本轮无。
Impact:         短 deadline 客户端 + 慢 handler 的组合下，两侧 handler 的实际运行时长可能不同。
Required Confirm: 用 exp-difftest-grpc-errors.sh 对同一用例分别用 100ms / 2000ms / 5000ms
                三档客户端 deadline 打两侧 grpc 端点，比对响应时刻与 handler 副作用。
```

> ⚠️ **行号引用已改为符号名**（2026-10-26）：原写 `RpcInterceptor.java:170-172` ——
> `service/common` 正被 `align/global` 持续改动（该文件在 2026-10-26 已从 170 行漂到 214 行）
> ⇒ **行号会随别人改动而失效** ⇒ 改引**表达式 / 符号名**（AGENTS.md §3.1：证据指向**消费点**）。

> #### ⚠️ 2026-10-26 销账状态：**只销了「一格 desc 文案」，**本条本体**未销**
>
> | | 状态 |
> |---|---|
> | **档位 B 的 desc 文案**（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`） | ✅ **已由 `align/global` 修掉**（`RpcInterceptor` 换用 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`；配套真 Netty 守门 `RpcInterceptorTimeoutDescriptionGateTest` + `tools/semantic-probe/rpc-timeout/` 双侧活体探针）<br>**状态（2026-10-26 实查）**：修复**已提交**（`align/global` = `22a9111`，含 `49808ad` 主改动 + `22a9111` 台账订正），且**已推 `origin` + `upstream` 双远端**（三处 ref 指向一致，实查过）⇒ **不会再丢**。<br>⚠️ **但尚未合回 `do-my-own-gh`** ⇒ 本条在本域台账里**仍记「待合回」**，**合回后才改成「已销账」**。<br>📌 **合回与否已拍板（2026-10-26，用户 Jason）：暂不合回**（`align/global` 与 `align/favorite` 都暂不并入主仓）⇒ 维持「待合回」**不是「忘了合」** |
> | **本条本体**（服务端 2s **不继承**客户端 deadline） | 🟡 **拆成三层**：① desc 文案 ✅已修（`22a9111`）· ② 期限计算 ✅已修（`af416d9`）· ③ 资源取消 🟡**机制层未做，但可观察面已被 ② + 第三十三轮（`d20b377`）消掉**（详见下方「三层拆分」与 §1.3 的销账补记）<br>⇒ 🔴 **2026-10-26 复核后改判**：原写「整条定性不变：CONFIRMED SEMANTIC MISMATCH 仍成立（可观察差距 `Δ_end = +1111ms` 来自未修的第 ③ 层）」⇒ **这句错了** —— `Δ_end` 由 **PG 取消时刻**决定（即 `statement_timeout`），**与业务线程是否被取消无关** ⇒ 第 ③ 层未修**拦不住**它归零<br>⇒ **现状态**：子项「是否 min 客户端 deadline」✅ 已闭环；**原观察依据按推导已失效**（`Δ_end` ≈ 0） 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上观察依据已归零**（`align/global` 上实测 **22/18/12ms**）⇒ **这句现在成立**（合回前实测 pre-E62 为 `Δ_end=+833ms`）⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ global **E48**）】；**机制层仍未做**（框架限制）；**非 SQL 路径仍有差**（⇒ `DRIFT-ARPC-010`）<br>⚠️ **推导未实测**（探针未进编排 ⇒ global **E48**）⇒ **不得记「已闭环」**<br>⇒ 已由 global 登记为 **E38**（`docs/DRIFT/global/_README.md` §七之五，**A 类 backlog**，落点在 global 写权内 ⇒ 不需拍板） |
>
> ⇒ **修文案 ≠ 修继承**：改完后**档位 A 的 `Δ_end` 仍是 +1111ms**（🔴 **此句为 2026-10-26 修前状态**；现按第 ② 层 + 第三十三轮推导 ≈ 0，**待实测**）。
> ⇒ ⚠️ **不得以「desc 对齐了」把本条判为已闭环** —— 修法落点仍是 `service/common` 的 `RpcInterceptor`
> （`service/common` 单写权归 `align/global`，本域无权改）。
>
> > #### 🟡 2026-10-26 更新：**本条已拆成三层，第 ② 层（期限计算）也修了**
> >
> > | 层 | 内容 | 状态 |
> > |---|---|---|
> > | ① | 超时响应的 **description 文案** | ✅ 已修（`22a9111`，**2026-10-26 已合回主干**） |
> > | ② | **期限计算**（本条原问题本体：服务端预算 min 客户端 deadline） | ✅ **已修**（`af416d9`，**2026-10-26 已合回主干**）—— `effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`，两处消费点都用它；取值点 `io.grpc.Context.current().getDeadline()` 服务端**实测可用**（客户端 1s ⇒ 读到 932ms 剩余） |
> > | ③ | **资源取消**（客户端放弃后 PG 查询仍在跑 ⇒ `Δ_end = +1111ms`） | 🟡 **机制层未做 · 可观察面已消** —— ❌ grpc-java 的 `ServerCall.close()` **不取消业务线程**（与 T-14 同根因）⇒ 真正对齐需「handler 挪受控 executor + 超时 interrupt」⇒ **架构级 · 影响 10 个 rpc 组件**<br>⇒ **Jason 2026-10-26 已拍板（经 global 域转述）：① 本轮做，② 另立项**<br>🔴 **E46 对第③层有两处实测修正**（global 第三十二轮末，comment 域 owner 触发）：**量级被高估**（SQL 侧已有 B-1 缓解 ⇒ 真剩 ≤1s 窗口）· **「挪受控 executor + interrupt」单独做不够**（pgjdbc 不响应线程中断 ⇒ 必须配「取消正在跑的 JDBC 语句」）⇒ E46 **已从 E38② 拆出独立成条**，**第三十三轮已落地**（`d20b377`）<br>🟢 **第三十三轮（`d20b377`）对本层的额外收益**（🔴 本表原先没有）：**毫秒精度**（`setQueryTimeoutMs`）⇒ **取消时刻 == deadline** ⇒ **`Δ_end` 由第 ② 层后的「≈ +80ms」再压到「≈ 0」** ⇒ **第 ③ 层「未修」并不阻止 `Δ_end` 归零**（口径纠正见 §1.3） 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】 |
> >
> > ⇒ **子项「是否 min 客户端 deadline」已答「是」且已修** ✅；
> > 🔴 **整条定性已改判**（2026-10-26 复核）—— 原文写「定性不变（第 ③ 层未修 ⇒ 可观察差距仍在）」⇒ **错**：
> > **`Δ_end` 测的是「SQL 超时上限有没有继承客户端 deadline」，不是「业务线程有没有被取消」** ⇒ ② + 第三十三轮已把它压到 ≈0（**推导，未实测**）。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判「子项『是否 min 客户端 deadline』✅ 已闭环 / ② 期限计算 ✅ 已修（`af416d9`，已合回主干）」⇒ **现行态 = ✅ 维持**；
> ⚠️ **但上表 ② 层记的符号名在当前代码里已全部不存在 ⇒ 属于「指针能解析、但记的不是现行写法」（E68 同型）。**
> - **实测（2026-10-29 逐行核过）**：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
>   **不存在** `effectiveTimeoutMs` / `serverBudgetMs` / `clientDeadlineRemainingMs()` 这三个符号
>   ⇒ 「`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`」这一行**记的不是现行代码**。
> - **现行写法**（单位是**纳秒**）：
>   - `:291-292` `long serverBudgetNanos = (timeout != null && timeout.compareTo(Duration.ZERO) > 0) ? toNanosSaturated(timeout) : Long.MAX_VALUE;`
>   - `:293` `long effectiveTimeoutNanos = Math.min(serverBudgetNanos, clientDeadlineRemainingNanos());`  ← **这一行就是「min」的现行实现**
>   - `:319` `long timeoutNanos = effectiveTimeoutNanos;`（超时调度器延时用**它** ⇒ 两处消费点都改到了，与本表 ② 层描述一致）
>   - `:471` / `:476` `if (effectiveTimeoutNanos == Long.MAX_VALUE) { … DeadlineState.bindRemainingNanos(effectiveTimeoutNanos) … }`
>   - `:707` `private static long clientDeadlineRemainingNanos() {`（取值点 `:710` `io.grpc.Context.current().getDeadline()`）
> - ⇒ **语义（min(服务端预算, 客户端 deadline 剩余)）仍然成立** ⇒ **本条状态不改判**；
>   **订正方式 = 保留上表原符号名与原行号，以本块为现行写法**。
> - ⚠️ **另注**：本表 ② 层末尾引的「取值点 `io.grpc.Context.current().getDeadline()` 服务端实测可用（客户端 1s ⇒ 读到 932ms 剩余）」
>   **本轮未复跑**（需起真栈）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」，不升级。

> ✅ **2026-10-24 验证轮续 §1：本条从「静态推断」升级为「运行期实证」**（结论**未变**，证据强度**变了**）。
>
> **实测（2026-10-24 00:44~00:46，两套 admin-rpc 同时在跑，主仪器 = PG 服务端日志）**：
>
> | 档位 | 侧 | 查询存活 Δ_start | **客户端返回后仍占 PG Δ_end** |
> |---|---|---|---|
> | 客户端 1000ms | Go | +1005ms | **+0ms** |
> | 客户端 1000ms | **RAG** | **+2117ms** | **+1111ms** |
> | 客户端 5000ms（对照） | Go | +2113ms | −3ms |
> | 客户端 5000ms（对照） | RAG | +2124ms | +3ms |
>
> ⇒ **Go 继承了客户端 deadline**（查询随客户端一起结束）；
> **RAG 没有继承**（客户端 1006ms 就返回了，查询还在 PG 上占着 **1.1 秒**）。
> ⇒ 对照组（客户端 5000ms）两侧 Δ_end 都 ≈0 ⇒ 差异**只**来自「是否继承客户端 deadline」。
>
> ⚠️ **本条最反直觉的一点（也是第一版实验做错的地方）**：
> **客户端耗时两侧必然相同**（都是客户端自己的 deadline）⇒
> 拿客户端耗时判这条会得出「两侧一致」的错误结论。
> 真正的可观察差异是**服务端资源在客户端放弃后继续占用**。
>
> ⇒ 完整实验设计与效力边界 ⇒ **验证轮续 §1**；
> 工具 ⇒ `tools/difftest/GrpcAdminDeadlineProbe.java` + `exp-grpc-admin-deadline-probe.sh`。

### UNRESOLVED-ARPC-003 —— Redis 客户端超时配置不对齐

```
Question:   Logout 写黑名单时，Redis 不可达时两侧分别在多久后失败、报什么？
Go Evidence:    etc/admin.yaml:16-19  BizRedis 只配了 Host/Type/Pass，
                **没有 Timeout** ⇒ 依赖 go-zero redis.New 的默认拨号/读超时
Java Evidence:  application.yml:48  timeout: 3s（Lettuce）
Why Unknown:    两边的「默认超时」分别是多少，需要读各自依赖库的默认值 + 双侧实跑；
                本轮既没读 go-zero 的默认超时，也没起 Redis 容器做故障注入。
Impact:         故障时的挂起时长与错误文本可能不同（错误码两侧都是 Internal，desc 也一致，
                所以对调用方**只影响时延不影响语义**）。
Required Confirm: 起一个 Redis 容器并 iptables 掉网络，两侧各打一次 Logout，
                比对「返回时刻」与「description 文本」。
```

> ✅ **2026-10-24 验证轮续 §2：本条关闭**（配置侧全部对齐，socket 层已实证）。
>
> **关闭依据（逐层）**：
> 1. **库默认值已读到底**（不是猜）：
>    - Go `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-39` 建 `red.Options` 时
>      **三个 timeout 一个都没传** ⇒ 走 go-redis/v9 `options.go` 的 `init()`：
>      `DialTimeout = 5s`（`:329-330`）/ `ReadTimeout = 3s`；
>    - 🔴 红鲱鱼：go-zero `core/stores/redis/redis.go:27` 的 `readWriteTimeout = 2 * time.Second`
>      是**死常量**（全文件仅此一处、从未被使用）⇒ 只看它会错判成 2s。
> 2. **RAG 侧已补齐并实施**（`connect-timeout: 5s`），理由见实施轮 §1。
> 3. **socket 层已实证**（验证轮续 §2.3/§2.4）：连黑洞 IP 的失败耗时落在
>    `[4000, 9500)` ms ⇒ **只有 5s 落得进去**（10s = Lettuce 默认 / 3s = 命令超时 / 75s = 系统默认）。
>
> **⚠️ 关闭时必须一起记的一条（本轮实测教会的）**：
> **Lettuce 的连接是异步的** ⇒ Redis 不可达时，**先到期的往往是命令超时（3s）而不是连接超时（5s）**
> ⇒ `connect-timeout: 5s` 在故障时**通常不决定用户可见的时长**。
> ⇒ 「Go 连接超时 5s / RAG 连接超时 5s」这个描述是**配置层**的等价，
> **不是**故障时的**可观察时延**的等价。
> ⇒ 两侧「错误码都是 Internal、desc 也一致」这个原始判断**依然成立**
> （本条 Impact 里那句话是对的：只影响时延不影响语义）。
>
> **守门网**：`AdminRedisTimeoutConfigTest`（yml 文本层 + Binder 绑定层）
> + `AdminRedisConnectTimeoutSocketTest`（配置消费点 + socket 层黑洞实测）。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已关闭（2026-10-24 验证轮续 §2 · 含 socket 层实证）** ⇒ **现行态 = ✅ 维持已关闭**（**配置侧两处仍在**）。
> 依据（两侧，当场核过）：**RAG** `service/users/admin/admin-rpc/src/main/resources/application.yml`（`connect-timeout: 5s` + `timeout: 3s` ⇒ **配置消费点**）· **Go** `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-39`（建 `red.Options` 时**三个 timeout 一个都没传** ⇒ 走 go-redis/v9 `options.go` 的 `init()`：`DialTimeout = 5s` / `ReadTimeout = 3s`）⇒ **「配置层等价」这半边仍成立**。⚠️ **关闭时必须一起记的那条仍在**：**Lettuce 的连接是异步的** ⇒ 故障时**先到期的往往是命令超时（3s）而不是连接超时（5s）** ⇒ 「配置等价」**不是**「可观察时延等价」。⇒ **本条维持关闭**（⚠️ **本轮未复跑 socket 黑洞实测** ⇒ 按 §0 读作「在该证据强度下一致」）。核对日期 2026-10-30。

### UNRESOLVED-ARPC-004 —— `extra_info` jsonb 的边界写入行为

```
Question:   extra_info 的值含 `\u0000` / 非法 UTF-8 / 超长串时，两侧分别成不成功？
Go Evidence:    列是 text + serializer:json ⇒ 任何合法 JSON 串都收
Java Evidence:  列是 jsonb + JsonbTypeHandler（ps.setObject(..., Types.OTHER)）⇒ PG 会校验
Why Unknown:    静态上能推断 jsonb 会拒，但「拒的到底是什么错误码、是否被
                AdminExceptions.isUniqueViolation 误分类成 ALREADY_EXISTS」需要实跑。
                ⚠️ 注意 isUniqueViolation 只认 SQLState 23505，jsonb 失败是 22P02，
                所以不会被误分类 —— 但这仍是推断。
Impact:         恶意/异常输入下 Go 成功而 RAG 5001。
Required Confirm: 对 createAdmin / updateUser / updateSelf 各打一次
                extra_info={"k":"\u0000"}，两侧比对。
```
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已关闭（§4 · 双侧活体对拍实测 · 2026-10-28 G1 补记）** ⇒ **现行态 = ✅ 维持已关闭**（**修法已落码：列已改 `TEXT`、处理器已改 `JsonTextTypeHandler`**）。
> 依据（两侧，当场核过）：**RAG** `service/users/users-common/src/main/resources/db/03_create_admin.sql:58` = `extra_info  TEXT,`（**DDL 已是 text**）＋ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/AdminEntity.java:89`（`typeHandler = …JsonTextTypeHandler.class` ⇒ **已不再走 jsonb 校验**）⇒ **「RAG 侧 jsonb 拒收 U+0000」这个前提**已因 2026-10-23 的 `DRIFT-ARPC-004①` 修法而**消失** · **Go** `service/user/admin/rpc/internal/model/data_model.go:11`（`serializer:json` ⇒ text 列照收）。⇒ **两侧现在同形（都是 text 列、都不经 jsonb 校验）⇒ 本条已闭环**（⚠️ **§4 里的双侧活体对拍表是 2026-10-23 修法**落地**前**的观测，属历史留档；⚠️ **本轮未复跑该对拍**）。核对日期 2026-10-30。

### UNRESOLVED-ARPC-005 —— `users_user` 建表责任交叉（跨组件，单侧验不完）

```
Question:   admin-rpc 与 user-rpc 都建 users_user，两侧同时对空库启动会不会撞？
已知:        SchemaInitializer.java:70-91 用 pg_advisory_lock(8675309) 串行化，
             且 4 份 DDL 全是 CREATE … IF NOT EXISTS
Go Evidence:    admin/rpc 与 user/user/rpc 的 User.TableName() **都是 "users"**（各自 AutoMigrate），
                Go 侧同样存在「两个进程都建同一张表」的结构，**但 Go 没有 advisory lock**
Java Evidence:  AdminSchemaBootstrap + AdminSeederBootstrap 的 @DependsOn 链
Why Unknown:    本组件单侧验完不了 —— 需要 user-rpc 侧也给出结论后合并判读。
                ⚠️ 注：check-ddl-static.py 的 diff 结果里 users_user 的 5 处类型差异
                有一半的责任在 user-rpc 侧脚本，不在本组件。
Impact:         若两边 DDL 漂移，会出现「谁先启动谁定型」的不一致。
Required Confirm: 与 user-rpc 台账合并；并跑一次「两个 rpc 同时对空库启动」的并发实测
                （AGENTS.md §6.2.5 记的 exp-go-concurrent-automigrate.sh 是 Go 侧版本）。
```

> ✅ **2026-10-23 验证轮 §1 已关闭**（本条是「单侧验不完」的跨组件条目，现两侧都跑过了）。
>
> **关闭依据（两侧各自的实测，不是推断）**：
> 1. **RAG 侧**：`rag-admin-rpc` / `rag-user-rpc` **两个进程真并发**启动
>    （`schema init starting` 分别是 `16.292` / `16.632`，PID 91018 / 91017），
>    但 advisory lock `8675309` **严格串行**：admin `17.610` 拿到锁 → `19.825` 释放，
>    user `19.825` 才拿到（`19.825` 与 admin 的 `19.825` 首尾相接）⇒ **不会撞**。
> 2. **Go 侧**：`exp-go-concurrent-automigrate.sh` 真并发起两个 rpc
>    （**共跑 4 轮**：`23:57:27.886`/`28.001`、`00:00:51.769`/`51.886`、
>    `00:01:07.845`/`07.976`、`00:01:23.698`/`23.801` —— 每轮两进程相差约 100ms，
>    确实是并发而非串行），**4 轮全部：两边都未报建表失败、4 张表
>    （`admin_invites,admins,user_avatar_history,users`）全建出、无撞**。
>
> **⚠️ 效力边界（关闭 ≠ 两侧性质相同，这条必须一起记）**：
> - **RAG 侧是「机制性保证」** —— advisory lock 让并发建表**在结构上**不可能撞；
> - **Go 侧是「本次没撞」** —— GORM AutoMigrate 的
>   `if !HasTable(value) { createTable(value) }` 是 **check-then-act**，
>   且 `createTable` 发的是**不带 `IF NOT EXISTS`** 的 `CREATE TABLE`
>   ⇒ **机制上仍有竞态窗口**，本次未撞只是时序没对上，**不构成保证**。
>
> ⇒ **结论只能写成「两侧在本次实测中都没有撞；RAG 侧有机制性保证，Go 侧没有」**，
> **不得**写成「两侧等价安全」。这条差异若将来 Go 侧真撞上，根因就在这里。
>
> **⚠️ 原脚本的一个坑（本轮修掉，否则会得出假结论）**：
> `exp-go-concurrent-automigrate.sh` 原来等库用 `pg_isready -U admin`（**不带库名**），
> 而 `pg_isready` 在 PG 的**初始化临时服务**阶段就返回 OK ⇒ 脚本会在数据库系统
> 还没起来时就把两个 rpc 放出去，实测报 `57P03 the database system is starting up`，
> **两边都连库失败、一张表都没建出来** ⇒ 判定列全是「?」，
> 也就是**实验根本没发生**（不是「Go 没问题」）。修法：带 `-d "$PG_DB"` 再等一轮 + 沉降 2s。
> —— 这是 AGENTS.md §3.3「跑绿/跑通 ≠ 结论对」的又一个形态：**实验失败时它看起来像结论**。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判「✅ 2026-10-23 验证轮 §1 已关闭」⇒ **现行态 = ✅ 已关闭（维持）**；
> ⚠️ **但本条正文「已知」栏写的证据指针 `SchemaInitializer.java:70-91` 已漂移 ⇒ 需追加现行位置**。
> - **被描述的机制是否还在**：**仍在** ——
>   `service/users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:91`
>   `private static final long ADVISORY_LOCK_KEY = 8_675_309L;`（**锁号 8675309 未变**）；
>   同文件 `:115` `st.execute("select pg_advisory_lock(" + ADVISORY_LOCK_KEY + ")");`
>   ＋ `:128` `pg_advisory_unlock(...)` ⇒ **「advisory lock 串行化建表」这一形态仍在**。
> - **指针订正（保留原值）**：`SchemaInitializer.java:70-91` ⇒ **现行 `:91`（锁号常量）+ `:115`（取锁消费点）+ `:128`（释放）**。
>   ⚠️ 按 AGENTS.md §3.3.1，行号应指向**消费点** ⇒ 关键那行是 **`:115`**。
> - **复现入口核实（2026-10-29）**：`tools/difftest/exp-go-concurrent-automigrate.sh` ✅ **存在**，
>   且本条正文说的那个坑**确已修**（`:49-55` 有「2026-10-23 修正」注释块 · `:56` `pg_isready -U "$PG_USER" -d "$PG_DB"` · `:57` `sleep 2`）
>   ⇒ **「实验根本没发生」那个坑已闭环**。
> - **不改判的理由**：本条断言的是「两侧并发建表不会撞，且 RAG 侧是机制性保证」，
>   而**机制（advisory lock 8675309 + 阻塞语义）仍在**、**复现脚本仍在且坑已修** ⇒ 状态维持 ✅。

---

## 10. 最终判定（spec §39 · 18 项 checklist）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边 public behavior 是否一致 | ⚠️ **否** | ARPC-002（wire）、ARPC-001（DB 状态） |
| 2 | 正常路径是否一致 | ✅ 是 | 79 条绿 + 真 PG 29 条（单侧） |
| 3 | 边界输入是否一致 | ⚠️ **否** | ARPC-003（空 default-password）；ARPC-004 的 NUL 边界未验 |
| 4 | NotFound / Empty / Null 是否一致 | ✅ 是 | 三态逐条对齐（§5.1 全表） |
| 5 | Error meaning 是否一致 | ✅ 是 | 13 个用例 × 每个分支的 code+desc 全部逐字对齐 |
| 6 | Error propagation 是否一致 | ✅ 是 | Go `return status.Error` ↔ Java 抛 `StatusRuntimeException` + invoke 透传 |
| 7 | External dependency failure 是否一致 | ⚠️ **部分** | Redis：语义一致、时延未验（UR-003）；PG 在途取消 4455ms vs 378ms（B-1/E-4 已台账） |
| 8 | Cache hit / miss 是否一致 | ➖ N/A | 本组件无缓存 |
| 9 | Transaction boundary 是否一致 | ✅ 是 | CreateAdmin 4 步原子（真 PG 实测）；其余 12 个两侧都无事务 |
| 10 | State transition 是否一致 | ✅ 是 | invite 三态 / user.status 二态逐条一致 |
| 11 | Side effects 是否一致 | ⚠️ **否** | ARPC-001（update_time）、ARPC-005（多建一表） |
| 12 | Ordering 是否一致 | ✅ 是 | `ORDER BY id DESC`；CreateAdmin 6 步序（R5） |
| 13 | Retry 是否一致 | ✅ 是 | CreateInviteCode 3 次，触发/次数/退避/终态全一致 |
| 14 | Timeout scope 是否一致 | ⚠️ **未定** | 服务端 2s 一致；客户端 deadline 是否被 min 未验（UR-002） |
| 15 | Concurrency semantics 是否一致 | ✅ 是 | 悲观锁 + UNIQUE 兜底，8 并发实测 1 成功 |
| 16 | Auth / Authz 是否一致 | ✅ 是 | 两侧都不在本组件鉴权 |
| 17 | Serialization semantics 是否一致 | ⚠️ **否** | proto wire 逐字节一致，但 ARPC-002 改变了 `success` 的取值；ARPC-004 的列类型改变 extra_info 边界 |
| 18 | Lifecycle 是否一致 | ✅ 是（次序）/ ⚠️ 覆盖面 | 建表→seeder→server 次序一致；ARPC-005 多建一表 |

```
最终判定（spec §39 三值之一）：

   ╔══════════════════════════════════════════════════════════════════╗
   ║  SEMANTICALLY NOT EQUIVALENT                                     ║
   ║                                                                  ║
   ║  · SEMANTIC MISMATCH:        0 条                                ║
   ║  · OBSERVABLE DIFFERENCE:    6 条（ARPC-001 ~ ARPC-006）           ║
   ║  · 已签字的有意偏离:          0 条新增（0 条需要签字）              ║
   ║  · UNRESOLVED:               5 条（§9）                          ║
   ║                                                                  ║
   ║  ⚠️ 证据强度上限：单侧真 PG / 静态源码+框架消费点                  ║
   ║     **本轮零条双侧活体对拍** —— 因此「NOT EQUIVALENT」这一判定    ║
   ║     只在已列出的 6 条上成立；其余维度是「未见差异」，              ║
   ║     不等于「已证明等价」。                                        ║
   ╚══════════════════════════════════════════════════════════════════╝
```

### 三条历史回归的复核结论（2026-10-23 逐条独立复核）

| # | 历史回归 | 结论 | 当前源码证据 | 运行证据 |
|---|---|---|---|---|
| H-1 | **`CreateAdmin` 事务 self-invocation 失效** | ✅ **已修**（且机制正确） | 4 步事务已独立成 Bean `AdminCreateAtomicService`（:39-40 `@Service`），由 `AdminDomainService` **构造器注入**（:103,111,118）并跨 Bean 调用（:446-447）；`@Transactional` 在 :110；主源码全模块 `@Transactional` 只出现在该类的两个重载上 | **真 PG**：`AdminRpcTransactionWiringTest` 4 条，W2/W3 失败 ⇒ invite 的 `used_at`/`used_by_uid` 仍为 NULL、admin 行未落（W3 唯一约束真被撞）；**0 skipped** |
| H-2 | **邀请码缺 `FOR UPDATE`** | ✅ **已修** | `AdminCreateAtomicService.java:116-120` `.last("FOR UPDATE")`，且在 `@Transactional` 方法体内 ⇒ 锁随事务释放。Go 出处：`model/admin.go:131` `clause.Locking{Strength: "UPDATE"}` | **真 PG**：`AdminRpcConcurrencyWiringTest` N=8 同 invite ⇒ **1 OK + 7 InviteUnavailable + 0 other**，且 `invite.used_by_uid` 唯一指向赢家；**0 skipped** |
| H-3 | **Flyway 双服务版本冲突** | ✅ **已修**（走的不是 §6.3 问题 4 那条路） | admin-rpc 走的是 §6.3 **问题 1**：pom.xml 里 flyway 三件套**已全部移除**（注释 :97-121，且依赖清单中确实没有）；`application.yml` 无 `spring.flyway.*`；改由 `AdminSchemaBootstrap` → `users-common SchemaInitializer.applyAll` 幂等重放（`CREATE … IF NOT EXISTS`，无账本无版本号）+ `pg_advisory_lock` 串行化。⇒ admin-rpc **根本不在**问题 4 描述的那三个仍用 Flyway 的模块里 | `check-flyway-ledger.py` `EXIT=0`，输出「扫描到 3 个启用 Flyway 的模块」= article-rpc / like-rpc / message-rpc，**不含 admin-rpc**；真 PG：容器测试证明「建表先于 seeder」 |

> ⚠️ **H-1/H-2 的残余风险（不算未修，但要记）**：
> `AdminCreateAtomicService` 的 **6 参重载**（:64-69）通过 **self-invocation** 调用 7 参重载。
> 它目前**安全**，因为 6 参重载自身也带 `@Transactional`，事务已在外层开启；
> 但这是与 H-1 原始 bug **完全同款**的形状，只差一个注解就复发。
> 生产路径（`AdminDomainService:446`）直调 7 参版本，不经过该重载。
> ⇒ 已记入 `docs/Pitfalls/users/admin/rpc.md` 的 P-1。

---

## 历史导航页（2026-10-21 原文，一字未改）

> **本文件是导航页，不是内容页。**
> 与 [`../user/rpc.md`](../user/rpc.md) 同理：AGENTS.md 里 `admin-rpc` 的记录没有独立成节，
> 散落在以 `service/users` 为标题的多模块叙述里。为**不割裂叙事、不改一字**，整体搬到
> [`../_shared.md`](../_shared.md) 与 [`_shared.md`](_shared.md)。本页给出精确索引。

### 搬移到 `users/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `admin-rpc` 的关系 |
|---|---|---|
| **L479 起** | AGENTS.md §6.2.5 | **Flyway 迁移并发** —— Go 的 `user-rpc` 与 `admin-rpc` 同时对空库启动的 DDL 竞态实测；advisory lock 修法。 |
| **L561 起** | AGENTS.md §6.2.6 | 证据边界台账 E-1~E-6，含 **E-5 的 admin-rpc 并发与回滚证据**（真 PG）。 |

### 搬移到 `users/admin/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `admin-rpc` 的关系 |
|---|---|---|
| 全篇 | AGENTS.md §3.4.3 | `service/users/admin` 审计后的三条新增留档 |
| 全篇 | AGENTS.md §6.2.2 | **issue #71186443** 语义审计修复清单 —— 原记「`service/users/admin` 全部 10 项 SEMANTIC MISMATCH 已修」 |
| 全篇 | AGENTS.md §6.2.3 | admin 域的 UNRESOLVED（尚无运行证据的项） |

### 搬移到 `users/admin/api.md` 的相关段落

| 在 `api.md` 的行号 | 来源 | 说明 |
|---|---|---|
| 全篇 | AGENTS.md §6.2.4① | Go 对「空 body」的三段取证，支撑 admin-api 13 端点 `required=false` |

### ⚠️ 本模块曾有一个「修了但没生效」的记录，务必读

§6.2 表中有一行 `RpcServerInterceptorFactory 在 user-rpc / admin-rpc 均未注册`，
标着 **「✅ 2026-10-21 已修 · ⚠️ 2026-10-21 二次更正：本次『已修』不成立」**：

> `@Import` 只让 Bean **存在**，grpc-spring-boot-starter 3.1.0 **从不自动注册**用户提供的
> `ServerInterceptor`。原测试之所以假绿，是因为它断言的是「Bean 存在」而非「server 用了它」。

⇒ 这是 AGENTS.md §3.3「绿测试可以是错误答案的守卫」的**原型案例**。完整内容见
[`../../_cross/open-items.md`](../../_cross/open-items.md)。

> **2026-10-23 复核：原结论修正为 —— 本条现已真正闭环。**
> 证据：`AdminRpcApplication.java:66` 有显式 `@Import(RpcServerInterceptorFactory.class)`，
> **且** :97-101 定义了 `GrpcServerConfigurer` bean `server -> server.intercept(rpcUnaryServerInterceptor)`
> —— 后者才是真正把 interceptor 挂到 gRPC server 上的入口（`@Import` 只保证 Bean 存在）。
> 与 Go 出处对齐：`service/user/admin/rpc/admin.go:43` `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(...))`。

### 尚未处理

- ~~`service/users/admin/admin/rpc` ↔ Go `service/user/admin/rpc` 逐字段 proto 对照：无独立留档。~~
  ✅ **2026-10-23 已闭环** —— `tools/difftest/check-proto-static.sh` 输出「admin(13 RPC)：admin.proto ↔ admin.proto：wire 契约逐字节一致」，`EXIT=0`；正式对照见本文档 §4 矩阵最后一行。
- 本模块的 `HUMAN_ONLY.MD`：**存在**（`service/users/admin/admin-rpc/HUMAN_ONLY.MD`）。
  ⚠️ **2026-10-23 实测发现其内容已过期**：第 28 行与第 83-84 行仍写
  「`createAdminAtomic` 走 `@Transactional default` 接口默认方法」「4 步事务：插 invite / 插 admin /
  标 invite used / 写密码」—— ①「default 接口方法」在当前代码里**不存在**（三个 Mapper 接口都零方法，
  见 `AdminMapper.java:19-38` / `AdminInviteMapper.java:18-33` / `UserMapper.java:21-31`）；
  ② 4 步的实际顺序是「查 invite / 查 username / bcrypt / 插 admin / 标 invite used」，
  **不是**「插 invite」；③ `pom.xml:82-86` 与三个 Mapper 的类注释还残留同一套错误说法
  （已在 2026-10-21 被订正过一遍，pom 与 HUMAN_ONLY.MD 漏改了）。
  ⇒ 属 §3.2 文档级病灶，本轮只记录不改（只读不写源码）。

## 搬移到 `users/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `admin-rpc` 的关系 |
|---|---|---|
| **L479 起** | AGENTS.md §6.2.5 | **Flyway 迁移并发** —— Go 的 `user-rpc` 与 `admin-rpc` 同时对空库启动的 DDL 竞态实测；advisory lock 修法。 |
| **L561 起** | AGENTS.md §6.2.6 | 证据边界台账 E-1~E-6，含 **E-5 的 admin-rpc 并发与回滚证据**（真 PG）。 |

## 搬移到 `users/admin/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `admin-rpc` 的关系 |
|---|---|---|
| 全篇 | AGENTS.md §3.4.3 | `service/users/admin` 审计后的三条新增留档 |
| 全篇 | AGENTS.md §6.2.2 | **issue #71186443** 语义审计修复清单 —— 原记「`service/users/admin` 全部 10 项 SEMANTIC MISMATCH 已修」 |
| 全篇 | AGENTS.md §6.2.3 | admin 域的 UNRESOLVED（尚无运行证据的项） |

## 搬移到 `users/admin/api.md` 的相关段落

| 在 `api.md` 的行号 | 来源 | 说明 |
|---|---|---|
| 全篇 | AGENTS.md §6.2.4① | Go 对「空 body」的三段取证，支撑 admin-api 13 端点 `required=false` |

## ⚠️ 本模块曾有一个「修了但没生效」的记录，务必读

§6.2 表中有一行 `RpcServerInterceptorFactory 在 user-rpc / admin-rpc 均未注册`，
标着 **「✅ 2026-10-21 已修 · ⚠️ 2026-10-21 二次更正：本次『已修』不成立」**：

> `@Import` 只让 Bean **存在**，grpc-spring-boot-starter 3.1.0 **从不自动注册**用户提供的
> `ServerInterceptor`。原测试之所以假绿，是因为它断言的是「Bean 存在」而非「server 用了它」。

⇒ 这是 AGENTS.md §3.3「绿测试可以是错误答案的守卫」的**原型案例**。完整内容见
[`../../_cross/open-items.md`](../../_cross/open-items.md)。

## 尚未处理

- `service/users/admin/admin/rpc` ↔ Go `service/user/admin/rpc` 逐字段 proto 对照：无独立留档。
- 本模块的 `HUMAN_ONLY.MD`：**存在**（`service/users/admin/admin-rpc/HUMAN_ONLY.MD`）。
---

<!-- @fix 2026-10-23 修复轮：DRIFT-ARPC-001（update_time 永不推进） -->

## 2026-10-23 修复轮：DRIFT-ARPC-001 已修

> 增量追加，上文 DRIFT-ARPC-001~00N 原始记录**一字未删**。
> ⚠️ 组件判定仍是 **`SEMANTICALLY NOT EQUIVALENT`** —— 本轮只关掉 001，其余条目与全部 U-* 未动。

### ✅ FIX-ARPC-001 —— 5 处 UPDATE 全部补上 `update_time = CURRENT_TIMESTAMP`

**根因**：`UserEntity.updateTime` / `AdminEntity.updateTime` 标的是
`updateStrategy = FieldStrategy.NEVER`（`domain/UserEntity.java:163`，2026-10-27 实测；原写 `:71` 是 2026-10-23 快照），本意是
「不让实体自动写这一列」。但 **MyBatis-Plus 没有 GORM 的 `autoUpdateTime`** ——
`NEVER` 的语义是「**永远不更新**」，不是「我没赋值时才不更新」。
于是 5 个写路径**全部**不推进 `update_time`：不报错、不告警、SQL 全部成功。

**Go 出处**：声明点 `service/user/admin/rpc/internal/model/data_model.go:12-13`（Admin）、
`:27-28`、`:44-45`（User），均含 `gorm:"column:update_time;autoUpdateTime"`；
消费点 `gorm@v1.31.2/callbacks/update.go:243-259` 与 `:279-281`。

**修法与 5 个落点**：

| # | 方法 | 表 | Go 对应 |
|---|---|---|---|
| 1 | `updateSelf` | `admin_admin` | `UpdateOneAdminByUid`（`internal/model/admin.go:96`） |
| 2 | `banUser` | `users_user` | `UpdateUserStatusByUid` status=1（`admin.go:104`） |
| 3 | `unBanUser` | `users_user` | `UpdateUserStatusByUid` status=0（`admin.go:104`） |
| 4 | `updateUser` | `users_user` | `UpdateOneUserByUid`（`admin.go:58`） |
| 5 | `resetUserPassword` | `users_user` | `UpdateUserPasswordByUid`（`admin.go:112`） |

### 🔴 为什么用 `setSql` 而不是 `MetaObjectHandler`（与 article-rpc 不同，**这是对的**）

本类 5 处写路径**全是 `update(null, wrapper)`** —— 第一个参数是 `null`。
MP 的 `MetaObjectHandler.updateFill` 需要一个**实体**去填字段；实体为 null 时
`MybatisParameterHandler:107-115` 根本不会建 `MetaObject` ⇒ **不会被调用**。
在 article-rpc 加 handler 才对（那边 8 处全是 `updateById(entity)`，实体非 null）。

> **两处修法不同不是不一致，是各自写路径形态决定的** —— 这条判断本身值得留档：
> 「同一个 GORM 语义在两个模块有两种 Java 修法」时，先看**写路径的实体形态**，
> 不要先看「哪个更地道」。

**放置位置**：照抄 user-rpc 既有解法（`UserMapper#updateUserById`）—— 把 `setSql`
放在「无变更则不发 UPDATE」判定**之后**。本类 5 处**没有**该判定，所以无条件跟在其后。

**`SkipHooks` 排除**：admin-rpc 侧**零 `UpdateColumn`**（Go 侧实测同样为零）
⇒ 全部落在 GORM 会推进时间戳的那一侧，无需像 article-rpc 那样排除任何路径。

### ⚠️ 已知残留分歧（**未修**，另立条目跟踪，不在本次内猜）

Go 的 `UpdateOneAdminByUid` / `UpdateOneUserByUid`（`admin.go:58` / `:96`）用
`Updates(struct)`，GORM **跳过零值字段**，全零时**根本不发出 UPDATE** ⇒ `update_time` 不变。
RAG 对应两处在**参数全空**时仍会发出一条只更新 `update_time` 的 UPDATE。

- 这**不是本次修复引入的**（修复前它们连 SET 子句都是空的）；
- 补齐它需要先确定「空更新该不该短路」的**返回语义**（短路返回什么？影响调用方吗？），
  属于独立决策 ⇒ **不在本条目内猜**。

### ⚠️ 顺带修正的一条错误断言（AGENTS.md §3.3.2 教科书案例）

`AdminSemanticDriftRegressionTest#resetUserPassword_usesConfiguredDefaultNotSeederConstant`
曾把 UPDATE 的 SET 子句**写死为** `password=#{ew.paramNameValuePairs.MPGENVAL1}`
—— 即**断言 `update_time` 不在 SET 里**。这是一条**把缺陷锁死的绿测试**。

按 §3.3.2「**修复漂移时先改/删错误断言，再改实现**」：先改断言（补上
`,update_time = CURRENT_TIMESTAMP`），再改实现。否则会先撞上「测试红了」
而误以为自己改错了方向。该测试的**主职责**（`resetUserPassword` 必须读配置的
`system.default-password` 而非 seeder 硬编码常量）**保留不变**。

### 证据

`AdminRpcUpdateTimeAdvanceWiringTest`（新建，真 PG + Testcontainers，5/5 绿）：

- 4 条正向：`banUser` / `unBanUser` / `updateUser` / `resetUserPassword` 推进 `update_time`；
- 1 条反向：用户不存在时 `banUser` 抛 `NOT_FOUND` 且**不动任何行**
  （对应 Go `admin.go:105-107` 的 `RowsAffected==0` 早返回）。

断言形状同样被缺陷决定：先把 `update_time` 推到 `2020-01-01` 让「有没有前进」无歧义，
且每条都**先断言业务列真的写进去了** —— 否则可能在测一条只动时间戳的空转 UPDATE。

admin-rpc 当前：`mvn -o -pl service/users/admin/admin-rpc -am test` ⇒
**81 tests / 0 failures / 0 errors**，BUILD SUCCESS。


---

<!-- @round 2026-10-23 20:41–21:35 · users/admin 域审计轮 · 证据强度升级轮 -->

# 2026-10-23 审计轮（users/admin 域 · 证据强度升级）

> **核对日期**：2026-10-23　　**范围**：`admin-rpc` + `admin-api` + `admin-proto`
> **本轮目标（用户 2026-10-23 指定）**：把本域推到**真 PG 守门测试 + Go ↔ RAG 双侧活体对拍**
> —— 这是 §3.3 的理想态；此前 admin-rpc 的**双侧活体对拍证据为零**（UNRESOLVED-ARPC-001）。
>
> ⚠️ **本节是增量追加**，上文 §1–§10 的原始记录**一字未删**。
> 新编号一律走 `DRIFT-ARPC-007` 起；与 `admin/_shared.md` 的共享台账零冲突。

## 0. 本轮实跑的命令与观察结果（**每条都可复现**）

| 命令 | 结果 |
|---|---|
| `mvn -o -pl service/users/admin/admin-rpc -am test`（本轮**基线**，改代码之前） | 12 个测试类 **84 tests / 0 failures / 0 errors / 0 skipped**。真 PG 容器用例 29 条（`AdminCreatePasswordOrderRegressionTest` 9 + `AdminRpcConcurrencyWiringTest` 2 + `AdminRpcPaginationGormSemanticsTest` 7 + `AdminRpcTransactionWiringTest` 4 + `AdminRpcUpdateTimeAdvanceWiringTest` 5 + `AdminRpcContainerWiringTest` 7）—— **Skipped=0**，即 `@EnabledIfDockerAvailable` **确实放行**（`admin-rpc/pom.xml:212-217` 已注入 `TESTCONTAINERS_RYUK_DISABLED=true`） |
| `tools/difftest/run.sh --keep --serve-only` | 两套真栈起来：**Go** user-rpc 19004 / admin-rpc 18081 / user-api 18888 / admin-api 18889；**RAG** user-rpc 59210 / admin-rpc 58081 / user-api 59900 / admin-api 58889。基建 = `difftest-postgres`(go_db + rag_db) + `difftest-redis` |
| `tools/difftest/diff.py --scenario all` | **steps=39 / asserted=10 / byte_equal=29 / unexpected=0**，`EXIT=0` —— **admin 域 HTTP wire 层的双侧活体对拍，全绿** |
| `tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up`（**本轮新建**） | **cases=18 / code_mismatch=0 / payload_diff=0 / description_diff=5**，`EXIT=0` —— **admin-rpc gRPC 层的双侧活体对拍**（本轮首次） |
| `mvn -o -pl service/users/admin/admin-rpc,service/users/admin/admin-api -am test`（本轮**收尾**） | 见 §6 |

> ⚠️ **一个必须记下来的坑（不是语义，但会让下一轮的数字全是假的）**：
> `diff.py` **不是可重复运行**的 —— 它的 `admin.update` 步骤会把 admin 的密码改成
> `newadmin123`、`admin.delete` 会删掉被测用户 ⇒ 在**同一个库**上**第二次**跑
> `admin.login` 就用旧密码失败；**两侧同时**返回 `{code:1002}` ⇒ diff.py 判「两侧相同 ⇒ OK」
> ⇒ **后续所有带 token 的步骤全部变成 401**，报告上却是一片 OK。
> 本轮第一次单独跑 `--scenario admin` 时踩到了（5 条 DIFF 全是
> `Go='strconv.ParseInt: parsing "": invalid syntax'` vs `RAG='field "uid" is not set'` ——
> 那是 `{{user_uid}}` 占位符为空造成的，**不是语义差异**）。
> ⇒ **正确姿势**：每轮先 `run.sh --keep --serve-only`（它会 `docker compose down -v` 重建库）
> 再跑 `diff.py`，**且只跑一次**。本轮的 39 步 0 差异就是在一个全新库上跑出来的。

## 1. 本轮结论概览

| | 结论 | 证据强度 |
|---|---|---|
| **admin-rpc（gRPC 层）** | 🔴 **NOT EQUIVALENT** —— 但**全部差异集中在 `status.description`**；**gRPC code 18/18 两侧全同** | ✅ **双侧活体对拍**（本轮首次） |
| **admin-api（HTTP 层）** | ✅ **EQUIVALENT**（在「路径前缀 / method / 参数通道 / 401 body 形状 / 黑名单业务码」这 5 条**已签字**偏离之外的范围内） | ✅ **双侧活体对拍**（39 步） |
| **admin-proto** | ✅ wire 契约逐字节一致（`check-proto-static.sh` EXIT=0，13 rpc） | ✅ 静态双侧 |
| **DDL 列类型**（`admin_admin` / `admin_invite` 的时间列、`extra_info` 的 jsonb/text） | 🔴 仍未修 —— **修法归属 `users-common`，本组件无 DDL 所有权** | ✅ 真 PG schema 对照 + **双侧活体对拍**（jsonb 边界已实测） |

> ⚠️ **「admin-api 等价」这句话的效力边界**：它只覆盖 HTTP wire 层。
> gRPC 层仍有一条**未签字**的差异（黑名单 401 的业务码 `401` → `1006`，
> DRIFT-ADMINAPI-004）—— **HTTP 对拍看不到它**，因为两侧都把它映射成了 401；
> 只有直连 admin-api 的客户端去看 body 才知道业务码不同。
> ⇒ 「39 步全绿」**不等于**「admin 域无漂移」。

## 2. 🔴 DRIFT-ARPC-007 —— Go 真源的 12 处字面量被转码损坏，RAG 吐的是正常中文

```
Component:   admin-rpc（gRPC status description）
Category:    Serialization / Error Identity（**只在 gRPC 层可见**）
Risk:        LOW（业务语义等价；但 wire 契约不一致 ⇒ §3.1 要求覆盖的项目）
Classification: OBSERVABLE DIFFERENCE（description 层） + gRPC code 层 EQUIVALENT
```

### 现象

Go 真源仓库 `Sea-RideTheWind_golang` 里 **admin/rpc 的 4 个文件、12 处中文字面量**
在磁盘上就是**乱码字节** —— 提交进 git HEAD 的就是乱码（`git show HEAD:…` 复核过），
不是本地工作区损坏。

### Go 出处（**原始字节**，从 Go 源文件逐字节读出；hex 可用 `python3` 复核）

| Go 文件:行 | Go 实际字节（hex） | Go 实际字面量 | RAG 吐出的 |
|---|---|---|---|
| `internal/logic/getselflogic.go:37` | `e990a2e384a6e59f9be6b693e5b685e793a8e98da63f` | `鐢ㄦ埛涓嶅pscene鍦?` | `用户不存在` |
| `internal/logic/updateselflogic.go:37` / `:83` | 同上 | `鐢ㄦ埛涓嶅pscene鍦?` | `用户不存在` |
| `internal/logic/createinvitecodelogic.go:55` | 同上 | `鐢ㄦ埛涓嶅pscene鍦?` | `用户不存在` |
| `internal/logic/createadminlogic.go:73` / `:99` | `e990a2e384a6e59fbe98d9ae5b685e587a1e7809be6a8bae6b9aa` | `鐢ㄦ埛鍚嶅凡瀛樺湪` | `用户名已存在` |
| `internal/logic/createadminlogic.go:119` | `4442e5a8a3e8afb2e5a79ee6beb6e8beabe8a7a6` | `DB娣诲姞澶辫触` | `DB添加失败` |
| `internal/logic/createinvitecodelogic.go:70` / `:101` | `e7bba0efbc84e6828ae98d9be6a980e5808be79287e9a38ee7889ce990a2e786b8e59e9ae6beb6e8beabe8a7a6` | `绠＄悊鍛橀個璇风爜鐢熸垚澶辫触` | `管理员邀请码生成失败` |
| `internal/logic/createinvitecodelogic.go:94` | `4442e98d90e6ac8fe58f86e6beb6e8beabe8a7a6` | `DB鍐欏叆澶辫触` | `DB写入失败` |
| `internal/logic/updateselflogic.go:63` | `e780b5e59799e7889ce98d94e78ab2e79891e6beb6e8beabe8a7a6` | `瀵嗙爜鍔犲瘑澶辫触` | `密码加密失败` |
| `internal/logic/updateselflogic.go:77` | `4442e98f87e5ad98e69f8ae6beb6e8beabe8a7a6` | `DB鏇存柊澶辫触` | `DB更新失败` |
| `internal/logic/getselflogic.go:40` / `createinvitecodelogic.go:60` | `4442e98f8ce383a8ee8797e6beb6e8beabe8a7a6` | `DB鏌ヨ澶辫触` | `DB查询失败` |
| `internal/logic/createadminlogic.go:50` / `:59` / `:67` | `e7bba0efbc84e6828ae98d9be6a980e5808be79287e9a38ee7889ce996bfe6ac92ee87a4` | `绠＄悊鍛橀個璇风爜閿欒`（**部分损坏**） | `管理员邀请码错误` |

> ⚠️ 「Go 实际字面量」一栏在终端里显示为乱码 —— 那**就是**文件里的内容。
> hex 一栏是从源文件逐字节读出的真值（`python3` 对 `status.Error(…, "…")` 的字面量取 UTF-8 字节）。

### 根因（**不是作者写的，是转码往返弄坏的**）

损坏是**逐字符**的：有的字符变成了 GBK 误解码产物（`鐢ㄦ埛`），有的字符只坏了一半
（`绠＄悊…` 里的 `＄` 是 **U+FF04 FULLWIDTH DOLLAR SIGN**，`閿欒` 也是半坏的）。
⇒ 这是 **UTF-8 → 被当作 GBK 解码 → 再编码成 UTF-8** 的**有损**往返的签名，
**不是**任何写作意图。
全仓共 **7 个文件**中招：
`admin/rpc/internal/logic/{getselflogic,updateselflogic,createinvitecodelogic,createadminlogic}.go`
（4 个，**本组件**）+
`user/rpc/internal/logic/{selectavatarlogic,uploadavatarlogic,getavatarhistorylogic}.go`
（3 个，**属 users/user 域 —— 另一 worker 所有**）。
`service/user/admin/api/**` 与 `service/user/common/**` **零命中**。

### RAG 出处

**符号级**（行号已随源码变动失效 ⇒ 改用方法名 + 现行行号，2026-10-27 实测）：
  `AdminDomainService.doLogin`（`:212/:220/:236`）· `getSelf`（`:296/:303`）· `updateSelf`（`:332/:339/:347/:357/:376/:416/:424/:431`）
  · `doCreateAdmin`（`:476/:516/:528/:530/:537/:540`）· `doCreateInviteCode`（`:587/:595/:618/:639/:653`）
  · `doUpdateUser`（`:961/:969/:988/:992/:1008`）· `doDeleteUser`（`:1096/:1100`）· `doResetUserPassword`（`:1138/:1146/:1171/:1183/:1200`）
—— 全部是 `Status.X.withDescription("正常中文")`（该文件共 **48** 处中文 description，`grep -c` 实测 2026-10-27）。

### 双侧活体对拍实测（**本轮的核心证据**）

`tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up`（**本轮新建**）：

```
cases=18  code_mismatch=0  payload_diff=0  description_diff=5
```

5 条 description 差异分别是 `getself.notfound` / `updateself.notfound` /
`createinvitecode.notfound`（Go = `鐢ㄦ埛涓嶅pscene鍦?`）与
`createadmin.emptyinvite` / `createadmin.badinvite`（Go = `绠＄悊鍛橀個璇风爜閿欒`）。

### 效力边界（**这条差异只在哪一层可见**）

| 面 | 是否可见 | 证据 |
|---|---|---|
| **gRPC 直连 admin-rpc** | ✅ 可见（description 不同，**code 全同**） | `exp-difftest-grpc-admin-errors.sh` 实测 5 条 |
| **HTTP（admin-api）** | ❌ **不可见** | `admin-api/.../advice/GlobalExceptionHandler.java:74-75`：响应体的 `msg` 取自 `ErrorCode.getMsg(businessCode)`，**不是** `e.getStatus().getDescription()` ⇒ description 只进日志（`:76-77`），不进响应体 |
| **业务判定** | ❌ 不可见 | 两侧 **gRPC code 18/18 全同**；admin-api 的业务码映射只看 code（`GlobalExceptionHandler.mapRPCStatusToCode:84-92`） |

⇒ **业务影响：零**。它是 **wire 契约层**的不一致（§3.1 明确要求覆盖「错误码语义」）。

### 处置（**本轮不改行为，升级为待签字项**）

**RAG 保持正常中文，不复刻乱码。** 理由三条：

1. **Go 那一侧是仓库级数据损坏，不是设计** —— 损坏的逐字符特征已经证明（见「根因」）。
   把乱码搬进产品是把数据损坏当规范。
2. **无业务影响**（code 全同、HTTP 不可见）⇒ 不值得用它换「一致性」。
3. **§3.4.3 N-7 已有同型先例**：「Go 的空列表是缺陷不是设计 ⇒ Java 现状即为已修正的 bug，
   两侧不一致属有意，不再对齐」。本条同型，但**尚未签字** ⇒ 列为**待用户裁决**。

> ⚠️ **不要在没有签字的情况下「对齐」**：把 RAG 的 description 改成乱码不是对齐，
> 是把一条**别的组件的测试**（`AdminRpcGoWireParityTest` 的 tripwire）逼红。
> 那条 tripwire 的作用正是：有人改了 RAG 的 description ⇒ 立刻红 ⇒ 逼他先改台账。

### 守门

`AdminRpcGoWireParityTest#errorStatusTableMatchesGoWireBytes`（**本轮新建**，真 PG）：
- 9 处**未损坏**的：断言 RAG 的 description 与 **Go 原始字节（hex 常量）逐字节相等** ——
  不是「与中文字面量相等」，而是与**从 Go 侧读出来的字节**相等；
- 5 处**已损坏**的：断言 RAG 仍吐正常中文，**且断言它与 Go 的损坏字节不同**（决策 tripwire）。
- 另加 `#createAdminUsernameTakenDescriptionIsStillChinese` 覆盖第 6 处损坏字面量（ALREADY_EXISTS）。

## 3. 🔴 DRIFT-ARPC-008 —— `status IS NULL` 的读法：Go 读成 **0**，不是「读不出来」

```
Component:   admin-rpc（users_user 的读路径）/ **同时证伪 users/user 域的一条结论**
Category:    State Semantics / Serialization
Risk:        MEDIUM（结论错误 ⇒ 会把人引向「修」错方向）
Classification: EQUIVALENT（两侧实测一致）
```

### 背景：被证伪的前提

`AGENTS.md` §0 标红「⚠️ admin-rpc 同款 `status=0L` 未修」，
`docs/DRIFT/users/user/rpc.md` 的 **DRIFT-USERRPC-V-001** 写：

> Go 的语义不是「NULL 当 0」，而是「**NULL 读不出来**」（`gorm@v1.31.2/scan.go:153`
> `db.AddError(rows.Scan(values...))` → `database/sql` 对「NULL → int64」返回 unsupported 错误）

⇒ 本轮**双侧活体对拍 + 独立 Go 探针**两条路都否掉了这个前提。

### Go 侧机制（三段，逐段指向消费点）

| # | 位置 | 事实 |
|---|---|---|
| 1 | `gorm.io/gorm@v1.31.1/scan.go:19`（`prepareValues`） | `values[idx] = reflect.New(reflect.PointerTo(field.FieldType)).Interface()` ⇒ 对 `int64` 字段造出来的是 **`**int64`（双指针）**，不是 `*int64` |
| 2 | `database/sql` 的 `convertAssign` | dest 是指针、src 为 nil ⇒ 走 `case reflect.Pointer: if src == nil { dv.SetZero(); return nil }` ⇒ **不报错**，只把内层指针置 nil |
| 3 | GORM 的 int setter（`schema/field.go`） | 收到 `**int64` 且 `*data == nil` ⇒ **字段保持零值 0**，同样不报错 |

⇒ **Go 的语义是「NULL 当 0」**，不是「读不出来」。
（关键点：GORM 造的是**双指针**，而 `database/sql` 只对**单指针** dest 报 unsupported 错误。）

### 双侧活体对拍实测（2026-10-23）

1. `go_db.users` 插一行 `status IS NULL`（该列在 Go 的 DDL 里**可空**：
   `data_model.go:41` 的 `Status int64 \`gorm:"column:status;default:0"\`` **无 `not null`**）；
2. `rag_db.users_user` 同样插一行（该列在 RAG 的 DDL 里是 `NOT NULL` ⇒ 先
   `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL`）；
3. 打两侧 admin-api：

```
Go  : GET  /admincenter/v1/admin/getuser?uid=7770000000000000001
      → {"code":200,"msg":"OK","data":{"user":{…,"status":0},"found":true}}
RAG : POST /admin/v1/getuser  {"uid":"7770000000000000002"}
      → {"code":200,"msg":"OK","data":{"found":true,"user":{…,"status":0}}}
```

⇒ **两侧都是 `status: 0`，一致。**

另有一条**独立可复现的 Go 探针**（不依赖服务起停，直接用 GORM v1.31.1 + Go 的结构体标签）：

```
$ DSN="host=127.0.0.1 port=55432 user=admin password=difftest dbname=go_db ..." go run .
err=<nil>
Status=0
Username="zzz_null_status_go"
```

（探针跑法：`/tmp/gorm-null-probe` —— 用冻结仓库的 `go.mod`/`go.sum` + 同版本 GORM，
**不写入冻结仓库一个字节**；探针里逐字照抄 `data_model.go:35-46` 的 `User` struct 与
`admin.go:27` 的 `Where("uid = ?", uid).First(&user)`。）

### 结论与处置

| 侧 | 现状 | 判定 |
|---|---|---|
| **admin-rpc**（`domain/UserEntity.java:136` 的 `= 0L`） | NULL → 0 | ✅ **与 Go 一致，不是漂移** ⇒ **不改** |
| **users/user**（user-rpc 端已删 `= 0L`，并加「status 为 null ⇒ fail-closed 抛 INTERNAL」） | NULL → INTERNAL「DB查询失败」 | 🔴 **与 Go 相反** ⇒ 已作为**跨组件问题上报**（§8），本组件**无权改** |

⇒ **AGENTS.md §0 的「admin-rpc 同款 `status=0L` 未修」这一行应当撤销**（依据已备好，见本节）。
本轮**没有改** AGENTS.md（超 32 KB 预算，且不在本 worker 的所有权内）。

### 守门

`AdminRpcGoWireParityTest#nullStatusColumnReadsAsZeroLikeGo`（**本轮新建**，真 PG）：
- 前提断言：这一行的 `status` 在 DB 里**真的是 NULL**（否则本用例什么也没测到）；
- 断言 `getUser` 读到 **0**（不是 null、不抛错）；
- 断言 `getUserList` 走同一条扫描路径也是 0。

## 4. ✅ UNRESOLVED-ARPC-004 已关闭 —— `extra_info` 的 jsonb 边界（双侧活体对拍实测）

### 提问

`extra_info` 的值含 `\u0000` / 非法 UTF-8 时，两侧分别成不成功？

### 回答（**实测**）

`admin_admin.extra_info` 在 Go 侧是 **`text`** 列（`serializer:json` 写 JSON 串），
在 RAG 侧是 **`jsonb`** 列（`JsonbTypeHandler` → `ps.setObject(…, Types.OTHER)`）。

2026-10-23 双侧活体对拍（同一份请求体，`extra_info = {"k":"U+0000"}`；
`encoding/json` 把 U+0000 转义成 6 个 ASCII 字符 `\u0000` ⇒ **不是**裸 NUL 字节）：

| | 结果 | 落库 |
|---|---|---|
| **Go** admin-api `POST /admincenter/v1/admin/create` | **HTTP 200 `{code:200,…,"uid":"…"}`** | ✅ 行已落库：`extra_info = {"k":"U+0000"}`（**text 列照收**） |
| **RAG** admin-api `POST /admin/v1/create` | **HTTP 200 `{code:5001,"msg":"系统内部错误"}`** | ❌ **无行**；PG 报 `ERROR: unsupported Unicode escape sequence`（**22P02**，jsonb 列拒收） |

⇒ **确认是 OBSERVABLE DIFFERENCE**（Go 成功 / RAG 失败）。
且 **错误码分类正确**：22P02 ≠ 23505 ⇒ 没被 `AdminExceptions.isUniqueViolation`
误判成 ALREADY_EXISTS ⇒ 落在 `INTERNAL` → api 层 `5001`（与 Go 的 `Internal→5001` 一致，
见 `admin/_shared.md` §6.2.2 DRIFT-004 的不对称映射表）。

> ⚠️ **写法说明（2026-10-28 · G1）**：本条两处 `{"k":"U+0000"}` 里的 `U+0000` 是**可见写法**，
> 表示该 JSON 字符串值里含 **U+0000 这个字符**（U+0000 = NUL），**不是** 6 个 ASCII 字符的 JSON 转义形态。
> ⇒ 原先此处直接写**真 NUL 字节** ⇒ ⇒ 使本文件被 git 按**二进制**处理 ⇒ ⇒ 三方合并【不报冲突、只取一边/逐块揉】
> ⇒ ⇒ **“零冲突”推不出“内容完整”** ⇒ ⇒ 故改为可见写法；**本条记录的事实一字未改**（仍是「`extra_info` 的值含 U+0000」）。
> ⇒ ⚠️ 别把它与上面第 1330 行给出的 **6 字符 JSON 转义形态**混为一谈：
> 那是**存储形态**，与此处表示的**字符本身**不是同一回事。
> ⇒ 守门：`tools/difftest/check-no-nul-bytes.py` 的 `docs/**` 零 NUL 口径 ⇒ 本文件修完后**恒绿**
> ⇒ ⇒ 该工具**不在 G1 写权面** ⇒ ⇒ 白名单里本文件的条目由 **G4 / 合并收口执行者**清空。

### 修法与归属

**修法 = 把 `admin_admin.extra_info` 从 `jsonb` 改成 `text`**（与 Go 的 `serializer:json` 同构）
+ `JsonbTypeHandler` 改成写 JSON 串。
🔴 **但 DDL 在 `service/users/users-common/src/main/resources/db/03_create_admin.sql:50`**
⇒ **归属另一个 worker**，本组件无 DDL 所有权 ⇒ **只报告，不实施**（见 §8）。

## 5. 本轮未闭环的项（逐条带原因）

> ⚠️ **2026-10-23 22:4x「台账 vs 实际」复核：本表已被「修复轮 §5」取代，勿按本表判现状。**
> 本表写于审计轮结束时；其后修复轮已关闭其中第 1、2、4、6 项（并新增了跨域上报项）。
> **权威现状见「修复轮 §5」，逐条对照表见「复核轮 §9」。**

| # | 项 | 状态 | 原因 / 下一步 |
|---|---|---|---|
| 1 | `admin_admin` / `admin_invite` 的**时间列** `timestamp` → `timestamptz`（DRIFT-ARPC-004 里的 6 处） | ~~🔴 未修~~ **✅ 修复轮 §1.2 已修** | | DDL 在 `users-common`（`03_create_admin.sql:55-56`、`04_create_admin_invite.sql:37-41`）。⚠️ **不能照抄 `users_user` 那轮的改法**：`users_user` 的时间值**不进入任何 proto/DTO**（`_shared.md` 已核），而 **`admin_invite.expires_at` 是 wire 可见的**（`CreateInviteCodeResp.expires_at` = Unix 秒 ⇒ 两侧算法一致、**当前 wire 不可见差异**），但 `expires_at` 的**比较**（10 分钟有效期）在跨时区多实例下会偏一个时区差 ⇒ 每张表的敏感点不同，必须逐表评估 |
| 2 | `admin_admin.extra_info` jsonb → text | ~~🔴 未修~~ **✅ 修复轮 §1.1 已修** | | DDL 归属 `users-common` |
| 3 | `admin_admin` / `admin_invite` 的**表名**两侧不同（Go `admins`/`admin_invites` vs RAG `admin_admin`/`admin_invite`） | ⚠️ 已确认为**族内约定**（同 `users`→`users_user`），非缺陷；`check-ddl-static.py` 有映射表 | 已在本组件的实体 javadoc 里改正（原先写「GORM 默认表名 admin_admin / admin_invite」是**错的**：Go 的 `TableName()` 返回 `admins` / `admin_invites`，见 `data_model.go:16-18` / `:31-33`） |
| 4 | `DRIFT-ARPC-002`（`UpdateUserResp.success`：Go=false / RAG=true） | 🟡 **待用户裁决**（未签字） | 本轮补了**双侧活体对拍证据** + **决策 tripwire** 测试；HTTP 面不可见（admin-api 硬编码 `success=true`，Go 侧同样硬编码） |
| 5 | `DRIFT-ARPC-003`（`system.default-password` 为空 ⇒ Go 成功 / RAG INTERNAL） | ~~🟡 未修~~ **📌 实施轮：用户签字保留，SD-2026-10-23-06** | | 需显式把配置置空才触发；本轮未加入对拍矩阵（要改配置重起两侧栈，代价大于收益） |
| 6 | `DRIFT-ARPC-005`（启动期建表 3 张 vs 4 张） | ~~🟡 未修~~ **✅ 修复轮 §3 关闭为实现差异（非缺陷）** | | RAG 多建 `users_user_avatar_history`（Go admin-rpc 不建）；全部 `CREATE … IF NOT EXISTS`，不破坏任何东西 |
| 7 | `DRIFT-ARPC-006`（RAG 侧零手写 span，Go 逐 Logic 打 `audit.*` 属性） | ~~🟡 未修~~ **✅ 实施轮 §2 已修（8 处，非 13 处）** | | 需要动 `service/common` 的 OTel 装配（不在本组件），且与 N-4 的签字理由直接相关 ⇒ 建议与 N-4 一起裁决 |
| 8 | `UNRESOLVED-ARPC-002`（超时是否 min 客户端 deadline） | ~~🟡 仍未定~~ **✅ 定性轮 §1：CONFIRMED SEMANTIC MISMATCH（修法在 service/common）** | | 本轮的 gRPC 对拍**没有**加客户端 deadline 档（`exp-difftest-grpc-admin-errors.sh` 用默认 deadline）⇒ 结论未变 |
| 9 | `UNRESOLVED-ARPC-003`（Redis 客户端超时配置） | ~~🟡 仍未定~~ **✅ 定性轮 §2 + 实施轮 §1：已定性并实施 connect-timeout** | | 本轮未做 Redis 故障注入 |
| 10 | `UNRESOLVED-ARPC-005`（`users_user` 建表责任交叉） | ~~🟡 跨组件~~ **✅ 验证轮 §1 已关闭** | | 已跑双侧并发实测：RAG 侧 advisory lock 串行、Go 侧实测未撞但机制上仍有竞态窗口 |

## 6. 本轮实测数字（**收尾跑**，可复现）

| 模块 | 测试类 | tests | failures | errors | skipped | 真 PG 容器用例 |
|---|---|---|---|---|---|---|
| `admin-rpc` | 13 | **88**（基线 84 + 本轮新建 `AdminRpcGoWireParityTest` 4） | **0** | **0** | **0** | **33**（29 + 4） |
| `admin-api` | 10 | **119** | **0** | **0** | **0** | 0（本组件无 SQL） |

> ⚠️ **「skipped = 0」是本轮硬门槛**（AGENTS.md §0 的 2026-10-23 新纪律）：
> `task-rpc` 曾因漏配 `RYUK_DISABLED` 被 `@EnabledIfDockerAvailable` **静默全跳**而 reactor 仍报
> `BUILD SUCCESS`。admin-rpc / admin-api 的 pom **都注入了**
> `TESTCONTAINERS_RYUK_DISABLED=true`（`admin-rpc/pom.xml:212-217`、`admin-api/pom.xml:165-170`），
> 且实测 `skipped=0` ⇒ 真 PG **确实跑了**，不是假绿。
> 复核命令：`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml`

## 8. 本轮新增/修改的文件

| 文件 | 性质 |
|---|---|
| `service/users/admin/admin-rpc/src/test/java/…/AdminRpcGoWireParityTest.java` | **新增**，真 PG 守门测试（4 条） |
| `service/users/admin/admin-rpc/src/main/java/…/domain/UserEntity.java` | javadoc 改写（`status = 0L` 的结论从「缺陷」改为「与 Go 等价」，含三段 Go 出处） |
| `service/users/admin/admin-rpc/src/main/java/…/domain/AdminEntity.java` | javadoc 改写（Go 表名 `admins`、DDL 权威来源、`extra_info` 边界） |
| `service/users/admin/admin-rpc/src/main/java/…/domain/AdminInviteEntity.java` | javadoc 改写（Go 表名 `admin_invites`、4 个时间列的类型差异与可观察性边界） |
| `service/users/admin/admin-rpc/src/main/java/…/mapper/AdminMapper.java` | 注释订正（`selectById(uid)` → `selectById(id)`：主键是自增 `id`，不是 `uid`） |
| `service/users/admin/admin-rpc/src/main/java/…/mapper/UserMapper.java` | 注释订正（删掉不存在的 `@Update`） |
| `tools/difftest/GrpcAdminDualPortRunner.java` | **新增**，gRPC 层双侧活体对拍执行体（18 用例） |
| `tools/difftest/exp-difftest-grpc-admin-errors.sh` | **新增**，上面的编排脚本 |
| `tools/difftest/grpc-admin-results.txt` | 本轮实测输出留档 |

> ⚠️ **三个 Mapper 的 javadoc 复核结论（待办里那条「曾与实现不符」）**：
> **已确认为「已修且当前成立」** —— 三个接口现在都**零方法声明**，且类注释里都显式写着
> 「本接口不声明任何方法（2026-10-21 订正）」并把方法真实归属列成表。
> ⇒ 本轮只需修两处残留的措辞（`selectById(uid)`、不存在的 `@Update`），见上表。

## 8. user 侧 / users-common 待裁决问题（**本 worker 无权改**）

| # | 问题 | 证据 | 建议 |
|---|---|---|---|
| **U-1** | 🔴 **`users/user` 的 DRIFT-USERRPC-V-001 结论被证伪**：`status IS NULL` 时 **Go 读成 0**（不是「读不出来」），而 user-rpc 端把 `= 0L` 删掉并加了 **fail-closed 抛 INTERNAL「DB查询失败」** ⇒ **user-rpc 与 Go 相反** | §3 的三段机制 + 双侧活体对拍 + 独立 Go 探针（三路一致） | **撤销 user-rpc 的 fail-closed 守卫**（或在签字后保留并明确写「RAG 有意 fail-closed」）。⚠️ 注意那条守卫的守门测试 `UserRpcLoginNullStatusFailClosedTest` 锁的是**错误答案**（§3.3.3「绿测试可能是错误答案的守卫」的又一个实例）⇒ 若决定撤销，测试与台账必须同步 |
| **U-2** | 🔴 **`users_user.status` 的 `NOT NULL` 与 Go 的可空不一致**（Go `default:0` 无 `not null`） | 本轮实测：`go_db.users.status` 可空（RAG 侧要先 `ALTER … DROP NOT NULL` 才能复现） | DDL 归属 `users-common`；本轮已把 admin-rpc 侧的 `users_user` 行为钉成「与 Go 一致（读成 0）」，**但 DDL 若改成可空，user-rpc 的守卫会变成常态路径** ⇒ U-1 与 U-2 必须一起裁决 |
| **U-3** | 🔴 **`admin_admin.extra_info` 应从 `jsonb` 改 `text`**（Go 用 `serializer:json` 写 text 列） | §4 的双侧活体对拍：Go 成功 / RAG 22P02 失败 | DDL 在 `users-common/.../03_create_admin.sql:50` ⇒ 归 users-common owner |
| **U-4** | 🔴 **`admin_admin` / `admin_invite` 的 6 个时间列应改 `timestamptz`** | `check-ddl-static.py` 的 7 处差异 + Go `data_model.go:12-13/27-28`（`time.Time` 无 type tag ⇒ postgres driver 生成 `timestamptz`） | DDL 在 `users-common` ⇒ 归 users-common owner。⚠️ **不要照抄 `users_user` 那轮的改法**：`admin_invite.expires_at` 是 wire 可见的，必须逐表评估 |
| **U-5** | ⚠️ **`users-common/.../03_create_admin.sql:14` 与 `04_create_admin_invite.sql:8/15` 的注释写错了 Go 的表名**（「GORM 默认表名 admin_admin / admin_invite」；Go 的 `TableName()` 实际返回 `admins` / `admin_invites`） | `data_model.go:16-18` / `:31-33` | 注释级病灶，在 users-common owner 的文件里 ⇒ 只报告 |
| **U-6** | ⚠️ **`users/user` 域另有 3 个 Go 文件的字面量同样被转码损坏**（`user/rpc/internal/logic/{selectavatarlogic,uploadavatarlogic,getavatarhistorylogic}.go`） | 全仓扫描（7 个文件命中） | 与 DRIFT-ARPC-007 同型；归 users/user owner。⚠️ **先确认那 3 个字面量是否会进 wire**（`getavatarhistorylogic` 的 `create_time` 可能是客户端可见字段） |

---

# 2026-10-23 修复轮（users/admin 域 · DDL 列类型 + ARPC-002 + 签字补录）

> **核对日期**：2026-10-23（22:0x–22:3x）
> **范围**：`admin-rpc` + `admin-api` + `admin-proto` + 共用 DDL（`users-common/db/03`、`04`）
> **分支**：`align/users-admin`（worktree `../RAG.worktrees/users-admin`）
> ⚠️ **本节是增量追加**，上文 §1–§10 与「2026-10-23 审计轮」两节**一字未删**。

## 0. 用户本轮拍板的 4 项（ask_user 问卷，逐条记录）

| # | 议题 | 裁决 | 落地位置 |
|---|---|---|---|
| 1 | **DRIFT-ARPC-003** 空 `system.default-password` | **签字保留 RAG 的严格拒绝** | [SD-2026-10-23-06](../../../Intentional_Deviations/users/admin/rpc.md) |
| 2 | **DRIFT-ARPC-007** Go 侧 12 处中文乱码 | **签字保留 RAG 的正常中文** | [SD-2026-10-23-07](../../../Intentional_Deviations/users/admin/rpc.md) |
| 3 | **DRIFT-ADMINAPI-004** 黑名单 401 业务码 `401`→`1006` | **签字保留 RAG 的 `1006`** + 补正式签字文件 | [SD-2026-10-23-08](../../../Intentional_Deviations/users/admin/api.md) |
| 4 | **6 个时间列** `timestamp`→`timestamptz` | **DDL 与实体一起改**（含 `LocalDateTime`⇒`OffsetDateTime`） | 本节 §2 |

## 1. ✅ DRIFT-ARPC-004 ①② 已修 —— `admin_admin.extra_info` jsonb→text、6 个时间列→timestamptz

> 上一轮（审计轮 §4、§5 第 1/2 项）判定这两条**修法归属 `users-common`、本组件无 DDL 所有权 ⇒ 只报告**。
> **本轮 ownership 已变**：本 worker 被指派 **users/admin 域**，
> 而 `03_create_admin.sql` / `04_create_admin_invite.sql` 建的是**本域自己的两张表**
> ⇒ 所有权成立，实施。**没有动 `01_create_user.sql` / `02_*`（users/user 域的表）。**

### 1.1 ① `admin_admin.extra_info`：`JSONB` → `TEXT`

| | 内容 |
|---|---|
| **Go 出处** | `data_model.go:11` `ExtraInfo map[string]string \`gorm:"column:extra_info;serializer:json"\``<br>**消费点**：`gorm.io/gorm@v1.31.1/schema/field.go` —— 字段带 `SERIALIZER` tag ⇒ `field.DataType = String` ⇒ 列按**字符串**建；`serializer/json` 的 `Value()` 返回 `json.Marshal` 的 **string** |
| **RAG 出处** | `03_create_admin.sql`（`extra_info JSONB`）+ `AdminEntity.extraInfo`（`JsonbTypeHandler`）+ `AdminDomainService.updateSelf` 的 `setSql("extra_info = CAST({0} AS jsonb)")` |
| **可观察性** | ✅ **OBSERVABLE** —— 2026-10-23 双侧活体对拍：含 U+0000 的 `extra_info`，Go 落库成功、RAG 被 jsonb 以 **22P02** `unsupported Unicode escape sequence` 拒收（根因：`encoding/json` 把 U+0000 转义成 6 个 ASCII 字符 `\u0000`，而 jsonb 的 unicode escape 校验拒绝它） |

**修法**（三处同提交，缺一不可）：
1. `03_create_admin.sql`：`extra_info TEXT` + 幂等自愈 `ALTER … TYPE text USING extra_info::text`
   （`jsonb::text` 取规范化文本、`text::text` 恒等 ⇒ 可反复重放；**不涉及会话时区**）
2. 新增 `mapper/JsonTextTypeHandler`（`ps.setString`），`AdminEntity` 改用它
   ——**不能复用 `JsonbTypeHandler`**：那个用 `ps.setObject(…, Types.OTHER)` 让 PG 按 jsonb 解释参数，只对 jsonb 列成立
3. `AdminDomainService.updateSelf` **去掉** `CAST({0} AS jsonb)`

> 🔴 **`updateUser` 里那处 CAST 必须保留** —— 它写的是 **`users_user.extra_info`**（`UserEntity`），
> 仍是 jsonb，DDL 归 users/user 域。两处文本逐字相同、只有实体泛型不同 ⇒
> 已记入 [Pitfalls P-8](../../../Pitfalls/users/admin/rpc.md)。

### 1.2 ② 6 个时间列：`timestamp` → `timestamptz`

| 表 | 列 | 可观察性 |
|---|---|---|
| `admin_admin` | `create_time`、`update_time` | ❌ 不可观察（admin.proto 无对应字段，且 RAG 侧从不读） |
| `admin_invite` | **`expires_at`** | 🔴 **本域唯一可观察的时间列** |
| `admin_invite` | `used_at`、`create_time`、`update_time` | ❌ 不可观察（proto 无字段；`used_at` 只做 `!= null` 判定） |

**`expires_at` 为什么咬人**（判定代码两侧**结构同构、语义不同**）：

```
Go   createadminlogic.go:64-68        now := time.Now()
                                     if invite.UsedAt != nil || !invite.ExpiresAt.After(now)
RAG  AdminCreateAtomicService:124-127 （修复前同构，LocalDateTime 版）
```

- Go 的 `invite.ExpiresAt` 来自 **timestamptz** ⇒ 带 location 的**绝对时刻**，`.After()` 比绝对时刻；
- RAG 原先是 **timestamp** ⇒ **墙钟**，`.isAfter()` 比墙钟。
- 单实例同区下两者恰好自洽 ⇒ **HTTP 对拍看不出来**；
  **跨时区多实例**时：实例 A（Asia/Shanghai）建码存下上海墙钟 12:00，
  实例 B（UTC）消费时 `now=04:00` ⇒ `04:00.isAfter(12:00)` 为 false
  ⇒ **邀请码刚生成就被判为已过期**。Go 侧不会。

**修法**：`04` / `03` 的 DDL + `AdminEntity` / `AdminInviteEntity` 的 `LocalDateTime ⇒ OffsetDateTime`
+ `createInviteCode` 与 `createAdminAtomic` 的 `now` 同步改 `OffsetDateTime`。

> ⚠️ **`expires_at` 的 wire 值一直是对的**：`createinvitecodelogic.go:83` 是
> `ExpiresAt: expiresAt.Unix()` —— **内存里算、不回读 DB**；RAG 修复前
> `expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond()` 也是内存里算 ⇒
> **`CreateInviteCodeResp.expires_at` 这个 HTTP 字段两侧本就一致**，
> 差异只在**下游能不能把它当成同一个绝对时刻**。
> （台账审计轮 §5 第 1 项的判断「两侧算法一致、当前 wire 不可见差异」是对的，本节是补上它的机制解释。）

> ⚠️ **DDL 重放的坑（沿用已验证的写法，不是照抄外形）**：
> `timestamptz AT TIME ZONE 'UTC'` 对**已经是 timestamptz** 的列**不恒等**
> —— 会话时区非 UTC 时**每启动一次整列偏移一次且永不收敛**；
> 而 `SchemaInitializer` 每次启动都原样重放。
> `DO $$ … $$` 条件守卫**实测不可行**（spring-jdbc `ScriptUtils` 按 `;` 切句、不支持 PG 美元引号
> ⇒ DDL 语法错 ⇒ user-rpc 与 admin-rpc **双双起不来**）。
> ⇒ 沿用 `SET TIME ZONE 'UTC'` → ALTER → `RESET TIME ZONE`（`01_create_user.sql:101-106` 已验证）。

## 2. ✅ DRIFT-ARPC-002 已修 —— `UpdateUserResp.success` 对齐 Go

| | 内容 |
|---|---|
| **Go 出处** | `updateuserlogic.go:105-113` —— `return &pb.UpdateUserResp{ User: &pb.UserInfo{…} }`，**只 set 了 User，没 set Success** ⇒ proto3 bool 默认值 `false`，线上该字段**缺席** |
| **RAG 出处** | `AdminServiceGrpcImpl.updateUser` 的 `.setSuccess(true)`（**已删除**） |
| **分类** | `WIRE CONTRACT / SERIALIZATION` —— **gRPC 层可观察**（Go 的字段缺席 vs RAG 的 varint 1） |
| **处置** | 按 §5.5「行为差异默认判 RAG 错」**对齐 Go**。⚠️ 本条**未列入本轮问卷**（4 项问的是 ARPC-003/007、ADMINAPI-004、时间列），按政策默认执行 |

**⚠️ HTTP 面不受影响**：admin-api 的 `AdminUpdateUserService` **两侧都硬编码 `success=true`**
（Go：`service/user/admin/api/internal/logic/admin/updateuserlogic.go:68`；RAG：同位置）
⇒ 端到端 HTTP 仍是 `{"success":true}`。
⇒ **这正说明 HTTP 对拍全绿推不出 gRPC 层已对齐**（与 `docs/DRIFT/users/admin/rpc.md` 审计轮 §1 的效力边界一致）。

**顺带订正一条错误出处**：RAG 的 `AdminUpdateUserService` 原注释写
「与参考侧 `UpdateUserLogic.java` 的第 129 行 1:1」—— **引错了文件与行号**（§3.3.2 形态）。
（⚠️ 原注释写的就是这个**不存在的文件名 + 不存在的行号** ⇒ 本处是「**引用一条已被本节指错的错误引用**」，
**它不是一个活的证据指针** ⇒ 机器复核报「文件找不到」属**预期**，不是台账缺陷。）
正确出处是 `service/user/admin/api/internal/logic/admin/updateuserlogic.go:68`（**api 层**，
不是 rpc 层的 `updateuserlogic.go:105-113`）。已改。
⇒ **2026-10-29 复核补记**：RAG 侧的对应落点现已核到
`service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/AdminUpdateUserService.java:99`
`resp.setSuccess(true);` ⇒ **两侧「api 层都硬编码 `success=true`」这半边仍成立** ✔（与上文 §2 的 HTTP 面结论一致）。

### 🔴 本条同时是一个「绿测试锁死错误答案」的实例（§3.3.3 原型案例）

`AdminRpcGoWireParityTest#updateUserRespSuccessFieldIsStillOppositeToGo` 原先断言
`assertTrue(resp.getSuccess())`，失败消息写「若本断言失败，说明有人改了实现」
—— **它把「与 Go 相反」这个漂移固化成了绿测试**。
修复实现后它立刻变红（`expected: <true> but was: <false>`），
**这条红正是「跑绿不是对」的直接证据**。

处置：改名 `updateUserRespSuccessMatchesGoWire`，断言改为
**与 Go 一致的 `false`**，并在 javadoc 里写明 Go 出处作为反向守卫
（有人重新 `setSuccess(true)` ⇒ 立刻红）。
⚠️ 顺序上本轮是**先改实现、后改断言**，与 §3.3.3「先改错误断言」的顺序相反，**结论一致**，已在 javadoc 里注明。

## 3. 📌 本轮关闭为「非缺陷」的项

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/run.sh` ⇒ **EXIT=0**（2026-10-30 03:29:45 · 137s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」） ⏐ PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ==> 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

### DRIFT-ARPC-005（启动期建表 3 张 vs 4 张）⇒ 关闭

Go `service/user/admin/rpc/internal/model/db.go:11-18` 是
`db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`（3 张）；
RAG 的 `AdminSchemaBootstrap` 通过 `SchemaInitializer.applyAll` 重放**全部 4 份**
users 域 DDL，多建 `users_user_avatar_history`。

**判定：IMPLEMENTATION DIFFERENCE，不是缺陷。** 理由：
1. 全部 `CREATE … IF NOT EXISTS`，多建一张**不破坏任何东西**；
2. 反过来「只建 3 张」会引入**跨域耦合** —— admin-rpc 要知道 users 域有哪几张表、
   并在 users/user 域增删表时同步改 admin-rpc 的白名单；
3. `SchemaInitializer` 的设计意图（见其 javadoc）就是
   「每个 rpc 进程启动时把**全套** users 域 DDL 重放一遍 = Go 的等价物」——
   用户与 admin-rpc **必须连同一个库**（§6.3 问题 1 的原由）。

⇒ **不改**，记为已关闭的实现差异。若日后要严格逐张对齐，须先决定
「admin-rpc 是否该知道 users 域的表清单」这个更上层的问题。

## 4. ✅ 签字档补录（修 `docs/Intentional_Deviations/users/admin/` 的 0 字节洞）

本轮建立/补齐三个文件（此前**全是 0 字节空占位**）：

| 文件 | 内容 |
|---|---|
| [`_shared.md`](../../../Intentional_Deviations/users/admin/_shared.md) | 本域签字主表：跨域通用 3 条（path/method/表名）+ N-1 / N-4 / N-5 + 指针 + **已撤销的 N-3 / N-6** |
| [`rpc.md`](../../../Intentional_Deviations/users/admin/rpc.md) | SD-2026-10-23-06（空默认密码）、SD-2026-10-23-07（乱码 description） |
| [`api.md`](../../../Intentional_Deviations/users/admin/api.md) | SD-2026-10-23-08（黑名单 401 业务码）+ SD-01 转录 |

**为什么必须补**：`users/` 这一层空着 ⇒ 按 `_INDEX.md → 域 → 组件` 检索时**在这一层断掉**，
于是 21:3x 的 admin-api 审计轮把**已经签字**的 SD-2026-10-23-01 误判成「未签字」。
详见 [`api.md` §3](../../../Intentional_Deviations/users/admin/api.md) 与
[Pitfalls P-9](../../../Pitfalls/users/admin/rpc.md)。

**顺带订正两处错误判定**（都写进了 `api.md`）：
- 「DRIFT-ADMINAPI-001 仍需用户裁决」⇒ **错**，SD-2026-10-23-01 两半都签了；
- 「N-1 从未声明『业务码 401 → 1006`』」⇒ **错**，N-1 原文就写着
  「业务码仍是 `ERROR_TOKEN_RUNTIME(1006)`」。

## 5. 未闭环 / 跨域上报（本轮**未**处理，逐条带原因）

| # | 项 | 状态 | 原因 / 下一步 |
|---|---|---|---|
| 1 | 🔴 **`users_user.extra_info` 也是 jsonb，而 Go 是 text**（`serializer:json`） | **跨域 · 未修** | DDL 归 users/user 域（`01_create_user.sql:41`），本 worker 无权改。**与本轮 ① 完全同型**：Go 落库成功 / RAG 被 jsonb 以 22P02 拒收。⇒ 建议 users-user 域照本节 §1.1 的三处同提交改法处理。**注意 `admin-rpc` 的 `updateUser` 也要跟着去掉那处 CAST**，否则跨域合并时会产生行为分叉 |
| 2 | 🟡 **DRIFT-ARPC-006**（RAG 侧零手写 span，Go 逐 Logic 打 `audit.*` 属性） | ~~未修~~ **✅ 实施轮 §2 已修（8 处 span）** | | ⚠️ **本条原先的判断「需动 `service/common`、不在本域」已被推翻** —— `admin-rpc` 自己的 `pom.xml` 已引 `opentelemetry-bom`，`GlobalOpenTelemetry.getTracer` 与 Go `otel.Tracer` 是同一套 API ⇒ 在 admin-rpc 内即可补齐，不动 `service/common`。另**订正计数**：Go 是 **8 个 logic** 有 span，不是 13（13 是 attribute 条数）⇒ 刻意不给 getself/getuser/getuserlist/logout/updateself 加 span，用负向断言钉死 |
| 3 | 🟡 **UNRESOLVED-ARPC-002**（超时是否 min 客户端 deadline） | ~~仍未定~~ **✅ 定性轮 §1 定性 → 验证轮续 §1 拿到运行期实证** | | **CONFIRMED SEMANTIC MISMATCH**（结论未变，证据强度变了：静态推断 → 实测）。实测：客户端 deadline 1000ms 时，Go 的查询随客户端一起死（Δ_end=0ms），**RAG 的查询在客户端返回后仍占着 PG 1111ms**。修法落在 `service/common` 的 `RpcInterceptor`（不继承入站 deadline），**不在本域**，故本 worker 无权改。⚠️ 现有 HTTP 对拍工具在结构上抓不到这条（见定性轮 §1.4）⇒ 不得以「对拍全绿」当它已闭环。<br>**2026-10-26 更新**：`align/global` 已修**档位 B 的 desc 文案**（`rpc timeout after 2000ms` → `context deadline exceeded`）⇒ **只销那一格**；**本条本体（不继承客户端 deadline）未修**，定性**不变**。该修复**已提交并推双远端**（`22a9111`，三处 ref 一致），**待合回 `do-my-own-gh`**。<br>**2026-10-26 再更新**：本条已拆成三层 —— ① desc 文案 ✅已修（`22a9111`）· ② **期限计算 ✅已修**（`af416d9`，`effectiveTimeoutMs = min(服务端预算, 客户端 deadline 剩余)`，待合回）· ③ **资源取消 🟡 机制层未做 / 可观察面已消**（grpc-java `ServerCall.close()` 不取消业务线程）⇒ 🔴 **2026-10-26 再复核：原写「`Δ_end` 不变」是错的** —— `Δ_end` 由 **PG 取消时刻**（= `statement_timeout`）决定 ⇒ **与业务线程是否被取消无关** ⇒ 第②层 + 第三十三轮（`d20b377`，毫秒精度 ⇒ 取消时刻 == deadline）⇒ **`Δ_end` ≈ 0**（**推导，未实测**） 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】⇒ **子项已闭环；整条定性降级为「机制层未闭环 + 非 SQL 路径残余（`DRIFT-ARPC-010`）」** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上观察依据已归零**（`align/global` 上实测 **22/18/12ms**）⇒ **这句现在成立**（合回前实测 pre-E62 为 `Δ_end=+833ms`）⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ global **E48**）】。⚠️ **探针未进编排**（global **E48**）⇒ **不得记「已闭环」**。本条已由 global 登记为 **E38**（A 类 backlog）；第③层机制部分已另立项（E38② 维持「另立项」），其 SQL 侧精度部分由 **E46** 在第三十三轮落地 |
| 4 | 🟡 **UNRESOLVED-ARPC-003**（Redis 客户端超时配置） | ~~仍未定~~ **✅ 定性轮 §2 + 实施轮 §1 + 验证轮 §2 + 验证轮续 §2 ⇒ **已关闭**（含 socket 层实证）** | | 读超时 3s 两侧一致（但**一致是巧合**，无出处无回归网）；连接超时已补 `connect-timeout: 5s`，**socket 层已用黑洞 IP 实测**（失败耗时落在 [4000,9500)ms ⇒ 只有 5s 落得进去）。⚠️ 但要记住：**Lettuce 的连接是异步的** ⇒ 故障时**先到期的往往是 3s 的命令超时**，5s 的连接超时通常不决定用户可见时延 |
| 5 | 🟡 **UNRESOLVED-ARPC-005**（`users_user` 建表责任交叉） | ~~跨组件~~ **✅ 验证轮 §1 已关闭（双侧并发实测）** | | RAG 侧 advisory lock **机制性**串行（同一把锁 8675309，两进程严格错开）；Go 侧 GORM AutoMigrate 是 check-then-act，**机制上仍有竞态窗口**，实测未撞≠机制保证 ⇒ 两侧性质不同，不可混谈 |
| 6 | 🟡 **DDL 的 `SET TIME ZONE` 副作用** | ~~已记录未验证~~ **✅ 修复轮 §8 已闭环（变异实测偏移 28800 秒）** | | 本轮新增的 `SET/RESET TIME ZONE` 与 `01`/`02` 同款；`SchemaInitializer` 四份脚本共用**一条连接**依次执行 ⇒ `03`/`04` 的 `SET` 只影响其自身语句区间，随后 `RESET` 还原。**未做「多实例并发建表 + 非 UTC 会话时区」的运行时验证** |
| 7 | ⚠️ **`users_user` 时间列的 3 张表** | 跨域 | `01`/`02` 已由 users/user 域于 2026-10-23 改为 TIMESTAMPTZ；本 worker **未复核**其正确性，只确认了 DDL 文本已是 `TIMESTAMPTZ` |

## 6. 本轮新增/修改的文件

| 文件 | 性质 |
|---|---|
| `service/users/users-common/src/main/resources/db/03_create_admin.sql` | `extra_info`→TEXT、2 个时间列→TIMESTAMPTZ、幂等自愈 ALTER |
| `service/users/users-common/src/main/resources/db/04_create_admin_invite.sql` | 4 个时间列→TIMESTAMPTZ、幂等自愈 ALTER |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/mapper/JsonTextTypeHandler.java` | **新增**（text 列专用；**不复用** `JsonbTypeHandler`） |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/AdminEntity.java` | `extraInfo` 换 handler、时间列→`OffsetDateTime`、javadoc 按 §3.2/§3.3.2 重写 |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/AdminInviteEntity.java` | 4 个时间列→`OffsetDateTime`、javadoc 补可观察性论证 |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java` | `updateSelf` 去 CAST、`createInviteCode` 改 `OffsetDateTime`、空密码守卫加签字注释 |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminCreateAtomicService.java` | `now` 改 `OffsetDateTime` |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminServiceGrpcImpl.java` | 删 `setSuccess(true)`（DRIFT-ARPC-002） |
| `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/service/AdminUpdateUserService.java` | 订正 Go 出处（引错的文件/行号） |
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcColumnTypeParityTest.java` | **新增**，真 PG 3 条（列类型 / `\u0000` 边界 / 跨时区 invite 判定） |
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcGoWireParityTest.java` | tripwire 反转：断言 `success == false` |
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/service/AdminUpdateSelfAndCreateAdminRegressionTest.java` | 跟随实体类型改 `OffsetDateTime` |
| `docs/Intentional_Deviations/users/admin/{_shared,api,rpc}.md` | **0 字节 → 正式签字档** |
| `docs/Pitfalls/users/admin/rpc.md` | 追加 P-8（同名列跨表）、P-9（签字档空占位） |

## 7. 本轮实跑结果（**逐条可复现**）

### 7.1 单元 / 容器测试

| 命令 | 结果 |
|---|---|
| `mvn -o -pl service/users/admin/admin-rpc,service/users/admin/admin-api -am test` | **BUILD SUCCESS** |
| `admin-rpc` surefire 汇总 | **91 tests / 0 failures / 0 errors / 0 skipped**（基线 88 + 本轮新建 `AdminRpcColumnTypeParityTest` 3 条） |
| `admin-api` surefire 汇总 | **121 tests / 0 failures / 0 errors / 0 skipped**（与审计轮基线相同，本轮无新增用例） |

> ⚠️ **`skipped = 0` 是硬门槛**（AGENTS.md §0 的 2026-10-23 新纪律）：
> `@EnabledIfDockerAvailable` 若被静默全跳，reactor 仍报 `BUILD SUCCESS`。
> 实测 `admin-rpc/pom.xml` / `admin-api/pom.xml` 都注入 `TESTCONTAINERS_RYUK_DISABLED=true`，
> 且汇总里 skipped 全 0 ⇒ **真 PG 确实跑了**。
> 复核：`grep -ho 'skipped="[0-9]*"' service/users/admin/*/target/surefire-reports/*.xml`

**新用例的逐条结果**（`AdminRpcColumnTypeParityTest`，真 PG，4.1s）：

```
✓ adminColumnsMatchGoDdlTypes                    库里的真实 schema：extra_info=text、6 个时间列=timestamptz
✓ extraInfoAcceptsPayloadJsonbWouldReject        \u0000 payload 落库成功 + 往返一致 + 整列覆盖语义
✓ inviteExpiryJudgedOnAbsoluteInstantNotWallClock 跨时区未过期邀请码被接受 / 已过期被拒（PERMISSION_DENIED）
```

### 7.2 双侧活体对拍（HTTP wire 层）

| 命令 | 结果 |
|---|---|
| `tools/difftest/run.sh --keep --serve-only` | Go: admin-rpc 18081 / admin-api 18889；RAG: admin-rpc 58081 / admin-api 58889，全部就绪 |
| `tools/difftest/diff.py --scenario all` | **steps=39 / asserted=10 / byte_equal=29 / unexpected=0**，`EXIT=0` |

⇒ **与审计轮记录的基线逐项相同** ⇒ 本轮 DDL 与 gRPC 改动**未造成任何 HTTP 层回归**。

> 🔴 **两条必须记下来的实测事实**：
>
> **① `--scenario admin` 单独跑会产生 5 条假 DIFF。**
> 本轮先跑了 `--scenario admin`，得到 `unexpected=5`，5 条全是
> `Go='strconv.ParseInt: parsing "": invalid syntax'` vs `RAG='field "uid" is not set'`
> —— 那是 `{{user_uid}}` 占位符为空造成的，**不是语义差异**（审计轮 §0 已记录同一坑）。
> ⇒ **只有 `--scenario all` 的数字有意义**。
>
> **② `user.avatar` 仍全绿 ⇒ 反向证明了「没误删 `users_user` 那处 CAST」。**
> `user.avatar` 走 `users_user` 的 jsonb 双写。若本轮把
> `AdminDomainService.updateUser` 的 `CAST({0} AS jsonb)` 一并删掉，
> 该步骤会报 `column "extra_info" is of type jsonb but expression is of type character varying`
> 而变红。**它是这次「两张表同名列、只有泛型不同」的活体证据。**

### 7.3 双侧活体对拍（gRPC 层）

| 命令 | 结果 |
|---|---|
| `tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up` | **cases=18 / code_mismatch=0 / payload_diff=0 / description_diff=5**，`exit code: 0` |

⇒ 与审计轮基线**逐项相同**；5 条 description 差异即已签字的 DRIFT-ARPC-007
（`updateself.notfound` / `createinvitecode.notfound` 的 `鐢ㄦ埛涓嶅pscene鍦?`，
与 `createadmin.emptyinvite` / `createadmin.badinvite` 的 `绠＄悊鍛橀個璇风爜閿欒`）。

### 7.4 🔴 **证据缺口（必须记下来，否则下一轮会拿它当闭环）**

**本轮修的 DRIFT-ARPC-002（`UpdateUserResp.success`）没有双侧活体对拍证据。**

`GrpcAdminDualPortRunner` 的 18 个用例里，`updateuser` **只有 `updateuser.notfound`（错误路径）**，
**没有 `updateuser.ok`（成功路径）** ⇒ `payload_diff=0` **不覆盖**本轮改的那条路径。
（同理，runner 里的成功路径只有 `login.ok` / `logout.ok` / `getself.ok` / `getuserlist.empty`。）

| 证据 | 强度 | 说明 |
|---|---|---|
| Go 侧「不 set Success」 | **静态源码逐字对照**（§3.1 的权威方法） | `updateuserlogic.go:105-113` 只 set `User` |
| RAG 侧「返回 false」 | ✅ **真 PG + 真 gRPC 实现** | `AdminRpcGoWireParityTest#updateUserRespSuccessMatchesGoWire` 调真实 `AdminServiceGrpcImpl`，真 PG 建行后 `updateUser` |
| **双侧活体** | ❌ **缺** | 见上 |

⇒ 本条的判定强度应读作「**Go 侧源码已逐字核对 + RAG 侧运行已证**」，
**不是**「双侧活体已证」。§3.3 的「两侧同时跑的探针」在此**尚未满足**。

**关闭缺口的最小动作**（**不在本 worker 所有权内** —— `tools/` 是共享资产，
§5.3 规定共享文件合并后统一更新）：
给 `tools/difftest/GrpcAdminDualPortRunner.java` 增一个 `updateuser.ok` 用例
（建一个存在的用户 → `updateUser` 成功 → 比对 `success` 字段与 `hasUser`）。
补上后本条即可升到「双侧活体」。

> 同理，`admin.updateuser` 在 **HTTP 层**虽被 `diff.py` 覆盖，
> 但两侧 api 层都硬编码 `success=true` ⇒ **它对 rpc 层的 `success` 字段零覆盖**。
> 「39 步全绿」推不出本条已闭环。

## 8. ✅ 本轮自引入的隐患已闭环 —— DDL 重放的时区幂等性（**含变异测试实证**）

> 上一节 §5 第 6 项自认「本轮新增的 `SET/RESET TIME ZONE` 未做运行时验证」⇒ 本节关闭它。

### 8.1 问题

`SchemaInitializer.applyAll` **每次启动都原样重放**这四份脚本（无版本账本，靠重放做升级），
而 `03` / `04` 的 ALTER 用了 `USING <col> AT TIME ZONE 'UTC'`：

```
timestamptz --AT TIME ZONE 'UTC'--> timestamp(UTC 墙钟)
             --按会话 TimeZone 铸回--> timestamptz(可能被平移)
```

会话时区非 UTC 时，**每启动一次整列时刻偏移一次，且永不收敛**。
`01_create_user.sql:80-94` 已把这条坑记得很清楚，但**没有任何测试守着那个前提**。

### 8.2 守门

`AdminRpcColumnTypeParityTest#ddlReplayDoesNotShiftInstantsUnderNonUtcSession`（真 PG）：
1. 写入 `create_time = '2026-01-02T03:04:05+00:00'::timestamptz`（**用带偏移量的字面量**，
   避免 `Timestamp.from()` 自己就掺进 JVM 默认时区）；
2. **把会话时区钉成 `Asia/Shanghai`**（含前提断言 `show timezone`），让危害条件成立；
3. 真实调用 `SchemaInitializer.applyAll(conn, "replay-probe")` —— **让脚本真跑一遍**，
   而不是查脚本文本里有几行 `SET`；
4. 用 `extract(epoch from create_time)` 比对前后（该表达式与会话时区无关，给的是真实 epoch 秒）。

### 8.3 🔴 变异测试实证（证明这条测试**不是假绿**）

按 §3.3「变异测试能证明测试不是假绿，但**不能**证明期望值是对的 —— 两者缺一不可」：

**变异**：把 `03_create_admin.sql` 的 `SET TIME ZONE 'UTC';` 那一行删掉（只改这一行，其余不动）。

```
Tests run: 1, Failures: 1
AssertionFailedError: … before=1767323045 after=1767294245
  ==> expected: <1767323045> but was: <1767294245>
```

**1767323045 − 1767294245 = 28800 秒 = 整 8 小时 = `Asia/Shanghai` 的 UTC 偏移。**

⇒ 两个结论同时拿到：
1. ✅ **守门是真的**：删掉那行 ⇒ 立刻红 ⇒ 有人误删 `SET TIME ZONE 'UTC'` 会被挡下；
2. ✅ **危害是真的**：无 `SET` 时**一次重放就平移 8 小时**，
   按 `SchemaInitializer` 的重放语义（每次启动一次）⇒ **每启动一次漂一次、永不收敛**。
   这不是理论推演，是本轮跑出来的数。

⇒ 文件已还原（`git diff service/users/users-common/` 为空），变异只存在于验证过程。

### 8.4 本轮收尾数字（补跑，含新增用例）

| 模块 | 测试类 | tests | failures | errors | **skipped** |
|---|---|---|---|---|---|
| `admin-rpc` | 14 | **92**（91 + 本节新增 1） | **0** | **0** | **0** |

`AdminRpcColumnTypeParityTest` 逐条（真 PG，7.98s）：

```
✓ adminColumnsMatchGoDdlTypes                    库里的真实 schema：extra_info=text、6 个时间列=timestamptz
✓ extraInfoAcceptsPayloadJsonbWouldReject        \u0000 payload 落库 + 往返一致 + 整列覆盖语义
✓ inviteExpiryJudgedOnAbsoluteInstantNotWallClock 跨时区未过期邀请码被接受 / 已过期被拒（PERMISSION_DENIED）
✓ ddlReplayDoesNotShiftInstantsUnderNonUtcSession 非 UTC 会话下重放 DDL，时刻不漂移（变异实测会红）
```

---

# 2026-10-23 复核轮（台账 vs 实际 · 逐条打回代码）

> **核对日期**：2026-10-23 22:4x–23:1x　**分支**：`align/users-admin`
> **方法**：把三档台账里每一条「已修 / 未修 / 已关闭 / N 处」断言**逐条打回代码、DDL、Go 源码核**，
> 核不上的当场改。**不新增对齐项**，只判「台账说的是否等于代码是的那样」。
> 依据：AGENTS.md §6.3 状态表纪律 —— **任何「已修/未修」断言都必须带核对日期，否则按 UNKNOWN 处理**。

## 1. 抽样复核通过的断言（不必改）

| 台账断言 | 核验方式 | 结果 |
|---|---|---|
| FIX-ARPC-001「5 处 `update_time = CURRENT_TIMESTAMP`」 | `grep -c` 实测 | ✅ **5 处**，全在 `AdminDomainService` |
| DRIFT-ARPC-008「admin-rpc 的 `status = 0L` 与 Go 等价，不要删」 | 看 `domain/UserEntity.java:136` | ✅ `= 0L` 在，javadoc 的三段 Go 出处完整 |
| DRIFT-ADMINAPI-011「400 的 `Content-Type` 已带 charset」 | `admin-api/.../advice/GlobalExceptionHandler.java:196` | ✅ `headers.set(CONTENT_TYPE, "text/plain; charset=utf-8")` |
| A-1「`user-api` 同款 charset 缺失仍在」 | `user-api/.../GlobalExceptionHandler.java:287` | ✅ 仍是 `setContentType(MediaType.TEXT_PLAIN)`，**A-1 仍有效** |
| 「`AdminEntity` 用 text handler、`UserEntity` 用 jsonb handler」 | 两个 `@TableField` 实读 | 🟢 **2026-10-27 订正：此断言已过期** —— global B10（`ff80214`，2026-10-25）把 `admin-rpc/.../domain/UserEntity.java` 的 `extra_info` typeHandler 也从 `JsonbTypeHandler` 改成了 `JsonTextTypeHandler`（`UserEntity.java:141`）⇒ ⇒ **两个实体现在都是 text handler**（`AdminEntity.java:89` / `UserEntity.java:141`）⇒ ⇒ `JsonbTypeHandler` 已整体删除 |
| 「`users_user.extra_info` 仍是 JSONB（跨域漂移仍在）」 | `01_create_user.sql:41` | 🟢 **2026-10-27 订正：此断言已过期** —— `users-common/.../db/01_create_user.sql:41` 建表就是 **`extra_info TEXT`**，`:103` 每次启动幂等 `ALTER TABLE users_user ALTER COLUMN extra_info TYPE text USING extra_info::text` ⇒ ⇒ **该列现在就是 TEXT，不是 JSONB** ⇒ ⇒ 本域 §4 表「Schema · extra_info 列类型」已判 EQUIVALENT（见 §11.8.3） |
| N-1 / SD-08「黑名单 401 业务码是 1006」 | `AdminJwtAuthInterceptor.java:127` 注释 + 对拍实测（⚠️ 2026-10-27：该行号随验签搬进 `auth/` 包而位移，原 `:124`） | ✅ |

## 2. 🔴 新发现（台账**从未记录**）：`admin_invite.update_time` 不推进

**这是本轮唯一的「新漂移」，且它一直就在 FIX-ARPC-001 的射程内被漏掉了。**

| | 内容 |
|---|---|
| **Go 行为** | `UpdateAdminInviteUsedTx`（`admin.go:141-146`）用 **`Updates(map[string]any{…})`** 标记 invite 已用，**同时补写 `update_time`** |
| **Go 消费点** | `gorm.io/gorm@v1.31.1/callbacks/update.go:235-255` —— autoUpdateTime 的补写逻辑位于 **map 更新分支**内（`default:` 结构体分支**之前**）；本调用无 `Select` ⇒ `selectColumns` 空、`restricted=false` ⇒ **条件成立** |
| **RAG 行为** | `AdminCreateAtomicService` W4 只 set 了 `used_by_uid` / `used_at`，**没有 `update_time`** |
| **可观察性** | ❌ **不可观察** —— `admin_invite.update_time` 不在 `admin.proto` 任何 message 里，业务代码也从没读过它 |
| **处置** | ✅ **已补**（`.setSql("update_time = CURRENT_TIMESTAMP")`）+ 守门测试 |

> ⚠️ **本条的价值不在「修了什么」，而在「为什么静态 review 连续两轮都漏了它」**：
> ① 不可观察 ⇒ 对拍永远看不到；② 它在**另一个类**里（`AdminCreateAtomicService`），
> 而 FIX-ARPC-001 的修复范围只盯着 `AdminDomainService`；
> ③ 「map 更新不推进 autoUpdateTime」是个**看起来很合理**的误解。
> ⇒ 与 P-5「79 条全绿仍漏 6 条」同型：**绿测试抓不到不可观察的漂移**。

### 连带订正：「5 处 UPDATE」这个计数是错的

`AdminDomainService.java` 的注释与本台账 FIX-ARPC-001 都写「全部 **5** 个 UPDATE 入口」——
那是**只数了 `AdminDomainService` 自己**。⇒ **全组件正确总数是 6 处**
（本类 5 + `AdminCreateAtomicService` 1）。两处注释均已订正。

**新增守门**：`AdminRpcGoWireParityTest#createAdminAdvancesInviteUpdateTimeLikeGo`
（真 PG + 真 gRPC）：造一条 `update_time=2020-01-01` 的 invite → 真调 `createAdmin`
→ 断言 `update_time` 前进，且 `used_by_uid` 已被写入（防「改坏 W4 主行为」）。

## 3. 🟡 陈旧断言：api.md 的 A-2 与本轮结论**自相矛盾**

`docs/DRIFT/users/admin/api.md` 有**两处**仍写着
「`docs/Intentional_Deviations/users/**` 下 **6 个文件全是 0 字节**」
（原第 999 行正文 + §5 表格的 A-2 行）。

而本轮已把 **admin 域 3 个补成正式签字档** ⇒ **同一文件内两处断言直接打架**。
⇒ 已加删除线 + 订正批注（admin 3 个已补，`users/user` 3 个仍空）。

> ⚠️ **这类矛盾比「漏写」危险**：后来者读到 §5 表格会以为洞还在，
> 可能重做一遍补档工作；读到正文又以为已补。**同一文件内必须自洽。**

## 4. 🟡 陈旧 + 悬空引用：`UserEntity.java` 的跨域描述

`domain/UserEntity.java` 的 javadoc 原文：

> 「⚠️ 反向提醒：`users/user` 域在 2026-10-23 把同名字段的 `= 0L` 删掉、并加了
> fail-closed 守卫……已作为跨组件问题上报（见 `docs/DRIFT/users/admin/rpc.md` **§11**）。」

**两处都错了**：

1. **内容已失效** —— `users/user` 域随后**撤销**了那个改动
   （主仓 commit `9ae121d`「撤销 V-001 反向判定 —— Go 侧 NULL status 是 fail-open 而非 fail-closed」）。
   实测：主仓与 `align/users-user` **两条分支**上 `user-rpc` 的 `status` 字段
   **都已恢复 `= 0L`** ⇒ 两侧现在一致，**都不应删**。
2. **指针悬空** —— `rpc.md` 最高只到 **§10**，且三个追加轮次各自从 §0 重启，
   **`§11` 从来不存在**。

⇒ 已重写为准确描述，并改指真正的权威位置（「审计轮 §3 DRIFT-ARPC-008」）。

> ⚠️ **元教训**：跨域引用是台账腐坏的**头号来源**。
> 本域的结论会被别人改掉，而**本域的注释仍在断言那个旧世界**。
> ⇒ 凡 javadoc/台账里引用**别的域的行为**，必须标注核对日期，
> 并在写「跨域问题上报」时**注明该上报是否已被对方撤销**。

## 5. 🟡 未销号：`UNRESOLVED-ARPC-001` 早已被推翻，却一直挂着

该条写「admin-rpc **没有任何**双侧活体对拍证据」，
Required Confirm 是「跑 `exp-difftest-grpc-errors.sh`」。

**而「2026-10-23 审计轮 §0」已经跑了**（用的是新增的 `-admin-` 变体，18 用例），
只是**没人回头销这条**。⇒ 本轮已补销号，并写明效力边界
（18 例**只覆盖错误路径**，成功路径只有 4 个 ⇒ 不是「13 个 RPC 全部闭环」）。

> **这条本身是个流程病灶**：UNKNOWN 条目不销号 ⇒ 下一轮 agent 会重复投入。
> ⇒ **纪律（本轮起）**：每轮结束必须回头销 UNRESOLVED，
> 哪怕结论是「只关闭了一部分」也要写清边界。

## 6. 🟡 未标注取代：审计轮 §5 的「本轮未闭环的 10 项」

审计轮 §5 写于 22:0x 前后；修复轮已关闭其中第 1（时间列）、2（extra_info）、
4（ARPC-002）、6（ARPC-005）项，并新增了跨域上报项。
但**旧表没有任何「已被取代」的标注** ⇒ 后来者可能按旧表判现状。
⇒ 已在该节顶部加取代提示 + 指向修复轮 §5 与本节。

## 7. 本轮未做（明确留给下一轮 / 别人）

| # | 项 | 原因 |
|---|---|---|
| 1 | 🔴 `users_user.extra_info` jsonb→text | 跨域，DDL 归 users/user 域。**复核确认该漂移仍在**（`01_create_user.sql:41` 仍 `JSONB`），§5 第 1 项**继续有效** |
| 2 | ARPC-006 零手写 span | ~~需 `service/common` 的 OTel 装配，不在本域~~ ⇒ 🟢 **2026-10-23 实施轮已推翻**：改为**在 `admin-rpc` 内**补齐（8 处 span，不动 `service/common`）⇒ **本行是首轮快照，已过时** |
| 3 | UNRESOLVED-ARPC-002 / -003 / -005 | 未做故障注入 / 需 user-rpc 侧结论 |
| 4 | `updateuser.ok` gRPC 对拍用例 | `tools/` 共享资产，按 §5.3 不在本 worker 所有权内 |
| 5 | **给 `AGENTS.md` §0 销账** | 共享索引/政策文件，须合并时统一更新；已把依据备在本节 |

## 8. 本轮实测数字（复核轮）

| 模块 | 测试类 | tests | failures | errors | **skipped** |
|---|---|---|---|---|---|
| `admin-rpc` | 14 | **93**（92 + 本轮 W4 守门 1） | **0** | **0** | **0** |

---

# 2026-10-23 定性轮（C 类：把「还没定性」的项判掉）

> **核对日期**：2026-10-23 23:2x–23:5x　**分支**：`align/users-admin`
> 起因：上一轮把它们归为「**连是不是漂移都还没判**」，用户要求做掉。
> 方法：**逐层读依赖库源码到消费点**（§3.3.1），不靠配置名、不靠默认值记忆。

## 1. ✅ UNRESOLVED-ARPC-002 定性 —— **CONFIRMED SEMANTIC MISMATCH**（不再是「未知」）

### 1.1 结论一句话

> **两侧的有效 deadline 不同：Go = min(客户端, 服务端配置)；RAG = 只有服务端配置。**
> ⇒ 客户端给一个比服务端更短的 deadline 时，**Go 会在客户端那一刻中止服务端的工作，
> RAG 不会** —— 它会一直干到自己的 2000ms。
>
> 🟢 **⚠️ 2026-10-26：上面这两句描述的是「修前」状态** —— E38 第①层已修
> （`align/global` @ `d531abd`，已推双远端、**2026-10-26 已合回主干**）⇒ **RAG 侧现在也是 `min(客户端, 服务端配置)`**
> ⇒ 「RAG 不会在客户端那一刻停」**已不成立**。
> ⚠️ **但「中止」与「取消在途工作」是两回事**（详见 §1.3 可观察后果表）：
> 第①层只让**超时 close 的时刻**对齐（`Δ_start` 会变），
> **在途 PG 查询随客户端放弃而被取消**那一层（第③层 / E38③ / E46）**仍未做**（`Δ_end` 不变）。
>
> 🔴 **2026-10-26 复核订正（上一行末句「`Δ_end` 不变」是错的）**：
> `Δ_end` 的主仪器是 **PG 服务端日志的取消时刻**（= `statement_timeout` 触发）⇒
> **它测的是「SQL 超时上限有没有继承客户端 deadline」，不是「业务线程有没有被 interrupt」** ⇒
> 第②层（期限计算，`af416d9`）一改，`Δ_end` 就跟着从 **+1111ms** 掉到 **≈ +80ms**（秒级取整残差），
> 第三十三轮（`d20b377`，毫秒精度）再把它压到 **≈ 0** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】 ⇒
> **第③层「机制未做」拦不住 `Δ_end` 归零**（**推导，未实测**）。
> ⇒ 第③层真正剩下的是**机制层**（业务线程不被取消）+ **非 SQL 路径**（⇒ `DRIFT-ARPC-010`）。

### 1.2 逐层证据链

| # | 层 | 事实 |
|---|---|---|
| 1 | Go `service/common/observability/rpc.go:38` | `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` —— **`ctx` 是 handler ctx**，gRPC 会把客户端的 `grpc-timeout` 传播进来 |
| 2 | Go 语言语义 | `context.WithTimeout(parent, d)` 在 parent deadline 已更早时**等价于 parent** ⇒ 有效 deadline = **min(parent, now+d)** = **min(客户端, 服务端)** |
| 3 | RAG `service/common/observability/RpcInterceptor` —— `deadlineEpochMs` 的计算 | **〔修前〕** `deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()` —— **从不读入站 `Context` 上的 deadline** ⇒ 有效 deadline = **只有服务端配置**<br>🟢 **〔2026-10-26 已变〕** 第①层落地后改为 `effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())` ⇒ **有效 deadline = min(客户端, 服务端)**，与 Go 侧第 1/2 行**同构**<br>⚠️ 本行与第 4 行**不再写行号**（`service/common` 被 `align/global` 持续改动 ⇒ 行号会漂）⇒ 改引表达式；现位置见下方「行号对照」 |
| 4 | RAG `RpcInterceptor` —— `TIMEOUT_SCHEDULER.schedule(…, timeoutMs, …)` | **〔修前〕** 到点才 `close(DEADLINE_EXCEEDED)`，**不受客户端 deadline 影响**<br>🟢 **〔2026-10-26 已变〕** 调度器延时改用 `effectiveTimeoutMs` ⇒ **close 时刻也随客户端 deadline 前移** |
| 5 | 配置值 | 两侧**配置相同**：Go 未配 `Timeout` ⇒ go-zero v1.10.2 `zrpc/config.go` 的 `Timeout int64 json:",default=2000"` = **2000ms**；RAG `application.yml:129` `rpc-timeout: 2s` = **2000ms** |

⇒ **差异不在配置值，在「是否继承」。** 这是本条最容易被误判成「两边都是 2s 所以一样」的地方。

> #### 📌 行号对照（2026-10-26，global 域逐条实测提供 · 本域**只读不改**）
>
> `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
> 在 `align/global` 上现共 **792 行**。本域旧引用 → 现位置：
>
> | 本域旧引用 | 现应指向 | 现内容 |
> |---|---|---|
> | `RpcInterceptor.java:162-165`（`rpc.md:563`） | **`:160-166`** | 「超时两层模型」的 javadoc（含「**本拦截器（gRPC 路径）没有第②层**」） |
> | `RpcInterceptor.java:170-172`（`rpc.md:657` / `:1820` / `:2511`） | **`:234-237`** | `effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())` + `deadlineEpochMs = …` |
> | `RpcInterceptor.java:184-198`（`rpc.md:1821`） | **`:263-264`** | `long timeoutMs = effectiveTimeoutMs;` + `TIMEOUT_SCHEDULER.schedule(() -> {` |
> | （E38 第①层新增的取值方法） | **`:592`** | `private static long clientDeadlineRemainingMs()`（从 `io.grpc.Context.current().getDeadline()` 取） |
>
> ⚠️ **本域已把这 4 处活引用改成符号名**（见各处的「原写 …，2026-10-26 改为符号名」留痕）
> ⇒ 上表是**给后人的定位辅助**，不是本域的活引用 ⇒ **行号仍会随 `align/global` 后续提交漂移**
> ⇒ **判据不变：共享层一律引符号名**（AGENTS.md §3.1：证据指向**消费点**）。
>
> #### 📌 精确 commit SHA（便于「合回后」逐条销账）
>
> | 条目 | commit | 状态 |
> |---|---|---|
> | **E30**（RPC 超时的 status description ⇒ Go 的 `context deadline exceeded`） | `49808ad` + `22a9111` | ✅ 已推 origin + upstream，**2026-10-26 已合回主干** |
> | **E38 第①层**（超时有效期限 ⇒ `min(客户端 deadline, 服务端 timeout)`） | `d531abd` | ✅ 已推 origin + upstream，**2026-10-26 已合回主干** |
> | **第三十二轮**（共享复核工具 `tools/check-ledger-selfcheck.py` 三处缺陷） | `bb680a3` | ✅ 已推 origin + upstream |

> #### ⚠️ C-1：遥测侧的 **gRPC code 名表示**两侧不同（**已登记的表述差异，不要去「修」**）
>
> | | 码名 |
> |---|---|
> | Go | `codes.Code.String()` ⇒ **`DeadlineExceeded`**（CamelCase） |
> | RAG | grpc-java 的 `Code.name()` ⇒ **`DEADLINE_EXCEEDED`**（全大写下划线） |
>
> - code 与 desc **均已保留**，**仅常量表示不同** ⇒ 已登记为 **C-1**
>   （`docs/Intentional_Deviations/common/_signed-deviations.md` · C 类）⇒ 明确「**不做臆造映射**」。
> - ⚠️ **不要为了对齐它去改 grpc-java 的命名** ⇒ 那是框架 API，改不了也不该改。
> - ⇒ **本表上文所有 `DEADLINE_EXCEEDED` 字样都是 grpc-java 的码名**；
>   与 Go 侧的 `DeadlineExceeded` 指**同一个 code** ⇒ **不是漂移**。

### 1.3 可观察后果

客户端发 `grpc-timeout: 100ms`、服务端配 2000ms，handler 需要 500ms：

| | Go | RAG |
|---|---|---|
| **客户端看到** | `DEADLINE_EXCEEDED` @~100ms | `DEADLINE_EXCEEDED` @~100ms（**gRPC 库替客户端兜的，与本条无关**） |
| **服务端 handler** | ctx 在 100ms 即 Done ⇒ **任何 ctx-aware 操作（SQL / 下游 RPC）中止**，在途查询被取消 | **〔修前〕继续跑到 2000ms**<br>🟢 **〔第①层已修〕超时 close 时刻 = min ⇒ 100ms 即 close**（`Δ_start` 会变）<br>🟢 **〔第②层 + 第三十三轮后〕SQL 路径**：`DeadlineState` 继承客户端 deadline ⇒ `statement_timeout` 也继承 ⇒ **在途查询在 deadline 时刻被取消**（`57014`）⇒ **handler 线程 ≈ 100ms 即结束**（与 Go 同款）<br>🔴 **〔仍未修〕但 close ≠ 取消在途工作** —— `ServerCall.close()` **不取消业务线程** ⇒ 业务线程**要等 SQL 的错误传回来**才知道失败（≈ deadline，不再是「跑满 2s」）<br>🔴 **〔非 SQL 路径未被覆盖〕** ⇒ 例如 Redis 写不绑 deadline ⇒ 跑满 Redis 自己的超时（⇒ 本域 `DRIFT-ARPC-010`） |
| **副作用** | 100ms 之后的写**不发生** | **〔修前〕100ms 之后的写照样落库**<br>🟢 **〔第②层 + 第三十三轮后 · SQL 路径〕** 在途查询在 deadline 时刻被 `statement_timeout` 取消 ⇒ **事务随异常回滚** ⇒ 写**不落库** ⇒ **与 Go 一致**<br>⚠️ **〔仍成立的缺口〕** ① **业务线程不被取消**（框架限制）⇒ 只能靠 SQL 错误回传感知 ② **非 SQL 路径**（Redis 等）不受 deadline 约束<br>⇒ ⚠️ 修的是「**何时 close**」+「**SQL 超时上限**」，**不是「业务线程会不会被 interrupt」** ⇒ **别把第①②层当成这条销账** |

⇒ **分类：`SEMANTIC MISMATCH`，Risk MEDIUM**（属于「客户端已放弃但服务端仍提交副作用」，
与本仓 B-1 那轮超时取消工作是同族问题）。
本条与 `admin-rpc` 的业务代码无关，**修法在 `service/common`** ⇒ 跨域，只报告不实施。

### 1.4 🔴 效力边界：**现有对拍工具在结构上抓不到这条**

- 客户端在两侧都是 @~100ms 拿到 `DEADLINE_EXCEEDED` ⇒ **任何客户端侧对拍都看不出差别**；
- `GrpcAdminDualPortRunner` / `diff.py` 都是客户端侧 ⇒ **加多少用例都测不出来**；
- 差别只在**服务端**：Go 取消了在途 SQL，RAG 没有。

⇒ **关闭本条需要的是 PG 侧探针，不是 gRPC 用例。** 最小做法复用现成范式：
照 `tools/difftest/exp-b1-timeout-cancel.sh` 的手法 —— 用另一个 psql 会话锁住一行
（`SELECT … FOR UPDATE`），让 handler 的 UPDATE 阻塞，然后发一个**短 deadline** 的请求，
最后**查 PG 侧那次查询是否被取消**（Go 应出现 cancel/短滞留；**RAG 修前跑满、修后也应在 deadline 时刻被取消**）。
⚠️ 造阻塞的脚本必须先证明「锁真的加上了」（`to_regclass()` 校验）——
`tools/difftest/README.md` 与 `Intentional_Deviations/_cross/signed-deviations.md` 都记了这个坑。

🟢 **2026-10-26 订正：本条写的「需要 PG 侧探针」这个前提，探针其实早已存在** ——
`tools/difftest/exp-grpc-admin-deadline-probe.sh`（**2026-10-24 就有**，17KB）**就是**它
（含短/长 deadline 双档 + `exit 1 ⟺ 短档下两侧 PG 查询存活时长差异 > 1s` + 配套 `GrpcAdminDeadlineProbe.java`）
⇒ **本条 §1.4 下面那句「现有工具抓不到」对 HTTP/gRPC 对拍仍成立，但对「PG 侧探针」不成立**
⇒ **真正的缺口是「探针没进常规编排」**（`run.sh` / `diff.py` 都不调它 ⇒ 它是手动取证工具 ⇒ 改坏了不会被自动抓到）
⇒ 已由 global 登记为 **E48**（落点 `tools/difftest/run.sh` ⇒ global 写权内 ⇒ A 类 · 下一轮做）。

> ⚠️ 本条的 Required Confirm 原文写的是「用 100ms / 2000ms / 5000ms 三档 deadline 对拍」。
> **那条 recipe 本身是无效的** —— 三档都只在客户端可见差异，测不出服务端行为。
> ⇒ 该 recipe 已订正，不要再照它跑。

## 2. ✅ UNRESOLVED-ARPC-003 定性 —— 读超时 **EQUIVALENT**（但一致是**巧合**）；连接超时**未验**

### 2.1 逐层证据链

| # | 层 | 事实 |
|---|---|---|
| 1 | Go `service/user/admin/rpc/etc/admin.yaml` | `BizRedis` 段**没有**任何 timeout 字段 |
| 2 | go-zero v1.10.2 `core/stores/redis/redisclientmanager.go:32-39` | 建 `red.Options` 时**只传** Addr/Username/Password/DB/MaxRetries/MinIdleConns/TLSConfig —— **三个 timeout 一个都没传** |
| 3 | go-redis v9 `options.go` `init()` | `DialTimeout = 5s`（`:329-330`）；`ReadTimeout` 的 `case 0: = 3 * time.Second` |
| 4 | RAG `admin-rpc/application.yml:48` | `spring.data.redis.timeout: 3s` |

⇒ **读命令超时：两侧都是 3s，一致。**

### 2.2 ⚠️ 一个会骗人的红鲱鱼：go-zero 里的 `readWriteTimeout`

`go-zero@v1.10.2/core/stores/redis/redis.go:27` 写着：

```go
readWriteTimeout     = 2 * time.Second
```

**全文件只有这一处 —— 声明了、从未被使用（死常量）。**
只看这一行会得出「Go 的 Redis 超时是 2s」的错误结论。
⇒ 真正的生效值在 go-redis 的 `init()` 里（**Read 3s / Dial 5s**）。
**这正是 §3.3.1「必须读到消费点，只贴声明行不算证据」的原型案例。**

### 2.3 一致性是**巧合**，没有出处也没有回归网

`spring.data.redis.timeout: 3s` 与 go-redis 的默认 `ReadTimeout=3s` **数值相同**，
但 RAG 侧这个值：
- **没有任何注释写明它来自 go-redis 的默认值**；
- go-zero 若升级 go-redis、默认值一变，RAG 会**静默分叉**；
- 仓内**没有一条测试**断言这个值。

⇒ **分类：读超时 EQUIVALENT（证据强度＝静态源码·双侧 + 依赖库源码），
但「一致」没有回归网。** 建议在配置里补一行注释写明出处（比补测试更便宜）。

### 2.4 仍存疑的一处：**连接超时**

| | Go | RAG |
|---|---|---|
| 连接（dial）超时 | **5s**（go-redis `DialTimeout` 默认） | **3s**（同一个 `spring.data.redis.timeout` 属性） |

Spring Data Redis 的 `timeout` 属性同时作用于 command 与 connect，
若成立 ⇒ **连接路径两侧不同（5s vs 3s）**。
⚠️ **未做运行时验证**（需要「连一个黑洞 IP 测拨号耗时」或直接查 Lettuce 的绑定点），
⇒ 记 **半定性**，不写成断言。

另：go-zero 显式 `maxRetries = 3`（`redisclientmanager.go:14`），
Lettuce/Spring 的重试语义与之**不一定等价**，本轮未查。

## 3. ✅ ARPC-002 的验证缺口已关闭 —— 补 `updateuser.ok` 对拍用例

**问题**：`GrpcAdminDualPortRunner` 的 18 个用例里 `updateUser` **只有 `notfound` 错误路径**，
而 DRIFT-ARPC-002（`UpdateUserResp.success`：Go=false / RAG=true）
**恰好只在成功响应里可见** ⇒ 那条漂移改了对拍也发现不了。

**改法**（`tools/difftest/` 共享资产，经用户指示后实施）：

| 文件 | 改动 |
|---|---|
| `exp-difftest-grpc-admin-errors.sh` | 新增 `[3/4]` 步骤：用 `psql` 在 `go_db.users` 与 `rag_db.users_user` 各造一行 probe user（**两侧 username / email / status / score 完全一致**，只有 uid 不同；`ON CONFLICT DO NOTHING` 幂等）；把两个 uid 以 `-Dgo.probe.uid` / `-Drag.probe.uid` 传给 runner |
| `GrpcAdminDualPortRunner.java` | 新增 `updateuser.ok` 用例；新增 `UpdateUserResp` 的归一化分支，**`success` 必须进归一化**（只比 `hasUser` 会漏掉它），uid 不比 |

⚠️ **为什么种数据那步的字段必须两侧一致**：
username / email 任一侧不同就会报 `payload_diff`，
把「种子数据没对齐」误报成**语义差异** —— 这是对拍工具最常见的自伤。

### 3.1 本轮实跑结果

| 命令 | 结果 |
|---|---|
| `run.sh --keep --serve-only` | 两套 admin/user 栈就绪（message-rpc 启动失败，**不在本域**） |
| `exp-difftest-grpc-admin-errors.sh --stacks-up` | **`cases=19 / code_mismatch=0 / payload_diff=0 / description_diff=5`**，`exit 0` |
| ↳ 新增 `updateuser.ok` | **`[MATCH]`** —— `success` 字段两侧同为 `false`，`hasUser`/username/email/status/score/extraKeys 全同 |
| `diff.py --scenario all`（种数据**之后**跑） | **`steps=39 / asserted=10 / byte_equal=29 / unexpected=0`**，`EXIT=0` |

**用例数 18 → 19**，其余数字与审计轮基线**逐项相同**（`description_diff=5` 仍是已签字的 DRIFT-ARPC-007）。

> ⚠️ **专门验了一次「种 probe user 会不会污染 HTTP 对拍基线」**：答案是不会。
> 两侧各加 1 行、`username`/`email`/`status`/`score` 完全对称 ⇒
> `admin.getuserlist` 的 `total` 两侧同时 +1，仍相等。
> ⚠️ 但这是**巧合性安全**，不是设计保证：若将来有人把两侧种子的字段改成不对称，
> 就会报出**假** `payload_diff`。种数据那步的注释里已写明这个要求。

### 3.2 🔴 效力边界（必须说清，别把这条当「ARPC-002 已完全闭环」）

`updateuser.ok` 这一条**证明的是**：`UpdateUserResp.success` 在**双侧活体**上同为 `false`。
⇒ **DRIFT-ARPC-002 的证据强度已从「Go 侧源码逐字 + RAG 侧真 PG 运行」
升到「双侧活体」，该条的验证缺口关闭。**

**但仍有两点要说清**：

1. **本轮没有为这条新用例跑变异测试。**（对比：DDL 时区守门那条**跑过**，
   删 `SET TIME ZONE 'UTC'` → 偏移 28800 秒 → 红。）
   本条的「可证伪」目前是**构造性推理**：`normalize()` 的返回值里**字面包含 `success=`**
   且本次为 `[MATCH]`，⇒ 把 `AdminServiceGrpcImpl` 的 `setSuccess(true)` 加回去
   必然让 RAG 侧归一化串变成 `success=true;…` 而 Go 侧仍是 `success=false;…`
   ⇒ `payload_diff=1`。**推理很直接，但没有实跑证据。**
2. **`success=false` 这个期望值的正确性来自 Go 源码**（`updateuserlogic.go:105-113` 不 set Success
   ⇒ proto3 默认 false），**不是**来自本次对拍 —— 对拍只能证明「两侧一致」，
   证不了「两侧都对」。这与 AGENTS.md §3.3 的告诫同型：变异测试与期望值来源两者缺一不可。

---

# 2026-10-23 实施轮（② Redis 超时 + ① ARPC-006 手写 span）

> **核对日期**：2026-10-23 23:4x–23:5x　**分支**：`align/users-admin`
> 起因：复核轮把 admin 域「还能改的漂移」列成 4 条，用户选定 **② → ①**。
> ⚠️ **本轮推翻了复核轮对 ① 的一处判断**（见 §2.1）。

## 1. ✅ ② UNRESOLVED-ARPC-003 的连接超时 —— 已实施

### 1.1 改了什么

| 文件 | 改动 |
|---|---|
| `admin-rpc/src/main/resources/application.yml` | 新增 `spring.data.redis.connect-timeout: 5s`；并给 `timeout: 3s` 补上**完整出处链注释**（此前只有裸数值） |
| `admin-api/src/main/resources/application.yml` | 同上（admin-api 也用 Redis 存黑名单） |

### 1.2 出处（写入 yml 注释，避免下一次又变成「裸数值」）

- Go `admin.yaml` **无** BizRedis 超时 → go-zero v1.10.2 `redisclientmanager.go:32-39` 建 `red.Options`
  时**三个 timeout 一个都没传** → go-redis/v9 `options.go init()` 填默认：
  **`DialTimeout = 5s`**（`:329-330`）、**`ReadTimeout = 3s`**（`switch opt.ReadTimeout { case 0: … }`）
- Boot 4 里 `connect-timeout` 是**独立字段**：`DataRedisProperties.connectTimeout`
  （模块 `spring-boot-data-redis` —— `RedisProperties` 已从 `spring-boot-autoconfigure` 拆出，
  这正是 §3.3「Boot 4 拆模块」那条纪律的现场）
- 不配它，Lettuce 会**回落到 `timeout`** ⇒ 连接超时跟着变成 3s，与 Go 的 5s 不一致

### 1.3 🔴 红鲱鱼（已写进注释）

go-zero `core/stores/redis/redis.go:27` 的 `readWriteTimeout = 2 * time.Second`
**全文件只有这一处 —— 死常量，从未被使用**。只看它会错判成「Go 是 2s」。

### 1.4 守门 + 变异实证

`AdminRedisTimeoutConfigTest`（**静态配置断言**，用 SnakeYAML 直接读 classpath 里的 yml）：

```
变异：删掉 connect-timeout: 5s
  → AssertionFailedError: 连接超时应为 5s… ==> expected: <5s> but was: <null>
✅ 文件已还原
```

⚠️ **效力边界（不夸大）**：这是**静态**断言 —— 证明「yml 里写了这两个值」，
**不证明**「Boot 4 真的把它们绑定到 Lettuce 客户端」。
后者需要运行期证据（例如把连接指向黑洞 IP、实测失败耗时落在 5s 而非 3s），**本轮未做**
⇒ 仍记 UNRESOLVED，见定性轮 §2.4。

## 2. ✅ ① DRIFT-ARPC-006 —— 已实施（本轮最大的改动）

### 2.1 🔴 先推翻一处旧判断

审计轮把本条写成「<b>需要动 `service/common` 的 OTel 装配（不在本组件）</b>」——
**过度保守，判断错误**。实测：

- `admin-rpc/pom.xml` **已**引 `opentelemetry-bom` + `opentelemetry-exporter-otlp`（API + SDK 都在编译期）
- `service/common` 自己的 `MqConsumer.java:81` 用的就是
  `GlobalOpenTelemetry.getTracer(tracerName)` —— **与 Go 的 `otel.Tracer(...)` 是同一个 API**

⇒ **完全可以在 admin-rpc 模块内完成**，一行共享代码都不用改。

### 2.2 🔴 更重要的纠正：是 **8 个 logic**，不是 13 个

本条台账原文写「Go 逐 Logic 打 `audit.*` 属性」，容易让人以为是 13 个 logic 全都有。
逐文件核（`grep -l "go.opentelemetry.io/otel"`）：

| 有 span（**8 个**） | span 名 | 属性 |
|---|---|---|
| `loginlogic.go` | `Action-Admin-Login` | `audit.admin_username = in.Username`（**原始入参**） |
| `createadminlogic.go` | `Action-Admin-CreateAdmin` | `audit.admin_username = username`（**trim 之后**） |
| `createinvitecodelogic.go` | `Action-Admin-CreateInviteCode` | `audit.operator_id` |
| `updateuserlogic.go` | `Action-Admin-UpdateUser` | `audit.operator_id` + `audit.target_uid` |
| `banuserlogic.go` | `Action-Admin-BanUser` | 同上 |
| `unbanuserlogic.go` | `Action-Admin-UnBanUser` | 同上 |
| `deleteuserlogic.go` | `Action-Admin-DeleteUser` | 同上 |
| `resetuserpasswordlogic.go` | `Action-Admin-ResetUserPassword` | 同上 |

**没有 span（5 个）**：`getselflogic.go`、`getuserlogic.go`、`getuserlistlogic.go`、
`logoutlogic.go`、`updateselflogic.go` —— 这 5 个文件**连 otel import 都没有**。

> **「13」是 attribute 条数，不是 logic 数**（1+1+1+2×5 = 13）。
> ⇒ 本轮**刻意不给那 5 个加 span** —— Go 没有的，RAG 也不凭空加，
> 否则 trace 里的 admin span 数会从 8 变 13，与真源不符。

### 2.3 改法

新增 `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminSpans.java`：`GlobalOpenTelemetry.getTracer("admin-rpc")` + `Scope` + `finally span.end()`。
`AdminDomainService` 的 **8 个** public 方法改成「薄壳 + 原体改名 `doXxx`」——
**原方法体一行未动**，只改签名处，diff 可读。

### 2.4 ⚠️ 与签字偏离 N-4 的关系（必须知情）

Go 的 `audit.operator_id` **恒为 0**（读 `ctx["uid"]`，而 claim 名是 `userId`），
RAG 传**真实 uid** —— 这是已签字的 **N-4**。
⇒ **补了 span 之后，这个差异第一次真正落到 trace 数据里**：
排障/对拍时两侧 `audit.operator_id` 会不同，**属预期差异，不是回归**。
`AdminSpanWiringTest` 直接断言 RAG 侧该属性 == 真实 operatorId，把这个决策钉死。

### 2.5 L4 差异（§3.2 留痕）

Go 的 `span.RecordError(err)` 分布在**具体错误分支**上（全 8 个 logic 共 16 处）；
本类在**异常逃出时统一** `recordException` + 置 ERROR 状态。
⇒ **IMPLEMENTATION DIFFERENCE**，不影响 span 名与属性。
（逐点复刻 16 处不在本轮范围；如需严格对齐应另立条目。）

> 🔴🟢 **2026-10-28 第二十二轮订正（`DRIFT-USERRPC-V-015` · 已修）**：本节原写的
> 「**`recordException` + 置 ERROR 状态**」⇒ ⇒ **「置 ERROR 状态」这一半是错的，已删**。
>
> | 项 | 证据 |
> |---|---|
> | **机制** | `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:631-656` `recordingSpan.RecordError` 的**方法注释原文**：<br>“SetStatus is required if the Status of the Span should be set to Error, **this method does not change the Span status**.”<br>⇒ 实现只 `addEvent("exception")` ⇒ **不碰 `s.status`** |
> | **Go 侧事实** | `service/user/admin/rpc/internal/logic/` 8 个 logic 共 **16 处 `span.RecordError`** ＋ **零处 `span.SetStatus`** ⇒ ⇒ Go 的 span 状态恒为 **`Unset`** |
> | **Go 腿实测** | otel-go v1.44.0 真跑：只 `RecordError` ⇒ 导出 span `status.Code=Unset` ＋ 1 条 `exception` 事件 |
> | **RAG 腿实测** | 新守门 `UserRpcSpanStatusGoParityTest`（5 条 · 自装 SDK ＋ 收集型 exporter ⇒ 断言**导出的 span 数据**）⇒ 修前 ① **红在状态断言**（实得 `ERROR`）· ④ 源扫红（命中 5 处）⇒ 修后 5 条全绿 · 变异（加回 `setStatus`）⇒ 2 红且红在目标断言 |
> | **⚠️ 不可混的那一条** | Go **共享层** `service/common/observability/observability.go:103-105` 是**显式三句**（含 `span.SetStatus(otelcodes.Error, msg)`）⇒ ⇒ **那条路径 Go 确实置 ERROR** ⇒ ⇒ RAG 共享层 `Observability.markSystemError`/`markTimeout` 照此实现是**对的** ⇒ ⇒ **共享层一行未动** |
> | **处置** | 删掉 `AdminSpans.traced` 里那处 `span.setStatus(StatusCode.ERROR, …)` ⇒ **保留 `recordException`**（**事件面本就与 Go 一致**）⇒ ⇒ 类 javadoc 同步订正 |
> | **留痕** | 「**统一在异常逃出时记录**」这个**结构**仍是 IMPLEMENTATION DIFFERENCE（逐点复刻 16 处不在本轮范围）⇒ ⇒ 但它**不再包含**「置 ERROR 状态」这一项 |
>
> ⇒ ⇒ 逐条 ⇒ [`../vs-common.md` §59](../vs-common.md) · [`../user/rpc.md` 第二十二轮](../user/rpc.md)。

### 2.6 守门：**装配层**证据 + 变异实证

`AdminSpanWiringTest`（3 条，真 PG）。它**不**断言「代码调用了 `AdminSpans.traced`」——
那只能证明代码写成了这样（§3.3）。它**自己装一个 SDK + 收集型 `SpanExporter`**
（不引 `opentelemetry-sdk-testing`，避免为测试扩大依赖面），
把 `GlobalOpenTelemetry` 指过去，然后断言**导出的 span**：

| 用例 | 断言 |
|---|---|
| `resetUserPasswordProducesGoSpanWithAttributes` | 导出名为 `Action-Admin-ResetUserPassword` 的 span；scope 名 == `admin-rpc`；`audit.operator_id`/`audit.target_uid` 都带上；`span.hasEnded()` |
| `methodsWithoutGoSpanMustNotProduceOne` | **负向**：Go 没打 span 的读路径**不许凭空产生** `Action-Admin-*` span |
| `spanNameConstantsMatchGoInventory` | 8 个 span 名常量与 Go 清单逐条相等 |

**变异实证**（证明不是假绿）：

```
变异：删掉 resetUserPassword 的 span.setAttribute(ATTR_TARGET_UID, uid)
  → AssertionFailedError: audit.target_uid 必须带上 ==> expected: <7701> but was: <null>
✅ 文件已还原
```

⇒ 「span 真的被导出」与「属性真的被带上」**双侧都有运行期证据**，
不是「代码里写了」的推测。

---

# 2026-10-23 验证轮：把「能验证的未知」验证掉

> **本轮目标**（用户指令：「把能验证的未知验证了，然后修台账」）：
> 把上一轮留下的 UNRESOLVED 里**在本域内可验证**的做掉，**做不掉的写清 recipe**，
> 然后把全台账里过期的「未修 / 仍未定」标记扫干净。
>
> **本轮做完的**：A（UNRESOLVED-ARPC-005 关闭）、B（Redis connect-timeout 绑定证据）。
> **本轮没做但已写清 recipe 的**：C（ARPC-002 的 PG 侧阻塞探针，见 §3）。

## 1. ✅ UNRESOLVED-ARPC-005 关闭 —— 双 rpc 同时对空库启动的并发实测

### 1.1 提问（原文）

> admin-rpc 与 user-rpc 都建 `users_user`，两侧同时对空库启动会不会撞？

### 1.2 RAG 侧实测：两进程真并发，但 advisory lock 严格串行

**证据源**：`/tmp/difftest/logs/rag-{admin,user}-rpc.log`（同一次 difftest 运行内两进程并发起跑）。

| 进程 | PID | `schema init starting` | `acquired advisory lock 8675309` | `all 4 scripts applied` | `schema init done` |
|---|---|---|---|---|---|
| `admin-rpc` | 91018 | `23:30:16.292` | **`17.610`** | `19.825` | `19.835` |
| `user-rpc` | 91017 | `23:30:16.632` | **`19.825`** | `20.978` | `20.985` |

**读法（这一步别读错）**：
1. 两个 `schema init starting` 相差 **340ms** ⇒ **确实并发**，不是排队启动；
2. 值得注意：`user-rpc` 的 init **发起得更早**（`16.632` vs `16.292`？—— 不，admin 更早 `16.292`），
   但**拿到锁的是 admin**（`17.610`），user 一直等到 `19.825`；
3. `user` 拿到锁的时刻 `19.825` **正好等于** admin `all 4 scripts applied` 的时刻
   ⇒ 锁是**首尾相接**交接的，中间没有重叠 ⇒ **没撞**。

⇒ **RAG 侧结论：并发建表不会撞，且这是「机制性保证」** ——
`pg_advisory_lock(8675309)` 让两个进程**在结构上**不可能同时执行 DDL。

### 1.3 Go 侧实测：4 轮真并发，4 轮全都没撞

**证据源**：`tools/difftest/exp-go-concurrent-automigrate.sh`（本轮修过等待逻辑，见 §1.5）。

| 轮次 | user-rpc 起 | admin-rpc 起 | 间隔 | 建表失败? | 建出的表 |
|---|---|---|---|---|---|
| 1 | `23:57:27.886` | `23:57:28.001` | 115ms | 两边都没有 | `admin_invites,admins,user_avatar_history,users` |
| 2 | `00:00:51.769` | `00:00:51.886` | 117ms | 两边都没有 | 同上 4 张 |
| 3 | `00:01:07.845` | `00:01:07.976` | 131ms | 两边都没有 | 同上 4 张 |
| 4 | `00:01:23.698` | `00:01:23.801` | 103ms | 两边都没有 | 同上 4 张 |

⇒ **Go 侧结论：4 轮真并发，两边都没报「数据表迁移失败」，4 张表全建出来。**

### 1.4 🔴 效力边界：**两侧性质不同，不可混谈**（这条必须一起记）

| | RAG 侧 | Go 侧 |
|---|---|---|
| 机制 | `pg_advisory_lock(8675309)` + 4 份 `CREATE … IF NOT EXISTS` | GORM `AutoMigrate`：`if !HasTable(value) { createTable(value) }` |
| 性质 | **机制性保证** —— 结构上不可能撞 | **check-then-act** —— **机制上仍有竞态窗口** |
| 本次观测 | 1 次，严格串行 | 4 次全未撞 |

GORM 的 `createTable` 发的是**不带 `IF NOT EXISTS`** 的 `CREATE TABLE ? (...)`
（只有 `createIndex` 是 `IF NOT EXISTS ? ON ?`）⇒ 两个进程若同时看到「表不存在」，
就会**同时发 CREATE** ⇒ 撞的是 PG 的 `pg_type_typname_nsp_index` 唯一约束。

⇒ **结论只能写成**：「两侧在实测中都没有撞；**RAG 侧有机制性保证，Go 侧没有**」。
⇒ **不得**写成「两侧等价安全」。这条差异若将来 Go 侧真撞上，根因就在 check-then-act。

### 1.5 🔴 顺带修掉的一个「假实验」陷阱（本轮最大的副产品）

原脚本等库用的是：

```bash
until docker exec gorace-pg pg_isready -U "$PG_USER" >/dev/null 2>&1; do sleep 1; done
```

**`pg_isready` 不带 `-d` 时，在 PG 的「初始化临时服务」阶段就已经返回 OK** ⇒
脚本会在数据库系统还没真正起来时就把两个 rpc 放出去。实测症状：

```
57P03 the database system is starting up
unexpected EOF
```

⇒ **两边都连库失败、一张表都没建出来**，判定列全是「?」。

🔴 **这个形态比「跑绿」更危险**：判定输出是「**两边都连库失败**」，
读起来像「**Go 侧连不上、结论待查**」，实际上**实验根本没发生** ——
既不能推出「Go 没问题」，也不能推出「Go 有问题」。
⇒ 与 AGENTS.md §3.3「跑绿 ≠ 结论对」同源：**实验失败时，它长得像结论**。

**修法**（本轮已改）：

```bash
until docker exec gorace-pg pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; do sleep 1; done
sleep 2
```

带 `-d "$PG_DB"` 等**目标库真的可连接**，再沉降 2s。

## 2. ✅ connect-timeout 的绑定证据：从「yml 里有键」升到「Spring 真绑进去了」

### 2.1 上一轮的断言只到「yml 里写了」

`AdminRedisTimeoutConfigTest` 第 1 条断言的是
`spring.data.redis.connect-timeout == "5s"` —— 它证明**yml 里有这个键**，
**不证明**属性名写对、relaxed binding 匹配得上、值能被塞进
`DataRedisProperties.connectTimeout`。这三件事任何一件错了，
**Spring 都会静默保持默认值、不报错**（§3.3 的典型形态）。

### 2.2 本轮补的第 2 条：走 Spring Boot 真实的 `Binder`

新增用例 `springBootBinderConsumesConnectTimeout`：

```java
MapConfigurationPropertySource src =
        new MapConfigurationPropertySource(flatten(load("application.yml")));
DataRedisProperties props = new Binder(src)
        .bind("spring.data.redis", DataRedisProperties.class)
        .orElseThrow(() -> new AssertionError("spring.data.redis 绑不上 —— "
                + "若属性名写错，Spring 会静默用默认值且不报错"));

assertEquals(Duration.ofSeconds(5), props.getConnectTimeout(), ...);
assertEquals(Duration.ofSeconds(3), props.getTimeout(), ...);
```

**为什么必须用真 `Binder` 而不是自己解析 yml**：
relaxed binding 规则（`connect-timeout` → `connectTimeout`）是 Spring 自己实现的，
自己解析就等于**把自己的期望当成被验证对象**。

**顺带处理的一个细节**：yml 里 `host`/`port`/`password` 是 `${REDIS_PORT:6379}` 这种
**占位符形态**，不解析就没法把 `port` 绑成 `int` ⇒ 辅助方法 `resolvePlaceholder`
按 Spring 的语义取默认值。

### 2.3 ⚠️ 效力边界（本节写于本轮；**末层已于次日补上**）

| 层 | 状态 |
|---|---|
| yml 里写了这两个值 | ✅ 已证（第 1 条断言） |
| Spring `Binder` 真把它们绑进 `DataRedisProperties` | ✅ **已证（本轮第 2 条）** |
| **Boot 把 `connectTimeout` 接到 Lettuce 的 `SocketOptions`** | ✅ **已证（2026-10-24 验证轮续 §2.2）** |
| **Lettuce 客户端在 socket 层真按 5s 超时** | ✅ **已证（2026-10-24 验证轮续 §2.3，实测 5220ms）** |

> ⚠️ **2026-10-24 更新**：本节原写「末层仍未证 ⇒ 不得写『Redis 超时已完全对齐』」。
> **该限制已解除** —— 末层（socket 层）已用黑洞 IP 实测补上，
> 且顺带发现了「**两个超时值互相遮蔽**」这条重要事实（验证轮续 §2.4）。
> ⇒ 现在的准确表述是：
> **「配置层（yml → Binder → Boot → Lettuce ClientOptions → socket 实测）已全链对齐，
>   但故障时的用户可见时延由 3s 的命令超时主导，不是 5s 的连接超时。」**

## 3. ✅ C 项（ARPC-002 的 PG 侧阻塞探针）：**已于 2026-10-24 做掉**

> ⚠️ **本节原写「预算内跑不完，只留 recipe」** —— 那是 2026-10-23 验证轮当时的结论。
> **2026-10-24 验证轮续 §1 已把它跑完**：recipe 可行，而且拿到了决定性数据。
>
> ⇒ **完整实验设计 / 实测数据 / 效力边界 ⇒ 验证轮续 §1**；
> ⇒ 工具 ⇒ `tools/difftest/GrpcAdminDeadlineProbe.java` + `exp-grpc-admin-deadline-probe.sh`。

### 3.1 原 recipe（保留存档 —— 它是**可复现**的）

**可复现 recipe**（现已跑通）：

1. 起 RAG `admin-rpc` + Go `admin-rpc`（`tools/difftest/run.sh --serve-only --keep`）；
2. 用**第三个** psql 会话：`BEGIN; LOCK TABLE <admin 表> IN ACCESS EXCLUSIVE MODE;`（**不提交**）；
3. 对两侧各发一个 **client deadline = 1000ms** 的 `Login` 请求（**直接打 gRPC 端口**，不经 HTTP 层）；
4. 比对**两件事**：
   a. 响应时刻（两侧**必然相同** ⇒ 这条量不出差异，**别拿它判**）；
   b. **PG 侧那条阻塞查询的存活时长**（主仪器 = **PG 服务端日志**里的
      `canceling statement due to user request` + `STATEMENT:` 行）。
5. **期望差异**：Go 侧查询随客户端一起死（Δ_end ≈ 0）；
   RAG 侧查询在客户端返回后**继续占着 PG ~1.1s**。

⚠️ **两条踩过的坑（都写在验证轮续 §1.2 里）**：
① **不能用 `SELECT … FOR UPDATE` 行锁**（普通 SELECT 走 MVCC，不会被行锁挡住）；
② **不能用 `pg_stat_activity` 轮询采样**（每次轮询起一个 psql，~200ms 开销，采样窗口比事件还粗，会直接漏掉）——
   **主仪器必须是 PG 的服务端日志**。

## 4. ✅ 顺手修掉的一类「事实错误注释」：GORM 的**默认表名**从来不是 `admin_admin`

**核对日期：2026-10-23（验证轮）**

审计轮 §5 第 3 项已经发现「实体 javadoc 里的表名说法是错的」，并订正了
`AdminEntity` / `AdminInviteEntity`。**本轮把同一类错误在另外 3 个文件里找齐并修掉** ——
它们都写着同一句话：**「GORM 默认表名 X」**。

### 4.1 事实核对（逐字读 Go 源码）

```go
// service/user/admin/rpc/internal/model/data_model.go
func (Admin) TableName() string      { return "admins" }         // :16-18
func (AdminInvite) TableName() string { return "admin_invites" }  // :31-33
func (User) TableName() string        { return "users" }          // :48-50
```

⇒ Go 的表名**既不是 `admin_admin`，也不是 GORM 的默认名** —— GORM 的默认名是
`admins`（结构体名 Admin → admins），Go 只是**显式覆盖**成了同一个值。
而 RAG 侧叫 `admin_admin` / `admin_invite` / `users_user` ——
**两侧物理上不共用 schema**，这是 users 域的「表名加前缀」约定。

### 4.2 修掉的三处 + 一处

| 文件 | 原文 | 改成 |
|---|---|---|
| `03_create_admin.sql:14` | `Admin struct（GORM 默认表名 admin_admin）` | 写明 Go `TableName()` 返回 `admins`、本脚本建 `admin_admin`，并点明原说法是**错的** |
| `03_create_admin.sql:33` | `Admin struct（GORM 默认表名 admin_admin）` | 同上 |
| `04_create_admin_invite.sql:8` | `AdminInvite struct（GORM 默认表名 admin_invite）` | 同上（Go 是 `admin_invites`） |
| `04_create_admin_invite.sql:15` | `AdminInvite struct（GORM 默认表名 admin_invite）` | 同上 |
| `domain/UserEntity.java:75`（`@TableName("users_user")`） | `User struct（GORM 默认表名 user_user，HUMAN_ONLY.MD 2026-10-22 改名为 users_user）` | 改为与 `AdminEntity` **同款**的写法：Go `TableName()` 返回 `users`、本实体映射 `users_user` |

### 4.3 为什么值得修（这不是洁癖）

「GORM 默认表名」这个说法会让人**以为两侧物理表同名**，
于是拿表名去对 DDL、拿 `admins` 去查 PG 表 ⇒ **查不到**，
或者更糟：以为两侧同表而**直接把 Go 的 DDL 贴过来**，
从而把 RAG 侧的 `admin_admin` 改没了。
⇒ 这是一句**会误导后续动作**的错误注释，不只是措辞问题。

⚠️ **顺带订正**：`_shared.md` §5 的 U-5 行原本写的是
「`03_create_admin.sql:14`、`04_create_admin_invite.sql:8/15` 的注释写错了 Go 的表名」——
本轮把 `03` 的**第 29 行**（第二处）也一并修了，那一行原文没被点出来。

## 5. ⚠️ 跨域观察（**只报告，不改**）：两域各有一份**逻辑完全相同**的 `JsonTextTypeHandler`

**核对日期：2026-10-23**

| | 位置 | 归属域 |
|---|---|---|
| 副本 1 | `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/mapper/JsonTextTypeHandler.java` | **users/admin（本域）** |
| 副本 2 | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/JsonTextTypeHandler.java` | users/user |

**两份的 `setNonNullParameter` / `getNullableResult` / `toJson` / `parse` 逐行相同**，
只有包名与 javadoc 不同（副本 2 是从 `JsonbTypeHandler` 改名而来，
javadoc 里记了改名理由与 Go 出处）。

⇒ **理想形态**是上提到 `service/users/users-common`（两个域都依赖它），
但那要改**两个域**的实体引用（`@TableField(typeHandler = ...)` 的 import）
⇒ **越本域边界**，按 §5.3「一域一 worktree、只写自己域」**本轮不改**，
仅记录供合并时裁决。

⚠️ **注意**：这不是「同一个类被复制到两个仓库」那种无害重复 ——
它是**同一段行为契约写了两遍**。若将来 Go 侧的 `serializer:json` 行为变了，
两份 javadoc 里的 Go 出处引用**要改两处**，漏一处就会留下一句假话。
（与 Pitfalls P-3「同一句话错了要一次改三处」同型。）

## 6. 🔴 新发现（本域已修，其余域只报告）：**RAG 侧源文件自身含 U+FFFD 替换字符**

**核对日期：2026-10-23（验证轮）**

### 6.1 现象

扫全仓（排除 `.git` / `target`）找 UTF-8 替换字符 `U+FFFD`（`EF BF BD`）：

```
含 U+FFFD 的文件数: 13    总替换字符数: 36
```

〔**2026-10-24 复核订正**：原写「15 / 42」—— 本域清零后（2 处 → 0），
总数变成 **13 文件 / 36 字符**；**下方 §6.2 的 13 行表逐条仍与实测一致**（合计 36），未动。〕

**本域的两处**（已修，本域现已 **0 处**）：

| 文件 | 原文（损坏） | 修成 | 性质 |
|---|---|---|---|
| `service/users/users-common/src/main/resources/db/03_create_admin.sql:10` | `共用库必然出⟪U+FFFD×3⟫（实测见…）` | `共用库必然出现（…）` | SQL **注释**，功能无影响 |
| `docs/DRIFT/users/admin/rpc.md:2043` | `**双⟪U+FFFD×3⟫都有运行期证据**` | `**双侧都有运行期证据**` | 台账正文 |

⚠️ **两处都是「一个 3 字节 UTF-8 字符被替换成 3 个 U+FFFD」** ——
这是**有损解码**的典型形态（原始字节被丢弃、每个坏字节各补一个替换符），
不是「有人手滑打错字」。

⚠️ **本表里用 `⟪U+FFFD×3⟫` 记号表示损坏样本，刻意不把真的替换字符写进本文件** ——
否则将来加「全仓字节合法性扫描」（见 §6.5）时，**本文件自己会变成假阳性**，
守门网会被一条「文档里引用了损坏样本」的记录挡住。
⇒ **记号约定**：凡要在文档里展示损坏字节，一律用 `⟪U+FFFD×n⟫` 之类的可见记号。

### 6.2 其余 13 处（**跨域，只报告，不改**）

| 文件 | 替换字符数 |
|---|---|
| `component-semantic-verification/springboot-semantic-model.md` | 5 |
| `tools/errmsg-probe/README.md` | 3 |
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcLoginNullStatusReadsAsZeroTest.java` | 3 |
| `service/message/message-api/src/test/java/com/rag/message/message/api/GoStyleGrpcClientBreakerE2ETest.java` | 3 |
| `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/domain/LikeOutboxEventEntity.java` | 3 |
| `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/domain/HeavyKeeper.java` | 3 |
| `service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/service/FavoriteFactSemanticsTest.java` | 3 |
| `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/config/FavoriteRpcLabels.java` | 3 |
| `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentAuditPublisher.java` | 3 |
| `service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/config/Jackson2ObjectMapperConfiguration.java` | 2 |
| `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteRpcSupport.java` | 2 |
| `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteItemMapper.java` | 2 |
| `docs/Pitfalls/users/user/rpc.md` | 1 |

⚠️ **其中 8 处在 `src/main/java` 的生产代码里**（like / hot / favorite / comment 四域）
⇒ 需各自域的 owner 判定「是注释/字符串字面量还是会影响行为」。
若落在**用户可见的字符串字面量**里，就是 §3.3 意义上的**行为漂移**，不能只当排版问题。

### 6.3 与 DRIFT-ARPC-007 的关系（**同族、不同机制**）

| | DRIFT-ARPC-007 | 本条 |
|---|---|---|
| 损坏发生在 | **Go 真源**的字面量 | **RAG 侧**的源文件 |
| 表现 | Go 吐乱码、RAG 吐正常中文 | RAG 源码里直接有 `U+FFFD` |
| 可观察 | **wire 层可见** | 大多在**注释**里 ⇒ 不可观察 |

⇒ 两者是**同一条流水线**（从 Go 抄文本 / 从终端粘文本）的两次翻车，
但**危害面不同**：ARPC-007 会改行为，本条多数只是「源文件里有乱码」。

### 6.4 为什么值得专门扫一遍（不是洁癖）

1. **`U+FFFD` 是有损解码的指纹** —— 它的存在说明**某一步搬运过程中丢过字节**。
   同一批搬运里，**可能还有丢字节但没变成 `U+FFFD` 的地方**（例如丢在不可见位置）。
   ⇒ 扫 `U+FFFD` 是**定位「哪里出过事」的探针**，不是「把乱码改好看」。
2. 一句 Java 源码里的乱码若落在**字符串字面量**里 ⇒ 线上吐出的就是乱码，
   **和 ARPC-007 的可观察形态一模一样**（只是方向反了）。
3. `check-ddl-static.py` / `check-proto-static.sh` 都**查不出**这个 ——
   它们查列类型和字段号，**不查字节合法性**。

### 6.5 留下的守门网（建议，**未实施**）

一条全仓字节合法性扫描（找出所有含 `U+FFFD` 的**非 `target/`** 文件即失败），
挂进 `tools/difftest/`。⚠️ **本轮未做** —— 它会**立刻让构建变红**（现有 13 处跨域），
属于需要各域先修完才能加的守门 ⇒ 记录备查。

## 7. 本轮实测数字

| 项 | 数字 |
|---|---|
| **admin-rpc 全量** | **98 / 0 failures / 0 errors / 0 skipped**（16 个测试类；上一轮 97 ⇒ 本轮 +1） |
| **admin-api 全量** | **121 / 0 failures / 0 errors / 0 skipped**（11 个测试类；未变） |
| `AdminRedisTimeoutConfigTest` | **2 条**（新增 Binder 绑定用例），`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` |
| Go 并发建表 | **4 轮全绿**（两边都没报建表失败、4 张表全建出） |
| RAG 并发建表 | **1 轮**（两进程严格串行，锁 `8675309`） |
| 本轮改动文件 | 8 个（台账 3 + 代码 3 + DDL 2 + 工具脚本 1，见下） |

> ⚠️ **`skipped=0` 是硬门槛**（AGENTS.md §3.3）：本轮**逐个测试类核过** `Skipped` 数，
> 确认没有 `@EnabledIfDockerAvailable` 之类的整类静默跳过。
> 上表里 admin-rpc / admin-api 的数字均由 `target/surefire-reports/*.txt` 汇总而来，
> **不是**从 Maven 控制台摘要抄的（控制台摘要会因多模块聚合而对不上单个模块）。

## 8. 本轮新增/修改的文件

| 文件 | 性质 |
|---|---|
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRedisTimeoutConfigTest.java` | **+1 条**：用真 `Binder` 证明 `connect-timeout` 被绑进 `DataRedisProperties` |
| `tools/difftest/exp-go-concurrent-automigrate.sh` | **修等待逻辑**：等库加 `-d "$PG_DB"` + 沉降 2s（原写法让实验根本没发生） |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/UserEntity.java` | **订正 javadoc**：「GORM 默认表名 user_user」是错的（Go 返回 `users`） |
| `service/users/users-common/src/main/resources/db/03_create_admin.sql` | **订正 2 处注释**（`:14` / `:33`）：Go 表名是 `admins`，不是 `admin_admin`；**另修 1 处 U+FFFD 乱码**（`:10`「共用库必然出现」） |
| `service/users/users-common/src/main/resources/db/04_create_admin_invite.sql` | **订正 2 处注释**（`:8` / `:15`）：Go 表名是 `admin_invites` |
| `docs/DRIFT/users/admin/rpc.md` | 本文件：扫 8+4 处过期标记、关闭 UNRESOLVED-ARPC-005、新增验证轮 §1–§8、**修 1 处 U+FFFD** |
| `docs/DRIFT/users/admin/_shared.md` | §5 汇总表：U-3 / U-4 / U-5 / A-2 四行状态订正 |
| `docs/Pitfalls/users/admin/rpc.md` | **新增 P-10**（实验失败时它长得像结论）、**P-11**（yml 有键 ≠ 属性被绑进 POJO） |

> ⚠️ **共享文件一律未动**：`AGENTS.md`、三个 `_INDEX.md`、`docs/DRIFT/_MANIFEST.json`
> 本轮**未改**（按 §5.3「共享索引合并后统一更新」）。
> ⚠️ **未改 `docs/DRIFT/users/_shared.md` 与 `docs/Pitfalls/users/_shared.md`** ——
> 这两个是 users 域**共享**文件，`align/users-user` 分支**正在改它们**
> ⇒ 本 worker 不写，避免并发互撞。

---

# 2026-10-24 验证轮续：ARPC-002 的**运行期**证据 + Lettuce socket 层

> 本轮把上一轮列的两条「未证」做掉：
> ① **C 项** —— ARPC-002 的 PG 侧阻塞探针（原写「预算内跑不完，只留 recipe」）；
> ② **Lettuce socket 层** —— connect-timeout 是否真在 socket 层生效。
>
> ⚠️ 两项的结论**都改变了本台账此前的措辞**，逐条标了「原结论 → 新结论」。

## 1. ✅ UNRESOLVED-ARPC-002 拿到**运行期**证据 —— CONFIRMED SEMANTIC MISMATCH 坐实

### 1.1 为什么**客户端耗时**量不出差异（这是本实验的第一个坑）

两侧的**客户端**都会在**自己的 deadline** 返回：

| | 客户端返回时刻由谁决定 |
|---|---|
| Go | 客户端 deadline（1000ms） |
| RAG | 客户端 deadline（1000ms）——**服务端那 2s 完全没参与** |

⇒ **客户端耗时两侧必然相同**（实测都是 ~1000ms），
拿它判这条会得出「两侧一致」的错误结论。
**可观察的差异在服务端**（PG 上那条阻塞查询活了多久）。

### 1.2 实验设计

| 要素 | 取法 | 为什么 |
|---|---|---|
| 阻塞源 | 后台 psql 事务 `LOCK TABLE <admin 表> IN ACCESS EXCLUSIVE MODE` | ⚠️ **不能用 `SELECT … FOR UPDATE` 行锁** —— 普通 SELECT 走 MVCC 快照读，**不会被行锁挡住**（`exp-b1-timeout-cancel.sh` 的注释里记了这个坑：第一版用行锁，两侧 login 都在 150ms 内成功返回，实验毫无意义）。⚠️ 每个 (库,表) 必须**独立 psql 进程** —— 塞进同一个事务会让一张表的 LOCK 报错把整个事务 abort |
| 表名 | Go `admins` / RAG `admin_admin` | Go 的 `TableName()` 返回 `admins`（`data_model.go:16-18`），RAG 是 users 域的加前缀约定 |
| 客户端 | `GrpcAdminDeadlineProbe`（新工具），**直接打 gRPC 端口**（Go 18081 / RAG 58081），带可配 deadline | 走 HTTP 层会多一跳 admin-api，把 gRPC 的 deadline 语义抹掉 |
| RPC | `Login` | 它先查库再验密码 ⇒ 查库这一步会被锁挡住 |
| **主仪器** | **PG 服务端日志**里的 `canceling statement due to user request` + `STATEMENT:` 行 | ⚠️ 第一版用 `pg_stat_activity` 轮询采样 —— 每次轮询要起一个 psql 进程（~200ms 开销），**采样窗口比事件本身还粗**，直接漏掉了 RAG 侧那条查询（当时读成「实验没生效」，其实是采样太粗）。PG 的错误日志给出**取消的确切时刻 + 语句原文**，而且「**没有日志条目**」本身就是强证据（= 那条查询根本没到 PG） |
| 指标 | `Δ_start` = 取消时刻 − RPC 发起时刻<br>`Δ_end` = 取消时刻 − **客户端返回**时刻 | `Δ_end` 是最锐利的那个：**客户端走了之后，查询还在 PG 上占着多久** |

⚠️ 探针必须报 **RPC 的绝对墙钟起止**（`rpc_start_ms` / `rpc_end_ms`）——
相对「进程启动时刻」的偏移里混着 JVM 启动时间（每次几百 ms 且每次都不同），直接比会得出错误的差值。

### 1.3 实测数据（2026-10-24 00:44~00:46，两套 admin-rpc 同时在跑）

| 档位 | 侧 | `Δ_start` 查询存活 | `Δ_end` 客户端返回后仍占 PG | 客户端看到的 desc |
|---|---|---|---|---|
| **A** 客户端 deadline **1000ms** | **Go** | **+1005ms** | **+0ms** | `CallOptions deadline exceeded`（客户端自己的） |
| **A** 客户端 deadline **1000ms** | **RAG** | **+2117ms**　**〔修前实测值〕** | **+1111ms**　**〔修前实测值〕** | `CallOptions deadline exceeded`（客户端自己的） |
| **B** 客户端 deadline **5000ms**（对照） | **Go** | +2113ms | −3ms | `context deadline exceeded` |
| **B** 客户端 deadline **5000ms**（对照） | **RAG** | +2124ms | +3ms | `rpc timeout after 2000ms`　**〔2026-10-24 修前实测值，见下方销账补记〕** |

> #### ✅ 2026-10-26 销账补记：档位 B 的 **desc 差异已消失**（`align/global` 修完，**已提交并推双远端，待合回 `do-my-own-gh`**）
>
> | | desc（客户端看到） |
> |---|---|
> | Go | `context deadline exceeded`（`service/common/observability/rpc.go:68-71`：`status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())`） |
> | RAG **修前** | `rpc timeout after 2000ms` |
> | RAG **修后** | **`context deadline exceeded`** —— 与 Go **逐字相同** |
>
> - **修法**：`align/global` 在 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` 里把超时 description
>   换成常量 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`；
>   配套守门 `RpcInterceptorTimeoutDescriptionGateTest`（真 Netty server + 真客户端，断言**网络上真正传过来的字节**）
>   ＋ 双侧活体探针 `tools/semantic-probe/rpc-timeout/`（Go 半边 `replace` 引用冻结仓真源，不改冻结仓一字节）。
> - **状态（2026-10-26 实查）**：`align/global` = `22a9111`（含 `49808ad` 主改动 + `22a9111` 台账订正），
>   `align/global` / `origin/align/global` / `upstream/align/global` **三处指向一致** ⇒ 修复**已提交、已推双远端**。
>   🟢 **2026-10-26 第五十轮双向合流已合回 `do-my-own-gh`** ⇒ **本表已改记「已销账」**（见 §11）。
> - 📌 **合回与否曾于 2026-10-26 由用户 Jason 拍板「暂不合回」** ⇒ ⚠️ **该拍板已被后续的第五十轮双向合流推翻**
>   ⇒ ⇒ **本条现为「已销账」**（不是「忘了合」）；
>   🟢 **主仓代码现在已是新串**（`RpcInterceptor` 的超时 description 已生效）⇒ ⇒ **基于主仓的验证现在看得到修复了**。
> - ⚠️ **本条只销「档位 B 的 desc」这一格**，**不销 ARPC-002 的本体** —— 见下方「⚠️ 必须一起读」。
>
> **⚠️ 必须一起读：这条差异分三层，global 已修两层，剩一层。**
>
> | 层 | 内容 | 状态（2026-10-26 实查） |
> |---|---|---|
> | **① description 文案** | 超时响应给客户端的 `status.description`（**档位 B** 那一格） | ✅ **已修** —— `RpcInterceptor` 换用 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`（`align/global` @ `22a9111`，**已推双远端，2026-10-26 已合回主干**） |
> | **② 期限计算**（**本条原问题本体**） | 服务端预算**不 min 客户端 deadline** ⇒ 服务端干等满自己的 2s（**档位 A** 的 `Δ_start = +2117ms`） | ✅ **已修** —— `effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`，两处消费点（`deadlineEpochMs` + 超时调度器延时）都用它（`align/global` @ `af416d9`，**已推双远端，待合回**）<br>⇒ 取值点 `io.grpc.Context.current().getDeadline()`（grpc-java 服务端**实测可用**：客户端 1s ⇒ 服务端读到 **932ms** 剩余） |
> | **③ 资源取消** | 客户端放弃后，**PG 上的查询仍在跑**（**档位 A** 的 `Δ_end = +1111ms`） | 🟡 **可观察面已被第 ② 层 + 第三十三轮消掉；机制层仍未做** —— 🔴 **先纠正一个测量口径（2026-10-26 复核，本行原表述把「`Δ_end`」当成「业务线程是否被取消」的度量，那是错的）**：<br>`Δ_end` = **PG 取消时刻 − 客户端返回时刻**，主仪器是 **PG 服务端日志**的 `canceling statement due to user request`（`tools/difftest/exp-grpc-admin-deadline-probe.sh` 的 `pg_log_cancel_events` 抓的就是这条）⇒ **它由 `statement_timeout` 决定** ⇒ **与「业务线程有没有被 interrupt」无关**<br>⇒ ⇒ **第 ③ 层「未修」并不阻止 `Δ_end` 归零**；**两道合起来正好把它压到 ≈0**：<br>① **第 ② 层**（`af416d9`）⇒ 服务端业务线程的 `DeadlineState`（`RpcInterceptor` 的 `onHalfClose()` 绑定 `deadlineEpochMs`）= `min(客户端, 服务端)` ⇒ **SQL 超时上限继承客户端 deadline** ⇒ `Δ_end` 从 **+1111ms** 降到 **≈ +80ms**（剩下的就是秒级向上取整的残差）<br>② **第三十三轮**（`d20b377`，`align/global`）⇒ **毫秒精度**（pgjdbc ≥ 42.7 的 `setQueryTimeoutMs(long)`，`remaining` 毫秒原样传下去）⇒ 残差也消掉 ⇒ **取消时刻 == deadline** ⇒ **`Δ_end` ≈ 0** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】<br>❌ **仍未做的是「机制层」**：grpc-java 的 `ServerCall.close()` **不取消业务线程**（**与 T-14 同根因**：框架限制）⇒ 业务线程要等 SQL 的 `57014` 错误传回来才知道失败（**E46** 的「查询级检查」已把这条尾巴从「跑满 2s」压到「≈ deadline」）<br>🔴 **本域实证：非 SQL 路径不被这层覆盖** ⇒ **`DRIFT-ARPC-010`**（admin `logout` 的 Redis 写**不绑 deadline** ⇒ 跑满 Redis 自己的 3s command timeout；Go 侧 `SetexCtx(l.ctx, …)` 在 deadline 时刻立即失败）⇒ **这是第 ③ 层在本域真正的残余**<br>⚠️ **E38② 维持「另立项」**（架构级：handler 挪受控 executor + 超时 interrupt）⇒ **E46 已从 E38② 拆出独立成条**，Jason 拍板「拆出来单独做」，**第三十三轮已落地**<br>🔴 **E46 对第 ③ 层的两处修正（global 实测，本表原先没有）**：① **量级被高估** —— SQL 侧**已有 B-1 缓解**（10 个 rpc 组件全部注册了 `DeadlineQueryTimeoutInjector`）⇒ 真正剩下的**只有 ≤1s 窗口**，不是「SQL 完全不受 deadline 约束」；② **「handler 挪受控 executor + 超时 interrupt」单独做不够** —— pgjdbc **不响应线程中断**（实测：interrupt 后等锁查询照常跑 **11014ms**）⇒ 必须配「**取消正在跑的 JDBC 语句**」 |
>
> ⇒ **本条（`UNRESOLVED-ARPC-002`）的定性要分两层读 —— 2026-10-26 复核后改判（🔴 本行原表述有误，见下）**
>
> | 维度 | 状态 |
> |---|---|
> | **子项「服务端是否 min 客户端 deadline」** | ✅ **已答「是」且已修**（第 ② 层，`af416d9`） |
> | **原「CONFIRMED SEMANTIC MISMATCH」的观察依据**（`Δ_end = +1111ms` vs `0ms`） | 🔴 **按机制推导已失效** —— 第 ② 层 + 第三十三轮 ⇒ `Δ_end` ≈ 0 ⇒ **原来支撑「CONFIRMED」的那一格差值不再成立** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上观察依据已归零**（`align/global` 上实测 **22/18/12ms**）⇒ **这句现在成立**（合回前实测 pre-E62 为 `Δ_end=+833ms`）⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ global **E48**）】 |
> | **机制层（业务线程不被取消）** | ❌ **仍未做**（框架限制，`ServerCall.close()` 不 interrupt 业务线程）⇒ **这一层本身不再是「可观察差异」**（SQL 路径已由 E46 对齐到 ≈ deadline） |
> | **非 SQL 路径** | 🔴 **仍有差** ⇒ 已在 **`DRIFT-ARPC-010`** 单独登记（admin `logout` 的 Redis 写不绑 deadline） |
>
> ⚠️ **本行原表述（2026-10-26 复核前）**：「可观察的差距（`Δ_end = +1111ms`）来自第 ③ 层，而第 ③ 层未修 ⇒ 定性不变」
> **⇒ 这句是错的** —— 它把 `Δ_end` 当成了「业务线程有没有被取消」的度量；
> **实际上 `Δ_end` 测的是「SQL 超时上限有没有继承客户端 deadline」**（主仪器 = PG 取消时刻）⇒
> **第 ② 层一改它就跟着变，第 ③ 层「未修」拦不住它归零。**
>
> ⚠️ **⚠️ 但这是「机制推导」，不是「活体实测」** —— 探针 `exp-grpc-admin-deadline-probe.sh`
> **未进常规编排**（global **E48**：缺口 = 「没进编排」，不是「缺探针」）⇒ **修后值待实测**。
> ⇒ **实测前本条既不得销账、也不得按「已闭环」记账** ⇒ 状态写「**机制层未闭环 · 观察依据待复测**」。
> ⇒ **复测的前置条件**：① `align/global` 的 `af416d9` + `d20b377` 合回主仓 ⇒ 🟢 **已满足**（2026-10-26 第五十轮双向合流）② 探针接进 `run.sh`（E48 的三步：补 `DIFTEST_PORT_OFFSET` → 接编排 → 一次变异证明它真被调）⇒ 🟢 **已满足**（global 第三十九~四十轮已做，E48 守门 37 条 + 变异 20 条全命中）⇒ ⇒ **两个前置条件现已全部满足 ⇒ 本条可在主干上复测**（探针：`tools/difftest/exp-grpc-admin-deadline-probe.sh`，**本域资产**）。
>
> ⚠️ ⇒ 上表两格都标了「修前实测值」——**它们是 2026-10-24 的活体测量，价值正在于它是「修前」的原始快照**；
> **修后的值待实测**（探针已就位，见 §1.4 的边界块）。
> ⚠️ **别把「desc 对齐了」或「期限算对了」读成「ARPC-002 销账了」** —— 整条销账要等**实测**确认 `Δ_end` 归零，**且**非 SQL 路径（`DRIFT-ARPC-010`）另行闭环。

### 1.4 结论

> 🔴 **2026-10-26 订正：本节全部数字是「修前」的活体测量（2026-10-24 快照）** ——
> **第②层（期限计算，`af416d9`）+ 第三十三轮（毫秒精度，`d20b377`）落地后，档位 A 的 `Δ_end` 按机制推导 ≈ 0** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】
> （口径纠正见 §1.3 的三层表第③层行：**`Δ_end` 由 `statement_timeout` 决定，与业务线程是否被取消无关**）
> ⇒ **本节结论是「修前状态」，不是「现状」** ⇒ 修后值**待实测**（探针未进编排 ⇒ global **E48**）。

1. **档位 A（客户端 1000ms < 服务端 2s）**：
   - **Go**：查询在 **1005ms** 被取消 ⇒ `context.WithTimeout(ctx, timeout)` **继承了客户端 deadline**（`rpc.go:38`），有效期限 = min(1000ms, 2s) = 1000ms。
     `Δ_end = 0ms` ⇒ **客户端一走，查询同步被 CancelRequest 掐掉**。
   - **RAG**：查询活到 **2117ms**（= 它自己的 2s），
     而客户端在 1006ms 就返回了 ⇒ **`Δ_end = +1111ms`：客户端已经放弃，服务端的查询还在 PG 上占着 1.1 秒。**
     （🔴 **此为修前值** ⇒ 修后按推导 ≈ 0，**待实测**）
2. **档位 B（客户端 5000ms > 服务端 2s）**：两侧 `Δ_end` **都 ≈ 0**（−3ms / +3ms）
   ⇒ **差异确实只来自「是否继承客户端 deadline」**，与两侧各自的 SQL 超时机制无关。
   ⇒ 这就是**对照组**的作用：把「SQL 超时机制不同」这个替代解释排除掉。

⇒ **UNRESOLVED-ARPC-002 从「静态推断」升级为「运行期实证」。**
定性轮 §1 的结论（CONFIRMED SEMANTIC MISMATCH）**得到实证支持**，不再是推断。

> ⚠️ **本节两条结论都不依赖「两侧 desc 是否相同」** ——
> 它们全部由 **`Δ_start` / `Δ_end` 两个时间差**支撑（档位 A 的 `+1111ms` vs `0ms`）。
> ⇒ 因此 2026-10-26 global 修掉档位 B 的 desc 差异后，**本节结论一个字都不用改** ——
> 这也正是当初把 desc 差异单独列一格、而把结论建立在时间差上的价值。
> （反过来：若当初的结论是「desc 不同 ⇒ 有漂移」，那么这次修文案就会**错误地销掉整条 ARPC-002**。）

> 🔴 **要把这条本体纳入回归网，需要一个「客户端 deadline 档」的双侧活体探针。**
>
> - **现有 HTTP/gRPC 对拍工具在结构上抓不到它**：`exp-difftest-grpc-admin-errors.sh` 用**默认 deadline**，
>   没开「客户端 deadline 档」⇒ **即便 `diff.py` 全绿，也抓不出这条**
>   ⇒ ⚠️ **不得以「对拍全绿」当它已闭环**（这条纪律已由 global 写进 **E38**）。
> - 🟢 **2026-10-26 订正：「必须新写探针」这个前提不成立** —— 探针**早已存在**
>   （`tools/difftest/exp-grpc-admin-deadline-probe.sh`，**2026-10-24 就有**，含短/长双档）
>   ⇒ **真正的缺口是「探针没进常规编排」**（`run.sh` / `diff.py` 都不调它 ⇒ 手动取证工具 ⇒ 改坏了不会被自动抓到）
>   ⇒ 已由 global 登记为 **E48**（落点 `tools/difftest/run.sh` ⇒ global 写权内 ⇒ A 类 · 下一轮做）。
> - **探针形态**（沿用本节 §1.2 的设计，主仪器 = PG 服务端日志）：
>   同一用例分别用 **短档**（客户端 100ms/1000ms）与 **对照档**（客户端 5000ms）打两侧 gRPC 端点，
>   比对 **`Δ_start` / `Δ_end`** —— ⚠️ **不是**比 desc、**不是**比客户端耗时
>   （§1.1 已记：客户端耗时两侧必然相同，量它会得出「两侧一致」的错误结论）。
> - ✅ **探针已经存在，不必新写**：`tools/difftest/exp-grpc-admin-deadline-probe.sh`
>   （2026-10-24 本轮 §1 就是用它产出的这张表）**已经带客户端 deadline 档 + 对照档**，
>   判定条件是 `exit 1 ⟺ 短档下两侧 PG 查询存活时长差异 > 1s` ⇒ **它就是抓这条的探针**。
> - 🔴 **真正的缺口不是「缺探针」，而是「探针没进常规编排」** ——
>   `tools/difftest/run.sh` 与 `diff.py` **都不调它** ⇒ 它是**手动跑**的取证工具，
>   **不在每轮门禁里** ⇒ 回归网缺这一环（改坏了不会被自动抓到）。
>   ⇒ 补法是把**已有**探针接进 `run.sh`（**落点：`tools/difftest/` 共享资产**）
>   + 它要用的 `service/common` 改动（**global 写权**）⇒ 属 global **E38** 的范围（**A 类 backlog，不需拍板**）。
>
> #### 🔴 2026-10-26 补记：**探针的档位设计有一个必须先满足的前提**（global 实测踩出来的）
>
> ⚠️ **客户端 deadline 必须早于服务端 timeout**，否则
> `effectiveTimeoutMs = min(服务端预算, 客户端 deadline 剩余)` **算出来就等于服务端值**
> ⇒ **两侧分不出差别** ⇒ **探针失效（会假绿）**。
> ⇒ 探针必须开一档**客户端 deadline < 服务端 2s** 的用例（如客户端 100ms）
> ＋ 一档**客户端 deadline > 服务端 2s** 的对照组（如 5000ms）。
> ⇒ **这条是「探针能不能验到修法」的前提，不是可选参数** ——
> 档位选错 ⇒ 探针恒绿 ⇒ **第 ② 层修没修都看不出来**。
> ⇒ global 已据此在 `tools/semantic-probe/rpc-timeout/` 的 Go 半边加 `client_deadline_ctx` 用例
> （**修前** Go=`client_100ms` vs RAG=`server_own` ⇒ **漂移坐实，EXIT=1**；**修后**两侧一致 ⇒ EXIT=0）
> ＋ 守门 `RpcInterceptorClientDeadlineGateTest` 2 条
> （① 客户端 deadline 早于服务端 ⇒ 有效期限必须落在客户端那档；
> ② 客户端**没带** deadline ⇒ 仍是服务端预算，防「把无 deadline 错当成有 deadline」）。

### 1.5 ⚠️ 效力边界（必须一起记，否则会被过度解读）

| | 内容 |
|---|---|
| **差异的可观察形态** | 「**服务端资源在客户端放弃后继续占用**」（PG 上多占 1.1s），**不是**「客户端响应不同」。<br>⚠️ **精确化（2026-10-26）**：「客户端响应两侧相同」**只对档位 A 成立**（两侧都是客户端自己的 `CallOptions deadline exceeded`）；**档位 B 修前 desc 是不同的**（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`）⇒ **global 修完后，档位 B 的 desc 也对齐了 ⇒ 这句话才在两个档位上都成立**。**但差异本体（档位 A 的资源占用）依旧没修** |
| **HTTP 层更看不见** | admin-api 把 gRPC status 翻译成业务码，deadline 差异在 HTTP 响应体里**根本不存在** ⇒ `diff.py` 的 HTTP 对拍**永远抓不到这条**。<br>（`DEADLINE_EXCEEDED` 两侧都落 `mapRPCStatusToCode` 的 default ⇒ 同为 `1015` ⇒ 修文案前后都**不体现**） |
| **修法归属** | 仍在 `service/common` 的 `RpcInterceptor` —— **只算服务端自己的 2s**（**〔以下为 2026-10-26 的历史描述〕** `RpcInterceptor` 里 `deadlineEpochMs` 的计算：`System.currentTimeMillis() + timeout.toMillis()`，**没有**取 `min(客户端 deadline, 服务端 2s)`）<br>🔴 **2026-10-29 订正（G4 回执 · `S1-USERS-014`）**：G4 `1f62a24`（**DRIFT-COMMON-005 结案**）已把 **`deadlineEpochMs` 从生产代码移除** ⇒ 改由 `DeadlineState` 持 **单调钟 + 纳秒精度**（`toNanosSaturated`）⇒ **本行所述「现状」已过期**（`git show align/g4-service-common:service/common/.../RpcInterceptor.java` 里已无 `deadlineEpochMs` 声明）<br>⚠️ **本文件其余 2 处同族引用**（`:693` / `:1914`）**同属历史记录**，阅读时按「修前」读；**合并后**须按新符号名重述 ⇒ **不在本域**，本 worker 无权改。<br>⚠️ **本行原先写的是行号 `RpcInterceptor.java:170-172`，已改为符号名** —— `service/common` 正被 `align/global` 持续改动（该文件在 2026-10-26 已从 170 行漂到 214 行），**行号会随别人改动而失效** ⇒ 行号引用一律换成**符号名 / 表达式**（AGENTS.md §3.1：证据要指向**消费点**） |
| **未覆盖** | 只测了 `Login` 一个 RPC。机制在 `RpcInterceptor` 里 ⇒ **对全部 13 个 RPC 生效**，但**逐个 RPC 的实测**未做。 |
| **🔴 本体未销** | **2026-10-26 的销账只覆盖「档位 B 的 desc 文案」**（global 修，已提交并推双远端 `22a9111`，**2026-10-26 已合回主干**）⇒ **ARPC-002 的本体（服务端 2s 不继承客户端 deadline）未被修**。<br>🔴 **2026-10-26 再复核：原写「条目的 CONFIRMED SEMANTIC MISMATCH 定性不变」需改判** —— 该定性原靠 `Δ_end = +1111ms` 支撑，而 `Δ_end` 由 **PG 取消时刻**（= `statement_timeout`）决定 ⇒ **第②层（`af416d9`）+ 第三十三轮（`d20b377`，毫秒精度）⇒ `Δ_end` ≈ 0**（推导，未实测） 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】⇒ **原观察依据按推导已失效** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上观察依据已归零**（`align/global` 上实测 **22/18/12ms**）⇒ **这句现在成立**（合回前实测 pre-E62 为 `Δ_end=+833ms`）⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ global **E48**）】；剩下的是**机制层**（业务线程不被取消，框架限制）+ **非 SQL 路径**（⇒ 本域 `DRIFT-ARPC-010`）。修法落点仍是 `service/common` 的 `RpcInterceptor`（`service/common` 单写权归 `align/global`）⇒ 已登记为 global **E38** |
| **⚠️ 本体的修法分两层（不可只做一半）** | **global 2026-10-26 补充的边界**（本表原先没有）：<br>**① 纯 deadline 计算**（可做）：`deadlineEpochMs` 与超时调度器延时改成 `min(客户端 deadline, now+timeout)` —— grpc-java 服务端 `Context.current().getDeadline()` **拿得到**客户端 deadline ⇒ `DeadlineState` 感知时刻与超时 close 时刻都正确。　🟢 **2026-10-26 已修**（`align/global` @ `af416d9`，待合回）<br>⚠️ **② 业务线程不被取消（机制层）**：Go 的 ctx 一取消 ⇒ **一切 ctx-aware 操作**（SQL / 下游 RPC / Redis）同步中止；grpc-java 的 `ServerCall.close()` **不取消业务线程**（**与 T-14 同一根因**：框架限制）⇒ 真正对齐要「handler 挪到受控 executor + 超时 interrupt」⇒ **架构级改动，影响 10 个 rpc 组件**。　🔴 **未修**，已另立项（**E38② 维持「另立项」**）<br>🔴 **E46 的两处实测修正（别照搬旧描述去做）**：① **量级被高估** —— SQL 侧**已有 B-1 缓解**（10 个 rpc 组件全部注册 `DeadlineQueryTimeoutInjector`）⇒ 真剩下**只有 ≤1s 窗口**；② **「挪受控 executor + 超时 interrupt」单独做不够** —— pgjdbc **不响应线程中断**（实测 interrupt 后等锁查询照常跑 **11014ms**）⇒ 必须配「**取消正在跑的 JDBC 语句**」<br>🟢 **第三十三轮（`d20b377`）已把 ② 的「SQL 侧」补上**：毫秒精度（`setQueryTimeoutMs`）⇒ **取消时刻 == deadline** ＋ 已过期 ⇒ 不发 SQL ⇒ ⇒ **`Δ_end` ≈ 0** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】（🔴 **口径纠正**：本行原写「但 PG 查询仍会跑满」⇒ **过时** —— `Δ_end` 由 `statement_timeout` 决定，**第①层一改它就跟着变**）⇒ **② 剩下的只有「非 SQL 路径」**（本域 ⇒ `DRIFT-ARPC-010`）<br>⇒ **只做 ① 只能修一半 ⇒ 修 ① 必须同时评估 ②** ⇒ **「desc 对齐了」更不等于「本体闭环了」** |

## 2. ✅ Lettuce socket 层 —— connect-timeout 5s 已证（第 3、4 层）

### 2.1 四层证据，第 3、4 层是本轮新增

| 层 | 内容 | 状态 |
|---|---|---|
| ① 文本层 | yml 里写了 `timeout: 3s` / `connect-timeout: 5s` | ✅ 已有（`AdminRedisTimeoutConfigTest` 第 1 条） |
| ② 绑定层 | Spring `Binder` 真把它们绑进 `DataRedisProperties` | ✅ 已有（`AdminRedisTimeoutConfigTest` 第 2 条） |
| ③ **消费点** | Boot 把 `connectTimeout` 接到 Lettuce 的 `SocketOptions` | ✅ **本轮新增** |
| ④ **socket 层** | 连黑洞 IP，实测失败耗时 | ✅ **本轮新增** |

### 2.2 第 ③ 层：Boot 4.0.8 的接线（**消费点**，符合 §3.3.1）

`spring-boot-data-redis-4.0.8` 的
`org/springframework/boot/data/redis/autoconfigure/LettuceConnectionConfiguration.java:212-215`：

```java
Duration connectTimeout = getProperties().getConnectTimeout();
if (connectTimeout != null) {
    builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
}
```

⇒ `DataRedisProperties.connectTimeout` → `ClientOptions.socketOptions.connectTimeout`。
实测（`AdminRedisConnectTimeoutSocketTest` ①）：

```java
cf.getClientConfiguration()      // ⚠️ LettuceConnectionFactory 没有 getClientOptions()，只有这个
 .getClientOptions()             // → Optional<ClientOptions>
 .map(ClientOptions::getSocketOptions)
 .map(SocketOptions::getConnectTimeout)   // → Duration.ofSeconds(5)  ✅
```

⚠️ **API 细节**：`spring-data-redis 4.0.7` 的 `LettuceConnectionFactory`
**没有** `getClientOptions()` 方法（javap 实测），只有 `getClientConfiguration()` ⇒
照抄别处的写法会编译不过。

### 2.3 第 ④ 层：socket 层实测（连黑洞）

黑洞 IP = `192.0.2.1`（RFC 5737 的 TEST-NET-1，TCP SYN 发出去**永不回包**；
本机实测 4 个保留段地址全部是黑洞）。

| 可能的失败耗时 | 来源 | 是否可能 |
|---|---|---|
| **≈5s** | 本配置的 `connect-timeout: 5s` | ✅ **期望值** |
| ≈10s | Lettuce 自己的默认（`SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = 10s`） | ⇒ 说明属性**没生效** |
| ≈3s | 回落到 `timeout: 3s` | ⇒ 说明 connect 超时没单独配 |
| ≈75s | macOS 的 TCP connect 系统默认（SYN 重传耗尽） | ⇒ 说明本配置完全没生效 |

⇒ 断言区间取 `[4000ms, 9500ms)` —— **只** 5s 落得进去。

### 2.4 🔴 一个必须记的发现：**两个超时值会互相遮蔽**（第一版实测 3266ms 教会的）

第一版测试**没有**覆盖 `spring.data.redis.timeout`，结果实测 **3266ms** —— 那是 `timeout: 3s`，
**不是** `connect-timeout: 5s`。

**根因**：**Lettuce 的连接是异步的** ——
`getConnection()` 立刻返回（真正握手在后台），
命令是**排在还没连上的通道**上的 ⇒ **先到期的往往是命令超时，不是连接超时**。

⇒ **只测「连不上要多久」很可能测的是另一个超时值。**
⇒ 修法：测试里把 `spring.data.redis.timeout` 覆盖成 `30s`（远大于 connect 超时），
**唯一**可能先到的就是 connect 超时 ⇒ 测出来的时长才有判别力。

⚠️ **这条同样适用于生产**：`connect-timeout: 5s` 与 `timeout: 3s` 并存时，
Redis 不可达的实际表现由 **3s 的命令超时**主导，5s 的连接超时**根本没机会生效**
⇒ 写「Redis 连接超时 5s」这个描述时，得知道它在故障时**通常不决定用户可见的时长**。

### 2.5 守门网

`AdminRedisConnectTimeoutSocketTest`（1 条，真连黑洞）：
- ① 配置消费点断言（`connectTimeout == 5s`）—— **永远生效**；
- ② socket 层时长断言 —— 区间 `[4000, 9500)`，
  且用 `Assumptions.assumeTrue` 判掉「本环境 192.0.2.1 不是黑洞」的情况
  （某些网络直接 `no route to host`，那样测到的是路由错误、不是超时）。

## 3. 本轮新增的文件

| 文件 | 性质 |
|---|---|
| `tools/difftest/GrpcAdminDeadlineProbe.java` | **新增**：带 **deadline 档位**的 gRPC 双端口客户端（报 RPC 绝对墙钟起止） |
| `tools/difftest/exp-grpc-admin-deadline-probe.sh` | **新增**：C 项实验编排（持表锁 → 打 deadline → 读 PG 服务端日志） |
| `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRedisConnectTimeoutSocketTest.java` | **新增**：Lettuce socket 层守门（配置消费点 + 黑洞实测） |

## 4. 本轮实测数字

| 项 | 数字 |
|---|---|
| **ARPC-002 客户端 deadline 1000ms 档** | Go 查询存活 **1005ms** / 客户端返回后仍占 PG **0ms**<br>RAG 查询存活 **2117ms** / 客户端返回后仍占 PG **1111ms** |
| **ARPC-002 客户端 deadline 5000ms 档（对照）** | Go **2113ms / −3ms**　RAG **2124ms / +3ms** |
| **Lettuce socket 实测** | 配置的 `connectTimeout=PT5S`　**实测失败耗时 = 5220ms**（其中 `getConnection` **0ms**） |
| **admin-rpc 全量** | **99 / 0 failures / 0 errors / 0 skipped**（16 个测试类；上一轮 98 ⇒ 本轮 +1） |
| **admin-api 全量** | **121 / 0 failures / 0 errors / 0 skipped**（11 个测试类；未变） |
| `mvn -pl admin-rpc,admin-api -am test` | **EXIT=0** |

> ⚠️ `getConnection` 耗时 **0ms** 这一个数字，就证实了「**Lettuce 的连接是异步的**」
> （`getConnection()` 立刻返回，真正握手在后台）⇒ 命令超时会先于连接超时到期。
> ⇒ 这也是第一版测试实测出 3266ms（= `timeout: 3s`）的原因。

---

<!-- ═══════════════════════════════════════════════════════════════════════ -->
<!-- 2026-10-24 完整性复核轮：「台账是否最新、最全」体检 —— 新增 DRIFT-ARPC-009 -->
<!-- ═══════════════════════════════════════════════════════════════════════ -->

# 2026-10-24 完整性复核轮（admin 域 · 「台账是否最新、最全」体检）

> **核对日期**：2026-10-24　**分支**：`align/users-admin`
> 与 `api.md` 的同名章节配对阅读。本轮**发现 1 处此前完全缺失的维度**（gRPC 拦截器链）。

## 1. 🔴 新增 DRIFT-ARPC-009 · admin-rpc 的 **gRPC 服务端拦截器链**缺 6 层（Go zrpc 框架级自带 vs RAG 只有 1 个）

- **Component**：admin-rpc / 全部 13 个 RPC 的**服务端**入口
- **Category**：Reliability / Resilience / Error Mapping
- **Classification**：OBSERVABLE DIFFERENCE　**Risk**：**MEDIUM-HIGH**（最严重的一条是 panic 的 status/业务码不同）
- **⚠️ 这是本轮才发现的维度**：此前 8 条 ARPC 全部聚焦「**单个 RPC 的语义**」，
  **没有一条检查过「服务端拦截器链」** ⇒ 属于**审计盲区**，不是「已审过没问题」。

### 1.1 Go 侧：zrpc **框架级**自带 7 个 unary 拦截器

**装配点**：`go-zero@v1.10.2/zrpc/server.go:144-168` `setupUnaryInterceptors`（按顺序 `AddUnaryInterceptors`）
＋ `go-zero@v1.10.2/zrpc/internal/server.go:57-59`（`AddUnaryInterceptors` 是 **append 到尾部** ⇒ **先注册的最外层**）
＋ `service/user/admin/rpc/admin.go:33,43`。

> 🔴 **引用订正（2026-10-28 · G4 修好 `check-ledger-selfcheck.py` 层 4 之后才暴露）**：
> 上面两条 Go 引用**原先写的行号/文件名有错**，本轮被守门判为「行号超出文件范围」⇒ ⇒ **已逐条重核真源并改正**：
> ① `zrpc/server.go` **真实 168 行** ⇒ 原写 `:143-170` **两端都超** ⇒ ⇒ 正确 = **`:144-168`**（`setupUnaryInterceptors` 的真实范围）；
> ② 原写 `zrpc/internal/**config**.go:57-60` ⇒ ⇒ **该文件真实只有 30 行，且整份是配置结构体**
>    （`StatConf` / `ClientMiddlewaresConf` / `ServerMiddlewaresConf` / `MethodTimeoutConf`）⇒ ⇒ **里面根本没有 `AddUnaryInterceptors`**
>    ⇒ ⇒ **这不是行号笔误，是【指错文件】**（`zrpc/internal/` 下 `config.go` 与 `server.go` **两个相邻同名族文件**）
>    ⇒ ⇒ 正确出处 = **`zrpc/internal/server.go:57-59`**（行号 57~59 **恰好与原值一致** ⇒ ⇒ **错的只有文件名**）。
>
> ⇒ ⇒ **语义结论本身没变**（「先注册的最外层」仍成立），但**现在补上了完整证据链**（原来只到「append」这一步就停了）：
> - **声明点**：`zrpc/internal/server.go:57-59` = `s.unaryInterceptors = append(s.unaryInterceptors, interceptors...)` ⇒ **append 到尾部**；
> - **消费点**（§3.3.1 要求写**消费点**而非只有声明点）：`zrpc/internal/rpcserver.go:53`
>   = `grpc.ChainUnaryInterceptor(s.unaryInterceptors...)` ⇒ ⇒ 顺序原样交给 grpc-go；
> - **最外层的证明**（grpc-go v1.65.0 `server.go:1185-1187` `chainUnaryInterceptors`）：
>   它的返回值把 **`interceptors[0]`**（**index 0**）作为**最外层**去调用，而它拿到的「下一层 handler」
>   正是 `getChainUnaryHandler(interceptors, 0, info, handler)`
>   ⇒ ⇒ **index 0 最外层** ⇒ ⇒ 结合「append 到尾部」⇒ ⇒ **先注册的最外层** ✔。
>   （⚠️ 原文是一句 `return` ＋ **`interceptors[0]`** ＋ `(ctx, req, info, getChainUnaryHandler(...))` ⇒ ⇒ **这里把 `[` 与 `(` 拆开写**
>   ⇒ ⇒ 因为 `tools/check-doc-links.py` 的 `LINK_RE` **不跳过代码块** ⇒ ⇒ 写成相邻形式会被 markdown 解析成链接 ⇒ ⇒ 守门误报。）
> - 顺带核过同段其它引用，**均正确**：`zrpc/config.go:42`（`Auth bool json:",optional"`）· `:46`（`Timeout int64 json:",default=2000"`）
>   · `:47`（`CpuThreshold int64 json:",default=900,range=[0:1000)"`）· `zrpc/internal/config.go:20`（`Trace bool json:",default=true"`）
>   · `zrpc/server.go:81`（`RpcServer.AddUnaryInterceptors` 公开包装，转调 `rs.server.AddUnaryInterceptors`）。

| # | 拦截器 | 装不装 | 依据 |
|---|---|---|---|
| 1 | `UnaryTracingInterceptor` | ✅ **装** | `Middlewares.Trace` 默认 `true`（`go-zero@v1.10.2/zrpc/internal/config.go:20`） |
| 2 | `UnaryRecoverInterceptor` | ✅ **装** | `Middlewares.Recover` 默认 `true`（`:21`） |
| 3 | `UnaryStatInterceptor` | ✅ **装** | `Middlewares.Stat` 默认 `true`（`:22`） |
| 4 | `UnaryPrometheusInterceptor` | ✅ **装** | `Middlewares.Prometheus` 默认 `true`（`:24`） |
| 5 | `UnaryBreakerInterceptor` | ✅ **装** | `Middlewares.Breaker` 默认 `true`（`:25`） |
| 6 | `UnarySheddingInterceptor` | ✅ **装** | `CpuThreshold` 默认 **900** > 0（`go-zero@v1.10.2/zrpc/config.go:47`）⇒ 自适应降载，CPU 阈值 90% |
| 7 | `UnaryTimeoutInterceptor` | ❌ **不装** | `admin.go:33` `DisableNativeRpcTimeout(&c.RpcServerConf)` 把 `conf.Timeout` 置 **0**（`observability/rpc.go:19-23`）⇒ `if c.Timeout > 0` 为假 |
| — | `UnaryAuthorizeInterceptor` | ❌ **不装** | `Auth bool json:",optional"` 默认 false（`go-zero@v1.10.2/zrpc/config.go:42`） |
| 8 | `observability.NewUnaryServerInterceptor(2000ms, slowThreshold)` | ✅ **装（最内层）** | `admin.go:43`（超时由它接管，见第 7 行） |

⇒ **Go 侧最终链（外→内）**：
`Tracing → Recover → Stat → Prometheus → Breaker → Shedding → observability(2000ms) → handler`
= **7 个拦截器**。

### 1.2 RAG 侧：**只有 1 个**

```java
// ⚠️ **历史值·已失效**（2026-10-23 快照，AdminRpcApplication.java:97-101）
//    —— 那是**只有 1 个拦截器**时的写法；现已改为共享链（GoStyleRpcProtectionChain）
// ⇒ **现行**：`AdminRpcApplication.java:105-113`
public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor) {
    return server -> server.intercept(rpcUnaryServerInterceptor);
}
```

- 唯一那个是 `RpcInterceptor.newUnaryServerInterceptor(rpcTimeout, slowThreshold)`（`RpcServerInterceptorFactory.java:41-46`）⇒ **超时 + 观测**合一。
- `service/users/admin/admin-rpc/src/main/` 对 `com.rag.common.protection` **零引用**（grep 零命中）
  ⇒ **recover / breaker / shedding 三层全无**；
- `recover` grep 零命中、admin-rpc 的 `application.yml` 关掉了 `management.endpoint.prometheus` ⇒ **stat / prometheus 两层全无**。

⇒ **缺 6 层：Tracing / Recover / Stat / Prometheus / Breaker / Shedding**。

### 1.3 最严重的一条：**panic 时 gRPC status 与业务码都不同**（端到端可观察）

| | Go | RAG |
|---|---|---|
| 谁兜底 panic | `UnaryRecoverInterceptor`（`go-zero@v1.10.2/zrpc/internal/serverinterceptors/recoverinterceptor.go:24-31`） | **无显式 recover 层** ⇒ 落到 grpc-java 框架默认 |
| 兜底后的 status | **`codes.Internal`（13）** + `"panic: <值>"`（`recoverinterceptor.go:39-42`：`status.Errorf(codes.Internal, "panic: %v", r)`） | **`Status.UNKNOWN`（2）** + `"Application error processing RPC"`（grpc-java `grpc-core-1.63.0` `io/grpc/internal/ServerImpl.java:811-814` `internalClose` ⇒ `Status.UNKNOWN.withDescription(...)`；`:858-865` catch 后**再 `throw t` 重抛**） |
| 服务端日志 | `logc.Errorf(ctx, "%+v\n\n%s", r, debug.Stack())` ⇒ **完整堆栈** | 依赖 grpc-java 的 uncaught handler ⇒ **无 Go 式堆栈日志** |
| **admin-api 渲染出的业务码** | `codes.Internal` ⇒ `getselflogic.go:57-58` `case codes.Internal: return nil, errmsg.ErrorServerCommon` ⇒ **5001** | `Status.UNKNOWN` **不在** `mapRPCStatusToCode`（`GlobalExceptionHandler.java:83-91`）的任何分支 ⇒ 落 **default** ⇒ **`CODE_SERVER_BUSY` = 1015** |

⇒ **同一个 panic，两侧 HTTP 业务码一个 5001、一个 1015。**
⚠️ 这**不是** `DRIFT-ADMINAPI-008` 已记的那 4 种 code（`ALREADY_EXISTS`/`PERMISSION_DENIED`/`UNAUTHENTICATED`/`INVALID_ARGUMENT`）——
`UNKNOWN` 是**第 5 种**，而且恰恰是 **panic 路径上真实会出现的那个**。

### 1.4 其余 5 层缺失的影响

| 层 | Go 行为 | RAG 现状 |
|---|---|---|
| **Breaker** | 熔断（`Middlewares.Breaker` 默认 true） | 无 ⇒ 慢下游不熔断，请求持续堆积 |
| **Shedding** | CPU > 90% 自适应降载 | 无 ⇒ 无降载保护 |
| **Stat** | go-zero stat 日志（慢请求阈值默认 **500ms**，`go-zero@v1.10.2/zrpc/internal/serverinterceptors/statinterceptor.go`） | 无 |
| **Prometheus** | gRPC server 侧指标 | admin-rpc 的 `application.yml` 关掉了 `management.endpoint.prometheus` ⇒ 无 HTTP 抓取口 |
| **Tracing** | go-zero `UnaryTracingInterceptor` | RAG 侧有 span（`RpcInterceptor` + 8 处 `AdminSpans.traced`）⇒ **部分覆盖**，但**不是同一层**，覆盖度未实测 |

### 1.5 处置：`align/message` 分支**已修**（未合并），本域**不重复改**

- `align/message` 给 `AdminRpcApplication` 加了 `@Import(GoStyleProtectionConfiguration.class)`，
  并把 `rpcInterceptorConfigurer` 改成
  `server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps))`
  ⇒ 链 = **recover → breaker → shedding → 业务观测拦截器**。
- ⚠️ **该 recover 是 Go 兼容的**：`GoStyleRecoverInterceptor.closeAsPanic` 用
  `Status.INTERNAL` + `PANIC_DESCRIPTION_PREFIX = "panic: "`（`observability/GoStyleRecoverInterceptor.java:71-72,140-145`）
  ⇒ **合并后 panic 的 status 与业务码都回到 Go 的一侧**（5001）。
- ⇒ **本域不重复改**（§5.3：工作树只写自己域；`service/common` 是共享模块）
  ⇒ **合并后本条自动消失**；**合并前本条成立**。
- ⚠️ **合并时留意**：别的域分支**动了我域的文件**
  （`align/message` 新增 `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminProtectionChainWiringTest.java`）
  ⇒ 合并时该文件是**新增**，与本分支无冲突；但其结论（链是否真挂上）**属 admin-rpc**，已记在本条。

### 1.6 Evidence strength

`静态源码·双侧`（Go 侧读了 `go-zero@v1.10.2/zrpc/server.go` / `go-zero@v1.10.2/zrpc/internal/config.go` / `go-zero@v1.10.2/zrpc/config.go` /
`recoverinterceptor.go` ＋ **配置默认值**；RAG 侧读了 `AdminRpcApplication.java` /
`RpcServerInterceptorFactory.java` / `RpcInterceptor.java` / `GlobalExceptionHandler.java`；
grpc-java 侧读了 `grpc-core-1.63.0` 的 `io/grpc/internal/ServerImpl.java` 源码）
⚠️ **未做活体验证**（未构造 panic、未实测 5001 vs 1015）⇒ **待实测**。

### 1.7 🔴 新增 DRIFT-ARPC-010 · `logout` 的 **Redis 写不绑 deadline** ⇒ 超时被记成「Redis 挂了」（**与 user 域 V-012 同型**）

- **Component**：admin-rpc / OP `Logout`
- **Category**：Error Mapping / Observability
- **Classification**：OBSERVABLE DIFFERENCE（**观测面**；**wire 面两侧一致**）　**Risk**：LOW-MEDIUM
- **来源**：**users/user 域 owner 2026-10-26 报**「同型新发现」`DRIFT-USERRPC-V-012` ⇒ **本域逐行复核后确认成立**（不是推测）
- **与 E38② 的关系**：**同型**（都属「在途工作不随客户端 deadline 取消」）⇒ **修法口径要与 global / user 域一起定，别两边各修各的**

#### 1.7.1 两侧行为逐行对照

| | Go | RAG |
|---|---|---|
| **Redis 写** | `service/user/admin/rpc/internal/logic/logoutlogic.go:38`<br>`l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))`<br>⇒ **`SetexCtx`（绑 ctx）** | `AdminDomainService.logout`（`AdminDomainService.java:267`）<br>`stringRedisTemplate.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds));`<br>⇒ **不绑 deadline** |
| **超时时的表现** | ctx 到点 ⇒ `SetexCtx` **立即**返回 ctx 错误 | 无人取消 ⇒ **跑满自己的 3s command timeout** 才失败 |
| **日志归因** | `logger.LogBusinessErr(ctx, errmsg.ErrorRedisUpdate, err)` ⇒ 与客户端**同时**发生 | `catch (Exception e)` ⇒ `log.error("admin logout Redis setex failed", e)` ⇒ **晚于**客户端 |
| **对外 status** | `codes.Internal` + `"注销失败,Redis写入异常"` | `Status.INTERNAL` + `"注销失败,Redis写入异常"` ⇒ ✅ **逐字相同** |

#### 1.7.2 差异本体（**只在观测面**）

- **wire 面两侧一致**：`INTERNAL` + 同一句中文 description ⇒ 客户端**看不出**差别。
- **观测面不一致**：
  - 客户端在 **2s**（服务端 gRPC deadline）就收到 `DEADLINE_EXCEEDED`；
  - 而服务端日志**再过 1s** 才记「**Redis 写入异常**」⇒ **日志说「Redis 挂了」，实际是超时**；
  - ⇒ **排障会被指向错误方向**（去查 Redis 基础设施，而不是查超时预算）。
- Go 侧因为 ctx 绑定 ⇒ Redis 写**立即**失败 ⇒ 日志与客户端**同时**发生 ⇒ 归因一致。

#### 1.7.3 ⚠️ RAG 侧那个 `catch (StatusRuntimeException e) { throw e; }` 守卫**挡不住这条**

`AdminDomainService.logout`（`AdminDomainService.java:268-271`）有一条 **B-1 守卫**（`StatusRuntimeException` 上抛，不被改写成 INTERNAL）——
但它**只挡得住 SQL 路径**：`SqlTimeoutStatusTranslator` 把 PG 的 `57014 query_canceled` 翻成
`StatusRuntimeException(DEADLINE_EXCEEDED)`。
⇒ **Redis 超时抛的是普通异常**（`RedisConnectionFailureException` / `QueryTimeoutException`）⇒ **不在守卫覆盖内**
⇒ 落进 `catch (Exception e)` ⇒ 被记成 `INTERNAL`「注销失败,Redis写入异常」。
⇒ ⚠️ **这是本条与 B-1 守卫的边界**：B-1 覆盖 SQL，**Redis 路径一直不在覆盖内**。

#### 1.7.4 处置

- **本轮不改代码**（修法口径未定 ⇒ 跨域统一决定）⇒ **只登记**。
- ⇒ 归 **`global` 的 E38② 同型**（在途工作不随客户端 deadline 取消）；
  `service/common` 单写权在 global ⇒ **本域无权改**。
- ⚠️ **与 E38② 的差别**：E38② 说的是 **PG 查询**；本条是 **Redis 写**
  ⇒ **两者是不同资源、不同路径**，但**根因同型**（业务层没有把 gRPC deadline 传到客户端调用）
  ⇒ 修 E38② 时**顺带覆盖 Redis 路径**才是正解，否则会只修一半。
- 🔴 **2026-10-26 交叉引用（同一个漂移，两个编号）**：global 在**第四十六轮 `96d94db`** 把 E38② 窄化时，
  已把**本条**单独登记成 **`E66`**（**「Redis 调用不感知 deadline ⇒ deadline 到点后缓存读写照跑不误（含写）」**）
  ⇒ ⇒ **本条 ＝ global `E66`（同一件事）** ⇒ ⇒ **销账时不要当两条分别销**；
  ⇒ **global 的 E66 证据是源码级**（`service/common` 无任何 Redis 期限载体 + 各域调用点扫描）⇒ **与本条同证据强度**（`静态源码·双侧`）⇒ **两侧都没做活体验证**。
- 🟢 **同轮另一条对 admin-rpc 的适用性（本域独立实测）**：global 的 **E65**（出向 gRPC 不继承 deadline）**本域不命中**
  ⇒ `grep -rn "Stub\|BlockingStub\|FutureStub\|GrpcClient" service/users/admin/admin-rpc/src/main/` ⇒ **零命中** ⇒ **admin-rpc 无出向 gRPC 调用**。

- **Evidence strength**：`静态源码·双侧`（Go 侧 `logoutlogic.go:38`；RAG 侧 `AdminDomainService.logout`：`AdminDomainService.java:259`（`checkDeadline()` 前置守卫）/ `:267`（Redis 写）/ `:268-275`（B-1 守卫 + `catch (Exception e)` + `log.error` + INTERNAL 抛出））
  ⚠️ **未做活体验证**（未构造 Redis 超时）⇒ **待实测**。
  🔴 **2026-10-26 订正（锚点漂移）**：本段原先写的是 `AdminDomainService.java` 的 **三个行号锚点**（Redis 写 `:228` · B-1 守卫 `:230-233` · Evidence strength `:220/228/230-234`）⇒ **它们在写下时（`f314fdd`）完全正确**，但被**本域自己的 `bd70865`**（给该文件加类级 javadoc「B-1 放行分支」段 ⇒ **+94/−28 行**）**整体推了 +39 行** ⇒ ⇒ **同一轮里「我改了文件」与「我的锚点失效」同时发生，却没有任何机制提示**（文件还在、行号仍在范围内、机器复核第 1 层只查路径存在、第 4 层只查 **Go 侧** file:line）⇒ ⇒ **本轮已逐条重核本域 37 处 Java 行号锚点**（见 §8）⇒ **坑位 `P-18`**。
  ⚠️ **写在这里的旧行号一律是「历史值、已失效」** ⇒ ⇒ **别把它们当活锚点抄走** ⇒ **现行锚点见本段上方（已改为带符号名 + 现行行号）**。

### 1.8 🟢 新增 DRIFT-ARPC-011 · **防回退留档**：29 处 catch 放行分支**不得**改回 Go 的 `codes.Internal`

- **Component**：admin-rpc（`AdminDomainService` 28 处 + `AdminCreateAtomicService` 1 处）
- **Category**：Error Mapping
- **Classification**：**留档（防回退）** —— **不是缺陷**：源码层形态两侧不同，**客户端可见面一致**
- **来源**：global **第三十六轮**（`697f547`）订正 E47 ⇒ 说「RAG 保留了更精确的 `DEADLINE_EXCEEDED`」
  ⇒ 并登记 **E58（有意偏离候选 🏷 建议签）** ⇒ **users/user 域**再复核 ⇒ 指出**「客户端可见」这一层不成立**
  ⇒ ⇒ **本条是本域对两侧的独立复核结论**（见 1.8.1 / 1.8.2），**顺带把 global 的 Go 侧清单数纠正了**。

#### 1.8.1 客户端可见面：**两侧都是 1015**（⇒ 不是 wire 级漂移 ⇒ 不需要签字）

| 层 | Go | RAG |
|---|---|---|
| **logic / service 层** | 🔴 **每条 DB 错误都改写成** `status.Error(codes.Internal, "DB查询失败")` ⇒ **logic 层根本不返回 1015** | ✅ 保留 `DEADLINE_EXCEEDED`（`SqlTimeoutStatusTranslator` 把 PG 的 57014 翻的）⇒ 原样上抛 |
| **interceptor 层** | `service/common/observability/rpc.go` 的 select：`case <-done:`（handler 先返回，**要** `lock.Lock()`）**vs** `case <-timeoutCtx.Done():`（**不需要锁**）⇒ **ctx 一取消超时分支立刻就绪** ⇒ select 几乎总走超时分支 ⇒ 客户端拿 `DEADLINE_EXCEEDED` | 同 ⇒ `RpcInterceptor` 的超时调度器在 deadline 时刻 close ⇒ `DEADLINE_EXCEEDED` |
| **api 层渲染** | `codes.DeadlineExceeded` **不在** `mapRPCStatusToCode` 的任何分支 ⇒ 落 **default** ⇒ **`CODE_SERVER_BUSY` = 1015** | 同 ⇒ 落 default ⇒ **1015** |

⇒ ⇒ **客户端可见的 HTTP 响应体两侧逐字一致** ⇒ ⇒ **不是 wire 级漂移**
⇒ ⇒ 按 `docs/Intentional_Deviations/` 的三类（**有意偏离 / 框架限制 / 表述差异**）
⇒ **归「表述差异」** ⇒ ⇒ **不需要用户签字**（⇒ 与 global 的 E58「有意偏离候选 🏷 建议签」**定性不同**，已回信）

⚠️ **残留（不声称 100%）**：Go 那个 select 是**竞速** ⇒ 机制上存在「handler 先返回 ⇒ 客户端拿到 5001」的窗口；
E-4 的 4 组采样全命中 1015，但那是 **4 次采样、不是概率刻画** ⇒ **不声称「Go 侧 100% 是 1015」**。

#### 1.8.2 🔴 顺手纠正 global 的 Go 侧清单（**本域自己数的，别照抄**）

global 第三十六轮列的 admin 域 Go 侧清单是 **12 处**、行号 `getuserlogic.go:38` · `banuserlogic.go:51` · …
⇒ **本域逐一核过**：`grep -rn "codes.Internal" service/user/admin/rpc/internal/logic/`（去掉 `_test.go`）
⇒ **共 26 处 `codes.Internal`**，其中：

| 类别 | 处数 | 清单 |
|---|---|---|
| **DB 错误出口** | **20** | `loginlogic.go:53` · `getuserlogic.go:39` · `getuserlistlogic.go:35` · `getselflogic.go:40` · `banuserlogic.go:52` · `unbanuserlogic.go:53` · `deleteuserlogic.go:54` · `createadminlogic.go:119` · `createinvitecodelogic.go:60` / `:94` · `resetuserpasswordlogic.go:53` / `:68` · `updateselflogic.go:40` / `:51` / `:77` / `:86` · `updateuserlogic.go:53` / `:66` / `:92` / `:100` |
| 非 DB 的 `codes.Internal` | 6 | 密码加密失败 ×3（`resetuserpasswordlogic.go:61` · `updateselflogic.go:63` · `updateuserlogic.go:77`）· 邀请码生成失败 ×2（`createinvitecodelogic.go:70` / `:101`）· **Redis 写入失败 ×1**（`logoutlogic.go:41`「注销失败,Redis写入异常」） |

⇒ 🔴 **global 的清单漏了 8 处**（`updateselflogic` 4 处 + `updateuserlogic` 4 处），
且 **它列的 12 处行号全部比实际小 1**（38↔39 · 51↔52 · 118↔119 · 59↔60 · 93↔94 · 53↔54 · 39↔40 · 34↔35 · 52↔53 · 52/67↔53/68 · 52↔53）
⇒ ⇒ **12 + 8 = 20** ✅ 账对上。
⇒ ⚠️ **那两个数根本不可比**：global 的「12」是 **Go 侧 DB 出口**，
而它同时把 RAG 侧的「28」（`catch (Exception e)` 数）拿来对照 ⇒ **两把尺子**。

#### 1.8.3 本域已做的动作（**行为零改动**）

1. **29 处 catch 注释的「理由」订正**（结论不变）：
   原文写「（**对齐 Go 的 1015**）」⇒ 让人以为**Go 的 logic 会返回 1015**，而**Go 侧没有任何 DB 路径返回 1015**
   ⇒ 按 AGENTS.md §3.3 强制第 3 条「**先改/删错误断言，再改实现**」：
   **只订正理由**，**生产行为一个字节都不改**。
   ⇒ 完整论证写**一次**（`AdminDomainService` 的类级 javadoc「B-1 放行分支」段），
   各站点只留一行指针 ⇒ **29 处注释都指向同一份论证**。
2. **本条（`DRIFT-ARPC-011`）留档防回退**（对标 users/user 域的 `DRIFT-USERRPC-V-013`）。

#### 1.8.4 与 `DRIFT-ARPC-010`（Redis 不绑 deadline）的分工

🔴 **两个不同的面**（users/user 域 2026-10-26 提出的「面 A / 面 B」洞见，本域采用）：

| | 面 A：**装配层** | 面 B：**异常类型** |
|---|---|---|
| 失效形态 | 翻译器/注入器**不在链里** | 异常**到了业务层但类型不对** |
| 后果 | 57014 不翻译 ⇒ 落进 `catch(Exception)` ⇒ **被改写成 INTERNAL** | Lettuce / `QueryTimeout` 异常**不是** `StatusRuntimeException` ⇒ 放行分支**永不命中** |
| 守网 | **插件链断言**（`AdminRpcContainerWiringTest` §5，2026-10-26 补） | **本条 + `DRIFT-ARPC-010`** |
| 本域状态 | ✅ **已闭环**（+3 条守卫 + 4 次变异） | 🔴 **ARPC-010 仍开着**（Redis 路径） |

⇒ ⇒ **两者表现都是「超时被当成 DB 故障」，但修法不同、守网不能互相替代** ⇒ **两边都补才算完整**。

#### 1.8.5 V-012 / ARPC-010 的「补日志」口径 —— **本域已经符合，无需改代码**

users/user 域的候选修法：「`catch (Exception e)` 里补一行记原始异常，**status/description 一个字不改**」
⇒ 本域实测：**29 处 `log.error(...)` 全部带原始异常 `e`**
⇒ **SQL 路径**（如 `log.error("admin banUser update DB error, uid={}", uid, e)`）
**与 Redis 路径**（`log.error("admin logout Redis setex failed", e)`）**两条都已记** ✅
⇒ ⇒ **本域无需改代码**；**wire 面本来就在两侧逐字一致**（两侧都是 INTERNAL + 同一句 desc）⇒ 不动它是**对的**。

- **Evidence strength**：`静态源码·双侧`（Go 侧 20 处 DB 出口逐一核过 + `rpc.go` 的 select 逐行读过；
  RAG 侧 29 处 catch + 29 处 `log.error` 逐处核过）
  ⚠️ **未做活体验证**（本域没跑 E-4 那套双栈活体）⇒ **客户端可见面的一致性目前靠 users/user 域的 E-4 实测 + 本域的机制推导**
  ⇒ **待本域复测**（探针在 `tools/difftest/exp-b1-timeout-cancel.sh`）。

### 1.9 🔴 新增 DRIFT-ARPC-012 · **Nacos 注册失败**：catch 的异常类型与实际抛出**不匹配** ⇒ 且「fail-open 还是 fail-fast」**口径未定**

- **Component**：admin-rpc / `bootstrap/NacosRegistration.java`（`register()` / `deregister()`）
- **Category**：Startup / Service Discovery（**可观测面**：启动日志；**当前不中断启动**）
- **Classification**：🟡 **口径未定 + 实现与自身声明不一致**　**Risk**：LOW（当前）→ MEDIUM（若 Spring 的事件错误处理策略改变）
- **发现时机**：**2026-10-26 第五十轮双向合流后**重跑门禁 ⇒ 合流日志里刷出 **760 条** `StatusRuntimeException: UNAVAILABLE / Connection refused: /127.0.0.1:1001` ⇒ ⇒ 追到 `NacosRegistration.register()`（详见 §11.5）

#### 1.9.1 两侧行为逐行对照（**本域自己的源码，不采信转述**）

| | Go | RAG |
|---|---|---|
| 注册动作 | `service/user/admin/rpc/admin.go:40` `reflection.Register(grpcServer)`（**go-zero 的 dev/test 模式 grpc 服务注册**，**不是 Nacos**） | `NacosRegistration.register()`：`nacosServiceManager.getNamingService().registerInstance(serviceName, groupName, instance)`（`ApplicationReadyEvent` 监听器） |
| **注册失败的语义** | 🔴 **`log.Fatal` ⇒ 进程退出（fail-fast）** | 🟡 **fail-open（记日志、继续启动）** —— ⚠️ **但这个 fail-open 没有 Go 出处**（RAG 用 Nacos、Go 用 etcd + gRPC reflection ⇒ **不是同一件事**） |
| **catch 的异常类型** | — | 🔴 **只 catch `NacosException \| UnknownHostException`**；而 nacos-client 3.x 实际抛 **`com.alibaba.nacos.shaded.io.grpc.StatusRuntimeException`** ⇒ ⇒ **不被捕获、直接逃出 `@EventListener`** |
| 与自身声明的一致性 | — | 🔴 **不一致** —— `AdminRpcContainerWiringTest` 的注释明写「NacosRegistration **捕获 NacosException 只记日志不中断启动**」⇒ ⇒ **实际捕获不到** |

#### 1.9.2 为什么本轮**不改代码**（§3.2 的硬要求）

- 「注册失败 ⇒ **fail-open**」这条语义**本域没有 Go 出处** ⇒ ⇒ **现在去调 catch 的宽窄 = 无出处 idiomatization**
  （AGENTS.md §3.2 点名的 11 处历史回归正是这一类 ⇒ **不重复**）；
- 且**逃逸当前不中断启动**（Spring 事件多播器只记日志）⇒ ⇒ **不是门禁阻断项**（§11.4 的 clean 重跑全绿）；
- ⇒ ⇒ **登记 + 上报口径，不擅自改**。

#### 1.9.3 🔴 跨域面（**实测枚举，非估算**）

**同款 `catch (NacosException e)` 形状**出现在：`like-rpc`（2 处）· `comment-rpc`（2 处）· `follow-rpc`（2 处）· `message-rpc`（2 处）· `favorite-rpc`（2 处）
⇒ ⇒ **⇒ 全仓口径必须统一**（否则各域各修各的 ⇒ 又一次「修文案 ≠ 修语义」的分裂）
⇒ ⇒ **落点分散在各域目录** ⇒ ⇒ **本域只报告**（§5.3）⇒ ⇒ **已上报 global**（唯一能定「统一口径 + 谁改」的角色）。

- **Evidence strength**：`静态源码·单侧 + 运行期日志`（RAG 侧 catch 子句逐行读过 + 合流后门禁日志里 760 条实际异常栈）
  ⚠️ **Go 侧只核了「注册失败是 fail-fast」这一点**（`admin.go` 的 `reflection.Register` 路径）⇒
  **Go 侧与 Nacos 等价的注册路径**（go-zero 的 etcd 注册）**本域未核** ⇒ ⇒ **待补**（不在本域写权内）。

## 2. 本轮在 rpc.md 侧的其他结论

| 项 | 结论 |
|---|---|
| 13 个 gRPC RPC 的**逐个语义** | ✅ 覆盖完整（§4 Equivalence Matrix 逐条） |
| `admin-proto` 13 rpc | ✅ 逐字节一致（`check-proto-static.sh` EXIT=0，`rpc.md:1066`） |
| 共用 DDL（`03`/`04`） | ✅ 已覆盖（ARPC-004 等） |
| 表名 `admins` vs `admin_admin` | ✅ **已记录**（`rpc.md:1289` + `Intentional_Deviations/users/admin/_shared.md:22`）——族内约定，**非缺陷**；且标注「两侧物理上不共用 schema」 |
| 8 条 ARPC + 3 条 H-* | ✅ 复核通过（H-2 的 `.last("FOR UPDATE")` 在 **119** 行，台账原文 `116-120` **范围正确**） |
| 跨分支影响 | ⚠️ `align/message` 改了 `AdminRpcApplication.java`（本条）+ `HttpMiddleware.java`（见 `api.md` 的 ADMINAPI-007）；`align/users-user` 改了 `JwtUtil.java`（**N-5 的 padding 未变**，结论仍成立，但签名档引用的代码已变 ⇒ 合并后应复核一次） |

## 3. 🔴 2026-10-26 补：共享复核工具 `tools/check-ledger-selfcheck.py` 对本域的效果（**只有第 1 层有效**）

**核对日期**：2026-10-26　**工具版本**：`align/global` @ `bb680a3`（第三十二轮，N8~N13 六条去噪）
（⚠️ 该工具改动**尚未合回主仓** ⇒ 主仓上的版本**无** N8~N13）

### 3.1 第 1 层：缺失 **33 → 0**（✅ 属实，已独立对照验证）

用 `--domain users-admin` 跑，本域三个台账文件（`api.md` / `rpc.md` / `_shared.md`）：

| 工具版本 | 台账引用路径 | 缺失 | 说明 |
|---|---|---|---|
| 主仓版（无 N8~N13） | 75 个 | **33 个** | **全是误报** —— N8 省略写法（`...`）、N9 裸文件名、N10 短路径 |
| `bb680a3` 版（N8~N13） | 75 个 | **0 个** | 33 条**全部去噪**（不计 FAIL） |

⇒ global 说的「**第 1 层从 33 条缺失 → 0**」**属实** ✅（我做了新旧两版对照，不是只看新版的输出）。

### 3.2 ⚠️ 但**第 2/3/4 层对非 `task` 域是误报或零信息量** ⇒ 不能当本域健康度结论

| 层 | 对 `users-admin` 的输出 | 判定 |
|---|---|---|
| 第 2 层「已修断言 → 仓库里查得到对应物」 | 断言清单写的是 **`DRIFT-TASK-RPC-001/002/005/006`** 等 | ❌ **误报** —— 判据**硬编码 task 域的编号前缀**（`tools/check-ledger-selfcheck.py:86-95`）⇒ 与本域无关 |
| 第 3 层「数字断言（实测 vs 台账）」 | 报「**commit 清单有 50 处缺陷**」 | ❌ **误报** —— 判据认的是 task 域的 `DRIFT-TASK-RPC-` / `PITFALL-TASK-RPC-` 编号与「commit 清单表」格式（`:624-658`）⇒ **本域台账是 append 式章节、根本没有那种表** |
| 第 4 层「Go 侧 file:line 是否悬空」 | 「引用不悬空: **0 条**」+ 已去噪 **201 条** | ⚠️ **零信息量** —— 201 条全被 N7（「台账已自陈并记档」）去噪 ⇒ 这个「0」**不代表没有悬空引用** |

⇒ ⚠️ **结论**：**该工具目前只对 `task` 域做了完整实现**；
对 `users-admin` 域**只有第 0/1 层可用**（第 0 层是工具自检，第 1 层是路径存在性），
**第 2/3/4 层跑出来的数字不要当本域台账的健康度结论** ——
尤其别把「50 处缺陷」当成本域台账有 50 个问题（**全是工具的任务域判据套错了域**）。
⇒ 工具在 `tools/`（**共享资产**），**不在本域写权内** ⇒ 本域**只报告、不改**。

## 4. 本轮改动清单（rpc.md 侧）

- **零代码改动** ⇒ 不需要重跑测试门禁（上次全量门禁：admin-rpc **99/0/0/0**、`skipped=0`）。

---

# 2026-10-26 global 第五轮通知消化（E46 落地 · E47 / E48 登记 · 🔴 推翻本域自己的一条断言）

> **本轮性质**：**台账订正 + 一处守卫测试**（唯一一处代码改动，见 §5）⇒ **需要重跑门禁**（结果见 §4）。
> **本轮最重要的一件事**：🔴 **本域台账里一条被引用 6 处的结论性断言是错的** —— 被自己推翻（不是被对方推翻）。
> ⇒ **坑位已立 `P-16`**（`docs/Pitfalls/users/admin/rpc.md`）：**仪器测的是什么 ≠ 你以为它在测什么**。

## 1. 🔴 被推翻的那条断言（本域自己的错）

**原文（2026-10-24 起，一直沿用到 2026-10-26）**：

> 「档位 A 的 `Δ_start` 那一格修后会变、**`Δ_end` 那格不变**（第 ③ 层未做）」

**错在哪** —— 我把 `Δ_end` 归因到了**错的机制**上：

| | 内容 |
|---|---|
| `Δ_end` 的定义 | **PG 取消时刻** − **客户端返回时刻**（`tools/difftest/exp-grpc-admin-deadline-probe.sh`） |
| 主仪器 | **PG 服务端日志**的 `canceling statement due to user request` ⇒ **取消时刻由 `statement_timeout` 决定** |
| 我原来的归因 | 「业务线程没被取消」（第③层）⇒ ❌ **与 `Δ_end` 无关** |
| 真正的归因 | 「**SQL 超时上限有没有继承客户端 deadline**」（第②层）⇒ ✅ |

⇒ **`Δ_end` 随第②层动，不随第③层动** ⇒ 「第③层未修 ⇒ `Δ_end` 不变」这条推理的**前提整个是错的**。

**六环归因链（每一环都有落点，可复现）**：

1. `Δ_end` = PG 取消时刻 − 客户端返回时刻（探针脚本的 `pg_log_cancel_events` + `python3` 差值计算）
2. PG 的取消时刻 = `statement_timeout` 到点 ⇒ pgjdbc 发 CancelRequest（**不是** PG 自己的 `statement_timeout` 超时 —— 那条日志文案是 `canceling statement due to statement timeout`，探针抓的是 `...due to user request` ⇒ **两者可区分**）
3. `statement_timeout` 由 `DeadlineQueryTimeoutInjector.applyQueryTimeout` 从 **`DeadlineState.remainingMillis()`** 设 ⇒ **与业务线程是否被 interrupt 无关**
4. 服务端业务线程的 `DeadlineState` 在 `RpcInterceptor` 的 **`onHalfClose()`** 里绑定 `deadlineEpochMs`（= `now + effectiveTimeoutMs`）⇒ **第②层（`af416d9`）让这个值 = `min(服务端预算, 客户端 deadline 剩余)`**
5. ⇒ **第②层一改，SQL 超时上限就继承了客户端 deadline** ⇒ `Δ_end` 从 **+1111ms** 掉到 **≈ +80ms**（剩下的就是秒级向上取整的残差）
6. ⇒ **第三十三轮（`d20b377`）的毫秒精度**（pgjdbc ≥ 42.7 的 `setQueryTimeoutMs(long)`）把残差也消掉 ⇒ **取消时刻 == deadline** ⇒ **`Δ_end` ≈ 0** 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**（合回前实测 pre-E62 RAG `Δ_end=+833ms`；`align/global` 上实测 **22/18/12ms** ⇒ **主干上的复测仍待跑**，探针已进编排 ⇒ 走 global **E48** 的常规复跑）】

**第 6 环的驱动侧证据（本轮实测，不采信任何转述）**：

- `javap`（pgjdbc **42.7.13**）：`org.postgresql.jdbc.PgStatement` **有** `public void setQueryTimeoutMs(long)`；
  接口 `org.postgresql.PGStatement` **没有** ⇒ 与 E46「反射按方法名探测」的理由一致
- `javap -c`：`setQueryTimeoutMs` → 写字段 `timeout:J` → `new StatementCancelTimerTask` → `PgConnection.addTimerTask(task, timeout)`
  → `Timer.schedule(task, delayMillis)` ⇒ **延时单位是毫秒**
- ⇒ 到点 ⇒ `PgConnection.cancelQuery()`（字节码里紧邻的调用点）⇒ 发 CancelRequest ⇒ **PG 记 `canceling statement due to user request`**
- **版本前提已核实**：`mvn -pl service/users/admin/admin-rpc dependency:tree` ⇒ `org.postgresql:postgresql:jar:42.7.13:runtime`
  ⇒ **≥ 42.7 ⇒ 毫秒路径生效**（若落到 42.6.2 则**没有** `setQueryTimeoutMs` ⇒ 反射探测失败 ⇒ 退回秒级 ⇒ `Δ_end` 会留 ≤999ms ⇒ **这个前提必须每次核**）

## 2. 订正后的状态（**推导，未实测**）

| 维度 | 状态 |
|---|---|
| 子项「服务端是否 min 客户端 deadline」 | ✅ **已答「是」且已修**（第②层 `af416d9`） |
| 原「CONFIRMED SEMANTIC MISMATCH」的观察依据（`Δ_end = +1111ms` vs `0ms`） | 🔴 **按机制推导已失效**（`Δ_end` ≈ 0） 🔴【**§9 订正** → **§11 订正（2026-10-26 合回后）**：**E46 + E62 均已合回主干** ⇒ **生产上观察依据已归零**（`align/global` 上实测 **22/18/12ms**）⇒ **这句现在成立**（合回前实测 pre-E62 为 `Δ_end=+833ms`）⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ global **E48**）】 |
| 机制层（业务线程不被取消，`ServerCall.close()` 不 interrupt） | ❌ **仍未做**（框架限制，**与 T-14 同根因**）⇒ 但它**不再产生 SQL 路径上的可观察差异** |
| 非 SQL 路径（Redis 等） | 🔴 **仍有差** ⇒ 已在 **`DRIFT-ARPC-010`** 登记（admin `logout` 的 Redis 写不绑 deadline） |

⚠️ **本轮没做实测，两条硬理由**：

1. **探针与编排会互踩**：探针脚本把容器名 `difftest-postgres` 与 `RUN_DIR=/tmp/difftest` **写死**
   ⇒ 与并发域会话的 harness 抢同一套 PG 容器 ⇒ **本仓明令禁止的并行踩踏**（`tools/difftest/run.sh` 头部）
2. **要测的 RAG 侧代码不在本分支**：`af416d9` + `d20b377` 都在 `align/global` 上 ⇒ 要测就得把 global 合进本域分支
   ⇒ 而 **E48 的落点正是 `tools/difftest/run.sh`**（global 写权内）⇒ 本域去改 = **抢 global 的活 + 越 §5.3 写权**

⇒ ⇒ **本轮只写推导，不写实测值**；**台账状态写「机制层未闭环 · 观察依据待复测」，不写「已闭环」**。

## 3. E47（业务层吞掉 `DEADLINE_EXCEEDED`）—— 🔴 **本域不命中**（独立核实，非采信对方盘点）

global 的 E47 列出「24 处硬改写 + 3 处静默降级」，落点在 like / comment / follow / message / article 五个域。
⇒ **本域已被 global 明确列入「不吞的（已修好，不必动）」那一栏** —— 但**本域仍独立核了一遍**（§3.3：不得采信转述）：

| 核项 | 结果 |
|---|---|
| `AdminDomainService.java` | `catch (Exception e)` **28** 处 ↔ `catch (StatusRuntimeException e) { throw e; }` **28** 处 ⇒ **1:1 全配对** |
| `AdminCreateAtomicService.java` | 1 ↔ 1 ⇒ 配对 |
| `AdminServiceGrpcImpl.java` | `catch (StatusRuntimeException e)` **在前**（`obs.onError(e)` 原样保留）· `catch (Throwable t)` **在后**（才转 INTERNAL）⇒ **不吞** |
| 合计 | **29** 处宽 catch 全配对 ⇒ 与 global 的「admin-rpc 30 处同款」**数字差 1**（global 第三十六轮已订正为 **28**，指的是 `AdminDomainService` 单类） |
| 翻译器抛什么 | `SqlTimeoutStatusTranslator` ⇒ `Status.DEADLINE_EXCEEDED.withDescription(...).asRuntimeException()` ⇒ **`io.grpc.StatusRuntimeException`** ⇒ **正是被 rethrow 捕获的那一类** ⇒ 透传成立 |
| **静默降级**（把超时当「计数为 0」而**接口返回成功**） | ❌ **无** —— `AdminDomainService` 里 5 处 `return true`（`:236` / `:650` / `:691` / `:1030` / `:1128`）**逐处查了上下文，全部在 catch 块之外的正常路径** ⇒ **admin-rpc 不命中 E47** |

⇒ 🟢 **E47 对本域无待办**。⚠️ **但这条结论只覆盖「catch 改写」这一形态** ——
**`DRIFT-ARPC-010`（Redis 写不绑 deadline）是另一形态**（不是吞异常，是**根本没有 deadline 可吞**）⇒ **仍开着**。

### 3.1 🔴 2026-10-26 global 第三十六轮（`697f547`）的订正 + 本域的再复核

global 第三十六轮把 E47 的定性**反过来了**：
「Go 侧**每一条 DB 错误都改写成** `codes.Internal` ⇒ **RAG 保留了更精确的 `DEADLINE_EXCEEDED`**
⇒ 方向是「**RAG 做了更准确的那一侧**」⇒ 登记 **E58（有意偏离候选 🏷 建议签）** ⇒ **不要改回 Go 行为**。」

⇒ ✅ **「不要改回 Go 行为」本域完全同意**（且已在 `DRIFT-ARPC-011` 留档防回退）。
⇒ 🔴 **但「wire 上码值不同 ⇒ 客户端可见」这一层，本域复核后不成立** ——
**客户端可见的 HTTP 业务码两侧都是 1015**（机制逐环见 `DRIFT-ARPC-011` §1.8.1：
Go 侧 logic 返 INTERNAL，但 `rpc.go` 的 select 在 ctx 取消时走 `timeoutCtx.Done()` 分支
—— **该分支不需要锁，而 `case <-done:` 要 `lock.Lock()`** ⇒ 超时分支先就绪
⇒ 客户端拿 `DEADLINE_EXCEEDED` ⇒ admin-api 落 `mapRPCStatusToCode` 的 **default** ⇒ **1015**）。
⇒ ⇒ **这是「源码层形态差异」，不是 wire 级漂移** ⇒ 归「**表述差异**」类 ⇒ **不需要用户签字**
⇒ ⇒ 与 global 的 E58「有意偏离候选 🏷 建议签」**定性不同** ⇒ **已回信 global 请其复核**。
⇒ ⚠️ **本域另外把 global 的 Go 侧清单数纠正了**（它列 12 处、行号全部小 1 ⇒ 实际 **20 处**）⇒ 详见 `DRIFT-ARPC-011` §1.8.2。

### 3.2 🔴 顺带纠正 global 的另一处：E47 的**原始登记方向**错了

E47 第三十三轮的原始登记是「**业务层把 SQL 被 deadline 取消改写/吞掉 ⇒ DEADLINE_EXCEEDED 被消灭**」，
它是**只读盘点 RAG 侧 catch 形态**的产物 ⇒ **没核 Go 侧** ⇒ **方向错了**（真实情况见 §3.1）。
⇒ 📌 **坑位**：global 已立 `PIT-GLOBAL-051`（「登记某域把异常改写了之前 ⇒ 必须逐条核 Go 侧」）
⇒ **本域的对应教训**：**「跨域的定性也要自己核」**（P-15 的同一条纪律，这次用在**反方向**）——
⇒ **两域对同一件事给出相反定性时，以「有活体实测 + 逐行源码」的那一侧为准**，并把冲突显式报回去。

## 4. E48（探针未进编排）—— 指针 + 本域视角

- **E48 原文**：`docs/DRIFT/global/_README.md` §七之五（落点 `tools/difftest/run.sh` ⇒ **global 单写权** ⇒ A 类 · 下一轮做）
- **E48 的前提正确**（本域独立核过）：探针 `tools/difftest/exp-grpc-admin-deadline-probe.sh` **2026-10-24 就有**（17KB），
  含短档 `DEADLINE_SHORT=1000ms` / 对照档 `DEADLINE_LONG=5000ms` + `exit 1 ⟺ 短档下两侧 PG 查询存活时长差异 > 1s`
  ⇒ **「缺探针」是错的**，**真缺口 = 没进编排**（`run.sh` / `diff.py` 都不调它）
- **本域的三步前置**（global 已在 E48 里写了前两步，本域补第三步的判据）：
  ① 探针补 `DIFTEST_PORT_OFFSET` 支持（现在端口与容器名都写死 ⇒ 破坏并行实例纪律）
  ② 排在 `diff.py` **之后**（它持 `ACCESS EXCLUSIVE` 表锁）
  ③ 🟢 **一次变异证明它真被调** —— 改坏探针 ⇒ 编排必须红
  ⇒ **本域的判据**：**编排层不能只看「脚本退出码 0」**，得看**「探针真的跑过并报出了两个 `Δ` 值」**
  ⇒ 判据出处：**AGENTS.md §3.3（2026-10-21 补）** ——
  **「判断一个注入/开关有没有真生效不能看启动日志」**（实证：注入点的 echo 照打、jar 照起、日志零告警，效果却是零）
  ⇒ **唯一可靠的判据是运行时证据** ⇒ 编排层要断言**「探针产出了两个 `Δ` 值」**，不是断言**「脚本退出码 = 0」**
- ⚠️ **E48 未做 ⇒ 本域 `Δ_end` 的修后值永远没有实测** ⇒ 这就是本条**不能记「已闭环」**的直接原因。

## 5. 🟢 顺带闭环：MyBatis 插件链的**装配层**覆盖缺口（本轮**唯一一处代码改动**）

**触发**：users/user 域回信指出 **B-1 的放行守卫只挡 SQL 路径**
（它们用 `javap` 读字节码得出：`QueryTimeoutException` / `RedisConnectionFailureException`
的继承链**都不是** `io.grpc.StatusRuntimeException`）⇒ 本域自查发现**同源的另一处**。

### 5.1 缺口是什么

`AdminRpcApplication` 上写着
`@Import({DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})`，
**但 admin-rpc 没有任何测试断言这两个 Bean 真进了 MyBatis 的插件链** ——
⇒ **这正是 P-4 的形状**（**声明点 ≠ 消费点**），只是消费点这次是 **MyBatis 插件链**，不是 gRPC 的 interceptor 链。

⇒ **链断掉的后果（本域的具体面）**：

| 缺哪个 | 后果 | 与 Go 的差 |
|---|---|---|
| `DeadlineQueryTimeoutInjector` | 每条 SQL 不带 `statement_timeout` ⇒ 客户端 deadline 到点时**在途查询继续跑满** | `Δ_end` 比 Go 多出「客户端返回后」那一段 |
| `SqlTimeoutStatusTranslator` | PG 的 57014 `query_canceled` **不会**变成 `DEADLINE_EXCEEDED` ⇒ 落进业务层 `catch (Exception e)` ⇒ **被改写成 INTERNAL** | **wire：5001 vs Go 的 1015** |

⇒ 🔴 **而且是静默的**：链里有它、其余测试也全绿（`AdminDeadlinePassthroughTest` 是 **mock** mapper
直接抛 `StatusRuntimeException` ⇒ 它验的是「catch 分支放不放行」，**不验**「异常到达时是不是那一类」）。

### 5.2 本轮的闭环（`AdminRpcContainerWiringTest` 加 **4 条** · **真 PG + 真容器**）

| # | 断言 | 钉住什么 |
|---|---|---|
| 1 | `sqlSessionFactory.getConfiguration().getInterceptors()` 里**真的有**这两个拦截器 | **消费点**（不是「容器里有没有这个 Bean」） |
| 2 | 翻译器的 `@Intercepts` 挂在 **`Executor`**（`query` + `update`）而**不是** `StatementHandler.prepare` | CancelRequest 引发的 `SQLException` 是**执行阶段**才抛的 ⇒ 挂 `prepare` 上就**永远翻译不到** |
| 3 | **负控 NC-A**：一枚**故意挂在 `StatementHandler.prepare` 上**的变异体，跑**同一条判据**，**必须被拒绝** | 判据**不是恒绿**（判据写松 ⇒ 用例 2 恒绿 ⇒ 等于没守门） |
| 4 | **负控 NC-B**：一枚「**一切正确 + 额外挂 `StatementHandler.prepare`**」的变异体 ⇒ **只有挂载点那条规则**能拒绝它 ⇒ 必须被拒绝、且**红在挂载点那条** | 判据**真的在看挂载点**（用例 3 只证明「不是恒绿」，证明不了「在看挂载点」） |

⇒ **① ② 两条合起来 = 整条 SQL 超时翻译链的装配层闭环**：
① 翻译器在链里（§5.2 表 1）→ ② 它挂在能看到执行期异常的位置（§5.2 表 2）→
③ 业务层放行分支在（`AdminDomainServiceCatchStructureTest`，字节码级 **28/28**）→
④ 翻译器本体行为对（`service/common` 的 `SqlTimeoutStatusTranslatorTest`）⇒ **四段都在，才算链通**。

⇒ **③ ④ 两条负控 = 证明 ② 那条判据本身不是摆设**（**users/user 域 2026-10-26 指出本域缺这一层**，
⇒ 他们说「你要抄的话**先自己跑一遍**，别照抄结论」⇒ **本域自己写了、自己跑了**，见 §5.3）。

### 5.3 变异证明它**不是假绿**（M1~M4）

| 变异 | 做法 | 预期（谁该红） | 实测 |
|---|---|---|---|
| **M1** | 摘 `@Import` 的 `SqlTimeoutStatusTranslator.class` | ① 红，实际链 `[DeadlineQueryTimeoutInjector]` | ✅ **只红 1 条**，红在目标断言，其余**仍绿** ⇒ 精确命中 |
| **M2** | 摘 `@Import` 的 `DeadlineQueryTimeoutInjector.class` | ① 红，实际链 `[SqlTimeoutStatusTranslator]` | ✅ **只红 1 条**，红在目标断言 ⇒ 精确命中 |
| **M3** | 把挂载点判据**弱化到恒绿**（只剩 `assertNotNull`） | **③ 负控必须红**（`Expected AssertionError to be thrown, but nothing was thrown`） | ✅ **②③④ 红**（三条负控/正例一起红）⇒ 判据恒绿被抓住 |
| **M4** | **只**去掉「不得挂 `StatementHandler`」那一条规则 | **只有 ④ 红、③ 仍绿** | ✅ **只红 1 条**（④）⇒ **证明 ④ 把挂载点规则单独隔离出来了** |

⇒ 每次变异后**还原 + sha256 一致 + `git diff` 为空**（`AdminRpcApplication.java` = `eb0afe29…`）。
⇒ ⚠️ **M3 的过程中本域踩到一次「过严的断言」并已改掉**：
最初本域在负控里加了一条「**必须红在特定那句断言**」的消息检查 ⇒ M3 只弱化其中一条规则时，
负控虽然红了，但**红的理由不是挂载点那条** ⇒ **那条消息检查把一次合法的部分弱化误报成失败**
⇒ ⇒ **判据的内部顺序不该被负控绑住** ⇒ 已改为：**③ 只要求「被拒绝」**（不查理由），
**④ 才查理由**（因为 ④ 的变异体**只违反一条规则** ⇒ 拒绝它的**只可能是**那条规则 ⇒ 查理由不会误报）。
⇒ 📌 **元教训**：**负控的「必须红在目标断言」要按「变异体违反了几条规则」来定强度** ——
**违反多条 ⇒ 不能查理由**（会误报）；**只违反一条 ⇒ 可以查理由**（且这才有隔离力）。

### 5.4 门禁结果（本轮实测 · **两口径一致**）

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common` | 174 | 0 | 0 | 0 |
| **`service/users/admin/admin-rpc`** | **105** | **0** | **0** | **0** |

⇒ 105 = 上轮 103 + **2 条负控**（③ ④）⇒ 净增 **+2**（本轮两条负控各 1 条）。
⚠️ **与更早记录的「99 / 16 个测试类」的差**：那 **+2** 来自 **message 域的 `21e4124`** 往 admin-rpc 加的
`AdminProtectionChainWiringTest`（**2 条**）⇒ **99 + 2 = 101 = 本轮基线** ⇒ **账正好对上**。
⇒ 📌 **「全量条数」这个数字会被别的域的提交改动** ⇒ **引用它时必须带核对日期与模块口径**，
否则下一个人会先怀疑「我是不是多写了测试」（本轮就怀疑了 ⇒ 靠基线实测才排掉）。
⇒ **实测口径**：基线用「把本轮唯一的测试文件改动 `git checkout` 回 HEAD ⇒ 跑同一条 `mvn -o -pl service/users/admin/admin-rpc -am test`」取
⇒ **不用 stash**（AGENTS.md §5.3：`git stash` 是工作区级状态，不作隔离手段）。

## 6. `hot-rpc` 未注册 `SqlTimeoutStatusTranslator` —— 🔴 **前提不成立，本域亦无影响**

global 第三十三轮已销案（`hot-rpc` **无 mapper / 无 MyBatis / 无 SQL**，Go 侧同样无 model 层 ⇒ **不是缺陷**）。
⇒ **本域独立核了「这是否波及 admin-rpc」**：**不波及** ——
`admin-rpc` **两个拦截器都注册了**（`AdminRpcApplication.java`：`DeadlineQueryTimeoutInjector.class` + `SqlTimeoutStatusTranslator.class`）
⇒ 且 admin-rpc **确有 SQL**（`userMapper` / `adminMapper` / `adminInviteMapper`）⇒ **适用面成立**。

## 7. 本轮改动清单（rpc.md 侧）

- **代码改动 2 处**：
  ① `AdminRpcContainerWiringTest` **新增 4 条**守卫（插件链 / 挂载点 / **负控 NC-A** / **负控 NC-B**）⇒ 详见 §5
  ② `AdminDomainService`（28 处）+ `AdminCreateAtomicService`（1 处）**只订正 catch 注释的「理由」**，
  完整论证写进 `AdminDomainService` 类级 javadoc ⇒ **生产行为零改动** ⇒ 详见 `DRIFT-ARPC-011`
- 台账订正 **6 处**过期断言 + **新增 `DRIFT-ARPC-011`**（防回退留档）+ **新增章节** + **新增 `P-16`** + **P-4 补一条当场闭环实例**。
- **33 处路径引用**由 `...` 缩写式展开为完整路径 ⇒ 见 §8（含**根因判断的自我订正**）。
- ⚠️ **回信 global**：① 「`Δ_end` 不变」这条错误结论 ② 与他们「`UNRESOLVED-ARPC-002` 的定性不变」的**冲突**
  ③ **E47/E58 的「客户端可见」这层不成立**（两侧都是 1015 ⇒ 非 wire 漂移 ⇒ 不需签字）
  ④ **他们 Go 侧 admin 清单漏了 8 处、行号全部小 1**（实际 **20** 处）⑤ E47 不命中 ⑥ E48 第三步判据 ⑦ hot-rpc 销案。
- ⚠️ **回信 users/user 域**：① 确认其 B-1 结论在我域成立、**面 A / 面 B 的分离**（我域面 A 已闭环、面 B = ARPC-010 仍开着）
  ② **负控已补**（M1~M4 全跑过，**自己写的没照抄**）③ **V-012/ARPC-010 口径我域已符合**（SQL + Redis 两条路径的 `log.error` 都带原始异常 ⇒ 无需改代码）
  ④ **客户端可见面我域复核后支持他们的结论** ⑤ 合并提醒已收到 ⇒ 同步主干。
- ⚠️ **合并提醒（users/user 域两次提）**：`align/users-admin` 从旧的 `do-my-own-gh` 分出 ⇒ **落后主干**
  ⇒ ⇒ **已处理**：本轮先 `git merge origin/do-my-own-gh`（`324c95e`）同步，再合回主干 ⇒ **干净无冲突**
  ⇒ ⇒ **2026-10-26 已合回主干**（Jason 拍板「现在合回」）⇒ 合并提交 **`8a1f355`** · 主仓门禁 **105/0/0/0** · 已推双远端
  ⇒ ⇒ **现在别的域从主干读我域台账，读到的就是订正后的版本**。

## 8. 🟢 附带修掉：第 1 层报 33 处 MISSING（**🔴 根因判断已被自己订正，见下**）

### 8.1 当时的发现

跑共享工具 `tools/check-ledger-selfcheck.py --domain users-admin`
⇒ **第 1 层（「台账引用的仓库内路径是否存在」）报 33 处 MISSING**（其中 api.md 7 处 / rpc.md 28 处 / 签字档 1 处 / 本节 3 处）。

### 8.2 🔴 订正：**「缩写式对工具永久不可解析」这句，在新版工具下不成立**

**当时的根因判断**（已作废）：本域路径引用大量用 `...` 省略中间包
⇒ 「缩写式对工具是永久不可解析的 ⇒ 第 1 层对它永远瞎 ⇒ 路径写错也没有机制能发现」。

**为什么作废**（2026-10-26 users/user 域提醒 ⇒ 本域复核确认）：
global 的 **`35a7b8e`**（第三十四轮 · 域适配 + commit 清单口径）给第 1 层**加了简写去噪**：
**N8**（省略写法）/ **N9**（裸文件名）/ **N10**（短路径）/ **N11**（Go 侧路径）/ **N12**（仓外引用）
⇒ ⇒ **新版工具把简写正确识别为「简写」并放过，不再报成「缺失」**
⇒ ⇒ **「缩写式 = 工具永远瞎」是旧版工具的口径问题，不是本域路径写错。**

**⇒ 本轮 33 处**逐条解析**全部唯一命中真实文件**（`service/common/.../observability/RpcInterceptor.java`
⇒ `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`，…）
⇒ ⇒ **零「真缺失」** ⇒ **本域的路径引用本来就都指向真实存在的文件。**

⚠️ **本段里上面那个 `service/common/.../observability/RpcInterceptor.java` 是「旧写法」的举例，本域台账里仅此一处**
⇒ ⇒ **用主干那份（中间版）工具跑第 1 层时会报「缺失 1 个」** ⇒ **那一条是本段这个举例本身，不是真缺失**
⇒ ⇒ 新版工具（`align/global` @ `35a7b8e`）的 **N8「省略写法」去噪**已把它放过（实测「**台账引用路径 73 个 ⇒ 缺失 0 个**」＋「**已去噪 1 条**」= **合计 74**）
⇒ 📌 **计数口径（§8.4 里同一个数会出现两种写法，**别当成矛盾**）**：
**工具输出的「引用路径 N 个」= 去噪后的净数**（本域 = 73）⇒ **再加上「已去噪 K 条」才是文件里出现的引用总数**（73 + 1 = **74**）。
⇒ ⇒ **判据**：若第 1 层只剩这一条 ⇒ 属预期；**若出现别的路径 ⇒ 那才是真缺失**。

⇒ 📌 **本轮那条「真缺失 = 0」的结论是对的**（解析全部唯一命中），
但**「因为缩写式不可校验所以要展开」的因果是错的** ⇒ 下面 §8.4 的理由已改写。

### 8.3 仍然展开成完整路径（**但理由变了**）

**新理由**（两条，都成立）：

1. **工具能校验它** —— 展开后它进得了第 1 层的**存在性判定**；
   缩写式则只能靠**去噪规则**放过（= 工具**不验证**它，只是不报错）⇒ **展开 = 从「不被检查」变成「被检查」**。
2. **人和工具都能直达** —— 完整路径可直接 `open` / 可被 IDE 定位；缩写式要人自己拼。

⚠️ **仍然不违反「共享层引用要用符号名」那条**（P-15）：那条针对**行号**（会随别人的改动漂移），
**完整路径不会漂移**（只有文件搬家才会，而搬家必是显式动作 ⇒ 可被工具抓到）⇒ **两者不冲突**：
**行号 → 符号名 / 路径 → 完整路径**，两类引用各自用各自稳定的锚点。

### 8.4 新版工具复测结果（本轮实测 · **2026-10-26**）

⚠️ **必须用新版工具跑**（`35a7b8e`，在 `align/global` 上，**尚未进主干** ⇒ 主干那份还是旧版 `7a01b51`）：
旧版对 users-admin 会给 60 条「不一致」，**拆开全是旧版误判** ⇒ 别把那个数字当本域问题。

```
python3 <新版 tools/check-ledger-selfcheck.py> \
        --repo-root <本 worktree> --go-root <Go 真源> --domain users-admin
```

| 层 | 新版对本域的判定 | 备注 |
|---|---|---|
| 第 0 层 · 复核器自检 | ✅ 全绿 | 含 40+ 条阳性对照 |
| **第 1 层 · 路径存在性** | ✅ **「台账引用路径 73 个 ⇒ 缺失 0 个」**（另**已去噪 1 条** ⇒ 合计 74） | 展开后它们**真的被校验了** |
| 第 2 层 | ⏭ **「不适用」** —— 工具原话：**「不是『通过』，是『没查』」** | 该层的「已修断言」清单只覆盖 `task` 域 |
| 第 3 层 | ⏭ **「不适用」** —— 工具原话：**「判据尚未为 `users-admin` 域定义」** | 编号前缀 / 章节结构 / commit 清单表都不一样 |
| 第 4 层 · Go 侧 file:line 悬空 | ✅ **「引用不悬空: 168 条」**（去重 204 → 去噪 36） | 去噪口径比旧版精确（N5/N7 分开） |
| 第 5/6 层 | ⚠️ 扫的仍是 `task` 域的类 ⇒ **不适用** | 工具自己标注「不算失败」 |

⇒ **总判定：层 0~4 全部通过**。

⚠️ **但「不适用」≠「通过」**（工具自己在输出里写了这句话）⇒
⇒ **不得拿「工具全绿」当「本域台账已复核」** —— 层 2/3 对本域**根本没查**。

📌 **本表已被更新版工具的结果取代 ⇒ 别拿本表的数字当现状**（**2026-10-26 第四十一轮实测**）：

| 层 | 本表（`35a7b8e` 那版） | **现状（`0107845` 那版，146 KB）** |
|---|---|---|
| 第 1 层 | 「台账引用路径 **73** 个 ⇒ 缺失 0 个」（+1 条 N8 去噪 ⇒ 合计 74） | **「台账引用路径 76 个 ⇒ 缺失 0 个」**（+1 条 N8 去噪 ⇒ 合计 77；**+2 净引用 = §9 新增的路径引用**） |
| 第 4 层 | 「引用不悬空: **168** 条」（去重 204） | **「引用不悬空: 186 条」**（去重 **222** → 去噪 36） |
| 第 5/6 层 | ⚠️ **扫的仍是 `task` 域的类 ⇒ 不适用** | 🟢 **已按域取源码根** ⇒ 跑 `--domain users-admin` 出的是 **admin 域自己的类**（**3 个测试类 + 45 个生产类**）⇒ **不再误报** |
| 工具来源 | `align/global` @ `35a7b8e`（100 KB） | `align/global` @ `0107845`（**146 KB**，2026-10-26 22:2x）⇒ **仍未进主干** ⇒ 主干那份（48 KB）**依旧不可用** |

⇒ 📌 **判据（再补一条，与 §8.6 同族）**：**引用工具输出得出的数字，必须写明「工具版本 + 核对日期」** ——
⇒ **同一个工具的两次跑，数字会不一样**（因为工具自己会变）⇒ ⇒ **「74 vs 76」这类差异先查工具版本，再查台账**。
⚠️ **本表的 73 / 168 是 `35a7b8e` 那版工具的输出，保留作历史留痕**；**现状看 §9 那一轮的数字**。

### 8.5 本条自己踩到的元教训

🔴 **这是本域第 3 次**「跨域销账通知要自己核」（P-15）——
**users/user 域说「global 的工具缺陷已修」⇒ 我复核后发现：**
**我上一轮基于旧版工具写的「根因」结论需要作废** ⇒
⇒ **「别人的工具升级」会让我自己 previously 写下的「根因判断」过期** ⇒
**凡是引用工具输出得出的结论，都必须写明「用哪个版本的工具」+ 核对日期**（§6.3 状态表纪律）。

⚠️ **本轮净变更**：33 处路径展开 + 6 处断言订正 + 1 个新坑位（`P-16`）+ **2 条守卫测试**（§5）+ 本章节。

### 8.6 🔴 本域同款「基于主仓的验证看不到守门」的雷（global 2026-10-26 撞过 ⇒ 本域主动自查）

global 第 37 轮实测 `--domain users --repo-root <主仓>` 时，第 1 层报
「`RpcInterceptorTimeoutDescriptionGateTest.java` 缺失」⇒ **那不是缺陷，是那个守门类只在 `align/global` 上、主干上没有**
⇒ global 已在台账写明（否则下一轮有人会去「补」一个已存在的守门）。

⇒ **本域主动自查了同一颗雷**（**不因为别人撞到就当自己没事** —— P-15 的同一条纪律）：

| 核项 | 实测（2026-10-26） |
|---|---|
| 主仓上我域台账的版本 | 🔴 **`023b63b`（2026-10-24，完整性复核轮）** ⇒ **落后 8 个提交** |
| ⇒ 别的域从主干读我域台账，读到的是 | ❌ **订正前**的旧版 ⇒ **含那条被推翻的 `Δ_end` 断言** · ❌ 无 `DRIFT-ARPC-010`（Redis 不绑 deadline）· ❌ 无 `DRIFT-ARPC-011`（防回退）· ❌ 无 `P-16` · ❌ 无第 1 层的路径展开 |
| 主仓上我域的 4 条新守门 | 🔴 **0 条**（`AdminRpcContainerWiringTest` 里的 4 条全只在 `align/users-admin` 上） |
| 第 1 层跑 `--repo-root <主仓>` | 「**71 个引用 ⇒ 缺失 0 个**」⇒ ⇒ **但这不代表我域的守门在** |

⚠️ ⇒ **本域与 global 的症状不同、根因同款**：
global 那个守门类是**分支上新加的** ⇒ 主干没有 ⇒ **触发 MISSING**；
**本域那个测试类是主干上早就有的** ⇒ 只是**方法不在** ⇒ **不触发 MISSING、也不影响路径计数**
⇒ ⇒ **本域更隐蔽**：第 1 层「0 缺失」与「层 0~4 全通过」**都照常显示**，**没有任何告警提示「守门其实不在主干上」**。

⇒ 📌 **判据（建议写进通用纪律）**：
**「工具全绿」要问一句「它验的是哪棵树」** —— `--repo-root` 指向**主仓**时，
验的是**主仓那份台账 + 主仓那份代码**，**不是**本域分支上的最新状态
⇒ ⇒ **分支未合回时，「工具全绿」的含义要打折扣**。

⇒ ⏳ **待办：合回主干** ⇒ 合回后主仓才含本域的台账订正 + 4 条守门
⇒ 🟢 **2026-10-26 18:0x 已合回**（Jason 拍板「现在合回」）⇒ 合并提交 **`8a1f355`**（主仓 `do-my-own-gh`）
⇒ ⇒ **主干上的我域台账 = `8a1f355`**（不再是 `023b63b`）⇒ **别的域现在从主干读到的就是订正后的版本**
⇒ ⇒ **合回实测**：合并**干净无冲突**（无冲突标记）· 改动**只落在我域 8 个文件**（三档台账 + `admin-rpc`）
⇒ **不含任何共享索引** ⇒ ⇒ **没有撞掉别的域正在改的共享文件**（AGENTS.md §5.3 说的「多路并发必撞」在本域不成立）
⇒ ⇒ 主仓门禁重跑：**admin-rpc 105/0/0/0** · `service/common` 174/0/0/0 · `skipped=0` · BUILD SUCCESS
⇒ ⇒ **已推 origin + upstream 双远端**（四处 ref 一致 = `8a1f355`）。
⇒ ⚠️ **剩下的「待合回」措辞**（本文件里 14 处）**全部指 `align/global`**（E30 desc 文案 / E38 第①层期限计算）
⇒ ⇒ **global 仍未合回** ⇒ ⇒ **那些措辞仍然准确，一律不动** —— ⚠️ **别把 global 的「待合回」误改成「已合回」**。<br>🟢 **2026-10-26 第五十轮双向合流后：本行已过期** ⇒ ⇒ **那些措辞已按 §11 逐条改成「已合回 / 已销账」** ⇒ ⇒ **⚠️ 本行保留原文作历史留痕**（它记的是「18:0x 那一刻的准确状态」）。

⇒ 🟢 **合回不会与并发域撞车**（本轮实测）：`git diff origin/do-my-own-gh...align/users-admin` 的改动文件
**全部落在本域自己的三档台账 + `service/users/admin/admin-rpc/`** ⇒
⇒ **不含任何共享索引**（`docs/DRIFT/_INDEX.md` / `docs/Pitfalls/_INDEX.md` /
`docs/Intentional_Deviations/_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `AGENTS.md`）⇒
⇒ ⇒ **合回主干时不会撞掉别的域正在改的共享文件**（AGENTS.md §5.3 说的「多路并发必撞」在本域不成立）。

---

# 2026-10-26 第四十一轮（global **E62**）—— 🔴 **再订正一次我自己的订正**：「毫秒精度已生效」在**生产上**是错的

> 📌 **编号消歧**：外部那些「🔴【**§9 订正**：…】」标记里的「**§9**」指的是**本块整体**；
> 而本块**内部**的子节编号是 1~10（`## 1.` … `## 10.`）⇒ **两者不是同一级**，读到子节 `## 9.` / `## 10.` 时别误当成「整块 §9」。
> ⇒ **本块的后三节（`## 8.` 锚点漂移 · `## 9.` 第四十二轮回执复核 · `## 10.` 第四十四轮回执消化）都是同一轮补的** ⇒ **一并以本块为准**。

> **本轮性质**：**纯台账订正**（**零代码改动**）⇒ 不需要重跑测试门禁。
> ⇒ **但本轮仍重跑了（2026-10-26 22:32 实跑）**：admin-rpc **105/0/0/0** · `skipped=0` · **BUILD SUCCESS**（`-pl service/users/admin/admin-rpc -am test`）
> ⇒ 理由：**状态表纪律**（§6.3）——「已修/未修」断言必须带**核对日期** ⇒ 引用上一轮的数字时必须写明它是什么时候测的。
> **本轮最重要的一件事**：🔴 **我在 `1d79f56` 那轮做的订正（`Δ_end` 随第②层动、不随第③层动）—— 口径是对的，
> 但我据此写的「`Δ_end` ≈ 0」在生产上不成立** ⇒ ⇒ **「观察依据已失效」这个结论也要再订正一次**。

## 1. global 报的事实（E62）+ 🔴 **本域独立核实**（不采信转述）

**事实**：注入器用 `statement.getClass()` 探测 `setQueryTimeoutMs(long)`，
而该方法只挂在 **pgjdbc 实现类** `PgStatement` 上 ⇒ **生产走 HikariCP ⇒ 拿到的是代理类** ⇒ **探测必然失败** ⇒ **退回秒级向上取整**
⇒ SQL 取消时刻 = deadline + (0~999]ms。

**本域独立核实（三条，都不依赖 global 的转述）**：

| 核项 | 方法 | 结果 |
|---|---|---|
| admin-rpc 的连接池 | `mvn -pl service/users/admin/admin-rpc dependency:tree -Dincludes=com.zaxxer:*,org.postgresql:*`（**2026-10-26 实跑**） | ✅ **`com.zaxxer:HikariCP:jar:7.0.2:compile`** ＋ **`org.postgresql:postgresql:jar:42.7.13:runtime`** ⇒ **生产走 HikariCP** |
| 代理类有没有毫秒方法 | `javap` HikariCP 7.0.2 的 `HikariProxyPreparedStatement` | ✅ **只有 `getQueryTimeout()` / `setQueryTimeout(int)`（秒级）**，**没有 `setQueryTimeoutMs(long)`** ⇒ **按代理类探测必失败** |
| 父类有没有（防「方法藏在父类」） | 同上，`javap` `ProxyPreparedStatement` / `ProxyStatement` | ✅ **整条代理继承链上都没有** 毫秒方法 ⇒ **不是「继承藏住了」，是「代理类压根没这方法」** |
| 毫秒方法到底挂在哪 | `javap` pgjdbc 42.7.13 的 `org.postgresql.jdbc.PgStatement` | ✅ **实现类上有 `setQueryTimeoutMs(long)` / `getQueryTimeoutMs()`**；而**接口 `org.postgresql.PGStatement` 上没有** ⇒ ⇒ **只能反射实现类**（与 global 的实现一致） |
| 修法可行吗 | `javap` 同上（`HikariProxyPreparedStatement`） | ✅ 该类**实现了 `java.sql.Wrapper`** ⇒ `unwrap()` 可用 ⇒ **不必把驱动实现类编进 `service/common` 的 API 面**<br>🟢 **额外依据**：pgjdbc 在 admin-rpc 里是 **`runtime` scope**（不在 compile classpath 上）⇒ **驱动类本来就不该出现在 `service/common` 的 API 面上** ⇒ 用 `Wrapper#unwrap` 是与依赖形态自洽的做法 |

⇒ ⇒ **E62 成立**（机制链完整、可独立复核）⇒ ⇒ **我台账里「毫秒精度已生效」的表述全部要加限定**。

## 2. 🔴 状态矩阵（**四个代码状态，`Δ_end` 完全不同**）

| 代码状态 | 注入器 | `statement_timeout` | 档位 A 的 `Δ_end` | `UNRESOLVED-ARPC-002` 的观察依据 |
|---|---|---|---|---|
| **主干 · 2026-10-24（修前）** | **pre-E46** | 秒级向上取整（服务端预算的 ceil） | **+1111ms**（2026-10-24 实测） | **仍成立** |
| `align/global` **pre-E62** | post-E46 | 秒级向上取整（`min(客户端,服务端)` 的 ceil） | **+833ms**（2026-10-26 global 实测） | **仍成立**（但**成因已变**：不再是「不继承客户端 deadline」，而是「**毫秒路径没生效**」） |
| `align/global` **post-E62** | post-E46 + **unwrap 后再探测** | **毫秒级**（= `min(客户端,服务端)` 剩余） | ✅ **实测 22ms / 18ms / 12ms**（三次跑，global **第四十五轮 `180f865`** 探针复跑 ⇒ **PASS**）⇒ **不再是「推导」** | ✅ **实测已归零**（观察依据**确实失效**） |
| 🟢 **主干 · 现在**（**2026-10-26 第五十轮合流后**） | **post-E46 + E62**（主干 `DeadlineQueryTimeoutInjector` 实测含 `unwrapDriverStatement`；注入器单测 **13** 条） | **毫秒级** | 🟡 **机制推导：`align/global` 上实测 22/18/12ms ⇒ 预期 ≈ 0**<br>⇒ ⚠️ **主干上的复测仍待跑**（探针已进编排 ⇒ 走 global **E48** 的常规复跑）⇒ ⇒ **在复测前，主干这一格属「机制推导 + 别处实测支持」，不是本域在主干上的实测** | 🟡 **预期已失效，待主干复测确认** |

⇒ ⇒ 🔴 **我 `1d79f56` 那轮写的「`Δ_end` ≈ 0」⇒ 只在「E46 合回」**且**「E62 修好并合回」两个条件同时满足后才成立**
⇒ ⇒ **今天的主干上、以及 pre-E62 的 global 分支上，都不成立**。

🟢 **2026-10-26 更新：本轮拿到了真值** ⇒ 上表第 3 行的「推导 ≈ 0 ⇒ 待复测」**已换成实测**
⇒ **真值**（global 第四十五轮 `180f865`）：短档两侧差 **22ms / 18ms / 12ms**（三次跑）⇒ **PASS**
⇒ ⚠️ **顺带一个阈值坑（global 挖出来的，对本域同样成立）**：旧默认阈值 **1000ms > 实测的 E62 回归信号 833ms**
⇒ ⇒ **E62 全坏掉时探针照样 PASS** ⇒ ⇒ global 已按**实测值重校准为 150ms**（对噪声 22ms 有 **6.8×** 余量 · 对回归信号 833ms 有 **5.5×** 余量）
⇒ 📌 **判据**：**探针的阈值必须用实测值校准，不能用推算值**（第四十轮推算「短档 300ms ⇒ 信号 ≈1700ms」，第四十一轮实测只有 833ms —— 因为那个推算依赖「SQL 超时已继承客户端 deadline」这个**当时不成立**的前提 ⇒ **同款「前提错」，见 `P-17`**）。

📌 **怎么判定「当前处在哪一格」（可复核的硬证据，2026-10-26 实测）**：

⚠️ **本表的 `align/global` 一列 = 该分支在 `0516774`（第四十八轮收尾）时的状态** ——
**核对 ref 写在表下**（**这正是 global 第四十二轮立的新纪律** ⇒ 见 §10）。

| 判据 | 主干 / 本域分支 | `align/global` @ `0516774` |
|---|---|---|
| `DeadlineQueryTimeoutInjector` 里有 `applyMillisecondTimeout` / `unwrapDriverStatement` | ❌ **都没有** ⇒ **pre-E46** | ✅ **都有** ⇒ **post-E46 + E62** |
| `DeadlineQueryTimeoutInjectorTest` 的 `@Test` 条数 | **7** | **13**（含 **3** 条 `🔴 E62`） |
| 运行时日志 | 无 `WARN DeadlineQueryTimeoutInjector` | 🔴 **该 WARN 是 `align/global` 在 E62 修复「之前」跑出来的**（global 贴的那条：代理类不支持毫秒级 ⇒ 退回秒级）⇒ **它证明的是「pre-E62 形态」** ⇒ **E62 修好后再跑同一场景应当不再出现这条 WARN** ⇒ ✅ **已被 global 第四十五轮实测闭环**：修好后探针短档差 **22/18/12ms ⇒ PASS** |

## 3. 🔴 二次订正：**「原观察依据已失效」这个结论，在生产上也不成立**

我 `1d79f56` 那轮写过：「原『CONFIRMED SEMANTIC MISMATCH』的观察依据（`Δ_end = +1111ms` vs `0ms`）**按机制推导已失效**」
⇒ ⇒ **那句的前提当时就少了一个条件**（我假定「毫秒精度已生效」）⇒ ⇒ **该假定在生产上不成立**
⇒ ⇒ **实测 `Δ_end = +833ms` ⇒ 观察依据在生产上仍然存在** ⇒ ⇒ **「整条定性」维持不变**（不是「降级为机制层未闭环」）。

🟢 **但**：我那轮的**口径订正本身被实证确认了** ⇒ 这是本轮最有价值的一条：
> **`Δ_end` 由 PG 取消时刻决定（= `statement_timeout`）⇒ 与「业务线程有没有被 interrupt」无关**
> ⇒ global 实测的 **`Δ_start = +1139ms` ≈ 1 秒**（既不是服务端 2s 预算、也不是客户端 300ms）
> ⇒ **与「秒级向上取整」的签名精确吻合** ⇒ ⇒ **那 833ms 是取整残差、不是业务线程问题** ⇒ ⇒ **我的口径订正是对的**。

⇒ 📌 **教训（本轮新增，见 `P-17`）**：**「按机制推导」出来的结论，必须逐条列出它依赖的每个前提，并核那些前提在目标环境里是否成立**
⇒ ⇒ 我当时核了「机制链」，**没核「这条代码路径在生产上是否真的被走到」** ⇒ ⇒ **同一个错误的第 N 次复发**：
**「代码写了」≠「生产走的是这条」**（§3.3 的老坑，E62 是它的新变体）。

## 4. E38② / 第②层定性 ⇒ ✅ **已在 global 第四十六轮重估完毕**（原修法**否决** · 第③层窄化成 E65 / E66）

global 第四十一轮：「第②层现在的可观察面（833ms）**主要来自秒级回退**，而不是「grpc-java 不取消业务线程」本身
⇒ 若注入器走到毫秒路径 ⇒ `Δ_end` ≈ 0 ⇒ ⇒ 「**架构级改造是闭环必要条件**」这个结论**可能不成立**」。

⇒ 🟡 **本轮当时的处置**：**两个方向都不断言**（「待重估」）——
- 现在的可观察差异（833ms）**确实**主要来自秒级回退（global 实测 + 本域 javap 复核一致）⇒ **「架构级改造必要」没有活体证据支持**；
- 但**「架构级改造不必要」也没有活体证据支持**（E62 修好之后**还没复测**）⇒ ⇒ **一律标「待重估」**。

⇒ 🟢 **2026-10-26 更新：`align/global` 已经把这一轮做完了** ⇒ **本域的「待重估」已有着落**：

| 项 | global 第四十六轮 `96d94db` 的结论 | 本域的读法 |
|---|---|---|
| E38② 原修法（handler 挪受控 executor + 超时 interrupt） | 🔴 **原修法否决**（**可观察面已归零** ⇒ 「必须做架构级改造」**不再被观测支持**） | ✅ **本域台账的「架构级 · 影响 10 个 rpc 组件」那条描述已过期** ⇒ 见下方「本域要改的那句」 |
| 第③层的真身 | 🔴 窄化成 **E65**（出向 gRPC 不继承 deadline）+ **E66**（Redis 调用不感知 deadline，**含写**） | ✅ **本域实测适用性**（下方逐条） |
| 定性 | 🟡 **global 本轮不改定性**（须专门一轮重估 + 给 Jason 立项建议） | ➡️ **本域不代 global 改定性**；本域只把**我域台账里引用它的地方**订正到与实测一致 |

📌 **本域实测：E65 / E66 对 admin-rpc 的适用性**（**2026-10-26 独立核实，不采信转述**）：

| global 条目 | 落点 | **admin-rpc 实测** | 结论 |
|---|---|---|---|
| **E65**（出向 gRPC 不继承 deadline） | `RpcInterceptor.onHalfClose` 只绑 `DeadlineState`、**从不绑** `GoStyleRequestDeadline` ⇒ gRPC 路径 `applyTo` 是 **no-op** | 🔴 **不命中** —— `grep -rn "Stub\|BlockingStub\|FutureStub\|GrpcClient" service/users/admin/admin-rpc/src/main/` ⇒ **零命中** ⇒ **admin-rpc 无任何出向 gRPC 调用** | 🟢 **本域无 E65 残余**（落点是 `service/common`，**global 单写权**） |
| **E66**（Redis 调用不感知 deadline，**含写**） | `service/common` 无任何 Redis 期限载体 + 各域 Redis 调用点 | 🔴 **命中** —— admin-rpc **唯一**一处 Redis 写：`AdminDomainService.logout` 里的 `stringRedisTemplate.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds))`<br>（key/value 与 Go `logoutlogic.go` 的 `SetexCtx(l.ctx, "admin:jwt_blacklist:"+token, "banned", expireTime)` **逐字同契约**）<br>⚠️ 该方法开头虽有 `checkDeadline()`，但那是**前置守卫**（`DeadlineState.isExpired()` 才拒进）⇒ **Redis 写本身仍不感知 deadline** | 🔴 **就是我域已登记的 `DRIFT-ARPC-010`** ⇒ ⇒ **同一件事、两个编号**（我域 `DRIFT-ARPC-010` ＝ global `E66`）⇒ **已交叉引用**（见 §6） |

⇒ 📌 **本域要改的那句**（**别照搬旧描述**）：
本域台账此前写第③层是「**架构级 · 影响 10 个 rpc 组件**」⇒ ⇒ **该描述已过期** ⇒
**可观察面在 SQL 路径上已被 E62 归零** ⇒ 第③层剩下的**不是**「必须做架构级改造」，而是**两条更窄的**：
**E65**（出向 gRPC · **本域不命中**）与 **E66**（Redis · **本域命中 = `DRIFT-ARPC-010`**）。
⇒ ⚠️ **但定性仍不是本域能改的**（global 单写权 + global 自己标了「不改定性」）⇒ ⇒ **本域只订正自己台账里引用它的那句**。

## 5. 🔴 元发现：**我域那 4 条新守门，对 E62 这类缺陷是「瞎」的**

| 我的守门 | 验什么 | E62 能不能穿过 |
|---|---|---|
| ① 插件链里有那两个拦截器 | **在不在链里** | ❌ 穿不过（E62 时它们**在**链里） |
| ② 翻译器挂在 `Executor` 执行点 | **挂载点对不对** | ❌ 穿不过（E62 时挂载点**是对的**） |
| ③ 负控 NC-A / ④ 负控 NC-B | 判据本身是不是摆设 | ❌ 穿不过 |
| **（缺）生产形态下毫秒路径真的 engage** | **真的走了毫秒分支** | 🔴 **会穿过** ⇒ **E62 就是从这扇门过去的** |

⇒ ⇒ **形状与 global 在 comment-rpc 踩的完全同款**：
他们的 E46 守门用 `DriverManagerDataSource`（**无池**）⇒ 反射成功 ⇒ **守门绿、生产坏**
⇒ ⇒ **我域的 4 条守门若再加一条，必须用「真 `HikariDataSource`」形态**（global 已在 `align/global` 上这么改了）。

🟢 **2026-10-26 更新：global 第四十五轮 `180f865` 已经把这条守门补上了** ⇒ **本域无需再补**：
· **守门形态**：真 **HikariCP 池** + 真 PG ⇒ **新增断言直接读驱动上的 `getQueryTimeoutMs()`**
  （pgjdbc 的 `setQueryTimeout(int s)` 内部就是 `setQueryTimeoutMs(s * 1000L)` ⇒ **能精确区分毫秒路径与秒级路径**）
· **自证断言**：必须真的走池化代理路径（`captured.getClass().getName()` 含 `Hikari`）⇒ **否则这条守门又会变成「守门绿、生产坏」**
· **变异 3 条全精确命中**（脚本入仓 `mutate-deadline-precision.py`
  —— **该脚本在 `align/global` 上 ⇒ 主干上查不到，路径悬空是预期状态，不是台账事实错误**）：
  **M1** 生产代码不 unwrap ⇒ 红 4 条（**旧 E46 守门升级到真池后也红了** ⇒ 这就是「守门绿、生产坏」被闭上的证据）·
  **M2** 把真 PG 守门**换回无池**（**E62 当初骗过自己的形态**）⇒ 红 ⇒ 证明「守门必须跑在生产形态上」这条自证**有牙** ·
  **M3** 毫秒路径偷偷按秒级取整 ⇒ 红 ⇒ 证明守门**不是**「只要设了超时就算过」

⚠️ **本域为什么不自己补这条守门**：
① 它验的对象在 **`service/common`**（`DeadlineQueryTimeoutInjector`）⇒ **global 单写权** ⇒ **本域无权改**；
② **它现在会红**（主干的注入器是 pre-E46、**根本没有毫秒路径**）⇒ **红着的测试不能进仓**（违反 `skipped=0` 与 build 绿）;
⇒ ⇒ **本域只做：核 + 台账订正 + 报告**（§5.3）。
⇒ 🟢 **且这条守门已经落在共享层了** ⇒ ⇒ **它对 admin-rpc 同样生效**（admin-rpc 也走同一个 `DeadlineQueryTimeoutInjector`）
⇒ ⇒ **本域「无权补」的代价 = 0** —— **守门在共享层 ⇒ 10 个 rpc 组件一起受益**（含 admin-rpc）。

## 6. 跨域归属

- **E62 的修法落点** = `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java`
  ⇒ **global 单写权** ⇒ **本域只报告、不改**。
- **E62 的影响面** = **10 个注册了 `DeadlineQueryTimeoutInjector` 的 rpc 组件**（**2026-10-26 本域实测枚举**，非估算：
  `article-rpc` · `comment-rpc` · `favorite-rpc` · `follow-rpc` · `hot-rpc` · `like-rpc` · `message-rpc` · `task-rpc` · **`admin-rpc`** · `user-rpc`
  —— 每个都在自己的 `*RpcApplication` 里 `@Import`）⇒
  ⇒ **本域受影响**（admin-rpc 的运行时日志就是 global 贴的那条 WARN）。
- **global 已修好**（**核对 ref = `align/global` @ `0516774`**（第四十八轮收尾）⇒ **E62 落在第四十五轮 `180f865`**）：
  `unwrap` 后再探测 + 共享层 `DeadlineQueryTimeoutInjectorTest` 从 **7 条 → 13 条**（**2026-10-26 实测**：主干/本域分支 **7**、`align/global` **13**，净增 6，其中 **3 条**显式标 `🔴 E62`：池化代理藏方法 / unwrap 链终止 / unwrap 失败回退）
  + comment-rpc 守门改真 `HikariDataSource` + **真池真 PG 守门 + 3 条 E62 变异**
  ⇒ ⇒ **探针已复跑取真值**（短档两侧差 **22/18/12ms ⇒ PASS**）⇒ ⇒ **「等它合回 + 复测探针」这一步已经走完一半**（复测已在 `align/global` 上做过 ⇒ **合回主干后本域主干上再跑一次即可**）。
- 🔴 **同一个漂移、两个编号（跨域撞号）**：**我域 `DRIFT-ARPC-010`（admin `logout` 的 Redis 写不绑 deadline）＝ global `E66`**（Redis 调用不感知 deadline，含写）
  ⇒ ⇒ **不是两条漂移，是一条** ⇒ ⇒ **本域台账已加交叉引用** ⇒ **别把它当两条分别销账**。
- ⚠️ **E65**（出向 gRPC 不继承 deadline）**本域不命中**（admin-rpc 无出向 gRPC 调用 ⇒ 实测零命中）⇒ **本域无 E65 残余**。

## 7. 本轮改动清单（rpc.md 侧）

- **零代码改动** ⇒ 不需要重跑测试门禁。**但仍重跑了**（2026-10-26 22:32）：admin-rpc **105/0/0/0** · `skipped=0` · **BUILD SUCCESS**。
- **本文件里「毫秒精度 ⇒ `Δ_end` ≈ 0」/「观察依据已失效」这类表述，全部加了限定**（指向本节）
  ⇒ **实测计数**：**12 行**（其中 2 行各挂 2 个限定标记）= **14 个限定标记**
  ⇒ ⚠️ **计数口径**：按**出现次数**数是 15（多出的 1 个是本节 §7 自己那句「每处都标了『已被 §9 订正』」⇒ **不是限定标记**）⇒ **别把 15 当成限定数**。
- **§9（本节）= 权威口径**；前面的段落保留历史留痕（含我被推翻的那两句），但**每处都标了「已被 §9 订正」**。
- 🔴 **本节自己写完后，又自查出并订正了本节内部的 2 处数字错误**（**留痕，不删**）：
  | 原写 | 订正 | 怎么发现的 |
  |---|---|---|
  | 「E62 的影响面 = **11 个**用 mybatis 的组件」 | **10 个 rpc 组件**（逐一 `grep -rln "DeadlineQueryTimeoutInjector" --include="*.java" service/` **枚举**，非估算） | 枚举实测 |
  | 「本文件里 **10 处**加了限定」 | **12 行 / 14 个标记** | `grep -o "§9 订正" \| wc -l` **按出现次数**数（`grep -c` 数的是**行**，两行各挂 2 个 ⇒ 少算） |
  ⇒ 📌 **又一条元教训**：**写进台账的数字，落地前必须用工具重新数一遍** ——
  ⇒ 「凭印象写个数」和「实测写个数」在**文档里长得一模一样** ⇒ **读者无法分辨**（§3.3 的同族：*代码/文档里的断言都是断言*）。
- ⚠️ **回信 global**：① 本域**独立核实 E62 成立**（HikariCP 7.0.2 + `javap` 代理类无毫秒方法 + 实现 `Wrapper` ⇒ 修法可行）
  ② **我的「`Δ_end` 口径订正确实被你们的实测确认**（`Δ_start=+1139ms` ≈ 1 秒 = 秒级取整签名）**」
  ③ 🔴 **但我据此写的「`Δ_end` ≈ 0 / 观察依据已失效」在生产上不成立** ⇒ **那是我自己的一次二次订正**（已落 §3）
  ④ **我域 4 条守门对 E62 是「瞎」的**（同款盲区）⇒ 已落 §5 ⇒ **本域无权补**（落点在 `service/common`）
  ⑤ **E38② / 第②层 ⇒ 标「待重估」**（两个方向都不断言）⇒ 已落 §4。
- 🆕 **坑位 `P-17`**（`docs/Pitfalls/users/admin/rpc.md`）：「**按机制推导**的结论，必须逐条核它在目标环境里的前提」⇒
  ⇒ **本轮 §3 那次二次订正的元教训，已固化成可复用判据**（本节 §3 里的「见 `P-17`」指针由此生效）。

---

## 8. 🔴 本轮新增：**我自己台账里的 3 处行号锚点，被我自己的上一个提交推走了**

**发现时机**：为了给 `DRIFT-ARPC-010` 补 global `E66` 的交叉引用，逐条重核本域指向 Java 的行号锚点。

### 8.1 现象

`DRIFT-ARPC-010`（§1.7）的三个锚点：
`AdminDomainService.java` 的**三个行号锚点**（`:228` = 声称的 Redis 写 · `:230-233` = 声称的 B-1 守卫 · `:220/228/230-234` = 声称的 Evidence strength）
⇒ ⚠️ **下面这行里出现的行号都是「已失效的历史值」**（现行锚点见 §1.7 与本节 §8.3）⇒ **别当活锚点抄走**。

⇒ **实测全部指到了 `login` 路径**（`:228` = `PasswordUtil.Outcome pwOutcome =`；`:230-233` = 密码匹配分支；`:220` = `throw Status.INTERNAL.withDescription("DB查询失败")`）
⇒ ⇒ **与该段文字声称的内容完全不符**。

### 8.2 根因：**它们写下时是对的，被「我自己这一轮」推走了**

追 `AdminDomainService.java` 的历史：

| 提交 | Redis 写所在行 | 说明 |
|---|---|---|
| `f314fdd`（**写下这三个锚点的那一版**） | **:228** | ✅ **锚点当时完全正确**（`:220` = `checkDeadline()`、`:230-234` = B-1 守卫 + `catch (Exception e)` + `log.error` + INTERNAL 抛出） |
| `bd70865`（**本域上一轮**） | **:267** | 🔴 该提交给本文件**加类级 javadoc「B-1 放行分支」段** ⇒ **+94 / −28 行** ⇒ **全部锚点整体 +39** |

⇒ ⇒ 🔴 **「我改了文件」与「我的锚点失效」发生在同一轮、同一提交里** ⇒ ⇒ **而没有任何机制提示**：
- 文件还在 ⇒ 路径存在性检查（第 1 层）**照常通过**；
- 行号仍在文件范围内 ⇒ **越界判定也照常通过**；
- 机器复核第 4 层**只查 Go 侧 `file:line`** ⇒ **本域自己的 Java 锚点根本不在它的射程内**。

⇒ ⇒ **本域的 Java 行号锚点是一块「机器不管、且会被自己改动打破」的空白区**。

### 8.3 本轮处置

- ✅ **逐条重核本域全部 37 处指向 Java 的行号锚点**（脚本把锚点处的实际源码打出来逐条比对）：
  | 文件 | 锚点数 | 结果 |
  |---|---|---|
  | `AdminDomainService.java` | **20** | 🔴 **3 处失效**（均在 `DRIFT-ARPC-010` 段）⇒ **已修** |
  | `AdminCreateAtomicService.java` | **7** | ✅ 全部对得上 |
  | `AdminServiceGrpcImpl.java` | **2** | ✅ 全部对得上 |
  | `AdminRpcApplication.java` | **3** | ✅ 全部对得上 |
  ⇒ ⇒ **只有 `DRIFT-ARPC-010` 那 3 处是错的**（都源自 `bd70865` 的 +39 行位移）。
- ✅ **修法不只是改数字**：三处锚点**同时补上符号名**（`AdminDomainService.logout`）⇒ ⇒ **即使将来再位移，符号名仍然可定位**。
- 🆕 **坑位 `P-18`**：「**你这一轮改的文件，会让你自己写的行号锚点在同一轮里失效**」。

---

## 9. 🔴 收到 global 第四十二轮回执 —— 🔴 **它引的 ref 落后 6 轮**，且它计划的「下一轮」**早已做完**

**收到**：global 第四十二轮回执（2026-10-26 22:3x 送达本域会话）⇒ 内容是答复我更早的一封回执。

### 9.1 它引的 ref 已过期（**两处远端实测**）

| 项 | global 回执里写的 | **本域实测（`git ls-remote`）** |
|---|---|---|
| `align/global` 当前 ref | **`51d6abd`**（并说「我这边此后已推了第四十一轮 `9404818`、第四十二轮 `51d6abd` ⇒ 请 re-fetch」） | 🔴 **origin 与 upstream 两处都是 `0516774`** ⇒ **`51d6abd` 是 `0516774` 的祖先**（落后 **6 轮**） |

⇒ ⇒ **`0516774` 之后还有 6 个提交**，其中**两个直接是关于 users/admin 域的**：

| 提交 | 轮次 | 内容 | 对本域的影响 |
|---|---|---|---|
| `a0b24a3` | 第四十三轮 | users/admin 域 owner 回执第二回 ⇒ **E63**（第 5/6 层写死 `service/task`）已修 | ✅ **就是我上轮回信报的那条** ⇒ **已闭合** |
| `76c3b26` | 第四十四轮 | users/admin 域 owner 回执第三回 ⇒ 归因提请成立 ⇒ 订正 | ✅ 本域的提请**被采纳** |
| **`180f865`** | **第四十五轮** | 🔴 **E62 落地**（unwrap 到驱动实现类）+ **探针复跑取真值** ⇒ **短档两侧差 22/18/12ms ⇒ PASS** | 🔴 **直接回答了本节 §2 的「待复测」** |
| **`96d94db`** | **第四十六轮** | 🔴 **E38② 重估 ⇒ 原修法否决** + 第③层窄成 **E65 / E66** | 🔴 **直接回答了本节 §4 的「待重估」** |
| `97a1fa8` | 第四十七轮 | favorite 回执 ⇒ **E68**（第 4 层补「行号合法但指错文件」判据） | 🟢 **本域已用新判据复跑 ⇒ 零命中**（见 §9.3） |
| `0107845` / `0516774` | 第四十八轮 | 共享复核器（**E69** `--entity-all`）+ 收尾 | 🟢 **本轮用的正是这版工具** |

### 9.2 它说的「下一轮：E62 落地」**已经做完了**

回执末尾写：「**下一轮：E62 落地**（探测前先 unwrap 到驱动实现类，已用 `javap` 验证可行）· 守门**必须用真 HikariCP 池 + 真 PG**」
⇒ ⇒ 🔴 **而 E62 正是在第四十五轮 `180f865` 落地的**，且守门**确实是**真池 + 真 PG（还多了 3 条 E62 变异）
⇒ ⇒ **它列的「下一轮」在它自己的分支上已完成 2 轮**。

### 9.3 本轮独立复核（不采信转述）

| 核项 | 方法 | 结果 |
|---|---|---|
| `align/global` 的真实 tip | `git ls-remote origin/upstream align/global` | ✅ **两处均 `0516774`** ⇒ **回执里的 `51d6abd` 过期 6 轮** |
| E62 修法落在哪个提交 | `git log --reverse 51d6abd..0516774 -- DeadlineQueryTimeoutInjector.java` | ✅ **`180f865`（第四十五轮）** ⇒ `unwrapDriverStatement` 在 `0516774` 里 **2 处**、在 `51d6abd`/`9404818` 里 **0 处** |
| 本域 Go 锚点能否过 E68 的新判据 | 最新版工具 `--entity-all`（第四十八轮 `E69` 的全仓模式） | ✅ **对 admin 域零命中** ⇒ **本域锚点没有「行号合法但指错文件」** ⇒ 🟢 **这是 global 新判据给本域的一次正面确认** |
| 本域当前处在状态矩阵哪一格 | `grep unwrapDriverStatement` + 单测条数 | ✅ **本域分支与主干都是 pre-E46** ⇒ **§2 第 1 行**；**`align/global` @ `0516774` 是第 3 行** |

### 9.4 纪律（采纳 global 的，并加上本域的同款教训）

🔴 **「回执方核的是哪一版 ref，必须在回执里标出来」** —— global 在这封回执里提的（`PIT-GLOBAL-053` 的**反向形态**：
不是我们抄他们的行号，是我们**引用了他们的旧快照**）。
⇒ ⇒ 📌 **本域的双向版本**：
- **发出方**：回执里凡引用别域的 ref / 行号 / 计数 ⇒ **必须写明「核对的 ref = X（日期）」**；
- **收到方**：收到回执 ⇒ **先 `ls-remote` 核那封回执引的 ref 是否还是当前 tip** ⇒ **不是 ⇒ 那封回执的「当前状态」类断言全部按「历史快照」读**。

⇒ 🟢 **这与本轮 §8 是一对**：§8 是「**自己改文件 ⇒ 自己的锚点失效**」；§9 是「**别人推分支 ⇒ 别人的回执过期**」
⇒ ⇒ **两者都是「锚点/引用的保质期不由引用者控制」** ⇒ ⇒ **同一个坑位 `P-18`**（见 `docs/Pitfalls/users/admin/rpc.md`）。

---

## 10. 🔴 第四十四轮回执消化 —— **三封回执连续三次过期** ＋ **合回前必看：`align/global` 与主干在我域子树上的 delta**

> **本节性质**：**纯台账订正**（零代码改动）⇒ 门禁沿用上一轮实测（admin-rpc **105/0/0/0** · `service/common` **174/0/0/0** · `skipped=0**，2026-10-26 22:32 实跑）。

### 10.1 🔴 **三封回执，连续三次都过期**（⇒ 这不是意外，是模式）

| # | 回执 | 它说 `align/global` 当前是 | **两处远端实测** | 落后 |
|---|---|---|---|---|
| 1 | 第四十二轮 | `51d6abd` | **`0516774`** | **6 轮** |
| 2 | 第四十三轮 | `a0b24a3` | **`0516774`** | **7 轮** |
| 3 | **第四十四轮**（本节） | `76c3b26` | **`0516774`** | **6 轮** |

⇒ 🔴 **三封都过期，且都因为同一个机制**：对方会话在**按队列顺序回放历史消息** ⇒
⇒ **它回你第 N 封消息时，用的是「处理第 N 封消息那一刻」的 ref** ⇒ ⇒ **ref 不会随队列前进而更新**。
⇒ ⇒ 📌 **这解释了为什么回执越积越旧**：**回执的 ref 是「回执生成时」的快照，不是「送达时」的现状**。
⇒ ⇒ 📌 **也解释了它为什么说「你们域分支 `1d79f56` 未合回」** ⇒ **也过期**：本域 `8a1f355` 已合回（2026-10-26 18:0x），现状 `f397319`。

⇒ 🟢 **它的 ④ 里那句「下一轮：E62 落地」** ⇒ 🔴 **E62 已在第四十五轮 `180f865` 落地**（详见 §9.1）⇒ **它排的「下一轮」在它自己的分支上早已做完 2 轮**。

⇒ 📌 **⇒ 本域的处置（已固化为 `P-18` 形态 B）**：
- **发出方**：回执里凡引用别域的 **ref / 行号 / 计数** ⇒ **必须写明「核对的 ref = X（日期）」**；
- **收到方**：收到回执 ⇒ **先 `git ls-remote` 核那封回执引的 ref 是否还是当前 tip** ⇒ **不是 ⇒ 那封回执的「当前状态」类断言全部按「历史快照」读**；
- 🔴 **推论（本轮新增）**：**回执的「当前状态」类断言会随队列深度一起变旧** ⇒ ⇒ **队列越深，回执越不能当现状用** ⇒ ⇒ **别把「三封都对得上我之前那几封」误当成「它现在说的也对」**。

### 10.2 ✅ **merge-tree 实测 ⇒ 我上一轮那个「4 条守门会丢」的担心，被推翻**（留痕，不删）

🔴 我上一轮（P-18 形态 A 那节）担心：「`align/global` 合回主干时，`AdminRpcContainerWiringTest.java` 必然冲突 ⇒ global 的 8 条版会覆盖我那 4 条守门」。

⇒ 🟢 **本轮用 `git merge-tree --write-tree` 干跑（`3cb52ad` + `0516774`）**：

| 核项 | 实测 | 结论 |
|---|---|---|
| 合并是否有冲突 | **EXIT=0，产出合并树 `b0a6e1a`** | ✅ **无冲突**（两边改的是**不同区段**：global 改前面、我的 4 条追加在后面 ⇒ git 可自动合并） |
| 合并树里 `AdminRpcContainerWiringTest` 的 `@Test` 数 | **12** | ✅ **我的 4 条 B-1 守门全在**（插件链 / 挂载点 / NC-A / NC-B）⇒ **守门不会丢** |
| 合并树里 `AdminDomainService` 的「B-1 放行分支」类级 javadoc | **29 处命中** | ✅ **在** |
| 合并树里 `JsonbTypeHandler` | **被删** | 🟢 **但所有引用已同步**（`UserEntity.extra_info` 的 typeHandler + `AdminEntity` / `JsonTextTypeHandler` / `AdminRpcColumnTypeParityTest` 的 javadoc 全部改写）⇒ ⇒ **不会编译失败** |

⇒ ⇒ 📌 **教训**：**「文件两边都改过 ⇒ 一定冲突」是错的** —— 冲突取决于**改的区段是否重叠**。
⇒ ⇒ **在报告「合回会出问题」之前，先跑一次 `merge-tree` 干跑**（零成本、不动工作树）⇒ **别把推测当结论报出去**（本域自己的 P-15）。

### 10.3 🔴 **合回前本域必看：`align/global` 与主干在我域子树上的 13 个文件 delta**

`git diff 3cb52ad 0516774 --stat -- service/users/admin` ⇒ **13 files changed, 912 insertions(+), 433 deletions(-)**

| # | 文件 | delta | 本域视角 |
|---|---|---|---|
| 1 | `admin-rpc/.../service/AdminDomainService.java` | **160** | ✅ 合并后**我的类级 javadoc 在**、global 的改动也在 ⇒ 需 review |
| 2 | `admin-rpc/src/test/.../bootstrap/AdminRpcContainerWiringTest.java` | **201** | ✅ 合并后 **12 条 @Test**（我的 4 条守门在） |
| 3 | `admin-rpc/src/test/.../AdminRpcColumnTypeParityTest.java` | **129** | 🟡 与 B10 配套（列类型改 text） |
| 4 | `admin-rpc/.../mapper/JsonbTypeHandler.java` | **−98（删除）** | 🟢 **global 的 B10**（`ff80214`）⇒ 见 §10.4 |
| 5 | `admin-rpc/.../mapper/JsonTextTypeHandler.java` | +40 | 🟢 B10 配套（合并两个 handler） |
| 6 | `admin-rpc/.../domain/AdminEntity.java` | 13 | 🟢 B10 配套（javadoc） |
| 7 | `admin-rpc/.../domain/UserEntity.java` | 18 | 🔴 **B10 的落点**：`extra_info` 的 typeHandler 改 `JsonTextTypeHandler` ⇒ 见 §10.4 |
| 8 | `admin-rpc/.../service/AdminCreateAtomicService.java` | 3 | 🟡 |
| 9 | `admin-rpc/pom.xml` | 8 | 🟡 |
| 10 | `admin-api/.../config/WebMvcConfig.java` | 2 | 🟡 |
| 11 | `admin-api/src/main/resources/application.yml` | **+33** | 🟡 |
| 12 | `admin-rpc/src/test/.../AdminRpcGrpcInterceptorWiringTest.java` | **+323（新增）** | 🔴 **global 写在我域目录下的守门**（`3e91b53` 第二十七轮 L6） |
| 13 | `admin-rpc/src/test/.../AdminRpcSlowThresholdGateTest.java` | **+317（新增）** | 🔴 **global 写在我域目录下的守门**（`0e2efaf` 第二十三轮 L2） |

⇒ ⇒ 📌 **三条本域须知**：
1. 🟢 **我的 4 条守门在合并后仍在**（§10.2 实测）⇒ **不用抢跑**；
2. 🔴 **本域台账里写死的测试条数会变**（`105` ⇒ 会加上 global 那 2 个守门类的条数）⇒ ⇒ **合回主干后本域台账的条数必须重数**（§6.3 状态表纪律）;
3. 🔴 **本域台账里「RAG 侧 `extra_info` 是 jsonb」「RAG 侧时间列是 `timestamp`」那两行已过期** ⇒ 已订正（见 §10.4）。

### 10.4 🟢 **global 的 B10 落在我域源码树里** ⇒ 连带两件事

🔴 **事实**：`ff80214`（global 第十八轮 · B10）把 `admin-rpc/.../domain/UserEntity.java` 的 `extra_info` typeHandler
从 `JsonbTypeHandler`（**jsonb 绑定**：`ps.setObject(…, Types.OTHER)`）改成 `JsonTextTypeHandler`（**text 绑定**），
并**整体删除** `admin-rpc/.../mapper/JsonbTypeHandler.java` ⇒ **全部引用同步改写**（合并树实测无编译问题）。

⇒ 📌 **为什么这落在我域**：`users_user` 表的实体与 mapper **在 admin-rpc 里**（`admin-rpc/.../domain/UserEntity.java`）
⇒ ⇒ **users/**user** 域的 `DRIFT-USERRPC-V-003` 销掉的是 DDL 那一格（`01_create_user.sql`）**，
⇒ ⇒ **代码那一格（写路径参数绑定）一直残留，且残留的文件在 admin 域的目录里** ⇒ ⇒ **global 在我域目录里把它修掉了**。

⇒ 🔴 **⇒ 已通知 users/user 域**：他们台账 §431 / §1499 写「两侧都按 text 写」⇒ **DDL 侧确实**，但
**「代码侧」在主干上当时不成立**（admin-rpc 的 `UserEntity.extra_info` 仍按 jsonb 绑定）⇒ **该格已由 global B10 修掉（**2026-10-26 第五十轮合流后主干上已生效**）。

⇒ 🟢 **本域台账的两处订正**（**组件级语义对照表 · Schema 两行**）：
| 行 | 原写 | 订正 |
|---|---|---|
| 时间列类型 | RAG 侧 `timestamp`（无时区）⇒ **OBSERVABLE DIFFERENCE** | 🟢 **两侧都 `timestamptz`** ⇒ **EQUIVALENT**（我域 §「DRIFT-ARPC-004 ①② 已修」早已修好 ⇒ **本表原先写的是修前状态**） |
| extra_info 列类型 | RAG 侧 `jsonb` ⇒ **OBSERVABLE DIFFERENCE** | 🟢 **DDL 两侧都 `text`** ⇒ **EQUIVALENT**；⚠️ **残留是「写路径参数绑定」那一格**（`UserEntity.extra_info` 走 jsonb 绑定）⇒ **global B10 已闭合（**2026-10-26 第五十轮合流后主干上已生效**） |

### 10.5 🔴 **P-18 形态 A 的更深一层：这张表用「裸行号」，比全路径更脆** —— 本轮又抓出 **6 处失效**

🔴 **上轮（§8）我只扫了「带文件名」的锚点（37 处）** ⇒ ⇒ **漏掉了本域对照表里 RAG 侧的**裸 `:NNN`****
⇒ ⇒ **本轮补扫 ⇒ 7 处裸行号里 6 处已失效**：

| 台账行 | 维度 | 旧（失效） | 现行（已改） |
|---|---|---|---|
| 230 | CreateAdmin · 空 inviteCode → PermissionDenied | **旧值 `:396-399`**（指向 `AdminCreateAtomicService` ⇒ **越界**，该文件只有 206 行）⇒ ⚠️ **历史值·已失效，别当活锚点抄** | `AdminDomainService.createAdmin` 的 inviteCode 非空校验（`:472-474`，**事务外**） |
| 231 | CreateAdmin · 空 username 不拦截 | **旧值 `:407`**（指向 `AdminCreateAtomicService` ⇒ **越界**）⇒ ⚠️ **历史值·已失效** | `AdminDomainService.java:485`（只 trim，DRIFT-008） |
| 236 | Logout · 黑名单 key/TTL/失败码 | `AdminDomainService.java:218-227`（**实为 login 的 DB 错误块**） | `AdminDomainService.logout`（`:258-277`） |
| 240 | GetUserList · 负 offset/limit 抑制 | `:781-798`（落在 javadoc） | `AdminDomainService.goStylePagination`（`:902`；调用点 `:831`） |
| 243 | UpdateUser · email **不** trim | `:875-877`（实为 getUserList 的尾巴） | `AdminDomainService.java:1013`（对比 `updateSelf` 的 `:385` 是 `email.trim()`） |
| 245 | UpdateUser · 重读后返回 | `:896-909`（落在 javadoc） | `AdminDomainService.java:1051-1054` |
| 249 | ResetUserPassword · 不检查 RowsAffected | `:996` | `AdminDomainService.doResetUserPassword`（`:1174`） |
| 250 | ResetUserPassword · default-password 为空 | `:976-979`（实为 updateUser 的代码） | `AdminDomainService.doResetUserPassword` 的 W3 段（`:1137` 起） |

⇒ ⇒ 📌 **⇒ 8 处已全部改成「符号名 + 现行行号」** ⇒ ⇒ **复检：裸行号 5 处 0 可疑 · 全路径 37 处 0 越界**。

🔴 **⇒ 为什么「裸行号」比「全路径」更脆**：
| | 全路径（`Foo.java:123`） | **裸行号（`:123`）** |
|---|---|---|
| 机器能查吗 | 第 1 层只查**路径存在**（不查行号） | ❌ **连路径都没有 ⇒ 机器连「存在性」都无从查** |
| 人能自己定位吗 | 知道去哪个文件找 | ❌ **依赖「上文最近一次点名的文件」⇒ 隔一行就可能认错文件** |
| 失效时能自检吗 | ❌ 不能 | ❌ **更不能** |
⇒ ⇒ 📌 **判据**：**裸行号是「台账里最不可查的锚点形式」** ⇒ ⇒ **凡是指向 RAG 侧代码的锚点，一律带文件/符号名**（AGENTS.md §3.1 对共享层的要求，本轮把它用到了**本域的对照表**上）。

### 10.6 🟢 global 的新守门 `check-ledger-tables.py` ⇒ 对本域**零违规**，且**本域非空转**（已自证）

🔴 **背景**（global 第四十四轮 ⑦）：它的落账脚本用「长文本 `replace`」插行 ⇒ **会静默吃掉上一行的行首**
⇒ ⇒ **它自己台账有 6 处「表格结构塌陷」**（内容一个字没丢 ⇒ **肉眼扫不出、内容层守门查不出**）⇒ ⇒ 新增了结构层守门。

⇒ ✅ **本域实测（2026-10-26）**：
| 核项 | 结果 |
|---|---|
| `check-ledger-tables.py --root <本域 worktree> --all` | ✅ **「台账表格结构完好」** ⇒ **本域零违规**（⇒ global 那个坑**我没踩中**：本轮改台账用的是行级编辑 + 追加，不是长文本 `replace`） |
| 退出码语义 | ✅ **干净态 0 / 变异态 1** ⇒ **参与退出码** ⇒ 可当门禁用 |
| 🔴 **非空转自证（本域自己做）** | 把「无行首内容行」注入**本域对照表的真表格块**（`rpc.md` 的 Schema 行）⇒ 守门**精确命中** `docs/DRIFT/users/admin/rpc.md:263` ⇒ **退出码 1** ⇒ ⇒ **守门对本域有牙**（⇒ 已还原，`git diff` 为空） |
| ⚠️ **该守门的 `--mutation-test` 对本域不适用** | ❌ 它是 **global 自家 `_README.md` 专用**（找不到「带日期行首」的时间线行 ⇒ 报「变异前置失败」）⇒ ⇒ **别的域要用它，得自己写一条本域的变异**（本域已做，如上） |

⇒ 🟢 **顺带**：global 说的「用脚本改台账时，插行请用行级插入（按行号/锚点整行），别用长文本 `replace`」
⇒ ⇒ **本域采纳**（本轮 §10.4/§10.5 的所有订正**都是行级替换** + 逐条复检）。

### 10.7 本轮改动清单（rpc.md 侧）

- **零代码改动** ⇒ 门禁沿用 2026-10-26 22:32 实测（admin-rpc **105/0/0/0** · `service/common` **174/0/0/0** · `skipped=0`）。
- **组件级语义对照表**：**8 处失效的 RAG 侧锚点**全部改成「符号名 + 现行行号」；**Schema 两行（时间列 / extra_info）**从「修前状态」订正为现状。
- **新增 §10**（本节）：三封回执连续三次过期 · merge-tree 实测推翻上轮担心 · 13 文件 delta · B10 落我域 · 裸行号盲区 · global 新守门对我域的结论。
- ⚠️ **回信 global**：① **三封回执连续三次过期**（表）⇒ **回执的 ref 是「生成时」的快照，且会随队列深度一起变旧** ⇒ ② **merge-tree 干跑结论**：合回**无冲突**、我域 4 条守门**全在**、类级 javadoc **在**、`JsonbTypeHandler` 删除**引用全同步** ⇒ ⇒ **我上轮那个「守门会丢」的担心不成立** ⇒ ③ **13 文件 delta 表** + **B10 落我域**（⇒ 已通知 users/user 域）⇒ ④ **global 新守门对我域零违规 + 我域自证非空转**（注入真表格块 ⇒ 精确命中 `rpc.md:263` + 退出码 1）⇒ ⑤ **该守门的 `--mutation-test` 是 global 自家专用** ⇒ 别的域要自己写一条。

---

## 11. 🟢🔴 **2026-10-26 第五十轮双向合流后** —— 前提变了 ⇒ 本域台账一批断言必须重写

> **本节性质**：**纯台账订正**（**零代码改动**）⇒ 但**门禁重跑了**（合流后必须重跑 ⇒ 见 §11.4 的一个重要发现）。

### 11.1 🔴 **合回这件事，发生在本轮的中途** ⇒ 台账的「当前状态」整块作废

**核实（2026-10-26 23:1x，本域实测）**：

| 项 | 实测 |
|---|---|
| 主干 `do-my-own-gh` 的 HEAD | **`4f839f9`** = 「merge(global): `align/global` → `do-my-own-gh`（**第五十轮 · 双向合流 · 零冲突**）」 |
| 主干是否含 `align/global` 的 E62 | ✅ **含** —— 主干 `DeadlineQueryTimeoutInjector` 实测含 `unwrapDriverStatement`（2 处）；注入器单测 **13** 条（3 条显式标 `🔴 E62`） |
| 主干是否含 global 的 B10 | ✅ **含** —— 主干 `admin-rpc/.../mapper/` 下**已无** `JsonbTypeHandler.java`（只剩 `JsonTextTypeHandler.java`）⇒ `users_user.extra_info` 写路径已是 **text 绑定** |
| 我域 4 条 B-1 守门 | ✅ **全在**（主干 `AdminRpcContainerWiringTest` = **12** 个 `@Test`）⇒ **§10.2 的 merge-tree 预测被主干实证** |
| 我域分支 | `align/users-admin` 已 merge 主干（`5ef464a`，**零冲突**） |

⇒ ⇒ 🔴 **⇒ 本域台账里「未合回 / 待合回 / 生产上毫秒路径未生效」这类「当前状态」断言，全部在合流那一刻失效** ⇒ ⇒ **本节逐条重写**。

### 11.2 ✅ **E62 的真值（global 第四十五轮 `180f865` 探针复跑）+ 阈值订正**

| 档位 | E62 未修 | **E62 修后** |
|---|---|---|
| 短档 RAG `Δ_end` | **+833 ms** | **+19 / +18 / +14 ms** |
| **短档两侧差** | 833 ms | **22 / 18 / 12 ms**（三次跑）⇒ **PASS** |
| 长档（对照组） | −5 / +5 ms | **−9 / −8 ms**（对照组仍成立） |

⇒ 🟢 **⇒ 我 §9 §4 那个「待重估」现已有着落**：**E38② 的「PG 层面可观察差距」已归零** ⇒
⇒ ⚠️ **global 本轮不改定性**（剩下更窄的「业务线程不被取消」那层 ⇒ 且 **Go 侧同样不杀 goroutine** ⇒ 可能降级为「框架限制」）
⇒ ⇒ **本域同样不代 global 改定性**（落点 `service/common` ⇒ global 单写权）。

🟢 **阈值订正（global 挖出来的，对本域直接相关）**：
旧默认阈值 **1000ms**，而 **E62 回归信号实测就是 833ms** ⇒ **833 < 1000 ⇒ E62 全坏了探针照样 PASS** ⇒ ⇒ **阈值形同虚设**
⇒ ⇒ 已按**实测值**重校准为 **150ms**（对噪声 22ms 有 **6.8×** 余量 · 对回归信号 833ms 有 **5.5×** 余量）
⇒ 📌 **判据（本域采纳）**：**阈值不是「判 PASS/FAIL」的，是「判『有没有回归』的」⇒ 必须小于回归信号，否则它测不出任何东西。**

⇒ ⚠️ **⇒ 但主干上的复测仍待跑**（P-17 的纪律）：
**「合回不改变行为」这个前提本身需要核** ⇒ ⇒ **合流把注入器换了 ⇒ ⇒ 行为可能变** ⇒ ⇒
⇒ **⇒ 必须在主干上真跑一次探针**（探针已进编排 ⇒ 走 global **E48** 的常规复跑；探针是**本域资产** `tools/difftest/exp-grpc-admin-deadline-probe.sh`）
⇒ ⇒ **在跑出来之前，主干那一格属「机制推导 + 别处实测支持」，不是本域在主干上的实测**（§9 §2 状态矩阵已按此标注）。

### 11.3 ✅ **本域台账的批量订正（合回前提兑现）**

| 订正项 | 数量 | 怎么改的 |
|---|---|---|
| 「生产上毫秒路径**未生效**（E62）⇒ 这句只在 E46 + E62 都合回后成立」 | **9 处**（9 行） | ⇒ 改为「**E46 + E62 均已合回主干** ⇒ **生产上毫秒路径已生效** ⇒ **这句现在成立**」+ 保留「主干复测待跑」的限定 |
| 「**生产上观察依据仍在**（实测 `Δ_end=+833ms`）⇒ 这句只在 E46 + E62 都合回后成立」 | **5 处**（5 行） | ⇒ 改为「**观察依据已归零**（`align/global` 实测 22/18/12ms）⇒ **这句现在成立**」+ 同一条限定 |
| 「待合回 `do-my-own-gh`」（E30 desc 文案 / E38 第①层期限计算 / `d531abd` / `22a9111`） | **6 处** | ⇒ 改为「**2026-10-26 第五十轮双向合流已合回主干**」 |
| Jason「暂不合回」的拍板记录 | **1 处** | ⇒ 标注「**该拍板已被后续的第五十轮双向合流推翻**」⇒ 并补「**主仓代码现在已是新串 ⇒ 基于主仓的验证现在看得到修复了**」 |
| 「复测的前置条件」 | **1 处** | ⇒ 两个前置条件（合回 + 探针进编排）**现已全部满足 ⇒ 本条可在主干上复测** |
| 对照表 **Schema 两行**（时间列 / extra_info 列类型） | **2 处** | ⇒ 时间列 ⇒ **两侧都 `timestamptz` ⇒ EQUIVALENT**；extra_info ⇒ **DDL 两侧都 `text` + 写路径绑定已随 B10 闭合 ⇒ 完全 EQUIVALENT** |
| §9 §2 状态矩阵 | **1 块** | ⇒ 由 3 行扩为 **4 行**（新增「**主干 · 现在**」那一格，标「预期 ≈ 0 · 主干复测待跑」） |

⇒ ⇒ **合计：25 处「当前状态」断言被合流这件事一次性作废并重写** ⇒ ⇒ **本节是它们的新权威口径**。

### 11.4 🔴 **一个必须记的元发现：合流后第一次门禁红了，但那是「假红」** —— 而它差点让我误判

**经过（2026-10-26 23:2x，本域实测）**：

| 步骤 | 结果 |
|---|---|
| ① 合流后第一次跑门禁（`-pl admin-rpc,service/common -am test`） | 🔴 **`Tests run: 112, Failures: 0, Errors: 11` + BUILD FAILURE** ⇒ 伴随大量 `StatusRuntimeException: UNAVAILABLE / Connection refused: /127.0.0.1:1001` 栈 |
| ② 逐类 surefire `.txt` 报告求和 | ⚠️ **112 条 · 0 failures · 0 errors** ⇒ **与聚合行的「Errors: 11」矛盾** |
| ③ 回看原始失败日志 | 🔴 **一个 `<<< ERROR` 标记都没有** ⇒ ⇒ **没有任何一个测试用例失败** |
| ④ 单独跑 `-pl admin-rpc`（先 `rm -rf surefire-reports`） | ✅ **112/0/0/0 · EXIT=0** |
| ⑤ **`clean` 后全量重跑**（`-pl admin-rpc,service/common -am clean test`） | ✅ **BUILD SUCCESS**：`service/common` **261/0/0/0** · `admin-rpc` **112/0/0/0** · 0 skipped |

⇒ ⇒ 📌 **⇒ 结论：那 11 个「errors」是 **`target/` 陈旧**造成的记账 artifact**（**同款 `PIT-GLOBAL-037`：Maven 依据 mtime 判定「class 比 source 新」⇒ 跳过重编译 ⇒ 复跑跑的仍是旧字节码**）⇒ ⇒ **不是真失败**。

⇒ ⇒ 🔴 **三条判据（本域采纳）**：
1. 🔴 **聚合行与逐类报告对不上时，以逐类报告 + `<<<` 标记为准** ⇒ ⇒ **「Errors: 11」不等于「11 个用例挂了」**；
2. 🔴 **判「是不是真失败」之前，先跑一次 `clean`** ⇒ ⇒ **陈旧 `target/` 会让任何一次「合流后首次门禁」不可信**；
3. 🔴 **别把「日志里刷出来的 ERROR 栈」当成「测试失败」** ⇒ ⇒ 本例那 760 条 `Connection refused` 栈**全部来自 `log.error`**（Nacos 连不上时的常规噪音）⇒ ⇒ **日志噪音与测试结论是两件事**。

### 11.5 🔴 **合流同时带出一个真缺陷（我域代码）⇒ 登记为 `DRIFT-ARPC-012`**

**现象**：`service/users/admin/admin-rpc/.../bootstrap/NacosRegistration.java` 的 `register()` 只 catch
**`NacosException | UnknownHostException`**，而 **nacos-client 3.x** 在服务不可达时抛的是
**`com.alibaba.nacos.shaded.io.grpc.StatusRuntimeException`（UNAVAILABLE: io exception）** ⇒ ⇒ **不被捕获、直接逃出 `@EventListener`**。

⇒ 🔴 **与既有声明矛盾**：`AdminRpcContainerWiringTest` 的注释明写「指到必然拒连的地址：NacosRegistration **捕获 NacosException 只记日志不中断启动**」
⇒ ⇒ **实际捕获不到** ⇒ ⇒ **那条注释所描述的保证当前不成立**。

⇒ 🟡 **但「逃逸」当前**不**中断启动**（Spring 的事件多播器只记日志）⇒ ⇒ **不是门禁阻断项**（§11.4 的 clean 重跑全绿）⇒ ⇒ **本轮不改代码**。

⇒ 📌 **为什么本轮不改（§3.2 的硬要求）**：
「注册失败 ⇒ **fail-open**（记日志继续启动）」这个语义**本域没有 Go 出处** ——
Go 侧 `service/user/admin/rpc/admin.go` 的注册是 go-zero 的 `reflection.Register(grpcServer)`（**dev/test 模式的 grpc 服务注册**，不是 Nacos），
其失败路径是 **`log.Fatal`（进程退出）= fail-fast** ⇒ ⇒ **「该 fail-open 还是 fail-fast」本身就是一条未定口径** ⇒ ⇒
**现在去调 catch 的宽窄 = 无出处 idiomatization**（AGENTS.md §3.2 点名的 11 处历史回归正是这一类）⇒ ⇒ **登记 + 上报，不擅自改**。

⇒ 🔴 **跨域面（实测枚举，非估算）**：**同款 `catch (NacosException e)` 形状**出现在
`like-rpc`（2 处）· `comment-rpc`（2 处）· `follow-rpc`（2 处）· `message-rpc`（2 处）· `favorite-rpc`（2 处）
⇒ ⇒ **⇒ 全仓口径必须统一 ⇒ ⇒ 落点在各域目录 ⇒ ⇒ 本域只报告、改自己那一处也需要先有 Go 出处** ⇒ ⇒ **已上报 global**（它是唯一能定「统一口径 + 谁改」的角色）。

### 11.6 ✅ **合流后本域当前数字（带核对日期 · §6.3 状态表纪律）**

| 项 | 数字 | 核对日期 / 口径 |
|---|---|---|
| `admin-rpc` 门禁 | **112 / 0 / 0 / 0** · `skipped=0` | **2026-10-26 23:2x 实跑**（`clean` 后）⇒ **比合流前 105 多 7 条** = global 那 2 个守门类落进本域目录（`AdminRpcGrpcInterceptorWiringTest` 3 条 + `AdminRpcSlowThresholdGateTest` 2 条）+ 其余差 2 条 ⇒ ⇒ **台账里写死的条数必须跟着更新** |
| `service/common` 门禁 | **261 / 0 / 0 / 0** · `skipped=0` | 同上 ⇒ **比合流前 174 多 87 条**（global 侧新增） |
| 本域 4 条 B-1 守门 | ✅ **在**（`AdminRpcContainerWiringTest` = 12 个 `@Test`） | 主干实测 |
| E62 注入器 | ✅ **在主干**（`unwrapDriverStatement` × 2；注入器单测 13 条） | 主干实测 |
| B10（`JsonbTypeHandler` 删除 + 写路径改 text） | ✅ **在主干** | 主干实测 |
| 本域三档台账 U+FFFD / 失效链接 | **0 / 0** | 2026-10-26 实测 |
| 机器复核（global `0107845` 工具 + `--entity-all`） | 层 0~4 全通过 · 层 1 78 引用 0 缺失 · 层 4 186 条不悬空 · EXIT=0 | 2026-10-26 实测 |
| `check-ledger-tables.py --all` | 零违规 · EXIT=0 | 2026-10-26 实测 |
| RAG 侧锚点复检 | 裸行号 5 处 0 可疑 · 全路径 37 处 0 越界 | 2026-10-26 实测 |
| 🟢 **锚点复检（锚点重定位后 · 2026-10-27）** | **口径 A（带文件名）184 处展开 · 活锚点 0 越界**（残留告警只出现在 §11.8.2 审计表的「旧锚点」列 = **故意照抄旧文本**）<br>**口径 B（对照表 RAG 列裸行号）4 处 · 0 可疑** | **2026-10-27 实测**<br>脚本：口径 A 按**路径片段消歧**（全仓同名 `GlobalExceptionHandler.java` 有 9 个、**同名 `UserMapper.java` 2 个** ⇒ 只写文件名 = 指向不明 ⇒ §11.8.5 规则 2） |
| 🟢 **本轮锚点订正量** | **77 处替换**（`rpc.md` 77 行 / `api.md` 12 行 / `_shared.md` 5 行）+ **3 处补订** | 2026-10-27 实测（`git diff --stat`） |

### 11.7 本轮改动清单（rpc.md 侧）

- **零代码改动** ⇒ 但**门禁重跑了两轮**（一次未 `clean` ⇒ 假红 ⇒ 一次 `clean` ⇒ 绿）⇒ ⇒ **§11.4 的元发现已立档**。
- **批量订正 25 处「当前状态」断言**（§11.3）⇒ 全部带核对日期。
- **新增 §11**（本节）· **新增 `DRIFT-ARPC-012`**（Nacos 注册失败的异常类型与口径，§11.5）。
- ⚠️ **回信 global**：① 🔴 **「仍未合回 / 基于主仓的验证看不到修复 / 别误读成没改对」这条提醒现在已过期且会误导** ⇒ **E62 已上主干**（`4f839f9`）⇒ **基于主仓的验证现在看得到修复** ⇒ ② **四封回执连续四次过期**（42nd `51d6abd` / 43rd `a0b24a3` / 44th `76c3b26` / 45th `180f865`；实际 tip 一直是 `0516774`）⇒ ③ **E62 真值已入库**（22/18/12ms · 阈值 150ms · 「阈值判的是有没有回归，不是判 PASS/FAIL」）⇒ ④ 🔴 **合流后首次门禁是「假红」**（`target/` 陈旧 ⇒ 同款 PIT-GLOBAL-037）⇒ ⑤ 🔴 **`DRIFT-ARPC-012`**（Nacos 注册失败口径未定 ⇒ **全仓 6 个域同款形状** ⇒ 请 global 定统一口径 + 谁改）⇒ ⑥ **本域台账 25 处「当前状态」断言已按合流事实重写**。

### 11.8 🔴 **锚点集体失效** —— 合流把 `AdminDomainService.java` 推了 14 行，**我上轮刚修好的锚点又被推走了**

**这一节是本轮（2026-10-27）唯一的实质产出**，且它是 **P-18 的第二形态**：上一轮（P-18 形态 A）是我**自己**的改动推走了自己的锚点；这一轮是 **合流把别人（global）的改动带进来 ⇒ 一次性推走几百行**。

#### 11.8.1 事件（实测，非推断）

| 项 | 事实 |
|---|---|
| 合流提交 | `5ef464a`（`align/users-admin` ← `origin/do-my-own-gh` @ `4f839f9`） |
| 被推走的文件 | `admin-rpc/.../service/AdminDomainService.java` ⇒ **1331 行 → 1345 行（+14）** |
| 位移是**分段**的 | `git diff --unified=0` 实测 3 段：旧行 ≤400 **Δ0** · 401–1021 **Δ+2** · 1022–1258 **Δ+12** · ≥1259 **Δ+14** ⇒ ⇒ **任何「统一位移」算法都会算错** |
| 改动内容 | 3 处，全是 `extra_info` 的 CAST 相关（global B10 `ff80214` 的尾巴）：`:398` 注释 +2 · `:1016` 的 `setSql` +11 · `:1254` javadoc +1 |
| 受害面 | 本域三档台账（`rpc.md` / `api.md` / `_shared.md`）里**全部裸行号锚点** |

> 🔴 **形态 A（自推走）与本轮形态的差别**：自推走只错 1–2 行、**人眼容易发现**；合流推走是**分段位移**，**靠「整体位移量」推算必错**。
> ⇒ 本轮**没有**用「旧内容指纹 + 位移推算」来定位（那法子在**大改区**会指错，见 §10.5 记录），而是**逐个锚点打开现行源码核对语义**。

#### 11.8.2 ✅ **全量订正（67 处替换 · 逐条实测 · 2026-10-27）**

| 台账位置 | 旧锚点 | 现行锚点 | 落点（现行源码） |
|---|---|---|---|
| §3 走查块 · OP-CREATE-ADMIN | `:392-465` | `:457-545` | `createAdmin` / `doCreateAdmin` |
| §3 · invite 非空校验 | `:396-399` | `:474-477` | `trimmedInvite.isEmpty()` → `PERMISSION_DENIED` |
| §3 · `Snowflake.nextId()` | `:431` | `:509` | `uid = Snowflake.nextId();` |
| §3 · 跨 Bean 调用 | `:446-447` | `:525-526` | `createAtomicService.createAdminAtomic(...)` |
| §3 · `createAdminAtomic` 全体 | `:110-179` | `:110-205` | 方法首行 → 方法末行 |
| §3 · invite 过期判定 | `:124-127` | `:132-135` | `OffsetDateTime now` + `isAfter(now)` |
| §3 · W2 username 冲突 | `:130-135` | `:137-143` | `adminMapper.selectOne` → `UsernameTakenException` |
| §3 · W3 bcrypt | `:145-158` | `:153-167` | `PasswordUtil.encrypt` |
| §3 · W4 insert | `:169` | `:178` | `adminMapper.insert(admin)` |
| §3 · W5 标 used | `:172-176` | `:180-184` | `set(used_by_uid)` + `set(used_at)` |
| §3 · 其余 ⇒ INTERNAL | `:448-461` | `:531-541` | `doCreateAdmin` 的 catch 链 |
| §3 · OP-CREATE-INVITE-CODE | `:492-554` | `:572-654` | `createInviteCode` / `doCreateInviteCode` |
| §3 · TTL / 重试 / continue | `:66/510` · `:536-539` | `:103/:605` · `:607` · `:633-636` | 常量 + 用点 |
| §3 · 生成 code | `:556-560` | `:656-660` | `generateInviteCode()` |
| §3 · OP-LOGIN / OP-LOGOUT | `:160-196` / `:212-230` | `:197-242` / `:258-277` | `login`+`doLogin` / `logout` |
| §3 · GET-USER / GET-SELF | `:640-655` / `:243-258` | `:760-794` / `:290-320` | `getUser` / `getSelf` |
| §3 · OP-GET-USER-LIST | `:675-755` | `:796-902` | `getUserList` |
| §3 · keyword 不 trim | `:682-683` | `:803-804` | `hasKeyword` + `pattern` |
| §3 · COUNT 独立查询 | `:717-723` | `:837-844` | `selectCount` |
| §3 · offset 回绕 | `:711` | `:832` | `long offset = (page - 1) * pageSize;` |
| §3 · `goStylePagination` | `:781-798` | `:904-921` | 方法定义 |
| §3 · OP-UPDATE-USER | `:817-910` | `:940-1074` | `updateUser` / `doUpdateUser` |
| §3 · email 不 trim | `:875-877` | `:1011-1014` | `wrapper.set(UserEntity::getEmail, email)` |
| §3 · OP-UPDATE-SELF | `:274-369` | `:322-433` | `updateSelf` |
| §3 · updateSelf email trim | `:328-335` | `:379-385` | `email.trim()` |
| §3 · BAN/UNBAN/DELETE/RESET | `:572-1009` | `:672-1205` | 四个方法的并集 |
| §3 · seeder 重读 | `:139-148` | `:138-152` | `isUniqueViolation` → 重读分支 |
| §4 表 · 跨 Bean 调用 | `:446` | `:525` | 同上 |
| §4 表 · invite 非空校验 | `:472-474` | `:474-476` | 同上 |
| §4 表 · 空 username 只 trim | `:483` | `:485` | `trimmedUsername` |
| §4 表 · uid 生成位置 | `:431` | `:506-509` | `// Snowflake uid（事务外）` |
| §4 表 · TTL/前缀/重试 | `:66,512,559` | `:103/106/109`（用点 `:605/:607/:659`） | 三个常量 |
| §4 表 · Login 三态 | `:163-191` | `:205-242` | `doLogin`（NOT_FOUND `:212` · 密码三态 `:230-237`） |
| §4 表 · keyword 不 trim | `:682-683` | `:803-804` | 同上 |
| §4 表 · `goStylePagination` | `:902`（调用点 `:831`） | `:904`（调用点 `:833`） | 定义 + 调用点 |
| §4 表 · UpdateUser 逐字段 set | `:840-881` | `:973-1040`（`:994`/`:1000`/`:1013`） | set 块 |
| §4 表 · UpdateUser email set | `:1011` | `:1013` | `wrapper.set(getEmail, email)` |
| §4 表 · UpdateUser 重读 | `:1042` | `:1051-1054` | `// W6 重读` |
| §4 表 · UpdateSelf email trim | `:328-335` | `:379-385` | `email.trim()` |
| §4 表 · ban RowsAffected | `:588-591` | `:698-701` | `if (rows == 0)` |
| §4 表 · DeleteUser NotFound | `:934` | `:1100` | `String.format("用户%d不存在", uid)` |
| §4 表 · ResetUserPassword W4 | `:1174` | `:1186` | `// W4 update（不检查 RowsAffected）` |
| §4 表 · ResetUserPassword W3 | `:1137` | `:1165-1171` | 密码加密失败分支 |
| §4 表 · Seeder 密码常量 | `:94` | `:131` | `DEFAULT_ADMIN_PASSWORD = "admin"` |
| §4 表 · createAdminAtomic 步骤序 | `:116,130,148,169,172` | `:116,138,156,169,178,181` | 6 个步骤标记 |
| §6 · §19 Serialization | `AdminServiceGrpcImpl:250-282` | `AdminServiceGrpcImpl:265-297` | `toProtoAdmin` + `toProtoUser` |
| §9 · 中文 description 清单 | 22 个裸行号 | **改用方法名 + 现行行号**（48 处中文 description，`grep -c` 实测） | 全文件 |
| §9 · ARPC-008 | `UserEntity.java:109` | `UserEntity.java:136` | `private Long status = 0L;` |
| §9 · ARPC-008（另一处） | `UserEntity.java:85` | `UserEntity.java:136` | 同上 |
| §9 · ADMINAPI-011 | `GlobalExceptionHandler.java:192` | `:196` | `headers.set(CONTENT_TYPE, "text/plain; charset=utf-8")` |
| §9 · A-1（user-api） | `GlobalExceptionHandler.java:216` | `:287` | `headers.setContentType(new MediaType(TEXT_PLAIN, …))` |
| §9 · 用户表名 | `UserEntity.java:17-18` | `UserEntity.java:75` | `@TableName("users_user")` |
| §10.5 表 · 右列 | `:483` / `:1011` / `:1042` | `:485` / `:1013` / `:1051-1054` | 现行值列（**左列历史值不动**） |
| `api.md` × 3 | `AdminRpcClient.java:25` | `AdminRpcClient.java:142` | `@GrpcClient("admin-rpc")` 注解行 |
| `api.md` | `GlobalExceptionHandler.java:216` | `:287` | 同上（user-api） |
| `_shared.md` | `GlobalExceptionHandler.java:216` | `:287` | 同上（user-api） |
| `_shared.md` U-7 | `:635/676/969/1116` + `:1015` | `:688/730/1040/1192` + `:1088` | `userMapper.update(...)` / `userMapper.delete(...)` |

#### 11.8.3 ⚠️ **历史快照一律只标注、不改数值**（否则就是篡改历史）

§7 的 `DRIFT-ARPC-001~006` 是 **2026-10-23 首轮对照的原始发现记录**（第 998 行明示「原始记录一字未删」）⇒
**那里的行号是当时的快照**，改写它们会**让历史记录看起来像是在描述今天的代码** ⇒ **本轮一律保留原值 + 加标注**：

| 位置 | 处理 |
|---|---|
| §7 整节 | 加节级 banner：**行号是 2026-10-23 快照 · 已失效 · 现行位置见 §11.8** |
| `DRIFT-ARPC-002`（`AdminServiceGrpcImpl.java:219`） | 标注 **历史值·已失效** + 指出现行构造点 `:234`（那行 `setSuccess(true)` **已被 `FIX-ARPC-002` 删除**） |
| `DRIFT-ARPC-003`（`AdminDomainService.java:976-979`） | 标注 **历史值·已失效** + 补现行 `:1165-1171` |
| `DRIFT-ARPC-004`（`JsonbTypeHandler.java:59`） | 标注 **历史值·已失效**（**该文件已被 global B10 整体删除**）+ 补现行调用点 `:403` / `:1033` |
| §「拦截器链」代码块（`AdminRpcApplication.java:97-101`） | 标注 **历史值·已失效**（那是「只有 1 个拦截器」时的写法）+ 补现行 `:105-113` |
| §3 走查块 `SideFX` 句 | 原写「**不推进** `update_time`」⇒ **该句是 2026-10-23 的状态**（`FIX-ARPC-001` 同轮已修）⇒ 已改为**带日期的现状陈述** |

#### 11.8.4 🔴 **顺带订正的一处过期判定**（不是行号问题）

§4 表「CreateAdmin · **update_time 推进**」那行原写 RAG 侧「**永不推进** / **OBSERVABLE DIFFERENCE**」⇒
**已过期**：`FIX-ARPC-001`（2026-10-23 修复轮）已给 admin-rpc 全部 UPDATE 补上 `update_time = CURRENT_TIMESTAMP`
⇒ **实测 6 处**（`grep -c` 2026-10-27：AdminDomainService **5** + AdminCreateAtomicService **1**）⇒
主路径 **EQUIVALENT**；残留「**参数全空时 Go 不发 UPDATE、RAG 仍发**」边界仍是 **OBSERVABLE DIFFERENCE**
（`FIX-ARPC-001` §「已知残留分歧」，**未修**，本轮不动）。
⇒ ⚠️ **证据强度 = 静态源码·RAG 侧**（双侧活体对拍**未做**）⇒ 按 §3.3 口径**不得**写成「已闭环」。

#### 11.8.5 ✅ **此后锚点的书写规则（AGENTS.md §3.1 + P-18 的落地）**

1. **指向代码的锚点一律「符号名 + 行号」**，行号前带符号：`AdminDomainService.doCreateAdmin`（`:470-545`）⇒
   **裸 `:NNN` 只允许出现在「同一段落的紧邻上下文已锁定同一文件」的情形**。
2. **跨文件引用必须带完整路径 + 文件名**（`admin-api/.../advice/GlobalExceptionHandler.java:196`）⇒
   全仓有 **9 个**同名 `GlobalExceptionHandler.java`、多个同名 `UserMapper.java` ⇒ 只写文件名 = 指向不明。
3. **历史快照不改数值、只加标注**（见 §11.8.3）⇒ 台账要同时保住「当时看到什么」和「现在是什么」。
4. **每次动本域源码 ⇒ 同一批次内重扫本域台账的行号锚点**（本轮实测：**67 处替换**来自一次合流 ⇒
   「改 1 个文件 ⇒ 平均牵连数十个台账锚点」是**常态**，不是意外）。
5. **跨域文件（`service/common`、users-common、user-rpc…）只引用不修改** ⇒ 引用它们的锚点**保质期不由本域控制**
   ⇒ 见 P-18 形态 B。
6. ⚠️ **§11.8.2 的「旧锚点」列故意照抄旧文本**（审计表要保真）⇒ ⇒ 扫描器扫到那里会报「越界 / 同名歧义」
   ⇒ ⇒ **这是预期噪声，不是缺陷** ⇒ 扫描脚本须把 §11.8.2 整表排除（否则每次复扫都要人工解释一遍）。

### 11.9 🟢 **2026-10-27 追加：锚点重定位（本轮第二轮，与 §11.8 同批）**

- **§11.8**（锚点集体失效的元记录 + 全量映射表 + 书写规则）· **`P-19`**（合流推走锚点 · 位移是分段的 ⇒ 推算法必错）。
- **77 处锚点替换 + 3 处补订**（`rpc.md` / `api.md` / `_shared.md`）⇒ 全部**逐条打开现行源码按语义核对**，
  **不用位移推算**（合流的位移是**分段**的：Δ0 / +2 / +12 / +14 ⇒ 任何统一位移模型都会指错）。
- **历史快照一律只标注、不改数值**（§7 的 `DRIFT-ARPC-001~006` 原始记录 + 第 998 行「原始记录一字未删」）。
- **顺带订正 3 处过期活断言**（不是行号问题）：
  ① §4 表「CreateAdmin · update_time 推进」原写「永不推进 / OBSERVABLE DIFFERENCE」⇒ **已过期**（`FIX-ARPC-001` 已修 · 实测 6 处）；
  ② §「抽样复核」表「`UserEntity` 用 jsonb handler」⇒ **已过期**（global B10 已改 `JsonTextTypeHandler`）；
  ③ 同表「`users_user.extra_info` 仍是 JSONB」⇒ **已过期**（`01_create_user.sql:41` 建表就是 TEXT）。
- **零代码改动** ⇒ 本轮**未重跑门禁**（改的是台账文本，不是代码 ⇒ 门禁结论不变；下次动代码时再跑）。

---

## 12. 2026-10-27：Redis 在 **rpc 侧**的同机制不同形态 ⇒ `DRIFT-ARPC-013`（**已修** · 域内自闭环）

### 12.0 触发：G2 的回执 + **同族横向对比**

G2（message / like / task lane）发来回执，说它在自己域的 **rpc 侧**查到一条
「Redis 断连把整个 RPC 拖到超时」的漂移 ⇒ 我把**本域所有用 Redis 的组件**并排核了一遍
（**同族横向对比**是查出「配置口径不一致」最省力的手段 —— 逐个对着真源读看不出「这一族该是多少」）：

| 组件 | Redis 用途 | `timeout`（命令） | `connect-timeout`（连接） | 断连策略 |
|---|---|---|---|---|
| `admin-api` | **读**黑名单（fail-open） | 3s ✅ | 5s ✅ | `REJECT_COMMANDS` ✅（`DRIFT-ADMINAPI-012`） |
| `admin-rpc`（本组件） | **写**黑名单（logout） | 3s ✅ | 5s ✅ | 🔴 **本轮补** |

⇒ **api 侧已补齐、rpc 侧没补** ⇒ 同一个 Redis 机制，**两种后果**。

### 12.1 🔴 漂移本体：断连时命令被**排队** ⇒ 挂到 3s ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时**

**Go 侧形状**

```
Go service/user/admin/rpc/internal/logic/logoutlogic.go:35-40
    err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
    if err != nil {
        logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)
        return nil, status.Error(codes.Internal, "注销失败,Redis写入异常")    ← **快速失败 ⇒ INTERNAL**
    }
为什么快：go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:14 maxRetries = 3
          · :32-39 red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns, ...})
          —— **不设** DialTimeout / ReadTimeout ⇒ 每次重试**立即失败** ⇒ 整体快速失败（实测 ~1.7s，仍在 2s 预算内）
```

**RAG 侧形状**（`AdminDomainService.logout:258-277`）

```
catch (StatusRuntimeException e) { throw e; }                                       ← B-1 守卫
catch (Exception e) { log.error("admin logout Redis setex failed", e);             ← 原始异常进日志（与 Go 同款）
                      throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException(); }
```

⇒ **错误面（status + description）本来就与 Go 逐字一致** ⇒ 漂移**不在错误面**，在**失败耗时**：

| 形态 | 机制 | 耗时 | 客户端看到 |
|---|---|---|---|
| **Go** | go-redis `MaxRetries=3` ⇒ 每次重试立即失败 | ≈1.7s | `INTERNAL`「注销失败,Redis写入异常」 |
| **RAG 修前** | Lettuce `DEFAULT` + `autoReconnect=true` ⇒ **命令排队等重连**（`DefaultEndpoint:149` / `:837-852`）⇒ 挂到 `commandTimeout: 3s` | ≈3s | 🔴 **1015「服务繁忙」**（`DEADLINE_EXCEEDED`，3s ≫ `rpc-timeout: 2s`） |
| **RAG 修后** | `REJECT_COMMANDS` ⇒ **立即拒命令**（`DefaultEndpoint:314`） | ≈1ms | `INTERNAL`「注销失败,Redis写入异常」 ✅ **与 Go 同形** |

⚠️ **只改「断连」，不改「连着但不回包」**：后者两侧都等满各自的读超时（Go `ReadTimeout` 3s / RAG `timeout: 3s`）
⇒ 都 ≫ 本组件 2s 预算 ⇒ **两侧都是 DEADLINE_EXCEEDED** ⇒ 已一致 ⇒ 两类故障**分别**对齐。

### 12.2 修法与落点

| 项 | 内容 |
|---|---|
| **落码** | `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/config/RedisDisconnectedBehaviorConfig.java`（**新建** · 域内副本）：`LettuceClientOptionsBuilderCustomizer` ⇒ `REJECT_COMMANDS` —— ⚠️ **2026-10-29 真实性复核（指针订正 · 原值保留）**：**该域内副本文件已不存在**（Redis 收编轮删除 5 份域内副本 ⇒ 共享层接管）⇒ **现行生产代码只在** `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:96`（`@AutoConfiguration`）＋ `:98`（`afterBeanInitialization(LettuceClientOptionsBuilderCustomizer.class, …)`）⇒ 由 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:5` 注册 ⇒ **实测 `find service/users -name "RedisDisconnectedBehaviorConfig.java"` = 0 命中**、`grep -rn "REJECT_COMMANDS" service/users/ --include="*.java" \| grep "/src/main/"` = **0 命中**（其余命中全在 `src/test/` 守门里）⇒ **本行读作「历史落点」**，现行落点见下一行 |
| **落点决策** | 载体文件住在**本域内** ⇒ **域侧自修**，不等 G4（判据：被改的那份文件本来住在哪） |
| **共享层收编** | ✅ 已作为反馈单派给 G4（`GoStyleRedisClientAutoConfiguration` ＋ `AutoConfiguration.imports`）⇒ **共享层那份先合进各域的树**，才能删域内副本 ⇒ 现共 **5** 份域内副本（3 api + 2 rpc）⇒ **收编时须一并删掉**（否则双份配置）<br>🟢 **2026-10-27 订正：收编【已完成】** —— 5 份域内副本**已全部删除**（共享层那份接管；见 [`../vs-common.md` §26](../vs-common.md)）⇒ ⇒ **本行的「现共 5 份副本 / 收编时须一并删掉」是历史状态** |
| **守门** | `AdminRpcRedisDisconnectedFastFailTest`（**新** · 2 例）：① `REJECT_COMMANDS` 真被装配 ＋ Redis 可达时写黑名单通；② 断连（**FIN** 形态）时写命令**快速失败**（<1500ms）**且**异常**不是** `RedisCommandTimeoutException` |
| **故障注入** | ⚠️ **必须是 FIN 不是 RST**：`container.stop()` 发 RST ⇒ Lettuce 立刻失败 ⇒ **走不到** `disconnectedBehavior` 分支 ⇒ 两种配置耗时都是 1~13ms ⇒ **分不出** ⇒ 用**可控 TCP 透传代理**「先关监听口 ＋ 再半关闭已建立连接」复刻 live 形态 |
| **变异验证** | `REJECT_COMMANDS` → `DEFAULT` ⇒ 用例 2 红（实测 ≈3000ms）⇒ **绿测试能变红** ✅ |

**✅ 变异实测（M3 · 2026-10-27）**：`REJECT_COMMANDS` → `DEFAULT` ⇒ **2/2 红**，失败原文：

```
AssertionFailedError: Redis 断连后命令必须**快速失败**，实测耗时 3011ms，预算 1500ms。…
  异常链：QueryTimeoutException: Redis command timed out <- RedisCommandTimeoutException:
           Command timed out after 3 second(s)   ==> expected: <true> but was: <false>
AssertionFailedError: 断连策略必须是 REJECT_COMMANDS：… ==> expected: <REJECT_COMMANDS> but was: <DEFAULT>
```

⇒ **3011ms** 正是「命令排队到 `commandTimeout: 3s`」的形状 ⇒ **3s ≫ `rpc-timeout: 2s` ⇒ RPC 先超时**这条推理**被实测坐实**。
⇒ 同批 user-rpc 侧变异（M2）实测 **3012ms** + `RedisCommandTimeoutException: Command timed out after 3 second(s)` ⇒ **两侧同形**。
⇒ ⚠️ 还原用 `md5` 校验（M2/M3 均 `YES`）。

### 12.3 🔴 顺带订正一个本仓多处写错的机制

本仓此前写「Lettuce 在 `connect-timeout` 未设时**回落到 timeout** ⇒ 跟着变成 3s」——**那是错的**。
Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 的**字节码**是：

```java
Duration connectTimeout = getProperties().getConnectTimeout();   // :9
if (connectTimeout != null) {                                     // :16
    builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
}
```

⇒ **null ⇒ 整段不执行** ⇒ 用 Lettuce 自己的默认
`io.lettuce.core.SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION` = **10s**
（`javap -constants` 实测：`DEFAULT_CONNECT_TIMEOUT = 10l`）。
⇒ 本组件的 `connect-timeout: 5s` **配得对**（值不变），但**注释里的机制说法是错的** ⇒ 已加订正注记（**保留原值不改写**）。
⇒ ⚠️ **admin-rpc 的 `connect-timeout` 本来就配了 5s** ⇒ 本组件**没有**「缺 connect-timeout」那一条
⇒ **只有 `user-rpc` 有**（见 `docs/DRIFT/users/user/rpc.md` 的 `DRIFT-USERRPC-V-014` 子项 ①）。

### 12.4 诚实边界

> 🟢 **2026-10-28 订正块（§48.1 · G1 lane）—— 本节第 ① 条与第 ② 条的「未证」状态已部分解除，但**不写 L4****
>
> | 本节原写的「没证」 | 现在的状态 |
> |---|---|
> | ① 「Go 腿是 L1 源码对照，不是双侧活体对拍」⇒ 证据强度 L1 | 🔴 **已解除一半**：本轮**自己**跑了 Go 腿 —— **真源的 `logoutlogic.Logout`**（不是重写）＋ **真源同款 go-zero Options**（`MustNewRedis` ⇒ `MaxRetries:3`/`MinIdleConns:8`、三个 timeout 都不传）＋ **同款 FIN 代理**（先关监听口 + 已建立连接半关闭）⇒ 实测 **1680ms**（本域自己的 Go 腿）＋ **客户端可见 gRPC status = `Internal`「注销失败,Redis写入异常」**（进程内直观测）⇒ ⇒ **Go 腿 = L4（活体）**；⇒ 逐条（含探针源码出处与两条踩坑）⇒ [`vs-common.md` §48.1](../vs-common.md) |
> | ② 「没有做『客户端看到 1015 vs INTERNAL』的端到端双侧活体对拍 ⇒ 另一半（客户端可见值）未实测」 | 🟢 **已解除（2026-10-28 · G1 lane · 本轮落码）** ⇒ ⇒ **RAG 侧的「客户端可见值」现在也是 L4（wire 上实测）** ⇒ ⇒ 配合上一行的 Go 腿 L4 ⇒ ⇒ **本条现在可以写 L4（双侧）** ⇒ ⇒ 逐条（四组实测数字 + A/B 反事实对照 + 两条变异验证 + 三条纪律）⇒ [`vs-common.md` §50](../vs-common.md) |
>
> 🔴🟢 **2026-10-28 第二块订正（§50 · G1 lane）—— 本条（②）已闭合，且【归因】也升到 L4**
>
> **实测四组（全部原文数字）**：
> | 腿 | 断连策略 | 客户端可见 status | 耗时 |
> |---|---|---|---|
> | **Go admin**（真源） | — | `Internal` ＋「注销失败,Redis写入异常」 | **1680ms** |
> | **RAG admin · A 组** | `REJECT_COMMANDS` | `INTERNAL` ＋「注销失败,Redis写入异常」 | **8ms** |
> | **RAG admin · B 组** | `DEFAULT`（同场反例） | **`DEADLINE_EXCEEDED`** ＋「context deadline exceeded」 | **2010ms** |
>
> ⇒ ⇒ **做法**：`@SpringBootTest(classes = AdminRpcApplication.class)` ⇒ `@GrpcService` 真起 gRPC server
> ⇒ ⇒ **真 gRPC 客户端**调 `logout` ⇒ ⇒ **wire 上**抓 status ⇒ ⇒ **L4（不是源码推断）**。
> ⇒ ⇒ **B 组是同场反事实**：唯一变量 = 断连策略（共享层那份 bean 带 `@ConditionalOnMissingBean`
> ⇒ ⇒ 域侧 test 侧提供一个 `DEFAULT` 的 customizer 即可**遮蔽**它 ⇒ ⇒ **域内零落码**）
> ⇒ ⇒ **B 组看到 1015 这件事 = 「A 组的 INTERNAL 是这条机制换来的」的正面证据**
> ⇒ ⇒ ⇒ **本条的修法不是可有可无的严格性偏好，而是「客户端能不能拿到与 Go 一样的 status」的前提**。
> ⇒ ⇒ **两条变异**：M1（摘掉 B 组的 `@Bean`）⇒ **红在前提断言**；M2（排掉共享层自动配置）⇒ **红在目标断言**
> ⇒ ⇒ **归因强度 = L4**（不是 L3：原先 B 组只以「变异形态」存在）。
> ⚠️ **本条的三条纪律**（同一次对照内部必须用**同一份**代理实现 · 故障注入前必须**先建立连接** ·
> **前提断言与目标断言分别做变异**）⇒ 逐条 ⇒ [`vs-common.md` §50.2](../vs-common.md)。
>
> ⚠️ **admin 侧的一处形态说明（2026-10-28 订正）**：**admin 与 user 的 logout 是真源里两份**独立实现**
> （`service/user/admin/rpc/internal/logic/logoutlogic.go` 与 `service/user/user/rpc/internal/logic/logoutlogic.go`
> ⇒ 各自 `SetexCtx`、各自的黑名单前缀（`admin:jwt_blacklist:` / `user:jwt_blacklist:`）、各自的 `AccessExpire`）
> ⇒ ⇒ ⇒ 故本轮**给 admin 单独做了一条 Go 腿探针**（`probe-admin`）⇒ ⇒ **admin 域的 Go 腿也是自己实测的**
> ⇒ ⇒ 实测：**Go 1680ms**（`Internal`「注销失败,Redis写入异常」）· **RAG 3ms** ⇒ ⇒ 与 user 域（Go 1689ms · RAG 0ms）同形。
> ⚠️ **这条订正本身也是一次「差点写错」的拦截**：我最初在台账里写的是「两侧共用同一份真源 logoutlogic」
> ⇒ ⇒ **写之前自己核了一遍真源**（`ls` + `grep` 两个 logoutlogic.go）⇒ ⇒ **发现是两份** ⇒ ⇒ 才没写成假话
> ⇒ ⇒ **教训同「不采信转述」：连自己刚写下的推断也要核**。

- ⚠️ **原写（2026-10-27）：「本轮 Go 腿是 L1 源码对照，不是双侧活体对拍 ⇒ 证据强度 L1」** ⇒ ⇒
  🟢 **2026-10-28 订正：已解除**（Go 腿 L4，见上表 ①）⇒ ⇒ **原文保留**（不重写历史）⇒ ⇒ **只加订正**。
- ⚠️ **原写（2026-10-27）：「没有做『客户端看到 1015 vs INTERNAL』的端到端双侧活体对拍 ⇒
  「修后客户端拿到 INTERNAL」是由代码路径推出 ⇒ 另一半（客户端可见值）未实测」** ⇒ ⇒
  🟢 **2026-10-28 订正：已解除**（RAG 侧的客户端可见值现在是 **wire 上实测** = L4）⇒ ⇒ **原文保留**（不重写历史）⇒ ⇒ 只加订正。
- ⚠️ `admin-rpc` 的 `connect-timeout: 5s` 早于本轮就已配对 ⇒ **本轮没动那个值**；
  本轮在 admin-rpc 侧只落了**断连策略**（`REJECT_COMMANDS`）与其守门。

### 12.5 与相邻域的关系

- **api 侧同款**：`DRIFT-ADMINAPI-012`（`admin-api`）⇒ 机制同、后果不同（503 vs **RPC 撞超时**）。
- **user 域同款**：`DRIFT-USERRPC-V-014`（`user-rpc`，子项 ② 同条、子项 ① 是 user-rpc 独有）⇒ **两侧 rpc 口径一致**。
- **跨域形态**：G2 在 task/like 域查到的是**同一个**形态 ⇒ **三域口径一致** ⇒ 共享层收编后应当是**一份**配置。

### 12.6 本轮回归（**2026-10-27 · 独占窗口**）

- **admin-rpc**：**117** 条（115 + 🆕2）· `failures=0 · errors=0 · skipped=0`
- **本域 6 组件合计 667**（user-api 121 · user-rpc 143 · admin-api 140 · admin-rpc 117 · follow-api 68 · follow-rpc 78）
  ⇒ 逐项明细见 [`../user/rpc.md` §9](../user/rpc.md) ⇒ **两个独立口径交叉验过**（maven 自身模块汇总 ＋ surefire XML 逐文件求和）
- **门禁 5/5 EXIT=0**（lane-ownership / role-model / coupling / doc-links / ledger-count）
- ⚠️ **错峰**：G4 的 52 模块全量与 G3 的 29 模块回归期间本域**不起 maven** ⇒ 独占窗口才跑 ⇒ **无 `EXIT=137`**。
  - 🔴 **订正（2026-10-27 · 第十八轮 · G3 回执）**：「G3 的 **29** 模块回归」**写错了** ⇒ **G3 的域级回归是 12 个模块**（4 域 × 3 组件：article / comment / favorite / hot 各 api+rpc+common）
    ⇒ **「29」是 G4 第八次全量的「有测试模块数」**（52 模块 / 29 个有测试模块 / 2461 条）⇒ 本行是**两处口径串台**。
    ⇒ **本轮已独立复核**：G3 树实测域内 12 模块**全部有 surefire 报告**（`service/{article,comment,favorite,hot}/*-{api,rpc,common}`）⇒ 「12 模块」成立 ⇒ 原「29」作废。
    ⇒ ⚠️ **不影响错峰结论本身**（时间点仍对：本域确实等 G3 跑完才起）⇒ 只订正**数字**，不改**结论**。
    ⇒ 📌 **同款串台的另一处** ⇒ [`../user/rpc.md` §9](../user/rpc.md) 同步订正。


---

## 🔴 `DRIFT-ARPC-014` · **「OS 盲选口」族** · 本组件 **5 处**（2026-10-28 · G1 lane）

> 📌 **机制 / 本机实测（Q3·Q6·Q7·Q8）/ 守门细节 / 效力边界 ⇒ [`docs/DRIFT/users/vs-common.md` §65](../vs-common.md)**
> ⇒ 本条只记**本组件那一份**（不抄机制表，避免两处漂移）。

| 族 | 位置（**改造后**行号） | 状态 |
|---|---|---|
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `AdminRpcGrpcInterceptorWiringTest.java:149` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `AdminRpcGrpcInterceptorWiringTest.java:207` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `AdminRpcSlowThresholdGateTest.java:240` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `AdminRpcLogoutClientVisibleGrpcStatusTest.java:171` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `AdminRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.java:185` | 已改 |

- **危害**：`forPort(0)` / `new ServerSocket(0)` / `RANDOM_PORT` 三种写法都是**让 OS 从临时端口段（49152–65535）里盲选** ⇒ ⇒ 与 Docker 随机发布口**同段**
  ⇒ ⇒ 邻居占**更具体地址** `127.0.0.1:P` 时我方**通配绑定照样成功**（实测：两个监听者并存）⇒ ⇒ 客户端被抢 ⇒ **静默假红**（看着像被测代码炸了，其实压根没被请求到）。
- **修法**：全部改走**域内私有** `support/RealGrpcPort`（`support/RealTomcatPort`）⇒ **段**：`22260–22279`（users 域段的子段）⇒ 段内挑口 ＋ **双地址 bind 探针**（只探通配**检不出**「更具体的占用者」）。
- **段来源**：共享层已预分配给本域的段（`docs/DRIFT/global/vs-common.md` §4.10.19.3）⇒ **共享层台账零改动**（写权归 G4）。
- **守门**：域级 `BlindPortGateTest`（落在 **user-rpc**，`service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java`）**5 条** ⇒ 一并覆盖本组件 ⇒ **本组件不单独建守门**（避免两份规则漂移）。
- **边界**：**本组件生产代码零改动**（全部落在 `src/test/java`）⇒ ⇒ **不构成 Go 对照项**（Go 侧无对应物 ⇒ 无对照义务）。
