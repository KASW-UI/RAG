<!-- @move AGENTS.md §3.3 · 框架机制静默失效的实证证据 -->

# 框架机制静默失效：实证证据

> 从 AGENTS.md §3.3 搬出。**规则条文留在 §3.3**，本文是它的证据。

---

**已实证的两组反向例子（`like-api`，见 §0 对应小节）：**

| 你会看到 | 真实情况 | 出处 |
|---|---|---|
| Go yaml 里**没有** `Timeout` | 实际 **3000ms**，struct tag `json:",default=3000"` 生效 ⇒ 3s 超时 503 真实存在 | `go-zero@v1.10.2/rest/config.go:55` |
| Go tag 是 `json:"target_ids"`，看着「没有 optional」 | 实际**解析失败**（`type mismatch`），不是「填空值放行」 | `core/mapping/unmarshaler.go:944-950` → `processFieldNotFromString` → `processFieldPrimitive` |

`like-api` 的 DRIFT-LIKEAPI-002 里，两条绿测试的 `@DisplayName` 直接写着
「Go: Slice 永不计必填」，而这个 Go 结论后来被活体探针**推翻**。
⇒ 危害不是「测试没抓到 bug」，而是**修复时会先撞上这两条红测试**，
且测试名会让人误判「修复」才是漂移方向。
已实证的四类静默失效：

| 机制 | 静默失效形态 |
|---|---|
| `@Transactional` | self-invocation 不经代理 → 注解形同虚设，且**无任何异常** |
| `@ResponseBodyAdvice` | `HttpEntityMethodProcessor` 继承 `writeWithMessageConverters` 并查询 advice chain → 连 `@ExceptionHandler` 返回的 `ResponseEntity` 也会被二次包裹 |
| `@TableField(typeHandler=…)` | 只对 MP 自己生成的 SQL 生效；`LambdaUpdateWrapper.set()` 的参数走 `ew.paramNameValuePairs`，**丢掉字段类型信息** |
| **命令行注入的配置属性** | 前缀写错 ⇒ 落到**不存在的** `@ConfigurationProperties` ⇒ Spring Boot 对未知命令行属性**静默接受** |
