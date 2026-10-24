<!-- @new 2026-10-24 · align/common 工作树产出 · 基线 commit 8422116 -->

# 「审计完成」的覆盖面是**静默**的 —— 它默认跳过了没有 Go 对应物的那一半

> **产出方**：`align/common` 工作树，基线 commit **`8422116`**。
> 逐条漂移：见 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md)。
> 本文件回答：**为什么「service/common 已审计完成」这句话当时是对的、现在看是错的。**

---

## 现象

`docs/DRIFT/common/audit.md`（2026-10-21）的结论是：

> 「只读审计（Go ↔ RAG 逐行对照，Go 侧 **8 个非测试源文件**全部通读原文）结论 `SEMANTICALLY NOT EQUIVALENT`，共 20 项偏差。」

AGENTS.md §0 的域表据此给 `common` 标了 **✅ 审计完成**。

2026-10-24 复验，结论是：**这句话掩盖了两件事。**

---

## 坑 1：44 个文件里，**26 个从来没有任何 Go 侧的东西可对照**

RAG `service/common` 实测 **44 文件 / 7,587 行 / 10 个包**（2026-10-24）：

| 包 | 文件 | 行 | Go 侧对应物 |
|---|---|---|---|
| `communityfact` | 4 | 1,320 | ✅ `service/common/communityfact/`（4 个） |
| `logger` | 6 | 810 | ✅ `service/common/logger/logger.go` |
| `observability` | 6 | 2,088 | ✅ `service/common/observability/`（4 个） |
| `response` | 1 | 17 | ✅ `service/common/response/response.go` |
| `snowflake` | 1 | 315 | ✅ `service/common/snowflake/snowflake.go` |
| **`protection`** | **11** | **1,790** | 🔴 **无** —— 等价物在 **go-zero 库**里（`rest.Server` 内建中间件） |
| **`spring`** | **8** | **584** | 🔴 **无** —— Spring 装配层 |
| **`http`** | **3** | **350** | 🔴 **无** —— 等价物在 **go-zero 库**里（`httpx` 字节形状） |
| **`persistence`** | **2** | **223** | 🔴 **无** —— B-1 的缓解手段（RAG 独有） |
| **`exception`** | **2** | **90** | 🔴 **无** —— Go 侧用 `error` + code 表达 |

⇒ **有 Go 对应物的只有 18 个文件（41%）；另外 26 个文件（59%，3,037 行 ≈ 40% 的代码）**
**从来没有被任何 Go 侧的源码校验过**。

### 为什么这在 2026-10-21 是「查不出来」的

那轮审计的方法是「**读 Go 源文件 → 与 RAG 对应实现逐字对照**」（AGENTS.md §3.1）。
对于**没有 Go 对应文件**的 5 个包，这个方法**根本无从下手** ——
不是漏了，是**框架上没有可对照的基准**。

⇒ 而「没有基准」这件事，在台账里**没有被写成一句话**。
台账只说「审计完成」，读者（包括 AGENTS.md §0 的 ✅ 标记）会默认「全部对照过了」。

### 纪律

> **「审计完成」必须同时说清两件事：**
> 1. **对照了多少**（分子/分母，精确到文件/包）
> 2. **没对照的是什么、为什么、拿什么当基准**
>
> 「已审计」与「**已审计且有基准**」是两件事。
> 无基准的那部分不是「没问题」，是**「没查过」** —— 二者在台账里必须用不同的词。

⇒ 对本仓的具体结论：**`protection/`(11) + `http/`(3) 的基准是 go-zero v1.10.2 的库源码，
不是本仓 `Sea-RideTheWind_golang`**；`spring/`(8) + `persistence/`(2) + `exception/`(2)
是 RAG 独有的表达层，基准是**框架语义**（Spring Boot 4 / MyBatis-Plus / grpc-java），
按 §3.3「机制必须验证实际生效」来审，不是按「逐字对照 Go」来审。

---

## 坑 2：「8 个非测试源文件」与实际 **11** 对不上

2026-10-21 台账写「Go 侧 **8** 个非测试源文件全部通读原文」。
2026-10-24 实测 Go `service/common` 的非测试源文件是 **11** 个：

```
communityfact/  wire.go  logging.go  authority_http.go  process.go      (4)
observability/  http.go  observability.go  rpc.go  mq.go               (4)
logger/logger.go                                                          (1)
response/response.go                                                      (1)
snowflake/snowflake.go                                                    (1)
                                                                    合计 11
```

⚠️ **无法判定这 3 个文件的差额是「笔误」还是「真漏读」** ——
台账没留下逐文件清单，事后无法重建。⇒ 记为 `UNRESOLVED`。

**⇒ 但本轮（2026-10-23/24）的三路逐行对照覆盖了全部 11 个**，
每个包的「逐项覆盖清单」都列到了 `文件:行号` 级别，可逐条核对。
（对照基准仍按 §3.1：**以 Go 原文为唯一基准**，不拿 RAG 现有代码或其注释当基准。）

---

## 坑 3：结论里的**「已修」也会过期**，且不会被任何东西提示

`audit.md` 里写着：

> 「**已修 21 项**（5 项 HIGH 全部处理）⋯⋯ ② gRPC 拦截器未注册（user-rpc/admin-rpc 补 `@Import`）
> —— ⚠️ **此条结论已被推翻，见下方 H2 勘误小节**」

这条勘误是 2026-10-21 当天补的，**方向正确**。
但它只修正了「**user-rpc / admin-rpc 两个模块**」，
**没有回答「那另外 8 个 rpc 模块呢」**。

2026-10-24 实测：**9 个有 `GrpcServerConfigurer`，`hot-rpc` 没有**（DRIFT-COMMON-004）。
⇒ 也就是说：**一次「已修」的结论 + 一次「勘误」，仍然漏掉了一个消费方**。

### 纪律

> **「已修」类结论必须带覆盖面，不只带状态。**
> 「H2 已修」是个**逐消费方**的命题，不是**一次性**的命题。
>
> 判据：**「已修」要写成「N 个消费方里 M 个已修」的形状**，
> 且 M/N 的取数方式必须可复跑。
> 本轮补的 `tools/difftest/check-grpc-interceptor-wiring.py` 就是这个形状的固化。

---

## 附：本轮（2026-10-24）复验后，common 域台账的完整形状

| 档 | 位置 | 状态 |
|---|---|---|
| 逐条漂移 | [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md) | 3 条已修 + 26 条编号未修 + 5 项未编号观察 |
| 既有 4 份 | `audit.md` / `deadline.md` / `http.md` / `protection.md` | **未改动**（本轮只做增量，不改写历史结论） |
| 签字偏离 | [`../../Intentional_Deviations/common/_signed-deviations.md`](../../Intentional_Deviations/common/_signed-deviations.md) | A-1 / A-2 **仍成立**（A-1 的 15 处 catch 逐个复验过，全是 rethrow） |
| 踩坑 | 本目录 `observability.md` / `protection.md` / `deadline.md` / `audit.md` / `http.md` | 本轮填 4 个坑位（`http.md` 为既有） |

⚠️ **`docs/Pitfalls/common/_signed-deviations.md` 是个 0 字节占位，名字却叫「签字偏离」** ——
它与 [`../../Intentional_Deviations/common/_signed-deviations.md`](../../Intentional_Deviations/common/_signed-deviations.md)
同名但属不同档案柜。**疑似建目录时的错放**。
按 §5.3b「三档之间不要互相搬运」，它**不该在 `Pitfalls/` 下**；
本轮**未动它**（删改共享档案结构需主会话拍板），仅在此登记。