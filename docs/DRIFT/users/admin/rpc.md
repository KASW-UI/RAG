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
