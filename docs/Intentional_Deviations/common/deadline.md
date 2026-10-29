<!-- @new 2026-10-29 · W-F lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/common/deadline.md`](../../DRIFT/common/deadline.md) -->

# common · deadline 主题签字档（deadline）

> **归属**：`common` 域的「deadline」议题档（§5.3b）。
> **对象**：[`../../DRIFT/common/deadline.md`](../../DRIFT/common/deadline.md)（**HTTP 侧的取消语义**）与 [`_signed-deviations.md`](./_signed-deviations.md) §B-1（**服务端侧的取消载体**）。

---

## 本域**没有**登记在本文件的有意偏离（核对日期 **2026-10-29**）

> **核法**：`grep -rn 'SD-.*deadline\|deadline.*SD-' docs/Intentional_Deviations/` ⇒ 本主题相关的签字项**全部在别处** ⇒ ⇒ 本文件**只留指针**，不复制正文（§5.3b 硬边界 2）。

| 事项 | 落在哪 | 状态 |
|---|---|---|
| **B-1**（超时无法取消在途 IO —— **JDBC / grpc-java / `HttpClient` 均不响应线程 interrupt**） | [`_signed-deviations.md`](./_signed-deviations.md) §B | ✅ **已关闭**（2026-10-21）⇒ 🔴 **2026-10-29 重核：关闭事实仍成立，但覆盖面止于「SQL 载体」** ⇒ **出向 gRPC 载体**单列为 `E65`（🔴 OPEN）⇒ **不得**据 B-1 推断「所有超时载体都已闭合」 |
| **DRIFT-COMMON-005**（`DeadlineState` 墙钟 vs Go 单调钟 ＋ 毫秒 vs 纳秒） | —— | ✅ **已修**（2026-10-29 · 落码 `6f39bea`）⇒ **不是「认账」而是「已修」** ⇒ 不进本档 |
| `SD-2026-10-23-09`（**「共享组件 `GoStyleRequestDeadline` 的公开 API 只暴露毫秒」**） | 🔴 **`like` 域**（[`../like/api.md`](../like/api.md)）⇒ **引用必须带域限定** | ✅ 已签字（**不在本域**）⇒ 本文件**不重复登记** |

### 核对记录

- 核对日期：**2026-10-29**（W-F lane · 共享层台账面）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
