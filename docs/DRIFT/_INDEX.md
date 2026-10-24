# DRIFT 台账总索引

> 本目录是 **AGENTS.md 漂移档案的物理落点**。
> AGENTS.md 保留「政策 + 索引」，本目录承接「逐条漂移与修复的完整证据」。
> 搬移于 2026-10-23，来源：AGENTS.md @ `81744d1` + 工作区未提交增量。

## 搬移账目

| 项 | 值 |
|---|---|
| 源文件 | `AGENTS.md` 369,625 B / 4094 行 |
| 搬出 | **39 段 / 3,629 行 / 330,494 B**（另 4 处手工搬移：§7.1 锚点表 / §4.3 原话 / §6.3 决策详述 / §0 原文快照） |
| 保留 + 压缩 | AGENTS.md 最终 **30,207 B / 396 行**，全文可进 32 KB 上下文预算 |
| 校验 | **零丢失 · 零重复**（逐字节）；删除前另有 39 段互不重叠 + 覆盖全文无空隙的检查 |
| 逐段指针 | [`_MANIFEST.json`](./_MANIFEST.json) —— 每段带 `AGENTS.md L<a>-L<b>`；6 段进 `docs/Pitfalls/` |
| 可重跑脚本 | `tools/drift-move.py`（幂等；重新执行会覆盖同路径文件） |

每个搬入文件的首行是 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵行。
**正文为原样字节拷贝，未改一字、未重排、未润色。** 校验方式：剥离哨兵行后与源行区间逐字节比对。

## 按域索引

| 域 | 台账 | 状态 |
|---|---|---|
| **`users`** | [`users/_shared.md`](./users/_shared.md)（67 KB，最大）<br>[`users/user/api.md`](./users/user/api.md) · [`users/user/rpc.md`](./users/user/rpc.md)<br>[`users/admin/api.md`](./users/admin/api.md) · [`users/admin/rpc.md`](./users/admin/rpc.md)<br>[`users/admin/_shared.md`](./users/admin/_shared.md) | 唯一做过完整 Go↔RAG 行为审计的域 |
| **`message`** | [`message/api.md`](./message/api.md) · [`message/rpc.md`](./message/rpc.md)<br>[`_cross/message-domain-wide.md`](./_cross/message-domain-wide.md) | 3 处请求解析层漂移 + 6 项启动级缺陷全关 |
| **`like`** | [`like/api.md`](./like/api.md) · [`like/rpc.md`](./like/rpc.md) | 事实投递链路**跨三个仓库**（含 `Sea-BreakTheWaves`） |
| **`common`** | [`common/alignment-2026-10-23.md`](./common/alignment-2026-10-23.md) ⭐ **组件级重对齐（最大一份，28 条编号未修）**<br>[`common/protection.md`](./common/protection.md) · [`common/http.md`](./common/http.md)<br>[`common/deadline.md`](./common/deadline.md) · [`common/audit.md`](./common/audit.md)<br>⚠️ 签字偏离已迁出 ⇒ [`../Intentional_Deviations/`](../Intentional_Deviations/_INDEX.md) | 🔴 **不是「请求保护层四层的最终落点」** —— `alignment-2026-10-23.md` §〇 已推翻「audit.md 标『✅ 审计完成』的覆盖面不成立」：5 个可比包 **3 个 NOT EQUIVALENT**（communityfact / logger / observability / snowflake），仅 `response` 逐条等价。**未修 = 28 条编号条目**（DRIFT-COMMON 3 + communityfact 15 + logger 7 + snowflake 3）+ 5 项未编号观察 + 9 项跨域交接 ⇒ **common 是共享层，其他域的尾巴挂在它身上**（like-api 015 / follow-api 008 / hot D14·D25·D27 / message -010·-011·-013 的落点全在此） |
| **`hot`** | [`hot/rpc.md`](./hot/rpc.md) · [`hot/common.md`](./hot/common.md)<br>[`hot/api.md`](./hot/api.md) | ✅ **三组件现已全部拿到双侧活体对拍**（common 逐字节一致 · rpc 第十一轮真 Kafka + 2×Redis · api 第五轮端到端 29 条）。⚠️ **剩余三条全在域外**：D21 / D22 / D23（Tomcat 请求目标校验 / form 参数上限 1000 vs Go 2048 / 请求行 8 KiB vs Go 1 MiB）**影响全部 10 个 api 模块**，另 D14 / D25 / D27 移交 `service/common` |
| **`article`** | [`article/common.md`](./article/common.md)<br>[`article/api.md`](./article/api.md) · [`article/rpc.md`](./article/rpc.md) · [`article/proto.md`](./article/proto.md) | 🔴 **api 与 rpc 均已对照，且都仍判 `NOT EQUIVALENT`**：api 第十三轮（41 文件 / 5,733 行，2026-10-24 修复轮 4 条未修漂移已修 + 变异验证）判定**不变**（仍有 DRIFT / UNRESOLVED 在，无双侧活体对拍）；rpc 13 条已改，**S-5 阻塞于 security 域迁移**，MQ 事件契约未冻结，U-2 真并发用例未写 |
| **`favorite`** | [`favorite/rpc.md`](./favorite/rpc.md) | **rpc 已落地 + 首轮源码级对照（2026-10-23）**：36 文件 / 7,057 行 / **56 例绿**（真 PG）；本轮**修掉 6 处 L2 语义偏差**（含 3 处「写路径误用读路径 5007」）。🔴 **2026-10-25 本轮未做**（合并时按用户要求跳过）⇒ `api`/`common`/`proto` 仍零台账 |
| 🆕 **`comment` / `follow` / `task`** | [`comment/api.md`](./comment/api.md) · [`comment/rpc.md`](./comment/rpc.md) · [`comment/common.md`](./comment/common.md) · [`comment/proto.md`](./comment/proto.md)<br>[`follow/api.md`](./follow/api.md) · [`follow/rpc.md`](./follow/rpc.md) · [`follow/common.md`](./follow/common.md) · [`follow/proto.md`](./follow/proto.md)<br>[`task/rpc.md`](./task/rpc.md) · [`task/api.md`](./task/api.md) · [`task/proto.md`](./task/proto.md) · [`task/_README.md`](./task/_README.md) | **2026-10-25 并发对齐后**：三域「零台账」状态已解除，各自 4/4/3 组件全部完成首轮对照。⚠️ 合并前的旧状态见 [`_cross/new-domains-2026-10-23.md`](./_cross/new-domains-2026-10-23.md) 与 [`_cross/component-coverage-matrix.md`](./_cross/component-coverage-matrix.md) |
| **`_cross`** | [`_cross/open-items.md`](./_cross/open-items.md)<br>[`_cross/decisions.md`](./_cross/decisions.md) | 已知未修表 + 决策详述（**签字偏离已迁出** ⇒ 见 `docs/Intentional_Deviations/`） |

## 🔴 状态更正（2026-10-23 实测，与 AGENTS.md §0 冲突）

搬移时顺带做了一次真实执行核对，**推翻了 AGENTS.md §0 的两条否定式断言**：

| AGENTS.md §0 原记录 | 实测 | 证据 |
|---|---|---|
| 「`article-api`（29 个文件）+ `article-rpc`（41 个文件）**仍全是 4 行空骨架**」 | **article-api：36 文件 / 4,275 行 / 0 个骨架**<br>**article-rpc：36 文件 / 7,029 行 / 仅 2 个骨架** | `find service/article/*/src/main -name '*.java'` + `wc -l`；最新写入 **10-22 23:56**，晚于 AGENTS.md 最后编辑（23:40） |
| 「`hot-api`（13 个文件仍全是 12 行空骨架）」 | ⚠️ **已于 2026-10-23 过期**：`hot-api` 已实现（16 文件 / 2,088 行 / 59 例绿），并完成**首轮源码级语义对照**（未发现 `SEMANTIC MISMATCH`）—— 但**无双侧活体对拍、无变异验证** ⇒ 见 [`hot/api.md`](./hot/api.md) | `mvn -pl service/hot/hot-api package` BUILD SUCCESS；逐条证据强度以该台账**证据强度列**为准 |

⇒ 「article 域整体仍未完成」的**计数已过期**。但两个模块**从未做过 Go ↔ RAG 语义对照**，
所以这是「代码已落地但未验证」，**不是「已验证无漂移」**。

## 🔴 状态更正 #2（2026-10-23 后续实测）

上表最后一句**已再次过期** —— `article-rpc` 当日补做了**首轮组件级语义对照**：

| 项 | 结论 |
|---|---|
| 结论 | ❌ `SEMANTICALLY NOT EQUIVALENT`（对照时刻） |
| 差异 | 7 条 `SEMANTIC MISMATCH` + 6 条 `OBSERVABLE DIFFERENCE`（另 1 条 Flyway 账本项**已证伪**） |
| 处置 | 13 条**已改**，S-5 阻塞于 security 域迁移 |
| 证据强度 | ⚠️ **仅 S-1 做过变异验证**（把 `@Transactional(readOnly=true)` 加回去 ⇒ 真 PG `25006` ⇒ 用例转红）；S-2/S-3/S-4/S-7 的新回归网**未证实非假绿** |
| 未决 | 无双侧活体对拍（Go 依赖 `etcd:2379` 不可拉起）；MQ 事件契约未冻结 |

⇒ 「已改」**不等于**「已验证」。逐条状态以 [`article/rpc.md`](./article/rpc.md) 的**状态列**为准。
⚠️ 本轮修复由一次**只读验证任务**的子 agent 越权执行，且其最终汇总与自身报告不一致（见该台账「流程问题」节）—— 属流程问题，不计入语义漂移。

## 🟡 状态更正 #3（2026-10-23 后续实测）

上表 `hot-api` 一行同样**已过期** —— 该模块当日完成首次实现 + 首轮语义对照：

| 项 | 结论 |
|---|---|
| 代码 | **16 文件 / 2,088 行 / 59 例测试全绿**；`mvn -pl service/hot/hot-api package` BUILD SUCCESS |
| 对照 | **首轮源码级 Go ↔ RAG 对照完成**，26 项逐条列出，**未发现 `SEMANTIC MISMATCH`** |
| 结论 | ❌ **不是「已验证无漂移」** —— **无双侧活体对拍、无变异验证** |
| 🔴 方法论弱点 | 顺序是「读 Go → 写 Java → 回读 Go 复核」，**复核时已知 Java 写了什么**，与 §3.1「不要拿现有 RAG 代码当对照基准」仍有距离 |
| 未决 | D1 双侧活体对拍、D2 变异验证、D6 端口 18897 vs Go 8893 待拍板 |

⇒ **下一步该做的是双侧活体对拍，不是再加 Java 侧单测。**
逐条状态与证据强度以 [`hot/api.md`](./hot/api.md) 为准。
⚠️ hot 域三组件代码均已落地，但 `hot-api` 是其中**证据强度最弱**的一个
（另两个是双侧对拍逐字节一致）。

## 🔴 状态更正 #4 —— 覆盖度实测：两个台账**未覆盖全部微服务**（2026-10-23）

实测口径：`service/` 下 **26 个模块**逐个对照本目录 + `docs/Pitfalls/`；分母取 Go 真源 `service/` 下 **14 个服务**。

| 缺口 | 明细 | 证据 |
|---|---|---|
| **分母缺口（最大）** | ⚠️ **2026-10-23 12:25 已缩小**：Go 有 14 个服务，RAG 现覆盖 **10 个域**（`comment` / `favorite` / `follow` / `task` 已升为范围内，但**仅 proto 层**）。仍**完全未译**的只剩 **3 个**：`knowledge` / `points` / `security` ⇒ 本目录对它们只能停在「未翻译」这一句 | Go `service/*` 目录实测；RAG `service/pom.xml` 现列 10 个 `<module>` |
| **组件缺口** | 8 个组件**无专属台账文件**：6 个 `*-proto`（`article` / `hot` / `like` / `message` / `user` / `admin`）+ `like-common` + `message-common`。其中 **`article-proto` / `like-proto` / `user-proto` 在两个目录里零提及** | `grep -rl` 全目录扫描 |
| **Pitfalls 是骨架** | 27 个文件里 **21 个是 0 字节占位**（索引已声明「空文件即待填坑位」），**只有 6 份有内容** | `find docs/Pitfalls -name '*.md' -size 0c` |
| **口径陷阱** | 「有台账」≠「已对照」。见 AGENTS.md §0 各域**证据强度列**——`hot/api` 仅源码级、`article/api` 从未对照 | 本目录各 `<域>/<模块>.md` |

⚠️ `knowledge` 曾被**整个漏出**枚举起（AGENTS.md §2 与本目录 `_cross/status-snapshot-2026-10-22.md` 的「从未验证过的子模块 | 9 个」两处均无它）——
Go 侧它是**独立微服务**（含 api 组件，依赖 Neo4j + Milvus），RAG 侧仅 `Infrastructure.md` 标「❌ 未实装」。
⇒ AGENTS.md §2 的枚举行已按实测校正；`status-snapshot-2026-10-22.md` 保持「原样快照」不动，**覆盖度以本节为准**。

## 🔴 状态更正 #5 —— `@EnabledIfDockerAvailable` 让真 PG 用例**静默全跳**（2026-10-23 20:2x）

一次全量 `mvn -o -T1C test` 得到 **`BUILD SUCCESS` / `1630 tests / 0 failures`**，但里面藏着 15 条**根本没跑**的用例：

```
service/task/task-rpc/…/TaskProgressUpsertParityTest
  tests=15  skipped=15   <skipped message="Docker is not available"/>
```

**而 Docker 明明可用** —— 同一次构建里 `FavoriteSchemaParityTest`(13) / `CommentSchemaPgGateTest`(18) / `FollowRpcWiringTest`(20) 全部真起了 PG 容器、`skipped=0`。
唯一差别：只有前者加了 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`（`TaskProgressUpsertParityTest.java:69`）。

⚠️ 为什么这条特别危险：**类名里带 `Parity`（= 与 Go 对照）**，而它测的恰好是 §3.3 点名的盲区（Flyway 是否真跑、MyBatis-Plus 是否真产出 `SqlSessionFactory`），并且 **reactor 报 BUILD SUCCESS，没有任何红灯提示你少跑了它**。

⇒ **新增纪律**：凡以「真 PG 守门」为证据强度的模块，绿灯必须**额外断言 skipped 数 = 0**，「tests 全绿」本身不构成「真 PG 已验」的证据。
⚠️ 该 skip 的 `message`（`Docker is not available`）**不可信** —— 它把「Docker 真不可用」与「Ryuk 镜像拉不下来」压成同一个 `false`
（`DockerAvailableDetector` 是 `catch (Throwable) → false`）。**真实根因已定性**：`task-rpc` 是全仓唯一漏配
`RYUK_DISABLED` surefire 注入的容器模块 ⇒ 类级求值触发拉 Ryuk ⇒ `ContainerFetchException` ⇒ 整类静默 disable。
逐条、修法与元教训见 [`_cross/component-coverage-matrix.md`](./_cross/component-coverage-matrix.md)。**本条只诊断未实施。**

## ⚠️ 两处刻意未按 `<域>/<模块>` 拆分的段落

为**避免割裂叙事**，以下两段保持整体存放，用导航页指向：

1. **`users/_shared.md`（67 KB，最大文件）** —— AGENTS.md §6.2.5 / §6.2.6 以 `service/users` / `tools/difftest`
   为标题，同时覆盖 user-rpc(22) / admin-rpc(19) / admin-api(12) / user-api(10) / users-common(9)。
   拆成 4 份会把「Flyway 并发」「PasswordUtil」「DTO 矩阵」这些跨模块叙事切断。
   ⇒ `users/user/rpc.md` 与 `users/admin/rpc.md` 是**只含精确行号索引的导航页**。

2. **`_cross/message-domain-wide.md`（34 KB）** —— AGENTS.md §6.7 是 `service/message` **全域**验证，
   含 message-api 与 message-rpc 双方，单列 `<api|rpc>` 会失真。

## 保留在 AGENTS.md 的内容（phase B 不动）

| 行区间 | 内容 | 理由 |
|---|---|---|
| 1–56 | 文件头 + §0 两张总览表 | 索引本体，phase B 重写为精简版 |
| 1535–1863 | §一 项目身份与双轨策略 / §二 HUMAN_ONLY / §三 **3.1~3.3 语义对齐纪律** | 纯政策 |
| 2332–2386 | §四 重构 phase 计划 / §五 用户协作规则 / §六 标题 + **状态表纪律三条约定** | 纯政策 |
| 3055–3124 | §6.3 架构决策 / §6.4 其他子模块索引 | 决策 ≠ 漂移 |
| 4025–4094 | §七 引用清单 | 索引 |

> §六 标题下的「任何『已修 / 未修』断言都必须带核对日期，否则按 `UNKNOWN` 处理」**必须留在 AGENTS.md**——
> 它是本目录所有台账条目的书写纪律。

## 阅读顺序建议

1. 先读 AGENTS.md §3.1~§3.3（纪律）—— **这是唯一会每次进入上下文的部分**
2. 再按上表进本目录对应域
3. 判断某条断言是否可信时，看它带不带**核对日期**；不带 ⇒ 按 `UNKNOWN` 处理（§6.2 约定）
---

## `_cross/` 跨模块档案

| 文件 | 内容 |
|---|---|
| [`open-items.md`](_cross/open-items.md) | 已知未修表（§6.2）—— 任何「已修 / 未修」断言都必须带核对日期，否则按 `UNKNOWN` |
| ~~signed-deviations.md~~ | **已迁出** ⇒ [`../Intentional_Deviations/_cross/signed-deviations.md`](../Intentional_Deviations/_cross/signed-deviations.md)（N-1 / N-2 / N-4 / N-5） |
| [`decisions.md`](_cross/decisions.md) | §6.3 问题 1~3 的决策详述与被否决的方向 |
| [`go-anchors.md`](_cross/go-anchors.md) | Go 真源锚点表（判定行为时读 Go 哪个文件）—— **参考资料，非政策** |
| [`status-snapshot-2026-10-22.md`](_cross/status-snapshot-2026-10-22.md) | §0 压缩前的原文快照，**测试口径与逐域状态以本文件为准** |
| [`user-qa-verbatim.md`](_cross/user-qa-verbatim.md) | 用户问答原话（备查，防止后续被「优化」掉） |
| [`message-domain-wide.md`](_cross/message-domain-wide.md) | `service/message` 全域语义验证 |
| [`component-coverage-matrix.md`](_cross/component-coverage-matrix.md) | **组件级覆盖矩阵（38 组件逐个）** —— 谁对照过 / 证据强度到哪层 / 下一刀切哪。**要按微服务逐个做时看这份** |

## 🔴 跨域项（配置层已修，运行时验证仍缺）

**Flyway 账本撞号**（影响 `message-rpc` / `like-rpc` / `article-rpc`）——
状态行见 [`open-items.md`](_cross/open-items.md)，完整坑档见
**[`../Pitfalls/_cross/flyway-ledger-collision.md`](../Pitfalls/_cross/flyway-ledger-collision.md)**。

---

## 证据强度分层（2026-10-23 实测）

> 从 AGENTS.md §〇 搬出。**判读台账里 ✅ 的前提。**

**证据强度分层（2026-10-23 实测）**：只有 `like-api` / `hot-api` 拿到**双侧活体对拍**；
> `article-rpc` / `admin-rpc` / `hot-rpc` / `like-rpc` / `message-rpc` / `user-rpc` 拿到**真 PG 或真启动**；
> `article-api` / `admin-api` / `message-api` / `user-api` 的上限是**静态源码**。
> ⇒ 12 份台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环。

> **仍未关闭的三类**（明细台账在 DRIFT，**勿凭本表下结论**）：

