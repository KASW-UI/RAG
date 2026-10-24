<!-- @new 2026-10-24 · comment 域 api 组件的待签字有意偏离 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# `comment` 域 api 层待签字偏离

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」。**
> ⚠️ **签字偏离是决策，不是缺陷**：审查时不重复质疑，**也不得「顺手修正回 Go 的旧行为」**。
>
> 🟡 **本文件条目状态分三种**：
> ✅ **已签字 2 条**（CAND-07 / CAND-08，2026-10-24 用户 Jason）——
> **不得「顺手修正回 Go 的旧行为」**；
> 🟡 **仍待签字 1 条**（CAND-05）；
> ⛔ **已作废 1 条**（CAND-04 —— 其前提已被活体实测推翻，见该条的「⛔ 作废结论」）。

---

## SD-2026-10-24-CAND-04：非 `application/json` 的 Content-Type ⇒ **415**，Go 是 **200 + `{code:5001}`**

| 项 | 值 |
|---|---|
| 状态 | ⛔ **已作废**（2026-10-24，用户 Jason 的签字轮一并裁定）—— **前提不成立，差异不存在** |
| 组件 | `service/comment/comment-api` |
| 行为层 | **HTTP 状态码**（客户端可见） |
| 已有自登记 | `CommentGlobalExceptionHandler.java:208-212` 的 javadoc **已自登记**为 OBSERVABLE DIFFERENCE |

### ⛔ 作废结论（2026-10-24）

**本条赖以成立的「RAG ⇒ 415」是错的** —— 双侧活体实测（6 组 Content-Type × body 形态）显示：

| 侧 | 实测 | 出处 |
|---|---|---|
| **Go** | **200 + `{code:5001, msg:"系统内部错误", data:null}`** | `httpx/requests.go:153-154` `withJsonBody` 假 ⇒ `UnmarshalJsonMap(nil, v)` ⇒ 必填缺失 |
| **RAG** | **200 + `{code:5001, msg:"系统内部错误", data:null}`** —— **与 Go 逐字节相同** | 实际落在 `HttpMessageNotReadableException("Required request body is missing")` ⇒ `CommentGlobalExceptionHandler.handleUnreadableBody`，**不是** `handleMediaTypeNotSupported` |

⇒ **两侧一致 ⇒ 不是差异 ⇒ 本条作废**，对应的 DRIFT 条目 **D1 也已撤销**（见 [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) §3.3）。

> ⚠️ **连带订正**：`CommentGlobalExceptionHandler.java` 里那句「Java 侧由 Spring 的消息转换器选择直接判 415」
> **已改写**为实测结论 —— 原断言本身是错的（AGENTS.md §3.2「先改/删错误断言」）。
> ⇒ 教训见 [`../../Pitfalls/comment/api.md`](../../Pitfalls/comment/api.md) 的 **PIT-COMMENT-API-03**。

### （存档）原候选理由 —— 已不适用

1. ~~**Go 的行为是「框架没做 Content-Type 检查」的副产物**~~ ⇒ 实测 Go 就是 200 + 5001，**不是**什么副产物。
2. ~~**改成 200 + 5001 需要显式吞掉异常**~~ ⇒ **无需任何改动**，RAG 侧本来就是 200 + 5001。
3. ~~**影响面窄**~~ ⇒ 该场景**根本无差异**。

**签字人：Jason　签字日期：2026-10-24（作废裁定）**

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

## SD-2026-10-24-CAND-07：**HS384 / HS512** 签名的 token ⇒ RAG **拒绝**，Go **接受**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，用户 Jason：**接受签字**）—— **不是缺陷** |
| 组件 | `service/comment/comment-api` |
| 行为层 | **鉴权入口**（HTTP 401 vs 通过） |
| 证据强度 | ✅ **双侧活体探针对拍**（11 例 token，字节完全相同）—— 见下方「证据」 |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `golang-jwt/jwt/v4@v4.5.2/hmac.go:49-74` `SigningMethodHMAC.Verify` **不限制 alg**（只要是 `*SigningMethodHMAC` 就用同一套逻辑验签）；go-zero 的 `rest/token/tokenparser.go:127-129` `newParser()` 用 `jwt.NewParser(jwt.WithJSONNumber())`，**没有** `WithValidMethods` ⇒ **HS384 / HS512 一律接受** | |
| **RAG** | jjwt 0.12.6 的 `impl/DefaultJwtParser.java:320-365` 把 **key 绑死在一个算法上**：`Keys.hmacShaKeyFor(key)` 对 32 字节密钥 ⇒ 产出 `HmacSHA256` ⇒ 请求头写 `HS384`/`HS512` 时抛 **`WeakKeyException`** ⇒ **拒绝** | |

### 证据（双侧探针，**11 个字节完全相同的 token**）

探针 Go 侧走 go-zero 的 `rest/token.TokenParser`、Java 侧走本仓 `CommentJwtAuthInterceptor` 的**真实代码路径**：

| token 形态 | Go | RAG | 判定 |
|---|---|---|---|
| HS256 / 缺 `exp` / `iat` 在未来 / HS384 / HS512 / 字符串 userId / 小数 userId … | 见 `docs/DRIFT/comment/api.md` §3.1 的矩阵 | | **11 例逐条对拍** |

⇒ **只有 HS384 / HS512 这一类不一致**，其余 9 例两侧一致。

### 为什么倾向签字（而不是放宽 RAG）

1. **放宽是降低安全性**：要接受 HS384/HS512，就得让 jjwt 接受任意 HMAC alg ⇒ 需要显式配置 `SignatureAlgorithm` 白名单或改用 `SecretKey` 的多算法派生 —— **扩大了攻击面**。
2. **实际部署只用 HS256**：两侧的密钥（`etc/commentservice.yaml:6` 与 `application.yml:64`）与签发方都只用 HS256 ⇒ **正常客户端零影响**。
3. **Go 侧接受是「没限制」的副产物**，不是刻意设计（`WithValidMethods` 是 jwt/v4 提供的、go-zero 没启用）。
4. ⚠️ **本条是「RAG 更严一侧」** ⇒ 与 `SD-2026-10-23-01/02/03` 的共性一致（RAG 做了更严/更安全的那一侧）。

### ⚠️ 需要一并决策的点

- **本条签字的含义**：**接受 RAG 比 Go 严** ⇒ **保持现状**（jjwt 继续拒绝 HS384/HS512）⇒ **不改任何代码**。
- 任何用 HS384/HS512 签 token 的上游都要改（当前**没有**这种上游；两侧签发方都只用 HS256）。
- ⚠️ **若将来要放宽**（改回 Go 的接受矩阵）：需要放开 jjwt 的算法白名单到 HS256/384/512 ⇒ **必须配套一条测试**锁住「不再抛 `WeakKeyException`」，**并同步改** `CommentJwtTokenMatrixGateTest` 里那两条断言（它们当前断言的正是「RAG 拒绝」）—— **签字本身不触发这一步**。

### ✅ 签字结论（2026-10-24，用户 Jason：**接受签字**）

- **决定**：**接受本偏离** —— RAG 的 JWT 解析**比 Go 严**（HS384/HS512 一律拒绝），**不改回 Go 的宽松矩阵**。
- **理由**：放宽是**降低安全性**；且两侧的实际签发方都只用 HS256 ⇒ **零实际影响**。
- **连带动作**：**代码零改动** —— `CommentJwtTokenMatrixGateTest`（11 例）**继续断言「RAG 拒绝」**，**该测试即为本条的行为锁**。
- **后续影响**：对拍看到 HS384/HS512 上「Go 通过 / RAG 401」⇒ **不得当成新回归去「修」回 Go 侧行为**。

---

## SD-2026-10-24-CAND-08：**保护层的覆盖范围** —— 未注册路径上 Go **从不**返回 413，RAG 返回 **413**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，用户 Jason：**接受签字**）—— **不是缺陷** |
| 组件 | `service/comment/comment-api`（**实现位于 `service/common/protection`**，六域共用） |
| 行为层 | **HTTP 状态码**（客户端可见）—— 仅限「**未注册路径**」 |
| 证据强度 | ✅ **双侧活体对拍**（对拍矩阵 **T20**，两轮独立复跑一致） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `rest/engine.go:105` —— `chn = ng.buildChainWithNativeMiddlewares(fr, route, metrics)` **写在 `bindRoute` 内部** ⇒ **只有被注册的路由**才拿到 chain；`rest/engine.go:163-165` 把 `MaxBytesHandler` 挂到**该路由的 chain** 上 ⇒ **未注册路径根本不经 MaxBytes** ⇒ 服务端**从不返回 413**（客户端表现为 `Broken pipe` / `Connection reset by peer`）。<br>`rest/config.go:21` `MaxBytes bool json:",default=true"` ⇒ 默认开；`:53` `MaxBytes int64 json:",default=1048576"` ⇒ 默认 **1 MiB**；`etc/commentservice.yaml` 未覆盖 ⇒ 生效值 = 1 MiB | |
| **RAG** | `service/common/protection/GoStyleMaxBytesFilter.java:82-84` —— `@Component` + `@Order(HIGHEST_PRECEDENCE + 6)` + `implements Filter` ⇒ **全局 servlet Filter**；`:112` `response.setStatus(SC_REQUEST_ENTITY_TOO_LARGE)` ⇒ **413 空 body** | |

### 🟢 活体证据（对拍 T20，两轮一致）

向**未注册路径** `/comment/v1/nope` 发 **1.5 MB** body：

| 侧 | 结果 |
|---|---|
| **Go** | **transport error**（`Broken pipe` / `Connection reset by peer`）⇒ **服务端从未返回状态码** |
| **RAG** | **HTTP 413**（空 body） |

⇒ **差异真实存在**（DRIFT 的 **D6**）。

### 为什么这是「更严 / 更安全」而不是「更差」

1. **Go 侧在未注册路径上不限 body 大小** ⇒ **无界读** ⇒ 服务端资源耗尽面更大；
2. RAG 的 **413 + 空 body** 是 HTTP 语义上正确的答案，且与 Go 在**已注册路径**上的行为**一致**（两侧都 413）；
3. **影响面极窄**：**仅限「连路由都没匹配上」的请求**（404 / 405 场景）—— 已注册路径、错误方法（T19）两侧都一致。

### ✅ 签字结论（2026-10-24，用户 Jason：**接受签字**）

- **决定**：**接受本偏离** —— **不改** `service/common` 的 4 个保护 Filter（**它们是六域共用 + `align/common` 并发分支**，本域不可动）。
- **含义**：RAG 在**未注册路径**上比 Go **更严**（返回 413 而非无界读）⇒ **保留全局 Filter**。
- **连带动作**：**代码零改动**；DRIFT 的 **D6 关闭**。
- **后续影响**：对拍看到「Go=transport error / RAG=413」⇒ **不得当成新回归去「修」回 Go 侧行为**。
- ⚠️ **给 `align/common` 的交接**：若将来要在共享面调整保护层覆盖范围，**必须**同时评估**六个域**的未注册路径行为 ⇒ 本条已签字 ⇒ 改动需重新征签。

**签字人：Jason　签字日期：2026-10-24**

---

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | 需要对齐的差异在本域的落点 |
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | `comment-rpc` 的台账 |
| [`../comment/proto.md`](./proto.md) | `SD-2026-10-23-04`（已签字） |
