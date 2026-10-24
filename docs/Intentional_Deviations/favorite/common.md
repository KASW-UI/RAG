<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 有意偏离档 · `service/favorite/favorite-common`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/common/errmsg.go`）
> ⚠️ **本文件登记的是「两侧确实不同、且判定为不必修」的实现层差异。**
> ⇒ 每条都必须能回答：**「为什么不修回去？」**

> ✅ **2026-10-25 用户已签字**：**2 条**（DEV-FAV-COMMON-001 / DEV-FAV-COMMON-002）——
> 「**保留现状，登记为签字偏离**」⇒ 审查时**不必重复质疑**，但**也不得「顺手修回 Go 行为」**。

---

## DEV-FAV-COMMON-001 · ✅ **已签字** · `GRPCError(codes.OK, …)` 的返回形态

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

## DEV-FAV-COMMON-002 · ✅ **已签字** · `status.FromError` 的「顶层 vs cause 链」判定

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
