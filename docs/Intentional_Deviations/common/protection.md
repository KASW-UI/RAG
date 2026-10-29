<!-- @new 2026-10-29 · W-F lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/common/protection.md`](../../DRIFT/common/protection.md) -->

# common · 保护层主题签字档（protection）

> **归属**：`common` 域的「protection」议题档（§5.3b）。
> **对象**：[`../../DRIFT/common/protection.md`](../../DRIFT/common/protection.md)（**请求保护层下沉到 `service/common`**）。

---

## 本主题的有意偏离**归 `global` 域收口** —— 本文件**不重复登记**（核对日期 **2026-10-29**）

> **核法**：实测 `grep -rn 'SD-2026-10-25-01' docs/Intentional_Deviations/` ⇒ **在 `global` 域**
> ⇒ ⇒ 保护层唯一一条已签字偏离**已由 `global` 域承载**（§5.3b 硬边界 2）。

| 事项 | 落在哪 | 状态 |
|---|---|---|
| **T-2 / T-5**（`MaxConns` 闸门作用域：Go **每路由一个** latch vs RAG **全进程一个**） | 🔴 [`../global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-01` | ✅ **已签字**（2026-10-29 重核：**签字仍有效** —— RAG `GoStyleMaxConnsFilter.java:102`/`:106` 仍是**构造期建一次**的 `Semaphore`；Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go` 仍是**每路由一个**） |
| **DRIFT-COMMON-009**（`method.getFullMethodName()` 无 target 前缀 ⇒ 熔断计数串了） | —— | ✅ **已修**（2026-10-25 第三轮 · 身份源改为 `Channel.authority()`）⇒ 不进本档 |
| **T-3**（503 / 499 响应缺 `X-Content-Type-Options: nosniff`） | —— | ✅ **已修**（2026-10-24 · commit `8d019e0`）⇒ 不进本档 |

### 核对记录

- 核对日期：**2026-10-29**（W-F lane · 共享层台账面）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
