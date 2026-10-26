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
| 测试 | 3 个测试类 / **56 例全绿**（29 语义 + 13 真 PG schema + 14 错误码映射）—— ⚠️ **此行是 2026-10-23 口径** ⇒ **最新见 §26**（favorite-rpc：**20 个测试类 / 110 例**（含 3 skip）—— ⚠️ **第二十轮实测**：新增的 `FavoriteSqlStatementParityTest` 在**没有探针环境变量**时按设计 skip ⇒ skip 数 2 → 3；⚠️ **第二十三轮实测**：新增 `FavoriteFolderItemTimeColumnTest` **5 例**（真 PG）⇒ 100 → **105**；⚠️ **第二十五轮实测**：新增 `FavoriteSqlTimeoutProductionPathTest` **4 例**（真 PG + 真 HikariCP + 真 MyBatis 链）⇒ 105 → **109**；⚠️ **第二十七轮实测**：该守门把「代理层秒数」与「驱动内部原始毫秒」**拆成两个测试方法**（同方法内第一条失败会遮住第二条）⇒ 4 → **5 例** ⇒ 109 → **110**） |
| 构建 | `mvn -o -pl service/favorite/favorite-rpc -am install` ⇒ **BUILD SUCCESS** |
| 本轮发现并修复 | **6 处 L2 语义偏差**（§2）+ **2 处实现期真 bug**（§3） |
| 未决 | **7 条**（§5）—— ✅ **2026-10-25 已全部有归属**（3 条已签字 / 3 条已修或已撤销 / **1 条第七轮闭合** / 1 条环境受限）⇒ **最新状态见 §11.6（U1–U7）+ §12.3（U7 进展）+ §13.3（U3 闭合）**<br>🏷 **第七轮新增 2 条候选已签字**（2026-10-25）：DEV-FAV-RPC-006（12 处命名差异）/ -007（RAG 更严的那道 CHECK）⇒ **本域已无 🏷 候选**，见 §13.2<br>✅ **U8（第十五轮新增 · 域内可动的最后一条）已于第十六轮闭合**：create / create_item 的**成功路径** + `ensureUserExists` 五分支 + **落库等价性**全部拿到双侧活体证据（**15 case 全 SAME**）⇒ **§25**（盘点全表 ⇒ §24.1）⇒ ~~域内「未知项」归零~~（只剩 B2 一条**流程项**：V7 上线前的域外写入方人工确认）<br>🔴🔴 **第二十轮翻案**：「域内『未知项』归零」是**提前收口** —— 当时**没查 SQL 语句层**（DDL / 错误码 / 事务语义三层闭了，**语句层是第四层，没做**）⇒ 第二十轮补上那一层 ⇒ **当场抓出 3 处 L1 可观察的漂移**（RAG 侧 INSERT / UPDATE **少绑时间列** ⇒ 落库 `NULL` ⇒ 对外时间戳 = **0**）⇒ **§29**<br>🔴 **U9（第十七轮新增 · 部署形态类 · 待拍板）**：Go 生产形态有 **16062** 那个 HTTP 面（`etc/favorite.yaml:23-30` 的 DevServer + `internal/devserver/server.go:79-82` 的 `if !c.Enabled { return }` ⇒ **与 mode 无关，生产也开**），RAG 侧 `web-application-type: none` ⇒ **没有** ⇒ 要么 RAG 侧也开（**部署形态变更**，需引 servlet 容器），要么**签字接受**⇒ **§26.8** |
| 第九轮 | 🆕 **注释可核验性审计**（§15）—— 抓出 **3 条「断言对、锚点指错行」的真缺陷**，门禁级发现 **67 → 0**；**零行为改动** |
| 第十轮 | 🆕 **补完 §15.8 记下的 I1 欠账**（§16）—— **153 条**内联断言补上锚点，再抓 **5 条**（4 条行号指错 + 1 条把 Java 事实误记成 Go）；**台账体检**：2 条死指针 + 3 处过期数字 + 4 处裸行号 + **6 处 RAG 侧锚点位移**已修，**6 个 Go 源文件未落点**已补记 ⇒ 新工具 `tools/ledger-lint/favorite/check-ledger-selfcheck.py`（EXIT=0） |
| 第十二轮 | 🆕 **可动项 A + B 落地**（§19）—— 🔴 **权威读 HTTP 面的双侧活体对拍做成了**（新工具 `tools/authority-live-probe/favorite/`，**21 个 case：20 个逐字节一致 + 1 个 SKIP**）⇒ **U7 的局部活体对拍 6 项 → 7 项**；并抓出并**已修 1 条真缺陷**（200 路径少一个尾部换行字节）<br>🆕 **域外写入方盘点机检**（新工具 `tools/outbox-writer-audit/favorite/`）⇒ 本仓 **0 个域外写入方**（EXIT=0）<br>| 第十一轮 | 🆕 **三条候选签字**（§17）—— DEV-FAV-API-002 / RPC-006 / RPC-007 ⇒ **本域已无 🏷 候选**；顺带改正 §0 头部一处**过期断言**（「未做变异验证」只适用 §0–§8）<br>📌 **域内「可动项」清单**（§18）—— 🔴 **权威读 HTTP 面的双侧活体对拍其实做得了**（不需要 etcd/Kafka/Redis/user-rpc/article-rpc）· 🟡 域外写入方盘点（V7 前置）· ⬜ 跨域项收口在 `global` |
| 第十四轮 | 🆕 **api 整域双侧活体对拍**（§22）—— 新探针 `tools/api-full-parity-probe/favorite/`：**32 case · 24 SAME / 1 SAME_CODE / 2 SAME_BODY / 4 SAME_STATUS / 1 OBS ⇒ 0 条新缺陷**；两侧 = **Go 真源**（22 个逐字节复制件 + sha256 校验）与 **RAG 两个 `java -jar` 真进程** + **共用一个 Redis**<br>🔴 **B-1 闭合并推翻第十三轮结论**（grpc-go 未知方法/服务的措辞差异 = **探针自己把 `:path` 拼成双斜杠**）<br>🔴 **2 个 RAG 侧启动阻塞已修**（`favorite-rpc`/`favorite-api` **从未被验证能以真实进程启动**）<br>🆕 **域外写入方盘点机检扩面**（`tools/outbox-writer-audit/favorite/` + `CHECKLIST.md`） |
| 第十六轮 | ✅ **U8 销账 —— create 成功路径双侧活体对拍**（§25）—— gRPC 契约探针加**第二轮**（上游**在场**：探针自带**最小 user-rpc/article-rpc 桩**当**两侧共同上游**）⇒ **15 case 全 SAME**（0 OBS / 0 SAME_CODE）⇒ `ensureUserExists` **五条分支**首次拿到活体证据 + **落库等价性**（`favorite_folder`/`favorite_item`/`favorite_fact_outbox` 三张表投影逐字段相同 ⇒ 「公开修订冻结」被实测执行到）<br>🔴 **1 条注释断言被实测证伪并修正**（`getStatus()!=0` 的后果是 **fail-open 放行**，不是原文写的「误判封禁 5005」）⇒ **产品代码行为零改动**<br>🆕 坑档 **PIT-FAV-RPC-023/024/025**（端口撞号 / 种子显式 id 毒化序列 / bash 3.2 引号）|
| 第十四轮收尾 | 🔴 **探针健壮性加固**（§23）—— 消除「**环境红**」假红：4 个起 PG 的探针**统一**改成 **探 TCP + 180s + 重建重试 + 容器退出即放弃**；新增守门网 `tools/probe-gate/favorite/`（含变异验证）；**零行为改动**、**0 条新漂移** |
| 第十七轮 | 🔴🔴 **生产启动形态**（§26）—— `global` 移交的「**生产 jar 起不来**」查清并修完：**3 个缺陷**（① gRPC client 地址**两斜杠** ⇒ 启动期抛 `IllegalArgumentException`；② `web-application-type` 缺失 ⇒ Boot 判定 SERVLET 而无容器 ⇒ **启动即死**；③ gRPC **reflection 暴露面**漏关）⇒ 新增 **3 个守门测试类 / 14 例**（全部读**生产文件**，不走 `@SpringBootTest` 覆盖）+ **变异验证 4 条**全转红 + 🆕 新探针 `tools/prod-startup-probe/favorite/`（**A/B/C** 真 fat jar 活体对拍 ⇒ EXIT=0）<br>🔴 **翻案**：第十四轮 §22.3 的「启动阻塞 #1 已修」是**半修**—— 修复只落在**探针启动参数**里，**生产 yaml 一行都没改** ⇒ 缺陷活到了发布那一刻 ⇒ 坑档 **PIT-FAV-RPC-026**<br>🔴 **新登记 U9**（部署形态类 · 待拍板）：Go 生产形态的 **16062** DevServer 那个面，RAG 侧没有 |
| 第十八轮 | 🔴 **借 `global` 的新规则跑本域台账 ⇒ 当场抓出 3 处**（§27）—— 全程**只读**借 `global` 的跨域共享复核器（`tools/check-ledger-selfcheck.py`，**global 单写权**，拷到 `/tmp` 后只调它的 L1/L4 两层）⇒ 抓出 **1 处行号真错**（`core/service/serviceconf.go` 的 **83-90 行** ⇒ 该文件共 89 行 ⇒ 应为 `:83-89`）+ **2 处路径写法不够精确**（`schema/naming.go` / `metrics.go` ⇒ 机器按 basename 核不动）<br>⇒ **不登记候选等别人扩**：把这两层**搬进本域自己的门禁**（`tools/ledger-lint/favorite/check-ledger-selfcheck.py` 新增 **⑤ 仓内路径存在性** + **⑥ Go 侧 `file.go:NN` 可解析** + `--mutation-test`）⇒ **门禁级 0 条**<br>⚠️ **工具第一版的 8 条门禁里 5 条是工具自己的 bug**（缺 Go 标准库 / 缺 N5 去噪 / key 结构写错 …）⇒ 坑档 **PIT-FAV-RPC-027**（**借来的工具只能当探针，不能当门禁**）<br>⚠️ 残留 **57 条「跨库/同名歧义」**（提示级 · 已量化 · 本轮不批量改）<br>🔴 **第二十一轮补正**：上面那句里的「83-90 行」是**当时写错的行号**（本轮订正）⇒ 写成「N 行」而**不是** `file:NN` ⇒ 否则任何 `file:NN` 扫描器都会把它当**活指针**判成悬空（`global` 第三十四轮修好行级去噪后，**它就会浮出来**）⇒ 同批修掉本域另外两处同源写法（`rpc.md` §27.2 表的**错误列** + `PIT-FAV-RPC-027` 的「真缺陷」行） |
| 第十九轮 | 🔴 **共享层 `DeadlineQueryTimeoutInjector` 改动的跨域验证**（§28 · `global` 第三十三轮）—— 两处行为变化：① **毫秒精度**（`setQueryTimeoutMs`）② **deadline 已过 ⇒ 不执行 SQL**（抛 57014 ⇒ 事务回滚）<br>✅ **favorite 域零条依赖旧行为**（权威读面的 deadline 是**紧贴调用新绑**的 3s ⇒ 新分支走不到；毫秒精度 ⇒ 取消时刻**差 δ（毫秒级）**（🔴 **第二十五轮订正**：这句描述的是**共享层那版代码的意图** ⇒ **生产路径上毫秒路径没走**（E62）⇒ 实际走**秒级回退** ⇒ 结论不变但**理由已换成纯算术依据** ⇒ 见 **§34**） ⇒ 且 favorite **没有**「SQL 前先烧掉大部分预算」的 handler ⇒ 「最坏 ≈999ms」窗口**不成立**）⇒ 叠 global 实现跑：**全量 177/99/94/29 全绿** + **三个探针全 EXIT=0**（authority-live 21 case / api 32 case / grpc-contract 36+15 case，冻结值一条没动）⇒ **零红、零需改动**<br>🔴 **给 global 的落地提醒**：主文件与测试改动**必须同一次提交**（只提主文件 ⇒ `service/common` 自己的旧行为断言红 1 例 ⇒ 仓库是红的）⇒ 同型 **PIT-FAV-RPC-026**<br>🔴 **纠正一条过期事实**：「favorite 台账 0 字节 / 从未对照」**不成立**（12 份 ≈ 430 KB · 19 轮对照）⇒ 出处是 `AGENTS.md:32` 的 2026-10-23 首轮快照 ⇒ 但**「SQL 语句级」双侧对拍确实未做**（DDL / 错误码 / 事务语义三层已闭）⇒ 这条我**认** |
| 第二十轮 | 🔴🔴 **SQL 语句层**双侧活体对拍（§29）—— 补上 favorite 域**四层验证的最后一层**（DDL / 错误码契约 / 事务语义 **三层早已各自闭**，**语句层没做**）⇒ 🆕 探针 `tools/sql-statement-probe/favorite/`：**PG 服务端日志**（`log_statement=all`）**零侵入**取证（Go 侧 `PGAPPNAME=go_side` / RAG 侧 JDBC `ApplicationName=rag_side` / 驱动自己 `probe_marker`）+ **marker 协议**逐用例切片 + **语义签名**比对（不是比 SQL 文本）+ **变异自检 5/5**（含阴性对照）<br>🔴 **抓出 3 处 L1 可观察的漂移**：① `favorite_folder` INSERT **不绑** `create_time`/`update_time` ⇒ NULL ⇒ L1 时间戳 = **0**；② `favorite_item` INSERT **不绑** `create_time` ⇒ NULL ⇒ L1 = **0**；③ 改名**不推进** `update_time` ⇒ L1 `updated_at` 不变（+1 处低影响：`favorite_fact_outbox` 两个投递列 `NULL` vs `''`）⇒ **23 用例 · 20 个用例的语句类别序列两侧逐条一致**，差异**全部集中在「写集合」一维**<br>🔴 **翻案**：§0「域内『未知项』归零」是**提前收口** ⇒ 每一层的**排除项**都会变成下一层的缝隙 ⇒ 坑档 **PIT-FAV-RPC-028** |
| 第二十一轮 | 🔴 **共享复核器改进后的跨域复跑**（§30）—— `global` 第三十四轮修好我提的 4 条工具缺陷（去噪**行级化** / `go.mod` 权威化 / 两条精确定位通道）⇒ 🔴 **去噪行级化后本域台账有 155 条此前被淹没的引用第一次真被检查** ⇒ 抓出 **1 条 FAIL**：`core/service/serviceconf.go` 的 83-90 行 —— **正是本域第十八轮「抓出并修好」的那一条**，但它在台账里出现 **3 次**、当时**只改了 1 次**（表格的修正列）⇒ **错误列 / 叙述行 / 坑档**三处漏改 ⇒ 本轮**一次改完**（改成「N 行」写法 ⇒ 记录不丢、也不再被任何 `file:NN` 扫描器当活指针）⇒ 改后 **L1 = 0 · L4 = 0** ✅<br>✅ 顺带确认**本域门禁不落后**（②③④ 三条通道本域第十八轮就自己实现了）；🆕 §29 新写的锚点**全部用 `module@version/...` 精确写法** ⇒ 在他们修好的工具里**不落进跨库歧义清单** ⇒ 精确写法应成为本域新标准（存量 62 条歧义仍为提示级，本轮不批量改）<br>🆕 坑档 **PIT-FAV-RPC-029**：「**修一处 ≠ 修完**」（同一处锚点在台账里有 N 个副本）|
| 第二十二轮 | 🔴 **提交前把本域门禁全跑一遍 ⇒ 抓出第二十轮遗留的 2 处错锚点**（§31）—— 本来只是例行复跑 ⇒ `assertion-audit` **EXIT=1 · 门禁级 2 条**：`FavoriteSqlStatementParityTest.java:296/:324` 的 Go 锚点写成 **logic 层**的 `delete_favorite_folder_logic.go` 的 **336-378 行**，但该文件**只有 96 行** ⇒ 实际指的是 **model 层**的 `service/favorite/rpc/internal/model/favorite_fact.go:336-378`（`DeleteFolderCascade`）⇒ **断言语义对、锚点指错文件** ⇒ 「**证据是假的**」（比没写锚点更危险）<br>⇒ 顺手做**全量语义核对**（门禁只判「行号越界」，抓不到「指错文件但行号在范围内」）：该测试文件**全部 20 个 Go 锚点**逐条回真源核对 ⇒ **18 条正确** · **2 条错锚点已修** · **1 条 I1**（「Go: 同上」无法定位）已展开成真锚点<br>✅ 另核掉 2 条 favorite-rpc 的 I3 ⇒ **全是工具误报**（一个因 excerpt 未从锚点起始行对齐、一个因 excerpt 跨两段）⇒ 锚点本身逐行正确<br>🔴 **根因**：第二十轮**新写了带 20 个 Go 锚点的文件**却**没跑管它的那个门禁** ⇒ 漏检**跨了两轮** ⇒ 坑档 **PIT-FAV-RPC-030**<br>✅ **SQL 探针复跑基线完全一致**（记录 855 / 语句 594 / 语义差异 10 / L1 响应不同 3 例）⇒ `out/` 归一化后**逐字节一致** ⇒ 注释改动**零行为影响** |
| 第二十三轮 | 🔴🔴 **修掉 SQL 层抓出的 4 处漂移 + 把对拍从「语句层」升级到「落库值层」⇒ 探针转绿**（§32）—— **产品侧 4 处全修**：① `favorite_folder` INSERT 补绑 `create_time`/`update_time`（**同一个** `now` ⇒ Go 的 `curTime` 整条 INSERT 只取一次）② `favorite_item` INSERT 补绑 `create_time` ③ 改名 UPDATE 补 `update_time`（GORM 的 map 形式 Update **自动追加** autoUpdateTime）④ outbox 的 `technical_receipt_id`/`technical_input_hash` 显式写 `''`（Go 是**非指针** string ⇒ 零值是 `''`）⇒ 全部用**应用进程时钟**（**不是** PG 的 `now()`）⇒ 与 Go 同源<br>⇒ 顺带订正 **2 处被实测证伪的 javadoc**（「由 PG 的 now() 生成」「Go 也只写 name」—— 两处**写错了事实**）<br>🔴 **对拍工具自身有 4 个缺陷**：① `PARAM_RE` 丢掉 NULL 参数（静默）② 值提取跑在 normalize **之后** ⇒ `$N` 已成 `?` ③ **只比列集不问 DDL** ⇒ 3 列误报 ④ **L1 精确相等 ⇒ 结构上恒红** ⇒ 判别不了「修没修」⇒ 全部修掉（**DDL 感知** + **L1 归一化**）<br>🟡 「多投影一列」不必然是行为差异 ⇒ 走**带出处的显式登记**（`equivalences.tsv`，**出处自动校验**，不过则作废）⇒ 根因：Go 的 struct **只有 8 个字段、无 `approved_at`** ⇒ 取回后在 struct 层就丢弃<br>✅ **探针转绿**：语义差异 **10 → 0** · L1 响应不同 **3 → 0** · EXIT=0 ⇒ **变异自检 6/6**（含**新增**阴性对照）+ **产品级变异 2/2 转红** ⇒ 绿是**有证据的绿**<br>⇒ 坑档 **PIT-FAV-RPC-031**：「**恒红的信号不携带信息**」 |
| 第二十四轮 | 🔴 **跨分支论证订正**（§33）—— `global` 第三十八轮回执指出我第十九轮的一条**依据**用错了符号 ⇒ **核完确认：结论对、依据错** ⇒ 台账订正：<br>🔴 原写「favorite 的 client deadline **上界恒为 2s**（`FavoriteRpcClient.capByClientTimeout` 把 `min(ctx 剩余, 2s)` 压进去）」⇒ **错**：那是 **api 侧**的**客户端封顶**（决定 api→rpc 那次调用还剩多久），**不决定** rpc 内部 SQL 的 R；真正决定 R 的是 **rpc 侧 `RpcInterceptor`（`:170-172`）每次调用自己新绑 `now + 2s`** ⇒ ⇒ **本分支上正确的依据 = 「R = 新绑的整预算 − δ，δ 是 handler 前置耗时（毫秒级）」⇒ 与「客户端上界」无关**<br>⚠️ **跨分支提醒**：`global` 侧的 `service/common` 与本分支**不是同一版本**（他们 `:232-237` 的 `min(server, clientRemaining)` vs 本分支 `:170-172` 的服务端自绑）⇒ ⇒ 那边「躲开最坏窗口」的依据**只能是「handler 不烧预算」** ⇒ 结论一致、**依据不同**<br>⇒ 坑档 **PIT-FAV-RPC-033**：「**符号存在 ≠ 符号决定你要论证的那个量**」+「同名文件在不同分支上是两个实现」 |
| 第二十五轮 | 🔴 **核 `global` 第四十一轮 E62：毫秒精度在本域生产路径上到底生效没有**（§34）—— `global` 要求本域核对台账里凡写「毫秒精度已生效」的表述并加限定 ⇒ **先核事实，不直接采信**：① **静态**（`javap`）：pgjdbc 的毫秒 API **只挂在实现类**上（**42.6.2 / 42.7.13 都一样**）· HikariCP 的 `HikariProxyPreparedStatement`（**4.0.3 / 5.0.1 / 7.0.2 全查**）**只有秒级接口** ⇒ ⇒ 按运行期类反射找毫秒方法**必然失败** ⇒ **机制成立** ② 🔴 **本域运行时实测**（真 PG + 真 HikariCP + 真 MyBatis 插件链 + 真 mapper）：statement = `HikariProxyPreparedStatement` ⇒ 毫秒 API **不可达** ⇒ 有效超时 = **2 秒 / 驱动内部原始毫秒 2000（整秒）** ⇒ **秒级回退**（R ≈ 1700 ⇒ 毫秒路径会给 ≈1700 ⇒ 可区分）③ 🔴 **阴性对照**（同 PG 不走池）⇒ pgjdbc 实现类上毫秒 API **可达** ⇒ 且实测 `setQueryTimeoutMs(1700)` ⇒ 原始毫秒 = **1700** ⇒ ⇒ **判别器不是恒真**<br>⇒ 🔴🔴 **叠加 `global` 那版实现（含毫秒路径）复跑**：他们自己的 `DeadlineQueryTimeoutInjectorTest` **10 例全绿** + 本域 **4 例仍全绿** ⇒ ⇒ **带毫秒路径的实现与旧秒级实现在本域给出完全相同的有效值** ⇒ ⇒ **毫秒路径在本域生产路径上根本没走到** ⇒ ⇒ **E62 在本域以运行时证据成立**（不是转述）⇒ ⇒ **共享层这次改动对 favorite 的可观察影响 = 0**（生产上）<br>⇒ 🔴 **台账订正**：§28 引言 + §28.1 对比表（改成「代码意图 / 生产实际」**三列**）+ §28.1 结论句（**区分 (A) 旧↔新 = 0** 与 **(B) Go↔RAG = δ**）⇒ ⇒ 结论不变（**A 类仍 0**）但**理由换成纯算术依据** ⇒ **不再依赖未验证前提**<br>⇒ ✅ **本域零红、零需改动**；共享面**零改动**（overlay 后已还原）⇒ 🆕 坑档 **PIT-FAV-RPC-034** |
| 第二十六轮 | 🟢 **接住 `global` 第四十四轮的结构层缺陷类 ⇒ 收窄成本域门禁**（§35）—— global 同步：本轮**无** `service/common/**` 行为改动 ⇒ 对 favorite **零影响**；并新增结构层守门 **`check-ledger-tables.py`**（global 域 · 在 `align/global` 分支的 `tools/` 目录下 ⇒ **本分支没有该文件**）（「**表格块内部不得出现无行首的内容行**」⇒ 否则 GFM 把它当上一行的 lazy continuation ⇒ **表格结构静默塌陷**：内容一个字没丢 ⇒ **肉眼扫不出**）<br>🔴 **但 global 的测量点过期了一轮**（他们在**我的第二十四轮 HEAD `a4a7ee0`** 上测 ⇒ 我的**第二十五轮**又改了台账）⇒ ⇒ 在**最新 `1111c8a`** 上复跑 `--all` ⇒ **我域 0 命中**（全仓 10 处全在 comment 域文件）⇒ ⇒ 逐条核证：是**真塌陷**（第 78 行是表格行、79/80 行**没有行首 `|`**）不是误报<br>🔴 **但 global 的原版不能直接当本域门禁**：① **默认范围不含本域** ⇒ 只能用 `--all` ⇒ 会因 **comment 域的文件**变红 ⇒ **别人的缺陷不该让我的门禁红** ② **`--mutation-test` 硬编码 global 自己的文件** ⇒ 在本分支上**「变异前置失败」** ⇒ 自证机制用不了<br>✅ ⇒ **收窄成本域版** `tools/ledger-lint/favorite/check-ledger-tables.py`（只扫**本 owner 写的 3 个 `rpc.md`**；显式排除 `api.md`（并发会话）/`common,proto`（别的组件）/`docs/FileTree/**`（用户维护）；变异靶子换成本域自己的 §0 时间线表）⇒ **EXIT=0** + **变异自证 M1/M2/M3 全过**（精确命中 `rpc.md:32`/`:33`）+ **真实数据交叉验证**（副本判据在 comment 域文件上**复现 10 处、行号逐条一致** ⇒ 判据没抄坏）⇒ ⇒ 坑档 **PIT-FAV-RPC-035** |
| 第二十七轮 | 🔴 **global 第四十五轮：毫秒路径真的生效了**（§36）—— **真·共享层行为变更**（`DeadlineQueryTimeoutInjector` 的毫秒探测改成**先 unwrap 到驱动实现类** ⇒ 不再被 Hikari 代理类挡住）⇒ **favorite-rpc 在内** ⇒ 只读 overlay 实测：他们那版 `DeadlineQueryTimeoutInjectorTest` **13 例全绿** + 本域守门 **5 例中 2 条红**（`expected: <2> but was: <1>`、`expected: <2000> but was: <1691>`）⇒ ⇒ **第二十五轮埋的「转红 = 台账该更新、不是回归」信号兑现**<br>🔴🔴 **实测证伪他们 ④ 的量化口径**：他们写「毫秒路径把取消时刻收紧到『deadline + δ』⇒ 差 = δ」⇒ **错**：驱动收到的是**原样 R（1691ms）** ⇒ `R = deadline − t_exec` ⇒ ⇒ **`t_exec + R ≡ deadline`** ⇒ ⇒ **Go ↔ RAG 的取消时刻差 = 0**（不是 δ）⇒ ⇒ **与 deadline 的来源公式无关**（⇒ 连「两分支 R 公式不同」都不再重要）<br>🔴 **「结论不变的真正理由」再订正**：毫秒路径生效后「≤999ms 窗口」**本身不存在** ⇒ 「handler 不烧预算 + 快 SQL」**退化为冗余** ⇒ 它的正确角色是「**毫秒路径若再次失效时的唯一结构性兜底**」⇒ ⇒ 当前生效的主因 = 「毫秒精度直接生效」<br>🟢 本域**本来就在真池路径上**（`@SpringBootTest` ⇒ HikariCP）⇒ 不存在他们说的「守门绿、生产坏」那个洞<br>🔴 **合并动作清单已提前写好**（§36.5）：守门期望值 2→**1** / 2000→**≈R**、台账 §28.1/§34/§35 改口径、坑档补「已修」⇒ ⚠️ **本分支现在是「秒级」是对的**（global 未合回主仓）⇒ 别误读成「修法不对」<br>✅ **守门改进**：两条断言**拆成两个测试方法**（同方法内第一条失败会遮住第二条）⇒ 5 例全绿；**产品代码零改动**、共享面零改动 ⇒ ⇒ 坑档 **PIT-FAV-RPC-036** |
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
| **消费点（全部）** | ① `core/service/serviceconf.go:83-89` `initMode()`：`dev\|test\|rt\|pre` ⇒ `load.Disable()`（关分布式追踪采样）+ `stat.SetReporter(nil)`（关指标上报）；`pro` ⇒ 全开。<br>② `service/favorite/rpc/favorite.go:31`：`SubjectRefV2Facts` 为 true 时，Mode **必须**是 `dev` 或 `test`，否则 `os.Exit(2)`（`LOCAL_CANDIDATE_ONLY`）⇒ **这是唯一会让进程起不来的 Mode 约束**。<br>③ `favorite.go:45`：`Mode == dev \|\| Mode == test` ⇒ 注册 gRPC **reflection**（生产不注册）。 |
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
| **A · 已识别且可修的漂移** | **0 ⇒ 3 ⇒ 0**（🔴 第二十轮补上「SQL 语句层」后新增 3 处 ⇒ ✅ **第二十三轮全部修掉**） | 已修：rpc 6 处 L2 + 2 处真 bug；api 4 处（L2×2 + javadoc 假断言×2 + L1×1）；DDL 14 处 DEFAULT + 3 条索引（**性能非行为**）<br>已签字 **9** 条（API-001/002 · COMMON-001/002 · RPC-001/003/004/006/007）· 已撤销 2 条（RPC-002/005）· 🏷 候选 **0**<br>🔴 **第二十轮新增 3 处** ⇒ ✅ **第二十三轮全修**：① `favorite_folder` INSERT 不绑 `create_time`/`update_time` ② `favorite_item` INSERT 不绑 `create_time` ③ 改名不推进 `update_time`（+1 处低影响：outbox 两个投递列 `NULL` vs `''`）⇒ 修完**探针转绿**（语义差异 10 → 0 · L1 3 → 0）⇒ **§29 / §32** | §2 / §3 · `api.md` §2 · `docs/Intentional_Deviations/favorite/` · **§29 / §32** |
| **B · 域内能补的证据缺口** | ~~**1 + 1**~~ ⇒ **0 + 1** | **B1 = U8**：create / create_item 的**成功路径**未双侧对拍（⇒ 见 §24.2）—— ✅ **已于第十六轮闭合**（15 case 全 SAME ⇒ **§25**）<br>**B2**：V7 上线前的 **6 条域外写入方人工确认**（**流程项，不是代码项** ⇒ **仍待办，但不是「未知项」**） | §24.2 · §25 · [`tools/outbox-writer-audit/favorite/CHECKLIST.md`](../../../../tools/outbox-writer-audit/favorite/CHECKLIST.md) |
| **C · 跨域（本域只读）** | **5** | ① 容器层参数上限口径（与 hot 域 `DRIFT-HOTAPI-030` 同一格）② 405 多一个 `Content-Type`（落点 `service/common`，其 javadoc 被实测证伪）③ Go 多一个 `traceparent`（落点 `service/common` 的 `HttpMiddlewareFilter`）④ 🔴 **别域模块**运行时缺 `grpc-inprocess` ⇒ 可部署 jar 起不来 —— ⚠️ **本条数字已被同节 §24.4 归因翻案**：真缺 = **3 个可执行应用模块**（article-rpc / like-rpc / task-rpc）+ **4 个 `-common` 库**（不算可执行模块）；原写「13 个」**是错的**（详见 §24.4） ⑤ `like-api` 的熔断注释 | §22.3 / §22.4 · `api.md` §3 / §9.5 ⇒ **收口在 `global`** |
| **D · 库级 / 不可观测（明确「不做」）** | **3** | ① gRPC **措辞**（grpc-go 区分未知方法/未知服务，grpc-java 共用一种）② gRPC **响应头 key 集合**（grpc-java 多 `grpc-accept-encoding` / `grpc-encoding`）③ percent-encoding 的 `~` 边界差 1 字节（L0 帧层；本域 24 条文案**无 `~`** ⇒ 客户端不可观测） | §20.4 / §20.5 ⇒ 两侧 `code` 语义一致 ⇒ **属库级，不是不一致** |

⇒ ~~**域内没有「已知但没改」的语义漂移了**~~；剩下的只有 **U8**（一条路径）+ **B2**（一条流程）+ 跨域与库级。
> 🔴 **第二十轮订正**：上面那句「域内没有已知但没改的语义漂移」是**提前收口** ——
> 当时的盘点**只覆盖了已经建了探针的层**（DDL / 错误码 / 事务语义），
> **没覆盖「SQL 语句层」** ⇒ 补上那一层后**抓出 3 处**（§29）。
> ⇒ **盘点结论的效力边界**：只对**已建探针的层**成立；**层没建 ⇒ 结论就不成立**。

> ✅ **2026-10-25 第十六轮更新**：**U8 已闭合**（⇒ §25）⇒ **B 类归零**；
> **A 类仍是 0 条**、**C 类仍是 5 条**（本域只读 ⇒ 收口在 `global`）、**D 类仍是 3 条**（库级 / 明确不做）⇒
> ⇒ **域内「可修漂移 = 0 条」「未知项 = 0 条」**（只剩 **B2** 这一条**流程项**）。

### 24.2 🆕 **U8** · 🔴 `create_folder` / `create_item` 的**成功路径**从未被双侧对拍

> ✅ **已于 2026-10-25 第十六轮闭合** ⇒ **§25**（15 case 全 SAME + 2 条变异验证）。
> ⚠️ 下面保留的是**第十五轮当时的原始记录**（只追加、不改上文）⇒ 读结论请以 §25 为准。

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

## 26. 2026-10-26 第十七轮（`align/favorite` 工作树 · 承 `fbac7fc`）—— 🔴🔴 **生产启动形态**：两个启动阻塞 + 一个漏配，**并推翻第十四轮「已修」的半修**

> **来源**：`global` 收口域移交过来的一条 —— 「本模块的 gRPC client 地址写法非法，**生产 jar 起不来**」。
> ⇒ 本轮的目标只有一个：**让真 fat jar 用生产 yaml 原样能起来**，并把这件事变成**可复跑的证据**。
> ⇒ 结论：**3 个缺陷**（2 个启动阻塞 + 1 个暴露面漏配）全部**已修**；
> 🔴 **并翻案了第十四轮的一条「已修」**（那是**半修** ⇒ 缺陷活到了发布那一刻）。

### 26.1 一句话结论

| | |
|---|---|
| **本轮新发现并修复** | **3 条**：① gRPC client 地址**两斜杠** ⇒ 启动期抛 `IllegalArgumentException`；② `web-application-type` 缺失 ⇒ Boot 判定 SERVLET ⇒ **无 servlet 容器 ⇒ 启动即死**；③ gRPC **reflection 暴露面**漏关（比真源多暴露一整个反射面） |
| **本轮翻案** | 🔴 **第十四轮 §22.3 的「启动阻塞 #1 已修」是半修** —— 那条修复只落在**探针启动参数**里，**生产 yaml 一行都没改** ⇒ 缺陷在产物里**原封不动** |
| **产品代码改动** | **2 行配置 + 2 个地址值**（`application.yml`）⇒ **零 Java 逻辑改动**；另 **2 段 pom 注释**纠错（行为零改动） |
| **新增守门** | **3 个测试类 / 14 例**（读**生产文件**、不走 `@SpringBootTest` 覆盖）⇒ **变异验证 4 条**全按预期转红 |
| **新增探针** | `tools/prod-startup-probe/favorite/` ⇒ **A/B/C 三 case 活体对拍**（真 fat jar + 生产 yaml 原样 + 真 PG） |
| **本轮新漂移** | **0 条**（3 条全是「RAG 侧本身的问题」，不是与 Go 的语义差） |
| **域内「未知项」** | **U8 已闭**（第十六轮）⇒ 本轮新增 **U9**（部署形态类，待拍板）⇒ 剩下 **B2**（流程项） |

### 26.2 🔴 缺陷 A：gRPC client 地址写成**两斜杠** ⇒ 启动期抛异常（`global` 移交的那条）

**位置**：`service/favorite/favorite-rpc/src/main/resources/application.yml`（修前 `:88` / `:94`）
⇒ `grpc.client.user-rpc.address: discovery://user-rpc` · `grpc.client.article-rpc.address: discovery://article-rpc`

**现象**（真 fat jar + 生产 yaml 原样，2026-10-26 实测）：

```
Caused by: java.lang.IllegalArgumentException: Incorrectly formatted target uri;
    expected: 'discovery:[//]/<service-name>'; but was 'discovery://article-rpc'
    at net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory
        .newNameResolver(DiscoveryClientResolverFactory.java:82)
    at io.grpc.internal.ManagedChannelImpl.getNameResolver(ManagedChannelImpl.java:760)
```

**判据（字节码实测，不是看文档猜的）** —— devh `grpc-client-spring-boot-starter` **3.1.0.RELEASE**：

```java
if ("discovery".equals(uri.getScheme())) {
    String path = uri.getPath();
    if (path == null || path.length() <= 1 || !path.startsWith("/")) {
        throw new IllegalArgumentException("Incorrectly formatted target uri; ...");
    }
    return newNameResolver(path.substring(1), args);   // ← 服务名 = path 去掉首斜杠
}
```

⇒ `discovery://user-rpc` 的 `path` = `""`（长度 0）⇒ **不合格** ⇒ 启动期抛异常。
⇒ 三条推论（都是**实测**出来的，不是推断）：
1. **与 Nacos 在不在线无关** —— 校验发生在**任何网络调用之前**；
2. **炸点在 gRPC client bean 后处理** ⇒ **连 Flyway 都跑不到** ⇒ 真 jar 起不来；
3. **三斜杠不改变服务发现名** —— `path.substring(1)` = `"user-rpc"` / `"article-rpc"`
   ⇒ **Nacos 上的名字一个字没改**。

**修法**：两处改三斜杠 `discovery:///user-rpc` / `discovery:///article-rpc`（+ 判据注释）。

⚠️ **这不是「与 Go 对齐」类的语义漂移修复** ——
两侧注册中心本来就不同（Go 用 etcd 键 `user.rpc` / `article.rpc`，
见 `service/favorite/rpc/etc/favorite.yaml:37-41` / `:43-47`；
那是**已签字的有意偏离**）⇒ 本条修的是「**RAG 侧写法非法导致起不来**」
⇒ **不登记**为有意偏离，也**不**进 `docs/Intentional_Deviations/`。

### 26.3 🔴 缺陷 B：`web-application-type` 缺失 ⇒ **无 servlet 容器却按 web 应用起** ⇒ 启动即死

**现象**（与 A 同一轮 A/B，**但更靠前** —— 修了 A 之后才撞出来）：

```
APPLICATION FAILED TO START
Description:
  Web application could not be started as there was no
  org.springframework.boot.web.server.servlet.ServletWebServerFactory bean defined in the context.
```

**根因**：classpath 上有 `jakarta.servlet`
（pom 里为编译期引用 `jakarta.servlet.http.HttpServlet/Request/Response` 而显式声明了
`jakarta.servlet:jakarta.servlet-api`）⇒ Spring Boot 的
`WebApplicationType.deduceFromClasspath()` 判定本进程是 **SERVLET web 应用**
⇒ 而本服务**没有** servlet 容器（不引 tomcat）⇒ 找不到 `ServletWebServerFactory`。

**修法**：`application.yml` 加

```yaml
spring:
  main:
    web-application-type: none
```

⇒ **不新增任何对外 wire 面**（与既有设计意图「纯 gRPC 进程默认没有 HTTP 端口」一致）
⇒ **可逆**（将来若要开 HTTP 面，改配置 + 加容器即可）。

⚠️ **连带更正了 pom 里两句错的注释**（原文两处都把结论说反了）：
- 「不引 `spring-boot-web-server` **不会**因此起 web 容器 —— 本服务无 WebApplicationType」
  ⇒ **错的**：决定 web 应用类型的是 **classpath 上有没有 jakarta.servlet**，不是有没有引那个包；
- 「`jakarta.servlet-api` 是纯 API jar，**加进运行时 classpath 不会启动任何容器**；
  favorite-rpc 仍然是 `WebApplicationType.NONE` 的纯 gRPC 进程」
  ⇒ **前半句错的**：**正因为它在 classpath 上**，Boot 才判定成 SERVLET；
  ⇒ **后半句也错的**：判定成 SERVLET 之后，因为**没有**容器，所以**起不来**，而不是「仍是 NONE」。

### 26.4 🔴 缺陷 C：gRPC **reflection 暴露面**漏关（比真源多暴露一整个反射面）

> ⚠️ 这条是**本轮核对时顺手查出来的**，与 A/B 同型：**其它 4 个 rpc 域都配了，本域漏了**。

| | 事实 |
|---|---|
| **Go 出处** | `service/favorite/rpc/favorite.go:45-47`：`if c.Mode == service.DevMode \|\| c.Mode == service.TestMode { reflection.Register(grpcServer) }` |
| **消费点判定** | `c.Mode` 来自内嵌的 `zrpc.RpcServerConf → service.ServiceConf`，其 tag 是 `json:",default=pro,options=dev\|test\|rt\|pre\|pro"`（go-zero v1.10.2 `core/service/serviceconf.go:35`）；而 `service/favorite/rpc/etc/favorite.yaml` **没有配顶层 `Mode`** ⇒ 取默认值 **`"pro"`** ⇒ **分支不成立** ⇒ **真源生产形态不注册 reflection** |
| **RAG 侧（修前）** | devh `GrpcServerProperties.reflectionServiceEnabled` **默认 `true`**；且 `GrpcReflectionServiceAutoConfiguration` 上是 `@ConditionalOnProperty(prefix="grpc.server", name="reflection-service-enabled", matchIfMissing=true)` ⇒ **不写这一行 = 开着** |
| **后果** | RAG 侧 `grpcurl -plaintext <host>:8082 list` 能列出**全部**方法并下载 proto 描述符；Go 侧同一命令连不上 |
| **修法** | `grpc.server.reflection-service-enabled: false`（与 `user-rpc` / `admin-rpc` / `message-rpc` / `hot-rpc` **同款显式关闭**） |
| **归类** | ✅ **可修漂移**（真源更严、RAG 更松）⇒ **本域已修** ⇒ **不进** `docs/Intentional_Deviations/` |

🔴 **推广面（跨域 ⇒ 本域只登记，收口在 `global`）** ——
其余 **5 个** rpc 模块**同样漏配**（本轮逐个核对过 Go 侧前提，**不是猜的**）：

| 模块 | RAG 侧 | Go 侧判据（已逐个核对） |
|---|---|---|
| `article-rpc` | ❌ 未配 | `service/article/rpc/article.go:47-48` 同一道 `Mode` 门；`etc/article.yaml` / `article.compose.yaml` **无顶层 `Mode`** ⇒ `pro` |
| `comment-rpc` | ❌ 未配 | `service/comment/rpc/comment.go:50-51`；`etc/comment.yaml` 无顶层 `Mode` ⇒ `pro` |
| `follow-rpc` | ❌ 未配 | `service/follow/rpc/follow.go:40-41`；`etc/follow.yaml` 无顶层 `Mode` ⇒ `pro` |
| `like-rpc` | ❌ 未配 | `service/like/rpc/like.go:37-38`；`etc/like.yaml` 无顶层 `Mode` ⇒ `pro` |
| `task-rpc` | ❌ 未配 | `service/task/rpc/task.go:40-41`；`etc/task.yaml` 无顶层 `Mode` ⇒ `pro` |

⇒ 已配的 5 个（修后）：`favorite-rpc`（本轮）· `user-rpc` · `admin-rpc` · `message-rpc` · `hot-rpc`
⇒ ⇒ **本域不碰别域文件**（AGENTS.md §5.3c）⇒ 收口在 `global`。

### 26.5 🔴🔴 **翻案：第十四轮 §22.3 的「启动阻塞 #1 已修」是半修**

| 原判（§22.3，第十四轮 `e76ea8e`） | 本轮实测 |
|---|---|
| 「启动加 `--spring.main.web-application-type=none`（对齐 Go 的『纯 gRPC、零 HTTP 端口』）」<br>落点写的是：「`pom.xml` 的依赖说明 + **探针启动参数**」 | 🔴 **那条参数只出现在 `tools/api-full-parity-probe/favorite/run.sh` 的 java 命令行里** ⇒ **`src/main/resources/application.yml` 里一行都没有** ⇒ **产物原封不动** |

⇒ 第十四轮的**现象观察是对的**（当时确实撞到了这条报错），
但**修法落在观测手段上，而不是被测物上** ⇒
⇒ **探针绿、测试绿、`mvn package` 成功，而真 fat jar 用生产 yaml 原样启动照样死。**

⚠️ **同一条修法在 §22.3 的 #2（`grpc-inprocess` scope）上是真修**（改了 pom）⇒
⇒ **两个「启动阻塞」里有一个是半修**，而台账把两条一起写成「**已修**」⇒ **台账也一起是错的**。

⇒ 元教训 ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-026**：
> **凡是靠「启动参数 / 环境变量 / `@SpringBootTest(properties=...)`」绕过去的缺陷，
> 都必须回到被绕过的那份配置里修掉，并且让探针**不再传**那个参数。**
> ⇒ **改了被测物**才算修；**只改了观测手段**不算。

⚠️ **处置已落地（不只是记一条）**：
`tools/api-full-parity-probe/favorite/run.sh` 里那行 `--spring.main.web-application-type=none`
**已摘掉**（并把原来的注释改写成「**故意不再传**」+ 为什么）。
⇒ 后果：**一旦有人把生产 yaml 里那一行删掉，这个探针会直接红**（进程起不来）⇒ 守门网成立。
⇒ 这也意味着 §22.3 那条修法**在探针侧同步失效了** —— 探针不再替 yaml 兜底。

### 26.6 守门网：**3 个测试类 / 14 例**（全部读**生产文件**，不走 `@SpringBootTest` 覆盖）

| 测试类 | 例数 | 挡什么 |
|---|---|---|
| `FavoriteRpcProductionAddressTest` | **8** | 生产 yaml 的**全部** `grpc.client.*.address` 必须是三斜杠；**真调** devh 的 `newNameResolver`（生产不抛 + 两斜杠**必须**抛）；client 集合 == 守门清单（新增 client 漏守门立刻红）；`target/classes` 与源码树一致 |
| `FavoriteRpcProductionStartupTest` | **2** | 生产 yaml 必须显式声明 `spring.main.web-application-type: none`；`target/classes` 与源码树一致 |
| `FavoriteRpcReflectionExposureTest` | **4** | 生产 yaml 必须显式 `grpc.server.reflection-service-enabled: false`；devh **默认值是 `true`**（⇒ 那行是承重的）；那个键**真能绑到** devh 的配置对象上（键名写错 ⇒ 绑完仍 `true` ⇒ 转红）；`target/classes` 与源码树一致 |

⇒ 三者都**不经过任何 `@SpringBootTest(properties=...)` 覆盖** ⇒ **生产配置第一次被真正跑过**。

**变异验证（4 条 ⇒ 守门网不是假绿）**：

| # | 注入 | 期望 | 实测 |
|---|---|---|---|
| **M1** | 生产 yaml 两处改回**两斜杠** | `FavoriteRpcProductionAddressTest` 转红 | ✅ **8 例 → 6 红**（报的是 `Incorrectly formatted target uri`） |
| **M2** | 生产 yaml 删掉 `web-application-type: none` | `FavoriteRpcProductionStartupTest` 转红 | ✅ **2 例 → 2 红** |
| **M3** | 生产 yaml `reflection-service-enabled` 改 `true` | `FavoriteRpcReflectionExposureTest` 转红 | ✅ **4 例 → 1 红** |
| **M4** | 生产 yaml **整行删掉** `reflection-service-enabled` | `FavoriteRpcReflectionExposureTest` 转红 | ✅ **4 例 → 2 红** |

### 26.7 新探针：`tools/prod-startup-probe/favorite/` —— **A/B/C** 三 case 活体对拍

> **它验的是哪一层**：「**生产配置原样**能不能真起得来」 ——
> 这是 `@SpringBootTest` **永远证明不了**的一层（§26.5 的半修就藏在这一层的缝里）。

| case | 参数 | 期望 | 实测 |
|---|---|---|---|
| **A** | `--grpc.client.{user,article}-rpc.address=discovery://<name>`（**按字节复现修前值**） | 🔴 启动即炸（`Incorrectly formatted target uri`） | ✅ 炸，栈顶 `DiscoveryClientResolverFactory.newNameResolver(...:82)` |
| **B** | **不传任何 `--grpc.client.*` 覆盖** | ✅ **真起来** | ✅ `gRPC Server started, listening on address: 0.0.0.0, port: 21300` + `Started FavoriteRpcApplication in 4.373 seconds` + `lsof` 核对端口 LISTEN |
| **C** | `--spring.main.web-application-type=servlet`（复现「**没有那一行**」的效果） | 🔴 启动即炸（`no ... ServletWebServerFactory bean defined`） | ✅ 炸，报错逐字命中 |

⇒ **三个 case 跑的是同一个 jar**，唯一变量是**那一个参数** ⇒ 差异**只能**来自该参数。
⇒ **只覆盖「环境相关」的三项**（PG 连接 / Nacos 地址 / gRPC 监听端口）——
Nacos 故意指向 `127.0.0.1:1` ⇒ 顺便证明「**注册中心不在也能起来**」。
⇒ 退出码：`0` = A 炸(uri) 且 C 炸(servlet) 且 B 起来。

⚠️ **本探针不证明什么**：下游 `user-rpc` / `article-rpc` 连不上（它们**根本不存在**）；
「下游在场」那条分支由 [`tools/grpc-contract-probe/favorite/`](../../../../tools/grpc-contract-probe/favorite/) 第二轮负责（§25）。
⚠️ 也不覆盖 Go 那个 **16062 的 DevServer 面** ⇒ 见 **U9**。

### 26.8 🔴 **U9**（新登记 · 待拍板）：Go 生产形态有 **16062** 那个 HTTP 面，RAG 侧没有

| | 事实（**完整锚点链**，本轮逐环核过） |
|---|---|
| **Go 配置** | `service/favorite/rpc/etc/favorite.yaml:23-30`：`DevServer: Enabled: true / Host: 0.0.0.0 / Port: 16062 / MetricsPath: /metrics / HealthPath: /healthz / EnableMetrics: true / EnablePprof: true` |
| **Go 启动路径** | `zrpc/server.go:63` → `c.SetUp()` ⇒ `core/service/serviceconf.go:77` → `devserver.StartAgent(sc.DevServer)` ⇒ `internal/devserver/server.go:79-82` 的 `if !c.Enabled { return }` |
| **🔴 关键一环** | **唯一的门控是 `Enabled`，不是 `Mode`** ⇒ 且 `Mode` 省略时取默认 **`pro`**（`core/service/serviceconf.go:35` 的 `json:",default=pro,..."`；`etc/favorite.yaml` 无顶层 `Mode`）⇒ ⇒ **生产形态也开着** |
| **RAG 侧现状** | `spring.main.web-application-type: none` ⇒ **纯 gRPC 进程** ⇒ **没有** 16062 那个面 |
| **性质** | **部署形态类偏离**（不是语义漂移）—— 那个面是 go-zero **自带**的 HTTP server，**不依赖 servlet 容器** ⇒ RAG 侧若要补，得**另加一个容器**（tomcat/jetty）⇒ **部署形态变更** |

⇒ **待拍板**（两条路）：
1. **RAG 侧也开** ⇒ 需引 servlet 容器 ⇒ **部署形态变更**；
2. **接受并签字** ⇒ 把「纯 gRPC、零 HTTP 面」登记为**有意偏离**。

⚠️ 本轮**不替用户选** ⇒ 归 **U9**，**不与 26.2/26.3 混谈**（那两条只解决「**能起来**」）。

### 26.9 本轮验证（全部实测）

| 项 | 结果 |
|---|---|
| **本域全量测试** | ✅ **BUILD SUCCESS** —— `mvn -o -pl service/favorite/favorite-{proto,common,rpc,api} -am test -Dsurefire.failIfNoSpecifiedTests=false`：<br>favorite-rpc **17 个测试类 / 99 例**（含 2 skip，**+3 类 / +14 例** = 本轮三个守门类）· favorite-api **8 类 / 94 例**（含 1 skip）· favorite-common **4 类 / 29 例** ⇒ **全绿，0 失败 0 错误** |
| **生产启动探针（新建）** | ✅ `bash tools/prod-startup-probe/favorite/run.sh` ⇒ **EXIT=0**（A 炸(uri) · C 炸(servlet) · B 起来 + 端口 LISTEN） |
| **变异验证** | ✅ **4 条**（M1/M2/M3/M4）⇒ 全部按预期转红 ⇒ **守门网不是假绿** |
| **gRPC 契约探针（两轮）** | ✅ `bash tools/grpc-contract-probe/favorite/run.sh` ⇒ **EXIT=0**（第一轮 36 case；第二轮 15 case 全 SAME） |
| **api 整域探针** | ✅ `bash tools/api-full-parity-probe/favorite/run.sh` ⇒ **EXIT=0** |
| **台账 vs 实际** | ✅ `tools/ledger-lint/favorite/check-ledger-vs-reality.py` ⇒ **EXIT=0** |
| **台账自检** | ✅ `tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **EXIT=0** |
| **注释锚点审计** | ✅ `tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **EXIT=0** |
| **共享面** | ✅ **零改动**（`docs/DRIFT/_INDEX.md` / `_MANIFEST.json` / `docs/Intentional_Deviations/_INDEX.md` / `docs/Pitfalls/_INDEX.md` / `AGENTS.md` / `docs/DRIFT/_cross/` / `docs/DRIFT/global/` / `service/common/**` 一律不碰） |

## 27. 2026-10-26 第十八轮（`align/favorite` 工作树 · 承 `0a4d2a8`）—— 🔴 **借 `global` 的新规则跑本域台账 ⇒ 当场抓出 3 处**，并把这两层**搬进本域自己的门禁**

> **来源**：`global` 收口域第三十二轮**共享工具**改动告知（`tools/check-ledger-selfcheck.py` 三处修复：
> N8~N13 六条去噪规则 / `sorted(srcs)` 输出可复现 / `--mutation-test` 阳性对照分支修正）。
> ⇒ **零 favorite 触碰、零 `service/common` 改动** ⇒ favorite 的可观察行为零变化。
> ⇒ **但**：他们同时点出「共享工具**没有 favorite 域预设** ⇒ `--domain favorite` 会报找不到台账文件」
> ⇒ 且**不会擅自扩**（要域内先登记候选）。

### 27.1 决定性动作：**不是登记候选，而是把规则搬进本域自己的门禁**

> 🔴 关键判断：**「共享工具没有我的域预设」的正确处置不是「等别人加」，
> 而是「让本域的台账有一把**自己**的梳子」。**
> ⇒ 否则本域的台账质量**取决于别的域什么时候有空** ⇒ 那是**外部依赖**，不是门禁。

⇒ 做法（**全程只读地借** global 的工具，**零改动**他们的文件）：
把 global 最新的 `tools/check-ledger-selfcheck.py` 拷到 `/tmp`，
用 `importlib` 只调它的 **L1（仓内路径存在性）** 与 **L4（Go 侧 file:line 出处）** 两层，
把本域 **12 份台账**喂进去 ⇒ **当场抓出 3 处**（见 §27.2）。

### 27.2 🔴 抓出的 3 处（**内容没错，但机器核不动**）

| # | 位置 | 原样 | 问题 | 改成 |
|---|---|---|---|---|
| 1 | `docs/Intentional_Deviations/favorite/rpc.md:122` / `:128` | `schema/naming.go:89-90` | 真源与全部第三方库里**按 basename 都找不到** ⇒ 机器判 MISSING | `gorm.io/gorm@v1.31.1/schema/naming.go:89-90`（**内容已核**：该处是 `IndexName()` ⇒ `formatName("idx", table, …)` ⇒ `idx_<table>_<column>` ✅） |
| 2 | `docs/Intentional_Deviations/favorite/rpc.md:70` | `metrics.go:22-27` | **跨库同名歧义**（go-redis / go-zero / 真源 各有一个 `metrics.go`）⇒ 机器无法判归属 | `service/favorite/rpc/internal/metrics/metrics.go:22-27`（**内容已核**：正是 `FavoriteRequestSecondsCounterMetric` 的 `prometheus.NewCounterVec` 定义 ✅） |
| 3 | `docs/DRIFT/favorite/rpc.md:637` | `core/service/serviceconf.go` 的 **83-90 行**（🔴 行号越界） | go-zero v1.10.2 该文件**共 89 行** ⇒ `initMode()` 的真实范围是 **83-89** | `core/service/serviceconf.go:83-89` |

⇒ **第 3 条是唯一一处「数字真的错了」**（前两条是**写法不够精确**）。

⚠️ **为什么第 1、2 条之前没人发现**：本域自检工具（`tools/ledger-lint/favorite/check-ledger-selfcheck.py`）
**没有这两层** ⇒ 它只看「§ 小节指针 / 编号闭合 / Go 源文件是否落点 / 裸行号锚点」
⇒ **路径写错 / 行号越界这两类，它压根看不见**。
⇒ 而 `global` 的共享工具**能看见**，但**没有 favorite 预设** ⇒ 看不见。
⇒ ⇒ **两头都看不见 ⇒ 这类缺陷就是活的** ⇒ 本轮补上。

### 27.3 🆕 本域门禁新增两层（`tools/ledger-lint/favorite/check-ledger-selfcheck.py`）

| 层 | 查什么 | 判据 |
|---|---|---|
| **⑤ 仓内路径存在性** | 台账里 `service/…` / `tools/…` / `docs/…` 路径存不存在 | **RAG 仓找不到 且 真源仓也找不到** 才算缺失（Go 真源也是 `service/` 布局 ⇒ 同一串路径在两个仓里都合法） |
| **⑥ Go 侧 `file.go:NN` 可解析** | 出处能不能落到真实文件、行号在不在范围内 | 解析顺序 **从精确到模糊**：① `module@version/…` ⇒ module cache；② `service/…` ⇒ 真源仓；③ 其余 ⇒ **末尾段最长匹配**（索引含**真源 + Go 标准库 + 真源 go.mod 列出的全部第三方库**，共 138 个） |

⚠️ **⑥ 层有三个「必须」**（都是本轮踩出来的）：

1. **必须含 Go 标准库**（`<GOROOT>/src`）⇒ 少了它，`net/http/server.go:2458`、`src/net/url/url.go:1001`
   这类引用会被判「文件找不到」⇒ **假阳性**。
2. **必须有 N5「记述历史纠错」去噪** ⇒ 本域台账里有 17 处引用**是历史记述**
   （例如「补成 `create_favorite_logic.go:280-282` ⇒ **越界**（该文件仅 170 行）」—— 记的是**当时**的错，不是现在的悬空引用）
   ⇒ 不去噪 ⇒ **17 条假阳性**。
   ⚠️ **判据必须行级局部**（引用所在行 ±1 行）——
   共享工具那一版是「取来源文件**前 2000 字**」⇒ **极宽** ⇒ 实测它把 178 条里的 **173 条**都去噪了
   ⇒ 那是**假阴性**（几乎什么都不查）⇒ **不能照抄**。
3. **必须报「同名多候选」** ⇒ 真源仓里 `api/internal/handler/routes.go` 这类路径**各域同名**
   （10 个域各有一份）⇒ 归属需人工确认 ⇒ 记**提示级**，不判死链。

### 27.4 🔴 工具**自身**修掉的 4 个 bug（本轮第一版输出**不能直接当结论**）

| # | bug | 症状 | 修法 |
|---|---|---|---|
| 1 | 省略写法的 dict key 结构写错 | `...` 的条目**漏进主循环** ⇒ 报「路径 `...` 不存在」 | 拆成 `exact` / `ellipsis` 两个字典 |
| 2 | 索引**缺 Go 标准库** | `net/http/server.go:2458` 等被误判 MISSING | 索引加 `<GOROOT>/src`（自动探测 `GOROOT` / `~/sdk/go*` / `/usr/local/go`） |
| 3 | **缺 N5 去噪** | 17 条「历史纠错」被误判死链 | 加行级局部 N5（正则见上） |
| 4 | 同库多候选**不报** | `create_favorite_folder_logic.go` 在 api/rpc 两侧同名 ⇒ 机器默默挑一个 | 候选多于命中数 ⇒ 记**提示级**「同名多候选」 |

⇒ **元教训**（⇒ 坑档 **PIT-FAV-RPC-027**）：
> **工具的第一版输出**必须**逐条核证**才能当结论 ——
> 本轮 8 条门禁里 **5 条是工具自己的 bug**、**2 条是台账在记述历史纠错**、**只有 1 条是真缺陷**。
> ⇒ 比例 **5 : 2 : 1** ⇒ 若不逐条核，就会把工具的 bug 当成台账的缺陷去「修台账」。

### 27.5 🆕 `--mutation-test`（工具自证）

⇒ 故意往台账文本里注入两类缺陷（一个必不存在的仓内路径 + 一个行号越界的 Go 锚点），
要求**必须被抓到**；再要求「**复原后 == 变异前**」。
⇒ 实测 **EXIT=0** ⇒ ⑤⑥ 两层**不是恒真**。

⚠️ 判据与 `global` 共享工具那边**同期**修的同型问题一致
（他们的阳性对照分支在非 task 分支上恒红 ⇒ 改成「复原后 == 变异前」）
⇒ **同一个教训，两处独立撞上** ⇒ 可信度高。

### 27.6 ⚠️ 残留：**57 条「跨库/同名歧义」**（**提示级**，本轮**不批量改**）

| basename | 条数 | 成因 |
|---|---|---|
| `helper.go` | 10 | favorite 的 api/rpc 两侧同名 |
| `create_favorite_logic.go` | 7 | 同上 |
| `errmsg.go` | 7 | favorite-common / api / rpc 三处同名 |
| `create_favorite_folder_logic.go` | 3 | api/rpc 两侧同名 |
| `delete_favorite_logic.go` | 3 | 同上 |
| 其余 | 27 | 跨域同名（`routes.go` 等）与跨库同名（`authhandler.go` 在 go-zero / oauth2 都有） |

⇒ **成因是「台账用裸 basename」** ⇒ 机器分不清是 api 侧还是 rpc 侧 / 是哪个域
⇒ **修法**是把它们写成 `service/favorite/rpc/internal/logic/helper.go` 这种**完整路径**。
⇒ ⚠️ **本轮不批量改的理由**：① 这是**提示级**不是门禁；② 逐条判定「当时指的到底是哪个文件」
需要**逐行核**（57 处）⇒ **半改比不改更危险**（把错的路径写死）。
⇒ **已量化 + 可枚举**（工具 `--json` 可导出）⇒ 留给下一轮或上线前统一收。

### 27.7 本轮验证

| 项 | 结果 |
|---|---|
| **本域台账自检** | ✅ **EXIT=0** —— 门禁级 **0** 条；新增 ⑤ 仓内路径（解析成功 48 / 命中真源 1 / 省略写法 2）· ⑥ Go 锚点（可解析 104 / 跨库歧义 57 / N5 记述 17） |
| **变异自证** | ✅ `--mutation-test` ⇒ **EXIT=0**（⑤⑥ 两层都能抓到注入的缺陷） |
| **台账 vs 实际** | ✅ `tools/ledger-lint/favorite/check-ledger-vs-reality.py` ⇒ **EXIT=0** |
| **注释锚点审计** | ✅ `tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **EXIT=0** |
| **产品代码** | ✅ **零改动**（本轮只动台账 + 本域工具） |
| **共享面** | ✅ **零改动**（`tools/check-ledger-selfcheck.py` 是 global 的文件 ⇒ **只读借用，未改**；`docs/DRIFT/global/` / `_INDEX.md` / `AGENTS.md` / `service/common/**` 一律不碰） |

## 28. 2026-10-26 第十九轮（`align/favorite` 工作树 · 承 `dcf90d9`）—— 🔴 **共享层 `DeadlineQueryTimeoutInjector` 改动的跨域验证**（`global` 第三十三轮）

> **来源**：`global` 收口域告知：改了 `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java`
> ⇒ 两处行为变化：
> ① **毫秒精度**（pgjdbc ≥ 42.7 的 `setQueryTimeoutMs(long)`，反射探测 ⇒ 取消时刻 == deadline 时刻）；
> ② **deadline 已过 ⇒ 不执行这条 SQL**（抛 `SQLTimeoutException` / SQLState 57014 ⇒ 翻成 `DEADLINE_EXCEEDED` ⇒ 事务回滚）
> —— ② 是**行为收窄**（原先「过期后仍会写入」）。
> ⇒ 他们问：**favorite 域若有任何测试/行为依赖「过期后 SQL 还能再跑最多 1 秒」这条旧行为 ⇒ 请回报。**

> 🔴🔴 **2026-10-26 第二十五轮加限定（`global` 第四十一轮 E62 通知触发）** ——
> 上面 ① 那句「**反射探测 ⇒ 取消时刻 == deadline 时刻**」是**共享层那版实现的描述**，
> **不是 favorite 生产路径的事实**：
> 🔴 探测用的是 `statement.getClass()`，而**生产路径上 statement 是 HikariCP 的代理类**
> ⇒ `setQueryTimeoutMs` **在它自己的类上不可达** ⇒ **探测必然失败 ⇒ 实际退回秒级向上取整**
> ⇒ ⇒ **「取消时刻 == deadline 时刻」在 favorite 的生产路径上不成立**。
> ⇒ 本域**运行时实测**（真 PG + 真 HikariCP + 真 MyBatis 插件链 + 真 mapper）⇒ 证据见 **§34**。
> ⇒ ⚠️ **本分支（`align/favorite`）的 `service/common` 里根本没有毫秒路径**
> —— `DeadlineQueryTimeoutInjector` 仍是**旧的秒级实现** ⇒ ⇒ 下面 §28.1 的「新实现」一列
> 指的都是**共享层那版**，不是本分支上跑着的代码。

### 28.1 ✅ 答案：**没有** —— favorite 域零条依赖那条旧行为

| favorite 的路径 | deadline 怎么来 | 新分支（②）会不会走到 |
|---|---|---|
| **权威读 HTTP 面**（`FavoriteAuthorityEndpointConfig:275-276`） | 调用点**紧贴**绑定 `bindToCurrent(now + GO_LOOKUP_TIMEOUT_MILLIS)`，而 `GO_LOOKUP_TIMEOUT_MILLIS = 3_000L`（`:135`） | ❌ **走不到** —— deadline 是**新绑**的 ⇒ SQL 执行时**必然未过期** |
| **gRPC 业务面** | `RpcInterceptor.onHalfClose` 绑的是**服务端自己的 2s 预算**（`rpc-timeout: 2s`） | ❌ favorite 的 handler 都是快 DB 操作，**没有**「SQL 跑在 2s 之后」的用例 |
| **Dispatcher**（`favorite.fact.dispatch.enabled` 默认**关**） | 单次 10s deadline | ❌ 域内**无测试**覆盖该路径 |
| **启动期建表**（Flyway） | **无** deadline | ❌ 拦截器**完全不动**（`remaining == NO_DEADLINE` ⇒ 直接 return） |

⚠️ **毫秒精度（①）对 favorite 的影响** —— ⚠️ **本条曾被我写成「完全相同」，那是措辞不精确，此处更正**（2026-10-26 第十九轮收尾自查）；
🔴🔴 **2026-10-26 第二十五轮再订正**：下表中间那一列描述的是**共享层那版代码的意图**，
**不是 favorite 生产路径的事实** ⇒ 🔴 **生产实际走的是最右列（秒级回退）**（E62 ⇒ 证据见 **§34**）：

| | 旧实现（秒级向上取整） | 新实现·**代码意图**（原样传毫秒） | 新实现·**生产实际**（E62：探测失败 ⇒ 秒级回退） |
|---|---|---|---|
| 给驱动什么 | `setQueryTimeout(ceil(R/1000))` ⇒ **整秒**，自**语句开始执行**起算 | `setQueryTimeoutMs(R)` ⇒ **剩余毫秒**，自**语句开始执行**起算 | **同左列** —— 回退路径是 `max(1, ceil(R/1000))`，与旧实现**逐字一致** |
| 取消时刻 | `t_exec + ceil(R/1000)*1000` | `t_exec + R` | `t_exec + ceil(R/1000)*1000` |
| **取消时刻差**（相对真实 deadline） | **`ceil(R/1000)*1000 - R`** ∈ **[0, 999] ms**（R 刚过整秒时最坏 ≈ 999ms） | 0 | **`ceil(R/1000)*1000 - R`** ∈ **[0, 999] ms** |

⇒ 🔴 **必须区分两个「差」**（这是本段最容易读错的地方）：

| | 是谁跟谁比 | 值 | 依赖什么 |
|---|---|---|---|
| **(A)** | **旧实现 ↔ 新实现** | **0** | 🔴 生产上**根本没走毫秒路径** ⇒ 两者**逐字一致** ⇒ 共享层这次改动对 favorite 的**可观察影响 = 0** |
| **(B)** | **Go 侧 ↔ RAG 侧** 的取消时刻差 | **`ceil(R/1000)*1000 - R`** ⇒ **favorite 恒等于 δ（毫秒级）** | ⇒ **纯算术**（见下）⇒ ⇒ **完全不依赖**毫秒路径是否生效 ✅ |

⇒ **R = 剩余毫秒数**。favorite 两条路径的 R：
- **权威读 HTTP 面**：`bindToCurrent(now + 3_000)`（`FavoriteAuthorityEndpointConfig:275-276`）⇒ `R = 3000 − δ` ⇒ 差 = **δ**
- **gRPC 业务面**：`interceptCall` 里 `deadlineEpochMs = now + 2_000`（`RpcInterceptor:170-172`，**每次调用**算）⇒ `R = 2000 − δ` ⇒ 差 = **δ**
（δ = 从「绑定 deadline」到「语句开始执行」之间的毫秒数 ⇒ favorite 两条路径都只有**几毫秒**）

⇒ ⇒ **结论不变**（favorite 域零红、零需改动、三个探针冻结值一条没动），
但**理由要写准 —— 而且理由变了**：
🔴 **不是**「毫秒精度让两边一致」（那条在生产上**不成立**，见 E62）；
✅ **是**「**favorite 的 R = 整预算 − δ ⇒ 秒级向上取整只多给 δ ⇒ 取消时刻差 = δ（毫秒级）**」
⇒ ⇒ 这条理由**不依赖**毫秒路径是否生效 ⇒ ⇒ **比原来那条更稳**（原来那条隐含依赖一个**未验证**的前提）。
⇒ 且 **favorite 域没有任何 handler 会在执行 SQL 之前烧掉大部分预算** ⇒ 那个「最坏 ≈ 999ms」的窗口
在 favorite 域**不成立** ⇒ 这才是「毫秒精度对 favorite 零影响」的**真正依据**（可推广的那条）。

⇒ ⚠️ **对照**：comment 域报的那条（E46）之所以能撞上「最坏 ≈ 1s」，
是因为它的 per-call deadline 会随**上游剩余预算**变小 ⇒ R 可能落进「刚过整秒」的区间。

⇒ 🔴🔴 **2026-10-26 第二十四轮订正**：本段原先写「favorite 的 client deadline **上界恒为 2s**
（`FavoriteRpcClient.capByClientTimeout` 把 `min(ctx 剩余, 2s)` 压进去）」⇒ **这条依据是错的** ——
它把 **api 侧**的**客户端封顶**当成了 **rpc 侧 SQL 预算**的决定因素。**两者不是一回事**：

| | 是什么 | 决定谁 |
|---|---|---|
| `favorite-api` 的 `FavoriteRpcClient.capByClientTimeout`（`favorite-api/.../client/FavoriteRpcClient.java:156-168`） | **api → rpc** 那次 gRPC 调用的客户端 deadline = `min(ctx 剩余, 2s)` | **客户端超时语义**（这次调用还剩多久）⇒ **不决定** rpc 内部 SQL 的 R |
| `favorite-rpc` 的 `RpcInterceptor`（`service/common/.../RpcInterceptor.java:170-172`） | **服务端**在**每次调用**上自己新绑 `deadlineEpochMs = now + timeout`（`rpc-timeout: 2s` ⇒ `application.yml:252`） | **SQL 注入器看到的 R** ⇒ `DeadlineState.bindToCurrent(deadlineEpochMs)`（同文件 `:281`） |

⇒ ⇒ **本分支（`align/favorite`）上正确的依据**：
**R = 服务端每次调用新绑的整预算 − δ**（业务面 2s / 权威读面 3s）⇒ δ 只是 handler 的前置耗时（毫秒级）
⇒ ⇒ 要落进「刚过整秒」的区间，需要 **δ ≈ 1s** ⇒ ⇒ favorite 的 handler 都是「先查后写」的快操作
⇒ ⇒ **δ 毫秒级** ⇒ ⇒ **窗口不成立** ⇒ ⇒ **与「客户端 deadline 上界」无关** ⇒ ⇒ 上界那条**别再当依据用**。

⇒ ⚠️ **跨分支提醒（`global` 第三十八轮回执触发）**：`global` 侧的 `service/common` 与**本分支不是同一版本**
—— 他们的 `RpcInterceptor` 用 `effectiveTimeoutMs = min(serverBudgetMs, clientDeadlineRemainingMs())`
⇒ **在他们的分支上，客户端剩余预算会压进服务端预算** ⇒ ⇒ 那边「favorite 躲开最坏窗口」的依据
**只能是「handler 不烧预算」**（与预算数值无关的那条）⇒ ⇒ 结论一致、**依据不同** ⇒
⇒ 🔴 **引用别域/别分支的 `文件:行号` 前，必须先确认该符号在**本分支**存在且语义相同**
（同名文件在不同分支上可能是两个不同实现 ⇒ 本域台账的全部行号一律指**本分支 `align/favorite`**）。

### 28.2 动态验证（两轮，叠 global 的实现跑）

| 轮 | 叠了什么 | 结果 |
|---|---|---|
| **A** | **只**叠主文件 | 🔴 `service/common` 自己的 `DeadlineQueryTimeoutInjectorTest.expiredDeadlineStillInjectsOneSecond:118` **红**（它断言的正是旧行为）⇒ **174 例里只有这 1 例红**<br>⚠️ 因为 `-am` 在 `common` 模块就 BUILD FAILURE ⇒ **favorite 的模块根本没跑到** |
| **B** | 主文件 + **他们改过的测试**一起叠 | ✅ **全绿**：`service/common` **177 例** · favorite-rpc **99 例** · favorite-api **94 例** · favorite-common **29 例** ⇒ **0 失败 0 错误** |

⇒ 🔴 **给 `global` 的落地提醒**：**主文件与测试改动必须同一次提交落地** ——
否则仓库是红的（守门没跟着改）⇒ 与 **PIT-FAV-RPC-026**（「改了被测物必须同时改守门」）同型。

### 28.3 三个探针带 overlay 复跑（全部 EXIT=0，冻结值一条没动）

| 探针 | 结果 |
|---|---|
| `tools/authority-live-probe/favorite/`（**正是 3s 预算那条路径**） | ✅ **EXIT=0** —— SAME 20 / OBS 0 / SKIP 1（共 21 case）⇒ 与基线**逐条一致** |
| `tools/api-full-parity-probe/favorite/` | ✅ **EXIT=0** —— SAME 24 / SAME_CODE 1 / SAME_BODY 2 / SAME_STATUS 4 / OBS 1（共 32 case）⇒ **与基线逐条一致** |
| `tools/grpc-contract-probe/favorite/` | ✅ **EXIT=0** —— 第一轮 36 case + 第二轮 15 case 全 SAME ⇒ **与基线逐条一致** |

⇒ **favorite 域对这次共享层改动：零红、零行为变化、零需改动。**

### 28.4 🔴 纠正一条**过期事实**：「favorite 域台账仍是 0 字节（从未被对照过）」

⇒ **不成立**。favorite 域的 **12 份台账全部非零**，合计 **≈ 430 KB**：

| 文件 | 字节 | 文件 | 字节 |
|---|---|---|---|
| `docs/DRIFT/favorite/rpc.md` | **213,799** | `docs/Pitfalls/favorite/rpc.md` | 65,762 |
| `docs/DRIFT/favorite/api.md` | 48,810 | `docs/Intentional_Deviations/favorite/rpc.md` | 23,481 |
| `docs/DRIFT/favorite/proto.md` | 15,216 | `docs/Pitfalls/favorite/api.md` | 20,608 |
| `docs/DRIFT/favorite/common.md` | 10,340 | `docs/Intentional_Deviations/favorite/api.md` | 7,649 |
| `docs/Intentional_Deviations/favorite/common.md` | 5,205 | `docs/Pitfalls/favorite/common.md` | 4,150 |
| `docs/Pitfalls/favorite/proto.md` | 4,059 | `docs/Intentional_Deviations/favorite/proto.md` | 1,336 |

⇒ **出处**：根 `AGENTS.md:32` 的域表 —— 那一行是 **2026-10-23 首轮**的快照
（它自己还标着「⚠️ **2026-10-25 本轮未做**」）。
⇒ 此后 favorite 域已做了 **19 轮**对照（台账 §0–§28）⇒ **不是「从未对照」**。

⚠️ **但有一层确实没做**（这条我**认**）：
⇒ 🔴 **「SQL 语句级」的双侧活体对拍**（同一条业务查询，Go 与 RAG 发出的 **SQL 文本 / 参数**是否逐条等价）
—— 现有证据是**三块拼起来**的，**不是**这一块：
| 层 | 状态 | 证据 |
|---|---|---|
| **DDL 层**（表结构 / 列类型 / 索引 / 约束） | ✅ **已双侧活体对拍** | `tools/ddl-parity-probe/favorite/` |
| **错误码契约层** | ✅ **已双侧活体对拍** | `tools/errmsg-probe/favorite/`（108 行 sha256 一致） |
| **事务语义层** | ✅ **已逐条源码对照** | §1 落地形态 + §2 的 D1–D6 + `FavoriteFactWriteService`（`@Transactional` self-invocation 静默失效那条）+ `TransactionTemplate` 显式 commit + `favorite_legacy_preflight.go` 逐行对齐（§16.3） |
| **SQL 语句层**（语句文本 / 参数 / 锁语义） | 🔴 **未做** | ⇒ **域内可排，尚未排** |

⇒ ⇒ 建议 `global` 把 E47 里那条改写成
**「favorite 的 SQL 语句层未做双侧活体对拍（DDL 层 / 错误码层 / 事务语义层已闭）」**，
而不是「favorite 台账 0 字节 / 从未对照」。

### 28.5 本轮验证

| 项 | 结果 |
|---|---|
| **favorite 全量测试（带 overlay）** | ✅ **BUILD SUCCESS** —— service/common **177** · favorite-rpc **99**（含 2 skip）· favorite-api **94**（含 1 skip）· favorite-common **29** ⇒ **0 失败 0 错误** |
| **三个探针（带 overlay）** | ✅ 全部 **EXIT=0**，冻结值**一条没动** |
| **恢复后** | ✅ `git checkout` 已还原 ⇒ **共享面零改动**；产物已重新出包回到本分支状态 |
| **产品代码** | ✅ **零改动**（本轮只动台账） |

---

## 29. 2026-10-26 第二十轮（`align/favorite` 工作树 · 承 `7629311`）—— 🔴🔴 **SQL 语句层**双侧活体对拍：**抓出 3 处 L1 可观察的漂移**（四层验证的最后一层）

### 29.1 一句话结论

favorite 域的**四层验证**里，前三层（DDL / 错误码契约 / 事务语义）**早就各自闭了**，
本轮补上**第四层（SQL 语句层）** ⇒ **当场抓出 3 处 L1 可观察的漂移**：
**RAG 侧的 INSERT / UPDATE 少绑时间列** ⇒ 落库是 `NULL` ⇒ **对外响应里的时间戳变成 0**。

| 缺陷 | 表 / 语句 | Go 侧 | RAG 侧 | 对外后果（L1） |
|---|---|---|---|---|
| 🔴 **1** | `favorite_folder` INSERT | 绑 `create_time` + `update_time` | **不绑** ⇒ `NULL` | `created_at`/`updated_at` = **0** |
| 🔴 **2** | `favorite_item` INSERT | 绑 `create_time` | **不绑** ⇒ `NULL` | `created_at` = **0** |
| 🔴 **3** | `favorite_folder` UPDATE（改名） | 顺带刷新 `update_time` | **不碰** | `updated_at` **不推进** |
| 🟡 **4** | `favorite_fact_outbox` INSERT | 绑 `technical_receipt_id`/`technical_input_hash` = `''` | **不绑** ⇒ `NULL` | 低影响（只写不读 · 投递路径默认关闭） |

⇒ **这 3 处此前三层全都没抓到**（§29.10 逐条说明为什么）。

### 29.2 为什么还需要第 ④ 层：**落库等价性只证「结果一样」，证不了「过程一样」**

前三层各自闭合时，每一层都**自洽**：
DDL 层说「`favorite_folder` 6 列全一致 ✅」、契约层说「响应字节逐字节相同 ✅」、
事务层说「三笔事务写同生共死 ✅」——
**但没有任何一层会去看「INSERT 到底绑了哪几列」。**

少绑一列 ⇒ 那一列的值从「Go 进程时钟」变成 `NULL` ⇒ 落库**内容**就变了 ⇒
可这一切在**前三层各自的观测口径里都不可见**（§29.10 逐条拆）。

### 29.3 探针设计（**零侵入**：只看 PG 服务端日志）

| 决策 | 理由 |
|---|---|
| **取证走 PG 服务端日志**（`log_statement=all`） | Go 侧是真源**冻结**，不能改它的 logger 配置；RAG 侧改 MyBatis 日志级别就**改变了被测面** ⇒ 只有服务端日志能让两侧**一行不改**地暴露真实 SQL |
| 附带好处 | `BEGIN`/`COMMIT`/`ROLLBACK` 与语句在**同一条时间线上** ⇒ 事务边界可判 |
| **两侧分流** | Go 侧 `PGAPPNAME=go_side` 环境变量（真源 DSN 硬编码在 `internal/model/db.go:21-28`，**没有** `application_name` 字段；pgx v5 把 `PGAPPNAME` 映射成 `application_name`：`pgconn/config.go:456`）· RAG 侧 JDBC URL 挂 `ApplicationName=rag_side` · 驱动自己用 `probe_marker` |
| **按用例切段** | 每个用例调用前，驱动用**独立连接**发 `SELECT 'SQLMARK::<side>::<case-id>::<seq>'` ⇒ 服务端日志里出现可识别标记 ⇒ 逐用例切片 |

⚠️ `PGAPPNAME` **只加在启动 Go server 的那一条命令上** ——
export 到整个脚本的话，psql 灌种子 / schemaprep 建表也会被标成 `go_side` ⇒ 污染 Go 桶。

### 29.4 🔴 实测记录：PG 16 的 `log_statement=all` **到底记什么**（不靠猜）

| 协议 / 形态 | 日志形态 | 证据 |
|---|---|---|
| simple（psql / 客户端插值） | `LOG:  statement: <sql>` | psql 执行 `INSERT` 实测 |
| extended（pgx / pgjdbc） | `LOG:  execute <名字>: <sql>` | pgx 连跑 3 次 ⇒ **3 行** execute |
| extended 的绑定值 | **独立**一行 `DETAIL:  parameters: $1 = '9001'` | 同上 |
| **parse / bind** | **不存在**（PG 16 实测 **0** 条） | ⇒ 一条语句恰好一行 ⇒ **不会重复计数** |
| 多行 SQL | **跨行**（原样保留换行） | `SELECT a,\n b\n FROM t` 实测跨 4 行 |

⇒ 解析规则：**带时间戳前缀的行 = 一条新记录；无前缀的行 = 上一条的续行**。

### 29.5 比对口径：**语义签名**，不是 SQL 文本

GORM 与手写 SQL 的生成差异是**库级噪声** ⇒ 逐条归一到
`(语句类别, 主表, 写入列集, WHERE 列集, 锁子句, ORDER BY, LIMIT/OFFSET, JOIN 表集, 投影)`，
差异分三档：`SEMANTIC`（**必须人工核证**）· `ORM-GENERIC`（库级生成差异）· `EQUIVALENT`（已证明等价）。

**三类必须剥离的噪声（两侧对称）**：
① `-- …`（GORM 的连接 ping）· ② `SHOW TRANSACTION ISOLATION LEVEL`（pgjdbc）·
③ `SET application_name = …`（pgjdbc 的连接参数回显）。
外加：**只包一条语句的 `BEGIN/COMMIT`**（GORM 默认给 Create/Update/Delete 套一层事务 ⇒ 单语句事务与自动提交**原子性等价**）；
⚠️ **包了两条以上的事务不能剥** —— 那才是真正的原子性边界。

**首轮基线（23 用例）**：

| 项 | 值 |
|---|---|
| 日志行数 / 记录数 / 语句记录数 | 1825 / 855 / **594** |
| marker 语句数 / parse+bind 记录数 | 46 / **0**（实测） |
| Go 侧语句数 / RAG 侧语句数 | 164 / 302 |
| **语义差异（SEMANTIC）** | **10 处** |
| ORM 生成噪声（ORM-GENERIC） | 43 处 |
| **L1 响应不同** | **3 例** |

⇒ **20 个用例的「语句类别序列」两侧逐条一致**（`SELECT → SELECT → BEGIN → SELECT → INSERT → INSERT → COMMIT` …）
⇒ 差异**全部集中在「写集合」这一维** ⇒ 锁 / 事务边界 / WHERE / 投影 / 排序 **全部一致**。

### 29.6 🔴 缺陷 1：`favorite_folder` 的 INSERT **不绑** `create_time` / `update_time`

**Go 侧（活体取证）**：
```sql
INSERT INTO "favorite_folder" ("folder_id","user_id","name","create_time","update_time")
VALUES ($1,$2,$3,$4,$5) RETURNING "id"
```
**RAG 侧（活体取证）**：
```sql
INSERT INTO favorite_folder (folder_id, user_id, name) VALUES ($1, $2, $3)
```

**Go 出处**：
- 真源 struct：`service/favorite/rpc/internal/model/data_model.go:10-11`
  `CreateTime time.Time \`gorm:"column:create_time;autoCreateTime"\`` /
  `UpdateTime time.Time \`gorm:"column:update_time;autoUpdateTime"\``
- GORM 在 INSERT 时把 `autoCreateTime` / `autoUpdateTime` 字段**赋 `curTime` 并绑进去**：
  `gorm.io/gorm@v1.31.1/callbacks/create.go:302-305`（切片分支）/ `:344-347`（单条分支）
  ⇒ 值来自 **Go 进程时钟**。

**RAG 侧出处**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteFolderMapper.java:41-47`
（`@Insert` 只有 3 列）。

**DDL 层为什么没抓到**：两边的 `create_time` / `update_time` **列定义完全一致**
（`timestamptz`，**可空、无 DEFAULT**）⇒ DDL 对照 ✅。
⇒ **列在、但没人往里写** ⇒ 这就是**跨层缝隙**：DDL 层看不见「谁负责填这一列」。

**L1 后果（活体取证）** —— `z01_list_folders_created`：
```
Go : n=1 [SqlProbeFolder|c=1793003071501|u=1793003071501]
RAG: n=1 [SqlProbeFolder|c=0|u=0]
```
（RAG 侧 `FavoriteRpcSupport.unixMillis`：`null → 0L`，
`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteRpcSupport.java:213-215`；
Go 侧 `helper.go:69-70` `folder.CreateTime.UnixMilli()` / `folder.UpdateTime.UnixMilli()`。）

⚠️ 🔴 **RAG 自己的注释把这个前提写成了「不可能发生」**：
`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteRpcSupport.java:209-211`
——「Go 的 `UnixMilli()` 对零值 `time.Time` 返回一个巨大的负数，
但那不会发生 —— **业务写入必带 autoCreateTime**」
⇒ **而 RAG 自己的 INSERT 路径就违反了这个前提** ⇒ 注释里的断言是**错的**（§3.3.2 高危项）。

### 29.7 🔴 缺陷 2：`favorite_item` 的 INSERT **不绑** `create_time`

**Go 侧（活体）**：
```sql
INSERT INTO "favorite_item" ("favorite_id","folder_id","user_id","target_id","target_type",
                             "target_revision","title","cover","create_time")
VALUES ($1,…,$9) RETURNING "id"
```
**RAG 侧（活体）**：
```sql
INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type,
                           target_revision, title, cover) VALUES ($1,…,$8)
```

**Go 出处**：`internal/model/data_model.go:30` `CreateTime time.Time \`gorm:"column:create_time;autoCreateTime"\``
+ `gorm.io/gorm@v1.31.1/callbacks/create.go:302-305`。
**RAG 出处**：`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteItemMapper.java:83-96`（`@Insert` 8 列）。

**L1 后果（活体）** —— `z02_list_items_created`：
```
Go : n=1 [t-78|post|req-title-2|c=1793003071610]
RAG: n=1 [t-78|post|req-title-2|c=0]
```
（Go 出处：`internal/logic/helper.go:83` `CreatedAt: item.CreateTime.UnixMilli()`。）

⚠️ **附带一个排序后果**：`favorite_item` 的读取是 `ORDER BY create_time DESC`
（Go `internal/model/favorite.go:86` /
RAG `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteItemMapper.java:40`），
而 PG 的 `DESC` 默认 **NULLS FIRST** ⇒ RAG 侧新建的行**排在最前**、且**多条新行之间的顺序未定义**
⇒ Go 侧按真实时间戳排序 ⇒ **两个及以上新行共存时列表顺序会分叉**。

### 29.8 🔴 缺陷 3：改名**不推进** `update_time`

**Go 侧（活体）**：
```sql
UPDATE "favorite_folder" SET "name"=$1,"update_time"=$2 WHERE folder_id = $3
```
**RAG 侧（活体）**：
```sql
UPDATE favorite_folder SET name = ? WHERE folder_id = ?
```

**Go 出处**：
- 真源 `internal/model/favorite.go:75-80`：`Model(&FavoriteFolder{}).Where("folder_id = ?").Update("name", name)`
- GORM 的 `Update`（**map 形式 Dest**）会**把 `autoUpdateTime` 列追加进 SET**：
  `gorm.io/gorm@v1.31.1/callbacks/update.go:235-254`
  （条件 `field.AutoUpdateTime > 0 && value[field.Name] == nil && value[field.DBName] == nil` ⇒
  map 里只有 `name` ⇒ `update_time` 被补上 `stmt.DB.NowFunc()`）。
- 真源 struct 的 tag：`data_model.go:11`。

🔴 **RAG 侧 mapper 的 javadoc 把这件事写反了**
（`service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/mapper/FavoriteFolderMapper.java:106-113`）：
> 「⚠️ 这不是遗漏：Go 的 `Update("name", name)` **也只写 name**。
> 但 GORM 的 `Update` 会同时刷新 `autoUpdateTime` 字段…」

⇒ **同一段注释里两句话自相矛盾**：前半句说 Go 只写 name（**错**），后半句说 GORM 会刷新 `autoUpdateTime`（**对**）。
⇒ 活体证据站在后半句这边 ⇒ **前半句是错的断言**（§3.3.2）。

**L1 后果（活体）** —— `z03_list_folders_user1001_after_rename`（改名后再读）：
```
Go : n=1 [Alpha2|c=1767294245678|u=1793003071663]   ← updated_at 推进到改名时刻
RAG: n=1 [Alpha2|c=1767294245678|u=1767294245678]   ← updated_at 原封不动
```
（Go 出处：`internal/logic/helper.go:70` `UpdatedAt: folder.UpdateTime.UnixMilli()`。）

### 29.9 🟡 缺陷 4：`favorite_fact_outbox` 的两个投递元数据列**不绑**（`NULL` vs `''`）

**Go 侧（活体）**：
```sql
INSERT INTO "favorite_fact_outbox" ("event_id","favorite_id","aggregate_version","payload","status",
  "retry_count","technical_receipt_id","technical_input_hash","technical_offset",
  "technical_received_at","delivered_at","created_at","updated_at") VALUES ($1,…,$13)
```
绑定值（从 PG 的 DETAIL 行实测）：`$7 = ''`（`technical_receipt_id`）· `$8 = ''`（`technical_input_hash`）·
`$9 = '0'`（`technical_offset`）· **`$10 = NULL`**（`technical_received_at`）· **`$11 = NULL`**（`delivered_at`）。

**RAG 侧（活体）**：`INSERT INTO favorite_fact_outbox (event_id, favorite_id, aggregate_version,
payload, status, retry_count, created_at, updated_at) VALUES ($1,…,$6)` ⇒ 那 5 列走 DB 默认/NULL。

⇒ 🔴 **订正一个容易搞错的点**：`technical_received_at` / `delivered_at` 两侧**都是 NULL**（**等价**）
—— 真源 struct 用的是**指针**（`favorite_fact.go:70-71` `*time.Time` ⇒ nil ⇒ GORM 绑 NULL），
**不是**零值时间。⇒ 真正的差异只有 **2 列**：`technical_receipt_id` / `technical_input_hash`
（Go = `''`，RAG = `NULL`）。

**影响评估（低）**：
- 这 2 列**只写不读** —— Go 侧只在「投递回执落库」时写（`internal/model/favorite_delivery.go:190-192`），
  取件查询（`favorite_delivery.go:141-147`）不读它们；
- 投递路径**两侧默认都不启用**（RAG `favorite.fact.dispatch.enabled=false`；Go 侧投递是独立 cmd）；
- ⚠️ 但要注意：Go 侧 `TechnicalInputHash string`（**非指针**，`favorite_fact.go:68`）⇒ 若 PG 里是 NULL，
  pgx 扫进 `string` 会**报错** ⇒ **两侧都不能让这列停在 NULL** ⇒ 修法必须绑 `''` 而不是「不绑」。

### 29.10 为什么前三层**全都**没抓到这 3 处（逐条拆）

| 层 | 为什么看不见 |
|---|---|
| ① DDL 层 | 两边的**列定义**完全一致（`timestamptz`、可空、无 DEFAULT）⇒ 列对得上，**「谁往里写」不在 DDL 的观测口径里** |
| ② gRPC 契约层 | 冻结值来自**种子行**（种子把时间列写成了常量）⇒ 只读种子的用例**永远看不到新建行的 NULL**；且契约层比对的是**响应字节**，新建行需要**先写后读**才暴露 |
| ③ 事务语义层 | 只验「业务行与事实行同生共死」⇒ **不关心业务行自己的时间列有没有值** |
| 落库等价性投影 | 投影**刻意排除了时间列**（两侧时钟必然不同 ⇒ 比不了）⇒ **NULL vs 真实值**正好被这个「合理排除」漏掉 |

⇒ **教训**：**每一层的排除项都会变成下一层看不见的缝隙**。
「时间列两侧时钟不同所以不比」是对的，但**没区分「值不同」与「一个是 NULL」** ⇒ 漏掉了最严重的那一类。

### 29.11 比对器的自证：`--mutation-test` **5/5 通过**

| 变异 | 期望 | 实测 |
|---|---|---|
| 抹掉一侧的 `FOR UPDATE` | 报 `lock` 维度 | ✅ 抓到（用例 `w03_create_item_ok`） |
| 抹掉一侧 INSERT 的一列 | 报 `write_cols` 维度 | ✅ 抓到（用例 `w01_create_folder_ok`） |
| 给一侧多加一句 | 报 `statement_count` 维度 | ✅ 抓到 |
| 改一侧 WHERE 的列名 | 报 `where_cols` 维度 | ✅ 抓到 |
| 🔴 **把 `SELECT *` 换成等价显式列清单** | **不得**报语义差异（阴性对照） | ✅ 只报 ORM 噪声 1 处 |

⇒ **比对器不是「见差异就报」的假绿**（阴性对照保证它对等价差异保持沉默）。

### 29.12 🆕 探针与坑档

- 🆕 `tools/sql-statement-probe/favorite/`：`run.sh`（编排）· `extract.py`（抽取 + marker 分段）·
  `compare.py`（语义签名比对 + `--mutation-test`）· `README.md`（设计 + **PG 16 实测记录**）
  · `out/report.md`（本轮基线证据）
- 🆕 `service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/sql/FavoriteSqlStatementParityTest.java`（23 用例 · marker 协议）
- 🆕 坑档 **PIT-FAV-RPC-028**：「**分层验证的排除项 = 下一层的缝隙**」

### 29.13 本轮验证

| 项 | 结果 |
|---|---|
| **新探针** | ✅ `bash tools/sql-statement-probe/favorite/run.sh` ⇒ 退出码 **1**（**有**语义差异 —— 这是**预期**的：它抓到了缺陷） |
| **两侧业务出口** | ✅ 23 个用例**全部一致**（探针自带断言，不一致直接挂）⇒ SQL 层可比 |
| **语句类别序列** | ✅ 20 个用例**两侧逐条一致** ⇒ 锁 / 事务边界 / WHERE / 投影 / 排序全部一致 |
| **分流泄漏** | ✅ **0 条**（两侧各自的 `application_name` 全程生效） |
| **变异自检** | ✅ **5/5 通过**（含阴性对照） |
| **favorite-rpc 全量测试** | ✅ **Tests run: 100 · Failures: 0 · Errors: 0 · Skipped: 3** ⇒ **BUILD SUCCESS**（skip 的 3 条含新增的 `FavoriteSqlStatementParityTest` —— 没有探针环境变量时按设计 skip） |
| **三个既有探针** | ✅ 全 **EXIT=0**：`grpc-contract`（36+15 case）· `authority-live`（20 逐字节一致 + 1 SKIP）· `api-full-parity`（32 case，冻结值一条没动） |
| **台账门禁** | ✅ `tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **门禁级 0 条**（含本轮新增的 30+ 处 Go / 仓内路径锚点，全部可解析）+ `--mutation-test` 通过 |
| **产品代码** | ✅ **零改动**（本轮只动台账 + 探针 + 一个测试类） |

> ⚠️ **一次环境红（已排除，结论不受影响）**：首次跑全量测试时，`FavoriteSchemaParityTest` 的
> Testcontainers 容器**端口转发器卡死** ⇒ JVM 挂在 `HikariPool.checkFailFast`（读 PG socket）。
> **确诊是环境问题**：容器内 PG 日志显示 `database system is ready to accept connections` 且
> `pg_stat_activity` 里**没有**该连接的后端；宿主直连该映射端口，TCP 能连上但 **PG 启动包无响应**。
> ⇒ 分阶段重跑（先 11 个不依赖容器的测试类，再 7 个依赖容器的）⇒ **两段全绿**；
> 之后一次干净整模块跑 **100 例全绿**。⇒ **不是代码问题**，但记在这里以免下次误判。


---

## 30. 2026-10-26 第二十一轮（`align/favorite` 工作树 · 承 `d871647`）—— 🔴 **共享复核器改进后的跨域复跑 ⇒ 抓出本域「修一处 ≠ 修完」的漏改**

### 30.1 触发：`global` 第三十四轮修好了我上一轮提的 4 条工具缺陷

`global` 复核我回执里的 4 条反馈 ⇒ **4 条全部成立**并已修完（`d52031e` + `fbfbdd9`）：
① **N7 去噪从「文件级」改成「引用行 ±1」** ② **第三方库清单改用真源 `go.mod` 权威**
③ **新增 `module@version/...` 精确定位通道** ④ **新增 Go 标准库通道**。

🔴 **① 对本域影响最大**：去噪改成行级后，`global` 那边的台账引用从「favorite 12 份 不悬空 3 条」
变成 **158 条** ⇒ **本域台账里 155 条此前被文件级去噪淹没的引用，第一次真被检查了**。

⇒ 我**只读**借他们修好的工具（拷 `/tmp` + `importlib` 调 L1/L4，原文件零改动）跑本域 12 份台账。

### 30.2 🔴 抓出：**同一处错误在台账里出现 3 次，我第十八轮只改了 1 次**

| 层 | 结果 |
|---|---|
| L1（仓内路径） | ✅ 58 个引用，**缺失 0 个**（2 条去噪 = 别域的省略写法，不算 FAIL） |
| L4（Go 侧 `file:line`） | ❌ **1 条 FAIL**：`core/service/serviceconf.go` 的 **83-90 行**（**行号超出文件范围**，该文件共 89 行） |

⇒ **这条正是我第十八轮抓出并「修好」的那一条**（§27.2）⇒ 但**它在本域台账里出现 3 次**：

| 位置 | 性质 | 第十八轮是否改 |
|---|---|---|
| `rpc.md` §27.2 表的**修正列** | 活指针（应指向真实范围） | ✅ 改成 `:83-89` |
| `rpc.md` §27.2 表的**错误列** | 引的是**当时写错的行号** | ❌ 漏改 |
| `rpc.md` §0 第十八轮**叙述行** | 同上 | ❌ 漏改 |
| `PIT-FAV-RPC-027` 的「真缺陷」行 | 同上 | ❌ 漏改 |

⇒ 本轮**三处同源写法全部改成「N 行」**（`core/service/serviceconf.go` 的 **83-90 行**）而**不是** `file:NN`
⇒ 记录**一字未丢**，但**不再被任何 `file:NN` 扫描器当成活指针**。
⇒ 改完重跑：`L1 = 0` · `L4 = 0` ✅。

### 30.3 顺带的确认：我的门禁**不落后**于共享工具

`global` 这轮修的 ②③④ 三条，**我本域工具第十八轮就自己实现了**（`tools/ledger-lint/favorite/check-ledger-selfcheck.py`：
`module@version/...` 精确定位通道 · 真源 `go.mod` 解析第三方库 · `<GOROOT>/src` 标准库通道）⇒
**本域门禁与共享工具同代**，不存在「等别人补」的缺口。

🆕 而且 §29 新写的锚点**全部用 `module@version/...` 精确写法**
（如 `gorm.io/gorm@v1.31.1/callbacks/create.go:302-305`）⇒ 在 `global` 修好的工具里
**不落进「同名文件跨库歧义」清单**（歧义清单里只有**旧写法** `v1.31.1/callbacks/update.go:235-254`）
⇒ **精确写法应当成为本域的新标准**（存量 62 条跨库歧义仍为提示级，**本轮不批量改**）。

### 30.4 教训：「修一处」≠「修完」

同一处错误在台账里出现 **N 次**时，只改其中 1 次 ⇒ **门禁仍然红**，而**本域自己的门禁看不见**
（我第十八轮的工具把它判成「N5 台账记述『删除/不存在』」⇒ 去噪 ⇒ 0 条）
⇒ **是靠「别的域把共享工具改好」才浮出来的**。

⇒ 判据：
> **修一个锚点前，先 `grep` 出它在**本域所有台账文件**里的**全部副本** ⇒ 一次改完。
> ⇒ 「表里有 N 行同款写法」时，**N 行都要改**（含叙述行与坑档）。

### 30.5 本轮验证

| 项 | 结果 |
|---|---|
| **`global` 修好的工具跑本域台账**（只读借，拷 `/tmp` + importlib 调 L1/L4） | ✅ 改前 **L4 = 1 FAIL** ⇒ 改后 **L1 = 0 · L4 = 0** |
| **本域门禁** | ✅ `tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **门禁级 0 条** + `--mutation-test` 通过 |
| **产品代码** | ✅ **零改动**（本轮只动台账） |
| **共享面** | ✅ **零改动**（`global` 的工作树与 `tools/check-ledger-selfcheck.py` 只读借用 ⇒ 跑完即弃） |

---

## 31. 2026-10-26 第二十二轮（`align/favorite` 工作树 · 承第二十一轮）—— 🔴 **提交前把本域门禁全跑一遍 ⇒ 抓出第二十轮遗留的 2 处错锚点**

### 31.1 触发：本来只是「提交前例行复跑」，结果门禁是**红的**

第二十一轮只动了台账，提交前例行复跑本域门禁 ⇒
`tools/assertion-audit/favorite/check-assertion-sources.py` **退出码 1** · **门禁级 2 条**：

```
[F3] .../sql/FavoriteSqlStatementParityTest.java:296  行号越界：`delete_favorite_folder_logic.go` 的 **336-378 行**，但该文件只有 96 行
[F3] .../sql/FavoriteSqlStatementParityTest.java:324  行号越界：同上
```

> ⚠️ 门禁原文里是「文件名:336-378」那种形式 ⇒ 此处按 §30 / PIT-FAV-RPC-029 的惯例改写成「**N 行**」
> ⇒ **记录一字不丢**，但**不再被任何 `file:NN` 扫描器当成活指针**（否则别的域的复核器会把它判成悬空/越界 ⇒ 假阳性）。

🔴 **这两处是第二十轮我自己写的文件**（SQL 语句层对拍测试类）⇒ **第二轮才被抓到**。

### 31.2 确诊：不是「行号越界」，是**锚点指错了文件**

| | |
|---|---|
| 写的 | `delete_favorite_folder_logic.go` 的 **336-378 行**（**logic 层**） |
| 实际应该是 | `service/favorite/rpc/internal/model/favorite_fact.go:336-378`（**model 层**）—— `DeleteFolderCascade` 在这里 |

⇒ 真源 `service/favorite/rpc/internal/logic/delete_favorite_folder_logic.go` **共 96 行** ⇒ 336-378 根本不在该文件；
`service/favorite/rpc/internal/model/favorite_fact.go` **共 378 行** ⇒ `:336` 正是 `DeleteFolderCascade` 的函数头。

⇒ **断言的语义是对的**（那两例确实走级联删除），**只是锚点指到了另一个文件** ⇒
§3.3.1「证据要带 `文件:行号`」的**形式**被满足、**内容**是错的 ⇒ **这类错误比「没写锚点」更危险**：
它让人以为这条证据已被核过。

### 31.3 顺手做了**全量语义核对**（不只修门禁抓到的那两条）

门禁只能判「行号越界」⇒ **指错文件但行号恰好在范围内**的锚点它**看不见**。
⇒ 我把该测试文件里**全部 20 个 Go 锚点**逐条解析到真源、打印对应行段、**人工核对语义**：

| 结论 | 数量 | 说明 |
|---|---|---|
| ✅ 语义正确 | **18** | 取证/基础设施 2（`db.go` DSN · `pgconn` PGAPPNAME）· 事实判定 1（`favorite_fact.go`）· 读路径 8 · 建路径 4 · 改/删路径 3 |
| 🔴 **指错文件** | **2** | `:296` / `:324`（本轮修） |
| 🟡 **I1：块内无锚点** | **1**（第 21 处 Go 出处注释） | `:221` 写「Go: 同上」⇒ 不是 `file:NN` 形式 ⇒ 无法唯一定位（本轮修） |

⇒ 18 条正确的**逐条**核对过 ⇒ 不是「抽查」。

### 31.4 另修了 1 条 I1：`Go: 同上` ⇒ 展开成真锚点

`:221` 的注释写 `// Go: 同上 ⇒ 用户不存在 ⇒ 空集（仍是一次 SELECT）`
⇒ 「同上」依赖阅读顺序，**机器定位不到**（§3.3.1 要求锚点可指到具体行）
⇒ 改成 `// Go: service/favorite/rpc/internal/logic/get_favorite_folders_logic.go:45-60 ⇒ …`。

### 31.5 🔴 顺带核掉 2 条 I3（**都是工具误报**，不是缺陷）

`assertion-audit` 另有 2 条 favorite-rpc 的 I3（「块内 `<pre>` 引用的 Go 源码在锚点文件里找不到」）⇒ 逐条回真源核对：

| I3 | 真源实况 | 判定 |
|---|---|---|
| `FavoriteRpcSupport.java:38` | `favorite_authority.go:60-70` **正是** `strictFavoriteJSON` 的真实范围（excerpt 落在 61-68 行） | ✅ **锚点正确** ⇒ 工具按「excerpt 必须从锚点**起始行**逐行对齐」判定 ⇒ **误报** |
| `FavoriteAuthorityLookupTimeoutTest.java:27` | `service/favorite/rpc/internal/authority/http.go:44` = `context.WithTimeout(..., 3*time.Second)` ✅；同文件 **80 行** = v2 变体 ✅ | ✅ **锚点正确** ⇒ excerpt **跨两段**，单锚点匹配器处理不了 ⇒ **误报** |

⇒ 记在这里，是为了下次有人看到 I3 时**不必重新查一遍**，也为了不让「I3 = 有问题」变成默认假设。

### 31.6 🔴 根因：**只跑了「本轮改动相关的那几个门禁」**

| 轮次 | 新写了带 Go 锚点的 Java 文件？ | 跑了 `assertion-audit`？ | 漏检 |
|---|---|---|---|
| 第二十轮 | ✅ 新增 `FavoriteSqlStatementParityTest.java`（**20 个 Go 锚点**） | ❌ **没跑** | 🔴 2 处错锚点入库 |
| 第二十一轮 | ❌ 只改台账 | ❌ **没跑**（只跑了 ledger-lint + 借 global 的工具） | 🔴 继续漏 |
| **第二十二轮** | ❌ | ✅ **跑了** | ✅ **抓到** |

⇒ **本域门禁不是「一劳永逸」的** ⇒ **每轮提交前要把本域门禁**全量**跑一遍**，
而不是「跑跟本轮改动有关的那几个」⇒ **本轮改动引入新文件 ⇒ 必跑管它的那个门禁**。

⇒ 坑档 **PIT-FAV-RPC-030**（新写文件 ⇒ 必跑管它的那个门禁；门禁只看「形式」抓不到「指错文件」⇒ 还得**全量语义核对**）。

### 31.7 本轮验证

| 项 | 结果 |
|---|---|
| **`assertion-audit`**（本域门禁） | ✅ 改前 **门禁级 2 条 · EXIT=1** ⇒ 改后 **悬空 0 · 行号越界 0 · 门禁级 0 条 · EXIT=0** |
| **20 个 Go 锚点全量语义核对** | ✅ 18 条正确（逐条回真源核对）· 2 条错锚点已修 · 1 条 I1 已修 |
| **`ledger-lint`（本域门禁）** | ✅ **门禁级 0 条** + `--mutation-test` 通过 |
| **`ledger-vs-reality`（本域门禁）** | ✅ **门禁级 0 条不一致** |
| **SQL 语句层探针复跑** | ✅ **基线复现**：记录 855 / 语句 594 / marker 46 / parse+bind 0 · go 164 条 · rag 302 条 · **语义差异 10 · ORM 噪声 43 · L1 响应不同 3 例**（与第二十轮**完全一致**）<br>✅ `out/report.md` 与 `out/extracted.json` **归一化（时间戳/雪花 ID）后逐字节一致** ⇒ 注释改动**零行为影响** ⇒ 已 `git checkout` 还原基线（不提交时间戳噪声） |
| **产品代码** | ✅ **零改动**（本轮只改**测试文件里的注释**） |
| **共享面** | ✅ **零改动** |

---

## 32. 2026-10-26 第二十三轮（`align/favorite` 工作树 · 承 `2875358`）—— 🔴 **修掉 SQL 层抓出的 4 处漂移 + 把对拍从「语句层」升级到「落库值层」⇒ 探针转绿**

### 32.1 起点：第二十轮抓出的 4 处漂移，本轮**全部修掉**

| # | 缺陷 | 修法 | Go 出处（消费点） |
|---|---|---|---|
| 1 | `favorite_folder` INSERT **不绑** `create_time`/`update_time` ⇒ 落库 NULL ⇒ L1 `created_at`/`updated_at` = **0** | 显式绑**应用层时钟**（`Instant.now(clock)`），两列绑**同一个** `now` | struct tag `internal/model/data_model.go:10-11` ＋ 赋值点 `gorm@v1.31.1/callbacks/create.go:246`（`curTime` **整条 INSERT 只取一次**）＋ `create.go:302-305` |
| 2 | `favorite_item` INSERT **不绑** `create_time` ⇒ 同上 | 显式绑应用层时钟 | `data_model.go:30` ＋ `create.go:302-305` |
| 3 | 改名 **不推进** `update_time` ⇒ L1 `updated_at` 不变 | UPDATE 的 SET 补 `update_time` | `gorm@v1.31.1/callbacks/update.go:236-254`（map 形式 Update **自动追加** `AutoUpdateTime` 字段） |
| 4 | `favorite_fact_outbox` 的 `technical_receipt_id`/`technical_input_hash` ⇒ Go 写 `''`、RAG 写 NULL | INSERT 显式写 `''` | `internal/model/favorite_fact.go:67-68`（**非指针** `string` ⇒ 零值是 `''` 而**不是** NULL）＋ `create.go:285-305`（无 `default` tag ⇒ 零值也**原样写入**） |

⇒ **顺带订正两处被实测证伪的 javadoc**（它们**写错了事实**，比没写更危险）：
① `FavoriteFolderMapper.insertFolder` 原写「`create_time`/`update_time` **由 PG 的 `now()` 生成**」
   ⇒ 🔴 **假**：DDL（`V1__create_favorite_folder.sql:24-26`）这两列**没有 DEFAULT** ⇒ 省略即 **NULL**。
② `FavoriteFolderMapper.updateNameByFolderId` 原写「Go 的 `Update("name", name)` **也只写 name**」
   ⇒ 🔴 **假且自相矛盾**（后半句自己又说「GORM 的 Update 会追加 autoUpdateTime」）⇒ 真实行为是**写两列**。

### 32.2 🔴 时钟源：必须用**应用进程时钟**，不能用 PG 的 `now()`

Go 的 auto-time 字段由 `stmt.DB.NowFunc()`（= `time.Now()`）在**应用层**赋值 ⇒ 值来自 **Go 进程时钟**。
⇒ 本实现绑 **Java 进程时钟**（`Instant.now(clock)`，用可注入的 `Clock` ⇒ 可测）
⇒ **时钟源同侧**（应用进程 vs 应用进程），**不是**数据库侧。
⇒ ⚠️ 用 DDL 的 `DEFAULT now()` 会引入**另一个时钟源**（PG 服务器）⇒ 且 DDL 根本**没有**这个 DEFAULT。

### 32.3 🔴 对拍工具本身有 4 个缺陷 ⇒ 本轮一并修（否则「转绿」是假的）

| # | 工具缺陷 | 后果 | 修法 |
|---|---|---|---|
| ① | `PARAM_RE` 只匹配**带引号**的 ⇒ PG 对 NULL 参数印的是**不带引号的** `NULL` ⇒ **整条参数被丢掉** | **静默**：分不清「绑了 NULL」与「根本没提到这个参数」 | 正则改为 `(NULL\|'…')`，NULL 存成 `None` |
| ② | `normalize()` 把 `$N` 改写成 `?` ⇒ 值提取跑在**归一化后**的 SQL 上 | Go 侧每个值都成了 `?` ⇒ 值比较全废 | 值提取改跑**原始** SQL |
| ③ | 比对器**只比列集**，不问 DDL | 把「值其实相同」误报成语义差异（实测 **3 列**：technical_offset / technical_received_at / delivered_at） | 引入 **DDL 感知**：`schema.__meta__` 带 `column_default` + `is_nullable` ⇒ 少写一列 ⇒ 取 DEFAULT（无则 NULL）⇒ 比**有效值** |
| ④ | **L1 响应比对是精确字符串相等**，而两侧是**两个独立进程** | 时间戳毫秒值**必然不同** ⇒ 该信号**结构上恒红** ⇒ 判别不了「修没修」 | 归一化：`c=`/`u=` 后是 **≥2 位数字** ⇒ `<ts>`；`0` 保持 `0`（= 没有值） |

### 32.4 🟡 「多投影一列」**不必然**是行为差异 ⇒ 走**带出处的显式登记**

Go 的 legacy 判定查 marker 用 `SELECT *`（GORM 的 `Take(&marker)`）⇒ 取回整表 **9 列**；
RAG 显式写 **8 列** ⇒ 探针报「投影列集不同」。

⇒ 🔴 **核到根因**：Go 的 struct `FavoriteLegacyFactMarker`（`internal/model/favorite_fact.go:28-37`）
**只有 8 个字段、没有 `approved_at`** ⇒ Go 把这一列取回来后**在 struct 映射层就丢弃**
⇒ 判定条件（`favorite_fact.go:228-232`）也从不引用它 ⇒ **行为等价**。

⇒ 探针**机械判不了**「下游用不用这一列」⇒ 不许它自己猜 ⇒ 走
**`tools/sql-statement-probe/favorite/equivalences.tsv`**：每条都要指到 Go 真源具体行，
且**出处会被自动校验**（文件不存在 / 行号越界 ⇒ **该条作废** ⇒ 差异照旧报成 SEMANTIC）
⇒ 「把红改成绿」必须**拿出能被验证的证据**，不能靠改开关。

### 32.5 🔴 变异验证（防假绿）：**探针自身 6 条 + 产品级 2 条**

| 层 | 变异 | 期望 | 实测 |
|---|---|---|---|
| 探针 | `drop_lock` | 抓到 lock 维度 | ✅ |
| 探针 | `drop_write_col`（**保对齐**地删末列 + 末值） | 抓到 write_cols 维度 | ✅ |
| 探针 | `extra_stmt` | 抓到 statement_count | ✅ |
| 探针 | `where_col` | 抓到 where_cols | ✅ |
| 探针 | `noop_projection`（阴性对照） | **不得**报语义差异 | ✅ |
| 探针 | `drop_write_col_with_default`（**新增**阴性对照：删掉 `technical_offset` 这个 **DEFAULT 0** 的列） | **不得**报语义差异 | ✅ ⇒ 证明 **DDL 感知判定真在工作**，不是「把差异全吃掉」 |
| **产品** | 🔴 改坏 `insertFolder`（不绑时间列） | 探针**转红** | ✅ **语义差异 1 · L1 响应不同 1 · EXIT=1** |
| **产品** | 🔴 改坏 `updateNameByFolderId`（不推 `update_time`） | 探针**转红** | ✅ **语义差异 1 · EXIT=1** |

⇒ ⚠️ **变异脚本本身踩过的坑**（已在代码注释里）：旧变异只删列不删值 ⇒ 列/值**错位**
⇒ 新版比对器判成「不可判定」⇒ **抓不到** ⇒ **假绿** ⇒ 必须**保对齐**地删列 + 删值。

### 32.6 ⚠️ 两层信号**各管一类**，缺一不可（产品级变异 M2 暴露的）

M2（改名不推进 `update_time`）里 **L1 差异 = 0**，只有 **write_cols 维度**报了差异。
⇒ 原因：归一化把「推进 / 不推进」抹平成「有值 / 无值」⇒ L1 那层管不了「值变了」。

⇒ ⇒ **两层互补**：
| 层 | 管的类 | 抓不到 |
|---|---|---|
| **L1 归一化** | 「**有没有值**」（NULL→0 vs 真实毫秒） | 「值变了但都非零」 |
| **write_cols（有效值）** | 「**列写没写 / 写的值同不同**」 | 「两侧都写了同一列但值不同」（**跨进程本就不可比**，不判） |

⇒ ⇒ 对本域，这两类**恰好覆盖全部 4 处漂移** ⇒ 组合起来是完整的。

### 32.7 本轮验证

| 项 | 结果 |
|---|---|
| **SQL 语句层探针** | ✅ **语义差异 0 · L1 响应不同 0 · EXIT=0**（第二十轮是 10 / 3 / 1）⇒ **两侧语句层语义一致** |
| **探针变异自检** | ✅ **6/6 全通过**（4 阳性 + 2 阴性对照） |
| **产品级变异** | ✅ **2/2 转红**（M1 语义差异 1 + L1 差异 1；M2 语义差异 1）⇒ 绿是**有证据的绿** |
| **基线可复现** | ✅ 三次独立运行（干净 / M1 / M2 / 干净）采集统计一致（记录 855 / 语句 594 / marker 46 / go 164 / rag 302） |
| **产品代码** | ✅ 4 处漂移已修（3 处 mapper + 2 处 javadoc 订正）· **新增 1 个 `Clock` 字段**（`FavoriteDomainService`） |
| **新增守门测试** | ✅ `FavoriteFolderItemTimeColumnTest`（**5 例** · **真 PG** + 真 Flyway + 真 mapper）⇒ 断言 folder/item 的时间列是**应用层传入值**、改名**推进** `update_time`、三列**无 DEFAULT**、outbox 两个投递列是**空串非 NULL**<br>✅ **变异验证**：改坏 `insertFolder` ⇒ **2 例转红 · EXIT=1** ⇒ 绿是**有证据的绿** |
| **全量测试** | ✅ **105 例 / 0 失败 / 0 错误 / 3 skip · BUILD SUCCESS**（100 + 新增 5）<br>⚠️ 期间**踩到一次假红**（34 例红在 `favorite-api`）⇒ 确诊是**同工作树并发跑 maven**（探针自己会 `mvn package` + `mvn test` ⇒ 两次 maven 同写一个 `target/`）⇒ **单独重跑全绿** ⇒ 坑档 **PIT-FAV-RPC-032** |
| **另外三个探针** | ✅ grpc-contract EXIT=0 · authority-live EXIT=0 · api-full-parity EXIT=0（32 case：SAME 24 / SAME_CODE 1 / SAME_BODY 2 / SAME_STATUS 4 / OBS 1）|
| **共享面** | ✅ **零改动**（`service/common/**` · `docs/DRIFT/_INDEX.md` · `docs/DRIFT/global/**` · `tools/check-ledger-selfcheck.py` 全未碰） |

---

## 33. 2026-10-26 第二十四轮（`align/favorite` 工作树 · 承 `4a875d0`）—— 🔴 **跨分支论证订正**：结论对、依据错

### 33.1 触发

`global` 第三十八轮回执指出：我在第十九轮回执里的 **§4** 用「**client deadline 上界恒为 2s**」
当「favorite 结构上更难撞上最坏窗口」的依据 ⇒ 🔴 **上界 ≠ 贴近** ⇒ 那条不是结构性论据。

⇒ ⇒ 他们的建议是：§4 的落点应落在**理由 2**（**handler 不烧预算 + SQL 是快操作**）—— 那条与预算数值无关，才是可推广的。

### 33.2 🔴 先核自己的台账，**不要直接认领**

⇒ ✅ 台账第十九轮 §28.1 **早已写对过**（该段末尾明写：
「**不要把『上界恒为 2s』当成『不会落进最坏区间』的依据** —— 那是**两条独立的理由**，别混用」）
⇒ ⇒ **真正要改的是那段「对照」写法**（它把 2s 上界摆在了结论附近，容易被读成依据）。

### 33.3 🔴 核完发现：**依据确实错了，而且比对方指出的还更具体**

`global` 说的是「公式两侧同款、favorite 没有结构性差异」。⇒ 我去核**本分支**的实现 ⇒ **本分支的公式与他们不同**：

| | 是什么 | 决定谁 |
|---|---|---|
| `favorite-api` 的 `FavoriteRpcClient.capByClientTimeout`（`favorite-api/.../client/FavoriteRpcClient.java:156-168`） | **api → rpc** 那次 gRPC 调用的**客户端** deadline = `min(ctx 剩余, 2s)` | **客户端超时语义** ⇒ **不决定** rpc 内部 SQL 的 R |
| `favorite-rpc` 的 `RpcInterceptor`（`service/common/.../RpcInterceptor.java:170-172`） | **服务端每次调用自己新绑** `deadlineEpochMs = now + timeout`（`rpc-timeout: 2s` ⇒ `favorite-rpc/src/main/resources/application.yml:252`）⇒ 再由 `:281` 的 `DeadlineState.bindToCurrent(deadlineEpochMs)` 交给 SQL 注入器 | **SQL 预算 R** ✅ |

⇒ ⇒ **本分支上正确的依据**：
**R = 服务端每次调用新绑的整预算 − δ**（业务面 2s / 权威读面 3s）
⇒ δ = handler 的前置耗时（favorite 都是「先查后写」的快操作 ⇒ **毫秒级**）
⇒ ⇒ 要落进「刚过整秒」的区间，需要 **δ ≈ 1s** ⇒ ⇒ **窗口不成立**
⇒ ⇒ **与「客户端 deadline 上界」完全无关** ⇒ ⇒ 那条**彻底不作为依据**。

⇒ ⇒ 顺带：**`global` 侧的 `service/common` 与本分支不是同一版本**
（他们 `:232-237` 的 `effectiveTimeoutMs = min(serverBudgetMs, clientDeadlineRemainingMs())`
vs 本分支 `:170-172` 的服务端自绑）⇒ ⇒ **同一路径在两个分支上是两个不同的文件/实现**
⇒ ⇒ 在**他们**的分支上，「favorite 躲开最坏窗口」的依据**只能是「handler 不烧预算」**（与预算数值无关那条）
⇒ ⇒ **两边结论一致、依据不同** ⇒ ⇒ 各写各的，**不要跨分支搬行号**。

### 33.4 教训（同型坑档 **PIT-FAV-RPC-033**）

1. 🔴 **符号存在 ≠ 符号决定你要论证的那个量** ⇒ 「上界恒为 2s」听起来合理，但控制的是**另一个量**。
2. 🔴 **同名文件在不同分支上可能是两个不同实现** ⇒ 跨域/跨分支引用 `文件:行号` 前，
   **必须确认该符号在本分支存在且语义相同** ⇒ 本域台账的全部行号一律指**本分支 `align/favorite`**。
3. ⇒ **被别域指出时先核自己的台账** —— 这次核完发现**台账别处早已写对**，要改的是**对照段的写法**，
   而不是整段推翻 ⇒ **认领要认到「具体哪一句错」，不是「我错了」**。

### 33.5 本轮验证

| 项 | 结果 |
|---|---|
| **符号存在性核对** | ✅ `capByClientTimeout` 在**本分支**存在，但位于 **`favorite-api`**（`favorite-api/.../client/FavoriteRpcClient.java:156-168`）⇒ 与 rpc 侧 SQL 预算**无关** |
| **本分支 R 的决定者** | ✅ `service/common/.../RpcInterceptor.java:170-172`（服务端每次调用新绑 `now + timeout`）+ `:281`（`bindToCurrent`）⇒ `rpc-timeout: 2s`（`application.yml:252`） |
| **本轮性质** | ✅ **纯台账订正** ⇒ **产品代码零改动** · **共享面零改动** |

## 34. 2026-10-26 第二十五轮（`align/favorite` 工作树 · 承 `a4a7ee0`）—— 🔴 **核 `global` 第四十一轮 E62：毫秒精度在本域生产路径上到底生效没有**

> 🔴 **第二十七轮的部分取代**：本节记的是「**当时**生产实际走秒级回退」⇒
> `global` **第四十五轮**（`180f865`）已把毫秒探测改成**先 unwrap 到驱动实现类** ⇒
> ⇒ **合并后**生产实际会变成「**毫秒精度生效**」⇒ ⇒ 本节的「生产实际」列与 §28.1 的 (A)/(B) 两行**将在合并后过期**
> ⇒ 合并时的动作清单 ⇒ **§36.5**；证伪他们量化口径的实测 ⇒ **§36.2**。

> **来源**：`global` 第四十一轮登记 **E62**（`docs/DRIFT/global/_README.md`）⇒ 告知 favorite owner：
> E46 的**毫秒精度**在**连接池代理**路径上**不生效** ⇒ SQL 超时退回**秒级向上取整**
> ⇒ 要求 favorite 域**核对台账中凡写「毫秒精度已生效 / 已对齐」的表述并加限定**。
>
> 🔴 **本轮边界**：E62 的**修法**落点在 `service/common` ⇒ **global 单写权** ⇒ **favorite 不改共享层**。
> ⇒ 本轮只做**三件事**：① **核事实**（不直接采信对方的结论）② **加限定**（订正本域台账表述）
> ③ **留下守门**（钉住本域生产路径的当前有效行为）。

### 34.1 🔴 先核事实：机制**成立**（静态）

`global` 给的机制是：毫秒探测用 `statement.getClass()`，而生产路径上 statement 是 **HikariCP 代理类** ⇒ 探测必然失败。

| 事实 | 核法 | 结果 |
|---|---|---|
| pgjdbc **有**毫秒 API，且只挂在**实现类**上 | `javap org.postgresql.PGStatement` / `org.postgresql.jdbc.PgStatement` | 接口**没有**；实现类**有** `setQueryTimeoutMs(long)` / `getQueryTimeoutMs()` ⇒ 与 `global` 的 javadoc **一致**（**42.6.2 与 42.7.13 都一样** ⇒ 与版本无关） |
| HikariCP 代理类**不暴露**毫秒 API | `javap com.zaxxer.hikari.pool.HikariProxyPreparedStatement`（4.0.3 / 5.0.1 / 7.0.2 全查） | 只有 `getQueryTimeout()` / `setQueryTimeout(int)` ⇒ **没有** `setQueryTimeoutMs` ⇒ ⇒ `statement.getClass().getMethod("setQueryTimeoutMs", long.class)` **必然抛 `NoSuchMethodException`** |
| 秒级路径写入的**原始毫秒**是整秒 | `javap -c org.postgresql.jdbc.PgStatement` | `setQueryTimeout(int)` 的字节码是 `i2l` → `lmul 1000` → `invokevirtual setQueryTimeoutMs` ⇒ ⇒ 驱动内部只有一个毫秒字段（`timeout`）⇒ 秒级路径写进去的是 **`ceil(R/1000)*1000`** |

⇒ ⇒ **机制成立**：按运行期类反射找毫秒方法 ⇒ 在**池化**路径上**必然失败** ⇒ 静默回退到秒级。

### 34.2 🔴 本域运行时实测（真 PG + 真 HikariCP + 真 MyBatis 插件链 + 真 mapper）

> 守门类：`service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteSqlTimeoutProductionPathTest.java`
> （**4 例**；`@SpringBootTest(classes = FavoriteRpcApplication.class)` ⇒ **生产那个注入器原样生效**；Testcontainers **真 PG 16**）

| # | 断言 | 实测结果 |
|---|---|---|
| 1 | 注入器看到的 statement 是**代理类** | `com.zaxxer.hikari.pool.HikariProxyPreparedStatement` ✅ |
| 2 | 该类上 `setQueryTimeoutMs` **不可达** | `NoSuchMethodException` ✅ |
| 3 | **有效超时是秒级向上取整** | 代理层 `getQueryTimeout()` = **2** 秒；驱动内部**原始毫秒** = **2000**（**整秒**）—— R ≈ 1700 ⇒ 若是毫秒路径应为 ≈1700 ⇒ ✅ **判定为秒级回退** |
| 4 | **阴性对照**：同一个 PG **不走连接池** | statement = `org.postgresql.jdbc.PgPreparedStatement` ⇒ 毫秒 API **可达** ⇒ ✅ 差别在**连接池代理**，不在驱动 |

⇒ 🟢 **判别器为什么可靠**（这条最容易做成恒真，所以专门写了**阳性对照**）：
代理层 `getQueryTimeout()`（int 秒）**分不开**两条路径 —— pgjdbc 的 `setQueryTimeout(int)` 内部就委托给
`setQueryTimeoutMs(seconds*1000)` ⇒ 两条路径都被抬成整秒 ⇒ **只有驱动内部的原始毫秒能区分**。
⇒ 而原始毫秒要 **unwrap 到驱动实现类**才读得到（代理类不暴露）⇒ 守门里就是这么做的。
⇒ **阳性对照**：在**无池**那条连接上直接 `setQueryTimeoutMs(1700)` ⇒ 原始毫秒实测 = **1700**
⇒ ⇒ 与生产路径上的 **2000** **明确不同** ⇒ ⇒ 这条断言**真的在区分两条路径**，**不是恒真**。

⇒ 🟢 **没有 deadline 时注入器完全不动**（定时任务 / MQ 消费 / 启动期建表不受影响）—— 第 4 例钉住。

### 34.3 🔴🔴 叠加 `global` 那版实现复跑 ⇒ **同一组断言仍然全绿** ⇒ 毫秒路径**没走**

> **只读 overlay**：`global` 的 `DeadlineQueryTimeoutInjector.java`（**含毫秒路径**）+ 他们的
> `DeadlineQueryTimeoutInjectorTest.java` 覆盖到本分支 ⇒ 跑完 `git checkout` **还原**
> ⇒ **共享面零改动**（`git status` 已确认还原干净）。

| 项 | 结果 |
|---|---|
| `service/common` 的 `DeadlineQueryTimeoutInjectorTest`（**他们那版**） | ✅ **10 例全绿** |
| 本域 `FavoriteSqlTimeoutProductionPathTest` | ✅ **4 例仍全绿** ⇒ **有效超时仍是 2000（整秒）** |

⇒ ⇒🔴 **决定性**：**带毫秒路径的实现**与**旧的秒级实现**在 favorite 的生产路径上
**给出完全相同的有效值** ⇒ ⇒ **毫秒路径在本域生产路径上根本没被走到**
⇒ ⇒ **E62 在 favorite 域以运行时证据成立**（不是转述，是本域自己跑出来的）
⇒ ⇒ **共享层这次改动对 favorite 的可观察影响 = 0**（生产上）。

### 34.4 🔴 台账订正清单（本轮改掉的每一句）

| 位置 | 原写法 | 改后 |
|---|---|---|
| **§28 引言 ①** | 「毫秒精度（…反射探测 ⇒ 取消时刻 == deadline 时刻）」 | 加上限定：**这是共享层那版实现的描述，不是 favorite 生产路径的事实** ⇒ 生产实际是**秒级回退** |
| **§28.1 对比表** | 两列（旧实现 / 新实现） | **三列**：旧实现 / 新实现·**代码意图** / 新实现·**生产实际** ⇒ 把「意图」与「事实」**分列** |
| **§28.1 结论句** | 「不是『完全相同』，而是『差 δ（毫秒级）』」 | 🔴 **区分两个「差」**：**(A)** 旧↔新 = **0**（生产上两者逐字一致）；**(B)** Go↔RAG = **δ**（纯算术）⇒ ⇒ 真正的理由是 **(B)**，它**不依赖**毫秒路径是否生效 ⇒ **比原来那条更稳** |
| **§0 第十九轮行** | 「毫秒精度 ⇒ 取消时刻差 δ（毫秒级）」 | 补第二十五轮订正指针 |

⇒ 🟢 **结论本身没变**：favorite 域**零红、零需改动**、三个探针冻结值一条没动 ⇒ **§24.1 的 A 类仍是 0**。
⇒ 🔴 **变的只是「理由」**：原理由隐含依赖「毫秒路径会生效」这个**未验证前提** ⇒ 现在换成**纯算术**依据。

### 34.5 本轮验证

| 项 | 结果 |
|---|---|
| **本域守门（新增）** | ✅ `FavoriteSqlTimeoutProductionPathTest` **4 例全绿**（真 PG + 真 HikariCP + 真 MyBatis 链 + 真 mapper） |
| **本域全量测试** | ✅ **BUILD SUCCESS** —— `mvn -o -pl service/favorite/favorite-{proto,common,rpc,api} -am test -Dsurefire.failIfNoSpecifiedTests=false`：favorite-rpc **109 例**（含 3 skip）· favorite-api **94 例** · favorite-common **29 例** ⇒ **0 失败 0 错误** |
| **叠加 `global` 实现复跑** | ✅ `service/common` 10 例 + 本域 **4 例**全绿 ⇒ **有效值不变** ⇒ 毫秒路径**没走** |
| **共享面** | ✅ **零改动**（overlay 后已 `git checkout` 还原，`git status` 干净） |
| **产品代码** | ✅ **零改动**（本轮只加**测试** + 改**台账/坑档**） |
| **本域四个门禁** | ✅ 全 **EXIT=0** —— `check-ledger-selfcheck.py`（**门禁级 0 条**）· `check-ledger-selfcheck.py --mutation-test`（**变异自证通过**）· `check-ledger-vs-reality.py` · `check-assertion-sources.py`（**无门禁级发现**）<br>⚠️ `check-ledger-vs-reality.py` **当场抓到**本轮新增测试类导致 §0 的「19 类 / 105 例」过期 ⇒ **已同步为 20 类 / 109 例** ⇒ 该门禁**确实在盯数字**（不是摆设） |
| **四个探针复跑** | ✅ 全 **EXIT=0** —— `grpc-contract-probe`（**36 + 15 case**，冻结值一条没动）· `authority-live-probe`（**SAME 20 / OBS 0 / SKIP 1**）· `api-full-parity-probe`（**32 case**：SAME 24 / SAME_CODE 1 / SAME_BODY 2 / SAME_STATUS 4 / OBS 1）· `sql-statement-probe`（**语义差异 0 · L1 0 例**；记录 855 / 语句 594 / marker 46 ⇒ 与第二十轮基线**逐条一致**）<br>🟢 SQL 探针的 `out/` 两个文件**归一化后逐字节一致**（只差时间戳与雪花 ID ⇒ 非确定量）⇒ ⇒ **不提交这批噪音**（已 `git checkout` 还原）⇒ 与第二十二轮同款处理 |

### 34.6 边界与后续

- 🔴 **E62 的修法（先 unwrap 到驱动实现类再探测）由 `global` 做** ⇒ favorite **只读不动**。
  ⇒ ⚠️ **一旦他们修好，本域第 3 条断言会转红**（原始毫秒会变成 ≈R 而非 2000）
  ⇒ ⇒ 那**不是回归**，而是「**本域台账的秒级表述该更新了**」的信号 ⇒ 已在守门类 javadoc 里写明。
- 🟡 **本域不做**「Go ↔ RAG 取消时刻是否逐字一致」的结论 —— 那是**共享层**的事，且当前**不成立**
  （Go 到 deadline 精确取消；RAG 走秒级回退 ⇒ 最坏晚 ≤999ms ⇒ favorite 因 δ 毫秒级而**不可观察**）。

## 35. 2026-10-26 第二十六轮（`align/favorite` 工作树 · 承 `1111c8a`）—— 🔴 **接住 global 第四十四轮的结构层缺陷类 ⇒ 收窄成本域门禁**

> **来源**：`global` 第四十四轮（`76c3b26`，origin + upstream 已推）同步四条：
> ① 本轮**无** `service/common/**` 行为改动 ⇒ 对 favorite **零影响**（**确实没有要处置的事**，不是漏发）；
> ② 新增**结构层守门**：global 域在 `align/global` 分支上新增了 **`check-ledger-tables.py`**
> （放在该分支的 `tools/` 目录下 ⇒ ⚠️ **本分支没有这个文件** ⇒ 本轮是把它**只读拷到 `/tmp`** 后跑的）；
> ③ 提醒：用脚本改台账时**插行要用行级插入，别用长文本 `replace`**；
> ④ global 在**我的第二十四轮 HEAD（`a4a7ee0`）**上跑过 `--all` ⇒ **10 处命中全在 comment 域文件** ⇒ 我域 0 命中。

### 35.1 🔴 但 global 的测量点**已经过期了一轮** ⇒ 必须在最新 HEAD 上复跑

我的**第二十五轮**（`1111c8a`）**又动了台账**：新增 §34（**4 张表**）· §0 新增一行 · 改写 §28.1 的**三列表**
⇒ ⇒ global 的结论（基于 `a4a7ee0`）**不能覆盖我这一轮的改动** ⇒ ⇒ **必须复跑**。

### 35.2 ✅ 复跑结果（**本分支最新 HEAD `1111c8a`**）：**我域 0 命中**

`check-ledger-tables.py --all`（**只读**，跑的是 `align/global` 那份；本轮用 `/tmp` 下的拷贝）⇒ 全仓 10 处命中，**全部在 `docs/FileTree/comment/common.md`**（comment 域文件）
⇒ ⇒ **本域台账 0 命中**（`docs/DRIFT/favorite/`、`docs/Pitfalls/favorite/`、`docs/Intentional_Deviations/favorite/` 全清）。

### 35.3 🔴 逐条核证：global 报的 10 处是**真塌陷**，不是工具误报

抽验 `docs/FileTree/comment/common.md:78-80`（工具报 79-80）：

```
 78 | | **27 个常量全留还是只留可达的 14 个** | ✅ **全留**（…`codeMsg` 是**字面量表**，
 79 | `GetErrMsg` 的未知码回退（`:97-103`）依赖 `codeMsg[Error]` 存在；      ← 没有行首 `|`
 80 | 且这 27 个数值是**跨服务契约**，删项等于改契约。…**保留但不视为已对齐**） |   ← 没有行首 `|`
```

⇒ 第 79/80 行**不以 `|` 开头** ⇒ GFM 把它们当**第 78 行的 lazy continuation**
⇒ ⇒ **表格结构静默塌陷**（内容一个字没丢 ⇒ **肉眼扫不出**）⇒ **真缺陷** ✅

### 35.4 🔴 但 global 的原版**不能直接当本域门禁**（两个硬理由）

| # | 理由 | 实测 |
|---|---|---|
| ① | **默认扫描范围不含本域**（只扫 `docs/DRIFT/global/**` 等）⇒ 要用就得 `--all` ⇒ `--all` 扫**全仓 `docs/`** ⇒ ⇒ 会因为 **comment 域的文件**变红 ⇒ ⇒ **别人的缺陷不该让我的门禁红** | `--all` ⇒ 10 处命中全在 comment 域文件 |
| ② | **`--mutation-test` 硬编码 global 自己的 `docs/DRIFT/global/_README.md`** 当靶子 ⇒ 在本分支上直接跑 ⇒ **「变异前置失败」** ⇒ ⇒ 自证机制在本分支上**根本用不了** | 实测 EXIT=1，输出「变异前置失败：在 `_README.md` 里找不到『带日期行首』的时间线行」 |

⇒ ⇒ **结论：只能当探针，不能当门禁**（同型 **PIT-FAV-RPC-027**）⇒ ⇒ **收窄成自己的版本**。

### 35.5 ✅ 收窄成本域版本：`tools/ledger-lint/favorite/check-ledger-tables.py`

| 决策 | 内容 |
|---|---|
| **扫描范围** | `docs/DRIFT/favorite/rpc.md` · `docs/Pitfalls/favorite/rpc.md` · `docs/Intentional_Deviations/favorite/rpc.md` —— **只含本 owner 写的** |
| **明确排除** | `favorite/api.md`（**并发会话**负责）· `favorite/{common,proto}.md`（**别的组件**）· `docs/FileTree/**`（**用户维护**的理论文件树，提交信息 `add sth`）⇒ ⇒ **不会因别人的文件变红** |
| **变异靶子** | 换成**本域自己的台账**（`docs/DRIFT/favorite/rpc.md` 的 §0 时间线表格行）⇒ ⇒ 自证机制在本分支上可用 |
| **判据** | 与 global 同源（表格块内部不得出现「无行首的内容行」）⇒ **未改动** |

### 35.6 ✅ 交叉验证：本域副本的判据在**真实数据**上**复现** global 的 10 处命中

用本域副本的 `find_violations` 跑 comment 域那份已知有塌陷的文件 ⇒ **10 处，行号逐条一致**
（79-80 · 84-85 · 349-351 · 353-354 · 358 · 360 · 371-372 · 374-375 · 377-378 · 380）
⇒ ⇒ **证明副本没把判据抄坏**（不是「只在自己域上绿」）⇒ ⇒ 副本可信。

### 35.7 本轮验证

| 项 | 结果 |
|---|---|
| **本域新守门** | ✅ `tools/ledger-lint/favorite/check-ledger-tables.py` ⇒ **EXIT=0**（本域 3 个台账文件 0 违规） |
| **变异自证** | ✅ `--mutation-test` ⇒ **EXIT=0** —— M1 阳性（吃掉行首 ⇒ 精确报 `rpc.md:32`）· M2 阳性（内容落进表格块 ⇒ 精确报 `rpc.md:33`）· M3 阴性（原样 0 违规）⇒ ⇒ **非空转** |
| **真实数据交叉验证** | ✅ 副本判据在 comment 域文件上**复现 10 处**（行号逐条一致）⇒ ⇒ 判据未被抄坏 |
| **原有四个门禁** | ✅ 全 **EXIT=0** —— 🔴 其中 `check-ledger-selfcheck.py` **当场抓到本轮自己写的一处**：§35 里把 **global 分支上的** `check-ledger-tables.py` 写成了**仓内路径**形式 ⇒ 该文件在本分支**不存在** ⇒ 仓内路径存在性层判为**悬空** ⇒ ⇒ **已改写法**（跨分支文件**不写仓内路径**）⇒ ⇒ 该门禁**确实在盯跨分支引用**，且**抓的是我本轮自己写的东西**（同型 **PIT-FAV-RPC-030** 的精神：新写的内容必须过门禁） |
| **产品代码 / 共享面** | ✅ **零改动**（本轮只加 1 个本域工具 + 改本域台账/坑档）⇒ ⇒ **本轮不必复跑 maven 与四个探针**：改动**零行 Java**、零行 yaml ⇒ 第二十五轮的「全量 109 例全绿 + 四探针 EXIT=0」**继续有效**（不是「没跑」，是**无需跑**） |

### 35.8 教训（同型坑档 **PIT-FAV-RPC-035**）

1. 🔴 **别人的工具不能直接当自己的门禁** —— 不是因为它不对，而是因为**扫描范围**与**变异靶子**是**按作者域**写死的
   ⇒ 收窄范围 + 换自己的靶子 ⇒ 再用它的判据。
2. 🔴 **别人给你的测量结论会过期** —— global 的结论基于 `a4a7ee0`，而我随后又改了一轮台账
   ⇒ ⇒ **跨域回执里的「我这边是干净的」必须问「在哪个 commit 上量的」**。
3. 🔴 **工具的命中要逐条核证**（是台账的问题还是工具自己的 bug）—— 抽验一处即可定性：行首确实没了 ⇒ 真缺陷。
4. 🔴 **改台账用行级插入，不用长文本 `replace`**（global 第四十四轮的实测结论，我自己也吃这个风险：
   本域每轮都在改 §0 那张**时间线表**）。

## 36. 2026-10-26 第二十七轮（`align/favorite` 工作树 · 承 `e33d222`）—— 🔴 **global 第四十五轮：毫秒路径真的生效了** ⇒ 我的守门「按设计转红」+ 🔴 **给他们的量化口径指正**

> **来源**：`global` 第四十五轮（`180f865`，origin + upstream 已推）⇒ §5.3b 通知：
> 本轮是**真·共享层行为变更** ⇒ 落点 `DeadlineQueryTimeoutInjector`（**所有 rpc 组件共用**）⇒ **favorite-rpc 在内**。
> **改了什么**：毫秒探测**先 unwrap 到驱动实现类**再探测
> （改前按 `statement.getClass()` ⇒ 生产拿到 **Hikari 代理类** ⇒ 代理类上没有 pgjdbc 的
> `setQueryTimeoutMs` ⇒ 探测**必然失败** ⇒ 静默退回秒级）。
>
> 🔴 **写权**：落点在 `service/common` ⇒ **global 单写权** ⇒ favorite **不改共享层**；
> 且 global **未合回 `do-my-own-gh`**（Jason 拍板暂不合回）
> ⇒ ⇒ **本分支上 `service/common` 仍是旧的秒级实现** ⇒ ⇒ **本分支跑出来是「秒级」是对的**
> ⇒ **别把「主仓还是旧行为」误读成「修法不对」**（他们 ⑥ 也这么说 ⇒ 确认）。

### 36.1 🔴 实测：我的守门**按设计转红**，且**红在预判的那两条**

> **只读 overlay**：`global` 第四十五轮的 `DeadlineQueryTimeoutInjector.java` + 他们的
> `DeadlineQueryTimeoutInjectorTest.java` 覆盖到本分支 ⇒ 跑完 `git checkout` **还原**。

| 项 | overlay global 第四十五轮 | 本分支（未合） |
|---|---|---|
| `service/common` 的 `DeadlineQueryTimeoutInjectorTest` | ✅ **13 例全绿**（他们那版） | ✅ **10 例全绿**（本分支那版） |
| 守门「代理层秒数」 | 🔴 **红**：`expected: <2> but was: <1>` | ✅ 绿（2） |
| 守门「驱动内部原始毫秒」 | 🔴 **红**：**`expected: <2000> but was: <1691>`** | ✅ 绿（2000） |
| 其余 3 例（代理类判定 / 阴性对照 / 无 deadline 不注入） | ✅ 绿 | ✅ 绿 |

⇒ ⇒ **第二十五轮故意留下的「转红 = 台账该更新、不是回归」信号，兑现了**。

### 36.2 🔴🔴 这次实测同时**证伪**了 `global` 第四十五轮给的量化口径

🔴 他们 ② 写：「毫秒路径把取消时刻从『deadline + (0~999ms]』收紧到『**deadline + δ**』⇒ ⇒ **可观察差异 = δ（毫秒级）**」
⇒ ⇒ **错**。实测**驱动收到的是原样 R（1691ms）**，不是整秒 ⇒ ⇒ 取消时刻 = `t_exec + R`
⇒ ⇒ 而 `R = DeadlineState.remainingMillis() = deadline − t_exec`（注入器算的就是「还剩多少」）
⇒ ⇒ ⇒ **`t_exec + R ≡ deadline`**（**恒等式**）
⇒ ⇒ ⇒ **Go ↔ RAG 的取消时刻差 = 0**（**不是 δ**）⇒ ⇒ **favorite 侧与 Go 在这一维度逐字一致**。

⇒ 💡 **为什么「R 的公式」在这里根本不重要**：
他们说 favorite 的 R = `min(客户端剩余, 2s) − δ`（**他们分支**的公式 —— 我第二十四轮 §33 已订正过一次），
本分支是**服务端每次调用自绑** `now + timeout`（`service/common/.../RpcInterceptor.java:170-172`）。
⇒ ⇒ 但毫秒路径的结论**与 deadline 的来源公式无关** ⇒ ⇒ `t_exec + R ≡ deadline` 对**任何**来源都成立
⇒ ⇒ ⇒ 我第二十四轮那句「上界 ≠ 贴近」的纠正在**这里不重要**（不是靠公式，是靠**恒等式**）。

### 36.3 🔴 「结论不变的真正理由」这句话要**再订正一次**

他们说：结论不变的真正理由**仍是**「favorite 的 handler 不烧预算 + SQL 是快操作 ⇒ 那个 ≤999ms 窗口里没有慢 SQL 可跑」。
⇒ ⇒ **在毫秒路径生效后，这句话退化为冗余** —— 那个「≤999ms 窗口」**本身已经不存在**（取消时刻 == deadline）。
⇒ ⇒ 它**仍有价值**，但**角色变了**：它是「**毫秒路径若再次失效**（例如又有人改成按代理类探测）」时的**唯一结构性兜底**。
⇒ ⇒ 更准确的说法：**当前生效的主因 = 「毫秒精度直接生效」；兜底 = 「handler 不烧预算 + 快 SQL」**。
⇒ ⇒ ⇒ 教训：**理由也是结论的一部分** ⇒ 机制变了，理由必须跟着变（否则台账会拿一个冗余理由继续挡着）。

### 36.4 🟢 本域本来就**不在**「守门绿、生产坏」那个洞里

他们 ③ 建议「favorite 域如果也有类似的 SQL 精度守门，建议同样换成真池」
⇒ ⇒ ✅ **本域的守门本来就是真池**：`@SpringBootTest` ⇒ Spring Boot 自动配置 ⇒ HikariCP
⇒ ⇒ 实测 statement 类 = `HikariProxyPreparedStatement`（§34.2）⇒ ⇒ **本域不存在这个洞**（已在 §34.2 记录）。

### 36.5 🔴 合并时的动作清单（提前写好，避免届时手忙脚乱）

global 的改动**未合回主仓** ⇒ **本分支现在跑出来是「秒级」是对的**。
⇒ 等新版权 `service/common` 合进本分支后，**必做**：

| # | 动作 | 具体 |
|---|---|---|
| 1 | **守门改期望值** | 「代理层秒数」：`2` → **`1`**（毫秒路径下 pgjdbc 的 `getQueryTimeout()` 是 `floor(R/1000)`，**会少报**）；「驱动内部原始毫秒」：`2000` → **≈R（≈1690，非整秒）** ⇒ **不能再断言成 2000** |
| 2 | **台账改口径** | §34 的「生产实际」列 · §28.1 的 (A)/(B) 两行 · §35 的结论 ⇒ 全部改成「**毫秒精度已生效**」 |
| 3 | **坑档补状态** | PIT-FAV-RPC-034 的「现象」表补一栏「**已修**」 |
| 4 | **重新量一次** | 合并后把守门改成毫秒期望值再跑 ⇒ 确认**真的生效**（不是又一次「按代理类探测」） |

### 36.6 本轮验证

| 项 | 结果 |
|---|---|
| **overlay 实测** | ✅ `service/common` **13 例全绿**（他们那版）· 本域守门 **5 例中 2 条红**（红在预判的两条，且给出实际值 **1** 与 **1691**） |
| **本分支复跑** | ✅ 本域守门 **5 例全绿** |
| **守门改进** | ✅ 把「代理层秒数」与「驱动内部原始毫秒」**拆成两个测试方法** —— 同方法内第一条失败会**遮住**第二条 ⇒ 届时只知道「红了」，不知道「红在哪、实际值多少」 |
| **共享面** | ✅ **零改动**（overlay 后已 `git checkout` 还原，`git status` 干净） |
| **产品代码** | ✅ **零改动**（本轮只改**守门测试** + 台账/坑档）⇒ ⇒ **无需复跑 maven 全量与四个探针**（改动零行产品代码；第二十六轮起本域产品代码已连续两轮未动） |
| **本域五个门禁** | ✅ 见 §36.7 |

### 36.7 教训（同型坑档 **PIT-FAV-RPC-036**）

1. 🔴 **同一方法里放多条断言 ⇒ 前面的失败会遮住后面的** ⇒ 守门要「**一条断言一个测试方法**」
   ⇒ 届时才知道红在哪、实际值是多少（本轮实测：`expected: <2000> but was: <1691>` 就是这么拿到的）。
2. 🔴 **跨域回执给的「量化口径」也要核** —— 本轮实测直接证伪了「差 = δ」（应为 **0**）
   ⇒ 根因：他们把「秒级取整的滞后」与「毫秒路径的滞后」混为一谈。
3. 🔴 **恒等式比公式更硬** —— `t_exec + R ≡ deadline` ⇒ 结论**不依赖** deadline 的来源公式
   ⇒ 比「拿某个数值区间去论证」更稳 ⇒ ⇒ 也让「跨分支公式不同」这件事**不再重要**。
4. 🔴 **「结论不变的真正理由」也是结论的一部分** ⇒ 机制变了，理由要跟着变
   ⇒ 否则台账会拿一个**冗余理由**继续挡着（且让人以为那条理由在起作用）。
