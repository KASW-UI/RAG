# like-api 踩坑台账

> **范围**：`service/like/like-api`（Go 侧 `service/like/api`）
> **首写**：2026-10-23（组件级复核轮）
> **配套**：[`docs/DRIFT/like/api.md`](../../DRIFT/like/api.md) —— 差异本体在这里，本文件只记**教训**。

本组件是 `AGENTS.md` §3.3.1 与 §3.3.2 两条硬规则的**发源地**。
下面每条坑都按「**现象 → 根因（框架/语言机制）→ 为什么测试抓不到 → 下次怎么防**」写。

---

## PIT-LIKEAPI-001 — go-zero 自己的解析层是**不对称**的，只学一半必然漏 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-001` ⇒ **新号**：`PIT-LIKE-01`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— go-zero 解析层「标量拒 / Slice 收」的**不对称**是语言层事实（真源冻结）⇒ 今天仍成立；本轮凡写 like-api 的解析类台账断言，仍须把「容器元素」与「标量」分两栏对照。
> **依据**：Go `core/mapping/unmarshaler.go:648-661`（标量路径 `processFieldPrimitiveWithJSONNumber`）· `:227-231`（Slice 路径 `fillSliceValue`）⇒ **证据强度：Go 侧文件行号**。

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

## PIT-LIKEAPI-002 — `null` 元素在数组里：Go 会**填空串**，Jackson 会**留 null**，而 protobuf 会**炸** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-002` ⇒ **新号**：`PIT-LIKE-02`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 数组里的 `null` 元素已按 Go 语义处理（显式返回 Java `null`，交给下游 normalize）⇒ 本域触发条件消除；本轮只改 `docs/**`、不动反序列化器。
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/dto/GoStyleRawStringListDeserializer.java:63`（显式返回 Java `null`，不再 NPE）⇒ **证据强度：源码行号**。

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

## PIT-LIKEAPI-003 — 「升级大版本」会**静默翻转默认值**：`FAIL_ON_TRAILING_TOKENS` ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-003` ⇒ **新号**：`PIT-LIKE-03`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— `FAIL_ON_TRAILING_TOKENS` 已在 like-api 显式关掉（Jackson 3 的默认被翻转）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/config/LikeGoStyleCoercion.java:74,85`（`builder.disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`） ⇒ **证据强度：源码行号**。

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

## PIT-LIKEAPI-004 — 「字面串比较」vs「解析后比较」：判据的**输入**不同，结论就相反 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-004` ⇒ **新号**：`PIT-LIKE-04`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 判据的输入已改成**原始头值**（不再用被规范化过的 `MediaType.toString()`）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：共享层 `service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.java:331`（`inputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)`）+ `:79,241-244`（`goReadsBody` 判据）· 守门 `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiGoStyleBodyContentTypeTest.java:183` ⇒ **证据强度：源码行号 + 守门在仓**。

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

## PIT-LIKEAPI-005 — 「应用入口类的 javadoc」是**最高杠杆的错误传播点** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-005` ⇒ **新号**：`PIT-LIKE-05`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 入口类 javadoc 里那句「Go 侧没配 Timeout ⇒ 刻意不配」的**方向反的**断言已订正 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`docs/DRIFT/like/api.md` 的 `DRIFT-LIKEAPI-018` / `DRIFT-LIKEAPI-019`（均 ✅ 已修 · 纯注释订正） ⇒ **证据强度：台账状态**。

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

## PIT-LIKEAPI-006 — 「引用了真实文件、但把行为读反了」：**同一个文件里会自相矛盾** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-006` ⇒ **新号**：`PIT-LIKE-06`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「引用了真实文件、但把行为读反了」今天仍会犯 —— 本仓注释密度极高，一条结论会被复述 N 遍 ⇒ 仍生效；本轮凡改 like 域任何 javadoc，仍须按 AGENTS.md §3.3.2 三条自查。
> **依据**：AGENTS.md §3.3.2 · 本域复发样本 `service/like/like-api/src/test/java/com/rag/like/like/api/LikeReqGoStyleValidationTest.java` 的类注释与用例内注释矛盾 ⇒ **证据强度：仓库政策 + 源码行号**。

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

## PIT-LIKEAPI-007 — 反直觉的默认值：**浮点溢出在两侧的处理相反** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-007` ⇒ **新号**：`PIT-LIKE-07`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 浮点字面量越出 float64 值域时现在**显式拒**（`isFinite` 闸门）⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/dto/GoStyleFloat64Deserializer.java:74`（`if (!Double.isFinite(parsed))`）⇒ **证据强度：源码行号**。

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

## PIT-LIKEAPI-008 — 同一语义旋钮的**取值来源不同**：固定值 vs 父预算派生 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`PIT-LIKEAPI-008` ⇒ **新号**：`PIT-LIKE-08`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 「单次调用上界」已实现为 `min(固定 2000ms, 剩余预算)`，两个旋钮都在 ⇒ 触发条件消除；本轮只改 `docs/**`。
> **依据**：`service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:157`（`Math.min(capMillis, remainingMillis)`）+ `:151`（`capMillis` 取自 `properties.getLikeRpc().getTimeout()`）· 守门 `service/like/like-api/src/test/java/com/rag/like/like/api/LikeRpcTimeoutCapTest.java:231-236` ⇒ **证据强度：源码行号 + 守门在仓**。

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

## PIT-LIKEAPI-009 — 「不存在某机制」与「两侧都实现了它」在台账上长得一样 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-009` ⇒ **新号**：`PIT-LIKE-09`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「台账里凡涉及某条中间件/某项保护，必须显式标注『不适用』并附 grep 证据，而不是打一个 ✅」这条**记录纪律**今天仍成立 ⇒ 仍生效；本轮改 like 域台账状态时同样适用。
> **依据**：AGENTS.md §0（「勿凭本表下结论」补丁）· 本域实例：like 域**无**黑名单中间件（`grep -rni blacklist service/like/ service/common/` 零命中）⇒ **证据强度：仓库政策 + grep 实测**。

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

## PIT-LIKEAPI-010 — 「直接单测判据函数」会给你**一条永远绿的回归网**，而它守错了东西 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-010` ⇒ **新号**：`PIT-LIKE-10`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「判据函数的单测只能作补充、不能作该行为的主要回归网」这条**测试组织纪律**今天仍成立 ⇒ 仍生效；本轮若给 like 域任何判据补测试，仍须配一条走完整管线的用例。
> **依据**：本域正确范式 `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java`（字节级只守一次 · 真 Tomcat）· 补格建议见本条正文第 2 点 ⇒ **证据强度：仓内文件路径**。

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

## PIT-LIKEAPI-011 — 恒真断言：不是「写错了」，是**主动制造一条假绿** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-011` ⇒ **新号**：`PIT-LIKE-11`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「恒真断言 = 用一条无意义的断言伪装成『已经验过了』」今天仍会犯 ⇒ 仍生效；本轮凡在台账里引用某条守门，必须能指出它**断言的是什么**，而不是「有个测试类」。
> **依据**：AGENTS.md §3.3.3（「绿测试可能是错误答案的守卫」）· 本域样本 `LikeApiMvcPipelineTest` 的 `httpTimeoutIsActuallyConfigured`（三元两分支同字面量） ⇒ **证据强度：仓库政策 + 源码行号**。

**现象（修复轮 2026-10-23）**
给 `DRIFT-LIKEAPI-018` 补守门测试时，我顺手写了这么一条：

```java
@Test
void httpTimeoutIsActuallyConfigured() {
    assertEquals("3s", ...isEmpty() ? "3s" : "3s", "占位：真实断言在别的类里");
}
```

**它绿了。永远会绿。** —— 三元表达式的两个分支是同一个字面量。

**为什么这条特别危险**
它比「没写测试」更糟。PIT-LIKEAPI-010 讲的是「测试守错了东西」，
而这条是**测试什么都没守，却占着一个 `@Test` 的位置**，
读代码的人会看到「🔴 已有守门测试」⇒ **跳过这件事**。

⇒ 这是 AGENTS.md §3.3.3「绿测试可能是错误答案的守卫」的**更隐蔽的变种**：
不是绿测试锁死了错答案，而是**用一条无意义的断言伪装成"已经验过了"**。

**本域的具体背景**
`DRIFT-LIKEAPI-018` 是**纯文档缺陷**（`LikeApiApplication` 的类 javadoc 曾宣称
「刻意不配 `observability.http-timeout`」，而 `application.yml:27-29` 早就配了 3s）。
台账 §9 **U-03 明确记着「3s 的 503 出口未在运行期复现」**（需真 Tomcat + 慢下游）。
⇒ 在这个状态下写任何"配置已生效"的断言都是自欺。

**下次怎么防**
1. **纯文档/注释类修复不需要新测试** —— 加测试才是异常信号。
2. 写完断言先问一句：**「把这个断言改成恒真，它还会绿吗？」** 会 ⇒ 别提交。
3. 真要钉配置，至少要能从**运行期**读到（如注入 `@ConfigurationProperties` 后的实际值），
   而不是在测试里复述一个字面量。
4. 拿不准就**不写**，并在测试类的注释里**写明为什么这条没有测试**
   （本轮 `LikeApiGoAlignmentTest` 末尾就是这么做的，把「不写」的理由显式化，
   避免下一个人以为「已经有人验过了」）。

---

## PIT-LIKEAPI-012 — `Arrays.asList(null)`：单参数时那个 `null` 是**数组本身** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-012` ⇒ **新号**：`PIT-LIKE-12`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— `Arrays.asList(T...)` 的单参数 `null` 语义是 **Java 语言事实**，不会消失 ⇒ 仍生效；本轮若给 like 域写「全 null 数组」类用例，仍须用 `Collections.singletonList(null)` 或显式数组。
> **依据**：Java API 语义（`java.util.Arrays#asList` varargs）· 本域已按此改写用例 ⇒ **证据强度：语言/库语义**。

**现象（修复轮 2026-10-23）**
测「target_ids 全为 null」时写：

```java
Arrays.asList(null)          // ❌ NullPointerException
```

**根因**
`Arrays.asList(T... a)` 是 **varargs**。传**一个** `null` 时，编译器无法区分
「一个 null 元素」与「整个数组是 null」⇒ 直接把 `null` 当成 `T[]` 传入
⇒ `Arrays$ArrayList.<init>` 里 `requireNonNull` 抛 NPE。

⚠️ **传两个以上参数不会踩**：`Arrays.asList(null, null)` 会正常展开成 `[null, null]`。
⇒ 这个坑只在**恰好一个 null 元素**时出现，而那恰好是最容易构造的测试输入。

**下次怎么防**
需要「允许 null 元素的单元素列表」时：
- `Collections.singletonList(null)` ✅（本轮改用这个）
- 或 `Arrays.asList(new String[]{null})` ✅（显式数组）

⚠️ 顺带：`List.of(null)` / `List.of(...)` 任何含 null 的写法**都会**抛 NPE ——
`List.of` 是**不允许 null 元素**的，而 `Arrays.asList` / `Collections.singletonList` 允许。
⚠️ 而本轮修的 `DRIFT-LIKEAPI-013` 恰恰是「数组里允许 null」这件事 ——
**测试数据构造方式与被测语义在同一处打架时，先确认构造 API 的契约**。

## PIT-LIKEAPI-013 — 「测错对象」比「测不出来」更贵：先确认 classpath 上到底是哪一代 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-013` ⇒ **新号**：`PIT-LIKE-13`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「同一仓里 Jackson 2 与 Jackson 3 同时存在、注解包名又不区分版本」今天**仍然成立** —— like-rpc 至今仍注入 Jackson 2 的 `ObjectMapper` ⇒ 仍生效。
> **依据**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/Jackson2ObjectMapperConfiguration.java:4` · `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeUpdateKafkaListener.java:3` ⇒ **证据强度：源码行号**。

**现象（修复轮 2026-10-23，修 012 时）**
我写了 4 个探测类，用 `com.fasterxml.jackson.databind.ObjectMapper` 测「JSON 数字反序列化成
`Object` 会得到什么类型」，拿到的结论一路推到底。

**根因：like-api 的 MVC 用的是 Jackson 3，不是 Jackson 2**
| 证据 | 说明 |
|---|---|
| `WebMvcConfig` 用 `JsonMapperBuilderCustomizer` + `tools.jackson.databind.JsonMapper.Builder` | 核心是 `tools.jackson.*` |
| DTO 用 `com.fasterxml.jackson.annotation.JsonProperty` | ⚠️ **注解包名仍是 Jackson 2 的** —— 这正是最容易看走眼的地方 |
| `Jackson2ObjectMapperConfiguration`（在 like-**rpc**） | 另一个组件显式造 Jackson 2 `ObjectMapper`，进一步混淆 |

⇒ 同一仓里 **Jackson 2 与 Jackson 3 同时存在**，而注解包名**不区分版本**
（Jackson 3 刻意保留 `com.fasterxml.jackson.annotation` 以便迁移）。

⚠️ 若是只测 Jackson 2，012 的方案（尤其是「要不要开 `USE_BIG_DECIMAL_FOR_FLOATS`」）
就会基于错误数据定型 —— 而 Jackson 3 的 `ObjectMapper` 是**不可变**的，
`new ObjectMapper().enable(...)` 直接编译失败，这个差异本来会暴露，但我差点靠旧数据先下了结论。

**下次怎么防**
1. **探测必须用「生产路径上的那个对象」**，而不是「同名的那个类」。
   MVC 场景就用 `@SpringBootTest` + `@AutoConfigureMockMvc` 走真实管线（这也是 PIT-LIKEAPI-010 的结论）。
2. 看到 `com.fasterxml.jackson.annotation.*` **不要**据此断定是 Jackson 2。
3. 探测类写完先看**它是否真能编译**——`enable()` 在 Jackson 2 上有、在 Jackson 3 上没有；
   如果探测代码顺利编译通过，反而要警惕「测的根本不是生产用的那个」。

---

## PIT-LIKEAPI-014 — 「API 存在性」要查 jar，不要凭记忆 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-014` ⇒ **新号**：`PIT-LIKE-14`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「否定式断言必须用检索证明、不能用记忆证明」今天仍成立 ⇒ 仍生效；本轮凡写「本仓没有 X」，仍须给出 jar / grep 证据。
> **依据**：本域样本：Jackson 3 无 `tools.jackson.databind.DeserializationException`、`Token` 已迁到 `tools.jackson.core.JsonToken`（用 `unzip -l` / `javap` 确认）⇒ **证据强度：仓内检索**。

**现象（同一个修复轮里连续发生两次）**
① 写了 `tools.jackson.databind.DeserializationException` —— **Jackson 3 里没有这个类**；
② 修好后又按记忆写了 `p.currentToken() == JsonParser.Token.START_ARRAY` —— Jackson 2 的
`Token` 是顶层类 `JsonToken`，Jackson 3 迁到 `tools.jackson.core.JsonToken`。

**为什么容易错**
Jackson 2 → 3 的迁移里，**包名、注解包名、异常体系都变了**，而网上的绝大多数示例仍是 2.x。
记忆里的「Jackson 写法」几乎全部指向 2.x。

**正确做法（两次都是靠它救回来的）**
```bash
unzip -l <jackson-databind-3.1.5.jar> | grep -E "exc/"   # 先看有哪些类
javap -cp <jar> <类名>                                        # 再看构造器是否 private / protected
```
⚠️ 第二个尤其重要：`MismatchedInputException` 的构造器是 **`protected`**，
必须用静态工厂 `from(JsonParser, Class, String)` —— 编译期报错比运行期踩坑便宜得多。

⇒ 这与 PIT-LIKEAPI-009（「不存在某机制」与「两侧都实现了它」在台账上长得一样）同族：
**否定式断言必须用检索证明，不能用记忆证明。**

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
---

# 2026-10-24 第五轮新增踩坑（DRIFT-LIKEAPI-017 修复过程）

## PIT-LIKEAPI-015 —— 🔴 **`Deadline` 是不可变对象：`timeRemaining(...)` 是「调用那一刻」才算的** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-015` ⇒ **新号**：`PIT-LIKE-15`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— `io.grpc.Deadline` 不可变、`timeRemaining(...)` 是「调用那一刻」才求值 —— **grpc-java 语义不变** ⇒ 仍生效；本轮若给 like 域写任何「服务端观测到的 deadline」断言，仍须在观测点当场取值。
> **依据**：本域守门 `service/like/like-api/src/test/java/com/rag/like/like/api/LikeRpcTimeoutCapTest.java:231`（在观测点当场取 `remainingMillis`）⇒ **证据强度：守门在仓**。

### 现象

守门测试里断言「服务端观测到的 deadline 是 2000ms」，实际读到 **-1ms** ——
一度以为「deadline 根本没传到服务端」，差点把结论写成「grpc 不传播 deadline」。

### 根因

`io.grpc.Deadline` 是**不可变**的：它只存一个**绝对到期时刻**，
`timeRemaining(unit)` 是**调用那一刻**拿当前时刻去减 ⇒ **读得越晚，值越小**。

我犯的错是：**把 `Deadline` 对象存进 `AtomicReference`，事后才读 `timeRemaining`** ——
等到断言时，2 秒早已过去 ⇒ 读到一个已过期的负数。

### 修法

```java
// ❌ 错：存对象，事后读
serverObservedDeadline.set(Context.current().getDeadline());
// …过一会儿…
observed.timeRemaining(TimeUnit.MILLISECONDS);   // ← 此时早已过期

// ✅ 对：在**观测点当场**把值取出来
long remaining = observed == null ? -1L : observed.timeRemaining(TimeUnit.MILLISECONDS);
serverObservedRemainingMillis.set(remaining);
```

### 元教训

⚠️ **「观测到的值」和「观测到的对象」不是一回事** ——
前者是**某一刻的快照**，后者要**在读的时候**才求值。
凡是「先采集、后断言」的写法，采集的就必须是**值**，不是**活对象**。

⇒ 与 PIT-LIKEAPI-009 / PIT-LIKEAPI-014（「API 的存在性/语义细节不能靠印象」）同族：
**测法本身的误判，会伪装成「实现有 bug」的结论。**
⚠️ 中间我还用了一个临时探针类量到 `grpc-timeout: 1970387u` 与 handler 侧 `remaining ≈ 1961ms`，
确认传播链路本身没问题 ⇒ **探针类已删除，不进仓**（它只回答了一个一次性问题）。

---

## PIT-LIKEAPI-016 —— 🔴 **台账里一句「错误的范围判断」，会让本来能修的项被无限期搁置** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`PIT-LIKEAPI-016` ⇒ **新号**：`PIT-LIKE-16`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「『这条要动共享模块』这句话本身就是一个需要被证伪的断言」—— 这条**台账层纪律**与本轮完全同型：本轮要给 message/like 两域的每条状态断言定「已修 / 未修」，而「它要动共享层所以改不了」正是本轮必须证伪的那类话 ⇒ **是**。
> **依据**：AGENTS.md §3.3.3（「先改/删错误断言，再改实现」）· 本域实例 `DRIFT-LIKEAPI-017` 已按此修法闭环 ⇒ **证据强度：仓库政策 + 台账状态**。

### 现象

`DRIFT-LIKEAPI-017`（单次 RPC 超时）在台账里被写成：

> 「需新增独立配置项 + 改 `GoStyleRequestDeadline` 的「无预算就原样返回」分支 ⇒
> **需要连带审 `common` 的 deadline 组件，同样受 §4 的并发约束**」

⇒ 读起来像「这条要动共享模块 ⇒ 多域并发下会冲突 ⇒ 得排期等某个域先动」。

### 根因

那句话**是错的**，而且错在**根上**：修法其实**只读** `GoStyleRequestDeadline` 已有的**公开** API
（`isPresent()` / `remainingMillis()`）就能拿到剩余预算，**根本不需要改 common**；
而「无预算就原样返回」那条分支**根本不参与**（修法是不再调用 `applyTo`）。

### 危害（这才是重点）

一个**纯域内**、**零跨域冲突**的漂移，因为一句错误的范围判断，
在台账里躺了好几轮，被当成「要排队等资源」而不是「现在就能修」。

### 元教训

⚠️ **「这条要动共享模块」这句话本身就是一个需要被证伪的断言** ——
它和「这条有 bug」一样，都是**待验证的结论**，不是前提。

⇒ 遇到「要动共享模块 ⇒ 有并发约束」的说法，先问一句：
**「共享模块的哪一部分真的必须改？能不能只读它的公开 API？」**
很多「必须改共享组件」的结论，实际只是**没想到有只读这条路**。

⇒ 这也是 §3.3.3「先改/删错误断言，再改实现」在**台账层面**的同一条纪律：
台账里的错误断言，同样会让下一轮**重复已修项**或**跳过可修项**。

---

## 🆕 2026-11-01 收官轮 · 「守门行号指针」会因**上游删 import** 而集体前移（自踩自证）

**现象** —— 本文件 `:271` / `:520` 的依据指针写着 `LikeRpcTimeoutCapTest.java:233` / `:233-238` ⇒ ⇒ 本轮实测该文件在 G2 lane「测试面 `import com.rag.common.*` → 全限定名」那笔落码后，**这两行已经前移到 `:231` / `:231-236`** ⇒ ⇒ **指针指向了错误的代码行**。

**根因（两层）** ——
① **直接根因** = **删 import ⇒ 其后所有行号前移** ⇒ ⇒ **凡是「按行号引用测试文件」的指针，都会在任何一次 import 清理后集体变旧**；
② **为什么没人发现** = `check-pointer-consistency.py` 的指针正则只认**指向 `docs/{DRIFT,Pitfalls,Intentional_Deviations}/**.md`** 的指针 ⇒ ⇒ **指向 `service/**` 源码的行号指针**（本文件两处、Worktree.md 三处、grading/W29·W30 各一处…）**完全不在任何机器守门的覆盖面内** ⇒ ⇒ 只能靠人工逐条订正。

**判定式（可复用）** ——
① **按行号整体平移是错的** —— 同名测试文件在仓内常有多份拷贝（`GoStyleGrpcClientBreakerE2ETest.java` 有 **3 份**：message-api / user-api / hot-api）⇒ ⇒ 位移量**逐拷贝不同**（实测 −2 / −1 / 0）⇒ ⇒ 必须**先按内容锚点定位拷贝身份**，再在**该拷贝内**做旧→新映射；
② **映射要逐行做内容级**（取旧版本该行内容 → 在当前文件里唯一定位），**不要按「删了几条 import」推算平移量**；
③ **区间指针要两端都验**（本轮 `:291-310` → `:290-309` 的首尾行内容逐字比对通过才落笔）；
④ ⇒ ⇒ **「回执说某指针要改」不等于「该指针真的指向那个文件」** —— 本轮 G2 报的 **3 处**经复核为**误报**（指向的是**未被改动**的 user-api 拷贝）⇒ ⇒ **先核拷贝身份，再改**。
⑤ ⚠️ **「指针指向当前内容」这个前提本身要先验** —— 本层自己又踩了一次：把 `message-api` 拷贝的 U+FFFD 普查表行号按「删 import ⇒ 行号前移」改成 `:42` ⇒ ⇒ **G1 lane 独立否证并撤回** ⇒ ⇒ 实测该文件**当前 U+FFFD = 0**（坏字节**早已被 G2 修掉**）⇒ ⇒ 普查表记的是**扫描当时的历史原址** ⇒ ⇒ **这类指针不随代码平移** ⇒ ⇒ 改后的 `:42` 落在 `* </ul>`（一个**完全无关的行**）⇒ ⇒ **「按行号平移」的前提是「指针指当前内容」⇒ 而这个前提对历史快照表不成立**。

**本轮处置** —— 本文件 `:271` / `:520` 两处已就地订正为 `:231-236` / `:231`（单行→单行 ⇒ 行数 **598 不变** ⇒ 零行位移）；⚠️ **全批净订正 = 5 处 · 不改 = 5 处**（G2 报 3 处 + 本层误订 2 处）⇒ ⇒ 完整清单见 [`docs/Worktree.md`](../../Worktree.md) 的 **M-21**。
