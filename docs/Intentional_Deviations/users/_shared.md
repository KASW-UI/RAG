<!-- @new 2026-10-25 · 由 `align/global-signoff` 工作树产出（第二十六轮 · L5 lane）· 2026-10-25 整合者从 `global/users-domain.md` fold 至此 -->
<!-- 核对日期：2026-10-25 -->

# `users` 域签字档（`_shared`）

> **归属**：`users` 域（原本由 `global` 收口域**代收**，2026-10-25 第二十六轮由整合者 fold 至此）。
> ⚠️ **为什么在这儿**：`global` 只是**收口域**，不是微服务域 ⇒ **跨域 + `service/common` 影响的搁置漂移**归它，
> 但**域内**的签字候选仍属**该域自己的档**（§5.3b 硬边界 2：一条问题**只在一个档落笔**）。
> ⇒ 本档两条（CAND-16 / CAND-17）最初由 `align/global-signoff` 的 L5 lane 产出于
> `docs/Intentional_Deviations/global/users-domain.md`（**临时落点**）⇒ 已于整合时迁回本文件并**删除临时档**。

> **本档只登记「明知与 Go 不同、经签字批准」的偏离**（判据：**要不要「修回去」**？要 ⇒ `DRIFT`；不要 ⇒ 本档）。
> ⚠️ **读法**：对拍看到这里登记的条目变红，**先确认是不是这一条**，不要当新回归去「修」。

---

## SD-2026-10-25-CAND-16 · 🏷 **待签字候选** —— users 域 **8 处**时间列 `DEFAULT CURRENT_TIMESTAMP`（Go 侧**无**默认）

| 项 | 值 |
|---|---|
| 状态 | 🏷 **候选，建议签** —— **未签字**（登记完就躺着，**不升级为拍板**） |
| 性质分类 | ✅ **刻意保留**（RAG 侧多一个默认值，**两侧写路径都显式绑定该列** ⇒ 当前**不可观察**） |
| 组件 | `service/users/users-common/src/main/resources/db/`（**4 张表 · 共 8 列**）⇒ `users` 域全部组件（`user-rpc` / `admin-rpc` / `user-api` / `admin-api`）共用这同一份 DDL |
| 对应的 DRIFT 条目 | **V-011**（[`docs/DRIFT/users/_shared.md`](../../DRIFT/users/_shared.md) · [`docs/DRIFT/users/user/rpc.md`](../../DRIFT/users/user/rpc.md)） |
| 行为层 | **数据库 schema 的 DEFAULT 表达式**（DBA 可见 / 漏传列时的写入行为） |
| 证据强度 | ✅ **真 PG 活体对照**（两侧各建一个真库后逐列 dump `information_schema.columns`）⇒ **DEFAULT 恰好 8 处差异** |

### 差异的实际面（**8 处，逐处列出**）

| # | RAG 侧 DDL（**行号**） | 列 | Go 侧对应列 |
|---|---|---|---|
| 1 | `01_create_user.sql:42` | `users_user.create_time` | `users.create_time` |
| 2 | `01_create_user.sql:43` | `users_user.update_time` | `users.update_time` |
| 3 | `02_create_user_avatar_history.sql:30` | `users_user_avatar_history.create_time` | `user_avatar_history.create_time` |
| 4 | `02_create_user_avatar_history.sql:31` | `users_user_avatar_history.update_time` | `user_avatar_history.update_time` |
| 5 | `03_create_admin.sql:59` | `admin_admin.create_time` | `admins.create_time` |
| 6 | `03_create_admin.sql:60` | `admin_admin.update_time` | `admins.update_time` |
| 7 | `04_create_admin_invite.sql:44` | `admin_invite.create_time` | `admin_invites.create_time` |
| 8 | `04_create_admin_invite.sql:45` | `admin_invite.update_time` | `admin_invites.update_time` |

⇒ **两侧的列类型 / 长度 / 可空性已全部一致**（`TIMESTAMPTZ`）⇒ **唯一残留差异就是这 8 个 DEFAULT**。

### Go 出处（**tag 是消费点**，§3.3.1）

Go 的 gorm tag **没有 `default`** ⇒ GORM `AutoMigrate` **不给**时间列 DEFAULT：

| Go 文件:行号 | tag 原文 |
|---|---|
| `Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:14` | `CreateTime time.Time \`gorm:"column:create_time;autoCreateTime"\`` |
| `Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:15` | `UpdateTime time.Time \`gorm:"column:update_time;autoUpdateTime"\`` |
| `Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:30` | `CreateTime  time.Time \`gorm:"column:create_time;autoCreateTime"\``（`UserAvatarHistory`） |
| `Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:31` | `UpdateTime  time.Time \`gorm:"column:update_time;autoUpdateTime"\``（`UserAvatarHistory`） |
| `Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:12` | `CreateTime time.Time \`gorm:"column:create_time;autoCreateTime"\``（`Admin`） |
| `Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:13` | `UpdateTime time.Time \`gorm:"column:update_time;autoUpdateTime"\``（`Admin`） |
| `Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:27` | `CreateTime  time.Time \`gorm:"column:create_time;autoCreateTime"\``（`AdminInvite`） |
| `Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:28` | `UpdateTime  time.Time \`gorm:"column:update_time;autoUpdateTime"\``（`AdminInvite`） |

⇒ ⚠️ **对照 `default` 是怎么写的**：同一批模型里**有** `default` 的时候是明写出来的
—— `data_model.go:11`（`Status int64 \`gorm:"column:status;default:0"\``）·
`data_model.go:29`（`IsCurrent bool \`gorm:"column:is_current;index;not null;default:false"\``）
⇒ ⇒ **「时间列没有 DEFAULT」不是漏写 tag，而是 tag 里确实没有这一项**。

### RAG 出处

`service/users/users-common/src/main/resources/db/` 下 4 个建表脚本（**行号见上表**）⇒ 8 列写的是
`TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP` ⇒ 漏传该列时 PG 用**事务开始时刻**补值。

### 为什么**刻意保留**（签字理由）

1. **当前不可观察**：两侧的写入路径**都显式绑定**这两个列
   —— Go 靠 GORM `autoCreateTime` / `autoUpdateTime`（tag 见上表）· RAG 靠应用侧时钟
   （`DRIFT-USERRPC-V-009` 已把 `insertHistory` 从 DB 的 `CURRENT_TIMESTAMP` 改绑应用侧时钟）
   ⇒ DEFAULT 只在「**有人不写该列**」时生效 ⇒ 现有写路径里**没有这种情况**；
2. **删掉它反而更差**：去掉 DEFAULT、保留 `NOT NULL` ⇒ 漏传时直接**插入失败**
   （比 Go 的落 NULL 更早暴露，比现在的「静默补值」更安全）；
3. **时区无风险**：`DEFAULT CURRENT_TIMESTAMP` 在 PG 中是**事务开始时刻**、以 `timestamptz` 存储
   ⇒ **不引入时区偏移** ⇒ 与 `application.yml` 的 `TimeZone=Asia/Shanghai` 无关；
4. ⚠️ **边界**：`04_create_admin_invite.sql` 的 `expires_at` / `used_at` 是**比较型**字段，
   与本条的 8 个时间列**不是同一件事** ⇒ 本条**不覆盖**它们。

### 触发面与残留风险

- 触发条件 = 「**某条 INSERT 漏传该列**」⇒ 目前**没有**这样的写路径 ⇒ 不可观察；
- ⚠️ **残留风险**：若将来新增**只写部分列**的 INSERT（本域已有 `users_user` **建表责任交叉**的问题 ——
  `user-rpc` 与 `admin-rpc` 各跑一份幂等重放）⇒ 差异会显现 ⇒ 那时**重新评估本条**（`ADJ-04` 已记此隐患）。

### 守门

⚠️ **本条没有专门守门** —— 现有守门只钉**列类型 / 可空性**（`UserRpcAvatarHistoryTimestamptzParityTest` 等），
**未钉 DEFAULT 表达式** ⇒ ⚠️ 若要「把 DEFAULT 删掉」改回 Go，**当前没有守门会变红**
⇒ 改这条之前**必须先补守门**（真 PG 逐列 dump `information_schema.columns.default`）。

### 留痕位置

- [`docs/DRIFT/users/_shared.md`](../../DRIFT/users/_shared.md) §2（真库对照表 · **V-011**）；
- [`docs/DRIFT/users/user/rpc.md`](../../DRIFT/users/user/rpc.md) §2026-10-24 真库对照（**V-011**）；
- [`docs/DRIFT/users/_shared.md`](../../DRIFT/users/_shared.md) §5·ADJ-04（DEFAULT 对 admin 写入路径的影响**未评估**）。

---

## SD-2026-10-25-CAND-17 · 🏷 **待签字候选** —— `users_user.status` 的**可空性**：Go **可空** / RAG `NOT NULL`

| 项 | 值 |
|---|---|
| 状态 | 🏷 **候选，建议签** —— **未签字**（登记完就躺着，**不升级为拍板**） |
| 性质分类 | ✅ **RAG 更严**（RAG 侧多一条 `NOT NULL` 约束 ⇒ 属「**更严/更安全**的那一侧」，**不是忘了做**） |
| 组件 | `service/users/users-common/src/main/resources/db/01_create_user.sql`（`users_user` 建表脚本） |
| 对应的 DRIFT 条目 | **V-002**（[`docs/DRIFT/users/_shared.md`](../../DRIFT/users/_shared.md)） |
| 行为层 | **数据库 schema 的可空性**（能否插入 `status IS NULL` 的行） |
| 证据强度 | ✅ **真 PG 活体对照**（两侧各用自己的建表机制建表后查 `information_schema` ⇒ Go `is_nullable = YES` / RAG `NO`） |

### 差异的实际面

| 侧 | `users.status` 的可空性 | 建表脚本 |
|---|---|---|
| **Go** | `is_nullable = YES`（**可空**） | GORM `AutoMigrate`（tag 无 `not null`） |
| **RAG** | `is_nullable = NO`（**`NOT NULL`**） | `service/users/users-common/src/main/resources/db/01_create_user.sql:39` — `status BIGINT NOT NULL DEFAULT 0,` |

⇒ **DEFAULT 两侧都是 `0`**（Go 侧由 tag `default:0` 提供，见下）⇒ **差异只在可空性这一维**。

### Go 出处（**tag 是消费点**，§3.3.1）

- **`Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:11`** ——
  `Status     int64             \`gorm:"column:status;default:0"\``
  ⇒ **有 `default:0`、但没有 `not null`** ⇒ GORM `AutoMigrate` 建出的是**可空**列（DEFAULT = 0）；
- 同构的第二处（`admin` 半边的同名模型）：**`Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:41`** ——
  `Status     int64             \`gorm:"column:status;default:0"\``；
- ⇒ **对照**：同一批模型里**要** `not null` 时是明写的 ——
  `data_model.go:7`（`Uid int64 \`gorm:"column:uid;uniqueIndex;not null"\``）·
  `data_model.go:29`（`IsCurrent bool \`gorm:"column:is_current;index;not null;default:false"\``）
  ⇒ ⇒ **「`status` 可空」是 tag 的实际语义，不是 GORM 的默认行为**。

### RAG 出处

- `service/users/users-common/src/main/resources/db/01_create_user.sql:39` — `status      BIGINT NOT NULL DEFAULT 0,`

### 为什么**接受**（签字理由）

1. **方向上是「RAG 更严」**：Go 允许 `status IS NULL` 的行存在 ⇒ 那些行的「是否被封禁」语义**依赖 SQL 三值逻辑**
   （`NULL <> 0` 求值为 NULL ⇒ **不成立** ⇒ 被当作「不是被封禁」）；
   RAG 直接**不允许**这种行存在 ⇒ **语义更确定**，也**更早暴露**写入路径的错误；
2. **DEFAULT 两侧一致**（都是 `0`）⇒ 正常写入路径的**结果完全相同** ⇒ 差异**只在「显式传 NULL」这一种输入**；
3. ⇒ 与同档的 `SD-2026-10-25-CAND-16`（8 处 DEFAULT）**同族**：都是「RAG 侧多一条约束」的形态。

### ⚠️ **必须一起读的那条耦合**（**签字不等于关闭缺陷**）

🔴 **`users_user.status` 的 `NOT NULL` 与 `admin-rpc` 的 `status = 0L` 是一对耦合项** ——
`docs/DRIFT/users/_shared.md` §5·**ADJ-01** 已记：

- `admin-rpc` 的 `UserEntity.status = 0L` 判据，与 Go 的 `status <> 0` 判据**不等价**（`DRIFT-USERRPC-V-001`）；
- **若单独把 `status` 放开成可空** ⇒ RAG 的 `WHERE status = 0` 就会**匹配到 NULL 行**
  ⇒ 暴露面是 `GetUser` 响应（未注册用户被当成已封禁 / 已删除）；
- ⇒ **本条签字只认账「RAG 更严」这个事实**，**不关闭** `admin-rpc` 的 `status = 0L` 缺陷
  ⇒ 该缺陷**仍留在 users 域台账里**，**不在本 lane 的写权范围内**。

⇒ ⚠️ **本条不构成「把 `status` 放开成可空」的许可** —— 那是**另一个决策**（要同时改 `admin-rpc` 的判据），
本 lane **不碰**（`admin-rpc` 不在 global 单写权内，且改它属 users 域）。

### 触发面与残留风险

- 触发条件 = 「**INSERT 显式传 `status = NULL`**」⇒ Go 接受、RAG 报 `NOT NULL` 违例；
- ⚠️ 现有写路径**都不传 NULL**（两侧都走默认值 `0`）⇒ 当前**不可观察**；
- ⚠️ **一旦有人把 `status` 放开成可空**，本条即失效 ⇒ 且**必须同时**修 `admin-rpc` 的判据（见上）。

### 守门

⚠️ **本条没有专门守门** —— 现有守门钉的是**列类型 / 可空性对照**
（`UserRpcAvatarHistoryTimestamptzParityTest` 一族）⇒ ⚠️ 改 `status` 的可空性**当前不会让任何守门变红**
⇒ 改之前**必须先补守门**。

### 留痕位置

- [`docs/DRIFT/users/_shared.md`](../../DRIFT/users/_shared.md) §2（真库对照表 · **V-002**）· §5·**ADJ-01**（耦合项）；
- [`docs/DRIFT/users/user/rpc.md`](../../DRIFT/users/user/rpc.md) §2026-10-24 真库对照（**V-002**）。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | 🆕 **首版（第二十六轮 · L5 lane `align/global-signoff`）** —— 收录 **2 条待签字候选**（**均未签字，登记完就躺着，不升级为拍板**）⇒ ① **`SD-2026-10-25-CAND-16`**：users 域 **8 处**时间列 `DEFAULT CURRENT_TIMESTAMP`（Go 侧**无**默认）—— 性质 = **刻意保留**；Go 出处 = `service/user/user/rpc/internal/model/data_model.go:14/15/30/31` + `service/user/admin/rpc/internal/model/data_model.go:12/13/27/28`（tag 里**没有** `default`）⇒ RAG 出处 = `users-common/src/main/resources/db/{01,02,03,04}_*.sql` 的 8 处 ⇒ **真 PG 活体对照**（DEFAULT 恰好 8 处差异）② **`SD-2026-10-25-CAND-17`**：`users_user.status` 可空性（Go 可空 / RAG `NOT NULL`）—— 性质 = **RAG 更严**；Go 出处 = `service/user/user/rpc/internal/model/data_model.go:11`（`default:0` 但**无** `not null`）⇒ RAG 出处 = `01_create_user.sql:39` ⇒ ⚠️ **与 `admin-rpc` 的 `status = 0L` 耦合**（ADJ-01）⇒ 本条**不关闭**该缺陷 ⚠️ **本档是 global 代收的临时落点**（users 域档 `docs/Intentional_Deviations/users/_shared.md` 当前 0 字节，且**不在 L5 白名单内**）⇒ **整合者可把两条 fold 进该文件后删除本档** |
