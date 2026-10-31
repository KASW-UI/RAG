<!-- @new 2026-10-29 · W-D lane（台账对齐）填写的空坑位 · 依据 [`../../DRIFT/comment/common.md`](../../DRIFT/comment/common.md) -->

# `comment` 域 common 层签字档

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」。**
> ⚠️ **签字偏离是决策，不是缺陷**：审查时不重复质疑，**也不得「顺手修正回 Go 的旧行为」**。

---

## SD-2026-10-31-02：gRPC 业务码的载体迁移（`grpc-status` → 自定义 trailer `rag-comment-biz-code`） ｜ **状态（核对日期 2026-10-31）：✅ 已签字**

> **签字人**：Jason（用户）· **签字日期**：2026-10-31
> **签字方式**：🏷 候选 → 用户逐条核对后确认签署（裁定语：「comment 3 条 + like 4 条签、foreign_plain 降级为 closed」）
> **原台账编号**：[`../../DRIFT/comment/common.md`](../../DRIFT/comment/common.md) 的 **D1**（OBSERVABLE DIFFERENCE）
> **现定性**：**框架限制**（grpc-java 无法构造越界 `grpc-status`）⇒ **不是**实现缺陷 ⇒ **不得「顺手改回 Go 的旧写法」**
> **机器基准**：`ci/contracts/errmsg/comment.txt` 的 `DEVIATION|OBS|obs_wire_grpcstatus_*` 三条 ⇒ 状态 `approved` + 依据 id `SD-2026-10-31-02`
> **同族先例**：`like` 域的 **`SD-2026-10-23-06@like`**（同一平台限制、同一修法）—— 差别只在**本域此前未签**
> **证据强度**：**L3（Go 源码逐条 + Java 逐条 + 双侧探针）**

| 项 | 值 |
|---|---|
| 组件 | `service/comment/comment-common`（`CommentGrpcError`） |
| 覆盖的差异 | **3 条**（`OBS|obs_wire_grpcstatus_1012` / `_2001` / `_5003`） |
| Go 出处 | `service/comment/common/errmsg/errmsg.go:33-35` ⇒ `NewGrpcErr(code, msg)` = `status.Error(codes.Code(code), msg)` ⇒ **业务码原样进 `grpc-status`** |
| RAG 出处 | `CommentGrpcError.java:72`（`BIZ_CODE_TRAILER_KEY = "rag-comment-biz-code"`）+ `:116-121`（`mapToGrpcCode`）+ `:139-145`（`grpcError`） |

### 差异内容（**只差 `grpc-status` 字段的数值**；业务码与文案两侧无损）

| case | 业务码 | 文案 | Go `grpc-status` | Java `grpc-status` | Java trailer |
|---|---|---|---|---|---|
| `obs_wire_grpcstatus_1012` | 1012 ✅同 | 评论区不存在 ✅同 | `code(1012)` | `unknown(2)` | 1012 ✅ |
| `obs_wire_grpcstatus_2001` | 2001 ✅同 | 目标类型和ID不能为空 ✅同 | `code(2001)` | `invalid_argument(3)` | 2001 ✅ |
| `obs_wire_grpcstatus_5003` | 5003 ✅同 | DB查询失败 ✅同 | `code(5003)` | `unknown(2)` | 5003 ✅ |

### 为什么不能对齐（框架限制的证据）

grpc-java **1.63.0**（本仓 `pom.xml` 的 `<grpc.version>`）：

| 证据 | 位置 | 结论 |
|---|---|---|
| `Code` 枚举只有 17 个成员 | `io/grpc/Status.java:66` `OK(0)` … `:216` `UNAUTHENTICATED(16)` | 合法值仅 **0~16** |
| 越界码一律降级 | `Status.java:305-310` `fromCodeValue` ⇒ `UNKNOWN.withDescription("Unknown code " + codeValue)` | `5003` ⇒ `UNKNOWN(2)` + **description 被覆盖** |
| 无法从 int 构造 | `Status.java:349` `fromCode(Code)` 只接受枚举 | **连编译都过不了** |

而 Go 侧是**无校验的强转**（`codes.Code` 是 `int32` 命名类型）⇒ 业务码以数值形式进 `grpc-status`。

⇒ RAG 取「**等价信息保全 + 载体迁移**」的最优可行方案：业务码挪到自定义 trailer `rag-comment-biz-code`，
**文案仍逐字留在 description** ⇒ **业务码与文案对客户端完全可得且无损**。

### ⚠️ 代价（必须随本偏离一起读）

- **唯一不可复原项 = `grpc-status` 字段的数值本身**（平台限制，非实现选择）。
- **方向不对称**（`../../DRIFT/comment/common.md` §3-D1 已登记）：**Go-rpc → RAG-api** 的业务码**能**捞回
  （grpc-java 降级成 `UNKNOWN + "Unknown code 1012"` ⇒ 按文本反查）；**RAG-rpc → Go-api** 的业务码**传不过去**
  （Go 只读 `grpc-status`，看不见 trailer）⇒ ⇒ **跨栈方向必须由对端显式兼容**。

---

## 本组件**此前没有**登记在案的签字偏离（核对日期 **2026-10-29**）

> ⚠️ **本节已被 2026-10-31 的 `SD-2026-10-31-02` 推翻** ⇒ 原写「本组件**没有**登记在案的签字偏离」**已过期** ⇒
> **保留原文备查**，不改写历史判断。
> **核法（可复算）**：
> ① `grep -rn '已签字\|签字偏离\|SD-' docs/DRIFT/comment/common.md` ⇒ **零命中**。
> ② `find service/comment/comment-common/src/main -type f` ⇒ **4 个文件 / 563 行**
> （`errmsg/CommentCodeError.java` · `errmsg/CommentErrorMessages.java` ·
> `errmsg/CommentGrpcError.java` · `errmsg/CommentErrorCode.java`）；
> Go 侧对应件 = `service/comment/common/errmsg/errmsg.go`（**1 个文件 / 103 行**）
> ⇒ **纯错误码表 + 一个跨模块错误通道的编码端**。
> ③ 本组件唯一的「非逐字复制点」（**跨模块 trailer 通道**）的**缺陷**落在 `comment-api`（消费端）
> ⇒ 已修 ⇒ **不是「决定不对齐」** ⇒ 不进本档；见 [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md)
> 的 **MISMATCH-API-01**（已修）· 教训档见 [`../../Pitfalls/comment/api.md`](../../Pitfalls/comment/api.md)
> 的 **PIT-COMMENT-01**（旧号 `PIT-COMMENT-API-01`）。
> ④ 本域**已签字**的偏离落在**别的组件** ⇒ [`./rpc.md`](./rpc.md)（`SD-2026-10-24-CAND-02` / `-06` /
> `SD-2026-10-23-04` 等）· [`./api.md`](./api.md)（`SD-2026-10-24-CAND-07` / `-08`）。
>
> ⚠️ **订正（2026-10-31）**：上段 ③ 的判断「trailer 通道不是『决定不对齐』」**只对「缺陷」成立**；
> **载体形态本身**（「Go 写 `grpc-status` vs Java 写 trailer」）是**平台限制下的结构性差异** ⇒
> 属「决定不对齐」⇒ **2026-10-31 已按 `SD-2026-10-31-02` 登记并签字**。

### 核对记录

- 核对日期：**2026-10-29**（W-D lane · 台账对齐 phase）· **2026-10-31** 新增 `SD-2026-10-31-02`（用户签字）
- ⚠️ **本文件此前是 0 字节空文件** ⇒ 本轮按「**确有指向 ⇒ 只留指针 + 写明归属**」处置。
- ⚠️ 若日后 `comment-common` 出现「**明知与 Go 不同、决定不对齐**」的条目 ⇒ **必须登记到本文件**（并带签字人 / 签字日期）。
  ⇒ ⚠️ **本条已于 2026-10-31 首次触发**（`SD-2026-10-31-02`）⇒ 该要求**已生效**，不是空条款。
