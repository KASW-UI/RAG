# 签字偏离台账 · `admin-api`

> 域共享条目（N-1 / N-4 / N-5 / SD-01 等）⇒ [`_shared.md`](./_shared.md)。
> 差异的逐条证据（Go 出处、活体对拍、失效边界）⇒ `docs/DRIFT/users/admin/api.md`。

---

## SD-2026-10-23-08@users · 黑名单 401 的**业务码**为 `1006`（Go 是 `401`）

> **2026-10-29 重核（台账对齐轮）**：
> - **别名**：`SD-2026-10-23-08@users`（域内唯一别名）。**撞号事实**：同一编号在本域被引用 **2 处** ——
>   本文件（本小节 · 全文落点）与 [`_shared.md`](./_shared.md) 的「本域逐条签字」表（**只留指针**）
>   ⇒ **指向的是同一处偏离，不是两条** ⇒ 别名只加在**全文落点**这一侧。
> - **被签字描述的行为是否仍存在**：**仍存在** ⇒ **签字有效，不失效、不从计数中剔除**。
> - **证据（两侧）**：RAG ⇒ `service/users/admin/admin-api/.../advice/GlobalExceptionHandler.java:59-70`
>   （`@ExceptionHandler(AuthenticationException.class)` ⇒ `ResponseEntity.status(401).body(new Response(e.getCode(), e.getMessage(), null))`，
>   `e.getCode()` = `ErrorCode.ERROR_TOKEN_RUNTIME` = **1006** ⇒ body = `{"code":1006,…,"data":null}`）；
>   Go ⇒ `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:38`
>   （**HTTP 401** + `{"code":401,"msg":"登录已失效,请重新登陆"}`，**无 `data`**）。
>   ⇒ **两侧 HTTP 状态码都是 401**（本条只管 body 里的业务码）⇒ 差异确实仍在。
> - **⚠️ 描述完整性**：本条原文只写了「黑名单 401 的业务码」，**未写 HTTP 状态码**；
>   本轮补一句（**追加，不改原文**）：**状态码两侧一致（401），差异只在 body 的 `code` 字段** ⇒
>   「看状态码的对拍永远发现不了本条」这一提醒仍然成立。

| 项 | 内容 |
|---|---|
| **组件** | `admin-api`（台账：[`DRIFT/users/admin/api.md`](../../../DRIFT/users/admin/api.md) DRIFT-ADMINAPI-004） |
| **Go 行为** | `{"code":401,"msg":"登录已失效,请重新登陆"}` |
| **RAG 行为** | `{"code":1006,"msg":"TOKEN已过期","data":null}` |
| **类别** | `ERROR SEMANTIC / RESPONSE ENVELOPE` |
| **签字结论** | **保留 RAG 的 `1006`** |

### ⚠️ HTTP **状态码**两侧都是 401 —— 本条只管**响应体里的业务码**

这是最容易读错的一条：两侧 `HTTP/1.1 401` 完全一致，
差异只在 JSON body 的 `code` 字段。**看状态码的对拍永远发现不了本条。**

### 证据（双侧活体对拍，2026-10-23）

`tools/difftest/diff.py` 的 `admin.getself.afterlogout` 步骤（注销后再访问）：

```
[ OK ] admin.getself.afterlogout   注销后再访问 → 401
```

⇒ Go 的黑名单 401 是 `{"code":401,…}`，RAG 是 `{"code":1006,"msg":"TOKEN已过期","data":null}`。

### 与 N-1 的关系（**纠正一条错误判定**）

本条其实**早在 N-1 的签字范围内** —— N-1 原文就写明「业务码仍是 `ERROR_TOKEN_RUNTIME(1006)`」。
2026-10-23 的 api 审计轮曾判定「N-1 从未声明业务码 `401 → 1006`，属未签字的语义改变」，
**该判定是错的**，根因是：当时 `docs/Intentional_Deviations/users/**` 下 6 个文件
**全是 0 字节空占位**，按签字档检索根本查不到 N-1。

⇒ 本条现在**单独复述**一次，为的是把「活体对拍证据」和「对拍时该怎么看」写进签字档，
避免下一个 agent 再走一遍同样的弯路。

### ⚠️ 代价（必须知情）

- 直连 admin-api 的客户端会看到与 Go **不同的业务码**。
- 任何双端对拍在这一条上**必然为红，属预期差异，不是回归**。
- `tools/difftest/diff.py` 该步骤的 assert 消息原先写「N-1 已签字」——
  措辞没错但指向不清，**本条**才是准确的引用。

### ⚠️ 不得做的事

不要为了让对拍变绿而把 RAG 的业务码改回 `401` —— 那会让 RAG 的错误码体系里
出现一个孤立的 `401`，与其余鉴权失败的码不一致。

### 关闭条件

若要求逐字复刻 Go 的 body，则需在拦截器里区分
「JWT 验签失败」与「黑名单命中」两条路径，分别写 body 与业务码
（与 N-1 的关闭条件是同一条，但**范围更大**：N-1 只管 body 形状，本条管业务码）。

---

## SD-2026-10-23-01@users · 身份解析**严格校验** JWT claim（转录自 `_cross`）

> **2026-10-29 重核（台账对齐轮）**：
> - **别名**：`SD-2026-10-23-01@users`（域内唯一别名）。**撞号事实**：同一编号在本域被引用 **2 处** ——
>   本小节（全文落点）与 [`_shared.md`](./_shared.md) 的「本域逐条签字」表（**只留指针**，且指向 `_cross/signed-deviations.md`）
>   ⇒ **同一处偏离的两个落点** ⇒ 别名只加在**全文落点**这一侧。
> - **被签字描述的行为是否仍存在**：**仍存在** ⇒ **签字有效**。
> - **证据（两侧）**：RAG ⇒ `service/users/admin/admin-api/.../service/IdentityResolver.java:100-103`
>   （`uid <= 0` ⇒ `ErrInvalidClaimException`，**claim 无效即拒**）+ `:133-136`
>   （`admin.getUid() != uid` ⇒ `ErrIdentityMismatchException`）；
>   Go ⇒ `service/user/admin/rpc/internal/logic/banuserlogic.go:36-39`
>   （`l.ctx.Value("uid")` 取不到 ⇒ `adminUid` **恒为 0**，**且不报错、不校验**）；
>   写 ctx 的 key 是 **claim 名**（`service/user/common/jwt/jwt.go:8-11` 只签 `userId`）⇒ 差异仍在。
> - **⚠️ 描述完整性（追加，不改原文）**：本条原文说「Go 恒 0 且不校验 claim」，**未写明「恒 0」的成因**；
>   本轮补上：**Go 侧 ctx 里根本没有 `uid` 这个 key**（go-zero 注入的是 claim 名，而 token 里只有 `userId`）
>   ⇒ 「Go 恒 0」不是「读到了 0」，而是「**取不到**」⇒ 二者对下游的可观察后果不同（见本文件下方的严重度下调说明）。

`admin-api` 的 `operator_id`：Go 恒为 `0` 且**不校验** claim，RAG **严格校验**、缺失即拒绝。

> 完整表（含 Go/RAG 行为、类别、关闭条件）⇒
> [`_cross/signed-deviations.md`](../../_cross/signed-deviations.md#sd-2026-10-23-01--admin-api-operatoridgo-恒-0-且不校验-claimrag-严格校验)。
>
> **⚠️ 2026-10-23 复核后严重度下调**：下游 admin-rpc **不读** `operator_id`
> （只有 `createInviteCode` 拿它去查 admin；`banUser`/`unBanUser`/`deleteUser`/
> `resetUserPassword`/`updateUser` 只当审计字段）⇒ 真正的差异只剩
> 「**claim 无效时 Go 照常发 RPC、RAG 不发**」这一条。
> 取值那一半由 **N-4** 签字；**校验**这一半由本条签字。两者不是同一条。