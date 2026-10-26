# global 第二十三轮 —— T-11 残留：采样比例（`Telemetry.Sampler`）与 batcher（`Telemetry.Batcher`）补齐 + **一处错账订正**

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`25f6837`（第二十二轮 T-13 收口）
> **性质**：**共享层（`service/common`）的补齐 + 一条自曝错账的订正**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**）

---

## 一、要解决什么

第二十一轮（T-11）落地后，台账留了 5 条「未证成」。本轮收掉其中**两条**（③ 采样比例 ④ 协议），
并且在复查时**抓到第二十一轮自己的一处错账**。

### 🔴 错账：§9.5 ④ 写的是「非 grpc 的 protocol ⇒ 抛错」，**代码里并没有这层校验**

第二十一轮台账原文：

> ④ | **协议只支持 `grpc`** | 与 Go 的 `Batcher: otlpgrpc` 一致；非 grpc 的 `protocol` ⇒ **抛错**（不静默降级）

⇒ **复查代码时发现**：`GoStyleOtelAgent.buildProvider(...)` **压根没读 `config.protocol()`**
⇒ 任何协议值都被**静默当成 grpc** ⇒ **那是真缺陷，不是文档问题**。

⇒ 这正是 AGENTS.md **§3.3.2** 点名的形态：「以『Go: …』形式写的断言，本身是高危项」——
本轮**自曝**一条，不是被别人抓的。

### 🔴 第二个坑：两侧的**协议值字面量就不一样**

| 侧 | 位置 | 值 |
|---|---|---|
| Go | `core/trace/config.go:12` `Batcher string` | **`otlpgrpc`**（合法集合 `zipkin\|otlpgrpc\|otlphttp\|file`） |
| RAG | 10 个 api 的 yaml `otel.exporter.otlp.protocol` | **`grpc`** |

⇒ 字面量差一个 `otlp` 前缀 ⇒ **不显式映射就会把合法值判成非法值**
⇒ ⇒ 本轮第一版实现就是这么写错的 ⇒ **被自己的新门当场抓住**（合法值被判 `unknown exporter: grpc`）。

---

## 二、Go 出处（逐条）→ RAG 落点

| Go 位置 | 语义 | RAG 落点（修后） |
|---|---|---|
| `core/trace/config.go:9` `Sampler float64` | 采样是**比例**（默认 1.0） | `GoStyleOtelConfig.toRatio(...)` 把 Boot 的采样器名字映射成比例 |
| `core/trace/agent.go:111` `sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Sampler))` | **永远** ParentBased ⇒ 入向 parent 不采样 ⇒ 子 span 也不采样 | `Sampler.parentBased(Sampler.traceIdRatioBased(ratio))`（**不变**，本轮补的是它的守门） |
| `core/trace/config.go:12` `options=zipkin\|otlpgrpc\|otlphttp\|file` | Batcher 的**合法集合** | `GoStyleOtelConfig.RAG_PROTOCOL_TO_GO_BATCHER` + `GO_BATCHERS` |
| `core/trace/agent.go:100-101` `default: return nil, fmt.Errorf("unknown exporter: %s", ...)` | 不认识的 exporter ⇒ **整个 agent 装不上** | `GoStyleOtelConfig.validateBatcher()` ⇒ `IllegalStateException` |
| `core/trace/agent.go:114-121` `if len(c.Endpoint) > 0 { createExporter(c) }` | **端点为空 ⇒ 根本不看 Batcher** | `validateBatcher()` 的 `if (endpoint == null) return;` |
| `core/trace/agent.go:43-52` `tp, err := startAgent(c); if err != nil { logx.Error(err); return }` | 装不上 ⇒ **只记 ERROR，继续启动**（全局仍是 no-op） | `GoStyleOtelAgent.install(...)` catch 住 ⇒ 记 ERROR ⇒ 返回 null ⇒ **异常不逃出去** |

---

## 三、改动（3 个文件）

| # | 文件 | 改动 |
|---|---|---|
| ① | `service/common/.../GoStyleOtelConfig.java` | 新增 `GO_BATCHERS` / `RAG_PROTOCOL_TO_GO_BATCHER` / `IMPLEMENTED_BATCHER` + `validateBatcher()` |
| ② | `service/common/.../GoStyleOtelAgent.java` | `buildProvider` 开头调 `config.validateBatcher()`；`install` 把 `buildProvider` 包进 try/catch ⇒ **失败不阻断启动** |
| ③ | `service/common/src/test/.../GoStyleOtelSamplerProtocolGateTest.java`（**新增**） | 7 条守门 |

### ⚠️ 残余能力缺口（**不是**静默降级，已登记）

Go 支持 `otlphttp` / `zipkin` / `file` 三种 exporter，**本仓只实现了 `otlpgrpc`** ⇒
写这三个值 ⇒ **装不上**（记 ERROR + 继续启动）⇒ **行为上与 Go 的「unknown exporter」路径同构**，
但**能力上不等价**（Go 能装，RAG 不能）⇒ 登记为**已知缺口**，不是缺陷。

---

## 四、守门（7 条，真证据）

`GoStyleOtelSamplerProtocolGateTest`

| # | 守门 | 断言 | Go 出处 |
|---|---|---|---|
| 1 | `ratioZeroMeansNothingIsExported` | `Sampler=0.0` ⇒ 根 span **不 recording**、**不 sampled**、**一个 span 都不出机器**（关停 flush 后 collector 仍是 0） | `agent.go:111` |
| 2 | `parentBasedKeepsSampledParentEvenWhenRatioIsZero` | `Sampler=0.0` + 入向 `traceparent`（flags=01）⇒ 子 span **仍被采样并导出** | `agent.go:111` 的 ParentBased |
| 3 | `samplerNameMapsToGoRatio` | `always_on`⇒1.0 / `always_off`⇒0.0 / `traceidratio`⇒arg / 非法名⇒**炸** | `config.go:9` + `agent.go:111` |
| 4 | `unknownBatcherMustFailInsteadOfSilentlyUsingGrpc` | 非法协议 + 端点非空 ⇒ **抛错**（消息含 `unknown exporter`） | `agent.go:100-101` |
| 5 | `goSupportedButUnimplementedBatcherMustFail` | Go 支持但本仓未实现（`otlphttp`）⇒ **同样抛错**（不许静默降级） | `config.go:12` |
| 6 | `emptyEndpointSkipsBatcherValidation` | 端点为空 ⇒ **不校验**、能装出 provider | `agent.go:114-121` |
| 7 | `installFailureDoesNotBreakStartup` | 装不上 ⇒ `install` **返回 null 不抛** + 「已装配」标志为 false + 全局仍是 no-op | `agent.go:43-52` |

⚠️ **门 1 的一处自我订正**：第一版断言「被 DROP 的 span 的 SpanContext **无效**」⇒ **红了**
⇒ 复查 OTel Java 1.44.0 与 Go SDK 的真实语义：**DROP 决策下 SpanContext 仍有效**（有 trace-id/span-id），
只是 **sampled 标志为 false**（trace flags = `00`）⇒ ⇒ 响应头里**仍会有** `traceparent`（flags=00）。
⇒ 断言改成 `isRecording()==false` + `isSampled()==false` + **一个 span 都不导出**。

---

## 五、变异测试（5 条）

隔离工作树跑（PIT-GLOBAL-020）；三道闸（PIT-GLOBAL-018）；判定正则匹配 surefire 的**全限定名**（PIT-GLOBAL-021）。

| # | 变异 | 期望红点 |
|---|---|---|
| M1 | `buildProvider` 去掉 `validateBatcher()` | 门 4（非法 batcher 不再抛） |
| M2 | `validateBatcher` 去掉「端点为空不校验」 | 门 6 |
| M3 | `install` 不再吞异常 | 门 7 |
| M4 | sampler 去掉 `ParentBased` 包装 | 门 2 |
| M5 | `toRatio` 的 `always_off` 改成 1.0 | 门 3 |

---

## 六、可观察变化（**记账，不静默**）

| # | 变化 | 依据 | 影响面 |
|---|---|---|---|
| ① | **协议值写错 ⇒ 装配失败**（记 ERROR + 继续启动）⇒ 全局 trace **不生效** | Go：`unknown exporter` ⇒ `startAgent` 返错 ⇒ 只 `logx.Error` | 本仓 20 个模块的 yaml 现在只写 `grpc` ⇒ **今天零行为变化**；写错值时**不再静默当 grpc** |
| ② | **`Sampler` 的非 1.0 取值现在真的生效** | Go：`Telemetry.Sampler` 是比例 | 本仓 yaml 全是 `always_on`/`parentbased_always_on`（⇒ 比例 1.0）⇒ **今天零行为变化**；但**以后**改 yaml 就能真的改采样率（此前是「配了也不生效」的哑配置） |

⇒ ⚠️ **favorite 侧**：favorite 的 yaml 写的是 `otel.exporter.otlp.protocol: grpc` + `otel.traces.sampler: parentbased_always_on`
⇒ **两个值都合法** ⇒ **零行为变化** ⇒ 但仍按协议 §六 记账并告知 owner。

---

## 七、未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **Go 侧未活体对拍** | Go 事实全部来自**逐行读源码**（`core/trace/{config,agent}.go`）⇒ 没有真跑 Go 进程 |
| ② | **真 jaeger 未接触** | 守门用**自制假 OTLP 接收端** ⇒ 证的是「OTLP/gRPC 字节真的出了机器」，**不是**「jaeger 真的收到了」 |
| ③ | **多进程下的 agent 行为未验** | Go 的 `sync.Once` 是**进程内**语义 |
| ④ | **`otlphttp` / `zipkin` / `file` 三种 exporter 未实现**（能力缺口） | 本轮只登记，未实现（实现它们要引入新依赖 ⇒ 超出本轮 scope） |

### ✅ 本轮**关闭**的上一轮未证成项

| 项 | 结论 |
|---|---|
| **T-13 未证成 ①「`103 Early Hints` 触发点未定位」** | ✅ **已定位**：是 `GoStyleClientDisconnectProbe` 的**主动探测写** —— 它用 `SocketWrapperBase.write + flush` **直接写 socket**（绕过 Tomcat `OutputBuffer` ⇒ 不提交响应），写的就是 `HTTP/1.1 103 Early Hints\r\n\r\n` ⇒ **正是已签字偏离 `SD-2026-10-25-04`**（「响应流最前面多出 N 行 103」）⇒ **不是新缺陷** ⇒ 本轮关闭 |
| **T-11 未证成 ③「采样比例只覆盖 1.0」** | ✅ **已证**（门 1/2/3） |
| **T-11 未证成 ④「协议只支持 grpc」** | ✅ **已证 + 修好**（门 4/5/6/7；且订正了「代码里没抛」这条错账） |

---

## 八、交接整合者

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-11 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | **订正共享台账的过期行**（详见 §九） | 同上 |
| ③ | 把**本轮变异脚本**落仓到 `tools/difftest/`（建议名 `otel-sampler-mutation.py`） | `tools/` **不在 L1 白名单** ⇒ 只留在会话临时目录 `/tmp/mutation-round23.py`；§五 已把 5 条变异的锚点与期望红点写全 |
| ④ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |

---

## 九、⚠️ 共享台账的**过期行**清单（供整合者订正）

`_README.md` 与 `cross-domain.md` / `common-tails.md` 之间存在**同一件事两种状态**的情况 ⇒ 整合者 fold 时请按后者：

| 文件 | 行 | 现状（**过期**） | 应为 |
|---|---|---|---|
| `global/_README.md` | §五 跨域不变量表「建表责任交叉 · client 契约」 | 「**均未审**」 | ✅ **第九轮已审完**（`cross-domain.md` §二·九：交叉但无冲突 · client 契约 10 域零漂移） |
| `global/_README.md` | §五「社区消费者差异」 | 「⏸ 仍未决」 | ✅ **第九轮已确证 + 第二十轮 like 半边已落地**（favorite 半边永久阻塞） |
| `global/_README.md` | §六 #9「社区消费者」 | 「⏸ 仍未决」 | 同上 |
| `global/_README.md` | §七之二 **A5**（X-4 like 半边） | 「🟡 可修」 | ✅ **第二十轮已落地** |
| `global/_README.md` | §七之二 **B4**（X-4 like 半边等价性判定） | 「❓ 未知项」 | ✅ **第二十轮已落地**（4 条契约对拍 + 22 条守门 + 变异 14/14） |
| `global/common-tails.md` | §二 汇总表 **T-11** | 「🔴 未修」 | ✅ **第二十一轮已落地**（全局 OTel SDK） |
| `global/common-tails.md` | §二 汇总表 **T-12** | 「🔴 未修」 | ⏸ **仍未修**（落点 = 出口层级差异 ⇒ 共享层改不动 ⇒ 交 L5 定性为签字候选） |
| `global/common-tails.md` | §一·T-11 的「未修」标注 | 「未修」 | ✅ 已修（同上） |
| `global/_README.md` | §七之二 **A7**（O-1） | 「🟡 可选优化」 | ⛔ **已签字「保持不变」⇒ 不是待办**（用户 2026-10-25 06:40） |
| `global/_README.md` | §七之二 整表 | 「2026-10-25 06:40 的快照」 | ⇒ **T-11 / T-12 / T-13 三条根本没进这张表**（T-13 是第二十二轮新增） |
