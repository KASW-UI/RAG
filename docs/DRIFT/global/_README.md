<!-- @new 2026-10-25 · global 域（跨域 + common 尾巴收口）· 由用户定义 -->

# global 台账 —— 跨域漂移与 `service/common` 尾巴的**收口域**

> **核对日期：2026-10-25**（创建）
>
> **本文回答一个别处回答不了的问题：还有哪些漂移没修？为什么没修？**

---

## 〇、这个域是什么（2026-10-25 用户定义）

> **其他域暂时都对齐了（单微服务域），但是有很多内容是跨微服务、
> 或者是因为 `service/common` 影响的，这些漂移被搁置。**
> **`global` 的作用就是解决这些。**

⇒ **`global` 不是第 11 个微服务域**，它是一个**收口域**：
- **不是**「某个微服务的组件」——它没有 `api` / `rpc` / `proto` 组件
- **是**「所有修法落在共享层、或影响多个域的漂移」的**唯一合法落点**

### 与既有档案的关系

| 档案 | 记什么 | `global` 的关系 |
|---|---|---|
| `docs/DRIFT/<域>/<组件>.md` | 单域单组件的漂移 | ✅ 已由各域收口完毕 |
| `docs/DRIFT/common/` | `service/common` 自身的漂移 | ⚠️ **部分重叠** —— 边界见 §二 |
| `docs/DRIFT/_cross/` | 跨模块档案（不变量 / 决策 / 快照） | ⚠️ **重叠** —— 本域是 `_cross` 的**活的部分** |

⇒ **落点规则**：
- 修法落在 `service/common/**` ⇒ `docs/DRIFT/global/<组件>.md`
- 修法落在**某个域的代码**、但影响多域 ⇒ 仍记在**那个域**的台账里，本域只做**索引 + 协调**
- 纯粹的**跨域不变量**（涉及 3+ 域、无法归给任何单一域）⇒ `docs/DRIFT/global/_cross.md`

---

## 一、为什么这些漂移之前修不了（三条硬阻塞）

### 1.1 单域纪律的直接后果

§5.3 规定「一个域一个工作树，只写自己域的台账」。
⇒ **修法一旦落在 `service/common`，那条漂移就从「本域的并发任务」降级成「共享写点」**
⇒ 别的域正在写同一批文件 ⇒ 并发对齐在这一点上**必然排队**。

> 出处 ⇒ [`common/alignment-2026-10-23.md` §十二·二](../common/alignment-2026-10-23.md)：
> 「**common 域是唯一的共享写点** —— 别的域的未修项只要修法落在 `service/common`，
> 就**自动从并发任务降级为串行任务**（§5.3 单域串行）⇒ **这才是 10 域并发对齐吞吐被卡住的那一层**」

### 1.2 「单改会制造不一致」

多域共性差异，**单改一个域反而制造新的不一致**：

| 条目 | 域 | 共性范围 | 处置 |
|---|---|---|---|
| `DRIFT-TASK-API-004`（`Content-Type` 的 charset/空白格式） | task | **五域共性** | ❌ 不在本域单独修 |
| yaml 三类「死配置」（`spring.mybatis-plus.*` / `otel.*` / `spring.jpa.*`） | task | **8 / 20 个 rpc 模块同款** | ❌ 不在本域单方面修 |
| `otel.*` 的跨域接线（Boot 4 OTel 自动配置 + 全仓 20 个 yaml） | task | 共享构建基建 | ❌ 须与其它域协调 |

### 1.3 破坏性变更需要跨域同意

`DRIFT-COMMON-005`（`bindToCurrent` 签名是否允许破坏性变更）
⇒ 跨域（common + admin-rpc + user-rpc）⇒ **唯一被反复确认仍未决的项**。

---

## 二、`global` 与 `docs/DRIFT/common/` 的边界

⚠️ **两者都会碰 `service/common`，必须分清，否则重复劳动**：

| | `common` 域台账 | `global` 域台账 |
|---|---|---|
| **对象** | `service/common` **自身**的语义漂移 | **别的域**的漂移，但**修法落在** `service/common` |
| **例子** | `DRIFT-CF-001`（communityfact）、`DRIFT-LOG-001`（logger）、`DRIFT-SF-001`（snowflake） | `T-1`（`GoStyleJsonBodyConverter` 的 Content-Type 大小写判定）——**`like` 域的条目，但落点在 common**；<br>⚠️ `T-2` / `T-5`（`GoStyleMaxConnsFilter` 的 MaxConns 作用域）**已于 2026-10-25 签字** ⇒ 归 `docs/Intentional_Deviations/global/` ⇒ 不再是本档的例子 |
| **判据** | 差异在 common 的**行为**本身 | 差异在**别的域的行为**，common 只是**载体** |

⇒ **一句话**：common 域问「common 自己对不对」，global 域问「**别人**因为 common 而对不对」。

---

## 三、待收口清单（从 `common/alignment-2026-10-23.md` §十二·二 转入）

> **层二：common 是唯一的共享写点 —— 外部域的尾巴清单（10 条）**
> ⚠️ **这 10 条不属于 common 域的漂移，但修法落在 `service/common` 的共享类里**
> ⇒ 改一处影响 6–10 个域 ⇒ 按 §5.3 必须单域串行 ⇒ **并发对齐在这一点上必然排队**。

| # | 域 | 条目 | 落点（`文件:行`） | 差异 | 状态（2026-10-25 实测） |
|---|---|---|---|---|---|
| **T-1** | `like` | `DRIFT-LIKEAPI-015` | `common/http/GoStyleJsonBodyConverter.java`（原 `:115-117` 用 `getContentType().toString()` ⇒ MediaType 已小写化）vs `goReadsBody` `:98-101`（`contains` 大小写敏感） | Content-Type 大小写敏感判定不同 | ✅ **2026-10-25 已修**（改取原始头值 `getFirst(CONTENT_TYPE)`）⇒ 残余 ① 已登记 |
| **T-2** | `follow` | `DRIFT-FOLLOW-008` | `common/protection/GoStyleMaxConnsFilter.java` | Go `MaxConnsHandler` **每路由一个** latch，RAG **全局一个** | ✅ **已签字**（2026-10-25）⇒ `SD-2026-10-25-01` ⇒ **已移出本档** |
| **T-3** | `hot` | `D14` / `DRIFT-HOTAPI-014` | `common/observability/HttpMiddleware.java:431`（`writeTimeoutResponse`） | 503 / 499 响应缺 `X-Content-Type-Options: nosniff` | ✅ 已修 —— **2026-10-25 实测确认在位**，common 侧无需再动 |
| **T-4** | `hot` | `D25` / `DRIFT-HOTAPI-025` | `common/observability/HttpMiddleware.java`（`flushCaptured`） | 客户端断连后 RAG 等 2s deadline、断连后继续发下游调用、无 499 语义 | ✅ **观测面 + 资源面均已修**（499 可见；**在飞 gRPC 会被提前取消**，实测 461ms）· 🟡 残留**时序差** ⇒ 新签字 `SD-2026-10-25-04`（wire 偏离：客户端先收到 N 行 `103 Early Hints`）；⚠️ 原 `SD-2026-10-25-02` 已**撤销**（问题被修掉）⚠️ **分类已改判**（第八轮）：原「框架限制」被证伪 ⇒ 准确表述 = 「**在『Tomcat + HTTP/1.1 + 同步 Servlet + 保 `Content-Length`』这组约束下没有 Go 等价路径**」⇒ 且**这组约束是项目自己选的** ⇒ 本条是**决策项**不是事实登记；6 条替代路径已全部查证（详见签字档 §①~④）<br>🟡 **2026-10-25 06:40 用户再次明确签字**（「接受现有的 CAND-12 处理」+「签字接受 socket 的偏离」）⇒ **本条闭环，不再另选方案**；并把 **Spring 官方路径记录为「可选后续优化」（非必选）** ⇒ **不触发返工** ⇒ 见签字档 §「`SD-2026-10-25-04` 附记 · 官方路径记录」 |
| **T-5** | `hot` | `D27` | `common/protection/GoStyleMaxConnsFilter.java` | 同 T-2（MaxConns 作用域） | ✅ **已签字**（同 T-2）⇒ **已移出本档** |
| **T-6** | `message` | `-010` | **`common` 的 Spring JSON converter**（原标的 `GoStyleRouterErrorResponses.java:83` **有误**——该文件只管 404/405） | 成功 / 失败响应的 `Content-Type` 差异（Go `application/json; charset=utf-8` / RAG `application/json`） | ✅ **归因已定位 · 可 Go 等价修** —— 🟡 **被 hot 域 `SD-HOT-API-006` 挡住**（其「不可逐字节复刻」前提已被本仓证伪）⇒ 需先撤销该签字 |
| **T-7** | `message` | `-011` | **Tomcat `Http11OutputBuffer`（容器层）** | 状态行 reason phrase 差异 | 🔴 **框架限制（真）** —— `service/common` 够不着 ⇒ 只能签字认账；**「归因尚未定位」已作废** |
| **T-8** | `message` | `-013` | **`common` 的 HTTP 入口**（Go = go-zero `rest/handler/tracehandler.go` 的 `propagator.Inject`） | 缺 `Traceparent` | ✅ **第十九轮已落地**（共享层新增 `GoStyleTraceparentFilter` ⇒ 10 个 api 全生效）· 🟢 守门 6 条 + 变异 5 条全红<br>🔴 **残留 T-12**：「方法不匹配」出口**多带**一个 `Traceparent` |
| **T-9** | `comment` | `D3` | `common/protection/GoStyleGrpcClientBreakerInterceptor.java`（`goBreakerName(targetOf(next), fullMethodName)`） | Go `path.Join(cc.Target(), method)` ⇒ 单 client 的熔断计数**串了** | ✅ **已修**（2026-10-25 第三轮）—— 身份源 = `Channel.authority()`；§3.2 缺陷一并订正 |
| **T-1 残余 ①** | — | — | 同 T-1 落点（`SanitizedJsonContentTypeInputMessage`） | 判定已修，但**解析层**仍走 MediaType 解析 ⇒ 畸形头仍抛（Go 不抛） | ✅ **已修**（2026-10-25 第三轮）⇒ 顺带修掉 `charset=gbk` 偏差 |
| **T-10** | `users` | 交接 **#2** | `service/users/user/user-rpc/.../UserDomainService.java:113`（javadoc 仍写「由 `RpcInterceptor` 调度」） | 声称 deadline 由 `DeadlineState#markExpired(long)` 设置，实际由 `bindToCurrent` 设置 ⇒ **javadoc 假断言** | ✅ **2026-10-25 已闭环**（两步走完 + 真管线守门 + 变异验证）⇒ 详见 [`cycle-deps.md`](cycle-deps.md) |

---

## 四、可**两步闭环**的循环依赖（本次扫描的新发现）

> ✅ **CYCLE-001 已于 2026-10-25 闭环**（两步同一次提交走完 + 真 in-process gRPC 守门 + 变异验证）
> ⇒ **逐条记录 ⇒ [`cycle-deps.md`](cycle-deps.md)**

```
service/common/.../RpcInterceptor.java:617（改前）   markExpired(long)  ← 标 @Deprecated，保留不删
        │  保留的唯一理由（RpcInterceptor.java:603-611（改前）的 javadoc 明写）：
        │    「service/users/user/user-rpc 的 UserDomainService javadoc 仍以 markExpired(long)
        │      描述 deadline 的来源（该描述本身也不准确……）…… 删除本方法会让那些域的 javadoc 链接悬空，
        │      而跨域文件的改动不由本域决定」
        ↓
service/users/user/user-rpc/.../UserDomainService.java:113（改前）   ← 这行 javadoc 本身就是错的
        （真相：deadline 由 onHalfClose 的 bindToCurrent 设置 ⇒ RpcInterceptor.java:282 / :373）
```

⇒ **这是个真循环**：A 保留 deprecated 方法是为了 B 的 javadoc 链接不断；
B 的 javadoc 修好了，A 就可以删方法。**谁先动都会留半截** ⇒ 必须**同一次提交**里两步走完。

⇒ 🔴 **这正是 `global` 存在的第一个具体理由** —— 它就是那条「能同时动 `service/common`
与 `service/users/**`」的合法落点。

---

## 五、跨域不变量（涉及 3+ 域，无法归给任何单一域）

| 不变量 | 涉及组件 | 状态（2026-10-25） |
|---|---|---|
| 共享 Flyway 账本 | `message-rpc`↔`like-rpc`↔`article-rpc` | ✅ **已闭环** —— **第二十四轮 L4 真 PG 16 实测**：六个域（article / comment / follow / like / message / task）**可安全并发同库**（三本账本互不覆盖、Flyway advisory lock **按账本表名**算 key ⇒ 互不阻塞）⇒ 🔴 **favorite 不行**（缺 `spring.flyway.baseline-version: 0` ⇒ 故障形态是「**起得来、首次查库才炸**」）⇒ **只记账不修**（favorite 零写权）⇒ 工具 `tools/difftest/check-flyway-concurrent.sh` |
| 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | ✅ **已审完**（第九轮）—— 交叉但无冲突 · client 契约 **10 域零漂移** ⇒ [`cross-domain.md`](cross-domain.md) §二·九 |
| 事实投递链路 | `like`/`comment`/`favorite` 的 rpc ↔ **仓外 DC 平台** | ✅ **已闭合**（B2）—— 末端 = 仓外 `POST /v1/events`；仓内两侧 4↔4 对齐 ⇒ [`cross-domain.md`](cross-domain.md) §二·七 |
| 社区消费者差异 | Go 用 `like`，RAG 不用 | ✅ **已确证 + like 半边已落地**（**第二十轮**：like 事实投递链路 + 三套守门 22 条 + 变异 14/14）⇒ ⛔ **favorite 半边永久阻塞**（必须删 favorite 自己的 10 处调用） |
| 裸 `ServerCallHandler` 不被 dispatch | **task + follow + user-rpc + message-rpc + hot-rpc** | ✅ **已修**（**第十七轮** task + follow 落地 ⇒ latch 取代 sleep + 补「handler 被调用过」断言）⇒ 🟢 **第二十四轮 L2 扩面**：10 个 rpc 组件里**只有 5 个**（user / message / hot / task / follow）有真 server 守门 ⇒ user-rpc / message-rpc **缺测试**（纯 Mockito）、hot-rpc **守门不严**（变异仍全绿）⇒ 三处**已补真 server 守门 + 加固** ⇒ 变异 8/8 全红 ⇒ ⛔ admin / article / comment / like / favorite **仍只有纯 mock**（登记在 L2 台账） |
| `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | 🟡 **未修 · 可修（共享层）**（总数 ≥2048 且非空值 ≤2048 ⇒ Go 200 / RAG 5001）—— ⚠️ **2026-10-25 第十一轮推翻了自己的「favorite 边界下永久阻塞」结论**：上限可在运行期由 `service/common` 改掉（`Connector.setMaxParameterCount(-1)`）⇒ **10 个 api 模块零改动 ⇒ favorite 零触碰** ⇒ 修法落 `service/common`（global 单写权内）⇒ **不是拍板项、不需签字** ⇒ 见 [`cross-domain.md`](cross-domain.md) §二·十一 |
| DDL 双侧活体逐列对照 | `like` 域 + 其余 5 域 | ✅ **已闭合（第二十四轮 L3）** —— **真 PG 16 两侧各建真库**逐列对照（类型/长度/可空性/DEFAULT/索引/约束/触发器）：article **0 差异**（56 列）· follow **0 差异**（10 列）· like / favorite 的 7 处差异**全部是「RAG 更严」**（多 CHECK / 多触发器 / 多部分索引；根因 = Go 的 `CREATE TABLE IF NOT EXISTS` 在 AutoMigrate 之后是**空操作**）⇒ 已登记为**待签字候选** ⇒ ⚠️ **hot 域两侧都无数据库 ⇒ N/A（不是「已对齐」）** ⇒ 工具 `tools/difftest/ddl-parity.sh` |

---

## 六、仍未决的 4 条 + 未销账的 2 条

> 来源：[`common/alignment-2026-10-23.md` §十二·三](../common/alignment-2026-10-23.md)「9 项交接的真实状态（逐条实测，2026-10-25）」
> ⚠️ 该节更正了 AGENTS.md §0 的「**8 项未决交接**」—— 那个数把 4 条「已销账但没记」也算成了未决。

| # | 事项 | 2026-10-25 实测 | 判定 |
|---|---|---|---|
| 2 | `UserDomainService` javadoc 假断言 | `UserDomainService.java:113` **原样还在** | ✅ **已销账**（2026-10-25，两步走完 ⇒ 见 [`cycle-deps.md`](cycle-deps.md)） |
| 3 | `DRIFT-COMMON-005` 定案（`bindToCurrent` 签名是否允许破坏性变更） | 跨域（common + admin-rpc + user-rpc）的破坏性变更 | ⏸ **仍未决**（唯一被反复确认的未决项） |
| 4 | `like` 的 `SD-2026-10-23-09` 描述订正 | 🟡 **补丁已由 L5 出**（订正内容：`GoStyleRequestDeadline.applyTo` **是纳秒的** ⇒ 真实理由是「**只读** API 只有毫秒粒度」）⇒ **待整合者 fold** |
| 8 | `docs/Pitfalls/common/_signed-deviations.md` 疑似错放 | ✅ **2026-10-25 已销账**：确认是 **0 字节空文件**（真名在 `docs/Intentional_Deviations/common/_signed-deviations.md`）⇒ **直接删除**（0 字节 ⇒ 删掉不可能丢内容） | ✅ **已销账** |
| 9 | 社区消费者：Go 用 `like`，RAG 不用 | ✅ **已销账**（**第二十轮**：like 半边事实投递链路落地 + 三套守门 22 条 + 变异 14/14）⇒ ⛔ **favorite 半边永久阻塞**（零写权 ⇒ 只记账） |
| 10 | 裸 `ServerCallHandler` 不被 dispatch（task 域） | ✅ **2026-10-25 第七轮已确证**（探针实测 ⇒ grpc-java 不向裸 handler 派发 `onHalfClose` ⇒ task + follow 两域的测试空转）⇒ **未知项消解，转为待修** |

⇒ **真实分布（2026-10-25 第二十四轮更新）**：已销账 **8 条**（#1/#5/#6/#7/#11/#2/**#8**/**#9**）· **未销账 0 条**· **仍未决 2 条**（#3/**#4**，其中 #4 的补丁已由 L5 出）—— #10 已由第七轮**确证**（机制 + 波及面）⇒ 从「未知」转为「待修」⇒ **第二十四轮 L2 已把 user-rpc / message-rpc / hot-rpc 三处补齐**。

⇒ **第三轮（2026-10-25）新增归位**：**T-4 的资源面残留**（「断连后在飞 gRPC 不被提前取消」）
经真 Tomcat 11.0.24 实测 + Tomcat 源码取证 ⇒ **Tomcat 的 Servlet 层不向应用暴露客户端断连信号**
⇒ 归位为 **`SD-2026-10-25-02`**
⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../../Intentional_Deviations/global/common-tails.md)。

> ⚠️ **2026-10-25 05:46 订正 —— 本条退回「待签字」**：用户要求「**先等下签字，我想知道有没有其他办法**」
> ⇒ ① 原判「**框架限制、不可修**⇒ 不存在决策空间」**过宽** ⇒ 实测只覆盖「**Tomcat + HTTP/1.1 + Spring MVC**」
> 这一种组合，**不证明「Java/Spring 生态做不到」** ⇒ **降级为「Tomcat 的 Servlet 层不给断连信号」**；
> ② 已登记 **6 条待查技术路径**（换容器 Jetty/Undertow · 换栈 WebFlux+Netty · 反射取 Tomcat 内部 socket 状态
> · HTTP/2 的 `RST_STREAM` 路径 · 主动探测写 · 缩短在飞 deadline）⇒ **研究结论回来前不签字**。
⇒ ⚠️ 其中 **#3（`DRIFT-COMMON-005`：`bindToCurrent` 签名是否允许破坏性变更）** 仍是**唯一被反复确认的未决项**
⇒ 本域连续两轮**均未动** `bindToCurrent` 的签名 ⇒ 不与之冲突。

> ✅ **2026-10-25 第十轮 + 第十三轮 最终定案（上面两段是历史过程，不是现状）**：
> ① **第六轮把 T-4 资源面归位为 `SD-2026-10-25-02`**（判「框架限制」）⇒ **第八轮推翻该定性**（6 条替代路径全查完）
> ⇒ **第十轮用户拍板方案⑤「主动探测写」并落地** ⇒ **T-4 资源面已修**（在飞 gRPC 会被提前取消，实测 461ms）
> ⇒ ❌ **`SD-2026-10-25-02` 已撤销**（问题被修掉 ⇒ **从未签字，也不需再签**）
> ⇒ ✅ **最终记录 = `SD-2026-10-25-04`**（wire 偏离：客户端先收到 N 行 `103 Early Hints` + 499 定型时点晚于 Go）
> ⇒ 🔴 **2026-10-25 06:40 用户再次明确签字**（「接受现有的 CAND-12 处理」+「签字接受 socket 的偏离」）⇒ **本条闭环，不再另选方案**
> ⇒ 🟡 **Spring 官方路径已记录为「可选后续优化」（非必选）** ⇒ **不触发返工**
> ⇒ ⇒ 逐条 ⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../../Intentional_Deviations/global/common-tails.md)（§`SD-2026-10-25-04` + 其「附记 · 官方路径记录」）。

---

## 七、开工前的纪律

1. **本域是 `service/common/**` 的唯一合法写点** —— 别的域只读（§二 边界表）。
1b. ⚠️ **写权扩展（2026-10-25 用户授权 + 同日重申）** —— 用户明确：「**你有对除了 favorite 外其他所有代码的权限**」
   ⇒ ⇒ `global` **可以**改**除 `favorite` 外**任何域的代码与文档
   ⇒ ⚠️ **但 `favorite` 一律不碰**（用户原话：「global 里的微服务只有 favorite 不是」）
   ⇒ 🔴 **2026-10-25 用户再次重申：「你没有 favorite 的所有权限」** ⇒ 口径收紧为：
   **favorite 域的代码、配置、测试、台账 —— 一律零改动**（不是「代码可改但别乱动」）。
   ⇒ ⚠️ **但「碰不到 favorite 的文件」≠「碰不到 favorite 的行为」** ——
   **`service/common` 是共享层，它改了就影响所有域（含 favorite）** ⇒ 这是**合法**的（共享层就是干这个的），
   但**必须记账并告知该域 owner** ⇒ 不能静默改别人的可观察行为。
   ⇒ 📌 **由此推出一条纪律**（⇒ [`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-012](../../../Pitfalls/global/cross-domain.md)）：
   **「修法 A 被边界挡住」≠「这条漂移不可修」** ⇒ 判「不可修」之前必须先查**共享层路径**。
   ⇒ ⚠️ **单写权仍然成立且方向相反**：`service/common/**` **只有 `align/global` 能写**，别的域只读
   ⇒ ⇒ 因此**跨域改动**（同时动 common + 别的域）**必须同一次提交**（§五 规则 2）。
2. **凡要改 `service/common` + 别的域的代码 ⇒ 同一次提交**（§四 那个循环依赖的教训）。
3. **每条收口都必须配真 PG / 真启动的守门测试**（§3.3）—— 纯 mock 单测不算。
4. **台账写入边界**（§5.3 3b）：本域只写 `docs/DRIFT/global/` + `docs/Pitfalls/global/` +
   `docs/Intentional_Deviations/global/`；三个 `_INDEX.md` 合并后统一更新。
5. ⚠️ **跨域改动前先 `git fetch upstream` 确认基线** —— `service/common` 是共享面，
   别的域的并发会话可能同时在改（§3.3 的「共享文件改动要尽早提交」）。
6. 🆕 **多 lane 并行（2026-10-25）** —— 本域待办横跨 6 个微服务域 + 共享层 + `tools/` + 三档台账，
   单会话串行每轮要重读 **221 KB** 台账 ⇒ 上下文才是瓶颈。
   ⇒ 拆成 **5 条 lane**（`align/global-delivery` / `-rpcgate` / `-ddl` / `-flyway` / `-signoff`），
   **按「叶子文件」切**（议题会跨文件，文件才是不冲突的单位）⇒ **文件级零重叠**。
   ⇒ **协议 + 5 份任务书 ⇒ [`lanes/_PROTOCOL.md`](lanes/_PROTOCOL.md)**
   （lane 基线 = `align/global` HEAD；lane 不写 4 个规范台账文件与 `AGENTS.md` ⇒ 共享文件由整合者统一更新）。

---

## 七之二、📋 **剩余可修漂移 / 未知项盘点**（2026-10-25 06:40）

> **口径**：`global` 收口域（**`favorite` 域零改动**）内**尚未闭环**的全部条目，按「能不能直接动手」分四类。
> ⇒ **登记类（待签字）不升级为拍板**（用户既定偏好）⇒ 真正需要用户拍板的只有 **D 类 2 条**。

### A · ✅ **可直接修**（落点在 global 单写权范围内 / 非 favorite 域 · **不需拍板**）

| # | 条目 | 落点 | 修法 | 状态 |
|---|---|---|---|---|
| **A1** | **X-6** / `DRIFT-HOTAPI-030` | `service/common` × 6 个新件 + **9 个域各一行 yaml** | 🔴 **第十四轮落地时推翻了第十三轮修法草案的一个前提** —— Go 的 `httpx.SetErrorHandler` 是**逐 api 注册**的 ⇒ 解析失败的 wire 形状**按域而异**（注册了 ⇒ **200 + 业务信封**；没注册 ⇒ **400 + Go 原文**）且**信封文案在域自己的错误码表**里 ⇒ 共享层**写不出信封** ⇒ 落地为：① `GoStyleFormValues`（只出 Go 判据）② `GoStyleFormLimitMode`（off/raw/envelope，非法值炸）③ `GoStyleFormLimitExceededException`（刻意不继承 `BusinessException`）④ `GoStyleFormValueLimitInterceptor`（**必须是 Interceptor**：servlet Filter 抛的异常到不了 advice）⑤ `GoStyleFormLimitWebMvcConfig`（注册）⑥ `GoStyleTomcatParameterLimitCustomizer`（上限 → **10001**，**仅非 off 模式生效**）⇒ 每域一行 `rag.http.go-form-limit.mode: raw\|envelope` ⇒ **favorite 缺省 = off ⇒ 容器上限也不动 ⇒ 零触碰** | ✅ **已落地**（第十四轮）· 🟢 **真容器 + 真 MVC + 真 advice + 变异测试** |
| **A2** | **T-8** / `DRIFT-MSGAPI-013`（缺 `Traceparent`） | `service/common` 的 **HTTP 入口** | 注入 W3C `traceparent`（Go = go-zero `rest/handler/tracehandler.go` 的 `propagator.Inject`） | ✅ **第十九轮已落地** —— 🔴 根因不是「忘了写一行」，而是 **RAG 侧压根没有 server span**（无 `micrometer-tracing-bridge-otel` / 无 otel 自动配置 ⇒ `GlobalOpenTelemetry.get()` 是 no-op ⇒ `SpanContext` 恒 INVALID）⇒ 新增 `GoStyleTraceparentFilter`（**私有** `SdkTracerProvider`，**不碰全局**）⇒ **10 个 api 全生效、零 api 代码改动** ⇒ 🟢 **真 Tomcat + 裸 socket 守门 6 条 + 变异 3 条全红**（含「不是回显入向」「头名 canonical」）⇒ ⚠️ **同时新登记 T-11**：trace **导出**链路在 RAG 侧整体缺失（**本轮只登记、未修**）<br>⇒ 🔴 **第二轮：链上位置定案**（9 个 filter 的 `@Order` 整体后移，**相对顺序不变**）—— CORS 必须在 traceparent **之前**（Go 的 `cors.Middleware` 对 OPTIONS 短路 ⇒ 预检不带头）/ MaxConns·Shedding 必须在它**之后**（Go 的 TraceHandler 是 native 链第一个）/ 必须在 `HttpMiddleware` **之前**（它把容器线程的 `Context.current()` 搬进 worker ⇒ span 属性落在本类的 server span 上）⇒ 🟢 `article-api` 真容器 **+3 条**守门 + **变异 M4/M5 全红**（红在目标断言上）⇒ 🔴 **同时新登记 T-12**：「方法不匹配」出口 RAG **多带**一个 `Traceparent`（**只登记、未修**）⇒ 详见 [common-tails.md §一·T-8](common-tails.md) / [§一·T-12](common-tails.md) |
| **A3** | **X-5**（裸 `ServerCallHandler` 不被 dispatch） | task + follow 的**测试**（非 favorite） | 换 `ServerCalls.asyncUnaryCall` + 补「handler 被调用过」断言 + 变异转红 | ✅ **第十七轮已落地** —— 两域已改（`sleep(10s)` ⇒ latch，因两条新断言在 sleep 方案下写不出来）⇒ **M1/M2/M3 三条变异全红** ⇒ 🟢 **生产代码零改动**（是**守门修复**，不是行为修复）⇒ 详见 [cross-domain.md §二·八 ⑤](cross-domain.md) |
| **A4** | **B10** `users_user.extra_info` 写路径仍按 jsonb | users 域 2 处（`AdminDomainService.java:962` + `UserEntity.java:129`） | 列**早已是 TEXT** ⇒ 写路径改为按 TEXT 绑定 ⇒ 当前含 `\u0000` 时 PG 报 `22P05`（Go 成功 / RAG 5001） | ✅ **第十八轮已落地** —— 🔴 **真 PG 16 坐实**（`text` 列收下 14 字节；`CAST … AS jsonb` 被拒，**SQLSTATE 是 22P05 不是 22P02** ⇒ 台账旧结论已订正）⇒ ✅ **三处改完**（去掉 CAST + 实体换 `JsonTextTypeHandler` + **删除** `JsonbTypeHandler`）⇒ ✅ **真 PG 守门 2 条 + 3 条变异全红** ⇒ 详见 [cross-domain.md §二·十四](cross-domain.md) |
| **A5** | **X-4 的 like 半边** | like 域 + `service/common` | RAG **另起炉灶**的 outbox+stream+scheduler 与 Go `communityfact` 收敛 | ✅ **第二十轮已落地**（like 事实投递链路 + 三套守门 22 条 + 变异 14/14 + 全仓 2213 条绿）⇒ ⛔ **favorite 半边永久阻塞**（必须删 favorite 自己的 10 处调用 ⇒ 零写权） |
| **A6** | **B6** like 域 `SD-2026-10-23-09` 描述订正 | like 域签字档 | 签字理由把**纳秒**的 `GoStyleRequestDeadline` 说成了毫秒 | 🟡 **补丁已由 L5 出** ⇒ **待整合者 fold**（⚠️ `docs/DRIFT/common/alignment-2026-10-23.md:575/:600` 的**编号也记错了** ⇒ `-09` 是 worker 池裸 503 那条） |
| **A7** | **O-1**（官方路径 · 可选优化） | `service/common` 探针那一处 | 用官方 `DisconnectedClientHelper.isClientDisconnectedException(ex)` 替掉自研断连判定 | ⛔ **不是待办** —— 用户 2026-10-25 06:40 已签字「**保持不变**」⇒ **不列入待办**（见签字档附记） |

### B · ❓ **未知项**（要取证才能定性）

| # | 未知项 | 消解它需要什么 |
|---|---|---|
| **B1** | ✅ **已消解（第二十四轮 L4）** —— 真 PG 16 三进程并发同库实测：六个域**可安全并发同库**（独立账本表名 + advisory lock 按账本表名隔离）⇒ 🔴 **favorite 不行**（缺 `spring.flyway.baseline-version: 0`）⇒ **只记账不修**（零写权）<br>🟡 **X-1** 三模块同库共存（`message-rpc` / `like-rpc` / `article-rpc` 同库同 schema） | 🟡 **第十五轮已部分消解** —— 🔴 取证发现 **7 个 Flyway rpc 模块全部连同一个库** ⇒ 同库共存是**常态** ⇒ 查出 **comment / follow / like 三个域缺 `spring.flyway.baseline-version: 0`** ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表建不出来 ⇒ 服务起不来**（同款缺陷 `message-rpc` 2026-10-24 已修并留了守门，**没被推广**）⇒ ✅ **三域已修 + 各配真 PG 守门（变异 6 条全红）**；⛔ **`favorite-rpc` 同款缺陷零触碰 ⇒ 记账 + 告知 owner**；🟢 **`article-rpc` 不受影响**（最低迁移版本 V8 > baseline 默认值 1 ⇒ 逐模块判定后排除，差点误修）；🟡 **「三进程并发」那个形态仍未验证** ⇒ 留一轮 |
| **B2** | ✅ **已消解（第二十四轮 L3）** —— 5 域**真 PG 16 双侧活体逐列对照**跑完（类型/长度/可空性/DEFAULT/索引/约束/触发器）⇒ article / follow **0 差异**；like / favorite 的 7 处差异**全是「RAG 更严」** ⇒ 登记为待签字候选；⚠️ **hot = N/A**（两侧都无数据库 ⇒ **不是「已对齐」**）⇒ 工具 `tools/difftest/ddl-parity.sh` ⇒ ⛔ favorite **只建库、不碰其代码** |
| **B3** | **X-9** 收敛方案定性 | hot / article 两份复制品**是否**收敛到唯一真源（favorite 那份永久留存 ⇒ 需定「收敛到什么程度」才不算制造新分叉） |
| **B4** | ✅ **已消解（第二十轮）** —— 四条调度契约**逐条对拍通过**（`POST /v1/events` · 1s 周期 · 每轮 16 条 · 单条失败即 break）⇒ 三套守门 22 条（真 PG 16 14 条 + 真 Spring 上下文 5 条 + 真 HTTP 假 DC 3 条）+ 变异 14/14 全红 |

### C · 🏷 **待签字（登记类 —— 不升级为拍板）**

| # | 条目 | 性质 |
|---|---|---|
| **C1** | `SD-2026-10-25-CAND-10`（hot 域 `DRIFT-HOTAPI-031`：超长请求行 ⇒ RAG 400+HTML / Go 431+`text/plain`） | 框架限制 |
| **C2** | **T-7**（状态行缺 reason phrase，落点 Tomcat `Http11OutputBuffer`） | 框架限制 · 🟡 **候选条目由 L5 落档**（`docs/Intentional_Deviations/global/`） |
| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无） | 刻意保留（同 message 域 `SD-2026-10-23-06` 已签的同形态） |
| **C4** | `users_user.status` 可空性（Go 可空 / RAG `NOT NULL`） | RAG 更严 ⇒ 同 C3 族 |
| **C5** | `DRIFT-COMMON-005` **差异②**（毫秒 vs 纳秒 ⇒ 必须动 `bindToCurrent` 签名） | 破坏性变更 |

### D · 🔴 **真决策项（需拍板 —— 只有这 2 条）**

| # | 事项 | 为什么必须用户拍板 |
|---|---|---|
| ~~**D1**~~ | ✅ **已拍板并落地（第二十五轮，2026-10-25 21:57）** —— 用户选「**撤销 `SD-HOT-API-006`，修 T-6**」⇒ 共享层 `GoStyleJsonContentTypeAdvice` ⇒ JSON 响应带 `charset=utf-8` ⇒ 10 个 api 全生效 ⇒ 真 Tomcat 守门 3 条 + 变异 2/2 全红<br>⚠️ **顺带订正一处错账**：原判「`SD-HOT-API-006` 的『不可逐字节复刻』前提已被本仓证伪」**不成立** —— 真实原因是 **Tomcat 11 归一化** ⇒ 应用层拿不到那个空格 ⇒ **原始理由成立且更强** ⇒ 撤销的性质 = **纯用户决策** | — |
| ~~**D2**~~ | ⏸ **不再上报** —— 用户 **2026-10-24 已拍板**「`DRIFT-COMMON-005` **先记入台账，本轮不改**」（记录在 `docs/DRIFT/common/alignment-2026-10-23.md` §八·决策 2）⇒ **不重复占用拍板额度** | — |

### 🆕 七之三、第二十一~二十五轮新增项（**2026-10-25 06:40 快照之后**）

> ⚠️ 上面的 §七之二是 **2026-10-25 06:40 的快照** ⇒ **第十九轮之后的五轮都没进去** ⇒ 本节补齐。

| # | 条目 | 落点 | 状态 |
|---|---|---|---|
| **E1** | **T-6** JSON 响应的 `Content-Type` 缺 `charset=utf-8` | `service/common`（新增 `GoStyleJsonContentTypeAdvice`） | ✅ **第二十五轮已修**（用户拍板撤销 `SD-HOT-API-006`）⇒ 10 个 api 全生效、零 api 代码改动 ⇒ 真 Tomcat 守门 3 条 + 变异 2/2 全红 ⇒ ⚠️ 残余**只差一个 OWS**（Tomcat 11 归一化 ⇒ 应用层拿不到那个空格 ⇒ **RFC 9110 OWS 不显著 ⇒ 语义相同**）⇒ 逐条证据见 [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §十三 |
| **E2** | **T-11** trace **导出**链路在 RAG 侧整体缺失 | `service/common`（全局 `OpenTelemetrySdk` + OTLP exporter + 关停 flush） | ✅ **第二十一~二十四轮已修** ⇒ **第二十四轮补上了 Go 侧活体对拍**（`tools/semantic-probe/trace/`：11 用例 · 8 一致 · 3 已知能力缺口 · **0 漂移**）⇒ 逐条证据见 [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §九 / §十一 / §十二 |
| **E3** | **T-12** 「方法不匹配」出口 RAG **多带**一个 `Traceparent` | 出口层级差异（Go router 层 vs RAG `DispatcherServlet` 内）⇒ **共享层改不动** | 🏷 **签字候选**（定性由 L5 落档）⇒ 根因与代价见 [`common-tails.md`](common-tails.md) §一·T-12 |
| **E4** | **T-13** 超时路径的「响应撕裂 / 状态码被抢」 | `service/common`（`ObservabilityResponseWriter` 的单写者语义） | ✅ **第二十二轮已修**（锁串行化 + 超时后 worker 不许提交 + 先标记后中断 + `Content-Length` 对齐）⇒ 真 Tomcat 守门 + 变异 5/5 全红 ⇒ 逐条证据见 [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §十 |
| **E5** | **X-5** 剩余 3 个 rpc 域的拦截器守门复查 | `service/{users/user/user-rpc,users/admin/admin-rpc,message/message-rpc,hot/hot-rpc}/src/test/**` | ✅ **第二十四轮 L2 已补齐**（user-rpc / message-rpc **缺测试** ⇒ 新建真 server 守门；hot-rpc **守门不严** ⇒ 加固；admin-rpc **新增** slow threshold 守门）⇒ 变异 8/8 全红 ⇒ ⛔ admin / article / comment / like / favorite **仍只有纯 mock**（登记在 L2 台账） |
| **E6** | **X-7** 5 域 DDL 双侧活体逐列对照 | `tools/difftest/ddl-parity.sh` | ✅ **第二十四轮 L3 已闭合** ⇒ article / follow **0 差异**；like / favorite 的 7 处差异**全是「RAG 更严」** ⇒ 待签字候选；⚠️ **hot = N/A**（两侧都无数据库 ⇒ **不是「已对齐」**） |
| **E7** | **X-1** 三进程同库共存 | `tools/difftest/check-flyway-concurrent.sh` | ✅ **第二十四轮 L4 已闭合**（六个域可安全并发同库）⇒ 🔴 **favorite 不行**（缺 `baseline-version: 0` ⇒ **起得来、首次查库才炸**）⇒ **只记账不修** |

### ⛔ favorite 相关的两条（**不可动，仅记账**）

- **X-4 favorite 半边** —— RAG 多出 10 处 Go 没有的 `communityfact` 调用 ⇒ 删掉它们 = 改 favorite ⇒ 永久阻塞
- **X-9 favorite 那份复制品** —— 「Go 是否读 body」判据的第 1 份生产复制品 ⇒ 永久留存
- ⇒ ⚠️ 但**共享层改动会影响 favorite 的可观察行为** ⇒ 每一次共享层改动**必须记账并告知该域 owner**（§七 纪律 1b）

### 📌 只记不修（**性能非行为**）

- users 域缺 Go 的 **3 条非唯一索引**（AGENTS.md §0 DDL 差异第 2 条）⇒ **性能非行为** ⇒ 不修

---

| 日期 | 动作 |
|---|---|
| 2026-10-25 | ✅ **第二十五轮（T-6 落地）** —— ① 用户拍板「撤销 `SD-HOT-API-006`，修 T-6」⇒ 共享层新增 `GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` ⇒ **10 个 api 全生效**、零 api 代码改动）⇒ JSON 响应头从裸 `application/json` 变成 `application/json;charset=utf-8` ② 🟢 **真 Tomcat + 裸 socket 守门 3 条 + 变异 2/2 全红**（M8 不写头 ⇒ 两个 JSON 门红；M9 无条件写 ⇒ 「不误伤」门红）③ 🔴 **自曝错账 #2**：本档原写的「`SD-HOT-API-006` 的『不可逐字节复刻』前提已被本仓证伪」**不成立** —— 真实原因是 **Tomcat 11 归一化**（`Response.setContentType` 把 Content-Type 拆成 (type/subtype, charset) 重新拼）⇒ 应用层**根本拿不到那个空格** ⇒ 原始理由**成立且更强** ⇒ 撤销的性质 = **纯用户决策** ④ ⚠️ 顺带订正 `GoStyleRouterErrorResponses` 一条**被证伪的注释**（原文称「写原始字符串是唯一能保证逐字节对上的做法」⇒ 实测证伪） |
| 2026-10-25 | ✅ **第二十四轮（Go 侧活体对拍 + 三条 lane 收口）** —— ① 🟢 **新增 `tools/semantic-probe/trace/`**（**第二个** semantic probe）：Go 半边是 go-zero **真源包内**测试（`createExporter` / `startAgent` 未导出 ⇒ 只能进包内）+ RAG 半边只调**生产类** ⇒ **11 用例 · 8 一致 · 3 已知能力缺口 · 0 漂移** ⇒ ⚠️ **挖出一条 RAG 侧没有守门的语义**（「入向 parent 未采样 ⇒ 子 span 不采样」）⇒ 补了**门 8** + 变异 2/2 ② 🟢 **L3**（5 域 DDL 活体对照）· **L4**（三进程同库共存）· **L2**（3 域 rpc 守门复查 + slow threshold）三条 lane **全部收口并合回** ③ 🔴 **白名单订正**：L1 补登记 `service/common`（T-11/T-13 实际写了但当时只记账没登记 ⇒ 守门给的是假保证）· L2 补登记 admin-rpc ⇒ 机器守门 EXIT=0 |
| 2026-10-25 | ✅ **第十九轮 · 第二轮（T-8 链上位置定案 + T-12 登记）** —— ① 🔴 **链上位置是「承重」的**：三条约束各有 Go 出处（CORS **之前** / MaxConns·Shedding **之后** / `HttpMiddleware` **之前**）⇒ 9 个 filter 的 `@Order` **整体后移**（**相对顺序不变**）⇒ 顺带修掉「CORS 与 path.Clean 同级 ⇒ 靠 Bean 注册序定序」的**隐式依赖** ② 🟢 `article-api` 真容器 **+3 条**守门（完整链序 / `cors < traceparent < maxConns` / **OPTIONS 预检 204 不带 `Traceparent`**）③ 🟢 **变异 M4/M5 全红且红在目标断言上** ⇒ ⚠️ 这两条**只有 article-api 的守门能抓**（`service/common` 那 6 条看不见 `@Order`）④ 🔴 **新增 T-12**（「方法不匹配」出口 RAG **多带**一个 `Traceparent` ⇒ 随 T-8 落地才暴露 ⇒ **只登记、未修**）⑤ ⚠️ 顺带查清易误判点：Go 的「未注册路径 404」**也带** traceparent（`rest/server.go:58` 的 `NewServer` **默认** prepend `WithNotFoundHandler(nil)`）⇒ T-12 **只**涉及「方法不匹配」这一个出口 |
| 2026-10-25 | ✅ **第十九轮（T-8 落地）** —— ① ✅ **T-8 已落地**（`service/common` 新增 `GoStyleTraceparentFilter`）⇒ **10 个 api 全生效、零 api 代码改动**② 🔴 **根因**：RAG 侧**压根没有 server span**（无 `micrometer-tracing-bridge-otel` / 无 otel 自动配置 ⇒ `GlobalOpenTelemetry.get()` 是 no-op）⇒ 故自带**私有** `SdkTracerProvider`（**不碰全局**）③ 🔴 **头名大小写**：Go canonical 成 `Traceparent`，OTel Java 写小写且 Tomcat 原样发出 ⇒ **字节级不一致** ⇒ 主动 canonical 化（变异 M3 实测）④ 🟢 **真 Tomcat + 裸 socket 守门 6 条 + 变异 3 条全红** ⑤ 🔴 **一并修掉** `service/common` 的 **OpenTelemetry 版本错配**（api 1.62.0 vs context/sdk 1.44.0 ⇒ 运行时 `NoSuchMethodError`）⇒ 统一由 BOM 管（**对 10 个 api 零影响**）⑥ 🟢 **新增 T-11**（trace **导出**链路整体缺失 ⇒ **只登记、未修**） |
| 2026-10-25 | ✅ **第十八轮（B10 落地）** —— ① 🔴 **真 PG 16 坐实** —— `text` 列收下 Go 侧 `encoding/json` 产出的 14 字节 `{"k":"\u0000"}`；`CAST(… AS jsonb)` **被拒** ⇒ **Go 成功 / RAG 5001** 坐实 ② 🔴 **订正到处写错的 SQLSTATE**：**22P05**（`json_bad_unicode_escape`），**不是** 22P02 ⇒ 本域 5 处已改；⛔ users 域台账 3 处**不在 global 写权内** ⇒ 记账 + 交 owner ③ ✅ **三处改完**（去 CAST + 实体换 `JsonTextTypeHandler` + **删** `JsonbTypeHandler`）④ ✅ **真 PG 守门 2 条 + 3 条变异全红**⑤ 🔴 **变异脚本第三个坑**：改**上游模块的资源**（`users-common` 的 DDL）而只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ **改动到不了运行代码** ⇒ 必须把上游拉进**同一 reactor**；surefire 参数换代 ⇒ `-Dsurefire.failIfNoSpecifiedTests=false` |
| 2026-10-25 | ✅ **第十七轮（X-5 落地）** —— ① 🔴 **第七轮只取证、这轮改完** —— `service/task/task-rpc` + `service/follow/follow-rpc` 两个域的 `*GrpcInterceptorWiringTest` 把**裸** `ServerCallHandler`（手写 `startCall` + `onHalfClose`）换成 grpc-java 的**标准派发入口** `ServerCalls.asyncUnaryCall(...)` ⇒ 处理函数**真的执行** ② 🔴 **补上原来缺的那一维** —— 两条新断言：「**handler 确实被调用过**」+「**取消发生时它仍在跑**」；并把 `Thread.sleep(10_000)` 换成 `probe.release.await(30, SECONDS)`（latch）⇒ 这两条断言在 `sleep` 方案下**根本写不出来** ③ ✅ **生产代码一行没动**（`server.intercept(...)` 原样）⇒ **这不是行为修复，是守门修复**（超时机制本来就是真的；缺的只是「在飞处理被打断」这一维**从未被验证**）④ ✅ **三条变异全部转红** —— M1 去 `server.intercept` ⇒ `elapsed 5010/5009ms`；M2 换回裸 handler ⇒ 红在**新增**的 `entered` 断言；M3「完成但**不回包**」⇒ 红在**新增**的 `completed` 断言 ⑤ 🔴 **变异脚本踩了两个坑**（⇒ 新增 **PIT-GLOBAL-018**）—— ① 首版 M1 写成**编译不过**的表达式 lambda ⇒ maven **压根没跑测试** ⇒ surefire 里留着**上一轮**的 XML ⇒ 脚本读旧结果 ⇒ **报成假绿**（靠「跑前删旧 XML + 看 maven 退出码 + 查 `COMPILATION ERROR`」三道闸才拦住）② 首版 M3 只做 `await(0)` 不去回包 ⇒ handler 秒完成**并正常回包** ⇒ 客户端拿到 OK ⇒ 红在**更早**的 `assertThrows` 上 ⇒ **目标断言压根没被执行到** ⑥ ✅ 两模块全量 **180 条全绿**（task-rpc 108 + follow-rpc 72）⑦ ⚠️ **边界**：Go 的 **slow threshold** 分支**仍无独立守门**；其余 8 个 rpc 域的同名测试**未逐一复查** ⇒ 「还有没有别的空转形态」**未穷尽** |
| 2026-10-25 | ✅ **第十六轮（X-9 收敛 + 解析层补 `maxBodyLen`）** —— ① ✅ **新建双侧活体探针 `tools/json-body-probe/`** —— Go 半边**真调** go-zero 的 `httpx.Parse`、Java 半边走 **hot 域真实实现** ⇒ **期望值不是人写的**；**无 known-dev 白名单** ⇒ 任一条不一致即 exit 1 ② ✅ **首跑 19/20 一致 ⇒ J20 不一致** ⇒ 坐实「RAG 整个读 body、Go 只读前 8 MiB」（Go 错误详情长度 **8388643 = 8 MiB + 3**）③ ✅ **共享层补 `GO_MAX_BODY_LEN` + `goLimitedBody`（Go `io.LimitReader` 等价物）+ servlet 版判据重载**（连「怎么取头值与长度」一起收进真源）· **hot 域改用唯一真源 + 改流式读** · **article 域删本地判据** ⇒ **「3 份生产复制品」⇒ 1 份** ④ 🔴 **澄清一条差点写错的话**：Go 的 `MaxBytes` **默认 1 MiB 且挂在引擎上全局生效**（`rest/config.go:53` + `rest/engine.go:163-164`）⇒ 9 MiB body **先被拦成裸 413** ⇒ **端到端那条漂移根本不存在**（两侧都是 413）⇒ 本次修的是**解析层**，端到端今天**零行为变化**（唯一可达的 article 段已被它自己的 filter 覆盖）⇒ 逐域可达性已列表 ⑤ ✅ **6 条守门 + 全部做过变异**（真 Tomcat 裸 socket 验原始头值 · 8 MiB 边界卡 ±1 字节 · hot 端到端 413 + 解析层 5001 · article 判别式断言 · **新 tools 守卫 `check-go-json-body-predicate.py`**）⑥ ⛔ **favorite 那份复制品永久残留**（零权限）⇒ 记账 + 写明修法与前提 ⑦ 📌 **新增 PIT-GLOBAL-015**（「**探针测的层 ≠ 端到端跑的层**」） |
| 2026-10-25 | ✅ **第十五轮（X-1 取证与修复）** —— ① 🔴 **取证推翻「同库共存是边缘场景」** —— **7 个 Flyway rpc 模块的 DSN 默认值全部是同一个 `first_db`** ⇒ 「先启动者把 public schema 弄非空」是**必然** ⇒ 每个 V1 起始的模块都必须扛得住 ② 🔴 **查出 3 个域缺 `spring.flyway.baseline-version: 0`** —— `baseline-on-migrate=true` + 非空 schema + 无账本 ⇒ Flyway 在 **version 1** 处打 BASELINE ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来 ⇒ **服务起不来** ⇒ 同款缺陷 `message-rpc` **2026-10-24 已修并留了守门**（`MessageFlywaySharedSchemaWiringTest`）⇒ **同一个坑在另外 3 个域原封不动地留着** ③ ✅ **三域已修**（各一行 yaml）+ **各配一个真 PG 守门**（真 PG 16 + 真 Flyway 引擎 + **配置取自模块自己的 yaml** + **先把 schema 弄成非空**）⇒ **变异（删掉那行）⇒ 6 条全红** ④ ⚠️ **`article-rpc` 逐模块判定后排除**（最低迁移版本 **V8** > baseline 默认值 1 ⇒ 不受影响 ⇒ **差点误修一个不存在的缺陷**）⑤ ⛔ **`favorite-rpc` 同款缺陷零触碰** ⇒ 记账 + **明确告知该域 owner**（含修法与守门思路）⇒ **不静默** ⑥ 🟡 **「三进程并发」的运行时形态仍未验证** ⇒ 留一轮 |
| 2026-10-25 | ✅ **第十四轮（X-6 落地）** —— ① 🔴 **推翻了第十三轮修法草案的一个隐含前提**：「共享 Filter 统一写 Go 的 400 原文」**是错的** —— Go 的 `httpx.SetErrorHandler`（go-zero `rest/httpx/responses.go:141-172`）是**逐 api 注册**的 ⇒ 解析失败的 wire 形状**按域而异**（hot/follow/like/message/comment 注册了 ⇒ **200 + 业务信封**；article/user/task/favorite 没注册 ⇒ **400 + Go 原文**）⇒ 且**各域 5001 文案不同**（like/comment =「系统内部错误」，hot/follow/message = `"internal server error"`）⇒ ⇒ **共享层写不出信封** ⇒ 拒绝形状**必须由域自己渲染** ⇒ ⇒ 落地为 `service/common` **6 个新件**（`GoStyleFormValues` 判据 / `GoStyleFormLimitMode` 模式 / `GoStyleFormLimitExceededException` / `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）+ **9 个域各一行 yaml**（`rag.http.go-form-limit.mode: raw\|envelope`）⇒ 🔴 **favorite 缺省 = off ⇒ 容器上限与闸门都不生效 ⇒ 零触碰、行为逐字节不变、无需通知其 owner** ② 🔴 **闸门必须是 `HandlerInterceptor` 不是 servlet `Filter`** —— Filter 在 DispatcherServlet **之外** ⇒ 抛的异常到不了 `@RestControllerAdvice` ⇒ envelope 模式无法渲染 ③ ✅ **顺带订正 `service/common/pom.xml`** 的 `spring-web` / `spring-test` 硬编码 **7.0.0**（与 starter-web 的 7.0.9 错配 ⇒ `NoClassDefFoundError: ApiVersionHolder`）⇒ 改为跟随 BOM ④ 🟢 证据强度首次达 **真容器 + 真 Spring MVC + 真 advice + 变异测试**（raw 5/5 · envelope 4/4 · 模式 8/8 · 变异 M1/M2 均转红）· `check-tomcat-limits.py` 新增第四旋钮 + **favorite 零触碰守卫** ⇒ EXIT=0 ⑤ 🆕 **踩坑留痕 `PIT-GLOBAL-013`**（Go 的注册式钩子 ⇒ 行为按进程而异 ⇒ 共享层复刻前先问「这条在 Go 侧是全局的吗」）+ `PIT-GLOBAL-014`（变异测试「没转红」要先排除「测试没跑」） |
| 2026-10-25 | ✅ **第十三轮（官方路径记录 + 再次签字 + 剩余项盘点）** —— 用户 2026-10-25 06:40：「**先记录官方处理**，然后**接受现有的 CAND-12 处理**，然后**签字接受 socket 的偏离**（但是我需要你**把官方的处理列为后续优化选项，而不是必选项**）」⇒ ① 🔴 **`SD-2026-10-25-04` 状态追加「再次明确签字」**（Jason · 2026-10-25 06:40）⇒ **本条闭环，不再另选方案** ② 🆕 **签字档新增「附记 · 官方路径记录（可选后续优化 —— 不是必选项）」** —— 全量 grep spring-web / spring-webmvc 7.0.9 + spring-boot / spring-boot-tomcat 4.0.8 ⇒ **官方没有「检测」API，只有「事件消费」链路**（`DisconnectedClientHelper` 事后分类 · `DefaultHandlerExceptionResolver.handleDisconnectedClientException` since 6.2「do nothing」· `AsyncListener.onError` + `WebAsyncManager` + `DeferredResult.onError`）；Boot 侧 grep = **0 命中**；关键 Tomcat 事实 `AbstractProtocol.java:1494-1503`（**async 期间不注册读兴趣** ⇒ HTTP/1.1 下官方链路**也不更早**，且已被第十轮 RST 实测独立验证）⇒ **方案⑤与官方路径互补而非替代** ⇒ 登记 **O-1~O-4** 四个**可选**优化项（O-1 用官方 `DisconnectedClientHelper` 分类 ⇒ 成本最低）③ ✅ **顺手订正 DRIFT 侧 T-4 的 4 处过期状态**（第十轮已修，但本档仍写「资源面残留待签字」+ 指向**已撤销**的 `SD-2026-10-25-02`）④ 🆕 **§七之二 剩余项盘点**（A 可直接修 7 条 / B 未知项 4 条 / C 待签字 5 条 / D 真决策 2 条 / favorite 不可动 2 条 / 只记不修 1 条） |
| 2026-10-25 | ✅ **第十二轮（favorite 零权限重申 ⇒ 推翻自己「X-6 永久阻塞」的结论）** —— 用户重申「**你没有 favorite 的所有权限**」⇒ ① 🔴 **X-6 改判：不是 favorite 边界下的永久阻塞** —— 第九轮那句「修法必然要动 `favorite-api`」**只覆盖「逐模块改 yaml」这一条修法**；本轮实测**共享层修法存在**（`Connector.setMaxParameterCount(-1)` + Boot customizer 顺序 `@Order` > 0 + `com.rag.common` 组件扫描 **10/10**）⇒ **10 个 api 模块零改动 ⇒ favorite 零触碰** ⇒ ⇒ **不是拍板项、不需签字认账** ⇒ 落 `service/common`（global 单写权内）② ✅ **连带影响面复核**：X-4（favorite 半边）**仍阻塞**（必须删 favorite 自己的 10 处调用）· X-9（favorite 那份复制品永久留存）· X-7（只读库 ⇒ 不受影响）③ 🆕 **踩坑留痕 `PIT-GLOBAL-012`**（「per-app 配置」≠「只能逐模块改」⇒ 判「不可修」前必须先查共享层路径）④ §七 纪律 1b **口径收紧**（favorite 域代码/配置/测试/台账**一律零改动**；但「碰不到文件」≠「碰不到行为」⇒ 共享层改动必须记账并告知该域 owner） |
| 2026-10-25 | **第六轮（签字 + 授权范围）**：① **`SD-2026-10-25-CAND-12` → `SD-2026-10-25-02`（已签字）** —— T-4 资源面残留是**框架限制** ⇒ 不存在决策空间 ⇒ 签字只认账事实、**不改任何代码** ② 🔴 **X-10 用户签字** ⇒ 保留 RAG 的 `(recipient_id, is_read)` 复合索引，**不改回** Go 的单列（根因 = Go 侧 gorm tag 的 `priority:1` 写到了另一个索引名）；配套：迁移脚本注释订正（留指针）+ DRIFT 侧移除该条只留指针（§5.3b）③ **#8 销账** —— `docs/Pitfalls/common/_signed-deviations.md` 确认是 0 字节错放文件 ⇒ 删除 ④ **记录写权扩展**：用户授权 global 改**除 favorite 外**所有域的代码（§七 纪律 1b） |
| 2026-10-25 | ✅ **第十一轮（T-4 资源面已修 · 方案⑤ 落地）** —— 用户拍板「**CAND-12：主动探测写（周期 flush()）（签字偏离）**」⇒ 落地「主动探测写 + gRPC `Context` 取消」⇒ **在飞 gRPC 会被提前取消**（实测 **461ms**）⇒ 🆕 签字 **`SD-2026-10-25-04`**（wire 偏离：客户端先收到 N 行 `103 Early Hints`；快请求零影响）+ 残留时序差 ⇒ ❌ 原 `SD-2026-10-25-02` **撤销**（问题被修掉 ⇒ 不需签字）
| 2026-10-25 | ✅ **第十轮（T-6 / T-7 / T-8 归因定位）** —— 🔴 **原落点标注有误**（`GoStyleRouterErrorResponses.java:83` 只管 404/405）⇒ 三条分属三层：**T-6** = Spring JSON converter（🟢 可修，被 hot 域 `SD-HOT-API-006` 挡住）· **T-7** = Tomcat 容器层（🔴 真框架限制）· **T-8** = `common` HTTP 入口（🟢 可修，不需拍板）⇒ 「三条同落点一次改」**作废**
| 2026-10-25 | ✅ **第九轮（T-4 资源面 6 条替代路径全部查完）** —— 用户质询「springboot 做分布式肯定有办法」⇒ **「生态做不到」被证伪**：① 换 Jetty 🟡 ② 换 WebFlux+Netty 🟡 ③ **轮询 Tomcat 内部 `isClosed()` ❌实测否决**（滞后 5.6s）④ **HTTP/2 `RST_STREAM` ⇒ Tomcat 真触发 `AsyncListener.onError`** 🟡但偏离 Go（Go 侧实测明文 HTTP/1.1）⑤ **主动探测写 ✅实测有效**（~500ms 抛 `ClientAbortException`）但改 framing ⑥ **缩短在飞 deadline ✅零改动·零 wire·零签字** ⇒ ⇒ **定性改判**：不是事实登记，是「**要不要为这一点动约束**」的决策项
| 2026-10-25 | ⚠️ **第八轮（SD-2026-10-25-02 退回待签字 · 分类降级）** —— 用户 05:46：「**先等下签字，我想知道有没有其他办法，springboot 做分布式肯定有办法**」⇒ ① **撤销第四轮的「已签字」**（当时用户只问了「它是什么」，**未点头**）⇒ **改回 ⏳ 待签字** ② 原分类「**框架限制、不可修**」**过宽** ⇒ 实测只覆盖「**Tomcat + HTTP/1.1 + Spring MVC**」一种组合 ⇒ **降级为「Tomcat 的 Servlet 层不给断连信号」** ③ 登记 **6 条待查技术路径**（换容器 Jetty/Undertow · 换栈 WebFlux+Netty · 反射取 Tomcat 内部 socket 状态 · HTTP/2 `RST_STREAM` · 主动探测写 · 缩短在飞 deadline）⇒ **研究结论回来前不签字** |
| 2026-10-25 | ⚠️ **第七轮（X-5 确证 + X-10 去重）**：① 🔴 **X-5 由「未展开」升级为「已确证」** —— 一次性探针实测：grpc-java 1.63 **会**调裸 handler 的 `startCall()`，但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener` ⇒ 「处理函数睡 10 秒」**从未发生** ⇒ **task + follow 两个 rpc 域**的同名测试**绿但空转**（验证的是「取消一个空调用」，不是「打断在飞处理」）⇒ 踩坑留痕 `PIT-GLOBAL-011` ② ⚠️ **X-10 去重** —— 复查发现 X-10 早在 2026-10-23 已签为 message 域 `SD-2026-10-23-06` ⇒ **撤销本域重复登记的 `SD-2026-10-25-03`**（改为指针）+ 同步订正迁移脚本注释与 DRIFT 侧指针。⚠️ 顺带记录：**`SD-2026-10-23-06` 这个编号在 3 个域各有含义** ⇒ 引用必须带域限定 |
| 2026-10-25 | **第五轮（B2 / X-3 闭合）**：**「第三个仓库」已定位** —— 链路末端**不是仓库**，是**仓外 DC 平台**的 `POST /v1/events`（契约由 `DC_PLATFORM_EVENT_URL` + `DC_PLATFORM_SERVICE_TOKEN` 钉死，路径必须以 `/v1/events` 结尾、token ≥ 32，见 Go `service/common/communityfact/wire.go:83-91`）。仓内两侧**都有**：`service/common/communityfact` **4 文件 ↔ 4 类**、authority 端点路径 1:1、调度形状 **1s/16/10s/单条失败即 break** 四项逐条对齐、⚠️ **2026-10-25 第九轮订正**：本条末句「`like` / `comment` / `favorite` 三域**都接了线**」⇒ **实测不成立** —— `like` 域 RAG 侧对 `com.rag.common.communityfact` 是 **0 引用**（它**另起炉灶**实现了自己的 outbox+stream+scheduler）⇒ 准确表述：**`comment` 与 `favorite` 接了共享组件；`like` 域没有；`article` 多了一处 Go 侧没有的用法** ⇒ 详见 §二·十（X-4）。⇒ ⚠️ **X-3 本身（末端 = 仓外 DC 平台 / 共享组件契约）仍成立** —— 订正的只是「哪些域接了线」这一句。 ⇒ **不是漂移，是架构事实 + 仓外边界** ⇒ **X-3 关闭** |
| 2026-10-25 | **第四轮（B3 / X-7 部分闭合）**：① 查出 **`check-ddl-static.py` 已有的域预设本身已失效**（`users` 容器改名、`message` 容器+库名+psql 用户三重失效）⇒ 修好 + 给脚本加按域的 `pg_user` ② **扩域** ⇒ 新增 `comment` ⇒ **可跑的域 1 → 4** ③ 跑出**真结论**：task ✅ 11 列逐字相同、message ✅ 37 列逐字相同（DEFAULT 6 处 = 已知刻意保留）、users/comment 各有**环境缺口**（Go 侧对应表未被 AutoMigrate）④ 🔴 **新增 X-10** —— `message` 域索引 `idx_notification_recipient_read` **两侧列不同**（Go 单列 `is_read` / RAG 复合 `recipient_id,is_read`），根因是 Go 侧 `priority:1` 写到了另一个索引名 ⇒ 修法落在 message 域的迁移脚本 ⇒ **global 只记账，等用户拍板** ⇒ [`cross-domain.md`](cross-domain.md) |
| 2026-10-25 | **第三轮收口（A1 / A2 / A3 / A4）**：① **T-1 残余 ① 已修**（包一层 `HttpInputMessage` ⇒ 畸形头不再抛 + `charset=gbk` 回归 UTF-8；守门 8 条 + 变异双向转红）② **T-9 已修**（熔断键补 target 前缀，身份源 = `Channel.authority()`；守门 6 条 + 变异转红）③ **T-4 观测面已修**（499 判据改成「**filter 写 socket 失败**」；真 Tomcat 守门 3 条 + M1/M2/M3 三组变异全转红；⚠️ 顺带修掉「提前 flush 把 `Content-Length` 退化成 chunked」这个**新引入的 wire 差异**）⇒ 🔴 资源面残留转**框架限制** ⇒ `SD-2026-10-25-CAND-12`（待签字）④ **CYCLE-002 普查 = 0 命中** ⇒ 落成可重跑守门 `tools/difftest/check-cross-domain-holds.py` ⇒ [`cycle-deps.md`](cycle-deps.md) |
| 2026-10-25 | **域创建**。用户定义：`global` = 跨微服务 + `service/common` 影响的漂移收口域。首版收录：T-1~T-10（common 尾巴 10 条）· 4 条仍未决 + 2 条未销账 · 7 条跨域不变量 · 1 条两步闭环循环依赖 |
| 2026-10-25 | **第二轮收口**：① **T-1 已修** —— 唯一真源改取**原始头值**（`getFirst(CONTENT_TYPE)`）⇒ 大小写敏感判定与 Go 对齐；配真 `HttpHeaders` 守门 6 条 + 变异转红；⚠️ 登记 **T-1 残余 ①**（解析层仍走 MediaType ⇒ 畸形头仍抛）② **T-2 / T-5 用户签字** ⇒ `SD-2026-10-25-01` ⇒ **移出本档**，代码 javadoc 改为指向签字档的指针 ③ 新增 **X-9**（3 份生产复制品；T-1 修前 RAG 内部自相矛盾）⇒ [`cross-domain.md`](cross-domain.md) |
| 2026-10-25 | **第一轮收口（`align/global` @ `58e8314`）**：<br>① **T-10 / CYCLE-001 闭环** —— 两步同一次提交（users 侧订正 javadoc 假断言 → common 侧删 `markExpired`）+ 真 in-process gRPC 守门测试 4 条 + 变异测试 2 组（均转红）⇒ [`cycle-deps.md`](cycle-deps.md)<br>② **T-1 / T-2 / T-5 / T-9 复核确认**为真漂移，补齐逐行 Go 出处 ⇒ [`common-tails.md`](common-tails.md)<br>③ 🔴 **发现「Go 出处在框架层」** —— 真源分 L1 项目层 / L2 go-zero v1.10.2 框架层 ⇒ [`cross-domain.md`](cross-domain.md) X-8 + [`Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) PIT-GLOBAL-001<br>④ 🔴 **发现 T-2/T-5 档位错置**（代码自称「有意」但无签字）⇒ 待用户拍板<br>⑤ 🔴 **发现 T-9 的 §3.2 缺陷**（javadoc 照抄 Go 那行却未说明自己没实现 target 前缀）<br>⑥ 踩坑留痕 4 条 ⇒ [`Pitfalls/global/`](../../Pitfalls/global/) |
