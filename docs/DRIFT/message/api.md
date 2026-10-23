# `service/message/message-api` 语义对照台账（Go ↔ Spring Boot）

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
> - `真源-双侧探针` = Go 侧跑 `go-zero v1.10.2 core/mapping` + RAG 侧跑**真实 DTO + 真实 `MessageGoStyleCoercion`**（与仓库内 `tools/semantic-probe` 同构；本轮扩展到 51 例，见 §8）
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
**当前状态**：message-api **59 tests 全绿**（41 条真实 MVC 管线 + 18 条纯单测）；
`tools/semantic-probe/run-parity.sh` **23/23 两侧一致，`EXIT=0`**。

#### 三条漂移：前 2 条来自子会话验证，第 3 条是**修前 2 条的过程中新挖出来的**

| # | 输入 | Go 实测 | 修复前 RAG | 性质 | 状态 |
|---|---|---|---|---|---|
| **DRIFT-MSGAPI-001** | `{"conversation_id":123}`（JSON **数字**） | **接受 → 123** | **拒绝 → 5001** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修 |
| **DRIFT-MSGAPI-002** | `{"offset":"10"}`（**无** `,string`，JSON 字符串） | **拒绝 → 5001** | **接受 → 10** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修 |
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
- Go 侧 `core/mapping/unmarshaler.go:889-894`（Kind 判定）、`:1115-1125`（`case string` + `case json.Number` 双分支）、
  `core/jsonx/json.go:74-77`（`UseNumber`）、`core/mapping/utils.go:376-377`（`,string` → `FromString=true`）
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

根因在 go-zero 的选项解析：`core/mapping/utils.go:372` 的 `parseOption` switch **只认三种**
（`inherit` / `string` / 以 `optional` 开头的），**`omitempty` 不在其中** ⇒ 它不设置任何 unmarshal 选项
⇒ `Optional` 保持 `false` ⇒ `core/mapping/unmarshaler.go:856-862` 的 `if mapValue == nil` 落到
`return fmt.Errorf("field %q mustn't be nil", key)`。

**`,omitempty` 只控制出参是否省略，与「入参可否为 null」无关。** 把它当 optional 是一个很容易自然发生的误解。

Evidence：
- Go 侧 `service/message/api/internal/types/types.go:18` vs `service/user/user/api/internal/types/types.go:10`
- go-zero v1.10.2 `core/mapping/utils.go:372-385` + `core/mapping/unmarshaler.go:856-862`
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
1. 「Go yaml 没写 MaxBytes/MaxConns/Breaker」——其实由 `rest/config.go` 的 `default` tag 开启；
2. 「Go 侧没有 client 熔断」——其实由 `zrpc/internal/config.go:14` 的 `default=true` 开启。
⇒ 两处都是**「默认值藏在框架 tag 里，而对照时只看了数据文件」**。
**否定式断言无法用「没找到」来证实**，只能靠找到那个 tag。

**必须做的三件事**：① 论证里每个 Go 事实都带 `文件:行号`；② 凡写「与 Go 一致」，
必须有**同时跑两侧**的探针；③ 修复漂移时**先改/删错误断言，再改实现**，否则会被测试挡住。

#### ✅ 请求保护层：四层全部补齐（2026-10-22）

此前记为「RAG 三个全无、需决策」。现已实现完毕，新增包
`service/message/message-api/src/main/java/.../protection/`（7 个类）+ 4 个测试类（**30 例**）。

**先说清一件事：这四层在 Go 的 yaml 里一个字都没写，但它们是开启的。**
依据是 go-zero v1.10.2 的两处 tag：`rest/config.go:52-56`（`MaxConns=10000` /
`MaxBytes=1048576` / `CpuThreshold=900`）与 `rest/config.go:12-22` `MiddlewaresConf`
九个开关**全部** `json:",default=true"`。⇒ **照 yaml 逐字段对，永远看不见这四层。**

| 层 | Go 出处 | 拒绝响应 | RAG 实现 | 测试 |
|---|---|---|---|---|
| 请求体上限 | `rest/handler/maxbyteshandler.go:10-25` | **413 裸响应** | `GoStyleMaxBytesFilter` | MVC 3 例 + **真 Tomcat 3 例** |
| 并发上限 | `rest/handler/maxconnshandler.go:12-36` | **503 裸响应** | `GoStyleMaxConnsFilter` | 6 例（含并发/异常归还） |
| CPU 降载 | `rest/handler/sheddinghandler.go:20-52` + `core/load/adaptiveshedder.go` | **503 裸响应** | `GoStyleCpuSheddingFilter` + `GoStyleAdaptiveShedder` | 10 例 |
| 熔断 | `rest/handler/breakerhandler.go:19-44` + `core/breaker/googlebreaker.go:52-79` | **503 裸响应** | `GoStyleBreakerFilter` + `GoStyleBreaker` | 8 例 |

##### 三条必须记住的可观察契约（都偏离直觉）

1. **裸状态码 = 无 body、无 Content-Type、不走 envelope。**
   Go 侧四处都是裸 `w.WriteHeader(code)`，不调 `httpx.Error`。
   RAG 因此**刻意不用 `sendError`**（那会让容器补 `text/html`），改用 `setStatus` + 不写 body。
   ⇒ 这与本模块「所有业务错误恒 200 + envelope」的约定**刻意不同**：保护层在到达业务逻辑前就拒了。

2. **413 压过 401。** Go `rest/engine.go:105-112` 先装 `buildChainWithNativeMiddlewares`
   （内含 MaxBytes，`:164`）**再** `appendAuthHandler`（`:109`）；而 `chain.Append` 是
   「加在请求流的最后」（`rest/chain/chain.go:38-43`）⇒ **MaxBytes 在鉴权之外**。
   可观察后果：**往受保护端点发 2 MiB 无 token 请求，Go 返回 413 而不是 401。**
   RAG 侧 MaxBytes 是一个 Servlet `Filter`，而**所有 Filter 都包在 `DispatcherServlet` 外、
   `HandlerInterceptor`（JWT 鉴权）跑在其内** ⇒ 「413 早于 401」来自**容器结构**，
   **不是**来自 `@Order` 数值。（⚠️ 2026-10-22 实测更正：把 `@Order` 改成 `LOWEST_PRECEDENCE`
   后断言<b>依然绿</b>；能真正让断言变红的是「让该层失效」——摘掉 `@Component` ⇒ 413 变 401。）
   已有两条测试锁死（MockMvc 装配 + 真 Tomcat），且做过**变异验证**：关掉 MaxBytes → 413 变 401 → 红。

3. **⚠️ 熔断<b>不会</b>被下游 RPC 失败触发。** breaker 的判据是
   `cw.Code < 500 ⇒ Accept()`（`breakerhandler.go:39`），而本模块所有业务错误
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

#### 🟡 其余已留档、待决策的差异（不再单开编号）

| 项 | Go | RAG | 建议 |
|---|---|---|---|
| ~~请求保护层~~ | `MaxBytes` 1MB→413、`CpuThreshold` 900→503、`MaxConns` 10000、**`Breaker` 熔断** | ✅ **已全部补齐**（2026-10-22）⇒ 见下方「请求保护层」小节 | — |
| 404 body | `"404 page not found\n"` + text/plain + nosniff | 空 body | 建议补 |
| 405 头 | 带 `Allow` | 无 `Allow` | 建议补 |
| 成功响应 Content-Type | `application/json; charset=utf-8` | 疑似无 charset | **未验证**，需真容器 curl |
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
- `zrpc/internal/config.go:14` — `ClientMiddlewaresConf.Breaker bool json:",default=true"`
- `zrpc/internal/client.go:112-113` — `if c.middlewares.Breaker { append(..., clientinterceptors.BreakerInterceptor) }`
- `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` —
  `breaker.DoWithAcceptableCtx(ctx, path.Join(cc.Target(), method), ...)` ⇒ **每个 RPC 方法一个 breaker**
- Go `etc/messagecenter.yaml` 未配 `Middlewares` ⇒ 走 `default=true`
- 实现：`protection/GoStyleGrpcClientBreakerInterceptor`（**复用 `GoStyleBreaker`**，因 go-zero 的
  client breaker 与 HTTP 层 breaker 是同一个 googleBreaker）+ `GoStyleCircuitOpenException`

##### 为什么它当初没被发现（比缺陷本身更值得记）

`zrpc/internal/codes/accept.go:9-17` 的 `codes.Acceptable` 只把
`DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted`
判为失败；而 message-rpc 的业务错误（逐个 `errWithBizCode` 调用点核对）
**只用** `InvalidArgument / NotFound / PermissionDenied`，**全都不在失败名单里**
⇒ **业务错误在 Go 侧被记为 Accept，不参与熔断**。
⇒ 正常流量与业务错误路径下两侧**行为完全一致**，只有「下游返回 5xx 类 gRPC code 或超时」才分岔。
**任何只测业务路径的验证（含本项目现有的 41 条 MVC 管线用例）都测不出这条。**

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
  `MessageApiMvcPipelineTest` 的 **41 条 MVC 管线用例**（单进程、快、能进 CI）+ 
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
| **Inputs** | JSON body（无 query、无 path var、无 header 业务字段）；JWT `Authorization: Bearer` | `handler/message/*.go` 全部 `httpx.Parse(r, &req)`；`rest/httpx/requests.go:50-77` |
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
   1. 按 rest/engine.go 的 chain 顺序过保护层（见 §5.1）
   2. rest/handler/authhandler.go:54-82 验签 → 非标准 claim 逐个塞进 ctx
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
   1. Servlet Filter 链（见 §5.1），最外层是 HttpMiddlewareFilter（@Order = HIGHEST_PRECEDENCE+5）
   2. HandlerInterceptor.preHandle：验签 → 只把 claim `userId` 的**原始对象**塞进 request attribute
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

> Result 只用 5 个枚举值。Evidence 列：`P` = 真源-双侧探针（51 例，见 §8）；
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
| client 熔断 | 开（`zrpc/internal/config.go:14` default=true） | 开（`GoStyleGrpcClientBreakerInterceptor`） | EQUIVALENT | S |
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
| **超时作用域** | 中间件在 **auth 之后**，只覆盖「解析 + 业务」 | Filter 在 **最外层**，覆盖「鉴权 + 解析 + 业务」 | OBSERVABLE DIFFERENCE（范围更大，低影响） | S |
| 事务边界 | 无（api 不碰 DB） | 无（无 `@Transactional`） | EQUIVALENT | S |
| 观测端点 | `:16066/metrics` + `/healthz` + pprof（`DevServer`） | `:8892/actuator/{health,info,prometheus}` | OBSERVABLE DIFFERENCE（运维面） | S |
| 启动失败模式 | 无 etcd → `MustNewClient` **panic** | Nacos 不可达 → 启动期/首调失败 | OBSERVABLE DIFFERENCE | S |

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 中间件 / 拦截顺序（这是本组件最容易被"类比"错的一层）

**Go（仅对已匹配路由生效，`rest/engine.go:101-116` + `:130-171`）**，由外到内：

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
 → HttpMiddlewareFilter（@Order HIGHEST_PRECEDENCE+5，3s 超时 + ThreadLocal deadline）
 → GoStyleProtectionFilter × 4（MaxBytes / MaxConns / CpuShedding / Breaker）
 → DispatcherServlet（404/405 在此产生）
 → HandlerInterceptor（JWT 401）
 → GoStyleJsonBodyConverter 解析
 → Service
```

**可观察后果（已逐条核对）**：
1. **413 压过 401**：两侧一致 ✅（Go 靠 chain 顺序，RAG 靠 Filter 在 DispatcherServlet 之外）
2. **404/405 不过 auth、不过保护层**：两侧一致 ✅
3. ⚠️ **超时范围不同**：Go 的 3s 不含鉴权，RAG 的 3s 含鉴权（JWT 验签通常 <1ms，实际不可观测）
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
| 17 | **Timeout** | ⚠️ OBSERVABLE DIFFERENCE | 作用域不同（Go 在 auth 后 / RAG 在最外层）；**且 503 分支至今无运行证据**（§9 UNRESOLVED-004） |
| 18 | **Retry** | ✅ EQUIVALENT | 两侧都无；RAG 的「禁止加 retry」有 javadoc 留痕并指向 `zrpc/config.go` 无 `Retries` 字段 |
| 19 | **Serialization** | ❌ **NOT EQUIVALENT** | `-005` ~ `-009` 五条 + 键序差异 + Content-Type charset（§7） |
| 20 | **Configuration** | ⚠️ 部分 OBSERVABLE | yaml → `@ConfigurationProperties` 逐字段对齐（含**两侧都不读**的 `accessExpire`）；但 §3.3.1 的默认值全在框架 tag 里，本轮逐条核对了 MaxBytes/MaxConns/CpuThreshold/Breaker/Timeout 五处 ✅；启动失败模式不同（panic vs 延迟失败） |
| 21 | **Lifecycle** | ⚠️ 部分 OBSERVABLE | 初始化顺序对齐（配置 → 服务上下文 → 路由）；`metrics.InitMetrics` 的 `sync.Once` 对应 Micrometer 按需注册 ✅；但 §6.3 问题 4 的 Flyway 账本问题**不属本组件**（api 无 DDL） |

---

## 7. Semantic Drifts（§27 + §36）

### 7.0 复核结论：DRIFT-MSGAPI-001 / -002 / -003 **仍然关闭**

**独立复核方式**：不复用首轮的 23 例，而是本轮新建一套 51 例双侧探针
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

### DRIFT-MSGAPI-005（2026-10-23 新发现 · **未修**）布尔字段收 JSON **整数**：Go 拒 / RAG 收

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
  - Go：`core/mapping/unmarshaler.go:648-650`（`case json.Number` 先于 `typeKind == valueKind` 判定）
    → `:664-715` `processFieldPrimitiveWithJSONNumber` 的 `switch typeKind` **没有 `reflect.Bool` 分支**
    → `:708-709` `default: return newTypeMismatchErrorWithHint(...)`。
    消费点即此行，不是 struct tag 声明行。
  - RAG：`config/MessageGoStyleCoercion.java` 的 `applyTo` 只对
    `LogicalType.Integer` / `LogicalType.Boolean` 设了 `CoercionInputShape.String` 一条。
  - **实测（`真源-双侧探针`，本轮 51 例之 A1–A3 / A7–A8）**：
    ```
    A1 unread_only 收 JSON 数字 1 | Go: REJECTED (type mismatch … expect "bool", actual "number") | RAG: ACCEPTED → true
    A2 unread_only 收 JSON 数字 0 | Go: REJECTED | RAG: ACCEPTED → false
    A3 unread_only 收 JSON 数字 2 | Go: REJECTED | RAG: ACCEPTED → true
    A7 broadcast 收 JSON 数字 1   | Go: REJECTED | RAG: ACCEPTED → true
    A8 broadcast 收 JSON 数字 0   | Go: REJECTED | RAG: ACCEPTED → false
    ```
  - **Classification**：**SEMANTIC MISMATCH** · Risk: **HIGH**（`broadcast` 误触发全量推送）

---

### DRIFT-MSGAPI-006（2026-10-23 新发现 · **未修**）`map[string]string` **值**收 JSON 数字：Go 收 / RAG 拒

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
  - Go：`core/mapping/unmarshaler.go:385-410` `generateMap` 的 `default:` 分支里**显式有**
    `case json.Number: setValueFromString(dereffedElemKind, target.Elem(), v.String())`
    ⇒ `convertTypeFromString(String, "1")` 返回 `"1"`（`core/mapping/utils.go:138-139`）。
  - 对照：顶层标量字段走的是 `processFieldPrimitiveWithJSONNumber`（`unmarshaler.go:664-715`），
    `default:` 分支直接报错。**两个判据不一致，是本条的全部根因。**
  - **实测（`真源-双侧探针` B1）**：`Go ACCEPTED | RAG REJECTED (Cannot coerce Integer value (1) …)`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: MEDIUM

---

### DRIFT-MSGAPI-007（2026-10-23 新发现 · **未修**）`map[string]string` **值**显式 null：Go 拒 / RAG 收

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
  - Go：`core/mapping/unmarshaler.go:411-417`（`default:` + `dereffedElemKind != keythValue.Kind()`）
  - **实测（`真源-双侧探针` B3）**：`Go REJECTED (type mismatch) | RAG ACCEPTED`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: LOW-MEDIUM

---

### DRIFT-MSGAPI-008（2026-10-23 新发现 · **未修**）`,optional` 标量字段**显式 null**：Go 收（零值）/ RAG 拒

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
  - Go：`core/mapping/unmarshaler.go:854-863`
    ```go
    if mapValue == nil { if opts.optional() { return nil }
                         return fmt.Errorf("field %q mustn't be nil", key) }
    ```
  - **实测（`真源-双侧探针` C5 / C6 / D4）**：
    `Go ACCEPTED | RAG REJECTED (Cannot map null into type int …)`
  - **Classification**：**SEMANTIC MISMATCH** · Risk: MEDIUM

---

### DRIFT-MSGAPI-009（2026-10-23 新发现 · **未修**）出参 `affected`：Go **数字** / RAG **字符串**
**🔴 本项目第二次「绿测试守卫错误答案」，形态与首轮 DRIFT-MSGAPI-003 完全同构**

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

### DRIFT-MSGAPI-010（2026-10-23 新发现 · **未修**）成功 / 错误响应的 `Content-Type` 缺 `charset`

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
- **⚠️ 为什么现有 41 条用例抓不到**：`MessageApiMvcPipelineTest` 有 17 处
  `.contentType(MediaType.APPLICATION_JSON)` 断言，而 MockMvc 的
  `ContentTypeResultMatcher` 用的是 `expected.isCompatibleWith(actual)` ——
  `application/json` 与 `application/json;charset=utf-8` **互相 compatible**。
  ⇒ **这些断言对 charset 完全不敏感**（与 `AGENTS.md` §3.3「代码里写了 ≠ 客户端收到」同源）。
- **Classification**：**OBSERVABLE DIFFERENCE** · Risk: LOW
- **Evidence**：
  - Go：`rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)`）
    + `rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"`）
  - RAG：**转换器级运行实测**（用真实 `MessageGoStyleCoercion` 的 mapper 构造
    `AbstractJacksonHttpMessageConverter(mapper, MediaType.APPLICATION_JSON)` 并 `write`）：
    `Content-Type = [application/json]`，`selectedContentType=null` 时同样是 `[application/json]`。
  - ⚠️ 证据边界：**转换器级**，非真容器 HTTP 往返（真 Tomcat 的 413/503 已验，
    但「正常 envelope 响应的完整响应头 dump」仍无）。

---

## 8. Test Cases（§29 + §30）

### 8.1 本轮双侧探针（51 例，`真源-双侧探针`）

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
| CASE-15 Serialization Edge Case | ⚠️ **部分（本轮补齐一半）** | 入参：本轮 51 例；出参：本轮首次覆盖（`affected` 被抓出） |

### 8.3 🔴 现有「41 条 MVC + 23 例对拍」**未覆盖**的维度（本轮明确清单）

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
| 10 | **超时作用域差异**（Go 在 auth 后 / RAG 在最外层） | 静态记录 | 无活体对拍 |
| 11 | **响应序列化里 map 键序**（Go 排序确定 vs RAG 不确定） | ⚠️ 静态记录（首轮已留档） | 无运行证据 |
| 12 | **`AdminSendNotificationReq.extra` 元素级 null** | ✅ 本轮补齐 | — |
| 13 | **`FAIL_ON_NULL_FOR_PRIMITIVES` 正交开关** | ✅ 本轮补齐（抓出 008） | — |

---

## 9. Unresolved（§37 · **不得为空才允许下结论**）

### UNRESOLVED-001
- **Question**：Go 真源 `message-rpc` 的**响应语义**（哪些 code / 哪些 description）是否与 RAG 侧 `message-common` 一致？
- **Go Evidence**：`service/message/rpc/**`（本轮**未读**，属 `message-rpc` 组件的审查范围，由另一并行会话负责）
- **Java Evidence**：`message-common` 的 `MessageGrpcError` / `MessageErrorMessages`（**未改、未验证**）
- **Why Unknown**：`DRIFT-MSGAPI-004` 的一条核心论证依赖「message-rpc 的业务错误只用
  `InvalidArgument / NotFound / PermissionDenied`」，而那条断言首轮是**逐个调用点核对**得出的，
  本轮**没有重做**该核对。
- **Impact**：`§4.4` 的「gRPC 错误 → 业务码」与「熔断 acceptable 判据」两行的最终结论。
- **Required Confirmation**：与 `message-rpc` 会话的台账合并判读（两侧都写「business errors 用哪三个 code」）。

### UNRESOLVED-002
- **Question**：RAG 侧 `MessageGoStyleCoercion` 的规则**是否真的作用在 Spring MVC 实际使用的那个 mapper 上**？
- **Go Evidence**：不适用。
- **Java Evidence**：`WebMvcConfig#goStyleCoercionCustomizer` 注册 `JsonMapperBuilderCustomizer`
  —— **本轮未跑任何装配层断言**（首轮的 41 条用例间接证明了它生效：它们断言了
  `{"offset":"10"}` 被拒，这条只可能来自 builder 上的 coercion）。
- **Why Unknown**：本轮的 51 例探针用的是 `JsonMapper.builder() + MessageGoStyleCoercion.applyTo`，
  与 MVC 配置**同构但不同实例**。它证明「规则本身是什么」，不证明「MVC 用的是这条规则」。
- **Impact**：`DRIFT-MSGAPI-005` ~ `-008` 的**修法方向**（改 builder 还是改 DTO）依赖这个前提。
- **Required Confirmation**：修 005 时同步补一条「规则进了 MVC mapper」的装配断言
  （首轮教训：`GoStyleGrpcClientBreakerWiringTest` 就是为这一层存在的）。

### UNRESOLVED-003
- **Question**：响应头在**真容器**下的完整形状（Content-Type / Content-Length / nosniff / Allow）两侧是否逐字节一致？
- **Go Evidence**：`rest/httpx/responses.go:175-196`、`rest/internal/header/headers.go:17`
- **Java Evidence**：本轮只到**转换器级**（`application/json`）；真 Tomcat 只验过保护层的裸 413/503。
- **Why Unknown**：MockMvc 断言对 charset 不敏感（§7 DRIFT-MSGAPI-010 已说明）；
  真 Tomcat 那 3 例测的是保护层路径，不经过消息转换器。
- **Impact**：404 / 405 / 正常响应三条响应头全部停在 `OBSERVABLE DIFFERENCE` 或未判。
- **Required Confirmation**：真容器 curl 两端 dump 全部头（首轮「下一步建议」第 3 条，至今未做）。

### UNRESOLVED-004
- **Question**：3s 超时触发时，RAG 是否真的写出 `503 + text/plain; charset=utf-8 + "Request Timeout\n"`？
- **Go Evidence**：`service/common/observability/http.go:88`（`http.Error(w, httpTimeoutBody, statusCode)`）
  + `rest/handler/timeouthandler.go`（已被 `DisableNativeRestTimeout` 关掉，不参与）
- **Java Evidence**：`HttpMiddleware#writeTimeoutResponse`（**静态读过，无运行证据**）
- **Why Unknown**：首轮「下一步建议」第 4 条（stub `sleep(5000)`）**至今未实现**；
  `MessageApiProtectionRealTomcatTest` 的 3 例只覆盖 MaxBytes 413，不触达超时分支。
- **Impact**：§6 的 Timeout 维度只能停在「设计对齐 + 静态一致」，不能写「已验证」。
- **Required Confirmation**：补一条真触发测试（stub 睡眠 > 3s）并 dump 响应头与 body。

### UNRESOLVED-005
- **Question**：`extra` 落库后的**键序**是否影响客户端？
- **Go Evidence**：`encoding/json` 对 `map[string]string` **按键排序**输出（确定性）
- **Java Evidence**：`MessageApiSupport#toNotificationListResp` 用 `LinkedHashMap`
  包 proto 的 `getExtraMap()`（protobuf Java 的 map 迭代顺序**不确定**）
- **Why Unknown**：本轮只确认了两侧**语义等价**（键值集合相同）；未做字节级对拍，
  且 proto map 的迭代顺序本就不保证。
- **Impact**：仅字节级差异，JSON 语义等价。首轮已留档，本轮维持。
- **Required Confirmation**：若未来出现按字节比对 body 的契约测试，需先决定「是否排序」。

---

## 10. 最终判定（§39 · 18 项 checklist）

| # | 检查项 | 结论 | 备注 |
|---|---|---|---|
| 1 | public behavior 一致？ | ❌ | 13 条路由 / 方法 / 鉴权边界一致；`affected` 的 JSON 类型不一致（-009） |
| 2 | 正常路径一致？ | ✅ | 13 条路由 + 响应形状（`data:{}` 而非 `null`、空 `items` 为 `[]`）逐条一致 |
| 3 | 边界输入一致？ | ❌ | 布尔收整数（-005）、`extra` 值收数字（-006）、`extra` 值 null（-007）、optional 标量 null（-008） |
| 4 | NotFound / Empty / Null 一致？ | ❌ | `null` 在 4 个字段上两侧结论相反（-005 ~ -008）；`NotFound` 属 rpc 侧，UNRESOLVED-001 |
| 5 | Error meaning 一致？ | ✅ | 码表 20 条逐条对齐；解析失败两侧都折叠成 5001 |
| 6 | Error propagation 一致？ | ✅ | `errors.As` ↔ `BusinessException`；`handleAny` 兜底 5001；熔断单列 1015 |
| 7 | External dependency failure 一致？ | ⚠️ | 设计对齐；断连 / 超时**无运行证据**（§8.3 #9） |
| 8 | Cache hit / miss 一致？ | ➖ N/A | 无缓存 |
| 9 | Transaction boundary 一致？ | ✅ | 两侧都无事务（api 不碰 DB） |
| 10 | State transition 一致？ | ✅ | 无业务状态；metric 计数时序一致 |
| 11 | Side effects 一致？ | ⚠️ | 业务副作用无差异；**准入控制有差异**（worker 池 503 为 RAG 新增） |
| 12 | Ordering 一致？ | ✅ | 413 > 401 > 解析 > 身份 > 参数 > RPC 两侧逐条一致（含 404/405 不过 auth） |
| 13 | Retry 一致？ | ✅ | 两侧都无 |
| 14 | Timeout scope 一致？ | ⚠️ | **作用域不同**（Go 在 auth 后 / RAG 在最外层）；503 分支零运行证据 |
| 15 | Concurrency semantics 一致？ | ⚠️ | MaxConns 作用域不同（每路由 vs 全局）+ worker 池上限为 RAG 新增 |
| 16 | Auth / Authorization 一致？ | ✅ | 两把密钥、claim 判定、401 空 body 全部一致 |
| 17 | Serialization semantics 一致？ | ❌ | `,string` 入参 ✅ / 出参 ❌（`affected`）+ 5 类解析分歧 + 键序 + Content-Type |
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

**为什么不是 `UNRESOLVED`**：5 条 MISMATCH 全部有 `真源-双侧探针` 级证据（Go 真源解析 +
RAG 真实 DTO + 真实 coercion，两侧同时跑），足以支撑判定。
**为什么不是 `SEMANTICALLY EQUIVALENT`**：第 3 / 4 / 17 项不通过，且其中一条
（`broadcast=1` 被当成 `true`）在业务上会造成**误发全量通知**。

**证据强度总表**：

| 结论 | 强度 |
|---|---|
| `-005` ~ `-009` 五条 SEMANTIC MISMATCH | **真源-双侧探针**（Go `core/mapping` 真源 + RAG 真实 DTO/coercion，51 例） |
| `-010` Content-Type charset | **转换器级运行**（非真容器 HTTP 往返） |
| 13 条路由 / 方法 / 鉴权边界 | 静态源码 + 41 条既有 MVC 用例（RAG 单侧） |
| 保护层 413 / 503 裸响应 | **真容器-RAG单侧**（Go 侧期望值取自源码） |
| 一切依赖 message-rpc 响应语义的结论 | **UNKNOWN**（UNRESOLVED-001） |
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
3. **改完必须两侧同时跑** —— `tools/semantic-probe` 需**扩到 51 例以上并新增出参用例面**，
   否则 `-009` 这类「出参漂移」没有任何守门。