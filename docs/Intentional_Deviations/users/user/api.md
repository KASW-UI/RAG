# 有意偏离 · `service/users/user/user-api`

> **这里是「决策」，不是缺陷。** 与 [`../../DRIFT/users/user/api.md`](../../DRIFT/users/user/api.md) 的分工：
> DRIFT 记「需要对齐的差异」，本文件记「**决定不对齐**的差异」。
>
> ⚠️ **读之前先记住两条**：
> 1. 本文件里的条目**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
> 2. 审查时**先来这里确认**，再决定某条红色差异是不是回归。

---

## SD-USERAPI-001（2026-10-23 用户签字）—— 未捕获异常的 500 响应**带完整 envelope**，不复刻 Go 的空 body

| 项 | 值 |
|---|---|
| 对应 DRIFT | `DRIFT-USERAPI-007` |
| 签字日期 | **2026-10-23** |
| 决定 | **保留 RAG 现状**（HTTP 500 + envelope），**不对齐 Go 的空 body** |
| 性质 | ① 有意偏离 —— RAG 侧保留更多信息，不是「忘了做」 |
| 守门网 | `PanicFallbackResponseShapeTest`（2 条，`standaloneSetup` + 真 advice，**零容器 / 0.6 秒**） |

### Go 侧事实（消费点，非声明点）

`go-zero@v1.10.2/rest/handler/recoverhandler.go:12-23` 全文：

```go
func RecoverHandler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if result := recover(); result != nil {
				internal.Error(r, fmt.Sprintf("%v\n%s", result, debug.Stack()))
				w.WriteHeader(http.StatusInternalServerError)   // ← 只写状态码
			}                                                  // ← 全函数没有任何 w.Write(...)
		}()
		next.ServeHTTP(w, r)
	})
}
```

⇒ Go 侧：**HTTP 500 + 空 body**（连 `Content-Type` 都不是这个 handler 设的）。

### RAG 侧事实

`GlobalExceptionHandler#handleAny:262-272`
⇒ **HTTP 500 + `{"code":5001,"msg":"系统内部错误","data":null}`**。

**状态码两侧一致（500），差异只在 body 形状。**

### 为什么签字保留 envelope（不改成空 body）

1. **原 DRIFT 自己的论证反对 Go 这一侧**：
   `DRIFT-USERAPI-007` 的 `Must Preserve` 栏原文写着
   > 「任何按 body 结构统一解析的客户端都会在这里**踩空**」。

   ⇒ **Go 的空 body 正是「让客户端踩空」的那一版**。对齐它 = 主动降低信息量。
2. **已有同款先例**：项目对 `401` 的 body 形状做过完全一样的决策并签字
   （`SD-2026-10-23-01/02/03` 同批中的 **N-1**，见 [`../_INDEX.md`](../_INDEX.md)）。
   ⇒ 同一类响应形状问题，不应在本域出现相反的处置。
3. **HTTP 500 的语义**：500 意味着「服务端没能正常处理完」。
   带一个**可识别的业务码 5001**，客户端才能区分「系统内部错误」与
   「网关/代理返回的 500」；空 body 做不到这一点。

### 守门网的作用已经反转（这点要特别说明）

`PanicFallbackResponseShapeTest` 的作用**不是**「守住正确」，
而是**守住「不漂」** —— 防止这个已签字的行为在后续重构里被无声改掉。

| # | 用例 | 若被误改会怎样 |
|---|---|---|
| 1 | 未捕获异常 → 500 + `application/json` + `{code:5001,msg:系统内部错误}` | 偏离消失（body 变空） |
| 2 | **对照组**：正常端点仍 200 + envelope | 有人为了对齐 500 而乱动 advice，把 200 路径一起改坏 |

⚠️ 用例 2 的必要性来自 `DRIFT-006` 的历史教训：
修 400 文本时曾出现「为对齐一种状态码而污染另一种」的形态。

### 效力边界（越界即失效）

- 本条**只覆盖「未捕获异常（Go 的 panic 兜底）」这一条路径**的 500 body 形状；
- **不覆盖** `DRIFT-USERAPI-006`（400 的 `Content-Type` 少 charset）——
  那条**未签字**，仍是待对齐项；
- **不覆盖** `DRIFT-USERAPI-010`（黑名单 401 的 `Content-Type` 拼写错误）——
  那条已由 **N-1** 签字「不复刻拼写错误」，属另一条偏离，不要与本条混谈。

### 关闭条件

本条**不设自动关闭条件** —— 它是长期决策。
若将来要撤销（即改为对齐 Go 的空 body），必须同时：
1. 改 `GlobalExceptionHandler#handleAny` 使其返回 null body；
2. 让 `GlobalResponseAdvice` 对该响应透传（否则会被再包成 envelope）；
3. **改写** `PanicFallbackResponseShapeTest` 的期望值 —— 否则它会红；
4. 在本文件追加一条「撤销记录」，保留历史。