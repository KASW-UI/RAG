<!-- @move AGENTS.md L2443-L2481 · §6.2.4① Go 对空 body 的三段取证（admin-api） -->
### 6.2.4 本轮（2026-10-21 第二批）取证与附带发现

#### ① Go 对「空 body」的三段取证（支撑 admin-api 13 端点 `required=false`）

不是推断，是逐段读源码得到：

| # | 出处 | 事实 |
|---|---|---|
| 1 | `service/user/admin/api/internal/handler/admin/*.go` | 13 个 handler 一律 `if err := httpx.Parse(r, &req); err != nil`，`req` 是包级 `var req types.XxxReq` |
| 2 | go-zero v1.10.2 `rest/httpx/requests.go:153` `withJsonBody` | `r.ContentLength > 0 && ContentType 含 application/json`。空 body ⇒ false ⇒ `ParseJsonBody` 走 `mapping.UnmarshalJsonMap(nil, v)`，对 struct 是 **no-op、不报错** |
| 3 | admin `api/internal/types/types.go` | 13 个 Req **零个** `Validate()` 方法、**零个** `validate:` tag；且 admin 服务**从未**调 `httpx.SetValidator` ⇒ `getValidator()` 返回 nil ⇒ `httpx.Parse` 的校验环节整体不生效 |

**结论**：Go 对空 body 的 POST 一律 **HTTP 200 + 业务码**，req 停在**零值 struct**。
以 `loginlogic.go:37-56` 为例：零值 username/password **直接透传给 rpc**，
由 rpc 判 `NotFound → 1003` / `Unauthenticated → ErrorLoginWrong`（= **1002**，不是 1008）。

**RAG 处置**：13 端点全改 `@RequestBody(required = false)` +
Controller 侧 `if (req == null) req = new XxxReq()`（对齐 Go 的零值 struct，服务层因此不会 NPE）。

> **user 侧同样做了独立取证**（2026-10-21，**未套用 admin 的结论** ——
> §3.4.3 N-3 明确警告过「admin 侧取参机制不同，不可跨模块套用结论」）：
> `internal/handler/user/*.go` 9 个 handler 一律 `var req types.XxxReq` + `httpx.Parse`；
> `types.go` 8 个 Req、**零个** `validate:` tag、**零个** `Validate()`；
> 整个 user 服务**从未**调 `httpx.SetValidator`。结论与 admin 侧一致 ⇒
> user-api **9 个端点全部**改为 `required=false` + Controller 侧 `null → new XxxReq()`。
> 落点验证：`loginlogic.go` 的 switch 是 `Internal → ErrorServerCommon` / `default → CodeServerBusy`，
> 零值 username/password **直接透传给 rpc**（`UserLoginService` 同样对 null 字段兜底成 `""`）。
>
> **⚠️ 2026-10-21 二次更正（DRIFT-NEW-016）**：本段原写「新增 2 条真管线断言
> `emptyBodyOnLoginDoesNotReturn400` / `emptyBodyOnUpdateDoesNotReturn400`」并断言
> 零值 LoginReq **带着空 username/password 抵达 rpc**。该结论**已被实测证伪**：
> Go 的 `UnmarshalJsonMap(nil, v)` 收的是**有类型的 nil map**，在 `unmarshaler.go:988`
> 的 type switch 里命中 `case map[string]any`，照常遍历字段 ⇒ 真空 body 返回
> `400 field "username" is not set`，**零值 req 到不了 rpc**。
> 该条已改名 `emptyBodyOnLoginIs400NotPassedThrough` 并反转断言；
> `emptyBodyOnUpdateDoesNotReturn400` 因 `user.update` 四字段全 `,optional` **不反转**，
> 改名 `emptyBodyOnUpdateStillPassesThroughBecauseAllFieldsAreOptional`，留作对照组
> ——同一个「空 body」在两个端点一个 400 一个 200，差别只在 DTO 的 tag。

