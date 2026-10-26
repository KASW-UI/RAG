# global 第二十五轮 —— **T-6**：JSON 响应的 `Content-Type` 补上 `charset=utf-8`

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`8e6b7b2`（第二十四轮 + L3/L4 合并）
> **性质**：**共享层改动**（`service/common`）⇒ **10 个 api 全部生效** ⇒ **零 api 代码改动**
> **触发**：**用户拍板**（2026-10-25 21:57）——「撤销 `SD-HOT-API-006`，修 T-6」
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**）

---

## 一、要解决什么

`T-6`：成功/失败响应的 `Content-Type` **缺 `charset=utf-8`**（Go 带，RAG 完全没有）。

| | 响应头 |
|---|---|
| Go | `application/json; charset=utf-8` |
| RAG（修前） | `application/json`（**连 charset 都没有**） |

⇒ 修法落在**共享层** ⇒ 改 1 处 ⇒ 10 个 api 全部生效。

---

## 二、Go 出处（逐条）

| Go 位置 | 语义 |
|---|---|
| `rest/httpx/responses.go:175-193`（`doWriteJson`） | `w.Header().Set(ContentType, header.ContentTypeJson)` ⇒ **无条件覆盖** |
| `rest/internal/header/headers.go:16-17` | `ContentTypeJson = "application/json; charset=utf-8"`（**分号后有空格**） |
| `rest/httpx/responses.go:142-173`（`doHandleError`） | 错误响应走 `writeJson` ⇒ **同一个 ContentTypeJson** |

---

## 三、🔴 本轮**自曝错账 #2**：台账说「`SD-HOT-API-006` 的理由已被本仓自己证伪」—— **不成立**

global 台账（`common-tails.md` §一·T-6）原写：

> ⚠️ **该理由已被本仓自己证伪**：`GoStyleRouterErrorResponses.java:81-88` 对 `text/plain; charset=utf-8`
> 就是**直接写原始字符串** ⇒ **同一手法用在 `application/json; charset=utf-8` 上即可**

⇒ ⇒ **第二十五轮实测证明这个推理是错的**：写原始字符串**并不管用** ——
**Tomcat 11 把 Content-Type 拆成 (type/subtype, charset) 两份存、写出时重新拼**
⇒ 线上恒为 `type/subtype;charset=xxx`（**分号后无空格**）
⇒ ⇒ **应用层根本拿不到那个空格**（`setContentType` / `setHeader` / `addHeader` 三条路都汇到 `setContentType`）。

⇒ ⇒ ⇒ **`SD-HOT-API-006` 的原始理由「不可逐字节复刻」是成立的，而且比当初写的更强**
⇒ ⇒ ⇒ **撤销的性质因此改变**：**不是**「技术上变得可行了」，而是
**「技术上仍然不可逐字节复刻，但我们选择向 Go 靠拢」** ⇒ **纯用户决策**。

---

## 四、改动（3 个文件）

| # | 文件 | 改动 |
|---|---|---|
| ① | `service/common/.../http/GoStyleJsonContentTypeAdvice.java`（**新增**） | `@AutoConfiguration` + `@RestControllerAdvice` ⇒ JSON 响应头写 `application/json;charset=utf-8`<br>⚠️ **2026-10-25 第二十六轮订正**：初版写的是 **Go 的字面量（带空格）** ⇒ 只在**真 Tomcat** 上「看起来对」⇒ 在 **MockMvc** 路径上分叉 ⇒ 打红了 favorite 的守门 ⇒ **已改为无空格形态**（= 各域自己 advice 会写的那个值）⇒ 详见本 spec 末尾「第二十六轮订正」 |
| ② | `service/common/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | 登记上面这个 advice ⇒ **10 个 api 全部生效** |
| ③ | `service/common/.../http/GoStyleRouterErrorResponses.java` | **订正一条被证伪的注释**（「写原始字符串是唯一能保证逐字节对上的做法」⇒ 实测证伪） |

### 为什么挂在 ResponseBodyAdvice 而不是转换器上

共享层的 JSON 转换器 `GoStyleJsonBodyConverter` **不是所有 api 都启用** ——
`task-api` 明确把它排除在组件扫描之外 ⇒ 挂在它上面 ⇒ **task-api 不生效**。
⇒ Advice 按「响应体是不是 JSON」判定 ⇒ **与用哪个转换器无关**。

### 判定规则（两条取或）

1. 选中的转换器**是 Jackson 系** ⇒ 响应体就是 JSON；
2. 选中的 **Content-Type 是 JSON** ⇒ 即使转换器不是 Jackson，body 仍是 JSON。

⇒ 404 / 405 的 `ResponseEntity<byte[]>`（`ByteArrayHttpMessageConverter` + 显式 `text/plain`）
**两条都不满足** ⇒ **不会被误伤**。

---

## 五、守门（3 条，真 Tomcat + 裸 socket）

`GoStyleJsonContentTypeCharsetTomcatTest`

| # | 守门 | 断言 |
|---|---|---|
| 1 | `jsonResponseContentTypeMatchesGoByteForByte` | JSON 成功响应 ⇒ `application/json;charset=utf-8` |
| 2 | `jsonErrorResponseContentTypeMatchesGoByteForByte` | JSON 失败响应（`@ExceptionHandler`）⇒ 同一个 |
| 3 | `explicitContentTypeIsNotClobbered` | 显式 `text/plain; charset=utf-8` ⇒ **不许**被改写成 JSON |

⚠️ **断言的是「归一化后」的形态** —— 与仓库既有先例一致
（`LikeApiRouterErrorRealTomcatTest` 对 404 就是**刻意解析后比对**）：

> 比原始字符串会得到一个「必然红、且红了也不代表有错」的断言（已实测）。

⇒ 锁「charset 必须在 + type 必须是 application/json」，**不去锁那个空格**。

---

## 六、变异（2 条，全部转红）

| # | 变异 | 期望红点 | 实测 |
|---|---|---|---|
| **M8** | Advice 不写 Content-Type | 两个 JSON 门红 + 不误伤门绿 | ✅ `maven_exit=1 · tests=3 · fail=2` |
| **M9** | Advice 无条件写（去掉 JSON 判定） | 不误伤门红 + 两个 JSON 门绿 | ✅ `maven_exit=1 · tests=3 · fail=1` |

---

## 七、🔴 新坑：`PIT-GLOBAL-021` 要补一条

**`surefire` 打印的 `-- in` 后面跟的是类的 `@DisplayName`（若有），不是全限定名。**

⇒ 只匹配 FQN 的判定正则 ⇒ **红与绿都读不出来** ⇒ 误报 `NO_RESULT`
⇒ ⇒ **修法**：判定**只认 surefire 的 XML**（逐条 `testcase`），**不依赖 stdout 的 `-- in` 行**。

---

## 八、可观察变化（**记账，不静默**）

| # | 变化 | 影响面 |
|---|---|---|
| ① | **JSON 响应头多一个 `charset=utf-8`** ⇒ **向 Go 收敛** | **全部 10 个 api** |
| ② | 与 Go 的**残余** = 只差一个 OWS ⇒ RFC 9110 **OWS 不显著** ⇒ **语义相同** | 同上 |
| ③ | 404 / 405 的 `text/plain; charset=utf-8` **不受影响** | 同上 |

---

## 九、favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
> favorite-api 的 JSON 响应头**多了** `charset=utf-8` ⇒ **向 Go 收敛**（Go 的 favorite api 同样带）
> ⇒ 若 favorite 域有守门断言「响应 Content-Type 恰为 `application/json`」⇒ 那是**依赖旧缺陷的断言**，需复核。

---

## 十、未证成

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Go 侧未活体对拍** | Go 的常量取自逐行读源码 ⇒ 没跑 Go 进程 |
| ② | **「那个空格」在应用层彻底不可达** | 已坐实（Tomcat 归一化）⇒ 但**没试过**改 Tomcat 配置 / 换容器 |
| ③ | **其余 9 个 api 未逐域对拍** | 本轮在 `service/common` 用真 Tomcat 证明「共享层语义」 |
| ④ | **RFC 8259 的规范方向未重估** | `SD-HOT-API-006` 的理由②说「JSON 的 charset 恒为 UTF-8，不应声明」⇒ 本轮按**用户决策**加了 charset ⇒ 该取舍未在台账重评 |

---

## 十一、交接整合者

| # | 事项 |
|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 **T-6** 段 + **订正 §一·T-6 里那条被证伪的「理由已被证伪」** |
| ② | `docs/Intentional_Deviations/_INDEX.md` 同步 `SD-HOT-API-006` 的撤销 |
| ③ | `docs/DRIFT/hot/api.md` 的 `DRIFT-HOTAPI-024` 已由本 lane 更新 ⇒ **请复核 + 知会 hot 域 owner** |
| ④ | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` |
| ⑤ | **L2 / L5 两条 lane 的合并**（L3 / L4 已合并） |
| ⑥ | `align/global` → `do-my-own-gh` 的合并 |


---

## 十二、🔴 第二十六轮订正（全仓回归收尾 · 由整合者执行）

> **触发**：第二十五轮改完后跑全仓回归 `mvn -o test -fae` ⇒ **2 条红** ⇒ 逐条定性 ⇒ **两条都不是生产缺陷**。
> ⇒ 逐条记录 ⇒ [`docs/DRIFT/global/lanes/L1-delivery.md`](../../docs/DRIFT/global/lanes/L1-delivery.md) §十四。

### 12.1 自曝错账 #3：本 spec 第五节写的「原始字符串」在 **MockMvc 路径上**不成立

| 项 | 内容 |
|---|---|
| **本 spec 第五节的推理** | 「JSON 响应头写**原始字符串** `application/json; charset=utf-8` ⇒ 意图在代码里可见，且万一哪天容器归一化行为变了，就会**自动**回到与 Go 逐字节一致」 |
| **🔴 为什么错** | 那个推理**只在真 Tomcat 上成立**（Tomcat 11 归一化 ⇒ 写什么都一样 ⇒ 真 Tomcat 守门 3/3 绿 ⇒ **看起来对**）。<br>⇒ **MockMvc 路径没有容器归一化** ⇒ 写什么就原样出现 ⇒ **带空格** ⇒ **打红 favorite-api 的守门**（`FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant`）。 |
| **改法** | 写成 **`application/json;charset=utf-8`（无空格）** = **各域自己 advice 会写的值**（`MediaType.parseMediaType("application/json; charset=utf-8").toString()` ⇒ Spring **保留原样大小写** ⇒ 小写 `utf-8`）⇒<br>真 Tomcat：**不变**；MockMvc：**零变化** ⇒ **favorite 一行不用改**。 |
| **⚠️ 陷阱** | `new MediaType(APPLICATION_JSON, UTF_8).toString()` = `application/json;charset=UTF-8`（**大写**）⇒ **不能**拿它当期望值。 |
| **⇒ 教训** | **PIT-GLOBAL-025** |

### 12.2 守门自身的缺陷：`LikeFactDispatchContractGateTest` 调度器句柄永远拿不到

⇒ 阻塞式 `Dispatcher.run` + 「句柄设在 `run` 之后」⇒ **句柄永远拿不到** ⇒ 调度器**跨用例泄漏**
⇒ 并发抢行 ⇒ 「毒丸失败 ⇒ 同轮 break」那条断言**因为并发**而偶发红 ⇒ 改用 `Dispatcher.runAsync` 即修。
⇒ **生产路径本来就是 `runAsync`** ⇒ 无此缺陷。⇒ 教训 ⇒ **PIT-GLOBAL-024**。

### 12.3 新守门（门 4）

`GoStyleJsonContentTypeCharsetTomcatTest.adviceWritesExactlyWhatDomainsOwnAdviceWritesInMockMvc`
⇒ 锁「**Advice 写的值 == 各域自己 advice 会写的值**」⇒ 该类 **3 条 ⇒ 4 条**。

### 12.4 本轮**没有**证明的事

见 [`docs/DRIFT/global/lanes/L1-delivery.md`](../../docs/DRIFT/global/lanes/L1-delivery.md) §14.5（其余 8 个 api 的 MockMvc 路径未逐域对拍 · RFC 8259 的规范方向取舍未重评）。
