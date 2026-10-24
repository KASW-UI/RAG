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
| **例子** | `DRIFT-CF-001`（communityfact）、`DRIFT-LOG-001`（logger）、`DRIFT-SF-001`（snowflake） | `T-2` / `T-5`（`GoStyleMaxConnsFilter` 的 MaxConns 作用域）——**`follow` / `hot` 域的条目，但落点在 common** |
| **判据** | 差异在 common 的**行为**本身 | 差异在**别的域的行为**，common 只是**载体** |

⇒ **一句话**：common 域问「common 自己对不对」，global 域问「**别人**因为 common 而对不对」。

---

## 三、待收口清单（从 `common/alignment-2026-10-23.md` §十二·二 转入）

> **层二：common 是唯一的共享写点 —— 外部域的尾巴清单（10 条）**
> ⚠️ **这 10 条不属于 common 域的漂移，但修法落在 `service/common` 的共享类里**
> ⇒ 改一处影响 6–10 个域 ⇒ 按 §5.3 必须单域串行 ⇒ **并发对齐在这一点上必然排队**。

| # | 域 | 条目 | 落点（`文件:行`） | 差异 | 状态（2026-10-25 实测） |
|---|---|---|---|---|---|
| **T-1** | `like` | `DRIFT-LIKEAPI-015` | `common/http/GoStyleJsonBodyConverter.java:115-117`（`getContentType().toString()` ⇒ MediaType 已小写化）vs `goReadsBody` `:98-101`（`contains` 大小写敏感） | Content-Type 大小写敏感判定不同 | 🔴 **已复核：确认为真漂移**（2026-10-25） |
| **T-2** | `follow` | `DRIFT-FOLLOW-008` | `common/protection/GoStyleMaxConnsFilter.java` | Go `MaxConnsHandler` **每路由一个** latch，RAG **全局一个** | 🔴 **仍未修** + 🔴 **发现档位错置**（代码自称「有意」但**无签字** ⇒ 待拍板） |
| **T-3** | `hot` | `D14` / `DRIFT-HOTAPI-014` | `common/observability/HttpMiddleware.java:431`（`writeTimeoutResponse`） | 503 / 499 响应缺 `X-Content-Type-Options: nosniff` | ✅ 已修 —— **2026-10-25 实测确认在位**，common 侧无需再动 |
| **T-4** | `hot` | `D25` / `DRIFT-HOTAPI-025` | `common/observability/HttpMiddleware.java:332`（`writeTimeoutResponse(resp, 499)`） | 客户端断连后 RAG 等 2s deadline、断连后继续发下游调用、无 499 语义 | 🔴 **仍开放**（落点已定位） |
| **T-5** | `hot` | `D27` | `common/protection/GoStyleMaxConnsFilter.java` | 同 T-2（MaxConns 作用域） | 🔴 **仍开放** + 🔴 **发现档位错置**（同 T-2） |
| **T-6** | `message` | `-010` | `common/observability/HttpMiddleware.java:421` + `common/http/GoStyleRouterErrorResponses.java:83` | 成功 / 失败响应的 `Content-Type` 差异 | 🟡 **仍 OPEN**（待拍板） |
| **T-7** | `message` | `-011` | 同上路径 | 状态行 reason phrase 差异 | 🟡 **仍 OPEN**（同上拍板） |
| **T-8** | `message` | `-013` | 同上路径 | 缺 `Traceparent` | 🟡 **仍 OPEN**（同上拍板） |
| **T-9** | `comment` | `D3` | `common/protection/GoStyleGrpcClientBreakerInterceptor.java:193`（`method.getFullMethodName()`，**无 target 前缀**） | Go `path.Join(cc.Target(), method)` ⇒ 单 client 的熔断计数**串了** | 🔴 **已复核：确认为真漂移** + 🔴 **发现 §3.2 缺陷**（javadoc 照抄 Go 那行却未说自己没实现） |
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
| 共享 Flyway 账本 | `message-rpc`↔`like-rpc`↔`article-rpc` | 配置层 + 运行时已闭环；**三模块同库共存**仍 `unresolved`（§6.3 问题 4） |
| 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | **均未审** |
| 事实投递链路 | `like` 的 api ↔ rpc ↔ 第三个仓库 | 链路本身不是任何组件的属性 |
| 社区消费者差异 | Go 用 `like`，RAG 不用 | ⏸ **仍未决**（交接 #9，与 `DRIFT-LIKE-003` 同一条） |
| 裸 `ServerCallHandler` 不被 dispatch | task 域 | ⏸ **仍未决**（交接 #10） |
| `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | 🔴 **未修 · 跨域 · 待拍板**（2049~10000 段且非空值 ≤2048 ⇒ Go 200 / RAG 5001） |
| DDL 双侧活体逐列对照 | `like` 域 + 其余 5 域 | 🔴 **未修 · 跨域**（`check-ddl-static.py` 只有 `users`/`task`/`message` 三域预设） |

---

## 六、仍未决的 4 条 + 未销账的 2 条

> 来源：[`common/alignment-2026-10-23.md` §十二·三](../common/alignment-2026-10-23.md)「9 项交接的真实状态（逐条实测，2026-10-25）」
> ⚠️ 该节更正了 AGENTS.md §0 的「**8 项未决交接**」—— 那个数把 4 条「已销账但没记」也算成了未决。

| # | 事项 | 2026-10-25 实测 | 判定 |
|---|---|---|---|
| 2 | `UserDomainService` javadoc 假断言 | `UserDomainService.java:113` **原样还在** | ✅ **已销账**（2026-10-25，两步走完 ⇒ 见 [`cycle-deps.md`](cycle-deps.md)） |
| 3 | `DRIFT-COMMON-005` 定案（`bindToCurrent` 签名是否允许破坏性变更） | 跨域（common + admin-rpc + user-rpc）的破坏性变更 | ⏸ **仍未决**（唯一被反复确认的未决项） |
| 4 | `like` 的 `SD-2026-10-23-09` 描述订正 | like 域本轮未动 | ⏸ **仍未决** |
| 8 | `docs/Pitfalls/common/_signed-deviations.md` 疑似错放 | 该文件**仍是 0 字节**，原地未动 | ❌ **未销账**（真名在 `docs/Intentional_Deviations/common/`） |
| 9 | 社区消费者：Go 用 `like`，RAG 不用 | 与 `DRIFT-LIKE-003` 同一条，favorite 域本轮未做 | ⏸ **仍未决** |
| 10 | 裸 `ServerCallHandler` 不被 dispatch（task 域） | task 域本轮未动 | ⏸ **仍未决** |

⇒ **真实分布（2026-10-25 更新）**：已销账 **6 条**（#1/#5/#6/#7/#11/**#2**）· **未销账 1 条**（#8）· **仍未决 4 条**（#3/#4/#9/#10）。

---

## 七、开工前的纪律

1. **本域是 `service/common/**` 的唯一合法写点** —— 别的域只读（§二 边界表）。
2. **凡要改 `service/common` + 别的域的代码 ⇒ 同一次提交**（§四 那个循环依赖的教训）。
3. **每条收口都必须配真 PG / 真启动的守门测试**（§3.3）—— 纯 mock 单测不算。
4. **台账写入边界**（§5.3 3b）：本域只写 `docs/DRIFT/global/` + `docs/Pitfalls/global/` +
   `docs/Intentional_Deviations/global/`；三个 `_INDEX.md` 合并后统一更新。
5. ⚠️ **跨域改动前先 `git fetch upstream` 确认基线** —— `service/common` 是共享面，
   别的域的并发会话可能同时在改（§3.3 的「共享文件改动要尽早提交」）。

---

## 八、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **域创建**。用户定义：`global` = 跨微服务 + `service/common` 影响的漂移收口域。首版收录：T-1~T-10（common 尾巴 10 条）· 4 条仍未决 + 2 条未销账 · 7 条跨域不变量 · 1 条两步闭环循环依赖 |
| 2026-10-25 | **第一轮收口（`align/global` @ `58e8314`）**：<br>① **T-10 / CYCLE-001 闭环** —— 两步同一次提交（users 侧订正 javadoc 假断言 → common 侧删 `markExpired`）+ 真 in-process gRPC 守门测试 4 条 + 变异测试 2 组（均转红）⇒ [`cycle-deps.md`](cycle-deps.md)<br>② **T-1 / T-2 / T-5 / T-9 复核确认**为真漂移，补齐逐行 Go 出处 ⇒ [`common-tails.md`](common-tails.md)<br>③ 🔴 **发现「Go 出处在框架层」** —— 真源分 L1 项目层 / L2 go-zero v1.10.2 框架层 ⇒ [`cross-domain.md`](cross-domain.md) X-8 + [`Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md) PIT-GLOBAL-001<br>④ 🔴 **发现 T-2/T-5 档位错置**（代码自称「有意」但无签字）⇒ 待用户拍板<br>⑤ 🔴 **发现 T-9 的 §3.2 缺陷**（javadoc 照抄 Go 那行却未说明自己没实现 target 前缀）<br>⑥ 踩坑留痕 4 条 ⇒ [`Pitfalls/global/`](../../Pitfalls/global/) |
