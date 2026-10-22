# DRIFT 台账 · `service/article/article-api`

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-api 的记录只有一句否定式断言
> （「`article-api`（29 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 00:1x 的实测证伪**。

## 2026-10-23 实测核对（真实执行产出，非推断）

依据 AGENTS.md §6.2 的阅读约定第 1 条 —— 「每行必须带最后核对日期，且该日期必须由一次真实执行产出」：

| 项 | 实测值 | 与 AGENTS.md §0 的冲突 |
|---|---|---|
| `src/main/java` 文件数 | **36** | §0 记「29 个文件」 |
| ≤15 行的空骨架数 | **0** | §0 记「仍全是 4 行空骨架」 |
| 主源码总行数 | **4,275** | — |
| 最新写入时间 | **2026-10-22 23:56** | 晚于 AGENTS.md 最后编辑（23:40） |

已实现的实体（节选）：`GlobalExceptionHandler`(339) / `ArticleApiSupport`(304) / `Article`(258) /
`ArticleController`(231) / `JwtAuthInterceptor`(228) / `ArticleUpdateService`(189) /
`ArticleCreateService`(187) / `CreateArticleReq`(151) / `GoFormValues`(148) /
`ArticleListService`(140) / `ArticleGoStyleCorsFilter`(137)。

## 结论

- **AGENTS.md §0「article 域整体仍未完成」的计数已过期。**
- article-api 的源码已落地，但**从未做过 Go ↔ RAG 语义对照**，也**没有对应的 DRIFT 台账内容**。
- ⇒ 本文件是**空白起点**，不是「已核对无漂移」。下一个接手 article-api 的 agent 应以此为第一件事。

## 尚未处理

- Go `service/article/article/api` ↔ RAG `article-api` 逐字段对照（**从未做过**）
- 本模块的 `HUMAN_ONLY.MD`（全仓仅 4 个 users 域有）
- 根目录 `HUMAN_ONLY/` 下无 article 相关包指南