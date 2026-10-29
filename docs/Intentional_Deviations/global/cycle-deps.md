<!-- @new 2026-10-29 · W-F lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/global/cycle-deps.md`](../../DRIFT/global/cycle-deps.md) -->

# global · 循环依赖签字档（cycle-deps）

> **归属**：`global` 收口域的「cycle-deps」议题档（§5.3b）。
> **对象**：`docs/DRIFT/global/cycle-deps.md` 记的**可两步闭环的循环依赖**（CYCLE-001 / CYCLE-002）。
> ⚠️ **与 DRIFT 侧的边界**：DRIFT 侧记「**还没对齐、要修**」；本档记「**明知不一致、已认账**」。

---

## 本域暂无已登记的有意偏离（核对日期 **2026-10-29**）

> **核法**：`grep -n 'Intentional_Deviations\|签字\|SD-' docs/DRIFT/global/cycle-deps.md` ⇒ **0 命中**
> ⇒ 该议题档**全文没有任何指向签字档的指针** ⇒ ⇒ **无签字项可登记**。

### 逐条结论

| 编号 | 事项 | 结论 |
|---|---|---|
| **CYCLE-001** | `RpcInterceptor.markExpired(long)` 与 `UserDomainService` javadoc 的**双向循环引用** ⇒ 改一个会留半截 | ✅ **已闭环（2026-10-25）** —— **两步同一次提交走完**（删 `markExpired` + 改 javadoc）⇒ ⇒ **不是「明知不一致、已认账」，而是「已修」** ⇒ **不进本档**（DRIFT 侧已销账，见 [`../../DRIFT/global/cycle-deps.md`](../../DRIFT/global/cycle-deps.md)） |
| **CYCLE-002** | `service/common` 的 javadoc 不得出现指向别的域类的 `{@link}` | ✅ **0 命中**（守门 `tools/difftest/check-cross-domain-holds.py`）⇒ ⇒ **无差异可认账** ⇒ 不进本档 |

### 关联（**不属本档**）

- 🔴 `PIT-GLOBAL-003`（订正一句假断言必须同时补运行时证据）⇒ [`../../Pitfalls/global/cycle-deps.md`](../../Pitfalls/global/cycle-deps.md)；
- 🔴 `PIT-GLOBAL-004`（守恒扫描会命中它自己）· `PIT-GLOBAL-010`（跨域引用扫描会命中 JDK 同名类）⇒ 同上。

### 核对记录

- 核对日期：**2026-10-29**（W-F lane · 共享层台账面）
- 核对方式：`grep`（DRIFT 侧指针）＋ 读 [`../../DRIFT/global/cycle-deps.md`](../../DRIFT/global/cycle-deps.md) 正文
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确实没有 ⇒ 写明「本域暂无已登记坑位/偏离」**」处置。
