# admin-api 踩坑台账

> 本文件只记**教训**：每个坑写成「现象 → 根因（框架 / 语言 / 认知机制）→ 为什么测试抓不到 → 下次怎么防」。
> 差异本体在 [`docs/DRIFT/users/admin/api.md`](../../../DRIFT/users/admin/api.md)（`DRIFT-ADMINAPI-0xx`）。
>
> **来源**：2026-10-23 admin-api 组件级 Go ↔ Spring Boot 语义验证（spec §31-b）。
> **证据强度**：本轮为 `静态源码·双侧`；文中凡称「已有测试」的均为 2026-10-21/22 建网、**本轮未复跑**。

---

## P-ADMINAPI-001 · 断言「ctx 里的某个值是什么」必须把**写入侧**也读完

- **现象**：第一遍读 Go 时，`banuserlogic.go:36-39` 看着像「取当前管理员 uid」：
  ```go
  var adminUid int64
  if uidNumber, ok := l.ctx.Value("uid").(json.Number); ok { adminUid, _ = uidNumber.Int64() }
  ```
  自然结论是「`adminUid` = 当前登录管理员」。**这个结论是错的，而且错得很贵** ——
  它把一条 SEMANTIC MISMATCH 伪装成了一条实现细节。

- **根因**：`uid` 这个 key **在整个 Go 侧 admin 域从来没有人写过**。要证明这一点必须跨三个文件：
  1. `service/user/common/jwt/jwt.go:8-11` —— admin 侧唯一签 token 的地方，claims 只有 `exp` / `iat` / `userId`；
  2. go-zero `rest/handler/authhandler.go:72-78` —— `for k, v := range claims { ctx = WithValue(ctx, k, v) }`，
     **key 就是 claim 名**，所以 ctx 里有什么完全由 token 内容决定；
  3. 同一段的 switch 又过滤掉 `aud/exp/jti/iat/iss/nbf/sub` ⇒ ctx 里实际**只剩 `userId`**。

  ⇒ `adminUid` **恒为 0**，且取不到时**不报错**。

- **为什么测试抓不到**：`AdminSemanticDriftApiRegressionTest#updateuser 的 operatorId 取自 token，不取请求体`
  这条断言测的是「**RAG 内部** operatorId 来自哪」，它对 Go 的行为一无所知。
  更危险的是 `admin/_shared.md` 的 **N-4** 已经给「operator_id 不恒为 0」签了字 ——
  **一个已签字的条目会让下一个 agent 停止追问**，而 N-4 签的只是「审计字段」那一半，
  「Go 根本不校验 claim、RAG 会因此拒绝」是**另外半条**，至今没人看过。

- **下次怎么防**：
  1. 凡是要断言「某个 ctx / attribute / ThreadLocal 的值是什么」，**必须同时给出写入侧的行号**；
     读不到写入侧就只能写进 `unresolved`，不能写成结论。
  2. go-zero 特有：`context.WithValue` 注入的是**claim 名**，所以「ctx 有什么」=「token 里有什么」，
     而 token 由**签发方**决定 —— 跨 api/rpc 追 token 时要从 `jwt.GetToken` 起追，不能从 logic 起追。
  3. **已签字 ≠ 已覆盖**。签字条目与新发现落在**同一条代码路径的不同维度**上时，
     新发现必须显式写「N-x 覆盖的是 A，本条覆盖的是 B」，否则下一轮一定会被当成重复劳动而丢掉。

---

## P-ADMINAPI-002 · 「更严格的校验」是最难自查的一类漂移，因为它写得太自然

- **现象**：`IdentityResolver.resolveAdmin` 里有两道检查，注释齐全、命名规范、单元测试友好：
  ```java
  if (admin == null || admin.equals(AdminInfo.getDefaultInstance())) throw new ErrAdminNotFoundException(...);
  if (admin.getUid() != uid)                              throw new ErrIdentityMismatchException(...);
  ```
  Go 侧 `getselflogic.go:62-69` **一道都没有**。一个普通 Spring 开发者读到
  「JWT 里的 uid」和「DB 里的 uid」，几乎必然会加上这两道 —— 它们太合理了。

- **根因**：**Java 侧比 Go 侧多一个「层」**（identity 层），而这个层是**我们自己设计的**，
  不是从 Go 翻译来的。多一层 ⇒ 多一条拒绝路径 ⇒ 多一批只在畸形数据上才出现的响应码差异。
  §26 的 counterfactual test 说的就是这种形态：**实现看起来合理，恰恰是危险信号**。

- **为什么测试抓不到**：`IdentityResolverTest` 测的是「两道检查在**被触发时**抛什么」，
  它锁住的是 RAG 自己的行为；两侧对拍里 `getself` 的正常路径两边都返回同一个 admin，
  于是 39 步 / 273 条矩阵全绿。**只有「下游返回空 Admin」这一种输入会暴露它**，
  而那需要 admin-rpc 配合构造 —— 于是它活过了 4 轮审计。

- **下次怎么防**：
  1. 审查里对**每一处 `throw` / `return err` 做一次「Go 有对应分支吗」的显式回答**。
     写不出来的那处，就是候选漂移（哪怕它是「修 bug」）。
  2. **防御性断言默认是漂移**，除非能指出 Go 的哪一行会产生同样的拒绝。
  3. 逐端点对照时**按「拒绝分支」而不是按「成功分支」列清单** ——
     成功路径两侧几乎总是相同，差异全在拒绝分支上。

---

## P-ADMINAPI-003 · 读 `json:",omitempty"` 时不能只看 tag 名：它对 struct 无效

- **现象**：`types.go:72` 写着 `User UserInfo \`json:"user,omitempty"\``，
  RAG 侧照着写了 `@JsonInclude(NON_NULL)` + `if (hasUser()) setUser(...)`。
  结果：Go 恒输出 `"user":{"uid":"0",...}`，RAG 在下游未 set 时**整个字段消失**。

- **根因**：Go 的 `omitempty` 只对 `false` / `0` / nil 指针 / nil 接口 / 空 array / slice / map / string 生效，
  **对 struct 永远不生效**。tag 名骗人。

- **为什么测试抓不到**：`difftest` 的 `matrix.py` 按字段展开边界，但它展开的是**请求 DTO**，
  响应 DTO 的「字段是否出现」不在矩阵的展开维度里。
  而这条差异要下游返回空对象才触发，**两侧默认都不会**。

- **下次怎么防**：
  1. 复制 Go 的响应 tag 到 Java 注解时，先问一句「**这个类型会被 omitempty 生效吗**」：
     struct ⇒ 不会；指针 / map / slice ⇒ 会。
  2. 响应 DTO 的「字段存在性」应当成为对拍维度，而不只是 body 的值相等
     （`{"user":{...}}` 与 `{}` 在很多断言写法下都会「通过」）。
  3. 本条本轮只做了静态判定（未跑探针）⇒ 已登记 `UNRESOLVED-003`。**证伪成本是 10 行 Go 程序**，
     下次遇到先跑它，别靠推理。

---

## P-ADMINAPI-004 · `standaloneSetup` 测 advice，**测不到 Filter 与容器时序** —— 而本组件的测试全是 `MOCK`

- **现象**：本组件 5 个 `@SpringBootTest` 全部是 `webEnvironment = SpringBootTest.WebEnvironment.MOCK`，
  而 `AdminResponseEnvelopeAdviceTest` 更早一层，用的是 `MockMvcBuilders.standaloneSetup`。
  加上项目历史上「`HttpMiddleware` 的 status propagation 被 Spring 的 commit 时序吞掉」
  （`DRIFT-FRAMEWORK-001`：**所有 `@ExceptionHandler → 401` 在运行期看起来全绿、实则全被降级成 200**），
  这两件事叠在一起意味着：**advice 的断言再绿，也证明不了真实容器里的最终出参**。

- **根因**：`standaloneSetup` 只装你手动 `setControllerAdvice` 的东西，**不装任何 Filter**；
  `MockMvc` + `MOCK` 环境虽然会装 Filter，但响应对象是 `MockHttpServletResponse`，
  它的 `setHeader` / `setCommitted` 时序**与 Tomcat 不同**（项目里已经实测到过「MockMvc 下头完好、真实栈下头丢失」）。

- **为什么测试抓不到**：它本来就是绿的。**这类测试只能证明「代码写成了这样」**，
  证明不了「客户端收到这样」。

- **下次怎么防**：
  1. 凡断言「**响应头**」或「**状态码在容器里真的是这个值**」，必须有一条 **真 Tomcat** 用例。
  2. **⚠️ 具体的坑（本轮实测到的）**：真实 Tomcat 守门脚本
     `tools/difftest/check-plaintext400-headers.sh` 的两个端点常量写死为
     `RAG_USER=http://127.0.0.1:59900` / `GO_USER=http://127.0.0.1:18888`
     ⇒ **只打 user-api，脚本里没有任何 admin 入口**（admin 的端口在 `diff.py:36` 是 58889 / 18889）。
     于是 `DRIFT-024`（400 响应头丢失）在 **admin-api 上零守门**。
     **教训不是「要写测试」，而是「守门脚本要按组件枚举入口」** ——
     一个只覆盖 user 的脚本很容易被读成「users 域已覆盖」。
  3. 审查时把「本组件的测试环境是 MOCK 还是 RANDOM/DEFINED_PORT」列为**必答项**，
     不要看到 `@SpringBootTest` 就默认是真实容器。

---

## P-ADMINAPI-005 · 「统一异常类型」会把业务码一起统一掉

- **现象**：黑名单命中时，Go 输出 `401` + `{"code":401,"msg":"登录已失效,请重新登陆"}`；
  RAG 输出 `401` + `{"code":1006,"msg":"TOKEN已过期","data":null}`。
  状态码是对的（都是 401），**业务码和 msg 全变了**。

- **根因**：`AuthenticationException` 统一了两种鉴权失败，注释也诚实地写了
  「两处都是 401，这是必须对齐的部分；两处 body 形状不同是 Go 自身的不一致，本端统一成完整 envelope」。
  **但它只声明了「状态码 + 形状」，从没声明「业务码」**。
  而业务码恰恰是客户端 SDK 真正读的字段 —— 形状统一是**排版**问题，业务码统一是**语义**问题。

- **为什么测试抓不到**：`AdminApiMvcPipelineTest` 的黑名单用例断言的是
  `status == 401` + body 含 `1006`。**它断言的是 RAG 自己的现状**，不是与 Go 的一致性。
  绿得毫无问题。

- **下次怎么防**：
  1. 凡把 N 种失败**收敛成一个异常类型 / 一个码**，必须逐个列出
     「Go 的 code → msg → 形状 → HTTP 状态」四元组，并**对四元组的每一格**单独判断是否有意偏离。
  2. 留档里出现「统一」「一致」「对齐」这类词时，逐字确认它修饰的是哪一维。
  3. §3.3.2 的纪律在这里再验证一次：**断言必须写「本模块要求的语义：…」而不是「Go: …」**，
     除非那条 Go 结论真的满足了三段取证 —— 本轮的 `code:401` 就是只做过两段取证的产物。

---

## P-ADMINAPI-006 · 台账里的**计数**（几个 client / 几个 Service）也要 grep 复核

- **现象**：任务简报里写「本组件有 **2 个 client**：admin-rpc **和** user-rpc，这**两个 rpc 都在被并行审查**」，
  依据是 `AGENTS.md` §4.2 Phase 4。实测：
  - Go 侧 `service/user/admin/api` 全树 grep `user/rpc` → **零命中**；
  - RAG 侧 `@GrpcClient` → **只有 1 个**（`AdminRpcClient.java:25`）。

  真正残留的是**误导性的资产**：`application.yml:70-75` 有一段**死的** `grpc.client.user-rpc`，
  `pom.xml:81-84` 的注释写着「连 admin-rpc + user-rpc」。

- **根因**：与项目里已记录的两种同款病灶完全一样 ——
  `AGENTS.md` §2 的 `HUMAN_ONLY.MD` 清单（登记了 3 份**实际不存在**的文件）、
  §3.3.3 的「文件已写 / 依赖已引 ≠ 存在」。**注释和 yaml 都只是声明，不是证据。**

- **为什么测试抓不到**：没有测试会断言「一个模块有几个 client」。
  而**一段死配置是完全无害的** —— 它不会让任何用例变红，只会误导下一个 agent 的依赖分析
  （本轮就浪费了「admin-api → user-rpc」的合并判读预算）。

- **下次怎么防**：
  1. 任何以「N 个 X」形式出现的**资产计数**，在用于依赖分析之前先 `grep` 一次；
     台账里的数字只当**线索**。
  2. 反过来：发现死配置（引了但没人用的 client / 依赖 / Bean）时，
     **要么删，要么在台账里点名** —— 留着不提，下一轮一定会有人拿它当依赖。

---

## P-ADMINAPI-007 · 「401 空 body」这类**已签字**条目，签字范围要按**维度**拆开写

- **现象**：`admin/_shared.md` 的 **N-1** 签的是「鉴权短路统一成 HTTP 401 + 完整 envelope」。
  它确实覆盖了：Go 的 `authhandler.go:121` 写 401（body 空）⇢ RAG 写 401（body envelope）。
  但**它没有覆盖**：签名有效而 claim 无效时，Go 是 `200 + {code:1006}`、RAG 是 `401 + {code:1006}`
  （`DRIFT-ADMINAPI-003`）。同一类「鉴权失败」，两侧的分层判定点不同，
  而签字条目只描述了**其中一种触发方式**。

- **根因**：签字时是按**代码位置**写的（「`AuthenticationException` 这个类」），
  而漂移是按**触发条件**产生的（缺头 / 签名错 / 过期 / claim 无效 / 黑名单 —— **5 条**）。
  位置维度的签字天然覆盖不了触发条件维度。

- **下次怎么防**：
  1. 签鉴权相关的偏离时，**先枚举触发条件清单**（逐条列），再逐条判定，而不是按类 / 按位置判一次。
  2. 审查新发现时，主动问「这条落在我已签字的那条的**哪个维度**上」——
     位置相同、维度不同 ⇒ **不算已签字**，必须新立条目。
  3. `AGENTS.md` §3.3.3 已经有同款纪律（「代码里写了 ≠ 客户端收到」），
     这里补一条：**「同一个类里已签字 ≠ 这个类的所有行为都已签字」**。

---

## P-ADMINAPI-008 · 「多一层设计」的代价要**在层边界**上记账，而不是在实现文件里

- **现象**：`HttpMiddlewareFilter`（servlet Filter）在 `AdminJwtAuthInterceptor`（HandlerInterceptor）**外层**；
  Go 的对应关系是 observability 中间件在 Authorize **内层**（`rest/engine.go:108-112` 的 append 顺序）。
  于是一条「签名无效」的请求：Go **不产生任何 observability span**，RAG 会产生；
  3s 超时预算的包含范围也随之不同。

- **根因**：两种框架的「中间件」不是同一个概念 ——
  go-zero 的 `server.Use` 往 **engine chain** 追加（可排在 auth 内或外，由 append 顺序决定），
  Servlet 的 Filter **必然**在 DispatcherServlet 之前。
  **把 Go 的 chain 顺序翻译成 Spring 的 Filter/Interceptor 顺序时，「相对顺序」会静默改变**，
  而且不会有任何报错。

- **为什么测试抓不到**：单端断言（"超时是不是 3s"、"401 是不是 401"）两侧都对，
  只有**两个维度叠在一起**（顺序 × 超时/遥测）才分叉。

- **下次怎么防**：
  1. 凡涉及中间件顺序，**必须画两侧的执行序列并逐层对照**，不能只对齐「有哪些层」。
  2. 顺序类结论的证据要指向**构造点**（`engine.go` 的 append 行、`@Order` 的注解行），
     而不是「层清单」。
  3. 顺序差异优先问一句「**它影响业务判定吗**」：不影响（只影响遥测 / 超时边界）就降级为留档，
     不要伪装成待办 —— 否则台账会被「看起来像缺陷的等价差异」淹没。