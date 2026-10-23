<!-- @new 2026-10-24 · comment 域 api 组件的待签字有意偏离 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# `comment` 域 api 层待签字偏离

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」。**
> ⚠️ **签字偏离是决策，不是缺陷**：审查时不重复质疑，**也不得「顺手修正回 Go 的旧行为」**。
>
> 🟡 本文件全部条目状态为「**待签字**」。

---

## SD-2026-10-24-CAND-04：非 `application/json` 的 Content-Type ⇒ **415**，Go 是 **200 + `{code:5001}`**

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（候选，建议签） |
| 组件 | `service/comment/comment-api` |
| 行为层 | **HTTP 状态码**（客户端可见） |
| 已有自登记 | `CommentGlobalExceptionHandler.java:208-212` 的 javadoc **已自登记**为 OBSERVABLE DIFFERENCE |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `httpx/requests.go:153-154` `withJsonBody` 假 ⇒ `UnmarshalJsonMap(nil, v)` ⇒ **必填字段缺失** ⇒ 走校验失败分支 ⇒ **HTTP 200 + `{code:5001, msg:"系统内部错误"}`** | |
| **RAG** | Spring MVC 抛 `HttpMediaTypeNotSupportedException` ⇒ `CommentGlobalExceptionHandler.java:208-212` ⇒ **HTTP 415** | |

### 为什么倾向签字（而不是改回 Go）

1. **Go 的行为是「框架没做 Content-Type 检查」的副产物**，不是刻意设计 —— 415 是 HTTP 语义上正确的答案。
2. **改成 200 + 5001 需要显式吞掉 `HttpMediaTypeNotSupportedException`**，那是在**主动制造一个误导性的成功响应**（客户端会以为「服务端处理了，只是业务失败」）。
3. 影响面窄：正常客户端都发 `application/json`；**只在「Content-Type 写错」这一个场景下**状态码不同。

### ⚠️ 需要一并决策的点

- 一旦签字，**任何按 HTTP 状态码分流**的客户端都要知道「415 是本域的正常契约之一」。
- 若不签，则必须显式把 415 转成 200 + 5001 ⇒ **必须配套一条测试**，否则会被 Spring 的默认行为改回去。

**签字人：______　签字日期：______**

---

## SD-2026-10-24-CAND-05：Bearer 前缀剥离的长度边界 `> 7` vs Go 的 `> 6`

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（候选；也可以直接改回，**行为等价**） |
| 组件 | `service/comment/comment-api`（实现在 `service/common` 的 `BearerTokenExtractor`，**改动影响多域**） |
| 行为层 | **当前完全不可观察**（结局相同） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `golang-jwt/jwt/v4/request/oauth2.go:8-14`：`len(tok) > 6 && strings.ToUpper(tok[0:7]) == "BEARER "` ⇒ 长度恰为 7 的头（`"Bearer "`）**会被剥成空串** ⇒ 后续解析失败 ⇒ **401** | |
| **RAG** | `users-common/.../BearerTokenExtractor.java:74-81`：判据是 `length() > 7` ⇒ 长度恰为 7 的头**保留整串** ⇒ 解析失败 ⇒ **401** | |

⇒ **结局相同**（都是 401），只有错误文本不同 ⇒ **当前不可观察**。

### 处置建议

- **首选**：直接改成 `> 6` 与 Go 对齐（消除隐患，且**不改变任何可观察行为**）。
  ⚠️ 该类在 `service/common` 下，**改动影响多个域** ⇒ 不属本域 scope，需主会话决策。
- **次选**：签字为「表述差异，结局一致」。

**签字人：______　签字日期：______**

---

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | 需要对齐的差异在本域的落点 |
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | `comment-rpc` 的台账 |
| [`../comment/proto.md`](./proto.md) | `SD-2026-10-23-04`（已签字） |
