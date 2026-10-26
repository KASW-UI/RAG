# Pitfalls 台账总索引

> **这里记「踩过的坑」**：框架机制静默失效、绿测试锁死错误答案、基础设施误用……
> 与 [`../DRIFT/_INDEX.md`](../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「与 Go 不一致」（需要对齐的差异），Pitfalls 记「为什么会踩」（教训本身）。**
> 每条都应回答：**症状像什么 · 真实原因 · 怎么证伪 / 怎么确认 · 留下的守门网**。

> 📌 **更新日期：2026-10-25（机械扫描全档 · 补总数与编号体检）**
> 合并前索引只列了 5 条，合并后各域产出大量新坑位，本索引已按实际内容重写。
> **2026-10-25 增量**：`hot/rpc.md` 新增 **P-HOTRPC-16 / 17 / 18**（累计 **18 条**）。
> **2026-10-25 第十三轮增量**：`hot/rpc.md` 再加 **P-HOTRPC-19 / 20**（累计 **20 条**）——
> **19**：变异打在 `target/classes` 会被 `mvn test` 的资源拷贝**覆盖** ⇒ 变异无效 ⇒ 守门**假绿**（必须打在 `src/main/resources`）；
> **20**：「Go 的上限是个常量」是错的 —— `net/http` 的真实上限 = 常量 **+ 4096 bufio slop**。
> **2026-10-25 增量**：`_cross/testing-pitfalls.md` 新增 **「陈旧 surefire 报告静默抬高测试计数」**
> （`user-rpc` 实测 XML 求和 129 / 真实 125，差 4 来自 10-23 的孤儿报告）⇒ **测试计数的口径已定死**。
> **2026-10-25 第二十五轮增量**：`global/` 三份台账**已填**（`PIT-GLOBAL-001..019` 共 **19 条**）
> ⇒ 本索引「已有内容」表里 global 那行原写的 **「0 份（3 个坑位已建）」已过期**（那是从「坑位已建、内容未填」时代留下的快照）。
> 另：`PIT-GLOBAL-020`（变异只准在隔离工作树跑）**记录在** [`../DRIFT/global/lanes/_PROTOCOL.md`](../DRIFT/global/lanes/_PROTOCOL.md)（不重复落本档）。
> **2026-10-25 第二十六轮增量**（L5 lane + 第二十五轮回归收尾）⇒ global 域再加 **5 条** ⇒ `docs/Pitfalls/global/` 三份合计 **24 条**（`PIT-GLOBAL-020` 仍在 `lanes/_PROTOCOL.md`）：
> **021**（变异判定只认 surefire XML：`-- in` 后面是 `@DisplayName` 而非 FQN）·
> **022**（抄来的「同形态先例」编号也要复核 —— 错的指针比空指针更贵）·
> **023**（「这个出口在 XX 层 ⇒ 不经过 X」是推断，必须落到**注册点**）·
> **024**（阻塞式启动器 + 句柄设在返回之后 ⇒ 句柄永远拿不到 ⇒ 调度器泄漏 ⇒ 守门失真）·
> **025**（MockMvc 与真 Tomcat 是两条观测路径 ⇒ 共享层改响应头会打红别的域的 MockMvc 守门）。
> **2026-10-26 第二十七轮增量**（6 条 lane + 整合者修缺陷）⇒ global 域再加 **3 条** ⇒ `docs/Pitfalls/global/` 三份合计 **27 条**（`PIT-GLOBAL-020` 仍在 `lanes/_PROTOCOL.md`）：
> **026**（`GlobalOpenTelemetry.set()` 存的是 `new ObfuscatedOpenTelemetry(sdk)` **包装器** ⇒ 断言「全局单例是不是我装的那个」**恒红** ⇒ 只能比 `getTracerProvider()`）·
> **027**（守门的绿必须问「它绿得**独立**吗」⇒ 靠**执行顺序**才成立的绿 = 假稳定 ⇒ 「快调用」用例会因**首调类加载开销**首调就红 ⇒ 必须预热 + 预热要用**独立 probe**）·
> **028**（slow 阈值的「配置被忽略」可静默发生 ⇒ 守门的「占用时长」必须 **< Go 兜底阈值 500ms** ⇒ 否则退化不可观察）。
> **2026-10-26 第二十八轮增量**（4 条 lane：生产启动阻塞 + 守门缺口）⇒ global 域再加 **2 条** ⇒ `docs/Pitfalls/global/` 三份合计 **29 条**（本块）+ 下面一块 2 条 ⇒ 合计 **31 条**：
> **029**（静态扫描不排除 `target/` ⇒ 构建产物的**旧副本**被当源码判 ⇒ baseline 键永远匹配不上 ⇒ **门禁恒红** ⇒ 一个恒红的守门等于没有守门 ⇒ 同源 **PIT-GLOBAL-019**「旧字节码」）·
> **030**（守门只扫一半的模块 ⇒ 另一半的缺陷只能靠**手工盘点**发现 ⇒ **手工必漏**（L11 手工查 10 个 rpc，漏了 `hot-rpc`）⇒ **守门的「覆盖范围」本身就是缺陷面** ⇒ 枚举要显式注释「**不覆盖哪些**」，如 `service/common` 刻意不入 jar 门禁）。
> **2026-10-26 第二十八轮增量 · 二**（全仓回归「跑红」的定性）⇒ global 域再加 **2 条** ⇒ 合计 **31 条**：
> **031**（守门断言的字符串可能**也出现在被测文件的注释里** ⇒ `contains()` 恒真 ⇒ **变异测不出来** ⇒ 断言前必须先剥 XML/YAML 注释 —— L14 实测：M1 变异删掉真声明，那条断言**居然是绿的**）·
> **032**（**「跑红」也可能是假红** ⇒ 两类非代码原因：**构建竞态**（两个 Maven 进程同时写同一 `target/` ⇒ 报出的错误指向**完全无关的模块**）· **环境时序**（依赖真 Kafka/PG 的守门）⇒ 必须**单独复跑**定性 ⇒ 与「跑绿 ≠ 对」对称 ⇒ **回归跑着时不要在别的进程里构建同一个 reactor**）。

## 计数（2026-10-25 机械扫描实测）

> **口径**：一条 = 一个**二级/三级标题**的独立坑位条目（`## P-xxx` / `## PIT-xxx` / `## PF-xxx` / `## PITFALL-xxx` …）。
> `_cross` 与 `common` 下 4+5 份用**散文式**标题（「坑 1 / 坑 2」「元教训」），**不计入**本表条数，但内容同样有效。

| 项 | 值 |
|---|---|
| **全域坑位条目** | **304 条** / **32 份有内容的台账**（另 **15 份 0 字节**坑位，见「待填」）；另有 `common` 5 份 + `_cross` 4 份 + `hot/common` 1 份用**散文式**标题，不计入条数 |
| **编号前缀族** | **8 族 / 15 个具体前缀**：`P-`（article/users/like 四家共用裸号）· `PIT-` · `PF-` · `PITFALL-` · `P-ADMINAPI-` · `P-HOTAPI-` · `P-HOTRPC-` · `P-MSGAPI-` ·（+ `PIT-GLOBAL-` / `PIT-LIKEAPI-` / `PIT-USERSCOMMON-` / `PF-ARTICLE-` / `PITFALL-TASK-` / `PITFALL-MSG-` / `PITFALL-FOLLOW-` / `PIT-COMMENT-` / `PIT-LIKE-` / `PIT-USERS-`）|
| **编号撞号** | **22 处** —— 见下方「编号撞号」 |

> ⚠️ **2026-10-25 第二十五轮复核（口径已复现）**：本表原先写 **280 条 / 29 份 / 16 份 0 字节**，其中
> **「280 条」本身是对的**（八域 users 67 + task 34 + message 36 + like 30 + hot 45 + article 37 + comment 15 + follow 16 = 280），
> 但**漏算了 `global/` 三份的 19 条** ⇒ 本次改为 **299 条**；「有内容份数」29 → **32**（+global 三份）·
> 「0 字节」16 → **15**（其中 1 份已被散文式内容填上）。
> ⚠️ **复现口径（供后续重扫照抄，否则数会飘）**：一条 = 一个 `##`/`###` 标题，
> 标题在去掉 `##` 与前置装饰字符（emoji 等）后**以 `P` / `PIT` / `PF` / `PITFALL` 开头并紧跟数字或编号段**；
> `## 🔴 PITFALL-TASK-RPC-01`（emoji 在前）与 `## P1 — …`（无分隔符）**都算** ⇒ 漏任一条都会少算 34（task）或 22（hot）。

⚠️ **与 `docs/Intentional_Deviations/` 同款缺陷**：两档的编号**都**跨文件复用 ⇒ **编号不可寻址**。
⇒ 引用坑位时**必须同时给出「文件 + 域」**，不能只写 `P-1`。

## 🔴 编号撞号（2026-10-25 扫描发现 · 台账自身的缺陷）

**同一编号在不同文件指向不同的坑** ⇒ 「查 `P-1`」会查出 3 个互不相干的条目。

| 编号 | 撞号文件数 | 涉及文件 |
|---|---|---|
| `P-1` … `P-7` | **3 处/条** | `article/api.md` · `users/admin/rpc.md` · `users/user/api.md` |
| `P-8` / `P-9` / `P-12` … `P-14` | 2 处/条 | `article/api.md` · `users/admin/rpc.md`（`P-10`/`P-11` 另有 `like/rpc.md`） |
| `P-01` … `P-07` | 2 处/条 | `like/rpc.md` · `users/user/rpc.md` |
| `PITFALL-TASK-RPC-15` | 2 处/条 | `task/rpc.md`（**同一文件内重复编号**） |

⇒ **修法方向（未实施）**：统一前缀 + 域内连续（`P-<域>-<NN>`）。
⚠️ **改编号会破坏既有引用**（DRIFT 台账、javadoc、测试注释都按旧号检索）⇒ 属**跨档改造**，**本轮只登记不动**。

⚠️ **2026-10-25 第二十五轮复核**：`global/` 域用的是**域内唯一前缀** `PIT-GLOBAL-` ⇒ **不新增撞号（+0）**。
本表 **22 处**仍是 2026-10-25 首扫口径，**本轮未重算**（重算会因「同文件内重复编号」（如 `PITFALL-TASK-RPC-15` 与 `PITFALL-TASK-RPC-15·补`）
是否算「一处」而产生口径漂移 ⇒ 宁可不改数，也不用一个**没验证过的口径**去覆盖一个**已标注日期的快照**。

## 目录结构与 `docs/DRIFT` 同构（`<域>/<api|rpc>.md`），空文件即待填坑位

## 已有内容（按域）

| 域 | 已填 | 条数 | 主要坑 |
|---|---|---|---|
| `users` | 6 份 | 67 | MyBatis-Plus 配置前缀写错**静默归零**；脚本批量改 N 处须配**守恒断言**；双侧活体对拍编排（admin-rpc 3s dialTimeout 导致的启动顺序硬约束） |
| `task` | 1 份 | 34 | Boot 4 拆模块缺依赖是**硬启动失败**不是静默失效；`@EnabledIfDockerAvailable` 把「拉不到镜像」与「Docker 不可用」压成同一个 skip ⇒ 15 条真 PG 用例静默全跳 |
| `message` | 2 份 | 36 | 响应已 committed 后 `setHeader` 被静默忽略 ⇒ text/plain 400 丢 `Content-Type`（4 轮对拍全绿仍漏） |
| `like` | 2 份 | 30 | Boot 4 把 Kafka 自动配置整体搬出 `spring-boot-autoconfigure` ⇒ `@KafkaListener` **完全静默忽略** |
| `hot` | 3 份 | 45 | 重桶 `hash()` 仿错影响 49.5% 的 key；Jackson 3 ⇒ `ObjectMapper` Bean 缺失；**H2 形态复发**（`hot-rpc` 缺 `GrpcServerConfigurer` ⇒ 拦截器从未挂上 server）；**「跨域登记 ≠ 域内认领」**（缺陷记在别人台账里 ⇒ 承接域不知道欠着）；**裸 `ServerCallHandler` 在 unary 方法上不被 dispatch** ⇒ 「超时生效」类断言**是假绿**；**并行跑时 Testcontainers 容器起不来** ⇒ 看起来像回归其实不是；**变异打在 `target/classes` 会被构建覆盖 ⇒ 假绿**；**Go 的上限 = 常量 + bufio slop**（不是常量本身） |
| `article` | 2 份 | 37 | `@Transactional(readOnly=true)` 在真 PG 触发 `25006`；jsonb 恒 `null` 而 101 条测试全绿 |
| `comment` | 2 份 | 15 | `comment-rpc` 的 17 个类曾是空壳而全量编译 `EXIT=0` |
| `follow` | 2 份 | 16 | —— |
| `common` | 5 份 | 散文式 | 请求保护层四层：**recover → breaker → shedding → 业务观测**；链的定义点必须在共享模块，否则 10 个 rpc server 各写一遍 = 10 个可能写错的地方 |
| 🆕 `global` | **3 份** | **24** | **跨微服务 + `service/common` 影响的搁置漂移**（§3.5）收口域。按议题分档：`common-tails` / `cross-domain` / `cycle-deps`。<br>主要坑：**查 Go 出处只在项目树里 grep 必落空**（真源在 `~/go/pkg/mod` 的 go-zero 源码里）· **「配置里没写」≠「没开」**· **变异两种假绿**（编译不过⇒读旧 XML / 红在更早断言上）· **改上游资源做变异而只跑下游⇒假阴性**· **用被测常量构造输入⇒恒绿**· **阻塞式启动器 + 句柄设在返回之后⇒句柄永远拿不到⇒调度器泄漏⇒守门失真**（024）· **MockMvc 与真 Tomcat 是两条观测路径⇒共享层改响应头会打红别的域的 MockMvc 守门**（025） |
| `_cross` | 4 份 | 散文式 | Flyway 账本撞号；框架机制静默失效实证；「跑绿 ≠ 对」的元教训；**陈旧 surefire 报告抬高测试计数** |

## 待填（空文件即坑位）

> **2026-10-25 第二十五轮机械扫描订正**（原表有 4 处「已填」断言与磁盘不符）：

| 坑位 | 状态 |
|---|---|
| `_cross/{message-domain-wide, open-items, signed-deviations}` | 0 字节 |
| `article/common` | 0 字节 |
| `comment/{common, proto}` | 0 字节（⚠️ 原表写「已填」⇒ **实为未填**） |
| `favorite/{api, common, proto, rpc}` | 0 字节（本域本轮未做） |
| `follow/{common, proto}` | 0 字节（⚠️ 原表写「`follow/common` 已填」⇒ **实为未填**） |
| `task/{api, proto}` | 0 字节 |
| `users/admin/_shared` | 0 字节（⚠️ 原表写「`users/admin/…` 已全填」⇒ **实为未填**） |

⇒ **已全填**：`hot/…`（3 份）· `like/…`（2 份）· `message/…`（2 份）· `users/{_shared, common, user/*}`（6 份）· `global/…`（3 份）· `common/…`（5 份，散文式）· `_cross/`（4 份散文式 + 3 份 0 字节）。

⚠️ **两个「本目录不存在」的路径**（原表把它们列进待填 ⇒ 指错了地方）：
- `task/common` —— **不存在**（Go 侧 `task` 域同样没有 `-common`，不是遗漏，是设计如此）；
- `common/_signed-deviations` —— **不在本目录** ⇒ 它在 [`../Intentional_Deviations/common/_signed-deviations.md`](../Intentional_Deviations/common/_signed-deviations.md)（签字档，不是踩坑档）。

## 与 AGENTS.md §3.3 的关系

AGENTS.md **§3.3 保留全部规则条文**（那是每次都会进上下文的纪律），
案例与证据在本目录。规则里凡提到具体案例处，均已改为指向本目录的链接。

## 搬移来源

每段内容首行为 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵，正文为原样字节拷贝。
逐段台账见 [`../DRIFT/_MANIFEST.json`](../DRIFT/_MANIFEST.json)（**39 段**搬移 + **1 段**后补 ⇒ `entries` 实测 **40**），
可重跑脚本 `tools/drift-move.py`（`extract` / `verify` / `delete`）。
