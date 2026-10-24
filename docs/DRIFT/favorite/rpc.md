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

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 落地 | **36 个 Java 文件 / 7,057 行 / 6 个迁移脚本**（本轮前：34 个文件全 0 字节、`pom.xml` 零依赖零插件、6 个 `.sql` 全 0 字节） |
| 测试 | 3 个测试类 / **56 例全绿**（29 语义 + 13 真 PG schema + 14 错误码映射） |
| 构建 | `mvn -o -pl service/favorite/favorite-rpc -am install` ⇒ **BUILD SUCCESS** |
| 本轮发现并修复 | **6 处 L2 语义偏差**（§2）+ **2 处实现期真 bug**（§3） |
| 未决 | **7 条**（§5），其中 3 条是**待签字的有意偏离** |
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

> **纪律**：本表任一条**都没有**「已修」的断言。核对日期一律 2026-10-23。

| # | 项 | 现状 | 待谁拍板 |
|---|---|---|---|
| **U1** | **权威端点的 wire 形态** | 按 Go 原样写成 **path 参数** servlet（`/internal/v1\|v2/favorite/facts/{producer}/{event_id}`），**不是** RAG 通用 `AuthorityHandlerFactory` 的 query 形态。整类 `@ConditionalOnProperty(favorite.fact.authority.enabled=true)` **默认关闭 ⇒ 默认不新增任何 HTTP 端口** | 开启时需给**纯 gRPC 进程**加 web connector ⇒ **部署形态变更**，未拍板 |
| **U2** | **preflight 的退出码语义** | Go 是独立进程 ⇒ 退出码即门禁。Java 进程内**刻意不调 `System.exit`**，改结构化日志（`outcome=succeeded` / `not_reconciled` / `failed` + `error_code`） | CI 需改为**解析日志**判门禁 |
| **U3** | `zrpc.RpcServerConf.Mode` 的取值域 | **不在本仓库**（Go 侧 `etc/favorite.yaml` 只有 `Mode: dev`）⇒ 用 Spring profile `dev` / `test` 映射 | 需 Go 侧取值域清单 |
| **U4** | **authority lookup 的 3s 超时** | Go 用 `context.WithTimeout(3s)` 约束 DB 查询；Java 侧走 Hikari `connection-timeout` ⇒ **未对齐** | 需确认这是否算可观察差异 |
| **U5** | **`ObserveRPC` 的 `result` 标签** | Go 侧 **21 处**错误返回因未赋值命名返回值 `err`，被记成 **`success`**。Java 侧**按真实结果计数**（成功 `success`、异常 `fail`） | **有意的指标行为变更，未签字** ⇒ 需走 `docs/Intentional_Deviations/` |
| **U6** | `favorite_request_seconds_counter` 指标名 | Micrometer Counter 会追加 `_total`（Go `client_golang` 不会）⇒ 保留不改 | 与 U5 一并拍板 |
| **U7** | **双侧活体对拍 + 变异验证** | 未做。Go 侧 `etcd` / Kafka 依赖不可拉起 | 需要一套能同时拉起两侧的环境 |

> ⚠️ **U5 / U6 是「已实现但未签字」** —— 按 AGENTS.md §0 的约定，**看到它们变红不要当新回归去「修」**。

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

### 9.3 ⚠️ 仍未决（**沿用 §5 的 7 条，本轮未复核**）

| # | 项 | 本轮状态 |
|---|---|---|
| **U1** | 权威端点的 wire 形态（path 参数 servlet，默认关） | ⬜ **未复核**（本轮未触碰 `FavoriteAuthorityEndpointConfig`） |
| **U2** | preflight 的退出码语义 | ⬜ **未复核** |
| **U3** | `zrpc.RpcServerConf.Mode` 的取值域 | ⬜ **未复核**（**需 Go 侧提供取值域清单**） |
| **U4** | authority lookup 的 3s 超时 | ⬜ **未复核** |
| **U5** | `ObserveRPC` 的 `result` 标签（Go 侧 21 处错误返回被记成 `success`） | ⬜ **未复核** ⇒ **待签字材料尚未落档**，见 [`../../Intentional_Deviations/favorite/rpc.md`](../../Intentional_Deviations/favorite/rpc.md) 的「尚未落档」标注 |
| **U6** | `favorite_request_seconds_counter` 指标名（Micrometer 追加 `_total`） | ⬜ **未复核** ⇒ 同上 |
| **U7** | **双侧活体对拍 + 变异验证** | ⬜ **仍未做**。本轮只把 **`favorite-common` 的错误码契约**做成了双侧活体对拍（`tools/errmsg-probe/favorite/`，EXIT=0）⇒ **rpc 侧整体仍无活体对拍** |

> ⚠️ **U7 仍是 rpc 侧最大的证据缺口**：Go 侧 rpc 的 `etcd` / Kafka 依赖本轮仍不可拉起。
> ⇒ 本轮的做法是**先把能双侧跑的层（错误码契约）单独做成探针**，而不是硬凑一个跑不起来的整域对拍。

### 9.4 一条**跨域观察**（不属于 favorite 域的修法，但影响本域）

`service/like/like-api/.../LikeRpcClient.java:43-48` 的注释写着
「⚠️ client 熔断：Go 侧有且默认开，Java 侧**尚未接入**（已登记的已知偏离）」。

⚠️ **实测该断言极可能同样是错的**：`LikeApiApplication` 的 `scanBasePackages` **也含 `com.rag.common`**
⇒ `GoStyleGrpcClientBreakerInterceptor` 同样会**自动注册** ⇒ 像 favorite 域一样，熔断**很可能早已生效**。

⇒ 但 **like 域不是本域的文件**（AGENTS.md §5.3：只写自己域）⇒ **本轮不改、不判**，
仅在此**登记为跨域线索**，交由 like 域 / `global` 收口域处理。
⚠️ 别把它当 favorite 域的漂移去「顺手统一」。
