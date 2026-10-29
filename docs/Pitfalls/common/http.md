<!-- @move AGENTS.md L1719-L1753 · §3.3.3 E-1 案例：text/plain 400 丢 Content-Type —— 「代码里写了」≠「客户端收到」 -->
## PIT-COMMON-08 · 「代码里写了」≠「客户端收到」 —— `text/plain` 400 丢 `Content-Type`（E-1 案例）  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**是**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：是** —— 「代码里写了 ≠ 客户端收到」正是本轮读 E65 / 守门结论时用的判据 ⇒ 直接适用。**修复已在位**（`setHeader` / `addHeader` / `setContentType` 三处都改成实时 propagate），但**形态**未闭环 ⇒ 仍生效。
> **旧号**：`（本文件原无编号 · `@move` 搬移段）` ⇒ **新号**：`PIT-COMMON-08`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留**）。
> **证据**：修复在位：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:1054`（`setHeader` 实时 propagate）/ `:1076`（`addHeader`）/ `:1092`（`setContentType`）；守门 `tools/difftest/check-plaintext400-headers.sh` 在位；⚠️ 原类名 `ObservabilityResponseWriter` **已不存在**（现为 `HttpMiddleware` 内的 `GoStyleStatusCapturingResponse`）⇒ 本条「出处」段的类名是**旧名**。
`GlobalExceptionHandler.goStylePlainTextBadRequest` 里明明写着
`headers.setContentType(TEXT_PLAIN)` + `set("X-Content-Type-Options","nosniff")`，
而 `ObservabilityResponseWriter.setHeader` 只把 header 收进 `capturedHeaders`、
等 `flushTo` 再写回 underlying。真实 Tomcat 下 `flushTo` 发生在 `chain.doFilter()`
**之后**——此时下游 `outputMessage.flush()` 已让底层 committed，
`dst.setHeader(...)` 被 **静默忽略**（不抛异常、不打日志）。

实测（2026-10-22 curl，两端同请求）：

| | Go | RAG 修复前 |
|---|---|---|
| 状态行 | `400 Bad Request` | `400 `（reason phrase 都没有） |
| Content-Type | `text/plain; charset=utf-8` | **缺失** |
| X-Content-Type-Options | `nosniff` | 缺失 |
| 传输 | `Content-Length: 25` | `Transfer-Encoding: chunked` + `Connection: close` |

**它此前 4 轮对拍全绿**，因为 difftest **只比 body 不比响应头**，
而 body 恰好是对的（DRIFT-006 对齐的正是正文）。缺陷就藏在从未被比对的那一层。

⇒ **两条推论**：
1. **响应头必须纳入对拍**。body 对齐 ≠ 响应对齐；`Content-Type` 决定客户端怎么解析 body，
   缺了它等于把「这是 JSON 还是纯文本」的判断权交给客户端默认值。
2. **这类缺陷 MockMvc 测不到**。实测 `MockHttpServletResponse` 在这个时序上与 Tomcat
   不同：MockMvc 里 Content-Type 完好（`text/plain;charset=UTF-8` + `nosniff`），
   真实栈 curl 是空的。⇒ 守门只能用**真实容器**（`tools/difftest/check-plaintext400-headers.sh`）。
   这是 §3.4.2「N-1 的二次坍塌」的同款陷阱：**层次没对齐时，单独那一层全绿就够骗过 CI。**

**修复**：`setHeader` / `addHeader` / `setContentType` 改为**实时 propagate** 到 underlying，
与 `setStatus` / `sendError`（2026-10-21 修的那两个）同一策略。
`capturedHeaders` 保留作兜底二次写入 —— Tomcat 的 `setHeader` 是**覆盖**语义，重复写不产生重复 header。

> ⚠️ **`HttpServletResponseWrapper` 默认的 `setContentType` 是直接透传的**，
> 本类此前未 override 它，于是同一份响应里存在「`setContentType` 直通 / `setHeader` 进 buffer」
> **两套语义**。已显式接管 `setContentType`，让三者走同一条路径。
> 下次再动这个 wrapper，注意它有三个覆写点，不是一个。
