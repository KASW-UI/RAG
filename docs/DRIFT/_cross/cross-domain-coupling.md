# 跨域耦合矩阵与分波方案

> **归属**：[`docs/DRIFT/_cross/`] 收口面（跨域不变量）· **真源 = Go**（`AGENTS.md` §3.1）
> **用途**：给「多域并发对齐」提供**可复算的**波次划分依据，并规定**共享层 `service/common` 的写权与落码窗口**。
> **核对日期**：2026-10-27 · **证据强度**：**L3 静态实测**（`pom.xml` 扫描 + `@GrpcClient` 注解扫描 + yaml topic 解析 + surefire 报告解析）
> ⚠️ **效力边界**：本文的契约边是「**声明边**」（源码里写着的依赖），**不是运行时活体对拍**（未两侧同时起进程抓真实调用）⇒ L3，非 L4。
> ⚠️ **写死数字只作规模参考**，改代码后须重跑 `tools/difftest/check-wave-coupling.py` 复算。

---

## 一、实测依据

### 1.1 编译期依赖（Maven 单 reactor）

`service/pom.xml` 是**单 reactor** ⇒ 共享层改**签名** ⇒ 依赖者**编译期红**（硬防线，RAG 相对 Go 跨仓库的结构性优势）。
反查 `service/**/pom.xml` 中 `<groupId>含 rag</groupId>` 的 `<dependency>`：

| 被依赖模块 | 依赖者模块数 | 依赖者构成 |
|---|---|---|
| `service/common` | **20** | 全部 10 个域的 api + rpc |
| `service/users/users-common` | **10** | users 的 4 个 + **6 个外域 api**（article / comment / favorite / follow / like / message） |
| 其余 7 个 `<域>-common` | 2 | 仅本域 api + rpc（`hot-common` 仅 hot-api）⇒ **真私有** |

⇒ **共享判据不是「目录位置」而是「依赖度数」**：`service/common` 与各微服务**平级**、`users-common` 埋在域目录里，
但两者都是**跨域共享层**；其余 7 个 `<域>-common` 是真私有库（判据来自 §1.3 的逐类度数，不只看 `pom.xml`）。

#### 1.1.1 那 7 个 `<域>-common` 真的**零跨工作树引用**吗？—— 三层实测（2026-10-27）

| 层 | 查什么 | 结论 |
|---|---|---|
| **① 编译边** | `pom.xml` 里谁声明了 `<域>-common` | 每个都**只被本域** api/rpc 依赖（`hot-common` 只被 `hot-api`）；**7 个都不被外域声明** |
| **② import 边** | 全仓 `import` 扫描（**全限定名**，`PIT-LANE-05`） | 每个 `<域>-common` 的类**只被本域**引用 ⇒ **零外域** |
| **③ 运行期** | `@ComponentScan` / `scanBasePackages` · SPI · `AutoConfiguration.imports` | 各域只扫 `com.rag.<域>.<域>.{api,rpc}` + `com.rag.common` ⇒ **`<域>-common` 的包不在任何扫描面里**；`<域>-common` 里**无 Spring 注解**（纯 POJO / 静态工具）⇒ 不存在「顺带扫到」 |

⇒ **结论**：除 `service/common`（20 模块）与 `service/users/users-common`（10 模块 / **6 外域 api**）外，
**其余 7 个 `<域>-common` 没有任何工作树外的消费者** ⇒
**G1/G2/G3 各自改自己域的 `-common` 库，不会炸到别的域** ⇒
**「真私有」这个判据在三层上都成立**，不是只看 `pom.xml` 猜的。

⇒ ⚠️ **唯一真正横跨所有域的运行期耦合**：`service/common` 在**每个** api 的 `scanBasePackages` 里
（`{"com.rag.<域>.<域>.api", "com.rag.common"}`）⇒ **G4 改共享层会改变每个域的运行期装配**
⇒ 这正是「**G4 每次落码后必跑全量 2411 条回归**」的机制层原因（§三 C5）——
**与 G4 在不在域波顺序里无关**（G4 是常设执行者，§三 C2）。

⇒ **机器守门**：`check-wave-lane-ownership.py` 的断言 ⑥
「**7 个真私有 `<域>-common` 无跨域引用**」⇒ 将来有人从外域 import 它 ⇒ **直接转红**
（变异验证：伪造 `task-api` import `like-common` ⇒ `EXIT=1` 且点名）。

### 1.2 跨域契约依赖 DAG

⚠️ **必须区分两类边**，它们决定的东西不一样：

| 边类型 | 来源 | 决定什么 | 本文的用法 |
|---|---|---|---|
| **契约边** | gRPC（`@GrpcClient`）/ Kafka（topic） | **运行时**依赖 ⇒ **回归面**（谁必须重跑） | ✅ **分波排序按这个** |
| **编译边** | Maven 模块依赖（`users-common` 等共享库） | **编译期**耦合 ⇒ 改签名即红 | ✅ 归入 §三 共享层规则，**不进 DAG** |

⇒ 例：`like` **没有**对 `users` 的契约边（它只调 `message-rpc`），
但 `like-api` **编译期**依赖 `users-common` ⇒ 这条是**编译边** ⇒ 归 §四 按类判定，不进 DAG。

**gRPC 契约边**（实测自 `@GrpcClient` 注解目标；自指已剔除）：

| 消费方 | @GrpcClient 目标（外部） | 契约边 |
|---|---|---|
| `article` | `message-rpc` · `user-rpc` · `security-rpc`（**范围外**，§AGENTS.md §一） | → `message`, `users` |
| `comment` | `article-rpc` · `message-rpc` | → `article`, `message` |
| `favorite` | `article-rpc` · `user-rpc` | → `article`, `users` |
| `follow` | `user-rpc` | → `users` |
| `hot` | `article-rpc` | → `article` |
| `like` | `message-rpc` | → `message` |
| `message` | `admin-rpc` · `user-rpc` | → `users` |
| `task` | —（无外部 gRPC） | — |

**Kafka 契约边**（实测自 `src/main/resources/application.yml` 的 topic 常量）：

| 生产方 | topic | 消费方 | 契约边 |
|---|---|---|---|
| `like-rpc` | `article-hot-events` | `hot-rpc` | `like` → `hot` |
| `like-rpc` | `task.logs.filtered.User` | `task-rpc` | `like` → `task` |
| `like-rpc` | `task.logs.filtered.Article` | `task-rpc` | `like` → `task` |
| `like-rpc` | `like_promotion_topic` | 自产自消（**域内**） | — |
| `comment-rpc` | `comment-audit-topic` | 自产自消（**域内**） | — |

⚠️ **订正（2026-10-27）**：`like → task` 的 topic 是 **`task.logs.filtered.*`**，
**不是** `task.logs.raw.*` —— 后者是 task 域**内部**的 goka 输入/输出
（`task-rpc/src/main/resources/application.yml:151-161`：`in-topic: task.logs.raw` /
`raw-user-topic: task.logs.raw.User` / `out-user-topic: task.logs.filtered.User`）。
`filtered.*` 才是跨域契约（task-rpc 的 yml 注释明写「同时被 like-rpc 的 TaskUserPusher /
TaskArticlePusher 生产（Go likeactionlogic.go:312-343），是跨域契约，改名即断链」）。

**合并后的跨域契约 DAG**（`X → Y` = X 运行时依赖 Y）：

```
users    → ∅
follow   → users
message  → users
like     → message                    （Kafka → hot, task）
article  → message, users             （+ security，范围外）
comment  → article, message
favorite → article, users
hot      → article                    （Kafka ← like）
task     →                            （Kafka ← like）
```

### 1.3 拓扑层级（由 1.2 逐层推出）

| 层 | 域 | 说明 |
|---|---|---|
| L0 | `users` | 无出向依赖（被 `follow`/`message`/`like`/`article`/`favorite` 依赖） |
| L1 | `follow` · `message` | 只依赖 `users` |
| L2 | `like` · `article` | 依赖 L1 + L0 |
| L3 | `comment` · `favorite` · `hot` · `task` | 依赖 L2 + L1 + L0 |

✅ **订正（2026-10-27）**：把**契约边**与**编译边**（§1.2）合并后重算，**域级图是**无环 DAG****
（Tarjan 算出的强连通分量**全部是 size=1**，即**没有任何环**）⇒
**「9 个域同属一个强连通分量」这个旧断言是错的**（它来自把 javadoc 里的类名提及误当成编译依赖）。
⇒ **好消息**：域级图无环 ⇒ **按层切波存在唯一拓扑解**（§2.3 的 4 波方案）⇒ 各波之间**真的互不依赖**。
⇒ **仍需注意**：契约层无环 **不等于** 行为层无环 —— 两个域可能共享**同一份共享库**（`service/common`）
或**同一段 Go 语义**（如 `users` 的 JWT 解析）⇒ G4（§三）正是为此单独存在。

### 1.4 共享层辐射度（`service/common`，63 个类，其中 **37 个被外部域引用**）

按**跨域引用域数**（**import 判定**）降序：

| 跨域引用域数 | 类 |
|---|---|
| **9 域** | `LoggerFactoryBean` · `GoStyleProtectionProperties` |
| **8 域** | `RpcServerInterceptorFactory` · `Response` · `ObservabilityProperties` · `GoStyleRouterErrorResponses` |
| **7 域** | `SqlTimeoutStatusTranslator` · `ObservabilityLifecycle` · `GoStyleRpcProtectionChain` · `GoStyleProtectionConfiguration` · `FatalError` · `DeadlineQueryTimeoutInjector` · `BusinessException` · `AuthenticationException` |
| 6 域及以下 | 其余 23 类 |

⚠️ **订正（2026-10-27）**：早先记的「3 类被全部 10 个域引用」是**假阳性** ——
统计时用了**全文正则**，把 javadoc 里 `{@code com.rag.common.xxx.X}` 的提及当成了真引用。
**import 判定后的真实最高度数是 9 域**（`common` 自身算第 10 个）。
⇒ **结论不变但数字更准**：共享层的改动面不随分波缩小（`service/common` = 20 个模块依赖）⇒ 共享层需要**唯一常设执行者**（§三 C2）。

### 1.5 回归面规模（surefire 报告实测，2026-10-27 基线）

| 域 | 用例数 | 域 | 用例数 |
|---|---|---|---|
| `users` | 514 | `favorite` | 236 |
| `common` | 261 | `hot` | 206 |
| `article` | 261 | `like` | 195 |
| `task` | 204 | `message` | 193 |
| `comment` | 180 | `follow` | 161 |

**合计 2411**（0 失败 / 0 错误 / 4 跳过）· 全量 `mvn -B clean verify` 21:38 min。

### 1.6 🔴 真空区：编译绿 ≠ 行为对

| 共享层改动形态 | 编译期 | 行为期 | 有无守门 |
|---|---|---|---|
| 改**签名**（删方法 / 改参数 / 改返回类型） | **红**（Maven 单 reactor，20 个依赖者一起编译） | — | ✅ 编译期硬防线 |
| 改**行为**（默认值 / 常量 / 分支顺序 / 注解） | 🟢 **绿** | **可能炸** | 🔴 **无网** |

⇒ **全仓无 API 兼容门禁**（实测无 `japicmp` / `revapi` / `clirr`）⇒ 共享层行为级破坏**编译期完全无感**。
⇒ G4**必须全量回归**，不能只跑「看起来受影响」的域。

---

## 二、分波方案

### 2.1 从 DAG 推出的四条规则

| 规则 | 内容 | 依据 |
|---|---|---|
| **R1** | 同波内**无契约依赖**的域 ⇒ 可**并发**（前提：只改行为层） | §1.2 |
| **R2** | 同波内**有契约边** ⇒ 该边两端**不得并发改契约层**；收口回归按**拓扑序**（上游先） | §1.3 |
| **R3** | **共享层单写者 + 收口回归在波末** ⇒ 共享层**可随时落码**（G4 常设执行者，§三 C2）⇒ 域波只需保证**收口回归包含当前共享层状态**（C7） | §1.6 + §3 |
| **R4** | 每波收口 = **本波全量** + **组外调用方的契约回归** | §1.2 |

### 2.2 ✅ **最终方案（2026-10-27 Jason 定）= 4 棵波次工作树 G1~G4**

| 波 | 工作树 / 分支 | 域 | 组内拓扑子序（**回归序**） | 组外调用方（必回归） |
|---|---|---|---|---|
| **G1** | `RAG.worktrees/g1-users-follow` / `align/g1-users-follow` | `users`(user+admin，**不含 `users-common`**) · `follow` | `users` → `follow` | `message` · `like` · `article` · `favorite` |
| **G2** | `RAG.worktrees/g2-message-like-task` / `align/g2-message-like-task` | `message` · `like` · `task` | `message` → `like` → `task` | `article` · `comment` · `hot` |
| **G3** | `RAG.worktrees/g3-article-comment-favorite-hot` / `align/g3-article-comment-favorite-hot` | `article` · `comment` · `favorite` · `hot` | `article` → {`comment`,`favorite`,`hot`} | 域内互依 |
| **G4** | `RAG.worktrees/g4-service-common` / `align/g4-service-common` | **共享层**：`service/common/**` + `service/users/users-common/**` | 按「跨域引用域数降序」排期 | **全部 9 域 ⇒ 全量 2411 条** |

⚠️ **域波顺序**：**G1 → G2 → G3**（由契约 DAG 定序，R2）。
⚠️ **G4 = 共享层常设执行者，不在域波顺序里** —— **2026-10-27 02:23 Jason 定**：
**先于 G1 就位 standby**，**随时接收** G1/G2/G3 的反馈单 ⇒ **执行落码** ⇒ 跑**全量 2411** ⇒ 回执反馈方（§三 C2）。
⇒ **落码窗口常开**（不是「只在 G4 波激活」）；G1~G3 始终**只读共享层、只登记**。
⇒ ✅ **撤销「基线不可变 / 结论作废」**：域波**收口回归在波次末尾跑** ⇒ 天然含 G4 至今的共享层改动 ⇒
**不存在「基线作废」**，只有「**收口回归后、合并前**共享层变了 ⇒ **重跑一次收口回归**」（§三 C7）。
⚠️ **G2 的 `task` 依赖 `like`（Kafka）**、**G3 的 `hot` 依赖 `article` + `like`** ⇒ 组内按拓扑序回归。

⇒ **写权 = 一棵工作树一个面**（文件级零重叠）⇒ 机器可证：
`tools/difftest/check-wave-lane-ownership.py` ⇒ `EXIT=0`。
⇒ 旧的 **10 条 `<域>-common` lane 工作树已全部撤掉**（2026-10-27 01:44）⇒ 粒度从「一个域一棵」改成「**一个波次一棵**」。

### 2.2.1 ✅ **波次收口执行记录（2026-10-28）** —— 四波全部合入主干（**合并收口执行者**）

> **本节由合并收口执行者写**（共享面 = §1e 表里「合并收口执行者」那一行）⇒ **域侧结论不改**（各域台账仍由各域 owner 写）。
> **核对日期 2026-10-28** · 合并前主干 `c9cf2c1` → 合并后主干 **`061bfff`**。

#### ① 合入的四个提交

| 提交 | 内容 |
|---|---|
| `02259ee` | `merge(align/g1-users-follow)` —— `users`（不含 `users-common`）+ `follow` **＋ G4 共享层波次**（G4 是 G1 的祖先 ⇒ 随其进；单独 `merge align/g4-service-common` 实测 = **Already up to date**） |
| `503a978` | `merge(align/g2-message-like-task)` —— `message` + `like` + `task` |
| `a2f2c2a` | `merge(align/g3-article-comment-favorite-hot)` —— `article` + `comment` + `favorite` + `hot` |
| `061bfff` | `docs(收口)` —— **合并引入的 3 条守门回归**订正（见 ③） |

#### ② 合并前的三项预演证据（**都是实测，不是推算**）

- **冲突面**：三种**合并**顺序（`G1→G2→G3` / **`G1→G2→G3` 之后再显式合一次 `G4`** / `G2→G1→G3`）**全部零冲突**，且**最终树逐字相同**（`342951b0c85f`）
  ⇒ ⇒ **合并顺序不影响结果**
  ⇒ ⇒ ⚠️ **注意**：上面那第二种顺序**不是**域波顺序（**域波顺序里没有 G4**，见 §2.2）⇒ 它**只是**我实测过的一种**合并顺序**。
  ⇒ ⇒ 📌 **订正一条旧结论**：曾记「**G2 需与（或先于）G1/G3/G4 合入**，否则主干会拿到『新守门 + 旧 yml』」
  ⇒ ⇒ **该约束只对【单树中间态】成立，对【最终合并树】不成立** ⇒ 合并树上 `check-tomcat-limits` 实测 **EXIT=0（绿）**。
- **内容面**：`check-merge-tree-content.py`（逐步验「**每步合并结果 = 两侧改动都在**」，而不只是零冲突）**PASS**
  ⇒ 0 问题 / 0 不可信 / **无含真 NUL 的多方修改文件**（排除「零冲突但二进制揉坏」那类静默丢失）
  ⇒ 重建出的合并树与主干 HEAD 树**逐字一致**（`19542fb7b076`）。
- **守卫面**：**合并引入 3 条新红**（主干基线上这 3 条原本都是绿 ⇒ 是合并引入，不是主干原有）⇒ 已修（③）。

#### ③ 合并引入的 3 条守门回归 ⇒ 已在 `061bfff` 修完

| 守卫 | 现象 | 修法 |
|---|---|---|
| `check-wave-coupling.py` | `common_radiation` 快照 **10 项漂移** | 按**合并树实况**重算快照（**44 条**）⇒ 绿 |
| `check-ledger-count.py` | 索引称 **315**、实测 **335**（+20） | 机器生成计数订正 ⇒ 绿 |
| `check-doc-links.py` | **4 处**相对链接层级写错 | `docs/DRIFT/{like,task}/vs-common.md` 里 `message/vs-common.md` ⇒ **`../message/vs-common.md`** ⇒ 绿 |

#### ④ 收口回归（**合并后主干的第一次全量**）

- 命令：`mvn -o -B -DargLine=-Xmx2g clean test`（**全仓 52 模块** · `clean` = 口径对齐，**清掉旧 surefire 报告**）
- 结果：**`BUILD SUCCESS` · `MVN_EXIT=0`** · **42:41 min** · 13:05:50 → 13:48:33 · HEAD `061bfff`
- **权威条数**（surefire XML **逐 `testsuite` 求和**；**442 份 XML / 28 个含测试模块**）：
  **2860 条 · 0 failures · 0 errors · 4 skipped**（skip = `favorite-rpc` 3 + `favorite-api` 1）
- **共享层 `service/common` = 337** ⇒ ⚠️ **旧台账的 333 已过期**，以本轮实测为准
- **`task-rpc` = 137** ⇒ 与台账声称值**逐字相等**（`docs/DRIFT/task/rpc.md:1633`）⚠️ **2026-10-29 订正**：原文写 **134**，是旧快照 —— 守门 `python3 tools/check-ledger-selfcheck.py` 实测输出为「**台账 134 / 实测 137**」⇒ **137 为准**；同一域另有 `task/rpc.md:29` 与 `task/vs-common.md:1433` 也写 134 ⇒ **三处 134 与一处 137 矛盾**（**该红门的根因就是这个数字漂移** ⇒ 已派 `P4-T04` 域侧订正）
  ⇒ ⇒ 📌 **订正一条踩坑结论**：合并后 `check-ledger-selfcheck.py` 一度报「台账 134 / **实测 114**」
  ⇒ ⇒ **根因 = 主工作树里 `service/task/*/target/surefire-reports` 是 10-26 的 stale 报告**（`mvn test` 读不到新报告 ⇒ 读到旧的）
  ⇒ ⇒ **不是台账错、也不是回归** ⇒ **`clean` 跑完后该守门即转绿**（实测 ✅「台账 134 / 实测 134」）。

#### ⑤ 新基线

- 四条 lane 分支已 `--ff-only` 推进到 **`061bfff`** ⇒ **只推进指针，不重写历史、不产生新提交**
- lane 原尖端 `173a591` / `18c6a36` / `51afbe8` / `299a9fd` **仍是主干祖先** ⇒ 一笔历史都没丢
- 四棵工作树与主干的**文件差异 = 0** ⇒ **基线 = 合并后主干** ⇒ 下一轮按 §1d「主干再前进就 `merge do-my-own-gh` 一次」继续。

#### ⑥ 剩余红：**1 条，且是主干原有**

- `check-ddl-static.py` ⇒ 与**合并前**主干基线**输出逐字相同** ⇒ **合并未引入任何新 DDL 漂移**
  （users 域 **9 处** = 1 处可空性 `users_user.status` + 8 处 `DEFAULT CURRENT_TIMESTAMP` 残留 ⇒ **已登记项**，非本波引入）。

### 2.3 备选（**未采纳**）：按层级拆 5 波

若日后要压总工期，可按 §1.3 的层级再切（W1 `users` / W2 `follow`+`message` / W3 `like`+`article` /
W4 `task`+`comment`+`favorite`+`hot` / W5 共享层）⇒ **并发峰值 4**、**W4 外溢 0**，代价是**波次多一轮**。
⇒ **两方案共享同一套 §三 共享层规则**；**当前按 G1~G4 执行**。

### 2.4 ⚠️ 一波收口 ≠ 「我这波绿了」

契约层虽无环（§1.3），但**共享层与共享语义**（`service/common` 37 个被外部引用的类、
`users-common` 的 8 个跨域类、Go 侧同一段 JWT/错误码语义）**横跨所有波** ⇒
**一波收口的判定标准不是「我这波绿了」，而是「我这波 + 我的组外调用方一起绿」**。
单跑本域测试拿到 ✅ **不构成收口证据**。

---

## 三、共享层 `service/common` 的处置规则

### 3.1 为什么「三组都有权改 common」不可行

| 理由 | 实测依据 |
|---|---|
| **无裁决者** | `service/common` 63 类里 **2 类被 9 个域引用**、4 类跨 8 域、8 类跨 7 域（§1.4）⇒ 同一类的语义由三种理解分别修改 ⇒ 必然出现「谁都对但合起来不对」 |
| **基线互毁** | 三组并发改 common ⇒ 组 A 的测试跑的是**旧 common**，组 B 跑的是**新 common** ⇒ 绿的可能是**过期基线** |
| **破坏无网** | 行为级改动编译绿、无 API 兼容门禁（§1.6）⇒ 组 B 拿到的是「编译通过但行为已变」的 common ⇒ 回归结论不可信 |
| **历史佐证** | 11 处历史回归**全部**是「无出处 idiomatization」—— 同一处语义被多人按各自理解改写（`AGENTS.md` §3.2 / §1.1） |

⇒ **结论：共享层必须有唯一裁决者 ⇒ 单独一个会话 + 单独一棵常设工作树（G4）。**

### 3.2 规则

| # | 规则 | 落地 |
|---|---|---|
| **C1** | **共享层写权唯一**：`service/common/**` + `service/users/users-common/**` ⇒ 只有 **G4** 能写 | **`align/g4-service-common`**；G1~G3 **只读** |
| **C2** | **G4 = 常设执行者**：**随时接收** G1/G2/G3 的反馈单 ⇒ **执行落码** ⇒ **跑全量 2411** ⇒ **回执**反馈方；**落码窗口常开**、**不在域波顺序里**（先于 G1 就位 standby） | G4 工作日志 + [`docs/DRIFT/global/vs-common.md`](../global/vs-common.md) |
| **C3** | **域波遇到共享层缺陷** ⇒ **不甩单、不留半截** ⇒ 登记**反馈单**（§3.3），域侧**能自修的自修**（调用方式 / 配置 / 断言 / 装配） | `docs/DRIFT/<域>/vs-common.md` 的「需转派」段 ⇒ 转派 **G4** |
| **C4** | **例外升级**：某波卡在「不改共享层就过不了」 ⇒ **该波就地升级为全量回归波**（跑全部 2411 条），等 **G4 落码后**再重跑 | 波次记录里写明升级原因与重跑结果 |
| **C5** | **G4 内部排期**：按「跨域引用域数降序」（§1.4）⇒ 先动 9 域类、再 8 域类 ⇒ **每次改动后立刻跑全量** | G4 的工作日志 |
| **C6** | **物理位置不变**：提级的是**写权与归属**，**不是目录搬迁** ⇒ `users-common` 的类**留在原目录**（避免大范围 import 改动 ⇒ 大爆炸半径） | 目录保持 `service/users/users-common/`（**G4 独占写权**） |
| **C7** | 🆕 **收口回归在波次末尾跑** ⇒ 天然含 G4 至今的共享层改动 ⇒ **不存在「基线作废」**；**唯一风险窗口 = 收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归** | 域波记录写明**收口回归对应的 commit**（`git rev-parse HEAD`） |

#### 3.2.1 🆕 为什么「基线不可变 / 结论作废」是错的（2026-10-27 02:23 订正）

**旧模型**：G4 必须最后 ⇒ G1~G3 期间共享层只读 ⇒ 期间改了共享层 ⇒ **那波结论全部作废**。

**问题**：它把「**域波中途**改共享层」与「**域波收口回归之后**改共享层」当成同一件事 —— 而两者的处置完全不同：

| 共享层改动发生在 | 对域波收口结论的影响 | 处置 |
|---|---|---|
| 域波**中途**（收口回归之前） | **无影响** —— 收口回归在末尾跑，天然含 G4 至今的改动 | **什么都不用做** |
| 域波**收口回归之后、合并之前** | 结论只对**旧共享层**成立 | **重跑一次收口回归**（不是「作废」） |

⇒ 且共享层的变动是**单向传导**（**共享层 → 域**，域改不动共享层）：
**G4 每次落码后必跑全量 2411**（C5）⇒ **任何共享层改动都会在全量回归里被验一遍** ⇒ G4 落码后主干恒绿。
⇒ **结论**：域波侧只需守住「**收口回归在末尾跑**」＋「**收口回归后～合并前共享层变了就重跑**」两条（C7），
**不需要**「基线不可变」这种全局冻结 ⇒ **G4 可与 G1~G3 并行**，**不阻塞流水线**。

⚠️ **代价对比**（实测口径）：

| 形态 | 共享层改动可用时机 | 域波需重跑次数 | 流水线阻塞 |
|---|---|---|---|
| **旧模型**（G4 最后） | G3 收口之后 | 期间任何一次改动 ⇒ **整波重跑** | G4 排在队尾 ⇒ 反馈单要等 |
| ✅ **新模型**（G4 常设） | **随时** | 收口回归**前** ⇒ 0 次；**后** ⇒ 1 次（只重跑收口回归） | **无**（G4 与域波并行） |

### 3.3 反馈单格式（域波 → G4，必填 7 项）

缺任一项 ⇒ **不得进入G4排期**（防止「甩单变成半截」）。

| # | 字段 | 要求 |
|---|---|---|
| 1 | **现象** | 具体的失败表现（哪个域 / 哪个接口 / 什么错误） |
| 2 | **Go 侧出处** | `文件:行号`（Go 真源路径 + 行号）—— **必须存在**，否则先自行核实 |
| 3 | **RAG 侧现状** | `文件:行号`（RAG 当前实现） |
| 4 | **证据强度** | L1（源码对照）/ L3（静态实测）/ L4（运行时双侧活体）—— **不得写成 L4 除非真有两侧同时跑的探针** |
| 5 | **建议修法** | 期望的语义（不是「把 RAG 改成 X」而是「两侧都应是 Y」） |
| 6 | **影响面** | 受影响的域/模块清单（可由 `tools/difftest/check-wave-coupling.py` 的度数数据生成） |
| 7 | **是否阻塞本波** | 是 ⇒ 触发 **C4** 例外升级；否 ⇒ 进G4排期 |

### 3.4 共享层落码与 9 条机制面轴对账的关系

**9 条机制面轴（A1~A9）的逐域对账 = 阶段 1（只读）**：在 **G1~G3** 三棵工作树里做
（各树负责自己那几个域的 `vs-common.md`）⇒ 产出**逐轴结论 + 反馈单清单** ⇒ **不落任何代码**。

**阶段 2（落码）= 两路并行**：
- **域侧**：**G1 → G2 → G3**（契约 DAG 定序）⇒ 各域落**自己域内**的代码 + 域侧自修；
- **共享层**：**G4 常设执行者，随时落**（C2）⇒ 消费 G1~G3 的反馈单 ⇒ 跑全量 ⇒ 回执。

⇒ ⚠️ **阶段 1 若落域侧代码，会与阶段 2 同域撞文件** ⇒ **阶段 1 必须全只读**。
⇒ ✅ **阶段 2 两路不会撞**：写权**文件级零重叠**（`check-wave-lane-ownership.py` 断言 ①）
—— 域侧写 `service/<域>/**`，G4 写 `service/common/**` + `service/users/users-common/**`。

⚠️ **原 10 条 `<域>-common` lane 工作树已于 2026-10-27 01:44 全部撤掉**
⇒ 它们的**方法与台账仍然有效**（9 轴表 + `docs/DRIFT/<域>/vs-common.md`），只是**不再一域一棵**。

---

## 四、`users-common` 的归属：**按类判定**，不按模块一刀切

`users-common` = 11 个类。实测逐类跨域引用（**import 判定**，⚠️ 见下方口径警告）：

| 类 | FQN | 引用域数 | 涉及域 | 归属 |
|---|---|---|---|---|
| `BearerTokenExtractor` | `…users.common.jwt.` | **7** | article, comment, favorite, follow, like, message, users | 🔴 **共享层** |
| `JwtUtil` | `…users.common.jwt.` | **7** | 同上 | 🔴 **共享层** |
| `GoRequiredFieldException` | `…users.common.http.` | **7** | 同上 | 🔴 **共享层** |
| `GoStyleCoercion` | `…users.common.http.` | **7** | 同上 | 🔴 **共享层** |
| `GoStyleRequired` | `…users.common.http.` | **7** | 同上 | 🔴 **共享层** |
| `GoStyleRequiredFields` | `…users.common.http.` | **7** | 同上 | 🔴 **共享层** |
| `GoStyleRequest` | `…users.common.http.` | **5** | article, follow, like, message, users | 🔴 **共享层** |
| `GoStyleStringInt64` | `…users.common.http.` | **3** | favorite, follow, like | 🔴 **共享层**（度数较低，但跨 3 域） |
| `ErrorCode` | `…users.common.errmsg.` | **1** | 仅 users | ⚪ **users 专用**（留 users 域） |
| `PasswordUtil` | `…users.common.cryptx.` | **1** | 仅 users | ⚪ **users 专用**（留 users 域） |
| `SchemaInitializer` | `…users.common.ddl.` | **1** | 仅 users | ⚪ **users 专用**（留 users 域） |

⇒ **结论**：**8 个类提级到G4**（写权归G4），**3 个类留在 users 域**。
⇒ **不拆则组 G1/W1 能炸 6 个外域**（`users-common` 被 6 个外域 api 依赖 ⇒ 改签名即 6 域编译红）；
⇒ **一刀切提级则误伤**（`ErrorCode` / `PasswordUtil` / `SchemaInitializer` 是 users 专用，提级只会平白扩大共享层写权面）。
⇒ **物理位置不动**（C6），只改**写权归属**。

⚠️ **统计口径警告（两个坑，都会造出假阳性）**：
1. **必须用全限定名**（如 `com.rag.users.common.jwt.JwtUtil`）——
   裸类名会撞各域同名类（`ErrorCode` / `SchemaInitializer` / `Response`）⇒ 假阳性。
2. 🔴 **必须用 `import` 语句判定，不能全文正则**（2026-10-27 实测踩到）——
   全文匹配会把 **javadoc 里的 `{@code com.rag.users.common.errmsg.ErrorCode}`** 当成真引用
   ⇒ `ErrorCode` 被误判成「common + users 共用」⇒ 实际它**只被 users 域引用**
   （`service/common` 只是注释里提到它）⇒ 误判会把 users 专用类**错误地提级**到共享层。
   ⇒ **本仓已因此订正过一次**（早期记的「9 个类真共享 + `ErrorCode` 半共享」= 错，实为 8 + 3）。

---

## 五、已识别的缺口（须登记进台账）

| # | 缺口 | 影响 | 建议 |
|---|---|---|---|
| **GAP-1** | **全仓无 API 兼容门禁**（无 `japicmp`/`revapi`/`clirr`） | 共享层行为级破坏**编译绿、无网**（§1.6） | 在 **G4** 引入 API 兼容检查（基线 = **G4 上一次落码前**的 `common` jar） |
| **GAP-2** | **`users-common` 未按类提级** | `users-common` 目前是「共享层与 users 私有混装」；写权归属不明 ⇒ **G1** 改 2 个私有类也可能炸 6 外域 | 按 §四 落实：**8 类归 G4、3 类留 users**（实测口径，§四） |
| **GAP-3** | **契约边只有 L3 静态证据** | DAG 是「声明边」，未两侧活体对拍 ⇒ 可能漏掉**运行期才出现**的依赖（如反射 / 配置注入的 client） | **G4** 补一次 L4 探针（两侧同时起进程，抓真实调用） |

---

## 六、可校验形式

### 6.1 机器可读快照（守门比对对象）

<!-- wave-coupling-snapshot:begin -->
```json
{
  "common_radiation": {
    "AuthenticationException": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "AuthorityHandler": [
      "comment"
    ],
    "BusinessException": [
      "article",
      "comment",
      "favorite",
      "follow",
      "hot",
      "message",
      "users"
    ],
    "DeadlineQueryTimeoutInjector": [
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "task",
      "users"
    ],
    "Dispatcher": [
      "comment",
      "favorite",
      "like"
    ],
    "FatalError": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleBreakerFilter": [
      "article",
      "favorite",
      "message",
      "task"
    ],
    "GoStyleCircuitOpenException": [
      "article",
      "comment",
      "follow",
      "message",
      "task",
      "users"
    ],
    "GoStyleCpuSheddingFilter": [
      "article",
      "favorite",
      "message",
      "task"
    ],
    "GoStyleFormLimitMode": [
      "message",
      "task"
    ],
    "GoStyleFormValueLimitInterceptor": [
      "message",
      "task"
    ],
    "GoStyleFormValues": [
      "article",
      "favorite"
    ],
    "GoStyleGrpcClientBreakerInterceptor": [
      "article",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "users"
    ],
    "GoStyleJsonBodyConverter": [
      "article",
      "hot"
    ],
    "GoStyleJsonBodyConverterConfig": [
      "article",
      "task"
    ],
    "GoStyleMaxBytesFilter": [
      "article",
      "favorite",
      "message",
      "task"
    ],
    "GoStyleMaxConnsFilter": [
      "article",
      "favorite",
      "message",
      "task"
    ],
    "GoStylePathNormalizingFilter": [
      "like",
      "message",
      "task"
    ],
    "GoStyleProtectionConfiguration": [
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "task",
      "users"
    ],
    "GoStyleProtectionProperties": [
      "article",
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task",
      "users"
    ],
    "GoStyleRecoverInterceptor": [
      "message"
    ],
    "GoStyleRedisClientAutoConfiguration": [
      "users"
    ],
    "GoStyleRequestDeadline": [
      "article",
      "comment",
      "favorite",
      "hot",
      "like"
    ],
    "GoStyleRouterErrorResponses": [
      "article",
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task"
    ],
    "GoStyleRpcBreakerInterceptor": [
      "message",
      "task"
    ],
    "GoStyleRpcProtectionChain": [
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task",
      "users"
    ],
    "GoStyleRpcSheddingInterceptor": [
      "message",
      "task"
    ],
    "HttpMiddlewareFilter": [
      "article",
      "comment",
      "message",
      "task"
    ],
    "LogOption": [
      "favorite",
      "follow"
    ],
    "LoggerFactoryBean": [
      "article",
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task",
      "users"
    ],
    "MqConsumer": [
      "article",
      "comment",
      "hot",
      "like",
      "task"
    ],
    "ObservabilityLifecycle": [
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "task",
      "users"
    ],
    "ObservabilityProperties": [
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task",
      "users"
    ],
    "ProcessLogger": [
      "favorite"
    ],
    "Response": [
      "article",
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "users"
    ],
    "RpcInterceptor": [
      "like"
    ],
    "RpcServerInterceptorFactory": [
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "message",
      "task",
      "users"
    ],
    "Snowflake": [
      "like",
      "message",
      "users"
    ],
    "SnowflakeBean": [
      "article",
      "comment",
      "favorite",
      "like",
      "message",
      "users"
    ],
    "SnowflakeProperties": [
      "comment",
      "favorite",
      "like",
      "message",
      "users"
    ],
    "SqlTimeoutStatusTranslator": [
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "task",
      "users"
    ],
    "Wire": [
      "comment",
      "favorite",
      "like"
    ],
    "WithArticleID": [
      "favorite",
      "hot"
    ],
    "WithUserID": [
      "article",
      "favorite",
      "follow"
    ]
  },
  "cross_domain_topics": {
    "article-hot-events": [
      "hot",
      "like"
    ],
    "task.logs.filtered.Article": [
      "like",
      "task"
    ],
    "task.logs.filtered.User": [
      "like",
      "task"
    ]
  },
  "grpc_edges": {
    "article": [
      "message",
      "security",
      "users"
    ],
    "comment": [
      "article",
      "message"
    ],
    "favorite": [
      "article",
      "users"
    ],
    "follow": [
      "users"
    ],
    "hot": [
      "article"
    ],
    "like": [
      "message"
    ],
    "message": [
      "users"
    ]
  },
  "pom_dependents": {
    "article-common": 2,
    "comment-common": 2,
    "common": 20,
    "favorite-common": 2,
    "follow-common": 2,
    "hot-common": 1,
    "like-common": 2,
    "message-common": 2,
    "users-common": 10
  },
  "users_common_classes": {
    "BearerTokenExtractor": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "ErrorCode": [
      "users"
    ],
    "GoRequiredFieldException": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleCoercion": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleRequest": [
      "article",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleRequired": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleRequiredFields": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "GoStyleStringInt64": [
      "favorite",
      "follow",
      "like"
    ],
    "JwtUtil": [
      "article",
      "comment",
      "favorite",
      "follow",
      "like",
      "message",
      "users"
    ],
    "PasswordUtil": [
      "users"
    ],
    "SchemaInitializer": [
      "users"
    ]
  }
}
```
<!-- wave-coupling-snapshot:end -->


```bash
python3 tools/difftest/check-wave-coupling.py        # ⇒ EXIT=0；重算 DAG + 度数，与本文快照比对
python3 tools/difftest/check-wave-coupling.py --print # 打印重算结果（用于更新本文快照）
python3 tools/difftest/check-wave-lane-ownership.py  # ⇒ EXIT=0；G1~G4 写权两两不重叠 + 排他归属 + 路径落空 + 分支存在
python3 tools/difftest/check-wave-role-model.py    # ⇒ EXIT=0；G4 角色模型 = 常设执行者（退休措辞未回流）
python3 tools/difftest/check-wave-role-model.py --mutation-test  # ⇒ 7/7 变异全红
```

守门逻辑：从仓库**重算**（① `pom.xml` 依赖度数 ② `@GrpcClient` 边 ③ yaml 跨域 topic ④ `users-common` 逐类跨域引用 ⑤ `service/common` 逐类辐射度），
与本文记录的快照**逐项比对** ⇒ **有人新增/删除一条契约边、或改了一个共享类的归属，却没更新本文 ⇒ EXIT=1**。

⚠️ **surefire 用例数只作参考输出，不参与判定** —— 它依赖 `target/` 是否已构建 ⇒ 进判定会在干净检出上恒红（同源「恒红的守门等于没有守门」）。

⚠️ **本守门已做两次变异验证**（2026-10-27）：① 注入一条新的 `@GrpcClient` 边（favorite → hot-rpc）⇒ `EXIT=1` 且精确点名 `favorite`；
② 改一个跨域 topic 名（`article-hot-events` → `article-hot-events-v2`）⇒ `EXIT=1` 且点名该 topic **从矩阵中消失**。
