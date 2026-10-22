# 🔴 Pitfall：多个服务共用同一个 PG schema 时，Flyway 账本必然撞号

> **归档时间**：2026-10-23 · **性质**：跨模块基础设施坑（影响 `message-rpc` / `like-rpc` / `article-rpc`）
> **决策记录**：AGENTS.md §6.3 问题 4 · **状态**：**已定案，未实施**
> **守门**：`tools/difftest/check-flyway-ledger.py`（未修复态实测 `EXIT=1`）

## 一句话

`flyway_schema_history` 是 **schema 级**账本，**一个 schema 只能有一本账**。
只要有第二个服务往同一个 schema 放迁移，它就会把自己的迁移**静默跳过**——不报错，表就是建不出来。

## 症状长什么样

后启动的那个服务在启动日志里打：

```
Current version of schema "public": 2
Migrating schema "public" to version "3 - create user"
```

看起来**迁移成功了**，实际上它自己的 `V1` / `V2` 被判定为「已应用」而**根本没跑**。
终局是业务侧的 `relation "xxx" does not exist`，很容易被误诊成「SQL 写错了」。

## 本仓的实测条件（2026-10-23）

三个硬条件**同时**成立，缺一不成立：

| # | 条件 | 证据 |
|---|---|---|
| 1 | **同一个库** | `docker-compose.yaml:16` 与 `:127` 均为 `POSTGRES_DB: ${POSTGRES_DB:-first_db}`；五个模块 JDBC URL 全指向它 |
| 2 | **同一个 schema** | 三处均为 `spring.flyway.schemas: public` |
| 3 | **同一本账** | 全仓 `spring.flyway.table` **零命中**，无任何模块改过账本表名 |

撞号的三方：

| 模块 | 账本表（修前） | 迁移 |
|---|---|---|
| `service/message/message-rpc` | `flyway_schema_history` | V1 V2 V3 |
| `service/like/like-rpc` | `flyway_schema_history` | V1 V2 V3 V4 V5 |
| `service/article/article-rpc` | `flyway_schema_history` | V8 V9 V10 |

⇒ `message-rpc` 与 `like-rpc` 的 **V1–V3 版本号完全撞车且内容互不相同**
（`message_conversation` vs `like_record` 等）。任一方先迁移，另一方的表就永远建不出来。

## 为什么这么隐蔽

1. **不报错**。Flyway 把「版本号已存在」判为已应用，是**合法状态**，不是错误。
2. **Go 侧不会暴露这个问题**。Go 用 GORM `AutoMigrate`，无版本号、无账本、无 checksum、无 baseline，
   谁先启动都行。⇒ 这是**本仓库引入 Flyway 带来的复杂度**，不是 PG 或 Go 的固有问题。
3. **各模块单跑都正常**。每个 `application.yml` 单独看都"配得挺全"，问题只在**它们叠在一起**时出现。

## ⚠️ 已被实测证伪的绕法（别再试第二次）

看上去最该先试的是给后来者加两个开关，让它"容忍"先来者写下的账本：

```bash
--spring.flyway.validate-on-migrate=false --spring.flyway.out-of-order=true
```

**它行不通，而且会让账本更脏。** `tools/difftest/exp-drift015-flyway-flags.sh`（真 jar + 真 PG 16）实测终态：

```
账本 = [ 1|create user,  2|create user avatar history,  3|create user ]
        ↑ user-rpc 写的              ↑ user-rpc 写的         ↑ admin-rpc 的 V3（与第 1 行语义重复）
✗ admin_admin   缺失   ← V1__create_admin.sql        根本没跑
✗ admin_invite  缺失   ← V2__create_admin_invite.sql  根本没跑
admin-rpc 启动失败：relation "admin_admin" does not exist
```

机理：Flyway 只应用「pending」的迁移，版本号被占 ⇒ 静默跳过。
校验开关只关掉 checksum 检查，**管不了这一步**。
更糟的是账本里多了一条 `version 3 = create user`，把 schema **永久钉在错误版本上**，日后正修得先人工清账本。

> 💡 **同一类错误在本仓发生过两次**：users 域 2026-10-21 因此**丢掉 Flyway**；
> message/like/article 三域踩的是**同一个坑**，只是还没被触发。
> 病根只有一条，10-21 那天的分析是对的——**只是只被应用到了 1/4 的模块上**。

## 采纳的修法

给每个模块独立账本表名：

```yaml
spring:
  flyway:
    table: flyway_schema_history_<模块>
```

- 改动面 = 3 个 yml 各加一行
- **不改任何迁移文件名**，各模块从 V1 各自重新开始也互不干扰
- **直接消除病根**（账本不再是 schema 级的唯一一本）

### 为什么不用「排全局唯一号段」

- 那是 2026-10-20 的旧结论，**10-21 已被推翻过一次**，别再翻回来
- 要改 8 个 SQL 文件名，却**不解决**「账本是 schema 级」这个根因——
  只要还有第二个模块往同一 schema 放迁移，撞号就会再现

## ⚠️ 实施前必读：本条尚未修

**三个 `application.yml` 的 `spring.flyway.table` 至今未添加。**
下一个接手者请先跑 `tools/difftest/check-flyway-ledger.py` 确认现状为红，再实施。

**效力边界**：静态检查只保证**声明层**账本表名两两不同；
**不证明 Flyway 运行时真的按 `spring.flyway.table` 建表** ——
那需要真 PG 上双模块对启动，属 AGENTS.md §3.3「框架机制必须验证实际生效」，**尚未做**。
