<!-- @new 2026-10-29 · W-F lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/common/http.md`](../../DRIFT/common/http.md) -->

# common · HTTP 主题签字档（http）

> **归属**：`common` 域的「http」议题档（§5.3b）。
> **对象**：[`../../DRIFT/common/http.md`](../../DRIFT/common/http.md)（**请求体 Content-Type 判据 / 响应头字节形状**）。

---

## 本主题的有意偏离**归 `global` 域收口** —— 本文件**不重复登记**（核对日期 **2026-10-29**）

> **核法**：实测 `grep -rn 'SD-2026-10-25-CAND-14\|CAND-15' docs/Intentional_Deviations/` ⇒ **两条都在 `global` 域**
> ⇒ ⇒ 本主题的 HTTP wire 层偏离**已由 `global` 域承载**（§5.3b 硬边界 2：一条问题只在一个档落笔）。

| 事项 | 落在哪 | 状态 |
|---|---|---|
| **T-7**（HTTP 状态行**缺 reason phrase**：Go `HTTP/1.1 200 OK` / RAG `HTTP/1.1 200`） | 🔴 [`../global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-CAND-14` | 🏷 **候选 · 未签字**（2026-10-29 重核：**源码级仍成立**；⚠️ 仓内**无机器守门**、本轮**未跑真容器取字节** ⇒ **待核实**） |
| **T-12 / CAND-15**（「方法不匹配」出口 RAG 比 Go **多带**一个 `Traceparent`） | 🔴 [`../global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-CAND-15` | 🏷 **候选 · 未签字**（2026-10-29 重核：**源码级仍成立**；⚠️ 仓内**无机器守门**、本轮**未跑真容器取响应头** ⇒ **待核实**） |
| **T-6**（成功/失败响应的 `Content-Type` 缺 `charset`） | —— | ⛔ **已失效（2026-10-29 真实性复核 · W-F lane）** —— 原判 **🟡 仍 OPEN**（依据「用户 2026-10-24 拍板『保持登记，本轮不做』」）⇒ **现行态 = ⛔ 已失效**：<br>① **被签字描述的那个行为已不存在** —— RAG 侧 JSON 响应**现在带** `charset=utf-8`：`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）＋ `:151`（`response.getHeaders().set(HttpHeaders.CONTENT_TYPE, GO_JSON_CONTENT_TYPE)`）；<br>② **该 advice 对 10 个 api 全生效** —— 已登记 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2`（`com.rag.common.http.GoStyleJsonContentTypeAdvice`）⇒ 且各 api 模块依赖 `common`（例：`service/like/like-api/pom.xml:61-64`）；<br>③ **挡路的那条签字已撤销** —— `SD-HOT-API-006` 已于 **2026-10-25 用户拍板撤销**，撤销理由**就是**「修 T-6」（`docs/Intentional_Deviations/hot/api.md` 的 `SD-HOT-API-006` 条目）⇒ ⇒ **2026-10-24 的「本轮不做」已被 2026-10-25 的「撤销签字并修 T-6」覆盖** ⇒ ⇒ **原判是陈旧快照**；<br>④ ⚠️ **本条不是签字项**（T-6 从未被登记为有意偏离）⇒ ⇒ **不进本档**（本行只是**销账**）；· 核对日期 2026-10-29 |
| **DRIFT-006**（请求体 Content-Type 判据） | —— | ✅ **已关闭**（[`../../DRIFT/common/http.md`](../../DRIFT/common/http.md)）⇒ 不进本档 |

### 核对记录

- 核对日期：**2026-10-29**（W-F lane · 共享层台账面）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
