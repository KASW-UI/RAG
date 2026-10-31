# 目标要求清单（Requirement Register）

> **一句话**：这份清单回答「**Go 原始系统要求保留哪些行为与工程能力？这些要求在 Java 侧对应什么、验没验、还差什么？**」
>
> **真源**：`ci/requirements/requirements.json`（机器可读，JSON）　｜　**守门**：`ci/scripts/check-requirements.py`
> **变异测试**：`ci/scripts/check-requirements-mutations.py`（10 条变异全部红在目标规则）

---

## 一、为什么要有这份清单（2026-10-31 方向纠正的产物）

之前的契约基准（R4/R5/R6）虽然都标称「以 Go 为基准」，但**没有任何机器机制**保证「基准真的是 Go 派生的」。
⇒ 于是这条路径畅通无阻：

> 先读 Java 实现 → 把 Java 实现整理成一份「标准」 → 让 CI 检查它是否符合这份标准

这只在验证「Java 是否符合自己」，**不验证「Java 是否符合原始系统的目标」**。

⇒ 本清单把纪律落成**机器判据**：

| 纪律 | 机器化方式 |
|---|---|
| 每条要求必须有 Go 原始依据 | 守门规则 **R3**：`go_basis` 必填且非空 ⇒ 没有 Go 依据的条目**不允许存在** |
| 差异必须经批准，不能自行豁免 | 守门规则 **R6**：`APPROVED_DIFFERENCE` 的 `deviation.id` 必须能在 `docs/Intentional_Deviations/` 里查到**且分类为「已签字」** |
| 未达成的目标不得伪装成通过 | 守门规则 + 汇总：**PASS 只 = VERIFIED**；`NOT_IMPLEMENTED` / `NOT_VERIFIED` / `BLOCKED` **一律不计入 PASS** |
| 「清单里没有」不等于「没有问题」 | 汇总会逐条打印 `coverage.unregistered_gap_ledgers`（**已知未登记的缺口**） |

---

## 二、三条铁律

1. **Go 决定 WHAT，Java 决定 HOW，CI 验证 Java 是否实现了 WHAT。**
2. **⛔ 禁止以 Java 当前现状反向定义目标。** 「Java 有这个模块」不等于「这是目标要求」；「Java 没这个能力」不等于「不需要检查」；「Java 有 N 个测试」不等于「覆盖充分」。
3. **⛔ 不许为了让 CI 变绿而修改 Java 业务代码。** 发现 Java 实现与目标要求不符 ⇒ **先报告证据与影响**，由人决定；不得自行加豁免。

---

## 三、状态词表（六态 + 一态 N/A）

| 状态 | 含义 | 计入 PASS？ |
|---|---|---|
| `VERIFIED` | ✅ Java 侧**实际验证通过**（有可复算命令 + 有证据） | ✅ **是**（且**只有**它是） |
| `FAIL` | ❌ Java 侧与目标要求**不一致**（含「已知差异但未经批准」） | ❌ 否 |
| `NOT_IMPLEMENTED` | ⛔ Java 侧**尚未实现**该目标要求 | ❌ 否 |
| `NOT_VERIFIED` | ⚠️ Java 侧**有实现，但尚无足够验证** | ❌ 否 |
| `APPROVED_DIFFERENCE` | 🏷 **已明确批准**的实现差异（必须机器回指已签字条目） | ❌ 否（它是一条「已知差异」记录） |
| `BLOCKED` | ⛔ **验证条件不具备** | ❌ 否 |
| `NOT_APPLICABLE` | ➖ 该维度**对目标域不适用**（例：hot 域两侧均无业务表） | ❌ 否（但**必须显式打印**） |

> ⚠️ `NOT_APPLICABLE` 是本仓在用户给的六态之外**显式增加**的一态 ——
> 因为「hot 域没有数据库」既不是「已对齐」也不是「失败」，把它塞进任何一态都是误导。

---

## 四、一条要求的字段

```jsonc
{
  "requirement_id": "REQ-PROTO-LIKE-001",   // 唯一 · 格式 REQ-<维度>-<域>-<NN>
  "dimension": "proto_contract",            // 见 §五
  "domain": "like",                         // "*" = 全域
  "title": "like 域 Proto 定义与 Go 真源逐条一致",
  "go_basis": "proto/like.proto",           // ⛔ 必填：Go 原始依据（路径）
  "go_basis_note": "Go 真源 proto（冻结 · 只读）",
  "semantics": "必须保持的语义（人类可读）",
  "java_impl": "service/like/like-proto/src/main/proto/like.proto",
  "verify_method": "contract_baseline",     // contract_baseline | manual
  "verify_command": "python3 ci/scripts/check-proto-contract.py --baseline ci/contracts/proto",
  "evidence": "ci/contracts/proto/like.proto",
  "status": "VERIFIED",                     // 六态 + N/A
  "deviation": null,                        // APPROVED_DIFFERENCE 时必填 {id, ledger, reason}
  "gap_note": "…",                          // FAIL / NOT_VERIFIED 时必填（理由与影响）
  "pipeline": "rag-like",                   // 归属流水线（"(仓级 · 所有流水线)" = 每条都跑）
  "task": "static-gates"                    // 归属 task（必须是 ci/tasks/*.yml 或真实 job 名）
}
```

---

## 五、维度（dimension）

| 维度 | 覆盖什么 | Go 依据形态 |
|---|---|---|
| `proto_contract` | Proto 定义（message / 字段名 / 字段号 / 类型 / 枚举） | `proto/<域>.proto` |
| `proto_consumption` | 生成代码一致性 · optional presence · 字段号硬编码 · Jackson 转换 | `proto/` |
| `errmsg_contract` | 错误码常量 · 文案表 · 未知码回落 | `service/*/common/errmsg*.go` |
| `errmsg_propagation` | 错误码↔业务场景对应 · 跨服务传播与 fallback | `service/*/rpc/internal/logic` |
| `schema_contract` | 列类型 / 长度 / 可空性 / DEFAULT / 唯一约束 | `service/*/rpc/internal/model` |
| `schema_constraint` | CHECK / 触发器 / 外键 | `service/*/rpc/internal/model` |
| `runtime_behavior` | deadline 传播 · 表单段数上限 · JWT claim · 状态机 · 异步消息 | 多处 |
| `callgraph` | 服务间实际调用关系 | `service/*/api/internal/svc` |
| `engineering_constraint` | **验证手段本身**的有效性（探针判定器有牙等） | 多处 |

> ⚠️ `engineering_constraint` 与 `runtime_behavior` 的区别：前者验的是「**验证手段可不可信**」，后者验的是「**目标行为达没达到**」。
> ⇒ **不得**用前者冒充后者（例如 deadline 的**探针**有牙 ≠ deadline 的**传播行为**已验证）。

---

## 六、守门规则（R1~R11）

| 规则 | 判据 | 违反后果 |
|---|---|---|
| R1 | `requirement_id` 格式 + 唯一 | FAIL |
| R2 | `status` 取值域封闭（7 值） | FAIL |
| R3 | ⛔ `go_basis` 必填非空 | FAIL（**反向定义的机器化阻断**） |
| R4 | `dimension` 取值域 | FAIL |
| R5 | `VERIFIED` ⇒ 有可复算命令 + 证据文件存在 | FAIL |
| R6 | ⛔ `APPROVED_DIFFERENCE` ⇒ `deviation.id` 在台账里**且**是「已签字」 | FAIL（**杜绝自行豁免**） |
| R7 | `FAIL` ⇒ 必须有 `gap_note` | FAIL（**差异不得被藏起来**） |
| R8 | `VERIFIED` ⇒ 归属 pipeline / task **真实存在** | FAIL（**要求不得没人执行**） |
| R9 | 同维度同域同标题不得重复 | FAIL |
| R10 | 契约基准里存在的域 ⇒ 清单里必须有对应要求 | FAIL（**覆盖缺口**） |
| R11 | `coverage.unregistered_gap_ledgers` 不得为空 | 警告 |

**退出码**：`0` = 无回归 + 结构合规　｜　`1` = 结构违规或 VERIFIED 声明复验失败（**Java 回归**）　｜　`2` = BLOCKED（检查没做成）
> ⚠️ **本门退出 0 的含义是「没有回归 + 结构合规」，不等于「目标全部达成」** ⇒ 六态分列必须与 PASS 数同时打印。

---

## 七、变更流程

1. **新增/修改一条要求** ⇒ 改 `requirements.json` ⇒ 跑 `python3 ci/scripts/check-requirements.py` ⇒ 跑 `python3 ci/scripts/check-requirements-mutations.py`（证明门禁没被改坏）。
2. **把某条从 `NOT_VERIFIED` 升到 `VERIFIED`** ⇒ 必须同时补上**可复算的** `verify_command` + `evidence`（守门 R5 会拦）。
3. **把某条从 `FAIL` 改成 `APPROVED_DIFFERENCE`** ⇒ 必须先在 `docs/Intentional_Deviations/` **签字**，再把编号填进 `deviation.id`（守门 R6 会拦 ⇒ **不能自己签**）。
4. **基准更新**（`ci/contracts/**`）属于**有审查的变更**，不随 Go 真源自动变化。

---

## 八、当前状态（2026-10-31 实测 · **签字后**）

```
✅ VERIFIED           （23）  proto 契约 ×10 · errmsg 契约 ×4（article/favorite/hot/users）
                                schema 契约 ×7（除 users）· 表单段数上限 ×1 · 探针判定器 ×1
❌ FAIL               （0）   （无）—— 2026-10-31 用户签字后清零
⚠️ NOT_VERIFIED      （18）  proto 消费面 4 · errmsg 场景/传播 2 · CHECK/触发器 2 · deadline 行为 1
                                JWT/状态机/异步消息 3 · 调用图 1 · schema users 未签字差异 1
                                errmsg 未签字差异 4 域（follow/message/task×2）
➖ NOT_APPLICABLE     （2）   hot 域 DDL 维度 · 外键维度（两侧皆无）
🏷 APPROVED_DIFFERENCE（3）   REQ-ERRMSG-LIKE-002（SD-2026-10-23-06）
                                REQ-ERRMSG-LIKE-001（**SD-2026-10-31-01** · 机器回指已签字）
                                REQ-ERRMSG-COMMENT-001（**SD-2026-10-31-02** · 机器回指已签字）
```

**PASS = 23**，**不计入 PASS 的缺口 = 18**，**FAIL = 0** ⇒ ⇒ **目标达成度远未完成**，这份清单的作用正是让这件事**可见**。

> ⚠️ **签字 ≠ 达成**：两条 errmsg 要求从 `FAIL`/`NOT_VERIFIED` 转成 `APPROVED_DIFFERENCE`
> ⇒ **不计入 PASS**（PASS 仍是 23）⇒ **缺口从 19 降到 18，不是降到 0**。
> ⇒ 签字的语义是「**承认并接受这处差异**」，不是「**证明两侧一致**」。

### 8.1 ⚠️ 「门禁 PASS」与「清单 VERIFIED」不是同一件事（别混谈）

| | 含义 |
|---|---|
| **门禁 PASS** | 「未发现**未登记**的漂移」—— 已签字 / 已销号 / 技术判定三类差异**放行** |
| **清单 VERIFIED** | 「这条目标要求已被**实际验证达成**」—— 任何未签字差异都让它**降级** |

⇒ 两者的差集 = 「**已登记但未签字**」的部分。
⇒ 例：R5 契约门对 `follow` / `message` / `task-*` 判 **PASS**（3 条 `technical` 差异放行），
但清单里这些域判 **NOT_VERIFIED**（差异是技术判定，**不是用户批准**）
⇒ **两处口径都保守，不冲突** —— 门禁不误报漂移，清单不虚报达成。

### 8.2 ✅ 已裁定的差异清单（R5 · 8 条 `pending` → **0 条待办**）

> **裁定（2026-10-31 · Jason）**：**「comment 3 条 + like 4 条签、foreign_plain 降级为 closed」**
> ⇒ 8 条**全部销项** ⇒ 本域 `pending` 清零 ⇒ R5 契约门退出码由 **3 → 0**。
> **落笔位置**：签字档 = `docs/Intentional_Deviations/like/rpc.md`（`SD-2026-10-31-01`）+
> `docs/Intentional_Deviations/comment/common.md`（`SD-2026-10-31-02`）；
> 机器基准 = `ci/contracts/errmsg/{like,comment}.txt` 的 `DEVIATION` 段状态。

| # | 域 | 键 | Go 侧 | Java 侧 | 出处 | 差异性质 | **裁定** |
|---|---|---|---|---|---|---|---|
| 1 | `like` | `BIZCODE\|foreign_internal` | `13` | `1015` | `docs/DRIFT/like/vs-common.md:72`（D-7） | Go like-api 把下游 gRPC `INTERNAL` 码当业务码直传；Java 侧 `mapGrpcCodeToBizCode` 收敛到 1015 | ✅ **approved**（`SD-2026-10-31-01`） |
| 2 | `like` | `BIZCODE\|foreign_invalid_argument` | `3` | `2001` | 同上 | Go 直传 `INVALID_ARGUMENT`；Java 映到 2001 | ✅ **approved**（同上） |
| 3 | `like` | `BIZCODE\|foreign_notfound` | `5` | `1015` | 同上 | Go 直传 `NOT_FOUND`；Java 收敛到 1015 | ✅ **approved**（同上） |
| 4 | `like` | `BIZCODE\|foreign_plain` | `5001` | `1015` | 同上 | Go 兜底 `ErrorServerCommon`(5001)；Java 兜底 1015 | ✅ **closed**（比较点错配 ⇒ 两侧对外一致 ⇒ 不是行为漂移） |
| 5 | `like` | `BIZCODE\|foreign_unauthenticated` | `16` | `1009` | 同上 | Go 直传 `UNAUTHENTICATED`；Java 映到 1009 | ✅ **approved**（`SD-2026-10-31-01`） |
| 6 | `comment` | `OBS\|obs_wire_grpcstatus_1012` | `1012\|1012\|code(1012)\|评论区不存在\|1012` | `1012\|2\|unknown\|评论区不存在\|1012` | `docs/DRIFT/comment/common.md`（D1） | **业务码 wire 载体**：Go 把业务码写进 `grpc-status`；grpc-java 只能发 17 个标准码 ⇒ Java 挪到自定义 trailer。**业务码与文案两侧无损**，只差 `grpc-status` 字段的数值 | ✅ **approved**（`SD-2026-10-31-02`） |
| 7 | `comment` | `OBS\|obs_wire_grpcstatus_2001` | `2001\|…\|code(2001)\|…` | `2001\|3\|invalid_argument\|…` | 同上 | 同上 | ✅ **approved**（同上） |
| 8 | `comment` | `OBS\|obs_wire_grpcstatus_5003` | `5003\|…\|code(5003)\|…` | `5003\|2\|unknown\|…` | 同上 | 同上 | ✅ **approved**（同上） |

> ℹ️ 第 6~8 条与 like 域**已签字**的 `SD-2026-10-23-06`（grpc 业务码载体迁移 · 框架限制）**同族**
> ⇒ 用户签字该族 ⇒ `comment` 域的 3 条一并转正。

#### 核对补记（2026-10-31 · 逐条核对 Go↔Java 消费点后 ⇒ 裁定依据）

> ⚠️ 核对方法 = **两侧同时读消费点**（Go `service/like/api/internal/logic/like/*.go` 6 处同形 +
> Java `service/like/like-api/.../LikeApiSupport.java`），不是拿 RAG 现状当基准。

| # | 核对结论 | 证据 |
|---|---|---|
| 1 / 2 / 3 / 5 | 🔴 **对外可见的真实差异**（不止是库方法层）—— Go 侧业务码 = 原始 gRPC 码（13/3/5/16），Java 侧 = 域内代表码（1015/2001/1015/1009）；**文案两侧相同**（Go `st.Message()` / Java `status.getDescription()`，都不查表）⇒ 客户端看到的 `code` 字段变了 ⇒ **签字即表示接受这个客户端可见的差异** | Go：`getlikecountlogic.go:41-46` 等 6 处 `status.FromError` → `errmsg.NewErrCodeMsg(int(st.Code()), st.Message())`；Java：`LikeApiSupport.java:181-186`（`status != null` ⇒ 走 `bizCodeFromError`）+ `LikeGrpcError.java:233-247`（`mapGrpcCodeToBizCode`） |
| 4（`foreign_plain`） | 🟡 **「比较点错配」**——探针比的是 Java 的**库方法** `LikeGrpcError#bizCodeFromError`（非 status ⇒ 末尾兜底 1015），Go 侧比的是**消费点**；而 Java 的**实际消费点** `LikeApiSupport#rpcError` **先判 `status == null`** ⇒ 对外返回 **5001**（= Go 的 `NewErrCode(ErrorServerCommon)`）⇒ **两侧对外一致** ⇒ **不是行为漂移** ⇒ 用户裁定降为 **`closed`** | Java：`LikeApiSupport.java:181-185`（`if (status == null) return CodeError.newCode(ERROR_SERVER_COMMON)`）· 佐证：全仓 `bizCodeFromError` 的**生产调用点只有 1 处**（`LikeApiSupport.java:183`）且带该前置守卫；`GoStyleCircuitOpenException extends RuntimeException`（`service/common/.../GoStyleCircuitOpenException.java:30`）⇒ 非 status ⇒ 熔断打开路径两侧同为 5001 |
| 6 / 7 / 8 | 🟡 **只差 `grpc-status` 字段的数值**；业务码与文案两侧无损（Java 从 trailer `rag-comment-biz-code` 取回）⇒ **不可复原项 = 平台限制**（grpc-java `Status.Code` 只有 17 个成员）⇒ 与 like 域已签字的 `SD-2026-10-23-06@like` **同一族** | Go：`service/comment/common/errmsg/errmsg.go:33-35`（`status.Error(codes.Code(code), msg)`）；Java：`CommentGrpcError.java:72`（trailer key）+ `:116-121`（`mapToGrpcCode`）+ `:139-145`（`grpcError`） |

> 🆕 **顺带核出一条新事实（属 like 域写权 ⇒ 本 lane 只登记、不改代码）**：
> `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:70-77` 的 javadoc 写
> 「熔断打开 ⇒ 落进 `bizCodeFromError` 的兜底 ⇒ 业务码 **1015**；Go 侧此时是 5001」
> ⇒ **该措辞与当前实现不符**：`LikeApiSupport#rpcError` 在调用 `bizCodeFromError` **之前**就判了
> `status == null` ⇒ 熔断打开（`GoStyleCircuitOpenException` 非 status）对外返回 **5001** = **与 Go 一致**。
> ⇒ **实测依据**：grpc-java 1.63.0 `io/grpc/ClientInterceptors.java:153-157`（`interceptCall` 在
> `newCall()` 时被调）＋ `io/grpc/stub/ClientCalls.java:148-155`（`channel.newCall(...)` 在 `try` **之外**）
> ⇒ 熔断异常**原样**抛出 ⇒ 不会被包装成 `StatusRuntimeException`。
> ⇒ 处置建议：**订正该 javadoc**（like 域写权）—— 但这**不影响**上表任何一条的状态。
> ⚠️ **该条已转派给 like 域 lane（G2）**（合并收口执行者已在回执里派出）。

**这 8 条在 CI 里长什么样**

| 门 | 裁定前（2026-10-31 上午） | 裁定后（2026-10-31 下午） |
|---|---|---|
| `rag-contract-errmsg`（R5） | PASS 7 / FAIL 0 / BLOCKED 0 / **PENDING 2**（域 `comment`·`like`）· 退出码 **3** · build 🔴 **红 —— 有意为之** | **PASS 9 / FAIL 0 / BLOCKED 0 / PENDING 0** · 退出码 **0** · build 🟢 **绿** |
| 差异台账分列 | approved 31 · closed 2 · technical 13 · **pending 8** | approved **38** · closed **3** · technical 13 · **pending 0** |
| 目标要求清单 | PASS 23 · **FAIL 1**（`REQ-ERRMSG-LIKE-001`）· NOT_VERIFIED 19 · 缺口 19 | PASS 23 · **FAIL 0** · NOT_VERIFIED **18** · 缺口 **18** · APPROVED_DIFFERENCE **3** |

> ⚠️ **「R5 变绿」≠「目标全部达成」**：两条 errmsg 要求转成了 **`APPROVED_DIFFERENCE`**（**不计入 PASS**，
> 因为「已批准差异」≠「已验证一致」）⇒ **PASS 仍是 23**、缺口仍是 **18**。
> ⇒ 签字的语义是「**承认这处差异并接受**」，不是「**证明两侧一致**」。
> ⇒ 下一步若要抬 PASS 数，靠的是**补齐那 18 项缺口**（proto 消费面 / schema 约束 / runtime 行为 / 调用图），
> **不是**再签字。

---

## 九、已知未登记的缺口（`coverage.unregistered_gap_ledgers`）

| 台账 | 范围 | 条数 | 状态 |
|---|---|---|---|
| `docs/DRIFT/common/alignment-2026-10-23.md` | 共享层 `service/common` | **28 项未修**（23 编号 + 5 未编号） | `NOT_REGISTERED` ⇒ 🔴 **这些是 Java 侧已知未实现的目标要求** |
| `docs/DRIFT/_cross/ddl-type-divergences.md` | DDL 维度 | 可空性 + DEFAULT 残留 22 处 + 3 条非唯一索引 | `PARTIALLY_REGISTERED` |
| `docs/Intentional_Deviations/` | 已批准差异 | 60 条已签字 + 12 条未签字候选 | `PARTIALLY_REGISTERED` |

> ⚠️ 守门每次运行都会把上表**逐条打印** ⇒ 「清单里没有这条」不能被读成「没有问题」。
