# semantic-probe：Go ↔ Spring Boot 解析语义双侧探针

AGENTS.md §3.3 要求的「**两侧同时跑的探针**」的落盘实现：

> 凡写「与 Go 一致」，必须有两侧同时跑的探针（不是只跑 RAG）。
> 最小形态：Go 侧直接调用真源的解析包，RAG 侧用项目**真实的** mapper（不是自己 new 一个）。

## 定位：是回归守门，不是缺陷清单

⚠️ **当前 23/23 一致是「预期基线」，不是「现在还有 23 个 bug」。**

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

Go 侧首行是 banner（不含 `|`），脚本在配对前会过滤掉所有不含 `|` 的行 —— 不过滤计数会变成 24 vs 23。

## 用例清单（23 例）

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

## 改动本工具时的守则

- 两侧的**用例名必须逐字一致**（含空格与引号）—— 脚本按 `|` 之前的文本配对。
  少一侧的用例会被报成 `<<缺失>>` 并 exit 1（这条分支也做过验证）。
- Go struct 必须逐字段对齐真源 `types.go`，新增字段就要新增用例。
- **不要**在本文件里写「与 Go 一致」却只跑 RAG 侧 —— 那正是 AGENTS.md §3.3 要防的事。