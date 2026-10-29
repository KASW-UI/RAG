# `service/message/message-api` 踩坑台账

> **范围**：Go `service/message/api` → Spring Boot `service/message/message-api` 的迁移与验证。
> **分工**：差异本体在 [`../../DRIFT/message/api.md`](../../DRIFT/message/api.md)；
> 本文件只回答「**为什么会踩**」「测试为什么抓不到」「下次怎么防」。
> **首轮（2026-10-22）的教训在 [`_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md)
> 与 DRIFT 台账 §0；本文件记 2026-10-23 第二轮新挖到的坑。**

---

## PIT-MESSAGE-01 🔴 同一个组件、同一种形态，**第二次**「绿测试守卫错误答案」 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-MSGAPI-001` ⇒ **新号**：`PIT-MESSAGE-01`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 触发条件（DTO javadoc 手抄 Go tag + 绿测试把错误答案断言成规范）**今天仍在**：message 域仍有 1 处「Go 无 `,string`、RAG 加了 `@JsonFormat(STRING)`」的**已签字偏离**未销号 ⇒ 本轮凡改 DTO 注解、或改 `MessageApiMvcPipelineTest` 里 `affected` 那条断言，必须先回 Go `types.go` 核 tag，不能凭 javadoc 判定。
> **依据**：`service/message/message-api/src/main/java/com/rag/message/message/api/dto/AdminSendNotificationResp.java:43`（`@JsonFormat(shape = JsonFormat.Shape.STRING)`）· Go `service/message/api/internal/types/types.go:22`（`Affected int64` + `json:"affected"` tag，**无** `,string`）· 签字条目 `docs/Intentional_Deviations/message/api.md`（`SD-2026-10-23-03`）⇒ **证据强度：源码行号（静态）**。

**对应**：`DRIFT-MSGAPI-009` · **性质**：注解级类型转换（运行时**不报错**，只是值变了）

### 症状
`POST /message/v1/admin/notifications/send` 返回的 `data.affected` 在 RAG 上是 **JSON 字符串** `"3"`，
Go 上是 **JSON 数字** `3`。类型契约变了，客户端反序列化直接失败。
而 **77 条测试全绿**。

### 根因（不是「忘了对照」，是「对照了但抄错了 tag」）
Go 真源 `internal/types/types.go:21-23`：

```go
type AdminSendNotificationResp struct {
	Affected int64 `json:"affected"`      // ← 没有 ,string
}
```

RAG 侧 `dto/AdminSendNotificationResp.java:13` 的 javadoc 手抄成了
`Affected int64 \`json:"affected,string"\``，并据此加了 `@JsonFormat(shape = Shape.STRING)`。

⚠️ **`types.go` 里 `,string` 一共出现 12 处，其余 11 处 RAG 全部标对了**
（本轮出参探针逐条验证，见 DRIFT 台账 §4.2）—— **错的是唯一没有 `,string` 的那一个**。
⇒ 这不是「整体没对照」，是**逐字段对照时漏了一格**，而那一格恰好是反向的。

### 为什么测试抓不到（三层叠加）
1. **测试把错误答案断言成了规范**：
   `MessageApiMvcPipelineTest#adminSendNotificationAffectedIsString`（第 749 行）的
   `@DisplayName` 直接写着「**affected 是字符串**」，
   断言 `jsonPath("$.data.affected").value("3")`。
   ⇒ **修复时会先撞上这条红测试**，且测试名会让人**误判「改成数字」才是漂移方向**。
2. **探针的覆盖面有明确缺口，而且缺口被写在 README 里却没人当回事**：
   `tools/semantic-probe/README.md`「已知边界」白纸黑字写着
   「**出参方向的 `,string`（响应序列化成字符串）未覆盖** —— 本探针只测请求解析」。
   ⇒ **41 条 MVC + 23 例对拍，两套网恰好都漏掉了同一面。**
3. **DTO 的 javadoc 本身就是「事实来源」**：
   复核时先读 javadoc（它写着 `,string`）→ 判定「与 Go 一致」→ 不再去开 `types.go`。
   这是 §3.1「不要拿现有 RAG 代码或注释当对照基准」的**最隐蔽的一种**：注释伪装成了代码。

### 下次怎么防
1. **注解必须来自 tag 的机械转写，不允许凭印象**：
   建一张 `types.go` 每行的 tag → DTO 注解的**逐行对照表**（本轮已在 DRIFT 台账 §4.2 落了 4 行证据），
   改 DTO 时对着表核。
2. **探针必须有出参面**：只测请求解析的探针，天然测不到 wire contract 的另一半。
   加一节「序列化后逐字节比对」。
3. **断言方向检查**：见到 `@DisplayName` 里带「X 是字符串 / X 是数字 / X 与 Go 一致」，
   先确认**期望值是从 Go 源码推出来的**，不是从当前实现读出来的。
   （`AGENTS.md` §3.3.2 的硬要求：以「Go: …」措辞出现的断言，若不满足 §3.3.1 的三条，不得使用该措辞。）

---

## PIT-MESSAGE-02 🔴 Jackson 的 `FAIL_ON_NULL_FOR_PRIMITIVES` 默认为 `true`，go-zero 的 `,optional` 对显式 null 是**放行** ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-MSGAPI-002` ⇒ **新号**：`PIT-MESSAGE-02`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 本条在 message 域的触发条件（Jackson 3 的 `FAIL_ON_NULL_FOR_PRIMITIVES` 默认 `true` 打中 `,optional` 标量字段）**已被消除**，且全局关闭的副作用已逐字段核过（必填字段全是引用类型，不靠这个开关拒 null）⇒ 本轮**只改 `docs/**`**、零 `service/**` 改动 ⇒ 不会出现「改反序列化开关 / 新增 primitive 可空字段」的动作。
> **依据**：修法落点 `service/message/message-api/src/main/java/com/rag/message/message/api/config/MessageGoStyleCoercion.java:94`（`builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)`）· 守门 `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiGoStyleCoercionWiringTest.java`（真 MVC 6 例，含「关掉之后必填 + 显式 null 仍 5001」的反向守门）⇒ **证据强度：源码行号 + 守门测试在仓**。

**对应**：`DRIFT-MSGAPI-008`

### 症状
`{"offset":null}` / `{"unread_only":null}` / `{"kind":null}` 在 Go 上**成功**（落零值），
在 RAG 上 5001。客户端把 `null` 当「未设置」发出是很常见的写法。

### 根因：又一个**框架默认值与框架 tag 相反**的开关
- Go：`core/mapping/unmarshaler.go:854-863`
  ```go
  if mapValue == nil {
      if opts.optional() { return nil }     // ← ,optional：显式 null = 零值
      return fmt.Errorf("field %q mustn't be nil", key)
  }
  ```
- RAG：Jackson 3 的 `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` **默认 true** ⇒
  `Cannot map null into type int (set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)`。

### 为什么测试抓不到
**两套网都只覆盖了「必填字段的 null」**：
探针有 `required 显式 null(conversation_id)` 一例，`MessageApiMvcPipelineTest` 有对应的 MVC 一例，
两条测的都是**必填**字段——而必填字段在两侧都拒，**永远看不到 optional 的分歧**。

⚠️ 这与 `DRIFT-MSGAPI-003`（`extra` 显式 null，`,optional`）**方向相反**，
两条一起看才发现真相：**不是「RAG 对 null 一律更严」，而是「RAG 对 null 走的是 Jackson 的默认、
Go 走的是 tag 的 optional」**。只测其中一半，结论必然是错的。

### 下次怎么防
1. **`null` 的测试矩阵必须是 2×2**：{必填, optional} × {缺席, 显式 null}。
   只测「必填 + 显式 null」或只测「optional + 缺席」都会漏。
2. **遇到底层框架开关（`DeserializationFeature` / `CoercionAction` / `DeserializationProblemHandler`）
   先去查默认值，再去查 Go 的对应 tag**。AGENTS.md §3.3.1 要求「读到消费点」——
   这里**连声明点都不够，要读到默认值**。
3. ⚠️ **关掉这个 feature 是否安全**：Go 对**必填**字段的显式 null 是拒绝的，而 RAG 的必填判定
   在 `GoStyleRequired.field(name, value)`（只看值是否 null），**与 Jackson 的 null 开关正交** ⇒
   全局关掉不会让必填字段漏过。**但改之前必须配一条「关掉之后必填 + 显式 null 仍然 5001」的守门**，
   否则会把 `DRIFT-MSGAPI-003` 的正确结论改回去。

---

## PIT-MESSAGE-03 ⚠️ `CoercionAction` 是**白名单式的 Fail 补丁**，漏一个形状就是一条静默漂移 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-MSGAPI-003` ⇒ **新号**：`PIT-MESSAGE-03`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 本条的具体缺口（`Boolean ← Integer` 输入形状没被 Fail）已补；message 域的 coercion 规则集现覆盖 `LogicalType.Boolean` 的 `String` 与 `Integer` 两个输入形状 ⇒ 本轮不改 `service/**`、不新增 DTO 字段 ⇒ 不触发。⚠️ 「枚举判据而非枚举补丁」这条**方法论**仍适用（见下条 PIT-MESSAGE-06）。
> **依据**：`service/message/message-api/src/main/java/com/rag/message/message/api/config/MessageGoStyleCoercion.java:101,119`（`LogicalType.Boolean` 的 `CoercionInputShape.String` 与 `CoercionInputShape.Integer` 均配 `CoercionAction.Fail`）· 守门同 PIT-MESSAGE-02 ⇒ **证据强度：源码行号**。

**对应**：`DRIFT-MSGAPI-005`

### 症状
`{"broadcast":1}` 在 Go 上是 **5001**（type mismatch），在 RAG 上**成功**并落 `true`。
⇒ 管理员本来要**单播**一条通知，请求被理解成**广播**，向全站用户推送。

### 根因
`MessageGoStyleCoercion` 只补了两条：
```java
builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg.setCoercion(CoercionInputShape.String, Fail));
builder.withCoercionConfig(LogicalType.Boolean, cfg -> cfg.setCoercion(CoercionInputShape.String, Fail));
```
而 Jackson 对 **`Boolean ← Integer`** 的默认动作是 **`TryConvert`（不是 Fail）**。
⇒ 「看起来补过了」与「真的补全了」是两件事。

### 为什么测试抓不到
探针里有 `admin bool 收 JSON 字符串 "true"`（覆盖 `String` 形状），
**没有** `admin bool 收 JSON 数字 1`（`Integer` 形状）。
⇒ 用例是**按「我修了什么」列的**，不是按「Go 拒绝什么」列的。
这是 `AGENTS.md` §3.3 那条「**把没看过的东西变成看过了**」的第三种形态：
**把「我写了规则」当成了「我覆盖了形状」。**

### 下次怎么防
1. **枚举判据，而不是枚举补丁**：
   先把 Go 侧对某个目标类型**会拒绝的全部输入形状**列出来（`String` / `Integer` / `Float` /
   `Boolean` / `Array` / `Object` / `null`），再逐条对应到 Jackson 的 `CoercionInputShape`。
   **清单驱动，不是补丁驱动。**
2. **对每个形状各写一例双侧对拍用例。** 一条形状 = 一行代码 = 一例测试。

---

## PIT-MESSAGE-04 ⚠️ Go 对 **map 元素**的类型判据与 **顶层标量字段**不是同一段代码 ｜ **状态（核对日期 2026-10-29）：已闭环**

> **旧号**：`P-MSGAPI-004` ⇒ **新号**：`PIT-MESSAGE-04`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— map 元素级与顶层标量级两条判据的差异，已在 message 域**逐字段**落成（字段上的 `contentUsing`），探针也补了 map 元素格 ⇒ 本条在 message 域的触发条件已消除；本轮只改 `docs/**` ⇒ 不触发。
> **依据**：台账 `docs/DRIFT/message/api.md` 的 `DRIFT-MSGAPI-006` / `DRIFT-MSGAPI-007`（均 ✅ 已修）· 探针 `tools/semantic-probe/README.md:50`（用例清单 56 例，含 map 元素格）⇒ **证据强度：台账状态 + 探针清单行号**。

**对应**：`DRIFT-MSGAPI-006` / `DRIFT-MSGAPI-007`

### 症状
同一份 `map[string]string extra`：
| 输入 | Go | RAG |
|---|---|---|
| `{"extra":{"k":1}}` | **接受**（落字符串 `"1"`） | **拒绝** |
| `{"extra":{"k":null}}` | **拒绝** | **接受**（落 null） |
| `{"extra":{"k":true}}` | 拒绝 | 拒绝 |
| `{"extra":null}`（字段级） | 接受 | 接受 |

⇒ **字段级 null 两侧一致（`DRIFT-003` 已修），元素级 null 两侧相反。**

### 根因：两条不同的 Go 代码路径
- **顶层标量** → `processFieldPrimitiveWithJSONNumber`（`unmarshaler.go:664-715`）：
  `switch typeKind` 只有 Int/Uint/Float 分支，`default:` 直接
  `newTypeMismatchErrorWithHint` ⇒ 数字进 bool/string 一律拒。
- **map 元素** → `generateMap`（`unmarshaler.go:385-417`）：**多一个 `case json.Number:` 分支**，
  走 `setValueFromString(String, …, "1")` ⇒ 接受并字符串化；
  `nil` 落到 `default:`，因 `Invalid != String` 被拒。

### 为什么测试抓不到
`GoStyleCoercion` 的规则 1（`LogicalType.Textual, Integer/Float/Boolean → Fail`）
是**按目标 LogicalType** 配的，**对顶层字段和 map 值一视同仁** ——
而 Go 侧这两者本来就不一样。
探针也只测了 `extra` 的**字段级 null / 缺席**（`DRIFT-003` 那两例），**从没测过元素级**。

### 下次怎么防
1. **遇到容器类型（map / slice / 嵌套 struct），必须单独读 Go 的容器解析函数**，
   不能把标量字段的判据外推。**容器里有一条 Go 专有的宽松分支**（`case json.Number` 字符串化）。
2. 探针的 map 用例至少覆盖 **4 个位置**：字段级缺席 / 字段级 null /
   元素级 null / 元素级标量强转（数字 / bool）。

---

## PIT-MESSAGE-05 ⚠️ MockMvc 的 `.contentType(MediaType.APPLICATION_JSON)` 对 **charset 完全不敏感** ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-MSGAPI-005` ⇒ **新号**：`PIT-MESSAGE-05`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**否** —— 「MockMvc 的 `isCompatibleWith` 对 charset 免疫」是**框架事实**，不会消失 ⇒ 本条作为**测试方法论**仍生效；但 message 域**实例已修**（共享层 T-6 统一给 10 个 api 补 `charset=utf-8`）⇒ 本轮只改 `docs/**`、不动转换器与断言 ⇒ 本轮不触发。
> **依据**：实例已修：共享层 `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（常量 `GO_JSON_CONTENT_TYPE`）+ `:151`（消费点 `response.getHeaders().set(...)`）· 生效链 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2` → `service/message/message-api/pom.xml:64`（共享层 `common` 依赖）· 真 Tomcat 守门 `GoStyleJsonContentTypeCharsetTomcatTest` ⇒ **证据强度：源码行号 + 装配链**。

**对应**：`DRIFT-MSGAPI-010`

### 症状
Go 的 `application/json; charset=utf-8` 与 RAG 的 `application/json` 差一个 charset，
但 `MessageApiMvcPipelineTest` 里 **17 处** `.contentType(MediaType.APPLICATION_JSON)` 全部绿。

### 根因
MockMvc 的 `ContentTypeResultMatcher` 用 `expected.isCompatibleWith(actual)` 比较，
而 `application/json` 与 `application/json;charset=utf-8` **互相 compatible**
⇒ 这些断言**在设计上就对 charset 免疫**。

### 为什么这个坑更深一层
`Content-Type` 的 charset **不在业务代码里**：它来自
`AbstractJacksonHttpMessageConverter` 的 `defaultContentType`，
Spring Framework 6+ 起**刻意不再**给 JSON 补 charset。
⇒ 想判它，必须**实例化真实 converter 并调 `write`**，读源码读不出来。

⚠️ 与 [`common/http.md`](../../DRIFT/common/http.md) 那条（「代码里写了 ≠ 客户端收到」，
response 已 committed 后 `setHeader` 被静默忽略 ⇒ text/plain 400 丢 Content-Type，
4 轮对拍全绿）**是同一条规则的两种形态**：一次是「写不进去」，一次是「断言看不见」。

### 下次怎么防
1. **要锁响应头就不能用 `isCompatibleWith` 语义的 matcher** ——
   直接 `result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE)` 并**逐字符串比较**。
2. **判断一个响应头值，只能靠「把真实 converter / 真实容器跑一遍」**，
   不能从业务代码里推。
3. ⚠️ MockMvc 能测的与不能测的要分开登记：它测得到**状态码、body、多数响应头的存在性**，
   测不到**响应头的精确字符串**（charset、参数顺序）。

---

## PIT-MESSAGE-06 ⚠️ 探针的用例是**人工枚举**的，而字段数远大于用例数 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-MSGAPI-006` ⇒ **新号**：`PIT-MESSAGE-06`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 「用例按『我修了什么』列、不按『Go 拒绝什么』列」今天仍在 —— 探针的用例清单仍是**人工枚举**，README 自己列着「已知边界」 ⇒ 本轮**任何**往探针/测试里加断言的动作都可能再犯 ⇒ 本轮若动探针，必须按「字段 × 形状」矩阵补格，而不是按缺陷列表补条。
> **依据**：`tools/semantic-probe/README.md:50`（「用例清单（56 例）」）· `:10`（当前 56 例 = 一致 54 · 已签字偏离 2 · 不一致 0）⇒ **证据强度：仓内文件行号**。

**对应**：`DRIFT-MSGAPI-005` ~ `-009` 全部五条的共同成因

### 症状
「41 条 MVC 管线用例 + 23 例双侧对拍」听起来很密，实际覆盖的是
**6 个字段 × 若干输入形态**。Go `types.go` 有 **6 个 struct / 21 个字段**。
第二轮扩到 51 例之后，仍然立刻查出 5 条新漂移。

### 根因
- 用例是按**「我修了什么」**列的（`DRIFT-001` → 加 6 例 `,string`），
  不是按**「Go 对每个字段 × 每个输入形状会怎么判」**列的。
- 首轮探针 README 自己列了「已知边界」（逐字段覆盖缺口、出参方向未覆盖、map 强转未覆盖），
  **但没把这些边界变成 TODO**，于是它们在 CI 全绿的状态下静静躺着。

### 下次怎么防
1. **用例清单从「字段 × 形状矩阵」生成，不是从缺陷列表生成。**
   最低形状集：`{缺席, null, 字符串, 数字, 浮点, 布尔, 数组, 对象, 超范围}`。
2. **探针的「已知边界」必须写成待办编号**，并进 `AGENTS.md` 的组件状态表；
   没编号的边界 = 没人会做。
3. **探针只断言 ACCEPTED/REJECTED 会漏掉「两侧都接受但值不同」** ——
   本轮已补上「解析出的值」的比对。凡是 `,string` / 精度 / 溢出相关的字段，**必须比值**。
4. ⚠️ **不要把「探针全绿」写成「语义已对齐」**。23/23 一致只意味着
   **那 23 个组合一致**。台账里必须同时写「覆盖了哪些字段 / 没覆盖哪些」。

---

## PIT-MESSAGE-07 ⚠️ 否定式断言 + 「默认值藏在 tag 里」：本组件已被咬过两次 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-MSGAPI-007` ⇒ **新号**：`PIT-MESSAGE-07`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— Go 真源**已冻结** ⇒「Go 侧有没有 X / 默认值是多少」这类**否定式断言**的举证负担**不会下降** ⇒ 本轮凡在台账里写「Go 侧…」的断言，仍必须给**消费点**行号，否则等于把「没找到」写成「不存在」。
> **依据**：Go 真源冻结只读（AGENTS.md §一）· 本条正文的 4 次复发记录（Go `rest/config.go:12-22,52-56` · `zrpc/internal/config.go:14` · `rest/config.go:55` · `rest/engine.go:101-116`）⇒ **证据强度：Go 侧文件行号**。

**对应**：`DRIFT-MSGAPI-004`（首轮）与本轮 §9 的多条 UNRESOLVED

### 症状与复发次数
| # | 否定式断言 | 真实情况 | 出处 |
|---|---|---|---|
| 1 | 「Go yaml 没写 MaxBytes/MaxConns/Breaker」 | 由 `rest/config.go` 的 `json:",default=…"` **开启** | `rest/config.go:12-22, 52-56` |
| 2 | 「Go 侧没有 client 熔断」 | `zrpc/internal/config.go:14` `default=true` **开启** | 同上 |
| 3 | 「Go 侧没有 3s 超时」 | `rest/config.go:55` `Timeout default=3000`（且被 `DisableNativeRestTimeout` 换成自研版） | `service/common/observability/http.go:30-34` |
| 4 | 「Go 的超时在 auth 之内/之外」 | 由 `rest/engine.go:101-116` 的链构造顺序决定，**不在任何配置文件里** | `engine.go:108-111` |

⇒ **四次都是同一个形态：对照时只看了数据文件（yaml / 目录树），没看框架的默认值与装配顺序。**

### 为什么否定式断言特别危险
**「没找到」不能证明「不存在」。**
`grep Timeout etc/*.yaml` 返回空，只能说明 yaml 里没写；不能说明超时没生效。

### 下次怎么防
1. **否定式断言必须配一条正面证据**，且正面证据必须落在**消费点**
   （`AGENTS.md` §3.3.1：`文件:行号` 要指向消费该 tag 的那段控制流，不是声明行）。
2. **框架的默认值去框架源码找**（go-zero / Spring Boot 的 `@ConfigurationProperties` /
   `DeserializationFeature` / `CoercionAction`），不要在本仓库里找。
3. **中间件的顺序差异要单独建表**（本轮已建：DRIFT 台账 §5.1），
   顺序本身就是行为，而且**不在任何配置里**。

---

## PIT-MESSAGE-08 ⚠️ 「Bean 存在 / 注解在 / 依赖有」仍不等于「机制生效」——本轮再次触发 ｜ **状态（核对日期 2026-10-29）：仍生效**

> **旧号**：`P-MSGAPI-008` ⇒ **新号**：`PIT-MESSAGE-08`
> **核对日期**：2026-10-29
> **是否影响当前轮次**：**是** —— 本轮**要改的正是台账里的状态断言** ⇒「类在 / 注解在 / 依赖在 ⇒ 机制生效」这条推理链今天仍会骗人 ⇒ 本轮每条状态断言都必须落到**消费点行号**或**守门真实退出码**，否则一律标「待核实」。
> **依据**：AGENTS.md §3.3 / §3.3.1 · 本域范式 `service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiGoStyleCoercionWiringTest.java`⇒ **证据强度：仓库政策 + 守门在仓**。

**对应**：UNRESOLVED-002（`MessageGoStyleCoercion` 是否真的作用在 MVC 的 mapper 上）

### 症状
本轮的 51 例探针用的是
`JsonMapper.builder() + MessageGoStyleCoercion.applyTo(b)` ——
与 `WebMvcConfig#goStyleCoercionCustomizer` 的配置**同构但不同实例**。
⇒ 它证明「规则本身是什么」，**不证明「Spring MVC 用的就是这个 mapper」**。

### 为什么这条不能靠读代码解决
首轮 `GoStyleGrpcClientBreakerInterceptor` 的连续两个装配缺陷已经演示过：
**8 条单测全绿，类在真实 Spring 装配下从不执行。**
（`@Autowired` 缺失 ⇒ 上下文起不来；`@Component` 不足以被 starter 收集
⇒ `GlobalClientInterceptorRegistry` 返回 `[]`。）

### 下次怎么防
1. **凡是「经由框架扩展点注册」的机制，必须有一条断言「它出现在框架自己的 registry 里」的测试** ——
   不是「注解在类上」。模板：`GoStyleGrpcClientBreakerWiringTest`。
2. **自建 mapper 的探针必须显式声明它的证据边界**（本轮已在 UNRESOLVED-002 登记），
   不允许把「同构」写成「等价」。
3. ⚠️ **`JsonMapperBuilderCustomizer` 这条链上还有一个未验证的前提**：
   Boot 4 的 `JacksonAutoConfiguration` 会不会在 customizer 之后再改默认值？
   本轮未验证，只能说「`application.yml` 没有 `spring.jackson.*` 段」。

---

## 附：本组件「已验证 / 未验证」的分界线（2026-10-23 收口）

| 层 | 状态 | 证据形态 |
|---|---|---|
| 请求解析（入参） | ✅ **51 例双侧对拍** | Go 真源 `core/mapping` + RAG 真实 DTO + 真实 coercion |
| 响应序列化 | ✅ **本轮首次覆盖 8 例** | 抓出 `-009` |
| 路由 / 方法 / 鉴权边界 | ✅ 41 条 MVC 用例（RAG 单侧） | Go 侧期望值取自源码 |
| 保护层 413 / 503 裸响应 | ✅ 真 Tomcat（RAG 单侧） | 同上 |
| 响应头（charset / 404 / 405） | ⚠️ **转换器级，未真容器 curl** | UNRESOLVED-003 |
| 3s 超时 503 分支 | ❌ **零运行证据** | UNRESOLVED-004 |
| 断连 / 超时 / 并发 / 过载 | ❌ **零运行证据** | DRIFT 台账 §8.3 #6~#9 |
| 依赖 message-rpc 的响应语义 | ⚠️ **UNKNOWN**（属另一会话） | UNRESOLVED-001 |
---

<!-- @new 2026-10-23 22:5x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

## 2026-10-23 第三轮的 5 条元教训（比单个缺陷更重要）

### 1. 🔴 **「结论写进台账，探针却没进仓」** —— 本轮第一个发现

`DRIFT/message/api.md` §8.1 白纸黑字写着「本轮双侧探针（51 例，真源-双侧探针）」，
实测 `tools/semantic-probe/go/main.go` 与 `java/Probe.java` **都还是 23 例**，
且最后一次改动（commit `69e3262` / `e993ff0`，标题 `add shit`）**早于**台账的写入时间。

⇒ 五条 `SEMANTIC MISMATCH` 的**判定性证据不在版本库里**：
不可复现、无法守门、下一个人做同类改动时没有任何拦截。
§3.3 要求「两侧同时跑的探针」——**跑过 ≠ 落仓**。
⇒ **纪律**：任何以「探针第 N 例」为形式的结论，必须同时交出
① 探针两侧代码的 diff；② 一次完整运行输出。缺任一项 ⇒ 结论按 `UNKNOWN` 处理。

### 2. 🔴 **「只比 ACCEPTED/REJECTED」的探针，会系统性漏掉一整类漂移**

首轮探针的 README 自认「只看 Go 是否 ACCEPTED 与 RAG 是否 ACCEPTED 是否相同」——
于是「**两侧都接受、但落的值不同**」这一类完全不可见。
`affected` 的出参类型漂移（`DRIFT-MSGAPI-009` / 现 `SD-2026-10-23-03`）**正是这么漏掉的**。

⇒ 本轮把判定升级为「判定 + **值逐字比对**」，新增一档 `XX(值不同)`。
⚠️ 代价是**格式必须两侧逐字对齐**（Go 的 `mapStr` ↔ Java 的 `mapStr`：
整数无千分位、bool 用 `true/false`、map 按键排序）—— 格式不一致会制造假红。
⇒ **纪律**：跨语言的值比对，先各写一个「格式化函数」并让两侧互为镜像，再写用例。

### 3. 🔴 **顶层字段与 map 元素在 Go 侧是「两段代码、相反判据」**

这是本轮 `-006` / `-007` 的根因，也是最容易「按常识推错」的一处：

| 目标 | Go 的判据 | 位置 |
|---|---|---|
| 顶层标量字段收 JSON 数字 | **一律拒**（`switch typeKind` 无 `Bool` 分支 ⇒ `default` ⇒ type mismatch） | `core/mapping/unmarshaler.go:663-715` |
| `map[string]string` 的**值**收 JSON 数字 | **收下并字符串化**（`case json.Number → setValueFromString`） | 同文件 `:385-417` |

⇒ 在 Java 侧，这两个目标**都是 `String`**，共享同一条 `LogicalType.Textual` 规则，
**无法在 LogicalType 维度区分**；而 Jackson 3 的类级 coercion 实测**完全不生效**
（`GoStyleCoercion` 类注释里有最小复现实验）。
⇒ **唯一可靠挂点是字段上的 `contentUsing`**。

### 4. 🔴 **修 map 的 null 值，只覆写 `deserialize` 是不够的**

`ValueDeserializer` 实现 `NullValueProvider`：map 元素为 `null` 时 Jackson
**不走 `deserialize`**，而走 `getNullValue`，其默认实现返回 `null` ⇒ 静默放行。
⇒ 必须**同时**覆写 `getNullValue` 抛错。
⚠️ 且 `getNullValue` 里**不能**调 `handleUnexpectedToken(…, p)` ——
该路径 Jackson **没有给 parser**，传 `null` 会 NPE（用 `reportInputMismatch`）。

⇒ 推广：**修 Jackson 行为前先问「这条路径真的会调到这个方法吗」**，
这与 §3.3.1「必须读到消费该 tag 的那段控制流」是同一条纪律的 Jackson 版本。

### 5. 🔴 **两个方向相反的 null，别一起「顺手改」**

| 形态 | Go 结论 | 出处 |
|---|---|---|
| **字段级** `extra: null` | **接受**（`,optional` ⇒ `if mapValue == nil { return nil }`） | `unmarshaler.go:854-863` |
| **元素级** `extra: {"k": null}` | **拒绝**（elem kind `String` ≠ `nil` 的 `Invalid`） | `unmarshaler.go:385-417` |

名字几乎一样，结论完全相反。
本轮两条各留了一条用例（`fieldLevelExtraNullIsStillAccepted` /
`mapValueExplicitNullIsRejectedWithoutCallingRpc`）——
**反向守门必须在修之前就存在**，否则修完就没有基线可对照（§3.3.3）。
