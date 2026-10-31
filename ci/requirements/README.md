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

## 八、当前状态（2026-10-31 实测）

```
✅ VERIFIED           （23）  proto 契约 ×10 · errmsg 契约 ×4（article/favorite/hot/users）
                                schema 契约 ×7（除 users）· 表单段数上限 ×1 · 探针判定器 ×1
❌ FAIL               （1）   REQ-ERRMSG-LIKE-001（like 域 4 条「D-7 下游非本域错误」待用户拍板）
⚠️ NOT_VERIFIED      （19）  proto 消费面 4 · errmsg 未签字差异 5 域 · schema users 未签字差异
                                1 · errmsg 场景/传播 2 · CHECK/触发器 2 · deadline 行为 1
                                JWT/状态机/异步消息 3 · 调用图 1
➖ NOT_APPLICABLE     （2）   hot 域 DDL 维度 · 外键维度（两侧皆无）
🏷 APPROVED_DIFFERENCE（1）   REQ-ERRMSG-LIKE-002（SD-2026-10-23-06，机器回指已签字）
```

**PASS = 23**，**不计入 PASS 的缺口 = 19**，**FAIL = 1** ⇒ ⇒ **目标达成度远未完成**，这份清单的作用正是让这件事**可见**。

### 8.1 ⚠️ 「门禁 PASS」与「清单 VERIFIED」不是同一件事（别混谈）

| | 含义 |
|---|---|
| **门禁 PASS** | 「未发现**未登记**的漂移」—— 已签字 / 已销号 / 技术判定三类差异**放行** |
| **清单 VERIFIED** | 「这条目标要求已被**实际验证达成**」—— 任何未签字差异都让它**降级** |

⇒ 两者的差集 = 「**已登记但未签字**」的部分。
⇒ 例：R5 契约门对 `follow` / `message` / `task-*` 判 **PASS**（3 条 `technical` 差异放行），
但清单里这些域判 **NOT_VERIFIED**（差异是技术判定，**不是用户批准**）
⇒ **两处口径都保守，不冲突** —— 门禁不误报漂移，清单不虚报达成。

### 8.2 🆕 待用户拍板的差异清单（R5 · 8 条 `pending`）

**这 8 条是 Java 与 Go 真源**确实不一致**、但**没有人批准**的差异 ⇒ 按方向纠正，
**不得自行加豁免**、**不得为绿改 Java 业务代码** ⇒ 需你逐条裁定。

| # | 域 | 键 | Go 侧 | Java 侧 | 出处 | 差异性质 |
|---|---|---|---|---|---|---|
| 1 | `like` | `BIZCODE\|foreign_internal` | `13` | `1015` | `docs/DRIFT/like/vs-common.md:72`（D-7） | Go like-api 把下游 gRPC `INTERNAL` 码当业务码直传；Java 侧 `mapGrpcCodeToBizCode` 收敛到 1015 |
| 2 | `like` | `BIZCODE\|foreign_invalid_argument` | `3` | `2001` | 同上 | Go 直传 `INVALID_ARGUMENT`；Java 映到 2001 |
| 3 | `like` | `BIZCODE\|foreign_notfound` | `5` | `1015` | 同上 | Go 直传 `NOT_FOUND`；Java 收敛到 1015 |
| 4 | `like` | `BIZCODE\|foreign_plain` | `5001` | `1015` | 同上 | Go 兜底 `ErrorServerCommon`(5001)；Java 兜底 1015 |
| 5 | `like` | `BIZCODE\|foreign_unauthenticated` | `16` | `1009` | 同上 | Go 直传 `UNAUTHENTICATED`；Java 映到 1009 |
| 6 | `comment` | `OBS\|obs_wire_grpcstatus_1012` | `1012\|1012\|code(1012)\|评论区不存在\|1012` | `1012\|2\|unknown\|评论区不存在\|1012` | `docs/DRIFT/comment/common.md`（D1） | **业务码 wire 载体**：Go 把业务码写进 `grpc-status`；grpc-java 只能发 17 个标准码 ⇒ Java 挪到自定义 trailer。**业务码与文案两侧无损**，只差 `grpc-status` 字段的数值 |
| 7 | `comment` | `OBS\|obs_wire_grpcstatus_2001` | `2001\|…\|code(2001)\|…` | `2001\|3\|invalid_argument\|…` | 同上 | 同上 |
| 8 | `comment` | `OBS\|obs_wire_grpcstatus_5003` | `5003\|…\|code(5003)\|…` | `5003\|2\|unknown\|…` | 同上 | 同上 |

> ℹ️ 第 6~8 条与 like 域**已签字**的 `SD-2026-10-23-06`（grpc 业务码载体迁移 · 框架限制）**同族**
> ⇒ 若你签字该族，这 3 条可一并转正 ⇒ `comment` 域即可从 `NOT_VERIFIED` 升为 `VERIFIED`。

---

## 九、已知未登记的缺口（`coverage.unregistered_gap_ledgers`）

| 台账 | 范围 | 条数 | 状态 |
|---|---|---|---|
| `docs/DRIFT/common/alignment-2026-10-23.md` | 共享层 `service/common` | **28 项未修**（23 编号 + 5 未编号） | `NOT_REGISTERED` ⇒ 🔴 **这些是 Java 侧已知未实现的目标要求** |
| `docs/DRIFT/_cross/ddl-type-divergences.md` | DDL 维度 | 可空性 + DEFAULT 残留 22 处 + 3 条非唯一索引 | `PARTIALLY_REGISTERED` |
| `docs/Intentional_Deviations/` | 已批准差异 | 60 条已签字 + 12 条未签字候选 | `PARTIALLY_REGISTERED` |

> ⚠️ 守门每次运行都会把上表**逐条打印** ⇒ 「清单里没有这条」不能被读成「没有问题」。
