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
- 纯粹的**跨域不变量**（涉及 3+ 域、无法归给任何单一域）⇒ `docs/DRIFT/global/cross-domain.md`

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
| 4 | `like` 的 `SD-2026-10-24-10` 描述订正 | ✅ **已订正（第二十六轮 · 整合者 fold 完毕）** —— ① **编号订正**：原文 `SD-2026-10-23-09` **记错**（`-09` 是 worker 池裸 503 那条）⇒ 正确是 **`SD-2026-10-24-10`**；② **理由订正**：`GoStyleRequestDeadline.applyTo` **是纳秒的** ⇒ 真实理由是「**只读** API（`remainingMillis()`）只有毫秒粒度」⇒ 落点 ⇒ `docs/Intentional_Deviations/like/api.md` + `docs/DRIFT/common/alignment-2026-10-23.md` §九·④ / §十一·④ |
| 8 | `docs/Pitfalls/common/_signed-deviations.md` 疑似错放 | ✅ **2026-10-25 已销账**：确认是 **0 字节空文件**（真名在 `docs/Intentional_Deviations/common/_signed-deviations.md`）⇒ **直接删除**（0 字节 ⇒ 删掉不可能丢内容） | ✅ **已销账** |
| 9 | 社区消费者：Go 用 `like`，RAG 不用 | ✅ **已销账**（**第二十轮**：like 半边事实投递链路落地 + 三套守门 22 条 + 变异 14/14）⇒ ⛔ **favorite 半边永久阻塞**（零写权 ⇒ 只记账） |
| 10 | 裸 `ServerCallHandler` 不被 dispatch（task 域） | ✅ **2026-10-25 第七轮已确证**（探针实测 ⇒ grpc-java 不向裸 handler 派发 `onHalfClose` ⇒ task + follow 两域的测试空转）⇒ **未知项消解，转为待修** |

⇒ **真实分布（2026-10-25 第二十六轮更新）**：已销账 **9 条**（#1/#5/#6/#7/#11/#2/**#8**/**#9**/**#4**）· **未销账 0 条**· **仍未决 1 条**（**#3** `DRIFT-COMMON-005`，跨域破坏性变更 ⇒ **唯一被反复确认的未决项**）—— #10 已由第七轮**确证**（机制 + 波及面）⇒ 从「未知」转为「待修」⇒ **第二十四轮 L2 已把 user-rpc / message-rpc / hot-rpc 三处补齐**。

⇒ **第三轮（2026-10-25）新增归位**：**T-4 的资源面残留**（「断连后在飞 gRPC 不被提前取消」）
经真 Tomcat 11.0.24 实测 + Tomcat 源码取证 ⇒ **Tomcat 的 Servlet 层不向应用暴露客户端断连信号**
⇒ 归位为 **`SD-2026-10-25-02`**
⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)。

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
> ⇒ ⇒ 逐条 ⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)（§`SD-2026-10-25-04` + 其「附记 · 官方路径记录」）。

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
   ⇒ 📌 **由此推出一条纪律**（⇒ [`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-012](../../Pitfalls/global/cross-domain.md)）：
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

> 🟥 **2026-10-26 第二十八轮订正 —— 本节的口径漏了「第五个来源」**
>
> **原口径只扫 global 自己的四桶（A/B/C/D）** ⇒ **漏掉**了「**各域台账里的『域外 / 跨域移交 / 交主会话』段**」。
>
> **为什么那是个漏洞**：那些条目**明写**「域外 ⇒ 交主会话派单」⇒ 它们**恰恰就是** global 该接的
> （落点在 `service/common`，或跨域 ⇒ global 有单写权），**且各域自己不能碰**（域外改动要派单，§5.3）。
> ⇒ 按字面「域内」口径，它们**永远进不了** global 的盘点 ⇒ **挂着不动 = 永久漂移**。
>
> **实测规模**：第 27 轮开工前（`d71a226`）非 global 台账里「域外/交主会话/跨域待办/跨域移交」共
> **63 行 / 14 份文件**（`follow/rpc.md` 17 行 · `hot/api.md` 11 行 · `common/alignment` 9 行 ·
> `follow/api.md` 5 行 · `message/rpc.md` 5 行 · `article/rpc.md` 4 行 · `comment/rpc.md` 3 行 · …）。
>
> **⇒ 修正后的 global 盘点口径 = ① 本节四桶（域内）＋ ② 各域台账的「域外/跨域移交/交主会话」段**。
>
> ⚠️ **由此产生的连带订正**：第 27 轮 L11 报的 N6 / N7 是**重发现**（follow 域 2026-10-24 `8eb6121`
> 已写明并点名同样 5 个模块 + 4 个 `grpc-inprocess` 模块，见 `follow/rpc.md:65-97` 与 `:408`），
> **不是本轮新发现** ⇒ 真正的「新发现」只有 N8（且 L11 漏报了 `hot-rpc`）· T-14 · T-15。

### A · ✅ **可直接修**（落点在 global 单写权范围内 / 非 favorite 域 · **不需拍板**）

| # | 条目 | 落点 | 修法 | 状态 |
|---|---|---|---|---|
| **A1** | **X-6** / `DRIFT-HOTAPI-030` | `service/common` × 6 个新件 + **9 个域各一行 yaml** | 🔴 **第十四轮落地时推翻了第十三轮修法草案的一个前提** —— Go 的 `httpx.SetErrorHandler` 是**逐 api 注册**的 ⇒ 解析失败的 wire 形状**按域而异**（注册了 ⇒ **200 + 业务信封**；没注册 ⇒ **400 + Go 原文**）且**信封文案在域自己的错误码表**里 ⇒ 共享层**写不出信封** ⇒ 落地为：① `GoStyleFormValues`（只出 Go 判据）② `GoStyleFormLimitMode`（off/raw/envelope，非法值炸）③ `GoStyleFormLimitExceededException`（刻意不继承 `BusinessException`）④ `GoStyleFormValueLimitInterceptor`（**必须是 Interceptor**：servlet Filter 抛的异常到不了 advice）⑤ `GoStyleFormLimitWebMvcConfig`（注册）⑥ `GoStyleTomcatParameterLimitCustomizer`（上限 → **10001**，**仅非 off 模式生效**）⇒ 每域一行 `rag.http.go-form-limit.mode: raw\|envelope` ⇒ **favorite 缺省 = off ⇒ 容器上限也不动 ⇒ 零触碰** | ✅ **已落地**（第十四轮）· 🟢 **真容器 + 真 MVC + 真 advice + 变异测试** |
| **A2** | **T-8** / `DRIFT-MSGAPI-013`（缺 `Traceparent`） | `service/common` 的 **HTTP 入口** | 注入 W3C `traceparent`（Go = go-zero `rest/handler/tracehandler.go` 的 `propagator.Inject`） | ✅ **第十九轮已落地** —— 🔴 根因不是「忘了写一行」，而是 **RAG 侧压根没有 server span**（无 `micrometer-tracing-bridge-otel` / 无 otel 自动配置 ⇒ `GlobalOpenTelemetry.get()` 是 no-op ⇒ `SpanContext` 恒 INVALID）⇒ 新增 `GoStyleTraceparentFilter`（**私有** `SdkTracerProvider`，**不碰全局**）⇒ **10 个 api 全生效、零 api 代码改动** ⇒ 🟢 **真 Tomcat + 裸 socket 守门 6 条 + 变异 3 条全红**（含「不是回显入向」「头名 canonical」）⇒ ⚠️ **同时新登记 T-11**：trace **导出**链路在 RAG 侧整体缺失（**本轮只登记、未修**）<br>⇒ 🔴 **第二轮：链上位置定案**（9 个 filter 的 `@Order` 整体后移，**相对顺序不变**）—— CORS 必须在 traceparent **之前**（Go 的 `cors.Middleware` 对 OPTIONS 短路 ⇒ 预检不带头）/ MaxConns·Shedding 必须在它**之后**（Go 的 TraceHandler 是 native 链第一个）/ 必须在 `HttpMiddleware` **之前**（它把容器线程的 `Context.current()` 搬进 worker ⇒ span 属性落在本类的 server span 上）⇒ 🟢 `article-api` 真容器 **+3 条**守门 + **变异 M4/M5 全红**（红在目标断言上）⇒ 🔴 **同时新登记 T-12**：「方法不匹配」出口 RAG **多带**一个 `Traceparent`（**只登记、未修**）⇒ 详见 [common-tails.md §一·T-8](common-tails.md) / [§一·T-12](common-tails.md) |
| **A3** | **X-5**（裸 `ServerCallHandler` 不被 dispatch） | task + follow 的**测试**（非 favorite） | 换 `ServerCalls.asyncUnaryCall` + 补「handler 被调用过」断言 + 变异转红 | ✅ **第十七轮已落地** —— 两域已改（`sleep(10s)` ⇒ latch，因两条新断言在 sleep 方案下写不出来）⇒ **M1/M2/M3 三条变异全红** ⇒ 🟢 **生产代码零改动**（是**守门修复**，不是行为修复）⇒ 详见 [cross-domain.md §二·八 ⑤](cross-domain.md) |
| **A4** | **B10** `users_user.extra_info` 写路径仍按 jsonb | users 域 2 处（`AdminDomainService.java:962` + `UserEntity.java:129`） | 列**早已是 TEXT** ⇒ 写路径改为按 TEXT 绑定 ⇒ 当前含 `\u0000` 时 PG 报 `22P05`（Go 成功 / RAG 5001） | ✅ **第十八轮已落地** —— 🔴 **真 PG 16 坐实**（`text` 列收下 14 字节；`CAST … AS jsonb` 被拒，**SQLSTATE 是 22P05 不是 22P02** ⇒ 台账旧结论已订正）⇒ ✅ **三处改完**（去掉 CAST + 实体换 `JsonTextTypeHandler` + **删除** `JsonbTypeHandler`）⇒ ✅ **真 PG 守门 2 条 + 3 条变异全红** ⇒ 详见 [cross-domain.md §二·十四](cross-domain.md) |
| **A5** | **X-4 的 like 半边** | like 域 + `service/common` | RAG **另起炉灶**的 outbox+stream+scheduler 与 Go `communityfact` 收敛 | ✅ **第二十轮已落地**（like 事实投递链路 + 三套守门 22 条 + 变异 14/14 + 全仓 2213 条绿）⇒ ⛔ **favorite 半边永久阻塞**（必须删 favorite 自己的 10 处调用 ⇒ 零写权） |
| **A6** | **B6** like 域 `SD-2026-10-23-09` 描述订正 | like 域签字档 | 签字理由把**纳秒**的 `GoStyleRequestDeadline` 说成了毫秒 | ✅ **已订正（第二十六轮 L5 · 已 fold）** —— 编号订正为 **`SD-2026-10-24-10`**（原文 `SD-2026-10-23-09` **记错** ⇒ `-09` 是 worker 池裸 503 那条）；理由订正为「**只读 API（`remainingMillis()`）只有毫秒粒度**」⇒ ⚠️ **不是「整个公开 API 只有毫秒」**（`applyTo(stub)` 是**纳秒**的）⇒ 落点 ⇒ [`docs/Intentional_Deviations/like/api.md`](../../Intentional_Deviations/like/api.md) + [`docs/DRIFT/common/alignment-2026-10-23.md`](../common/alignment-2026-10-23.md) §九·④ / §十一·④ |
| **A7** | **O-1**（官方路径 · 可选优化） | `service/common` 探针那一处 | 用官方 `DisconnectedClientHelper.isClientDisconnectedException(ex)` 替掉自研断连判定 | ⛔ **不是待办** —— 用户 2026-10-25 06:40 已签字「**保持不变**」⇒ **不列入待办**（见签字档附记） |

### B · ❓ **未知项**（要取证才能定性）

| # | 未知项 | 消解它需要什么 |
|---|---|---|
| **B1** | ✅ **已消解（第二十四轮 L4）** —— 真 PG 16 三进程并发同库实测：六个域**可安全并发同库**（独立账本表名 + advisory lock 按账本表名隔离）⇒ 🔴 **favorite 不行**（缺 `spring.flyway.baseline-version: 0`）⇒ **只记账不修**（零写权）<br>🟡 **X-1** 三模块同库共存（`message-rpc` / `like-rpc` / `article-rpc` 同库同 schema） | 🟡 **第十五轮已部分消解** —— 🔴 取证发现 **7 个 Flyway rpc 模块全部连同一个库** ⇒ 同库共存是**常态** ⇒ 查出 **comment / follow / like 三个域缺 `spring.flyway.baseline-version: 0`** ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表建不出来 ⇒ 服务起不来**（同款缺陷 `message-rpc` 2026-10-24 已修并留了守门，**没被推广**）⇒ ✅ **三域已修 + 各配真 PG 守门（变异 6 条全红）**；⛔ **`favorite-rpc` 同款缺陷零触碰 ⇒ 记账 + 告知 owner**；🟢 **`article-rpc` 不受影响**（最低迁移版本 V8 > baseline 默认值 1 ⇒ 逐模块判定后排除，差点误修）；🟡 **「三进程并发」那个形态仍未验证** ⇒ 留一轮 |
| **B2** | ✅ **已消解（第二十四轮 L3）** —— 5 域**真 PG 16 双侧活体逐列对照**跑完（类型/长度/可空性/DEFAULT/索引/约束/触发器）⇒ article / follow **0 差异**；like / favorite 的 7 处差异**全是「RAG 更严」** ⇒ 登记为待签字候选；⚠️ **hot = N/A**（两侧都无数据库 ⇒ **不是「已对齐」**）⇒ 工具 `tools/difftest/ddl-parity.sh` ⇒ ⛔ favorite **只建库、不碰其代码** |
| **B3** | ~~**X-9** 收敛方案定性~~ | ✅ **已消解（第十六轮 · 本行原为过期快照）** —— hot / article 两份复制品**已改为调用共享层唯一真源** ⇒ 3 份收敛到只剩 **favorite 那份**（⛔ 永久残留 · 零权限）⇒ 🔴 探针实测又发现解析层缺 Go 的第二道闸 `maxBodyLen = 8 MiB` ⇒ ✅ 已在共享层 + hot 域补上 ⇒ ⚠️ **端到端今天不可达**（Go 的 MaxBytes 默认 1 MiB 全局生效 ⇒ 先拒成 413）⇒ 详见 [cross-domain.md §二·十三](cross-domain.md) |
| **B4** | ✅ **已消解（第二十轮）** —— 四条调度契约**逐条对拍通过**（`POST /v1/events` · 1s 周期 · 每轮 16 条 · 单条失败即 break）⇒ 三套守门 22 条（真 PG 16 14 条 + 真 Spring 上下文 5 条 + 真 HTTP 假 DC 3 条）+ 变异 14/14 全红 |

### C · 🏷 **待签字（登记类 —— 不升级为拍板）**

| # | 条目 | 性质 |
|---|---|---|
| **C1** | `SD-2026-10-25-CAND-10`（hot 域 `DRIFT-HOTAPI-031`：超长请求行 ⇒ RAG 400+HTML / Go 431+`text/plain`） | 框架限制 |
| **C2** | **T-7**（状态行缺 reason phrase，落点 Tomcat `Http11OutputBuffer`） | 框架限制 ⇒ 🏷 **已登记 `SD-2026-10-25-CAND-14`**（`docs/Intentional_Deviations/global/common-tails.md`）<br>Go 出处：Go 标准库 `net/http/server.go:1602-1605` 写 `StatusText(code)`；RAG 侧 Tomcat `Http11OutputBuffer.java:372-374` **刻意省略** |
| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无） | 刻意保留 ⇒ 🏷 **已登记 `SD-2026-10-25-CAND-16`**（`docs/Intentional_Deviations/users/_shared.md`）<br>⚠️ **先例指针订正（第二十六轮 L5）**：「同 message 域 `SD-2026-10-23-06`」**不成立**（那条讲的是**索引列数**，与 DEFAULT 无关）⇒ 真实同形态先例 = `message/rpc.md` 的 **`SD-2026-10-24-04`**（6 列 DEFAULT，已签）+ `comment/rpc.md` 的 **`SD-2026-10-24-CAND-01`**（9 处 `NOT NULL DEFAULT`，⏳ 未签）⇒ 教训 ⇒ **PIT-GLOBAL-022** |
| **C4** | `users_user.status` 可空性（Go 可空 / RAG `NOT NULL`） | RAG 更严 ⇒ 同 C3 族 ⇒ 🏷 **已登记 `SD-2026-10-25-CAND-17`**（`docs/Intentional_Deviations/users/_shared.md`）<br>Go 出处：`data_model.go:11` 有 `default:0` 但**无** `not null`；RAG `01_create_user.sql:39` 是 `NOT NULL` |
| **C5** | `DRIFT-COMMON-005` **差异②**（毫秒 vs 纳秒 ⇒ 必须动 `bindToCurrent` 签名） | 破坏性变更 |
| **C6** | **T-11**（trace **导出**链路整体缺失） | ✅ **已修**（第二十一 ~ 二十四轮）⇒ **不是误报** —— Go `core/trace/agent.go:126-127` 确实装全局 provider + `:123` `WithBatcher` ⇒ **Go 确实导出** ⇒ 曾是真漂移 ⇒ **登记保留**（不撤销）<br>⇒ 详见 [common-tails.md §一·T-11](common-tails.md) 与 [lanes/L1-delivery.md](lanes/L1-delivery.md) §九 / §十一 / §十二 |
| **C7** | **T-12**（「方法不匹配」出口 RAG **多带**一个 `Traceparent`） | 🔴 **出口层级差异** ⇒ **共享层改不动**（filter 阶段无法知道最终会不会落到方法不匹配）⇒ 🏷 **已登记 `SD-2026-10-25-CAND-15`**（`docs/Intentional_Deviations/global/common-tails.md`）<br>⚠️ **只影响「方法不匹配」这一个出口** —— 404 与正常响应**两侧都带** traceparent ⇒ 别读成「RAG 一律多一个」<br>⇒ 判据落在**注册点**（Go `rest/engine.go:113-115` + `patrouter.go:59-83`）⇒ 教训 ⇒ **PIT-GLOBAL-023** |

### D · 🔴 **真决策项（需拍板 —— 只有这 2 条）**

| # | 事项 | 为什么必须用户拍板 |
|---|---|---|
| ~~**D1**~~ | ✅ **已拍板并落地（第二十五轮，2026-10-25 21:57）** —— 用户选「**撤销 `SD-HOT-API-006`，修 T-6**」⇒ 共享层 `GoStyleJsonContentTypeAdvice` ⇒ JSON 响应带 `charset=utf-8` ⇒ 10 个 api 全生效 ⇒ 真 Tomcat 守门 3 条 + 变异 2/2 全红<br>⚠️ **顺带订正一处错账**：原判「`SD-HOT-API-006` 的『不可逐字节复刻』前提已被本仓证伪」**不成立** —— 真实原因是 **Tomcat 11 归一化** ⇒ 应用层拿不到那个空格 ⇒ **原始理由成立且更强** ⇒ 撤销的性质 = **纯用户决策** | — |
| ~~**D2**~~ | ⏸ **不再上报** —— 用户 **2026-10-24 已拍板**「`DRIFT-COMMON-005` **先记入台账，本轮不改**」（记录在 `docs/DRIFT/common/alignment-2026-10-23.md` §八·决策 2）⇒ **不重复占用拍板额度** | — |

### 🆕 七之三、第二十一~二十六轮新增项（**2026-10-25 06:40 快照之后**）

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
| **E8** | **C 类签字候选补齐**（T-7 / T-12 / users 8 处 DEFAULT / `users_user.status` 可空性） | `docs/Intentional_Deviations/` | ✅ **第二十六轮 L5 已登记 4 条**（`CAND-14..17`，全部「🏷 候选，建议签」⇒ **登记完就躺着**）⇒ ⛔ **C1 不重复登记**（`SD-2026-10-25-CAND-10` 已在 `hot/api.md`）⇒ **需拍板 0 条** |
| **E9** | 🔴 **T-6 的实现缺陷**：写的原始串**只在真 Tomcat 上**成立 ⇒ **MockMvc 路径**分叉 | `service/common`（`GoStyleJsonContentTypeAdvice`） | ✅ **第二十六轮已修**（改写**无空格**形态 = 各域自己 advice 会写的值 ⇒ 真 Tomcat 不变 · MockMvc 零变化 ⇒ **favorite 一行不用改**）⇒ 🆕 **门 4** 锁住这个等式 ⇒ **自曝错账 #3** ⇒ 教训 **PIT-GLOBAL-025** ⇒ 逐条见 [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §14.1 |
| **E10** | 🔴 **守门自身的缺陷**：`LikeFactDispatchContractGateTest` 的调度器**句柄永远拿不到** ⇒ 跨用例泄漏 ⇒ 偶发红 | `service/like/like-rpc/src/test/**`（测试文件） | ✅ **第二十六轮已修**（改用 `Dispatcher.runAsync`）⇒ ⚠️ 生产路径本来就是 `runAsync` ⇒ **无此缺陷**；⚠️ **`favorite-rpc` 的 `FavoriteFactDispatchScheduler` 用阻塞式 `run`** ⇒ 但触发点是 `ApplicationReadyEvent` ⇒ **不卡启动**，只是占住主线程 ⇒ **只记账 + 告知 owner** ⇒ 教训 **PIT-GLOBAL-024** ⇒ 逐条见 [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §14.2 |

### 🆕 七之四、第二十七轮（**2026-10-26**）—— 9 条「能直接动手」的未知项 → **6 条 lane 并行**

> 本轮把第二十六轮盘点出的 **9 条**「落点在 global 写权内、不需要拍板」的未知项
> 拆成 **6 条 lane（L6~L11）** 并行推进 ⇒ **全部生产代码零改动**（只有守门与脚本）。

| # | 条目 | 落点 | 状态 |
|---|---|---|---|
| **E11** | **L2 缺口①**：4 个 rpc 组件的 **X-5 轴真 server 守门**（admin / article / comment / like） | `service/{users/admin/admin-rpc,article/article-rpc,comment/comment-rpc,like/like-rpc}/src/test/**` | ✅ **第二十七轮 L6 + L7 已补齐**（各 3 用例 / 14 断言）⇒ X-5 轴覆盖 **8/9 个 rpc 组件** ⇒ ⛔ 只剩 `favorite-rpc`（零写权） |
| **E12** | **L2 缺口②**：slow threshold 守门从 1 个样板推广到其余 8 个 rpc 组件 | 8 个 rpc 组件的 `src/test/**` | ✅ **第二十七轮 L7 + L8 + L9 已补齐** ⇒ 逐域证据齐 ⇒ ⛔ 只剩 `favorite-rpc`（零写权） |
| **E13** | **L2 缺口⑤**：「主动打断 handler 线程」这一维的定性 | 只读 `service/common` | ✅ **第二十七轮 L9 已定性** = **框架限制成立**（RAG 不打断；**Go 也不杀 goroutine**）⇒ **L2 的 javadoc 自陈成立，不是新发现** ⇒ **销账，不补守门**（没有「应当被打断」的可观察行为可守） |
| **E14** | **L2 缺口⑦**：admin-rpc slow 守门前提（`GlobalOpenTelemetry` 在 refresh 前被替换）失效复评 | 只读 `service/common` | ✅ **第二十七轮 L6 已复评** ⇒ ① **Boot 的 OTel 自动配置不在依赖里**（pom 零命中 · m2 无 jar · autoconfigure jar 里零个 `*OpenTelemetry*` 类）⇒ 本仓是**自研**装配 ② 前提**侥幸成立** ⇒ ✅ **整合者已把前提变成断言**（7 份守门）⇒ 剩余加固建议 ⇒ [`common-tails.md` §一·T-15](common-tails.md) |
| **E15** | **L3 盲区①**：触发器函数体（`reject_*_mutation()`）逐条核对 | `tools/difftest/check-ddl-static.py` | ✅ **第二十七轮 L10 已闭合** ⇒ 脚本新增**结构化语义属性**比对（函数名 / 返回类型 / 参数 / volatility / timing / 事件集 / 粒度 / constraint / `WHEN`）⇒ **不逐字比 SQL 文本** ⇒ 人工逐条核对 3 对函数体（like / favorite ×2 / article 上下文）⇒ 三对**逐字节相同** ⇒ 🆕 顺带补上一个旧盲区（`pg_get_triggerdef` **查了却从不比对**） |
| **E16** | **L3 盲区②**：`task` / `message` 两域的 DDL runner 重建 | `tools/difftest/check-ddl-static.py` + `ddl-parity.sh` | ✅ **第二十七轮 L10 已闭合** ⇒ 两域改用 **runner 自管容器**（`ddl-task-pg` / `ddl-message-pg`）⇒ 退出码语义与其它域统一 ⇒ 全 9 域复跑**逐域与 L3 基线一致** |
| **E17** | **L4 边界①~③**：真 rpc 形态 / 迁移失败与回滚 + 同模块两副本 / 多轮压力与随机延迟 | `tools/difftest/flyway*` | ✅ **第二十七轮 L11 已闭合** ⇒ ① **真 rpc 进程并发同库**（`follow ‖ task ‖ message` 三个真 `*RpcApplication` 进程 + **advisory-lock 屏障**证明三者**同时**卡在同一把迁移锁上才放锁 ⇒ **不是墙钟凑巧**）② **迁移中途失败零残留** + 同模块两副本账本 **2 行（不是 4）** ③ **N 轮 + 0~4000ms 随机启动偏移**全绿 ⇒ **反向注入**（两模块共用一本账本）⇒ **EXIT=1** 红在「表没建出来 + 迁移记录互相污染」⇒ 正常跑 **EXIT=0** ⇒ ⚠️ 只覆盖 **3/7 域** · 只验**迁移面** · 判定器**不进 `mvn test`** |
| **E19** | 🟢 **第二十七轮全仓回归** | `align/global` @ 第二十七轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2263 条 · 0 failures / 0 errors / 0 skipped**（上一轮 2235 ⇒ **净增 28 条** = X-5 轴 4×3 + slow 轴 8×2）⇒ 跑前删过全部旧 surefire XML ⇒ 条数无历史残留污染 |
| **E18** | 🔴 **整合者修的两处跨 lane 守门缺陷** | 7 份 slow 轴守门 | ✅ **第二十七轮整合阶段已修** ⇒ ① **慢轴守门缺预热** ⇒ 绿是**执行顺序的副产品** ② **慢轴守门不断言前提成立** ⇒ 将来会以「找不到 span」变红 ⇒ 连带 **自曝错账 #4**（OTel 的 `set()` 存的是包装器 ⇒ 引用相等类断言恒红）⇒ 3 条新坑 **PIT-GLOBAL-026/027/028** |

### 🆕 七之五、第二十八轮（**2026-10-26**）—— **生产启动阻塞**修复 + **两个守门缺口**闭合

> **触发**：Jason 追问「不是已经清空未知了嘛，怎么又有」⇒ 查证发现 §七之二的盘点口径
> **漏了第五个来源**（各域台账的「域外/跨域移交/交主会话」段，63 行 / 14 份文件）
> ⇒ 本轮把那批**挂着不动 = 永久漂移**的条目真正修掉。

| # | 条目 | 落点 | 状态 |
|---|---|---|---|
| **E20** | 🔴 **N6**：`discovery://<name>` 两斜杠 ⇒ devh 要求 `path` 长度 > 1 ⇒ **真 fat jar 起不来**（连 Flyway 都跑不到） | 7 个 rpc 模块的 `application.yml` | ✅ **已修**（L12 修 5 处 + L13 修 2 处）⇒ 🟢 3 个 `XxxRpcProductionAddressTest`（读**生产** yaml · 遍历**全部** client · **真调 devh 消费点**：生产不抛 + 两斜杠**必须**抛）⇒ ⛔ **favorite 2 处零触碰** |
| **E21** | 🔴 **N7**：`grpc-inprocess` 是 `test` scope ⇒ 运行时 classpath 上整个 artifact 消失 | article / like / task 的 `pom.xml` | ✅ **已修** ⇒ 🟢 产物层守门（解包真 fat jar 查 `BOOT-INF/lib/`）⇒ 🟢 独立实测：修后 `grpc-inprocess-1.63.0.jar` **在** `BOOT-INF/lib/`、修前不在 ⇒ ⛔ favorite 1 处零触碰 |
| **E22** | 🔴 **N8**：`task-rpc` / `hot-rpc` 的 jar **没有 `Main-Class`** | 2 个 `pom.xml` | ✅ **已修**（L13 + L14）⇒ 🔴 **L11 漏报了 `hot-rpc`**（逐个解包 10 个 rpc 才看出来）⇒ **根因 = 守门缺口** ⇒ ✅ L15 已把 rpc 纳入 jar 门禁 |
| **E23** | 🔴 **守门缺口①**：`check-jar-executable.sh` 只扫 `*-api` ⇒ **10 个 rpc 模块的产物可执行性从来没被守门查过** | `tools/difftest/check-jar-executable.sh` | ✅ **已闭合**（L15）⇒ 枚举 10 → **20** 个模块 ⇒ 实测 **20/20 全绿** ⇒ 教训 **PIT-GLOBAL-030** |
| **E24** | 🔴 **守门缺口②**：`check-grpc-runtime-classpath.py` 扫到 `target/classes` ⇒ 同一违规算两遍 ⇒ **门禁恒红**（实测 `EXIT=1`，而源码树只有 9 处违规） | `tools/difftest/check-grpc-runtime-classpath.py` | ✅ **已闭合**（L15）⇒ **存量缺陷**（脚本是 2026-10-24 建的）⇒ 修后 **EXIT=0** ⇒ 教训 **PIT-GLOBAL-029** |
| **E25** | 🔴 **盘点口径漏了第五个来源** | `global/_README.md` §七之二 | ✅ **已订正** ⇒ 口径 = ① 四桶（域内）＋ ② 各域台账的域外交接段 ⇒ 连带订正：第 27 轮的 N6/N7 是**重发现**（follow 域 2026-10-24 已写明并点名同样 5 个模块）⇒ **真新发现只有 N8 / T-14 / T-15** |
| **E26** | 🔴 **整合者修的守门缺陷**：`HotRpcJarExecutableWiringTest` 的一条断言**没剥 XML 注释** ⇒ 而 pom 的注释里就写着那个字符串 ⇒ **M1 变异时它居然是绿的** ⇒ 恒真 | `service/hot/hot-rpc/src/test/**` | ✅ **已修** ⇒ 三条涉及字面量的断言全部先 `stripXmlComments()` ⇒ 复跑 M1 那条也红 ⇒ 教训 **PIT-GLOBAL-031** |
| **E27** | 🟢 **hot 域台账的过期快照** | `docs/DRIFT/hot/{api,rpc}.md` | ✅ **已订正** ⇒ D14（nosniff，`HttpMiddleware.java:692` 已实现）· D25（T-13 已闭合）· D27（**已签字偏离** `SD-2026-10-25-01`）· D21/D22/D23（第十三轮已落地 10/10）⇒ **hot 域内未闭合项清单为空** |
| **E28** | 🔵 **favorite 侧复查**（global 对 favorite **零写权** ⇒ **只查不动**） | `cross-domain.md` N6/N7 + `tools/difftest/baselines/grpc-runtime-classpath.txt` | ✅ **查完 + 订正台账** ⇒ ① 🟢 `grpc-inprocess` scope：**favorite owner 自己已修**（`align/favorite` @ `e76ea8e` 第十四轮 `test`→`runtime`，pom 里留了 devh `GrpcClientAutoConfiguration:160` 实测论证）⇒ 我台账上「favorite 1 处」**对它已过期** ⇒ 已订正（本分支未合并 favorite 提交，故 baseline 该行仍在 ⇒ 合并后脚本提示「已过期」⇒ 届时删）② 🔴 **两斜杠 2 处：owner 尚未修**（`align/favorite` @ `fbac7fc` 的 `application.yml:88,94` **仍是两斜杠**）⇒ **修法已备好**（三斜杠 `discovery:///<name>` ⇒ devh 取 `path.substring(1)` **仍是服务名** ⇒ 服务名一个字不改）⇒ **交 favorite owner** ⇒ baseline 注释已按两类分别写明<br>📨 **2026-10-26 已直接通知 favorite 域会话**（`mvs_96b399362f7b4f05b433a2f1acdb1498`，含缺陷证据 + 三斜杠修法 + 样例位置 + 「global 零写权、默认不动」边界）⇒ **不是只记账** ⇒ 避免「挂着不动 = 永久漂移」<br>🟢 **2026-10-26 第三十轮收尾复查：owner 已修** —— `align/favorite` @ **`0a4d2a8`**（第十七轮）⇒ `application.yml` 的 `discovery:///user-rpc` + `discovery:///article-rpc` **已是三斜杠**（实测 grep 确认）⇒ **本条从「待修」改为「owner 已修，待合回」** ⇒ ⚠️ **global worktree 里的 favorite 文件仍是旧的**（该提交未合回本分支）⇒ `check-grpc-runtime-classpath.py` 的 baseline **那 3 行暂时仍要保留**（合并后脚本会提示「已过期」⇒ 届时删） |
| **E29** | 🟢 **第二十八轮全仓回归** | `align/global` @ 第二十八轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2301 条 · 0 failures / 0 errors / 0 skipped**（上轮 2263 ⇒ **净增 38 条**）⇒ **账对得上**：本轮新增 13 个守门类 / **44 条**用例中，**在 `mvn test` 阶段跑的正好 38 条**；余 **6 条 = 2 个 `*FatJarArtifactGateIT`**（`TaskRpcFatJarArtifactGateIT` 4 + `HotRpcFatJarArtifactGateIT` 2）绑在 **verify** 阶段 ⇒ `mvn test` 不执行 ⇒ **逐个守门类核过**（10 个 ✅ 已跑 / 2 个 ⏭️ verify 阶段）⇒ 跑前删过全部旧 surefire XML ⇒ **模块汇总行与 XML 求和两个口径完全一致（都是 2301）** ⇒ ⚠️ 8 条 `Surefire is going to kill self fork JVM` 是 **surefire fork 退出超时噪声**（users 域 4 模块 · 历史就有 · **不是测试失败**）⇒ ⚠️ **第一次回归 BUILD FAILURE 是假红**（构建竞态 + 环境时序 ⇒ 单独复跑全绿）⇒ 教训 **PIT-GLOBAL-032** |
| **E30** | 🔴 **RPC 超时文案不对齐**（**第三十轮已修**） | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java` | ✅ **已修**（第三十轮 · `align/global` 持 `service/common` 单写权）⇒ 🔴 **证据强度比原记录高得多**：这条漂移被**三个域各自独立**记过 —— `comment/rpc.md` U10 · `users/user/rpc.md` **V-007**（「仍成立，但不在本组件判定权内」）· `users/admin/rpc.md` 的**活体测量** ⇒ **8 个 rpc 模块的 `rpc-timeout: 2s` ⇒ 这条路径线上真实生效** ⇒ 客户端今天收到的就是错的那串。⇒ **两处都改**（Go 真源 `service/common/observability/rpc.go:68-71`：`err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ description = `context.DeadlineExceeded.Error()` 的字面量 = **`context deadline exceeded`**；遥测侧 `MarkTimeout` 取 `err.Error()`）：① wire description → `context deadline exceeded` ② 遥测 `sea.biz_msg` → Go 的 status error 格式。⇒ 🟢 **守门 2 条全绿**（`RpcInterceptorTimeoutDescriptionGateTest`：**真 Netty server + 真 channel + 真客户端 + 真 OTel SDK/InMemorySpanExporter** ⇒ 断言的是**网络上真正传过来的那串字节**）⇒ 🟢 **双侧活体探针 PASS**（`tools/semantic-probe/rpc-timeout/`：Go 半边**直接引用冻结仓真源**（`replace sea-try-go => <冻结仓>`）+ 真 gRPC server，RAG 半边跑**生产类** ⇒ `timeout_wire` **两侧逐字节一致**）⇒ 🟢 **变异 2 条全红且红在目标断言上**（M1 改回旧串 ⇒ 守门 2/2 红 + **探针 EXIT=1**；M2 只改遥测段 ⇒ **只红遥测那条**、wire 那条仍绿）⇒ 🔴 **边界：修完后剩下的差异恰好等于「表述差异 C-1」**（见 E36）⇒ **零 favorite 改动**<br>📨 **2026-10-26 已直接通知三个域的 owner 会话**（global **不写别域台账** ⇒ 只能记账 + 通知）：`comment` 会话 `mvs_b59aa347fa7a451cba5188e873839d15`（回写 U10）· `users/user` 会话 `mvs_46eefb86d35a4ff29b182b6acf433a8c`（回写 **V-007** + CASE-31 + **行号引用过期提醒**）· `users/admin` 会话 `mvs_855d65b508d84045b4b898dbe0ff79b1`（回写 §1.3 档位 B **并保留原实测值** + 行号引用过期提醒）⇒ 三条消息均含：落点文件 + 守门类名 + 探针目录 + **C-1 边界（请勿为对齐 code 名去改 grpc-java 命名）** + 「global 不代替别域改台账」边界 ⇒ **不是只记账** ⇒ 避免「挂着不动 = 永久漂移」 |
| **E31** | 🔴 **`DRIFT-FOLLOW-006` 未被 global 跟踪** | `follow-rpc` 指标出口（落点 = 全仓 rpc 层统一方案） | 🔴 5 个 `follow_rpc_*` 指标**无任何抓取出口**（Go 有 DevServer `/metrics`，RAG 的 follow-rpc 是纯 gRPC 进程）⇒ **可观察（抓取侧）** ⇒ 用户 **2026-10-24** 已定「**本轮不落地，转全仓 rpc 层统一方案交主会话拍板**」⇒ 🔴 **global 台账 0 命中 ⇒ 球在主会话却没人接** ⇒ **真决策项**（不是登记类 ⇒ 与 CAND 系列不同） |
| **E32** | 🔵 **AGENTS.md §0 users 行的过期快照** | `AGENTS.md:20` | 🔴 原写「⚠️ admin-rpc `status=0L` 未修」⇒ ✅ **users/admin/rpc.md 已备好双侧活体对拍 + 独立 Go 探针**（GORM 造的是 `**int64` **双指针** ⇒ `database/sql` 只对**单指针** dest 报 unsupported ⇒ NULL 读成 **0**）⇒ **两侧都是 `status:0` ⇒ 不是漂移** ⇒ ✅ **已撤销该行**（该 worker 无权改共享文件 ⇒ **卡了 3 天** ⇒ 整合者补）⇒ 连带：同段里 users/user 侧「与 Go 相反」也是**过期快照**（`DRIFT-USERRPC-V-001` **2026-10-24 已闭环** ⇒ 两侧都登录成功） |

| **E33** | 🔵 **T-14**：`RpcInterceptor` 的 javadoc 把「两层补救」**并列**陈述 | `RpcInterceptor.java`（javadoc） | ✅ **第三十轮已修**（零行为改动）⇒ 改写为「**两条互不覆盖的路径**」：① **gRPC 路径只有第 ① 层**（`DeadlineState` 感知，**不是打断**）② **第 ② 层（interrupt）仅适用 `handle(...)` 这个非 gRPC 入口** ⇒ 并把「业务 handler 必须实现的四种机制」逐条标注**适用路径** ⇒ 附**逐行取证**（`workerFuture.cancel(true)` 全文**仅两处**且都在 `handle(...)` 内；gRPC 的 `onCancel` 里那个 `cancel(false)` 取消的是**超时调度任务**、**不是**业务线程）⇒ 修法与 T-14 登记时的「修法」栏一致 |
| **E34** | 🟡 **T-15**：「全局 OTel 由测试预置」这个前提**侥幸成立** | 7 份 slow 轴守门 | ✅ **第三十轮闭合其 ③**（各加一行 `rag.test.gate.context-key=<域>-slow-threshold`）⇒ 🔴 **实测出真实脆弱点**：Spring 的 context cache key **包含 `properties`**、但 **`@DynamicPropertySource` 的值不进 key** ⇒ 只要本类与同模块另一个 `@SpringBootTest` 的 `properties` **恰好相同** ⇒ **共享同一个 Spring 上下文** ⇒ 本类 `@BeforeAll` 装的 OTel SDK **装不进那个已存在的上下文** ⇒ 上下文创建时拦截器取走的是**别的测试**的 `Tracer` ⇒ 本类 exporter 收不到 span ⇒ 用例以「**找不到 span**」这种**看不懂的形态**变红 ⇒ **今天没真的共享**（实测 7 个模块内各 `@SpringBootTest` 的 `classes` 相同但 `properties` 行数各异 ⇒ 键必不同）⇒ **这正是「侥幸成立」** ⇒ ③ 把侥幸变成**结构保证** ⇒ ⏸ **② 不做**（**防不住目标失效形态**：缓存上下文不会重新 refresh ⇒ initializer 不会重跑 ⇒ 真正的防线是 ③）⇒ ⏸ **④ 不做**（**越界**：改法横跨 10 个域且**会碰 favorite**（global 零写权）⇒ 属架构级改造，须专门一轮 + 明确授权） |
| **E35** | 🟢 **第三个 semantic probe**：`tools/semantic-probe/rpc-timeout/` | `tools/semantic-probe/rpc-timeout/` | ✅ **已落地并 PASS** ⇒ **Go 半边直接 `replace` 引用冻结仓**（不复制、不改写冻结仓一个字节 ⇒ 跑的就是真源）+ **真 gRPC server**（grpc-go）+ 冻结仓**真实生成**的 `pb.LikeService` ⇒ **RAG 半边跑生产类** + **真 Netty server** ⇒ 两个用例（`timeout_wire` / `timeout_telemetry`）逐键比对 ⇒ **PASS** ⇒ ⚠️ **三条踩坑纪律已写进脚本头**：① 冻结仓路径**机器相关** ⇒ 由脚本运行时生成 `go.mod`（仓库里不提交任何绝对路径）② `CGO_ENABLED=0`（本机无 Xcode 头文件 ⇒ cgo 报 `'stdlib.h' file not found`）③ **Go 的 select 有三个分支** ⇒ 探针的 handler **必须永不返回**（若在 `ctx.Done()` 时返回 ⇒ done 分支与 timeout 分支同时就绪 ⇒ Go 随机选 ⇒ 客户端拿到 `code=Unknown` ⇒ **探针变成随机**） |
| **E36** | 🔴 **C-1 边界**（E30 修完后剩下的差异） | `docs/Intentional_Deviations/common/_signed-deviations.md` C 类 C-1 | 🔴 **已登记 ⇒ 不得动** —— 修 E30 时守门抓出**更深一层**：RAG 的 `goStyleStatusMessage` 用 grpc-java 的 `Code.name()`（`DEADLINE_EXCEEDED`），Go 用 grpc-go 的 `codes.Code.String()`（`DeadlineExceeded`，CamelCase，见 `codes/code_string.go:31-59`）⇒ **全部 17 个码都差一个大小写形态** ⇒ 🔴 **但 C-1 已把它登记为「表述差异」并写明「仅常量表示不同，**不做臆造映射**」** ⇒ 按「已登记偏离不得顺手修正回旧行为」⇒ **本轮不改** ⇒ ⇒ **E30 修完后，剩下的差异恰好等于 C-1** ⇒ 🟢 **守门刻意把 C-1 的形态钉住**（断言 code 段 = `DEADLINE_EXCEEDED`）⇒ **防止后人把它当成新漂移去「修」** |
| **E39** | 🟢 **第三十一轮全仓回归** | `align/global` @ 第三十一轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2305 条 · 0 failures / 0 errors / 0 skipped**（上轮 2303 ⇒ **净增 2 条** = E38 守门 `RpcInterceptorClientDeadlineGateTest` 的 2 条 ⇒ **账对得上**）⇒ 🟢 **两口径一致**（模块级汇总 29 个模块 = **2305**；`TEST-*.xml` 305 份求和 = **2305**）⇒ 耗时 **19:08 min**<br>🔴 **第一次回归是 BUILD FAILURE（2 errors）⇒ 已定性为「假红」**：唯一失败 `UserRpcGrpcInterceptorWiringTest`（2 errors）⇒ cause 链末端 `BindException: Address already in use`（绑 33060）⇒ **单独复跑 ×3 全绿** ⇒ ⇒ **与本轮改动无关**（改动只在 `interceptCall` 内执行，而 bind 发生在 server 启动期；且第 30 轮同一测试绿）⇒ 三步定性法 ⇒ 新坑 **PIT-GLOBAL-038**<br>⚠️ **统计口径修正**：失败模块的汇总行前缀是 `[ERROR]` ⇒ 只 grep `[INFO]` 会**漏掉整模块的用例数**（本轮 2176 vs 2305，差 129 = 失败模块）⇒ **两个口径必须同时看**（差值本身就是失败模块规模）<br>🟢 **三门禁 EXIT=0**（grpc-classpath 无新增违规 + favorite 3 条 baseline 已知未修 · jar 可执行 **20/20** · lane 写权 favorite 零写权）· **AGENTS.md 32,749 B / 32,768**（预算内，余 19 B） |
| **E37** | 🟢 **第三十轮全仓回归** | `align/global` @ 第三十轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2303 条 · 0 failures / 0 errors / 0 skipped**（上轮 2301 ⇒ **净增 2 条** = E30 守门 `RpcInterceptorTimeoutDescriptionGateTest` 的 2 条 ⇒ **账对得上**）⇒ 🟢 **两口径一致**（模块级汇总行 29 条 = **2303**；`TEST-*.xml` 304 份求和 = **2303** ⇒ 完全相同）⇒ 跑前删过全部旧 surefire XML（243 份 ⇒ 0）⇒ 无历史残留污染 ⇒ 耗时 **20:52 min** ⇒ ⚠️ 期间**没有在别的进程里跑同一 reactor 的构建**（守门脚本只读 pom / jar / 文件树 ⇒ 不碰 maven；探针内部会 `mvn compile` ⇒ **特意等回归结束才复跑**）⇒ 教训 **PIT-GLOBAL-032** 的正面用法 ⇒ 🟢 **探针复跑 PASS**（回归后单独复跑 `tools/semantic-probe/rpc-timeout/` ⇒ `timeout_wire` 两侧逐字节一致 · `timeout_telemetry` = 预期差异 C-1）⇒ 🟢 **三门禁 EXIT=0**（`check-grpc-runtime-classpath.py` ⇒ 无新增违规 + favorite 3 条 baseline 已知未修 · `check-jar-executable.sh` ⇒ **20/20** 可执行 fat jar · `check-global-lane-ownership.py` ⇒ 在跑 0 条 / 已合回 15 条 / favorite 零写权）|
| **E38** | 🔴 **超时未继承客户端 deadline**（**第三十轮末新发现 · 落点 `service/common` ⇒ global 写权内**） | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java`（`deadlineEpochMs` 的计算 + 超时调度器延时） | 🔴 **已定性为 CONFIRMED SEMANTIC MISMATCH + 有运行期实证**（不是未知）—— 2026-10-26 由 **users/admin 域 owner** 主动提出并附实测 ⇒ 🔴 **global 台账此前 0 命中 ⇒ 从未跟踪**（⇒ 第五来源口径漏了这个措辞变体，见下方「口径补充」）<br>**Go 侧**：`rpc.go:38` `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ **`ctx` 携带客户端 deadline** ⇒ Go 标准库语义 ⇒ **有效期限 = min(客户端 deadline, now+2s)**（客户端 1s ⇒ 服务端 ctx 1s 到点 ⇒ handler 的 ctx 被取消 ⇒ **GORM 同步取消 PG 查询**）<br>**RAG 侧**：`RpcInterceptor.java:214-215` `deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis()` ⇒ **只算服务端自己的 2s** ⇒ 客户端 deadline 完全不参与<br>**运行期实证**（users/admin 实测 · 客户端 deadline 1000ms 档）：Go `Δ_start=+1005ms / Δ_end=+0ms`（**随客户端一起死**）vs RAG `Δ_start=+2117ms / Δ_end=+1111ms`（**客户端已放弃，PG 查询仍占着 1.1s**）⇒ ⇒ **可观察（抓取侧）** ⇒ 🔴 **现有 HTTP 对拍工具在结构上抓不到这条**（`exp-difftest-grpc-admin-errors.sh` 用默认 deadline ⇒ 没加客户端 deadline 档）⇒ **不得以「对拍全绿」当它已闭环**<br>**修法分两层（不可只做一半）**：① **纯 deadline 计算**（可做）：`deadlineEpochMs` 与超时调度器延时都改 `min(客户端 deadline, now+timeout)`（grpc-java 服务端 `Context.current().getDeadline()` 拿得到客户端 deadline）⇒ 让 `DeadlineState` 的感知时刻与超时 close 时刻正确 ⚠️ **② 但 PG 查询仍会跑满** —— Go 的 ctx cancel ⇒ GORM 取消查询；grpc-java 的 `ServerCall.close()` **不取消业务线程**（**与 T-14 同一根因**：框架限制）⇒ 真正对齐需「handler 挪到受控 executor + 超时 interrupt」⇒ **架构级改动，影响 10 个 rpc 组件** ⇒ ⇒ **只做 ① 只能修一半 ⇒ 必须把 ① ② 一起评估**<br>⚠️ **编号区分**（users/admin owner 提醒）：本条 = **`UNRESOLVED-ARPC-002`**（是否 min 客户端 deadline）≠ **`DRIFT-ARPC-002`**（`UpdateUserResp.success` 的 wire 差异，**已修**）⇒ 后续沟通**必须带全前缀**<br>🟡 **状态**：已登记 ⇒ **落点在 global 写权内 ⇒ 属 global 的 A 类 backlog**（不需拍板）⇒ 但 ② 的架构级改造 + 需要**新的「客户端 deadline 档」双侧活体探针**（现有工具抓不到）⇒ **须专门一轮**<br>✅ **2026-10-26 Jason 拍板**：① **下一轮做**（纯 deadline 计算 + 新写「客户端 deadline 档」双侧活体探针）· ② **另立项**（架构级：handler 挪受控 executor + 超时 interrupt · 影响 10 个 rpc 组件）⇒ ⇒ **第 ① 层不销本条**（只修一半）⇒ 本条在 ② 落地前保持「CONFIRMED SEMANTIC MISMATCH 定性不变」<br>🟢 **第三十一轮：① 已落地** ⇒ 落点 `RpcInterceptor.java`（`service/common` 单写权）⇒ 改动两处：① `deadlineEpochMs` 与超时调度器延时都改用 **`effectiveTimeoutMs = min(服务端预算, 客户端 deadline 剩余)`** ② 新增 `clientDeadlineRemainingMs()`（从 `io.grpc.Context.current().getDeadline()` 取；**三个约定**：无 deadline ⇒ `Long.MAX_VALUE` · 已过期 ⇒ `0`（不是负数）· 取值抛异常 ⇒ `Long.MAX_VALUE`）⇒ 🟢 **grpc-java 取值点已实测**（`interceptCall` 里 `Context.current().getDeadline()` 可用 ⇒ 客户端 1s ⇒ 服务端读到 **932ms** 剩余；headers 里 `grpc-timeout: 936027u`）⇒ 🟢 **双侧活体探针**：`client_deadline_ctx` 用例 —— 修前 **Go=client_100ms vs RAG=server_own**（漂移坐实，探针 EXIT=1）⇒ 修后 **两侧一致**（EXIT=0）⇒ 🟢 **守门 2 条**（`RpcInterceptorClientDeadlineGateTest`：真 Netty server + 真 channel + 真客户端带 deadline ⇒ 断言 `DeadlineState.remainingMillis()` 落在**客户端那一档**；另一条钉「客户端没带 deadline ⇒ 仍是服务端预算」）⇒ 🟢 **变异 1 条**（`effectiveTimeoutMs` 退回 `serverBudgetMs` ⇒ 守门 **1 条红且红在目标断言**（另一条仍绿 ⇒ 精确命中）+ **探针 EXIT=1**）⇒ 🔴 **修完后仍不等于 Go 全语义** —— 第 ② 层（在途 PG 查询随客户端放弃而取消）**未做** ⇒ ⇒ **本条只是「期限计算」这一层对齐**，`UNRESOLVED-ARPC-002` 的**定性不变**<br>🟢 **第三十二轮末：第①层的收益被重新量到 —— `DeadlineState` 的 deadline 现在 = `min(客户端, 服务端)` ⇒ ⇒ **SQL 超时上限也继承了客户端 deadline**（B-1 缓解 `DeadlineQueryTimeoutInjector` 读的就是它）⇒ ⇒ 原本「服务端 2s − 客户端 deadline」的窗口被压缩到 **≤1s**<br>🔴 **第②层同时被两处修正**（量级被高估 + 修法描述不完整）⇒ ⇒ **另见 E46**｜<br>🟡 **第三十三轮订正（users/admin owner 提出 ⇒ 复核后「属实」）**：E38 原文两处写「需要**新的**『客户端 deadline 档』双侧活体探针（现有工具抓不到）」⇒ 🔴 **该前提不成立** —— `tools/difftest/exp-grpc-admin-deadline-probe.sh`（**2026-10-24 就有**，17KB）**就是**那个探针⇒ 它**已带客户端 deadline 档**（短档 `DEADLINE_SHORT=1000`ms vs 对照档 `DEADLINE_LONG=5000`ms）+ 判定 `exit 1 ⟺ 短档下两侧 PG 查询存活时长差异 > 1s` + 配套 `tools/difftest/GrpcAdminDeadlineProbe.java`⇒ 且 E38 单元里的「运行期实证」那张表（客户端 **1000ms** 档：Go `Δ_end=+0ms` vs RAG `Δ_end=+1111ms`）**正是它跑出来的**⇒ ⇒ **真正的缺口不是「缺探针」，而是「探针没进常规编排」**（`tools/difftest/run.sh` 与 `diff.py` **都不调它** ⇒ 它是**手动取证工具** ⇒ 改坏了不会被自动抓到）⇒ 🟡 **本轮处置**：措辞已订正（「须新写探针」→「探针已存在，缺口是未进编排」）⇒ 编排缺口**另立 E48**（落点 `tools/difftest/run.sh` ⇒ global 写权内 ⇒ A 类 · 不需拍板） |
> 🟢 **第五来源的口径补充（第三十轮末实测）** —— 第 29 轮的第五来源关键词集（域外 / 跨域移交 / 交主会话）**漏了一种措辞**：users/admin 的 `UNRESOLVED-ARPC-002` 写的是「**修法落在 `service/common`（不继承入站 deadline），不在本域**，故本 worker **无权改**」⇒ **「不在本域」+「无权改」也是域外交接的表述** ⇒ ⇒ **盘点时不能只 grep 那几个固定词** ⇒ 🔴 **纪律：第五来源要按「落点是否在本域写权内」逐条判，而不是按措辞 grep**（措辞只是线索） |
> 📊 **2026-10-26 第二十九轮盘点结论**（**核对日期 = 当日**）—— 口径 = ① global 四桶（§七之二 A/B/C/D + §七之三 ~ §七之五）＋ ② 各域台账的「域外 / 跨域移交 / 交主会话」段 ＋ ③ `common` 域台账 §十二·二 的 10 条「外部域尾巴」。
>
> | 桶 | 条数 | 内容 |
> |---|---|---|
> | **A · 可直接修**（落点在 global 写权内 · **不需拍板**） | **4 ⇒ 第三十轮已修 3 · 第三十轮末 +1** | **E30**（RPC 超时文案 ⇒ ✅ **已修**，含双侧活体探针 + 变异 2 条）· **T-14**（javadoc「两层补救并列」误述 ⇒ ✅ **已修**，零行为改动）· **T-15**（⇒ ✅ **③ 已做**：7 份 slow 守门加专属上下文缓存键；⏸ **② ④ 不做**，理由见 E34）· **E32**（AGENTS.md users 行 ⇒ ✅ **已改**）⇒ 前三条落点全在 `service/common` ⇒ **global 单写权**<br>🆕 **E38**（超时未继承客户端 deadline ⇒ 落点同在 `service/common` ⇒ **也是 global 单写权** ⇒ **已定性 CONFIRMED SEMANTIC MISMATCH + 有运行期实证** ⇒ **须专门一轮**：修法分两层，① 可做 · ② 是架构级且影响 10 个 rpc 组件） |
> | **D · 真决策（需拍板）** | **2** | **E31**（FOLLOW-006 指标出口）+ `LikeAuthorityEndpointConfig`（wire 面变更 · 沿用上轮未回复） |
> | **C · 登记类**（登记完就躺着 · **不升级为拍板**） | **5** | `SD-2026-10-25-CAND-10`（hot 超长请求行 400/431 · 框架限制）/ `-14`（T-7 状态行 reason phrase · Tomcat 层）/ `-15`（T-12 方法不匹配出口多带 `Traceparent` · 共享层改不动）/ `-16`（users 8 处 `DEFAULT CURRENT_TIMESTAMP` · 刻意保留）/ `-17`（`users_user.status` 可空性 · RAG 更严）⇒ ⚠️ `DRIFT-COMMON-005` 差异②（毫秒 vs 纳秒）⇒ 用户 2026-10-24 已拍板「先记账不改」⇒ **不重复上报** |
> | **未知项** | **0 条属 global** | ⚠️ 最大的未知池在 **common 域自己**（**27 条编号未修 + 5 项未编号** + §七 未覆盖范围：`protection/` 13 文件 + `http/` 2 文件 + `HttpMiddleware` 758 行 + `MqConsumer` 145 行 + `persistence/` 2 文件 + **`align/message` 新加的 7 个类从未被任何台账对照过**）⇒ 🔴 **但该台账 §十二 原文写明「用户 2026-10-25 明确『我自己修』」⇒ 不是 global 的 backlog** ⇒ 🔴 **且全仓无 `align/common` 分支 / 工作树 ⇒ 若 Jason 不做就无人做**（同款「挂着不动 = 永久漂移」风险） |
>
> ⚠️ **第五来源本轮逐条查证的结论**：10 份域台账里「域外 / 跨域 / 交主会话」共 **48 行命中** ⇒ **其中 46 行是过期快照**（`users/admin/api.md` §5 四条全已修 · `hot` 的 D14/D25/D27/HOTAPI-014/Tomcat 三旋钮全闭合 · `follow` 的 N6/N7 已修 · `message` 的 V-030「6 个 api 的 jar 不可执行」已修 —— **实测 20/20 模块 jar 全可执行**）⇒ **真正还开着的只有 `comment` 的 U10（= E30）与 `follow` 的 U10/FOLLOW-006（= E31）** ⇒ ⇒ **第五来源的价值已被证明：它确实能翻出东西，但它翻出来的多数是「别人修完了但没人销账」的快照** ⇒ 🔴 **新纪律：跨域条目被别域修完后，须回写销账**（否则下轮又要重新核一遍）。<br>🟢 **第三十轮订正**：**`comment` 的 U10 已修**（= E30）⇒ 第五来源里**真正还开着的只剩 `follow` 的 U10/FOLLOW-006（= E31）** ⇒ ⇒ **第五来源从「48 行命中 / 46 行过期快照 / 2 条真开着」收窄成「1 条真开着」**。

### ⛔ favorite 相关的两条（**不可动，仅记账**）
- **X-4 favorite 半边** —— RAG 多出 10 处 Go 没有的 `communityfact` 调用 ⇒ 删掉它们 = 改 favorite ⇒ 永久阻塞
- **X-9 favorite 那份复制品** —— 「Go 是否读 body」判据的第 1 份生产复制品 ⇒ 永久留存
- ⇒ ⚠️ 但**共享层改动会影响 favorite 的可观察行为** ⇒ 每一次共享层改动**必须记账并告知该域 owner**（§七 纪律 1b）

### 📌 只记不修（**性能非行为**）

- users 域缺 Go 的 **3 条非唯一索引**（AGENTS.md §0 DDL 差异第 2 条）⇒ **性能非行为** ⇒ 不修


### 🆕 七之六、第三十二轮（**2026-10-26**）—— **共享复核工具的三处缺陷**：第 1 层零去噪 + 输出不可复现 + 自检分支相关

> **触发**：**users/user 域 owner** 反馈「新版丢了去噪规则」⇒ 按「**先复核前提再登记**」（AGENTS.md §5.3b）复核 ⇒ 🔴 **它的归因不成立**：去噪**一直在跑**（第 2/4/5/6 层共 **255+ 条**）⇒ 真正缺陷是 —— 🔴 **第 1 层一条去噪都没有** ⇒ 而台账里大量用**省略写法**指代文件 ⇒ 全被误判成「引用了不存在的文件」⇒ **信号被噪声淹没**（users-user 域实测：60 处「不一致」里 **59 处**是这类）。
> ⇒ 该工具是**跨域共用**（task / users-user / users-admin 三域同一路径）⇒ **global 收口**（改了要记账 + 告知相关域）。

| # | 条目 | 落点 | 状态 |
|---|---|---|---|
| **E40** | 🔴 **第 1 层零去噪**（其它层 255+ 条去噪） | `tools/check-ledger-selfcheck.py` | ✅ **已修** —— 第 1 层新增 **N8~N13 六条**去噪规则：**N8** 含 `...` 的省略写法 · **N9** `service/<Name>.java` 裸文件名 · **N10** 短路径（省了 `src/main/java/com/rag/<pkg>/` 前缀 ⇒ **靠「basename 相同 + 中段连续相邻」落到真实文件**判定）· **N11** Go 侧路径（在 **Go 真源仓**存在 ⇒ 本仓本就不该有）· **N12** 顶层目录不在本仓（`service/<X>/` 不存在 ⇒ **逻辑上不可能是本仓任何形态的路径** ⇒ 必是仓外引用）· **N13** 台账对**该文件的状态叙述**（已删除 / 不存在 / 0 字节占位 ⇒ 历史记述；**邻行窗口 ±2** —— 台账常把叙述写在上一行再列路径；🔴 **且叙述必须归属同一个文件**：同行的叙述归该行**第一个**路径、邻行的叙述必须是**不含任何路径**的纯叙述行 ⇒ 否则**汇总行**里别人的叙述会把真实缺陷吃掉 ⇒ 见 **PIT-GLOBAL-040** 形态④⑤）<br>🟢 **效果**：users-user 域 **10 → 0** · users-admin **33 → 0** · task **0 → 0**（无回归）⇒ **全仓扫描 150 条缺失 ⇒ 149 条去噪 + 仅 1 条真缺陷**（那条落在 like 域 ⇒ E43）<br>🟢 **守门进工具自身** —— 第 0 层自检新增 **18 条正负对照**（用**人造样本** ⇒ 与本仓内容无关 ⇒ 任何分支都成立）⇒ 🟢 **变异 11 条全部被抓住且红在目标断言上**（M1 抽 N10 · M2 抽 N10 的中段过滤 · M3 抽 `sorted` · M4 抽 N11 · M5 抽 N12 · M6 抽 N13 邻行窗口 · M7 抽 N8 · M8 抽 N9 · M9 抽 N13 词表 · M10 抽 N13「邻行须为纯叙述行」· M11 抽 N13「同行取首个路径」）⇒ 教训 **PIT-GLOBAL-040** |
| **E41** | 🔴 **第 4 层的输出**不可复现** | 同上 | ✅ **已修** —— 根因 = `for sf in srcs:` 遍历的是 **set** ⇒ Python **字符串 hash 每进程随机化** ⇒ 拼出的 `src_text` 顺序每次不同 ⇒ `_is_noise_ref` 的正则命中不同 ⇒ **同一条 `file:line` 每次落进不同分类桶**（N5 / N7 / 跨库歧义 / MISSING）⇒ 实测**同一份仓库状态连跑 3 次**，层 4 的「引用不悬空 / 已去噪 / 跨库歧义」三个计数分别是 **(20,105,3) / (13,112,2) / (14,111,1)** ⇒ `PYTHONHASHSEED=0` 连跑 3 次完全一致 ⇒ **坐实是 hash 随机化**⇒ ✅ 修法 = `sorted(srcs)`（抽成 `_source_context()` helper + 第 0 层加**确定性对照**：用**逆序 list** 而非 set ⇒ set 的迭代序在**同进程内**也不保证等于 sorted 序 ⇒ 原写法是 **flaky 对照**）⇒ 🟢 修后三域各连跑 3 次**逐字节一致** ⇒ 教训 **PIT-GLOBAL-039** |
| **E42** | 🟢 **global 自己的台账 5 处路径写错** | `docs/DRIFT/global/**` | ✅ **已修** —— ① 共享 DRIFT 索引路径 ×2（**漏了 `docs/DRIFT/` 一段** ⇒ 正确是 `docs/DRIFT/_INDEX.md`）② global 的跨域不变量档名（原写成 `_cross.md` ⇒ 真实是 `cross-domain.md`）③ `LikeRpcClient.java` 的路径前缀写成了 common 域（真实在 **like 域** `service/like/like-api/…/client/` ⇒ **行号 108-121 与内容逐字对得上** ⇒ 只是前缀写错）④ 签字档的临时落点 ×2（该临时档**已于整合时迁回 `docs/Intentional_Deviations/users/_shared.md` 并删除** ⇒ 指向已改）<br>⚠️ **本条刻意不写出「错误的完整路径」** —— 台账里若把错路径按**活引用形态**原样写下，复核工具会把它当成「引用了不存在的文件」重新报出来（本轮实测：**这条记录一度把自己修的缺陷又报了出来**）⇒ ⇒ **纪律：台账里引述「曾写错的路径」时只写文件名 / 不带前缀的短形式**，否则工具分不清它是「历史记述」还是「活引用」 ⇒ 详见 **PIT-GLOBAL-040** 形态⑤ |
| **E46** | 🔴 **SQL 取消时刻的精度差** ⇒「客户端已放弃但事务仍可能提交」的窗口比 Go **大 2~3 个数量级** | `service/common/.../persistence/DeadlineQueryTimeoutInjector.java`（`setQueryTimeout` 取整）+ 各域事务边界 | 🔴 **第三十二轮末新发现**（**comment 域 owner 触发** ⇒ global 复核后**成立但机制归因要改**）<br>**机制**：`applyQueryTimeout` 按 `Math.ceil(remaining/1000.0)` **秒** 设 `Statement.setQueryTimeout` ⇒ 取消时刻 = **deadline + (0~999ms)**；Go 侧 `m.conn.WithContext(ctx)`（comment 域 model 层 **12+ 处**）⇒ ctx 到点**立即**发 CancelRequest ⇒ 窗口 = **CancelRequest 的传播延迟（毫秒级）**<br>**后果**：gRPC 超时调度器在 deadline 时刻 close ⇒ **客户端放弃**；业务线程继续跑 ⇒ SQL 在窗口内**可能自然完成** ⇒ **事务提交** ⇒ ⇒ **写入已提交、客户端收不到** ⇒ 两侧对「这次操作算不算成功」的答案**相反**<br>**命中条件**：查询耗时 ∈ (remaining, ceil(remaining/1000)×1000] ⇒ **概率性**（不是必现）<br>**现有守卫不覆盖**：① `checkDeadline()` 只在**方法入口**（admin-rpc 13 处）⇒ **不在提交前** ② B-1 的 `setQueryTimeout` 因**秒级取整**而留窗口 ③ 事务风格**混用**（`@Transactional` 5 域 / `TransactionTemplate` 4 域 / hot·task 无事务）⇒ 提交前守卫得**同时吃两种风格**<br>🔴 **对 E38② 的两处修正**：① **量级被高估** —— SQL 侧**已有 B-1 缓解**（10 个 rpc 组件**全部**注册了 `DeadlineQueryTimeoutInjector`）⇒ 真正剩下的**只有这个 ≤1s 窗口**，不是「SQL 完全不受 deadline 约束」；② **修法描述不完整** —— E38② 原写「架构级：handler 挪受控 executor + 超时 interrupt」⇒ ⚠️ **单靠 interrupt 无效**：pgjdbc **不响应线程中断**（该类 javadoc 引的 2026-10-21 实测：interrupt 后等锁查询照常跑 **11014ms**）⇒ ⇒ 必须配「**取消正在跑的 JDBC 语句**」⇒ 原描述**单独做不足**<br>🟢 **E38① 的额外收益（此前未记录）**：`DeadlineState` 的 deadline 现在 = `min(客户端, 服务端)` ⇒ ⇒ **SQL 超时上限也继承了客户端 deadline** ⇒ ⇒ 窗口从「服务端预算 − 客户端 deadline」压缩到「**≤1s**」<br>🟡 **修法候选**：① **提交前 deadline 守卫**（`TransactionSynchronization.beforeCommit` ⇒ 已过期则标 rollback-only）⇒ 落点 `service/common` ⇒ **global 单写权** ⇒ **轻** ② 毫秒级 SQL 超时（pgjdbc `setQueryTimeout` 只到秒 ⇒ 需 `SET LOCAL statement_timeout`）③ E38② 原描述的架构级改造 ⇒ ⚠️ **已知单独做不足**<br>⚠️ **共享层影响面**：改共享层 ⇒ **favorite-rpc 也用同一个拦截器** ⇒ ⇒ 须记账 + **告知 favorite owner**（§5.3b）<br>✅ **2026-10-26 Jason 拍板**：**「拆出来单独做」** —— E46 **从 E38② 里拆出**，作为**独立一轮**落地（提交前 deadline 守卫）⇒ E38② 维持「另立项」（其量级与修法描述的两处修正见上）⇒ ⚠️ **本条不改 `UNRESOLVED-ARPC-002` 的定性**（E38② 未做 ⇒ 定性不变）⇒ 本条是**独立的新漂移**，编号 `E46`，不是 E38② 的子项<br>✅ **第三十三轮：本体已落地**（落点 `service/common` ⇒ global 单写权）⇒ 🔴 **先纠正修法方向（Go 源码钉死）**：E46 原登记的修法候选 ①「**提交前 deadline 守卫**（`TransactionSynchronization.beforeCommit` ⇒ 已过期则标 rollback-only）」⇒ **不能做** —— `gorm.io/gorm@v1.31.1` `finisher_api.go:622` 的 `Transaction` ⇒ `db.Begin()` ⇒ `BeginTx(db.Statement.Context, opt)`（事务带 ctx），但 `tx.Commit()` ⇒ `committer.Commit()` ⇒ **不检查 ctx** ⇒ ⇒ **Go 的提交不看 deadline** ⇒ 「提交前守卫」会比 Go **更严**（Go 提交成功的那一档 RAG 会回滚）⇒ **那是新造一条漂移，不是修漂移** ⇒ ⇒ **正确修法 =「查询级检查」**（每次 SQL 执行前查 deadline；到期即失败 ⇒ 事务自然回滚 ⇒ 与 Go 的 ctx-aware 查询逐字等价）<br>✅ **改动两处**（`DeadlineQueryTimeoutInjector`）：① **毫秒精度** —— 旧实现把剩余毫秒**向上取整成秒**再 `setQueryTimeout(int)`（pgjdbc 只有秒级 API）⇒ 取消时刻 = `deadline + (0~999ms]`；⇒ 改用 pgjdbc ≥ 42.7 的 **`setQueryTimeoutMs(long)`**（**反射按方法名探测**：该方法只挂在实现类 `org.postgresql.jdbc.PgStatement` 上、**接口 `org.postgresql.PGStatement` 没有它** —— 实测 `javap`；`service/common` 是共享库 ⇒ **不把驱动实现类编进 API 面** ⇒ 反射 + **探测不到就退回秒级并记 WARN**）⇒ 驱动内部 `connection.addTimerTask(cancelTask, timeout)`（`PgStatement.java:1047`）**直接用毫秒** ⇒ ⇒ **取消时刻 == deadline 时刻** ② **已过期 ⇒ 不执行这条 SQL**（旧行为是「设 1 秒兜底」= **放行它再跑最多 1 秒** ⇒ 自动提交场景下那条 SQL **会在服务端真的执行并提交**，而 Go 侧此时**根本不会把它发出去**）⇒ 改为直接抛 `SQLTimeoutException(SQLState=57014)` ⇒ 与 Go 的 `database/sql`「取连接前查 `ctx.Err()`」同款 ⇒ 且该 SQLState 会被 `SqlTimeoutStatusTranslator` 翻成 `DEADLINE_EXCEEDED`<br>🟢 **守门 2 层**：① `service/common` 单测 **10 条**（新增：毫秒数**原样传下去且不走秒级路径** · 已过期**立即失败且语句被 close**（MyBatis 的 `doQuery` 里 `stmt` 此刻还是 null ⇒ 抛异常后它的 `finally` 关不到 ⇒ 必须自己关）· 驱动不支持毫秒级 ⇒ 退回秒级 · 剩余大到秒级换算会溢出 ⇒ 当作「无 deadline」）② **真 PostgreSQL 守门 2 条**（`CommentSqlDeadlinePrecisionGateTest`：Testcontainers **真 PG** + **真 pgjdbc** + **真 MyBatis 插件链**（生产那两个拦截器原样注册）⇒ ① **已过期 ⇒ 第二条语句立即失败 ⇒ 第一条随事务回滚**（**直接钉 E46 的核心后果**「客户端已放弃、服务端却提交了写入」）② **在途查询在 deadline 时刻被取消**（另开连接持 `ACCESS EXCLUSIVE` 表锁把 SELECT 堵住 ⇒ 断言存活时长 **< 剩余+400ms**；旧的秒级取整会是 ~1000ms））<br>🟢 **变异 2 条**（**各红 1 条且红在目标断言**）：M1 退回秒级取整 ⇒ **精度断言红**（另一条仍绿 ⇒ 精确命中）· M2 去掉「已过期立即失败」⇒ **「过期后仍执行 SQL」断言红**（另一条仍绿）⇒ 还原后校验 sha256 一致 + `touch`（**PIT-GLOBAL-037**）<br>🟡 **本条只销「机制」这一层**：① **业务层仍可能吞掉** `DEADLINE_EXCEEDED`（`catch (Exception e) → INTERNAL`；更糟：**3 处静默降级**把超时当「计数为 0」而**接口返回成功**）⇒ 另立 **E47**；② **`UNRESOLVED-ARPC-002` 的定性不变**（E38② 未做） |
| **E45** | 🟢 **第三十二轮全仓回归** | `align/global` @ 第三十二轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2305 条 · 0 failures / 0 errors / 0 skipped**（耗时 **19:00 min**）⇒ 🟢 **账对得上**：本轮**只改 Python 工具**（`tools/check-ledger-selfcheck.py`）⇒ **不动任何 Java 测试** ⇒ 数字与第三十一轮**同值 2305**（净增 0）⇒ 🟢 **两口径一致**（模块级汇总 **29 行** = **2305**；`TEST-*.xml` **305 份** 求和 = **2305**）⇒ ⚠️ **统计口径坑（复现了一遍）**：`grep "Tests run:"` 会**同时**匹配 **per-class 行（带 `-- in`）**与 **per-module 汇总行（不带）**⇒ **求和会翻倍（实测 4610 = 2×2305）** ⇒ ⇒ **模块汇总口径必须再 `grep -v -- "-- in "`**⇒ ⚠️ 跑前删过全部旧 surefire XML（**305 份**）⇒ 无历史残留污染 |
| **E43** | 🔵 **别域台账 1 处真实路径错误** | `docs/DRIFT/like/rpc.md:163` | 🟡 **记账 + 报主会话**（global **不写别域台账**；⚠️ **当前无 like 域 owner 会话** ⇒ 只能经主会话转达）—— `tools/check-ddl-static.py` ⇒ 真实是 **`tools/difftest/check-ddl-static.py`** ⇒ 修法 = 补 `difftest/` 一段 |
| **E44** | 🔴 **工具自检的阳性对照是**分支相关**的 ⇒ `--mutation-test` 在任何非 task 分支上恒红** | `tools/check-ledger-selfcheck.py` | ✅ **已修** —— 阳性对照原来要求「复原后第 3 层 == **0**」，而第 3 层含**分支相关**判据（**commit 清单覆盖** = 本分支 commit 数 vs 台账登记行数）⇒ 在 `align/global`（89 个 commit）上**恒报 134 处** ⇒ ⇒ **整个 `--mutation-test` 在别的分支上不可用**⇒ ✅ **正确口径 = 「复原后的计数 == 变异前的计数」**（判据可逆 + 不误报）⇒ 基线在**任何变异之前**取 ⇒ 🟢 **修后 `--mutation-test` EXIT=0**（9 个变异 + 阳性对照全通过）⇒ 与 **PIT-GLOBAL-040** 同族（**变异自身的有效性**） |

| **E47** | 🔴 **业务层把「SQL 被 deadline 取消」改写/吞掉** ⇒ 事务 / 指标 / wire 三重口径失真 | 5 个 rpc 域的 `catch (Exception e) → INTERNAL` + **3 处静默降级** | 🔴 **第三十三轮新发现**（E46 实施时顺带盘出 ⇒ **落点在各域 ⇒ 不是 global 单写权 ⇒ 登记 + 通知 owner**）<br>**规模**（只读盘点）：**24 处硬改写**（宽 catch ⇒ 改写成 INTERNAL / 业务码 ⇒ **DEADLINE_EXCEEDED 被消灭**）+ **3 处静默降级**（🔴 **最危险**：把超时当「计数为 0」/ best-effort 失败 ⇒ **接口返回成功**）<br>**按域**：like-rpc **5 + 2 静默**（`LikeDomainService.java:227/293/358/444/551` + `:729-731`（返回 redisTotal）· `:765-771`（返回 0L））· comment-rpc **10**（`CommentDomainService.java:161/434/447/474/523/541/590/651/690/710`）· follow-rpc **4**（`FollowDomainService.java:455-460` / `:474-479` 两个 helper **覆盖全域** + `FollowRecommendationService.java:251/263`）· message-rpc **3**（`MessageDomainService.java:755/873/921`）· article-rpc **2 + 1 静默**（`ArticlePublicDomainService.java:279/304` + `ArticleDomainService.java:216-223`）<br>🟢 **不吞的（已修好，不必动）**：user-rpc **27 处**宽 catch **一一配对** `catch (StatusRuntimeException e) { throw e; }`（`UserDomainService.java:185-187`）· admin-rpc **30 处**同款（`AdminDomainService.java:175-177`）· favorite-rpc **13 处全部收窄成 `catch (DataAccessException)`** ⇒ 抓不到 `StatusRuntimeException` ⇒ **天然透传**（🟢 favorite 的 catch 形态**无需动** ⇒ 但 favorite 域自身的**SQL 语句层**（语句文本/参数/锁语义）**未做双侧活体对拍** ⇒ DDL 层 / 错误码契约层 / 事务语义层**三层已闭** ⇒ 这是 favorite 域内的事 ⇒ **global 不替它下结论**）· task-rpc **mapper 调用裸奔**（`TaskDomainService.java:102`，注释明写「不要吞掉或改写」）· message-rpc 的 DB 路径**几乎全是 `throw e` 原样重抛**<br>🔴 **时序限定（重要）**：`RpcInterceptor` 的超时调度器在 **deadline 时刻**就把 call 关成 `DEADLINE_EXCEEDED`（`RpcInterceptor.java:264-287`）⇒ **客户端早就拿到 1015** ⇒ ⇒ 业务层改写**在 rpc-deadline 主路径上对 wire 不可见** ⇒ 本条真正伤到客户端的是 **① 取消来自 rpc deadline 之外**（JDBC `default-statement-timeout` / PG `statement_timeout` / `pg_cancel_backend`）· **② 3 处静默降级**（**返回成功**）· **③ 事务与指标口径**（超时被当「DB 故障」记进熔断器计数）<br>🟡 **状态**：已登记 ⇒ **落点在各域（like/comment/follow/message/article）⇒ 不是 global 单写权** ⇒ ⇒ **global 只记账 + 通知 owner**（like/article 无 owner 会话 ⇒ 经主会话转达）
| **E48** | 🟡 **「客户端 deadline 档」双侧活体探针没进常规编排** ⇒ E38 的运行期回归网缺这一环 | `tools/difftest/run.sh` / `tools/difftest/diff.py` | 🟡 **第三十三轮登记**（由 users/admin owner 的纠正引出）—— 探针**已经存在**（`tools/difftest/exp-grpc-admin-deadline-probe.sh`，2026-10-24 ⇒ 含短/长 deadline 双档 + `exit 1 ⟺ 短档下两侧 PG 查询存活时长差异 > 1s`）⇒ **真正的缺口** = `run.sh` 与 `diff.py` **都不调它** ⇒ 它是**手动取证工具** ⇒ 改坏了不会被自动抓到<br>🟡 **落点已评估（本轮不做）**：① 探针**硬编码端口**（`GO_ADMIN_PORT=18081` / `RAG_ADMIN_PORT=58081`）与 `RUN_DIR=/tmp/difftest` ⇒ **不支持 `DIFTEST_PORT_OFFSET`** ⇒ 直接接进 `run.sh` 会**破坏并行实例纪律**（`run.sh` 头部已写明多域并发时两套 harness 会互踩端口/容器/目录）⇒ 必须先给探针补偏移支持 ② 探针会持 `ACCESS EXCLUSIVE` 表锁 ⇒ 必须排在 `diff.py` **之后** ③ 每次对拍 +~60s<br>⇒ **属 A 类（落点在 global 写权内 · 不需拍板）** ⇒ **下一轮做**（补偏移 + 接编排 + 一次变异证明它真的被调） |
| **E49** | 🟢 **第三十三轮全仓回归** | `align/global` @ 第三十三轮收口 | ✅ **BUILD SUCCESS · 52 模块 · 2310 条 · 0 failures / 0 errors / 0 skipped**（上轮 2305 ⇒ **净增 5 条** = `service/common` 的 `DeadlineQueryTimeoutInjectorTest` **+3**（10 条，原 7 条：删 1 条「过期设 1 秒」+ 新增 4 条）+ comment-rpc 的 `CommentSqlDeadlinePrecisionGateTest` **+2** ⇒ **账对得上**）⇒ 🟢 **两口径一致**（模块级汇总 **29** 个模块 = **2310**；`TEST-*.xml` **306** 份求和 = **2310**）⇒ 耗时 **19:04 min**⇒ ⚠️ **跑前删过全部旧 surefire XML**（无历史残留污染）⇒ ⚠️ **期间没有在别的进程里跑同一 reactor 的构建**（守门脚本只读 pom / jar / 文件树 ⇒ 不碰 maven ⇒ 遵守 **PIT-GLOBAL-032**）⇒ 🟢 **本轮是真正的 Java 改动**（与第三十二轮的「纯 Python ⇒ 净增 0」不同）⇒ ⇒ **favorite-rpc 的 129 条用例也在其中全绿** ⇒ 共享层改动**未打红 favorite**

### 🆕 七之七、第三十三轮（**2026-10-26**）—— **E46 落地**：SQL 取消时刻 == deadline 时刻

> **主线**：Jason 2026-10-26 拍板「**拆出来单独做，下一轮修**」⇒ 本轮把 E46 的**机制**落地（落点 `service/common` ⇒ global 单写权）。
> ⚠️ **本轮同时纠正了两处「方向性」错误**（不是细节）：① **E46 的修法从「提交前守卫」改成「查询级检查」**（Go 的 `tx.Commit()` 不查 ctx ⇒ 守卫会比 Go 更严 ⇒ **会新造漂移**）；② **E38 的「须新写探针」前提不成立**（探针 2026-10-24 就有了 ⇒ 真缺口是**没进编排** ⇒ 另立 E48）。

**本轮四件事**：
1. ✅ **E46 机制落地**（`service/common`）⇒ 毫秒精度（`setQueryTimeoutMs`）+ 已过期立即失败 ⇒ 详见 E46 单元格。
2. ✅ **守门 2 层**：`service/common` 单测 10 条 + **真 PostgreSQL** 守门 2 条（comment-rpc）⇒ **变异 2 条各红 1 条且红在目标断言**。
3. 🟢 **销案一条待确认项**：`hot-rpc` 未注册 `SqlTimeoutStatusTranslator` ⇒ **前提不成立** ⇒ `hot-rpc` **无 mapper / 无 MyBatis / 无 DataSource / 无 SQL**（Go 侧同样无 model 层）⇒ **注册与否无差别，不是缺陷**。
4. 🟡 **新登记 2 条**：**E47**（业务层吞掉 DEADLINE_EXCEEDED · 24 处硬改写 + 3 处静默降级 · 落点在各域 ⇒ 只记账 + 通知 owner）· **E48**（探针未进编排 · 落点在 `tools/difftest/run.sh` ⇒ global 写权内 ⇒ 下一轮做）。

> 🔴 **共享层改动 ⇒ 记账 + 告知（§5.3b）**：本轮改了 `service/common` 的 `DeadlineQueryTimeoutInjector` ⇒ **9 个有 SQL 的 rpc 组件全部受影响**（article / comment / **favorite** / follow / like / message / task / user / admin；**hot 无 SQL 不适用**）⇒ ⇒ **favorite owner 必须被告知**（favorite 域台账**非零** ⇒ 域内已审过 ⇒ **只能记账 + 告知，不能替它下结论**）。
> ⚠️ **favorite 域零改动**：本轮**未碰 favorite 的任何代码/配置/测试/台账** ⇒ 写权门禁 `check-global-lane-ownership.py` 应仍 EXIT=0。
> 🟢 **favorite owner 的跨域实测回执（第三十三轮）**：把 global 的 `DeadlineQueryTimeoutInjector.java` **叠到 `align/favorite` 上跑** ⇒ **零红、零需 favorite 改动** ⇒ `service/common` 177 / favorite-rpc 99 / favorite-api 94 / favorite-common 29 **全 0 失败**；三个探针（authority-live 21 case / api-full-parity 32 / grpc-contract 36+15）**全 EXIT=0、冻结值一条没动**。
> 🔴 **该回执同时纠正了本轮台账里的一条错误事实**：本轮曾写「favorite 域台账 0 字节 ⇒ 从未被审过」⇒ **不成立** —— favorite 的 12 份台账全部非零、合计 ≈ **430 KB**（最大一份 `docs/DRIFT/favorite/rpc.md` **213,799 字节**），此后已做 **19 轮**对照 ⇒ **根因是本轮引用了「注入进上下文的 AGENTS.md 快照」而不是磁盘上的文件**（磁盘 `AGENTS.md` 的 favorite 行早已更新为「✅ 2026-10-26 核对：第十六轮已收口」）⇒ ⇒ 新坑 **PIT-GLOBAL-044**。


---

| 日期 | 动作 |
|---|---|
| 2026-10-26 | ✅ **第三十三轮（E46 落地 · SQL 取消时刻 == deadline 时刻）** —— ① ✅ **Jason 拍板项已执行**（「拆出来单独做，下一轮修」）⇒ 落点 `service/common`（global 单写权）⇒ 🔴 **先纠正修法方向**：E46 原候选「**提交前 deadline 守卫**」**不能做** —— `gorm v1.31.1 finisher_api.go:622` ⇒ `tx.Commit()` **不查 ctx** ⇒ 守卫比 Go **更严** ⇒ **会新造漂移** ⇒ ⇒ 改为**查询级检查**（与 Go 的 ctx-aware 查询逐字等价）② ✅ **改动两处**（`DeadlineQueryTimeoutInjector`）：**毫秒精度**（pgjdbc ≥ 42.7 的 `setQueryTimeoutMs(long)`，**反射探测** ⇒ 驱动内部 `addTimerTask(cancelTask, timeout)` 直接用毫秒 ⇒ 取消时刻 == deadline 时刻）+ **已过期 ⇒ 不执行这条 SQL**（旧行为「设 1 秒兜底」= 放行它再跑最多 1 秒 ⇒ 自动提交下**会在服务端真的提交**）③ 🟢 **守门 2 层**：`service/common` 单测 **10 条**（+4）+ **真 PostgreSQL** 守门 **2 条**（`CommentSqlDeadlinePrecisionGateTest`：真 PG + 真 pgjdbc + 真 MyBatis 插件链 ⇒ ① 已过期 ⇒ 第二条语句立即失败 ⇒ 第一条随事务回滚 ② 在途查询在 deadline 时刻被取消 ⇒ 存活 < 剩余+400ms）④ 🟢 **变异 2 条**（各红 1 条且**红在目标断言**）：M1 退回秒级取整 ⇒ 精度断言红 · M2 去掉「已过期立即失败」⇒ 「过期后仍执行 SQL」断言红 ⇒ 还原后 sha256 一致 + `touch`⑤ 🟡 **E38 台账订正**（users/admin owner 纠正 ⇒ 复核属实）：「须**新写**客户端 deadline 档探针」**前提不成立** ⇒ `exp-grpc-admin-deadline-probe.sh`（2026-10-24）**已存在且已带双档** ⇒ 真缺口 = **没进常规编排** ⇒ 另立 **E48**（落点 `tools/difftest/run.sh` ⇒ A 类 · 下一轮做）⑥ 🟢 **销案一条待确认项**：`hot-rpc` 未注册 `SqlTimeoutStatusTranslator` ⇒ **前提不成立**（hot-rpc **无 mapper / 无 MyBatis / 无 SQL**，Go 侧同样无 model 层）⇒ **不是缺陷**⑦ 🟢 **全仓回归**：**BUILD SUCCESS · 52 模块 · 2310 条 · 0 failures / 0 errors / 0 skipped**（19:04 min · 两口径一致 · 净增 5 条 ⇒ 账对得上）⑧ 🟡 **新登记 E47**：业务层吞掉 `DEADLINE_EXCEEDED`（**24 处硬改写 + 3 处静默降级** ⇒ 静默降级会把超时当「计数为 0」而**接口返回成功**）⇒ 落点在 like/comment/follow/message/article ⇒ **不是 global 单写权 ⇒ 只记账 + 通知 owner**⑨ 🟡 **共享层记账**：本轮改 `service/common` ⇒ **9 个有 SQL 的 rpc 组件受影响**（含 **favorite**）⇒ ⇒ **favorite owner 已告知**（favorite 域台账**非零** ⇒ 域内已审过）
| 2026-10-26 | 🟡 **第三十二轮末 · E46（comment 域 owner 触发 ⇒ 复核后成立但机制归因要改）** —— ① 🔴 **触发**：comment 域 owner 报「comment-api 的 per-call deadline = `min(2s, HTTP 剩余预算)` ⇒ 客户端先放弃、而 **RAG 服务端的 PG 查询仍跑满到 2s** ⇒ 客户端已收不到结果、但 RAG 侧事务/写入已完成」⇒ 按「**先复核前提再登记**」复核 ⇒ 🟢 **结论方向成立、机制归因不成立** ⇒ **改的是精度不是有无** —— RAG 侧**有** SQL 级缓解 `DeadlineQueryTimeoutInjector`（10 个 rpc 组件**全部**注册，含 comment-rpc）⇒ SQL 会被 **CancelRequest** 掐掉 ⇒ **不会跑满 2s**；② 🔴 **真差异 = 秒级向上取整留下的 ≤1s 窗口**：`Math.ceil(remaining/1000.0)` 秒 ⇒ 取消时刻 = **deadline + (0~999ms)**；Go 侧 `m.conn.WithContext(ctx)` 贯穿 comment 域 model 层**每一次查询/事务**（12+ 处）⇒ ctx 到点**立即** CancelRequest ⇒ 窗口 = **毫秒级** ⇒ ⇒ **两侧差 2~3 个数量级**；③ ⚠️ **命中是概率性的**：查询耗时须落在 `(remaining, ceil(remaining/1000)×1000]` 这条 ≤1s 带子内；**现有守卫不覆盖**（`checkDeadline()` 只在方法入口、**不在提交前**）；④ 🔴 **E38② 两处修正**：**量级被高估**（SQL 侧已有 B-1 缓解 ⇒ 第②层真正剩下的只有这个 ≤1s 窗口）＋ **修法描述不完整**（**单靠 interrupt 无效** —— pgjdbc 不响应线程中断，实测 interrupt 后等锁查询照常跑 11014ms ⇒ 必须配「取消正在跑的 JDBC 语句」）；⑤ 🟢 **E38① 的额外收益**（此前未记）：`DeadlineState` 的 deadline 现在 = `min(客户端, 服务端)` ⇒ ⇒ **SQL 超时上限也继承了客户端 deadline** ⇒ 窗口从「服务端 2s − 客户端 deadline」压到 **≤1s**；⑥ 🟡 **修法候选**：**提交前 deadline 守卫**（`TransactionSynchronization.beforeCommit`）⇒ 落点 `service/common` ⇒ **global 单写权** ⇒ 轻；⚠️ 事务风格**混用**（`@Transactional` 5 域 / `TransactionTemplate` 4 域 / hot·task 无事务）⇒ 守卫须同时吃两种；⚠️ 共享层改动 ⇒ **favorite-rpc 也用同一拦截器** ⇒ 须记账 + 告知 favorite owner |
| 2026-10-26 | ✅ **第三十二轮（共享复核工具三处缺陷修复 · 第 1 层去噪 + 输出可复现 + 自检去分支相关）** —— ① 🔴 **触发**：users/user 域 owner 反馈「新版丢了去噪规则」⇒ **复核前提后归因不成立**（去噪一直在跑：第 2/4/5/6 层 255+ 条）⇒ 真正缺陷 = **第 1 层零去噪** ⇒ 该工具**跨域共用** ⇒ global 收口 ② ✅ **E40 已修**：第 1 层新增 **N8~N13 六条**去噪（N8 `...` · N9 裸文件名 · N10 短路径（靠 basename + 中段连续相邻落真实文件）· N11 Go 侧路径（在 Go 真源仓存在）· N12 顶层目录不在本仓（逻辑上不可能是本仓路径）· N13 台账对该文件的状态叙述（邻行 ±2 + 叙述须归属同一文件））⇒ users-user **10 → 0** · users-admin **33 → 0** · task **0 → 0** ⇒ 全仓 **150 缺失 ⇒ 149 去噪 + 1 真缺陷**（那条在 like 域）⇒ 🟢 **第 0 层新增 18 条正负对照**（人造样本 ⇒ 与本仓无关）⇒ 🟢 **变异 11 条全被抓住且红在目标断言** ③ ✅ **E41 已修**：第 4 层 `for sf in srcs` 遍历 **set** ⇒ **字符串 hash 随机化** ⇒ 同一条 `file:line` 每次落进不同桶 ⇒ 实测三次计数 (20,105,3)/(13,112,2)/(14,111,1) ⇒ `PYTHONHASHSEED=0` 三次一致 ⇒ 坐实 ⇒ 修法 `sorted(srcs)` + **逆序 list 确定性对照**（set 迭代序在同进程内也不保证等于 sorted ⇒ 原写法是 flaky 对照）⇒ 修后三域各连跑 3 次**逐字节一致** ④ 🟢 **E42 已修**：global 自己台账 5 处路径写错（`docs/DRIFT/_INDEX.md` ×2 · `cross-domain.md` · `LikeRpcClient.java` 真实在 like 域 · `users-domain.md` 临时落点已迁回并删除）⑤ 🟡 **E43 记账 + 通知**：like 域 `docs/DRIFT/like/rpc.md:163` 的 `tools/check-ddl-static.py` ⇒ 真实是 `tools/difftest/check-ddl-static.py` ⑥ ✅ **E44 已修**：工具自检的阳性对照原要求「复原后第 3 层 == 0」，而第 3 层含**分支相关**判据（commit 清单覆盖）⇒ 在 `align/global` 恒报 134 处 ⇒ ⇒ **`--mutation-test` 在任何非 task 分支上恒红** ⇒ 口径改为「**复原后 == 变异前**」⇒ 🟢 修后 `--mutation-test` **EXIT=0** ⑦ 🔴 **新坑 2 条**：**PIT-GLOBAL-039**（set 迭代序 ⇒ 门禁输出不可复现）· **PIT-GLOBAL-040**（变异无效的三种形态：规则被掩护 / 对照 flaky / 变异打错层） |
| 2026-10-26 | ✅ **第三十一轮（E38 第 ① 层落地 · 超时继承客户端 deadline）** —— ① 🔴 **触发**：**users/admin 域 owner 主动提出**「同在 `service/common` 的 `RpcInterceptor` 还有一条本体漂移：超时期限**不继承客户端 deadline**」⇒ 核实**成立** ⇒ **global 台账此前 0 命中 ⇒ 从未跟踪** ⇒ 登记为 **E38**（落点 `service/common` ⇒ global 单写权 ⇒ A 类）② ✅ **Jason 拍板**：**① 下一轮做 · ② 另立项**（架构级：handler 挪受控 executor + 超时 interrupt · 影响 10 个 rpc 组件）③ 🟢 **① 已落地**：新增 `clientDeadlineRemainingMs()`（从 `io.grpc.Context.current().getDeadline()` 取 · 三个约定：无 deadline ⇒ `MAX_VALUE` · 已过期 ⇒ `0` · 取值异常 ⇒ `MAX_VALUE`）+ `effectiveTimeoutMs = min(服务端预算, 客户端剩余)`（两处消费点：`deadlineEpochMs` + 超时调度器延时）⇒ ⚠️ `timeout<=0` 时**刻意保持旧行为**（否则业务会在「服务端没超时」时误判超时）④ 🟢 **grpc-java 取值点已实测**（`interceptCall` 里可用 ⇒ 客户端 1s ⇒ 服务端读到 **932ms** 剩余 · headers 里 `grpc-timeout: 936027u`）⑤ 🟢 **双侧活体探针加第 3 个用例** `client_deadline_ctx`：Go 半边**直接引用冻结仓真源** ⇒ 修前 **Go=client_100ms vs RAG=server_own**（**漂移坐实** · EXIT=1）⇒ 修后**两侧一致**（EXIT=0）⇒ ⚠️ **档位设计踩坑**：客户端 deadline **必须早于**服务端 timeout，否则 `min` = 服务端值 ⇒ 两侧分不出差别（Go 半边最初设 1000ms / 服务端 300ms ⇒ 无效 ⇒ 改成 100ms）⑥ 🟢 **守门 2 条**（`RpcInterceptorClientDeadlineGateTest`：① 客户端 deadline 早于服务端 ⇒ 有效期限必须落在**客户端那一档** ② 客户端**没带** deadline ⇒ 仍是服务端预算 ⇒ 防「把无 deadline 错当成有 deadline」）⇒ 🟢 **变异 1 条**（退回 `serverBudgetMs` ⇒ 守门 **1 条红且红在目标断言**（另一条仍绿 ⇒ 精确命中）+ 探针 EXIT=1）⑦ 🔴 **新坑 PIT-GLOBAL-037**（还原变异用 `shutil.copy2` ⇒ **mtime 被保留** ⇒ Maven 判定「class 比 source 新」⇒ **跳过重编译** ⇒ 复跑跑的**仍是变异后的字节码** ⇒ 症状「还原了却仍红」⇒ **还原后必须 `touch`**）⑧ 📨 **两域已通知**（users/admin：本体第①层已修 + **第②层未做 ⇒ 定性不变** + 它的两条实测哪一格会变/哪一格不变；favorite：共享层改动 ⇒ §5.3 记账 + 告知，**零 favorite 改动**）⑨ 🔴 **口径补充**：第五来源的关键词集漏了「**不在本域 + 无权改**」这个措辞变体 ⇒ 纪律改为「**按落点是否在本域写权内逐条判**，而不是按措辞 grep」 |
| 2026-10-26 | ✅ **第三十轮（A 类 3 条落地 · E30/T-14/T-15）** —— ① 🔴 **E30 修完**：**RPC 超时的 description** 与 Go 不一致（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`）⇒ 落点 `service/common` ⇒ **8 个 rpc 模块的 `rpc-timeout: 2s` ⇒ 线上真实生效** ⇒ **证据强度比原记录高**：这条被**三个域各自独立**记过（`comment/rpc.md` U10 · `users/user/rpc.md` **V-007** · `users/admin/rpc.md` 活体测量）⇒ 🟢 **守门 2 条**（**真 Netty server + 真 channel + 真客户端 + 真 OTel exporter** ⇒ 断言**网络上真正传过来的字节**）⇒ 🟢 **双侧活体探针**（`tools/semantic-probe/rpc-timeout/` = **第三个 semantic probe**：Go 半边**直接 `replace` 引用冻结仓真源** + 真 gRPC server；RAG 半边跑**生产类**）⇒ **PASS**⇒ 🟢 **变异 2 条全红且红在目标断言上**（M1 ⇒ 守门 2/2 红 + **探针 EXIT=1**；M2 ⇒ **只红遥测那条**、wire 那条仍绿）② 🔴 **C-1 边界**：守门抓出**更深一层** —— code 名大小写（grpc-java `DEADLINE_EXCEEDED` vs grpc-go `DeadlineExceeded`）⇒ **已登记为「表述差异 C-1」且写明「不做臆造映射」⇒ 本轮不动** ⇒ ⇒ **E30 修完后剩下的差异恰好等于 C-1** ⇒ 守门**刻意把 C-1 的形态钉住**，防后人误改 ③ 🔵 **T-14 已修**（javadoc「两层补救并列」误述 ⇒ 改成「两条互不覆盖的路径」+ 逐行取证 + 四种机制逐条标注适用路径；零行为改动）④ 🟡 **T-15 的 ③ 已做**（7 份 slow 守门各加**专属上下文缓存键** ⇒ 把「靠 properties 恰好不同」的**侥幸**变成**结构保证**；实测 `@DynamicPropertySource` **不进** context cache key）⇒ ⏸ **② 不做**（防不住目标失效形态：缓存上下文不会重新 refresh）· ⏸ **④ 不做**（横跨 10 域且**会碰 favorite** ⇒ 属架构级改造）⑤ 🟢 **新坑 4 条** **PIT-GLOBAL-033~036** ⑥ 📨 **三域销账已通知**（`comment` U10 · `users/user` V-007 + CASE-31 · `users/admin` §1.3 档位 B ⇒ **global 不写别域台账 ⇒ 只能记账 + 通知 owner**；三条消息均含落点 + 守门类名 + 探针目录 + **C-1 边界** + **行号引用过期提醒** + 「global 不代替别域改台账」边界）|
| 2026-10-26 | ✅ **第二十九轮（盘点轮 · 口径三来源合流）** —— ① 🔴 **触发**：Jason 追问「现在 global（除 favorite）域内可修漂移、未知项有哪些」⇒ 本轮**不做修复，只做盘点**，但盘点过程**又翻出 2 条从未被任何台账跟踪的条目** ② ✅ **盘点口径 = 三来源合流**（① global 四桶 ＋ ② 各域台账域外交接段 ＋ ③ `common` 域台账 §十二·二 的 10 条「外部域尾巴」）⇒ **A 类 4 条 / D 类 2 条 / C 类 5 条 / 未知项 0 条属 global** ⇒ 逐条 ⇒ §七之五 **E30~E32** + 盘点结论块 ③ 🔴 **E30 新发现**：**RPC 超时文案不对齐**（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms` ⇒ gRPC status **description** 不同）⇒ 落点 `service/common/.../RpcInterceptor.java:197,205` ⇒ **global 台账 0 命中 · 签字档 0 命中 ⇒ 此前从未被记录**（源自 `comment/rpc.md` U10「上报主会话」后无人接）④ 🔴 **E31 新发现**：**`DRIFT-FOLLOW-006`**（5 个 `follow_rpc_*` 指标无抓取出口）⇒ 用户 2026-10-24 已定「转全仓 rpc 层统一方案交主会话拍板」⇒ **global 台账 0 命中 ⇒ 球在主会话却没人接** ⇒ **真决策项** ⑤ ✅ **E32**：AGENTS.md §0 users 行「⚠️ admin-rpc `status=0L` 未修」是**过期快照**（users 域已备好双侧活体对拍 + 独立 Go 探针 ⇒ 两侧都读成 0 ⇒ 不是漂移）⇒ **已撤销**（该 worker 无权改共享文件 ⇒ **卡了 3 天**）⇒ AGENTS.md 32,746 → **32,708 B（余 60 B）** ⑥ ⚠️ **第五来源的价值与代价同时被证明**：48 行命中里 **46 行是过期快照**（别人修完了但没人销账）⇒ **真正还开着的只有 2 条** ⇒ 🔴 **新纪律：跨域条目被别域修完后须回写销账** ⑦ ⚠️ **最大的未知池在 common 域自己**（27 条编号未修 + 5 项未编号 + 未覆盖范围）⇒ **但其台账原文写明「用户 2026-10-25 明确『我自己修』」⇒ 不是 global 的 backlog** ⇒ 🔴 **且全仓无 `align/common` 分支 / 工作树 ⇒ 若不做就无人做** |
| 2026-10-26 | ✅ **第二十八轮（4 条 lane 并行 + 整合者修缺陷 + 端到端实证）** —— ① 🔴 **触发**：Jason 追问「不是已经清空未知了嘛，怎么又有」⇒ 查证发现 §七之二的盘点口径**漏了第五个来源**（各域台账的「域外/跨域移交/交主会话」段 ⇒ **63 行 / 14 份文件**）⇒ 那批「挂着不动 = 永久漂移」的条目本该由 global 收口 ⇒ ② ✅ **4 条 lane**：**L12**（article/comment/like：两斜杠 ×5 + `grpc-inprocess` scope ×2 · 9 个守门类 / 30 条用例 · 4 变异）· **L13**（message/task：两斜杠 ×2 + scope ×1 + task-rpc 无 `Main-Class` · 2 类 / 8 条 · 3 变异）· **L14**（hot-rpc 无 `Main-Class` = **L11 漏报的那一条**）· **L15**（**两个守门缺口**：① `check-jar-executable.sh` 只扫 `*-api` ⇒ **10 个 rpc 的产物可执行性从来没被查过**② `check-grpc-runtime-classpath.py` 扫到 `target/` ⇒ **门禁恒红**（存量缺陷））⇒ ③ 🔴 **N8 实际是 2 个模块**（逐个解包 10 个 rpc 才看出来）⇒ **漏报根因 = 守门缺口** ⇒ 教训 **PIT-GLOBAL-030**⇒ ④ 🔴 **整合者修了一处守门缺陷**（`pomMustDeclareSpringBootMavenPlugin` **没剥 XML 注释** ⇒ M1 变异时它**居然是绿的** ⇒ 恒真）⇒ 教训 **PIT-GLOBAL-031** ⑤ 🟢 **端到端对照实证**（真 fat jar 修前/修后 A/B · **用生产 yaml 原样起**）⇒ 修前炸 `Incorrectly formatted target uri`；修后 **gRPC Server started + TCP 监听实证** ⇒ 详见 `cross-domain.md` §二·十六⑥ ✅ **hot 域台账过期快照订正**（D14/D25/D27/D21/D22/D23 全闭合 ⇒ 域内未闭合项清单为空）⇒ ⑦ 🟥 **第一次回归 BUILD FAILURE 是个教训**（两类非代码原因：**构建竞态** + **环境时序** ⇒ 单独复跑全绿）⇒ 教训 **PIT-GLOBAL-032** ⇒ 8 条新坑 **PIT-GLOBAL-029~032** ⑧ 🟢 **干净回归 BUILD SUCCESS**（52 模块 · **2301 条** · 0 failures / 0 errors / 0 skipped · 上轮 2263 ⇒ **净增 38 条** = 44 条新用例中**在 `mvn test` 阶段跑的 38 条** ⇒ **账对得上** · 模块汇总行与 XML 求和两口径一致）⑨ 🔵 **favorite 侧复查**（global 零写权 ⇒ 只查不动）⇒ `grpc-inprocess` scope **已被 favorite owner 自己修掉**（`e76ea8e`）⇒ 台账订正；**两斜杠 2 处尚未修** ⇒ 已**直接通知 favorite 域会话**（含证据 + 修法 + 边界）⇒ **不是只记账** |
| 2026-10-26 | ✅ **第二十七轮（6 条 lane 并行 + 整合者修缺陷）** —— ① **9 条「能直接动手」的未知项**拆成 **6 条 lane**（L6 x5gate / L7 slowgate / L8 slowgate2 / L9 interrupt / L10 ddldeep / L11 flywaydeep）⇒ **按模块零重叠**（L2 的教训：同模块两个 lane 会互踩 `target/`）⇒ ② 🟢 **X-5 轴真 server 守门补到 8 个 rpc 组件**（admin/article/comment/like 各 3 用例 14 断言）· **slow 轴守门补到 8 个域**（逐域证据齐）⇒ **生产代码零改动** ③ ✅ **缺口③ 定性** = 框架限制成立（**Go 也不杀 goroutine**）⇒ **L2 缺口⑤ 销账** ④ ✅ **缺口④ 复评** = Boot 的 OTel 自动配置**不在依赖里** ⇒ 本仓自研装配 ⇒ 前提**侥幸成立** ⇒ ⑤ 🔴 **整合者修两处跨 lane 守门缺陷**（slow 守门缺**预热** ⇒ 绿是执行顺序的副产品 · 不断言**前提成立**）⇒ **自曝错账 #4**（`GlobalOpenTelemetry.set()` 存的是**包装器** ⇒ 引用相等类断言恒红）⇒ 3 条新坑 **PIT-GLOBAL-026/027/028** ⑥ ✅ **L10 闭合 DDL 两条盲区**（触发器函数体**结构化语义属性**比对 + task/message runner 重建）⇒ 9 域复跑逐域与 L3 基线一致 ⑦ 🔴 **L11 挖出重大新发现**：`discovery://<name>`（**两斜杠**）在 **5/7 个 rpc 模块**的 yaml 里 ⇒ devh 的 `DiscoveryClientResolverFactory` 要求 `targetUri.getPath()` 长度 > 1 ⇒ **真 fat jar 起不来** ⇒ **连 Flyway 都跑不到** ⇒ 详见 `cross-domain.md` §二·十五 |
| 2026-10-25 | ✅ **第二十六轮（整合收口）** —— ① **L5（signoff）lane 合回**（`22e1cc6`）⇒ 🆕 **4 条待签字候选**（`CAND-14..17`，全部「🏷 候选，建议签」⇒ **登记完就躺着**）· **T-11 = ✅ 已修**（Go `core/trace/agent.go:126-127` 确实装全局 provider ⇒ **不是误报** ⇒ 登记不撤销）· **T-12 = 出口层级差异**（判据落到 Go `rest/engine.go:113-115` 的**注册点**）⇒ **需拍板 0 条**
| 2026-10-25 | ✅ **第二十六轮 · 🔴 自曝错账 #3** —— T-6 初版写的原始串**只在真 Tomcat 上「看起来对」**（Tomcat 归一化 ⇒ 写什么都一样）⇒ **MockMvc 路径没有归一化** ⇒ **打红了 favorite 自己的守门** ⇒ 而 favorite 对 global **零写权** ⇒ 修法只能落共享层 ⇒ 改为**无空格**形态（= 各域自己 advice 会写的值）⇒ 真 Tomcat 不变 · MockMvc 零变化 ⇒ **favorite 一行不用改** ⇒ 🆕 门 4 锁住 ⇒ 教训 **PIT-GLOBAL-025**
| 2026-10-25 | ✅ **第二十六轮 · 🔴 守门自身的缺陷** —— `LikeFactDispatchContractGateTest` 的调度器**句柄永远拿不到**（阻塞式 `Dispatcher.run` + 「句柄设在 `run` 之后」）⇒ **跨用例泄漏** ⇒ 与本用例的调度器**并发抢行** ⇒ 「毒丸失败 ⇒ 同轮 break」**因为并发**而偶发红 ⇒ 改用 `runAsync` 即修 ⇒ 教训 **PIT-GLOBAL-024** ⇒ ⚠️ 生产路径本来就用 `runAsync` ⇒ **无此缺陷**；⚠️ `favorite-rpc` 的 scheduler 用阻塞式 `run`（但触发点是 `ApplicationReadyEvent` ⇒ **不卡启动**）⇒ **只记账 + 告知 owner**
| 2026-10-25 | ✅ **第二十六轮 · 共享索引整合** —— 三个 `_INDEX.md` + `AGENTS.md` §0/§3.5/§6.3 + `tools/difftest/README.md` + L2/L3/L4 的共享台账补丁（**X-1 运行时已闭环** / **favorite-rpc 故障形态订正** / **DDL 探针 4 → 9 域**）+ **21 处坏相对链接**修正（链接自检 `MISSING_LINKS = 0`）+ **`AGENTS.md` 压回 32 KB 预算内**（34,151 → 32,680 B）⇒ 逐条 ⇒ [`lanes/L1-delivery.md`](lanes/L1-delivery.md) §十四 · [`lanes/L5-signoff.md`](lanes/L5-signoff.md)
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
