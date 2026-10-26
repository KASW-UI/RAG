# global 第八轮 —— CAND-12 退回待签字 + 6 条替代路径全查证

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`3b49ed6`
> **性质**：**定性改判 + 证据补强**（不改任何业务代码）

---

## 一、要解决什么

用户 2026-10-25 05:46 的两条指令：

1. 「`SD-2026-10-25-CAND-12` **先等下签字**，我想知道**有没有其他办法**，springboot 做分布式肯定有办法」
2. 「现在**未知项有多少**」

⚠️ **附带发现的越权**：第四轮（`228da5c`）把 CAND-12 **自行转正为「已签字」**，
而用户当时只问了「它是什么」、**并未点头** ⇒ 本轮**撤销该签字**。

---

## 二、为什么要重查（原来的定性过宽）

原判定链：`Servlet 容器不向应用暴露客户端断连信号` ⇒ `框架限制` ⇒ `不存在决策空间` ⇒ `直接签字`。

🔴 **逻辑漏洞**：实测只覆盖了「**Tomcat + HTTP/1.1 + 同步 Servlet + Spring MVC**」**一种组合**，
却推出了「**生态层面做不到**」⇒ 而组合轴有 **4 条**（容器 × 协议版本 × 同步/异步 × 应用栈），**每条都还有别的取值**。

⇒ 用户质询成立。

---

## 三、6 条路径的查证结果

| # | 路径 | 判定 | 依据 |
|---|---|---|---|
| ① | 换 Servlet 容器（Jetty `HttpChannel.Listener#onAbort()`） | 🟡 可行 | Jetty `HttpChannelState#onReadEof()`「Called to signal that a read has read -1」；`onAbort()` 回调 | 🔴 10 个 api 模块换运行时 |
| ② | 换栈（WebFlux + Netty） | 🟡 可行 | Netty `channelInactive` / `Channel.isActive()` | 🔴 整个技术栈重写 |
| ③ | **轮询 Tomcat 内部 `isClosed()`** | ❌ **实测否决** | 反射链**通**：`RequestFacade` → `Request.getCoyoteRequest()`（`catalina/connector/Request.java:169`）→ 字段 `hook` → `AbstractProcessor.getSocketWrapper()` → `NioSocketWrapper`；**但**实测**滞后 5.6 秒**（客户端 986ms 断 ⇒ 6622ms 才翻 true）⇒ 因为只在 Tomcat **真正 close socket** 时置位（`SocketWrapperBase.java:657-658`）⇒ **比主动写还慢** |
| ④ | **HTTP/2 `RST_STREAM`** | 🟡 可行但**偏离 Go** | `Http2UpgradeHandler.java:1808-1822` `reset()` → `http2/Stream.java:216-226` `receiveReset()` → `http2/Stream.java:1461-1494`（`:1464` `resetReceived=true` + `DISPATCH_ERROR`）→ `AbstractProcessor.java:646-652` → `CoyoteAdapter.java:140-157`（`:151/154` `listener.onError`、`:157` `setErrorState`）⇒ **`AsyncListener.onError` 真触发**；HTTP/1.1 **无此路径**（`http11/**` 内 `DISPATCH_ERROR` 找不到）<br>⚠️ **Go 侧实测明文 HTTP/1.1**（`Sea-RideTheWind_golang` 全仓无 TLS / 无 `http2` / 无 `h2c`；唯一 `http.Server` 是 `fact-authority`）⇒ **换协议 = 偏离 Go** |
| ⑤ | **主动探测写** | ✅ **实测有效** | 客户端 **986ms** 断 ⇒ `flush()` **1649ms** 抛 `ClientAbortException: Broken pipe`（`catalina/connector/OutputBuffer.java:327-331` 包装）⇒ 延迟 ≈ 探测周期<br>🔴 **必须提前提交响应** ⇒ `Content-Length` → chunked ⇒ **wire 差异** |
| ⑥ | **缩短在飞 gRPC 的 deadline** | ✅ **零改动可用** | 断连后浪费 = 剩余 deadline；**零 wire 差异、零签字** | ⚠️ 非语义修复（Go 是 0ms） |

补充：Spring 官方 `org.springframework.web.util.DisconnectedClientHelper`（Since 6.1）
只**事后按异常分类** ⇒ **官方路径同样是「写失败」** ⇒ ⑤ 不是野路子。

---

## 四、两个探针（均不进仓）

| 探针 | 形态 | 结论 |
|---|---|---|
| `/tmp/probe-async/AsyncDisconnectProbe.java`（第七轮已有） | `startAsync()` + `AsyncListener` | `onError` / `onTimeout` / `onComplete` **一个都不触发** |
| `/tmp/probe-tomcat/DisconnectProbe.java`（本轮新增） | **同步 servlet**（= RAG 真实形态） | ① 主动 `flush()` → 断连后 ~500ms 抛 `ClientAbortException`；② 轮询 `isClosed()` → **滞后 5.6s** |

---

## 五、修正后的定性

| 原判定 | 新判定 |
|---|---|
| 「框架限制 ⇒ 不存在决策空间 ⇒ 签字认账」 | ❌ **「生态做不到」被证伪** |
| — | ✅ **「在『Tomcat + HTTP/1.1 + 同步 Servlet + 保 `Content-Length`』这组约束下没有 Go 等价路径」** |
| — | ⚠️ **而这组约束是项目自己选的** ⇒ ⇒ 本条从「**事实登记**」改判为「**要不要为这一点动约束**」的**决策项** |

⇒ **推荐顺序**：⑥（零改动零签字）→ ⑤（改 wire）→ ④（改协议）→ ①②（换栈）

---

## 六、改动清单

| # | 改动 | 落点 |
|---|---|---|
| ① | `SD-2026-10-25-02` **撤销「已签字」** ⇒ ⏳ 待签字；候选号 `CAND-12` 保留 | `docs/Intentional_Deviations/global/common-tails.md` |
| ② | 新增 §①~④（6 条路径查证 + 修正定性 + 推荐顺序） | 同上 |
| ③ | T-4 ③ 的「不可修」表述改为「在既定约束下拿不到信号」+ 指向新 §①②③④ | `docs/DRIFT/global/common-tails.md` |
| ④ | PIT-GLOBAL-007 补「第八轮订正」段（实测 + 教训） | `docs/Pitfalls/global/common-tails.md` |
| ⑤ | `_README.md` T-4 / §六 / 变更记录同步 | `docs/DRIFT/global/_README.md` |
| ⑥ | 交接 #10 状态：✅ **已确证**（第七轮）⇒ **仍未决 4 → 3 条** | `docs/DRIFT/global/_README.md` |

---

## 七、验证

- [x] 2 个探针实跑（真 Tomcat 11.0.24 嵌入式 + 裸 socket RST）
- [x] Tomcat 源码逐行取证（HTTP/2 `RST_STREAM` → `AsyncListener.onError` 完整链路）
- [x] Go 侧协议核对（全仓无 TLS / 无 http2 / 无 h2c ⇒ 明文 HTTP/1.1）
- [x] Spring 官方 helper 核对（`DisconnectedClientHelper` javadoc）
- [x] 三档口径一致（DRIFT / Intentional_Deviations / Pitfalls 互相只链接、不搬内容）

---

## 八、边界

- **未改任何业务代码** ⇒ 无 wire 行为变化。
- **未签字** ⇒ 签字权在用户。
- **未碰共享面**（三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`）。
- **未碰 favorite**。
