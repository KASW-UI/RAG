# admin-api 踩坑台账

> 本文件只记**教训**：每个坑写成「现象 → 根因（框架 / 语言 / 认知机制）→ 为什么测试抓不到 → 下次怎么防」。
> 差异本体在 [`docs/DRIFT/users/admin/api.md`](../../../DRIFT/users/admin/api.md)（`DRIFT-ADMINAPI-0xx`）。
>
> **来源**：2026-10-23 admin-api 组件级 Go ↔ Spring Boot 语义验证（spec §31-b）。
> **证据强度**：本轮为 `静态源码·双侧`；文中凡称「已有测试」的均为 2026-10-21/22 建网、**本轮未复跑**。

---

> **📌 2026-10-29 台账对齐轮 · 编号与状态口径**（本轮新增；**只增不删**，旧内容一律保留）
>
> - **新号段**：`PIT-USERSADM-01 … PIT-USERSADM-17`（旧号 `P-ADMINAPI-001 … 017`）⇒ **逐条对照**写在每个条目首的「**旧号** ⇒ **新号**」行里。
> - **状态字段**：每个条目标题行尾以 `· **状态：X**` 标注（取值域：`仍生效` / `已闭环` / `已撤销` / `待核实`），
>   条目首的引用块给出 **核对日期（2026-10-29）** · **是否影响当前轮次**（填「否」时写明不适用的具体原因）· **证据**（`文件:行号` 或守门退出码）。
> - **判定口径**：`仍生效` ＝ 该坑描述的错误做法**今天仍可能发生**（触发条件仍存在）；
>   `已闭环` ＝ **本条记录的那个具体实例**已被消除且**有证据指针**；
>   `待核实` ＝ 判不了（**不许默认「仍生效」也不许默认「已闭环」**）。
> - ⚠️ **本轮只改台账**（`docs/**`），**未复跑**任何代码侧守门 ⇒ 「已闭环」类条目的证据指向**上一次实测**，本轮只做「该实例是否仍在」的存在性复核。

---

## PIT-USERSADM-01 · 断言「ctx 里的某个值是什么」必须把**写入侧**也读完 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-001` ⇒ **新号**：`PIT-USERSADM-01`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核 users/admin 域的鉴权与身份来源时直接适用
> **证据**：Go 侧 `service/user/common/jwt/jwt.go:8-11` 只签 `userId` 一个 claim、go-zero `rest/handler/authhandler.go:72-78` 把 **claim 名**写进 ctx ⇒ 「ctx 里有什么 = token 里有什么」不变 ⇒ 断言 ctx 值必须回读写入侧


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

## PIT-USERSADM-02 · 「更严格的校验」是最难自查的一类漂移，因为它写得太自然 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-002` ⇒ **新号**：`PIT-USERSADM-02`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核 admin-api 身份层时直接适用
> **证据**：`service/users/admin/admin-api/.../service/IdentityResolver.java:100-103`（`uid <= 0` 抛错）与 `:133-136`（`admin.getUid() != uid` 抛错）两道 **Go 侧没有**的检查仍在 ⇒ 「更严格的校验」这一类漂移仍会复现


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

## PIT-USERSADM-03 · 读 `json:",omitempty"` 时不能只看 tag 名：它对 struct 无效 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-003` ⇒ **新号**：`PIT-USERSADM-03`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核响应体字段存在性时直接适用
> **证据**：Go `service/user/admin/api/internal/types/types.go:72` 的 `json:"user,omitempty"` 不变（Go 的 omitempty 对 struct 永远不生效）；RAG 侧仍 `if (hasUser())` 才 set ⇒ 字段整体消失的形态仍在


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

## PIT-USERSADM-04 · `standaloneSetup` 测 advice，**测不到 Filter 与容器时序** —— 而本组件的测试全是 `MOCK` · **状态：仍生效**

> **旧号**：`P-ADMINAPI-004` ⇒ **新号**：`PIT-USERSADM-04`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮若给 admin-api 补守门时直接适用
> **证据**：admin-api 的 `@SpringBootTest` 仍是 `webEnvironment = MOCK`；真 Tomcat 证据靠 `AdminApiRealTomcatResponseBytesTest`（2026-10-28 新增）⇒ 「standaloneSetup / MOCK 测不到容器时序」的形态仍在


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

## PIT-USERSADM-05 · 「统一异常类型」会把业务码一起统一掉 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-005` ⇒ **新号**：`PIT-USERSADM-05`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核 admin-api 鉴权失败响应时直接适用
> **证据**：`service/users/admin/admin-api/.../advice/GlobalExceptionHandler.java:59-70` 仍把 `AuthenticationException` 统一成 401 + envelope（业务码取 `e.getCode()`）⇒ 「统一异常类型会把业务码一起统一掉」仍在


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

## PIT-USERSADM-06 · 台账里的**计数**（几个 client / 几个 Service）也要 grep 复核 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-006` ⇒ **新号**：`PIT-USERSADM-06`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮写本域台账里的「N 个 client / N 个 Service」类计数时直接适用
> **证据**：「注释和 yaml 只是声明、不是证据」不变（本条点名的 `grpc.client.user-rpc` 死配置段仍需重核是否已清）⇒ 资产计数仍须 `grep` 复核


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

## PIT-USERSADM-07 · 「401 空 body」这类**已签字**条目，签字范围要按**维度**拆开写 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-007` ⇒ **新号**：`PIT-USERSADM-07`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核签字档与新发现的归属时直接适用
> **证据**：本域签字现已**按维度拆开**（N-1 签 body 形状 · SD-2026-10-23-08 签黑名单业务码 · SD-2026-10-23-01 签 claim 校验 · N-4 签取值）⇒ 但「同一条按代码位置签 ⇒ 盖不住触发条件维度」的形态仍在，每条新发现仍须问「落在我已签字那条的哪个维度」


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

## PIT-USERSADM-08 · 「多一层设计」的代价要**在层边界**上记账，而不是在实现文件里 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-008` ⇒ **新号**：`PIT-USERSADM-08`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核中间件顺序类结论时直接适用
> **证据**：`service/common/.../spring/HttpMiddlewareFilter.java:41`（Servlet Filter，容器外）与 `JwtAuthInterceptor`（HandlerInterceptor，容器内）仍是**两层容器** ⇒ 相对顺序事实不变


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
---

## PIT-USERSADM-09 · 🔴 **最小上下文 ≠ 生产装配**：全局 client 拦截器会把 gRPC channel「包掉」 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-009` ⇒ **新号**：`PIT-USERSADM-09`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核 gRPC client 装配时直接适用
> **证据**：当前修法走 devh 的 `grpc.client.<name>.immediate-connect-timeout`（不摸 channel 具体类型）⇒ 但生产仍有全局 client 拦截器（`service/common/.../protection/GoStyleGrpcClientBreakerInterceptor`）⇒ 「最小上下文 ≠ 生产装配」的形态对新断言仍成立


> 本轮（2026-10-24）ADMINAPI-009 的 **v1 修法被真栈推翻**，代价是「admin-api 在任何环境下都起不来」。
> 这是本仓至今最贵的一次假绿，**根因不是写错了，是「测的装配 ≠ 生产的装配」**。

- **现象**：为对齐 Go 的 `zrpc.MustNewClient`（下游不可达 ⇒ panic ⇒ 进程起不来），
  v1 在 `AdminRpcClient.afterPropertiesSet()` 里读 `stub.getChannel()`，
  判 `instanceof ManagedChannel` 再 `getState(true)` 等 READY，3s 超时即抛。
  **单测 2/0/0/0 全绿**；**真栈里 admin-api 100% 起不来**：
  ```
  BeanCreationException: admin-rpc 的 gRPC channel 不是 ManagedChannel
    （实际类型 io.grpc.ClientInterceptors$InterceptorChannel）
  ```

- **根因（三层，逐层都得查到消费点）**：
  1. devh 的 `AbstractChannelFactory.createChannel` 最后一行是
     `ClientInterceptors.interceptForward(channel, interceptors)` ⇒ stub 拿到的是**包装后**的 channel；
  2. 包出来的是 `io.grpc.ClientInterceptors$InterceptorChannel extends io.grpc.Channel`，
     而 **grpc-api 1.63.0 的 `io.grpc.Channel` 只有 `newCall` 与 `authority` ——
     根本没有 `getState`**（`getState` 只在 `io.grpc.ManagedChannel` 上）⇒ 包装后**拿不到**连通性状态；
  3. 生产里**必然**有全局拦截器：`com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor`
     是 `@Component + @GrpcGlobalClientInterceptor`，admin-api 依赖 `service/common`
     ⇒ channel **一定**被包装 ⇒ `instanceof ManagedChannel` **永远为 false**。

- **为什么测试抓不到（关键）**：v1 的守门用 `ApplicationContextRunner` +
  **只引 `GrpcClientAutoConfiguration` 的最小上下文**，**没有装配那个全局拦截器**
  ⇒ 测出来的 channel 是**裸** `ManagedChannel` ⇒ `instanceof` 成立 ⇒ **绿灯是假的**。
  ⇒ **「最小上下文」验不了「依赖 channel 具体类型」的断言** —— 它验的是另一个装配。

- **下次怎么防**：
  1. 凡断言依赖**框架内部对象的具体类型**，先问一句：**生产上有没有全局拦截器/包装器？**
     有 ⇒ **别摸那个对象**，去用**框架自己持有的原始对象**。
     devh 的正解是 `grpc.client.<name>.immediate-connect-timeout`
     （`AbstractChannelFactory.newManagedChannel → connectOnStartup`，
     它作用在原始 `ManagedChannel` 上 ⇒ **天然免疫包装**）。
  2. **守门测试必须复刻生产装配**：至少把**全局 client 拦截器**装上
     （本轮 `AdminApiStartupFailFastTest` 加了 `WithGlobalInterceptorConfig`）。
  3. **fail-fast 类检查必须配一条正向用例**（「可达 ⇒ 能启动」）。
     v1 只有「不可达 ⇒ 失败」⇒ **一个「永远失败」的实现也能全绿**。
     这条正向用例正是本轮能立刻发现 v1 是错的唯一原因。
  4. **启动期语义一律用「真栈起一次」收口**，不要用「单测里上下文起得来」替代。

---

## PIT-USERSADM-10 · 「503 + 空 body」在 `service/common` 里有**三个来源** ⇒ 光看 503 归不了因 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-010` ⇒ **新号**：`PIT-USERSADM-10`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮核 503 归因时直接适用
> **证据**：`service/common` 三条「503 + 空 body」路径仍在（worker-pool 饱和 / `GoStyleCpuSheddingFilter` 自适应 CPU  shedding / `GoStyleMaxConnsFilter`）⇒ 「光看 503 归不了因」不变


- **现象**：300 并发压测 admin-api，观察到 **100 次「503 + 空 body」**
  ⇒ 直觉结论：「worker-pool 饱和分支触发了」。
  **实际不是**：关掉 CPU shedding 后，裸 503 变成 **0**。

- **根因**：`service/common` 里有**三条**能产出「503 + 空 body」的路径：

  | # | 路径 | 位置 | 默认生效条件 |
  |---|---|---|---|
  | ① | worker-pool 饱和（`RejectedExecutionException`） | `HttpMiddleware.java:296-315` | 池满（默认**不可达**，见下） |
  | ② | **CPU shedding**（Go `sheddinghandler` 对应物） | `GoStyleCpuSheddingFilter.java:94` | **默认开启**，threshold 900‰ |
  | ③ | max-conns（Go `maxconnshandler` 对应物） | `GoStyleMaxConnsFilter.java:114` | 默认 `maxConns=10000` |

- **为什么难归因**：CPU shedding 是**自适应**的（读**整机** CPU）
  ⇒ **裸 503 出不出、出多少，取决于压测当时机器有多忙**
  ⇒ 实测同一份代码、同一个并发数，两次跑出来的裸 503 数是 **100** 和 **0**。
  ⇒ **「裸 503 计数」不是稳定信号，不能用它判定池饱和。**

- **下次怎么防**：
  1. 判定「哪条分支」要**做对照实验**（关掉其中一个 → 差异即归因），
     **不要**靠「响应长什么样」猜。
  2. 判定「分支可达性」要量**机制本身的量**（池并发峰值 vs 上限），
     而不是量「症状出现了几次」。
  3. 顺带一条：**Tomcat 线程只增不复用** ⇒ 「exec 线程号上界」**不是**并发上限
     （实测见过 390）；正确口径是**同一时刻不同 exec 线程的个数**。

---

## PIT-USERSADM-11 · bash：`$VAR` 后面紧跟**全角标点**，全角字符会被并进变量名 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-011` ⇒ **新号**：`PIT-USERSADM-11`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本轮不改任何 `.sh` 脚本 ⇒ 触发面不落地
> **证据**：**本条实例已闭环**：本条点名的 `tools/difftest/run.sh` 两处已修（2026-10-29 实测 `\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]` 命中 = 0，原 `$a（` / `$j，` 已改 `${a}` / `${j}`）⇒ bash + UTF-8 locale 的通用形态仍在


- **现象**：脚本里 `echo "退出码 = $RC（期望 ...）"` 在 `set -u` 下直接
  `RC（: unbound variable` ⇒ 脚本在**最该给出结论的那一行**崩掉。

- **根因**：bash 的变量名只允许 `[A-Za-z_][A-Za-z0-9_]*`，
  但在 **UTF-8 locale** 下，多字节字符的**首字节**有时会被当作变量名的合法延续
  ⇒ `$RC（` 被解析成变量 `RC（`。
  ⇒ 只有当多字节字符**紧贴** `$VAR` 时才发生 ⇒ **`"$VAR "`（后面有空格）没事**，
  ⇒ 越是在中文注释/文案里随手写 `$VAR）`，越容易中招。

- **为什么 `bash -n` 抓不到**：语法检查只查语法，**查不到「变量未定义」**
  （`set -u` 是**运行期**才生效的）。

- **下次怎么防**：
  1. **凡 `$VAR` 后面紧跟中文/全角标点，一律写成 `${VAR}`**。
  2. 自检一行（本次用过的）：
     ```bash
     python3 - << 'PY'
     import pathlib, re
     pat = re.compile(r'\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]')
     for p in pathlib.Path("tools/difftest").glob("*.sh"):
         for i, line in enumerate(p.read_text().splitlines()):
             if pat.search(line):
                 print(f"{p}:{i+1}: {line}")
     PY
     ```
  3. ⚠️ 已知**存量命中**：`tools/difftest/run.sh` 的 `未知参数：$a（` 与 `缺少产物 $j，`
     —— **`align/task` 分支已修**（`${a}` / `${j}`）⇒ 合并时别重复改（会撞同一段）。

---

## PIT-USERSADM-12 · `docker exec` 不带 `-i` ⇒ **heredoc 里的 SQL 根本没进去**，而且**不报错** · **状态：仍生效**

> **旧号**：`P-ADMINAPI-012` ⇒ **新号**：`PIT-USERSADM-12`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本轮不写也不跑夹具复位脚本 ⇒ 触发面不落地
> **证据**：`docker exec` 默认不接 stdin ⇒ `psql <<SQL` 不进容器且 exit 0 的形态不变；仓内 `tools/difftest/reset-admin-fixtures.sh` 一类脚本仍是这一形态


- **现象**：写了个「复位 admin 夹具」的脚本（`tools/difftest/reset-admin-fixtures.sh`），
  跑完打印「✓ 密码已复位」，**数据库里的值却一个字没变**。

- **根因**：`docker exec` **默认不接 stdin** ⇒ `psql <<SQL ... SQL` 的**输入根本没进容器**
  ⇒ psql 读到 EOF 直接结束 ⇒ **exit 0** ⇒ `set -e` 也拦不住。

- **为什么难发现**：脚本的**自述输出**（"✓ 已复位"）与**实际效果**脱钩 ——
  这正是 AGENTS.md §3.3「『代码里写了』与『客户端收到』之间隔着一次 commit」的同款。

- **下次怎么防**：
  1. `docker exec` 要喂 stdin 就必须加 **`-i`**（`docker exec -i ... psql <<SQL`）。
  2. **凡是「我改了外部状态」的脚本，必须回读一次**再打印「✓」。
     本轮补的校验：
     ```bash
     docker exec difftest-postgres psql -U admin -d rag_db -tAc \
       "select username||' => '||left(password,20) from admin_admin;"
     ```
  3. 自查：脚本里凡有 `<<` heredoc 给 `docker exec`，逐个确认 `-i` 在不在。

---

## PIT-USERSADM-13 · 差分脚本的**夹具不是幂等的** ⇒ 第二轮跑出来的全是假 DIFF · **状态：仍生效**

> **旧号**：`P-ADMINAPI-013` ⇒ **新号**：`PIT-USERSADM-13`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本轮不跑 `diff.py --scenario admin` 差分 ⇒ 触发面不落地
> **证据**：`tools/difftest/diff.py` 的 admin 场景表仍含会改状态的动作（改密码 / 固定用户名 / 注销写黑名单）⇒ 夹具不幂等 ⇒ 第二轮全红是假 DIFF 的形态不变


- **现象**：`diff.py --scenario admin` 第一轮 22 步里 12 步逐字相同；
  第二轮**几乎全红**（Go 侧 `admin.login` 直接 1002「用户名或密码错误」）。

- **根因**：场景表里有两处**会改状态**的动作：
  ① `admin.update` 把 admin 的**密码**改成 `newadmin123`
  ⇒ 第二轮 `admin.login`（用 `admin/admin`）必然失败 ⇒ 后面 18 步被**连带**带成假 DIFF；
  ② `admin.create` 每次都用**固定**用户名 `dt_admin2` ⇒ 第二轮撞「用户名已存在」。
  ③ `admin.logout` 把 token 写进 **Redis 黑名单** ⇒ 只清数据库密码**不够**。

- **为什么难发现**：脚本**退出码是 1**，看起来像「测出真问题」；
  实际是**夹具没复位**。⇒ 「红」本身不携带信息量。

- **下次怎么防**：
  1. 差分脚本旁边**必须**配一个**夹具复位脚本**，且**复位完要回读校验**
     （见 P-ADMINAPI-012）。
  2. 本轮产出的 `tools/difftest/reset-admin-fixtures.sh` 复位三样：
     `go_db.admins` 密码、`rag_db.admin_admin` 密码、Redis `admin:jwt_blacklist:*`。
     （两侧都是 bcrypt：Go `service/user/common/cryptx/password.go:8`，
     RAG `PasswordUtil.encrypt` ⇒ 一份 bcrypt("admin") 两边都验得过。）
  3. **差分脚本要能区分「两侧不同」与「本场景在当前 scope 下不可判定」**：
     `{{user_uid}}` 这类模板在 `--scenario admin` 下**没被 capture** ⇒ 解析成空串
     ⇒ 两侧都 400 但错误文本来源不同（Go 走 form 参数的 `strconv.ParseInt`，
     RAG 走 body 的必填校验）⇒ 那**不是漂移**，是**前置缺失**。
     ⇒ 本轮给 `diff.py` 加了 `inconclusive` 分类：单列「不可判定」，**不计入 unexpected**
     （但也不计入通过）。**断言本身没被放松**：模板有值时它们照样走逐字比对
     （full scope 下这 5 步实测两侧逐字相同）。

---

## PIT-USERSADM-14 · 🔴 跑模块测试**不带 `-am`** ⇒ 依赖模块从**共享 `~/.m2`** 取 jar ⇒ 可能测的是**别的 worktree 的代码** · **状态：仍生效**

> **旧号**：`P-ADMINAPI-014` ⇒ **新号**：`PIT-USERSADM-14`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮跑 users 域模块测试即依赖该纪律
> **证据**：`service/users/users-common` 仍是独立模块 + 本仓并发多 worktree 共用同一个 `~/.m2` ⇒ 不带 `-am` 仍可能测到别的 worktree 的 jar


- **现象**：本轮跑 admin-rpc 测试，得到 4 条**红**：
  ```
  AdminRpcColumnTypeParityTest.adminColumnsMatchGoDdlTypes
    expected: <text> but was: <jsonb>
  AdminRpcColumnTypeParityTest.extraInfoAcceptsPayloadJsonbWouldReject
    INTERNAL: DB更新失败（22P02）
  AdminRpcColumnTypeParityTest.inviteExpiryJudgedOnAbsoluteInstantNotWallClock
    已过期的邀请码必须仍被拒绝 ⇒ nothing was thrown
  AdminCreatePasswordOrderRegressionTest.oversizedPasswordWithExpiredInviteYieldsInviteUnavailable
    IllegalArgumentException: password cannot be more than 72 bytes
  ```
  ⇒ 一看像「我上一轮的 DDL 改动把 admin-rpc 弄坏了」。
  **全是假的。**

- **根因（两层）**：
  1. 命令写成 `mvn -pl <module1>,<module2> test` —— **没有 `-am`**
     ⇒ Maven **不构建** 依赖模块 ⇒ `com.rag:users-common` 从 **`~/.m2` 的已安装 jar** 解析；
  2. **本仓是并发多 worktree 的**（§5.3「一域一 worktree」）⇒ **`~/.m2` 是所有 worktree 共享的**
     ⇒ 那个 jar 可能是**另一个分支的另一个 worker** 装进去的。
     实测：jar（04:14 构建）里的 `db/03_create_admin.sql` 是 **`extra_info JSONB`** 的**旧版**，
     而本工作树的源文件（00:12）早已改成 `TEXT`
     ⇒ ⇒ 测的是「**旧 DDL + 新代码**」的组合 ⇒ 报出来的失败**在本分支上根本不存在**。

- **为什么难发现**：
  - 失败**看起来非常合理**（正好命中本轮改过的 DDL 主题）；
  - `mvn test` **不会报错**，只是拿到一个「能跑但版本不对」的依赖；
  - **共享 `~/.m2` + 并发 worktree** ⇒ 同一命令在不同时间跑出**不同结果**，
    而且**与被测代码无关**。

- **下次怎么防**：
  1. **跑模块测试一律带 `-am`**（仓库自己的纪律，见 AGENTS.md §4.2：
     `mvn -pl <module> -am package`）—— 这不是风格问题，是**正确性前提**。
  2. 看到「与本轮改动主题高度相关的失败」时，**先怀疑依赖版本**，再怀疑实现：
     ```bash
     # 查依赖模块当前被解析到的是哪一份
     unzip -p ~/.m2/repository/com/rag/users-common/0.0.1-SNAPSHOT/users-common-0.0.1-SNAPSHOT.jar \
         db/03_create_admin.sql | head -60
     ```
  3. 提交前若要确保「装到 `~/.m2` 的就是本分支的」，先 `mvn -o -DskipTests install`
     —— 但**注意**：这会**污染其它并发 worktree 的 `~/.m2`** ⇒ 优先用 `-am`，
     `install` 只在确有必要时做，且做完提醒他人。

---

## PIT-USERSADM-15 · `baseline-on-migrate: true` + 默认 `baseline-version=1` ⇒ **V1 迁移被静默吞掉** · **状态：已闭环**

> **旧号**：`P-ADMINAPI-015` ⇒ **新号**：`PIT-USERSADM-15`
> **状态**：已闭环 · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 本轮不碰任何 Flyway 配置 ⇒ 触发面不落地
> **证据**：**实例已闭环**：启用 Flyway 的模块已显式钉 `baseline-version: 0`（如 `service/comment/comment-rpc/src/main/resources/application.yml:89-99`、`service/article/article-rpc/src/main/resources/application.yml:55-76`）⇒ V1 不再被 baseline 静默吞；⚠️ 残留风险：新增模块仍可能忘钉（本条形态即为此而写）


- **现象**：`difftest` 栈起来后，`assert_tables` 报 `rag_db.message_notification 不存在`
  ⇒ 但 message-rpc 的迁移文件 `V1__create_message_notification.sql` **明明在**，
  且内容是 `CREATE TABLE IF NOT EXISTS`（幂等）。

- **根因**：查 Flyway 账本一眼就看出来：
  ```
  flyway_schema_history_message:
    1 | << Flyway Baseline >>        | success=true   ← V1 从没执行
    2 | create message conversation  | success=true
    3 | create message conversation message | success=true
  ```
  `message-rpc/application.yml` 设了 `baseline-on-migrate: true`，
  而 **Flyway 的 `baseline-version` 默认是 1** ⇒
  当它启动时 schema **已经非空**（`user-rpc` / `admin-rpc` 的 `SchemaInitializer`
  先建了 `users_user` / `admin_admin`）⇒
  Flyway 判定「非空 schema + 无账本」⇒ **把版本 1 当作 baseline 记进账本**
  ⇒ **`V1` 永远不执行**，而 `V2`/`V3` 照跑 ⇒ **看起来「迁移跑了」，其实少跑了一个**。

- **为什么难发现**：
  - 账本里有 3 行、`success=true` ⇒ **没有任何报错**；
  - 失败的表是**业务表**，不是启动依赖 ⇒ 进程**照样起来**、端口**照样监听**；
  - 只在「别的模块先建过表」的多模块同库场景下才出现 ⇒ 单模块跑永远看不到。

- **下次怎么防**：
  1. 多模块共库时，`baseline-version` 要设 **0**（让 baseline 落在 0 ⇒ V1 照跑），
     或改用「幂等重放」而不是版本化迁移（本仓 users 域就是这么做的：`SchemaInitializer` 无账本重放）。
  2. **断言要打在「表真的在」上**（`assert_tables`），不能只看「迁移跑成功」——
     账本里的 `success=true` 证明不了该跑的都跑了。
  3. 看到「某个表不存在，但迁移文件在、账本也说成功」⇒ **先查账本里有没有 `<< Flyway Baseline >>`**。

---

## PIT-USERSADM-16 · 🔴 读框架源码推中间件顺序时，**漏掉 `Disable*` 开关** ⇒ 把「真实存在的差异」误判成「不成立」 · **状态：仍生效**

> **旧号**：`P-ADMINAPI-016` ⇒ **新号**：`PIT-USERSADM-16`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮读 Go 侧中间件链序时直接适用
> **证据**：Go 侧 `observability.DisableNativeRestTimeout(&c.RestConf)`（`service/user/admin/api/internal/admincenter.go:34` → `observability/http.go:32`）仍是个「签名看不出它改了配置」的开关 ⇒ 推链序仍须先 grep `Disable`/`With`/`Enable`


- **场景**：2026-10-24「台账 vs 实际」复核轮，要核实台账里
  `DRIFT-ADMINAPI-006 ②`（Go 的 3s 超时计时器从哪起算）。

- **初稿的推理**（**错**）：
  读 go-zero `rest/engine.go` 的 `buildChainWithNativeMiddlewares`，
  看到 native 中间件顺序是
  `Trace → Log → Prometheus → MaxConns → Breaker → Shedding → **Timeout** → Recover → Metrics`，
  而 `Authorize` 是在 `appendAuthHandler`（`engine.go:70-84`）里**之后** append 的
  ⇒ **`Timeout` 比 `Authorize` 更外层** ⇒ JWT 验签计入 3s 预算
  ⇒ 与 RAG 的最外层 Filter「一致」⇒ **这条漂移不成立**。

- **真相**：`TimeoutHandler` **根本不在链上**。
  `admincenter.go:34` 在建 server **之前**调了
  `observability.DisableNativeRestTimeout(&c.RestConf)`，
  它把 `conf.Middlewares.Timeout` 置 `false`（`observability/http.go:32`），
  而 `rest/engine.go:154-155` 的条件 `if ng.conf.Middlewares.Timeout` 因此为假 ⇒ native Timeout 被跳过。
  真正计时的是 `NewHTTPMiddleware`（`server.Use` ⇒ `ng.middlewares` ⇒ `engine.go:110-112` **最后** append
  ⇒ 比 `Authorize` **更内层**）⇒ **台账原文是对的，差异真实存在**。

- **为什么难发现**：
  - `DisableNativeRestTimeout` 是个**看起来像工具函数**的调用，返回一个 `time.Duration`，
    **读签名完全看不出它改了配置**；
  - 它改的是**结构体字段**（`conf.Middlewares.Timeout`），而 `rest.MustNewServer(c.RestConf)` 传的是**值拷贝**
    ⇒ 只有「先改后传」的顺序才生效 ⇒ 顺序错了也**不报错**，只是语义悄悄变了；
  - go-zero 源码里 native Timeout 的位置**客观上**就在 `Authorize` 外层 ——
    **不看那段关闭代码，推理过程「无懈可击」**。

- **最坏的后果不是这一条错，而是它会被写进台账**：
  台账的错误比代码的错误更难发现 —— 它**看起来有证据**（行号、函数名、顺序图都对），
  而且**会把一条真实存在的漂移注销掉**，让后续所有轮次都不再查它。
  ⇒ **复核轮最大的风险不是漏看，而是「复核出一个假的结论」**。

- **下次怎么防**：
  1. 推中间件顺序时，**先 grep 一遍 `Disable` / `With` / `Enable` 这类开关调用**，
     确认「框架自带的那条链」**有没有被摘掉**；
  2. 判「某框架机制是否生效」，**读消费该开关的那段控制流**，不是读声明
     （AGENTS.md §3.3.1 第 1 条）；
  3. 复核台账时，**先把「原文可能是对的」当成默认假设** ——
     复核的目的是找**陈旧**与**错误**，不是把所有结论翻新一遍。
     ⚠️ 本轮 81 条可证伪断言里，只有 **6 条**真的有问题（7.4%）；
     若复核结论是「大部分都反了」，那更可能是**复核方法本身有偏差**。

---

## PIT-USERSADM-17 · 🔴 「假订正」比「漏订正」更危险 —— 它会**静默覆盖一条本来正确的事实** · **状态：仍生效**

> **旧号**：`P-ADMINAPI-017` ⇒ **新号**：`PIT-USERSADM-17`
> **状态**：仍生效 · **核对日期**：2026-10-29 · **是否影响当前轮次**：是 —— 本轮改台账里的行号引用时直接适用
> **证据**：「范围类引用要先算包含关系再决定改不改」+「订正必须留原文」两条纪律不变 ⇒ 本轮所有订正一律保留原值并追加 2026-10-29 结论


- **场景**：2026-10-24 复核轮，台账 `rpc.md` 的 H-2 行写着
  「`.last("FOR UPDATE")`（`AdminCreateAtomicService.java:116-120`）」。

  复核时我看到实际代码：
  ```java
  116:  AdminInviteEntity invite = adminInviteMapper.selectOne(
  117:          new LambdaQueryWrapper<AdminInviteEntity>()
  118:                  .eq(AdminInviteEntity::getCode, inviteCode)
  119:                  .last("FOR UPDATE")
  120:          );
  ```
  ⇒ `.last("FOR UPDATE")` 在 **119** 行。
  ⇒ 我的判断：「台账写 `116-120`，但实际在 119 ⇒ 行号微偏 ⇒ 订正为 `120-123`」——
  ⚠️ **这一步是错的**：`120-123` **不包含** 119，
  而台账原文的 `116-120` **包含** 119 ⇒ **台账原文完全正确**。
  ⇒ 我把一条正确的引用改错了。

- **为什么难发现**：
  - 「实际行号 119」这个事实是**对的**，错的是**从它推出「原文有偏」那一步**；
  - 「范围」与「点」的比较很容易凭感觉做：看到 `116-120` 就觉得「应该是 119 那一行吧」，
    于是想改成更「精确」的 `119`，却写成了 `120-123`（**跨过了 119**）；
  - 改完之后台账**看起来更精确**，review 时反而更难怀疑。

- **与 P-ADMINAPI-016 的关系**：**同一个根因的两个面** ——
  - 016：**漏读** ⇒ 复核出一个**假结论**（把真实存在的差异判成「不成立」）；
  - 017：**误读** ⇒ 复核出一个**假订正**（把正确的原文改成错的）。
  ⇒ 共同根因：**复核时预设「台账总该有几条是错的」**。

- **最坏的后果**：
  台账的错误比代码的错误更难发现 ——
  「假订正」会让**一条本来正确的事实被静默改错**，
  且**没有留下任何痕迹**（读新台账的人只会看到「120-123」，看不出原文是 `116-120`），
  更糟的是**后续所有轮次都会拿这个错值当基准**。

- **下次怎么防**：
  1. **范围类引用要先算包含关系**，再决定要不要改：
     `116-120 ∋ 119` ⇒ **原文正确，不改**。
     ⇒ 规则：**「实际行号 ∈ 台账写的范围」⇒ 台账原文正确，禁止「订正」**。
  2. **订正必须留下原文**（本轮已在 `api.md` 的完整性复核轮 §4 留痕）——
     ⚠️ **不静默改掉**：新值旁边写清「原文是 X，错在 Y」，否则后人无法判断是「订正」还是「本来就该这么写」。
  3. **订正前先问一句**：「这条原文**真的**错了吗？」——
     默认答案是「对」；要推翻它，得给出**能证伪它的具体证据**（而不是「看起来不准」）。
  4. 同 P-ADMINAPI-016 第 3 条：**用命中率校准复核方法** ——
     81 条里只有 6 条真有问题（7.4%）⇒ 「我改了一堆」本身就是个**危险信号**。
