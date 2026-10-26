# global 第二十六轮 —— **整合收口**（4 条 lane 合回）+ **两处缺陷修复** + **一条自曝错账**

> **分支**：`align/global` · **基线**：`d7a126a`（L2 合并后）· **性质**：**整合轮**（1 个共享层缺陷 + 1 个守门缺陷 + 大量台账整合）
> **对 favorite 的改动量**：**0 个文件**（代码 / 配置 / 测试 / 台账全零改动）⇒ 共享层影响**记账 + 告知 owner**
> **需拍板**：**0 条**（登记类事项登记完就躺着；D1/D2 已单独处理，不重复提）

---

## 一、这一轮在做什么

| # | 事项 | 性质 |
|---|---|---|
| ① | **L5（signoff）lane 产出合回**（`22e1cc6`）⇒ 4 条待签字候选（`CAND-14..17`）+ T-11/T-12 定性 + 2 条新坑 | 文档 |
| ② | **L2 / L3 / L4 三条 lane 的共享台账补丁 fold 进共享文件** | 文档 |
| ③ | **共享索引整合**（三个 `_INDEX.md` + `AGENTS.md` §0/§3.5/§6.3 + `tools/difftest/README.md`） | 文档 |
| ④ | 🔴 **修「共享层实现缺陷」**：T-6 写的原始串只在真 Tomcat 上成立 ⇒ MockMvc 路径分叉 ⇒ 打红了 favorite 的守门 | **代码** |
| ⑤ | 🔴 **修「守门自身缺陷」**：`LikeFactDispatchContractGateTest` 的调度器句柄永远拿不到 ⇒ 跨用例泄漏 ⇒ 偶发红 | **测试** |
| ⑥ | 🆕 **2 条新坑**（PIT-GLOBAL-024 / 025）+ **补上 L1 提出但一直没人写的 PIT-GLOBAL-021** | 文档 |

---

## 二、🔴 自曝错账 #3：T-6 初版写的原始串**只在真 Tomcat 上「看起来对」**

| 项 | 内容 |
|---|---|
| **原实现** | `GoStyleJsonContentTypeAdvice.GO_JSON_CONTENT_TYPE = "application/json; charset=utf-8"`（**Go 的字面量，带空格**） |
| **原推理** | 「Tomcat 归一化 ⇒ 写什么都一样 ⇒ 写原始字符串就能**自动**回到与 Go 逐字节一致」 |
| **🔴 实测推翻** | **真 Tomcat**：Tomcat 11 把 Content-Type 拆成 (type/subtype, charset) 重新拼 ⇒ 写什么都归一化 ⇒ 真 Tomcat 守门 3/3 绿 ⇒ **看起来对**。<br>**MockMvc**（各域 `*MvcPipelineTest` 走这条）：**没有容器归一化** ⇒ 写什么就原样出现 ⇒ **带空格** ⇒ **打红 favorite-api 的 `FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant`**。<br>⇒ ⇒ **「真容器守门绿」≠「这个改动在所有观测路径上都无害」** |
| **修法** | 改写 **`application/json;charset=utf-8`（无空格）** = **各域自己 advice 会写的那个值**<br>（`MediaType.parseMediaType("application/json; charset=utf-8").toString()` ⇒ Spring **保留原样的大小写** ⇒ 小写 `utf-8`）<br>⇒ 真 Tomcat：**不变** · MockMvc：**零变化** ⇒ **favorite 一行不用改**（零写权约束满足） |
| **⚠️ 陷阱** | `new MediaType(APPLICATION_JSON, UTF_8).toString()` = `application/json;charset=UTF-8`（**大写**）⇒ **不能**拿它当期望值（会把正确实现判成红）<br>⇒ 实测取证：`jshell --class-path spring-web-7.0.9.jar` |

### Go 出处

| Go 文件:行号 | 事实 |
|---|---|
| `go-zero@v1.10.2/rest/internal/header/headers.go:16-17` | `ContentTypeJson = "application/json; charset=utf-8"`（**分号后有空格**） |
| `go-zero@v1.10.2/rest/httpx/responses.go:175-193` | `doWriteJson` ⇒ `w.Header().Set(ContentType, header.ContentTypeJson)` ⇒ **无条件覆盖** |

### RAG 侧依据

| 位置 | 事实 |
|---|---|
| `org/apache/catalina/connector/Response.java`（11.0.24）`setContentType(String)` | `MediaTypeCache.parse(type)` ⇒ 有 charset 时 `setContentTypeNoCharset(parsedType[0])` + `setCharsetHolder(parsedType[1])` ⇒ **拆开存** |
| `org/apache/coyote/Http11OutputBuffer.writeHeaders()` | `put(contentType)` + `put(SEMICOLON)` + `put(CHARSET)` ⇒ **线上恒为 `type/subtype;charset=xxx`（无空格）** |
| Spring `MediaType.parseMediaType("application/json; charset=utf-8").toString()` | = `application/json;charset=utf-8`（**保留原样大小写**）⇒ **各域自己 advice 的产物** |

---

## 三、🔴 守门自身的缺陷：调度器句柄永远拿不到 ⇒ 跨用例泄漏

| 项 | 内容 |
|---|---|
| **症状** | `LikeFactDispatchContractGateTest.failedDeliveryBreaksTheInnerBatchImmediately` **偶发红**：`expected: 0 but was: 1` |
| **根因** | `Dispatcher.run(...)` 是**阻塞式**（在调用线程上跑循环，**直到 `stop()` 才返回**），而守门把句柄**设在 `run` 之后** ⇒ **句柄永远拿不到** ⇒ `@AfterEach` 停不掉 ⇒ **跨用例泄漏**<br>⇒ 泄漏的调度器仍在 1s 一跳地领行 ⇒ 与本用例的调度器**并发抢行** ⇒ 领到毒丸的一方把 `msg-01` 标成 `delivery_status=1`（在途）⇒ 另一方领走 `msg-02` ⇒ 「毒丸失败 ⇒ 同轮 break ⇒ 下一条一次都不许被试」**因为并发**（而不是因为 break）而变红 |
| **🔴 更糟的一面** | 泄漏**污染兄弟用例**：第 2 个用例断言「每轮 16 条」，那个 16 是**两个调度器凑出来的** ⇒ 即使绿，**测的也不是它声称在测的东西** |
| **生产代码** | ✅ 无此缺陷（`LikeFactDispatchScheduler` / `CommentFactDispatchScheduler` 都用 `runAsync`）<br>⚠️ `FavoriteFactDispatchScheduler` 用**阻塞式 `run`** —— 但触发点是 `@EventListener(ApplicationReadyEvent)`（**不是** `@PostConstruct`）⇒ 上下文已刷新 ⇒ **不卡启动**，只是**占住主线程** ⇒ **favorite 零写权 ⇒ 只记账 + 告知 owner** |
| **修法** | 守门改用 `Dispatcher.runAsync(...)` ⇒ 句柄立刻可拿 ⇒ `@AfterEach` 真能停 |

---

## 四、改动清单

| # | 文件 | 改动 |
|---|---|---|
| ① | `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java` | `GO_JSON_CONTENT_TYPE` ⇒ **无空格**形态；类注释**改写**（删掉「写原始字符串是为了逐字节对上 Go」那段**已被证伪**的推理） |
| ② | `service/common/src/test/java/com/rag/common/http/GoStyleJsonContentTypeCharsetTomcatTest.java` | 🆕 **门 4**：`adviceWritesExactlyWhatDomainsOwnAdviceWritesInMockMvc`（该类 3 条 ⇒ **4 条**） |
| ③ | `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeFactDispatchContractGateTest.java` | `startDispatcher()` ⇒ 改用 `Dispatcher.runAsync`；注释写清**为什么不能用 `run`** |
| ④ | `docs/DRIFT/global/lanes/L1-delivery.md` | 🆕 **§十四**（两处缺陷 + 错账 #3 + favorite 记账 + 未证成项） |
| ⑤ | `docs/DRIFT/global/common-tails.md` | T-6 的「修法」行订正 + 🆕 **自曝错账 #3** 行 |
| ⑥ | `docs/Pitfalls/global/common-tails.md` | 🆕 **PIT-GLOBAL-024**（调度器泄漏）/ **025**（两条观测路径分叉） |
| ⑦ | `docs/Pitfalls/global/cross-domain.md` | 🆕 **PIT-GLOBAL-021**（变异判定只认 surefire XML）—— L1 提出、本轮补上 |
| ⑧ | `docs/Pitfalls/_INDEX.md` | 坑位计数 **299 → 304** + global 行 + 待填段的 4 处假「已填」订正 |
| ⑨ | `docs/DRIFT/_INDEX.md` | global 行（20 轮 → **25 轮** + 5 份 lane 台账指针）+ 搬出段 39/40 两口径 |
| ⑩ | `docs/Intentional_Deviations/_INDEX.md` | CAND 族 **10 → 14**、未签候选 **14 → 18** + `global` 行 + `users` 行 |
| ⑪ | `docs/Intentional_Deviations/{hot,like}/api.md` | `SD-2026-10-25-CAND-10` **补 Go 出处**；`SD-2026-10-24-10` **性质字段订正** |
| ⑫ | `docs/Intentional_Deviations/users/_shared.md` | ← **fold** 自 `global/users-domain.md`（临时落点 ⇒ 按 §5.3b 硬边界 2 迁回域内档并删临时档） |
| ⑬ | `docs/DRIFT/common/alignment-2026-10-23.md` | `:575` / `:600` 的**编号记错**订正（`SD-2026-10-23-09` ⇒ `SD-2026-10-24-10`） |
| ⑭ | `docs/DRIFT/_cross/{open-items,ddl-type-divergences}.md` | P2 / P3 状态更新（按 L3） |
| ⑮ | `docs/DRIFT/global/cross-domain.md` | X-1 §二·十二 ⑥「三进程并发」**已闭环** + ⑤ favorite-rpc 故障形态订正（**起得来、首次查库才炸**） |
| ⑯ | `docs/Pitfalls/_cross/flyway-ledger-collision.md` | 「三模块同库共存仍 `unresolved`」⇒ **已闭环** + 改掉「`FlywayLedgerIsolationTest` 负责」那句**不存在的类**的声明 |
| ⑰ | `tools/difftest/README.md` | 🆕 `ddl-parity.sh`（L3）+ `check-flyway-concurrent.sh`（L4）两行 |
| ⑱ | `AGENTS.md` | §0 global 行 + 未签候选数 + DDL 段（探针 4 → **9 域**）+ §3.5 表三行 + §6.3 问题 4；**并压回 32 KB 预算内**（见 §六） |
| ⑲ | 全仓 **21 处坏相对链接** | 机械修正（多/少一级）⇒ 链接自检 **MISSING_LINKS = 0** |

---

## 五、favorite 记账（**本轮对 favorite 零文件改动**）

| 项 | 内容 |
|---|---|
| **① MockMvc 路径的响应头** | 共享层曾把 favorite-api 的 JSON 响应头改成带空格形态 ⇒ **打红 favorite 自己的守门** ⇒ 已由**共享层**改回无空格 ⇒ favorite 的观测值回到与改动前**逐字节相同** ⇒ **favorite 侧不需要改任何代码**。<br>⇒ ⚠️ favorite owner 需要知道：**favorite-api 与 follow-api 各有自己的 advice** ⇒ 共享层的 advice 与它们**竞争同一个响应头**（谁最后写谁赢）⇒ 共享层必须写成**与各域自己 advice 相同的值**。 |
| **② `FavoriteFactDispatchScheduler` 用阻塞式 `Dispatcher.run`** | 触发点 `@EventListener(ApplicationReadyEvent)` ⇒ **不卡启动**，但**占住主线程** ⇒ 🔴 **建议改成 `runAsync`**（与 `LikeFactDispatchScheduler` 同款）⇒ **global 不改**（零写权）。 |

---

## 六、🔴 顺带修掉的一个「自己破了自己的约定」

本轮改完 `AGENTS.md` §0 / §3.5 后，文件变成 **34,151 B** ⇒ **超出 §0 明写的 32 KB 预算 1,383 B**
⇒ §0 那条「**AGENTS.md 超预算 = 没走这条约定**」被自己破了。

⇒ 压缩回 **32,680 B**（余 88 B），**只删真冗余、不删纪律**：

| 删了什么 | 为什么是冗余 |
|---|---|
| §一 RAG 行尾（「非 Spring Boot 的 Java 写法不在范围内」） | 与紧随其后的「RAG 的存在范围」段**完全重复** |
| §7.1 重复打印 Go 仓库路径 | §一 表已有 |
| §五 item 3 末尾「⇒ 手册见 §7.2」 | **错指针**（手册在 `docs/Worktree.md`）且与 item 3b 重复 |
| §7.2 工具表 / 文档档表的描述性文字 | 各脚本 docstring 与各档自己的表头已有一份 |
| §3.4 一段 12:25 的历史细节 | 「实测产出 88 java + 4 `*Grpc`」已归档在 `proto-verification.md` |

---

## 七、验收标准

- [x] 自曝错账 #3 **写进台账**（`lanes/L1-delivery.md` §14.1 + `common-tails.md` T-6 段 + spec §十二）
- [x] 两处缺陷的**定性**都落到**根因**（不是「先让它变绿」）
- [x] 共享层改动**没有**打红任何别的域的守门 ⇒ **favorite 零改动**
- [x] 新守门（门 4）**非空转**（它断言的是一个**具体等式**，改任一侧都会红）
- [x] 2 条新坑 + 1 条补写的坑都**落到 `docs/Pitfalls/global/`**（三档互斥：一条问题只落一档）
- [x] `AGENTS.md` 压回 32 KB 预算内 + 链接自检 `MISSING_LINKS = 0`
- [x] 全仓回归 `mvn -o test -fae` ⇒ **0 failures / 0 errors / 0 skipped**
- [x] 需拍板清单 **≤ 2 条**（实际 **0 条**）

## 八、本轮**没有**证明的事

| # | 未证成项 | 原因 |
|---|---|---|
| ① | 其余 8 个 api 的 **MockMvc 路径**逐域对拍 | 只在 favorite-api（实测被打红的）与 follow-api（同形态）上验证了「无空格 = 各域自己的值」⇒ 未逐域跑 |
| ② | 真 Tomcat 下「两种字面量**确实**归一化成同一个值」 | 由 Tomcat 源码坐实 + 真 Tomcat 守门间接印证 ⇒ 未写「两种字面量各发一次、逐字节比对」的对照用例 |
| ③ | RFC 8259 的规范方向取舍 | `SD-HOT-API-006` 理由②说「JSON 的 charset 恒为 UTF-8、不应声明」⇒ 本轮按**用户决策**加 charset ⇒ 该规范方向**仍未重评** |
