<!-- @new 2026-10-29 · W-F lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/common/audit.md`](../../DRIFT/common/audit.md) -->

# common · 审计主题签字档（audit）

> **归属**：`common` 域的「audit」议题档（§5.3b）。
> **对象**：[`../../DRIFT/common/audit.md`](../../DRIFT/common/audit.md) 记的 **2026-10-21 `service/common` 语义一致性审计**（20 项偏差 / 修复 21 处）的**认账部分**。

---

## 本主题的有意偏离**集中在同域的 `_signed-deviations.md`** —— 本文件**不重复登记**（核对日期 **2026-10-29**）

> **核法**：实测 `grep -n 'A-1\|A-2' docs/Intentional_Deviations/common/_signed-deviations.md` ⇒ **A-1 / A-2 两条都在**；
> ⇒ ⇒ 审计产出的**全部幸存偏离**（A-1 / A-2）**已由 [`_signed-deviations.md`](./_signed-deviations.md) 承载**。
>
> ⚠️ **§5.3b 硬边界 2「一条问题只在一个档落笔」** ⇒ **本文件不复制那两条正文**，只留指针。

| 审计产出的条目 | 落在哪 | 状态 |
|---|---|---|
| `A-1`（`Logger.doLogFatal` 等价域收窄） | [`_signed-deviations.md`](./_signed-deviations.md) §A | ✅ 已签字（2026-10-29 重核：**签字仍有效**，Go `logger.go:293-296` 无条件 `os.Exit(1)` vs RAG `Logger.java:443` 抛 `FatalError`） |
| `A-2`（`Logger.isFiltered` 的 `call_chain` 少框架帧） | [`_signed-deviations.md`](./_signed-deviations.md) §A | ✅ 已签字（2026-10-29 重核：**签字仍有效**，Go `logger.go:299-316` 的 `getFuncName` 只回末两段 ⇒ `github.com/zeromicro/go-zero/` 前缀仍是死前缀） |
| 其余 18 项（审计当时「已修 21 处」） | —— | ✅ **已修**，不进本档；逐条证据见 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md) §十三·一 |

### 核对记录

- 核对日期：**2026-10-29**（W-F lane · 共享层台账面）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置（**不是**「本域暂无」）。
