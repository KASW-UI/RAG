# DRIFT 台账 · `service/hot/hot-api`

> ⚠️ **本文件当前无搬移内容**。AGENTS.md 里关于 hot-api 的记录只有一句否定式断言
> （「`hot-api`（13 个文件仍全是 12 行空骨架）未动 ⇒ **hot 域整体仍未完成**」）。

## 2026-10-23 实测核对（真实执行产出，非推断）

| 项 | 实测值 |
|---|---|
| `src/main/java` 文件数 | **13** |
| ≤15 行的空骨架数 | **13**（**全部**） |
| 主源码总行数 | 见下 |

**结论：hot-api 的否定式断言仍然成立**，无需推翻。与 `article` 域不同。

## 与已完成的 hot 域两组件的关系

hot 域是唯一被拆成三个组件的域，当前进度：

| 组件 | 状态 | 台账 |
|---|---|---|
| `hot-common` | ✅ 双侧对拍 sha256 逐字节一致 | [`../hot/common.md`](common.md) |
| `hot-rpc` | ✅ 双侧对拍逐字节一致，46 条契约测试 | [`../hot/rpc.md`](rpc.md) |
| **`hot-api`** | ❌ **13 个文件全为空骨架，从未实现** | **本文件** |

⇒ **hot 域整体未完成的唯一原因就是本模块。** 这是 hot 域收口的唯一剩余工作。

## 尚未处理

- Go `service/hot/hot/api` ↔ RAG `hot-api` 语义对照（**代码都还没写，对照无从谈起**）
- 本模块的 `HUMAN_ONLY.MD`
- ⚠️ 按 AGENTS.md §3.3，hot-api 若实现，**必须先确认它是否也需要
  `com.rag.common.protection` 的四层请求保护**（`hot-rpc` 已接入，需查 hot-api 的 Go yaml 是否同样默认开启）