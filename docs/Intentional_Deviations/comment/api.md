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

> 📌 **2026-10-29 对齐复核（台账对齐 phase · W-D lane）—— 本文件 4 条逐条重核完毕**
>
> 判据 = [`../_INDEX.md`](../_INDEX.md)「被签字描述的那个行为**已不存在** ⇒ 签字自动失效」。
> **4/4 条的状态全部不变**（CAND-04 仍作废 · CAND-05 仍待签字 · CAND-07 / CAND-08 仍已签字）⇒ **无一条需标「已失效」**。
> **但有 2 处订正**（原句**一律保留**，新结论追加在后面）：
> ① **CAND-08**：原条写 `GoStyleMaxBytesFilter` 的 `@Order` 是 `HIGHEST_PRECEDENCE + 6` ⇒ **实测是 `+ 8`**（`+6` 是同包的 `GoStyleCpuSheddingFilter`）；
> ② **CAND-05**：原条写的实现路径需订正为**实际文件路径**（`service/users/users-common/.../BearerTokenExtractor.java:75`）。
>
> ⇒ 每条下方的「📌 2026-10-29 对齐复核」小节给出逐条结论 + 证据指针 + **唯一别名**。

---

## SD-2026-10-24-CAND-04：非 `application/json` 的 Content-Type ⇒ **415**，Go 是 **200 + `{code:5001}`** ｜ **状态（核对日期 2026-10-29）：⛔ 已失效·已撤销**

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

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-CAND-04@comment` |
| **撞号检查** | 全仓 `grep -rln SD-2026-10-24-CAND-04 docs/` ⇒ **定义只此一处**（`docs/DRIFT/comment/api.md` 为**引用**）⇒ **无撞号** |
| **撤销的那个行为现在是不是真的没了** | ✅ **是** —— 「RAG ⇒ 415」这个差异**至今不存在** ⇒ **本条的作废裁定仍成立，状态不变**（⛔ 已作废 ⇒ 按索引口径**不计入计数**） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测** | `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/advice/CommentGlobalExceptionHandler.java:200-212`（javadoc 已写明「**2026-10-24 双侧活体实测后订正** … **非 `application/json` 的 Content-Type ⇒ 两侧都是 HTTP 200 + `{code:5001}` ⇒ 一致**」）· `:170-171`（`@ExceptionHandler(HttpMessageNotReadableException.class)` ⇒ `handleUnreadableBody` ⇒ 200 + 5001）· `:223-225`（`handleMediaTypeNotSupported` ⇒ 415 **仅在真的抛 `HttpMediaTypeNotSupportedException` 时**才走，而实测路径**不是**它）⇒ ⇒ **差异不存在，作废成立** |
| **Go 侧实测** | `service/comment/rpc/internal/httpx/requests.go:153-154` 的 `withJsonBody` 假 ⇒ `UnmarshalJsonMap(nil, v)` ⇒ 必填缺失 ⇒ 200 + 5001 ⇒ **两侧仍逐字节相同** |

---

## SD-2026-10-24-CAND-05：Bearer 前缀剥离的长度边界 `> 7` vs Go 的 `> 6` ｜ **状态（核对日期 2026-10-29）：⏳ 未签字**

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

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-CAND-05@comment` |
| **撞号检查** | 全仓 `grep -rln SD-2026-10-24-CAND-05 docs/` ⇒ **只命中本文件** ⇒ **无撞号** |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ 本条**状态不变**（🟡 待签字 / 候选；也可直接改回，**行为等价**） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测** | `service/users/users-common/src/main/java/com/rag/users/common/jwt/BearerTokenExtractor.java:75`（`if (authHeader.length() > BEARER_PREFIX.length() && …` ⇒ `BEARER_PREFIX = "Bearer "`（`:35`，长度 **7**）⇒ 判据是 **`length() > 7`**）⇒ ⇒ **长度恰为 7 的头（`"Bearer "`）保留整串** ⇒ 解析失败 ⇒ **401** ⇒ **与 Go 的 `> 6` 仍有差异，且结局相同（都 401）** ⇒ **仍不可观察** |
| **Go 侧实测** | golang-jwt/jwt/v4 `request/oauth2.go:8-14`：`len(tok) > 6 && strings.ToUpper(tok[0:7]) == "BEARER "` ⇒ **长度 7 的头被剥成空串** ⇒ 解析失败 ⇒ **401** |
| **🔴 订正（实现路径 · 原句保留）** | 原条写「`users-common/.../BearerTokenExtractor.java:74-81`」⇒ **实测文件路径为** `service/users/users-common/src/main/java/com/rag/users/common/jwt/BearerTokenExtractor.java`，**判据所在行 = `:75`**（`:74-81` 的范围仍覆盖该行 ⇒ 不冲突）⇒ ⇒ **只订正完整路径，结论与措辞不变** |
| **⚠️ 写权提示（本轮不改）** | 该类位于**共享层** `service/users/users-common` ⇒ **写权归 G4**（AGENTS.md §5.3f）⇒ 本 lane **只登记、不落码** |

---

## SD-2026-10-24-CAND-07：**HS384 / HS512** 签名的 token ⇒ RAG **拒绝**，Go **接受** ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

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

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-CAND-07@comment` |
| **撞号检查** | 全仓 `grep -rln SD-2026-10-24-CAND-07 docs/` ⇒ **定义只此一处**（`docs/DRIFT/comment/api.md` 为**引用**）⇒ **无撞号** |
| ⚠️ **2026-10-29 真实性复核（标题结构修复 · 主线）** | 本条的 **`##` 标题在上一 phase（`765e3eeb`「台账对齐轮」）被并进 CAND-05 最后一行表格的末格** ⇒ 末格原为「…⇒ 本 lane **只登记、不落码** \|：**HS384 / HS512** 签名的 token…」⇒ ⇒ **机器计数器（`ledger-deviation-count.py` 口径 = 标题以编号开头）完全看不到本条** ⇒ **签字数少算 1 条**。<br>🟢 **修复方式 = 精确还原，不改内容**：① 把末格截回 CAND-05 的「写权提示」格（**原文字一字未改**）；② 还原原标题 —— 取自 git 历史 `d0fa35cf:docs/Intentional_Deviations/comment/api.md:79` 的**逐字原文** `## SD-2026-10-24-CAND-07：**HS384 / HS512** 签名的 token ⇒ RAG **拒绝**，Go **接受**`；③ 追加规范状态字段 `｜ **状态（核对日期 2026-10-29）：✅ 仍成立**`（取值来自本块「被描述的行为现在还存在吗 = ✅ 仍存在 ⇒ 签字有效」）。<br>⇒ **本条正文内容零改动**（表格、证据、签字结论、复核结论全部原样）⇒ **只修「机器可寻址性」**。核对日期 2026-10-29。 |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ **签字有效，状态不变**（✅ 已签字） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测** | `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/auth/CommentJwtSignatureVerifier.java:86-87`（`this.key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(…));` ⇒ 32 字节 ⇒ 产出 **`HmacSHA256`**）· `:116-119`（`Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` ⇒ **未设算法白名单** ⇒ 请求头写 `HS384`/`HS512` ⇒ **jjwt 抛 `WeakKeyException` ⇒ 拒绝**）⇒ ⇒ **「RAG 拒绝 / Go 接受」的差异仍在** |
| **Go 侧实测** | go-zero 的 `rest/token/tokenparser.go` 用 `jwt.NewParser(jwt.WithJSONNumber())`、**无** `WithValidMethods` ⇒ golang-jwt/v4 的 `SigningMethodHMAC.Verify` **不限制 alg** ⇒ **HS384/HS512 一律接受** |
| **连带动作核验** | 原条称「`CommentJwtTokenMatrixGateTest`（11 例）**继续断言「RAG 拒绝」**，**该测试即为本条的行为锁**」⇒ **2026-10-29 实测该测试类仍在**：`service/comment/comment-api/src/test/java/com/rag/comment/comment/api/interceptor/CommentJwtTokenMatrixGateTest.java`（`:42,54-72` 的矩阵注释仍把 HS384/HS512 标为「🔴 差异」）⇒ ⇒ **行为锁仍在 ⇒ 签字含义仍成立** |

## SD-2026-10-24-CAND-08：**保护层的覆盖范围** —— 未注册路径上 Go **从不**返回 413，RAG 返回 **413** ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

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

### 📌 2026-10-29 对齐复核（台账对齐 phase · W-D lane）

| 项 | 结论 |
|---|---|
| **唯一别名** | `SD-2026-10-24-CAND-08@comment` |
| **撞号检查** | 全仓 `grep -rln SD-2026-10-24-CAND-08 docs/` ⇒ **定义只此一处**（`docs/DRIFT/comment/api.md` · `docs/DRIFT/comment/rpc.md` · `docs/DRIFT/comment/vs-common.md` · `docs/DRIFT/_cross/domain-common-lanes.md` 均为**引用**）⇒ **无撞号** |
| **被描述的行为现在还存在吗** | ✅ **仍存在** ⇒ **签字有效，状态不变**（✅ 已签字） |
| **核对日期** | 2026-10-29 |
| **RAG 侧实测（差异仍在）** | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:82`（`@Component`）· `:83`（`@Order(Ordered.HIGHEST_PRECEDENCE + 8)`）· `:84`（`implements Filter`）⇒ **仍是全局 servlet Filter** ⇒ **未注册路径也会被它拦**；`:112`（`response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)` ⇒ **裸 413**）⇒ ⇒ **「未注册路径上 RAG 返回 413 / Go 从不返回 413」的差异仍在** |
| **🔴 订正（`@Order` 数值 · 原句保留）** | 原条写「`@Order(HIGHEST_PRECEDENCE + 6)`」⇒ **2026-10-29 实测是 `+ 8`**；**`+ 6` 是同包的 `GoStyleCpuSheddingFilter`**（`service/common/src/main/java/com/rag/common/protection/GoStyleCpuSheddingFilter.java:63`）⇒ ⇒ **数值订正为 `+ 8`**。⚠️ **但结论不变**：该类 javadoc（`GoStyleMaxBytesFilter.java:55-79`）已自记「**「在鉴权之外」靠的是它是 Filter，不是 `Order` 的数值**」—— Servlet 的**所有** Filter 都包在 `DispatcherServlet` **外面**，而 JWT 鉴权是 `HandlerInterceptor`（在 `DispatcherServlet` **内部**）⇒ ⇒ **Filter 与 Interceptor 的先后由容器结构决定，与 order 数值无关** ⇒ **「Go 200 / RAG 413」这条签字含义仍成立** |
| **连带动作核验** | 原条称「**代码零改动**；DRIFT 的 **D6 关闭**」⇒ **2026-10-29 实测**：`docs/DRIFT/comment/api.md:142`（D6 行 = ✅ **已签字偏离**）· `:175`（§3.2 标题同）⇒ **D6 已关闭且仍以签字态登记** ⇒ 签字含义仍成立 |

---

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | 需要对齐的差异在本域的落点 |
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | `comment-rpc` 的台账 |
| [`../comment/proto.md`](./proto.md) | `SD-2026-10-23-04`（已签字） |
