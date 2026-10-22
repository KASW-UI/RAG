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
> 它要么是**巧合**、要么是**在给未验证的假设发通行证**。后一种比没有测试更危险 ——
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
| ~~Content-Type 非 JSON~~ | ~~继续处理出 envelope~~ | ~~415 空 body~~ | ✅ **2026-10-22 已关闭**，与 like 域合并为一份共享实现。⚠️ 原签字理由「两侧都是拒绝」**已被推翻**：Go 的 `withJsonBody` 不满足时是**丢弃 body 后按空对象继续**，对零必填 DTO（`PageReq`/`EmptyReq`）Go 其实**成功**。详见「请求体 Content-Type 判据」章节 |
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
4. **给 3s 超时补一条真触发测试**（stub `sleep(5000)` → 断言 503 + text/plain + body）——
   Bean 存在已被证明，但 503 分支**至今没有任何运行证据**（§3.3 同款形态）；
5. ~~给 gRPC client 熔断补一条端到端触发测试~~ ✅ 已完成
   （`GoStyleGrpcClientBreakerE2ETest`：真 in-process gRPC server 恒返 INTERNAL，
   断言响应码 **5001 → 1015**）。补它的过程又挖出兜底异常处理器吃码的缺陷，已修。
6. **补 `message-api/HUMAN_ONLY.MD`**（§2 已把该模块登记为「已实现 + 已对照，但文档缺失」；§7.2 的旧清单曾把它误列为已存在，已校正）。

