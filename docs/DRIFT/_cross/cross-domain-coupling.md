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
或**同一段 Go 语义**（如 `users` 的 JWT 解析）⇒ 共享层波（§三）正是为此单独存在。

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
⇒ **结论不变但数字更准**：共享层的改动面不随分波缩小（`service/common` = 20 个模块依赖）⇒ 共享层应**单独成波**（§三）。

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
⇒ 共享层波**必须全量回归**，不能只跑「看起来受影响」的域。

---

## 二、分波方案

### 2.1 从 DAG 推出的四条规则

| 规则 | 内容 | 依据 |
|---|---|---|
| **R1** | 同波内**无契约依赖**的域 ⇒ 可**并发**（前提：只改行为层） | §1.2 |
| **R2** | 同波内**有契约边** ⇒ 该边两端**不得并发改契约层**；收口回归按**拓扑序**（上游先） | §1.3 |
| **R3** | **共享层改动单独成波** —— 域波进行中改共享层 ⇒ 域波行为基线变 ⇒ **域波测试结论作废** | §1.6 + §3 |
| **R4** | 每波收口 = **本波全量** + **组外调用方的契约回归** | §1.2 |

### 2.2 主方案（2026-10-27 Jason 定：3 组 + 1 共享层波）

| 波 | 域 | 组内拓扑子序（**回归序**） | 并发上限 | 组外调用方（必回归） |
|---|---|---|---|---|
| **G1** | `users`(user+admin) · `follow` | `users` → `follow` | 1（行为层可 2） | `message` · `like` · `article` · `favorite` |
| **G2** | `message` · `like` · `task` | `message` → `like` → `task` | 1 | `article` · `comment` · `hot` |
| **G3** | `article` · `comment` · `favorite` · `hot` | `article` → {`comment`,`favorite`,`hot`} | 3 | `favorite` · `comment` · `hot`（域内互依） |
| **G4** | **共享层波**：`service/common/**` + `users-common` 提级类（§四） | 按「跨域引用域数降序」排期 | 1（单写权） | **全部 9 域 ⇒ 全量 2411 条** |

⚠️ **G2 的 `task` 依赖 `like`（Kafka）**、**G3 的 `hot` 依赖 `article` + `like`** ⇒
**跨组顺序不可乱**：G1 → G2 → G3 → G4。**G4 必须在 G3 之后**（否则 G1~G3 的结论全部作废，R3）。

### 2.3 备选：按层级拆 4 域波（并发峰值更高）

若要压总工期，按 §1.3 的层级重切：

| 波 | 域 | 并发 | 组外调用方 |
|---|---|---|---|
| W1 | `users` | 1（user 轨 + admin 轨 = 2） | **5** |
| W2 | `follow` · `message` | 2 | 3 |
| W3 | `like` · `article` | 2 | 4 |
| W4 | `task` · `comment` · `favorite` · `hot` | **4** | **0**（外溢为零） |
| W5 | 共享层（`service/common` + `users-common` 提级类） | 1 | 全量 2411 |

**取舍**：4 波方案 **并发峰值 4**（3 波方案 3）、**W4 外溢 0**（3 波方案 G3 仍有域内互依），
代价是**波数多一轮**。**两方案共享同一套 §三 共享层规则**，可按工期压力二选一。

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

⇒ **结论：共享层必须有唯一裁决者 ⇒ 单独一个会话 + 单独一个波次（G4/W5）。**

### 3.2 规则

| # | 规则 | 落地 |
|---|---|---|
| **C1** | **共享层写权唯一**：`service/common/**` + `users-common` 提级类 ⇒ 只有共享层波的工作树能写 | `align/global-common`（分支保留，**角色改为「共享层波」**）；其余域 lane **只读** |
| **C2** | **落码窗口**：共享层**只在 G4/W5 波激活** ⇒ 域波期间 common **只读、只登记** | 域波期间改 common ⇒ 该波结论作废（R3） |
| **C3** | **域波遇到 common 缺陷** ⇒ **不甩单、不留半截** ⇒ 登记**反馈单**（§3.3），域侧**能自修的自修**（调用方式 / 配置 / 断言 / 装配） | `docs/DRIFT/<域>/vs-common.md` 的「需转派」段 |
| **C4** | **例外升级**：某波卡在「不改 common 就过不了」 ⇒ **该波就地升级为全量回归波**（跑全部 2411 条），等共享层改完再重跑 | 波次记录里写明升级原因与重跑结果 |
| **C5** | **共享层波内部排期**：按「跨域引用域数降序」（§1.4）⇒ 先动 10 域类、再 9 域类 ⇒ **每次改动后立刻跑全量** | 共享层波的工作日志 |
| **C6** | **物理位置不变**：提级的是**写权与波次归属**，**不是目录搬迁** ⇒ `users-common` 的类**留在原目录**（避免大范围 import 改动 ⇒ 大爆炸半径） | 目录保持 `service/users/users-common/` |

### 3.3 反馈单格式（域波 → 共享层波，必填 7 项）

缺任一项 ⇒ **不得进入共享层波排期**（防止「甩单变成半截」）。

| # | 字段 | 要求 |
|---|---|---|
| 1 | **现象** | 具体的失败表现（哪个域 / 哪个接口 / 什么错误） |
| 2 | **Go 侧出处** | `文件:行号`（Go 真源路径 + 行号）—— **必须存在**，否则先自行核实 |
| 3 | **RAG 侧现状** | `文件:行号`（RAG 当前实现） |
| 4 | **证据强度** | L1（源码对照）/ L3（静态实测）/ L4（运行时双侧活体）—— **不得写成 L4 除非真有两侧同时跑的探针** |
| 5 | **建议修法** | 期望的语义（不是「把 RAG 改成 X」而是「两侧都应是 Y」） |
| 6 | **影响面** | 受影响的域/模块清单（可由 `tools/difftest/check-wave-coupling.py` 的度数数据生成） |
| 7 | **是否阻塞本波** | 是 ⇒ 触发 **C4** 例外升级；否 ⇒ 进共享层波排期 |

### 3.4 共享层波与 10 条 `<域>-common` lane 的关系

10 条 lane 是**阶段 1（只读对账）**：并行、互不依赖、产出**逐轴结论 + 反馈单清单**。
**阶段 2（按波次落地）** 才落码，且**共享层统一在 G4/W5 落** ⇒
⚠️ **阶段 1 若落域侧代码，会与阶段 2 同域撞文件** ⇒ 阶段 1 **必须全只读**。

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

⇒ **结论**：**8 个类提级到共享层波**（写权归共享层波），**3 个类留在 users 域**。
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
| **GAP-1** | **全仓无 API 兼容门禁**（无 `japicmp`/`revapi`/`clirr`） | 共享层行为级破坏**编译绿、无网**（§1.6） | 在共享层波引入 API 兼容检查（基线 = 波次开始前的 `common` jar） |
| **GAP-2** | **`users-common` 未按类提级** | `users-common` 目前是「共享层与 users 私有混装」；写权归属不明 ⇒ G1/W1 改 2 个私有类也可能炸 6 外域 | 按 §四 落实：10 类归共享层波、2 类留 users |
| **GAP-3** | **契约边只有 L3 静态证据** | DAG 是「声明边」，未两侧活体对拍 ⇒ 可能漏掉**运行期才出现**的依赖（如反射 / 配置注入的 client） | 共享层波补一次 L4 探针（两侧同时起进程，抓真实调用） |

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
    "GoStyleFormValues": [
      "article"
    ],
    "GoStyleGrpcClientBreakerInterceptor": [
      "article",
      "favorite",
      "follow",
      "hot",
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
    "GoStyleRpcProtectionChain": [
      "comment",
      "favorite",
      "follow",
      "hot",
      "like",
      "task",
      "users"
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
      "comment",
      "hot",
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
```

守门逻辑：从仓库**重算**（① `pom.xml` 依赖度数 ② `@GrpcClient` 边 ③ yaml 跨域 topic ④ `users-common` 逐类跨域引用 ⑤ `service/common` 逐类辐射度），
与本文记录的快照**逐项比对** ⇒ **有人新增/删除一条契约边、或改了一个共享类的归属，却没更新本文 ⇒ EXIT=1**。

⚠️ **surefire 用例数只作参考输出，不参与判定** —— 它依赖 `target/` 是否已构建 ⇒ 进判定会在干净检出上恒红（同源「恒红的守门等于没有守门」）。

⚠️ **本守门已做两次变异验证**（2026-10-27）：① 注入一条新的 `@GrpcClient` 边（favorite → hot-rpc）⇒ `EXIT=1` 且精确点名 `favorite`；
② 改一个跨域 topic 名（`article-hot-events` → `article-hot-events-v2`）⇒ `EXIT=1` 且点名该 topic **从矩阵中消失**。
