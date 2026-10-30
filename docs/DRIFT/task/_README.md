<!-- @new 2026-10-23 · task 域台账导航 · 由 `align/task` 工作树产出 -->

# task 域台账导航

> **基线 commit**：`8422116` · **工作树**：`RAG.worktrees/task`（分支 `align/task`）
> **首轮核对日期**：2026-10-23 · **最近更新**：2026-10-24（DRIFT-TASK-API-003 收口）
> **Go 真源**：`Sea-RideTheWind_golang`（go-zero `v1.10.2`、goka `v1.1.16`）

| 组件 | 台账 | 最新结论 | 证据强度 |
|---|---|---|---|
| `task-rpc` | [`rpc.md`](./rpc.md) | ✅ **首轮为 🔴 NOT EQUIVALENT（5 条漂移），现已全部修复；本轮又查出并修掉第 6 条** · 已修 **6** 条 | ✅✅✅ Kafka 端到端活体对拍 · ✅✅✅ Kafka 畸形消息活体对拍 · ✅✅ gRPC 双向跨语言对拍 · ✅✅✅ **「首次消费无位点」活体对拍（8 条断言全过，查出 DRIFT-TASK-RPC-006）** · ✅ 真启动 + 真 PG 16 + 真 Redis |
| `task-api` | [`api.md`](./api.md) | ✅ **EQUIVALENT** · 已修 **3** 条 | ✅✅ 双侧活体对拍（44 条，MATCH 44/DIFF 0）· ✅✅ 真 Tomcat 守门（12 条 + 变异测试）· ✅✅ Nacos 真注册中心（注册/注销实测）· ✅ 真实 MVC 管线（MockMvc，20 条）· ⚠️ gRPC 边界在单测里是 mock |
| `task-proto` | [`proto.md`](./proto.md) | ✅ 一致（消费方式本轮首次核对） | ✅ 双侧静态 diff（`check-proto-static.sh` EXIT=0）+ 真构建 |

**task 域没有 `-common`** —— Go 侧 `service/task` 下只有 `api` 与 `rpc`，**不是遗漏**。
⇒ 错误全部是标准 gRPC code（`INVALID_ARGUMENT` / `UNKNOWN`），不需要 like/message 那种业务码 ↔ gRPC code 转换类。

## 本轮改了什么（首轮 + 第二轮 + 盘点收口 + 第三轮 + 第四轮 + 第五轮 + 第六轮）

> **权威 commit 计数**：`git rev-list --count 8422116..HEAD`
> ⚠️ **本标题刻意不写死数字** —— 改台账的**那次提交本身**也会让计数 +1 ⇒
> 写死的数字**必然滞后一拍**（本轮就实测到：写 31、提交后变 32）。
> ⇒ 复核器（`tools/check-ledger-selfcheck.py` 第 3 层）检查的是**更强的不变式**：
> **分支上的每个 commit 都在下面的清单里出现过**（漏登记会立刻被抓到）。

### 首轮（2026-10-23）

| commit | 内容 |
|---|---|
| `e5948c9` | 救活 `TaskProgressUpsertParityTest` 的 15 条静默 skip（Ryuk 未禁用） |
| `4241690` | `task.definitions` 四个 TTL 键是死配置（relaxed binding 键名对不上） |
| `6dc9b4c` | `TaskCountStore` 跨 group 串号 + 重启归零；`task-api` 端口回 Go 原值 18886；4 处 `Go:` 行号引用校正 |
| `38ce9da` | 三档台账首轮（rpc/api/proto）+ 4 处 `Go:` 行号引用校正 |
| `2865ffe` | HTTP 层双侧活体对拍（43 用例）+ gRPC 单组件对拍 runner（7 用例） |
| `25ca5f5` | 🔴 **`RedisPipelines` 把 Go 的 pipeline 当成 MULTI/EXEC 事务 ⇒ 整条消息消费链路是死的**（`ERR EXEC without MULTI`）+ 真 Redis 守门测试（9 条，含阳性对照 + 变异测试） |
| `2900360` | Kafka 端到端对拍脚本（`tools/difftest/parity-task-kafka.py`）+ gRPC runner 入库 |
| `a42f606` | 台账补 DRIFT-TASK-RPC-004/005 与 5 条踩坑（06~10）+ §四·三/四·四 端到端对拍证据 |
| `c6a4902` | `_README` 的 commit 清单换成真实哈希 |
| `6fc2d54` | Kafka 端到端编排入仓（`run-task-kafka-e2e.sh` 等 4 文件），从零跑通 `EXIT=0` |
| `9a95c65` | `api.md` 修正一处自相矛盾 + 补对拍编排的复现指引 |
| `7d9e90a` | DRIFT-TASK-RPC 编号归并（消掉 004 空洞），同步锚点与全部引用 |

### 第二轮（2026-10-24 · 修 DRIFT-TASK-API-003 + 验证未知）

| commit | 内容 |
|---|---|
| `364d8c3` | 🔴 **修 DRIFT-TASK-API-003** —— form-urlencoded / multipart 的 JSON body 被 Spring 拒（Go 收）。根因是 `ServletServerHttpRequest.getBody()` 的 form-post 分支 + `EmptyBodyCheckingHttpInputMessage` **构造期**就消费 body ⇒ 转换器层修不了；修在**消息输入层**（`createInputMessage`）+ multipart 配置层。真 Tomcat 守门 12 条（含阳性对照）+ **两次变异测试** |
| `e378114` | **HTTP 侧双侧活体对拍一键编排入仓**（`run-task-http-parity.sh`）+ `parity-task.py` 三处改造（补上一直没实现的「不带 Content-Type」用例 / 有 DIFF 返回非零 / 按媒体类型归一后判定）⇒ **44 条 MATCH 44 / DIFF 0** |
| `37486c0` | 三个静态守门脚本扩到 task 域：`check-proto-static.sh`（EXIT=0）、`check-ddl-static.py`（11 列逐字一致）、`check-plaintext400-headers.sh`（task 段三条断言全过） |
| `a4d772c` | 台账：API-003 收口 + **推翻上一版的根因误判** + 5 条新踩坑（11~15） |
| `243b5e5` | **gRPC 跨语言互调** —— Go 客户端 → 两端服务端（补齐第二个跨语言方向；用真源**自己生成**的桩，真源零写入）⇒ 7/7 一致 |
| `ca37b4e` | **Kafka 畸形消息活体对拍**（五个用例，两侧完全一致）⇒ 并**推翻本仓 javadoc 里一条错误断言**（「Go 会被 kafka-go 的自动提交抵消」—— kafka-go 用 `FetchMessage` 时自动提交不生效） |
| `8d89170` | **编排脚本**：pkill 后只 sleep 2 ⇒ 新进程抢不到端口，症状却是「api 503」⇒ 改成轮询等端口释放 / 等端口监听 |
| `a50b6cb` | 台账：rpc.md 加 §四·五（跨语言 gRPC + 畸形消息活体对拍）+ 证据边界与待办更新 |
| `546457b` | **Nacos 真注册中心对拍**（注册 → 注销，EXIT=0）+ 修既有 `run.sh` 里 2 处同款潜伏 bug（`$var` 紧跟多字节字符） |
| `e5e47f9` | 台账：api.md 加 §四·六（Nacos 证据）+ Pitfalls 15·补 / 17 + 本 README |
| `127e71c` | `_README` 更新：第二轮 4 个 commit + 最新状态 + 共享工具改动清单 |

### 第二轮之二（2026-10-24 · 台账盘点收口）

| commit | 内容 |
|---|---|
| `14b9560` | **全量盘点「task 域还剩多少可修复的漂移与未知」** ⇒ 结论：**语义漂移 0 条**；修掉台账里 **8 处过期/自相矛盾**的断言（见下） |

**盘点修掉的 8 处台账自身问题**（全是**文档过期**，不是代码缺陷）：

| # | 位置 | 原写法（错） | 改成 |
|---|---|---|---|
| 1 | `rpc.md` §〇「已修」 | 编号列表写 `001/002/003/**005/006**` | `006` 不存在（编号归并前的旧号），且**漏列 004** ⇒ 改为 `001~005` |
| 2 | `rpc.md` §〇「语义等价性」 | 🔴 **NOT EQUIVALENT** | 与同表「未修 0 条」**自相矛盾** ⇒ 5 条全修 + 活体对拍 0 DIFF ⇒ 改为 ✅（保留 §五 效力边界指针） |
| 3 | `rpc.md` §〇「未修」 | 「另有 1 条签字偏离 + **1 条有意偏离待登记**」 | 偏离档里只有 `SD-2026-10-23-TASK-01`，**没有待登记项** ⇒ 删掉 |
| 4 | `rpc.md` §〇「仍未覆盖 ①」 | 「文章路**未纳入**本轮对拍矩阵」 | **已纳入**（§四·三 结果表有 `article:like:cnt:*` / `task:inits:*` / `task:done:190:5001`）⇒ 删掉 |
| 5 | `rpc.md` §〇「仍未覆盖 ②」 | 「goka 计数表**未做逐值对拍**」 | **已做**（§四·三 结果表末行：用户路 + 文章路都 ✅）⇒ 删掉 |
| 6 | `rpc.md` §四·二 | 「索引/唯一约束/CHECK **仍未覆盖**」 | 正文自己写着「两侧一致地没有，不是漏检」⇒ **表述矛盾**，改为「已闭合」 |
| 7 | `proto.md` §〇/§四·1/§五·1 | 「`check-proto-static.sh` **未覆盖** task」 | **本轮已做**（`EXIT=0`）⇒ 改为已覆盖 |
| 8 | `proto.md` §四·2/§五·2 | 「❌ **未做**跨语言互调」 | **本轮已做**（Go 客户端 → 两端，7/7）⇒ 改为已做 |

⇒ 修这 8 处的原因是**防下一个 agent 照着过期待办重做已完成的事**（`proto.md` 那两条当时是会真去重做的）。

**最终测试**：`task-api` **90**（含新增 12 条真 Tomcat）/ `task-rpc` **99**（含 15 条真 PG + **13 条真 Redis** + **5 条 offset-reset 契约**）/ `common` **146**
⇒ **335 条全部 0 failures / 0 errors / 0 skipped**。

**静态守门**：`check-proto-static.sh` EXIT=0 · `check-ddl-static.py --domain task` EXIT=0（11 列 0 差异）· `check-plaintext400-headers.sh` EXIT=0。

**活体对拍**：`parity-task.py` 44 条 **MATCH 44 / DIFF 0**（`EXIT=0`）· `parity-task-kafka.py` 最终态完全一致（`EXIT=0`）·
`run-task-kafka-malformed.sh` 五个畸形消息用例两侧一致（`EXIT=0`）·
`run-task-grpc-xlang.sh` 跨语言 7/7 一致（`EXIT=0`）· `run-task-nacos-discovery.sh` 注册/注销符合预期（`EXIT=0`）。

### 「验证未知」10 条的完成情况

| # | 项 | 状态 |
|---|---|---|
| 1 | `check-proto-static.sh` 扩 task | ✅ 完成（EXIT=0） |
| 2 | `check-ddl-static.py` 扩 task | ✅ 完成（11 列 0 差异，EXIT=0）；顺带补上 NOT NULL/DEFAULT 对照 |
| 3 | `check-plaintext400-headers.sh` 覆盖 task-api | ✅ 完成（EXIT=0） |
| 4 | API-003 彻底修法 | ✅ 完成（`364d8c3`） |
| 5 | Go↔RAG 跨语言 gRPC 互调 | ✅ 完成（`243b5e5`，Go 客户端 → 两端服务端） |
| 6 | Kafka 畸形消息活体对拍 | ✅ 完成（`ca37b4e`，五个用例） |
| 7 | NacosDiscovery 真注册中心行为 | ✅ 完成（Nacos 注册 → 注销） |
| 8 | 「Kafka 写入 → task-api 读到」闭环 | ⚠️ **不做** —— 两侧读写 Redis key **本来就不一致**（Go 侧也读不到自己写的缓存）⇒ 不是 RAG 的缺陷 |
| 9 | `parity-task-kafka.py` 纳入 CI | ⚠️ **未做** —— 要用 Testcontainers 改造**共享**构建基建，范围超出 task 域，需与其它域协调 |
| 10 | `task:goka:count:*` 保留策略 | ✅ **已定案**（2026-10-24）—— 实测证明 Go 的 compacted table **不按时间过期** ⇒ **不设 TTL 才是对齐**（加了会造成业务错误）⇒ 改为「用真 Redis 守门测试把「不设 TTL」锁成契约」 |
### 第三轮（2026-10-24 · 验「goka OffsetOldest」+ 定案「保留策略」）

| commit | 内容 |
|---|---|
| `6fc73e5` | 🔴 **修 DRIFT-TASK-RPC-006**：六个 listener 的 `properties` 写成了**连字符** `auto-offset-reset` ⇒ **Kafka 直接忽略** ⇒ goka 三处的 `earliest` **从未生效**（kq 三处的 `latest` 属「碰巧对」）。改成**点分**的 `auto.offset.reset` + 新增 5 条契约守门测试（含阳性对照 + ✅ 变异测试） |
| `0ba9c0b` | **「首次消费无位点」活体对拍**：新脚本 `tools/difftest/run-task-offset-oldest.sh`（**先灌消息、再起消费者**）⇒ **8 条断言全过、失败项 0**；修复前 A2/A4 是 `<absent>` FAIL |
| `8959779` | **定案「goka 表的保留策略」**：`cleanup.policy=compact` 单独使用时 **Kafka 不按时间删**（带阳性对照的对照实验）⇒ **Go 的计数表永不过期** ⇒ **RAG 的 `task:goka:count:*` 不设 TTL 才是对齐**（加了会造成业务错误）⇒ 新增 4 条真 Redis 契约守门测试（含 ✅ 变异测试） |
| `a02a326` | 🔴 **修跨域隐患**：`tools/difftest/` 下两个 compose 文件**项目名撞车**（都叫 `difftest`）⇒ `up -d` 会**删掉 user 域的容器**、`down -v` 还会删卷 ⇒ 给 task 域的 compose 加 `name: taskprobe` |
| `a75fcb9` | 台账：rpc.md 加 §四·七（保留策略）+ §四·八（OffsetOldest 对拍）+ DRIFT-TASK-RPC-006；Intentional_Deviations 加 `SD-2026-10-24-TASK-01`；Pitfalls 加 **5 条**（18~22） |
| `b0350ea` | 修正两处盘点后**新出现的过期断言**（rpc.md §〇「⚠️ 仍未覆盖 ①」—— 原文还写着「goka OffsetOldest 未做活体验证」，而本轮已做 ⇒ 改成「表 topic config 未逐项对照，但已实测 compact 不按时间删且 Go 侧不创建 topic ⇒ 一致」；`_README`「验证未知」表第 10 条 —— `task:goka:count:*` 保留策略还标着「未做」） |
### 第四轮（2026-10-24 · 固化「配置键名扫描」，并查出三类 yaml 死配置）

| commit | 内容 |
|---|---|
| `f3d4cb4` | 🔴 **新增守门测试 `TaskConfigKeyBindingContractTest`（5 条全绿）**：yaml 的每一个键都必须有消费方（框架元数据 ∪ 自有 `@ConfigurationProperties` 前缀 ∪ `@Value` 占位符）⇒ **新增死配置会被立刻抓住**；白名单是**显式台账**（带出处与定性）+ **反向纪律**（每条登记必须真的盖住至少一个孤儿键）<br>✅ **三种变异测试全部精确命中**（非假绿）：① `mybatis-plus` 段移回顶层 ⇒ 白名单纪律红；② 新增 `bogus-typo-key` ⇒ 主断言红并点名；③ `init-ttl` → `init-ttl-days` ⇒ 绑定断言红（复现历史缺陷 `initTtlDays`）<br>🔴 **查出三类 yaml「死配置」**（§四·九）：`spring.mybatis-plus.*`（**前缀层级写错** ⇒ 三个键全死，但**零行为影响**，8 个 rpc 模块同款）· `otel.*`（**Boot 4.0.8 完全没有 OTel 自动配置** ⇒ 20 个 yaml 同款，⚠️ **与 Go 有观测差异**：Go `serviceconf.go:67 trace.StartAgent` 真导出 span）· `spring.jpa.*`（本模块无 JPA 依赖，6 个模块同款）⇒ **全部跨域共性，本域不单方面修**<br>台账：rpc.md 加 §四·九 + 更新 §〇/§一/§七；Pitfalls 加 **3 条**（23~25，记的是**扫描器与判定逻辑自己踩的坑**） |
### 第五轮（2026-10-24 · 「台账 vs 实际」的复核）

| commit | 内容 |
|---|---|
| `c73d9cf` | 🔴 **新增复核器 `tools/check-ledger-selfcheck.py`**（首版**五层**：路径存在性 / 「已修」断言→仓库证据 / 数字断言 / Go 侧 `file:line` / 未点名测试类）⇒ **解决「台账过期时没有任何东西会报警」**（AGENTS.md §6 的「核对日期」纪律的机械化）<br>✅ **三种变异测试全部精确命中**：① 修复被回退 ⇒ 第 2 层红；② 台账测试数造假 ⇒ 第 3 层红；③ Go 侧行号越界 ⇒ 第 4 层红<br>🔴 **复核查出 4 处台账缺陷并全部修掉**：① 章节标题「24 个 commit」**过期**（实际 31）② 第三/四轮清单的 commit 栏是**占位符**「（本轮）」（第二轮起就已改用哈希 ⇒ 本轮反而回退）；且第四轮**写成 4 行却只对应 1 个 commit** ⇒ 会误导读者 ③ 开放项表上方引用「§三·第四轮」**章节号失准**（该节在 rpc.md §四·九）④ **4 个测试类在册但从未被台账点名** ⇒ 两个台账各加一节「〇·一、**守门测试清单**」逐个登记（含条数 + 锁住哪条契约 + 关键前提）<br>台账：rpc.md 加 §四·十（复核方法 + 查出的 4 处缺陷 + 变异测试 + **效力边界** + 脚本自身的 3 个坑）；_README 加第五轮清单 + 复核器登记；Pitfalls 加 **1 条**（26） |
| `c52cbea` | 复核器第 3 层改查「**commit 覆盖**」不变式（不再比对写死计数 ⇒ 不自指）+ 补登 3 个漏登记 commit（`b0350ea` / `127e71c` / `c73d9cf`）+ 判定**只认表格行**（纯文本搜哈希会把**正文引用**当成登记）⇒ 新增第 0 层「自检」（阳性对照）+ 第 3 层「commit 覆盖」<br>⚠️ 并把章节标题的**写死 commit 数**改成「指向 `git rev-list --count`」+ 加一句说明（写死的数字**必然滞后一拍**：改台账的那次提交本身就会 +1）<br>⚠️ Pitfalls 加 **2 条**（27·28） |

### 第五轮之二（2026-10-24 · 「补登链」：每个 commit 登记前一个）

> ⚠️ **这一串 commit 的作用就是把「每个 commit 都必须在台账里登记」这条不变式**真正**闭合**
> （补一个 → 下一个补上一个）。**链的形状说明它是可收敛的**：每补一个，链长 +1，不会分叉。
> 🔴 **但链在 `79d4aac` 处断过**：它补登了 `185448c`，**自己却没被登记** ⇒
> ⇒ **2026-10-24 19:48 三次复核把 `79d4aac` 补上**（链尾已闭合，见 rpc.md §四·十·四·三）。

| commit | 内容 |
|---|---|
| `5e9fb53` | 补登 `c52cbea`（复核器「commit 覆盖」不变式与自检层）<br>⚠️ **这一步本身就在验证不变式的闭合性**：提交后新 commit 自动被识别为「本轮占位」⇒ `EXIT=0` **恒定**，不再自指 |
| `c3038ba` | 补登 `5e9fb53`（第二次验证闭合性：连续两次补登后 `EXIT=0` 仍恒定 ⇒ 不变式稳定） |
| `4897a4c` | 修 **api.md §一 标题条数失准**（「已修（2 条）」⇒ 实际 **4** 个小节：001/002/003 已修 + **004 未修**）⇒ 标题改为「逐条对照（4 条：…）」<br>复核器补 **api 域断言**（§一 标题条数 vs 实际小节数 + `DRIFT-TASK-API` 编号连续性）<br>⚠️ 标题条数的正则**不能锚定措辞**（第一版锚定 ⇒ 变异测试没抓到 ⇒ 收窄后才命中）<br>✅ **第四种变异测试**：标题改回「已修（2 条）」⇒ 报「声明 2 / 实际小节 4」 |
| `1e79811` | 补登 `c3038ba`（不变式闭合性三次验证） |
| `548dcd9` | 补登 `4897a4c`（api.md §一 标题条数修正 + 复核器补 api 域断言） |
| `d4b990b` | 补登 `1e79811`（不变式闭合性四次验证）<br>⚠️ **本次暴露了不变式的一个真实缺陷**：「允许 ≤1 个未登记」在**连续快速提交**时会漏过真正的漏登记（一次补登只解决一个，前一个还没登记就又提交了新的）<br>⇒ 规则改为「**只允许 HEAD 那一个未登记**」（其余一律是漏登记的历史 commit，必须报出来） |
| `88ed290` | 修「commit 覆盖」不变式的**漏报缺陷**（只允许 HEAD 未登记）+ 补登 `548dcd9` / `d4b990b`<br>⇒ Pitfalls 加 **1 条**（29：「允许 ≤1 个例外」的判定会漏过真正的漏登记 ⇒ 例外要**限定到具体对象**，不能只数个数） |
| `5da7fbc` | Pitfalls 加 **1 条**（29：「允许 ≤1 个例外」的判定会漏过真正的漏登记） |
| `185448c` | 补登 `88ed290` / `5da7fbc`（不变式漏报修复 + Pitfalls 29）<br>⚠️ **这一次同时补齐了 2 个** —— 印证「一次补登只解决一个」正是那个漏报缺陷的成因 ⇒ 规则改为「只允许 HEAD 未登记」后才正确 |
| `79d4aac` | 补登 `185448c`（不变式闭合性最终验证）<br>🔴 **它自己没被登记** ⇒ 补登链在此断掉 ⇒ **2026-10-24 19:48 三次复核补上**（见 rpc.md §四·十·四·三） |

### 第六轮（2026-10-24 19:25 · 二次复核：查「最新 + 最全」）

| commit | 内容 |
|---|---|
| `c886406` | 🔴 **第二轮复核**：距上轮 13 小时后重跑 ⇒ 复核器 `EXIT=0`、数字断言全对（104/90 与 surefire 实测一致）、**9 项开放项逐条实测确认仍成立**（无 CI 引用 / 读写 key 仍不一致 / 三类死配置仍在 yaml / 没别的域碰过 otel / common 未重审）<br>🔴 **查出「生产类覆盖缺口」**：第一轮只查了**测试类** ⇒ **12 个生产类从未被台账点名**，其中 **4 个是「缺了就起不来」的启动阻塞项**（`NacosRegistration` Bean 名撞名 / `Jackson2ObjectMapperConfiguration` Boot 4 给的是 Jackson 3 / `MybatisPlusOrderingBridgeAutoConfiguration` 的 `@AutoConfigureAfter` 指向 Boot 3 包名 ⇒ 静默悬空 / `TaskKafkaTopics` 是 `@ConfigurationProperties` 宿主且含**跨域契约** topic 名）⇒ 已全部登记到 **rpc.md §〇·二「本组件的生产类清单」**；复核器加**第 6 层**（生产类覆盖）并配 ✅ 变异测试<br>⚠️ **合并冲突预警**：`tools/check-ledger-selfcheck.py` 与 **users-user 域同名文件**（两域互不包含 ⇒ 合并必撞）⇒ 两版**互补**（本域版有 7 层 + 自检 + 生产类层；users 版有「打印行号附近原文」+ 7 条去噪规则）⇒ 建议**取长补短**合成一版；另列本域与 `message` / `users-admin` / `users-user` 在 **4 个共享文件**上的重叠<br>台账：rpc.md 加 §〇·二（生产类清单）+ §四·十·四·二；_README 加第六轮清单 + 合并冲突预警 + 开放项表第 10 项；Pitfalls 加 **1 条**（30：覆盖缺口只查测试类不够） |

### 第七轮（2026-10-24 19:48 · 三次复核：查「commit 清单」的**结构性**缺陷）

| commit | 内容 |
|---|---|
| `（本轮）` | 🔴 **三次复核**：前两轮查的是「**内容**对不对」，本轮改查「**元信息**对不对」⇒ **查出 commit 清单表格的 4 类结构性缺陷**（详见 rpc.md §四·十·四·三）<br>① **8 行 commit 栏是占位符「（本轮）」** ⇒ **完全逃过**复核器第 3 层的「commit 覆盖」判据（那条正则只认哈希 ⇒ 占位符既不算「已登记」也不算「漏登记」⇒ **静默通过**）<br>② **同一个 commit 被写成多行**：`c73d9cf` **3 行**（散落在第五轮与第六轮两处）、`c52cbea` **2 行**、`c886406` **4 行** ⇒ 读者会把 **3 个 commit 误读成 9 个**<br>③ **2 个 commit 完全没登记**（`79d4aac` / `c886406`）⇒ 补登链在 `79d4aac` 处断掉<br>④ ⇒ **AGENTS.md 已记的「第四轮『写成 4 行却只对应 1 个 commit』」缺陷第三次复发**，且**复发在同一位置**（commit 清单表格）⇒ 再次印证「同一个坑靠人记不住，必须机械化」<br>🔴 **复核器第 3 层补 5 个判据**：**占位符残留**（只允许 1 个、且必须在末尾）/ **同一 commit 多行** / **登记行数与 commit 数守恒** / **陈旧登记**（登记了不在本分支上的 commit）/ **编号连续性 + 悬空引用**（Pitfall / 签字偏离 ID 必须真有定义）⇒ 把「集合覆盖」升级成「**一一对应**」<br>
🔴 **变异测试进仓**（`--mutation-test`）：**7 种变异 + 1 条回归 + 1 个阳性对照**，全部精确命中 ⇒ 判据自己也有反向证据<br>🔴 **「开放项 #6『service/common 未重审』」的现状描述已过期** ⇒ 实测 **common 域已组件级重对齐** `service/common`（改 3 个 **task-rpc 直接 import** 的类：`RpcServerInterceptorFactory` / `ObservabilityLifecycle` / `RpcInterceptor`，共 339 行）⇒ 顺着这条线**查出本域一条从未进台账的契约**：**gRPC 拦截器注册链**（Go `task.go:28`/`task.go:44` 显式注册）—— 代码**两件套齐**（行为对齐），但**台账零记录 ＋ 本域零守门测试**（follow/message/users-user 三域都有）⇒ **已补 `TaskGrpcInterceptorWiringTest`（2 条，真 gRPC server）**，且 ✅ **变异测试精确命中**（摘掉 `server.intercept(...)` ⇒ 耗时从 ≈300ms 退到 **5011ms** ⇒ 证明不是假绿）<br>🔴 复核器**第 6 层补盲区**：生产类型判定从**只认 `class`** 扩到 **也认 `interface`/`enum`**（本域 2 个 Mapper 接口正属这一类，碰巧已登记 ⇒ 无实际后果，**但盲区本身要修**）<br>台账：_README 补登 `79d4aac` + 重排第五/六轮块（**一个 commit 一行**）；rpc.md 加 §四·十·四·三 + §三「gRPC 拦截器注册链」；Pitfalls 加 **3 条**（`PITFALL-TASK-RPC-31` / `-32` / `-33`） |

⚠️ **第三轮复核的结论**：task 域台账的**内容**仍然可信（**119 条** Go 侧出处**全部不悬空**，
其中 4 条核心断言人工抽验**内容对得上**），但**元信息**这轮又查出 **4 类结构性缺陷**
（占位符残留 / 同一个 commit 多行 / 2 个 commit 没登记 / 「4 行对 1 commit」缺陷第三次复发）⇒ **全部已修**。

⇒ ⚠️ **这恰好印证了 AGENTS.md §6**：台账的「已修 / 未修」断言必须能被**机械复核**，否则它只是文字。
⇒ 🔴 **而本轮更深一层**：**连「机械复核」本身也要被机械复核** ——
上一轮写的「commit 覆盖」不变式**连续三轮都是绿的**，却**完全看不见**上面 4 类缺陷
（它只问「在不在」，不问「几行」；且**占位符两边都不沾**）。
⇒ ⇒ 判据从「**集合覆盖**」升级为「**一一对应**」，并给判据自己配上**阳性对照 + 变异测试**。

> 第五轮（第一次复核）的结论见 [rpc.md §四·十·二](rpc.md#四十二本轮复核查出的-4-处台账缺陷已全部修掉)。


## 🔓 本域**当前仍开放**的项（2026-10-24 20:1x 三次复核：第 6 项已修正，其余仍成立）

> **一句话**：**语义漂移 0 条**（⚠️ 注意：盘点之后**又验出并修掉了第 6 条** —— `auto-offset-reset` 连字符被 Kafka 忽略，
> ⇒ 「goka 的 earliest 从未生效」；详见 §三·第三轮）。
> ⚠️ **第四轮又查出 yaml 侧的三类「死配置」**（见 [rpc.md §四·九](rpc.md#四九🔴-配置键名扫描yaml-里的死配置是怎么被查出来的2026-10-24-实测)）—— **零行为影响**，但 `otel.*` 与 Go 有**观测差异**
> ⇒ **全部是跨域共性**（8~20 个模块同款），本域**不单方面修**。
> 剩下的全是「证据缺口 / 待办 / 跨域」，**没有一条是已知的、只属于本域的行为缺陷**。

| # | 项 | 性质 | 本域能独立做掉？ |
|---|---|---|---|
| 1 | ~~goka `OffsetOldest` 的「**首次消费无位点**」分支未做活体验证~~ ⇒ **2026-10-24 已做**（`run-task-offset-oldest.sh`，8 条断言全过）⇒ **并查出第 6 条漂移（`auto-offset-reset` 连字符 ⇒ Kafka 忽略）已修** | ✅ **已关闭** | — |
| 2 | ~~`task:goka:count:*` **无 TTL、只增不删**~~ ⇒ **2026-10-24 已定案**：实测证明 Go 的 compacted table **不按时间过期** ⇒ **不设 TTL 才是对齐**（加了会造成业务错误）⇒ 改为「真 Redis 契约守门测试」 | ✅ **已关闭** | — |
| 3 | **`parity-task-kafka.py` / `run-task-offset-oldest.sh` 纳入 CI**（`rpc.md` §七 第 6、7 条） | 要用 Testcontainers 改**共享**构建基建 | ❌ 范围**超出 task 域**，须与其它域协调 |
| 4 | DRIFT-TASK-API-004（`Content-Type` 的 charset/空白格式） | **五域共性** | ❌ 不在本域单独修（改了会造成本域与其它域不一致） |
| 5 | 「Kafka 写入 → task-api 读到」跨域闭环 | **非缺陷**：两侧读写 Redis key 本来就不一致（Go 侧也读不到自己写的缓存） | ❌ **明确不做** |
| 6 | ~~`service/common` 未重审~~ ⇒ **2026-10-24 20:1x 三次复核修正**：**common 域其实已经组件级重对齐了**（改了 3 个 **task-rpc 直接 import** 的类：`RpcServerInterceptorFactory` / `ObservabilityLifecycle` / `RpcInterceptor`，共 339 行）⇒ 原描述**已过期**；但那些 commit **仍在 common 域的 worktree、未合入主干** ⇒ 本域看到的仍是旧版 ⇒ 本域的测试结论**待 common 合入后复跑一次** | **跨域**（common 域 worktree） | ❌ 属其它域 |
| 7 | ~~「配置键名对不上 ⇒ 静默失效」的**全域扫描固化**~~ ⇒ **2026-10-24 已做**：新增 `TaskConfigKeyBindingContractTest`（**5 条全绿 + ✅ 三种变异测试**）⇒ **扫描已进 CI** | ✅ **已关闭** | — |
| 8 | 🆕 **yaml 三类「死配置」跨域统一修**（`spring.mybatis-plus.*` / `otel.*` / `spring.jpa.*`，见 [rpc.md §四·九](rpc.md#四九🔴-配置键名扫描yaml-里的死配置是怎么被查出来的2026-10-24-实测)） | **跨域共性**（8 / 20 / 6 个模块同款）· **零行为影响**（三处逐一核过）· ⚠️ 但 `otel.*` **与 Go 有观测差异**（Go 真导出 span，Java 侧 no-op） | ❌ 不在本域单独修（改了会造成本域与其它域不一致）；`otel.*` 还需**全仓统一接线** |
| 9 | 🆕 **`otel.*` 的跨域接线**（补 Boot 4 的 OTel 自动配置 + 全仓 20 个 yaml） | 属**共享构建基建** + 观测面 | ❌ 须与其它域协调 |
| 10 | 🆕 **`tools/check-ledger-selfcheck.py` 与 users-user 域的同名文件合并**（两版**互补**：本域版有 7 层 + 自检 + 生产类覆盖，users 版有「打印行号附近原文」+ 7 条去噪规则） | **跨域**（同一路径、互不包含） | ❌ 需读 users-user 实现并改写 ⇒ 属跨域动作，**待用户决策** |

> **2026-10-24 19:25 复核**：上表 1~9 项**逐条实测确认仍成立**（无 CI 引用 / 读写 key 仍不一致 /
> 三类死配置仍在 yaml 里 / 没有别的域碰过 otel / `service/common` 未重审）。
> ⚠️ **2026-10-24 20:1x 三次复核修正第 6 项**：`service/common` **已被 common 域组件级重对齐**（原描述过期），
> 且顺着这条线**查出本域一条从未进台账的契约**（gRPC 拦截器注册链）⇒ 已补台账 + 补守门测试。
> 另实测确认：三类 yaml 死配置仍在 yaml 里 · 没有别的域碰过 otel · CI 里仍无对拍脚本引用。
> ⚠️ 另见本文件末尾的「**合并冲突预警**」—— 本域与 `message` / `users-admin` / `users-user`
> 三个域在 **4 个共享文件**上有重叠。

⚠️ 另有**两条不是本域的活**：`AGENTS.md` §0 / §3.5 与 `docs/DRIFT/_INDEX.md` 的 task 域行
—— 按 `docs/Worktree.md` §3 留给**合并后统一更新**，本工作树不碰。

## 读本域台账前必读

1. [`../../AGENTS.md`](../../../AGENTS.md) §3.1（Go 是唯一基准）/ §3.3（框架机制要运行证据）/ §3.5（**rpc 必须先于 api**）
2. 本域 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md)
   —— 里面有 **4 项「不是偏离但极易被当成回归修回去」**的东西
3. 本域 [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md)
   —— 「跑绿」在本域曾经意味着「15 条真 PG 用例一条没跑」；
   2026-10-23 又添了 **5 条**（PITFALL-06 ~ 10），其中 06/07 记的是
   **「整条消息链路是死的，而 81 条测试全绿」**这件事是怎么发生的；
   2026-10-24 再添 **5 条**（PITFALL-11 ~ 15），记的是
   **「上一版台账的根因结论是误判」**这件事是怎么发生的；
   同日第三轮再添 **5 条**（PITFALL-18 ~ 22），记的是
   **「配置意图一致」其实是假的**（`auto-offset-reset` 连字符 ⇒ Kafka 静默忽略）、
   **「探针自己会撒谎」连翻三次车**、
   **「compose 项目名撞车会删掉别的域的容器」**、
   **「`$var` 紧跟全角标点」**、
   **「先起消费者的对拍测不出 earliest/latest」**这五件事

## 共享索引未更新（按 `docs/Worktree.md` §3 留给合并后统一处理）

- `docs/DRIFT/_INDEX.md`（task 域行）
- `docs/DRIFT/_cross/component-coverage-matrix.md`（14 个零台账 → 12 个）
- `AGENTS.md` §0 / §3.5
- `docs/DRIFT/_MANIFEST.json`

## ⚠️ 本分支改到了**共享工具**（合并时留意）

`tools/difftest/` 下三个脚本是**多域共用**的，本轮为 task 域做了扩展：

| 文件 | 改动 | 对其它域的影响 |
|---|---|---|
| `check-proto-static.sh` | 加 task 域 + 退出码改成有差异返回 1 | users/admin 的判定内容不变；**退出码语义变了**（原先恒 0） |
| `check-ddl-static.py` | 改 `--domain` 参数化 + 加 NOT NULL/DEFAULT 对照 + 排除 Flyway 账本表 | users 域默认参数与原有输出**不变**（多一节补充对照） |
| `check-plaintext400-headers.sh` | 加 task 域 + 端口不可达判 SKIP | user 域的判定与默认值**不变** |
| `run.sh` | **修 2 处既有潜伏 bug**（`$var` 紧跟多字节字符 ⇒ `set -u` 下硬失败） | 只改了 2 个错误分支的 echo 文案，判定逻辑不变 |
| `parity-task.py` | task 域私有脚本 | 无 |
| `docker-compose.task-kafka.yml` | **加 `name: taskprobe`**（修跨域误删：同目录的两个 compose 文件项目名原本都叫 `difftest`） | ⚠️ **合并时若与其它域同时改这个文件，注意别把 `name:` 行删掉** |
| `run-task-offset-oldest.sh` | **新建**（task 域私有） | 无 |
| `tools/check-ledger-selfcheck.py` | **新建**（**台账 vs 实际**的复核器，**7 层** + 第 0 层「自检（阳性对照）」+ **变异测试进仓**；当前只覆盖 task 域，**其它域可直接复用**） | 无（纯只读脚本；`--mutation-test` 会**临时**改台账再复原）<br>⚠️ **放在 `tools/` 根**（与 `drift-move.py` 同级）⇒ 合并后建议在 **`AGENTS.md` §7.2 的工具档表**加一行（**本工作树不碰 AGENTS.md**，留给合并后统一处理） |

⇒ 合并时若其它域也在改这三个文件，需要人工合。

### 复核器（可重复跑）

```bash
python3 tools/check-ledger-selfcheck.py            # task 域，退出码 0=通过 / 1=有不一致
python3 tools/check-ledger-selfcheck.py --mutation-test   # 只跑变异测试（故意弄坏台账 ⇒ 必须被抓到）
python3 tools/check-ledger-selfcheck.py --domain task --go-root /path/to/Sea-RideTheWind_golang
```

⚠️ **它解决的是「台账过期时没有任何东西会报警」这个问题**（AGENTS.md §6 的「核对日期」纪律的机械化）。
⚠️ **效力边界**：只保证「引用不悬空 / 断言有对应物」，**不保证「内容完全对得上」** ⇒ 关键断言仍需人工抽验。
⚠️ 第三方库必须按**真源 `go.mod` 的精确版本**核对（⚠️ 本机 cache 里当时只有 go-zero v1.10.3，真源用的是 **v1.10.2**）。
⚠️ **其它域要复核**：改脚本里的 `LEDGER_FILES` 与 `CLAIMS` 两张表即可（`--domain` 参数已预留）。
⚠️ **`--mutation-test` 会临时改 `docs/DRIFT/task/_README.md` 与 `api.md`**（破坏 → 判 → 复原）⇒
**中断它可能留下被改坏的台账** ⇒ 跑完请 `git diff --stat docs/DRIFT/task/` 确认已复原（脚本内已用 `finally` 兜底）。
⇒ 详见 [rpc.md §四·十](rpc.md#四十台账-vs-实际的复核2026-10-24-实测)。

---

## ⚠️ 合并冲突预警：`tools/check-ledger-selfcheck.py` 与 **users-user 域同名文件**

> **2026-10-24 19:25 实测**：本分支在 07:03 新建 `tools/check-ledger-selfcheck.py`；
> **users-user 域在 18:38 独立实现了同名文件**（其 commit `47cbfbf`）⇒
> **两域互不包含**（`git merge-base --is-ancestor` 验证为否）⇒ **合并时必然撞车**。

| 维度 | 本域版（888 行） | users-user 版（391 行） |
|---|---|---|
| 结构 | **7 层分层** + 第 0 层「自检（阳性对照）」 | 单函数流（A/B/C 三类检查） |
| **打印行号附近原文**供人工判读 | ❌ | ✅ **独有** |
| **去噪规则**（历史记述≠引用 / 临时脚本记 WARN / 台账自陈不存在的类记 OK） | ❌ | ✅ **7 条独有**（N1~N7） |
| 多域参数化 | `--domain` | `--ledger` |
| 覆盖缺口检查 | 测试类（层 5）**+ 生产类（层 6）** | 无 |
| **变异测试进仓**（`--mutation-test`） | ✅ **7 种变异 + 1 回归 + 1 阳性对照** | ❌ 无 |
| commit 清单「**一一对应**」判据（占位符 / 多行 / 守恒 / 陈旧登记） | ✅ | ❌ 无 |
| 第三方库按真源 `go.mod` 精确版本核对 | ✅ | ✅ |

⇒ **两版互补，不是重复** ⇒ 建议**取长补短**合成一版放 `tools/` 根，
而不是二选一（谁先合谁覆盖 ⇒ 另一域的独有能力会丢）。

⚠️ **本工作树不合并**（那需要读 users-user 的实现并改写本域文件 ⇒ 属跨域动作）。
⇒ 决策留给用户，见本节末尾的选项。

### 其它域也改了共享工具（合并时会撞车，**本域不碰**）

| 域 | 改动的共享文件 |
|---|---|
| `message` | `tools/difftest/check-ddl-static.py`⚠️（**与本域同文件**）、`tools/difftest/run.sh`⚠️（**与本域同文件**）、`tools/semantic-probe/*`、`tools/breaker-probe/*` |
| `users-admin` | `tools/difftest/check-plaintext400-headers.sh`⚠️（**与本域同文件**）、`tools/difftest/run.sh`⚠️、`tools/difftest/diff.py`⚠️ |
| `users-user` | `tools/check-ledger-selfcheck.py`⚠️（**与本域同文件，本节主题**）、`tools/difftest/exp-difftest-*.sh` |
| `follow` | `tools/difftest/` 下新增的 grpc runtime classpath 探查脚本（**名字待定，与本域不撞**） |
| `comment` / `hot` | 各自 `tools/difftest/<域>/` 子目录（**与本域不撞**） |

⚠️ **本域与 message / users-admin 域在 3 个共享文件上有重叠**
（`check-ddl-static.py` / `run.sh` / `check-plaintext400-headers.sh`）⇒ 合并需人工合。

---

## 🆕 **2026-10-29 真实性复核回执**（W-B lane · 「台账结论 ↔ 代码」逐条复核 · task 域）

> **口径**：每条带编号的结论 → 回 **Go 真源（只读）** ＋ **RAG 源码** 两侧核「被描述的那个行为现在还在不在」，
> 再核「证据指针那一行是不是真的在讲这件事」（**指针能解析 ≠ 结论为真** —— 本轮就抓到 3 处「行号合法但指错行/指错文件」）。
> **原值一律保留**；订正以 ⚠️ 段**追加**（明细订正在各条正文）。
> **机器判据**：`python3 tools/difftest/ledger-claim-audit.py --domain task` ⇒ **🔴 落空 = 0**（`RedisURI.java:221` / `LettuceConnectionFactory.java:132` 现被工具**单列进「📦 jar 内第三方源码」档**、不计失败；本轮**独立**从本机 `lettuce-core-6.8.2.RELEASE-sources.jar` 与 `spring-data-redis-4.0.7-sources.jar` 取出那两行核对 ⇒ **行内容与断言逐字相符**，故**不改行号**）。

### 一、逐条结论表（编号 · 原状态 · 现行态 · 依据 · 是否改判）

| 编号 | 原状态 | 现行态 | 依据（两侧 `文件:行号` · 核对日期 2026-10-29） | 改判 |
|---|---|---|---|---|
| `DRIFT-TASK-API-001` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-api/src/main/resources/application.yml:72` = `port: ${TASK_API_PORT:18886}` ＋ Go `service/task/api/etc/task.yaml:3` = `Port: 18886` ⇒ 两侧一致。⚠️ `manage.sh:842` 指针指向**真源**那份 **1179 行**的 `manage.sh`（`:842` = `-p 18886:18886 -p 19005:9005`，本轮已核）⇒ **不是 RAG 仓那份 161 行的** ⇒ 按主线指示**不动** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-API-002` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-api/src/main/java/com/rag/task/task/api/advice/GlobalExceptionHandler.java:76`（引 `go-zero@v1.10.2/rest/httpx/responses.go:150-153`）＋ `:279`/`:292`（引 `:154-155`）＋ `…/dto/GetTaskResp.java:18`（引 `:56-64`）⇒ 本轮**逐行核对** go-zero v1.10.2 真实源码：`:150-153` = gRPC 分支、`:154-155` = 非 gRPC ⇒ `http.Error(..., http.StatusBadRequest)`、`:56-64` = `OkJsonCtx`、`:142-173` = `doHandleError` ⇒ **4 处引用现已全部对得上** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-API-003` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-api/src/main/java/com/rag/task/task/api/controller/TaskController.java:60,87` = `@RequestMapping` / `@PostMapping` **无 `consumes`**（`:57` 的注释明写「不要写 `consumes = "application/json"`」）⇒ Go `service/task/api/internal/handler/task/gettaskhandler.go:18-21` = `json.NewDecoder(r.Body)`（**不看 Content-Type**）⇒ 两侧同形 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-API-004` | 🟡 未修（全域共性） | 🟡 未修（不变） | 判据是 **HTTP 头渲染层**（`application/json` 无 charset ＋ `text/plain;charset=utf-8` 无空格）⇒ **必须真容器活体**才能量到 ⇒ 本轮**未重跑**该对拍 ⇒ **字节级结论标「待核实」**（源码级侧已核：Go 侧常量在 `go-zero@v1.10.2/rest/httpx/responses.go:182` = `w.Header().Set(ContentType, header.ContentTypeJson)`，RAG 侧由 Spring 转换器产出） | 否（字节级待核实）（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-MSG-V-030` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-api/pom.xml:134` = `spring-boot-maven-plugin` ⇒ 产物是可执行 jar | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-001` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskCountStore.java:42-43` = 「按 **group id** 命名空间隔离」＋ `:69` = key `task:goka:count:<groupId>:<messageKey>` ⇒ 原「内存单例 ⇒ 两 group 串号 + 重启归零」**已消除** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-002` | ✅ 已修 | ✅ 已修（不变）**＋ 指针订正** | RAG `…/config/TaskSinkProperties.java:60` = `private Duration initTtl = Duration.ofDays(90);`（**类型已是 `Duration`** ⇒ 与 yaml `init-ttl` 逐字对上 ⇒ 原「`initTtlDays: long` ⇒ 静默丢弃」的死配置已消除）＋ `…/application.yml:254` = `definitions:` · `:260` = `init-ttl: 90d` | **指针订正**（`application.yml:184-185` ⇒ `:249`）（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-003` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-rpc/pom.xml:336-341` = surefire `configuration` ＋ `…/mapper/TaskProgressUpsertParityTest.java:71` = 「刻意**不加** `@EnabledIfDockerAvailable`」⇒ **本轮实测 surefire XML 求和：task-rpc `tests=137 failures=0 errors=0 skipped=0`（20 个 XML，最新 2026-10-29 21:31）** ⇒ 「15 条真 PG 用例静默全跳」**已消除** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-004` | ✅ 已修 | ✅ 已修（不变） | RAG `…/service/RedisPipelines.java:98` = 「这里**绝不能**再调 `operations.exec()` —— pipeline 模式没发过 MULTI」⇒ 原「把 pipeline 当 MULTI/EXEC 事务」**已消除** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-005` | ✅ 已修 | ✅ 已修（不变） | RAG `…/RedisPipelines.java:129-133` = `catch (RuntimeException e)` **向上抛**（只补一条 WARN，不吞）⇒ 与 Go `service/task/rpc/internal/sink/like_sink.go:223-225` / `article_sink.go:308-310`（`pipe.Exec` 报错 ⇒ 向上抛）同形 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-006` | ✅ 已修 | ✅ 已修（不变）**＋ 指针订正** | RAG `…/mq/TaskUserCountConsumer.java:84` / `TaskArticleCountConsumer.java:80` / `TaskRawDiagnosticConsumer.java:54` = 三处**点分** `properties = "auto.offset.reset=…"` ⇒ 与 Go `service/task/rpc/internal/Init/initGoKa.go:131,173,207` = `sarama.OffsetOldest` ／ `initKafkaRaw.go:32` + `initArticleFilter.go:40` = `Offset: "latest"` 逐条对得上 | **指针订正**（`TaskUserCountConsumer.java:80` ⇒ `:84`）（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-007` | ✅ 已修 | ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/resources/application.yml` = `timeout: 3s` ＋ `connect-timeout: 5s` ⇒ **不再落 Lettuce 默认 60s**（`RedisURI.java` 第 221 行 = `* Default timeout: 60 sec`，本轮已从本机 sources jar 独立核过）<br>⚠️ **2026-10-30 真实性复核（follow+task lane · 补指针 + 短确认）**：RAG `service/task/task-rpc/src/main/resources/application.yml:108` = `timeout: 3s` ＋ `:117` = `connect-timeout: 5s`（**本轮逐行重核这两行**）⇒ **现行态 = ✅ 已修（不变）**；⚠️ **jar 内第三方源码指针不许改行号**（`RedisURI.java:221` = `* Default timeout: 60 sec` ⇒ 已在 `lettuce-core-6.8.2.RELEASE-sources.jar` 内核过 ⇒ **行号正确**）；核对日期 2026-10-30 | 否 |
| `DRIFT-TASK-RPC-008` | ✅ 已修 | ✅ 已修（不变） | RAG `…/application.yml:152-158` = `lettuce.pool`（`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / `max-wait: 4s`）＋ `service/task/task-rpc/pom.xml:148` = `commons-pool2` ⇒ **池已启用**（Go `service/task/rpc/internal/svc/service_context.go` 的 `redis.NewClient` 未设 `PoolSize` ⇒ 落库默认 ⇒ **Go 侧本就有池** ⇒ 形态轴已对齐） | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `SD-2026-10-23-TASK-01` | ✅ 已签字 | ✅ **仍成立**（不变） | Go `service/task/rpc/internal/svc/service_context.go:45` = `sqlDB.SetConnMaxLifetime(time.Duration(c.Postgres.ConnMaxLifetimeMinutes))`（**少乘 `* time.Minute`**）＋ `service/task/rpc/etc/task.yaml:53` = `ConnMaxLifetimeMinutes: 30` ⇒ **30 纳秒仍在**；RAG `service/task/task-rpc/src/main/resources/application.yml:37` = `max-lifetime: 1800000`（30 分钟）⇒ **两侧差异仍在 ⇒ 签字仍成立**（决策不是缺陷） | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `SD-2026-10-24-TASK-01` | ✅ 已签字 | ✅ **仍成立**（不变） | Go `service/task/rpc/internal/Init/initGoKa.go:138-145` = `goka.NewProcessor(...)` ⇒ `if err != nil { log.Fatal(err) }`（**进程退出**仍在）；RAG `…/mq/TaskUserCountConsumer.java:72` / `TaskArticleCountConsumer.java:70` / `TaskRawDiagnosticConsumer.java:44` = 三个 `@KafkaListener` ＋ 全仓 `missingTopicsFatal` **零命中** ⇒ **服务存活**仍在 ⇒ **签字仍成立** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |

### 二、仍**待核实**（不默认「仍生效」也不默认「已闭环」）

| 编号 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `DRIFT-TASK-API-004` 的**字节级**结论（charset / 空白） | 属**真容器 HTTP 头渲染层** ⇒ 只能靠真容器活体对拍量到 | 源码级侧已核（Go 侧常量出处 + RAG 侧由 Spring 产出）；**未重跑**对拍 |
| `DRIFT-TASK-RPC-007` / `-008` 的**活体读数**（池等待 4.005s / 桶边界 12） | 需**真 Redis 容器 + 真 Go 客户端** ⇒ 本轮**未重跑** | 已核：**配置层**（`timeout` / `connect-timeout` / `lettuce.pool` 五项 / `commons-pool2` 依赖）+ **jar 内源码行**（`RedisURI.java` 第 221 行、`LettuceConnectionFactory.java` 第 132 行，本机 sources jar 独立核过） |
| `PIT-TASK-01`~`PIT-TASK-34` 中标「仍生效」的**条目** | 这些是**方法论/踩坑形态**，其「仍生效」= 「该坑的触发条件仍可能重现」⇒ 需按条重跑才能证伪 | 本轮对**全部条目**做了「所引仓内文件是否存在」的机械复核 ⇒ **零缺失**；并抽核 `PIT-TASK-06`（Go `Pipeline()` 非事务）/ `-07`（`RedisPipelinesRealRedisGateTest.java` 存在）/ `-18`（三处点分 `properties`） |

### 三、本轮**只核台账、不改产品代码**（AGENTS.md §3.3）

⇒ 本 lane **未修改任何 `service/**` / `tools/**` 文件**；所有订正只落在 `docs/DRIFT/task/**`（本文件 + `api.md` + `rpc.md`）。

---

## 🆕 **2026-10-30 真实性复核回执**（follow+task lane · 「台账结论 ↔ 代码」逐条复核 · task 域 · 本文件）

> **口径**：同 [`docs/DRIFT/follow/api.md`](../follow/api.md) 的 2026-10-30 回执（Go 真源 + RAG 两侧逐条核 · 指针逐条核到「那一行确实讲这件事」）。
> **原值一律保留**；本轮**未改判任何一条**，只做**指针补齐 + 短确认**。
> **机器判据**：`python3 tools/difftest/ledger-claim-audit.py --domain task --strict` ⇒ **🔴 落空 = 0**（`RedisURI.java:221` / `LettuceConnectionFactory.java:132` 仍被工具**单列进「📦 jar 内第三方源码」档**、不计失败；本轮**再次独立**从本机 `lettuce-core-6.8.2.RELEASE-sources.jar` 与 `spring-data-redis-4.0.7-sources.jar` 取出那两行核对 ⇒ **行内容与断言逐字相符** ⇒ **不改行号**）。

### 一、逐条结论表（编号 · 原状态 → 现行态 · 两侧依据 · 是否改判 · 核对日期 2026-10-30）

| 编号 | 原状态 → 现行态 | 依据（两侧 `文件:行号`） | 改判 |
|---|---|---|---|
| `DRIFT-TASK-API-001` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-api/src/main/resources/application.yml:72` = `port: ${TASK_API_PORT:18886}`（**本轮重核**）· Go `service/task/api/etc/task.yaml:3` = `Port: 18886` ⇒ **两侧逐字一致**；⚠️ `manage.sh:842` 指针落在**真源**那份 **1179 行**的 `manage.sh`（`:842` = `-p 18886:18886 -p 19005:9005`，**本轮重核**）⇒ **不是 RAG 仓那份 160 行的** ⇒ 按主线指示**不动** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-API-002` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-api/src/main/java/com/rag/task/task/api/advice/GlobalExceptionHandler.java:76`（引 `go-zero@v1.10.2/rest/httpx/responses.go:150-153`）＋ `:279` / `:292`（引 `:154-155`）＋ `service/task/task-api/src/main/java/com/rag/task/task/api/dto/GetTaskResp.java:18`（引 `:56-64`）⇒ **本轮重核这 5 行确实在讲各自引用的那件事** ⇒ 引用与 go-zero v1.10.2 真实源码仍**逐条对得上** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-API-003` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-api/src/main/java/com/rag/task/task/api/controller/TaskController.java:60` = `@RequestMapping("/taskcenter/v1")` ＋ `:87` = `@PostMapping("/task/get")` ⇒ **两处都无 `consumes`** · Go `service/task/api/internal/handler/task/gettaskhandler.go:19` = `decoder := json.NewDecoder(r.Body)`（**不看 Content-Type**）⇒ **两侧同形** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-API-004` | 🟡 未修（全域共性）→ 🟡 未修（不变；**字节级待核实**） | 判据是 **HTTP 头渲染层**（`application/json` 无 charset ＋ `text/plain;charset=utf-8` 无空格）⇒ **必须真容器活体**才能量到 ⇒ 本轮**未重跑**该对拍 ⇒ **字节级结论标「待核实」**（源码级侧已核：Go 侧常量在 `go-zero@v1.10.2/rest/httpx/responses.go:182` = `w.Header().Set(ContentType, header.ContentTypeJson)`，RAG 侧由 Spring 转换器产出）⇒ **维持 🟡，不因未复跑而升级或降级** | 否（字节级待核实）（核对日期 2026-10-30） |
| `DRIFT-MSG-V-030` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-api/pom.xml:134` = `spring-boot-maven-plugin` ⇒ **产物是可执行 jar**（**本轮重核该行内容**） | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-001` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskCountStore.java:42-43` = 「现在按 **group id** 命名空间隔离」＋ `:69` = key `task:goka:count:<groupId>:<messageKey>` ⇒ 原「内存单例 ⇒ 两 group 串号 + 重启归零」**已消除**（**本轮重核这 3 行**） | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-002` | ✅ 已修 → ✅ 已修（不变）**＋ 指针订正（沿用 2026-10-29）** | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/config/TaskSinkProperties.java:60` = `private Duration initTtl = Duration.ofDays(90);`（**本轮重核**）⇒ **类型已是 `Duration`** ⇒ 与 yaml `init-ttl` 逐字对上 ⇒ 原「`initTtlDays: long` ⇒ 静默丢弃」的死配置已消除 | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-003` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-rpc/pom.xml:336-341` = surefire `configuration` ＋ `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/mapper/TaskProgressUpsertParityTest.java:71` = 「刻意**不加** `@EnabledIfDockerAvailable`」（**本轮重核这两处**）⇒ 「15 条真 PG 用例静默全跳」**已消除** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-004` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/service/RedisPipelines.java:98` = 「这里**绝不能**再调 `operations.exec()` —— pipeline 模式没发过 MULTI」（**本轮重核**）⇒ 原「把 pipeline 当 MULTI/EXEC 事务」**已消除** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-005` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/service/RedisPipelines.java:129` = `catch (RuntimeException e)`（**向上抛**，只补一条 WARN，不吞；本轮重核该行）· Go `service/task/rpc/internal/sink/like_sink.go:223` = `if _, err := pipe2.Exec(ctx); err != nil {` ＋ `service/task/rpc/internal/sink/article_sink.go:308` = 同款 ⇒ **两侧同形** | 否（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-006` | ✅ 已修 → ✅ 已修（不变）**＋ 指针订正（沿用 2026-10-29）** | RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskUserCountConsumer.java:84` / `…/TaskArticleCountConsumer.java:80` / `…/TaskRawDiagnosticConsumer.java:54` = 三处**点分** `properties = "auto.offset.reset=…"`（**本轮重核这 3 行**）· Go `service/task/rpc/internal/Init/initGoKa.go:131` / `:173` / `:207` = `sarama.OffsetOldest` ＋ `service/task/rpc/internal/Init/initKafkaRaw.go:32` = `Offset: "latest"` ＋ `service/task/rpc/internal/Init/initArticleFilter.go:40` = `Offset: "latest"` ⇒ **逐条对得上** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-TASK-RPC-007` | ✅ 已修 → ✅ 已修（不变）**＋ 补指针** | RAG `service/task/task-rpc/src/main/resources/application.yml:108` = `timeout: 3s` ＋ `:117` = `connect-timeout: 5s`（**本轮逐行重核**）⇒ **不再落 Lettuce 默认 60s**（jar 内 `RedisURI.java:221` = `* Default timeout: 60 sec`，**行号不改**） | 否（**补指针**）（核对日期 2026-10-30） |
| `DRIFT-TASK-RPC-008` | ✅ 已修 → ✅ 已修（不变） | RAG `service/task/task-rpc/src/main/resources/application.yml:152-158` = `lettuce.pool`（`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 0` / `max-wait: 4s`）＋ `service/task/task-rpc/pom.xml:148` = `commons-pool2`（**本轮重核这两处**）⇒ **池已启用**（Go `service/task/rpc/internal/svc/service_context.go` 的 `redis.NewClient` 未设 `PoolSize` ⇒ 落库默认 ⇒ **Go 侧本就有池** ⇒ 形态轴已对齐） | 否（核对日期 2026-10-30） |
| `SD-2026-10-23-TASK-01` | ✅ 已签字 → ✅ **仍成立**（不变） | Go `service/task/rpc/internal/svc/service_context.go:45` = `sqlDB.SetConnMaxLifetime(time.Duration(c.Postgres.ConnMaxLifetimeMinutes))`（**少乘 `* time.Minute`**，本轮重核该行）＋ Go `service/task/rpc/etc/task.yaml:53` = `ConnMaxLifetimeMinutes: 30` ⇒ **30 纳秒仍在**；RAG `service/task/task-rpc/src/main/resources/application.yml:37` = `max-lifetime: 1800000`（30 分钟）⇒ **两侧差异仍在 ⇒ 签字仍成立**（决策不是缺陷） | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `SD-2026-10-24-TASK-01` | ✅ 已签字 → ✅ **仍成立**（不变） | Go `service/task/rpc/internal/Init/initGoKa.go:138-145` = `goka.NewProcessor(...)` ⇒ `if err != nil { log.Fatal(err) }`（**进程退出**仍在，本轮重核该段）· RAG `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/mq/TaskUserCountConsumer.java:72` / `…/TaskArticleCountConsumer.java:70` / `…/TaskRawDiagnosticConsumer.java:44` = 三个 `@KafkaListener` ＋ 全仓 `missingTopicsFatal` **零命中** ⇒ **服务存活**仍在 ⇒ **签字仍成立** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |

### 二、本轮新增/订正的证据指针

| 落点 | 原指针 | 现行指针 | 性质 |
|---|---|---|---|
| §一 `DRIFT-TASK-RPC-007` | `service/task/task-rpc/src/main/resources/application.yml`（**无行号** ⇒ 不可复算） | **`…/application.yml:108`**（`timeout: 3s`）＋ **`:117`**（`connect-timeout: 5s`） | **补指针** |

### 三、仍未核到（**待核实**，不默认「仍成立」也不默认「已闭环」）

| 项 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `DRIFT-TASK-API-004` 的**字节级**结论（charset / 空白） | 属**真容器 HTTP 头渲染层** ⇒ 只能靠真容器活体对拍量到 ⇒ 本轮**未重跑** | 源码级侧已核（Go 侧常量出处 + RAG 侧由 Spring 产出）⇒ **维持 🟡 未修**（本轮未独立改判） |
| `DRIFT-TASK-RPC-007` / `-008` 的**活体读数**（池等待 4.005s / 桶边界 12） | 需**真 Redis 容器 + 真 Go 客户端** ⇒ 本轮**未重跑** | 已核**配置层**（`timeout` / `connect-timeout` / `lettuce.pool` 五项 / `commons-pool2` 依赖）+ **jar 内源码行**（`RedisURI.java:221`、`LettuceConnectionFactory.java:132`） |
| `SD-2026-10-23-TASK-01` / `SD-2026-10-24-TASK-01` 的**活体复现**（30ns 真的生效 / processor 失败真的进程退出） | 需**真 PG + 真 Kafka** ⇒ 本轮**未重跑** | 已核**Go 源码行**（`service_context.go:45` / `initGoKa.go:138-145`）+ **RAG 配置行** ⇒ **签字仍成立**（本轮未独立改判） |
| `PIT-TASK-01`~`PIT-TASK-34` 中标「仍生效」的**条目** | 「仍生效」= 「该坑的触发条件仍可能重现」⇒ 需按条重跑才能证伪 ⇒ 本轮**未逐条复现** | 机械核对了「所引仓内文件是否存在」（**零缺失**）＋ 抽核 `PIT-TASK-06` / `-07` / `-18` ⇒ **状态沿用 2026-10-29 结论**，本轮**未独立改判**（明细见 [`docs/Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md) 的 2026-10-30 回执） |

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


### lane W13

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| DRIFT-TASK-API-004 | D（经分析确认不实现 · 容器层渲染 ⇒ 应用层无实现路径） | P2 | 否 | Go `go-zero@v1.10.2/rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"` ⇒ **分号后有空格**）＋ `go-zero@v1.10.2/rest/httpx/responses.go:182`（`w.Header().Set(ContentType, header.ContentTypeJson)` ⇒ Go 的 JSON 响应头带 charset）＋ Go 标准库 `net/http/server.go:2351`（`h.Set("Content-Type", "text/plain; charset=utf-8")` ⇒ 错误响应同样带空格）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"` ⇒ **charset 已在**，只差一个 OWS）＋ `:151`（写入点）＋ `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2`（⇒ 共享层注册 ⇒ **10 个 api 全生效**）＋ `service/task/task-api/pom.xml:61`（task-api 依赖 `common` ⇒ 对本域生效）＋ `service/task/task-api/src/main/java/com/rag/task/task/api/advice/GlobalExceptionHandler.java:71`（`TEXT_PLAIN_UTF8 = "text/plain; charset=utf-8"` ⇒ **应用层写的本来就是 Go 的带空格原串**）＋ `:308`（写入点）⇒ ⇒ **台账记的「RAG `application/json`（无 charset）」这一半已过期**：共享层 2026-10-25 落地 `GoStyleJsonContentTypeAdvice` 后，JSON 响应头已是 `application/json;charset=utf-8`（**带 charset**）⇒ 本轮实跑 `mvn -o -pl service/common test -Dtest=GoStyleJsonContentTypeCharsetTomcatTest` ⇒ `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（**真 Tomcat ＋ 裸 socket** ⇒ 字节级读数 = `application/json;charset=utf-8`，期望值常量在 `service/common/src/test/java/com/rag/common/http/GoStyleJsonContentTypeCharsetTomcatTest.java:101`、断言在 `:120`）⇒ ⇒ **仍成立的那一半 = `text/plain` 的 OWS**：本轮实跑 `mvn -o -pl service/task/task-api test -Dtest=TaskApiFailurePathMatrixTest` ⇒ `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`（**真 Tomcat ＋ 原始字节**）⇒ `service/task/task-api/src/test/java/com/rag/task/task/api/TaskApiFailurePathMatrixTest.java:178`（实测读数常量 `text/plain;charset=utf-8` ⇒ **无空格**）＋ `:259`（断言点）vs Go 的 `text/plain; charset=utf-8` ⇒ ⇒ **差异仍成立**（字节级只差一个 OWS；且**经分析确认不可修**：`tomcat-embed-core 11.0.24` 的 `org/apache/coyote/http11/Http11OutputBuffer.java:370-376`（容器重渲染 ⇒ `;` 后无空格）＋ Servlet API 的 `setContentType`／`setHeader`／`addHeader` 三条路都汇到 `Response.setContentType` ⇒ **应用层无路可走** ⇒ 落点不在 `service/common` ⇒ 与 like／message 等域同款共性）⇒ ⇒ 归 D · **不阻断阶段一**；P2（展示面 · 字节级；影响面 = 全部 api 的 JSON 与 `text/plain` 响应头 ⇒ 但按 RFC 9110 OWS 不显著 ⇒ **语义等价**，不命中 P0 九条） | 2026-10-30 |
