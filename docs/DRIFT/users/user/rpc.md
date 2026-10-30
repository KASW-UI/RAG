# DRIFT 台账 · `service/users/user/user-rpc`

> 🟥 **当前态指针（2026-10-29 · R4·T-4.2（G-12）回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部 §0 表的「核对日期：2026-10-23」已过期；而其「最终判定」行是**当前**口径 ⇒ **0 项 SEMANTIC MISMATCH + 2 项 OBSERVABLE DIFFERENCE**（`V-002` / `V-012`））⇒ **当前状态一律以「0. 元信息与证据强度声明」表内的「最终判定」行为准** ⇒ ⚠️ 「10. 最终判定（spec §39 · 18 项 checklist）」的判定框仍是**首轮口径**（写着「1 项 SEMANTIC MISMATCH（V-001）与 7 项 OBSERVABLE DIFFERENCE」）⇒ **不要**读作当前结论；最新一轮见「2026-10-28 第二十二轮：`DRIFT-USERRPC-V-015`（新立 · ✅ 已修 · 域内自闭环）—— Go 的 `span.RecordError`【不】置 span 状态 ⇒ RAG 多置 ERROR（**遥测面**漂移）」，末尾最新一条见「🔴 `DRIFT-USERRPC-V-016` · **「OS 盲选口」族** · 本组件 **5 处**（2026-10-28 · G1 lane）」。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

> **本文件原有内容 = 导航页**（下方 L1–L28 逐字保留，一字未改）；**新增内容 = 组件级语义对照台账**（自
> 「组件级语义对照台账（本轮）」一节起，按验证方法论 spec §31-b 组织）。
> 两部分并存：**历史导航里指向的叙述仍在 [`../_shared.md`](../_shared.md) 与 [`./api.md`](./api.md)**，
> 本轮的新证据不替换它们，只在其之上叠加。

> **2026-10-23 复核：原结论修正为 —— 本组件不是「只有导航、没有本体」。**
> 本轮已按 spec 建立完整组件模型（§1–§10）。原导航页 L34–41 写的
> 「本模块自身**从未**单独建立过的记录（缺口）」三条，逐条判定见 §4.5：
> 第一条已闭合、第三条**原结论错误**（`service/users/user/user-rpc/HUMAN_ONLY.MD` **确实存在**，
> 本轮 `glob **/HUMAN_ONLY.MD` 实测 4 份，与 AGENTS.md §2 一致）、第二条已升级复跑。

> **本文件是导航页，不是内容页。**
> AGENTS.md 里与 `user-rpc` 相关的漂移/修复记录**没有独立成节**——它们散布在几段
> 以 `service/users` / `tools/difftest` 为标题的叙述里，那几段同时覆盖 4 个子模块 + `users-common`。
> 为了**不割裂叙事、不改一个字**，这些段落整体搬到了 [`../_shared.md`](../_shared.md)。
> 本页给出精确行号索引。

## 搬移到 `users/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `user-rpc` 的关系 |
|---|---|---|
| **L479 起** | AGENTS.md §6.2.5 | **Flyway 迁移并发** —— 把 Go 的 `user-rpc` 与 `admin-rpc` **同时**对着全新空库启动的实测，暴露 DDL 竞态；`SchemaInitializer.applyAll` 取 PG 会话级 advisory lock。**这是 `user-rpc` 至今最重的一条运行时缺陷记录。** |
| **L561 起** | AGENTS.md §6.2.6 | 证据边界台账 E-1~E-6。其中 **E-2 / E-4 / E-5** 的 `user-rpc` 项、`E-5` 的 admin-rpc 并发与回滚证据。 |

## 也需一并读的相邻台账

- [`../../message/rpc.md`](../../message/rpc.md) —— **§6.2.4② `NotificationEntity.isRead` 运行期致命缺陷**原挂在 `service/users` 的章节下，
  但 `trimPreview` / `markNotificationRead` 实为 **message-rpc**。搬移时已按真归属改判。
- [`../../common/deadline.md`](../../common/deadline.md) —— deadline 透传的接入点含 `LikeRpcClient` / `MessageRpcClient`，**不含** user-rpc。

## 本模块自身**从未**单独建立过的记录（缺口）

- `service/users/user/user/rpc` ↔ Go `service/user/user/rpc` 的**逐字段 proto 对照**：无独立留档，
  证据散落在 `users/_shared.md` 的 §6.2.5 与 §6.2.6。
- ⚠️ `AvatarTransactionWiringTest`（5 条，含 2 条真触发 rollback）证明 `@Transactional` 真生效，
  但 §6.2.6 自己声明 **「无真实 PG，故不证明 jsonb `CAST` 等 PG 行为」**——该缺口后由
  `UserRpcContainerWiringTest`（真 Testcontainers PG，**16 条**，2026-10-26 实测）关闭，详见 `users/_shared.md`。
- 本模块的 `HUMAN_ONLY.MD` **不存在**（全仓 4 个 HUMAN_ONLY.MD 全在 users 域，但不含 user-rpc 之外的域）。
---

# 组件级语义对照台账（本轮）

> **2026-10-23 复核：原结论修正为 —— 上面 L34–41 的三条缺口，第一条与第三条已被本轮推翻，
> 第二条由本轮升级为「已闭合且可复跑」。逐条判定见 §4.5 与 §10。**

## 0. 元信息与证据强度声明

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23** |
| 组件 | `service/users/user/user-rpc`（AGENTS.md §3.5 的 23 个组件之一） |
| Go 侧 | `Sea-RideTheWind_golang/service/user/user/rpc` —— 通读 **18 个源文件**（排除 `pb/*.pb.go`）。⚠️ **不含** `service/user/rpc/internal/logic/` 的 **7 个孤儿 `_test.go`**（全仓零引用，既不属本组件也不属 admin-rpc） |
| RAG 侧 | `RAG/service/users/user/user-rpc` —— 通读 **16 个 main 源文件** + `pom.xml` + `application.yaml` + `AutoConfiguration.imports` + `users-common` 的 4 份 DDL 与 `SchemaInitializer` |
| 外部依赖（**不属于本组件，但本组件消费**） | ① `service/user/common/{cryptx,jwt,errmsg}`（user 与 admin 两域共用）② `service/common/{snowflake,observability,logger}` ③ `service/common/*` ↔ RAG `com.rag.common.*` |
| 编号命名空间 | 本轮新增 **`DRIFT-USERRPC-V-001` 起** / **`UNRESOLVED-USERRPC-001` 起**。users 域已占用的 `DRIFT-001..011/016/018/024`、`DRIFT-NEW-001/005/007/008/011/012/013/014/015/016/018`、`DRIFT-FRAMEWORK-001` **一条都不复用**。⚠️ **2026-10-26 第十一轮新增 `DRIFT-USERRPC-V-012`**（编号续在 V-011 之后）|
| 本轮自跑证据 | ✅ **真 PG**：`mvn -o -pl service/users/user/user-rpc -am test`，**2026-10-26 实测 `Tests run: 128, Failures: 0, Errors: 0, Skipped: 0`**（较 2026-10-24 基线 125 净增 +3 = 第十二轮新增的 3 条 B-1 装配层守网）。`UserRpcContainerWiringTest` **16 条**跑在 Testcontainers `postgres:16-alpine` 上（日志实证 `DockerClientFactory` + `Found Docker environment`），`AvatarTransactionWiringTest` 5 条跑在真实 Spring 容器装配上 |
| ✅ **已**取得（2026-10-24 更新） | ✅ **双侧活体对拍** —— `tools/difftest/exp-difftest-grpc-user9.sh`：**43 场景**（9 个 RPC × 字段级响应 / `grpc-message` 逐字 / 边界输入），**真进程 + 真 PG + 真 Redis**，自带容器/库/端口 ⇒ **39 等价 / 4 处差异，4 处全是 N-2 已签字的 Go 源码乱码** ⇒ **无新漂移**。⇒ **§9.1**。<br>⚠️ 此前那句「`grpc-dual-port-runner.java` 只覆盖 4 档 status code」的**缺口描述已过期**（那是 2026-10-23 的状态） |
| 最终判定 | **`SEMANTICALLY NOT EQUIVALENT`** —— **2 项 OBSERVABLE DIFFERENCE**（**V-002**，§7 · **V-012**，§「2026-10-26 第十一轮」—— 后者是**观测面**：超时时的业务日志错误归因）—— ⚠️ 原写「2 项（含 V-007）」，**V-007 已于 2026-10-26 由 `global` 域销账**（§7）+ 4 项 IMPLEMENTATION DIFFERENCE，**0 项 SEMANTIC MISMATCH**、**0 项 unresolved**（§9 的 7 条已于 2026-10-24 全部闭环）。⚠️ **此前写「1 项 SEMANTIC MISMATCH + 7 项 OBSERVABLE DIFFERENCE + 5 项 unresolved」是过期状态** —— V-001 已证伪并闭环（Go 的 GORM 把 `status IS NULL` 读成 0 ⇒ **两侧行为一致**） |

> ⚠️ **按 AGENTS.md §3.3 与本 spec 硬约束：91 条全绿不构成 `EQUIVALENT` 的证据。**
> 本文件里每条 `EQUIVALENT` 的真实支撑是**逐行读 Go 消费点**，测试只是「RAG 侧写法确实如此」的旁证。

---

## 1. Component Contract（spec §4 + §5）

```
Component:  user-rpc（Go）↔ user-rpc（Spring Boot）
Purpose:    用户域的唯一 gRPC 服务端。9 个 RPC，是 user-api / admin-api 的下游。
            它自己不做鉴权、不签发 JWT、不解析 token 字符串 —— 它只接受 int64 uid。
Public Ops: 9 个，与 proto `service UserService` 逐条一一对应
            GetUser / Register / Login / Logout / UpdateUser /
            DeleteUser / UploadAvatar / GetAvatarHistory / SelectAvatar
Inputs:     proto message（wire 契约见 §4.1）
Outputs:    proto message **或** gRPC status（非 0 时响应消息被丢弃 —— 两侧同，见 §6.5）
Dependencies:
            PG(postgres)   —— 全部 9 个 OP 都依赖
            Redis          —— 仅 Logout（Setex）
            Snowflake      —— Register(uid) / UploadAvatar(history_id)
            bcrypt         —— Register / UpdateUser / Login
Side Effects:
            写 users / user_avatar_history 两张表（Go）/ users_user / users_user_avatar_history（RAG）
            写 Redis key `user:jwt_blacklist:<token>`（值恒为字面量 "banned"）
            Prometheus 指标 8 个
State:      无进程内状态（Go 的 sf/snowflake 是包级单例，RAG 是静态字段，见 §6.11）
Success 意味着: 对应行已落库 / Redis key 已写入 / LoginResp.status=0
Failure 意味着: 对应 gRPC status.code（非 0），响应消息丢弃
```

> **组件边界上的一条关键事实**：Go 的 `GetUser` / `DeleteUser` 在失败时返回的是
> **「非 nil resp + 非 nil err」**（`getuserlogic.go:37-39`、`deleteuserlogic.go:44-46`）。
> gRPC 在 `err != nil` 时**丢弃响应消息**，所以这个「半成品 resp」**永远到不了客户端**。
> RAG 侧直接抛 `StatusRuntimeException`，客户端可见行为**完全一致**（§6.5 判为 `EQUIVALENT`）。
> ⇒ **「resp 里那个 `found:false` / `success:false`」是死数据，不是行为。**
> 这一条同时否掉了 AGENTS.md §1.1 列的「`success` 字段语义反转」在本组件的可见性。

---

## 2. GO-SEMANTIC-MODEL（spec §7，逐 OP，含 §6 的 18 字段）

下表每一行的 Go 事实都带 `文件:行号`，且行号指向**消费点**。

### OP-01 `Login`
| 字段 | Go 行为 |
|---|---|
| Input | `LoginReq{username, password}` |
| Precondition | 无（不做鉴权） |
| Processing | 1. `FindOneByUserName`（`model/user.go:20-30`，`First()` 无 ORDER BY）→ 2. `cryptx.CheckPassword`（`cryptx/password.go:15-18`，`CompareHashAndPassword` 任何 err 都返 false）→ 3. `user.Status == 1` 判封禁（`loginlogic.go:67`） |
| External Calls | PG ×1 |
| Branch | `ErrorNotFound`→`status=1, err=nil`（`:46-52`）；其它 err→`db_error` + `codes.Internal "DB查询失败"`（`:53-56`）；密码错→`status=1, err=nil`（`:60-66`）；`status==1`→`status=2, err=nil`（`:67-72`）；否则 `status=0 + uid` |
| Result | **`LoginResp.status` 是三态业务码，不是 gRPC code**；三种失败**全部 err=nil** |
| State Change | 无 |
| Metrics | `login_total{user_not_found}`（`:47`）/ `{internal_error}`（`:61`，**密码错也走这一档**）/ `{db_error}`（`:53`）/ `{success}`（`:73`）。**封禁分支无指标** |
| Trace | span `Action-User-Login` + `biz.username`（`:38-41`） |
| Timeout/Retry/Concurrency | 无显式检查；靠 `ctx` 取消（见 OP-10） |
| **关键语义** | 用户不存在与密码错**共用 `status=1`** ⇒ 防用户名枚举 |

### OP-02 `Register`
| 字段 | Go 行为 |
|---|---|
| Processing | 1. `FindOneByUserName` 查重（`registerlogic.go:44`）→ 2. `PasswordEncrypt`（`cryptx/password.go:7-13`，`bcrypt.DefaultCost`=10）→ 3. `snowflake.GetID()`（`snowflake.go:41-50`，**懒初始化** `Init()`）→ 4. `Insert`（`model/user.go:49-52`） |
| Branch | 查到→`fail_exists` + `codes.AlreadyExists "用户名已存在"`；非 `ErrorNotFound` 的 err→`db_error` + `codes.Internal "DB查询失败"`（`:50-55`，**独立成一支**）；bcrypt 失败→`internal_error` + `"密码生成失败"`；snowflake 失败→`internal_error` + `"uid生成失败"`；insert 失败→`db_error` + `"DB更新失败"` |
| State Change | INSERT `users`；`Status=0`、`Score=0`、`ExtraInfo=in.ExtraInfo`（nil 时 GORM serializer 写 NULL） |
| 并发 | **无显式锁**，靠 `username`/`email` UNIQUE 兜底；race 撞唯一约束时 `Insert` 返 err → `codes.Internal "DB更新失败"` |

### OP-03 `GetUser`
| 字段 | Go 行为 |
|---|---|
| Branch | `ErrorNotFound` → **返回 `&GetUserResp{Found:false}` + `codes.NotFound "用户不存在"`**（响应被 gRPC 丢弃）；其它 err → `Found:false` + `codes.Internal "DB查询失败"` |
| Result | `UserInfo{Uid, Username, Email, ExtraInfo, Status:&user.Status}`。**`Score` 字段从不填**（proto 有 `uint32 score = 2`，两端都不填） |
| Trace | 无 span、无 metrics（Go 侧本来就没有） |

### OP-04 `UpdateUser`
| 字段 | Go 行为 |
|---|---|
| Processing | 条件式组装 `toUpdate`：`len(Username)>0` → 先查重（**不排除自己**）→ 已存在则 `codes.AlreadyExists`；`len(Password)>0` → bcrypt；`len(Email)>0` → 覆盖；**`in.ExtraInfo != nil`** → 覆盖（`:58-60`） |
| 写库 | `UpdateUserById`（`model/user.go:44-47`）= GORM `Updates(struct)` ⇒ **自动跳过零值**（`""`/`0`/`nil` map/`false`），只写非零列；**`autoUpdateTime` 会在真发出 UPDATE 时推进 `update_time`**；全零值 struct ⇒ 不发 UPDATE |
| Branch | update 失败→`codes.Internal "DB更新失败"`；随后 `FindOneByUid` 重读：`ErrorNotFound` → `codes.NotFound "用户不存在"`，其它 → `"DB查询失败"` |
| Result | `UserInfo{Uid, Username, Email, ExtraInfo}` —— **不含 `Status`** |
| 并发 | 无事务、无锁；「重读」与「写」之间存在窗口 |

### OP-05 `DeleteUser`
| 字段 | Go 行为 |
|---|---|
| Processing | `FindOneByUid` 存在性检查 → `DeleteUserByUid`（`model/user.go:54-57`，`DELETE ... WHERE uid = ?`） |
| 副作用 | **不级联删 `user_avatar_history`**；两侧 DDL 都**没有 FK**，⇒ 孤儿行两边都留 |
| Trace | span `Action-User-Delete` + `biz-uid`（`deleteuserlogic.go:34-38`） |

### OP-06 `SelectAvatar`
| 字段 | Go 行为 |
|---|---|
| 前置 | `strings.TrimSpace(HistoryId)`，空则 `codes.InvalidArgument "history_id is required"`（`selectavatarlogic.go:33-36`） |
| 事务 | `SelectAvatarHistory`（`model/user.go:134-169`）5 步：`First(user)` → `First(history, uid+history_id)` → `UPDATE …is_current=false WHERE uid AND is_current` → `UPDATE …is_current=true WHERE uid AND history_id` → `UPDATE users SET extra_info` |
| **无行锁** | 事务闭包里**没有** `FOR UPDATE`，只有 `First()`（MVCC 快照读）⇒ 并发下可出现两条 `is_current=true` |
| 内存回写 | `history.IsCurrent = true`（`:174`）**在事务提交后、返回前**显式赋值 ⇒ 响应恒为 true |
| Branch | `ErrorNotFound`→`codes.NotFound`；`ErrorAvatarHistoryNotFound`→`codes.NotFound`；default→`codes.Internal "DB更新失败"` |
| ⚠️ message | 这 3 条 + `uploadavatarlogic.go` 3 条 + `getavatarhistorylogic.go` 3 条 = **9 条 message 在 Go 源码里就是 GBK 双重编码乱码**（本轮实测 hex 确认，见 §7 V-007 的姊妹项 N-2） |

### OP-07 `UploadAvatar`
| 字段 | Go 行为 |
|---|---|
| 前置 | `TrimSpace(AvatarUrl)` 空则 `InvalidArgument`；`snowflake.GetID()` → `history_id`（**服务端生成，不接受请求传入**） |
| ContentType | `model.NormalizeAvatarContentType`（`model/user.go:179-184`）：`TrimSpace` 后空 ⇒ `"image/png"` |
| 事务 | `CreateAvatarHistory`（`model/user.go:72-105`）4 步：`First(user)` → 清 current → `Create(history, IsCurrent:true)` → 写 `users.extra_info["avatar"]` |

### OP-08 `GetAvatarHistory`
| 字段 | Go 行为 |
|---|---|
| 前置 | 先 `FindOneByUid` 存在性检查（`getavatarhistorylogic.go:32`） |
| 列表 | `ListAvatarHistory`（`model/user.go:114-126`）：`limit <= 0 \|\| limit > 30 ⇒ limit = 30`（**静默 clamp，不报错**）；`Order("create_time DESC")` |
| Result | `AvatarHistoryItem{Id: HistoryID, ..., CreateTime: RFC3339 秒精度}`；`CreateTime` 为零值时 `create_time = ""`（`avatarhelpers.go:16-19`） |

### OP-09 `Logout`
| 字段 | Go 行为 |
|---|---|
| 前置 | `in.Token == ""` → `codes.InvalidArgument "Token不能为空"` |
| 写 Redis | `SETEX "user:jwt_blacklist:<整串token>" "banned" <UserAuth.AccessExpire=2592000>`（`logoutlogic.go:35-37`） |
| Result | `&pb.LogoutResp{}` ⇒ **`success` 字段恒为 `false`**（Go 自身形态，RAG 必须跟随） |
| ⚠️ | **不做任何 token 有效性检查** —— 已过期 / 伪造 / 空格的 token 一律写入黑名单 |

### OP-10（横切）超时 / deadline
`user.go:32` `observability.DisableNativeRpcTimeout(&c.RpcServerConf)` 读走 go-zero 的
`Timeout` 并置 0；`user.go:43` `s.AddUnaryInterceptors(NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))`。
`etc/user.yaml` **未配置 `Timeout`** ⇒ 实际值来自 struct tag：
`go-zero@v1.10.2/zrpc/config.go:46` `Timeout int64 \`json:",default=2000"\`` ⇒ **2000ms**。
拦截器（`service/common/observability/rpc.go:38-72`）用 `context.WithTimeout` + goroutine + select；
超时时 `rpc.go:69` 返回 `codes.DeadlineExceeded`，description = **`context deadline exceeded`**。

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（spec §7 / §34，结构与 §2 完全同构）

| OP | RAG 实现位置 | 与 §2 的关键对应 |
|---|---|---|
| Login | `UserDomainService.java:141-207` | 三态 + `PasswordUtil.checkDetailed`；`catch (StatusRuntimeException)` 放行分支保证 DEADLINE_EXCEEDED 不被吞 |
| Register | `:221-314` | 5 个 catch 分支与 Go 的 5 个 return 分支一一对应 |
| GetUser | `:333-382` | 额外加了 `status == null ⇒ INTERNAL "DB查询失败"` 的守卫（`:359-363`），对应 Go「NULL 扫进 int64 会报错」 |
| UpdateUser | `:401-499` | 条件式组装 `toUpdate` 逐条对应；`getExtraInfoCount() > 0`（Go 是 `in.ExtraInfo != nil`，**这里有一处差异**，见 §7 V-005） |
| DeleteUser | `:514-559` | 不级联；span 用 `Context.current().with(span)` |
| SelectAvatar | `:573-610` | 调 `avatarHistoryMapper.selectAvatarHistory(...)`，**经 mapper 代理**⇒ 事务生效 |
| UploadAvatar | `:623-673` | `String.valueOf(Snowflake.nextId())` 作 `history_id` |
| GetAvatarHistory | `:686-723` | clamp 在 `UserAvatarHistoryMapper.listAvatarHistory:43-49` 内 |
| Logout | `:737-757` | `bizRedis.opsForValue().set(key,"banned",Duration.ofSeconds(ttl))` |
| 超时 | `UserRpcApplication.java:103-107` `@Bean GrpcServerConfigurer` → `server.intercept(...)`；`application.yaml:155-156` `observability.rpc-timeout: 2s` | **`@Import(RpcServerInterceptorFactory.class)` 已在 `:52`** ⇒ §6.5 的 H2（拦截器 Bean 存在但没挂上）在本组件**不成立**，`GrpcInterceptorWiringTest` 用 `verify(builder).intercept(same(...))` 锁死 |

---

## 4. Equivalence Matrix（spec §24 + §35）

Result 只用 5 个枚举值。证据列的记号：
`[GO]` 读 Go 消费点 · `[RAG]` 读 RAG 源码 · `[PG]` 本轮真 PG 实测 · `[T]` 仅单侧测试旁证（**不构成等价证据**）

### 4.1 契约层

| Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|
| 全组件 | proto 契约（message 名 / 字段名 / 字段号 / 类型 / rpc 签名） | `proto/user.proto` | `user-proto/src/main/proto/users.proto` | **EQUIVALENT** | 剥离 `option` / 注释 / 空行后逐行 `diff` ⇒ **零差异**（本轮实测）。`UserInfo.status` 的 `optional` 两侧同 |
| 全组件 | service 方法集与顺序 | 9 个（`userserviceserver.go:25-67`） | 9 个（`UserServiceGrpcImpl`） | **EQUIVALENT** | `[GO][RAG]` |
| 全组件 | 表名 | `users` / `user_avatar_history` | `users_user` / `users_user_avatar_history` | **IMPLEMENTATION DIFFERENCE** | 已在 AGENTS.md §3.4 作为签字偏离登记；两侧表名不同 ⇒ 本组件的 SQL 无法跨端复用（但两侧各连各的库，本就不可复用） |

### 4.2 逐 OP

| Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|
| Login | 用户不存在分支 | `status=1, err=nil` | 同 | **EQUIVALENT** | `[GO] loginlogic.go:46-52` `[RAG] :153-157` `[T]` |
| Login | 密码错分支（与不存在**共用** status=1） | `status=1, err=nil` | 同 | **EQUIVALENT** | `[GO] :60-66` `[RAG] :173-187` |
| Login | 封禁分支 | `status=2`，**无 metric** | 同，**无 metric** | **EQUIVALENT** | `[GO] :67-72` `[RAG] :190-195` |
| Login | bcrypt hash 畸形 | `CompareHashAndPassword` 返 err ⇒ `false` ⇒ 密码错 | `checkDetailed` 返 `MALFORMED_HASH` ⇒ 仍 `status=1` | **EQUIVALENT**（对外）；RAG 多打 span/log | `[GO] cryptx/password.go:15-18`；DRIFT-004 已签字 |
| Login | **`status == null`** | NULL 扫进 `int64` 报错 ⇒ `db_error` + `INTERNAL "DB查询失败"` | `!= null && == 1L` ⇒ **跳过封禁检查，落成功分支** | **SEMANTIC MISMATCH** | **V-001** |
| Register | 查重分支 | `AlreadyExists` | 同 | **EQUIVALENT** | `[GO] registerlogic.go:44-49` `[RAG] :231-235` |
| Register | 「非 NotFound 的 err」独立成一支 | `:50-55` `db_error` + `ErrorDbSelect` | `:241-249` 同 | **EQUIVALENT** | `[GO][RAG]` |
| Register | bcrypt / snowflake / insert 三个失败分支 | 三个不同 message | 三个不同 message，逐一对应 | **EQUIVALENT** | `[GO] :56-87` `[RAG] :251-305` |
| Register | `extra_info` 缺席 | `nil` ⇒ GORM 写 NULL | 不进 patch ⇒ 列取 DEFAULT(NULL) | **EQUIVALENT** | `[GO] model/user.go:49-52` `[RAG] :289-293` |
| Register | 主键 `id` 取值来源 | GORM `bigserial` 序列 | MP `IdType.ASSIGN_ID` 客户端雪花 | **OBSERVABLE DIFFERENCE** | **V-006**（仅 DB 层，wire 不可见） |
| GetUser | NotFound | `resp{Found:false}` + `NOT_FOUND`（resp 被丢弃） | `NOT_FOUND` 抛异常 | **EQUIVALENT** | `[GO] getuserlogic.go:35-40` `[RAG] :339-341` |
| GetUser | `status == null` | 扫 NULL 报错 ⇒ `INTERNAL "DB查询失败"` | 显式守卫 ⇒ 同一条路径 | **EQUIVALENT** | `[GO]` GORM scan 语义 + `[RAG] :359-363`（javadoc 已写明推导） |
| GetUser | `Score` 字段 | 从不填（0） | 从不填（0） | **EQUIVALENT** | `[GO] :48-54` `[RAG] :365-369` |
| GetUser | `extra_info` 为 NULL 的 map | proto map 空 | proto map 空 | **EQUIVALENT** | `[GO] :52` `[RAG] :370-375` |
| UpdateUser | username 自查重**不排除自己** | 是 | 是 | **EQUIVALENT** | `[GO] updateuserlogic.go:36-46` `[RAG] :406-429` |
| UpdateUser | 空 email 不清列 | `len(in.Email) > 0` 才写 | `!isEmpty()` 才写 | **EQUIVALENT**（AGENTS §1.1 第 6 条在本组件**不存在**） | `[GO] :55-57` `[RAG] :446-448` + `[PG]` |
| UpdateUser | **extra_info jsonb 写入** | GORM serializer | `setSql("extra_info = CAST({0} AS jsonb)")`（**不是** `wrapper.set`） | **EQUIVALENT** | `[RAG] UserMapper.java:122`；`[PG]` `UserRpcContainerWiringTest#updateUserByIdCastsToJsonbOnRealPg` 本轮通过。**AGENTS §1.1 第 4 条已修** |
| UpdateUser | 全零值 patch | GORM 不发 UPDATE | `any==false ⇒ return 0`，不发 | **EQUIVALENT** | `[GO] model/user.go:45` `[RAG] UserMapper.java:125`；`[PG]` `zeroValuePatchDoesNotUpdate` |
| UpdateUser | `update_time` 推进 | GORM `autoUpdateTime` | `setSql("update_time = CURRENT_TIMESTAMP")`（放在 `any` 之后） | **EQUIVALENT** | `[GO] data_model.go:15` `[RAG] :126-127`；`[PG]` 同上用例断言 `update_time` 不变 |
| UpdateUser | **显式空 map** | `in.ExtraInfo != nil` ⇒ 写 `{}` | `getExtraInfoCount() > 0` ⇒ **不写** | **OBSERVABLE DIFFERENCE** | **V-005** |
| UpdateUser | 响应 `UserInfo` 不含 `Status` | 不含 | 不含 | **EQUIVALENT** | `[GO] :80-87` `[RAG] :496` |
| UpdateUser | 并发窗口（写与重读之间无事务） | 有 | 有 | **EQUIVALENT**（共有窗口，非漂移） | `[GO][RAG]` |
| DeleteUser | 存在性检查 → 删除 | 是 | 是 | **EQUIVALENT** | `[GO] :40-61` `[RAG] :526-553` |
| DeleteUser | 级联删头像历史 | **不删**（无 FK） | **不删**（无 FK） | **EQUIVALENT**（共有孤儿行） | `[GO] model/user.go:54-57` + `02_create_user_avatar_history.sql` 无 FK |
| DeleteUser | `success` 字段 | 仅成功路径 true | 仅成功路径 true | **EQUIVALENT** | `[GO] :63-65` `[RAG] :555`。AGENTS §1.1 第 5 条在本组件**不成立** |
| DeleteUser | span 关联 | `spanCtx` 传给全部 logger | `Context.current().with(span)` | **EQUIVALENT** | `[GO] :35-36` `[RAG] :521` |
| SelectAvatar | history_id trim 后为空 | `InvalidArgument` | 同 | **EQUIVALENT** | `[GO] :33-36` `[RAG] :576-579` `[T]` |
| SelectAvatar | **响应 `is_current`** | 事务后显式置 true | 事务内显式置 true | **EQUIVALENT** | `[GO] model/user.go:174` `[RAG] UserAvatarHistoryMapper.java:196`；`[PG]` `selectAvatarHistorySucceedsAndIsolatesByUid`。**AGENTS §1.1 第 5 条（SelectAvatar）已修** |
| SelectAvatar | 事务 5 步原子性 | 真 PG 事务 | `@Transactional` 在 mapper `default` 上，经代理进入 | **EQUIVALENT** | `[PG]` `createAvatarHistoryRollsBackOnRealPg` / `selectAvatarHistoryMissingDoesNotClearCurrent` |
| SelectAvatar | **是否有行锁** | 无 `FOR UPDATE` | 无 `FOR UPDATE` | **EQUIVALENT**（AGENTS §1.1 第 7 条在本组件**不成立**） | `[GO] model/user.go:134-169` `[RAG] UserAvatarHistoryMapper.java:175-211` |
| SelectAvatar | history 查询带 uid 隔离 | 是 | 是 | **EQUIVALENT** | `[GO] :142` `[RAG] :55`；`[PG]` 负向用例 |
| SelectAvatar | NotFound 两条分支的 gRPC code | 都是 `NOT_FOUND`（message 不同，见 N-2：Go 源码乱码） | 同 | **EQUIVALENT**（code 层） | ⚠️ 原写「message 不同，见 V-007」—— **误引**：V-007 只讲**超时路径的 description**，与 `NOT_FOUND` 的 message 无关（那条是 Go 源码乱码，已由 **N-2 签字**）| `[GO] :41-50` `[RAG] :585-595` |
| UploadAvatar | avatar_url trim 后为空 | `InvalidArgument` | 同 | **EQUIVALENT** | `[GO] uploadavatarlogic.go:34-37` `[RAG] :626-629` |
| UploadAvatar | `history_id` 来源 | 服务端 snowflake 字符串 | 同 | **EQUIVALENT** | `[GO] :39,48` `[RAG] :634` |
| UploadAvatar | `content_type` 默认值 | 空 ⇒ `image/png` | 同 | **EQUIVALENT** | `[GO] model/user.go:179-184` `[RAG] AvatarDomainService.java:90-93` `[T]` |
| UploadAvatar | 事务 4 步原子性 | 真 PG 事务 | 同上 | **EQUIVALENT** | `[PG]` `createAvatarHistorySucceedsOnRealPg` / `...RollsBackOnRealPg` |
| GetAvatarHistory | 用户存在性检查 | 有 | 有 | **EQUIVALENT** | `[GO] :32` `[RAG] :689-702` |
| GetAvatarHistory | limit clamp `[≤0 或 >30] ⇒ 30` | 静默 clamp | 静默 clamp | **EQUIVALENT** | `[GO] model/user.go:115-117` `[RAG] UserAvatarHistoryMapper.java:44` |
| GetAvatarHistory | 排序 | `create_time DESC` | `create_time DESC` | **EQUIVALENT** | `[GO] :122` `[RAG] :47` |
| GetAvatarHistory | `create_time` 渲染格式 | `time.RFC3339`，**秒精度**（布局无 `.999` 段） | `yyyy-MM-dd'T'HH:mm:ssXXX`，秒精度 | **EQUIVALENT**（格式层） | `[GO] avatarhelpers.go:16-19` `[RAG] AvatarDomainService.java:51-52,74` |
| GetAvatarHistory | **`create_time` 时区标签与底层墙钟** | `timestamptz` + DSN `TimeZone=Asia/Shanghai` ⇒ 带偏移 | `timestamp` + JDBC 无 TimeZone ⇒ 恒 `Z` | **OBSERVABLE DIFFERENCE** | **V-004** |
| Logout | 空 token | `InvalidArgument "Token不能为空"` | 同 | **EQUIVALENT** | `[GO] logoutlogic.go:31-34` `[RAG] :740-742` `[T]` |
| Logout | Redis key | `"user:jwt_blacklist:" + 整串 token` | 同 | **EQUIVALENT** | `[GO] :35` `[RAG] :745` `[T]` |
| Logout | Redis value | 字面量 `"banned"` | 同 | **EQUIVALENT** | `[GO] :37` `[RAG] :748` `[T]` |
| Logout | TTL | `UserAuth.AccessExpire` = 2592000 | `user-rpc.user-auth.access-expire` = 2592000 | **EQUIVALENT** | `[GO] etc/user.yaml:35` `[RAG] application.yaml:96` |
| Logout | **`success` 字段** | 恒 `false` | 恒 `false` | **EQUIVALENT**（跟随后 Go 的可疑形态） | `[GO] :43` `[RAG] :756` |
| Logout | token 有效性检查 | **无** | **无** | **EQUIVALENT** | `[GO] 全文 35 行` `[RAG] :737-757` |
| 全组件 | 指标名 / label | `user_rpc_register_total{result=success\|fail_exists\|db_error\|internal_error}`、`user_rpc_login_total{result=success\|user_not_found\|db_error\|internal_error}` | 逐条 1:1 | **EQUIVALENT** | `[GO] metrics/metrics.go:9-22` `[RAG] UserRpcMetrics.java:47-79` |
| 全组件 | 指标**可抓取性** | 🔴 **生产也起 DevServer**（`go-zero@v1.10.2/core/service/serviceconf.go:77` `devserver.StartAgent(sc.DevServer)` 在 `SetUp()` 里**无条件**调用 ⇒ `:83-89` `initMode()` 只做 `load.Disable()` + `stat.SetReporter(nil)`、**不门控 DevServer** ⇒ `internal/devserver/server.go:79-82` 只判 `c.Enabled` ⇒ `internal/devserver/config.go:5` `Enabled` **default=true**（⚠️ **该默认值只在「DevServer 段存在但没给值」时生效** —— 真源 `go-zero@v1.10.2/core/service/serviceconf.go:40` 的 `DevServer DevServerConfig` 带 **`optional`** 标签 ⇒ **整段缺失 ⇒ 保持零值 ⇒ `Enabled=false` ⇒ 不启动** ⇒ ⇒ **本域成立是因为 `user.yaml:23-27` 显式写了 `DevServer: Enabled: true`**（`admin.yaml:31-35` 同款，端口 9097）⇒ ⇒ **不是「DevServer 默认全开」**）⇒ `zrpc/server.go:63` `c.SetUp()` 走同一条路）⇒ **生产就有** `/metrics` + `/healthz`（`user.yaml:23-30` ⇒ 端口 **9094**） | 载体已补（`pom.xml:218` `spring-boot-starter-web` ＋ `application.yaml:231-232` `server.port: ${USER_RPC_MANAGEMENT_PORT:9094}` ＋ `:266` `include` 含 prometheus）⇒ **有 HTTP 抓取口**（端口与 Go 的 9094 一致）⇒ ⇒ ⚠️ **暴露路径不同**（Go `/metrics` vs RAG `/actuator/prometheus`）⇒ 属 `twoStage.md` §10「**暴露形态差异**」⇒ 按 follow-rpc / favorite-rpc 同款口径**不新造偏离登记** ⇒ ⇒ **运行期守门已真跑通过**（`UserRpcPrometheusCarrierGateTest` `tests=4 · failures=0 · errors=0 · skipped=0`） | 🟢 **已闭合（2026-10-31 · Jason 追认 ＋ 运行期真跑）** —— ⚠️ 原判「**OBSERVABLE DIFFERENCE**」**已被载体落码消解**：RAG 侧现与 Go **同端口（9094）可抓** ⇒ ⇒ **仅余暴露路径不同**（Go `/metrics` vs RAG `/actuator/prometheus`）⇒ 属 `twoStage.md` §10「**暴露形态差异**」⇒ 按 follow-rpc / favorite-rpc 同款口径**不新造偏离登记** ⇒ ⇒ **原值保留备查** | `[GO] user.yaml:23-30` + `go-zero@v1.10.2/core/service/serviceconf.go:77` / `:83-89` + `internal/devserver/server.go:79-82` + `internal/devserver/config.go:5` + `zrpc/server.go:63`；`[RAG] application.yaml:155-167`<br>⚠️ **2026-10-30 订正（G1 lane · `align/g1-users-follow`）**：原判「**DevServer 仅 dev/test 起，生产无端点 ⇒ 两侧生产都不可抓 ⇒ EQUIVALENT**」**不成立** —— Go 侧 `SetUp()` **无条件**起 DevServer、**`initMode()` 也不门控它** ⇒ ⇒ **Go 生产可抓、RAG 零出口** ⇒ 改判 **OBSERVABLE DIFFERENCE**。落点 = **users 域 rpc 层的指标出口方案**（与 follow 域 `DRIFT-FOLLOW-006` / `S1-FOLLOW-001` 同族；`docs/DRIFT/follow/rpc.md:330-336` 已把 **9094 / 9097** 明确划归 **users 域**）⇒ ⚠️ **修法 = 引入 `spring-boot-starter-web`（纯 gRPC 进程 → 带 Tomcat ⇒ 部署形态变更）+ 关 Nacos 自动注册（否则注册的是 web 容器端口）** ⇒ **属「全仓 rpc 层指标出口统一方案」** ⇒ **用户 2026-10-24 已定交主会话拍板** ⇒ ⇒ **本轮只订正判定、只登记，不落码**（且**不在 `docs/DRIFT/_cross/stage1-derivation.tsv` 的 `wave=G1` 开放行内** ⇒ 不在本波派工面）。核对日期 2026-10-30。 ⚠️ **2026-10-30 16:7x 补载体层证据（依赖树实测，非 grep pom）**：`service/users/user/user-rpc` 的实际 classpath 只有 `spring-boot-starter-actuator`（**无** `spring-boot-starter-web` / **无** `tomcat-embed-core`）⇒ ⇒ **无 servlet 载体 ⇒ `/actuator/prometheus` 没有载体 ⇒ 「`management:` 段三重关闭」不是本域独有问题** ⇒ ⇒ ⚠️ **【2026-10-31 再订正】** 原写「**10 个 rpc 里 9 个无载体**（仅 `follow-rpc` 有）⇒ ⇒ 本行「RAG 侧零出口」的改判**成立且比原判更强**（不是本域孤例，是 **9/10 的通例**）」**已被推翻、且加强方向反了** ⇒ ⇒ 逐域实测 = **载体面 8/10 有 / 2/10 无**（无载体 = `comment-rpc`（显式 `web-application-type: none`）· `task-rpc`（classpath 无 servlet 指示物））⇒ ⇒ 且「**依赖树里有 `tomcat-embed-core` ⇒ 有载体**」这个判据**本身不成立**（它只是三条件之一：另两个 = `spring-web` 指示类在 ＋ 未被 `web-application-type: none` 关掉）⇒ ⇒ **口径唯一来源 = `docs/DRIFT/global/vs-common.md` §4.19.23**（三层：配置面 10/10 · 载体面 8/10 · 可抓取面 8/10）⇒ ⇒ ⇒ **本行「RAG 侧零出口 ⇒ 载体已落 ⇒ 有 HTTP 抓取口」的改判仍然成立**，但**「9/10 通例」这个加强说法已作废**⚠️ **2026-10-30 载体落地后本句已过期** —— 现为 **8/10 有载体**（⚠️ **2026-10-31 订正 · 合并收口执行者**：原写「3/10」（`follow-rpc` ＋ `user-user-rpc` ＋ `user-admin-rpc`）**已过期** ⇒ ⇒ **逐模块依赖树实测 `tomcat-embed-core`**：有载体 = article / favorite / follow / hot / like / message / user-user-rpc / user-admin-rpc；无载体 = **comment-rpc / task-rpc**）⇒ ⇒ 本域那一格已闭合（详见本文件末尾载体小节）⇒ 完整两层口径见 `docs/DRIFT/follow/vs-common.md` 末尾对应小节<br>🟢 **【2026-10-31 订正 · Jason 追认 ＋ 运行期真跑】** 原写「**本轮只订正判定、只登记，不落码**」**已过期** ⇒ ⇒ **Jason 于 2026-10-31 拍板「追认保留」**（`S1-FOLLOW-001` 的 users 域实例）⇒ ⇒ **载体已落地**（`8d276b89`）＋ **运行期守门已落并真跑通过**（`9754344d`：`UserRpcPrometheusCarrierGateTest` `tests=4 · failures=0 · errors=0 · skipped=0`）⇒ ⇒ **原值保留备查**。
| 全组件 | 指标注册形态 | `prometheus.Unregister(GoCollector/ProcessCollector)`（`:25-26`） | Micrometer 保留默认 collector | **IMPLEMENTATION DIFFERENCE** | 无行为影响 |
| 全组件 | 死代码 | 无 | 3 个异常类 + 2 个 mapper 方法零调用点，其中 3 个异常类的 javadoc 引用**不存在的 Go 出处** | **OBSERVABLE DIFFERENCE**（无行为影响） | **V-008** |

### 4.3 横切维度（超时 / 建表 / 配置）

| Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|
| 服务端超时时长 | struct tag default 2000ms（`go-zero@v1.10.2/zrpc/config.go:46`） | `observability.rpc-timeout: 2s` | **EQUIVALENT** | 本轮实读 `go-zero@v1.10.2/zrpc/config.go`，**行号指向消费该 tag 的 `conf.MustLoad`** |
| 超时触发机制 | `context.WithTimeout` 真取消（ctx 到 PG） | `call.close(DEADLINE_EXCEEDED)` + `DeadlineState` + `setQueryTimeout` 补 | **IMPLEMENTATION DIFFERENCE**（B-1 已签字：JDBC 不响应 interrupt） | `_shared.md` §6.2.6 E-4 |
| 超时的 gRPC **description** | `"context deadline exceeded"`（`rpc.go:69`） | **逐字相同**（`RpcInterceptor` 的超时描述常量，global 修后命名为 `GO_DEADLINE_EXCEEDED_DESCRIPTION`） | **EQUIVALENT** | ✅ **已修（2026-10-26，`global` 域）** —— 修前 RAG 为 `"rpc timeout after 2000ms"`；修前状态见 §7 V-007；📌 **已合回主干（`4f839f9`，2026-10-26 23:1x）** ⇒ ✅ **本域双侧活体已复跑**（2026-10-26 23:31 · `REDIS_MODE=hanging` · 3 轮 description 逐字一致 ⇒ 见 §7 V-007）<br>⚠️ **历史留痕**：本日**早间**拍板为「暂不合回」⇒ 同日 23:1x 实际合回（合并提交 `4f839f9`）⇒ **上面那句已过期** |
| 拦截器是否真挂上 server | `user.go:43` 显式 `AddUnaryInterceptors` | `@Bean GrpcServerConfigurer`（`UserRpcApplication.java:103-107`） | **EQUIVALENT** | `[T]` `GrpcInterceptorWiringTest`（含对照用例证明 starter 自身无此入口） |
| 建表机制 | GORM `AutoMigrate`（无账本） | `SchemaInitializer` 幂等重放 + PG advisory lock | **IMPLEMENTATION DIFFERENCE**（RAG **更强**：见 `_shared.md` §6.2.5 并发实测） | `[PG]` `UserRpcSchemaTextColumnTest` + `schemaInitCreatedJsonbColumns` |
| 建表失败 | `log.Fatalln` → 进程退出 | 抛 `IllegalStateException` → 启动失败 | **EQUIVALENT** | `[GO] db.go:33-36` `[RAG] UserSchemaBootstrap.java:52-56` |
| **建表责任交叉** | `AutoMigrate(&User{}, &UserAvatarHistory{})`，`users` 排第 1 | 4 份 DDL **全部**重放，含 `admin_admin` / `admin_invite` | **OBSERVABLE EQUIVALENT**（两侧在「两个 rpc 并发对同一个空库启动」下都能起来、表都建齐、0 致命错误） | **§9.2**（双侧对称并发启动实测，共 9 轮；Go 侧靠 `HasTable` 的**时序侥幸**，RAG 侧靠 advisory lock ⇒ **机制不同但可观察结果一致**） |
| Flyway 是否残留 | N/A | `mvn -o -pl … dependency:list \| grep -i flyway` ⇒ **零命中** | **EQUIVALENT**（已按 §6.3 问题 1 移除干净） | 本轮实测；`pom.xml:136-154` 的注释完整记录了「只删 flyway-core 不够」 |
| DB 连接串时区 | `db.go:21` 钉 `TimeZone=Asia/Shanghai` | `application.yaml:13` **未指定** | **IMPLEMENTATION DIFFERENCE**（⚠️ 原记 OBSERVABLE DIFFERENCE 属**过度归因**） | **U-002 已闭环**：偏移量由**进程本地时区**（Go 的 `time.Local`）决定，**PG 会话时区不参与**（pgx 走二进制解码；`pgtype/timestamptz.go:133-137` `ScanLocation` 默认 nil → `:270-276` 回落到 `time.Local`）⇒ **DSN 的差异不是观测差异的成因**。⚠️ 残留：PG 会话时区会影响 `NOW()` / `CURRENT_TIMESTAMP` 这类**会话时区相关表达式** ⇒ 见 **V-011**（当前不可观察）。列类型本身已改 `timestamptz`（V-004 已修） |
| Snowflake workerId 解析 | `env → hash`（`snowflake.go:52-85`） | `env → properties → hash` | **OBSERVABLE DIFFERENCE**（配置超集） | DRIFT-005 已留档，本轮只确认 Go 侧优先级 |
| Snowflake 启动期 fail-fast | **无**（懒初始化，首个 `GetID()` 才 init） | `ApplicationReadyEvent` 校验 workerId 越界则启动失败 | **OBSERVABLE DIFFERENCE** | `[GO] snowflake.go:41-50` `[RAG] SnowflakeBootstrap.java:37-47` |
| 服务发现 | etcd 静态 `Key: user.rpc` | Nacos `discovery://user-rpc` | **IMPLEMENTATION DIFFERENCE**（AGENTS §3.4 已签字：部署面偏离） | — |

### 4.4 结果统计（**只数本轮新发现，不含已签字偏离**）

| 类别 | 数量 | 编号 |
|---|---|---|
| **SEMANTIC MISMATCH** | **0** | ~~V-001~~（**2026-10-24 已闭环**：Go 的 GORM 把 `status IS NULL` 读成 **0** 且不报错 ⇒ **两侧都是 `status=0` 登录成功** ⇒ 行为一致 ⇒ 该条已修） |
| **OBSERVABLE DIFFERENCE** | **2** | **V-002**（`users_user.status` NOT NULL 差异，🟡 真库实证确认 ＋ **2026-10-27 订正：DDL 层差异 · 行为层零差异 ⇒ 建议保持 NOT NULL**（原写「🔴 未修 · 需与 admin 域同改」**已作废** ⇒ 放开可空反而**制造新漂移** ⇒ 落点无动作））<br>✅ **已销账**：**V-007**（超时的 gRPC `description` 不同，归属 `service/common`）—— **2026-10-26 由 `global` 域修完**（见 §7；📌 **已合回主干 `4f839f9`（2026-10-26 23:1x）** ⇒ ✅ **本域双侧活体已复跑**，description 逐字一致）<br>🔴 **V-012（新 · 2026-10-26 第十一轮）**：**超时时的业务日志错误归因** —— RAG 记「Redis 写入异常」而客户端收到的是「超时」，Go 两侧一致（**观测面**，非 wire 面）<br>⚠️ 已闭合的：**V-003**（jsonb⇒text，已修）· **V-004**（timestamptz，已修）· **V-005**（**判为不成立** —— protobuf3 空 map 在 wire 上不可表达）· **V-006**（雪花 id，永不进 wire）· **V-008**（死代码 + 伪出处，已修） |
| IMPLEMENTATION DIFFERENCE | 4 | 表名 / 指标注册 / 建表机制 / 服务发现 |
| 已签字偏离（引用不重开） | 2 | N-1、N-2（见 `./api.md`） |
| **UNKNOWN / unresolved** | **0** | §9 的 **7 条（U-001 ~ U-007）已于 2026-10-24 全部闭环** ⇒ §9.1 / §9.2 / §9.3 |
| ⚠️ **新发现但当前不可观察** | 1 | **V-011**（时间列 DEFAULT `CURRENT_TIMESTAMP`，Go 无 DEFAULT）⇒ 两侧写入路径都显式写该列 ⇒ **当前不可观察** |
| ⚠️ **源码层形态差异（客户端不可见 ⇒ 不计入 OBSERVABLE DIFFERENCE）** | 1 | **V-013**（2026-10-26 第十三轮）—— DB 超时的**源码层**形态两侧不同（Go 一律糊成 `codes.Internal` / RAG 保留 `DEADLINE_EXCEEDED`），但**客户端可见面两侧逐字一致**（E-4 四组 Go 采样全是 `{"code":1015}`）⇒ **不是 wire 漂移**。⚠️ **反向风险**：照「Go 是 INTERNAL」把 18 处 catch 改回去 ⇒ RAG 渲染 5001 ⇒ **离 Go 更远** ⇒ **不得改回 Go 行为** |

### 4.5 复核历史导航页 L34–41 的三条「缺口」

| 原缺口陈述 | 本轮结论 |
|---|---|
| 「proto 逐字段对照无独立留档」 | **已闭合（本轮补齐）**：两侧 `.proto` 剥离 `option`/注释/空行后 `diff` **零差异**；另有常驻守门脚本 `tools/difftest/check-proto-static.sh`（`user`/`admin` 两个 proto 有网，见 AGENTS.md §3.4） |
| 「`AvatarTransactionWiringTest` 无真实 PG，不证明 jsonb CAST」 | **已闭合且本轮升级复跑**：`UserRpcContainerWiringTest` **16 条**跑在真 `postgres:16-alpine` 上，**2026-10-26 实测 16/16 绿**，覆盖 jsonb `Types.OTHER` 写入、`@Results` 读回 Map、`CAST(... AS jsonb)`、4/5 步**真实行回滚**、UNIQUE 约束，**外加第十二轮的 3 条 B-1 装配层守网**（插件链 / 挂载点 / 负控） |
| 「本模块的 `HUMAN_ONLY.MD` 不存在」 | **原结论错误，予以修正**：`service/users/user/user-rpc/HUMAN_ONLY.MD` **确实存在**。本轮 `glob **/HUMAN_ONLY.MD` 实测 **4 份**（`user-rpc` / `user-api` / `admin-rpc` / `admin-api`），与 AGENTS.md §2 的计数一致 —— **错的只是「本模块没有」这一句，不是计数**。⚠️ 这类「文件已写 / 文件不存在」是同款需证据的断言（AGENTS.md §2 已自陈）：改清单前先跑 glob，别照抄上一版 |

---

## 5. External Interaction & Error Flow（spec §8 + §10）

### 5.1 外部交互清单

| External System | Operation | Input | 期望结果 | 失败结果 | 结果是否影响分支 | 失败是否阻断操作 | 副作用 |
|---|---|---|---|---|---|---|---|
| PostgreSQL | `SELECT ... WHERE username=? LIMIT 1` | username | 1 行 / 0 行 | 抛异常 | **是**（`NotFoundException` vs `db_error` 两条完全不同的分支） | **是** | 无 |
| PostgreSQL | `SELECT ... WHERE uid=? LIMIT 1` | uid | 1 行 / 0 行 | 抛异常 | **是** | **是** | 无 |
| PostgreSQL | `INSERT INTO users_user ...` | UserEntity | 1 行 | UNIQUE 冲突 | **是**（→ `INTERNAL "DB更新失败"`） | **是** | 写 |
| PostgreSQL | `UPDATE users_user SET ... WHERE uid=?`（动态 SET） | uid + patch | affected 行数 | 抛异常 | **否**（RAG 返回 0 / 1，两侧都不据此分支） | **是** | 写 |
| PostgreSQL | `DELETE FROM users_user WHERE uid=?` | uid | affected 行数 | 抛异常 | **是** | **是** | 删 |
| PostgreSQL | 事务闭包（4/5 步） | — | 全部成功 | 任一步失败整体回滚 | **是** | **是** | 写/删 |
| Redis | `SETEX user:jwt_blacklist:<token> 2592000 "banned"` | token | OK | 抛异常 | **是**（→ `INTERNAL "注销失败,Redis写入异常"`） | **是** | 写 |
| Prometheus/Micrometer | Counter +4 | — | 内存计数 | N/A | 否 | 否 | 无 |

> ⚠️ **「空结果 ≠ 依赖失败」这条两侧都做对了**，值得单独记：查不到用户是**业务分支**（`NotFoundException`），
> PG 连不上是**依赖失败**（`INTERNAL`）。RAG 用 `NotFoundException` 异常子类承载「空」，
> Go 用 sentinel error `model.ErrorNotFound` 承载「空」，两边都把它与真异常分开 ⇒ **EQUIVALENT**。

### 5.2 Error Flow 对照（每种 failure 一行）

| Failure | 触发条件 | Go 处理 | Go 输出 | RAG 处理 | RAG 输出 | 等价？ |
|---|---|---|---|---|---|---|
| 用户不存在 | `FindOneBy*` 0 行 | sentinel `ErrorNotFound` | 视 OP：`NOT_FOUND` / `status=1` / `INVALID_ARGUMENT` 前置 | `NotFoundException` | 同 | **YES** |
| 头像历史不存在 | `history_id` 查不到 | `ErrorAvatarHistoryNotFound` | `NOT_FOUND` | `AvatarHistoryNotFoundException` | `NOT_FOUND` | **YES** |
| PG 故障 | 连接/权限/SQL 错 | 裸 GORM err | `INTERNAL` + `DB查询失败`/`DB更新失败`/`DB插入失败` | 裸 Exception | 同 | **YES** |
| Redis 故障 | Setex 失败 | 裸 err | `INTERNAL "注销失败,Redis写入异常"` | 同 | 同 | **YES** |
| bcrypt 失败 | >72 字节 | `PasswordEncrypt` 返 err | `INTERNAL "密码生成失败"` | 同 | 同 | **YES** |
| snowflake 失败 | init 失败 | `GetID` 返 err | `INTERNAL "uid生成失败"` / `"history_id生成失败"` | 同 | 同 | **YES** |
| 超时 | 2000ms | interceptor select | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | `call.close` | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | **YES**（code 与 description 两侧全等）—— ✅ 原为「code YES / description NO」（V-007，**2026-10-26 已销账**） |
| SQL 被取消 | 57014 | ctx cancel ⇒ GORM 返 ctx err ⇒ **落到 `INTERNAL`** | `INTERNAL "DB查询失败"` | `SqlTimeoutStatusTranslator` 翻成 `DEADLINE_EXCEEDED` | `DEADLINE_EXCEEDED` | **NO** —— 但两侧都只在 2000ms 之后才发生，**外层 interceptor 已先返回**，内层结果被丢弃 ⇒ **客户端可见行为仍一致**。已由 `_shared.md` §6.2.6 E-4 实测闭环（Go 182ms / RAG 176ms 查询滞留） |
| 未映射异常 | 任何 `Throwable` | Go 不会走到（每条都显式 `status.Error`） | — | `UserServiceGrpcImpl` 兜底 | `INTERNAL` + `"类名: 消息"` | **IMPLEMENTATION DIFFERENCE**（RAG 多一层兜底，Go 侧对应情形是 panic ⇒ gRPC `UNKNOWN`） |

> ⚠️ **最后一行不要当成「RAG 更好」**：`UserServiceGrpcImpl` 的 `catch (Throwable)` 兜底是 **DRIFT-02** 的修复，
> 目的是把 grpc-java 默认的 `UNKNOWN` 收敛成 `INTERNAL`。Go 侧真出现 panic 时 grpc-go 会返回 `UNKNOWN`
> （`status.FromError` 拿不到 code 时 `markRPCResult` 走 `codes.Unknown`，`rpc.go:80-83`）。
> ⇒ **真触发 panic 时两侧 code 会不同**。本轮**未实测**（无 panic 注入路径），记为 UNRESOLVED-USERRPC-007 的一部分。
> ⚠️ **2026-10-24 更新**：`UNRESOLVED-USERRPC-007` 已闭环（43 场景双侧活体对拍，§9.1），
> **但 panic 路径不在那 43 个场景里** ⇒ 本条**仍未实测** ⇒ 保留为 §7 的**未量化残留差异**，
> 不再挂在已关闭的 UNRESOLVED 名下。

---

## 6. 逐维度结论（spec §12–§21）

### 6.1 State Semantics
- **头像状态机**：Go 与 RAG 都是「清全部 current → 置目标为 current」，`is_current` 布尔、无历史链。
  两边的状态转换集合**完全相同**。初始态（无 current）与非法态（两条 current）两边同样可达。
- **登录三态**：`{status, uid}` 的取值集合相同；**三个失败态都是 `err=nil`**，这点极易被误改。
- **Redis 黑名单**：Go 侧「写进去就是 banned，读判在 api 层的中间件」；RAG 同。**user-rpc 自己从不读** ⇒ 本组件无状态语义。

### 6.2 Cache Semantic Equivalence
**本组件无缓存**（N/A）。⚠️ 但有一条必须写下来：Go 侧的 `autoUpdateTime` 常被误认为「触发器」，
它其实是 GORM 在 `Updates` 时拼进 SET 子句的行为；RAG 用显式 `setSql` 复刻，**不是** 依赖 PG 侧 `DEFAULT`。

### 6.3 Authentication / Authorization Semantic Equivalence
**本组件不做鉴权**（N/A）。它信任 `uid` 是 `int64` 协议字段、无解析步骤 ——
⇒ **AGENTS.md §1.1 的「uid 解析静默转 0」在本组件不存在**（那是 user-api `IdentityResolver` 的事，
且 N-3 已证伪「RAG 提前拦截 uid=0」）。
⚠️ 但**一处需要注意**：`Logout` 对 token **不做任何校验**即写入黑名单，两侧一致 ——
这是一条**共有**的、可被用来把任意字符串塞进 Redis 的行为，**不是漂移**，登记在此以免日后被误当成 RAG 缺陷修掉。

### 6.4 Transaction Semantic Equivalence
| | Go | Spring Boot |
|---|---|---|
| 边界 | `m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error{...})`（`model/user.go:72` / `:134`） | `@Transactional` 在 **mapper 接口的 `default` 方法**上（`UserAvatarHistoryMapper.java:118` / `:174`） |
| 是否经代理 | 不需要（直接调 GORM） | **经代理**：`UserDomainService.uploadAvatar:648` / `.selectAvatar:584` 是**跨 Bean 调用** |
| **self-invocation 风险** | 无 | **无** —— 方法体内的 `clearCurrentByUid` / `insertHistory` / `updateUserExtraInfo` 虽是同 Bean 自调用，但此时事务**已在代理边界开启**，靠 `SqlSessionTemplate` → `DataSourceUtils` 的线程绑定连接参与同一事务 |
| 回滚条件 | 闭包返回 err | 抛 `RuntimeException`（默认 rollbackOn） |
| 证据 | — | `[PG]` 真 PG：W3 撞 `UNIQUE(history_id)` ⇒ W2 的 UPDATE 被真回滚；`AvatarHistoryNotFoundException` ⇒ W3 的 clearCurrent 不生效 |

⇒ **结论：`EQUIVALENT`。AGENTS.md §1.1 第 6 条（`CreateAdmin` self-invocation）在本组件不成立。**
⚠️ 但**必须记下这条的脆弱性**：本组件的正确性依赖「调用方永远从别的 Bean 调进来」。
一旦有人把 `createAvatarHistory` 的调用改成同 Bean 自调用，**不会有任何报错**。
现有 `AvatarTransactionWiringTest#userMapperIsNotProxied_butStillJoinsAmbientTransaction`
**测不到这一点**（它测的是 UserMapper 不被代理，不是「入口必须经代理」）。

### 6.5 Concurrency Semantic Equivalence
| 维度 | Go | Spring Boot | 结果 |
|---|---|---|---|
| 并发注册同名用户 | TOCTOU + UNIQUE 兜底 → 一成一败 | 同 | **EQUIVALENT** |
| 并发 `UploadAvatar` 同一 uid | 都无锁 ⇒ 可能两条 `is_current=true` | 同 | **EQUIVALENT（共有缺陷）** |
| 并发 `SelectAvatar` | 都无锁 ⇒ 同上 | 同 | **EQUIVALENT（共有缺陷）** |
| 并发 `DeleteUser` / `UpdateUser` | 无锁 | 无锁 | **EQUIVALENT** |
| 幂等性 | 全部 RPC 都**不幂等** | 同 | **EQUIVALENT** |

⇒ 本组件**没有任何一处悲观锁**，两侧一致。AGENTS.md §1.1 第 7 条（邀请码缺 `FOR UPDATE`）
与 admin-rpc 相关，**在本组件无对应面**。

### 6.6 Timeout Semantic Equivalence
- **来源**：Go 藏在 struct tag（`go-zero@v1.10.2/zrpc/config.go:46`，`user.yaml` 未配），RAG 显式 `observability.rpc-timeout: 2s`。
  ⚠️ 这正是 §3.3.1 的典型：**只看 yaml 会以为 Go 没有超时**；读到消费 tag 的那段控制流才拿到 2000ms。
- **时长**：2000 = 2000 ⇒ **EQUIVALENT**。
- **scope**：Go 是 `context.WithTimeout` 包住整个 handler；RAG 是「调度线程到点 `call.close` + 业务侧 `checkDeadline()` 主动抛」。
  **两者对客户端的可见 scope 等价**（都在 2000ms 让调用方拿到 `DEADLINE_EXCEEDED`），
  但对**在途 IO 的影响不同** —— B-1 已签字（Java interrupt 到不了 JDBC），
  `DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator` 是补丁，实测查询滞留已从 4657ms 降到 176ms。
- **description**：**两侧逐字一致**（`context deadline exceeded`）—— ⚠️ 原为「**不等价** ⇒ V-007」，
  **V-007 已于 2026-10-26 由 `global` 域销账**（§7）。

### 6.7 Retry Semantic Equivalence
**两侧都无 Retry**（N/A）。⚠️ 明确记录：Go 的 `mustNewRedis` 在启动期失败会 `log.Fatalln`，
RAG 的 Redis 是懒连接 —— **首次 `logout` 才会暴露连接问题**。Go 侧 `svc.NewServiceContext`
（`servicecontext.go:29` `redis.MustNewRedis`）在 `zrpc.MustNewServer` **之前**执行，
RAG 侧无对应物。记为 V-008 的姊妹项，见 §9 UNRESOLVED-USERRPC-004。
> ⚠️ **2026-10-24 更正（§9.3）**：本段原推断「Go 侧是**启动期 fail-fast**」**已被活体实验证伪** ——
> go-zero `RedisConf.NonBlock` 的 struct tag 默认是 `true` ⇒ `MustNewRedis` **不做连接检查**
> ⇒ **两侧都是「启动成功」**。上面这段保留原文供追溯，**不要再当结论引用**。

### 6.8 Serialization Semantic Equivalence
| 维度 | Go | Spring Boot | 结果 |
|---|---|---|---|
| wire 契约 | proto3 | 同（逐字节核对） | **EQUIVALENT** |
| extra_info 参数绑定 | 驱动按 `text` 列写 | `ps.setString`（原 `ps.setObject(..., Types.OTHER)`） | ✅ **列类型已一致（V-003 已修，`ec33159`）**，两侧都按 text 写<br>🔴 **2026-10-26 主干实测补记**：`user-rpc` 与 **`admin-rpc`（`c10f639`）两侧都已是 `JsonTextTypeHandler` ⇒ `ps.setString`** ⇒ **这一格在主干上成立**（`admin-rpc` 侧是 `users/admin` 域自己修的，**不是** global B10 `ff80214`）⇒ 详见 §7 V-003 的「2026-10-26 补记」 |
| json 序列化 key 序 | `encoding/json` 对 `map[string]string` **按键排序** | Jackson `LinkedHashMap` **保持插入序**，再被 jsonb 按 (长度,字节) 重排 | **两端都不透传 key 序**（proto 是 map）⇒ wire 不可见 |
| `create_time` 格式 | RFC3339 秒精度 | 同（`[T]` 3 条用例锁死） | **EQUIVALENT** |
| `create_time` 时区标签 | 带偏移 | 恒 `Z` | **OBSERVABLE DIFFERENCE**（V-004） |
| 空 map vs null map | proto 两者都编码为「空」 | 同 | **EQUIVALENT** |

### 6.9 Configuration Semantic Equivalence
| 配置项 | Go | Spring Boot | 结果 |
|---|---|---|---|
| `AccessExpire` | `user.yaml:35` = 2592000 | `application.yaml:96` `${USER_JWT_EXPIRE:2592000}` | **EQUIVALENT**（RAG 多一层环境变量覆盖面） |
| `AccessSecret` | `user.yaml:34` 写死 | `application.yaml:95` 同值 + env 覆盖 | **IMPLEMENTATION DIFFERENCE**（本组件**不消费** accessSecret，签发在 user-api） |
| DB 时区 | `TimeZone=Asia/Shanghai` | **未指定** | **OBSERVABLE DIFFERENCE**（V-004） |
| Redis | `BizRedis{Host: redis:6379}` | `spring.data.redis.host` 同 | **EQUIVALENT**（⚠️ 必须在 `spring:` 下，yaml 注释已记录该坑） |
| gRPC 端口 | `0.0.0.0:9004` | `49210` | **IMPLEMENTATION DIFFERENCE**（Infrastructure.md §3 已锁定） |
| gRPC reflection | 仅 DevMode/TestMode 注册 | 默认 `false` | **EQUIVALENT**（Go 生产 `Mode=pro` ⇒ 不注册） |

### 6.10 Lifecycle Semantic Equivalence
| 阶段 | Go | Spring Boot | 结果 |
|---|---|---|---|
| 顺序 | `InitMetrics → conf.MustLoad → logx → logger.Init → DisableNativeRpcTimeout → **NewServiceContext(DB AutoMigrate + MustNewRedis)** → MustNewServer → Start` | Spring 容器 refresh：MP/DataSource/Redis 自动配置 → `@MapperScan` 注册 → **`UserSchemaBootstrap.@PostConstruct`（DDL）** → gRPC server 生命周期 → **`ApplicationReadyEvent`：`SnowflakeBootstrap` + `NacosRegistration`** | **IMPLEMENTATION DIFFERENCE** |
| 建表失败 | `log.Fatalln` 进程退出 | `IllegalStateException` → 启动失败 | **EQUIVALENT** |
| Redis 不可用 | 启动即 `MustNewRedis` 失败 ⇒ 进程退出 | 启动成功、首次 `logout` 才失败 | **OBSERVABLE DIFFERENCE**（见 §6.7） |
| Snowflake 不可用 | 懒初始化，首个 `Register`/`UploadAvatar` 返 `INTERNAL` | `ApplicationReadyEvent` 校验 workerId，越界则启动失败 | **OBSERVABLE DIFFERENCE** |
| 关闭 | `defer s.Stop()` | `ObservabilityLifecycle` 回收线程池 | **IMPLEMENTATION DIFFERENCE** |

### 6.11 Lifecycle / State：Snowflake 单例
Go 是包级 `var sf *snowflake.Node` + `sync.Once`；RAG 是 `Snowflake` 静态字段 + `SnowflakeBean` 启动期注入。
**位布局（`EPOCH/NodeBits/StepBits`）、`%1024` 模序、时钟回拨无保护**在两侧一致（DRIFT-005 已逐条核对）。
⚠️ 本轮**只确认了 Go 侧**：`generateNodeId` 的优先级是 `env → hash`（`snowflake.go:55,83`），
RAG 多一层 properties。**该条不重开。**

---

## 7. Semantic Drifts（spec §27 + §36）

> 编号 `DRIFT-USERRPC-V-0xx`。**每条都写明「证据强度」，未取得双侧活体对拍的一律不写成已闭环。**

### DRIFT-USERRPC-V-001 🔴 `Login` 对 `status == NULL` 的分支与 Go **相反**（fail-open） —— ✅ **已修（第二轮）**

- **Component**: user-rpc / **Operation**: `Login`
- **Category**: State Semantics / 分支可达性
- 🔴 **2026-10-24 更正（下方「Go Behavior」那句已被证伪，保留原文供追溯）**：
  真源侧活体实测 + admin 域的机制追查（`admin/rpc.md` §2）证明
  **Go 的 GORM 把 `status IS NULL` 读成 `0` 且不报错**
  （`gorm.io/gorm@v1.31.1` 的 `prepareValues` 对 `int64` 造的是 `**int64` ⇒ `database/sql` 的
  `convertAssign` 走 `case reflect.Pointer: if src == nil { dv.SetZero(); return nil }`
  ⇒ 不报错，字段保持零值 0）⇒ **Go 也是 `status=0` 登录成功**，不是 `INTERNAL`。
  ⇒ 本条 V-001 的实质是「**RAG 的写法与 Go 的写法长得不一样**，但行为相同」⇒ **已修（两侧行为一致）**。
  ⚠️ **下方原文把 Go 的行为写成了「GORM 会失败 ⇒ INTERNAL」—— 那是错的**，
  下面几行的「Go Behavior / Difference」请按本条更正读。
- **Go Behavior**：`users.status` 为 NULL 时，GORM 把 NULL 扫进 `int64` 会失败 ⇒ `FindOneByUserName` 返回
  **非 sentinel 错误** ⇒ 走 `loginlogic.go:50-55` 的 `db_error` 支 ⇒ `codes.Internal "DB查询失败"`，
  **登录被拒**。
- **Java Behavior**：`UserDomainService.java:190`
  `if (user.getStatus() != null && user.getStatus() == 1L)` ⇒ NULL 时**跳过封禁检查**，
  继续走到 `:198-203` 的成功分支 ⇒ 返回 `LoginResp{uid, status=0}`，**登录被放行**。
- **Difference**：同一个「status 为 NULL」的行，Go 拒、RAG 放。方向是 **fail-open**。
- **⚠️ 为什么这条格外刺眼**：**本模块自己的 `getUser` 恰好做了相反的选择**——
  `UserDomainService.java:359-363` 显式写了一段注释：
  > 「绝不 fallback 成 0 —— status=0 在 identity 解析里意为「活跃」，把 NULL 默认成活跃是朝错误方向的 fail-open」
  然后抛 `INTERNAL "DB查询失败"`。
  ⇒ **同一份代码里，`getUser` 认为 fail-open 不可接受，`login` 却正在 fail-open。**
- **Observable Impact**：`status` 列若为 NULL，被封禁的用户可以登录。
- **Business / Technical Impact**：HIGH（安全方向）。但**当前不可达** —— 见下。
- **Why It Matters**：这是 §3.3.1 的反例形态：「配置里没写 ≠ 没做」。`!= null` 这个防御写法看起来是稳健的，
  但它在 Go 里对应的是**相反**的分支结果。
- **Must Preserve**：Go 的语义是「status 不可判定 ⇒ 整条 Login 失败」，不是「当作没封禁」。
- **Evidence**: `[GO]` `service/user/user/rpc/internal/model/data_model.go:11`（`Status int64 gorm:"column:status;default:0"`，**无 NOT NULL**）
  + `internal/logic/loginlogic.go:45-57`；`[RAG]` `UserDomainService.java:190` vs `:359-363`。
  证据强度：**静态源码**。**未取得端到端证据**（`UNRESOLVED-USERRPC-001`）。
  ⚠️ **2026-10-24 更正（端到端证据已取得，且推翻了本条的判定）**：
  真 PG 实测（`UserRpcLoginNullStatusReadsAsZeroTest`，`DROP NOT NULL` + 插 NULL 行 + 反向对照组）
  ⇒ **Go 的 GORM 把 NULL 读成 0 且不报错** ⇒ **两侧都是 `status=0` 登录成功**
  ⇒ ⇒ 本条**不是** SEMANTIC MISMATCH，而是「写法不同、行为相同」⇒ **已修（两侧一致）**。
  ⚠️ 本行「未取得端到端证据」是**过期状态**。
- **Classification**: **SEMANTIC MISMATCH**（可达性未证）
- **Risk**: HIGH（若可达）
- **Recommended Verification**：在 RAG 库里 `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL`，
  插一条 `status IS NULL` 的行，两侧各打一次 `Login`，比对 `Status.getCode()`。
  在改 DDL 前，**不要**把这条按「不可达」销掉。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修（维持）**，**但两处证据指针已漂移**。
> - **被描述的行为是否还在**：**仍在**（fail-closed 守卫确已删除）——
>   RAG 侧 `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:186-203`
>   保留着「守卫已删除」的完整论证（Go 出处三段 + 双侧活体对拍记录），
>   实际判定行在同文件 **`:260`** `if (user.getStatus() != null && user.getStatus() == 1L)`；
>   Go 侧 `service/user/user/rpc/internal/model/data_model.go:11` `Status int64 \`gorm:"column:status;default:0"\``（**无 NOT NULL**）**未变**。
> - **指针订正（保留原值，追加现行位置）**：
>   ① 本条「Java Behavior」栏写的 `UserDomainService.java:190` ⇒ **现行判定在 `:260`**（`:190` 现已是 GORM 出处注释行）；
>   ② 本条「为什么这条格外刺眼」栏写的 `UserDomainService.java:359-363`（「绝不 fallback 成 0」注释段）⇒ **现行已不在该处**
>   （`:359` 现为 `logger.logBusinessErr(...)`、`:363` 现为 `// W4 Insert`）⇒ **该行号是陈旧快照**。
> - **不改判的理由**：「两侧行为一致（Go 把 NULL 读成 0 ⇒ 放行登录）」这一**结论本身**与当前代码一致，
>   且 DDL 侧仍为 `status BIGINT NOT NULL DEFAULT 0`（`service/users/users-common/src/main/resources/db/01_create_user.sql:39`）⇒
>   「RAG 侧不可达」这一半也仍然成立（与 V-002 一致）。

### DRIFT-USERRPC-V-002 DDL：`users_user.status` 的 **NOT NULL** 两侧不同 —— 🟡 **2026-10-27 订正：定性为「DDL 层差异 · 行为层零差异」⇒ 建议保持 NOT NULL**（原标题「🔴 仍成立，但被卡（改动必须与 admin 域同改）」已被推翻 ⇒ **放开可空反而制造新漂移** ⇒ 逐层证据链见下方订正块）

- **Category**: Schema
- **Go**：`gorm:"column:status;default:0"` ⇒ GORM 建出 **`status bigint DEFAULT 0`（可空）**。
- **RAG**：`users-common/src/main/resources/db/01_create_user.sql:39` ⇒ **`status BIGINT NOT NULL DEFAULT 0`**。
- **Difference**：列可空性不同。⇒ 这是 V-001 在 RAG 侧不可达的**直接原因**。
- **Observable Impact**：RAG 侧任何试图写 NULL 的代码都会在 PG 层被拒；Go 侧不会。
- **Classification**: **OBSERVABLE DIFFERENCE**（DDL 层）
- **Evidence**: `[GO]` `data_model.go:11`；`[RAG]` `01_create_user.sql:39`。证据强度：**静态源码**。
  ⚠️ 未实跑 `information_schema` 比对（见 `UNRESOLVED-USERRPC-006`）。
- **Must Preserve**：这是**决策问题**，不是 bug —— 若保留 NOT NULL，V-001 需按「不可达」降级；
  若要与 Go 一致放开可空，V-001 就变成实打实的安全缺陷。**两者必须一起判。**

> 🟢🟡 **2026-10-27 订正（行为层零差异 ⇒ 「保留 NOT NULL」才是等价的那一侧）** —— G1 lane · **本轮独立复核**
>
> ⚠️ **本轮推翻的是「Go 读 NULL status 会扫描报错」这个前提**（它被写进了 G4 的派单 #6）。
> ⇒ **逐层证据链（本轮独立复核，不是转述台账）**：
> | 层 | 证据 | 结论 |
> |---|---|---|
> | ① GORM 装给 int64 字段的 setter | `gorm.io/gorm@v1.31.1/schema/field.go:616-622`（**本轮在 `GOMODCACHE` 里逐行读过**）<br>`case reflect.Int, …, reflect.Int64:` → `switch data := v.(type) { case **int64: if data != nil && *data != nil { field.ReflectValueOf(ctx, value).SetInt(**data) } }` | ⇒ DB 值是 NULL ⇒ `*data == nil` ⇒ **什么都不做** ⇒ 字段保持 Go 零值 **0** ⇒ **不报错** |
> | ② stdlib 转换层 | `database/sql/convert.go:431-435`（**本轮在 `GOROOT` 里逐行读过**）<br>`case reflect.Pointer: if src == nil { dv.SetZero(); return nil }` | ⇒ dest 的 kind 是 **Pointer**（GORM 传的是 `**int64`）⇒ 走这一支 ⇒ **不报错**<br>（⚠️ 网上常见的 `converting NULL to int64 is unsupported` 那条属 `case reflect.Int64` ⇒ **走不到**） |
> | ③ 双侧活体对拍 | 本文件「2026-10-23 第三轮」：`status IS NULL` 打 Login ⇒ **Go 200 + token**；RAG 当时「删掉 `= 0L` + 加 null ⇒ INTERNAL」的版本 ⇒ **5001** | ⇒ **删掉 `= 0L` 才是不等价的那一侧**（已恢复，见 `UserEntity.status` 的 javadoc） |
>
> ⇒ **行为层结论（本条的关键补充）**：
> | | Go | RAG |
> |---|---|---|
> | DDL | `status bigint DEFAULT 0` ⇒ **可空**（真库实证 `is_nullable=YES`，本文件 §4） | `status BIGINT NOT NULL DEFAULT 0` ⇒ **不可空**（`is_nullable=NO`） |
> | 库里有 NULL 行时读出来 | **0**（不报错） | **读不到 NULL**（列里就是 0/1） |
> | **可观察结果** | **status = 0** | **status = 0** |
> ⇒ ⇒ **`DRIFT-USERRPC-V-002` 是「纯 DDL 层差异」，行为层零差异** ⇒ **两侧在 NULL 行上的可观察结果相同**。
>
> ⇒ ⇒ **「保留 RAG 的 NOT NULL」不是缺陷，是等价的那一侧**（RAG 更严：写 NULL 会被 PG 拒；Go 侧会写成 NULL 但读出来还是 0 ⇒ 可观察行为同形）。
> ⇒ ⇒ **「放开可空」反而会制造新漂移**：RAG 侧 `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 若字段无 `= 0L` 初始值 ⇒ 读出 **null** ⇒ 与 Go 的 **0** **不一致**（且 admin 侧会从「NULL 当活跃(0)」变成「NULL 当未知(null)」⇒ **方向相反**）。
> ⇒ ⇒ **本条从「🔴 仍成立，但被卡」降级为「🟡 DDL 层差异 · 行为层零差异」**，**建议保持 RAG NOT NULL**。
> ⇒ ⚠️ **本条尚未销号**（销号须与 G4 共同确认，见下）；**分类字段保留原值 `OBSERVABLE DIFFERENCE` 不改写**（订正以注记形式留痕）。
> ⇒ 🔴 **给 G4 派单 #6 的回执**：其「两步方案」的第 1 步（把 `UserEntity.status` 改成能表达 NULL）**不能执行** ⇒ 那会破坏本条第 ③ 层已用双侧活体对拍证实的等价性；其裁决点「Go 侧读到 NULL status 会扫描报错」**事实错误**（第 ① ② 层）⇒ **#6 应当按「行为层零差异」重新定性，而不是走两步方案**。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡（2026-10-27 订正：定性为「DDL 层差异 · 行为层零差异」⇒ 建议保持 NOT NULL）** ⇒ **现行态 = 🟡 维持**（**可空性差异仍真实存在，但方向与「放开可空」相反**）。
> - **RAG 侧**：`service/users/users-common/src/main/resources/db/01_create_user.sql:39` = `status      BIGINT NOT NULL DEFAULT 0,` ⇒ **NOT NULL 在位**（`:13` 为同句的注释头行）。
> - **Go 侧**：`service/user/user/rpc/internal/model/data_model.go:11` = `Status     int64             `gorm:"column:status;default:0"`` ⇒ **无 `not null` tag** ⇒ GORM v2 对标量字段**默认不加 NOT NULL** ⇒ Go 侧 `status` 可空（`bigint` / `is_nullable=YES`）。
> - ⇒ **两侧可空性差异仍存在**；但 Go 读 NULL 得 0、RAG 因 NOT NULL 读不到 NULL 也得 0 ⇒ **可观察结果相同**（本条原判的「行为层零差异」成立）⇒ **维持 🟡、维持「建议保持 NOT NULL」**。
> - ⚠️ 本轮**未复跑真 PG 对拍** ⇒ 「可空性差异」按 §0 读作**在该证据强度下一致**（静态 DDL + Go model tag 两侧在场）；「行为层零差异」这一层**沿用原结论，本轮未重新取证**。
> - ⚠️ **诚实边界**：本条**尚未销号**，与 G4 的共同确认项**仍开着**。核对日期 2026-10-30。
> 🟢 **2026-10-30 补记（本轮真 PG 活体实测，替换上一段「未复跑」的诚实边界）**：本轮**已复跑** `python3 tools/difftest/check-ddl-static.py --domain users --check-source --indexes` ⇒ **退出码 1**（实质差异 16 处）。其中与本条直接相关的一行：**补充对照 · NOT NULL（可空性）：差异 1 处 ⇒ `users_user.status` go=(nullable=YES) / rag=(nullable=NO)** ⇒ **本条的可空性差异已被真 PG 活体实证**（不再是「静态 DDL + Go tag 推断」）。同一次实测还给出：**类型/长度差异 0 处**（35 列键集合一致、35 列逐字相同）· **DEFAULT 差异 8 处**（8 个 `create_time`/`update_time` 的 `DEFAULT CURRENT_TIMESTAMP`）· **索引实质差异 7 处**（Go 独有 3 条非唯一索引 + RAG 独有 4 条非唯一索引 ⇒ **性能面非行为面**）· **约束集合两侧完全一致** · **触发器两侧各 0 个 ⇒ 该维度「什么都没比」，不等于「已对齐」**。⚠️ 「**行为层零差异**」这一层仍**未复跑**（需真 PG 插 NULL 行的对照实验）⇒ 该层**沿用原结论**。核对日期 2026-10-30。

### DRIFT-USERRPC-V-003 DDL：`extra_info` 列类型 **text ↔ jsonb** —— ✅ **已修（2026-10-23 23:4x，commit `ec33159`）**

- **Category**: Schema / Serialization
- **Go 侧成因（本轮独立复核，不是猜的）**：
  `data_model.go:13` `ExtraInfo map[string]string gorm:"column:extra_info;serializer:json"`
  → `gorm@v1.31.1/schema/field.go:195-199`：`serializer` 命中时 **强制 `field.DataType = String`**
  → `gorm.io/driver/postgres@v1.6.0/postgres.go:245-250`：`schema.String` 且 `Size == 0` ⇒ 返回 **`text`**。
  **⇒ Go 侧 `users.extra_info` 就是 `text`，不是 jsonb。**
  ⚠️ 上面这条推导链**每一环都对，但已不再是唯一依据**：本轮另取**建表语句级实证**
  （`go run -overlay` 虚拟注入探针 → 调真源 `model.InitDB` → PG `log_statement=all` 抓原文），
  见本文件 §4 的补记。**「推导对」与「有实证」是两回事，后者才关得住争议。**
- **RAG（修复前）**：`01_create_user.sql:41` `extra_info JSONB`。
- **Difference**：jsonb 会归一化（key 按「长度 + 字节序」重排、重复 key 丢弃、空白删除），
  text 原样保留。
  **实测（本轮 PG 上直接跑，2026-10-23 23:46）**：
  ```
  输入  {"b":"2","a":"1","bb":"3"}
  text  ⇒ {"b":"2","a":"1","bb":"3"}            原样
  jsonb ⇒ {"a": "1", "b": "2", "bb": "3"}      键重排 + 冒号后补空格
  ```
- **Observable Impact**：**wire 层无影响**（两侧都解析成 `map<string,string>` 再发 proto map）。
  DB 层可见：任何直接对 `extra_info` 做文本匹配的跨组件查询会因 key 序不同而失效。
- **Classification**: **OBSERVABLE DIFFERENCE**（仅 DB 层）
- **Evidence**: `[GO]` 上述三处**消费点**行号 **+ 建表语句级实证**；`[RAG]` `01_create_user.sql:41`；
  `[PG]` 见下方「修复与守门」。
- **Why It Matters**：`AGENTS.md` §0 第 2 条说「10 处列类型差异从未评估」并特别点名
  「⚠️ `users_user.extra_info` 的 `text→jsonb` **必须单独取证** —— 不能照『RAG 用了 jsonb 所以 Go 也该用』来猜」。
  **本条就是那份取证**，结论是：**Go 侧确实是 `text`。**

#### 修复（`ec33159`）

| 层 | 改法 |
|---|---|
| DDL | `extra_info JSONB` ⇒ `TEXT` + 幂等自愈 `ALTER … TYPE text USING extra_info::text` |
| handler | `JsonbTypeHandler` ⇒ **`JsonTextTypeHandler`**，`setObject(…,Types.OTHER)` ⇒ `setString` |
| CAST ×2 | `UserMapper#updateUserById` 与 `UserAvatarHistoryMapper#updateUserExtraInfo` 去掉 `CAST(… AS jsonb)` |

**为什么连类名一起改**：该 handler 存在的**全部理由**就是「jsonb 拒收 varchar 绑参」，
理由消失后类名若仍叫 `Jsonb…`，就是在给下一个人讲一个已经不成立的故事
（与 `TokenBlacklistedException` 声称 Go 有 `PERMISSION_DENIED` 同型病灶，见 §3.4.1 D-2 先例）。

**⚠️ 两处 CAST 是本次最容易漏的**：它们在 text 列上**能跑通**
（`jsonb→text` 是 assignment cast），语句不报错、端到端用例**照样全绿**，
但 PG 会顺手规范化一次 ⇒ **「能跑但语义错」，比编译不过更难发现**。

#### 🔴 2026-10-26 补记：**代码侧（写路径参数绑定）的跨组件那一格 —— 主干上已成立**

> **触发**：`users/admin` 域 owner 来信，说「主干上 `admin-rpc` 的 `extra_info` 仍按 jsonb 绑定（`JsonbTypeHandler` → `ps.setObject(…, Types.OTHER)`）⇒ DDL 是 text、绑定是 jsonb ⇒ 代码侧那一格不成立 ⇒ 建议我域加限定」。
> ⇒ **本域独立核实后结论相反**：**主干上那一格已经成立**，且**不是** global 的 B10 带来的。

| 事实 | 证据（2026-10-26 在主干上实测） |
|---|---|
| `admin-rpc` 的 `extra_info` 已是 **`JsonTextTypeHandler`** | `service/users/admin/admin-rpc/.../domain/UserEntity.java:141` `@TableField(value = "extra_info", typeHandler = …JsonTextTypeHandler.class)` |
| 该 handler 走 **`ps.setString`**（text 的原生绑定） | `admin-rpc/.../mapper/JsonTextTypeHandler.java:86` `ps.setString(i, toJson(parameter))`（`:22` 的 javadoc 专门写了「不能用 jsonb 味的 `setObject(…, Types.OTHER)`」） |
| `admin-rpc` 里 **`JsonbTypeHandler.java` 已不存在** | 该目录下只有 `JsonTextTypeHandler.java` ⇒ 全主干 grep `JsonbTypeHandler` 在 admin-rpc / user-rpc **零命中**（like-rpc / comment-rpc 里的同名类是**它们自己域的 jsonb 列**，与本条无关） |
| 落地提交 = **`users/admin` 域自己的 `c10f639`** | `fix(users/admin): DDL 列类型对齐 Go + ARPC-002 wire 对齐 + 签字档补录`；`git merge-base --is-ancestor c10f639 do-my-own-gh` ⇒ **在主干** ✅ |
| global 的 B10 `ff80214` | 在 **`align/global`**（`git merge-base --is-ancestor ff80214 do-my-own-gh` ⇒ **不在主干** ❌）；它对 admin-rpc 做的正是**同一件事**（typeHandler ⇒ `JsonTextTypeHandler` + 删 `JsonbTypeHandler.java`）⇒ ⇒ **对主干而言它已是冗余** |

⇒ **结论**：**「两侧都按 text 写」在主干上成立** —— DDL 侧 `ec33159` + 代码侧（`user-rpc` 与 `admin-rpc` 各一）均已是 text 绑定。
⇒ ⇒ **本条不需要加「待 global B10 合回」的限定**（加了反而是把一个**不成立**的 caveat 写进台账）。
⇒ ⚠️ **合回安全性**（`align/global` 将来合回时）：admin 域已用 `git merge-tree --write-tree` 干跑 `3cb52ad` + `0516774` ⇒ **无冲突**，且合并树里引用已同步 ⇒ **不会编译失败**。
⇒ 💡 **方法论（跨域回执纪律）**：「主干上不成立」这类断言**必须在主干上实测**才能写进台账 ——
　本次若直接照抄，会把一条**已经闭环**的条目重新标成「未闭环」⇒ **比漏记更坏**（它会让人去重做一遍已完成的修复）。
　⇒ 对照 **PIT-USERS-SHARED-020**（收方给的清单要逐个核实）与 global 侧的 **PIT-GLOBAL-045**（跨域回执要自己核）。

#### 守门网（3 条，全部经变异验证）

| # | 守门 | 层 | 变异 → 结果 |
|---|---|---|---|
| 1 | `extraInfoBytesAreStoredVerbatim`：断言落库字节**逐字节**等于 Jackson 输出 | 真 PG | DDL 回退 jsonb ⇒ 红 |
| 2 | `updateUserById_extraInfoHasNoJsonbCast`：SQL 片段不得含 CAST/jsonb | mock | CAST 加回 ⇒ 红 |
| 3 | `updateUserExtraInfoSqlHasNoJsonbCast`：`@Update` 注解反射 | 零容器 | CAST 加回 ⇒ 红 |

**第 1 条的判别键组**：`LinkedHashMap` 插入序 `c, a, bb`；
PG jsonb 会重排成 `a, c, bb`（键长 → 字节序）⇒ 两列类型可分辨，已实测确认。
⚠️ **第 3 条是补自查缺口**：头像双写那条 `@Update` 路径原本**无任何断言**，
CAST 加回去端到端全绿也抓不到 ⇒ 只验第 1 条不够。

**反转 1 条**：`schemaInitCreatedTextExtraInfoColumn`（原名 `schemaInitCreatedJsonbColumns`，
断言「必须是 jsonb」）。⚠️ 原断言的**目的没变、服务对象变了**：它原本是防「退化成 text 导致 jsonb 用例假绿」，
现在列就是 text ⇒ 新的风险是**反方向**的（改回 jsonb 时下面用例照样全绿），
拦住它的是第 1 条而非类型断言。**两条缺一不可。**

**跨域**：`admin_admin.extra_info` 已由 admin 域改为 `text`（`DRIFT-ARPC-004①`，`c10f639`/`d7b2b45`）。
本条修复后**两张表列类型一致，且都与 Go 一致** ⇒ §5b 记的那个不对称状态已消除。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（2026-10-23 23:4x，commit `ec33159`）** ⇒ **现行态 = ✅ 维持已修**（**列类型两侧现在都是 `text`**）。
> - **RAG 侧**：`service/users/users-common/src/main/resources/db/01_create_user.sql:41` = `extra_info  TEXT,` ⇒ **已是 text**（`:15` 为注释头行）· `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:115` = `@TableField(value = "extra_info", typeHandler = com.rag.users.user.rpc.mapper.JsonTextTypeHandler.class)` ⇒ **写路径绑定也是 text 型 handler**。
> - **Go 侧**：`service/user/user/rpc/internal/model/data_model.go:13` = `ExtraInfo  map[string]string `gorm:"column:extra_info;serializer:json"`` ⇒ GORM v2 的 `serializer:json` 字段落地为 **text** ⇒ **两侧同型**。
> - ⇒ **`jsonb` 那一侧的差异已消失** ⇒ 维持 ✅ 已修。核对日期 2026-10-30。

### DRIFT-USERRPC-V-004 DDL：4 个时间列 **timestamptz ↔ timestamp**，连带 `create_time` 的时区标签 —— ✅ **已修（列类型与可观察半边）**

- **Category**: Schema / Serialization
- **Go**：`schema.Time` ⇒ `postgres.go:252-255` 返回 **`timestamptz`**；DSN 钉 `TimeZone=Asia/Shanghai`（`db.go:21`）。
- **RAG**：`01_create_user.sql:42-43` / `02_...:30-31` 是 **`TIMESTAMP`**（无时区）；
  JDBC URL **不带** `TimeZone` 参数（`application.yaml:13`）。
- **Difference（两层）**：
  1. **列类型**：timestamptz 存绝对时刻、按会话时区呈现；timestamp 存墙钟字面量。
  2. **渲染**：`AvatarDomainService.fromModel:74` 把读到的 `LocalDateTime` 一律 `atOffset(ZoneOffset.UTC)` ⇒
     `create_time` **恒以 `Z` 结尾**；Go 的 `Format(time.RFC3339)`（`avatarhelpers.go:18`）在会话时区
     非 UTC 时**带偏移**。
- **Observable Impact**：`AvatarHistoryItem.create_time` 是**纯客户端可见字段** ⇒ 同一份数据两侧渲染不同。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Evidence**: `[GO]` `postgres.go:252-255` + `db.go:21` + `avatarhelpers.go:18`；
  `[RAG]` `application.yaml:13` + `AvatarDomainService.java:47-49,74`。
  ⚠️ `AvatarDomainService` 的 javadoc **自陈**这是未结项（「时区残留问题（UNRESOLVED，见验证报告 U-2）」）
  ⇒ **本条是把它从代码注释提升为台账条目**，不是新发现。
- **同构先例**：`DRIFT-MSG-010` 是完全同一条（message 域），可接受性同样 `UNKNOWN`。
- **可接受性**: **UNKNOWN** —— 见 `UNRESOLVED-USERRPC-002`（JVM / PG 会话时区未实测）。
  ⚠️ **2026-10-24 更新**：`UNRESOLVED-USERRPC-002` 已闭环 ⇒ **机制已定**：
  偏移量由**进程本地时区**（Go 的 `time.Local`）决定，**PG 会话时区不参与**（pgx 走二进制解码）；
  源码依据 `pgtype/timestamptz.go:133-137`（`ScanLocation` 默认 nil）→ `:270-276` 回落到 `time.Local`。
  ⇒ 「UNKNOWN」是**过期状态**；列类型本身也已改 `timestamptz`（V-004 已修）。

#### FIX（2026-10-23，部分）——`users_user` 半边已修，**`users_user_avatar_history` 半边仍 OPEN**

| 半边 | 状态 | 说明 |
|---|---|---|
| `users_user.create_time/update_time`（`01_create_user.sql:42-43`） | ✅ **已修** | 列改 `TIMESTAMPTZ`；两个实体 4 字段改 `OffsetDateTime`。守门 `UserRpcUsersUserTimestamptzParityTest`（6 条，真 PG），变异测试已证伪。admin-rpc **84/0**、user-rpc **102/0** |
| `users_user_avatar_history.create_time/update_time`（`02_create_user_avatar_history.sql:30-31`） | ✅ **已修**（第六轮复核更正） | 这才是本条 **OBSERVABLE** 的那一半（`CASE-27`）。DDL 现为 `TIMESTAMPTZ`（`02_...:30-31` + 幂等 ALTER），守门 `UserRpcAvatarHistoryTimestamptzParityTest`（**9 条**，真 PG）。⚠️ 本行此前写「未修」，是**过期状态**（第六轮复核时才发现 DDL 与守门早已到位）。**残留**：DEFAULT 差异（`CURRENT_TIMESTAMP` vs Go 无）仍属 V-011，未修<br>⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：本行原第 4 格（**原值保留**）写「`UserAvatarHistoryEntity:47,50` 仍 `LocalDateTime`，`AvatarDomainService:74` 仍 `atOffset(UTC)` ⇒ wire 恒 `Z`；叠加 JDBC URL 未钉 `TimeZone`（Go `db.go:21` 钉 `Asia/Shanghai`）」⇒ **那是【修复前】的形态，已全部作废**（实测 2026-10-29 逐行核过）：① 实体**早已**是 `OffsetDateTime` —— `UserAvatarHistoryEntity.java:76` `private OffsetDateTime createTime;` · `:85` `private OffsetDateTime updateTime;`（`:47` 的 javadoc 现**明写**「类型为 `OffsetDateTime` 而非 `LocalDateTime`」）⇒ 原句「仍 `LocalDateTime`」**不成立**；② 渲染**早已**落在**进程本地时区** —— `AvatarDomainService.java:115` `ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()` ⇒ `:117` `RFC3339.format(local)` ⇒ **不再有 `atOffset(UTC)`**（`:74` 今是 javadoc；旧写法只作为「原实现是错的」被记在 `:57` 的注释里）⇒ 原句「wire 恒 `Z`」**不成立**（`systemDefault()` 即 Go 的 `time.Local` 语义）；③ DDL `02_create_user_avatar_history.sql:30-31` 现为 `TIMESTAMPTZ` ✔（与本行前半格一致）。⇒ **本条状态维持 ✅ 已修**（它本来就判对了）；**只订正这一格陈旧描述 + 把它并回「说明」列**（原第 4 格会因列数不符而被渲染丢弃 ⇒ 并回后不再丢失） |

⚠️ **本条原标题「4 个时间列」口径已更正**：那 4 处是 **两份 Go model 各 2 个字段**
（`user/rpc/.../data_model.go:14-15` 与 `admin/rpc/.../data_model.go:12-13`），
**指向同一张表 `users_user`**，不是 4 张表 4 列。

⚠️ **原台账「改动三部分」的第 3 部分（3 处格式化）经复核归因错误**，详见
[`_shared.md` 的同名小节](../_shared.md)：`AvatarDomainService` 格式化的是**头像历史表**的实体，
`UserApiSupport:69` 只是 proto 字符串透传、根本不是格式化点。
⇒ **`users_user` 的时间值不进任何 wire 字段**，本轮改动**无可观察差异**，
是纯 schema / 类型层对齐。**别把「已修」读成「wire 已对齐」。**

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判 **✅ 已修（列类型与可观察半边）** ⇒ **现行态 = ✅ 已修（维持）**；
> ⚠️ **但上方 FIX 表 `users_user_avatar_history` 那一行的「残留」格是陈旧快照 —— 已被 2026-10-23 的同批修复作废**。
> - **实测（2026-10-29 逐行核过）**：
>   - `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserAvatarHistoryEntity.java:76` / `:85`
>     字段类型**已是 `OffsetDateTime`**（`private OffsetDateTime createTime;` / `private OffsetDateTime updateTime;`）
>     ⇒ 残留格写的「**仍 `LocalDateTime`**」**不成立**；
>   - `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/AvatarDomainService.java:115-117`
>     渲染点是 `OffsetDateTime local = ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();`
>     ⇒ 残留格写的「**仍 `atOffset(UTC)` ⇒ wire 恒 `Z`**」**不成立**（`atOffset(UTC)` 已在同一文件 `:57` 的注释里被标为「原实现…是错的」）。
> - **指针订正（保留原值）**：本条「Difference」栏引的 `AvatarDomainService.fromModel:74` ⇒ **现行渲染点在 `:115-117`**（`:74` 现为 javadoc）。
> - **仍然成立的残留只有一条**：**DEFAULT 差异**（`CURRENT_TIMESTAMP` vs Go 无）⇒ 仍属 **V-011** / `SD-2026-10-25-CAND-16`。
> - **不改判的理由**：列类型（`TIMESTAMPTZ`）与「渲染落到进程本地时区」这两个**被描述的行为**都还在
>   （`01_create_user.sql:42-43` 与 `02_create_user_avatar_history.sql:30-31` 均为 `TIMESTAMPTZ`）⇒ 状态维持 ✅。

### DRIFT-USERRPC-V-005 `UpdateUser` 对**显式空 map** 的分支不同 —— ✅ **判为「不成立」予以关闭**（protobuf3 的空 map 在 wire 上不可表达）

- **Category**: Input Interpretation
- **Go**：`updateuserlogic.go:58-60` `if in.ExtraInfo != nil { toUpdate.ExtraInfo = in.ExtraInfo }`；
  GORM `Updates(struct)` 对 **非 nil 的空 map** 判 `IsZero() == false` ⇒ **进 SET，写 `{}`**。
- **RAG**：`UserDomainService.java:451` `if (req.getExtraInfoCount() > 0)` ⇒ 空 map **不进 patch**；
  `UserMapper.java:112` `getExtraInfo() != null` 也为 false ⇒ **不写**。
- **Difference**：请求携带「显式存在但为空」的 `extra_info` 且其余字段也为空时，
  Go 发一条只改 `extra_info` + `update_time` 的 UPDATE；**RAG 一条 UPDATE 都不发**（`any==false → return 0`）。
  ⇒ **`update_time` 是否推进**在两侧不同（DB 层可见；wire 不可见，响应都是重读后的实体）。
- **Classification**: **OBSERVABLE DIFFERENCE**（窄）
- **Evidence**: `[GO]` `updateuserlogic.go:58-60` + `model/user.go:45`；
  `[RAG]` `UserDomainService.java:451` + `UserMapper.java:112,125`。证据强度：**静态源码**。
- **⚠️ 可达性未证**（`UNRESOLVED-USERRPC-003`）：protobuf 编码器是否为「显式空 map」产出字段 tag，
  两侧生成代码行为可能不同。本轮**未构造 wire 级输入**验证。
  ⚠️ **2026-10-24 更新**：`UNRESOLVED-USERRPC-003` 已闭环 ⇒ 答案是
  「**protobuf3 的空 map 在 wire 上不可表达**」（零条目 ⇒ 字段缺席 ⇒ 解出来是 nil）
  ⇒ ⇒ **V-005 判为「不成立」予以关闭**。本行「可达性未证」是**过期状态**。
- **Risk**: LOW
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 判为「不成立」予以关闭** ⇒ **现行态 = ✅ 维持关闭**（**关闭理由在本轮被重新取证，仍然成立**）。
> - **Go 侧**：`service/user/user/rpc/internal/logic/updateuserlogic.go:58-59` = `if in.ExtraInfo != nil {` / `toUpdate.ExtraInfo = in.ExtraInfo` ⇒ **分支条件是「指针非 nil」** ⇒ protobuf3 的**空 map 在 wire 上不可表达**（零条目 ⇒ 字段缺席 ⇒ 解出来必为 nil）⇒ Go 这条分支**不可达**。
> - **RAG 侧**：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:371` = `if (req.getExtraInfoCount() > 0) {` ⇒ **用 count 而非 null 判断** ⇒ 与 Go 同样不可达。
> - ⇒ **两侧都到不了「显式空 map」这一格** ⇒ **本条不成立**、维持关闭。核对日期 2026-10-30。

### DRIFT-USERRPC-V-006 `users_user.id` / `users_user_avatar_history.id` 的取值来源不同 —— ✅ **已关闭（第四轮，真 PG 实证；永不进 wire）**

- **Category**: State / Schema
- **Go**：`Id uint64 gorm:"primaryKey"` ⇒ GORM 视为主键自增，`Create` 时省略该列，
  值由 `bigserial` 序列产生（1,2,3…）。
- **RAG**：`UserEntity.java:33` / `UserAvatarHistoryEntity.java:25` 均 `@TableId(type = IdType.ASSIGN_ID)`
  ⇒ MP 在**客户端侧**生成雪花 ID；`users_user` 走 `BaseMapper.insert` ⇒ 序列永不被使用。
- **Difference**：两侧 `id` 列的取值分布完全不同（自增小整数 vs 雪花大整数）。
- **Observable Impact**：**wire 层零影响** —— 任何 proto message 都不含该列
  （`AvatarHistoryItem.id` 取的是字符串 `history_id`，见 `avatarhelpers.go:22`）。仅 DB 层可见。
- **Classification**: **OBSERVABLE DIFFERENCE**（仅 DB 层）
- **Evidence**: `[GO]` `data_model.go:6,23`；`[RAG]` `UserEntity.java:33`。证据强度：**静态源码**。
- **✅ 已解（2026-10-23 第四轮 = V-006 关闭）**：`users_user_avatar_history` 走手写 `@Insert` +
  `@Options(useGeneratedKeys=true)`（`UserAvatarHistoryMapper.java:88-93`）时，
  `IdType.ASSIGN_ID` 与 `Jdbc3KeyGenerator` **谁最终胜出** ⇒ **V-006 已关闭**
  （真 PG：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；**永不进 wire**）⇒ `UNRESOLVED-USERRPC-004①` 已闭环。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判 **✅ 已关闭（第四轮，真 PG 实证；永不进 wire）** ⇒ **现行态 = ✅ 已关闭（维持）**；
> **两处证据指针已漂移（结论不变，指针需订正）**。
> - **被描述的行为是否还在**：**仍在** ——
>   `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:34`
>   `@TableId(type = IdType.ASSIGN_ID)`；
>   `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserAvatarHistoryEntity.java:25`
>   `@TableId(type = IdType.ASSIGN_ID)`（**与原指针一致，未漂**）；
>   `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:111` `@Insert(...)`
>   ＋ **`:115`** `@Options(useGeneratedKeys = true, keyProperty = "id")` ⇒ **「手写 @Insert + useGeneratedKeys」这一形态仍在**。
> - **指针订正（保留原值）**：① `UserEntity.java:33` ⇒ **现行 `:34`**（`:33` 现为一行 javadoc）；
>   ② `UserAvatarHistoryMapper.java:88-93` ⇒ **现行 `:111-115`**（`:88-93` 现为 V-009 的 javadoc 段）。
> - **Go 侧未变**：`service/user/user/rpc/internal/model/data_model.go:6` `Id uint64 \`gorm:"primaryKey"\``（**未漂**）
>   ⇒ 「自增 vs 雪花」这半边描述仍准确。

### DRIFT-USERRPC-V-007（跨组件 `service/common`）超时路径的 gRPC **status description** 不同 —— ✅ **已修（2026-10-26，由 `global` 域修完）**

> ⚠️ **本条曾是「仍成立，但不在本组件判定权内」** —— 该归属判断是对的（判定权确实在
> `service/common` 组件），而 **`global` 域已把 `service/common` 修完** ⇒ 本条销账。
> **修前状态（保留供追溯）**：两侧 `code` 相同（`DEADLINE_EXCEEDED`），
> 但 **description 逐字不同** ⇒ 客户端收到的 `grpc-message` trailer 不同。

- **Category**: Error Identity
- **Go**：`service/common/observability/rpc.go:69`
  `status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = **`context deadline exceeded`**。
- **RAG（修前）**：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
  的超时关闭分支 ⇒ description = **`rpc timeout after 2000ms`**。
  ⚠️ **行号引用已改为符号名** —— `global` 域本轮改动使该文件行号漂移，
  行号快照会变成新的过期事实（`global` 域明确要求改符号名）。
- **RAG（修后）**：同一处改为常量 `GO_DEADLINE_EXCEEDED_DESCRIPTION`
  = **`"context deadline exceeded"`** ⇒ 与 Go **逐字相同**。
  落点文件：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
  （`global` 域持 `service/common` 单写权 ⇒ 本域**只回写台账，不动代码**）。
- **Difference**：`code` 相同（`DEADLINE_EXCEEDED`），**description 逐字不同**。
- **Observable Impact**：`grpc-status-details-bin` / `grpc-message` trailer 不同。
  user-api 侧**只按 code 分支**，故不进 HTTP body（与 N-2 同款影响面）。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Evidence**: `[GO]` `rpc.go:69`；`[RAG]` `RpcInterceptor` 的超时描述常量（见上）。
  证据强度：修前 **静态源码**；修后 **双侧活体**（`global` 域跑的，**本域未复跑**）。
- **归属**：`service/common` 组件，不属 user-rpc 自身。**判定权在 common 组件的台账**，
  此处只登记 user-rpc 作为消费方的事实，避免同一问题被两个组件各判一次。
- **✅ 销账依据（2026-10-26，`global` 域交付）**：
  | 项 | 内容 |
  |---|---|
  | 修法 | `RpcInterceptor` 新增常量 `GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded"`；**三处**旧串（超时 `TimeoutException` 文案、`call.close` 的 `withDescription`、另一处 `Status.DEADLINE_EXCEEDED.withDescription`）+ 遥测 `markTimeout` 的消息一并改掉 |
  | 守门 | `RpcInterceptorTimeoutDescriptionGateTest.java`（真 Netty server + 真 channel + 真客户端 + 真 OTel SDK ⇒ 断言**网络上真正传过来的字节**）<br>✅ **该守门类已随 `4f839f9` 合入主干**（2026-10-26 23:1x）⇒ **现在主干上直接可查**<br>⚠️ **历史留痕**：合回之前，它「在 `global` 的提交里、主干上查不到」（路径悬空**是预期状态**，不是台账事实错误）⇒ 那段时间机器复核报「该文件缺失」是**分支差异**，**不是**「守门没写」<br>⚠️ **路径刻意写成短形式（只写文件名）** —— global 第三十七轮提醒：引述一个**已知是错的路径**时，若写成**活路径形态**（带 `service/` `tools/` 前缀）会被工具**报出来**（噪声可忍），若与**叙述词同行**又会被**吃掉**（**假阴性**，更危险）⇒ 短形式两头都躲：既不被当路径引用、也不被误当「已点名类」以外的噪声 |
  | 探针 | `tools/semantic-probe/rpc-timeout/`（`go/` + `java/` + `run-rpc-timeout-parity.sh`）—— Go 半边 `replace` 引用**冻结仓真源**、RAG 半边跑**生产类** ⇒ `timeout_wire` 两侧逐字节一致 |
  | 变异 | M1 改回旧串 ⇒ 守门 2/2 红 + 探针 EXIT=1；M2 只改遥测段 ⇒ 只红遥测那条、wire 那条仍绿 |
  | 本域已核实 | ✅ **代码 diff 属实**（三处替换 + 常量）、✅ 守门类与探针目录**确实存在**、✅ 落点在 `service/common`（`global` 单写权域）<br>⚠️ **未复跑**守门/探针（跑在 `global` 的工作区，避免干扰其构建）|
  | **提交（SHA）** | **E30 = `49808ad` 单一提交**（实现 + 守门类 + 探针**全在这一个 commit 内**）—— 分支 **`align/global`**，**已推 `origin` + `upstream`**（`git branch -r --contains` 实测两个远端分支都含）|
  | ✅ **合并状态（2026-10-26 23:1x 核对）** | ✅ **已合回主干** —— 合并提交 **`4f839f9`**（`merge(global): align/global → do-my-own-gh`（第五十轮 · 双向合流 · 零冲突））⇒ `git merge-base --is-ancestor 85c4588 do-my-own-gh` ⇒ **YES**；`ff80214` / `0516774` 亦 **YES**<br>⇒ **主干实测**：`service/common/.../observability/RpcInterceptor.java:109` ⇒ `private static final String GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded";` ⇒ **E30 已在主干生效**<br>⇒ 🔴 **本条销账证据强度升级：本域自己复跑了双侧活体**（2026-10-26 23:31，`tools/difftest/exp-difftest-redis-neverup-rpc.sh`，`REDIS_MODE=hanging` ⇒ 命令超时路径）：<br>　Go = `DeadlineExceeded` / **`context deadline exceeded`** / **2014·2001·2002ms**<br>　RAG = `DeadlineExceeded` / **`context deadline exceeded`** / **2113·2008·2003ms**<br>⇒ ⇒ **两侧 code 与 description 逐字一致** ⇒ **V-007 在主干上确认闭环**（不再依赖「以 SHA 为准」的推断）<br>⚠️ **历史留痕**：本日**早间**拍板为「暂不合回」（⇒ 三域统一措辞「待合回不是忘了合」）⇒ 同日 **23:1x 实际合回** ⇒ **上面那段措辞已过期**；§7 守门行里的「合回前主干上查不到」同理**已过期**（该守门类现已在主干上）<br>⚠️ **当年那条陷阱仍然成立（作为方法论）**：「合回之前主仓代码是旧串 ⇒ 基于主仓的验证看不到修复」—— 本轮正是靠**等合回后再复跑**才把 V-007 从「静态 + 他域活体」升级为「**本域双侧活体**」 |
  | ⚠️ **本条记录的更正（2026-10-26 自查）** | 本行初稿写的是「`49808ad`（实现）+ **`22a9111`**（台账/守门/探针收口）」—— ❌ **后半句不成立**。<br>**独立核实**：`22a9111` 的提交信息是「docs(global): 订正 E28 —— favorite 的两斜杠 owner 已修」，`git show --stat` 显示它**只改了 1 个文件、1 行**（`docs/DRIFT/global/_README.md`）⇒ **与 E30 无关**。<br>**真正的守门类与探针提交**：`git log --all --diff-filter=A` 查新增路径 ⇒ 守门类 `RpcInterceptorTimeoutDescriptionGateTest.java` 与探针 `rpc-timeout/` **均由 `49808ad` 新增**（该 commit 共 20 文件 / +1200 行）⇒ **E30 只有一个提交，没有第二个收口提交**。<br>⇒ 教训见 `docs/Pitfalls/users/_shared.md` 的 **PIT-USERS-SHARED-020**（收方给的 SHA 清单必须逐个 `show --stat` 核实主题，不能整包照抄）|
- **🔴 边界（`global` 域明确要求一并读）**：修完后剩下的 **code 名差异**是一条**已登记的表述偏离**，**本轮不动**：
  Go（grpc-go）`codes.Code.String()` = CamelCase `DeadlineExceeded`；
  RAG（grpc-java）`Code.name()` = `DEADLINE_EXCEEDED`
  ⇒ 已登记在 `docs/Intentional_Deviations/common/_signed-deviations.md` 的 **C-1**，写明「**不做臆造映射**」。
  ⇒ 守门**刻意把 C-1 的形态钉住**（断言 `code = DEADLINE_EXCEEDED`）⇒ 后人不会把它当新漂移顺手改掉。
  ⇒ ⚠️ **请勿**为了让 code 名也一致而去改 grpc-java 的 code 命名。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判 **✅ 已修（2026-10-26，由 `global` 域修完）** ⇒ **现行态 = ✅ 已修（维持）**；
> **一处证据指针已漂移（结论不变）**。
> - **被描述的行为是否还在**：**仍在** ——
>   `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:168`
>   `private static final String GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded";`
>   ⇒ 旧串已被**三处**消费点统一改用该常量（同文件 `:330` / `:338` / `:594` / `:601`）。
> - **指针订正（保留原值）**：本条销账依据表里写的 `service/common/.../observability/RpcInterceptor.java:109`
>   ⇒ **现行 `:168`**（`:109` 现已是别处代码）。
> - **Go 侧未变**：`service/common/observability/rpc.go:69`
>   `err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = `context deadline exceeded`（**未漂**）。
> - ⚠️ **另注（与本条无关、但同一份文件的符号名已变）**：本文件下方「共享层改动影响面记账 · E38 第①层」表里
>   写的 `effectiveTimeoutMs` / `clientDeadlineRemainingMs()` **在当前代码里已不存在** ⇒ 见该表下方的订正块。

### 🆕 共享层改动影响面记账 —— `global` 域 **E38 第①层**（`d531abd`）：超时期限继承客户端 deadline

> §5.3：共享层（`service/common`）改动 ⇒ **逐个消费方记账**。本节是 **user-rpc 的记账**。

| 项 | 事实 | 出处 |
|---|---|---|
| Go 侧（**客户端**） | `RpcClientConf.Timeout int64 json:",default=2000"` ⇒ **zrpc 客户端默认带 2000ms deadline** | `go-zero@v1.10.2/zrpc/config.go:31`（`RpcClientConf` 结构体，line 24 起）+ `go-zero@v1.10.2/zrpc/client.go:71`（`opts = append(opts, WithTimeout(...))`）|
| Go 侧（**服务端**） | `RpcServerConf.Timeout int64 json:",default=2000"`（注释原文：`setting 0 means no timeout`）⇒ **user-rpc 服务端预算 2000ms** | `go-zero@v1.10.2/zrpc/config.go:46` |
| Go 侧是否覆盖 | `service/user/user/rpc/etc/user.yaml` **通篇没有 `Timeout`** ⇒ 客户端与服务端**双双走默认 2000ms**；`service/user/user/api/etc/usercenter.yaml` 的 `UserRpc:` 只配了 `Etcd` ⇒ 客户端 Timeout 也走默认 | `service/user/user/rpc/etc/user.yaml`（全文实测）· `service/user/user/api/etc/usercenter.yaml:37-41` |
| RAG 侧（**服务端**） | `observability.rpc-timeout: 2s` ⇒ **user-rpc 服务端预算 2000ms** | `service/users/user/user-rpc/src/main/resources/application.yaml:155-156`（注释自述对齐 `go-zero@v1.10.2/zrpc/config.go:46` 的 2000ms）|
| RAG 侧（**客户端**） | **不带 call deadline** ⇒ 有效期限只剩服务端预算 | ✅ **字节码级证据**（非「配置里没写」）：devh `grpc-client-spring-boot-starter` **3.1.0.RELEASE** 的 `net.devh.boot.grpc.client.config.GrpcChannelProperties` **字段清单里根本没有 `deadline`**（只有 `immediateConnectTimeout` = 连接超时，非调用 deadline）⇒ 该 starter **无处可配 call deadline** ⇒ 不配即无<br>另：`service/users/user/user-api/src/main/resources/application.yml:90-97` 的 `grpc.client.user-rpc` 也确实没有 deadline 项 |
| E38 修法 | `deadlineEpochMs` 由 `now + timeout` 改为 **min(服务端预算, 客户端 deadline 剩余)**；超时调度器延时同用 `effectiveTimeoutMs` | `align/global` commit **`d531abd`**，落点 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`（已读 diff 原文核实：`effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）|

⇒ **本组件结论：无变化、无可观察差异。**
- Go 侧有效期限 = min(客户端 2000ms, 服务端 2000ms) = **2000ms**
- RAG 侧有效期限 = min(**无客户端 deadline** ⇒ 服务端 2000ms) = **2000ms**（E38 修前也是 2000ms）
⇒ 两侧**同形** ⇒ `DRIFT-USERRPC-V-007` 之外**不新增任何条目**，§10 的计数**不变**。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判「**本组件结论：无变化、无可观察差异**」⇒ **现行态 = 维持（结论不变）**；
> ⚠️ **但上表「E38 修法」一行的符号名在当前代码里已全部不存在 ⇒ 属于「指针能解析、但记的不是现行写法」**。
> - **实测（2026-10-29 逐行核过）**：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`
>   **不存在** `deadlineEpochMs` / `effectiveTimeoutMs` / `serverBudgetMs` / `clientDeadlineRemainingMs()` 这四个符号。
>   **现行写法**（单位是**纳秒**，不是毫秒）：
>   - `:291-292` `long serverBudgetNanos = (timeout != null && timeout.compareTo(Duration.ZERO) > 0) ? toNanosSaturated(timeout) : Long.MAX_VALUE;`
>   - `:293` `long effectiveTimeoutNanos = Math.min(serverBudgetNanos, clientDeadlineRemainingNanos());`
>   - `:319` `long timeoutNanos = effectiveTimeoutNanos;`（超时调度器延时用**它** ⇒ 与上表「超时调度器延时同用」一致）
>   - `:707` `private static long clientDeadlineRemainingNanos() {`（取值点 `:710` `io.grpc.Context.current().getDeadline()`）
> - ⇒ **「min(服务端预算, 客户端 deadline 剩余)」这个语义仍然成立**（`:293` 就是那一行）⇒ **本组件「无变化、无可观察差异」的结论维持**。
> - ⇒ **订正方式**：保留上表原符号名与原行号，**以本块为现行写法**（`effectiveTimeoutMs` ⇒ `effectiveTimeoutNanos`，
>   `clientDeadlineRemainingMs()` ⇒ `clientDeadlineRemainingNanos()`，`deadlineEpochMs` ⇒ **已无对应符号**）。

> ⚠️ **但两侧的「2000ms 从哪一刻起算」不同**（**毫秒级，不构成可观察漂移**，故不立条目，只记事实）：
> Go 的客户端 deadline **在客户端发出时起算** ⇒ 服务端收到时**已消耗一段传输时间** ⇒ 服务端 ctx 的剩余期限是「2000ms − 传输耗时」；
> RAG 的服务端预算 `rpc-timeout: 2s` **在服务端收到时起算** ⇒ 恒为满额 2000ms。
> ⇒ **RAG 的绝对截止点比 Go 晚约一个传输耗时**（本机直连为亚毫秒级）。
> ⇒ 之所以不立条目：**可观察面在秒级抖动之内**，且 E38 的修法方向正是「向 Go 靠」（一旦 RAG 客户端带上 deadline，服务端就会像 Go 一样继承并被传输时间扣减）。

> ⚠️ **顺带记一条「消费方不对称」**（**本组件内**，不是漂移）：
> **Go 的 user-api 给 user-rpc 传 2000ms deadline，RAG 的 user-api 不传。**
> ⇒ 当前**数值相同 ⇒ 行为同形**。
> ⇒ ⚠️ **但 E38 落地后，这条注记的「分叉方向」变了**（原文写的是「RAG 不会跟着客户端提前死」——
> **在 E38 修前确实如此，修后不再如此**）：
> **E38 修法落地后，RAG 的服务端会 `min(服务端预算, 客户端 deadline 剩余)`**
> ⇒ **只要 RAG 侧一旦给 user-rpc 配上 deadline，服务端就会像 Go 一样跟着客户端提前死**。
> ⇒ 也就是说，**两侧「跟着客户端死」这个行为已经是同形的**；
> 当前剩下的**唯一**不对称是「**RAG 客户端没配 deadline**」这一个配置事实（以及由它带来的毫秒级起算点差异，见上）。
> ⇒ 故记此条，**供将来调参时对照**：若有人给 RAG 的 `grpc.client.user-rpc` 加了 deadline，
> **两侧会同时变同形，不需要额外的服务端改动**；若有人只调 RAG 的 `rpc-timeout`，两侧就会分叉。
> ⚠️ 另注：`global` 域已明确
> 「`UNRESOLVED-ARPC-002` 的 CONFIRMED SEMANTIC MISMATCH 定性不变」⇒ 那是 **users/admin 域**的编号，**不在本域**。

---

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-redis-neverup-rpc.sh` ⇒ **EXIT=0**（2026-10-30 04:13:31 · 26s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：· Go  的 SetexCtx 受 handler ctx 的 **2000ms deadline** 约束 ⇒ 失败延迟 < 2s ⇒ INTERNAL 赢 ⏐ · RAG 的 Redis 写**不受** gRPC deadline 约束（只受 3s command timeout）⇒ **2s 必然先到** ⇒ DEADLINE_EXCEEDED ⏐ · 若 Go=INTERNAL 而 RAG=DEADLINE_EXCEEDED 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-redis-neverup-rpc.sh` ⇒ **EXIT=0**（2026-10-30 04:13:31 · 26s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：· Go  的 SetexCtx 受 handler ctx 的 **2000ms deadline** 约束 ⇒ 失败延迟 < 2s ⇒ INTERNAL 赢 ⏐ · RAG 的 Redis 写**不受** gRPC deadline 约束（只受 3s command timeout）⇒ **2s 必然先到** ⇒ DEADLINE_EXCEEDED ⏐ · 若 Go=INTERNAL 而 RAG=DEADLINE_EXCEEDED 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

### DRIFT-USERRPC-V-008 死代码 + **伪出处** —— ✅ **已修（2026-10-23 第四轮，commit `fa2de52`）**

> **本节是首轮原文，保留供追溯。** 修复经过见本文件第四轮 §2「`DRIFT-USERRPC-V-008` ✅ 已修」——
> 三个异常类与两个 mapper 方法**已删除**（2026-10-23 复核确认：仓内只剩注释残留，无任何类声明/方法定义）。
> ⚠️ 复核时**差点漏掉**：本节标题与 §5 汇总表两处都还写着「仍成立 / 建议下轮删掉」，
> 是与 §9 `UNRESOLVED` 表同型的**台账脱节**，已一并更正。

- **Category**: 文档 / 死代码
- **发现**：
  1. `UserExceptions.PasswordMismatchException`（`:34`）、`UsernameExistsException`（`:39`）、
     `TokenBlacklistedException`（`:44`）—— **全仓零 `throw` 点**。
     `UserDomainService.java:32-33` 只有两条 **import**，没有任何使用。
  2. `UserMapper.selectCurrentByUid`（`:149-151`）与 `findCurrentAvatar`（`:153-155`）—— **零调用点**，
     Go 侧无对应物。
- **⚠️ 最值得记的一条**：`TokenBlacklistedException` 的 javadoc 写着
  > 「对应 Go 端 LogoutLogic：用户 token 在黑名单中 → Status.PERMISSION_DENIED」
  **而 Go 的 `logoutlogic.go` 全文 35 行里没有任何 token 有效性检查**（`in.Token == ""` 之外一路直通 Redis）。
  ⇒ 这是 §3.2「**无出处 idiomatization**」的残留，而且比无出处更贵：
  **它给了一个不存在的出处**，下一个 agent 会据此「恢复」一个 Go 从未有过的行为。
- **Classification**: **OBSERVABLE DIFFERENCE**（无行为影响）
- **Evidence**: `grep -rn 'PasswordMismatchException|UsernameExistsException|TokenBlacklistedException|findCurrentAvatar|selectCurrentByUid' user-rpc/src` ⇒
  **7 处命中全部是类声明 / import / 方法定义，零调用点**；`[GO]` `logoutlogic.go` 全文。
- **Risk**: LOW（行为）／MEDIUM（误导下一个 agent）
- **Recommended**：删掉三个异常类与两个 mapper 方法，或把 javadoc 的伪出处改成「RAG 侧预留、未启用」。

---
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（2026-10-23 第四轮，commit `fa2de52`）** ⇒ **现行态 = ✅ 维持已修**（**三个异常类与两个 mapper 方法现在确实只剩注释、无任何声明/定义**）。
> - **RAG 侧（异常类）**：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/exception/UserExceptions.java:34` = `// ── 2026-10-23 删除：以下三个异常类全仓零 throw 点…` · `:35` = `// 原类：PasswordMismatchException / UsernameExistsException / TokenBlacklistedException` ⇒ **仅注释，无类声明**；保留的 `NotFoundException` / `AvatarHistoryNotFoundException` 有真实 throw 点（见同文件 `:26` / `:29`）。
> - **RAG 侧（mapper 方法）**：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserMapper.java:186` = `// 原 selectCurrentByUid(@Select) 与 findCurrentAvatar(default 包装) 全仓零调用点，` ⇒ **仅注释，方法定义已删**。
> - **Go 侧（伪出处的反证）**：`service/user/user/rpc/internal/logic/loginlogic.go`（密码不匹配 ⇒ `return &pb.LoginResp{Status: 1}, nil`，**不是异常**）· `service/user/user/api/internal/logic/user/logoutlogic.go`（**无任何 token 有效性检查**）⇒ 两个 javadoc 声称的 Go 出处确实不存在。
> - ⇒ 维持 ✅ 已修。⚠️ 本轮**未复跑** `mvn -pl user-rpc -am test` ⇒ 守门存在性已核（文件在位），**当前全绿未复跑**。核对日期 2026-10-30。

## 8. Test Cases（spec §29 + §30）

> 格式见 spec §29。**「Expected」列全部逐条核对自冻结的 Go 源文件的消费点**，不是从 RAG 现状反推的。

| # | Input / Environment | Go（真源，出处） | Spring Boot | Expected Semantic Result | Equivalent | 本轮证据 |
|---|---|---|---|---|---|---|
| CASE-01 | `Register` 新用户名 + 正常密码 | 写入成功，`RegisterResp{uid}`（`registerlogic.go:81-92`） | 同 | 两端 uid 均可用；`extra_info` 为 NULL | YES | `[PG]` `jsonbTypeHandlerWorksOnGeneratedInsert`（RAG 侧）+ `[GO]` 静态 |
| CASE-02 | `Register` 已存在用户名 | `AlreadyExists "用户名已存在"`（`:48`） | 同 | 同 code 同 description | YES | `[T]` `register_usernameExists_throwsAlreadyExists` |
| CASE-03 | `Register` 空 extra_info | 写 NULL（`:79`） | 不进 patch ⇒ DEFAULT NULL | 列值同为 NULL | YES | `[PG]` `seedUser` 走的是裸 INSERT，不足以证明 ⇒ **仅静态** |
| CASE-04 | `Login` 用户不存在 | `status=1, err=nil`（`:46-52`） | 同 | **gRPC code=OK**，`LoginResp.status=1` | YES | `[T]` + `[GO]` 静态 |
| CASE-05 | `Login` 密码错 | `status=1`（`:60-66`），打 `login_total{internal_error}` | 同 | 与 CASE-04 **不可区分** | YES | `[T]` `LoginMalformedHashDrift004Test` |
| CASE-06 | `Login` `status=1` 的用户 | `status=2`（`:67-72`），**无 metric** | 同 | `code=OK` + `status=2` | YES | `[T]` `login_bannedUser_returnsStatusTwo` |
| CASE-07 | **`Login` `status IS NULL` 的用户** | **返回 `status=0` 登录成功**（⚠️ 原写 `INTERNAL "DB查询失败"` 是**已被证伪的旧说法**：实测 Go 的 GORM 把 NULL 读成 **0** 且不报错） | 返回 `status=0` 登录成功 | **应当等价** | ✅ **一致**（**已修**，V-001） | `[PG]` `UserRpcLoginNullStatusReadsAsZeroTest`（真 PG：`DROP NOT NULL` + 插 NULL 行 + 反向对照组） |
| CASE-08 | `Login` 成本越界的 bcrypt hash（`$2a$99$…`） | `false` ⇒ 密码错 | `MALFORMED_HASH` ⇒ 仍密码错 | 对外同为 `status=1` | YES | `[T]` 4 条 DRIFT-004 用例 |
| CASE-09 | `GetUser` 不存在的 uid | `NOT_FOUND`（响应被丢弃） | `NOT_FOUND` | 同 code | YES | `[T]` |
| CASE-10 | `GetUser` 存在的 uid | `Found=true` + `UserInfo{uid,username,email,extra_info,status}`，**`score=0`** | 同 | `score` 两侧都是 0 | YES | `[T]` `getUser_success_returnsUserInfo` |
| CASE-11 | `UpdateUser` 只传 password | 只改 password + `update_time` | 同 | email / extra_info 不变 | YES | `[PG]`（`updateUserById` 层）+ `[T]` |
| CASE-12 | `UpdateUser` 传空 email | **email 列不变** | 同 | 邮箱不丢 | YES | `[T]` `updateUser_partialUpdate_onlyWritesNonEmptyFields` |
| CASE-13 | `UpdateUser` 把 username 改成自己的现值 | `AlreadyExists`（不排除自己，`updateuserlogic.go:36-39`） | 同 | 同 | YES | `[T]` `updateUser_selfUsernameChange_throwsAlreadyExists` |
| CASE-14 | `UpdateUser` 目标 uid 不存在 | update 影响 0 行 → 重读 `NOT_FOUND` | 同 | 同 code | YES | `[T]` `updateUser_userDeletedConcurrently_throwsNotFound` |
| CASE-15 | `UpdateUser` 全字段为空 | 不发 UPDATE，`update_time` 不动 | 同（`any==false→return 0`） | 同 | YES | `[PG]` `zeroValuePatchDoesNotUpdate` |
| CASE-16 | **`UpdateUser` 显式空 map + 其余空** | 发 UPDATE（写 `{}` + 推进 `update_time`） | **不发任何 UPDATE** | **应当等价** | **YES**（**已判等价**） | **V-005 判为「不成立」** —— protobuf3 的**空 map 在 wire 上不可表达**（零条目 ⇒ 字段缺席 ⇒ 解出来是 nil）⇒ `UNRESOLVED-USERRPC-003` 已闭环 |
| CASE-17 | `DeleteUser` 不存在的 uid | `NOT_FOUND` | `NOT_FOUND` | 同 code；响应 `success` 不可见 | YES | `[T]` |
| CASE-18 | `DeleteUser` 存在的 uid（带头像历史） | 只删 users 行，**孤儿 history 保留** | 同 | 两侧都留孤儿 | YES | `[GO][RAG]` 静态（无 FK） |
| CASE-19 | `UploadAvatar` 空 `avatar_url`（全空格） | `INVALID_ARGUMENT "avatar_url is required"`（`uploadavatarlogic.go:34-37`） | 同 | 同 code 同 description | YES | `[T]` |
| CASE-20 | `UploadAvatar` 空 `content_type` | 落库 `"image/png"`（`model/user.go:179-184`） | 同 | 同 | YES | `[T]` `uploadAvatar_emptyContentType_defaultsToImagePng` |
| CASE-21 | `UploadAvatar` 正常 | 4 步落库 + `extra_info["avatar"]` 双写 | 同 | 同 | YES | `[PG]` `createAvatarHistorySucceedsOnRealPg` |
| CASE-22 | `UploadAvatar` history_id 撞 UNIQUE | 事务回滚，旧 current 仍在 | 同 | 同 | YES | `[PG]` `createAvatarHistoryRollsBackOnRealPg` |
| CASE-23 | `SelectAvatar` 不属于该 uid 的 history_id | `NOT_FOUND`，**且不清任何 current** | 同 | 同 | YES | `[PG]` `selectAvatarHistorySucceedsAndIsolatesByUid` + `...MissingDoesNotClearCurrent` |
| CASE-24 | `SelectAvatar` 成功后响应 `is_current` | **恒 `true`**（`model/user.go:174`） | 恒 `true` | 同 | YES | `[PG]` + `[T]` |
| CASE-25 | `GetAvatarHistory` `limit=0` / `31` / `-1` | 静默 clamp 到 30 | 同 | 同 | YES | `[GO][RAG]` 静态 |
| CASE-26 | `GetAvatarHistory` 不存在的 uid | `NOT_FOUND` | `NOT_FOUND` | 同 | YES | `[T]` |
| CASE-27 | **`GetAvatarHistory` 的 `create_time` 渲染** | 带时区偏移（如 `+08:00`） | 恒 `Z` | **应当等价** | **YES**（**已修**） | **V-004 已修**（列类型改 `timestamptz`）· 偏移量机制已定（`UNRESOLVED-USERRPC-002` 闭环）⇒ 见 `UserRpcUsersUserTimestamptzParityTest` / `UserRpcAvatarHistoryTimestamptzParityTest` |
| CASE-28 | `Logout` 空 token | `INVALID_ARGUMENT "Token不能为空"` | 同 | 同 | YES | `[T]` |
| CASE-29 | `Logout` 正常 token | `SETEX user:jwt_blacklist:<tok> 2592000 "banned"`；**`success=false`** | 同 | **两侧 success 都是 false** | YES | `[T]` `logout_writesRedisWithCorrectKeyAndTtl` |
| CASE-30 | `Logout` token 含前后空格 | **不 trim**，整串作 key | 不 trim，整串作 key | 同 | YES | `[GO][RAG]` 静态 |
| CASE-31 | 任一 RPC 执行超过 2000ms | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | **完全等价**（code 与 description 两侧全等）| **YES** | ✅ 原为 **PARTIAL**（description 不等价）—— **V-007 已于 2026-10-26 由 `global` 域销账** |
| CASE-32 | PG 被 ACCESS EXCLUSIVE 锁住 12s | 客户端 2000ms 返回；PG 查询 203ms 后消失 | 客户端 2000ms 返回；PG 查询 176ms 后消失 | 同量级 | YES | `_shared.md` §6.2.6 E-4 **实测**（2026-10-21，本轮未重跑） |
| CASE-33 | 同一 uid 并发 `UploadAvatar` ×2 | 可能两条 `is_current=true` | 同 | **共有缺陷，非漂移** | YES（缺陷等价） | `[GO][RAG]` 静态：两侧都无 `FOR UPDATE` |
| CASE-34 | 同一 username 并发 `Register` ×2 | 恰好 1 成功 + 1 `INTERNAL`（撞 UNIQUE） | 同 | 同 | YES | `[PG]` `uniqueConstraintsAreEnforcedOnRealPg`（约束层）+ 静态 |
| CASE-35 | 进程启动时 Redis 不可用 | **启动成功**（`NonBlock` 默认 `true` ⇒ `MustNewRedis` **不做连接检查**） | 启动成功（懒连接） | ✅ **一致**（原记 **NO**，**2026-10-24 活体实验证伪**） | **§9.3** |
| CASE-36 | 进程启动时 `status` DDL 已存在但列宽是 varchar(255) | — | 幂等 `ALTER ... TYPE text` 自愈 | RAG 单侧特性 | N/A | `[PG]` `UserRpcSchemaTextColumnTest#alterSelfHealsAnExistingVarcharColumn` |
| CASE-37 | **`Logout` 时 Redis **拒连**（没人监听）** | `INTERNAL`「注销失败,Redis写入异常」/ ~1.7s | `INTERNAL`「注销失败,Redis写入异常」/ ~0.005~0.2s | ✅ **同形**（code + message 一致；**耗时差一个数量级**）| **YES** | `[GO][RAG]` **双侧活体直连 gRPC**（2026-10-26 第十一轮）：`tools/difftest/exp-difftest-redis-neverup-rpc.sh` |
| CASE-38 | **`Logout` 时 Redis **主机名解析失败**（`redis`，**生产默认形态**）| `INTERNAL`「注销失败,Redis写入异常」/ ~1.8s | `INTERNAL`「注销失败,Redis写入异常」/ ~0.09~0.34s | ✅ **同形**（code + message 一致）| **YES** | 同上（`REDIS_HOST=redis`）|
| CASE-39 | **`Logout` 时 Redis **连上但不回包**（⇒ 命令超时路径）| `DEADLINE_EXCEEDED`「context deadline exceeded」/ ~2.0s | `DEADLINE_EXCEEDED`「**context deadline exceeded**」/ **2113·2008·2003ms** | ✅ **两侧 code 与 description 逐字一致** ⇒ **V-007 已闭环**（2026-10-26 23:31 · **合回后本域双侧活体**，同一次运行实测）；<br>🔴 **业务日志归因仍不一致** ⇒ **V-012**（RAG 记「Redis 写入异常」，客户端收到「超时」；Go 一致）⇒ 本轮同时抓到 RAG 日志现场：`logout RPC failed: Status{code=INTERNAL, description=注销失败,Redis写入异常}` | **YES**（wire 侧 · 已对齐）/ **观测面 NO**（V-012） | 同上（`REDIS_MODE=hanging`）|

> 📌 **历史留痕**：本行 RAG 侧那一格**曾经**是「合回前的旧串」（`rpc timeout after 2000ms`）⇒ 当日 18:00 拍板「暂不合回」⇒ ⇒ 「**在主仓上重跑本行，看到的仍是旧串**」当时成立。
> ✅ **2026-10-26 23:1x 后已过期**：`align/global` 已合回（`4f839f9`）⇒ **本行的 RAG 侧现在也是 `context deadline exceeded`** ⇒ ⇒ **重跑本行看到的应是两侧逐字一致**（23:31 实测已如此）。

> 🔴 **2026-10-26 第十二轮新增守网（**覆盖缺口闭环，不是新漂移**）** ——
> **B-1 的两个 MyBatis 拦截器「真的被装进插件链」此前从未被断言** ⇒ 若被摘掉，
> PG 的 `57014` 不会翻成 `DEADLINE_EXCEEDED` ⇒ 落进 `catch (Exception e)` ⇒ 被改写成 `INTERNAL`
> ⇒ api 侧渲染 **5001** 而 Go 是 **1015** ⇒ **wire 级漂移，且静默**。
> ⇒ 补 3 条（插件链 / 挂载点 / 负控），含 **M1·M2·M3** 三次变异验证 ⇒ 详见**第十二轮**。

### 8.1 本组件（`user-rpc`）的**销账提交清单**（结构化 · 2026-10-26 补）

> **为什么要补这张表**：本域此前的销账提交**散落在条目标题里**，没有结构化登记
> （`api.md` 的 12 个条目里**只有 D-004 带 SHA**）⇒ 机器复核工具的
> 「登记的哈希必须存在 / 必须在祖先链上」这类判据**对本域无从下手**。
> ⇒ 本表把「**条目 ↔ 销账方式 ↔ 销账提交**」变成可解析的结构，
> 供 `global` 域的 `DOMAIN_CFG['users']` 直接消费
> （判据规格 ⇒ [`users/_shared.md`](../_shared.md) 的「**可机检不变量**」一节）。
>
> ⚠️ **两条纪律**：
> 1. **每个 SHA 都逐个 `git show --stat` 核过主题与文件面**（PIT-USERS-SHARED-020）——
>    **不靠提交标题推断**；
> 2. 2026-10-23 有 **3 个主题为「`add sth` / `add shit`」的批量导入提交**
>    （`4a63342` / `e993ff0` / `69e3262`），它们**混合了多条漂移的修复** ⇒
>    **不能作条目级锚点** ⇒ 相关条目在表里标「**未具名**」并写明理由，**不猜**。

| 条目 | 销账方式 | 销账提交（SHA） | 提交主题（逐字） | 备注 |
|---|---|---|---|---|
| **V-001** | 证伪关闭（**无代码修复**）| `9ae121d` | `fix(users/user): 撤销 V-001 反向判定 —— Go 侧 NULL status 是 fail-open 而非 fail-closed` | 原判「Go 拒 / RAG 放」被真 PG 端到端**证伪**（两侧都是 `status=0` 登录成功）⇒ 关闭靠的是**证据**，不是改代码。⚠️ 同一 commit 另含 D-001 / D-002 的实现（⇒ 见 [`api.md` §8.2](./api.md)） |
| **V-002** | 🟡 **2026-10-27 订正：定性为「DDL 层差异 · 行为层零差异」⇒ 建议保持 RAG NOT NULL ⇒ 本条无落码动作** | — | — | ⚠️ **本行原写「🔴 未修（被卡）· 改动必须与 admin 域同一次提交」—— 该口径已被 2026-10-27 的逐层证据链推翻**（Go 读 NULL ⇒ **0** 且不报错；RAG NOT NULL ⇒ 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**）⇒ **「放开可空」反而会制造新漂移**（RAG `callSettersOnNulls=false` ⇒ 读出 null ≠ Go 的 0 ⇒ 方向相反）⇒ ⇒ **正确处置 = 维持 NOT NULL**（落点 `service/users/users-common` **无动作**）⇒ ⚠️ **销号签字不在 G1 写权面**（`docs/Intentional_Deviations/**` 不在白名单）⇒ 仍待 G4 / 合并收口执行者确认；逐层证据链见 §「2026-10-27 订正块」 |
| **V-003** | 代码修复 | `ec33159`（DDL + `user-rpc` 侧）<br>`c10f639`（**`admin-rpc` 侧**） | `align(users/user): 修 DRIFT-USERRPC-V-003（users_user.extra_info jsonb⇒text，与 Go 一致）`<br>`fix(users/admin): DDL 列类型对齐 Go + ARPC-002 wire 对齐 + 签字档补录` | 落点 `service/users/users-common/src/main/resources/db/01_create_user.sql`（DDL）+ 两个 rpc 的实体 typeHandler<br>⚠️ **2026-10-26 核实**：写路径绑定侧**跨组件那一格（`admin-rpc`）也已在主干上闭合**（`c10f639`，**`users/admin` 域自己的提交**）⇒ **不是** global 的 B10 `ff80214`（那个仍在 `align/global`、对主干已冗余）⇒ 详见 §7 V-003 的「2026-10-26 补记」 |
| **V-004** | 代码修复 | ⚠️ **未具名** | — | 实际落在 2026-10-23 的批量导入提交 `4a63342`（主题「`add sth`」，**混合多条漂移**）⇒ 不可作条目级锚点 |
| **V-005** | 证伪关闭（protobuf3 空 map 在 wire 上不可表达）| ⚠️ **未具名** | — | 同上；关闭依据是**协议层事实**，非代码改动 |
| **V-006** | 证伪关闭（真 PG 实证：雪花 id 永不进 wire）| `fa2de52` | `align(users/user): user-rpc 关闭 V-006、修 V-008（并揭出第二个伪出处）` | 同 commit 兼销 V-008 ⇒ **一条 commit 销两条**（工具若按「一 commit 一行」判会漏） |
| **V-007** | 销账回写（**实现在 `global` 域**）| 实现 `49808ad` + 本域回写 `fc1c4a7` | 实现：`第三十轮：A 类 3 条落地 —— E30（RPC 超时文案）· T-14 · T-15③` ✅ **已合回主干 `4f839f9`（2026-10-26 23:1x）** ⇒ **本域已自己复跑双侧活体**（23:31 · `REDIS_MODE=hanging` · 两侧 description 逐字一致）⇒ **证据强度：静态 + 他域活体 ⇒ 本域双侧活体**<br>⚠️ **历史留痕**：本日早间拍板为「暂不合回 ⇒ 待合回不是忘了合」⇒ 同日 23:1x 实际合回 ⇒ 那句措辞已过期 |
| **V-008** | 代码修复（删死代码 + 揭出**两个**伪出处）| `fa2de52` | 同 V-006 | 同上 |
| **V-011** | ⚠️ **未修**（当前不可观察）| — | — | 时间列 `DEFAULT CURRENT_TIMESTAMP`（Go 无 DEFAULT）⇒ 两侧写入路径都显式写该列 ⇒ 当前不可观察 |
| **V-012** | 🔴 **未修**（观测面 · 新立）| — | — | 候选修法**待拍板**；与 `global` E38② / `users/admin` ARPC-010 同型 |
| **B-1 装配层守网** | 守网补强（**非漂移**）| `bcfdaf7` | `test(users/user): B-1 的装配层守网（插件链 + 挂载点 + 负控）⇒ 覆盖缺口闭环（非新漂移）` | 生产代码**一字未改**；M1·M2·M3 三次变异验证 ⇒ 详见**第十二轮** |
| **V-013** | 已核实的形态差异（**非 wire 漂移**）+ 断言订正 | `a5a2e6c` | `docs+test(users/user): E58 的客户端可见面复核 ⇒ 源码层成立 / 客户端面两侧一致 ⇒ 订正 17 处注释的**理由** + 新立 V-013` | **行为零改动**（只订正 17 处注释的**理由** + 类级 javadoc 论证）⇒ 详见**第十三轮** |

⚠️ **为什么「销账方式」这一列不能省**：本域的销账**不是一种形态**——
「代码修复」（V-003/V-008）·「证伪关闭」（V-001/V-005/V-006）·「销账回写」（V-007，落点在他域）·
「签字保留」·「未修」⇒ **只有区分开，机器才能把 §4.4 的分类计数与条目逐条对账**（不变量 I1）。

---

## 9. Unresolved（spec §37，**此节不得为空**）

| # | Question | Go Evidence | Java Evidence | Why Unknown | Impact | Required Confirmation |
|---|---|---|---|---|---|---|
| ~~**UNRESOLVED-USERRPC-001**~~ | ✅ **已闭环（第三/四轮，2026-10-24 复核同步）** —— V-001 的端到端可达性**已实测** | `data_model.go:11`（无 NOT NULL）⇒ 列可空 ⇒ 机制上可达 | `01_create_user.sql:39` `NOT NULL` ⇒ RAG 自建库不可达 | 两条静态证据**互相矛盾** ⇒ 需真实数据才能判 | ~~HIGH（安全方向）~~ → 已闭环 | **已真做**：RAG 库 `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL` + 插 NULL 行（`UserRpcLoginNullStatusReadsAsZeroTest.java:116-125`，真 PG Testcontainers）⇒ **两侧都是 `status=0` 登录成功**（Go 的 GORM 把 NULL 读成 0 且不报错，见 `admin/rpc.md` §2 的三段机制）⇒ **V-001 已修且两侧一致**。⚠️ 原写的「Go 会 `INTERNAL DB查询失败`」是**已被证伪的旧说法** |
| ~~**UNRESOLVED-USERRPC-002**~~ | ✅ **已闭环（第三轮 §2）** —— 时区偏移量的机制已定 | DSN 钉 `TimeZone=Asia/Shanghai`（`db.go:21`）；列类型 `timestamptz` | JDBC URL 无 `TimeZone`；列类型已改 `timestamptz`（V-004 已修） | ~~JVM 默认时区、PG 会话 `TimeZone`、容器 TZ 三者都未实测~~ ⇒ **已实测** | ~~MEDIUM~~ → 已闭环 | **机制已定**：偏移量由**进程本地时区**（Go 的 `time.Local`）决定，**PG 会话时区不参与**（pgx 走二进制解码）；源码依据 `pgtype/timestamptz.go:133-137`（`ScanLocation` 默认 nil）→ `:270-276` 回落到 `time.Local`。⚠️ 本行原写的「列类型 `timestamp`」是**过期状态**（V-004 已修） |
| ~~**UNRESOLVED-USERRPC-003**~~ | ✅ **已闭环（第三轮 §3）** —— 「显式空 map」的 wire 可达性已定 | protobuf-go 对非 nil map 视为「已设置」⇒ 会编码 | protobuf-java 的 `MapField` 写出条件**已读源码** | ~~只做了分支层静态对照，未构造 wire 输入~~ ⇒ **已定** | ~~LOW~~ → 已闭环 | 答案：「**protobuf3 的空 map 在 wire 上不可表达**」（零条目 ⇒ 字段缺席 ⇒ 解出来是 nil）⇒ **V-005 判为「不成立」予以关闭** |
| ~~**UNRESOLVED-USERRPC-004**~~ | ✅ **已闭环（2026-10-24）** —— ① 由第四轮 V-006 关闭；② 由**活体实验**关闭 | — | — | ① id 的取值来源：**V-006 已关闭**（真 PG：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；永不进 wire）。② 启动期 Redis 不可用：**两侧都启动成功 ⇒ 一致**（原推断「Go fail-fast」**被证伪** —— go-zero `RedisConf.NonBlock` 的 struct tag 默认是 `true` ⇒ `MustNewRedis` **不做连接检查**） | ~~LOW~~ → 已闭环 | ⇒ **§9.3**（活体：把两侧 Redis 都指到死端口后起进程） |
| ~~**UNRESOLVED-USERRPC-006**~~ | ✅ **已闭环（2026-10-23 第五轮 §4，2026-10-24 独立复现）** | — | — | **已逐项核对**（真库对照：两侧各用自己的建表机制把表建出来，再查 `information_schema` + `pg_indexes` + `pg_constraint`）。⚠️ **不要把 `check-ddl-static.py`「全绿」读成「DDL 已一致」** —— 该脚本文件头**自陈**只查 `data_type` 与 `character_maximum_length`，其余标 **NOT CHECKED** | ~~MEDIUM~~ → 已闭环 | ⇒ 结论见本文件 §「2026-10-23 第五轮」§4；**2026-10-24 用本轮自建的一对全新库（`uu_both_go_db` / `uu_both_rag_db`）独立复现，结论逐条一致**：`status` 的 NOT NULL 差异（**V-002**，⚠️ **原写「需与 admin 域同改」已被 2026-10-27 的逐层证据链推翻** ⇒ 正确处置 = **保持 NOT NULL** ⇒ 落点无动作）、两个时间列的 DEFAULT 差异（**V-011**，Go 无 DEFAULT / RAG 有 `CURRENT_TIMESTAMP`，**当前不可观察**）、唯一约束与索引**功能等价** |
| ~~**UNRESOLVED-USERRPC-007**~~ | ✅ **已闭环（2026-10-24）** | — | — | 双侧活体对拍已跑（真进程 + 真 PG + 真 Redis，**自带容器 / 自带库 / 自带端口**）：**43 场景 / 39 等价 / 4 处差异**，4 处**全部**是 N-2 已签字的 Go 源码乱码文案 ⇒ **无新漂移**<br>🔴 **2026-10-30 活体对拍轮：这条断言被独立复现，且「可复现性」本身是本轮修回来的** —— 复跑 `bash tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **43 场景 / 差异 4**（与 2026-10-24 的「39 等价 / 4 处差异」**逐条一致**）⇒ 4 处差异仍是 `UploadAvatar: 查无此人` / `GetAvatarHistory: 查无此人` / `SelectAvatar: 不存在的 history_id` / `SelectAvatar: 查无此人`⇒ **全部是 N-2 已签字的 Go 源码 GBK 乱码文案**（Go 侧 `鐢ㄦ埛涓嶅瓨鍦?` = `e990a2…` vs RAG 侧 `用户不存在` = `e794a8…`）⇒ **无新漂移** ⇒ **现行态 = ✅ 已闭环（不变）**<br>⚠️ **但本轮同时发现：这条断言的证据在 2026-10-30 之前是「腐烂」的** —— 该探针**起 RAG user-rpc 时没给 fail-closed 密钥**（`${USER_JWT_SECRET}` 无默认值 ⇒ 占位符解析不出来 ⇒ 进程起不来）⇒ **探针根本跑不到比对阶段** ⇒ 2026-10-24 的活体记录**无法被复算** ⇒ **「有活体记录」≠「现在还跑得起来」** ⇒ 已修（新增 `tools/difftest/lib/rag-secrets.sh` 共享件 · 12 个探针注入）⇒ 修后复跑 ⇒ **43/39/4 逐条复现** ⇒ 证据链**恢复可复现**。核对日期 2026-10-30 | ~~HIGH（本轮最大的证据缺口）~~ → 已闭环 | ⇒ **§9.1**（含首轮 2 处**假漂移**的辨伪记录 + 守网清单） |
| ~~**UNRESOLVED-USERRPC-005**~~ | ✅ **已闭环（2026-10-24）** | — | — | **双侧对称并发启动实测**：Go 侧 `go-user-rpc`+`go-admin-rpc` 与 RAG 侧 `user-rpc`+`admin-rpc` **各自同时**对**同一个空库**启动 ⇒ 两侧都起来、表都建齐（各 4 张）、**0 致命错误**（共 9 轮：Go 5 轮 + RAG 6 轮） | ~~HIGH（跨组件不变量）~~ → 已闭环 | ⇒ **§9.2**（含 advisory lock 串行化的时间戳证据 + Go 侧「时序侥幸」的机制差异 + admin 域的复核提示） |

---

## 9.1 UNRESOLVED-USERRPC-007 闭环记录（2026-10-24 · 双侧活体对拍）

> 本节是 §9 那条 UNRESOLVED 的**闭环记录**，**不是**新的漂移条目。
> 证据强度：**双侧活体对拍（真进程 + 真 PG + 真 Redis）** ——
> 这是 AGENTS.md §0 证据分层里**最高的一档**（此前本组件的 91 条测试**全是 RAG 单侧**）。

### 9.1.1 怎么跑的

| 项 | 内容 |
|---|---|
| 编排脚本 | `tools/difftest/exp-difftest-grpc-user9.sh`（**自带容器 + 自带库 + 自带端口**，不碰共享的 `go_db` / `rag_db`） |
| 执行主体 | `tools/difftest/grpc-user9-dual-port-runner.java`（43 场景） |
| Go 侧 | 真源二进制 `go-user-rpc`（`go build` 自冻结仓库，**不复制源码**），监听 `:19005`，连 `uu_go_db` |
| RAG 侧 | `user-rpc-0.0.1-SNAPSHOT.jar`，监听 `:59211`，连 `uu_rag_db` |
| 基础设施 | 自建 `uudifftest-postgres`（:55434）/ `uudifftest-redis`（:56382） |
| 覆盖 | 9 个 RPC × ① 字段级响应 ② `grpc-message` 逐字 ③ 边界输入 |

**逐字约定**：普通字段两侧必须逐字相同；带 `~` 前缀的字段（uid / token / history.id / create_time）为每侧自有，只断言存在性；
`user.has_status` 专测 proto3 `optional` 字段的**存在性**（Go 的 `GetUser` 会填 `Status`、`UpdateUser` 不填 ⇒ 「有没有这个字段」本身就是 wire 级差异）。

### 9.1.2 结论：43 场景 / 39 等价 / 4 处差异 —— **4 处全是 N-2，无新漂移**

| # | 场景 | Go（原始字节） | RAG | 判定 |
|---|---|---|---|---|
| 1 | `UploadAvatar` 查无此人 | `鐢ㄦ埛涓嶅瓨鍦?` | `用户不存在` | N-2 已签字 |
| 2 | `GetAvatarHistory` 查无此人 | `鐢ㄦ埛涓嶅瓨鍦?` | `用户不存在` | N-2 已签字 |
| 3 | `SelectAvatar` 不存在的 history_id | `澶村儚鍘嗗彶涓嶅瓨鍦?` | `头像历史不存在` | N-2 已签字 |
| 4 | `SelectAvatar` 查无此人 | `鐢ㄦ埛涓嶅瓨鍦?` | `用户不存在` | N-2 已签字 |

⇒ **4/4 的 gRPC 状态码两侧完全相同**；差异只在 `grpc-message` 头的字节。

### 9.1.3 「Go 源码乱码」为什么判定为**编辑事故**而不是语义（2026-10-24 逐文件 hex 实测）

**同一个 Go 仓库里，同一个语义同时存在正常写法与损坏写法**：

| 语义 | 正常写法（Go） | 损坏写法（Go） |
|---|---|---|
| 用户不存在 | `getuserlogic.go:39` / `deleteuserlogic.go:46` / `updateuserlogic.go:74` | `uploadavatarlogic.go:57` / `getavatarhistorylogic.go:35` / `selectavatarlogic.go:43` |
| DB查询失败 | `getuserlogic.go:44` / `registerlogic.go:54` / `loginlogic.go:56` / `updateuserlogic.go:43`·`:77` / `deleteuserlogic.go:52` | `getavatarhistorylogic.go:38` / `:44` |
| DB更新失败 | `registerlogic.go:86` / `updateuserlogic.go:66` / `deleteuserlogic.go:60` | `selectavatarlogic.go:49` |

⇒ 损坏的那几处是 **UTF-8 → GBK 误解码 → UTF-8** 的转码往返：
「用户不存在」的 UTF-8 字节 `e794a8 e688b7 e4b88d e5ad98 e59ca8` 按 GBK 解码正好得到「鐢ㄦ埛涓嶅瓨鍦」+ 末字节落成 `?`。
⇒ **Go 自己对同一语义会吐两种字节** ⇒ 损坏的那份在定义上就不是语义。

**处置**：N-2（**用户 2026-10-21 已拍板**：RAG 保留正常中文，不复刻乱码）。
⚠️ 兄弟域 `users/admin` 侧同样有 **17 处**（`updateselflogic.go` 7 / `createinvitecodelogic.go` 5 / `createadminlogic.go` 3 / `getselflogic.go` 2），
已由 admin 域台账 `DRIFT-ARPC-007` 处置 —— **本轮不重开**。

### 9.1.4 🔴 辨伪记录：首轮跑出的 2 处差异**不是漂移**（脚本状态残留）

**首轮结果**：43 场景 / **6 处**差异，其中 2 处看起来像**新的产品漂移**：

| 场景 | 首轮 Go | 首轮 RAG | 看着像 |
|---|---|---|---|
| `Register` 空用户名 | `OK` | `ALREADY_EXISTS`「用户名已存在」 | RAG 误判重名 |
| `Register` 空邮箱 | `OK` | `INTERNAL`「DB更新失败」 | RAG 写库失败 |

**真相：两侧状态不对称。**
- Go 侧表名 `users`、RAG 侧表名 `users_user`，两边都有 `UNIQUE(username)` / `UNIQUE(email)`；
- 本对拍里**「空用户名」「空邮箱」两个场景的输入不是按 run 唯一的**（每次都是 `''`）；
- ⇒ 上一轮跑完留下的 `username=''` / `email=''` 行，会让下一轮在**留着残留的那一侧**撞 UNIQUE。

**实测证据**：
```
首轮（uu_rag_db 里有上一轮遗留的 username='' 与 email=''）：
    Register: 空用户名   go=OK  rag=ALREADY_EXISTS   ← 假漂移
    Register: 空邮箱     go=OK  rag=INTERNAL          ← 假漂移
两侧同时清空后复跑：
    Register: 空用户名   go=OK  rag=OK                ✓
    Register: 空邮箱     go=OK  rag=OK                ✓
    ⇒ 43 场景 / 4 处差异（只剩 N-2 的乱码文案）
```

⇒ **这 2 处是假阳性**，而且**反向证明了两侧的 UNIQUE 约束行为是一致的**（干净库上两侧都成功插入）。
⇒ **处置**：清状态的责任落到 **runner 自己**身上（`resetState()`：跑前清空两侧业务表），
而不是只靠脚本的 `DROP DATABASE` —— 因为「复用已起好的栈只重跑 runner」这条**最常见的用法**不受脚本保护。
⇒ 教训见 `docs/Pitfalls/users/_shared.md` 的 **PIT-USERS-SHARED-007**。

### 9.1.5 守网

| 护网 | 覆盖 |
|---|---|
| `UserRpcGoErrorMessageParityTest`（本轮新建） | 9 条 gRPC description **逐字**断言（正常中文）+ **决策 tripwire**（断言 RAG ≠ Go 的损坏字节）；DB 三条失败路径用**真故障注入**（改表名 / 加 CHECK 约束）制造，不是 mock |
| `exp-difftest-grpc-user9.sh` + `grpc-user9-dual-port-runner.java` | 43 场景双侧活体对拍，可一键复跑 |

**变异验证（2026-10-24，4/4 取得有效证据）** —— §3.3 的纪律：**必须能编译 + 断言红**（构建红 / 上下文加载错都不算）：

| 变异 | 改法 | 结果 | 断言原文 |
|---|---|---|---|
| M-A | `"用户不存在"` → `"用户不存在X"`（7 处） | ✅ 断言红 | `expected: <用户不存在> but was: <用户不存在X>` |
| M-B | `"用户不存在"` → Go 的**损坏字面量**（7 处） | ✅ 断言红 | `expected: <用户不存在> but was: <鐢ㄦ埛涓嶅瓨鍦?>` |
| M-C | `"头像历史不存在"` → Go 的损坏字面量（2 处） | ✅ 断言红 | `expected: <头像历史不存在> but was: <澶村儚鍘嗗彶涓嶅瓨鍦?>` |
| M-D | `"用户名已存在"` → `"用户名已存在X"`（2 处，未损坏那组） | ✅ 断言红 | `expected: <用户名已存在> but was: <用户名已存在X>` |

⇒ 复原后回归**绿**，且 `UserDomainService.java` 与备份**逐字节一致**。

⚠️ **第一版变异脚本把「上下文加载失败」当成了「红」** —— M-A 首次跑出的是
`Failed to load ApplicationContext`（`Errors: 3`）而不是断言失败。
**基线复跑 3 次全绿**证明那是**环境抖动**（本机 38 个容器、7.65 GiB 内存下的 testcontainers 启动竞争），
**不是**变异造成的 ⇒ 按 §3.3 **该次 M-A 不算证据**，已重做。
⇒ 第二版判定逻辑**显式区分**「编译失败 / 上下文加载失败 / 断言红」三类，并保留**断言消息原文**作为一手证据
（同 `PIT-USERS-SHARED-006` 的纪律）。

⚠️ **已知覆盖缺口（1/9，如实记录）**：`history_id生成失败`（Go 对应 `uploadavatarlogic.go:42` 的 `history_id鐢熸垚澶辫触`）
**进不了运行时护网** —— 它要求 `Snowflake.nextId()` 抛错，而 `Snowflake` 是**进程级静态单例**
（`setWorkerId(null)` 是 no-op、`workerId` first-wins、无 reset 钩子）⇒ 在不动共享模块 `service/common` 的前提下无法诱发。
⇒ **别让下一个人以为 9/9 都守住了。**

---

## 9.2 UNRESOLVED-USERRPC-005 闭环记录（2026-10-24 · 双侧对称并发启动）

> §9 那条 UNRESOLVED 的闭环记录。台账原文写着「**本组件单侧验不完**：必须与 `admin-rpc` 的结论合并判读」——
> 本轮把**两侧都跑了**（把 admin-rpc 当**被测进程**运行，**未改它的任何代码**）。

### 9.2.1 问题

`users_user`（Go 侧叫 `users`）由 **user-rpc 与 admin-rpc 都建** ⇒ 两个进程**并发**对**同一个空库**启动时会不会撞？

### 9.2.2 实验设计（双侧对称）

| 项 | 内容 |
|---|---|
| 编排脚本 | `tools/difftest/exp-difftest-concurrent-schema.sh` |
| Go 侧 | `go-user-rpc`（:19005）+ `go-admin-rpc`（:19015）**同时**启动，连**同一个空库** `uu_both_go_db` |
| RAG 侧 | `user-rpc`（:59211）+ `admin-rpc`（:59311）**同时**启动，连**同一个空库** `uu_both_rag_db` |
| 基础设施 | 自建 `uudifftest-postgres`（:55434）/ `uudifftest-redis`（:56382）；**不碰共享的 go_db / rag_db** |

### 9.2.3 两侧的机制（声明点）

| | 建表集合 | 并发保护 |
|---|---|---|
| Go user-rpc | `AutoMigrate(&User{}, &UserAvatarHistory{})` ⇒ `users` + `user_avatar_history`（`service/user/user/rpc/internal/model/db.go:33`） | GORM 的 **check-then-act**（`AutoMigrate → if !HasTable → createTable`，且 `CREATE TABLE` **不带** `IF NOT EXISTS`） |
| Go admin-rpc | `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` ⇒ `admins` + `admin_invites` + **`users`**（`service/user/admin/rpc/internal/model/db.go:15`） | 同上 |
| RAG user-rpc | `SchemaInitializer.applyAll` ⇒ **全部 4 张** | **PG advisory lock（键 `8_675_309`）** 串行化 + `CREATE TABLE IF NOT EXISTS` |
| RAG admin-rpc | 同上，**全部 4 张** | 同上（**同一个锁键**） |

⇒ **两侧都是「两个服务建同一批表、都碰 `users`/`users_user`」的同型交叉**。

### 9.2.4 结果（Go 侧 6 轮 + RAG 侧 10 轮，每轮都是「空库 + 两侧同时启动」）

| | Go 侧 | RAG 侧 |
|---|---|---|
| 两个进程都起来？ | ✅ 6/6 | ✅ 10/10 |
| 建出的表 | `users` / `user_avatar_history` / `admins` / `admin_invites`（**4 张**） | `users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite`（**4 张**） |
| 致命错误 | **0 处** × 6 轮 | **0 处** × 10 轮 |
| 共享表建出来？ | ✅ `users` | ✅ `users_user` |

⚠️ **其中有 1 轮出现过「两个进程都『起来了』、但 RAG 侧库里 0 张表」的异常** ——
**经诊断确认是【脚本时序假象】，不是产品缺陷**：上一轮的 JVM 还没退干净 ⇒
`wait_port` 看到的是**旧进程**在监听（**误判 OK**）⇒ 新 JVM 绑端口失败 ⇒ 没人跑 schema init。
⇒ 处置：脚本加 `wait_port_free`（起之前先确认端口已释放）+ `cleanup` 里 `wait` 到进程真的退出
+ `DROP DATABASE ... WITH (FORCE)`（否则上一轮的连接会让 DROP 静默失败）。
**复测 3/3 轮全绿。** 详见 `docs/Pitfalls/users/_shared.md` 的 **PIT-USERS-SHARED-010**。

**RAG 侧 advisory lock 真的串行化了 —— 时间戳钉死**（2026-10-24 06:11:33~35 实测）：
```
06:11:33.987  user-rpc    schema init starting          ← 先到
06:11:34.308  admin-rpc   acquired advisory lock 8675309
06:11:34.309  admin-rpc   applying db/01_create_user.sql …（全部 4 个）
06:11:34.665  admin-rpc   all 4 scripts applied          ← 释放
06:11:34.659  user-rpc    acquired advisory lock 8675309 ← 正好等到 admin 放锁才拿到
06:11:35.565  user-rpc    all 4 scripts applied
```
⇒ user-rpc 早到 **670 ms**，**被阻塞到 admin-rpc 放锁才拿到** ⇒ 锁是真的，不是「碰巧没撞」。

### 9.2.5 判定

**UNRESOLVED-USERRPC-005 关闭**：两侧在「两个服务并发对同一个空库启动」下**都能起来、表都建齐、0 致命错误** ⇒
**可观察结果等价**（`users_user` 这个**共享表**在两侧都被建出来了）。

⚠️ **但机制不同，差在「能不能保证」而不是「这一次有没有撞上」**：
- **RAG**：advisory lock ⇒ **机制保证**串行化（时间戳已证）。
- **Go**：GORM 的 **check-then-act**（`HasTable` 后再 `CREATE TABLE`，**没有** `IF NOT EXISTS`）⇒
  **并发下理论上会撞**（两个进程都看到「表不存在」⇒ 其一报 `relation already exists`）。
  本轮 **5/5 轮都过了**，但按机制那是**时序侥幸**，**不是保证**。
  ⇒ 这条差异的准确表述是 **IMPLEMENTATION DIFFERENCE（RAG 更强）**，**不是** SEMANTIC MISMATCH ——
  两侧的**对外行为**（表最终都在）一致。
  ⚠️ 既有 Go 侧取证脚本 `tools/difftest/exp-go-concurrent-automigrate.sh` 的文件头**也写明了这一点**
  （它就是为验证「Go 到底会不会撞」而写的），本轮结果与它的结论方向一致。

⚠️ **RAG 侧多建了 2 张 admin 域的表**（耦合是真的）：
**RAG 的 user-rpc 会建 `admin_admin` / `admin_invite`**，而 **Go 的 user-rpc 只建 `users` + `user_avatar_history`**。
⇒ 后果：`user-rpc` 与 `admin-rpc` 的 DDL **被绑在一起** —— 日后 admin 域改 `03_create_admin.sql`，
先启动的 user-rpc 可能建出一个**旧版本**的 admin 表（幂等重放**不会**改已存在的表）。
⇒ 这**不是**本组件的缺陷（表结构仍以 admin 域的 DDL 为准），但**耦合是真的**：
**改 admin 域 DDL 时，要意识到 user-rpc 也会建它。**

⚠️ **给 admin 域会话的提示**：本条的 admin 侧结论由本会话**代跑**
（只运行其二进制与 jar，**未改它的任何代码/台账**）⇒
admin 域台账的 **`UNRESOLVED-ARPC-005`**（`docs/DRIFT/users/admin/rpc.md`）应当据此复核并关闭。
（§5.3：工作树内只写自己域的台账文件 ⇒ 本会话**没有**动 admin 的台账。）

---

## 9.3 UNRESOLVED-USERRPC-004 闭环记录（2026-10-24 · 活体**证伪**了一条旧结论）

> §9 那条 UNRESOLVED 的闭环记录。**这条特别值得单列**：它不是「补上了缺的证据」，
> 而是**证伪了台账里一条已经写了好几轮的结论**（CASE-35 原记 `NO` / 「Go 启动期 fail-fast」）。

### 9.3.1 问题

进程启动时 Redis 不可用，**两侧的失败时机**是否不同？

### 9.3.2 原结论（静态推断，写了好几轮）

> Go：`service/user/user/rpc/internal/svc/servicecontext.go:29` `redis.MustNewRedis(c.BizRedis)`
> 在 `MustNewServer` **之前** ⇒ **启动即失败**；RAG 是懒连接 ⇒ **启动成功** ⇒ **两侧不一致**。

### 9.3.3 活体实验（`tools/difftest/exp-difftest-redis-failfast.sh`）

把**两侧**的 Redis 都指到一个**死端口**（`127.0.0.1:56999`，启动前确认无监听者 ⇒ connection refused），
再起进程，看**到底起没起来**：

| | 指向死端口后的启动结果 |
|---|---|
| Go `go-user-rpc` | ✅ **起来了**（日志原文：`Starting rpc server at 0.0.0.0:19005...`） |
| RAG `user-rpc` | ✅ **起来了** |

⚠️ **为什么用「死端口」而不是「停掉 Redis 容器」**：停容器会**同时影响别的会话**
（本机多个会话共用 Redis）⇒ 死端口只影响本脚本自己起的进程，**零副作用**。
（但要记住：这测的是「**连不上**」；「**连得上但慢**」是另一种故障，**不可互相替代**。）

### 9.3.4 根因：漏读了 go-zero 的 **struct tag 默认值**

go-zero v1.10.2 `core/stores/redis/conf.go`：
```go
RedisConf struct {
    Host     string
    Type     string `json:",default=node,options=node|cluster"`
    ...
    NonBlock bool   `json:",default=true"`      // ← ⚠️ 默认就是 true
    PingTimeout time.Duration `json:",default=1s"`
}
```
而 `go-zero@v1.10.2/core/stores/redis/redis.go:122-148`：
```go
rds := newRedis(conf.Host, opts...)
if !conf.NonBlock {                     // ← 默认 true ⇒ **整个连接检查被跳过**
    if err := rds.checkConnection(conf.PingTimeout); err != nil {
        return nil, errorx.Wrap(err, ...)
    }
}
```
⇒ **`MustNewRedis` 默认根本不去 ping Redis** ⇒ Redis 不可达**不会**让 Go 启动失败。

⚠️ **旧结论错在哪**：它读了 `MustNewRedis` 的**调用点**（`servicecontext.go:29` 在 `MustNewServer` 之前）
和 `MustNewRedis` 的**名字**（"Must" 看起来像「必须成功」），
**却没读 `RedisConf` 的 struct tag 默认值** ⇒ 以为 `NonBlock` 是 `false`（零值）⇒ 以为会做连接检查。
⇒ 这正是 AGENTS.md §3.3.1 那条纪律的**反面**：
**「配置里没写」≠「没开」** —— 在 Go 侧，**默认值藏在 struct tag 里**，
而 `NonBlock` 的默认是 `true`（= **跳过**检查）⇒ 「Must」这个名字**不构成证据**。

### 9.3.5 判定

**UNRESOLVED-USERRPC-004 关闭**：
- ①（id 谁胜出）⇒ **V-006 已关闭**（第四轮，真 PG 实证：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；**永不进 wire**）。
- ②（启动期 Redis 不可用）⇒ **两侧都是「启动成功」⇒ 一致**。

⇒ **CASE-35 由 `NO` 改为 `YES（一致）`**；
⇒ 顺带**少了一条假漂移**（原先被记成「Go 启动失败 / RAG 启动成功」的 OBSERVABLE DIFFERENCE，**实际上不存在**）。

⚠️ **仍然成立的那一半**（**运行期**，不是启动期）：
两侧的 Redis 都是**首次真正被用到**时才会失败 ——
Go `logout` 写黑名单失败 ⇒ `INTERNAL "注销失败,Redis写入异常"`（`logoutlogic.go:40`）；
RAG `UserDomainService.logout:780` ⇒ 同样的 `INTERNAL "注销失败,Redis写入异常"` ⇒ **一致**。
⇒ **失败时机两侧都是「首次使用时」**，不是启动时。

---

## 10. 最终判定（spec §39 · 18 项 checklist）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ⚠️ **不完全一致** | 9 个 RPC 的对外形状一致；3 处 DB 层 + 1 处分支层不一致（V-001~V-006） |
| 2 | 正常路径是否一致？ | ✅ **一致** | 9 个 OP 的 happy path 逐条对照一致（§4.2） |
| 3 | 边界输入是否一致？ | ⚠️ **不完全一致** | trim / clamp / 空串全部一致；**显式空 map 分支不一致**（V-005） |
| 4 | NotFound / Empty / Null 是否一致？ | ⚠️ **不完全一致** | NotFound 一致（响应被丢弃）；Empty 一致；**NULL status 不一致**（V-001）；NULL extra_info 一致 |
| 5 | Error meaning 是否一致？ | ⚠️ **code 一致 / message 部分不一致** | 全部 gRPC code 逐条一致；**4 处** message 因 Go 源码乱码不同（**N-2 已签字**，2026-10-24 双侧活体对拍实测）<br>⚠️ 原写「+ 1 处超时 description（V-007）」—— **V-007 已于 2026-10-26 由 `global` 域销账** ⇒ 超时 description **两侧逐字一致**。⚠️ 那 4 处的**状态码两侧完全相同**，差异只在 `grpc-message` 头的字节<br>🔴 **2026-10-26 第十一轮新增（V-012）**：**wire 面两侧一致，但「超时」这一情形下的**业务日志归因**不一致** —— RAG 记「Redis 写入异常」，客户端收到的是「超时」；Go 两侧一致（**观测面**差异，不改 wire） |
| 6 | Error propagation 是否一致？ | ✅ **一致** | Go 的「resp+err 双返」在 gRPC 层被丢弃 ⇒ 两侧客户端只见 status code |
| 7 | External dependency failure 是否一致？ | ✅ **一致**（超时路径除外） | PG/Redis/bcrypt/snowflake 四类失败两侧同 code 同 message；57014 取消的内层差异被外层 interceptor 掩盖（E-4 已实测闭环）<br>🔴 **2026-10-26 第十三轮（V-013）**：那句「被外层 interceptor 掩盖」**已逐条核到机制** —— Go 的 `service/common/observability/rpc.go:59-72` 在 ctx 取消那一刻就 ready，`select` 几乎总选超时分支 ⇒ **客户端拿到 DEADLINE_EXCEEDED ⇒ 1015**；Go 侧 4 组采样（基线 / 同场对照 / user / admin）响应体**全是** `{"code":1015,"msg":"服务繁忙"}` ⇒ **与 RAG 逐字一致**。⇒ 本域 18 处 `catch (StatusRuntimeException)` 放行分支**不得改回 Go 的 INTERNAL** |
| 8 | Cache hit / miss 是否一致？ | **N/A** | 本组件无缓存 |
| 9 | Transaction boundary 是否一致？ | ✅ **一致** | 4/5 步原子性两侧相同；`[PG]` 真回滚实证；**无 self-invocation** |
| 10 | State transition 是否一致？ | ⚠️ **基本一致，一处相反** | 头像 current 状态机一致、登录三态一致；**`status==NULL` 的 login 分支相反**（V-001） |
| 11 | Side effects 是否一致？ | ⚠️ **不一致（DB 层）** | extra_info 双写 / Redis SETEX 一致；`users_user.id` 取值来源不同（V-006）；孤儿 history 两侧一致 |
| 12 | Ordering 是否一致？ | ✅ **一致** | `create_time DESC` 一致；头像 current 清理顺序一致 |
| 13 | Retry 是否一致？ | **N/A**（两侧都无重试） | — |
| 14 | Timeout scope 是否一致？ | ⚠️ **时长一致 / scope 机制不同 / description 一致** | 2000ms=2000ms（§6.6）；B-1 已签字；✅ description 两侧逐字一致（**V-007 已于 2026-10-26 销账**）|
| 15 | Concurrency semantics 是否一致？ | ✅ **一致**（含共有缺陷） | 两侧都无悲观锁 ⇒ TOCTOU 窗口集合相同 |
| 16 | Authentication / Authorization 是否一致？ | **N/A**（本组件不做鉴权） | uid 是 `int64` 协议字段，无解析步骤 |
| 17 | Serialization semantics 是否一致？ | ⚠️ **wire 一致 / 时间戳不一致** | proto 逐字节一致、jsonb 写入两侧都成功；`create_time` 时区标签不同（V-004） |
| 18 | Lifecycle 是否一致？ | ⚠️ **机制不同，失败语义两处不同** | 建表：幂等重放 vs AutoMigrate（RAG 更强）；**Redis fail-fast 时机不同**、**Snowflake fail-fast 时机不同**（§6.10） |

### 判定

```
SEMANTICALLY NOT EQUIVALENT
```

**依据**：18 项中 4 项「不完全一致」+ 1 项「一致但有一处相反」（第 10 项 `State transition`）
+ 1 项「N/A 但两侧都无缓存」。核心是 **1 项 SEMANTIC MISMATCH（V-001）** 与 **7 项 OBSERVABLE DIFFERENCE**。

**⚠️ 判定的可翻转点（必须一并交给用户裁定，不得由单个 agent 自行决定）**：
若 **UNRESOLVED-USERRPC-001** 证明 V-001 在 RAG 的 DDL 下**永不可达**
（`status BIGINT NOT NULL` + `UserDomainService` 的写入路径永不写 null ⇒ 属实），
则 V-001 降级为潜伏缺陷，最终判定可改为：

```
SEMANTICALLY EQUIVALENT（全部可达路径） + 7 项 OBSERVABLE DIFFERENCE
```

**但反过来**：若决定把 `status` 的 NOT NULL 放开去对齐 Go，**V-001 立刻变成实打实的安全缺陷**。
⇒ **V-001 与 V-002 必须一起判，单独判任一条都会得出错误结论。**

**证据强度的诚实边界（AGENTS.md 硬约束 1~4）**：
本轮 ✅ 真 PG（**16 条**容器测试，**2026-10-26 实跑**）、✅ 静态源码（含 gorm / gorm-postgres 的**消费点**行号）、
❌ **双侧活体对拍 = 缺**。⇒ 本文件里任何 `EQUIVALENT` 结论的强度都是
「**逐行读 Go 消费点得出**，RAG 单侧真 PG 测试只作旁证」，
**不构成 spec §1 意义上的「双侧同时跑的探针」**。这一点不得在下游被读成「已闭环」。

> **2026-10-26 第十二轮补记（守网侧，不改变上面任何判定）**：
> 本轮闭环了 B-1 的**装配层覆盖缺口**（3 条断言 + M1/M2/M3 变异验证）⇒
> 「B-1 缓解链真的装上了」这件事从「一次性端到端实测」升级为「**常驻断言**」。
> ⚠️ **该结论只覆盖 user-rpc** —— `service/common` 的这两个拦截器被**其余 9 个 rpc 组件**共用，
> 而**它们是否都注册了，本轮未盘点**（admin 域已独立核实 `hot-rpc` **未注册** ⇒ 前提不成立已销案
> ⇒ **不是每条链都该有翻译器**，盘点时必须逐个看「该组件有没有 SQL 路径」，**不能一刀切**）
> ⇒ 记为**跨域待办**，请 `global` 域收口时统一盘点。

---

<!-- @fix 2026-10-23 第二轮：DRIFT-USERRPC-V-001 已修（根因比台账更深） -->

## 2026-10-23 第二轮：DRIFT-USERRPC-V-001 已修

> user-rpc 当前：**96 tests / 0 failures / 0 errors**，BUILD SUCCESS（原 91 + 新 5）。

### 🔴 根因比台账记的更深一层（台账只写到分支，没写到根因）

台账记的是「`UserDomainService:190` 的 `!= null && == 1L` 让 NULL 跳过封禁检查」。
实测发现：**即使删掉那个 `!= null`，判据仍然不会触发**。

| 层 | 事实 |
|---|---|
| 台账记录的表层缺陷 | `if (user.getStatus() != null && user.getStatus() == 1L)` ⇒ NULL 时跳过封禁检查 |
| **实际根因** | `UserEntity.status` 声明为 `private Long status = 0L;` |
| 机制 | MyBatis `DefaultResultSetHandler` 在 `value == null && !callSettersOnNulls` 时**跳过 setter**（`callSettersOnNulls` 默认 `false`）⇒ 字段保持初始化值 |
| 结果 | `status IS NULL` 读出来是 **`0L`**，不是 `null` ⇒ `0 != 1` ⇒ 判定「未封禁」⇒ **放行** |

⇒ 台账里 `getUser` 处那段「status=0 意为活跃，把 NULL 默认成活跃是朝错误方向的 fail-open」
的注释，**当时是死代码**：那一段的 `if (user.getStatus() == null)` 同样永远不会触发。
**同一个字段、两条路径、朝两个方向** —— 这条自相矛盾此前无人发现。

### Go 侧依据（声明点 + 消费点）

- 声明点：`service/user/user/rpc/internal/model/data_model.go:11`
  `Status int64 `gorm:"column:status;default:0"`` —— <b>值类型 + 无 not null</b>
- 消费点：`gorm@v1.31.1/scan.go:153` `db.AddError(rows.Scan(values...))`
  → `database/sql` 对「NULL → int64」返回 unsupported 错误
  → `internal/model/user.go:28` `return nil, err`（<b>非</b> `ErrRecordNotFound`）
  → `internal/logic/loginlogic.go:53-57` db_error 支 ⇒ `codes.Internal "DB查询失败"`

⇒ Go 的语义不是「NULL 当 0」，而是「**NULL 读不出来**」。Java 侧必须让 `null` 真传上来。

### 修法（两处，缺一不可）

1. `UserEntity.status` 删除字段初始化值 `= 0L`，改为 `@TableField("status") private Long status;`
2. `UserDomainService#login` 在 mapper 调用<b>之后、密码校验<b>之前</b>显式判 NULL 并抛

**第 2 步刻意抛在 try 内部**：这样它会落进既有的 `catch (Exception e)`，
从而**同时**拿到 `metrics.getLoginDbError()` + `span.recordException`
+ `ErrorDbSelect` 业务日志 + `INTERNAL "DB查询失败"` —— 与 Go 的四条副作用逐条对齐。
若抛 `StatusRuntimeException` 会被 `catch (StatusRuntimeException)` 接走原样重抛，四条副作用全丢。

**删除初始化值不改变写入语义**（两条路径都不依赖它）：

| 路径 | 机制 | 是否依赖 `= 0L` |
|---|---|---|
| INSERT | `UserDomainService.register` 显式 `u.setStatus(0L)`（`:321`） | ❌ 不依赖 |
| UPDATE | `UserMapper#updateUserById` 用 `LambdaUpdateWrapper.set(...)` 逐字段显式 set，<b>不走</b> `updateById(entity)` | ❌ 不依赖 |

### 证据

`UserRpcLoginNullStatusFailClosedTest`（新建，真 PG + Testcontainers，5/5 绿）：

- `loginWithNullStatusFailsClosed` —— NULL ⇒ `INTERNAL` + description 逐字等于 `"DB查询失败"`
- `nullStatusIsDetectedBeforePasswordCheck` —— 用**错误密码** + NULL 组合区分两支
  （Go 的 NULL 扫描失败<b>先于</b>密码错误发生 ⇒ 必须是 INTERNAL 而非 `LoginResp{status=1}`）
- `nonNullStatusBehaviourUnchanged` —— 对照：status=0 成功 / status=1 封禁
- `missingUserStillReturnsStatusOne` —— 对照：用户不存在仍是 `status=1`，未被 db_error 判据吞掉
- `mapperMapsNullStatusToNull` —— **前提断言**：mapper 必须把 NULL 映射成 `null`
  （这条正是本轮第一次跑时红的，它直接证伪了「`!= null` 判据不够」这个假设）

> ⚠️ 本类需要 `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL` 才能复现 Go 的可达性
> —— 因为 RAG 的 DDL 声明 `NOT NULL` 而 Go 没有，这本身就是 `DRIFT-USERRPC-V-002` 记的那条差异。

### ⚠️ admin-rpc 有<b>同款缺陷</b>（跨组件，本轮未改）

`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/UserEntity.java:60` 同样是 `private Long status = 0L;`。
属**另一个组件**，本轮未动；admin 侧不跑 `Login`，暴露面不同（`GetUser` 响应会带 `status=0`）。
⇒ 登记为跨组件待办，**不要**当成「已随 user-rpc 修好」。

> 🟢 **2026-10-27 订正（本节的「缺陷」指认已被撤销）** —— G1 lane
>
> ⚠️ **本节写于 2026-10-23 上午，是那次「误修」期间的判断** ⇒ **已被同日第三轮的双侧活体对拍证伪**：
> - **Go 侧读 `status IS NULL` 得到的是 `0`，不报错**（逐层证据见本文件 `DRIFT-USERRPC-V-002` 的 2026-10-27 订正块
>   ① `gorm@v1.31.1/schema/field.go:616-622` ② `database/sql/convert.go:431-435` ③ 双侧活体 `Go 200 + token`）；
> - ⇒ **admin-rpc 保留 `= 0L` 是「当时正确」的一侧**（与 user-rpc 同形），**不是缺陷** ——
>   这一点 **admin-rpc 自己的台账已明确撤销该指认**
>   （`docs/DRIFT/users/admin/rpc.md` 的 `UserEntity.status` javadoc：「admin-rpc 的同名实体一直保留 `= 0L`（与本类同形）—— 是当时正确的一侧 …该指认已撤销」）；
> - ⇒ **本节保留原文供追溯**（不重写历史），**读本节时以本注记为准**。
> ⇒ ⚠️ 连带：下面 `nullStatusIsDetectedBeforePasswordCheck` 那条守门所依赖的前提（「Go 的 NULL 扫描失败先于密码错误发生」）
> **同样已被证伪** ⇒ 那条用例属于**已撤销的误修**留下的残留，**不应再作为「Go 行为」的判据**。

---

<!-- @fix 2026-10-23 第三轮：双侧活体对拍 + 真 PG 守门（users/user 域） -->

## 2026-10-23 第三轮：双侧活体对拍 + 真 PG 守门

> 本节把本组件的证据强度从「静态源码 + RAG 单侧真 PG」抬到
> **「Go 进程 ↔ RAG 进程 双侧活体对拍（各自的真 PG + 各自的真 Redis）」**。
> 本节同时**推翻了本文件上一节的 3 条结论**（V-004 的归因、CASE-15、CASE-16）。

### 0. 元信息与证据强度

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23**（21:00–21:30 实跑） |
| 组件 | `service/users/user/user-rpc`（本节）· `service/users/user/user-api`（见 [`api.md`](./api.md) 同名节） |
| **证据强度** | ✅ **双侧活体对拍**（Go 进程 + RAG 进程 + 各自真 PG + 各自真 Redis，喂同一串请求逐字比对）+ ✅ **真 PG 守门测试**（Testcontainers） |
| 对拍 harness | 独立容器 `userprobe-postgres`(127.0.0.1:56432) / `userprobe-redis`(127.0.0.1:57379)；端口 Go rpc **19104** / api **19188**，RAG rpc **61210** / api **60900** |
| ⚠️ 为什么不用 `tools/difftest` 的共享资源 | 并行的 **users/admin 会话当时正在跑 `tools/difftest/run.sh`**（占 19004/19094/19095/18888 + `difftest-postgres:55432`）。撞端口会让两边的取证互相污染（实测撞过一次：19004 被占 ⇒ Go rpc 起不来）。⇒ 本轮**另起一套隔离环境**，**不碰**共享容器/端口 |
| 探针脚本 | `/tmp/userprobe/probe.py`（create_time **逐字**）· `probe_auth.py`（claim 语义）· `probe_v005b.py`（update_time 推进）· `ddl_compare.py`（DDL 列级/索引/约束） |
| Go 侧 | 从冻结仓库 `go build` 出的二进制；配置用 `tools/difftest/conf/go/*.yaml` 的**副本**（端口/PG/Redis 换成本轮隔离值）⇒ **冻结仓库一个字节未动**（`git status` 复核：仅 `.DS_Store` 与一份既有未跟踪文档） |
| 基线（修复前） | users-common **11**/0/0/0 · user-rpc **102**/0/0/0 · user-api **69**/0/0/0 |
| 终态（修复后） | users-common **11**/0/0/0 · user-rpc **112**/0/0/0 · user-api **76**/0/0/0 |

> ⚠️ **skipped 纪律（2026-10-23 新立，本轮已逐模块核对）**
> 本轮所有真 PG 用例都带 `@EnabledIfDockerAvailable` ⇒ **无 Docker 时会被整类跳过**，
> 而 reactor 仍报 `BUILD SUCCESS`（`task-rpc` 的 15 条真 PG 测试就是这么被静默跳过的）。
> ⇒ 本轮**逐个模块读了 surefire XML 的 `skipped` 属性**：
> `users-common` / `user-rpc` / `user-api` 三者的 `skipped` **全为 0**。
> 复现：`grep -ho 'skipped="[0-9]*"' service/users/user/*/target/surefire-reports/*.xml`

> ⚠️ **`tools/difftest/diff.py` 测不到 `create_time`**：它的 `Normalizer.walk()`
> 对 `TS_KEYS = {"create_time","update_time"}` **无条件抹平**成 `"<TS>"`
> （`tools/difftest/diff.py:180` + `:530`）⇒ 本轮新写的 `create_time` 逐字探针**不复用**它。

### 1. 本轮确认并修掉的漂移

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **EXIT=1**（2026-10-30 03:53:22 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**） ⏐ ==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源） ⏐ ==> [3/5] 构建（Go 二进制 + RAG jar） ⏐ ==> [4/5] 起两套 user-rpc（Go :19005 / RAG :59211） ⏐ ==> [5/5] 编译并跑双端口对拍；<br>`tools/difftest/run.sh` ⇒ **EXIT=0**（2026-10-30 03:29:45 · 137s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」） ⏐ PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ==> 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

#### DRIFT-USERRPC-V-004（可观察半边）✅ 已修 —— `users_user_avatar_history` 的时间列

**现象（双侧活体对拍，修复前，2026-10-23 21:01）**：

| 字段 | Go | RAG（修复前） |
|---|---|---|
| `UploadAvatarResp.history.create_time` | `"2026-10-23T21:01:48+08:00"` | `""`（**空串**） |
| `GetAvatarHistoryResp.list[].create_time` | `"2026-10-23T21:01:48+08:00"` | `"2026-10-23T21:01:48Z"`（**时刻差 8 小时**） |

**Go 出处**：
- 列类型 —— 声明点 `service/user/user/rpc/internal/model/data_model.go:30-31`
  （`CreateTime/UpdateTime time.Time` + `gorm:"column:…;auto…Time"`，**无 type tag**）；
  消费点 `gorm.io/driver/postgres@v1.6.0/postgres.go:251-256`
  （`case schema.Time:` → 无 precision ⇒ `"timestamptz"`）
- 渲染 —— `service/user/user/rpc/internal/logic/avatarhelpers.go:16-19`
  （`item.CreateTime.Format(time.RFC3339)`）

**RAG 出处（修复前）**：
- `service/users/users-common/src/main/resources/db/02_create_user_avatar_history.sql:30-31` ⇒ `TIMESTAMP`
- `UserAvatarHistoryEntity.java:47,50` ⇒ `LocalDateTime`
- `AvatarDomainService.java:74` ⇒ `RFC3339.format(ct.atOffset(ZoneOffset.UTC))`

**修法**（三处必须同一提交，否则更糟）：
1. DDL → `TIMESTAMPTZ` + 幂等自愈 `ALTER … TYPE TIMESTAMPTZ USING … AT TIME ZONE 'UTC'`
   （`SET TIME ZONE 'UTC'` … `RESET TIME ZONE` 包夹，理由见 `01_create_user.sql:80-101` 的同款记录：
   **裸 ALTER 在重放时会累积平移整列**）
2. 实体 → `OffsetDateTime`
3. 渲染 → **落在进程本地时区**（见 §2 的归因更正）

**守门**：`UserRpcAvatarHistoryTimestamptzParityTest`（**9 条，真 PG**，本轮新建）
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（列类型与可观察半边）** ⇒ **现行态 = ✅ 维持已修**（**DDL 与迁移脚本都在**）。
> - **RAG 侧**：`service/users/users-common/src/main/resources/db/02_create_user_avatar_history.sql:30` = `create_time  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,` · `:31` = `update_time  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP` ⇒ **建表语句已是 TIMESTAMPTZ**；`:87` = `ALTER TABLE users_user_avatar_history ALTER COLUMN create_time TYPE TIMESTAMPTZ USING create_time AT TIME ZONE 'UTC';` · `:88` = 同句对 `update_time` ⇒ **迁移脚本也在**（存量库可改型）。
> - **Go 侧**：`service/user/user/rpc/internal/model/data_model.go:30` = `CreateTime  time.Time `gorm:"column:create_time;autoCreateTime"`` · `:31` = `UpdateTime  time.Time `gorm:"column:update_time;autoUpdateTime"`` ⇒ **无 `type:` tag** ⇒ GORM v2 对 `time.Time` 的 PG 默认类型就是 **timestamptz** ⇒ 两侧同型。
> - ⇒ 维持 ✅ 已修。⚠️ 「**可观察半边**（`create_time` 渲染形态）」本轮**未复跑双侧活体对拍** ⇒ 按 §0 读作在该证据强度下一致。核对日期 2026-10-30。

#### DRIFT-USERRPC-V-009 ✅ 已修（**本轮新发现**）—— 上传响应的 `create_time` 是空串

- **Go 出处**：`service/user/user/rpc/internal/model/user.go:86-95`
  ```go
  history = UserAvatarHistory{ …, IsCurrent: true }
  if err := tx.Create(&history).Error; err != nil { return err }
  ```
  GORM 的 `autoCreateTime/autoUpdateTime` 在 `Create` **之前**把两个字段写成**应用侧时钟**
  （`gorm.io/gorm/callbacks/create.go` 的 `ConvertToAssignments` 用 `stmt.DB.NowFunc()`），
  随 INSERT 一起写库；由于传的是**指针**，**同一个结构体被回填**
  ⇒ `avatarHistoryItemFromModel(&history)` 拿到真实时刻
  ⇒ `UploadAvatarResp.history.create_time` 是非空 RFC3339 字符串。
- **RAG 出处（修复前）**：`UserAvatarHistoryMapper.insertHistory:88-91` 用 `CURRENT_TIMESTAMP`
  （**PG 时钟**）写库，**且不回填实体** ⇒ 返回的实体 `createTime == null`
  ⇒ `AvatarDomainService.fromModel` 不 set ⇒ **wire 上是空串**。
- **修法**：与 Go 同构 —— **应用侧时钟写库 + 同一实体回填**
  （`createAvatarHistory` 里 `OffsetDateTime now = OffsetDateTime.now(); setCreateTime(now); setUpdateTime(now);`
  + INSERT 改绑 `#{createTime}` / `#{updateTime}`）。
  Go 侧 `autoCreateTime` 与 `autoUpdateTime` 在 Create 路径上取**同一个** `curTime` ⇒ 同一个 `now`。
- **守门**：同上 9 条里的 `uploadAvatarResponseCarriesNonEmptyCreateTime` +
  `returnedEntityCarriesTheStoredInstant` + `uploadResponseCreateTimeEqualsLaterRead`。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（上传响应的 `create_time` 是空串）** ⇒ **现行态 = ✅ 维持已修**。
> - **RAG 侧**：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/AvatarDomainService.java:117` = `b.setCreateTime(RFC3339.format(local));` ⇒ **上传响应现在真的写 `create_time`**（`:116` = `OffsetDateTime local = ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();` ⇒ 带偏移量；`:118` 的注释写明「布局在偏移为 0 时输出 `"Z"`、否则 `"+08:00"`，与 Go 的 `"Z07:00"` 逐字符同构」；`:78` = `private static final DateTimeFormatter RFC3339 =`）。
> - **Go 侧**：`service/user/user/rpc/internal/model/user.go`（`AvatarHistoryItem.CreateTime.Format(time.RFC3339)`；RAG 侧对照注释见 `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/AvatarDomainService.java:52` = `createTime = item.CreateTime.Format(time.RFC3339)`）。
> - ⇒ 维持 ✅ 已修。核对日期 2026-10-30。

#### DRIFT-USERRPC-V-010 ✅ 已修（**本轮新发现**）—— 全零值 update 也必须推进 `update_time`

- **现象（双侧活体对拍，2026-10-23 21:25，真进程 + 真 PG）**：

  | UPDATE 请求体 | Go `update_time` | RAG（修复前） |
  |---|---|---|
  | `{}` | **推进** | **不推进** ← 漂移 |
  | `{"extra_info":{}}` | **推进** | **不推进** ← 漂移 |
  | `{"email":…}` | 推进 | 推进 ✅ |

- **Go 出处（消费点，不是声明点）**：`gorm.io/gorm@v1.31.1/callbacks/update.go:235-252`
  ```go
  if field.AutoUpdateTime > 0 && value[field.Name] == nil && value[field.DBName] == nil {
      now := stmt.DB.NowFunc()
      assignValue(field, now)
      set = append(set, clause.Assignment{Column: …, Value: now})   // ← 无条件进 SET
  }
  ```
  ⇒ 即便所有业务字段都是零值，`autoUpdateTime` 字段**仍被加进 SET**
  ⇒ `ConvertToAssignments` 的 set 非空
  ⇒ `callbacks/update.go:74-79` 的 `else { return }`（"空 set 就不发 UPDATE"）**不触发**
  ⇒ **Go 总会发出一条只推进 `update_time` 的 UPDATE**。
  Go 的 `UpdateUserById` 无条件被调用（`updateuserlogic.go:62`），它**不判断**"有没有要改的字段"。
- **RAG 出处（修复前）**：`UserMapper.updateUserById` 的 `if (!any) return 0;` 早退
  ⇒ 不发 UPDATE ⇒ `update_time` 不动。
  ⚠️ **这条早退是「无出处 idiomatization」**（§3.2）：旧注释写「GORM 对全零值 struct 同样不发 UPDATE」，
  **那句没有 Go 出处**，且被本轮双侧对拍证伪。
- **修法**：删掉早退，`update_time` **无条件**进 SET
  ⇒ 发出的 SQL 与 Go 的 `UPDATE users SET update_time = ? WHERE uid = ?` 逐条同构。
- **守门**：`UserRpcContainerWiringTest#zeroValuePatchStillAdvancesUpdateTimeOnly`（**真 PG**，本轮**反转**了旧用例）
  + `UserRpcJsonTextAndAvatarProjectionTest#updateUserById_noBusinessFields_stillAdvancesUpdateTime`。
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（全零值 update 也必须推进 `update_time`）** ⇒ **现行态 = ✅ 维持已修**。
> - **RAG 侧**：`service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserMapper.java:95` = `🔴 <b>2026-10-23（DRIFT-USERRPC-V-010 修复）：{@code update_time} 必须<b>无条件</b>进 SET</b>` ⇒ **修法的意图与来由在代码里被显式钉住**；`:84` = `<p><b>update_time 必须在 SET 子句里显式带上</b>（DRIFT-001 修复）` ⇒ 两轮的修法都在；`:113` = `⇒ <b>Go 总会发出一条只推进 update_time 的 UPDATE</b>` · `:121`-`:122` 逐行列出 `请求体 {}` / `{"extra_info":{}}` 两个原本漂移的格子。
> - **Go 侧**：`service/user/user/rpc/internal/model/data_model.go:15` = `UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`` ⇒ **autoUpdateTime 无条件推进**。
> - ⇒ 维持 ✅ 已修。⚠️ 本轮**未复跑**对应守门 ⇒ 守门存在性已核、**当前全绿未复跑**。核对日期 2026-10-30。

### 2. 🔴 归因更正：偏移量由**进程本地时区**决定，**不是** PG 会话时区

> ⚠️ **本文件上一节 V-004 写的「DSN 钉 `TimeZone=Asia/Shanghai` ⇒ 带偏移」是错的。**
> 本节用两组实测把它推翻并给出正确机制。**下一个 agent 不要照抄旧说法。**

**实测 1 —— Go 侧（把 Go 进程的 TZ 改成 UTC，DSN 里的 `TimeZone=Asia/Shanghai` 原样不动）**：

| Go 进程 TZ | DSN `TimeZone=` | Go 渲染的 `create_time` |
|---|---|---|
| Asia/Shanghai | Asia/Shanghai | `2026-10-23T21:01:48+08:00` |
| **UTC** | Asia/Shanghai（**未改**） | **`2026-10-23T13:02:19Z`** |

⇒ 决定者是**进程本地时区**（Go 的 `time.Local`），**PG 会话时区不参与**。
源码依据：`pgx v5.10.0 pgtype/timestamptz.go:270-276`
```go
tim := time.Unix(…)
if plan.location != nil { tim = tim.In(plan.location) }
```
而 `TimestamptzCodec.ScanLocation` **默认 nil**（`pgtype/timestamptz.go:133-137`）⇒ 落回 `time.Local`；
且 pgx 走**二进制**解码 ⇒ PG 会话时区压根不参与。
（旁证：`pgx@v5.10.0 pgconn/config.go:399-438` 的 `notRuntimeParams` **不含** `timezone`
⇒ DSN 里的 `TimeZone=` 确实被当成**服务器运行时参数**下发 ⇒ 它只影响 PG 的**文本呈现**。）

**实测 2 —— RAG 侧（pgjdbc 42.7.13，独立 JDBC 探针）**：

| PG 会话 TZ | `rs.getObject(1, OffsetDateTime.class)` | `rs.getTimestamp(1)` |
|---|---|---|
| UTC | `2026-01-01T08:00Z`（offset `Z`） | `2026-01-01 16:00:00.0`（JVM 默认时区） |
| Asia/Shanghai | `2026-01-01T08:00Z`（offset `Z`） | `2026-01-01 16:00:00.0`（JVM 默认时区） |

⇒ **pgjdbc 把 `timestamptz` 取成 `OffsetDateTime` 时恒用 UTC**，
与 PG 会话时区、与 JVM 默认时区**都无关**
（MyBatis 的 `OffsetDateTimeTypeHandler` 走的就是 `rs.getObject(i, OffsetDateTime.class)`）。

⇒ **两侧要同形，必须由 RAG 侧显式换算到 `ZoneId.systemDefault()`**：
```java
OffsetDateTime local = ct.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
b.setCreateTime(RFC3339.format(local));
```
⚠️ **不要**在 JDBC URL 上钉 `TimeZone=`：那会让 RAG 依赖**配置**、Go 依赖**进程环境**，
两者在"配置 ≠ 环境"时分叉 —— 那是**制造**一处新漂移。
⇒ **两侧同形的条件是「两侧进程时区相同」**，这与 Go 自身的前提完全一致（Go 也不钉 `TZ`）。

**修复后的双侧活体对拍（2026-10-23 21:21）**：
```
upload 响应 create_time : Go = '2026-10-23T21:21:40+08:00'   RAG = '2026-10-23T21:21:42+08:00'
history 列表 create_time: Go = ['2026-10-23T21:21:40+08:00']  RAG = ['2026-10-23T21:21:42+08:00']
```
⇒ **形态逐字同构**（仅时刻相差 2 秒 —— 两侧是先后探的，不是同一瞬间）。

### 3. 🔴 CASE-15 / CASE-16 被推翻：V-005 的原结论**错误**

> 本文件上一节 §8 的 CASE-15 / CASE-16 判为 `EQUIVALENT`，
> 且 CASE-15 的证据栏写着 `[PG] zeroValuePatchDoesNotUpdate`。
> ⚠️ **那条 `[PG]` 证据是 RAG 单侧的**（只证明 RAG 不发 UPDATE），**没有对照 Go**。
> 本轮双侧对拍把这两条一起推翻了。

**CASE-15**（全字段为空 ⇒ 不发 UPDATE、update_time 不动）⇒ **错**，Go **会**发 UPDATE 并推进 `update_time`。
⇒ 已开 **DRIFT-USERRPC-V-010** 并修复（见 §1）。

**CASE-16**（显式空 map ⇒ Go 写 `{}`、RAG 不写）⇒ **前半句错、后半句对**，但**整条不是漂移**：
- **「Go 写 `{}`」是错的** —— Go **不**写 `extra_info`。实证两条：
  1. **wire 层**（Go 侧独立探针，用**冻结仓库的真实 pb 包** + protobuf-go v1.36.11）：
     `map[string]string{}`（空但非 nil）经 `proto.Marshal` ⇒ **wire 字节为空**（字段缺席）
     ⇒ 对端解出来 `ExtraInfo == nil` ⇒ `updateuserlogic.go:58-60` 的 `in.ExtraInfo != nil` **为 false**
     ⇒ Go 不写 `extra_info`。
     （go-zero 侧也确认了：`{"extra_info":{}}` 经 `mapping.UnmarshalJsonMap` ⇒ `Extrainfo` 是**空但非 nil**，
     而 `{}` ⇒ `Extrainfo == nil` —— 差别只存在于 api 层，**过 wire 后消失**。）
  2. **DB 层**（双侧活体对拍后直查两库）：两侧 `extra_info` 都是 `<NULL>` ⇒ **两侧都没写**。
- ⇒ **V-005（显式空 map ⇒ Go 写 `{}`）判定为「不成立」，予以关闭**；
  真正剩下的可观察差异是 `update_time` 是否推进 ⇒ 那就是 **V-010**（已修）。
- ⇒ **`UNRESOLVED-USERRPC-003`（wire 可达性）就此关闭**：答案是
  「**protobuf3 的空 map 在 wire 上不可表达**（零条目 ⇒ 字段缺席 ⇒ 解出来是 nil）」。

### 4. DDL 列级对照：`UNRESOLVED-USERRPC-006` 关闭（真库对照）

> `tools/difftest/check-ddl-static.py` 的文件头**自陈**只查 `data_type` 与
> `character_maximum_length`，索引 / 唯一约束 / NOT NULL / DEFAULT 标 **NOT CHECKED**
> ⇒ 「该脚本全绿」**不能**读成「DDL 已一致」。
> 本轮改用**真库对照**：两侧各用**自己的建表机制**把表建出来
> （Go 侧 GORM `AutoMigrate` → `go_db.users` / `go_db.user_avatar_history`；
> RAG 侧 `users-common/db/*.sql` 幂等重放 → `rag_db.users_user` / `rag_db.users_user_avatar_history`），
> 再逐列比对 `data_type` / `is_nullable` / `column_default`，并列出两侧索引与约束。

**确认的差异（8 处，去掉表名导致的序列名噪声后 4 项）**：

| 列 | Go | RAG | 判定 |
|---|---|---|---|
| `users.status` | `bigint` / **is_nullable=YES** / default `0` | `bigint` / **is_nullable=NO** / default `0` | 🔴 **DRIFT-USERRPC-V-002 升级为「真库实证」**（此前只有静态源码） |
| `users.extra_info` | **`text`** | ~~`jsonb`~~ → **`text`** | ✅ **DRIFT-USERRPC-V-003 已修**（`ec33159`；修复前为 🔴「真库实证」） |
| `users.create_time` / `update_time` | `timestamptz` / is_nullable=YES / **default `<none>`** | `timestamptz` / is_nullable=YES / **default `CURRENT_TIMESTAMP`** | 🔴 **新发现（V-011）**：Go 的 AutoMigrate **不给**时间列 DEFAULT；RAG 给了 |
| `users_user_avatar_history.create_time` / `update_time` | `timestamptz` / default `<none>` | `timestamptz` / default `CURRENT_TIMESTAMP` | 同上（本轮已修列类型；DEFAULT 差异仍在） |

> **2026-10-23 晚独立复现（换了取证路径，结论一致）**：用
> `go run -overlay` 把探针虚拟挂进真源模块树，直接调真源自己的 `model.InitDB`
> （内含 `AutoMigrate`），并开 PG `log_statement=all` **抓建表语句原文**，
> 得到比 `information_schema` 更硬的一手证据：
> ```sql
> CREATE TABLE "users" ("id" bigserial,"uid" bigint NOT NULL,"username" text,
>   "password" text,"email" text,"status" bigint DEFAULT 0,"score" integer,
>   "extra_info" text,"create_time" timestamptz,"update_time" timestamptz,
>   PRIMARY KEY ("id"),CONSTRAINT "uni_users_username" UNIQUE ("username"),
>   CONSTRAINT "uni_users_email" UNIQUE ("email"))
> ```
> ⇒ **`extra_info text` 逐字确认**；`status` 可空、`create_time`/`update_time` 无 DEFAULT 也逐字确认。
> 探针方法与踩坑 ⇒ [`PIT-USERS-SHARED-003`](../../../Pitfalls/users/_shared.md)。
> ⚠️ **这条复现的价值不在「结论变了」（没变），而在「结论不再依赖推导链」** ——
> 原来的依据是 `serializer:json` ⇒ `DataType=String`（`schema/field.go:195-200`）
> → `Size` 因 `reflect.Map` 不在默认分支而保持 0（`:330-340`）
> → `DataTypeOf` 走 `field.Size > 0` 为假 ⇒ `text`（`postgres.go:245-250`）。
> 推导链每一环都对，但**现在有了一条不依赖它的实证**。

**索引 / 约束对照结论：功能等价，RAG 略强**：

| 语义 | Go | RAG | 判定 |
|---|---|---|---|
| `users.uid` 唯一 | `idx_users_uid`（**唯一索引**） | `users_user_uid_key`（**UNIQUE 约束**） | 等价（PG 用唯一索引实现 UNIQUE 约束） |
| `users.username` / `email` 唯一 | `uni_users_username` / `uni_users_email` | `users_user_username_key` / `users_user_email_key` | 等价 |
| `user_avatar_history.history_id` 唯一 | `idx_user_avatar_history_history_id`（唯一索引） | `users_user_avatar_history_history_id_key`（UNIQUE 约束） | 等价 |
| `user_avatar_history.uid` 索引 | `idx_user_avatar_history_uid` | `idx_users_user_avatar_history_uid` | 等价 |
| `is_current` 索引 | `idx_user_avatar_history_is_current`（**单列**） | `idx_users_user_avatar_history_uid_is_current`（**(uid, is_current) 复合**） | ⚠️ RAG **更强**：实际查询是 `WHERE uid=? AND is_current=true`，复合索引更贴合；Go 的单列索引选择性更差 |
| `NOT NULL`（uid / history_id / avatar_url / is_current / id） | 一致 | 一致 | ✅ 等价 |

⇒ **索引 / 唯一约束 / NOT NULL / DEFAULT 四项已从「NOT CHECKED」升级为「已逐项核对」**。

### 5. 本轮**未**修的项与理由

| ID | 内容 | 本轮结论 | 为什么不动 |
|---|---|---|---|
| **DRIFT-USERRPC-V-002** | `users_user.status` **NOT NULL**（Go 可空） | 🟡 **2026-10-27 订正：DDL 层差异 · 行为层零差异**（Go 读 NULL ⇒ **0**，不报错；RAG NOT NULL ⇒ 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**）⇒ **建议保持 RAG NOT NULL**（⚠️ 尚未销号，须与 G4 共同确认） | 🟡 **真库实证确认**（§4） | ⚠️ **本行原写「动它会立刻激活 admin-rpc 的同款缺陷」—— 该指认已于 2026-10-23 被第三轮双侧活体对拍撤销**（admin-rpc 的 `= 0L` 是**与 Go 同形**的一侧，**不是缺陷**）⇒ **且「放开可空」会制造新漂移**（RAG 读出 null ≠ Go 的 0 ⇒ 方向相反）⇒ **正确处置是保持 NOT NULL**，不是与 admin 侧同改<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡（DDL 层差异 · 行为层零差异 ⇒ 建议保持 NOT NULL）** ⇒ **现行态 = 🟡 维持**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/users-common/src/main/resources/db/01_create_user.sql:39` = `status      BIGINT NOT NULL DEFAULT 0,` · **Go** `service/user/user/rpc/internal/model/data_model.go:11` = `Status int64` + gorm tag `column:status;default:0`（**无 not null tag** ⇒ GORM 建表可空）⇒ 两侧可空性差异仍在。⚠️ **未复跑真 PG 对拍**；**尚未销号**。核对日期 2026-10-30。<br>🟢 **2026-10-30 补记（真 PG 活体实测）**：本轮已复跑 `python3 tools/difftest/check-ddl-static.py --domain users --check-source --indexes` ⇒ **退出码 1**；输出「补充对照 · NOT NULL（可空性）：差异 **1** 处 ⇒ `users_user.status` go=(nullable=YES) / rag=(nullable=NO)」⇒ **本条的可空性差异已被活体实证**（替换上一段的「未复跑真 PG 对拍」）。⚠️ 「行为层零差异」仍未复跑 ⇒ 沿用原结论。|
| **DRIFT-USERRPC-V-003** | `extra_info` `text` ↔ `jsonb` | ✅ **已修**（`ec33159`） | 你随后拍板「一起改成 text」。连带改 `JsonTextTypeHandler` + 去掉 2 处 CAST + 3 条守门（详见 §502）。跨域不对称随之消除<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（`ec33159`）** ⇒ **现行态 = ✅ 维持已修**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/users-common/src/main/resources/db/01_create_user.sql:41` = `extra_info  TEXT,` + `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:115`（`typeHandler = JsonTextTypeHandler.class`）· **Go** `service/user/user/rpc/internal/model/data_model.go:13` = `ExtraInfo map[string]string` + gorm tag `column:extra_info;serializer:json` ⇒ 两侧同型。核对日期 2026-10-30。|
| **DRIFT-USERRPC-V-011（新）** | 时间列 **DEFAULT `CURRENT_TIMESTAMP`**（Go 无 DEFAULT） | 🔴 新发现 | 两侧写入路径都**显式**写这两个列（Go 靠 GORM autoCreateTime；RAG 本轮起靠应用侧时钟）⇒ DEFAULT 只在「有人不写该列」时生效 ⇒ **当前不可观察**。⚠️ 改它要先确认 admin 域与 `users_user` 的写入路径 |
| **DRIFT-USERRPC-V-006** | `id` 的取值来源（Go `bigserial` 序列 vs RAG `IdType.ASSIGN_ID` 雪花） | ✅ **已关闭**（第四轮，真 PG 实证：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；**永不进 wire**） | ⚠️ 本行此前写「仍成立」，是**过期状态**（第四轮已关，第六轮复核才发现汇总表没同步）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已关闭（第四轮，真 PG 实证：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；永不进 wire）** ⇒ **现行态 = ✅ 维持关闭**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:34` = `@TableId(type = IdType.ASSIGN_ID)` ⇒ **雪花 ID 仍在**（`:18` 的类注释明写「`@TableId(type = IdType.ASSIGN_ID)` 主键策略（雪花 ID）取代 JPA 的 `@Id + @GeneratedValue`」）· **Go** `service/user/user/rpc/internal/model/data_model.go:6` = `Id uint64` + gorm tag `primaryKey` ⇒ GORM 对 uint64 主键建 **bigserial 序列** ⇒ 两侧机制不同、但**都自产、都不进 wire** ⇒ 维持关闭。⚠️ 「真 PG 实证 `last_value` 仍是 1」本轮**未复跑** ⇒ 按 §0 读作在该证据强度下一致。核对日期 2026-10-30。|
| **DRIFT-USERRPC-V-007** | 超时的 gRPC `description` 不同（`service/common` 组件） | ✅ **已销账（2026-10-26）** | 由 **`global` 域**修完（落点 `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` 的 `GO_DEADLINE_EXCEEDED_DESCRIPTION`；守门 `RpcInterceptorTimeoutDescriptionGateTest`；探针 `tools/semantic-probe/rpc-timeout/`）⇒ 两侧 description 逐字一致。⚠️ 修前状态保留在 §7 V-007 小节<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已销账（2026-10-26，由 `global` 域修完）** ⇒ **现行态 = ✅ 维持销账**（**修法与守门都还在**）。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:168` = `private static final String GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded";` ⇒ **常量在位**（`:330` = `goStyleStatusMessage(Code.DEADLINE_EXCEEDED, GO_DEADLINE_EXCEEDED_DESCRIPTION)` ⇒ 消费点）· **Go** `google.golang.org/grpc@v1.81.1/rpc_util.go:1100` = `var errContextDeadline = status.Error(codes.DeadlineExceeded, context.DeadlineExceeded.Error())` ⇒ **Go 侧 description 字面量就是 `context deadline exceeded`**（真源 `go.mod:25` pin `grpc v1.81.1`）⇒ 两侧逐字一致 ⇒ 维持销账。守门/探针**文件在位**：`service/common/src/test/java/com/rag/common/observability/RpcInterceptorTimeoutDescriptionGateTest.java` · `tools/semantic-probe/rpc-timeout/`。⚠️ 本轮**未复跑**探针。核对日期 2026-10-30。|
| **DRIFT-USERRPC-V-008** | 死代码 + **伪出处**（3 个异常类 + 2 个 mapper 方法） | ✅ **已修**（`fa2de52`） | 第四轮 §2 已删；且**伪出处是两个，不是一个**（`PasswordMismatchException` 声称的 `ALREADY_EXISTS` 在 Go 恰好相反）。⚠️ 本行此前仍写「仍成立」，属台账脱节，本轮复核更正<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（`fa2de52`）** ⇒ **现行态 = ✅ 维持已修**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/exception/UserExceptions.java:35`（三个原类的**删除说明注释**，**无类声明**）+ `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserMapper.java:186`（两个原 mapper 方法的**删除说明注释**，**无方法定义**）· **Go** `service/user/user/rpc/internal/logic/loginlogic.go`（密码不匹配 ⇒ `return &pb.LoginResp{Status: 1}, nil`，**非异常** ⇒ 伪出处之一）⇒ 维持已修。核对日期 2026-10-30。|
| **DRIFT-USERRPC-V-012（新 · 2026-10-26）** | **超时时的业务日志错误归因**：RAG 记「Redis 写入异常」而客户端收到「超时」；Go 两侧一致 | ✅ **已修（2026-10-27 · G1 lane · 域内一行）** —— 仅**「原始异常被整个吞掉」**这一条闭环（补 `logBusinessErr`）⇒ **wire 面零影响**；⚠️ ②③ 两条架构级**另立项** ⇒ **本条不销** | 三形态 × 双侧 gRPC 直连实测发现（**wire 面两侧一致**：都是 `INTERNAL` + 同一句）：<br>① **RAG 把原始异常整个吞掉**（`catch (Exception e)` 里既没 log 也没挂 cause ⇒ 实测 `cause=null`）⇒ 底层 Lettuce 异常在日志里看不见<br>🔴 **2026-10-26 晚范围收窄**：**只对 `logout` 的 Redis 写路径这 1 处成立**（本类 18 处 `catch (Exception e)` 里其余 17 处**都带 `e` 记了日志**）⇒ 修法收窄为「只补 1 处」；<br>② **B-1 守卫只挡 SQL 路径**（Redis 抛 `QueryTimeoutException` / `RedisConnectionFailureException`，**都不是** gRPC 的 `StatusRuntimeException` ⇒ 守卫永不命中）；<br>③ RAG 的 Redis 写**不绑** gRPC deadline ⇒ 在途写跑满 3s（客户端 2s 已收 `DEADLINE_EXCEEDED`）⇒ 解释「日志晚 1s」。<br>⚠️ **修法不能改 status/description**（会与 Go 不一致）⇒ 只能在 `catch (Exception e)` 里**补一行把原始异常记进日志**（与 admin 域写法对齐）⇒ 域内一行可闭环。<br>⚠️ 与 `global` 域 **E38 第②层**同型；**admin 域已独立确认并登记 `DRIFT-ARPC-010`** ⇒ **三域口径宜一起定** |
| **DRIFT-USERRPC-V-014（新 · 2026-10-27）** | **Redis 超时/断连在 rpc 侧的两条**：① 缺 `connect-timeout` ⇒ 连接超时落 Lettuce 自己的默认 **10s** ≠ Go `DialTimeout` **5s**；② 断连时 Lettuce `DEFAULT` 把命令**排队** ⇒ 挂到 `commandTimeout: 3s` ≫ 本组件 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 1015「服务繁忙」，而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」` | ✅ **已修（2026-10-27 · G1 lane · 域内自闭环）** —— ① 补 `connect-timeout: 5s` ＋ 守门；② 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（`REJECT_COMMANDS`）⇒ 断连时**立即拒命令** ⇒ 走进 `catch (Exception e)` ⇒ **INTERNAL「注销失败,Redis写入异常」** ⇒ 与 Go 同形 | 与 api 侧 `DRIFT-ADMINAPI-012` / `DRIFT-FOLLOW-012` **同机制、不同形态**（api 侧后果 = 503；rpc 侧后果 = **RPC 撞超时**）⇒ 由 G2 在 task/like 域独立发现的同款形态**互相印证**。⚠️ **共享层收编仍归 G4**（现共 **5** 份域内副本 ⇒ 收编时须**一并删掉**，否则双份配置）<br>🟢 **2026-10-27 订正：收编【已完成】** —— **5 份域内副本已全部删除**，共享层那份接管（见 [`vs-common.md` §26](../vs-common.md)）⇒ ⇒ **本段「收编仍归 G4 / 现共 5 份副本」是历史状态，不是现状**|<br>🔴🟢 **2026-10-28 订正：本条的证据强度升【双侧 L4】** —— 「客户端看到 1015 vs INTERNAL」那一格原先只到 **RAG 腿 L1**（源码推断）⇒ ⇒ **本轮真起 gRPC server ＋ 真客户端把 wire 上的 status 抓了下来**：Go `Internal`/1689ms · RAG(A 组) `INTERNAL`/19ms · RAG(B 组·同场反例) **`DEADLINE_EXCEEDED`**/2013ms ⇒ ⇒ **归因也升 L4**（B 组是受控反事实）⇒ ⇒ 逐条 ⇒ [`vs-common.md` §50](../vs-common.md)|
| **UNRESOLVED-USERRPC-004 ②** | 启动期 Redis 不可用：Go `MustNewRedis` fail-fast vs RAG 懒连接 | ✅ **已闭环（2026-10-24，§9.3）** —— **原推断被活体实验证伪**：go-zero `RedisConf.NonBlock` 的 struct tag 默认 `true` ⇒ `MustNewRedis` **不做连接检查** ⇒ **两侧都启动成功** ⇒ **无差异** | 本轮做了「把两侧 Redis 都指到死端口再起进程」的注入实验（`tools/difftest/exp-difftest-redis-failfast.sh`） |
| **UNRESOLVED-USERRPC-005** | `users_user` 建表责任交叉（user-rpc ↔ admin-rpc） | ✅ **已闭环（2026-10-24，§9.2）** —— 双侧对称并发启动实测：两侧都能起来、表都建齐、0 致命错误 | ⚠️ 本行此前写「仍 unresolved」，是**过期状态**（第六轮已关，第七轮复核才发现汇总表没同步）。⇒ **机制不同**（Go 靠 `HasTable` 的时序侥幸 / RAG 靠 advisory lock）**但可观察结果一致**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已闭环（2026-10-24，§9.2）** ⇒ **现行态 = ✅ 维持已闭环**（**机制仍不同、可观察结果仍一致**；本轮**未复跑**双侧对称并发启动 ⇒ 按 §0 读作在该证据强度下一致）。依据：**RAG** `service/users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:53`（幂等重放器）+ `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/bootstrap/UserSchemaBootstrap.java:51` + `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/bootstrap/AdminSchemaBootstrap.java:55`（两侧各自重放）· **Go** `service/user/user/rpc/internal/model/db.go:33` = `db.AutoMigrate(...)` ⇒ **Go 靠 `HasTable` 的时序侥幸、RAG 靠 advisory lock**。harness **文件在位**：`tools/difftest/exp-go-concurrent-automigrate.sh` · `tools/difftest/exp-difftest-concurrent-schema.sh` · `tools/difftest/flyway-concurrent-boot.java` · `tools/difftest/check-flyway-concurrent.sh`。核对日期 2026-10-30。<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/check-flyway-concurrent.sh` ⇒ **EXIT=0**（2026-10-30 03:46:57 · 72s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ follow ⏐ ✅ task ⏐ ⏱  并发组 ['favorite', 'message'] 的起跑时刻：[1793303287034, 1793303287033] （最大偏差 1ms ⇒ 真并发） ⏐ ✅ like ⏐ ✅ message ⏐ ✅ 无差异：各模块的表都建出来了、账本互不覆盖、baseline 落位正确。。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。|

### 5b. 跨域状态更新（2026-10-23 晚）：V-003 的跨域不对称**已消除** ✅

> **本节已被 §502「修复」小节取代结论**，保留全过程供追溯：
> 下面是「admin 先改、user 未改」那个中间态的记录；最终你拍板「一起改成 text」，
> 已由本组件在 `ec33159` 落地 ⇒ **两张表现在都是 `text`，且都与 Go 一致。**

§5 写「⚠️ 需与 admin 域的 `admin_admin.extra_info` 一起判」时，两边都还是 `jsonb`。
**此后 admin 会话单方面判了并已提交**（分支 `align/users-admin`，`c10f639` / `d7b2b45`，
`03_create_admin.sql:54,99`：`extra_info TEXT` + `ALTER … TYPE text USING extra_info::text`，
其台账编号 `DRIFT-ARPC-004①`，并自陈「`users_user.extra_info` 的 DDL 归属 users/user 域」）。

⇒ **当前跨域状态是「不对称」的**：

| 表 | 归属域 | 现状 | 相对 Go（`text`） |
|---|---|---|---|
| `admin_admin.extra_info` | admin | **`text`**（已改已提交） | ✅ 一致 |
| `users_user.extra_info` | user（本组件） | **`jsonb`**（未改） | 🔴 **V-003 仍在** |

⇒ 两侧列类型**一度不一致**（这正是本组件上轮向你提的那个问题）。
当时的处置依据是你已拍板的口径：**V-002 / V-003 / V-011 三条 DDL 一条不动，只取证写台账**。

➡️ **后续**：取证摆上桌后（建表语句级实证 + jsonb 改写字节的 PG 实测），
你改判为「**一起改成 text**」⇒ 本组件已在 `ec33159` 修复，见 §502。
**⇒ 本节记录的「不对称」状态已不存在。**

⚠️ **因此这不是「本组件单方面能定」的项**：把 `users_user` 也改成 `text` 会连带
`JsonbTypeHandler` / `CAST({0} AS jsonb)` / `UserAvatarHistoryMapper:127` /
`UserEntity:113` 共 4 处生产代码 + 5 个测试类一起动（其中
`UserRpcContainerWiringTest:141` 断言的正是「`extra_info` 必须是 jsonb」），
⚠️ **更正本文件早先「会产生同一张 `users-common/db/*.sql` 的双写冲突」的措辞** ——
**不准确**：本组件拥有 `01_create_user.sql` / `02_create_user_avatar_history.sql`，
admin 域拥有 `03_create_admin.sql` / `04_create_admin_invite.sql`，**四个文件两两不重叠**。
测试断言也是各域各断言自己那张表（admin 断 `admin_admin` 是 text，本域断 `users_user` 是 jsonb）。
⇒ **改 `users_user` 是文件级可分离的，不存在 §5.3 的共享文件冲突。**
（本条更正由 2026-10-23 晚的跨域状态复核发现；原措辞把「同目录」误当成「同文件」。）
~~⇒ 需你在三个选项里裁定。~~ **已裁定：改成 text（`ec33159`）。**

### 6. 本轮没做成 / 没验的事（诚实边界）

1. **`UNRESOLVED-USERRPC-007`（9 个 RPC 的字段级对拍）仍然只做了一部分**：
   本轮对拍覆盖的是 **HTTP wire 层的 9 个端点**（`tools/difftest/diff.py --scenario user` 复制件改端口后
   **17 步 / 12 逐字相同 / 5 条按已签字边界断言 / 0 未预期差异**）
   + **2 个 create_time 逐字探针** + **5 个 claim 语义探针**。
   ⇒ **gRPC 层的错误 message 逐字对拍**（`grpc-message-dual-port-runner.java`）本轮**未**跑。
2. **三个 rpc 同库共存**（`message-rpc`/`like-rpc`/`article-rpc` 的 Flyway 账本）**不在本组件范围**，未验。
3. **admin 域**的一切**未审**（属并行会话）。
4. **`extra_info` 的 jsonb↔text** 与 **`status` 的 NOT NULL** 两项**需要跨组件联合决策**，
   本轮只做取证、**未动手**（§5）。

### 7. 本轮守门测试清单（全部真 PG，skipped=0）

| 测试类 | 条数 | 覆盖 | 真 PG |
|---|---|---|---|
| `UserRpcAvatarHistoryTimestamptzParityTest`（**新建**） | **9** | V-004 可观察半边 + V-009 | ✅ Testcontainers `postgres:16-alpine` |
| `UserRpcContainerWiringTest`（**反转 1 条**） | 12 | V-010 | ✅ |
| `UserRpcJsonTextAndAvatarProjectionTest`（**反转 1 条**） | 4 | V-010 | ❌ mock 数据层（**但**断言的是 SQL 片段形态） |
| `UserDomainServiceTest`（**反转 2 条 + 新增 1 条**） | 44 | V-004 渲染 | ❌ 纯内存 |
| `UserRpcUsersUserTimestamptzParityTest`（既有） | 6 | `users_user` 时间列 | ✅ |
| `UserRpcLoginNullStatusFailClosedTest`（既有） | 5 | V-001 | ✅ |
| `UserRpcSchemaTextColumnTest`（既有） | 4 | R3 列宽 | ✅ |
| **合计 user-rpc** | **112** | | **skipped = 0** |


---

<!-- @fix 2026-10-23 第四轮：V-006 关闭（真 PG）· V-008 已修（删死代码 + 揭出第二个伪出处） -->

## 2026-10-23 第四轮：V-006 关闭 · V-008 已修

> 本轮把 §7 里两条「静态推断 + 未实测」的残留项变成实证结论。
> **没有引入任何新的 Go↔RAG 行为改动** —— 两项都不影响 wire。

### 1. `DRIFT-USERRPC-V-006` ✅ **关闭** —— `id` 取值来源不同，但**永不进 wire**（真 PG 实证）

台账上一轮把它列为「仍成立，wire 零影响；⛔ 本轮**未**实测」。
本轮补上真 PG 证据，并回答了 §9 `UNRESOLVED-USERRPC-004①` 追问的那个问题
（`@Options(useGeneratedKeys=true)` 与 `IdType.ASSIGN_ID` 谁胜出）。

**Go 侧（消费点，非声明点）**：

| 位置 | 事实 |
|---|---|
| `service/user/user/rpc/internal/model/data_model.go:6` | `Id uint64 \`gorm:"primaryKey"\`` —— **无 `autoIncrement` 标签** |
| 同上 `:23` | `UserAvatarHistory.Id uint64 \`gorm:"primaryKey"\` —— 同样无标签 |
| `registerlogic.go:73` | 只赋 `Uid`，**从不赋 `Id`** ⇒ 恒零值 |
| `model/user.go:86-94` | 构造 `UserAvatarHistory{HistoryID, Uid, AvatarURL, ContentType, SizeBytes, IsCurrent}` —— **不含 `Id`** |

⇒ GORM 约定「整型单列主键 ⇒ 视作自增」，零值主键**不入 INSERT**，由 DDL 的 `BIGSERIAL` 序列填。

**RAG 侧**：`UserEntity.id` / `UserAvatarHistoryEntity.id` 均为 `@TableId(type = IdType.ASSIGN_ID)`
⇒ MyBatis-Plus 生成雪花并**显式写进 INSERT**。

**真 PG 实测结论（`UserRpcPrimaryKeySourceParityTest`，4 条，skipped = 0）**：

| 事实 | 实测值 |
|---|---|
| `users_user.id` | **雪花量级**（> 1e18），不是 1,2,3… |
| `users_user_id_seq.last_value` | **仍为 1** ⇒ 序列**根本没被消耗** ⇒ `ASSIGN_ID` 胜出，`useGeneratedKeys` 无效 |
| `users_user_avatar_history.id` | 同上，雪花量级 |
| `users_user_avatar_history_id_seq.last_value` | 仍为 1 |

⇒ **台账那个悬而未决的「谁决定 id」有了确定答案：雪花胜出，bigserial 序列是死的。**

**为什么这条不构成可观察差异（关键，也是关闭的依据）**：

1. `users_user.id`：proto 的 `UserInfo` 只有 `uid`，**没有 id**（`users.proto:9-15`）；
2. `users_user_avatar_history.id`：proto 的 `AvatarHistoryItem.id` 虽然叫 id，
   但 Go 在 **`avatarhelpers.go:22` 填的是 `item.HistoryID`**（雪花**字符串**），
   **不是 DB 主键**。

⇒ 两个 `id` 列**永远不进入任何 proto 字段**，取值来源不同**在 wire 上不可观察**。
**判定：`NOT OBSERVABLE`（可关闭）**，但保留本节记录：若将来 proto 新增 `id` 字段，本条立即复活。

**守恒断言（新增，防「只靠注释」）**：第 4 条用例**直接读 `users.proto` 源文件**，
断言 `UserInfo` 不含 `int64 id`、`AvatarHistoryItem` 的 id 是 `string id = 1` 且不含 `int64 id`。
⇒ proto 一改，这条用例立刻红。

### 2. `DRIFT-USERRPC-V-008` ✅ **已修** —— 删死代码；且**伪出处是两个，不是一个**

台账只点名了 `TokenBlacklistedException`。
本轮按 §3.1「**不要拿台账当基准**」逐个回到 Go 原文复核，**发现了第二个假出处**：

| 类 | 台账怎么说 | **Go 原文实际** | 判定 |
|---|---|---|---|
| `TokenBlacklistedException` | 「对应 Go 端 LogoutLogic：token 在黑名单中 → `Status.PERMISSION_DENIED`」 | **PERMISSION_DENIED 在注销链上根本不存在**：<br>`user/api/.../user/logoutlogic.go:34-57`（全文 58 行）只判 `token == nil`；<br>`user/rpc/internal/logic/logoutlogic.go:30-43`（全文 44 行）只判 `in.Token == ""`，之后直接 `Setex` 进 Redis | 🔴 **伪出处** |
| `PasswordMismatchException` | 「对应 Go 端 password 不匹配抛 `ALREADY_EXISTS`（Register 密码生成失败 / Login 密码错）」 | **Go 恰好相反**：<br>`loginlogic.go:59-64` → `return &pb.LoginResp{Status: 1}, nil`（**err 为 nil，不是异常**）<br>`registerlogic.go:56-62` → `status.Error(codes.Internal, "密码生成失败")`（**是 Internal，不是 AlreadyExists**） | 🔴 **伪出处（台账漏记）** |
| `UsernameExistsException` | 「对应 Go 端 RegisterLogic W1：用户名已存在 → `Status.ALREADY_EXISTS`」 | `registerlogic.go:48` → `status.Error(codes.AlreadyExists, "用户名已存在")` ✅ | ⚪ **出处准确但零消费者** |

**处置**：三个类**全部删除**（含 `UserDomainService` 的两条未用 import），
`UserMapper.selectCurrentByUid` / `findCurrentAvatar` 两个零消费者方法**一并删除**，
并删掉随之失效的 `java.util.Optional` import。

**为什么删而不是留**（§3.2 / §3.4.1 D-2 先例）：
零消费者的代码不能算已对齐；更要命的是那两个伪出处**指向了真实的 Go 文件、也编造了看起来合理的行号**，
下一个 agent 会据此「恢复」两个 Go 从未有过的错误码 —— 把潜伏的假结论**扶正成行为回归**。
删除理由与 Go 行号已写进 `UserExceptions.java` 的注释，不留在台账里就算没了。

**顺带修掉的第三处文档缺陷**（§5「发现明显破损就顺手修」）：
`UserMapper.findOneByUserName` 的 javadoc 写「用 `Optional` 包装…调用方应 `.orElseThrow(...)`」，
但实现返回 `UserEntity`、找不到时**在方法内直接抛** —— javadoc 描述的是另一个 API。
已按实现改正（与 Go 的 `(user, err)` 二元组同构：「找不到」是 error，不是值）。

### 3. 本轮回归

| 模块 | 结果 |
|---|---|
| `user-rpc` | **116** tests / 0 failures / 0 errors / **skipped = 0**（删除死代码后用例数**未减少** ⇒ 反证它们确实零消费者） |
| 新增 `UserRpcPrimaryKeySourceParityTest` | **4** / 0 / 0 / 0 |

### 4. ⚠️ 本轮踩到的一个**共享基础设施**隐患（不属于语义漂移，但会让别的会话假红）

`application.yaml:81` 把 gRPC 端口**钉死在 49210**。本轮跑新测试时该端口被
**Docker（另一个并行 worktree 会话的栈）**占着 ⇒ `BindException: Address already in use`
⇒ **整个 `ApplicationContext` 起不来**，4 条用例全部变成
`ApplicationContext failure threshold exceeded` —— **一条真实断言都没跑到，却显示为 4 errors**。

处置：新测试用 `properties = "grpc.server.port=0"`（内核分配临时端口）。
⚠️ 本测试只关心 DDL 与主键来源，**不需要真的监听端口**。
⇒ 详见 [`../../../Pitfalls/users/_shared.md`](../../../Pitfalls/users/_shared.md)。

---

<!-- @fix 2026-10-23 第五轮：§9 UNRESOLVED 状态复核（台账 vs 实际） -->

## 2026-10-23 第五轮：§9 `Unresolved` 表**状态复核**（台账 vs 实际）

> **背景**：§9 那张表是**首轮**（2026-10-23 02:00）写的，
> 而第二轮 / 第三轮**已经关掉其中 4 条却没回填** ⇒ 表与实际脱节。
> 本节是**权威的状态更正**，§9 原表保留作审计轨迹。

### 复核方法

不看 §9 写了什么，**只看证据是否真实存在**：
① 台账后文是否已声明关闭；② 对应的测试类/实验是否**真的在仓里**；
③ 引用的库源码行号是否支持该结论。

### 复核结果：**4 条已关但表里仍写 open**

| # | §9 写的 | **实际** | 证据（为什么判定为已关） |
|---|---|---|---|
| **UNRESOLVED-USERRPC-001**<br>V-001 端到端可达性 | open | ✅ **已关** | §9 要求「在 RAG 库 `DROP NOT NULL` + 插 NULL 行」。<br>**该实验已真做**：`UserRpcLoginNullStatusReadsAsZeroTest.java:116` `void dropNotNullAndSeed()`、`:122` `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL`、`:125` `INSERT … status`(NULL)，真 PG Testcontainers；`:154` `loginWithNullStatusSucceedsLikeGo`、`:171` 反向对照组 |
| **UNRESOLVED-USERRPC-002**<br>V-004 时区偏移量 | open | ✅ **已关** | 第三轮 §2 用**两组实测**推翻旧说法并给出机制：偏移量由**进程本地时区**（Go `time.Local`）决定，**PG 会话时区不参与**（pgx 走二进制解码）。附 `pgtype/timestamptz.go:133-137` `ScanLocation` 默认 nil、`:270-276` 回落到 `time.Local` 的源码依据 |
| **UNRESOLVED-USERRPC-003**<br>V-005 wire 可达性 | open | ✅ **已关** | 第三轮 §3 已明写「就此关闭」，答案是「**protobuf3 的空 map 在 wire 上不可表达**（零条目 ⇒ 字段缺席 ⇒ 解出来是 nil）」。§9 表未同步 |
| **UNRESOLVED-USERRPC-006**<br>索引/唯一约束/NOT NULL/DEFAULT | open | ✅ **已关** | 第三轮 §4 用**真库逐列对照**（`information_schema`）完成，给出确认差异表 + 索引/约束等价表。<br>⚠️ 本轮**重新审了这条的证据强度**：它是 2026-10-23 当天的真实执行、有具体输出，**符合 §6.2 状态表纪律** ⇒ 维持「已关」，**我先前的怀疑不成立，特此更正** |

### 复核结果：**确实仍 open 的 3 条** ⇒ ⚠️ **2026-10-24：本节 3 条已全部闭环，下表为历史留档**

| # | 为什么仍 open（**历史**） |
|---|---|
| `UNRESOLVED-USERRPC-004` ② | 启动期 Redis 不可用时两侧的失败时机（Go `MustNewRedis` fail-fast vs RAG 懒连接）。<br>**①（id 谁胜出）已由本轮 V-006 关闭**（真 PG：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出）。<br>② ✅ **已于 2026-10-24 闭环并**证伪原推断**（§9.3）：go-zero `RedisConf.NonBlock` 的 struct tag 默认 `true` ⇒ `MustNewRedis` **不做连接检查** ⇒ 活体实测**两侧都启动成功** ⇒ **无差异**。本行原写「需注入实验，未做」是**过期状态** |
| `UNRESOLVED-USERRPC-005` | `users_user` 建表责任交叉（user-rpc ↔ admin-rpc）。**跨组件**：必须两个 rpc **同库并发启动**才能验，单侧 harness 证明不了。<br>✅ **已于 2026-10-24 闭环（§9.2）**：造了**双侧对称**的 harness（Go 两个 rpc 同库 / RAG 两个 rpc 同库，各起 9 轮）⇒ 两侧都起来、表都建齐、0 致命错误<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已于 2026-10-24 闭环（§9.2）** ⇒ **现行态 = ✅ 维持已闭环**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:109`（`applyAll` 消费点）+ `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/bootstrap/UserSchemaBootstrap.java:51` · **Go** `service/user/user/rpc/internal/model/db.go:33` = `db.AutoMigrate(&User{}, &UserAvatarHistory{})` ⇒ 两侧建表责任交叉的事实仍在、但**机制不同**（Go 时序侥幸 vs RAG advisory lock）⇒ 维持闭环。⚠️ **双侧对称并发启动本轮未复跑**（需真 PG）。核对日期 2026-10-30。|
| `UNRESOLVED-USERRPC-007` | 9 个 RPC 的 **gRPC 层错误 message 逐字对拍**。第三轮 §6.1 明写「`grpc-message-dual-port-runner.java` 本轮**未**跑」；已覆盖的只是 HTTP wire 层。<br>✅ **已于 2026-10-24 闭环（§9.1）**：43 场景双侧活体对拍（9 RPC × 字段级 / `grpc-message` 逐字 / 边界输入）⇒ 39 等价 + 4 处差异（**全部**是 N-2 已签字的 Go 源码乱码）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已于 2026-10-24 闭环（§9.1，43 场景双侧活体对拍：39 等价 + 4 处差异全部是 N-2 已签字的 Go 源码乱码）** ⇒ **现行态 = ✅ 维持已闭环**。依据：**探针与 runner 文件在位** ⇒ `tools/difftest/exp-difftest-grpc-user9.sh`（10446 字节）· `tools/difftest/grpc-user9-dual-port-runner.java`（40043 字节）⇒ **不是「引用的守门/实测不存在」那种假闭环**。⚠️ **诚实边界**：本轮**未复跑**这 43 场景（需真进程 + 真 PG + 真 Redis）⇒ 「39 等价 + 4 处差异」这个**具体数字**本轮**未重新取证**，按 §0 读作在该证据强度下一致；**文件在位 ≠ 本轮跑过**。核对日期 2026-10-30。<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **EXIT=1**（2026-10-30 03:53:22 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**） ⏐ ==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源） ⏐ ==> [3/5] 构建（Go 二进制 + RAG jar） ⏐ ==> [4/5] 起两套 user-rpc（Go :19005 / RAG :59211） ⏐ ==> [5/5] 编译并跑双端口对拍。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。|

### ⚠️ 顺带查出的一处**台账事实错误**（与 AGENTS.md 已记的 `FlywayLedgerIsolationTest` 同型）

§7 守门测试清单与第三轮 §7 都写着 **`UserRpcLoginNullStatusFailClosedTest`（5 条）**。

**该测试类在仓内不存在。** 实际类名是：

| 台账写的 | 实际 |
|---|---|
| `UserRpcLoginNullStatusFailClosedTest`（5 条） | **`UserRpcLoginNullStatusReadsAsZeroTest`**（**8** 条） |

⇒ 同一条结论（V-001 已修）在文档里被写成了一个**不存在的类名 + 错误的条数**。
这与 AGENTS.md §6.3 警告的「多处声称由 `FlywayLedgerIsolationTest` 负责，**该测试类全仓不存在**」是**同一类病灶**。
⇒ 已在 `UNRESOLVED-001` 的证据栏写明正确类名；**后续引用一律以本节为准**。

### 本节不改变任何代码

纯台账状态更正，**未改动任何实现**。

---

## 2026-10-24 第十轮：跨组件面登记 —— 「Redis **主机名解析失败**」在 **user-rpc 侧**（**本轮未测 ⇒ 不下结论**）

> ⚠️ **本节已被 2026-10-26 的实测取代** —— 原文保留供追溯。
> ⇒ **实测结论见下一节「第十一轮」**（三形态 × 双侧 rpc 直连）。
> ⇒ 一句话预告：**015 在 rpc 侧不成立**（两侧 gRPC code 全同）；
> 但**测出了另一条东西** —— RAG 的**业务日志错误归因与客户端所见不一致**（Go 一致）⇒ 立 **`DRIFT-USERRPC-V-012`**。

> 触发：本轮在 **user-api** 侧立了 `DRIFT-USERAPI-015`（见 [`user/api.md`](./api.md)）——
> 双侧活体实测发现「Redis 主机名**解析不出来**」时 **RAG 的失败耗时贴着 3s 预算** ⇒ 200/503 摇摆。
> ⇒ 本节只登记**同一故障形态在 user-rpc 侧的面** —— **本轮未测，故不下结论**。

### 事实（已核实到文件与行号）

| 项 | 事实 | 出处 |
|---|---|---|
| user-rpc **写**黑名单 | `user:jwt_blacklist:<token>` | `UserDomainService.java:772-775`（`bizRedis.opsForValue().set(key, "banned", ttl)`）|
| api 侧**读**同一个 key | `user:jwt_blacklist:<token>` | `JwtAuthInterceptor`（见 api.md §4 #38）|
| user-rpc 的 Redis 配置 | **与 user-api 同款**：`host: ${REDIS_ADDR:redis}` + `timeout: 3s` | `service/users/user/user-rpc/src/main/resources/application.yaml:40-45` |
| 写失败的行为 | `catch (Exception e)` ⇒ `Status.INTERNAL`「注销失败,Redis写入异常」；<br>`catch (StatusRuntimeException e)`（含 deadline 类）**上抛、不改写** | `UserDomainService.java:774-781` |

⇒ **api 读 / rpc 写同一个 key** ⇒ 这是 AGENTS.md §3.5 意义上的**跨组件不变量**
（与「建表责任交叉 · client 契约」同型）⇒ **不属于任何单个组件**。

### 待审（本轮**未测**，故**不下结论**）

1. user-rpc 是 **gRPC**（无 HTTP 3s 预算），但它有 gRPC deadline（`checkDeadline()`）
   ⇒ **「Redis 命令超时 3s」与「gRPC deadline」是否同刻**？
   ⇒ 若同刻，写失败可能表现为 `INTERNAL`（"注销失败,Redis写入异常"）**或** `DEADLINE_EXCEEDED`
   ⇒ **形状是否摇摆**（与 api 侧 015 同型）？⇒ **未测**。
2. 写失败 ⇒ **黑名单没写进去** ⇒ 该 token **仍然有效**；
   同时 api 侧读取也失败（fail-open）⇒ 两侧一起「失效」
   ⇒ **这个「一起失效」是不是 Go 侧同款**（Go 的 `logoutlogic` 写失败时的形状）？⇒ **未审**。
3. ⚠️ **不要**直接套用 api 侧的结论 —— AGENTS.md §3.1 明确禁止
   「拿『现有 RAG 代码』或另一域的结论当对照基准」。

### 复现入口

`tools/difftest/exp-difftest-redis-neverup.sh`（`REDIS_HOST=redis`）——
该脚本目前**只探测 api 侧** ⇒ rpc 侧的对应面**需要另加一个探测点**
（logout 的响应形状 + 耗时），本轮**未加**。

---

## 2026-10-26 第十一轮：015 的 **rpc 侧对应面** —— ✅ **实测完毕**（三形态 × 双侧 rpc 直连）

> 上一节的三条「待审」全部有答案了。**015 的影响面由此收窄到 api 侧**。
> ⚠️ 本轮**未改动任何实现**，只加了两个**只读探针/脚本** + 回写台账。

### 测法（为什么必须直连 gRPC）

api 侧有 3s HTTP 预算：RAG 的**黑名单查询**自己就要 ~3s ⇒ 请求**根本走不到** logout 的 rpc 调用
⇒ 经 api 观测 rpc 侧会被 api 侧预算**遮蔽** ⇒ **只能直连 gRPC**。

| 构件 | 说明 |
|---|---|
| 客户端 | `tools/difftest/redis-logout-rpc/go` —— go.mod 里 `replace sea-try-go => <冻结仓>`，直接 import **真源生成的 grpc stub**（`sea-try-go/service/user/user/rpc/pb`，不在 `internal/` 下）⇒ **两侧用同一份客户端代码** ⇒ wire 契约一致 ⇒ 结果可直接比 |
| 构建设置 | **CGO_ENABLED=0**（与真源二进制一致 —— `go version -m /tmp/difftest/bin/go-user-rpc` 实测为 `CGO_ENABLED=0`）⇒ 纯 Go 解析器 ⇒ DNS 形态的耗时可比 |
| 编排脚本 | `tools/difftest/exp-difftest-redis-neverup-rpc.sh`（起两侧 user-rpc + 直连 Logout ×3 轮）|

### 三形态 × 双侧结果（**本机实测**，2026-10-26）

| 形态 | 探针看到的 Go | 探针看到的 RAG | code 是否同形 |
|---|---|---|---|
| **拒连**（`127.0.0.1:56391` 没人监听） | `Internal` / `注销失败,Redis写入异常` / **1729·1714·1758ms** | `Internal` / `注销失败,Redis写入异常` / **210·5·4ms** | ✅ **同** |
| **解析失败**（`redis:56391`，生产默认形态） | `Internal` / `注销失败,Redis写入异常` / **1836·1804·1749ms** | `Internal` / `注销失败,Redis写入异常` / **336·103·88ms** | ✅ **同** |
| **挂起**（TCP 连上但**永不回包** ⇒ 命令超时路径） | `DeadlineExceeded` / `context deadline exceeded` / **2018·2002·2006ms** | `DeadlineExceeded` / **`context deadline exceeded`** / **2113·2008·2003ms** | ✅ **code 与 description 两侧逐字一致** ⇒ **V-007 已闭环**（2026-10-26 23:31 · **合回后本域双侧活体**）<br>⚠️ **历史留痕**：RAG 侧那一格**曾经**是 `rpc timeout after 2000ms`（合回前） |

⇒ ✅ **结论一：`DRIFT-USERAPI-015` 在 **rpc 侧不成立** —— 三种形态下两侧 gRPC **code 全部相同**。**
⇒ ⇒ **015 的影响面收窄为「仅 api 侧」**（api 侧的 3s HTTP 预算 vs Redis 3s command timeout 那一格）。

> **📌 为什么 Go 侧是 ~1.7~1.8s 而不是 ~3s？（从 modcache 逐条核实）**
> `go-redis@v9.20.0/options.go` 的 `Options.init()` 默认值：**`DialTimeout=5s` · `DialerRetries=5` · `DialerRetryTimeout=100ms` · `ReadTimeout=3s`**。
> ⇒ Go 的失败延迟 = **5 次拨号尝试**（DNS 解析 + 100ms 重试间隔）⇒ **~1.7~1.8s** ⇒ 日志里的 `failed to dial after 5 attempts` 正是这条。
> ⇒ 而 **RAG 侧只用了 ~0.005~0.34s**（同形态）⇒ **两侧的失败耗时差一个数量级**（**形状相同、耗时不同**）。
> ⚠️ **这个差距本身不是漂移**（两侧 code 与 message 全同），但它解释了 §「消费方不对称」：Go 的 2s deadline 只剩 **~200~300ms 余量**，
> ⇒ **一旦 DNS 解析慢过 2s，Go 侧就会从 `INTERNAL` 翻成 `DEADLINE_EXCEEDED`，而 RAG 侧仍是 `INTERNAL` ⇒ 分叉方向会反转** —— 属**潜在脆弱性**，本轮不立条目。

### 上一节「待审」三问的答案

1. **「Redis 命令超时 3s」与「gRPC deadline」是否同刻？** ⇒ **不同刻，且 RAG 侧是 deadline 必然先到**：
   - Go：`SetexCtx(l.ctx, ...)` **绑了** handler ctx 的 2000ms deadline ⇒ **但 ctx 的感知范围只到「取连接 + 拨号」这一段**：`internal/pool/pool.go:1099-1104`（`waitTurn` fast path **无条件先查 `ctx.Done()`**）＋ `:837`（`getConn` **必调** `waitTurn`）＋ `:1113`（slow path 的 `semaphore.Acquire(ctx, …)` 同样绑 ctx）＋ `:589-591`（`newConn` → `dialConn(ctx)` 拨号也绑 ctx）；⇒ **一旦连接已取到、进入 socket 读写，`redis.go:1027-1032` 的 `c.context(ctx)` 就把 ctx 换成 `context.Background()` ⇒ 该段只剩固定 3s `ReadTimeout`**（`options.go:174` 零值；go-zero `core/stores/redis/redisclientmanager.go:32-40` **未设** `ContextTimeoutEnabled`）⇒ ⇒ **本条实测形态（Redis 拒连/解析失败）里，Go 侧是 ~1.7~1.8s 拨号失败（见本节上一段的 5 次尝试换算）⇒ 早于 2s ⇒ 不是「2s 被 ctx 掐断」** ⇒ handler 返回 `INTERNAL` ⇒ **< 3s** ⇒ 与拦截器不同刻（挂起形态另算）。
   - RAG：Redis 写**只受 `spring.data.redis.timeout: 3s` 约束、不受 gRPC deadline 约束** ⇒ **拦截器的 2s 必然先到** ⇒ 客户端拿到 `DEADLINE_EXCEEDED`。
   ⇒ **形状不摇摆**（上一节的担心不成立）—— 因为两侧的「谁先到」是**各自确定**的。
2. **「写失败 ⇒ 黑名单没写进去 ⇒ token 仍有效；api 侧读取也 fail-open ⇒ 两侧一起失效」是不是 Go 同款？**
   ⇒ ✅ **是同款**：Go 的 `logoutlogic.go:37-41` 写失败也返回 `INTERNAL`「注销失败,Redis写入异常」，也**没写进黑名单**；
   Go 的 api 侧黑名单查询失败也 **fail-open**（`checkblacklistmiddleware.go:35-36`：`err == nil && exists` 才拦）⇒ **两侧一起「失效」** ⇒ **同形，无漂移**。
3. **「不要拿另一域/另一面的结论当基准」** ⇒ ✅ 本轮**两侧都实测**了，结论只对这一面成立（api 面仍由 `DRIFT-USERAPI-015` 独立持有）。

### 🆕 测出的**另一条**东西 ⇒ `DRIFT-USERRPC-V-012`（观测面差异 · 业务日志的错误归因）

**在「挂起」形态下，两侧**业务日志**说的失败原因不一样**：

| | Go | RAG |
|---|---|---|
| 客户端收到 | `DEADLINE_EXCEEDED` / `context deadline exceeded` | `DEADLINE_EXCEEDED` / `rpc timeout after 2000ms`（V-007 合后同）|
| **业务日志的失败原因** | `business_error` · `error_reason = "context deadline exceeded"` · `file_line = logoutlogic.go:39` | `ERROR … logout RPC failed: Status{code=INTERNAL, description=注销失败,Redis写入异常}` |
| 一致性 | ✅ **日志与客户端所见一致** | ❌ **日志说「Redis 写入异常」，客户端看到的却是「超时」** |

**根因（已定位到行，并经异常继承链 + 实测日志双重核实）**：
- Go：`SetexCtx(l.ctx, …)` —— **ctx 带 deadline** ⇒ go-redis **在 2s 取消在途命令** ⇒ handler 立刻返回 ctx 错误 ⇒ 日志与客户端一致。
  ⚠️ 且 Go **把原始 err 记进了日志**：`logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)`（`logoutlogic.go:39`）
  ⇒ 实测日志原文就是 `"error_reason":"context deadline exceeded"` ⇒ **「为什么是超时」这个信息被保留下来了**。
- RAG：`UserDomainService.logout` 的 catch 链是
  ```java
  } catch (StatusRuntimeException e) {   // ← B-1 守卫：只挡 SQL 路径
      throw e;
  } catch (Exception e) {
      throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException();
      //                                     ↑ 原始异常 e 被整个吞掉：既没 log，也没挂成 cause
  }
  ```
  ⇒ **三条并存的事实**：
  1. **原始异常被丢弃** —— `asRuntimeException()` 造出的 `StatusRuntimeException` **cause=null**
     ⇒ 实测日志原文就是 `Status{code=INTERNAL, description=注销失败,Redis写入异常, cause=null}`
     🔴 **2026-10-26 晚 范围收窄（`users/admin` 域回信触发，本域独立复核）**：
     本条**只对 `logout` 的 Redis 写路径这 1 处成立**，**不是** `catch (Exception e)` 的普遍形态 ——
     本类 **18 处** `catch (Exception e)` 里，**其余 17 处都把原始异常记进了日志**
     （`logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e)` 等，共 28 处 `logBusinessErr` 调用全部带 `e`），
     **只有 `UserDomainService.java:906-908` 这一处既没 log 也没 cause**：
     ```java
     } catch (Exception e) {
         throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException();   // ← 原始异常彻底消失
     }
     ```
     ⇒ **修法因此收窄为「只补这 1 处」**（**SQL 路径不需要补** —— 它早就记了）。
     ⚠️ **顺带更正我们上一封回信给 admin 域的说法**：「SQL 路径 + Redis 路径一起补」是**错的**
     —— admin 域两条都已记（29 处 `log.error` 全带 `e`），我域的 SQL 路径也已记 ⇒ **两边都只需补 Redis 路径**。
     ⇒ **底层 Lettuce 异常在日志里完全看不见** ⇒ 排障只看到「Redis 写入异常」，看不到「其实是超时」。
     ⚠️ **与 admin 域写法不齐** —— admin 侧 `AdminDomainService.logout` 有 `log.error("admin logout Redis setex failed", e)` ⇒ **原始异常进了日志**。
  2. **B-1 守卫只挡 SQL 路径** —— 守卫靠 `catch (StatusRuntimeException)`（**gRPC 的那个**）识别「已被翻译成 DEADLINE_EXCEEDED 的错误」，
     而 **Redis 侧抛的是 Spring Data 的普通异常**，继承链实测（`javap` 读 class 文件）：
     - 命令超时 ⇒ `org.springframework.dao.QueryTimeoutException` → `TransientDataAccessException` → `DataAccessException` → `RuntimeException`（`spring-tx-7.0.9`）
     - 连接失败 ⇒ `org.springframework.data.redis.RedisConnectionFailureException` → `DataAccessResourceFailureException`（`spring-data-redis-4.0.7`）
     ⇒ **两者都不是 `io.grpc.StatusRuntimeException`** ⇒ **守卫永远不命中** ⇒ Redis 路径**一直**落在 `catch (Exception)` 里被改写成 INTERNAL。
     （SQL 路径之所以能命中：PG `57014` 经 `SqlTimeoutStatusTranslator` 翻成 gRPC 的 `StatusRuntimeException` ⇒ 守卫拦得住。）
  3. **RAG 的 Redis 写不绑 gRPC deadline** —— 域侧闸门**只在入口**（⚠️ **2026-10-31**：logout 那处**已撤** ⇒ `UserDomainService.java:899` 现记「入口不设闸门」⇒ ⇒ 在途写只受**共享层期限代理 ＋ 3s command timeout** 约束），
     在途写一路跑到自己的 **3s command timeout** 才失败 ⇒ 而客户端早在 **2s** 就收到了拦截器发的 `DEADLINE_EXCEEDED`
     （`RpcInterceptor` 的超时调度器 `call.close(DEADLINE_EXCEEDED, …)`，业务线程**不中断**）
     ⇒ ⇒ 这解释了「**服务端日志比客户端晚 1s**」（实测日志里 RAG 那两行 `INTERNAL` 的时间戳在 3s 处）。

- **Classification**: **OBSERVABLE DIFFERENCE**（**观测面**：应用日志的错误归因；**wire 面两侧完全一致** —— 都是 `INTERNAL` + 同一句「注销失败,Redis写入异常」）
- **Observable Impact**: 运维排障会被**指向错误方向**（日志说「Redis 写入异常」⇒ 去查 Redis 基础设施；实际是**超时**）；日志告警口径也会把超时误计成业务写失败。
  ⚠️ **wire 面无影响** ⇒ 修法**不能改 status / description**（改了反而与 Go 不一致）⇒ **只能加可观测性**。
- **归属**：**本组件内**（user-rpc 的注销路径）⇒ 修法**可以只落在 user-rpc** ⇒ **不跨域**。
- **🛠 候选修法（域内一行 · 不改 wire）**：在 **`logout` 的 Redis 写路径那一处** `catch (Exception e)` 里把原始异常记进日志，与 admin 域写法对齐
  ⚠️ **2026-10-26 晚收窄**：**只需补这 1 处**（其余 17 处 `catch (Exception e)` **早已带 `e` 记了日志**）⇒ 原先「两处一起补」的说法作废
  ```java
  } catch (Exception e) {
      logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);   // ← 补：原始异常进日志
      throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException();
  }
  ```
  ⇒ **status 与 description 一个字都不改**（仍与 Go 逐字一致）⇒ 纯增可观测性 ⇒ 排障时能看到「其实是超时」。
  ⚠️ **本轮不动**（修法口径未定，且要与 admin 域写法对齐后再一起落）。

#### ✅ `DRIFT-USERRPC-V-012` 已修（2026-10-27 · G1 lane · 域侧自闭环 · **落点在本域**）

- **改动**：`service/users/user/user-rpc/.../service/UserDomainService.java` 的 `logout()`
  ⇒ 在 `catch (Exception e)`（Redis 写路径那 1 处）里**补一行**：
  ```java
  logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_SELECT.getCode(), e);
  ```
  ⇒ **status 与 description 一个字都没改**（仍 `INTERNAL` + 「注销失败,Redis写入异常」）
  ⇒ **wire 面零影响**（与 Go 逐字一致），**只增可观测性**。
  ⚠️ 原文示例写的是 `otelCtx`，但 `logout()` 方法内**没有** `otelCtx` 局部变量
  ⇒ 本类同方法族用的是 `Context.current()`（如 `:515`）⇒ 照本类既有写法落。

- **守门**：`UserDomainServiceTest#logout_redisError_logsOriginalCause_forAttribution`
  ⇒ 让 `ValueOperations.set` 抛 `RuntimeException("redis command timed out")`
  ⇒ 断言 `logger.logBusinessErr(ctx, ERROR_DB_SELECT.getCode(), **同一个异常对象**)` 被调用一次
  ⇒ ⚠️ 断言的是**异常对象本身**（`same(...)`），不是它的 message 文本 ⇒ 防止「只记了个字符串」这种假修。
- **变异验证**：删掉那一行 ⇒ 该守门红（实测 `Wanted but not invoked`）⇒ **不是假绿**。
- **证据强度**：**L2**（Mockito 行为断言）＋ **L1**（Go 侧 `logoutlogic.go:39`
  `logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)` 把原始 err 记进日志）。
  ⚠️ **未跑双侧活体对拍** ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」。
- **残余**：本条只覆盖**日志归因**（观测面）。
  ⚠️ **②（B-1 守卫只挡 SQL 路径）与 ③（Redis 写不绑 gRPC deadline）**两条**仍在**
  ⇒ 它们是**架构级**（Redis 侧抛的不是 gRPC 的 `StatusRuntimeException` ⇒ 守卫永不命中）
  ⇒ 与 `global` 域 **E38 第②层**同型 ⇒ 按 Jason 2026-10-26 的口径「**另立项**」⇒ **本条不销**。
  ⇒ 本轮只把「原始异常整个被吞掉」这一条**可观测性缺陷**补上。
- **与 E38 第②层的关系**：`global` 域的 **E38 第①层**（`d531abd`）做的是「服务端超时期限 = min(服务端预算, 客户端 deadline)」；
  **第②层（「在途查询随客户端放弃而取消」）global 明确未做**，且其 `UNRESOLVED-ARPC-002` 编号属 **users/admin 域**。
  ⇒ **本条是 E38 第②层在 Redis 写路径上的同型实例**（在途命令不随 deadline 取消）⇒ 记录此关联，供 global 与 admin 域对照。
  ⇒ ⚠️ **admin 域已于 2026-10-26 独立确认同型并登记为 `DRIFT-ARPC-010`**（提交 `c09fa45`，分支 `align/users-admin`）⇒ **三域口径应一起定**（global / users-user / users-admin），
  ⇒ 且 admin 域已提醒：**「修 E38② 时顺带覆盖 Redis 路径才是正解，否则只修一半」**。

### 复现入口（本轮新增）

```bash
# 三形态各跑一次；先构建探针（一次即可）
cd tools/difftest/redis-logout-rpc/go
CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off go build -o /tmp/redis-logout-rpc .

cd tools/difftest
./exp-difftest-redis-neverup-rpc.sh                     # 拒连
REDIS_HOST=redis ./exp-difftest-redis-neverup-rpc.sh    # 解析失败（生产默认形态）
REDIS_MODE=hanging ./exp-difftest-redis-neverup-rpc.sh  # 挂起 ⇒ 命令超时路径
```

---

<!-- @fix 2026-10-26 第十二轮：B-1 的**装配层**守网（user-rpc 侧）—— 覆盖缺口闭环（非新漂移） -->

## 2026-10-26 第十二轮：B-1 的**装配层**守网（user-rpc 侧）—— ✅ **覆盖缺口闭环**（**非新漂移**）

### 0. 定性先说清（免得被误读成「又发现一条漂移」）

本轮**没有发现任何新的语义漂移**，**没有改动任何生产代码**（生产文件 `git diff` 为空）。
本轮闭环的是一处**守网覆盖缺口**：
**「B-1 的两个 MyBatis 拦截器真的被装进了插件链」这件事，此前从未被任何断言覆盖。**

| 项 | 内容 |
|---|---|
| **触发** | `users/admin` 域在 `align/users-admin`（提交 `1d79f56`）自查发现同款缺口，回信点名本域 ⇒ 本域**独立复核**（不采信对方结论）后确认**同款成立** |
| **缺口的准确性质** | **覆盖缺口**，**不是**当前生产缺陷 —— 端到端实测（2026-10-21 E-4）已证明 B-1 缓解**确实生效**（查询滞留 4303ms → **176ms**，与 Go 的 182ms 同量级）⇒ 拦截器**事实上是装上了的**。<br>⚠️ **但那条端到端证据是 2026-10-21 的一次性实测，不是常驻断言** ⇒ 谁在某个提交里摘掉 `@Import`，**测试全绿、线上才炸** |
| **为什么它值钱** | 链断掉的后果是 **wire 级漂移**，而且**静默**：缺翻译器 ⇒ PG 的 `57014 query_canceled` 不翻成 `DEADLINE_EXCEEDED` ⇒ 落进业务层 `catch (Exception e)` ⇒ 被改写成 `INTERNAL`「DB查询失败」⇒ api 侧 `mapRPCStatusToCode`（`service/users/user/user-api/src/main/java/com/rag/users/user/api/advice/GlobalExceptionHandler.java:154-155`）渲染 **5001**，而 Go 是 **1015**（`service/user/common/errmsg/errmsg.go:6`） |
| **与 V-012 的关系** | **两个独立的失效面**：V-012 是「**Redis 路径**压根到不了 SQL 翻译器」（继承链实测：Lettuce / QueryTimeout 异常**都不是** `StatusRuntimeException`）；本条是「**SQL 路径**的翻译器压根没装进链」⇒ 两者都要各自有守网，谁也替不了谁 |

### 1. 缺口的确切位置（声明点 vs 消费点）

| | 位置 | 本轮之前的状态 |
|---|---|---|
| **声明点** | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/UserRpcApplication.java:47-68` 的 `@Import({… DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})` | ✅ 写着（**但这只是声明**） |
| **消费点** | MyBatis-Plus 3.5.17 的 `MybatisPlusAutoConfiguration` 构造器收 `ObjectProvider<Interceptor[]>` → `sqlSessionFactory(DataSource)` 交给 `SqlSessionFactoryBean` ⇒ 最终形态 = `sqlSessionFactory.getConfiguration().getInterceptors()` | ❌ **无任何断言** |

⚠️ **§3.3.1 的口径**：行号指向**消费点**而非**声明点**。
⇒ 我方**没有**只读 `@Import` 那一行就下结论：先 `javap` 读出 MP 的构造器签名（确有 `ObjectProvider<Interceptor[]>`），
再由**真容器里那个 `SqlSessionFactory` 实例**读出链的最终形态。

**改动前实测事实**（主干全域 grep）：`getInterceptors` **零命中**
⇒ 本仓 **10 个 rpc 组件没有任何一个**断言过这条链。
（`DeadlinePassthroughTest` 是 mock mapper 直接抛 `StatusRuntimeException`
⇒ 只验「catch 分支放不放行」，**不验「异常到达时是不是那一类」**。）

### 2. 本轮补的 3 条断言（`UserRpcContainerWiringTest`，真 PG + 真容器）

| # | 用例 | 断言什么 | 拦住的失效 |
|---|---|---|---|
| 1 | `sqlTimeoutInterceptorsAreReallyInThePluginChain` | `sqlSessionFactory.getConfiguration().getInterceptors()` 里**真的有** `SqlTimeoutStatusTranslator` 与 `DeadlineQueryTimeoutInjector` | 链里缺任一个（`@Import` 被摘 / Bean 装配失败 / MP 改版不收 `Interceptor[]`） |
| 2 | `translatorInterceptsTheExecutionPoint` | 翻译器的 `@Intercepts` 挂在 **`Executor`**（`query` + `update`）而**不是** `StatementHandler.prepare` | 挂错点 ⇒ CancelRequest 引发的 `SQLException` 在**执行**阶段才抛，`prepare` 阶段根本看不到 ⇒ **永远翻译不到**，且**静默**（链里有它、测试也全绿） |
| 3 | `translatorMountPointGuardRejectsWrongMountPoint`（**负控 NC-A**） | 同一条判据喂一枚**故意挂错点**的变异体 ⇒ **必须拒绝**（**不查理由**） | 断言本身写坏 ⇒ 退化成恒绿的假守门 |
| 4 | `translatorMountPointGuardRejectsExtraStatementHandlerSignature`（**负控 NC-B · 2026-10-26 补**） | 喂一枚**「一切正确 + 只多挂一条 StatementHandler.prepare」**的变异体 ⇒ 必须被拒绝，**且拒绝理由必须含「StatementHandler」** | NC-A 的变异体**同时违反三条规则** ⇒ 判据只要有**任意一条别的规则**在拦它就会绿 ⇒ NC-A **只证明「不是恒绿」，证明不了「真在看挂载点」** ⇒ NC-B 只违反一条 ⇒ 才有隔离力 |

⚠️ **第 3 条是 admin 域没有的一层**（他们只做了变异 M1）。
负控的价值：用例 2 读的是**注解元数据**；若判据写错（例如只查「有没有 `@Intercepts`」而不查挂载点），
它会**恒绿**，等于没有守门。负控用一枚**形状错误**的输入跑**同一条判据**，要求它必须拒绝。
变异体是测试类里的**静态嵌套空壳**（未被 `@Import`、不在组件扫描路径上）⇒ **不参与任何生产装配**。

### 3. 变异验证（§3.3：必须**能编译 + 测试变红**）

| 变异 | 操作 | 预期 | 实测 |
|---|---|---|---|
| **M1** | 摘掉 `@Import` 里的 `SqlTimeoutStatusTranslator.class` | 只红 1 条，红在目标断言 | ✅ `Tests run: 16, Failures: 1` —— 失败信息带出实际链 `[com.rag.common.persistence.DeadlineQueryTimeoutInjector]` ⇒ **链是真的**（不是空链恒红） |
| **M2** | 摘掉 `@Import` 里的 `DeadlineQueryTimeoutInjector.class` | 只红 1 条，红在目标断言 | ✅ `Tests run: 16, Failures: 1` —— 实际链 `[com.rag.common.persistence.SqlTimeoutStatusTranslator]` |
| **M3（负控 NC-A 自检）** | 把「挂载点」判据弱化成空实现 | **负控必须变红**（它不再能识破变异体） | ✅ `Tests run: 16, Failures: 1` —— `Expected java.lang.AssertionError to be thrown, but nothing was thrown` |
| **M4（负控 NC-B 隔离性 · 2026-10-26 补）** | **只**摘掉「不得挂 StatementHandler」那一条规则（保留 Executor 与 query/update 两条） | **只红 NC-B、NC-A 仍绿** ⇒ 证明 NC-B 真把挂载点规则隔离出来了 | ✅ `Tests run: 17, Failures: 1` —— 红的是 `translatorMountPointGuardRejectsExtraStatementHandlerSignature`（`Expected AssertionError to be thrown, but nothing was thrown`）⇒ **NC-A 仍绿** |

⚠️ **M1/M2 只红 1 条这件事本身就是结论**：其余 15 条（含用例 2、用例 3）**不受 `@Import` 影响** ——
用例 2/3 读的是**类上的注解**（与容器无关）⇒ 它们的红绿边界是干净的。
⚠️ **三次变异后均已还原**，`git diff` 只剩测试文件本身（**+161 行**），生产代码**一字未改**。

### 4. 门禁

| 命令 | 结果 |
|---|---|
| `mvn -o -pl service/users/user/user-rpc -am test -Dtest=UserRpcContainerWiringTest -Dsurefire.failIfNoSpecifiedTests=false` | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn -o -pl service/users/user/user-rpc -am test`（全量） | `Tests run: 128, Failures: 0, Errors: 0, Skipped: 0` —— 较 2026-10-24 基线 **125** 净增 **+3**（= 本轮新增 3 条，账对得上） |

### 5. 与相邻域的关系（避免各修各的）

- **`users/admin` 域**：`align/users-admin` 的 `1d79f56` 已做**同款** 2 条（插件链 + 挂载点），**触发源正是本域此前的回信** ⇒ 两域口径一致。
  ⚠️ **差异**：admin 只做了变异 M1；本域多了 **M2**（另一个拦截器）与 **M3 负控** ⇒ 若 admin 愿意，可把负控那条抄过去（**抄之前先自己跑一遍**，别照抄结论）。
- **`global` 域**：`service/common` 的两个拦截器是**跨 10 个 rpc 组件**共享的。
  ⚠️ **本轮实测边界**：我只核了 **user-rpc** 一条链；**其余 9 个 rpc 组件是否都注册了这两个拦截器，本轮未查** ⇒ 记为**跨域待办**，请 global 收口时统一盘点。
  （admin 域已独立核实 `hot-rpc` **未注册** `SqlTimeoutStatusTranslator` ⇒ 前提不成立已销案 ⇒ **不是每条链都该有翻译器** ⇒ 盘点时必须逐个看「这个组件有没有 SQL 路径」，**不能一刀切**。）
- **`service/common` 的单写权**：本轮**未改** `service/common` 任何一行（那是 global 域的）⇒ 断言落在**本域自己的**容器测试里。

### 6. 诚实边界

- 本轮**只证明「装配」**（链里有 + 挂载点对），**不证明**「链断了会怎样」—— 后者由另外两段守网覆盖：
  ① 放行分支：`DeadlinePassthroughTest`（纯控制流，mock mapper）；
  ② 翻译器本体：`service/common` 的 `SqlTimeoutStatusTranslatorTest`。
  ⇒ **三段合起来才覆盖整条链，缺一段就有洞。**
- **未做**：没有重跑 2026-10-21 那条端到端（`exp-b1-timeout-cancel.sh`）⇒ 本轮**不声称**「B-1 端到端仍成立」，只声称**新增了常驻守网**。
- **未盘点其余 9 个 rpc 组件**（见 §5）⇒ 本轮**不声称**「全仓只有 user-rpc 缺这条守网」。

---

<!-- @fix 2026-10-26 第十三轮：`global` E58 的客户端可见面复核 ⇒ 订正 17 处注释的**理由** + 新立 V-013 -->

## 2026-10-26 第十三轮：`global` E58 的**客户端可见面**复核 —— 源码层差异成立 · **客户端面两侧一致** ⇒ **不得改回 Go 行为**

### DRIFT-USERRPC-V-013 —— DB 超时的**源码层形态**两侧不同，但**客户端可见面逐字一致** ⇒ **不是 wire 漂移** · **不得改回 Go 行为**

> **触发**：`global` 域第三十六轮（`697f547`）把 **E47** 全面订正：原判「业务层把『SQL 被 deadline 取消』改写/吞掉」**方向相反**，
> 重新定性为「Go 侧把 DB 错误一律改写成 `codes.Internal`，RAG 侧保留 `DEADLINE_EXCEEDED`」
> ⇒ 两侧**确实不同**，但方向是「**RAG 做了更准确的那一侧**」⇒ 登记为有意偏离候选 **E58（🏷 建议签）**
> ⇒ 明确要求：**请不要改回 Go 行为**。
>
> ⇒ 本域**独立复核**（不采信转述）：**源码层成立 ✅ · 客户端可见面不成立 🔴**。

#### §1 源码层：global 的事实成立，但**条数与行号要订正**

**Go 侧** —— `service/user/user/rpc/internal/logic/` 里 **DB 错误出口共 10 处**，全部 `codes.Internal`：

| 文件 | 行号 | 文案 |
|---|---|---|
| `loginlogic.go` | `:56` | `status.Error(codes.Internal, "DB查询失败")` |
| `getuserlogic.go` | `:44` | 同上 |
| `deleteuserlogic.go` | `:52` / `:60` | `"DB查询失败"` / `"DB更新失败"` |
| `registerlogic.go` | `:54` / `:86` | `"DB查询失败"` / `"DB更新失败"` |
| `updateuserlogic.go` | `:43` / `:66` / `:77` | `"DB查询失败"` / `"DB更新失败"` / `"DB查询失败"` |

⚠️ **订正 global 的两处**（PIT-USERS-SHARED-020：对方给的行号要自己核）：
1. **条数**：global 报「7 处」⇒ **实测 10 处** —— global 的清单**漏了 `updateuserlogic.go` 的 3 处**（`:43` / `:66` / `:77`）；
2. **行号**：global 报 `getuserlogic.go:41` · `loginlogic.go:55` · `deleteuserlogic.go:49,57`
   ⇒ 实测 `:44` · `:56` · `:52`（+ `:60`）⇒ **差 1~3 行** ⇒ 照 global 的行号去改会改错位置。

⚠️ **另：Go 侧另有 9 处 `codes.Internal` 但不是 DB 错误**
（`getavatarhistorylogic.go:38,44` · `logoutlogic.go:40` · `registerlogic.go:61,70` ·
`selectavatarlogic.go:49` · `uploadavatarlogic.go:42,60`），其中 4 处文案是 **N-2 已签字的 Go 源码乱码**
⇒ 本条**只管 DB 错误出口那 10 处**。

**RAG 侧** —— `UserDomainService` 的 **18 处** `catch (StatusRuntimeException e) { throw e; }`
（订正后行号：`200 / 295 / 312 / 333 / 365 / 417 / 494 / 523 / 554 / 577 / 638 / 657 / 714 / 759 / 789 / 831 / 850 / 897`）
⇒ DEADLINE_EXCEEDED 原样上抛 ✅（与 global 说的 18 处一致）。

#### §2 🔴 客户端可见面：**两侧逐字一致** —— 与 global 的 E58 定性冲突

global 写「两侧确实不同（wire 上码值不同 ⇒ **客户端可见**）」。
⇒ 本域查了 **2026-10-21 的 E-4 双栈活体实测**（真进程 + 真 PG，用 ACCESS EXCLUSIVE 锁把 login 的 SELECT 挡住）：

| | 客户端返回耗时 | 响应体 | PG 查询存活 |
|---|---|---|---|
| **Go**（基线） | 2190ms | `200 + {"code":1015,"msg":"服务繁忙"}` | 203ms |
| **Go**（同场对照） | 2208ms | `{"code":1015,"msg":"服务繁忙"}` | 182ms |
| **Go / user** | 2236ms | `{"code":1015,"msg":"服务繁忙"}` | 161ms |
| **Go / admin** | 2181ms | `{"code":1015,"msg":"服务繁忙"}` | 176ms |
| **RAG**（生产方案实施后） | 2223ms | `{"code":1015,"msg":"服务繁忙"}` | 176ms |

⇒ **Go 侧 4 组独立采样，客户端可见响应体全部是 `{"code":1015}`** ⇒ **两侧逐字一致**。

**机制（为什么 Go 的 `codes.Internal` 客户端看不见）** —— Go 的外层 interceptor 会抢先返回：

```go
// service/common/observability/rpc.go:59-72
select {
case <-done:                       // handler 先返回（logic 的 INTERNAL）
    return resp, err
case <-timeoutCtx.Done():          // ← ctx 一取消就 ready
    err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())
    return nil, err                // ← 客户端拿到 DEADLINE_EXCEEDED
}
```

⇒ ctx 取消 ⇒ pgx 查询报错 ⇒ logic 返回 INTERNAL，这需要**再花时间**（网络往返 + 解栈 + 组装 status）；
⚠️ **更强的机制论证（`users/admin` 域回信补的，他们读的是 Go 源码）**：
`case <-done:` 分支**还要 `lock.Lock()`**（handler goroutine 持有锁），
而 `case <-timeoutCtx.Done():` 分支**不需要锁**
⇒ ⇒ **ctx 取消那一刻，超时分支已经 ready，而 done 分支还要等锁** ⇒ select 几乎总走超时分支
⇒ 这一条比我方原先的「时间差」论证更强（**不只靠「还要花时间」，而是靠「锁的先后」**）；
而 `timeoutCtx.Done()` **在 ctx 取消那一刻就已经 ready** ⇒ select 几乎总是选超时分支
⇒ **客户端拿到 DEADLINE_EXCEEDED** ⇒ api 的 `default` 分支 ⇒ **1015**（`errmsg.CodeServerBusy`）。
⇒ Go 侧 api 的 per-Logic switch（`service/user/user/api/internal/logic/user/loginlogic.go:50-54`）
只对 `codes.Internal` 给 5001、对 `DEADLINE_EXCEEDED` 走 `default` 给 1015 ⇒ **两侧落到同一个分支**。

⚠️ **这正是本文件 §10 第 7 项早就写下的那句话**：「**57014 取消的内层差异被外层 interceptor 掩盖（E-4 已实测闭环）**」
⇒ global 的 E58 把**源码层**的差异直接当成了**客户端可见**的差异，**跳过了这一层掩盖**。

#### §3 处置：订正 17 处注释的**理由**（结论保留 · 行为不动）

本域 17 处 catch 注释原文写着「…（**对齐 Go 的 1015**）」。
⇒ **结论是对的**（客户端可见值两侧都是 1015），**理由是错的/不完整的** ——
它让人以为「Go 的 logic 层会返回 1015」，而 **Go 侧根本没有任何 DB 路径返回 1015**。
⇒ 按 AGENTS.md §3.3 强制第 3 条「**先改/删错误断言，再改实现**」：先订正断言，**不动实现**。

订正后的注释（17 处同款）：

```java
// B-1：...必须上抛，不能掉进下面的 db_error 分支被改写成 INTERNAL。
//       ⚠️「对齐 Go 的 1015」指的是**客户端可见值**（E-4 双栈活体实测：两侧同为 {"code":1015,"msg":"服务繁忙"}），
//       **不是** Go 的 logic 层会返回 1015 —— Go 的 logic 把 DB 错误一律糊成 codes.Internal
//       （…共 10 处出口），那一层**被外层 interceptor 掩盖**：
//       Go 的 service/common/observability/rpc.go:68-71 在超时分支直接回 codes.DeadlineExceeded。
//       ⇒ **不得改回 Go 的 INTERNAL**（那会让 RAG 渲染 5001，离 Go 更远）。
```

并在**类级 javadoc** 写一次完整论证（18 处 catch 的共同依据 + E-4 五行实测数据 + Go 侧机制）。
⇒ **生产行为零改动**（本轮只改注释）。

#### §4 🔴 残留风险（诚实边界）

Go 侧那个 `select` 是**竞速**：`done` 与 `timeoutCtx.Done()` 同时 ready 时 Go **随机选**
⇒ 机制上存在「handler 先返回 ⇒ 客户端拿到 5001」的窗口。
⇒ **E-4 的 4 组 Go 采样全部是 1015，但那是 4 次采样、不是概率刻画**
⇒ 本域**不声称「Go 侧 100% 是 1015」**。
⇒ 若要闭合这一格，需在同一条件下跑 N 轮并统计分布（**本轮未做**）。

#### §5 与 `global` E58 的关系（已回信）

- **本域不把这条登记为「有意偏离」** —— 因为**客户端可见面两侧一致**
  ⇒ 按 AGENTS.md「差异只允许出现在表达层」，它**不构成 wire 漂移**，也**不需要**用户签字。
  ⇒ 但**必须留档** —— 否则下一个人会照着「Go 是 INTERNAL」把 18 处 catch 改回去
  ⇒ RAG 渲染 5001 ⇒ **离 Go 更远**（2026-10-21 B-1 缓解实验第三行踩过的坑）。
- 🔴 **需 `global` 侧处理的两处**（**`service/common` 是 global 域单写权 ⇒ 本域只报告、不动手**）：
  1. `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:29-38` 的 javadoc 写着
     「Go 侧 ctx 到点后，查询返回 `context.DeadlineExceeded`，**经 go-zero 的错误映射成为「服务繁忙」1015**」
     ⇒ **理由不准**：1015 来自 **api 层的 default 分支** + **interceptor 的超时分支**，不是 go-zero 的错误映射；
     且同段「Go 侧这两者本来就不同（超时 1015 / 数据库错 5001）」**只在客户端面成立**（源码层 Go 两处都是 INTERNAL）。
  2. **E58 的定性**：建议从「wire 级差异 ⇒ 客户端可见」改为
     「**源码层形态差异 · 客户端可见面一致（E-4 四组实测）**」
     ⇒ 否则别的域会照着「RAG 更准确 ⇒ 保留」去保留**那些确实客户端可见的改写**
     （message 域 E59 / like 域 E60 恰好就是那种）。

#### §6 复现入口

```bash
# E-4 双栈实测（Go 18888 / RAG 59900）；客户端可见响应体就在脚本输出里
bash tools/difftest/exp-b1-timeout-cancel.sh
# ⚠️ 该脚本 2026-10-21 的判读段原文就写着：
#    「user 与 admin 两侧都应当是 {"code":1015,"msg":"服务繁忙"}」—— 本条的依据就是这句。
```

### V-013 的分类与统计影响

| 项 | 内容 |
|---|---|
| **Classification** | **NOT A WIRE DRIFT**（源码层形态差异 · **客户端可见面两侧逐字一致** · E-4 四组 Go 采样 + RAG 生产方案同场对照）⇒ **不计入 §4.4 的 OBSERVABLE DIFFERENCE** |
| **Observable Impact** | **无**（客户端可见的 code 与 msg 两侧完全相同） |
| **Risk** | 🔴 **反向风险**：若按「Go 是 INTERNAL」把 18 处 catch 改回去 ⇒ RAG 渲染 **5001**（`ErrorServerCommon`，在 Go 侧表示「数据库故障」而非「超时」）⇒ **离 Go 更远** |
| **处置** | ✅ **已处置**（2026-10-26）：17 处注释的**理由**已订正 + 类级 javadoc 完整论证；**生产行为零改动** |
| **归属** | **本组件内**（`service/users/user/user-rpc`）⇒ 修法只落本域 ⇒ **不跨域**（但 `service/common` 的同款 javadoc 问题须由 `global` 域处理） |

---

## 2026-10-27 第十四轮：Redis 在 **rpc 侧**的同机制不同形态 ⇒ `DRIFT-USERRPC-V-014`（**已修** · 域内自闭环）

### 0. 这条是怎么被查出来的（**不是**复述已知结论）

G2（message / like / task lane）发来回执，说它在自己域的 **rpc 侧**查到一条「Redis 断连把整个 RPC 拖到超时」的漂移
⇒ 它提醒我「同款机制可能在我域也有」⇒ 我按**同族横向对比**把**本域所有用 Redis 的组件**并排核了一遍
（**不是**逐个对着 Go 读 —— 逐个读看不出「这一族到底该是多少」）：

| 组件 | Redis 用途 | `timeout`（命令） | `connect-timeout`（连接） | 断连策略 |
|---|---|---|---|---|
| `user-api` | **读**黑名单（fail-open） | 3s ✅ | 5s ✅ | `REJECT_COMMANDS` ✅ |
| `admin-api` | **读**黑名单（fail-open） | 3s ✅ | 5s ✅ | `REJECT_COMMANDS` ✅ |
| `follow-api` | **读**黑名单（fail-open） | 3s ✅ | 5s ✅ | `REJECT_COMMANDS` ✅ |
| `admin-rpc` | **写**黑名单（logout） | 3s ✅ | 5s ✅ | 🔴 **缺** |
| **`user-rpc`（本轮）** | **写**黑名单（logout） | 3s ✅ | 🔴 **缺** | 🔴 **缺** |

⇒ **两处离群项，全部落在 rpc 侧** ⇒ 这是「同机制、不同形态」的另一面：
**api 侧三个域已经在上一轮补齐，rpc 侧两个域一个没补**。

### 1. 子项 ①：`user-rpc` 缺 `connect-timeout` ⇒ 连接超时落 **Lettuce 默认 10s** ≠ Go 的 5s

**Go 侧出处（逐层到消费点）**

```
Go service/user/user/rpc/etc/user.yaml:17-20
    BizRedis: { Host: redis:6379, Type: "node", Pass: "" }   ← **没有任何 timeout 字段**
  → go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-39
      建 red.Options 时只传 Addr/User/Pass/DB/MaxRetries/MinIdleConns/TLSConfig
      —— **三个 timeout 一个都没传**
  → github.com/redis/go-redis/v9 options.go 的 init()：
      DialTimeout = 5s（:329-330） · ReadTimeout = 3 * time.Second
⇒ Go：连接 5s / 命令 3s
```

**RAG 侧修前**：`application.yaml` 的 redis 段**只有** `timeout: 3s` ⇒ **没有 `connect-timeout`**
⇒ 连接超时 = **Lettuce 自己的默认 10s** ≠ Go 的 5s。

🔴 **顺带订正一个此前写错的机制（本仓多处）**：
本仓此前写「Lettuce 在 `connect-timeout` 未设时**回落到 timeout** ⇒ 跟着变成 3s」——**那是错的**。
Boot 4.0.8 `org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration.createClientOptions`
的**字节码**是：

```java
Duration connectTimeout = getProperties().getConnectTimeout();   // :9
if (connectTimeout != null) {                                     // :16
    builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
}
```

⇒ **null ⇒ 整段不执行** ⇒ 用 Lettuce 自己的默认
`io.lettuce.core.SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION` = **10s**
（`javap -constants` 实测：`DEFAULT_CONNECT_TIMEOUT = 10l`）。
⇒ **修法与结论不变**（显式配 5s），但**漂移方向是 10s vs 5s**，不是 3s vs 5s。
⇒ 已在 **4 个 yml 注释 + 4 个 config 守门 + 4 个 socket 守门 + 5 处台账**里加订正注记（**保留原值不改写**）。

**修法**：`service/users/user/user-rpc/src/main/resources/application.yaml` 补 `connect-timeout: 5s` ＋ 写明出处链。

### 2. 子项 ②：断连时命令被**排队** ⇒ 挂到 3s ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时**

**Go 侧形状**

```
Go service/user/user/rpc/internal/logic/logoutlogic.go:34-39
    err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
    if err != nil {
        logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)
        return nil, status.Error(codes.Internal, "注销失败,Redis写入异常")    ← **快速失败 ⇒ INTERNAL**
    }
为什么快：go-zero redisclientmanager.go:14 maxRetries = 3 · :32-39 不设任何 timeout
          ⇒ 连接被拒/被关时每次重试都**立即失败** ⇒ 整体快速失败（实测 ~1.7s，仍在 2s 预算内）
```

**RAG 侧形状**

```
UserDomainService.logout:905-930
    catch (StatusRuntimeException e) { throw e; }                                  ← B-1 守卫
    catch (Exception e) { logger.logBusinessErr(...);                               ← DRIFT-USERRPC-V-012 已修
                           throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException(); }
```

⇒ **错误面（status + description）本来就与 Go 逐字一致** ⇒ 漂移**不在错误面**，在**失败耗时**：

| 形态 | 机制 | 耗时 | 客户端看到 |
|---|---|---|---|
| **Go** | go-redis `MaxRetries=3` ⇒ 每次重试立即失败 | ≈1.7s | `INTERNAL`「注销失败,Redis写入异常」 |
| **RAG 修前** | Lettuce `DEFAULT` + `autoReconnect=true` ⇒ **命令排队等重连**（`DefaultEndpoint:149` / `:837-852`）⇒ 挂到 `commandTimeout: 3s` | ≈3s | 🔴 **1015「服务繁忙」**（`DEADLINE_EXCEEDED`，因为 3s ≫ `rpc-timeout: 2s`） |
| **RAG 修后** | `REJECT_COMMANDS` ⇒ **立即拒命令**（`DefaultEndpoint:314`） | ≈1ms | `INTERNAL`「注销失败,Redis写入异常」 ✅ **与 Go 同形** |

⇒ **同一套 Redis 机制，在 api 侧的后果是 503，在 rpc 侧的后果是「RPC 撞超时」** —— 这就是 G2 说的「同机制、不同形态」。

**修法**：落**域内副本** `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/config/RedisDisconnectedBehaviorConfig.java` —— ⚠️ **该域内副本已于 Redis 收编轮【删除】**（5 份域内副本全部删除 ⇒ 共享层那份接管 ⇒ 见 [`vs-common.md` §26](../vs-common.md)）
（`LettuceClientOptionsBuilderCustomizer` ⇒ `REJECT_COMMANDS`）。

⚠️ **只改「断连」，不改「连着但不回包」**：后者两侧都等满各自的读超时（Go `ReadTimeout` 3s / RAG `timeout: 3s`）
⇒ 都 ≫ 本组件 2s 预算 ⇒ **两侧都是 DEADLINE_EXCEEDED** ⇒ 已一致 ⇒ 两类故障**分别**对齐。

> 🔴🟢 **2026-10-28 订正块（§50 · G1 lane）—— 上表的「客户端看到」那一列，现在从【推断】升级为【wire 上实测】**
>
> **上表三行里，「客户端看到」那一列当时是【代码路径推出的】**（`REJECT_COMMANDS` ⇒ 立即抛 ⇒ 走进 `catch (Exception e)` ⇒ INTERNAL）
> ⇒ ⇒ 本轮**真起了一个 gRPC server ＋ 真 gRPC 客户端**，把 wire 上的 status 抓了下来 ⇒ ⇒ **实测**：
>
> | 腿 | 断连策略 | 客户端可见 status（**wire 实测**） | 耗时 |
> |---|---|---|---|
> | **Go user**（真源） | — | `Internal` ＋「注销失败,Redis写入异常」 | **1689ms** |
> | **RAG user · A 组** | `REJECT_COMMANDS` | `INTERNAL` ＋「注销失败,Redis写入异常」 | **19ms** |
> | **RAG user · B 组** | `DEFAULT`（**同场反事实**） | **`DEADLINE_EXCEEDED`** ＋「context deadline exceeded」 | **2013ms** |
>
> ⇒ ⇒ **上表「RAG 修后 ≈1ms ⇒ 客户端看到 INTERNAL ✅」现在是 L4 实测**（实测 19ms）⇒ ⇒
> ⇒ ⇒ **上表「RAG 修前 ≈3s ⇒ 客户端看到 1015」也拿到正面实测**（B 组 = 2013ms / DEADLINE_EXCEEDED）
> ⇒ ⇒ ⇒ **「1015 vs INTERNAL」这个分歧点，两侧都有活体证据了**。
> ⇒ ⇒ **B 组是同场反事实**：唯一变量 = 断连策略（共享层那份 bean 带 `@ConditionalOnMissingBean`
> ⇒ ⇒ 域侧 test 侧提供一个 `DEFAULT` 的 customizer 即可**遮蔽**它 ⇒ ⇒ **域内零落码**）
> ⇒ ⇒ ⇒ **这才是「A 组的 INTERNAL 是这条机制换来的」的正面证据** ⇒ ⇒ **归因强度 L4**（原先 B 组只以「变异形态」存在 ⇒ 只能写 L3）。
> ⇒ ⇒ **两条变异**：M1（摘掉 B 组的 `@Bean`）⇒ **红在前提断言**；M2（排掉共享层自动配置）⇒ **红在目标断言**
> ⇒ ⇒ ⇒ 「A 组的 INTERNAL」与「B 组的 1015」**互为对照**，且 M2 是**另一条机制路径**得到同一结论 ⇒ ⇒ **交叉验证**。
> ⇒ ⇒ **⚠️ 顺带一条「差点写错的断言」**：本轮第一版断言按直觉写了「`success == true`」⇒ ⇒ **实测红**
> ⇒ ⇒ ⇒ 核 Go 真源 `logoutlogic.go:43` = **`return &pb.LogoutResp{}, nil`** ⇒ ⇒ **`success` 恒为 proto3 默认 `false`**
> ⇒ ⇒ ⇒ **RAG 侧不 setSuccess 与 Go 逐字同形 ⇒ 改成 true 反而制造漂移** ⇒ ⇒ 现已把「false」钉成守卫。
> ⇒ ⇒ 逐条（四组数字 · A/B 反事实 · 两条变异 · 三条纪律 · 四个新测试类）⇒ [`vs-common.md` §50](../vs-common.md)。

### 3. 落点决策：**域内副本**（不是等 G4）

| 判据 | 结论 |
|---|---|
| 被改的载体文件本来住在哪 | `config/RedisDisconnectedBehaviorConfig.java` 住在**本域内**（`service/users/user/user-rpc/config/`）⇒ **域侧自修** |
| 共享层收编 | ✅ 已作为反馈单派给 G4（`GoStyleRedisClientAutoConfiguration` ＋ `AutoConfiguration.imports`）⇒ **共享层落码由 G4 做** |
| ⚠️ 删除前置 | 共享层那份**先**合进各域的树 ⇒ 才能删域内副本 ⇒ **否则双份配置**（两份 customizer 同值，幂等但双份维护）⇒ 现共 **5** 份域内副本（3 api + 2 rpc） |

### 4. 守门（**每条都要能变红**）

| 守门 | 断言 | 变异 |
|---|---|---|
| `UserRpcRedisTimeoutConfigTest`（**新** · 2 例） | 文本层：`timeout: 3s` / `connect-timeout: 5s`；绑定层：Spring `Binder` 真把两个值吃进 `DataRedisProperties` | 改 yml 值 ⇒ 红 |
| `UserRpcRedisConnectTimeoutSocketTest`（**新** · 2 例） | **消费层**：`getClientOptions()→getSocketOptions()→getConnectTimeout()` == 5s；`getClientConfiguration().getCommandTimeout()` == 覆盖后的 30s（证明属性真的流到了客户端）＋ 黑洞 IP `192.0.2.1` 实测失败耗时落在 `[4000ms, 9500ms)` | 摘掉 `connect-timeout` ⇒ 客户端读数变 **10s** ⇒ 红 |
| `UserRpcRedisDisconnectedFastFailTest`（**新** · 2 例） | 断连（**FIN** 形态）时写命令**快速失败**（<1500ms）**且**异常**不是** `RedisCommandTimeoutException` | `REJECT_COMMANDS` → `DEFAULT` ⇒ 实测 ≈3000ms ⇒ 红 |

#### 4.1 ✅ 实测数字（**台账只引用具体数字**）

- **socket 层（user-rpc）**：配置 `connectTimeout=PT5S` ⇒ 黑洞实测失败耗时 **5004ms**（落在 `[4000ms, 9500ms)`）
  ⇒ 同批五组件实测：admin-rpc **5003ms** · admin-api **5136ms** · user-rpc **5004ms** · user-api **5175ms** · follow-api **5003ms**
- **断连（FIN）**：api 侧修后 admin-api **0ms** · follow-api **3ms**（预算 1500ms）

#### 4.1.1 🔴 命令超时的**四条腿**（探针实测 · 补一个真实的覆盖缺口）

G2 提醒「`getTimeoutOptions()` 不是值的判据」⇒ 我域据此**只**加了「值」那一条断言 ⇒ 用探针实测后发现
**「值」这一条单独存在 ⇒ 是个假绿口子** ⇒ 本轮补上**机制**那条腿。

**探针实测（user-api · Boot 4.0.8 + spring-data-redis 4.0.7 + lettuce 6.8.2）**

```
LettuceClientConfiguration.getCommandTimeout()   = PT3S
LettuceConnectionFactory.getTimeout()            = 3000ms      ← 连接级 ⇒ RedisURI.timeout
TimeoutOptions.isTimeoutCommands()               = true
TimeoutOptions.isApplyConnectionTimeout()        = true
TimeoutOptions.getSource()                       = DefaultTimeoutSource   ← 恒返回 -1
```

⇒ **链条**：`spring.data.redis.timeout: 3s`（配置层）
→ `LettuceClientConfiguration.commandTimeout` = **3s**（**值**）
→ `LettuceConnectionFactory.getTimeout()` = **3000ms** ⇒ `RedisURI.timeout`（**连接级那一路**）
→ `TimeoutOptions.isApplyConnectionTimeout()` = **true**（**机制**：把连接级 timeout 用作命令过期时长）
⇒ ⚠️ **因为 `source` 恒为 `DefaultTimeoutSource`（返回 -1）⇒ 真正的时长只能由 `applyConnectionTimeout` 分支从连接级 timeout 取**
⇒ ⇒ **`isTimeoutCommands()` 恒为 true ⇒ 它不是有效判据**（它只说明「机制位开着」，**不说明时长会被施加**）。

🔴 **由此暴露的缺口（已补）**：我域 5 份 FastFail 守门原本**只断言「值」** ⇒ 若 `applyConnectionTimeout` 被关掉
（Boot 升级换了接线方式），**值照样读得到 3s、但命令永不过期** ⇒ 断连时从「3s 快速失败」退化成「**无限等待**」
⇒ 守门会**照样绿** ⇒ **典型的「绿是因为没走到」**。
⇒ **已补**：5 份守门各加**机制腿**（`getTimeout() == 3000` ＋ `isApplyConnectionTimeout() == true`）。
⇒ **变异验证**：把期望值 `3000L` 改成 `3001L` ⇒ **红**（`expected: <3001> but was: <3000>`）⇒ 断言**在读运行期值、不是常量**；
⚠️ **诚实的边界**：被测机制本身（Boot 的 `TimeoutOptions.enabled()` 接线）**不在本域写权面 ⇒ 无法从域侧变异**
⇒ ⇒ 这条断言的价值是**回归网**（Boot 升级时若换接线方式，它会红），**不是**「我已证伪过该机制」。

#### 4.2 ✅ 变异验证（**3/3 真变红** —— 退出码之外还核了失败原因）

| 变异 | 结果 | 失败详情（原文） |
|---|---|---|
| **M1**：摘掉 `user-rpc` 的 `connect-timeout: 5s` | **4/4 红**（config 2 + socket 2） | ① `expected: <PT5S> but was: <PT10S>`（配置消费点）<br>② 黑洞实测 **10151ms** ⇒ `expected: <true> but was: <false>`<br>③ 绑定层 `expected: <PT5S> but was: <null>` |
| **M2**：`user-rpc` 的 `REJECT_COMMANDS` → `DEFAULT` | **红** | 实测耗时 **3012ms** ＋ `RedisCommandTimeoutException: Command timed out after 3 second(s)` |
| **M3**：`admin-rpc` 的 `REJECT_COMMANDS` → `DEFAULT` | **2/2 红** | 实测耗时 **3011ms** ＋ 异常链 `QueryTimeoutException: Redis command timed out <- RedisCommandTimeoutException: Command timed out after 3 second(s)` |

🔴 **M1 是本轮最重要的一条证据**：它**实测证伪**了本仓多处写的「Lettuce 在 connect-timeout 未设时回落到 timeout ⇒ 跟着变成 3s」
⇒ **真实值是 PT10S / 黑洞 10151ms** ⇒ **漂移方向是 10s vs Go 的 5s**（不是 3s vs 5s）。
⇒ ⇒ 本轮所有「订正块」都改的是**机制表述**，**修法与结论不变**。

⚠️ **一次自抓的「假红」**（记录在案，防止下次再犯）：M3 的第一轮脚本把 `admin-rpc` 的模块路径拼成了
`service/users/user/admin-rpc` ⇒ maven 直接报「Could not find the selected project in the reactor」⇒ **退出码也是 1**
⇒ 若只看退出码，会把「**模块找不到**」当成「**测试变红**」。
⇒ ⇒ **判定式：变异验证必须同时看「失败消息原文」，不能只看退出码。**（本轮已补跑 M3 拿到真红。）
⇒ 还原用 `md5` 校验（M1/M2/M3 三次均 `YES`）。

⚠️ **故障注入必须是 FIN 而不是 RST**：`container.stop()` 发的是 RST ⇒ Lettuce 往 socket 写命令时**立刻失败**
⇒ **压根走不到** `disconnectedBehavior` 那个分支 ⇒ 两种配置的耗时都是 1~13ms ⇒ **分不出**
⇒ 用**可控 TCP 透传代理**「先关监听口（重连被拒）＋ 再对已建立连接半关闭（发 FIN）」复刻 live 形态。

### 5. 诚实边界（本轮**没有**证的）

> 🟢 **2026-10-28 订正块（§48.1 · G1 lane）—— 本节第 ① 条与第 ② 条的「未证」状态已部分解除，但**不写 L4****
>
> | 本节原写的「没证」 | 现在的状态 |
> |---|---|
> | ① 「Go 腿是 L1 源码对照，不是双侧活体对拍」⇒ 证据强度 L1 | 🔴 **已解除一半**：本轮**自己**跑了 Go 腿 —— **真源的 `logoutlogic.Logout`**（不是重写）＋ **真源同款 go-zero Options**（`MustNewRedis` ⇒ `MaxRetries:3`/`MinIdleConns:8`、三个 timeout 都不传）＋ **同款 FIN 代理**（先关监听口 + 已建立连接半关闭）⇒ 实测 **1689ms**（首轮 1737ms ⇒ 同量级）＋ **客户端可见 gRPC status = `Internal`「注销失败,Redis写入异常」**（进程内直观测）⇒ ⇒ **Go 腿 = L4（活体）**；⇒ 逐条（含探针源码出处与两条踩坑）⇒ [`vs-common.md` §48.1](../vs-common.md) |
> | ② 「没有做『客户端看到 1015 vs INTERNAL』的端到端双侧活体对拍 ⇒ 另一半（客户端可见值）未实测」 | 🟢 **已解除（2026-10-28 · G1 lane · §50 落码）** ⇒ ⇒ **RAG 侧的「客户端可见值」现在也是 L4（wire 上实测）** ⇒ ⇒ 配合上一行的 Go 腿 L4 ⇒ ⇒ **本条现在可以写 L4（双侧）** ⇒ ⇒ 逐条 ⇒ [`vs-common.md` §50](../vs-common.md) |
> | ③ 「『Go 的断连失败耗时 ≈1.7s』是引 G2 的实测，不是我在 user-rpc 域测的」 | ✅ **已订正**：本域自己测到 **1689ms**（首轮 1737ms ⇒ 同量级，且不再依赖转述） |
>
> 🟢 **2026-10-28 第二块订正（§50 · G1 lane）—— 上面那条「B 组没有同场对照」已解除**
>
> | 腿 | 断连策略 | 客户端可见 status（**wire 实测**） | 耗时 |
> |---|---|---|---|
> | **Go user**（真源） | — | `Internal` ＋「注销失败,Redis写入异常」 | **1689ms** |
> | **RAG user · A 组** | `REJECT_COMMANDS` | `INTERNAL` ＋「注销失败,Redis写入异常」 | **19ms** |
> | **RAG user · B 组** | `DEFAULT`（**同场反例**） | **`DEADLINE_EXCEEDED`** ＋「context deadline exceeded」 | **2013ms** |
>
> ⇒ ⇒ **B 组现在是真的同场受控反事实**（唯一变量 = 断连策略）⇒ ⇒ **RAG 腿的机制归因强度 = L4**（原先 L3）
> ⇒ ⇒ ⇒ **本条（V-014）现在双侧 L4** ⇒ ⇒ 逐条 ⇒ [`vs-common.md` §50](../vs-common.md)

- ⚠️ **原写（2026-10-27）：「本轮的 Go 腿是 L1 源码对照，不是双侧活体对拍 ⇒ 证据强度 L1」** ⇒ ⇒
  🟢 **2026-10-28 订正：已解除**（Go 腿 L4，见上表 ①；RAG 腿的客户端可见值也升到 L4，见上表 ②）⇒ ⇒ **原文保留**（不重写历史）⇒ ⇒ 只加订正。
  ⇒ 「Go 的断连失败耗时 ≈1.7s」是**引 G2 的实测**（它在自己域测的），**不是我在 user-rpc 域测的**。
- ⚠️ **没有做「客户端看到 1015 vs INTERNAL」的端到端双侧活体对拍** ⇒
  「修后客户端拿到 INTERNAL」是由**代码路径推出来的**（`REJECT_COMMANDS` ⇒ 立即抛 ⇒ 走进 `catch (Exception e)`）
  ＋ **守门证明「立即失败」这一半** ⇒ **另一半（客户端可见值）未实测**。
  ⇒ 若要把这条升到 L4，需补一个「两侧 rpc 直连 + Redis 断连 + 比客户端可见响应」的探针。
- ⚠️ **命令超时的消费层判据**：`getClientConfiguration().getCommandTimeout()`（`LettuceClientConfiguration` 上的独立字段）。
  ⚠️ **`io.lettuce.core.ClientOptions` 在 lettuce 6.8.2 没有 `getCommandTimeout()`**；
  `ClientOptions.getTimeoutOptions()` 的 source 恒为 `DefaultTimeoutSource` ⇒ **是假判据**（G2 的建议已核实并采纳）。

### 6. 复现入口

```bash
# 守门（真 Redis 容器 + 可控 TCP 代理；需 Docker）
cd "/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow"
mvn -B -o -pl service/users/user/user-rpc test \
  -Dtest='UserRpcRedisTimeoutConfigTest,UserRpcRedisConnectTimeoutSocketTest,UserRpcRedisDisconnectedFastFailTest'
```

### 7. 与相邻域的关系（避免各修各的）

- **api 侧同款**：`DRIFT-ADMINAPI-012`（admin-api）/ `DRIFT-FOLLOW-012`（follow-api）⇒ 机制同、后果不同（503 vs RPC 撞超时）。
- **admin 域同款**：本轮独立立 `DRIFT-ARPC-013`（admin-rpc 的 ②）⇒ **两侧 rpc 都补了 `REJECT_COMMANDS`** ⇒ 域内口径一致。
- **跨域形态**：G2 在 task/like 域查到的是**同一个**形态 ⇒ **三域口径一致** ⇒ 共享层收编后应当是**一份**配置。

### 8. 🧹 附记：**1 处 U+FFFD 注释损坏修复**（**只改注释、不改行为**）

> 触发：G3 全仓扫 U+FFFD 时列进了我域 ⇒ 归属在我域（G1 写权内）⇒ **我域自修**。
> ⚠️ 原字不可考 ⇒ **我自己核了上下文**再落笔（G3 给的是推断）。

| 文件:行 | 损坏形态 | 重建 | 核对依据 |
|---|---|---|---|
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcLoginNullStatusReadsAsZeroTest.java:44` | `���是 {@code poolInitializer(…)}`（3×FFFD ⇒ 1 个完整汉字） | **「就是」**（缺字在「是」**之前** ⇒ 只能是一个副词 ⇒ 「就」） | 上下文：「`prepareValues` 与 `NewValuePool` ___ 是 `poolInitializer(...)`」 |

⚠️ `docs/Pitfalls/users/user/rpc.md:146` 的 `'用户不�?'` 是**故意记录「末字已不可恢复」** ⇒ **不动**。

### 9. 本轮回归（**2026-10-27 · 独占窗口**）

| 组件 | 本轮 | 上轮基线 | 差 | 说明 |
|---|---|---|---|---|
| `user-api` | 121 | 121 | ±0 | 只加了消费层断言（条数不变） |
| `user-rpc` | **143** | 137 | **+6** | 🆕 3 个新类 × 2 例 |
| `admin-api` | 140 | 140 | ±0 | 同上 |
| `admin-rpc` | **117** | 115 | **+2** | 🆕 `AdminRpcRedisDisconnectedFastFailTest` 2 例 |
| `follow-api` | 68 | 68 | ±0 | 同上 |
| `follow-rpc` | 78 | 78 | ±0 | 未动 |
| **合计** | **667** | 659 | **+8** | **+8 = +6 +2 ⇒ 与新增守门条数逐项吻合** ✅ |

- `failures=0 · errors=0 · skipped=0`（6 个模块逐个聚合）
- 🔴 **两个独立口径交叉验过**（AGENTS.md「取聚合数字必须按 key 对应，不可按行序 tail」）：
  ① maven 自己的模块汇总行（带模块名，reactor 顺序）= 117/140/143/121/68/78
  ② `target/surefire-reports/TEST-*.xml` 逐文件求和 = 同上
  ⇒ **667 可复算**（口径：`mvn -pl <6 个组件> test`）
- **门禁 5/5 EXIT=0**：`check-wave-lane-ownership` · `check-wave-role-model` · `check-wave-coupling` · `check-doc-links` · `check-ledger-count`
- ⚠️ **本轮刻意错峰**：G4 的 52 模块全量（09:19~09:42）与 G3 的 29 模块回归（09:42~09:51）期间**本域不起 maven**
  ⇒ 独占窗口才跑 ⇒ **未出现 `EXIT=137`**（AGENTS.md §4.1 错峰纪律有效）。
  - 🔴 **订正（2026-10-27 · 第十八轮 · G3 回执）**：「G3 的 **29** 模块回归」**写错了** ⇒ **G3 的域级回归是 12 个模块**（4 域 × 3 组件）· 「**29**」= G4 第八次全量的**有测试模块数**（52 / 29 / 2461 条）⇒ **两处口径串台**。
    ⇒ **已独立复核**（2026-10-27 13:1x · G3 树实测）：域内 12 模块**逐个有 surefire 报告** · 129 份 · **条数 922** · **0 份陈旧报告**（mtime 全部 ≥ 12:30）⇒ 「12 模块」成立。
    ⇒ ⚠️ **错峰结论不变**（本域确实等 G3 跑完才起 ⇒ 无 `EXIT=137`）⇒ 只订正**数字**。
    ⇒ 📌 同步订正 ⇒ [`../admin/rpc.md` §12.6](../admin/rpc.md)。

### 10. 🔴 必查项（共享层合入后必须复跑）—— **本轮 667 条不是最终数字**

| # | 共享层变更 | 我域同构证据 | 为什么要复跑 |
|---|---|---|---|
| 1 | G4 `#8`（`3b573fb`）纯 gRPC 进程关闭路径的 `NoClassDefFoundError: jakarta/servlet/Filter` ⇒ 拆出 `HttpPoolLifecycle`（`@ConditionalOnWebApplication(type=SERVLET)`） | 我域 **3 个 rpc 组件全部 `@Import ObservabilityLifecycle`** ⇒ `UserRpcApplication.java:57` · `AdminRpcApplication.java:71` · `FollowRpcApplication.java:77` | 修好 #8 ⇒ `ObservabilityLifecycle.@PreDestroy` **真的会执行** `RpcInterceptor.shutdown()` ⇒ **三个进程级静态池**（`static final` ⇒ 终止即永久）的潜伏缺陷**显形** ⇒ 可能出现**跨测试污染型偶发红**（`ScheduledThreadPoolExecutor[Terminated]`）⇒ ⚠️ **这正是「绿是因为没走到」**：#8 的缺陷此前恰好把保护罩挡住了 |
| 2 | G4 `7488b3d`（池改 `AtomicReference` + CAS 自旋 ⇒ **终止后按需重建**） | 同上（我域三个 rpc 同构） | 修法在共享层 ⇒ 修完必须复跑我域 6 组件 ⇒ **G2 域已登记同一项** |

⇒ ⚠️ **所以**：上表的 **667 条是跑在 G4 修 #8 / 池自愈**之前**的共享层上**
⇒ ⇒ **波末收口回归必须重跑**（C7）⇒ **本域不会拿 667 当最终数字**。
⇒ 📌 **一个纪律记录（今天踩了两次）**：判「有没有别的 lane 在跑 maven」**不能**用 `ps | grep -c` ⇒ **该命令自身的命令行含模式串 ⇒ 自匹配**
⇒ ⇒ **要判「有哪棵树」**（`multiModuleProjectDirectory=` 后缀），或把判据写进**独立脚本文件**（argv 不含模式名）。
（今天已在等待器上踩过一次 ⇒ 又在起回归前踩一次 ⇒ 两次都被自己抓到 ⇒ 没造成 137。）

---

## 2026-10-28 第二十二轮：`DRIFT-USERRPC-V-015`（新立 · ✅ 已修 · 域内自闭环）—— Go 的 `span.RecordError`【不】置 span 状态 ⇒ RAG 多置 ERROR（**遥测面**漂移）

### 1. 现象与判据

Go 的 `span.RecordError(err)` **不**把 span 状态置成 ERROR ⇒ ⇒ Go logic 层这些错误分支导出后
span 状态恒为 **`Unset`** ⇒ ⇒ **RAG 侧多置一个 `ERROR`** 就是**遥测面漂移**（导到 trace 里的 span 状态字段不同）。

| 项 | 证据（逐条）|
|---|---|
| **机制（SDK 原文）** | `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:631-656` `recordingSpan.RecordError` 的**方法注释**：<br>“SetStatus is required if the Status of the Span should be set to Error, **this method does not change the Span status**.”<br>⇒ 实现只 `s.addEvent(semconv.ExceptionEventName, opts...)`（带 `exception.type`/`exception.message`）⇒ **全程不碰 `s.status`** |
| **版本来源** | Sea 仓 `go.mod:18-21` pin `go.opentelemetry.io/otel` ＋ `/sdk` **v1.44.0** |
| **Go 侧调用点（user-rpc）** | `service/user/user/rpc/internal/logic/`：`loginlogic.go:54` · `registerlogic.go:53,60,69,85` · `deleteuserlogic.go:48,56` ⇒ **7 处 `span.RecordError`，零处 `span.SetStatus`** |
| **Go 侧调用点（admin-rpc）** | `service/user/admin/rpc/internal/logic/`（8 个 logic）⇒ **16 处 `span.RecordError`，零处 `span.SetStatus`** |
| **⚠️ 别混的那一条** | Go **共享层** `service/common/observability/observability.go:103-105` 是**显式三句**（`RecordError` ＋ `SetAttributes` ＋ **`SetStatus(otelcodes.Error, msg)`**）⇒ ⇒ **那条路径 Go 确实置 ERROR** ⇒ ⇒ RAG 共享层 `Observability.markSystemError`/`markTimeout` 照此实现是**对的** ⇒ ⇒ **「logic 层只 RecordError」与「共享层 helper 三句」不是一回事** |
| **Go 腿运行时实测**（otel-go v1.44.0 真跑）| 形态 A（只 `RecordError`）⇒ 导出 span `status.Code=Unset` ＋ 1 条 `exception` 事件；形态 B（`observability.go:103-105` 三句）⇒ `status.Code=Error` |

### 2. RAG 侧现状（修前）

| 文件 | 站点 | Go 对应 | 修前 RAG |
|---|---|---|---|
| `UserDomainService.java` | login 的 DB 查询失败 | `loginlogic.go:54` | 🔴 `recordException` ＋ **`setStatus(ERROR,"DB查询失败")`** |
| `UserDomainService.java` | register 的 DB 查重失败 | `registerlogic.go:53` | 🟢 只 `recordException`（**与 Go 一致**）|
| `UserDomainService.java` | register 的密码生成失败 | `registerlogic.go:60` | 🔴 `recordException` ＋ **`setStatus(ERROR,"密码生成失败")`** |
| `UserDomainService.java` | register 的 uid 生成失败 | `registerlogic.go:69` | 🔴 `recordException` ＋ **`setStatus(ERROR,"uid生成失败")`** |
| `UserDomainService.java` | register 的 DB 更新失败 | `registerlogic.go:85` | 🔴 `recordException` ＋ **`setStatus(ERROR,"DB更新失败")`** |
| `UserDomainService.java` | delete 的 DB 查询失败 | `deleteuserlogic.go:48` | 🟢 只 `recordException`（**与 Go 一致**）|
| `UserDomainService.java` | delete 的 DB 更新失败 | `deleteuserlogic.go:56` | 🟢 只 `recordException`（**与 Go 一致**）|
| `AdminSpans.java` | `traced` 的 catch 分支（覆盖 admin 侧 16 处）| admin 侧 8 个 logic | 🔴 `recordException` ＋ **`setStatus(ERROR, msg)`** |

⇒ ⇒ **域内 `setStatus(StatusCode.*)` 命中 = 5**（user-rpc 4 ＋ admin-rpc 1）⇒ ⇒ **且 user-rpc 自身 4 处置 / 3 处不置 ⇒ 自相矛盾，无任何台账解释**。

### 3. 修法（域内自闭环 · 零共享层改动）

- **删掉那 5 处 `span.setStatus(StatusCode.ERROR, …)`**（**保留 `recordException`** —— **事件面本就与 Go 一致**：Go 的 `RecordError` 就是「加一条 exception 事件」）
- 共享层 `service/common` 的 `Observability` **一行未动**（它照 Go 的显式 `SetStatus` 实现，是对的）
- 每处留 Go 出处注记（AGENTS.md §3.2），写明「Go 的 `RecordError` 不改状态」与 SDK 注释原文

### 4. 守门与证据强度（**L4 · 双侧运行期**）

- **新守门** `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/service/UserRpcSpanStatusGoParityTest.java`（**5 条**）
  - ①②③ **运行期**：自装 OTel SDK ＋ 收集型 `SpanExporter` ⇒ 断言**导出的 span 数据**（先断言 span 真被导出 ＋ 带 `exception` 事件，**再**断言 `status == UNSET` ⇒ 「没产生 span」不会被误读成「状态对了」）
  - ④ **源扫**：域内主源码**零处** `setStatus(StatusCode.*)`（**先剥注释**；**排除共享层 `users-common`**）
  - ⑤ **判据自检**：造假文件 ⇒ 扫描器必须报出它；再验「注释里的字面量必须不被报」
- **修前基线**：5 条里 **2 红** —— ① **红在状态断言**（实得 `ERROR`）· ④ 红在源扫命中（**5 处**，与手工 grep 逐条一致）；②③⑤ 绿（**② ③ 是内建对照** ⇒ 证明有判别力）
- **修后**：**5 条全绿**；既有 `AdminSpanWiringTest`（3 条）**仍全绿** ⇒ 删 `setStatus` 没打破既有 span 装配守门
- **变异实证**（对最终代码）：加回 login 那处 `setStatus` ⇒ `maven EXIT=1` ⇒ **2 红且红在目标断言** ⇒ 回滚后三文件 **sha256 逐字节一致**

### 5. 📌 附带发现（对共享层台账的**事实订正**）

`docs/DRIFT/global/vs-common.md` §4.10.64 ⑤ 断言「Go 的 `span.RecordError(...)` **会把 status 置 ERROR** ⇒ Java 必须显式补」
⇒ ⇒ **与上面 SDK 注释原文正好相反** ⇒ ⇒ **已发信给 G4 订正**（并提醒 G3 别照它加 `setStatus`，否则是在 G3 域**新造**漂移）。
⇒ ⇒ 域级台账 ⇒ [`../vs-common.md` §59](../vs-common.md)。


---

## 🔴 `DRIFT-USERRPC-V-016` · **「OS 盲选口」族** · 本组件 **5 处**（2026-10-28 · G1 lane）

> 📌 **机制 / 本机实测（Q3·Q6·Q7·Q8）/ 守门细节 / 效力边界 ⇒ [`docs/DRIFT/users/vs-common.md` §65](../vs-common.md)**
> ⇒ 本条只记**本组件那一份**（不抄机制表，避免两处漂移）。

| 族 | 位置（**改造后**行号） | 状态 |
|---|---|---|
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `UserRpcSlowThresholdGateTest.java:304` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `UserRpcSlowThresholdGateTest.java:331` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `UserRpcGrpcInterceptorWiringTest.java:139` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `UserRpcLogoutClientVisibleGrpcStatusTest.java:221` | 已改 |
| A/C 族（`forPort(0)` ／ `new ServerSocket(0)` 探针） | `UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.java:225` | 已改 |

- **危害**：`forPort(0)` / `new ServerSocket(0)` / `RANDOM_PORT` 三种写法都是**让 OS 从临时端口段（49152–65535）里盲选** ⇒ ⇒ 与 Docker 随机发布口**同段**
  ⇒ ⇒ 邻居占**更具体地址** `127.0.0.1:P` 时我方**通配绑定照样成功**（实测：两个监听者并存）⇒ ⇒ 客户端被抢 ⇒ **静默假红**（看着像被测代码炸了，其实压根没被请求到）。
- **修法**：全部改走**域内私有** `support/RealGrpcPort`（`support/RealTomcatPort`）⇒ **段**：`22220–22239`（共享层预分配的 users 域段 22200–22299 的子段）⇒ 段内挑口 ＋ **双地址 bind 探针**（只探通配**检不出**「更具体的占用者」）。
- **段来源**：共享层已预分配给本域的段（`docs/DRIFT/global/vs-common.md` §4.10.19.3）⇒ **共享层台账零改动**（写权归 G4）。
- **守门**：域级 `BlindPortGateTest`（落在 **user-rpc**，`service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java`）**5 条** ⇒ 一并覆盖本组件 ⇒ **本组件不单独建守门**（避免两份规则漂移）。
- **边界**：**本组件生产代码零改动**（全部落在 `src/test/java`）⇒ ⇒ **不构成 Go 对照项**（Go 侧无对应物 ⇒ 无对照义务）。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）· `V-014` / `V-015` / `V-016` 三条 + §9 的 4 条 UNRESOLVED 闭环记录**
>
> | 条目 | 原判 | 现行态 | 2026-10-29 实测依据（两侧） |
> |---|---|---|---|
> | `DRIFT-USERRPC-V-014` | ✅ 已修（域内自闭环 · Redis 断连策略） | ✅ **维持** | 断连策略 **REJECT_COMMANDS 生效**：`service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:96` `@AutoConfiguration` ＋ `:98` `afterBeanInitialization(LettuceClientOptionsBuilderCustomizer.class, …)` ⇒ `:41` javadoc 记 `ClientOptions.DisconnectedBehavior#REJECT_COMMANDS`；且已由 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:5` 注册 ⇒ **共享层那份真的在类路径上** ✔<br>⇒ **域内副本确已删除**（`find service/users -name "RedisDisconnectedBehaviorConfig.java"` ⇒ **0 命中**；`grep -rn "REJECT_COMMANDS" service/users/**/src/main/` ⇒ **0 命中**）⇒ 与本条 2026-10-28 订正块「域内副本已于收编轮删除」**一致** ✔ |
> | `DRIFT-USERRPC-V-015` | ✅ 已修（Go 的 `span.RecordError`【不】置 span 状态） | ✅ **维持** | Go `service/user/`（user-rpc + admin-rpc）**零处** `span.SetStatus`（grep ⇒ 0）· Go 全仓 **9** 处；RAG `service/users/**` 主源码**零处** `span.setStatus`（grep ⇒ 0）⇒ **那 5 处确已删净** ✔ |
> | `DRIFT-USERRPC-V-016` | ✅ 5 处「OS 盲选口」已改 | ✅ **维持** | 5 处**逐条**改为 `RealGrpcPort.pick()`：`UserRpcSlowThresholdGateTest.java:304` · `UserRpcGrpcInterceptorWiringTest.java:139` · `UserRpcLogoutClientVisibleGrpcStatusTest.java:221` · `UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.java:225`（**均已核过**）＋ 域级守门 `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java` ✅ **存在**（**5** 条 `@Test`）✔ |
> | `UNRESOLVED-USERRPC-004` | ✅ 已闭环（活体**证伪**了一条旧结论） | ✅ **维持** | 复现入口 `tools/difftest/exp-difftest-redis-failfast.sh` ✅ **存在**；被证伪的那条旧结论（「Go 读 NULL status 会扫描报错」）**至今仍是被证伪状态**（Go `data_model.go:11` 无 `not null` ⇒ 读出 0）✔ |
> | `UNRESOLVED-USERRPC-005` | ✅ 已闭环（双侧对称并发启动） | ✅ **维持** | 机制**仍在**：`service/users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:91` 锁号 `8_675_309L` ＋ `:115` `pg_advisory_lock` ＋ `:128` `pg_advisory_unlock` ⇒ **并发建表仍被串行化** ✔；复现入口 `tools/difftest/exp-difftest-concurrent-schema.sh` / `exp-go-concurrent-automigrate.sh` ✅ **均存在** ✔ |
> | `UNRESOLVED-USERRPC-006` | ✅ 已关闭（真库对照） | ✅ **维持** | DDL 逐列结论**仍成立**（本文件 §4 与 `docs/DRIFT/users/_shared.md` §2 的复核块已逐条核过）；机器复算入口 `tools/difftest/check-ddl-static.py --domain users` ✅ **存在**（**2026-10-29 实跑**：类型/长度 **0** 差异 · 可空性 **1** · DEFAULT **8** · 加 `--indexes` 合计 **16** 处实质差异）✔ |
> | `UNRESOLVED-USERRPC-007` | ✅ 已闭环（双侧活体对拍 · 43 场景 / 39 等价 / 4 处差异全是 N-2） | 🟡 **维持闭环，但「43/39/4」这组数字标为「待核实」** | ⚠️ 该组数字**来自 2026-10-24 的一次性探针**（`/tmp/userprobe/probe.py` · **`/tmp` 脚本，已不存在**）⇒ **本机不可复算**；本轮**未复跑** ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」；<br>⇒ **可复算的替代入口已落仓**：`tools/difftest/exp-difftest-grpc-user9.sh` ＋ `tools/difftest/grpc-user9-dual-port-runner.java` ＋ `tools/difftest/diff.py`（**均存在**）⇒ **要重跑请用它们，不要找 `/tmp/userprobe/`** |
>
> - **不改判的理由**：上表 7 条断言的都是「**某个具体事实还在不在**」；
>   逐条实测后**全部与台账所写一致**（唯一需要标注的是 `USERRPC-007` 的**数字口径**不可复算）⇒ **状态全部维持**。
> - ⚠️ **本轮未复跑任何双侧活体对拍**（需起两套真栈 + 真 PG/Redis，本轮只改台账）⇒
>   **所有依赖活体实测的条目一律维持原证据强度，不升级、不降级**。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **EXIT=1**（2026-10-30 03:53:22 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**） ⏐ ==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源） ⏐ ==> [3/5] 构建（Go 二进制 + RAG jar） ⏐ ==> [4/5] 起两套 user-rpc（Go :19005 / RAG :59211） ⏐ ==> [5/5] 编译并跑双端口对拍。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../../_cross/live-parity-2026-10-30.md)。

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W11

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-USERRPC-V-002 | D（经分析确认不实现：保持 NOT NULL 才是等价的那一侧） | P2 | 否 | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status     int64             \`gorm:"column:status;default:0"\`` ⇒ **无 `not null` tag** ⇒ GORM v2 对标量字段默认不加 NOT NULL ⇒ Go 侧 `status` **可空**）vs RAG `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（`status      BIGINT NOT NULL DEFAULT 0,` ⇒ **不可空**）⇒ **DDL 层可空性差异仍成立**（真 PG 活体对拍实测：`go=(nullable=YES)` ／ `rag=(nullable=NO)`，记录于 `docs/DRIFT/users/user/rpc.md:573`）⇒ **但两侧可观察结果相同**：Go 侧 GORM 对 `int64` 造 `**int64` ⇒ DB 值为 NULL 时**跳过 setter** ⇒ 读出 Go 零值 **0** 且不报错；RAG 侧 NOT NULL ⇒ **根本读不到 NULL** ⇒ 也是 0 ⇒ ⇒ **行为层零差异**；⇒ ⇒ **「放开可空」反而会制造新漂移**（RAG `callSettersOnNulls=false` ⇒ 读出 `null` ≠ Go 的 `0`；admin 侧更会从「NULL 当活跃(0)」翻成「NULL 当未知(null)」⇒ **方向相反**）⇒ ⇒ **经分析确认「保持 RAG 的 NOT NULL」＝ 本条不实现**（D 类构成要件「经分析确认」＋「明确理由」齐备；不是「还没查」）⇒ 归 **D**；⚠️ 本条**尚未销号**（销号签字归 G4 ／ 合并收口执行者，**落点无代码动作**）；**P2**：DDL 层差异真实存在但**零可观察行为影响** ⇒ 不命中阶段一 P0 九条 ⇒ **不阻断阶段一** | 2026-10-30 |

### lane W5

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-USERRPC-V-001 | D | P3 | 否 | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status int64 gorm:"column:status;default:0"` ⇒ 值类型 ⇒ GORM 恒写 0 ⇒ 该列永不出现 NULL）＋ `service/user/user/rpc/internal/logic/loginlogic.go:67`（`if user.Status == 1 {` ⇒ status 读成 0 ⇒ 不判封禁 ⇒ 放行登录）vs RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:260`（`if (user.getStatus() != null && user.getStatus() == 1L)` ⇒ fail-closed 守卫已删）＋ `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/domain/UserEntity.java:110`（`private Long status = 0L;` ⇒ NULL 读成 0）⇒ **差异已消失**（两侧同形：「status ⇒ 0 ⇒ 不判封禁 ⇒ 放行登录」；RAG 侧 `status BIGINT NOT NULL` ⇒ 更不可达）⇒ 已修（**证伪关闭**，commit `9ae121d`）⇒ 无落码动作 | 2026-10-30 |
| DRIFT-USERRPC-V-016 | D | P3 | 否 | Go `service/user/user/rpc/user.go:36`（`zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ 生产侧等价面；Go 全仓 `forPort(0)` ／ `new ServerSocket(0)` ／ `RANDOM_PORT` 三个模式串 **0 命中** ⇒ Go 侧无对应物 ⇒ 不构成 Go 对照项）vs RAG `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcSlowThresholdGateTest.java:304`（`ServerBuilder.forPort(RealGrpcPort.pick())`）＋ `UserRpcSlowThresholdGateTest.java:331` ＋ `UserRpcGrpcInterceptorWiringTest.java:139` ＋ `UserRpcLogoutClientVisibleGrpcStatusTest.java:221` ＋ `UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.java:225` ⇒ **差异已消失**（本组件 5 处「OS 盲选口」已全部改走域内私有固定段端口 `22220–22239`）⇒ 且**生产代码零改动**（全在 `src/test/java`）⇒ 无落码动作（域级守门 `BlindPortGateTest.java` 5 条在位） | 2026-10-30 |
| V-002 | D | P3 | 否 | Go `service/user/user/rpc/internal/model/data_model.go:11`（`gorm:"column:status;default:0"` ⇒ 值类型 · 无 `not null` ⇒ 建出可空列，但 GORM 恒写 0 ⇒ 实际永不出现 NULL）vs RAG `service/users/users-common/src/main/resources/db/01_create_user.sql:39`（`status BIGINT NOT NULL DEFAULT 0`）＋ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/domain/UserEntity.java:136`（`private Long status = 0L;` ⇒ 读成 0）⇒ **差异仍成立**（DDL 层可空性：Go `is_nullable=YES` ／ RAG `NO`）**但行为层零差异**（Go 读 NULL ⇒ 0；RAG 读不到 NULL ⇒ 也是 0）⇒ 2026-10-27 逐层证据链已推翻「必须与 admin 域同一次提交」⇒ 正确处置 = **保持 RAG NOT NULL**（放开可空反而制造新漂移：RAG `callSettersOnNulls=false` ⇒ 读出 `null` ≠ Go 的 `0`）⇒ 无落码动作；唯一残留落点 = `SD-2026-10-25-CAND-17` 签字（合并收口执行者写权面） | 2026-10-30 |
| V-013 | D | P3 | 否 | Go `service/user/user/rpc/internal/logic/getuserlogic.go:44`（`status.Error(codes.Internal, "DB查询失败")` ⇒ logic 层根本不返回 1015）vs RAG `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:209`（`catch (StatusRuntimeException e)` ⇒ `DEADLINE_EXCEEDED` 原样上抛）＋ `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:107`（PG `57014` ⇒ `Status.DEADLINE_EXCEEDED`）⇒ **差异仍成立**（**仅源码层形态**；客户端可见面两侧同为 1015 ⇒ **不构成 wire 漂移**）⇒ 归 D（**不得改回 Go 的 `codes.Internal`** —— 那样 RAG 渲染 5001 反而离 Go 更远；17 处注释理由已订正 + 类级 javadoc 完整论证在位）⇒ 无落码动作 | 2026-10-30 |

## 指标抓取出口补载体（2026-10-31 · G1 lane）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选 ⇒ 也不劫持任何既有编号的 `domain` / `wave` / `basis`。
> ⚠️ **本小节不重登记** ⇒ 本体仍是本文件既有的那条「指标可抓取性」行（`:280`）⇒ ⇒ **销账时不要当新条目**。

**一、落码清单（域侧自修 · 全部在 G1 写权面）**

| 落点 | 文件与行号 | 内容 |
|---|---|---|
| servlet 载体 | `service/users/user/user-rpc/pom.xml:218` | 新增 `spring-boot-starter-web`（**非 test 作用域**；版本由 `spring-boot-dependencies` BOM 管 ⇒ 不写 `<version>`） |
| HTTP 观测口 | `service/users/user/user-rpc/src/main/resources/application.yaml:231-232` | `server.port: ${USER_RPC_MANAGEMENT_PORT:9094}`（与 gRPC 业务口 49210 严格分离） |
| 暴露面 | `service/users/user/user-rpc/src/main/resources/application.yaml:266` | `include: health,info,prometheus,metrics`；`:269` 与 `:271` 打开两个 endpoint；`:275-277` 打开导出开关 |
| Nacos 自动注册 | `service/users/user/user-rpc/src/main/resources/application.yaml:115` | `spring.cloud.nacos.discovery.register-enabled: false` |

**二、Go 出处（真源 · 消费点 · 逐条读过）**

- `service/user/user/rpc/etc/user.yaml:23-30` —— `DevServer: {Enabled: true, Host: 0.0.0.0, Port: 9094, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true, EnablePprof: true}`
- `go-zero@v1.10.2/core/service/serviceconf.go:77` —— `devserver.StartAgent(sc.DevServer)`
- `go-zero@v1.10.2/internal/devserver/server.go:44-49` —— `if s.config.EnableMetrics { prometheus.Enable(); s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }`
- `go-zero@v1.10.2/internal/devserver/server.go:67-75` —— **独立端口** `http.ListenAndServe(addr, s.server)`（不是主端口）
- ⇒ ⇒ **端口一字不差**：Go 9094 ↔ RAG `${USER_RPC_MANAGEMENT_PORT:9094}`
- ⇒ ⇒ **路径不同**：Go `/metrics` + `/healthz` ↔ RAG `/actuator/prometheus` + `/actuator/health`（Actuator 固定前缀）⇒ **抓取配置改路径不改端口**（表达层差异，已按 `docs/twoStage.md` §10 定级）
- ⚠️ `management.metrics.tags.application` **刻意不加**（与 follow-rpc 同口径）：Go 侧 `service/user/user/rpc/internal/metrics/metrics.go:9-21` 用 `Namespace:""` + `Subsystem:"user_rpc"` ⇒ **不存在 `application` 标签** ⇒ RAG 侧 meter 名已自带 `user_rpc_` 前缀 ⇒ 再挂就是多一个标签 ⇒ 同一份 PromQL 两侧聚合结果会不同

**三、为什么必须同时关掉 Nacos 自动注册（必做项，不是可选项）**

- `AbstractAutoServiceRegistration implements ApplicationListener<WebServerInitializedEvent>`（spring-cloud-commons `AbstractAutoServiceRegistration.java:49`）
- ⇒ ⇒ 有了 web 容器 ⇒ 该事件**就会发** ⇒ ⇒ 自动注册被触发
- `NacosAutoServiceRegistration.getRegistration()`（`NacosAutoServiceRegistration.java:59-64`）取的是 **web server 端口**（= 观测口 9094），**不是** gRPC 业务口 49210
- ⇒ ⇒ Nacos 里会出现「user-rpc → 9094」这条**错端口实例** ⇒ ⇒ user-api 按它连 ⇒ 连到一个只提供 `/actuator/**` 的 HTTP 口 ⇒ ⇒ **gRPC 链路直接断（业务码塌成 1015）**
- ⇒ ⇒ 本域注册是**手写**的（`bootstrap/NacosRegistration.java` 用 `GrpcServerProperties.getPort()`）⇒ ⇒ 与 Go 侧 `zrpc.MustNewServer` 注册 `ListenOn` 同形 ⇒ ⇒ 自动注册必须关掉，否则同一服务名下会有两个端口的实例
- ⚠️ **实测坑（载体守门首跑踩到）**：devh 3.1.0 的 `GrpcServerAutoConfiguration` **无条件**起 gRPC server（`spring-configuration-metadata.json` 里**没有** `grpc.server.enabled` ⇒ **没有**「关掉 gRPC」的开关）⇒ ⇒ 守门必须**排除**它；排除后 `GrpcServerProperties` bean 随之消失 ⇒ ⇒ 还得**自己补一个空壳**；`GrpcServerFactoryAutoConfiguration` 的 `@ConditionalOnClass` 认 shaded Netty ⇒ ⇒ 本域 classpath 上有 ⇒ ⇒ **还得排除它**（否则 `GrpcServiceDiscoverer` 缺失 ⇒ 上下文起不来 ⇒ 4 条用例全 Error）

**四、守门网**

- `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcPrometheusCarrierGateTest.java` —— **6 条**（载体存在 / 暴露面真抓得到 / 指标名与 Go 同形 / 标签集与 Go 同形 / **api 侧中间件不在容器** / **rag.side=rpc 已声明**；新增 2 条见文末小节）
  - `servletContainerIsActuallyPresent` —— 上下文确实是 `WebServerApplicationContext`（= `WebApplicationType` 推导出 SERVLET ⇒ servlet 容器真在 classpath 上）
  - `prometheusEndpointIsReachableOnMainPort` —— 真 HTTP GET `/actuator/prometheus` ⇒ **200** ＋ body 含 `# HELP` 与 `# TYPE`
  - `exportedMetricNamesMatchGo` —— 指标名与 Go 逐条同形
  - `exportedLabelSetMatchesGo` —— 标签集与 Go 逐条同形 ＋ 两条负向断言（无 `application` 标签、无 `_total` 重复追加）
- `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/RealTomcatPort.java` —— 域内固定段 **22290–22294**（禁 OS 盲选口）
- ⚠️ 守门读的是**域侧真实 `application.yaml`**（本类不带自己的 management 配置）⇒ ⇒ **改坏配置就红**

**五、变异实测（判别力）**

- 变异 M1：`include` 里去掉 `prometheus` ⇒ **3 条转红**（端点 404 ＋ 指标缺失）
- 变异 M2：pom 里去掉 `spring-boot-starter-web` ⇒ **4 条转 Error**（上下文起不来）
- 变异 M3：写回 `management.server.port: -1` ⇒ **3 条转红**
- ⇒ ⇒ 三个变异**各自**精确转红、还原后复绿 ⇒ ⇒ **守门不是假绿**

**六、证据强度与诚实边界**

- 证据强度 **L3**（真 Tomcat ＋ 真 HTTP 抓取 ＋ 真 Prometheus 文本 ＋ 真指标名与标签核对）—— **不是 L4**
- ⚠️ **未做**「业务启动类在真 PG/Redis 下真起一遍」⇒ ⇒ **L4 活体验证仍缺**（需 Docker；本机 Docker 全停）
- ⚠️ **未做双侧活体对拍**（Go 侧真进程与 RAG 侧真进程的同请求抓取比对）
- ⚠️ 守门用**独立最小上下文**（`@SpringBootConfiguration` ＋ `@EnableAutoConfiguration` ＋ 显式 `@Import`，**无 `@ComponentScan`**）⇒ ⇒ 不被 PG/Redis 绑架 ⇒ ⇒ 测的正是「载体 + 暴露面 + 指标定义」三件事
- ✅ **「13 个 api 侧 web Bean 不在容器里」已全部落**（**2026-10-31 · 合并主干后**）：10 个组件扫描类 ＋ 3 个自动配置面（`GoStyleJsonContentTypeAdvice` / `GoStylePathNormalizingAutoConfiguration` / `GoStyleBreakerRouteTemplateAutoConfiguration`）⇒ ⇒ 判据按**结果**写「不在容器」⇒ ⇒ **两侧都绿** ⇒ ⇒ 那 3 个自动配置面此前「交不了」的原因是**本分支基线尚未含**共享层的 `@ConditionalOnApiSide`（`53bc05ec`）⇒ ⇒ **合并主干后即可写** ⇒ ⇒ 原写「跨分支不稳定 ⇒ 不给恒红守门」**已过期**（见文末小节）
- ⚠️ **载体改动的进程形态差异**（已按 `docs/twoStage.md` §10 登记为残余项，不修）：Go 侧是 `http.ListenAndServe` 的小 server（无 servlet、无业务路由）；RAG 侧是 Tomcat（有 servlet 容器，但**无任何业务 HTTP 路由**）⇒ ⇒ 两者对外可观测面（独立端口 + Prometheus 文本）一致

**七、端口号待统一分配**

- 现状取 **Go DevServer 端口一字对齐**（user-rpc 9094 · admin-rpc 9097 · follow-rpc 16065），且都用 `${<X>_RPC_MANAGEMENT_PORT:<默认值>}` env 可覆盖 ⇒ ⇒ 统一分配后**只改默认值一行**
- ⚠️ **端口号不是本域自选** ⇒ 等合并收口执行者一句话

**八、指针位移登记（供后续重锚用）**

- 本轮给 `application.yaml` 加了内容 ⇒ ⇒ **该文件行号整体下移** ⇒ ⇒ 指向它的硬指针（如 `docs/DRIFT/_cross/grading/W10.md` 里的 `application.yaml:89-90`、以及本文件 `:1947` 的 `:40-45`）**本轮未重锚**
- ⇒ ⇒ 理由：**端口号还要再改一次**（统一分配）⇒ ⇒ **重锚应在端口定稿后做一次**，避免改两遍
- ⇒ ⇒ 已就地核对：本轮**只改注释**的 4 个 java 文件（`UserRpcApplication.java` / `AdminRpcApplication.java` 与两个 `NacosRegistration.java`）**行数与 HEAD 完全一致** ⇒ ⇒ 指向它们的代码行指针（`AdminRpcApplication.java:85` 与 `:112`、`UserRpcApplication.java:118`、两个 `NacosRegistration.java` 的 `:56`/`:62`/`:77`/`:96`/`:110`）**未漂移**

---

## 载体运行期守门扩面：rpc 侧隔离（新增 2 条）

**一、为什么这条现在才必须断言（背景）**

- 本域 rpc 原先是**纯 gRPC 进程** ⇒ classpath 上**没有** spring-webmvc ⇒ ⇒ 那 10 类 api 侧中间件**连类都加载不了**（`NoClassDefFoundError`）⇒ ⇒ 「不在容器」当时是**免费的**
- 载体任务给 rpc 加了 `spring-boot-starter-web` ⇒ ⇒ classpath 上**有了** webmvc ⇒ ⇒ 这 10 类**技术上可以活** ⇒ ⇒ 「它们仍然不在」才变成一个**需要守门的事实**

**二、新增 2 条（判据按「结果」写，不按「机制」写）**

| 用例 | 判据 | 为什么这个写法跨分支稳 |
|---|---|---|
| `apiSideMiddlewareIsAbsentFromRpcContainer` | **13 个** api 侧 web Bean 的 `getBeanNamesForType` **全为空** | **本树已含** `@ConditionalOnApiSide`（`53bc05ec`）＋ `rag.side: rpc` ⇒ ⇒ 13 类**分两批挡**（10 个扫描类靠域侧约定 ＋ 3 个自动配置面靠 `@ConditionalOnApiSide`）⇒ ⇒ **两侧同机制、结果相同** ⇒ ⇒ 断言「结果」⇒ ⇒ 两侧都绿 ⇒ ⇒ **且有牙**（变异 `rag.side → api` ⇒ 精确转红，见文末小节） |
| `rpcSideFlagIsDeclared` | 读**域内真实 yml**（本测试**不自带** `rag.side`）⇒ 值 = `rpc` | 防「那行被删 / 被改成 api」；✅ **合并主干后它已有消费者**（共享层 `@ConditionalOnApiSide` = `@ConditionalOnProperty(name="rag.side", havingValue="api")` ⇒ `53bc05ec`）⇒ ⇒ 这行 yml **从此是必做项**，不再是防御性配置 |

被断言的 **13 类** = **10 个组件扫描类**（`@Component` / `@Configuration`）：`GoStyleFormLimitWebMvcConfig` · `GoStyleFormValueLimitInterceptor` · `GoStyleJsonBodyConverterConfig` · `GoStyleTomcatParameterLimitCustomizer` · `GoStyleTraceparentFilter` · `GoStyleBreakerFilter` · `GoStyleCpuSheddingFilter` · `GoStyleMaxBytesFilter` · `GoStyleMaxConnsFilter` · `HttpMiddlewareFilter` ＋ **3 个自动配置面**（走 `AutoConfiguration.imports` ⇒ 与组件扫描解耦）：`GoStyleJsonContentTypeAdvice` · `GoStylePathNormalizingAutoConfiguration` · `GoStyleBreakerRouteTemplateAutoConfiguration`

**三、Go 出处（逐行读过）**

- `service/user/user/rpc/user.go:36` ⇒ `s := zrpc.MustNewServer(c.RpcServerConf, ...)`
- ⇒ ⇒ Go 侧 rpc 是 `zrpc.MustNewServer` ⇒ ⇒ **零 rest 中间件**（`rest.With*` 全在 api 侧 `service/<域>/api/internal/handler/routes.go`）⇒ ⇒ RAG 侧 rpc 容器里**不得出现** api 侧中间件

**四、跨分支差异（同一句断言、两种机制、两种含义）**

- **本树已含** `@ConditionalOnApiSide`（`53bc05ec`，**2026-10-31 合并主干后**）＋ `rag.side: rpc`（`e86ec93b`）⇒ ⇒ **13 类全部不注册** ⇒ ⇒ rpc 侧 web 容器上**零个 api 侧中间件** ⇒ ⇒ **载体任务没有引入偏离**，与 Go 的 `zrpc.MustNewServer` 同形
- **13 类分两批挡**（两批机制不同、结果相同）：
  - 10 个**组件扫描类** ⇒ 靠**域侧约定**不活（`UserRpcApplication` 的 `@ComponentScan` 刻意不扫 `com.rag.common`，启动类注释写明不能加）⇒ 不注册 —— **第一道**
  - 3 个**自动配置面** ⇒ 走 `AutoConfiguration.imports` ⇒ ⇒ 「不扫 `com.rag.common`」**挡不住**它们 ⇒ ⇒ 靠 `@ConditionalOnApiSide` ＋ `rag.side: rpc` 挡 —— **第二道** ⇒ ⇒ 两道叠加 ⇒ ⇒ 「13 类全不在」这一条**跨分支稳定**（主干与本树同机制）⇒ ⇒ 原写「跨分支不稳定 ⇒ 不给恒红守门」**已过期** ⇒ ⇒ **守门已落**（见文末小节）
- ⇒ ✅ **该边界已消解**：合并主干后本树与主干**同机制**（13 类全不注册）⇒ ⇒ **载体功能结论**与**「中间件洁净度」结论都可外推** ⇒ ⇒ ⚠️ 仅保留一条边界：读数依赖 Docker 容器（条数口径见文末小节）

**五、那 3 个面是谁（自读主干 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，共 7 项）**

- `GoStyleJsonContentTypeAdvice` —— `@AutoConfiguration` ＋ `@ConditionalOnWebApplication(SERVLET)`
- `GoStylePathNormalizingAutoConfiguration` —— 同上
- `GoStyleBreakerRouteTemplateAutoConfiguration` —— `@AutoConfiguration` ＋ `@ConditionalOnClass(RequestMappingHandlerMapping)`
- ⚠️ **前两个的开关就是「有没有 web 容器」⇒ ⇒ 载体任务正是把它们从「不活」翻成「活」的那个开关**（第三个靠 classpath 有没有 spring-webmvc ⇒ 同一个开关）
- ⇒ ⇒ **曾登记为「载体引入的行为变化」**：Go 侧 rpc 的 9094 是 go-zero DevServer（`devserver.StartAgent` ⇒ 独立端口 `http.ListenAndServe`，**不跑 rest 中间件链**）⇒ ⇒ RAG 侧 rpc 的 web 容器上会多挂 3 层 api 侧中间件 ⇒ ⇒ ⚠️ **2026-10-31 合并主干后该登记已消解**：共享层的 `@ConditionalOnApiSide`（`53bc05ec`）＋ `rag.side: rpc` ⇒ ⇒ **这 3 个面在 rpc 侧也不注册** ⇒ ⇒ **不是偏离** ⇒ ⇒ **本域不自行签字，也无需登记**

**六、变异实测（合并主干前 · user-rpc 单跑 · 三条；⚠️ 其中第 3 条在合并主干后已过期 ⇒ 见文末小节）**

| 变异 | 实测结果 | 读法 |
|---|---|---|
| `@Import` 一个**有依赖**的 api 侧类（`GoStyleBreakerFilter`） | **6 条全 Error**（上下文起不来：`No qualifying bean of type 'GoStyleProtectionProperties'`） | ⇒ **api 侧中间件一旦进 rpc 容器，rpc 域直接起不来** ⇒ 比「断言红」更硬 |
| `@Import` 一个**无依赖**的 api 侧类（`GoStyleTraceparentFilter`） | **6 条里 1 条 Failure / 0 Error**（唯一红的是 `apiSideMiddlewareIsAbsentFromRpcContainer`） | ⇒ 新断言**有牙**：**精确转红**，不是上下文挂掉 |
| （负面实测）把 yml 的 `rag.side` 改成 `api` | `rpcSideFlagIsDeclared` **红**；`apiSideMiddlewareIsAbsentFromRpcContainer` **仍绿** | ⇒ **实测坐实边界**：**合并主干前**本分支上护栏是「不扫 common」不是 `rag.side` ⇒ ⇒ **这条类断言在合并主干前抓不到 `rag.side` 回归**；⚠️ **合并主干后已过期**（同一变异现在**类断言也红** ⇒ 有牙）⇒ ⇒ **现读数见文末小节** |

⇒ ⇒ 三个变异**全部还原**（`git diff` 只剩 3 个测试文件的 +105 行新增，`application.yaml` 无 diff）⇒ ⇒ ⚠️ **合并主干后重测的第四个变异（三域同跑）也已还原**（见文末小节）

**七、回归（2026-10-31 · 三域同跑）**

- ⚠️ **口径订正（2026-10-31 · 合并主干后复跑）**：上面那个 **379** 是 **surefire XML 读数**，**随 Docker 的故障形态变化** ⇒ ⇒ **不能当覆盖面用**（详见文末小节）⇒ ⇒ **覆盖面口径 = 源声明测试方法数**：基线 `7bc9df3c` **445** ⇒ 载体扩面 `e61360d9` **451**（**+6** = 三域各 2 条，数目严丝合缝）⇒ ⇒ 合并主干 `27fb2a6c` **仍是 451** ⇒ ⇒ **一条未丢**（三域测试类 32+29+17 = **78 类**，全程未变）
- ⇒ ⇒ 证据强度 **L3**（真容器 + 真 HTTP 抓取已验；真 PG/Redis 那层**未跑** ⇒ 到不了 L4）

---

## 合并主干后的判别力实测 ＋ 回归条数口径订正

**一、这一节解决两件事**

- ① **判别力**：「13 类 api 侧 web Bean 全不在容器里」这条断言，在合并主干（`27fb2a6c`）后**到底有没有牙**？⇒ ⇒ 实测（第四个变异）
- ② **条数口径**：上文那个 **379**（以及本轮中途观察到的 **299**）**能不能当覆盖面用**？⇒ ⇒ **不能** ⇒ ⇒ 订正

**二、第四个变异：`rag.side: rpc` → `api`（三域同跑，2026-10-31 02:0x–02:1x）**

| 变异 | 实测结果 | 读法 |
|---|---|---|
| 三域 yml 的 `rag.side` 改成 `api` | **三域各 `Tests run: 6, Failures: 2, Errors: 0`** —— ① `rpcSideFlagIsDeclared` 红（`expected: "rpc" but was: "api"`）② `apiSideMiddlewareIsAbsentFromRpcContainer` 红（`Expecting empty but was: ["com.rag.common.http.GoStyleJsonContentTypeAdvice"]`） | ⇒ ⇒ **与合并主干前那个负面实测结论相反**：合并前该变异只让「配置断言」红、**类断言仍绿** ⇒ ⇒ 合并后**类断言也红** ⇒ ⇒ 「13 类全不在」**有牙**（不是恒绿）⇒ ⇒ 且 **0 Error** ⇒ ⇒ 红在**目标断言**，不是上下文起不来 |

- ⇒ ⇒ **机制侧的旁证**（共享层，非本域）：`GoStyleApiSideMiddlewareActivationGateTest` **5 条全绿**（形态③ vs 形态⑤ **只差 `rag.side` 一个变量**）⇒ ⇒ `@ConditionalOnApiSide` 这个**元注解真生效**（AGENTS.md §3.3：注解存在 ≠ 机制生效）
- ⇒ ⇒ **三个 yml 全部 `git checkout` 还原**（`side: rpc`）⇒ ⇒ 还原后复跑 **三域各 6 条全绿**

**三、⚠️ 同一个变异、两个结论 —— 判别力不能跨基线继承**

| 基线 | `rag.side → api` 的实测 | 结论 |
|---|---|---|
| 合并主干**前**（本分支基线无 `@ConditionalOnApiSide`） | 只有配置断言红；类断言**仍绿** | ⇒ 当时的边界**准确**：类断言抓不到 `rag.side` 回归 |
| 合并主干**后**（本树已含 `53bc05ec`） | 配置断言 **＋ 类断言** 都红 | ⇒ 当时的边界**已过期**：类断言**有牙** |

- ⇒ ⇒ **教训**：同一句断言的判别力**取决于基线** ⇒ ⇒ 「这条断言有没有牙」**不能跨基线继承** ⇒ ⇒ 必须**在当前基线上重测**（本轮已重测）

**四、⚠️ 回归条数口径订正（上文那个「379 条」不能当覆盖面用）**

- **覆盖面口径（环境无关，可跨 commit 比）= 源声明测试方法数**：`7bc9df3c`（基线）**445** ⇒ `e61360d9`（载体守门扩面）**451**（**+6** = 三域各 2 条，数目严丝合缝）⇒ ⇒ `27fb2a6c`（合并主干后）**仍是 451** ⇒ ⇒ **一条未丢**（三域测试类 32+29+17 = **78 类**，全程未变）
- **执行条数口径（环境相关）**：本轮 Docker 恢复后复跑 = **三域合计 417 条**（user-rpc **181** ＋ admin-rpc **139** ＋ follow-rpc **97**）· **Failures = 0** · **Errors = 0** · **Skipped = 0** ⇒ ⇒ **BUILD SUCCESS**
- ⚠️ **为什么之前会看到 379 / 299 两个数**：容器在 `@BeforeAll` 起不来 ⇒ ⇒ **整类 N 个方法折叠成 1 条 error** ⇒ ⇒ 总条数**变小** ⇒ ⇒ **两个读数都是「折叠后」的读数，口径不同 ⇒ 不可相减**
- ✅ **交叉验证**：把折叠掉的类按**源方法数**展开 ⇒ 得 **417**（181 / 139 / 97）⇒ ⇒ 与 Docker 恢复后的**真实读数逐条吻合** ⇒ ⇒ **折叠假说成立**（不是「丢了 80 条测试」）
- ⚠️ **教训**：「测试条数下降」这个现象**先分清是「覆盖面变小」还是「读数折叠」** ⇒ ⇒ 判据 = **源声明方法数**（与环境无关）＋ **XML 读数**（与环境相关）**两个数一起报** ⇒ ⇒ 只报一个数会误判

**五、⚠️ Docker 故障的根因（不是「整个停了」，是「存储层损坏」）**

- 现象：容器类**类级 error**，消息是 `Can't get Docker image: RemoteDockerImage(imageName=postgres:16-alpine, …)` ⇒ ⇒ **拉镜像阶段**失败
- 根因：Docker Desktop 的 containerd content store **blob I/O 错误** —— `blob sha256:16bc… expected at /var/lib/desktop-containerd/daemon/io.containerd.content.v1.content/blobs/sha256/16bc…: open …: **input/output error**` ⇒ ⇒ **宿主磁盘 94% 满**（当时剩 763 MiB）⇒ ⇒ 写不进 blob ⇒ ⇒ 镜像起不来
- ⚠️ **与旧记的消息不是同一阶段**：旧记 `Previous attempts to find a Docker environment failed` = **守护进程完全不可达**；本轮 = **守护进程活着、镜像拉不动** ⇒ ⇒ 「Docker 整个停了」这句**不够准** ⇒ ⇒ 应写成「Docker 存储层因磁盘满损坏」
- ✅ **已自愈**：磁盘从 763 MiB 回到 **65 GiB**（15%）⇒ ⇒ Docker 守护进程 API 恢复 200（29.2.1 · 62 容器）⇒ ⇒ **本轮复跑全部转绿** ⇒ ⇒ 全反应堆 **0 Failures / 0 Errors**
- ⚠️ 共享层那 1 个 Error（`MybatisPlusInterceptorChainGateTest`）= **时序artifact**（它在 02:13 跑，Docker 尚未恢复）⇒ ⇒ 单独复跑 **3 条全绿** ⇒ ⇒ **已销号**（共享层写权面，本域只报不改）

**六、✅ L4 活体验证（本轮达成；上文那些「未做」里的一部分现在做完了）**

- **三域业务启动类在真 PG/Redis 下真起了一遍**：user-rpc **Tomcat 22290** · admin-rpc **Tomcat 22295** · follow-rpc **Tomcat 22360** ⇒ ⇒ 三个口都落在**域内固定段**（222xx / 223xx）⇒ ⇒ 本轮只证「**在真 PG/Redis 下起得来**」
- 本轮**真容器启动 35 次**（`postgres:16-alpine` / `redis:7-alpine`）⇒ ⇒ 三域含容器的用例**本轮真跑且全绿**（此前是「Docker 停 ⇒ 类级 error ⇒ 折叠」）
- ⚠️ **仍未覆盖（不得写成已闭环）**：① 域侧「logout 走的是共享层那个 Redis 期限代理」的**装配接线**（域侧 `AdminLogoutDeadlineHandoffTest` 用 **Mockito 替换数据层** ⇒ ⇒ **只证控制流，不证代理接线**）② **handler 级码集合面的 RAG↔Go 双侧活体对拍** ⚠️ **【2026-10-31 订正】** 原列的 ②「真 Redis 上『**预算耗尽 ⇒ 写不发生**』的**活体**行为（共享层的 `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest` 本轮 **3 条全 skipped** ⇒ ⇒ 需要外部 Redis 地址）」**已过期** —— 该守门在主干 **2026-10-31 03:23 真跑通过**（surefire `TEST-com.rag.common.redis.GoStyleRedisDeadlineLiveRedisWriteFaceGateTest.xml` ⇒ `tests=3 · failures=0 · errors=0 · skipped=0` ⇒ **不是 SKIP**）⇒ ⇒ 且它正是「**独立通道读回仍是基线值** ⇒ 命令真的没落进 Redis」那一格 ⇒ ⇒ **这一件已达成** ⇒ ⇒ **残余从三件收敛到两件**（① 装配接线 · ② 双侧活体对拍）⚠️ **【2026-10-31 再订正】** ② 的**写面那一半**已由 **Go 半边活体探针**补齐（`tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh` ⇒ 本轮实跑 **EXIT=0** · Go/RAG 两侧 PROBE 行**逐键相等**：`form=exhausted|write_applied=false|err_is_deadline=true|value_after=baseline` 两侧相同）⇒ ⇒ **「预算耗尽 ⇒ 命令不下发」两侧活体同形已坐实** ⇒ ⇒ **② 现只指 handler 级码集合面**（客户端可见码 {1015, 5001} 的**活体**对拍 ⇒ 探针测的是写面、不是 handler 级码 ⇒ 仍未做）
- ⇒ ⇒ 故 **E66 那格（admin 域）仍是「机制已落 · 活体验证未做」** ⇒ ⇒ **不改**

---

## user 域 logout 的期限感知归属（与 admin 侧同款 · 2026-10-31）

**一、Go 出处（逐行读过，不是转述）**

- `service/user/user/rpc/internal/logic/logoutlogic.go`（**全文 44 行** · 本轮 cat 全文）：
  - `:31-34` token 空 ⇒ `codes.InvalidArgument`「Token不能为空」
  - `:35-36` key = `fmt.Sprintf("user:jwt_blacklist:%s", in.Token)` ＋ `UserAuth.AccessExpire`
  - `:37` `err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))` ⇒ **写绑 ctx**
  - `:38-40` 写失败 ⇒ `status.Error(codes.Internal, "注销失败,Redis写入异常")`
  - `:42-43` 成功返回
  - ⇒ ⇒ **全文无 `ctx.Done()` 预检** ⇒ ⇒ Go **不是入口拒**，而是**让写去失败**
- ⇒ ⇒ **与 admin 侧同构**：`service/user/admin/rpc/internal/logic/logoutlogic.go`（**全文 46 行**）同样 `:38 SetexCtx(l.ctx, …)` ＋ `:39-42 codes.Internal`、同样无 ctx 预检
- ⇒ ⇒ **两侧 Go 的 handler 级码 = `INTERNAL`(5001)** ⇒ ⇒ **RAG 侧两个域都该不设域内抢先闸门**

**二、改前 RAG 侧现状（本轮修掉的那一处）**

- `service/users/user/user-rpc/.../UserDomainService.java:899` = `checkDeadline();` ⇒ 期限已耗尽时**在 Redis 写之前**就抛 `DEADLINE_EXCEEDED`
- ⇒ ⇒ handler 级码 = **1015**（≠ Go 的 5001）⇒ ⇒ **这一面与 Go 不同构**
- ⚠️ 共享层已**点名要 G1 去掉那处抢先检查**：`service/common/.../redis/GoStyleRedisDeadline.java:153-158`（原文：「⚠️ **但带域侧抢先 `checkDeadline()` 的路径根本走不到这里**（域侧检查先触发 ⇒ handler 级是 1015）⇒ ⇒ **完全同形需域侧去掉那处抢先检查 ⇒ 归 G1（users 域写权）**」）
- ⚠️ **合并约束（共享层写明）**：必须与共享层代理**同一个提交**进主干（无代理而先删 ⇒ 两道闸门都没了 ⇒ RAG 写、Go 不写 ⇒ 数据面回归）⇒ ⇒ **该代理已在本树**（主干 `53bc05ec`）⇒ ⇒ **约束已满足**

**三、改法与行数中性**

- `UserDomainService.java` **三处就地替换、行数 936 → 936 不变**（该文件有跨文件行号指针）：
  - `:888-897` 方法 javadoc（10 行 → 10 行）⇒ 补 Go 出处（全文 44 行 · 无 ctx 预检 · `:37`/`:38-40`）＋ handler 级码 = 5001
  - `:899` `checkDeadline();` → 1 行说明注释（**不再调**）
  - `:911-917` B-1 注释块（7 行 → 7 行）⇒ 改成 admin 侧**同款措辞**（集合口径 ＋ 共享层代理抛 `QueryTimeoutException` ⇒ 不被本分支拦 ⇒ 落 `catch (Exception)` ⇒ INTERNAL）
- ⇒ ⇒ **三个硬指针全部保住**：`:899`（含**共享面** `docs/DRIFT/global/vs-common.md:4989` 引的那个）· `:906` · `:931`（`users/user/rpc.md:2048` / `users/vs-common.md:792` / `:3196` 引的）
- ⚠️ 附带订正一处**本身就错的指针**：`docs/DRIFT/users/user/rpc.md` 原引 `UserDomainService.java:764` ＝ `uploadAvatar` 体内（**不是** logout 的闸门）⇒ 已改指 `:899`

**四、新守门（对齐 admin 侧 `AdminLogoutDeadlineHandoffTest`）**

- 新增 `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/service/UserLogoutDeadlineHandoffTest.java` —— **3 条**：
  - ① 域侧不再抢先拦：期限已耗尽时 logout **仍调用到** Redis 写（`verify(ops).set(...)`）
  - ② handler 级码 = `INTERNAL`「注销失败,Redis写入异常」（与 Go `codes.Internal` 逐字同形）
  - ③ 反向：token 为空仍 `INVALID_ARGUMENT`（防「撤闸门」顺手撤掉参数校验）

**五、变异实测（有牙 ⇒ 不是恒绿）**

| 变异 | 实测结果 | 读法 |
|---|---|---|
| 把 `checkDeadline();` **加回** `:899` | **3 条里精确 1 条转红**（`logoutReachesRedisWriteEvenWhenDeadlineAlreadyExpired` ⇒ `DEADLINE_EXCEEDED: rpc deadline reached`）· ②③ **仍绿**（二者不依赖 interrupt） | ⇒ ⇒ 新网**有牙**、且**红在目标断言**（①）⇒ ⇒ 闸门若被加回 ⇒ 必红 ⇒ ⇒ 防回退成立 |
| 还原 | **3 条全绿**（user-rpc 59 条 + admin-rpc 5 条；⚠️ 该轮是 **Mockito 层** ⇒ 真 Redis+gRPC 的活体用例跑在变异**前**那轮：1/1 绿 · 26.3s） | ⇒ ⇒ 还原后无残留 |

**六、⚠️ 证据边界（如实，不夸大）**

- 本轮证据强度 = **L3**（静态 ＋ 域内单测）⇒ ⇒ **不是 L4**
- 三处仍未覆盖：① 共享层 Redis 期限代理**是否真在**本域 Redis 连接链上（**装配接线**，跨 lane ⇒ G4 侧守）② **RAG↔Go 双侧活体对拍**⚠️ **【2026-10-31 订正】** 原列的 ②「真 Redis 上『**预算耗尽 ⇒ 写不发生**』的**活体**行为」**已达成**—— 共享层的 `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest` 在主干 **2026-10-31 03:23 真跑通过**（surefire `tests=3 · failures=0 · errors=0 · skipped=0` ⇒ **不是 SKIP**）⇒ ⇒ 以「**独立通道读回仍是基线值**」证明命令真的没落进 Redis ⇒ ⇒ **残余收敛到两件**（① 装配接线 · ② 双侧活体对拍）⚠️ **【2026-10-31 再订正】** ② 的**写面那一半**已由 **Go 半边活体探针**补齐（`tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh` ⇒ 本轮实跑 **EXIT=0** · Go/RAG 两侧 PROBE 行**逐键相等**：`form=exhausted|write_applied=false|err_is_deadline=true|value_after=baseline` 两侧相同）⇒ ⇒ **「预算耗尽 ⇒ 命令不下发」两侧活体同形已坐实** ⇒ ⇒ **② 现只指 handler 级码集合面**（客户端可见码 {1015, 5001} 的**活体**对拍 ⇒ 探针测的是写面、不是 handler 级码 ⇒ 仍未做）
- ⇒ ⇒ **E66 那格（admin 域）维持「机制已落 · 活体验证未做」** ⇒ ⇒ **不改**

**七、G4 转派单前提的一处订正（我自己核的）**

- 转派单写「调整 `UserDomainServiceDeadlineTest` 的 `GUARDED` 清单（logout 出列）」⇒ ⇒ **该类全仓不存在**（`find` + 全仓 grep 只在 `docs/DRIFT/global/vs-common.md` 里出现过这个名字）⇒ ⇒ **user 域没有 GUARDED 清单** ⇒ ⇒ **无清单可调**
- ⇒ ⇒ 真正受影响的是 **admin 域** `AdminDomainServiceDeadlineTest:26` 那句「`UserDomainService` 实有 **11 处** `checkDeadline()`」⇒ ⇒ 撤掉 logout 那处后是 **10 处** ⇒ ⇒ 已就地订正（**行数 148 → 148 不变** —— 该文件有共享面指针 `global/vs-common.md:5460` 引的 `:41-42` 与 `docs/Worktree.md:587` 引的 `:41-43`）

---

## gRPC channel target 显式 scheme（2026-10-31 · G1 lane）

- **落点（2 处，同形态）**：
  - `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcLogoutClientVisibleGrpcStatusTest.java:157`
  - `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.java:173`
  - 两处形态一致：`ManagedChannelBuilder.forAddress("127.0.0.1", GRPC_PORT)` ⇒ `.forTarget("dns:///127.0.0.1:" + GRPC_PORT)`
  - **同行替换**（行数不变）＋ **文件末尾追加**说明段 ⇒ ⇒ **零行位移** ⇒ `docs/DRIFT/_cross/grading/W5.md:20` 与 `docs/DRIFT/_cross/stage1-derivation.tsv` 指的 `:221` / `:225`（`return RealGrpcPort.pick();`）**逐字未动**（已逐个复核）
- **机制**：devh `net/devh/boot/grpc/client/autoconfigure/GrpcDiscoveryClientAutoConfiguration:29` = `@ConditionalOnBean(DiscoveryClient.class)`；`DiscoveryClientResolverFactory.priority()` = **6**（源码注释「More important than DNS」）⇒ 注册进**进程级全局** `io.grpc.NameResolverRegistry` 后，grpc 的 `refreshProviders()` 会把**默认 scheme** 判给它（priority 最高者胜）⇒ ⇒ 任何**无 scheme** 的 target 都被 discovery resolver 接管 ⇒ 把 `127.0.0.1:P` 当**服务名**去服务发现查 ⇒ `UNAVAILABLE` ⇒ ⇒ 典型「单跑绿、同跑红」（surefire 复用同一 JVM ⇒ 跨测试类顺序依赖）
- **⚠️ 本域实测口径（如实，不照抄上游措辞）**：M-C（把 target 回落成 `.forAddress("127.0.0.1", GRPC_PORT)`）⇒ **仍绿**，且全日志**零** resolver 接管痕迹 ⇒ ⇒ **本模块当前并不会被接管** ⇒ ⇒ **这 2 处属「预防性修正」，不是修一个正在起火的红**
  - 原因（本轮实测）：本模块测试**一律不关** `spring.cloud.nacos.discovery.enabled` ⇒ ⇒ `DiscoveryClient` bean 由 spring-cloud-alibaba 的自动配置提供 ⇒ ⇒ devh 的 `@ConditionalOnBean(DiscoveryClient.class)` 在其处理时**未命中** ⇒ ⇒ provider **不注册** ⇒ ⇒ 无接管
  - 反向验证：M-D（额外加 `spring.cloud.nacos.discovery.enabled=false` 来制造「回落 `SimpleDiscoveryClient`」的环境）⇒ ⇒ **上下文直接起不来**（`Failed to load ApplicationContext`）⇒ ⇒ 该模块**无法**在内部造出接管环境
- **改它的理由（与上面那条限定分开写）**：① **跨域静态守门的判据不区分模块** —— `tools/difftest/check-grpc-channel-target-scheme.py` 扫的是**形态**（代码态 `forAddress(`）⇒ ⇒ 不改就得登记永久豁免（点位级例外集 = 只能变紧的棘轮，目标恒为 0 例外）② **机制面本身是错的**：无 scheme ⇒ 走「默认 scheme」⇒ 依赖进程级全局注册顺序 ⇒ 脆 ③ 一旦同 JVM 内出现 `DiscoveryClient` 上下文（未来任何一个新测试类关掉 Nacos discovery 就会发生）⇒ ⇒ 立刻从「预防性」变成「活雷」
- **Go 侧对应物（表达层，非行为偏离）**：`go-zero@v1.10.2/zrpc/internal/client.go:126` = `grpc.DialContext(timeCtx, server, options...)`；`go-zero@v1.10.2/zrpc/client.go:84` = `target, err := c.BuildTarget()` ⇒ ⇒ Go 侧把 target **直连**给 grpc ⇒ ⇒ 不存在「默认 scheme 被全局 resolver 抢」这一层 ⇒ ⇒ RAG 侧显式 `dns:///` 属**表达层**对齐 ⇒ ⇒ **不构成有意偏离，无需登记签字**
- **验收读数**：user-rpc **184 条**（0F/0E/0S）· 与改动前同数 ⇒ ⇒ `BUILD SUCCESS`

### 补充：devh discovery provider「是否注册」的直接探针 ＋ 装置正确的配对实验（2026-10-31 · G1 lane · 同轮追加）

- **为什么要补这一段**：上一节「这 2 处属预防性修正」原先只有「回落 target 仍绿」这一条**间接**证据 ⇒ ⇒ 绿也可能是「**装置不够**」（只跑了单类，而「同跑红」按定义需要**同一 JVM 里另一个 devh `@SpringBootTest` 先跑**）⇒ ⇒ 这一段补上**直接探针**与**装置正确的配对实验**
- **直接探针（实测，不是推断）**：在测试方法里打印进程级全局 registry 的真实状态
  - `io.grpc.NameResolverRegistry.getDefaultRegistry().getDefaultScheme()` ⇒ **`dns`**
  - `…getProviderForScheme("discovery")` ⇒ **`null`** ⇒ ⇒ **devh 的 discovery provider 根本没注册进这个 JVM**
  - `…getProviderForScheme("dns")` ⇒ `io.grpc.internal.DnsNameResolverProvider@…` ⇒ ⇒ 默认 scheme 仍是 grpc 自带 DNS resolver ⇒ ⇒ 无 scheme 的 target 走 DNS ⇒ ⇒ 不被接管
- **配对实验（装置正确：触发源先跑）**：`mvn … -pl service/users/user/user-rpc test -Dsurefire.runOrder=alphabetical -Dtest='UserRpcLogoutClientVisibleGrpcStatusTest,UserRpcGrpcInterceptorWiringTest,UserRpcContainerWiringTest'`
  - 执行顺序**实测**：`UserRpcGrpcInterceptorWiringTest`（devh 用得最重的一类）**先跑** → `UserRpcLogoutClientVisibleGrpcStatusTest` → `UserRpcContainerWiringTest`
  - 触发源先跑之后，logout 那条的探针**仍然是** `defaultScheme=dns` / `discoveryProvider=null` ⇒ ⇒ **无接管**
  - 同一次运行日志里 resolver 接管痕迹（`Failed to resolve name` / `No servers found` / `Can not get hosts`）计数 = **0**
  - 回落 `.forAddress` 的那一版 ⇒ **仍绿**
  - ⚠️ **首轮配对装置是错的**（用了 `-Dsurefire.runOrder=reversealphabetical` ⇒ 触发源反而排在后面）⇒ ⇒ 那一轮**不能**用来否定「同跑红」⇒ ⇒ 已按正确顺序重跑（上面这条）
- **⇒ 结论（本模块内）**：「同跑红」在 user-rpc 模块内**原理上不可复现** —— 模块内**任何** `@SpringBootTest` 的上下文都是 user-rpc 应用上下文 ⇒ ⇒ `DiscoveryClient` bean 由 spring-cloud-alibaba 的自动配置提供 ⇒ ⇒ devh 的 `@ConditionalOnBean(DiscoveryClient.class)` 在其处理时**未命中** ⇒ ⇒ provider **永不注册** ⇒ ⇒ 无接管
- **⇒ 与上游分类口径的差异（如实记录，不隐藏）**：共享层例外集按「**真发 RPC ⇒ 活雷**」分类 ⇒ ⇒ 但「真发 RPC」是**必要条件、不是充分条件** —— 决定性变量是**该上下文有没有 `DiscoveryClient` bean**（devh `@ConditionalOnBean` 命中与否）⇒ ⇒ 惰性点即便 provider 注册了也不报错；**provider 没注册时、真发 RPC 的点同样不报错** ⇒ ⇒ 本模块那 2 处属预防性；**user-api 侧那 1 处才是实测可复现的活雷**（该测试显式关掉 Nacos ⇒ 回落 `SimpleDiscoveryClient` ⇒ 命中 ⇒ 回落 target 即 `UNAVAILABLE: No servers found for 127.0.0.1:22200`）
- **探针纪律**：临时补丁 + 事后还原（**不用 `git stash`** —— 并发 lane 会互偷工作区状态）⇒ ⇒ 还原后 `grep -c 'forTarget("dns:///127.0.0.1:" + GRPC_PORT)'` = 1 ⇒ ⇒ 无残留；**零行位移**不受影响（探针只在测量期间存在）
