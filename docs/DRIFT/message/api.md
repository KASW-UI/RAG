# `service/message/message-api` 语义对照台账（Go ↔ Spring Boot）

> 🟥 **当前态指针（2026-10-25 台账校准轮回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部「核对日期：2026-10-23（第二轮）」，而正文已到**第九轮（api 侧）**）⇒ **当前状态一律以文件末尾「2026-10-24 第九轮（api 侧）」一节为准**。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

> **真源**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/message/api`
> **目标**：`/Users/pr/Documents/java&golang/RAG/service/message/message-api`
> **核对日期**：2026-10-23（第二轮 · 全维度复核）
> **首轮**：2026-10-22（首轮实现 + 语义验证 + 修复，结论 `SEMANTICALLY NOT EQUIVALENT`）
>
> ⚠️ **本文件是增量台账，历史条目一律保留**。§0 是 2026-10-22 首轮记录的**原文**，
> 未做任何删改；§1–§10 是本轮（2026-10-23）按 `semantic-verification-prompt.md` §31-b
> 补齐的维度分析。**首轮结论「3 处请求解析层漂移已全部关闭」在本轮仍然成立，
> 且本轮独立复核后予以确认（见 §7.0）。**
>
> ⚠️ **证据强度标注**（每条结论都带）：
> - `真源-双侧探针` = Go 侧跑 `go-zero v1.10.2 core/mapping` + RAG 侧跑**真实 DTO + 真实 `MessageGoStyleCoercion`**（与仓库内 `tools/semantic-probe` 同构；本轮扩展到 56 例，见 §8）
> - `静态源码` = 两边源码逐行对照，**未运行**
> - `真容器-RAG单侧` = RAG 侧有运行证据，Go 侧期望值取自源码
> - `双侧活体对拍` = Go 真进程与 RAG 真进程同时打
>
> 本轮**没有**产出任何 `双侧活体对拍` 级结论：Go 真源的 `zrpc.MustNewClient` 在
> `internal/svc/servicecontext.go:21` 无 etcd 时直接 panic，本环境起不来（首轮已记录，
> 本轮复核仍成立）。

---

## 0. 历史记录：2026-10-22 首轮原文（**保留，未删改**）

<!-- @move AGENTS.md L102-L420 · §0 message-api 落地与 3 处请求解析层漂移 + 请求保护层 + client 熔断 -->
### 🟡 `service/message/message-api`：已落地，验证查出的 **3 处请求解析层语义漂移已全部关闭**（2026-10-22）

> 本次工作是「Go `service/message/api` → Spring Boot `message-api`」的首次实现 → 独立子会话语义验证
> （结论 `SEMANTICALLY NOT EQUIVALENT`）→ **两侧探针实证** → 逐条修复。
> 13 个端点的主干维度（正常路径 / 鉴权 / envelope / 身份解析 / 错误码 / 响应序列化）**自始等价**，
> 失配**全部**集中在**请求解析的输入解释层**，现已逐条关闭并有回归守门。

**落地规模**：主源码 4089 行 / 43 文件 + 测试 1019 行 / 2 文件；创建前该模块是 38 个只有 `package` 声明的空壳。
**当前状态**：message-api **88 tests 全绿**（42 条真实 MVC 管线 + 46 条纯单测）—— 2026-10-24 实测；
`tools/semantic-probe/run-parity.sh` **23/23 两侧一致，`EXIT=0`**。

#### 三条漂移：前 2 条来自子会话验证，第 3 条是**修前 2 条的过程中新挖出来的**

| # | 输入 | Go 实测 | 修复前 RAG | 性质 | 状态 |
|---|---|---|---|---|---|
| **DRIFT-MSGAPI-001** | `{"conversation_id":123}`（JSON **数字**） | **接受 → 123** | **拒绝 → 5001** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**）。**本条段内原无可复算指针，本轮补齐**：RAG `service/message/message-api/src/main/java/com/rag/message/message/api/dto/ConversationMessagesReq.java:64-66`（`@JsonProperty("conversation_id")` + `setConversationId(Object raw)` → `GoStyleStringInt64.parse(raw, "conversation_id")` ⇒ **接受** JSON 数字）· RAG `service/message/message-api/src/main/java/com/rag/message/message/api/dto/GoStyleStringInt64.java:74`（`public static Long parse(Object raw, String fieldName)`）· Go `go-zero@v1.10.2/core/mapping/unmarshaler.go:1113-1125`（`case json.Number:` → `setValueFromString(baseType.Kind(), value, v.String())` ⇒ Go 的 `,string` 入参方向**宽松**，接受数字）· Go `go-zero@v1.10.2/core/jsonx/json.go:74-77`（`decoder.UseNumber()` ⇒ 解出来是 `json.Number`，不是 `float64`/`int`）· 核对日期 2026-10-30。 |
| **DRIFT-MSGAPI-002** | `{"offset":"10"}`（**无** `,string`，JSON 字符串） | **拒绝 → 5001** | **接受 → 10** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修 <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:16:43 · 4s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| **DRIFT-MSGAPI-003** 🆕 | `{"extra":null}`（字段 tag 是 **`optional`**） | **接受** | **拒绝 → 5001** | SEMANTIC MISMATCH | ✅ 已修 |

⚠️ 前两条是**方向相反**的 —— 一条该收不收、一条不该收却收，所以「粗看都能跑通」，
只有**逐用例两侧对拍**才暴露得出来。

##### DRIFT-001/002 根因：一个 Go 反射事实被写反了

**`json.Number` 是 `type Number string`，`reflect.Kind()` 返回 `string`，不是 `number`。**
⇒ Go 端 `processNamedFieldWithValueFromString` 的 `if valueKind != reflect.String` 对 JSON 数字**放行**，
再经 `fillPrimitive` 的 `case json.Number` 分支 `ParseInt` 成功。所以 Go 的 `,string`
是**「入参方向宽松（string 与 number 都收），出参方向才严格（输出成字符串）」**，
而 RAG 当成了「双向都只收字符串」。

Evidence：
- Go 侧 `go-zero@v1.10.2/core/mapping/unmarshaler.go:889-894`（Kind 判定）、`:1115-1125`（`case string` + `case json.Number` 双分支）、
  `go-zero@v1.10.2/core/jsonx/json.go:74-77`（`UseNumber`）、`go-zero@v1.10.2/core/mapping/utils.go:376-377`（`,string` → `FromString=true`）
- RAG 侧原 `ConversationMessagesReq.java:54-63` 等 6 个 DTO 的 `String` setter + `GoStyleCoercion.java:118-127`
  （`LogicalType.Textual` + `CoercionInputShape.Integer=Fail`）
- 修复：新增 `dto/GoStyleStringInt64.java` 复刻双向接受语义，**8 个 setter 从 `String` 入参改成 `Object` 入参**
  （只有「不声明目标类型、让 Jackson 原样递出」能同时表达「两种都收」）；
  新增 `config/MessageGoStyleCoercion.java` 叠加两条 message 域补强（`Integer/String=Fail`、`Boolean/String=Fail`）。

⚠️ **DRIFT-001 的影响面是 9 个请求字段**：`conversation_id` ×4 端点、`notification_id`、`recipient_id` ×2、`target_id`。
任何用 `JSON.stringify({conversation_id: 123})` 的客户端在 Go 上成功、在 RAG 上全量失败。

⚠️ **DRIFT-002 的补强刻意只落在 message-api**，**没有**改 `users-common` 的 `GoStyleCoercion`。
理由：那条「String→int 豁免」是 users 域**按 users 真源逐字段实测**得出的结论，对 message 域不成立；
共享规则一改就会把 users 那边已经验证对的行退回去。**补强要局部叠加，不要污染共享层。**

##### DRIFT-003 根因：把 `,omitempty` 当成了 `,optional`

我最初的判断来自 users 域的一条实测（`extra_info: null` 被拒），于是给 message 也加了 `explicitNull` 拦截。
但**两处 tag 根本不同**：

| 字段 | tag | Go 对 `null` 的判定 |
|---|---|---|
| message `AdminSendNotificationReq.Extra`（`types.go:18`） | `json:"extra,optional"` | **接受** |
| users `CreateUserReq.Extrainfo`（`user/api/.../types.go:10`） | `json:"extra_info,omitempty"` | **拒绝** |

根因在 go-zero 的选项解析：`go-zero@v1.10.2/core/mapping/utils.go:372` 的 `parseOption` switch **只认三种**
（`inherit` / `string` / 以 `optional` 开头的），**`omitempty` 不在其中** ⇒ 它不设置任何 unmarshal 选项
⇒ `Optional` 保持 `false` ⇒ `go-zero@v1.10.2/core/mapping/unmarshaler.go:856-862` 的 `if mapValue == nil` 落到
`return fmt.Errorf("field %q mustn't be nil", key)`。

**`,omitempty` 只控制出参是否省略，与「入参可否为 null」无关。** 把它当 optional 是一个很容易自然发生的误解。

Evidence：
- Go 侧 `service/message/api/internal/types/types.go:18` vs `service/user/user/api/internal/types/types.go:10`
- go-zero v1.10.2 `go-zero@v1.10.2/core/mapping/utils.go:372-385` + `go-zero@v1.10.2/core/mapping/unmarshaler.go:856-862`
- 探针同时覆盖了**两种 tag**（`required` 显式 null → 两侧都拒；`,optional` 显式 null → 两侧都收）
- ⚠️ **探针的比对面是 ACCEPTED/REJECTED 判定，不是错误文案**：`required` + 显式 null 时两侧都拒，
  但**原因不同**（Go `mustn't be nil` / RAG `is not set`）。两者都映射到同一 envelope 业务码 5001，
  在 API 边界**不可观测**，故按 §25 归为 `IMPLEMENTATION DIFFERENCE`。
  ⚠️ 但若日后要比**错误文案**（例如某端点把 msg 回传给客户端），探针必须先扩到比对 message 字段，
  否则会拿到一个「看起来全绿」的假结论。
- 修复：删掉 `GoStyleRequired.explicitNull(..)` 调用 + `extraPresent` 字段，改正 javadoc，
  并把锁死错误答案的 `extraPresenceSemanticsArePreserved` 换成对照用例

##### 探针已落盘进仓库（不再是 `/tmp` 一次性脚本）

`tools/semantic-probe/`：`go/main.go`(102) + `java/Probe.java`(106) + `run-parity.sh`(109) + `README.md`(136)。
Go 半边是**真源镜像**（struct 逐字段对齐 `types.go`）直接跑 go-zero v1.10.2 的 `core/mapping`；
Java 半边跑项目**真实 Jackson3 + 项目自己的 DTO**。复现：

```
bash tools/semantic-probe/run-parity.sh      # 23 例；一致 23 / 不一致 0 / EXIT=0
```

⚠️ 该探针的价值是**回归守门**，不是一次性取证（已做过变异验证：把 setter 改回 `String` → 2 行变红 `EXIT=1`；
Go 侧删一个用例 → 不对称分支触发 `EXIT=1`）。它与 §3.3 那条「绿测试守卫错误答案」互为镜像：
**探针守「不许回退」，测试守「不许再错」。**

#### 元教训：**绿测试可以是「错误答案的守卫」**（本条比三个缺陷本身更重要）

**3 条漂移里有 3 条都被一条绿测试锁死过**，且都不是笔误、都有完整推导：

1. **DRIFT-001** 固化在两处：
   - `ConversationMessagesReq.java` 的 javadoc 写着「`json.Number` 的 Kind 是 number」—— **事实错误**；
   - `MessageApiMvcPipelineTest#int64AsJsonNumberIsRejected` 把 **RAG 的行为**断言成了「与 Go 一致」，
     **这条绿测试会主动阻止正确修复**。
2. **DRIFT-002** 同款：`GoStyleCoercion` 里那条 String→int 豁免被我当成通用规则写死，
   连带测试断言了 `{"offset":"10"}` 被接受。
3. **DRIFT-003**：`MessageApiMvcPipelineTest#extraPresenceSemanticsArePreserved`
   断言 `extra: null` → 5001。测试名里带「PresenceSemanticsArePreserved」，
   **读起来就是在宣称对齐了真源，实际断言的是我的错误类比**。

⇒ 沿用 §3.3 那条「把没看过的东西变成看过了」的元教训，补一条更硬的：
> **测试断言的是「RAG 现在的行为」，不是「Go 的行为」。**
> 当测试名/DisplayName 里写着「对齐 Go」「已保留语义」而断言的是 RAG 的实际输出时，
> 它要么是**巧合**、要么是**给未验证的假设发通行证**。后一种比没有测试更危险 ——
> 它会让 CI 绿灯为一个反向漂移背书，且修复时会先撞上「测试红了」的心理阻力。

**⚠️ 附带一条判据（本项目专有）**：一个**跨域照搬来的实测结论**在动手前必须先核对 **tag 是否逐字相同**。
DRIFT-003 的根因不是「实测错了」，而是**实测对象（users `extra_info,omitempty`）与本次对象（message `extra,optional`）
根本不是同一个 tag**。⇒ §3.1 的「不要拿现有 RAG 代码当对照基准」要再加一条：
**也不要拿另一个域的实测结论当对照基准**，除非已确认其前提条件逐字成立。

**⚠️ 再附带一条（本轮复核 DRIFT-MSGAPI-004 时补上）**：**「Go 侧没有 X」这类否定式断言，
默认不可信，必须去被否定的那一侧找正面证据。** 本项目的反例有两条，且形态相同：
1. 「Go yaml 没写 MaxBytes/MaxConns/Breaker」——其实由 `go-zero@v1.10.2/rest/config.go` 的 `default` tag 开启；
2. 「Go 侧没有 client 熔断」——其实由 `go-zero@v1.10.2/zrpc/internal/config.go:14` 的 `default=true` 开启。
⇒ 两处都是**「默认值藏在框架 tag 里，而对照时只看了数据文件」**。
**否定式断言无法用「没找到」来证实**，只能靠找到那个 tag。

**必须做的三件事**：① 论证里每个 Go 事实都带 `文件:行号`；② 凡写「与 Go 一致」，
必须有**同时跑两侧**的探针；③ 修复漂移时**先改/删错误断言，再改实现**，否则会被测试挡住。

#### ✅ 请求保护层：四层全部补齐（2026-10-22）

此前记为「RAG 三个全无、需决策」。现已实现完毕，新增包
`service/message/message-api/src/main/java/.../protection/`（7 个类）+ 4 个测试类（**30 例**）。

**先说清一件事：这四层在 Go 的 yaml 里一个字都没写，但它们是开启的。**
依据是 go-zero v1.10.2 的两处 tag：`go-zero@v1.10.2/rest/config.go:52-56`（`MaxConns=10000` /
`MaxBytes=1048576` / `CpuThreshold=900`）与 `go-zero@v1.10.2/rest/config.go:12-22` `MiddlewaresConf`
九个开关**全部** `json:",default=true"`。⇒ **照 yaml 逐字段对，永远看不见这四层。**

| 层 | Go 出处 | 拒绝响应 | RAG 实现 | 测试 |
|---|---|---|---|---|
| 请求体上限 | `go-zero@v1.10.2/rest/handler/maxbyteshandler.go:10-25` | **413 裸响应** | `GoStyleMaxBytesFilter` | MVC 3 例 + **真 Tomcat 3 例** |
| 并发上限 | `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12-36` | **503 裸响应** | `GoStyleMaxConnsFilter` | 6 例（含并发/异常归还） |
| CPU 降载 | `go-zero@v1.10.2/rest/handler/sheddinghandler.go:20-52` + `go-zero@v1.10.2/core/load/adaptiveshedder.go` | **503 裸响应** | `GoStyleCpuSheddingFilter` + `GoStyleAdaptiveShedder` | 10 例 |
| 熔断 | `go-zero@v1.10.2/rest/handler/breakerhandler.go:19-41` + `go-zero@v1.10.2/core/breaker/googlebreaker.go:52-79` | **503 裸响应** | `GoStyleBreakerFilter` + `GoStyleBreaker` | 8 例 |

##### 三条必须记住的可观察契约（都偏离直觉）

1. **裸状态码 = 无 body、无 Content-Type、不走 envelope。**
   Go 侧四处都是裸 `w.WriteHeader(code)`，不调 `httpx.Error`。
   RAG 因此**刻意不用 `sendError`**（那会让容器补 `text/html`），改用 `setStatus` + 不写 body。
   ⇒ 这与本模块「所有业务错误恒 200 + envelope」的约定**刻意不同**：保护层在到达业务逻辑前就拒了。

2. **413 压过 401。** Go `go-zero@v1.10.2/rest/engine.go:105-112` 先装 `buildChainWithNativeMiddlewares`
   （内含 MaxBytes，`:164`）**再** `appendAuthHandler`（`:109`）；而 `chain.Append` 是
   「加在请求流的最后」（`go-zero@v1.10.2/rest/chain/chain.go:38-43`）⇒ **MaxBytes 在鉴权之外**。
   可观察后果：**往受保护端点发 2 MiB 无 token 请求，Go 返回 413 而不是 401。**
   RAG 侧 MaxBytes 是一个 Servlet `Filter`，而**所有 Filter 都包在 `DispatcherServlet` 外、
   `HandlerInterceptor`（JWT 鉴权）跑在其内** ⇒ 「413 早于 401」来自**容器结构**，
   **不是**来自 `@Order` 数值。（⚠️ 2026-10-22 实测更正：把 `@Order` 改成 `LOWEST_PRECEDENCE`
   后断言<b>依然绿</b>；能真正让断言变红的是「让该层失效」——摘掉 `@Component` ⇒ 413 变 401。）
   已有两条测试锁死（MockMvc 装配 + 真 Tomcat），且做过**变异验证**：关掉 MaxBytes → 413 变 401 → 红。

3. **⚠️ 熔断<b>不会</b>被下游 RPC 失败触发。** breaker 的判据是
   `cw.Code < 500 ⇒ Accept()`（`go-zero@v1.10.2/rest/handler/breakerhandler.go:33-34` —— 🆕 **2026-10-27 订正**：原写 `breakerhandler.go:39`，`:39` 实为 `next.ServeHTTP(cw, r)`；该判据的真身在 `:33`（`cw.Code < http.StatusInternalServerError`）＋ `:34`（`promise.Accept()`），`:36` 是 `promise.Reject(...)` ⇒ **结论不变、指针订正**），而本模块所有业务错误
   （含 gRPC 失败）恒为 **HTTP 200 + envelope** ⇒ 在 Go 侧被记为**成功**。
   能触发熔断的只有两条路径：**超时（`timeouthandler.go:115` 写 503）**与
   **未捕获 panic（`recoverhandler.go:17` 写 500）**。
   ⇒ **它的真实职责是「别让正在超时/崩溃的端点被继续打」，不是「保护下游」。**
   ⚠️ 若按 envelope 里的业务码去熔断，就是**新增行为**，不是对齐。

##### 一处刻意的表达层差异（已在 javadoc 留痕，非隐藏缺口）

Go 的 MaxConns 闸门是**每路由一个**（`engine.go:146` 对每条路由各建一次 latch），
RAG 取**全局一个**。区别只在「单端点打满 10000 且同时另一端点也接近 10000」时显现，
而那时 Tomcat 的 `max-threads=200` 早已先成为瓶颈。

##### 为什么 Tomcat 自带的配额**不能**替代 MaxConns

`max-threads` 超限是**排队**、`max-connections` 超限是**停止 accept**，
两者都不是 Go 的**立刻 503**。「变慢」与「被拒」在过载时是决定性的客户端行为差异，
调 Tomcat 参数只能近似、不能替代。

##### 两条降载相关的诚实边界

- **CPU 读不到时 fail-open**：`getSystemCpuLoad()` 在部分容器恒返回 `-1`。
  本实现把 `-1` 视作**未过载**（放行），与 Go `systemOverloaded()` 首个分支同向。
  理由：当成过载会把**全部流量杀光**；降级为不降载则与 Go 同向。
- **⚠️ 本环境无法启动 Go 真源做活体双侧对拍**：其 yaml 指向 `etcd:2379`，
  而 `zrpc.MustNewClient` 在 `internal/svc/servicecontext.go:21` 无 etcd 时**直接 panic**。
  ⇒ 本次 413 的期望值取自 Go 源码而非活体 curl，该证据边界已在测试 javadoc 中标注。

#### 🟡 其余已留档的差异（**不再单开编号** · 2026-10-24 第十一轮：标题原写「待决策」，但表内各行现已全部定性 ⇒ **不再有「待决策」的格子**）

> **表内各行的当前状态**：
> · **✅ 两侧一致**（4 行）：请求保护层 / 404 body / 405 的 `Allow` 头 / Content-Type 非 JSON —— 后三行的首轮结论**已被真容器实测推翻**
> · **⏸ 保持登记、后续修**（1 行）：成功响应 Content-Type 缺 charset（= `MSGAPI-010`）—— 用户 2026-10-24 拍板
> · **✅ 已签字**（1 行）：admin HMAC 密钥（`N-5`）
> · **📌 仅字节级、语义等价**（1 行）：`extra` map 键序
> · **📌 运维配置差异**（1 行）：观测端点（`:16066/metrics` vs `:8892/actuator/*`）⇒ 属部署侧，代码不改

| 项 | Go | RAG | 建议 |
|---|---|---|---|
| ~~请求保护层~~ | `MaxBytes` 1MB→413、`CpuThreshold` 900→503、`MaxConns` 10000、**`Breaker` 熔断** | ✅ **已全部补齐**（2026-10-22）⇒ 见下方「请求保护层」小节 | — |
| ~~404 body~~ | `"404 page not found\n"` + text/plain + nosniff | 空 body | ✅ **2026-10-24 第八轮实测：两侧 body 一致**（`Content-Length` 都是 **19**）⇒ 首轮「空 body」结论**已被推翻**；剩余差异只有**状态行 reason phrase**（= MSGAPI-011）+ **Content-Type 空格**（= MSGAPI-012） |
| ~~405 头~~ | 带 `Allow: POST` | 带 `Allow: POST` | ✅ **2026-10-24 第十轮复核原始 dump 产物：两侧 `Allow` 头完全一致** ⇒ **首轮「RAG 无 `Allow` 头」的结论是错的**（静态读源码判反了）⇒ **这一行不需要「建议补」，它从来就不缺**。详见下方 §2.2 的完整 6 档对照表 |
| 成功响应 Content-Type | `application/json; charset=utf-8` | `application/json` | ✅ **2026-10-24 第八轮真容器级已确认**（= `DRIFT-MSGAPI-010`）；⚠️ **用户 2026-10-24 拍板「保持登记，本轮不做」**（共享面影响全部 10 个 api）⇒ **不是签字**，仍是登记在册的待办 |
| `extra` map 键序 | `encoding/json` 排序（确定性） | `LinkedHashMap` 保序 | 字节级差异，JSON 语义等价 |
| 观测端点 | `:16066/metrics`+`/healthz`+pprof | `:8892/actuator/*` | 运维配置要改 |
| ~~Content-Type 非 JSON~~ | ~~继续处理出 envelope~~ | ~~415 空 body~~ | ✅ **2026-10-22 已关闭**，与 like 域合并为一份共享实现。⚠️ 原签字理由「两侧都是拒绝」**已被推翻**：Go 的 `withJsonBody` 不满足时是**丢弃 body 后按空对象继续**，对零必填 DTO（`PageReq`/`EmptyReq`）Go 其实**成功**。详见「请求体 Content-Type 判据」章节（已搬至 `docs/DRIFT/common/http.md` §0 DRIFT-006） |
| admin HMAC 密钥 | 15 字节原样 | zero-pad 到 32B | **N-5 已签字**（跨部署 token 不互认） |

#### ✅ DRIFT-MSGAPI-004（2026-10-22 复核新发现，**已修**）：RAG 的 gRPC **client 熔断缺失**

起因是复核 `MessageRpcClient` 里那句「⚠️ 禁止在这里加 retry / circuit breaker：Go 侧没有这层」——
**该注释的 retry 那半对、breaker 那半是事实错误**。

| 项 | Go | RAG（修前） | 状态 |
|---|---|---|---|
| client 熔断 | **有，默认开** | **无任何 client 拦截器** | ✅ 已修 |
| client retry | 无（`RpcClientConf` 没有 `Retries` 字段） | 无 | EQUIVALENT（**保持不加**） |

Evidence：
- `go-zero@v1.10.2/zrpc/internal/config.go:14` — `ClientMiddlewaresConf.Breaker bool json:",default=true"`
- `go-zero@v1.10.2/zrpc/internal/client.go:112-113` — `if c.middlewares.Breaker { append(..., clientinterceptors.BreakerInterceptor) }`
- `go-zero@v1.10.2/zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` —
  `breaker.DoWithAcceptableCtx(ctx, path.Join(cc.Target(), method), ...)` ⇒ **每个 RPC 方法一个 breaker**
- Go `etc/messagecenter.yaml` 未配 `Middlewares` ⇒ 走 `default=true`
- 实现：`protection/GoStyleGrpcClientBreakerInterceptor`（**复用 `GoStyleBreaker`**，因 go-zero 的
  client breaker 与 HTTP 层 breaker 是同一个 googleBreaker）+ `GoStyleCircuitOpenException`

##### 为什么它当初没被发现（比缺陷本身更值得记）

`go-zero@v1.10.2/zrpc/internal/codes/accept.go:9-17` 的 `codes.Acceptable` 只把
`DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted`
判为失败；而 message-rpc 的业务错误（逐个 `errWithBizCode` 调用点核对）
**只用** `InvalidArgument / NotFound / PermissionDenied`，**全都不在失败名单里**
⇒ **业务错误在 Go 侧被记为 Accept，不参与熔断**。
⇒ 正常流量与业务错误路径下两侧**行为完全一致**，只有「下游返回 5xx 类 gRPC code 或超时」才分岔。
**任何只测业务路径的验证（含本项目现有的 42 条 MVC 管线用例）都测不出这条。**

##### ⚠️ 判据必须逐条对齐：一个 code 都不能多

**acceptable 判据照抄 Go 的那 6 个失败 code。若图省事改成「所有非 OK 都算失败」，
会把 `NOT_FOUND`（会话不存在 5202）、`PERMISSION_DENIED`（无权限 5205）这类
正常运营中天天发生的业务错误大量计成失败 ⇒ 熔断误开 ⇒ 打掉正常流量。
那比「该熔断时没熔断」的现状**更糟**。**已由
`GoStyleGrpcClientBreakerInterceptorTest#theSixFailureCodesAreNotAcceptable` /
`#businessErrorsAreAcceptable` 双向锁死。**

##### 熔断拒绝时必须抛**普通异常**，不是 `StatusRuntimeException`

Go 返回 `breaker.ErrServiceUnavailable`（普通 `error`，非 gRPC status）
⇒ `BizCodeFromError` 走 `status.FromError(err)` 的 `!ok` 分支 ⇒ **1015 + msg "service busy"（查表）**。
RAG 若改成抛 `StatusRuntimeException`，code 碰巧仍是 1015，但 description 会变成
`"circuit breaker is open"` ⇒ **msg 与 Go 不同**。已由
`#circuitOpenExceptionIsNotAGrpcStatus` 锁死。

##### 🔴 这条修复暴露了 §3.3 的又一次真实发作（比缺陷本身更值得记）

拦截器的**8 条单元测试全程绿灯**，但它**在真实 Spring 装配下完全没生效**，连续被抓到两个装配缺陷：

1. **两个构造器都没标 `@Autowired`** ⇒ Spring 退回找无参构造器 ⇒ 找不到 ⇒
   **`ApplicationContext` 直接起不来**（`No default constructor found`）。
2. **只加 `@Component` 不会被 starter 收集** ⇒
   `GlobalClientInterceptorRegistry.getClientInterceptors()` 返回 **`[]`**。
   根因：devh 的 `AnnotationGlobalClientInterceptorConfigurer` 用
   `applicationContext.getBeansWithAnnotation(...)` 收集，**它只认
   `@GrpcGlobalClientInterceptor`，不认「是不是 ClientInterceptor」**；
   而该注解本身又**不是** Spring stereotype ⇒ **两个注解缺一不可**。

⇒ 这两条**都是纯单元测试看不见的**（它们直接 `new` 拦截器，完全不经过 Spring）。
新增 `GoStyleGrpcClientBreakerWiringTest`（2 例）专治这一层：它断言的是
「拦截器出现在 starter 自己的 registry 里」，而不是「拦截器存在」。
**若没有它，这次修的是一个从不执行的类。**

##### 🔴 补端到端时又挖出第 3 个缺陷：**异常类型选对了，但没人接它**

前两层都绿之后，补 `GoStyleGrpcClientBreakerE2ETest`（真 in-process gRPC server +
真 channel + 真 MockMvc）断言「响应码从 **5001 变成 1015**」——
**第一次跑就红了，恒为 5001**。

诊断过程（`trackedMethods=1` 证明拦截器在链上；`closeCount=41` 而调用了 161 次，
证明熔断**确实打开过**、只是异常被吃掉了）：
**13 个 service 全部写成 `catch (StatusRuntimeException e)`**，而 Go 是
{@code if err != nil}（**捕获一切**）。我刻意抛的普通异常
{@code GoStyleCircuitOpenException} 绕过了这个 catch，
最后落到 `GlobalExceptionHandler#handleAny(Exception)` 的**兜底分支**，
被统一映射成 **5001** ⇒ 客户端看到的业务码与 Go 不同。

⚠️ **这正是我自己写在 {@code GoStyleCircuitOpenException} javadoc 里的那条推理失效了**：
我论证了「普通异常 ⇒ {@code bizCodeFromError} 会返回 1015」，
单测也断言了这一点并通过 —— **但 {@code bizCodeFromError} 根本不会被调用**。
判据本身没错，错在**没验证调用链上有没有人真的走那条判据**。

**修法**：在 `GlobalExceptionHandler` 单列 `@ExceptionHandler(GoStyleCircuitOpenException.class)`
→ 1015 + msg 查表（`errmsg.go:55` 的 "service busy"）。

⚠️ **为什么没把 13 个 catch 改成 `Exception`**（更「忠于 Go `err != nil`」的做法）：
那个 try 里除了 stub 调用，还包着 `support.toXxxResp(...)` 的响应转换；
一旦它抛 `BusinessException`，宽 catch 会把它**重新包成 1015** ——

那是**新引入的错**。⇒ 按异常类型精确处理是三项里副作用最小且可证明的。

#### 已关闭的未决项

- **Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES` 默认值 = `false`**（实测）⇒ 与 Go「忽略未知字段」**等价**。
  这条与 Jackson 2 的默认值**相反**，属 Jackson 3 的破坏性变更，别按 Jackson 2 的直觉写断言。
- **请求保护层四层**（MaxBytes / MaxConns / CPU 降载 / 熔断）：已补齐，见上方小节。
- **DRIFT-MSGAPI-001 / -002 / -003**：全部已修（见上）。防回退由**两套互补的网**守住：
  `MessageApiMvcPipelineTest` 的 **42 条 MVC 管线用例**（单进程、快、能进 CI）+ 
  `tools/semantic-probe` 的 **23 例双侧对拍**（慢、但锚定的是真源而非 RAG 现状）。
  ⚠️ **只有探针能发现「两侧一起错」**，所以这两套网不能互相替代。

#### 下一步建议（message 域）

1. ~~先修 DRIFT-MSGAPI-001，再修 -002~~ ✅ 已完成（-003 为附带挖出）；
2. ~~保护层决策（补齐 vs 放弃）~~ ✅ 已补齐四层（见上方「请求保护层」小节）；
   「Go yaml 未配置 ≠ Go 未开启」也已写进 `application.yml` 的 `rag.protection` 段头部注释
   （⚠️ 前缀 2026-10-22 由 `message-api.protection` 改来，保护层已下沉到 `service/common`，见下方新章节）。
   ⚠️ 复核 `MessageRpcClient` 时顺带发现它那条「禁止加 retry/熔断」的注释**breaker 那半是错的**，
   并由此挖出 DRIFT-MSGAPI-004（client 熔断缺失）——**已修**。retry 那半仍然成立，**继续保持不加**。
3. **补响应头对拍**（真容器 curl 两端 dump 全部头 + body），一次关掉 404/405/Content-Type 三条；
   ⚠️ 注意保护层的 413/503 已在 `MessageApiProtectionRealTomcatTest` 用真 Tomcat 验过裸响应，
   但那是 **RAG 单侧**（本环境起不了 Go 真源），Go 侧期望值仍取自源码；
   ⇒ **2026-10-23 部分收口**：Content-Type 一条已用转换器级运行证据确证（见 §7 DRIFT-MSGAPI-010），
   404/405 两条已在 `docs/DRIFT/common/http.md` DRIFT-006 侧收口，但**本组件仍缺真容器 curl 对拍**；
4. **给 3s 超时补一条真触发测试**（stub `sleep(5000)` → 断言 503 + text/plain + body）——
   Bean 存在已被证明，但 503 分支**至今没有任何运行证据**（§3.3 同款形态）；
   ⚠️ **2026-10-23 复核：该结论仍然成立**，本轮未补上（见 §9 UNRESOLVED-004）；
5. ~~给 gRPC client 熔断补一条端到端触发测试~~ ✅ 已完成
   （`GoStyleGrpcClientBreakerE2ETest`：真 in-process gRPC server 恒返 INTERNAL，
   断言响应码 **5001 → 1015**）。补它的过程又挖出兜底异常处理器吃码的缺陷，已修。
6. **补 `message-api/HUMAN_ONLY.MD`**（§2 已把该模块登记为「已实现 + 已对照，但文档缺失」；§7.2 的旧清单曾把它误列为已存在，已校正）。
7. **⚠️ 2026-10-23 新增**：本轮新增 5 条解析/序列化漂移（`DRIFT-MSGAPI-005` ~ `-009`）与
   1 条响应头漂移（`-010`），**必须先按 §7 的 Must Preserve 修 DTO 与 coercion，再改被锁死的绿测试**。
   其中 `-009` 是**本项目第二次「绿测试守卫错误答案」**，形态与首轮的 `extraPresenceSemanticsArePreserved` 完全同构。

---

## 1. Component Contract（§4 + §5）

**不从前缀或类名推断，以下全部由源码确认。**

| 项 | 内容 | 出处 |
|---|---|---|
| **Component** | `message-api` = message 域的 **HTTP gateway**，只做「解析 → 鉴权身份 → 参数微校验 → 转 message-rpc → 映射 → envelope」，**不碰 DB** | `routes.go`（13 条路由）+ 13 个 logic（**零 SQL**） |
| **Purpose** | 给 user-api / admin-api 的前端提供会话与通知能力；把 gRPC 的业务码包成 HTTP 200 + envelope | `messagecenter.go:41-66`（`SetOkHandler` / `SetErrorHandler`） |
| **Public Operations** | **13 个**：user 8（`POST conversations/{list,messages,read,send}`、`POST notifications/{list,read,readall}`、`GET unread`）+ admin 5（`POST admin/conversations/{list,messages,read,send}`、`POST admin/notifications/send`） | `routes.go:20-58` / `:61-102`；RAG `MessageController` + `MessageAdminController` |
| **Inputs** | JSON body（无 query、无 path var、无 header 业务字段）；JWT `Authorization: Bearer` | `handler/message/*.go` 全部 `httpx.Parse(r, &req)`；`go-zero@v1.10.2/rest/httpx/requests.go:50-77` |
| **Outputs** | `{"code":200,"msg":"OK","data":<DTO>}` 或 `{"code":<biz>,"msg":...,"data":null}`，**HTTP 恒 200**（鉴权失败除外） | `messagecenter.go:41-66` |
| **Dependencies** | ① `message-rpc`（gRPC blocking stub，`discovery:///message-rpc` ↔ Go etcd key `message.rpc`）② 两把 JWT 验签密钥（只验不签）③ Prometheus ④ OTLP | `servicecontext.go:21`；`types.go:14`；`metrics.go:34-48` |
| **State** | **无业务状态**。只持有计数器（3 个 metric）+ 熔断器 + CPU 降载器 + MaxConns 闸门 | `metrics.go`、`GoStyleBreaker` |
| **Side Effects** | 仅 metrics 记账。**无本地写、无 MQ、无本地缓存** | 13 个 logic 全文无副作用 |
| **Failure Model** | 除「鉴权失败 = 401 空 body」与「框架级 404/405/415/413/503/超时时 503」外，**一切失败都是 HTTP 200 + 业务码** | `messagecenter.go:55-65` |

**⚠️ 本组件没有事务边界。** Go 侧 13 个 logic 无一个调 `sql`/`Begin`（api 层不碰 DB）；
RAG 侧 13 个 Service 无一个 `@Transactional`。⇒ §15 判定 `EQUIVALENT`（两侧都不保护任何跨语句操作集）。

---

## 2. GO-SEMANTIC-MODEL（§7 + §6 的 18 字段）

> 13 个 operation 结构完全同构（goctl 产物），下表逐条列差异项，其余字段统一列在底部。

### 2.1 公共骨架（13 个 logic 逐字相同）

```
Input:  HTTP 请求 + Authorization 头
Precondition: 无
Trigger: 请求到达被 router 匹配
Processing（语义顺序，不抄 API）：
   1. 按 go-zero@v1.10.2/rest/engine.go 的 chain 顺序过保护层（见 §5.1）
   2. go-zero@v1.10.2/rest/handler/authhandler.go:54-82 验签 → 非标准 claim 逐个塞进 ctx
   3. handler.Entries() → httpx.Parse(r, &req) 解析 body
   4. shared.UserIDFromContext(ctx)：ctx.Value("userId").(json.Number) → Int64()
      失败 ⇒ NewErrCode(5004) + ObserveReject(route, "user_id_missing"|"admin_id_missing")
   5.（仅 sendadminnotification）!broadcast && target_id<=0 ⇒ 5003 + ObserveReject(…,"target_missing")
   6. 调 message-rpc（blocking，唯一出向调用）
   7. 失败 ⇒ shared.RPCError(err) → BizCodeFromError + status.Message() 原文
   8. 成功 ⇒ shared.To*Resp(proto) → httpx.OkJsonCtx → SetOkHandler 包 envelope
Result:      HTTP 200 + envelope；失败亦 200
State Change: 无本地状态；metric 记账（request_total 恒记；reject_total 条件记）
Side Effects: 仅 metric
Error:        见 §5.2
Timeout:      3s（`observability.DisableNativeRestTimeout` 关掉 go-zero 原生，
              换成 `service/common/observability/http.go` 自己的 3s + ctx 传播）
Retry:        无（`zrpc.RpcClientConf` 无 Retries 字段）
Concurrency:  每请求一个 goroutine；MaxConns 闸门**每路由一个**
Ordering:     保护层 → 鉴权 → 解析 → 身份 → 参数 → RPC
Lifecycle:    metrics.InitMetrics(sync.Once) → conf.MustLoad → MustNewServer
              → NewServiceContext(MustNewClient，**无 etcd 即 panic**)
```

### 2.2 13 个 operation 的差异项

| # | Operation | Input DTO（tag 原文） | 零必填？ | 下发的 proto 关键字段 | 出参 DTO |
|---|---|---|---|---|---|
| OP-01 | `POST /message/v1/conversations/list` | `PageReq{offset,optional / limit,optional / unread_only,optional}` | 是 | `operator_id=JWT uid`、`operator_role=USER` | `ConversationListResp` |
| OP-02 | `POST /message/v1/conversations/messages` | `ConversationMessagesReq{conversation_id,string / offset,optional / limit,optional}` | 否（`conversation_id`） | + `conversation_id`、`operator_role=USER` | `ConversationMessagesResp` |
| OP-03 | `POST /message/v1/conversations/read` | `MarkConversationReadReq{conversation_id,string}` | 否 | + `operator_role=USER` | `EmptyReq{}` |
| OP-04 | `POST /message/v1/conversations/send` | `UserSendChatReq{recipient_id,string,optional / conversation_id,string,optional / content}` | 否（`content`） | `sender_role=USER`、`recipient_role=USER`（**硬编码**） | `SendChatResp` |
| OP-05 | `POST /message/v1/notifications/list` | `PageReq` | 是 | `user_id=JWT uid`（**不是 operator_id**） | `NotificationListResp` |
| OP-06 | `POST /message/v1/notifications/read` | `MarkNotificationReq{notification_id,string}` | 否 | `user_id=JWT uid` | `EmptyReq{}` |
| OP-07 | `POST /message/v1/notifications/readall` | `EmptyReq{}` | 是 | `user_id=JWT uid` | `EmptyReq{}` |
| OP-08 | `GET /message/v1/unread` | `EmptyReq{}`（**唯一 GET，body 仍被 Parse**） | 是 | `user_id=JWT uid` | `UnreadSummaryResp` |
| OP-09 | `POST /message/v1/admin/conversations/list` | `PageReq` | 是 | `operator_role=ADMIN` | `ConversationListResp` |
| OP-10 | `POST /message/v1/admin/conversations/messages` | `ConversationMessagesReq` | 否 | `operator_role=ADMIN` | `ConversationMessagesResp` |
| OP-11 | `POST /message/v1/admin/conversations/read` | `MarkConversationReadReq` | 否 | `operator_role=ADMIN` | `EmptyReq{}` |
| OP-12 | `POST /message/v1/admin/conversations/send` | `AdminSendChatReq{recipient_id,string / conversation_id,string,optional / content}` | 否（`recipient_id`、`content`；**与 OP-04 的差别**） | `sender_role=ADMIN`、`recipient_role=USER`（**硬编码**） | `SendChatResp` |
| OP-13 | `POST /message/v1/admin/notifications/send` | `AdminSendNotificationReq{broadcast,optional / target_id,string,optional / kind,optional / title / content / extra,optional}` | 否（`title`、`content`） | `recipient_ids = broadcast?nil:[target_id]`、`kind=AdminNotificationKind(broadcast,raw)` | `AdminSendNotificationResp` |

**Go 出处**：`internal/types/types.go:5-124`、`internal/logic/message/*.go`、
`internal/logic/messageadmin/*.go`、`internal/logic/shared/helper.go:14-161`。

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§34，与 §2 同结构）

### 3.1 公共骨架（`AbstractMessageApiService#invoke`）

```
Input:  HTTP 请求 + Authorization 头
Precondition: 无
Trigger: 请求到达 DispatcherServlet
Processing（语义顺序）：
   1. Servlet Filter 链（见 §5.1）：验签在 `MessageJwtAuthFilter`（+6）完成；3s 预算在 `HttpMiddlewareFilter`（+7）起算 ⇒ **验签在预算之外**（= DRIFT-MSGAPI-014）
   2. HandlerInterceptor.preHandle：**消费** Filter 的验签结果（不自己验签）→ 只把 claim `userId` 的**原始对象**塞进 request attribute
   3. @RequestBody(required=false) 反序列化（GoStyleJsonBodyConverter 判 Content-Type）
   4. GoStyleRequest.normalize(req, XxxReq::new)：空 body 归零值 → assertGoRequiredFields()
   5. AbstractMessageApiService#requireIdentity：MessageApiSupport#userId
      失败 ⇒ BusinessException(5004) + observeReject(route, "user_id_missing"|"admin_id_missing")
   6.（仅 sendadminnotification）!broadcast && (targetId==null || targetId<=0) ⇒ 5003 + observeReject
   7. GoStyleRequestDeadline.applyTo(stub) 套 deadline → 调 message-rpc blocking stub
   8. 失败 ⇒ support.rpcError(e)（**只 catch StatusRuntimeException**）+ GoStyleCircuitOpenException 单列
   9. 成功 ⇒ support.to*Resp(proto) → @ResponseBodyAdvice 包 envelope
Result:      HTTP 200 + envelope；失败亦 200
State Change: 无本地状态；micrometer 记账（try/finally 保证异常也记）
Side Effects: 仅 metric
Error:        见 §5.2
Timeout:      3s（`observability.http-timeout: 3s`），由 HttpMiddlewareFilter 执行 + ThreadLocal deadline 传给 stub
Retry:        无（显式禁止，javadoc 留痕）
Concurrency:  Tomcat 线程 + **obs worker 线程池（512，SynchronousQueue，AbortPolicy → 裸 503）**；MaxConns 闸门**全局一个**
Ordering:     Filter 链（含 3s 超时）→ 鉴权 → 解析 → 身份 → 参数 → RPC
Lifecycle:    Spring 上下文；@GrpcClient 由 devh starter 建连；@PreDestroy → HttpMiddleware.shutdown()
```

### 3.2 逐 operation 与 Go 的结构映射

| # | Controller | Service | 差异 |
|---|---|---|---|
| OP-01 | `MessageController#listConversations`（`zeroIfNull`） | `MessageListConversationsService` → `AbstractMessageApiService#listConversations` | role 参数化共用 |
| OP-02 | 同上 `#getConversationMessages` | `MessageGetMessagesService` | |
| OP-03 | 同上 `#markConversationRead` | `MessageMarkConversationReadService` | |
| OP-04 | 同上 `#sendChat` | `MessageSendUserChatService` | |
| OP-05 | 同上 `#listNotifications` | `MessageListNotificationsService` | |
| OP-06 | 同上 `#markNotificationRead` | `MessageMarkNotificationReadService` | |
| OP-07 | 同上 `#markAllNotificationsRead` | `MessageMarkAllNotificationsReadService` | |
| OP-08 | 同上 `#unread`（**`@GetMapping`**） | `MessageGetUnreadSummaryService` | |
| OP-09~11 | `MessageAdminController` | `MessageAdmin*Service` | 同 Abstract 方法 |
| OP-12 | 同上 `#sendChat` | `MessageAdminSendChatService` | |
| OP-13 | 同上 `#sendNotification` | `MessageAdminSendNotificationService` | |

**RAG 出处**：`controller/MessageController.java`、`controller/MessageAdminController.java`、
`service/AbstractMessageApiService.java`、`service/*Service.java`、`service/MessageApiSupport.java`。

---

## 4. Equivalence Matrix（§24 + §35）

> Result 只用 5 个枚举值。Evidence 列：`P` = 真源-双侧探针（56 例，见 §8）；
> `S` = 静态源码；`RT` = 真容器 / RAG 单侧运行。

### 4.1 请求解析（`internal/types/types.go` ↔ `dto/*`）

| Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|
| OP-01/05/09 | `PageReq.offset` 收 JSON 字符串 | 拒 | 拒 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.offset` 收 JSON 数字 | 收→值生效 | 收→值生效 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.offset` 收 JSON 浮点 `1.5` / `1.0` | 拒 | 拒 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.offset` 超 int32 范围 | 拒 | 拒 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.offset` **显式 null** | **收→0** | **拒（FAIL_ON_NULL_FOR_PRIMITIVES）** | **SEMANTIC MISMATCH** | P |
| OP-01/05/09 | `PageReq.unread_only` 收 JSON 字符串 | 拒 | 拒 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.unread_only` 收 JSON **整数** | **拒** | **收→true/false** | **SEMANTIC MISMATCH** | P |
| OP-01/05/09 | `PageReq.unread_only` 收 JSON 浮点 | 拒 | 拒 | EQUIVALENT | P |
| OP-01/05/09 | `PageReq.unread_only` **显式 null** | **收→false** | **拒** | **SEMANTIC MISMATCH** | P |
| OP-02/10 | `conversation_id` `,string` 收 JSON 字符串 | 收 | 收 | EQUIVALENT | P |
| OP-02/10 | `conversation_id` `,string` 收 JSON **数字** | 收 | 收 | EQUIVALENT | P |
| OP-02/10 | `conversation_id` `,string` 收 JSON 浮点 / bool / 超范围 | 拒 | 拒 | EQUIVALENT | P |
| OP-02/10 | `conversation_id` `,string` 收 `"+123"` / `"0012"` | 收 | 收（值 123 / 12 相同） | EQUIVALENT | P |
| OP-02/10 | `conversation_id` `,string` 收 `" 12"` / `""` / `"0x10"` | 拒 | 拒 | EQUIVALENT | P |
| OP-02/10 | `conversation_id` 缺席（必填） | 拒 | 拒 | EQUIVALENT | P |
| OP-02/10 | `conversation_id` 显式 null（必填） | 拒 | 拒（文案不同） | IMPLEMENTATION DIFFERENCE | P |
| OP-04 | `UserSendChatReq.recipient_id/conversation_id` `,string,optional` | 收 string+number | 收 string+number | EQUIVALENT | P（`target_id` 代理覆盖） |
| OP-04 | `content` 收 JSON 数字 / bool | 拒 | 拒 | EQUIVALENT | P |
| OP-04 | `content` 收空串 | 收→`""` | 收→`""` | EQUIVALENT | P |
| OP-12 | `AdminSendChatReq.recipient_id` 必填 | 拒（缺席） | 拒 | EQUIVALENT | S |
| OP-13 | `broadcast` 收 JSON 字符串 | 拒 | 拒 | EQUIVALENT | P |
| OP-13 | `broadcast` 收 JSON **整数** | **拒** | **收** | **SEMANTIC MISMATCH** | P |
| OP-13 | `broadcast` **显式 null** | **收→false** | **拒** | **SEMANTIC MISMATCH** | P |
| OP-13 | `kind` 收 JSON 字符串 | 拒 | 拒 | EQUIVALENT | P |
| OP-13 | `kind` 收负数 / 超 int32 | 收 / 拒 | 收 / 拒 | EQUIVALENT | P |
| OP-13 | `kind` **显式 null** | **收→0** | **拒** | **SEMANTIC MISMATCH** | P |
| OP-13 | `extra` 缺席 | 收→nil | 收→null | EQUIVALENT | P |
| OP-13 | `extra` **显式 null** | 收→nil | 收→null | EQUIVALENT | P |
| OP-13 | `extra` 值收 JSON **数字** | **收→字符串化** | **拒** | **SEMANTIC MISMATCH** | P |
| OP-13 | `extra` 值收 JSON **bool** | 拒 | 拒 | EQUIVALENT | P |
| OP-13 | `extra` 值收 JSON **null** | **拒** | **收→null** | **SEMANTIC MISMATCH** | P |
| OP-13 | `extra` 值收空串 | 收 | 收 | EQUIVALENT | P |
| OP-13 | `extra` 收 JSON 字符串 / 数组 | 拒 | 拒 | EQUIVALENT | P |
| OP-13 | `extra` 数字键 `{"1":"a"}` | 收 | 收 | EQUIVALENT | P |
| 全部 | 未知字段 | 忽略 | 忽略（Jackson3 `FAIL_ON_UNKNOWN_PROPERTIES=false`） | EQUIVALENT | P |
| 全部 | 空 body（`Content-Length: 0`） | 走必填校验 | `required=false` + 零值归一 + 必填校验 | EQUIVALENT | S |
| 全部 | 非 JSON `Content-Type` | 丢 body 按空对象 | `GoStyleJsonBodyConverter` 同判据 | EQUIVALENT | RT |

### 4.2 响应序列化

| Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|
| 全部 | `,string` 出参（id 三兄弟等 12 个字段） | **JSON 字符串** | **JSON 字符串**（`@JsonFormat(STRING)`） | **EQUIVALENT** | P（本轮新覆盖：出参方向） |
| OP-13 | `affected` 出参 | **JSON 数字** `3` | **JSON 字符串** `"3"` | **SEMANTIC MISMATCH** | P |
| 全部 | 空 `items` | `[]` | `[]` | EQUIVALENT | P |
| 全部 | nil map（`extra`） | `null` | `null` | EQUIVALENT | P |
| OP-04/12 | `message` 缺失 | 全零值对象（非 null） | 全零值对象 | EQUIVALENT | P |
| 全部 | `,string` 之外的计数/时间字段 | 数字 | 数字 | EQUIVALENT | P |
| 全部 | `extra` 键序 | `encoding/json` **排序**（确定） | `LinkedHashMap` **保序**（不确定） | OBSERVABLE DIFFERENCE | P（字节级） |
| 全部 | 成功响应 `Content-Type` | `application/json; charset=utf-8` | `application/json`（**无 charset**） | **OBSERVABLE DIFFERENCE** | RT（转换器级） |
| 全部 | 错误响应 `Content-Type` | 同上 | 同上 | OBSERVABLE DIFFERENCE | RT |

### 4.3 鉴权 / 身份

| Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|
| 密钥来源 | yaml `UserAuth/AdminAuth.AccessSecret` | `message-api.user-auth/admin-auth.access-secret` | EQUIVALENT | S |
| 密钥长度 | admin 15 字节，golang-jwt 不校验 | jjwt 要求 ≥32B → zero-pad | IMPLEMENTATION DIFFERENCE（已签字 N-5） | S |
| 两套密钥互不通用 | 是（两段独立 `WithJwt`） | 是（excludePathPatterns） | EQUIVALENT | S |
| Bearer 前缀剥离 | 不匹配时**原样返回整串** | 同 | EQUIVALENT | S |
| claim 数值类型 | `json.Number`（`jwt.WithJSONNumber()`） | jjwt 的 `Integer`/`Long`/`BigInteger` | EQUIVALENT（映射后同） | S |
| claim 缺失 / 字符串 / 布尔 / 浮点 / 超 long | 5004（**HTTP 200**） | 5004（HTTP 200） | EQUIVALENT | S |
| claim = 0 或负数 | 透传给 rpc | 透传给 rpc | EQUIVALENT | S |
| 未匹配路径 | 404（**不过 auth**） | 404（`handler instanceof HandlerMethod` 判据） | EQUIVALENT | RT |
| 401 响应 | 空 body、无 Content-Type | 空 body、无 Content-Type | EQUIVALENT | RT |

### 4.4 依赖 / 外部交互 / 失败传播

| Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|
| 出向调用形态 | blocking（无 goroutine / errgroup） | `MessageServiceBlockingStub` | EQUIVALENT | S |
| 出向调用数 | 每次请求恰好 1 次 | 恰好 1 次 | EQUIVALENT | S |
| retry | 无 | 无（显式禁止） | EQUIVALENT | S |
| client 熔断 | 开（`go-zero@v1.10.2/zrpc/internal/config.go:14` default=true） | 开（`GoStyleGrpcClientBreakerInterceptor`） | EQUIVALENT | S |
| 熔断 acceptable 判据 | `codes.Acceptable` 6 个失败 code | 逐条照抄 | EQUIVALENT | S |
| 熔断拒绝时的业务码 | 1015 + `"service busy"`（**普通 error，非 status**） | 1015 + `"service busy"` | EQUIVALENT | S |
| gRPC 错误 → 业务码 | `BizCodeFromError`：先按 description 反查码表，再按 code class 兜底 | `MessageGrpcError.bizCodeFromError` 同 | EQUIVALENT（**依赖 message-common，属另一会话审查范围**） | S |
| 未被 catch 的 gRPC status | 落 error handler 兜底 → 5001 | `handleRpcStatus` → 5001 | EQUIVALENT | S |
| 身份缺失 vs 参数非法顺序 | 身份（5004）先于参数（5003） | 同 | EQUIVALENT | RT |
| 下游 5xx / 超时 | ctx 到期 → `DEADLINE_EXCEEDED` → 计入熔断 | stub deadline → `DEADLINE_EXCEEDED` | EQUIVALENT（设计已对齐，**未活体对拍**） | S |

### 4.5 保护层 / 生命周期 / 配置

| Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|
| MaxBytes 413（裸） | 1 MiB，在 auth **之外** | 1 MiB Servlet Filter | EQUIVALENT | RT（RAG 真 Tomcat） |
| MaxConns 503（裸） | **每路由一个** latch，10000 | **全局一个**，10000 | OBSERVABLE DIFFERENCE（已留痕，低影响） | S |
| CPU 降载 503（裸） | threshold 900‰，`-1` fail-open | 同 | EQUIVALENT | S |
| HTTP 熔断 503（裸） | `cw.Code < 500 ⇒ Accept` | 同 | EQUIVALENT | S |
| **worker 池饱和 503（裸）** | **无此路径**（Go 是每请求 goroutine） | **有**：512 上限，`AbortPolicy` | **OBSERVABLE DIFFERENCE（RAG 新增准入控制）** | S |
| 3s 超时 503 | `http.Error(w,"Request Timeout",503)` + `text/plain; charset=utf-8` + `\n` | `writeTimeoutResponse` 同形状 | EQUIVALENT（**但 503 分支无任何运行证据**） | S |
| **超时作用域** | 中间件在 **auth 之后**，只覆盖「解析 + 业务」 | Filter 在 **budget 层之外**（验签已搬出），覆盖「解析 + 业务」 | ✅ EQUIVALENT（🔴 2026-10-27：原写「RAG 在最外层，覆盖鉴权」= **已消除**，`DRIFT-MSGAPI-014` 修复后两侧范围一致） | S + **L4 活体探针** |
| 事务边界 | 无（api 不碰 DB） | 无（无 `@Transactional`） | EQUIVALENT | S |
| 观测端点 | `:16066/metrics` + `/healthz` + pprof（`DevServer`） | `:8892/actuator/{health,info,prometheus}` | OBSERVABLE DIFFERENCE（运维面） | S |
| 启动失败模式 | 无 etcd → `MustNewClient` **panic** | Nacos 不可达 → 启动期/首调失败 | OBSERVABLE DIFFERENCE | S |

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 中间件 / 拦截顺序（这是本组件最容易被"类比"错的一层）

**Go（仅对已匹配路由生效，`go-zero@v1.10.2/rest/engine.go:101-116` + `:130-171`）**，由外到内：

```
Trace → Log → Prometheus → MaxConns(503) → Breaker(503) → Shedding(503)
      → Timeout  ← 被 observability.DisableNativeRestTimeout 关掉
      → Recover(500) → Metrics → MaxBytes(413) → Gunzip
      → auth(401)                       ← appendAuthHandler (engine.go:108)
      → server.Use 的 observability 中间件（**3s + ctx 传播**，覆盖「解析 + 业务」）
      → route.Handler（httpx.Parse → logic）
```
⚠️ 未匹配路径**完全不进这条链**（`router/patrouter.go:73,79`）。

**RAG**：

```
Tomcat
 → GoStyleTraceparentFilter（@Order HIGHEST_PRECEDENCE+3，起 SERVER span）
 → GoStyleMaxConnsFilter（+4）
 → GoStyleBreakerFilter（+5）
 → GoStyleCpuSheddingFilter（+6）
 → MessageJwtAuthFilter（+6，**JWT 验签**；只写 request attribute、**绝不写响应**）
 → HttpMiddlewareFilter（+7，3s 超时 + ThreadLocal deadline ＝ **3s 预算起算点**）
 → GoStyleMaxBytesFilter（+8，413）
 → DispatcherServlet（404/405 在此产生）
 → HandlerInterceptor（**消费** Filter 的验签结果 → 401）
 → GoStyleJsonBodyConverter 解析
 → Service
```

> 🔴 **2026-10-27 订正**：本链图的槽位数字此前写的是「HttpMiddlewareFilter ＝ `HIGHEST_PRECEDENCE+5`」，
> **与实测不符**（实测 **+7**）⇒ 连带把「保护层 ×4 排在预算层之后」这个错误顺序也带进来了。
> 现按 `service/common` 各 Filter 的 `@Order` **实测值**重排（+3/+4/+5/+6/+6/+7/+8）。
> ⚠️ 槽位是**共享层**的事实，G4 写权；域侧只**引用**（实测自 `service/common` 源码）。
>
> ⚠️ 另注：`MessageJwtAuthFilter` 与 `GoStyleCpuSheddingFilter` **同为 +6**（并列）⇒
> 两者先后由容器装配顺序决定；**这不影响任何响应**，因为 JWT Filter **绝不写响应**。

**可观察后果（已逐条核对）**：
1. **413 压过 401**：两侧一致 ✅（Go 靠 chain 顺序，RAG 靠 Filter 在 DispatcherServlet 之外）
2. **404/405 不过 auth、不过保护层**：两侧一致 ✅
3. ✅ **超时范围已一致**（2026-10-27 · **DRIFT-MSGAPI-014** 修复）：Go 的 3s **不含**鉴权（Authorize 在 observability(3s) 之外）；
   RAG 此前把验签放在 `HandlerInterceptor`（DispatcherServlet 内）⇒ 3s **含**鉴权 ⇒ 现已把验签搬到
   `MessageJwtAuthFilter`（+6，在预算 Filter +7 **之外**）⇒ **两侧的 3s 预算范围一致**（验签在预算之外）
4. ⚠️ **worker 池饱和 503 只在 RAG 存在**（见 §4.5）

### 5.2 Error flow 逐条

| 触发条件 | Go 输出 | RAG 输出 | 等价？ |
|---|---|---|---|
| 非法 JSON / 标量类型不匹配 / body 畸形 | 200 + `{code:5001,msg:"internal server error",data:null}` | 同 | ✅ |
| 必填字段缺席 / 显式 null | 同上 | 同上 | ✅ |
| 签名错 / 过期 / 缺 Authorization | **401 + 空 body** | 同 | ✅ |
| `userId` claim 缺失/非整型 | 200 + `{code:5004}` | 同 | ✅ |
| `target_id<=0` 且非广播 | 200 + `{code:5003,msg:"invalid parameter"}` | 同 | ✅ |
| rpc 返回业务错误（InvalidArgument/NotFound/PermissionDenied） | 200 + 对应业务码 + **description 原文作 msg** | 同 | ✅（依赖 message-common） |
| rpc 返回 5xx code / 超时 | 熔断累计 → 1015 + `"service busy"` | 同 | ✅（设计对齐） |
| 未匹配路径 | 404 + `"404 page not found\n"` + `text/plain; charset=utf-8` + nosniff | 同（共享实现） | ✅ |
| 方法不匹配 | 405 + `Allow` 头，**无 body 无 CT** | 同 | ✅ |
| Content-Type 非 JSON | **丢 body 按空对象**（零必填 DTO → 成功） | 同 | ✅ |
| 任何其它异常 | 200 + 5001 | 200 + 5001 | ✅ |

**错误「继续 / 终止 / 重试 / 回退」的分类**：Go 与 RAG **全部**是 `Error → Terminate(当前请求)`，
无一条走 fallback 或 retry（业务错误也直接返回给客户端）。⇒ §10 判定 `EQUIVALENT`。

---

## 6. 逐维度结论（§12–§21）

| § | 维度 | 结论 | 依据 |
|---|---|---|---|
| 12 | **State Semantics** | ✅ EQUIVALENT | 无业务状态；metric 计数时序（成功失败都记 `request_total`）两侧一致（`AbstractMessageApiService#invoke` 的 `try/finally` ↔ Go 的 `defer`） |
| 13 | **Cache Semantic Equivalence** | ➖ N/A | 本组件**无缓存**（Go 侧无 redis client，RAG 侧无 `@Cacheable`）。唯一带 Cache 字样的是熔断器的滑动窗口计数器，语义已逐条对齐（§4.4） |
| 14 | **Auth/Authz** | ✅ EQUIVALENT | §4.3 全表；证据强度 `静态源码`（**无活体对拍**） |
| 15 | **Transaction** | ✅ EQUIVALENT | 两侧都无事务边界（api 层不碰 DB） |
| 16 | **Concurrency** | ⚠️ OBSERVABLE DIFFERENCE | ① worker 池 512 提前拒绝（RAG 新增准入）② MaxConns 全局 vs 每路由 ③ Go goroutine 无上界 vs RAG 线程池 |
| 17 | **Timeout** | ✅ EQUIVALENT（🔴 2026-10-27 更新：原写「⚠️ OBSERVABLE DIFFERENCE · 作用域不同」，**已消除** —— `DRIFT-MSGAPI-014` 修复后验签搬出 3s 预算 ⇒ 两侧作用域一致（Go 在 auth 后 / RAG 同样在 auth 后））；503 分支运行证据见 `MessageApiTimeoutRealTomcatTest` 2 例 |
| 18 | **Retry** | ✅ EQUIVALENT | 两侧都无；RAG 的「禁止加 retry」有 javadoc 留痕并指向 `go-zero@v1.10.2/zrpc/config.go` 无 `Retries` 字段 |
| 19 | **Serialization** | ❌ **NOT EQUIVALENT** | `-005` ~ `-009` 五条 + 键序差异 + Content-Type charset（§7） |
| 20 | **Configuration** | ⚠️ 部分 OBSERVABLE | yaml → `@ConfigurationProperties` 逐字段对齐（含**两侧都不读**的 `accessExpire`）；但 §3.3.1 的默认值全在框架 tag 里，本轮逐条核对了 MaxBytes/MaxConns/CpuThreshold/Breaker/Timeout 五处 ✅；启动失败模式不同（panic vs 延迟失败） |
| 21 | **Lifecycle** | ⚠️ 部分 OBSERVABLE | 初始化顺序对齐（配置 → 服务上下文 → 路由）；`metrics.InitMetrics` 的 `sync.Once` 对应 Micrometer 按需注册 ✅；但 §6.3 问题 4 的 Flyway 账本问题**不属本组件**（api 无 DDL） |

---

## 7. Semantic Drifts（§27 + §36）

### 7.0 复核结论：DRIFT-MSGAPI-001 / -002 / -003 **仍然关闭**

**独立复核方式**：不复用首轮的 23 例，而是本轮新建一套 56 例双侧探针
（Go 侧仍跑 go-zero v1.10.2 `core/mapping` 的真源解析，RAG 侧仍跑**真实 DTO + 真实
`MessageGoStyleCoercion`**），把 `,string` 的**入参 6 个形态 + 出参 1 个形态**全部重跑。

- **DRIFT-001**（`conversation_id` 收 JSON 数字）→ 两侧都 ACCEPT，**已关闭** ✅
- **DRIFT-002**（无 `,string` 字段收 JSON 字符串）→ 两侧都 REJECT，**已关闭** ✅
- **DRIFT-003**（`extra` 显式 null）→ 两侧都 ACCEPT，**已关闭** ✅
- ⚠️ **`GoStyleStringInt64` 的 5 个边界形态**（`"+123"` / `"0012"` / `" 12"` / `""` / `"0x10"` / `1e2`）
  两侧 6/6 一致 —— 这 6 条首轮**没有**任何用例覆盖，本轮补测后一致。
- ⚠️ **出参方向的 `,string`**（首轮 probe README 自认「未覆盖」）→ 本轮补测：
  Go `{"conversation_id":"1234567890123",...}` vs RAG `{"conversation_id":"1234567890123",...}`
  **逐字段一致**（`@JsonFormat(STRING)` 在 Jackson 3 下生效）。

⇒ **首轮的核心结论成立；但首轮「已全部关闭」的覆盖面明显小于实际请求解析面**，
见下面 5 条。

---

### ✅ DRIFT-MSGAPI-005 ✅ **已修（2026-10-23 第三轮）** 布尔字段收 JSON **整数**：Go 拒 / RAG 收

> **⚠️ 2026-10-24 状态更正**：本节标题原写「未修」，**实为已修**——代码里早有「补强 3（2026-10-23，DRIFT-MSGAPI-005）」（`MessageGoStyleCoercion` 的
> `LogicalType.Boolean + CoercionInputShape.Integer → Fail`），探针 A 组 7 例**两侧全部 REJECTED**，
> `MessageApiMvcPipelineTest#adminBroadcastRejectsJsonNumber` 也已锁 ⇒ **代码早于台账**。
> 详见下方「2026-10-23 第三轮」第二节。

- **Component**：`message-api` 请求解析
- **Operation**：OP-01 / OP-05 / OP-09（`PageReq.unread_only`）、OP-13（`AdminSendNotificationReq.broadcast`）
- **Category**：Serialization / Input Interpretation
- **Go Behavior**：`{"unread_only":1}` → **REJECTED**
  `type mismatch for field "unread_only", expect "bool", actual "number"`
- **Java Behavior**：`{"unread_only":1}` → **ACCEPTED**，落库为 `true`（`2` 也落 `true`，`0` 落 `false`）
- **Difference**：RAG 把 JSON 数字静默转成布尔；Go 一律拒。
- **Why It Matters**：`unread_only=1` 在 Go 上是 5001（客户端 bug 立刻暴露），在 RAG 上是「只看未读」——
  **一个本该报错的请求变成了成功的查询**，且返回的是**过滤后的**列表，调用方无从察觉。
  `broadcast=1` 更危险：它会把**单播请求变成广播**，向全站用户发通知。
- **Observable Impact**：`POST /message/v1/notifications/list`、`POST /message/v1/admin/notifications/send`
  的请求体形状两侧不同。
- **Business/Technical Impact**：RAG 新增了一条 Go 没有的接受路径；`broadcast` 路径有误发全量的风险。
- **Likely Cause**：`MessageGoStyleCoercion` 只补了 `LogicalType.Boolean, CoercionInputShape.String=Fail`，
  **没补 `CoercionInputShape.Integer=Fail`** —— 而 Jackson 对 `Boolean←Integer` 的默认动作是
  `TryConvert`（不是 Fail）。
- **Must Preserve**：无 `,string` 的布尔字段只接受 JSON `true` / `false`。
- **Recommended Verification**：往 `tools/semantic-probe` 补 6 例
  （`unread_only` 与 `broadcast` × `1`/`0`/`1.0`），并补 6 条 MVC 用例。
- **Evidence**：
  - Go：`go-zero@v1.10.2/core/mapping/unmarshaler.go:648-650`（`case json.Number` 先于 `typeKind == valueKind` 判定）
    → `:664-715` `processFieldPrimitiveWithJSONNumber` 的 `switch typeKind` **没有 `reflect.Bool` 分支**
    → `:708-709` `default: return newTypeMismatchErrorWithHint(...)`。
    消费点即此行，不是 struct tag 声明行。
  - RAG：`config/MessageGoStyleCoercion.java` 的 `applyTo` 只对
    `LogicalType.Integer` / `LogicalType.Boolean` 设了 `CoercionInputShape.String` 一条。
  - **实测（`真源-双侧探针`，本轮 56 例之 A1–A3 / A7–A8）**：
    ```
    A1 unread_only 收 JSON 数字 1 | Go: REJECTED (type mismatch … expect "bool", actual "number") | RAG: ACCEPTED → true
    A2 unread_only 收 JSON 数字 0 | Go: REJECTED | RAG: ACCEPTED → false
    A3 unread_only 收 JSON 数字 2 | Go: REJECTED | RAG: ACCEPTED → true
    A7 broadcast 收 JSON 数字 1   | Go: REJECTED | RAG: ACCEPTED → true
    A8 broadcast 收 JSON 数字 0   | Go: REJECTED | RAG: ACCEPTED → false
    ```
  - **Classification**：**SEMANTIC MISMATCH** · Risk: **HIGH**（`broadcast` 误触发全量推送）

---

### ✅ DRIFT-MSGAPI-006 ✅ **已修（2026-10-23 第三轮）** `map[string]string` **值**收 JSON 数字：Go 收 / RAG 拒

> **⚠️ 2026-10-24 状态更正**：标题原写「未修」，**实为已修**——新增 `GoStyleMapStringValueDeserializer`
> （字段级 `contentUsing` 挂点）。⚠️ 修法**不能**放在共享 coercion 规则里（Jackson 的 `LogicalType` 维度
> **无法区分**顶层 String 字段与 map 元素 ⇒ 放行 map 元素会让 `DRIFT-MSGAPI-002` 回退）。
> 详见下方「2026-10-23 第三轮」第三节。

- **Component**：`message-api` 请求解析
- **Operation**：OP-13（`AdminSendNotificationReq.extra`）
- **Category**：Serialization
- **Go Behavior**：`{"extra":{"k":1}}` → **ACCEPTED**，map 值落为字符串 `"1"`
- **Java Behavior**：**REJECTED**（`Cannot coerce Integer value (1) to java.lang.String`）
- **Difference**：方向与 DRIFT-002 相反 —— Go 对 map 元素的类型判据**比顶层标量字段更宽松**。
- **Why It Matters**：`extra` 会被原样写进 `pb.SendNotificationReq.Extra` 并落库。
  客户端在 Go 上能用的 `{"extra":{"article_id":123}}`，在 RAG 上直接 5001。
- **Observable Impact**：`POST /message/v1/admin/notifications/send`。
- **Likely Cause**：`GoStyleCoercion` 的规则 1 是 `LogicalType.Textual, Integer/Float/Boolean → Fail`，
  它**对顶层字段和 map 值一视同仁**；但 Go 侧这两条路径根本不是同一段代码
  （见 Evidence 的两个不同消费点），对 map 元素必须**撤销**规则 1 的 Integer/Float 分支。
- **Must Preserve**：`map[string]string` 的值接受 JSON 字符串**与数字**（数字走字符串化），
  拒绝 bool / null / 嵌套。
- **Recommended Verification**：探针补 3 例（`1` / `1.5` / `true`），并给 `extra` 的 DTO 加局部豁免。
- **Evidence**：
  - Go：`go-zero@v1.10.2/core/mapping/unmarshaler.go:385-410` `generateMap` 的 `default:` 分支里**显式有**
    `case json.Number: setValueFromString(dereffedElemKind, target.Elem(), v.String())`
    ⇒ `convertTypeFromString(String, "1")` 返回 `"1"`（`go-zero@v1.10.2/core/mapping/utils.go:138-139`）。
  - 对照：顶层标量字段走的是 `processFieldPrimitiveWithJSONNumber`（`unmarshaler.go:664-715`），
    `default:` 分支直接报错。**两个判据不一致，是本条的全部根因。**
  - **实测（`真源-双侧探针` B1）**：`Go ACCEPTED | RAG REJECTED (Cannot coerce Integer value (1) …)`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: MEDIUM

---

### ✅ DRIFT-MSGAPI-007 ✅ **已修（2026-10-23 第三轮）** `map[string]string` **值**显式 null：Go 拒 / RAG 收

> **⚠️ 2026-10-24 状态更正**：标题原写「未修」，**实为已修**。
> 🔴 **修它踩的 Jackson 细节**：map 元素为 `null` 时 Jackson **不走 `deserialize`**，而走 `getNullValue`
> ⇒ **只覆写 `deserialize` 不会被修掉**，必须同时覆写 `getNullValue` 抛错；且 `getNullValue` 里
> **不能**调 `handleUnexpectedToken(…, p)`（该路径 Jackson 没给 parser，传 `null` 会 NPE）。
> 详见下方「2026-10-23 第三轮」第三节。

- **Component**：`message-api` 请求解析
- **Operation**：OP-13（`AdminSendNotificationReq.extra`）
- **Go Behavior**：`{"extra":{"k":null}}` → **REJECTED**（`type mismatch`）
- **Java Behavior**：**ACCEPTED**，map 落为 `{"k": null}`
- **Difference**：
  ⚠️ 与 DRIFT-003 **方向相反**：字段级 `extra: null` 两侧都接受（`,optional`），
  但**元素级 `{"k":null}` 两侧都应拒绝**。RAG 放行了。
- **Why It Matters**：`extra` 原样进 proto → 落库后 Go 侧读回来的 map 里这个 key 不存在，
  RAG 侧却多一个 null 值。落库内容与读取结果两侧不一致。
- **Likely Cause**：Jackson 对 `Map<String,String>` 的 null 值默认放行；Go 的
  `generateMap` `default:` 分支（`unmarshaler.go:411-417`）对 `nil` 落到
  `keythValue.Kind() == reflect.Invalid ≠ String` ⇒ `errTypeMismatch`。
- **Must Preserve**：`extra` 的**值**为 null 时整条解析失败。
- **Evidence**：
  - Go：`go-zero@v1.10.2/core/mapping/unmarshaler.go:411-417`（`default:` + `dereffedElemKind != keythValue.Kind()`）
  - **实测（`真源-双侧探针` B3）**：`Go REJECTED (type mismatch) | RAG ACCEPTED`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: LOW-MEDIUM

---

### ✅ DRIFT-MSGAPI-008 ✅ **已修（2026-10-23 第三轮）** `,optional` 标量字段**显式 null**：Go 收（零值）/ RAG 拒

> **⚠️ 2026-10-24 状态更正**：标题原写「未修」，**实为已修**——关掉 Jackson 的
> `FAIL_ON_NULL_FOR_PRIMITIVES`（默认 true ⇒ `{"offset":null}` 等全被拒）。
> ⚠️ **为什么全局关掉是安全的**（逐字段核过 `types.go`，非拍脑袋）：本模块**没有任何必填的原始类型字段**；
> 必填的 `title`/`content`/`conversation_id` **全是引用类型**，其显式 null 的拒绝靠 `GoStyleRequired`
> （Controller 层），**不靠这个 feature**。详见下方「2026-10-23 第三轮」第四节。

- **Component**：`message-api` 请求解析
- **Operation**：OP-01/05/09（`PageReq.offset` / `limit` / `unread_only`）、OP-13（`kind`）
- **Go Behavior**：`{"offset":null}` → **ACCEPTED**，值 0（`unread_only:null` → false，`kind:null` → 0）
- **Java Behavior**：**REJECTED**
  `Cannot map null into type int (set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)`
- **Difference**：这是**§3.3.1 同款病灶的第二个实例** —— 又一个「框架默认值与 Go 相反」的开关。
  Jackson 3 的 `FAIL_ON_NULL_FOR_PRIMITIVES` **默认 true**；
  go-zero 的 `,optional` 对显式 null 的处理是 `if mapValue == nil { if optional { return nil } }`。
- **Why It Matters**：
  - 分页参数：客户端序列化时把 `null` 当「未设置」发出是很常见的写法，Go 上正常，RAG 上 5001。
  - `kind: null`：Go 落 0 ⇒ `AdminNotificationKind` 走 broadcast 推断；RAG 直接拒。
- **Likely Cause**：`MessageGoStyleCoercion` 只处理了 `CoercionAction`（形状→动作），
  **没有动 `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` 这个正交开关**。
  ⚠️ 注意它**不能**简单地全局关掉：Go 对**必填**字段的显式 null 是拒绝的
  （`unmarshaler.go:862`），而必填判定在 RAG 侧靠 `GoStyleRequired`（只看值是否为 null）——
  关掉之后必填字段仍会被 `GoStyleRequired.field(...)` 拦住，**所以全局关掉是安全的**
  （两条路径的拒绝文案本就不可观测，见首轮 §0 的说明）。
- **Must Preserve**：`,optional` 标量字段的显式 null = 零值（不报错）。
- **Recommended Verification**：探针补 3 例 + MVC 3 例；并补一条「关掉该 feature 后
  必填字段显式 null 仍然 5001」的守门（防修这条时把 DRIFT-003 的结论改回去）。
- **Evidence**：
  - Go：`go-zero@v1.10.2/core/mapping/unmarshaler.go:854-863`
    ```go
    if mapValue == nil { if opts.optional() { return nil }
                         return fmt.Errorf("field %q mustn't be nil", key) }
    ```
  - **实测（`真源-双侧探针` C5 / C6 / D4）**：
    `Go ACCEPTED | RAG REJECTED (Cannot map null into type int …)`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: MEDIUM

---

### ⏸ DRIFT-MSGAPI-009 ⏸ **已签字（`SD-2026-10-23-03`）· 不是待修项** 出参 `affected`：Go **数字** / RAG **字符串**

> **⚠️ 2026-10-24 状态更正**：标题原写「未修 · 必须改成 JSON 数字」，**该结论已作废**——
> 用户已逐条拍板「**保留 RAG 的字符串形态，不改 wire 契约**」（`SD-2026-10-23-03`）。
> ⇒ **按 Go 改反而是制造未受控漂移**。探针把 `X1`/`X2` 放进 `known-dev` 白名单 ⇒ 对拍时必然为红但不计失败。
> 详见下方「2026-10-23 第三轮」第五节。
**🔴 本项目第二次「绿测试守卫错误答案」，形态与首轮 DRIFT-MSGAPI-003 完全同构**（⚠️ **2026-10-29 对齐**：`DRIFT-MSGAPI-003` **本身已 ✅ 已修**（见本文件 `:50`）⇒ 此处说的是**第二次复发**的**新**缺陷，**不是** `-003` 的状态）

- **Component**：`message-api` 响应序列化
- **Operation**：OP-13（`POST /message/v1/admin/notifications/send` 的响应）
- **Category**：Serialization（wire contract）
- **Go Behavior**：`{"code":200,"msg":"OK","data":{"affected":3}}`
- **Java Behavior**：`{"code":200,"msg":"OK","data":{"affected":"3"}}`
- **Difference**：`affected` 是 JSON **数字**还是**字符串**。
- **Why It Matters**：类型契约变了。Go 客户端（`map[string]any` / `float64`）在 RAG 上拿到 string；
  强类型客户端（Go struct / TS）直接反序列化失败。
- **Likely Cause**：**DTO 的 javadoc 手抄 Go tag 时多加了一个 `,string`。**
  Go 真源 `internal/types/types.go:21-23`：
  ```go
  type AdminSendNotificationResp struct {
      Affected int64 `json:"affected"`      // ← 没有 ,string
  }
  ```
  RAG 侧 `dto/AdminSendNotificationResp.java:13` 的 javadoc 却写成
  `Affected int64 \`json:"affected,string"\`` 并据此加了 `@JsonFormat(STRING)`。
  ⚠️ `types.go` 里**唯一**没有 `,string` 的 int64 出参就是它；
  其余 12 个带 `,string` 的出参字段（`conversation_id` / `peer_id` / `id` 三兄弟 /
  `pending_sender_id` / `sender_id` / `recipient_id` / `created_at` 之外的计数）RAG 全部标对了
  （本轮出参探针逐条验证，见 §4.2）。
- **Observable Impact**：`data.affected` 的 JSON 类型（number ↔ string）。
- **Classification**：**SEMANTIC MISMATCH** · Risk: **MEDIUM**（wire contract）
- **🔴 为什么至今没被发现**：
  - `MessageApiMvcPipelineTest#adminSendNotificationAffectedIsString`（**第 749 行**）
    的 `@DisplayName` 直接写着「**affected 是字符串**」，断言
    `jsonPath("$.data.affected").value("3")` —— **这条绿测试正把错误答案锁死**。
  - 首轮的 `tools/semantic-probe` README **自己写了**「出参方向的 `,string`（响应序列化成字符串）
    **未覆盖** —— 本探针只测请求解析」。⇒ 两套网恰好都漏掉了这一面。
- **Must Preserve**：`affected` 是 JSON 数字（Go tag 无 `,string`）。
- **Recommended Verification**：
  1. **先改/删错误断言**（`AGENTS.md` §3.3 强制三条之③：先改测试，再改实现），
  2. 再去掉 `@JsonFormat(Shape.STRING)`、改正 DTO javadoc，
  3. 给 `tools/semantic-probe` 增加**出参序列化**用例面（当前完全没有）。
- **Evidence**：
  - Go：`service/message/api/internal/types/types.go:21-23`（tag 声明）
    + `encoding/json` 对无 `,string` 的 `int64` 输出数字。
  - **实测（`真源-双侧探针`，Go `jsonx.Marshal` vs RAG 真实 mapper 序列化）**：
    ```
    X1 affected=3 | Go: {"affected":3}      | RAG: {"affected":"3"}
    X2 affected=0 | Go: {"affected":0}      | RAG: {"affected":"0"}
    ```
  - **Classification**：**SEMANTIC MISMATCH**

---

### ✅ DRIFT-MSGAPI-010 ✅ **仍 OPEN，但 2026-10-24 已在真容器级确认并细化** 成功 / 错误响应的 `Content-Type` 缺 `charset` ｜ **2026-10-29 对齐：原判 🔴「仍 OPEN」⇒ 现行态 = ✅ 已修（共享层 T-6 已落码）**

> **2026-10-29 对齐 · 依据**：共享层 T-6 第二十五轮已落码 —— `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（常量 `GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）+ `:151`（消费点 `response.getHeaders().set(HttpHeaders.CONTENT_TYPE, GO_JSON_CONTENT_TYPE)`）· 生效链 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2` → `service/message/message-api/pom.xml:64`（共享层 `common` 依赖）· 真 Tomcat 守门 `GoStyleJsonContentTypeCharsetTomcatTest` ⇒ 与 `docs/DRIFT/message/vs-common.md:38` 的 ✅ 一致 ⇒ **证据强度：源码行号 + 装配链 + 真 Tomcat 守门在仓**。


> ⚠️ **2026-10-24 用户拍板：「保持登记，本轮不做」** —— **不是签字**。
> 理由：补 charset 会让**全部 10 个 api** 的 JSON 响应头都变 ⇒ 属跨域共享面改动。
> ⇒ 读法：它**仍是登记在册的待办**，只是这一轮不动；⚠️ **不要**在别的域的会话里「顺手把它修了」。

> **⚠️ 2026-10-24 真容器级取证（第八轮）** —— 本条不再是「只有转换器级证据」：
>
> ```
> Go  message-api（真 net/http）： Content-Type: application/json; charset=utf-8
> RAG message-api（真 Tomcat）  ： Content-Type: application/json              ← 无 charset
> ```
> ⇒ **真容器下确认成立**（此前只有转换器级推断）。
>
> **⚠️ 同时查清了根因，且它把本条拆成了两半**：
> 1. **「缺 charset」这一半是可修的** ⇒ RAG 的 JSON 转换器 `defaultContentType` 就是 `application/json`，
>    补一个带 charset 的 media type 即可逼近 Go。
> 2. **「分号后的空格」那一半在 Tomcat 上不可达** ⇒ ⚠️ **实测**：
>    Tomcat 11 的 `Response.setContentType(String)` 会把 Content-Type **解析成 MediaType 再序列化**，
>    序列化时**吃掉分号后的空格** ⇒ 发出 `application/json;charset=utf-8`；
>    且 **`setHeader("Content-Type", …)` 也一样**（Tomcat 内部对 Content-Type **转手调 `setContentType`**）
>    ⇒ **没有任何写法能产出 Go 的 `application/json; charset=utf-8`** ⇒ 归 **框架限制**，不可修。
>
> ⇒ 故本条的正确表述是：**「charset 缺失」可修；「charset 后的空格」属框架限制（不可修）。**
> ⇒ 修复路径与共享面影响见第八轮第 4 节。

- **Component**：`message-api` 响应头
- **Operation**：全部 13 个（成功与 envelope 错误两条路径）
- **Go Behavior**：`Content-Type: application/json; charset=utf-8`
- **Java Behavior**：`Content-Type: application/json`（**无 charset**）
- **Difference**：响应头逐字节不同。
- **Why It Matters**：低。`application/json` 隐含 UTF-8，绝大多数客户端无影响；
  但严格按 header 断言（部分网关 / 契约测试 / 老客户端）会看到差异。
- **Likely Cause**：Go 在 `doWriteJson` 里**显式**写死 `header.ContentTypeJson`
  （`application/json; charset=utf-8`）；RAG 的 Content-Type 来自
  `AbstractJacksonHttpMessageConverter` 的 `defaultContentType`，
  Spring Framework 6+ 起**刻意不再**给 JSON 补 charset。**业务代码里没有任何一行决定它。**
- **⚠️ 为什么现有 42 条用例抓不到**：`MessageApiMvcPipelineTest` 有 17 处
  `.contentType(MediaType.APPLICATION_JSON)` 断言，而 MockMvc 的
  `ContentTypeResultMatcher` 用的是 `expected.isCompatibleWith(actual)` ——
  `application/json` 与 `application/json;charset=utf-8` **互相 compatible**。
  ⇒ **这些断言对 charset 完全不敏感**（与 `AGENTS.md` §3.3「代码里写了 ≠ 客户端收到」同源）。
- **Classification**：**OBSERVABLE DIFFERENCE** · Risk: LOW
- **Evidence**：
  - Go：`go-zero@v1.10.2/rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)`）
    + `go-zero@v1.10.2/rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"`）
  - RAG：**转换器级运行实测**（用真实 `MessageGoStyleCoercion` 的 mapper 构造
    `AbstractJacksonHttpMessageConverter(mapper, MediaType.APPLICATION_JSON)` 并 `write`）：
    `Content-Type = [application/json]`，`selectedContentType=null` 时同样是 `[application/json]`。
  - ⚠️ 证据边界：**转换器级**，非真容器 HTTP 往返（真 Tomcat 的 413/503 已验，
    但「正常 envelope 响应的完整响应头 dump」仍无）。

---

## 8. Test Cases（§29 + §30）

### 8.1 本轮双侧探针（56 例，`真源-双侧探针`）

Go 半边：`go-zero v1.10.2 core/mapping.UnmarshalJsonReader`（struct 逐字段对齐真源 `types.go`）；
RAG 半边：真实 DTO + 真实 `MessageGoStyleCoercion` + `assertGoRequiredFields()`。
**与首轮 23 例同构，本轮为其超集 + 出参序列化 + 值比对。**

| 组 | 维度 | 例数 | 两侧一致 | 不一致 |
|---|---|---|---|---|
| F（`,string` 入参边界） | 字符串/数字/浮点/bool/超范围/`"+123"`/`"0012"`/`" 12"`/`""`/`"0x10"`/`1e2` | 11 | 11 | 0 |
| A（布尔字段） | `unread_only`/`broadcast` × `1`/`0`/`2`/`1.0`/`1.5`/`true` | 12 | 6 | **6** |
| B（`extra` map） | 值 `1`/`true`/`null`/`""`；整体 字符串/数组；数字键 | 7 | 4 | **3** |
| C（int 范围与 null） | int32 上限±1、负数、显式 null | 6 | 3 | **3** |
| D（`kind`） | 负数、超范围、显式 null、值 | 4 | 3 | **1** |
| E（String 字段） | bool / 空串 / 数组 / 对象 | 4 | 4 | 0 |
| G（`,string,optional`） | null / `"0"` / `"-5"` | 3 | 3 | 0 |
| H（**出参**） | `,string` 序列化 / nil map / 空 items / 零值 `SendChatResp` | 4 | 4 | 0 |
| X（`affected`） | 响应类型 | 2 | 0 | **2** |
| **合计** | | **51** | **28** | **23** |

⚠️ 这 23 条「不一致」**不是 23 个缺陷** —— 它们归并为 §7 的 **5 条 DRIFT**（005~009）
+ 1 条响应头（010）。探针的判定口径仍是 ACCEPTED/REJECTED，
本轮**新增了「解析出的值」的比对**（首轮探针自认只比 ACCEPTED/REJECTED）。

### 8.2 §30 最重要的测试集合适用性

| CASE | 适用？ | 现有覆盖 |
|---|---|---|
| CASE-01 正常成功 | ✅ | `MessageApiMvcPipelineTest` 13 条路由 + 响应形状 |
| CASE-02 空输入 | ✅ | 空 body、`{}`、`null`（本轮补 C5/C6/D4） |
| CASE-03 不存在 | ⚠️ 依赖 rpc | 会话/通知不存在由 rpc 判（不在本组件） |
| CASE-04 重复 | ❌ N/A | api 层无幂等语义 |
| CASE-05 外部依赖失败 | ⚠️ 部分 | `GoStyleGrpcClientBreakerE2ETest`（恒 INTERNAL → 1015）；**断连/超时未测** |
| CASE-06 超时 | ❌ **无覆盖** | 3s 超时 503 分支**至今零运行证据**（§9 UNRESOLVED-004） |
| CASE-07 Retry | ✅（两侧都无） | 无需测 |
| CASE-08/09 Cache | ❌ N/A | 无缓存 |
| CASE-10 权限失败 | ✅ | 两把密钥互不通用 2 例 + claim 异常 6 例 |
| CASE-11 并发 | ⚠️ 部分 | MaxConns 6 例（Filter 级）；**端到端并发未测** |
| CASE-12 重复调用 | ✅ | 无幂等差异 |
| CASE-13 部分成功 | ❌ N/A | api 层无多操作事务 |
| CASE-14 Transaction Rollback | ❌ N/A | 无事务 |
| CASE-15 Serialization Edge Case | ⚠️ **部分（本轮补齐一半）** | 入参：本轮 56 例；出参：本轮首次覆盖（`affected` 被抓出） |

### 8.3 🔴 现有「42 条 MVC + 56 例对拍」**未覆盖**的维度（本轮明确清单）

| # | 未覆盖维度 | 本轮如何处理 | 剩余缺口 |
|---|---|---|---|
| 1 | `AdminSendChatReq.recipient_id`、`MarkConversationReadReq.conversation_id`、`MarkNotificationReq.notification_id`、`UserSendChatReq.recipient_id/conversation_id` 的**逐字段** `,string` 回归 | 探针用 `StrOnlyReq` / `target_id` **代理**覆盖同一代码路径 | 逐字段用例仍缺（首轮 probe README 已自认，本轮未补） |
| 2 | **出参方向的 `,string`** | ✅ 本轮补齐（8 例） | — |
| 3 | **`map[string]string` 的键/值强转** | ✅ 本轮补齐（7 例，抓出 006/007） | — |
| 4 | **响应 `Content-Type` 的 charset** | ⚠️ 转换器级实证（抓出 010） | 真容器 HTTP 往返 dump 未做 |
| 5 | **解析出的**值是否相同（首轮探针只比 ACCEPTED/REJECTED） | ✅ 本轮新增值比对（11 例） | 复杂值（嵌套结构体 / 数组）仍无 —— 本组件 DTO 里也没有 |
| 6 | **3s 超时 503 分支** | ❌ 未做 | 零运行证据；`HttpMiddleware#writeTimeoutResponse` 的 4 个动作（状态码/CT/body/换行）从未被执行过 |
| 7 | **worker 池饱和 503**（512 上限） | ❌ 未做 | RAG 新增路径，无 Go 对照，无运行证据 |
| 8 | **端到端并发 / 过载行为** | ❌ 未做 | MaxConns、CPU 降载、HTTP 熔断都只有组件级单测 |
| 9 | **gRPC 断连 / 超时 / 非 6 个 code 的 status** | ⚠️ 部分（只测 INTERNAL 恒返） | 断连（`UNAVAILABLE`）与 `DEADLINE_EXCEEDED` 未触发过 |
| 10 | **超时作用域差异**（Go 在 auth 后 / RAG 在最外层） | ✅ **2026-10-27 已消除并补上活体证据**（`DRIFT-MSGAPI-014`：验签搬出 3s 预算 ⇒ 两侧范围一致；Go 腿 = 真源 observability 中间件的活体探针：观测点 A `deadline_set=false` / B `deadline_set=true remaining=3s`，**非法 token ⇒ 401 且三个观测点一个都没打印** ⇒ Authorize 在所有 user middleware 之前） | 无活体对拍 → **已补（L4）** |
| 11 | **响应序列化里 map 键序**（Go 排序确定 vs RAG 不确定） | ⚠️ 静态记录（首轮已留档） | 无运行证据 |
| 12 | **`AdminSendNotificationReq.extra` 元素级 null** | ✅ 本轮补齐 | — |
| 13 | **`FAIL_ON_NULL_FOR_PRIMITIVES` 正交开关** | ✅ 本轮补齐（抓出 008） | — |

---

## 9. Unresolved（§37 · **不得为空才允许下结论**）

### UNRESOLVED-001 ✅ **已关闭（2026-10-24 第十轮）**
- ~~**Question**：Go 真源 `message-rpc` 的**响应语义**（哪些 code / 哪些 description）是否与 RAG 侧 `message-common` 一致？~~
- **✅ 关闭依据（三条，缺一不可）**：
  1. **静态逐条对照**：`message-common` 的 `MessageGrpcError#mapStatusCodeToBizCode` 的 switch 与
     Go `service/message/common/errmsg.go:117-132` **逐条一致**
     （`InvalidArgument` / `Unauthenticated` / `PermissionDenied` / `AlreadyExists` / `NotFound` / `Internal` + `default=1015`）
     ⇒ 这一条**早已由 `message-rpc` 组件的审查答完**（见 [`common.md`](common.md) 的 `UNRESOLVED-MSGAPI-001` 行）
  2. **双侧活体对拍**（2026-10-24 第八轮）：真起 Go + RAG 两套 message-rpc（端口 20082 / 60210），
     跑 `tools/difftest/exp-difftest-grpc-errors-message.sh`
     ⇒ **11 条 status 码 + happy path 全部逐条一致，EXIT=0**，产物 `tools/difftest/message-grpc-results.txt`
  3. **兜底码专项**（2026-10-24 第九轮）：`MessageRpcUnknownStatusWiringTest`（4 例，**真 gRPC**）
     ⇒ DB / 未知异常两侧都归 `UNKNOWN`（Go `Unknown` vs RAG `UNKNOWN`）⇒ 熔断判据不分岔
- **⇒ 关闭口径**：原问法问的是「响应语义是否一致」，答案 = **在上述证据强度下一致**（含 `description` 走 code class 映射的那一支）。
- ⚠️ **为什么这一条拖了这么久**：它当时的卡点是「Go 侧未读、归 message-rpc 组件的审查范围」——
  **跨组件的同一个问题被拆成两个组件的待办，就等于永远没人答**（§3.5 的同源坑）。
  ⇒ 教训：**问题归属要按「问题」分，不能按「组件」分**。
- ⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已关闭（2026-10-24 第十轮）** ⇒ **现行态 = ✅ 已关闭**（**不改判**；短确认）。依据：RAG `service/message/message-common/src/main/java/com/rag/message/message/common/errmsg/MessageGrpcError.java:255`（`mapStatusCodeToBizCode(Status.Code)`）· RAG `service/message/message-common/src/main/java/com/rag/message/message/common/errmsg/MessageGrpcError.java:244`（反查未命中 ⇒ 走 code class 映射）· RAG `service/message/message-common/src/main/java/com/rag/message/message/common/errmsg/MessageGrpcError.java:247`（兜底 `CODE_SERVER_BUSY`）· Go `service/message/common/errmsg.go:117-132`（同一张 switch：`InvalidArgument` / `Unauthenticated` / `PermissionDenied` / `AlreadyExists` / `NotFound` / `Internal` + `default`）· Go `service/message/common/errmsg.go:31`（`CodeServerBusy = 1015`）· 核对日期 2026-10-30。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-errors-message.sh` ⇒ **EXIT=0**（2026-10-30 04:27:11 · 4s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [1/3] 生成 proto 桩 + 计算 classpath ⏐ ==> [2/3] 编译 GrpcMessageDualPortRunner ⏐ ==> [3/3] 跑对拍。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

### UNRESOLVED-002 ✅ **已关闭（2026-10-24 第九轮）**
- ~~**Question**：RAG 侧 `MessageGoStyleCoercion` 的规则**是否真的作用在 Spring MVC 实际使用的那个 mapper 上**？~~
- **✅ 关闭依据**：新增 `MessageApiGoStyleCoercionWiringTest`（**6 例，全部走真实 MVC 管线**，不是「同构但不同实例」的探针）：
  - 2 例 `,optional` 字段显式 null **放行**并落零值
  - 1 例 admin `kind:null`
  - 1 例 `extra` 元素收 JSON 数字，并断言**下游 proto 里的字面量**（不只是 DTO）
  - 1 例 `extra` 元素 null ⇒ 5001 **且零次下游调用**（证明拦截发生在调用前）
  - 1 例必填 `title:null` ⇒ 仍 5001
  ⇒ 它证明的是「**规则真挂在 Spring MVC 用的那个 mapper 上**」
  —— 探针只证明「规则是什么」，**装配层断言才证明「MVC 用的是这条规则」**（§3.3.3）。
- ⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已关闭（2026-10-24 第九轮）** ⇒ **现行态 = ✅ 已关闭**（**不改判**）。**本条段内原无可复算指针，本轮补齐**：RAG `service/message/message-api/src/main/java/com/rag/message/message/api/config/MessageGoStyleCoercion.java:97`（`builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg.setCoercion(CoercionInputShape.String, CoercionAction.Fail))` ⇒ JSON 字符串不进整数字段）· RAG `service/message/message-api/src/main/java/com/rag/message/message/api/config/MessageGoStyleCoercion.java:101`（`LogicalType.Boolean` 同款规则）· 守门 RAG `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiGoStyleCoercionWiringTest.java:69`（类）+ `:115`/`:140`/`:168`/`:210`/`:236`/`:254`（**6 例 `@Test`**，全部走真实 MVC 管线）· Go `go-zero@v1.10.2/core/mapping/unmarshaler.go:1113-1125`（Go 侧入参方向的判据出处）· 核对日期 2026-10-30。

### UNRESOLVED-003 ✅ **已关闭（2026-10-24 第八轮）**
- ~~**Question**：响应头在**真容器**下的完整形状两侧是否逐字节一致？~~
- **✅ 关闭依据**：真起 Go + RAG 两套 message-api（端口 20892 / 60992），
  新增 `tools/difftest/exp-http-headers-message.sh` 对 **4 条路径**逐条 dump 两侧**全部**响应头并 diff：
  `401 未带 token` / `404 未知路径` / `405 方法不匹配` / `415 Content-Type 不对` / `200 正常 envelope`（带 JWT）。
  原始响应头产物在 `/tmp/difftest-msg-headers/`。
- **⇒ 取证结论见第八轮第 2 节的对照表**（3 处真实差异 + 1 处框架限制）。
- ⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已关闭（2026-10-24 第八轮）** ⇒ **现行态 = ✅ 已关闭**（**不改判**）。**本条段内原无可复算指针，本轮补齐**：工具 RAG `tools/difftest/exp-http-headers-message.sh`（在位，8007 字节，可执行位 `-rwxr-xr-x`）· Go `go-zero@v1.10.2/rest/handler/tracehandler.go:61`（`propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))` ⇒ Go 侧响应头注入点）· RAG `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:188-190`（`@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE + 3)`）· RAG `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:261-285`（`Extract` → `Start` → `Inject` 三步）· 核对日期 2026-10-30。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-http-headers-message.sh` ⇒ **EXIT=0**（2026-10-30 04:13:26 · 5s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：（未抓到结论行）；<br>`tools/difftest/exp-http-headers-message.sh` ⇒ **EXIT=0**（2026-10-30 04:13:26 · 5s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

### UNRESOLVED-004 ✅ **已关闭（2026-10-24 第八轮）**
- ~~**Question**：3s 超时触发时，RAG 是否真的写出 `503 + text/plain; charset=utf-8 + "Request Timeout\n"`？~~
- **✅ 关闭依据**：新增 `MessageApiTimeoutRealTomcatTest`（2 例，**真 Tomcat**）
  —— 用一个**睡 5s 的 mock stub**（> 3s 阈值）把超时分支真正逼出来，并断言：
  - 状态码 **503**（Go `http.Error(w, httpTimeoutBody, 503)`）
  - body **恰好是 `"Request Timeout\n"`**（Go 的 `http.Error` 写 body + `\n`）
  - `X-Content-Type-Options: nosniff`（Go 的 `http.Error` 自带）
  - `Content-Type` 是 `text/plain` 且**带 charset**（⚠️ 分号后的空格不可达，见下）
  - 对照组：同一路径、同一 stub，**不睡** ⇒ 正常 200 + envelope ⇒ 证明上一例确实由超时触发
- **⚠️ 取证同时挖出并修掉一条真实差异**：RAG 侧的 503 **原本没有 `X-Content-Type-Options: nosniff`**
  ⇒ 已在 `service/common` 的 `HttpMiddleware#writeTimeoutResponse` **补上**（Go 的 `http.Error` 会写）
  ⇒ 影响全部 10 个 api（共享面）。
- **⚠️ 残留（不可修 · 框架限制）**：`Content-Type` 分号后的**空格** ——
  Tomcat 的 `setContentType` 会把 Content-Type 解析成 MediaType 再序列化、**吃掉空格**；
  实测 `setHeader("Content-Type", …)` 也一样（Tomcat 内部转手调 `setContentType`）
  ⇒ RAG 侧最多只能发 `text/plain;charset=utf-8`，Go 是 `text/plain; charset=utf-8`。
- ⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已关闭（2026-10-24 第八轮）** ⇒ **现行态 = ✅ 已关闭**（**不改判**）。**本条段内原无可复算指针，本轮补齐**：RAG `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiTimeoutRealTomcatTest.java:67`（类声明）· RAG `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiTimeoutRealTomcatTest.java:91`（`GO_TIMEOUT_BODY = "Request Timeout"`）· RAG `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiTimeoutRealTomcatTest.java:147`（`slowDownstreamYieldsGoTimeoutContract` ⇒ 503 契约）· RAG `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiTimeoutRealTomcatTest.java:183`（`fastDownstreamStillReturns200` ⇒ 快路径对照）· RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:388`（`httpResp.setStatus(503)` ⇒ 503 出口）· Go `go-zero@v1.10.2/rest/config.go:55`（`Timeout int64 json:",default=3000"` ⇒ 3s 阈值同源）· 核对日期 2026-10-30。
- 🔴 **口径订正（本轮）**：本条正文写「新增 `MessageApiTimeoutRealTomcatTest`（**2 例**，真 Tomcat）」⇒ 实测该类有 **3 个 `@Test`**（`:106` `bindsToDedicatedPortSegment` / `:147` 503 契约 / `:183` 200 快路径）⇒ 精确口径 = **2 条契约断言 + 1 条端口绑定前置检查**。**结论不变**，仅计数口径订正。

### UNRESOLVED-005
- **Question**：`extra` 落库后的**键序**是否影响客户端？
- **Go Evidence**：`encoding/json` 对 `map[string]string` **按键排序**输出（确定性）
- **Java Evidence**：`MessageApiSupport#toNotificationListResp` 用 `LinkedHashMap`
  包 proto 的 `getExtraMap()`（protobuf Java 的 map 迭代顺序**不确定**）
- **Why Unknown**：本轮只确认了两侧**语义等价**（键值集合相同）；未做字节级对拍，
  且 proto map 的迭代顺序本就不保证。
- **Impact**：仅字节级差异，JSON 语义等价。首轮已留档，本轮维持。
- **Required Confirmation**：若未来出现按字节比对 body 的契约测试，需先决定「是否排序」。
- ⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **🟡 仍未定论（Why Unknown：只确认两侧语义等价，未做字节级对拍）** ⇒ **现行态 = 🟡 仍未定论**（**不改判** —— 本条是「待决」而非「已闭合」，按 §4.5「判不了就写待核实」不擅自升级）。短确认：RAG `service/message/message-api/src/main/java/com/rag/message/message/api/service/MessageApiSupport.java:237`（`new java.util.LinkedHashMap<>(item.getExtraMap())` ⇒ RAG 侧用 `LinkedHashMap` 包 proto map）· Go 侧口径 = `encoding/json` 对 `map[string]string` **按键排序**输出（Go 标准库行为；⚠️ **本机无 Go 标准库源码副本 ⇒ 该条无法在本机复算**，只作口径说明，不作证据）· 核对日期 2026-10-30。

---

## 10. 最终判定（§39 · 18 项 checklist）

> ⚠️ **2026-10-24 第十一轮 · 判定重述** —— 本节是 **2026-10-23 首轮**的判定，
> **它的 ❌ 项大多已被后续轮次推翻**（`-005` ~ `-009` 已修或已签字）⇒
> **不要照着下面的 ❌ 去估工作量**。当前有效判定见本节末尾的「**🔁 重述后的判定**」。

| # | 检查项 | 结论 | 备注 |
|---|---|---|---|
| 1 | public behavior 一致？ | ✅ | 13 条路由 / 方法 / 鉴权边界一致；⚠️ `affected` 的 JSON 类型不一致（`-009`）⇒ **已签字**（`SD-2026-10-23-03`），非待修项 |
| 2 | 正常路径一致？ | ✅ | 13 条路由 + 响应形状（`data:{}` 而非 `null`、空 `items` 为 `[]`）逐条一致 |
| 3 | 边界输入一致？ | ✅ | 布尔收整数（`-005`）、`extra` 值收数字（`-006`）、`extra` 值 null（`-007`）、optional 标量 null（`-008`）⇒ **四条已修（2026-10-23 第三轮）** |
| 4 | NotFound / Empty / Null 一致？ | ✅ | `null` 在 4 个字段上的分歧已随 `-005` ~ `-008` 修好而消除；`NotFound` 属 rpc 侧 ⇒ `UNRESOLVED-001` 已关闭 |
| 5 | Error meaning 一致？ | ✅ | 码表 20 条逐条对齐；解析失败两侧都折叠成 5001 |
| 6 | Error propagation 一致？ | ✅ | `errors.As` ↔ `BusinessException`；`handleAny` 兜底 5001；熔断单列 1015 |
| 7 | External dependency failure 一致？ | ⚠️ | 设计对齐；断连 / 超时**无运行证据**（§8.3 #9）<br>⚠️ **2026-10-24 第八轮补**：`UNRESOLVED-004`（3s 超时 503）已用真 Tomcat 取证 ⇒ 该子项已定 |
| 8 | Cache hit / miss 一致？ | ➖ N/A | 无缓存 |
| 9 | Transaction boundary 一致？ | ✅ | 两侧都无事务（api 不碰 DB） |
| 10 | State transition 一致？ | ✅ | 无业务状态；metric 计数时序一致 |
| 11 | Side effects 一致？ | ⚠️ | 业务副作用无差异；**准入控制有差异**（worker 池 503 为 RAG 新增） |
| 12 | Ordering 一致？ | ✅ | 413 > 401 > 解析 > 身份 > 参数 > RPC 两侧逐条一致（含 404/405 不过 auth） |
| 13 | Retry 一致？ | ✅ | 两侧都无 |
| 14 | Timeout scope 一致？ | ✅ | 🔴 **2026-10-27 更新**：原写「⚠️ 作用域不同（Go 在 auth 后 / RAG 在最外层）」—— **已消除**（`DRIFT-MSGAPI-014`：验签从 `HandlerInterceptor` 搬到 `MessageJwtAuthFilter`(+6) ⇒ RAG 也在 auth 后起算 ⇒ 两侧范围**一致**）<br>503 分支的运行证据见 `MessageApiTimeoutRealTomcatTest` 2 例（真 Tomcat） |
| 15 | Concurrency semantics 一致？ | ⚠️ | MaxConns 作用域不同（每路由 vs 全局）+ worker 池上限为 RAG 新增 |
| 16 | Auth / Authorization 一致？ | ✅ | 两把密钥、claim 判定、401 空 body 全部一致 |
| 17 | Serialization semantics 一致？ | ✅ | `,string` 入参 ✅ / 出参 ❌（`affected` ⇒ **已签字**）+ 5 类解析分歧（**已修**）+ 键序（**仅字节级，语义等价**）+ Content-Type（**`MSGAPI-010`，用户拍板保持登记**） |
| 18 | Lifecycle 一致？ | ⚠️ | 初始化顺序一致；观测端点与启动失败模式不同（运维面） |

### 判定

```
╔══════════════════════════════════════════════════════════════════════╗
║   SEMANTICALLY NOT EQUIVALENT                                        ║
║                                                                      ║
║   新增 5 条 SEMANTIC MISMATCH（-005 ~ -009）+ 1 条 OBSERVABLE（-010）  ║
║   首轮的 -001 / -002 / -003 经独立复核仍然关闭 ✅                        ║
╚══════════════════════════════════════════════════════════════════════╝
```

> ⚠️ **2026-10-24 第十一轮：这个框是 2026-10-23 首轮的原文，已过期** ——
> `-005` ~ `-008` **已修**（2026-10-23 第三轮）、`-009` **已签字**（`SD-2026-10-23-03`）
> ⇒ **「5 条 SEMANTIC MISMATCH 未修」这句话已经不成立**。

**为什么不是 `UNRESOLVED`**：5 条 MISMATCH 全部有 `真源-双侧探针` 级证据（Go 真源解析 +
RAG 真实 DTO + 真实 coercion，两侧同时跑），足以支撑判定。

#### 🔁 重述后的判定（2026-10-24 第十一轮 · **当前有效**）

| 项 | 状态 |
|---|---|
| **`-005` ~ `-008`** | ✅ **已修**（2026-10-23 第三轮）—— `MessageGoStyleCoercion.java:119`（`CoercionInputShape.Integer` ⇒ `Fail`）、`:94`（`disable(FAIL_ON_NULL_FOR_PRIMITIVES)`）+ `GoStyleMapStringValueDeserializer`（`-006`/`-007`）；守门 `MessageApiGoStyleCoercionWiringTest` **6 例走真实 MVC 管线** |
| **`-009`** | ✅ **已签字**（`SD-2026-10-23-03`）—— 出参 `affected` Go 数字 / RAG 字符串；**不是待修项** |
| **`-010`** | 🟡 **仍 OPEN**（`Content-Type` 缺 charset）—— **用户 2026-10-24 拍板「保持登记，本轮不做」**（共享面影响全部 10 个 api）⇒ **不是签字**，是登记在册的待办 |
| **`-011` / `-013`** | 🟡 **仍 OPEN**（状态行 reason phrase / 缺 `Traceparent`）—— 同上，**保持登记，后续修**（落点同为 `service/common`） |
| **`-012`** | ⛔ **不可修**（Tomcat 序列化吃空格，实测无写法可达） |
| **最终判定** | **2026-10-29 对齐**：`-010`/`-013` 已由共享层 T-6/T-8 落码 ⇒ 本行所列「共享面三项响应头差异」现只剩 `-011`（🔴 框架限制 · 不可修）｜ ⚠️ **仍写 `SEMANTICALLY NOT EQUIVALENT`，但支撑已换** ——<br>不再是「5 条解析漂移未修」，而是「**共享面三项响应头差异**（`-010`/`-011`/`-013`）+ **结构性差异**（MaxConns 作用域 / worker 池）」<br>🔴 2026-10-27：**「超时作用域」已从这份清单里划掉**（`DRIFT-MSGAPI-014` 修复后与 Go 一致）<br>⇒ 组件内的**解析与序列化语义已全部对齐** |
| **本轮实测（2026-10-24 19:50）** | `message-api` **88 例，0 failures / 0 errors / 0 skipped**；`message-rpc` 76 / `message-common` 21 / `common` 168 ⇒ 合计 **353 例，BUILD SUCCESS** |
**为什么不是 `SEMANTICALLY EQUIVALENT`**：第 3 / 4 项不通过，且其中一条
（`broadcast=1` 被当成 `true`）在业务上会造成**误发全量通知**。
🔴 **2026-10-27 更新**：原写「第 3 / 4 / **17** 项不通过」—— **17（Timeout 作用域）已通过**
（`DRIFT-MSGAPI-014` 修复 ⇒ 两侧作用域一致）。

**证据强度总表**：

| 结论 | 强度 |
|---|---|
| `-005` ~ `-009` 五条 SEMANTIC MISMATCH | **真源-双侧探针**（Go `core/mapping` 真源 + RAG 真实 DTO/coercion，56 例） |
| `-010` Content-Type charset | **转换器级运行**（非真容器 HTTP 往返） |
| 13 条路由 / 方法 / 鉴权边界 | 静态源码 + 42 条既有 MVC 用例（RAG 单侧） |
| 保护层 413 / 503 裸响应 | **真容器-RAG单侧**（Go 侧期望值取自源码） |
| 一切依赖 message-rpc 响应语义的结论 | ✅ **已定级（第十轮）** —— 静态逐条对照 + 双侧活体 12 档对拍（EXIT=0）+ 兜底码专项，**`UNRESOLVED-001` 已关闭**（原为 **UNKNOWN**） |
| 超时 / 断连 / 并发 / 过载 | **无运行证据**（§8.3 #6~#8） |

### 与 AGENTS.md §0 状态表的同步建议（本轮不改 AGENTS.md，登记在此）

`message` 域当前记为「✅ api + rpc 双端验证完成」。本轮结论要求把它降级为：
`message-api ✅ rpc ⚠️（api 侧本轮查出 6 条未修漂移，rpc 侧另有并行会话在审）`。

---

## 附：修这些漂移时的强制顺序（引 `AGENTS.md` §3.3 强制三条）

1. **先改/删错误断言，再改实现** —— `-009` 有绿测试锁死（`#adminSendNotificationAffectedIsString`，
   `@DisplayName` 写着「affected 是字符串」），不先动它，修正确实代码会被测试挡住，
   且测试名会让人**误判「修复」才是漂移方向**。
2. **每个 Go 事实带 `文件:行号`，且指向消费点** —— 本台账已逐条标注；修改时不得退回 struct tag 声明行。
3. **改完必须两侧同时跑** —— `tools/semantic-probe` 需**扩到 56 例以上并新增出参用例面**，
   否则 `-009` 这类「出参漂移」没有任何守门。
   > ✅ **2026-10-24 第十一轮：本条已满足** —— 探针已扩到 **56 例**（Go 侧 56 / Java 侧 56），
   > 且**出参用例面已加**（`X1` / `X2` 两条 `affected` 出参用例）⇒
   > 实跑 `bash tools/semantic-probe/run-parity.sh` ⇒
   > **总计 56 例：一致 54，不一致 0，已签字偏离 2，EXIT=0**。
---

<!-- @fix 2026-10-23 22:0x 第三轮 · 探针补到 56 例 · -005 已修 / -006 -007 -008 本轮修 / -009 已签字 -->

## 2026-10-23 第三轮：探针落盘 + `-005/-006/-007/-008` 关闭

> **核对时间**：2026-10-23 22:31–22:50
> **基线 commit**：`8422116`（`align/message` 工作树）
> **本轮证据强度**：**真源-双侧探针 56 例**（Go 侧真源 `core/mapping` + RAG 侧真实 DTO/真实 coercion，**值级比对**）

### 🔴 本轮第一个发现：上一轮宣称的「51 例探针」**从未落盘**

§8.1 写「本轮双侧探针（51 例，真源-双侧探针）」，但实测 `tools/semantic-probe/` 里：

| 文件 | 上一轮宣称的 | 实测 |
|---|---|---|
| `go/main.go` | 51 例 | **102 行 / 23 例** |
| `java/Probe.java` | 51 例 | **106 行 / 23 例** |
| 最后一次改动 | — | commit `69e3262` / `e993ff0`（标题就是 `add shit`），**早于**本台账 02:14 的写入时间 |

⇒ **五条 SEMANTIC MISMATCH（-005 ~ -009）的判定性证据不在仓内，不可复现、无人守门。**
这正是 §3.3「凡写『与 Go 一致』，必须有两侧同时跑的探针」被绕过的一次 ——
**结论写进了台账，探针却没进仓**。本轮第一件事就是把探针补齐并跑通。

### 一、探针从 23 例扩到 56 例（新增两组机制 + 33 例）

**机制①：值比对**（首轮探针自认「只比 ACCEPTED/REJECTED」）
现在同一用例打印**解析出的值 / 序列化结果**，两侧逐字比对（Go 侧 `mapStr` ↔ Java 侧 `mapStr`，
整数十进制无分隔、bool 用 `true/false`、map 按键排序拼 `k=v`）。
判定新增一档 `XX(值不同)` —— 「两侧都收、但落的值不一样」从此可见。

**机制②：签字偏离白名单**（`run-parity.sh` 内置 `known-dev` 清单）
`docs/Intentional_Deviations/` 里已签字的差异**必然**为红。若让它们计入失败，
守门人迟早会去「修绿」—— 那正是把已签字决策改回未受控漂移的最短路径（§3.2 / §5.3b）。
⇒ 白名单用例标成 `SD-xx 预期差异`，**不计入失败，但始终打印出来**。
⚠️ 加白名单是有后果的动作：每条都必须能在签字档里找到对应记录，否则就是拿白名单掩盖真漂移。

**新增用例组**：`A`（布尔收数字 7 例）· `B`（`extra` 元素 10 例）· `C`（显式 null / int32 范围 8 例）
· `D`（`,string` 值解析边界 6 例）· `X`（**出参**序列化 2 例，首轮完全没有这一面）。

### 二、`-005`：**代码里早已修好，台账没更新**（本轮只做核实与补证）

`MessageGoStyleCoercion` 里已有「补强 3（2026-10-23，DRIFT-MSGAPI-005）」：
`LogicalType.Boolean + CoercionInputShape.Integer → Fail`。
探针 A 组 7 例（`unread_only`/`broadcast` × `1`/`0`/`2`/`1.0`/`1.5`）**两侧全部 REJECTED**，
`MessageApiMvcPipelineTest#adminBroadcastRejectsJsonNumber` 也已在锁。
⇒ **`-005` 关闭（代码早于台账）**；台账的「未修」标记本轮更正。

### 三、`-006` / `-007`：`map[string]string` 的**元素级**判据（本轮修）

**Go 出处（消费点，已本轮重新逐行核对 `go-zero@v1.10.2/core/mapping/unmarshaler.go`）**：
`generateMap` 赋值元素的 `default` 分支（`:385-417`）

```go
case json.Number:                       // ← 数字：收下并字符串化（v.String() 是**原始字面量**）
    target := reflect.New(dereffedElemType)
    if err := setValueFromString(dereffedElemKind, target.Elem(), v.String()); err != nil { … }
case bool:                              // ← bool：要求 elem kind == Bool，对 string map ⇒ errTypeMismatch
default:                                // ← nil/数组/对象：要求 elem kind == 值 kind ⇒ Invalid ≠ String ⇒ 报错
```

⚠️ **与顶层标量字段是两段代码、判据相反**（顶层走 `processFieldPrimitiveWithJSONNumber`，
其 `switch typeKind` 无 `reflect.Bool` 分支 ⇒ 一律拒）。这正是 006/007 的全部根因。

**为什么不能在 `MessageGoStyleCoercion` 里加一条规则解决**：
共享规则靠 `LogicalType.Textual + Integer/Float → Fail` 对齐**顶层 String 字段**，
而 map 的值同样声明为 `String` ⇒ 命中同一条规则。
放行 map 元素上的数字，就必须把顶层 String 字段一起放行 ⇒ `{"content":123}` 会重新变成 ACCEPTED
（`DRIFT-MSGAPI-002` 回退）。两个目标类型在 Jackson 的 **LogicalType 维度上无法区分**；
`GoStyleCoercion` 类注释还记录了另一条路也不通 —— Jackson 3 的**类级** coercion 配置实测完全不生效。
⇒ 唯一可靠挂点是字段上的 `contentUsing`，即新增的 `GoStyleMapStringValueDeserializer`。

**🔴 修 `-007` 时踩到的 Jackson 细节（不写下来一定会再踩）**：
`ValueDeserializer` 实现 `NullValueProvider`，map 元素为 `null` 时 Jackson **不走 `deserialize`**，
而走 `getNullValue` —— 默认实现返回 `null` ⇒ 静默放行。
**只覆写 `deserialize` 的话 `-007` 不会被修掉**，必须同时覆写 `getNullValue` 抛错。
且 `getNullValue` 里**不能**调 `handleUnexpectedToken(…, p)`：该路径 Jackson 没给 parser，传 `null` 会 NPE。

**证据（双侧，值级）**：`B1`(1) `B2`(1.5) `B9`(1.0) `B10`(1e2) 两侧一致；
`B3`（元素 null）两侧均 REJECTED；`B4`/`B6`/`B8`（bool/数组/整体字符串）两侧均 REJECTED。
`B9`/`B10` 专门钉住「**保留原始字面量**」：Go 用 `json.Number.String()` ⇒ `1.0` 落 `"1.0"`、`1e2` 落 `"1e2"`，
不是 `"1"` ⇒ Java 侧必须用 `p.getText()`，不能用数值格式化。

### 四、`-008`：`,optional` 标量字段的显式 null（本轮修）

**Go 出处**：`unmarshaler.go:854-863 processNamedFieldWithValue`

```go
if mapValue == nil {
    if opts.optional() { return nil }        // ← 接受，值保持零值
    return fmt.Errorf("field %q mustn't be nil", key)
}
```

Jackson 3 的 `FAIL_ON_NULL_FOR_PRIMITIVES` **默认 true** ⇒ `{"offset":null}` / `{"kind":null}` /
`{"unread_only":null}` 三种形态全被拒。

**为什么在本模块全局关掉是安全的**（逐字段核过 `types.go`，不是拍脑袋）：
本模块**没有任何必填的原始类型字段**。`PageReq` 三字段全 `,optional`；
`AdminSendNotificationReq` 的 `broadcast`/`kind` 全 `,optional`，`target_id` 是 `Long`；
必填的 `title`/`content`/`conversation_id` **全是引用类型** ——
它们收显式 null 的拒绝靠 `GoStyleRequired#field(...)`（Controller 层），
**不靠这个 feature** ⇒ 关掉它动不到必填语义。
探针 `required 显式 null(conversation_id)` + 新增 MVC 用例 `requiredFieldExplicitNullIsStillRejected` 双向钉死。

**证据（双侧，值级）**：`C1` offset=0 · `C2` limit=0 · `C3` unread_only=false · `C4` kind=0，
四例两侧一致。`C5`（`,string,optional` 的 `target_id:null`）两侧本就一致（早前已对）。

### 五、`-009`：**已被签字，不再是待修项**（本轮更正台账）

`DRIFT-MSGAPI-009` 仍写着「未修 · 必须改成 JSON 数字」，但
[`SD-2026-10-23-03`](../../Intentional_Deviations/_cross/signed-deviations.md#L67-L78)
已由用户逐条拍板「**保留 RAG 的字符串形态，不改 wire 契约**」。

⇒ **本组件不再把它当漂移**。三条连带处置：

1. 探针把 `X1`/`X2` 放进 `known-dev` 白名单 ⇒ 对拍时它**必然为红但不计失败**，
   且每次都打印出来（不会「悄悄消失」）。
2. 🔴 **`AdminSendNotificationResp` 的 javadoc 把 Go tag 抄错了** ——
   它写 `Affected int64 \`json:"affected,string"\``，而真源 `types.go:21-23` 是
   `Affected int64 \`json:"affected"\``（**没有** `,string`）。
   注释里那句错误的「Go: …」正是当年加 `@JsonFormat(STRING)` 的理由（§3.3.2）。
   ⇒ 已改正为中性描述 + 指向 SD-03；**行为按签字结论保持字符串不变**。
3. 台账里「Must Preserve：affected 是 JSON 数字」一句**作废** —— 按 Go 改反而是制造未受控漂移。

### 六、`-010`（Content-Type 缺 charset）—— **仍开放，本轮未动**

`OBSERVABLE DIFFERENCE` / LOW。要在**真容器**下 dump 全部响应头才能与 Go 逐字节对齐；
本轮未做（与 `UNRESOLVED-003` 同一件事）。**未修不等于已登记为待办以外的东西** ——
它仍是 OPEN，且探针不覆盖 HTTP 响应头（那需要双侧起栈）。

> ⚠️ **2026-10-24 第八轮更新**：**真容器 dump 那一半已经做了** ——
> `UNRESOLVED-003` 已关闭，`exp-http-headers-message.sh` 对 5 条路径逐条 dump 两侧全部响应头
> ⇒ **`-010` 本身已在真容器级确认**（Go `application/json; charset=utf-8` vs RAG `application/json`）。
> ⇒ **仍然开放的只剩「要不要改」**（不是「不知道是什么」）。
> ⚠️ **2026-10-24 用户拍板：保持登记，本轮不做**（改 charset 会动全部 10 个 api 的响应头）⇒ **不是签字**，仍是登记在册的待办。

### 七、本轮关闭的 Unresolved

| 编号 | 状态 | 依据 |
|---|---|---|

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:16:43 · 4s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。
| `UNRESOLVED-002` | ✅ **关闭** | 新增 `MessageApiGoStyleCoercionWiringTest`（**6 例，全部走真实 MVC 管线**：2 例 `,optional` 显式 null 放行并落零值、1 例 admin `kind:null`、1 例 `extra` 元素收数字并断言下游 proto 里的**字面量**、1 例 `extra` 元素 null → 5001 **且零次下游调用**、1 例必填 `title:null` 仍 5001）。它证明的是「规则真挂在 Spring MVC 用的那个 mapper 上」——探针只证明「规则是什么」 |

### 八、本轮实测数据

```
探针：56 例 → 一致 54，不一致 0，已签字偏离 2        EXIT=0
message-api：84 tests / 0 failures / 0 errors / 0 skipped   BUILD SUCCESS
             （原 78 + 新增 6；新增类的 surefire XML skipped=0，符合 §状态更正#5 的要求）
```

⚠️ **「84 全绿」不等于「对齐了 84 条」**：探针的 54 例才是这一轮的判定性证据，
MVC 那一层的作用是证明装配（规则没掉在 builder 上），两者不可互相替代（§3.3.3）。

---

<!-- @fix 2026-10-24 第八轮 · 真双栈响应头取证 + 503 超时取证 + 3 条新发现 -->

## 2026-10-24 第八轮：**真容器响应头双侧取证** + **3s 超时取证** + 3 条新发现

> **用户指示**：「起真双栈对拍，一次关 4 项未知（difftest 12 档 + 响应头 dump + 503 超时取证 + MSGAPI-010）」

### 1. 取证方法（新增 `tools/difftest/exp-http-headers-message.sh`）

- 真起两套 message-api（Go `20892` / RAG `60992`），对 **4+2 条路径**逐条 dump 两侧**全部**响应头并 diff：
  `401 未带 token` / `404 未知路径` / `405 方法不匹配` / `415 Content-Type 不对` / `200 正常 envelope`（带 JWT）/ `200 通知列表`（带 JWT）
- **为什么必须带 JWT**：Go 侧 `rest.WithJwt(UserAuth.AccessSecret)` 覆盖整个 `/message/v1` 分组
  ⇒ 无 token 一律 401 ⇒ 「正常响应」那一档必须现签一个两侧都认的 HS256 token（脚本内用 python3 现签）
- ⚠️ 规范化只剔除**逐次变化**的 `Date` / `Server` / `Content-Length`，**其余逐条比对**

### 2. 逐条对照结果（原始响应头产物：`/tmp/difftest-msg-headers/`）

| 路径 | Go | RAG | 判定 |
|---|---|---|---|
| `401 未带 token` | `HTTP/1.1 401 Unauthorized`<br>`Traceparent: 00-…`<br>`Content-Length: 0` | `HTTP/1.1 401 `<br>`Content-Length: 0` | 🔴 状态行缺 reason phrase + 缺 `Traceparent` |
| `404 未知路径` | `HTTP/1.1 404 Not Found`<br>`Content-Type: text/plain; charset=utf-8`<br>`Traceparent`<br>`X-Content-Type-Options: nosniff` | `HTTP/1.1 404 `<br>`X-Content-Type-Options: nosniff`<br>`Content-Type: text/plain;charset=utf-8` | 🔴 状态行 + `Content-Type` 空格 + 缺 `Traceparent` |
| `405 方法不匹配` | `HTTP/1.1 405 Method Not Allowed` | `HTTP/1.1 405 ` | 🔴 状态行 |
| `415 Content-Type 不对` | 两侧都是 **401**（鉴权在 Content-Type 判定之前） | 同 | ✅ **一致**（且与 Go 的 chain 顺序一致） |
| `200 正常 envelope` | `Content-Type: application/json; charset=utf-8` | `Content-Type: application/json` | ✅ **= `DRIFT-MSGAPI-010`**（真容器级确认） **2026-10-29 对齐：现行态 = ✅ 已修**（共享层 T-6 · `GoStyleJsonContentTypeAdvice.java:127,151`） |

⇒ **body 逐字一致**：`{"code":200,"msg":"OK","data":{...}}` 两侧结构与取值一致
（仅 JSON 对象的**键序**不同 —— Go 的 `map` 序列化按 key 排序，RAG 按声明序 ⇒ 见 `UNRESOLVED-005`，语义等价）。

### 2.1 原始响应头逐字留档（**仓内证据**，随本台账一起 review）

> ⚠️ 原始 txt 产物按 `.gitignore` 的 `tools/difftest/*-results.txt` 策略属「日志不入仓」；
> ⇒ 这里把**原始响应头**逐字贴进台账，**证据因此留在仓内**（可 review、可 diff）。
> 运行时产物目录：`/tmp/difftest-msg-headers/`（Go 侧 20892 / RAG 侧 60992，2026-10-24）。

**`200-unread`**

```
--- Go ---
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Traceparent: 00-b8d94466c2e11e4e4260d5999ee14f77-7947fb03073c5d57-01
Date: Fri, 23 Oct 2026 22:20:23 GMT
Content-Length: 97

--- RAG ---
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 97
Date: Fri, 23 Oct 2026 22:20:24 GMT
```

**`404-未知路径`**

```
--- Go ---
HTTP/1.1 404 Not Found
Content-Type: text/plain; charset=utf-8
Traceparent: 00-01ad4794b3d336b454e8d79378fc7293-918ba39aaf963c08-01
X-Content-Type-Options: nosniff
Date: Fri, 23 Oct 2026 22:20:22 GMT
Content-Length: 19

--- RAG ---
HTTP/1.1 404 
X-Content-Type-Options: nosniff
Content-Type: text/plain;charset=utf-8
Content-Length: 19
Date: Fri, 23 Oct 2026 22:20:22 GMT
```

**`401-未带token`**

```
--- Go ---
HTTP/1.1 401 Unauthorized
Traceparent: 00-873628a39f27f0fc33c505e48080aa1b-b126d839fa1e8a9c-01
Date: Fri, 23 Oct 2026 22:20:21 GMT
Content-Length: 0

--- RAG ---
HTTP/1.1 401 
Content-Length: 0
Date: Fri, 23 Oct 2026 22:20:22 GMT
```

**`405-方法不匹配`**（**2026-10-24 第十一轮补贴** —— 这档的原始头是 `Allow` 结论的**唯一证据**，必须留在仓内）

```
--- Go ---
HTTP/1.1 405 Method Not Allowed
Allow: POST
Date: Fri, 23 Oct 2026 22:20:22 GMT
Content-Length: 0

--- RAG ---
HTTP/1.1 405 
Allow: POST
Content-Length: 0
Date: Fri, 23 Oct 2026 22:20:22 GMT
```

> ✅ **逐字对照结论**：两侧**都有 `Allow: POST`**，body 都是**空**（`Content-Length: 0`）⇒
> **该档唯一的差异是状态行缺 reason phrase**（= `DRIFT-MSGAPI-011`）。
> ⇒ **首轮台账写的「RAG 无 `Allow` 头」是错的** ⇒ 该待办**不存在**。
> ⇒ ⚠️ **不要**再去「补一个 `Allow` 头」—— 它本来就有。


### 2.2 ✅ 完整 6 档响应头对照表（**2026-10-24 第十轮复核**：把原始 dump 产物逐档读完）

> ⚠️ **第十轮做的事**：第八轮只把**状态行**贴进了对照表，**把每个头的逐条 diff 留在了产物里没读出来**
> ⇒ 导致「`Allow` 头两侧到底一不一致」这个子项被挂成「无运行证据」。
> ⇒ **本轮把 `/tmp/difftest-msg-headers/*.h.n`（归一化后的完整头集合）逐档读完**，
> 结论如下（`normalize` 只剔除逐次变化的 `Date` / `Server` / `Content-Length`）：
>
> | 档位 | Go | RAG | 差异 |
> |---|---|---|---|
> | `401-未带token` | `401 unauthorized` + `traceparent` | `401 ` | 状态行 reason phrase ＋ **缺 `traceparent`** |
> | `404-未知路径` | `404 not found` + `content-type: text/plain; charset=utf-8` + `traceparent` + `x-content-type-options: nosniff` | `404 ` + `content-type: text/plain;charset=utf-8` + `x-content-type-options: nosniff` | 状态行 ＋ Content-Type **空格** ＋ 缺 `traceparent` |
> | **`405-方法不匹配`** | `405 method not allowed` + **`allow: post`** | `405 ` + **`allow: post`** | ✅ **只有状态行** ⇒ **`Allow` 头两侧一致** |
> | `415-ContentType不对` | `401 unauthorized` + `traceparent` | `401 ` | 两侧**都是 401**（鉴权在 Content-Type 判定之前）⇒ 该档**结构一致**，差异同上 |
> | `200-unread` | `200 ok` + `content-type: application/json; charset=utf-8` + `traceparent` | `200 ` + `content-type: application/json` | 状态行 ＋ **缺 charset** ＋ 缺 `traceparent` |
> | `200-notiflist` | 同上 | 同上 | 同上 |
>
> ⇒ **差异面收敛为 4 条**（此前台账只记了 3 条）：
> | # | 差异 | 归属 |
> |---|---|---|
> | 1 | **状态行缺 reason phrase** | `DRIFT-MSGAPI-011`（**容器层** · 框架限制 · 不可修 ⇒ 见下方 2026-10-27 订正） |
> | 2 | **Content-Type 缺 charset**（200 档） | `DRIFT-MSGAPI-010`（共享层） **✅ 已修**（2026-10-29 对齐 · 共享层 T-6） |
> | 3 | **Content-Type 分号后空格**（404 档） | `DRIFT-MSGAPI-012`（Tomcat 限制，**不可修**） |
> | 4 | **缺 `Traceparent`** | `DRIFT-MSGAPI-013`（共享层） **✅ 已修**（2026-10-29 对齐 · 共享层 T-8） |
>
> ⇒ ✅ **首轮那张「待决策」表里判反的两行**（「404 body 空」「405 无 `Allow` 头」）**双双作废** ——
> 两者在真容器下**两侧都一致**。⚠️ 这两条都是**静态读源码**得出的结论 ⇒ 见
> [`PITFALL-MSG-R-028`](../../Pitfalls/message/rpc.md)。
>
> ⚠️ **产物的时效性**：dump 时刻 = **2026-10-23 22:20 GMT（= 2026-10-24 06:20 CST）**；
> `message-api/src/main/` 的**最后一次**改动是 `52b327b`（**2026-10-23 23:02 +0800**）
> ⇒ **改动在 dump 之前** ⇒ 产物忠实代表当前行为，**无需重起栈**。

### 3. 🆕 三条新发现

#### 🆕 `DRIFT-MSGAPI-011` 🔴 **RAG 全域通病**：**状态行缺 reason phrase**

> ⚠️ **2026-10-24 用户拍板：「保持登记，本轮不做」** —— **不是签字**。
> 理由：归因在**共享层**（Tomcat + Spring Boot 4 + `HttpMiddleware` 的 response wrapper）⇒ 影响全部 10 个 api。
> ⇒ ⚠️ 台账里「归因尚未定位到具体一行 ⇒ 待查」这句**仍然成立** —— 别把「归因没定位」写成「已定位」。

```
Go  ： HTTP/1.1 200 OK / 401 Unauthorized / 404 Not Found / 405 Method Not Allowed
RAG ： HTTP/1.1 200    / 401             / 404            / 405
```
⚠️ **不是 message 域特有** —— 实测 **RAG 的 user-api / admin-api / message-api 全都这样**，Go 侧全都有
⇒ 归因在**共享层**（Tomcat + Spring Boot 4 + `HttpMiddleware` 的 response wrapper）
⇒ 本工作树**不改共享层**（会影响 20 个组件）⇒ 登记为**跨组件待办**。
⚠️ 归因尚未定位到具体一行 ⇒ 台账里写「待查」，不臆断。

> ✅ **2026-10-27 订正（本轮 G2 阶段 2 · D-3）** —— 上面那句「归因在**共享层**」
> 与「归因尚未定位到具体一行 ⇒ 待查」**两句都已过期**：归因**已定位**，
> 且落点**既不在共享层、也不在本域**，而在**容器层**。
>
> | 项 | 事实 | 证据 |
> |---|---|---|
> | **Go 侧** | 状态行写 `code + 空格 + StatusText(code)`，reason phrase 由**标准库**补 | `go-zero@v1.10.2/rest/httpx/responses.go:183` → Go 标准库 `net/http/server.go:1596-1611`（`:1602-1605` 写 `StatusText(code)`）+ `net/http/status.go:81` |
> | **RAG 侧（应用层）** | 只写码，**不带 reason** | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:943`（`public void setStatus(int sc)`）+ `:956`（`resp.setStatus(sc)`）<br>⚠️ **2026-10-29 真实性复核 · 行号订正**：原写 **`:907` / `:920`** ⇒ **行号已漂移**（该文件此后增补了 ~36 行）⇒ **结论不变，仅出处行号订正** ⇒ 现在的 `public void setStatus(int sc)` 在 **`:943`**、`resp.setStatus(sc)` 在 **`:956`**（2026-10-29 当场核对） |
> | **RAG 侧（容器层 · 决定性事实）** | Tomcat 的 `Response` **已没有 `setStatus(int, String)`** ⇒ 应用层**无法**补 reason phrase | **实测 `javap`**：`tomcat-embed-core` **9.0.63** 有 `setStatus(int)` **+** `setStatus(int,String)`；**10.1.52** 与 **11.0.24** **只剩 `setStatus(int)`** ⇒ 本项目 **Boot 4.0.8 ⇒ `tomcat.version=11.0.24`**（`spring-boot-dependencies-4.0.8.pom`）⇒ **无路可走** |
>
> ⇒ **性质 = 框架限制（容器层）** ⇒ 共享层 `service/common` **够不着**
> ⇒ 已在 [`Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)
> 登记 **`SD-2026-10-25-CAND-14`**（🏷 待签字候选 · 性质「框架限制（容器层）」）。
> ⚠️ `docs/Intentional_Deviations/**` **不在本 lane 写权内** ⇒ 本轮**只引用**、不改那份档
> （合并后由整合者统一处理）。
> ⚠️ **本条不影响 2026-10-24「保持登记，本轮不做」那个拍板** —— 结论不变，
> 只是把「归因未定位」升级成「归因已定位且不可修」。

#### 🆕 `DRIFT-MSGAPI-012` 🟡 **框架限制**：`Content-Type` 分号后的**空格**在 Tomcat 上不可达

```
Go  ： Content-Type: text/plain; charset=utf-8     （有空格）
RAG ： Content-Type: text/plain;charset=utf-8       （无空格）
```
⚠️ **实测**：Tomcat 11 的 `Response.setContentType(String)` 会把 Content-Type **解析成 MediaType 再序列化**，
序列化时**吃掉空格**；且 **`setHeader("Content-Type", …)` 也一样**（Tomcat 内部对 Content-Type **转手调 `setContentType`**）
⇒ **没有任何写法能产出带空格的 Content-Type** ⇒ 归 **框架限制**，**不可修**。
⇒ 连带：`DRIFT-MSGAPI-010` 即使补上 charset，也只能是 `application/json;charset=utf-8` ⇒ **与 Go 仍差一个空格**。

#### 🆕 `DRIFT-MSGAPI-013` ✅ **缺 `Traceparent` 响应头** ｜ **2026-10-29 对齐：原判 🟡 ⇒ 现行态 = ✅ 已修（共享层 T-8 已落码）**

> **2026-10-29 对齐 · 依据**：共享层 T-8 第十九轮已落码 —— `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:188-191`（`@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE + 3)`）+ `:270-283`（`PROPAGATOR.extract` → `start` → `PROPAGATOR.inject`）· 生效链 `service/message/message-api/src/main/java/com/rag/message/message/api/MessageApiApplication.java:65`（`@ComponentScan` 含 `"com.rag.common"`）⇒ 与 `docs/DRIFT/message/vs-common.md:40` 的 ✅ 一致 ⇒ **证据强度：源码行号 + 装配链**。


```
Go  ： Traceparent: 00-<trace-id>-<span-id>-01     （每个响应都有）
RAG ： 无
```
⇒ 归类：**响应头集合不对称** ⇒ 影响分布式追踪的**跨域接力**（下游拿不到上游的 trace context）。

> ✅ **2026-10-24 第十轮：RAG 侧「有没有等价传播机制」这个问题已经查实了** ——
> 原台账写「需另查 ⇒ 本轮只登记事实，不下结论」，现在有结论了：
>
> | 事实 | 证据 |
> |---|---|
> | **Go 侧的 `Traceparent` 响应头来自 go-zero 内置的 trace 中间件**（**不是** message 域的业务代码） | `go-zero@v1.10.2/rest/handler/tracehandler.go:61` `propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))`<br>⇒ 传播器是 `go-zero@v1.10.2/core/trace/propagation.go:9-11` 的 `otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))` ⇒ `TraceContext{}.Inject` 写出的正是 **`traceparent`**（+ `tracestate`）到 `w.Header()` |
> | **前提是 `Telemetry` 开启** | `service/message/api/etc/messagecenter.yaml:18-22`：`Telemetry: {Name: message-api, Endpoint: jaeger:4317, Sampler: 1.0, Batcher: otlpgrpc}` |
> | **RAG 侧「有 span，只是没写成响应头」** | ① span **存在**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:305-310 / 346-351 / 376-378` 用 `Span.fromContext(ctx)` 打 `service.name` / `http.route` / `http.method` / `http.status_code`<br>② OTel 依赖**存在**：`service/message/message-api/pom.xml:131-135` `opentelemetry-exporter-otlp`（全仓 18 个组件都有）<br>③ **缺的只是最后一步**：把 span 的 trace context 写进 HTTP 响应头 |
>
> ⇒ **结论（替代原来那句「需另查」）**：**RAG 不是「没有追踪」，是「追踪没透出到响应头」**。
> 修法落点是 `service/common` 的 `HttpMiddleware`（或 message-api 自己的 filter），
> 用 `Span.fromContext(ctx).getSpanContext()` 的 `traceId` / `spanId` / `traceFlags`
> 拼成 `00-<32hex>-<16hex>-01` 写进 `w.Header()` ⇒ **与 Go 的输出格式逐字节同构**。
>
> ⚠️ **为什么它仍然算「共享面」**：落点在 `service/common` ⇒ **影响全部 10 个 api** ⇒
> 与 `MSGAPI-010`（charset）/ `MSGAPI-011`（reason phrase）**同类** ⇒ **需用户拍板后再动**。
> ⚠️ **不得做的事**：不要在 message 域单独加一个 filter 就完事 ——
> 那样会变成「message-api 有、其它 9 个 api 没有」的**新的不对称**（比现状更难解释）。

### 📌 `MSGAPI-013` 的**后续修**执行计划（2026-10-24 用户拍板「计入台账，后续修」）

> **状态**：**已计入台账，本轮不做**（与 `MSGAPI-010` / `MSGAPI-011` 同批，但**这三件是一起的** —— 都在 `service/common` 的响应头上）。

| 步骤 | 内容 | 落点 / 证据 |
|---|---|---|
| **1. 落点** | 改**共享层** `service/common` 的 `HttpMiddleware`（响应写出那一段），**或**新增一个所有 api 都装配的 filter ⇒ **10 个 api 一起生效** | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java` |
| **2. 取值** | `Span.fromContext(ctx).getSpanContext()` ⇒ `getTraceId()`（32 位 hex）/ `getSpanId()`（16 位 hex）/ `getTraceFlags()` | ⚠️ **必须判 `isValid()`** —— 无效 span 写出来的 `traceparent` 是全 0，比不写更坏 |
| **3. 拼格式** | `00-<traceId>-<spanId>-<flags>`：`flags` 用 `traceFlags.isSampled() ? "01" : "00"` | ⚠️ **Go 侧的格式**：`go-zero@v1.10.2/core/trace/propagation.go:9-11` 的 `TraceContext{}.Inject` 写出的就是 `version-traceid-spanid-flags`（`00` 开头，flags 位 `01`=sampled）⇒ **逐字节同构** |
| **4. 写入** | `resp.setHeader("Traceparent", ...)` | ⚠️ **别用 `Content-Type` 那条路** —— Tomcat 对 `Content-Type` 有特殊处理（`setContentType` 会解析再序列化、吃掉空格 ⇒ `MSGAPI-012`）；`Traceparent` 是普通头，**不受影响** |
| **5. 守门** | ① **单测**：`HttpMiddleware` 层断言 `Traceparent` 头的**正则形状** `^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$`（MockMvc **能**测，因为是我们自己写的头）；<br>② **真容器对拍**：`tools/difftest/exp-http-headers-message.sh` 的 6 档 dump —— 改完后两侧的 `traceparent` 应**逐条一致**（Go 侧的 trace-id 每次不同 ⇒ 归一化时要比**格式**不比**值**） | ⚠️ 「格式一致」这件事**单测能守**，「真的出现在真容器的响应里」**要真容器** ⇒ 两个都要 |
| **6. 变异验证** | 摘掉那段写 header 的代码 ⇒ 守门必须转红 | ⚠️ 摘掉后若守门仍绿 ⇒ 说明断言写错了地方（§3.3.3） |
| **7. 顺带** | 同一次改动**顺手把 `MSGAPI-010`（charset）与 `MSGAPI-011`（reason phrase）一起拍板** —— 它们与本条**落在同一处**，分三次改共享层是三次冲突 | ⇒ 三件**一起做**或**一起不做** |

> ⚠️ **为什么本轮不做**：共享层改动 ⇒ 影响全部 10 个 api ⇒ 需与 `MSGAPI-010` / `MSGAPI-011` **合并成一次**共享面变更 ⇒ 需用户单独拍板。
> ⚠️ **不得做的事**：不要只改 message 域（会造成新的不对称）；不要只写单测就宣布修好（真容器那一层必须也验）。

### ✅ `DRIFT-MSGAPI-014` ✅ **已修（2026-10-27 · G2 域侧落码）**：3s 预算的作用域 —— RAG 把 JWT 验签算进了预算，Go 不算

```
Go  ： 3s 预算【不含】JWT 验签（Authorize 在 observability(3s) **之外** ⇒ 验签耗时不吃预算）
RAG ： 3s 预算【含】JWT 验签（验签在 HandlerInterceptor ⇒ DispatcherServlet 内 ⇒ 预算 Filter 之下）
```

**① Go 侧依据（L1，逐行）** —— 真源 **10 个 api 全部**先关掉 go-zero 原生超时中间件：

| 事实 | 证据 |
|---|---|
| **原生 `TimeoutHandler` 不在链上** | `service/common/observability/http.go:30-34` `DisableNativeRestTimeout` ⇒ `conf.Middlewares.Timeout = false`（真源 10 个 api 全部调它，如 `service/message/api/messagecenter.go:40`） |
| **3s 预算实际由真源自己的中间件提供**，注册在 `server.Use` ⇒ 落在 `ng.middlewares` | `service/message/api/messagecenter.go:42` `server.Use(observability.NewHTTPMiddleware(...))` |
| **计时器起点 = 该中间件自己的入口** | `service/common/observability/http.go:53` `ctx, cancel := context.WithTimeout(r.Context(), timeout)` |
| **`ng.middlewares` 在 `Authorize` 之内** | `go-zero@v1.10.2/rest/engine.go:101-116` bindRoute：`:108` `appendAuthHandler`（JWT）**先**，`:110-112` `for _, mw := range ng.middlewares { chn = chn.Append(...) }` **后**<br>`go-zero@v1.10.2/rest/chain/chain.go:38-42` `Append` = 「加在请求流的**最后**」⇒ 越 Append 越靠内；`:86-88` `Then` 反向包裹 ⇒ 列表首位 = 最外层 |

⇒ 执行序：**原生中间件 → Authorize(验签) → observability(3s 计时) → handler** ⇒ **验签在 3s 预算之外**。

**② Go 侧活体探针（L4 · 2026-10-27）** —— 真源整仓复制到 `/tmp/goprobe`（**不动被冻结的真源**），
探针 `import` **真源自己的** `sea-try-go/service/common/observability`，启动路径与真源 api 逐行同构；
观测点 A 插在 observability 中间件**之前**、观测点 B 插在**之后**：

```
PROBE conf.Timeout=3000ms httpTimeout=3s nativeTimeoutMiddlewareEnabled=false
腿① 有效 token /probe/fast ： PROBE-A(outer) deadline_set=false claim_userId=12345
                              PROBE-B(inner) deadline_set=true remaining=3s claim_userId=12345  → HTTP 200
腿② 有效 token /probe/slow（handler 睡 4s）→ HTTP 503 @ 3.003s，body `Request Timeout`
腿③ 非法 token               → HTTP 401，body 空，且 A/B/HANDLER 三个观测点【一个都没打印】
```

> **腿③ 是决定性的**：若 `Authorize` 不是跑在所有 user middleware **之前**，观测点 A 必然会打印；
> 它没打印 ⇒ `Authorize` 在最外层 ⇒ **验签在 3s 预算之外**。
> 腿② 的 503 + `Request Timeout` 与 Go `http.go:88` `http.Error(w, httpTimeoutBody, 503)` 逐字一致。

**③ RAG 侧漂移形态（修前）** —— `service/common/observability/HttpMiddleware.java:270/294/304/322`：
预算在 submit worker **之前**起算（`deadlineNanos = startNanos + timeout`），`chain.doFilter` 整条下游
（含 DispatcherServlet 与其中的 `HandlerInterceptor`）都在 worker 里跑 ⇒ **验签耗时会吃掉 3s 预算**。

**④ 修法（域侧自修 · 2026-10-27）**

| 项 | 内容 |
|---|---|
| **落点** | 新增 `service/message/message-api/src/main/java/com/rag/message/message/api/auth/MessageJwtAuthFilter.java`（`@Component` + `@Order(HIGHEST_PRECEDENCE + 6)`）与 `MessageJwtSignatureVerifier.java`（持两把 key，按路径分派） |
| **只验签、只写 attribute、绝不写响应** | 验签失败也**放行** ⇒ `GoStyleMaxBytesFilter`(+8) 仍有机会写 **413** ⇒ 「413 压过 401」不变（Go 侧 MaxBytes 在 Authorize **之外**，也是 413 压过 401） |
| **401 仍由 interceptor 抛** | `AbstractMessageJwtAuthInterceptor` 改为**消费** Filter 的结果；Filter 未生效时**兜底就地验签**（安全不倒退，但守门会红） |
| **两把 key 按路径分派** | Go `routes.go:60-61` 用 UserAuth（8 条）、`:92-93` 用 AdminAuth（5 条）⇒ `verifyForPath` 按 `/message/v1/admin/**` 分派 |
| **形状不变** | 401 + **空 body**（Go `authhandler.go:106-122` 只 `WriteHeader(401)` 不写字节） |

**⑤ 守门 + 变异验证（`MessageApiJwtVerificationScopeTest`，7 条全绿）**

| 变异 | 预期 | 实测 |
|---|---|---|
| ① `@Order` +6 → **+8**（跑到 3s 预算之后） | 「JWT 在预算之外」断言红 | ✅ 红（1 failure，红在目标断言） |
| ② Filter **自己写 401** | 「413 压过 401」红 | ✅ 红（`MessageApiProtectionMvcTest` 报 413 ≠ 401） |
| ③ 摘掉 Filter 的 `@Component` | 「interceptor 消费 filter 结果」红 | ✅ 红（`expected:<filter> but was:<inline>`） |

> ⚠️ 守门断言的是**容器装配出的链**（`ServletContextInitializerBeans` 的排序输出），
> **不是**两个 `@Order` 数字（注解只是排序的依据，装配结果才是运行时事实）。

**⑥ 与 Go 的残余差异（如实登记）**：Go 的链只挂在**路由匹配**上 ⇒ 未注册路径（404）不验签；
Servlet Filter 在路由匹配**之前** ⇒ 本 Filter 会对 `/message/v1/**` 下的未匹配路径**多算一次 HMAC**
⇒ 但它**不写响应** ⇒ 最终仍是 **404**（与 Go 一致）⇒ **只影响 CPU，不影响任何响应**。

**⑦ 本轮实测（2026-10-27 · G2）**：`message-api` **98 例**（原 91 + 本轮新守门 7）、`message-rpc` 84、
`like-api` **89 例**（原 84 + 新守门 5）、`like-rpc` 107、`task-api` 97、`task-rpc` 111
⇒ **三域六模块全量 `mvn test` ⇒ BUILD SUCCESS，0 failures / 0 errors / 0 skipped**
（口径 = `mvn test`；`mvn verify` 口径另多 4 条只在 verify 阶段跑的 fat-jar 产物层用例）。
⚠️ 另：`message-rpc` 的 `MessageRpcSlowThresholdGateTest` 在**全量并发**下偶发
`UNAVAILABLE: Network closed for unknown reason` ⇒ **单独复跑 2/2 全绿** ⇒ 判为 **flaky（非回归）**
⇒ 已登记 `docs/DRIFT/message/rpc.md` 的 `DRIFT-MSG-V-032`。

> ⚠️ **本条与 `DRIFT-USERAPI-009②`（users 域）是同一条**：G1 已在 user/admin/follow 三域落码，
> 本条是 message 域的对应项；两条的 Go 侧依据**同源**（`DisableNativeRestTimeout` + `engine.go` 链序）。

### 4. `DRIFT-MSGAPI-010` 的修法（已定位，**本轮未实施**）

> **2026-10-29 对齐**：本条**已实施** —— 共享层 T-6 第二十五轮落码（`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127,151`）⇒ **现行态 = ✅ 已修**；本节原文（修法定位过程）**保留作历史留档**。


- **可修的一半**：「缺 charset」⇒ RAG 的 JSON 转换器 `defaultContentType` 就是 `application/json`
  ⇒ 给转换器补一个带 charset 的 media type 即可（落点在 api 侧的 `WebMvcConfig` 或共享的转换器配置）。
- **不可修的一半**：「分号后的空格」⇒ 见 `DRIFT-MSGAPI-012`（Tomcat 序列化行为）。
- ⚠️ **实施前必须先确认**：改了 charset 之后，**所有** api 的 JSON 响应头都会变
  ⇒ 属于**跨域影响**（20 个组件共享 `service/common`）⇒ **需用户拍板后再做**。

### 5. 本轮 api 侧实测数据

```
message-api：84 + 2（MessageApiTimeoutRealTomcatTest）= 86 tests / 0 failures / 0 errors / 0 skipped
真容器响应头：6 条路径 × 两侧全量 dump ⇒ 产物 /tmp/difftest-msg-headers/
3s 超时：503 + text/plain;charset=utf-8 + nosniff + "Request Timeout\n"   （真 Tomcat）
```

> ⚠️ **超时路径会打一条 `HttpMessageNotWritableException` 日志 —— 不是行为差异，别当 bug 修**

### 🆕 6. 第十轮新增守门（§5.3e 项⑤ · A3「路径归一」的真 Tomcat 探针 · 2026-10-27）

- **`MessageApiPathNormalizationRealTomcatTest`（3 条）** · **`MessageApiPathNormalizationControlTest`（2 条）** ⇒ ⇒ **共 5 条**
- **锁住的契约**：Go 侧 `go-zero@v1.10.2/rest/router/patrouter.go:48`（注册时 `path.Clean`）· `:60`（匹配时 `path.Clean`）⇒ ⇒ **路径变体命中同一条路由**
- **实测**：4 种变体（尾斜杠 / 重复斜杠 / `.` 段 / `..` 段）**与规范路径同状态且非 404**（A 组 401 ⇒ 路由匹配上了、被鉴权拦下）⇒ ⇒ **对照组（归一层缺席）实测 4 种变体全部 404** ⇒ ⇒ **Tomcat / Spring 自己不会归一它们** ⇒ ⇒ **共享层那层是承重件**
- **变异验证**（三域各做一遍）：域内放**同名但什么都不做**的 Filter Bean ⇒ 共享层那份被 `@ConditionalOnMissingBean(name=…)` 跳过（= **遮蔽**，不是叠加 —— 与 users 域「域内副本遮蔽共享层」同款真实形态）⇒ ⇒ **3 条红 2 条、`Errors = 0`**（上下文正常启动，不是启动期炸）⇒ ⇒ **红在两条目标断言上**（前提断言 + 变体断言）⇒ ⇒ 还原**逐字节一致** ⇒ ⇒ 复跑全绿
- ⚠️ **本轮暴露并已修的一条设计缺陷**：第一版用 `@Autowired GoStylePathNormalizingFilter`（**具体类型**）⇒ 遮蔽时**上下文启动期就炸** ⇒ ⇒ 守门会「**红在启动**」而不是「红在断言」⇒ ⇒ 改成**按 Bean 名 + 断言类型**（`containsBean` + `getBean` + `instanceof`）⇒ ⇒ 遮蔽现在**红在断言上**
- ⇒ ⇒ **「路径归一」这一格 = RAG 腿 L4**；**Go 腿 = 共享层那一次的活体（不是本域独立跑的）** ⇒ ⇒ **不整体升 L4** ⇒ 详见 `docs/DRIFT/message/vs-common.md` §4·十
> （`No converter for [class com.rag.common.response.Response] with preset Content-Type 'text/plain;charset=utf-8'`）
>
> 机理：`HttpMiddleware#writeTimeoutResponse` 已经**写完并 flush 了** 503 + `text/plain`；
> 随后超时异常继续往上抛 ⇒ Spring MVC 的 `@ExceptionHandler` 又想写一个 JSON `Response` 回来
> ⇒ 但 Content-Type 已被预设成 `text/plain` ⇒ 转换器不匹配 ⇒ 抛这条异常。
> **客户端早已收到正确的 503**（响应已提交，二次写入改不了）⇒ 守门测试断言的 4 项全部成立。
>
> ⚠️ 与本轮改动的**因果关系**：与本轮加的 `nosniff` **无关** ——
> 预设 Content-Type 的那行 `setContentType("text/plain; charset=utf-8")` 是**既有代码**（D-11 修复），
> `setHeader("X-Content-Type-Options", …)` 不参与转换器选择。
> ⇒ 归「既有噪音，本轮不改」：改它要动 api 侧的全局异常处理链，**超出本轮 scope**。


---

## 2026-10-24 第九轮（api 侧）：**V-028 的点名式防回退** + 挖出 **6 个 api 模块的同款「产物不可执行」**

> 本轮 api 侧**没有改任何业务逻辑**（V-018 / V-025 / V-026 都在 rpc 侧与共享层）。
> api 侧的产出是**两道守门** + **一条跨域发现**。

### 1. ✅ `DRIFT-MSG-V-028` 的点名式防回退

**新增 `MessageApiJarExecutableWiringTest`（2 例）**，断言 message-api 的 pom
**`<build><plugins>` 里真的有 `spring-boot-maven-plugin`**。

⚠️ **为什么这里只断言 pom、不断言 jar**：surefire 跑在 `test` 阶段，jar 是 `package` 阶段才生成的
⇒ **测试运行时 jar 还不存在** ⇒ 写「jar 不存在就跳过」会引入 `skipped`，
违反「绿灯必须额外断言 `skipped=0`」的纪律。
⇒ 「产物真的可执行」交给 `tools/difftest/check-jar-executable.sh`（build 完解包 MANIFEST）。

⚠️ **这条断言第一版被自己的注释打败**（实测翻车）：pom 的说明性注释里就写着
`<build>` 与 `spring-boot-maven-plugin` ⇒ 裸 `indexOf("<build>")` 先撞到**注释里那一次**
⇒ 修法：**先剥掉 XML 注释再取下标**。
⇒ 完整教训 ⇒ [`PITFALL-MSG-R-024`](../../Pitfalls/message/rpc.md)。

### 2. 🔴 `DRIFT-MSG-V-030`（新挖出 · **跨域待办**）：**6 个 api 模块的 jar 根本不可执行**

**实测（真实产物，2026-10-24，全仓 `mvn package` 之后逐个解包 MANIFEST）**：

| 模块 | 产物大小 | `Main-Class` | 判定 |
|---|---|---|---|
| `article-api` | **68 KB** | ❌ 无 | 🔴 不可执行 |
| `comment-api` | **52 KB** | ❌ 无 | 🔴 不可执行 |
| `follow-api` | **56 KB** | ❌ 无 | 🔴 不可执行 |
| `hot-api` | **32 KB** | ❌ 无 | 🔴 不可执行 |
| `like-api` | **48 KB** | ❌ 无 | 🔴 不可执行 |
| `task-api` | **24 KB** | ❌ 无 | 🔴 不可执行 |
| `favorite-api` / `admin-api` / `user-api` / `message-api` | 68–80 MB | ✅ `JarLauncher` | ✅ 可执行 |

⇒ 与 `DRIFT-MSG-V-028` **同款**（pom 没有 `<build>` 段 ⇒ 无 `repackage` ⇒ library jar）
⇒ **`java -jar` 报「中没有主清单属性」** ⇒ 那 6 个模块**从来没被打成过可部署产物**。

⚠️ **为什么能潜伏这么久**：本项目至今的每一项验证（测试全绿、`mvn package` 成功、
上下文能起、双侧对拍通过）**都发生在容器 / 测试环境里**，**从来没有碰过 jar 本身**
⇒ 这正是 §3.3「编译通过 ≠ 产物可运行」的形态。

**新增守门脚本**：`tools/difftest/check-jar-executable.sh`
⇒ 逐个 api 模块解包 `META-INF/MANIFEST.MF` 看 `Main-Class`，任一缺失即 `EXIT=1`
⇒ **2026-10-24 当场实测 `EXIT=1`**（6 个模块不合格）⇒ 门是有效的。

**⚠️ 归属**：属**别的域**，按域间纪律本工作树**不改它们的 pom** ⇒ 登记为跨域待办
（与 `DRIFT-MSG-V-029` 同款处理：登记 + 交接，不越界）。
⇒ 修法与清单 ⇒ 见 [`docs/DRIFT/message/rpc.md` 第九轮 · 第 4 节](rpc.md)。

---

## 2026-10-27 · G2 · U+FFFD 跨树复核：G3 报的「message-api 那条」**在本树已干净**（主树仍坏）

| G3 报的 | 本树实测（**字节级**） | 结论 |
|---|---|---|
| `service/message/message-api/src/test/java/com/rag/message/message/api/GoStyleGrpcClientBreakerE2ETest.java:43`：`响应码先U+FFFD×3 5001` | **FFFD = 0**；真实内容 = `断言 HTTP 响应码先是 5001、` —— ⚠️ **行号是 `:46` 不是 `:43`**（`:43` 是 G3 那棵树的行号） | 🟢 **已干净** |

⚠️ **判据本身的坑**：**`read` 的输出会把正常的「是」渲染成 `U+FFFD×3`** ⇒ **不能拿 `read` 的输出判 U+FFFD**；
`grep -rn $'\xef\xbf\xbd'` 在本机也会 0 命中 ⇒ **只认字节级扫描**（`b'\xef\xbf\xbd' in read_bytes()`）。

> 📌 **本节引述里的坏字符已改写为 ASCII 标记 `U+FFFD×3`**（3 个替换字符 = 1 个 UTF-8 汉字的 3 字节全丢）
> ⇒ **台账自身不再植入坏字节** ⇒ 复扫可保持 **0 命中**；需原样比对时按「`文件:行号` + `U+FFFD×3`」去主树/G1/G3/G4 树取原件。

🔴 **主树 / G1 树 / G3 树 / G4 树 仍各坏 1 处** ⇒ **因果坐实**：`ce81c5f^` ⇒ FFFD=**3**、`ce81c5f` ⇒ FFFD=**0**
⇒ 本树阶段 2 那个 commit 重写该文件区域时**顺带写对了中文**。
⇒ 该文件在**我域写权面** ⇒ 合并时**以本树版本为准** ⇒ **登记为合并后必查项**。

⚠️ **顺带提醒（别当「无关改动」误删）**：本树该文件相对主树还有**正常改动** ——
`RANDOM_PORT` → `DEFINED_PORT` ＋ `RealTomcatPort` 支撑类 ＋ 端口段注释
（属 commit `c6fdc1f`「A5 · 真容器测试端口段治理」，**不是** `ce81c5f`）⇒ 合并时**两处都要带**。

---

## 2026-10-29 · G2 收工轮（第二轮）· 🔴→✅ **Go 出处行号订正 · message-api 2 处**

> **归属**：`align/g2-message-like-task` 工作树产出 · **基线 commit** `5fa8558` · **核对日期** 2026-10-29
> **证据强度**：**E3**（go-zero v1.10.2 **逐行核验** + 改后逐条回验）
> **性质**：**P2 · B 类**（**不阻断**阶段 1 准入；**纯注释面**，产品行为零影响）

| # | 位置 | 改前 | **改后（真源实况）** | 性质 |
|---|---|---|---|---|
| 1 | `config/GoStyleMapStringValueDeserializer.java:16` | `core/mapping/unmarshaler.go:663-715` | **`:664-715`** | 起点多 1（`:663` 是空行）· 终点 `:715` 是 `processFieldPrimitiveWithJSONNumber` 的**闭合括号**（`:716` 空行 · `:717` 下一个 func） |
| 2 | `auth/MessageJwtSignatureVerifier.java:132` | `rest/token/tokenparser.go:80-84` | **`:81-86`** | 起止行错（`:80` 空行 · `:86` 闭合括号 · `:85` 是 `request.WithParser(newParser())`） |

> ⚠️ **与 like-api 的同一处是同款**（`LikeJwtSignatureVerifier.java:89`）⇒ **同一族、跨两个域** ⇒ 已一并修。
> ⚠️ **「结束行」最易写成「下一个空行 / 下一个函数起点」** ⇒ 复核动作 = **先看那一行是不是 `}`**，再回退一行确认 ⇒ 详见 `docs/DRIFT/like/rpc.md` §12.1。

### 复算命令

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/g2-message-like-task"
GZ="$HOME/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2"
sed -n '664p;715,717p' "$GZ/core/mapping/unmarshaler.go"   # ⇒ func 起点 / } / 空行
sed -n '80,87p'  "$GZ/rest/token/tokenparser.go"            # ⇒ 空行 / func doParseToken / } / 空行
```

---

## 🔴 P0 #8 **域侧** · message-api 的 **明文默认验签密钥** ⇒ 已去掉 ⇒ 缺变量/空串 ⇒ **启动失败**（2026-10-29 · G2 阶段 1）

> **阶段归属（`twoStage.md` §10）**：**A 类**（P0 #8「关键配置和敏感信息没有明显的不安全处理方式」）⇒ **域侧已修**（fail-closed）⇒ **根级**半边（compose 缺 `:?` 守卫 ＋ 根 README 的 `.env.example`）归「**合并收口执行者**」⇒ ⇒ **不阻断阶段 1**。
> **产出归属**：`align/r2-message-follow-secret` 工作树 · **核对日期** 2026-10-29。
> **性质**：**RAG 侧更严**（Go 侧写的是字面量）⇒ ⇒ 属**有意偏离**（不是「RAG 抄错了 Go」）⇒ 登记位置 = 本域台账（按 AGENTS.md「一条问题只在一个档里落笔」）。

### §5.3 差异与暂缓项（11 项）

| # | 项 | 内容 |
|---|---|---|
| **1** | **唯一编号** | `DRIFT-MSGAPI-016`（**新登记** · 域内）· **跨域同款**：`article-api` / `comment-api` / `favorite-api`（2026-10-29 · G3 · commit `45e0af4`）—— ⚠️ **本条比那三域多一层判据，见第 11 项** |
| **2** | **原始项目中的文件 / 方法 / 业务契约** | Go 真源：`service/message/api/etc/messagecenter.yaml:11`（`UserAuth.AccessSecret` = **36 字节 UUID**）与 `:15`（`AdminAuth.AccessSecret` = **15 字节**）⇒ ⇒ **Go 侧同样把密钥写成仓里的字面量**。<br>Go 侧消费点（逐层到消费点，AGENTS.md §3.3.1）：`service/message/api/internal/handler/routes.go:60` 的 `rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)` ＋ `:92` 的 `rest.WithJwt(serverCtx.Config.AdminAuth.AccessSecret)`。<br>RAG 侧（修前）：`service/message/message-api/src/main/resources/application.yml:84`（`${USER_JWT_SECRET:<36 字节 UUID>}`）＋ `:87`（`${ADMIN_JWT_SECRET:<15 字节>}`）—— **两段都带明文默认值** |
| **3** | **原始行为** | Go：**字面量** ⇒ 部署时不改也能起 ⇒ 但**任何拿到仓库的人都能用它签出合法 JWT** ⇒ ⇒ 该域的**鉴权等于没有**（除非运维记得改） |
| **4** | **阶段 1 当前行为（修前）** | ① 不设环境变量 ⇒ **用公开密钥正常起来** ⇒ 🔴 **token 可伪造**；② 设成空串 ⇒ Spring 把空串当合法值 ⇒ **空签名密钥正常起来** ⇒ 🔴 **token 可伪造**（HMAC 空密钥谁都能算） |
| **5** | **具体差异** | 修前：**两条路径都会「静默地不安全地起来」**。<br>修后：① **不设变量 ⇒ 启动失败** ② **设成空串 ⇒ 启动失败** ⇒ ⇒ **两条路都 fail-closed** |
| **6** | **暂缓或简化理由** | **不适用 —— 已修**。⚠️ **但有一处不能域侧单做**：根级 `docker-compose.yaml` 的 `USER_JWT_SECRET` / `ADMIN_JWT_SECRET` 注入**缺 `:?` 守卫** ⇒ ⇒ 不设变量时 compose 会把**空串**注入容器 ⇒ ⇒ 修后容器会**启动失败**（安全上是 fail-closed ✔）⇒ ⇒ 但运维看到的是 crash-loop 而不是 compose 级的清晰报错 ⇒ ⇒ **该半边属【根级文件】⇒ 归「合并收口执行者」**（根 README 无 `.env.example` 属 **P0 #5** · 同样根级） |
| **7** | **对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响** | **安全性**：**正面**（核心）—— 从「可静默用公开密钥起来」变成「起不来」⇒ ⇒ 消除「token 可伪造」。<br>**业务正确性**：**无影响**（变量被提供时，密钥取值与 Go 侧**逐字节一致** ⇒ 验签行为零变化）。<br>**数据一致性 / 可靠性**：**无影响**。⚠️ **运维**：**缺变量 ⇒ 起不来** ⇒ ⇒ 部署说明必须同步（根 README 无 `.env.example` ⇒ 根级 ⇒ 归「合并收口执行者」） |
| **8** | **是否阻断阶段 1** | **否**（域侧已修 ⇒ P0 #8 的**域侧部分**闭合）。⚠️ 根级半边（compose `:?` ＋ 根 README `.env.example`）**仍开** ⇒ ⇒ P0 #8 **整体**仍不能判 ✅ ⇒ ⇒ **收口执行者的事** |
| **9** | **阶段 2 的恢复或替换方案** | **不需要恢复**（这是「更严」的一侧）。阶段 2 要做的是**把密钥管理做完整**：① 根级 `.env.example`（列出**全部**必填变量）② 密钥轮换 ③ 部署期校验清单 ⇒ ⇒ **共享面 ⇒ 阶段 2 / 收口** |
| **10** | **阶段 2 的测试场景和通过标准** | ① **compose 侧**：`docker compose config` 在**不设** `USER_JWT_SECRET` / `ADMIN_JWT_SECRET` 时**必须报错**（`:?` 生效）⇒ **通过标准：非零退出 + 明确报错**。② **空变量起栈实测**：`USER_JWT_SECRET= docker compose up` ⇒ 容器**必须起不来**（fail-closed）⇒ **通过标准：日志里出现本域的 `IllegalStateException`**。③ **域侧回归**：`message-api` 全量测试全绿（密钥由 test scope 提供） |
| **11** | **当前证据及剩余证据缺口** | **已有**：**L4**（域内守门 `MessageApiPropertiesSecretGuardTest`：**7 条** · **真 `ApplicationContextRunner` ＋ 真 `SpringApplication`（完整 ConfigData 流程）** ⇒ 缺变量 / 空串 / 未解析占位符 ⇒ **上下文起不来**（`hasFailed()`）＋ 正常密钥 ⇒ 上下文正常起（对照组）＋ 判据边界（含「未解析占位符字面量」）＋ **读真 yaml 的绑定回归网**）＋ `message-api` 全量 **114 条 / 0F / 0E / 0S**（`mvn -o -B -pl service/message/message-api -am -DargLine=-Xmx2g test` ⇒ **BUILD SUCCESS** · 3:13 min）。<br>⚠️ **本条比 article/comment/favorite 那三域多一层判据（实测逼出来的，重要）**：那三域（commit `45e0af4`）的守卫**只判「空 / 空白」**，而它们在 yaml 注释里断言的「**缺变量 ⇒ 启动失败**」**实测不成立** —— Spring 的 `@ConfigurationProperties` 绑定器遇到**解析不出来**的占位符时**不报错**，而是把**占位符原文**（如 `"${USER_JWT_SECRET}"`）**当成值绑上去** ⇒ ⇒ 应用**照常启动**，且那个「密钥」是**公开可知的字面量** ⇒ ⇒ **本域的守卫额外识别「未解析占位符字面量」**（`MessageApiProperties.java:177` 的 `UNRESOLVED_PLACEHOLDER`）⇒ ⇒ 才真正做到「缺变量 ⇒ 启动失败」。<br>**剩余缺口**：① **compose 侧**未验证（**根级 ⇒ 归「合并收口执行者」**）② **根 README 无 `.env.example`**（P0 #5 · 根级） |

### §5.4 实际改造记录（8 项）

| # | 项 | 内容 |
|---|---|---|
| **1** | **修改前的实现和行为** | `message-api/src/main/resources/application.yml:84`：`access-secret: ${USER_JWT_SECRET:<36 字节 UUID>}`；`:87`：`access-secret: ${ADMIN_JWT_SECRET:<15 字节>}` ⇒ **两段都有默认值** ⇒ 不设变量也能起 ⇒ 用仓里的公开密钥验签 |
| **2** | **具体问题** | 「仓里写死的公开密钥」＋「Spring 把空串当合法值」＋「Spring 把未解析占位符**原文**当合法值」⇒ ⇒ **三条路都能静默地不安全地起来** ⇒ ⇒ P0 #8 的域侧缺口 |
| **3** | **修改后的实现** | ① yaml：`:100` `access-secret: ${USER_JWT_SECRET}`、`:103` `access-secret: ${ADMIN_JWT_SECRET}`（**去默认值**；**键路径逐字未动**：`message-api.user-auth.access-secret` / `message-api.admin-auth.access-secret`）＋ 注释写明「为什么」与「测试从哪来」。<br>② `MessageApiProperties`：**新增 `@PostConstruct verifyAccessSecrets()`**（`:91`）⇒ 逐段调 `verifyUserAccessSecret()`（`:124`）/ `verifyAdminAccessSecret()`（`:140`）⇒ 值为 `null`/空串/全空白/**未解析占位符字面量** ⇒ 抛 `IllegalStateException` ⇒ **启动失败**。<br>③ 新增 `service/message/message-api/src/test/resources/application.properties`：**test scope** 提供两段密钥（值 = 测试里签 token 的常量）⇒ ⇒ **测试不依赖环境变量** ＋ **不进产物 jar** ⇒ 生产拿不到默认值 |
| **4** | **修改理由** | ① 去默认值只能堵「变量被提供」那一半 ⇒ 堵不住「变量没设」（**实测**：绑定器留占位符原文）也堵不住「变量是空串」（**Spring 的 `PropertyPlaceholderHelper` 实测行为：存在但为空串 ⇒ 解析为空串、不走默认值**）⇒ ⇒ **三条都必须由代码校验**才真正 fail-closed。<br>② 用 `@PostConstruct` 而不是 `@Validated + @NotBlank` ⇒ **零新增依赖**（不引入 `spring-boot-starter-validation`）。<br>③ **两个 `@PostConstruct` 写成两个方法 ⇒ 报错顺序不确定**（实测两次运行分别先抛 user 段与 admin 段 ⇒ 同一 bean 内多个 `@PostConstruct` 的执行顺序不由 JVM 规范保证）⇒ ⇒ 改成**一个入口 + 顺序固定的逐段校验**。<br>④ 测试用 test scope 的 `application.properties` 而不是改 14 个 `@SpringBootTest` 类 ⇒ ⇒ **一处改动** ＋ **不污染主配置**（test scope 不进 jar） |
| **5** | **实际变更文件** | ① 改 `service/message/message-api/src/main/resources/application.yml`（**只改值** ＋ 注释）<br>② 改 `…/config/MessageApiProperties.java`（+`@PostConstruct` 校验 ＋ 类级 javadoc 订正「上表是 Go 值、不是 RAG 侧默认值」）<br>③ **新增** `…/src/test/resources/application.properties`<br>④ **新增** `…/src/test/java/com/rag/message/message/api/config/MessageApiPropertiesSecretGuardTest.java`（**7 条**） |
| **6** | **测试场景和断言** | ① **空串 / 未设 ⇒ 上下文起不来**（`ApplicationContextRunner` ＋ `hasFailed()`）② **正常密钥 ⇒ 上下文正常起**（对照组 ⇒ 证明不是「无脑拒绝启动」）③ **判据边界**：`null` / `""` / `"   "` / **未解析占位符字面量** ⇒ 抛；正常值 ⇒ 不抛（两段各测一遍 ＋ 组合入口）④ **读真 yaml**：绑定进嵌套字段（键路径写错 ⇒ 红）⑤ **读真 yaml ＋ 变量未设 / 空串 ⇒ 起不来** ⑥ **生产同款启动**（真 `SpringApplication` ＋ 完整 ConfigData ＋ `spring.config.location` 只加载 `application.yml`）＋ 变量未设 ⇒ **起不来**（⇒ 「缺变量 ⇒ 启动失败」的**生产等价**证据） |
| **7** | **实际执行结果** | `mvn -o -B -pl service/message/message-api -am -DargLine=-Xmx2g test` ⇒ **BUILD SUCCESS**（3:13 min）⇒ **message-api 114 条 / 0F / 0E / 0S**（含守门 7 条全绿）。<br>⚠️ 另注：`service/common` 的 `RpcInterceptorClientDeadlineGateTest.effectiveTimeoutMustInheritClientDeadline` 在同一轮**偶发红**（**超时继承的时序敏感**）⇒ 重跑即绿 ⇒ **与本条无关**（`service/common` 与 message/follow **无依赖关系**，且本轮**未改动** `service/common` 任何文件） |
| **8** | **尚未解决的问题 / 对阶段 2 的影响** | ① **compose 侧 `:?`**（**根级 ⇒ 归「合并收口执行者」**）② **根 README 的 `.env.example`**（P0 #5 · 根级 ⇒ 合并收口执行者）③ **跨域同款缺口**：article-api / comment-api / favorite-api 的守卫**只判「空 / 空白」** ⇒ 它们的「缺变量 ⇒ 启动失败」**实测不成立**（绑定器留占位符原文 ⇒ 应用照常启动）⇒ ⇒ **本轮已在本域修好，但那三域需同款补齐**（**不属本 lane 写权面 ⇒ 上报项**）④ **阶段 2**：完整的密钥管理（轮换 ＋ `.env.example` ＋ 部署清单） |

### 复算命令

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r2-message-follow-secret"
# ① 明文默认值已清零
grep -rnE '\$\{(USER_JWT_SECRET|ADMIN_JWT_SECRET):[^}]' service/message/ service/follow/ ; echo "exit=$? (1 = 0 命中)"
# ② 域侧守门（缺变量 / 空串 / 未解析占位符 ⇒ 启动失败；正常密钥 ⇒ 正常起）
mvn -o -B -pl service/message/message-api -am -DargLine=-Xmx2g \
    -Dtest=MessageApiPropertiesSecretGuardTest -DfailIfNoTests=false test
# ③ 域内全量
mvn -o -B -pl service/message/message-api -am -DargLine=-Xmx2g test
```
