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
| 测试 | 3 个测试类 / **56 例全绿**（29 语义 + 13 真 PG schema + 14 错误码映射）—— ⚠️ **此行是 2026-10-23 口径** ⇒ **最新见 §13.5**（**11 个测试类 / 82 例全绿**） |
| 构建 | `mvn -o -pl service/favorite/favorite-rpc -am install` ⇒ **BUILD SUCCESS** |
| 本轮发现并修复 | **6 处 L2 语义偏差**（§2）+ **2 处实现期真 bug**（§3） |
| 未决 | **7 条**（§5）—— ✅ **2026-10-25 已全部有归属**（3 条已签字 / 3 条已修或已撤销 / **1 条第七轮闭合** / 1 条环境受限）⇒ **最新状态见 §11.6（U1–U7）+ §12.3（U7 进展）+ §13.3（U3 闭合）**<br>🏷 **第七轮新增 2 条候选已签字**（2026-10-25）：DEV-FAV-RPC-006（12 处命名差异）/ -007（RAG 更严的那道 CHECK）⇒ **本域已无 🏷 候选**，见 §13.2 |
| 第九轮 | 🆕 **注释可核验性审计**（§15）—— 抓出 **3 条「断言对、锚点指错行」的真缺陷**，门禁级发现 **67 → 0**；**零行为改动** |
| 第十轮 | 🆕 **补完 §15.8 记下的 I1 欠账**（§16）—— **153 条**内联断言补上锚点，再抓 **5 条**（4 条行号指错 + 1 条把 Java 事实误记成 Go）；**台账体检**：2 条死指针 + 3 处过期数字 + 4 处裸行号 + **6 处 RAG 侧锚点位移**已修，**6 个 Go 源文件未落点**已补记 ⇒ 新工具 `tools/ledger-lint/favorite/check-ledger-selfcheck.py`（EXIT=0） |
| 第十二轮 | 🆕 **可动项 A + B 落地**（§19）—— 🔴 **权威读 HTTP 面的双侧活体对拍做成了**（新工具 `tools/authority-live-probe/favorite/`，**21 个 case：20 个逐字节一致 + 1 个 SKIP**）⇒ **U7 的局部活体对拍 6 项 → 7 项**；并抓出并**已修 1 条真缺陷**（200 路径少一个尾部换行字节）<br>🆕 **域外写入方盘点机检**（新工具 `tools/outbox-writer-audit/favorite/`）⇒ 本仓 **0 个域外写入方**（EXIT=0）<br>| 第十一轮 | 🆕 **三条候选签字**（§17）—— DEV-FAV-API-002 / RPC-006 / RPC-007 ⇒ **本域已无 🏷 候选**；顺带改正 §0 头部一处**过期断言**（「未做变异验证」只适用 §0–§8）<br>📌 **域内「可动项」清单**（§18）—— 🔴 **权威读 HTTP 面的双侧活体对拍其实做得了**（不需要 etcd/Kafka/Redis/user-rpc/article-rpc）· 🟡 域外写入方盘点（V7 前置）· ⬜ 跨域项收口在 `global` |
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
| **rpc 侧整域双侧活体对拍** | 🔴 未闭（Go 侧 `etcd` / Kafka 不可拉起） | 🔴 **仍未闭**（**环境受限**，非方法缺陷） |
| **已做的局部双侧对拍** | **4 项**：错误码契约 / path 形态 / 指标 result 语义 / v2 响应体形状 | **5 项**：＋ **protobuf 消息体 wire 字节** |
| **DDL 逐列对照** | 🔴 未做（`check-ddl-static.py` 无 favorite 预设） ⇒ ✅ **活体对拍已完成**（第七轮，§13.2）⇒ **本行已被 §13.2 取代** |
| **仍未闭的** | — | **帧层**（HTTP2 / length-prefix / gRPC status 帧）⇒ 需两侧同时起服务 |

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
| **gRPC 帧层字节对拍**（HTTP2 / length-prefix / status 帧） | ⬜ **不属本域可做** ⇒ **不记入漂移条目** | 帧层编码由 **gRPC 库**决定（grpc-go vs grpc-java），**不是 favorite 域的行为**；两侧都用各自的标准 gRPC 实现 ⇒ 若有差异，那是**库级**差异，**不是 RAG 重构引入的** ⇒ 不在本域 scope（AGENTS.md §1）。<br>⇒ 真要对，只能测「两个 gRPC 实现之间的帧层差」⇒ 与「RAG 是否忠实复刻 Go」**无关** ⇒ 收益低。 |
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
| gRPC **帧层**字节对拍（HTTP2 / length-prefix / status 帧） | 库级差异 ⇒ **不属本域** | 已判定 |
| **gRPC 整域**活体对拍 | 🔴 真受阻（Go gRPC server 注册走 etcd） | 已拆成 6 项局部对拍 |

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
