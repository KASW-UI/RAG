# DRIFT 台账 · `service/users/admin/admin-rpc`

> **本文件是导航页，不是内容页。**
> 与 [`../user/rpc.md`](../user/rpc.md) 同理：AGENTS.md 里 `admin-rpc` 的记录没有独立成节，
> 散落在以 `service/users` 为标题的多模块叙述里。为**不割裂叙事、不改一字**，整体搬到
> [`../_shared.md`](../_shared.md) 与 [`_shared.md`](_shared.md)。本页给出精确索引。

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