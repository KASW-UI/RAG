# AGENTS.md: RAG 项目政策与当前重构状态

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **2026-10-18 重写**：原文件是从 vLLM 模板继承的、含大量与本项目无关的内容（GGUF、ModelRegistry、FusedChain 等）。本次重写只保留 RAG 实际需要的政策与本次重构的当前状态。
> **2026-10-20 第三次真理之源切换**：真源退回 **Go**；非 Spring Boot 的 Java 实现（`Sea-RideTheWind_java` 等）从策略中移除，不在 RAG 工作范围内；并新增 §3 语义对齐纪律。起因是 `service/users` Go ↔ RAG 对照发现 **11 处行为回归**，全部集中在「换成更地道的 Spring 写法」的位置。详见 §1.1。

## 〇、当前状态速查（2026-10-22）

> **这一节回答两个问题**：「`service/users` 现在还有漂移吗」与「下一个 agent 该从哪下手」。
> 详细留档在 §3.4 / §6.2.6，此处只给结论与边界。

**已关闭**（全部有运行证据，不是静态推断）：

| 项 | 结论 | 证据 |
|---|---|---|
| **E-3**（DTO 字段边界） | **R1~R5 五个根因全部已修** | 矩阵 273 条 `NEW_DRIFT 77 → 0`；MATCH 121 → 130 |
| **E-1**（响应未比对） | **10 步「只比状态码」已升级为按边界断言** | `steps=39 asserted=10 byte_equal=29 unexpected=0` |
| **DRIFT-024**（E-1 附带挖出） | **RAG 所有 text/plain 400 丢 `Content-Type`/`nosniff` 已修** | 真 Tomcat 守门脚本红绿双向实测（修复前 `EXIT=1` → 修复后 `EXIT=0`） |
| E-2 / E-4 / E-5 / E-6 / D-1 | 已关 | 见 §6.2.6 |
| **DRIFT-LIKE-001**（like-rpc 列表 DB 回源恒空） | **已签字：Java 现状即为「已修正的 Go bug」，两侧不一致属有意，不再对齐** | 真 PG + gorm v1.31.1 实测 `schema.LookUpField("created_at")` 返回 `nil`、插入真实行后 `Scan` 得 `CreateTime=0`（期望 `1792670400`） |

全仓 `mvn -o clean test`（**2026-10-22 19:02 实测**）：**619 tests, 0 failures, 0 errors, 0 skipped**（全模块 BUILD SUCCESS）。
> ⚠️ **该数字只对 message 域有效（2026-10-22 19:26 补注）**：此后本机有**另一个会话在并行跑 Maven**
> （实测 6 个 maven/java 进程），持续改写 `service/like/**` 并重建共享的 `target/` 与
> `~/.m2` 里的 SNAPSHOT jar。在这种状态下**全仓口径不可复现**：
> `like-rpc` 编译失败、`message-api` 的 `MessageApiMvcPipelineTest` 41 条报
> `ClassNotFoundException: com.rag.users.common.http.GoRequiredFieldException`
> —— 而该类在 `users-common/target/classes` 与已安装的 jar 里**都存在**。
> ⇒ **并发构建期间不要采信全仓数字**；单模块隔离跑才有效。
> 口径说明：由各模块 `target/surefire-reports` 汇总（`common 102 / message 132 / users 357 / like 18 / article 10`）。
> `message 132` = `message-rpc 32` + **`message-api 100`**（41 MVC 管线 + 18 纯单测 + **41 保护层**）。
> `message-rpc 32` = 原有 21 + **§6.7.7 新增 11**（真 PG 6 + trim 5）。
> ⚠️ 上一版口径（580 / message 121 / rpc 21）已过期，见下方 ⚠️ 关于「测试数不是质量指标」的告诫。
> 其中保护层 41 例含 **3 例真 Tomcat**（`RANDOM_PORT` + JDK HttpClient 读原始响应头）——
> 响应头这层 MockMvc 测不到（DRIFT-024 同款），必须真容器；
> 另含 **2 例 starter 接线断言**（`GoStyleGrpcClientBreakerWiringTest`）——
> 断言「拦截器进了 starter 的 registry」，不是「拦截器存在」（§3.3 同款陷阱）；
> 另含 **1 例真 in-process gRPC 端到端**（`GoStyleGrpcClientBreakerE2ETest`）——
> 断言响应码 **5001 → 1015**，是唯一能把「拦截器在链上 + 异常有人接 + 码映射对」三件事连起来证的用例。
> ⚠️ **测试数不是质量指标**：本次 4 条漂移里有 3 条都曾被绿测试锁死（见下方 message-api 段），
> 且 DRIFT-MSGAPI-004 的修复中先后出现过「8 条单测全绿但功能完全没生效」
> 与「判据单测全绿但 `bizCodeFromError` 根本不会被调用」两种假绿，
> 真正锚定真源的是 `tools/semantic-probe` 的 23 例双侧对拍，不是测试条数。

**⚠️ 但「无漂移」这句话有明确边界**，不得越读：

| 仍然存在的 | 数量 | 性质 |
|---|---|---|
| **已签字的有意偏离** | 6+ 条 | N-1 / N-2 / N-4 / N-5 + §3.4 的 path/method/表名。**是决策，不是缺陷**，但确实不一致 |
| **DDL 已知未处理** | 12 处列类型 | 10 处 `timestamptz→timestamp` + 2 处 `text→jsonb`；**索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED**。⚠️ 2026-10-22 18:35 已对其中 message 域的时间列**首次做出可观察影响评估**（DRIFT-MSG-V-007：JVM 非 UTC 时全部时间戳偏移一个时区差），其余 10 处模块的列类型差异**仍未评估过影响** |
| **从未验证过的子模块** | **9 个** | `article / comment / favorite / follow / hot / like / points / security / task / common` —— **从未做过 Go ↔ RAG 对照**，也连 `HUMAN_ONLY.MD` 都没补。⚠️ **`hot` 一项已收口 2/3**：① `hot-common`（Go `service/hot/common`，hot 域唯一的非 api/rpc 组件，1 个 `errmsg.go`）—— 11 个码值 + 11 条文案 + 未知码回退，双侧输出 **sha256 逐字节一致**，14 条契约测试，7 项变异全部变红；② `hot-rpc`（2026-10-22 晚，Go `rpc` + `heavykeeper`）—— 由 13 个 12 行空骨架**首次实现**，`HeavyKeeper` **双侧对拍逐字节一致**，**46 条契约测试**，**10 项变异全部变红** ⇒ **`hot-common` / `hot-rpc` 均不再属于「从未验证」**；⚠️ 但 `hot-api`（13 个文件仍全是 12 行空骨架）未动 ⇒ **hot 域整体仍未完成**，计数由 10 降为 **9**。⚠️ **`article` 一项已收口 1/3**：`article-common`（Go `service/article/common`，1 个 `errmsg.go`，15 个码）—— 双侧对拍 **sha256 逐字节一致**（49 行 dump）、**15 条契约测试**、**8 项变异双层全红**，但 `article-api`（29 个文件）+ `article-rpc`（41 个文件）**仍全是 4 行空骨架** ⇒ **article 域整体仍未完成**，计数不变 |
| **已验证但文档缺失** | 3 个 | `users-common` / `message-rpc` / `message-api` —— Go ↔ RAG 对照已做，但 `HUMAN_ONLY.MD` **实际不存在**（§0/§7.2 曾误列为已存在，2026-10-22 已按 glob 实测校正，详见 §2） |

**⇒ 准确表述是：「`service/users` 的 DTO 字段边界层已无漂移」，不是「整个 `service/users` 无漂移」。**

### 🔴 下一个 agent 请先读这条：`like-rpc` 的事实投递链路**跨三个仓库**（2026-10-22 新发现）

> **`Sea-BreakTheWaves` 是本项目的第三个仓库**：`/Users/pr/Documents/GitHub/Sea-BreakTheWaves`。
> 它是 like 领域事实的**下游消费者** —— 消费 `usermodel.like-facts.v1`，并通过
> `GET /internal/v1/community/facts` **回调 like 的权威端点**做逐字段核对才肯落库。
> ⚠️ **先前只搜了 `Sea-RideTheWind_golang` 与 `RAG` 就断言「DC 接收端不存在」是错的**
> （§3.3 纪律的又一次真实发作：否定式断言不可信，跨仓库尤其）。
> 完整拓扑、冻结的权威端点契约、6 个骨架文件清单、12 条漂移、8 条错误断言、
> 31 条测试的盲区分析 ⇒ 见 **`service/like/like-rpc` 小节**。
>
> **同时注意两个仍未解决的前置项**：
> ① `like-rpc` **从未做过一次 Spring 上下文启动测试**（与 §6.6.5 message-rpc 同型）；
> ② dispatch / authority 两个 Go 入口**只经 DB 耦合**，与 like-rpc 的 gRPC 面无任何调用关系
> ⇒ 它们**不必须**随 like-rpc 一起 Java 化，这是「要不要重实现」的前置判断点。

### 🟡 `service/message/message-rpc`：3 项 P0 **已修**，并**新挖出 3 项「模块根本起不来」级缺陷**（2026-10-22）

> 首轮审计见 **§6.6**。审计当天即补测试 + 修复，**3 项 P0 已全部关闭（真 PG +变异测试双重验证）**，
> 但补测试的过程**又挖出 3 项此前从未被发现的缺陷 —— message-rpc 这个模块此前根本启动不了**。

| # | 缺陷 | 后果 | 状态 |
|---|---|---|---|
| **DRIFT-MSG-001** | 6 个 `@Transactional` 因 **self-invocation** 静默失效 | 事务形同虚设，新建会话失败会**残留孤儿会话行** | ✅ 已修（抽 `MessageAtomicService` 独立 Bean） |
| **DRIFT-MSG-002** | `message_conversation_message.conversation_id` **恒为 0** | 消息全写 `conversation_id=0` ⇒ **查不出历史消息、unread_count 恒 0** | ✅ 已修（两条写路径都在事务内 `setConversationId`） |
| **DRIFT-MSG-003** | `ServerInterceptor` 未挂到 server（§6.5 的 H2 原样复现） | `observability.rpc-timeout: 2s` **从未生效** | ✅ 已修（`@Import` + `GrpcServerConfigurer`） |
| **DRIFT-MSG-004** 🆕 | `NacosRegistration` 裸 `@Component`，Bean 名与 `NacosServiceRegistryAutoConfiguration` **撞车** | **上下文直接起不来**（Bean 覆盖默认禁用） | ✅ 已修（显式名 `messageRpcNacosRegistration`） |
| **DRIFT-MSG-005** 🆕 | 缺 `@MapperScan`，3 个 Mapper **注册不上** | **上下文直接起不来** | ✅ 已修 |
| **DRIFT-MSG-006** 🆕 | 缺 `spring-boot-flyway`（Boot 4 拆模块后必需），迁移脚本**从不执行** | 启动无任何 Flyway 日志、**表永不创建**，第一次打 SQL 才炸 | ✅ 已修 |

**⇒ 教训比修复更重要**：`message-rpc` 有 **21 条测试且全绿**，却**一次都没真正启动过**。
9 条旧测试全是纯 Mockito 单测（不装配上下文），`tools/difftest` 也从未纳入它
⇒ 「上下文能不能起」「迁移跑没跑」这两件事**从未被执行过一次**。
**这与 §6.6.5 记录的方法论完全吻合：缺失的不是测试数量，而是「让代码真正跑一次」这个动作。**

新增 **11 条**测试（`MessageTransactionWiringTest` 8 条真 PG + `MessageGrpcInterceptorWiringTest` 3 条），
`message-rpc` 测试数 **9 → 21**。两个变异测试实测证明新测试不是假绿（详见 §6.6.5）。
message 域同时已纳入 `tools/difftest` 双端口对拍（§6.6.6）。

**下一步建议**：先清 DDL 剩余 12 处（`check-ddl-static.py` 已就位，改完可立即验证），
再按模块逐个进 phase 补那 10 个未验证子模块。
⚠️ 其中 `users_user.extra_info` 的 `text→jsonb` **必须单独取证** —— 它涉及 Go 侧 model
到底是 `string` 还是自定义类型，不能照「RAG 用了 jsonb 所以 Go 也该用」来猜。
⚠️ **每个模块接手的第一件事应该是「先让它真的启动一次」**（最小 `@SpringBootTest` + 真容器），
而不是先写业务单测 —— message-rpc 证明了「21 条全绿」与「一次没起过」可以并存。

### 🟡 `service/message/message-api`：已落地，验证查出的 **3 处请求解析层语义漂移已全部关闭**（2026-10-22）

> 本次工作是「Go `service/message/api` → Spring Boot `message-api`」的首次实现 → 独立子会话语义验证
> （结论 `SEMANTICALLY NOT EQUIVALENT`）→ **两侧探针实证** → 逐条修复。
> 13 个端点的主干维度（正常路径 / 鉴权 / envelope / 身份解析 / 错误码 / 响应序列化）**自始等价**，
> 失配**全部**集中在**请求解析的输入解释层**，现已逐条关闭并有回归守门。

**落地规模**：主源码 4089 行 / 43 文件 + 测试 1019 行 / 2 文件；创建前该模块是 38 个只有 `package` 声明的空壳。
**当前状态**：message-api **59 tests 全绿**（41 条真实 MVC 管线 + 18 条纯单测）；
`tools/semantic-probe/run-parity.sh` **23/23 两侧一致，`EXIT=0`**。

#### 三条漂移：前 2 条来自子会话验证，第 3 条是**修前 2 条的过程中新挖出来的**

| # | 输入 | Go 实测 | 修复前 RAG | 性质 | 状态 |
|---|---|---|---|---|---|
| **DRIFT-MSGAPI-001** | `{"conversation_id":123}`（JSON **数字**） | **接受 → 123** | **拒绝 → 5001** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修 |
| **DRIFT-MSGAPI-002** | `{"offset":"10"}`（**无** `,string`，JSON 字符串） | **拒绝 → 5001** | **接受 → 10** | SEMANTIC MISMATCH / HIGH-RISK | ✅ 已修 |
| **DRIFT-MSGAPI-003** 🆕 | `{"extra":null}`（字段 tag 是 **`optional`**） | **接受** | **拒绝 → 5001** | SEMANTIC MISMATCH | ✅ 已修 |

⚠️ 前两条是**方向相反**的 —— 一条该收不收、一条不该收却收，所以「粗看都能跑通」，
只有**逐用例两侧对拍**才暴露得出来。

##### DRIFT-001/002 根因：一个 Go 反射事实被写反了

**`json.Number` 是 `type Number string`，`reflect.Kind()` 返回 `string`，不是 `number`。**
⇒ Go 端 `processNamedFieldWithValueFromString` 的 `if valueKind != reflect.String` 对 JSON 数字**放行**，
再经 `fillPrimitive` 的 `case json.Number` 分支 `ParseInt` 成功。所以 Go 的 `,string`
是**「入参方向宽松（string 与 number 都收），出参方向才严格（输出成字符串）」**，
而 RAG 当成了「双向都只收字符串」。

Evidence：
- Go 侧 `core/mapping/unmarshaler.go:889-894`（Kind 判定）、`:1115-1125`（`case string` + `case json.Number` 双分支）、
  `core/jsonx/json.go:74-77`（`UseNumber`）、`core/mapping/utils.go:376-377`（`,string` → `FromString=true`）
- RAG 侧原 `ConversationMessagesReq.java:54-63` 等 6 个 DTO 的 `String` setter + `GoStyleCoercion.java:118-127`
  （`LogicalType.Textual` + `CoercionInputShape.Integer=Fail`）
- 修复：新增 `dto/GoStyleStringInt64.java` 复刻双向接受语义，**8 个 setter 从 `String` 入参改成 `Object` 入参**
  （只有「不声明目标类型、让 Jackson 原样递出」能同时表达「两种都收」）；
  新增 `config/MessageGoStyleCoercion.java` 叠加两条 message 域补强（`Integer/String=Fail`、`Boolean/String=Fail`）。

⚠️ **DRIFT-001 的影响面是 9 个请求字段**：`conversation_id` ×4 端点、`notification_id`、`recipient_id` ×2、`target_id`。
任何用 `JSON.stringify({conversation_id: 123})` 的客户端在 Go 上成功、在 RAG 上全量失败。

⚠️ **DRIFT-002 的补强刻意只落在 message-api**，**没有**改 `users-common` 的 `GoStyleCoercion`。
理由：那条「String→int 豁免」是 users 域**按 users 真源逐字段实测**得出的结论，对 message 域不成立；
共享规则一改就会把 users 那边已经验证对的行退回去。**补强要局部叠加，不要污染共享层。**

##### DRIFT-003 根因：把 `,omitempty` 当成了 `,optional`

我最初的判断来自 users 域的一条实测（`extra_info: null` 被拒），于是给 message 也加了 `explicitNull` 拦截。
但**两处 tag 根本不同**：

| 字段 | tag | Go 对 `null` 的判定 |
|---|---|---|
| message `AdminSendNotificationReq.Extra`（`types.go:18`） | `json:"extra,optional"` | **接受** |
| users `CreateUserReq.Extrainfo`（`user/api/.../types.go:10`） | `json:"extra_info,omitempty"` | **拒绝** |

根因在 go-zero 的选项解析：`core/mapping/utils.go:372` 的 `parseOption` switch **只认三种**
（`inherit` / `string` / 以 `optional` 开头的），**`omitempty` 不在其中** ⇒ 它不设置任何 unmarshal 选项
⇒ `Optional` 保持 `false` ⇒ `core/mapping/unmarshaler.go:856-862` 的 `if mapValue == nil` 落到
`return fmt.Errorf("field %q mustn't be nil", key)`。

**`,omitempty` 只控制出参是否省略，与「入参可否为 null」无关。** 把它当 optional 是一个很容易自然发生的误解。

Evidence：
- Go 侧 `service/message/api/internal/types/types.go:18` vs `service/user/user/api/internal/types/types.go:10`
- go-zero v1.10.2 `core/mapping/utils.go:372-385` + `core/mapping/unmarshaler.go:856-862`
- 探针同时覆盖了**两种 tag**（`required` 显式 null → 两侧都拒；`,optional` 显式 null → 两侧都收）
- ⚠️ **探针的比对面是 ACCEPTED/REJECTED 判定，不是错误文案**：`required` + 显式 null 时两侧都拒，
  但**原因不同**（Go `mustn't be nil` / RAG `is not set`）。两者都映射到同一 envelope 业务码 5001，
  在 API 边界**不可观测**，故按 §25 归为 `IMPLEMENTATION DIFFERENCE`。
  ⚠️ 但若日后要比**错误文案**（例如某端点把 msg 回传给客户端），探针必须先扩到比对 message 字段，
  否则会拿到一个「看起来全绿」的假结论。
- 修复：删掉 `GoStyleRequired.explicitNull(..)` 调用 + `extraPresent` 字段，改正 javadoc，
  并把锁死错误答案的 `extraPresenceSemanticsArePreserved` 换成对照用例

##### 探针已落盘进仓库（不再是 `/tmp` 一次性脚本）

`tools/semantic-probe/`：`go/main.go`(102) + `java/Probe.java`(106) + `run-parity.sh`(109) + `README.md`(136)。
Go 半边是**真源镜像**（struct 逐字段对齐 `types.go`）直接跑 go-zero v1.10.2 的 `core/mapping`；
Java 半边跑项目**真实 Jackson3 + 项目自己的 DTO**。复现：

```
bash tools/semantic-probe/run-parity.sh      # 23 例；一致 23 / 不一致 0 / EXIT=0
```

⚠️ 该探针的价值是**回归守门**，不是一次性取证（已做过变异验证：把 setter 改回 `String` → 2 行变红 `EXIT=1`；
Go 侧删一个用例 → 不对称分支触发 `EXIT=1`）。它与 §3.3 那条「绿测试守卫错误答案」互为镜像：
**探针守「不许回退」，测试守「不许再错」。**

#### 元教训：**绿测试可以是「错误答案的守卫」**（本条比三个缺陷本身更重要）

**3 条漂移里有 3 条都被一条绿测试锁死过**，且都不是笔误、都有完整推导：

1. **DRIFT-001** 固化在两处：
   - `ConversationMessagesReq.java` 的 javadoc 写着「`json.Number` 的 Kind 是 number」—— **事实错误**；
   - `MessageApiMvcPipelineTest#int64AsJsonNumberIsRejected` 把 **RAG 的行为**断言成了「与 Go 一致」，
     **这条绿测试会主动阻止正确修复**。
2. **DRIFT-002** 同款：`GoStyleCoercion` 里那条 String→int 豁免被我当成通用规则写死，
   连带测试断言了 `{"offset":"10"}` 被接受。
3. **DRIFT-003**：`MessageApiMvcPipelineTest#extraPresenceSemanticsArePreserved`
   断言 `extra: null` → 5001。测试名里带「PresenceSemanticsArePreserved」，
   **读起来就是在宣称对齐了真源，实际断言的是我的错误类比**。

⇒ 沿用 §3.3 那条「把没看过的东西变成看过了」的元教训，补一条更硬的：
> **测试断言的是「RAG 现在的行为」，不是「Go 的行为」。**
> 当测试名/DisplayName 里写着「对齐 Go」「已保留语义」而断言的是 RAG 的实际输出时，
> 它要么是**巧合**、要么是**在给未验证的假设发通行证**。后一种比没有测试更危险 ——
> 它会让 CI 绿灯为一个反向漂移背书，且修复时会先撞上「测试红了」的心理阻力。

**⚠️ 附带一条判据（本项目专有）**：一个**跨域照搬来的实测结论**在动手前必须先核对 **tag 是否逐字相同**。
DRIFT-003 的根因不是「实测错了」，而是**实测对象（users `extra_info,omitempty`）与本次对象（message `extra,optional`）
根本不是同一个 tag**。⇒ §3.1 的「不要拿现有 RAG 代码当对照基准」要再加一条：
**也不要拿另一个域的实测结论当对照基准**，除非已确认其前提条件逐字成立。

**⚠️ 再附带一条（本轮复核 DRIFT-MSGAPI-004 时补上）**：**「Go 侧没有 X」这类否定式断言，
默认不可信，必须去被否定的那一侧找正面证据。** 本项目的反例有两条，且形态相同：
1. 「Go yaml 没写 MaxBytes/MaxConns/Breaker」——其实由 `rest/config.go` 的 `default` tag 开启；
2. 「Go 侧没有 client 熔断」——其实由 `zrpc/internal/config.go:14` 的 `default=true` 开启。
⇒ 两处都是**「默认值藏在框架 tag 里，而对照时只看了数据文件」**。
**否定式断言无法用「没找到」来证实**，只能靠找到那个 tag。

**必须做的三件事**：① 论证里每个 Go 事实都带 `文件:行号`；② 凡写「与 Go 一致」，
必须有**同时跑两侧**的探针；③ 修复漂移时**先改/删错误断言，再改实现**，否则会被测试挡住。

#### ✅ 请求保护层：四层全部补齐（2026-10-22）

此前记为「RAG 三个全无、需决策」。现已实现完毕，新增包
`service/message/message-api/src/main/java/.../protection/`（7 个类）+ 4 个测试类（**30 例**）。

**先说清一件事：这四层在 Go 的 yaml 里一个字都没写，但它们是开启的。**
依据是 go-zero v1.10.2 的两处 tag：`rest/config.go:52-56`（`MaxConns=10000` /
`MaxBytes=1048576` / `CpuThreshold=900`）与 `rest/config.go:12-22` `MiddlewaresConf`
九个开关**全部** `json:",default=true"`。⇒ **照 yaml 逐字段对，永远看不见这四层。**

| 层 | Go 出处 | 拒绝响应 | RAG 实现 | 测试 |
|---|---|---|---|---|
| 请求体上限 | `rest/handler/maxbyteshandler.go:10-25` | **413 裸响应** | `GoStyleMaxBytesFilter` | MVC 3 例 + **真 Tomcat 3 例** |
| 并发上限 | `rest/handler/maxconnshandler.go:12-36` | **503 裸响应** | `GoStyleMaxConnsFilter` | 6 例（含并发/异常归还） |
| CPU 降载 | `rest/handler/sheddinghandler.go:20-52` + `core/load/adaptiveshedder.go` | **503 裸响应** | `GoStyleCpuSheddingFilter` + `GoStyleAdaptiveShedder` | 10 例 |
| 熔断 | `rest/handler/breakerhandler.go:19-44` + `core/breaker/googlebreaker.go:52-79` | **503 裸响应** | `GoStyleBreakerFilter` + `GoStyleBreaker` | 8 例 |

##### 三条必须记住的可观察契约（都偏离直觉）

1. **裸状态码 = 无 body、无 Content-Type、不走 envelope。**
   Go 侧四处都是裸 `w.WriteHeader(code)`，不调 `httpx.Error`。
   RAG 因此**刻意不用 `sendError`**（那会让容器补 `text/html`），改用 `setStatus` + 不写 body。
   ⇒ 这与本模块「所有业务错误恒 200 + envelope」的约定**刻意不同**：保护层在到达业务逻辑前就拒了。

2. **413 压过 401。** Go `rest/engine.go:105-112` 先装 `buildChainWithNativeMiddlewares`
   （内含 MaxBytes，`:164`）**再** `appendAuthHandler`（`:109`）；而 `chain.Append` 是
   「加在请求流的最后」（`rest/chain/chain.go:38-43`）⇒ **MaxBytes 在鉴权之外**。
   可观察后果：**往受保护端点发 2 MiB 无 token 请求，Go 返回 413 而不是 401。**
   RAG 侧 MaxBytes 是一个 Servlet `Filter`，而**所有 Filter 都包在 `DispatcherServlet` 外、
   `HandlerInterceptor`（JWT 鉴权）跑在其内** ⇒ 「413 早于 401」来自**容器结构**，
   **不是**来自 `@Order` 数值。（⚠️ 2026-10-22 实测更正：把 `@Order` 改成 `LOWEST_PRECEDENCE`
   后断言<b>依然绿</b>；能真正让断言变红的是「让该层失效」——摘掉 `@Component` ⇒ 413 变 401。）
   已有两条测试锁死（MockMvc 装配 + 真 Tomcat），且做过**变异验证**：关掉 MaxBytes → 413 变 401 → 红。

3. **⚠️ 熔断<b>不会</b>被下游 RPC 失败触发。** breaker 的判据是
   `cw.Code < 500 ⇒ Accept()`（`breakerhandler.go:39`），而本模块所有业务错误
   （含 gRPC 失败）恒为 **HTTP 200 + envelope** ⇒ 在 Go 侧被记为**成功**。
   能触发熔断的只有两条路径：**超时（`timeouthandler.go:115` 写 503）**与
   **未捕获 panic（`recoverhandler.go:17` 写 500）**。
   ⇒ **它的真实职责是「别让正在超时/崩溃的端点被继续打」，不是「保护下游」。**
   ⚠️ 若按 envelope 里的业务码去熔断，就是**新增行为**，不是对齐。

##### 一处刻意的表达层差异（已在 javadoc 留痕，非隐藏缺口）

Go 的 MaxConns 闸门是**每路由一个**（`engine.go:146` 对每条路由各建一次 latch），
RAG 取**全局一个**。区别只在「单端点打满 10000 且同时另一端点也接近 10000」时显现，
而那时 Tomcat 的 `max-threads=200` 早已先成为瓶颈。

##### 为什么 Tomcat 自带的配额**不能**替代 MaxConns

`max-threads` 超限是**排队**、`max-connections` 超限是**停止 accept**，
两者都不是 Go 的**立刻 503**。「变慢」与「被拒」在过载时是决定性的客户端行为差异，
调 Tomcat 参数只能近似、不能替代。

##### 两条降载相关的诚实边界

- **CPU 读不到时 fail-open**：`getSystemCpuLoad()` 在部分容器恒返回 `-1`。
  本实现把 `-1` 视作**未过载**（放行），与 Go `systemOverloaded()` 首个分支同向。
  理由：当成过载会把**全部流量杀光**；降级为不降载则与 Go 同向。
- **⚠️ 本环境无法启动 Go 真源做活体双侧对拍**：其 yaml 指向 `etcd:2379`，
  而 `zrpc.MustNewClient` 在 `internal/svc/servicecontext.go:21` 无 etcd 时**直接 panic**。
  ⇒ 本次 413 的期望值取自 Go 源码而非活体 curl，该证据边界已在测试 javadoc 中标注。

#### 🟡 其余已留档、待决策的差异（不再单开编号）

| 项 | Go | RAG | 建议 |
|---|---|---|---|
| ~~请求保护层~~ | `MaxBytes` 1MB→413、`CpuThreshold` 900→503、`MaxConns` 10000、**`Breaker` 熔断** | ✅ **已全部补齐**（2026-10-22）⇒ 见下方「请求保护层」小节 | — |
| 404 body | `"404 page not found\n"` + text/plain + nosniff | 空 body | 建议补 |
| 405 头 | 带 `Allow` | 无 `Allow` | 建议补 |
| 成功响应 Content-Type | `application/json; charset=utf-8` | 疑似无 charset | **未验证**，需真容器 curl |
| `extra` map 键序 | `encoding/json` 排序（确定性） | `LinkedHashMap` 保序 | 字节级差异，JSON 语义等价 |
| 观测端点 | `:16066/metrics`+`/healthz`+pprof | `:8892/actuator/*` | 运维配置要改 |
| ~~Content-Type 非 JSON~~ | ~~继续处理出 envelope~~ | ~~415 空 body~~ | ✅ **2026-10-22 已关闭**，与 like 域合并为一份共享实现。⚠️ 原签字理由「两侧都是拒绝」**已被推翻**：Go 的 `withJsonBody` 不满足时是**丢弃 body 后按空对象继续**，对零必填 DTO（`PageReq`/`EmptyReq`）Go 其实**成功**。详见「请求体 Content-Type 判据」章节 |
| admin HMAC 密钥 | 15 字节原样 | zero-pad 到 32B | **N-5 已签字**（跨部署 token 不互认） |

#### ✅ DRIFT-MSGAPI-004（2026-10-22 复核新发现，**已修**）：RAG 的 gRPC **client 熔断缺失**

起因是复核 `MessageRpcClient` 里那句「⚠️ 禁止在这里加 retry / circuit breaker：Go 侧没有这层」——
**该注释的 retry 那半对、breaker 那半是事实错误**。

| 项 | Go | RAG（修前） | 状态 |
|---|---|---|---|
| client 熔断 | **有，默认开** | **无任何 client 拦截器** | ✅ 已修 |
| client retry | 无（`RpcClientConf` 没有 `Retries` 字段） | 无 | EQUIVALENT（**保持不加**） |

Evidence：
- `zrpc/internal/config.go:14` — `ClientMiddlewaresConf.Breaker bool json:",default=true"`
- `zrpc/internal/client.go:112-113` — `if c.middlewares.Breaker { append(..., clientinterceptors.BreakerInterceptor) }`
- `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` —
  `breaker.DoWithAcceptableCtx(ctx, path.Join(cc.Target(), method), ...)` ⇒ **每个 RPC 方法一个 breaker**
- Go `etc/messagecenter.yaml` 未配 `Middlewares` ⇒ 走 `default=true`
- 实现：`protection/GoStyleGrpcClientBreakerInterceptor`（**复用 `GoStyleBreaker`**，因 go-zero 的
  client breaker 与 HTTP 层 breaker 是同一个 googleBreaker）+ `GoStyleCircuitOpenException`

##### 为什么它当初没被发现（比缺陷本身更值得记）

`zrpc/internal/codes/accept.go:9-17` 的 `codes.Acceptable` 只把
`DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted`
判为失败；而 message-rpc 的业务错误（逐个 `errWithBizCode` 调用点核对）
**只用** `InvalidArgument / NotFound / PermissionDenied`，**全都不在失败名单里**
⇒ **业务错误在 Go 侧被记为 Accept，不参与熔断**。
⇒ 正常流量与业务错误路径下两侧**行为完全一致**，只有「下游返回 5xx 类 gRPC code 或超时」才分岔。
**任何只测业务路径的验证（含本项目现有的 41 条 MVC 管线用例）都测不出这条。**

##### ⚠️ 判据必须逐条对齐：一个 code 都不能多

**acceptable 判据照抄 Go 的那 6 个失败 code。若图省事改成「所有非 OK 都算失败」，
会把 `NOT_FOUND`（会话不存在 5202）、`PERMISSION_DENIED`（无权限 5205）这类
正常运营中天天发生的业务错误大量计成失败 ⇒ 熔断误开 ⇒ 打掉正常流量。
那比「该熔断时没熔断」的现状**更糟**。**已由
`GoStyleGrpcClientBreakerInterceptorTest#theSixFailureCodesAreNotAcceptable` /
`#businessErrorsAreAcceptable` 双向锁死。**

##### 熔断拒绝时必须抛**普通异常**，不是 `StatusRuntimeException`

Go 返回 `breaker.ErrServiceUnavailable`（普通 `error`，非 gRPC status）
⇒ `BizCodeFromError` 走 `status.FromError(err)` 的 `!ok` 分支 ⇒ **1015 + msg "service busy"（查表）**。
RAG 若改成抛 `StatusRuntimeException`，code 碰巧仍是 1015，但 description 会变成
`"circuit breaker is open"` ⇒ **msg 与 Go 不同**。已由
`#circuitOpenExceptionIsNotAGrpcStatus` 锁死。

##### 🔴 这条修复暴露了 §3.3 的又一次真实发作（比缺陷本身更值得记）

拦截器的**8 条单元测试全程绿灯**，但它**在真实 Spring 装配下完全没生效**，连续被抓到两个装配缺陷：

1. **两个构造器都没标 `@Autowired`** ⇒ Spring 退回找无参构造器 ⇒ 找不到 ⇒
   **`ApplicationContext` 直接起不来**（`No default constructor found`）。
2. **只加 `@Component` 不会被 starter 收集** ⇒
   `GlobalClientInterceptorRegistry.getClientInterceptors()` 返回 **`[]`**。
   根因：devh 的 `AnnotationGlobalClientInterceptorConfigurer` 用
   `applicationContext.getBeansWithAnnotation(...)` 收集，**它只认
   `@GrpcGlobalClientInterceptor`，不认「是不是 ClientInterceptor」**；
   而该注解本身又**不是** Spring stereotype ⇒ **两个注解缺一不可**。

⇒ 这两条**都是纯单元测试看不见的**（它们直接 `new` 拦截器，完全不经过 Spring）。
新增 `GoStyleGrpcClientBreakerWiringTest`（2 例）专治这一层：它断言的是
「拦截器出现在 starter 自己的 registry 里」，而不是「拦截器存在」。
**若没有它，这次修的是一个从不执行的类。**

##### 🔴 补端到端时又挖出第 3 个缺陷：**异常类型选对了，但没人接它**

前两层都绿之后，补 `GoStyleGrpcClientBreakerE2ETest`（真 in-process gRPC server +
真 channel + 真 MockMvc）断言「响应码从 **5001 变成 1015**」——
**第一次跑就红了，恒为 5001**。

诊断过程（`trackedMethods=1` 证明拦截器在链上；`closeCount=41` 而调用了 161 次，
证明熔断**确实打开过**、只是异常被吃掉了）：
**13 个 service 全部写成 `catch (StatusRuntimeException e)`**，而 Go 是
{@code if err != nil}（**捕获一切**）。我刻意抛的普通异常
{@code GoStyleCircuitOpenException} 绕过了这个 catch，
最后落到 `GlobalExceptionHandler#handleAny(Exception)` 的**兜底分支**，
被统一映射成 **5001** ⇒ 客户端看到的业务码与 Go 不同。

⚠️ **这正是我自己写在 {@code GoStyleCircuitOpenException} javadoc 里的那条推理失效了**：
我论证了「普通异常 ⇒ {@code bizCodeFromError} 会返回 1015」，
单测也断言了这一点并通过 —— **但 {@code bizCodeFromError} 根本不会被调用**。
判据本身没错，错在**没验证调用链上有没有人真的走那条判据**。

**修法**：在 `GlobalExceptionHandler` 单列 `@ExceptionHandler(GoStyleCircuitOpenException.class)`
→ 1015 + msg 查表（`errmsg.go:55` 的 "service busy"）。

⚠️ **为什么没把 13 个 catch 改成 `Exception`**（更「忠于 Go `err != nil`」的做法）：
那个 try 里除了 stub 调用，还包着 `support.toXxxResp(...)` 的响应转换；
一旦它抛 `BusinessException`，宽 catch 会把它**重新包成 1015** ——
那是**新引入的错**。⇒ 按异常类型精确处理是三项里副作用最小且可证明的。

#### 已关闭的未决项

- **Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES` 默认值 = `false`**（实测）⇒ 与 Go「忽略未知字段」**等价**。
  这条与 Jackson 2 的默认值**相反**，属 Jackson 3 的破坏性变更，别按 Jackson 2 的直觉写断言。
- **请求保护层四层**（MaxBytes / MaxConns / CPU 降载 / 熔断）：已补齐，见上方小节。
- **DRIFT-MSGAPI-001 / -002 / -003**：全部已修（见上）。防回退由**两套互补的网**守住：
  `MessageApiMvcPipelineTest` 的 **41 条 MVC 管线用例**（单进程、快、能进 CI）+ 
  `tools/semantic-probe` 的 **23 例双侧对拍**（慢、但锚定的是真源而非 RAG 现状）。
  ⚠️ **只有探针能发现「两侧一起错」**，所以这两套网不能互相替代。

#### 下一步建议（message 域）

1. ~~先修 DRIFT-MSGAPI-001，再修 -002~~ ✅ 已完成（-003 为附带挖出）；
2. ~~保护层决策（补齐 vs 放弃）~~ ✅ 已补齐四层（见上方「请求保护层」小节）；
   「Go yaml 未配置 ≠ Go 未开启」也已写进 `application.yml` 的 `rag.protection` 段头部注释
   （⚠️ 前缀 2026-10-22 由 `message-api.protection` 改来，保护层已下沉到 `service/common`，见下方新章节）。
   ⚠️ 复核 `MessageRpcClient` 时顺带发现它那条「禁止加 retry/熔断」的注释**breaker 那半是错的**，
   并由此挖出 DRIFT-MSGAPI-004（client 熔断缺失）——**已修**。retry 那半仍然成立，**继续保持不加**。
3. **补响应头对拍**（真容器 curl 两端 dump 全部头 + body），一次关掉 404/405/Content-Type 三条；
   ⚠️ 注意保护层的 413/503 已在 `MessageApiProtectionRealTomcatTest` 用真 Tomcat 验过裸响应，
   但那是 **RAG 单侧**（本环境起不了 Go 真源），Go 侧期望值仍取自源码；
4. **给 3s 超时补一条真触发测试**（stub `sleep(5000)` → 断言 503 + text/plain + body）——
   Bean 存在已被证明，但 503 分支**至今没有任何运行证据**（§3.3 同款形态）；
5. ~~给 gRPC client 熔断补一条端到端触发测试~~ ✅ 已完成
   （`GoStyleGrpcClientBreakerE2ETest`：真 in-process gRPC server 恒返 INTERNAL，
   断言响应码 **5001 → 1015**）。补它的过程又挖出兜底异常处理器吃码的缺陷，已修。
6. **补 `message-api/HUMAN_ONLY.MD`**（§2 已把该模块登记为「已实现 + 已对照，但文档缺失」；§7.2 的旧清单曾把它误列为已存在，已校正）。

### 🔴 `service/message` 全域第二轮语义验证：**10 项漂移 + 3 条新挖出的 P0**（2026-10-22）

> **详细留档见 §6.7**（逐条取证、UNRESOLVED、修复结果、变异验证表、两条通用纪律）。
> 本小节只回答「message 域现在还有没有漂移」。

**验证方式与前两轮不同**：不是「找当前实现的缺陷」，而是按 **Go → Spring 行为等价模型逐维度对照**
（契约 / 输入 / 分支 / 外部交互 / 状态 / 错误身份 / 超时 / 重试 / 并发 / 生命周期）。
覆盖 4 个组件：`message-rpc` + `message-api` + `message-common` + `message-proto`，
Go 侧通读 36 个文件、RAG 侧通读 71 个文件。

**结论：`SEMANTICALLY NOT EQUIVALENT`**
> ⚠️ **2026-10-22 19:10 纠正**：本节初版称「与 §6.6 均无重叠」——**错的**。
> §6.6 另有一张 DRIFT-MSG-004~012 的漂移表，其中 **8 行就是下表的 V-001~V-008**。
> 本轮真正新发现的只有 **V-009 / V-010** 与补测试挖出的 3 条 P0；
> 其余 8 项的价值在于**定级 + 修复 + 防回退**（§6.6 原标 `UNKNOWN`）。
> 完整对照见 §6.7.0。

#### 第一批（10 项静态对照）：**6 项已修 + 2 项签字留档 + 2 项待外部条件**

| 编号 | 一句话 | 状态 |
|---|---|---|
| **DRIFT-MSG-V-001** | `listNotifications(unread_only=true)` 的 **`total` 丢了过滤** | ✅ 已修 |
| **DRIFT-MSG-V-002** | 广播通知单条 foreach INSERT，**收件人 ≥5462 整批失败** | ✅ 已修 |
| **DRIFT-MSG-V-003** | DB/内部故障业务码 **Go `1015` → RAG `5001`/`5008`** | ⏸ **签字保留 RAG 侧**，见 §6.7.8 |
| **DRIFT-MSG-V-004** | 会话消息排序键 **`created_at asc` → `id asc`** | ✅ 已修 |
| **DRIFT-MSG-V-005** | 会话列表**缺次级排序键 `updated_at DESC`** | ✅ 已修 |
| **DRIFT-MSG-V-006** | `markConversationRead` **多写一列** `conversation.updated_at` | ✅ 已修 |
| **DRIFT-MSG-V-007** | 时间列 `timestamptz`→`timestamp` + 读侧按 UTC 解释墙钟 ⇒ **部署时区非 UTC 时全部时间戳偏一个时区差**（上海 = 偏晚 8h） | ✅ 已修（§6.7.10，部署时区已确认为 Asia/Shanghai） |
| **DRIFT-MSG-V-008** | `String.trim()` vs `strings.TrimSpace` **空白集不同** | ✅ 已修 |
| **DRIFT-MSG-V-009** | 直方图 bucket 布局不同 | ⏸ 留档（Prometheus 规则适配） |
| **DRIFT-MSG-V-010** | 错误解包深度不同（Go 穿透 / Java 不穿透） | ⏸ 留档（当前无发作路径） |

#### 🔴 第二批：**补测试时当场挖出 3 条 P0，静态审计两轮都没抓到**（§6.7.7）

| 编号 | 一句话 | 后果 |
|---|---|---|
| **DRIFT-MSG-V-011** | `#{item.isRead}` 取不到属性（实体字段叫 `read`） | **`SendNotification` 100% 失败** ⇒ admin 定向通知与广播**从未成功过一次** |
| **DRIFT-MSG-V-012** | `listByParticipant` 的 `limit` / `offset` **实参传反** | SQL 变成 `LIMIT 0` ⇒ **`ListConversations` 永远返回空列表**，且无任何错误日志 |
| **DRIFT-MSG-V-013** | MP 的 `JacksonTypeHandler` 用 `setString` 绑定，**jsonb 列拒收**；且缺 `autoResultMap` | 通知写入再次失败；**读**方向同样失效 |

> **这三条 P0 的共同形态**：都是「一条从未被任何测试执行过的写/查路径」。
> 与 §6.6.4 的 DRIFT-MSG-004/005/006（模块起不来）同源 ——
> **缺失的不是测试数量，是「让代码真正跑一次」这个动作。**

> ⚠️ **编号纪律**：本轮用 `DRIFT-MSG-V-*`，**不复用** `DRIFT-MSG-001..006`（§6.6）与
> `DRIFT-MSGAPI-001..004`（本节上方）。
> **代码注释里的漂移编号不是权威台账** —— `MessageAtomicService` javadoc 已发生编号撞车
> （把「多写 updated_at」标成 DRIFT-MSG-006，而 §6.6 的 -006 是「缺 flyway」），
> 并引用了**本文件里不存在的** `DRIFT-MSG-008`。

> ⚠️ **测试覆盖率必须重新表述**：修复前，`message-rpc` 的 21 条 + `message-api` 的 59 条 +
> `semantic-probe` 的 23 例双侧对拍，**没有一条能抓到上表任何一项**。
> 尤其 `-001`（无任何用例断言分页接口的 `total`）与 `-002`（无任何用例造过 >5461 收件人）。
> ⇒ **「有测试」与「测到了漂移面」是两件事**，见 §6.7.5 纪律 A。

#### 本轮新确立的三条通用纪律（已写进 §6.7，对全仓适用）

- **A**：分页接口的 **`total` 与 `items` 必须成对断言**；过滤条件要逐条覆盖到**计数**而不只是列表。
- **B**：`String.trim()` / `strip()` **都不是** `strings.TrimSpace` 的等价物（连 NBSP 都不认），
  跨语言迁移必须显式写空白集合。
- **C**：**做变异测试时每次变异与还原都必须 `mvn clean`** ——
  增量编译会把变异 class 留在 `target/classes`，导致「还原后复跑」测的不是当前源码（§6.7.7 实测踩中）。

### 🟡 `service/like/like-api`：已落地，独立子会话验证判定 **NOT EQUIVALENT**，2 条 HIGH 漂移（2026-10-22）

> 本节是**独立 verifier 子会话**（Go → Spring Boot 语义一致性验证，方向固定为 Go 重建基准）对
> 刚建成的 `like-api` 的裁定结果。**该模块的 44 条绿测试不能推翻此判定** —— 原因见末尾元教训。

**模块现状**：Go `service/like/api`（6 个 POST `/like/v1/*` 的 HTTP→gRPC 网关，零业务逻辑）
→ Java `service/like/like-api` 落地，主源 2566 行 / 测试 866 行，**44 tests 全绿**
（29 条真实 MVC 管线 + 15 条 DTO 校验）。`like-common`（LikeErrorCode/Messages/GrpcError）
由用户先行完成，不在本次范围内。

#### 🔴 两条 HIGH 漂移：**同一个错误动作，做了两次，方向相反**

| ID | 项 | Go | Spring Boot | 分类 |
|---|---|---|---|---|
| **DRIFT-LIKEAPI-001** | HTTP 请求超时 | **3s → 503 + `"Request Timeout"`** | **无超时** | SEMANTIC MISMATCH |
| **DRIFT-LIKEAPI-002** | `target_ids` 必填性 | **必填**（缺失/显式 null 均解析失败） | **非必填**（放行并真的发出 RPC） | SEMANTIC MISMATCH |

**根因是同一个动作：只读 yaml / struct tag，不读消费它们的那段控制流。**

**① `rest/config.go:55` 是 `Timeout int64 json:",default=3000"`** ——
`likecenter.yaml` 从头到尾**没写** `Timeout`，但 `conf.MustLoad` 会套用 struct tag 的默认值，
所以 `Timeout == 3000`，**不是 0**。于是 `DisableNativeRestTimeout` 返回 3s，
`observability.NewHTTPMiddleware` 真实生效 → 超时 `http.Error(w,"Request Timeout",503)`。
⇒ 原 `application.yml` 写的「yaml 没写 ⇒ `TimeoutFromMillis(0)==0` ⇒ 无超时」**字面自洽但完全错误**。
⚠️ 三条本可拦住它的旁证当时全部存在且都没被用上：
`com.rag.common.spring.ObservabilityProperties.java:45` 已写明「即 Go 实际跑 HTTP 3s；
**各模块 yaml 须显式写出同值，不得留空**」；`message-api:173` / `user-api:143` / `admin-api:132`
三个兄弟模块**都配了** `http-timeout: 3s`；`like-api` 是**全仓唯一不配的那个**。

**② `core/mapping/unmarshaler.go:944-950` 的 Slice 分支并非「填空值放行」** ——
`processNamedFieldWithoutValue` 对 `Array/Map/Slice` 且非 optional 时，
调的是 `processFieldNotFromString(..., value: emptyMap, ...)`，它落到 `default:` →
`processFieldPrimitive`，此时 `typeKind(Slice) != valueKind(Map)` → **`newTypeMismatchError`**。
⇒ 探针实测：缺 `target_ids` → `type mismatch for field "target_ids"`；`target_ids: null` →
`field "target_ids" mustn't be nil`。**两者都解析失败。**
连带后果：Go like-rpc 的 2001「输入需要查询的ID为空」（`getlikecountlogic.go:44`）
在 Go 上**永远不可达**（解析期已拦），而 Java 侧可达 ⇒ **两端 rpc 看到的入参集合不同**。

> ⚠️ **这两条的共同形状与 DRIFT-MSGAPI-001/002 完全同款**：
> 「不配置就是没有」是错的 —— 实际是「不配置就用代码里写死的那条」。
> 只不过这次一条**该有却没有**（超时），一条**该拒却放行**（必填），
> 粗看两边都能跑通，且**方向相反**，所以比单方向漂移更难发现。

#### 🟡 其余已登记项

| ID | 项 | 分类 | 状态 |
|---|---|---|---|
| ~~DRIFT-LIKEAPI-003~~ | ~~请求保护层四层全缺~~ | — | ✅ **2026-10-22 已关**：保护层下沉到 `com.rag.common.protection`，本模块 `scanBasePackages` 本就含 `com.rag.common` ⇒ **自动生效、零装配代码**。见下方「请求保护层下沉」章节 + `LikeApiProtectionMvcTest` |
| ~~DRIFT-LIKEAPI-004~~ | ~~超限请求的优先级（413 而非 401）~~ | — | ✅ **2026-10-22 已关**（同上）。⚠️ 但归因被变异测试**推翻过一轮**，见下方更正 |
| ~~DRIFT-LIKEAPI-005~~ | ~~gRPC client 熔断缺失~~ | — | ✅ **2026-10-22 已关**：共享拦截器自动接入；且 like 侧**无需任何 Java 改动**（`LikeApiSupport#rpcError` 的 `ok` 二分已把非 status 错误落到 5001，与 Go `ok=false` 分支一致） |
| **DRIFT-LIKEAPI-010** | `LikeApiSupport#userId` 的 javadoc **说反了** | 文档致高危 | **代码是对的，注释是错的** |
| ~~DRIFT-LIKEAPI-006~~ | ~~非 JSON Content-Type：Java 415 vs Go 丢弃 body 后按空对象继续~~ | — | ✅ **2026-10-22 已关**（含 message 域同型）。⚠️ 原签字理由「两侧都是拒绝」**已被推翻**（零必填 DTO 在 Go 侧是成功的） |
| ~~DRIFT-LIKEAPI-007 / -008~~ | ~~404 空 body；405 缺 `Allow` 头~~ | — | ✅ **2026-10-22 已关**。⚠️ 修 404 时**挖出比它更大的一条**：`/like/v1/**` 下的未知路径**根本没有 404**，被鉴权遮蔽成 **401** ⇒ 见 DRIFT-LIKEAPI-011 |
| **DRIFT-LIKEAPI-011** 🆕 | **未知路径被鉴权遮蔽**：`/like/v1/nope` 无 token → RAG **401**，Go **404** | SEMANTIC | ✅ **2026-10-22 已修**（`preHandle` 加 `handler instanceof HandlerMethod` 守卫）。message-api 同型一并修 |
| ~~DRIFT-LIKEAPI-009~~ | ~~`author_id = -2^63` 被误拒~~ | — | ❌ **复核为误报，已撤销** |

> ⚠️ **DRIFT-LIKEAPI-009 是误报，撤销记录（2026-10-22，修复过程中实测发现）**
>
> 该条声称 `BigInteger.bitLength()` 不含符号位 ⇒ `Long.MIN_VALUE` 的 bitLength 是 64 ⇒
> 被 `>= 64` 的越界判据误拒。**前提是错的**：
> <pre>
 *   实测（java.math.BigInteger）：
 *     Long.MIN_VALUE (-2^63)        bitLength = 63   ← 不是 64
 *     Long.MAX_VALUE ( 2^63-1)     bitLength = 63
 *     2^63                          bitLength = 64
 *     -2^63-1                       bitLength = 64
 * </pre>
> ⇒ 「{@code bitLength() >= 64} ⟺ 越出 int64 值域」是**成立**的，
> 原实现 `GoStyleStringInt64` 与 `LikeApiSupport#userId` 的判据本就正确，**不存在漂移**。
> 曾按该误报改过一次代码（换成 `compareTo` 值域比较），因行为完全等价而**已回滚**，
> 只把上面这条不变量写进注释，并用 `LikeReqGoStyleValidationTest#int64BoundaryHandlingIsCorrect` 钉死。
> **教训**：复核报告里的「实测数字」也要复核。这次是**修复方**差点被误报牵着走 ——
> 若当初没写那条断言 `bitLength() == 63`，就会把一个不存在的缺陷「修」成一次无意义改动，
> 并在 AGENTS.md 里留下一条假记录。

#### 🟥 元教训：**带「Go 出处」引用的绿测试，可能在为反向漂移背书**

DRIFT-002 被发现时，**两条绿测试正锁着错误答案**，且 `@DisplayName` 里**直接引用 Go 作为理由**：

```
LikeApiMvcPipelineTest#missingSliceIsNotAParseFailure
   @DisplayName("⚠️ target_ids 缺席**不算**缺字段（Go: Slice 永不计必填）")
LikeReqGoStyleValidationTest#sliceTargetIdsIsNeverRequired
   @DisplayName("⚠️ target_ids 缺席**不**算缺字段（Go: Slice 永不计必填）")
```

这比 AGENTS.md §3.3 已记录的「绿测试守卫错误答案」**更进一步**：
§3.3 说的是「测试断言 RAG 的现状却自称对齐 Go」，本次是**测试名里引用了一个被独立复核推翻的 Go 结论**。
⇒ 危害在于**修复会先撞上这两条红测试**，且测试名会让人以为「修复」才是漂移。
⇒ **新增硬纪律：任何以「Go: …」形式写进 `@DisplayName` / javadoc 的断言，
其 Go 依据必须带 `文件:行号`，且该行号要指向「消费该 tag 的控制流」而不是「tag 本身」。**

#### ✅ 经复核**站得住**的部分（不要在后续轮次里重复怀疑）

鉴权 401 + **零字节 body**；身份失败 **1006**（不是 401、不是 5001）；6 个端点的
**鉴权不对称**（`getlikecount` / `gettargetlikerlist` 不读 uid）；
`status.FromError` 的 `ok` 标志二分（status → 业务码 + `st.Message()` **原文**；
非 status → **5001**，**不是 1015**）；`options=1|2|3|4`；`,string` 的 `author_id` **字符串与数字都收**；
`success` **硬编码 true**（Go 不读 proto 的 `success`）；`weight` float64→float32 窄化；
`counts` / `list` **恒为 `{}` / `[]` 而非 null**；`is_end` 不会多吐一个 `end` 键。
⇒ 这些由 verifier 用**活体 Go 探针**（56 例解析矩阵 + 配置默认值 + envelope 字节）对拍确认，非仅源码推断。

#### ⚠️ `like-api` 的口径边界

**44 条绿测试不能作为「无漂移」的证据**：本模块证明了「绿测试 + 带 Go 出处的 `@DisplayName`」
仍可能是反向漂移的守卫。真源口径以 verifier 的 Go 侧取证（探针 + `file:line`）为准。

### ✅ 请求保护层**下沉到 `service/common`**：`com.rag.message...protection` → `com.rag.common.protection`（2026-10-22）

> 用户拍板「那扔common」。本节记录**搬了什么**、**为什么**、**搬的过程中实测挖出的三件事**
> （其中一件推翻了我自己写在代码注释里的错误推理）。

#### 搬了什么

| 内容 | 从 | 到 | 理由 |
|---|---|---|---|
| 10 个主类（4 Filter + breaker + shedder + properties + 状态包装 + 熔断异常 + client 拦截器） | `com.rag.message.message.api.protection` | **`com.rag.common.protection`** | Go 这四层是 **go-zero `rest.Server` 的内建行为**，不是 message 域的代码 —— 任何 `rest.MustNewServer` 起的进程都自带 |
| 4 个纯单测 | 同上 | `com.rag.common.protection`（test） | 无 Spring 上下文、无 message 依赖，纯粹搬家 |
| 4 个集成测试 | 同上 | `com.rag.message.message.api`（**留在 message-api**，改包名） | 它们证明的是「message-api 的装配」，不是「这层逻辑对不对」 |

**依据**：搬迁前 grep 确认 10 个类**零 `com.rag.message` / `com.rag.users` 内部 import**；
`service/common` 现有依赖已含 `jakarta.servlet-api`(provided) / `spring-boot-starter` / `spring-web`(optional) / `grpc-stub`。

#### 🔴 搬的过程中实测挖出①：**「私有组件变共享」必须连同它的装配义务一起搬**

下沉后立刻炸了 **`admin-api` 23 条测试全红**：

```
Error creating bean with name 'goStyleBreakerFilter'
  ... Unsatisfied dependency expressed through constructor parameter 0:
  No qualifying bean of type 'com.rag.common.protection.GoStyleProtectionProperties' available
```

根因：`@ConfigurationProperties` 类**不会**被组件扫描自动注册，必须由
`@EnableConfigurationProperties` 声明；而当时**只有 message-api 写了那一行**。
⇒ 修法不是「给每个下游模块补一行」，而是新增 `GoStyleProtectionConfiguration`
（`@Configuration` + `@EnableConfigurationProperties`）**放在同一个包里**，
让「被扫到即生效」。message-api 随即**删掉**了自己那行显式声明。

> **可复用的教训**：组件从私有变共享时，它的装配义务必须一起共享。
> 把装配写成 N 份，漏一份就是一个**启动即崩**的模块 —— 而且崩在下游、看起来与本次改动无关。

#### 🔴 搬的过程中实测挖出②：**我写在 javadoc 里的「顺序」推理是错的**

`GoStyleMaxBytesFilter` 原先写着「注册在 `HIGHEST_PRECEDENCE + 6`（早于 auth 拦截器进入）以保住 413 压过 401」。
**变异测试直接推翻它**：把 `@Order` 改成 `LOWEST_PRECEDENCE` 后，message-api 与 like-api 的
「超限 → 413 而非 401」断言**依然全绿**（且已用 `javap` 确认 jar 里的 `@Order` 确实变成了
`2147483647`，即变异真的进了产物 —— 不是变异没生效）。

**正确机制**：Servlet 的**所有** `Filter` 都包在 `DispatcherServlet` **外面**，而 `HandlerInterceptor`
（JWT 鉴权）跑在其**内**部 ⇒ 两者的先后由**容器结构**决定，**与 order 数值无关**。
`@Order` 的真实作用是**在 Filter 之间排序**（相对 `HttpMiddlewareFilter` 的 `+5`、
相对同包另外三个保护 Filter 的 `+1/+2/+3`），以及对齐 Go 的 native middleware 同序。

⇒ 能真正让该断言变红的变异是**让这层失效**（摘掉 `@Component`），已实测：
**`Status expected:<413> but was:<401>`**。
三处错误表述（`GoStyleMaxBytesFilter` 类 javadoc、两个测试的注释、本文档上一段）已同步改正。

> **元教训（与 §6.7.7 互补）**：变异测试**没红**有两种原因 ——「测试是假绿」与「**变异选错了对象**」。
> 区分它们必须**先验证变异真的进了产物**（本次用 `javap` 读 `~/.m2` 里的 class），
> 再判断归因。**直接跳到「测试有问题」是错的。**

#### 搬的过程中实测挖出③：一条 `@DisplayName` 断言原本指向**不存在的守门脚本**

`MessageApiProtectionMvcTest` 的类 javadoc 写着「响应头那层已由
`tools/difftest/check-protection-layer.sh` 在真容器下守」—— 按 `find` 实测，**该脚本不存在**，
响应头一直由 `MessageApiProtectionRealTomcatTest` 守着。已改正。
⇒ §3.3 的形态：**javadoc 里写「已由 X 守」不等于 X 存在**。

#### 加的两道装配守卫（**诚实边界：都不是补现行 bug**）

实测（`mvn dependency:list -pl service/message/message-rpc`）：该模块依赖树里
**没有** `jakarta.servlet-api`、也没有 `tomcat-embed-core`（只有不含 servlet API 的 `tomcat-embed-el`）；
而四个 rpc 进程又**已经刻意不扫 `com.rag.common`**（原因正是同包下 `HttpMiddlewareFilter` 是 servlet Filter）。
⇒ 今天两者**没有相交**。但那句「不扫 common」写在每个 rpc 进程的注释里，而
`com.rag.common` 恰恰是唯一必须被所有进程共享的包 ⇒ 用条件注解把「这是 HTTP 层」编码进去：

| 类 | 注解 | 覆盖 |
|---|---|---|
| 4 个 Filter | `@ConditionalOnWebApplication(SERVLET)` | 无 servlet 的进程不注册 |
| `GoStyleGrpcClientBreakerInterceptor` | `@ConditionalOnClass({GrpcGlobalClientInterceptor, ManagedChannel})` | 无 devh starter 的进程不注册 |

`service/common` 里 devh client starter 声明为 **`optional=true`**（同 `spring-web` 的理由）：
普通 compile 会把整套 gRPC 客户端栈 + Nacos 传递给每个只需要一张错误码表的纯库模块。

**两道守卫都有变异实证**：摘掉 `@ConditionalOnWebApplication` ⇒
`ProtectionNotRegisteredInNonWebContextTest` 转红（`expected: <false> but was: <true>`）。

#### 一处**部署可见**变更

配置前缀 **`message-api.protection` → `rag.protection`**（共享组件不该归某一业务域所有）。
各服务 yaml 与测试属性覆盖已同步改写。**漏改的后果是回落到默认值，而默认值 == go-zero tag 默认值**
⇒ 不会静默关闭保护层，是刻意选的 fail-safe 方向。

#### ✅ 连带关闭 `like-api` 的 DRIFT-003 / 004 / 005

`like-api` 的 `scanBasePackages` **本来就含 `com.rag.common`** ⇒ 保护层对它**自动生效、零装配代码**。
正因为零装配，**没有任何编译期或启动期信号**告诉使用者「这层在你身上生效了」⇒ 必须补测试，否则
就是一条**从未验证过就生效的行为变更**（§3.3）。新增 `LikeApiProtectionMvcTest`（4 条）：

| 断言 | 覆盖 |
|---|---|
| 四个 Filter Bean 都存在 | 「自动生效」这个前提本身 |
| 超限 + **无 token** → **413** | DRIFT-004（413 压过 401） |
| 未超限 + 无 token → **401** | 上一条不是被无条件短路 |
| 未超限 + 合法 token → **200 + envelope** | 正常路径没被误拒 |

**DRIFT-005（gRPC client breaker）对 like-api 无需任何 Java 改动**：
Go `getlikestatelogic.go:57-63` 的 `status.FromError` 对非 status 错误返回 `ok=false` ⇒
`NewErrCode(ErrorServerCommon)` = **5001**；而 `LikeApiSupport#rpcError` 已经实现了同一条 `ok` 二分
（`extractStatus` 返回 null ⇒ 5001）。⇒ 熔断异常自然落到正确分支。
⚠️ 这与 message-api **不同**：message 域那条路径给 **1015**，所以它需要单列 `@ExceptionHandler`。

#### 测试归属的一处调整：1015 的 msg 断言从 `common` 移到 `message-api`

`GoStyleGrpcClientBreakerInterceptorTest` 原先断言「熔断拒绝 → 1015 + msg `service busy`」，
它 import 了 `message-common` 的码表 ⇒ **会让 `common` 反向依赖业务模块**（common 是所有业务模块的下游）。
拆成两半：`common` 只留**机制**断言（不是 `StatusException` / `StatusRuntimeException`），
**码表映射**那条挪到 `GoStyleGrpcClientBreakerE2ETest`，且在那里是**真运行时**断言
（读真实 envelope 的 `msg`）—— 证据强度高于原来的静态映射断言：
它同时证明「异常被谁接住」「接到哪个码」「文案从哪来」三件事。

#### 实测口径（各模块**隔离单跑**，非全仓汇总）

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **134** | 102 + 32（迁入的 4 个纯单测类） |
| `service/message/message-api` | **70** | 68 + 2（`ProtectionNotRegisteredInNonWebContextTest`） |
| `service/like/like-api` | **51** | 47 + 4（`LikeApiProtectionMvcTest`） |
| `service/users/admin/admin-api` | **113** | 下沉曾致 23 红，已由 `GoStyleProtectionConfiguration` 修复 |
| `service/users/user/user-api` | **63** | 无变化 |
| `service/hot/hot-api` | BUILD SUCCESS | 无变化 |

⚠️ 本次全程有**并行 Maven 会话**在跑，期间出现过一次 `Wire.java` 的 Lombok
「构造器参数个数不符」编译失败 —— 那是 `target/` 被抢占的偶发，与本次改动无关（重跑即消失）。

#### 本轮（404/405 那节）之后仍未关闭

**`like-api` 已登记的漂移至此全部关闭**（003/004/005/006/007/008/010/011 均已处理，009 是被复核推翻的误报）。
最后关闭的 006 见下一节 —— ⚠️ 它顺带推翻了 message-api 那条「两侧都是拒绝」的签字理由。

⚠️ 本节原先还写着「DRIFT-007/008 未修」与「DRIFT-001 取消语义未闭合（`Future.cancel(true)`
**无法中断**阻塞 gRPC 调用，需显式 deadline 透传）」—— **两条都已关闭，而后者那句前提是错的**：

- DRIFT-007/008 → 见下一节（✅ 已修，并顺带挖出 DRIFT-LIKEAPI-011）。
- 取消语义 → 见「取消语义对齐」那节（✅ 已修：池加界 + deadline 透传）。
  ⚠️ 「`Future.cancel(true)` 无法中断阻塞 gRPC 调用」**被实测推翻**：它**能**中断，
  只是产生的 status 是 `CANCELLED` 而非 `DEADLINE_EXCEEDED`。
  ⇒ 原判断描述的现象**根本不存在**，真正的问题是**超时对 client breaker 不可见**。

### ✅ 404 / 405 字节级对齐（DRIFT-LIKEAPI-007 / -008），并挖出 **DRIFT-LIKEAPI-011**（2026-10-22）

> 用户「继续」。本节记录：修两条已登记的表达层漂移时，**发现其中一条的真正问题比登记的更大**。

#### Go 侧真源（`go-zero v1.10.2`，两条**形状完全相反**）

| | Evidence | 形状 |
|---|---|---|
| **404** | `router/patrouter.go:73,93-97` → `http.NotFound` → `http.NotFoundHandler()` → `http.Error(w,"404 page not found",404)`；`net/http.Error` 做四件事：`Del("Content-Length")` / `Set("Content-Type","text/plain; charset=utf-8")` / `Set("X-Content-Type-Options","nosniff")` / `WriteHeader` + `Fprintln` | **有 body**（`"404 page not found\n"`，含尾随换行，全长 20）+ 有 `Content-Type` + 有 `nosniff` |
| **405** | `router/patrouter.go:79-82`：`w.Header().Set("Allow", allows)` + `w.WriteHeader(405)` | **裸状态码**：无 body、**无** `Content-Type`，只多一个 `Allow` |

⚠️ 这是本项目遇到的**第三种响应形状**（前两种：业务错误 = 200+envelope；保护层 = 裸状态码），
**最容易被「顺手统一」** ⇒ 测试逐项钉死两种形状**相反**这一点。

⚠️ **`Allow` 的取值顺序在 Go 侧不确定**：`patrouter.go:101-117` 的 `methodsAllowed` 是
`for treeMethod, tree := range pr.trees` —— **遍历 Go map，顺序随机**。
本实现**原样透传不排序**：排序＝新增行为＝漂移。当前 like/message 的路由全是 POST ⇒ 只有一个值，不可观测。
**一旦有模块给同一路径注册第二个方法，必须先决定这件事怎么对齐。**

#### 🔴 DRIFT-LIKEAPI-011：修 404 时发现**根本没有 404**

真 Tomcat 探针（`RANDOM_PORT` + JDK HttpClient 读原始响应）：

```
/like/v1/nope        -> 401 | ct=<none>     | body=[]                    ← 修复前
/nope                -> 404 | ct=text/plain;charset=utf-8 | body=[404 page not found\n]
/actuator/nope       -> 404 | ct=text/plain;charset=utf-8 | body=[404 page not found\n]
/like/v1/getlikecount-> 401 | ct=<none>     | body=[]
```

**根因**：Go 的 `engine.go:104-118` `bindRoute` 只把 chain（native + auth + 用户中间件）
挂到 `router.Handle(method, path, handle)` 上 ⇒ **chain 只对匹配上的路由生效**；
`patrouter.go:73,79` 的 404/405 分支**根本进不了这条链** ⇒ Go 侧未知路径恒 404，与 token 无关。

Spring 侧**默认做不到**：Boot 的静态资源处理器匹配 `/**`，未注册路径**也拿到了 handler**
⇒ `mappedHandler != null` ⇒ `HandlerInterceptor` 照跑 ⇒ 404 被鉴权遮蔽成 401。

**修法**：`preHandle` 加一行守卫

```java
if (!(handler instanceof org.springframework.web.method.HandlerMethod)) { return true; }
```

`instanceof HandlerMethod` 精确对应 Go 那句「有没有匹配上 `@RequestMapping` 方法」——
`ResourceHttpRequestHandler` 不是 `HandlerMethod`。⚠️ **不要改成「路径是否以 `/v1/` 开头」之类的字符串判断**：
那是重新发明一遍路由表。`message-api` 同型一并修（`AbstractMessageJwtAuthInterceptor`）。

⚠️ **既有的 404 测试从未覆盖这条**：`MessageApiMvcPipelineTest#unknownPathIs404` **带了合法 token**，
所以它测的是「有 token 时的 404」，不是「无 token 的未知路径」。
⇒ 这条漂移能长期存在，是因为**绿测试断言的是「带 token」这一条路径**。
已补 `MessageApiRouteAuthBoundaryTest`（MockMvc，3 条）与
`LikeApiRouterErrorRealTomcatTest`（真 Tomcat，4 条）双向钉死。

#### 一处**刻意不消除**的字节差异

Go 的 `net/http` 逐字写出 `text/plain; charset=utf-8`（分号后**有空格**），
真 Tomcat 会规范化成 `text/plain;charset=utf-8`（**无空格**）。按 RFC 9110 参数分隔符两旁的 OWS 不显著
⇒ 语义相同。**不与容器对抗**，但测试必须**解析后比对**（`ctRaw.replace(" ","")`）——
直接比原始字符串会得到一个「必然红、且红了也不代表有错」的断言（已实测踩到）。
`MediaType.parse(String)` 在 Spring 7 已移除，别照抄老写法。

#### 实现落点：一处共享，两个模块

新增 `com.rag.common.http.GoStyleRouterErrorResponses`（404 / 405 两个工厂）。
理由：二者由 **go-zero 的路由层**产出，形状只取决于「路由有没有匹配上」，与该服务注册了哪些端点无关；
复制到每个 api 模块，早晚出现「message 的 404 有 body、like 的没有」这类静默分叉。

**测试分工**（避免重复又不留缺口）：

- **字节级**（body / Content-Type / nosniff / Content-Length）⇒ `LikeApiRouterErrorRealTomcatTest`
  **真 Tomcat**，因为 MockMvc 不驱动容器选 converter、不参与 `Content-Length`、也测不到「容器会不会补 body」——
  这正是 DRIFT-024 当初漏网的原因。**共享实现只需守一次。**
- **每模块的拦截器守卫** ⇒ 各守一次（`LikeApiRouterErrorRealTomcatTest#routerErrorsBypassAuth`
  + `MessageApiRouteAuthBoundaryTest`），因为守卫代码是**每个模块各写一遍**的。

#### 变异实证（每次变异与还原都 `mvn clean`，且确认变异进了产物）

| 变异 | 结果 |
|---|---|
| 摘掉 `preHandle` 的 `HandlerMethod` 守卫 | **3 条转红**，`expected: <404> but was: <401>` |
| 把共享实现的 404 body 改成 `"MUT"` | **2 条转红**，body 与「以换行结尾」两条同时抓到 |

#### 实测口径

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **134** | 不变（共享实现在此模块，字节级守门在 like 侧） |
| `service/like/like-api` | **55** | 51 + 4（`LikeApiRouterErrorRealTomcatTest`） |
| `service/message/message-api` | **73** | 70 + 3（`MessageApiRouteAuthBoundaryTest`） |

⚠️ 真 Tomcat 测试必须多配一条 `"spring.cloud.nacos.discovery.enabled=false"`：
`RANDOM_PORT` 会真的把 webServer 启起来，naming client 随即注册并在 `STARTING` 态报
「Client not connected」而让 `webServerStartStop` 启动失败（实测）。
**MOCK 环境不启容器所以不需要它 ⇒ 两种环境的 property 集合不可互相照抄。**
### ✅ HTTP 超时的**取消语义**对齐（DRIFT-001 取消语义部分），并**推翻了我自己写下的一个前提**（2026-10-22）

> 用户拍板：**① 池加界 + deadline 透传，两者都做；② 接受「超时计入 client breaker 失败」**（与 Go `codes.Acceptable` 一致）。
> 本节记录落地过程，以及过程中被实测**两次推翻**的推断 —— 那两条比修复本身更值得留档。

#### Go 侧的机制（逐句）

```go
rest/handler/timeouthandler.go:67-68
    ctx, cancelCtx := context.WithTimeout(r.Context(), h.dt)
    r = r.WithContext(ctx)                      // ← deadline 被【传进业务】
rest/handler/timeouthandler.go:79-89
    go func() { h.handler.ServeHTTP(tw, r); close(done) }()
    select { case <-done: ... case <-ctx.Done(): 写 503; tw.timedOut = true }
```

⇒ 「超时后不 kill goroutine」是**假象**：handler 函数还在跑，但它手上的 ctx 已到期，
所有 **ctx-aware** 的下游 I/O 立即中止。真正可观察的语义是**客户端 status code = `DEADLINE_EXCEEDED`**。

#### 🔴 被实测推翻①：「grpc-java 的阻塞 stub 不可中断」——**错的**

我最初把漂移写成「`Future.cancel(true)` 无法中断阻塞 gRPC 调用，worker 会被钉死」。
**变异测试直接推翻**：摘掉 deadline 传播后，调用并没有挂死，而是以 **`CANCELLED`** 收场。
⇒ 建立 `DeadlineCancellationSemanticsTest`（真 in-process gRPC server + 真阻塞 stub）逐条测出真相：

| 机制 | 客户端看到 | 服务端 handler | 在 `codes.Acceptable` 失败名单里？ | breaker 记为 |
|---|---|---|---|---|
| Go ctx 到期 | `DEADLINE_EXCEEDED` | 自己查 ctx 后退出 | **是** | 失败 |
| Java `Future.cancel(true)` | **`CANCELLED`** | **被线程中断** | **否** | **成功** |
| 客户端取消本身 | — | **不会**被杀掉（除非它自己查 ctx） | — | — |

⚠️ **第三条也是被实测纠正的**：我原以为「客户端取消 ⇒ 下游被中止」。实测服务端 handler
**照常在跑**；只有当 handler 像 Go 那样自己查 ctx（`Context.isCancelled()`）时才会退出。
⇒ 写进测试的方式也随之改了：服务端 handler 改成**像 Go 那样查 ctx**，
并且用 **call id 按调用隔离**记录状态（原用全局标志，被上一个用例残留的 handler 污染，实测偶发红）。

#### 🔴 真正的漂移比原判断**严重得多**：超时对熔断器**不可见**

补 deadline 之前，超时走 interrupt 路径 ⇒ `CANCELLED` ⇒ **不在** Go 的六个失败 code 里
⇒ breaker 把它当**成功** ⇒ **下游变慢对 client breaker 完全不可见，熔断器永远不会因超时打开**。
这才是 deadline 透传的实际收益，也正是用户选择「接受计入失败」的实质价值
（在补 deadline 之前，`DEADLINE_EXCEEDED` 这条判据是「按 Go 写好但**从未被触发**」的静态断言）。
已由 `GoStyleGrpcClientBreakerInterceptorTest#timeoutPathsDifferForTheBreaker` 双向钉死。

#### 落地：Step 1 池加界

`HttpMiddleware.HTTP_EXECUTOR` 由 `Executors.newCachedThreadPool`（`maxPoolSize == Integer.MAX_VALUE`）
改为 `ThreadPoolExecutor(0, 512, SynchronousQueue, AbortPolicy)`：

- ⚠️ **必须仍是 `SynchronousQueue`** —— 与旧形状一致（提交即交接、不排队）。
  换成 `LinkedBlockingQueue` 会引入「排队」语义，超时计时就从「进入业务」推迟到「开始处理」，**那是新行为**。
- ⚠️ **不用 `CallerRunsPolicy`** —— 它把业务搬回容器线程，等于超时保护整条失效。
  拒绝时回 **`maxconnshandler.go:12-36` 的裸 503**：有状态码、**无 body、无 Content-Type**，
  且**刻意与「超时 503」不同**（超时那条走 `writeTimeoutResponse`，有 body）——
  两者可观察含义不同：容量不足是「现在别来」，超时是「来不及了」。
- `512 ≈ 2.5 × Tomcat 默认 max-threads(200)`，使正常流量永不触发拒绝。
  ⚠️ 是**常量不是配置项**（本类是 static 工具、Spring 装配前就初始化，挂配置只会得到
  「看起来能配、实际改了没反应」的面板）。

#### 落地：Step 2 deadline 透传

新增 `com.rag.common.observability.GoStyleRequestDeadline`（ThreadLocal 载体 + `applyTo(stub)`）：

- **在 worker 线程上 `begin`**（不是容器线程 —— 容器线程的 ThreadLocal 对 worker **不可见**，最容易写错处），
  **在同一个 `finally` 里 `clear`**（否则线程池复用会串味）。
- 预算**在 `submit` 之前**起算（Go 也是在起 goroutine 之前 `WithTimeout`）。
- 预算耗尽时给 **1 纳秒**而不是「无 deadline」—— 否则会发起一个没有 deadline 的慢调用，正是要修的那个漂移。
- 接入点选在 **`LikeRpcClient#likeRpc()` / `MessageRpcClient#messageRpc()` 这两个 getter**：
  所有调用点都经由它们，改一处即全覆盖；漏一处就是「有的有 deadline、有的没有」的不一致。
  ⚠️ **不要把返回值缓存到字段上** —— 会把「第一次调用时的预算」固化下来。

⚠️ **`DEADLINE_EXCEEDED` 与外层超时同时到期是 Go 本身就有的竞态**
（Go 的 `select` 在 `done` 与 `ctx.Done()` 之间也是随机选）⇒ 测试**不能**断言「外层不双写」。
改为：服务端睡 30s、外层超时 1s ⇒ **唯一**能让调用结束的就是 deadline 本身，
再用 latch 等内层收场。这样断言不再依赖先后顺序。

#### 变异实证（每次变异与还原都 `mvn clean`）

| 变异 | 结果 |
|---|---|
| 摘掉 worker 里的 `GoStyleRequestDeadline.begin(...)` | e2e 转红：内层不再是 `DEADLINE_EXCEEDED` |
| `MAX_HTTP_WORKERS` → `Integer.MAX_VALUE` | **2 条转红**，其中饱和测试直接报「打满 544 个任务后必须出现拒绝，否则池其实无界」 |

#### 🔴 附带教训：我自己写的测试踩了自己警告的坑

`GoStyleRequestDeadline` 的 javadoc 警告「ThreadLocal 会串味」。
**紧接着** `DeadlineCancellationSemanticsTest` 因为漏写 `@AfterEach` 清 deadline，
把 `GoStyleRequestDeadlineTest#returnsStubUnchangedWithoutDeadline` 搞红
（surefire 所有测试类共用同一条线程）。
⇒ 那条 javadoc 警告是真的，而且**写下来不防自己**。

#### 实测口径

| 模块 | 结果 | 变化 |
|---|---|---|
| `service/common` | **146** | 134 + 12（池上界 3 + deadline 4 + 取证 4 + breaker 判据 1） |
| `service/like/like-api` | **55** | 不变（接线改动，无新增断言） |
| `service/message/message-api` | **73** | 不变 |
| `admin-api` / `user-api` / `hot-api` | **113 / 63 / BUILD SUCCESS** | 不变 |

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

#### 🔴 补网：admin-api / user-api **自动继承了共享组件，却一条测试都没有**（2026-10-22 晚）

上一节那次 admin-api 回归之所以**没有任何一条本模块测试抓到**，根因不是判据写错，而是
**admin-api 从来没有发过非 JSON 的 Content-Type**。`GoStyleJsonBodyConverterConfig` 被
`com.rag.common` 的组件扫描自动应用到所有 api 模块（admin / user / hot 全部），
而 like-api 有 7 条、message-api 有 4 条对拍用例，**admin-api 与 user-api 各 0 条**。
⇒ 这是一处**已知的、无人看守的行为变更**：共享层下一次改动照样能把它们打坏，而 CI 全绿。

补 `AdminApiGoStyleBodyContentTypeTest`(6) + `UserApiGoStyleBodyContentTypeTest`(6)，与 like/message 同构。

##### 判据测试刻意走 **public 的 `/x/v1/login`**，这是选择而不是顺手

`LoginReq` 两个字段都是 go-zero **标量必填**（`types.go` 里无 tag ⇒ 非 `,optional`），
所以「body 被丢弃」与「body 为空」必然撞出**同一条错误文本**，可以做逐字节对比。
而它**不需要 token**（Go `routes.go` 第一段 `AddRoutes` 不带 `WithJwt`）
⇒ 判据测试完全不碰 Redis，把「body 解析」与「鉴权」两层**隔离开**。

⚠️ **刻意没有用 `/admin/v1/getuser` 做这条断言**：Go `types.go` 的 `GetUserReq.Uid` 标的是
**`form:"uid,string"` 而不是 `json`**。没有 `json` tag 时 go-zero 回退到**字段名**，
那条「key 到底叫什么、是否必填」的推导链在本次范围外且未实测
⇒ 拿它当判据的支点，等于把一个**未验证的前提**写进断言（§3.3.2）。

##### 🔴 变异实测暴露了一件**反直觉**的事：只比「两条响应相等」是**不够**的

| 变异 | admin-api | user-api |
|---|---|---|
| `goReadsBody` 改成「永远返回 true」 | **3 条红** | **3 条红** |
| `canWrite(ResolvableType,...)` 改成 `true`（复现 2026-10-22 事故） | **3 条红** | **3 条红** |

两次变异都用 `javap -p -c` 读 `~/.m2` 里的 class 确认过**真的生效**
（分别看到 `ldc MUTATION_ALWAYS_READ` 与 `iconst_1; ireturn`）。

⚠️ **第二项变异才是本节最有价值的产出**：它的失败形态是

```
expected: <field "username" is not set
 but was: <"field \"username\" is not set\n">
```

**状态码仍是 400、Content-Type 仍是 `text/plain`，只有 body 被转义** —— 与事故当天逐字一致。
而此时「`{}` 与 `text/plain` 两条响应逐字节相等」那条断言**依然是绿的**，
因为**两侧被同样地转义了，相对关系没变**。

⇒ **两条响应互相比较，只能证伪「判据不同」，证伪不了「两条一起被写坏」。**
必须再有一条**独立于响应之间比较**的**精确文案断言**
（`assertEquals("field \"username\" is not set\n", body)`）才抓得住。
⇒ 写「两侧对拍」类测试时，**「彼此相等」与「等于某个绝对值」是两种不同的断言，不能互相替代**。

##### 附带：这两类的 400 是 `text/plain`，与 like/message 的 `200 + envelope` 不同

测试里同时断言了 `Content-Type: text/plain` + `X-Content-Type-Options: nosniff` +
**尾随换行**（Go 的 `fmt.Fprintln`），就是为了把「共享判据、不共享表现」这条线在测试里钉死 ——
否则日后有人「顺手统一」两个域的失败形态，这 12 条不会有任何一条报警。

### 🔴 `service/like/like-rpc`：已落地，独立子会话验证判定 **NOT EQUIVALENT**，12 条漂移（2026-10-22）

> 本节是独立 verifier 子会话（Go → Spring Boot 基础组件语义一致性验证）的裁定。
> 模块现状：Go `service/like/rpc` → Java `service/like/like-rpc`，主源 39 文件 / 5247 行
> + 5 份 Flyway 迁移，**31 tests 全绿**（Redis 降级 8 / 幂等状态机 11 / 基础件 9 / gRPC wire 3）。
> ⚠️ **31 条绿测试不能推翻此判定** —— 三条 CRITICAL/HIGH 漂移恰好全部落在
> 「需要真 DB / 真 Redis / 真 Kafka / 跨语言对拍」的缺口里（见末尾盲区分析）。

#### ✅ 已签字：DRIFT-LIKE-001 按「Java 现状即为已修正」处理（2026-10-22）

`GetUserLikeList` / `GetTargetLikerList` 的 DB 回源，Go 因 GORM 列名不匹配
（`CreateTime` → `create_time`，而 `Select` 取 `created_at`）导致 timestamp **恒为 0**，
`if timestamp==0 { continue }` 对每行成立 ⇒ **列表恒空**；而 Redis ZSet 的 ZADD 只在
回源成功后才执行 ⇒ **回源路径从功能上是死的**，且返回 `isEnd=false` + `nextCursor=0` 的矛盾态。
**实证**：真 PG + gorm v1.31.1，`schema.LookUpField("created_at")` 返回 `nil`，插入真实行后
`Scan` 得 `CreateTime=0`（期望 `1792670400`）。Java 侧 `LikeRecordMapper.java:121,139-147`
显式映射，返回真实数据。

⇒ **签字：Go 的空列表是缺陷不是设计，Java 现状为已修正的 bug，两侧不一致属有意，不再对齐。**
⚠️ 边界必须同时记住：Go 侧该功能**冷缓存下不可用**；灰度期两端同跑时
**Java 会返回 Go 永远返回不了的数据**——这是修正的代价，不是新缺陷。

#### 🔴 跨仓库拓扑：事实链路的下游在**第三个仓库** `Sea-BreakTheWaves`（2026-10-22 新发现）

> ⚠️ **本条修正了一条错误的否定式断言**：先前判定「DC 接收端不在本仓、无法验证」，
> 实际接收方在 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`。
> AGENTS.md §3.3 的纪律在此再次生效：**否定式断言默认不可信**——
> 「grep 不到实现」≠「不存在」，**跨仓库时尤其**。当时只搜了两个目录就下了结论。

```
Sea-RideTheWind_golang/service/like/rpc
  ├─ appendLikeFact      写 like_domain_fact_outbox（与状态变更同一事务）
  ├─ cmd/fact-dispatch   POST 外部 DataCenter {DC_PLATFORM_EVENT_URL}/v1/events
  │      delivery_status: 0 待投递 / 1 ACCEPTED / 2 FAILED(可重试) / 3 BLOCKED(终止重试·需人工)
  └─ cmd/fact-authority  GET  /internal/v1/community/facts        ◀───┐ HTTP 回调
Sea-BreakTheWaves/service/async/rpc/cmd/worker                        │
  └─ 消费 job type「usermodel.like-facts.v1」                        │
     ├─ 断言 DC 回执（producer / event_id / input_hash / offset /
     │        receipt_id / technical_status==accepted 全部对得上）     │
     └─ 回调 like 权威端点逐字段核对事实，才肯落库 ─────────────────────┘
```

**Evidence（Go 侧）**：
- `service/like/rpc/cmd/fact-dispatch/main.go:52-55`（`LIKE_DATABASE_URL` / `DC_PLATFORM_EVENT_URL` /
  `DC_PLATFORM_SERVICE_TOKEN`，token 长度 <32 **直接拒绝启动**）、`:72`（HTTP 8s 超时）
- `service/like/rpc/internal/model/fact_delivery.go:52-126`（`DispatchFactOnce`：
  `FOR UPDATE SKIP LOCKED` 认领 → 前驱版本必须全部 ACCEPTED → 投递 → 结算；
  冻结信封校验不过 ⇒ **BLOCKED 终止重试**要求人工迁移，失败 ⇒ FAILED 可重试）
- `service/like/rpc/cmd/fact-authority/main.go:71`（`NewAuthorityHandler("rtw.like-mq", ...)`）
- `service/common/communityfact/authority_http.go:28`
  （`mux.HandleFunc("GET /internal/v1/community/facts", ...)`）
- `service/common/communityfact/wire.go:87`（DC endpoint 的 path **必须恰为** `/v1/events`）

**Evidence（`Sea-BreakTheWaves` 侧 = 下游消费者）**：
- `service/async/rpc/cmd/worker/config.go:23`（`likeFactJobType = "usermodel.like-facts.v1"`）
- `service/async/rpc/cmd/worker/config.go:167`（like / comment / favorite 同属 community fact job）
- `service/async/rpc/cmd/worker/config.go:175,196`（`BTW_COMMUNITY_AUTHORITY_URL` / `_TOKEN`）
- `service/async/rpc/cmd/worker/favorite.go:183-186`（`case likeFactJobType` → `NewCommunityAuthorityBinder`）
- `service/async/rpc/internal/app/community_authority_binder.go:46-62`
  （BaseURL **path 必须为空**、token ≥32、3s 超时）、`:110-113`（回执断言）、
  `:116-118`（**确切请求 URL**）、`:65-71`（响应体形状）
- `service/async/rpc/cmd/worker/community_process_integration_test.go:105`
  （like 用例读 `SEA_LIKE_AUTHORITY_URL` / `SEA_LIKE_AUTHORITY_TOKEN`）

#### ✅ 权威端点契约已冻结（`LikeAuthorityEndpointConfig` 原「⚠️ UNKNOWN」注释作废）

| 项 | 冻结值 |
|---|---|
| 方法 + 路径 | `GET /internal/v1/community/facts` |
| 查询参数 | `producer`（仅 `rtw.like-mq` / `rtw.comment-mq`）、`event_id`（≤128 字符） |
| 鉴权 | `Authorization: Bearer <token>`，token **≥32 字符**，否则拒绝装配 |
| 超时 / 上限 | 调用方 3s；响应体 ≤ 256 KiB；**strict JSON**（未知字段即失败） |
| 响应体 | `{event, subject_ref:{issuer,subject_id}, predecessor_event_id?, technical_receipt:{...}, source_event_hash}` |

⇒ 仍需给 like-rpc **新增 HTTP connector**（BaseURL 不能带 path ⇒ 不能挂在 gRPC 端口之后），
但性质从「UNKNOWN 待拍板」降级为「按已冻结契约实现，端口走配置」。

#### 🔴 DRIFT-LIKE-003 改判：事实投递链路缺失，**由「建议关停」翻转为「必须补齐」**

Java 侧 **6 个文件**是 4 行骨架（verifier 报 5 个，**漏计 `LikeFactDispatchScheduler`**）：
`LikeFactProcessLogging` / `LikeAuthorityEndpointConfig` / `LikeFactDispatchScheduler` /
`LikeAuthorityLookupService` / `LikeDeliveryValidator` / `LikeFactDispatchService`。
而生产侧 `LikeDomainFactService`（175 行）**完整**，`LikeDomainFactOutboxMapper` 的
`claimNextDeliverable` / `markBlocked` / `markDeliveryFailed` / `markDelivered` /
`selectByEventId` / `selectByAggregateIdOrderByVersion` 也**全部就绪且零调用方**
⇒ **只写不送**：`delivery_status` 恒 0、`retry_count` 恒 0、
`technical_receipt_id` / `technical_input_hash` / `technical_offset` /
`technical_received_at` / `delivered_at` 五列恒空。

⚠️ **一次反向误判已被证伪并记录在此**：曾建议「关停事实生产 + 留档偏离」，
其核心论据是「接收端不在本仓、无法验证」与「契约 UNKNOWN」——**两条均被 `Sea-BreakTheWaves` 推翻**。
不补的后果不止「表无限增长」：会让 `Sea-BreakTheWaves` 的 **usermodel 画像永久缺 like 的全部
交互事实**（静默缺失，最难发现的一类故障）。

⚠️ **唯一仍为真的证据边界**：真正的 DC 接收服务 `/v1/events` **在两个仓里都只有发送方/客户端**
（`Sea-BreakTheWaves/service/common/clients/datacenter/client.go:153,164` 是客户端）
⇒ **投递环节只能对 stub server 验证，不能对真 DC 验证**。

#### ⚠️ 决策前提：dispatch / authority 与 like-rpc **只经 DB 耦合**

两个 Go 入口都是**独立进程、纯 DB 客户端**，不调用 like-rpc 的任何 gRPC 方法
（`cmd/fact-dispatch/main.go:52`、`cmd/fact-authority/main.go` 均只取 `LIKE_DATABASE_URL`，
投递目标由 `DC_PLATFORM_EVENT_URL` 决定，与 like-rpc 的服务端口无关）。
⇒ **生产侧（`appendLikeFact`，与状态变更同事务）必须在 Java like-rpc 内；
投递侧与权威读侧可以继续留在 Go**，只需让 Flyway DDL 与 GORM struct tag 的列名/类型保持一致。
⚠️ 但这条跨语言 DDL 耦合本身就是 DRIFT-LIKE-001 那类缺陷的来源（GORM 列名不匹配 ⇒ 静默返零），
⇒ 若决定留 Go，**必须补一条 DDL ↔ GORM 列名一致性静态校验**（`tools/check-ddl-static.py` 可扩展），
否则等于把一个**已经真实发生过**的缺陷留在跨语言边界上。

#### 📊 性能对照：Java 化**不会**让它变慢（2026-10-22 核实，非估算）

> 结论先行：**瓶颈不在语言层。** Java 化的真实差异是**内存占用**，不是吞吐。
> ⚠️ 「JVM 常驻 150-300MB」是 JVM 通用基线，**未为本服务实测 RSS**。

**负载实测特征**：`communityfact/process.go:43-61` 的投递循环是**严格串行**的
（`for i := 0; i < 16; i++ { dispatch() }`，无第二个 goroutine 并发）⇒ **进程内 DB 连接占用峰值为 1**。
单次 `DispatchFactOnce`（`fact_delivery.go:52-126`）= 4 次 PG 往返 + **1 次 HTTP POST（8s 超时）**
⇒ **时间 95%+ 花在等网络**，吞吐上限 16 次/秒。
⇒ **GC / JIT / 启动时间 / 堆大小全部不影响**。

| 维度 | Java vs Go |
|---|---|
| 吞吐 / 单次延迟 | **无差别**（串行 + I/O 主导，瓶颈是 DC 延迟） |
| GC 抖动 | **无实质差别**（payload ~600B、分配速率极低，pause 淹没在 3s/8s 超时里） |
| authority 并发 | Java 略优（Tomcat 线程池 vs goroutine-per-request 的调度开销），两边都远超实际需要 |
| **常驻内存** | **Java 大 10-20 倍**（Go 10-30MB；JVM 150-300MB 起 = 堆 + metaspace + code cache + 线程栈 + JIT 区）。⚠️ 容器化必须配 `-XX:MaxRAMPercentage`，否则 OOMKill。**这是「怪物」的真实形态——不是慢，是大** |
| 连接池 | **Java 更安全**：Hikari 默认 `maximumPoolSize=10`；Go `database/sql` 默认 `MaxOpenConns=0`（**不限**）+ `MaxIdleConns=2` ⇒ **Go 那个「不限」才是多副本下的真风险**（打爆 PG `max_connections`） |

#### 🔴 架构更正：`LikeFactDispatchScheduler` **不得**用 `@Scheduled` 内嵌进 like-rpc（2026-10-22）

原骨架注释「驱动 common Dispatcher（@PreDestroy → handle.stop()）」暗示进程内调度，**方向错误**。
Go 的 `DispatchFactOnce` 是**在事务里发 HTTP**：`Begin` → 行锁 → POST → `Commit`，
而 `process.go:28` 给每次 attempt 套了 **10s context** ⇒ **行锁最长持有 10 秒**。
作为独立进程时这只影响 outbox 表；**塞进 like-rpc 进程后会新增两个 Go 现在没有的耦合**：
① 与 gRPC 服务共享同一进程的连接池 / 线程池，一个卡住的出网调用会拖累主服务；
② 故障不隔离（dispatcher 卡死 ⇒ like-rpc 一起卡）。
⇒ 若决定 Java 化，**必须保持独立进程**（独立模块或独立 Spring Boot 应用），不是 `@Scheduled`。
**这一条比「用 Go 还是 Java」重要得多。**

#### ⚠️ 与语言无关的真隐患：`AuthoritativeFact` 是 O(N) 全量重放

`fact_authority.go:69-73` 每次请求 `SELECT ... WHERE aggregate_id=? ORDER BY fact_version` **拉全部行**从头重放，
而 aggregate 是 `like-state/{userId}/{targetType}/{targetId}` ⇒ 用户对同一目标反复操作 N 次，则单次权威查询 O(N)。
**Go 同样如此，重写成 Java 一点都不会改善。** 若 N 会无界增长，该改的是算法（增量重放 / 只验最近窗口），
不是语言。⚠️ **N 的真实分布尚未取证**，本条列为待评估，不作结论。

#### 其余 11 条漂移摘要（明细见 verifier 报告；均未修复）

| ID | 差异 | 分类 | 风险 |
|---|---|---|---|
| **DRIFT-LIKE-002** | 业务码对**未迁移的 Go like-api** 丢失：Go 直发 `grpc-status: 5003`；grpc-java `fromCodeValue(5003)` → `UNKNOWN(2)`（实测 `desc="Unknown code 5003"`，`Code.values().length == 17`），Java 改放自定义 trailer `rag-like-biz-code`，而 Go like-api 只读 `int(st.Code())` ⇒ 灰度期 envelope code 变 **2**、msg 因 `GetErrMsg(2)` 落表外而回落 `"FAIL"` | SEMANTIC MISMATCH | 🔴 |
| DRIFT-LIKE-004 | Kafka 消费侧 Java 多 **9 次进程内重试**：`new DefaultErrorHandler(recoverer)` 内部即 `SeekUtils.DEFAULT_BACK_OFF = FixedBackOff(0,9)`；Go `kq` 的 `ForceCommit` 默认 true 且未注册 errorHandler ⇒ **0 次**，失败只不提交位点 | OBSERVABLE | 🟠 |
| DRIFT-LIKE-005 | Kafka key/分区：Go `Push` 内部即 `PushWithKey(UnixNano)` 且用 `LeastBytes{}` 分区；Java `send(topic,null,payload)` 走默认分区器 | OBSERVABLE | 🟠 |
| DRIFT-LIKE-006 | 模块**从未做过 Spring 上下文加载测试**（无 `@SpringBootTest` / `ApplicationContextRunner`），而 javadoc 点名的 4 个 wiring 测试**全部不存在** —— 与 §6.6.5 message-rpc 同型 | UNKNOWN | 🟠 |
| DRIFT-LIKE-007 | 未知字段从「忽略」变「丢弃」：`@JsonIgnoreProperties(ignoreUnknown=false)` 覆盖了 Jackson 3 的默认忽略 ⇒ 抛异常 ⇒ 记 `json_error` 并**提交位点**（永久丢失、不可恢复的消息黑洞） | SEMANTIC MISMATCH | 🟡 |
| DRIFT-LIKE-008 | map 形式 `Updates` **会**刷新 `updated_at`（真 PG 实测：`2020-01-01 08:00` → `2026-10-22 19:48:54.598+08`）；Java `updateState` 不刷新 ⇒ `idx_user_list(user_id, updated_at)` 内容两侧不同 | OBSERVABLE | 🟡 |
| DRIFT-LIKE-009 | 指标 label 漂移 4 处：`redis_read_error` 的 target_type 塞字面量且 action 恒 `"n/a"`；Go「字段不存在」也记指标而 Java 直接返回；Go `GetLikeState` 记 `redis_error` 而 Java 不记；Java 把序列化失败并入 `kafka_error` | OBSERVABLE | 🟡 |
| DRIFT-LIKE-010~012 | `.trim()` 容忍空白（Go `ParseInt` 不忽略） / outbox `ON CONFLICT` 目标（Go 无目标、覆盖任意唯一约束冲突） / 可选 producer 开关粒度（Go 两个 pusher 独立判空，Java 合并为单个 `taskPushersEnabled`） | OBSERVABLE | 🟡 |

#### ⚠️ Java 侧 8 条错误断言（其中 5 条经真框架实测证伪，优先级等同缺陷）

| # | 断言内容 | 实测事实 | 出处 |
|---|---|---|---|
| **A** | 「GORM 扫 `created_at` 取的是 `data.Unix()` 即秒，秒/毫秒刻度不一致是真实可观察行为，原样保留」 | GORM **根本没匹配上该列**，`timestamp` 恒 0，整段分析描述的是一条**不可达路径** | `LikeDomainService.java:374-386,944-951` |
| **B** | 「Go 的 `Updates` 用 map 形式不会带上 `updated_at`，保持一致」 | map 形式**会**带上（`ConvertToAssignments` map 分支显式追加），**真 PG 实测** | `LikeRecordMapper.java:194-200` |
| **C** | 「`kq.Pusher.Push(ctx, msg)` **不带 key**」 | `Push` 内部即 `PushWithKey(ctx, strconv.FormatInt(time.Now().UnixNano(),10), v)` ⇒ **带 key** | `LikeDomainService.java:1014`、`LikeHotEventOutboxRelay.java:80` |
| **D** | 「Go `Push` 是**同步**阻塞写，所以这里选 acks=1 + 异步」 | `NewPusher` 默认挂 `ChunkExecutor`，`Push` 只入队并立即返回 ⇒ **异步缓冲**，写错误只出现在 `logx.Error`，永不返回调用方 | `LikeDomainService.java:984-985`、`application.yml:91-94` |
| **E** | 「不做进程内重试……若在 Java 侧加 `FixedBackOff` 做 N 次进程内重试，那是**新增行为**」 | `new DefaultErrorHandler(recoverer)` **内部就是 `FixedBackOff(0,9)`** ⇒ 声称「不做」的事正在发生 | `LikeUpdateKafkaListener.java:44-52,170-175`、`LikeRpcApplication.java:105-110` |
| **F** | 「这五个装配点**每一条都有对应的 wiring 测试锁死**」并点名 2 个测试类 | **4 个测试类一个都不存在**（`LikeMessageIngestWiringTest` / `LikeGrpcInterceptorWiringTest` / `LikeSchemaGateTest` / `NacosRegistrationTest`），模块零 Spring 上下文测试 | `LikeRpcApplication.java:35-55,88`、`LikeMessageIngestService.java:24-26` |
| **G** | outbox relay 注释「`Push` **不带 key**，且用 payload 原文」 | 同 C。「用 payload 原文」这半句是对的 | `LikeHotEventOutboxRelay.java:80` |
| **H** | 「Go 侧 etcd 注册失败会让 `MustNewServer` panic ⇒ 进程退出」 | **未核实**（`zrpc.MustNewServer` 的 etcd 失败路径未查证），标 UNKNOWN | `NacosRegistration.java:91-92` |

⇒ **A / C / D / E 是同一型错误**：只读框架源码或文档，对 Go 侧下了**否定式或类比式断言**而未做实证，
且其中 A / D 描述的代码路径在 Go 侧**根本不可达**。**B 是照搬 GORM 直觉未验证。**
**F 是「把没看过的东西当成看过了」的又一例**（§3.3 同款）。
⇒ 修相邻代码时应顺手清理这 8 条注释，否则会让下一个人沿错误前提继续改。

#### ⚠️ 现有 31 条测试的盲区（解释 3 条 CRITICAL/HIGH 为何全部逃过）

| 测试类 | 真正证明了「与 Go 一致」的部分 | 逃过了什么 |
|---|---|---|
| `LikeDomainServiceRedisDegradationTest`(8) | ✅ Redis 降级语义（Redis 挂/超时都回源 DB、DB 失败才致命、Lua 失败才致命） | ❌ 全是 Mockito mock `StringRedisTemplate`，**从未连过真 Redis**；`getUserLikeList` 只有一条验 `fetchLimit` 参数的用例，**完全不验回源结果** ⇒ 001 逃过 |
| `LikePrimitivesTest`(9) | 部分：`scoreOf/timestampOf` 互逆、并发唯一性、Lua 返回码常量、key 字面量 | ❌ `htmlEscapingMatchesGoEncodingJson` 只验 **Java 自己前后一致**，未与 Go 实跑对拍 |
| `LikeMessageIngestServiceTest`(11) | ✅ 幂等三层顺序、hash 冲突报错重投、单调守卫、软删拒绝、状态机转移 —— 真对 Go 逐行写的 | ❌ 全 mock Mapper，**`@Transactional` 是否真生效（代理有没有被绕过）零证据** |
| `LikeServiceGrpcImplWireTest`(3) | ✅ 业务码经真 in-process gRPC 链路往返、文案保真、同码不同文案 | ❌ 只测 **Java like-api → Java like-rpc**；**无任何「Go like-api → Java like-rpc」验证** ⇒ 002 逃过 |

⇒ **这是系统性盲区不是偶然遗漏**：31 条测试在「降级语义 / 幂等状态机 / 文案保真」三条线上是真对 Go 的，
但它们系统性地绕开了所有需要真 DB / 真 Redis / 真 Kafka / 跨语言对拍的断言。

#### ❌ 8 条 UNRESOLVED（尚无运行证据）

U1 上下文能否启动 · U2 拦截器是否真挂上 gRPC server · U3 Flyway 是否真建表 ·
U4 `@Select` 承载 `INSERT…RETURNING` 与 `:jsonb` 在真 PG 的行为 · U5 真 Kafka 位点行为 ·
**U6 `KafkaTemplate.send` 既无 `.get()` 也无 `whenComplete` ⇒ 异步失败完全静默**
（叠加 DRIFT-005，`kafka_error` 指标几乎永远打不出来） · U7 `MustNewServer` etcd 失败是否 panic ·
U8 `HotspotTTL` 读取点（两侧都不读，无行为影响，但 `LikeRpcProperties` 仍映射了该属性且注释自相矛盾）。
⚠️ **U1 的教训与 §6.6.5 完全同型**：message-rpc 是「21 条全绿却一次都没真正启动过」，
like-rpc 复制了同一套装配代码（`@MapperScan` / `@Import` / `GrpcServerConfigurer` /
`@EnableScheduling` / 自定义 `kafkaListenerContainerFactory`）**却同样零证据表明能起来**。

### ✅ `service/hot/hot-common`：已落地，Go ↔ Java 双侧对拍 **sha256 逐字节一致**（2026-10-22）

> **hot 域唯一的非 api/rpc 组件**，也是 §0「从未验证」清单里 `hot` 一项本次**部分收口**的部分。
> Go 真源只有 1 个文件（`errmsg.go`，121 行），是纯值定义 + 纯函数库：
> **无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存** ⇒ L3 技术语义在本组件上**全部为空**，风险集中在 L1。
> 详细推导见 **`FileTree/hot/common.md`**（`api.md` / `rpc.md` 两处已按其要求同步）。

#### 落地了什么：只有 2 个类

| Go 来源 | Java 落点 | 关系 |
|---|---|---|
| 11 个 const（`errmsg.go:33-46`） | `hot-common/errmsg/HotErrorCode`（enum，**声明顺序 = Go 书写顺序**） | ADAPT |
| `codeMsg` 表（`:48-61`） | `hot-common/errmsg/HotErrorMessages`（**包级私有** `LinkedHashMap`） | ADAPT |
| `GetErrMsg`（`:76-82`） | `HotErrorMessages.getMsg(int)`，未知码回退 `"FAIL"` | ADAPT |
| `CodeError` + `NewErrCode` | **REUSE** `com.rag.common.exception.BusinessException(int, String)` | REUSE |
| `NewErrCodeMsg` / `GRPCError` / `BizCodeFromError` / `bizCodeByMsg` | **有意不迁**（零调用点，见下方边界） | — |

⚠️ **落点是 `hot-common` 而非 `hot-api` 的 `service` 包**，三条互相独立的依据：`hot-api/pom.xml` 早已声明对本模块的依赖、`service/hot/pom.xml` 早已把 `hot-common` 列为 `<module>`、§12.5 推荐 + 四域口径统一（users/message/article 三域均用独立 `-common`）。**类数（2）与落点是两个独立决定**——类数由「码 / 文案是否分离」定，落点由上面的三条定。

#### 三条硬约束（错一条就是客户端可见的错码）

1. **hot 必须自持码表，禁止 REUSE 任何既有 `ErrorCode`** —— hot 的 5002/5003/5004 是 `record not found` / `invalid parameter` / `unauthorized`（**英文**），而 users 与 article 的同号码是「更新数据库失败 / 查询数据库失败 / 数据库插入失败·文件上传失败」（**中文**）⇒ **三方两两冲突**，不存在「取一个通用码表」的选项。
2. **与 message 域「巧合相同」不构成复用许可** —— 两域前 11 个码值文案**逐条相同**（同源模板），但它们是两个独立域：message 日后加码或改文案不得波及 hot，反之亦然。
3. **`GetErrMsg` 的未知码回退 `"FAIL"` 本身属 L1** —— Go `httpx.SetErrorHandler`（`hotcenter.go:53-66`）的错误分支正依赖它产出 `code=5001 / msg="internal server error"` ⇒ **不能返回 `null` 或空串**，也不是返回 code 的字符串。

⚠️ 另有一条**前置条件**被测试钉死：**11 条文案必须两两互异**。Go 的 `bizCodeByMsg` 靠「先到先得」处理重复文案，而 Go map 遍历顺序随机 ⇒ 一旦有两条同文案，反查就变成真随机。当前互异让该分支不可达，这是 Go 侧的正确性前提，不是 Go 的缺陷。

#### 验证：4 层，且都不是「绿了就信」

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `go run` 直接跑真源 `sea-try-go/service/hot/common`（探针放 `/tmp`，**不入 Go 仓**）+ Java 探针输出同格式 dump（11 已知码 + 12 未知码） | **sha256 逐字节一致** `f23fe570…` |
| **契约测试** | 14 条（码值 / 顺序 / 唯一性 / 总数 / 文案 / 双射 / 表内顺序 / 未知码回退 / 文案互异 / 英文语种 / 跨域守卫 / 表不可变） | 全绿 |
| **变异验证** | **7/7 全部变红**：回退改 `null`、码值 5002→5009、文案改中文、枚举重排、擅自加码、漏迁码、文案表漏一条 | 守门有效，**非假绿** |
| **编译** | `hot-common` / `hot-api` / `hot-rpc` 三模块 `test-compile` | 全通过 |

⚠️ 其中「**漏迁一个码**」是在**生产代码编译期**就失败（`HotErrorMessages` 引用了已删除的枚举常量），不是断言失败 —— 最强的一档。
⚠️ 本仓已有的反面样本：**`like` 域的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError`** —— 那份超建不要照抄。

#### ⚠️ 三处边界（不得越读成「hot 域已完成」）

1. **有意不迁的 4 个符号不是遗漏** —— grep 验证它们在 `service/hot` 全域**零调用点**、hot-rpc 对本文件**零引用** ⇒ **它们在 Go 侧本身就是死代码**，迁过来只会制造无调用方的结构。`BizCodeFromError` 的**判定顺序**已完整留档在 `FileTree/hot/common.md §12.3`；日后 hot 若接上 gRPC 消费方，**照该顺序**补 `HotGrpcError`（照 `MessageGrpcError` 的形状），不要临时另写一套。
2. **`CodeError.Error()` 的 `"ErrCode:%d, Errmsg:%s"` 未复刻** —— 已查实两侧可观察面：HTTP envelope **完全一致**（Go 是**逐字段复制** `codeErr.Code`/`codeErr.Msg`，不是 `Error()` 整串）；**仅**日志 `error_reason` 自由文本少一段冗余前缀，而码值在两侧都另有结构化 `error_code` 字段承载（Go `logger.go:152` / Java `Logger.java:277`）⇒ 语义信息不丢，属表达层，非 L1。
3. **消费侧尚未接线** —— 全仓目前**无任何代码**引用这两个类；hot-api 的 `GlobalExceptionHandler` / `GlobalResponseAdvice` 仍是空骨架（属 `service/hot/api` 另一个 Component）。接线等价式：`new BusinessException(HotErrorCode.X.getCode(), HotErrorMessages.getMsg(code))` ≡ Go 的 `hotcommon.NewErrCode(...)`。

⚠️ **`hot-common` 没有 `HUMAN_ONLY.MD`，且不应补** —— 全仓仅 4 份、全在 `service/users/` 下；`-common` 模块族（users-common / message-common / article-common / like-common）**从来没有过**这份文档。

**下一步（hot 域）**：① hot-api 的 advice 与 service 层落地时会首次真正调用本码表，届时**必须补一条 MVC 管线测试断言 envelope 的 code/msg**，否则「库侧对拍通过」仍不能证明 HTTP 边界等价；② hot-rpc 零引用本模块，**刻意不依赖**，改判时需同步其 pom。

### ✅ `service/hot/hot-rpc`：已从「13 个 12 行空骨架」落地，Go ↔ Java **双侧对拍逐字节一致**（2026-10-22 晚）

> **§0「从未验证」清单里 `hot` 一项的第二块收口**（第一块是 `hot-common`）。
> ⚠️ 本次是**首次实现**，不是「已存在实现的审计」—— 接手时该模块 13 个 `.java` **全部只有 12 行 javadoc**，
> `mvn compile` 却产出 `.class` ⇒ **「编译绿」与「零行实现」可以并存**（§6.6.5 方法论的又一实例）。
> 详细推导见 **`FileTree/hot/rpc.md`**；完整落地记录见 **§6.8**。

#### 🔴 落地前先撞上两个「编译期就暴露、但此前无人发现」的硬阻塞

| # | 阻塞 | 为什么一直没被发现 |
|---|---|---|
| 1 | **`hot-proto` 的 pom 没有 `protobuf-maven-plugin`** ⇒ `hot.proto` **从未被编译成 Java**，`HotServiceGrpc` 根本不存在 | 13 个骨架类全部没有 `extends …ImplBase`，所以「不引用生成类」就能编译通过 |
| 2 | **Spring Boot 4 把 Kafka 自动配置整体搬出了 `spring-boot-autoconfigure`**（实测该 jar 内 Kafka 类数 = **0**），改由独立模块 `spring-boot-kafka` 提供；只引 `spring-kafka` ⇒ **没有 `ConsumerFactory` Bean、没有 `@KafkaListener` 后置处理器** | 空骨架里 `@KafkaListener` 所在的 `HotEventConsumer` 是 12 行，注解根本不存在 |

⚠️ 第 2 条是**全仓级别**的：`service/like/like-rpc/pom.xml:120` 同样只引了 `spring-kafka`，
**有同款问题**（本次未动，不在 scope）。⇒ 见 **§3.3.4**。

#### 🔴 Go 真源本身有 3 个缺陷（**不是 RAG 的漂移**，是「照抄会出事」）

| # | Go 位置 | 缺陷 | 本次处置 |
|---|---|---|---|
| **G-HOT-001** | `hot_handler.go:132` `hkMu.Lock()` → `:136` `syncToRedis` → `:196` `hkMu.RLock()` | Go `sync.RWMutex` **不可重入** ⇒ **第一次 rebalance 撤销分区即必然自死锁**，`defer Unlock` 永不执行，`delete` 从不执行，**分片快照从未落盘过** | **有意偏离**：改用 Go 另外两个调用点已有的「锁内取引用 → 锁外 Redis I/O」形态，**需签字** |
| **G-HOT-002** | `hot_handler.go:370` `scoreMap[z.Member.(string)] += …` | **裸类型断言**，member 非字符串直接 panic；发生在 `periodicMerge` goroutine，**不在 sarama 的 recover 范围内 ⇒ 崩进程**（同文件 `:275` 与 `get_hot_articles_logic.go:55` 都有 `, ok` 保护，三处不一致） | **有意偏离**：改为跳过 |
| **G-HOT-003** | `hot_handler.go:311` / `:408` `result, _ := …Int()` | Redis **脚本执行失败**也被报成「lock lost」，排障指向错误方向 | **照抄**（§15 不擅自修复 Go 行为），已在注释与测试里留痕 |

⚠️ **G-HOT-001 是本轮最重要的发现，且是「1:1 直译会把它悄悄抹掉」的那一类**：
Java 的 `ReentrantReadWriteLock` **允许**写锁持有者再取读锁 ⇒ 按 `FileTree/hot/rpc.md §12.5` 的规划直译，
Go 的死锁会变成「能正常跑」，两套实现行为不一致且**零告警**。
⇒ 这条**不能靠 review 结构发现**，只能靠「先复现 Go 的行为」再决定怎么写。

#### 验证：4 层（口径：**隔离跑**，非全仓）

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `go run` 跑真源 `sea-try-go/service/hot/heavykeeper`（探针放 `/tmp`，**不入 Go 仓**）+ Java 探针同输入 | **逐字节一致**（含中文键 UTF-8、weight=0 早退、TopK 顺序与计数） |
| **契约测试** | **46 条** / 8 个类（`HeavyKeeperTest` 5 / `HeavyKeeperUint32OverflowTest` 4 / `HotEventConsumerDropTest` 12 / `HotPartitionSketchRegistryDeadlockTest` 6 / `HotRevokeFlushOutsideLockTest` 1 / `HotRankingQueryServiceTest` 9 / `InteractionStubServiceTest` 4 / `HotServiceGrpcImplErrorMappingTest` 5） | 全绿 |
| **变异验证** | **10/10 全部变红**：无符号比较（`maxCount` / 最小堆比较器 ×2）、指标 Inc 与 hkMap 查找次序、`UNKNOWN→INTERNAL`、去掉 TopK 归一、`scoreToCount→round`、指标名漂移、revoke 不移除分区、落盘挪回写锁内、坏消息不再返回 error | 守门有效，**非假绿** |
| **编译** | `hot-proto` / `hot-common` / `hot-rpc` / `hot-api` 四模块 `install` | BUILD SUCCESS |

⚠️ **变异验证第一轮抓到一个真实 bug**（不是测试问题）：
`HeavyKeeper.hash()` 写 `h & 0xFFFFFFFFFFFFFFFFL` 想做无符号掩码，但该字面量位模式全 1（即 `-1L`），
**掩码等于什么都没做** ⇒ FNV 结果最高位为 1 时取模得负数 ⇒ `ArrayIndexOutOfBounds`。
⚠️ **它是被「永不外抛」契约吞掉的**：消费者把异常降级成返回值 ⇒ 表现为**事件静默丢弃、热榜永不更新、启动零报错**。
⇒ 这是 §3.3 那条纪律的加强版：**「异常被降级处理」会同时遮蔽下游 bug**。

#### ⚠️ 四条边界（**不得越读成「hot 域已完成」**）

1. **无真 Redis / 真 Kafka 的集成验证** —— 4 段 Lua、两个 TTL、`RENAME` 原子性、`EXISTS` 守卫、RoundRobin 分配、零重试 handler 目前**只有代码级证据**。§6.8 #5。
2. **`/actuator/prometheus` 无暴露路径** —— 纯 gRPC 进程不起 web 容器（与 `user-rpc` 同款取舍 `management.server.port: -1`），而 Go 的 `prometheus.StartAgent` 在 19091/19092/19093 是**真起 HTTP** 的。L1 抓取端点暂缺。
3. **gRPC 端口未经分配** —— `Infrastructure.md §3` 只给 hot 预占 18897(HTTP)。本次暂沿用 Go 的 `8083`（8081=admin-rpc / 8082=message-rpc / 8083 空闲），走 `HOT_RPC_GRPC_PORT` 环境变量。
4. **`hot-api` 仍是 12 行空骨架**（13 个文件），`hot-rpc` 的事实**没有 HTTP 消费方**。

### ✅ `service/article/article-common`：Go ↔ Java 双侧对拍 **sha256 逐字节一致**（2026-10-22 晚）

> **§0「从未验证」清单里 `article` 一项的第一块收口**（`hot-common` 之后的第 3 个 `-common` 模块族先例）。
> Go 真源只有 1 个文件（`service/article/common/errmsg/errmsg.go`，**45 行、零 import**），
> 是纯值定义 + 纯函数库：**无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存**
> ⇒ **L3 技术语义在本组件上全部为空**，风险 100% 集中在 L1（码值 + 文案 + 回落）。

#### 落地了什么：1 个类 + 1 个契约测试类 + 1 个探针工具

| Go 来源 | Java 落点 | 关系 |
|---|---|---|
| 15 个 const（`errmsg.go:3-19`） | `article-common/errmsg/ErrorCode`（enum，**声明顺序 = Go 书写顺序**） | ADAPT |
| `codeMsg` 表（`:21-37`，15 条） | 同上枚举的 `msg` 字段（**与码值合并**） | ADAPT |
| `GetErrMsg`（`:39-45`），未知码回退 `"FAIL"` | `ErrorCode.getMsg(int)`，未知码回退 `"FAIL"` | ADAPT |
| — | `ErrorCode.of(int)` / 实例 `getMsg()` | **新增便利层**（无 Go 对应物，见下方边界 3） |

⚠️ **落点是 `article-common` 而非 `article-api` 的 `service` 包** —— 三条互相独立的依据：
`article-api` 的 pom **尚未**声明对本模块的依赖（消费侧仍是空骨架）、
`service/article/pom.xml` 已把 `article-common` 列为 `<module>`、
`users-common` / `message-common` / `like-common` / `hot-common` 四域口径统一。
⚠️ **类数（1）与落点是两个独立决定** —— 类数由「码与文案是否**同一个结构**」定：
Go 的 `const` 块与 `codeMsg` 是**两个**结构，但 article 的两侧**一一对应且无第三方码**，
合并成单枚举**不丢任何可观察行为**（合并可能丢东西这一点已被测试钉死，见下）。

⚠️ `article-api` 侧的**平行常量表已被前序会话废止** —— `article-api/service/ArticleErrorCodes.java`
现为 18 行「已废止，请勿在此实现常量」的说明文件，指向本模块。
⇒ **`article-common` 是 article 域码表的唯一真源**，不要在 api/rpc 侧另起一份。

#### 三条硬约束（错一条就是客户端可见的错码）

1. **未知码必须回退 `"FAIL"`，不是 `null` / 空串 / code 的字符串** —— Go `errmsg.go:39-45`
   `if !ok { return codeMsg[Error] }`。Go api 侧 `httpx.OkJson` 把 `Msg: errmsg.GetErrMsg(code)`
   直接写进 envelope ⇒ 回落值是**客户端可见**的。
2. **Go 侧 `const` 块与 `codeMsg` 表登记的必须是同一组码** —— Go api 层 handler 传的是 const、
   查的是 `codeMsg` 表；两者若不一致，Go 自己的信封 msg 就会意外回落到 `"FAIL"`。
   ⇒ 这条**只能在 Go 侧查**（Java 侧把两者合并了，看不见），已放进探针的 Go 自检里。
3. **article 与 users / like 共用同一段 int 值空间，且 article 的 15 个码**全部**能在 users 域找到同号**
   —— 其中 **6 个同号同义**（200/500/1015/5001/5002/5003，同源模板），
   **9 个同号不同义**（1001~1006、5004~5006）。⇒ 跨域推断含义是**高频误操作**，
   必须逐条钉死（见「跨域守卫」）。

#### 验证：4 层，且都不是「绿了就信」

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `tools/errmsg-probe/run-parity.sh`：Go 半边把**真源 `errmsg.go` 逐字节复制**进临时模块并**同包编译**（能读未导出的 `codeMsg`），Java 半边跑**真实编译产物 `ErrorCode`**；输出 CONST/TABLE/FALLBACK 三节共 **49 行** | **sha256 逐字节一致** `cad5cb4f…`（`EXIT=0`） |
| **契约测试** | **15 条**（码值 / 文案 / 顺序 / 唯一性 / 总数 / 未知码回落 / 往返 / `getCode` 产 int / **Go 双结构双射** / 文案两两互异 / 非空白 / 不可变性 / **9 条跨域差异** / 6 条跨域同义 / 不搬别域码） | 全绿 |
| **变异验证** | **8/8 全部变红，且「契约测试 + 双侧探针」**双层同时红**：回落改 `null` / 码值漂移 5002→5009 / 文案改英文 / 枚举重排（**编译期红**）/ 擅自加码 / 漏迁 1003（**测试编译期红**）/ 回落改空串 / `of()` 返回 `null` | 守门有效，**非假绿**；基线复跑双层均绿 |
| **编译** | `article-common` 单模块 `mvn -o test` | **15 tests, 0 failures**, BUILD SUCCESS |

⚠️ 其中「**漏迁一个码**」在**测试编译期**就失败（`GO_CONST_VALUES` 引用了已删除的枚举常量），
与 `hot-common` 同款 —— **最强的一档**。
⚠️ 「**枚举重排**」在**生产代码编译期**就失败（重复常量）—— 本仓已出现过反面样本：
`like` 域的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError`，那份超建不要照抄。

#### ⚠️ 本次补上的两个真实缺口（不是「重做一遍」）

1. **跨域守卫只钉了 5 条，漏了 4 条** —— 早前版本只守 `1005/1006/5004-5006`，
   而**真正同号不同义的是 9 条**：`1001`（文章已存在 vs 用户名已存在）、
   `1002`（文章不存在 vs 用户名或密码错误）、`1003`（文章处理中 vs 用户不存在）、
   `1004`（文章发布失败 vs TOKEN不存在）**这 4 条此前完全没守**。
   本次补齐 9 条，并**反向**补了 6 条「同号同义」的模板码
   —— 防的是「有人看到 5004 与别域不同就顺手统一成数据库插入失败」这种反向误操作。
2. **合并成单枚举的「没丢东西」此前无人证明** —— Go 是两个结构，Java 是一个。
   本次用**两份独立的裸 int 抄录**（const 块一份、`codeMsg` 键一份，刻意不复用同一字面量）
   断言「Go const 集合 ≡ Go codeMsg 键集合 ≡ Java 枚举码集合」。

#### ⚠️ 三条边界（**不得越读成「article 域已完成」**）

1. **消费侧尚未接线** —— `article-api`（29 个文件）与 `article-rpc`（41 个文件）
   **全部仍是 4 行空骨架**（`wc -l` 实测），本模块目前**零生产调用方**。
   接线等价式：`new BusinessException(ErrorCode.X.getCode(), ErrorCode.getMsg(code))`
   ≡ Go 的 `response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp}`。
2. **Go 的 rpc 侧只用码值、从不取文案** —— Go rpc 侧把 `errmsg.X` 当
   `logger.LogBusinessErr(ctx, code, err, …)` 的 **`error_code` 日志字段**用
   （`logger.go:145`），**该路径从不调用 `GetErrMsg`** ⇒ rpc 日志里**没有文案**，
   别误以为查表失效。Java 侧对称：`Logger.logBusinessErr(otelCtx, int code, Throwable, LogOption…)`
   只吃 int。⇒ 文案只在 **api 层的 HTTP 信封**里可见。
3. **`of(int)` 与实例 `getMsg()` 是 Java 侧的增量** —— Go 无对应物（Go 只有 `GetErrMsg`）。
   属于 §13 情况 D/E 的**新增便利层**，对齐 `users-common` 的既有约定
   （单枚举 + `of()` + `getMsg(int)`），不改变任何可观察行为。

⚠️ **`article-common` 没有 `HUMAN_ONLY.MD`，且不应补** —— 全仓仅 4 份、全在 `service/users/` 下；
`-common` 模块族（users-common / message-common / article-common / like-common / hot-common）
**从来没有过**这份文档。

⚠️ **方法论（比这个组件本身更重要）**：**任何纯 Java 侧的契约测试，对 Go 都只能靠「人工抄录」建立联系** ——
它能证明「改动 Java 时立刻变红」，但**不能**证明「Java 等于 Go」。
⇒ **锚必须是双侧探针**（§3.3「凡写『与 Go 一致』必须有两侧同时跑的探针」）。
本模块的分工是：**探针锚定真源，契约测试守住不回退** —— 与 §3.3 那条「绿测试守卫错误答案」互为镜像。

## 一、项目身份与双轨策略

RAG 仓库路径：`/Users/pr/Documents/java&golang/RAG`。

本项目对**已废弃商用架构** `Sea-RideTheWind_golang` 做 Spring Boot 重构。RAG 不是教学练手项目，而是商用架构的现代化迁移产物——按工作项目标准交付。

| 项目 | 路径 | 角色 | 实现风格 |
|---|---|---|---|
| **Sea-RideTheWind_golang** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | **真理之源（唯一真源，2026-10-20 第三次切换）** —— 商用架构的原始实现，2026-10-18 起冻结，所有行为的唯一判定标准 | go-zero + GORM |
| **RAG（本仓库）** | `/Users/pr/Documents/java&golang/RAG` | **Spring Boot 重构产物** —— 按工作项目标准把 Go 行为用 Spring 生态表达；非 Spring Boot 的 Java 写法（如 JPA + flat `internal/{logic,model,server,svc}`）不在重构范围内 | **MyBatis-Plus + domain/mapper/service 分层 + 幂等 DDL 脚本 + Bootstrap 抽出** |

**RAG 的存在范围**：用 Spring 生态表达 Go 的行为。**不**对任何"非 Spring Boot 的 Java 实现"做对照——那些是另一种工作，不属于本项目的范围；也不接收 RAG 反向同步。

### 1.1 三次真理之源切换的结论

| 时间 | 真源 | 结果 |
|---|---|---|
| 2026-10-18 前 | Go | 基线正确 |
| 2026-10-18 ~ 10-20 | Java 镜像 | 引入了一整层可漂移的中间副本 |
| **2026-10-20 起（本文件）** | **Go** | 回到唯一真源 |

**为什么退回 Go（2026-10-20 行为一致性审计的实测结论）**：

`service/users` 的 Go vs RAG 对照结果：

| 实现 | 缺陷数 | 说明 |
|---|---|---|
| **Go** | **0** | 冻结不动，每个行为有唯一确定答案 |
| **RAG** | **11（相对 Go 的回归）** | 全部集中在「把 Java 写法换成更地道的 Spring 写法」的位置 |

那 11 处回归：`CreateAdmin` 事务 self-invocation 失效、邀请码缺 `FOR UPDATE`、`extra_info` jsonb 写入丢失 typeHandler、`SelectAvatar` 响应 `is_current` 陈旧、`UpdateSelf` 空 email 清空列、uid 解析静默转 0、`success` 字段语义反转、`JwtUtil` 密钥补齐被删、HTTP 状态码语义变更、Flyway 双服务版本冲突、`@ResponseBodyAdvice` 二次包裹。

**教训**：多一跳的代价不是「多一次翻译」，而是**多一整层可以无声漂移的地方**。政策上的直接结论就是：**只留一个源**。

**2026-10-20 起两条线的分工**：

- **Sea-RideTheWind_golang**：**真理之源**。冻结，不再接收反向同步——一个不会移动的源才是最可靠的真源。
- **RAG**：Spring Boot 重构产物。行为以 Go 为准，表达以 Spring 为准。任何偏离 Go 的"更地道的 Spring 写法"都是潜在回归点（§3）。

## 二、HUMAN_ONLY.MD：设计目标，不是现状

每个非叶子子模块各有一份 `HUMAN_ONLY.MD`，包含两部分：

1. **文件阅读顺序与职责**：描述当前实现下每个文件的角色（这一部分反映现状）。
2. **Java 理论文件树**：描述目标架构——`domain/Entity + mapper/Mapper + service/DomainService + service/impl/GrpcImpl + bootstrap/* + resources/db/migration/*.sql` 等。

当前已落 HUMAN_ONLY.MD 的子模块（**2026-10-22 用 `glob **/HUMAN_ONLY.MD` 实测校正，全仓共 4 份**）：

- **service/users/user**：user-rpc / user-api
- **service/users/admin**：admin-rpc / admin-api

⚠️ **本节此前写的「共 7 份」是错的**（§7.2 的文件清单也同步错了）。以下 3 条**被登记为已存在、实际不存在**：
`service/users/users-common/HUMAN_ONLY.MD`、`service/message/message-rpc/HUMAN_ONLY.MD`、
`service/message/message-api/HUMAN_ONLY.MD`。
⇒ **「文件已写」同样是需要证据的断言**（§3.3 同款形态）：改这些清单前先跑一次 glob，别照抄上一版。

未覆盖的子模块：**users-common / message-rpc / message-api**（虽已实现并做过 Go ↔ RAG 对照，但**文档缺失**）
+ article / comment / favorite / follow / hot / like / points / security / task / common
（**连 Go ↔ RAG 对照都还没做过**，见 §0）。前者建议按 phase 补文档，后者暂按 Go 行为对齐、进入对应 phase 再补。

`HUMAN_ONLY.MD` 第 2 部分的"Java 理论文件树"是 aspirational design，从未被代码落地过。RAG 的本次重构就是把这个目标架构在 RAG 上落地一遍。

**2026-10-20 起取消「反向同步」**：真源（Go）不接受任何方向的写入或修正。此前允许 RAG 反向同步到 Java 镜像，等于让真源变成**双向可变**——这比单向漂移更危险，因为任何一侧都无法再被信任为判定标准。RAG 产出的是 Spring 表达经验（值得沉淀进 `HUMAN_ONLY.MD`），不是行为定义。

## 三、语义对齐纪律（Spring idiomatization 的唯一防线）

RAG 与 Go 的差异只允许出现在**表达层**。本节规定如何确保差异不越界。

### 3.1 Go ↔ RAG 对照（强制）

任何行为一致性审查必须**以 Go 为唯一基准**核对 RAG。Go 已冻结，行为漂移只可能从 RAG 一侧发生，因此 Go ↔ RAG 两方对照足以发现所有行为问题。

**审查方式**：

```
对每一个行为决策点，读 Go 对应位置的源文件 → 与 RAG 端对应实现逐字对照
```

**对照必须覆盖**（不限于）：
- proto 字段号、message 名称、错误码语义
- JWT claim 名称与必需字段（如 `userId`、`status` 的 presence 语义）
- 数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT
- 行级悲观锁与事务边界
- 字符串解析（uid、keyword）失败行为
- HTTP 路径与方法

**避免的对照陷阱**（历史教训）：
- **不要拿"现有 RAG 代码"当对照基准**——现存代码里就有 11 处回归，对照它会"对齐到错"
- **不要拿代码注释当对照基准**——现存注释里写的是 Java 镜像视角，Go 视角可能完全不同
- **不要"读两遍 Go 取并集"**——两遍都错的机会不等于零，必须有一遍回到原文件 line-by-line

> **历史**：2026-10-18 政策曾引入 Java 镜像作为第二对照面，但实测发现 Java 镜像本身带 2 处缺陷且与 Go 在多处一致地一起错，导致两方对照也漏掉 `admin-rpc` 直写 `users_user` 与 Flyway 拆分两类问题。**结论**：只留一个源、两方对照——而不是三方对照。

### 3.2 idiomatization 留痕（强制）

RAG 每次选择「用更地道的 Spring 写法」时，必须能回答：

> **这段比 Go 多做了什么？语义还一样吗？**

回答不了 = 无出处的 idiomatization = **不可 review，不得合入**。

落法：写入该类/方法的 javadoc，格式：

```java
/**
 * <p>L4 idiomatization：HandlerInterceptor 取代 Go 的 rest.WithJwt + CheckBlacklistMiddleware。
 * <p>Go 出处：service/user/user/api/internal/handler/routes.go（两段 AddRoutes，public 路径不带 JWT）。
 * <p>语义等价论证：public 路径集合逐条一致；blacklist key 前缀与 fail-open 行为一致；
 *   <b>HTTP 状态码差异</b>：Go 写 401，本实现走异常处理器 —— 见下方已知差异。
 */
```

**11 处历史回归全部属于「无出处 idiomatization」**，它们在注释里都写得很完整，唯独没有一处标注 Go 出处，导致 review 时无从判断"这是必要表达差异还是悄悄改了行为"。

### 3.3 框架机制必须验证实际生效（强制）

**3.3.1（2026-10-22 新增，全仓适用）「配置里没写」≠「没开」，也≠「没做」**

Go 侧大量行为的默认值**藏在 struct tag 里**而不是 yaml 里；反之，Java 侧大量行为**藏在框架里**
而不是注解里。只看配置文件/注解名称做双向推断，两头都会错。

**已实证的两组反向例子（`like-api`，见 §0 对应小节）：**

| 你会看到 | 真实情况 | 出处 |
|---|---|---|
| Go yaml 里**没有** `Timeout` | 实际 **3000ms**，struct tag `json:",default=3000"` 生效 ⇒ 3s 超时 503 真实存在 | `go-zero@v1.10.2/rest/config.go:55` |
| Go tag 是 `json:"target_ids"`，看着「没有 optional」 | 实际**解析失败**（`type mismatch`），不是「填空值放行」 | `core/mapping/unmarshaler.go:944-950` → `processFieldNotFromString` → `processFieldPrimitive` |

⇒ **强制要求**：
1. 判断某个 Go 默认行为是否生效，**必须读到消费该 tag 的那段控制流**，
   只贴 struct 定义行不算证据；
2. 判断某个 Java 框架能力是否生效，**必须有运行证据**（真实管线 / 真容器），
   「pom 里有依赖」「类上有注解」都不算（AGENTS.md §3.3 原有纪律）；
3. 写 `文件:行号` 证据时，行号要指向**消费点**而不是**声明点**。

**3.3.2（2026-10-22 新增）以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，本身是高危项**

`like-api` 的 DRIFT-LIKEAPI-002 里，两条绿测试的 `@DisplayName` 直接写着
「Go: Slice 永不计必填」，而这个 Go 结论后来被活体探针**推翻**。
⇒ 危害不是「测试没抓到 bug」，而是**修复时会先撞上这两条红测试**，
且测试名会让人误判「修复」才是漂移方向。
⇒ **硬要求**：任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，
否则**不得**用该措辞，改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。

#### 3.3.3 原有纪律（2026-10-20 起）

**不得凭注解名称断定机制已启用。** Spring Boot 的隐式机制（`@Transactional` AOP 代理、`@ResponseBodyAdvice`、`mybatis-plus` 的 typeHandler）全部是运行期动态装配，出错时**不报错**。

已实证的四类静默失效：

| 机制 | 静默失效形态 |
|---|---|
| `@Transactional` | self-invocation 不经代理 → 注解形同虚设，且**无任何异常** |
| `@ResponseBodyAdvice` | `HttpEntityMethodProcessor` 继承 `writeWithMessageConverters` 并查询 advice chain → 连 `@ExceptionHandler` 返回的 `ResponseEntity` 也会被二次包裹 |
| `@TableField(typeHandler=…)` | 只对 MP 自己生成的 SQL 生效；`LambdaUpdateWrapper.set()` 的参数走 `ew.paramNameValuePairs`，**丢掉字段类型信息** |
| **命令行注入的配置属性** | 前缀写错 ⇒ 落到**不存在的** `@ConfigurationProperties` ⇒ Spring Boot 对未知命令行属性**静默接受** |

**第四类的完整经过（2026-10-21，B-1 实验）**：为验证 `setQueryTimeout` 能否缓解 B-1，
`run.sh` 注入 `--mybatis.configuration.default-statement-timeout=2`。**前缀错了** ——
MyBatis-Plus 的前缀是 `mybatis-plus`（`MybatisPlusProperties`），全仓**没有**任何
`@ConfigurationProperties(prefix="mybatis")`。结果：启动无 warn、无报错、jar 正常起、
**PG 阻塞查询照旧存活 4.5s，效果恒为零**。改对前缀后同一实验立刻出效果。

> 绑定链路本身是通的、已逐段核实，**不要**误判成「MP 不支持该属性」：
> `--mybatis-plus.configuration.default-statement-timeout=2`
> → `MybatisPlusProperties$CoreConfiguration.defaultStatementTimeout`（`Integer`，有 getter+setter）
> → `MybatisPlusAutoConfiguration.applyConfiguration()` 调 `CoreConfiguration.applyTo(MybatisConfiguration)` 搬运
> → `MybatisConfiguration.defaultStatementTimeout`
> → MyBatis `BaseStatementHandler` 调 `statement.setQueryTimeout(int)`
> → pgjdbc `StatementCancelTimerTask` → 向 PG 发 **CancelRequest**。
> 另外 `user-rpc/application.yaml` 里 `mybatis-plus.configuration.map-underscore-to-camel-case`
> 一直生效（否则实体映射全崩），本身就是「`configuration` 子块绑定正常」的现成旁证。

**规则**：凡引入新的框架机制，必须配一条**能真正触发该机制**的测试。
纯 Mockito mock 数据层的单测**不算**——它不会执行 SQL，也不会装配 Spring 上下文，天然测不到本节三类问题。

**规则（2026-10-21 补）**：判断「一个注入/开关有没有真生效」**不能看启动日志**——
本节第四类里，注入点的 echo 照打、jar 照起、日志零告警，效果却是零。
唯一可靠的判据是**运行时证据**（本例是 PG 侧是否出现 `57014 query_canceled`）。
凡是「注入后实测无变化」的结论，一律先怀疑注入本身没到位，再怀疑机制无效。

**规则（2026-10-22 补 · 由「E-1 收口抓到 text/plain 400 丢 Content-Type」推出）**：
**「代码里写了」与「客户端收到」之间隔着一次 commit。**

`GlobalExceptionHandler.goStylePlainTextBadRequest` 里明明写着
`headers.setContentType(TEXT_PLAIN)` + `set("X-Content-Type-Options","nosniff")`，
而 `ObservabilityResponseWriter.setHeader` 只把 header 收进 `capturedHeaders`、
等 `flushTo` 再写回 underlying。真实 Tomcat 下 `flushTo` 发生在 `chain.doFilter()`
**之后**——此时下游 `outputMessage.flush()` 已让底层 committed，
`dst.setHeader(...)` 被 **静默忽略**（不抛异常、不打日志）。

实测（2026-10-22 curl，两端同请求）：

| | Go | RAG 修复前 |
|---|---|---|
| 状态行 | `400 Bad Request` | `400 `（reason phrase 都没有） |
| Content-Type | `text/plain; charset=utf-8` | **缺失** |
| X-Content-Type-Options | `nosniff` | 缺失 |
| 传输 | `Content-Length: 25` | `Transfer-Encoding: chunked` + `Connection: close` |

**它此前 4 轮对拍全绿**，因为 difftest **只比 body 不比响应头**，
而 body 恰好是对的（DRIFT-006 对齐的正是正文）。缺陷就藏在从未被比对的那一层。

⇒ **两条推论**：
1. **响应头必须纳入对拍**。body 对齐 ≠ 响应对齐；`Content-Type` 决定客户端怎么解析 body，
   缺了它等于把「这是 JSON 还是纯文本」的判断权交给客户端默认值。
2. **这类缺陷 MockMvc 测不到**。实测 `MockHttpServletResponse` 在这个时序上与 Tomcat
   不同：MockMvc 里 Content-Type 完好（`text/plain;charset=UTF-8` + `nosniff`），
   真实栈 curl 是空的。⇒ 守门只能用**真实容器**（`tools/difftest/check-plaintext400-headers.sh`）。
   这是 §3.4.2「N-1 的二次坍塌」的同款陷阱：**层次没对齐时，单独那一层全绿就够骗过 CI。**

**修复**：`setHeader` / `addHeader` / `setContentType` 改为**实时 propagate** 到 underlying，
与 `setStatus` / `sendError`（2026-10-21 修的那两个）同一策略。
`capturedHeaders` 保留作兜底二次写入 —— Tomcat 的 `setHeader` 是**覆盖**语义，重复写不产生重复 header。

> ⚠️ **`HttpServletResponseWrapper` 默认的 `setContentType` 是直接透传的**，
> 本类此前未 override 它，于是同一份响应里存在「`setContentType` 直通 / `setHeader` 进 buffer」
> **两套语义**。已显式接管 `setContentType`，让三者走同一条路径。
> 下次再动这个 wrapper，注意它有三个覆写点，不是一个。

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

**规则（2026-10-21 再补 · 由 H2 假绿事件推出）**：**断言「Bean 存在」不等于断言「机制生效」。**

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

**规则（2026-10-21 再补 · 由「46 处批量插入」推出）**：**凡是「脚本批量改 N 处」，
必须配一条覆盖全部 N 处的守恒断言，而不是逐点行为测试。**

issue #71187874 用脚本往两个 DomainService 插了 46 处
`catch (StatusRuntimeException e) { throw e; }`（user-rpc 17 + admin-rpc 29）。
逐点行为测试只覆盖到 8 个方法，**其余 38 处当时只有「代码写进去了」这一层保证** ——
而「代码存在」正是上一条规则里那个 H2 假绿的形态。

补法：新增两个**结构性元测试**，用 Spring 自带的 `org.springframework.asm`
读 class 文件的异常表，断言每个 `catch(Exception)` handler **前邻**都有一条
`catch(StatusRuntimeException)`：

| 用例 | 覆盖 | 断言 |
|---|---|---|
| `UserDomainServiceCatchStructureTest` | 18 处 catch(Exception) / 18 处放行 | 前邻断言 + 守恒计数 |
| `AdminDomainServiceCatchStructureTest` | 29 处 / 29 处 | 同构副本 |

**两条断言必须成对**：只有前邻断言时，删掉一个 `catch(Exception)` 会让总数"看起来对得上"；
只有计数断言时，新增一处不写放行分支也看不出来。变异测试已实证两者都能变红
（删掉 `login` 的放行分支后，前邻断言报「catch 序列 = [NotFoundException, Exception]」、
计数断言 `expected: 18 but was: 17`）。

⚠️ 扫描必须用**字节码**而非源码：javac 生成的异常表里，同一个 try 的多个 catch 共享
`(from, to)` 且顺序即源码顺序，这才使「前邻」可判定；源码扫描依赖缩进与文本形态。

**规则（2026-10-22 补 · 由 message-api 的 `,string` 漂移推出）**：
**绿测试可能是「错误答案的守卫」，比没有测试更危险。**

起因：`service/message/message-api` 首轮实现把 Go 的 `,string` int64 请求字段做成了
「**只接受 JSON 字符串**」，而 Go 侧 `json.Number` 的 `reflect.Kind()` 是 `string`
（`type Number string`），所以 Go **同时接受字符串与数字**。
本项目这边两处都「有据可依」地写错了：

1. DTO javadoc 断言「`json.Number` 的 Kind 是 number」—— 事实错误（有完整推导，不是笔误）；
2. `MessageApiMvcPipelineTest#int64AsJsonNumberIsRejected` 把 **RAG 的实际输出**断言成了
   「与 Go 一致」—— 这条测试会**主动阻止**正确修复。

⇒ **测试断言的是「RAG 现在的行为」，不是「Go 的行为」。**
当测试名 / `@DisplayName` 里出现「对齐 Go」「与 Go 一致」，而断言写的是 RAG 的输出时，
它要么是**巧合**，要么是**在给未验证的假设发通行证**。

**强制三条**：
1. 论证里的每个 Go 事实都必须带 `文件:行号`，**不许只写结论**；
2. 凡写「与 Go 一致」，必须有**两侧同时跑的探针**（不是只跑 RAG）——
   最小形态：Go 侧直接调用真源的解析包，RAG 侧用项目**真实的** mapper（不是自己 new 一个）；
3. 修复漂移时**先改/删错误断言，再改实现** —— 否则会先撞上「测试红了」而误以为自己改错了。

⚠️ **「只跑 RAG 的探针」同样不算数**：它证明的只是「代码写成了这样」，
证明不了「Go 是这样」。§6.6.5 的变异测试是同一问题的另一半 ——
变异测试能证明测试**不是假绿**，但**不能证明测试的期望值是对的**。两者缺一不可。

**规则（2026-10-22 晚补 · 由 `hot-rpc` 的 Spring Boot 4 模块化迁移推出）**：
**「注解存在」≠「机制生效」，且 Boot 4 让这一层比以往更难发现。**

Spring Boot 4 把 Kafka 自动配置**整体搬出了 `spring-boot-autoconfigure`**
（实测 `spring-boot-autoconfigure-4.0.8.jar` 内 Kafka 相关类数量 = **0**），
改由独立模块 **`org.springframework.boot:spring-boot-kafka`** 提供，包名也从
`org.springframework.boot.autoconfigure.kafka.*` 移到 `org.springframework.boot.kafka.autoconfigure.*`。

⇒ 只引 `org.springframework.kafka:spring-kafka` 时：
**没有 `ConsumerFactory` Bean、没有 `KafkaListenerAnnotationBeanPostProcessor`**
⇒ `@KafkaListener` 被**完全静默忽略**：不报错、上下文照常启动、容器正常起来、**一行消息都不消费**。

这比 §3.3 已有的 H2（`@Component` ≠ 自动注册，表现为 Bean 不存在）**更难发现**：
H2 至少还有「类存在但 Bean 不存在」这条线索；本条是**连类都不在 classpath 上**，
而源码里那个 `@KafkaListener` 看起来完全正常。

**强制**：
1. 凡用 `@KafkaListener`，必须有一条**装配层**测试断言 listener **真的进了容器的 registry**
   （不是「listener 方法存在」），且须覆盖「缺少 `spring-boot-kafka` 会怎样」；
2. 引入任何 `spring-*-starter` 类框架时，先确认该能力在 **Boot 4 下仍在 `spring-boot-autoconfigure` 里**，
   已被拆走的必须显式补对应模块（版本由 BOM 管理，不写 `<version>`）；
3. **不得**把「编译通过 / 上下文能启动」当作「注解已生效」的证据。

⚠️ 同款缺依赖已确认存在于 `service/like/like-rpc/pom.xml:120`（只引 `spring-kafka`）——**尚未修**。
明细见 §6.8。

### 3.4 已知偏离（有意为之，需留档）

以下偏离经过确认并记录，审查时不必重复质疑，但也不得"顺手修正回旧行为"：

| 偏离 | 内容 | 理由 |
|---|---|---|
| HTTP path | `/user/v1/*`、`/admin/v1/*`（Go 为 `/usercenter/v1/user/*`、`/admincenter/v1/admin/*`） | HUMAN_ONLY.MD 决策；路由不属于行为语义，但属 wire 契约变更 |
| HTTP method | 统一 POST（Go 为 GET/DELETE 混合） | 同上 |
| 表名 | `users_user` / `admin_admin` | HUMAN_ONLY.MD 决策。**⚠️ 2026-10-21 补全 Go 侧真实表名**（原行只写「Go 为 GORM 默认名」，太笼统，写脚本时容易照抄成同名 —— 见下） |

**表名全映射（2026-10-21 取证自 Go 真源 `service/user/admin/rpc/internal/model/data_model.go`）**：

| 实体 | Go `TableName()` | RAG |
|---|---|---|
| User | `users` | `users_user` |
| Admin | **`admins`** | `admin_admin` |
| AdminInvite | **`admin_invites`** | `admin_invite` |
| UserAvatarHistory | `user_avatar_history` | `users_user_avatar_history` |

⚠️ **admin 侧是两端都改名，不是一端改**。扩展超时对拍时我按「admin_admin 两侧同名」写锁脚本，
`go_db` 立刻报 `relation "admins" does not exist` —— 而 `LOCK` 报错会让**整个事务 abort**，
连同一进程里已经拿到的 `users` 表锁也一起丢掉，于是 Go/user 在 363ms 就登录成功、
**看着像"RAG 超时更慢"的假偏差**。最终修法：每个 (库, 表) 用独立 psql 进程 + 加锁前
`to_regclass()` 校验存在，否则直接 `exit 1`。**教训：造阻塞的脚本必须先证明"锁真的加上了"。**

#### 3.4.2 `service/users` 语义审计后的两条新增留档（2026-10-21）

Go → Spring Boot 语义一致性审计 `service/users/user`（9 RPC + 9 HTTP 端点 + `users-common`）产出
**3 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 6 项 UNRESOLVED**，已修 11 处 + 新增 22 条回归测试。
以下两条**是修复后仍然存在的有意偏离**：

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-1** | **鉴权失败的响应体形状** | Go 侧两处 401 的 body **互不相同**：`rest.WithJwt` → `authhandler.go:106 unauthorized()` 写 **HTTP 401 + 空 body**；`CheckBlacklistMiddleware` → `checkblacklistmiddleware.go:38` 写 **HTTP 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（无 `data`）。RAG 两侧统一成 **HTTP 401 + 完整 `{code,msg,data}`** | **用户 2026-10-21 拍板**。HTTP **状态码 401 已与 Go 对齐**（这是本次修掉的 SEMANTIC MISMATCH）；body 统一属于表达层收敛——Go 自身两处不一致，RAG 不复制这种不一致。业务码仍是 `ERROR_TOKEN_RUNTIME(1006)` | 若要求逐字复刻 Go 的两种 body，则需在拦截器里区分「JWT 验签失败」与「黑名单命中」两条路径分别写 body |
| **N-2** | **Go 真源的中文错误消息乱码** | Go `user/rpc/internal/logic/` 下 `uploadavatarlogic.go` / `selectavatarlogic.go` / `getavatarhistorylogic.go` 共 **9 处** `status.Error(...)` 的中文 message 是 **GBK 双重编码乱码**（`鐢ㄦ埛涓嶅瓨鍦?`，尾字节已丢、还原不出「用户不存在」）；而同模块 `loginlogic.go` / `registerlogic.go` / `getuserlogic.go` / `updateuserlogic.go` / `deleteuserlogic.go` 是正常 UTF-8。RAG 写的是正确中文 | 取证：`"鐢熸垚澶辫触".encode('gbk').decode('utf-8') == "生成失败"`。Go 已冻结（§2 不接受写入），无法在真源侧修；照抄乱码会让日志与 gRPC status message 对人类不可读。**用户 2026-10-21 拍板：RAG 保留正确中文**，沿用 §3.4.1 A-2（`Logger.isFiltered`）的既有先例。影响面仅 gRPC status message——Go api 层只按 `status.Code` 分支、不透传 message，**不进 HTTP body** | 若日后在 Go 侧修正编码，需同步 RAG 的 9 条 message |

> **这两条的共同教训**：N-1 的修复前状态是「HTTP 200 + 业务码 1006」，而支撑它的注释写着
> 「401 只由鉴权层的 Servlet Filter 写出，不经过本处理器」——**本工程根本没有这样的 Filter**，
> 鉴权是 `HandlerInterceptor` 抛异常、必然经过该处理器。
> 这是一条**引用了不存在的机制**来论证 idiomatization 的典型，
> 与 §3.2「无出处 idiomatization」同类，但更隐蔽：它有出处、只是出处是假的。
> 新的防线是 `AuthenticationException`：**层次由类型承载，不靠业务码推断**
> （1006 在鉴权层与 Service 层都会出现，按码分流必然搅在一起）。
> 锁定用例：`user-api` 的 `AuthenticationHttpStatusTest`（3 条，含「同码不同型 → 不同状态码」）。

> **N-1 的二次坍塌（2026-10-21 补）**：§6.1 第 10 项「HTTP 状态码语义变更」修了
> 「`GlobalExceptionHandler` 拿不到 401」这一层。但还有一层被跳过：
> **`HttpMiddleware$ObservabilityResponseWriter.setStatus` 的 status 没 propagate 到
> `MockHttpServletResponse`**——下游 `outputMessage.flush()` 触发 `flushBuffer()` 把底层
> `setCommitted(true)`，再走 `HttpMiddleware.flushTo` 的 `dst.setStatus(401)` 就被守卫吞掉。
> **`AuthenticationHttpStatusTest` 用的是 `standaloneSetup`**，不挂 `HttpMiddleware` filter；
> 这就是它能在 user-api 写完时全绿、而 admin-api 实测却出 HTTP 200 的根因——两层一起错时
> 单独那一层全绿就够骗过 CI。直到 `AdminApiMvcPipelineTest`（真 MVC + 真 filter）才把它们联起来。
> 锁定用例（覆盖本批发现的 DRIFT-FRAMEWORK-001）：`admin-api` 的 `AdminApiMvcPipelineTest`
> 4 条 401 路径用例（`blacklistCheckedBeforeSignatureVerification` /
> `invalidSignatureAfterBlacklistMiss` / `missingHeaderSkipsBlacklistLookup` / `emptyBearerTokenRejected`）。
> 修复方法：`HttpMiddleware$ObservabilityResponseWriter.setStatus` / `sendError`
> 立即 propagate 到 underlying（用 `(HttpServletResponse) this.original` 绕过
> javac 对 inner class `getResponse()` 的回退到 `ServletResponse` 的怪题），
> 见 §6.2.2 DRIFT-FRAMEWORK-001 行。

#### 3.4.3 `service/users/admin` 语义审计后的三条新增留档（2026-10-21，issue #71186443）

Go → Spring Boot 语义一致性审计 `service/users/admin`（13 RPC + 13 HTTP 端点）产出
**10 项 SEMANTIC MISMATCH + 8 项 OBSERVABLE DIFFERENCE + 3 项 UNRESOLVED**。
**9 项 MISMATCH + 2 项 wire 契约 + 1 项共享底层 bug（DRIFT-FRAMEWORK-001）已修**，
新增 **47 条回归测试**
（`admin-rpc` 的 `AdminSemanticDriftRegressionTest` 12 条 + `admin-api` 的
`AdminSemanticDriftApiRegressionTest` 24 条 + `AdminApiMvcPipelineTest` 11 条 ——
**首条把真实 MVC 管线 + 真实 Redis 容器 + 真实拦截器 advice 链纳入回归网**，
同时锁住 DRIFT-FRAMEWORK-001 / DRIFT-009 / N-1）。
以下四条是本节记录过的条目。**注意：其中 N-3 与 N-6 已于 2026-10-21（DRIFT-NEW-016 审计期间）
被实测证伪并降级** —— 它们描述的 RAG 行为从未存在过，RAG 本来就与 Go 一致。
**当前真正成立的「有意偏离」只有 N-4、N-5 两条**。
保留 N-3 / N-6 两行是为了让后人看到「签字 + 防回退测试都齐了，结论仍可能是错的」。

| # | 偏离 | 内容 | 理由 | 关闭条件 |
|---|---|---|---|---|
| **N-3** | ~~**`uid=0` 被错误归类为「业务不存在」**~~ **⚠️ 已证伪，本条不是偏离（2026-10-21 DRIFT-016 审计期间）** | 留档曾写「RAG 在 `AdminApiSupport.parseUid` **提前拦截 uid=0** → 1017」。**代码里没有这个拦截**：`parseUid` 只拒 null / 空串 / 非数字 / 溢出，全仓 grep `uid == 0` 在 admin-api 侧**零命中**。实测 RAG 真实行为：`uid=0` → **透传到 rpc** → 查无此人 → `1003`，**与 Go 逐字相同** | **本条降级为「留档与代码不符，已修正」**，不是有意偏离。Go 侧三态（探针实测）：不传 uid → `400 field "uid" is not set`；`uid=abc` → `400 strconv.ParseInt`；`uid=0` → `200 {code:1003}`。**RAG 在这三条上本来就与 Go 一致** ⇒ 无需签字、无需改动行为 | 已闭合。防回退测试 `AdminApiMvcPipelineTest#uidZeroIsPassedThroughAndMatchesGo`（断言 uid=0 **确实被透传**，即证明「没有 1017 拦截」） |
| **N-4** | **`operator_id` 不恒为 0** | Go 的 `updateuser` / `banuser` / `unbanuser` / `delete` / `resetuserpassword` 读 `ctx["uid"]`，而 `rest.WithJwt` 写入 ctx 的 key 是 JWT claim 名 **`userId`** ⇒ 永远取不到 ⇒ `OperatorId` 恒为 **0**。RAG 五个端点都传真实 uid | **用户 2026-10-21 拍板：保留真实 uid**。该字段**不参与任何业务判定**，只写进 OTel span 的 `audit.operator_id` 属性；恒 0 会让审计日志失去操作人信息。影响面 = 可观测性数据，不是业务行为 | 若要求审计 span 与 Go 逐字一致，需在这 5 个 Service 里刻意丢弃 token 中的 uid |
| **N-5** | **JWT HMAC 密钥长度** | Go 用 15 字节原始 secret 参与 HS256；RAG 用 `JwtUtil.padToHmacKeySize` zero-pad 到 32 字节 | jjwt 0.12 强制 key ≥256 bit。RAG 内部签发与验签共用同一份 padded key、自洽；代价是 **Go 签的 token 在 RAG 验不过，反之亦然**（两套部署各自闭环）。与 `user-api` 同款 | 若需跨部署互认 token，必须统一 key 长度——要么改 Go 侧 secret 到 ≥32 字节，要么 RAG 放弃 padding（jjwt 会抛 `WeakKeyException`） |
| **N-6** | ~~**uid 格式非法时 body 形状 ≠ envelope**~~ **⚠️ 已证伪，本条不是偏离（2026-10-21 DRIFT-016 审计期间）** | 留档曾写「`uid=abc` 经过 `parseUid` 拦截 → `200 + 1017` envelope」。**代码里到不了 `parseUid`**：`GetUserReq.uid` 类型是 `Long`，admin-api **无自定义 ObjectMapper**（grep 零命中），Jackson 在反序列化阶段就抛 `InvalidFormatException` → Spring 包成 `HttpMessageNotReadableException` → `handleUnreadableBody` → `400 + text/plain`。Go 侧 `uid=abc` **同样**是 `400 + text/plain` | **本条降级为「留档与代码不符，已修正」**，不是有意偏离。**状态码本就一致**，只有错误**文本**来自不同框架（Jackson vs go-zero）—— 与 §3.4.4 DRIFT-006 已确立的「形状对齐、文本不对齐」边界同款 | 已闭合。防回退测试 `AdminApiMvcPipelineTest#nonNumericUidIsRejectedByJacksonNotByRequiredCheck`（同时断言错误文本**不是**我们自己的 `is not set`，以证明来源是 Jackson） |

| **N-7** | **`like-rpc` 列表接口的 DB 回源：Go 恒空，Java 返回真实数据** | `GetUserLikeList` / `GetTargetLikerList` 在 Redis 未命中后的 DB 回源路径上，Go 因 **GORM 列名不匹配**而恒返回空列表：Go 结构体字段是 `CreateTime int64`（无 column tag，GORM 命名规则推出列名 `create_time`），而查询 `Select("id, target_type, target_id, created_at")` 取的是 `created_at` ⇒ **该列映射不上、被丢弃、`CreateTime` 恒为 0** ⇒ `if timestamp == 0 { continue }` 对每一行都成立 ⇒ `list` 恒空、`nextCursor` 恒 0；但 `isEnd` 仍按 `len(dbResults) > limit` 置 `false`，`ExpireCtx` 仍会执行 ⇒ 返回 `isEnd=false` + `nextCursor=0` 的**自相矛盾态**。**连带**：Redis ZSet 的 ZADD 只在回源成功后执行 ⇒ 该路径**从功能上是死的** ⇒ Go 侧冷缓存下用户点赞列表 / 作品点赞人列表实质不可用，且客户端会反复请求第一页。Java 侧 `LikeRecordMapper.java:121,139-147` 显式 `SELECT ... created_at` 映射到 `OffsetDateTime`，`LikeDomainService.java:439-466,539-563` 取到真实 Unix 秒 ⇒ 返回真实分页数据 | **用户 2026-10-22 拍板：Java 现状即为「已修正的 bug」，两侧不一致属有意，不再对齐。** 理由：Go 的空列表是**缺陷不是设计**（GORM 命名规则不匹配，可自行验证），照搬它等于让 Java 复刻一个让功能不可用的 bug。⚠️ **代价必须同时记住**：灰度期两端同跑时 **Java 会返回 Go 永远返回不了的数据**，调用方若按 Go 的行为（恒空）做过降级或兜底，切换时会出现行为跳变。**实证**：真 PG + gorm v1.31.1 复刻 Go 结构体，`schema.LookUpField("created_at")` 返回 `nil`（`CreateTime` 的 `DBName` 实为 `create_time`）；插入真实行后 `Scan` 得 `CreateTime=0`（期望 `1792670400`） | Go 侧补 `column:created_at` tag 后即可撤销本条签字。⚠️ **在此之前任何人不得以「必须对齐 Go」为由把 Java 改回空列表** —— 那是把一个已确认的缺陷搬过来。另：Go 侧该功能仍不可用，**Go 分支的冷缓存路径需在灰度方案里单独说明** |

##### ⚠️ N-3 勘误（2026-10-21，commit 690ba4c 之后）

留档初版（commit `9b2303c`）写「Go admin 侧的 uid 走 `form:"uid,string"` 绑定，**key 缺失时静默保留零值**且不报错 ⇒ `uid=0` 进 rpc ⇒ 查无此人 ⇒ 返回 1003」。**前半段理由已被实测推翻**：

- go-zero `httpx.Parse` 对 `form:"uid,string"` 的 required 缺失行为是**直接 400**（`core/mapping/unmarshaler.go` 的 `parseOption` 把所有非 `optional` 字段标 `Required=true`，缺失走 `newInitError` 报 `field %q is not set`），不是「静默保留零值」。
- 实测覆盖：`admin.getuser` 三态（完全不传 → `400 field "uid" is not set` / `uid=abc` → `400 strconv.ParseInt` / `uid=0` → `200 {code:1003}`），取证脚本 `tools/difftest/probe-go-required-fields.py`（page_size=100 三组各 3 次，可复现）。

**结论本身不变**（保留 RAG 的 1017），但理由改为「Go 把 `uid=0` 当合法查询，业务不存在语义掩盖参数拼装 bug」。边界也明确收窄到 `uid=0` 这一路径 —— 缺失/非数字/空串的覆盖在 N-6 + DRIFT-016（实施后两边统一 400+text/plain）。**N-3 与 N-6 不再是「同源」**，N-3 单独覆盖「显式 0 被业务路径吃掉」这一边界。

> **这四条的共同教训**：与 N-1/N-2 不同，N-3/N-4/N-5/N-6 都被归入「Go 的行为本身有缺陷」
> 而非「Go 表达得更地道」。真源冻结（§2）意味着 Go 的缺陷也会被冻结，于是产生了
> 「按政策应当对齐、但对齐会让 RAG 变差」的真实张力。
> **新的处置纪律**：这类项不得由单个 agent 自行判断，必须显式列出、用户签字、写进本节，
> 并用**防回退测试**锁住现状 —— 否则下一个「语义一致性审计」agent 会把它当成新发现的漂移再「修」一遍。
>
> **⚠️ 2026-10-21 补一条更硬的纪律（DRIFT-NEW-016 审计的产物）**：
> 上面那套「签字 + 防回退测试」**并不足以保证结论正确**。
> **N-3 与 N-6 两条都经过了签字、都有防回退测试、javadoc 出处写得很完整，
> 而它们描述的行为从未存在过。** 见 §6.2.5「DRIFT-NEW-016 的处置」。
> 真正的防线是：**任何留档声称「RAG 的行为是 X」时，都必须有一条断言 X 的运行测试；
> 若该测试不存在或从未跑过，它只是推测，不得当作事实引用。**
> 区分「代码里写了什么」与「运行时真的发生什么」——
> 前者 grep 得到，后者只有真进程能证明。
>
> **同一条纪律的反面也必须台账化**：**证据缺口本身要写下来**。
> 否则下一个 agent 跑完 `tools/difftest` 看到「39/39 全绿」就以为已经验证完了 ——
> 而 39 步里有 **13 步只比状态码不比 body**，且完全没有**超时 / 并发 / gRPC 层**的对拍。
> 完整台账见 **§6.2.6「`service/users` 的证据边界台账」**（E-1 ~ E-6）。

#### 3.4.4 `service/users` 基础组件审计后的新增留档（2026-10-21，issue #71186893）

对 `service/users` 9 个**基础组件**（JWT 签发 / 错误码表 / 密码哈希 / 身份断言 / 鉴权链 / 响应信封 / 雪花 ID / 事务持久化 / RPC 客户端）做 Go → Spring Boot 语义一致性验证（只读审查），
结论 **SEMANTICALLY NOT EQUIVALENT**：1 项方向性错误 + 1 项**安全漏洞** + 3 项可观察差异 + 1 项死代码。

##### DRIFT-018 的登记（2026-10-21 补记 · 代码里早已存在、AGENTS.md 零命中）

`UserDomainService` 的 `checkDeadline()` 上方注释写着「**DRIFT-018 修复**」，
`RpcInterceptor.handle()` 里也写着同一条注释，但 **AGENTS.md 全文 grep `DRIFT-018` 零命中**
—— 一个被代码引用了、却从没有留档的编号。本条补上它的登记。

| 项 | 内容 |
|---|---|
| **做了什么** | 两条路径各绑一次：`RpcInterceptor.handle()`（主动调用的路径）在提交 worker 时 `DeadlineState.bindToCurrent(deadlineEpochMs)`；gRPC server 路径在 `newUnaryServerInterceptor` 的 `onHalfClose()` 里绑定（那里才是真正跑 handler 的线程）。业务侧在**发起** DB / bcrypt / snowflake / 下游调用**之前**调 `checkDeadline()`，已过期则抛 `Status.DEADLINE_EXCEEDED` |
| **Go 对应** | `context.WithTimeout(ctx, timeout)` 的 `ctx.Done()`：每个 Logic 入口 / 关键步骤前的 select |
| **覆盖情况（2026-10-21 实测）** | `UserDomainService` **11 处**（login/register/update/selectavatar/uploadavatar 等主路径）；`AdminDomainService` 此前 **0 处**，本轮已补到 **13 处**（admin-api 对 admin-rpc 的全部业务调用） |
| **⚠️⚠️ 此前从未真正生效** | **这 24 处守卫在 gRPC server 路径上一直是通过死代码在跑**：`onHalfClose()` 从未被调用（gRPC `ServerInterceptor` 压根没挂到 server 上，根因见 §6.4 的 H2 勘误），handler 线程上 `DeadlineState` 恒未设置 ⇒ `checkDeadline()` 的条件恒为 false，**一次都没有拦下过任何请求**。2026-10-21 补 `@Bean GrpcServerConfigurer` 后才第一次真正可达 |
| **回归网** | `AdminDomainServiceDeadlineTest`（2 条，13 方法逐个拦 + 反向放行）。**但它只能证明「分支逻辑对」，不能证明「生产路径会走到」** —— 这一点已被本轮实测打脸：守卫全绿，而 `onHalfClose` 从未执行。见下 |
| **⚠️ 它不能做什么** | **不能取消在途 IO**。JDBC 驱动不响应线程 interrupt，这是 Java 侧的结构性限制，属 §3.4.1 B-1，**不是 DRIFT-018 能修的**。B-1 由 `DeadlineQueryTimeoutInjector` 的 `setQueryTimeout` 关闭（已实施并端到端验证：查询滞留 4303ms → **176ms**，与 Go 的 182ms 同量级） |

> **为什么单独记这一条**：它说明了 §6.2.6 E-4 那类缺口的典型形态 ——
> **「框架限制」与「业务侧主动响应」是两件不同的事**，前者改不了，后者能做但容易漏做。
> B-1 原留档把两者混成一句「当前 user-rpc 一处都未使用」，既高估了缺失（其实 11 处）、
> 又让人误以为补齐 `checkDeadline` 就能关闭 B-1（不能）。
>
> **⚠️ 本条留档自身也曾踩了 §3.4.4 那条纪律（2026-10-21 补记）**：
> 「回归网已实证能真红」是**真的**（删掉守卫确实会红），但它证明的只是
> **「给定 `DeadlineState` 已过期，业务分支会走对」**，
> 而**没有**证明「`DeadlineState` 真的会在生产请求里被设上」。
> 这是「单测红 ≠ 生产可达」的又一例，与 `ObservabilityWiringTest` 的假绿同源。
> **⇒ 补一条判据**：对「超时 / 取消 / deadline」这类**由框架注入的状态**，
> 必须有一条**端到端**测试证明它真的被注入；只测「拿到该状态之后的行为」不够。

> **为什么单独记这一条**：它说明了 §6.2.6 E-4 那类缺口的典型形态 ——
> **「框架限制」与「业务侧主动响应」是两件不同的事**，前者改不了，后者能做但容易漏做。
> B-1 原留档把两者混成一句「当前 user-rpc 一处都未使用」，既高估了缺失（其实 11 处）、
> 又让人误以为补齐 `checkDeadline` 就能关闭 B-1（不能）。

##### 已修（4 项，全部带能真正触发机制的回归测试）

| ID | 内容 | Go 行为 | RAG 修复前 | 修复后 |
|---|---|---|---|---|
| **DRIFT-009** | **鉴权链顺序反了**（SEMANTIC MISMATCH） | `Authorize(JWT)` → `CheckBlacklist` | `CheckBlacklist` → `Authorize` | 改回验签优先 |
| **DRIFT-010** | **注销绕过**（安全，本批新发现） | `stripBearerPrefixFromTokenString` 前缀不匹配时**原样返回整串** → 验签必失败 | `split(" ", 2).parts[1]` 取到 `"eyJ..."` **验签通过**，黑名单又用整串查不到 | 按 Go 逐字重写；`uid <= 0` 检查移出拦截器，交给 `IdentityResolver` |
| **DRIFT-002** | 前缀语义 | 前缀**大小写不敏感**；缺失前缀**不报错**、原样当 token | `startsWith("Bearer ")` 大小写敏感 → 小写 `bearer` 被误拒 | 与 admin 侧共用 `BearerTokenExtractor` |
| **DRIFT-006** | 400 响应形状 | `http.Error(w, msg, 400)` → `text/plain; charset=utf-8` + 裸文本 + 尾换行 + `nosniff` | `ResponseEntity.status(400).build()` → **空 body** | `goStylePlainTextBadRequest` + advice 对非 JSON 透传 |
| **DRIFT-024** | **text/plain 400 的响应头丢失**（2026-10-22 E-1 收口时发现，**DRIFT-006 的另一半从未真正对齐**） | 每次 400 都带 `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff` + `Content-Length` | **`Content-Type` 与 `nosniff` 全部缺失**，状态行连 reason phrase 都没有，传输退化为 `Transfer-Encoding: chunked` + `Connection: close` | `HttpMiddleware$ObservabilityResponseWriter.setHeader` 只收进 `capturedHeaders`、等 `flushTo` 才写回 underlying，而那时底层已 committed，Tomcat **静默忽略**。已改为三个覆写点（`setHeader`/`addHeader`/`setContentType`）实时 propagate，与 `setStatus` 同一策略。守门：`tools/difftest/check-plaintext400-headers.sh`（**必须真实 Tomcat**） |

> **DRIFT-009 的病灶形态与 §3.4.2 N-1 完全同构，但方向相反，值得单独记住**：
> N-1 是「引用了不存在的机制」，DRIFT-009 是「**引用了真实文件、但把 chain 的内外关系读反了**」。
> 原 javadoc 写「WithJwt 追加到切片尾部 ⇒ 黑名单是外层、先执行」——
> `Append` 到尾部确实是 chain 里最内层，但**黑名单根本不在 chain 里**：
> `rest/server.go:222-233` `WithMiddleware` 在**构造期**就把 `route.Handler` 包成
> `middleware(route.Handler)`，它是 `ThenFunc` 的**参数**，比 chain 任何元素都靠内。
> **假出处比无出处更贵**：无出处时 review 会去查；假出处会让 review 直接采信一个方向相反的结论。
>
> 更严重的是它还**同时锁死了测试**：`AdminApiMvcPipelineTest#blacklistCheckedBeforeSignatureVerification`
> 与 `AdminSemanticDriftApiRegressionTest#interceptor_checksBlacklistBeforeSignatureVerification`
> 两个用例名都把错误顺序写成了规格。**评审纪律的推论：测试名里出现「先后顺序」时，
> 必须独立回 Go 源码复核一次，不能因为「已有测试锁住」就默认它对。**

> **DRIFT-010 的教训是「安全规则的第二份实现本身就是缺陷来源」**。
> 同样的前缀提取逻辑当时散在 admin（`split(" ", 2)`）与 user（`startsWith("Bearer ")`）两处，
> 各错各的。已抽到 `users-common` 的 `BearerTokenExtractor`，两个模块共用一份，
> 且**刻意保留 Go 内部两套互不相同的取法**（验签用大小写不敏感的 strip，
> 黑名单用大小写敏感的 `TrimPrefix`）—— 合并成一个方法等于擅自修好 Go 的 bug，
> 那会制造一处新的语义漂移。

##### 不改代码、只留档（3 项）

| ID | 内容 | 理由 |
|---|---|---|
| **DRIFT-003** | Go 的 `errmsg.codeMsg`（`errmsg.go:38-68`）**缺 1017 / 1018** ⇒ `GetErrMsg(1017)==GetErrMsg(1018)=="FAIL"`；RAG 的 `ErrorCode` 补了真实中文 | 当前潜伏：Go 从不返回这两个码；RAG 侧 1018 **只作日志码**（`UserDomainService.java:550,607`，线上抛的仍是 `Status.NOT_FOUND`）。**但若 N-3 日后改成对齐 Go，`GetErrMsg(1017)` 会变成 `"FAIL"`，届时 RAG 的提示语必须同步回退。下一个 agent 不得把它当成新发现的漂移再「修」一遍** |
| **DRIFT-005** | Snowflake workerId 优先级：Go = `env → hash`（`snowflake.go:52-85`），RAG = `env → properties → hash`（多一层配置面）；截断 Go = `uint16(id)` 后 `%1024`，RAG 直接 `%1024` | RAG 是配置超集；Go 的截断对 `>65535` 的值是缺陷。两者都不可观察，不改代码 |
| **DRIFT-004** | ~~`cryptx.CheckPassword` 对任何 bcrypt 错误返回 false，RAG 抛 `IllegalArgumentException` ⇒ `500 + 5001`~~ | ✅ **2026-10-21 已修（用户签字「对外对齐 Go，但保留可观测性」）** | 见下方「DRIFT-004 的处置」小节。**触发面比原留档窄得多**：实测 Spring Security 7.0.7 下只有 **cost 字段越界**（`$2a$99$…`）会抛，空串 / 非 bcrypt / 截断这些形态 Spring 本就返回 false、与 Go 已一致 |

##### 本批新增的测试（11 条）

| 模块 | 测试类 | 条数 | 关键点 |
|---|---|---|---|
| admin-api | `AdminApiMvcPipelineTest` | 13 → 17 | 改用 `@MockitoSpyBean` 包真 Redis，使「黑名单**一次都没查**」成为可断言的顺序证据 |
| admin-api | `AdminSemanticDriftApiRegressionTest` | 24 → 26 | 新增注销绕过 + 大小写不敏感两条 |
| user-api | `UserApiMvcPipelineTest` | 9 → 14 | 新增顺序 / 绕过 / 大小写 / 黑名单 key / 400 text/plain 五条 |

> **`@MockitoSpyBean` 而非纯 mock Redis 的理由**：纯 mock 只能证明「代码调用了 `hasKey`」，
> 证明不了「它一次都没调用」—— 而 DRIFT-009 的语义**恰恰是后者**。
> 旧的 `blacklistCheckedBeforeSignatureVerification` 用「Redis 里有这条 key」当顺序判据，
> 那条断言在任何实现下都成立，属于**假绿**。

#### 3.4.5 `service/users` **DTO 边界矩阵**产出的差异：R1~R4 已修、R5 新发现（2026-10-21 起，§6.2.6 **E-3**）

按 DTO 逐字段展开边界组合（字段全缺 / 部分缺 / 类型错 / 超长 / 特殊字符 / 越界），
Go 与 RAG 双端对拍 **273 条**。首次产出 **77 条 NEW_DRIFT**，收敛到 **4 个根因**，
**UNCLASSIFIED = 0**（连续三次运行结果逐字一致）。
**2026-10-22：R1~R4 全部已修并经端到端复测（NEW_DRIFT 77 → 0）；
修复过程中又暴露第 5 条根因 R5（曾被分类器误归入 R3）。** 复现：

```bash
./tools/difftest/run.sh --keep --serve-only   # 起两套栈（**必须完整构建**，见下方警告）
python3 tools/difftest/matrix.py             # 运行时半（273 条）
python3 tools/difftest/check-ddl-static.py   # 静态半（R3 的 DDL 证据）
python3 tools/difftest/r5-order-probe.py     # R5 专项（用**真邀请码**补齐矩阵盲区）
```

> ⚠️ **不要用 `--no-build`**：该参数**不会把新 jar 拷到 `$RUN_DIR/bin/`**，
> 起的是旧副本 —— 表现为「修复没生效」，是本项目反复误判的头号来源。
>
> ⚠️ **不要在被矩阵跑过的 cluster 上取证**：`admin.update` 的边界用例会真的改写
> `admin/admin` 的口令，跑过一轮后两侧登录均失败（`1002`）。取证一律用
> `run.sh` 重建的**全新 cluster**。
>
> **本节 5 条均已由用户逐条签字并有防回退测试**。按 §3.4.3 末尾那条纪律 ——
> 签字 + 防回退测试**并不足以保证结论正确**（N-3 / N-6 都签了字、都有防回退测试，
> 而行为从未存在过）—— 每条下面都只写**实测到的**行为，并附**独立于矩阵**的取证命令。

| # | 根因 | 实测现象 | Go 出处 | RAG 修复前 | 状态 |
|---|---|---|---|---|---|
| **R1** | **JSON 标量被 Jackson 静默强转 / 截断**（64 条） | `{"username":99999}`、`{"password":true}`：Go `400`；RAG `200`+业务码。`{"page":1.5}`：Go `400`；RAG **按 page=1 执行** | Go 走 `encoding/json` 的类型检查 / go-zero 的 `processNamedFieldWithValueFromString`（先字符串化再 `strconv.ParseInt`） | 字段是 Java `String` / `Long`，Jackson 默认允许 number→String、bool→String，并**向零截断** float→Long | | ✅ **已修**：新增 `GoStyleCoercion`（Jackson 3）+ 两端 `WebMvcConfig` 注册 `JsonMapperBuilderCustomizer`；锁死用例 `GoStyleParseSemanticsMvcTest`（14 条，真 MVC）
| **R2** | **显式 `null` ≠ 字段缺席**（4 条） | `{"extra_info":null}`：Go `400 field "extra_info" mustn't be nil`；**缺席该字段**则 Go `200`。RAG 两种都 `200` | go-zero `core/mapping/unmarshaler.go:856-862`：`mapValue == nil` 且非 `optional` ⇒ `mustn't be nil`，**对 map / slice 一样生效**；而「缺席」走 `processNamedFieldWithoutValue`，Map 分支不报错 | `RegisterReq` / `CreateAdminReq` / `UpdateSelfReq` / `UpdateUserReq` 四处 javadoc 都写着「Extrainfo 是 map ⇒ 不校验」，把 null 与缺席**混为一谈** | | ✅ **已修**：4 个 DTO 加 `extraInfoPresent` 标志 + `@JsonProperty` setter，`GoStyleRequired.explicitNull` 判「显式 null」；锁死用例同上
| **R3** | **DDL 列宽：`text` vs `varchar(255)`**（4 条） | 5000 字符 `username`：Go 写入成功；RAG `5001`/`5003`。反噬读路径：Go 库里有、RAG 存不下 ⇒ 登录时 Go 命中、RAG `1002`/`1003` | Go 的 GORM `AutoMigrate` 给 Go `string` 生成 **`text`**（无上限） | 4 份 `users-common/.../db/*.sql` 的注释自陈「列结构与**原 JPA `@Column`** 注解严格对齐」⇒ `VARCHAR(255)`。**全量 DDL 对比：35 列里 22 列类型不同**（12 列 text→varchar(255)、8 列 timestamptz→timestamp、2 列 text→jsonb） | | ✅ **已修**（用户签字）：4 份 `db/*.sql` 改 `TEXT` + 各补幂等 `ALTER TABLE ... TYPE text` 自愈；锁死用例 `UserRpcSchemaTextColumnTest`（4 条，真 PG）
| **R4** | **分页越界：GORM 静默抑制 vs RAG 直传 PG**（5 条） | `page=0` / `page=-1` / `page_size=-5` / `page=9223372036854775807`：Go 全 `200`+`code 200`；RAG 全 `5001` | `service/user/admin/rpc/internal/model/admin.go:159-160`：`Offset(int(offset)).Limit(int(pageSize))` —— GORM 只在 `offset>0` / `limit>=0` 时才生成子句，负值**静默忽略**并返回首页全量 | 负数原样交给 PG ⇒ `22023: OFFSET must not be negative` ⇒ `5001` | | ✅ **已修**：`getUserList` 改 `long`、新增 `goStylePagination` 逐字镜像 GORM `clause/limit.go` 抑制规则；锁死用例 `AdminRpcPaginationGormSemanticsTest`（7 条，真 PG）
| **R5** | **bcrypt 哈希抢在 invite / username 检查之前**（2 条；**曾被分类器误归入 R3**） | 见下方「R5 专节」实测表 | `createadminlogic.go` 事务闭包：`FindAdminInviteByCodeForUpdate` → `FindOneAdminByUsernameTx` → `cryptx.PasswordEncrypt` → `GetID` → insert | 事务外先 `PasswordUtil.encrypt`，72 字节上限抢跑 ⇒ 该报 1016/1001 的报成 5001 | ✅ **已修**（用户签字「完全对齐 Go」）：哈希移进事务、排在 W2 之后；锁死用例 `AdminCreatePasswordOrderRegressionTest`（9 条，真 PG，变异测试实证 6 条真红） |

> **R3 的条数依赖环境状态，不是稳定的 4**：写侧那 4 条（5001/5003）任何时候都在；
> 读路径另有 2 条（`user/admin.login.Username.long5000` 报 1002/1003）**只在 Go 库里
> 已经存在一条超长用户名行时**才出现 —— 那是**上一轮矩阵自己写进去的**。
> 本节基线 **4 条**取自 `./run.sh` 刚重建的全新 cluster（`down -v` 后 `serve-only`）；
> 在被矩阵跑过的库上重跑会变成 **6 条**。**根因分类两处完全一致，不要按总数判断 R3 变了。**

**R1 附一条反直觉的实测**（写下来免得下次又按 `,string` 标签想当然）：
Go 的 `json:"uid,string"` **不等于「只收字符串」**。go-zero 先把 JSON 值字符串化再
`strconv.ParseInt`，所以 `{"uid":1}`（数字）**两端都接受**（实测均 `1003`），
真正的分叉只在浮点。取证：`curl -d '{"uid":1.5}'` → Go `400 strconv.ParseInt: parsing "1.5"` / RAG `200 + 1003`，
且 RAG 的 `{"uid":1.5}` 与 `{"uid":1}` 响应逐字相同（截断）。
`page` 侧同款：`{"page":1.5}` 与 `{"page":1}` 响应逐字相同、`{"page":2.9}` 与 `{"page":2}` 相同。

**R1 的 `float→Long` 截断比 `→String` 强转更危险**：前者让请求**成功执行但作用在错误的行上**
（`banuser {"uid":1.5}` 等价于封禁 `uid=1`），后者只是把错的值存进去。

##### R1 修复踩的三个坑（Jackson **3**，不是 2）

1. 本工程用的是 **Jackson 3**（`tools.jackson.core:jackson-databind`），API 位置与 2.x 不同：
   `JsonMapper.builder()` / `MapperBuilder`，不是 `ObjectMapper`。
2. **`withCoercionConfig(Class, …)` 对全局规则无效**：实测 3.1.5 下完全不起作用，
   必须用 `LogicalType.Textual` 的 `CoercionInputShape` / `CoercionAction` 逐项配置。
   `GoStyleCoercion` 类注释里已写死这条，**防止下一个人重试**。
3. 规则只能**全局生效**：每端在 `WebMvcConfig` 注册一个 `JsonMapperBuilderCustomizer` bean。

**R1 有一条只对 json 通道成立**：Go 走 **form 通道**的字段值本就是字符串，
所以 `GetUserListReq.setKeyword(Object)` 需要**自己标量化**
（只接受 String/Number/Boolean，其余抛 `IllegalArgumentException`）——
全项目仅此一个字段走 form 通道的 `Object` 入参。
⚠️ 第一版曾用 `String.valueOf` 把 `{"a":1}` 变成 `"{a=1}"`，等于把「拒」变成了「静默接受」，已修。

##### R5 专节：矩阵为什么把它误分类成了 R3，以及真实分歧面（2026-10-22）

R3 修完后矩阵仍报 `R3 = 1`，一度以为 R3 没修干净。**实际是分类器的锅**：
`matrix.py` 的 `classify()` 有一条宽规则「`long5000` 且 RAG 报 5001/5003 ⇒ R3（列宽）」，
它把这条**顺序问题**顺手归进了 R3。

**真实分歧面比矩阵看到的大一倍**——因为 `matrix.py` 在 `admin.create` 这一族里
**恒定使用假邀请码** `MATRIX-INVITE-NOT-A-REAL-CODE`，只能命中「邀请码校验失败」
这条最早返回的分支，**永远走不到**「邀请码有效 → 后续检查」那一段。

新增 `tools/difftest/r5-order-probe.py` 用**真邀请码**补齐，实测（全新 cluster，两端真实 HTTP）：

| 支 | 场景 | Go | RAG 修复前 | RAG 修复后 |
|---|---|---|---|---|
| A | 有效邀请码 + 超长密码 + **新**用户名 | `5001` | `5001` | `5001`（本就一致） |
| **B** | 有效邀请码 + 超长密码 + **用户名已存在** | `1001`「用户名已存在」 | `5001` ★ | `1001` ✅ |
| **C** | 无效邀请码 + 超长密码 | `1016`「邀请码错误」 | `5001` ★ | `1016` ✅ |

**根因**：两端 bcrypt 实现**相同**（Go `bcrypt.DefaultCost`=10 / RAG `STRENGTH`=10，
都拒 >72 字节），分歧**纯粹来自步骤顺序**。
交集 = 「密码 >72 字节」且「本该先被前置检查拦下的输入」。

**修复（用户 2026-10-22 签字「完全对齐 Go」）**：bcrypt 从 `AdminDomainService` 的事务外
移进 `AdminCreateAtomicService`，排在 **W2 查 username 之后**。
Go 步骤序（`createadminlogic.go` 事务闭包）逐条镜像：
`FindAdminInviteByCodeForUpdate` → `FindOneAdminByUsernameTx` → `PasswordEncrypt` → `GetID` → insert。

> ⚠️ **本次撤销了一条「无出处 idiomatization」**：原注释写着
> 「bcrypt password（事务外，**避免延长事务持有时间**）」——这是一条**从未对照 Go 出处**
> 的优化（§3.2 的典型形态：注释写得很有道理，唯独没有出处）。
> 代价已知并接受：cost=10 约 60~100ms，会延长 invite 行 `SELECT ... FOR UPDATE`
> 的持锁时间；但该锁**只锁 invite 单行**。
> **`Snowflake.nextId()` 仍留在事务外**：它不触碰 invite 锁、不参与错误码归属，
> 与 Go 放在哈希之后相比无可观测差异（Go 侧 `GetID` 失败同样是 Internal）。

**回归网**：`AdminCreatePasswordOrderRegressionTest`（9 条，**真 PG**）。
变异测试已实证 **6 条能真红**——把哈希挪回 W1 之前，
B 支报 `expected ALREADY_EXISTS but was INTERNAL`、C 支报 `expected PERMISSION_DENIED but was INTERNAL`，
与端到端实测的漂移形态逐字一致。

**连带改动（不是回归，是结构变更的必然结果）**：
`AdminDomainService` 的 `catch(Exception)` 由 **29 → 28**（bcrypt 那处移进了
`AdminCreateAtomicService`）。`AdminDomainServiceCatchStructureTest` 的守恒期望值已同步，
并在注释里写明「**不要改回 29，那意味着有人又把 bcrypt 搬回了事务外**」。

> ⚠️ **`matrix.py` 的 `classify()` 那条 R3 宽规则仍有误分类风险**：
> `long5000` + 5001 的形态既可能是列宽，也可能是顺序。已由独立探针覆盖，
> 但**不要把「矩阵 R3 = 0」当作「列宽问题全部消失」的充分证据**。

> **R3 修复的副产品**：`check-ddl-static.py` 的列类型差异从 **22 处降到 12 处**
> （10 个 `varchar(255)` 全部变 `text`）。剩下 10 处 `timestamptz→timestamp` + 2 处 `text→jsonb`
> 属**范围外**（本轮只修列宽，不动时间精度与 jsonb 归属）。

> **E-3 同时暴露了一个「此前没人查」的层**：§3.1 的对照清单里写着
> 「数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT」，但**此前没有任何脚本在查这一层**
> （`check-proto-static.sh` 只比 proto 契约）。R3 就是从这个缺口里漏出来的 ——
> 运行时只看到「5001」，顺着查才落到 DDL。现补 `tools/difftest/check-ddl-static.py`，
> 但它**只查列类型与长度**；索引 / 唯一约束 / NOT NULL / DEFAULT 仍是 **NOT CHECKED**，
> 不得把该脚本的结论读成「DDL 已一致」。

#### 3.4.1 `service/common` 的 Java 端差异（2026-10-21 逐行审计产出）

Go ↔ RAG 对照审计 `service/common` 全部 8 个 Go 非测试源文件与 RAG 全部主源码，产出 20 项偏差、修复 21 处（分支 `row/common-fix-semantic-drift`，issue #71186341）。**以下 5 条是修复后仍然存在、且无法靠改代码消除的差异**。

**分类原则**：「有意偏离」= 主动选择与 Go 不同（须签字，不得顺手改回）；「框架限制」= 想对齐但语言/框架做不到；「表述差异」= 语义已对齐、仅字面不同。三者性质不同，review 时不可混谈。

##### A. 有意偏离（需签字）

| # | 位置 | 差异 | Go 行为 | Java 行为 | 理由 | 关闭条件 |
|---|---|---|---|---|---|---|
| A-1 | `Logger.doLogFatal` / `LoggerFactoryBean.logFatal` | **等价域收窄** | `logger.go:293-296` 输出 `fatal_error` 后无条件 `os.Exit(1)`，**永远终止** | 抛 `FatalError`，**仅在无人 catch 时**终止 | 用户 2026-10-21 拍板：`System.exit` 会绕过 Spring graceful shutdown 与 `@PreDestroy` | 若业务出现 `catch (FatalError)` 后继续运行的写法，等价性即被打破 |
| A-2 | `Logger.isFiltered` / `LoggerFactoryBean.isFiltered` | **`call_chain` 内容比 Go 少框架帧** | `logger.go:53-58` 的 `github.com/zeromicro/go-zero/` 前缀**本身是死的**（`getFuncName` 同样剥掉包路径），只有 `runtime.`/`testing.` 生效 | 按该列表的**意图**执行，6 条前缀全部可命中 | Go 侧是 bug；Java 照抄会让「过滤」永远不生效。已在 javadoc 留档 | 若要求逐字复刻 Go 的**实际行为**（即不过滤），需回退本项 |

##### B. 框架限制导致的已知不等价（**不是**有意偏离，无法靠改 `service/common` 消除）

| # | 位置 | 差异 | 影响面 | 关闭条件 |
|---|---|---|---|---|
| B-1 | `HttpMiddleware` 超时分支 / `RpcInterceptor` 超时调度 | **超时无法取消在途 IO**。Go 的 `context.WithTimeout` 到点 ctx Done，所有 ctx-aware IO（`http.Client` / `sql.DB` / `grpc.Dial`）立即返回、连接池立刻释放；Java 只能 `Future.cancel(true)` 发 `InterruptedException`，而 **JDBC 驱动、grpc-java 阻塞 stub、`java.net.http.HttpClient` 均不响应线程 interrupt** —— 阻塞在 socket read 上的调用会挂到查询自行结束 | **✅ 已实测坐实并已修复（E-4 + issue #71187874，2026-10-21）**。**修前**：`exp-b1-timeout-cancel.sh` 用 ACCESS EXCLUSIVE 表锁造真阻塞、两侧同跑 `user.login`，客户端返回后 **Go 侧 PG 查询 203ms 消失、RAG 侧还存活 4657ms**。**修后**：`DeadlineQueryTimeoutInjector` 按 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 发 CancelRequest，**RAG 侧查询 176ms 消失，与 Go 的 182ms 同量级**；响应体也从 `text/plain "Request Timeout"` 变成 Go 的 `{"code":1015,"msg":"服务繁忙"}`。完整三层证据（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」 | **✅ 已关闭**（本行原写「无法靠改 `service/common` 关闭」，该结论**已过时**）。① **字节码证据**：链路 MyBatis-Plus → MyBatis → Spring JDBC → pgjdbc → socket，逐层 `javap -c` 数 `isInterrupted` 命中**均为 0** —— interrupt 在整条数据访问路径上无人检查。② **行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：主动 interrupt 一条等锁查询，它**照常正常返回、耗时 11014ms**（比不 interrupt 还长 1s）。③ **修复路径**：拦 `StatementHandler.prepare` 注入 timeout —— 与 Go `database/sql` 的 ctx-aware 取消是**同一条服务端语义且连接保可用**；`socketTimeout` 会让连接报废，语义相反，不可用。⚠️ 修复过程中还暴露更大的前置缺陷：gRPC `ServerInterceptor` 从未挂到 server 上，`DeadlineState` 恒未设置 ⇒ **DRIFT-018 的 24 处守卫此前全部是死代码**，见 §6.4 的 H2 勘误 |

##### B-1 补记：机制实验与缓解手段定性（2026-10-21 同日同场三组对照）

B-1 上方的时长数字取自首轮实验。本轮把它拆成三层各测一遍，**结论有实质修正**，此前数字一并更正。

**第一层 —— 纯 JDBC 机制层（`tools/difftest/probe-jdbc-cancel.java`，绕开 HTTP/gRPC/MyBatis）**
ACCESS EXCLUSIVE 表锁持 10s，三组对照：

| mode | 结果 | 耗时 |
|---|---|---|
| `none` | ✅ 正常返回（阻塞到锁释放） | 10022ms |
| `qto:2` | ❌ **`SQLState=57014`** `canceling statement due to user request` | **2034ms** |
| `interrupt` | ✅ 正常返回 —— **interrupt 零效果** | 11014ms |

⇒ interrupt 组比 none 组还慢 1s（interrupt 就发生在 1000ms 处），说明 interrupt **连让查询稍早返回都没做到**。B-1 由此从「字节码推断」升级为「行为实测」。

**第二层 —— 同日同场端到端对照（`exp-b1-timeout-cancel.sh`，21:03 一场连跑基线与注入两组）**

| | 客户端返回 | 响应体 | PG 查询总寿命 | 客户端返回后又存活 |
|---|---|---|---|---|
| **Go**（基线） | 2190ms | `{"code":1015,"msg":"服务繁忙"}` | ~2.2s | 203ms |
| **RAG 基线** | 3174ms | `text/plain "Request Timeout"` | **~7.4s** | **4657ms** |
| **RAG 注入 `default-statement-timeout=2`** | 2449ms | `{"code":5001,"msg":"系统内部错误"}` | ~2.06s | 193ms |

⇒ 注入组 RAG 与 Go 已同量级（worker `business_error` 落在请求发起 +2.06s，与设定值吻合）。
基线组的 RAG 查询 `pid=113 query_start=21:03:15.203` 一直活到 `21:03:22.600`，而 worker `business_error` 在 `21:03:22.722` —— **worker 是在查询真正结束后才退出的**，比客户端返回（21:03:18.382）晚了 4.34s。

> **⚠️ 本次差点踩的坑：把日志里相邻两行当成同一次请求。**
> 首轮观测出现「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后才结束」的「三方矛盾」，
> 追了很久。真相是：`business_info` 那一行属于 **[2/4] 阶段的注册请求**，
> `business_error` 才是本轮 login 的 —— 中间隔着整段 Go 探测，**根本不是同一次调用**。
> **纪律**：任何靠日志两行之差推断耗时的读数，必须先确认两行属于**同一次请求**
> （本例靠 worker 侧 span 名 / 请求内容区分），否则该读数不可用。
> 这与 N-3/N-6「留档声称的行为从未存在过」同类：**先证伪读数，再谈结论**。

**第三层 —— 缓解手段定性**

| 手段 | 实现 | 超时后果 | 与 Go 是否等价 |
|---|---|---|---|
| **`Statement.setQueryTimeout`** | pgjdbc 起 `StatementCancelTimerTask` → 发 **CancelRequest** | 查询停止，**连接保持可用** | ✅ 等价 Go 的 ctx cancel |
| `socketTimeout` | `PGStream.setSocketTimeout` → `Socket.setSoTimeout` | socket 读超时 → **连接报废** | ❌ 语义相反，会污染连接池 |

⇒ **只能用 `setQueryTimeout`，不能用 `socketTimeout`。**

##### B-1 补记（续）：生产方案已实施（2026-10-21，issue #71187874）

上述实验结论已落地为代码，**两半缺一不可**：

| # | 组件 | 位置 | 职责 |
|---|---|---|---|
| ① | `DeadlineQueryTimeoutInjector` | `service/common` `persistence/` | 拦 `StatementHandler.prepare`，按 `DeadlineState.remainingMillis()` 设 `setQueryTimeout(ceil)`；**无 deadline 时完全不动** |
| ② | `SqlTimeoutStatusTranslator` | 同上 | 拦 `Executor.query/update`，把 `SQLState=57014` 翻成 `Status.DEADLINE_EXCEEDED` |
| ③ | 46 处 `catch (StatusRuntimeException e) { throw e; }` | `UserDomainService` 17 + `AdminDomainService` 29 | 让 ①② 的产物、以及 `checkDeadline()` 自己抛的 DEADLINE_EXCEEDED，**不被 `catch (Exception e)` 吞成 INTERNAL** |

**api 层零改动**：`DEADLINE_EXCEEDED` 落到 `mapRPCStatusToCode` 的 default 分支 → 1015，与 Go 一致。

> ⚠️ ③ 顺带修掉一个既有缺陷：`checkDeadline()` 抛的正是 `StatusRuntimeException(DEADLINE_EXCEEDED)`，
> 而它被写在 `try` 块内 ⇒ 此前会被同方法的 `catch (Exception e)` 吞成 INTERNAL。
> **即 DRIFT-018 那 24 处守卫里，落在 try 内部的部分从未生效过。**

**端到端结果（修复前 → 修复后，对照 Go）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |
| difftest 39 步 | 39/39 | **39/39，unexpected=0** | — |

**⚠️ 但实施过程先撞上一个更大的前置缺陷**：③ 全部补齐、①② 全部就位后，
端到端**仍然零效果**（3318ms / `text/plain` / 查询滞留 4378ms）。诊断日志显示：
`newUnaryServerInterceptor` 确实收到 `timeout=PT2S`，可 `interceptCall` 与 `onHalfClose`
**从未被调用**，`DeadlineState.remainingMillis()` 恒为 `Long.MAX_VALUE`。

**根因不在 B-1，而是 gRPC `ServerInterceptor` 从未真正挂到 server 上** ——
`@Import` 只保证 Bean 存在，starter 3.1.0 不自动注册。详见 **§6.4 的 H2 勘误小节**。
⇒ 这条链路上**任何**依赖 gRPC 超时 / deadline 传播的机制此前都是死的，
包括 §3.4.4 登记的 DRIFT-018（24 处 `checkDeadline()`）——**它们一次都没真正生效过**。

**新增回归网（32 条）**：
`DeadlineQueryTimeoutInjectorTest`（7）+ `SqlTimeoutStatusTranslatorTest`（8）+
`DeadlinePassthroughTest`（user-rpc 7）+ `AdminDeadlinePassthroughTest`（admin-rpc 8）+
`GrpcInterceptorWiringTest`（2，防 H2 假绿复发）+ 两个结构性元测试（6）。
全仓 383 → **421 条，0 failures**。

> **实现期踩到的三个坑（均已写进代码注释）**
> ① `Invocation.proceed()` 声明抛的是 `InvocationTargetException`（反射包装），**不是** `SQLException`
>    —— 写 `catch (SQLException)` 编译通过但**永远匹配不上**，翻译器静默失效。
> ② MyBatis 3.5.19 的 `Invocation` 构造器校验 `method.getDeclaringClass()` ∈ 内部白名单，
>    测试里自造同名方法会报 `"is not supported as a plugin target"`；必须用 `mock(接口)`。
> ③ `LambdaUpdateWrapper` 在无 Spring 上下文时缺 MP 的 TableInfo 缓存，抛
>    `MybatisPlusException: can not find lambda cache` —— 它**不是** StatusRuntimeException，
>    于是被**正确地**吞成 INTERNAL，测试以「放行分支没生效」的面貌失败，极易误导排查方向。

##### C. 表述差异（语义已对齐，仅字面不同，无需签字）

| # | 位置 | 差异 |
|---|---|---|
| C-1 | `RpcInterceptor.goStyleStatusMessage` | gRPC Java 的 `Code.name()` 返回 `NOT_FOUND`，Go 的 `codes.Code.String()` 返回 `NotFound`。code 与 desc 均已保留（Go 出处 `rpc.go:95`），仅常量表示不同，不做臆造映射 |

##### D. UNRESOLVED（证据不足，**不得**按「已对齐」处理）

| # | 项 | 未知点 | 需要什么才能定 |
|---|---|---|---|
| D-1 | `Wire.canonicalHash` 的 JCS 库 | ✅ **已关（2026-10-21）**。`service-common` 的 `JcsDifferentialTest`(5/5 通过)用 cyberphone 出版 testdata 作 oracle:**复制 6 对(input,expected-output)到 `src/test/resources/jcs-fixture/{input,output}/`,erdtman 跑同样输入,断言 byte-equal**。这是真正的 Go vs Java 差分 —— 期望值就是 cyberphone 自身的 canonical 字节输出。覆盖:6 个 KAT 文件(数组/法文字符/嵌套/Unicode/数值宽度/边界) + 4 条针对性(键序/空白/指数大小端/数值塌缩) + 锁住 `Wire.canonicalHash` 全链 JCS→SHA-256→hex 与 cyberphone hash path 一致。离线无外部依赖。**实质成果**:erdtman 与 cyberphone 在 RFC 8785 KAT 上 byte-equal —— 签发/校验链两端产同一 canonical,D-1 承诺实证成立。**留档**:erdtman 在 parse 阶段对未转义 ASCII 控制字符 fail-fast,cyberphone 不 fail —— 这是 erdtman 的额外输入校验质保,**不是** D-1 想要的 canonical 差异,见 `JcsDifferentialTest` 末尾注释 |
| D-2 | `communityfact` 整体 | RAG 侧**零消费者**。代码层面已对齐，但**没有任何运行证据**，不能算行为对齐 | 接入 `comment` / `article` 等域后补集成测试 |

##### 本节的效力边界

- 本节记录的是**代码层面**的差异结论。`service/common` **从未做过 Go ↔ RAG 运行时对拍**（两边同时喂同一批输入比对输出）。因此「已修复 20 项偏差」≠「行为已验证等价」。
  - ⚠️ **2026-10-21 更正**：本行原写「全仓也**没有真实 PG / Redis 集成测试**（现有测试全部 mock 数据层）」，
    该句**已被后续工作推翻**，现共 5 个 testcontainers 文件：`UserRpcContainerWiringTest`（真 PG 12 条，
    见 §6.2 P1 已关闭）、`AvatarTransactionWiringTest`、`AdminRpcContainerWiringTest`、
    `UserApiMvcPipelineTest` / `AdminApiMvcPipelineTest`（真 Redis 容器 + 真 MVC 管线）。
    **但覆盖面仍有限**：`admin-rpc` 侧只有 wiring 级验证，`service/common` 的 20 项偏差
    仍**没有任何一条**跑在真实 PG/Redis 上 —— mock 数据层的单测证明不了 DDL、索引、
    事务隔离与连接池行为。
- 尤其 B-1 这类差异，**只有真实慢查询 + 真实超时才能暴露**，静态审查与单测都测不到，
  `tools/difftest` 也**没有慢查询场景**（见 §6.2.6「证据边界台账」）。

---

## 四、当前重构（多 phase · 真理之源 = Go）

### 4.1 锁定的决策（来自用户问答，2026-10-18）

**这些决策只决定「表达层怎么组织」，不决定「行为是什么」。** 每一项都必须按 §3.2 标注 Go 出处，并证明语义等价。

| 维度 | 决策 | 不走简化路线的原因 |
|---|---|---|
| ORM | **严格**：JPA → MyBatis-Plus（`@TableName` + Mapper 接口 `extends BaseMapper<Entity>`） | HUMAN_ONLY.MD 明确写 `@TableName` |
| Logic 类 | **严格**：合并为 1 个 `UserDomainService`（admin-rpc 是 1 个 `AdminDomainService`） | HUMAN_ONLY.MD 描述的是单一 DomainService，不是 9/13 个独立 Logic |
| DataModel | **严格**：删除，按实体拆成多个 Mapper 接口（user-rpc：2 个；admin-rpc：3 个） | HUMAN_ONLY.MD 里完全没有 DataModel 痕迹 |
| 新文件 | **落地**：`bootstrap/SnowflakeBootstrap.java` + users 域 DDL 脚本。~~Flyway `V*__create_*.sql`~~ → **2026-10-21 改为幂等重放，见 §6.2.5 DRIFT-NEW-015** | HUMAN_ONLY.MD 列出这两个文件。Bootstrap 保留；**迁移机制被推翻**（有状态账本与「双 rpc 共用一个 schema」不兼容） |

> **✅ Flyway 条目已于 2026-10-21 处置完毕**：Go 用 `AutoMigrate`（无状态），Flyway 是**有状态账本**，双 rpc 共用同一 schema 时版本号必然冲突（已实证两轮）。最终改成**幂等重放 DDL**（`SchemaInitializer`），无账本无版本号 —— 与 Go 的 AutoMigrate 同构。完整经过与实证见 §6.2.5 DRIFT-NEW-015。

### 4.2 5 阶段范围

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **Phase 0** | RAG 仓库建 worktree、提交基线、4 个 issue、4 份 spec（`RAG/.agents/specs/<slug>.md`） | 4 个 issue + 4 份 spec 落仓 |
| **Phase 1（pilot）** | `user-rpc` 落地完整 pattern：JPA→MyBatis-Plus + DataModel→2 Mapper + 9 Logic 合 1 + SnowflakeBootstrap + users 域 DDL | `mvn -pl user-rpc -am package` 通过 + 既有 LogicTest 改完跑绿 |
| **Phase 2** | `user-api`：HTTP 层（controller / dto / interceptor / advice / client），不引入 Mapper | `mvn -pl user-api -am package` 通过 |
| **Phase 3** | `admin-rpc`：平行 user-rpc，13 用例合 1 `AdminDomainService` + 3 Mapper + admin 域 DDL | `mvn -pl admin-rpc -am package` 通过 |
| **Phase 4** | `admin-api`：平行 user-api，13 Service + 2 client（admin-rpc + user-rpc） | `mvn -pl admin-api -am package` 通过 |
| **Phase 5** | 全量 `mvn clean package` + docker-compose 起来跑 gRPC 端到端 | 全绿 |

每阶段独立 stop point：跑绿后停下等用户 review，再决定继续。

### 4.3 用户问答原话（备查，避免后续被覆盖）

| 问题 | 用户答案 |
|---|---|
| ORM 这一层怎么处理？ | 严格按 HUMAN_ONLY：JPA → MyBatis-Plus（重写数据层） |
| Logic 类怎么处理？ | 严格按 HUMAN_ONLY：9 合 1 |
| DataModel.java 怎么映射？ | 严格按 HUMAN_ONLY：拆成两个 Mapper（仅在选 MyBatis-Plus 时适用） |
| SnowflakeBootstrap 与 Flyway 脚本要不要落地？ | 落地：从 UserRpcApplication 抽 SnowflakeBootstrap + 引入 Flyway + 写两份 DDL。**⚠️ 2026-10-21 补充：Bootstrap 保留，Flyway 已被移除**（有状态账本不兼容双 rpc 共库），DDL 改为幂等重放 |
| 全量 5 阶段怎么走？ | 你先修改 `/Users/pr/Documents/java&golang/RAG/AGENTS.md`，说明是什么情况 |
| 重构以什么形式落到 RAG 仓库？ | 先等下（暂停，等本文件落地后再议） |

## 五、用户协作规则（精简）

1. **每个 phase 一个 PR**。PR 自带 spec（`RAG/.agents/specs/<slug>.md`），提交前 spec 已落仓。
2. **issue 在 PR 之前**。每个 phase 起手先在 RAG 仓库开 issue 跟踪，PR body 引用 issue。
3. **分支策略：所有动作直接在 `do-my-own-gh` 上做**（2026-10-21 用户明确要求）。不开 `row/*` 分支、不建独立 worktree——即使改动很大或涉及多文件。需要隔离时用 `git stash` 而不是切分支。历史上的 `row/*` 分支（如 `row/common-fix-semantic-drift`）均为该规则生效前的产物，现已全部合并回 `do-my-own-gh`。
4. **作用范围**：本次重构仅限 RAG 仓库。`Sea-RideTheWind_golang` 已冻结只读；其他非 Spring Boot 的 Java 实现（`Sea-RideTheWind_java` 等）不在 RAG 工作范围内。
5. **Go 是真理之源**（2026-10-20 第三次切换，所有子模块统一适用）。所有 proto 字段号、错误码、JWT claims、表结构、HTTP path、Kafka topic 等语义细节，一律以 `Sea-RideTheWind_golang` 对应子模块为准。
   - RAG 与 Go 的差异**只允许出现在表达层**（框架、分层、命名、注解）。出现行为差异时，默认判定为 **RAG 错**，而非 Go 与 RAG 各有一套。
   - 引入新的 Spring 特有写法时，按 §3.2 强制标注 Go 出处；按 §3.1 走 Go ↔ RAG 对照；按 §3.3 配能真正触发该机制的测试。
6. **session 语言**：用户用中文；技术术语、代码、文件路径保留英文。
7. **review**：每个 PR 走完本地 `mvn -pl <module> -am package` + 全量 `mvn clean verify` 后，等待用户或独立 review 才能合并。**review 必须含 Go ↔ RAG 对照**。

## 六、当前状态（§6.1–§6.3 截至 2026-10-21 核对；§6.4 起为 2026-10-20 快照）

> **状态表纪律见 §6.2 表头的三条约定**——本节任何「已修 / 未修」断言都必须带核对日期，否则按 `UNKNOWN` 处理。

### 6.1 service/users：行为一致性审计已完成（本次）

Go ↔ RAG 对照审计 `service/users` 全部 5 个子模块（22 个 RPC / 22 个 HTTP 端点），**结论：修复前不具备验收条件**。

**已修复（10 项，全部有回归测试锁定）**：

| # | 问题 | 类别 |
|---|---|---|
| 1 | `@ResponseBodyAdvice` 二次包裹 `@ExceptionHandler` 的 `ResponseEntity` → **全部错误响应顶层 `code` 恒为 200** | 实测确认的致命缺陷 |
| 2 | `CreateAdmin` 4 步事务 self-invocation → `@Transactional` 静默失效 | 安全（邀请码可并发消费） |
| 3 | `CreateAdmin` 邀请码查询缺 `FOR UPDATE`（注释写了锁、SQL 没有） | 安全 |
| 4 | `UpdateUser` 写 `extra_info` 走 `wrapper.set(Map)` → jsonb 被绑成 varchar | 功能失败 |
| 5 | `SelectAvatar` 响应 `is_current` 返回更新前的旧值 | 响应自相矛盾 |
| 6 | `UpdateSelf` 空 email 把列写成 SQL NULL → 只改密码会丢邮箱 | 数据丢失 |
| 7 | `JwtUtil` zero-pad 被删 → admin 默认密钥（15B）签发必抛 `WeakKeyException` | 启动阻断 |
| 8 | 登录 `UNAUTHENTICATED` 未映射 → 密码错报成 1015「服务繁忙」 | 用户可见回退 |
| 9 | uid 解析失败静默转 `0` → 坏请求报成 1003「用户不存在」 | 错误分类丢失 |
| 10 | HTTP 状态码语义变更（业务码 1004/1006/1009 → 401；框架错误 → 500） | wire 契约 |
| 10' | §6.2.2 **DRIFT-FRAMEWORK-001** —— 第 10 项的修复在 admin-api 侧**实际上未生效**：`HttpMiddleware$ObservabilityResponseWriter.setStatus` 只更新缓存字段，等下游 `outputMessage.flush()` 把底层 `MockHttpServletResponse.flushBuffer` 提交后，`flushTo` 的 `dst.setStatus(401)` 才会被 `!isCommitted()` 守卫吞掉 → 任何 `@ExceptionHandler → ResponseEntity.status(...)` 在运行期都退化为 HTTP 200。这是同一份代码里本批 §6.2.3 A-U0 admin-api 侧补验时发现的 | 实测确认的根因；原表第 10 项已修，但缺少「transport 层 status 必须 propagate 到 underlying」的链路。本批**新增**；该 bug 同时破坏 user-api 的 N-1「推到 401」——`AuthenticationHttpStatusTest` 看似通过是因为它走的是 `standaloneSetup`，不挂 `HttpMiddleware` filter；用 `AdminApiMvcPipelineTest`（真 MVC 管线）才能复现 |

新增 **19 个回归测试**（含 2 个 `ResponseEnvelopeAdviceTest` 把 Web 层纳入回归网——此前 RAG 无任何 `@WebMvcTest`/`MockMvc`，这是这批缺陷能长期潜伏的根因）。
验证：`mvn -pl <5 modules> -am test` + `package` 全绿（users-common 3 / service-common 69 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30）。

### 6.2 已知未修（按优先级）

> **⚠️ 本表的阅读约定（2026-10-21 新增）**
>
> 1. **每行必须带「最后核对」日期，且该日期必须由一次真实执行产出**——写代码、读代码、推断都不算核对。
> 2. **无核对日期或日期过期的行，一律按 `UNKNOWN` 处理**，不得直接采信其状态或结论。这与 §3.1「不拿现存代码当对照基准」是同一条纪律：一份没有时效标记的状态表，和未标日期的旧结论一样不可信。
> 3. 发现状态与本表不符时，**先更新本表再动手**，不要让文档漂移到代码后面。

| 优先级 | 项 | 最后核对 | 说明与证据 |
|---|---|---|---|
| ~~P0~~ | ~~`RpcServerInterceptorFactory` 在 user-rpc / admin-rpc 均未注册~~ | ✅ 2026-10-21 已修 · **2026-10-21 二次更正（issue #71187874）** | `UserRpcApplication` / `AdminRpcApplication` 的 `@Import` 均补 `RpcServerInterceptorFactory` + `ObservabilityProperties` + `ObservabilityLifecycle`；admin-rpc 另补 `SnowflakeBean/SnowflakeProperties/LoggerFactoryBean`（它此前连 `@Import` 都没有）。新增 `ObservabilityWiringTest`（`@SpringBootTest`）断言 `ServerInterceptor` 真是容器里的 Bean。分支 `row/common-fix-semantic-drift` **⚠️ 二次更正：本次「已修」不成立。** `@Import` 只让 Bean **存在**，grpc-spring-boot-starter 3.1.0 **从不自动注册**用户提供的 `ServerInterceptor`（`javap GrpcServerFactoryAutoConfiguration` → 只注入 `List<GrpcServerConfigurer>`；`GrpcServerProperties` 无 interceptor 字段）。真实缺陷由 B-1 的端到端实验暴露（见 §3.4.1「B-1 补记」）：`interceptCall` / `onHalfClose` 从未执行。**真正修法**：两个 rpc 模块各补 `@Bean GrpcServerConfigurer { server -> server.intercept(interceptor); }`。原测试之所以假绿，是因为它断言的是「Bean 存在」而非「server 用了它」⇒ §3.3 新增纪律与 `GrpcInterceptorWiringTest`（2 条） |
| ~~P0~~ | ~~`ObservabilityProperties` 无 timeout 字段~~ | **2026-10-21 已修** | 新增 `httpTimeout` / `rpcTimeout`；两个 factory 改为读取该配置。**目标值已取证**：Go user 系 yaml 均未配 `Timeout`，实际生效值来自 go-zero v1.10.2 `rest/config.go:55` `default=3000` 与 `zrpc/config.go:46` `default=2000` ⇒ 各模块 yaml 显式落 `http-timeout: 3s` / `rpc-timeout: 2s` |
| **P1** | `message-rpc` 6 个 `@Transactional` 方法的 self-invocation 风险 | —（**未核对**） | 同上。按约定 2 按 UNKNOWN 处理 |
| ~~P1~~ | ~~`Logger`（静态）与 `LoggerFactoryBean`（Bean）两份逐行同构实现，重复约 370 行~~ | ✅ **2026-10-21 已去重**（用户 2026-10-21 批准动共享库公开 API） | 经方法体逐字比对，两份实现只差三处：方法可见性、方法名自调用、Bean 缺 serviceName 判空守卫。**修法**：给 `Logger` 加 `public Logger(String serviceName)` 构造 + 把 `doLogBusinessErr/doLogInfo/doLogFatal` 由包私有改为 public；`LoggerFactoryBean`（392 行 → 95 行）改为薄委托，同时删掉它自己那份 `FILTER_PREFIXES` / `IMAGE_BASE64_TRUNCATE` / `getFuncName` / `getCallChain` / `isFiltered` / `extractTraceId` / `fileBaseName`。**两套 public API 全部保留**（静态 `Logger.logXxx` 与 Bean 的 `logXxx`），调用方零改动；serviceName 仍各自独立（决策 3 未变）。**委托顺带修正一个 off-by-one**：委托前 Bean 路径栈是 `getCallChain → Bean.logInfo → 业务`，`getCallChain(3)` 跳 3 帧会落在业务调用方的**上一层**（少一帧）；委托后多一层 `doLogXxx`，正好对齐静态入口。锁定用例 `LoggerFactoryBeanTest.callChainStartsAtBusinessCaller`（探针放在未被 `FILTER_PREFIXES` 过滤的 `com.rag.common.verify` 包） |
| ~~P2~~ | ~~`Logger.call_chain` 过滤完全失效~~ | **2026-10-21 已修** | `isFiltered` 改用 `StackFrame.getDeclaringClass().getName()` 全名比对包前缀（短名比对仅作补充）；`getFuncName` 的 `contains("$")` 分支按 Go 两分支差异化。`Logger` 与 `LoggerFactoryBean` 两份同步修改。新增 `LoggerTest.callChainFilterActuallyRemovesFrameworkFrames` 锁定。**留档**：Go 侧 `github.com/zeromicro/go-zero/` 那条前缀**本来也是死的**（`getFuncName` 同样剥掉包路径），Java 版按其**意图**执行 → `call_chain` 内容比 Go 少框架帧，属 OBSERVABLE DIFFERENCE |
| ~~P2~~ | ~~499 状态码永远到不了客户端~~ | **2026-10-21 已修** | 断连分支改为 `writeTimeoutResponse(resp, 499)`，不再抛 `ServletException`；503/499 body 一并对齐 Go `http.Error`（`text/plain; charset=utf-8` + 尾部换行）。新增 `HttpMiddlewareTest.clientDisconnectReturns499`（此前**零条** 499 断言）。**连带**：两条既有 503 断言的期望值因 D-11 变更，已同步并注明变更原因 |
| ~~P2~~ | ~~`Observability.isTimeoutError` 漏判 HTTP 超时~~ | **2026-10-21 已修** | 补 `HttpTimeoutException` / `HttpConnectTimeoutException` / `InterruptedIOException`，并沿 cause 链生效。新增 `ObservabilityTest.isTimeoutErrorDetectsHttpClientTimeouts` |
| ~~P1~~ | ~~user-rpc mapper `default` 方法上的 `@Transactional` 是否真生效~~ | ✅ **2026-10-21 已验证生效** | 新增 `AvatarTransactionWiringTest`（`@Configuration @EnableTransactionManagement(proxyTargetClass = false) @MapperScan`，与 Boot `TransactionAutoConfiguration` 同构；`MybatisSqlSessionFactoryBean` + `SqlSessionTemplate`；事务管理器换成**记录型**实现）。5 条用例全绿，其中 2 条是**真触发**：DataSource 的 `getConnection()` 抛异常，断言 `createAvatarHistory` / `selectAvatarHistory` 的记录序列恰为 **`begin → rollback`** 且无 `commit`。结论：`@Transactional` **确实生效**，头像 4/5 步原子性成立。**原表建议的修法「抽独立 `@Service` Bean」不需要** —— 问题从来不是自调用，而是**缺证据**。**效力边界**：无真实 PG，故只证明「事务边界装上了、异常走回滚分支」，不证明 jsonb `CAST` 等 PG 行为（§3.3） |
| ~~P1~~ | ~~**user-rpc 无真实 PG 集成测试（jsonb / CAST / 事务真实回滚均未被证明）**~~ | ✅ **2026-10-21 已关闭**（issue #71186893） | 新增 `UserRpcContainerWiringTest`（`@SpringBootTest(classes = UserRpcApplication.class)` + Testcontainers `postgres:16-alpine`），**12 条用例全绿**。关闭 5 个 §3.3 缺口：① `JsonbTypeHandler` 的 `ps.setObject(..., Types.OTHER)` 在 MP 生成的 insert 上真能写进 jsonb 列（失效则 PG 报 `column "extra_info" is of type jsonb but expression is of type character varying`，**H2 与 mock 都测不到**）；② 手写 `@Select` 上的 `@Results` 真把 jsonb 读成 `Map` 而非静默 null；③ `updateUserById` 的 `CAST({0} AS jsonb)` 在真 PG 成立；④ 4/5 步事务**真的回滚行** —— 让 W3 撞 `UNIQUE(history_id)` 失败后断言 W2 的 `clearCurrent` 被回滚（旧类只证明 `begin→rollback` 的**调用序列**，证明不了行是否还在）；⑤ `username`/`email` 的 UNIQUE 约束真拦得住（Go 侧 `AlreadyExists` 的地基）。另加两条防御：`extra_info` 列类型必须是 `jsonb`（若退化成 text，全部 jsonb 断言会假绿）、全零值 patch 不发 UPDATE。**Ryuk 已关**（与 admin-rpc 同款 surefire env 注入）。**效力边界**：只证明 RAG 侧在真 PG 上的行为，**仍未做 Go ↔ RAG 双端对拍**（A-U2） |
| ~~P2~~ | ~~admin-api 全部 13 个端点 `@RequestBody` 仍是 `required=true`~~ | ✅ **2026-10-21 已修 · 2026-10-21 二次更正（DRIFT-NEW-016）** | 13 个端点改 `required=false` + Controller 侧 `null → new XxxReq()` 归一化。**⚠️ 二次更正**：本行原写「Go 三段取证（§6.2.4）证明 Go 对**空 body 一律不报错**」—— **该前提已被 DRIFT-NEW-016 实测证伪**（Go 的 `UnmarshalJsonMap(nil, v)` 收的是有类型的 nil map，照常遍历字段并报必填 ⇒ 真空 body 返回 `400 field "xxx" is not set`）。**连带**：(a) 本行引用的两条防回退测试 `emptyBodyDoesNotReturn400` / `emptyBodyOnPublicEndpointDoesNotReturn400` 已在 DRIFT-016 中**重命名**（现为 `emptyBodyOnProtectedEndpointIs400TextPlain` / `emptyBodyOnPublicEndpointIs400TextPlain`），旧名已不存在；(b) 原写「user-api 侧仍有 5 个端点保持 `required=true`、本轮未处理」—— grep `required = true` 在 `service/users` 全域 `src/main` **零命中**，且那 5 个端点（register / login / avatar / avatar-select 中有必填字段的 4 个 + user.update 全 `,optional`）已被 DRIFT-NEW-016 改走 `GoStyleRequest.normalize`，见 §6.2.5 |
| ~~P3~~ | ~~`admin-api` 的 `AdminApiSupport.codeFromRPCError` 未删，且与 `GlobalExceptionHandler.mapRPCStatusToCode` 重复~~ | ✅ **2026-10-21 已删** | grep 确认**零调用方**（只有自身声明与 javadoc 提到）。已删除方法与常量依赖，删除理由写进类 javadoc：留着的真实风险不是「重复」而是它是一份**可被误用的错误映射** —— 新 Service 顺手调它就会绕过自身 switch 落进默认值，这正是 `user-api` 的 `AvatarSelectService` 已踩过的坑（Go `NOT_FOUND → 500` 变成 `1003`，DRIFT-003） |
| ~~P1~~ | ~~**`service/users/admin` 全部 10 项 SEMANTIC MISMATCH**~~ | ✅ **2026-10-21 已修**（issue #71186443） | 见 §6.2.2 |
| ~~P3~~ | ~~`admin-rpc` 三个 Mapper 的 javadoc 与实现不符~~ | ✅ **2026-10-21 已修** | 三个接口都改为「空壳接口 + 方法真实归属表」。原注释不仅指错位置，还**与文件末尾的注释自相矛盾**（一个说在 Mapper default 方法里、一个说在 Service 里）。悲观锁的真实位置已写明：`LambdaQueryWrapper.last("FOR UPDATE")` 在 `AdminCreateAtomicService.createAdminAtomic`，`@Transactional` 在该 Service 上。**动机**：注释说在 Mapper 会让后续 review 去 Mapper 找锁、找不到就怀疑锁没生效 |
| ~~P2~~ | ~~`MessageRpcSupport.trimPreview` 截断长度错：255 vs Go 的 80 runes~~ | ✅ **2026-10-21 已修** | Go 出处 `message.go:234-240` 字面量 `80`。旧实现的 255 抄自列宽 `varchar(255)` 而非 Go 规则；且用 `String.length()`/`substring` 按 UTF-16 code unit 切，**中文测不出来、代理对会切出半个非法字符**。改为 `codePointCount` + `offsetByCodePoints`。新增 `MessageRpcSupportTrimPreviewTest` 6 条，其中 `surrogatePairCountedAsOneRune` 用 100 个 emoji 锁住 char≠rune |
| ~~P2~~ | ~~`markNotificationRead` 拆 SELECT + UPDATE~~ | ✅ **2026-10-21 已修** | Go `message.go:72-86` 是单条 `UPDATE ... WHERE id = ? AND recipient_id = ?` + `RowsAffected == 0 → ErrRecordNotFound`。旧实现 `selectById` 校验归属后再 `updateById` 写入（**TOCTOU 竞态**），且 `updateById` 生成的 WHERE **不含 `recipient_id`**、受影响行数被丢弃。已折成单条原子 UPDATE，归属条件进 WHERE，受影响行数透出并映射 `NotFound`。新增 `MessageMarkNotificationReadTest` 4 条，断言打在 `LambdaUpdateWrapper.getSqlSegment()` 上（§3.3：mock 数据层测不到 SQL） |
| **P0** | **`NotificationEntity.isRead` 字段名导致 MP lambda 运行期抛异常** | ✅ **2026-10-21 已修（本轮附带发现）** | 见 §6.2.4。这是本轮**唯一的运行期致命项**，且与 §6.2 上表所有条目无关，是修 `markNotificationRead` 时顺带撞出来的 |
| ~~P0~~ | ~~**admin-api 鉴权链顺序反了（DRIFT-009）**~~ | ✅ **2026-10-21 已修**（issue #71186893） | Go 出处三段：`rest/engine.go:101-113` bindRoute 先 `appendAuthHandler` 再 `ThenFunc(route.Handler)`；`rest/chain/chain.go` 的 `Then = m0(m1(...mN(h))` 索引 0 最外层先执行；`rest/server.go:222-233` `WithMiddleware` 在构造期把黑名单包进 `route.Handler`（`ThenFunc` 的参数，比 chain 任何元素都内层）。⇒ **Authorize 先于 CheckBlacklist**。原 javadoc 的 Go 出处把内外关系读反了。已改回并把两个反向锁定的测试改名反向。详见 §3.4.4 |
| ~~P0~~ | ~~**admin-api 注销绕过（DRIFT-010，安全）**~~ | ✅ **2026-10-21 已修**（issue #71186893） | `golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12` 的 `stripBearerPrefixFromTokenString` 在前缀不匹配时**原样返回整串**；旧实现 `split(" ",2).parts[1]` 会用 `"eyJ..."` 验签通过，而黑名单又按整串查不到 ⇒ **已注销的管理员 token 加 `XXBearer ` 前缀后仍可调用 admin-api**。已按 Go 逐字重写，并把该逻辑抽到 `users-common/BearerTokenExtractor` 由两个 api 模块共用。锁定用例 `AdminApiMvcPipelineTest#nonBearerPrefixedTokenCannotBypassBlacklist` + `AdminSemanticDriftApiRegressionTest#interceptor_nonBearerPrefixCannotBypassBlacklist` |
| ~~P1~~ | ~~**user-api 前缀语义与 Go 不一致（DRIFT-002）**~~ | ✅ **2026-10-21 已修** | 旧 `startsWith("Bearer ")` 大小写敏感 ⇒ 小写 `bearer <有效token>` 被误拒 401；Go 是 `strings.ToUpper(tok[0:7])=="BEARER "` 的大小写不敏感比较。已改走 `BearerTokenExtractor`。锁定用例 `UserApiMvcPipelineTest#bearerPrefixIsCaseInsensitive` |
| ~~P2~~ | ~~**400 响应体空 vs Go 的 text/plain（DRIFT-006）**~~ | ✅ **2026-10-21 已修** | Go：`service/user` 全树未注册 `httpx.SetErrorHandler` ⇒ `httpx.Parse` 失败走 `doHandleError` 的 `handler==nil` 分支 → `http.Error(w, msg, 400)` = `text/plain; charset=utf-8` + 裸文本 + 尾换行 + `nosniff`。RAG 原为 `ResponseEntity.status(400).build()`（空 body）。已加 `goStylePlainTextBadRequest`，并让 `GlobalResponseAdvice#beforeBodyWrite` 对非 JSON 响应透传（否则 400 文本会被再包成 envelope）。锁定用例 `UserApiMvcPipelineTest#malformedJsonReturnsPlainTextBadRequest` |
| ~~P3~~ | ~~**`UserApiSupport.extractUserId` / `AdminApiSupport.extractAdminId` 零调用方**~~ | ✅ **2026-10-21 已删** | 二者是 `IdentityResolver.claimedUID` 的薄包装，Go 侧无对应物（Go 直接调 `identity.ClaimedUID`），`src/main` 下各 0 个调用方。按 §3.4.1 D-2 先例「零消费者的代码不能算已对齐」删除，避免留下第二个入口 |
| ~~P1~~ | ~~`PasswordUtil.check` 对损坏 bcrypt hash 抛异常 vs Go 返回 false（DRIFT-004）~~ | ✅ **2026-10-21 已修**（用户签字「对外对齐 Go + 保留可观测性」） | Go `cryptx.CheckPassword` 对任何 bcrypt 错误都返回 false ⇒ 登录报密码错；RAG `BCryptPasswordEncoder.matches` 对 **cost 越界**的 hash 抛 `IllegalArgumentException` ⇒ `500 + 5001`。已加 `PasswordUtil.checkDetailed` + `Outcome` 枚举，对外行为对齐 Go、额外把「hash 格式非法」记进 span/log。**实测推翻原留档的触发面估计**（只有 cost 越界才抛，空串/非 bcrypt/截断 Spring 本就返回 false）。回归网 11 条，已实证能真红。详见 §3.4.4「DRIFT-004 的处置」 |

### 6.2.4 本轮（2026-10-21 第二批）取证与附带发现

#### ① Go 对「空 body」的三段取证（支撑 admin-api 13 端点 `required=false`）

不是推断，是逐段读源码得到：

| # | 出处 | 事实 |
|---|---|---|
| 1 | `service/user/admin/api/internal/handler/admin/*.go` | 13 个 handler 一律 `if err := httpx.Parse(r, &req); err != nil`，`req` 是包级 `var req types.XxxReq` |
| 2 | go-zero v1.10.2 `rest/httpx/requests.go:153` `withJsonBody` | `r.ContentLength > 0 && ContentType 含 application/json`。空 body ⇒ false ⇒ `ParseJsonBody` 走 `mapping.UnmarshalJsonMap(nil, v)`，对 struct 是 **no-op、不报错** |
| 3 | admin `api/internal/types/types.go` | 13 个 Req **零个** `Validate()` 方法、**零个** `validate:` tag；且 admin 服务**从未**调 `httpx.SetValidator` ⇒ `getValidator()` 返回 nil ⇒ `httpx.Parse` 的校验环节整体不生效 |

**结论**：Go 对空 body 的 POST 一律 **HTTP 200 + 业务码**，req 停在**零值 struct**。
以 `loginlogic.go:37-56` 为例：零值 username/password **直接透传给 rpc**，
由 rpc 判 `NotFound → 1003` / `Unauthenticated → ErrorLoginWrong`（= **1002**，不是 1008）。

**RAG 处置**：13 端点全改 `@RequestBody(required = false)` +
Controller 侧 `if (req == null) req = new XxxReq()`（对齐 Go 的零值 struct，服务层因此不会 NPE）。

> **user 侧同样做了独立取证**（2026-10-21，**未套用 admin 的结论** ——
> §3.4.3 N-3 明确警告过「admin 侧取参机制不同，不可跨模块套用结论」）：
> `internal/handler/user/*.go` 9 个 handler 一律 `var req types.XxxReq` + `httpx.Parse`；
> `types.go` 8 个 Req、**零个** `validate:` tag、**零个** `Validate()`；
> 整个 user 服务**从未**调 `httpx.SetValidator`。结论与 admin 侧一致 ⇒
> user-api **9 个端点全部**改为 `required=false` + Controller 侧 `null → new XxxReq()`。
> 落点验证：`loginlogic.go` 的 switch 是 `Internal → ErrorServerCommon` / `default → CodeServerBusy`，
> 零值 username/password **直接透传给 rpc**（`UserLoginService` 同样对 null 字段兜底成 `""`）。
>
> **⚠️ 2026-10-21 二次更正（DRIFT-NEW-016）**：本段原写「新增 2 条真管线断言
> `emptyBodyOnLoginDoesNotReturn400` / `emptyBodyOnUpdateDoesNotReturn400`」并断言
> 零值 LoginReq **带着空 username/password 抵达 rpc**。该结论**已被实测证伪**：
> Go 的 `UnmarshalJsonMap(nil, v)` 收的是**有类型的 nil map**，在 `unmarshaler.go:988`
> 的 type switch 里命中 `case map[string]any`，照常遍历字段 ⇒ 真空 body 返回
> `400 field "username" is not set`，**零值 req 到不了 rpc**。
> 该条已改名 `emptyBodyOnLoginIs400NotPassedThrough` 并反转断言；
> `emptyBodyOnUpdateDoesNotReturn400` 因 `user.update` 四字段全 `,optional` **不反转**，
> 改名 `emptyBodyOnUpdateStillPassesThroughBecauseAllFieldsAreOptional`，留作对照组
> ——同一个「空 body」在两个端点一个 400 一个 200，差别只在 DTO 的 tag。

#### ② `NotificationEntity.isRead` → 运行期致命（本轮唯一 P0，附带发现）

修 `markNotificationRead` 时为写「断言打在真实 SQL 上」的测试（§3.3），
用 `TableInfoHelper.initTableInfo` 初始化 MP 元数据后撞出来的：

```
### keySet=[content->content, ..., isRead->isRead, readAt->readAt, ...]
### NotificationEntity::isRead  →  MybatisPlusException:
        can not find lambda cache for this property [read] of entity [...NotificationEntity]
### NotificationEntity::getReadAt →  OK
```

**根因**：MP 的 `LambdaXxxWrapper` 解析 `Entity::method` 时按 JavaBean 约定剥掉
`get`/`is` 前缀再拿**属性名**查 `TableInfo.propertyMap`。
字段叫 `isRead` ⇒ 属性名 `isRead`、getter `isRead()` ⇒ lambda 解析成属性 `read` ⇒ 落空。

**影响面**：`NotificationMapper` 的 `countUnread` / `markAllReadByRecipient` / `markRead`
三处都用 `NotificationEntity::isRead` 构造条件 ⇒
**`listNotifications(unreadOnly)` / `getUnreadSummary` / `markAllNotificationsRead` /
`markNotificationRead` 四条业务路径一调即抛异常**。

**为什么长期没暴露**：message-rpc **此前没有任何一条会真正执行 SQL 的测试**，
全部是纯 Mockito mock —— mock 数据层不装配 MP 元数据，天然测不到这条（§3.3）。

**修法**：字段改名 `isRead` → `read` + `@TableField("is_read")` 显式列映射，
getter 仍为 `isRead()`。实测改后：

```
### keySet=[read->is_read, readAt->read_at, recipientId->recipient_id]
### E::isRead → (is_read = ? AND recipient_id = ?)   ✅
```

**新增防线**：`MessageMarkNotificationReadTest#entityFieldMustBeNamedReadNotIsRead`
用反射锁死字段名 + `@TableField` 值（静态引用在编译期看不出问题，只有真正解析 lambda 时才炸）。

> **这条的教训**：`@TableField` / Lambda wrapper 属于 §3.3 点名的「运行期动态装配、不报错」
> 那一类，但它比 `@Transactional` 更隐蔽 —— `@Transactional` 失效是**静默降级**，
> 而 lambda 解析失败是**立即抛异常**。也就是说：一个模块只要从来没跑过真实 SQL，
> 这类问题就<b>完全不会出现在任何测试报告里</b>。判据不是「有没有报错」，
> 而是「<b>有没有真的执行过这条 SQL</b>」。

### 6.2.2 `service/users/admin` 语义审计修复清单（issue #71186443，2026-10-21）

审计范围：Go `service/user/admin/{rpc,api}` 14 logic + 12 api logic + model + middleware
↔ RAG `service/users/admin/{admin-rpc,admin-api,admin-proto}`。结论 **SEMANTICALLY NOT EQUIVALENT**。
**已修 13 项（9 MISMATCH + 2 wire 契约 + 1 处随修复连带纠正的文档病灶 + 1 服务共享底层 bug）**，
留档 4 项见 §3.4.3 的 N-3/N-4/N-5/N-6。

| # | 类别 | 位置 | Go 行为 | 修法 |
|---|---|---|---|---|
| DRIFT-001 | MISMATCH | `AdminSeederBootstrap` | seeder 用包内常量 `defaultAdminPassword = "admin"`（`svc/servicecontext.go:18`），**不读** `Config.System.DefaultPassword` | seeder 不再依赖 `AdminRpcProperties`，改用 `AdminDomainService.DEFAULT_ADMIN_PASSWORD`；`resetUserPassword` **仍读配置**（Go 侧本就是两个不同来源，已加防回退测试锁住） |
| DRIFT-002 | MISMATCH | `AdminGetUserService` | api 层写死 `Found: true`（`getuserlogic.go:59`）；rpc 层确实不设（`false`） | 显式 `setFound(true)`；同步修正 `AdminServiceGrpcImpl.getUser` 里「参照了错误层」的注释 |
| DRIFT-003 | MISMATCH | `AdminUpdateSelfService` | `NotFound → 1003`（`updatelogic.go:61`） | 补 `NOT_FOUND` case（此前落 default `1015`，与 user-api DRIFT-003 同类） |
| DRIFT-004 | MISMATCH | `update` / `resetuserpassword` / `unbanuser` | 这 3 个端点 `Internal → 5003 查询数据库失败`，其余 9 个 → `5001` | 三处改 5003，并加**防回退测试**断言 `banuser` 仍是 5001 |
| DRIFT-006 | MISMATCH | `AdminDomainService.getUserList` | `if len(keyword) > 0`，**不 trim**（`model/admin.go:152`） | 去掉 trim |
| DRIFT-007 | MISMATCH | 同上 + `GetUserListReq` | API 层 `form:"page,default=1"` 只在**参数缺失**时生效；rpc 层不兜底，`page=0` → 负 OFFSET → PG 报错 → `Internal` | default 上提到 api 层（DTO 改 `Long` 以区分 null / 显式 0，新增 `pageOrDefault()` / `pageSizeOrDefault()`）；rpc 层删掉归一化 |
| DRIFT-008 | MISMATCH | `AdminDomainService.createAdmin` | 无 username 空校验，空串可建号 | 移除 `INVALID_ARGUMENT` 分支（此前该 code 在 api 层无 case ⇒ 报成 `1015 服务繁忙`） |
| DRIFT-009 | MISMATCH | `AdminJwtAuthInterceptor` | go-zero `WithJwt` 把 Jwt middleware 追加到切片尾部 + `withMiddlewares` 反向包裹 ⇒ 实际链路 `CheckBlacklist(Jwt(handler))`，**黑名单先执行** | 两段拆开，黑名单前置；各自 fail-open 行为逐条对齐 |
| DRIFT-010 | MISMATCH | `AdminSeederBootstrap` | `NewServiceContext` 在 `zrpc.MustNewServer` **之前**跑 seeder | `@EventListener(ApplicationReadyEvent)` → `@PostConstruct`（早于 `finishRefresh` 的 `SmartLifecycle.start()`）。**连带修正**：注入 `SnowflakeBean` 强制 bean 创建顺序，否则 `@PostConstruct` 阶段会用静态兜底 workerId产出与后续不同 workerId 的 ID，存在撞号风险。**再连带**：补 `@DependsOnDatabaseInitialization`（实测证明框架已通过传递链保证该顺序，本注解是冗余保险而非修复 —— 见下方⚠）|
| A-01 | wire | `AdminListUserService` + `GetUserListResp` | 空结果 → `"list": null`（Go 是 nil slice，且 `json:"list"` **无 omitempty**） | Service 侧空结果置 null；DTO 侧摘掉 `@JsonInclude(NON_NULL)`（否则 null 会变成「字段消失」） |
| A-02 | wire | `AdminApiSupport.fromProto(AdminInfo)` | Go `types.AdminInfo.Email` 是 `json:"email"`（**无 omitempty**）⇒ 恒输出 `"email":""` | 总是 setEmail。**注意 `UserInfo` 侧不改**：Go `types.UserInfo.Email` 带 `omitempty`，空时应省略 |
| DRIFT-FRAMEWORK-001 | 共享 bug | `HttpMiddleware$ObservabilityResponseWriter.setStatus/sendError` | 此前只缓存到 `this.status`，等 `chain.doFilter` 跑完再 `flushTo(dst)` 才写底层。**实测**：Spring `HttpEntityMethodProcessor.handleReturnValue` 末尾调 `outputMessage.flush()` → `wrapper.flushBuffer()` → 底层 `MockHttpServletResponse.setCommitted(true)`；后续 `flushTo` 的 `dst.setStatus(401)` 被 `MockHttpServletResponse.setStatus(int)` 的 `if (!isCommitted())` 守卫吞掉。**结果**：`@ExceptionHandler(AuthenticationException.class)` 返回 `ResponseEntity.status(UNAUTHORIZED)` 也只输出 HTTP 200 + `{code:1006}` —— 整条 admin-api / user-api 的「鉴权短路 → 401」在所有运行期看起来全绿，实则全部被降级。这是把 DRIFT-009 与 N-1「推到 401」的修复完全埋没的根因 | setStatus / sendError 立即 propagate 到 underlying（`((HttpServletResponse) this.original).setStatus(sc)`，每次**同时**更新 `this.status`，因为 timeout/canceled 分支的合成响应仍走 `rec.statusCode()` 读）；body 仍 buffer；setHeader 仍 buffer（不影响 commit 状态）。**全栈验证**：admin-api 83 + admin-rpc 36 + user-api/user-rpc 5 modules 全绿 |

**回归测试**：`AdminSemanticDriftRegressionTest`（admin-rpc，12 条）+ `AdminSemanticDriftApiRegressionTest`（admin-api，24 条）
+ `AdminApiMvcPipelineTest`（admin-api，11 条；**首次把真实 MVC 管线 + 真实 Redis 容器 + 真实拦截器 advice 链纳入回归**，同时锁住 DRIFT-FRAMEWORK-001 / DRIFT-009 / N-1）。
其中 DRIFT-006/007 的断言打在**MyBatis-Plus 实际生成的 SQL 片段与绑定参数**上，不是 mock 返回值 —— 后者无论 wrapper 里装什么都照常返回预设值，测试必然假绿（§3.3）。
**容器集成测试**：`AdminRpcContainerWiringTest`（admin-rpc，7 条，真实 PG 16 + 真实 Spring 容器），见 §6.2.3。
验证：`mvn -pl admin-rpc,admin-api -am test` + `package` 全绿（**admin-rpc 36 / admin-api 83** / users-common 3 / service-common 78，均为 2026-10-21 实测计数；新加的 11 条被 DRIFT-FRAMEWORK-001 修复前全部因 HTTP 200 假绿，修复后全部断言 401 / 2003 通过）。

**效力边界（已部分更新）**：DRIFT-001 / 006 / 007 的行为已在真实 PG 上验证（§6.2.3 A-U2 / A-U3）；
**DRIFT-009 的鉴权链与 HTTP 401（N-1）现已穿过真实 MVC 管线 + 真实 Redis 容器验证**（`AdminApiMvcPipelineTest`）。
仍缺的：DRIFT-008 的**建号路径**尚未在真库上跑过（需要真实 invite 行 + 并发消费）。

> **⚠ DRIFT-010 提前 seeder 后会丢 Flyway 时序吗？—— 曾误判，实测已定论（2026-10-21）**
>
> **结论：不会丢。框架本来就保证顺序。**
>
> 把 seeder 从 `ApplicationReadyEvent` 提前到 `@PostConstruct` 后，它会在
> `finishBeanFactoryInitialization` 期间创建；而 Flyway 迁移跑在 `flywayInitializer`
> bean 创建时。单看 bean definition 注册顺序（组件扫描早于自动配置）**看起来**会先跑 seeder。
> 当时据此写下断言：「没有机制把二者串起来，不加约束会报
> `relation "admin_admin" does not exist` 并让容器启动失败」。
>
> **那个断言是错的 —— 而它恰好违反 §3.2/§3.3：凭静态推理就断定了一个框架机制。**
> 加 Testcontainers 集成测试后做负向验证：把 `@DependsOnDatabaseInitialization`
> 注释掉重跑，**7 条仍全部通过**。真正原因是：
>
> MyBatis-Plus 自带 `com.baomidou.mybatisplus.autoconfigure.MybatisDependsOnDatabaseInitializationDetector`
> （继承 `AbstractBeansOfTypeDependsOnDatabaseInitializationDetector`，监听类型为
> `org.mybatis.spring.SqlSessionTemplate`），Spring Boot 的
> `DatabaseInitializationDependencyConfigurer` 会自动给匹配的 bean 打上「依赖数据库初始化」标记：
> ```
> AdminSeederBootstrap → AdminMapper → SqlSessionTemplate（被自动标注） → flywayInitializer 先跑
> ```
>
> **处置**：注解**保留**在 seeder 上，但定位从「必需的修复」降级为「不依赖传递链的冗余保险」——
> 传递链依赖「MyBatis-Plus 提供 detector」+「Boot 收集 detector」两个前提，
> 任一变化（换 ORM、Boot 大版本升级、detector 被裁掉）都会让 seeder 静默失去保护。
> javadoc 已按实测结论重写，不再声称它是必需的。
>
> **这条教训比结论本身更重要**：上一轮正是用「读代码 → 推理 bean 生命周期 → 断言会失败」
> 得出的结论，而 §3.3 明写「不得凭注解名称断定机制已启用」。
> **推理出的机制风险必须配一条能真触发的测试去证实或证伪，否则只能记 UNRESOLVED，不能写成断言。**

### 6.2.3 `service/users/admin` 的 UNRESOLVED（2026-10-21 收尾）

> 下列状态由 `AdminRpcContainerWiringTest`（真实 PostgreSQL 16 容器 + 真实 Spring 容器，7 条）
> 与 `AdminApiMvcPipelineTest`（admin-api，11 条；真实 Redis 容器 + 真实 Spring 容器 + 真 MVC 管线）
> 两套实测产出，非静态推导。**4 项原始 UNRESOLVED 已全部关闭**（A-U0 两端、A-U1、A-U2、A-U3），
> 其中 A-U2 已于 2026-10-21 由 `tools/difftest` 双端运行时对拍补齐（见 §6.2.5）。

| # | 项 | 状态 | 证据 / 剩余未知点 |
|---|---|---|---|
| A-U0 | 真实 Spring 上下文测试 | **已关闭（admin-rpc + admin-api 两侧）** | admin-rpc 由 `AdminRpcContainerWiringTest`（7 条，真 PG）关闭；admin-api 由 `AdminApiMvcPipelineTest`（11 条，真 Redis 容器 + `@SpringBootTest(AdminApiApplication)`）关闭，本批**新增**。该测试同时暴露并锁住一个 **共享底层 bug**（DRIFT-FRAMEWORK-001，`HttpMiddleware` 的 status propagation 与 Spring 的 commit 时序冲突：所有 `ExceptionHandler → 401 / 5003` 在 MockHttpServletResponse 的 `!isCommitted()` 守卫下被降级为 200） |
| A-U1 | Go 侧 uid **格式非法**（非缺失）时的 HTTP 状态码 | **已关闭**（2026-10-21 + §3.4.3 后补行补录） | 已读 go-zero v1.10.2 源码原文：admin 服务 yaml 未注册 `httpx.SetErrorHandler` ⇒ `buildErrorHandler` 返回 `nil` ⇒ `doHandleError` 走到 `http.Error(w, err.Error(), http.StatusBadRequest)` ⇒ Go 输出 **HTTP 400 + text/plain**（无 JSON envelope）。与 user-api 不可跨模块套用结论——user-api 走的是另一个 handler 配置。这里由 `AdminApiMv*cPipelineTest` 里未覆盖，**不进入回归网**——若日后有人加 uid 路径的端到端测试，按 Go 现状写 `uid=abc → HTTP 400 + text/plain` 即可 |
| A-U2 | 两端运行时对拍 | **已关闭（两侧，2026-10-21）** | RAG 侧实证：空库上 seeder 造出的 admin 用 `admin/admin` 登录通过、用 `123456` **不**通过（DRIFT-001 关闭）；重复执行 seeder 不产生第二行（幂等）；`resetUserPassword` 用的是配置值 `123456` 而非 seeder 常量（两个来源未串）。Go 侧由 `tools/difftest` 补齐：两套栈同时起、各跑同一串 39 步请求序列，逐字段比对响应。最新一次结果 **39 步 / 39 同 / 0 已签字 / 0 未预期差异**（DRIFT-NEW-016 对齐后，原「36 同 / 3 已签字」的 3 条已消解）。**⚠️ 效力边界见 §6.2.6：39 步里有 13 步只比状态码不比 body，且是 HTTP wire 层对拍、gRPC 层未直接对拍** |
| A-U3 | `getUserList` 的 LIKE 双重包裹 | **已关闭**（双端对拍二次确认） | 真 PG 上实测：RAG 的 `%%alice%%` 与 `%%%alice%%%` 命中集合完全相同，且与原生 `LIKE '%alice%'`（Go 写法）结果集一致；已覆盖 username 命中 + email 命中（OR 分支）两个来源。判定为 IMPLEMENTATION DIFFERENCE 而非漂移，已实证。**2026-10-21 双端对拍二次确认**：`admin.getuserlist` 三种形态（精确匹配 / 前缀 `dt_` / 匹配不到）在两侧返回完全相同的 `list` 与 `total`，含 `extra_info` jsonb 内容逐字段一致 |

##### 搭建容器测试时踩到的环境坑（换机器 / 换网络时必读）

| 坑 | 现象 | 解法 | 出处 |
|---|---|---|---|
| **Ryuk 拉不下来** | `Can't get Docker image: RemoteDockerImage(testcontainers/ryuk:0.14.0)`，发生在任何 `@Container` 之前，整类 ContainerFetch | surefire 注入**环境变量** `TESTCONTAINERS_RYUK_DISABLED=true`（见 admin-rpc/pom.xml）。**`testcontainers.properties` 与 `-D` 都无效** —— Testcontainers 2.x 的 `ResourceReaper.instance()` 字节码里就一句 `System.getenv(...)` | 已实测 |
| **`disabledWithoutDocker` 误判** | Docker 明明可用，日志显示已 Connected，但全部用例 Skipped | 去掉该参数。2.x 的 `DockerAvailableDetector` 在本机判定为不可用，而静态 `@Container` 字段初始化早于条件求值，所以日志顺序具有欺骗性 | 已实测 |
| **gRPC server 关不掉** | 写 `grpc.server.enabled=false` 无效，仍起 server 去抢 8081 → `BindException: Address already in use` | 改 `grpc.server.port=0` 绑临时端口。devh 3.1.0 的 `GrpcServerLifecycle` **没有** `@ConditionalOnProperty("grpc.server.enabled")`，无开关可用 | 已实测 |
| **`PullPolicy` 位置变了** | 2.x 里 `PullPolicy` 在 `org.testcontainers.images`，且**没有** `neverPull()` | 用默认的 `DefaultPullPolicy`（本地已有镜像则不拉）。测试用的 `postgres:16-alpine` 需本地预先存在 | 已实测 |

> ⚠ 关掉 Ryuk 的代价是**容器不再自动回收**，CI 跑完可能残留。
> Docker Hub 可达后删掉 admin-rpc/pom.xml 里那段 surefire 配置即可恢复。

### 6.2.5 双端运行时对拍 `tools/difftest` 的产出（2026-10-21）

首次把 Go 与 Spring Boot **同时起进程、喂同一串请求、逐字段比对响应**。
产物在 `tools/difftest/`，读法与踩过的坑都写在那里的 README 里。
最新一次：**39 步 / 36 同 / 3 已签字 / 0 未预期差异**。

它立刻抓出了三个静态审查完全看不到的东西 —— **前两个是 RAG 的真缺陷，不是对拍环境的产物**。

| ID | 级别 | 内容 | 证据 |
|---|---|---|---|
| **DRIFT-NEW-014** | **P0 · 配置缺陷 · 已修（2026-10-21 当日）** | **`user-api` 连不上 `user-rpc`。** `user-api/application.yml` 的 `grpc.client.user-rpc` **漏了 `negotiation-type: plaintext`**，而 devh `grpc-client-spring-boot-starter` 3.1.0 的 `grpc.client.GLOBAL.negotiation-type` **默认值就是 TLS**（证据：jar 内 `META-INF/spring-configuration-metadata.json` 的 `defaultValue`）。于是 user-api 发 TLS ClientHello，user-rpc 的 netty 报 `Http2Exception: HTTP/2 client preface string missing or corrupt. Hex dump: 16030301...`（`0x16` = TLS handshake record），所有经 user-rpc 的业务码塌成 `1015 服务繁忙` | `admin-api/application.yml` **有**这一行且带 DRIFT-NEW-008 注释解释为什么必须有 —— 当年 admin 侧踩过并修了，**user 侧单纯被漏掉**。这不是对拍环境特有的：默认值在任何环境都一样 |
| **DRIFT-NEW-014 修法** | — | `user-api/application.yml` 补 `negotiation-type: plaintext`（含字节码级出处注释）。回归网 `UserApiGrpcNegotiationTest`（3 条）：把 `grpc.client.user-rpc.address` 覆盖成 `static://127.0.0.1:<动态端口>`，起一个**真 plaintext gRPC server**，用容器里**真实的** `UserRpcClient` 打一次**真实调用** —— 走的是 devh `ShadedNettyChannelFactory` 的生产代码路径，不是测试里写死 plaintext。外加一条 **TLS 负向对照**（同一个 server 用 TLS 连必须失败），保证正向用例不是假绿。**已实证能真红**：临时删掉那行配置后，该用例报 `NotSslRecordException: not an SSL/TLS record`、pipeline 里出现 `SslHandler` —— 正是生产上 `HTTP/2 client preface string missing` 的镜像（同根因、客户端侧视角） |
| **DRIFT-NEW-015** | **P0 · 结构性缺陷 · 已修（2026-10-21，用户拍板去掉 Flyway）** | **Flyway 版本号撞车 ⇒ `admin-rpc` 根本无法启动。** 两个模块各自维护 `classpath:db/migration`，`V1/V2` **版本号相同、内容不同**：`user-rpc` = `V1__create_user` / `V2__create_user_avatar_history`；`admin-rpc` = `V1__create_admin` / `V2__create_admin_invite` / `V3__create_user`。`flyway_schema_history` 挂在 schema 上、**一个 schema 只能有一本账**，共用库时后启动的要么报 `version (3) is newer than the latest available migration (2)` 并**静默跳过自己的迁移**（进程照样 Started，表却是空的），要么 checksum mismatch 直接启动失败 | 而 `admin-rpc` **按设计必须**与 `user-rpc` 共用 `users_user` —— 这是它自己 `V3__create_user.sql` 的注释写明的（「admin-rpc 也读写 users_user 表……用 `IF NOT EXISTS 保证幂等：user-rpc 启动时已经建过表」）。⇒ **要满足设计意图就必须共用库，共用库就撞账本，无解**。§4.1 早就警告过这点，现在有实证了 |
| **DRIFT-NEW-015 修法** | — | **去掉 Flyway，改为幂等重放 DDL**：4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`，由各 rpc 的 `bootstrap/*SchemaBootstrap` 在 `@PostConstruct` 调 `SchemaInitializer` 原样重放；两个 rpc 的 flyway 三件套依赖全删；`AdminSeederBootstrap` 补显式 `@DependsOn`。详见下方「已定案」小节 |
| **DRIFT-NEW-016** | P0 · 语义差异 · **已修（2026-10-21，用户拍板「向 Go 对齐」）** | **Go 有必填字段校验，RAG 没有。** go-zero 的 `httpx.Parse` 走 `core/mapping`，只认 `,optional` 标签，**`,omitempty` 不算 optional**（`unmarshaler.go:966-969` 走 `!opts.optional()` 分支直接 `newInitError`）。缺字段 ⇒ `HTTP 400 + text/plain + field "xxx" is not set` | 取证脚本 `tools/difftest/probe-go-required-fields.py`（真 Go 进程，2026-10-21）。修法与完整规则见下方「DRIFT-NEW-016 的处置」小节 |
| **DRIFT-NEW-016 修法** | — | **新增 `users-common/http/` 四件套**（`GoStyleRequiredFields` 接口 / `GoRequiredFieldException` / `GoStyleRequired.field()` / `GoStyleRequest.normalize()`），14 个 DTO 显式实现必填校验、14 个 controller 端点走 `GoStyleRequest.normalize`，两份 `GlobalExceptionHandler` 各加一个 `@ExceptionHandler(GoRequiredFieldException)` → 复用既有 `goStylePlainTextBadRequest`。**同时推翻了 `UserController` / `AdminController` 类级 javadoc 里的「空 body 是 no-op、不报错」这条错误论证**（详见下方小节） |

> **为什么 DRIFT-NEW-015 至今没被发现**：RAG 的 `docker-compose.yaml` 里**只有
> `user-rpc` / `user-api` 两个服务**，根本没有 `admin-rpc` / `admin-api`。
> 单跑 user 域时账本永远是自洽的；admin 域从未被编排放上过，
> 冲突自然一次都没露过。**这不是「还没测」，是「根本没部署过」。**

##### DRIFT-004 的处置：**已定案（用户 2026-10-21 签字「对外对齐 Go，但保留可观测性」）· 已实施**

**决策**：对外行为与 Go 逐字一致（都报密码错），但**额外**把「hash 格式非法」记进
log 与 OTel span。理由是 §1「行为以 Go 为准、表达以 Spring 为准」：
**错误码属行为必须对齐，指标属表达可以比 Go 更强**。Go 侧这一路**完全静默**。

**实测行为谱**（Spring Security **7.0.7** + `spring-security-crypto`，真跑非推断）：

| 输入形态 | 修复前 | Go | 修复后 |
|---|---|---|---|
| 合法 hash + 密码匹配 | `true` | `true` | `true` |
| 合法 hash + 密码不匹配 | `false` | `false` | `false` |
| 空串 hash | `false` | `false` | `false` |
| `"abc"`（完全不是 bcrypt） | `false` | `false` | `false` |
| `"$2a$10$tooshort"`（截断） | `false` | `false` | `false` |
| **`"$2a$99$…"`（cost 越界）** | **抛异常 → `500+5001`** | `false` | **`false`** |
| `"$2b$"` / `"$2y$"` 合法 hash | `true` | `true` | `true` |
| null hash / null 明文 | `false` | `false` | `false` |

> **⚠️ 本次实测推翻了两个广泛流传的猜测，下一轮审计不要重新"发现"它们**：
> 1. **触发面远窄于原留档**。原留档写「对**任何** bcrypt 错误抛异常」，
>    实测**只有 cost 字段越界**才抛。Spring 的
>    `BCRYPT_PATTERN`（`\A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}`）
>    在 `matchesNonNull` 里把绝大多数畸形 hash 挡在 `BCrypt.checkpw` 之前并**返回 false**。
> 2. **「RAG 不认 `$2b$`」是伪漂移**。`BCRYPT_PATTERN` 的 `(a|y|b)?` 已覆盖
>    `$2$`/`$2a$`/`$2b$`/`$2y$`，与 Go `decodeVersion`（只判 major ≤ `'2'`）接受的集合相同。
>    老版本 Spring 的 `startsWith("$2a$")` 硬检查在 **7.0.7 已不存在**。

**实现**：`users-common/cryptx/PasswordUtil` 新增 `Outcome` 枚举与
`checkDetailed(raw, hash)`；`check(raw, hash)` 保留原签名但**契约收紧**为「永不抛异常」。
两个 rpc 的登录调用点改用 `checkDetailed`：
- `UserDomainService#login`：`MALFORMED_HASH` 时 `span.setAttribute("auth.malformed_hash", true)`
  + `logger.logBusinessErr`，**对外仍返回 `status=1`**（与 Go 的 `loginlogic.go` 一致）
- `AdminDomainService#login`：`MALFORMED_HASH` 时打一条 `log.error`（该类**没有** span 上下文，
  只有 `log` + `metrics`，见其字段声明），对外仍抛 `UNAUTHENTICATED`

**回归网**（11 条，`mvn clean verify` 全绿）：
- `PasswordUtilGoCompatibilityTest`（users-common，7 条）—— 上表每一行逐条钉死
- `LoginMalformedHashDrift004Test`（user-rpc，4 条）—— 端到端证明「报密码错而非 500」，
  外加「正常密码错不被误吞成 malformed」「正常登录路径未受影响」

**已实证能真红**：临时移除 `checkDetailed` 的 catch 后，
两条用例立刻报 `IllegalArgumentException: Bad number of rounds` ——
正是生产上冒泡成 `500 + 5001` 的那个异常（§3.3 要求）。

**残留的已知反向差异（不修，留档）**：无 minor 的 `"$2$"` 前缀。
实测 RAG 返回 `false`，而 Go 的 `decodeVersion` 在 `sbytes[2] == '$'` 时会接受 ⇒ Go 应返回 `true`。
**不修的三个理由**：① 方向是「RAG 更严格」，登录场景 fail-closed 更安全，不是漏洞；
② 修它要绕过 `BCryptPasswordEncoder` 直接调 `BCrypt.checkpw`，等于放弃模式校验，
为一个几乎不会出现的旧格式牺牲防御；③ `$2$`（无 minor）是 bcrypt 最早的实验格式。
锁定用例 `PasswordUtilGoCompatibilityTest#barePrefixIsFalseInRagButGoWouldAcceptIt`——
它断言的是**当前偏差**，所以哪天要对齐 Go，改的是这条测试的期望值而不是删掉它。

##### DRIFT-NEW-016 的处置：**已定案（用户 2026-10-21 拍板「向 Go 对齐」）· 已实施**

**规则（全部经 `tools/difftest/probe-go-required-fields.py` 真进程实测）**：

```
字段必填  ⟺  tag 不含 ,optional
              AND tag 不含 ,default=
              AND 字段类型是【标量】（Map / Slice / Array 永不计必填）
```

| 输入 | Go 行为 | Go 出处 |
|---|---|---|
| 标量必填字段**缺席**（含**真空 body** 与 `{}`） | `400 field "xxx" is not set` | `unmarshaler.go:966-969` → `newInitError`（`:1199`） |
| 标量字段值 `""`（**json 通道**） | **接受**，继续往下走 | `processFieldPrimitive` → `fillWithSameType`，不查必填 |
| 标量字段值 `null` | `400 field "xxx" mustn't be nil` | `unmarshaler.go:862` |
| Map / Slice 字段缺席 | **接受** | `unmarshaler.go:944-948` 走 `processFieldNotFromString` |
| 带 `,default=` 的字段缺席 | **填默认值**，不报必填 | `unmarshaler.go:918` 先命中 `opts.getDefault()` |

**实测命中的必填字段**（此前注释里标成 optional 的，全部是错的）：
`CreateUserReq.email`、`CreateAdminReq.email` / `invite_code`、
`UpdateSelfReq` 的 username / password / email **三个全是**、
`UpdateUserReq` 的 username / password / email、`GetUserListReq.keyword`、
`SelectAvatarReq.history_id`、`UploadAvatarReq.avatar_url`。
**不**必填的：`user.update` 四个字段（全 `,optional`）、
`GetUserListReq.page` / `page_size`（有 `default=`）、所有 map 字段。

**实现**：`users-common/http/` 四件套 + 14 个 DTO + 14 个 controller 端点。
刻意**不用反射**：反射读字段名是隐式约定，字段改名时编译器不报错、校验静默失效
（§3.3「框架机制必须验证实际生效」的同一类病灶）；每个 DTO 显式写出自己的必填字段，
改名即编译失败。

> **⚠️ 本次同时推翻了 `UserController` / `AdminController` 类级 javadoc 的一条错误论证**
> （与 §3.4.4 DRIFT-009 **完全同型**：引用了真实文件、但把行为读反了）。
>
> 原文写：「空 body ⇒ `withJsonBody` 为 false ⇒ `ParseJsonBody` 走
> `mapping.UnmarshalJsonMap(nil, v)` ⇒ **对 struct 是 no-op、不报错**」。
> **前半句对，后半句错**：`core/mapping/jsonunmarshaler.go:19-21` 的
> `UnmarshalJsonMap(m map[string]any, v any)` 收的是**有类型的 nil map**，
> 传进 `Unmarshal(i any, ...)` 后在 `unmarshaler.go:988` 的 type switch 里
> **命中 `case map[string]any`**（不会落到 `default` 的 `errUnsupportedType`），
> 于是照常 `unmarshalValuer` 遍历全部字段 —— 空 map 下 `getValue` 全部落空，
> 标量必填照样走 `newInitError`。
>
> **实测坐实**：真空 body（Content-Length: 0）与 `{}` 都返回 `400 field "username" is not set`。
>
> **代价**：这个错误结论曾让 22 个端点全部设成 `required = false` + 零值归一化，
> 并被写进 **7 条测试**当作「A-P2 回归」锁住。本批把它们逐条对照 Go 源码 + 探针实测后
> **反转了 6 条**。**这正是 DRIFT-009 末尾那条评审纪律的第二次应验：
> 测试名与注释写得再完整，也必须独立回 Go 源码复核一次。**
> 被反转的 6 条：`AdminApiMvcPipelineTest` 的
> `emptyBodyDoesNotReturn400` / `emptyBodyOnPublicEndpointDoesNotReturn400` /
> `missingUidIs1017EndToEnd` / `emptyUserListIsNullEndToEnd` /
> `updateSelfNotFoundIs1003EndToEnd` / `updateSelfInternalIs5003EndToEnd`，
> 以及 `UserApiMvcPipelineTest` 的 `emptyBodyOnLoginDoesNotReturn400`
> （其中 3 条只是**请求 body 不完整**才 400，补全 body 后仍测原目标；另 1 条
> `/update` 因全 `,optional` **不反转**，留作对照组）。

> **⚠️ 本次同时证伪了 §3.4.3 的 N-3 与 N-6 两条「有意偏离」留档** —— 它们描述的
> RAG 行为**从未存在过**，观测行为其实一直与 Go 一致：
>
> | 留档声称 | 实际代码 | 实际观测 | Go |
> |---|---|---|---|
> | N-3：「RAG 在 `AdminApiSupport.parseUid` **提前拦截 uid=0** → 1017」 | `parseUid` 只拒 null / 空串 / 非数字 / 溢出（`AdminUidParseTest` 5 条），**没有 uid==0 分支**；全仓 grep 零命中 | `uid=0` → 透传 rpc → 查无此人 → **`1003`** | **同样是 `1003`** ⇒ **本就一致** |
> | N-6：「`uid=abc` 经过 `parseUid` 拦截 → 200 + `1017` envelope」 | `GetUserReq.uid` 是 `Long` 且 admin-api **无自定义 ObjectMapper** ⇒ Jackson 在反序列化阶段就抛 `InvalidFormatException`，**到不了 `parseUid`** | `uid=abc` → **`400` + text/plain**（文本来自 Jackson） | **同样 `400` + text/plain**（文本来自 go-zero）⇒ **状态码本就一致**，仅文本不同 |
>
> ⇒ **N-3 与 N-6 应从「有意偏离」降级为「留档与代码不符，已修正」**。
> 这不是改行为，是**改记录**——按 §1「Go 是唯一真源」，RAG 本来就对，
> 是留档把「本该对齐」写成了「有意偏离」，下一个审计 agent 会据此
> **主动把 RAG 改坏**去"恢复"一个从未存在的偏离。防回退测试已同步反转，
> 见 `AdminApiMvcPipelineTest#uidZeroIsPassedThroughAndMatchesGo` 与
> `#nonNumericUidIsRejectedByJacksonNotByRequiredCheck`。

> **残留的已知差异（不修，留档）**：`GetUserListReq.keyword` 在 Go 侧走 **form 通道**，
> 而 `rest/httpx/util.go:37-43` 的 `GetFormValues` 会把空值（`len(v) == 0`）整个丢弃，
> 于是 `keyword=` 退化成「缺席」⇒ 400；RAG 的 keyword 在 JSON body 里，`keyword=""`
> 会被**接受**。两边是否 400 取决于传输通道，而传输通道差异是 §3.4.1 已签字的 wire 契约偏离。
> **刻意不为单个字段引入第二套空串规则** —— DRIFT-010 的教训是
> 「安全规则的第二份实现本身就是缺陷来源」。

##### DRIFT-NEW-015 的处置：**已定案（用户 2026-10-21 拍板「直接把 Flyway 丢掉」）· 已实施**

**方案 A（保留 Flyway + 单一迁移拥有者）曾被实现并验证通过**，两个 rpc 各自对着空库
都能应用完整的 4 份迁移。但用户最终选择更彻底的方案 —— 见下面的取舍说明。

最终落地：

| 项 | 做法 |
|---|---|
| DDL 位置 | 4 份全部收归 `users-common/src/main/resources/db/`：`01_create_user.sql` / `02_create_user_avatar_history.sql` / `03_create_admin.sql` / `04_create_admin_invite.sql`。数字前缀**只保证执行顺序，不表示版本** |
| 执行者 | `users-common` 的 `com.rag.users.common.ddl.SchemaInitializer`（静态工具，非 `@Component` —— 两个 rpc 只扫自己的包，且注释里明确禁止扩 `scanBasePackages`）；由各模块的 `bootstrap/UserSchemaBootstrap` / `AdminSchemaBootstrap` 在 `@PostConstruct` 调用 |
| 失败语义 | 任一 DDL 失败 → 抛 `IllegalStateException` → **进程拒绝启动**（对齐 Go 的 `log.Fatalln`） |
| 时序 | `AdminSeederBootstrap` 显式 `@DependsOn("adminSchemaBootstrap")`。原先后序靠「MyBatis-Plus detector → SqlSessionTemplate → flywayInitializer」这条**传递**链兜住，去掉 Flyway 后整条链消失，必须点名 |
| pom | 两个 rpc 的 `spring-boot-flyway` + `flyway-core` + `flyway-database-postgresql` **三件套全删**。⚠️ 只删 `flyway-core` 不够：`spring-boot-flyway` 在 classpath 上就会让 `FlywayAutoConfiguration` 自动 migrate，然后报 `Found non-empty schema(s) "public" but no schema history table.`（本次实测撞过） |
| 验证 | `UserRpcContainerWiringTest` 12 条 + `AdminRpcContainerWiringTest` 7 条全绿；两份日志里 `schema init` 各打印 4 份脚本全部 applied；整份构建日志 `flyway` 出现 **0** 次 |

##### ⚠️ 丢掉账本丢掉的东西：PG 的 `CREATE TABLE IF NOT EXISTS` **在并发下并不安全**

这是去掉 Flyway 后**必须自己补上**的一层，也是本轮唯一一处「单元测试全绿、端到端才炸」的问题。

实测现象（2026-10-21 双端对拍）：user-rpc 与 admin-rpc **同时**启动、各自对同一个空库
执行 `CREATE TABLE IF NOT EXISTS users_user`，后到者直接失败：

```
org.springframework.jdbc.datasource.init.ScriptStatementFailedException:
  Failed to execute SQL script statement #1 of db/01_create_user.sql
Caused by: org.postgresql.util.PSQLException:
  ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
```

**为什么单测测不到**：`UserRpcContainerWiringTest` / `AdminRpcContainerWiringTest`
每次只起一个服务进程，并发窗口根本不存在。
只有 `tools/difftest` 同时起两套栈才会复现 —— 这正是双端对拍存在的意义。

**修法**：`SchemaInitializer.applyAll` 在跑 DDL 前先取 **PG 会话级 advisory lock**
（`select pg_advisory_lock(8675309)`），跑完显式 `pg_advisory_unlock`。
阻塞语义、不需要额外表、不需要事务、连接断开自动释放 —— 比模拟 Flyway 的行锁轻得多。
键是 `SchemaInitializer` 里的常量，**两个模块必须用同一个**。

> Flyway 原本用 schema history 上的行锁天然挡住了这一层。去掉它就等于去掉这层保护，
> 这是「丢掉有状态账本」的代价之一 —— 与 §6.3 问题 1 记的「失去升级路径」并列。

##### 「为什么 Go 没有这个问题」—— 准确答案（别再问第二遍）

这是本轮被问到的问题，实测 + 读源码后的结论是：**要分成三件事，别混谈。**

| 问题 | Go 有没有 | 依据 |
|---|---|---|
| **① 账本撞车（Flyway 特有）** | **结构性没有** | GORM `AutoMigrate` 无状态：没有版本号、没有 `flyway_schema_history`、没有 checksum、没有 `baseline`。DRIFT-NEW-015 是**本仓库自己引入的复杂度造成的**，不是 PG 或 Go 的固有问题 |
| **② PG 的 DDL 并发竞态** | **有，只是窗口窄得多** | GORM 的建表 SQL **没有** `IF NOT EXISTS`（`migrator.go:309` 就是 `tx.Exec("CREATE TABLE ? (...")`），保护只来自 `HasTable` 先查（`migrator.go:124` 的 check-then-act）。并发失败形态会是 `relation already exists`，与 RAG 的 `pg_type_typname_nsp_index` 不同 |
| **③ 真实部署里两个服务同时对空库启动** | **两边都不发生** | Go 与 RAG 的 `docker-compose` **都只有基建、没有应用服务**（Go 侧连 user-rpc / admin-rpc 都没有）。首次初始化通常由单个服务完成 |

**② 的实测**：把 Go 的 user-rpc 与 admin-rpc **同时**对着一个全新空库启动（刻意不 sleep 错开），
连续 5 轮全部成功，4 张表齐活（`users` / `user_avatar_history` / `admins` / `admin_invites`）。
脚本见 `tools/difftest/exp-go-concurrent-automigrate.sh`。

**为什么 Go 能扛住（推测，未进一步验证）**：两个服务的模型顺序不同 ——
user-rpc 是 `AutoMigrate(&User{}, &UserAvatarHistory{})`，**第一个就碰 `users`**；
admin-rpc 是 `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`，**`users` 排第三**。
admin-rpc 要先建完 `admins` + `admin_invites` 才轮到 `users`，
那时 user-rpc 早已建好、它的 `HasTable(users)` 返回 true 直接跳过。
**靠的是时序侥幸，不是机制。**

> ⚠️ 所以 RAG 这边加的 advisory lock **比 Go 更强**，不是多余的：
> Go 那个是「时序恰好错开」，RAG 若照抄同样依赖错开，一旦模型顺序或启动时序变化就会炸。
> 锁是机制，不靠运气 —— 这条要守住。

**为什么丢而不是留**（用户的取舍）：项目仍在重构期、**无任何已部署环境**
（`docker-compose.yaml` 里连 admin-rpc 都没有），表结构还会继续动 ——
升级路径的价值要等到第一次 `ALTER` 才兑现，而那时只有一张库要改，代价可控。
更接近 Go（Go 用的是无状态 `AutoMigrate`），且去掉了一整类约束
（版本号唯一、checksum 稳定性、跨进程状态）。

**已知代价（第一次 `ALTER` 之前可以无视）**：脚本要同时承担「建表」与「升级」两件事，
只能到处加 `ADD COLUMN IF NOT EXISTS`；也没有任何地方记录「这张表现在该长什么样」。
**第一次 `ALTER` 之前必须重新引入版本化机制。**

被否决的两个方向（记录下来，避免下一个 agent 重提）：

| 方案 | 为什么没选 |
|---|---|
| **保留 Flyway + 单一迁移拥有者** | 技术上可行且已验证，但保留了「有状态账本」这一整类约束。**已被用户否决** |
| **拆库 + 跨域 RPC** | 直接违反设计意图（Go 两个服务都直连同一库建 `users_user`），改动面最大 |

##### ⚠️ 已被实证证伪的「最省事方案」（别再试第二次）

看上去最该先试的是「给 admin-rpc 加两个 Flyway 开关，让它容忍 user-rpc 的账本」：

```bash
--spring.flyway.validate-on-migrate=false --spring.flyway.out-of-order=true
```

**它行不通，而且会让账本更脏。** `tools/difftest/exp-drift015-flyway-flags.sh` 用真 jar +
真 PG 16 跑过一遍，实测终态：

```
账本 = [ 1|create user,  2|create user avatar history,  3|create user ]
        ↑ user-rpc 写的              ↑ user-rpc 写的         ↑ admin-rpc 的 V3（与第 1 行语义重复）
✗ admin_admin   缺失   ← V1__create_admin.sql        根本没跑
✗ admin_invite  缺失   ← V2__create_admin_invite.sql  根本没跑
admin-rpc 启动失败：relation "admin_admin" does not exist
```

机理：Flyway 只应用「pending」的迁移。admin-rpc 的 V1/V2 版本号已被 user-rpc 占用，
于是被判定为**已应用而静默跳过** —— 校验开关只关掉 checksum 检查，管不了这一步。
结果 admin 域两张核心表永远建不出来，`AdminSeederBootstrap`（`@PostConstruct`）起来就炸。
更糟的是账本里多了一条 `version 3 = create user`，把 schema 永久钉在错误版本上，
日后想正修还得先人工清账本。

> **教训**：这正是 §3.3 说的「推理出的机制风险必须配一条能真触发的验证去证实或证伪」。
> 本条最初只是推理（"两个开关应该能糊过去"），实测直接推翻。

> 在用户拍板前，**不得**由单个 agent 自行选一条实施 —— 这正是 §3.4.2 纪律针对的情形：
> 「按政策应当对齐、但对齐会让 RAG 变差」的真实张力。
> 对拍 harness 里那条绕法已经在 `initdb.sql` 的注释里写明是权宜之计，不要当成既定结论。

##### 附带发现：冻结 Go 仓库的配置占位符不生效（**不改，只留档**）

`Sea-RideTheWind_golang/service/user/user/rpc/etc/user.yaml:13` 写的是
`Password: "${POSTGRES_PASSWORD}"`，`admin/rpc/etc/admin.yaml:14` 的 `DataSource`
更是整条 DSN 都用 `${POSTGRES_USER}:${POSTGRES_PASSWORD}@${POSTGRES_ADDR}:...`。
但四个入口都是 `conf.MustLoad(*configFile, &c)`（`user/rpc/user.go:29`、
`admin/rpc/admin.go:30` 等），**没有传 `conf.UseEnv()`**；
而 go-zero v1.10.2 只在 `opt.env` 为真时才展开（`core/conf/config.go:62-63`
的 `os.ExpandEnv`、`core/conf/properties.go:58-59` 同理）。

⇒ **这些占位符不会被展开**，密码会被当成字面量字符串 `${POSTGRES_PASSWORD}`，
SASL 认证必然失败。真源已冻结（§2 不接受写入），**只在 `tools/difftest/conf/go/*.yaml`
的副本里换成了字面值**。

记这一条是因为：**任何「起 Go 进程跑一遍」的验证都会先撞上它**，
而报错信息是 `password authentication failed`，看起来像密码写错了，不像配置没展开 ——
很容易被误诊成对拍脚本的问题。

### 6.2.1 本次从本表移出（已修或已定案）

| 原优先级 | 项 | 状态 |
|---|---|---|
| ~~P0~~ | ~~**B-1：rpc 超时无法取消在途 IO**~~ | ✅ **2026-10-21 已关闭**（issue #71187373 实验 → #71187874 实施） | 三层证据：① 纯 JDBC 层 `probe-jdbc-cancel.java` 证明 **interrupt 对等锁查询零效果**（照常返回、11014ms）、**`setQueryTimeout` 有效**（2034ms 抛 `SQLState=57014`，与 Go ctx cancel 同一条服务端语义且连接保可用）；② 同日同场端到端；③ 手段定性（`socketTimeout` 会让连接报废，语义相反，不可用）。**实施**：`DeadlineQueryTimeoutInjector`（按 `DeadlineState.remainingMillis()` 注入）+ `SqlTimeoutStatusTranslator`（57014→DEADLINE_EXCEEDED）+ 46 处 `catch (StatusRuntimeException) { throw e; }`（user-rpc 17 + admin-rpc 29）。**api 层零改动** —— `DEADLINE_EXCEEDED` 本就落 `mapRPCStatusToCode` 的 default → 1015。**结果**：客户端 3170ms→**2223ms**（Go 2208ms）、响应体 `text/plain`→**`{"code":1015}`**、PG 查询滞留 4303ms→**176ms**（Go 182ms）；difftest 39/39 unexpected=0；**admin 域另跑两轮**（RAG 2544ms/179ms vs Go 2181ms/176ms，同样对齐）；全仓 383→**421 条** 0 failures，其中 6 条是**扫描字节码异常表的全量守恒元测试**（`UserDomainServiceCatchStructureTest` / `AdminDomainServiceCatchStructureTest`）—— 46 处放行分支由「逐点人工读」升级为「机器守恒」，变异测试已实证能变红。⚠️ 过程中发现更大前置缺陷 —— gRPC `ServerInterceptor` 从未挂到 server 上，连带 **DRIFT-018 的 24 处 `checkDeadline()` 此前全是死代码**（见上一行二次更正）。详见 §3.4.1「B-1 补记」 |
| ~~P0~~ | admin-api 默认配置下起不来（`WeakKeyException`） | ✅ **已修**（commit `71be288`）。`AdminJwtAuthInterceptor.java:67` 与 `JwtAuthInterceptor.java:76` 均已走 `JwtUtil.padToHmacKeySize(...)`；`JwtUtil.java:44` 已是 `public static`——即原表建议的「提成 `users-common` public 方法」方案已实施。**本行此前一直挂在 P0 未修，是过期信息** |
| ~~P2~~ | user-api 4 项清理 | ⚠️ **半修**。① 4 个端点 `@RequestBody(required=false)` **已完成**（`UserController.java:118/126/137/153`）；② 「删 `codeFromRPCError` 死代码」**未完成，且原文把位置写错**——它在 **admin-api**（`AdminApiSupport.java:66`），不在 user-api。剩余部分已并入本表 P3 |
| ~~P3~~ | Flyway 迁移归属 | ✅ **已定案** → §6.3 问题 1 |
| ~~P3~~ | `admin-rpc` 边界缺失 | ✅ **已定案**（不转发，与 Go 一致）→ §6.3 问题 2 |

### 6.2.6 `service/users` 的**证据边界台账**（2026-10-21 新增）

§3.4.4 末尾那条新纪律管的是「留档声称 RAG 的行为是 X ⇒ 必须有断言 X 的运行测试」。
**本节管的是它的反面：证据缺口本身必须台账化。**
动机很具体 —— 一个 agent 跑完 `tools/difftest` 看到「39/39 全绿」，
若不知道下面第 1 条，就会以为 `service/users` 已经验证完了。

**不要把本节的条目当成「已知缺陷」读**：这里列的绝大多数**不是代码写错了**，
而是「目前还没有能证明它对（或不对）的运行证据」。

| # | 缺口 | 现状 | 要补什么 |
|---|---|---|---|
| **E-1** | **difftest 10/39 步只比状态码、不比 body** （历史轨迹：2026-10-21 由 13 → 12（b3066ab） → 10 → **0（2026-10-22 关闭）**） | ✅ **已关（2026-10-22）**。做法**不是**把两侧 body 改成逐字相同 —— 那 10 步**注定**不同：N-1 七条是 Go 自身两处 401 body 就互不一致（空 body vs `{code:401}` 无 data），DRIFT-006 三条的正文来自两个框架各自的 JSON 解析层。**真正缺的不是「比成一样」，而是「body 连看都没看」** —— 旧写法只断言 `gs == rs`，于是「RAG 把 401 改成 200 空 envelope」「RAG 的 400 变成 JSON」这类回归也能一路绿灯。现改为 `BODY_EXPECTATION` + `check_body_expectation()`：**分别断言两侧各自该是什么样**（N-1：两侧 status 均 401；Go body 必须命中它自己的两种形态之一；RAG 必须是 `{code,msg,data}` 三键齐全且业务码 1006）（DRIFT-006：两侧 status 均 400；两侧 Content-Type 均含 text/plain；两侧 body 均非空裸文本）。`http()` 相应改为返回 `(status, text, resp_headers)` —— 不取响应头就无法断言「形状」。统计口径同步改为 `asserted=10 / byte_equal=29`，**两个数分开报**，避免把「靠断言通过」误读成「两侧 body 逐字相同」。**变异测试**：把 RAG 的 N-1 envelope 拆掉 / 把 400 改回 JSON，10 条全部变红。 **⚠️ 关闭 E-1 当场抓出一条此前不可见的真实漂移**：详见 §3.3 新增的「代码里写了 ≠ 客户端收到」规则 —— RAG 所有 text/plain 400 都**丢 Content-Type 与 nosniff**（真实 Tomcat 证据），根因是 `HttpMiddleware$ObservabilityResponseWriter.setHeader` 只进 buffer、等 `flushTo` 才写回，而那时底层已 committed。**已修**（三个覆写点改实时 propagate）。守门脚本 `tools/difftest/check-plaintext400-headers.sh` —— **必须真实 Tomcat，MockMvc 测不到**（MockMvc 里 Content-Type 完好，会假绿）。**留档一个 SCENARIOS 排序的副作用**：admin.update 在第 28 步把 admin 密码改成 `newadmin123`，**同 cluster 第二次跑** admin_token-using 步骤会因密码不匹配而全 DIFF （本体已从 stale 状态隔离，**留作 SCENARIOS 排序整治一项**） | |
| **E-2** | **gRPC 层没有直接对拍** | **✅ 已关（2026-10-21）**。拆为两段同时收口：**静态半**（字段号 / message 名）—— Go 真源 `proto/{user,admin}.proto` 与 RAG `*-proto/.../{users,admin}.proto` 字节级对齐，`tools/difftest/check-proto-static.sh` 三秒跑完；**动态半**（错误码语义）—— 同 stub 双端口打 4 档 RPC（`OK` 注册新号 / `INVALID_ARGUMENT` logout 空 token / `NOT_FOUND` getUser 鬼 uid / `ALREADY_EXISTS` 注册重名），4/4 双端口 `Status.getCode()` 一致（首测结果见 `tools/difftest/grpc-results.txt`）。**留档注意点**：Login 三种业务失败（用户不存在 / 密码错 / 被禁）**不**抛 gRPC code、走 `LoginResp.Status = 1|2`，是 go-zero 业务级状态码设计；本实验因此特意不把 login 列进 4 档，避免误把业务码当 wire code 测 |
| **E-3** | **39 步不是状态空间穷举** | ✅ **已关（2026-10-21）**，**5 个根因全部已修（2026-10-22，用户逐条签字）**。`tools/difftest/matrix.py` 把 9 个 DTO 按**字段**展开边界组合，共 **273 条**（原 39 步只覆盖每端点 1~3 条典型路径）。首轮产出 **77 条 NEW_DRIFT**，UNCLASSIFIED = 0。**根因见 §3.4.5**：R1 Jackson 标量强转与截断 64 条、R2 显式 null ≠ 缺席 4 条、R3 DDL `text` vs `varchar(255)` 4 条、R4 GORM 静默抑制负 OFFSET/LIMIT 5 条，**R5 bcrypt 抢在 invite/username 检查之前 2 条（修复过程中新暴露，曾被误分类进 R3）**。**端到端复测：NEW_DRIFT 77 → 0**（R1/R2/R4/R5 均 0；R3 的 0 由 `r5-order-probe.py` 独立佐证，因为 `classify()` 那条宽规则仍可能误分类）。**已验证与既有 difftest 共存**：全新 cluster 上先跑矩阵再跑 `diff.py`，仍是 **39/39 unexpected=0**。配套静态证据 `tools/difftest/check-ddl-static.py`（列类型差异已从 22 处降到 12 处）。**⚠️ 工具本身的坑已记在脚本头**：① token 不可跨端复用（N-5）② method/参数通道必须按端声明（A-03 + `form:` tag）③ 字段来源必须是真源 `types.go` 而非手抄 ④ **`--no-build` 不拷新 jar，起的是旧副本** ⑤ **`admin.create` 恒用假邀请码 ⇒ 看不到「邀请码有效」分支**，R5 即由此漏掉，补齐靠 `tools/difftest/r5-order-probe.py`（用真邀请码，实测出比矩阵多一倍的 2 处分歧） |
| **E-4** | **超时路径 —— 已于 2026-10-21 实测坐实，见下方专节** | ~~零覆盖~~ → 已有数据。**这是 B-1 的运行证据**，此前只有静态分析 | ✅ 已补（`exp-b1-timeout-cancel.sh` 端到端 + `probe-jdbc-cancel.java` 纯 JDBC 机制层，三层拆解见 §3.4.1「B-1 补记」） |
| **E-5** | **并发路径零覆盖** | ✅ **已关（2026-10-21）**。admin-rpc 侧 `AdminRpcConcurrencyWiringTest`（真 PG + Testcontainers，2 条全过）针对 `AdminCreateAtomicService` 双场景：(a) 同一 invite code N=8 并发消费 ⇒ **恰好 1 OK + 7 InviteUnavailable**（W1 悲观锁 `SELECT ... FOR UPDATE` 实证有效），admin 表里 `username LIKE 'u-%'` 严格 1 行；(b) 同一 username N=8 并发注册（各用唯一 invite）⇒ **恰好 1 INSERT 成功 + 7 撞 unique**，最终严格 1 行。Go 一致性靠「同样策略（行锁/UNIQUE 兜底）+ 同样约束」按设计而非按端到端对拍；要做端到端对拍那是 E-2 的范围 |
| **E-6** | **事务异常回滚只测了 `user-rpc`** | ✅ **已关（2026-10-21）**。`AdminRpcTransactionWiringTest`（真 PG + Testcontainers，4 条全过）在 admin-rpc 侧同款补齐 `AdminCreateAtomicService.createAdminAtomic` 4 步事务的回滚行为：(1) W3 admin uid 唯一冲突 ⇒ invite `used_at`/`used_by_uid` 双 null（rollback 实证）；(2) W1 invite 不存在 ⇒ admin_admin 表行数无变化；(3) W2 username 已存在 ⇒ invite 不被标 used；(4) happy path ⇒ invite 被正确标 used，admin 行被插入。user 侧 `AvatarTransactionWiringTest` + admin 侧本类 ⇒ 两端写路径回滚都有真 PG 实证。**service/common 的 20 项偏差**这条不在 E-6 范围,留作另一笔 |

##### E-4 的实测结果（2026-10-21，`exp-b1-timeout-cancel.sh`）

**造阻塞的方式**：不用 `pg_sleep`（需要能执行任意 SQL 的端点，两栈都没有），
而是用 **`LOCK TABLE ... IN ACCESS EXCLUSIVE MODE`** —— 起一个后台事务独占整张表，
两端的 `user.login`（`SELECT ... WHERE username=?`）就阻塞在 PG 上。
这是真实的长事务形态，且不碰任何代码。

> **⚠️ 第一版实验失败了一次，值得记下来**：最初用的是 `SELECT ... FOR UPDATE` 行锁，
> **两侧 login 都在 150ms 内成功返回了**，实验毫无意义。
> 原因是 **PostgreSQL 的普通 SELECT 走 MVCC 快照读，不会被 `FOR UPDATE` 持有的行锁挡住** ——
> 只有 `FOR UPDATE`/`FOR SHARE`/`UPDATE`/`DELETE` 才等行锁。
> 必须用**表级 ACCESS EXCLUSIVE 锁**才能挡住读。
> 同一次还踩了两个：`psql "SQL"` 会把 SQL 当成 **dbname**（静默失败、输出空），
> 必须走 `-c`；Go 库的表名是 `users`（GORM 默认名）而 RAG 是 `users_user`（§3.4 wire 契约偏离）。

**数据**（持锁 12s；两侧各自的用户表上各挂一个 ACCESS EXCLUSIVE 事务）。
**以下是 21:03 同日同场连跑「基线 + 注入」两组的复测值**；首轮曾测得 Go `2035ms/378ms`、
RAG `3047ms/4455ms`，量级一致、可复现，**但以复测值为准**：

| | 客户端返回耗时 | 响应体 | **客户端返回后，PG 里的阻塞查询又存活** |
|---|---|---|---|
| **Go**（基线） | 2190ms | `200 + {"code":1015,"msg":"服务繁忙"}` | 203ms |
| RAG 基线 | 3174ms | `Request Timeout`（`text/plain`） | **4657ms** |
| RAG 注入静态 `default-statement-timeout=2` | 2449ms | `{"code":5001,"msg":"系统内部错误"}` | 193ms |
| **RAG 生产方案实施后** | **2223ms** | **`{"code":1015,"msg":"服务繁忙"}`** | **176ms** |
| Go（同场对照） | 2208ms | `{"code":1015,"msg":"服务繁忙"}` | 182ms |

**admin 域同样跑了四轮（2026-10-21 扩展，user 域两轮 + admin 域两轮）**：

| | 客户端返回 | 响应体 | PG 查询又存活 |
|---|---|---|---|
| Go / user | 2236ms | `{"code":1015,"msg":"服务繁忙"}` | 161ms |
| **RAG / user** | 2294ms | 同上 | 191ms |
| Go / admin | 2181ms | 同上 | 176ms |
| **RAG / admin** | 2544ms | 同上 | **179ms** |

⇒ **admin-rpc 的 29 处放行分支 + 13 处 `checkDeadline()` 也拿到了端到端证据**，
不再只是「user 侧全绿所以推断 admin 侧也成立」。admin 域 RAG 与 Go 的查询滞留差 3ms。

> ⚠️ 第三行那个 `{"code":5001}` 曾经让人差点选错方案：它说明**只注入超时而不做异常映射**，
> 会把 `text/plain` 差异换成一个**离 Go 更远**的差异。
>
> **第四行是最终结果（issue #71187874 实施后）**：三项判据与 Go 逐字对齐 ——
> 耗时差 15ms、查询滞留差 6ms、响应体完全相同；`difftest` 39 步仍 39/39、unexpected=0。
> 三层拆解（纯 JDBC / 端到端 / 手段定性）与生产方案的组件清单见 §3.4.1「B-1 补记」。

> 第三行是 B-1 缓解实验，**该机制的三层拆解（纯 JDBC / 端到端 / 手段定性）见 §3.4.1「B-1 补记」**。
> 一句话：注入组把 RAG 的「查询又存活 4657ms」压到 193ms，与 Go 同量级；
> 但错误体从 `text/plain` 变成了 `5001`，**离 Go 更远** ⇒ 生产方案不能只做注入，还必须做异常映射。
>
> **⚠️ 这张表读起来最刺眼的一行是「注入组 ⇒ 5001」**：那正是**差点选错方案**的地方。
> 若当时只按「资源占用解决了」收工，RAG 的超时响应体会长期停在 5001，
> 而 5001 在 Go 侧表示**数据库故障**、不表示超时 —— 等于用一个新的 wire 差异替换掉旧的。
> 生产方案（第 4 行）之所以要做两半，根因就在这里。

**观测方法的坑（2026-10-21 记录，值得单独记）**：探针最早只统计
`count(*) WHERE wait_event_type='Lock'`，并在 count 掉到 0 时判定「查询消失」。
这个判据太弱 —— 一次实验就撞出「客户端 2121ms 返回 / 查询 398ms 消失 / worker 却 6860ms 后结束」
的**伪矛盾**。真相是日志里 `business_info` 那一行属于**上一个请求**（[2/4] 阶段的注册），
不是本次 login。**现在探针改为打印完整明细 `pid:state:wait_event_type:query_start` 并逐次打时间戳**，
且新增「worker 业务起止时刻」一节与该时间轴对齐 —— 客户端返回 / PG 查询消失 / worker 退出
是三件不同的事，**混在一起量必然读错**。

**判读（三个可观测差异，同一个根因）**：

1. **② PG 查询存活时长 = B-1 的核心**。Go 的 ctx cancel 触达 PG、查询立刻消失（203ms）；
   RAG 的 `cancel(true)` 发不到 JDBC，查询一直挂到持锁事务释放（4657ms）。
   静态分析预言的「Java 可能占用到 10s」，实测是「**客户端已返回超时、底层连接仍被占 4.7 秒**」。
   复测还额外确认：**worker 比客户端返回晚 4.34s 才退出** —— 线程池里这段时间一直占着。

2. **① 客户端感知时延差 ~1 秒，且是同一个根因的另一个面**。
   Go：内层 rpc 2s 超时 → ctx cancel 真的让 PG 返回 → 外层立刻收到 → 总计 2190ms。
   RAG：内层 2s 的 `cancel(true)` **外层感知不到**，拖到自己的 http-timeout 3s 才返回（3174ms）。
   ⇒ **内层超时不生效 ⇒ 外层多等 1 秒**，不是两个独立问题。
   ⚠️ 注入组反向印证：一旦内层 SQL 真在 2s 被取消，RAG 客户端耗时从 3174ms **回落到 2449ms**，
   差值消失 —— 这条判读被实验闭环了。

3. **③ 响应体形状不同**（Go `200 + envelope{code:1015}` vs RAG `text/plain`）。
   这是 ① 的下游后果，但**它是一个独立的 wire 差异，且此前从未被对拍过**
   （difftest 的 39 步里没有任何超时场景）。**新发现，尚未定性** ——
   需先确认 `HttpMiddleware` 的超时应不应该对齐 Go 的 envelope 表达，再决定改还是留档。

   > **⚠️ 定性时必须连同注入组一起看，否则会选错方案**。三种形态实测如下，
   > 它们**不是同一个问题的三种写法**，而是两条不同路径的产物：
   >
   > | 场景 | 响应体 | 由哪条路径产出 |
   > |---|---|---|
   > | Go | `{"code":1015,"msg":"服务繁忙"}` | rpc 超时 → ctx cancel |
   > | RAG 基线 | `text/plain "Request Timeout"` | `HttpMiddleware` 超时分支 |
   > | RAG 注入 `setQueryTimeout` | `{"code":5001,"msg":"系统内部错误"}` | SQL 抛 57014 → 被 `catch (Exception e)` 吞成 `Status.INTERNAL` |
   >
   > ⇒ **只做「注入超时」这一个动作，会把这个 wire 差异换成另一个，而且离 Go 更远。**
   > 任何 B-1 缓解方案都必须同时处理 57014 → `DEADLINE_EXCEEDED` 的映射。

**复现**：`cd tools/difftest && ./exp-b1-timeout-cancel.sh`（自带 `--serve-only` 起栈与收摊）。

**机制侧的字节码佐证**（比黑盒更精确）：对数据访问链路逐层 `javap -c` 数 `isInterrupted` 命中 ——
**MyBatis `BaseExecutor` 0 次 / `SimpleExecutor` 0 次 / pgjdbc 42.6.2 `QueryExecutorImpl` 0 次**。
即这条不是「框架大概不响应 interrupt」的推测，而是**整条路径上没有任何一层检查中断标志**。
注意「RAG 侧 MyBatis-Plus」这个上层 ORM 换成什么都不会改变结论 ——
**只要底层还是 JDBC socket，就是同一个限制**。

**⚠️ 但字节码仍是「静态证据」，2026-10-21 已补上行为级证据**（`tools/difftest/probe-jdbc-cancel.java`）：
绕开 HTTP / gRPC / MyBatis 直连 PG，用 `ACCESS EXCLUSIVE` 锁造阻塞，
在查询跑到 1000ms 时对该线程 `interrupt()`：

| mode | 结果 | 耗时 |
|---|---|---|
| `none` | ✅ 正常返回（阻塞到锁释放） | 10022ms |
| `interrupt` | ✅ **正常返回 —— interrupt 零效果** | 11014ms |

⇒ `interrupt` 组比 `none` 组**还慢 1s**（interrupt 就发生在 1000ms 处），
说明它连「让查询稍早返回」都没做到。**这才是 B-1 的直接运行证据**，
字节码只是佐证。复现：`cd tools/difftest && java -cp <pgjdbc> probe-jdbc-cancel.java interrupt`（约 10s）。

> **这三条为什么是「缺口」而不是「缺陷」**：E-4 的根因 B-1 是**已知且已签字**的框架限制
> （Java 线程 interrupt 不被 JDBC / grpc-java / HttpClient 响应），它需要的是**业务侧主动响应**
> 而不是「改代码对齐」；E-5 / E-6 缺的是证据，代码本身可能已经是对的。
> **三者都不能靠静态审查关闭** —— §3.3 的纪律同样适用于此。
>
> **优先次序建议（2026-10-21 更新）**：E-4 **已完成**（纯 JDBC 机制层 + 同日同场端到端 + 手段定性，
> 且已据此实施修复并消除 B-1，见 §3.4.1「B-1 补记」）⇒ 下一位是 **E-2**
> （§3.1 硬性要求项：proto 字段号 / message 名称 / 错误码语义）→ E-5 / E-6 → E-1 的清理 → E-3。
> ⚠️ E-4 的经历给 E-2 留了一条现成的做法：**不要只做 HTTP 层**，本轮的 B-1 正是
> 「HTTP 全绿 + gRPC 链路从未执行」才暴露出来的。

### 6.3 架构决策（问题 1、2 已于 2026-10-21 拍板；问题 3 为新增留档）

**✅ 问题 1：建表机制 —— 2026-10-21 用户拍板「直接把 Flyway 丢掉」。**（原为待定，2026-10-20 曾定案为「保留 Flyway + 全局版本号」，该结论已被推翻）

| 表 | 谁建 |
|---|---|
| `users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite` | **user-rpc 与 admin-rpc 都会建**（幂等，谁先启动都一样） |
| message 三张表 | **message-rpc 仍用 Flyway**（本次未动，见下方范围说明） |

执行要点：

1. **4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`**，编号前缀（`01_`…`04_`）
   只保证确定性执行顺序，**不表示版本**。
2. `users-common` 的 `com.rag.users.common.ddl.SchemaInitializer` 用 `ScriptUtils` 原样重放；
   user-rpc / admin-rpc 各自的 `bootstrap/UserSchemaBootstrap` / `AdminSchemaBootstrap`
   在 `@PostConstruct` 里调用，任一 DDL 失败即启动失败（对齐 Go 的 `log.Fatalln`）。
3. `AdminSeederBootstrap` 显式 `@DependsOn("adminSchemaBootstrap")`。
4. 两个 rpc 的 `spring-boot-flyway` / `flyway-core` / `flyway-database-postgresql`
   **三件套全部从 pom 移除** —— 只删 `flyway-core` 是不够的，`spring-boot-flyway`
   在 classpath 上就会让 `FlywayAutoConfiguration` 按默认值自动 migrate。

Go 侧证据：`user/rpc/internal/model/db.go:33` = `AutoMigrate(&User{}, &UserAvatarHistory{})`，`admin/rpc/internal/model/db.go:15` = `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— **Go 两个服务都建 `users_user`**，
Go 之所以无冲突，正因为 `AutoMigrate` 无状态且幂等。丢掉 Flyway 之后 Java 侧回到同一条路上。

**⚠️ 已知代价（第一次 `ALTER` 之前可以无视）**：脚本要同时承担「建表」与「升级」两件事，
只能到处加 `ADD COLUMN IF NOT EXISTS`；也没有任何地方记录「这张表现在该长什么样」。
**第一次 `ALTER` 之前必须重新引入版本化机制。**

**⚠️ 本问题的实际范围比原记录更宽**：`message-rpc` **仍在用 Flyway**，本次未动。
它与 users 域共用同一个 `first_db`，一旦 users 域也留下 Flyway 账本就会撞号 ——
现在 users 域已经没有账本了，所以 message-rpc 暂时安全。
若日后 message 域要接入 users 域的数据、或想让 message-rpc 也去掉 Flyway，
按同一套 `SchemaInitializer` 模式处理即可。

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷。**（原为待定）

实测证据：

- `admin/rpc` 全仓 `UserRpc` / `userpb` / `UserService` **零命中**；`ServiceContext` 只有 `Config` / `AdminModel` / `BizRedis` —— **零出向 gRPC 依赖**
- 7 个用户管控 RPC（`BanUser` / `UnBanUser` / `UpdateUser` / `DeleteUser` / `ResetUserPassword` / `GetUser` / `GetUserList`）全是 `internal/model/admin.go` 的直接 SQL
- Go 两份 `User` struct（`user/rpc` 与 `admin/rpc` 的 `internal/model/data_model.go`）**逐字一致** —— Go 自己就有两份同样的表定义

**⚠️ 原记录的前提有误，已更正**：原文称「Go 把『读身份』边界建好了，只漏了『管理用户数据』」。实测 Go 的 gRPC 边界是 **api→rpc**：`identity.UserReader`（`user/identity/resolve.go:60-62`）签名带 `grpc.CallOption`（只能被 gRPC stub 满足），但调用方只有 `user/api`（`svcCtx.UserRpc = userservice.NewUserService(zrpc.MustNewClient(...))`）与 `knowledge/api` —— **都是 api**。Go 里**不存在 rpc→rpc 的用户数据边界**，「读身份」与「管用户数据」不是同一个轴。

**⚠️ 一条常见反对理由已被推翻**：`CreateAdmin` **不建 User**（只做锁邀请码 / 查重 / 插 `Admin` / 标记已用，见 `createadminlogic.go`），7 个管控 RPC 也都是单表独立操作，**无跨边界事务** —— 所以「转发会破坏 `CreateAdmin` 原子性」**不成立**。

若日后仍决定转发，属 §3.4 留档的有意偏离，须单独立 spec；代价是：admin-rpc **新增一个可用性依赖**（user-rpc 挂则管控挂，Go 无此耦合）+ 错误分类跨两层映射 + 7 个 RPC 多一跳延迟。

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包。**（2026-10-21 新增）

Go 侧每个 rpc 服务都在入口显式注册（`user/rpc/user.go:32,43`、`admin/rpc/admin.go:33,43`、`hot/rpc/hot.go:32,42` 三处一致）：

```go
rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)
s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
```

**Go 里不存在「全局自动注册」**。RAG 把 `@Component` 当自动注册，是一次**无出处 idiomatization**（§3.2）—— `UserRpcApplication` 的 `@Import` 只含 3 个 bean、`AdminRpcApplication` 连 `@Import` 都没有，两者**当时**均无 gRPC span / 无 deadline 传播 / 无 gRPC 超时。

> **2026-10-21 已实施并验证**：本问题已随 `row/common-fix-semantic-drift` 合并落地（见 §6.5）。`ObservabilityWiringTest` 用 `@SpringBootTest` 断言 `ServerInterceptor` 确实是容器里的 Bean —— 正是这条「自动注册」假设此前无任何测试覆盖，才让缺陷长期静默。`message-rpc` 归一到显式 `@Import` 仍**未做**，见 §3.4.1 效力边界。

定案（**2026-10-21 已实施**，见 §6.5）：`@Import` 数组补 `RpcServerInterceptorFactory.class`（`@Import` 本就接受普通 `@Component` 类，**零搬迁**）。连带结果：**不再有任何模块扫 `com.rag.common`** ⇒ servlet 类不会被拖进纯 gRPC 进程 ⇒ §12.5 曾提的 `spring/web/` + `spring/grpc/` 拆包**动机自我消解，已撤回**。`message-rpc` 现在能跑属**撞运气**（恰好有 `spring-boot-starter-web-server` 兜住），须一并归一到显式 `@Import`。

**📌 问题 4：Flyway 账本撞号 —— 问题 1 的结论只应用到了 1/4 的模块上。（2026-10-23 新增）**

问题 1 判定「丢掉 Flyway」的**唯一根因**是：`flyway_schema_history` 是 **schema 级**账本，一个 schema 只能有一本账；两个模块连同一个库、而 `V1/V2` 版本号相同内容不同时，后启动的那个会把自己的迁移**静默跳过**。

⚠️ **这条病根在另外三个模块原封不动地成立着，而问题 1 当时只处理了 users 域。** 实测（2026-10-23）：

| 模块 | 库 | schema | 账本表 | 迁移 |
|---|---|---|---|---|
| `message-rpc` | `first_db` | `public` | `flyway_schema_history` | V1 V2 V3 |
| `like-rpc` | `first_db` | `public` | `flyway_schema_history` | V1 V2 V3 V4 V5 |
| `article-rpc` | `first_db` | `public` | `flyway_schema_history` | V8 V9 V10 |

三个硬条件同时成立：**同一个库**（`docker-compose.yaml` 两处 `POSTGRES_DB: ${POSTGRES_DB:-first_db}`，五个模块 JDBC URL 全指向 `first_db`）+ **同一个 schema**（三处 `spring.flyway.schemas: public`）+ **同一本账**（全仓 `spring.flyway.table` **零命中**，谁都没改过）。

⇒ `message-rpc` 与 `like-rpc` 的 **V1–V3 版本号完全撞车且内容互不相同**（`message_conversation` vs `like_record` 等）。谁先启动，后者的表就永远建不出来。

**为什么 10-21 没发现**：问题 1 当时只盯「users 域会不会留下账本」，而 `like-rpc` / `article-rpc` 是在那之后才建的。`article-rpc` 的 pom 注释至今还写着「§6.3 定的全局唯一号段：message-rpc 占 V1-V3、users 占 V1-V4」—— **该注释两处失真**：users 走的是无版本号的 `01_`~`04_`；号段表**整个漏了 `like-rpc`**。

**定案（2026-10-23 决策已作出）**：给每个模块独立账本表名 —— `spring.flyway.table: flyway_schema_history_<模块>`。不改任何迁移文件名，各模块版本号从 V1 各自重新开始也互不干扰。

> ⚠️ **实施状态（2026-10-23 00:24 核对）：决策已定，代码一行未改。** 三个
> `application.yml` 的 `spring.flyway.table` **尚未添加**，本条仍处于**未修复**状态。
> 下一个接手者请先跑 `tools/difftest/check-flyway-ledger.py` 确认现状为红，再实施。
> ⇒ 写「已定案」不等于「已修」，按 §6.2 约定，未实施前按 `UNKNOWN` 处理。

**为什么不用「排全局唯一号段」**：那是 2026-10-20 的旧结论，**已被 10-21 推翻过一次**；且它要改 8 个 SQL 文件名，却并不解决「账本是 schema 级」这个真正的病根 —— 只要还有第二个模块往同一 schema 放迁移，撞号就会再现。

**⛔ 明确禁止**：不给任何模块加 `validate-on-migrate=false` / `out-of-order=true`。该绕法已被 `tools/difftest/exp-drift015-flyway-flags.sh` 用真 jar + 真 PG 16 **实测证伪**：不但修不好，还往账本塞一条语义重复的记录，把 schema 永久钉在错误版本上。

**守门网（部分已就位）**：`tools/difftest/check-flyway-ledger.py` 已落盘，按 (库, schema) 分组断言同组账本表名两两不同且非默认值（纯静态、不连库；变异实测：未修复态 `EXIT=1`）。各模块的 `FlywayLedgerIsolationTest`（用真实 `application.yml` 断言配置绑进 `FlywayProperties`）**尚未编写**。

**效力边界（不得越读）**：静态检查只保证**声明层**不撞，**不证明 Flyway 运行时真的按 `spring.flyway.table` 建表** —— 那需要真 PG 双模块对启动，属 §3.3「框架机制必须验证实际生效」，**尚未做**。全量证据见 `DRIFT/_cross/open-items.md`。

### 6.4 其他子模块

| 阶段（module） | 状态 |
|---|---|
| `service/message` | **`message-rpc` 已完成审计 + 修复（2026-10-22）**：首轮 3 项 P0 已全部关闭，补测试时又新挖出 3 项「模块根本起不来」级缺陷（Bean 名撞车 / Mapper 未注册 / Flyway 未加载）⇒ 见 §6.6。**`message-api` 也已完成首轮实现 + 语义验证 + 修复（2026-10-22）**：验证结论 `SEMANTICALLY NOT EQUIVALENT`，3 处请求解析层漂移（-001/-002/-003）全部已关，防回退网 = 41 条 MVC 管线用例 + `tools/semantic-probe` 23 例双侧对拍 ⇒ 见 §0 |
| Phase 5（全量 + 端到端） | 未启动；需真实 PG + Redis 集成测试补上（当前测试全部 mock 数据层） |

### 6.5 `service/common` 语义一致性审计与修复（2026-10-21）

只读审计（Go ↔ RAG 逐行对照，Go 侧 8 个非测试源文件全部通读原文）结论 **SEMANTICALLY NOT EQUIVALENT**，共 20 项偏差。修复在分支 `row/common-fix-semantic-drift`（issue #71186341，spec `.agents/specs/common-semantic-drift-fix.md`）。

**消费者现状（决定了修复优先级）**：

| | Go 侧被谁用 | RAG 侧被谁用 |
|---|---|---|
| communityfact | 11 个服务 | **0** |
| observability | 全部 13 个服务 | 1（user-rpc，且只 import 了 `DeadlineState` 类型） |
| logger | 全部服务 | `LoggerFactoryBean` ← user-rpc 12 处 |
| snowflake | 全部服务 | user-rpc / admin-rpc / message-rpc |

**已修 21 项**（5 项 HIGH 全部处理）：

| 组 | 内容 |
|---|---|
| HIGH | ① HTTP 超时整体失效（新增 `httpTimeout`/`rpcTimeout` 配置，5 个 yaml 落 3s/2s）② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**③ `logFatal` 不终止进程（新增 `FatalError` + users 两处 advice 显式 rethrow）④ 499 分支不可达（改为写响应）⑤ 超时启用后业务跑在无请求上下文的线程（补 `RequestContextHolder`/MDC/OTel Context） |
| MEDIUM | `isTimeoutError` 漏判 HTTP 超时、`RecordError` 空指针、`biz_msg` 丢 code、span 重复打标、span 二次 `end()`、`DeadlineState` 设在调度线程而非 handler 线程、`MqConsumer` 吞 panic、`shutdown()` 无调用者、`call_chain` 过滤失效、Snowflake 双份生成状态、admin-rpc `SnowflakeBean` 未装配 |
| LOW | 时钟回拨按 bwmarrin v0.3.0 **真实算法**（无回拨保护，继承 Go 风险并留档）、进程日志 KV 一律 `toString()` 破坏 JSON schema、timestamp 精度与尾零裁剪、trace 字段跨线程丢失、charset 默认编码、64KB 截断多读、`getFuncName` 闭包分支死代码 |

**顺带修掉的潜伏缺陷**（都是本轮改动触发才暴露的）：

- **`logback-classic` 1.6.3 与 `logback-core` 1.5.38 版本错配** → 任何真正启动的 Spring Boot 应用都会在 `LoggingApplicationListener` 抛 `AbstractMethodError: PatternLayout.getDefaultConverterMap()`。此前能长期潜伏，正因为测试全是纯单元测试。已改为交由父 pom 统一管理。
- `ObservabilityProperties` 同时带 `@Component` + `@ConfigurationProperties`，与 `@EnableConfigurationProperties` 并存会产生**两个同名类型 Bean**。本轮统一走 `@Import` 路径（与 rpc 模块一致），测试也照抄该路径。

**新增测试（§3.3 要求：必须真正装配 Spring 上下文）**：

`service/common` 现有 78 个测试（原 75 + 新增），其中 `ObservabilityWiringTest` 是**全仓第一个 `@SpringBootTest`** —— H1/H2 这类「机制没接上」的缺陷按定义无法被纯单测捕获。新增回归用例：`clientDisconnectReturns499`、`workerThreadInheritsRequestContext`、`isTimeoutErrorDetectsHttpClientTimeouts`、`wireAuthorityUnavailableSentinelMapsTo404`、`callChainFilterActuallyRemovesFrameworkFrames`、`logFatalThrowsFatalErrorInsteadOfOnExit`×2。

验证：`mvn clean verify` 全仓 9 模块 BUILD SUCCESS（common 78 / admin-rpc 24 / admin-api 46 / user-rpc 46 / user-api 30 / users-common 3 / message-rpc 待复核）。

> **⚠️ 上面这行是 2026-10-21 那轮的快照，数字已过时**。截至 issue #71187874 之后的最新值：
> `common 97 / admin-rpc 53 / admin-api 97 / user-rpc 84 / user-api 63 / users-common 11 / message-rpc 10`，
> **全仓 415 条、0 failures**。**但更要紧的不是数字变化，而是本节那句「H1/H2 这类机制没接上的缺陷
> 按定义无法被纯单测捕获」** —— `ObservabilityWiringTest` 恰恰就是纯装配测试，
> 而它**漏掉了 H2**：断言 `ctx.getBean(ServerInterceptor.class)` 非空是对的、也是绿的，
> 但 starter 根本不会用这个 Bean。**装配对了 ≠ 机制接上了**，见本节的 H2 勘误小节。

##### ⚠️⚠️ 勘误：**H2 当时并未真正修好**（2026-10-21，issue #71187874 期间实测发现）

本节「已修 21 项」里的 **② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）**← ⚠️ 此条结论已被推翻，见下方 H2 勘误小节**** ——**结论是错的**。
`@Import(RpcServerInterceptorFactory.class)` 只让 `ServerInterceptor` **Bean 存在**，
而 **grpc-spring-boot-starter 3.1.0.RELEASE 从不自动把它挂到 server 上**：

```
javap GrpcServerFactoryAutoConfiguration
  → inProcessGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer, List<GrpcServerConfigurer>)
                                             ↑ 只认 Configurer，没有任何 ServerInterceptor 形参
javap GrpcServerConfigurer → extends Consumer<ServerBuilder<?>>
javap GrpcServerProperties | grep -i intercept → （无字段）
```

唯一注册入口是 `GrpcServerConfigurer` —— 在 `accept(ServerBuilder)` 里调 `server.intercept(...)`。
已在 `UserRpcApplication` / `AdminRpcApplication` 各补一个 `@Bean GrpcServerConfigurer`。

**代价远不止「少了几个 span」**：`interceptCall` / `onHalfClose` 从未执行 ⇒

1. gRPC **无 deadline 传播** ⇒ handler 线程上 `DeadlineState` 恒未设置
   （实测 `DeadlineState.remainingMillis() == Long.MAX_VALUE`）；
2. ⇒ `UserDomainService` 的 11 处 + `AdminDomainService` 的 13 处 **`checkDeadline()` 全部失效**；
3. ⇒ B-1 的 `setQueryTimeout` 注入无从谈起（它就挂在 `DeadlineState` 上）；
4. gRPC **无超时**（客户端只能等 api 层 `http-timeout` 3s）⇒ 超时响应体退化成
   `text/plain "Request Timeout"`，与 Go 的 `{"code":1015}` 不一致。

**实测对照（`exp-b1-timeout-cancel.sh`，修复前 → 修复后）**：

| | 修复前 | 修复后 | Go |
|---|---|---|---|
| 客户端返回 | 3170ms（= api 的 3s） | **2223ms**（= rpc 的 2s） | 2208ms |
| 响应体 | `text/plain "Request Timeout"` | **`{"code":1015,"msg":"服务繁忙"}`** | 同左 |
| PG 查询滞留 | 4303ms | **176ms** | 182ms |

> **为什么 `ObservabilityWiringTest` 没抓到**：`grpcInterceptorBeanExists` 断言的是
> `ctx.getBean(ServerInterceptor.class)` 非空 —— 断言的是**Bean 存在**，
> 而缺陷是**框架没用它**。这类断言在任何实现下都成立，属**结构性假绿**。
>
> **由此新增一条纪律（补在 §3.3）**：**断言「Bean 存在」≠ 断言「机制生效」。**
> 凡依赖框架自动注册的机制（starter 的自动装配、auto-configuration、拦截器链），
> 测试必须断言**运行时的可观测后果**（本例：客户端是否 ≈2s 返回 1015），
> 或至少断言「该对象确实进入了框架的接管点」（本例：`verify(builder).intercept(...)`）。
> 锁死用例：`GrpcInterceptorWiringTest`（2 条）。

**残留差异**不在本节重复维护，统一见 **§3.4.1**（按「有意偏离 / 框架限制 / 表述差异 / UNRESOLVED」四类分列，每条带关闭条件）。

其中本轮新增、值得单独强调的一条：**B-1 超时无法取消在途 IO**。
> ⚠️ **本行原写「按 Go 的 3s/2s 打开超时后转为当前生效」—— 该描述本身也是错的**（2026-10-21 补）。
> 配置确实绑上了（实测 `newUnaryServerInterceptor` 收到 `timeout=PT2S`），
> 但因为同一个 H2 缺陷（gRPC `ServerInterceptor` 从未挂到 server 上），
> **`interceptCall` / `onHalfClose` 一次都没执行过** ⇒ 超时**从未生效**，
> 客户端只能等 api 层 `http-timeout` 3s。
> ⇒ 结论：这条「潜伏 → 生效」的叙述，以及本节对 H1 的「已修」判断，**都建立在一个从未接通的链路上**。
⚠️ 本行末句「`user-rpc` 业务侧一处都未主动响应取消」**已被 §3.4.4 的 DRIFT-018 登记推翻**
（`user-rpc` 实有 11 处、`admin-rpc` 已补到 13 处 `checkDeadline()`），
且 2026-10-21 的机制实验证明 **`checkDeadline` 对在途 IO 无效、缓解手段是 `setQueryTimeout`** ——
完整结论与数据见 §3.4.1「B-1 补记」。

另一条：Snowflake 时钟回拨**照搬 bwmarrin v0.3.0 的无保护行为**，继承其「回拨可能撞 ID」的风险。RAG 不擅自加保护（加了即与 Go 不等价），若要加须单独立项。

### 6.6 `service/message/message-rpc` 语义一致性审计 + 修复（2026-10-22）

> **本节只覆盖 `message-rpc` 一个子模块**；`message-api` 的首轮实现 + 语义验证 + 修复记录在 **§0**
> （不在本节，避免两处各写一半）。与 §6.1–§6.3（`service/users`）、§6.5（`service/common`）**互不覆盖、无重叠**。

**审计方式**：Go ↔ RAG 逐行对照静态审计 + proto 契约逐字节比对。Go 侧通读 15 个源文件
（9 个 `*Logic.go` + `helper.go` + `message.go` + `data_model.go` + `db.go` + `config.go` +
`servicecontext.go` + `messageserviceserver.go` + `messageservice.go`），Java 侧通读 18 个源文件
（`MessageDomainService` / `MessageRpcSupport` / 3 个 Mapper / 3 个 Entity / 2 个 RpcClient /
`MessageProperties` / `MessageMetrics` / `NacosRegistration` / `MessageRpcServiceImpl` +
3 份 Flyway 迁移 + `application.yml` + `message.proto`）。

**审计结论：`SEMANTICALLY NOT EQUIVALENT`** —— 首轮查出 **3 项 P0 语义不一致，其中 2 项使聊天功能整体不可用**。

**修复结论（2026-10-22 当日完成）**：3 项 P0 **全部关闭**（真 PG + 变异测试双重验证）；
补测试的过程**又挖出 3 项首轮静态审计没抓到的缺陷 —— message-rpc 这个模块此前根本启动不了**。
合计 **6 项缺陷已修**，测试数 **9 → 21**，message 域已纳入 `tools/difftest` 双端口对拍。

> ⚠️ 下文 §6.6.1 保留的是**审计当时**的缺陷形态原文（不删改），
> 目的是让后人看到「注释与配置看起来完全对齐、实际机制一个都没生效」的具体样子；
> 修法与防回退用例见 §6.6.1 顶部的状态表。

---

#### 6.6.1 三项 P0（功能级失效）—— **2026-10-22 已全部关闭**

> ⚠️ 本小节记的是**审计当时**的缺陷形态，保留原文是为了让后人看到「注释与配置看起来完全对齐、
> 实际机制一个都没生效」的具体样子。**当前状态与修法见下面的状态表。**

| # | 缺陷 | 修法 | 防回退用例 | 变异测试 |
|---|---|---|---|---|
| **DRIFT-MSG-001** | 6 个 `@Transactional` self-invocation 失效 | 新增独立 Bean `MessageAtomicService`，6 个方法全部搬过去；`MessageDomainService` 跨 Bean 调用 | `rollbackConversationWhenMessageInsertFails`（真 PG）+ `domainServiceMustNotDeclareTransactionalMethods` / `atomicServiceMustOwnTheTransactionalMethods`（结构断言，不依赖 Docker） | ✅ 摘掉 `@Transactional` ⇒ `expected: <0> but was: <1>`（孤儿会话行真的残留） |
| **DRIFT-MSG-002** | `conversation_id` 恒为 0 | 两条写路径都在**事务内** `msg.setConversationId(conv.getId())`，与 Go `message.go:186`/`:196` 的闭包内赋值逐行对齐 | `conversationIdMustBePropagatedToResponseAndRow`、`firstMessageMustBeFindableByConversationId`、`appendedMessageMustAlsoCarryConversationId` | ✅ 删掉 `createOrSendMessageTx` 的赋值 ⇒ `expected: <...883> but was: <0>` |
| **DRIFT-MSG-003** | 拦截器未挂到 server | `@Import({RpcServerInterceptorFactory, ObservabilityProperties, ObservabilityLifecycle, …})` + `GrpcServerConfigurer` Bean | `MessageGrpcInterceptorWiringTest` 3 条 | — |

##### ⚠️⚠️ 由补测试**新挖出**的 3 项：message-rpc 这个模块此前**根本启动不了**（2026-10-22）

这三项**不在首轮静态审计的台账里** —— 它们是「把上下文真正装配起来」时才暴露的。
**这也是本轮最重要的产出**：静态逐行审计 + 21 条全绿的纯单测，都没能发现它们。

| # | 缺陷 | 触发条件与证据 | 修法 |
|---|---|---|---|
| **DRIFT-MSG-004** | `NacosRegistration` 用裸 `@Component`，默认 Bean 名 `nacosRegistration` 与 `spring-cloud-starter-alibaba-nacos-discovery` 的 `NacosServiceRegistryAutoConfiguration` **同名撞车** | 上下文启动即失败：<br>`The bean 'nacosRegistration', defined in ... NacosServiceRegistryAutoConfiguration.class, could not be registered. A bean with that name has already been defined in .../NacosRegistration.class and overriding is disabled.`<br>（Spring Boot 2.1+ 默认 `allow-bean-definition-overriding=false`） | `@Component("messageRpcNacosRegistration")`。**另两个模块早就这么写了**（`userRpcNacosRegistration` / `adminRpcNacosRegistration`），只有 message-rpc 漏了 |
| **DRIFT-MSG-005** | 缺 `@MapperScan`，3 个 Mapper 注册不上 | `No qualifying bean of type '…mapper.NotificationMapper' available` ⇒ `UnsatisfiedDependencyException: messageAtomicService`。**同款问题 `UserRpcApplication` 已踩过**，其 `@MapperScan` 注释写着「代码一直没加 → Mapper 注册不上」 | `@MapperScan("com.rag.message.message.rpc.mapper")` |
| **DRIFT-MSG-006** | 缺 `spring-boot-flyway`，Flyway 自动配置**从未加载** | **启动日志里连一行 Flyway 都没有**（不是报错，是根本没被加载）⇒ 三张 `message_*` 表永不创建 ⇒ 第一次打 SQL 才炸：`relation "message_conversation_message" does not exist`。Spring Boot 4 把自动配置拆成独立模块，`flyway-core` 只提供引擎、不提供自动配置 | 补 `org.springframework.boot:spring-boot-flyway`（版本由 `spring-boot-starter-parent` 管理）。修复后实测：`Successfully applied 3 migrations to schema "public", now at version v3` |

##### ⚠️ DRIFT-MSG-001：6 个 `@Transactional` 因 self-invocation 全部静默失效

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

Go 侧是**显式**事务：

```go
// service/message/rpc/internal/model/message.go:186
func (m *MessageModel) CreateConversationWithMessageTx(...) error {
    return m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
        if err := tx.Create(conversation).Error; err != nil { return err }
        message.ConversationID = conversation.ID
        return tx.Create(message).Error
    })
}
```

Java 侧 6 个 `@Transactional` 方法全部挂在 `MessageDomainService` 本类上，且**全部由同类私有方法
`doXxx` 通过 `this.` 调用**：

```
MessageRpcServiceImpl.invoke(→ domain.sendChatMessage(req))     ← 走代理，但该方法本身无 @Transactional
    └→ doSendChatMessage(req)                                    ← 目标对象内部
         └→ this.createConversationWithMessageTx(conv, msg)     ← 不经代理 ⇒ @Transactional 静默失效
              ├→ conversationMapper.insertConv(conv)   ← 独立 auto-commit
              └→ messageMapper.insertMsg(msg)         ← 独立 auto-commit
```

- **证据** `[CODE]`：全仓 `exposeProxy` / `AopContext` / `EnableAspectJAutoProxy(exposeProxy=true)`
  **零命中**（`grep -rn` 覆盖 `service/**/*.java` + `*.yml`）。
  受影响方法 6 个：`createConversationWithMessageTx:385` / `createOrSendMessageTx:399` /
  `insertNotificationsTx:509` / `markConversationReadTx:759` / `markNotificationReadTx:901` /
  `markAllNotificationsReadTx:953`。
- **本仓库已有同款教训** `[CODE]`：`service/users/admin/admin-rpc` 的 `AdminCreateAtomicService`
  **正是为修这个问题而抽出的独立 Bean**，并留有防回退断言
  （`AdminUpdateSelfAndCreateAdminRegressionTest:169`：「4 步事务必须独立成 Bean，否则同类自调用会让
  `@Transactional` 失效」）。`message-rpc` **没有做**这件事，直接复现了 AGENTS.md §1.1 列出的 11 处回归中的第一处
  （`CreateAdmin` 事务 self-invocation 失效）。
- **可观察影响**：
  1. 新建会话时若 `insertMsg` 失败 → Go 全部回滚，**Java 残留孤儿会话行**且对调用方报 INTERNAL；
  2. `createOrSendMessageTx` 若 update 失败 → 已插入的 message 行残留；
  3. **并发同对用户首发**：两边都有 `uk_conversation_pair` 唯一索引，但只有 Go 的唯一约束冲突会
     连带回滚整个事务；Java 侧第一个 INSERT 成功、第二个冲突时 `@Transactional` 已失效 ⇒ 会话行无法回滚。

##### 🔴 DRIFT-MSG-002：`message_conversation_message.conversation_id` 恒为 0

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

```
Go:  CreateConversationWithMessageTx:  message.ConversationID = conversation.ID   ← tx 内设置
     CreateOrSendMessageTx:          message.ConversationID = conversation.ID   ← tx 内设置
Java: ConversationMessageEntity.conversationId 是 primitive long（未设置 ⇒ 0）
      insertMsg 的 VALUES 绑 #{conversationId}
      doSendChatMessage 构造 messageItem 时只设了 id/senderId/senderRole/recipientId/
      recipientRole/content/read/createdAt —— 唯独漏了 conversationId
```

- **证据** `[CODE]`：`grep -rn "setConversationId" message-rpc/src/main/java/` 共 5 处命中，
  **全部是 protobuf builder**（`SendChatMessageResp.newBuilder()` × 3、`MessageRpcSupport.buildConversationItem`
  × 1、`toMessageItem` 读 getter × 1），**实体 setter 零调用**。
- **可观察影响**：所有消息写成 `conversation_id = 0`，于是
  - `GetConversationMessages(conv)` 按 conversation_id 过滤 → **永远返回空列表**
  - `CountUnreadMessagesByConversation` → 恒 0 ⇒ 会话列表 `unread_count` 恒 0
  - `MarkConversationRead` → 更新 0 行
  - 响应中 `message.conversation_id` 也返回 0
- **为何长期未暴露** `[CODE]`：`message-rpc` 现有测试仅 2 个类 9 条，**全部是纯 Mockito 单测**，
  不装配 Spring 上下文、不执行 SQL（§3.3 纪律）。9 个 RPC 端到端路径**零覆盖**。

##### 🔴 DRIFT-MSG-003：gRPC 2000ms 超时从未生效（`ServerInterceptor` 未挂到 server）

**分类：`SEMANTIC MISMATCH`　业务可接受性：`UNACCEPTABLE`　风险：HIGH**

```
Go:  zrpc RpcServerConf.Timeout 未配 → go-zero 默认 2000ms（zrpc/config.go:46）→ 服务端强制 deadline
Java: application.yml:130  observability.rpc-timeout: 2s          ← 配了
      → ObservabilityProperties（common/spring，@Component + @ConfigurationProperties("observability")）  ← 绑得上
      → RpcServerInterceptorFactory 产出 ServerInterceptor Bean
         （因 MessageRpcApplication 的 scanBasePackages 含 "com.rag.common"，该 Bean 大概率存在）
      → ✗ devh grpc-server-spring-boot-starter 3.1.0.RELEASE 从不自动注册 ServerInterceptor Bean
      → ✗ message-rpc 全模块零处 GrpcServerConfigurer / builder.intercept(...)
      ⇒ 拦截器从未进入 ServerBuilder，deadline / span / DeadlineState 全部永不执行
```

- **证据** `[CODE]`：`grep -rn "GrpcServerConfigurer\|ServerInterceptor\|intercept"` 在
  `message-rpc/src/main/java/` **零命中**。对照 `UserRpcApplication:104` 有
  `@Bean rpcInterceptorConfigurer(...)`，且 `GrpcInterceptorWiringTest` 用
  `verify(builder).intercept(same(interceptor))` 锁死。
- **本条就是 §6.5「H2 勘误小节」在 message-rpc 上的原样复现** —— 该小节已明确记载
  「装配对了 ≠ 机制接上了」，且唯一注册入口是 `GrpcServerConfigurer`。
  **message-rpc 既没有 `GrpcServerConfigurer`，也没有任何等价 wiring 测试。**
- **附带失效**：`DeadlineState` 永不绑定 ⇒ 任何依赖它的超时守卫与
  `setQueryTimeout` 取消在途 SQL 均无效 ⇒ 慢查询无上界。
- ⚠️ **本条属 §3.3 新增纪律「断言 Bean 存在 ≠ 断言机制生效」的第二个实例**。
  第一个实例是 §6.5 的 H2（`ObservabilityWiringTest#grpcInterceptorBeanExists` 结构性假绿）。

---

#### 6.6.2 P1 / P2 台账

| # | 维度 | Go | Java | 分类 | 业务可接受性 |
|---|---|---|---|---|---|
| **DRIFT-MSG-004** | `listNotifications(unread_only=true)` 的 `total` | `ListNotifications` 用已含 `unread_only` 过滤的 query 做 `Count(&total)` ⇒ **未读数** | `selectCount(QueryWrapper.eq("recipient_id", uid))` ⇒ **漏了 unread_only** = 全部数 | `SEMANTIC MISMATCH` | `UNACCEPTABLE` |
| **DRIFT-MSG-005** | 数据库错误的错误身份 | 裸 GORM error → grpc `Unknown` → 客户端 `BizCodeFromError` 兜底 ⇒ **1015** | 读路径裸 RuntimeException → `invoke` 兜底 `INTERNAL`+"internal server error" ⇒ **5001**；三个 mark-* 写路径显式包 `INTERNAL`+"database update failed" ⇒ **5008** | `SEMANTIC MISMATCH` | `UNKNOWN`（无契约规定应对外报哪个码） |
| **DRIFT-MSG-006** | `markConversationRead` 副作用列 | 只 UPDATE message 表 | 额外 `conversationMapper.touchUpdatedAt(conversationId, readAt)` | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |
| **DRIFT-MSG-007** | `trimContent` 空白集 | `strings.TrimSpace`（Unicode，含 U+00A0/U+3000） | `String.trim()`（仅 ≤ U+0020） | `SEMANTIC MISMATCH`（窄） | `UNKNOWN` |
| **DRIFT-MSG-008** | `ListConversations` 排序 | `Order("last_message_at desc").Order("updated_at desc")` | 仅 `orderByDesc(getLastMessageAt)` | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |
| **DRIFT-MSG-009** | `GetConversationMessages` 排序键 | `created_at ASC` | `id ASC`（雪花单调 ⇒ 实践等价，非严格等价） | `OBSERVABLE DIFFERENCE` | `ACCEPTABLE` |
| **DRIFT-MSG-010** | 时间戳 epoch | DSN 钉 `TimeZone=Asia/Shanghai` + GORM→`timestamptz`（存绝对时刻） | Flyway DDL `TIMESTAMP`（无时区）+ `LocalDateTime` + `toEpochSecond(ZoneOffset.UTC)` | `OBSERVABLE DIFFERENCE` | **`UNKNOWN`**（取决于 JVM 运行时区，见 UNRESOLVED-MSG-001） |
| **DRIFT-MSG-011** | 广播批量插入分片 | `CreateInBatches(items, 200)` 会按 200 分片 | 单条 `INSERT ... VALUES (...),(...)` 全量拼接 | `OBSERVABLE DIFFERENCE`（容量边界） | `UNKNOWN` |
| **DRIFT-MSG-012** | 部署面 | 无 servlet 容器 | `pom.xml` 引 `spring-boot-web-server`，与 `NacosRegistration` javadoc 自述「本服务不带 web」矛盾 | `OBSERVABLE DIFFERENCE` | `UNKNOWN` |

**DRIFT-MSG-007 值得展开**（它是 DRIFT-011 的同类形态但影响的是**校验分支**而非展示）：

```
content = "\u00A0"（NBSP）
Go:  TrimSpace → ""            → content == "" → InvalidArgument(5003)，不写库
Java: String.trim() → "\u00A0"  → 非空 → 通过校验 → 写入一条 content=NBSP 的消息
      且 runeLen("\u00A0") = 1，参与「首条 ≤ 50 rune」判定
```

> `MessageRpcSupport.trimPreview` 的 javadoc 已声明「Java trim 只去 ≤ ' '，是有意保留的表达层差异」，
> 但 **`trimContent` 没有同等声明，且它喂的是 `content.isEmpty()` 这个校验分支** —— 后果比 preview 严重。

---

#### 6.6.3 已确认等价的高价值项（勿重复核查）

以下维度经逐行对照**确认无漂移**，记录在此以免下一轮重复核查：

| 维度 | 证据 |
|---|---|
| **proto 契约逐字节一致** | 两份 `message.proto` 除 `option go_package` / `java_package`+`java_multiple_files` 外**完全相同**：19 个 message、3 个枚举（`NotificationKind` / `SenderRole` / `ConversationStatus`）、9 个 service 方法的字段号与类型全同 |
| **错误码与文案表** | 18 个业务码（200/500/1015/5001-5008/5101-5102/5201-5207）+ 22 条文案 1:1；`msg→code` 反查表排除 200/500 的逻辑一致；未知码 fallback `"FAIL"` 一致 |
| **9 个 RPC 的参数校验分支** | `userId>0` / `operatorId>0 && conversationId>0 && validRole` / `senderId>0 && content!="" && validRole` 等逐条一致 |
| **`normalizeLimit` 与配置值** | `≤0→default`、`maxLimit>0` 截断；配置 `default 20 / max 100 / pageSize 200` 与 Go `etc/message.yaml` 一致 |
| **四个会话权限函数** | `canonicalParticipants` / `isParticipant` / `peerOf` / `canSend` 逐条一致，**含 `PENDING` 时排除 pending_sender 本方这条易漏规则** |
| **`runeLen`** | `codePointCount(0,len)` ↔ `len([]rune(s))`，等价 |
| **`trimPreview`** | 上限 **80 rune**（非 255）且按码点边界切（`offsetByCodePoints`），不产生孤立代理对；5 条测试锁死。**本模块做得最好的对齐点** |
| **`markNotificationRead`** | 已修成与 Go 同构的单条 `UPDATE ... WHERE id=? AND recipient_id=?` + `RowsAffected==0 → NotFound`；有 `ArgumentCaptor` 抓真实 SQL 片段的测试（符合 §3.3） |
| **会话状态机** | `PENDING→OPEN` / pending_sender 连发拦截 `FailedPrecondition(5204)` / `UNSPECIFIED→OPEN` |
| **`createOrSendMessageTx` 更新字段** | 7 个字段（status / pending_sender_role / pending_sender_id / last_message_id / last_message_preview / last_message_at / updated_at）齐全 |
| **雪花 ID** | `EPOCH/NodeBits/StepBits` 位布局、`%1024` 模序等价性、**时钟回拨不保护**这一 bwmarrin v0.3.0 原生性质均如实复刻 |
| **连接池** | Hikari `maximum-pool-size:100` / `minimum-idle:10` ↔ Go `SetMaxOpenConns(100)` / `SetMaxIdleConns(10)` |
| **指标** | 5 个指标名（`message_rpc_*`）、标签维度、`result=success/fail` 语义一致 |
| **`loadProfile` 兜底** | `"User {id}"` / `"Admin {id}"` / `"Unknown {id}"` 三态一致 |
| **表名** | 三张表 `message_notification` / `message_conversation` / `message_conversation_message` 两端**同名**，未发生 §3.4 表名偏离 |

---

#### 6.6.4 证据边界台账（message-rpc，**2026-10-22 补测试后更新**）

| 项 | 审计时（2026-10-22 上午） | **现在** |
|---|---|---|
| 测试总量 | 9 条 / 2 个类 | **21 条 / 4 个类** |
| 纯 Mockito 单测 | 9 条 | 9 条（保留） |
| **真 PG 集成测试** | **0** | **8 条**（`MessageTransactionWiringTest`，Testcontainers + `postgres:16-alpine`） |
| **wiring 测试** | **0** | **3 条**（`MessageGrpcInterceptorWiringTest`） |
| 端到端对拍 | 未纳入 `tools/difftest` | **已纳入**（§6.6.6） |
| P0 的证据类型 | `[CODE]` 静态分析 + 仓库内同款教训佐证 | **`[RUNTIME]` 真 PG 实测 + 变异测试双向验证** |

**仍然存在的证据缺口（勿当成已验证）**：

| # | 缺口 | 影响 |
|---|---|---|
| **UNRESOLVED-MSG-001** | JVM 运行时区未知 | DRIFT-MSG-010 时间戳 epoch 是否整体偏移仍未定级。查部署环境 `TZ` / `-Duser.timezone`，或两端各发一次请求比对 `created_at` |
| **UNRESOLVED-MSG-002** | `ServerInterceptor` 挂载只有 `verify(builder).intercept(...)` 这层证据 | 「starter 会在 build 阶段 apply 所有 Configurer」属 starter 自身契约。端到端层面由 §6.6.6 的双端口对拍 + `exp-b1-timeout-cancel.sh` 兜底 |
| **UNRESOLVED-MSG-003** | 双端口对拍的 12 档**尚未实际执行过** | 脚本已就位且 `javac` 通过、`bash -n` 通过，但**未跑过完整两套栈**（需 `run.sh --keep --serve-only` 起栈）。首次执行前不要把它当作已验证 |
| **UNRESOLVED-MSG-004** | DRIFT-MSG-004/005/006 未各配独立防回退用例 | 它们目前只被 `MessageTransactionWiringTest` 的「上下文能起来」间接锁住。若有人删掉 `@MapperScan` / `spring-boot-flyway` / Bean 名，测试会红，但**红的原因不是断言点名了它**，而是连带失败 —— 建议后续拆出 `MessageContextWiringTest` 逐条点名 |
| **UNRESOLVED-MSG-005** | `AutoMigrate`（Go 每次启动校验表结构）vs `Flyway`（Java 一次性迁移后不再校验）的存量表漂移 | 需确认是否存在已存在的非 Flyway 建表的库 |

---

#### 6.6.5 ⚠️ 本轮的方法论产出（比发现本身更重要）

三项 P0 **全部位于「注释写得很完整、代码看起来已对齐、实际机制未生效」的位置**：

| 位置 | 注释/配置声称 | 实际 |
|---|---|---|
| `createConversationWithMessageTx:385` | 顶部类注释白纸黑字列出 6 个事务方法「放本类上 `@Transactional`」 | 全部 self-invocation，注解形同虚设 |
| `application.yml:130` | `observability.rpc-timeout: 2s` + 三行注释解释「取 2s 对齐 go-zero 默认」 | 拦截器从未挂上，超时从未执行 |
| `doSendChatMessage:243-251` | `messageItem` 逐字段 setter 齐全，注释标注每个字段对应的 Go gorm tag | 独缺 `conversationId`，而代码里另有 3 处 `setConversationId` 在 builder 上，肉眼扫过会误判为已覆盖 |

> **这三条印证并扩展了 §3.3 的现有纪律**：本项目已知的静默失效有四类
> （`@Transactional` self-invocation / `@ResponseBodyAdvice` 二次包裹 / MP `typeHandler` 丢字段类型 /
> 命令行属性前缀写错），**现在可确认还要加上第五类**：
> **实体必填字段在构造点漏赋值，而同一字段名在别处以 builder 形式出现，掩盖了缺口。**
>
> 与前三类的区别：前四类都是「注解/配置写了但没生效」，**这一类是「普通代码少写了一行」**，
> 按理说最容易被发现 —— 但它恰好躲过了本模块唯一的那类测试（纯 Mockito 单测不执行 SQL，
> 看不出 `conversation_id` 写成 0）。
>
> ⇒ **推论**：§3.3「必须配一条能真正触发该机制的测试」这条纪律，其适用范围应从
> 「框架机制」扩展到**「任何跨进程边界的字段赋值」**。判断标准不是「有没有注解」，
> 而是「这条测试如果为真，能不能观察到该字段在下游的真实取值」。

##### ⚠️⚠️ 元教训二（2026-10-22 修复阶段，比元教训一更值钱）：**变异测试抓出了「我自己的新测试也是假绿」**

修复阶段按 §3.3 的要求做了变异测试，方式是**把刚修好的代码改回缺陷态，确认新测试变红**。
第一刀就翻车了：

```
变异：删掉 createOrSendMessageTx 里的 msg.setConversationId(conv.getId())
结果：8 条用例全部照常通过 ⇒ 该分支的 conversation_id 缺陷本来漏网
```

原因是新写的 7 条用例**全部只走「会话不存在 → 新建」这条 `createConversationWithMessageTx` 路径**，
`createOrSendMessageTx`（已有会话追加消息）**一条都没覆盖**。
补上 `appendedMessageMustAlsoCarryConversationId`（对端回复才能走到该分支）后，
同一刀变异立刻变红：`expected: <2113123504948088833> but was: <0>`。

第二刀（摘掉 `@Transactional`）同样被抓住，而且是**两道网同时变红**：
真 PG 语义测试报 `expected: <0> but was: <1>`（孤儿会话行真的残留），
结构断言报 `expected: [6 个方法] but was: [5 个方法]`。

> **这一条比「发现 3 项 P0」本身更重要**，因为它说的是：
> **「我刚补的测试覆盖了我以为覆盖的路径」这件事，会真实发生，且默认发生。**
>
> 具体机制值得记下来：一个模块的写路径有 N 条（新建 / 追加 / 批量 / 更新），
> 而测试往往只挑「最容易构造输入」的那条（此处是「发一条新消息」），
> 于是**恰好最需要验证的那条路径最容易被跳过** —— 因为它需要多一次调用才能到达
> （新建后再让对端回一条）。
>
> ⇒ **新增纪律**：写事务 / 状态机相关的测试时，
> **「变异测试」不是可选的收尾，而是必须项**；且变异点要覆盖
> **每一条独立的写路径**，而不只是「刚改的那一行」。
> 判据：把每条写路径分别破坏一次，测试必须**每条都红**。
> 只红一条 ⇒ 其余路径仍是假绿。
>
> 与 §3.3「断言 Bean 存在 ≠ 断言机制生效」并列，两者合起来是同一条元规则：
> **任何测试都必须先证明「它在缺陷存在时会红」，再相信它的绿。**

#### 6.6.6 message 域纳入 `tools/difftest`（2026-10-22）

**为什么必须单开一套**：既有 `diff.py` 是 **HTTP wire 层**对拍，走 api 入口。
`message-rpc` 是**纯 gRPC、没有 HTTP 面** ⇒ 现有 harness 完全覆盖不到它。
沿用 user 域 `grpc-dual-port-runner.java` 的范式：**一个 Java 进程、两个 ManagedChannel、
同一 stub 双端口打**，比对 `Status.getCode()` 与 happy path 响应体。

| 文件 | 作用 |
|---|---|
| `tools/difftest/conf/go/message-rpc.yaml` | Go 真源 yaml 的**副本**（Etcd 整块移除、`Password` 写字面值、下游改 `Endpoints` 直连、端口 18082） |
| `tools/difftest/grpc-message-dual-port-runner.java` | 12 档断言：11 档 status code + 1 组 happy path 响应体 |
| `tools/difftest/exp-difftest-grpc-errors-message.sh` | 编排：起栈 → 编译 → 跑 → 存结果 |
| `tools/difftest/run.sh` | **增量**接入：构建/启动两端 message-rpc + 6 条 `assert_tables`；**users 域逻辑一字未改** |

**12 档的 expected 全部逐条核对自冻结的 Go 真源**（每档在 runner 注释里标了 `file:line`）：
INVALID_ARGUMENT ×6（`listNotifications` / `getUnreadSummary` / `markNotificationRead` /
`markAllNotificationsRead` / `sendChatMessage` 空 content / 首条 51 rune）、
NOT_FOUND ×2、`PERMISSION_DENIED` ×1（`USER → ADMIN` 且无会话 ⇒ `ErrorAdminOnlyInit`）、
自聊 `ErrorSelfChat` 计入 INVALID_ARGUMENT，
外加 happy path 比对 `conversation_id` / `message.conversation_id` / `status` / `can_send`。

**端口与库**（两侧对称，避开本机占用）：

| | Go | RAG |
|---|---|---|
| message-rpc | **18082** | **58210** |
| 库 | `go_db`（GORM `AutoMigrate` 建表） | `rag_db`（Flyway 建表） |
| 下游寻址 | `Endpoints: ["127.0.0.1:19004"]` / `["127.0.0.1:18081"]` | `--grpc.client.*.address=static://127.0.0.1:…` |

> ⚠️ **`UNRESOLVED-MSG-003`：这套 harness 目前只验证到「能编译、语法正确」，尚未实际跑过完整两套栈。**
> 首次执行前不要把它计入「已验证」。执行方式：
> ```
> ./tools/difftest/exp-difftest-grpc-errors-message.sh          # 自动起栈
> ./tools/difftest/exp-difftest-grpc-errors-message.sh --stacks-up
> ```
> ⚠️ 另：Go 的 `NewServiceContext` 里 `zrpc.MustNewClient(c.UserRpc)` 默认 `NonBlock=false`
> ⇒ 走 `grpc.WithBlock()`，**下游没监听会一直阻塞拨号**。
> 所以 `run.sh` 里 message-rpc 必须排在 user-rpc / admin-rpc 之后启动（已加 `sleep`）。

---

### 6.7 `service/message` 全域语义一致性验证（2026-10-22 第二次）

> **本节与 §6.6 的关系**：§6.6 是「`message-rpc` 单模块首轮审计 + 修 6 项」；
> 本节是「`message` **全域**（rpc + api + common + proto）的独立第二轮验证」，
> **验证方法与 §6.6 不同**：不是「找当前实现的缺陷」，而是**按 Go → Spring 的
> 行为等价模型逐维度对照**（契约 / 输入 / 分支 / 外部交互 / 状态 / 错误身份 / 超时 / 重试 / 并发 / 生命周期）。
> 两节**无重叠**：§6.6 的 6 项本节一条都没重复发现，本节的 10 项 §6.6 也没抓到。

#### 6.7.0 编号命名空间 —— **以及一次必须公开纠正的错误**

**本轮发现用 `DRIFT-MSG-V-0xx`**（V = verification 第二轮），不复用 `DRIFT-MSG-*` 也不复用
`DRIFT-MSGAPI-*`。

> 🔴 **纠正一：本节首版声称「与 §6.6 的 6 项均无重叠」——这是错的。**
> §6.6 除了那 6 条 P0，**另有一张 DRIFT-MSG-004~012 的 9 行漂移表**，
> 其中 **8 行正是本轮的 V-001/002/003/004/005/006/007/008**。逐条对照：
>
> | §6.6 漂移表 | 内容 | = 本轮 |
> |---|---|---|
> | DRIFT-MSG-004 | `listNotifications` 的 `total` 丢 `unread_only` | **= V-001** |
> | DRIFT-MSG-005 | DB 错误身份 `1015` vs `5001`/`5008` | **= V-003** |
> | DRIFT-MSG-006 | `markConversationRead` 多写一列 | **= V-006** |
> | DRIFT-MSG-007 | `trimContent` 空白集 | **= V-008** |
> | DRIFT-MSG-008 | `ListConversations` 缺次级排序键 | **= V-005** |
> | DRIFT-MSG-009 | `GetConversationMessages` 排序键 | **= V-004** |
> | DRIFT-MSG-010 | 时间戳 epoch | **= V-007** |
> | DRIFT-MSG-011 | 广播批量插入未分片 | **= V-002** |
> | DRIFT-MSG-012 | 部署面（无 servlet 容器 vs 引了 web starter） | 本轮未复核 |
>
> ⇒ **本轮真正「新发现」的只有 2 项：V-009（直方图 bucket）与 V-010（错误解包深度）**，
> 外加补测试时挖出的 3 条 P0（V-011/012/013）。
>
> **那第二轮的价值在哪？** 不在「发现」，在**定级 + 修 + 守**：
> §6.6 的表把它们标成 `UNKNOWN`（可接受性未定），本轮逐条补齐了取证、
> 可接受性判断、修复与防回退用例。§6.6 的 DRIFT-MSG-009 甚至写着
> 「雪花单调 ⇒ 实践等价，**ACCEPTABLE**」——本轮证明它**不等价**（并列时顺序不同）并已修。

> 🔴 **纠正二：本节首版称「`DRIFT-MSG-008` 在本文件里根本不存在」——也是错的。**
> 它存在，在 §6.6 的那张漂移表里。
>
> 🔴 **纠正三（根因）：§6.6 自身就有三套编号分配互相撞车**，这是本文件最严重的台账问题：
>
> | 编号 | §6.6.1 第一张表（3 条 P0） | §6.6.1 第二张表（模块起不来） | §6.6 漂移表（9 行） |
> |---|---|---|---|
> | `DRIFT-MSG-004` | — | `NacosRegistration` Bean 名撞车 | `listNotifications` 的 `total` |
> | `DRIFT-MSG-005` | — | 缺 `@MapperScan` | DB 错误身份 |
> | `DRIFT-MSG-006` | — | 缺 `spring-boot-flyway` | `markConversationRead` 多写列 |
>
> ⇒ **同一个 ID 在同一文件里指两个完全不同的缺陷。**
> 而 §0 的速查表只引用了「-001..-006」（第二张表那套），
> **完全看不到漂移表的 9 行** —— 这才是「§0 看着全绿、实际漂移一片」的机制来源。
> ⇒ **`MessageAtomicService` javadoc 里那句「DRIFT-MSG-006」与「DRIFT-MSG-008」并非笔误，
> 它引用的确实是漂移表那套编号**，只是被 §0 省略了。
>
> **本轮不做编号重整**（会牵动 §0/§6.6 大量交叉引用，代价大于收益），
> 但**必须留档**：查漂移时**两个 ID 空间都要查**，
> 且 `DRIFT-MSG-V-*` 是本轮之后的新空间。

#### 6.7.1 验证结论

**`SEMANTICALLY NOT EQUIVALENT`** —— 10 项差异。

| 编号 | 维度 | 一句话 | 风险 | 状态 |
|---|---|---|---|---|
| **DRIFT-MSG-V-001** | 结果语义 | `listNotifications(unread_only=true)` 的 **`total` 丢了 `is_read=false` 过滤** | HIGH | 待修 |
| **DRIFT-MSG-V-002** | 外部交互 | 广播通知**单条 foreach 多行 INSERT**，收件人 ≥ 5462 时整批失败 | HIGH | 待修 |
| **DRIFT-MSG-V-003** | 错误身份 | DB / 内部故障的业务码 **Go `1015` → RAG `5001`/`5008`** | MED-HIGH | 待决策 |
| **DRIFT-MSG-V-004** | 排序语义 | 会话消息排序键 **`created_at asc` → `id asc`** | MED | 待修 |
| **DRIFT-MSG-V-005** | 排序语义 | 会话列表**缺次级排序键 `updated_at DESC`** | MED | 待修 |
| **DRIFT-MSG-V-006** | 副作用集 | `markConversationRead` **多写一列** `conversation.updated_at` | MED | 待决策 |
| **DRIFT-MSG-V-007** | 序列化 | 时间列 **`timestamptz` → `timestamp`** + 时区解读不同 | MED（条件性） | UNRESOLVED |
| **DRIFT-MSG-V-008** | 输入解释 | `String.trim()` vs `strings.TrimSpace` **空白集不同** | MED | 待修 |
| **DRIFT-MSG-V-009** | 可观测指标 | 直方图 **bucket 布局不同**（DefBuckets vs Micrometer 默认） | LOW | 待决策 |
| **DRIFT-MSG-V-010** | 错误传播 | 错误**解包深度不同**（Go `errors.As` 穿透 vs Java `instanceof` 不穿透） | LOW / latent | 留档 |

#### 6.7.2 两项 HIGH 的取证

##### 🔴 DRIFT-MSG-V-001：`total` 丢了 `unread_only` 过滤

```
Go  message.go:39-48
    query := Model(&Notification{}).Where("recipient_id = ?", recipientID)
    if unreadOnly { query = query.Where("is_read = ?", false) }   // ← 先加过滤
    total = query.Count()                                          // ← 复用同一个 query ⇒ total 只数未读

RAG MessageDomainService.doListNotifications:756-763
    items  = notificationMapper.listByRecipient(..., unreadOnly, ...)   // ← 有 is_read=false
    total  = selectCount(QueryWrapper.eq("recipient_id", uid))          // ← 没有 is_read
    unread = countUnreadByRecipient(uid, 0, 0)
```

**可观察影响**：`unread_only=true` 时响应体 `{"total":N,"unread_count":M}` 中 `total` 恒 ≥ M 且不随过滤变化
⇒ 前端分页器按 total 算总页数会多翻出空页。

**Counterfactual（§3.2 检验）= YES**：Spring 开发者写 `countByRecipient` 时几乎必然只按主维度过滤，
把 `unreadOnly` 当成「列表查询条件」而不是「计数条件」——这个写法看起来完全合理。
**这是典型的迁移期语义漂移**，不是笔误。

**测试现状**：message-rpc 现有 21 条测试中**无任何一条断言 `total`** ⇒ 这条漂移完全无人看守。

##### 🔴 DRIFT-MSG-V-002：广播在 >5461 用户时整体失败

```
Go   message.go:22-27   conn.CreateInBatches(items, 200)     ← 自动分批，收件人数无上限
RAG  NotificationMapper.insertBatch   单条 <foreach> 多行 INSERT，12 列/行
```

Postgres 协议单条 SQL 绑定参数上限 **65535** ⇒ `floor(65535/12) = 5461`。
**recipients ≥ 5462 时整条 SQL 失败 → 事务回滚 → 一条通知都发不出去。**
广播模式下 `collectBroadcastRecipients` 收集**全量用户** ⇒ 这是广播通知的规模悬崖。

> 🔴 **附带发现（注释说了假话）**：
> [`MessageAdminSendNotificationService`](/Users/pr/Documents/java&golang/RAG/service/message/message-api/src/main/java/com/rag/message/message/api/service/MessageAdminSendNotificationService.java)
> 的类注释写着「message-rpc 按 `page-size=200` **分批插库**」。
> **这句话是事实错误**：`broadcast.page-size` 只用于 `admin.GetUserList` 的 **RPC 拉取分页**，
> 插库侧没有任何分批逻辑。
> ⇒ §3.3 的又一次发作：**注释给了错误的安全感**，让读代码的人以为规模问题已经处理过了。

#### 6.7.3 三条「已签字但表述需要校正」的差异

| 项 | 现状表述 | 本轮校正 |
|---|---|---|
| **DRIFT-MSG-V-006** | RAG javadoc 标为「DRIFT-MSG-006 有意保留的差异，需 review 签字」 | 编号撞车（见 §6.7.0）；且**它并不能补偿 DRIFT-MSG-V-005** —— 补写 `updated_at` 只是多了一个 Go 没有的副作用，排序键仍然缺失 |
| **`String.trim()`** | RAG javadoc 定性为「有意保留的**表达层差异**，不在本项修复范围」 | **定性错误**。它改变了**输入解释**（全 NBSP 的 content：Go 判空→5003，RAG 入库）与**持久化结果**，按 §3.2 的字段差异规则**必须作为行为差异继续验证**，不能按「表达层」豁免 |
| **`bizCodeFromError` 判定顺序** | RAG javadoc 写「顺序重要：先识别 status，再识别 CodeError」 | **顺序无关**：Java 单继承下 `CodeError` 与 `StatusRuntimeException` 互斥，两个分支不可能同时命中。建议在 javadoc 补这句，否则后人会误以为顺序有语义而不敢动 |

#### 6.7.4 UNRESOLVED（本轮新增；**编号用 `UNRESOLVED-MSG-V-*`**，避开 §6.6 已占用的 -001..-005）

| 编号 | 问题 | 为何无法判定 | 需要什么 |
|---|---|---|---|
| ~~UNRESOLVED-MSG-V-000~~ | ~~JVM 实际时区 ⇒ V-007 是否真的发作~~ | — | ✅ **2026-10-22 用户已确认：部署时区 = Asia/Shanghai** ⇒ 该漂移**真实发作**。见 §6.7.10 |
| **UNRESOLVED-MSG-V-001** | Nacos namespace/group 不一致 | `message-rpc` 用 `public`/`DEFAULT_GROUP`，user/admin 用 `sea-rtw`/`USER_GROUP`（`message-api/application.yml:14-19` 自述） | 两侧对齐后才能做活体对拍 |
| **UNRESOLVED-MSG-V-002** | `insertBatch` 参数上限的确切触发点 | 静态推导（12 列 × 65535），**未做活体验证** | 造 5462 条 recipient 实跑一次 |
| **UNRESOLVED-MSG-V-003** | DDL 的索引 / 唯一约束 / NOT NULL / DEFAULT | `check-ddl-static.py` 需活库连接，本轮未运行 | 起 PG 后跑该脚本；时间列的 10 处差异本轮已单独取证 |

> ⚠️ **DRIFT-MSG-V-007 的条件化结论**（本轮唯一「影响面覆盖全部时间字段」的一条）：
> Go 侧 `gorm.io/driver/postgres@v1.6.0` 的 `postgres.go:251-255` 把 `schema.Time` 映射为
> **`timestamptz`**，且 DSN 显式钉 `TimeZone=Asia/Shanghai`（`db.go:22`）；
> RAG 侧 DDL 是 **`TIMESTAMP`（无时区）**，读出时用 `LocalDateTime.toEpochSecond(ZoneOffset.UTC)`
> （[`MessageRpcSupport.java:87,106,261-264`](/Users/pr/Documents/java&golang/RAG/service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/MessageRpcSupport.java)）。
> ⇒ **JVM 时区 = UTC 则完全一致；非 UTC 则出错。**
> ⚠️ **2026-10-22 19:08 实测校正：方向是「RAG 比 Go 晚 8 小时」，不是「早」。**
> （此前本文与首次报告都写成「早」，属推理错误，已由 `jshell` 实测算准。
> 该错误已作为 §3.3 同款的反面教材留在本节：<b>时区偏移的方向不能靠推理，必须实测。</b>）
> 配置维度上也不等价：**Go 显式钉了 session 时区，RAG 什么都没钉**。

#### 6.7.5 本轮确立的两条通用纪律

> **纪律 A：持久化查询的「过滤条件」必须逐条覆盖到「计数」而不只是「列表」。**
> DRIFT-MSG-V-001 的形态是：列表查询带了过滤、计数查询没带。
> 这在代码评审里极难一眼看出（两行相距 7 行、变量名都是同一个 `unreadOnly`），
> 且**单测极容易漏**——因为测「列表返回了正确的 2 条」不需要 `total` 参与断言。
> ⇒ 断言分页接口时，**`total` 与 `items` 必须成对断言**，否则 `total` 是无人区。

> **纪律 B：`String.trim()` / `trimStart()` / `strip()` 不得当作 `strings.TrimSpace` 的等价物。**
> 跨语言迁移时**空白字符集**是默认被忽略的输入解释维度。最小判法：
> 把 `"\u00A0"`（NBSP）、`"\u3000"`（全角空格）、`"\u2028"` 各写一条用例。
> 顺带记一个容易反向误判的点：`strip()` 用的是 `Character.isWhitespace`，
> 它**也**不认 NBSP（`isWhitespace('\u00A0') == false`），**两个都不是**，
> 要对齐 Go 必须自己写显式集合。

#### 6.7.6 本轮核对通过、无需改动的部分（留档以免重复审计）

- **错误身份**：21 项 code + msg 逐条 1:1；`msg → code` 反查表（排除 200/500 + first-write-wins）一致。
- **IDL**：9 个 RPC、全部字段号、3 个枚举值全部 1:1。
- **业务分支**：9 个 Logic 的校验顺序、错误码、gRPC code 逐条对齐，含 `5201/5204/5205/5206/5207` 这几个易错分支。
- **事务边界**：会话 + 消息原子写；事务内**语句序**（先 UPDATE 会话后 INSERT 消息，刻意对齐以避免加锁顺序相反）。
- **HTTP 契约**：除 401 外**恒 200 + envelope**；401 **空 body**；404 / 405 / 415 各自独立。
- **身份解析**：claim 缺失 / 非数字 / 非整数 / 超 int64 **全部 → 5004（HTTP 200，非 401）**；身份先于参数校验。
- **角色硬编码**：user 端点 `SenderRole=USER` + `RecipientRole=USER`；admin 端点 `ADMIN`。
- **解析层**：`,string` 双向接受（根因是 `json.Number.Kind() == string`）；`,optional` vs `,omitempty` 的 null 语义。
- **保护层四层参数值**：MaxConns 10000 / MaxBytes 1MiB / CpuThreshold 900 / Breaker 开。
- **client 熔断**：acceptable 判据 6 个 code 逐条对齐；**retry 两侧都无（保持不加）**。
- **超时**：rpc 2s（Go `zrpc/config.go:46` 默认 2000ms）/ http 3s（Go `rest/config.go:55` 默认 3000ms）。
- **`trimPreview` 上限是 80 码点**（不是 255 char、不是 UTF-16 code unit）——这个曾经修错的地方现在是对的。
- **`MarkNotificationRead` 单条 UPDATE + `RowsAffected==0 → 5203`**，原子性比 Go 修复前的形态更好。

#### 6.7.7 修复结果（2026-10-22 晚，与 6.7.1 同一批纪律）

| 编号 | 结论 | 修法一句话 | 防回退用例 | 变异验证 |
|---|---|---|---|---|
| **DRIFT-MSG-V-001** | ✅ 已修 | 新增 `NotificationMapper.countByRecipient(recipientId, role, kind, unreadOnly)`，`total` 与 `items` 共用同一个 `unreadOnly`；`countUnreadByRecipient` 改为委托它，避免两处漂移 | `listNotificationsTotalMustRespectUnreadOnly`（真 PG，**total 与 items 成对断言**） | ✅ 去掉 `unreadOnly` ⇒ 红 |
| **DRIFT-MSG-V-002** | ✅ 已修 | `insertNotificationsTx` 按 `INSERT_BATCH_SIZE=200` 切片，全部仍在同一 `@Transactional` 内 | `notificationBatchInsertMustSurviveTheParameterLimit`（真 PG，**6000 条**）+ `batchInsertMustStayAtomicAcrossChunks`（跨片仍原子） | ✅ 去掉切片 ⇒ 红 |
| **DRIFT-MSG-V-004** | ✅ 已修 | `listByConversationPage` 排序键 `id` → `created_at`（与 DDL 索引 `(conversation_id, created_at)` 对齐） | `conversationMessagesMustBeOrderedByCreatedAtNotId`（真 PG，**构造与 id 相反的次序**） | ✅ 改回 `getId` ⇒ 红 |
| **DRIFT-MSG-V-005** | ✅ 已修 | `listByParticipant` 补次级键 `orderByDesc(updatedAt)` | `conversationListMustUseUpdatedAtAsTiebreaker`（真 PG，**last_message_at 完全并列**） | ✅ 去掉次级键 ⇒ 红 |
| **DRIFT-MSG-V-006** | ✅ 已修 | 删掉 `markConversationReadTx` 里的 `touchUpdatedAt`（**理由**：DRIFT-MSG-V-005 修好后，它当初的补偿对象已消失） | `markConversationReadMustNotTouchConversationUpdatedAt`（真 PG + **正向确认消息真被标读**，防假绿） | ✅ 加回 `touchUpdatedAt` ⇒ 红 |
| **DRIFT-MSG-V-008** | ✅ 已修 | `trimContent` / `trimPreview` 改走 `trimGoSpace`；逐码点复刻 Go `unicode.IsSpace` 的 **25 个码点**（`go run` 实测枚举得出，非凭记忆） | `MessageRpcSupportTrimPreviewTest` +5 例（NBSP / U+3000 / 分隔符 / **U+001C–1F 反向断言** / 25 码点全枚举 / 全空白塌成空串） | ✅ 3 次：`trim()` ⇒ 4 红、**`strip()` ⇒ 4 红**、改共享 helper ⇒ 5 红 |
| **DRIFT-MSG-V-009** | ⏸ 留档 | 未改代码（Prometheus 规则适配，属运维侧） | — | — |
| **DRIFT-MSG-V-010** | ⏸ 留档 | 未改代码（当前无发作路径，见 6.7.4 说明） | — | — |

##### 🔴 由新测试**当场挖出**的 3 条 P0 —— 静态审计两轮都没抓到

这三条**不在 §6.7.1 的 10 项里**，是写 `MessageSemanticEquivalenceTest` 跑真 PG 时暴露的。
**`message-rpc` 此前 21 条测试 + `message-api` 59 条 + `semantic-probe` 23 例，
没有一条能碰到它们** —— 再次印证 §6.6.4 的结论。

| 编号 | 缺陷 | 后果 | 修法 | 用例 |
|---|---|---|---|---|
| **DRIFT-MSG-V-011** | `insertBatch` 里写的是 `#{item.isRead}`，而实体字段叫 `read`（getter `isRead()`）⇒ MyBatis `Reflector` 找不到属性 | `org.apache.ibatis.reflection.ReflectionException: There is no getter for property named 'isRead'` ⇒ **`SendNotification` 100% 失败**。而 `insertBatch` 是通知写入的**唯一生产路径**（`notificationMapper.insert(` 在 main 下零命中），`sendNotification` 又**零测试覆盖** ⇒ **admin 的定向通知与广播从未成功过一次** | `#{item.read}` | `notificationBatchInsertMustSurviveTheParameterLimit` |
| **DRIFT-MSG-V-012** | `listByParticipant(role, id, limit, offset)` 的调用点传的是 `(…, in.getOffset(), limit)` ⇒ 实参落到 `limit=offset, offset=limit` | SQL 变成 `LIMIT <offset> OFFSET <limit>`，`offset=0` 时即 **`LIMIT 0`** ⇒ **`ListConversations` 永远返回空列表**（会话列表页全空）。与 DRIFT-MSG-002 同款形态：一个被写错的普通值让整条路径静默失效、**不产生任何错误日志** | 修正实参顺序 | `conversationListMustUseUpdatedAtAsTiebreaker`（`itemsCount` 期望 2 实得 0） |
| **DRIFT-MSG-V-013** | `extra` 列是 `jsonb`，但用的是 MP 自带 `JacksonTypeHandler` —— 其 `AbstractJsonTypeHandler.setNonNullParameter` 反编译出来是 **`ps.setString(...)`**，pgjdbc 按 **VARCHAR** 发送 | `ERROR: column "extra" is of type jsonb but expression is of type character varying` ⇒ 通知写入再次失败。且 `@TableName` 缺 `autoResultMap = true` ⇒ **读**方向同样失效 | 新增 `JsonbMapTypeHandler`（`ps.setObject(i, json, Types.OTHER)`）+ `@TableName(autoResultMap = true)` | `notificationBatchInsertMustSurviveTheParameterLimit` |

> **DRIFT-MSG-V-013 的两条通用结论**（对全仓 MyBatis-Plus 模块都成立）：
> 1. **MP 自带的 `JacksonTypeHandler` 不能直接用于 `jsonb` 列** —— 它用 `setString` 绑定，
>    pgjdbc 会带显式 VARCHAR 类型，Postgres 直接拒收。正确写法是 `Types.OTHER`。
> 2. **`@TableField(typeHandler = …)` 写在字段上 ≠ 它在 SELECT 上生效** ——
>    MyBatis-Plus 只有在 `@TableName(autoResultMap = true)` 时才把 typeHandler 应用到
>    自动生成的结果映射。这是 §3.3「断言 Bean 存在 ≠ 断言机制生效」在 ORM 层的同款形态。

##### 🔴 本轮踩到的方法论陷阱：**Maven 增量编译会把变异 class 留在 target 里**

做 §6.6.5 要求的变异验证时，8 个变异点跑完、源码**全部还原**之后复跑，
**仍然红**（`column "extra" is of type jsonb`），而源码里 `JsonbMapTypeHandler` 明明是
`ps.setObject(i, ..., Types.OTHER)`。加 `clean` 后立刻全绿。

⇒ **本仓做变异测试时，每次变异与每次还原都必须 `mvn clean`**，
否则**「还原后复跑」这一步测的不是当前源码，而是上一次变异残留的 class**。
这条会让「变异 → 还原 → 复跑」的标准闭环得出**假的红灯**，
进而得出「测试不稳定 / 修不好」的**假结论** —— 比不测更坏。

##### ⚠️ 另一条同源陷阱（本轮也踩到）：**编译错误不会出现在你习惯 grep 的那几行里**

`JsonbMapTypeHandler` 首版有未捕获的 `JsonProcessingException`，编译失败。
而当时的验证命令是
`mvn -q ... | grep -E "Tests run:|ERROR\]   |ERROR: "`，
**编译错误的格式是 `[ERROR] /path:[line,col] 未报告的异常错误…`，一条都没匹配上**，
输出里只剩一个 python 的 `ok` —— 看起来像「跑完了、没报错」。

⇒ **验证纪律**：**先看退出码**（`EXIT=$?`），再看测试统计。
用 `grep` 过滤 mvn 输出时，**必须额外匹配 `[ERROR] /`（编译错误行）**，
或干脆先 `if [ $? -ne 0 ]` 判断。**「过滤后没输出」≠「没有失败」。**

#### 6.7.10 DRIFT-MSG-V-007 决策与修复：部署时区 = Asia/Shanghai（2026-10-22 19:08）

**前置**：§6.6 已把它登记为 `DRIFT-MSG-010` 并挂在 `UNRESOLVED-MSG-001`「JVM 运行时区未知」上。
用户于 2026-10-22 19:08 确认 **message 服务的部署时区是 Asia/Shanghai** ⇒ 该条目**解除挂起，本轮关闭**。

##### 🔴 第一件事：纠正方向 —— 我此前写反了，且是靠推理得出的

首次报告与 §6.7.4 都写「RAG 比 Go **早** 8 小时」。**方向反了：实际是「晚」**。
`jshell` 实测（JVM 时区 = Asia/Shanghai）：

```
JVM default zone       = Asia/Shanghai
写入的墙钟(无时区)      = 2026-10-22 19:08:17
真实瞬时(trueInstant)  = 2026-10-22T11:08:17Z

Go  .Unix()             = 1792667297
RAG toEpochSecond(UTC)  = 1792696097   差 +8 小时
修法 atZone(system)     = 1792667297   差  0 小时
```

⇒ **RAG 的 `created_at` / `latest_message_at` / `updated_at` 比真源晚 8 小时。**
⇒ 与 §3.3 同款的反面教材：**时区偏移的方向不能靠推理，必须实测。**
（推理为什么容易反：`toEpochSecond(UTC)` 把「+08:00 的墙钟」当成 UTC，得到的 epoch **偏大**；
但人直觉上「少了一次换算」容易往「偏小」想。）

##### 根因

Go 侧时间列是 **`timestamptz`**（gorm postgres driver `postgres.go:251-255` 把 `schema.Time`
映射成 `timestamptz`），DSN 钉 `TimeZone=Asia/Shanghai`（`db.go:22`）⇒ 读出 `time.Time`（绝对瞬时）
⇒ `.Unix()` **与时区无关**。
RAG 侧列是 **`TIMESTAMP`（无时区）**，实体 `LocalDateTime`，pgjdbc 对无时区列**不做换算**
⇒ 这是一个**不含 epoch 信息的墙钟**，而读侧却用 `toEpochSecond(ZoneOffset.UTC)` 把它当成 UTC。

##### 修法：读侧统一走 `atZone(ZoneId.systemDefault())`

`MessageRpcSupport` 新增私有 `toEpochSecond(LocalDateTime)`，替换 **4 处**
（`toNotificationItem` / `toMessageItem` / `buildConversationItem` 的 `latestMessageAt` 与 `updatedAt`）。

**为什么用 `systemDefault()` 而不是硬编码 `Asia/Shanghai`**：
写侧用的是 `LocalDateTime.now()`（= JVM 本地墙钟），读侧也用本地时区解释 ⇒ **写读自洽**，
**无论 JVM 跑在哪个时区 epoch 都正确**。硬编码 `Asia/Shanghai` 反而会在「JVM 改成 UTC」的部署上错。
⇒ **本条的决策不是「部署时区必须是上海」，而是「写读必须用同一个时区解释」** ——
后者才是可长期成立的性质。

| 防回退用例 | 锁住的性质 | 变异验证 |
|---|---|---|
| `notificationCreatedAtMustBeTheTrueInstantInShanghai` | 上海时区下等于真实瞬时 | ✅ 退回 `toEpochSecond(UTC)` ⇒ 红 |
| `mustStaySelfConsistentUnderADifferentJvmZone` | **换到 UTC 仍自洽**（禁止硬编码时区） | ✅ 硬编码 `Asia/Shanghai` ⇒ 红 |
| `messageCreatedAtMustUseTheSameConversion` | 会话消息走同一套规则 | ✅ 同上 ⇒ 红 |
| `conversationTimestampsMustUseTheSameConversion` | 会话两个时间字段同样 | ✅ 同上 ⇒ 红 |
| `epochMustDenoteAbsoluteInstant` | epoch 是绝对瞬时（还原回墙钟 == 写入值） | ✅ 同上 ⇒ 红 |
| `nullTimestampsMustStillMapToZero` | null 仍回 0，修时区不得改 null 语义 | — |
| `notificationTimestampsMustRoundTripLosslessly`（真 PG） | **`TIMESTAMP` 列对墙钟无损** —— 整个修法依赖这个前提 | — |

> ⚠️ **每条用例都自己 `TimeZone.setDefault(...)` 并在 finally 还原**：
> 跟着宿主机时区跑的话，在 UTC 机器上修复前后结果**完全相同**（差 0）⇒ 用例假绿。
> 判据必须与宿主机时区无关（§6.7.5 纪律 B 的同源问题：**默认值域也要显式钉**）。

##### ⚠️ 遗留的稳健性缺口（**不在本次修复范围，已留档**）

列类型仍是 `TIMESTAMP` 而非 `TIMESTAMPTZ`，数据**不自描述瞬时**。上表的自洽性成立的前提是
「一个 DB 只由同一时区的 JVM 写」。以下情形会重新错位：

1. 迁移期新旧实例并存、且两个实例时区不同；
2. 换时区后回读历史数据；
3. 同一张库被另一个时区的进程写入。

**根治需要一条 `V4` 迁移**把三张表的时间列改成 `TIMESTAMPTZ`
（`ALTER TABLE … ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Shanghai'`），
**且读侧仍需保留 `atZone(systemDefault())`**（因为实体仍是 `LocalDateTime`，pgjdbc 按 JVM 时区折算）。

**本轮不做该迁移的理由**：① 行为已通过读侧修法完全正确，迁移不改变当前可观察行为；
② `ALTER TABLE` 在已有数据的表上有锁表与回填风险；
③ §6.7.4 的 `UNRESOLVED-MSG-V-003`（索引/唯一约束/NOT NULL/DEFAULT 完全 NOT CHECKED）说明
**这张 DDL 还没有被系统性核对过**，在没核对前做类型迁移是盲改。
⇒ 记为**已知缺口**，与 `tools/difftest/check-ddl-static.py` 的跑库核对一起做。

#### 6.7.8 DRIFT-MSG-V-003 决策：**签字保留 RAG 侧，不对齐 Go**（2026-10-22）

**决策：不改代码。** RAG 的 `5001` / `5008` 比 Go 的 `1015` 更正确，对齐过去是**引入缺陷**。

| 判据 | Go 行为 | RAG 行为 | 谁更对 |
|---|---|---|---|
| 业务码语义 | `1015` "service busy" —— 把**数据库故障**说成**服务繁忙** | `5001` "internal server error" / `5008` "database update failed" | **RAG** |
| 信息泄露 | `shared.RPCError` 把 gRPC description（= gorm 原始错误文本）塞进 envelope：<br>`{"code":1015,"msg":"ERROR: relation \"x\" does not exist (SQLSTATE 42P01)"}` | 只回码表文案 | **RAG** |
| 可运维性 | DB 故障被计为「服务繁忙」⇒ 值班会去看**容量/限流**，而故障在 DB | 码可区分「系统内部」与「写库失败」 | **RAG** |

**签字理由**：AGENTS.md 的对齐原则是「复刻 Go 的**行为**」，而**不是**「复刻 Go 的**缺陷**」。
Go 侧这条同时命中信息泄露与错误归因两条，已属缺陷而非语义。

**必须一起签字的代价**（不签字就会被人当 bug 报回来）：
- ⚠️ **监控 / 告警规则若按 `code == 1015` 判定「下游熔断」，迁移后不再命中** ——
  基础设施故障现在落在 `5001` / `5008`。**告警规则需要同步改**。
- ⚠️ **业务错误路径完全一致**（`5002/5003/5004/5005/5201-5207` 逐条核对过），
  所以这条差异**只在基础设施故障时显现**，压测与正常流量测不出来 ——
  也正因如此，它不会被「测试全绿」自动发现，必须靠本节留档。

#### 6.7.9 本轮测试资产总账

| 类 | 规模 | 覆盖 |
|---|---|---|
| `MessageSemanticEquivalenceTest`（新增，真 PG Testcontainers） | **7** | DRIFT-MSG-V-001 / -002 / -004 / -005 / -006 / -011 / -012 / -013 |
| `MessageRpcSupportTrimPreviewTest`（+5 例） | **11** | DRIFT-MSG-V-008 |
| `MessageRpcSupportTimeZoneTest`（新增，纯单元） | **6** | DRIFT-MSG-V-007 |
| `MessageGrpcInterceptorWiringTest` / `MessageTransactionWiringTest` / `MessageMarkNotificationReadTest` | 15 | §6.6 的 DRIFT-MSG-001/002/003 |
| **message-rpc 合计** | **39**（原 21） | — |

**message 域回归（2026-10-22 19:26，隔离跑）**：

| 范围 | 结果 |
|---|---|
| `message-rpc`（39 例） | ✅ 全绿 |
| `message-api`（100 例，**单模块隔离连跑两次**） | ✅ BUILD SUCCESS，0 错误 |
| `tools/semantic-probe/run-parity.sh` | ✅ 23/23 一致，EXIT=0 |

> ⚠️ **全仓口径当前不可复现**：本机有另一个会话并行跑 Maven（6 个进程），
> 持续改写 `service/like/**` 并重建共享 `target/` 与 `~/.m2` SNAPSHOT jar。
> 在并发构建期间出现的 `like-rpc` 编译失败与 `MessageApiMvcPipelineTest` 的
> 41 条 `ClassNotFoundException`，**均与本轮改动无关**（该类在 `users-common/target/classes`
> 与已安装 jar 里都存在；本轮未碰 `GlobalExceptionHandler` / `message-api/pom.xml` / `users-common`）。
> ⇒ 引用测试数字时**必须标注「隔离跑」还是「全仓跑」**。

> ⚠️ **仍然零覆盖的**：`sendNotification` / `sendChatMessage` 的**端到端 happy path**（走真 gRPC 端口）、
> `listConversations` 的分页翻页、任何 ≥5461 收件人的真实广播。
> 本轮的 `MessageSemanticEquivalenceTest` 是**进程内直调 service 层**，不是双端口对拍；
> 真源对拍仍要等 `UNRESOLVED-MSG-003`（Go 真源在本环境起不来）。

### 6.8 `service/hot/hot-rpc` 首次实现：Go `rpc` + `heavykeeper` → Spring Boot（2026-10-22 晚）

> **性质与其他 §6.x 不同**：§6.6 / §6.7 都是「审计已存在的 RAG 实现」；
> 本节是**首次从零实现**一个 Component。接手时的状态是 **13 个 `.java` 全部只有 12 行 javadoc**
> （`public class X {}`），`target/classes` 里却已有 `.class` ⇒ **`mvn compile` 全绿而行为为零**。
> ⚠️ 这是 §6.6.5 那条方法论（「缺失的不是测试数量，而是让代码真正跑一次这个动作」）的**最强实例**：
> 一个模块可以「编译成功 + 有 class 文件 + 完全没有任何实现」。

#### 6.8.1 Go Component 的真实边界（先追踪，后落笔）

Go 侧范围：`service/hot/rpc/`（`hot.go` 75 行 + `etc/`×3 + `internal/{config,svc,server,logic×5,mqs×2,metrics}`）
\+ 强依赖 `service/hot/heavykeeper/heavykeeper.go`（210 行）。

**执行路径**（两条，互相独立）：

```
① 查询面：GetHotArticles → ZREVRANGE hot:articles 0 k-1 WITHSCORES → uint32(score)
② 消费面：sarama ConsumerGroup → TraceConsumer span → 3 类校验 → eventConsumeTotal.Inc
           → hk.Add → 每 100 条异步 syncToRedis → 30s periodicSync → 10s periodicMerge + 分布式锁
```

**依赖追踪的边界判断**：`KqPusher`（建了零调用）、`RedisConf.Type`（`svc` 只用 `Addr`+`Password`）、
`KqConsumerConf.Name`（hot 未读）、`metrics.InitMetrics`（全仓 grep 确认零调用点）
—— 四项**在 Go 侧本身就是死配置/死代码**，**刻意不迁**（不给死代码建结构）。
⚠️ 与 `hot-common` 那条「like-rpc 的 258 行 `LikeGrpcError` 是反面样本」同源判据。

#### 6.8.2 L1 外部契约（逐项对照，均有测试锁死）

| 契约 | Go 出处 | Java 落点 | 锁定测试 |
|---|---|---|---|
| 5 个 gRPC 方法 + proto | `hot.proto` / `hot_service_server.go` | `HotServiceGrpcImpl`（`@GrpcService`） | `HotServiceGrpcImplErrorMappingTest` |
| 3×`UNIMPLEMENTED` + 1×`success=true` | `like:44` / `coin:36` / `share:36` / `comment:30` | `InteractionStubService` | `InteractionStubServiceTest`（含「恰好 3:1」的计数断言） |
| **裸 error → `UNKNOWN`** | `get_hot_articles_logic.go:50` 的 `return nil, err` | `invoke()` 的兜底分支 | 同上（`redisFailureMapsToUnknownNotInternal`） |
| `TopK` 归一 `≤0→20` / `>100→100` | `:35-40` | `HotRankingQueryService` | `HotRankingQueryServiceTest`（verify 读取范围 `0,19` / `0,99`） |
| `uint32(z.Score)` **向零截断** | `:62` | `HeavyKeeper.scoreToCount` | `HeavyKeeperUint32OverflowTest` |
| `redis.Nil` → 空列表不报错 | `:45-48` | Spring 返空集合，天然等价 | `missingKeyReturnsEmptyListWithoutError` |
| topic / group / offset | `hot.yaml` | `spring.kafka.*` | 配置文件（未做自动化断言） |
| Redis keyspace 5 个 + 两个 TTL | `hot_handler.go:208,209,390,25` | `HotRedisSnapshotStore` / `HotMergeScheduler` | ⚠️ **无 IT**，见 6.8.6 #5 |
| 指标名 `article_hot_{kafka_consume,redis_sync}_total` | `hot_handler.go:30-45` | `HotRpcMetrics` | 变异 M9（改名字即 3 条红） |

⚠️ **`UNKNOWN` 不是 `INTERNAL`，这条极易被 message-rpc 的模板带偏**：
message-rpc 的兜底统一 `INTERNAL`，因为它把业务错误预先包成了 `StatusRuntimeException`；
hot-rpc 的 `GetHotArticles` 返回的是**裸 error** ⇒ grpc-go `status.FromError` 返回 `ok=false` ⇒ `UNKNOWN`。

#### 6.8.3 L2 业务语义

| 语义 | Go 出处 | 说明 |
|---|---|---|
| **指标 Inc 早于 `hkMap` 查找** | `:168` → `:171` | 分区未持有时**仍然计数**，只是不入 sketch。⚠️ 变异 M4 证明这条被锁死 |
| 无条件 `MarkMessage` ⇒ 坏消息永久 drop | `:108` | listener 永不外抛 + `DefaultErrorHandler` 零重试 + String payload 手动解析（三重） |
| 全局计数器 `% syncEvery`（**非每分区**） | `:179` | 分区越多单分区快照越稀疏 |
| 权重按 **`Name`** 建 map，`Code` 丢弃 | `hot.go:47-50` | `code` 字段**保留在配置里**但不参与分发（`hot2/hot3.yaml` 的唯一结构差异就是它的缺失） |
| 回灌是**新估计**不是原值 | `:279` | sketch 桶计数与真实次数无逆关系 |
| 合并是**有损的二次有损** | `:356-415` | 每片 Top-100 之和再取 100 ⇒ 每片都排 101 名的文章永不上榜 |

#### 6.8.4 L3 行为相关技术语义（本组件**无 DB / 无事务 / 无鉴权**）

| 项 | 结论 |
|---|---|
| MQ 失败处理 | drop 而非 retry（理由见 6.8.3） |
| 分区分配 | `RoundRobinAssignor`（Go `NewBalanceStrategyRoundRobin`）；⚠️ Spring 默认是 `RangeAssignor`，**改了会改变分区归属进而改变 `hkMap` 布局** |
| gRPC deadline | `observability.rpc-timeout: 2s`（Go `zrpc` 默认 2000ms，`DisableNativeRpcTimeout` 转移给观测拦截器） |
| concurrency | Go 的 `go func()` 扇出 → Spring `TaskExecutor`（`applicationTaskExecutor`）；**锁策略见 6.8.5** |
| error propagation | 校验失败 → 作为 span error 记录 → 在 listener 层丢弃 |

#### 6.8.5 🔴 一处**有意偏离 Go**：撤销路径的锁升级（G-HOT-001）

Go 形态：`Lock()` → 内部函数 `RLock()` ⇒ `sync.RWMutex` 不可重入 ⇒ **必然自死锁**（已用最小 Go 程序复现：
`revoke` 打印「write lock held」后再无输出，随后 `consumeMessage` 的 `RLock` 也拿不到 ⇒ 整个 `HotHandler` 永久冻结）。

**为什么不能照抄**：Java 的 `ReentrantReadWriteLock` **允许**写锁持有者再取读锁
⇒ 1:1 直译会把死锁**悄悄变成能跑**，两套行为不一致且零告警；而刻意在 Java 里写死锁会让 L2 语义（rebalance 落盘）彻底落空。

**采用**：Go 另外两个调用点（`periodicSync:250-255`、`consumeMessage:170-172`）已有的正确形态 ——
**锁内取引用 → 锁外做 Redis I/O**。`HotPartitionSketchRegistry.detach` 在写锁内原子移除并返回引用，
由调用方在锁外落盘。

⚠️ **该偏离的可验证形式不是「会不会卡住」**（Java 锁允许降级，卡不住），
**而是「锁的持有时长」**：用 latch 卡住 `flush` 制造慢 Redis，断言读者仍能立即返回
⇒ `HotRevokeFlushOutsideLockTest`（变异 M8' 证明：落盘挪回写锁内即变红）。

⚠️ **本条需用户签字**；若要求「连缺陷一起复刻」，改回 `Lock` 内落盘即可，但那样 L2 的 rebalance 落盘语义不再成立。

#### 6.8.6 证据边界（**五条，不得越读**）

1. **`hot-proto` 的 protoc 插件是本次新加的** —— 此前 `hot.proto` 从未被编译成 Java，`HotServiceGrpc` 从未存在。
2. **探针不入 Go 仓** —— 对拍探针在 `/tmp`（`go.mod` 用 `replace` 指向真源），Go 仓零改动。
3. **隔离跑，非全仓口径** —— §〇 已警告本机有并发 Maven；本节数字均来自
   `mvn -o -pl service/hot/{hot-proto,hot-common,hot-rpc,hot-api} -am install`。
4. **`like-rpc` 有同款缺依赖问题**（只引 `spring-kafka`、无 `spring-boot-kafka`），**本次未动**。
5. **无真 Redis / 真 Kafka IT** —— 4 段 Lua、`EXISTS` 守卫、`RENAME` 原子性、两个 TTL、
   RoundRobin 分配、零重试 handler 全部只有代码级证据。需 Docker 环境补 IT。

#### 6.8.7 本轮确立的通用纪律

**① 「注解存在」≠「机制生效」，Boot 4 的模块化迁移让这一点更隐蔽（⇒ 已写入 §3.3.4）**
`@KafkaListener` 在缺 `spring-boot-kafka` 时被**完全静默忽略**：不报错、不启动失败、容器照常起来。
这与 §3.3 已有的「`@Component` ≠ 自动注册」同型，但**更难发现**——后者至少类 Bean 不存在，
前者连类都不存在。⇒ **凡用 `@KafkaListener`，必须有一条「listener 真的在容器 registry 里」的测试。**

**② 「异常被降级处理」会同时遮蔽下游 bug**
`HotEventConsumer` 必须吞掉一切（drop 契约），而正是这条契约把 `HeavyKeeper.hash()` 的
`ArrayIndexOutOfBounds` 变成了「事件静默丢弃 + 热榜永不更新 + 启动零报错」。
⇒ **凡实现「永不外抛」的入口，必须同时给被调方配一条能暴露异常的独立测试**
（本轮靠 `HeavyKeeperTest` / `HeavyKeeperUint32OverflowTest` 达成）。

**③ 变异验证第一轮会有假绿，必须逐条归因**（10 个变异里 3 个首轮未变红）：

| 未捕获 | 归因 | 处置 |
|---|---|---|
| 最小堆比较器改有符号 | **测试设计缺陷**：最终闸门走的是**直接**无符号比较，绕过了比较器 | 重设输入（TopK=2 + 权重落在两值之间），round 3 已捕获 |
| 坏消息不再返回 error | 变异打在了**错误的层**（内部 barrier 拆不掉） | 顺带发现**真实语义漂移**：span 被误标成成功 ⇒ 重构 + 补断言 |
| 落盘挪回锁内 | Java 锁允许降级 ⇒ **结构上不可能触发死锁** | 改用「锁持有时长」作为判据（见 6.8.5） |

⇒ **「变异未变红」本身是一条结论**，要么测试设计错、要么该变异在本语言下不可达，
**不能默认当作「守门有效」**。

**④ `uint32` 移植到 Java 的两个必查点**（本组件唯一的有损算法，两处都踩过）：
① **加/减**两语言都自然回绕（两补码）⇒ 不会错；② **比较**必须是 `Integer.compareUnsigned`
/ `Integer.toUnsignedLong` ⇒ 写 `>` / `<` 就与 Go 分岔。
⚠️ 同理 `long` 的无符号取模**不能**写 `h & 0xFFFFFFFFFFFFFFFFL`（该字面量位模式全 1，即 `-1L`，掩码无效）。

---

## 七、引用清单

### 7.1 真理之源（判定行为）

**`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`** —— 冻结的唯一真源

| RAG 子模块 | Go 对应路径 |
|---|---|
| `service/users` | `<go>/service/user` |
| `service/message` | `<go>/service/message` |

常用锚点（审计 `service/users` 时已验证）：

| 主题 | Go 路径 |
|---|---|
| 路由 / 鉴权中间件装配 | `service/user/user/api/internal/handler/routes.go` |
| 响应封装（显式 `httpx.OkJson`） | `service/user/admin/api/internal/handler/admin/*.go` |
| 建表（`AutoMigrate`，无状态） | `service/user/{user,admin}/rpc/internal/model/db.go` |
| 事务边界（闭包传 `tx`） | `service/user/admin/rpc/internal/logic/createadminlogic.go` |
| 邀请码行锁（`clause.Locking`） | `service/user/admin/rpc/internal/model/admin.go` |
| 身份解析（**走 gRPC**，边界正确的范例） | `service/user/user/identity/resolve.go` |
| JWT 签发（无长度校验） | `service/user/common/jwt/jwt.go` |
| 字段与 ORM 映射（struct tag） | `service/user/admin/rpc/internal/model/*.go` |

常用锚点（审计 `service/message/message-rpc` 时已验证，2026-10-22）：

| 主题 | Go 路径 |
|---|---|
| 9 个 RPC 的业务分支（`SendChatMessage` 最复杂） | `service/message/rpc/internal/logic/*.go` |
| 跨 RPC 共享语义工具（`canSend` / `peerOf` / `canonicalParticipants` / `TrimPreview`） | `service/message/rpc/internal/logic/helper.go` |
| **事务边界（显式 `conn.Transaction`）** | `service/message/rpc/internal/model/message.go:186`、`:196` |
| 建表（`AutoMigrate`，`TimeZone=Asia/Shanghai` 写在 DSN 里） | `service/message/rpc/internal/model/db.go:20` |
| 业务码 → gRPC status 的双向转换（`bizCodeByMsg` 反查表） | `service/message/common/errmsg.go` |
| 服务发现与超时（yaml 未配 `Timeout` ⇒ 走 go-zero 默认 2000ms） | `service/message/rpc/etc/message.yaml` |

### 7.2 本仓库

- 根目录：`/Users/pr/Documents/java&golang/RAG`
- **`FileTree/`（14 份，§6.2 多数行的证据出处）** —— 按 §3.1/§3.2 推导的**理论 Java 文件树**，每份含 §12.1–§12.7；**§12.5 是可直接落地的纯文件树**，`§12.7` 是不通过项与 `UNKNOWN` 的汇总。
  - `common.md`、`message/{api,rpc}.md`、`hot/{api,rpc,common}.md`、`like/{api,rpc}.md`、`article/{api,rpc}.md`、`users/{user,admin}/{api,rpc}.md`
  - ⚠️ **两个 `common` 不是同一个组件**：`common.md` = Go `service/common`（跨服务共享，14 文件）；`hot/common.md` = Go `service/hot/common`（**hot 专属，仅 1 个 `errmsg.go`**，2026-10-22 从 `hot/api.md` 拆出）。边界声明见 `FileTree/hot/common.md` §12.1
  - **状态**：`article` / `hot` / `like` 的 RAG 模块**仍是空骨架**（`article-api` 29 个文件 + `article-rpc` 41 个文件、`hot-api` 13 个文件，**全部只有 4~12 行 javadoc**），其树为**待实施**目标；`users` / `message` 的树与现存代码对照过，含已知偏差（见各文件 §12.7）
    ⚠️ **例外（2026-10-22 晚起）**：`article-common`（1 个类 + 15 条契约测试）、`hot-common`（2 个类）、`hot-rpc`（已首次实现）**已落地并做过 Go ↔ RAG 对拍** ⇒ 这三个 `-common`/`-rpc` 模块**不再是纯骨架**，其状态见 §0 与对应小节。`article-api` / `article-rpc` 的**空骨架事实依然成立**。
  - **消费约定**：文件树是**迁移期脚手架**，不是长期规范。按 phase 消费——某 phase 跑绿并 review 后，对应文件即可退休
- 模块 `HUMAN_ONLY.MD`（**4 份，2026-10-22 glob 实测校正**）：
  - `service/users/user/user-rpc/HUMAN_ONLY.MD`
  - `service/users/user/user-api/HUMAN_ONLY.MD`
  - `service/users/admin/admin-rpc/HUMAN_ONLY.MD`
  - `service/users/admin/admin-api/HUMAN_ONLY.MD`
  - ⚠️ 原清单另列的 `users-common` / `message-rpc` / `message-api` 三份**不存在**，已从本清单移除（详见 §2）
- **双侧语义探针**：`tools/semantic-probe/`（`go/main.go` + `java/Probe.java` + `run-parity.sh` + `README.md`）
  - 用途：Go 半边跑**真源镜像 struct** + go-zero v1.10.2 `core/mapping`，Java 半边跑**项目真实 Jackson3 + 项目 DTO**，逐用例比对 ACCEPTED/REJECTED
  - 现状：**23 例全一致，`EXIT=0`**；已做变异验证（任一侧改坏即红）
  - 复现：`bash tools/semantic-probe/run-parity.sh`（cwd 无关）
  - ⚠️ 这是 AGENTS.md §3.3「凡写『与 Go 一致』必须有两侧同时跑的探针」的**首个落地实现**；
    其余模块要做同类断言时，**照这个目录的结构复制一份，不要在 `/tmp` 写一次性脚本**（无法被下个 agent 重跑）
- **错误码表双侧探针**：`tools/errmsg-probe/`（`go/main.go` + `go/errmsg_probe.go` + `java/Probe.java` + `run-parity.sh`）
  - 用途：**错误码契约**（码值 / 文案 / 未知码回落）的 Go ↔ Java 逐字节对拍；
    Go 半边把**真源 `errmsg.go` 逐字节复制**进临时模块并**同包编译**（因此能读未导出的 `codeMsg` 表），
    Java 半边跑 **article-common 的真实编译产物 `ErrorCode`**
  - 现状：article 域 **49 行 dump（CONST 15 + TABLE 15 + FALLBACK 19）sha256 逐字节一致**（`cad5cb4f…`），`EXIT=0`；
    已做 **8 项变异验证（契约测试 + 探针双层同时变红）**
  - 复现：`bash tools/errmsg-probe/run-parity.sh`（cwd 无关；`GO_REPO=...` 可覆盖 Go 真源路径）
  - ⚠️ 依赖 **Go toolchain**（≥1.25），但**不需要 Go module cache**（临时模块零外部依赖，`GOPROXY=off` 可跑）
- 任务指南：`RAG/.agents/`（不得削弱本文件的规则）
- spec：`RAG/.agents/specs/<slug>.md`

### 7.3 已知的 Go 侧孤儿（勿照搬）

`service/user/rpc/internal/logic/` 是 7 个 `_test.go` 的孤儿目录，全仓无任何引用——搬迁时不要把它一起带过来。