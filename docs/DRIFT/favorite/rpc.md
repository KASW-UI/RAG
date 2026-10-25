# DRIFT 台账 · `service/favorite/favorite-rpc`

> ⚠️ **本文件有两轮记录，不要混读**：
> · **§0–§8（2026-10-23）**：并发会话产出的首轮对照 + 落地实现记录 —— **本轮（2026-10-25）未重新验证**，
>   下面每一条的「证据强度」仍按 2026-10-23 的口径读。
> · **§9（2026-10-25）**：`align/favorite` 工作树的**复核补充**，**只追加、不改上文**。
>
> **核对时间：2026-10-23**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/rpc`）
> **本轮性质：实现 + 首轮源码级 Go ↔ RAG 逐条对照**（不是只读审计）。
> ⚠️ **证据强度上限 = 真 PG（Testcontainers）+ 真启动 + 源码逐行对照**；
> **无双侧活体对拍**（Go 侧 `etcd` / Kafka 依赖不可拉起）、**未做变异验证**。
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
>
> ⚠️ **⚠️ 上面那段「证据强度上限 / 未做变异验证」只适用于 §0–§8（2026-10-23 的首轮）** ——
> **§9 之后（2026-10-25 第十一轮）已大幅提高**：
> · **变异验证** ✅ 已做（§11.7 的 M1/M2、§13.x 的 4 例转红等）；
> · **双侧活体对拍** ✅ 已做 **6 项**（错误码契约 / path 形态 / 指标 result 语义 / v2 响应体形状 /
>   **protobuf 消息体 wire 字节** / **DDL 逐列**）⇒ **U7 仍未闭**，但剩下的只是「整域」层面（业务编排与部署）。
> ⇒ **读结论时按「§号越大证据越强」来读**，不要拿 2026-10-23 的上限套全文件。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 落地 | **36 个 Java 文件 / 7,571 行 / 7 个迁移脚本**（V7 为第十轮前新增的时钟源对齐脚本）<br>⚠️ **核对 2026-10-25（第十轮）**：行数首轮记 7,057 ⇒ 现 **7,571**，增量 514 行**全部是各轮的纯注释增量**（可执行逻辑零改动）（本轮前：34 个文件全 0 字节、`pom.xml` 零依赖零插件、6 个 `.sql` 全 0 字节） |
| 测试 | 3 个测试类 / **56 例全绿**（29 语义 + 13 真 PG schema + 14 错误码映射）—— ⚠️ **此行是 2026-10-23 口径** ⇒ **最新见 §25**（favorite-rpc：**14 个测试类 / 85 例**（含 2 skip）） |
| 构建 | `mvn -o -pl service/favorite/favorite-rpc -am install` ⇒ **BUILD SUCCESS** |
| 本轮发现并修复 | **6 处 L2 语义偏差**（§2）+ **2 处实现期真 bug**（§3） |
| 未决 | **7 条**（§5）—— ✅ **2026-10-25 已全部有归属**（3 条已签字 / 3 条已修或已撤销 / **1 条第七轮闭合** / 1 条环境受限）⇒ **最新状态见 §11.6（U1–U7）+ §12.3（U7 进展）+ §13.3（U3 闭合）**<br>🏷 **第七轮新增 2 条候选已签字**（2026-10-25）：DEV-FAV-RPC-006（12 处命名差异）/ -007（RAG 更严的那道 CHECK）⇒ **本域已无 🏷 候选**，见 §13.2<br>✅ **U8（第十五轮新增 · 域内可动的最后一条）已于第十六轮闭合**：create / create_item 的**成功路径** + `ensureUserExists` 五分支 + **落库等价性**全部拿到双侧活体证据（**15 case 全 SAME**）⇒ **§25**（盘点全表 ⇒ §24.1）⇒ **域内「未知项」归零**（只剩 B2 一条**流程项**：V7 上线前的域外写入方人工确认） |
| 第九轮 | 🆕 **注释可核验性审计**（§15）—— 抓出 **3 条「断言对、锚点指错行」的真缺陷**，门禁级发现 **67 → 0**；**零行为改动** |
| 第十轮 | 🆕 **补完 §15.8 记下的 I1 欠账**（§16）—— **153 条**内联断言补上锚点，再抓 **5 条**（4 条行号指错 + 1 条把 Java 事实误记成 Go）；**台账体检**：2 条死指针 + 3 处过期数字 + 4 处裸行号 + **6 处 RAG 侧锚点位移**已修，**6 个 Go 源文件未落点**已补记 ⇒ 新工具 `tools/ledger-lint/favorite/check-ledger-selfcheck.py`（EXIT=0） |
| 第十二轮 | 🆕 **可动项 A + B 落地**（§19）—— 🔴 **权威读 HTTP 面的双侧活体对拍做成了**（新工具 `tools/authority-live-probe/favorite/`，**21 个 case：20 个逐字节一致 + 1 个 SKIP**）⇒ **U7 的局部活体对拍 6 项 → 7 项**；并抓出并**已修 1 条真缺陷**（200 路径少一个尾部换行字节）<br>🆕 **域外写入方盘点机检**（新工具 `tools/outbox-writer-audit/favorite/`）⇒ 本仓 **0 个域外写入方**（EXIT=0）<br>| 第十一轮 | 🆕 **三条候选签字**（§17）—— DEV-FAV-API-002 / RPC-006 / RPC-007 ⇒ **本域已无 🏷 候选**；顺带改正 §0 头部一处**过期断言**（「未做变异验证」只适用 §0–§8）<br>📌 **域内「可动项」清单**（§18）—— 🔴 **权威读 HTTP 面的双侧活体对拍其实做得了**（不需要 etcd/Kafka/Redis/user-rpc/article-rpc）· 🟡 域外写入方盘点（V7 前置）· ⬜ 跨域项收口在 `global` |
| 第十四轮 | 🆕 **api 整域双侧活体对拍**（§22）—— 新探针 `tools/api-full-parity-probe/favorite/`：**32 case · 24 SAME / 1 SAME_CODE / 2 SAME_BODY / 4 SAME_STATUS / 1 OBS ⇒ 0 条新缺陷**；两侧 = **Go 真源**（22 个逐字节复制件 + sha256 校验）与 **RAG 两个 `java -jar` 真进程** + **共用一个 Redis**<br>🔴 **B-1 闭合并推翻第十三轮结论**（grpc-go 未知方法/服务的措辞差异 = **探针自己把 `:path` 拼成双斜杠**）<br>🔴 **2 个 RAG 侧启动阻塞已修**（`favorite-rpc`/`favorite-api` **从未被验证能以真实进程启动**）<br>🆕 **域外写入方盘点机检扩面**（`tools/outbox-writer-audit/favorite/` + `CHECKLIST.md`） |
| 第十六轮 | ✅ **U8 销账 —— create 成功路径双侧活体对拍**（§25）—— gRPC 契约探针加**第二轮**（上游**在场**：探针自带**最小 user-rpc/article-rpc 桩**当**两侧共同上游**）⇒ **15 case 全 SAME**（0 OBS / 0 SAME_CODE）⇒ `ensureUserExists` **五条分支**首次拿到活体证据 + **落库等价性**（`favorite_folder`/`favorite_item`/`favorite_fact_outbox` 三张表投影逐字段相同 ⇒ 「公开修订冻结」被实测执行到）<br>🔴 **1 条注释断言被实测证伪并修正**（`getStatus()!=0` 的后果是 **fail-open 放行**，不是原文写的「误判封禁 5005」）⇒ **产品代码行为零改动**<br>🆕 坑档 **PIT-FAV-RPC-023/024/025**（端口撞号 / 种子显式 id 毒化序列 / bash 3.2 引号）|
| 第十四轮收尾 | 🔴 **探针健壮性加固**（§23）—— 消除「**环境红**」假红：4 个起 PG 的探针**统一**改成 **探 TCP + 180s + 重建重试 + 容器退出即放弃**；新增守门网 `tools/probe-gate/favorite/`（含变异验证）；**零行为改动**、**0 条新漂移** |
| 本台账范围 | **仅 `favorite-rpc`**。`favorite-api` / `favorite-common` 由并发会话负责，不在本表结论内 |

---

## 1. 落地形态（Go Component → Java Behavior，不是文件映射）

| Go 侧 | Java 侧 | 依据 |
|---|---|---|
| `favorite.go`（1 个 main，含 3 个 `cmd/*` 子命令） | **1 个 Spring Boot JVM**（`FavoriteRpcApplication`） | Go 的 `cmd/fact-dispatch` / `cmd/fact-authority` / `cmd/fact-legacy-preflight` **都不注册任何 gRPC method**、共用同一张 PG；RAG 侧 `Dispatcher` / `AuthorityHandler` 本就是纯库 |
| `internal/logic/*` **7 个 logic** | **1 个 `FavoriteDomainService`**（7 个用例） | AGENTS.md §4.1 已锁定（与 users / like 域同形） |
| `model/favorite_fact.go` 的 **3 个事务写** | **独立 Bean `FavoriteFactWriteService`**（3 个 `@Transactional`） | 🔴 `@Transactional` **self-invocation 静默失效**（AGENTS.md §3.3）——必须是另一个 Bean |
| `cmd/fact-dispatch` 的 `Dispatcher` | `FavoriteFactDispatchScheduler`（`@ConditionalOnProperty` 默认关） | Go 的 dispatcher 是**独立进程**；RAG 侧默认不启用 ⇒ 默认零副作用 |
| `cmd/fact-authority` 的 `authority/http.go` | `FavoriteAuthorityEndpointConfig`（`@ConditionalOnProperty` 默认关） | 同上。**wire 形态按 Go 原样**（path 参数），见 §5-U1 |
| `cmd/fact-legacy-preflight` | `FavoriteLegacyPreflightRunner`（`@ConditionalOnProperty` 默认关） | 只读 RR 事务；**退出码语义差异见 §5-U2** |

**🔴 部署后果已显式承认**：Go 的 4 个进程合并成 1 个 JVM ⇒ 事实投递 / 权威读 / 预检的**启用开关变成配置项**，不再是进程边界。

### 事务语义的一处刻意偏离（机制层，非行为层）

Go 的 `FavoriteFactDispatchService` 有 **3 处显式 `tx.Commit()` 之后返回错误**。
⇒ Java 侧用 `TransactionTemplate` **显式 commit/rollback**，**不用 `@Transactional`**：
`@Transactional` 会把 blocked / failed 状态一起回滚，那正是 Go 明确提交的内容。

---

## 2. 🔴 本轮发现并修复的 6 处 L2 语义偏差

全部来自「Go 侧 7 个 logic 的 `GRPCError` 调用点逐条对照」。**每条都配了回归网**（`FavoriteErrorCodeMappingTest`，14 例）。

| # | 位置 | Go 行为 | 修复前 | 修复后 |
|---|---|---|---|---|
| **D1** | `createFavoriteFolder` 重名预查 | `create_favorite_folder_logic.go:82-87` 三分支：命中 ⇒ **5203** / 查不到 ⇒ 继续 / **DB 出错 ⇒ 5007** | 漏掉第三支 ⇒ 异常逃到 gRPC 兜底 ⇒ **5001** | **5007**（`Internal`） |
| **D2** | `createFavorite` 事务内收藏夹消失 | `favorite_fact.go:276-278` 把 `ErrRecordNotFound` **原样 return**，logic 只区分 blocked 与「其它一切」⇒ **5008** | **5007**（误用读路径的码） | **5008**（`Internal`） |
| **D3** | `deleteFavorite` 事务内条目消失 | `favorite_fact.go:306-311` 同上 ⇒ **5008** | **5007** | **5008** |
| **D4** | `deleteFavoriteFolder` 事务内收藏夹消失 | `favorite_fact.go:342-347` 同上 ⇒ **5008** | **5007** | **5008** |
| **D5** | `getFavoritesByFolder` 条目读失败的 `module` label | `get_favorites_by_folder_logic.go:84` ⇒ `ObserveDBError(**itemModule**, …)` | `folderModule` | `itemModule` |
| **D6** | `deleteFavoriteFolder` 级联删除的 `module` label | `delete_favorite_folder_logic.go:88` ⇒ `ObserveDBError(**folderModule**, …)` | `itemModule` | `folderModule` |

> ⚠️ **D5 / D6 的来源是本台账自身的一次错误记录**：初稿把这两处记成「Go 侧 label 写错了，按 Go 原样保留」。
> 逐条拉全 Go 的 **18 处 `ObserveDBError`** 后确认：**Go 侧全部自洽**（label 恒等于被操作对象的模块），
> 错的是 RAG 侧的映射。⇒ 该错误记录已作废，**不要再按「保留 Go 缺陷」的思路改回去**。

> 🔴 **D2 / D3 / D4 的共同机理**：Go 的模型层把「查不到」原样上抛，**logic 层不区分**「读路径的查不到」与
> 「写路径的查不到」。写路径的一切失败 ⇒ `Internal/5008`。
> ⇒ **读路径的 5007 不能渗进写路径** —— 这是本域最容易写错的一类码。

### 顺带钉死的 Go「不优雅」行为（**刻意保留，不是缺陷**）

| 行为 | Go 出处 | 为何保留 |
|---|---|---|
| 事务内归属复核失败 ⇒ **5008**，**不是** `PermissionDenied` | `favorite_fact.go:280-282 / 313-315 / 349-351`（`ErrFavoriteOwnerMismatch` 在 logic 层不被单独翻译） | L2 语义 |
| 同名改名 ⇒ **直接成功、不写库**，`update_time` 不推进 | `update_favorite_folder_name_logic.go:91-95` | L2 语义（对外可观察） |
| DB 读一律 5007 / 写一律 5008（23505 除外） | 全部 7 个 logic | L1 契约 |
| 删除路径 blocked ⇒ `FailedPrecondition/5304`，其它 ⇒ `Internal/5008` | `delete_favorite_logic.go:84-90` | L1 契约 |
| `favorite_item` 无 `update_time` | `data_model.go:18-31`（Go struct 无该字段） | L1 契约 |

---

## 3. 🔴 实现期自查修掉的 2 个真 bug（不是漂移，是 Java 侧的错）

| # | 现象 | 根因 | 修法 | 证据 |
|---|---|---|---|---|
| **B1** | `normalizeTargetType` 把 `İ`（U+0130）映成 `i̇`（U+0069 U+0307） | Java `String.toLowerCase(Locale.ROOT)` 做**完整 Unicode 映射**；Go `strings.ToLower` 做**简单映射**（`İ`→`i`） | 改为逐 code point `Character.toLowerCase(int)` + ASCII 快路径 | 用 `go run` 实测两侧输出对比 |
| **B2** | 投递失败时 outbox 的 blocked / failed 状态被回滚 | `TransactionTemplate` 里在 `markBlocked` / `markFailed` 之后调了 `status.setRollbackOnly()` | 删除该两处 `setRollbackOnly`（**仅 EMPTY 分支保留回滚**） | Go 的 `DispatchFavoriteFactOnce` 是**显式 `tx.Commit()` 后返回错误** |

---

## 4. L1 契约核对结果

| 契约项 | 核对方式 | 结果 |
|---|---|---|
| gRPC 方法与路径 | `favorite.proto` 7 个 RPC / `/favorite.FavoriteService/*` | ✅ 与 Go 逐条一致 |
| 请求 / 响应 schema | 直接复用 `favorite-proto` 生成的 `FavoriteServiceGrpc` | ✅ |
| 业务错误码 24 个 | `favorite-common` 的 `FavoriteErrorCode` 对 `errmsg.go:9-40` | ✅ 1:1（由 `favorite-common` 自己的契约测试锁死） |
| gRPC 码 + description 文案 | `FavoriteGrpcError.grpcError(Status.Code, int)` —— **标准 gRPC 码 + 查表文案**，与 like 域把业务码塞进 `grpc-status` 的做法**不同** | ✅ 原样透传业务层给的 gRPC 码，**不**为躲开熔断失败码改写成 `UNKNOWN` |
| DDL（4 表 + 2 触发器 + CHECK + UNIQUE + 部分索引） | 6 个 Flyway 脚本 + Go 侧 `AutoMigrate` 的 struct tag + 4 个手写 SQL | ✅ 真 PG 逐条校验（`FavoriteSchemaParityTest`，13 例） |
| `uk_user_folder_name (user_id, name)` / `uk_folder_target (folder_id, target_id, target_type)` | Go 的 GORM tag 逐条翻译 | ✅ 与 `data_model.go:8-9 / 21-24` 一致 |
| `target_id` 为 `varchar(255)`（文章雪花 ID > 2^53） | Go `data_model.go:23` | ✅ 真 PG 实存 `9223372036854775807` |
| 业务 ID 一律显式 `Long.toString(id, 10)` | `aggregate_id` / `payload.favorite_id` / `payload.folder_id` / `subject_ref.subject_id` | ✅ 不依赖序列化器默认渲染 |

### 🔴 一处**必须由 article-rpc 配合**才成立的契约（2026-10-23 已核实，**不再是 UNKNOWN**）

`CreateFavorite("article")` 依赖 `GetArticle` 回执里的 `ext_info` 两个 key：

| key | Go 写入方 | RAG 写入方 | 核实结果 |
|---|---|---|---|
| `published_revision_id` | `get_article_logic.go:186` | `ArticlePublicDomainService.java:289` | ✅ **两侧逐字一致** |
| `publication_gap` = `legacy_revision_missing` | `get_article_logic.go:180` | `ArticlePublicDomainService.java:276`（常量 `LEGACY_GAP_KEY/VALUE`） | ✅ **两侧逐字一致** |

且 Go favorite 调的是 `ArticleRpc.GetArticle(ArticleId, IncrView:false, PublicOnly:true)`（`helper.go:133-137`），
RAG 侧 `ArticleRpcClient.getPublicArticle` **固化了同样两个参数**。
⇒ 12 判定的 `FavoriteArticleSnapshotResolver` 能拿到 revision，**不构成 `Unavailable/5001` 死锁**。

---

## 5. ⚠️ 仍未决（7 条）

> ⚠️ **本表的状态已被 §11.6 取代**（2026-10-25 第五轮）：
> U1 / U2 / U6 → **已签字**；U4 → **已修**；U5 → **已撤销**；U3 → **第七轮闭合**（§13.3）；U7 → **环境受限**。
> ⇒ 读本表请以 §11.6 为准，下表保留 2026-10-23 的原始登记以便追溯。

> **纪律**：本表任一条**都没有**「已修」的断言。核对日期一律 2026-10-23。

| # | 项 | 现状 | 待谁拍板 |
|---|---|---|---|
| **U1** | **权威端点的 wire 形态** | 按 Go 原样写成 **path 参数** servlet（`/internal/v1\|v2/favorite/facts/{producer}/{event_id}`），**不是** RAG 通用 `AuthorityHandlerFactory` 的 query 形态。整类 `@ConditionalOnProperty(favorite.fact.authority.enabled=true)` **默认关闭 ⇒ 默认不新增任何 HTTP 端口** | 开启时需给**纯 gRPC 进程**加 web connector ⇒ **部署形态变更**，未拍板 |
| **U2** | **preflight 的退出码语义** | Go 是独立进程 ⇒ 退出码即门禁。Java 进程内**刻意不调 `System.exit`**，改结构化日志（`outcome=succeeded` / `not_reconciled` / `failed` + `error_code`） | CI 需改为**解析日志**判门禁 |
| **U3** | `zrpc.RpcServerConf.Mode` 的取值域 | **不在本仓库**（Go 侧 `etc/favorite.yaml` 只有 `Mode: dev`）⇒ 用 Spring profile `dev` / `test` 映射 | ✅ **第七轮已闭合**（§13.3）—— 取值域 = `dev\|test\|rt\|pre\|pro`，**不需要外部输入** |
| **U4** | **authority lookup 的 3s 超时** | Go 用 `context.WithTimeout(3s)` 约束 DB 查询；Java 侧走 Hikari `connection-timeout` ⇒ **未对齐** | ✅ **2026-10-25 第四轮已修**（见 §10）⇒ 本行保留作历史 |
| **U5** | **`ObserveRPC` 的 `result` 标签** | Go 侧 **21 处**错误返回因未赋值命名返回值 `err`，被记成 **`success`**。Java 侧**按真实结果计数**（成功 `success`、异常 `fail`） | ❌ **已撤销**（2026-10-25 第二轮实测证伪）⇒ 见 §9.3 |
| **U6** | `favorite_request_seconds_counter` 指标名 | Micrometer Counter 会追加 `_total`（Go `client_golang` 不会）⇒ 保留不改 | 与 U5 一并拍板 |
| **U7** | **双侧活体对拍 + 变异验证** | 未做。Go 侧 `etcd` / Kafka 依赖不可拉起 | 需要一套能同时拉起两侧的环境 |

> ⚠️ **本表状态已被 §11.6 取代**（2026-10-25 第五轮）：**U5 已撤销**（前提被实测证伪）、
> **U6 已签字**（= DEV-FAV-RPC-001）⇒ **不要再把 U5 当成「待签字的指标行为变更」**，
> 也**不要**因为 U6 的指标名与 Go 不同就去「修」——它是**已签字的有意偏离**。

---

## 6. L3 行为相关技术语义（保留清单）

| 维度 | 保留内容 |
|---|---|
| **事务** | 业务行与它的 assert / retract 事实**同生共死**（3 个 `@Transactional` 写）；投递路径用 `TransactionTemplate` 显式 commit |
| **锁** | 收藏夹 `SELECT … FOR UPDATE` 是并发创建 / 删除的锁点；删除级联时 `FOR UPDATE ORDER BY favorite_id` 保证**确定性顺序**（否则两个事务各算一半 retract 集合，撞 `UNIQUE(favorite_id, aggregate_version)`） |
| **错误传播** | 模型层哨兵（`NotFound` / `OwnerMismatch` / `FactMigrationBlocked` / `FactUnavailable`）**不自带码翻译**，由 logic 层统一决定 |
| **序列化** | outbox `payload` **刻意保持 `String`**（JCS hash 需要原始字节；Jackson 省略 null 会改变 hash） |
| **幂等** | 唯一键 + `UNIQUE(favorite_id, aggregate_version)` 双保险；`target_type` 小写化后参与 `uk_folder_target` |
| **ID 语义** | 雪花 ID 显式十进制；`target_id` 用 `varchar` 而非 `bigint`（> 2^53） |
| **v1 / v2 事实 schema** | 进程开关只影响**新建** assert；retract 继承该收藏**冻结**的 schema ⇒ 重启后翻转开关不会让旧收藏的撤回事件形状错乱 |
| **Flyway** | `spring.flyway.table: flyway_schema_history_favorite` —— 独立账本（AGENTS.md §6.3 问题 4） |

---

## 7. 验证

| 项 | 命令 | 结果 |
|---|---|---|
| 编译 + 打包 | `mvn -o -pl service/favorite/favorite-rpc -am install` | **BUILD SUCCESS** |
| 全量测试 | `mvn -o -pl service/favorite/favorite-rpc test` | **56 例全绿** |
| ├ 语义单测 | `FavoriteFactSemanticsTest` | **29**（normalize / rfc3339Nano / parseAuthorityId / subjectV1 / 信封构造 / validDelivery 12 条 / JCS） |
| ├ 真 PG schema | `FavoriteSchemaParityTest`（Testcontainers `postgres:16-alpine`） | **13**（4 表 / 2 不可变触发器 / UNIQUE / CHECK / 部分索引 / `timestamptz` / `target_id` varchar / 无 `update_time`） |
| └ 错误码映射 | `FavoriteErrorCodeMappingTest` | **14**（本轮 6 处修复的回归网） |

### 🔴 两个**已修**的实现期缺陷（自查，非漂移）

1. **`normalizeTargetType` 的 `İ` 映射** —— Java `toLowerCase(Locale.ROOT)` 做完整 Unicode 映射，
   Go `strings.ToLower` 做简单映射 ⇒ 已改为逐 code point `Character.toLowerCase(int)`。
2. **`TransactionTemplate` 的 `setRollbackOnly`** —— 初版在 `markBlocked` / `markFailed` 之后调它，
   会回滚 Go 明确 `tx.Commit()` 的状态变更 ⇒ 已删除（**仅 EMPTY 分支保留回滚**）。

### 三个**必须真 PG、不能用 H2** 的理由

plpgsql `BEFORE UPDATE` 触发器 + `IS DISTINCT FROM`、部分索引 `WHERE status IN (0,2)`、
正则 CHECK `source_snapshot_sha256 ~ '^[a-f0-9]{64}$'` —— **H2 一样都不支持**
⇒ 换成 H2 等于「把要验证的东西换没了」。

---

## 8. 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG

# 编译 + 打包
mvn -o -pl service/favorite/favorite-rpc -am install

# 全量测试（56 例）
mvn -o -pl service/favorite/favorite-rpc test

# 单类
mvn -o -pl service/favorite/favorite-rpc test -Dtest='FavoriteSchemaParityTest'   # 真 PG，需 Docker
mvn -o -pl service/favorite/favorite-rpc test -Dtest='FavoriteErrorCodeMappingTest' # 错误码映射
```

> ⚠️ 真 PG 用例带 `@EnabledIfDockerAvailable` ⇒ 无 Docker 时**静默跳过**，不是失败。
> 判定「schema 对齐」是否成立时，**必须确认该类真的跑了 13 例**（不是 0 例 / skipped）。

---

## 9. 2026-10-25 复核补充（`align/favorite` 工作树 · 基线 commit 58e8314）

> ⚠️ **本节只追加，不修改 §0–§8。**
> **本轮的工作范围是 `favorite-common` / `favorite-api` / `favorite-proto`**（此前三者台账为 0 字节）。
> ⇒ **rpc 侧的 §0–§8 结论本轮未重新验证**，下列条目只记「从 api 侧观察到的、与 rpc 相关的间接事实」。

### 9.1 本轮跑通的事实（可复现）

| 项 | 命令 | 结果 |
|---|---|---|
| 四组件编译 + 打包 | `mvn -o -pl service/favorite/{favorite-proto,favorite-common,favorite-rpc,favorite-api} -am install -DskipTests` | **BUILD SUCCESS** |
| 四组件全量测试 | 同上（不带 `-DskipTests`） | **BUILD SUCCESS**；rpc **58 例全绿** |
| **真 PG schema 对照** | `FavoriteSchemaParityTest` | **13 例真跑**（Docker 可用 ⇒ **未被 `@EnabledIfDockerAvailable` 跳过**）⇒ §7 要求的「必须确认真的跑了 13 例」**本轮已满足** |
| 错误码映射回归网 | `FavoriteErrorCodeMappingTest` | **14 例全绿**（§2 的 6 处修复仍然守得住） |
| 保护链装配 | `FavoriteProtectionChainWiringTest` | **2 例全绿**（含 `DRIFT-MSG-V-026`「熔断/降载链必须真的挂到 gRPC server 上」） |

### 9.2 从 api 侧观察到的、与 rpc 相关的两条事实

| # | 事实 | 依据 |
|---|---|---|
| **F1** | **rpc 侧发出的业务错误码，本轮在 api 侧被双侧对拍逐条验过**：Go 的 `GRPCError` 产生的 wire 形态（标准 gRPC 码 + description 文案）与 `BizCodeFromError` 的还原矩阵 **17/17 逐字节一致** ⇒ §2 的 D1–D6（5007/5008/5304 等码）经由 api 侧的还原链路**仍然成立** | 见 [`common.md`](./common.md) §1 与探针 `tools/errmsg-probe/favorite/` |
| **F2** | **rpc 侧的错误分布已实测**：favorite 域 56 个 `GRPCError` 调用点发出 **7 种标准 gRPC 码**（`Internal`×19、`InvalidArgument`×10、`NotFound`×9、`PermissionDenied`×6、`AlreadyExists`×6、`Unavailable`×4、`FailedPrecondition`×2），**一个越界码都没有** ⇒ §4 的「业务码靠文案反查」这一条**前提成立** | 同上（`FavoriteGrpcError` 类注释记载的分布，本轮探针的 `WIRE` 段侧面印证） |

### 9.3 🔁 U1–U7 复核结论（2026-10-25）

> ⚠️ **本轮把 U1/U2/U4/U5/U6 逐条复核了**（源码级 + 可复现探针）；
> **U3 已在第七轮闭合**（§13.3，**不需要外部输入**），**U7 仍是最大缺口**。

| # | 项 | 2026-10-23 的登记 | **2026-10-25 复核结论** | 落点 |
|---|---|---|---|---|
| **U1** | 权威端点的 wire 形态 | 「按 Go 原样写成 path 参数 servlet，默认关」 | ✅ **路径形态本身是对的**，逐条比对通过（见 §10.2）。<br>⚠️ 但**剩余差异比原登记多 4 项**（配置不完整时 Go 起不来 / RAG 照常跑；无启动前 DB 探活；HTTP server 超时值不同；优雅关闭超时不同） | [`../../Intentional_Deviations/favorite/rpc.md`](../../Intentional_Deviations/favorite/rpc.md) **DEV-FAV-RPC-004** |
| **U2** | preflight 的退出码语义 | 「刻意不调 System.exit，改结构化日志」 | ✅ 结论成立，但**原登记漏了两项**：① Go 的 report 是**写到 stdout 的 JSON**，RAG 不产出 JSON；② Go 的 **「未配置」也是 exit 2**（与 blocked 同级），RAG 的「DB 不可用」落 `failed`（对应 exit 1）⇒ 两侧落档不同 | **DEV-FAV-RPC-003** |
| **U3** | `zrpc.RpcServerConf.Mode` 的取值域 | 「不在本仓库；Go 侧 `etc/favorite.yaml` 只有 `Mode: dev`」 | ✅ **第七轮已闭合**（§13.3）—— **原登记的前提有偏差**：`zrpc.RpcServerConf` 自己**并没有** `Mode` 字段，它嵌的是 `service.ServiceConf`，`Mode` 在那里（`options=dev\|test\|rt\|pre\|pro`，默认 `pro`）⇒ **不需要外部输入** | §13.3 |
| **U4** | authority lookup 的 3s 超时 | 「Go 用 `context.WithTimeout(3s)` 约束 DB 查询；Java 侧走 Hikari `connection-timeout` ⇒ **未对齐**」 | ✅ **确认未对齐**，并进一步定位：Go 的 3s 约束的是**查询**（ctx 传进 GORM），RAG 侧连到的是**连接超时** ⇒ **不是同一个量纲**。<br>🔴 **2026-10-25 第四轮已修** ⇒ 见 §10-D1（复用仓库已有的 `DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator`，在调用点绑 3s deadline）。⇒ **DEV-FAV-RPC-005 撤销**（不再是偏离） |
| **U5** | `ObserveRPC` 的 `result` 标签 | 「Go 侧 21 处错误返回因未赋值命名返回值 `err`，被记成 `success`；Java 按真实结果计数 ⇒ 有意的指标行为变更」 | ❌ **撤销 —— 前提被实测证伪**。Go 的 `return nil, GRPCError(...)` **会**赋值命名返回值 `err` ⇒ defer 里 `ObserveRPC` 看到非 nil ⇒ 记 **`fail`** ⇒ **Go 与 RAG 本来就一致，不存在偏离**。<br>（「21」实为 `return nil, GRPCError(...)` 的出现处数，**实测 22**。） | **撤销记录** + 可复现探针 `tools/metrics-probe/favorite/` |
| **U6** | `favorite_request_seconds_counter` 指标名 | 「Micrometer Counter 会追加 `_total`（Go `client_golang` 不会）⇒ 保留不改」 | ✅ **确认成立**。补充：其余三个指标名（`favorite_request_total` / `favorite_ops_total` / `favorite_db_error_total`）**两侧完全一致**（名字已以 `_total` 结尾）⇒ **只有这一个**不一致 | **DEV-FAV-RPC-001** |
| **U7** | 双侧活体对拍 + 变异验证 | 「未做。Go 侧 etcd / Kafka 依赖不可拉起」 | 🔴 **仍是最大缺口**。本轮推进：<br>· **错误码契约层已做成双侧活体对拍**（`tools/errmsg-probe/favorite/`，EXIT=0，108 行逐字节一致）—— 但那属于 `favorite-common`，**不是 rpc 层**。<br>· **指标 result 语义已做成可复现探针**（`tools/metrics-probe/favorite/`，EXIT=0）—— 单侧 Go 语义锚定 + RAG 侧运行时守门，**不是整域双侧对拍**。<br>· ⬜ **rpc 侧整体仍无双侧活体对拍**（Go 侧 `etcd` / Kafka 仍不可拉起） | 留在本节，**未闭环** |

### 9.4 U1 的逐条复核：权威读端点

| Go 行为（`authority/http.go`） | RAG 实现 | 结论 |
|---|---|---|
| `GET /internal/v1/favorite/facts/{producer}/{event_id}`（:32）/ `GET /internal/v2/…`（:63）—— **path 参数** | `FavoriteAuthorityEndpointConfig` 的两个 `ServletRegistrationBean`（`V1_PREFIX + "*"` / `V2_PREFIX + "*"`） | ✅ 形态一致（**不是** `AuthorityHandlerFactory` 的 query 形态） |
| `Cache-Control: no-store`（:33 / :64） | `resp.setHeader("Cache-Control", "no-store")` | ✅ |
| `Authorization` 必须**恰好 1 个** + `Bearer ` 前缀（:34-36） | `Collections.list(req.getHeaders("Authorization"))` + `size() != 1` | ✅ |
| 恒定时间比较（:36 `subtle.ConstantTimeCompare`） | `MessageDigest.isEqual` | ✅ |
| 失败 ⇒ 401 + body `unauthorized`（:37） | `writePlain(401, "unauthorized")` | ✅ |
| **带 query ⇒ 404**（:40-43，**不是** 400） | `getQueryString()` 非空 ⇒ 404 | ✅ |
| 单次 lookup **3s**（:44 / :80） | **未接到 DB 查询** | 🔴 未对齐 ⇒ **U4 / DEV-FAV-RPC-005** |
| `ErrFavoriteFactUnavailable` ⇒ 404（:47-50） | `FactUnavailableException` ⇒ 404 | ✅ |
| 其它 error ⇒ **503** + `source unavailable`（:51-56） | 同 | ✅ |
| 成功 ⇒ 200 + `Content-Type: application/json`（:57-61） | 同 | ✅ |
| v2：reader 不实现 `ReaderV2` ⇒ 404（:75-79） | 本实现**始终**支持 V2（Go 侧的 `model.FavoriteModel` 实现了 V2 ⇒ 该分支实际不可达） | ✅ 等价 |
| `http.Error` 的 text/plain + `X-Content-Type-Options: nosniff` | `writePlain` 显式写两个头 | ✅ |

> ⚠️ **一处未列的细节**：Go 的 `r.PathValue` 走 `http.ServeMux` 的 pattern 匹配，**自带 path 规范化**
> （清理 `.`/`..`、合并 `//`、逐段解码）；RAG 侧是 `getRequestURI()` + **手工切分 + 手工 `URLDecoder.decode`**。
> ⇒ 对含 `%2F` / `//` / `..` 的畸形路径，两侧可能给出不同的 404 判定。
> ⚠️ **2026-10-25 第四轮：已实测** ⇒ **不是「可能」，是确实不同，而且挖出 5 处漂移** ⇒ 见 §10-D2。
> ⇒ 双侧对拍探针 = `tools/authority-path-probe/favorite/`（EXIT=0）。

### 9.5 一条**跨域观察**（不属于 favorite 域的修法，但影响本域）

`service/like/like-api/.../LikeRpcClient.java:43-48` 的注释写着
「⚠️ client 熔断：Go 侧有且默认开，Java 侧**尚未接入**（已登记的已知偏离）」。

⚠️ **实测该断言极可能同样是错的**：`LikeApiApplication` 的 `scanBasePackages` **也含 `com.rag.common`**
⇒ `GoStyleGrpcClientBreakerInterceptor` 同样会**自动注册** ⇒ 像 favorite 域一样，熔断**很可能早已生效**。

⇒ 但 **like 域不是本域的文件**（AGENTS.md §5.3：只写自己域）⇒ **本轮不改、不判**，
仅在此**登记为跨域线索**，交由 like 域 / `global` 收口域处理。
⚠️ 别把它当 favorite 域的漂移去「顺手统一」。

> ✅ **第七轮已把前提核实清楚**（只读，不改 like 的文件）：
> `service/like/like-api/src/main/java/com/rag/like/like/api/LikeApiApplication.java:75-78`
> 的 `scanBasePackages` = `{"com.rag.like.like.api", "com.rag.common"}`
> ⇒ **`com.rag.common` 在内** ⇒ `GoStyleGrpcClientBreakerInterceptor`
> （`service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java`）
> **会被扫到** ⇒ 与 `favorite-api`（`FavoriteApiApplication:36-39`，同样含 `com.rag.common`）**同款**
> ⇒ ⇒ **该注释「Java 侧尚未接入」几乎确定是错的** ⇒ 熔断**很可能早已自动注册**。
> ⚠️ **仍未最终判定**：要断言「熔断真的生效」，还需查 `favorite-api` 侧同款断言是怎么写的
> （它有 `FavoriteApiProtectionWiringTest` 守着）⇒ **该结论应由 like 域自己用同款装配层测试关掉**。
> ⇒ **本域只提供这条线索**，不代 like 域下结论。

---

## 10. 2026-10-25 第四轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `d753572`）

> ⚠️ **本节只追加，不修改 §0–§9。**
> 本轮目标：**修 U4 / DEV-FAV-RPC-005**，并**解决 §4 证据边界里的未知项**。
> ⇒ §0–§8 的结论本轮**仍未重新验证**（那是 2026-10-23 的另一轮）。

### 10.1 🔴 D1（U4 / DEV-FAV-RPC-005）· 权威读的单次 lookup 3s 预算**没有接到 DB 查询**

| | 内容 |
|---|---|
| **Go 行为** | `authority/http.go:44` / `:80`：`ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)` ⇒ **约束的是 DB 查询**（`m.conn.WithContext(ctx)` 把 ctx 带进 GORM）⇒ pg 驱动在 ctx 到点时发 CancelRequest ⇒ 查询在服务端立刻停止 ⇒ `query.Error != nil` ⇒ Go **原样上抛**（`favorite_authority.go:215-217`，**不**包成 `ErrFavoriteFactUnavailable`）⇒ ⇒ **503 "source unavailable"** |
| **RAG 修复前** | `FavoriteAuthorityLookupService` 走 MyBatis-Plus + HikariCP，**没有**把 deadline 接到查询上 ⇒ 慢查询一路顶到 Hikari 的 `connection-timeout`（**连接**超时 ⇒ **量纲不同**）⇒ 响应时长与 Go 不一致 |
| **等价物论证** | ⚠️ **不是** `@Options(timeout=3)`：那是**每条语句**各给 3s，而 Go 的一个 ctx **覆盖整次 lookup**（17 道校验里的多次查询共用）⇒ 6 条慢查询能拖到 18s。<br>⇒ **复用仓库已有的成套机制**（本模块**早已注册**，`FavoriteRpcApplication:79-80`）：<br>· `DeadlineQueryTimeoutInjector`（MyBatis 拦 `StatementHandler.prepare`）—— 按**当前线程**的 `DeadlineState.remainingMillis()` 给每条 SQL 设 `setQueryTimeout` ⇒ pgjdbc 到点发 CancelRequest ⇒ **与服务端语义同一条，且连接保可用**（对比 `socketTimeout`：那是读超时，连接会报废，语义相反）；<br>· `SqlTimeoutStatusTranslator` —— 把 CancelRequest 的 SQLState `57014` 翻成 `DEADLINE_EXCEEDED`。<br>⇒ 本域只需在**调用点**用 `DeadlineState.bindToCurrent(...)` 绑 3s ⇒ 一次 lookup 的多条 SQL **共用一个预算** ⇒ 与 Go 的 ctx 逐字一致 |
| **绑定位置** | **放在 servlet（handler）里**，与 Go 的绑定点（`http.go:44`）一致；⚠️ 下沉到 service 会让 service 的**其它**调用方也被套上这个预算（Go 侧没有这层） |
| **超时值** | **硬编码 3s**，**刻意不做成配置项**：Go 侧是硬编码常量，做成配置就等于允许部署时改掉它 ⇒ 那时两侧行为又不一致了 |
| **修复** | `FavoriteAuthorityEndpointConfig`：调用点包 `DeadlineState.bindToCurrent(now + 3_000)`；`FavoriteAuthorityLookupService` 的 javadoc 补「调用方必须先绑预算」 |
| **回归网** | `FavoriteAuthorityLookupTimeoutTest`（3 例，**真 PG** Testcontainers + **真 MyBatis** + **真 CancelRequest**） |
| **变异证据** | 去掉 `bindToCurrent` ⇒ **1 例转红**，实测 elapsed = **10024ms**（落到了测试安全网设的 PG `statement_timeout=10s`，而不是 3s）⇒ 证明该测试**不是假绿**，且修复**真的**把预算从 10s 压到了 3s |

> ⚠️ **为什么必须断言「耗时」而不能只断言状态码**：
> 「查不到」（404）与「查询超时」（503）在本域是**两条不同的分支**，
> 但**在更早的版本里二者都落 500** ⇒ 只断言状态码区分不出来。
> ⇒ 本测试**同时**断言「状态码 = 503」「body = source unavailable」「elapsed ∈ [2.5s, 8s]」三件事。

> ⚠️ **测试里那条 `statement_timeout=10s` 是安全网，不是被测行为**：
> 它保证「实现被改坏」时查询在 ~10s 后返回（而不是永久阻塞）⇒ 变异运行**不会挂死**；
> 但因为 10s > 3s，**3s 的预算必然先到** ⇒ 它不可能影响判定。

### 10.2 🔴 D2（§9.4 末尾那条 OBS）· path 形态与错误响应：实测出 **5 处**漂移

> ⚠️ **本轮把「未实测 ⇒ 记为 OBS」升级成了双侧活体对拍** ——
> 新增 `tools/authority-path-probe/favorite/`（Go 侧**只用标准库**复刻 `http.ServeMux` +
> `http.Error` / `http.NotFound` / `http.Redirect`，15 行矩阵；`EXIT=0`）。

| # | 漂移 | Go（实测） | RAG 修复前 | 严重度 |
|---|---|---|---|---|
| **1** | **少一段的 path** | **404** page not found | 🔴 **抛 `StringIndexOutOfBoundsException` ⇒ HTTP 500** | **真 bug** |
| **2** | **需要清理的 path**（`//` / `/./` / `/../`） | **301** + `Location` | **404**（无重定向） | L1 |
| **3** | **404 的 body** | `"404 page not found\n"` + `text/plain; charset=utf-8` + `nosniff` | **只写状态码，无 body、无头** | L1 |
| **4** | **401 的 body** | `"unauthorized\n"`（`http.Error` 用 `fmt.Fprintln`） | `"unauthorized"`（**少一个 `\n`**） | 逐字节 |
| **5** | **判定顺序** | mux 层判定（301 / 404）**先于鉴权** ⇒ 「畸形 path + 无 token」是 **404** | 先撞鉴权 ⇒ **401** | L1 |

**根因与修法**

| # | 根因 | 修法 |
|---|---|---|
| 1 | 段数判据写成 `rest.indexOf('/') != rest.lastIndexOf('/')` ⇒ **没有 `/` 时两者都是 -1 ⇒ 判据不成立** ⇒ 继续走 ⇒ `substring(0, -1)` 抛异常 | 改成 `slash < 0 \|\| slash != rest.lastIndexOf('/')` |
| 2 | 手写 servlet **没有 mux 层** | 逐字复刻 Go 的 `net/http.cleanPath`（⚠️ **不能用 `Paths.get(p).normalize()`** —— 那是文件系统语义）+ `escape(path, encodePath)` + `htmlEscape` ⇒ 301 + `Location` |
| 3 | 404 分支只 `setStatus` | 统一走 `writeGoError`（Go 的 `http.Error` 形状：状态码 + `text/plain; charset=utf-8` + `nosniff` + body） |
| 4 | `writePlain` 少写 `\n` | 同上（`http.Error` 用 `fmt.Fprintln` ⇒ body = `msg + "\n"`） |
| 5 | 鉴权在 path 判定之前 | 重排为 **mux 层（清理 → 形状）→ 鉴权 → query → lookup**，与 Go 的 `findHandler → handler` 一致 |

**⚠️ 三条「照抄 Go 而不是照抄正确」的**（都刻意保留）

1. **Go 的 `Location` 会二次转义**：`/…//a%2Fb` ⇒ `Location: /…/a%252Fb`
   （Go 构造 `url.URL{Path: escapedPath}` 时**没设** `RawPath` ⇒ `EscapedPath()` 又把 `%` 转义成 `%25`）。
   ⇒ 看着像 Go 的缺陷，但**偏离它会让客户端跟随到不同路径** ⇒ 照抄并标注。
2. **Go 的 301 body 末尾是**两个** `\n`**（`http.Redirect` 先拼一个 `\n` 再交给 `fmt.Fprintln`）。
3. **Go 的 404 / 401 body 末尾都有一个 `\n`**。

**回归网**：`FavoriteAuthorityPathParityTest`（2 例 = 12 条矩阵 + 1 条「少一段不得抛异常」），
且每条都断言**状态码 + Location + Content-Type + nosniff + body 逐字节**；
末尾 `verifyNoInteractions(lookupService)` 把「这 12 条都到不了业务层」也钉住
⇒ 本测试**不需要 PG、不需要 Spring 上下文** ⇒ 不会因环境问题变绿/变红。

### 10.3 ✅ 未知项闭合：common 的「description 在真实 gRPC 链路上逐字节保真」

`docs/DRIFT/favorite/common.md` §4 记的那条「⚠️ 未做活体对拍」⇒ **本轮闭合**：

- 新增 `FavoriteGrpcErrorWireFidelityTest`（**4 例**，`favorite-common` 模块，**真 in-process gRPC**）：
  ① description 与 `FavoriteErrorMessages.getMsg(码)` **逐字节相同**；
  ② 该 description 经 `bizCodeFromError` **能还原回原业务码**；
  ③ 标准 gRPC 码在链路上保真（不被改写成 `UNKNOWN`）；
  ④ **5304**（唯一只能靠文案反查还原的码）在真链路上也能还原。
- **变异证据**：把 `FavoriteGrpcError.grpcError` 的 `withDescription(...)` 去掉 ⇒ **3 例转红**，
  且错误信息精确展示因果链：`5304 → 1015`、`5007 → 5001`
  ⇒ **证明「文案就是业务码的载体」这条核心机制，也证明该测试不是假绿**。

### 10.4 ✅ 未知项闭合：proto 的「默认值不落 wire」的生成码级证据

`docs/DRIFT/favorite/proto.md` §2 记的那条「⚠️ **未验**（本仓无生成码级证据）」⇒ **本轮闭合**：

- 实测 `favorite-proto/target/generated-sources/protobuf/java/…/FavoriteFolder.java` 的
  `writeTo`：**零值有条件表达式** ⇒
  `if (folderId_ != 0L)` / `if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(name_))`
  ⇒ **零值确实不落 wire** ⇒ 与 Go 侧「struct literal 无条件写零值」在隐式 presence 下**wire 等价**。
- ⚠️ 顺带确认：生成码 extends `GeneratedMessageV3`（**full runtime**），
  而 `protobuf-javalite` 虽在 pom 里也在 classpath 上 ⇒ **实测全仓 10 个 `*-proto` 都是这个模板**
  （`message-proto` 已被验证过不会遮蔽生成码）⇒ **不是 favorite 域的新问题**，不记入漂移条目。

### 10.5 本轮验证

| 项 | 结果 |
|---|---|
| `FavoriteAuthorityLookupTimeoutTest`（真 PG 慢查询） | **3 例全绿**；变异 ⇒ 1 例转红（elapsed 10024ms） |
| `FavoriteAuthorityPathParityTest` | **2 例全绿**（12 条矩阵 + 少一段） |
| `FavoriteGrpcErrorWireFidelityTest`（真 in-process gRPC） | **4 例全绿**；变异 ⇒ 3 例转红 |
| `tools/authority-path-probe/favorite/run.sh` | **EXIT=0**（Go 侧 15 行矩阵 == `expected.txt`；Java 侧断言全绿） |
| `tools/errmsg-probe/favorite/run-parity.sh` | **EXIT=0**（108 行 sha256 一致） |
| `tools/metrics-probe/favorite/run.sh` | **EXIT=0** |

---

## 11. 2026-10-25 第五轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `8cfced5`）

> ⚠️ **本节同样只追加，不修改 §0–§10。**
> 本轮目标：① 落实用户 2026-10-25 的签字决定（7 条全登记）；
> ② 在落实过程中**逐条复核签字前提** ⇒ **查出 1 条前提不成立的偏离（已撤销）**；
> ③ 查出并修复 **1 条新的 L1 wire 漂移**（v2 响应体形状）。

### 11.1 ✅ 签字批次（用户 2026-10-25 拍板）

| 文件 | 条目 | 结论 |
|---|---|---|
| `docs/Intentional_Deviations/favorite/api.md` | DEV-FAV-API-001（401 body 形态） | ✅ **已签字** —— 「保留现状，登记为签字偏离」；⚠️ 签字时一并明确：**follow 域选「Go 逐字」、本域选「三字段 envelope」**，两域做法不一致，但**状态码（401）与业务码（5004）完全一致** ⇒ 按现状签，**不改任何一方** |
| `docs/Intentional_Deviations/favorite/common.md` | DEV-FAV-COMMON-001 / -002 | ✅ **已签字** —— 两条的**豁免依据都是「不可达」**（favorite 域 56 个 `GRPCError` 调用点**没有一个**传 `codes.OK`；实际 error **全部**来自 gRPC stub）⇒ 各自附了「若将来出现该形态，必须先回来重评」的前置动作 |
| `docs/Intentional_Deviations/favorite/rpc.md` | DEV-FAV-RPC-001 / -003 / -004 | ✅ **已签字** —— 各自附了前置动作（迁看板时改指标名 / CI 门禁改解析日志 / 开启开关前补就绪探针） |
| `docs/Intentional_Deviations/favorite/rpc.md` | **DEV-FAV-RPC-002** | ❌ **本轮撤销**（见 §11.4）⇒ **不在签字范围内** |
| `docs/Intentional_Deviations/favorite/api.md` | **DEV-FAV-API-002**（500 兜底 body） | ✅ **已签字**（2026-10-25）—— 附前置动作：客户端若靠 500 的 body 区分成因 ⇒ 告知客户端方（RAG 侧更宽，不是更窄）；「5xx ⇒ 不解析 body」的客户端**两侧都安全** |
| `docs/Intentional_Deviations/favorite/rpc.md` | **DEV-FAV-RPC-006**（12 处命名差异） | ✅ **已签字**（2026-10-25）—— 附前置动作：若统一索引 / 约束名（迁库）⇒ **按名字 DROP / 重建的运维脚本会失效**（部署脚本层） |
| `docs/Intentional_Deviations/favorite/rpc.md` | **DEV-FAV-RPC-007**（RAG 更严的 CHECK） | ✅ **已签字**（2026-10-25）—— 附前置动作：出现**第二个写入方**直接写 `favorite_item` ⇒ 写入方需知道 RAG 多这条约束（23514 拒）；**不需要改 RAG** |

> ⚠️ **共享索引未动**：`docs/Intentional_Deviations/_INDEX.md` 属共享面（AGENTS.md §5.3）⇒ 本轮**不碰**。
> ⇒ **合并后需主会话统一更新**：已签字条数 **49 → 58**
> （新增 **9** 条：API-001 / **API-002** / COMMON-001 / COMMON-002 / RPC-001 / RPC-003 / RPC-004 / **RPC-006** / **RPC-007**）。
> ⚠️ **口径待主会话定**：另有 **2 条已撤销**（DEV-FAV-RPC-002 / -005）—— 共享索引的口径
> 是「一条 = 一个有**独立编号**的签字条目」，**没说撤销怎么算** ⇒ 若计入则 **60**。
> 🔴 **订正**：本行原先写的是「49 → 51」—— **算术错了**（49 + 6 = 55，不是 51）⇒ 已改正；
> 第十轮再补 3 条（API-002 / RPC-006 / RPC-007）⇒ **55 → 58**。
> ⇒ ✅ **favorite 域已无 🏷 候选**（本轮三条候选全部签字）。

### 11.2 🔴 D3 · v2 权威读端点的响应体形状错误（**新发现并已修**）

> 🔴 **这是本轮挖出的 L1 wire 漂移**，此前**没有任何测试覆盖**。

| | 内容 |
|---|---|
| **Go 行为** | `internal/model/favorite_authority.go:195-204` 的 `AuthoritativeFavoriteFactV2` 把 resolve 的结果**压扁**成 v2 形状：`SubjectRef: FavoriteSubjectRefV2{Issuer: fact.SubjectRef.AuthorityID, SubjectID: fact.SubjectRef.SubjectID}`。<br>⇒ `FavoriteSubjectRefV2`（`favorite_fact.go:86-89`）**只有 2 个字段**（`issuer` / `subject_id`）⇒ **v2 的 `subject_ref` 里没有 `tenant_id`**（Go 注释：*"The legacy fixed platform slot is deliberately absent from the v2 EventSpec"*）。 |
| **RAG 修复前** | `FavoriteAuthorityLookupService#authoritativeFactV2` **直接返回 `FactV1`**（三字段：`authority_id` / `tenant_id` / `subject_id`）⇒ **v2 端点把 v1 的形状发给了下游**。 |
| **后果** | 下游 binder（BTW）**按字段名解析** ⇒ 取不到 `issuer`、多出 `tenant_id` ⇒ **解析失败**。<br>⚠️ **为什么此前测不出来**：v1 端点的形状本来就对，v2 端点只是「多了字段」⇒ 断言状态码 / 断言业务码**都区分不出来** ⇒ 必须**逐字段断言响应体**。 |
| **修法** | ① `FavoriteAuthorityFact.toV2(FactV1)` —— 新增纯函数做那一步压扁（`tenant_id` **丢弃**、`authority_id` **改名** `issuer`）；<br>② 🔴 `authoritativeFactV2` 的**返回类型**从 `FactV1` 改成 `Wire.AuthorityFact` ⇒ **编译器级约束**：Go 的 v2 入口返回的**就是**两字段形状，若还返回 `FactV1` 则**编译不过** ⇒ 不靠注释提醒；<br>③ 端点的响应体变量改成 `Object`（v1/v2 是两种不同的 wire 形状，Jackson 按运行时类型序列化）。 |
| **为什么复用 `Wire.AuthorityFact`** | 它与 Go 的 `FavoriteAuthorityFactV2` **逐字段逐 tag 一致**（event / subject_ref / predecessor_event_id(omitempty) / technical_receipt / source_event_hash）⇒ 不另立类型 —— 与本类已复用 `Wire.Event` / `Wire.TechnicalReceipt` 同一理由（`FavoriteAuthorityFact` 的类注释本就写着「只有 `/internal/v2/...` 才可以（用 `Wire.AuthorityFact`）」）。<br>⚠️ `service/common/**` **只读不写**（AGENTS.md §5.3）—— 本轮**只接线，未改 `service/common` 一行**。 |
| **回归网** | `FavoriteAuthorityV2ResponseShapeTest`（**3 例**，**真 PG Testcontainers + 真 MyBatis + 真 servlet**）：<br>① v2 端点：`subject_ref` **恰好** `{issuer, subject_id}`，**无** `tenant_id`，顶层**恰好** 4 个字段；<br>② v1 端点：`subject_ref` **恰好** `{authority_id, tenant_id, subject_id}`；<br>③ 两个端点按**信封 schema** 分域（v1 读 schema=2 ⇒ 404，反之亦然）。 |
| **fixture 的合法性** | 用的是「**信封 schema=2、聚合版本=1**」的 assert 行 ⇒ 这是 Go 的 `favoriteOutboxV2` 真实产出的形态（schema 由 `FavoriteAssertionSchemaResolver` 决定），**不是造出来的怪东西**；且在这条行上 Go 走 `row.AggregateVersion != 2` 分支（favorite_authority.go:225）⇒ **一行即可覆盖 v2 形状**。 |
| **变异证据** | ① **M1**：`toV2` 里 `issuer` 误取 `tenantId` ⇒ **转红**，精确报错 `expected: <rtw.identity> but was: <platform>`；<br>② **M2**：**完整回退修复**（service 返回 `FactV1` + 端点序列化 `FactV1`）⇒ **转红**，精确报出**修复前的真实 wire 形状**：`实际=[authority_id, subject_id, tenant_id]`。⇒ **不是假绿**。 |

### 11.3 🔴 连带修正：`FavoriteRpcMetrics` 的错误「Go: …」断言

`FavoriteRpcMetrics` 的 javadoc ③ 原写：

> 「**两处 `module` label 写错**：`create_favorite_logic.go:89` 与 `get_favorites_by_folder_logic.go:68` 都在 **item** 流程里用了 `folderModule`。本实现**按 Go 原样保留**…」

⇒ 这是 AGENTS.md §3.3.2 点名的**高危形态**（「Go: …」断言本身可能是错的）⇒ **已改正**为「Go 侧是对的，本实现与 Go 一致」，并附行号依据。逐条证据见 §11.4。

### 11.4 🔴 D4 · `DEV-FAV-RPC-002` 的**前提被逐行核对证伪** ⇒ 撤销

| | 内容 |
|---|---|
| **原结论** | 「Go 侧 `create_favorite_logic.go:89` 与 `get_favorites_by_folder_logic.go:68` 都在 **item** 流程里用了 `folderModule` ⇒ **Go 侧 label 写错**，RAG 按 Go 原样保留 ⇒ 属有意偏离。 |
| **证伪** | ① 那两行**不是 item 流程**，而是**收藏夹查询**的失败分支：`create_favorite_logic.go:79` 是 `FindFolderByFolderId` ⇒ 其失败分支 `:89` 记 `folderModule` —— **正确**；`get_favorites_by_folder_logic.go:58` 是 `FindFolderByFolderId` ⇒ 其失败分支 `:68` 记 `folderModule` —— **正确**。<br>② 真正的 item 读失败分支：`get_favorites_by_folder_logic.go:81`（`FindFavoritesByFolderId`）⇒ `:84` 记 `itemModule`；`create_favorite_logic.go:122`（`FindFavoriteByFolderTarget`）⇒ `:130` 记 `itemModule` —— **也都正确**。<br>③ ⇒ **Go 侧 18 处 `ObserveDBError` 的 label 全部自洽**（label 恒等于被操作对象的模块）。 |
| **RAG 侧** | `FavoriteDomainService:657-659` 与 `:376-377` 的注释与实现**同样自洽**（前者明写「上一行的收藏夹读失败才是 folderModule（Go: :68）—— 两者不可混」）⇒ **两侧一致**。 |
| **结论** | 🔴 **从来不是偏离 ⇒ 撤销**，且**不在本轮签字范围内**（签字的是「确认不同」的那几条）。<br>⇒ 原登记出错的原因是**看错了分支**（把「收藏夹查询失败」误读成「item 流程」）⇒ 与本台账 §2 的 D5/D6 是**同一个坑的两次重犯**。 |
| **落点** | [`../../Intentional_Deviations/favorite/rpc.md`](../../Intentional_Deviations/favorite/rpc.md) 的「撤销记录 · DEV-FAV-RPC-002」。 |

### 11.5 📌 口径说明：`ObserveDBError` 的**静态调用点数不等 ≠ 行为差**

> ⚠️ **这一条是「别把静态差异当行为差异」的守门记录** —— 它差点被记成一条新漂移。

| | 内容 |
|---|---|
| **观察到的现象** | Go 侧 `ObserveDBError` 共 **18** 处；RAG 侧 `metrics.observeDbError` 共 **22** 处。按 `(module, action, type)` 三元组做**多重集**比对，4 组不等：`FOLDER/delete/db` 1↔3 · `FOLDER/select/db` 7↔5 · `ITEM/delete/db` 1↔3 · `ITEM/insert/db` 1↔3。 |
| **复现** | `grep -rn "ObserveDBError" <go>/service/favorite/rpc/internal/logic/`（Go 18 处）<br>`grep -rn "observeDbError" service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteDomainService.java`（RAG 22 处） |
| **⚠️ 为什么【不是】漂移** | **Go 的 1 处调用，对应的是 RAG 的 N 个【互斥】catch 块**：<br>· `ITEM/insert/db`：Go 1 处（`create_favorite_logic.go:162`）↔ RAG 3 处（`:297` `OwnerMismatchException` / `:305` `NotFoundException` / `:319` `DataAccessException`）—— 三者是**不同的异常类型** ⇒ **一次失败只会命中其中一个** ⇒ 运行时**至多记 1 次**；<br>· `ITEM/delete/db`：Go 1 处（`delete_favorite_logic.go:88`）↔ RAG 3 处（`:456`/`:463`/`:468`）—— 同上，**互斥**；<br>· `FOLDER/delete/db`：Go 1 处（`delete_favorite_folder_logic.go:88`）↔ RAG 3 处（`:379`/`:386`/`:391`）—— 同上，**互斥**；<br>· `FOLDER/select/db`：Go 7 处 ↔ RAG 5 处 —— RAG 把 Go 的两个独立 select 分支**合并进同一个 catch** ⇒ 运行时仍是**一次失败记一次**。 |
| **结论** | ⇒ **静态调用点数不是行为等价性的判据**；判据是「**同一次失败，两侧各记几个、记成什么**」⇒ 本轮逐组核对后 **4 组全部运行时等价** ⇒ **不记入漂移条目**。 |
| **⚠️ 同源提醒** | `tools/metrics-probe/favorite/README.md` 的「已知边界」写的是「**不覆盖** `ObserveOp` / `ObserveDBError` 的标签取值（那两条两侧都是显式传参，无语言语义差异）」—— **「无语言语义差异」这半句是对的**（两侧都显式传参，不存在 Go 的 defer/命名返回值那类语言层陷阱）；但**它没有回答「标签取值本身两侧是否一致」** ⇒ 本节把这一问补上了（结论：一致）。 |

### 11.6 📌 §5「未决 7 条」的**最新状态**（2026-10-25 第五轮后）

| # | 项 | 2026-10-23 的登记 | **最新状态** | 落点 |
|---|---|---|---|---|
| **U1** | 权威端点的 wire 形态 | path 参数 servlet，默认关 | ✅ **已签字**（部署形态差异 = 进程边界降级为配置项）；✅ 路径形态本身经 §10.2 逐条对拍通过 | **DEV-FAV-RPC-004** |
| **U2** | preflight 的退出码语义 | 改结构化日志 | ✅ **已签字** | **DEV-FAV-RPC-003** |
| **U3** | `zrpc.RpcServerConf.Mode` 取值域 | 不在本仓库 | ✅ **已闭合**（第七轮，§13.3）—— 取值域 = `dev\|test\|rt\|pre\|pro`（默认 `pro`），**不需要外部输入** | §13.3 |
| **U4** | authority lookup 的 3s 超时 | 走 Hikari 连接超时 | ✅ **已修**（第四轮，§10.1）⇒ **DEV-FAV-RPC-005 撤销** | §10.1 |
| **U5** | `ObserveRPC` 的 `result` 标签 | Go 21 处被记成 success | ❌ **已撤销**（第二轮实测证伪，§9.3） | 撤销记录 + `tools/metrics-probe/favorite/` |
| **U6** | `favorite_request_seconds_counter` 指标名 | Micrometer 追加 `_total` | ✅ **已签字**（其余 3 个指标名两侧完全一致） | **DEV-FAV-RPC-001** |
| **U7** | 双侧活体对拍 + 变异验证 | 未做 | 🔴 **仍未闭** —— Go 侧 `etcd` / Kafka 不可拉起（**环境受限**）⇒ 但**已有 6 项局部双侧对拍**：错误码契约（`tools/errmsg-probe/favorite/`）、path 形态（`tools/authority-path-probe/favorite/`）、指标 result 语义（`tools/metrics-probe/favorite/`）、v2 响应体形状、**protobuf 消息体 wire 字节**（`tools/proto-wire-probe/favorite/`）、**DDL 逐列**（`tools/ddl-parity-probe/favorite/`）⇒ **最新见 §13.4** | §11.2 / §13.2 / 各探针 |

### 11.7 本轮验证

| 项 | 结果 |
|---|---|
| `FavoriteAuthorityV2ResponseShapeTest`（**真 PG**，3 例） | **全绿**；变异 M1 ⇒ 转红（`expected: <rtw.identity> but was: <platform>`）；变异 M2（完整回退）⇒ 转红（`实际=[authority_id, subject_id, tenant_id]`） |
| 本域全量测试 | **183 例全绿**（api 84 · rpc **70** · common 29 · proto 0），真 PG 19 例真跑未 skip |
| `tools/authority-path-probe/favorite/run.sh` | **EXIT=0** |
| `tools/errmsg-probe/favorite/run-parity.sh` | **EXIT=0** |
| `tools/metrics-probe/favorite/run.sh` | **EXIT=0** |
| `check-tomcat-limits.py` / `check-flyway-ledger.py` / `check-proto-static.sh` | 全 **EXIT=0** |
| 共享面（`docs/DRIFT/_INDEX.md` / `_MANIFEST.json` / `docs/Intentional_Deviations/_INDEX.md` / `AGENTS.md` / `docs/DRIFT/_cross/`） | **零改动** |

---

## 12. 2026-10-25 第六轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `d660a04`）

> ⚠️ **本节只追加，不修改 §0–§11。**
> 本轮目标：**补 U7 的证据缺口** —— 把「protobuf 消息体的 wire 字节」从**未验**升级为**双侧活体对拍**。

### 12.1 ✅ U7 的第 5 项局部双侧对拍：protobuf 消息体的 wire 字节

| | 内容 |
|---|---|
| **工具** | `tools/proto-wire-probe/favorite/`（**EXIT=0**） |
| **Go 侧** | 复制**真源真正在用的** `service/favorite/rpc/pb/favorite.pb.go`（**逐字节复制 + sha256 校验**）+ `google.golang.org/protobuf` 运行时 |
| **RAG 侧** | `favorite-proto` 的生成码 + `protobuf-java` 运行时 |
| ⚠️ **两侧运行时不同** | protoc-gen-go 的运行时 vs protoc 的 Java 运行时 ⇒ **不是「同一份实现自证」** |
| **冻结期望** | `expected.txt`：**6 组 `WIRE|<name>|<hex>`** —— `folder_full` / `folder_zero` / `item_full` / `item_partial` / `folders_resp_two` / `create_req` |
| **关键结论** | `folder_zero` 是**空字节** ⇒ proto3 标量零值**不落 wire** ⇒ 把 §10.4 的「生成码级证据」升级为**字节级**；<br>`item_partial` 证明**空字符串不落 wire**（那是 JSON 的行为，不是 proto3 的）；<br>`create_req` 证明 `int64` 在 wire 上是 **varint**，**不是**字符串（字符串是 api 层的 JSON 约定）。 |
| **回归网** | `FavoriteProtoWireParityTest`（**6 例**，**住在 `favorite-rpc` 模块**）—— ⚠️ `favorite-proto` 的 pom 里**一行 junit 依赖都没有** ⇒ 放**消费方**（它本就依赖 proto）。⇒ 副作用：proto 组件从「0 例测试」变成「有 1 个字节级守门」。 |

### 12.2 ⚠️ DDL 的**逐列对照**（**源码级**）—— 🔴 **已被 §13 取代**

> 🔴 **本节是源码级推导，结论对、但方法不可靠** ⇒ 第七轮（§13）用**双侧活体对拍**重做，
> **推翻了两处结论** ⇒ **以 §13 为准**。
>
> 被推翻的两处：① 「目标 schema = AutoMigrate ∪ 4 个手写 SQL」**漏了顺序**（顺序会改结果）；
> ② 「4 张表列级零差异」**漏了 `created_at`/`updated_at` 的 DEFAULT**（Go 侧无、RAG 侧有 ⇒ 不是零差异）。
>
> ⚠️ 保留本节是为了留痕「源码级推导能到哪一步、卡在哪一步」。

> 🔴 **为什么此前是个缺口**：`tools/difftest/check-ddl-static.py` 只有 **users / task / message**
> 三个域预设 ⇒ **favorite 不在其中**；而本域的 `FavoriteSchemaParityTest`（13 例真 PG）只**抽查**了
> 3 项（`target_id` 是 varchar / 时间列是 timestamptz / `favorite_item` 无 `update_time`）
> ⇒ **其余列的类型 / 长度 / 可空性 / DEFAULT 从未被逐列比过**。

**先确立「Go 侧的真源 schema 是什么」**（这一步本身就是结论）：

| Go 侧 | 内容 |
|---|---|
| `internal/model/db.go:33` | `db.AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})` —— **只跑 GORM AutoMigrate** |
| 🔴 **`.sql` 文件不由程序执行** | 全仓 grep：**没有任何 Go 代码执行这 4 个 `.sql`** ⇒ 它们是**运维手工执行**的（`DELIVERY_ACCEPTANCE.md:7`：「先在现有 Favorite PostgreSQL 库执行 `001_favorite_fact_outbox.sql`，再执行 `002_favorite_fact_delivery.sql`」）⇒ **目标 schema = AutoMigrate ∪ 4 个手写 SQL** |
| ⇒ | RAG 的 `V1–V6` 正是这个**并集** ⇒ 结构上对齐 ✅ |

**逐列比对结果（4 张表）⇒ 列级零差异**：

| 表 | 比对依据 | 结果 |
|---|---|---|
| `favorite_folder` | Go `data_model.go:5-16` + GORM 类型推断链 | ✅ **6 列全一致**（`id bigserial` / `folder_id bigint NN` / `user_id bigint NN` / `name varchar(100) NN` / `create_time timestamptz` / `update_time timestamptz`） |
| `favorite_item` | Go `data_model.go:18-35` + `003` | ✅ **10 列全一致**（含 `target_revision varchar(96)` 可空 / `title varchar(255)` **可空** / `cover varchar(500)` **可空** / **无 `update_time`**） |
| `favorite_fact_outbox` | Go `001` + `002` + `favorite_fact.go:60-76` | ✅ **13 列全一致**（含 `payload jsonb NN` / `status smallint NN DEFAULT 0` / `retry_count integer NN DEFAULT 0` / `technical_input_hash char(64)` / `created_at timestamptz NN DEFAULT now()`） |
| `favorite_legacy_fact_marker` | Go `004`（**AutoMigrate 不覆盖此表**） | ✅ **9 列全一致**（含 `target_type text` / `target_id text` / `source_snapshot_sha256 char(64)` + 正则 CHECK / `approved_at timestamptz NN DEFAULT now()`） |

**类型推断链（不靠猜，逐条有出处）**：

| Go 侧写法 | GORM 产出 | 出处 |
|---|---|---|
| `Id uint64` + `primaryKey` | **`bigserial`** | `gorm.io/gorm@v1.31.1/schema/schema.go:336-347`（`PrioritizedPrimaryField` 且 `GORMDataType == Uint` ⇒ 推断 `AutoIncrement = true`）→ `gorm.io/driver/postgres@v1.6.0/postgres.go:216-227`（`size++` ⇒ 65 > 32 ⇒ `bigserial`） |
| `int64` / `int32` | `bigint` / `integer` | 同上 `:229-236` |
| `type:varchar(N)` | `varchar(N)` | 同上 `:246-250` |
| `time.Time` | `timestamptz` | 同上 `:251-255` |
| `string` **无** `type:` | `text` | 同上 `:247-250`（`Size == 0` ⇒ 回落 `text`）⇒ **对 `favorite_legacy_fact_marker` 而言，实际来源是 `004` 里显式写的 `text`**（该表**不在** AutoMigrate 名单里 ⇒ GORM 推断对它**不生效**）⇒ 但两边**恰好一致**（若该表哪天进了 AutoMigrate，GORM 也会给 `text`）⇒ 记为一致，不是巧合需要解释。 |

**唯一差异（表达层，行为等价）** —— **GORM 自动生成的唯一索引名**：

| 索引 | Go / GORM | RAG |
|---|---|---|
| `favorite_folder.folder_id` 唯一索引 | **`idx_favorite_folder_folder_id`** | `uk_favorite_folder_folder_id` |
| `favorite_item.favorite_id` 唯一索引 | **`idx_favorite_item_favorite_id`** | `uk_favorite_item_favorite_id` |

⇒ 依据 `gorm.io/gorm@v1.31.1/schema/index.go:119-134`（未命名的 `uniqueIndex` 走
`namer.IndexName(table, column)` ⇒ `idx_<table>_<column>`，**与是否 UNIQUE 无关**）
⇒ **列集合相同、唯一性相同、约束语义相同 ⇒ 行为等价**，仅**标识符**不同。
⇒ 已登记：`docs/Intentional_Deviations/favorite/rpc.md` 的 **DEV-FAV-RPC-006**（✅ **2026-10-25 已签字**）。

> ⚠️ **为什么这条必须登记、不能默默「统一」**：两边名字不同 ⇒ **统一到任何一边都是行为变化**
> （PG 目录里索引名变了；23505 的错误信息里带的约束名也变了）⇒
> 而 RAG 侧**按 SQLSTATE 23505** 翻译错误码（不看约束名）⇒ 错误码**不受影响** ⇒ 属纯命名层。

> ⚠️ **本节的方法边界**：这是**源码 + 驱动源码级**的推导（把 GORM 的类型推断链逐条读出来），
> **不是**「两侧同时跑真 PG 逐列对拍」⇒ **证据强度 = 源码级（但推导链有出处）**。
> ⇒ **要升级为活体对拍**，需要「真 Go 进程 + 真 PG 拉起 AutoMigrate」⇒ 与 §12.3 的环境受限同源。

### 12.3 U7 状态更新

| | 2026-10-25 第五轮后 | **第六轮后** |
|---|---|---|
| **rpc 侧整域双侧活体对拍** | 🔴 未闭（Go 侧 `etcd` / Kafka 不可拉起） | 🔴 **仍未闭**（**环境受限**，非方法缺陷）⇒ ⚠️ **第十三轮已部分推翻**：见 §20.6（**不需要 etcd**）与 §20.7（局部活体对拍 6 项 → **9 项**） |
| **已做的局部双侧对拍** | **4 项**：错误码契约 / path 形态 / 指标 result 语义 / v2 响应体形状 | **5 项**：＋ **protobuf 消息体 wire 字节** ⇒ ⚠️ **第十三轮 ⇒ 9 项**（＋ gRPC **status code / description / 消息字节 / metadata key 集合**，见 §20.3） |
| **DDL 逐列对照** | 🔴 未做（`check-ddl-static.py` 无 favorite 预设） ⇒ ✅ **活体对拍已完成**（第七轮，§13.2）⇒ **本行已被 §13.2 取代** |
| **仍未闭的** | — | **帧层**（HTTP2 / length-prefix / gRPC status 帧）⇒ 需两侧同时起服务 ⇒ ⚠️ **第十三轮已重新评估并拆成三层**（见 §20.1）：L0 不做比对（库级）、**L1 已双侧活体对拍**、L2 早已闭 |

### 12.4 本轮验证

| 项 | 结果 |
|---|---|
| 本域全量测试 | **194 例全绿**（api **89** · rpc **76** · common 29 · proto 0），真 PG 19 例真跑未 skip（**此数为第六轮口径** ⇒ 第七轮见 §13.5） |
| 探针 | api-envelope / proto-wire / authority-path / errmsg / metrics ⇒ **5 个全 EXIT=0** |
| 守门 | `check-tomcat-limits.py` / `check-flyway-ledger.py` / `check-proto-static.sh` ⇒ 全 **EXIT=0** |
| 共享面 + `service/common/**` | **零改动** |

---

## 13. 2026-10-25 第七轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `347d338`）

> ⚠️ **本节只追加，不修改 §0–§12。**
> 本轮目标：**把 §12.2 的「源码级 DDL 对照」升级为「双侧活体对拍」** ⇒ 并**据此修掉 1 条真漂移**。

### 13.1 🔴 D5（L1 漂移）· outbox 时间列的**时钟源**与 `updated_at` 刷新（**已修**）

| | 内容 |
|---|---|
| **Go 行为** | `internal/model/favorite_fact.go:158-159` 的 struct 字面量<br>`FavoriteFactOutbox{EventID:…, FavoriteID:…, AggregateVersion:…, Payload:…, Status:…}`<br>⇒ **没有**设 `CreatedAt` / `UpdatedAt` ⇒ 靠 tag `gorm:"autoCreateTime;not null"` / `gorm:"autoUpdateTime;not null"`<br>⇒ **GORM 在应用层赋值**（`gorm.io/gorm@v1.31.1/callbacks/create.go:302-306` 的 `field.AutoCreateTime > 0` 分支）⇒ 值来自 **Go 进程时钟**。<br>⇒ 且 **每次 UPDATE 都会刷新 `updated_at`**（`favorite_delivery.go:156-157 / :176 / :189` 三处 `tx.Model(...).Update(...)` 都走 GORM 的 Update 回调 ⇒ `autoUpdateTime` 生效）。 |
| **RAG 修复前** | ① `insertFact` **不绑** `created_at` / `updated_at` ⇒ 落到 DDL 的 `DEFAULT now()` ⇒ 值来自 **PG 服务器时钟**；<br>② `markBlocked` / `markFailed` **根本不写 `updated_at`** ⇒ 行的 `updated_at` 永远停在插入时刻；<br>③ `markAccepted` 写 `updated_at = now()` ⇒ 同样是 **PG 服务器时钟**。 |
| **为什么是行为差异（不是 DDL 差异）** | `created_at` 是取件查询的**排序键**（`ORDER BY created_at, event_id`，`favorite_delivery.go:141-147`）⇒ 应用进程与数据库之间的时钟偏移会让**取件顺序**产生分歧 ⇒ **投递顺序**分叉。<br>⇒ `updated_at` 不刷新 ⇒ 行看不出「被处理过」⇒ 运维按 `updated_at` 判断新鲜度会**读到错值**。 |
| **修法** | ① `insertFact` / `markBlocked` / `markFailed` / `markAccepted` **全部显式绑定应用层时钟**（`OffsetDateTime`，UTC 渲染 ⇒ 不依赖 JVM 默认时区）；<br>② 新增 `V7__align_favorite_outbox_time_columns_with_go.sql` ⇒ **去掉两列的 `DEFAULT now()`**。<br>⚠️ **为什么必须去掉 DEFAULT**：Go 侧无 DEFAULT ⇒ 漏绑会**硬失败**（NOT NULL 违例）；RAG 侧若留着 ⇒ 同一处遗漏被**静默兜底**成 DB 时钟 ⇒ 两边分叉且**无告警**。去掉之后 ⇒ 两侧**同样硬失败** ⇒ 遗漏立刻暴露。<br>⚠️ 去掉 DEFAULT 的机理（已读源码核实）：`gorm.io/gorm@v1.31.1/migrator/migrator.go:543-550` 的「default value → null」分支 ⇒ **GORM 的 AutoMigrate 会主动删掉已存在的 DEFAULT** ⇒ Go 侧 live schema **确定没有** DEFAULT。 |
| **回归网** | `FavoriteOutboxTimeColumnTest`（**5 例**，**真 PG** Testcontainers + **真 Flyway** + **真 mapper**）：<br>① INSERT 的两列**精确等于**应用层传入的时刻（并反证「不等于 DB 的 now()」）；<br>② `markBlocked` / `markFailed` / `markAccepted` 后 `updated_at` **被推进**；<br>③ `information_schema` 里两列**没有 DEFAULT**。 |
| **变异证据** | ① **M1**：`insertFact` 去掉时间列绑定 ⇒ **转红**，精确报出 `expected: <2026-01-02T03:04:05.123456Z> but was: <2026-10-24T21:43:19.435344Z>`（= DB 的 now()）；<br>② **M2**：`markBlocked` 去掉 `updated_at` ⇒ **转红**；<br>③ **M3**：`V7` 的 `DROP DEFAULT` 注释掉 ⇒ **2 例转红**（`created_at=now()` 仍带 DEFAULT ＋ `FavoriteDdlParityTest` 的 MAIN 指纹不符）。⇒ **4 例转红 ⇒ 不是假绿**。 |

### 13.2 🔴 DDL 的**双侧活体对拍**（新增探针 `tools/ddl-parity-probe/favorite/`）

| | 内容 |
|---|---|
| **新工具** | `tools/ddl-parity-probe/favorite/`（**EXIT=0**）<br>· **Go 侧**：**真源** `service/favorite/rpc/internal/model/`（**8 个 `.go` + 4 个 `.sql` 逐字节复制 + sha256 校验**）+ **真源** `InitDB()`（AutoMigrate + `migrateFavoriteTargetIDToString`）+ **整体继承真源 `go.mod`/`go.sum`**（gorm v1.31.1 / driver-postgres v1.6.0）<br>· **RAG 侧**：**真 Flyway** 迁移脚本 `V1..V7`<br>· 两侧**共用同一份 dump 查询**（`sql/dump.sql` + `sql/names.sql`）⇒ 不会「各说一半」 |
| **判定口径** | dump 输出**语义行**（`S\|…`，不含对象名）与**命名行**（`N\|…`）：<br>· 语义不一致 ⇒ 🔴 **真漂移**（exit 1）<br>· 名字不同但语义相同 ⇒ **命名层差异** ⇒ 进 OBS 段冻结<br>· `expected.txt` = `MAIN\|<sha256>`（两侧共同语义内容指纹）+ **13 条 OBS** |
| **结论** | **4 张表、38 列、全部约束 / 索引 / 触发器 / 函数体**：<br>✅ **列级（类型 / 长度 / 可空性 / DEFAULT / 顺序）零差异**（修完 D5 之后）<br>⚠️ **12 处命名差异**（约束 9 + 索引 3）⇒ 已登记 **DEV-FAV-RPC-006**<br>⚠️ **1 处语义差异**（RAG 侧 `favorite_item` 多一道 CHECK）⇒ 已登记 **DEV-FAV-RPC-007** |
| **🔴 顺带查明：执行顺序是行为的一部分** | 探针同时跑**两种顺序**：<br>· **sql-first**（运维先跑 `001..004`、应用后起）⇒ **判定基准**（真源文档 `DELIVERY_ACCEPTANCE.md:7` 指定的顺序）<br>· **app-first**（应用先起、运维后跑）⇒ `001` 的 `CREATE TABLE IF NOT EXISTS` 变成 **no-op**<br>⇒ **两者产出的 schema 不同**，差两处：<br>① `CHECK (aggregate_version IN (1,2))` —— 它在 Go 的 `001` 里是**内联**在 CREATE TABLE 里的 ⇒ no-op ⇒ **建不出来**；<br>② `favorite_fact_outbox` 的**列顺序**不同（AutoMigrate 按 struct 序，`001` 按 DDL 序）。<br>⇒ ⚠️ **但 `001` 里的部分索引 `favorite_fact_outbox_pending` 是独立语句 ⇒ 照样建出来** —— 这一点是**实测纠正**的（源码级推理曾猜错）。<br>⇒ **RAG 侧与 sql-first 一致** ⇒ 与真源文档指定的部署顺序一致 ✅ |

### 13.3 ✅ U3 闭合：`zrpc.RpcServerConf.Mode` 的取值域（**不需要外部输入**）

| | 内容 |
|---|---|
| **原登记** | 「`zrpc.RpcServerConf.Mode` 的取值域」不在本仓库 ⇒ **需 Go 侧提供清单** ⇒ 标「待外部输入」。 |
| **🔴 实测：原登记的前提有偏差** | go-zero v1.10.2 的 `zrpc.RpcServerConf`（`zrpc/config.go:37-54`）**自己并没有 `Mode` 字段** ⇒ 它嵌的是 `service.ServiceConf`，`Mode` 在**那里**（`core/service/serviceconf.go`）：<br>`Mode string \`json:",default=pro,options=dev\|test\|rt\|pre\|pro"\`` ⇒ **取值域 = `dev` / `test` / `rt` / `pre` / `pro`（5 个），默认 `pro`**。 |
| **消费点（全部）** | ① `core/service/serviceconf.go:83-90` `initMode()`：`dev\|test\|rt\|pre` ⇒ `load.Disable()`（关分布式追踪采样）+ `stat.SetReporter(nil)`（关指标上报）；`pro` ⇒ 全开。<br>② `service/favorite/rpc/favorite.go:31`：`SubjectRefV2Facts` 为 true 时，Mode **必须**是 `dev` 或 `test`，否则 `os.Exit(2)`（`LOCAL_CANDIDATE_ONLY`）⇒ **这是唯一会让进程起不来的 Mode 约束**。<br>③ `favorite.go:45`：`Mode == dev \|\| Mode == test` ⇒ 注册 gRPC **reflection**（生产不注册）。 |
| **⇒ favorite 域实际只需 3 组** | `dev\|test`（+ reflection + 允许 v2）/ `rt\|pre`（initMode 关 load+stat，但无 reflection、v2 被拒）/ `pro`（默认）。 |
| **RAG 侧现状** | `FavoriteSubjectRefGate`（`DEV_TEST_PROFILES = ["dev","test"]`）⇒ 与 Go 的 `favorite.go:31` / `:45` **一致** ✅；`rt` / `pre` 在 favorite 域**无行为差异**（除 load/stat 上报，属框架层）。<br>⚠️ **默认值也一致**：Go 的 `Mode` 省略时是 `pro`；RAG 的 `getActiveProfiles()` 为空 ⇒ `devOrTest=false` ⇒ 等价于 `pro` ✅。 |
| **结论** | ✅ **U3 闭合**（从「待外部输入」变成「已查清」）⇒ **不需要 Go 侧再给清单**。<br>⇒ 唯一残留是 `initMode` 的 **stat/load 上报差异**，但它**只影响非 pro 环境**且属**框架层**（go-zero 的 stat vs Micrometer）⇒ **不记入漂移条目**。 |

### 13.4 ✅ 帧层 / 整域对拍的**边界重核**（把「环境受限」拆成「该做」与「不该做」）

| 项 | 判定 | 理由 |
|---|---|---|
| **gRPC 帧层字节对拍**（HTTP2 / length-prefix / status 帧） | ⬜ **不属本域可做** ⇒ **不记入漂移条目** | 帧层编码由 **gRPC 库**决定（grpc-go vs grpc-java），**不是 favorite 域的行为**；两侧都用各自的标准 gRPC 实现 ⇒ 若有差异，那是**库级**差异，**不是 RAG 重构引入的** ⇒ 不在本域 scope（AGENTS.md §1）。<br>⇒ 真要对，只能测「两个 gRPC 实现之间的帧层差」⇒ 与「RAG 是否忠实复刻 Go」**无关** ⇒ 收益低。<br>🔴 **第十三轮已修订本行**（原判定**方向对但把三层一起打包扔掉了**）⇒ 见 **§20.1**：L0（帧原始字节）仍**不做**（已量化到行号）；**L1（status code / description / 消息字节 / metadata key）本域该管** ⇒ **已双侧活体对拍**（§20.3）。 |
| **rpc 整域双侧活体对拍**（U7 主项） | 🔴 **仍环境受限**（**真受阻**，非方法缺陷） | Go 侧 favorite-rpc 真进程依赖 `etcd` + Redis + `article-rpc` / `user-rpc` ⇒ 本机拉不起。<br>⇒ **但**已被拆成 **6 项局部双侧对拍**：错误码契约 / path 形态 / 指标 result 语义 / v2 响应体形状 / **protobuf 消息体 wire 字节** / **DDL 逐列** ⇒ 剩下的「整域」只覆盖**业务编排与部署**层面。 |
| **api 整域双侧活体对拍** | 🔴 **仍环境受限** | 真 Go favorite-api 依赖 `etcd` + favorite-rpc + Redis ⇒ 起不来；⇒ **响应字节层**已用 `tools/api-envelope-probe/favorite/` 拆出来闭合（§2.2）。 |
| **U3 Mode 取值域** | ✅ **本轮闭合**（§13.3） | 原登记写「需 Go 侧提供清单」⇒ **其实不需要**：go-zero 源码里就有（`options=dev\|test\|rt\|pre\|pro`）⇒ **登记类事项可以自查的，就不该挂着等人**。 |

> 📌 **一条元教训**：这一轮里 **U3 挂了好几轮「等外部输入」**，但真相是**去 go-zero 源码里读一眼就有**
> ⇒ 「需要外部输入」这个判断本身**也需要证据**（AGENTS.md §3.3.1 的同一条纪律）。
> ⇒ **先自查，再登记为未知项**。

### 13.5 本轮验证

| 项 | 结果 |
|---|---|
| `tools/ddl-parity-probe/favorite/run.sh` | **EXIT=0**（MAIN 指纹一致 + **13 条 OBS** 逐条一致） |
| `FavoriteDdlParityTest`（**RAG 侧守门**，进 `mvn test`） | **1 例全绿**；变异（V7 不 DROP DEFAULT）⇒ **转红**（MAIN 指纹不符） |
| `FavoriteOutboxTimeColumnTest`（真 PG + 真 mapper） | **5 例全绿**；变异 ⇒ **4 例转红**（含精确报出 `created_at` 变成了 DB 的 `now()`） |
| 本域全量测试 | **200 例全绿**（api **89** · rpc **82** · common 29 · proto 0），**21 个测试类**，真 PG 25 例真跑未 skip |
| 六个探针 | api-envelope / proto-wire / authority-path / errmsg / metrics / **ddl-parity** ⇒ **全 EXIT=0** |
| 三个守门 | `check-tomcat-limits.py` / `check-flyway-ledger.py` / `check-proto-static.sh` ⇒ 全 **EXIT=0** |
| 共享面 + `service/common/**` | **零改动** |

### 13.6 ⚠️ V7 是**破坏性 DDL 变更** ⇒ 外部写入方必须同步适配

> 🔴 **V7 去掉了 `created_at` / `updated_at` 的 `DEFAULT now()`** ⇒ **任何直接写
> `favorite_fact_outbox` 的代码，若没绑这两列 ⇒ 立刻 `NOT NULL` 违例**。
> ⇒ 本轮**实测到这个后果**：三个既有测试类里的 **6 处裸 INSERT** 全部当场变红
> （`null value in column "created_at" … violates not-null constraint`）
> ⇒ **已全部改成显式绑定**（绑**固定的应用层时刻** `2026-01-01T00:00:00Z`）⇒ 复跑全绿。
>
> ⇒ 这**正是 V7 的设计意图**（让漏绑**硬失败**而不是被静默兜底）⇒ 但也意味着：
>
> | 谁 | 状态 |
> |---|---|
> | 本域生产代码（唯一写入方 `insertFact`） | ✅ 已显式绑定 |
> | 本域测试的裸 INSERT（6 处） | ✅ 已改绑 |
> | **域外**的直接写入方（人工 SQL / 报表 / 另一个服务 / 数据修复脚本） | ⚠️ **未盘点** ⇒ 上线前**必须**确认没有这类写入方 ⇒ 否则会**突然报错** |
>
> ⇒ **登记的前置动作**：**部署 favorite-rpc 的 V7 之前**，先确认「除 `insertFact` 外没有别的写入方
> 直接写 `favorite_fact_outbox`」⇒ 有的话，先改它们，再上 V7。

---

## 14. 2026-10-25 第八轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `7ab9f91`）

> ⚠️ **本节只追加，不修改 §0–§13。**
> 本轮目标：**把「首轮（2026-10-23）产出的结论」做一次机器化复核** ——
> 因为 §0–§8 的头部一直写着「**本轮未重新验证**」⇒ 那是本域最长的一个悬空点。

### 14.1 ✅ rpc 侧：56 个 `GRPCError` 调用点的 **(gRPC 码, 业务码)** 逐条复核

**方法**（机器可复现）：

| 侧 | 提取方式 |
|---|---|
| Go | `grep` 出 `internal/logic/*.go` 里全部 `GRPCError(codes.X, favoritecommon.Y)` ⇒ **56 处** |
| RAG | `grep` 出 `FavoriteExceptions.*Factory(` 调用点 ⇒ 再从 `FavoriteExceptions` 的**工厂方法体**读出它产出的 `(Status.Code, bizCode)` |

**两侧的 16 种 (gRPC 码, 业务码) 组合 ⇒ 逐条一一同构**：

| (gRPC 码, 业务码) | Go 处数 | RAG 工厂 | RAG 处数 | 判定 |
|---|---|---|---|---|
| `InvalidArgument` / `ErrorInvalidParam`(5003) | 7 | `invalidParam()` | 7 | ✅ |
| `InvalidArgument` / `ErrorFavoriteFolderNameEmpty`(5202) | 2 | `folderNameEmpty()` | 2 | ✅ |
| `InvalidArgument` / `ErrorFavoriteTargetEmpty`(5303) | 1 | `favoriteTargetEmpty()` | 1 | ✅ |
| `NotFound` / `ErrorFavoriteFolderNotFound`(5201) | 4 | `folderNotFound()` | 4 | ✅ |
| `NotFound` / `ErrorFavoriteNotFound`(5301) | 1 | `favoriteNotFound()` | 1 | ✅ |
| `NotFound` / `ErrorUserNotFound`(5101) | 2 | `userNotFound()` | 2 | ✅ |
| `NotFound` / `ErrorNotFound`(5002) | 2 | `articleNotFound()` | 2 | ✅ |
| `AlreadyExists` / `ErrorFavoriteFolderNameExists`(5203) | 4 | `folderNameExists()` | 4 | ✅ |
| `AlreadyExists` / `ErrorFavoriteAlreadyExist`(5302) | 2 | `favoriteAlreadyExist()` | 2 | ✅ |
| `PermissionDenied` / `ErrorForbidden`(5005) | 6 | `forbidden()` | 6 | ✅ |
| `Unavailable` / `ErrorServerCommon`(5001) | 4 | `unavailableServerCommon()` | — | ✅（**刻意**与 `Internal` 分开） |
| `Internal` / `ErrorServerCommon`(5001) | 2 | `internalServerCommon()` | — | ✅ |
| `Internal` / `ErrorDbSelect`(5007) | 10 | `dbSelect()` | — | ✅ |
| `Internal` / `ErrorDbUpdate`(5008) | 5 | `dbUpdate()` | — | ✅ |
| `Internal` / `ErrorGenerateID`(5009) | 2 | `generateId()` | — | ✅ |
| `FailedPrecondition` / `ErrorFavoriteHistoryBlocked`(5304) | 2 | `historyBlocked()` | 2 | ✅ |

⇒ **`ErrorServerCommon` 同时用 `Unavailable` 与 `Internal`**（Go 4+2）这条最容易被"统一"掉的规则，
RAG 侧**也**拆成两个工厂 ⇒ 一致 ✅。

### 14.2 📌 四组「调用点数不等」的**逐条归因**（**全部等价，零漂移**）

机器比对时出现 4 组不等，逐条查清后**全部是统计口径，不是行为差**：

| # | 组 | Go / RAG | 归因 | 证据 |
|---|---|---|---|---|
| 1 | `Unavailable/ErrorServerCommon` | 4 / 7 | RAG 侧把 Go `helper.go` 里 `ensureUserExists` / `resolveArticleSnapshot` 的失败**合并**成同一个工厂 | Go 侧 `helper.go` 的 4 处里 3 处是 `resolveArticleSnapshot` |
| 2 | `Internal/ErrorDbSelect` | 10 / 8 | RAG 把「收藏夹读」抽成私有方法 `requireOwnedFolder`（内含 1 处 `dbSelect`）⇒ 按方法归位后**两处都在**（`FavoriteDomainService:642` 收藏夹读 + `:655` item 读） | Go `get_favorites_by_folder_logic.go:71`（收藏夹读）+ `:87`（item 读） |
| 3 | `Internal/ErrorDbUpdate` | 5 / 11 | RAG 的**互斥 catch 块**（`OwnerMismatch` / `NotFound` / `DataAccessException`）⇒ **一次失败只命中一个** ⇒ 运行时仍记 1 次 | 与 §11.5 的 `ObserveDBError` 同型，但**这次比的是错误码产出点**，所以逐条核到了 catch 块 |
| 4 | `Internal/ErrorGenerateID` | 2 / 1 | RAG 把雪花 ID 抽成 `nextId()`（`FavoriteDomainService:712-718`）⇒ 两个调用点共用 | Go `create_favorite_folder_logic.go` + `create_favorite_logic.go` 各 1 处 |

⇒ **结论：§2 的 D1–D6（5007/5008/5304 的读写路径之分）至今仍然成立**，没有新漂移。

> ⚠️ **方法上的一个坑**：第一遍统计按「方法名」归位 ⇒ 把 `requireOwnedFolder` / `nextId` 里的调用点
> 算到了**它们自己名下** ⇒ 看起来少了几处 ⇒ **差点误判成漂移**。
> ⇒ 复核必须**按调用链归位**（公共子步骤要摊回它的调用方），否则会造出假漂移（同 §11.5 的教训）。

### 14.3 ✅ api 侧：7 条路由契约复核

| Method | Path | Go（`routes.go`） | RAG（`FavoriteController`） | 判定 |
|---|---|---|---|---|
| POST | `/favorite/v1/folder/create` | `routes.go` 第 1 条 | `@PostMapping("/folder/create")` `:129` | ✅ |
| DELETE | `/favorite/v1/folder/delete` | 第 2 条 | `@DeleteMapping("/folder/delete")` `:136` | ✅ |
| GET | `/favorite/v1/folder/list` | 第 3 条 | `@GetMapping("/folder/list")` `:143` | ✅ |
| POST | `/favorite/v1/folder/update` | 第 4 条 | `@PostMapping("/folder/update")` `:156` | ✅ |
| POST | `/favorite/v1/item/create` | 第 5 条 | `@PostMapping("/item/create")` `:167` | ✅ |
| DELETE | `/favorite/v1/item/delete` | 第 6 条 | `@DeleteMapping("/item/delete")` `:174` | ✅ |
| GET | `/favorite/v1/item/list` | 第 7 条 | `@GetMapping("/item/list")` `:191` | ✅ |

⇒ 前缀 `/favorite/v1` 两侧一致 ✅；中间件挂载（黑名单在 JWT 之内）见 `api.md` §1.4 ✅。

### 14.4 本轮验证

| 项 | 结果 |
|---|---|
| rpc 侧 56 个调用点的 (gRPC 码, 业务码) | ✅ **16 种组合逐条同构** |
| 4 组调用点数不等的归因 | ✅ **全部等价，零漂移** |
| api 侧 7 条路由 + 前缀 | ✅ **逐条一致** |
| 代码改动 | **零**（本轮是**只读复核**） |
| 全量测试 | **200 例全绿**（未变） |

---

## 15. 2026-10-25 第九轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `9f52c8d`）

### 15.1 本轮审计的是「注释的可核验性」，不是行为

前八轮把**行为**对照到了「双侧活体对拍」的水位。本轮处理的是另一类风险：
**注释里的 Go 断言本身是否可核验**（AGENTS.md §3.3.2 / §3.3.1）。

> §3.3.2：「任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，否则**不得**用该措辞，
> 改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。」
> §3.3.1 第 1 条：「论证里的每个 Go 事实都必须带 `文件:行号`，**不许只写结论**」。

⚠️ 这一类风险**不会被任何行为测试抓到** —— 测试是绿的，行为也是对的，
但注释把读者指向了错误的行（或根本没给行）。**只能靠机器核验**。

### 15.2 新增工具

| 项 | 值 |
|---|---|
| 位置 | `tools/assertion-audit/favorite/check-assertion-sources.py` + `README.md` |
| 依据 | AGENTS.md §3.3.2（硬要求）+ §3.3.1（三条） |
| 扫描范围 | favorite 域 **4 个组件 / 91 个 java 文件 / 1029 个注释块**，其中 **416 块含 Go 断言** |
| 锚点解析 | **Go 真源** → **锁定版本的依赖**（真源 `go.mod` pin 住的版本，取本机 module cache）→ **Go 标准库**（`GOROOT/src`） |
| 多候选消歧 | **同域 → 同模块 → basename**（favorite 的 api 侧与 rpc 侧**各有同名 logic 文件**，只按同域筛不够） |

四类门禁级发现：

| 类 | 含义 |
|---|---|
| **F1** | 断言块里一个 `*.go:NN` 都没有 |
| **F2** | 锚点里的 .go 文件在真源 / 锁定依赖 / 标准库里都找不到 |
| **F3** | 文件找到了，但锚点行号超出该文件行数（行号过期） |
| **F4** | 块里 `<pre>` 引用的 Go 源码**落在锚点行段之外** ⇒ 锚点指到了别的函数 |

### 15.3 🔴 抓到的真缺陷（3 条 · 断言对、锚点错）

前八轮手工 review 没抓到，机器一次抓到 3 条 —— **都是「断言内容正确、锚点指错行」**：
读者按锚点跳过去会看到不相干的代码。

| # | 位置 | 缺陷 | 真值（2026-10-25 实测） |
|---|---|---|---|
| **D-a** | `favorite-api/…/FavoriteController.java` `assertGoEmptyStructBody` | 锚点 `rest/httpx/requests.go:129-137` 标为 `ParseJsonBody` ⇒ 实际 129-137 行是 **`ParsePath`**（另一个函数） | `ParseJsonBody` 在 **`rest/httpx/requests.go:117-124`** |
| **D-b** | `favorite-api/…/FavoriteRpcClient.java` | 锚点 `zrpc/config.go:29` 标为 `RpcClientConf.Timeout` ⇒ 实际 29 行是 **`Token`** | `Timeout int64 \`json:",default=2000"\`` 在 **`zrpc/config.go:31`** |
| **D-c** | `favorite-rpc/…/FavoriteAuthorityEndpointConfig.java` | 注释写「`net/http/html.go` 的 `htmlReplacer`」⇒ **该文件在 Go 1.25 已不存在** | `htmlReplacer` 在 **`net/http/server.go:2458`** |

⇒ **D-a / D-b 已修**（锚点改正）；**D-c 已修**（改为 `net/http/server.go:2458` 并注明
「Go 1.25 的 `net/http/html.go` 已不存在」）。

另 2 条 F2 同批修掉：

| 位置 | 缺陷 | 修法 |
|---|---|---|
| `FavoriteDeliveryValidator.java` | 锚点 `favoriteDelivery.go:155` ⇒ 真源里是 **`favorite_delivery.go`**（snake_case） | 改正命名 |
| `FavoriteJwtAuthInterceptor.java` | 锚点 `/api/internal/middleware/checkblacklistmiddleware.go` 的**前导 `/`** 有误导性（它指的是 **users 域**那份同名文件） | 改为 `service/user/user/api/internal/middleware/…` |

⚠️ **注意 `checkblacklist` 的两套命名**：favorite 用 `checkblacklist_middleware.go`（下划线），
follow / user 域用 `checkblacklistmiddleware.go`（无下划线）⇒ **字面存在但指向别的域**，
机器消歧时按「同域优先」才能发现。

### 15.4 修掉的门禁级发现（67 条 → 0）

| 类 | 条数 | 修法 |
|---|---|---|
| **F1**（缺锚点） | **61** | 逐条补 `文件:行号`；每条的行号都在 **Go 真源 / go-zero v1.10.2 / Go 1.25.11** 里**当场核对过** |
| **F2**（悬空） | **1** | `favoriteDelivery.go` → `favorite_delivery.go` |
| **F4**（引用不符） | **1** | `rest/httpx/requests.go:129-137` → `117-124` |
| **F3**（行号越界） | **0**（修完 F1 后新增 1 条，已一并修） | `helper.go:69` → `service/favorite/rpc/internal/logic/helper.go:69`（api 侧那份只有 46 行 ⇒ 原锚点指的是 rpc 侧） |

⇒ **本轮结束时门禁级发现 = 0**（`check-assertion-sources.py` EXIT=0）。

⚠️ **修法是「补锚点」，不是「改措辞」** —— 因为这些断言的内容本身**经核对是对的**（本轮逐条在 Go 真源 / go-zero v1.10.2 / Go 1.25.11 里核对过行号所指的内容）。
⇒ 换句话说：**补锚点 = 把「本来对但读者查不到」的断言变成可核验的**，**不是**把行为改回 Go。

### 15.5 另修的 26 处「读者定位不到」的裸路径（I2 → 0）

裸文件名在机器里能消歧，但**读者定位不到**。逐条人工核对上下文后补全：

| 类 | 处数 | 例子 |
|---|---|---|
| api 侧 vs rpc 侧**同名 logic 文件** | 54 | `create_favorite_logic.go` → `api/internal/logic/favorite/…` 或 `rpc/internal/logic/…`（按 Java 文件所属模块定） |
| `cmd/*/main.go` 三个进程同名 | 26 | `main.go` → `cmd/fact-dispatch/main.go`（调度器）/ `cmd/fact-legacy-preflight/main.go`（预检） |
| rpc 的 `favorite.go` 有两份 | — | 进程入口 `service/favorite/rpc/favorite.go` vs 数据模型 `…/internal/model/favorite.go`（按被引用的内容定） |
| go-zero 的 `httpx` vs `httpc` 同名 | 2 | `requests.go` / `responses.go` → `rest/httpx/…` |

### 15.6 1 处表述改正（不是行为漂移）

`FavoriteAuthorityEndpointConfig.goCleanPath` 的注释原写「**逐字复刻** Go 1.25 的实现」，
但引用的 Go 源码是**删节版**（漏了 Go 1.25 的 fast path，`net/http/server.go:2639-2646`）。

⇒ 已改为「**等价复刻**」，并补注证明：

> fast path 的命中条件 `len(p) == len(np)+1 && strings.HasPrefix(p, np)`，
> 由于 `np = path.Clean(p)` 会去掉结尾的 `"/"` ⇒ 等价于 `p == np + "/"`
> ⇒ `np = p` 与 `np += "/"` **结果完全相同** ⇒ 省略它**不改变任何可观察行为**。

⇒ **RAG 实现无需改动**（它本来就是 `np += "/"`），只改注释的表述精度。

### 15.7 ⚠️ 工具自身的 3 个缺陷（避免把工具缺陷当成代码缺陷）

审计器第一版把 4 条**假阳性**报成 F2。逐条查下来全是**工具**的问题，不是代码的问题 ——
这本身就是一条教训：**审计工具的第一版输出不能直接当结论用**。

| 工具缺陷 | 症状 | 修法 |
|---|---|---|
| 锚点正则的字符类漏了 `@` | `gorm.io/gorm@v1.31.1/callbacks/create.go:302` 被截成 `v1.31.1/…` ⇒ 报「悬空」 | 字符类补 `@` |
| 标准库索引只按 `GOROOT/src` 相对路径登记 | `src/net/url/url.go:1001` 解析不到 | 同一棵树再以 `src/` 前缀登记一份 |
| basename 回退对**带目录**的锚点也生效 | `zrpc/config.go` 被错映射成别的域的 `config.go` | basename 回退只在锚点**不含 `/`** 时才允许 |

⇒ 修完工具后重跑，真实门禁级发现从 67 收敛到 **63**（少 4 条 = 上面 3 条 + `src/net/url` 那条）。

### 15.8 剩余提示级（**已知 · 非门禁**）

| 类 | 条数 | 含义 |
|---|---|---|
| **I1** | **153** | **内联注释**里的 Go 断言没有锚点（§3.3.2 字面只点名 `@DisplayName` / javadoc ⇒ 只提示不拦） |
| **I3** | **10** | `<pre>` 引用的 Go 源码在锚点文件里**找不到逐字匹配**（多半是节选或重排成单行） |
| **I4** | **0** | 锚点只写了文件名 —— §15.5 已把裸路径全部补全 ⇒ **该类归零** |

⚠️ **I1 的 153 条是真实欠账**，不是噪音：它们多在 `// Go: …` 形态的行注释里。
按 §3.3.2 的字面要求（「任何以「Go: …」措辞出现的断言」）它们也该带 `文件:行号`。
本轮**未做**（量大且多为重复句式）⇒ **记为欠账，留给下一轮**。

### 15.9 本轮验证

| 项 | 结果 |
|---|---|
| `check-assertion-sources.py` | ✅ **EXIT=0**（门禁级 0 / I2 0） |
| 扫描规模 | 91 文件 / 1029 注释块 / 416 含 Go 断言的块 / **398 个锚点**（真源 313 / 依赖 69 / 标准库 16 / **悬空 0 / 越界 0 / 仅靠文件名定位 0**） |
| `<pre>` 引用逐字核对 | 12 段 —— **不匹配 0**（另 10 段是节选/改写 ⇒ I3，不拦） |
| **I2**（读者定位不到） | **0**（§15.5 补全 26 处裸路径后归零） |
| **可执行逻辑改动** | **零** —— 43 个 java 文件的改动**全部落在注释里**；机器复核（`git diff -U0 -- '*.java'` 去掉注释行）只剩 **6 行**，全是**测试展示文本**（2 处断言失败消息 + 1 处 `@DisplayName`），不参与任何判定 |
| 全量测试 | **200 例全绿**（未变） |
| 六个探针 + 三个守门 | 全部 **EXIT=0**（未变） |

---

## 16. 2026-10-25 第十轮（`align/favorite` 工作树 · 基线 commit 58e8314 · 承 `a72a6f2`）

### 16.1 本轮做两件事

1. **把第九轮记下的 I1 欠账补掉** —— 153 条内联注释里的 Go 断言没有 `文件:行号`。
2. **台账体检** —— 查 favorite 域的 12 份台账是不是「最新、最全」。

### 16.2 I1 补锚点：153 条 → 0

| 形态 | 条数 | 原样 | 补法 |
|---|---|---|---|
| **A** | **124** | `// Go: :51-57 —— …` | **行号已写、文件缺失** ⇒ 补文件前缀 |
| **B** | **29** | `// Go: <自由文本>` | 行号也没写 ⇒ 追加 `（文件:行号）` |

⚠️ **补文件不是「按类猜」**：favorite 的 api 侧与 rpc 侧**各有同名 logic 文件**，
`FavoriteDomainService` 一个类就横跨 **8 个 Go 文件**（7 个 logic + model 层的 `favorite_fact.go`）。

⇒ 本轮用**两条证据**反查目标文件，再用**第三条**核对行号：

| # | 证据 | 作用 |
|---|---|---|
| 1 | **行段在界约束** | 声称的行号必须落在候选文件的行数内 ⇒ 直接排除不可能的文件 |
| 2 | **特征 token 命中数** | 注释里 `{@code X}` / 反引号 / CamelCase 的标识符，有几个出现在**该行段内** |
| 3 | **打印 Go 原文逐条核对** | 86 条单文件类 + 37 条 `FavoriteDomainService` 全部打印原文，人工过一遍 |

⇒ 复核中确认 `FavoriteDomainService` 的 37 条**跨 8 个 Go 文件**（logic 层 7 个 + model 层 1 个），
其中 `model/favorite_fact.go:280-282` 属于 **model 层**而非 logic 层 —— 只按「类 ↔ 文件」映射会**解析错**。

### 16.3 🔴 补锚点时又抓到 **4 处「行号指错」**（断言内容对，行号错）

| # | 位置 | 原锚点 | 实际 | 修正 |
|---|---|---|---|---|
| **E-a** | `FavoriteLegacyPreflightRunner.java:138` | `:38-44` | 38-44 是那条 **SELECT 的尾巴**；**读失败分支在 48-50**，`exit 1` 在 cmd 的 38 | `model/favorite_legacy_preflight.go:48-50` ＋ `cmd/fact-legacy-preflight/main.go:38` |
| **E-b** | `FavoriteLegacyPreflightRunner.java:205` | `:70` | `markerApproval.String == ""` 在 **71** 行 | `model/favorite_legacy_preflight.go:71` |
| **E-c** | `FavoriteLegacyPreflightRunner.java:209` | `:70` | `markerHash.String != hash` 在 **71** 行 | `model/favorite_legacy_preflight.go:71` |
| **E-d** | `FavoriteDomainService.java:295` | 补成 `create_favorite_logic.go:280-282` ⇒ **越界**（该文件仅 170 行） | 该行是 **model 层**的 `folder.UserId != item.UserId ⇒ ErrFavoriteOwnerMismatch` | `model/favorite_fact.go:280-282` |

⇒ **E-a 是第九轮那 3 条之外的第 4 条** —— 形态是「行号存在、但指向了邻近的别的语句」。

⚠️ **本表自身的锚点也守同一条纪律**：E-a~E-d 的「实际 / 修正」列写的是**裸行号**，
本轮已全部补上文件前缀（`model/favorite_legacy_preflight.go` / `model/favorite_fact.go`）
⇒ 否则台账自己就违反本轮「补锚点」的主题。

### 16.4 🔴 1 处**把 Java 事实误记成 Go 断言**

`FavoriteProtectionChainWiringTest.java:102-103` 原写：

> `（Go: breakerName := info.FullMethod）`

⇒ **`info.FullMethod` 是 grpc-java 的 `MethodInfo`**，**不是 Go 侧写法**。
Go 侧的真值是：

```go
// go-zero@v1.10.2/zrpc/internal/clientinterceptors/breakerinterceptor.go:15
breakerName := path.Join(cc.Target(), method)
```

⇒ 已改正，并**显式标注** `info.FullMethod` 属 grpc-java ⇒ 以后不会再被当成 Go 证据引用。

⚠️ 这条的形态与 §15.3 那 3 条**不同**：那 3 条是「Go 的事实写对了、锚点指错行」；
这条是「**根本不是 Go 的事实**，却挂在 Go 名下」⇒ **更难发现**，因为它读起来完全像一条 Go 断言。

### 16.5 台账体检结果（12 份台账全查）

本轮体检分**两遍**：第一遍手工（查死指针 / 过期数字），第二遍**机器化**（见 §16.7 的新工具）。

| 类别 | 条数 | 处理 |
|---|---|---|
| **死指针** | **2** | ✅ 已修 |
| **过期/错误数字** | **3** | ✅ 已修（并注明增量为纯注释） |
| **台账自身的裸行号锚点** | **4** | ✅ 已修（§16.3 表内 3 处 + §16.2 1 处） |
| **RAG 侧行号锚点位移** | **6** | ✅ 已修（§16.8）—— **本轮改注释 ⇒ 自己把台账的锚点挪了** |
| **Go 侧源文件未落点** | **6** | ✅ 已补记（§16.7）—— 全部**行为已对照**，缺的是台账点名 |
| **本轮新发现未登记** | 本节 | ✅ 已登记 |

**死指针 2 条**（都是**跨文件**的章节引用，单文件内查不出来）：

| 位置 | 原引用 | 实况 | 改为 |
|---|---|---|---|
| `DRIFT/favorite/rpc.md` §5-U1 行 | 「见 §9.5.1」 | rpc.md **没有 §9.5.1**（§9.5 是 like-api 的跨域观察） | **§10.2**（D2 · path 形态与错误响应逐条比对） |
| `Intentional_Deviations/favorite/rpc.md`「撤销记录 · DEV-FAV-RPC-002」 | 指向 DRIFT 的 **§10.8** | rpc.md 的 §10 只到 **§10.5** | **§11.4**（D4 · 前提被逐行核对证伪） |

**过期数字 3 条**：

| 位置 | 原值 | 现值 | 判定 |
|---|---|---|---|
| `DRIFT/favorite/api.md` 落地形态 | 2,920 行 / **13 个 DTO** | **3,116 行 / 15 个 DTO** | ⚠️ **DTO 数 13 从一开始就写错了**（基线 `58e8314` 就是 15 个）；行数 +196 = **各轮纯注释增量** |
| `DRIFT/favorite/rpc.md` 落地 | 7,057 行 / 6 个迁移脚本 | **7,571 行 / 7 个迁移脚本** | V7 是第七轮加的时钟源对齐脚本；行数增量 **全部是纯注释** |
| `DRIFT/favorite/common.md` 落地形态 | 456 行 | **456 行** | ✅ 仍准确（基线 453） |

⇒ ⚠️ **「落地形态」这类数字一旦被后续轮次改过就会过期** ⇒ 已按 AGENTS.md §6 的
「状态表纪律」补上**核对日期**，并注明增量的性质（**纯注释 vs 行为**）。

### 16.6 本轮验证

| 项 | 结果 |
|---|---|
| `check-assertion-sources.py` | ✅ **EXIT=0**（门禁级 **0** / **I1 0** / I2 0） |
| `check-ledger-selfcheck.py`（本轮新建） | ✅ **EXIT=0**（门禁级 **0**：章节指针全解析 + 32 个编号全闭合 + Go 侧 50/50 有落点；提示级 59 处裸行号已逐条判定语境） |
| 锚点规模 | 91 文件 / 1029 注释块 / 416 含 Go 断言的块 / **553 个锚点**（真源 457 / 依赖 76 / 标准库 20 / **悬空 0 / 越界 0 / 仅靠文件名定位 0**） |
| 补锚点 | **153 条**（形态 A 124 + 形态 B 29）—— 按模块：rpc **139** / api **12** / common **2**，覆盖 **22 个 java 文件** |
| 新抓出的真缺陷 | **5 条**（E-a~E-d 四条行号指错 + 一条把 Java 事实误记成 Go） |
| **RAG 侧行号锚点位移** | **6 条**（R-1~R-6）⇒ 越界检查全过但**内容偏 1~2 行** ⇒ 靠「打印实际内容逐条对」抓出（§16.8） |
| **可执行逻辑改动** | **零** —— 26 个 java 文件的改动**全部落在注释里**；机器复核（`git diff -U0 -- '*.java'` 去掉注释行）**剩余为空** |
| 改动规模 | 26 个文件 / **+303 / −157**（全为注释） |
| 全量测试 | **200 例全绿**（api 89 / rpc 82 / common 29 / proto 0），**BUILD SUCCESS** |
| 六个探针 + 三个守门 | 全部 **EXIT=0**（未变） |

### 16.7 🆕 机器化体检：Go 侧源文件**覆盖闭合**

**问题**：「台账是不是**全**的」以前只能靠人肉翻 50 个 Go 文件。
**解法**：新工具 [`tools/ledger-lint/favorite/check-ledger-selfcheck.py`](../../../../tools/ledger-lint/favorite/check-ledger-selfcheck.py)
（域内文件，**不碰共享面**）⇒ 机检三件事：**章节指针** / **编号闭合** / **Go 源文件覆盖**。

#### 16.7.1 机检结果（核对 2026-10-25）

| 项 | 规模 | 结果 |
|---|---|---|
| 章节指针 | 文件级 12 处 + 裸引用 186 处 | ✅ **全部可解析**（含 4 个外部目标：根 `AGENTS.md` + 三个共享 `_INDEX.md`） |
| 编号闭合 | 32 个 `DEV-FAV-*` / `PIT-FAV-*` | ✅ **全部有定义处** |
| Go 侧源文件覆盖 | **50 个**非测试源文件 | ⚠️ 初次跑出 **6 个未落点** ⇒ 见下表 |
| 裸行号锚点 | 59 处 | ℹ️ **提示级**（非门禁）⇒ 逐条判定：55 处有语境列，4 处无语境 ⇒ 已修 |

#### 16.7.2 6 个「台账未点名」的 Go 文件 —— **行为全部已对照，缺的只是点名**

| Go 文件 | 性质 | 行为对照落点 | 处置 |
|---|---|---|---|
| `rpc/internal/model/favorite_legacy_preflight.go` | 🔴 **真逻辑** | RAG `FavoriteLegacyPreflightRunner`：SQL `40-47` / 逐行比对 `50-78` / `69-73` / 孤儿撤回 `86-91` / `Clear()` `22-25` 逐条对齐；事务参数 `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` ↔ Go `sql.TxOptions{Isolation: sql.LevelRepeatableRead, ReadOnly: true}`（`:35`） | ✅ 本轮点名（§16.3） |
| `rpc/internal/model/vars.go` | 🔴 **契约** | `ErrorNotFound` 哨兵（`errors.New("record not found")`）⇒ Go 侧 7 个 logic 用 `errors.Is` 区分「记录不存在」与别的 DB 错误 ⇒ 映射 `codes.NotFound`；RAG 侧对应 `FavoriteExceptions.NotFoundException`（`FavoriteFolderMapper:19-21` 已注明映射关系） | ✅ 本轮点名（本表） |
| `rpc/internal/svc/service_context.go` | 装配层 | 注入 `FavoriteModel` / `UserRpc` / `ArticleRpc`，并把 `c.SubjectRefV2Facts` 接成 `model.WithSubjectRefV2Facts()` ⇒ **v1/v2 事实信封的行为开关**（台账 §0-156 已覆盖该开关语义） | ✅ 本轮点名（本表） |
| `api/internal/svc/service_context.go` | 装配层 | 注入 `FavoriteRpc`（zrpc client）+ `CheckBlacklistMiddleware`（Redis）⇒ 黑名单 fail-open 语义已在 `api.md` §1.4 逐条对照 | ✅ 本轮点名（本表） |
| `rpc/favoriteservice/favorite_service.go` | goctl **生成** | `// Code generated by goctl. DO NOT EDIT.` ⇒ 但它是 **7 个 RPC 方法签名的权威声明处** ⇒ `proto.md` §… 的 7 条 RPC 契约以此为准 | ✅ 本轮点名（本表） |
| `rpc/pb/favorite_grpc.pb.go` | protoc **生成** | wire 契约产物 ⇒ 由 `tools/proto-wire-probe/favorite/` 做双侧字节对拍（已 EXIT=0） | ✅ 本轮点名（本表） |

⇒ **结论：favorite 域的 Go 侧 50 个非测试源文件现已 50/50 有台账落点。**
⚠️ **这不等于「行为全对」** —— 它只等于「**没有文件被漏看**」
⇒ 行为层结论仍以各域台账的「证据强度」列为准（§0 口径）。

#### 16.7.3 工具的误报处理（**工具自身的第一版必须先修再信**）

| 误报类 | 处理 |
|---|---|
| 正文里裸写的路径（如 ``DRIFT/favorite/rpc.md`` §5-U1``）被判死链 | ⇒ 只有 **markdown 链接** ``[..](path.md) §x.y`` 才做文件级解析；裸路径是「位置描述」，不判 |
| 指向根 `AGENTS.md` / 共享 `_INDEX.md` 的引用被判死节 | ⇒ 把这 4 个外部目标纳入索引，按**真实标题**判定 |
| RAG 侧 `target/` 下的 protoc 产物被算成源文件 | ⇒ 生成代码不计入覆盖检查 |
| 表格里**有语境列**的裸行号（`:129`，列头已写「RAG（`FavoriteController`）」）被判问题 | ⇒ 裸行号降级为**提示级**；无语境才需人工修 |

⚠️ **与第九轮同一条教训**：这个工具的第一版也踩了坑（正则漏捕获组导致崩溃、`FILES_REF` 定义顺序颠倒）
⇒ **先修工具再信工具**，且 §16.7.2 的 6 条结论是**工具与人工双向独立得出**的（结论一致才算数）。

### 16.8 🔴 6 处 **RAG 侧行号锚点位移**（本轮改注释 ⇒ 自己把台账的锚点挪了）

**根因**：台账的「位置」列引用的是 **RAG 侧 java 文件的行号**（如 `FavoriteController:190`）。
而本轮**改了 26 个 java 文件的注释** ⇒ **行号整体位移** ⇒ 台账自己引用的位置**当场过期**。

⇒ 这是**自指的漂移**：本轮修的是「java 注释里的 Go 锚点」，却顺手弄坏了「台账里的 java 锚点」。

| # | 位置 | 台账写的 | 实际 | 判定 |
|---|---|---|---|---|
| **R-1** | `api.md` §1.4「Redis 故障」行 | `isBlacklisted:220-230` | **221-231** | ❌ 整体偏 1 行 |
| **R-2** | `api.md` §4「空 struct body 校验」行 | `FavoriteController:234-263` | **235-264** | ❌ 首尾各偏 1 行 |
| **R-3** | `rpc.md` §14.3 路由表（item/list 行） | `FavoriteController:190` | **191** | ❌ 偏 1 行 |
| **R-4** | 本档 §16.3 **E-b** 的「位置」列 | `FavoriteLegacyPreflightRunner.java:204` | **205** | ❌ 偏 1 行 |
| **R-5** | 本档 §16.3 **E-c** 的「位置」列 | `FavoriteLegacyPreflightRunner.java:208` | **209** | ❌ 偏 1 行 |
| **R-6** | 本档 §16.4 的「位置」列 | `FavoriteProtectionChainWiringTest.java:101` | **102-103** | ❌ 偏 1~2 行 |

⇒ ✅ **6 处已全部修正**（本轮 2026-10-25 核对）。

⚠️ **注意 R-4/R-5/R-6 的讽刺之处**：它们是**本轮自己新写的**行号 ——
写的时候用的是**补锚点之前**的文件状态，补锚点又改了同一个文件 ⇒ 又偏了。
⇒ **「写完锚点必须再核一遍行号」不是可选项**，哪怕改动是纯注释。

#### 16.8.1 机检：RAG 侧锚点的行号越界 + 内容抽查

| 项 | 结果 |
|---|---|
| 台账里可判定的 RAG 侧行号锚点 | **50 个**（修正前口径；修正并补记后重跑为 **64 个**） |
| **越界**（行号超出文件行数） | **0** ✅ —— 但**这不代表对**（R-1~R-3 越界检查全过，内容却偏了 1 行） |
| **逐条打印实际内容人工抽查** | **6 处漂移**，其余 44 处 ✅ |
| **修正后重跑** | 64 个锚点**越界 0**；6 处修正逐条打印复核 ⇒ **全部对上** |

⇒ ⚠️ **越界检查是必要不充分条件**：行号落在文件内 **≠** 指对了那一行。
真正能抓出的是「**打印实际内容逐条对**」⇒ 这是**人工动作**，本轮已把范围压到 50 个锚点。

⇒ 元教训已登记 ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-011**。


---

## 17. 2026-10-25 第十一轮（`align/favorite` 工作树 · 承 `a2fcbc4`）—— **三条候选签字**

> 本轮**无代码改动**：只把三条 🏷 候选转成 ✅ 已签字，并同步各处状态引用。

| 条目 | 签字前 | 签字后 | 附带的前置动作 |
|---|---|---|---|
| **DEV-FAV-RPC-006**（12 处约束 / 索引**命名**差异） | 🏷 候选，建议签 | ✅ **已签字** | 若统一索引 / 约束名（迁库）⇒ **按名字 DROP / 重建的运维脚本会失效**（部署脚本层） |
| **DEV-FAV-RPC-007**（RAG 多一道 `target_revision scope` CHECK，**更严**） | 🏷 候选，建议签 | ✅ **已签字** | 出现**第二个写入方**直接写 `favorite_item` ⇒ 写入方需知道 RAG 多这条约束（23514 拒）；**不需要改 RAG** |

⇒ **favorite 域现无 🏷 候选**。

⚠️ **共享索引未动**（AGENTS.md §5.3 共享面禁写）⇒ **合并后需主会话统一更新**
`docs/Intentional_Deviations/_INDEX.md`，共 **4 处**：

| # | 位置 | 现值 | 应改为 |
|---|---|---|---|
| 1 | 「✅ 已签字合计」 | **49 条** | **58 条**（+9：API-001 / **API-002** / COMMON-001 / COMMON-002 / RPC-001 / RPC-003 / RPC-004 / **RPC-006** / **RPC-007**）<br>⚠️ **口径待主会话定**：另有 **2 条已撤销**（RPC-002 / -005）⇒ 若计入则 **60** |
| 2 | 「⏳ 未签字候选合计」 | **14 条** | **14 条（不变）** —— ⚠️ 本域的 3 条候选**从未进过共享索引**（索引里 favorite 是「0 份 · 本轮未做」）⇒ **不要减 3** |
| 3 | 域覆盖表 `favorite` 行 | `0 份` · 「本轮该域**未做**」 | **4 份**（`api.md` / `common.md` / `proto.md` / `rpc.md`）· **9 条已签字 + 2 条已撤销** |
| 4 | 「待填（空文件即坑位）」段 | `favorite/*`（本域本轮未做） | **删除该行**（本域 4 份**已全填**） |

⇒ 💡 **favorite 域的 `DEV-FAV-*` 已是「域前缀」编号** ⇒ **不受**共享索引「🔴 编号撞号」那条
（`SD-2026-10-23-06` 等三处撞号）影响 ⇒ 无需改号。

### 17.1 顺带改正的一处**过期断言**

§0 头部原写「**未做变异验证**」⇒ 那是 **2026-10-23 首轮（§0–§8）**的口径，
**第五轮起已做**（§11.7 的 M1/M2 等）⇒ 已把该断言**限定到 §0–§8**，
并补上「**§9 之后证据强度大幅提高**（变异验证 + 6 项双侧活体对拍）」的读法指引。

⇒ 这正是第十轮体检抓出的那类「**过期断言**」——只是这次是从**台账自己**身上找到的。

---

## 18. 2026-10-25 第十一轮（续）· 📌 **favorite 域内「可动项」清单**

> 用户问「现在域内可修漂移、未知项有哪些」⇒ 本节给出**带证据的清单**，
> 并把「可动」与「不可动（跨域 / 环境 / 已决策）」分开。

### 18.1 ✅ 可动项 A：**权威读 HTTP 面的双侧活体对拍 —— 其实做得了**（**第十二轮已落地** ⇒ §19.1）

⚠️ **本节修正一处过宽的判定**：此前「U7 环境受限」的理由是
「Go 侧 favorite-rpc 依赖 `etcd` + Kafka + Redis + article-rpc / user-rpc ⇒ 本机拉不起」。
**这个理由只对 gRPC 整域成立**，对**权威读 HTTP 面不成立**：

| 依赖 | Go `cmd/fact-authority/main.go` | RAG `FavoriteAuthorityEndpointConfig` |
|---|---|---|
| **etcd** | ❌ **不需要**（`:32-37` 只读 `FAVORITE_DATABASE_URL` / `_LISTEN` / `_TOKEN`） | ❌ 不需要（favorite 域**零 Redis 零 Kafka**，`application.yml:9-11`） |
| **Kafka** | ❌ 不需要 | ❌ 不需要 |
| **Redis** | ❌ 不需要 | ❌ 不需要 |
| **PG** | ✅ 需要（`:38` `gorm.Open`） | ✅ 需要 |
| **user-rpc / article-rpc** | ❌ **不需要** —— `AuthoritativeFavoriteFact`（`model/favorite_authority.go:208+`）只查 `favorite_fact_outbox` + `favorite_item` 两张表 | ❌ **不需要** —— `FavoriteAuthorityLookupService` 的依赖只有 `outboxMapper` / `favoriteItemMapper` / `deliveryValidator`（`:51-53`） |
| **形态** | 独立 `http.Server`（`:56-57`，`ReadHeaderTimeout: 3s` / `WriteTimeout: 5s`） | path 参数 servlet（`@ConditionalOnProperty` 默认关 ⇒ 需 `favorite.fact.authority.enabled=true` + token ≥32 字节） |

⇒ **两侧都是 HTTP + PG ⇒ 真双侧活体对拍可行**，且对拍面是**字节层**的
（Go `json.NewEncoder(w).Encode(fact)` vs RAG Jackson 序列化 + `Cache-Control: no-store` + 401/404/503 形态）。

⇒ **建议的对拍矩阵**（复用 `tools/authority-path-probe/favorite/` 的期望文件结构）：
`v1`/`v2` × {token 正确 / 缺失 / 前缀错 / 重复 Authorization 头 / token 短于 32} ×
{事件存在 / 不存在 / schema 不匹配 / 撤回态 / 撤回后收藏仍在} × {带 query / 路径不匹配}。

⚠️ **本节只是「判定可做」，尚未实施** ⇒ 实现归下一轮。

### 18.2 ✅ 可动项 B：**域外直接写入方盘点**（V7 上线前置）—— **第十二轮已落地为机检工具** ⇒ §19.2

| 项 | 状态 |
|---|---|
| 本域生产代码（唯一写入方 `insertFact`） | ✅ 已显式绑定 `created_at` / `updated_at` |
| 本域测试的裸 INSERT（6 处） | ✅ 已改绑 |
| **域外**的直接写入方（人工 SQL / 报表 / 另一个服务 / 数据修复脚本） | ⚠️ **未盘点** ⇒ **V7 上线前必须确认** |

⇒ **可做成工具**：全仓扫 `favorite_fact_outbox` 的裸 INSERT（跨仓库 / 跨服务），
把「盘点」从人工变成机检 ⇒ 建议归下一轮（**只读扫描，不改别域代码**）。

### 18.3 ⬜ **不可动（跨域 / 收口在别处）** —— 本轮**只登记、不动手**

| 项 | 归属 | 本域动作 |
|---|---|---|
| 容器层参数上限口径（Tomcat 数总「值」数 vs Go 数非空值数） | 🔴 `global`（与 hot 域 `DRIFT-HOTAPI-030` 同一格） | 已在 `api.md` §3 登记 |
| `like-api` 的 `LikeRpcClient.java:43-48` 熔断注释（疑似把 Java 事实误记成未接入） | `like` 域文件 ⇒ 本域**只读** | 已登记在 §9.5，**未改** |
| gRPC **帧层**字节对拍（HTTP2 / length-prefix / status 帧） | 库级差异 ⇒ **不属本域**（**第十三轮已修订**：拆成三层，L0 仍不做、L1 已对拍）⇒ 见 §20.1 | 已判定 ⇒ 第十三轮补做 **§20** |
| **gRPC 整域**活体对拍 | 🔴 真受阻（Go gRPC server 注册走 etcd）⇒ ⚠️ **第十三轮已推翻**：**Endpoints 优先级 ⇒ 不需要 etcd** | 已拆成 6 项局部对拍 ⇒ **第十三轮 ⇒ 9 项**（§20.3） |

### 18.4 ✅ **本域已完成、无需再动**（避免重复劳动）

| 类别 | 条数 | 落点 |
|---|---|---|
| **已签字偏离** | **9** 条 | API-001 / **API-002** / COMMON-001 / COMMON-002 / RPC-001 / RPC-003 / RPC-004 / **RPC-006** / **RPC-007** ⇒ **本域已无 🏷 候选** |
| **已撤销**（前提被证伪 ⇒ 不是偏离） | 2 条 | RPC-002（`:89`/`:68` 实为**正确**）/ RPC-005（U4 已修） |
| **已修漂移** | rpc 6 处 L2 + 2 处真 bug；api 4 处（L2 ×2 + 假断言 ×2 + L1 ×1） | §2 / §3 / `api.md` §2 |
| **已闭合** | U1 / U2 / U3 / U4 / U5 / U6 | §11.6 |
| **Go 侧源文件覆盖** | **50/50** 有台账落点 | §16.7 |

⇒ **favorite 域本轮无「未决的已识别漂移」** —— 剩下的全是**证据强度问题**（§18.1 / §18.2）与**跨域项**（§18.3）。

---

## 19. 2026-10-25 第十二轮（`align/favorite` 工作树 · 承 `0fa76f2`）—— **可动项 A + B 落地**

> 本轮把第十一轮 §18.1 / §18.2 的两个「可动项」真正做掉了。
> ⚠️ **有 1 处可执行逻辑改动**（不是纯注释）—— 见 §19.3。

### 19.1 ✅ 可动项 A：**权威读 HTTP 面的双侧活体对拍**

**新工具**：[`tools/authority-live-probe/favorite/`](../../../../tools/authority-live-probe/favorite/README.md)
（`run.sh` + `expected.txt` + `seed.sql` + 13 个真源逐字节复制件 + Go 侧探针）
**Java 侧**：`FavoriteAuthorityLiveParityTest`（真 PG Testcontainers + 真 Flyway + 真 MyBatis + 真 servlet）

| 项 | 结果 |
|---|---|
| **对拍结果** | **21 个 case：20 个逐字节完全一致 + 1 个 SKIP** ⇒ **EXIT=0** |
| 覆盖 | 200 正常（v1 三字段 / v2 两字段 `subject_ref`）· 鉴权 7 条（含**重复 Authorization 头**）· mux 5 条（path 清理 301 / 404 / 405）· query 2 条（带 query ⇒ 404）· 数据面 6 条（不存在 / producer 不匹配 / v1↔v2 schema 交叉 / event_id 超长 / `%2F`） |
| 两侧怎么做到同一份数据 | **共用同一份 `seed.sql`** ⇒ 种子由 **真源自己的写入路径**生成：`model.InsertFavorite` + `model.DispatchFavoriteFactOnce`（+ 一个 `FavoriteFactSender` stub 用**同一个** `jsoncanonicalizer` 算 DC 端的 JCS hash）⇒ **真源随后自己用 `favoriteJCSHash(row.Payload)` 复核** ⇒ hash 算错真源自己就会拒 ⇒ **探针自证** |
| 真源漂移哨兵 | `expected.txt` = **冻结的 Go 侧实测值**；`run.sh` 把 Go 输出与它逐行比对 ⇒ **Go 真源变了探针立刻响**；Java 侧断言的**也是这份** ⇒ 两侧共用一份真源 |
| **🔴 抓出并已修的 1 条真缺陷** | 见 §19.3 |
| **唯一的 SKIP** | `mux_method_not_allowed` ⇒ Go 的 405 来自 **`http.ServeMux` 层**（`Allow: GET, HEAD` + text/plain + nosniff + body）；RAG 侧是 **Servlet 层**（只有 `doGet`）⇒ Mock 下空 body，**真 Tomcat 下是容器的 HTML 错误页** ⇒ 形态取决于**部署形态** ⇒ 归 **DEV-FAV-RPC-004**（默认无 web 容器 ⇒ 端点默认关闭）⇒ **SKIP 理由已写进 `expected.txt` 注释**，不许默默忽略 |
| 变异验证 | 去掉 RAG 侧 200 路径的尾部 `"\n"` ⇒ **`v1_ok` 转红**；复原 ⇒ 绿 ⇒ **不是假绿** |

### 19.2 ✅ 可动项 B：**域外写入方盘点机检**

**新工具**：[`tools/outbox-writer-audit/favorite/check-outbox-writers.py`](../../../../tools/outbox-writer-audit/favorite/README.md)

| 项 | 结果 |
|---|---|
| **本仓结论** | **7 个 INSERT 站点、0 个域外写入方**、**8 个 UPDATE 站点**（只登记）⇒ **EXIT=0** |
| 判定 | ① 站点必须在 `favorite-rpc` 模块内（域外 ⇒ 🔴 exit 1）；② INSERT 必须显式绑 `created_at` + `updated_at`（V7 去 DEFAULT 后不绑 ⇒ 立刻 NOT NULL 违例） |
| 变异验证 | 去掉任一 INSERT 的两列绑定 ⇒ **EXIT=1 且点名文件行号**；复原 ⇒ EXIT=0 ⇒ **不是假绿** |
| ⚠️ **边界（必须一并读）** | 工具只看得见**本仓库的代码** ⇒ 看不见**人工 psql、报表工具、数据修复脚本、另一个仓库、部署期一次性命令** ⇒ **EXIT=0 ≠ 可以放心上 V7** ⇒ 上线前**仍需人工确认一次** |

### 19.3 🔴 本轮修的 1 条**可执行逻辑**差异（不是注释）

| 项 | 内容 |
|---|---|
| **位置** | `FavoriteAuthorityEndpointConfig`（200 响应路径） |
| **Go 行为** | `json.NewEncoder(w).Encode(fact)` ⇒ **必定追加一个 `"\n"`**（encoding/json 的 `Encoder.Encode` 契约：写完 JSON 后跟一个换行符） |
| **RAG 原行为** | `STRICT_JSON.writeValueAsBytes(fact)` ⇒ **少了那一个字节** ⇒ 响应体逐字节不一致（`Content-Length` 也差 1） |
| **为什么判它是「缺口」而不是「有意偏离」** | 同一类的**错误路径**（`writeGoError` / `writeMovedPermanently`）**刻意**补了尾部 `"\n"` 以对齐 Go ⇒ **同一件事上的不一致** ⇒ 不是设计选择 |
| **修法** | `resp.getOutputStream().write('\n')`（紧跟 body 之后） |
| **守门** | `tools/authority-live-probe/favorite/`（`v1_ok` / `v2_ok` 两例逐字节对拍）+ 变异验证 |

### 19.4 🔴 顺带修掉的一类**探针假红**（`-pl` 不带 `-am` ⇒ 用过期上游产物）

**现象**：`tools/api-envelope-probe/favorite/run.sh` 稳定失败
（`Too many interim responses received: 9 > 8`）⇒ 看着像「Go ↔ RAG 响应字节对不上」。

**根因（已定位，非猜测）**：`-pl <module>` **不带 `-am`** ⇒ 不重建上游模块 ⇒
直接用**过期的** `service/common/target/classes`。
⚠️ 在 worktree 场景里这是**常态**：`service/common` 是跨域共享模块，源码可能已被
**别的会话**改过，而本 worktree 的 `target/` 还是上一轮的 ⇒ api 模块链接旧 class ⇒ 行为不一致。

**决定性对照**（同一份源码、同一台机器）：

| 环境 | 命令 | 结果 |
|---|---|---|
| **基线 worktree**（`0fa76f2`，零 target） | `mvn -o -pl … -am test` | ✅ 绿 |
| 本 worktree，**不带** `-am` | `mvn -o -pl … test` | ❌ 红（3/3 次） |
| 本 worktree，**带** `-am` | `mvn -o -pl … -am test` | ✅ 绿 |

⇒ 变量只有「上游产物是否新鲜」⇒ 根因坐实 ⇒ **不是产品缺陷**。

**修法**：**所有 favorite 域探针的 mvn 调用一律加 `-am`** +
`-Dsurefire.failIfNoSpecifiedTests=false`（`-am` 会把 `-Dtest=X` 带到上游模块，
上游没这个测试 ⇒ surefire 直接失败）。
已改：`api-envelope-probe` / `proto-wire-probe` / `authority-path-probe` /
`authority-live-probe` / `errmsg-probe`（`ddl-parity-probe` 无 mvn 调用 ⇒ 不受影响）。
⇒ 元教训登记为 **PIT-FAV-RPC-013**。

### 19.5 本轮验证

| 项 | 结果 |
|---|---|
| `tools/authority-live-probe/favorite/run.sh` | ✅ **EXIT=0**（SAME 20 / OBS 0 / SKIP 1） |
| `tools/outbox-writer-audit/favorite/check-outbox-writers.py` | ✅ **EXIT=0**（0 个域外写入方） |
| `check-assertion-sources.py` | ✅ **EXIT=0** |
| `check-ledger-selfcheck.py` | ✅ **EXIT=0** |
| 全量测试 | 见 §19.5 |
| 六个既有探针 + 三个守门 | 见 §19.5 |
| 共享面 | **零改动** |

### 19.6 ⚠️ U7 的状态更新（**环境受限的判定被切掉一块**）

> 🔴 **本轮推翻了「U7 环境受限」的一部分判定** ——
> 此前说「拿不到整域活体对拍」的理由是「Go 侧 favorite-rpc 依赖 etcd + Kafka + Redis +
> article-rpc / user-rpc」⇒ **那个理由只对 gRPC 整域成立**：
> **权威读 HTTP 面两边都只要 PG + HTTP** ⇒ 真双侧活体对拍**做得了**，本轮做掉了。

⇒ U7 的**局部**双侧活体对拍由 **6 项 → 7 项**（新增「**权威读 HTTP 面**」，21 个 case 逐字节）。
⇒ **U7 仍未闭** —— 剩下的「整域」= **gRPC 整域**（Go gRPC server 注册走 etcd ⇒ 真受阻）
+ **api 整域**（Go favorite-api 依赖 etcd + favorite-rpc + Redis）。

---

## 20. 2026-10-25 第十三轮（`align/favorite` 工作树 · 承 `2208cab`）—— **帧层重新评估 + gRPC 级可观测契约双侧活体对拍**

> **本轮性质**：把挂了三轮的那条「🔴 未验：帧层（HTTP2 / length-prefix / gRPC status 帧）」**拆成三层**分别给结论，
> 同时**推翻了此前「favorite-rpc 的双侧活体对拍被环境锁死」的判定**。
>
> **新工具**：`tools/grpc-contract-probe/favorite/`（EXIT=0）· **新测试**：`FavoriteGrpcContractParityTest`
> **证据强度**：✅ **双侧活体对拍** —— Go 真源 server 与 RAG server **同时在跑**，
> 由**同一个 grpc-java 客户端**分别打两侧（排除客户端自身差异）。

### 20.1 旧判定的重评：「帧层」不是一个东西，必须拆成三层

| 层 | 内容 | 判定 | 依据（带行号） |
|---|---|---|---|
| **L0 帧原始字节** | HPACK 编码 / 帧序 / WINDOW_UPDATE / PING / HTTP2 SETTINGS | 🔴 **不做字节比对**（旧判定**成立**，理由更精确） | 由 **gRPC 库**决定（grpc-go vs grpc-java）；且**客户端不可观测** —— 客户端的 HTTP/2 实现会**重新编码**对端帧 ⇒ 属**库级**差异，**不是 favorite 域的行为** |
| **L1 帧层之上、消息层之下** | gRPC **status code** / **description** / 响应消息**字节** / **metadata key 集合** | ✅ **本域该管**（旧判定**漏了这一层**） | 这四项**客户端可观测**，且**由 favorite 域的代码决定**：Go `service/favorite/common/errmsg.go:104-106` 的 `GRPCError(grpcCode, bizCode)` → `status.Error(code, GetErrMsg(bizCode))`；RAG `favorite-common/.../FavoriteGrpcError.java:94-98` 的 `grpcError(code, bizCode)` → `Status.fromCode(code).withDescription(getMsg(bizCode))` |
| **L2 消息层** | protobuf 消息体 wire 字节 | ✅ 已在第六轮闭合 | [`tools/proto-wire-probe/favorite/`](../..) |

**⇒ 旧判定的错误所在**：把「帧层」当成一整块，于是 **L0 的「客户端不可观测」被外推成「L1 也不该管」**。

**⇒ L1 恰恰是本域责任最高的一层**：favorite 域把**业务码藏在 description 文案里**（业务码本身不上线），
客户端（favorite-api）靠文案反查还原
（Go `service/favorite/common/errmsg.go:108-137` 的 `BizCodeFromError`；RAG `FavoriteGrpcError.java:171-188` 的 `bizCodeFromError`）
⇒ **description 一旦变形，业务码直接丢** ⇒ 必须守，而且**必须用真实 HTTP/2 通道守**（in-process 传输不施加 percent-encoding）。

### 20.2 方法论：为什么必须「同一个客户端打两侧」

- 帧层的原始字节由库决定 ⇒ 直接拿两边的字节互比 = 在测「两个 gRPC 实现之间的差异」，**与「RAG 是否忠实复刻 Go」无关**。
- L1 可以比，但要**消掉客户端自身的差异** ⇒ **两侧用同一个 grpc-java 客户端、同一份 stub、同一套 metadata 处理**。
- ⇒ 本探针的全部结论都建立在这一条上：**换一个客户端，结论就不成立**。

### 20.3 结果：36 个 case（33 SAME / 2 SAME_CODE / 1 OBS）

| 档 | 条数 | 覆盖 |
|---|---|---|
| **SAME**（两侧**逐字节相同**） | **33** | 7 个方法的全部参数校验分支 / `NotFound` / `PermissionDenied` / **`FAILED_PRECONDITION`+5304** / 成功响应的**消息字节** / **下游 user-rpc 缺席**的依赖失败分支 |
| **SAME_CODE**（**status code 相同**，措辞允许不同） | **2** | 未注册的方法 / 未注册的服务 ⇒ 两侧 `code` 都是 `UNIMPLEMENTED` |
| **OBS**（两侧**必须不同**，已登记） | **1** | 完整响应头 key 集合 |

**⇒ 关键结论（逐条都有实测值）**：

1. **中文业务文案在真实 HTTP/2 通道上逐字节一致** ⇒ `参数错误` / `收藏夹名称不能为空` / `收藏夹不存在` /
   `收藏记录不存在` / `无权限操作` / `收藏目标不能为空` / `收藏暂不可撤回` / `系统内部错误` 两侧完全相同
   ⇒ **favorite-api 的「文案反查业务码」链路成立**（这是本域最脆的一环）。
2. **成功响应的 protobuf 字节逐字节一致**（含 `seed.sql` 写死的时间列 ⇒ `UnixMilli()` 相同）
   ⇒ L1 与 L2 在同一份数据上对齐。
3. **5304「收藏暂不可撤回」两侧同为 `FAILED_PRECONDITION`** ⇒ 这条**最容易被 presence 语义走偏**的分支（且它
   **不在** `BizCodeFromError` 的 switch 白名单里，只能靠文案还原）实测一致。
4. **下游 user-rpc 缺席 ⇒ 两侧同为 `INTERNAL` + 5001「系统内部错误」**
   （Go `internal/logic/helper.go:103-107` ⇒ `GRPCError(codes.Internal, ErrorServerCommon)`；
   RAG `FavoriteUserGuard.java:78-90` ⇒ `internalServerCommon()`）⇒ **依赖失败契约也一致**。

### 20.4 库级差异的量化（3 条，全部显式登记为 OBS）

| # | 差异 | 两侧实测 | 归因 |
|---|---|---|---|
| 1 | **响应头 key 集合** | Go：`content-type`；RAG：`content-type` + **`grpc-accept-encoding=gzip`** + **`grpc-encoding=identity`** | grpc-java 的传输层发的（`io/grpc/internal/GrpcUtil.java:111-112` 的 `MESSAGE_ACCEPT_ENCODING_KEY`）；grpc-go 不发 ⇒ **库级**，非本域行为 |
| 2 | **未注册方法的 description 措辞** | Go：`unknown method DoesNotExist for service favorite.FavoriteService`；RAG：`Method not found: favorite.FavoriteService/DoesNotExist` | 两侧 `code` 都是 `UNIMPLEMENTED`（**语义一致**）；措辞由 gRPC 库决定（grpc-java 的 `io/grpc/internal/ServerImpl.java:541-543`） |
| 3 | **未注册服务的 description 措辞** | Go：`unknown service favorite.NoSuchService`；RAG：`Method not found: favorite.NoSuchService/DoesNotExist` | 同上 |

⚠️ **第 2/3 条的措辞归因已于 2026-10-25 第十四轮查清并修正** ⇒ 详见 §22.2。
⚠️ **本节原记的「Go 侧 `unknown service /favorite.FavoriteService`（带前导斜杠）」是探针自己把
`:path` 拼坏造成的假象，已被推翻** ⇒ 上表的措辞值按修正后的实测**重新冻结**。
⇒ ⇒ 本探针**只断言「status code 相同」**（expected.txt 的 `SAME_CODE` 档），
**不对措辞内容下结论** —— 因为措辞**由 gRPC 库决定**，不是本域行为。

### 20.5 percent-encoding 的**源码级**量化（wire 层，本域不可观测）

`grpc-message` 在 wire 上是 percent-encoded 的，两个实现的**转义边界差 1 个字节**：

| 实现 | 条件 | 出处 |
|---|---|---|
| grpc-go | 转义当 `b < 0x20 \|\| b > 0x7E \|\| b == '%'` ⇒ **`~`(0x7E) 不转义** | `google.golang.org/grpc@v1.81.1/internal/transport/http_util.go:256`（函数体 :227-265） |
| grpc-java | 转义当 `b < ' ' \|\| b >= '~' \|\| b == '%'` ⇒ **`~`(0x7E) 转义成 `%7E`** | `io/grpc/Status.java:591-593`（marshaller `StatusMessageMarshaller` :573-621） |

⇒ **对本域无影响，两条理由**：
1. 本域 24 条文案里**没有 `~`**（`service/favorite/common/errmsg.go:43-76` 的 `codeMsg`）⇒ wire 上不可观测；
2. 即便有，两侧**客户端都会 percent-decode**（grpc-go `http_util.go:268-299`、grpc-java `Status.java:623+`）
   ⇒ **客户端看到的 description 相同** ⇒ 对「文案反查业务码」**零影响**。

⇒ **结论**：这属于 **L0（帧层）**，**库级、客户端不可观测** ⇒ **不做比对**，但**已量化到行号**（旧判定只有一句「库级差异」，没有边界）。

### 20.6 为什么本轮能推翻「环境受限」

| 事实 | 出处 |
|---|---|
| `BuildTarget()` 优先级 = **Endpoints > Target > Etcd** ⇒ 填 `Endpoints` **完全不需要 etcd** | go-zero `zrpc/config.go:92-114` |
| `if c.HasEtcd()` 才注册 etcd ⇒ `Etcd.Key` 留空就不注册 | go-zero `zrpc/server.go:42-43` |
| `RpcClientConf.NonBlock` 默认 `true` ⇒ **启动不需要对方在线** | go-zero `zrpc/config.go`（`RpcClientConf`） |
| 7 个 RPC 里**只有 `internal/logic/helper.go`** 引用 `UserRpc` / `ArticleRpc` ⇒ **6 个 RPC 不需要任何外部 rpc** | 真源 `service/favorite/rpc/internal/logic/helper.go` |
| Go 侧 schema 按 **sql-first** 部署顺序建（AutoMigrate 建不出 CHECK / 触发器 / `favorite_legacy_fact_marker`） | 真源 `internal/model/db.go:20-41` + `DELIVERY_ACCEPTANCE.md:7` |

⇒ **既不需要 fake etcd，也不需要桩**：探针把 `UserRpc` / `ArticleRpc` 指向**死地址** `127.0.0.1:1`，
顺带把「下游不可用」这条契约分支变成被测对象。

### 20.7 U7 状态更新

| 项 | 旧状态（第十三轮之前） | 新状态 |
|---|---|---|
| 「帧层」 | 🔴 未验（且判定为「库级、不属本域」） | ✅ **拆成三层**：L0 **不做**（已量化到行号）；L1 **已双侧活体对拍**；L2 早已闭 |
| rpc 侧整域双侧活体对拍 | 🔴 环境受限 | ✅ **局部活体对拍 6 项 → 9 项**（新增：gRPC status code / description / 消息字节 / metadata key 集合） |
| 本域缺陷 | — | **0 条新缺陷**（33 条 SAME）⇒ 本域 gRPC 契约**未发现漂移** |
| 库级差异 | 一句「库级差异」 | **3 条量化 + 1 条源码级量化**（percent-encoding 的 `~` 边界） |

### 20.8 复现

```bash
bash tools/grpc-contract-probe/favorite/run.sh     # EXIT=0
```

⚠️ 必须带 `-am`（run.sh 已带）—— 见 [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) PIT-FAV-RPC-013。

### 20.9 探针自身的两个坑（本轮真实踩到，已记进坑档）

1. **用例顺序 = 副作用顺序** ⇒「读同一行的用例」必须排在「写同一行的用例」之前
   （第一版把「响应头全集」用例放在最后 ⇒ 它读的收藏夹已被改名用例改过 ⇒ `updated_at` 变成「本次运行的时刻」
   ⇒ 冻结值每次都不同 ⇒ **假红**）。
2. **错误分支的 shape 渲染必须 null-safe** ⇒ 声明「预期成功」但服务端实际报错时，不判空会抛 NPE，
   把「两侧行为不一致」这个**真结论**变成看不懂的测试崩溃。

---

## 21. 2026-10-25 第十三轮（续）—— **api 整域活体对拍的可行性再评估**（结论：**可行，且不需要 fake etcd**）

> **本节性质**：用户提问「Go favorite-api 依赖 etcd + favorite-rpc + Redis；**也许能用桩（fake etcd / 内嵌 redis）拉起来**」
> ⇒ 本节把三个依赖**逐个查清**（查源码，不是推理），给出结论 + 实施配方。
> ⚠️ **本节只做「可行性判定 + 配方」，探针本身本轮未实施**（见 §21.5）⇒ **不冒充已完成**。

### 21.1 三个依赖逐个查清 ⇒ **无一需要「桩」**

| 依赖 | Go favorite-api 的用法（出处） | 结论 |
|---|---|---|
| **etcd** | `FavoriteRpc zrpc.RpcClientConf`（真源 `service/favorite/api/internal/config/config.go:15`），真源 yaml 用 `Etcd` 段（`etc/favorite.yaml:34-38`） | ✅ **完全不需要** ⇒ **不需要 fake etcd**<br>go-zero `zrpc/config.go:92-114` 的 `BuildTarget()` 优先级 = **Endpoints > Target > Etcd** ⇒ 探针 yaml 里改填 `FavoriteRpc.Endpoints`（指向 favorite-rpc 的实际地址）即**绕开 etcd**；<br>且 `HasEtcd()`（`zrpc/server.go:42-43`）为 false 时服务端**根本不注册** etcd |
| **Redis** | `BizRedis redis.RedisConf`（`internal/config/config.go:16`）→ `redis.MustNewRedis`（`internal/svc/service_context.go:20`）→ 黑名单中间件 `Redis.ExistsCtx`（`internal/middleware/checkblacklist_middleware.go:37`） | 🟡 **需要真 Redis**（一个容器即可），**不需要「内嵌 redis」**<br>go-zero 的 `RedisConf` 只认 `Host/Type/Pass` ⇒ 指向一个真 Redis 容器即可；<br>⚠️ 更精确：**只有带 `Authorization` 头的请求才会碰 Redis** —— 中间件 `:24-34` 在没有 token 时直接 `next(w, r)` ⇒ **不带 token 的矩阵在链路上根本不触碰 Redis** |
| **favorite-rpc** | `favoriteservice.NewFavoriteService(zrpc.MustNewClient(c.FavoriteRpc))`（`internal/svc/service_context.go:24`） | ✅ **本轮已实测可拉起** ⇒ 第十三轮已把「真源 favorite-rpc 起**真 gRPC server**」跑通（[`tools/grpc-contract-probe/favorite/`](../..)）⇒ 直接当子进程用即可 |

⇒ **⇒ 结论：可行**。用户的两个猜测（fake etcd / 内嵌 redis）**都不需要**：
**etcd 被 `Endpoints` 优先级直接绕开；Redis 用真容器即可，且无 token 的用例链路上根本不碰它。**

### 21.2 两侧配置怎么覆盖（都已有现成入口，不改代码）

| 侧 | 要覆盖的项 | 入口 |
|---|---|---|
| **Go api** | favorite-rpc 地址 | 探针 yaml 的 `FavoriteRpc.Endpoints`（新写一份 api 用的 yaml） |
| **Go api** | Redis | 探针 yaml 的 `BizRedis.{Host,Port,Pass}`（对应真源 `etc/favorite.yaml:14-16`） |
| **RAG api** | favorite-rpc 地址 | `grpc.client.favorite-rpc.address=static://127.0.0.1:<port>`（现值 `discovery:///favorite-rpc`，见 `favorite-api/src/main/resources/application.yml:77-81`） |
| **RAG api** | Redis | `REDIS_ADDR` / `REDIS_PORT` / `REDIS_PASSWORD`（同上 `:27-35`） |
| **两侧** | JWT 密钥 | Go `UserAuth.AccessSecret`（真源 `etc/favorite.yaml:10-12`）与 RAG `favorite.user-auth.access-secret`（`FavoriteApiProperties.UserAuth`）**必须填同一个值**，否则同一个 token 一边认一边不认 ⇒ 矩阵本身就会分叉 |

### 21.3 进程图（4 个应用进程 + 2 个中间件）

```
                    ┌───────────────────────── 探针编排（run.sh）─────────────────────────┐
                    │                                                                    │
  docker: postgres  │   Go favorite-rpc (真源副本) ──PG──┐                                 │
  docker: redis     │            ▲ Endpoints            │                                 │
                    │            │                       │      ┌──────────────┐            │
                    │   Go favorite-api (真源副本) ───────┘      │ RAG favorite-│            │
                    │            ▲                              │ rpc (Spring)  │            │
                    │            │ HTTP :port                  └──────┬───────┘            │
                    │      同一个 HTTP 客户端（同一套请求矩阵）        │ static://          │
                    │            │                              ┌──────┴───────┐            │
                    └────────────┼──────────────────────────────│ RAG favorite-│────────────┘
                                 └──────────────────────────────│ api (Spring) │
                                                                └──────────────┘
```

⇒ **Go 侧两进程之间用 `Endpoints` 直连**（不经 etcd）⇒ 与真源部署的**服务发现语义不同**，
但**业务编排语义相同**（同一份 logic、同一套 pb）。
⚠️ 这条差异必须登记：**服务发现本身不在本域 scope**（AGENTS.md §3.3：框架层），
探针只用来比**业务编排的可观测结果**。

### 21.4 它比已有的 `api-envelope-probe` 多验什么

| | `tools/api-envelope-probe/favorite/`（已闭） | api 整域活体对拍（待做） |
|---|---|---|
| 验的层 | **响应字节**（envelope + DTO 字段 tag），用**真 go-zero 库**跑 | **整个 HTTP 面**：路由 / method / JWT 中间件 / 黑名单中间件 / **gRPC 往返** / 状态码 / 响应头 |
| 两侧 | Go 真源（真库）vs RAG（**单侧断言**） | Go 真源 api ↔ RAG api（**同一客户端双打**） |
| 增量价值 | — | 🔴 **JWT 之后的整条链路**：favorite-api 的业务码是靠 **favorite-rpc 的 description 文案**反查的（`favoritecommon.BizCodeFromError`）⇒ **这条链路的端到端正确性目前只被 §20 的 rpc 级对拍间接覆盖**，api 级从未活体对过 |

### 21.5 ⚠️ 本轮**未实施**（显式登记，不是「已完成」）

> ✅ **2026-10-25 第十四轮已实施** ⇒ 见 §22.1（探针 `tools/api-full-parity-probe/favorite/`，EXIT=0）。
> ⚠️ **实施时与本配方有出入，如实记录**（以 §22.1 + §22.3 为准）：
>
> | 配方 | 实际做法 | 为什么改 |
> |---|---|---|
> | ① 复制 `service/favorite/api/**`（15 个文件） | **21 个** api 文件 + **1 个** `service/common/response/response.go`（api 的 handler 要用真源的 envelope） | 漏掉 `service/common/response` ⇒ 编译期直接失败 |
> | ③ 用 `SpringApplicationBuilder` 手工起两个 Spring 上下文 | 改成**两个 `java -jar` 真实进程** | 真实进程 = **可部署产物本身** ⇒ 顺带查出 §22.3 的两个启动阻塞（`@SpringBootTest` 永远查不到） |
> | ④ 请求矩阵复用 §20 的 7 个用例 | 自建 **32 个 case**（8 组） | §20 的 7 个是 **gRPC 层**的；api 层要覆盖路由 / 鉴权 / 解析 / 黑名单 / 响应头面 |
> | ⑤ 四档（SAME / SAME_CODE / OBS / SKIP） | 扩成**五档**（加 `SAME_BODY` / `SAME_STATUS`） | Go 的 401 是**空 body** ⇒ 取不到业务码；405 的 Content-Type 两侧不同 ⇒ 需要「只比 status / 只比 body」这两档 |
>
> ⚠️ 原配方里**漏掉**的三件事（实施时踩到）：
> ① **JWT token 必须由 Go 侧签**（探针的 `cmd/token`）⇒ 两侧拿到**逐字节相同**的 token；
> ② **RAG 的两个模块的 Nacos 处置相反**（见 PIT-FAV-RPC-019）；
> ③ **RAG 的两个模块都需要修 pom 才发现得了启动阻塞**（见 PIT-FAV-RPC-018）。

---

## 22. 2026-10-25 第十四轮（`align/favorite` 工作树 · 承 `b0b2d6f`）—— **api 整域活体对拍落地** + **B-1 归因闭合** + **2 个启动阻塞**

> 本轮做三件事：
> ① 第十三轮 §21.5 登记为「待做」的 **api 整域活体对拍** —— **已实施**；
> ② 第十三轮 §20.4 登记为「**归因未查清**」的那条 —— **已闭合**（并**推翻了原结论**）；
> ③ 顺带查出 **2 个 RAG 侧启动阻塞**（favorite-rpc / favorite-api **从未被验证能以真实进程启动**）。

### 22.1 ✅ api 整域双侧活体对拍（探针 `tools/api-full-parity-probe/favorite/` ⇒ EXIT=0）

| 项 | 值 |
|---|---|
| 补的缺口 | [`api.md`](./api.md) §4 第一行「🔴 未做：双侧活体对拍（真 Go favorite-api vs RAG）」 |
| 验的层 | **整个 HTTP 面**：路由 / method / JWT 中间件 / 黑名单中间件 / **gRPC 往返** / 状态码 / 响应头 |
| 矩阵 | **32 个 case**（鉴权 6 · 路由 3 · 解析 7 · 业务 7 · 只读 4 · 响应头面 1 · 写入 3 · 写入生效验证 1） |
| 结果 | **24 SAME** / **1 SAME_CODE** / **2 SAME_BODY** / **4 SAME_STATUS** / **1 OBS** ⇒ **0 条新缺陷** |
| Go 侧 | 真源 `service/favorite/api`（22 个逐字节复制件 + sha256 校验）+ 真源 `service/favorite/rpc`（42 个，**复用同域 rpc 级探针的复制件**）⇒ 真 `rest.MustNewServer` + 真 `zrpc.MustNewServer` + 真 PG |
| RAG 侧 | `java -jar favorite-api.jar` + `java -jar favorite-rpc.jar`（**可部署产物本身**）+ 真 Flyway + 真 MyBatis + 真 PG |
| 中间件 | 真 PG（两个库：`godb` / `first_db`）+ 真 Redis（**两侧共用** ⇒ 黑名单 key 在同一命名空间） |
| 客户端 | **同一个 `java.net.http.HttpClient`** 打两侧 ⇒ 消掉客户端自身差异 |

**为什么这条链值得单独活体对**（本探针的增量价值）：

> favorite-api 的**业务码不在 gRPC status 里，而在 description 文案里**
> （Go `favoritecommon.BizCodeFromError` 反查 ⇒ RAG `FavoriteGrpcError.bizCodeFromError`）
> ⇒ 「api 的业务码对不对」**只有真的把请求从 HTTP 打通到 gRPC 再打回来**才能验。
> 在此之前它只被 §20 的 rpc 级对拍**间接**覆盖 —— api 级从未活体对过。

⇒ **实测证据**：业务组 **7 条**全部**逐字节一致**，包括
「下游 user-rpc 缺席 ⇒ rpc 返回 INTERNAL/5001 ⇒ api 经文案反查还原成 5001 ⇒ 包成 envelope」这条全链。

**🔴 变异验证（防假绿）** —— 把 `FavoriteGrpcError.bizCodeFromMessage` 改成**恒返回 null**
（= 彻底打掉「文案反查」）：基线 **EXIT=0** ⇒ 变异后 **EXIT=1**，**4 个 case 转红**：

| case | Go（真源） | 变异后的 RAG | 说明 |
|---|---|---|---|
| `b03_delete_folder_not_found` | `5201` 收藏夹不存在 | `5002` not found | NotFound 走标准码兜底 |
| `b04_delete_item_not_found` | `5301` 收藏记录不存在 | `5002` not found | 同上 |
| `b05_update_folder_not_found` | `5201` 收藏夹不存在 | `5002` not found | 同上 |
| `w02_delete_item` | `5304` 收藏暂不可撤回 | `1015` 服务繁忙 | FailedPrecondition 不在白名单 ⇒ 落 `default` |

⇒ 转红的**恰好是「业务码只能靠文案反查」的那几条** ⇒ **探针确实在测真 gRPC wire 上的反查**，不是假绿。
（变异已立即还原，`git diff` 为空。）

⚠️ **仍未验的部分**（诚实登记）：**create 成功路径**没被测到 —— 本探针**故意不接** user-rpc ⇒
`ensureUserExists` 必失败 ⇒ create 落「下游不可用」5001。
⇒ 但 create 成功路径的**出参字节契约**（`folderId` 以字符串出参）已由 `tools/api-envelope-probe/favorite/` 覆盖（真 go-zero 库实测）。

### 22.2 ✅ B-1 闭合：「未知服务 vs 未知方法」的措辞差 = **探针自己把 `:path` 拼坏**

第十三轮 §20.4 记的是「Go 侧 `unknown service /favorite.FavoriteService`（**带前导斜杠**）」，
并标注「**归因未查清**」。**本轮查清了 ⇒ 原结论被推翻**：

| 环节 | 事实 | 出处 |
|---|---|---|
| ① grpc-java 的 `generateFullMethodName(service, method)` | = `service + "/" + method`（**不**补前导斜杠） | 字节码实测 |
| ② netty 客户端写 `:path` | **自己补一个 `/`** ⇒ 线上路径恒为 `/service/method` | 实测锚点见下 |
| ③ 探针却手写成 `"/favorite.FavoriteService/DoesNotExist"` | **多带一个斜杠** ⇒ 线上路径成 **`//…`（双斜杠）** | 探针自己的代码 |
| ④ grpc-go v1.81.1 解析 | `sm = sm[1:]` **只剥一个**前导 `/` ⇒ service 名 = `"/favorite.FavoriteService"`（**带斜杠**） | `server.go:1826` |
| ⑤ 结果 | 查 `s.services` 注册表**落空** ⇒ 走 `unknown service` 分支，**而不是**「已注册服务 + 未注册方法」的 `unknown method` 分支 | `server.go:1869-1874` |

**实证锚点**（怎么证明客户端确实会补斜杠）：同一矩阵里的 `c34_health_check` 描述符**本来就不带**
前导斜杠 ⇒ 线上路径合法 ⇒ 真源 go-zero 注册的 health 服务**正常应答**（`body=hex:0801` = SERVING）。

⇒ **修正后的真实库级差异**：

| 用例 | Go（grpc-go） | RAG（grpc-java） |
|---|---|---|
| 未注册**方法**（服务已注册） | `unknown method DoesNotExist for service favorite.FavoriteService` | `Method not found: favorite.FavoriteService/DoesNotExist` |
| 未注册**服务** | `unknown service favorite.NoSuchService` | `Method not found: favorite.NoSuchService/DoesNotExist` |

⇒ **grpc-go 区分「未知方法 / 未知服务」两种措辞；grpc-java 两种情形共用一种措辞**
⇒ **grpc-java 丢掉了「方法 vs 服务」的区分**（且回显的是**不含前导斜杠**的全方法名）。
⇒ 两侧 `code` 都是 `UNIMPLEMENTED` ⇒ **语义一致** ⇒ 仍是库级差异，不是不一致。

⇒ 已修探针（描述符去掉前导斜杠）+ **重新冻结** `expected.txt` 的 c35 / c36 两条。
⇒ 元教训 ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-017**。

### 22.3 🔴🔴 两个**启动阻塞**（RAG 侧真实缺陷，**已修**）：本模块**从未被验证能以真实进程启动**

> AGENTS.md §0 的「每个模块接手的第一件事仍是『先让它真的启动一次』」
> —— 本轮第一次真的起，**两个模块都起不来**。

| # | 现象 | 根因 | 修法 | 落点 |
|---|---|---|---|---|
| **1** | `APPLICATION FAILED TO START`：`no ServletWebServerFactory bean defined` | pom 只有 `spring-boot-web-server` + `jakarta.servlet-api`，**没有** `spring-boot-starter-web`（无内嵌 Tomcat）⇒ Boot 默认按 SERVLET 起 | 启动加 `--spring.main.web-application-type=none`（对齐 Go 的「纯 gRPC、零 HTTP 端口」，见 DEV-FAV-RPC-004） | `service/favorite/favorite-rpc/pom.xml` 的依赖说明 + 探针启动参数 |
| **2** | `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` | devh 的 `GrpcClientAutoConfiguration.shadedNettyGrpcChannelFactory`（3.1.0.RELEASE:160）在**运行期**引用它，而本模块**直接声明**的 `grpc-inprocess` 是 **test scope** ⇒ **直接声明覆盖了传递来的 compile**（maven「最近者优先」）⇒ 运行时 classpath 里没有 | scope 改 **runtime** | `favorite-rpc/pom.xml` |

⚠️ **这两个缺陷被 `@SpringBootTest` 完全掩盖**（`webEnvironment=NONE` + test classpath）
⇒ 「**测试全绿**」与「**jar 能跑**」之间有一道没人测过的缝
⇒ `tools/difftest/check-jar-executable.sh` 只查 `Main-Class`，**查不出少一个运行时依赖**。
⇒ 元教训 ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-018**。

⚠️ **连带的一条部署形态事实**（本轮实测）：**favorite-rpc 不能关掉 Nacos discovery**
（`FavoriteNacosRegistration` 把 `NacosServiceManager` 设成了**构造器硬依赖** ⇒
`--spring.cloud.nacos.discovery.enabled=false` ⇒ 上下文起不来）；
而 **favorite-api 恰恰相反**（走 spring-cloud 自动注册 ⇒ Nacos 不可达时 failFast ⇒ 必须关）。
⇒ 两侧处置**相反** ⇒ 坑档 **PIT-FAV-RPC-019**。

⚠️ **第 2 条的跨域清单（本域只读 ⇒ 归 `global`）** —— 🔴 **2026-10-25 第十五轮实测推翻本节的原判定**
（原表说「13 个模块的**可部署 jar** 都起不来」，且把「**没声明** grpc-inprocess」判成缺陷 ⇒ **条数与方向都错了**）：

**实测口径**（`mvn -o dependency:tree -Dincludes=io.grpc:grpc-inprocess`，全仓 25 个模块逐个看）：

| 形态 | 判定 | 实测模块 |
|---|---|---|
| **直接声明 `grpc-inprocess` 且 scope=test** | 🔴 **真缺**（直接声明**覆盖**了传递的 compile ⇒ 运行时没有） | `article/article-rpc` · `like/like-rpc` · `task/task-rpc` |
| **直接声明 `grpc-inprocess` 且 scope=compile/runtime** | ✅ 有 | `favorite/favorite-api` · `favorite/favorite-rpc`（本域第十四轮已修）· `users/{user/user-api,user/user-rpc,admin/admin-api,admin/admin-rpc}` · `article/article-api` · `hot/{hot-api,hot-rpc}` · `message/{message-api,message-rpc}` · `like/like-api` · `task/task-api` · `follow/{follow-api,follow-rpc}` · `comment/{comment-api,comment-rpc}` |
| **完全没声明** | ✅ **有**（devh starter 的 `grpc-inprocess` 是 **compile** ⇒ 传递下来就有） | 同上一行的 api 模块 |
| **直接声明成 test 的 `-common` 库** | 🟡 **不是可执行模块** ⇒ **不影响 jar 启动**（运行时由应用模块提供）；但它们**自己**跑测试/作为客户端用时会缺 | `like/like-common` · `follow/follow-common` · `comment/comment-common` · `favorite/favorite-common` |

⇒ **可执行应用模块真缺 = 3 个**（`article-rpc` / `like-rpc` / `task-rpc`）⇒ **它们的可部署 jar 起不来**。
⇒ ⚠️ **原表把「没声明」判成缺陷是方向性错误** —— 实测证明**没声明反而是对的**（传递 compile）；
真正的病根是「**自己声明成 test scope，把传递来的 compile 顶掉了**」。
⇒ ⚠️ **本域第十四轮也有一次同样的误判**（把 favorite-api 的「没声明」当成第二个受害者）⇒
**第十四轮那次 `NoClassDefFoundError` 的现场日志证明：它出在 `favorite-rpc` 启动时**（`✗ RAG favorite-rpc 端口 … 超时未就绪`）
⇒ favorite-api **本来就有** compile 的传递依赖（改前 `dependency:tree` 实测 = compile）⇒ **不是受害者**。
⇒ 本域只登记不动别域文件（AGENTS.md §5.3）⇒ 收口在 `global`。

### 22.4 🔴 两条**跨域**发现（本域**只登记**，收口在 `global`）

| # | 发现 | 落点 | 为什么本域不能改 |
|---|---|---|---|
| **1** | **405 响应多一个 `Content-Type: application/json;charset=utf-8`**（Go 是**裸状态码**：既无 body 也无 Content-Type） | `service/common` 的 `GoStyleRouterErrorResponses.methodNotAllowed` —— 它的 javadoc 明写「容器不会补 Content-Type」，**实测被证伪**（Spring 仍会选出一个 MediaType） | `service/common/**` 是**共享面** ⇒ 本域**只读** |
| **2** | Go 的响应**多一个 W3C `traceparent` 响应头**（go-zero 的 otelhttp 注入 ⇒ 响应回显活动 trace context）；RAG 侧没有 | 同属**可观测层**（RAG 侧落点在 `service/common` 的 `HttpMiddlewareFilter`） | 同上 |

⇒ 探针里：**#1 用 `SAME_BODY` 档**（status 与 body 两侧全等，只有 Content-Type 不同）、
**#2 用 `OBS` 档**（单独量化，不混进业务用例）⇒ **真差异被量化、被冻结、可见**，但没被「顺手修掉」。

### 22.5 ✅ A-2：写入方盘点工具**拓宽覆盖面** + 产出**人工确认清单**

第十三轮 §18.2 的「可动项 B」已落地为机检工具，但只覆盖 `favorite_fact_outbox` 的**字面量 SQL**。
本轮拓宽：

| 项 | 拓宽前 | 拓宽后 |
|---|---|---|
| 覆盖的表 | 1 张（`favorite_fact_outbox`） | **2 张**（+ `favorite_item` ⇒ V6 的额外 CHECK 那条，DEV-FAV-RPC-007） |
| **ORM 写入路径** | ❌ **看不见**（MyBatis-Plus 的 `mapper.insert(entity)` 不含 `INSERT INTO` 字面量）⇒ **假绿漏洞** | ✅ **机器自动判定**：找 `interface XxxMapper extends BaseMapper<该表实体>` ⇒ 有则**额外扫** `.insert(`/`.save(` 调用点；无则明确写「该表只有显式 SQL 写入 ⇒ 字面量扫描即**全覆盖**」 |
| `tools/**` 下的写入 | 整目录排除 | **登记但不判失败**（探针设施不是部署产物）⇒ 报告里可见 |
| 机器的边界 | 散在 stdout 的一句话 | **独立清单文件** ⇒ `tools/outbox-writer-audit/favorite/CHECKLIST.md`（6 条可逐条勾掉的确认项 + 「去哪看 / 确认口径」） |

⇒ 机检结果（**EXIT=0**）：outbox **9 个 INSERT 站点全部已绑两列** / item **12 个 INSERT 全部域内**
/ **两张表都无 ORM 写入路径**（机器判定）/ **域外写入方 0 个**。
⇒ ⚠️ **机器看不见的仍需人工确认一次**（人工 psql / 报表 / 仓库外脚本 / 另一个服务 / DBA 触发器）
⇒ 清单已落地 ⇒ 「上线前人工确认一次」从一句话变成**可勾选的 6 条**。

### 22.6 本轮验证

| 项 | 结果 |
|---|---|
| `tools/api-full-parity-probe/favorite/run.sh`（新） | **EXIT=0** · 32 case · 24 SAME / 1 SAME_CODE / 2 SAME_BODY / 4 SAME_STATUS / 1 OBS |
| ↳ **变异验证** | 打掉「文案反查」⇒ **EXIT=1** · **4 个 case 转红**（5201→5002 · 5301→5002 · 5304→1015）⇒ 非假绿 |
| `tools/grpc-contract-probe/favorite/run.sh` | **EXIT=0**（c35 / c36 重新冻结后复跑） |
| `tools/outbox-writer-audit/favorite/check-outbox-writers.py` | **EXIT=0**（扩面后复跑） |
| 本域全量测试 | ✅ **BUILD SUCCESS** —— `mvn -o -pl service/favorite/favorite-{proto,common,rpc,api} -am test`：<br>favorite-api **90 例**（含 1 skip = 本轮新探针，无 env 时自动跳过）· favorite-rpc **84 例**（含 1 skip = gRPC 契约探针）· favorite-common **29 例** · service/common **174 例** · users-common **33 例** ⇒ **全绿，0 失败 0 错误** |
| 断言审计器 | `python3 tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **EXIT=0**（586 锚点 · 真源 483 / 依赖 81 / **悬空 0 / 越界 0**） |
| 台账自检 | `python3 tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **EXIT=0** |
| 写入方盘点 | `python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py` ⇒ **EXIT=0**（扩面后复跑） |
| Tomcat 三旋钮 / Flyway 账本 / proto 静态 / jar 可执行性 | 全 **EXIT=0** |
| 共享面（`docs/DRIFT/_INDEX.md` / `_MANIFEST.json` / `docs/Intentional_Deviations/_INDEX.md` / `docs/Pitfalls/_INDEX.md` / `AGENTS.md` / `docs/DRIFT/_cross/`） | **零改动** |

### 22.7 ⚠️ 合并后需主会话统一更新的共享面（本域**未碰**）

| 文件 | 要改什么 |
|---|---|
| `docs/DRIFT/_INDEX.md` | favorite 域状态：api 侧「🔴 未做」⇒「✅ 已验（32 case 活体对拍）」；新增 2 条**跨域**收口项（405 的 Content-Type、Go 的 `traceparent` 响应头）；本域探针清单加 `api-full-parity-probe` |
| `docs/Pitfalls/_INDEX.md` | 坑档新增 **3 条**：**PIT-FAV-RPC-017**（grpc-java 的 fullMethodName 不带前导斜杠）/ **PIT-FAV-RPC-018**（`@SpringBootTest` 掩盖「模块能不能真起」）/ **PIT-FAV-RPC-019**（rpc 与 api 的 Nacos 处置相反） |
| `docs/DRIFT/_MANIFEST.json` | 重新生成（`python3 tools/drift-move.py verify`） |
| `docs/Intentional_Deviations/_INDEX.md` | ⚠️ **本轮无新增签字项** ⇒ 已签字条数**不变**（仍是 58；撤销计入则 60）⇒ 合并时只需确认未被误改 |
| `docs/Pitfalls/_INDEX.md` | （第十四轮**收尾**追加）坑档再增 **1 条**：**PIT-FAV-RPC-020**（探针的「环境红」不是「被测代码红」—— 等待过短 / 探活探错目标 ⇒ 假红）⇒ **本域坑位 19 → 20 条** |
| `docs/DRIFT/_INDEX.md` | （第十四轮**收尾**追加）本域探针清单加 `probe-gate`（**PG 就绪守门网**，新工具）⇒ §23 |
| `docs/DRIFT/_INDEX.md` | （第十五轮追加）本域**台账复核工具**清单加 `ledger-lint/check-ledger-vs-reality`（新工具）⇒ §24.3 / §24.4 |
| `docs/DRIFT/global/` | 🔴 **第十五轮复核的更正项（必须转交 `global`）** —— §22.3 那条**跨域清单已被实测推翻**：<br>· 原写「**13 个模块**的**可部署 jar** 都起不来」⇒ 🔴 **实际只有 3 个可执行应用模块真缺**：`article/article-rpc` · `like/like-rpc` · `task/task-rpc`（其余 api 模块实测都是 **compile**）<br>· 原把「**没声明** grpc-inprocess」判成缺陷 ⇒ 🔴 **方向性错误**：devh starter 的 inprocess 是 **compile** ⇒ **没声明反而有**；真病根是「**自己声明成 test scope 把传递来的 compile 顶掉**」<br>· 另有 **4 个 `-common` 库**声明成 test scope（**不是可执行模块** ⇒ 不影响 jar 启动，但它们自己跑测试/当客户端用时会缺）<br>⇒ 本域**只登记不动别域文件**（AGENTS.md §5.3）⇒ **收口在 `global`**；证据 = 全仓 `mvn -o dependency:tree -Dincludes=io.grpc:grpc-inprocess` |

> ⚠️ **本轮新增的 2 条跨域项**（§22.4）收口在 `global`：
> ① **405 响应多一个 `Content-Type`**（落点 `service/common` 的 `GoStyleRouterErrorResponses` —— 它的 javadoc 说「容器不会补 Content-Type」，**实测被证伪**）；
> ② **Go 响应多一个 W3C `traceparent`**（可观测层）。
> ⇒ 两条**本域只登记不修**（`service/common/**` 是共享面）。
> ⚠️ 另有一条**更广的跨域清单**（§22.3 的 `grpc-inprocess` 运行时缺失 ⇒ **全仓 13 个别域模块**同样起不来）⇒ 归 `global`。

---

## 23. 第十四轮收尾 · 🔴 **探针健壮性加固**（消除「环境红」假红）

> **建章时间**：2026-10-25（第十四轮交付后的收尾）
> **性质**：**零行为改动** —— 改的全是 `tools/*/favorite/run.sh`（探针设施）+ 新增守门网，
> **不碰** `service/favorite/**` 的任何一行 Java / SQL。
> ⚠️ **本域 0 条新漂移** —— 本章记的是**探针基建缺陷**，不是 Go ↔ RAG 差异。

### 23.1 现象：单跑绿、连跑红

第十四轮把 favorite 域 9 个探针**串行连跑**（机器 load avg ≈ 10，别的会话在跑）：

| 探针 | 单跑 | 连跑 |
|---|---|---|
| api 整域对拍 · gRPC 契约对拍 · api envelope · DDL · proto wire · authority path · metrics · errmsg | ✅ EXIT=0 | ✅ EXIT=0 |
| **authority live 对拍** | ✅ EXIT=0 | ❌ **EXIT=2 · `PG 没起来`** |

⇒ **同一个 commit、同一份代码**，单跑绿、连跑红 ⇒ **第一反应不该是「被测代码坏了」**。

### 23.2 根因（两条叠在一起）

1. **探活探错了目标** —— `pg_isready -U postgres`（**没带 `-h`**）⇒ 走 **unix socket**。
   PG 官方镜像初始化阶段只起一个**临时 server**，它**只监听 unix socket**（`listen_addresses=''`）
   ⇒ socket 探活会**假就绪**；紧接着的 `CREATE DATABASE` 才去撞「系统正在启动」而随机失败。
   ⚠️ 4 个探针里**只有 `authority-live` 探 socket**，另外 3 个（`ddl-parity` / `grpc-contract` /
   `api-full-parity`）**本来就带 `-h`** ⇒ 这次假红**只有它中招**，不是全部。
2. **等待上限太短** —— `for _ in $(seq 1 40)`（**40s**）⇒ 机器高负载时 PG 的 `initdb` 本身就要几十秒
   ⇒ **还没起来就判死**。

⇒ ⚠️ **反向风险更大**：socket 假就绪后若 `CREATE DATABASE` 恰好蒙对 ⇒
探针会**带着一个没真正就绪的 PG 跑下去** ⇒ **那一轮的结论本身不可信**（不只是报错不可信）。

### 23.3 修法：4 个起 PG 的探针**统一**加固

新工具：[`tools/probe-gate/favorite/`](../../../../tools/probe-gate/favorite/README.md)

| 探针 | 原等待 | 改后 |
|---|---|---|
| `authority-live` | 40s · **探 socket** | **180s · 探 TCP** · 重建重试 · 容器退出即放弃 |
| `ddl-parity` | 60s · 探 TCP | **180s · 探 TCP** · 重建重试 · 容器退出即放弃 |
| `grpc-contract` | 60s · 探 TCP | **180s · 探 TCP** · 重建重试 · 容器退出即放弃 |
| `api-full-parity` | 60s · 探 TCP | **180s · 探 TCP** · 重建重试 · 容器退出即放弃 |

统一后的三件套（4 份 `run.sh` **逐条一致**）：
- ① **探 TCP**（`-h 127.0.0.1`）—— 不是 socket；
- ② 上限 **180s**；
- ③ 没就绪 ⇒ **重建容器再来一次**（容器起不来 = 资源竞争 / 端口映射失败）；
- ④ **容器自己退出 ⇒ 立刻放弃等待**（`--rm` ⇒ 退出会被删掉 ⇒ `docker inspect` 失败 ⇒ 别白等满超时）。

顺带（同类脆弱点，一并收紧）：
- `api-full-parity` 的 **Redis** 等待 30s → **60s** + 「容器退出即放弃」；
- `grpc-contract` 的 **Go server 端口**等待 60s → **120s**（Go server 启动要连 PG 跑 InitDB）。

🔴 **另有一处同源脆弱点（端口归属）—— 与 §23.1 是同一类「环境红」，但后果更重**：
`api-full-parity` 与 `grpc-contract` 原先用 `bind(("127.0.0.1", 0))` 拿 **OS 随机端口**
⇒ 落在**临时端口段 49152–65535** ⇒ **正是 Docker 随机发布容器端口的段**
⇒ macOS 上 `127.0.0.1:P` 会**抢占**服务绑的 `0.0.0.0:P`
⇒ 探针客户端会**打到别人的服务**（本机实测那次打到了 PostgreSQL）
⇒ ⚠️ 探针侧比 JUnit 侧更危险：`wait_port` **只验「连得上」** ⇒ **连到别人的服务也算就绪**
⇒ ⇒ 探针结论会**整个不可信**（而不只是报错）。
⇒ 已一并改成**专用端口段 21400–21599 + 双绑定自检**（`0.0.0.0:P` 且 `127.0.0.1:P` 都要能绑）。
⇒ 完整实证与判据 ⇒ [`docs/DRIFT/favorite/api.md`](api.md) §9 · [`docs/Pitfalls/favorite/api.md`](../../Pitfalls/favorite/api.md) **PIT-FAV-API-008**。

### 23.4 守门网（**新工具**）—— 加固自己不能变成橡皮章

`tools/probe-gate/favorite/check-pg-readiness.sh`：**从每个 `run.sh` 原样 `sed` 出真函数**
（`awk '/^wait_pg_tcp\(\) \{/,/^\}$/'`，**不重写、不 mock**）⇒ 跑 3 个场景：

| # | 场景 | 期望 | 抓什么 |
|---|---|---|---|
| ① | 容器**根本不存在** | 返回 `1` | **假就绪** ⇒ 探针带空环境往下跑 ⇒ **假绿** |
| ② | 容器**启动即退** | 返回 `1` 且 **< 30s** | **白等满 180s 超时** |
| ③ | **真 PG 容器** | 返回 `0` | 把真就绪判成没就绪 ⇒ 探针永远跑不起来 |

⚠️ **4 份副本漂移也会被 ①③ 抓到**（域内不共享 `tools/` 根 ⇒ 4 份副本是既成事实）。

⇒ **变异验证**（守门网自己不是橡皮章）：把 `authority-live` 的 `wait_pg_tcp` 改成**永远 `return 0`**
⇒ 守门网 **EXIT=1**，且**只**报 `authority-live` 这一份红（场景① 假就绪 + 场景② 误判就绪），
其余 3 份仍绿 ⇒ **能定位到具体副本**，不是「全红」。

### 23.5 本轮验证

| 项 | 结果 |
|---|---|
| `tools/probe-gate/favorite/check-pg-readiness.sh`（新） | ✅ **EXIT=0**（4 个探针 × 3 场景 = 12 项全过） |
| ↳ **变异验证** | `wait_pg_tcp` 永远 `return 0` ⇒ **EXIT=1 · 2 项红**（只报被改的那一份）⇒ 非假绿 |
| `tools/probe-gate/favorite/check-port-ownership.sh`（新） | ✅ **EXIT=0**（2 个探针 × 4 场景 = 8 项全过） |
| 本域 9 个探针**串行连跑** | ✅ **全 EXIT=0**（authority-live 30s · ddl 9s · grpc-contract 34s · api-full-parity 52s · api-envelope 17s · proto-wire 9s · authority-path 13s · metrics 1s · errmsg 5s） |
| 本域全量测试 | ✅ **BUILD SUCCESS** —— `mvn -o -pl service/favorite/favorite-{proto,common,rpc,api} -am test`：<br>favorite-api **94 例**（含 1 skip；**新增 4 例**端口归属守门测试）· favorite-rpc **84 例**（含 1 skip）· favorite-common **29 例** · service/common **174 例** · users-common **33 例** ⇒ **全绿，0 失败 0 错误** |
| `service/favorite/**` 的**产品代码** | **零改动**（本轮只动 `tools/` 与 **测试**代码 ⇒ `service/favorite/**/src/main/**` 一行没动） |

### 23.6 ⚠️ 推广面（**归 `global` 收口域**，本域只写自己这份）

- `tools/difftest/` 下**别的域**的脚本有**同款**写法（`run-message-ddl-parity.sh` /
  `exp-go-concurrent-automigrate.sh` 里就带着**同款注释**与同款坑）⇒ **大概率普遍存在**；
- `hot` 域**已记过同款**（「并行跑时 Testcontainers 容器起不来 ⇒ 看起来像回归其实不是」）
  ⇒ 两条合起来说明这是**探针基建约定层**的问题，不是某一域的个别 bug；
- ⇒ **别的域需各自复制 `tools/probe-gate/<域>/`** ⇒ 登记在 `global`。

⇒ 元教训全文 ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) 的 **PIT-FAV-RPC-020**。

---

## 24. 2026-10-25 第十五轮（`align/favorite` 工作树 · 承 `dccb234`）—— 📌 **域内「可修漂移 / 未知项」盘点** + 🆕 **U8**

> 用户问「现在 favorite 域内可修漂移、未知项有哪些」⇒ 本节给出**带证据的盘点**，
> 并把其中**唯一一条真正的业务路径缺口**从行内备注提升为**编号未决项 U8**。
> ⚠️ **本节不改产品代码** ⇒ **0 条新漂移**。

### 24.1 盘点结论（四类）

| 类别 | 条数 | 内容 | 落点 |
|---|---|---|---|
| **A · 已识别且可修的漂移** | **0** | 已修：rpc 6 处 L2 + 2 处真 bug；api 4 处（L2×2 + javadoc 假断言×2 + L1×1）；DDL 14 处 DEFAULT + 3 条索引（**性能非行为**）<br>已签字 **9** 条（API-001/002 · COMMON-001/002 · RPC-001/003/004/006/007）· 已撤销 2 条（RPC-002/005）· 🏷 候选 **0** | §2 / §3 · `api.md` §2 · `docs/Intentional_Deviations/favorite/` |
| **B · 域内能补的证据缺口** | **1 + 1** | **B1 = U8**：create / create_item 的**成功路径**未双侧对拍（⇒ 见 §24.2）<br>**B2**：V7 上线前的 **6 条域外写入方人工确认**（**流程项，不是代码项**） | §24.2 · [`tools/outbox-writer-audit/favorite/CHECKLIST.md`](../../../../tools/outbox-writer-audit/favorite/CHECKLIST.md) |
| **C · 跨域（本域只读）** | **5** | ① 容器层参数上限口径（与 hot 域 `DRIFT-HOTAPI-030` 同一格）② 405 多一个 `Content-Type`（落点 `service/common`，其 javadoc 被实测证伪）③ Go 多一个 `traceparent`（落点 `service/common` 的 `HttpMiddlewareFilter`）④ **13 个别域模块**运行时缺 `grpc-inprocess` ⇒ 可部署 jar 起不来 ⑤ `like-api` 的熔断注释 | §22.3 / §22.4 · `api.md` §3 / §9.5 ⇒ **收口在 `global`** |
| **D · 库级 / 不可观测（明确「不做」）** | **3** | ① gRPC **措辞**（grpc-go 区分未知方法/未知服务，grpc-java 共用一种）② gRPC **响应头 key 集合**（grpc-java 多 `grpc-accept-encoding` / `grpc-encoding`）③ percent-encoding 的 `~` 边界差 1 字节（L0 帧层；本域 24 条文案**无 `~`** ⇒ 客户端不可观测） | §20.4 / §20.5 ⇒ 两侧 `code` 语义一致 ⇒ **属库级，不是不一致** |

⇒ **域内没有「已知但没改」的语义漂移了**；剩下的只有 **U8**（一条路径）+ **B2**（一条流程）+ 跨域与库级。

### 24.2 🆕 **U8** · 🔴 `create_folder` / `create_item` 的**成功路径**从未被双侧对拍

| 项 | 内容 |
|---|---|
| **现象** | 两套活体对拍探针**都故意不接 user-rpc** ⇒ `ensureUserExists` **必失败** ⇒ **create 的成功路径一次都没跑到** |
| **证据（逐条）** | `tools/grpc-contract-probe/favorite/`：`c01`/`c05` = `uid=0` ⇒ `INVALID_ARGUMENT｜参数错误`；`c04`/`c09` = `user_rpc_absent` ⇒ 依赖失败分支<br>`tools/api-full-parity-probe/favorite/`：`b01`/`b02` = `downstream_absent` ⇒ `200 + code 5001｜系统内部错误` |
| ⇒ **缺口到底是什么** | 「**uid 合法 + user-rpc 在场 ⇒ 真创建成功**」这条路径，**两侧都没跑到** ⇒ 落库、id 生成、`favorite_item` / `favorite_fact_outbox` 的联动**未获双侧证据** |
| **不是什么** | **不是出参字节契约的缺口** —— `folderId` 以字符串出参等**已由 `tools/api-envelope-probe/favorite/` 覆盖**（真 go-zero 库实测）⇒ 缺的是**端到端成功路径** |
| **可动性** | ✅ **域内可闭环** —— 探针自带一个**最小 user-rpc 桩**（只需实现 `CheckUserExists` 一个方法）作为**两侧共同上游** ⇒ 就能走成功路径并双侧对拍（与 api 探针自带 JWT 签发器 `cmd/token` 同一思路）⇒ **不需要 users 域的产物** |
| ⚠️ **桩的边界（必须先说清）** | **桩 ≠ 真 user-rpc** ⇒ 这一测能证明的是「**favorite 域自己在这条路径上的行为两侧一致**」，**不能**证明「favorite 与 user-rpc 的交互契约」⇒ 期望值**不能**从真 user-rpc 取，只能由「Go 真源 favorite-rpc 的 logic + 真 PG」产出 |
| ⚠️ **代价** | 多一个常驻进程 + 桩的 `CheckUserExists` 语义要与 Go 真源的调用约定一致；否则**会把「桩的语义」当成「真源语义」测** |

### 24.3 🔁 「台账 vs 实际」复核（新工具）

| 项 | 值 |
|---|---|
| **新工具** | [`tools/ledger-lint/favorite/check-ledger-vs-reality.py`](../../../../tools/ledger-lint/favorite/check-ledger-vs-reality.py) |
| **它管什么** | 现有 `check-ledger-selfcheck.py` 管的是「**台账之间**」（指针 / 编号 / Go 源文件落点）⇒ **不管「台账说的」vs「仓库实际的」** ⇒ 本工具补这一层 |
| **做法** | **从台账正文里抽断言**（不把「真相」写死在工具里）⇒ 再**独立**从仓库算实际值 ⇒ 逐条比对 ⇒ 不一致即红 |
| **覆盖的断言类型** | 测试计数 · 已签字条数 · 🏷 候选数 · 跨域清单的条数 · 探针清单 · 端口段声明 · 「零产品代码改动」声明 · Go 源文件覆盖 |
| **为什么必须有** | 盘点当场就抓到一处**过期断言**（`api.md` 写「6 个测试类 / 89 例」，实际已是 8 类 / 94 例）⇒ 与 §16「台账体检」同款缺陷，只是**发生在数字上** |

### 24.4 🔁 本轮「台账 vs 实际」复核的**结果**（工具 + 人工双向独立）

> 工具：[`tools/ledger-lint/favorite/check-ledger-vs-reality.py`](../../../../tools/ledger-lint/favorite/check-ledger-vs-reality.py)
> 口径：**断言从台账正文里抽** ⇒ **独立**从仓库算实际值 ⇒ 逐条比对。
> ⇒ 工具与人工**各算一遍**，结论必须一致（§7「工具第一版输出不能直接当结论用」）。

**第一次跑：4 条不一致 ⇒ 逐条人工核实 ⇒ 2 条台账真错 + 2 条工具抽取 bug。**

| # | 检查项 | 台账说 | 实际 | 判定 |
|---|---|---|---|---|
| ① | favorite-api **用例数** | 「89 例」 | surefire 求和 = **94** | 🔧 **工具抽取 bug** —— 正则匹配到了 **§6 第十二轮的旧口径**（「**89 例全绿**（6 个测试类）」）⇒ 已改成**锚定 §0 的「\| 测试 \|」行** |
| ② | **grpc-inprocess 缺失模块数** | 「**13 个模块**…它们的可部署 jar 都起不来」 | **可执行应用模块真缺 = 3 个**（article-rpc / like-rpc / task-rpc） | 🔴 **台账真错（条数 + 方向都错）** ⇒ 已按 `dependency:tree` 全仓实测重写 ⇒ 见 §22.3 的更正表 |
| ③ | 全仓 `RANDOM_PORT` 暴露面 | 「7 个域 / **14 个测试类**」 | **13 个测试类** | 🔴 **台账真错**（把两个**只在 javadoc 里提到**它的支撑类算进去了）⇒ 已改 13，并写明口径 |
| ④ | 端口段（探针侧） | 探针侧 21400–21599 | 探针实际 21400–21599 | 🔧 **工具抽取 bug** —— 正则先匹配到了 Java 侧那句（「专用端口段」两处同形）⇒ 已改成锚定「同样改成\*\*专用端口段」 |

#### 🔴 最重要的一条：**第十四轮的归因翻案**（由 ② 引出）

第十四轮 §22.3 写「`grpc-inprocess` 运行时缺失 ⇒ **13 个模块**的 jar 起不来」，
并把 **favorite-api 的「没声明」也判成第二个受害者**。本轮实测（`mvn -o dependency:tree`）：

| 原判 | 实测 | 结论 |
|---|---|---|
| 「**没声明** grpc-inprocess ⇒ 缺」 | devh starter 的 inprocess 是 **compile** ⇒ **没声明反而有** | 🔴 **方向性错误** |
| 「13 个模块的 jar 起不来」 | **可执行模块真缺 = 3 个**（其余 api 模块实测都是 compile） | 🔴 **条数高估 4 倍** |
| 「favorite-api 也缺」 | 改前 `dependency:tree` = `grpc-inprocess:1.63.0:compile` ⇒ **本来就不缺**；那次 `NoClassDefFoundError` 的现场日志（`✗ RAG favorite-rpc 端口 … 超时未就绪`）证明**出在 favorite-rpc** | 🔴 **误判** |

⇒ **真机制**（这才是该记住的）：
> **直接声明会覆盖传递依赖**（maven「最近者优先」）⇒
> 「自己声明成 test scope」把传递来的 compile **顶掉** ⇒ 运行时没有；
> 而「**没声明**」是安全的。
> ⇒ **判「运行时有没有」不能看 pom 写没写，要看 `dependency:tree` 的最终作用域。**

⚠️ 连带处置：
- **favorite-api 的 pom 注释已更正**（原文把「没声明」写成缺陷 ⇒ 现改为「**加固**：显式优于隐式，不依赖 starter 的传递作用域」）；
- **favorite-api 的 runtime 声明保留**（实测它本来就有 ⇒ 这次只是把隐式变显式，**无害**）；
- **favorite-rpc 的注释无需改**（它**确实**自己声明成 test scope ⇒ 缺陷是真的）。

#### 元教训

⚠️ **第十四轮那条跨域清单，正好撞上 AGENTS.md §3.3.2 说的「无出处断言」**：
当时是**推断**「没声明 ⇒ 也缺」⇒ 没实测就写进了台账 ⇒ 清单的方向从根上就错了。
⇒ **判据**：
> **跨域清单里的每一条，都要能指出「它凭什么说缺」** ——
> 「pom 里没写」**不是**依据，「`dependency:tree` 显示 test scope」**才是**。

⚠️ 也正好印证了「**台账 vs 实际**」这一层的价值：
前三个门禁（`check-ledger-selfcheck.py` 查指针 / `check-assertion-sources.py` 查注释锚点）
**全都不会响** —— 它们查的是「台账内部自洽」，而本条错的是「**台账说的 ≠ 仓库实际的**」。

## 25. 2026-10-25 第十六轮（`align/favorite` 工作树 · 承 `598bc93`）—— ✅ **U8 销账**：create 成功路径双侧活体对拍落地

> 目标：把 §24.2 的 **U8**（「create / create_item 的**成功路径**从未被双侧对拍」）**关掉**。
> ⇒ **0 条新漂移**；**1 条注释断言被实测证伪并修正**（行为零改动）；探针自身踩了 **3 个坑**。

### 25.1 做法：给 gRPC 契约探针加「第二轮」（上游**在场**）

| | **第一轮**（既有 · `expected.txt`） | **第二轮**（新增 · `expected-success.txt`） |
|---|---|---|
| **上游** | **故意不接** user-rpc / article-rpc（`Endpoints: 127.0.0.1:1` 死地址） | **接上**探针自带的**最小桩**（user-rpc + article-rpc，Go 写的） |
| **测的分支** | 「**下游不可用**」⇒ `helper.go:103-107` ⇒ `Internal/5001` | 「**下游在场**」⇒ create **成功路径** + `ensureUserExists` 的**其余四条分支** |
| **Go 真源 server** | 第 1 个（`ListenOn` = 端口 A） | **第 2 个**（**同一份二进制**，`UserRpc/ArticleRpc` 改指桩） |
| **RAG 侧** | 第 1 个 Spring 上下文 | **另一次 `mvn` 调用**（第 2 个上下文） |
| **结论** | SAME 33 / SAME_CODE 2 / OBS 1 / SKIP 0 | **SAME 15 / SAME_CODE 0 / OBS 0 / SKIP 0** |

🔴 **为什么是「两个 server / 两次 maven」而不是改第一轮**：
「下游不可用」与「下游在场」是**两条不同的契约分支**，**都要有活体证据**
⇒ 改第一轮会把前者的证据**弄丢** ⇒ 两条分支各测各的，**冻结值互不影响**。

🔴 **为什么第二轮是「另一次 `mvn` 调用」**（而不是同一个 JVM 里加第二个测试类）：
两个 `@SpringBootTest` 的属性不同 ⇒ **Spring 上下文缓存键不同** ⇒ 同 JVM 里会起**两个**上下文
⇒ 两个 gRPC server 端口 + 两套数据源都得错开 ⇒ 分开跑则**每次只有一个上下文**，冲突面最小。

### 25.2 桩：实现、位置、以及它的边界

| 项 | 值 |
|---|---|
| **实现** | [`tools/grpc-contract-probe/favorite/go/service/favorite/rpc/cmd/upstreamstub/main.go`](../../../../tools/grpc-contract-probe/favorite/go/service/favorite/rpc/cmd/upstreamstub/main.go)（**Go**，~200 行） |
| **它是什么** | `UserService.GetUser` + `ArticleService.GetArticle` 的**最小实现**，按 **uid / article_id** 查一张写死的表 |
| **它不是什么** | ⚠️ **不是 user-rpc / article-rpc 的实现** ⇒ **不在** `go/filelist.txt` 的 sha256 复制件清单里（与 `cmd/schemaprep` 同款） |
| **两侧怎么用** | **两侧共同打它** ⇒ 两边收到的**字节相同** ⇒ 差出来的才是 favorite 自己的行为差异，不是「两个桩的差异」 |
| **框架层** | 用**裸** `grpc.NewServer`（不走 go-zero 的 `zrpc`）⇒ 桩不带任何拦截器 / metrics / 熔断 ⇒ 测不出「桩的框架差异」 |

⚠️🔴 **证据边界（读结论前必看）**：
> **桩 ≠ 真 user-rpc / 真 article-rpc** ⇒ 本轮证明的是
> 「**favorite 域自己在这条路径上的行为，两侧一致**」，
> **不**证明「favorite 与 user-rpc / article-rpc 之间的交互契约」。
> ⇒ 期望值**不能**从真 user-rpc 取，只能由「**Go 真源 favorite-rpc + 真 PG**」产出。
> ⇒ 桩返回的 `published_revision_id` 用了 `<id>:r<N>` 这个 **favorite 侧写死的约定**
> （Go `helper.go:159-166` / RAG `FavoriteArticleSnapshotResolver:166-207`）
> ⇒ **不构成 article 域的任何证据**。

### 25.3 结论：15 个 case **全部 SAME**（0 条 OBS / 0 条 SAME_CODE）

| case | 打的是什么 | Go 侧（= 冻结值） |
|---|---|---|
| `s01_create_folder_ok` | `create_favorite_folder_logic.go:89-113` 正常创建 | `OK`（雪花 ID > 0） |
| `s02_create_folder_duplicate` | `:75-87` 重名 | `ALREADY_EXISTS｜收藏夹名称已存在` |
| `s03_create_folder_user_notfound_resp` | `helper.go:109-112` `found=false` | `NOT_FOUND｜用户不存在` |
| `s04_create_folder_user_notfound_rpc` | `helper.go:103-106` RPC NotFound | `NOT_FOUND｜用户不存在` |
| `s05_create_folder_user_status_absent` | `helper.go:113-116` **status 字段缺失** | `UNAVAILABLE｜系统内部错误` |
| `s06_create_folder_user_uid_mismatch` | `helper.go:113-116` uid 不符 | `UNAVAILABLE｜系统内部错误` |
| `s07_create_folder_user_forbidden` | `helper.go:117-120` status≠0 | `PERMISSION_DENIED｜无权限操作` |
| `s08_create_folder_user_rpc_internal` | `helper.go:107` RPC 其它错误 | `INTERNAL｜系统内部错误` |
| `s09_create_item_ok_post` | `create_favorite_logic.go:102-134` target=post | `OK`（title/cover 用**请求里的值**） |
| `s10_create_item_ok_article` | `:105-120` target=article | `OK`（title/cover/revision **只**来自公开投影） |
| `s11_create_item_ok_article_ext_cover` | `helper.go:180-202` `articleCover` 二级回退 | `OK` |
| `s12_create_item_duplicate` | `:122-127` 同 folder+target | `ALREADY_EXISTS｜该内容已收藏` |
| `d01_db_folder_rows` | 落库：`favorite_folder` | `rows=1;names=ParityFolder` |
| `d02_db_item_rows` | 落库：`favorite_item` | `rows=3;items=art-1~article~art-1:r7~probe-article-title~probe-article-cover~owner=2001, …` |
| `d03_db_outbox_rows` | 落库：`favorite_fact_outbox` | `rows=3;facts=1~0~rtw.favorite.assert~1~rtw.community.favorite~post~t-77~~assert, …` |

⇒ **`ensureUserExists`（Go `helper.go:92-124`）的五条分支，现在全部有双侧活体证据**
（此前**一条**都没有：只有源码级）。

### 25.4 落库等价性：**「公开修订冻结」这条最核心的规则被实测执行到了**

🔴 `d02` / `d03` 是「**写下去的东西也一样**」的**直接**证据（不只是「返回码一样」）：

- `s10` / `s11` 的请求里 `title/cover` 故意写成 `req-title-ignored*`；
- 而**落库里存的是** `probe-article-title` / `probe-article-cover*`（来自上游公开投影）
  ⇒ **两侧都真的执行了「请求参数不得覆盖冻结修订」**（Go `create_favorite_logic.go:113-115`）；
- `d03` 同理：`favorite_fact_outbox` 的**事实 payload 形状**（版本 / 事件类型 / schema / producer /
  目标三元组 / 操作）两侧逐字相同 ⇒ **「业务行 + 事实行同事务落库」这条联动两侧一致**。

⚠️ **投影口径（为什么进投影的列是这些）**：
雪花 ID（`folder_id` / `favorite_id` / `id`）与「本次运行的时刻」**两侧必然不同**
⇒ 进投影就等于**制造假红** ⇒ 只投影**契约形状**：
`folder` 只取名字 · `item` 取 `target_id/type/revision/title/cover` + **归属收藏夹的 owner**（用 owner 代替雪花 folder_id，保住归属不变量又不引入雪花值）·
`outbox` 取 `aggregate_version/status` + payload 的 7 个稳定字段。

### 25.5 变异验证（**2 条** ⇒ 守门网不是假绿）

| # | 变异点 | 期望转红的 case | 实测 |
|---|---|---|---|
| **M1** | `FavoriteUserGuard.ensureExists`：`!user.hasStatus()` ⇒ `user.getStatus() != 0L`（**丢掉 presence 语义**） | `s05_create_folder_user_status_absent` | ✅ **转红** —— Go `UNAVAILABLE｜系统内部错误` vs RAG **`code=OK`（并真的建出了收藏夹）**；其余 14 条仍绿 ⇒ 归因明确 |
| **M2** | `FavoriteDomainService.createFavorite`：**不**用公开投影覆盖 title/cover | `d02_db_item_rows` | ✅ **转红** —— Go `probe-article-title` vs RAG `req-title-ignored`；其余 14 条仍绿 ⇒ 归因明确 |

⇒ 两次变异都是「**只该红的那一条红**」⇒ 第二轮的 15 条断言**没有假绿**。

### 25.6 🔴 一条注释断言被**实测证伪**并修正（行为零改动）

`FavoriteUserGuard.ensureExists` 的注释原文写着：

> ⚠️ Java 侧必须用 `hasStatus()`，不能用 `getStatus() != 0` ——
> 后者会把「下游没给 status」**误判成「用户被封禁」，于是 PermissionDenied/5005**……

🔴 **这句是错的**（M1 实测）：
`getStatus()` 对**缺失**的 `optional int64` 返回**默认值 0** ⇒ 换成 `getStatus() != 0L` 之后
presence 检查**变成永真** ⇒ 请求被**直接放行**（`code=OK`，**真的建出了收藏夹**），
**不是** `PermissionDenied/5005`。

⇒ **真正的漂移方向是 fail-open（放行），不是 fail-closed（误判封禁）** ——
方向搞反的话，下一个读注释的人会把「改错的后果」记错。
⇒ 已按实测结论改写注释（并补上 M1 的**实测锚点**与 case id）⇒ **产品代码行为零改动**。

⚠️ 归类：这是**注释纠错**，不是缺陷修复 ⇒ **不计入「已修漂移」**，也不进 `docs/Intentional_Deviations/`。

### 25.7 探针自身踩的 3 个坑（都记进坑档）

| # | 坑 | 坑档 |
|---|---|---|
| 1 | **端口排除列表漏了第一轮的 `RAG_PORT`** ⇒ 桩抢到 RAG server 的端口 ⇒ 客户端打到桩上 ⇒ 报「unknown service favorite.FavoriteService」（**症状离根因很远**）⇒ 已加**互异性自检**（5 个端口去重后必须仍是 5 个） | **PIT-FAV-RPC-023** |
| 2 | **种子显式写自增主键 `id`** ⇒ PG **不**推进序列 ⇒ 首次 INSERT 撞 `favorite_folder_pkey` ⇒ 而业务码把 23505 翻译成「名称已存在 5203」⇒ **症状伪装成重名误报** ⇒ 已改为只写业务列 | **PIT-FAV-RPC-024** |
| 3 | **bash 3.2 的 `${arr[@]+"${arr[@]}"}` 多一个 `"`** ⇒ 整份脚本 `unexpected EOF`（报错行号指向最后那个 `)`，离真凶很远）⇒ 新增端口一律**显式传参** | **PIT-FAV-RPC-025** |

⚠️ 坑 2 改的是**共用种子**（`tools/api-full-parity-probe/favorite/run.sh` 也用它）
⇒ **两个探针都已复跑**，api 侧冻结值**不变**（响应体里本就没有 `id`）。

### 25.8 ✅ **U8 销账** + 域内现状

| 项 | 状态 |
|---|---|
| **U8** | ✅ **已闭**（本节）—— create / create_item 成功路径 + `ensureUserExists` 五分支 + 落库等价性，**全部双侧活体证据** |
| **本轮新漂移** | **0 条** |
| **本轮改动** | 探针（`tools/grpc-contract-probe/favorite/`）+ 1 个新测试类（`favorite-rpc`，**只读断言**）+ 1 条**注释**纠错（`favorite-rpc`）⇒ **产品代码行为零改动** |
| **域内「可修漂移」** | **仍是 0 条**（§24.1 的盘点结论不变） |
| **域内「未知项」** | **U8 已销账 ⇒ 归零**；剩下的只有 **B2**（V7 上线前的 6 条域外写入方人工确认 · **流程项**）⇒ 见 [`tools/outbox-writer-audit/favorite/CHECKLIST.md`](../../../../tools/outbox-writer-audit/favorite/CHECKLIST.md) |
| **跨域** | 仍是 §24.1-C 的 5 条 + §3.5 的 `global` 收口项 ⇒ **本域只读**，不归本轮 |

### 25.9 本轮全量验证（全部实测）

| 项 | 结果 |
|---|---|
| **本域全量测试** | ✅ **BUILD SUCCESS** —— `mvn -o -pl service/favorite/favorite-{proto,common,rpc,api} -am test -Dsurefire.failIfNoSpecifiedTests=false`：<br>favorite-api **94 例**（含 1 skip）· favorite-rpc **85 例**（含 2 skip，**+1 例** = 本轮新增的成功路径探针测试类）· favorite-common **29 例** ⇒ **全绿，0 失败 0 错误** |
| **gRPC 契约探针（两轮）** | ✅ `bash tools/grpc-contract-probe/favorite/run.sh` ⇒ **EXIT=0**<br>第一轮 36 case：SAME 33 / SAME_CODE 2 / OBS 1<br>**第二轮 15 case：SAME 15 / SAME_CODE 0 / OBS 0** |
| **变异验证** | ✅ **2 条**（M1 `s05` / M2 `d02`）⇒ 都按预期转红 ⇒ **守门网不是假绿** |
| **台账 vs 实际** | ✅ `tools/ledger-lint/favorite/check-ledger-vs-reality.py` ⇒ **EXIT=0** |
| **台账自检** | ✅ `tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **EXIT=0** |
| **注释锚点审计** | ✅ `tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **EXIT=0** |
| **共享面** | ✅ **零改动**（`docs/DRIFT/_INDEX.md` / `_MANIFEST.json` / `docs/Intentional_Deviations/_INDEX.md` / `docs/Pitfalls/_INDEX.md` / `AGENTS.md` / `docs/DRIFT/_cross/` / `service/common/**` 一律不碰） |
