<!-- @new 2026-10-27 · `article-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）** -->

# `article-common` lane 台账 —— 「article ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/article/article-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `article-common`（**原第 1/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot`<br>⚠️ 原 `align/article-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/article/{article-api, article-rpc, article-common, article-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/article/**` ＋ `docs/DRIFT/article/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-HOTAPI-030` | Tomcat 数「**总参数个数**」vs Go 数「**非空值个数**」⇒ 2049~10000 段且非空值 ≤2048 时 **Go 200 / RAG 5001** | `service/common`（X-6 共享层） | ✅ **已落地**（global 第十四轮 · 6 个共享类 + 每域一行 yaml）<br>✅ **本域侧已验**（核对日期 **2026-10-27**）：`article-api/src/main/resources/application.yml:159` = `mode: raw` ⇒ 与 Go 同向（Go 侧 `service/article/api/` **未注册** `httpx.SetErrorHandler` ⇒ `httpx.ErrorCtx` ⇒ **400 + text/plain + nosniff + Go 原文**；见 `rest/httpx/responses.go:155`） | [`api.md`](api.md) |
| `DRIFT-ARTICLE-API-006` | Go 必填字段校验：`core/mapping` **只认 `,optional`**，`,omitempty` **不算** optional ⇒ Go 缺字段即 400 | ⚠️ **落点已订正**：`service/users/users-common/src/main/java/com/rag/users/common/http/{GoStyleRequired,GoStyleRequiredFields,GoStyleRequest,GoRequiredFieldException}.java`（**实测四类全在 `users-common`**，不在 `service/common`）⇒ 仍是**共享层** ⇒ G4 写权 | ⚠️ **状态已订正**（核对日期 **2026-10-27**）：**article 域已自实现**，原记「🔴 未修」是**过期**。<br>**实现**：`article-api/.../dto/CreateArticleReq.java:57`（`implements GoStyleRequiredFields`）`:109-119`（`assertGoRequiredFields()` ⇒ `GoStyleRequired.field("title"/"content", null)` + `GoStyleRequired.explicitNull(...)`）`:69-96`（`@JsonProperty` setter 记录 **presence**，区分「缺席」与「显式 null」）<br>**⇒ 转派 G4 的剩余项已缩小**为「**共享 helper 的文案/口径基线取自 users 域，未覆盖 article 的 `title`/`content`**」（不是 article 域的缺口） | [`api.md`](api.md) |
| `S-6` | `SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被 `invoke` 的 `catch(RuntimeException)` 降级为 `UNKNOWN` ⇒ 业务码 5001 → 1015 | `service/common` | ✅ **已改**（纯 Mockito 证据）<br>✅ **本域侧复核：在 article 域不成立**（核对日期 **2026-10-27**）—— `article-rpc/.../service/impl/ArticleServiceGrpcImpl.java:119`（`invoke`）→ `:127`（`catch (StatusRuntimeException e)` **在前**）→ `:147`（`catch (RuntimeException e)` **在后**）⇒ `DEADLINE_EXCEEDED` **不会**被二次抹掉 | [`rpc.md`](rpc.md) |
| （域侧 · 非编号） | `article-rpc` **缺 `baseline-version: 0`** ⇒ `baseline-on-migrate=true` + 非空 schema ⇒ `V1` 被整条跳过（起得来、首次查库才炸） | **域侧** `service/article/article-rpc/src/main/resources/application.yml:53-57` | 🔴 **未修**（核对日期 **2026-10-27** 实测确认：flyway 块只有 `enabled` / `baseline-on-migrate` / `locations` / `schemas` 四行，**无 `baseline-version`**、无 `table:`）<br>⚠️ **AGENTS.md §6.3 问题 4 只点名了 `favorite-rpc` 这个例外 ⇒ 记载不完整，漏了 `article-rpc`**（⇒ 共享面台账订正项） | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。
> ✅ **2026-10-27 复核轮已就地订正 3 条过期状态**（`DRIFT-ARTICLE-API-006` 落点+状态 · `S-6` 适用性 · `baseline-version`）——
> ⇒ **教训**：抽取快照的「落点」与「状态」两列**都要独立复核**，落点尤其不能照抄（本次实测落点由 `service/common` 改为 `users-common`）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | `article-rpc/.../application.yml:248` `observability.rpc-timeout: 70s` ＋ `article-api/.../application.yml:233-234` `http-timeout: 70s`；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:76-82`；装配点 `ArticleRpcApplication.java:124-131`（`server.intercept(...)` 真正挂载）；出向 `article-api/.../client/UserRpcClient.java:84-87` | ✅ **一致** —— Go `service/article/rpc/etc/article.yaml:3` `Timeout: 70000` ⇒ 两侧同为 **70s**（**四域里唯一显式配超时值的域**）。Go 侧 `article.go:36` `DisableNativeRpcTimeout` ＋ `:51` `NewUnaryServerInterceptor` ⇒ **超时唯一执行者**；RAG 侧 `rpcInterceptorConfigurer` → `server.intercept(...)` 同构。出向 `min(剩余, 2000ms)` ≡ Go `context.WithTimeout` 的「取父子较早者」语义（`zrpc/internal/clientinterceptors/timeoutinterceptor.go:17-29`） | **L3**（Go 侧 L1 逐行对照 ＋ RAG 侧装配面扫描；未起进程） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `ArticleRpcApplication.java:124-131` → 共享层 `GoStyleRpcProtectionChain.build(...)`（`service/common/.../protection/GoStyleRpcProtectionChain.java:61-78` 定义链序：recover → breaker → shedding → 业务观测） | ✅ **一致** —— Go `zrpc/server.go:144-167`（`setupUnaryInterceptors`：Trace/Recover/Stat/Prometheus/Breaker/Shedding/Timeout 逐条按 `c.Middlewares.*` 装配）⇒ RAG 三层 ＋ 业务观测层**顺序与判据逐条对齐**；Go 侧 `Timeout` 被 `DisableNativeRpcTimeout` 置 0 ⇒ Go 的 `UnaryTimeoutInterceptor` 不生效 ⇒ **两侧都只有一个超时执行者**。<br>🟡 Go 专属的 `Stat`/`Prometheus` 两层 RAG 改用 Micrometer 承接（**表达层**） | **L3**（链序 ＋ 装配面）；装配真挂另有 `ArticleGrpcInterceptorWiringTest`（`@Testcontainers` 真 PG ＋ 真发 gRPC ＋ 断言 `DEADLINE_EXCEEDED` ＋ 断言「取消时仍在跑」）⇒ **测试存在但本轮未跑** | 2026-10-27 |
| **A3** | HTTP 保护链 | `article-api/.../application.yml:159` `rag.http.go-form-limit.mode: raw` ＋ `:160-174` `rag.protection.*`（max-conns 10000 / max-bytes 10485760 / cpu-threshold 900 / breaker enabled）＋ 共享层 4 个 Filter（`GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleMaxBytesFilter` / `GoStyleMaxConnsFilter`）；扫描面 `ArticleApiApplication.java:81-84`（含 `com.rag.common`） | ✅ **一致** —— `mode: raw` **同向**：Go 侧 `service/article/api/` **未注册** `httpx.SetErrorHandler`（实测：全仓仅 `comment/api/commentservice.go:47` ＋ `hot/api/hotcenter.go:53` 注册）⇒ Go handler 走 `httpx.ErrorCtx`（`get_article_handler.go:21-24`）⇒ **400 + text/plain + nosniff + Go 原文**；RAG `raw` 档同形。其余阈值与 go-zero `rest/config.go` 的 tag 默认逐条同值 | **L3**（yaml ＋ 装配面；Filter 实现体未逐行读） | 2026-10-27 |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`（404/405 裸响应）＋ `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude`）＋ 域侧 `article-api/.../advice/GlobalResponseAdvice.java:69-113` / `GlobalExceptionHandler.java` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（`Code/Msg/Data` 三字段**无 omitempty**）⇒ `data:null` 一定出现；Go `rest/router/patrouter.go:73,79-82`（404/405）≡ RAG（404 = `404 page not found\n` ＋ `text/plain; charset=utf-8` ＋ `nosniff`）。<br>🟡 **方法不匹配（405）被刻意分叉成 404 空 body**（article-api 是四域中唯一开 `rest.WithCors()` 的 ⇒ Go 的 `notAllowed` 被 CORS handler 接管 ⇒ `rest/internal/cors/handlers.go:44-48`）⇒ 已登记差异 | **L4**（`tools/errmsg-probe/run-parity.sh` ⇒ **EXIT=0**，Go sha256 == Java sha256 == `cad5cb4f…d1134`，49 行 = CONST 15 ＋ TABLE 15 ＋ FALLBACK 19；真源 sha256 校验通过） | 2026-10-27 |
| **A5** | 端口治理 | `article-api/.../application.yml:43` `server.port: ${ARTICLE_API_PORT:8889}` ＋ `article-rpc/.../application.yml:96` `grpc.server.port: 9001` | ✅ **一致** —— Go `service/article/api/etc/article-api.yaml:3` `Port: 8889` ＋ `service/article/rpc/etc/article.yaml:2` `ListenOn: 0.0.0.0:9001` ⇒ **端口图 1:1 复刻**（含 Go 自身存在的重号：article-rpc 9001 ＝ comment-rpc 9001、article-api 8889 ＝ admin-api 8889 ⇒ 靠容器隔离）。`RANDOM_PORT` **只出现在测试**（`ArticleApiAssemblyWiringTest.java:72` 用 `${local.server.port}` 读回，不硬编码）⇒ 生产面无随机端口 | **L3**（yaml 面扫描；未起进程验绑定） | 2026-10-27 |
| **A6** | errmsg 契约 | `article-common/.../errmsg/ErrorCode.java:95-178`（15 个常量 ＋ 文案）`:229-231`（`of`）`:254-256`（`getMsg`）；消费点 `article-api/.../advice/GlobalResponseAdvice.java:113` ＋ 各 Service | ✅ **一致** —— 15 个码值/文案与 Go `service/article/common/errmsg/errmsg.go:3-19`（const）`:21-37`（codeMsg）`:39-45`（`GetErrMsg`）**逐条相同**；未知码回落（`of` → `ERROR` ⇒ `"FAIL"`）一致；**业务码从不上 gRPC 线**（rpc 发标准 gRPC 码、api 侧映射回业务码）≡ Go `get_author_article_logic.go:44-52` | **L4**（同 A4 的探针 ⇒ **错误码值/文案/未知码回落语义逐字节一致**） | 2026-10-27 |
| **A7** | proto 契约 | `service/article/article-proto/src/main/proto/article.proto:1-134` | ✅ **一致**（差异仅 option 块＝表达层）—— 与 Go `proto/article.proto:1-133` **逐行相同**：`ArticleStatus` 6 个枚举值、6 个 message 的字段名/字段号/类型、`repeated`/`optional`/`map` 标注、`service ArticleService` 的 6 个 rpc 全部一致；唯一差异 = 删 `option go_package`（Go 原 `:4`）＋ 加 `java_package` ＋ `java_multiple_files=true`。<br>🔴 **但无机器回归网**：`tools/difftest/check-proto-static.sh` **只覆盖 `user` / `admin` / `task` 三域** ⇒ **article 的 proto 契约无任何自动守门**（AGENTS.md §3.4 已记「10 个 proto 中 8 个无回归网」）⇒ **域侧可自修**（域内加一条 proto 契约测试） | **L1**（逐行对照）；⚠️ **无回归网 ⇒ 下次改动无人守** | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `article-rpc/.../db/migration/V8__create_article.sql:21-49` ＋ `V9__create_article_sync_outbox.sql:11-40` ＋ `V10__create_article_community_revision.sql:16-57`；jsonb typeHandler 在**域内** `article-rpc/.../mapper/JsonbTypeHandler.java:48-93`（配 `autoResultMap=true` ＋ 自定义 `@Select` 必须挂 `@ResultMap`，见 `ArticleMapper.java:43-48,55-70`） | ✅ **DDL 列级一致**（类型/可空性/DEFAULT/索引/触发器全对齐，含 `secondary_tags`/`ext_info` = JSONB、三个时间列 = TIMESTAMPTZ）<br>🔴 **但 `baseline-version: 0` 缺**（`article-rpc/.../application.yml:53-57`）⇒ 非空 schema 下 **V1 被整条跳过** ⇒ **域侧自修**（见 §3） | **L4**（`python3 tools/difftest/check-ddl-static.py --domain article` ⇒ **EXIT=0**：两侧各 56 列、**逐字相同 56 列**、可空性差异 **0**、DEFAULT 差异 **0**）＋ 配置面 L3 | 2026-10-27 |
| **A9** | trace / otel | 共享层 `service/common/.../http/GoStyleTraceparentFilter.java:188-287`（`@Component` ＋ `@Order(HIGHEST_PRECEDENCE+3)`：Extract→Start→Inject→chain）＋ `service/common/.../observability/GoStyleOtelAutoConfiguration.java:51-61`（`@AutoConfiguration`，经 `META-INF/spring/...AutoConfiguration.imports` 注册 ⇒ **rpc 模块也生效**）＋ `RpcInterceptor.java:197-210,302-308`；域侧 `article-api/.../application.yml:205-214` ＋ `article-rpc/.../application.yml:231-240`（otel exporter ＋ sampler） | ✅ **一致** —— Go `rest/handler/tracehandler.go:47-63`（Extract→Start→**Inject 到响应头**→next）＋ `rest/engine.go:134-137,245-251`（Trace 是 native 链第一个；404 也挂 TraceHandler）≡ RAG；`Traceparent` **canonical 化**（Go `textproto.MIMEHeader.Set` 同样首字母大写）。<br>ℹ️ **属性名是 `sea.*`（共享层：`sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms`），不是 `audit.*`** —— `audit.*` 只存在于 users/admin 域 ⇒ **本轴的「`audit.*`」措辞对 article = N/A**（见 §4 的共享面订正项） | **L3**（Go 侧 L1 ＋ RAG 侧装配面扫描） | 2026-10-27 |

> **A3 预填锚点的复核结论（2026-10-27 核 · 原文保留供追溯）**
> 原文断言：「本域 `go-form-limit.mode` = **raw**；Go 侧：**未注册** ⇒ Go 侧解析失败 = **400 + Go 原文** ⇒ 与 RAG `mode=raw` 同向 ✅」
> ✅ **该锚点成立**（本域 `mode: raw` 实测无误，Go 侧未注册 `httpx.SetErrorHandler` 实测无误）⇒ **A3 一致**。
> ⇒ ⚠️ 但**必须区分**：本域是**显式声明 `raw`**；**`off` 档是 favorite 专属的刻意默认**（`GoStyleFormLimitMode.java:41-49` 的 javadoc 明写「favorite 不在 global 写权范围 ⇒ 共享层对它零影响 ⇒ 默认 `OFF`」）⇒ **其余 9 个域必须显式声明**，漏声明即漏保护（由 `tools/difftest/check-tomcat-limits.py` 静态断言）。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **L-1** | 🔴 **补 `baseline-version: 0`** | `service/article/article-rpc/src/main/resources/application.yml:53-57` | 域侧 yaml ⇒ 域侧自改；写法照抄 `comment-rpc/.../application.yml:90-109` 或 `like-rpc:59-78` 的既有注释块（含「为什么必须钉 0」的论证）。⚠️ **本次实测确认确实缺** ⇒ 另见 §4 的共享面记载订正项 |
| **L-2** | 🔴 **补 SQL deadline 注入的装配**（`article-rpc` **未** `@Import` `DeadlineQueryTimeoutInjector`） | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java`（`@Import` 段）＋ `application.yml` | **实测**：全仓只有 `CommentRpcApplication.java:67-68` 与 `FavoriteRpcApplication.java`（同样 import）**导入了** `DeadlineQueryTimeoutInjector` / `SqlTimeoutStatusTranslator` ⇒ **`article-rpc` 是三张库表域里唯一没导的** ⇒ article 域的 SQL **当前不受 deadline 约束**（Go 侧受 `context` deadline 约束）。修法 = 照抄 comment-rpc/favorite-rpc 的 `@Import` 两行 + 补一条**真 Spring 上下文 + 真 mapper + 真 PG** 的测试（§3.3.3：纯 Mockito / 手工 `new Configuration()` 的单测**不算**） |
| **L-3** | 🔴 **补 proto 契约回归网**（A7 无守门） | `service/article/article-proto` ＋ `article-rpc/src/test/**` | `tools/difftest/check-proto-static.sh` 只覆盖 `user`/`admin`/`task` ⇒ **article 的 proto 无自动守门**（AGENTS.md §3.4：10 个 proto 中 8 个无回归网）。域内加一条「逐字段/字段号/枚举值」契约测试（**域内文件，不改共享脚本**） |
| **L-4** | ✅ A3 `rag.protection.*` 阈值 —— **无需改** | `article-api/.../application.yml:160-174` | 当前值与 go-zero `rest/config.go` 的 tag 默认逐条同值；任何调值都在域内 |
| **L-5** | ✅ `audit.*` 埋点 —— **现在不该补** | — | Go 侧 article **没有** `audit.*` ⇒ 补了就是**新增 Go 没有的行为**（违反 AGENTS.md §3.1「差异只允许出现在表达层」） |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-1** | 🔴 **`DRIFT-ARTICLE-API-006` 的剩余项**：共享 helper 的**文案/口径基线取自 users 域**，未覆盖 article 的 `title`/`content` | ⚠️ **落点已订正** = `service/users/users-common/src/main/java/com/rag/users/common/http/{GoStyleRequired,GoStyleRequiredFields,GoStyleRequest,GoRequiredFieldException}.java`（**实测四类全在 `users-common`**，不在 `service/common` ⇒ 仍是共享层 ⇒ G4 写权） | article 域**已自实现**（`CreateArticleReq.java:57,109-119`）⇒ 缺口不在 article 域 ⇒ 请 G4 复核：**共享 helper 的两条失败文案基线**（`GoStyleRequired.field(...)` 缺值 vs `explicitNull(...)` 显式 null）是否需要**按域可配**，避免 users 域的基线反向约束 article 域 |
| **G4-2** | 🔴 **MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制** —— 全仓**无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链 | `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java` ＋ MyBatis-Plus 自动配置 | **实测缺口**：`CommentSqlDeadlinePrecisionGateTest.java:130-142` 走的是**手工** `new Configuration()` ＋ `configuration.addInterceptor(new DeadlineQueryTimeoutInjector())` ⇒ **绕开 Spring 与 MyBatis-Plus 自动配置**；`FavoriteSqlTimeoutProductionPathTest.java:161` 也是手工 `addInterceptor(PROBE)`（PROBE 是测试自己的探针）⇒ **两条测试都证明不了「`@Import` 的 Bean 被自动收集」**。⇒ 建议：在**共享层**补一条**真 Spring 上下文 + 真 mapper + 真 PG** 的测试，一次性为 10 个 rpc 域钉死该机制（AGENTS.md §3.3.3：注解存在 ≠ 机制生效） |
| **G4-3** | 🟡 `S-6` 的 `SqlTimeoutStatusTranslator` javadoc 归因 | `service/common/.../persistence/SqlTimeoutStatusTranslator.java` | **本域侧复核：article 域不成立**（`ArticleServiceGrpcImpl.java:127` 的 `catch (StatusRuntimeException e)` **在前**、`:147` 的 `catch (RuntimeException e)` **在后** ⇒ `DEADLINE_EXCEEDED` 不会被二次抹掉）⇒ 归因说明仍可订正，但**不要**把它算成 article 域的漂移 |
| **G4-4** | 🟡 **A9 轴定义的措辞缺陷**（共享面 `_cross`） | `docs/DRIFT/_cross/domain-common-lanes.md` 的 A9 行 | 轴定义写「span 属性 **`audit.*`**」，实测：**共享层用 `sea.*`**（`ObservabilityConstants.java:57-116`：`sea.error.kind`/`sea.biz_code`/`sea.biz_msg`/`sea.timeout`/`sea.slow`/`sea.duration_ms`）；**`audit.*` 只存在于 users/admin 域**；**favorite 域用 `biz.*`** ⇒ **轴定义应按域区分**（共享层=`sea.*` · admin=`audit.*` · favorite=`biz.*`）⇒ **共享面文件，G3 不写** ⇒ 已请 G4 登记进 `global/vs-common.md` 的建议项段 |
| **G4-5** | 🟡 **AGENTS.md §6.3 问题 4 的记载不完整** | `AGENTS.md` §6.3（问题 4）＋ §3.5 | 该处只点名「**`favorite-rpc` 例外**（缺 `baseline-version: 0`）」⇒ **实测漏了 `article-rpc`**（同样 `baseline-on-migrate: true` ＋ 无 `baseline-version`，`article-rpc/.../application.yml:53-57`）⇒ 共享面文件 ⇒ **整合者订正** |

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
