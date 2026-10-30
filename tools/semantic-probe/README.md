# semantic-probe：Go ↔ Spring Boot 解析语义双侧探针

AGENTS.md §3.3 要求的「**两侧同时跑的探针**」的落盘实现：

> 凡写「与 Go 一致」，必须有两侧同时跑的探针（不是只跑 RAG）。
> 最小形态：Go 侧直接调用真源的解析包，RAG 侧用项目**真实的** mapper（不是自己 new 一个）。

## 定位：是回归守门，不是缺陷清单

⚠️ **当前 56 例（一致 54 · 已签字偏离 2 · 不一致 0）是「预期基线」，不是「现在还有 56 个 bug」。**
（这 56 个数字是**脚本实跑出来的**，不是写死的 ⇒ 见下面「怎么复核这个数字」。）

`DRIFT-MSGAPI-001` / `-002`（`service/message/message-api` 的 `,string` 语义漂移）已于 2026-10-22 修完。
本工具的价值在**往后**：任何人把 DTO 的 `,string` setter 改回 `String` 类型，`run-parity.sh` 会立刻 exit 1
并指出不一致的行。已做过变异验证（把 setter 改成 `String` ⇒ 2 行变红、exit 1）。

## 怎么跑

```bash
bash tools/semantic-probe/run-parity.sh     # 从任意 cwd 都可以
echo $?                                      # 0 = 全一致，1 = 有漂移
```

依赖：`mvn`（离线 `-o`）、JDK、Go toolchain + 本地 module cache 里的 **go-zero v1.10.2**。
Go 侧以 `GOPROXY=off` 运行，缺依赖直接报错，**不会**悄悄联网。

**它没有被挂进 `mvn test`**：需要 Go toolchain 与 module cache，进 CI 等于给流水线引入新依赖。
它是独立脚本，由人（或后续 agent）在改了 message-api DTO / coercion 之后手动跑。

## 结构

| 文件 | 角色 |
|---|---|
| `go/main.go` | Go 半边：直接调 `go-zero/core/mapping.UnmarshalJsonReader` |
| `go/go.mod` `go/go.sum` | 照抄实测可用版本（`go 1.24.0` + 完整 indirect 列表）。**不要**改成 `go 1.21`、**不要**删 indirect、**不要**联网跑 `go mod tidy`（离线会报缺依赖） |
| `java/Probe.java` | RAG 半边：真实 DTO + 真实 `MessageGoStyleCoercion` |
| `run-parity.sh` | 编译两侧 → 跑两侧 → 按用例名配对 → 打印对比表 → 不一致则 exit 1 |

Go 真源：`Sea-RideTheWind_golang/service/message/api/internal/types/types.go`
（`go/main.go` 里的 struct 必须与它**逐字段**对齐，漏字段＝漏用例，见「已知边界」）。

## 判定标准

只看 **Go 是否 ACCEPTED 与 RAG 是否 ACCEPTED 是否相同**。**原因文本不比** ——
两侧天生不同，例如「required 显式 null」：Go 是 `mustn't be nil`、RAG 是 `is not set`。
表里仍然打印原因（截断到 46 字符），只是**不参与判定**，方便漂移复现时定位。

Go 侧首行是 banner（不含 `|`），脚本在配对前会过滤掉所有不含 `|` 的行 —— 不过滤计数会变成 57 vs 56。

## 用例清单（56 例）

### 怎么复核这个数字（**别信本文档，信脚本**）

```bash
bash tools/semantic-probe/run-parity.sh     # 末行应打印：
#   总计 56 例：一致 54，不一致 0，已签字偏离 2
```

⇒ **56 / 54 / 0 / 2 这四个数是脚本实跑出来的**，不是本文档写死的 ⇒
⇒ 本文档与脚本不一致时，**以脚本为准，并顺手把本文档改过来**（本节就是为此存在的）。

### 原有 6 组（23 例）

### `,string` int64 请求字段（6 例 · `ConversationMessagesReq.conversation_id`）

测的是 Go 的 `,string` **入参宽松、出参严格**：
`encoding/json` 的 `type Number string` ⇒ `json.Number` 的 `reflect.Kind()` 是 `string` 而不是 `number`
（真源 `core/mapping/unmarshaler.go:889-894`），于是 JSON **数字也被接受**。

| 用例 | Go | RAG |
|---|---|---|
| 收 JSON 字符串 `"123"` | 接受 | 接受 |
| **收 JSON 数字 `123`** | **接受** | **接受** ← 这就是 DRIFT-MSGAPI-001，修复前 RAG 拒绝 |
| 收 JSON 数字 `1.5` | 拒绝（ParseInt） | 拒绝 |
| 收 JSON 数字 `1.0` | 拒绝（字面量 `"1.0"`，**哪怕数值上是整数也拒**） | 拒绝 |
| 收 JSON bool `true` | 拒绝（`not string, but bool`） | 拒绝 |
| 收超范围大整数 | 拒绝（range） | 拒绝 |

### 无 `,string` 的字段（6 例 · `PageReq`）

Jackson 默认允许标量强转，Go 直接 `type mismatch`（`core/mapping/unmarshaler.go:643-662`）。
`offset` 4 例 + 新补的 `limit` 2 例（字符串 `"20"` 拒绝 / 数字 `20` 接受）。

### String 字段 / 未知字段（2 例）

`content` 收 JSON 数字 ⇒ 两侧拒绝；未知字段 `nope` ⇒ 两侧忽略。

### 必填校验（3 例）

`conversation_id` 缺席 / 显式 `null` / `content` 缺席 ⇒ 两侧拒绝。

### `extra` 的显式 null（2 例 · `AdminSendNotificationReq`）

`,optional`（不是 `,omitempty`）⇒ 显式 `null` **放行**。这曾差点照抄 users-common 里
`extra_info` 的反向结论写错 —— 见 DTO 类注释的取证。

### admin 通知的 `,string,optional`（4 例）

`broadcast` / `kind` 收 JSON 字符串 ⇒ 拒绝；新补的 **`target_id`** 两个方向
（`"9"` 接受 / `9` 接受）⇒ 验证 `,string` 与 `,optional` 组合时的语义。

### A 组 · bool 字段收 JSON 数字（**7 例** · `unread_only` / `broadcast`）

Go 侧 `type mismatch for field "unread_only", expect "bool"` ⇒ 两侧都**拒绝**
（RAG 侧是 Jackson 的「不能把 Integer 转成 boolean」）。

| 用例 | Go | RAG |
|---|---|---|
| `unread_only` 收数字 `1` | 拒绝 | 拒绝 |
| `unread_only` 收数字 `0` | 拒绝 | 拒绝 |
| `unread_only` 收数字 `2` | 拒绝 | 拒绝 |
| `unread_only` 收数字 `1.0` | 拒绝 | 拒绝 |
| `unread_only` 收数字 `1.5` | 拒绝 | 拒绝 |
| `broadcast` 收数字 `1` | 拒绝 | 拒绝 |
| `broadcast` 收数字 `0` | 拒绝 | 拒绝 |

⚠️ **编号跳过 A6** —— A6 在探针源码（`go/` 与 `java/` 两半）里**根本不存在**
⇒ 是**编号留了坑**，**不是**有用例被静默丢掉。

### B 组 · `extra` map 的值/键形态（**10 例** · `AdminSendNotificationReq.extra`）

| 用例 | Go | RAG |
|---|---|---|
| 值收 JSON 数字 `1` | 接受 `{k=1}` | 接受 `{k=1}` |
| 值收 JSON 数字 `1.5` | 接受 `{k=1.5}` | 接受 `{k=1.5}` |
| 值收 JSON 数字 `1.0` | 接受 `{k=1.0}` | 接受 `{k=1.0}` |
| 值收 JSON 数字 `1e2` | 接受 `{k=1e2}` | 接受 `{k=1e2}` |
| 值收 JSON 空串 | 接受 `{k=}` | 接受 `{k=}` |
| **键**收 JSON 数字 | 接受 `{1=v}` | 接受 `{1=v}` |
| **值收显式 `null`** | 拒绝 | 拒绝 |
| 值收 JSON bool | 拒绝 | 拒绝 |
| 值收 JSON 数组 | 拒绝 | 拒绝 |
| **整体**收 JSON 字符串 | 拒绝 | 拒绝 |

⚠️ **别把两件事混起来**：`extra` **整体**收显式 `null` ⇒ 两侧**放行**（见上面「`extra` 的显式 null（2 例）」）；
而 `extra` 的**元素**（值）收显式 `null` ⇒ 两侧**拒绝** ⇒ `map[string]string` 的元素不接受 `null`。

### C 组 · 显式 `null` 与 int32 边界（**8 例**）

| 用例 | Go | RAG |
|---|---|---|
| `offset` 显式 `null` | 接受 `offset=0` | 接受 `offset=0` |
| `limit` 显式 `null` | 接受 `limit=0` | 接受 `limit=0` |
| `unread_only` 显式 `null` | 接受 `unread_only=false` | 接受 `unread_only=false` |
| `kind` 显式 `null` | 接受 `kind=0` | 接受 `kind=0` |
| `,string,optional` 的 `target_id` 显式 `null` | 接受（`target_id_present=false`） | 接受（同） |
| `offset` 收 int32 上限 `2147483647` | 接受 | 接受 |
| **`offset` 收 int32 上限+1** | 拒绝 | 拒绝 |
| `offset` 收负数 `-1` | 接受 | 接受 |

### D 组 · `,string` 的字面量形态（**6 例**）

| 用例 | Go | RAG |
|---|---|---|
| 收 `"+123"` | 接受 `123` | 接受 `123` |
| 收 `"0012"` | 接受 `12` | 接受 `12` |
| 收 `" 12"`（带前导空格） | 拒绝 | 拒绝 |
| 收空串 `""` | 拒绝 | 拒绝 |
| 收 `"0x10"` | 拒绝 | 拒绝 |
| 收 `1e2` | 拒绝 | 拒绝 |

### X 组 · 出参 `,string`（**2 例** · **已签字偏离** `SD-2026-10-23-03`）

Go 的 tag **无** `,string` ⇒ 输出**数字**；RAG 按签字结论输出**字符串**
⇒ ⇒ 这 2 例是「**预期差异**，不计入失败」，但**仍会逐条列在输出里**（便于漂移复现时定位）。
`X2`（零值形态）专门确认「**不是只有非零才转字符串**」。

## 踩过的坑（改动前务必先读）

**① `StrOnlyReq` 与 `StrReq` 必须分成两个 struct。**
Go 侧的 `,string` 用例必须用**不含必填字段**的 struct。第一版合成一个 struct（同时含
`conversation_id` 与必填的 `content`），必填校验污染了 `,string` 用例 ⇒ 得到 **2/19 假不一致**。
`,string` 宽松这件事只在「字段本身不缺席」时才看得见。

**② RAG 侧反序列化之后必须调 `assertGoRequiredFields()`。**
go-zero 的必填校验在 `UnmarshalJsonReader` **内部**一次完成；RAG 侧放在 Controller 层
（`GoStyleRequiredFields`）。漏掉这步 ⇒ 所有「必填缺席」类用例在 RAG 侧**假通过**，
探针永远绿、守门彻底失效。

## 环境坑

`dependency:build-classpath` 会把 `users-common` 解析成 `~/.m2/.../users-common-0.0.1-SNAPSHOT.jar`
（**旧构建产物**）。而 `GoStyleRequired` / `GoStyleRequiredFields` / `GoRequiredFieldException`
都在那个 jar 里 ⇒ 不处理的话**可能验证的是旧实现**，而且不会报错。

处理方式：`run-parity.sh` 把 `service/users/users-common/target/classes` **前置**到 classpath 最前面。
脚本会在该目录缺失时直接 exit 2 并提示先 `mvn -o -pl service/users/users-common install`。

## 已知边界（本探针**不**覆盖的东西）

**字段覆盖缺口：**

- `AdminSendChatReq` / `AdminSendChatReq.recipient_id`、`MarkConversationReadReq`、
  `MarkNotificationReq.notification_id`、`UserSendChatReq.recipient_id` /
  `conversation_id` —— 这些 `,string` 字段**没有被单独用例覆盖**。
  它们走的是同一套 `GoStyleStringInt64` + 同一份 coercion，所以同类回归仍会被
  `conversation_id` / `target_id` 两组用例抓到；但**逐字段的完整回归不算覆盖**。
- 出参方向的 `,string`（响应序列化成字符串）**未覆盖** —— 本探针只测请求解析。
- `map[string]string` 的**键/值类型强转**、嵌套结构体、数组 —— 未覆盖。

**层次边界：**

- **响应头层不在范围内**（`Content-Type` / `nosniff` / 404 / 405 那些）。
  那些属于 `tools/difftest/` 真容器 curl 对拍的职责 —— MockMvc 测不到，见 AGENTS.md §3.3 的
  「MockMvc vs 真实 Tomcat」那条。
- **请求保护层不在范围内**（`MaxBytes` 413 / `CpuThreshold` 503 / `Breaker` 熔断 / `MaxConns`）。
- **业务逻辑与身份解析层不在范围内**（越界检查、空 id 组合语义、内容长度 —— 那些在 message-rpc）。
- 本探针只断言 **ACCEPTED / REJECTED**，不比对解析出来的**值**是否相同。
  两侧都接受同一输入但解析出不同数值（例如精度截断）本探针查不出来。

## 通用档位设计规则（**探针能不能验到修法**的前提，不是可选参数）

> 🔴 **第四十二轮补记**（users/admin 域 owner 提出 ⇒ global 采纳并推广）：
> 这条**不只属于 `rpc-timeout/`** —— 凡探针要验「两侧是否都做了 `min(客户端, 服务端)`」这类语义，
> **档位选错 ⇒ 探针恒绿 ⇒ 修没修都看不出来**。

**规则**：验「`min(客户端, 服务端)` 是否被继承」时，**客户端 deadline 必须严格早于服务端 timeout**。

- 若客户端 deadline **晚于**服务端 timeout ⇒ `min` 的结果**恒等于服务端值** ⇒
  **两侧算出来的 deadline 完全一样** ⇒ ⇒ 探针**分不出差别** ⇒ ⇒ **探针恒绿**（假绿）。
- ⇒ 探针**必须**成对给两档：
  - **短档**：客户端 deadline **<** 服务端 timeout
    （⇒ 两侧若都继承 `min`，短档两侧应当**一致**，且都 ≈ 客户端那一档）；
  - **长档（对照组）**：客户端 deadline **>** 服务端 timeout
    （⇒ 两侧都 ≈ 服务端预算 ⇒ ⇒ **证明差异只来自「是否继承」**，而不是别的什么）。
- ⇒ **短档与长档必须成对** ⇒ 只给短档 ⇒ 无法区分「差异来自继承」与「环境不可靠」。

⚠️ **反例（本仓真实踩过两次）**：`tools/difftest/exp-grpc-admin-deadline-probe.sh`
- 第四十轮：短档默认曾是 **1000ms**，而判定阈值也是 **1000ms** ⇒ 「RAG 不继承」时的信号差**恰好 ≈1000ms**
  ⇒ **卡在阈值边上** ⇒ **假绿** ⇒ 改 300ms；
- 第四十一轮（**实测校准**）：改成 300ms 后**实测信号只有 833ms**（不是推算的 1700ms）
  ⇒ ⇒ **档位/阈值的选择必须用实测值校准，不能用推算值** ——
  推算值依赖「SQL 超时是否已继承客户端 deadline」这个**尚未验证**的前提（当时它**不成立**：走的是秒级回退 ⇒ 见 E62）。

## 改动本工具时的守则

- 两侧的**用例名必须逐字一致**（含空格与引号）—— 脚本按 `|` 之前的文本配对。
  少一侧的用例会被报成 `<<缺失>>` 并 exit 1（这条分支也做过验证）。
- Go struct 必须逐字段对齐真源 `types.go`，新增字段就要新增用例。
- **不要**在本文件里写「与 Go 一致」却只跑 RAG 侧 —— 那正是 AGENTS.md §3.3 要防的事。
---

# 第二个探针：`trace/`（Go ↔ RAG 的 **trace 链路**活体对拍）

> 位置：`tools/semantic-probe/trace/` · 跑法：`bash tools/semantic-probe/trace/run-trace-parity.sh`
> 退出码：`0` = 两侧一致 · `1` = 有漂移 · `2` = 环境不满足

## 为什么要有它（AGENTS.md §3.3 规则 2 的**第二个**落地）

T-11（全局 OpenTelemetry）落地后，RAG 侧有守门（`GoStyleOtelSamplerProtocolGateTest` 等），
但 Go 侧**只有「逐行读源码」** ⇒ 而 §3.3 明确要求：

> 凡写「与 Go 一致」，必须有**两侧同时跑的探针**（不是只跑 RAG）。

⇒ 本探针把 **go-zero 的真源跑起来**，与 RAG 侧的**生产类**逐条对拍。

## 结构

| 文件 | 角色 |
|---|---|
| `trace/go/liveprobe_test.go` | **Go 半边**：**包内**测试（`package trace`）⇒ 能调未导出的 `createExporter` / `startAgent` |
| `trace/java/TraceProbe.java` | **RAG 半边**：只调**生产类** `GoStyleOtelAgent` / `GoStyleOtelConfig`（不复制装配逻辑） |
| `trace/run-trace-parity.sh` | 跑两侧 → 按用例名配对 → 打印对比表 → 不一致则 exit 1 |

## 三条纪律（都是踩出来的）

**① Go 半边必须进包内（`package trace`）。**
`createExporter` / `startAgent` 是**未导出**的 ⇒ 外部包调不到 ⇒
「unknown exporter」「端点为空不校验」「四种 exporter 都能构造」这三条**只能**靠包内测试活体证。

**② 模块缓存是只读的，且 `go -overlay` 不允许替换 `GOMODCACHE` 下的文件。**
实测报错：`Files beneath GOMODCACHE must not be replaced`
⇒ 做法：把缓存里的 go-zero **整份拷到临时目录** → 放入包内探针 → `go test ./core/trace/`。
⇒ **拷完必须逐字节校验**（`diff -r -x liveprobe_test.go`）⇒ 确认「跑的就是缓存里那份真源」。

**③ 「只输出稳定值」。**
trace-id 是**随机**的 ⇒ 探针只输出布尔 / 枚举（`parent_trace_id_inherited=true`），
**不输出原始 id** ⇒ 否则两侧永远对不上。

## 用例（11 条）

| 用例 | 测什么 | Go 出处 |
|---|---|---|
| `G1_startAgent_failure` | 装配失败 ⇒ 只记 ERROR、**不 panic**、全局仍是 no-op | `core/trace/agent.go:43-52` |
| `G2_unknown_exporter` | 非法 batcher + 端点非空 ⇒ 抛 `unknown exporter` | `core/trace/agent.go:100-101` |
| `G3_empty_endpoint` | 非法 batcher + **端点为空** ⇒ **不报错** | `core/trace/agent.go:115-124` |
| `G4_ratio_zero` | `Sampler=0.0` ⇒ 不 recording / 不 sampled，但 **SpanContext 仍有效** | `core/trace/agent.go:110` + `config.go:10` |
| `G5_parentbased_sampled_parent` | `Sampler=0.0` + 入向 **sampled** parent ⇒ 子 span 仍采样 | `core/trace/agent.go:110` 的 ParentBased |
| `G6_parentbased_unsampled_parent` | `Sampler=1.0` + 入向 **未采样** parent ⇒ 子 span **不**采样 | 同上（`remoteParentNotSampled ⇒ AlwaysOff`） |
| `G7_exporter_{otlpgrpc,otlphttp,zipkin,file}` | Go 的**四种** batcher 都能构造 | `core/trace/agent.go:60-103` |
| `G8_propagation` | 全局传播器 = Composite(TraceContext, Baggage) | `core/trace/propagation.go:8-11` |

### 🔴 G6 是本探针**挖出来的缺口**

RAG 侧的 `GoStyleTraceparentFilter` 类注释里写着「入向 parent 不采样时子 span 也不采样」，
但**此前没有任何守门** ⇒ 那是一条**写在注释里、没有运行期证据**的断言（AGENTS.md §3.3.2）。
⇒ 本探针用 Go 真源活体确认了这条语义 ⇒ 随后在 RAG 侧补了对应的 JUnit 守门
（`GoStyleOtelSamplerProtocolGateTest#remoteParentNotSampledDropsChildSpanEvenWhenRatioIsOne`）。

## 已知能力缺口（不计入失败，但**始终打印**）

RAG 只实现了 `otlpgrpc`；Go 还支持 `otlphttp` / `zipkin` / `file`
⇒ 这三行**必然**不一致 ⇒ 放在脚本里的 `KNOWN_GAP` 白名单
⇒ 标成「预期差异」，**不计入失败**，但**始终打印出来**，让人看得见它还在那儿。

> ⚠️ 加白名单是**有后果的动作**：它等于承认「这条差异不修」。
> ⇒ 每条都必须能在 global 台账里找到出处；没有出处的**不许**进来。

## 已知边界

- **不是完整进程**：Go 半边只跑 `core/trace` 这个包 ⇒ 不起 HTTP server、不起 Kafka/Nacos。
- **不比对 span 的导出字节**：只比对**采样决策 / 装配结果 / 传播器字段**；
  「OTLP 字节真的出了机器」由 RAG 侧的 `FakeOtlpCollector` 守门负责（真 gRPC 接收端）。
- **不覆盖 Go 侧的 `rest/handler/tracehandler.go`**（HTTP 层的 span 命名 / 属性）——
  那属于响应头层，RAG 侧由 `GoStyleTraceparentFilter*` 系列守门负责。
- **Go 侧 OTel SDK 是 1.40.0，RAG 侧是 1.44.0** ⇒ 版本不同 ⇒ 探针证的是**语义**，
  不是「逐字节同版本」。

---

# 第三个探针：`rpc-timeout/`（Go ↔ RAG 的 **gRPC 超时路径**活体对拍）

> 位置：`tools/semantic-probe/rpc-timeout/` · 跑法：`bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh`
> 退出码：`0` = 两侧一致 · `1` = 有漂移 · `2` = 环境不满足

## 为什么要有它（AGENTS.md §3.3 规则 2 的**第三个**落地）

E30：Go 真源 `service/common/observability/rpc.go:69` 在服务端超时时给客户端的
status description 是 **`context deadline exceeded`**；
RAG 旧实现写的是 `rpc timeout after 2000ms`
⇒ **客户端拿到的错误文案与 Go 不一致**（wire 面差异，客户可见）。

前两个探针证的是「解析」与「装配」；本探针证的是**超时语义在真实网络栈上的落地**。

## 结构

| 文件 | 角色 |
|---|---|
| `rpc-timeout/go/main.go` | **Go 半边**：真 gRPC server（grpc-go）+ 冻结仓**真实生成**的 `pb.LikeService` + 真 client stub ⇒ 走真实网络栈 |
| `rpc-timeout/java/TimeoutProbe.java` | **RAG 半边**：只调**生产类** `RpcInterceptor`（真 Netty server + 真 channel） |
| `rpc-timeout/run-rpc-timeout-parity.sh` | 跑两侧 → 按用例名配对 → 逐键比对 → 不一致则 exit 1 |

## 三条纪律（都是踩出来的）

**① Go 半边用 `replace` 直接引用冻结仓，不复制、不改写。**
冻结仓只读 ⇒ 探针不能往里写文件 ⇒ 用独立 module + `replace sea-try-go => <冻结仓>`
直接**引用**它 ⇒ 跑的就是冻结仓那份源码。
⚠️ 冻结仓路径是**机器相关**的 ⇒ `go.mod` 由脚本**运行时生成**，仓库里只放 `main.go`
（**不提交任何绝对路径**；可用环境变量 `GO_TRUTH_REPO` 覆盖默认路径）。

**② `CGO_ENABLED=0`。**
本机没有 Xcode 头文件 ⇒ cgo 会报 `'stdlib.h' file not found` ⇒ 与探针无关的失败。
另外 `GOPROXY=off` / `GOSUMDB=off` / `GOPRIVATE='*'` ⇒ 缺依赖直接报错，不悄悄联网。

**③ Go 的 select 有三个分支 ⇒ 慢 handler 必须**永不返回**。**
Go 真源 `rpc.go:59-72` 的 select 分支是 `done` / `panicChan` / `timeoutCtx.Done()`。
若 handler 在 ctx.Done() 时就返回，则 `done` 分支与超时分支**同时就绪**
⇒ Go 的 select **随机选** ⇒ 可能返回 `context.DeadlineExceeded`（**非 status error**）
⇒ 客户端拿到 `code=Unknown` ⇒ **探针变成随机**。
真实的慢 handler 就是一直不返回 ⇒ 这里照做（`time.Sleep(30s)`）。

## 用例（3 条）

| 用例 | 测什么 | Go 出处 |
|---|---|---|
| `timeout_wire` | **客户端真正收到的** status：code + description | `service/common/observability/rpc.go:69` |
| `timeout_telemetry` | 超时 span 的 `sea.biz_msg`（= `err.Error()`） | `service/common/observability/observability.go:108-112` |
| `client_deadline_ctx` | **E38**：客户端 deadline 早于服务端 timeout 时，**有效期限**必须取客户端的那个 | `service/common/observability/rpc.go:38` |

两侧参数一致（超时 300ms · slow 阈值 750ms），两侧都走**真传输层**：
Go 侧 `grpc.NewServer` + 真 client；RAG 侧真 Netty server（`forPort(0)`）+
真 channel（`dns:///127.0.0.1:<port>` 直连，绕开 Nacos 服务发现）。

### 🔴 `client_deadline_ctx` 是本探针**第二个**挖出来的缺口（E38）

Go 真源 `rpc.go:38` 的 `context.WithTimeout(ctx, timeout)` 里 **`ctx` 携带客户端 deadline**
⇒ Go 标准库语义 ⇒ 有效期限 = **min(客户端 deadline, 服务端 timeout)**；
而 RAG 旧实现只算 `now + timeout` ⇒ **客户端 deadline 完全不参与**。

⇒ 探针实测（客户端 deadline 100ms / 服务端 300ms）：
- Go 半边：`bucket=client_100ms`（handler 看到的 ctx 剩 ≈100ms ⇒ **继承了客户端的**）
- RAG 半边（修前）：`bucket=server_own`（剩 ≈300ms ⇒ **只算服务端自己的**）
⇒ ⇒ **漂移坐实**，探针 EXIT=1；修后两侧一致。

⚠️ **档位设计的一条纪律**：客户端 deadline **必须早于**服务端 timeout ⇒ 否则 `min` 等于服务端值
⇒ 两侧看不出差别 ⇒ 探针失效（本轮踩过一次：Go 半边最初把客户端设成 1000ms、服务端 300ms
⇒ `min` = 300ms ⇒ 与「只算服务端」不可区分 ⇒ 改成客户端 100ms 才分得开）。

⚠️ **只输出分档，不输出绝对毫秒**：剩余毫秒受传输耗时影响 ⇒ 两侧逐字比对会不稳定
⇒ 只比对「落在客户端那一档还是服务端那一档」（bucket）⇒ 稳定可配对。

## 已知能力缺口（不计入失败，但**始终打印**）

`timeout_telemetry` 的 **code 段**：Go 是 grpc-go 的 `codes.Code.String()`（CamelCase
`DeadlineExceeded`，`codes/code_string.go:31-59`），RAG 是 grpc-java 的 `Code.name()`
（`DEADLINE_EXCEEDED`）⇒ **这一半已登记为「表述差异 C-1」**
（`docs/Intentional_Deviations/common/_signed-deviations.md` C 类 C-1，写明「不做臆造映射」）
⇒ 进脚本的 `KNOWN_GAP`。

> ⚠️ 加白名单是**有后果的动作**：它等于承认「这条差异不修」。
> ⇒ 每条都必须能在 global 台账里找到出处；没有出处的**不许**进来。
> ⇒ 且 RAG 侧的 JUnit 守门 `RpcInterceptorTimeoutDescriptionGateTest`
> **把 C-1 的形态钉住**（断言 `code = DEADLINE_EXCEEDED`）⇒ 后人不会把它当新漂移「顺手修掉」。

## 已知边界

- **只覆盖服务端超时这一条路径**：不覆盖客户端侧 deadline（`ctx` 超时）、不覆盖重试、
  不覆盖 `panicChan` 分支、不覆盖 stream（只测 unary）。
- **不比对超时时长**：只比对超时**之后**客户端拿到的 code / 文案。
- **Go 侧用 `pb.LikeService`（`service/like/rpc/pb`）**：因为它是冻结仓里**真实生成**的
  ⇒ 避免手搓 `ServiceDesc` 引入偏差；RAG 侧用手搓的最小 `MethodDescriptor`
  ⇒ **两侧服务/方法名不同**（Go `/like.LikeService/GetLikeCount` vs RAG `probe.Timeout/Hang`）
  ⇒ 探针**不比对方法名**。
- **grpc-java 的 `fullMethodName` 不带前导斜杠**，grpc-go 用 `"/service/method"`
  ⇒ 两侧探针各写各的，**不要**互相「对齐」成同一个字符串。

---

# 第四个探针：`client-visible-code/`（Go 侧「客户端可见码**集合**」活体探针）

> 位置：`tools/semantic-probe/client-visible-code/` · 跑法：`bash tools/semantic-probe/client-visible-code/run-client-visible-code.sh`
> 退出码：`0` = 形态与已登记一致（集合含 4 与 13）· `1` = **形态变了**（集合不含 13 ⇒ 前提要回源头重核）· `2` = 环境不满足

## 为什么要有它

它回答的是一个**决定 logout 修法**的问题：**Go 侧在「请求 deadline 已过期」时，客户端到底能看到哪些码。**

- 判据不是「Go 的 handler 级码是什么」（那是 `codes.Internal` = 业务码 5001），
  而是「**客户端能看到什么**」⇒ 因为只有**客户端可见面**才是漂移判定对象。
- 真源 `service/common/observability/rpc.go:38` ⇒ `timeoutCtx` 一创建就是到期的（父 ctx 已过期）
  ⇒ `:59-72` 的 `select` 里 **`done` 与 `timeoutCtx.Done()` 可能同时就绪**
  ⇒ ⇒ **Go 的 `select` 在多个就绪分支间随机选** ⇒ ⇒ 走 `done` 分支（`:63-67`）就**返回 handler 的 err**（5001）。
- ⇒ ⇒ **实测集合 = {4, 13}** ⇒ ⇒ 「handler 级差异不可观察 ⇒ 不构成漂移」**不成立**
  ⇒ ⇒ RAG 保留域内 `checkDeadline()` ⇒ 集合 = {4} = **Go 集合的真子集** ⇒ ⇒ **删掉守卫才同集合**。

## 文件

| 文件 | 角色 |
|---|---|
| `client-visible-code/go/main.go` | **Go 半边**：直调真源 `observability.NewUnaryServerInterceptor`（经 `replace` 引用冻结仓）＋ 真 ctx（已过期）⇒ 统计它返回的码 |
| `client-visible-code/run-client-visible-code.sh` | 生成 `go.mod` → 离线构建 → 跑 → **判形状**（集合里有没有 13）→ 形态变了就 exit 1 |

## 三条纪律

① **只提交 `go/main.go`**；`go.mod` 运行时生成 ⇒ 仓库里不出现机器相关的绝对路径。
② **`CGO_ENABLED=0`**（本机没有 Xcode 头文件）。
③ **期望值必须有台账出处** ⇒ 出处：[`docs/DRIFT/_cross/client-visible-code-set.md`](../../docs/DRIFT/_cross/client-visible-code-set.md)。

## 它是**形状**守门，不是**数值**守门

断言的是「**集合里有没有那个码**」，**不是**精确比例 ⇒ ⇒ 命中比例受调度竞争影响（机器/负载相关）⇒ 不进期望值
⇒ ⇒ **形态变了就红** ⇒ ⇒ 逼人**回源头重核**，而不是把期望值改成新的数。

## ⚠️ 为什么这条证据必须落仓

这个数**曾在一次临时 module 里跑出来**（N=20000 ⇒ 1015=19995 / 5001=5），但**源码没落仓**（只在当时的 `/tmp`）
⇒ ⇒ 「跑某条命令会得到 N」变成**不可复算**的口径 ⇒ ⇒ 谁都没法复核、前提变没变也没法判断。
⇒ ⇒ **纪律**：凡台账里写「跑某条命令会得到 N」，**必须把那条命令 / 那个探针落仓**。

---

# 第三个探针：`redis-deadline/`（Go ↔ RAG 的 **Redis 期限「写面」**活体对拍）

> 位置：`tools/semantic-probe/redis-deadline/` · 跑法：`bash tools/semantic-probe/redis-deadline/run-redis-deadline-parity.sh`
> ⇒ ⇒ 退出码：`0` = 两侧逐键相等 ＋ 形态与 Go 真源已登记基线一致 · `1` = 对拍破或形态变了 · `2` = 环境不满足

## 它补的是哪一格（E66 的**证据缺口**）

E66（Redis 写不绑 deadline）原先是**两侧没有同一个观测量**：

| 半边 | 原状 |
|---|---|
| **Go 侧** | 「靠**读源码**」⇒ `c.rdb.Get(ctx, …)` 吃 ctx ⇒ go-redis 的 `internal/pool.waitTurn(ctx)` 先查 `ctx.Done()` ⇒ 命令不下发 |
| **RAG 侧** | 「靠 **JUnit 守门**」（`GoStyleRedisDeadlineLiveRedisWriteFaceTest`）⇒ 证 RAG 单侧「预算耗尽 ⇒ 不写」 |

⇒ ⇒ **两边各自有证据，却没有一份能逐键对拍的活体证据** ⇒ ⇒ 「同形」只是推演。
⇒ ⇒ 本探针把两侧**拉齐到同一个观测量** ⇒ ⇒ 才是活体同形证据。

## 观测量 = **读回来还是基线值**（不是靠异常类型推断）

1. 活着的 ctx ⇒ 写基线值 `baseline`；
2. **已耗尽**的 ctx ⇒ 再写一次 `overwritten`；
3. **另一条不带预算的独立通道**读回 ⇒ ⇒ **必须还是 `baseline`**
   ⇒ ⇒ 「命令真的没落到 Redis 里」是**读出来的**，不是推断的。

⇒ ⇒ 两侧**同一个观测量** ⇒ ⇒ PROBE 行才可比。

| 文件 | 角色 |
|---|---|
| `redis-deadline/go/main.go` | **Go 半边**：go-redis/v9（版本从**冻结仓 go.mod 钉死**）· 调用形态 = 真源那一行（`rdb.Get(ctx,…)` / `rdb.Set(ctx,…)`）· ⚠️ **不 import 真源的 `internal` 包**（internal ⇒ 外部模块 import 不到 ⇒ ⇒ 复刻**一行形态**、跑**同一套依赖**） |
| `redis-deadline/java/RedisDeadlineProbe.java` | **RAG 半边**：**生产类** `GoStyleRedisDeadlineConnectionFactory` ＋ `GoStyleRequestDeadline` ＋ 真 Lettuce（**不是** mock） |
| `redis-deadline/run-redis-deadline-parity.sh` | 起真 Redis（docker `redis:7-alpine`）⇒ 离线构建 Go 半边（用**冻结仓的 `go.sum`**）⇒ 编译 RAG 半边（`service/common` 生产类 + 依赖 classpath）⇒ 跑两侧 ⇒ **逐键对拍** + 校验形态基线 |

## Go 出处（期望值必须有台账出处）

- `service/comment/rpc/internal/cache/comment_subject_cache.go:76` / `:84`（`c.rdb.Get(ctx, …)`）/ `:103`（`SetSubjectCache(ctx, …)`）
- `service/comment/rpc/internal/cache/comment_invalidation_cache.go:10` / `:37` / `:56`
- ⇒ **每次缓存读写都吃 ctx** ⇒ ⇒ **「预算耗尽 ⇒ 不写」就是 Go 的形态**

## 三条纪律

① **只提交 `go/main.go`**；`go.mod` 运行时生成（用**冻结仓的 `go.sum`** 校验依赖）⇒ 仓库里不出现机器相关的绝对路径。
② **`CGO_ENABLED=0`**（本机没有 Xcode 头文件）· **`GOPROXY=off`**（离线构建）。
③ **两侧产出不足 ⇒ 退出码 2**（那是「没跑」，不是「形态变了」）⇒ ⇒ 不许拿「退出码 0」当「跑过了」。

## ⚠️ 诚实边界

- **RAG 半边是直接构造生产类**（不是走 Spring 容器）⇒ ⇒ 「**BPP 在真实容器里确实包上了**」这一格是**静态核实**（`StringRedisTemplate` ⇒ `RedisTemplate` 子类 ⇒ BPP 自动包装；且**全仓生产码零 `RedisConnectionFactory` 直接注入**）⇒ ⇒ **不是**活体。
- **观测量只覆盖「写面」** ⇒ 「读面」与「连接已建立后再耗尽」是另外两格（各由 `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest` 的另外两例覆盖）。

## ✅ 已做变异测试（否则就是再造一个恒绿假绿）

把生产类 `GoStyleRedisDeadline.assertNotExhausted` 的闸门拆掉（`if (remaining >= 0L && remaining <= 0L)` ⇒ `if (false)`）
⇒ ⇒ RAG 侧立刻翻成 `write_applied=true` / `value_after=overwritten`，Go 侧不变
⇒ ⇒ 对拍**精确红在 `exhausted.write_applied`** ⇒ ⇒ **该探针有牙**
⇒ ⇒ 还原后 **sha256 一致** + 复跑绿。
