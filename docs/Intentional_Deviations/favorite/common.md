<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-common`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/common/errmsg.go`）
> ⚠️ **本文件登记的是「两侧确实不同、且判定为不必修」的实现层差异。**
> ⇒ 每条都必须能回答：**「为什么不修回去？」**

> ✅ **2026-10-25 用户已签字**：**2 条**（DEV-FAV-COMMON-001 / DEV-FAV-COMMON-002）——
> 「**保留现状，登记为签字偏离**」⇒ 审查时**不必重复质疑**，但**也不得「顺手修回 Go 行为」**。
>
> 🆕 **核对时间：2026-10-29**（台账对齐 phase · W-E lane 逐条重核）—— 结论见下表。

## 🆕 2026-10-29 逐条重核结论（台账对齐 phase · W-E lane）

> **判定口径**：`docs/Intentional_Deviations/_INDEX.md:34` ——「被签字描述的那个行为**已不存在** ⇒ 本条签字**自动失效** ⇒ 不计入计数」。
> **方法**：**回 Go 真源 + 回 RAG 源码两侧核**（真源 = `Sea-RideTheWind_golang`，冻结只读；grpc-go 取 `v1.71.0` / `v1.65.0` 均可复核）。
> **撞号检查**：`DEV-FAV-COMMON-001` / `DEV-FAV-COMMON-002` **全域唯一**
> （`grep -rhoE "DEV-FAV-[A-Z]+-[0-9]+" docs/Intentional_Deviations/` ⇒ 本域 11 个编号**各对应一条**偏离）⇒ **无撞号** ⇒ **不需要** `SD-<编号>@<域>` 别名。

| ID | 结论 | 证据（核对日期 2026-10-29） |
|---|---|---|
| `DEV-FAV-COMMON-001` | ✅ **仍成立** | Go 侧仍返回 **nil**：`service/favorite/common/errmsg.go:104-106` 的 `GRPCError` = `status.Error(grpcCode, GetErrMsg(bizCode))`；grpc-go `status/status.go:58-60` `func Error(c codes.Code, msg string) error { return New(c, msg).Err() }` ⇒ `codes.OK` 时 `Err()` 返回 **nil** ⇒ 上游 `if err != nil` 不成立 ⇒ 「当成功」。RAG 侧仍返回**非 nil** 的 `StatusException`（grpc-java `Status#asException()` 对 OK 也返回对象）⇒ 调用方的 `err != null` **成立** ⇒ 「当失败」。⇒ 差异未变 |
| `DEV-FAV-COMMON-002` | ✅ **仍成立** | Go 侧仍**只认顶层**：`status.FromError(err)`（grpc-go）不沿 cause 链回溯 ⇒ 一个「被包过一层」的 `StatusException` 落 `ok=false` ⇒ `BizCodeFromError` 返回 **1015**。RAG 侧仍**刻意不回溯**：`service/favorite/favorite-common/src/main/java/com/rag/favorite/favorite/common/errmsg/FavoriteGrpcError.java:234-241`（`extractStatus` 只认 `StatusException` / `StatusRuntimeException` 两个具体类）⇒ 差异未变 |

> ⚠️ **计数影响**：本域 **2 条全部仍成立** ⇒ **主线更新 `docs/Intentional_Deviations/_INDEX.md` 时本域无需减项**（本 lane 不写共享索引）。
> ⚠️ **两条的「不可达」豁免依据本轮未重数**：原文写「favorite 域 **56 个** `GRPCError` 调用点没有一个传 `codes.OK`」与「actual error 全部来自 gRPC stub」⇒
> ⇒ **本轮未重跑那次调用点普查**（**超出本 lane 的核对范围**）⇒ ⇒ **该数字按原句保留**，**不**据此改写任何一侧；若将来要引用，请先重数。
>
> 📌 **2026-10-29 真实性复核 —— 上面那个「未重数」**已补齐**（原句保留，不改写）**：本轮**实测重数**（真源 `Sea-RideTheWind_golang/service/favorite/`，排除 `errmsg.go:104` 的定义行与 `_test.go`）：
> `grep -rn "GRPCError(" service/favorite/ --include=*.go | grep -v errmsg.go:104 | grep -v _test.go | wc -l` ⇒ **56** ⇒ **与原文的「56 个」逐位对上**；
> 分文件 = `create_favorite_folder_logic.go` 7 · `create_favorite_logic.go` 10 · `delete_favorite_folder_logic.go` 6 · `delete_favorite_logic.go` 6 · `get_favorite_folders_logic.go` 2 · `get_favorites_by_folder_logic.go` 5 · `helper.go` 11 · `update_favorite_folder_name_logic.go` 9 ⇒ **合计 56**；
> ⇒ ⚠️ **且 `grep -rn "codes.OK" service/favorite/ --include=*.go`（排除 `_test.go`）⇒ 零命中** ⇒ ⇒ **「56 个调用点没有一个传 `codes.OK`」成立** ⇒ ⇒ **「不可达」豁免依据成立**（本轮**未**改写任何一侧）⇒ ⇒ **DEV-FAV-COMMON-001 的状态维持 ✅ 仍成立**。
> ⇒ 📌 **与 Pitfall `PIT-FAV-RPC-022`（「台账里最危险的断言是『数字』」）的呼应**：本轮**机器重算**了这个数字 ⇒ **56 是可复算的**，不再是「信原句」。**核对日期 2026-10-29**。

---

## DEV-FAV-COMMON-001 · ✅ **已签字** · `GRPCError(codes.OK, …)` 的返回形态 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| | 内容 |
|---|---|
| **Go 行为** | `errmsg.go:104-106` 的 `GRPCError` 底层是 `status.Error(grpcCode, GetErrMsg(bizCode))`；grpc-go 的 `status.Error` 在 `code == codes.OK` 时返回 **nil**（不是 error）。⇒ `GRPCError(codes.OK, 5001)` ⇒ **nil** ⇒ 上游 `if err != nil` **不成立** ⇒ 调用方当成「成功」。 |
| **RAG 行为** | `FavoriteGrpcError.grpcError` = `Status.fromCode(grpcCode).withDescription(...).asException()`。grpc-java 的 `Status#asException()` **对 OK 不抛异常**（只有 `asRuntimeException()` 才抛 `IllegalStateException`）⇒ 返回一个 `code=OK` 的 `StatusException`（**非 nil**）⇒ 调用方的 `err != null` **成立** ⇒ 被当成失败。 |
| **实测** | 双侧探针 `OBS|obs_ok_status`：**Go=200**（`BizCodeFromError(nil)`）vs **Java=5001**（走文案反查/兜底）。 |
| **为什么接受（不可达）** | favorite 域 **56 个 `GRPCError` 调用点没有一个传 `codes.OK`**（实测分布：`Internal`×19、`InvalidArgument`×10、`NotFound`×9、`PermissionDenied`×6、`AlreadyExists`×6、`Unavailable`×4、`FailedPrecondition`×2）⇒ 该形态在本域**不可达**。 |
| **什么时候会变成真差异** | 若将来新增一个「用 `codes.OK` 表示成功」的调用点 ⇒ Go 侧会**静默返回 nil**（错误被吞掉）、Java 侧会**抛异常** ⇒ 行为分叉。⚠️ Go 的这个行为更像是**陷阱**（吞错误），但对齐它就等于把陷阱也搬过来。 |
| **守门** | 探针的 OBS 段**断言差异仍然存在**（Go 值 ≠ Java 值）⇒ 若哪天 Java 改成与 Go 一致，探针 **exit 2** 提示重新评估。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **不可达是它的豁免依据**：favorite 域 56 个 `GRPCError` 调用点**没有一个**传 `codes.OK`（码分布已实测）⇒ 只要不新增「用 `codes.OK` 表示成功」的调用点，这条就永远走不到。<br>⇒ **签字附带的前置动作**：若将来新增此类调用点，**必须**先回来重评本条（对齐 Go 就等于把「静默吞错误」的陷阱搬过来）。 |

---

## DEV-FAV-COMMON-002 · ✅ **已签字** · `status.FromError` 的「顶层 vs cause 链」判定 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| | 内容 |
|---|---|
| **Go 行为** | grpc-go 的 `status.FromError(err)` 只检查**顶层** error 是否实现 `GRPCStatus() *Status` 接口（**不**遍历 cause 链）⇒ 一个被业务代码包了一层的 `StatusException`（顶层不是 status error）在 Go 侧会落 `ok=false` ⇒ `BizCodeFromError` 返回 **1015**。 |
| **RAG 行为** | `FavoriteGrpcError#extractStatus:234-242` **刻意不沿 cause 链回溯**，只认 `StatusException` / `StatusRuntimeException` 两个具体类 ⇒ 与 Go 的单层语义一致。 |
| **差异点** | Go 认「**任意**实现了 `GRPCStatus()` 的 error」；RAG 认「**grpc-java 的两个具体类**」。⇒ 一个**自定义**的、实现了 `GRPCStatus()` 的 error：Go 能认（文案反查命中 ⇒ **5007**），RAG 认不出（⇒ **1015**）。 |
| **实测** | 双侧探针 `OBS|obs_custom_grpcstatus`：**Go=5007** vs **Java=1015**。 |
| **为什么接受（不可达）** | favorite 域的实际 error **全部**来自 gRPC stub ⇒ 永远是 `StatusRuntimeException` ⇒ 该分叉不可达。 |
| **什么时候会变成真差异** | 若业务代码引入自定义异常并把它当 gRPC error 抛出 ⇒ 两侧还原出的业务码不同（5007 vs 1015）⇒ 客户端看到的 `code` 不同。 |
| **守门** | 同上：探针 OBS 段断言差异存在。 |
| **状态** | ✅ **已签字**（2026-10-25，用户 Jason：**保留现状，登记为签字偏离**）—— **不是缺陷**；改动需**重新征签**<br>⚠️ **不可达是它的豁免依据**：favorite 域的实际 error **全部**来自 gRPC stub ⇒ 永远是 `StatusRuntimeException`。<br>⇒ **签字附带的前置动作**：若业务代码引入自定义异常并把它当 gRPC error 抛出，**必须**先回来重评本条（那时两侧还原出的业务码会不同：5007 vs 1015）。 |

---

## 本档其它空位

- `api.md` ⇒ 401 响应 body 形态，见 [`api.md`](./api.md)。
- `rpc.md` ⇒ U5 / U6 —— ✅ **均已落定**：U5 **撤销**（前提被实测证伪）、U6 **已签字**（= DEV-FAV-RPC-001），见 [`rpc.md`](./rpc.md)。
