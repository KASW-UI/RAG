# global 第二十四轮 —— T-11「Go 侧未活体对拍」闭环 + **探针挖出的新缺口**（门 8）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`71477ff`（第二十三轮收口）
> **性质**：**新增双侧活体探针（工具）** + **一条新守门**（生产代码零改动）
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（**零改动**）

---

## 一、要解决什么

第二十一~二十三轮的台账里，T-11 一直挂着一条**未证成**：

> ① | **Go 侧未活体对拍** | Go 事实全部来自**逐行读源码**（`core/trace/{config,agent}.go`）⇒ 没有真跑 Go 进程

而 AGENTS.md **§3.3 规则 2** 写得很死：

> 凡写「与 Go 一致」，必须有**两侧同时跑的探针**（不是只跑 RAG）。

⇒ 第二十一~二十三轮的 T-11 台账里那些「与 Go 一致」的断言，**当时都缺这一半证据**。

本轮把这一半补上 ⇒ 并**顺手挖出一条 RAG 侧没有守门的语义**。

---

## 二、Go 侧活体探针（怎么跑起来的）

### 2.1 为什么必须进包内

`createExporter` / `startAgent` 是 go-zero `core/trace` 的**未导出**函数 ⇒ 外部包调不到
⇒ 「unknown exporter」「端点为空不校验」「四种 exporter 都能构造」这三条**只能**靠**包内测试**活体证。

### 2.2 为什么是「拷模块」而不是 `go -overlay`

| 方案 | 结果 |
|---|---|
| `go test -overlay` 直接在模块缓存里加包内测试 | ❌ 实测报错 `Files beneath GOMODCACHE must not be replaced` |
| **把 module cache 里的 go-zero 整份拷到临时目录** → 放入包内探针 → `go test ./core/trace/` | ✅ **可用**（且拷完做 `diff -r` **逐字节校验** ⇒ 确认跑的就是缓存里那份真源） |

### 2.3 落点

`tools/semantic-probe/trace/`（**第二个** semantic probe；第一个是 message-api 的 DTO 解析对拍）。

| 文件 | 角色 |
|---|---|
| `trace/go/liveprobe_test.go` | **Go 半边**：`package trace` 的包内测试 |
| `trace/java/TraceProbe.java` | **RAG 半边**：只调**生产类** `GoStyleOtelAgent` / `GoStyleOtelConfig` |
| `trace/run-trace-parity.sh` | 跑两侧 → 按用例名配对 → 打印对比表 → 不一致 exit 1 |

---

## 三、双侧对拍结果（11 个用例）

| 用例 | 测什么 | Go | RAG | 判定 |
|---|---|---|---|---|
| `G1_startAgent_failure` | 装配失败 ⇒ 不 panic、返回 null、全局仍 no-op | ✅ | ✅ | **一致** |
| `G2_unknown_exporter` | 非法 batcher + 端点非空 ⇒ 抛 `unknown exporter` | ✅ | ✅ | **一致** |
| `G3_empty_endpoint` | 非法 batcher + **端点为空** ⇒ 不报错 | ✅ | ✅ | **一致** |
| `G4_ratio_zero` | `Sampler=0.0` ⇒ 不 recording / 不 sampled / **SpanContext 仍有效** | ✅ | ✅ | **一致** |
| `G5_parentbased_sampled_parent` | `Sampler=0.0` + 入向 **sampled** parent ⇒ 子 span 仍采样 | ✅ | ✅ | **一致** |
| `G6_parentbased_unsampled_parent` | `Sampler=1.0` + 入向 **未采样** parent ⇒ 子 span **不**采样 | ✅ | ✅ | **一致**（但 RAG 侧**此前无守门** ⇒ 见 §四） |
| `G7_exporter_otlpgrpc` | OTLP/gRPC | ✅ | ✅ | **一致** |
| `G7_exporter_otlphttp` | OTLP/HTTP | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G7_exporter_zipkin` | Zipkin | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G7_exporter_file` | 文件 | ✅ | ❌ | ⚠️ **已知能力缺口** |
| `G8_propagation` | 全局传播器 = Composite(TraceContext, Baggage) | ✅ | ✅ | **一致** |

⇒ **11 个用例：8 一致 · 3 已知能力缺口 · 0 条漂移**

⚠️ **3 条能力缺口不计入失败，但**始终打印** ⇒ 与 `tools/semantic-probe/run-parity.sh` 的
`known-dev` 白名单同款纪律（加白名单 = 承认「这条差异不修」⇒ 必须有台账出处）。

---

## 四、🔴 探针挖出的缺口：RAG 侧**没有**「入向 parent 未采样 ⇒ 子 span 不采样」这条守门

`GoStyleTraceparentFilter` 的类注释里写着：

> ⇒ 根 span 必采样；**入向 parent 不采样时子 span 也不采样**。

⇒ 但这句话**没有任何运行期证据** ⇒ 正是 AGENTS.md **§3.3.2** 点名的形态
（「以『Go: …』形式写的断言，本身是高危项」）。

⇒ 本轮用 Go 真源**活体**确认了这条语义（Go 的 `sdktrace.ParentBased` 的
`remoteParentNotSampled ⇒ AlwaysOff` 分支）⇒ 随后在 RAG 侧补了对应的 JUnit 守门。

**新增守门**：`GoStyleOtelSamplerProtocolGateTest#remoteParentNotSampledDropsChildSpanEvenWhenRatioIsOne`
（该类 7 → **8** 条）

---

## 五、变异测试（2 条）

隔离工作树（`/tmp/rag-round24-mut`）+ 三道闸（PIT-GLOBAL-018）+ 全限定名判定（PIT-GLOBAL-021）。

| # | 变异 | 期望红点 | 设计要点 |
|---|---|---|---|
| **M6** | `setRemoteParentNotSampled(alwaysOn())` | **只有门 8 红**，门 2 保持绿 | 用 OTel Java 的 `Sampler.parentBasedBuilder(...).setRemoteParentNotSampled(...)` ⇒ **只**改一个分支 ⇒ 证明门 8 守的正是那一个分支 |
| **M7** | 去掉 `parentBased` 包装 | 门 2 红 | 证明「ParentBased 这层包装真的在生效」 |

---

## 六、可观察变化（**记账，不静默**）

| # | 变化 | 影响面 |
|---|---|---|
| ① | **生产代码零改动** ⇒ **零行为变化** | 全仓 |
| ② | **新增一条 JUnit 守门**（门 8）⇒ 今后若有人把 `parentBased` 改成非 ParentBased 变体 ⇒ 红 | `service/common` |
| ③ | **新增一个双侧探针** ⇒ 「T-11 与 Go 一致」这句话从此**有两侧同时跑的证据** | 工具层 |
| ④ | 探针的 `KNOWN_GAP` 白名单里**明写**了 3 条能力缺口 ⇒ 它们**始终可见**，不会被当成「已对齐」 | 工具层 |

---

## 七、favorite 记账（**本轮对 favorite 零文件改动**）

> `service/favorite/**` 与 `docs/*/favorite/**` **零改动**。
> 本轮**生产代码零改动** ⇒ favorite 的可观察行为**零变化** ⇒ 仍按协议 §六记账并告知 owner。

---

## 八、未证成（**不声称已证明的**）

| # | 未证成项 | 原因 |
|---|---|---|
| ① | **真 jaeger 未接触** | 探针只验「装配结果 / 采样决策 / 传播器字段」⇒ **不是**「jaeger 真的收到了 span」 |
| ② | **Go 侧 OTel SDK 是 1.40.0，RAG 侧是 1.44.0** | 版本不同 ⇒ 探针证的是**语义**，不是「逐字节同版本」 |
| ③ | **多进程下的 agent 行为未验** | Go 的 `sync.Once` 是**进程内**语义 |
| ④ | **`otlphttp` / `zipkin` / `file` 三种 exporter 仍未实现** | 能力缺口（Go 能装、RAG 装不上）⇒ **行为同构、能力不等价** |
| ⑤ | **Go 的 `rest/handler/tracehandler.go`（HTTP 层）不在探针范围** | 响应头层由 RAG 侧 `GoStyleTraceparentFilter*` 系列守门负责 |
| ⑥ | **探针不是 CI 门** | 与已有的 `tools/semantic-probe/run-parity.sh` 同款定位：**手动跑**（需要 Go toolchain + module cache） |

---

## 九、交接整合者

| # | 事项 | 为什么本 lane 做不了 |
|---|---|---|
| ① | 把本节 fold 进 `docs/DRIFT/global/common-tails.md` 的 T-11 段 | 共享台账 ⇒ 协议 §二.3 |
| ② | 更新 `AGENTS.md` §0 的 `global` 行 + 三个 `_INDEX.md` + `_MANIFEST.json` | 共享索引 |
| ③ | `tools/semantic-probe/README.md` 已由本 lane 更新（第二个探针的说明）⇒ 整合者**只需**把 `tools/difftest/README.md` 的工具清单补一行 | `tools/difftest/README.md` 是共享文件 |
| ④ | **L2 / L3 / L4 三条 lane 的合并** | 由整合者按 §五 合并纪律执行 |
| ⑤ | `align/global` → `do-my-own-gh` 的合并 | 由 Jason 决定时机 |
