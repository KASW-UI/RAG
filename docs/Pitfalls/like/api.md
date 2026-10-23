# like-api 踩坑台账

> **范围**：`service/like/like-api`（Go 侧 `service/like/api`）
> **首写**：2026-10-23（组件级复核轮）
> **配套**：[`docs/DRIFT/like/api.md`](../../DRIFT/like/api.md) —— 差异本体在这里，本文件只记**教训**。

本组件是 `AGENTS.md` §3.3.1 与 §3.3.2 两条硬规则的**发源地**。
下面每条坑都按「**现象 → 根因（框架/语言机制）→ 为什么测试抓不到 → 下次怎么防**」写。

---

## PIT-LIKEAPI-001 — go-zero 自己的解析层是**不对称**的，只学一半必然漏

**现象**
`{"target_type":"article","target_ids":[1,2]}` 在 Go 侧**解析成功**（`TargetIds == ["1","2"]`，
真的发出 RPC），在 RAG 侧**解析失败**（`200 + 5001`，不发 RPC）。
同一个 JSON 数字，进**标量** String 字段 Go 会拒（`{"target_type":1}` → type mismatch），
进 **Slice 元素** Go 反而收。

**根因（语言机制，不是框架）**
`core/mapping/unmarshaler.go` 里有**两条独立的代码路径**，只有一条会拒数字：

| 位置 | 代码 | 遇到 `json.Number` |
|---|---|---|
| 标量：`processFieldPrimitive` **:648-661** | `switch v := mapValue.(type) { case json.Number: return processFieldPrimitiveWithJSONNumber(...) }` | 进 `processFieldPrimitiveWithJSONNumber` → `typeKind=String` 落 `default:` → **`newTypeMismatchError`** ❌ |
| Slice 元素：`fillSliceValue` **:227-231** | `switch v := value.(type) { case fmt.Stringer: ... case string: ... }` | ⚠️ **`json.Number` 命中 `fmt.Stringer`**（因为 `type Number string` 自带 `String()`）→ `setValueFromString(String, …)` → **接受** ✅ |

⇒ 「Go 拒 JSON 数字进 String」这条规则**只对标量成立**。
把它当全局规则实现（正是 `GoStyleCoercion` 的 `LogicalType.Textual ← Integer/Float = Fail`），
就会把 Slice 这一半**误伤成拒绝**。

**为什么测试抓不到**
- Go 侧：Go 仓没有一行针对这个组合的测试 —— 它在 go-zero 自己的测试套里也没被当成契约。
- RAG 侧：`LikeReqGoStyleValidationTest` 只测**必填性**，`LikeApiMvcPipelineTest#jsonStringIntoIntFieldIsRejected`
  只测**标量**方向。**没有任何一条测试往数组里放过数字** —— 用例矩阵里根本没有这一格。
- 更隐蔽的是：这条规则让 RAG「更严」，绿测试天然不会红。

**下次怎么防**
1. **枚举字段时按「容器元素」与「标量」两栏分别对照**，不要把它们当同一类。
2. 涉及第三方解析库的「全局强转开关」时，先问一句：**这个开关管不管集合的元素？**
3. 把矩阵做成**可执行的探针**（Go 侧 / RAG 侧各一份、同一批输入），
   而不是把结论写进 javadoc —— 见 PIT-LIKEAPI-006。

---

## PIT-LIKEAPI-002 — `null` 元素在数组里：Go 会**填空串**，Jackson 会**留 null**，而 protobuf 会**炸**

**现象**
`{"target_ids":["x",null,"y"]}`：
- Go ⇒ `["x","","y"]`，**长度仍是 3**，发 RPC；
- RAG ⇒ `[x, null, y]` ⇒ `addAllTargetIds` 抛 `NullPointerException: Element at index 1 is null.`
  ⇒ `handleAny` 兜成 `500 + {"code":5001}`，**下游没被调用**。

更阴的是 `{"target_ids":[null]}`：Go 里 slice **保持 nil**（`fillSlice` 的 `valid` 标志没被置位，
`value.Set(conv)` 不执行，**:181-183**），RAG 里是 `[null]`。

**根因（反射 + protobuf 双重）**
- `core/mapping/unmarshaler.go:156-160`：`if ithValue == nil { continue }` ——
  **跳过但不缩短**，跳过的那一位留在 `MakeSlice` 的零值里，就是 `""`。
- `valid` 只在遇到非 nil 元素时置位；全 nil ⇒ `value.Set(conv)` 不执行 ⇒ 字段保持 nil。
- protobuf 的 `addAllXxx(Iterable)` 对 null 元素**硬失败**（`checkNotNull`），
  而 `.build()` 之前不会暴露 —— **错误点离错误源很远**，这是排查成本高的主因。

**为什么测试抓不到**
`LikeApiMvcPipelineTest` 的所有 `target_ids` 用例都传**纯字符串数组**。
`[]`（空数组）被测了 —— 但 `[]` 和 `[null]` 在三条路径上的结果**完全不同**：
Go 的 `[]` 走 `:148-151` 提前返回，`[null]` 走 `:153-183` 的完整循环。
⇒ **只测「空数组」会给人「数组边界已覆盖」的错觉。**

**下次怎么防**
1. 数组/集合类字段的边界矩阵必须包含：**空数组 / 全 null / 部分 null / 超界元素**。
2. **凡是要把集合塞进 protobuf 的地方，先做一次 null 元素清洗**，并把「清洗规则」
   （null→`""`，且**不缩短**）写进注释 —— 因为「过滤掉 null」和「null→空串」是两种不同的下游语义。
3. NPE 被 `handleAny` 兜成 5001 时，**5001 会掩盖「解析层 vs 下游构造层」的定位**。
   这类 NPE 必须在发生点被命名（自定义异常），而不是交给兜底。

---

## PIT-LIKEAPI-003 — 「升级大版本」会**静默翻转默认值**：`FAIL_ON_TRAILING_TOKENS`

**现象**
`{"target_type":"a","target_ids":["1"]} garbage`：
Go **接受**（只解第一个 JSON 值），RAG **拒绝**。

**根因**
- Go：`core/jsonx/json.go:74-77` 用 `json.Decoder.Decode`，它**只解一个值**、不检查其后内容。
- Jackson 3（`tools.jackson` 3.1.5）：**默认开启** `FAIL_ON_TRAILING_TOKENS`。
  ⚠️ **Jackson 2 的这个开关默认是 `false`** —— 大版本升级把它翻了过来。
  报错文案会直接点名机制：`not allowed as per DeserializationFeature.FAIL_ON_TRAILING_TOKENS`，
  **这是唯一能一眼看出真相的线索**。

**为什么测试抓不到**
任何「合法 JSON」的用例都不会产生尾随 token。**它只在畸形输入下出现**，
而畸形输入通常只测「会不会报错」这种粗断言 —— 两侧都报错就被当成一致，
**没人去看「谁报得更早、报得更细」**。

**下次怎么防**
1. 换 Jackson / protobuf / grpc 大版本时，**逐条比对默认特性（`DeserializationFeature` 的默认值）**，
   不要只看 API 是否还在。
2. 遇到「Go 收、RAG 拒」的畸形输入，先确认这是**有意的收紧**还是**默认值漂移**。
   两者在客户端看来一样，在维护者看来完全不同 —— 本轮就属于后者，且**从未被登记过**。
3. **沉默最坏**：既没关掉它，也没有台账记录 ⇒ 下次复核会把它当成「上轮漏了」重新发现一遍。

---

## PIT-LIKEAPI-004 — 「字面串比较」vs「解析后比较」：判据的**输入**不同，结论就相反

**现象**
`Content-Type: APPLICATION/JSON`（合法的 HTTP 请求，媒体类型大小写不敏感）：
- Go ⇒ body 被整个丢弃 ⇒ `200 + 5001`，**不发 RPC**；
- RAG ⇒ 正常解析 ⇒ `200 + 成功`，**发 RPC**。

**根因**
两边都写着「`contains("application/json")`」，但**取的是不同的东西**：

| | 取值方式 | 大小写 |
|---|---|---|
| Go `rest/httpx/requests.go:154` | `r.Header.Get("Content-Type")` —— **原始头字符串** | 敏感 |
| RAG `GoStyleJsonBodyConverter.java:115-118` | `getContentType().toString()` —— **解析后的 `MediaType`** | 已被规范化为小写 |

⇒ 「规范化」发生在判据**之前**，判据拿到的东西已经不是同一个字符串了。

**为什么测试抓不到（本轮最值得记的一条）**
`GoStyleJsonBodyConverter.goReadsBody(ct, len)` 是 `public static`，
`LikeApiGoStyleBodyContentTypeTest#goReadsBodyBoundaries` **直接单测它**，
7 条断言**全绿**。但直接传 `"APPLICATION/JSON"` 进去**确实返回 `false`** ——
**测试是对的，端点行为是错的**：真实管线里它拿到的是规范化后的 `"application/json"`。

⇒ **判据函数单测 ≠ 端点行为**。这与 §3.3.3 的四类静默失效同族，
只是这次的静默点在**「输入从哪来」**而不是「机制有没有装配」。

**下次怎么防**
1. 只要判据吃的是**框架提供的对象**（`MediaType` / `HttpHeaders` / `PathVariable`），
   就要问一句：**框架有没有在把它交给我之前改写它？**
2. 判据函数的单测**必须配一条走完整管线的用例**，哪怕只有一条。
3. 判据函数的参数要**尽量取原始值**。取不到时，在注释里写清「这是规范化后的值」。

---

## PIT-LIKEAPI-005 — 「应用入口类的 javadoc」是**最高杠杆的错误传播点**

**现象**
`LikeApiApplication.java:25-29` 至今写着：

> 「⚠️ **本服务没有配置超时**：Go 侧 `etc/likecenter.yaml` 的 RestConf **没有 `Timeout` 字段**
> ⇒ `TimeoutFromMillis(0) == 0` ⇒ … Java 侧因此**刻意不配** `observability.http-timeout`」

而 `application.yml:27-29` **已经配了 `observability.http-timeout: 3s`**。
两处直接矛盾，且这行 Go 断言本身就是 **DRIFT-LIKEAPI-001 的成因**
（Go 的 3000ms 来自 `rest/config.go:55` 的 struct tag，不是 yaml）。

**根因（不是框架，是「注释没有生命周期」）**
写注释时它是对的（当时确实没配）；修漂移时**改了 yaml，没回头改注释**。
`application.yml` 自己在文件头写了一大段「本条已更正」，但**入口类的 javadoc 没同步**。

⇒ 而入口类恰恰是「排查超时 / 整理配置」时**第一眼会读到**的地方。

**为什么测试抓不到**
javadoc 不参与编译，不参与测试。**没有任何工具会告诉你「注释和配置不一致」。**

**下次怎么防**
1. **`AGENTS.md` §3.3.2 的硬要求在这里再加一条落地动作**：
   任何以「Go: …」措辞出现的断言，修正时**必须同一次提交里搜一遍同域全部文件**的同类措辞。
2. 修漂移的 checklist 里加一条：「**这次改动涉及的结论，在别处有没有被复述过？**」
   本项目的注释密度极高（几乎每个类都逐字引用 Go 源），这既是优点也是**放大器**：
   一条错误结论会被复述 **N 遍**，其中至少一遍在不该出现的地方。
3. 入口类（`XxxApplication`）的 javadoc 应当被视为**契约文档**而不是自由文本。

---

## PIT-LIKEAPI-006 — 「引用了真实文件、但把行为读反了」：**同一个文件里会自相矛盾**

**现象**
`LikeReqGoStyleValidationTest.java`：
- 第 **31** 行（类注释）：「保住 Go 的语义（标量必填 / **Slice 永不计必填** / options 取值域）」
- 第 **40** 行（Go 代码摘录旁）：`if !opts.optional() { return u.processFieldNotFromString(... emptyMap ...) }  ← 不报错`
- 第 **59-73** 行（用例内注释）：「**target_ids 也是必填 —— Slice 不豁免**」+ 完整调用链推导

⇒ 同一文件里，**类级注释说 A、用例内注释说 ¬A**。

**根因**
- 「Slice 永不计必填」是 2026-10-22 被活体探针**推翻**的旧结论；
  当天只改了 `@DisplayName` 与用例内注释（因为要修绿测试），**类级注释漏改**。
- 更隐蔽的是第 40 行的 `← 不报错` 标注：**Go 代码摘录本身是对的**（那确实是
  `processFieldNotFromString(emptyMap)`），**错的是旁边的箭头注释**。
  ⇒ 「代码引用准确」与「结论正确」是两件事，**前者不能为后者背书**。

**为什么测试抓不到**
Javadoc 里的话**没有任何东西会验证它**。而且这类错误**看起来极其可信**：
它引用了真实的文件、真实的行号、真实的函数名。

**下次怎么防（这是 `AGENTS.md` §3.3.2 的由来）**
任何以「Go: …」形式写进 `@DisplayName` / javadoc 的断言，必须同时满足三条：
1. **读到消费该 tag 的那段控制流**（不是只贴 struct 定义行）；
2. **有运行证据**（不是只读源码）；
3. **行号指向消费点**而不是声明点。

⚠️ 附带一条本轮新增的实践：**「推翻旧结论」时，搜同域全部文件里的旧措辞**。
本轮就靠这一步又挖出 3 处残留（DRIFT-018/019/020）。

---

## PIT-LIKEAPI-007 — 反直觉的默认值：**浮点溢出在两侧的处理相反**

**现象**
`{"weight": 1e400}`：
- Go ⇒ `strconv.ParseFloat: value out of range` ⇒ `200 + 5001`，**不发 RPC**；
- RAG ⇒ Jackson 3 **接受**，字段值 `Double.POSITIVE_INFINITY`
  ⇒ `(float)` 窄化后仍是 `+Inf` ⇒ **发出一个 weight=+Inf 的写请求**。

**根因**
go-zero 走 `strconv.ParseFloat` 并把 `ErrRange` 往上抛；
Jackson 对溢出字面量**不报错**，直接按 IEEE 754 给无穷。

⚠️ **但 `1e39` 两侧一致**（f64 合法、f32 溢出 ⇒ 都被窄化成 `+Inf`）。
⇒ 差异**只在字面量本身越出 float64 值域**时出现 ——
只测「窄化」不测「解析」，就会漏掉这一格。

**为什么测试抓不到**
`LikeApiMvcPipelineTest#weightIsNarrowedToProtoFloat` 测的是窄化路径；
没有一条用例喂一个**越界的浮点字面量**。

**下次怎么防**
1. 浮点字段的用例矩阵加一格「**超出宿主类型值域的字面量**」（`1e400` / `-1e400`）。
2. 「解析层报错」与「序列化层窄化」是**两件事**，要分别钉。
3. `Double` / `Float` 落到业务字段前，确认**有没有一道 `isFinite()`**。
   注意：`Infinity` 不会自己消失，它会一路进结算。

---

## PIT-LIKEAPI-008 — 同一语义旋钮的**取值来源不同**：固定值 vs 父预算派生

**现象**
「单次下游调用超时」：Go 是**固定 2000ms**，RAG 是**HTTP 超时的剩余预算（≤3000ms）**。
下游耗时落在 **2s~3s** 区间时，Go 返 `DEADLINE_EXCEEDED`，RAG 成功返回。

**根因（不是 bug，是「旋钮被合并了」）**
- Go 有**两个独立旋钮**：`rest.RestConf.Timeout`（3000，整请求）
  与 `zrpc.RpcClientConf.Timeout`（**2000，单次调用**）。
  ⚠️ **两个都不在 yaml 里**，都来自 struct tag。
- RAG 只有**一个**：`HttpMiddleware` 把 HTTP 超时的剩余预算用 ThreadLocal 传下去
  （`GoStyleRequestDeadline`），**没有第二个旋钮**。
- ⇒ RAG 的 yaml 里**根本不存在**能对应 Go 那个 2000 的字段。

**为什么测试抓不到**
任何「超时生效了吗」的用例都会通过（两侧都超时）。
要抓到差异，必须让下游**耗时落在 2~3s 这个窄窗口里** ——
这需要真的慢下游，不是 mock 得出来的。

**下次怎么防**
1. 对照 `RestConf` / `RpcClientConf` 这类**内嵌配置结构体**时，
   **逐字段列出超时/重试/限流类旋钮**，不要把它们当「一个 timeout」。
2. 「用 ThreadLocal 传播预算」是**有意识的取舍**（见 `GoStyleRequestDeadline` 的类注释），
   但取舍**不自动覆盖语义**：派生预算 ≠ 固定预算。
   ⇒ 派生实现必须显式取 `min(固定值, 剩余预算)`，否则就是少了一个旋钮。
3. 这类差异**很难靠单测发现**，必须登记成 UNRESOLVED 并写清「需要什么样的活体探针」。

---

## PIT-LIKEAPI-009 — 「不存在某机制」与「两侧都实现了它」在台账上长得一样

**现象**
本次任务的检查清单里有一条：「核实 **public 路径集合**逐条一致、**blacklist key 前缀**与
**fail-open** 行为一致」。

实测结论：**like 域根本没有黑名单中间件。**
`grep -rni blacklist service/like/ service/common/` ⇒ **零命中**。

**根因（记录纪律，不是代码）**
如果台账只写「public 路径集合一致 = ✅」，半年后没人分得清：
- (a) 两侧都实现了黑名单且行为一致？
- (b) **两侧都没有**？

这两种情况的**后续动作完全相反**：前者要防回归，后者一旦有人加一个反而是漂移。

**下次怎么防**
台账里凡涉及「某条中间件 / 某项保护」，**必须显式标注「不适用」并附上 grep 证据**，
而不是打一个 ✅。`AGENTS.md` §0 已经因为同类问题打过补丁
（「勿凭本表下结论」），本条是它的具体化。

---

## PIT-LIKEAPI-010 — 「直接单测判据函数」会给你**一条永远绿的回归网**，而它守错了东西

**现象（与 PIT-LIKEAPI-004 同源，这里记「测试组织方式」这一面）**
`LikeApiGoStyleBodyContentTypeTest` 有 7 条 `goReadsBody(...)` 边界断言，全绿。
但端点的真实行为（`Content-Type: APPLICATION/JSON`）与这些断言**矛盾**。

**根因**
判据函数是 `public static`，所以它**看起来是**可测的纯逻辑；
于是测试选择「测它」而不是「测用它的管线」。
**判据函数的入参类型（`String ct`）掩盖了「真实调用方传的是什么」这个问题** ——
测试直接给 `String`，而管线里给的是 `getContentType().toString()`。

**下次怎么防**
1. 「判据函数单测」**只能作为补充**，不能作为该行为的**主要**回归网。
   本项目已有正确范式：`LikeApiRouterErrorRealTomcatTest` 明确分工 ——
   **字节级只守一次（真 Tomcat），每模块的装配守卫各守一次**。
2. 判据函数的单测里，**至少一条要覆盖「值是怎么来的」**（例如显式断言
   `MediaType.parse("APPLICATION/JSON").toString()` 等于 `"application/json"`）。
3. 见 PIT-LIKEAPI-006：这类测试的 `@DisplayName` 里如果出现「Go: …」，
   它的措辞精度就是风险 —— 本轮的 `application/*+json 在 Go 侧同样不算 JSON`
   就属于**过宽**（`application/json-patch+json` 在 Go 侧**是**算的）。

---

## 附：本轮两条最贵的教训（给下一个接手的人）

1. **「绿测试」和「绿色断言」是两回事。**
   本轮 6 条 SEMANTIC MISMATCH，**没有一条**会被现有 62 条测试抓到 ——
   因为**用例矩阵里根本没有那些格子**。
   测试覆盖率的真实单位不是「条数」，而是「**输入空间被切了几刀**」。
   ⇒ 下一个动作应该是**扩矩阵**，不是加测试条数。

2. **探针要能双侧跑同一批输入。**
   本轮 `/tmp/likeprobe` 的 Go 侧小程序（直接调 `httpx.Parse`）
   与 RAG 侧 `RagProbe`（生产 mapper + 生产 DTO + 生产 converter + 生产 advice）
   **共享同一份用例清单**。这才让「Go 收、RAG 拒」变成一条**可复现的事实**，
   而不是两段各自正确的注释。
   ⚠️ 关键是**两侧都必须用生产装配**：
   我第一版 RAG 探针漏了 `GoStyleJsonBodyConverter`，
   于是得出「RAG 返回 415」的错误结论 —— 而真实装配下 RAG 返回 `200 + 5001`。
   **探针漏一个生产 Bean，结论就整个反过来。**