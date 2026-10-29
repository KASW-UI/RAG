<!-- @new 2026-10-29 · W-D lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/comment/common.md`](../../DRIFT/comment/common.md) -->

# `comment` 域 common 层签字档

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」。**
> ⚠️ **签字偏离是决策，不是缺陷**：审查时不重复质疑，**也不得「顺手修正回 Go 的旧行为」**。

---

## 本组件**没有**登记在案的签字偏离（核对日期 **2026-10-29**）

> **核法（可复算）**：
> ① `grep -rn '已签字\|签字偏离\|SD-' docs/DRIFT/comment/common.md` ⇒ **零命中**。
> ② `find service/comment/comment-common/src/main -type f` ⇒ **4 个文件 / 563 行**
> （`errmsg/CommentCodeError.java` · `errmsg/CommentErrorMessages.java` ·
> `errmsg/CommentGrpcError.java` · `errmsg/CommentErrorCode.java`）；
> Go 侧对应件 = `service/comment/common/errmsg/errmsg.go`（**1 个文件 / 103 行**）
> ⇒ **纯错误码表 + 一个跨模块错误通道的编码端**。
> ③ 本组件唯一的「非逐字复制点」（**跨模块 trailer 通道**）的**缺陷**落在 `comment-api`（消费端）
> ⇒ 已修 ⇒ **不是「决定不对齐」** ⇒ 不进本档；见 [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md)
> 的 **MISMATCH-API-01**（已修）· 教训档见 [`../../Pitfalls/comment/api.md`](../../Pitfalls/comment/api.md)
> 的 **PIT-COMMENT-01**（旧号 `PIT-COMMENT-API-01`）。
> ④ 本域**已签字**的偏离落在**别的组件** ⇒ [`./rpc.md`](./rpc.md)（`SD-2026-10-24-CAND-02` / `-06` /
> `SD-2026-10-23-04` 等）· [`./api.md`](./api.md)（`SD-2026-10-24-CAND-07` / `-08`）。

### 核对记录

- 核对日期：**2026-10-29**（W-D lane · 台账对齐 phase）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
- ⚠️ 若日后 `comment-common` 出现「**明知与 Go 不同、决定不对齐**」的条目 ⇒ **必须登记到本文件**（并带签字人 / 签字日期）。
