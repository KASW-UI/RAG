# DRIFT 台账 · `service/article/article-rpc`

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-rpc 的记录只有一句否定式断言
> （「`article-rpc`（41 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 的实测证伪**。

## 2026-10-23 实测核对（真实执行产出，非推断）

| 项 | 实测值 | 与 AGENTS.md §0 的冲突 |
|---|---|---|
| `src/main/java` 文件数 | **36** | §0 记「41 个文件」 |
| ≤15 行的空骨架数 | **2** | §0 记「仍全是 4 行空骨架」 |
| 主源码总行数 | **7,029** | — |

已实现的实体（节选）：`ArticleDomainService`(652) / `ArticleSyncEventTypes`(625) /
`ArticleAtomicService`(583) / `ArticleRpcProperties`(402)。

> `ArticleAtomicService` 的存在说明**自调用式 `@Transactional` 的坑（DRIFT-MSG-001 同型）在本模块已按正确形态处理过**——
> 即抽独立 Bean。article-rpc 未做语义对照，所以这一点**只能从代码形状推断，不能当作已验证**。

## 结论

- **AGENTS.md §0「article 域整体仍未完成」的计数已过期。**
- article-rpc 源码已落地（7k 行），但**从未做过 Go ↔ RAG 语义对照**。
- ⚠️ 本模块含 `mq/ArticleSyncEventTypes`(625 行) —— **MQ 事件契约**。按 AGENTS.md §3.3，
> 「topic / payload / 字段号属行为必须对齐」，这是 article 域最可能藏漂移的位置，**对照时应优先**。

## 尚未处理

- Go `service/article/article/rpc` ↔ RAG `article-rpc` 逐字段对照（**从未做过**）
- MQ 事件契约冻结（无任何一端留档）
- 本模块的 `HUMAN_ONLY.MD`