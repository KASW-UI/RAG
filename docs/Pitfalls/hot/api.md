# Pitfalls · `service/hot/hot-api`

> 领域：`docs/Pitfalls/hot/api.md`（教训本体）
> 差异本体在 [`docs/DRIFT/hot/api.md`](../../DRIFT/hot/api.md)。
> **核对日期：2026-10-24**（第四轮：日志面 / `CodeError` 对齐 + 真 Tomcat / Prometheus 导出取证）。
> 本文件只写「为什么会踩 / 框架怎么静默失效 / 下次怎么防」，不重复记差异本身。

---

## P1 — 「配置文件里没写」既不等于「没开」，也不等于「没做」

**现象**
首轮对照照着 `etc/hotcenter.yaml` 逐字段核，结果漏掉了 9 个配置项里的 **6 个**：
HTTP 超时 3s、gRPC 超时 2s、MaxConns 10000、MaxBytes 1 MiB、CpuThreshold 900‰、client breaker 开启。
yaml 里**一个字都没有**，照 yaml 对永远看不见。

**根因**
两头的默认值都藏在别处：

- Go 侧藏在 **struct tag**，且必须读到**消费点**才算数：
  - `rest/config.go:55` `Timeout int64 \`json:",default=3000"\`` —— 这是**声明点**；
  - 真正的**消费点**在 `service/common/observability/http.go:30-33`
    `DisableNativeRestTimeout()`：它先把 `conf.Middlewares.Timeout = false`（关掉 go-zero 原生超时），
    再把 `conf.Timeout` 读出来交给自己的中间件 ⇒ **超时只有一个执行者**。
  - 只贴 `rest/config.go:55` 不算证据：你不知道这个值有没有人读、读了算不算。
- Java 侧的默认值在**框架装配层**，不在注解里，也不在 yaml 里。

**为什么容易被漏**
`hotcenter.yaml` 是逐字段可读的，读它有一种「我已经核对过配置」的错觉。
默认值藏在 tag 里这件事，只有在你**去读消费代码**时才会暴露。

**怎么防**
1. 对 Go 配置，一律走「声明点 → 消费点」两跳，只贴声明点直接记 `UNKNOWN`。
2. 本项目已把这条写进 AGENTS.md §3.3.1，本条是该条在 `hot-api` 上的第二个实例
   （第一个实例是 `like-api` 的 `default=3000` + `type mismatch`）。
3. 核对配置时先问一句：**「这个值是谁在读？」** 答不上来就是没核。

---

## P2 — `@ResponseBodyAdvice` 的二次包裹：对 `@ExceptionHandler` 返回的 `ResponseEntity` 同样生效

**现象**
`GlobalResponseAdvice.beforeBodyWrite` 若无条件包一层，错误响应会变成：

```json
{"code":200,"msg":"OK","data":{"code":5001,"msg":"internal server error","data":null}}
```

**顶层 `code` 恒为 200** ⇒ 所有「按 `code != 200` 判失败」的客户端**全部失效**，
且排查时看到的响应「像是成功的」，极易被当成下游问题。

**根因（框架机制）**
`HttpEntityMethodProcessor` **继承**了 `AbstractMessageConverterMethodProcessor.writeWithMessageConverters`
并在其内部**查询 advice chain**。
⇒ 连 `@ExceptionHandler` 返回的 `ResponseEntity`（走的是 `HttpEntityMethodProcessor`）
也在链上，**不是**「只有 controller 正常返回才被包」。

**为什么测试可能抓不到**
如果测试只断言「controller 直接抛异常」这一条路径，恰好绕开了二次包裹；
或者断言写成了 `jsonPath("$.data.code").value(5001)`（**在 data 里找 code**），
就把错误答案锁成了「正确答案」。

**怎么防**
1. `beforeBodyWrite` 里至少两条放行分支，缺一条就出事：
   - `body == null` → 放行（否则 404/405/保护层裸状态码会被包成「成功但无数据」）
   - `body instanceof Response` → 放行（信封已成型）
2. 断言要打在**顶层**：`jsonPath("$.code").value(5001)`，**不是** `$.data.code`。
3. 本项目已有绿测试 `HotApiMvcPipelineTest.errorEnvelopeIsNotDoubleWrapped` 覆盖这一条 ——
   **它是对的**，但要注意它只覆盖「异常路径」，不覆盖「裸状态码路径」。

---

## P3 — 断言用的 mapper ≠ 运行时用的 mapper（本轮实测出的假绿）

**现象**
`HotApiWireContractTest` 用**裸 `new ObjectMapper()`** 断言响应字节，全绿。
但**真实运行时**（`@SpringBootTest` + `@AutoConfigureMockMvc`）返回的 JSON
**字段顺序是字母序**，Go 是**声明序**。两者字段值、类型、null 语义**逐字节等价**，
**只有顺序不同** —— 所以 `jsonPath` 类断言也照样全绿。

**根因（2026-10-23 第三轮修正：不是 Boot 开的，是 Jackson 3 把默认值翻了）**
Boot 4 的 web 栈用的是 **Jackson 3**（`tools.jackson.core:jackson-databind:3.1.5`，
经 `spring-boot-starter-jackson` 进来），
而 **Jackson 3 把 `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` 的默认值从 false 翻成了 true**。

字节码取证（两侧同一行代码、不同结果）：

| 库 | `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` 的构造实参 | 默认 |
|---|---|---|
| Jackson 2 `com.fasterxml.jackson.databind.MapperFeature` | `iconst_0` | **false**（声明序） |
| **Jackson 3 `tools.jackson.databind.MapperFeature`** | **`iconst_1`** | **true（字母序）** |

⇒ **不是** `spring-boot-jackson` 里「显式开启」了它 ——
全 jar 扫描 `SORT_PROPERTIES_ALPHABETICALLY` 字符串**零命中**；
是 **Jackson 3 自己的默认**。
⇒ 也**不是** `Jackson2ObjectMapperBuilder.json().build()` 开的 ——
那个 builder 造的是 **Jackson 2** 的 mapper，默认仍是 false。
⇒ ⚠️ **这解释了本条里最反直觉的那格**：
「裸 mapper 与 builder 都是 false、唯独运行时是 true」的真正原因是
**它们根本不是同一个 Jackson**（2 vs 3），而不是「Boot 的装配层做了什么」。

⚠️ **反直觉的实测结果**（别记反）：

| mapper | `SORT_PROPERTIES_ALPHABETICALLY` |
|---|---|
| `new ObjectMapper()` | false（声明序，**与 Go 一致**） |
| `Jackson2ObjectMapperBuilder.json().build()` | **false** |
| **Spring Boot 装配的那个（运行时）** | **true（字母序）** |

⇒ 「用 builder 就不一样」是错的。差异**只**出现在 Boot 的装配层。
⇒ 所以本轮最初怀疑「是不是 standalone 的问题」也是错的：
standalone 与真 Boot 实测**都是字母序**。

**为什么测试抓不到**
它测的 mapper 与运行的 mapper 不是同一个对象，
而「序列化顺序」这种差异**既不改变 JSON 解析结果，也不改变业务判定** ——
所有非字节断言天然看不见它。

**怎么防**
1. 写 wire 契约测试时**注入运行时那个 `ObjectMapper` bean**，不要 `new ObjectMapper()`。
   ⚠️ 注意 Boot 4 的运行时 bean 是 **Jackson 3**（`tools.jackson.databind.json.JsonMapper`），
   **Jackson 2 的 `ObjectMapper` 在 classpath 上但没有 Bean**（`common` 传递来的只有 jar）——
   这正是 `hot-rpc` 当年起不来的那个缺口的同一个根因。
2. 本项目的 `difftest` 若按字节比对，就必须显式决定：
   关掉 Boot 的字母序（`Jackson2ObjectMapperBuilderCustomizer`），
   或把比对改成「JSON 语义比对」。**不能含糊** ——
   否则「逐字节一致」这个说法在字段序上是假的。
3. 这是 AGENTS.md §3.3 那条纪律的一个新变体：
   **「断言 Bean 存在 ≠ 断言机制生效」的孪生句是「断言用 A ≠ 运行时用 A」。**

---

## P4 — MockMvc 不做 percent-decoding，会伪造出「不存在的漂移」

**现象**
用 `mockMvc.perform(get("/hot/v1/articles?page=%2B5"))` 探测，得到 `{"code":5001,...}`，
而 Go 侧同样的输入返回 `page=5`。看起来是一条新漂移。

**真相**
两条都不是漂移，是**两个夹具各自的不同失真**：

- **MockMvc 把 query string 原样交给参数绑定，不做 URL 解码** ⇒ `%2B5` 原样进了
  `@RequestParam`，`Integer.parseInt("%2B5")` 失败 ⇒ 5001。
- 真 Tomcat 会把 `%2B` 解成 `+` ⇒ `Integer.parseInt("+5", 10) = 5`，
  而 Go 的 `strconv.ParseInt("+5", 10, 32) = 5` ⇒ **两侧本来就一致**
  （Go 与 Java 的整数解析**都接受前导 + 号**）。

用 `.param("page", "+5")` 构造（绕开 URL 串）就能直接看到 `page=5`。

**为什么危险**
如果照着 MockMvc 的结果去「修」`GoFormValues`，就会**为了迎合一个测试夹具的失真**
而改坏本来正确的实现 —— 而且改完 MockMvc 会绿，**更没人发现改错了**。
这正是 AGENTS.md §3.3.2 警告的形态：**测试变成错误实现的守卫**。

**怎么防**
1. 探测 percent-encoding 行为时**不要用 URL 字符串**构造请求，用 `.param(name, decodedValue)`。
2. 看到「两侧不一致」时，先问：**「这个差异是运行时行为，还是夹具行为？」**
   判据 = 把同一语义用另一种构造方式再跑一次，看结论是否翻转。
3. 真要验编码路径，只能上真 Tomcat（见 P5）。

---

## P5 — 「代码里写了」与「客户端收到」之间隔着一次 commit（本组件已吃两次亏）

**现象**
`HttpMiddleware` 的响应包装器早期把响应头**缓存起来**、打算在 `chain.doFilter()` 之后统一写回。
四轮对拍**全绿** —— 因为 difftest **只比 body 不比响应头**，而 body 恰好是对的。
直到 2026-10-22 在**真实 Tomcat** 上实测才发现：

```text
200 JSON 响应 → Content-Type: application/json   正常
400 text/plain → Content-Type 完全丢失，且 Transfer-Encoding: chunked + Connection: close
```

**根因（框架机制）**
`flushTo` 在 `chain.doFilter()` **之后**才执行。此时下游
`outputMessage.flush()` → wrapper `flushBuffer()` → **底层 Tomcat 已 committed**，
此后 `dst.setHeader(...)` 会被 Tomcat **静默忽略**（不报错、不断言）。

**为什么 MockMvc 测不到**
MockMvc 的 `MockHttpServletResponse` **没有 commit 语义** ——
响应头写多晚都还在，Content-Type 永远"正常"。

**本轮的第二处同款**
`writeTimeoutResponse`（超时 / 499 出口）设了状态码、Content-Type、body、尾随换行，
**唯独漏了 `X-Content-Type-Options: nosniff`** ——
而同一个组件的 404 路径（`GoStyleRouterErrorResponses:98`）**是**设了的。
两条走 `http.Error` 形状的响应，头部字段不一致。

**怎么防**
1. **响应头断言必须在真容器上做**，MockMvc 的响应头断言只能算「代码写了」的证据，
   不能算「客户端收到」的证据。
2. 抄 Go 的 `http.Error` 时按它的**四行**逐行对，别只对 body：
   ```go
   h.Del("Content-Length")
   h.Set("Content-Type", "text/plain; charset=utf-8")
   h.Set("X-Content-Type-Options", "nosniff")
   w.WriteHeader(code); fmt.Fprintln(w, error)
   ```
   （`net/http/server.go:2337-2356`）
3. 同一组件里**多条走同一形状的响应路径**，头部字段要一起核 ——
   漏一条的现象是「404 有 nosniff、503 没有」这种内部不自洽。

---

## P6 — Go 的 `httpx.Parse` 会读 body，但本端点上**读了等于没读**

**现象**
台账首轮记了一条 D7：「GET + JSON body 覆盖 query，Go 的 `httpx.Parse` 还会跑
`ParseJsonBody`，RAG 未实现」。看起来是一条待补的漂移。

**真相**
`httpx.Parse`（`requests.go:50-77`）确实**无条件**跑 `ParseJsonBody`，
但对本端点的结构体**完全无效**：

```go
// core/mapping/utils.go:589-597
func usingDifferentKeys(key string, field reflect.StructField) bool {
    if len(field.Tag) > 0 {
        if _, ok := field.Tag.Lookup(key); !ok {
            return true          // ← 有 tag、但没有本层的 key ⇒ 整个字段跳过
        }
    }
    return false
}
```

`HotArticlesReq` 的两个字段只有 `form:"…"` tag、**没有 `json` tag**，
而 `ParseJsonBody` 用的 unmarshaler 的 key 是 `jsonTagKey` ⇒
`unmarshaler.go:571-573` 直接 `return nil`，**一个字段都不碰**。

Go 侧活体探针实测（真 go-zero v1.10.2）：

```text
?page=5 + body {"page":9,"page_size":3}   →  page=5  pageSize=20   ← JSON 被忽略
无 query  + body {"page":9}               →  page=1  pageSize=20   ← JSON 被忽略
POST     + body {"page":9}                →  page=1  pageSize=20   ← JSON 被忽略
```

⇒ RAG「不读 body」与 Go 行为**一致**。**首轮的 D7 是一条假阳性。**

**但真漂移在隔壁一格**
`?page=5` + `Content-Type: application/json` + body `{`（**畸形**）
⇒ Go 在 `ParseJsonBody` 阶段就报错 ⇒ `5001`；RAG 不读 body ⇒ **成功**。
这才是需要决策的那一条（DRIFT-HOTAPI-013）。

**元教训（本轮最贵的一条）**
**「Go 调了某个函数」≠「那个函数对本端点有效果」。**
`ParseJsonBody` 出现在控制流里是真的，但它对**没有 `json` tag 的字段**是死代码。
⇒ 只读「调用链上有没有它」会得到假阳性；
必须**再往里读一层**，确认它在**这个具体结构体**上是否真的生效。

顺带一条同族结论：
**Go 的默认值确实来自 struct tag**，但**重复键取首值**这件事来自另一个开关 ——
`httpx/requests.go:27-31` 给 `formUnmarshaler` 传了 `WithFromArray()`，
消费点在 `unmarshaler.go:835-846`（`val.Index(0)`）。
少了这个开关，重复键就不是取首值（详见 DRIFT-HOTAPI-011）。

---

## P7 — `strconv.ParseInt` 与 `Integer.parseInt` 的分歧，比「base 0 vs base 10」多一条

**现象**
`@RequestParam(defaultValue="20") int pageSize` 这种「地道写法」会让 `?page=010` 变成 **8**（八进制）。
这是已知的。但**只盯着 base 这一个分歧**会漏掉另外两条 —— 都是「Go 成功 / Java 失败」方向：

| 输入 | Go（`strconv.ParseInt(s,10,32)`） | Spring 默认（`NumberUtils.parseNumber`） | 本项目 `GoFormValues` |
|---|---|---|---|
| `?page=010` | **10** | 8（八进制） | 10 ✅ |
| `?page=0x10` | **失败** → 5001 | 16（静默通过） | 失败 ✅ |
| `?page=99999999999` | **失败**（越界）→ 5001 | 溢出截断 | 失败 ✅ |
| `?page= 5`（前导空格） | **失败** → 5001 | — | 失败 ✅ |
| `?page=+5`（前导 +） | **5** | — | 5 ✅ |

⇒ 落到实现上就是显式 `Integer.parseInt(v, 10)`，**不要**用 Spring 的自动转换。

**元教训**
「Go 与 Spring 的整数转换有分歧」这句话本身**不够可执行**。
必须落成一张**逐条输入 × 两侧输出**的对照表，
否则「我们处理了 base 问题」很容易被当成「我们处理了转换问题」。

顺带：`?page=`（空值）在 Go 是**取默认值**而不是报错，
因为空值在 `httpx/util.go:44-45` 被**整个丢弃**，key 压根不进 map ——
这不是「Spring 把空串转换失败」，是「Go 认为这个参数不存在」。

---

## P8 — 读 go-zero 源码前先确认版本：同一台机器的 module cache 里有三个版本

**现象**
`ls ~/go/pkg/mod/github.com/zeromicro/` 给出 `go-zero@v1.10.0`、`v1.10.2`、`v1.10.3` 三个目录。
直接 `cat` 第一个（v1.10.0）就会读到**行号不同**的同一段代码 ——
而本项目台账里所有引用都写着 `v1.10.2`。

**根因**
本仓库 `go.mod:17` 钉的是 `github.com/zeromicro/go-zero v1.10.2`，
但 module cache 是**全局共享**的，里面躺着其它项目拉过的其它版本。

**为什么危险**
AGENTS.md §3.1 要求每个 Go 事实带 `文件:行号`。
**行号本身就是证据的一部分** —— 引用 v1.10.0 的行号去支撑一条
建立在 v1.10.2 上的结论，等于给了错误坐标。

**怎么防**
读 go-zero / 任何三方库源码前，**先从 `go.mod` 确认版本，再用那个版本目录**：

```bash
grep -n "zeromicro/go-zero" go.mod
GZ=~/go/pkg/mod/github.com/zeromicro/go-zero@<那个版本>
```

本轮的实际后果：v1.10.0 的 `rest/config.go:55` 恰好也是 `Timeout default=3000`，
结论侥幸没变 —— **但这是运气，不是方法**。
若结论靠的是行号，这一步漏了就直接是错证据。

---

## P9 — `@Nested` 测试类的 surefire `.txt` 摘要会少报，别拿它合计

**现象**
`HotRankPaginatorTest` 有 14 个 `@Test`（分布在 4 个 `@Nested` 类里），
但 surefire 的 `.txt` 摘要写着 `Tests run: 0`。
按 7 个 `.txt` 合计得 **45**，而台账记的是 **59**。

**真相**
`.txt` 摘要只统计**直接方法**，`@Nested` 内的用例被归到别的条目。
逐个解析 `TEST-*.xml` 的 `tests` 属性才是准的：
`7 + 5 + 4 + 10 + 12 + 7 + 14 = 59` ✅ —— **台账的 59 是对的，45 是我拿 `.txt` 加出来的错数。**

**元教训**
这是一次**「差点写出假纠正」**：
如果我按 45 写进台账，就会把一条**正确的既有结论**标成「原结论有误」。
⇒ 复核一个数字之前，先确认**取数口径**，别拿一个口径的数字去纠正另一个口径的结论。

**怎么防**
统计测试数一律读 `target/surefire-reports/TEST-*.xml` 的 `tests` 属性；
`.txt` 只用来看失败详情。

---

## P10 — 「测试绿」与「测试有效」是两件事，本轮在同一组件里各抓到一次

本轮**没有**做系统变异测试（D2 仍未关闭），但抓到了两处**已存在的假绿**：

| 位置 | 假绿形态 | 后果 |
|---|---|---|
| `HotApiWireContractTest` | 用**裸 `ObjectMapper`** 断言 wire 字节，运行时却是 Boot 装配的 mapper | 字段序测试**永久无效**（P3） |
| `HotApiMvcPipelineTest` 的 404/405 | 用 **MockMvc** 断言响应头，MockMvc 没有 commit 语义 | 真 Tomcat 下的响应头**未被覆盖**（P5） |

⇒ 两处的共同形态：**测试用的东西 ≠ 运行时用的东西**。
这与 AGENTS.md §3.3.3 的「`@Transactional` self-invocation 失效」同源 ——
不是「没写」，是「写了但对的对象不是你以为的那个」。

**怎么防**
每条 L1（字节级 / 协议级）断言，回答三个问题：
1. **这个对象是运行时的那个吗？**（mapper / filter / 容器）
2. **这个环境是生产的那个吗？**（MockMvc vs 真 Tomcat vs Mockito）
3. **换一个同义输入，结论会翻转吗？**（夹具失真自检，见 P4）

答不上来就**不要**把它算进「已验证」，按 AGENTS.md §3.3 记 `UNKNOWN`。

---

## 附：本组件踩坑的证据强度一览

| 条目 | 现象来源 | 证据 |
|---|---|---|
| P1 配置默认值藏 tag | 读 Go 源码 | 静态双侧（读到消费点） |
| P2 advice 二次包裹 | 读 RAG 源码 + 已有绿测试 | 静态 + 已有绿测试覆盖 |
| P3 mapper 装配差异 | **本轮实测**（真 Boot 上下文 + builder 对照实验） | ✅ 双侧实测 |
| P4 MockMvc 不解码 | **本轮实测**（URL 串 vs `.param()` 两种构造） | ✅ 双侧实测 |
| P5 响应头与 commit | 已有实测记录（2026-10-22 E-1）+ 本轮发现 503 漏 nosniff | ⚠️ 静态 + 历史实测 |
| P6 `ParseJsonBody` 死代码 | **本轮实测**（Go 活体探针 4 组 JSON body 用例） | ✅ Go 活体 |
| P7 整数转换分歧 | **本轮实测**（15 组 form 用例逐条对照） | ✅ 双侧实测 |
| P8 版本与行号 | **本轮踩到**（v1.10.0 / v1.10.2 / v1.10.3 并存） | ✅ 实测 |
| P9 surefire `.txt` 少报 | **本轮踩到**（45 vs 59） | ✅ 实测 |
| P10 测试有效性 | 本轮抓到 2 处 | ✅ 一处实测、一处实测 |

⚠️ **本表里没有任何一条是靠「读一遍代码觉得没问题」得出的。**
P3 / P4 / P6 / P7 / P9 都是**先有分歧或先有疑点，再用探针分清「真漂移」与「夹具失真」** ——
这个动作本身就是本项目 §3.1「不要一次读就下结论」的可操作版本。
---

## P11 — `@RequestParam String` 在**同名多值**时被 Spring 拼成逗号串（不是取首值）

**现象**
`?page=5&page=3` 在 Go 侧是 **200 + page=5**，在 RAG 侧是 **200 + `{code:5001}`**（整包失败）。
方向是「**Go 成功 / Java 失败**」—— 最难被发现的一类漂移，因为**两边都返回 200**。

**根因**
Spring 的 `RequestParamMethodArgumentResolver` 拿到的是 `String[]`，
转换到 `String` 时走 `ArrayToStringConverter` ⇒ **用 `,` 拼接** ⇒ `"5,3"`
⇒ 后续 `Integer.parseInt("5,3")` 失败 ⇒ 整包 5001。
Go 侧则是 `core/mapping/unmarshaler.go:835-846` 的 `fromArray` 分支 ⇒ **`val.Index(0)`**（取首值）。

**为什么测试抓不到**
凡是**只测「单值」**的用例（`?page=5`、`?page_size=7`）两侧都过；
**重复参数是一种「参数个数」的维度**，不在任何单值用例的覆盖里。
⇒ 59 条用例全绿，而这条 MISMATCH 就在门外。

**怎么防**
1. 凡是自己解析 query 的地方，**不要用 `@RequestParam String`** ——
   直接 `request.getParameterValues(name)` 取第 0 个（还要记得 Go 的**空值丢弃**与 **`[]` 后缀**两条规则）。
2. **把「同名多值」写进用例矩阵**：每个 form 字段至少一条「重复键」用例。
3. go-zero 的 `GetFormValues` 里还有两条同源的规则值得整体搬：
   `httpx/util.go:44-45`（**空值丢弃**）、`httpx/util.go:59-61`（**`[]` 后缀剥离**）、
   `httpx/util.go:15,48-53`（**2048 个 form 值上限**）。

---

## P12 — Go 的 **map 遍历序** ⇒ 同一个 URL，**Go 自己会给出不同结果**

**现象**
`?page=1&page[]=2` 这类「同名 + `[]` 后缀混用」的输入，
Go 侧连跑 8 次得到 `page=2` 6 次、`page=1` 2 次 —— **真源自己是非确定性的**。

**根因**
`rest/httpx/util.go:36` 的 `for name, values := range r.Form` 遍历的是 **map**（顺序随机），
而 `:59-61` 又把 `page[]` 的后缀剥掉写进**同一个** `params["page"]` ⇒ **谁覆盖谁随机**。

**为什么这件事值得单独记**
如果不去跑真源、只照源码推，会得出「Go 取 `page=1`」的**错误确定性结论**，
然后把 RAG 写成「取 `page[]` 的值」或「取后出现的值」——
**两条都与 Go 不一致**（Go 6/8 次是 2）。

**怎么防**
1. **对拍前先确认真源这一格是确定的**：同一个输入跑 8 次，结果不一致 ⇒ 那是**真源的非确定性**，
   要么登记为签字偏离，要么在 fixture 里明确「这是 Go 的行为包络」。
2. **不要把「真源非确定」当成「RAG 的 bug」去修**，也不要为了「像 Go」而引入随机性 ——
   RAG 取**包络内**的一个确定值即可，并在台账里写明。
3. 推论：**Go 侧凡是 `map` 参与决策的地方，都可能有非确定性**，
   对拍脚本要能区分「RAG 错」与「Go 自己会变」。

---

## P13 — 顺序类断言必须用**原始字节**；`jsonPath` 天然看不见顺序

**现象**
`jsonPath("$.data.items[0].article_id").value("a1")` 这类断言，
在「声明序」与「字母序」两种输出下**都绿** ⇒ 字段顺序漂移对它**完全隐形**。

**根因**
JSON 解析成树之后，**字段顺序信息被丢掉了**；
`jsonPath` 只按 key 取值，不看 key 的先后。

**怎么防**
1. 顺序类断言只能断言**原始响应体字符串**（`getContentAsString()`）里
   各字段名的**下标先后**；`jsonPath` 一律无效。
2. 想让顺序**不再依赖 mapper 配置**，用 `@JsonPropertyOrder` **显式钉死** ——
   注解优先于 `SORT_PROPERTIES_ALPHABETICALLY` 这类全局特性
   （本仓已有先例：`like-rpc` 的 `LikeEventContracts`、`comment-rpc` 的 `CommentCacheService`）。
3. 加一条**变异**：把 `@JsonPropertyOrder` 去掉，顺序断言**必须变红** ——
   否则你只是把「当前的字母序」固化了下来，等于把漂移锁进了测试。

## P14 — 「配了 SLO 边界」不等于「导出就是那些边界」：Micrometer 的 `publishPercentileHistogram()` 会**换掉**它们

**现象**
`HotApiMetrics` 里明明写着

```java
.publishPercentileHistogram()
.serviceLevelObjectives(GO_DEF_BUCKETS)   // GO_DEF_BUCKETS = Go 的 prometheus.DefBuckets 逐条转写
```

注释还言之凿凿：「不显式开这两个，dashboard 上的 `histogram_quantile` 在两侧不可比」。
**结果真抓 `/actuator/prometheus` 一看，导出的是 80 个 `le` 边界**，Go 侧只有 12 个
⇒ 两侧的 p95 **根本不是一个数**，而所有绿测试都看不出来（断言只到「meter 定义」层）。

**真实原因**
`publishPercentileHistogram()` 在 Micrometer 里不是「额外开一个直方图」，
而是**强制把直方图换成 Micrometer 自己的 exponential percentile 桶**
（69 个：`0.001, 0.001048576, 0.001398101, …`）。
你给的 `serviceLevelObjectives(...)` 只是被**合并**进这套桶里，**并没有替代它**。

实测四个变体（Micrometer 1.16.7 + `io.micrometer.prometheusmetrics`）：

| 变体 | `le` 边界数 |
|---|---|
| `publishPercentileHistogram()` + SLO | **80** |
| **只 SLO** | **12**（Go 的 11 + `+Inf`）✅ |
| 只 `publishPercentileHistogram()` | 69 |
| 裸 Timer | 0（只有 `_count/_sum/_max`） |

⇒ **「只 SLO」才是唯一能给出 Go 那 11 个边界的配置**；
「SLO + percentile」是**最糟**的组合（80 个）。

**为什么危险**
1. **注释与实现相反**：注释说「开了就能可比」，实现恰好开了那个让它不可比的开关
   ⇒ **注释是「意图」，不是「事实」**，读者会照着注释相信已经对齐了。
2. **症状只在导出层可见**：`meter` 定义、注册、记录全都正常，**零告警**。
3. `histogram_quantile` 是**慢查询**才用到的指标 ⇒ 出问题时往往已上线很久。

**怎么防**
1. **凡是「把 A 的分桶对齐到 B」的断言，必须验**导出文本**里的边界序列**，
   不是验「配置项写对了」。⇒ 真 `/actuator/prometheus`（或至少真 `registry.scrape()`）。
2. 断言**数量 + 逐条数值 + 反向断言**（Micrometer 独有的 exponential 边界一个都不许出现）。
3. 配置项之间会**互相覆盖**（本例是「开 percentile ⇒ 覆盖 SLO」）⇒
   **组合**要逐个实测，不能各自单测都过就以为组合也对。
4. ⚠️ **探针自己也会写错**：我第一版探针 grep 的是 `<meter>_bucket`，
   而 Micrometer 会给 Timer 名字**自动加单位后缀**（`xxx_seconds_bucket`）
   ⇒ 探针报「0 个 bucket」，看起来像「没生效」，实际是**探针的前缀写错了**。
   ⇒ **探针的阴性结果也要自证**：先用「已知会有的东西」校准探针，再拿它去测未知。

## P15 — 🔴「变异没红」的**第一反应应该是「我的变异脚本错了」**，不是「守门不敏感」

**现象**
给 hot-api 新增的 `GoStyleGrpcClientBreakerWiringTest` 做变异验证时，
把 `@GrpcClient("hot-rpc")` 改成 `@GrpcClient("hot-rpc-renamed")`，
用例**仍然全绿**。

⚠️ 按直觉，下一步应该是「这条断言对 channel 名不敏感 ⇒ 它是弱断言 ⇒ 降级/改写」。
⚠️ **如果真那么做，就会把一条本来有效的守门改成弱断言** —— 而**生产代码其实没问题**。

**真实原因**
变异脚本写成了：

```python
old = '@GrpcClient("hot-rpc")'
new = '@GrpcClient("hot-rpc-renamed")'
assert old in s                    # ← 只断言「存在」
p.write_text(s.replace(old, new, 1))   # ← 替换**第一个**出现
```

而这个类里 `@GrpcClient("hot-rpc")` **出现两次**：
- 第 19 行 —— **javadoc 注释**里的 `{@code @GrpcClient("hot-rpc")}`；
- 第 48 行 —— **真正的注解**。

`replace(..., 1)` 改的是**第一个** ⇒ **注释被改了，注解原封不动**
⇒ 生产行为完全没变 ⇒ 测试当然绿。

⚠️ 更糟的是：那次变异还**把生产源码的注释污染了**（javadoc 里出现了不存在的 channel 名），
差点连着提交进去。

**修正后**
① 变异脚本加 `assert s.count(old) == 1`（**只命中 1 处**才允许替换）；
② 变异后**先看字节码**（`javap -v` 看注解值）再跑测试 —— 别跳过这一步；
③ 跑完立刻**恢复**并用 `git diff` 确认工作区干净。

⇒ 用正确脚本重跑 M-W1 ⇒ **变红** ⇒ 证明那条断言**本来就是敏感的**。

**怎么防**
1. **变异脚本的锚点必须带上下文**（缩进 + 下一行），不能只匹配一个可能重复出现的短串；
2. **`count == 1` 是硬门槛** —— 命中多处说明锚点不唯一，**该停下来改锚点**，不是加 `replace` 次数；
3. **「变异没红」的排查顺序**：① 变异真的编译进去了吗（`javap` 看字节码）→ ② 变异脚本改对地方了吗
   → ③ 断言真的敏感吗。**顺序反了就会去改本来正确的守门**。
   ⚠️ 这与 `docs/Pitfalls/hot/rpc.md` **P-HOTRPC-10**（「先怀疑断言，别急着怀疑生产代码」）
   是**镜像**：那次是「先怀疑断言」，这次是「**先怀疑工具**」——
   ⇒ 两者合起来是：**先怀疑你的工具，再怀疑世界，最后才怀疑被测代码**。
4. ⚠️ 改生产源码做变异时，**注释里的同名文本是最大的坑** ⇒ 优先改**有独特上下文**的位置。

---

## P16 · MockMvc 不是「真 Tomcat」：它跳过请求行校验、header 长度上限、参数个数上限

> **触发**：2026-10-24 第五轮（U1/D1 端到端对拍）
> **代价**：第二轮台账里 **4 条 ✅ 结论** 被真栈推翻（D21 / D22 / D23 + D12 的运行时阈值）

### 现象

第二轮用 MockMvc 测出来的结论：

| 用例 | 第二轮结论（MockMvc） | 第五轮真栈实测 |
|---|---|---|
| `?page[]=5` | ✅ 5 / 20 | 🔴 **HTTP 400 + Tomcat HTML 错误页** |
| `?page=5&page=3` | ✅ 5 / 20 | ✅ 5 / 20 |
| 2048 个 form 值 | ✅ OK | ✅ OK |
| 2049 个 form 值 | ✅ 5001 | 🟡 **1001 个就 5001** |

⇒ 前两行 MockMvc 全绿，真栈全红。

### 根因

MockMvc 直接调 `DispatcherServlet`，**不经过 Tomcat 的 HTTP 解析层** ⇒ 下面三道闸它一道都碰不到：

| 闸 | Tomcat 11 的位置 | Boot 4 属性（默认） | Go 的对应（库级默认） |
|---|---|---|---|
| 请求目标字符校验 | `Http11InputBuffer.parseRequestLine` → `HttpParser.isQueryRelaxed` | `server.tomcat.relaxed-query-chars`（无） | `net/http` **允许** `[` `]` |
| 请求行 + 头总长 | `maxHttpHeaderSize` | `server.max-http-request-header-size`（**8KB**） | `net/http` `MaxHeaderBytes`（**1 MiB**） |
| 参数个数 | `Parameters` / Connector | `server.tomcat.max-parameter-count`（**1000**） | go-zero `maxFormParamCount`（**2048**） |

### 怎么防

1. **凡是要断言「HTTP 入口长什么样」的用例，一律用 `RANDOM_PORT` + 真实 `HttpClient`**
   （本仓第四轮已用它做过一次 —— `HotApiRealTomcatGateTest`），
   **不要**用 MockMvc 断言「状态码 + 响应头 + 原始 body」。
   ⚠️ 判据：**断言里出现 `Content-Type` / `X-Content-Type-Options` / `Allow` / 原始 body 字节**
   ⇒ MockMvc 不合格。
2. **端到端对拍要覆盖「query 的形状」**，不只覆盖「query 的值」。
   本轮 4 条漂移里有 3 条是**形状**问题（`[]` 后缀、值的个数、query 的长度），
   而第二轮的 25 条分页/归一化用例全是**值**问题 ⇒ 覆盖面上就漏了。
3. ⚠️ **Tomcat 的默认值比 Go 的库默认值更严**，这是**全仓**问题（10 个 api 模块同款）
   ⇒ 修法是**显式配**，不是「RAG 照 yaml 对」—— 因为 Go 那边这些值**根本不在 yaml 里**
   （§3.3.1：藏在 struct tag / 库默认值里）。

---

## P17 · 探针自己也会被环境污染：上一个动作留下的环境会量出假数

> **触发**：2026-10-24 第五轮（`probe-hot-limits.py` 第一版）

### 现象

边界探针第一版量出「Go 侧首个翻转 n = 1125，HTTP 503」—— **假数**。
Go 的真实限制是 **2049**（值个数）。

### 根因

`diff-hot.py` 的**最后一条**用例是 `CASE-21`（下游睡 30s，触发 3s 超时），
它把桩的**模式文件**留在了 `article-slow` 上 ⇒ 探针的每个请求都被拖过 3s 超时
⇒ 量到的不是「限制点」，是「超时点」。

### 怎么防

1. **探针启动时必须显式设定它依赖的环境**，不能假设「上一个动作留下的环境是对的」。
   本轮修法：探针先把模式切回 `hot-rpc-empty`（hot-rpc 返 0 条 ⇒ **不发任何 article-rpc 调用**
   ⇒ 响应时间与被测变量无关）。
2. **用「无副作用」模式做测量**：测上限时不要让下游参与。
3. ⚠️ 判据：**探针量出来的数字必须能被另一个独立观测解释**
   （本轮：修正配方下重测 ⇒ 两侧都是 2049，且与 go-zero 源码常量 `maxFormParamCount = 2048` 吻合
   ⇒ 才认这个数）。**孤证不算数**。
4. 与 **P15** 同源：**先怀疑工具，再怀疑世界**。

---

## P18 · 「谁写 socket」决定了哪个错误分支**可达** —— 抄框架的分支形状不够

> **触发**：2026-10-24 第七轮（U3/D3 断连取证）

### 现象

Go 与 RAG 的 499 分支**看起来同构**：

| | 分支代码 |
|---|---|
| Go | `if errors.Is(ctx.Err(), context.Canceled) { statusCode = 499 }` |
| RAG | `if (isClientCancelledException(panic)) { statusCode = 499 }` |

但**真栈实测**：Go 侧断连后**必定**产出 499（还进日志），
RAG 侧**一次都没产出过**（日志里连 499 字样都没有）。

### 根因：两边的 499 由**不同信号**驱动

- Go 的 499 = **ctx 被取消** ⇒ 与「响应写在哪一层」**无关** ⇒ 断连必然触发；
- RAG 的 499 = **worker 线程里抛了写失败** ⇒ 而本组件的响应体写在 **worker 的内存缓冲**里，
  真正写 socket 的是 **filter 线程**的 `flushTo` ⇒ 写失败抛在 filter 上 ⇒
  **worker 的 panic 通道永远是空的** ⇒ 该分支**结构性不可达**。

⇒ **抄了分支形状 ≠ 抄了触发条件。** 分支的可达性由「信号从哪来」决定，
而信号又由「这个组件的写发生在哪一层」决定。

### 怎么防

1. 抄一条错误分支时，除了抄 `if` 条件，还要问一句：**这个条件依赖的信号，
   在**本组件**的架构下还会出现吗？**
2. 断言「某分支可达」必须**真触发**它，不能靠「代码里有这个 if」——
   本仓 §3.3 的老坑（注解存在 ≠ 机制生效）在这里是**分支存在 ≠ 分支可达**。
3. 判别一个分支是否可达，最省事的办法是**造出让它失效的输入再跑一遍**：
   本轮用「客户端断连」这个 Go 有、RAG 没有的信号，一测就分开了。

---

## P19 · 「变异编译进去了」≠「测试用到了它」—— 单模块跑会用 **~/.m2 里已安装的 jar**

> **触发**：2026-10-24 第七轮（U4 熔断 E2E 的变异验证）
> **代价**：第一版变异验证**假绿**（改了源码、也编进了工作树的 `target/classes`，测试却依然通过）

### 现象

变异目标在**跨模块**的 `service/common`：

```java
// service/common/.../GoStyleGrpcClientBreakerInterceptor.java
- if (!breaker.accept()) {
+ if (!breaker.accept() && Boolean.FALSE) {   // MUTATION-M-U4
```

变异后跑测试：

```bash
mvn -o -pl service/hot/hot-api test -Dtest=GoStyleGrpcClientBreakerE2ETest ...
```

⇒ **测试通过**（绿）⇒ 第一反应是「这条断言不敏感」。

### 真因（比 P15 又深一层）

1. `mvn -pl service/hot/hot-api compile` 编出的是 **`service/common/target/classes`**（带变异）——
   `javap` 一看，变异**确实在字节码里**；
2. 但 `mvn -pl service/hot/hot-api test`（**不带 `-am`**）解析 `com.rag:common` 依赖时，
   走的是 **本地仓库 `~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-...jar`**
   ⇒ 那是**上一次 `install` 时的旧 jar**（未变异）；
3. ⇒ 测试的**运行时**用的是未变异的字节码 ⇒ 熔断照样打开 ⇒ 断言照样通过。

⇒ **P15 只查了「变异编进去了吗」；这次要查的是「测试的运行时用的是哪一份字节码」。**

### 怎么防

1. **变异验证一律用 `-am`**：`mvn -o -pl <被测模块> -am test -Dtest=...`
   ⇒ 同一 reactor 内解析依赖到 `target/classes`，**不碰 ~/.m2**；
   ⚠️ 配套参数是 `-Dsurefire.failIfNoSpecifiedTests=false`
   （`-DfailIfNoSpecifiedTests=false` **不生效**，上游模块会因「没有匹配的测试」直接 BUILD FAILURE
   ⇒ 失败在**上游模块**，看起来像「测试没跑」）；
2. **别用 `mvn install` 装变异版到 ~/.m2** —— 那是**共享状态**，
   并发会话会直接吃到你的变异（AGENTS.md 已警告并发会话干扰）；
3. **判别依据要看「运行时用的哪一份」**，不是「源码改没改」：
   ```bash
   javap -c -p -cp ~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar \
          com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor | grep -c Boolean
   ```
   ⇒ 安装 jar 里 **0** 次、工作树 classes 里 **1** 次 ⇒ 差的那份就是真相；
4. **「变异没红」的排查顺序要再往前插一步**：
   ① 变异编进**哪一份**字节码了？→ ② 测试运行时用的是**哪一份**？→ ③ 脚本改对地方了吗？
   → ④ 断言敏感吗。
   （P15 的顺序是 ①②③，P19 在最前面插了「运行时用的是哪一份」。）

### 附带一条：Go 熔断统计的「只认已完成桶」

第一版测试还犯了个**语义**错误：断言「熔断未开时前 40 次都打到下游」。
实测只有 16 次 ⇒ 因为 go-zero `googlebreaker.go:52-79` 的 `total` 来自
`collection/rollingwindow.go:65-80` 的 `Reduce`，它 **`ignoreCurrent`**
（注释：<i>ignore the current bucket, because of partial data</i>）
⇒ **只有已完成的 250ms 桶**计入统计 ⇒ 统计窗口实际是 39 个完整桶 ≈ 9.75s。
⇒ 写断言前必须先问「这个计数器统计的是**哪个窗口**的什么」。
