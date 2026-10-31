# DRIFT 台账 · `service/article/article-api`

> 本文件**两代内容并存**：§0 是 2026-10-23 早先那次「文件计数实测」，原文逐字保留不删改；
> §1 起是 2026-10-23 本轮 **Go ↔ RAG 语义对照**（首次）的完整台账（spec §31-b 结构）。
> 编号空间统一为 `DRIFT-ARTICLE-API-0xx`；RAG 源码注释里遗留的 `DRIFT-001…007` 是上游实现期的
> 内部编号，**未被本轮验证**，逐条对应关系见 §7.6。
>
> ⚠️ **两套编号会撞号**（2026-10-24 实测确认，本文件内已发生两次误读）：
> `DRIFT-ARTICLE-API-0xx`（本台账的**权威编号**）与上游源码注释里的 `DRIFT-001…007`（**另一套**）
> **同形不同义** —— 例如 `DRIFT-006` 在前者是「8–10 MiB body 文案」，在后者是「type mismatch 三段式文案」。
> ⇒ 引用**必须带全前缀**（`DRIFT-ARTICLE-API-006`）；见 §7.6 的对照表。
> ## 📌 怎么读这份台账（**先看这一段**）
>
> 本文件是**多轮累积**的（2026-10-24 一整天），每个轮次小节各有一张
> 「判定变化（FIX-xx）」表 ⇒ **那些表是历史快照，只反映当轮结论，不要当现状读。**
>
> | 你要找的东西 | **唯一权威面** |
> |---|---|
> | 某条 `DRIFT-ARTICLE-API-0xx` 的**现状** | **§7 的对应条目**（带「当前状态（日期）」横幅 ⇒ 指向关闭它的那一轮） |
> | 某条 `UNRESOLVED-xxx` 的**现状** | **§9 Unresolved**（逐条标了 ✅ 已关闭 + 关闭依据） |
> | 最新的整体判定变化 | **最后一轮**的「判定变化」表（当前是**第十二轮 / FIX-18**） |
> | 某条结论的**完整取证过程** | 对应轮次的小节（见下方**轮次对照表**） |
>
> ⇒ **判断「某条现在是什么状态」，一律回 §7 / §9 与最新一轮，不要回历史轮次表。**
>
> ⚠️ 组件判定（2026-10-24 第十二轮）：**`SEMANTICALLY NOT EQUIVALENT`**（不变）。
> ⚠️ 证据强度上限：**真 Tomcat / 真 PG / 双侧探针**，
> **无双侧活体对拍**（Go 侧仍需 `etcd:2379`）⇒ 所有 ✅ 一律读作
> 「**在该证据强度下一致**」，不是「已闭环」。
>
> ### ⚠️ 「第 N 轮」**跨文件会撞号** —— 引用时必须带「哪个文件」
>
> article 域的轮次编号**不是**本文件私有的；但**两份文件的编号在第 2~5 轮区间是撞的**
> （各自从 1 开始数）⇒ **只写「第 N 轮」会指错**。
>
> | 轮次 | 落在哪个文件 | 主题 |
> |---|---|---|
> | 「2026-10-24 修复轮」（未编号） | **api.md** | 4 条未修漂移 + article-proto 建台账 |
> | 第二轮 | **api.md** | 装配层真容器测试（关 5 条 `UNRESOLVED`） |
> | 第二轮 | **rpc.md** | `DRIFT-ARTICLE-RPC-004` 不修（台账 Go 侧描述有误） |
> | 第三轮 | **api.md** | `DRIFT-007` 码点序 |
> | 第三轮 | **rpc.md** | `U-5` 关闭 + `DRIFT-009` |
> | 第四轮 | **api.md** | `DRIFT-005` query 参数上限 |
> | 第四轮 | **rpc.md** | `DRIFT-005` schema 门禁 |
> | 第五轮 | **api.md** | `UNRESOLVED-007` / `-008` |
> | 第五轮 | **rpc.md** | `U-7` 关闭 + `DRIFT-010` |
> | 第六轮 | **api.md** | `DRIFT-006` LOW 不修 |
> | 第七轮 | **rpc.md** | `U-2` 锁序实测 |
> | 第八轮 | **api.md** | `UNRESOLVED-010` 关闭 + `DRIFT-009` 已修 |
> | 第九轮 | **rpc.md** | `DRIFT-007` Kafka brokers |
> | 第十轮 | **rpc.md** | `U-5'` 关闭 + `DRIFT-004` 展开并关闭 |
> | 第十一轮 | **rpc.md** | `P-U-2` 关闭（wire 字节级） |
> | **第十二轮** | **api.md**（本轮） | `strconv.Quote` 的 Latin-1 补充段 |
>
> ⇒ **第 6 轮起**两份文件的编号**不再撞**（每轮只落在一个文件里，编号顺延），
> 且与 commit 顺序一致（已用 `git log` 逐条核对）。
> ⚠️ 另：`api.md` 有 1 个、`rpc.md` 有 2 个标题叫「**修复轮**」（日期不同）⇒
> 它**不参与**数字编号，引用时**必须带日期**。

---

## 0. 前置核对（2026-10-23 早先实测，原文保留）

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-api 的记录只有一句否定式断言
> （「`article-api`（29 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 00:1x 的实测证伪**。

### 2026-10-23 实测核对（真实执行产出，非推断）

依据 AGENTS.md §6.2 的阅读约定第 1 条 —— 「每行必须带最后核对日期，且该日期必须由一次真实执行产出」：

| 项 | 实测值 | 与 AGENTS.md §0 的冲突 |
|---|---|---|
| `src/main/java` 文件数 | **36** | §0 记「29 个文件」 |
| ≤15 行的空骨架数 | **0** | §0 记「仍全是 4 行空骨架」 |
| 主源码总行数 | **4,275** | — |
| 最新写入时间 | **2026-10-22 23:56** | 晚于 AGENTS.md 最后编辑（23:40） |

已实现的实体（节选）：`GlobalExceptionHandler`(339) / `ArticleApiSupport`(304) / `Article`(258) /
`ArticleController`(231) / `JwtAuthInterceptor`(228) / `ArticleUpdateService`(189) /
`ArticleCreateService`(187) / `CreateArticleReq`(151) / `GoFormValues`(148) /
`ArticleListService`(140) / `ArticleGoStyleCorsFilter`(137)。

### 结论

- **AGENTS.md §0「article 域整体仍未完成」的计数已过期。**
- article-api 的源码已落地，但**从未做过 Go ↔ RAG 对照**，也**没有对应的 DRIFT 台账内容**。
- ⇒ 本文件是**空白起点**，不是「已核对无漂移」。下一个接手 article-api 的 agent 应以此为第一件事。

### 尚未处理

- Go `service/article/article/api` ↔ RAG `article-api` 逐字段对照（**从未做过**）
- 本模块的 `HUMAN_ONLY.MD`（全仓仅 4 个 users 域有）
- 根目录 `HUMAN_ONLY/` 下无 article 相关包指南

### 0.1 2026-10-23 复核：原文的计数已被本轮实测**再次修正**（原文保留，不抹除）

> 依据：任务简报要求「核一下实际 `.java` 文件数与原文记的 36 是否一致」。
> 命令：`find service/article/article-api/src/main/java -name "*.java" | wc -l`（本会话实跑）

| 项 | §0 原文（00:1x 实测） | **本轮复核实测（02:0x）** | 结论 |
|---|---|---|---|
| `src/main/java` `.java` 文件数 | 36 | **38** | ⬆ 已被推翻，见 DRIFT-ARTICLE-API-008 |
| `src/test/java` `.java` 文件数 | 未记 | **2** | 新增信息 |
| 主源码总行数 | 4,275 | **4,666** | ⬆ +391 |
| `src/main/java` 最新写入 | 2026-10-22 23:56 | **2026-10-23**（`advice/`、`config/`、`service/`、`dto/` 共 7 个文件当日改过） | ⬆ |

**不改写 §0 原文**：那是一次真实执行的结果，抹掉它等于抹掉证据。正确做法是追加本节。
**新增 2 个文件的实际来源**（`ArticleApiSupport` 与 `GoStyleStringFields` 的 javadoc 里留有
「第一次实现漏了这条 / 已被实测推翻」的自述）：

| 新增文件 | 首次写入日期 | 说明 |
|---|---|---|
| `dto/GoStyleStringFields.java` | 2026-10-23 | 修 `{"title":123}` 被接受的问题（注释自述「第一次实现漏了这条」） |
| `config/GoStylePathNormalizingFilter.java` | 2026-10-23 | 修 `path.Clean` 归一（注释标 DRIFT-004 已修） |
| `config/GoStyleJsonBodyLimitFilter.java` | 2026-10-23 | 修 8 MiB JSON body 上限（注释标 DRIFT-002 已修） |

⇒ **教训**：§0 那次「实测」在**同一天内**就已经过期。
计数类指标必须带**时间戳 + 该时间之后是否还有写入**，否则会被下一个会话当成现状。

### 0.2 2026-10-24 复核（第十二轮 · 台账审计）：§0.1 的计数**又过期了一次**（原文同样保留，不抹除）

> 依据：第十二轮做「台账是不是最新、最全状态」的审计 ⇒ 逐条核对 §7 的断言。
> 命令（本会话实跑）：
> `find service/article/article-api/src/main/java -name "*.java" | wc -l`
> 与 `find service/article/article-api/src/main/java -name "*.java" -exec cat {} + | wc -l`。

| 项 | §0 原文（00:1x 实测） | §0.1 复核实测（2026-10-23 02:0x） | **本轮复核实测（2026-10-24）** | 结论 |
|---|---|---|---|---|
| `src/main/java` `.java` 文件数 | 36 | 38 | **41** | ⬆ **再次被推翻**（见下方来源） |
| `src/test/java` `.java` 文件数 | 未记 | 2 | **6** | ⬆（本域 11 轮加了 4 个测试类） |
| 主源码总行数 | 4,275 | 4,666 | **5,733** | ⬆ +1,067 |
| `src/main/java` 最新写入 | 2026-10-22 23:56 | 2026-10-23 | **2026-10-24**（`ArticleApiApplication` / `GlobalExceptionHandler` / `GoStylePlainTextErrors` 等） | ⬆ |

**这 3 个新增文件是本域自己的对齐工作带来的**（不是外部改动）⇒ **不是异常，是台账过期**：

| 新增文件 | 加入的 commit | 来源 |
|---|---|---|
| `http/GoStyleGrpcStatusMapper.java` | `2aabe8c` | 修复轮 · 状态码映射对齐 Go |
| `http/GoStyleHttpErrorException.java` | `2aabe8c` | 修复轮 · 错误映射对齐 Go |
| `config/GoStyleGetFormValuesFilter.java` | `3eff5cd` | 第八轮 · `DRIFT-009`（`GoStyleFormParamCountFilter` **改名**而来 ⇒ 净增 0） |

⇒ `git log --diff-filter=AD --name-status do-my-own-gh..HEAD -- service/article/article-api/src/main/java` 逐条核对：
`d8c382b` 加 `GoStyleFormParamCountFilter`（+1），`3eff5cd` 删它、加 `GoStyleGetFormValuesFilter`（净 0）
⇒ 38 + 2（`2aabe8c`）+ 1（`d8c382b`）= **41** ✅ 与实测一致。

⚠️ **又一次踩了 §0.1 自己写下的那个坑**（本仓 Pitfalls `P-6`：「台账里的『实测计数』会在同一天内过期」）：
**计数类指标必须带时间戳 + 该时间之后是否还有写入**，否则会被下一个会话当成现状。
⇒ 本节按 §0.1 的同样规矩处理：**不抹除原文，追加新节**。

⇒ **对 `DRIFT-ARTICLE-API-008` 的影响**：那条「文件计数结论已被再次推翻」**本身依然成立**
（§0 原文的 36 确实错了），但**它记的具体数字（38 / 4,666）也已成为历史** ——
⇒ 该条的状态横幅已在第十二轮补上**核对日期**，并指向本节。

### 0.3 2026-10-29 复核（W-D lane · 台账真实性复核 phase）：§0.2 的计数**第三次过期**（原文同样保留，不抹除）

> 依据：本 phase 的任务就是「台账结论 ↔ 代码真实性逐条对齐」⇒ `DRIFT-ARTICLE-API-008` 本身就是被核对象 ⇒ **必须先把它自己的数字重新量一遍**。
> 命令（**与 §0.2 完全同法**，可复算）：
> `find service/article/article-api/src/main/java -name "*.java" | wc -l`
> 与 `find service/article/article-api/src/main/java -name "*.java" -exec cat {} + | wc -l`。

| 项 | §0 原文（00:1x） | §0.1（2026-10-23） | §0.2（2026-10-24） | **本轮实测（2026-10-29）** | 结论 |
|---|---|---|---|---|---|
| `src/main/java` `.java` 文件数 | 36 | 38 | 41 | **43** | ⬆ **第三次被推翻** |
| `src/test/java` `.java` 文件数 | 未记 | 2 | 6 | **12** | ⬆ |
| 主源码总行数 | 4,275 | 4,666 | 5,733 | **5,447** | ⬇ **−286（行数首次下降）** |
| `src/main/java` 最新写入 | 2026-10-22 23:56 | 2026-10-23 | 2026-10-24 | **2026-10-29 09:10**（`dto/ListArticlesResp.java` · `dto/Article.java` · `config/ArticleApiProperties.java`） | ⬆ |

⚠️ **行数下降但文件数上升 ⇒ 是「拆分 / 搬迁」而不是「代码变少」** ⇒ 与 §0.2 的「本域自己的对齐工作带来的」同因（2026-10-28~29 的 JWT 验签外迁等改造把大文件拆成多个类）⇒ ⇒ **不构成任何行为结论**。
⚠️ **又一次踩了 §0.1 自己写下的那个坑**（本仓 Pitfalls `P-6`：「台账里的『实测计数』会在同一天内过期」）⇒ **本节按 §0.1 / §0.2 的同样规矩处理：不抹除原文，追加新节**。
⇒ **`DRIFT-ARTICLE-API-008` 的状态横幅已按本次实测再补一次核对日期**（状态本身 **✅ 已修** 不变 —— 「台账计数会被推翻」这个结论**恰恰又一次被本轮证实**）。核对日期 2026-10-29。


---

## 1. Component Contract（§4 + §5）

| 项 | 内容 | 证据 |
|---|---|---|
| **Component** | `article-api` —— 文章域的 HTTP 网关 | Go `service/article/api/article.go` |
| **Purpose** | 把 HTTP 请求（认证 / 入参解释 / 错误映射 / 响应成形）翻译成对 `article-rpc` 与 `user-rpc` 的 gRPC 调用 | — |
| **Public Operations** | 7 个端点（见下表） | Go `internal/handler/routes.go:19-73` |
| **Inputs** | path 段 / query string / JSON body / multipart file / `Authorization: Bearer` | — |
| **Outputs** | 6 个端点 = `{"code","msg","data"}` 信封（HTTP 200）；`POST /v1/upload` = **裸** `{"image_url":…}`；失败路径分三种（400 text/plain / 401 空 body / 裸 4xx-5xx） | Go 6 个 handler + `upload_image_handler.go:28` |
| **Dependencies** | `article-rpc`（6 个方法）、`user-rpc`（`GetUser`，仅用于昵称补全）、JWT secret、注册中心（Go=etcd / RAG=Nacos）、PG/MinIO **间接**（经 rpc） | Go `internal/svc/service_context.go:24-26` |
| **State** | **无组件级可变状态**。请求内状态（去重 map、body buffer）全在栈上 | `helper.go:53`（局部 map） |
| **Side Effects** | 仅转发：`incr_view=true` 会让 rpc 侧 `+1` 阅读量 | `get_article_logic.go:38` |
| **Failure Model** | 三层：① 保护层裸状态码（413/503/499）② 框架层（401 空 body / 404 两种 / 裸 500）③ 业务层（HTTP 200 + 非 200 业务码，或 400 text/plain） | 见 §5 |

### 1.1 路由与鉴权契约（L1，逐条对齐）

| # | Method | Path | Go 段 | JWT | Go 证据 | RAG 证据 | Result |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/v1/article` | 第一段 | ✅ | `routes.go:22-25` | `ArticleController.java:122` | EQUIVALENT |
| 2 | PUT | `/v1/article/:id` | 第一段 | ✅ | `routes.go:27-30` | `ArticleController.java:136` | EQUIVALENT |
| 3 | DELETE | `/v1/article/:id` | 第一段 | ✅ | `routes.go:32-35` | `ArticleController.java:151` | EQUIVALENT |
| 4 | GET | `/v1/me/article/:id` | 第一段 | ✅ | `routes.go:37-40` | `ArticleController.java:166` | EQUIVALENT |
| 5 | GET | `/v1/article/:id` | 第二段 | ❌ | `routes.go:50-53` | `ArticleController.java:179` | EQUIVALENT |
| 6 | GET | `/v1/articles` | 第二段 | ❌ | `routes.go:55-58` | `ArticleController.java:193` | EQUIVALENT |
| 7 | POST | `/v1/upload` | 第三段 | ❌ **公开** | `routes.go:66-69` | `ArticleController.java:227` | EQUIVALENT |

> ⚠️ **第 7 条是本组件最反直觉的 L1 契约**：`POST /v1/upload` **不带 JWT**。
> 「上传图片要登录」凭直觉一定会加，但 Go 侧第三段 `AddRoutes` 没有 `rest.WithJwt`。
> RAG 侧靠 `ArticleJwtProtectedRoutes`（4 条，`article-api/.../auth/ArticleJwtProtectedRoutes.java`
> —— **2026-10-27（DRIFT-USERAPI-009②）从 `JwtAuthInterceptor` 搬出来**）精确排除它 ——
> 这张表必须**同时带 method**，否则 `/v1/article/{id}` 会把公开的 GET 一起拦掉。
> ⚠️ **搬家的原因不是「好看」**：验签搬进 Servlet Filter 后，**Filter 与 interceptor 必须共用同一份判据**
> ⇒ 否则要么 public 端点被误伤，要么受保护端点落进兜底分支（验签退回 70s 预算之内 ⇒ ②静默不闭环）。

### 1.2 「briefing 提到的黑名单中间件」在本组件**不存在**（已核实，非遗漏）

`grep -rn "Blacklist|blacklist" service/article/api/` → **0 命中**。
Go 侧 `rest.WithJwt`（`rest/server.go:185`）→ `engine.go:74` `handler.Authorize(secret, ...)`
→ `rest/handler/authhandler.go:45-84`，该链里**没有**黑名单查表。
⇒ Go / RAG 两侧**都没有**黑名单 ⇒ EQUIVALENT（不是漏实现）。

### 1.3 `SecurityRpc`：Go 配了但**零消费者**，RAG 刻意不建 client —— 判定 EQUIVALENT

Go 侧三处命中全部是「定义它」：`api/etc/article-api.yaml:14`、`api/internal/config/config.go:18`、
`api/internal/svc/service_context.go:17,25`；`grep -rn "SecurityRpc|ImageSecurity" api/` 在
`internal/logic/` 与 `internal/handler/` 下**零命中**。
RAG `ArticleApiProperties` 类注释声称「刻意不建这个 client」—— **核对成立**。

但「不建」是否少了一个**启动期**行为？逐点核对：

- Go `service_context.go:25` 会执行 `zrpc.MustNewClient(c.SecurityRpcConf)`。
- `RpcClientConf.NonBlock bool \`json:",default=true"\``（`zrpc/config.go:30`，
  `article-api.yaml` 未配 ⇒ 走 default）⇒ 消费点 `zrpc/internal/client.go:78-80`
  `if !cliOpts.NonBlock { options = append(options, grpc.WithBlock()) }` ⇒ **不加 `WithBlock`**
  ⇒ 非阻塞 dial，`security.rpc` 解析不到也**不 panic**。
- `BuildTarget()` 需要 `Etcd.Key`，yaml 已给 `security.rpc` ⇒ 不返回 error。

⇒ **启动行为与响应行为均不可观测** ⇒ EQUIVALENT。
**但这是「两条错误路径恰好同值」，不是设计对齐** —— 若日后 Go 侧给 `SecurityRpcConf` 显式写
`NonBlock: false`，这条结论立刻失效。已记入 §9 UNRESOLVED-007。

---

## 2. GO-SEMANTIC-MODEL（§7 / §33）

### OP-01 `POST /v1/article` → `CreateArticle`

| 字段 | 内容 |
|---|---|
| Input | JSON body（`Content-Length>0` 且 `Content-Type` 含 `application/json` 才读，否则**丢弃 body**）；path 无；query 无 |
| Precondition | JWT 通过（否则 401 空 body）；`userId` claim 可解析（否则 **200 + 1005**） |
| Trigger | `POST /v1/article` |
| Processing | ①`httpx.Parse`（path→form→header→json 四通道，**任一失败即 400**）②`extractCurrentUserID` ③调 `article-rpc.CreateArticle` ④`status.FromError` 五分支映射 |
| External Calls | `article-rpc.CreateArticle`，**显式 `zrpc.WithCallTimeout(70*time.Second)`** |
| External Call Parameters | `Title`(必填) `Brief`(&恒下发) `MarkdownContent`(必填) `CoverImageUrl`(&恒下发) `ManualTypeTag` `SecondaryTags` `AuthorId` |
| Result | `{"code","msg","data":{"article_id"}}`，HTTP **恒 200** |
| State Change | 无本地状态；rpc 侧建行 |
| Side Effects | 触发 rpc 侧发布流水线（可能异步） |
| Error | 见下方五分支表 |
| Timeout | HTTP 70s（唯一执行者）+ 本调用 70s 独立预算 |
| Retry | **无**（`zrpc.RpcClientConf` 无 `Retries` 字段，`zrpc/config.go:24-35`） |
| Concurrency | 每请求一 goroutine，无共享写状态 |
| Ordering | 无 |
| Observable | 信封；失败时 `data` 可非空（1003/1004 两条） |
| Evidence | `create_article_handler.go:17-33`、`create_article_logic.go:38-92` |

**Go 侧五分支（`create_article_logic.go:55-75`）—— `data` 的有无逐条不同：**

| `status.Code()` | 业务码 | `data` | 备注 |
|---|---|---|---|
| `AlreadyExists` | 1001 | **null** | |
| `FailedPrecondition` | 1004 | **`{"article_id":…}`** | id 从 status **文案**里抠 |
| `DeadlineExceeded` | 1003 | **`{"article_id":…}`** | 同上 |
| `Internal` | 5001 | null | |
| default | 1015 | null | |

`extractArticleIDFromStatusMessage`（`create_article_logic.go:82-92`）：
取**最后一个** `:` 之后的内容并 `TrimSpace`；无 `:` ⇒ `""`；`:` 在末尾 ⇒ `""`。

### OP-02 `PUT /v1/article/:id` → `UpdateArticle`

| 字段 | 内容 |
|---|---|
| Input | path `:id` + 7 个全 `,optional` 的 JSON 字段（**空 body 合法，等于「什么都不改」**） |
| Precondition | JWT + userId |
| Processing | ①`extractCurrentUserID` ②**先读** `GetArticle{IncrView:false, RequesterId:uid}` ③`article==nil` ⇒ 1002 ④`AuthorId != uid` ⇒ 1006 ⑤组装：5 个字符串**非空才下发**，`SecondaryTags` **恒下发**，`Status != 0` 且是合法枚举才下发 ⑥`UpdateArticle` |
| External Calls | `article-rpc.GetArticle` → `article-rpc.UpdateArticle` |
| Result | `{"code":200,"data":{"success":<rpc 的 GetSuccess()，**可能 false**>}}` |
| Error | **读**4 分支：`NotFound→1002` / `PermissionDenied→1006` / `Internal→5001` / default→1015<br>**写**3 分支：`NotFound→1002` / `Internal→5001` / default→1015（**无** `PermissionDenied→1006`） |
| Timeout | HTTP 70s（无 per-call override） |
| Evidence | `update_article_logic.go:36-111` |

> ⚠️ **读 4 分支 vs 写 3 分支的不对称是 Go 现状**，不是笔误。RAG 侧刻意保留
> （`ArticleUpdateService.mapAuthReadError` 与 `updateArticle` 内联三分支分开）。

### OP-03 `DELETE /v1/article/:id` → `DeleteArticle`

同 OP-02 的「先读后写」骨架，但：写分支**也有** `PermissionDenied→1006`（与读**逐字相同**，
`delete_article_logic.go:47-60` 与 `72-85`），且成功时 `success` **恒 true**
（`delete_article_logic.go:87-89`）。请求同时带 `RequesterId`（读）与 `OperatorId`（写）。

### OP-04 `GET /v1/article/:id` → `GetArticle`（公开）

| 字段 | 内容 |
|---|---|
| Input | path `:id` + `form:"incr_view,optional"`（**bool，副作用字段**） |
| Precondition | **无**（公开端点，Go 的 `GetArticleLogic` 根本不调 `extractCurrentUserID`） |
| Processing | `GetArticle{ArticleId, IncrView, **PublicOnly: true**}`；`res==nil \|\| Article==nil \|\| Status != PUBLISHED` ⇒ **1002** |
| Result | `{"code":200,"data":{"article":{…18 字段…}}}` |
| Error | 三分支：`NotFound→1002` / `Internal→5001` / default（含 `PermissionDenied`）→ **1015** |
| Side Effects | `incr_view=true` ⇒ rpc 侧 `+1` 阅读量 |
| Evidence | `get_article_logic.go:35-79` |

> ⚠️ `Status != PUBLISHED ⇒ 1002` **不是冗余防御**：`public_only=true` 只是「请求」，
> 对端旧版本会无视它 ⇒ api 层必须自己再判（`get_article_logic.go:54-56`）。
> 同理 `ListArticles` 里任一行非 PUBLISHED ⇒ **整条请求 5001**（`return` 不是 `continue`，
> `list_articles_logic.go:63-68`）。

### OP-05 `GET /v1/me/article/:id` → `GetAuthorArticle`

`GetArticle{ArticleId, RequesterId:uid}`（**`IncrView` 与 `PublicOnly` 均不设 ⇒ 恒 false**）。
四分支错误映射（多 `PermissionDenied→1006`）；`Article==nil` ⇒ 1002；
`AuthorId != uid` ⇒ **1006**（不因 status 而拦，作者可看自己的草稿）。
⚠️ handler 复用了带 `IncrView` 的 `GetArticleReq`，但 logic **不读它** ⇒
`?incr_view=true` 在本端点是**无效参数**。

### OP-06 `GET /v1/articles` → `ListArticles`

8 个 `form:` 字段，4 个带 `,default=`（`page=1` / `page_size=20` / `sort_by=create_time` / `desc=true`）。
四个筛选字段**全部以非 nil 指针下发**（空串也下发，proto3 `optional` 的 presence 语义）。
`PublicOnly: true`。回显的 `Total/Page/PageSize` 取自 **rpc 响应**而非请求（`list_articles_logic.go:90-95`）。

### OP-07 `POST /v1/upload` → `UploadImage`（公开 · 唯一不走信封的端点）

```
r.FormFile("image")  失败 → httpx.ErrorCtx            → 400 text/plain + nosniff
io.ReadAll(file)      失败 → return nil, err → ErrorCtx → 400 text/plain
article-rpc.UploadFile 失败 → return nil, err → ErrorCtx → ⚠️ 状态码见 DRIFT-ARTICLE-API-001
成功 → httpx.OkJsonCtx(ctx, w, resp)                    → 裸 {"image_url":"…"}，HTTP 200
```
证据：`upload_image_handler.go:14-30`、`upload_image_logic.go:34-52`。
`errmsg.ErrorMinioUpload`(5004) **只进日志，不进响应**。

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§7 / §34）

结构与 §2 完全一致（逐 OP、逐字段），此处只记**与 §2 的差异点**与「实现选择」。

| 维度 | RAG 实现 | 与 Go 的关系 |
|---|---|---|
| 路由 | `@RestController @RequestMapping("/v1")` + 7 个 mapping | EQUIVALENT |
| 鉴权 | `JwtAuthInterceptor`（`HandlerInterceptor`，**只消费**验签结果）＋ `auth/GoStyleJwtAuthFilter`（`@Order(+6)` Servlet Filter，**只验签**）＋ 4 条 `ProtectedRoute`(method+PathPattern，**已搬进 `ArticleJwtProtectedRoutes`**，两侧共用） | EQUIVALENT（DRIFT-004 上游已修 path.Clean 后的匹配一致性；⚠️ **验签已搬出 3s 预算** ⇒ 对齐 Go `engine.go:108` vs `:110-112` 的链序，见 `vs-common.md` §3-L-8） |
| 身份解析 | `ArticleApiSupport.userId(req)`（`req.getAttribute("userId")`） | EQUIVALENT |
| 响应成形 | `GlobalResponseAdvice`（`ResponseBodyAdvice`）统一包信封 | EQUIVALENT（Go 是 6 份手写重复） |
| 错误成形 | `GlobalExceptionHandler`（9 个 `@ExceptionHandler`） | **部分不等价 → §7 DRIFT-001 / 004** |
| gRPC 调用 | `@GrpcClient` + `BlockingStub` + `GoStyleRequestDeadline.applyTo()` | **超时作用域不等价 → §7 DRIFT-002** |
| 昵称补全 | `ArticleAuthorResolver`（独立 Bean，永不抛） | EQUIVALENT |
| 保护层 | `com.rag.common.protection.*` 4 件套 + `ArticleGoStyleCorsFilter` / `GoStyleJsonBodyLimitFilter` / `GoStylePathNormalizingFilter` | EQUIVALENT（MaxBytes 值已显式配 10485760） |

### 3.1 `@ResponseBodyAdvice` 二次包裹专项核对（AGENTS.md §3.3.3）

`HttpEntityMethodProcessor` 会在 `@ExceptionHandler` 返回的 `ResponseEntity` 上**继续查 advice chain**。
`GlobalResponseAdvice.beforeBodyWrite` 的 5 条放行分支逐条核过：

| `@ExceptionHandler` 返回 | 拆包后 `body` | 放行分支 | 结果 |
|---|---|---|---|
| `ResponseEntity<Void>.build()`（401） | `null` | 放行 1（`body == null`，`:77-79`） | 裸 401，无 Content-Type ✅ |
| `ResponseEntity<Response>`（200 信封） | `Response` | 放行 2（`instanceof Response`，`:81-83`） | 单层信封 ✅ |
| `ResponseEntity<byte[]>`（400 / 404） | `byte[]` | 放行 4（`:101-104`） | 原样 text/plain ✅ |
| `ResponseEntity<Void>`（405→404 空 body） | `null` | 放行 1 | 裸 404 空 body ✅ |
| `ResponseEntity<UploadImageResp>`（upload 成功） | `UploadImageResp` | 放行 3（**看 `returnType`**，`:91-93`） | 裸 `{"image_url":…}` ✅ |
| gRPC 未转换（兜底 500） | `null` | 放行 1 | 裸 500 ✅ |

⚠️ 放行 3 **必须看 `returnType` 不能看 `body`**（注释已指出）：`HttpEntityMethodProcessor`
先把 `ResponseEntity` 拆开，`body instanceof ResponseEntity` 恒假。
⇒ **AGENTS.md §3.3.3 那一类静默失效在本组件已被显式处理**（静态核对，未做活体对拍 → §9 UNRESOLVED-001）。

---

## 4. Equivalence Matrix（§24 + §35）

Result 仅取 `EQUIVALENT / IMPLEMENTATION DIFFERENCE / OBSERVABLE DIFFERENCE / SEMANTIC MISMATCH / UNKNOWN`。

| Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|
| 全局 | 路由表（7 端点 method+path） | `routes.go:19-73` | `ArticleController` | EQUIVALENT | 静态源码 |
| 全局 | public 路径集合（含 upload 公开） | 第二/三段无 `WithJwt` | `ArticleJwtProtectedRoutes` 4 条（**Filter 与 interceptor 共用**） | EQUIVALENT | 静态源码 |
| 全局 | 业务码表（15 条） | `errmsg.go:3-37` | `ErrorCode` | EQUIVALENT | 静态源码 |
| 全局 | 未知码回落 | `errmsg.go:39-45` → `FAIL` | `ErrorCode.getMsg` | EQUIVALENT | 静态源码 |
| 全局 | 信封字段名 | `response.Response` | `Response{code,msg,data}` | EQUIVALENT | 静态源码 |
| 全局 | 业务错误的 HTTP 状态码 | 恒 200 | `envelope()` 恒 `OK` | EQUIVALENT | 静态源码 |
| 全局 | 解析失败的形状 | 400 text/plain + nosniff + `\n` | `GoStylePlainTextErrors` | EQUIVALENT | 静态源码 |
| 全局 | 鉴权失败 | 401 **空 body** | `ResponseEntity.status(401).build()` | EQUIVALENT | `authhandler.go:106-122` |
| 全局 | 路径未注册 | 404 `404 page not found\n` | `GoStyleRouterErrorResponses.notFound` | EQUIVALENT | `patrouter.go:73` |
| 全局 | 方法不匹配 | **404 空 body**（`WithCors` 接管） | `handleMethodNotSupported` | EQUIVALENT | `patrouter.go:77-78`+`server.go:150`+`cors/handlers.go:44-48` |
| 全局 | panic / 未转换 | 裸 500 空 body | `handleAny`/`handleUnconvertedRpcStatus` | EQUIVALENT | `recoverhandler.go:14-19` |
| 全局 | MaxBytes | 10485760，裸 413 | `rag.protection.max-bytes` | EQUIVALENT | `maxbyteshandler.go:19-22` |
| 全局 | JSON body 8 MiB 上限 | `LimitReader` 静默截断 ⇒ 400，body = `"unexpected EOF"` | 显式拒绝 400，body = `"request entity too large"` | 🟡 **已确认 LOW，不修**（状态码/Content-Type/nosniff 三项一致） | DRIFT-006 |
| 全局 | CORS（5 头 / OPTIONS 204 / 不写 Allow-Credentials） | `cors/handlers.go:105-123` | `ArticleGoStyleCorsFilter` | EQUIVALENT | 静态源码 |
| 全局 | 路径归一 | `path.Clean` 注册+匹配两侧 | `GoStylePathNormalizingFilter` | EQUIVALENT | `patrouter.go:48,60` |
| 全局 | 超时执行者 | `DisableNativeRestTimeout` → 只有 observability 一个 | `observability.http-timeout: 70s` | EQUIVALENT | `observability/http.go:30-34,88` |
| 全局 | HTTP 超时响应 | 503 + `Request Timeout\n` | `HttpMiddleware.writeTimeoutResponse` | EQUIVALENT | 静态源码（共享层，like/message 已验） |
| 全局 | RPC retry | **无**（`RpcClientConf` 无 `Retries`） | 无 | EQUIVALENT | `zrpc/config.go:24-35` |
| 全局 | RPC client breaker | 默认开（`default=true`） | `GoStyleGrpcClientBreakerInterceptor` | EQUIVALENT | `zrpc/internal/config.go:14` |
| 全局 | 并发语义（无组件级状态） | 每请求一 goroutine | worker 池 + 无共享写 | EQUIVALENT | 静态源码 |
| 全局 | 事务 | 无 | 无 | EQUIVALENT | — |
| 全局 | Cache | 无 | 无 | EQUIVALENT | — |
| OP-01 | `Brief`/`CoverImageUrl` presence | `&req.Brief` 恒下发 | `nullToEmpty` 恒 `set` | EQUIVALENT | 静态源码 |
| OP-01 | 五分支错误码 | `create_article_logic.go:59-74` | `ArticleCreateService:113-125` | EQUIVALENT | 静态源码 |
| OP-01 | **1003/1004 失败带 data** | `:62-69` | `CreateFailure` + `handleCreateFailure` | EQUIVALENT | 静态源码 |
| OP-01 | article_id 从 status 文案提取 | `:82-92` | `:144-153` 逐字复刻 | EQUIVALENT | 静态源码 |
| OP-01 | 无 userId ⇒ **200+1005** | `:39-43` | `ArticleApiSupport.userId` | EQUIVALENT | 静态源码 |
| OP-02 | 先读后写 | `:43-67` | `ArticleUpdateService:87-105` | EQUIVALENT | 静态源码 |
| OP-02 | 读 4 分支 / 写 3 分支不对称 | `:48-60` vs `:95-105` | `mapAuthReadError` vs 内联 | EQUIVALENT | 静态源码 |
| OP-02 | 5 字段非空才下发 | `:74-88` | `setIfNotEmpty` | EQUIVALENT | 静态源码 |
| OP-02 | 非法 status ⇒ 字段不下发 | `:89-92`（`Enum()` 返 nil） | `forNumber` 返 null 则不 set | EQUIVALENT | 静态源码 |
| OP-02 | `SecondaryTags` 恒下发 | `:71` | `addAll` | EQUIVALENT | 静态源码 |
| OP-03 | 成功恒 `success=true` | `:87-89` | `new DeleteArticleResp(true)` | EQUIVALENT | 静态源码 |
| OP-03 | 双 id 下发 | `:42-46,68-71` | `RequesterId`+`OperatorId` | EQUIVALENT | 静态源码 |
| OP-04 | `PublicOnly=true` 闸门 | `:39,54-56` | `:107,123-126` | EQUIVALENT | 静态源码 |
| OP-04 | 非 PUBLISHED ⇒ 1002 | `:54-56` | `isPublished` | EQUIVALENT | 静态源码 |
| OP-04 | `PermissionDenied` ⇒ **1015** | `:49-50`（default） | `:118` | EQUIVALENT | 静态源码 |
| OP-04 | 不做身份判定 | 公开端点不调 `extractCurrentUserID` | `getArticle(id, incrView)` 无 uid 参数 | EQUIVALENT | 静态源码 |
| OP-05 | `IncrView`/`PublicOnly` 恒 false | `:38-40` 不设 | `:63-67` 不设 | EQUIVALENT | 静态源码 |
| OP-05 | ownership 闸门 ⇒ 1006 | `:60-62` | `:89-91` | EQUIVALENT | 静态源码 |
| OP-05 | 不套 status 闸门 | `:54-56` 只判 nil | `:83-85` 只判 `hasArticle` | EQUIVALENT | 静态源码 |
| OP-06 | 4 筛选字段恒下发 | `:37-47` 全 `&` | `:90-93` 全 `set` | EQUIVALENT | 静态源码 |
| OP-06 | 任一行非 PUBLISHED ⇒ **5001 整条失败** | `:63-68` | `:117-119` | EQUIVALENT | 静态源码 |
| OP-06 | `Total/Page/PageSize` 取 rpc 响应 | `:90-95` | `:132-134` | EQUIVALENT | 静态源码 |
| OP-06 | 空结果 ⇒ `articles: null` | `var articles []types.Article` | `:114-126` 保持 null | EQUIVALENT | 静态源码 |
| OP-07 | 成功走**裸**响应 | `upload_image_handler.go:28` | `ResponseEntity` + advice 放行 3 | EQUIVALENT | 静态源码 |
| OP-07 | 下游失败 ⇒ HTTP 状态码 | `errcode.CodeFromGrpcError` | `GoStylePlainTextErrors.badRequest` = 400 | **SEMANTIC MISMATCH** | **DRIFT-001** |
| 入参解释 | 必填判据 = tag 有无 `,optional` | `parseOption`（只认 inherit/string/optional*） | `GoStyleRequiredFields` | EQUIVALENT | `utils.go:372` |
| 入参解释 | 数字/布尔进 String 字段 | `newTypeMismatchError` ⇒ 400 | `GoStyleStringFields` | EQUIVALENT | `unmarshaler.go:643-662` |
| 入参解释 | 字符串进 int 字段 | `type mismatch`（本域无 `,string`） | `ArticleGoStyleCoercion` 补强 | EQUIVALENT | 静态源码 |
| 入参解释 | `omitempty` ≠ 可缺省 | `parseOption` 不认 `omitempty` | 未使用 | EQUIVALENT | `utils.go:372` |
| 入参解释 | 空 query 值被丢弃 | `httpx/util.go:44-45` | `GoFormValues.raw` | EQUIVALENT | 静态源码 |
| 入参解释 | bool 真值表 `1/0/true/false` | `utils.go:106-113` | `GoFormValues.bool:103-111` | EQUIVALENT | 静态源码 |
| 入参解释 | int base-10、位宽 32 | `utils.go:120-121` | `GoFormValues.int32` | EQUIVALENT | 静态源码 |
| 入参解释 | 非 JSON Content-Type ⇒ 丢 body | `withJsonBody` 字面 `Contains` | `GoStyleJsonBodyConverter.goReadsBody` | EQUIVALENT | `requests.go:153-155` |
| 入参解释 | **重复 query key** | 取**第一个**（`unmarshaler.go:841-843`） | Spring 逗号拼接 → 400 | **SEMANTIC MISMATCH** | **DRIFT-003** |
| 入参解释 | query 参数总数上限 2048 | `httpx/util.go:15,48-53` | 🔴 **原判「无上限」已被实测推翻** ⇒ **已补应用层 Go 语义闸门**；✅ **2026-10-25 第十三轮**：容器旋钮对齐后闸门真正可达，body 与 Go **逐字节一致**（原 🟡 形状差异**已关闭**） | ✅ **EQUIVALENT**（判据 + body） | **真 Tomcat 实测**（2026-10-24 / 2026-10-25，`DRIFT-ARTICLE-API-005`） |
| 入参解释 | query 段数上限 10000 | `net/url/url.go:1000-1002,1020-1022` | 🔴 **修前：2048 计数先触发 ⇒ body 文案不对** | ✅ **已对齐**（顺序也按 Go：段数上限**先**于 2048 计数） | **真 Tomcat 实测**（2026-10-24，`DRIFT-ARTICLE-API-009`） |
| 入参解释 | query 里有 `;` | `net/url/url.go:1026-1029` | 🔴 **修前：静默放过 ⇒ 请求被接受（200）** | ✅ **已对齐**（400 + Go 逐字文案） | **真 Tomcat 实测**（2026-10-24，`DRIFT-ARTICLE-API-009`） |
| 入参解释 | query 里有非法 `%` 转义 | `net/url/url.go:1034-1045` | 🔴 **修前：漏到 Spring ⇒ 500 空 body** | ✅ **已对齐**（400 + Go 逐字文案，含 `strconv.Quote` 的 `\xNN`） | **真 Tomcat 实测**（2026-10-24，`DRIFT-ARTICLE-API-009`） |
| 入参解释 | 请求行含**原始非 ASCII** 字节 | Go **接受**（探针实测 `err=<nil>`） | 🔴 **Tomcat 连接器层就 400**（HTML 错误页） | 🟡 **LOW，不修**（应用层不可达；状态码与 Go 不同 ⇒ 仅畸形输入） | **真 Tomcat 实测**（2026-10-24） |
| 序列化 | `ext_info` 空 map ⇒ null | `cloneStringMap:106-108` | `:223-226` | EQUIVALENT | 静态源码 |
| 序列化 | `ext_info` key 排序 | UTF-8 字节序（`encoding/json/encode.go:805-807` `strings.Compare`） | ~~`TreeMap`（UTF-16 码元序）~~ ⇒ **已改为按码点比较** | ✅ EQUIVALENT | **双侧探针实测**（2026-10-24，`DRIFT-ARTICLE-API-007`） |
| 序列化 | `secondary_tags` 空 ⇒ null | nil slice | `cloneStringList` | EQUIVALENT | 静态源码 |
| 序列化 | `author_name` 空 ⇒ **key 消失** | `json:",omitempty"` | `@JsonInclude(NON_EMPTY)` | EQUIVALENT | 静态源码 |
| 序列化 | `status` 输出数值非枚举名 | `int32(res.Article.Status)` | `getStatusValue()` | EQUIVALENT | 静态源码 |
| 昵称补全 | 4 条失败路径全回落 | `helper.go:71-96` | `ArticleAuthorResolver:82-130` | EQUIVALENT | 静态源码 |
| 昵称补全 | 请求内去重 | `helper.go:53-68` | `ArticleApiSupport:159-174` | EQUIVALENT | 静态源码 |
| 昵称补全 | `uid <= 0` 不发 RPC | `helper.go:77-80` | `:90-98` | EQUIVALENT | 静态源码 |
| 昵称补全 | **RPC 调用超时作用域** | `RpcClientConf.Timeout` default **2000ms** | HTTP 全量预算（70s） | **SEMANTIC MISMATCH** | **DRIFT-002** |
| 配置 | `SecurityRpc` 客户端 | 有（但零消费者、NonBlock ⇒ 不 panic） | 无 | EQUIVALENT | `zrpc/config.go:30` |

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 外部系统

| External System | Operation | 输入 | 失败语义 | 是否影响分支 | 是否阻塞 |
|---|---|---|---|---|---|
| `article-rpc` | `CreateArticle/GetArticle/UpdateArticle/DeleteArticle/ListArticles/UploadFile` | 见 §2 各 OP | gRPC status → 业务码 | ✅ | ✅ |
| `user-rpc` | `GetUser(uid)` | `author_id` 解析出的正整数 | **永不冒泡**（回落 `用户 <id>` / `未知作者`） | ❌ | ❌（但占用延迟） |
| 注册中心 | etcd（Go） / Nacos（RAG） | flat key / service name | 非阻塞 dial | ❌ | ❌ |
| JWT | HMAC 验签 + `exp` | `Authorization: Bearer` | 401 空 body | ✅ | ✅ |

⚠️ **区分 `null` 与 `error`**：Go 的 `res == nil`（`list_articles_logic.go:58-60`）⇒ 5001 **不是** 404；
RAG 的 `res == null` 检查已保留（`ArticleListService.java:109-112`）。

### 5.2 错误流全景（Go → HTTP）

| 触发 | Go 落点 | HTTP | body | RAG 落点 | Result |
|---|---|---|---|---|---|
| 保护层 MaxBytes | `maxbyteshandler.go:22` 裸 `WriteHeader` | 413 | 空 | `GoStyleMaxBytesFilter` | EQUIVALENT |
| 保护层 413 **压过** 401 | 链序（MaxBytes 在鉴权链内、鉴权前） | 413 | 空 | Filter 在 DispatcherServlet 外 | EQUIVALENT |
| 保护层 Shedding | `shedder` 裸写 | 503 | — | `GoStyleCpuSheddingFilter` | UNKNOWN（无活体） |
| HTTP 超时 | `observability/http.go:88` `http.Error` | 503 | `Request Timeout\n` | `HttpMiddleware` | EQUIVALENT（静态） |
| 客户端断开 | 同上，`statusClientClosedRequest` | 499 | `Request Timeout\n` | `HttpMiddleware` | EQUIVALENT（静态） |
| JWT 缺失/错/过期 | `authhandler.go:121` | 401 | **空** | `handleAuthentication` | EQUIVALENT |
| `userId` claim 取不到 | `create_article_logic.go:39-43` | **200** | 信封 1005 | `handleBusiness` | EQUIVALENT |
| `httpx.Parse` 失败 | `responses.go:155` `http.Error` | 400 | 裸文案 + `\n` | `GoStylePlainTextErrors` | EQUIVALENT |
| 必填缺席 / 显式 null | `unmarshaler.go:835` / `:856-862` | 400 | `is not set` / `mustn't be nil` | `GoRequiredFieldException` | EQUIVALENT |
| 类型不符 | `unmarshaler.go:1202` | 400 | `type mismatch for field %q, expect…, actual…` | `GoStyleBadRequestException` | EQUIVALENT |
| 上传缺 `image` 字段 | `upload_image_handler.go:17-19` | 400 | `http: no such file` | `handleParseFailure` | EQUIVALENT |
| **上传时下游 gRPC 失败** | `responses.go:150-153` `errcode.CodeFromGrpcError` | **按 gRPC 码映射** | `rpc error: code = …` | `handleGoStyleBadRequest` | **SEMANTIC MISMATCH** |
| 路径未注册 | `patrouter.go:73` → `http.NotFound` | 404 | `404 page not found\n` | `handleNoResourceFound` | EQUIVALENT |
| 方法不匹配 | `patrouter.go:78` → `cors.NotAllowedHandler` | 404 | **空** | `handleMethodNotSupported` | EQUIVALENT |
| panic / 未转换的 gRPC | `recoverhandler.go:14-19` | 500 | **空** | `handleAny` | EQUIVALENT |

---

## 6. 逐维度结论（§12–§21）

### 6.1 State Semantics（§12）
无组件级状态。请求内两处状态（`enrichArticleAuthors` 的去重 map、observability 的 body buffer）
均为局部变量，Go 侧同样是局部（`helper.go:53`、`observability/http.go:147`）。
⇒ **EQUIVALENT**。⚠️ RAG 把 stub 缓存成单例字段（`ArticleRpcClient.articleServiceBlockingStub`），
但 protobuf `AbstractStub` 的 `withDeadlineAfter` 每次返回**新实例**（`GoStyleRequestDeadline:110-120`），
调用点每次都重新取（`ArticleRpcClient:67-69`），没有把 deadline 固化 ⇒ 仍是 EQUIVALENT。

### 6.2 Cache Semantics（§13）
本组件**无缓存**（Go / RAG 均无）。请求内去重 map 是「同一请求内不重复 RPC」，
不是跨请求缓存 —— RAG 侧刻意不把它提级为 Bean（`ArticleApiSupport:152-153` 的注释说明了理由）。
⇒ **EQUIVALENT / N/A**。

### 6.3 Authentication / Authorization（§14）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| Identity source | JWT `userId` claim（`authhandler.go:72-79` 把非标准 claim 塞 ctx） | `request.setAttribute("userId", claims.get("userId"))` | EQUIVALENT |
| Claim default | 取不到 ⇒ logic 层 1005（**不是 401**） | `ArticleApiSupport.userId` → `BusinessException(1005)` | EQUIVALENT |
| claim 取值判定 | `extractCurrentUserID` 的 type switch + `TrimSpace` + `"<nil>"` 排除 | `resolveUserIdClaim:101-114` | EQUIVALENT |
| Signature / exp | golang-jwt HS256，`tok.Valid` | jjwt `parseSignedClaims` | EQUIVALENT |
| Resource ownership | api 层 `AuthorId != uid ⇒ 1006`（OP-02/03/05） | 同 | EQUIVALENT |
| Failure mapping | 401 **空 body** | `ResponseEntity.status(401).build()` | EQUIVALENT |
| 黑名单 | **两侧都没有**（§1.2） | — | EQUIVALENT |
| **HTTP 预算作用域（验签在预算之外）** | 计时器在 Authorize **之后**起算 ⇒ 验签**不计入**预算（`rest/engine.go:108` vs `:110-112`） | `auth/GoStyleJwtAuthFilter.java:101` `@Order(+6)` ⇒ 落在 `+7 HttpMiddlewareFilter`（预算起算）**之外** | 🔴 → ✅ **2026-10-27 已修**（见 `vs-common.md` §3-L-8）<br>⚠️ **本域预算是 70s**（`application.yml:234` `http-timeout: 70s` ≡ Go `etc/article.yaml:4` `Timeout: 70000`）⇒ **全仓唯一不是 3s 的域** |

### 6.4 Transaction Semantics（§15）
api 层无事务。Go 的「先读后写」（OP-02/03）**不是**事务，是两次独立 RPC。
RAG 保持两次调用，无 `@Transactional`（也不需要 —— 无 DataSource）。
⇒ **EQUIVALENT / N/A**。

### 6.5 Concurrency Semantics（§16）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| 请求隔离 | 每请求一 goroutine | worker 池（`SynchronousQueue`，上限 512） | EQUIVALENT |
| 共享写状态 | 无 | 无 | EQUIVALENT |
| 幂等 | 上游 `IncrView` 本身有副作用，两侧一致 | 同 | EQUIVALENT |
| deadline 载体 | ctx 隐式传递 | `ThreadLocal`（worker 线程 set / finally clear） | EQUIVALENT（实现差异） |

### 6.6 Timeout Semantics（§17）⚠️
| 调用点 | Go | RAG | Result |
|---|---|---|---|
| HTTP 总预算 | 70000ms（`article-api.yaml:4`），唯一执行者是 observability（`http.go:30-34`） | `observability.http-timeout: 70s` | EQUIVALENT |
| `article-rpc` 单次 | `ArticleRpcConf.Timeout=70000`（yaml:12） | 剩余 HTTP 预算（≤70s） | EQUIVALENT（近似） |
| `article-rpc` CreateArticle 单次 | **显式** `zrpc.WithCallTimeout(70s)`（`create_article_logic.go:53`） | 无 per-call override | EQUIVALENT（当前部署同值） |
| **`user-rpc` 单次** | yaml **未配** ⇒ `RpcClientConf.Timeout` **default 2000ms** | 剩余 HTTP 预算（≤70s） | **SEMANTIC MISMATCH → DRIFT-002** |
| `rest.WithTimeout(70s)`（routes.go:44） | **no-op** —— `DisableNativeRestTimeout` 已把 `Middlewares.Timeout` 置 false（`engine.go:154-156` 的唯一消费点失效） | 无对应旋钮 | EQUIVALENT |

### 6.7 Retry Semantics（§18）
Go：`zrpc.RpcClientConf`（`zrpc/config.go:24-35`）**没有** `Retries` 字段 ⇒ 零重试。
RAG：`ArticleRpcClient` / `UserRpcClient` 未配置任何 retry ⇒ 零重试。
⇒ **EQUIVALENT**。⚠️ 「加 retry」会把「一次失败 → 1015」变成「重试 N 次后失败」，
可观察的调用次数与延迟都变 ⇒ 属新增行为。

### 6.8 Serialization Semantics（§19）
见 §4 矩阵「序列化」6 行。⚠️ 三处最容易错的地方均已正确处理：
`secondary_tags`/`ext_info` 空 ⇒ **null**（不是 `[]`/`{}`）；`author_name` 空 ⇒ **key 消失**（不是 `""`）；
`ext_info` 用 `TreeMap`（不是 `LinkedHashMap`）。

### 6.9 Configuration Semantics（§20）
| Go 字段 | 生效值 | RAG 落点 | Result |
|---|---|---|---|
| `Host`/`Port` | `0.0.0.0` / `8889` | `server.address` / `server.port` | EQUIVALENT |
| `Timeout` | **70000**（显式，非 tag 默认 3000） | `observability.http-timeout: 70s` | EQUIVALENT |
| `MaxBytes` | **10485760**（显式，覆盖 tag 默认 1048576） | `rag.protection.max-bytes: 10485760` | EQUIVALENT |
| `Middlewares.*` | 全 `default=true`（`rest/config.go:12-22`） | 4 件套 `enabled: true` | EQUIVALENT |
| `CpuThreshold` | 900（千分比 = 90%） | `cpu-shreshold: 900` | EQUIVALENT |
| `Auth.AccessSecret` | `ae0536f9-…-5a19ed505da0`（36 字节） | 同（`${USER_JWT_SECRET:…}`） | EQUIVALENT |
| `Auth.AccessExpire` | 31536000，**本模块不读** | 保留不读 | EQUIVALENT |
| `ArticleRpcConf.Timeout` | 70000 | 无独立项（走 HTTP 预算） | EQUIVALENT（近似） |
| **`UserRpcConf.Timeout`** | **未配 ⇒ 2000ms** | 无独立项 | **SEMANTIC MISMATCH → DRIFT-002** |
| `SecurityRpcConf` | 配了但零消费者 | 无 | EQUIVALENT |
| `Log`/`Telemetry` | file / jaeger:4317 / sampler 1.0 | logback / OTLP / `always_on` | EQUIVALENT（表达层） |

### 6.10 Lifecycle Semantics（§21）
启动：Go = `conf.MustLoad` → `logx.MustSetup` → `DisableNativeRestTimeout` → `MustNewServer`（含 `WithCors`）
→ `NewServiceContext`（3 个 client，NonBlock 非阻塞）→ `RegisterHandlers` → `Start`。
RAG = `SpringApplication.run`（扫 `article.api` + `com.rag.common`）→ `WebMvcConfig` 挂拦截器 →
Filter 链（`@Order` 排序）→ Tomcat ready。
⇒ 顺序差异均属表达层；**唯一需确认的是 Filter 链的实际 `@Order` 全序**，静态可见但无活体 ⇒ §9 UNRESOLVED-003。

---

## 7. Semantic Drifts（§27 + §36）

### 7.1 DRIFT-ARTICLE-API-001 —— 上传路径下游 gRPC 失败的 HTTP 状态码

> **当前状态（✅ **已修**（2026-10-24 修复轮 · F-1；3/3 变异转红））**

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `POST /v1/upload` |
| **Operation** | `UploadImage` |
| **Category** | Error Identity / HTTP 状态码语义 |
| **Go Behavior** | `upload_image_logic.go:45-48` 对 `UploadFile` 的错误 `return nil, err`（裸 err）→ `upload_image_handler.go:25-26` 调 `httpx.ErrorCtx(r.Context(), w, err)`（**不带 fns**）→ `httpx/responses.go:142-158`：<br>`handler == nil`（本模块未注册 `SetErrorHandler`）→ 走 `else if errcode.IsGrpcError(err)` 分支 → `http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))`。<br>`errcode.IsGrpcError` 判 `err` 是否实现 `GRPCStatus()`（`rest/internal/errcode/grpc.go:45-55`）；gRPC-Go 的 `status.Error` **实现**它，且 `articleservice.UploadFile`（`rpc/articleservice/article_service.go:76-79`）**原样返回**该错误、无包装。<br>⇒ HTTP 状态码 = `CodeFromGrpcError`（`grpc.go:12-42`）映射结果。 |
| **Java Behavior** | `ArticleUploadService.java:93-98` 把 `StatusRuntimeException` 包成 `GoStyleBadRequestException` → `GlobalExceptionHandler.java:181-185` → `GoStylePlainTextErrors.badRequest(...)` ⇒ **恒 HTTP 400**。 |
| **Difference** | 同一失败事件，两侧 HTTP 状态码不同，且 RAG 侧**丢弃了 gRPC 码**。典型映射差异：<br>`Unavailable` → Go **503** / RAG **400**<br>`Internal` → Go **500** / RAG **400**<br>`DeadlineExceeded` → Go **504** / RAG **400**<br>`ResourceExhausted` → Go **429** / RAG **400**<br>`NotFound` → Go **404** / RAG **400** |
| **Why It Matters** | `POST /v1/upload` 是**公开端点**（无 JWT）。下游 article-rpc 不可用时（发布/扩容/故障）几乎必然是 `Unavailable`。客户端与网关若按状态码分流（`>=500` 触发重试/告警、429 退避），RAG 侧的 400 会被判成**客户端错误** ⇒ 不重试、不告警、熔断器不计入。 |
| **Observable Impact** | 一个原本应触发重试与告警的下游故障，在 RAG 上表现为静默的 400。 |
| **Likely Cause** | 「article-api 没有 error handler ⇒ 一律 400」这条**推理只走完了 `doHandleError` 的 else 分支**，漏掉了它前面的 `errcode.IsGrpcError` 分支。 |
| **Must Preserve** | `httpx.ErrorCtx` 的两条分支要分开：`非 gRPC 错误 ⇒ 400`；`gRPC 错误 ⇒ errcode.CodeFromGrpcError`。 |
| **Risk** | **HIGH** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：`service/article/api/internal/logic/article/upload_image_logic.go:45-48`、`internal/handler/article/upload_image_handler.go:25-26`；go-zero `v1.10.2` `rest/httpx/responses.go:145-159`、`rest/internal/errcode/grpc.go:12-55`；RAG：`ArticleUploadService.java:93-98`、`GlobalExceptionHandler.java:181-185`、`GoStylePlainTextErrors.java:64-75`。**证据强度：静态源码（两侧 + 框架消费点），无双侧活体对拍。** |

**修复方向（不在本轮范围）**：`GoStyleBadRequestException` 增加一个「按 gRPC 码映射」工厂，
并在 `GlobalExceptionHandler` 里用 `GoStylePlainTextErrors.text(status, msg)` 输出。

---

### 7.2 DRIFT-ARTICLE-API-002 —— `user-rpc`（昵称补全）单次调用超时作用域

> **当前状态（✅ **已修**（2026-10-24 修复轮 · F-2；2/2 变异转红））**

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `ArticleAuthorResolver.resolveAuthorName` |
| **Operation** | `GET /v1/article/:id`、`GET /v1/articles` 内部的 `user-rpc.GetUser` |
| **Category** | Timeout Semantic Equivalence（spec §17） |
| **Go Behavior** | `etc/article-api.yaml:20-24` 的 `UserRpcConf` **没有 `Timeout` 字段** ⇒ 走 tag 默认 `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\`` = **2000ms**。消费点：`zrpc/client.go:70-71` `if c.Timeout > 0 { opts = append(opts, WithTimeout(2000ms)) }` → `zrpc/internal/client.go:83` 装进 unary interceptor 链 ⇒ **每一次 `GetUser` 调用有 2s deadline**。超时后 `resolveAuthorName` 走 `helper.go:83-86` 回落 `用户 <id>`。 |
| **Java Behavior** | `UserRpcClient.userRpc()`（`UserRpcClient.java:48-50`）套 `GoStyleRequestDeadline.applyTo(stub)` ⇒ deadline = **本次 HTTP 请求的剩余预算（上限 70s）**。 |
| **Difference** | 同一降级事件的可观察延迟差 **约 35 倍**：Go 在 ~2s 返回 200 + 回落昵称；RAG 最多挂 70s。若 user-rpc 变慢到 70s，Go 侧早已返回 200，RAG 侧则会被 observability 中间件在 70s 拦下 ⇒ **HTTP 503 `Request Timeout`**。 |
| **Why It Matters** | 「昵称补全失败不得影响文章读取」（`ArticleAuthorResolver` 类注释的显式设计目标）在 **70s 尺度上被打破** —— user-rpc 抖动会让整个文章列表页超时失败。 |
| **Observable Impact** | 正常态无差异（user-rpc 亚秒级返回）。user-rpc 慢/挂 ⇒ Go 200、RAG 慢响应或 503。 |
| **Likely Cause** | RAG 侧把「Go 的 ctx deadline 传播」等价成了「HTTP 预算传播」，但 Go 侧除 ctx 之外还有**一层 per-client 的 2s 硬上限**；逐字段对 yaml 时看不到 tag 默认值。 |
| **Must Preserve** | `user-rpc` 单次调用预算 = 2s（`RpcClientConf.Timeout` tag 默认）。 |
| **Risk** | **MEDIUM-HIGH** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：`service/article/api/etc/article-api.yaml:20-24`（无 Timeout）、go-zero `zrpc/config.go:31`（声明点）、`zrpc/client.go:70-71` + `zrpc/internal/client.go:83`（**消费点**）；RAG：`UserRpcClient.java:48-50`、`GoStyleRequestDeadline.java:110-120`、`application.yml:188-189`。**证据强度：静态源码（已读到消费点）。**<br>⚠️ **2026-10-29 真实性复核（W-D lane · 指针订正）**：本行三处 RAG 侧指针中，**两处行号已过期**（本行记的是**修复前**的证据形态，行号随重构漂移）⇒ `UserRpcClient.java:48-50` ⇒ 实为 **`:82-87`**（`userRpc()` 的 `min(remaining, CLIENT_TIMEOUT_MILLIS)` 真实实现）；`application.yml:188-189` ⇒ 实为 **`:260`**（`http-timeout: 70s`）；`GoStyleRequestDeadline.java:110-120` ⇒ **仍准确**（`applyTo` 方法体）。⇒ **本条「已修」判定不变**（修法证据见 §F-2：`UserRpcClient.java:82-87` 显式算 min · 守门 `UserRpcClientDeadlineTest` **5 例**实测在册）。核对日期 2026-10-29。 |

⚠️ 这正是 AGENTS.md §3.3.1 点名的形态：**「yaml 里没写」≠「没设」**。
`UserRpcConf.Timeout` 就是一条藏在 struct tag 里、且**与 `ArticleRpcConf.Timeout`（显式 70000）不同**的默认值。

---

### 7.3 DRIFT-ARTICLE-API-003 —— 重复 query key 的解释

> **当前状态（✅ **已修**（2026-10-24 修复轮 · F-3；2/2 变异转红））**

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `GET /v1/article/:id`、`GET /v1/articles` |
| **Category** | Input Interpretation（入参解释） |
| **Go Behavior** | `httpx.GetFormValues`（`util.go:35-62`）把同名 key 的多个值收成 `[]string` 存入 params；`formUnmarshaler` 带 `mapping.WithFromArray()`（`requests.go:27-31`）⇒ 消费点 `unmarshaler.go:835-846`：<br>`if u.opts.fromArray { if 目标字段不是 slice { … mapValue = val.Index(0).Interface() } }`<br>⇒ **取第一个值**，其余丢弃。`?page=1&page=2` ⇒ `page=1` ⇒ HTTP 200。 |
| **Java Behavior** | `ArticleController` 的 8 个 `@RequestParam String`（`ArticleController.java:194-202`）由 Spring `RequestParamMethodArgumentResolver` 处理多值 ⇒ 逗号拼接成 `"1,2"` ⇒ `GoFormValues.int32("1,2", …)` ⇒ `Integer.parseInt` 抛 ⇒ `GoStyleBadRequestException` ⇒ **HTTP 400**。 |
| **Difference** | `?page=1&page=2`：Go **200**（page=1），RAG **400**。<br>`?incr_view=1&incr_view=0`：Go `true`，RAG 400。 |
| **Why It Matters** | 重复 query key 在实践中不罕见（客户端拼接 bug、网关重写、部分 HTTP 客户端的数组序列化）。一次原本成功的读请求变成 400。`incr_view` 那条还有副作用语义（重复 key 下 Go 恒取第一个）。 |
| **Likely Cause** | 「把 query 收成 String 再自己按 Go 规则解析」漏掉了「多值 → 取第一个」这一步 —— `GoFormValues.raw` 只处理了「单值 / 空值」。 |
| **Must Preserve** | 同名 key 出现多次 ⇒ 取**第一个**（`unmarshaler.go:841-843`）。 |
| **Risk** | **MEDIUM** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：go-zero `rest/httpx/util.go:35-62`、`rest/httpx/requests.go:27-31`、`core/mapping/unmarshaler.go:835-846`（**消费点**）；RAG：`ArticleController.java:194-202`、`form/GoFormValues.java:83-88,121-137`。**证据强度：静态源码。** |

---

### 7.4 DRIFT-ARTICLE-API-004 —— 一条**锁死了错误答案**的绿测试

> **当前状态（✅ **已修**（断言已按本条「处置」拆开：`codes.Internal` ⇒ 500 / `codes.Unavailable` ⇒ 503 / 非 gRPC ⇒ 400；原「恒 400」那条把 **RAG 行为**写成 **Go 事实**的用例已被取代））**

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` 测试 |
| **Category** | 测试证据（AGENTS.md §3.3 硬约束） |
| **现状** | `ArticleApiMvcPipelineTest.java:353-372`：`@DisplayName("upload 的 RPC 失败 ⇒ **400 text/plain + nosniff**，不是 1015 信封（Go 无 error handler）")`，注入 `Status.INTERNAL.withDescription("minio down")`，断言 `status().isBadRequest()`。本轮实测该测试**通过**（`Tests run: 33, Failures: 0`）。 |
| **问题** | 该 `@DisplayName` 的**前提**（Go 无 error handler ⇒ 400）只覆盖了 `doHandleError` 的 `else` 分支。按 Go 实际控制流，`codes.Internal` 会被 `errcode.CodeFromGrpcError`（`grpc.go:31-32`）映射成 **HTTP 500**。⇒ 这条绿测试断言的是 **RAG 的行为**，却被写成了 **Go 的事实**。 |
| **危害** | ① 它会在修 DRIFT-ARTICLE-API-001 时**先变红**，且测试名会让人误判「修复 = 漂移」；② 它让「400」这个数字看起来已被真源验证过 —— 正是 AGENTS.md §3.3.2 点名的「以 `Go: …` 措辞出现的断言是高危项」。 |
| **处置** | 修 DRIFT-001 时**先改这条断言**（拆成两条：`codes.Internal` ⇒ 500、`codes.Unavailable` ⇒ 503、非 gRPC ⇒ 400），再改实现。 |
| **Risk** | **HIGH**（作为测试风险） |
| **Classification** | **OBSERVABLE DIFFERENCE**（测试与真源不一致） |
| **Evidence** | `ArticleApiMvcPipelineTest.java:353-372`（静态源码）；本轮 `mvn -pl service/article/article-api test` ⇒ `Tests run: 50, Failures: 0, Errors: 0`。**证据强度：纯 MockMvc + Mockito（无真实 gRPC、无真实 Tomcat）。** |

---

### 7.5 其余条目（低危 / 上游已自登记）

#### DRIFT-ARTICLE-API-005 —— query 参数总数上限（OBSERVABLE DIFFERENCE，LOW）

> **当前状态（✅ **已修**（2026-10-24 第四轮：真 Tomcat 实测**推翻了原定性**，并补上应用层 Go 语义闸门））**
- **Go**：`httpx/util.go:15` `maxFormParamCount = 2048`；消费点 `util.go:48-53` —— 累计超过 2048 个非空值即 `return nil, fmt.Errorf("too many form values, …")` ⇒ **400**。
- **RAG（首轮静态判定，已被实测推翻）**：`ArticleController` 的 8 个 `@RequestParam` 无总数上限，Tomcat 只在 `maxHttpHeaderSize` 等处限流。
- **影响**：需要 >2048 个 query 值的请求 Go 拒、RAG 收。本组件的 query 字段只有 8 个，**实际不可达** ⇒ LOW。
- **证据**：静态源码。
- **🔴 当前状态（2026-10-24）**：本条**原定性被真容器实测推翻** —— 「RAG 无上限」**不成立**：
  Tomcat 的 `server.max-http-request-header-size`（Boot 4 默认 8 KB）**更早**触发 ⇒ 状态码两侧都是 400，
  且 RAG 阈值**更严**（≈2040 个值 < Go 的 2049）⇒ **不存在 DoS 暴露**。
  已**补应用层 Go 语义闸门**（`GoStyleFormParamCountFilter`，**2026-10-24 第八轮已改名** `GoStyleGetFormValuesFilter`），真容器实测 body 与 Go **逐字节一致**（2/2 变异验证）。
  🟡 **残留**：默认部署下拒绝发生在连接器层 ⇒ body 是 Tomcat 的 HTML 页（Go 是 text/plain）⇒ **LOW，决定不修**。
  详见本文件「第四轮」节。

> 🟥 **2026-10-25 第十三轮（Tomcat 三旋钮）：上面的「🟡 残留」已关闭 ⇒ 本条可结案**
>
> - **容器旋钮对齐**（`docs/DRIFT/hot/api.md` 第十三轮，跨 10 个 api 模块落地）：
>   `server.max-http-request-header-size: **1028KB**` ＋ `server.tomcat.max-parameter-count: 2048`
>   ⇒ 2049 个最短非空 query 值（≈8196 字节）**不再**在连接器层被拒
>   ⇒ 请求**能到达** `GoStyleGetFormValuesFilter` ⇒ **body 变成 Go 的 text/plain 形态**
>   （`too many form values, error: <r.Form.Encode()>`）。
> - **实测（2026-10-25 真 Tomcat）**：2049 个非空值 ⇒ **400 + `text/plain` + Go 的 body 形态**
>   （**不再**是 Tomcat 的 HTML 错误页）；**恰好 2048** 个 ⇒ **200**（与 Go 一致，
>   改前是 400 —— 那 400 是 Tomcat 的 8 KB 上限造成的，**不是** Go 的语义）。
> - ⇒ **状态码与 body 都与 Go 一致** ⇒ 本条原先唯一的残留（body 形状）**关闭**。
> - ⚠️ **两条 characterization 断言因此转红**（它们断言的是「漂移存在」）：
>   `ArticleApiAssemblyWiringTest#realTomcatRejectsTooManyQueryParamsWith400LikeGo`
>   （原断言 body 含 `Bad Request`）与 `#realTomcatDefaultHeaderLimitFiresBeforeTheFormParamGate`
>   （原断言 2048 个值 ⇒ 400）⇒ 已按 §3.3「**先改/删错误断言，再改实现**」改写为断言 **Go 的形态**。
> - ⚠️ **残余（不在本组件内）**：Tomcat 数「**总参数个数**」、Go 数「**非空值个数**」⇒
>   「2049~10000 段且非空值 ≤2048」这个窗口仍有差异 ⇒ 登记为 `DRIFT-HOTAPI-030`（**跨域待拍板**）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:207,273`（`MAX_FORM_PARAM_COUNT = 2048` ＋ `nonEmpty > MAX_FORM_PARAM_COUNT` ⇒ 按 Go 口径数**非空值**）· Go `go-zero@v1.10.2/rest/httpx/util.go:15,48-53`（`maxFormParamCount = 2048` ＋ 消费点）· 核对日期 2026-10-30。
> ⚠️ **本条两处叙述已过期（原句保留不改写）**：① 本条点名的域内类 `GoStyleFormParamCountFilter` / `GoStyleGetFormValuesFilter` **已不存在** —— commit `1adc627d`（2026-10-25 · global 第十四轮 X-6）把它**删除并迁到共享层**（现落点 = 上面的 `GoStyleFormValues`）⇒ ⇒ 「已补应用层 Go 语义闸门」的**结论仍成立**，**只是落点换了**；
> ② 「容器旋钮对齐 ⇒ `server.tomcat.max-parameter-count: 2048`」说的是 **yaml 层** ⇒ **运行期值已是 10001**：`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170`（`MAX_PARAMETER_COUNT = 10001`；该类 `@Order(LOWEST_PRECEDENCE)` ⇒ **无条件覆盖** yaml 的 2048）⇒ ⇒ 判定权交还应用层（= Go `net/url` 段数上限 10000 ＋ 1）⇒ ⇒ **本条「状态码与 body 与 Go 一致」的结论不变**。

#### DRIFT-ARTICLE-API-006 —— 8–10 MiB JSON body 的错误文案（OBSERVABLE DIFFERENCE，LOW）

> **当前状态（🟡 **LOW，确认不修**（2026-10-24 第六轮；characterization test + 变异验证））**
- **Go**：`requests.go:21` `maxBodyLen = 8 << 20`（**框架常量，不在任何 tag/yaml 里**）→ `io.LimitReader` **静默截断** → 解析器报 `unexpected EOF` → 400。
- **RAG**：`GoStyleJsonBodyLimitFilter.java:93-97` 显式拒绝，body 为 `request entity too large\n`。
- **可观察契约相同**（400 + text/plain + nosniff + 业务不执行），差异仅在 body 文案。
- **证据**：静态源码。上游已在该 Filter 的 javadoc 自登记（标 DRIFT-002）。
- **✅ 当前状态（2026-10-24）**：**已确认 LOW OBSERVABLE DIFFERENCE，决定不修**。
  Go 侧文案由 Go 1.25.11 探针**实测**得到（`json.NewDecoder(io.LimitReader(r.Body, 8<<20)).Decode`
  解析半个文档 ⇒ `err.Error() == "unexpected EOF"` ⇒ `http.Error` 的 body = `"unexpected EOF\n"`），
  RAG 侧 = `"request entity too large\n"`。
  ⇒ 状态码 / Content-Type / nosniff **三项一致** ⇒ 仅文案不同。
  ⇒ 已用 **characterization test**（`ArticleApiMvcPipelineTest#oversizedJsonBodyIsRejectedAtEightMiB`）
  锁住实测现状（含文案断言 + 变异验证 MUT-RED）⇒ 将来谁改动这条路径，测试会立刻变红。
  详见本文件「第六轮」节。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **🟡 LOW，确认不修** ⇒ **现行态 = 🟡 LOW，确认不修**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:94,96`（`:94` = `getContentLengthLong() > MAX_JSON_BODY_BYTES` 判定；`:96` = `writeGoStyleBadRequest(response, "request entity too large")` ⇒ body = `"request entity too large\n"`）＋ 守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiMvcPipelineTest.java:965`（`oversizedJsonBodyIsRejectedAtEightMiB`）· Go `go-zero@v1.10.2/rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20` ⇒ `io.LimitReader` 静默截断）· 核对日期 2026-10-30。
> ⚠️ **两处小订正**：① 正文写的 RAG 侧行号 `:93-97` → 现行 **`:95-97`**（`MAX_JSON_BODY_BYTES` 常量在 `:86`，判定与文案在 `:95-97`）；② 状态码 / `Content-Type` / `nosniff` 三项一致、**仅 body 文案不同**这一结论**两侧当场复核成立** ⇒ **一条差异 ≠ 一个缺陷** ⇒ 维持「不修」。

#### DRIFT-ARTICLE-API-007 —— `ext_info` key 排序在非 BMP 字符上不同（OBSERVABLE DIFFERENCE，极低）✅ **已修**

> **当前状态（✅ **已修**（2026-10-24 第三轮 · FIX-09；双侧探针 + 变异验证））**
- **Go**：`encoding/json/encode.go:793-807`，`slices.SortFunc` 按 `ks`（Go string `<` = **UTF-8 字节序**）。
- **RAG（修复前）**：`ArticleApiSupport.java:226` `new java.util.TreeMap<>(source)` ⇒ `String.compareTo` = **UTF-16 码元序**。
- **影响**：仅当 `ext_info` 的 key 含增补平面字符（代理对）时两者序不同；ASCII / BMP 范围内逐字节一致。
- **证据**：`go1.25.11 src/encoding/json/encode.go:805`。静态源码。
- **✅ 当前状态（2026-10-24）**：**已修**。改用**码点序**比较器（`CODE_POINT_ORDER`），
  码点序 = Go 的 UTF-8 字节序 ⇒ 双侧探针实测一致（Go 1.25.11 + JDK 21 同跑），
  并配回归网 + 变异验证 MUT-RED。详见本文件「第三轮」节。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleApiSupport.java:227-228`（`new java.util.TreeMap<>(CODE_POINT_ORDER)`）＋ `:261`（`CODE_POINT_ORDER` 比较器本体，逐 `codePointAt` 比较 ⇒ **码点序**）· Go `go1.25.11/src/encoding/json/encode.go:793-807`（`// Extract and sort the keys.` ＋ `slices.SortFunc` 按 `strings.Compare` ⇒ **UTF-8 字节序**）· 核对日期 2026-10-30。
> ⚠️ **补当前态指针**：正文的 `ArticleApiSupport.java:226` 描述的是**修复前**的 `new java.util.TreeMap<>(source)`（历史值，原句保留）；**现行实现**在 `:227-228` ＋ `:261` ⇒ ⇒ 「码点序 = Go 的 UTF-8 字节序 ⇒ 双侧一致」的结论**当场复核成立**。

#### DRIFT-ARTICLE-API-008 —— 本仓台账的文件计数结论已被再次推翻（台账卫生）

> **当前状态（✅ **已修**（台账卫生：§0.1 已按实测更正为 **38** 个主源文件 / **4,666** 行；
> **核对日期 2026-10-23**；⚠️ **该计数已于 2026-10-24 再过期一次** ⇒ 现为 **41** 个 / **5,733** 行，
> 逐文件来源见 **§0.2**））**
> ⚠️ **2026-10-29 真实性复核（W-D lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**：本条「台账的文件计数结论会被推翻」这一结论**恰恰又被本轮实测证实一次**）。依据：**§0.3**（本轮按 §0.2 同法实测：`src/main/java` **43** 个 `.java` / **5,447** 行 · `src/test/java` **12** 个 · 最新写入 **2026-10-29 09:10** ⇒ **§0.2 的 41 / 5,733 已第三次过期**）· 核对日期 2026-10-29。
见 **§0.1**。`src/main/java` 实测 **38**（原文 36），主源码 **4,666** 行（原文 4,275），
且 7 个主源文件在 **2026-10-23 当日**仍有写入。
- **Risk**（作为台账风险）：MEDIUM —— 任何按「36 个文件 / 4,275 行」做的完备性推断都已失效。
- **Classification**：台账卫生问题（非实现漂移）。
- **证据**：本会话 `find`/`wc` 实跑。

#### DRIFT-ARTICLE-API-009 —— Go `net/url` 的三条 query 解析行为，RAG 曾**静默放过** ✅ **已修（2026-10-24）**

> **当前状态（✅ **已修**（2026-10-24 第八轮 · FIX-14；真 Tomcat 逐字一致 + 3/3 变异转红））**
- **Go**（Go 1.25 **标准库** `net/url/url.go:1019-1054` `parseQuery`）：
  | # | 行为 | 出处 | Go 侧的文案（**探针实测**） |
  |---|---|---|---|
  | ① | 段数（`&` 分隔）> 10000 ⇒ **400** | `url.go:1000-1002,1020-1022` | `number of URL query parameters exceeded limit` |
  | ② | 段内含 `;` ⇒ **400** | `url.go:1026-1029` | `invalid semicolon separator in query` |
  | ③ | key/value 的 `%` 转义非法 ⇒ **400** | `url.go:1034-1045` | `invalid URL escape "%zz"` |
  ⇒ 这些 error 由 `net/http.Request.ParseForm` 返回，而 go-zero 的
  `httpx.GetFormValues`（`rest/httpx/util.go:23-25`）**原样往上抛**
  ⇒ `httpx.Parse` 失败 ⇒ `httpx.ErrorCtx` ⇒ `http.Error(w, err.Error(), 400)`。
- **RAG（修复前）**：`GoStyleFormParamCountFilter`（现 `GoStyleGetFormValuesFilter`）
  按 Go 的**分段规则**处理这三种输入，但**只跳过该段、不上报错误**
  ⇒ ②**静默放过**（请求被接受）、③漏到 Spring 的参数解析 ⇒ **500 空 body**、
  ①**顺序反了**（2048 计数先触发 ⇒ body 文案不对）。
- **影响**：②③ 只需客户端发畸形 query（**不需要任何特殊权限**）⇒ ②尤其糟：
  Go 拒的请求 RAG **照单全收**。
- **证据**：Go 侧探针（真包 `httpx.Parse`）+ 真 Tomcat 实测（修前实测见「第八轮」节）。
- **✅ 当前状态（2026-10-24）**：**已修**。filter 按 Go 的**顺序**复刻：
  段数上限 → 分段解析（错误**要上报**）→ go-zero 的 2048 计数。
  ⇒ 真容器实测 **body 与 Go 逐字一致**（含 `strconv.Quote` 的转义文案）。
  ⚠️ **残留（LOW，不修）**：请求行含**原始非 ASCII 字节** ⇒ Tomcat 在**连接器层**就 400
  ⇒ 应用层不可达 ⇒ 与 `DRIFT-005` 的残留同类。详见本文件末尾「第八轮」节。

  ⚠️ **第十二轮已把这条从「实测如此」升级为「结构性必然」**：Tomcat 11.0.24 的
  HTTP/1.1 与 HTTP/2 **两条路径**都拒 query 里任何 `>= 0x80` 的字节 ⇒ 换配置也绕不过。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:263`（`number of URL query parameters exceeded limit`）＋ `:274`（`too many form values, error: ` ＋ Go 的 `r.Form.Encode()` 形态）· Go `go1.25.11/src/net/url/url.go:1001,1019-1022,1026-1029,1034-1045`（`defaultMaxParams = 10000` ＋ 三条解析错误）· 核对日期 2026-10-30。
> ⚠️ **落点已迁（原句保留）**：本条点名的域内类 `GoStyleFormParamCountFilter`（现 `GoStyleGetFormValuesFilter`）**已不存在** —— commit `1adc627d`（2026-10-25 · global 第十四轮 X-6）删除并迁到共享层 ⇒ ⇒ **现落点 = `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java`**（文案与 2048 计数都在这里）⇒ ⇒ 「filter 按 Go 的**顺序**复刻：段数上限 → 分段解析（错误**要上报**）→ go-zero 的 2048 计数」这条**结论本身复核成立**（共享层 `:263` 先判段数、`:274` 再判 2048）⇒ ⇒ **不改判**。

#### DRIFT-ARTICLE-API-010 —— Go `strconv.IsPrint` 的 **Latin-1 补充段**（`0xA1..0xFF`）RAG 曾**判成不可打印** ✅ **已修（2026-10-24）**

> **当前状态（✅ **已修**（2026-10-24 第十二轮 · FIX-18；Go 侧真标准库探针 + 直接单元测试 + 4/4 变异转红））**
- **Go**（Go 1.25.11 **标准库** `strconv/quote.go:522-563` `IsPrint`）：
  | 码点段 | Go 的判据 | 出处 |
  |---|---|---|
  | `0x20..0x7E` | 可打印 | `quote.go:525-528` |
  | `0xA1..0xFF` | 可打印，**唯一例外 `0xAD`（软连字符）** | `quote.go:529-532` |
  | `> 0xFF` | unicode 的 L/M/N/P/S 五类 **加** ASCII 空格（不含其它 Unicode 空格） | `quote.go:536-562` |
  ⇒ 消费点 `quote.go:79-81`（`appendEscapedRune`）：`IsPrint(r) == true` ⇒ `utf8.AppendRune`
  **原样输出**；否则落到 `\u00XX` / `\xNN` 分支。
- **RAG（修复前）**：`GoStyleGetFormValuesFilter.isGoPrintable` 写作 `return r >= 0x20 && r < 0x7F;`
  ⇒ 把**整个 `0x7F..0xFF`** 判成不可打印 ⇒ 与 Go 的 `0xA1..0xFF` 段**不一致**
  （Go 判可打印、RAG 判不可打印 ⇒ 输出 `\u00XX` 而非原样字节）。
- **⚠️ 影响面（这条最容易被误判成「不重要」）**：**端到端不可达** ——
  `goQuote` 的唯一输入是 **query 串**里 `%` 开头的切片，而
  **Tomcat 在连接器层就拒掉了 query 里任何 `>= 0x80` 的字节**
  （HTTP/1.1 与 HTTP/2 **两条路径同判据** ⇒ 逐行证明见「第十二轮」节）
  ⇒ query 串恒为纯 ASCII ⇒ 该分支是**结构性死代码**。
  **但源码里的偏离是真的，且 javadoc 曾写着「Go 的 `strconv.IsPrint`」** ——
  按 AGENTS.md §3.2 / §3.3.2，**「以 Go 措辞断言等价」却没实现** = 无出处的 idiomatization
  ⇒ 必须消除，不能拿「端到端测不到」当借口。
- **证据**：Go 侧探针（真标准库 `strconv.Quote`，逐字节十六进制）+ RAG 侧**直接单元测试**
  （端到端测不到 ⇒ 必须直测）+ **4/4 变异转红**。详见本文件末尾「第十二轮」节。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:691-700`（`isGoPrintable`：`0x20..0x7E` ⇒ 可打印；`0xA1..0xFF` ⇒ 可打印且**唯一例外 `0xAD`**；其余 ⇒ 不可打印）· Go `go1.25.11/src/strconv/quote.go:522-563`（`IsPrint` 本体）＋ `:525-528`（`0x20..0x7E`）＋ `:529-532`（`0xA1..0xFF` ＋ `r != 0xAD`）· 核对日期 2026-10-30。
> ⚠️ **落点已迁（原句保留）**：本条点名的 `GoStyleGetFormValuesFilter.isGoPrintable` **已不存在** —— commit `1adc627d`（2026-10-25 · global 第十四轮 X-6）删除域内 filter 并迁共享层 ⇒ ⇒ **现行落点 = `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:691`**（其类注释自记 `Go strconv/quote.go:524-533` 的 Latin-1 快路径）⇒ ⇒ **判据与 Go 逐条同形 ⇒ 结论复核成立，不改判**。

---

#### DRIFT-ARTICLE-API-011 —— `application.yml` 少 `auth:` 一层 ⇒ **`article-api` 永远起不来**（启动阻断）✅ **已修（2026-10-29 · G3）**

> **当前状态（✅ **已修**（2026-10-29 · G3 阶段 1 · A-1 双侧活体实测时暴露））**
- **现象（🔴 启动阻断，不是「配置漂移」）**：`article-api` **根本起不来** —— 无论环境变量 `USER_JWT_SECRET` 设没设，容器都抛
  「access-secret 不能为空」并退出。
- **根因**：commit **`45e0af4`**（2026-10-29 02:09:34 · P0 #8「去明文默认验签密钥」）在改 `access-secret` 那一行时
  **把 `auth:` 整行删掉了** ⇒ yaml 变成 `article-api.access-secret` ⇒
  而 `ArticleApiProperties`（`service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleApiProperties.java:72-76`）
  的字段是**嵌套的 `Auth auth`**（`@NestedConfigurationProperty`）⇒
  **绑定路径必须是 `article-api.auth.access-secret`** ⇒ 绑定**永远落空** ⇒ `auth.accessSecret` 恒为 `null`
  ⇒ `verifyAccessSecret()`（同文件 `:107-115`）**无条件抛** ⇒ **组件起不来**。
- **🔴 为什么「跑绿」没抓到**：当时的 `ArticleApiPropertiesSecretGuardTest` 用的是
  `withPropertyValues("article-api.auth.access-secret=…")` ⇒ **它从不读那份 yaml**
  ⇒ 3 条用例全绿，而组件在真实部署里**一次都没起来过**。
  ⚠️ 这正是 AGENTS.md §3.3.3「注解/配置存在 ≠ 机制生效」的同型：**测的是「我写的那份配置」，不是「产物里那份」**。
- **修法**：① yaml 补回 `auth:` 这一层（并留注释说明**为什么不能少**）；
  ② 新增回归网 `ArticleApiPropertiesSecretGuardTest#realYamlBindsSecretIntoNestedAuth`
  —— **读真 `application.yml`**（`YamlPropertySourceLoader` ＋ 真 key 路径 `article-api.auth.access-secret`），
  而不是 `withPropertyValues`。
- **证据**：**变异验证** —— 删掉 yaml 的 `auth:` 一行 ⇒ **只有新增这条用例转红**，原有 3 条仍绿
  ⇒ **证明原有 3 条真的看不见 yaml 的键路径**；还原后转绿。
  ⇒ **证据强度：L4**（真文件 ＋ 变异精确转红 ＋ 真起容器验证）。
- **✅ 同族自查（2026-10-29 · G3 已逐个核过，结论：只有 article-api 命中）**：
  commit `45e0af4` 同时改过 `comment-api` / `favorite-api` 的同类行，但**这两个域不成立**——
  它们的 yaml 是 `comment-api.user-auth.access-secret` / `favorite-api.user-auth.access-secret`
  （**一层 `user-auth:`**），而各自的 Properties 类是 `private UserAuth userAuth` **直接挂在 prefix 下**
  （`CommentApiProperties.java:48` / `FavoriteApiProperties.java:50`）⇒ **键路径与结构一致，绑定正常**。
  ⇒ 🔴 **只有 `article-api` 的结构是嵌套的 `Auth auth`**（`ArticleApiProperties.java:72-76`）⇒ **只有它要求多一层 `auth:`**。
  ⇒ **别去「修」没坏的两个域**。

> ⚠️ **2026-10-30 真实性复核（W-D lane · 本条为 HIGH 档 · 逐条严格核）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/resources/application.yml:126-127`（`auth:` ＋ `access-secret: ${USER_JWT_SECRET}` **已补回**）· RAG `service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleApiProperties.java:77-78`（`@NestedConfigurationProperty` ＋ `private Auth auth = new Auth()` ⇒ **嵌套**结构 ⇒ 绑定路径必须是 `article-api.auth.access-secret`）＋ `:133-136`（`@PostConstruct void verifyAccessSecret()` ⇒ `requireSecret(secret, "article-api.auth.access-secret", "USER_JWT_SECRET")`）· 守门 `service/article/article-api/src/test/java/com/rag/article/article/api/config/ArticleApiPropertiesSecretGuardTest.java:134`（`realYamlBindsSecretIntoNestedAuth` ⇒ **读真 yaml**）· 核对日期 2026-10-30。
> ⚠️ **两处指针已过期（原句保留，订正在此）**：本条写的 `ArticleApiProperties.java:72-76` ⇒ 现行 **`:77-78`**；写的 `:107-115` ⇒ 现行 **`:133-136`**。⇒ **根因**（yaml 少 `auth:` 一层 ⇒ 绑定落空 ⇒ 组件起不来）**已不复存在**，但**偏移原因**是 commit `01d37829` / `9e0e8c2f`（2026-10-29 · P0 #8 补正）在 `verifyAccessSecret()` 之前补了一大段「未解析占位符 / 空串」判据的 javadoc ⇒ ⇒ **文件变长 ⇒ 行号后移**（**结论不变**）。
> ⚠️ **同族自查本轮复核（结论不变）**：`comment-api` 仍是**一层** `user-auth:`（yaml `:76`）＋ `CommentApiProperties.java:51` `private UserAuth userAuth`；`favorite-api` 同构（yaml `:160` ＋ `FavoriteApiProperties.java:53`）⇒ ⇒ **两个域的键路径与结构一致、绑定正常** ⇒ ⇒ 「只有 `article-api` 命中、别去修没坏的两个域」**仍然成立**（⚠️ 本条正文写的 `:48` / `:50` 现行分别是 **`:51`** / **`:53`**）。

---

#### DRIFT-ARTICLE-API-012 —— JSON **字段顺序**漂移：Go 按 struct 声明序，Jackson 按字母序 ✅ **已修（2026-10-29 · G3）**

> **当前状态（✅ **已修**（2026-10-29 · G3 阶段 1 · A-1 双侧活体实测时暴露））**
- **现象**：两侧响应的**字段与值完全相同**，但**字节不同** ⇒ 任何按字节比对的对拍都会报红。
- **根因（框架机制）**：
  - Go `encoding/json` 序列化 **struct** 时按**字段声明顺序**输出；
  - Jackson 序列化 **POJO** 时默认按**字母序**（`MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`，默认开）。
  ⇒ ⇒ **同样的数据 ⇒ 不同的字节序列**。
- **Go 侧出处**：
  - `service/article/api/internal/types/types.go:6-24` —— `Article` 的**声明序**：
    `id` → `title` → `brief` → `content` → `cover_image_url` → `manual_type_tag` → `secondary_tags` →
    `author_id` → `author_name` → `create_time` → `update_time` → `status` → `view_count` →
    `like_count` → `comment_count` → `share_count` → `ext_info`；
  - `service/article/api/internal/types/types.go:67-72` —— `ListArticlesResp` 的**声明序**：
    `articles` → `total` → `page` → `page_size`。
- **RAG 侧修法**：给 `service/article/article-api/src/main/java/com/rag/article/article/api/dto/Article.java`
  与 `.../dto/ListArticlesResp.java` 加 `@JsonPropertyOrder({...})`，顺序**逐条**对齐上面两处 Go 声明序。
- **分类**：**OBSERVABLE DIFFERENCE**（可观测差异）⇒ **修到逐字节相同**，**不登记为有意偏离**
  （同 `hot` 域 `DRIFT-HOTAPI-030` 的处理口径）。
- **证据**：新增 `service/article/article-api/src/test/java/com/rag/article/article/api/dto/ArticleApiJsonFieldOrderTest.java`（3 例）；
  **变异验证** —— 去掉 `@JsonPropertyOrder` ⇒ **只有 `Article` 那条转红** ⇒ 证明断言真有牙齿；还原后转绿。
  ⇒ **证据强度：L4**（真 DTO ＋ 变异精确转红）。
- **⚠️ 边界（不得外推）**：本条只钉了 `Article` 与 `ListArticlesResp` **两个 DTO**；
  同目录其它 DTO（`GetArticleResp` 等）**尚未逐个核对字段序** ⇒ **登记为域内未闭合项**。

---

### 7.6 上游实现期的内部编号（`DRIFT-001…007`）对照 —— 本轮**未独立验证**

RAG 源码注释里出现的这些编号**不在本轮验证范围**，仅登记其存在与本轮的间接观察：

> 🔴 **本表是第二套编号空间，与 §7 的 `DRIFT-ARTICLE-API-0xx` 同形不同义**（2026-10-24 实测确认）：
>
> | 编号 | 本表（上游源码注释内部编号） | §7 的权威编号（`DRIFT-ARTICLE-API-0xx`） |
> |---|---|---|
> | `DRIFT-005` | `setHeader` vs `setContentType` 的 `charset=utf-8` 空格 | **005** = query 参数总数上限 2048 |
> | `DRIFT-006` | type mismatch 三段式文案 | **006** = 8–10 MiB JSON body 错误文案 |
> | `DRIFT-007` | `ext_info` 用 TreeMap 而非 LinkedHashMap | **007** = `ext_info` key 排序在非 BMP 字符上不同 |
>
> ⇒ 本表内的交叉引用（如「见 DRIFT-006」）指向的是 **§7 的权威编号**，**不是**本表同名的条目。
> ⇒ 引用本表条目时**必须写全前缀** `DRIFT-ARTICLE-API-0xx` 或注明「上游内部编号」。

| 上游编号 | 主题 | 落点 | 本轮观察 |
|---|---|---|---|
| `DRIFT-001` | 方法不匹配 ⇒ 404 而非 405 | `GlobalExceptionHandler.java:262-318` | ✅ 本轮静态核对**成立**（`patrouter.go:77-78` + `server.go:150` + `cors/handlers.go:44-48`） |
| `DRIFT-002` | JSON body > 8 MiB ⇒ 400 | `GoStyleJsonBodyLimitFilter.java` | ✅ 成立（见 `DRIFT-ARTICLE-API-006`；文案差异保留） |
| `DRIFT-003` | 父类 `MultipartException` 未列 ⇒ 裸 500 | `GlobalExceptionHandler.java:219-225` | 未做活体验证 |
| `DRIFT-004` | `path.Clean` 归一 | `GoStylePathNormalizingFilter.java` | ✅ 静态成立（`patrouter.go:48,60`） |
| `DRIFT-005` | `setHeader` vs `setContentType` 的 `charset=utf-8` 空格 | `GoStyleJsonBodyLimitFilter.java:104-108` | **无真实 Tomcat 对拍 ⇒ UNKNOWN**（§9 UNRESOLVED-001） |
| `DRIFT-006` | type mismatch 三段式文案 | `GoStyleStringFields.java:69-77` | ✅ 静态成立（`unmarshaler.go:1202-1204` 的 `%q` 格式） |
| `DRIFT-007` | `ext_info` 用 TreeMap 而非 LinkedHashMap | `ArticleApiSupport.java:211-227` | ✅ 静态成立（`encode.go:805`），但见 `DRIFT-ARTICLE-API-007` 的排序边界 |

🔴 **已更正（2026-10-24，第五轮 FIX-12）** —— 上游注释里 `GlobalExceptionHandler.java:118` 引用的
**`DRIFT-024`** 原文写的是「在本仓 `docs/DRIFT/` 下**查无对应台账条目** ⇒ 该编号来自另一份未入库的历史清单」
⇒ **该结论是错的**：`DRIFT-024` 在 **users 域** `docs/DRIFT/users/_shared.md:103`
（主题 =「text/plain 400 的响应头丢失」）⇒ **域内编号跨域撞号**，不是"未入库"。
本域**不受影响**（共享层 `HttpMiddleware` 已修为实时 propagate + 本域真 Tomcat 用例已断言 400 响应头）。
⚠️ 跨域引用**必须带域前缀**才查得到 —— 详见本文件末尾「第五轮」节。

---

## 8. Test Cases（§29 + §30）

### 8.1 现有测试盘点（本轮实跑）

| 测试类 | 条数 | 结果 | 载体 |
|---|---|---|---|
| `ArticleApiMvcPipelineTest` | 33 | ✅ 全绿 | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockitoBean` |
| `ArticleApiRouteAuthBoundaryTest` | 17 | ✅ 全绿 | 同上 |
| **合计** | **50** | **50 passed / 0 failed** | **全部 MockMvc + Mockito** |

命令：`mvn -pl service/article/article-api -am -DskipTests install` ⇒ `BUILD_EXIT=0`；
`mvn -pl service/article/article-api test -Dtest='ArticleApi*Test'` ⇒ `Tests run: 50, Failures: 0, Errors: 0`。

⚠️ 按 AGENTS.md：**「50 条绿测试」不构成本台账任何一条 EQUIVALENT 的证据**。
理由：① 纯 Mockito，不执行 gRPC；② MockMvc 不走真实 Tomcat；
③ 至少 **DRIFT-004** 就是被绿测试锁死的错误断言。

### 8.2 本轮建立的 CASE（逐条对照表）

| CASE | 输入 | Go（真源） | RAG | 判定 |
|---|---|---|---|---|
| CASE-01 正常创建 | 合法 token + 合法 JSON | 200 + `{code:200,data:{article_id}}` | 同（绿测 `ArticleApiMvcPipelineTest`） | EQUIVALENT（MockMvc） |
| CASE-02 空 body 创建 | `Content-Length: 0` | body 丢弃 ⇒ `title is not set` ⇒ **400** | `required=false` + normalize ⇒ 同 400 | EQUIVALENT |
| CASE-03 必填显式 null | `{"title":null,…}` | `mustn't be nil` ⇒ 400 | 同 | EQUIVALENT |
| CASE-04 非 JSON CT | `Content-Type: text/plain` + JSON body | body **丢弃** ⇒ 必填失败 ⇒ 400 | `GoStyleJsonBodyConverter` 返 null ⇒ 同 | EQUIVALENT |
| CASE-05 重复存在 | rpc `AlreadyExists` | 200 + 1001 + `data:null` | 同 | EQUIVALENT |
| CASE-06 发布流水线未完 | rpc `DeadlineExceeded` + msg 带 `xxx:id` | 200 + **1003 + data 带 id** | 同 | EQUIVALENT |
| CASE-07 无 userId claim | 签名有效但无 `userId` | **200 + 1005**（不是 401） | 同 | EQUIVALENT |
| CASE-08 无 Authorization | 受保护端点 | **401 空 body** | 同 | EQUIVALENT |
| CASE-09 越权改文章 | 非作者 | 1006 | 同 | EQUIVALENT |
| CASE-10 公开读草稿 | rpc 回 `DRAFT` | **1002**（不泄露正文） | 同 | EQUIVALENT |
| CASE-11 列表混入草稿 | rpc 回任一行非 PUBLISHED | **整条 5001** | 同 | EQUIVALENT |
| CASE-12 空列表 | rpc 回 0 行 | `articles: null` | 同 | EQUIVALENT |
| CASE-13 `?incr_view=1` | — | true | true | EQUIVALENT |
| CASE-14 `?incr_view=` | 空值 | false，不报错 | 同 | EQUIVALENT |
| CASE-15 `?page=010` | 八进制字面量 | **10**（base-10） | 10 | EQUIVALENT |
| CASE-16 **重复 query key** | `?page=1&page=2` | **200，page=1** | **400** | ⛔ **DRIFT-003** |
| CASE-17 **上传时 rpc `Unavailable`** | 下游不可用 | **503 + `rpc error: code = Unavailable…`** | **400** | ⛔ **DRIFT-001** |
| CASE-18 **上传时 rpc `Internal`** | 下游 500 | **500** | **400** | ⛔ **DRIFT-001 + DRIFT-004（绿测锁死）** |
| CASE-19 **user-rpc 慢 3s** | 昵称补全 | **~2s 返回 200**（回落昵称） | **~3s 才返回 200**；>70s 则 **503** | ⛔ **DRIFT-002** |
| CASE-20 8 MiB ~ 10 MiB JSON | 合法 JSON | 400（截断后解析失败） | 400（显式拒绝），文案不同 | 🟡 **LOW，已确认不修**（`DRIFT-ARTICLE-API-006`） |
| CASE-21 路径未注册 | `/v1/nope` | 404 `404 page not found\n` | 同 | EQUIVALENT |
| CASE-22 方法不匹配 | `POST /v1/article/1` | **404 空 body** | 同 | EQUIVALENT |
| CASE-23 OPTIONS 预检 | `OPTIONS /v1/任意` | 204 空 + CORS 头 | 同 | EQUIVALENT |
| CASE-24 超 10 MiB | — | **裸 413** | 同 | EQUIVALENT |
| CASE-25 尾斜杠 | `/v1/articles/` | 200（`path.Clean`） | 200 | EQUIVALENT |
| CASE-26 并发 | 同一 author 出现在 N 行 | 1 次 `GetUser` | 1 次 | EQUIVALENT |
| CASE-27 事务回滚 | — | 无事务 | 无事务 | N/A |
| CASE-28 缓存命中/未命中 | — | 无缓存 | 无缓存 | N/A |

### 8.3 建议补的探针（**2026-10-24 第二轮当时未做** ⇒ **2026-10-29 G3 逐条重核**）

> 🟥 **订正（2026-10-29 · G3 域内收敛自查 C）**：本小节原是**「本轮未做，列为待办」**的四项清单，
> 但**四项里有三项早已被后续轮次消解，只有状态列没回填** ⇒ ⇒ **读者会以为它们还开着**。
> ⇒ 依据 AGENTS.md §3.1「台账是下一个 agent 的对照基准」＋ 本文件自己的元教训
> 「**台账的「现在时」是最危险的一种错**」⇒ **逐条补时间坐标**。
> ⚠️ **本小节现为「活状态」面；各轮「判定变化」表是历史记录**。

| # | 原探针设想 | **当前状态（2026-10-29 核对）** |
|---|---|---|
| 1 | **CASE-17/18 双侧活体对拍**：Go 侧用 `httptest` + 真实 `UploadImageHandler` 注入一个 `status.Error(codes.Unavailable, …)` 的 fake client，断言 HTTP 503；RAG 侧同理 | 🟡 **部分闭合（2026-10-29 · G3）** —— 原文写「**这是修 `DRIFT-001` 的前置**」⇒ ⚠️ **该前提已失效**（`DRIFT-ARTICLE-API-001` **已修**：2026-10-24 修复轮 **F-1**）⇒ ⇒ 它**不是从「前置」降级为「补强」**，而是**双侧活体证据缺口**（⇒ 归属 **G-3 双侧活体**，**不阻塞阶段 1**）<br>🔴 **2026-10-29 G3 已建探针并跑通**：`tools/api-full-parity-probe/article/`（**G3 真私有目录**）⇒ **Go 真源栈与 RAG 栈同时起服务** ⇒ 同一请求 ⇒ **逐字节**比对（Go 真源二进制 + **配置副本**〔真源仓全程 `git status` 干净〕· 两侧**各自独立** Postgres · 真源 `article.sql` + `002_community_revision.sql`）<br>**已取到 3 条 L4 双侧活体证据**（`GET /v1/articles` 200 · `GET /v1/article/9007199254740993` 5001 · `GET /v1/article/does-not-exist` 1002 ⇒ **两侧逐字节一致**）<br>⇒ **同时暴露并修掉 2 个新缺陷**：`DRIFT-ARTICLE-API-011`（yaml 少 `auth:` ⇒ **组件起不来**）· `DRIFT-ARTICLE-API-012`（JSON 字段序漂移）<br>⚠️ **不是全闭合**：① `judge` §九 G-3 原文要求「article 域双侧活体 **0 条**」⇒ 现 3 条 ⇒ **部分**；② **成功路径只覆盖公开列表端点** —— 宿主上 `user.rpc` **两侧都不可达** ⇒ 单条详情端点两侧**同样降级成 5001**（该用例钉的是「**降级路径两侧一致**」，不是「取到文章」）⇒ ⇒ **成功路径的完整字段语义仍未双侧验证** |
| 2 | **真实 Tomcat 探针**：起 `RANDOM_PORT` 容器，断言 400 响应的 `Content-Type: text/plain; charset=utf-8`（**带空格**）与 `X-Content-Type-Options: nosniff` ⇒ 用于关闭 §9 `UNRESOLVED-001` | ✅ **已消解（2026-10-24，第二轮）** —— `UNRESOLVED-001` **已关闭**（§9 标题行已带 ✅）⇒ **本项的前提本身已过期**；且**本域现已真起 `RANDOM_PORT` 容器**（`ArticleApiAssemblyWiringTest` / `ArticleApiFormParamLimitTest` 两个 `@SpringBootTest(webEnvironment = RANDOM_PORT)`）⇒ ⇒ **不需要再补** |
| 3 | **user-rpc 慢响应探针**：Go 侧把 `UserRpcConf.Timeout` 显式写成 0/2000 并注入慢 client；RAG 侧注入慢 stub 实测端到端延迟 ⇒ 用于关闭 `DRIFT-002` | ✅ **已消解（2026-10-24 修复轮）** —— `DRIFT-ARTICLE-API-002` **已修**（**F-2**：`UserRpcClientDeadlineTest` × 5 ＋ **变异 2/2 转红**）⇒ ⇒ **守门已在**（`UserRpcClientDeadlineTest`），**不需要再补** |
| 4 | **重复 query key 探针**：`?page=1&page=2` 双侧各跑一次 ⇒ 用于关闭 `DRIFT-003` | ✅ **已消解（2026-10-24 修复轮）** —— `DRIFT-ARTICLE-API-003` **已修**（**F-3**：`ArticleApiMvcPipelineTest` × 3 ＋ **变异 2/2 转红**）⇒ ⇒ **守门已在**，**不需要再补** |

⇒ **小结**：本小节**只剩第 1 项**，且已从「🔴 完全未做」推进到「🟡 **部分闭合**」
（**2026-10-29 · G3**：3 条 L4 双侧活体证据已取到；**成功路径的完整字段语义**待 `user.rpc` 可用后补）
⇒ 第 2~4 项**均已消解**（原「待办」措辞已过期 ⇒ 本轮订正）。

---

## 9. Unresolved（§37 · 证据不足，不得猜）

### UNRESOLVED-001 —— 400/401/404 响应头在**真实 Tomcat** 下的形状 ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：RAG 侧 400 text/plain 的 `Content-Type` 是否逐字节等于 Go 的 `text/plain; charset=utf-8`（**分号后有空格**）？`nosniff` 是否真的到客户端？
- **Go Evidence**：`net/http.Error` 设 `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff`。
- **Java Evidence**：`GoStylePlainTextErrors.java:69-75` 用 `ResponseEntity.header(...)` 设同值；`GoStyleJsonBodyLimitFilter.java:110-115` 刻意用 `setHeader` 而非 `setContentType`（注释称 `setContentType` 会被 Tomcat 改写成无空格形式）。
- **Why Unknown**：本组件 50 条测试**全部是 MockMvc**，不经 Tomcat 的 header 规范化。
  同仓 `message-api` / `like-api` 已实测踩过同类坑（`HttpMiddleware` 的 E-1「Content-Type 完全丢失」），
  说明**静态正确 ≠ 客户端收到**。上游 `DRIFT-005` 正是这条。
- **Impact**：低（仅文案字节），但它曾是 E-1 事故的形态。
- **Required Confirmation**：起真实 Tomcat 容器打一次 400 请求，抓原始响应头。
- **✅ 关闭依据（2026-10-24，真 Tomcat）**：见本文件「第二轮」节第 4 条（`UNRESOLVED-001` / `-005`）。
  ⇒ 真容器实测：`Content-Type` / `nosniff` **都到位**（响应头没丢）；
  ⇒ 同时**首次确认一条 LOW 漂移**：Go 写 `text/plain; charset=utf-8`（**带空格**），实测 `text/plain;charset=utf-8`（**无空格**，Tomcat 规范化）⇒ **决定不修**。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/http/GoStylePlainTextErrors.java:78-79`（`.header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN_UTF8)` ＋ `.header("X-Content-Type-Options", "nosniff")`；常量在 `:53` = `"text/plain; charset=utf-8"`）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:111-115`（刻意用 `setHeader` 而非 `setContentType` ⇒ 规避 Tomcat 规范化）· 真容器守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:334-335`（`realTomcatBadRequestHeadersAreGoShaped` ⇒ 断言 `text/plain;charset=utf-8` ＋ `nosniff`）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Evidence 写的 `GoStylePlainTextErrors.java:69-75` ⇒ 现行 **`:75-80`**（写头的两行在 **`:78-79`**）；`GoStyleJsonBodyLimitFilter.java:110-115` ⇒ 现行 **`:111-115`** ⇒ ⇒ **「已关闭」的结论本身复核成立**（真 Tomcat 守门仍在位、断言的仍是 Go 的形状）。

### UNRESOLVED-002 —— **依赖 `article-rpc` 的响应语义（跨组件，需与 `docs/DRIFT/article/rpc.md` 合并判读）** ✅ **已关闭（2026-10-30 · W-D lane 跨组件复核）**
- **Question**：article-api 的三处安全闸门是否真的有效？
  ① `GetArticle` 是否尊重 `public_only=true`；② `ListArticles` 是否尊重 `public_only=true`；
  ③ `ListArticles` / `GetArticle` 返回的 `status` 是否真的等于 `PUBLISHED(枚举值)`；
  ④ `CreateArticle` 在 `FailedPrecondition` / `DeadlineExceeded` 时，**status 文案**是否真的按
  `create_article_logic.go:87-91` 期望的「冒号分隔、冒号后是 article_id」格式拼（这是约定式协议，无契约保证）。
- **Go Evidence**：`article-rpc` 侧 `internal/logic/*`、`internal/model/article_public.go`（**本轮未读**）。
- **Java Evidence**：`ArticleGetService.java:121-126`、`ArticleListService.java:117-119`、
  `ArticleCreateService.java:144-153`（api 侧的三道兜底与解析器）。
- **Why Unknown**：`article-rpc` 正由**另一个并行会话**审查；本轮**没有**对其做任何验证，
  按纪律**不能替对端背书**。api 侧的兜底是「对端可能失约」的补偿，不是对端正确的证明。
- **Impact**：若对端忽略 `public_only`，api 侧闸门能挡住草稿泄露（这是设计意图）；
  但若对端连 `status` 都不填，api 侧会把**所有文章判成非 PUBLISHED ⇒ 恒 1002 / 恒 5001**。
- **Required Confirmation**：与 `docs/DRIFT/article/rpc.md` 合并判读；建议提升为**跨组件条目**
  （`public_only` 契约由两个组件共同承担，见 §7 各条）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **未决（跨组件待判读 · 原文「本轮未读 article-rpc ⇒ 不能替对端背书」）** ⇒ **现行态 = ✅ 已关闭**（**改判**）。
> **关闭理由**：跨组件前提**已可判读** —— `article-rpc` 侧在 2026-10-29（W-D lane）已完成逐条回源核，本轮**又独立复核了一遍**，四个子问题**两侧都有 `文件:行号`** ⇒ ⇒ 「证据不足」这个**未决前提本身已消失** ⇒ ⇒ 本条**不是**「判不了」，是「当初的依赖方已核完」。
>
> | 子问题 | Go 真源 | RAG | 判定 |
> |---|---|---|---|
> | ① `GetArticle` 尊重 `public_only=true` | `service/article/rpc/internal/logic/get_article_logic.go:41-43`（`if in.PublicOnly { return l.getPublicArticle(in) }`） | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java:230-236` | ✅ **两侧都有公开分支**（早于 span 起始，与 Go 同序） |
> | ② `ListArticles` 尊重 `public_only=true` | `service/article/rpc/internal/logic/list_articles_logic.go:46-48` | `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java:740-744` | ✅ **两侧都有公开分支** |
> | ③ 返回的 `status` 真的等于 `PUBLISHED` | api 侧 `service/article/api/internal/logic/article/get_article_logic.go:54` ＋ `list_articles_logic.go:64`（`!= articlepb.ArticleStatus_PUBLISHED` 即拒） | api 侧 `service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleGetService.java:121-126` ＋ `ArticleListService.java:117-119`（`ArticleApiSupport.isPublished`） | ✅ **两侧同口径**；枚举值也同：Go `proto/article.proto:10` `PUBLISHED = 2` ≡ RAG `service/article/article-proto/src/main/proto/article.proto:11` `PUBLISHED = 2` |
> | ④ `CreateArticle` 的 status 文案（冒号分隔、冒号后是 article_id） | api 侧 `service/article/api/internal/logic/article/create_article_logic.go:58`（`extractArticleIDFromStatusMessage(st.Message())`）＋ `:82-91`（`LastIndex(":")` ⇒ `TrimSpace(message[idx+1:])`） | api 侧 `service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleCreateService.java:144-153`（`lastIndexOf(':')` ⇒ `substring(idx+1).trim()`） | ✅ **逐字同构**（约定式协议，两侧同一形态） |
>
> ⚠️ **一处**产品代码**注释已过期**（**本轮只记，台账不改代码**）：`ArticleGetService.java:120` 写「老版本 rpc 忽略 `public_only` ⇒ 这里必须自己再判一次」⇒ 但 rpc 侧**现在有** `public_only` 分支（`ArticleDomainService.java:230,740`）⇒ ⇒ 该 api 侧闸门现为**防御性冗余**（两侧都有，仍**一致**，**不构成漂移**）⇒ **建议**由域 owner 择机订正注释（本 lane **不碰产品代码**）。
> ⚠️ **本条的 Java Evidence 指针复核通过**：`ArticleGetService.java:121-126` / `ArticleListService.java:117-119` / `ArticleCreateService.java:144-153` **三处逐行对上**（原句保留）· 核对日期 2026-10-30。
> ⚠️ **证据强度声明（不要当成活体）**：本次关闭依据 = **L1 静态双侧源码对照**（两侧 `文件:行号` 逐条读）· **本轮未跑跨栈活体探针** ⇒ ⇒ 原 §8.3 里的「article 域双侧活体 **0 条**」那项**仍开着**（属 G-3 双侧活体，**不阻塞阶段 1**）⇒ ⇒ **不要把本条的关闭读成「article-api 的公开读已双侧活体验过」**。

### UNRESOLVED-003 —— Filter 链的实际 `@Order` 全序 ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：`GoStylePathNormalizingFilter`(HIGHEST) / `ArticleGoStyleCorsFilter`(HIGHEST) /
  `GoStyleMaxConnsFilter`(+1) / `GoStyleCpuSheddingFilter`(+3) / `HttpMiddlewareFilter`(+5) /
  `GoStyleMaxBytesFilter`(+6) / `GoStyleJsonBodyLimitFilter`(+7) 的**实际生效顺序**是否与 Go 链序同构？
- **Java Evidence**：各 Filter 上的 `@Order` 注解（静态可见）。
- **Why Unknown**：两个 Filter 同为 `HIGHEST_PRECEDENCE`，实际先后取决于 bean 名 / 注册序，
  **只有容器起来才知道**。§3.3 同款：「注解存在」≠「生效」。
- **Impact**：顺序错 ⇒ 413/503 是否带 CORS 头、`path.Clean` 是否在鉴权前生效。
- **Required Confirmation**：启动容器打印 `FilterRegistrationBean` 顺序，或补一条装配层断言。
- **✅ 关闭依据（2026-10-24）**：见本文件「第二轮」节第 3 条 —— 已用**装配层测试**钉死实际生效序
  （并把该结论固化成 `ArticleApiAssemblyWiringTest` 的链序断言 ⇒ 后来者改 `@Order` 会立刻变红）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）· 核对日期 2026-10-30。
> **补可复算指针（本条原为 PTRLESS · Java Evidence 原文只写「各 Filter 上的 `@Order` 注解（静态可见）」）**：
> - **守门（装配层 · 真起容器）**：`service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:173-174`（`effectiveFilterOrderMatchesGoChain` ⇒ 断言实际生效序 == Go 的链序 ＋ 路由层（CORS / path.Clean）必须最先 ＋ traceparent 的位置约束）。
> - **现行 `@Order` 全序（逐个当场核过行号）**：`service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleGoStyleCorsFilter.java:79`（**+1**）→ `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java:70`（**+2**）→ `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:190`（**+3**）→ `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:96`（**+4**）→ `service/common/src/main/java/com/rag/common/protection/GoStyleBreakerFilter.java:87`（**+5**）→ `service/common/src/main/java/com/rag/common/protection/GoStyleCpuSheddingFilter.java:63`（**+6**，与域内 `service/article/article-api/src/main/java/com/rag/article/article/api/auth/GoStyleJwtAuthFilter.java:101` 的 **+6** 同值）→ `service/common/src/main/java/com/rag/common/spring/HttpMiddlewareFilter.java:40`（**+7**）→ `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:83`（**+8**）→ `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:78`（**+9**）。
> ⚠️ **本条原文列的 `@Order` 数值（`+1/+3/+5/+6/+7`）已全部过期**（原句保留）⇒ **现行全序见上** ⇒ ⇒ 「装配层测试钉死实际生效序」这一**结论仍成立**（守门仍在位、仍在断言相对序）。
> ⚠️ **问法已部分变形（结构性变更，非漂移）**：本条问的是「**Filter 链**的全序」⇒ 但 **form 值闸门已于 2026-10-25（commit `1adc627d`）从 servlet Filter 改成 HandlerInterceptor** —— `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitWebMvcConfig.java:42`（`registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE)`）⇒ ⇒ 它**不再参与 Filter 链排序**（改走 MVC interceptor 序，**排在鉴权之前**）⇒ ⇒ 「Filter 链全序」这一子问已由共享层接管，**域内不再有 `GoStyleGetFormValuesFilter`**（已被删除）。

### UNRESOLVED-004 —— `@GrpcClient` 是否真在容器装配 + Nacos 寻址 ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：`ArticleRpcClient` / `UserRpcClient` 的 `@GrpcClient("article-rpc")` 阻塞 stub
  是否真被注入？`discovery:///article-rpc` 在本部署能否解析到实例？
- **Java Evidence**：`ArticleRpcClient.java:52-53`、`application.yml:56-69`
  （`negotiation-type: plaintext` 是必须的，否则 devh 默认 TLS）。
- **Why Unknown**：AGENTS.md §3.3 的 Boot-4 条款要求「凡用 `@XxxListener` 须有装配层测试」。
  本组件**没有**任何装配层测试；50 条测试全部用 `@MockitoBean` 把 client 换掉了 ——
  **恰好把要验证的那一段替换掉了**。
- **Impact**：若装配失败，所有端点在首次调用时报 NPE/IllegalStateException ⇒ 被 `handleAny` 兜成裸 500。
- **Required Confirmation**：起真实 Spring 上下文（不 mock client）断言两个 stub 非 null。
- **✅ 关闭依据（2026-10-24）**：见本文件「第二轮」节第 1 条 —— 已起**真实 Spring 上下文**（不 mock client）
  断言两个 stub 非 null ⇒ **真在容器装配**。（Nacos 寻址属于**部署期**依赖，仍随部署验证。）

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/client/ArticleRpcClient.java:52-53`（`@GrpcClient("article-rpc")` ＋ 阻塞 stub 字段）· RAG `service/article/article-api/src/main/resources/application.yml:69-73`（`grpc.client.article-rpc.address: discovery:///article-rpc` ＋ **`negotiation-type: plaintext`**）· 守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:114-115`（起**真实 Spring 上下文**（不 mock client）断言两个 stub 非 null）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条 Java Evidence 写的 `application.yml:56-69` ⇒ `negotiation-type: plaintext` 实际在 **`:73`** ⇒ 现行指针取 **`:69-73`**（`:56-59` 是 tomcat 三旋钮，与本条无关）⇒ ⇒ **「真在容器装配」的结论复核成立**（`ArticleApiProperties`/`ArticleRpcClient` 均未 mock 掉）。
> ⚠️ **边界不变**：**Nacos 寻址仍属部署期依赖**（本轮未起真 Nacos ⇒ 不宣称已验）⇒ 原文这句**保留**。

### UNRESOLVED-005 —— 真实 Tomcat 下 405→404 空 body 是否仍无 `Content-Type` ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：`ResponseEntity.status(404).build()`（`GlobalExceptionHandler.java:314-318`）
  在真实容器里会不会被 Tomcat 补上 `Content-Type`？
- **Why Unknown**：同 UNRESOLVED-001；MockMvc 测不到。
- **Impact**：低（客户端可据此区分两种 404，Go 侧同样区分）。
- **Required Confirmation**：同 UNRESOLVED-001 的真实 Tomcat 探针。
- **✅ 关闭依据（2026-10-24，真 Tomcat）**：同 `UNRESOLVED-001` —— 见本文件「第二轮」节第 4 条。
  ⇒ 真容器实测 405→404 仍是**空 body**且**无 `Content-Type`**，与 Go 一致。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/advice/GlobalExceptionHandler.java:341-343`（`@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` ⇒ `ResponseEntity.status(HttpStatus.NOT_FOUND).build()` ⇒ **空 body、无 `Content-Type`**）· 守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:393-394`（`realTomcatMethodMismatchIsEmptyBodyNotFound` ⇒ **真 Tomcat** 实测）· 核对日期 2026-10-30。
> ⚠️ **指针订正**：本条写的 `GlobalExceptionHandler.java:314-318` ⇒ 现行 **`:341-343`**（该文件其后被扩写，方法整体下移）⇒ ⇒ **「405→404 空 body 且无 Content-Type」的结论复核成立**（真容器守门仍在位）。

### UNRESOLVED-006 —— 共享层（`com.rag.common`）在 article-api 上的真实生效 ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：`GoStyleGrpcClientBreakerInterceptor` / `GoStyleCpuSheddingFilter` / `GoStyleMaxConnsFilter` /
  `HttpMiddlewareFilter` / `GoStyleJsonBodyConverterConfig` 这 5 个共享件是否**真**被 `scanBasePackages={"…","com.rag.common"}` 扫到？
- **Java Evidence**：`ArticleApiApplication.java:81-84`。
- **Why Unknown**：扫描是运行期行为；且 `HttpMiddleware.java:105-129` 的 worker 池
  （`SynchronousQueue` + 512 上限 + `AbortPolicy`）在饱和时**抛 `RejectedExecutionException`**，
  该分支的 HTTP 输出本轮未验证。
- **Impact**：漏扫 ⇒ 保护层**静默消失**且不报错（AGENTS.md §3.3）。
- **Required Confirmation**：补一条「上下文里这些 Bean 都存在」的装配层测试。
- **✅ 关闭依据（2026-10-24）**：见本文件「第二轮」节第 2 条 —— 已补**装配层测试**，断言这 5 个共享件
  **真的被扫到**（而不是「Bean 存在」这种弱断言）。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/ArticleApiApplication.java:81-84`（`@SpringBootApplication(scanBasePackages = {"com.rag.article.article.api", "com.rag.common"})` ⇒ **扫到 `com.rag.common`**）· 守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:129-152`（`sharedCommonBeansAreReallyRegistered` ⇒ 断言各共享件 Bean **真在上下文里** ＋ `GoStyleJsonBodyConverter` **真在 MVC 的 message converter 链里**）· 核对日期 2026-10-30。
> ⚠️ **计数订正**：本条正文说「**5 个**共享件」⇒ 守门现已断言 **6 个**（新增 `GoStyleBreakerFilter` ＋ `GoStyleMaxBytesFilter`；`GoStyleJsonBodyConverterConfig` 另以「转换器在链里」单独断言）⇒ ⇒ **结论不变**（共享层**真被扫到** ＋ **真生效**）。
> ⚠️ **边界不变（原文保留）**：`HttpMiddleware` 的 worker 池（`SynchronousQueue` ＋ 512 上限 ＋ `AbortPolicy`）在饱和时抛 `RejectedExecutionException` ⇒ **该分支的 HTTP 输出本轮仍未验证**（原文「本轮未验证」一句**不改**）。

### UNRESOLVED-007 —— `SecurityRpc` 的启动期行为在 `NonBlock:false` 下的差异 ✅ **已关闭（2026-10-24，零新代码）**
- **Question**：若 Go 侧日后给 `SecurityRpcConf` 显式写 `NonBlock: false`，`zrpc.MustNewClient`
  会在 etcd 解析不到 `security.rpc` 时 **panic**，而 RAG 侧无此 client ⇒ **启动失败 vs 启动成功**。
- **Why Unknown**（原文）：当前 yaml 未配 ⇒ 走 default `true`（`zrpc/config.go:30`）⇒ 不阻塞 dial。
  结论**依赖默认值**，不是设计对齐。
- **✅ 关闭依据（2026-10-24 实测到消费点，结论已定）**：
  | 事实 | 出处（消费点） |
  |---|---|
  | `NonBlock` 的 tag 默认值 = **true** | `go-zero@v1.10.2/zrpc/config.go:30` `NonBlock bool \`json:",default=true"\`` |
  | 消费点：`NonBlock` ⇒ `WithNonBlock()` / `WithBlock()` | `zrpc/client.go:65-69` |
  | `WithBlock()` ⇒ `options.NonBlock = false`（**阻塞 dial**） | `zrpc/internal/client.go:147-151` |
  | `MustNewClient` ⇒ `logx.Must(err)` ⇒ **panic** | `zrpc/client.go:50-54` |
  | Go 的 api **零消费者**：`SecurityRpc` 在 `api/` 下除定义/配置外**引用数 = 0** | `api/internal/svc/service_context.go:17,25` + 全目录 grep |
  | yaml 未配 `NonBlock` ⇒ 取默认 true ⇒ 非阻塞 dial ⇒ **启动不 panic** | `api/etc/article-api.yaml:14-18` |
  ⇒ **今天的实际行为：两侧都启动成功 ⇒ 无差异** ✅
  ⇒ 差异**只在**「yaml 显式写 `NonBlock: false`」时显形 ⇒ **条件性（config-gated）**，
  且作用在一个**零消费者**的 client 上。
- **判定**：**条件性差异，当前不可达**（与 `DRIFT-ARTICLE-RPC-007`「配置面误导，今天无行为差异」同类）。
  ⇒ 不是"未验证"，而是"**已验证且当前无差异**" ⇒ 本条关闭。
- **⚠️ 遗留的登记要求**：若真源日后给 `SecurityRpcConf` 显式写 `NonBlock: false`
  ⇒ 立刻登记为一条 DRIFT（启动失败 vs 启动成功），**不要**再留在 UNRESOLVED 里。
- **⚠️ 为什么不"顺手补一个 client"**：devh 的 `@GrpcClient` 默认也是**非阻塞**建 channel
  ⇒ 即使补上，RAG 也**不会**像 Go 那样 panic ⇒ 补了也不产生对齐效果，
  反而引入一个**零消费者**的启动期依赖 ⇒ 不做。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，零新代码）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据（Go 侧逐条回到**消费点**）：`go-zero@v1.10.2/zrpc/config.go:30`（`NonBlock bool \`json:",default=true"\`` ⇒ **默认 true**）· `go-zero@v1.10.2/zrpc/client.go:65-69`（`if c.NonBlock { WithNonBlock() } else { WithBlock() }` ⇒ 消费点）· `go-zero@v1.10.2/zrpc/internal/client.go:147-151`（`WithBlock()` ⇒ `options.NonBlock = false` ⇒ **阻塞 dial**）· `go-zero@v1.10.2/zrpc/client.go:50-54`（`MustNewClient` ⇒ `logx.Must(err)` ⇒ **panic**）· Go `service/article/api/internal/svc/service_context.go:17,25`（`SecurityRpc` 字段 + 装配 ⇒ **零消费者**）· Go `service/article/api/etc/article-api.yaml:14-18`（`SecurityRpcConf` **未配 `NonBlock`** ⇒ 取默认 true）· 核对日期 2026-10-30。
> ⚠️ **结论不变**：**今天的实际行为 = 两侧都启动成功 ⇒ 无差异**；差异仅在「yaml 显式写 `NonBlock: false`」时显形（**config-gated**）且作用在一个**零消费者**的 client 上 ⇒ ⇒ **「已验证且当前无差异」而非「未验证」** ⇒ **不改判**。

### UNRESOLVED-008 —— 上游注释引用的 `DRIFT-024` 在本仓无对应台账 ✅ **已关闭（2026-10-24，零新代码）**
- **Question**（原文）：`GlobalExceptionHandler.java:118` 的「DRIFT-024 同款教训（多出来的 Content-Type / nosniff）」
  指向何处？
- **Why Unknown**（原文）：`docs/DRIFT/` 全树无 `DRIFT-024` 条目；`docs/DRIFT/_MANIFEST.json` 亦未登记。
- 🔴 **原结论「查无此编号」是错的** —— 2026-10-24 全仓 + 全部工作树搜索后找到：
  | 位置 | 内容 |
  |---|---|
  | **`docs/DRIFT/users/_shared.md:103`** | **DRIFT-024 =「text/plain 400 的响应头丢失」**（2026-10-22 E-1 收口时发现，*article-api 的 `DRIFT-ARTICLE-API-006` 的另一半从未真正对齐*）<br>症状：`Content-Type` 与 `nosniff` **全部缺失**，状态行连 reason phrase 都没有，传输退化为 `Transfer-Encoding: chunked` + `Connection: close`<br>根因：`HttpMiddleware$ObservabilityResponseWriter.setHeader` 只收进 `capturedHeaders`、等 `flushTo` 才写回 underlying，而那时底层已 committed ⇒ Tomcat **静默忽略**<br>已改为三个覆写点（`setHeader`/`addHeader`/`setContentType`）实时 propagate<br>守门：`tools/difftest/check-plaintext400-headers.sh`（**必须真实 Tomcat**） |
  | `docs/DRIFT/common/http.md:127` | 引用它当**类比**（「响应对了不代表 body 对」）—— ⚠️ **2026-10-30 订正**：原指针 `:117` 指向**空行**（订正对照表见本节下方「3 个交叉指针指错行」） |
  | `docs/DRIFT/_cross/status-snapshot-2026-10-22.md:29,46` | 跨域快照里登记了它的修复结论 |
  | `docs/DRIFT/users/admin/api.md:65,723` | 指出 **admin-api 上没有任何守门** |
  ⇒ ⇒ **`DRIFT-024` 是 users 域的编号**（域内编号会重复，跨域引用必须带域前缀才查得到）
  ⇒ **不存在"未入库的历史漂移清单"** —— 原文的这个推断**作废**。
- **✅ 本域是否受影响：不受影响**（2026-10-24 实测确认）
  - 共享层 `service/common/.../observability/HttpMiddleware.java:661-678`
    的 `ObservabilityResponseWriter.setHeader` **已改成实时 propagate**（原缺陷已修，**全域受益**）。
  - `ArticleApiAssemblyWiringTest`（**真 Tomcat**）已断言 article-api 的 400 带
    `Content-Type: text/plain;charset=utf-8` + `X-Content-Type-Options: nosniff` ✅
    ⇒ 本域**不存在**该缺陷。
  - ⚠️ 唯一残留是**已登记**的 LOW 漂移：Go 写 `text/plain; charset=utf-8`（**带空格**），
    实测是 `text/plain;charset=utf-8`（**无空格**）⇒ Tomcat 规范化 ⇒ **决定不修**。
- **判定**：引用有效、缺陷已修、本域不受影响 ⇒ 本条关闭。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，零新代码）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）· 核对日期 2026-10-30。
> **共享层已修的证据（指针订正）**：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:1054,1076,1093`（`ObservabilityResponseWriter` 的**三个**覆写点 `setHeader` / `addHeader` / `setContentType` ⇒ **实时 propagate**；`:1071` 是 `resp.setHeader(name, value)` 本体）⇒ ⇒ 原句写的 `:661-678` **已过期**（该文件其后被扩写，行号后移）⇒ ⇒ **「原缺陷已修、全域受益」的结论复核成立**。
> **本域不受影响的证据**：守门 `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java:334-335`（真 Tomcat 断言 400 带 `Content-Type: text/plain;charset=utf-8` ＋ `X-Content-Type-Options: nosniff`）· Go `net/http.Error` ⇒ 设 `text/plain; charset=utf-8` ＋ `nosniff`（**带空格**）⇒ 唯一残留仍是**已登记的 LOW**（Tomcat 规范化去空格）⇒ **决定不修**（原句保留）。
> 🔴 **原表里 3 个交叉指针指错行（本条原值保留，在此逐条订正 —— 均在**别的域**的文件里，本 lane 无写权，只订正本条里的指针文本）**：
> | 原指针 | 真实位置 | 讲的是什么 |
> |---|---|---|
> | `docs/DRIFT/users/_shared.md:103` | **`docs/DRIFT/users/_shared.md:115`** | **DRIFT-024 的定义行**（`text/plain 400 的响应头丢失`） |
> | `docs/DRIFT/common/http.md:117` | **`docs/DRIFT/common/http.md:127`** | 「只看响应头会以为没问题」（DRIFT-024 同款）—— 原 `:117` 指向**空行** |
> | `docs/DRIFT/users/admin/api.md:723` | **`docs/DRIFT/users/admin/api.md:950`** | 「`users/_shared.md` DRIFT-024 的修复在 admin-api 上没有任何守门」—— 原 `:723` 指向别的段落 |
> | `docs/DRIFT/_cross/status-snapshot-2026-10-22.md:29,46` | ✅ **两处都对** | 跨域快照里的登记 |
> | `docs/DRIFT/users/admin/api.md:65` | ✅ **对** | 「域内最大为 DRIFT-024」 |

### UNRESOLVED-009 —— RAG 侧 `dto/UpdateArticleReq.java` 的 Go 行号引用有误 ✅ **已关闭（2026-10-24，第二轮）**
- **Question**：`UpdateArticleReq.java:8` 的 javadoc 写「Go `types.go:61-71`」，
  实际 Go 的 `UpdateArticleReq` 在 `service/article/api/internal/types/types.go:74-83`。
- **Why It Matters**：不是行为差异，是**证据指针错误**。AGENTS.md §3.3 要求证据行号指向消费点；
  一条指错的行号会让后来者按 §3.1 的纪律去核对时读到 `ListArticlesResp`。
- **Required Confirmation**：修正 javadoc 行号（本轮**只读不写源码**，未改）。
- **✅ 关闭依据（2026-10-24）**：见本文件「第二轮」节第 6 条 —— javadoc 的 Go 行号引用已更正为
  `types.go:74-83`（原文 `types.go:61-71` 实为 `ListArticlesReq` / `ListArticlesResp`）。
  ⚠️ 这条**不是行为差异**，是**证据指针错误** —— 但同样必须修：错行号会让后来者按 §3.1 的纪律
  去核对时读到**另一个结构体**。

> ⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已关闭（2026-10-24，第二轮）** ⇒ **现行态 = ✅ 已关闭**（**判定不变**）。
> 依据：RAG `service/article/article-api/src/main/java/com/rag/article/article/api/dto/UpdateArticleReq.java:8`（javadoc 已写「对应 Go `types.go:74-83` 的 `UpdateArticleReq`」；订正块在 `:10-13`）· Go `service/article/api/internal/types/types.go:74-83`（`:74` = `type UpdateArticleReq struct {` … `:83` = `}` ⇒ **确为 `UpdateArticleReq`**，不是 `ListArticlesReq` / `ListArticlesResp`）· 核对日期 2026-10-30。
> ⚠️ **补当前态指针**：原句只说「javadoc 的 Go 行号引用已更正为 `types.go:74-83`」而**没给 RAG 侧行号** ⇒ 现补 **`service/article/article-api/src/main/java/com/rag/article/article/api/dto/UpdateArticleReq.java:8`** ⇒ ⇒ **「已更正、已关闭」的结论复核成立**。

### UNRESOLVED-010 —— Go `net/url` 的三个 query 解析行为，RAG 未覆盖 ✅ **已关闭（2026-10-24）**
- **Question**：`httpx.GetFormValues` 之前还有一层 Go **标准库** `net/url` 的解析，
  它有三条行为会让请求**直接 400**；本轮补的 `GoStyleFormParamCountFilter`
  **刻意只复刻了 go-zero 的 2048 判据**，这三条未覆盖：
  | # | Go 行为 | 出处（Go 1.25 标准库） |
  |---|---|---|
  | ① | `defaultMaxParams = 10000` ⇒ 超限 400「number of URL query parameters exceeded limit」 | `net/url/url.go:1001,1019-1022` |
  | ② | query 里出现 `;` ⇒ 记错并**返 400**「invalid semicolon separator in query」 | `net/url/url.go:1026-1029` |
  | ③ | query 里出现非法 `%` 转义 ⇒ **返 400** | `net/url/url.go:1034-1045` |
- **✅ 关闭依据（2026-10-24，Go 侧探针 + 真 Tomcat 实测）**：
  - **Go 侧**（Go 1.25.11 + go-zero v1.10.2 的**真包** `httpx.Parse`，探针
    `/tmp/probe-url/go/main.go`）⇒ 三条**都返 400**，文案逐字实测：
    `invalid semicolon separator in query` / `invalid URL escape "%zz"` /
    `number of URL query parameters exceeded limit`。
  - **RAG 侧（修前，真 Tomcat 实测）**：
    | 场景 | Go | RAG（修前） |
    |---|---|---|
    | `?a=1;b=2` | 400 + 分号文案 | 🔴 **200**（请求被**接受**了！） |
    | `?a=%zz` | 400 + 转义文案 | 🔴 **500 空 body** |
    | 10001 段 | 400 + 段数文案 | 🟡 400，但 body 是 **2048** 那条（**顺序反了**） |
  ⇒ **三条全是真差异** ⇒ 已**修**（filter 改名 `GoStyleGetFormValuesFilter`，
  按 Go 的顺序复刻：段数上限 → 分段解析（错误要上报）→ 2048 计数）。
  ⇒ 详见 `DRIFT-ARTICLE-API-009` 与本文件末尾「第八轮」节。
- **残留（已在同轮定性为 LOW，不修）**：**请求行里有原始非 ASCII 字节**时
  （`?a=%中`、`?a=中`）⇒ **Tomcat 在连接器层就 400**（HTML 错误页）⇒ 请求到不了应用层
  ⇒ 应用层无从插手 ⇒ 与 `DRIFT-005` 的残留同类 ⇒ **LOW，决定不修**
  （已用 characterization test 锁住现状）。


---

## 10. 最终判定（§39 · 18 项逐条）

| # | 检查项 | 结论 | 依据 / 编号 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致 | ⚠️ **部分** | 路由/鉴权/信封一致；上传失败状态码不一致（DRIFT-001） |
| 2 | 正常路径是否一致 | ✅ | §4 矩阵 60+ 行 EQUIVALENT；CASE-01 |
| 3 | 边界输入是否一致 | ⚠️ **部分** | 空值/类型/默认值一致；重复 query key 不一致（DRIFT-003、CASE-16）；**query 畸形输入（`;` / 非法 `%` / >10000 段）已对齐 Go**（DRIFT-009，2026-10-24 真 Tomcat 实测 body 逐字一致） |
| 4 | NotFound / Empty / Null 是否一致 | ✅ | `articles:null`、`secondary_tags:null`、`ext_info:null`、`author_name` 缺席 —— 四处逐条对齐 |
| 5 | Error meaning 是否一致 | ⚠️ **部分** | 业务码全一致；上传路径丢失 gRPC 码语义（DRIFT-001） |
| 6 | Error propagation 是否一致 | ⚠️ **部分** | 业务异常一致；`extractCurrentUserID` 的「200+1005」一致；panic 兜底一致 |
| 7 | External dependency failure 是否一致 | ⚠️ **部分** | article-rpc 各码映射一致；**上传路径不一致**（DRIFT-001）；user-rpc 失败均回落但**超时作用域不同**（DRIFT-002） |
| 8 | Cache hit / miss 是否一致 | ✅ N/A | 两侧无缓存（§6.2） |
| 9 | Transaction boundary 是否一致 | ✅ N/A | 两侧无事务（§6.4） |
| 10 | State transition 是否一致 | ✅ | 无组件级状态（§6.1） |
| 11 | Side effects 是否一致 | ✅ | `incr_view` → rpc `+1`；请求内昵称去重 |
| 12 | Ordering 是否一致 | ⚠️ UNKNOWN | Filter `@Order` 实际生效序未验（UNRESOLVED-003） |
| 13 | Retry 是否一致 | ✅ | 两侧零重试（§6.7） |
| 14 | Timeout scope 是否一致 | ❌ **否** | **DRIFT-002**（`user-rpc` 2s vs 70s） |
| 15 | Concurrency semantics 是否一致 | ✅ | 无共享写状态（§6.5） |
| 16 | Authentication / Authorization 是否一致 | ✅ | 7 端点鉴权边界、401 空 body、1005/1006 语义全一致（§6.3） |
| 17 | Serialization semantics 是否一致 | ⚠️ **基本一致** | §4「序列化」矩阵各行现均为 EQUIVALENT（其中 `ext_info` key 排序 **已修**：码点序 = Go 字节序，双侧探针实测，2026-10-24）；先前点名的两项 —— 非 BMP 排序边界差（DRIFT-007，**已修**）、query 上限（DRIFT-005，**已补 Go 语义闸门**）—— 均已关闭/部分关闭。⚠️ 仍判「基本一致」而非「一致」：残留的 `DRIFT-005` body 形状（Tomcat HTML 页 vs Go text/plain）与 `DRIFT-006` body 文案属**响应体形状**类差异，虽不涉序列化语义本身，仍是可观察差异 |
| 18 | Lifecycle 是否一致 | ⚠️ UNKNOWN | 静态顺序一致；Filter 链与装配未验（UNRESOLVED-003/004/006） |

### 三值结论

```
SEMANTICALLY NOT EQUIVALENT
```

理由（spec §38 要求的归纳链）：

```
Component   article-api
   ↓
Operation   POST /v1/upload（失败路径）
   ↓
Dimension   Error Identity —— HTTP 状态码
   ↓
Go          codes.Unavailable → errcode.CodeFromGrpcError → HTTP 503
   ↓
Java        StatusRuntimeException → GoStyleBadRequestException → HTTP 400
   ↓
Comparison  状态码语义不同；客户端按 ≥500 分流的重试/告警/熔断全部失效
   ↓
Risk        HIGH
```

**次要结论（同一判定下的另外两条）**：
- `GET /v1/article/:id` / `GET /v1/articles` 的 `user-rpc` 调用超时作用域 2s vs 70s（DRIFT-002，Risk MEDIUM-HIGH）。
- 重复 query key 从「取第一个」变成 400（DRIFT-003，Risk MEDIUM）。

### 证据强度声明

| 类别 | 本轮占比 | 说明 |
|---|---|---|
| 静态源码（两侧 + go-zero `v1.10.2` 消费点） | **全部** | 每条 Go 事实都带 `文件:行号`，且行号指向**消费点**（`responses.go:145-159`、`errcode/grpc.go:12-42`、`zrpc/client.go:70-71`、`unmarshaler.go:835-846`、`httpx/util.go:44-45` …） |
| 纯 Mockito / MockMvc | 50 条测试 | **不作为任何 EQUIVALENT 的证据**；仅用于「现状未被绿测锁死」的说明 |
| 双侧活体对拍 | **0 条** | 本组件**从未做过**，本轮也未做（Go 侧未运行、RAG 侧未起真实 Tomcat / 真实 gRPC） |
| 真 PG | 不适用 | api 层无 DB |
| 装配层 / 容器层 | **0 条** | UNRESOLVED-003/004/006 |

⇒ 按 AGENTS.md §6.3 状态表纪律：**未标注证据强度的断言一律按 `UNKNOWN` 处理**；
本台账 §4 矩阵中所有 EQUIVALENT 行的证据强度均为「静态源码」，
**它们是「读 Go 读得对」的证明，不是「双侧行为一致」的证明**。
---

<!-- @fix 2026-10-24 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

## 2026-10-24 修复轮：4 条未修漂移已修 + 1 条新发现，全部经**变异验证**

> 本节是**增量追加**，上文 §0~§10 任何结论**一字未删**（含 §7.1 DRIFT-001 的原始描述）。
> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树，从 `do-my-own-gh` 切出）。
>
> **证据强度四档**（比上一轮强一档）：
> **真 MVC 管线（MockMvc）+ Mockito**（本轮）· 纯 Mockito · 静态源码（Go 侧逐行读到消费点）· 双侧活体对拍（**仍 0 条**）。

### 0. 本轮做了什么

| # | 条目 | 上一轮状态 | 本轮状态 | 守门网 | 变异验证 |
|---|---|---|---|---|---|
| **F-1** | `DRIFT-ARTICLE-API-001` 上传失败恒 400 | ❌ 未修 | ✅ **已修** | `ArticleApiMvcPipelineTest` × 3 | ✅ **3/3 转红** |
| **F-1b** | 🆕 **F-1 顺带发现**：熔断打开时 Go 400 / RAG **500** | — | ✅ **已修** | 同上 × 1 | ✅ 转红 |
| **F-2** | `DRIFT-ARTICLE-API-002` `user-rpc` 超时 2s vs 70s | ❌ 未修 | ✅ **已修** | `UserRpcClientDeadlineTest` × 5 | ✅ **2/2 转红** |
| **F-3** | `DRIFT-ARTICLE-API-003` 重复 query key → 400 | ❌ 未修 | ✅ **已修** | `ArticleApiMvcPipelineTest` × 3 | ✅ **2/2 转红** |
| **F-4** | `DRIFT-ARTICLE-API-004` 锁死错误答案的绿测试 | ❌ 未改 | ✅ **已改断言** | 随 F-1 | — |

⇒ `mvn -o -pl service/article/article-api test` ⇒ **60 tests / 0 failures / 0 errors / 0 skipped**（原 50）。
⚠️ 按 §3.3：**「60 条全绿」本身不是证据**；本轮的证据是**下面那 7 条变异全部转红**。

---

### F-1 —— `DRIFT-ARTICLE-API-001` 已修：上传失败按 gRPC 码映射状态码

**Go 侧证据（读到消费点，非推断）**

```
go-zero v1.10.2 · rest/httpx/responses.go:142-159
  if handler == nil {                                  // ← article-api 没有注册 error handler
      if len(fns) > 0 { … }
      else if errcode.IsGrpcError(err) {               // ① 先问「是不是 gRPC 错误」
          http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))   // ② 按 gRPC 码映射
      } else {
          http.Error(w, err.Error(), http.StatusBadRequest)            // ③ 非 gRPC ⇒ 恒 400
      }
  }
```

判据链（逐环实测）：
- `rest/internal/errcode/grpc.go:45-55` `IsGrpcError` ⇒ 判 `err` 是否实现 `GRPCStatus() *status.Status`；
- `rpc/articleservice/article_service.go:76-79` `UploadFile` **原样返回**该错误、**不包装**；
- `zrpc/internal/clientinterceptors/` 的 `duration/prometheus/tracing` 三个拦截器**都是 `return err`**，不包装 ⇒ ② 命中；
- 映射表 `rest/internal/errcode/grpc.go:12-42`：`Unavailable⇒503`、`Internal⇒500`、`DeadlineExceeded⇒504`、
  `ResourceExhausted⇒429`、`NotFound⇒404`、未知码⇒**500**（Go 的 switch **没有 default**，落 `:41`）。

**修法**：`GoStyleGrpcStatusMapper`（新增，逐条复刻映射表）+ `GoStyleHttpErrorException`（新增，携带 HTTP 状态码）
+ `GlobalExceptionHandler#handleGoStyleHttpError`（新增，走 `GoStylePlainTextErrors.text(status, msg)`）。
`ArticleUploadService` 的 `catch (StatusRuntimeException)` 改为抛 `GoStyleHttpErrorException`。

**🔴 body 文案也必须逐字对齐（wire 层可见，上一轮没查到）**

Go 写的是 `err.Error()`，而 grpc-go 的 `*status.Error.Error()`（`internal/status/status.go:211-212`）
转发到 `Status.String()`（`:201-203`）：

```go
fmt.Sprintf("rpc error: code = %s desc = %s", s.Code(), s.Message())
```

⇒ Go 的 body 形如 `rpc error: code = Unavailable desc = minio down`。
⚠️ Java 的 `StatusRuntimeException.getMessage()` 是 `UNAVAILABLE: minio down` ⇒ **字面不同**。
且 Go 的 `codes.Code.String()`（`codes/code_string.go:31+`）用的是 **CamelCase**（`Canceled` / `DeadlineExceeded`），
Java 的 `Status.Code.name()` 是 **SCREAMING_SNAKE** ⇒ 照抄 `name()` 仍不一致。
⇒ 新增 `GoStyleGrpcStatusMapper#goCodeName` 显式维护 Go 的规范名表。

**守门网**：`ArticleApiMvcPipelineTest` 3 条
（`codes.Internal⇒500` + body 逐字断言、`codes.Unavailable⇒503`、非 gRPC⇒400）。

---

### F-1b —— 🆕 顺带发现：**熔断打开**时 Go 是 400、RAG 却是 500

上一轮只查到「gRPC 错误 → 按码映射」，**漏了非 gRPC 错误这一侧**。实测：

- Go：熔断打开时 `breaker.DoWithAcceptableCtx` 返回
  `breaker.ErrServiceUnavailable = errors.New("circuit breaker is open")`（`core/breaker/breaker.go:19`）
  —— **普通 error**，不实现 `GRPCStatus()` ⇒ `IsGrpcError` 为**假** ⇒ 走 ③ 分支 ⇒ **400**。
- RAG：`GoStyleCircuitOpenException`（`service/common`，**刻意**不是 `StatusRuntimeException`）
  ⇒ 原先无人接住 ⇒ 一路落到 `GlobalExceptionHandler#handleAny` ⇒ **裸 500**。

⇒ 修法：`ArticleUploadService` 增接 `catch (GoStyleCircuitOpenException)` ⇒ 400。

> ⚠️ **注意 RAG 侧的熔断拦截器是真装上了的**：
> `GoStyleGrpcClientBreakerInterceptor` 同时带 `@Component` + `@GrpcGlobalClientInterceptor`
> （devh 的 `AnnotationGlobalClientInterceptorConfigurer` **只认这个注解**，不认「是不是 ClientInterceptor」）
> ⇒ 该路径**可达**，不是理论分支。

**守门网**：`uploadNonGrpcFailureIs400`（注入 `GoStyleCircuitOpenException` ⇒ 断言 400 + body 前缀）。

---

### F-2 —— `DRIFT-ARTICLE-API-002` 已修：`user-rpc` 单次调用预算 2s

**Go 侧证据（三段，缺一不可）**

```
① 声明点：go-zero v1.10.2 · zrpc/config.go:31
     Timeout int64 `json:",default=2000"`
② yaml：service/article/api/etc/article-api.yaml:20-24 —— UserRpcConf **没有** Timeout 字段
     ⇒ 走 ① 的 tag 默认 = 2000ms
     （⚠️ 同一份 yaml 里 ArticleRpcConf.Timeout 是**显式** 70000 ⇒ 两个 client 差 35 倍）
③ 消费点：zrpc/client.go:70-71 → zrpc/internal/client.go:83（buildUnaryInterceptors）
        → zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
     ctx, cancel := context.WithTimeout(ctx, t)
```

**🔴 修法上最关键的一点：不能链式 `withDeadlineAfter`**

Go 的 `context.WithTimeout(ctx, 2s)` 语义是**取父子 deadline 中较早的**。
而 grpc-java 的 `io.grpc.CallOptions.withDeadline`（**grpc-api 1.66.0 字节码实测**）是：

```
builder.deadline = deadline;        // ← **直接覆盖**，不是取最小
```

⇒ `applyTo(stub).withDeadlineAfter(2, SECONDS)` 会把 HTTP 剩余预算**顶掉**
（剩 0.5s 时反而给 2s）⇒ 请求可能**超出 HTTP 总预算**。

⇒ 修法：`UserRpcClient#userRpc()` **显式算 min**：

```java
long remaining = GoStyleRequestDeadline.remainingMillis();   // 无预算 ⇒ -1
long effective = remaining < 0L ? 2000L : Math.min(remaining, 2000L);
return userServiceBlockingStub.withDeadlineAfter(effective, TimeUnit.MILLISECONDS);
```

**守门网**：`UserRpcClientDeadlineTest` 5 条
（无预算⇒2s / 预算 10s⇒仍 2s / 预算 500ms⇒500ms / 预算耗尽⇒立即到期 / 常量防漂移）。

---

### F-3 —— `DRIFT-ARTICLE-API-003` 已修：重复 query key 取第一个

**Go 侧证据（两段，顺序不能反）**

```
① rest/httpx/util.go:35-62 —— 同名 key 的多值收成 []string；**空值先被整个丢掉**
     for _, v := range values { if len(v) == 0 { continue }; filtered = append(filtered, v) }
     if len(filtered) > 0 { params[name] = filtered }        // ← 存的是**切片**
② core/mapping/unmarshaler.go:835-846（消费点）—— 目标不是 slice 时取 Index(0)
     if u.opts.fromArray { if fieldKind != Slice && != Array { mapValue = val.Index(0).Interface() } }
```

⚠️ **顺序**：先过滤空值、再取首个 ⇒ `?page=&page=2` 落进 map 的是 `["2"]` ⇒ Go 得 **2**。
若先取首个再过滤，会得到默认值 1 ⇒ 与 Go 不同。

**RAG 侧原状**：`@RequestParam String` ⇒ Spring 把多值**拼成一个字符串**（`"1,2"`）
⇒ `GoFormValues.int32("1,2")` ⇒ `parseInt` 抛 ⇒ **400**。

**修法**：`GoFormValues.first(String[])`（新增）+ controller 的 9 个 query 参数改成 `String[]` 再取首。
⚠️ 这条**不能**只改 `GoFormValues` —— Spring 的拼接发生在**参数解析阶段**，
必须让 controller 拿到**原始数组**才拿得到「第一个」。

**守门网**：`ArticleApiMvcPipelineTest` 3 条
（`?page=1&page=2⇒page=1` / `?incr_view=1&incr_view=0⇒true` / `?page=&page=2⇒page=2`）。

---

### F-4 —— `DRIFT-ARTICLE-API-004` 已改：那条**锁死错误答案**的绿测试

原用例 `uploadRpcFailureIsBareTextPlain400` 的 `@DisplayName` 写
「upload 的 RPC 失败 ⇒ **400 text/plain**（Go 无 error handler）」——
它把 **RAG 的行为**写成了 **Go 的事实**（AGENTS.md §3.3.2 点名的形态）。

⇒ 本轮**先改断言**（按 §3.3「先改/删错误断言，再改实现」的顺序），
拆成 3 条按 gRPC 码分别断言的用例，再改实现。

> ⚠️ **上一轮的 P-1 坑档（`docs/Pitfalls/article/api.md`）早就把这条写清楚了** ——
> 也就是说 **2026-10-23 那轮「记了坑、没修漂移」**。
> ⇒ **坑档 ≠ 修复**；本条是本轮补上的最后一环。

---

### 变异验证（§3.3 硬要求：证明新回归网**非假绿**）

方法：把修复**改回去**（或改成等价错误实现），重跑对应用例，看是否转红。

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | `GoStyleGrpcStatusMapper.httpStatusOf` 恒返回 400 | ✅ **转红**（`expected:<500> but was:<400>`） |
| 2 | `goErrorMessage` 改用 Java 的 `"X: Y"` 格式 | ✅ **转红** |
| 3 | `ArticleUploadService` 不再接住 `GoStyleCircuitOpenException` | ✅ **转红**（`expected:<400> but was:<500>`） |
| 4 | `UserRpcClient` 去掉 `min`（恒 2s）· 预算 500ms 场景 | ✅ **转红**（实际 1999ms） |
| 5 | `UserRpcClient` 去掉 `min`（恒 2s）· 预算耗尽场景 | ✅ **转红**（实际 1999ms） |
| 6 | `GoFormValues.first` 不丢空值（先取首再过滤） | ✅ **转红**（`expected:<2> but was:<1>`） |
| 7 | `GoFormValues.first` 改成取**最后一个** | ✅ **转红**（`expected:<1> but was:<2>`） |

⇒ **7/7 全部转红** ⇒ 本轮新增的回归网**非假绿**。

⚠️ **一条方法论教训**：首轮变异脚本用 `sed` 做多行替换，BSD sed 不支持 `\n` ⇒
把文件改坏导致**编译失败**，脚本把「没跑到测试」误判成「仍全绿」。
⇒ **变异验证脚本必须三态判定**（转红 / 仍全绿 / **变异无效导致编译失败**），
否则会产出一条**假的「假绿」结论**。

---

### 本轮**未**关掉的（保持 OPEN，不得当已修）—— **历史快照（第一轮）**

> ⚠️ **本表是第一轮的快照，条目状态已过时** ⇒ **当前状态不看这里**：
> - `DRIFT-*` 的现状 ⇒ **§7 的「当前状态表 —— 活状态」**
> - `UNRESOLVED-*` 的现状 ⇒ **§9 Unresolved**（那里逐条标了 ✅ 已关闭 与关闭依据）
> ⇒ 保留本表是为了记录**第一轮当时的判断**（那是一次真实执行的产出，不抹除）。

| 条目 | 状态 | 说明 |
|---|---|---|
| `DRIFT-ARTICLE-API-005` query 参数总数上限 2048 | ✅ **已结案**（2026-10-25 第十三轮：容器旋钮对齐后 body 与 Go **逐字节一致**；⚠️ 残余窗口见 `DRIFT-HOTAPI-030`） | 2026-10-24 补应用层闸门；**2026-10-25 第十三轮**把请求行上限抬到 1028KB ⇒ 闸门首次真正可达 ⇒ 400 + Go 的 text/plain body；两条 characterization 断言已按 §3.3 改写为断言 Go 的形态 |
| `DRIFT-ARTICLE-API-006` 8–10 MiB body 文案 | 🔴 OPEN（LOW） | 本轮未动 〔**本行为历史快照；现状见 §7 当前状态表 = 🟡 已确认 LOW、不修**〕 |
| `DRIFT-ARTICLE-API-007` `ext_info` key 排序（非 BMP） | 🔴 OPEN（极低） | 本轮未动 〔**本行为历史快照；现状见 §7 当前状态表 = ✅ 已修（2026-10-24）**〕 |
| `UNRESOLVED-001` / `-005` 真实 Tomcat 下的 400/401/404 响应头 | 🔴 OPEN | MockMvc 测不到，**必须真容器** 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24，第二轮）**〕 |
| `UNRESOLVED-003` Filter 链实际 `@Order` 全序 | 🔴 OPEN | 同为容器级 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24，第二轮）**〕 |
| `UNRESOLVED-004` `@GrpcClient` 真装配 + Nacos 寻址 | 🔴 OPEN | 本轮 60 条测试仍**全部**用 `@MockitoBean` 把 client 换掉了 —— **恰好把要验证的那一段替换掉了** 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24，第二轮）**〕 |
| `UNRESOLVED-006` 共享层 `com.rag.common` 真被扫到 | 🔴 OPEN | 同上 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24，第二轮）**〕 |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | 🟡 部分关闭 | 本轮**已在 rpc 侧**核对 `public_only` / `status` 的消费方式（见 [`rpc.md`](./rpc.md) 与 [`proto.md`](./proto.md)） 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-30 · W-D lane 跨组件复核）** —— 四个子问题两侧都有 `文件:行号`；「缺跨栈活体」是**另一个独立未决项**，见 §8.3 的「article 域双侧活体 0 条」= G-3，**不阻塞阶段 1**〕 |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | 🔴 OPEN | 仍查无对应台账 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24）—— 原结论作废：`DRIFT-024` 在 **users 域** `docs/DRIFT/users/_shared.md:103`，本域不受影响**〕 |
| `UNRESOLVED-009` `UpdateArticleReq.java:8` 的 Go 行号引用有误 | 🔴 OPEN | 本轮**只读不写**该文件；建议下一轮顺手改 〔**本行为历史快照；现状见 §9 = ✅ 已关闭（2026-10-24，第二轮）**〕 |

### 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-001` 上传失败状态码 | ❌ SEMANTIC MISMATCH（HIGH） | ✅ 已修 + 变异验证 |
| `DRIFT-002` `user-rpc` 超时作用域 | ❌ SEMANTIC MISMATCH（MEDIUM-HIGH） | ✅ 已修 + 变异验证 |
| `DRIFT-003` 重复 query key | ❌ SEMANTIC MISMATCH（MEDIUM） | ✅ 已修 + 变异验证 |
| `DRIFT-004` 锁死错误答案的绿测试 | 🔴 OBSERVABLE DIFFERENCE | ✅ 已改断言 |
| **F-1b** 熔断打开时的状态码 | ❌ **未记录**（RAG 500 vs Go 400） | ✅ 已修 + 变异验证 |
| 其余 005~007 / UNRESOLVED-* | OPEN | **OPEN** |
| **组件判定** | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮只关掉 4 条**已记录**漂移 + 1 条**新发现**；
`DRIFT-005~007` 与 8 条 `UNRESOLVED-*` 仍在，且**证据强度上限仍是 MockMvc/Mockito**
（**无双侧活体对拍、无真实 Tomcat、无装配层测试**）⇒
本轮全部 ✅ 一律读作「**在 MockMvc 级证据下一致**」，不是「已闭环」。

---

<!-- @fix 2026-10-24 · 第二轮 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

## 2026-10-24 第二轮：**装配层真容器测试** —— 关闭 5 条 `UNRESOLVED`，并首次确认 1 条真漂移

> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树）。
> 新增测试类：`ArticleApiAssemblyWiringTest`（7 条）。
> **证据强度：真容器（`webEnvironment = RANDOM_PORT` ⇒ 真 Tomcat）+ 真 Spring 上下文 + 真实时钟**
> + **变异验证 4/4 转红**。

### 0. 为什么这一轮必须做

上一轮的 60 条测试**全部**是 `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`，
而 `@MockitoBean` **恰好把要验证的那一段替换掉了**：

| 台账条目 | 为什么 MockMvc/Mockito 测不到 |
|---|---|
| `UNRESOLVED-004` `@GrpcClient` 是否真装配 | client 被 `@MockitoBean` 整个换掉 |
| `UNRESOLVED-006` 共享层是否真被扫到 | 需要看**真实**上下文里的 Bean 集合 |
| `UNRESOLVED-003` Filter 链实际 `@Order` 全序 | MockMvc 不走 Filter 链 |
| `UNRESOLVED-001` / `-005` 400/401/404 响应头 | MockMvc **不经过 Tomcat 的 header 规范化** |

⇒ 本轮新增的测试类**刻意不 mock 任何东西**，gRPC channel 指向必然连不上的
`static://127.0.0.1:1`（只验证装配，不发调用），
HTTP 侧用 **JDK 的 `HttpClient`**（绕开任何框架再加工，断言的就是客户端真正收到的字节）。

### 1. ✅ `UNRESOLVED-004` 已关闭 —— `@GrpcClient` 真装配

| 项 | 值 |
|---|---|
| 断言 | `articleRpcClient.articleRpc()` 与 `userRpcClient.userRpc()` **非 null** |
| 变异 | 摘掉 `@GrpcClient("article-rpc")` 注解 ⇒ 字段不再被注入 ⇒ **转红** ✅ |

⚠️ 附带确认：devh 的 `@GrpcClient` 在 **Bean 初始化期**就建 channel（不是懒建）
⇒ 测试环境没有 Nacos 时 `discovery://` 解析失败 ⇒ **上下文起不来**
⇒ 必须覆盖成 `static://`。
⚠️ 另一条：**必须整个关掉服务发现**（`spring.cloud.discovery.enabled=false`）——
否则 Nacos 客户端连不上会让 `webServerStartStop` 直接失败
（`Client not connected, current status:STARTING`），**上下文根本起不来**。

### 2. ✅ `UNRESOLVED-006` 已关闭 —— 共享层真被扫到

断言 6 个共享件 Bean 存在 + **`GoStyleJsonBodyConverter` 真在 MVC 的 converter 链里**。

⚠️ **`GoStyleJsonBodyConverter` 本身不是 Bean**（它在
`GoStyleJsonBodyConverterConfig.extendMessageConverters` 里 new 出来）
⇒ 断言「Bean 存在」测不到「它真被插进了转换器链」
⇒ 必须查 `RequestMappingHandlerAdapter.getMessageConverters()`。
（第一版写成了 `ctx.getBean(GoStyleJsonBodyConverter.class)` ⇒ 直接 `NoSuchBeanDefinition`。）

| 变异 | 结果 |
|---|---|
| `scanBasePackages` 去掉 `"com.rag.common"` | ✅ **转红**（保护层整体消失） |

### 3. ✅ `UNRESOLVED-003` 已关闭 —— Filter 链的**实际生效顺序**

**Go 出处**（go-zero v1.10.2 · `rest/engine.go:130-166` `buildChainWithNativeMiddlewares`）：

```go
chn := chain.New()
chn = chn.Append(TraceHandler)          // ①
chn = chn.Append(logHandler)             // ②
chn = chn.Append(PrometheusHandler)      // ③
chn = chn.Append(MaxConnsHandler)        // ④
chn = chn.Append(BreakerHandler)         // ⑤
chn = chn.Append(SheddingHandler)        // ⑥
chn = chn.Append(TimeoutHandler)         // ⑦
chn = chn.Append(RecoverHandler)         // ⑧
chn = chn.Append(MetricHandler)          // ⑨
chn = chn.Append(MaxBytesHandler)        // ⑩
chn = chn.Append(GunzipHandler)          // ⑪
```

⇒ 与本组件相关的子序列：**MaxConns → Breaker → Shedding → Timeout → MaxBytes**。

**真容器实测链序（2026-10-24）**：

```
1. ArticleGoStyleCorsFilter           @Order(HIGHEST_PRECEDENCE)
2. GoStylePathNormalizingFilter       @Order(HIGHEST_PRECEDENCE)
3. GoStyleMaxConnsFilter              @Order(HIGHEST+1)
4. GoStyleBreakerFilter               @Order(HIGHEST+2)
5. GoStyleCpuSheddingFilter           @Order(HIGHEST+3)
6. HttpMiddlewareFilter               @Order(HIGHEST+5)
7. GoStyleMaxBytesFilter              @Order(HIGHEST+6)
8. GoStyleJsonBodyLimitFilter         @Order(HIGHEST+7)
```

⇒ **3→8 与 Go 的链序逐条一致** ✅
⇒ **1、2 两位与 Go 也一致**：Go 侧 `WithCors`（`server.go:148-153`）包装的是 **router**，
而 `path.Clean`（`patrouter.go:48,60`）在 router **内部**
⇒ Go = **CORS 在外、path.Clean 在内** ⇒ RAG 实测同构 ✅（**不是巧合**）。

> ⚠️ **前两位同为 `@Order(HIGHEST_PRECEDENCE)`** ⇒ 它们的**相对**顺序由 **Bean 注册序**决定，
> **不是 `@Order` 表达的** ⇒ 这一条只能靠**真容器**测出来。
> ⇒ 测试里把**完整链序**钉死，既是回归网，也把「相对顺序是容器决定的」这件事留了痕。

| 变异 | 结果 |
|---|---|
| 把 `GoStyleCpuSheddingFilter` 的 `@Order` 从 `+3` 改成 `+9` | ✅ **转红**（链序被打乱） |

> ⚠️ **2026-10-29 真实性复核（W-D lane · 订正，不改结论）**：上方「真容器实测链序（2026-10-24）」的**绝对 `@Order` 数字已过期** —— 共享层后来整体重排过，**2026-10-29 实测当前值**为：`ArticleGoStyleCorsFilter` **+1** · `GoStylePathNormalizingFilter` **+2** · `GoStyleTraceparentFilter` **+3** · `GoStyleMaxConnsFilter` **+4** · `GoStyleBreakerFilter` **+5** · `GoStyleCpuSheddingFilter` **+6** · `HttpMiddlewareFilter` **+7** · `GoStyleMaxBytesFilter` **+8** · `GoStyleJsonBodyLimitFilter` **+9**（`service/common/src/main/java/com/rag/common/protection/GoStyleCpuSheddingFilter.java:63` 等，各 `@Order` 注解处）。⇒ **但本条的结论仍成立**：**这 8 个 Filter 的相对次序与 2026-10-24 快照完全一致**（仍与 Go 的 `MaxConns → Breaker → Shedding → Timeout → MaxBytes` 同序）⇒ ⇒ 「3→8 与 Go 的链序逐条一致 ✅」**不变**。⚠️ **另有一处子结论已过期**：上方「前两位同为 `@Order(HIGHEST_PRECEDENCE)` ⇒ 相对顺序由 Bean 注册序决定」—— 现已**分别**是 `+1` / `+2` ⇒ **相对顺序已由 `@Order` 显式表达**，不再是「容器决定」。核对日期 2026-10-29。

> 🔴 **本条变异第一次判成「假绿」—— 是我的变异工装有问题**：
> 被变异的是 `service/common`，而命令是 `mvn -pl service/article/article-api test`
> ⇒ **不重编 `service/common`**，跑的是本地仓库里的**旧 jar** ⇒ 变异根本没生效。
> ⇒ 加 `-am`（并补 `-Dsurefire.failIfNoSpecifiedTests=false`）后才真正转红。
> ⇒ **教训：变异一个非目标模块的源码时，必须确认它被重编了**，否则会产出一条**假的「仍全绿」**。
> ⇒ 已记入 [`docs/Pitfalls/article/api.md`](../../Pitfalls/article/api.md)。

### 4. ✅ `UNRESOLVED-001` / `-005` 已关闭 —— 🔴 **并首次确认一条真漂移**

真 Tomcat 实测结果：

| 场景 | Go | RAG 实测 | 判定 |
|---|---|---|---|
| 400 的 body | `http.Error` ⇒ 裸文案 + `\n` | ✅ 一致 | ✅ |
| 400 的 `X-Content-Type-Options` | `nosniff` | ✅ 一致 | ✅ |
| 400/404 的 **`Content-Type`** | `text/plain; charset=utf-8`（**分号后有空格**） | `text/plain;charset=utf-8`（**无空格**） | 🔴 **差一个空格** |
| 401 的 body | 空 | ✅ 空 | ✅ |
| 401 的 `Content-Type` | **无**（Go 只 `WriteHeader(401)`） | ✅ 无 | ✅ |
| 404 的 body | `404 page not found\n` | ✅ 一致 | ✅ |
| 方法不匹配 | **404 空 body**（不是 405） | ✅ 一致 | ✅ |

⇒ **`UNRESOLVED-001` 从 `UNKNOWN` 升级为「已确认的 LOW OBSERVABLE DIFFERENCE」**：
`Content-Type` 的参数分隔符后少一个空格。

**根因（已定位到具体机制，不是推断）**

1. Go 的 `net/http.Error`（`net/http/server.go`）写的是**字面量**
   `h.Set("Content-Type", "text/plain; charset=utf-8")` ⇒ **带空格**。
2. RAG 侧 `GoStylePlainTextErrors` 写的**也是**带空格的同一个字面量
   （`ResponseEntity.header(CONTENT_TYPE, "text/plain; charset=utf-8")`）。
3. ⚠️ **但这个原值根本没到达客户端** ——
   Spring 的 `ServletServerHttpResponse.writeHeaders()` 在
   `servletResponse.getContentType() == null` 时会调
   `setContentType(mediaType.toString())`，
   而 **Java 的 `MediaType.toString()` 不在分号后加空格**
   ⇒ 原始 header 被**覆盖**成 `text/plain;charset=utf-8`。
4. **实测证明（对照变异）**：把 RAG 的常量从带空格改成无空格 ⇒ **输出字节不变** ⇒
   证明「原值不过那层归一化就出不来」。

| 变异 | 结果 | 含义 |
|---|---|---|
| 把常量改成 `"text/plain"`（去掉 charset） | ✅ **转红** | 测试是**真网**（输出字节真的变了会被抓到） |
| 把常量改成 `"text/plain;charset=utf-8"`（去掉空格） | ⚪ **仍全绿**（**预期**） | ⇒ **证明 RAG 写的原值到不了客户端** |

> ⚠️ **上游只在一处绕过了这个归一化**：
> `GoStyleJsonBodyLimitFilter`（Filter 里用 `setHeader` 裸写）能保住空格，
> 而**异常处理这条路**（`ResponseEntity` ⇒ Spring 写响应）**没绕过** ⇒ 这就是差异的来源。

**为什么不修（记录理由，供后续决策）**

- 该差异**语义等价**：按 RFC 9110，`;` 后的可选空白（OWS）不是语法的一部分
  ⇒ **任何** HTTP 客户端解析两者一致。
- 要修必须**绕过 Spring/Tomcat 的 `setContentType` 路径**（例如在异常处理器里直接写
  `HttpServletResponse`），那会动到**本组件风险最高的一条路径**（错误写出），
  并连带影响 `GlobalResponseAdvice` 的「放行 4」分支与 401/404 空 body 路径。
- ⇒ 判定：**已确认的 LOW OBSERVABLE DIFFERENCE，本轮不修**。
  测试里用**characterization test** 锁住实测现状，且 `@DisplayName` 明说「与 Go 差一个空格」
  —— **不是把 RAG 现状写成 Go 的事实**。

### 5. 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| `UNRESOLVED-001` / `-005` 真 Tomcat 响应头 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭**（并确认 1 条 LOW 漂移） |
| `UNRESOLVED-003` Filter 链实际顺序 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭**（实测链序已钉死） |
| `UNRESOLVED-004` `@GrpcClient` 真装配 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭** |
| `UNRESOLVED-006` 共享层真被扫到 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭** |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | 🟡 部分关闭 | ✅ **已关闭（2026-10-30 · W-D lane 跨组件复核）** —— 关闭理由：原判未决的原因是「`article-rpc` 侧**没核** ⇒ 不能替对端背书」⇒ **该未决前提本身已消失**（rpc 侧 2026-10-29 已逐条回源核 + 2026-10-30 独立复核，四个子问题两侧都有 `文件:行号`）⇒ **不是「判不了」，是「当初的依赖方已核完」** ⇒ ⇒ 逐条见 §9 `UNRESOLVED-002` · ⚠️ **证据强度 = L1 静态双侧源码对照**（**本轮未跑跨栈活体探针**）⇒ 「article 域双侧活体 0 条」那项**仍开着**（§8.3 = G-3，**不阻塞阶段 1**）⇒ **不要把本条的关闭读成「公开读已双侧活体验过」**（本表「修复前」列的 🟡 保留原值） |
| `UNRESOLVED-007` `SecurityRpc` 的 `NonBlock:false` 差异 | 🔴 OPEN | ✅ **已关闭**（2026-10-24，零新代码）：已读到消费点（`zrpc/config.go:30` tag 默认 true → `client.go:65-69` → `internal/client.go:147-151` → `MustNewClient` panic）⇒ **条件性差异，当前不可达** |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | 🔴 OPEN | ✅ **已关闭**（2026-10-24，零新代码）：已定位到 users 域台账条目 + 共享层已修 + 本域真 Tomcat 用例已断言 400 响应头 |
| `UNRESOLVED-009` `UpdateArticleReq.java:8` 的 Go 行号引用有误 | 🔴 OPEN | ✅ **已关闭**（2026-10-24，第二轮）〔**2026-10-29 订正**：原「修复后」格写「🔴 OPEN（本轮只读不写该文件）」⇒ **与紧邻的 §6「✅ `UNRESOLVED-009` 已关闭」直接矛盾** ⇒ 已按实测收敛为 ✅；**原文保留于「修复前」格**〕 |
| `DRIFT-005~007` | 🔴 OPEN | 🔴 OPEN〔**2026-10-29 订正**：本行是**第二轮当时**的快照 ⇒ **现状见 §7 当前状态表** —— `DRIFT-ARTICLE-API-005` ✅ **已结案**（2026-10-25 第十三轮）· `DRIFT-ARTICLE-API-006` 🟡 **已确认 LOW、不修**（2026-10-24）· `DRIFT-ARTICLE-API-007` ✅ **已修**（2026-10-24）〕 |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮关掉的是 **5 条「从未验证」的 UNRESOLVED**（证据强度提升），
**没有关掉任何一条 DRIFT**；另**新增确认 1 条 LOW OBSERVABLE DIFFERENCE**（`Content-Type` 空格）。
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

### 6. ✅ `UNRESOLVED-009` 已关闭 —— javadoc 的 Go 行号引用更正

`dto/UpdateArticleReq.java:8` 原写「Go `types.go:61-71` 的 `UpdateArticleReq`」，
但实测 **`types.go:61-71` 是 `ListArticlesReq` / `ListArticlesResp`**，
`UpdateArticleReq` 的真实位置是 **`types.go:74-83`**（2026-10-24 实测确认）。

⇒ 已改。⚠️ 这条本身**不是**行为差异，是**证据指针错误** ——
但按 AGENTS.md §3.3「证据行号指向消费点」，一条指错的行号会让后来者
按 §3.1 的纪律去核对时**读到另一个结构体** ⇒ 属于「必须顺手修掉」的那一类。

### 7. 当前状态表 —— **活状态**（2026-10-24 更新，跨轮次累积）

> ⚠️ **读法**：本表是 `article-api` 的**当前状态权威面**；本文件后面各轮的「判定变化」表是**历史记录**，
> 只说明"那一轮改了什么" ⇒ **判断某一条的现状，一律回本表**，不要读历史轮次的表。
> ⚠️ 本表**只登记仍有讨论价值 / 本轮有变动的条目**；更早已关闭的条目见各轮「判定变化」表与 §7.5。

| 条目 | 状态 | 说明 |
|---|---|---|
| `DRIFT-ARTICLE-API-005` query 参数总数上限 2048 | ✅ **已结案**（2026-10-25 第十三轮） | 🔴 **原定性（「RAG 无上限」）已被真容器实测推翻**：Tomcat 的 `server.max-http-request-header-size`（默认 8 KB）**更早**触发 ⇒ **状态码两侧都是 400**，且 RAG 阈值**更严**（≈2040 < Go 的 2049）⇒ 不存在 DoS 暴露。已**补应用层 Go 语义闸门**（`GoStyleFormParamCountFilter`，**2026-10-24 第八轮已改名** `GoStyleGetFormValuesFilter`），真容器实测 body 与 Go **逐字节一致**（2/2 变异验证）。✅ **2026-10-25 第十三轮已闭合**：容器旋钮（请求行 1028KB + 参数 2048）落地 ⇒ 闸门真正可达 ⇒ body 与 Go **逐字节一致**（原「残留」关闭）。⚠️ 残余窗口见 `DRIFT-HOTAPI-030`（**跨域面 ⇒ 由 `global` / G4 写权，本表不重复登记**）<br>⚠️ **2026-10-29 对齐**：状态格原写「🟡 **部分关闭**（2026-10-24）」⇒ **与同行说明自相矛盾**（说明已写「已闭合」）且与 §7.5 条目「✅ 已修（2026-10-24）」+ 本文件 `:1378` 的「✅ **已结案**」不一致 ⇒ **本轮收敛为单一状态 ✅ 已结案**；**原行号 `DRIFT-005` 亦已改为权威编号** `DRIFT-ARTICLE-API-005` |
| `DRIFT-ARTICLE-API-006` 8–10 MiB body 文案 | 🟡 **已确认 LOW，不修**（2026-10-24） | 可观察契约相同（400 + text/plain + nosniff），差异仅在 body 文案：Go 侧 Go 1.25 探针实测 = `"unexpected EOF"`（`json.NewDecoder(io.LimitReader(r.Body, 8<<20)).Decode` 解析半个文档）；RAG 侧 = `"request entity too large"` ⇒ 已用 characterization test 锁住实测现状<br>⚠️ **2026-10-29 对齐**：编号由 `DRIFT-006` 改为权威编号 `DRIFT-ARTICLE-API-006`；**状态格不变**（🟡 = 本仓取值域里的「已确认不修」，与 §7.5 `:682` 的「🟡 **LOW，确认不修**」一致） |
| `DRIFT-ARTICLE-API-007` `ext_info` key 排序（非 BMP） | ✅ **已修**（2026-10-24） | 已按**码点序**比较（= Go 的 UTF-8 字节序），见本文件末尾「第三轮」节<br>⚠️ **2026-10-29 对齐**：编号由 `DRIFT-007` 改为权威编号 `DRIFT-ARTICLE-API-007`；**状态格不变**（与 §7.5 `:696`「✅ **已修**」一致） |
| `DRIFT-ARTICLE-API-009` Go `net/url` 的三条 query 解析行为 | ✅ **已修**（2026-10-24） | 修前 ②**静默放过**（200）、③**500 空 body**、①**顺序反了** ⇒ 已按 Go 的顺序复刻（段数上限 → 分段解析（错误上报）→ 2048 计数），真容器实测 **body 逐字一致** ⇒ 见本文件末尾「第八轮」节<br>⚠️ **2026-10-29 对齐**：编号由 `DRIFT-009` 改为权威编号 `DRIFT-ARTICLE-API-009`；**状态格不变**（与 §7.5 `:718`「✅ **已修（2026-10-24）**」一致） |
| `DRIFT-ARTICLE-API-011` `application.yml` 少 `auth:` 一层 ⇒ **组件起不来** | ✅ **已修**（2026-10-29 · G3） | 启动阻断类 ⇒ 修前 `article-api` **根本起不来** ⇒ 见 §7 条目 `:774` · 探针副产品（实跑才暴露）<br>⚠️ **2026-10-29 对齐**：本条**本轮有变动** ⇒ 按本表读法（「只登记仍有讨论价值 / **本轮有变动**的条目」）**补登记**（此前只出现在 §7 条目，未进活状态表） |
| `DRIFT-ARTICLE-API-012` JSON **字段顺序**漂移 | ✅ **已修**（2026-10-29 · G3） | Go 按 struct 声明序 / Jackson 按字母序 ⇒ 见 §7 条目 `:806`<br>⚠️ **2026-10-29 对齐**：同上 ⇒ **补登记**到活状态表 |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | ~~🟡 部分关闭~~ ⇒ ✅ **已关闭（2026-10-30 · W-D lane 跨组件复核）** | 四个子问题（`GetArticle`/`ListArticles` 尊重 `public_only` · 返回 `status == PUBLISHED` · `CreateArticle` 的 status 文案格式）两侧都有 `文件:行号`（Go `get_article_logic.go:41-43` / `list_articles_logic.go:46-48` / `create_article_logic.go:58,82-91` ≡ RAG `ArticleDomainService.java:230-236,740-744` / `ArticleGetService.java:121-126` / `ArticleListService.java:117-119` / `ArticleCreateService.java:144-153`）⇒ ⇒ 「原判未决 = 对端没核」这个**前提已消失** ⇒ ⇒ **逐条见 §9 `UNRESOLVED-002`**。⚠️ **证据强度 = L1 静态双侧源码对照**（**未跑跨栈活体探针**）⇒ 「仍缺跨栈活体对拍」是**另一个独立未决项**（§8.3「article 域双侧活体 0 条」= G-3，**不阻塞阶段 1**）⇒ ⇒ **本行不再把「缺活体」当作本条未闭合的理由**（原值 🟡 部分关闭 以删除线保留） |
| `UNRESOLVED-007` `SecurityRpc` 的 `NonBlock:false` | ✅ **已关闭**（2026-10-24） | 结论依赖 go-zero 的 tag 默认值，不是设计对齐 ⇒ 已读到消费点确认为**条件性差异、当前不可达**（详见 §9） |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | ✅ **已关闭**（2026-10-24） | 🔴 **原结论作废**：`DRIFT-024` 在 **users 域** `docs/DRIFT/users/_shared.md:103`，common 域与 `_cross` 快照都在引用它；本域**不受影响**（共享层已修 + 真 Tomcat 已断言） |
| 🔴 **新增确认**：`Content-Type` 少一个空格 | 🟡 **已确认为 LOW OBSERVABLE DIFFERENCE** | 本轮首次由真 Tomcat 确认；**决定不修**（理由见上节），已用 characterization test 锁住实测现状 |

---

<!-- @fix 2026-10-24 第三轮 · 由 align/article 工作树产出 · 基线 commit e440d37 -->

## 2026-10-24 第三轮：`DRIFT-ARTICLE-API-007` 已修（`ext_info` key 按**码点序**输出）

### ✅ FIX-09 —— `DRIFT-ARTICLE-API-007` 已修（排序的**非 BMP 边界**）

#### Go 出处（消费点，Go 1.25 **标准库**，不是 go-zero）

```
$GOROOT/src/encoding/json/encode.go:805-807
  slices.SortFunc(sv, func(i, j reflectWithString) int {
      return strings.Compare(i.ks, j.ks)      // ← 逐字节比 UTF-8
  })
```

⚠️ **台账原引的 `encode.go:805` 是 Go 标准库 `encoding/json`**，不是 go-zero
（go-zero v1.10.2 的 `core/mapping/` 下**没有** `encode.go`；只有 `marshaler.go` / `unmarshaler.go`）。
⇒ 本条已把出处更正到标准库的具体文件与行号。

⇒ `strings.Compare` 是**逐字节**比较 UTF-8 ⇒ 等价于**码点序**。

#### Java 侧（修复前）

`ArticleApiSupport.cloneStringMap` 用 `new TreeMap<>(source)` ⇒ 自然序 = `String.compareTo`
= **UTF-16 码元序**。

#### 🔴 差异只在**非 BMP** 字符上出现

- 对 **BMP 内**字符：UTF-16 码元序 == 码点序 == UTF-8 字节序 ⇒ 两侧**一致** ⇒ ASCII 用例测不出来。
- 对**非 BMP**（增补平面，如 U+1F600 😀）：UTF-16 把它拆成代理对 `D83D DE00`（落在 U+D800–U+DFFF），
  **排在 U+E000–U+FFFF 之前**；码点序里它排在它们**之后** ⇒ 两侧**顺序不同**。

#### 双侧探针实测（2026-10-24，Go 1.25.11 + JDK 21）

同一组 key：`a` / `中`(U+4E2D) / U+E000 / U+FFFD / `😀`(U+1F600)

| 实现 | 顺序 |
|---|---|
| Go `encoding/json` | `a, 中, U+E000, U+FFFD, 😀` ← **期望** |
| Java `TreeMap` 自然序（修复前） | `a, 中, 😀, U+E000, U+FFFD` ← ❌ |
| Java `TreeMap` + 码点比较器（修复后） | `a, 中, U+E000, U+FFFD, 😀` ← ✅ 与 Go 一致 |

#### 修复

`ArticleApiSupport` 新增 `CODE_POINT_ORDER` 比较器（逐码点比较，短者为前缀时短者在前），
`cloneStringMap` 改为 `new TreeMap<>(CODE_POINT_ORDER)` + `putAll`。

⚠️ 比较器与 `equals` **自洽**（两个不同的字符串码点序列必不同）⇒ TreeMap 不会出现"键冲突丢数据"。

#### 证据

- **双侧探针**：Go 侧 `encoding/json.Marshal` 与 Java 侧两种比较器同跑（上面的表）⇒ 差异是**实测**的，不是推理的。
- **回归网**：`ArticleApiMvcPipelineTest#extInfoKeysWithNonBmpCharactersUseCodePointOrder`
  （真 MockMvc 管线 + 真 Jackson 序列化 + 真 proto map ⇒ 非 BMP key 走完整条路）。
  ⚠️ 读 body 必须用 `getContentAsByteArray()` + **UTF-8** 解码：
  `MockHttpServletResponse.getContentAsString()` 用的是 response 的 character encoding
  （没带 charset 时是 ISO-8859-1）⇒ 非 ASCII 会乱码。
- **变异验证（三态判定）**：`MUT-RED`
  —— 把 `cloneStringMap` 改回 `new TreeMap<>(source)`（自然序）⇒ 用例转红。
- **模块级**：`ArticleApiMvcPipelineTest` **39 条全绿**（原 38 ⇒ +1）；
  `article-api` **68 tests / 0 failures / 0 errors / 0 skipped**（原 67 ⇒ +1）；
  连同 `article-rpc` 115 + `article-common` 15 ⇒ **article 域合计 198 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### 判定变化（FIX-09）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-API-007` `ext_info` key 排序（非 BMP） | ❌ OBSERVABLE DIFFERENCE（极低） | ✅ **已修** + 双侧探针 + 变异验证 |
| `DRIFT-005` query 参数总数上限 2048 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `UNRESOLVED-002` / `007` / `008` | 🔴 OPEN | 🔴 **OPEN**（本轮未动）〔**2026-10-29 订正**：本行是**第三/四轮当时**的快照 ⇒ **现状见 §9** —— `UNRESOLVED-007` ✅ **已关闭**（2026-10-24，零新代码）· `UNRESOLVED-008` ✅ **已关闭**（2026-10-24，零新代码）· `UNRESOLVED-002` 🟡 **部分关闭**（仍缺跨栈活体对拍）〕 |
| `Content-Type` 空格（已确认不修） | 🟡 LOW OBSERVABLE DIFFERENCE | 🟡 **不变**（已用 characterization test 锁住） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮只修掉 1 条**极低**风险漂移；
`DRIFT-005` / `DRIFT-006` 与 3 条 `UNRESOLVED` 仍在，
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）⇒ 本条的 ✅ 读作「**在双侧探针证据下一致**」。

#### 📌 一条可复用的元教训

**"ASCII 用例全绿"不等于"排序语义一致"。**
⇒ 对"排序/比较"类契约，测试**必须**挑一个**两侧会分歧的输入**。
本例的分歧点是**代理对**（非 BMP），需要刻意构造 —— 随手写的用例几乎必然落在 BMP 内，
于是"两侧一致"被**假绿**地确认了。
⇒ 对照本轮 `DRIFT-010`（直方图桶）：**同一类错误的两面** ——
一个测了名字没测桶，一个测了 BMP 没测非 BMP。
⇒ **通用判据**：写"两侧应当一致"的用例前，先问一句
**「什么样的输入会让两侧不一致？」**，并把它写进用例。

---

<!-- @fix 2026-10-24 第四轮 · 由 align/article 工作树产出 · 基线 commit bedd9a5 -->

## 2026-10-24 第四轮：`DRIFT-ARTICLE-API-005`（query 参数上限）—— 🔴 **台账原定性被实测推翻**

### 🔴 先说结论：台账原写的「RAG **无上限**」是**错的**

原台账（§4 对照表 + §7「本轮未关掉的」表）写：

> `DRIFT-005` query 参数总数上限 2048 —— Go `httpx/util.go:15,48-53` 累计超 2048 个非空值即 400；**RAG 无上限**。

**2026-10-24 真 Tomcat 实测推翻了后半句**：

| 场景（真 Tomcat，默认配置） | 实测 |
|---|---|
| 2049 个非空 query 值（≈ 8196 字节） | **400**，body = **Tomcat 的 HTML 错误页** |
| **2048** 个非空 query 值（≈ 8192 字节） | **400**（同样） |
| 3000 个**空值** query 参数（≈ 6000 字节） | 请求**通过** ⇒ 走到 controller ⇒ 下游 gRPC 不可达 ⇒ 500 |

⇒ **RAG 并非"无上限"**：Tomcat 自己在**连接器层**就拒了（Boot 4 的
`server.max-http-request-header-size`，默认 **8 KB**）。
⇒ 而且 **2048 个（Go 允许的上限）在 RAG 默认配置下就已经被拒** ⇒
**RAG 的阈值比 Go 更严**（≈2040 个值 < Go 的 2049）⇒ **不存在"无上限解析"的 DoS 暴露**。

⇒ 因此原来的定性「RAG 无上限 ⇒ OBSERVABLE DIFFERENCE」**站不住**，
真实的差异只剩**响应体形状**（见下）。

### ✅ FIX-10 —— 新增 `GoStyleFormParamCountFilter`（Go 的 2048 判据落到应用层）

**Go 出处（逐行，消费点）**

```
go-zero@v1.10.2/rest/httpx/util.go:10-16
  const (
      // most servers and clients have a limit of 8192 bytes (8 KB)
      // one parameter at least take 4 chars, for example `?a=b&c=d`
      maxFormParamCount = 2048
  )

go-zero@v1.10.2/rest/httpx/util.go:22-65  GetFormValues（下面摘录的是其中 :35-54 的双层循环）
  for name, values := range r.Form {
      for _, v := range values {
          if len(v) == 0 { continue }                             // ← 空值不计数
          if n < maxFormParamCount { filtered = append(filtered, v); n++ }
          else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
      }
  }
```

调用链：handler 的 `httpx.Parse`（`internal/handler/article/*.go:21`）→ `httpx.ParseForm`
→ `httpx.GetFormValues` ⇒ 超限 ⇒ `httpx.ErrorCtx` → `httpx/responses.go:142-159`
（未注册 error handler ⇒ `handler == nil`；该 error 非 gRPC error ⇒ 走 else 分支）
→ `http.Error(w, err.Error(), 400)`
⇒ **400** + text/plain + nosniff + body = `"too many form values, error: " + r.Form.Encode()` + `"\n"`。

**实现**：`service/article/article-api/.../config/GoStyleFormParamCountFilter.java`
（`@Order(HIGHEST_PRECEDENCE + 8)`，紧贴 controller —— Go 侧这个上限在**路由 handler 内**，
即所有中间件之后 ⇒ Java 侧放在最后一个 filter 位最贴近）。

⚠️ **刻意放在 api 自己的 config 包**，**不**动 `service/common`（被 16 个组件依赖，
与 `align/common` 工作树并发修改会冲突，AGENTS.md §5.3）。

**逐条复刻的 Go 语义**

| 语义 | Go 出处 | 本实现 |
|---|---|---|
| 上限 2048（数**非空值**） | `util.go:15,48-53` | `MAX_FORM_PARAM_COUNT = 2048` + `countNonEmptyValues` |
| 分段规则（`&` 切分、`;` 跳过、空 key 跳过、首个 `=` 切分、解码失败跳过） | `net/url/url.go:1019-1054` | `parseGoQuery` |
| 错误 body 的 `r.Form.Encode()` | `net/url/url.go:1055-1073` | `encodeGoValues` |
| `QueryEscape`（`A-Za-z0-9-_.~` 不转义、空格 ⇒ `+`、其余 ⇒ `%XX` 大写） | `url.go:108-161,282-284` | `queryEscape` |
| key 排序 = **字节序**（= 码点序） | `url.go:1060` `slices.Sorted` | `compareByCodePoint`（与 `DRIFT-ARTICLE-API-007` 同一个坑） |
| `http.Error` 的响应形状 | `net/http/server.go` | `writeGoStyleBadRequest`（与 `GoStyleJsonBodyLimitFilter` 同款） |

#### 证据（真 Tomcat + 真 HTTP）

| 用例 | 断言 |
|---|---|
| `tooManyFormValuesIsGoShapedBadRequest` | 2049 个非空值 ⇒ **400** + body **逐字节** = `"too many form values, error: " + r.Form.Encode() + "\n"`（`a=1` × 2049）✅ |
| `encodedFormMatchesGoUrlValuesEncode` | body 里的 `r.Form.Encode()` = `a=%E4%B8%AD&a=~&a=%2A&b=a+b&z=1&…` —— **key 字节序** + Go 的转义 ✅ |
| `exactlyMaxFormValuesIsAccepted` | 恰好 2048 ⇒ **不**触发（Go 的判据是 `n < 2048`）✅ |
| `emptyValuesAreNotCounted` | 3000 个**空值** ⇒ **不**触发（Go 的 `len(v)==0 ⇒ continue`）✅ |

⚠️ **Go 探针实测**（Go 1.25.11，`net/url`）用来钉死转义规则：
`url.QueryEscape("*") == "%2A"`（`*` **不在** Go 的 unreserved 集合里 ⇒ 要转义）、
`"~"` 不转义、`" "` ⇒ `"+"`、`"中"` ⇒ `"%E4%B8%AD"`；
`url.Values{a:[中,~,*], b:[a b]}.Encode() == "a=%E4%B8%AD&a=~&a=%2A&b=a+b"`。
⇒ **本实现与 Go 的 `Values.Encode()` 逐字节一致。**

#### 变异验证（三态判定）

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | `MAX_FORM_PARAM_COUNT` 2048 ⇒ 2047 | ✅ **转红**（"2048 个值恰好在上限内"用例） |
| 2 | 不再跳过空值（把 `if (!v.isEmpty())` 去掉） | ✅ **转红**（"3000 个空值不该被拒"用例） |

⇒ **2/2 转红** ⇒ 守门网**非假绿**。

**测试计数**：`ArticleApiFormParamLimitTest` **4 条全绿**（新建，真 Tomcat + 真 HTTP）；
`ArticleApiAssemblyWiringTest` **10 条全绿**（原 7 ⇒ +3：链序新增一个 filter + 两条 DRIFT-005 事实）；
`article-api` **75 tests / 0 failures / 0 errors / 0 skipped**（原 67 ⇒ +8）；
连同 `article-rpc` 115 + `article-common` 15 ⇒ **article 域合计 205 / 0 / 0 / 0**（`BUILD SUCCESS`）。

### 判定变化（FIX-10）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-API-005` 的**定性** | 「RAG **无上限**」 ⇒ OBSERVABLE DIFFERENCE | 🔴 **原定性被推翻**：RAG 有上限（Tomcat 的 8 KB，更严）；真实差异只剩**响应体形状** |
| 应用层的 Go 2048 判据 | 🔴 无 | ✅ **已实现**（`GoStyleFormParamCountFilter`，真容器实测 body 与 Go 逐字节一致） |
| 默认部署下的 400 **body** | Tomcat 的 HTML 错误页 | Tomcat 的 HTML 错误页（**不变**）⇒ 🟡 **LOW OBSERVABLE DIFFERENCE，决定不修** |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `UNRESOLVED-002` / `007` / `008` | 🔴 OPEN | 🔴 **OPEN**（本轮未动）〔**2026-10-29 订正**：本行是**第三/四轮当时**的快照 ⇒ **现状见 §9** —— `UNRESOLVED-007` ✅ **已关闭**（2026-10-24，零新代码）· `UNRESOLVED-008` ✅ **已关闭**（2026-10-24，零新代码）· `UNRESOLVED-002` 🟡 **部分关闭**（仍缺跨栈活体对拍）〕 |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

#### ⚠️ 为什么默认部署下的 **body 差异不修**

默认配置下，超长 query 在**连接器层**被拒 ⇒ 产出的是 Tomcat 的 HTML 错误页
（`<!doctype html>…HTTP Status 400 – Bad Request`），而 Go 是 text/plain 的
`too many form values, error: …`。
⇒ 要改它就得替换 Tomcat 的错误页产出（`ErrorReportValve`）⇒
**风险远大于收益**（且属于容器级改动，不是本组件该做的事）。
⇒ 登记为 **LOW OBSERVABLE DIFFERENCE**，并用 characterization test 钉住实测现状。
⚠️ **状态码本身两侧已一致（都 400）** ⇒ 这一条**不是**「无上限」问题。

#### ⚠️ 本条**未**覆盖的相邻 Go 行为（另立，不在本条范围）

| Go 行为 | 出处 | RAG 现状 |
|---|---|---|
| `net/url` 自己的 `defaultMaxParams = 10000` ⇒ 超限 400「number of URL query parameters exceeded limit」 | `url.go:1001,1020-1022` | Tomcat 的 `maxParameterCount` 默认也是 10000，但**判据不同**（数"参数"vs 数"`&` 分段"） |
| query 里出现 `;` ⇒ Go 记错并**返 400**「invalid semicolon separator in query」 | `url.go:1026-1029` | 本 filter 按 Go 的分段规则**跳过**该段，**不返 400** |
| query 里出现非法 `%` 转义 ⇒ Go **返 400** | `url.go:1034-1045` | 本 filter **跳过**该段，**不返 400** |

⇒ 这三条都是**独立的**相邻差异，未修 ⇒ 已在 §9 的 UNRESOLVED 里登记方向。

#### 📌 一条可复用的元教训

> **台账写「RAG 无上限」之前，应该问一句：「RAG 所在的那一层，会不会已经有上限？」**
>
> 本轮的教训链：
> ① 台账凭**源码**判定"RAG 无上限"（Spring 的 `@RequestParam` 确实不限制总数）；
> ② 补真容器用例时才发现 —— **Tomcat 在连接器层就先拒了**，而且**比 Go 更严**；
> ③ 于是这条漂移从「DoS 暴露」降级成「响应体形状差异」。
>
> ⇒ 对照 AGENTS.md §3.1「不要拿未验证的结论当基准」：
> **"RAG 侧没有 X" 这类断言，必须在**运行时**验证，而不是只看 RAG 的源码** ——
> 因为 RAG 跑在**容器**里，容器自带一堆上限。
> ⇒ 同 AGENTS.md §3.3.1「配置里没写 ≠ 没开」的镜像版：
> **「你的代码里没写 ≠ 运行时没有」**。

---

<!-- @fix 2026-10-24 第五轮 · 由 align/article 工作树产出 · 基线 commit d8c382b -->

## 2026-10-24 第五轮：两条 UNRESOLVED 关闭（**零新代码**，靠把 Go 侧读到消费点 + 全仓检索）

### ✅ FIX-11 —— `UNRESOLVED-007` 关闭（`SecurityRpc` 的 `NonBlock` 条件性差异）

结论：**条件性差异，当前不可达** ⇒ 今天的实际行为两侧一致（都启动成功）。
详细取证见 §9 的 `UNRESOLVED-007`。

### ✅ FIX-12 —— `UNRESOLVED-008` 关闭（`DRIFT-024` **查到了**，且本域不受影响）

🔴 **原结论「全树查无此编号 ⇒ 存在一份未入库的历史漂移清单」是错的**：
`DRIFT-024` 在 **users 域**的 `docs/DRIFT/users/_shared.md:103`（域内编号会重复，
跨域引用**必须带域前缀**才查得到）。

⇒ 本域**不受影响**（共享层 `HttpMiddleware` 的 writer 已修为实时 propagate；
article-api 的真 Tomcat 用例已断言 400 带 `Content-Type` + `nosniff`）。

### 判定变化（FIX-11 / FIX-12）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `UNRESOLVED-007` `SecurityRpc` 的 `NonBlock:false` | 🔴 OPEN | ✅ **已关闭**（条件性差异，当前不可达） |
| `UNRESOLVED-008` `DRIFT-024` 查无台账 | 🔴 OPEN | ✅ **已关闭**（已定位到 users 域条目；本域不受影响） |
| `UNRESOLVED-002` 跨栈活体对拍 | 🟡 部分关闭 | 🟡 **部分关闭**（仍缺跨栈活体对拍） |
| `UNRESOLVED-010` Go `net/url` 的三条相邻行为 | 🔴 OPEN | ✅ **已关闭**（2026-10-24 第八轮 · Go 侧真包探针 + 真 Tomcat 实测 + 3/3 有效变异转红）〔**2026-10-29 订正**：原「修复后」格写「🔴 **OPEN**（本轮登记）」⇒ **与本文件第八轮节「`UNRESOLVED-010` 关闭」直接矛盾** ⇒ 已按实测收敛为 ✅；**原文保留于「修复前」格**〕 |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN | 🟡 **已确认 LOW、不修**（2026-10-24 第六轮 · characterization test + 变异验证）〔**2026-10-29 订正**：原「修复后」格写「🔴 **OPEN**（本轮未动）」⇒ **与 §7 当前状态表不一致** ⇒ 已收敛为 🟡；**原文保留于「修复前」格**〕 |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮关闭的两条**都没有改变任何运行时行为**
（一条是"条件性但当前不可达"，一条是"引用有效且本域不受影响"），
且其余 `DRIFT` / `UNRESOLVED` 仍在，也**无双侧活体对拍**。

#### 📌 一条可复用的元教训

> **域内编号（`DRIFT-0xx` / `UNRESOLVED-0xx`）在跨域时会撞号。**
> ⇒ 跨域引用**必须带域前缀**（`users` 域的 `DRIFT-024` ≠ `article` 域的 `DRIFT-005`）。
> ⇒ 检索"查无此编号"时，**必须全仓 + 全部工作树**一起搜
> （本轮就是只搜了本域 ⇒ 误判成"存在一份未入库的历史清单"，
> 而那份清单根本不存在 —— 那条线索在 users 域的台账里）。
> ⇒ 反过来：**凡是在注释里引用别的域的编号**，要么写全 `docs/DRIFT/<域>/…` 的路径，
> 要么别引（否则下一个 agent 会和你一样查不到）。

---

<!-- @fix 2026-10-24 第六轮 · 由 align/article 工作树产出 · 基线 commit f44d168 -->

## 2026-10-24 第六轮：`DRIFT-ARTICLE-API-006` 确认 **LOW，不修**（characterization + 变异验证）

### ✅ FIX-13 —— `DRIFT-006` 8–10 MiB body 文案：**确认 LOW，不修**

#### Go 侧文案来源（探针实测，不是推理）

Go 侧超长 body 的路径（`requests.go:21` `maxBodyLen = 8 << 20`）是**静默截断**：

```
go-zero@v1.10.2/rest/httpx/requests.go
  r.Body = io.LimitReader(r.Body, maxBodyLen)     // ← 静默截断，不报错
  ...
  json.NewDecoder(r.Body).Decode(v)               // ← 解析器只拿到半个文档
```

⇒ 解析器返回的 err 是 **`unexpected EOF`** ⇒ `http.Error(w, err.Error(), 400)`
⇒ body = `"unexpected EOF\n"`。

⚠️ **本轮实测确认（Go 1.25.11 探针 `/tmp/probe-sort/go/body.go`）**：
不是读源码猜的 —— 探针直接复刻上面三行，拿一个 9 MiB 的合法 JSON 文档喂进去，
打印出的错误字符串就是 `unexpected EOF`。

#### RAG 侧实测现状

`GoStyleJsonBodyLimitFilter` **显式**拒绝 ⇒ body = `"request entity too large\n"`。

#### 可观察契约对照

| 维度 | Go | RAG | 一致？ |
|---|---|---|---|
| 状态码 | 400 | 400 | ✅ |
| `Content-Type` | `text/plain; charset=utf-8` | `text/plain; charset=utf-8` | ✅ |
| `X-Content-Type-Options: nosniff` | 有 | 有 | ✅ |
| 业务是否执行 | 不执行 | 不执行 | ✅ |
| **body 文案** | `"unexpected EOF\n"` | `"request entity too large\n"` | ❌ **仅此一项** |

⇒ **可观察契约相同，差异仅在文案** ⇒ 判定 **LOW OBSERVABLE DIFFERENCE**。

#### 为什么不修（决策，不是逃避）

要对齐文案，就得改成「先读 8 MiB → 解析失败 → 报 `unexpected EOF`」，
即**故意让请求走到解析器报错**来复刻一条**更费解**的错误信息。
⇒ 收益：客户端多认得一个字符串；代价：放弃**显式、清晰**的拒绝原因。
⇒ 决定**不修**，改用 **characterization test** 锁住现状。

#### 守门网（characterization test）

`ArticleApiMvcPipelineTest#oversizedJsonBodyIsRejectedAtEightMiB` 增加**文案断言**：

```java
assertEquals("request entity too large\n", response.getContentAsString(), …);
```

⚠️ **这条断言的语义要说清楚**（避免下一个人把它当成「Go 的事实」）：
它锁的是 **RAG 的实测现状**，**不是** Go 的期望值。
⇒ 断言消息里直接写明了「这是实测现状，与 Go 的 `unexpected EOF` 不同 ⇒ DRIFT-006 已确认 LOW，决定不修」。

#### 变异验证（三态判定）

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | 把期望值改成 Go 的 `"unexpected EOF\n"` | ✅ **转红**（`expected: <unexpected EOF\n> but was: <request entity too large\n>`） |

⇒ **1/1 转红** ⇒ 该断言**确实在评估 body 内容**，不是空过
（⚠️ 变异测试只能证明「测试不是假绿」，**不能**证明期望值对不对 ——
期望值对不对，靠的是上面那张**双侧**对照表）。

#### 测试计数

`article-api` **75 tests / 0 failures / 0 errors / 0 skipped**（断言加在既有用例上 ⇒ 条数不变）。

### 判定变化（FIX-13）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN（LOW，待定性） | ✅ **已确认 LOW，不修**（双侧文案实测 + characterization test + 变异验证） |
| `DRIFT-005` query 参数总数上限 2048 | 🟡 部分关闭 | 🟡 **部分关闭（不变）** |
| `DRIFT-007` `ext_info` key 排序 | ✅ 已修 | ✅ **已修（不变）** |
| `UNRESOLVED-002` 跨栈活体对拍 | 🟡 部分关闭 | 🟡 **部分关闭（不变）** |
| `UNRESOLVED-010` Go `net/url` 三条相邻行为 | 🔴 OPEN | ✅ **已关闭**（2026-10-24 第八轮）〔**2026-10-29 订正**：原「修复后」格写「🔴 **OPEN（不变）**」⇒ **与第八轮节的关闭结论直接矛盾** ⇒ 已按实测收敛为 ✅；**原文保留于「修复前」格**〕 |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮**没有改变任何运行时行为**（只把一条待定性的漂移定性为 LOW 并锁住），
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

### 🧹 本轮顺带修掉的**台账卫生**问题（不改行为，只改记录）

本轮在整理台账时发现并修正了**四类**「记录本身会误导下一个读者」的问题 ——
它们**不影响运行时**，但会让后来者按错的结论去改代码：

| # | 问题 | 修法 |
|---|---|---|
| 1 | **两套编号空间同形不同义**：`DRIFT-ARTICLE-API-0xx`（本台账权威）vs 上游源码注释里的 `DRIFT-001…007` —— 二者的 `005/006/007` **语义完全不同**（例如 `DRIFT-006`：前者 = body 文案，后者 = type mismatch 文案） | 文件头 + §7.6 各加**撞号警告 + 对照表**；§7.6 内的交叉引用改写全前缀（`DRIFT-ARTICLE-API-006` / `-007`） |
| 2 | §7 的表头写「本轮**未**关掉的（保持 OPEN）」，但表里已混入**已关闭**条目 ⇒ 表名与内容自相矛盾 | 改标题为「**当前状态表 —— 活状态**」，并写明「判断现状一律回本表，各轮『判定变化』表是历史记录」 |
| 3 | §7 表里 `DRIFT-007` 行尾仍写「`DRIFT-005` / `DRIFT-006` 仍 🔴 OPEN」 ⇒ **已被本域后续两轮推翻** | 删掉陈旧尾巴（两条的真实状态见同表其它行） |
| 4 | §7.5 的三条目正文用**现在时**描述 RAG 的旧实现（`new TreeMap<>(source)`、「无总数上限」）⇒ 描述的是**已经不存在的代码** | 每条补「**当前状态（2026-10-24）**」行；把旧描述标注为「修复前 / 首轮静态判定，已被实测推翻」 |
| 5 | 🔴 **§9 是 UNRESOLVED 的权威清单，但 6 条已被第二轮真容器实测关闭的条目，正文仍写着「Why Unknown / Required Confirmation」** ⇒ 与第二轮的关闭结论**直接矛盾**，读者会以为它们还开着 | `UNRESOLVED-001` / `-003` / `-004` / `-005` / `-006` / `-009` 逐条加 ✅ **已关闭（2026-10-24，第二轮）** + 关闭依据指针（指到第二轮对应小节）⇒ §9 现在只剩 `-002`（部分关闭）与 `-010`（仍开） |
| 6 | **第一轮的「本轮未关掉的」表与 §7 的同名表状态冲突**（前者说 005/006/007 全 OPEN，后者说已修） | 第一轮那张**降级为「历史快照（第一轮）」**并加指针（`DRIFT-*` 看 §7 / `UNRESOLVED-*` 看 §9）⇒ 全文件只剩**一个** DRIFT 现状面和**一个** UNRESOLVED 现状面 |
| 7 | 第五轮曾**就地改写**第一轮表里 `UNRESOLVED-008` 那一行 ⇒ 等于用第五轮的结论去改第一轮的历史 | **回退为第一轮原值**（「仍查无对应台账」）；关闭结论留在 §9 条目 + 第五轮小节 + §7 当前状态表 |
| 8 | §7.6 里仍写「`DRIFT-024` 在本仓查无台账 ⇒ 存在另一份未入库的历史清单」⇒ **与第五轮的结论矛盾**（它在 users 域） | 就地**更正**为 users 域条目 + 跨域撞号说明 |
| 9 | §10 第 17 项写「5/6 项一致；… query 上限缺失（DRIFT-005）」⇒ 描述的是**修之前**的状态 | 按现状重写（点名的两项已修 / 已补闸门），并说明为何仍判「基本一致」而非「一致」（残留的是响应体形状类差异） |
| 10 | §8 的 `CASE-20` Result 列写「△ DRIFT-006」⇒ 定性还是待定 | 更新为「🟡 **LOW，已确认不修**（`DRIFT-ARTICLE-API-006`）」 |

⚠️ **为什么这也算「必须顺手修掉」**（对照 AGENTS.md §3.1）：
台账是**下一个 agent 的对照基准**。基准本身写错时，
§3.1 禁的那件事（「拿现有 RAG 代码当对照基准」）就会**通过台账复活** ——
后来者读到「RAG 无上限」，很可能照着去"修"一个已经不存在的缺陷，
或者读到「`new TreeMap<>(source)`」，以为代码还这样写。

#### 📌 一条可复用的元教训

> **台账的「现在时」是最危险的一种错。**
>
> 描述某个代码点的行为时，至少要标明**时间坐标**：
> 「**修复前**的 RAG 是 `new TreeMap<>(source)`」是事实；
> 「**RAG 是** `new TreeMap<>(source)`」在修复之后就成了**假事实** ——
> 而假事实比"没有记录"更危险，因为读的人**不会怀疑它**。
>
> ⇒ **判据**：台账里每一条描述「RAG 侧是什么样」的句子，
> 都必须能回答「**这是哪一天的 RAG 侧？**」
> —— 回答不了 ⇒ 它就是个随时会骗人的陈述。
>
> ⇒ 同理，**「本轮未动」的历史表不要当成现状表** ——
> 本轮就有一次误读：差点把第三轮的「DRIFT-006 OPEN」当成现状写进当前状态表
> （已回退，历史记录保持原样，现状另起第六轮记录）。

---

<!-- @fix 2026-10-24 第八轮 · 由 align/article 工作树产出 · 基线 commit 0b5543d -->

## 2026-10-24 第八轮：`UNRESOLVED-010` 关闭 —— Go `net/url` 的三条 query 解析行为**已对齐**

### ✅ FIX-14 —— `DRIFT-ARTICLE-API-009` 已修（三条 query 解析行为）

#### 🔴 先说结论：修前的 RAG 现状是**实测**出来的，不是推理的

Go 侧（Go 1.25.11 + go-zero v1.10.2 的**真包** `httpx.Parse`，探针 `/tmp/probe-url/go/main.go`）
与 RAG 侧（**真 Tomcat**，真容器实测）的对照：

| 场景 | Go（真包实测） | RAG（修前，真容器实测） |
|---|---|---|
| `?a=1;b=2` | **400** + `invalid semicolon separator in query` | 🔴 **200**（请求被**接受**了！） |
| `?a=%zz` | **400** + `invalid URL escape "%zz"` | 🔴 **500 空 body** |
| 10001 段 | **400** + `number of URL query parameters exceeded limit` | 🟡 **400**，但 body 是 **2048** 那条 ⇒ **顺序反了** |
| `?a=%中`（原始非 ASCII） | **400** + `invalid URL escape "%\xe4\xb8"` | 🔴 **400** + **Tomcat 的 HTML 错误页** |
| `?a=中`（原始非 ASCII，无 `%`） | ✅ **接受**（`err=<nil>` ⇒ 200） | 🔴 **400** + Tomcat 的 HTML 错误页 |

⚠️ **②（分号）最糟**：Go 拒的请求，RAG **照单全收**（200）。
⇒ 这三条**都是要修的**（不是签字偏离）⇒ 判据「要不要修回去」= **要**。

#### Go 出处（逐行，消费点）

```
Go 1.25 标准库 net/url/url.go:1019-1054  parseQuery
  if !urlParamsWithinMax(strings.Count(query, "&") + 1) {
      return errors.New("number of URL query parameters exceeded limit")
  }                                        // ← ⚠️ 在解析**之前**就 return
  for query != "" {
      key, query, _ = strings.Cut(query, "&")
      if strings.Contains(key, ";") {
          err = fmt.Errorf("invalid semicolon separator in query")   // ← 无条件赋值
          continue
      }
      if key == "" { continue }
      key, value, _ = strings.Cut(key, "=")
      key, err1 := QueryUnescape(key)
      if err1 != nil { if err == nil { err = err1 }; continue }      // ← 第一个转义错误胜出
      value, err1 = QueryUnescape(value)
      …
  }
```
⇒ 这些 error 由 `net/http.Request.ParseForm` 返回，而 go-zero 的
`httpx.GetFormValues`（`rest/httpx/util.go:23-25`）**原样往上抛**：

```
go-zero@v1.10.2/rest/httpx/util.go:23-25
  if err := r.ParseForm(); err != nil {
      return nil, err
  }
```
⇒ `httpx.Parse` 失败 ⇒ `httpx.ErrorCtx` ⇒ `httpx/responses.go:142-159`
⇒ `http.Error(w, err.Error(), 400)` ⇒ **400** + body = 文案 + `"\n"`。

⚠️ **两条顺序事实（都可观察）**：
1. `r.ParseForm()`（内含 net/url 的三条）**先于** go-zero 的 2048 计数
   ⇒ 10001 段时 Go 报的是**段数**那条，不是 `too many form values`。
2. `parseQuery` 里**分号错误无条件覆盖**、**转义错误只在 `err == nil` 时设置**
   ⇒ 多个错误并存时，分号错误优先（后出现的分号段会覆盖前面的）。

#### 修复

**filter 改名** `GoStyleFormParamCountFilter` → **`GoStyleGetFormValuesFilter`**：
它现在复刻的是 Go 的 `httpx.GetFormValues`（**解析 + 计数两步**），旧名字只说了后半截 ⇒ 名字已不诚实。

按 Go 的**顺序**复刻（`doFilter`）：

| 步 | Go | 本实现 |
|---|---|---|
| ① | `parseQuery` 开头的段数上限（**解析之前**） | `countQuerySegments(query) > 10000` ⇒ 400 + Go 文案 |
| ② | `parseQuery` 的分段解析（**错误要上报**） | `parseGoQuery` 返回 `GoQueryParse{form, error}` ⇒ 有错 ⇒ 400 + Go 文案 |
| ③ | go-zero 的 2048 计数 | `countNonEmptyValues(form) > 2048` ⇒ 400 + `too many form values, error: …` |

⚠️ **错误文案的字节级保真**：
Go 的转义错误文案是 `"invalid URL escape " + strconv.Quote(那一小截)`，
而那一小截是 Go **按字节**截的（`%` + 最多 2 字节）⇒ **可能截断一个多字节字符**
⇒ 截出来的是**非法 UTF-8** ⇒ `strconv.Quote` 会输出 `\xNN`（**小写**）。
⇒ 本实现按**字节**定位与截取，并复刻了 `strconv.Quote`
（`\a \b \f \n \r \t \v`、`\xNN` 小写、`\uNNNN`、`\UNNNNNNNN`、非法 UTF-8 字节 ⇒ `\xNN`）。
⇒ Go 探针实测 `?a=%中` ⇒ `invalid URL escape "%\xe4\xb8"`，
本实现**逐字节相同**（已由真容器用例断言）。

#### 回归网（真 Tomcat + 真 HTTP）

`ArticleApiFormParamLimitTest`（**4 条 ⇒ 9 条**）：

| 用例 | 断言 |
|---|---|
| `semicolonInQueryIsRejectedLikeGo` | 400 + body **逐字** = `invalid semicolon separator in query\n` |
| `invalidPercentEscapeInQueryIsRejectedLikeGo` | 400 + body **逐字** = `invalid URL escape "%zz"\n` |
| `tooManyQuerySegmentsIsRejectedLikeGo` | 400 + body **逐字** = `number of URL query parameters exceeded limit\n` |
| `invalidEscapeFollowedByMultiByteCharIsRejectedAtConnectorLayer` | ⚠️ characterization：Tomcat **连接器层**就 400（HTML 页）⇒ 应用层不可达 ⇒ LOW 不修 |
| `rawNonAsciiInQueryIsRejectedAtConnectorLayer` | ⚠️ characterization：同上 + 对照（`?a=%E4%B8%AD` 合规编码 ⇒ 200） |

⚠️ **必须用裸 socket**：JDK 的 `URI` / `HttpClient` **自己就会拒绝**非法 `%`
（`Malformed escape pair`）⇒ 根本发不出去 ⇒ 测不到那条路径。
⚠️ **请求行必须按 UTF-8 编码**：按 ISO-8859-1 编码会把非 ASCII 换成 `?`
（实测踩过：`?a=%中` 被发成 `?a=%?` ⇒ 服务端收到的是**另一个** query）。

#### 变异验证（三态判定）—— **3 个有效变异全转红 + 2 次「变异无效」的教训**

| # | 变异 | 结果 | 判定 |
|---|---|---|---|
| A | 分号分支改回「跳过该段」 | ✅ **转红** | 有效 |
| B₁ | **只**删 key 分支的错误上报 | ⚠️ 仍全绿 | ⚠️ **变异无效** —— 用例测的是 **value** 分支（`?a=%zz` ⇒ 坏转义在 value 里）⇒ 变异没打在被测行为上 |
| B₂ | key + value 两条错误分支都删 | ✅ **转红**（且复现**修复前的原始症状**：`expected: <400> but was: <500>`） | 有效 |
| C | 去掉段数上限 | ✅ **转红** | 有效 |
| D₁/D₂ | 把段数上限挪位置（但**相对顺序没变**） | ⚠️ 仍全绿 | ⚠️ **变异无效** —— 挪到 2048 计数**之前**仍是「10000 先」的同一顺序 |
| D₃ | 把段数上限挪到 2048 计数**之后** | ✅ **转红**（body 变成 `too many form values…`） | 有效 |

⇒ **3/3 有效变异全部转红** ⇒ 守门网**非假绿**。

⚠️⚠️ **本轮最值钱的一条元教训**：
**「仍全绿」必须先排除「变异无效」，否则会把"变异没打中"误报成"测试覆盖不到"。**
本轮两次「仍全绿」都是**变异无效**（B₁ 打错分支、D₁ 相对顺序没变），
**不是**测试不敏感 ⇒ 如果不查这一步，就会得出"这条用例测不到锁序/顺序"的**错误结论**，
进而可能去写一条**其实不需要**的新用例（或是错怪测试）。
⇒ 排查手法很简单：**把变异内容打印出来 + 确认它真的落到了被测行为上**
（本轮用 `grep -c "MUT-…"` 确认标记在文件里；D₁ 的教训则是**看代码确认相对顺序**）。

#### 残留（LOW，决定不修）

**请求行里有原始非 ASCII 字节**（`?a=%中`、`?a=中`）⇒ **Tomcat 在连接器层就 400**
（HTML 错误页）⇒ 请求**到不了应用层** ⇒ 应用层 filter **无从插手**。
⇒ 与 `DRIFT-005` 的残留同类（Tomcat 的 HTML 错误页 vs Go 的 text/plain）⇒ **LOW，不修**。
⚠️ 状态码：Go 是 400（`?a=%中`）/ 200（`?a=中`），RAG 都是 400 ⇒
`?a=中` 这条**状态码也不同**，但同样只影响**不合规的客户端**（正常客户端会发 `%E4%B8%AD`）。

#### 测试计数

`ArticleApiFormParamLimitTest` **9 条全绿**（原 4 ⇒ +5）；
`ArticleApiAssemblyWiringTest` **10 条全绿**（链序断言里的 filter 改名 ⇒ 同步更新）；
`article-api` **80 tests / 0 failures / 0 errors / 0 skipped**（原 75 ⇒ +5）；
连同 `article-rpc` 117 + `article-common` 15 ⇒ **article 域合计 212 / 0 / 0 / 0**（`BUILD SUCCESS`）。

### 判定变化（FIX-14）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `UNRESOLVED-010` Go `net/url` 三条相邻行为 | 🔴 OPEN | ✅ **已关闭**（Go 侧真包探针 + 真 Tomcat 实测 + 3/3 有效变异转红） |
| `DRIFT-ARTICLE-API-009`（新增条目） | 🔴 三条真差异（② 静默放过 200、③ 500 空 body、① 顺序反了） | ✅ **已修**（body 与 Go **逐字一致**） |
| 请求行含原始非 ASCII | 未记录 | 🟡 **LOW，不修**（Tomcat 连接器层就拒 ⇒ 应用层不可达）⇒ characterization test 锁现状 |
| `DRIFT-005` / `DRIFT-006` / `DRIFT-007` | 已关闭/部分关闭 | **不变** |
| `UNRESOLVED-002` 跨栈活体对拍 | 🟡 部分关闭 | 🟡 **部分关闭（不变）** |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮关掉了一条 UNRESOLVED、修掉一条 DRIFT（**行为层面确实变了**），
但其余 `DRIFT` / `UNRESOLVED` 仍在，且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

#### 📌 一条可复用的元教训

> **「Go 的错误会一路冒泡」这件事，必须读到**消费点**才知道它会不会变成 400。**
>
> 本轮的链路是：`net/url.parseQuery` 记 error ⇒ `net/http.Request.ParseForm` **返回**它
> ⇒ go-zero 的 `httpx.GetFormValues` **原样往上抛**（`util.go:23-25`，**不是吞掉**）
> ⇒ `httpx.Parse` 失败 ⇒ `httpx.ErrorCtx` ⇒ `http.Error(w, err.Error(), 400)`。
>
> ⇒ **中间任何一层把它吞了，Go 就不会返 400** —— 而这一层恰好是 go-zero 的一行代码。
> ⇒ 同 AGENTS.md §3.3.1 的镜像版：**「错误被记录了」≠「错误被上报了」**。
>
> ⇒ 也因此，RAG 侧复刻时**不能只复刻"分段规则"** ——
> 「解析失败时怎么办」才是 Go 的**可观察行为**，
> 漏掉它就不是"漏了个细节"，而是**把一个 400 变成了 200**。

## 2026-10-24 第十二轮：**台账审计** —— 逐条核对「现状权威面」，并修掉一条**源码级偏离**（`strconv.Quote` 的 Latin-1 补充段）

### 🔴 为什么这一轮突然做「审计」而不是「对齐」

前面十一轮都在**往外找差异**（Go ↔ RAG）。这一轮反过来：**往里看台账本身**——
「台账是不是最新、最全状态」。做法是七个维度逐项过：

| 维度 | 查什么 | 结论 |
|---|---|---|
| 1 | 四个台账文件的「**现状权威面**」是否唯一且明确 | rpc.md ✅ 有指针；**api.md ❌ 缺** ⇒ 本轮补 |
| 2 | §7 / §9 / 最新轮次**三面**是否一致 | ✅ 一致（本轮补完 9+7 条状态横幅后无矛盾） |
| 3 | 编号体系有无**撞号 / 断号** | ✅ `DRIFT-ARTICLE-API-001..010`、`RPC-001..010` 连续无撞号 |
| 4 | 交叉引用（相对链接 / 章节号 / 跨档） | ❌ 4 处失效相对链接（已修）；✅ 21 条相对链接现已全有效 |
| 5 | Go 出处的 `文件:行号` 抽样验证 | ❌ **8 处指错消费点**（本轮全修，见下） |
| 6 | 状态表纪律（断言是否带核对日期） | ❌ **抓到 1 条**：`DRIFT-ARTICLE-API-008` 的横幅**缺核对日期**，且它记的计数**已过期** ⇒ 本轮补日期 + 新增 **§0.2**（详见下） |
| 7 | 测试计数与实际是否一致 | ✅ 通过（本轮全量重跑：`article-api` **86**（80 → 86，+6 新网）、`article-common` 15、`article-rpc` 136 ⇒ **article 域 237 / 0 failures / 0 errors / 0 skipped**，`BUILD SUCCESS`） |

⇒ **维度 5 挖出了一条真偏离**（下面）。

### ✅ FIX-18 —— `DRIFT-ARTICLE-API-010` 已修（`isGoPrintable` 对齐 Go 的 `IsPrint`）

#### 1. 发现路径

审计维度 5 抽样核对 Go 行号时，发现
`GoStyleGetFormValuesFilter.java` 里 `isGoPrintable` 的 javadoc 写着
「Go 的 `strconv.IsPrint`（`strconv/quote.go:296-303`）」——
而 `quote.go:296-303` 落在 **`UnquoteChar` 的转义解析**里（`UnquoteChar` 起于 `:259`），
**`IsPrint` 根本不在那儿**（真身在 `:522`）。

⇒ 按 AGENTS.md §3.3.2（「以 Go 措辞断言等价」是高危项），
**必须先确认代码到底实没实现 Go 的语义** —— 一查，**它没实现**：

| 码点 | Go 的 `IsPrint` | RAG（修复前） | 一致？ |
|---|---|---|---|
| `0x20..0x7E` | ✅ 可打印 | ✅ 可打印 | ✅ |
| `0x7F`（DEL） | ❌ 不可打印 | ❌ 不可打印 | ✅ |
| **`0xA1..0xFF`** | ✅ **可打印**（唯一例外 `0xAD`） | ❌ **不可打印** | 🔴 **不一致** |
| `0xAD`（软连字符） | ❌ 不可打印 | ❌ 不可打印 | ✅ |
| `0xA0`（NBSP） | ❌ 不可打印 | ❌ 不可打印 | ✅ |
| `> 0xFF` | unicode L/M/N/P/S + ASCII 空格 | 同 | ✅ |

⇒ **Go 会原样输出 `é`，RAG 会输出 `\u00e9`** ⇒ **wire 字节不同**。

#### 2. ⚠️ 但它在 HTTP 入口**结构上不可达** —— 这是本条最关键的一步

`goQuote` 的**唯一**调用点是 `escapeErrorMessage`，它拿到的输入是
**query 串**里 `%` 开头的**原始字节**切片（`%` + 最多 2 字节）。
⇒ 要让非 ASCII 字节进到这里，请求行里就得有**原始非 ASCII 字节**。
⇒ **而 Tomcat 在连接器层就把这类请求全拒了**（Tomcat 11.0.24，逐行）：

```
HTTP/1.1 —— org/apache/coyote/http11/Http11InputBuffer.java:468-473
    } else if (parsingRequestLineQPos != -1 && !httpParser.isQueryRelaxed(chr)) {
        String invalidRequestTarget = parseInvalid(parsingRequestLineStart, byteBuffer);
        throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget", invalidRequestTarget));
    }
    ⇒ 请求行里 `?` 之后的每个字节都要过 isQueryRelaxed；不过 ⇒ 400（连接器层）

HTTP/2  —— org/apache/coyote/http2/StreamProcessor.java:539-545
    String qs = request.queryString().toString();
    if (qs != null) {
        for (char c : qs.toCharArray()) {
            if (!httpParser.isQueryRelaxed(c)) { return false; }      // ⇒ 400
        }
    }
    ⇒ HTTP/2 的 `:path` 走**同一个** isQueryRelaxed 判据

判据本身 —— org/apache/tomcat/util/http/parser/HttpParser.java:145-172
    for (int i = 0; i < ARRAY_SIZE; i++) {                  // ARRAY_SIZE = 256
        if (IS_USERINFO[i] || i == '@' || i == '/') { IS_ABSOLUTEPATH_RELAXED[i] = true; }
        if (IS_ABSOLUTEPATH_RELAXED[i] || i == '?') { IS_QUERY_RELAXED[i] = true; }
    }
    ⇒ IS_USERINFO 只由 IS_UNRESERVED / '%' / IS_SUBDELIM / ':' 组成（:97-108），
      **全是 ASCII** ⇒ IS_QUERY_RELAXED 在 0x80..0xFF 上**恒为 false**

放宽口子 —— HttpParser.java:1138-1148（relax）
    if (isRelaxable(c)) { flags[c] = true; ... }
    ⇒ isRelaxable 的字符集是 " < > [ \ ] ^ ` { | }"（:114-117），**同样全是 ASCII**
    ⇒ **任何 relaxedQueryChars 配置都放宽不了非 ASCII**
```

⇒ **结论：query 串恒为纯 ASCII** ⇒ `goQuote` 的输入恒为 ASCII
⇒ `isGoPrintable` 的 **Latin-1 分支是结构性死代码**。

**真容器实测（2026-10-24，article-api，裸 socket）**：

| 请求行 | 状态码 | body | 谁拒的 |
|---|---|---|---|
| `?a=%zz` | 400 | `invalid URL escape "%zz"`（**Go 文案**） | **应用层**（filter） |
| `?a=%é`（`25 c3 a9`） | 400 | `<!doctype html>…` | **连接器层** |
| `?a=%©`（`25 c2 a9`） | 400 | `<!doctype html>…` | **连接器层** |
| `?a=%ÿ`（`25 c3 bf`） | 400 | `<!doctype html>…` | **连接器层** |
| `?a=%­`（`25 c2 ad`） | 400 | `<!doctype html>…` | **连接器层** |
| `?a=%中`（`25 e4 b8 ad`） | 400 | `<!doctype html>…` | **连接器层** |
| `?a=é`（`25 c3 a9`） | 400 | `<!doctype html>…` | **连接器层** |

#### 3. 那为什么还要修？（三条理由，缺一不可）

1. **留痕断言失真**（AGENTS.md §3.2 / §3.3.2）——
   javadoc 声称「Go 的 `strconv.IsPrint`」，而代码**没实现**它 ⇒
   这是**无出处的 idiomatization**，本身就不该合入。
   ⇒ **javadoc 写 Go 等价 ≠ 代码是 Go 等价**；必须反向核。
2. **端到端测不到 ⇒ 端到端测不到就一直没人管** ——
   既然这个分支在 HTTP 路径上不可达，那**唯一的守门网就是直接单元测试**。
   没有它，下次有人「简化」这段代码（或换 Tomcat 版本）时**没有任何网会响**。
3. **修复成本为零、行为影响为零** ——
   改的是一段**死代码** ⇒ 生产行为**逐字节不变**；
   但源码从此**可证**与 Go 的 `IsPrint` 等价。

> ⚠️ 这与本域第十轮的 `DRIFT-004` 相邻差异处置**刻意不同**：
> 那一条是「差异真实存在但 API 层面不可观察 ⇒ **不修**、不立条目」；
> 这一条是「差异真实存在 **且** javadoc 声称等价 **且** 修复零风险 ⇒ **修**」。
> 分野在于：**留痕是否在说谎**。

#### 4. Go 出处（逐行，消费点）

```
Go 标准库 strconv/quote.go:522-563  IsPrint
    if r <= 0xFF {
        if 0x20 <= r && r <= 0x7E { return true }              // ← :525-528
        if 0xA1 <= r && r <= 0xFF { return r != 0xAD }           // ← :529-532
        return false
    }
    … // > 0xFF：isPrint16 / isPrint32 表（:536-562）

消费点 —— strconv/quote.go:68-119  appendEscapedRune
    } else if IsPrint(r) || graphicOnly && isInGraphicList(r) {
        return utf8.AppendRune(buf, r)                          // ← :79-81 原样输出
    }
    switch r { case '\a': … }                                  // ← :82+ 落到转义分支
```

⇒ **RAG 的修复**（`GoStyleGetFormValuesFilter.isGoPrintable`）：

```java
if (r <= 0xFF) {
    // Go strconv/quote.go:524-533（IsPrint 的 Latin-1 快路径）
    if (r >= 0x20 && r <= 0x7E) {
        return true;
    }
    if (r >= 0xA1 && r <= 0xFF) {
        return r != 0xAD;          // ← 软连字符是唯一例外
    }
    return false;
}
```

#### 5. 回归网 + 变异验证（§3.3 硬要求：证明新网**非假绿**）

**新建 `ArticleGoStrconvQuoteParityTest`（6 条）** —— 用**反射**调 `goQuote`（不动生产代码的可见性），
逐**字节**比对 Go 的输出。

⚠️ **为什么必须是「直接单元测试」而不是端到端**：
端到端路径上非 ASCII 进不了这个 filter（见 §2）⇒ 端到端**测不到**这段分支
⇒ 端到端测试对它**恒绿**，等于**没有网**。

**变异验证（4/4 MUT-RED）**：

| # | 变异 | 结果 |
|---|---|---|
| MUT-1 | 整段 Latin-1 分支退回旧实现（`r < 0x7F`） | ✅ **转红**（3 条 Latin-1 断言） |
| MUT-2 | 软连字符不再排除（`r != 0xAD` ⇒ `true`） | ✅ **转红** |
| MUT-3 | Latin-1 下界 `0xA1` ⇒ `0xA0`（把 NBSP 也当可打印） | ✅ **转红** |
| MUT-4 | ASCII 上界 `0x7E` ⇒ `0x7F`（把 DEL 也当可打印） | ✅ **转红** |

⇒ 4 个变异**全部有效** ⇒ 这张网**不是假绿**。

#### 6. Go 侧探针（**可复现**：源码与逐字输出都记在这里）

> ⚠️ 探针**不进仓**（`tools/` 是共享面，多域并发必撞）⇒ 源码与输出记在本台账，复制即可复跑。

**探针源码**（Go 1.25.11 标准库，无第三方依赖）：

```go
package main

import (
	"fmt"
	"strconv"
)

func main() {
	cases := []struct {
		name string
		b    []byte
	}{
		{"ascii_zz", []byte{0x25, 0x7A, 0x7A}},
		{"latin1_e_acute", []byte{0x25, 0xC3, 0xA9}},
		{"latin1_copyright", []byte{0x25, 0xC2, 0xA9}},
		{"latin1_y_diaeresis", []byte{0x25, 0xC3, 0xBF}},
		{"soft_hyphen", []byte{0x25, 0xC2, 0xAD}},
		{"truncated_euro", []byte{0x25, 0xE2, 0x82}},
		{"truncated_cjk", []byte{0x25, 0xE4, 0xB8}},
		{"nbsp", []byte{0xC2, 0xA0}},
		{"cjk", []byte{0xE4, 0xB8, 0xAD}},
		{"emoji", []byte{0xF0, 0x9F, 0x98, 0x80}},
		{"quote_char", []byte{0x25, 0x22}},
		{"backslash", []byte{0x25, 0x5C}},
		{"space", []byte{0x20}},
		{"del", []byte{0x7F}},
		{"nul", []byte{0x00}},
		{"soh", []byte{0x01}},
		{"bel", []byte{0x07}},
		{"vt", []byte{0x0B}},
	}
	for _, c := range cases {
		q := strconv.Quote(string(c.b))
		fmt.Printf("%-22s in=%-24x out=%-30x\n", c.name, c.b, []byte(q))
	}
}
```

**逐字输出**（`out` 列即 Java 侧测试表里的 `expectedHex`）：

```
ascii_zz               in=257a7a                   out=22257a7a22
latin1_e_acute         in=25c3a9                   out=2225c3a922
latin1_copyright       in=25c2a9                   out=2225c2a922
latin1_y_diaeresis     in=25c3bf                   out=2225c3bf22
soft_hyphen            in=25c2ad                   out=22255c753030616422
truncated_euro         in=25e282                   out=22255c7865325c78383222
truncated_cjk          in=25e4b8                   out=22255c7865345c78623822
nbsp                   in=c2a0                     out=225c753030613022
cjk                    in=e4b8ad                   out=22e4b8ad22
emoji                  in=f09f9880                 out=22f09f988022
quote_char             in=2522                     out=22255c2222
backslash              in=255c                     out=22255c5c22
space                  in=20                       out=222022
del                    in=7f                       out=225c78376622
nul                    in=00                       out=225c78303022
soh                    in=01                       out=225c78303122
bel                    in=07                       out=225c6122
vt                     in=0b                       out=225c7622
```

⇒ 逐条读出来：
- `latin1_e_acute` 的 out 是 `2225c3a922` ⇒ **`é` 原样输出**（不是 `\u00e9`）⇒ 这就是 RAG 修复前**做错**的那条。
- `soft_hyphen` 的 out 是 `22255c753030616422` ⇒ `\u00ad` ⇒ **软连字符确实是唯一例外**。
- `nbsp` 的 out 是 `225c753030613022` ⇒ `\u00a0` ⇒ **`0xA0` 在 Latin-1 段下界之下**（MUT-3 钉的就是这条）。

#### 7. 🧹 本轮顺带修掉的**台账卫生**（8 处 Go 行号指错消费点）

| # | 位置 | 原写法 | 改成 | 为什么原写法错 |
|---|---|---|---|---|
| 1 | `DRIFT/article/api.md` ×3 + `Pitfalls/article/api.md` ×1 | `httpx/util.go:25-27` | **`util.go:23-25`** | `:25-27` 是 `ParseMultipartForm` 那段；`ParseForm` 的 error 上抛在 `:23-25` |
| 2 | `DRIFT/article/api.md` ×1 | `util.go:44-56  GetFormValues` | **`util.go:22-65`**（摘录的是其中 `:35-54`） | `:44-56` 是循环体，不是函数 |
| 3 | `DRIFT/article/api.md` ×1 | `util.go:11-17` | **`util.go:10-16`** | const 块是 `:10-16` |
| 4 | `GoStyleGetFormValuesFilter.java` ×5 | `util.go:24-64` / `11-17` / `22-64` / `25-27` / `44-56` | **`22-65` / `10-16` / `22-65` / `23-25` / `44-53`** | 同上（源码 javadoc 也算留痕） |
| 5 | `ArticleApiFormParamLimitTest.java` ×3 | `util.go:11-17` / `44-56` / `25-27` | **`10-16` / `37-54` / `23-25`** | 同上 |
| 6 | `DRIFT-ARTICLE-API-008` 的状态横幅 | 无核对日期 + 计数 **38 / 4,666** | **补「核对日期 2026-10-23」+ 新增 §0.2（现为 41 / 5,733）** | §6.2：断言不带核对日期 ⇒ 按 `UNKNOWN` 处理；且计数**又过期了一次**（本仓 Pitfalls `P-6`） |

⚠️ **教训**：`文件:行号` 的**偏移一两行**在通读时**完全看不出来** ——
本轮是抽样 34 条才抓到 4 条 ⇒ **抽样比例要够**，且**声明点 ≠ 消费点**的坑（§3.3.1）同样适用于行号**范围**。

### 判定变化（FIX-18）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-API-010`（新增条目） | 🔴 `isGoPrintable` 与 Go 的 `IsPrint` 在 `0xA1..0xFF` 不一致 | ✅ **已修**（Go 侧真标准库探针 + 直接单元测试 + 4/4 MUT-RED） |
| `isGoPrintable` 的 javadoc | 🔴 写着 Go 等价，行号**指到 `UnquoteChar`** | ✅ 行号改到 `quote.go:522-563`，语义描述与代码**一致** |
| 「请求行含原始非 ASCII ⇒ 连接器层 400」 | 🟡 **实测如此**（characterization test） | 🟢 **结构性必然**（HTTP/1.1 + HTTP/2 双路径逐行证明）⇒ 换配置也绕不过 |
| `api.md` 的「怎么读这份台账」指针 | ❌ **缺失** | ✅ 已补（含**跨文件轮次撞号**的警示） |
| Go 行号抽样（34 条） | ❌ 4 条指错 | ✅ **8 处全修**（台账 7 + 源码 8） |
| `DRIFT-005` / `DRIFT-006` / `DRIFT-007` / `DRIFT-009` | 已关闭 | **不变** |
| `UNRESOLVED-002` 跨栈活体对拍 | 🟡 部分关闭 | 🟡 **部分关闭（不变）** |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮修的是一段**端到端不可达**的死代码 ⇒
**行为层面零变化**；其余 `DRIFT` / `UNRESOLVED` 仍在，且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

#### 📌 一条可复用的元教训

> **「javadoc 说它等价于 Go」不是证据，是「待核实的断言」。**
>
> 本轮正是从一句 javadoc 的**行号写错**出发，顺藤摸到**代码也没实现 Go 的语义**。
> ⇒ **行号错**与**语义错**往往是**同一个疏漏的两面**：抄的时候抄的是「看起来像」的那一段，
> 而不是**真正在消费这个值**的那一段。
>
> ⇒ 与 AGENTS.md §3.3.2 呼应：**以 Go 措辞写下的断言，本身就是高危项** ——
> 它**必须在被 review 时被反向验证**（读 Go 源码 ⇒ 确认代码真的这么判）。
>
> ⇒ 另一条：**「端到端测不到」要分成两问** ——
> ① 它**可观察**吗？（本条：**否** ⇒ 不立「需要对齐」的条目）
> ② 它**有网**吗？（本条：**没有** ⇒ 必须补一张**直接单元测试**）
> 只答 ① 就放过 ⇒ 就会留下一段**没人守、还自称等价**的死代码。

---

## 2026-10-25 补记：`DRIFT-MSG-V-030` 的**域内部分**已修 —— 本模块产物现在是可执行 jar

> **登记方在 message 域**（[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-030`）——
> 它是 V-028 的同款缺陷，但**修法落在本模块自己的 `pom.xml`** ⇒ 本域已修。

| 项 | 修前 | 修后 |
|---|---|---|
| 产物 | **68 KB** library jar，**无 `Main-Class`** | **69 MB** Spring Boot fat jar，`Main-Class: org.springframework.boot.loader.launch.JarLauncher` |
| `java -jar` | 「中没有主清单属性」 | 可启动（外部依赖齐备的前提下） |
| pom | **无 `<build>` 段** | 补 `<build>` 段 + `spring-boot-maven-plugin`（版本由根 pom 的 `spring-boot-starter-parent` 继承，不写 `<version>`） |

**修法**：照 `service/message/message-api/pom.xml` 的 V-028 同款写法，在本模块 pom 补
`<build><plugins><plugin>spring-boot-maven-plugin</plugin></plugins></build>`。
启动类 `ArticleApiApplication` 唯一且带 `main` 方法 ⇒ **不显式写 `<mainClass>`**（少一处可能写错的配置）。

⚠️ **本次只证明「产物带 `Main-Class` 且是可执行 fat jar」**，**没有**真起进程验证启动成功
（会撞 Nacos / Redis / Kafka / gRPC 等外部依赖 ⇒ 起不来不等于产物有问题）。

⚠️ **「`mvn package` 成功」不算数** —— 本项目至今的每一项验证都跑在容器 / 测试环境里，
**从来没有碰过 jar 本身**（这正是 §3.3「编译通过 ≠ 产物可运行」的形态）。
⇒ **判据是守门脚本**：`tools/difftest/check-jar-executable.sh`
（逐个 api 模块解包 `META-INF/MANIFEST.MF` 看 `Main-Class`）
⇒ **2026-10-25 实测 `EXIT=0`**（修前 `EXIT=1`）。
---

## 附录 · 🧹 U+FFFD 复核结论（2026-10-27）—— **本域 2 处**是**故意保留**，**不修**

> 🔴🔴 **2026-10-27 第三十轮订正（G1 反馈）**：本节标题原写「**本域 1 处**」⇒ ⇒ **与下表不符 —— 下表本来就是 2 行**（`:1143` ＋ `:1159`，**同一份文件里的两处**，两处**都是故意**）⇒ ⇒ 标题改成「**2 处**」⇒ ⇒ **别让下一个人以为只处理了一处、把 `:1159` 当漏网的去「修」** ⇒ ⇒ **改它会直接破坏那条测试的语义**。

| 文件:行 | 表象 | 结论 |
|---|---|---|
| `src/test/java/com/rag/article/article/api/ArticleApiMvcPipelineTest.java:1143` | `.putExtInfo("` + **U+FFFD** + `", "4")` | 🟢 **故意的测试数据** |
| 同文件 `:1159` | `body.indexOf("\"" + **U+FFFD** + "\"")` | 🟢 同上 |

**为什么不能改**：该用例（`@DisplayName`：🔴 DRIFT-007：ext_info 的 key 含**非 BMP**字符时仍按码点序输出）
把 U+FFFD 当作 `ext_info` 的 **key** 来用，钉死「按**码点序**（= Go 的 UTF-8 字节序）输出」：

```
a < 中 < U+E000 < **U+FFFD** < 😀（U+1F600，非 BMP 代理对）
```

⇒ 这里的 U+FFFD 是**被测对象**，不是编码损坏 ⇒ **改它会直接破坏该测试的语义**
⇒ **本轮刻意不动** ⇒ ⚠️ 「全仓 U+FFFD 归零」这条目标**在本域不成立**（也不该成立）——
**扫描 U+FFFD 时必须先判断它是不是测试数据**。

---

## 📌 指针 · 🔴 P0 #8 域侧：**yaml 明文默认验签密钥**已去掉 ⇒ 缺变量/空串 ⇒ **启动失败**（2026-10-29 · G3 阶段 1）

> **阶段归属（`twoStage.md` §10）**：**A 类**（P0 #8）⇒ **域侧已修** ⇒ 不阻断阶段 1（**根级**半边归「**合并收口执行者**」⇒ 根因并入 G1 的 `S1-USERS-001` ⇒ **非 G4 写权面**，见下表「根级半边」行）。
> **完整记录在** [`docs/DRIFT/favorite/api.md` §24](../../DRIFT/favorite/api.md) —— **按 AGENTS.md「一条问题只在一个档里落笔」的纪律**，本域只留指针。

| 项 | 本域情况 |
|---|---|
| **落点** | `service/article/article-api/src/main/resources/application.yml`（`article-api.auth.access-secret`，**修前** = `${USER_JWT_SECRET:ae0536f9-6450-4606-8e13-5a19ed505da0}`）＋ `…/config/ArticleApiProperties.java`（新增 `@PostConstruct verifyAccessSecret()`）＋ **新增** `…/src/test/resources/application.properties`（test scope 提供密钥 ⇒ 测试不依赖环境变量 ⇒ 且**不进产物 jar**） |
| **Go 出处** | `service/article/api/etc/article-api.yaml:26-29`（`Auth.AccessSecret` = **字面量**）⇒ ⇒ **Go 侧同样把密钥写在仓里** ⇒ ⇒ 本条属「**RAG 更严**」的一侧（**不是**抄错 Go）⇒ 登记为有意偏离 |
| **为什么必须代码校验（不能只去默认值）** | Spring 的 `PropertyPlaceholderHelper`：**属性存在但为空串 ⇒ 解析为空串、不走默认值、也不报错** ⇒ ⇒ 「变量没设」被 placeholder 挡住，**「变量是空串」挡不住** ⇒ ⇒ 必须由 `@PostConstruct` 显式抛 `IllegalStateException` ⇒ ⇒ **两条路都 fail-closed** |
| **守门** | **新增** `service/article/article-api/src/test/java/com/rag/article/article/api/config/ArticleApiPropertiesSecretGuardTest.java`（**3 例** · **真 `ApplicationContextRunner`**）：① 空串 ⇒ **上下文起不来**（`hasFailed()`）② 未设 ⇒ **上下文起不来** ③ 正常密钥 ⇒ **上下文正常起**（对照组）＋ 判据边界（`null`/`""`/`"   "` ⇒ 抛） |
| **证据强度** | **L4**（真容器装配 ⇒ 上下文起不来/起得来，**不是**「方法存在」的源码断言） |
| **根级半边（归「合并收口执行者」）** | 根级 `docker-compose.yaml` 的 `USER_JWT_SECRET` 缺 `:?` ⇒ ⇒ 修后容器会 **crash-loop**（安全上 fail-closed ✔）⇒ ⇒ 运维侧要 compose 级清晰报错 ⇒ 🔴 **2026-10-29 归属订正**：原写「已转派 G4」**归属错了** —— 根级文件**无人拥有**（既不在任何 lane 白名单，也不在 `check-wave-lane-ownership.py` 的 `SHARED` 13 条 ⇒ 该守门**自己的口径**：「N 个共享文件无人拥有 ⇒ 归合并收口执行者」）⇒ ⇒ **非 G4 写权面**（且该守门断言**全是声明侧**、不校验「实际改了哪个文件」⇒ ⇒ 派给 G4 改根级文件**没有任何守门会拦**）⇒ ⇒ 根因**并入 G1 的 `S1-USERS-001`**（**不另开编号**）＋ 根 README **无 `.env.example`**（P0 #5 · 同属该根因） |

---

## 📌 指针 · 🔴 **P0 #4 主责**：`article` 域的失败路径覆盖 —— **基线口径订正 + 双侧活体缺口**（2026-10-29 · G3 阶段 1）

**归属**：`align/g3-article-comment-favorite-hot` 工作树产出；基线 = `do-my-own-gh` @ `29a0bce`。
**核对日期**：2026-10-29
**基线关联**：`docs/PhaseGoal-judge.md` §五 P0 **#4**（**本 lane 主责**）· §六 6.1 / 6.2 · §九 **G-3**

### 一、基线原文的**内部矛盾**（先摆出来，否则没法判）

> P0 #4 行原文：「上述探针**只覆盖 5 个域**（**article** / message / hot / shared-layer / favorite）；**`article` 域**（失败路径最密集的域：下载/上传/审核/S-5）**没有失败路径探针**」

⇒ ⇒ **同一句话里，article 既在「已覆盖的 5 个域」里，又在「没有失败路径探针」里**。

**拆开就清楚了** —— 基线把**两个不同维度**混在了一个「域覆盖」计数里：

| 维度 | article 域的覆盖 | 基线怎么写的 |
|---|---|---|
| ① **错误码层**（码值/文案/未知码回落） | ✅ `tools/errmsg-probe/run-parity.sh`（**真·双侧逐字节**，19 个未登记码） | 算进「5 个域」了 |
| ② **失败路径**（4xx/5xx/解析失败/上限/鉴权） | 🟡 **域内**有大量覆盖（见下表）· 但**双侧活体 = 0 条** | 写成「**没有失败路径探针**」 |

⇒ ⇒ **「没有失败路径探针」这句话，对「②的【双侧活体】」成立；对「②的【用例存在性】」不成立** ⇒ ⇒ 需订正的是**口径**，不是行为。

### 二、机器取证：article 域的失败路径覆盖**不是 0**（2026-10-29 实测）

`service/article/**/src/test/java/**` ⇒ **33 个测试类 / 294 个 `@Test`**；其中失败路径特征命中（`isBadRequest`/`isUnauthorized`/`isNotFound`/`isPayloadTooLarge`/`assertThrows`/`error_reason`/错误码字面量）**91 处**。

`article-api` 单模块（**权威计数**：`mvn -o -B -pl service/article/article-api -am clean test` ⇒ **BUILD SUCCESS**，**构建收工后**逐 `TEST-*.xml` 求和）⇒ **9 个测试类 / 102 条 / 0F / 0E / 0S**：

| 测试类 | 覆盖的失败路径 |
|---|---|
| `ArticleApiRouteAuthBoundaryTest`（17 例） | **401**（无 token / 非法 token / 过期 / `alg=none` / 签名不符 / HS384 密钥过短）· **404**（路由不存在）· **413**（超 `MaxBytes`）· **400**（`text/plain` 形状逐字断言 + 换行符）· 路径规范化 |
| `ArticleApiMvcPipelineTest`（39 例） | 业务失败分支的响应码与信封 |
| `ArticleApiFormParamLimitTest`（9 例） | 表单参数上限（Go 的 `maxFormParamCount=2048` 同款判据） |
| `ArticleApiJsonBodyLimitCaseTest`（3 例） | JSON body 读取判据的边界 |
| `ArticleApiJwtVerificationScopeTest`（7 例） | JWT 验签的**保护面**（哪些路径免鉴权） |
| `ArticleApiPropertiesSecretGuardTest`（3 例 · **本轮新增**） | 空验签密钥 ⇒ **启动失败**（fail-closed） |
| `ArticleErrorCodeContractTest`（15 例） | 码值 / 顺序 / 未知码回落 |

⚠️ **这些是【单侧】证据**（只跑 RAG）⇒ 按基线 §六 6.2 的口径，**不能当运行面证据** ⇒ ⇒ 它们**证明「失败路径有实现、且期望值有 Go 出处」**，**不证明「两侧行为相同」**。

### 三、✅ **已闭合**：**双侧活体失败路径对拍**已落仓并逐字节复跑通过（基线 §九 G-3 · **2026-10-29 当日订正**）

**为什么现在能做了**（基线的受阻前提**已过期**）：

- 基线写「Go 侧需 `etcd:2379`，宿主 `2379` **未映射**」⇒ ⇒ **2026-10-29 实测：宿主 `Sea-etcd` 的 `49319→2379` 映射已存在**（`nc -z 127.0.0.1 49319` ⇒ **TCP OPEN**）⇒ ⇒ **环境阻塞已解除**；
- Go 真源 `service/article/api/etc/article-api.yaml:9-24` 的**三个** etcd 客户端（`ArticleRpcConf` / `SecurityRpcConf` / `UserRpcConf`）**全部写死 `etcd:2379`**（docker 网络内的主机名）⇒ ⇒ 真源仓**冻结不可改**（AGENTS.md §一）⇒ ⇒ 必须用**副本配置**改成 `127.0.0.1:49319`；
- ⚠️ **`SecurityRpcConf` 在 Go 侧「配了但无消费者」**（`internal/svc/service_context.go:17,25` 只装配、**没有任何 logic 读它**）⇒ ⇒ 这一条**不构成启动前置条件**（已在 `ArticleApiProperties` 的类注释里留档）⇒ ⇒ 副本配置只需改 `ArticleRpcConf` + `UserRpcConf` 两个。

**本轮为何没做**：用户已拍板本轮范围 = 「**域内自修 6 项**」，**明确不含** article 的**双侧活体 / 真 Kafka**（`etcd` 可达只是解除了环境阻塞，不改变本轮范围）⇒ ⇒ **登记为下一轮的活**，本轮**不预支**。

**阶段归属（`twoStage.md` §10）**：**D 类**（**已闭合 ⇒ 无待实现内容**）· **优先级 P2** ⇒ **不阻断阶段一** ⇒ ⇒ ⚠️ **原写「**A 类**（P0 #4 主责）＋ 必须留在未闭合清单里」已作废**（那是 2026-10-29「本轮范围不含双侧活体」的**当轮**记录；同日共享 judge `docs/PhaseGoal-judge.md:479` 已判 **✅ 已闭合** ⇒ 探针落仓 `tools/api-full-parity-probe/article/` ＋ **主线独立复跑 EXIT=0** ＋ 变异 M2/M3 **精确转红** ⇒ ⇒ 本条**已不在未闭合清单里**）。

### 四、⇒ 结论（写清楚，免得下一轮重复劳动）

1. **P0 #4 在 article 域的「用例存在性」维度已达标**（域内 91 处失败路径断言 · 期望值有 Go 出处）⇒ **基线「article 域没有失败路径探针」的措辞需订正**（**基线文件属共享面 ⇒ 本 lane 只在域内台账留记录，交合并收口执行者改**）。
2. ⚠️ **本条已于 2026-10-29 当日闭合**（原写「双侧活体维度仍为 0 ⇒ **G-3 未闭**」**已作废**）：共享 judge `docs/PhaseGoal-judge.md:479` 判 **✅ 已闭合** ⇒ 探针落仓 `tools/api-full-parity-probe/article/`（`run.sh` + `mutate-parity.sh` + `expected.txt`）⇒ **两侧同时起服务**（Go 真源栈 + RAG 栈）⇒ 3 场景（`list_articles` / `get_article_exists` / `get_article_missing`）**逐字节**相等 ⇒ **主线独立复跑 EXIT=0** ＋ 变异 M2/M3 **精确转红** ⇒ ⇒ 「真源冻结 ⇒ 副本配置」那一步**已完成**。
3. ⚠️ **口径订正 ≠ 降级**：两个维度必须**分开记** —— 否则会出现「用单侧用例数把双侧缺口顶掉」的假闭环（这正是 §六 6.2 要防的）。

**判定命令（下一轮）**：
```bash
# Go 侧副本配置（真源零改动）
#   cp -R <GO_REPO>/service/article/api <TMP>/ && sed -i 's|etcd:2379|127.0.0.1:49319|g' <TMP>/etc/article-api.yaml
# 两侧同时跑同一批失败路径请求 ⇒ 逐字节比对（参考 tools/api-full-parity-probe/favorite/run.sh 的形状）
bash tools/api-full-parity-probe/favorite/run.sh   # 现成骨架
```

---

## 📌 指针 · 🔴 P0 #8 **补正** · 验签密钥守卫**只堵了一半**：「未解析占位符」判据缺失 ⇒ **缺变量 ⇒ 应用仍能启动**（2026-10-29 · R2 轮）

> **完整记录在** [`docs/DRIFT/favorite/api.md` §25](../../DRIFT/favorite/api.md) —— **按 AGENTS.md「一条问题只在一个档里落笔」的纪律**，本域只留指针 + 本域落点。
> ⚠️ **本条同时订正了 §24 那句「缺变量 ⇒ 启动失败（Spring placeholder 不可解析 ⇒ 绑定期炸）」** ——
> 那句**只对「显式喂键路径」成立**；**走真 ConfigData 流程时**（= 生产路径）Spring 的绑定器**不炸**，
> 反而把**占位符原文**当值绑上去 ⇒ ⇒ **应用照常启动** ⇒ ⇒ 「yaml 不给默认值」**只堵住了一半**。

| 项 | 本域情况 |
|---|---|
| **落点** | `service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleApiProperties.java`（`@PostConstruct verifyAccessSecret()` 的判据扩为 **null / 空串 / 全空白 / 未解析占位符字面量**；新增 `Pattern.compile("^\\$\\{[^}]*\\}$")`）＋ `src/main/resources/application.yml`（**注释订正**） |
| **配置键 / 环境变量** | `article-api.auth.access-secret` / `USER_JWT_SECRET`（**单段** ⇒ 不涉及多段有序入口） |
| **Go 出处** | `service/article/api/etc/article-api.yaml:27`（`Auth.AccessSecret`）写的是**字面量** ⇒ ⇒ 本条属「**RAG 更严**」的一侧 ⇒ 登记为有意偏离（**不是**「RAG 抄错了 Go」） |
| **语义等价** | 守卫**不改动任何值** ⇒ 变量一旦被提供，验签密钥与 Go 侧**逐字节一致** ⇒ 验签行为**零变化**（Go 侧消费点 = `service/article/api/internal/handler/routes.go:42`） |
| **守门** | `ArticleApiPropertiesSecretGuardTest`：**4 → 10 例**（新增 **真 `SpringApplication` + 真 ConfigData** 的 4 条 + 真 yaml 的 2 条 + 判据边界补占位符 1 例） |
| **证据强度** | **L4**（真容器装配 ⇒ 起不来/起得来）＋ **变异测试**（去掉占位符判据 ⇒ **4 条精确转红**，还原后 `git status` 干净） |
| **实测** | `mvn -o -B -pl service/article/article-api -am -DargLine=-Xmx2g test` ⇒ **BUILD SUCCESS**（EXIT=0）· 模块内 **112 tests / 0 failures / 0 errors** · 守卫 **10/10 绿** |
| **实际异常输出** | `article-api.auth.access-secret 未配置（绑到了未解析的占位符字面量 ${USER_JWT_SECRET}）：请设置环境变量 USER_JWT_SECRET（与签发方 user-api 一致）—— 本模块只验签、不签发，占位符原文是公开可知的「密钥」⇒ 任何人都能伪造 JWT` |

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/favorite/run.sh` ⇒ **EXIT=0**（2026-10-30 03:28:29 · 48s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 两侧种子就绪 ⏐ ✅ 黑名单对照 token 就绪（Redis exists=1） ⏐ ✅ 两侧各产出 31 个 case ⏐ SAME 25 条 / SAME_CODE 1 条 / SAME_BODY 2 条 / SAME_STATUS 4 条 / OBS 0 条 / SKIP 0 条（共 32 个 case） ⏐ ✅ api 整域两侧一致（SAME 全等；SAME_CODE 业务码等；SAME_BODY body 等；SAME_。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W4

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-001 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/router/patrouter.go:72-82`（`if !ok { pr.handleNotFound(w, r); return }` ⇒ 路径未注册 ⇒ **404**；`if pr.notAllowed != nil { pr.notAllowed.ServeHTTP(w, r) }` ⇒ **405 那段根本没执行**）＋ `go-zero@v1.10.2/rest/internal/cors/handlers.go:44-48`（`if r.Method == http.MethodOptions { gw.WriteHeader(http.StatusNoContent) } else { gw.WriteHeader(http.StatusNotFound) }` ⇒ **404**）＋ Go 真源 `service/article/api/article.go:33`（`rest.MustNewServer(c.RestConf, rest.WithCors())` ⇒ article-api **是四域里唯一开 CORS** 的 ⇒ `notAllowed` 被 CORS handler 接管）vs RAG `service/article/article-api/src/main/java/com/rag/article/article/api/advice/GlobalExceptionHandler.java:314`（javadoc 明写「**为什么不用**共享的 `GoStyleRouterErrorResponses#methodNotAllowed`」⇒ 刻意分叉）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleGoStyleCorsFilter.java:1`（域内 CORS filter）⇒ **差异已消失**（article 域两侧都输出 **404 空 body**；台账 `DRIFT/article/api.md:894` 那行标「✅ 本轮静态核对成立」，`DRIFT/article/api.md:122` 的「上游内部编号」与 §7 权威编号**同形不同义** —— 本条按 **上游内部编号** `DRIFT-001`（方法不匹配 ⇒ 404）判定）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| F-3 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/httpx/util.go:44-53`（同名 key 多值收成 `[]string` 存 map，空值 `continue`）＋ `go-zero@v1.10.2/core/mapping/unmarshaler.go:835-846`（`u.opts.fromArray` ⇒ 目标非 slice/array 时 `val.Index(0)` ⇒ **取第一个**）vs RAG `service/article/article-api/src/main/java/com/rag/article/article/api/form/GoFormValues.java:133`（`first(String[])` ⇒ 先丢空值再取首）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/controller/ArticleController.java:207`（`@RequestParam(name = "page", required = false) String[] page` ⇒ 声明成多值数组而非 `String`）⇒ **差异已消失**（`?page=1&page=2` 两侧都得 `page=1`；台账 `DRIFT/article/api.md:1291` 那格「❌ 未修 → ✅ 已修」为**同一编号跨表合并**后的历史快照，`DRIFT/article/api.md:947` 的 CASE-16「400」已被 `DRIFT/article/api.md:950` 之后的第 12 轮实测覆盖）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| FIX-10 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）＋ `:48-53`（`if n < maxFormParamCount { filtered = append(...); n++ } else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }` ⇒ 数**非空值**、计数**跨所有 key 共享**）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:207`（`MAX_FORM_PARAM_COUNT = 2048`）＋ `:273`（`if (nonEmpty > MAX_FORM_PARAM_COUNT) return "too many form values, error: " + encodeGoValues(parsed.form);`）＋ `service/common/src/main/java/com/rag/common/http/GoStyleFormValueLimitInterceptor.java:86`（`String violation = GoStyleFormValues.goParseViolation(request.getQueryString());` ⇒ 装闸门）＋ `service/article/article-api/src/main/resources/application.yml:185`（`go-form-limit: mode: raw` ⇒ 本域装闸门）⇒ **差异已消失**（台账 `DRIFT/article/api.md:1867` 记的落点 `GoStyleFormParamCountFilter.java` **已不存在** —— 该类被共享层 `GoStyleFormValues` + `GoStyleFormValueLimitInterceptor` 取代；判据本身逐条对齐 Go：空值不计数、计数跨 key 共享、body 逐字节 = Go 原文）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| FIX-11 | D | P3 | 否 | Go `go-zero@v1.10.2/zrpc/config.go:30`（`NonBlock bool \`json:",default=true"\`` ⇒ **声明点**，默认 true）＋ `go-zero@v1.10.2/zrpc/client.go:65-66`（**消费点**：`if c.NonBlock { opts = append(opts, WithNonBlock()) }` ⇒ 非阻塞拨号）＋ `go-zero@v1.10.2/zrpc/internal/client.go:78`（`if !cliOpts.NonBlock { options = append(options, grpc.WithBlock()) }` ⇒ NonBlock=true ⇒ **不加** `WithBlock` ⇒ 拨号不阻塞启动）vs RAG `service/article/article-rpc/src/main/resources/application.yml:225`（`security-rpc: address: discovery:///security-rpc` ⇒ devh 客户端**默认非阻塞**拨号 ⇒ 与 Go 的 `WithNonBlock()` 同形）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/UnavailableContentSecurityClient.java:76`（`throw new SecurityAuditException(NOT_INITIALIZED);` ⇒ fail-loud）⇒ **差异已消失**（Go 侧 `NonBlock` 默认值落在**消费点**的效果 = 非阻塞拨号；RAG 侧 devh 同样非阻塞 ⇒ **两侧启动行为一致**，台账 `DRIFT/article/api.md:1966` 记的「条件性差异、当前不可达」经回源复核**前提已消解**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| FIX-13 | C | P2 | 否 | Go `go-zero@v1.10.2/rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20` ⇒ `io.LimitReader` **静默截断**）＋ Go 1.25.11 `src/strconv` 无关；解析器拿到半个文档 ⇒ `http.Error(w, err.Error(), 400)` ⇒ body = `"unexpected EOF\n"` vs RAG `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:1`（`GoStyleJsonBodyLimitFilter` ⇒ **显式**拒绝）⇒ 状态码 400 / `Content-Type: text/plain; charset=utf-8` / `nosniff` / 业务不执行 **四项全同**，**仅 body 文案**不同（Go `"unexpected EOF\n"` vs RAG `"request entity too large\n"`）⇒ **差异仍成立**（文案层，可观察）⇒ ⚠️ **不是签字偏离**（`docs/Intentional_Deviations/article/api.md:26` 明写「仍不是签字偏离 —— 它是 characterization test 锁住实测现状的漂移，不构成『决定不对齐』的签字」）⇒ 修法 = 改成「先读 8 MiB → 解析失败 → 报 `unexpected EOF`」= **故意让请求走到解析器报错**来复刻一条更费解的信息 ⇒ 收益低于代价 ⇒ 归 **C**（阶段二工程化时统一处置文案策略，不阻断阶段一准入） | 2026-10-30 |
| Latin-1 | D | P3 | 否 | Go 1.25.11 `src/strconv/quote.go:522`（`func IsPrint(r rune) bool`）＋ `:524-533`（Latin-1 快路径：`0x20..0x7E` 可打印；**`0xA1..0xFF` 可打印，唯一例外 `0xAD`**（软连字符）；其余 `0xFF` 以下不可打印）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:691`（`private static boolean isGoPrintable(int r)`）＋ `:697-699`（`if (r >= 0xA1 && r <= 0xFF) { return r != 0xAD; }` ⇒ **软连字符是唯一例外**）⇒ **差异已消失**（台账 `DRIFT/article/api.md:2338` 那张「RAG ❌ 不可打印」对照表描述的是**修复前**状态；`DRIFT/article/api.md:2436-2446` 记录的修复已落到共享层 `GoStyleFormValues`（原文写的落点 `GoStyleGetFormValuesFilter.isGoPrintable` **已不存在**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |

### lane W9

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-ARTICLE-API-006 | D（经分析确认不实现 · 可观察契约四项一致、仅 body 文案不同） | P3 | 否 | Go `go-zero@v1.10.2/rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20  // 8MB` ⇒ `io.LimitReader` **静默截断** ⇒ 解析器报 `unexpected EOF` ⇒ 400 + body `"unexpected EOF\n"`）vs RAG `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:94`（`request.getContentLengthLong() > MAX_JSON_BODY_BYTES` ⇒ 显式拒绝）＋ `:96`（`writeGoStyleBadRequest(response, "request entity too large")` ⇒ body = `"request entity too large\n"`）＋ `:112-114`（`text/plain; charset=utf-8` + `nosniff` + 400）⇒ **差异仍成立**（**仅 body 文案**：状态码 / Content-Type / nosniff / 业务不执行 四项两侧一致）⇒ 但**经分析确认不实现**：台账 `docs/DRIFT/article/api.md:705` 已判「✅ 已确认 LOW OBSERVABLE DIFFERENCE，决定不修」＋ characterization test `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiMvcPipelineTest.java:965`（`oversizedJsonBodyIsRejectedAtEightMiB`）锁住实测现状（含文案断言 + 变异验证）⇒ 一条差异 ≠ 一个缺陷 ⇒ 归 D；⚠️ `docs/Intentional_Deviations/article/api.md:26` 明写本条「**仍不是签字偏离**」⇒ 本行的 D **不是**「已签字偏离」，而是「经分析确认 LOW 不修」 | 2026-10-30 |
