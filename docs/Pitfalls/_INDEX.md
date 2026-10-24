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

## 计数（2026-10-25 机械扫描实测）

> **口径**：一条 = 一个**二级/三级标题**的独立坑位条目（`## P-xxx` / `## PIT-xxx` / `## PF-xxx` / `## PITFALL-xxx` …）。
> `_cross` 与 `common` 下 4+5 份用**散文式**标题（「坑 1 / 坑 2」「元教训」），**不计入**本表条数，但内容同样有效。

| 项 | 值 |
|---|---|
| **全域坑位条目** | **280 条** / **29 份有内容的台账**（另 **16 份 0 字节**坑位，见「待填」）；另有 `common` 5 份 + `_cross` 4 份用**散文式**标题，不计入条数 |
| **编号前缀族** | **8 族 / 15 个具体前缀**：`P-`（article/users/like 四家共用裸号）· `PIT-` · `PF-` · `PITFALL-` · `P-ADMINAPI-` · `P-HOTAPI-` · `P-HOTRPC-` · `P-MSGAPI-` |
| **编号撞号** | **22 处** —— 见下方「编号撞号」 |

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
| 🆕 `global` | 0 份（3 个坑位已建） | 收口域 | **跨微服务 + `service/common` 影响的搁置漂移**（§3.5）。按议题分档：`common-tails` / `cross-domain` / `cycle-deps` |
| `_cross` | 4 份 | 散文式 | Flyway 账本撞号；框架机制静默失效实证；「跑绿 ≠ 对」的元教训；**陈旧 surefire 报告抬高测试计数** |

## 待填（空文件即坑位）

`article/{common}` · `common/{_signed-deviations}` · `hot/…` 已全填 ·
`like/…` 已全填 · `message/…` 已全填 · `task/{api,common,proto}` ·
`users/admin/…` 已全填 · `users/user/…` 已全填 ·
`favorite/*`（本域本轮未做）· `follow/common` 已填、`follow/proto` ·
`comment/{common,proto}` 已填、`comment/proto` ·
`_cross/{message-domain-wide,open-items,signed-deviations}`

## 与 AGENTS.md §3.3 的关系

AGENTS.md **§3.3 保留全部规则条文**（那是每次都会进上下文的纪律），
案例与证据在本目录。规则里凡提到具体案例处，均已改为指向本目录的链接。

## 搬移来源

每段内容首行为 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵，正文为原样字节拷贝。
逐段台账见 [`../DRIFT/_MANIFEST.json`](../DRIFT/_MANIFEST.json)（39 段），
可重跑脚本 `tools/drift-move.py`（`extract` / `verify` / `delete`）。
