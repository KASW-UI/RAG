<!-- @move /tmp/AGENTS.md.phaseB.bak L1-L56 · AGENTS.md §0 原状态速查（2026-10-22 全文） -->

# §0 原状态速查（2026-10-22 全文存档）

> AGENTS.md 的 §0 已在 2026-10-23 **压缩重写为纯索引**。
> 此处保留压缩前的原文，**证据、测试口径、逐域状态一律以本文件为准**。
> ⚠️ 其中「article 域整体仍未完成」等结论已被 2026-10-23 的实测推翻，见各域台账。

---

# AGENTS.md: RAG 项目政策与当前重构状态

> 本文件是 RAG 项目唯一由每个 agent 自动加载的根政策文件。
> `RAG/.agents/` 下的文件是任务指南，不能削弱本文档里的规则。
> **2026-10-18 重写**：原文件是从 vLLM 模板继承的、含大量与本项目无关的内容（GGUF、ModelRegistry、FusedChain 等）。本次重写只保留 RAG 实际需要的政策与本次重构的当前状态。
> **2026-10-20 第三次真理之源切换**：真源退回 **Go**；非 Spring Boot 的 Java 实现（`Sea-RideTheWind_java` 等）从策略中移除，不在 RAG 工作范围内；并新增 §3 语义对齐纪律。起因是 `service/users` Go ↔ RAG 对照发现 **11 处行为回归**，全部集中在「换成更地道的 Spring 写法」的位置。详见 §1.1。

## 〇、当前状态速查（2026-10-22）

> **这一节回答两个问题**：「`service/users` 现在还有漂移吗」与「下一个 agent 该从哪下手」。
> 详细留档在 §3.4 / §6.2.6，此处只给结论与边界。

**已关闭**（全部有运行证据，不是静态推断）：

| 项 | 结论 | 证据 |
|---|---|---|
| **E-3**（DTO 字段边界） | **R1~R5 五个根因全部已修** | 矩阵 273 条 `NEW_DRIFT 77 → 0`；MATCH 121 → 130 |
| **E-1**（响应未比对） | **10 步「只比状态码」已升级为按边界断言** | `steps=39 asserted=10 byte_equal=29 unexpected=0` |
| **DRIFT-024**（E-1 附带挖出） | **RAG 所有 text/plain 400 丢 `Content-Type`/`nosniff` 已修** | 真 Tomcat 守门脚本红绿双向实测（修复前 `EXIT=1` → 修复后 `EXIT=0`） |
| E-2 / E-4 / E-5 / E-6 / D-1 | 已关 | 见 §6.2.6 |
| **DRIFT-LIKE-001**（like-rpc 列表 DB 回源恒空） | **已签字：Java 现状即为「已修正的 Go bug」，两侧不一致属有意，不再对齐** | 真 PG + gorm v1.31.1 实测 `schema.LookUpField("created_at")` 返回 `nil`、插入真实行后 `Scan` 得 `CreateTime=0`（期望 `1792670400`） |

全仓 `mvn -o clean test`（**2026-10-22 19:02 实测**）：**619 tests, 0 failures, 0 errors, 0 skipped**（全模块 BUILD SUCCESS）。
> ⚠️ **该数字只对 message 域有效（2026-10-22 19:26 补注）**：此后本机有**另一个会话在并行跑 Maven**
> （实测 6 个 maven/java 进程），持续改写 `service/like/**` 并重建共享的 `target/` 与
> `~/.m2` 里的 SNAPSHOT jar。在这种状态下**全仓口径不可复现**：
> `like-rpc` 编译失败、`message-api` 的 `MessageApiMvcPipelineTest` 41 条报
> `ClassNotFoundException: com.rag.users.common.http.GoRequiredFieldException`
> —— 而该类在 `users-common/target/classes` 与已安装的 jar 里**都存在**。
> ⇒ **并发构建期间不要采信全仓数字**；单模块隔离跑才有效。
> 口径说明：由各模块 `target/surefire-reports` 汇总（`common 102 / message 132 / users 357 / like 18 / article 10`）。
> `message 132` = `message-rpc 32` + **`message-api 100`**（41 MVC 管线 + 18 纯单测 + **41 保护层**）。
> `message-rpc 32` = 原有 21 + **§6.7.7 新增 11**（真 PG 6 + trim 5）。
> ⚠️ 上一版口径（580 / message 121 / rpc 21）已过期，见下方 ⚠️ 关于「测试数不是质量指标」的告诫。
> 其中保护层 41 例含 **3 例真 Tomcat**（`RANDOM_PORT` + JDK HttpClient 读原始响应头）——
> 响应头这层 MockMvc 测不到（DRIFT-024 同款），必须真容器；
> 另含 **2 例 starter 接线断言**（`GoStyleGrpcClientBreakerWiringTest`）——
> 断言「拦截器进了 starter 的 registry」，不是「拦截器存在」（§3.3 同款陷阱）；
> 另含 **1 例真 in-process gRPC 端到端**（`GoStyleGrpcClientBreakerE2ETest`）——
> 断言响应码 **5001 → 1015**，是唯一能把「拦截器在链上 + 异常有人接 + 码映射对」三件事连起来证的用例。
> ⚠️ **测试数不是质量指标**：本次 4 条漂移里有 3 条都曾被绿测试锁死（见下方 message-api 段），
> 且 DRIFT-MSGAPI-004 的修复中先后出现过「8 条单测全绿但功能完全没生效」
> 与「判据单测全绿但 `bizCodeFromError` 根本不会被调用」两种假绿，
> 真正锚定真源的是 `tools/semantic-probe` 的 23 例双侧对拍，不是测试条数。

**⚠️ 但「无漂移」这句话有明确边界**，不得越读：

| 仍然存在的 | 数量 | 性质 |
|---|---|---|
| **已签字的有意偏离** | 6+ 条 | N-1 / N-2 / N-4 / N-5 + §3.4 的 path/method/表名。**是决策，不是缺陷**，但确实不一致 |
| **DDL 已知未处理** | 12 处列类型 | 10 处 `timestamptz→timestamp` + 2 处 `text→jsonb`；**索引 / 唯一约束 / NOT NULL / DEFAULT 完全 NOT CHECKED**。⚠️ 2026-10-22 18:35 已对其中 message 域的时间列**首次做出可观察影响评估**（DRIFT-MSG-V-007：JVM 非 UTC 时全部时间戳偏移一个时区差），其余 10 处模块的列类型差异**仍未评估过影响** |
| **从未验证过的子模块** | **9 个** | `article / comment / favorite / follow / hot / like / points / security / task / common` —— **从未做过 Go ↔ RAG 对照**，也连 `HUMAN_ONLY.MD` 都没补。⚠️ **`hot` 一项已收口 2/3**：① `hot-common`（Go `service/hot/common`，hot 域唯一的非 api/rpc 组件，1 个 `errmsg.go`）—— 11 个码值 + 11 条文案 + 未知码回退，双侧输出 **sha256 逐字节一致**，14 条契约测试，7 项变异全部变红；② `hot-rpc`（2026-10-22 晚，Go `rpc` + `heavykeeper`）—— 由 13 个 12 行空骨架**首次实现**，`HeavyKeeper` **双侧对拍逐字节一致**，**46 条契约测试**，**10 项变异全部变红** ⇒ **`hot-common` / `hot-rpc` 均不再属于「从未验证」**；⚠️ 但 `hot-api`（13 个文件仍全是 12 行空骨架）未动 ⇒ **hot 域整体仍未完成**，计数由 10 降为 **9**。⚠️ **`article` 一项已收口 1/3**：`article-common`（Go `service/article/common`，1 个 `errmsg.go`，15 个码）—— 双侧对拍 **sha256 逐字节一致**（49 行 dump）、**15 条契约测试**、**8 项变异双层全红**，但 `article-api`（29 个文件）+ `article-rpc`（41 个文件）**仍全是 4 行空骨架** ⇒ **article 域整体仍未完成**，计数不变 |
| **已验证但文档缺失** | 3 个 | `users-common` / `message-rpc` / `message-api` —— Go ↔ RAG 对照已做，但 `HUMAN_ONLY.MD` **实际不存在**（§0/§7.2 曾误列为已存在，2026-10-22 已按 glob 实测校正，详见 §2） |

**⇒ 准确表述是：「`service/users` 的 DTO 字段边界层已无漂移」，不是「整个 `service/users` 无漂移」。**
