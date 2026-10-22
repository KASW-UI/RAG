<!-- @move AGENTS.md L484-L590 · §0 like-api 2 条 HIGH 漂移 -->
### 🟡 `service/like/like-api`：已落地，独立子会话验证判定 **NOT EQUIVALENT**，2 条 HIGH 漂移（2026-10-22）

> 本节是**独立 verifier 子会话**（Go → Spring Boot 语义一致性验证，方向固定为 Go 重建基准）对
> 刚建成的 `like-api` 的裁定结果。**该模块的 44 条绿测试不能推翻此判定** —— 原因见末尾元教训。

**模块现状**：Go `service/like/api`（6 个 POST `/like/v1/*` 的 HTTP→gRPC 网关，零业务逻辑）
→ Java `service/like/like-api` 落地，主源 2566 行 / 测试 866 行，**44 tests 全绿**
（29 条真实 MVC 管线 + 15 条 DTO 校验）。`like-common`（LikeErrorCode/Messages/GrpcError）
由用户先行完成，不在本次范围内。

#### 🔴 两条 HIGH 漂移：**同一个错误动作，做了两次，方向相反**

| ID | 项 | Go | Spring Boot | 分类 |
|---|---|---|---|---|
| **DRIFT-LIKEAPI-001** | HTTP 请求超时 | **3s → 503 + `"Request Timeout"`** | **无超时** | SEMANTIC MISMATCH |
| **DRIFT-LIKEAPI-002** | `target_ids` 必填性 | **必填**（缺失/显式 null 均解析失败） | **非必填**（放行并真的发出 RPC） | SEMANTIC MISMATCH |

**根因是同一个动作：只读 yaml / struct tag，不读消费它们的那段控制流。**

**① `rest/config.go:55` 是 `Timeout int64 json:",default=3000"`** ——
`likecenter.yaml` 从头到尾**没写** `Timeout`，但 `conf.MustLoad` 会套用 struct tag 的默认值，
所以 `Timeout == 3000`，**不是 0**。于是 `DisableNativeRestTimeout` 返回 3s，
`observability.NewHTTPMiddleware` 真实生效 → 超时 `http.Error(w,"Request Timeout",503)`。
⇒ 原 `application.yml` 写的「yaml 没写 ⇒ `TimeoutFromMillis(0)==0` ⇒ 无超时」**字面自洽但完全错误**。
⚠️ 三条本可拦住它的旁证当时全部存在且都没被用上：
`com.rag.common.spring.ObservabilityProperties.java:45` 已写明「即 Go 实际跑 HTTP 3s；
**各模块 yaml 须显式写出同值，不得留空**」；`message-api:173` / `user-api:143` / `admin-api:132`
三个兄弟模块**都配了** `http-timeout: 3s`；`like-api` 是**全仓唯一不配的那个**。

**② `core/mapping/unmarshaler.go:944-950` 的 Slice 分支并非「填空值放行」** ——
`processNamedFieldWithoutValue` 对 `Array/Map/Slice` 且非 optional 时，
调的是 `processFieldNotFromString(..., value: emptyMap, ...)`，它落到 `default:` →
`processFieldPrimitive`，此时 `typeKind(Slice) != valueKind(Map)` → **`newTypeMismatchError`**。
⇒ 探针实测：缺 `target_ids` → `type mismatch for field "target_ids"`；`target_ids: null` →
`field "target_ids" mustn't be nil`。**两者都解析失败。**
连带后果：Go like-rpc 的 2001「输入需要查询的ID为空」（`getlikecountlogic.go:44`）
在 Go 上**永远不可达**（解析期已拦），而 Java 侧可达 ⇒ **两端 rpc 看到的入参集合不同**。

> ⚠️ **这两条的共同形状与 DRIFT-MSGAPI-001/002 完全同款**：
> 「不配置就是没有」是错的 —— 实际是「不配置就用代码里写死的那条」。
> 只不过这次一条**该有却没有**（超时），一条**该拒却放行**（必填），
> 粗看两边都能跑通，且**方向相反**，所以比单方向漂移更难发现。

#### 🟡 其余已登记项

| ID | 项 | 分类 | 状态 |
|---|---|---|---|
| ~~DRIFT-LIKEAPI-003~~ | ~~请求保护层四层全缺~~ | — | ✅ **2026-10-22 已关**：保护层下沉到 `com.rag.common.protection`，本模块 `scanBasePackages` 本就含 `com.rag.common` ⇒ **自动生效、零装配代码**。见下方「请求保护层下沉」章节 + `LikeApiProtectionMvcTest` |
| ~~DRIFT-LIKEAPI-004~~ | ~~超限请求的优先级（413 而非 401）~~ | — | ✅ **2026-10-22 已关**（同上）。⚠️ 但归因被变异测试**推翻过一轮**，见下方更正 |
| ~~DRIFT-LIKEAPI-005~~ | ~~gRPC client 熔断缺失~~ | — | ✅ **2026-10-22 已关**：共享拦截器自动接入；且 like 侧**无需任何 Java 改动**（`LikeApiSupport#rpcError` 的 `ok` 二分已把非 status 错误落到 5001，与 Go `ok=false` 分支一致） |
| **DRIFT-LIKEAPI-010** | `LikeApiSupport#userId` 的 javadoc **说反了** | 文档致高危 | **代码是对的，注释是错的** |
| ~~DRIFT-LIKEAPI-006~~ | ~~非 JSON Content-Type：Java 415 vs Go 丢弃 body 后按空对象继续~~ | — | ✅ **2026-10-22 已关**（含 message 域同型）。⚠️ 原签字理由「两侧都是拒绝」**已被推翻**（零必填 DTO 在 Go 侧是成功的） |
| ~~DRIFT-LIKEAPI-007 / -008~~ | ~~404 空 body；405 缺 `Allow` 头~~ | — | ✅ **2026-10-22 已关**。⚠️ 修 404 时**挖出比它更大的一条**：`/like/v1/**` 下的未知路径**根本没有 404**，被鉴权遮蔽成 **401** ⇒ 见 DRIFT-LIKEAPI-011 |
| **DRIFT-LIKEAPI-011** 🆕 | **未知路径被鉴权遮蔽**：`/like/v1/nope` 无 token → RAG **401**，Go **404** | SEMANTIC | ✅ **2026-10-22 已修**（`preHandle` 加 `handler instanceof HandlerMethod` 守卫）。message-api 同型一并修 |
| ~~DRIFT-LIKEAPI-009~~ | ~~`author_id = -2^63` 被误拒~~ | — | ❌ **复核为误报，已撤销** |

> ⚠️ **DRIFT-LIKEAPI-009 是误报，撤销记录（2026-10-22，修复过程中实测发现）**
>
> 该条声称 `BigInteger.bitLength()` 不含符号位 ⇒ `Long.MIN_VALUE` 的 bitLength 是 64 ⇒
> 被 `>= 64` 的越界判据误拒。**前提是错的**：
> <pre>
 *   实测（java.math.BigInteger）：
 *     Long.MIN_VALUE (-2^63)        bitLength = 63   ← 不是 64
 *     Long.MAX_VALUE ( 2^63-1)     bitLength = 63
 *     2^63                          bitLength = 64
 *     -2^63-1                       bitLength = 64
 * </pre>
> ⇒ 「{@code bitLength() >= 64} ⟺ 越出 int64 值域」是**成立**的，
> 原实现 `GoStyleStringInt64` 与 `LikeApiSupport#userId` 的判据本就正确，**不存在漂移**。
> 曾按该误报改过一次代码（换成 `compareTo` 值域比较），因行为完全等价而**已回滚**，
> 只把上面这条不变量写进注释，并用 `LikeReqGoStyleValidationTest#int64BoundaryHandlingIsCorrect` 钉死。
> **教训**：复核报告里的「实测数字」也要复核。这次是**修复方**差点被误报牵着走 ——
> 若当初没写那条断言 `bitLength() == 63`，就会把一个不存在的缺陷「修」成一次无意义改动，
> 并在 AGENTS.md 里留下一条假记录。

#### 🟥 元教训：**带「Go 出处」引用的绿测试，可能在为反向漂移背书**

DRIFT-002 被发现时，**两条绿测试正锁着错误答案**，且 `@DisplayName` 里**直接引用 Go 作为理由**：

```
LikeApiMvcPipelineTest#missingSliceIsNotAParseFailure
   @DisplayName("⚠️ target_ids 缺席**不算**缺字段（Go: Slice 永不计必填）")
LikeReqGoStyleValidationTest#sliceTargetIdsIsNeverRequired
   @DisplayName("⚠️ target_ids 缺席**不**算缺字段（Go: Slice 永不计必填）")
```

这比 AGENTS.md §3.3 已记录的「绿测试守卫错误答案」**更进一步**：
§3.3 说的是「测试断言 RAG 的现状却自称对齐 Go」，本次是**测试名里引用了一个被独立复核推翻的 Go 结论**。
⇒ 危害在于**修复会先撞上这两条红测试**，且测试名会让人以为「修复」才是漂移。
⇒ **新增硬纪律：任何以「Go: …」形式写进 `@DisplayName` / javadoc 的断言，
其 Go 依据必须带 `文件:行号`，且该行号要指向「消费该 tag 的控制流」而不是「tag 本身」。**

#### ✅ 经复核**站得住**的部分（不要在后续轮次里重复怀疑）

鉴权 401 + **零字节 body**；身份失败 **1006**（不是 401、不是 5001）；6 个端点的
**鉴权不对称**（`getlikecount` / `gettargetlikerlist` 不读 uid）；
`status.FromError` 的 `ok` 标志二分（status → 业务码 + `st.Message()` **原文**；
非 status → **5001**，**不是 1015**）；`options=1|2|3|4`；`,string` 的 `author_id` **字符串与数字都收**；
`success` **硬编码 true**（Go 不读 proto 的 `success`）；`weight` float64→float32 窄化；
`counts` / `list` **恒为 `{}` / `[]` 而非 null**；`is_end` 不会多吐一个 `end` 键。
⇒ 这些由 verifier 用**活体 Go 探针**（56 例解析矩阵 + 配置默认值 + envelope 字节）对拍确认，非仅源码推断。

#### ⚠️ `like-api` 的口径边界

**44 条绿测试不能作为「无漂移」的证据**：本模块证明了「绿测试 + 带 Go 出处的 `@DisplayName`」
仍可能是反向漂移的守卫。真源口径以 verifier 的 Go 侧取证（探针 + `file:line`）为准。

<!-- @move AGENTS.md L734-L827 · §0 404/405 字节级对齐 DRIFT-LIKEAPI-007/-008/-011 -->
### ✅ 404 / 405 字节级对齐（DRIFT-LIKEAPI-007 / -008），并挖出 **DRIFT-LIKEAPI-011**（2026-10-22）

> 用户「继续」。本节记录：修两条已登记的表达层漂移时，**发现其中一条的真正问题比登记的更大**。

#### Go 侧真源（`go-zero v1.10.2`，两条**形状完全相反**）

| | Evidence | 形状 |
|---|---|---|
| **404** | `router/patrouter.go:73,93-97` → `http.NotFound` → `http.NotFoundHandler()` → `http.Error(w,"404 page not found",404)`；`net/http.Error` 做四件事：`Del("Content-Length")` / `Set("Content-Type","text/plain; charset=utf-8")` / `Set("X-Content-Type-Options","nosniff")` / `WriteHeader` + `Fprintln` | **有 body**（`"404 page not found\n"`，含尾随换行，全长 20）+ 有 `Content-Type` + 有 `nosniff` |
| **405** | `router/patrouter.go:79-82`：`w.Header().Set("Allow", allows)` + `w.WriteHeader(405)` | **裸状态码**：无 body、**无** `Content-Type`，只多一个 `Allow` |

⚠️ 这是本项目遇到的**第三种响应形状**（前两种：业务错误 = 200+envelope；保护层 = 裸状态码），
**最容易被「顺手统一」** ⇒ 测试逐项钉死两种形状**相反**这一点。

⚠️ **`Allow` 的取值顺序在 Go 侧不确定**：`patrouter.go:101-117` 的 `methodsAllowed` 是
`for treeMethod, tree := range pr.trees` —— **遍历 Go map，顺序随机**。
本实现**原样透传不排序**：排序＝新增行为＝漂移。当前 like/message 的路由全是 POST ⇒ 只有一个值，不可观测。
**一旦有模块给同一路径注册第二个方法，必须先决定这件事怎么对齐。**

#### 🔴 DRIFT-LIKEAPI-011：修 404 时发现**根本没有 404**

真 Tomcat 探针（`RANDOM_PORT` + JDK HttpClient 读原始响应）：

```
/like/v1/nope        -> 401 | ct=<none>     | body=[]                    ← 修复前
/nope                -> 404 | ct=text/plain;charset=utf-8 | body=[404 page not found\n]
/actuator/nope       -> 404 | ct=text/plain;charset=utf-8 | body=[404 page not found\n]
/like/v1/getlikecount-> 401 | ct=<none>     | body=[]
```

**根因**：Go 的 `engine.go:104-118` `bindRoute` 只把 chain（native + auth + 用户中间件）
挂到 `router.Handle(method, path, handle)` 上 ⇒ **chain 只对匹配上的路由生效**；
`patrouter.go:73,79` 的 404/405 分支**根本进不了这条链** ⇒ Go 侧未知路径恒 404，与 token 无关。

Spring 侧**默认做不到**：Boot 的静态资源处理器匹配 `/**`，未注册路径**也拿到了 handler**
⇒ `mappedHandler != null` ⇒ `HandlerInterceptor` 照跑 ⇒ 404 被鉴权遮蔽成 401。

**修法**：`preHandle` 加一行守卫

```java
if (!(handler instanceof org.springframework.web.method.HandlerMethod)) { return true; }
```

`instanceof HandlerMethod` 精确对应 Go 那句「有没有匹配上 `@RequestMapping` 方法」——
`ResourceHttpRequestHandler` 不是 `HandlerMethod`。⚠️ **不要改成「路径是否以 `/v1/` 开头」之类的字符串判断**：
那是重新发明一遍路由表。`message-api` 同型一并修（`AbstractMessageJwtAuthInterceptor`）。

⚠️ **既有的 404 测试从未覆盖这条**：`MessageApiMvcPipelineTest#unknownPathIs404` **带了合法 token**，
所以它测的是「有 token 时的 404」，不是「无 token 的未知路径」。
⇒ 这条漂移能长期存在，是因为**绿测试断言的是「带 token」这一条路径**。
已补 `MessageApiRouteAuthBoundaryTest`（MockMvc，3 条）与
`LikeApiRouterErrorRealTomcatTest`（真 Tomcat，4 条）双向钉死。

#### 一处**刻意不消除**的字节差异

Go 的 `net/http` 逐字写出 `text/plain; charset=utf-8`（分号后**有空格**），
真 Tomcat 会规范化成 `text/plain;charset=utf-8`（**无空格**）。按 RFC 9110 参数分隔符两旁的 OWS 不显著
⇒ 语义相同。**不与容器对抗**，但测试必须**解析后比对**（`ctRaw.replace(" ","")`）——
直接比原始字符串会得到一个「必然红、且红了也不代表有错」的断言（已实测踩到）。
`MediaType.parse(String)` 在 Spring 7 已移除，别照抄老写法。

#### 实现落点：一处共享，两个模块

新增 `com.rag.common.http.GoStyleRouterErrorResponses`（404 / 405 两个工厂）。
理由：二者由 **go-zero 的路由层**产出，形状只取决于「路由有没有匹配上」，与该服务注册了哪些端点无关；
复制到每个 api 模块，早晚出现「message 的 404 有 body、like 的没有」这类静默分叉。

**测试分工**（避免重复又不留缺口）：

- **字节级**（body / Content-Type / nosniff / Content-Length）⇒ `LikeApiRouterErrorRealTomcatTest`
  **真 Tomcat**，因为 MockMvc 不驱动容器选 converter、不参与 `Content-Length`、也测不到「容器会不会补 body」——
  这正是 DRIFT-024 当初漏网的原因。**共享实现只需守一次。**
- **每模块的拦截器守卫** ⇒ 各守一次（`LikeApiRouterErrorRealTomcatTest#routerErrorsBypassAuth`
  + `MessageApiRouteAuthBoundaryTest`），因为守卫代码是**每个模块各写一遍**的。

#### 变异实证（每次变异与还原都 `mvn clean`，且确认变异进了产物）

| 变异 | 结果 |
|---|---|
| 摘掉 `preHandle` 的 `HandlerMethod` 守卫 | **3 条转红**，`expected: <404> but was: <401>` |
| 把共享实现的 404 body 改成 `"MUT"` | **2 条转红**，body 与「以换行结尾」两条同时抓到 |

#### 实测口径

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **134** | 不变（共享实现在此模块，字节级守门在 like 侧） |
| `service/like/like-api` | **55** | 51 + 4（`LikeApiRouterErrorRealTomcatTest`） |
| `service/message/message-api` | **73** | 70 + 3（`MessageApiRouteAuthBoundaryTest`） |

⚠️ 真 Tomcat 测试必须多配一条 `"spring.cloud.nacos.discovery.enabled=false"`：
`RANDOM_PORT` 会真的把 webServer 启起来，naming client 随即注册并在 `STARTING` 态报
「Client not connected」而让 `webServerStartStop` 启动失败（实测）。
**MOCK 环境不启容器所以不需要它 ⇒ 两种环境的 property 集合不可互相照抄。**
