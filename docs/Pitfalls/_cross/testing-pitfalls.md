<!-- @move AGENTS.md L1755-L1769 · §3.3.3 元教训：把没看过的东西变成看过了 -->
**元教训（2026-10-22 同日）**：**「把没看过的东西变成看过了」本身就能挖出真 bug。**

这一条值得单独说，因为它的收益方向和直觉相反 ——
E-1 立项时只是「补 10 步的 body 比对」，看着是个机械活，
结果新断言一上线就红了，**当场挖出一条连续 4 轮对拍都没看见的真实漂移**。

⇒ **不要因为「把待办清干净」而降低验证强度。**
一个长期挂着「已知无法比对」的条目，往往不是技术债，
而是**当年就没想清楚该比对什么**的信号。
本次的做法可复用：
1. 先问「这条为什么**注定**不同」—— 把不可比的理由**写成断言**（Go 侧必须命中它自己的形态之一），
   而不是用「跳过」代替；
2. 为了断言而**扩到此前从未采集的数据**（这次是响应头），往往就撞出新缺陷；
3. 撞到之后**别急着改断言去迁就现状**——先确认是现状错了还是断言错了。
   本次正是靠「MockMvc vs 真实 Tomcat 的差异」才判定的。
<!-- @move AGENTS.md L1773-L1785 · §3.3.3 H2 案例：断言「Bean 存在」≠ 断言「机制生效」 -->
`ObservabilityWiringTest#grpcInterceptorBeanExists` 长期为绿，因为它断言的是
`ctx.getBean(ServerInterceptor.class)` 非空 —— 而真实缺陷是
**grpc-spring-boot-starter 3.1.0 根本不会把用户提供的 `ServerInterceptor` Bean 挂到 server 上**
（`javap` 证据见 §6.4 勘误小节）。Bean 在容器里、starter 不用它，两件事都对，机制照样是死的。

⇒ 凡依赖**框架自动注册**的机制（starter 自动装配、`AutoConfiguration.imports`、拦截器链），
测试必须断言二者之一：
- **运行时的可观测后果**（最强）：本例即「客户端是否 ≈2s 返回 1015」，由
  `tools/difftest/exp-b1-timeout-cancel.sh` 端到端覆盖；
- **对象确实进入了框架的接管点**（次强）：如 `verify(serverBuilder).intercept(interceptor)`，
  由 `GrpcInterceptorWiringTest` 锁住。

锁死用例：`user-rpc` 的 `GrpcInterceptorWiringTest`（2 条）。
---

<!-- @move AGENTS.md §3.3.3 · `,string` 漂移：绿测试给「错误答案」发通行证 -->

## 🔴 绿测试可能是「错误答案的守卫」，比没有测试更危险

**起因**：`service/message/message-api` 首轮实现把 Go 的 `,string` int64 请求字段做成了
「**只接受 JSON 字符串**」，而 Go 侧 `json.Number` 的 `reflect.Kind()` 是 `string`
（`type Number string`），所以 Go **同时接受字符串与数字**。
本项目这边两处都「有据可依」地写错了：

1. DTO javadoc 断言「`json.Number` 的 Kind 是 number」—— 事实错误（有完整推导，不是笔误）；
2. `MessageApiMvcPipelineTest#int64AsJsonNumberIsRejected` 把 **RAG 的实际输出**断言成了
   「与 Go 一致」—— 这条测试会**主动阻止**正确修复。

⇒ **测试断言的是「RAG 现在的行为」，不是「Go 的行为」。**
当测试名 / `@DisplayName` 里出现「对齐 Go」「与 Go 一致」，而断言写的是 RAG 的输出时，
它要么是**巧合**，要么是**在给未验证的假设发通行证**。

**危害的方向和直觉相反**：不是「测试没抓到 bug」，而是**修复时会先撞上这两条红测试**，
且测试名会让人误判「修复」才是漂移方向。⇒ 修复漂移时**先改/删错误断言，再改实现**。
