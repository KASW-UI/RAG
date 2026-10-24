<!-- @move AGENTS.md L928-L1088 · §0 DRIFT-006 请求体 Content-Type 判据 -->
### ✅ DRIFT-006 关闭：**请求体 Content-Type 判据**对齐 Go 的 `withJsonBody`（2026-10-22）

> 用户「修复」。本节记录最后一条 like 域漂移（以及 message 域同型漂移）的关闭过程，
> 其中**原签字理由被推翻**、且**差点用「看起来最自然的修法」引入一条更糟的漂移**。

#### Go 侧真源（判据只有三行，但每一条都产生可观察差异）

```go
rest/httpx/requests.go:117-125
    func ParseJsonBody(r *http.Request, v any) error {
        if withJsonBody(r) {
            return mapping.UnmarshalJsonReader(io.LimitReader(r.Body, maxBodyLen), v)  // 真读
        }
        return mapping.UnmarshalJsonMap(nil, v)                                       // ⭐ 完全不看 body
    }
rest/httpx/requests.go:153-155
    func withJsonBody(r *http.Request) bool {
        return r.ContentLength > 0 && strings.Contains(r.Header.Get("Content-Type"), "application/json")
    }
```

#### 🔴 关键：Go 不是「宽松接受非 JSON」，而是**丢弃整个请求体**

判据不满足时走 `UnmarshalJsonMap(nil, v)` = **按空对象继续**，不是「拒绝」，也不是「勉强解析」：

| Content-Type | body | Go 结果 |
|---|---|---|
| `text/plain` | **完全合法、字段齐全**的 JSON | **丢弃** ⇒ 必填字段缺失 ⇒ `200 + 5001` |
| `text/plain` | 任意 | `PageReq` / `EmptyReq` / `GetUserTotalLikeReq` 等**零必填** DTO ⇒ **成功** |
| `application/vnd.api+json` | 合法 JSON | **丢弃**（`contains` 是**字面子串**，`+json` 后缀不含 `application/json`） |
| chunked（`Content-Length: -1`） | 合法 JSON | **丢弃**（`ContentLength > 0` 为假） |
| 缺失 Content-Type | 合法 JSON | **丢弃**（`Header.Get` 返回空串，`contains` 为假） |
| `application/json; charset=utf-8` | 合法 JSON | **读取**（`contains` 命中，不是精确相等） |

⚠️ 前三行各自都是一条独立于「415」的漂移，**只修 415 等于只修了最显眼的那一条**。

#### 🔴 原签字理由「两侧都是拒绝」**不成立**

`message-api` 当初对 415 的签字理由是「两侧都是拒绝、都无业务副作用」；`like-api` 沿用了同一结论。
**实测反例**：`PageReq`（`types.go:101-105`，字段全 `,optional`）与 `GetUserTotalLikeReq`（空结构体）
在 Go 侧遇到非 JSON Content-Type 会**成功**，不是拒绝。
⇒ 与 DRIFT-MSGAPI-004 是同款病灶：**「无害」判断建立在一个只验证了有必填字段那批端点的观察上**。

#### 落地：`com.rag.common.http.GoStyleJsonBodyConverter`（一处共享，两域共用）

继承 Spring 7 / Jackson 3 的 `org.springframework.http.converter.json.JacksonJsonHttpMessageConverter`，
覆写两处：

- `canRead(ResolvableType, MediaType)` → **无条件认领**。不放开的话 Spring 在<b>选择转换器阶段</b>
  就抛 415，根本到不了 `read`。
- `read(ResolvableType, HttpInputMessage, Map)` → 判据不满足时 **返回 `null` 且不消费请求流**。

**为什么返回 `null` 就是对的**：各端点都是 `@RequestBody(required = false)`，
`null` 会走到 `GoStyleRequest.normalize(null, factory)` ⇒ 零值 DTO + `assertGoRequiredFields()`
⇒ 与 Go 的 `UnmarshalJsonMap(nil, v)` **同构**（已在 like 域「空 body」路径上被验证过是对的）。

⚠️ 三条踩过的坑：

1. **不能用 `JacksonJsonHttpMessageConverter()` 无参构造器** —— 那会造出第二个**不带**
   `LikeGoStyleCoercion` / `MessageGoStyleCoercion` 的 mapper，把已用探针双侧对拍确认过的
   `,string` / `options=1|2|3|4` 语义**静默退回默认**。⇒ 只注入 Boot 的 `JsonMapper` Bean
   （`JacksonAutoConfiguration#jacksonJsonMapper`，与 Boot 的转换器同一个 builder），
   且**不提供无参构造器**，让「忘注入」变成编译错误。
2. **Spring 7 的签名是 `read(ResolvableType, HttpInputMessage, Map)`**，不是 Jackson 2 时代的
   `read(Type, Class, HttpInputMessage)`。用 `javap` 读的，没照抄旧写法。
3. **不写 `<version>` 给 spring-webmvc**：写死 `7.0.0` 本地根本没有（离线只有 7.0.5/7.0.9），
   更要命的是那等于在同一 classpath 引入**第二个 Spring Framework 版本**，
   会让 `@ConditionalOnWebApplication`、`WebMvcConfigurer` 出现「同名不同类」。
   ⇒ 交给 `spring-boot-dependencies` 管理。（旁边那条 `spring-web` 7.0.0 的硬编码是**既有**技术债，本次没动它。）

注册方式是 `GoStyleJsonBodyConverterConfig`（`WebMvcConfigurer#extendMessageConverters`），
**插到第一个 Jackson 转换器之前**而不是 `add(0)`：后者会盖过 `ByteArray`/`String` 转换器，
把影响面从「JSON 相关请求」扩大到全部 `@RequestBody`。

#### 🔴 差点用「看起来最自然」的修法引入更糟的漂移

最直觉的修法是「放宽 Content-Type 后**照常解析 body**」。它能让 415 消失、让测试变绿，
但它把 Go **明确拒绝**的请求（合法 body + 错 Content-Type）变成了**成功** ——
比 415 更糟，因为它看起来像"修好了"。
⇒ 三条测试专门盯这个：`nonJsonBodyIsIgnoredEvenWhenItIsValidJson` /
`vendorJsonSuffixIsNotApplicationJson` / `absentContentTypeAlsoDiscardsBody`，
并用 `verify(stub, never())` 直接断言**没调到下游**。
变异实测：把判据改成 `if (false)`（= 照常解析）⇒ **正好这 3 条转红**。

#### ⚠️ 写测试时我自己犯的一个错（已订正）

`MessageApiGoStyleBodyContentTypeTest` 的夹具最初写成 `{"offset":"10"}`，测试红了。
查 Go `types.go:101-105` 后确认：`PageReq.Offset` 是 `json:"offset,optional"`，
**没有 `,string`** ⇒ 字符串本来就该 type mismatch ⇒ **两侧都返回 5001，是一致的**。
⇒ 是**夹具写错**，不是实现坏了。已改为数字，并把这段查证过程写进夹具注释。
**教训**：测试红了先怀疑夹具，但**必须回去查 Go 源码**才能下这个结论——
「两边一致」和「实现有 bug」在这里长得一模一样。

#### 实测口径

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **146** | 不变（新增 2 个类，无新增测试） |
| `service/like/like-api` | **62** | 55 + 7（`LikeApiGoStyleBodyContentTypeTest`） |
| `service/message/message-api` | **77** | 73 + 4（`MessageApiGoStyleBodyContentTypeTest`） |
| `admin-api` / `user-api` | **119 / 69** | 113 + 6 / 63 + 6（**次日补网**，见下方「补网」小节） |

⇒ **`service/like/like-api` 的已登记漂移至此全部关闭**（DRIFT-001/002/003/004/005/006/007/008/010/011
均已处理；DRIFT-009 是被复核推翻的误报，已撤销留档）。
#### 🔴 连带事故：共享转换器一度让 **admin-api 回归 4 条**，且**第一次"修复"是无效的**

`GoStyleJsonBodyConverterConfig` 被 `com.rag.common` 的组件扫描**自动应用**到所有 api 模块，
包括 **admin-api** —— 而它的 Go 语义**不同**（`400 + text/plain`，不是 `200 + envelope`）。

**症状极具迷惑性**：`AdminApiMvcPipelineTest` 的 4 条红了，但

- HTTP 状态码**对**（400）
- `Content-Type` **对**（`text/plain`）
- 只有 **body** 被 JSON 转义了：`field "uid" is not set` → `"field \"uid\" is not set\n"`

⇒ **只看响应头会以为"没问题"**（DRIFT-024 同款：响应对了不代表 body 对）。

**根因**：`setSupportedMediaTypes(MediaType.ALL)` 对**读写两侧同时生效**；
`AbstractSmartHttpMessageConverter` 会据此认为它能写出 `text/plain`，
于是抢在 `StringHttpMessageConverter` 之前把 admin-api 的 `ResponseEntity<String>` **按 JSON 字符串写出**。

##### 🔴 第一次「修复」等于没修：覆写了**错误的重载**

第一次只加了 `canWrite(Class<?>, MediaType)` → `false`，admin-api **仍然红**。
用 `javap -c` 看字节码才看清（`SmartHttpMessageConverter`）：

```
public abstract boolean canWrite(ResolvableType, Class<?>, MediaType);   // ← 真正被 MessageWriter 调用
public default  boolean canWrite(Class<?>, MediaType);                    // ← 只是 default 委托给上面那个
```

`AbstractSmartHttpMessageConverter` 的两参实现体第一件事就是
`ResolvableType.forClass(clazz)` 然后 `invokevirtual canWrite(ResolvableType, ...)`。
⇒ **覆写「委托的那一环」等于没覆写**。
⇒ 现在两个重载都覆写，并在注释里写明原因。
（同理 `canRead` 必须覆写带 `ResolvableType` 的那个 —— 本类从一开始就覆对了，所以读侧一次就通。）

> **教训**：当「机制说得通但实测不生效」时，**去读字节码**，不要继续推断。
> 本次是连续两次「推理正确、对象错误」：先是判据读错（把丢弃当解析），
> 后是重载覆错（委托链的另一端）。

⚠️ 附带一次**口径陷阱**：`user-api` 在中途那次全量 sweep 里报了 4 条同症状失败，
**隔离重跑却是绿的**（20/20）。原因是并发 Maven 会话下 `~/.m2` 里
`com.rag:common` 快照被抢占/陈旧（§0 已记的同一个坑）。
⇒ 那 4 条<b>不能</b>记为「修复不完整」，最终口径以**串行重跑**为准。
**但反过来也成立**：如果只看隔离跑，就会漏掉 admin-api 那次真实回归 ——
**共享组件的爆炸半径只能用全量跑发现，隔离跑永远看不到邻居受害**。

#### ⚠️ 本次共享实现的**适用边界**（写给下一个 agent）

`GoStyleJsonBodyConverter` 解决的是「**什么时候读请求体**」这一层，**不含错误响应形态**。
各域的解析失败形态仍然各归各域，且**确实不同**：

| 域 | 解析失败 | Go 出处 |
|---|---|---|
| `like-api` / `message-api` | `200 + {"code":5001}` | `likecenter.go:48-61` / 同款注册了 `httpx.SetErrorHandler` |
| `admin-api` | `400 + text/plain` | **未**注册 error handler ⇒ `doHandleError` 的 `handler == nil` 分支 ⇒ `http.Error(..., 400)` |

⇒ 「共享**判据**、不共享**表现**」是这里的正确切分线。
若日后有人想「把 400/5001 也统一」，那是一次**跨域行为决策**，不是本次的收尾工作。

