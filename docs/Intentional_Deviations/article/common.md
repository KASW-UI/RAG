<!-- @new 2026-10-29 · W-D lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/article/common.md`](../../DRIFT/article/common.md) -->

# `article` 域 common 层签字档

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」。**
> ⚠️ **签字偏离是决策，不是缺陷**：审查时不重复质疑，**也不得「顺手修正回 Go 的旧行为」**。

---

## 本组件**没有**登记在案的签字偏离（核对日期 **2026-10-29**）

> **核法（可复算）**：
> ① `grep -rn '已签字\|签字偏离\|SD-' docs/DRIFT/article/common.md` ⇒ **零命中**。
> ② `find service/article/article-common/src/main -type f` ⇒ **1 个文件 / 256 行**
> （`errmsg/ErrorCode.java`）；Go 侧对应件 = `service/article/common/errmsg/errmsg.go`
> （**1 个文件 / 45 行**）⇒ **纯错误码表**：无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存 / 无 Spring 装配层
> ⇒ **没有可供「决定不对齐」的运行面**。
> ③ 本域**已签字**的偏离全部落在**别的组件** ⇒ [`./rpc.md`](./rpc.md)（`SD-2026-10-24-01` / `-02`）。

### 核对记录

- 核对日期：**2026-10-29**（W-D lane · 台账对齐 phase）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
- ⚠️ 若日后 `article-common` 接入 DB / MQ / Spring 装配并出现「**决定不对齐**」的条目 ⇒ **必须登记到本文件**。
