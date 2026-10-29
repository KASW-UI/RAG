# `_cross/signed-deviations.md` —— 跨域**签字偏离**相关的踩坑（跨域档）

> **本文件此前是 0 字节占位**（2026-10-29 台账对齐轮补上）。**不编造坑位** —— 这里写清
> 「这份文件是干什么的 / 已经核过什么 / 真正的内容在哪」。

## 用途

登记**跨域范围**的**有意偏离**（不是缺陷）在对齐过程中**踩到的坑** —— 即：
「看到两侧不一致时，怎么判断它是签字项还是真漂移」这一类**判断陷阱**。

## 已核范围（核对日期 2026-10-29）

- 全仓 `docs/Pitfalls/**` 的条目标题（守门口径：`^#{2,4}\s+(.+)$` + `^(?:PIT|PF|P)(?:-[A-Za-z0-9]+)*-\d+`）
  ⇒ **共 336 条**，**其中与本文件同名的「跨域签字偏离类踩坑」= 0 条** ⇒ **本文件确实没有独立登记的坑位**。

## 内容在哪（指针）

| 内容 | 位置 |
|---|---|
| **跨域签字偏离本体**（决策，不是缺陷） | [`../../Intentional_Deviations/_cross/signed-deviations.md`](../../Intentional_Deviations/_cross/signed-deviations.md) |
| **签字失效口径**（被签字描述的行为已不存在 ⇒ 自动失效） | [`../../Intentional_Deviations/_INDEX.md`](../../Intentional_Deviations/_INDEX.md) §计数表 |
| **跨域漂移本体**（要修的那些） | [`../../DRIFT/_cross/open-items.md`](../../DRIFT/_cross/open-items.md) |
| **「跑红 ≠ 是缺陷」类元教训** | [`testing-pitfalls.md`](testing-pitfalls.md) · [`fix-round-lessons.md`](fix-round-lessons.md) |

## 状态

- **状态**：⚪ **无独立登记条目**（不是「已闭环」，是**从未登记**）
- **核对日期**：**2026-10-29**
- **是否影响当前轮次**：**否** —— 本文件只是**占位说明**，不构成任何执行约束
  （原因：**没有可转化的约束**；真正的跨域纪律在 `docs/Worktree.md` 与 `AGENTS.md` §3.1/§3.3）
