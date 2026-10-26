# AGENTS.md: RAG 项目政策

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件，**只放政策与索引**。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **真源 = Go**（2026-10-20 第三次切换，非 Spring Boot 的 Java 实现不在范围内，详见 §1.1）。
> **2026-10-23 瘦身**：逐条漂移与修复记录移出到 [`docs/DRIFT/`](docs/DRIFT/_INDEX.md)，
> 踩坑与元教训在 [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md)，签字偏离在
> [`docs/Intentional_Deviations/`](docs/Intentional_Deviations/_INDEX.md)。

## 〇、当前状态（2026-10-27）

**权威索引：[`docs/DRIFT/_INDEX.md`](docs/DRIFT/_INDEX.md)** —— 按域列出全部台账、搬移账目、证据边界。本节只给结论。
**AGENTS.md 只留结论 + 一行指针**（预算 32 KB，超预算 = 没走这条约定）。

| 域 | 台账（**逐条证据在此**）| 域 | 台账（**逐条证据在此**） |
|---|---|---|---|
| `users` | [`users/`](docs/DRIFT/users/_shared.md) | `comment`/`follow`/`task` | [`_cross/`](docs/DRIFT/_cross/component-coverage-matrix.md) |
| `message` | [`message/`](docs/DRIFT/message/rpc.md) | `favorite` | [`favorite/`](docs/DRIFT/favorite/rpc.md) |
| `like` | [`like/`](docs/DRIFT/like/rpc.md) | 🆕 `global`（**收口域**，非微服务 ⇒ §3.5） | [`global/`](docs/DRIFT/global/_README.md) |
| `common` | [`common/`](docs/DRIFT/common/alignment-2026-10-23.md) | 🆕 `<域>-common`（**10 条** lane） | [`_cross/domain-common-lanes.md`](docs/DRIFT/_cross/domain-common-lanes.md) |
| `hot` | [`hot/`](docs/DRIFT/hot/rpc.md) | `article` | [`article/`](docs/DRIFT/article/rpc.md) |

> ⚠️ **「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」** ⇒ 台账里的 ✅ 一律读作「**在该证据强度下一致**」，
> 不是已闭环。**每条的证据强度以该域台账的「证据强度」列为准**（分层口径见 `docs/DRIFT/_INDEX.md` 末节）。

1. **已签字的有意偏离**（另 **18 条未签字候选**）—— 是决策不是缺陷，但两侧确实不一致 ⇒ [`docs/Intentional_Deviations/`](docs/Intentional_Deviations/_INDEX.md)
   ⚠️ **共性**：多数是「**RAG 做了更严/更安全的那一侧**」或「proto `package` 归一（表达层）」⇒ 对拍看到它们变红，**先确认是不是签字项，不要当新回归去「修」**。
2. **DDL 差异**（2026-10-25 双侧活体对拍 · **9 域**）：**类型/长度 0 差异**。**仍未修**：`users_user.status` 可空性 · `DEFAULT CURRENT_TIMESTAMP` 残留 **16 处**（**刻意保留**）· users 域缺 3 条非唯一索引（**性能非行为**）。`hot` = **N/A**（两侧无库）⇒ 不许写成「已对齐」。⇒ [`ddl-type-divergences.md`](docs/DRIFT/_cross/ddl-type-divergences.md)
3. **跨组件不变量仍无法单侧闭环**（§3.5 表）⇒ **收口域 = `global`**。
   ⇒ **每个模块接手的第一件事仍是「先让它真的启动一次」**（2026-10-23 修复轮**之前**实测 `hot-rpc`/`like-rpc` **根本起不来，而测试全绿**）⇒ **「跑绿」不是「对」**。
> 历轮修复记录 ⇒ [`docs/DRIFT/_cross/fix-round-2026-10-23.md`](docs/DRIFT/_cross/fix-round-2026-10-23.md) · 教训 ⇒ [`fix-round-lessons.md`](docs/Pitfalls/_cross/fix-round-lessons.md)

## 一、项目身份与双轨策略

本项目对**已废弃商用架构** `Sea-RideTheWind_golang` 做 Spring Boot 重构。RAG 不是教学练手项目，而是商用架构的现代化迁移产物——按工作项目标准交付。

| 项目 | 路径 | 角色 | 实现风格 |
|---|---|---|---|
| **Sea-RideTheWind_golang** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | **真理之源（唯一真源，2026-10-20 第三次切换）** —— 商用架构的原始实现，2026-10-18 起冻结，所有行为的唯一判定标准 | go-zero + GORM |
| **RAG（本仓库）** | `/Users/pr/Documents/java&golang/RAG` | **Spring Boot 重构产物** —— 把 Go 行为用 Spring 生态表达 | **MyBatis-Plus + domain/mapper/service 分层 + 幂等 DDL + Bootstrap 抽出** |

**RAG 的存在范围**：用 Spring 生态表达 Go 的行为。**不**对任何「非 Spring Boot 的 Java 实现」做对照 —— 那是另一种工作，不在本项目范围内；也不接收 RAG 反向同步。

**微服务范围（2026-10-23 用户定，12:25 扩到 10 个 / 38 个组件）** —— 10 api + 10 rpc + 8 common + 10 proto，清单与覆盖见 §3.5。
`task` 域无 `-common`（Go 侧同样没有，不是遗漏）。Go 侧 `knowledge`/`points`/`security` **范围外 ⇒ 不是待办**。

⚠️ **「未翻译」≠「该做」**：范围是**决策**，不是进度。
### 1.1 三次真理之源切换的结论

真源三次切换（10-18 前 Go → 10-18~10-20 Java 镜像 → **10-20 起回到 Go**）。
退回 Go 的依据：2026-10-20 行为一致性审计实测 —— **Go 0 缺陷，RAG 11 处回归**，
且 11 处**全部**集中在「把 Java 写法换成更地道的 Spring 写法」的位置。
⇒ **教训**：多一跳的代价不是「多一次翻译」，而是多一整层可以无声漂移的地方 ⇒ 政策结论：**只留一个源**。
⇒ 三次切换时间线与 11 处回归逐条清单 ⇒ [`docs/DRIFT/_cross/truth-source-switches.md`](docs/DRIFT/_cross/truth-source-switches.md)

- **Sea-RideTheWind_golang**：**真理之源**，冻结、不接受反向同步 —— 一个不会移动的源才是最可靠的。
- **RAG**：Spring Boot 重构产物。行为以 Go 为准，表达以 Spring 为准；偏离 Go 的「更地道的 Spring 写法」都是潜在回归点（§3）。

## 二、HUMAN_ONLY.MD：设计目标，不是现状

每个非叶子子模块各有一份 `HUMAN_ONLY.MD`，两部分：**① 文件阅读顺序与职责**（现状）；**② Java 理论文件树**（目标：`domain/Entity + mapper/Mapper + service/DomainService + service/impl/GrpcImpl + bootstrap/* + db/migration/*.sql`）。
当前已落 **4 份**（`users/user` 与 `users/admin` 各 2 份）。
⇒ **存在性台账、已撤销的误登记与未覆盖清单** ⇒ [`docs/DRIFT/_cross/human-only-status.md`](docs/DRIFT/_cross/human-only-status.md)

## 三、语义对齐纪律（Spring idiomatization 的唯一防线）
RAG 与 Go 的差异只允许出现在**表达层**。本节规定如何确保差异不越界。

### 3.1 Go ↔ RAG 对照（强制）

任何行为一致性审查必须**以 Go 为唯一基准**核对 RAG。Go 已冻结，行为漂移只可能从 RAG 一侧发生，
因此两方对照足以发现所有行为问题。**审查方式**：对每一个行为决策点，读 Go 对应位置的源文件 →
与 RAG 端对应实现**逐字对照**。

**对照必须覆盖**（不限于）：proto 字段号 / message 名称 / 错误码语义；JWT claim 名称与必需字段
（如 `userId`、`status` 的 presence 语义）；数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT；
行级悲观锁与事务边界；字符串解析（uid、keyword）失败行为；HTTP 路径与方法。

**避免的对照陷阱**（历史教训）：**不要拿「现有 RAG 代码」当对照基准**（现存代码里就有 11 处回归，对照它会「对齐到错」）
· **不要拿代码注释当对照基准**（现存注释写的是 Java 镜像视角，Go 视角可能完全不同）
· **不要「读两遍 Go 取并集」**（两遍都错的机会不等于零，必须有一遍回到原文件 line-by-line）

### 3.2 idiomatization 留痕（强制）

RAG 每次选择「用更地道的 Spring 写法」时，必须能回答：**这段比 Go 多做了什么？语义还一样吗？**
回答不了 = 无出处的 idiomatization = **不可 review，不得合入**。落法：写入该类/方法的 javadoc，三段式 ——

```java
/** <p>L4 idiomatization：HandlerInterceptor 取代 Go 的 rest.WithJwt + CheckBlacklistMiddleware。
 * <p>Go 出处：service/user/user/api/internal/handler/routes.go（两段 AddRoutes，public 路径不带 JWT）。
 * <p>语义等价论证：public 路径集合逐条一致；blacklist key 前缀与 fail-open 行为一致；
 *   <b>HTTP 状态码差异</b>：Go 写 401，本实现走异常处理器 —— 见下方已知差异。 */

**11 处历史回归全部属于「无出处 idiomatization」** —— 注释写得很完整，唯独没一处标注 Go 出处，
导致 review 时无从判断「这是必要表达差异，还是悄悄改了行为」。

### 3.3 框架机制必须验证实际生效（强制）

**3.3.1（2026-10-22 新增，全仓适用）「配置里没写」≠「没开」，也≠「没做」**

Go 侧大量行为的默认值**藏在 struct tag 里**而不是 yaml 里；反之，Java 侧大量行为**藏在框架里**而不是注解里。只看配置文件/注解名称双向推断，两头都会错。

⇒ **强制要求**：
1. 判断某个 Go 默认行为是否生效，**必须读到消费该 tag 的那段控制流**，只贴 struct 定义行不算证据；
2. 判断某个 Java 框架能力是否生效，**必须有运行证据**（真实管线 / 真容器），
   「pom 里有依赖」「类上有注解」都不算（§3.3 原有纪律）；
3. 写 `文件:行号` 证据时，行号指向**消费点**而非**声明点**。

**3.3.2（2026-10-22 新增）以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，本身是高危项**
⇒ **硬要求**：任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，否则**不得**用该措辞，改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。

#### 3.3.3 原有纪律（2026-10-20 起）

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler）全部是运行期动态装配，出错时**不报错**（四类静默失效的实证 ⇒ 同上文件）。
**规则**：凡引入新的框架机制，必须配一条**能真正触发该机制**的测试。纯 Mockito mock 数据层的单测**不算**——它不会执行 SQL，也不会装配 Spring 上下文，天然测不到本节三类问题。

**规则**（四条，各自都有实证）：
- 判断「一个注入/开关有没有真生效」**不能看启动日志**——注入点的 echo 照打、jar 照起、日志零告警，效果却是零 ⇒ 唯一可靠判据是**运行时证据**（本例是 PG 侧是否出现 `57014 query_canceled`）。
- **断言「Bean 存在」≠ 断言「机制生效」**（H2 假绿）。
- **「脚本批量改 N 处」必须配覆盖全部 N 处的守恒断言**，而非逐点行为测试（「46 处批量插入」）。
- **「代码里写了」与「客户端收到」之间隔着一次 commit**（E-1 收口抓到 text/plain 400 丢 Content-Type）；**绿测试可能是「错误答案的守卫」**，比没有测试更危险（message-api 的 `,string` 漂移）。

**强制三条**：
1. 论证里的每个 Go 事实都必须带 `文件:行号`，**不许只写结论**；
2. 凡写「与 Go 一致」，必须有**两侧同时跑的探针**（不是只跑 RAG）——
   最小形态：Go 侧直接调用真源的解析包，RAG 侧用项目**真实的** mapper（不是自己 new 一个）；
3. 修复漂移时**先改/删错误断言，再改实现** —— 否则会先撞上「测试红了」而误以为自己改错了。

（案例与完整经过见 `docs/Pitfalls/_cross/testing-pitfalls.md`。）

⚠️ **「只跑 RAG 的探针」同样不算数**：它只证明「代码写成了这样」，证明不了「Go 是这样」。
[`message/rpc.md`](docs/DRIFT/message/rpc.md) 的变异测试是另一半 —— 变异测试能证明测试**不是假绿**，但**不能证明期望值是对的**。两者缺一不可。

**规则（2026-10-22 晚补 · `hot-rpc` 的 Boot 4 模块化迁移推出）**：**「注解存在」≠「机制生效」**，且 Boot 4 让这层更难发现 ⇒ **强制**：
1. 凡用 `@KafkaListener`，必须有一条**装配层**测试断言 listener **真的进了容器的 registry**
   （不是「listener 方法存在」），且须覆盖「缺少 `spring-boot-kafka` 会怎样」；
2. 引入任何 `spring-*-starter` 类框架时，先确认该能力在 **Boot 4 下仍在 `spring-boot-autoconfigure` 里**，
   已被拆走的必须显式补对应模块（版本由 BOM 管理，不写 `<version>`）；
3. **不得**把「编译通过 / 上下文能启动」当作「注解已生效」的证据。

⚠️ **Boot 4 拆模块缺口 = 硬启动失败，不是静默失效**（2026-10-23 实测：`like-rpc` 缺 `spring-boot-kafka` ⇒ `NoSuchBeanDefinitionException: KafkaTemplate` ⇒ 起不来；`hot-rpc` 同款）。
⇒ **判定时先实测启动，再决定风险等级**，不要凭形态推定。⇒ [`like/rpc.md`](docs/DRIFT/like/rpc.md) · [`hot/rpc.md`](docs/DRIFT/hot/rpc.md)

### 3.4 proto 是**复制粘贴**，不是翻译（2026-10-23 实测）

RAG 侧 10 个 `*.proto` **全部从 Go 侧复制**，非翻译。改动**严格限定在 option 块与空白层面**：删 `option go_package` /
加 `java_package` + `java_multiple_files=true` / 缩进与首尾空行统一（10/10 表达层，Go 专用项 Java 不消费）；
`user.proto` 改名 `users.proto` + 加注释（仅 user）。**message 名 / 字段名 / 字段号 / 类型 / 枚举值 10/10 逐条一致 = 行为层**。

⚠️ **12:25 补齐的 4 个 `*-proto` 此前只有 `pom.xml`**（依赖与 build 全空 ⇒ 空 jar）⇒ 本次**同时接上 protoc 插件段**（§3.3）⇒ 实测产出 **88 java + 4 `*Grpc`** ⇒ [`proto-verification.md`](docs/DRIFT/_cross/proto-verification.md)
⇒ ⚠️ **10 个 proto 中 8 个无回归网**（复制忠实 ≠ 有网可证）。

⚠️ **本条的效力边界（三条，越界即失效）**：
1. **复制忠实 ≠ 消费方式已验** —— 只证明 `.proto` **文件本身**两侧一致，**不证明** RAG 侧使用生成代码的方式与 Go 一致（`optional` presence 语义 / 字段号硬编码 / Jackson 配置差异）。
2. **不外推到 api / rpc 组件** —— proto 一致 **推不出** wire 层行为一致；§0 的组件状态一律以各域台账为准。
3. **不覆盖范围外的 3 个 proto**（`knowledge` / `points` / `security`）——它们**不是待办**。

### 3.5 组件级审查范围（2026-10-23 用户定 · 12:25 扩到 38 个组件）

**审查粒度 = 组件级**，不是微服务级、也不是 api/rpc 二分。**common / proto 不是「可选项」**（同域对照的默认公理：
共享 DTO、错误码契约、wire 契约）—— 少审它们 = **拿一面没人校过的镜子照自己**，与 §3.1 禁止的
「拿现有 RAG 代码当对照基准」同源。

**范围内组件清单（权威）** —— 10 微服务 / **38 个组件**：`article`/`hot`/`like`/`message`/`comment`/`favorite`/`follow` 各 4 · `users` 7（user+admin 两套）· `task` 3（**无 `-common`**，Go 侧同样没有）。

⚠️ **落地 ≠ 已对照** ⇒ 逐组件状态见 [`docs/DRIFT/_INDEX.md` 覆盖矩阵](docs/DRIFT/_INDEX.md)。
**为什么不能退到微服务粒度**：域粒度下 `hot` 与 `article` 都会被打成 ✅ —— **那是假绿灯**。

**跨组件不变量（不属于任何单个组件）** —— **收口域 = `global`**（2026-10-25 用户定）：

> **`global` 不是第 11 个微服务域，是「收口域」**：修法落在共享层、或影响多个域的搁置漂移全部归它；按**议题**分档：`common-tails` · `cross-domain`（3+ 域不变量）· `cycle-deps`（同一次提交走完）。
> ⇒ **台账** [`docs/DRIFT/global/`](docs/DRIFT/global/_README.md)。⚠️ **边界**：`common` 域问「common 自己对不对」，`global` 问「**别人**因 common 而对不对」。

**下表 6 条落点均为 `global/cross-domain`**：

| 不变量 | 涉及组件 | 状态 |
|---|---|---|
| 共享 Flyway 账本 | `message-rpc`↔`like-rpc`↔`article-rpc` | ✅ **配置层 + 运行时（真 PG 三进程并发同库）均已闭环**（L4）⇒ 六域可安全并发同库；⚠️ **`favorite-rpc` 例外**（缺 `baseline-version: 0`）⇒ 只记账不修 |
| 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | ✅ **已审完**（第九轮：交叉但无冲突 · client 契约 10 域零漂移） |
| 事实投递链路 | `like` 的 api ↔ rpc ↔ 第三个仓库 | ✅ **已落地**（第二十轮 A5′ · 守门 22 条） |
| `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | ✅ **已落地**（共享层修法 · favorite 零触碰） |
| 🔴 **rpc 真进程可启动性** | **7 个 rpc 组件** | ✅ **已修** —— `discovery://<name>` 两斜杠 ⇒ fat jar 起不来 ⇒ 已改三斜杠 · `hot-rpc`/`task-rpc` 补 `Main-Class` ⇒ 🟢 **两守门缺口同批闭合**（**20/20 全绿**） |
| DDL 双侧活体逐列对照 | 其余 5 域（favorite 只建库） | ✅ **已做**（L3 ⇒ **9 域**；`article`/`follow` **0 差异**；`like`/`favorite` 差异全是「RAG 更严」⇒ 签字候选；`hot` = **N/A**） |

**排序约束**：**rpc 必须先于 api**（Go 侧边界是 api→rpc；rpc 没验对就先验 api ⇒ 产出「RAG api 行为正确」的假结论——`message-rpc` 曾有 21 条全绿测试却一次没起过）。
**跨域收口**：`service/common` + `users-common` 的修法 ⇒ **单独成波（G4）+ 写权唯一**（§5.3e·§5.3f）。
## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService` / `AdminDomainService` | HUMAN_ONLY.MD 描述单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口 | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + users 域 DDL 脚本 | **迁移机制已于 2026-10-21 改为幂等重放**（Flyway 条目处置完毕，理由见 §6.3 问题 1） |

### 4.2 5 阶段范围

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **Phase 1–4** | `user-rpc` → `user-api` → `admin-rpc` → `admin-api`（各阶段 pattern 见 §4.1 锁定的决策） | ✅ **均已完成**（users 域四组件台账齐备） |
| **Phase 5** | 全量 `mvn clean package` + docker-compose 跑 gRPC 端到端 | 全绿 |

每阶段独立 stop point：跑绿后停下等用户 review，再决定继续。

### 4.3 用户问答原话

> 📖 原话存档（防被「优化」掉）⇒ [`docs/DRIFT/_cross/user-qa-verbatim.md`](docs/DRIFT/_cross/user-qa-verbatim.md)

## 五、用户协作规则（精简）

1. **每个 phase 一个 PR**；PR 自带 spec（`RAG/.agents/specs/<slug>.md`），提交前 spec 已落仓。
2. **issue 在 PR 之前**；PR body 引用 issue。
3. **分支策略（2026-10-23 改写，取代「不开 worktree + git stash」）**：串行仍在 `do-my-own-gh`；**多域并发对齐时一域一 worktree**
   （`align/<域>` + `../RAG.worktrees/<域>`，均从 `do-my-own-gh` 切，主干最后接合并）。**工作树内只写自己域的台账文件；
   共享索引（各 `_INDEX.md`/`AGENTS.md`/`_MANIFEST.json`）合并后统一更新** ⇒ 多路并发必撞，且事后无法区分台账归属。
   ⚠️ `git stash` 是工作区级状态，会被并发 agent 互偷 ⇒ 不再作隔离手段。⚠️ **共享文件的改动要尽早提交**（实测已被并发会话
   `git checkout` 整体回滚过一次）。
3b. **三档台账：每域必读 + 必写**（2026-10-23 用户定）—— 开工前**必读** `docs/DRIFT/` + `docs/Intentional_Deviations/` +
   `docs/Pitfalls/` 的**本域子目录 + `_cross/`**；产出**必写回**同名的**本域子目录**。⚠️ **只写自己域，三个 `_INDEX.md` 合并后统一更新**。⇒ 细则 ⇒ [`docs/Worktree.md`](docs/Worktree.md) §二·五·**三·1d**
3c. **`global` 收口域（2026-10-25 用户定）** —— 跨微服务 + 共享层影响的搁置漂移**全部归它**
   （§3.5）。⇒ **共享层只有 `align/g4-service-common` 能写**；
   修法同时涉及共享层与别的域代码时**必须同一次提交**（否则留下半截）。
   ⇒ 🆕 **一个域可开多棵 lane 树并行**（`global` 已拆 15 条 lane（**按模块零重叠** ⇒ 同模块两个 lane 会互踩 `target/`））⇒ 协议 ⇒ [`docs/DRIFT/global/lanes/_PROTOCOL.md`](docs/DRIFT/global/lanes/_PROTOCOL.md)
3d. 🆕 **波次工作树 G1~G4（2026-10-27 01:44 用户定）** —— **4 棵**（= 一波次一棵），均从 `do-my-own-gh` 切：
   **G1** `align/g1-users-follow`（`users` **不含 `users-common`** + `follow`）· **G2** `align/g2-message-like-task` · **G3** `align/g3-article-comment-favorite-hot` · **G4** `align/g4-service-common`（**共享层**）。
   ⇒ **顺序 G1→G2→G3→G4**，**G4 最后**（§5.3e）。**旧 10 条 `<域>-common` lane 工作树已撤**（方法与台账仍有效，改在 G1~G3 里做）。
   ⇒ **写权**：🔴 **`service/common/**` + `service/users/users-common/**` 只有 G4 能写**，G1~G3 **只读**；
   **域侧能自修的域侧修**，**共享层缺陷一律转派 G4**（反馈单 7 项，§5.3e）。
   ⇒ **台账**：按域各一份 `docs/DRIFT/<域>/vs-common.md`（**只写自己域** ⇒ 跨树零冲突）；共享面合并后统一更新。
   ⇒ **交付物** = 9 条机制面轴（A1~A9）的**本域侧**逐轴结论（✅/🔴/🟡 + **证据强度** + **核对日期**）+ **两侧同时跑**的探针（§3.3）。
   ⇒ **机器守门** = `check-wave-lane-ownership.py`（写权不重叠 + 排他归属 + 路径落空 + 分支存在）。
   ⇒ 总纲 ⇒ [`domain-common-lanes.md`](docs/DRIFT/_cross/domain-common-lanes.md) · 波次表 ⇒ [`cross-domain-coupling.md`](docs/DRIFT/_cross/cross-domain-coupling.md)
3e. 🆕 **共享层波 G4（2026-10-27）** —— `service/common` + `users-common` ⇒ **单独成波**、**写权唯一**（`align/g4-service-common`）。
   ⇒ **G1~G3 期间共享层只读、只登记反馈单**（7 项：现象 / Go 侧 `文件:行号` / RAG 侧现状 / 证据强度 / 建议修法 / 影响面 / 是否阻塞本波）。
   ⇒ **例外升级**：某波「不改共享层就过不了」⇒ 该波**就地升级为全量回归波**（**2411** 条），等共享层改完再重跑。
   ⇒ **基线不可变**：G1~G3 进行中改共享层 ⇒ **那波测试结论作废**（编译绿 ≠ 行为对：**全仓无 API 兼容门禁** ⇒ 行为级破坏无网）。
   ⇒ 波次表 · 依赖 DAG · 逐类归属 ⇒ [`cross-domain-coupling.md`](docs/DRIFT/_cross/cross-domain-coupling.md)（守门 `check-wave-coupling.py`）
3f. ⚠️ **共享判据 = 依赖度数，不是目录位置**（2026-10-27 实测）：`service/common`（20 模块依赖）与 `service/users/users-common`（**10** 模块 / **6 外域 api**）**都是共享层**（⇒ 写权归 G4）；
   其余 7 个 `<域>-common` 只被本域 2 模块用 ⇒ **真私有**（⇒ 归各自域）。
   ⇒ `users-common` 是**混合体** ⇒ **按类判定**（8 个跨域类归共享层波 / 3 个 users 专用类留 users 域），**不按模块一刀切**、**不搬目录**。
4. **作用范围**：仅限 RAG 仓库。`Sea-RideTheWind_golang` 已冻结只读；其他非 Spring Boot 的 Java 实现不在范围内。
5. **Go 是真理之源**（2026-10-20 第三次切换，全子模块适用）：proto 字段号、错误码、JWT claims、表结构、HTTP path、
   Kafka topic 等语义细节一律以 Go 对应子模块为准；差异**只允许出现在表达层**，出现行为差异时默认判 **RAG 错**。
6. **session 语言**：用户用中文；技术术语、代码、文件路径保留英文。
7. **review**：每个 PR 走完 `mvn -pl <module> -am package` + 全量 `mvn clean verify` 后等 review，
   **且 review 必须含 Go ↔ RAG 对照**。

## 六、架构决策与其他留档

> **状态表纪律（2026-10-21）**：本文件任何「已修 / 未修」断言必须带**核对日期**，否则按 `UNKNOWN` 处理。

### 6.3 架构决策（问题 1~4；问题 1/2/3 于 2026-10-21 拍板，问题 4 于 2026-10-23 新增）

**✅ 问题 1：建表机制 —— 2026-10-21 拍板「直接把 Flyway 丢掉」。** users 域改用 `SchemaInitializer` 幂等重放（无版本号、无账本，接近 Go `AutoMigrate`）。⚠️ **实际范围比原记录更宽** ⇒ **问题 4**。

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷**（Go 侧 `admin/rpc` 零出向 gRPC 依赖）。

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包**（已实施）。Go 里不存在「全局自动注册」；拆包**动机自我消解、已撤回**。⚠️ `message-rpc` 归一仍未做。

⇒ 三条的完整经过 ⇒ [`decisions.md`](docs/DRIFT/_cross/decisions.md)

**📌 问题 4：Flyway 账本撞号 —— 问题 1 的结论只应用到 1/4 的模块上。（2026-10-23 新增）**

问题 1 丢掉 Flyway 的**唯一根因**：`flyway_schema_history` 是 **schema 级**账本，一个 schema 只能有一本账。
⚠️ 该根因在 `message-rpc`/`like-rpc`/`article-rpc` 原封不动成立（同库 `first_db`、同 schema `public`、同账本表）⇒ 任一方先启动，另一方的表就永远建不出来。
**定案**：每模块独立账本表名 `spring.flyway.table: flyway_schema_history_<模块>` —— 不动迁移文件名，各模块从 V1 各自重来也互不干扰，**直接消除病根**。（否决「排全局唯一号段」：不解决根因。）

> **实施状态（2026-10-25 实测）**：**7 个模块各有独立账本表**（`message`/`like`/`comment`/`favorite`/`follow`/`task` + `article` 独占默认表名），**未改任何迁移文件名**；`check-flyway-ledger.py` ⇒ **`EXIT=0`**。
> ✅ **「多模块同库共存」已闭环**（真 PG 三进程并发实测）⇒ 详见 §3.5 表「共享 Flyway 账本」行 + [`L4-flyway.md`](docs/DRIFT/global/lanes/L4-flyway.md) §8.4。
> ⚠️ **残留风险**：新增模块若忘加 `table` ⇒ 撞上仍用默认表名的 article；**若忘加 `baseline-version: 0`** ⇒ 同样是「起得来、首次查库才炸」。
> ⚠️ **文档缺陷**：多处声称由 `FlywayLedgerIsolationTest` 负责，**该测试类全仓不存在** ⇒ 实际由 `check-flyway-concurrent.sh` + 各域 `*FlywaySharedSchemaGateTest` 承担。
> ⇒ `docs/Pitfalls/_cross/flyway-ledger-collision.md`

### 6.4 其他子模块

`service/message` 台账见 §0 域表（不重复）。
**Phase 5（全量 + 端到端）**：未启动；需真实 PG + Redis 集成测试补上（当前测试大量 mock 数据层）。

## 七、引用清单

### 7.1 真理之源（判定行为）

Go 真源仓库：见 §一 表首行（**已冻结**，不接受写入）。
RAG 不是从 Go「推测」行为，而是**逐字对照 Go 源码**（§3.1）。
> 📖 完整锚点表（路由 / 事务 / 行锁 / 身份 / JWT / 建表 + message 域 9 个 RPC）⇒ [`go-anchors.md`](docs/DRIFT/_cross/go-anchors.md) —— **参考资料，非政策**

### 7.2 本仓库：每份文档各管什么

> **读法**：想知道「某条结论的依据在哪」查本表；想知道「怎么自己跑一遍」看 `复现` 列。⚠️ 任何「已修 / 未修 / 已对齐」断言**以对应台账为准**，本表只负责指路。

**根目录**：见 §一 表首行

#### 文档档（`docs/`）

| 位置 | 作用 | 什么时候看它 |
|---|---|---|
| [`docs/DRIFT/`](docs/DRIFT/_INDEX.md) | **语义漂移台账**：「RAG 与 Go 不一致」逐条留档。`<域>/<组件>.md` + `global/` | 判定某模块是否已对齐 |
| [`docs/Pitfalls/`](docs/Pitfalls/_INDEX.md) | **踩坑档**：「为什么会踩」 | 写测试 / 「配了却没生效」时 |
| [`docs/Intentional_Deviations/`](docs/Intentional_Deviations/_INDEX.md) | **有意偏离档**：「明知与 Go 不同、**经签字批准**」。⚠️ **是决策不是缺陷：不重复质疑** | 判断某条差异「要不要修」时 |
| [`docs/FileTree/`](docs/FileTree/README.MD) · [`docs/HUMAN_ONLY/`](docs/HUMAN_ONLY/simple.md) · [`docs/MappingTable/`](docs/MappingTable/README.MD) · [`docs/Worktree.md`](docs/Worktree.md) | 理论文件树（15 份，⚠️ **迁移期脚手架**）· 阅读指南 · 名字映射 · **并发工作流**（§5.3） | 规划结构 / 开并发对齐前 |
| [`_cross/domain-common-lanes.md`](docs/DRIFT/_cross/domain-common-lanes.md) · [`_cross/cross-domain-coupling.md`](docs/DRIFT/_cross/cross-domain-coupling.md) | **`<域>-common` 对账总纲**（9 条机制面轴 A1~A9 · 写权边界 · 执行协议）· **跨域耦合矩阵与分波方案**（依赖 DAG · G1~G4 波次表 · 共享层写权规则） | 开工作树前 / 排波次前 |

- ⚠️ **`docs/` 下两个 `common` 不是同一个组件**：`FileTree/common.md` = Go `service/common`（跨服务共享）；
  `FileTree/hot/common.md` = Go `service/hot/common`（**hot 专属，仅 1 个 `errmsg.go`**）。

#### 工具档（`tools/`）—— 全部是「机器会看」的东西，用途见各脚本 docstring

| 工具 | 作用（细节见各脚本 docstring） | 复现 |
|---|---|---|
| `tools/semantic-probe/` | **双侧语义探针 ×4**（§3.3：解析 / trace / gRPC 超时 / **下游调用 deadline 传播**） | `bash tools/semantic-probe/run-parity.sh`（另 3 个变体见 README） |
| `tools/errmsg-probe/` | 错误码契约逐字节对拍 | `bash tools/errmsg-probe/run-parity.sh` |
| `tools/difftest/` | 双端运行时对拍（两套栈 + 请求矩阵） | `bash tools/difftest/run.sh` |
| `tools/difftest/check-ddl-static.py` | **DDL 列级对照**（类型/可空性/DEFAULT/索引/触发器）· **9 域** | `python3 … [--domain <域>] [--indexes]` |
| `tools/difftest/check-{proto-static.sh,plaintext400-headers.sh,jar-executable.sh}` | proto 契约 · **真 Tomcat** 400 头（MockMvc 测不到）· **产物可执行性**（漏配 `Main-Class` ⇒ 有的域 200、有的域 400） | `bash …` |
| `tools/difftest/check-flyway-ledger.py` | Flyway 账本隔离（按库+schema）⇒ `EXIT=0`；运行时同库共存 ⇒ `check-flyway-concurrent.sh`（§6.3 问题 4） | `python3 …` |
| `tools/difftest/check-grpc-runtime-classpath.py` | devh client 运行时 classpath + 服务发现地址门禁（baseline 机制） | `python3 …` |
| `tools/difftest/check-{grpc-interceptor-wiring,tomcat-limits,global-lane-ownership,wave-coupling,wave-lane-ownership}.py` | 五个装配/写权守门：rpc 拦截器接线 · api 容器输入上限 · `global` 历史 lane 写权 · **跨域耦合矩阵漂移** · **G1~G4 波次写权**（⇒ `EXIT=0`） | `python3 tools/difftest/check-*.py`|
| `tools/difftest/{check,mutate}-deadline-*.py` | **deadline 家族**：E48 harness 守门+变异 · E62 守门变异 · **E38② 漂移形态守门**（漂移被修好就红） | `python3 tools/difftest/check-deadline-*.py` |
| `tools/drift-move.py` | AGENTS.md ↔ docs/ 搬移（幂等）· 台账 `docs/DRIFT/_MANIFEST.json` | `python3 … verify` |
| `tools/check-ledger-{tables,selfcheck}.py` · `check-agents-budget.py` | 台账结构层 + 台账自检 + AGENTS.md 体量守门 | `python3 …` |

> ⚠️ `tools/difftest/exp-*.sh` 是**被证伪方案的实证记录**（脚本头部已写明），**不要当修复用**。

#### 任务档与模块内文档

- `RAG/.agents/`（任务指南，**不得削弱本文件的规则**）· `RAG/.agents/specs/<slug>.md`（每个 phase 一个 PR 的 spec）
- 模块 `HUMAN_ONLY.MD`（**4 份**，2026-10-22 glob 实测校正）：`service/users/{user/user,user/user-api,admin/admin-rpc,admin/admin-api}/`。
  ⚠️ 原清单另列的 `users-common` / `message-rpc` / `message-api` 三份**不存在**（详见 §2）。

### 7.3 已知的 Go 侧孤儿（勿照搬）

`service/user/rpc/internal/logic/` 是 7 个 `_test.go` 的孤儿目录，全仓无任何引用——搬迁时不要带过来。