<!-- @new 2026-10-23 23:0x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-api` 签字偏离（域内索引）

> **本文件是索引，不是第二条记录。** 签字偏离的**正本**在
> [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) §「SD-2026-10-23-03」。
> 重复登记会让同一件事有两个版本，日后改一处忘一处（§5.3b 三条硬边界之二）。

## 本域已有的签字偏离：1 条

| SD 编号 | 一句话 | 正本 |
|---|---|---|
| `SD-2026-10-23-03@message` | `AdminSendNotification` 的 `affected`：Go 返 `int64`（JSON **数字**），RAG 输出**字符串** | [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) |

> **唯一别名**：`SD-2026-10-23-03@message`（⚠️ **撞号事实**：同一编号在 [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) 是**正本**、在本文件是**域内索引** ⇒ 两者指向**同一条**偏离（不构成跨域撞号）；但 `comment` / `like` 域另有 `SD-2026-10-23-04@comment` / `SD-2026-10-23-05@like` ⇒ 为跨域唯一定位，本条加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效，继续计入计数** —— 被签字描述的行为**今天仍存在**：RAG 侧 `AdminSendNotificationResp` 仍带 `@JsonFormat(shape = JsonFormat.Shape.STRING)` ⇒ 出参是 JSON **字符串** `"3"`；Go 侧出参是 JSON **数字** `3` ⇒ wire 契约差异**未消除**
> **依据**：`service/message/message-api/src/main/java/com/rag/message/message/api/dto/AdminSendNotificationResp.java:43`（`@JsonFormat(shape = JsonFormat.Shape.STRING)`）· Go `service/message/api/internal/types/types.go:22`（`Affected int64` + `json:"affected"` tag，**无** `,string`）⇒ **证据强度：源码行号（静态）**。


### 对本域审查者的三条提醒

1. **它必然让 `affected` 的 wire 对拍变红。** 看到 `{"affected":"3"}` 而非 `{"affected":3}` 时，
   先确认是不是这一条，**再**下结论。
2. **不要单方面改回数字。** 原 DRIFT 台账 `DRIFT-MSGAPI-009` 的
   「Must Preserve：affected 是 JSON 数字」一句已于 2026-10-23 作废 ——
   按 Go 改会把**已签字的偏离**变成**未受控的漂移**。
   若确要改回 Go 形态，**须重新签字**。
3. **不要把 javadoc 的错误描述当依据。** `AdminSendNotificationResp` 的类注释当年把 Go tag 抄成
   `json:"affected,string"`（真源 `types.go:21-23` 是 `json:"affected"`），
   那句错误的「Go: …」正是加 `@JsonFormat(STRING)` 的理由 —— §3.3.2 的典型样本。
   该注释已于本轮改正为中性描述 + 指向本条 SD。

### 本轮顺带关闭的一处台账矛盾

`DRIFT/message/api.md` 的 §7 仍把 `-009` 列为「**未修** · SEMANTIC MISMATCH」，
而签字档已按「保留 RAG 现状」拍板 ⇒ 两者直接矛盾。
按 §5.3b 的判据（**这条差异要不要「修回去」？**）：不要 ⇒ 属本档，不属 DRIFT。
DRIFT 侧的更正已写入 [`DRIFT/message/api.md`](../../DRIFT/message/api.md) 第三轮第五节
（保留原始差异记录、不删除，只更正状态）。

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 本来都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| 请求保护层的四层（MaxBytes / MaxConns / CPU 降载 / 熔断） | ❌ 不属偏离。首轮补齐时即按 Go `service/common/observability` 的语义实现，见 `DRIFT/message/api.md` §0 的「请求保护层：四层全部补齐」 |

## 阶段归属定级附录（2026-11-01 · G2 lane · 残余差异登记 · **已接受 · 不新签**）

> 本节由 G2 lane（`align/g2-message-like-task`）追加 · **只追加，不改任何既有结论**。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层的**两个扫描器**都不读它（保留区机制）⇒ **零投影影响**。
> ⚠️ **本节不是「已签字偏离（SD 编号）」的记录** ⇒ **不与本文件既有的「正本在 `_cross/signed-deviations.md`」约束冲突**
> （那约束只管 SD 编号的**已签字偏离**）⇒ ⇒ 本节是**「已接受 · 不新签」的残余差异登记**，
> 与共享面先例 `docs/Intentional_Deviations/hot/api.md:230`「残余：那个空格（**已接受，不新签**）」**同款**。

### 残余差异登记：`Content-Type` 分号后的那个空格（**已接受 · 不新签**）

| 项 | 值 |
|---|---|
| 性质 | **框架限制（容器层）** —— 不是缺陷、不是待办 |
| 差异（**字节级仍在**） | Go `text/plain; charset=utf-8`（分号后**有**空格）↔ RAG `text/plain;charset=utf-8`（**无**空格） |
| 原台账编号 | `DRIFT-MSGAPI-012`（`docs/DRIFT/message/api.md:1532` · **原判 🟡 部分闭合 · 不可修**，保留） |
| 销账动作 | **2026-11-01**（G2 lane）⇒ 该行**现行态 = ⚫ 已签字偏离**（不再作为待修漂移跟踪）⇒ **离开派工面**（`⚫` ∉ `open_states`） |
| 是否需要新签字 | ❌ **不需要** —— 共享面**已有接受面**：`docs/Intentional_Deviations/hot/api.md:230`「残余：那个空格（**已接受，不新签**）」⇒ **沿用既有接受** ⇒ ⇒ **本 lane 不代签**（Jason 决策） |
| 同款先例 | `docs/Intentional_Deviations/like/api.md:23`（同一条差异的域内登记面）· 销账 house form `docs/DRIFT/like/rpc.md:234` |

**Go 出处（`文件:行号`）**：`go-zero@v1.10.2/rest/internal/header/headers.go:17`
（`ContentTypeJson = "application/json; charset=utf-8"` ⇒ 分号后**有**空格）＋ Go 标准库
`net/http/server.go:2351`（`h.Set("Content-Type", "text/plain; charset=utf-8")` ⇒ 错误响应同样带空格）·
Go `go-zero@v1.10.2/rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)`）

**RAG 现状（`文件:行号`）**：`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`
（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"` ⇒ **charset 已在**）＋ `:151`（写入点）·
`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:718`
（`resp.setContentType("text/plain; charset=utf-8")` ⇒ **应用层写的本来就是 Go 的带空格原串**）＋
`:723-727`（同文件注释自记「Tomcat 序列化吃掉空格 ⇒ 归框架限制」）

**语义等价论证**：Tomcat 11 `org.apache.catalina.connector.Response.setContentType(String)` 走
`MEDIA_TYPE_CACHE.parse` ⇒ media type 本体交 `setContentTypeNoCharset`、charset 单独走 `CharsetHolder`
⇒ 重新序列化时**必然无空格**；`setHeader` / `addHeader` 遇到 `"Content-Type"` 都**转手调 `setContentType`**
（`checkSpecialHeader`）⇒ ⇒ **Servlet API 的三条写法（`setContentType` / `setHeader` / `addHeader`）
全部产出同一个被规范化后的值** ⇒ ⇒ **应用层无路可走** ⇒ ⇒ **框架限制 · 不是本域可修的差异**；
且按 **RFC 9110**，参数分隔符两旁的 **OWS 不显著** ⇒ ⇒ 两者**语义相同** ⇒ ⇒ **不是行为漂移**

**已知差异（保留可见 · 不藏）**：线上真发 `text/plain;charset=utf-8` ／ Go 真发 `text/plain; charset=utf-8`
⇒ **wire 字节级不同**（**展示面 · 非功能面**；影响面 = 全部 api 的 `text/plain` 响应）

**证据强度**：**E4**（真容器实测 + 框架源码机制 + `javap` 实测 `tomcat-embed-core` **11.0.24**）
· **核对日期 2026-11-01**

## 阶段归属定级附录（2026-11-01 · G2 lane · 观测口形态面 · **D 类 · 不改**）

> 本节由 G2 lane（`align/g2-message-like-task`）追加 · **只追加，不改任何既有结论**。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层的**两个扫描器**都不读它（保留区机制）⇒ **零投影影响**。
> ⚠️ **口径由 hub（g4）判**（2026-11-01）：**不改 + 登记为 D 类残余差异**。

### 观测口形态面登记（**D 类 · 不改** · 部署与可观察性形态）

| 项 | 值 |
|---|---|
| 性质 | **部署与可观察性形态**（不进入业务行为契约）⇒ AGENTS.md §3「差异只允许出现在表达层」⇒ **本来就是表达层** ⇒ **不是行为漂移** |
| 判定 | **D 类 · 不改**（口径由 hub（g4）判 · 2026-11-01） |
| 差异面 | ① **路径**：Go `/metrics`·`/healthz` ↔ RAG `/actuator/prometheus`·`/actuator/health`（Actuator 固定前缀）<br>② **端口**（**仅 api 侧**）：Go api 的 devserver 是**独立端口** ↔ RAG api **无独立 `management.server.port`** ⇒ Actuator 挂**业务端口** ⇒ **端口也不同** |
| rpc 侧 | **端口逐域一致** ⇒ 抓取侧**只改 path** ⇒ **观测能力不缺** ⇒ 路径差异 = Spring Actuator 的**表达层形态** |
| api 侧 | **端口 + path 两样都要改** ⇒ 改它 = 改**部署形态** ⇒ 跨 **8+ 个 api 组件** ⇒ **单域不得擅改**（否则域间不一致）⇒ ⇒ 归**阶段二** |
| 落点（形态面行） | `docs/DRIFT/message/vs-common.md` 文末「阶段归属定级附录（2026-11-01 · G2 收官轮 · 观测口「端口 + 路径」形态面登记）」节（**保留区 ⇒ 零投影影响**） |
| 证据强度 | **E3**（Go 侧 `文件:行号` 逐行 + RAG 侧 yml 定位 + 全仓 `management.server.port` 真配置 **0 处**实测）· **核对日期 2026-11-01** |

**逐组件矩阵**：见 [`docs/DRIFT/message/vs-common.md`](../../DRIFT/message/vs-common.md) 文末
「阶段归属定级附录（2026-11-01 · G2 收官轮 · 观测口「端口 + 路径」形态面登记）」节。
⚠️ **同款面在 message-rpc 侧的对应登记** ⇒ `docs/DRIFT/message/rpc.md` 文末
「阶段归属定级附录（2026-11-01 · G2 收官轮 · 部署面「残余差异登记」）」节（`DRIFT-MSG-012` · 端口 16067 **一致**）。
· 核对日期 2026-11-01
