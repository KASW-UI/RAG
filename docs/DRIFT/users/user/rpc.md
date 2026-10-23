# DRIFT 台账 · `service/users/user/user-rpc`

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
  `UserRpcContainerWiringTest`（真 Testcontainers PG，12 条）关闭，详见 `users/_shared.md`。
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
| 编号命名空间 | 本轮新增 **`DRIFT-USERRPC-V-001` 起** / **`UNRESOLVED-USERRPC-001` 起**。users 域已占用的 `DRIFT-001..011/016/018/024`、`DRIFT-NEW-001/005/007/008/011/012/013/014/015/016/018`、`DRIFT-FRAMEWORK-001` **一条都不复用** |
| 本轮自跑证据 | ✅ **真 PG**：`mvn -o -pl service/users/user/user-rpc test`，`Tests run: 91, Failures: 0, Errors: 0`（39s）。其中 `UserRpcContainerWiringTest` **12 条**跑在 Testcontainers `postgres:16-alpine` 上（日志实证 `DockerClientFactory` + `Found Docker environment`），`AvatarTransactionWiringTest` 5 条跑在真实 Spring 容器装配上 |
| ✅ **未**取得 | ❌ **双侧活体对拍（Go ↔ RAG 同时起进程打同一串请求）** —— `tools/difftest/grpc-dual-port-runner.java` 只覆盖 4 档 status code，且 §6.2.6 E-2 的那次结果来自 stub 而非真实现。⇒ **本轮绝大多数结论的证据强度是「静态源码 + RAG 单侧真 PG」，不是「双侧对拍」** |
| 最终判定 | **`SEMANTICALLY NOT EQUIVALENT`** —— 1 项 SEMANTIC MISMATCH + 7 项 OBSERVABLE DIFFERENCE + 1 项死代码/伪出处（§7），7 项 unresolved（§9）。**其中 SEMANTIC MISMATCH 的端到端可达性未证**（§9 UNRESOLVED-USERRPC-001），若用户裁定其为「永不可达」，判定会降为「可达路径全等价 + 7 项可观察差异」 |

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
| SelectAvatar | NotFound 两条分支的 gRPC code | 都是 `NOT_FOUND`（message 不同，见 V-007） | 同 | **EQUIVALENT**（code 层） | `[GO] :41-50` `[RAG] :585-595` |
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
| 全组件 | 指标**可抓取性** | DevServer 仅 dev/test 起，生产无端点 | `management.server.port:-1` + 全端点关 | **EQUIVALENT**（两侧生产都不可抓） | `[GO] user.yaml:23-30` + `ServiceConf.Mode` 默认 `pro`；`[RAG] application.yaml:102-111` |
| 全组件 | 指标注册形态 | `prometheus.Unregister(GoCollector/ProcessCollector)`（`:25-26`） | Micrometer 保留默认 collector | **IMPLEMENTATION DIFFERENCE** | 无行为影响 |
| 全组件 | 死代码 | 无 | 3 个异常类 + 2 个 mapper 方法零调用点，其中 3 个异常类的 javadoc 引用**不存在的 Go 出处** | **OBSERVABLE DIFFERENCE**（无行为影响） | **V-008** |

### 4.3 横切维度（超时 / 建表 / 配置）

| Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|
| 服务端超时时长 | struct tag default 2000ms（`zrpc/config.go:46`） | `observability.rpc-timeout: 2s` | **EQUIVALENT** | 本轮实读 `go-zero@v1.10.2/zrpc/config.go`，**行号指向消费该 tag 的 `conf.MustLoad`** |
| 超时触发机制 | `context.WithTimeout` 真取消（ctx 到 PG） | `call.close(DEADLINE_EXCEEDED)` + `DeadlineState` + `setQueryTimeout` 补 | **IMPLEMENTATION DIFFERENCE**（B-1 已签字：JDBC 不响应 interrupt） | `_shared.md` §6.2.6 E-4 |
| 超时的 gRPC **description** | `"context deadline exceeded"`（`rpc.go:69`） | `"rpc timeout after 2000ms"`（`RpcInterceptor.java:192-194`） | **OBSERVABLE DIFFERENCE** | **V-007**（跨组件 `service/common`） |
| 拦截器是否真挂上 server | `user.go:43` 显式 `AddUnaryInterceptors` | `@Bean GrpcServerConfigurer`（`UserRpcApplication.java:103-107`） | **EQUIVALENT** | `[T]` `GrpcInterceptorWiringTest`（含对照用例证明 starter 自身无此入口） |
| 建表机制 | GORM `AutoMigrate`（无账本） | `SchemaInitializer` 幂等重放 + PG advisory lock | **IMPLEMENTATION DIFFERENCE**（RAG **更强**：见 `_shared.md` §6.2.5 并发实测） | `[PG]` `UserRpcSchemaTextColumnTest` + `schemaInitCreatedJsonbColumns` |
| 建表失败 | `log.Fatalln` → 进程退出 | 抛 `IllegalStateException` → 启动失败 | **EQUIVALENT** | `[GO] db.go:33-36` `[RAG] UserSchemaBootstrap.java:52-56` |
| **建表责任交叉** | `AutoMigrate(&User{}, &UserAvatarHistory{})`，`users` 排第 1 | 4 份 DDL **全部**重放，含 `admin_admin` / `admin_invite` | **UNKNOWN** | `UNRESOLVED-USERRPC-005` |
| Flyway 是否残留 | N/A | `mvn -o -pl … dependency:list \| grep -i flyway` ⇒ **零命中** | **EQUIVALENT**（已按 §6.3 问题 1 移除干净） | 本轮实测；`pom.xml:136-154` 的注释完整记录了「只删 flyway-core 不够」 |
| DB 连接串时区 | `db.go:21` 钉 `TimeZone=Asia/Shanghai` | `application.yaml:13` **未指定** | **OBSERVABLE DIFFERENCE** | **V-004** |
| Snowflake workerId 解析 | `env → hash`（`snowflake.go:52-85`） | `env → properties → hash` | **OBSERVABLE DIFFERENCE**（配置超集） | DRIFT-005 已留档，本轮只确认 Go 侧优先级 |
| Snowflake 启动期 fail-fast | **无**（懒初始化，首个 `GetID()` 才 init） | `ApplicationReadyEvent` 校验 workerId 越界则启动失败 | **OBSERVABLE DIFFERENCE** | `[GO] snowflake.go:41-50` `[RAG] SnowflakeBootstrap.java:37-47` |
| 服务发现 | etcd 静态 `Key: user.rpc` | Nacos `discovery://user-rpc` | **IMPLEMENTATION DIFFERENCE**（AGENTS §3.4 已签字：部署面偏离） | — |

### 4.4 结果统计（**只数本轮新发现，不含已签字偏离**）

| 类别 | 数量 | 编号 |
|---|---|---|
| **SEMANTIC MISMATCH** | **1** | V-001 |
| **OBSERVABLE DIFFERENCE** | **7** | V-002 ~ V-008 |
| IMPLEMENTATION DIFFERENCE | 4 | 表名 / 指标注册 / 建表机制 / 服务发现 |
| 已签字偏离（引用不重开） | 2 | N-1、N-2（见 `./api.md`） |
| **UNKNOWN / unresolved** | **6** | §9（**U-007 已于 2026-10-24 闭环** ⇒ §9.1） |

### 4.5 复核历史导航页 L34–41 的三条「缺口」

| 原缺口陈述 | 本轮结论 |
|---|---|
| 「proto 逐字段对照无独立留档」 | **已闭合（本轮补齐）**：两侧 `.proto` 剥离 `option`/注释/空行后 `diff` **零差异**；另有常驻守门脚本 `tools/difftest/check-proto-static.sh`（`user`/`admin` 两个 proto 有网，见 AGENTS.md §3.4） |
| 「`AvatarTransactionWiringTest` 无真实 PG，不证明 jsonb CAST」 | **已闭合且本轮升级复跑**：`UserRpcContainerWiringTest` 12 条跑在真 `postgres:16-alpine` 上，本轮 12/12 绿，覆盖 jsonb `Types.OTHER` 写入、`@Results` 读回 Map、`CAST(... AS jsonb)`、4/5 步**真实行回滚**、UNIQUE 约束 |
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
| 超时 | 2000ms | interceptor select | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | `call.close` | `DEADLINE_EXCEEDED` + `"rpc timeout after 2000ms"` | **code YES / description NO**（V-007） |
| SQL 被取消 | 57014 | ctx cancel ⇒ GORM 返 ctx err ⇒ **落到 `INTERNAL`** | `INTERNAL "DB查询失败"` | `SqlTimeoutStatusTranslator` 翻成 `DEADLINE_EXCEEDED` | `DEADLINE_EXCEEDED` | **NO** —— 但两侧都只在 2000ms 之后才发生，**外层 interceptor 已先返回**，内层结果被丢弃 ⇒ **客户端可见行为仍一致**。已由 `_shared.md` §6.2.6 E-4 实测闭环（Go 182ms / RAG 176ms 查询滞留） |
| 未映射异常 | 任何 `Throwable` | Go 不会走到（每条都显式 `status.Error`） | — | `UserServiceGrpcImpl` 兜底 | `INTERNAL` + `"类名: 消息"` | **IMPLEMENTATION DIFFERENCE**（RAG 多一层兜底，Go 侧对应情形是 panic ⇒ gRPC `UNKNOWN`） |

> ⚠️ **最后一行不要当成「RAG 更好」**：`UserServiceGrpcImpl` 的 `catch (Throwable)` 兜底是 **DRIFT-02** 的修复，
> 目的是把 grpc-java 默认的 `UNKNOWN` 收敛成 `INTERNAL`。Go 侧真出现 panic 时 grpc-go 会返回 `UNKNOWN`
> （`status.FromError` 拿不到 code 时 `markRPCResult` 走 `codes.Unknown`，`rpc.go:80-83`）。
> ⇒ **真触发 panic 时两侧 code 会不同**。本轮**未实测**（无 panic 注入路径），记为 UNRESOLVED-USERRPC-007 的一部分。

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
- **来源**：Go 藏在 struct tag（`zrpc/config.go:46`，`user.yaml` 未配），RAG 显式 `observability.rpc-timeout: 2s`。
  ⚠️ 这正是 §3.3.1 的典型：**只看 yaml 会以为 Go 没有超时**；读到消费 tag 的那段控制流才拿到 2000ms。
- **时长**：2000 = 2000 ⇒ **EQUIVALENT**。
- **scope**：Go 是 `context.WithTimeout` 包住整个 handler；RAG 是「调度线程到点 `call.close` + 业务侧 `checkDeadline()` 主动抛」。
  **两者对客户端的可见 scope 等价**（都在 2000ms 让调用方拿到 `DEADLINE_EXCEEDED`），
  但对**在途 IO 的影响不同** —— B-1 已签字（Java interrupt 到不了 JDBC），
  `DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator` 是补丁，实测查询滞留已从 4657ms 降到 176ms。
- **description**：**不等价** ⇒ V-007。

### 6.7 Retry Semantic Equivalence
**两侧都无 Retry**（N/A）。⚠️ 明确记录：Go 的 `mustNewRedis` 在启动期失败会 `log.Fatalln`，
RAG 的 Redis 是懒连接 —— **首次 `logout` 才会暴露连接问题**。这是**启动期 fail-fast 语义的差异**，
Go 侧 `svc.NewServiceContext`（`servicecontext.go:29` `redis.MustNewRedis`）在 `zrpc.MustNewServer` **之前**执行，
RAG 侧无对应物。记为 V-008 的姊妹项，见 §9 UNRESOLVED-USERRPC-004。

### 6.8 Serialization Semantic Equivalence
| 维度 | Go | Spring Boot | 结果 |
|---|---|---|---|
| wire 契约 | proto3 | 同（逐字节核对） | **EQUIVALENT** |
| extra_info 参数绑定 | 驱动按 `text` 列写 | `ps.setString`（原 `ps.setObject(..., Types.OTHER)`） | ✅ **列类型已一致（V-003 已修，`ec33159`）**，两侧都按 text 写 |
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
- **Classification**: **SEMANTIC MISMATCH**（可达性未证）
- **Risk**: HIGH（若可达）
- **Recommended Verification**：在 RAG 库里 `ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL`，
  插一条 `status IS NULL` 的行，两侧各打一次 `Login`，比对 `Status.getCode()`。
  在改 DDL 前，**不要**把这条按「不可达」销掉。

### DRIFT-USERRPC-V-002 DDL：`users_user.status` 的 **NOT NULL** 两侧不同 —— 🔴 **仍成立，但被卡**（改动必须与 admin 域同改）

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

### DRIFT-USERRPC-V-003 DDL：`extra_info` 列类型 **text ↔ jsonb** —— ✅ **已修（2026-10-23 23:4x，commit `ec33159`）**

- **Category**: Schema / Serialization
- **Go 侧成因（本轮独立复核，不是猜的）**：
  `data_model.go:13` `ExtraInfo map[string]string gorm:"column:extra_info;serializer:json"`
  → `gorm@v1.31.2/schema/field.go:195-199`：`serializer` 命中时 **强制 `field.DataType = String`**
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

#### FIX（2026-10-23，部分）——`users_user` 半边已修，**`users_user_avatar_history` 半边仍 OPEN**

| 半边 | 状态 | 说明 |
|---|---|---|
| `users_user.create_time/update_time`（`01_create_user.sql:42-43`） | ✅ **已修** | 列改 `TIMESTAMPTZ`；两个实体 4 字段改 `OffsetDateTime`。守门 `UserRpcUsersUserTimestamptzParityTest`（6 条，真 PG），变异测试已证伪。admin-rpc **84/0**、user-rpc **102/0** |
| `users_user_avatar_history.create_time/update_time`（`02_create_user_avatar_history.sql:30-31`） | ✅ **已修**（第六轮复核更正） | 这才是本条 **OBSERVABLE** 的那一半（`CASE-27`）。DDL 现为 `TIMESTAMPTZ`（`02_...:30-31` + 幂等 ALTER），守门 `UserRpcAvatarHistoryTimestamptzParityTest`（**9 条**，真 PG）。⚠️ 本行此前写「未修」，是**过期状态**（第六轮复核时才发现 DDL 与守门早已到位）。**残留**：DEFAULT 差异（`CURRENT_TIMESTAMP` vs Go 无）仍属 V-011，未修 |`UserAvatarHistoryEntity:47,50` 仍 `LocalDateTime`，`AvatarDomainService:74` 仍 `atOffset(UTC)` ⇒ wire 恒 `Z`；叠加 JDBC URL 未钉 `TimeZone`（Go `db.go:21` 钉 `Asia/Shanghai`） |

⚠️ **本条原标题「4 个时间列」口径已更正**：那 4 处是 **两份 Go model 各 2 个字段**
（`user/rpc/.../data_model.go:14-15` 与 `admin/rpc/.../data_model.go:12-13`），
**指向同一张表 `users_user`**，不是 4 张表 4 列。

⚠️ **原台账「改动三部分」的第 3 部分（3 处格式化）经复核归因错误**，详见
[`_shared.md` 的同名小节](../_shared.md)：`AvatarDomainService` 格式化的是**头像历史表**的实体，
`UserApiSupport:69` 只是 proto 字符串透传、根本不是格式化点。
⇒ **`users_user` 的时间值不进任何 wire 字段**，本轮改动**无可观察差异**，
是纯 schema / 类型层对齐。**别把「已修」读成「wire 已对齐」。**

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
- **Risk**: LOW

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
- **⚠️ 未解**：`users_user_avatar_history` 走手写 `@Insert` + `@Options(useGeneratedKeys=true)`
  （`UserAvatarHistoryMapper.java:88-93`）时，`IdType.ASSIGN_ID` 与 `Jdbc3KeyGenerator` **谁最终胜出**
  本轮未验证 ⇒ `UNRESOLVED-USERRPC-004`。

### DRIFT-USERRPC-V-007（跨组件 `service/common`）超时路径的 gRPC **status description** 不同 —— 🔴 **仍成立，但不在本组件判定权内**

- **Category**: Error Identity
- **Go**：`service/common/observability/rpc.go:69`
  `status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = **`context deadline exceeded`**。
- **RAG**：`service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:192-194`
  ⇒ description = **`rpc timeout after 2000ms`**。
- **Difference**：`code` 相同（`DEADLINE_EXCEEDED`），**description 逐字不同**。
- **Observable Impact**：`grpc-status-details-bin` / `grpc-message` trailer 不同。
  user-api 侧**只按 code 分支**，故不进 HTTP body（与 N-2 同款影响面）。
- **Classification**: **OBSERVABLE DIFFERENCE**
- **Evidence**: `[GO]` `rpc.go:69`；`[RAG]` `RpcInterceptor.java:192-194`。证据强度：**静态源码**。
- **归属**：`service/common` 组件，不属 user-rpc 自身。**判定权在 common 组件的台账**，
  此处只登记 user-rpc 作为消费方的事实，避免同一问题被两个组件各判一次。

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
| CASE-07 | **`Login` `status IS NULL` 的用户** | `INTERNAL "DB查询失败"` | **返回 `status=0` 登录成功** | **应当等价** | **NO** | **V-001**，`UNRESOLVED-USERRPC-001` |
| CASE-08 | `Login` 成本越界的 bcrypt hash（`$2a$99$…`） | `false` ⇒ 密码错 | `MALFORMED_HASH` ⇒ 仍密码错 | 对外同为 `status=1` | YES | `[T]` 4 条 DRIFT-004 用例 |
| CASE-09 | `GetUser` 不存在的 uid | `NOT_FOUND`（响应被丢弃） | `NOT_FOUND` | 同 code | YES | `[T]` |
| CASE-10 | `GetUser` 存在的 uid | `Found=true` + `UserInfo{uid,username,email,extra_info,status}`，**`score=0`** | 同 | `score` 两侧都是 0 | YES | `[T]` `getUser_success_returnsUserInfo` |
| CASE-11 | `UpdateUser` 只传 password | 只改 password + `update_time` | 同 | email / extra_info 不变 | YES | `[PG]`（`updateUserById` 层）+ `[T]` |
| CASE-12 | `UpdateUser` 传空 email | **email 列不变** | 同 | 邮箱不丢 | YES | `[T]` `updateUser_partialUpdate_onlyWritesNonEmptyFields` |
| CASE-13 | `UpdateUser` 把 username 改成自己的现值 | `AlreadyExists`（不排除自己，`updateuserlogic.go:36-39`） | 同 | 同 | YES | `[T]` `updateUser_selfUsernameChange_throwsAlreadyExists` |
| CASE-14 | `UpdateUser` 目标 uid 不存在 | update 影响 0 行 → 重读 `NOT_FOUND` | 同 | 同 code | YES | `[T]` `updateUser_userDeletedConcurrently_throwsNotFound` |
| CASE-15 | `UpdateUser` 全字段为空 | 不发 UPDATE，`update_time` 不动 | 同（`any==false→return 0`） | 同 | YES | `[PG]` `zeroValuePatchDoesNotUpdate` |
| CASE-16 | **`UpdateUser` 显式空 map + 其余空** | 发 UPDATE（写 `{}` + 推进 `update_time`） | **不发任何 UPDATE** | **应当等价** | **NO** | **V-005**，`UNRESOLVED-USERRPC-003` |
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
| CASE-27 | **`GetAvatarHistory` 的 `create_time` 渲染** | 带时区偏移（如 `+08:00`） | 恒 `Z` | **应当等价** | **NO** | **V-004**，`UNRESOLVED-USERRPC-002` |
| CASE-28 | `Logout` 空 token | `INVALID_ARGUMENT "Token不能为空"` | 同 | 同 | YES | `[T]` |
| CASE-29 | `Logout` 正常 token | `SETEX user:jwt_blacklist:<tok> 2592000 "banned"`；**`success=false`** | 同 | **两侧 success 都是 false** | YES | `[T]` `logout_writesRedisWithCorrectKeyAndTtl` |
| CASE-30 | `Logout` token 含前后空格 | **不 trim**，整串作 key | 不 trim，整串作 key | 同 | YES | `[GO][RAG]` 静态 |
| CASE-31 | 任一 RPC 执行超过 2000ms | `DEADLINE_EXCEEDED` + `"context deadline exceeded"` | `DEADLINE_EXCEEDED` + `"rpc timeout after 2000ms"` | code 等价 / description 不等价 | **PARTIAL** | **V-007** |
| CASE-32 | PG 被 ACCESS EXCLUSIVE 锁住 12s | 客户端 2000ms 返回；PG 查询 203ms 后消失 | 客户端 2000ms 返回；PG 查询 176ms 后消失 | 同量级 | YES | `_shared.md` §6.2.6 E-4 **实测**（2026-10-21，本轮未重跑） |
| CASE-33 | 同一 uid 并发 `UploadAvatar` ×2 | 可能两条 `is_current=true` | 同 | **共有缺陷，非漂移** | YES（缺陷等价） | `[GO][RAG]` 静态：两侧都无 `FOR UPDATE` |
| CASE-34 | 同一 username 并发 `Register` ×2 | 恰好 1 成功 + 1 `INTERNAL`（撞 UNIQUE） | 同 | 同 | YES | `[PG]` `uniqueConstraintsAreEnforcedOnRealPg`（约束层）+ 静态 |
| CASE-35 | 进程启动时 Redis 不可用 | **启动失败**（`MustNewRedis`） | 启动成功，首个 `logout` 才失败 | **应当等价** | **NO** | **V-008 姊妹项 / `UNRESOLVED-USERRPC-004`** |
| CASE-36 | 进程启动时 `status` DDL 已存在但列宽是 varchar(255) | — | 幂等 `ALTER ... TYPE text` 自愈 | RAG 单侧特性 | N/A | `[PG]` `UserRpcSchemaTextColumnTest#alterSelfHealsAnExistingVarcharColumn` |

---

## 9. Unresolved（spec §37，**此节不得为空**）

| # | Question | Go Evidence | Java Evidence | Why Unknown | Impact | Required Confirmation |
|---|---|---|---|---|---|---|
| **UNRESOLVED-USERRPC-001** | **V-001（`Login` 的 status==NULL fail-open）端到端是否真的可达** | `data_model.go:11`（无 NOT NULL）⇒ 列可空 ⇒ 机制上可达 | `01_create_user.sql:39` `NOT NULL` ⇒ RAG 自建库不可达 | 两条静态证据**互相矛盾**：Go 机制可达、RAG 机制不可达。需真实数据才能判 | HIGH（安全方向） | 在 RAG 库 `DROP NOT NULL` + 插 NULL 行，两侧各打一次 `Login` 比对 `Status.getCode()`。**在跑通前不得把 V-001 按「不可达」销掉** |
| **UNRESOLVED-USERRPC-002** | **V-004 的时区偏移量到底是多少** | DSN 钉 `TimeZone=Asia/Shanghai`（`db.go:21`）；列类型 `timestamptz` | JDBC URL 无 `TimeZone`（`application.yaml:13`）；列类型 `timestamp`；渲染恒 `Z`（`AvatarDomainService.java:74`） | JVM 默认时区、PG 会话 `TimeZone`、容器 TZ 三者都未实测 | MEDIUM（客户端可见字符串） | 查部署环境 `TZ` / `-Duser.timezone` / `SHOW TimeZone`；或两端各发一次 `UploadAvatar` + `GetAvatarHistory` 直接比 `create_time`。**同构先例 `DRIFT-MSG-010` / `UNRESOLVED-MSG-001`** |
| **UNRESOLVED-USERRPC-003** | **V-005 的 wire 可达性**：protobuf 编码器会为「显式空 map」产出字段 tag 吗 | protobuf-go 对非 nil map 视为「已设置」⇒ 会编码 ⇒ 服务端解出非 nil 空 map | protobuf-java 的 `MapField` 写出条件本轮**未读源码** | 只做了分支层静态对照，未构造 wire 输入 | LOW | 构造 `extra_info: {}` 的请求（或用 grpcurl 手写 wire）打两侧，比对 `update_time` 是否推进 |
| **UNRESOLVED-USERRPC-004** | **① `IdType.ASSIGN_ID` 与 `useGeneratedKeys=true` 谁决定 `users_user_avatar_history.id`（V-006）；② 启动期 Redis 不可用时两侧的失败时机（CASE-35）** | `servicecontext.go:29` `redis.MustNewRedis` 在 `MustNewServer` **之前** ⇒ 启动即失败 | Redis 为懒连接；`insertHistory` 的两种 id 来源并存 | 两条都需要真容器 / 真 jar 才能判 | LOW | ① 真 PG 下插入一条后 `SELECT id FROM users_user_avatar_history` 看量级；② 停掉 Redis 起进程看是否 fail-fast |
| **UNRESOLVED-USERRPC-005** | **建表责任交叉**：`users_user` 由 **user-rpc 与 admin-rpc 都建**（幂等重放 + 同一个 advisory lock 键 `8_675_309`） | Go 两个服务**各有各的表名**（`users` vs `users_user` 不冲突；admin-rpc 也建 `users`）⇒ 靠 `HasTable` 的**时序侥幸**（`_shared.md` §6.2.5 第 ②③ 段已实测 5 轮） | 两侧**表名相同** ⇒ 真正共享同一张表；靠 `SchemaInitializer` 的 advisory lock 串行化 | **本组件单侧验不完**：必须与 `admin-rpc` 的结论合并判读。RAG 的锁比 Go 的「时序侥幸」强，但那是**机制更强**，不是「与 Go 等价」 | HIGH（跨组件不变量） | 与 admin-rpc 组件的台账合并判读；跑 `tools/difftest` 双栈并发启动（`_shared.md` 已有实测记录，但那是 2026-10-21 的，需在当前代码上复跑） |
| **UNRESOLVED-USERRPC-006** | **索引 / 唯一约束 / NOT NULL / DEFAULT 四项仍未逐列核对** | 需查 `information_schema` 的 `indexes` / `constraints` | 同 | `tools/difftest/check-ddl-static.py` 的文件头**自陈只查 `data_type` 与 `character_maximum_length`**，其余标 `NOT CHECKED` | MEDIUM | 起两侧容器后跑一条 psql 查询逐列比对。⚠️ **不要把该脚本「全绿」读成「DDL 已一致」**（该脚本当前在有差异时是 EXIT=1） |
| ~~**UNRESOLVED-USERRPC-007**~~ | ✅ **已闭环（2026-10-24）** | — | — | 双侧活体对拍已跑（真进程 + 真 PG + 真 Redis，**自带容器 / 自带库 / 自带端口**）：**43 场景 / 39 等价 / 4 处差异**，4 处**全部**是 N-2 已签字的 Go 源码乱码文案 ⇒ **无新漂移** | ~~HIGH（本轮最大的证据缺口）~~ → 已闭环 | ⇒ **§9.1**（含首轮 2 处**假漂移**的辨伪记录 + 守网清单） |

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

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ⚠️ **不完全一致** | 9 个 RPC 的对外形状一致；3 处 DB 层 + 1 处分支层不一致（V-001~V-006） |
| 2 | 正常路径是否一致？ | ✅ **一致** | 9 个 OP 的 happy path 逐条对照一致（§4.2） |
| 3 | 边界输入是否一致？ | ⚠️ **不完全一致** | trim / clamp / 空串全部一致；**显式空 map 分支不一致**（V-005） |
| 4 | NotFound / Empty / Null 是否一致？ | ⚠️ **不完全一致** | NotFound 一致（响应被丢弃）；Empty 一致；**NULL status 不一致**（V-001）；NULL extra_info 一致 |
| 5 | Error meaning 是否一致？ | ⚠️ **code 一致 / message 部分不一致** | 全部 gRPC code 逐条一致；**4 处** message 因 Go 源码乱码不同（**N-2 已签字**，2026-10-24 双侧活体对拍实测）+ 1 处超时 description（V-007）。⚠️ 那 4 处的**状态码两侧完全相同**，差异只在 `grpc-message` 头的字节 |
| 6 | Error propagation 是否一致？ | ✅ **一致** | Go 的「resp+err 双返」在 gRPC 层被丢弃 ⇒ 两侧客户端只见 status code |
| 7 | External dependency failure 是否一致？ | ✅ **一致**（超时路径除外） | PG/Redis/bcrypt/snowflake 四类失败两侧同 code 同 message；57014 取消的内层差异被外层 interceptor 掩盖（E-4 已实测闭环） |
| 8 | Cache hit / miss 是否一致？ | **N/A** | 本组件无缓存 |
| 9 | Transaction boundary 是否一致？ | ✅ **一致** | 4/5 步原子性两侧相同；`[PG]` 真回滚实证；**无 self-invocation** |
| 10 | State transition 是否一致？ | ⚠️ **基本一致，一处相反** | 头像 current 状态机一致、登录三态一致；**`status==NULL` 的 login 分支相反**（V-001） |
| 11 | Side effects 是否一致？ | ⚠️ **不一致（DB 层）** | extra_info 双写 / Redis SETEX 一致；`users_user.id` 取值来源不同（V-006）；孤儿 history 两侧一致 |
| 12 | Ordering 是否一致？ | ✅ **一致** | `create_time DESC` 一致；头像 current 清理顺序一致 |
| 13 | Retry 是否一致？ | **N/A**（两侧都无重试） | — |
| 14 | Timeout scope 是否一致？ | ⚠️ **时长一致 / scope 机制不同 / description 不同** | 2000ms=2000ms（§6.6）；B-1 已签字；V-007 |
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
本轮 ✅ 真 PG（12 条容器测试，2026-10-23 02:08 实跑）、✅ 静态源码（含 gorm / gorm-postgres 的**消费点**行号）、
❌ **双侧活体对拍 = 缺**。⇒ 本文件里任何 `EQUIVALENT` 结论的强度都是
「**逐行读 Go 消费点得出**，RAG 单侧真 PG 测试只作旁证」，
**不构成 spec §1 意义上的「双侧同时跑的探针」**。这一点不得在下游被读成「已闭环」。

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
- 消费点：`gorm@v1.31.2/scan.go:153` `db.AddError(rows.Scan(values...))`
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

`service/users/admin/admin-rpc/.../domain/UserEntity.java:60` 同样是 `private Long status = 0L;`。
属**另一个组件**，本轮未动；admin 侧不跑 `Login`，暴露面不同（`GetUser` 响应会带 `status=0`）。
⇒ 登记为跨组件待办，**不要**当成「已随 user-rpc 修好」。

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
  + `UserRpcJsonbAndAvatarProjectionTest#updateUserById_noBusinessFields_stillAdvancesUpdateTime`。

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
| **DRIFT-USERRPC-V-002** | `users_user.status` **NOT NULL**（Go 可空） | 🔴 **真库实证确认**（§4） | ⚠️ **动它会立刻激活 admin-rpc 的同款缺陷**：admin-rpc 的 `UserEntity.status` 是 `private Long status = 0L;`（MyBatis `callSettersOnNulls=false` ⇒ NULL 读成 0）⇒ 放开 NOT NULL 会让 admin 侧把 NULL 当成「活跃」。⇒ **必须与 admin 侧一起改**（见 `_shared.md` 的 admin 待裁决节） |
| **DRIFT-USERRPC-V-003** | `extra_info` `text` ↔ `jsonb` | ✅ **已修**（`ec33159`） | 你随后拍板「一起改成 text」。连带改 `JsonTextTypeHandler` + 去掉 2 处 CAST + 3 条守门（详见 §502）。跨域不对称随之消除 |
| **DRIFT-USERRPC-V-011（新）** | 时间列 **DEFAULT `CURRENT_TIMESTAMP`**（Go 无 DEFAULT） | 🔴 新发现 | 两侧写入路径都**显式**写这两个列（Go 靠 GORM autoCreateTime；RAG 本轮起靠应用侧时钟）⇒ DEFAULT 只在「有人不写该列」时生效 ⇒ **当前不可观察**。⚠️ 改它要先确认 admin 域与 `users_user` 的写入路径 |
| **DRIFT-USERRPC-V-006** | `id` 的取值来源（Go `bigserial` 序列 vs RAG `IdType.ASSIGN_ID` 雪花） | ✅ **已关闭**（第四轮，真 PG 实证：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出；**永不进 wire**） | ⚠️ 本行此前写「仍成立」，是**过期状态**（第四轮已关，第六轮复核才发现汇总表没同步） |
| **DRIFT-USERRPC-V-007** | 超时的 gRPC `description` 不同（`service/common` 组件） | 仍成立 | 归属 `service/common`，不在本组件判定权内 |
| **DRIFT-USERRPC-V-008** | 死代码 + **伪出处**（3 个异常类 + 2 个 mapper 方法） | ✅ **已修**（`fa2de52`） | 第四轮 §2 已删；且**伪出处是两个，不是一个**（`PasswordMismatchException` 声称的 `ALREADY_EXISTS` 在 Go 恰好相反）。⚠️ 本行此前仍写「仍成立」，属台账脱节，本轮复核更正 |
| **UNRESOLVED-USERRPC-004 ②** | 启动期 Redis 不可用：Go `MustNewRedis` fail-fast vs RAG 懒连接 | 仍 unresolved | 本轮未做「停掉 Redis 再起进程」的注入实验 |
| **UNRESOLVED-USERRPC-005** | `users_user` 建表责任交叉（user-rpc ↔ admin-rpc） | 仍 unresolved | 跨组件不变量；本轮的隔离 harness 是**单 rpc** 的，**证明不了**两 rpc 同库共存。⇒ 属 admin 会话范围 |

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
| `UserRpcJsonbAndAvatarProjectionTest`（**反转 1 条**） | 4 | V-010 | ❌ mock 数据层（**但**断言的是 SQL 片段形态） |
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

### 复核结果：**确实仍 open 的 3 条**

| # | 为什么仍 open |
|---|---|
| `UNRESOLVED-USERRPC-004` ② | 启动期 Redis 不可用时两侧的失败时机（Go `MustNewRedis` fail-fast vs RAG 懒连接）。<br>**①（id 谁胜出）已由本轮 V-006 关闭**（真 PG：两条 bigserial 序列 `last_value` 仍是 1 ⇒ 雪花胜出）。② 需「停掉 Redis 再起进程」的注入实验，未做 |
| `UNRESOLVED-USERRPC-005` | `users_user` 建表责任交叉（user-rpc ↔ admin-rpc）。**跨组件**：必须两个 rpc **同库并发启动**才能验，单侧 harness 证明不了 |
| `UNRESOLVED-USERRPC-007` | 9 个 RPC 的 **gRPC 层错误 message 逐字对拍**。第三轮 §6.1 明写「`grpc-message-dual-port-runner.java` 本轮**未**跑」；已覆盖的只是 HTTP wire 层 |

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
