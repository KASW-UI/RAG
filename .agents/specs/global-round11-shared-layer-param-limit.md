# global 第十一轮 —— favorite 零权限重申 ⇒ 推翻自己「X-6 永久阻塞」的结论 + 共享层修法定案

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`12ea6ae`
> **性质**：**台账改判 + 修法定案 + 踩坑留痕**（**本轮不含代码改动** ⇒ 落地另起一轮 PR）
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（用户重申「你没有 favorite 的所有权限」）

---

## 一、要解决什么

用户 2026-10-25 06:23（两件事）：

1. 问 **Spring 官方 `DisconnectedClientHelper`（Since 6.1）为什么也做不到「提前发现客户端断连」**；
2. 声明 **「你没有 favorite 的所有权限」**。

⇒ 第 2 条直接命中 `docs/DRIFT/global/cross-domain.md` §二·十一（X-6）——
**第九轮结论写的是「修法必然要动 `favorite-api` ⇒ 在 favorite 边界下永久阻塞」**，
并给出了「A 放开 favorite 写权 / B 9 改 1 不改 / C 签字认账」三条出路。

⇒ 本轮**先按用户边界把出路 A 撤下**，但**复核时发现第九轮的结论本身站不住** ⇒ 顺势推翻。

---

## 二、🔴 核心结论：**X-6 不是 favorite 边界下的永久阻塞**

### ① 第九轮结论错在哪

> 「修法必然要动 `favorite-api`」——**这句话只证明了「逐模块改 `application.yml`」这一条修法被边界挡住**，
> **没有证明「不存在别的修法」**。

⇒ 这是一条**方法论错误**：把**属性绑定的入口**当成了**配置的唯一生效途径**。
⇒ 已登记踩坑 ⇒ [`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-012](../../../docs/Pitfalls/global/cross-domain.md)。

### ② 共享层修法存在（逐条实测取证）

| 前提 | 实测 | 结论 |
|---|---|---|
| 10 个 api 模块依赖 `service/common` | 逐模块查 `pom.xml` ⇒ **10/10 ✅** | 共享层类在每个 api 的 classpath 上 |
| 10 个 api 模块扫 `com.rag.common` | 9 个 `@SpringBootApplication(scanBasePackages = {…, "com.rag.common"})`；`task-api` = `@ComponentScan(basePackages = {…, "com.rag.common"})` ⇒ **10/10 ✅** | ⇒ **共享层 Bean/Filter 自动进所有 api 上下文**（本仓**不用** `AutoConfiguration.imports`，靠组件扫描 ⇒ `service/common/.../protection/GoStyleProtectionConfiguration.java:45-49`） |
| 上限可在运行期改 | `Connector.setMaxParameterCount(int)`（Tomcat `catalina/connector/Connector.java:538`；默认 `1000` 在 `:251`；`Request.java:148/480` 每请求取值） | ⇒ 容器上限不是只读的 |
| `-1` = 不限 | Tomcat `tomcat/util/http/Parameters.java:79`（javadoc「or -1 for no limit」）+ `:247`（`if (limit > -1 && parameterCount >= limit)`） | ⇒ 与 Go 的 `net/http`（不限制）+ go-zero 应用层 2048 判据**同构** |
| Boot 顺序可被超越 | Boot 4.0.8 `org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer.getOrder()` **返回 0**（javap 实测）；它用 `ConnectorCustomizer` lambda 设值 | ⇒ 共享层用 `@Order > 0` 的 `WebServerFactoryCustomizer` ⇒ **排在 Boot 之后 ⇒ 覆盖生效** |

⇒ **修法（两处，全在 `service/common`）**：

1. `WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>`（`@Order > 0`）⇒ `factory.addConnectorCustomizers(c -> c.setMaxParameterCount(-1))`
   ⇒ **10 个 api 模块统一解除 Tomcat 上限**，**不动任何 `application.yml`**（含 `favorite-api`）。
2. **共享 Filter** 复刻 go-zero `GetFormValues`（`net/url` 解析层 + **2048 个非空值**计数）
   ⇒ 可从 `article-api/.../config/GoStyleGetFormValuesFilter.java` 移植（它已做全 `net/url` 段数上限 10000 / URL escape 校验 / 2048 非空值计数三件事）。

⇒ ⇒ **10 个 api 模块零代码改动 ⇒ favorite 零触碰 ⇒ 不制造分叉 ⇒ 不是拍板项、不需签字认账。**

### ③ 已有实现分布（影响工作量）

| api 模块 | Go 等价应用层判据 | 位置 |
|---|---|---|
| `article-api` | ✅（含 `net/url` 解析层） | `article-api/.../config/GoStyleGetFormValuesFilter.java` |
| `hot-api` | ✅ | `hot-api/.../form/GoFormValues.java` |
| `favorite-api` | ✅（**只含 2048 计数**） | `favorite-api/.../service/FavoriteApiSupport.java:327-358` |
| 其余 7 个 | ❌ | like / message / comment / follow / task / user-api / admin-api |

### ④ 落地前必须先答的 3 个问题（已预判，留给落地轮实测）

1. **yaml 声明值 ≠ 运行时值**：10 份 yaml 仍是 `2048`（`favorite-api` 不可碰 ⇒ 改不了），共享层在运行期设 `-1`
   ⇒ 边界内唯一可行形态；代价是 yaml 变「名义值」⇒ **必须同步改 `tools/difftest/check-tomcat-limits.py` 的期望语义**（同一次提交）。
2. **共享 Filter 与 3 个模块已有的本地实现重复** ⇒ 共享 Filter 先执行 ⇒ 本地实现变不可达 ⇒ **400 body 必须逐字节一致**。
3. **共享 Filter 会改变 favorite 的可观察行为**（本地 `5003` ⇒ Go 的 `400`）⇒ **必须记账并告知 favorite 域 owner**（global 不碰它的代码，但共享层会改它的行为 ⇒ 不能静默）。

---

## 三、连带影响面复核（本轮新增 §二·十一 ④ 之二）

| 条目 | 是否要动 favorite | 处置 |
|---|---|---|
| **X-4**（社区消费者差异） | 🔴 **要** —— 必须删 favorite 自己多出的 10 处 `communityfact` 调用 | **仍阻塞**（favorite 边界） |
| **X-6**（本条） | 🟢 **不用**（共享层接管） | **改判为可修** |
| **X-9**（3 份「Go 是否读 body」复制品） | 🟡 **部分要** —— hot/article 可收敛，favorite 那份不能 | **本轮不动**（现状行为正确）⇒ favorite 那份永久留作复制品 |
| **X-7**（DDL 双侧活体对照） | ❌ **不要**（只读库） | **不受影响，仍可推进** |

---

## 四、Spring `DisconnectedClientHelper` 的定性（回答用户第 1 问）

| 项 | 事实 | 出处 |
|---|---|---|
| 公开 API | 只有 3 个：构造器 + `checkAndLogClientDisconnectedException(Throwable)`（实例）+ `isClientDisconnectedException(Throwable)`（**static**） | `javap`（spring-web **7.0.9**） |
| 分类依据 ① | 异常**类名**集合：`AbortedException` / `ClientAbortException` / `EOFException` / `EofException` / `AsyncRequestNotUsableException` | spring-web 源码 `:48-49` |
| 分类依据 ② | 异常 **message** 关键词：`broken pipe` / `connection reset by peer` | 同上 `:45` |
| javadoc 覆盖 | `ClientAbortException or EOFException for Tomcat` / `IOException "Broken pipe" or "connection reset by peer"` | 同上 `:94-96` |

⇒ **它的输入是一个已经存在的 `Throwable`** ⇒ **结构上只能「事后按异常分类」，无法「提前」**：
Servlet 栈里断连的唯一异常来源就是 IO 失败（写 socket / 读 body）⇒ **与 499 判据同源，不是第二条通道**；
且在 T-4 资源面场景里（worker 还在跑 gRPC、**还没有任何写**）⇒ **没有异常 ⇒ 无从分类**。
⇒ **它的定位是「别把正常的客户端断连当系统错误刷屏」，不是「检测客户端断连」。**
⇒ ⇒ **它是 T-4「资源面」的必要配套，但不能替代方案⑤**。

---

## 五、改动清单（**全是台账，零代码**）

| 文件 | 改动 |
|---|---|
| `docs/DRIFT/global/cross-domain.md` | §二·十一 **改判**（③ 增「③ 之二 共享层修法」；④ 重写为出路 D；④ 之二 新增连带影响面）· 顶部 X-6 行 · §三 X-6 行 · 排期建议 · 变更记录（第十一轮行 + 第九轮补行订正） |
| `docs/DRIFT/global/_README.md` | HOTAPI-030 行改判 · §七 纪律 **1b** 口径收紧（favorite 零改动；「碰不到文件」≠「碰不到行为」）· 变更记录新增第十二轮行 |
| `docs/Pitfalls/global/cross-domain.md` | 🆕 **PIT-GLOBAL-012**（「per-app 配置」≠「只能逐模块改」+ 四问判据 + 共享层改别人行为要记账）· 变更记录 |
| `.agents/specs/global-round11-shared-layer-param-limit.md` | 本文件 |

⚠️ **未写**：`docs/Intentional_Deviations/global/cross-domain.md` ——
第九轮一度准备登记的候选 **`SD-2026-10-25-CAND-13` 未登记**（**编号已回收，勿引用**）
⇒ 理由：**问题可修 ⇒ 不该「认账不改」**（签字是给「明知不一致且决定不改」用的）。

---

## 六、验证

| # | 验证项 | 方式 | 结果 |
|---|---|---|---|
| 1 | Spring 侧 API 与分类依据 | `javap` + spring-web 7.0.9 sources 逐行 | ✅ 3 个公开 API / 异常名集合 / message 关键词 / javadoc 覆盖范围 |
| 2 | Tomcat 上限可写 + `-1` 语义 | 源码 `Connector.java:251/538` + `Request.java:148/480` + `Parameters.java:79/247` | ✅ |
| 3 | Boot 顺序 | `javap -c`（Boot 4.0.8 `spring-boot-tomcat`）⇒ `getOrder()` = 0 | ✅ |
| 4 | 共享层可达性 | 逐模块查 `pom.xml` + `@SpringBootApplication`/`@ComponentScan` | ✅ **10/10** |
| 5 | 已有应用层判据分布 | 全仓 `grep`（`too many form values` / `MAX_FORM_PARAM_COUNT` / `countNonEmptyValues`） | ✅ **3/10 有、7/10 无** |

⚠️ **本轮是台账改判 ⇒ 无代码 ⇒ 无需跑测试**；
⚠️ **共享层修法本身尚未做真容器实测**（§3.3）⇒ **落地轮必须配真 Tomcat 守门 + 变异测试**。

---

## 七、下一轮（落地轮）待办

1. **X-6 修法落地**：`service/common` 两处（Tomcat 上限解除 + 共享 Filter）＋ 真 Tomcat 守门 ＋ 变异测试 ＋ 同步改 `check-tomcat-limits.py`。
2. **B5/X-5 修法落地**：task + follow 换 `ServerCalls.asyncUnaryCall` ＋「handler 被调用过」断言 ＋ 变异转红。
3. **B9/T-8**：`service/common` HTTP 入口注入 W3C `traceparent`（对齐 go-zero `tracehandler.go`）。
4. **B10**：`users_user.extra_info` jsonb→text 漏改（真 PG 复现 + 修）。
5. **B6**：like 域 `SD-2026-10-23-09` 描述订正。
6. **X-1**：三模块同库共存运行时验证（真 PG + 三进程并发）。
7. **X-4 like 半边**：与 Go `communityfact` 四条调度契约逐条对拍。
8. **X-7 剩余 5 域**：DDL 双侧库环境 + runner（**favorite 域只建库，不碰其代码**）。
