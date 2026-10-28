<!-- @move AGENTS.md L1444-L1534 · §0 article-common 双侧对拍 -->
> 📌 **本文件是单轮快照**（从 AGENTS.md 搬移，**无历史轮次**）⇒ **全文即「现状权威面」**。
> ⇒ 域内其它组件是**多轮**台账（各有「怎么读这份台账」的指针 + **跨文件轮次对照表**）：
> [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`proto.md`](proto.md)。
### ✅ `service/article/article-common`：Go ↔ Java 双侧对拍 **sha256 逐字节一致**（2026-10-22 晚）

> **§0「从未验证」清单里 `article` 一项的第一块收口**（`hot-common` 之后的第 3 个 `-common` 模块族先例）。
> Go 真源只有 1 个文件（`service/article/common/errmsg/errmsg.go`，**45 行、零 import**），
> 是纯值定义 + 纯函数库：**无 DB / 无 RPC / 无 MQ / 无鉴权 / 无事务 / 无缓存**
> ⇒ **L3 技术语义在本组件上全部为空**，风险 100% 集中在 L1（码值 + 文案 + 回落）。

#### 落地了什么：1 个类 + 1 个契约测试类 + 1 个探针工具

| Go 来源 | Java 落点 | 关系 |
|---|---|---|
| 15 个 const（`errmsg.go:3-19`） | `article-common/errmsg/ErrorCode`（enum，**声明顺序 = Go 书写顺序**） | ADAPT |
| `codeMsg` 表（`:21-37`，15 条） | 同上枚举的 `msg` 字段（**与码值合并**） | ADAPT |
| `GetErrMsg`（`:39-45`），未知码回退 `"FAIL"` | `ErrorCode.getMsg(int)`，未知码回退 `"FAIL"` | ADAPT |
| — | `ErrorCode.of(int)` / 实例 `getMsg()` | **新增便利层**（无 Go 对应物，见下方边界 3） |

⚠️ **落点是 `article-common` 而非 `article-api` 的 `service` 包** —— 三条互相独立的依据：
`article-api` 的 pom **已**声明对本模块的依赖（**消费侧早已接线**，见下方边界 1 的 2026-10-29 实测）、
`service/article/pom.xml` 已把 `article-common` 列为 `<module>`、
`users-common` / `message-common` / `like-common` / `hot-common` 四域口径统一。
⚠️ **类数（1）与落点是两个独立决定** —— 类数由「码与文案是否**同一个结构**」定：
Go 的 `const` 块与 `codeMsg` 是**两个**结构，但 article 的两侧**一一对应且无第三方码**，
合并成单枚举**不丢任何可观察行为**（合并可能丢东西这一点已被测试钉死，见下）。

⚠️ `article-api` 侧的**平行常量表已被前序会话废止** —— `article-api/service/ArticleErrorCodes.java`
现为 18 行「已废止，请勿在此实现常量」的说明文件，指向本模块。
⇒ **`article-common` 是 article 域码表的唯一真源**，不要在 api/rpc 侧另起一份。

#### 三条硬约束（错一条就是客户端可见的错码）

1. **未知码必须回退 `"FAIL"`，不是 `null` / 空串 / code 的字符串** —— Go `errmsg.go:39-45`
   `if !ok { return codeMsg[Error] }`。Go api 侧 `httpx.OkJson` 把 `Msg: errmsg.GetErrMsg(code)`
   直接写进 envelope ⇒ 回落值是**客户端可见**的。
2. **Go 侧 `const` 块与 `codeMsg` 表登记的必须是同一组码** —— Go api 层 handler 传的是 const、
   查的是 `codeMsg` 表；两者若不一致，Go 自己的信封 msg 就会意外回落到 `"FAIL"`。
   ⇒ 这条**只能在 Go 侧查**（Java 侧把两者合并了，看不见），已放进探针的 Go 自检里。
3. **article 与 users / like 共用同一段 int 值空间，且 article 的 15 个码**全部**能在 users 域找到同号**
   —— 其中 **6 个同号同义**（200/500/1015/5001/5002/5003，同源模板），
   **9 个同号不同义**（1001~1006、5004~5006）。⇒ 跨域推断含义是**高频误操作**，
   必须逐条钉死（见「跨域守卫」）。

#### 验证：4 层，且都不是「绿了就信」

| 层 | 手段 | 结果 |
|---|---|---|
| **双侧对拍** | `tools/errmsg-probe/run-parity.sh`：Go 半边把**真源 `errmsg.go` 逐字节复制**进临时模块并**同包编译**（能读未导出的 `codeMsg`），Java 半边跑**真实编译产物 `ErrorCode`**；输出 CONST/TABLE/FALLBACK 三节共 **49 行** | **sha256 逐字节一致** `cad5cb4f…`（`EXIT=0`） |
| **契约测试** | **15 条**（码值 / 文案 / 顺序 / 唯一性 / 总数 / 未知码回落 / 往返 / `getCode` 产 int / **Go 双结构双射** / 文案两两互异 / 非空白 / 不可变性 / **9 条跨域差异** / 6 条跨域同义 / 不搬别域码） | 全绿 |
| **变异验证** | **8/8 全部变红，且「契约测试 + 双侧探针」**双层同时红**：回落改 `null` / 码值漂移 5002→5009 / 文案改英文 / 枚举重排（**编译期红**）/ 擅自加码 / 漏迁 1003（**测试编译期红**）/ 回落改空串 / `of()` 返回 `null` | 守门有效，**非假绿**；基线复跑双层均绿 |
| **编译** | `article-common` 单模块 `mvn -o test` | **15 tests, 0 failures**, BUILD SUCCESS |

⚠️ 其中「**漏迁一个码**」在**测试编译期**就失败（`GO_CONST_VALUES` 引用了已删除的枚举常量），
与 `hot-common` 同款 —— **最强的一档**。
⚠️ 「**枚举重排**」在**生产代码编译期**就失败（重复常量）—— 本仓已出现过反面样本：
`like` 域的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError`，那份超建不要照抄。

#### ⚠️ 本次补上的两个真实缺口（不是「重做一遍」）

1. **跨域守卫只钉了 5 条，漏了 4 条** —— 早前版本只守 `1005/1006/5004-5006`，
   而**真正同号不同义的是 9 条**：`1001`（文章已存在 vs 用户名已存在）、
   `1002`（文章不存在 vs 用户名或密码错误）、`1003`（文章处理中 vs 用户不存在）、
   `1004`（文章发布失败 vs TOKEN不存在）**这 4 条此前完全没守**。
   本次补齐 9 条，并**反向**补了 6 条「同号同义」的模板码
   —— 防的是「有人看到 5004 与别域不同就顺手统一成数据库插入失败」这种反向误操作。
2. **合并成单枚举的「没丢东西」此前无人证明** —— Go 是两个结构，Java 是一个。
   本次用**两份独立的裸 int 抄录**（const 块一份、`codeMsg` 键一份，刻意不复用同一字面量）
   断言「Go const 集合 ≡ Go codeMsg 键集合 ≡ Java 枚举码集合」。

#### ⚠️ 三条边界（**不得越读成「article 域已完成」**）

1. ~~**消费侧尚未接线**~~ ⇒ ✅ **已接线（2026-10-29 · G3 阶段 1 实测订正）** ——
   本行原文写「`article-api`（29 个文件）与 `article-rpc`（41 个文件）**全部仍是 4 行空骨架**
   （`wc -l` 实测），本模块目前**零生产调用方**」⇒ ⇒ **该描述已过期，且是**同一份文件里
   未被自身订正**的过期描述** ⇒ ⇒ **实测**：
   · `article-api` = **43 个 java 文件 / 5390 行**（`find service/article/article-api/src/main/java -name '*.java'`）；
   · `article-rpc` = **39 个 java 文件 / 8648 行**；
   · **两个模块的 pom 都已声明 `<artifactId>article-common</artifactId>` 依赖**；
   · `article-api` 侧 **10 个生产文件** `import com.rag.article.article.common`；
   · 生产代码里 `ErrorCode.` / `getMsg(` 共 **53 处**调用。
   ⇒ ⇒ **不是空骨架、不是零生产调用方** ⇒ ⇒ 本模块**已有真实消费侧**。
   接线等价式（仍然成立，继续沿用）：`new BusinessException(ErrorCode.X.getCode(), ErrorCode.getMsg(code))`
   ≡ Go 的 `response.Response{Code: code, Msg: errmsg.GetErrMsg(code), Data: resp}`。
   ⚠️ 订正依据：`docs/DRIFT/_cross/component-coverage-matrix.md` 的「当前态」节也已记
   `article-api` 41 文件 / 5759 LOC ⇒ ⇒ **共享面那一份同样落后于域台账**（归合并收口执行者）。
2. **Go 的 rpc 侧只用码值、从不取文案** —— Go rpc 侧把 `errmsg.X` 当
   `logger.LogBusinessErr(ctx, code, err, …)` 的 **`error_code` 日志字段**用
   （`logger.go:145`），**该路径从不调用 `GetErrMsg`** ⇒ rpc 日志里**没有文案**，
   别误以为查表失效。Java 侧对称：`Logger.logBusinessErr(otelCtx, int code, Throwable, LogOption…)`
   只吃 int。⇒ 文案只在 **api 层的 HTTP 信封**里可见。
3. **`of(int)` 与实例 `getMsg()` 是 Java 侧的增量** —— Go 无对应物（Go 只有 `GetErrMsg`）。
   属于 §13 情况 D/E 的**新增便利层**，对齐 `users-common` 的既有约定
   （单枚举 + `of()` + `getMsg(int)`），不改变任何可观察行为。

⚠️ **`article-common` 没有 `HUMAN_ONLY.MD`，且不应补** —— 全仓仅 4 份、全在 `service/users/` 下；
`-common` 模块族（users-common / message-common / article-common / like-common / hot-common）
**从来没有过**这份文档。

⚠️ **方法论（比这个组件本身更重要）**：**任何纯 Java 侧的契约测试，对 Go 都只能靠「人工抄录」建立联系** ——
它能证明「改动 Java 时立刻变红」，但**不能**证明「Java 等于 Go」。
⇒ **锚必须是双侧探针**（§3.3「凡写『与 Go 一致』必须有两侧同时跑的探针」）。
本模块的分工是：**探针锚定真源，契约测试守住不回退** —— 与 §3.3 那条「绿测试守卫错误答案」互为镜像。

