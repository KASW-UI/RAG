# DRIFT 台账 · `service/users/admin/admin-rpc`

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
OP-CREATE-ADMIN  AdminDomainService.createAdmin（AdminDomainService.java:392-465）
  Pre:     invite trim 后非空，否则 PERMISSION_DENIED（:396-399，事务外）—— 与 Go 同位置 ✔
  事务外:  Snowflake.nextId()（:431）—— ⚠️ Go 在事务内 W5（顺序差异，见 §4 矩阵）
  跨 Bean: createAtomicService.createAdminAtomic(..., passwordIsPlaintext=true)（:446-447）
           ⇒ 独立 Bean，@Transactional 代理生效
  AdminCreateAtomicService.createAdminAtomic（:110-179）
    W1 selectOne(eq code).last("FOR UPDATE")（:116-120）⇒ 与 Go 的 clause.Locking 等价
       null ⇒ InviteUnavailableException（:121-123）
       usedAt != null || !expiresAt.isAfter(now) ⇒ InviteUnavailable（:124-127）
    W2 selectOne(eq username) 命中 ⇒ UsernameTakenException（:130-135）
    W3 PasswordUtil.encrypt（:145-158）  ← **在 W1/W2 之后**（R5 修复）
    W4 adminMapper.insert（:169）
    W5 标 invite used：set used_by_uid + used_at（:172-176）
  Err:     InviteUnavailable ⇒ PERMISSION_DENIED；UsernameTaken ⇒ ALREADY_EXISTS；
           isUniqueViolation ⇒ ALREADY_EXISTS；其余 ⇒ INTERNAL「DB添加失败」（:448-461）
  SideFX:  **不推进任何表的 update_time**（见 DRIFT-ARPC-001）

OP-CREATE-INVITE-CODE  AdminDomainService.createInviteCode（:492-554）
  结构与 Go 同：校验 operator → TTL 10min（:66/510）→ 3 次循环 → unique violation continue（:536-539）
  → 生成 "ADM-" + HexFormat 大写（:556-560）

OP-LOGIN  :160-196  —— 三态 + PasswordUtil.checkDetailed（MALFORMED_HASH 也报「密码错误」，对齐 Go）

OP-LOGOUT  :212-230  —— 同 key 前缀、TTL 取配置；成功返回 true 但
  AdminServiceGrpcImpl:85 构造 LogoutResp.newBuilder().build() ⇒ success 恒 false ✔ 与 Go 一致

OP-GET-USER / GET-SELF  :640-655 / :243-258

OP-GET-USER-LIST  :675-755
  keyword **不 trim**（:682-683）✔；COUNT 独立查询（:717-723）；
  offset := (page-1)*pageSize 用 long 复刻 int64 回绕（:711）；
  goStylePagination（:781-798）逐字镜像 GORM clause/limit.go 的 LIMIT/OFFSET 抑制规则

OP-UPDATE-USER  :817-910  —— email **不 trim**（:875-877）✔ 与 Go :81-83 一致
OP-UPDATE-SELF  :274-369  —— email **trim 且空白跳过**（:328-335）✔ 与 Go optionalString 一致
OP-BAN/UNBAN/DELETE/RESET-PASSWORD  :572-1009  —— 含 RowsAffected 语义与 Go 逐条一致

OP-SEEDER  AdminSeederBootstrap.ensureDefaultAdmin（:109-159）
  密码取常量 AdminDomainService.DEFAULT_ADMIN_PASSWORD="admin"（:121），**不读配置** ✔（DRIFT-001 已修）
  unique violation ⇒ 重读（:139-148）✔ 与 Go servicecontext.go:62-70 同款
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
| CreateAdmin | **事务代理生效** | 无 AOP | 跨 Bean 调用（AdminDomainService.java:446） | **EQUIVALENT** | 真 PG 容器测试；结构断言 `AdminUpdateSelfAndCreateAdminRegressionTest:158-180` |
| CreateAdmin | **invite 行悲观锁** | `clause.Locking{Strength:"UPDATE"}`（admin.go:131） | `.last("FOR UPDATE")`（AdminCreateAtomicService.java:119） | **EQUIVALENT** | 真 PG：`AdminRpcConcurrencyWiringTest` N=8 ⇒ 1 OK + 7 拒（0 skipped） |
| CreateAdmin | 步骤序（invite→username→bcrypt→uid→insert→标 used） | createadminlogic.go:55,70,79,84,96,105 | AdminCreateAtomicService.java:116,130,148,169,172 | **EQUIVALENT** | 真 PG：`AdminCreatePasswordOrderRegressionTest` 5 条 R5-A/B/C/C2/C3 |
| CreateAdmin | 空 inviteCode → PermissionDenied（事务外） | :48-51 | :396-399 | **EQUIVALENT** | 静态源码 |
| CreateAdmin | 空 username 不拦截 | 无校验（:44 只 trim） | 无校验（:407，DRIFT-008） | **EQUIVALENT** | `AdminSemanticDriftRegressionTest:352` |
| CreateAdmin | **uid 生成位置** | 事务内 W5（:84） | 事务外（AdminDomainService.java:431） | **IMPLEMENTATION DIFFERENCE** | 静态源码；uid 值本就随时间变，不构成可观测差异 |
| CreateAdmin | **update_time 推进** | GORM autoUpdateTime 必进 SET | 永不推进 | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-001 |
| CreateInviteCode | TTL=10min / 前缀 ADM- / 3 次重试 | createinvitecodelogic.go:26,64,109 | AdminDomainService.java:66,512,559 | **EQUIVALENT** | 静态源码 + 3 条 Mockito 单测 |
| Login | 三态 + 密码错 vs hash 畸形同码 | loginlogic.go:43-60；cryptx/password.go:15-18 | AdminDomainService.java:163-191 | **EQUIVALENT** | 静态源码 + `AdminDomainServiceTest` |
| Logout | 黑名单 key/TTL/失败码 | logoutlogic.go:36-42 | AdminDomainService.java:218-227 | **EQUIVALENT** | 静态源码 |
| Logout | **success 字段** | false（空 Resp） | false（空 Builder） | **EQUIVALENT** | AdminServiceGrpcImpl.java:85 |
| GetUserList | keyword 不 trim | admin.go:152-155 | AdminDomainService.java:682-683 | **EQUIVALENT** | 真 PG：`AdminSemanticDriftRegressionTest` 2 条 + 容器测试 1 条 |
| GetUserList | LIKE 双重包裹 | `%kw%` | `%%kw%%`（MP 拼接） | **IMPLEMENTATION DIFFERENCE** | 真 PG（A-U3 已在 §6.2.3 关闭） |
| GetUserList | **负 offset/limit 抑制** | GORM clause/limit.go | `goStylePagination`（:781-798） | **EQUIVALENT** | 真 PG：`AdminRpcPaginationGormSemanticsTest` 7 条 |
| GetUserList | 空结果 wire 形状 | `make([]…,0)` | 无 addList | **EQUIVALENT** | 静态源码（proto3 repeated 两侧都零字节） |
| UpdateUser | 逐字段「非空才 set」 | updateuserlogic.go:56-86 | AdminDomainService.java:840-881 | **EQUIVALENT** | 静态源码 |
| UpdateUser | email **不** trim | :81-83 | :875-877 | **EQUIVALENT** | 静态源码 |
| UpdateUser | **success 字段（wire）** | **false**（未 set） | **true** | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-002 |
| UpdateUser | 重读后返回 | :95-113 | :896-909 | **EQUIVALENT** | 静态源码 |
| UpdateSelf | email trim + 空白跳过 | updateselflogic.go:67-69 | AdminDomainService.java:328-335 | **EQUIVALENT** | `AdminUpdateSelfAndCreateAdminRegressionTest` 3 条 |
| BanUser/UnBanUser | RowsAffected==0 ⇒ NotFound | admin.go:103-109 | AdminDomainService.java:588-591 | **EQUIVALENT** | 静态源码 |
| DeleteUser | NotFound 文本「用户%d不存在」 | deleteuserlogic.go:48 | AdminDomainService.java:934 | **EQUIVALENT** | 静态源码（格式串逐字一致） |
| ResetUserPassword | 不检查 RowsAffected | admin.go:111-114 | :996（不检查） | **EQUIVALENT** | 静态源码 |
| ResetUserPassword | **default-password 为空** | bcrypt("") **成功** ⇒ 设空密码哈希 | 直接 `Internal「密码加密失败」`（:976-979） | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-003 |
| Seeder | 密码来源 | 常量 "admin"（servicecontext.go:18） | 常量（AdminDomainService.java:94） | **EQUIVALENT** | 真 PG：`AdminRpcContainerWiringTest` 2 条 |
| Seeder | 并发启动幂等 | servicecontext.go:62-70 | AdminSeederBootstrap.java:138-152 | **EQUIVALENT** | 真 PG：容器测试「重复执行 seeder 不产生重复账号」 |
| Seeder | 相对 gRPC server 的时机 | MustNewServer **之前**（admin.go:34 vs :36） | `@PostConstruct`，早于 `SmartLifecycle.start()` | **EQUIVALENT** | 真 PG：容器测试「建表先于 seeder」+ DRIFT-010 已修 |
| Schema | **建表覆盖面** | 3 张（db.go:15） | **4 张**（SchemaInitializer.SCRIPTS） | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-005 |
| Schema | **时间列类型** | `timestamptz` | `timestamp`（无时区） | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-004；真 PG schema 对照 |
| Schema | extra_info 列类型 | `text`（存 JSON 串） | `jsonb` | **OBSERVABLE DIFFERENCE** | DRIFT-ARPC-004；真 PG schema 对照 |
| Schema | 建表并发保护 | 无（AutoMigrate 裸跑） | `pg_advisory_lock` | **IMPLEMENTATION DIFFERENCE**（增强） | SchemaInitializer.java:91,115 |
| Flyway | 是否启用 | 不适用（Go 无 Flyway） | pom 无 flyway 三件套；`application.yml` 无 `spring.flyway.*` | **EQUIVALENT** | `check-flyway-ledger.py` EXIT=0 且不含 admin-rpc |
| 鉴权 | 是否在本组件鉴权 | **不鉴权**，信任入参 uid/operator_id | 同 | **EQUIVALENT** | 全模块无 JWT/session 校验代码 |
| 超时 | 服务端超时 | 2000ms（`zrpc/config.go` struct tag default）→ `DisableNativeRpcTimeout` → 拦截器 | `observability.rpc-timeout: 2s` → `RpcInterceptor` 调度 close | **EQUIVALENT**（服务端侧） | 静态源码；`AdminDeadlinePassthroughTest` 8 条 |
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
| §17 | **Timeout Semantics** | 服务端 2s 一致。⚠️ scope 有一处未定：是否 min 客户端 deadline（UNRESOLVED-ARPC-002）。⚠️ 在途 SQL 取消时长 4455ms vs 378ms（B-1/E-4，跨组件） |
| §18 | **Retry Semantics** | 全仓唯一的重试是 CreateInviteCode 的 3 次唯一冲突重试；触发条件、次数、退避（无）、最终失败（Internal 同一 desc）全部一致 ⇒ **EQUIVALENT** |
| §19 | **Serialization** | proto wire 逐字节一致（`check-proto-static.sh` EXIT=0，13 rpc）；proto3 无 `optional` ⇒ 无 presence 语义差异；`null` vs `""` vs 缺失三态在 13 个 message 上逐条对齐（`AdminServiceGrpcImpl:250-282`）。**唯一差异是 `UpdateUserResp.success`**（DRIFT-ARPC-002）与 `extra_info` 列类型（DRIFT-ARPC-004） |
| §20 | **Configuration** | `AdminAuth.AccessExpire=2592000` ✓；`System.DefaultPassword="123456"` ✓；`AccessSecret` 两侧在 rpc 侧**都未使用**（token 由 admin-api 签发）✓；`DataSource` ✓；`AdminAuth` 的 15 字节 secret 只影响 admin-api（N-5，本组件外）。**唯一分歧是空值容忍**（DRIFT-ARPC-003） |
| §21 | **Lifecycle** | Go：`InitDB(AutoMigrate)` → `ensureDefaultAdmin(logx.Must)` → `MustNewServer` → `AddUnaryInterceptors` → `Start`。RAG：`AdminSchemaBootstrap.@PostConstruct` → `AdminSeederBootstrap.@PostConstruct`（`@DependsOn` 显式）→ gRPC server（`SmartLifecycle.start`）。**次序一致** ✓。差异：建表张数（DRIFT-ARPC-005）+ RAG 多了 `pg_advisory_lock` |
| §26 | **Counterfactual（反事实）** | 6 条新发现里，**DRIFT-ARPC-001（update_time）** 与 **DRIFT-ARPC-002（success）** 命中 §26 的 HIGH-RISK 判据：一个不看 Go 源码、只按 Spring/MyBatis-Plus 惯例写的实现，**必然**会写成 RAG 现在这样（MP 默认不管 autoUpdateTime；proto 成功就填 true）。⇒ **HIGH-RISK DRIFT** |

---

## 7. Semantic Drifts（spec §27 + §36）

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
  ① 它是**已修过的同款漂移**在另一模块的漏修：user-rpc 侧的 UserMapper.java:81-127 已按
     **完全相同的 Go 出处**修过（DRIFT-001，javadoc 直引 `autoUpdateTime` tag），
     修法是 `wrapper.setSql("update_time = CURRENT_TIMESTAMP")`，
     并有回归测试 `UserRpcJsonbAndAvatarProjectionTest:113-148` 钉住 SET 子句必须含该列。
     **admin-rpc 没有同步修** —— 这正是「按组件粒度逐个审计」的漏网形态。
  ② DRIFT-001 当时的修法注释写的是「依赖 Postgres CURRENT_TIMESTAMP 在 row-level 触发」，
     那条注释已被证伪并改成显式 setSql。admin-rpc 若照抄会引入同一个错。

Classification: OBSERVABLE DIFFERENCE（DB 状态外部可观察；业务判定不依赖它，故不上调 MISMATCH）
Evidence:      静态源码 + GORM 框架源码消费点 + 仓库内同款修复的交叉引用
Must Preserve: 每条成功 UPDATE 都把 update_time 推到当次写入时刻
Recommended:   按 UserMapper.java:127 的同款手法，在 6 条写路径的 wrapper 上补
               `setSql("update_time = CURRENT_TIMESTAMP")`，并加一条「SET 子句必须含
               update_time」的守恒断言（照 UserRpcJsonbAndAvatarProjectionTest 的写法）。
```

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
  AdminServiceGrpcImpl.java:219
    return UpdateUserResp.newBuilder().setSuccess(true).setUser(toProtoUser(user)).build();
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

### DRIFT-ARPC-003 —— `system.default-password` 为空时行为分歧

```
Component:   admin-rpc / ResetUserPassword
Category:    Configuration + Error Semantics
Risk:        LOW（需显式把配置项置空才触发）

Go:    resetuserpasswordlogic.go:56  cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)
       cryptx/password.go:8          bcrypt.GenerateFromPassword([]byte(""), DefaultCost)
       ⇒ 空串**合法**，Go 会把「空密码的哈希」写进 users_user，并返回 Success=true

Java:  AdminDomainService.java:976-979
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

### DRIFT-ARPC-006 —— 无 gRPC span：Go 逐 Logic 手写 `tracer.Start`，RAG 侧业务方法无 span 属性

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
     （RpcInterceptor.java:162-165），**不含 audit.* 属性**。
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

### UNRESOLVED-ARPC-002 —— 超时是否 min 客户端 deadline

```
Question:   客户端带一个比 2s 更短的 grpc-timeout 时，两侧是否都按客户端的更早时刻返回？
Go Evidence:    service/common/observability/rpc.go:38
                timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
                ⇒ 父 ctx（已含客户端 deadline）**被继承**，客户端更早 ⇒ 提前取消
Java Evidence:  service/common 的 RpcInterceptor.java:170-172
                deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()
                ⇒ **只算服务端自己的 2s**，没有读 Context.current().getDeadline()
Why Unknown:    语义上确有差异，但「客户端 deadline 是否被 gRPC 库单独处理到 handler 之前」
                需要双侧活体对拍才能定性；本轮无。
Impact:         短 deadline 客户端 + 慢 handler 的组合下，两侧 handler 的实际运行时长可能不同。
Required Confirm: 用 exp-difftest-grpc-errors.sh 对同一用例分别用 100ms / 2000ms / 5000ms
                三档客户端 deadline 打两侧 grpc 端点，比对响应时刻与 handler 副作用。
```

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
`updateStrategy = FieldStrategy.NEVER`（`domain/UserEntity.java:71`），本意是
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

`AdminDomainService.java:167/249/284/325/354/362/400/439/453/459/462/501/524/544/555/589/593/622/626/650/831/860/875`
—— 全部是 `Status.X.withDescription("正常中文")`。

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
| **admin-rpc**（`domain/UserEntity.java:85` 的 `= 0L`） | NULL → 0 | ✅ **与 Go 一致，不是漂移** ⇒ **不改** |
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

2026-10-23 双侧活体对拍（同一份请求体，`extra_info = {"k":" "}`；
`encoding/json` 把 U+0000 转义成 6 个 ASCII 字符 `\u0000` ⇒ **不是**裸 NUL 字节）：

| | 结果 | 落库 |
|---|---|---|
| **Go** admin-api `POST /admincenter/v1/admin/create` | **HTTP 200 `{code:200,…,"uid":"…"}`** | ✅ 行已落库：`extra_info = {"k":" "}`（**text 列照收**） |
| **RAG** admin-api `POST /admin/v1/create` | **HTTP 200 `{code:5001,"msg":"系统内部错误"}`** | ❌ **无行**；PG 报 `ERROR: unsupported Unicode escape sequence`（**22P02**，jsonb 列拒收） |

⇒ **确认是 OBSERVABLE DIFFERENCE**（Go 成功 / RAG 失败）。
且 **错误码分类正确**：22P02 ≠ 23505 ⇒ 没被 `AdminExceptions.isUniqueViolation`
误判成 ALREADY_EXISTS ⇒ 落在 `INTERNAL` → api 层 `5001`（与 Go 的 `Internal→5001` 一致，
见 `admin/_shared.md` §6.2.2 DRIFT-004 的不对称映射表）。

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
| 1 | `admin_admin` / `admin_invite` 的**时间列** `timestamp` → `timestamptz`（DRIFT-ARPC-004 里的 6 处） | 🔴 未修 | DDL 在 `users-common`（`03_create_admin.sql:55-56`、`04_create_admin_invite.sql:37-41`）。⚠️ **不能照抄 `users_user` 那轮的改法**：`users_user` 的时间值**不进入任何 proto/DTO**（`_shared.md` 已核），而 **`admin_invite.expires_at` 是 wire 可见的**（`CreateInviteCodeResp.expires_at` = Unix 秒 ⇒ 两侧算法一致、**当前 wire 不可见差异**），但 `expires_at` 的**比较**（10 分钟有效期）在跨时区多实例下会偏一个时区差 ⇒ 每张表的敏感点不同，必须逐表评估 |
| 2 | `admin_admin.extra_info` jsonb → text | 🔴 未修（见 §4） | DDL 归属 `users-common` |
| 3 | `admin_admin` / `admin_invite` 的**表名**两侧不同（Go `admins`/`admin_invites` vs RAG `admin_admin`/`admin_invite`） | ⚠️ 已确认为**族内约定**（同 `users`→`users_user`），非缺陷；`check-ddl-static.py` 有映射表 | 已在本组件的实体 javadoc 里改正（原先写「GORM 默认表名 admin_admin / admin_invite」是**错的**：Go 的 `TableName()` 返回 `admins` / `admin_invites`，见 `data_model.go:16-18` / `:31-33`） |
| 4 | `DRIFT-ARPC-002`（`UpdateUserResp.success`：Go=false / RAG=true） | 🟡 **待用户裁决**（未签字） | 本轮补了**双侧活体对拍证据** + **决策 tripwire** 测试；HTTP 面不可见（admin-api 硬编码 `success=true`，Go 侧同样硬编码） |
| 5 | `DRIFT-ARPC-003`（`system.default-password` 为空 ⇒ Go 成功 / RAG INTERNAL） | 🟡 未修 | 需显式把配置置空才触发；本轮未加入对拍矩阵（要改配置重起两侧栈，代价大于收益） |
| 6 | `DRIFT-ARPC-005`（启动期建表 3 张 vs 4 张） | 🟡 未修（低风险） | RAG 多建 `users_user_avatar_history`（Go admin-rpc 不建）；全部 `CREATE … IF NOT EXISTS`，不破坏任何东西 |
| 7 | `DRIFT-ARPC-006`（RAG 侧零手写 span，Go 逐 Logic 打 `audit.*` 属性） | 🟡 未修 | 需要动 `service/common` 的 OTel 装配（不在本组件），且与 N-4 的签字理由直接相关 ⇒ 建议与 N-4 一起裁决 |
| 8 | `UNRESOLVED-ARPC-002`（超时是否 min 客户端 deadline） | 🟡 仍未定 | 本轮的 gRPC 对拍**没有**加客户端 deadline 档（`exp-difftest-grpc-admin-errors.sh` 用默认 deadline）⇒ 结论未变 |
| 9 | `UNRESOLVED-ARPC-003`（Redis 客户端超时配置） | 🟡 仍未定 | 本轮未做 Redis 故障注入 |
| 10 | `UNRESOLVED-ARPC-005`（`users_user` 建表责任交叉） | 🟡 跨组件 | 需要 user-rpc 侧结论后合并判读 |

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

## 7. 本轮新增/修改的文件

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
「与参考侧 `UpdateUserLogic.java:129` 1:1」—— **引错了文件与行号**（§3.3.2 形态）。
正确出处是 `service/user/admin/api/internal/logic/admin/updateuserlogic.go:68`（**api 层**，
不是 rpc 层的 `updateuserlogic.go:105-113`）。已改。

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
| 2 | 🟡 **DRIFT-ARPC-006**（RAG 侧零手写 span，Go 逐 Logic 打 `audit.*` 属性） | 未修 | 需动 `service/common` 的 OTel 装配，不在本域。⚠️ 但 **SD-2026-10-23-01/06 签字后，`audit.operator_id` 的缺失对审计的影响更大** ⇒ 建议与 N-4 一起在 users 域层面裁决 |
| 3 | 🟡 **UNRESOLVED-ARPC-002**（超时是否 min 客户端 deadline） | 仍未定 | 本轮 gRPC 对拍未加客户端 deadline 档，结论未变 |
| 4 | 🟡 **UNRESOLVED-ARPC-003**（Redis 客户端超时配置） | 仍未定 | 本轮未做 Redis 故障注入 |
| 5 | 🟡 **UNRESOLVED-ARPC-005**（`users_user` 建表责任交叉） | 跨组件 | 需 user-rpc 侧结论后合并判读。⚠️ 本轮已改 `admin_admin`/`admin_invite` 的 DDL，**共享账本仍只有一套 users 域脚本**，机制不变 |
| 6 | 🟡 **DDL 的 `SET TIME ZONE` 副作用** | 已记录未验证 | 本轮新增的 `SET/RESET TIME ZONE` 与 `01`/`02` 同款；`SchemaInitializer` 四份脚本共用**一条连接**依次执行 ⇒ `03`/`04` 的 `SET` 只影响其自身语句区间，随后 `RESET` 还原。**未做「多实例并发建表 + 非 UTC 会话时区」的运行时验证** |
| 7 | ⚠️ **`users_user` 时间列的 3 张表** | 跨域 | `01`/`02` 已由 users/user 域于 2026-10-23 改为 TIMESTAMPTZ；本 worker **未复核**其正确性，只确认了 DDL 文本已是 `TIMESTAMPTZ` |

## 6. 本轮新增/修改的文件

| 文件 | 性质 |
|---|---|
| `service/users/users-common/src/main/resources/db/03_create_admin.sql` | `extra_info`→TEXT、2 个时间列→TIMESTAMPTZ、幂等自愈 ALTER |
| `service/users/users-common/src/main/resources/db/04_create_admin_invite.sql` | 4 个时间列→TIMESTAMPTZ、幂等自愈 ALTER |
| `service/users/admin/admin-rpc/.../mapper/JsonTextTypeHandler.java` | **新增**（text 列专用；**不复用** `JsonbTypeHandler`） |
| `service/users/admin/admin-rpc/.../domain/AdminEntity.java` | `extraInfo` 换 handler、时间列→`OffsetDateTime`、javadoc 按 §3.2/§3.3.2 重写 |
| `service/users/admin/admin-rpc/.../domain/AdminInviteEntity.java` | 4 个时间列→`OffsetDateTime`、javadoc 补可观察性论证 |
| `service/users/admin/admin-rpc/.../service/AdminDomainService.java` | `updateSelf` 去 CAST、`createInviteCode` 改 `OffsetDateTime`、空密码守卫加签字注释 |
| `service/users/admin/admin-rpc/.../service/AdminCreateAtomicService.java` | `now` 改 `OffsetDateTime` |
| `service/users/admin/admin-rpc/.../service/AdminServiceGrpcImpl.java` | 删 `setSuccess(true)`（DRIFT-ARPC-002） |
| `service/users/admin/admin-api/.../service/AdminUpdateUserService.java` | 订正 Go 出处（引错的文件/行号） |
| `service/users/admin/admin-rpc/src/test/.../AdminRpcColumnTypeParityTest.java` | **新增**，真 PG 3 条（列类型 / `\u0000` 边界 / 跨时区 invite 判定） |
| `service/users/admin/admin-rpc/src/test/.../AdminRpcGoWireParityTest.java` | tripwire 反转：断言 `success == false` |
| `service/users/admin/admin-rpc/src/test/.../AdminUpdateSelfAndCreateAdminRegressionTest.java` | 跟随实体类型改 `OffsetDateTime` |
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
| DRIFT-ARPC-008「admin-rpc 的 `status = 0L` 与 Go 等价，不要删」 | 看 `domain/UserEntity.java:109` | ✅ `= 0L` 在，javadoc 的三段 Go 出处完整 |
| DRIFT-ADMINAPI-011「400 的 `Content-Type` 已带 charset」 | `GlobalExceptionHandler.java:192` | ✅ `headers.set(CONTENT_TYPE, "text/plain; charset=utf-8")` |
| A-1「`user-api` 同款 charset 缺失仍在」 | `user-api/.../GlobalExceptionHandler.java:216` | ✅ 仍是 `setContentType(MediaType.TEXT_PLAIN)`，**A-1 仍有效** |
| 「`AdminEntity` 用 text handler、`UserEntity` 用 jsonb handler」 | 两个 `@TableField` 实读 | ✅ 拆分正确，**没误改 users_user 那张表** |
| 「`users_user.extra_info` 仍是 JSONB（跨域漂移仍在）」 | `01_create_user.sql:41` | ✅ 仍 `JSONB`，§5 第 1 项**仍然成立** |
| N-1 / SD-08「黑名单 401 业务码是 1006」 | `AdminJwtAuthInterceptor.java:124` 注释 + 对拍实测 | ✅ |

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
| 2 | ARPC-006 零手写 span | 需 `service/common` 的 OTel 装配，不在本域 |
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

### 1.2 逐层证据链

| # | 层 | 事实 |
|---|---|---|
| 1 | Go `service/common/observability/rpc.go:38` | `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` —— **`ctx` 是 handler ctx**，gRPC 会把客户端的 `grpc-timeout` 传播进来 |
| 2 | Go 语言语义 | `context.WithTimeout(parent, d)` 在 parent deadline 已更早时**等价于 parent** ⇒ 有效 deadline = **min(parent, now+d)** = **min(客户端, 服务端)** |
| 3 | RAG `service/common/observability/RpcInterceptor.java:170-172` | `deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()` —— **从不读入站 `Context` 上的 deadline** ⇒ 有效 deadline = **只有服务端配置** |
| 4 | RAG `RpcInterceptor.java:184-198` | `TIMEOUT_SCHEDULER.schedule(…, timeoutMs, …)` —— 到点才 `close(DEADLINE_EXCEEDED)`，同样**不受客户端 deadline 影响** |
| 5 | 配置值 | 两侧**配置相同**：Go 未配 `Timeout` ⇒ go-zero v1.10.2 `zrpc/config.go` 的 `Timeout int64 json:",default=2000"` = **2000ms**；RAG `application.yml:129` `rpc-timeout: 2s` = **2000ms** |

⇒ **差异不在配置值，在「是否继承」。** 这是本条最容易被误判成「两边都是 2s 所以一样」的地方。

### 1.3 可观察后果

客户端发 `grpc-timeout: 100ms`、服务端配 2000ms，handler 需要 500ms：

| | Go | RAG |
|---|---|---|
| **客户端看到** | `DEADLINE_EXCEEDED` @~100ms | `DEADLINE_EXCEEDED` @~100ms（**gRPC 库替客户端兜的，与本条无关**） |
| **服务端 handler** | ctx 在 100ms 即 Done ⇒ **任何 ctx-aware 操作（SQL / 下游 RPC）中止**，在途查询被取消 | **继续跑到 2000ms** |
| **副作用** | 100ms 之后的写**不发生** | 100ms 之后的写**照样落库** —— 客户端看到失败，数据却变了 |

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
最后**查 PG 侧那次查询是否被取消**（Go 应出现 cancel/短滞留；RAG 应跑满）。
⚠️ 造阻塞的脚本必须先证明「锁真的加上了」（`to_regclass()` 校验）——
`tools/difftest/README.md` 与 `Intentional_Deviations/_cross/signed-deviations.md` 都记了这个坑。

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

`core/stores/redis/redis.go:27` 写着：

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

新增 `service/AdminSpans.java`：`GlobalOpenTelemetry.getTracer("admin-rpc")` + `Scope` + `finally span.end()`。
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

⇒ 「span 真的被导出」与「属性真的被带上」**双���都有运行期证据**，
不是「代码里写了」的推测。
