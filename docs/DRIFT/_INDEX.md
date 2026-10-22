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
| **`common`** | [`common/protection.md`](./common/protection.md) · [`common/http.md`](./common/http.md)<br>[`common/deadline.md`](./common/deadline.md) · [`common/audit.md`](./common/audit.md)<br>[`common/_signed-deviations.md`](./common/_signed-deviations.md) | 请求保护层四层的最终落点 |
| **`hot`** | [`hot/rpc.md`](./hot/rpc.md) · [`hot/common.md`](./hot/common.md)<br>[`hot/api.md`](./hot/api.md) | 前两者双侧对拍逐字节一致；**api 已实现 + 源码级对照完成，但无双侧活体对拍** |
| **`article`** | [`article/common.md`](./article/common.md)<br>[`article/api.md`](./article/api.md) · [`article/rpc.md`](./article/rpc.md) | common 已对拍；**rpc 已完成首轮组件级对照（2026-10-23，见下方「状态更正」）**，api 未对照 |
| **`_cross`** | [`_cross/signed-deviations.md`](./_cross/signed-deviations.md)<br>[`_cross/open-items.md`](./_cross/open-items.md) | 跨模块的签字偏离 + 已知未修表 |

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
| [`signed-deviations.md`](_cross/signed-deviations.md) | 已签字的有意偏离（N-1 / N-2 / N-4 / N-5） |
| [`decisions.md`](_cross/decisions.md) | §6.3 问题 1~3 的决策详述与被否决的方向 |
| [`go-anchors.md`](_cross/go-anchors.md) | Go 真源锚点表（判定行为时读 Go 哪个文件）—— **参考资料，非政策** |
| [`status-snapshot-2026-10-22.md`](_cross/status-snapshot-2026-10-22.md) | §0 压缩前的原文快照，**测试口径与逐域状态以本文件为准** |
| [`user-qa-verbatim.md`](_cross/user-qa-verbatim.md) | 用户问答原话（备查，防止后续被「优化」掉） |
| [`message-domain-wide.md`](_cross/message-domain-wide.md) | `service/message` 全域语义验证 |

## 🔴 跨域未修复项

**Flyway 账本撞号**（影响 `message-rpc` / `like-rpc` / `article-rpc`）——
状态行见 [`open-items.md`](_cross/open-items.md)，完整坑档见
**[`../Pitfalls/_cross/flyway-ledger-collision.md`](../Pitfalls/_cross/flyway-ledger-collision.md)**。
