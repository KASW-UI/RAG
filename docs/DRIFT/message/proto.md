<!-- @new 2026-10-23 23:0x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `service/message/message-proto` 对照台账（§3.4 的**第 1 条边界**：复制忠实 ≠ 消费方式已验）

> **核对时间**：2026-10-23 23:00
> **基线 commit**：`8422116`（`align/message` 工作树）
> **本文件回答**：§3.4 结论「10 个 proto 复制忠实」在 message 域**具体意味着什么**，
> 以及 §3.4 自己声明的「效力边界第 1 条」（**不证明 RAG 消费生成代码的方式与 Go 一致**）在本域**逐条核实**的结果。

## 1. 对照对象

| 项 | Go | RAG |
|---|---|---|
| `.proto` 源文件 | `Sea-RideTheWind_golang/proto/message.proto` | `service/message/message-proto/src/main/proto/message.proto` |
| 生成物 | `service/message/rpc/pb/message.pb.go` + `message_grpc.pb.go`（Go 包名 `pb`） | protoc 生成的 `com.rag.message.message.proto.*` |

## 2. 文件层：复制忠实（复核 §3.4）

```
归一化 diff（剥 package / option / 空行 / 注释）：
  ✅ 0 差异
计数：rpc 9=9   message 19=19   enum 3=3
```

`option` 块的差异与 §3.4 的约定完全一致：

| 项 | Go | RAG | 判定 |
|---|---|---|---|
| `package` | `message;` | `message;` | ✅ **逐字相同** |
| `option go_package` | `./pb` | **删除** | ✅ Go 专用项，Java 不消费 |
| `option java_package` | — | `com.rag.message.message.proto` | ✅ 表达层 |
| `option java_multiple_files` | — | `true` | ✅ 表达层 |

### ✅ ~~🔴~~ **已销号（2026-10-30 · G2 复核）**：澄清一条容易误判的点：本域**没有** SD-04 / SD-05 那类 `package` 归一偏离

`comment` / `like` 两域的签字偏离（`SD-2026-10-23-04` / `-05`）是
**proto `package` 由 Go 的遗留名 `pb` 归一为域名**，代价是 gRPC 方法路径前缀不同。

**message 域不是这种情况**，实测依据：

```
Go  proto/message.proto:3        package message;
Go  rpc/pb/message_grpc.pb.go:383  ServiceName: "message.MessageService"
RAG message.proto:3              package message;
```

⇒ 两侧的 proto package **本来就是 `message`**。
Go 生成代码的**语言包名**叫 `pb`（来自 `option go_package = "./pb"`），
那是**语言层面的包名**，不是 proto package，也不是 gRPC 服务名的前缀。

⚠️ **这条必须写清楚的原因**：只看「Go 包叫 `pb`」就顺手登记一条签字偏离，
是本轮差点犯的错 —— 那会把一条**不存在的偏离**记进档案，
而档案一旦有假条目，§5.3b「不读就误判」的成本就落到所有后来人头上。
⇒ **本域 `docs/Intentional_Deviations/message/proto.md` 判定为「无偏离」，不建该文件。**

#### ✅ 2026-10-30 · G2 复核销号：本行的 🔴 标记**已过期**（派生层曾把标题里的交叉引用当成一条登记）

> **归属**：`align/g2-message-like-task` 工作树产出 · **基线 commit** `bdac3607` · **核对日期** 2026-10-30
> **证据强度**：**E3**（两侧源码逐字对照 + 真源回核）· **阶段归属**：**P1 · D 类**（经分析确认不适用 ⇒ **不阻断**阶段 1）
> ⚠️ **原「🔴」标记保留不改**（§5.3b「原值一律保留」+ 改标题状态会断掉入链锚点）⇒ **以本小节为准**。

**派生层为什么会把它算成一条未闭合项**：本行的**标题里出现了 `SD-04`**（作为「本域**没有** SD-04 那类偏离」的**交叉引用**）⇒ 派生器的「标题里认得出编号」规则 ⇒ 把它当成**一条 `SD-04` 的登记** ⇒ 归到 `DRIFT/message/proto.md:34` ⇒ 状态取到标题的 🔴 ⇒ 判成「🔴 未闭合」。

**逐条回源复核（2026-10-30 · 本 lane 实测，非转述）**：

| 侧 | 出处 | 实测内容 |
|---|---|---|
| Go | `proto/message.proto:3` | `package message;` ⇒ **proto package 本来就是 `message`** |
| Go | `proto/message.proto:5` | `option go_package = "./pb";` ⇒ Go 生成物的**语言包名** `pb` 来自这里 ⇒ **不是** proto package、**不是** gRPC 服务名前缀 |
| Go | `service/message/rpc/pb/message_grpc.pb.go:383` | `ServiceName: "message.MessageService",` ⇒ 生成后的服务名前缀就是 `message.MessageService` |
| RAG | `service/message/message-proto/src/main/proto/message.proto:3` | `package message;` ⇒ 与 Go **逐字相同** |
| RAG | `service/message/message-proto/src/main/proto/message.proto:4-5` | `option java_package = "com.rag.message.message.proto";` + `option java_multiple_files = true;` ⇒ **表达层**（§3.4 允许范围） |
| RAG | `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/impl/MessageRpcServiceImpl.java:62` | `extends MessageServiceGrpc.MessageServiceImplBase` ⇒ 生成物同样落在 proto package `message` 下 ⇒ 服务名前缀与 Go **同为** `message.MessageService` |

⇒ **结论**：本域**从未引入** `comment`/`like` 两域那种「proto package 由遗留名 `pb` 归一为域名」的偏离 ⇒ **差异已消失** ⇒ **本条无待实现内容** ⇒ 归 **D 类**（经分析确认不适用）· **不阻断阶段一** ⇒ **销号**。
⇒ ⚠️ **本条不是「RAG 错」也不是「Go 错」** —— 是**派生层的标题识别口径**把一条**澄清**当成了**登记** ⇒ 属 §0-1 效力边界①「派生层是台账的投影、不是代码的事实」的典型样本 ⇒ 故**本轮以回源复核为准**。

## 3. 消费方式（§3.4 效力边界第 1 条）—— 逐条核实

§3.4 明写：*「复制忠实 ≠ 消费方式已验。本条只证明 `.proto` 文件本身两侧一致，
不证明 RAG 侧使用生成代码的方式与 Go 一致（`optional` presence 语义、
字段号是否被某处硬编码覆盖、Jackson/protobuf 配置差异）」*。
本轮针对 message 域把这三项分别查了：

### 3.1 `optional` presence 语义 —— ✅ 本域**不适用**

```
grep -n 'optional' message.proto   →  0 命中
```

⇒ proto3 **没有任何 `optional` 标量字段**，因此不存在
「proto3 标量没有 presence，Go 的零值与 RAG 的 null 不可区分」这条经典风险。
**这一条在本域是空风险面**，不需要额外处置。

⚠️ 对照：users 域的 proto 若含 `optional`，就必须单独验 presence ——
**同一个 §3.4 结论在不同域的风险面不同，不能跨域外推**。

### 3.2 字段号被硬编码覆盖 —— ✅ 未发现

```
grep -rn 'getDescriptor|FieldNumber|setField(|getNumber()' service/message/*/src/main
→ 4 处命中，全部是 **枚举** 与 int 的互转，不是 protobuf 字段号：
   AbstractMessageApiService:202   role.getNumber()
   MessageApiSupport:191/193      NotificationKind.ADMIN_BROADCAST/ADMIN_NOTICE.getNumber()
   MessageDomainService:239       canon.role1().getNumber()
```

⇒ 没有绕过生成代码直接操作 descriptor 或按号取字段的写法。
（`MessageApiSupport#adminNotificationKind` 用 `setKindValue(int)` 而非 `setKind(enum)`
是为了容纳 proto3 未定义值 —— 见该方法 javadoc，**属正确用法**，不是绕过。）

### 3.3 序列化路径 —— ✅ 两侧都不经 Jackson

- **gRPC**：devh starter + protobuf marshaller（`message-rpc` 的 pom 无 Jackson 参与序列化路径）。
- **HTTP**：`message-api` 用 Jackson 3，但那是**自有 DTO**（`AdminSendNotificationResp` 等），
  **与 proto 无关**。

⇒ 不存在「同一份对象既走 protobuf 又走 Jackson，两套配置互相干扰」的面。
⚠️ 但由此产生一条**已签字**的 wire 差异：HTTP 侧的 `affected` 输出字符串（`SD-2026-10-23-03`），
详见 [`DRIFT/message/api.md`](./api.md) 第三轮第五节与
[`Intentional_Deviations/_cross/signed-deviations.md`](../../Intentional_Deviations/_cross/signed-deviations.md)。

## 4. 结论

| # | 检查项 | 结论 | 证据强度 |
|---|---|---|---|
| 1 | `.proto` 文件与 Go 一致 | ✅ 归一化 diff 0 差异 | **静态逐行** |
| 2 | proto `package` / 服务名 | ✅ 两侧均 `message` / `message.MessageService` | 静态 + Go 生成物 |
| 3 | `optional` presence 风险面 | ✅ 本域为空（0 个 optional） | 静态 |
| 4 | 字段号硬编码 | ✅ 无（4 处 `getNumber()` 均为枚举互转） | 静态 + 全域 grep |
| 5 | protobuf / Jackson 配置互扰 | ✅ 不存在（两条路径物理分离） | 静态 |
| 6 | 双侧活体 gRPC 往返 | ✅ **已验证（2026-10-24 第八轮）** | 见下 |

**判定：`SEMANTICALLY EQUIVALENT`** —— ⚠️ **2026-10-24 第十轮升档**：
本组件原先的判定是「`SEMANTICALLY EQUIVALENT`（在该证据强度下）」，
且**证据强度低于** §3.4 的其它结论 —— 因为第 6 项（双侧活体往返）当时**仍未验证**，
它与 `UNRESOLVED-MSG-003`（12 档对拍**脚本就位但从未执行**）是同一件事。

> ✅ **2026-10-24 第八轮：`UNRESOLVED-MSG-003` 已关闭** ⇒ 第 6 项不再是未知项：
> 真起 Go + RAG 两套 message-rpc（端口 20082 / 60210），跑
> `tools/difftest/exp-difftest-grpc-errors-message.sh`
> ⇒ **11 条 status 码 + happy path 全部逐条一致，EXIT=0**，产物 `tools/difftest/message-grpc-results.txt`。
> ⇒ **proto 组件的证据强度已从「静态逐行 + 源码级」升到「双侧活体往返」这一档**，
> 不再低于 `hot-common`。
>
> ⚠️ **升档的边界**：活体往返覆盖的是**已列出的 9 个 RPC 的错误路径 + happy path**；
> **字段级 presence 语义**（`optional` 字段在 wire 上的存在/缺失）本域为空
> （proto.md §3 的第 3 项：0 个 `optional`）⇒ 无残留风险面。
> ⚠️ 仍按 §6.2 约定 2 读作「**在该证据强度下一致**」，不是「任何维度都已证明」。

## 5. 本组件此前**没有**专属台账

`docs/DRIFT/_cross/component-coverage-matrix.md` 的「组件缺口」把
6 个 `*-proto` + `like-common` + `message-common` 列为无专属台账；本轮补齐了
`proto.md`（本文件）与 `common.md`。
⇒ message 域四组件的台账现状：**rpc / api / common / proto 均有**，
`Intentional_Deviations` 侧现有 **7 条**（`SD-2026-10-24-01/02/03/04` + `SD-2026-10-23-06/07/08`，其中 `SD-07` 已撤销；正本在 [`Intentional_Deviations/message/rpc.md`](../../Intentional_Deviations/message/rpc.md)），
⚠️ **2026-10-24 第十一轮更正**：本句原先写「只有 api 一条指针（指向 `_cross` 里的 SD-03）」—— **已过期**；
`Pitfalls` 侧 api / rpc 两份本轮已追加。

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W6

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| SD-04 | D（经分析确认：本域从未引入该偏离 ⇒ 不适用） | P1 | 否（不阻断阶段一） | Go `proto/message.proto:3`（`package message;` ⇒ proto package 本来就是 `message`）＋ `proto/message.proto:5`（`option go_package = "./pb";` ⇒ Go 生成物的语言包名 `pb` 来自这里，**不是** proto package、也不是 gRPC 服务名前缀）＋ `service/message/rpc/pb/message_grpc.pb.go:383`（`ServiceName: "message.MessageService",` ⇒ 生成后的 gRPC 服务名前缀就是 `message.MessageService`）vs RAG `service/message/message-proto/src/main/proto/message.proto:3`（`package message;` ⇒ 与 Go 逐字相同）＋ `:4`（`option java_package = "com.rag.message.message.proto";`）＋ `:5`（`option java_multiple_files = true;`）＋ `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/impl/MessageRpcServiceImpl.java:62`（`extends MessageServiceGrpc.MessageServiceImplBase` ⇒ 生成物同样落在 proto package `message` 下 ⇒ 服务名前缀与 Go 同为 `message.MessageService`）⇒ ⇒ **差异已消失**：`comment`/`like` 两域那种「proto package 由遗留名 `pb` 归一为域名」的偏离**在本域从未被引入** ⇒ 台账 `docs/DRIFT/message/proto.md:34` 的那一行本身就是一条**澄清**（「本域**没有** SD-04 / SD-05 那类 `package` 归一偏离」）⇒ 派生器把标题里的交叉引用当成了登记 ⇒ 逐条回源后确认本域无待实现内容 ⇒ 归 D、不阻断阶段一 | 2026-10-30 |
