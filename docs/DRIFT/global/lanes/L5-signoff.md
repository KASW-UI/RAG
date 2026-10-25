# L5 · `align/global-signoff` —— C 类登记 + T-7/T-11/T-12 定性 + A6 订正

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **本 lane 不产出「要用户拍板」的一堆问题** —— 用户既定偏好：**登记类事项登记完就躺着**，
> 不升级为拍板。只有**真正需要决策才能往下走**的事项才写进「需拍板」清单（**最多 1~2 条**）。

## 一、目标

把 `global` 域待办里**全部「登记类」**做干净，并给 **T-11 / T-12** 定性。

## 二、任务清单

### A. 补齐缺失的签字候选条目（`docs/Intentional_Deviations/global/`）

| 编号 | 内容 | 出处 |
|---|---|---|
| **C2** | **T-7**（状态行缺 reason phrase，落点 Tomcat `Http11OutputBuffer`）⇒ **框架限制** ⇒ ⚠️ 台账明写「**尚未登记候选条目** ⇒ 需补」 | `docs/DRIFT/global/common-tails.md` §一·T-7 |
| **C1** | `SD-2026-10-25-CAND-10`（hot 域 `DRIFT-HOTAPI-031`：超长请求行 ⇒ RAG 400+HTML / Go 431+`text/plain`）⇒ 框架限制 | `docs/DRIFT/global/_README.md` §七之二 C1 |
| **C3** | users 域 **8 处 `DEFAULT CURRENT_TIMESTAMP`**（Go 无）⇒ **刻意保留**（同 message 域 `SD-2026-10-23-06` 已签的同形态） | `_README.md` §七之二 C3 |
| **C4** | `users_user.status` 可空性（Go 可空 / RAG `NOT NULL`）⇒ **RAG 更严** ⇒ 同 C3 族 | `_README.md` §七之二 C4 |

⚠️ **每条签字候选必须先自己复核前提**（两侧**确实**不同）——
已发生过「7 条里 1 条前提根本不成立 ⇒ 登记错了」的事故 ⇒ **前提不成立的** ⇒ **不登记**，写明为什么不成立。

### B. T-11 / T-12 定性（§一 · 两条都是第十九轮新登记、**未修**）

| 编号 | 问题 | 要定性成什么 |
|---|---|---|
| **T-11** | trace **导出**链路在 RAG 侧整体缺失（`opentelemetry-sdk-extension-autoconfigure` 不在 ⇒ Boot 的 tracing 自动配置不生效 ⇒ span 建了也无人收；⚠️ exporter 的 jar 是在的，缺的是装配） | 🔴 **先核实 Go 侧到底导不导出 span** —— go-zero 的 `rest/handler/tracehandler.go` 只做 `propagator.Inject`（**注入 header**），**不一定导出** ⇒ 若 Go 也不导出 ⇒ **本条不是漂移** ⇒ 撤销或改写。**先查真源再下结论** |
| **T-12** | 「方法不匹配」这个出口，RAG 比 Go **多带**一个 `Traceparent` | 落点 = **出口层级差异**（Go 在 router 层 / RAG 在 `DispatcherServlet` 内）⇒ 台账已判「共享层改不动 ⇒ 建议签字认账」⇒ 复核后定稿为**签字候选**（或证伪） |

### C. A6 订正（纯文档）

- **B6** like 域 `SD-2026-10-23-09` 的**描述**订正：
  🔴 台账原文「`GoStyleRequestDeadline` 的公开 API 只暴露毫秒」**不成立** ——
  `applyTo(stub)` 就是**纳秒**的（`GoStyleRequestDeadline.java:110-120`）⇒
  真实理由是「**只读** API 只有毫秒粒度」（`LikeRpcClient.java:108-121`：
  `applyTo` 没有「上界」概念 ⇒ 表达不了 `min(上限, 剩余)`）。
- ⛔ `docs/DRIFT/common/alignment-2026-10-23.md:575/:600` 把这条记成了 `SD-2026-10-23-09`
  ⇒ **编号记错**（`-09` 是 worker 池裸 503 那条）⇒ 本 lane **可改**（属 global 写权范围）。

### D. `_README.md` §七之二 的**快照过期**（顺手修）

`_README.md` §七之二 是 **2026-10-25 06:40** 的快照 ⇒ **第十九轮新增的 T-11 / T-12 没进去**。
⇒ ⚠️ 但 `_README.md` 是**共享文件** ⇒ 本 lane **不直接改** ⇒ **把补丁内容写进本文件末尾**，由整合者 fold。

## 三、必读（定向读）

| 读什么 | 在哪 |
|---|---|
| 域纪律 + 待办清单 | `docs/DRIFT/global/_README.md` §〇 / §七 / §七之二 |
| T-7 / T-11 / T-12 | `docs/DRIFT/global/common-tails.md` §一·T-7 / §一·T-11 / §一·T-12 |
| 签字档**既有格式** | `docs/Intentional_Deviations/_INDEX.md` + `docs/Intentional_Deviations/hot/api.md`（`SD-HOT-API-010` 是最近一条样板） |
| 已签字的同形态先例 | `docs/Intentional_Deviations/message/`（`SD-MESSAGE-2026-10-23-06` = `DEFAULT CURRENT_TIMESTAMP` 同形态） |
| Go 真源 | `Sea-RideTheWind_golang/rest/handler/tracehandler.go`（T-11/T-12 的判据）· `Sea-RideTheWind_golang/rest/server.go:58`（404 也带 traceparent） |

## 四、写权范围（白名单）

**只写这些**：
- `docs/Intentional_Deviations/global/**`
- `docs/Pitfalls/global/**`
- `docs/DRIFT/global/lanes/L5-signoff.md`（本文件）

**⛔ 禁区**：
- `service/**`（**本 lane 一行代码都不改** —— 这是文档/登记任务）
- `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`（**共享文件 ⇒ 写补丁进本文件，等整合者 fold**）
- `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json`
- `docs/*/favorite/`

## 五、交付物

1. `docs/Intentional_Deviations/global/` 下新增/更新若干条**签字候选**条目，格式照既有样板。
2. **T-11 / T-12 的定性结论**（含 Go 出处 `文件:行号`）。
3. **A6 的订正文本**（供整合者 fold 进 `common/alignment-2026-10-23.md` 与 like 域台账）。
4. `_README.md` §七之二 的**补丁**（含 T-11 / T-12 两行）。
5. **需拍板清单（≤2 条）**：只列「不拍板就没法继续」的；其余一律**登记完就躺着**。

## 六、验收标准

- [ ] 每条签字候选的**前提已自己复核**（两侧确实不同）⇒ 不成立的**不登记**，写明原因
- [ ] 每条含：Go 出处 `文件:行号` · 两侧实际差异 · 性质分类（框架限制 / 刻意保留 / 破坏性变更）· 签字状态
- [ ] T-11 的结论**基于 Go 真源的实读**（不是推断）⇒ 若结论是「Go 也不导出 ⇒ 不是漂移」⇒ 明确写出并**撤销**该登记
- [ ] **需拍板清单 ≤ 2 条**
- [ ] 一条 Go 事实都没有「只写结论不带行号」的（§3.3 强制三条之一）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **登记的前提可能不成立** | 已发生过：7 条里 1 条的前提根本不成立 ⇒ **先复核前提再登记** ⇒ 别把错误前提推给用户签字 |
| **登记 ≠ 待拍板** | 用户明确纠正过：「都进 `docs/Intentional_Deviations/` 了，为什么还要我拍板」⇒ 登记完就躺着 |
| **「Go: …」措辞是高危项** | §3.3.2：以「Go: …」形式写在 `@DisplayName` / javadoc 里的断言，**必须**有 `文件:行号` 支撑消费点，否则**不许用该措辞** |
| **共享文件** | `_README.md` / `common-tails.md` / `cross-domain.md` / `cycle-deps.md` / `AGENTS.md` / `_INDEX.md` ⇒ **本 lane 不写** ⇒ 补丁进本文件 |
