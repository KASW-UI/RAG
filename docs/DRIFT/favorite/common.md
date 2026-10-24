<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# DRIFT 台账 · `service/favorite/favorite-common`

> **核对时间：2026-10-25**（Go 冻结版 `Sea-RideTheWind_golang/service/favorite/common/errmsg.go`）
> **本轮性质**：首轮源码级 Go ↔ RAG 逐条对照 **+ 双侧活体对拍**（新增探针 `tools/errmsg-probe/favorite/`）。
> **证据强度**：✅ **双侧活体对拍**（两侧同时跑、逐字节比对，sha256 相同）
> —— 这是本仓 §3.3「两侧同时跑的探针」在 favorite 域的首次落地。
> ⇒ 本台账里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 落地形态 | 3 个类 / 456 行（`FavoriteErrorCode` / `FavoriteErrorMessages` / `FavoriteGrpcError`） |
| 码表规模 | **24 个**（Go `errmsg.go:9-40` 逐行计数），RAG 侧 1:1 |
| 本轮发现 | **0 条 L1 漂移**；**2 条 OBS**（不可达的实现层差异，已登记） |
| 双侧探针 | `bash tools/errmsg-probe/favorite/run-parity.sh` ⇒ **EXIT=0**（108 行逐字节一致，sha256 相同） |
| 本台账范围 | **仅 `favorite-common`**。`favorite-api` / `favorite-rpc` 见同目录另两份台账 |

---

## 1. 对照结论：L1 契约逐条一致

| 契约项 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| 24 个码值 | `errmsg.go:8-41`（const 块） | `FavoriteErrorCode`（枚举，声明顺序逐条一致） | ✅ 探针 `CONST` 段 24/24 相同 |
| 码 → 文案表 | `errmsg.go:43-76`（`codeMsg`） | `FavoriteErrorMessages.MSG_BY_CODE` | ✅ 探针 `TABLE` 段 24/24 相同 |
| 未知码回落 | `errmsg.go:91-97`（`GetErrMsg`）⇒ 落 `codeMsg[Error]` = `"FAIL"` | `FavoriteErrorMessages.getMsg` 同款回落 | ✅ 探针 `FALLBACK` 段 19/19 相同 |
| `GRPCError` 的 wire 形态 | `errmsg.go:104-106`（`status.Error(grpcCode, GetErrMsg(bizCode))`） | `FavoriteGrpcError.grpcError` | ✅ 探针 `WIRE` 段 24/24 相同 |
| `BizCodeFromError` 还原 | `errmsg.go:108-137` | `FavoriteGrpcError.bizCodeFromError` | ✅ 探针 `BIZCODE` 段 17/17 相同 |
| 反查表的**排除规则** | `errmsg.go:78-89`（排除 `Success`/`Error`，first-write-wins） | `FavoriteGrpcError` 静态块同款 | ✅ 由 `desc_ok_text` / `desc_fail_text` 两条钉死 |
| 反查**优先于**码类别 | `errmsg.go:118-120` 在 switch 之前 | `bizCodeFromError` 同序 | ✅ 由 `desc_5304` / `desc_5304_unauth` 两条钉死 |

> ✅ **本域最关键的语义是「业务码不在 `grpc-status` 里，而在 description 文案里」** ——
> 由探针 `WIRE` 段（24 条）与 `BIZCODE` 段（17 条）双向钉死。
> `5304`（收藏暂不可撤回）走 `FAILED_PRECONDITION`，而它**不在** Go 的 switch 白名单里
> ⇒ **只能**靠文案反查还原 —— 这条链断掉就会退化成 1015。

### ⚠️ 「反查优先」是本域最容易被写错的一条

| case | 构造 | 期望 | 判别力 |
|---|---|---|---|
| `desc_5304` | `FAILED_PRECONDITION` + "收藏暂不可撤回" | 5304 | `FAILED_PRECONDITION` 不在白名单 ⇒ 只有反查能给出 5304 |
| `desc_5304_unauth` | `UNAUTHENTICATED` + "收藏暂不可撤回" | 5304 | `UNAUTHENTICATED` **在**白名单（否则给 5004）⇒ 反查优先必须赢 |

---

## 2. 🔴 2 条 OBS（不可达的实现层差异，已登记待签字）

| # | 项 | Go | RAG | 为何不可达 |
|---|---|---|---|---|
| **OBS-1** | `GRPCError(codes.OK, …)` | `status.Error(codes.OK, msg)` 返回 **nil** ⇒ `BizCodeFromError(nil)` = **200** | `grpcError(OK, …)` 返回一个 `code=OK` 的 `StatusException`（**非 nil**）⇒ 走反查/兜底 = **5001** | favorite 域 **56 个 `GRPCError` 调用点没有一个传 `codes.OK`**（实测分布：`Internal`×19、`InvalidArgument`×10、`NotFound`×9、`PermissionDenied`×6、`AlreadyExists`×6、`Unavailable`×4、`FailedPrecondition`×2） |
| **OBS-2** | 「实现了 `GRPCStatus()` 的自定义 error」 | `status.FromError` 认「**顶层**实现 `GRPCStatus()`」⇒ 文案反查命中 = **5007** | `extractStatus` 只认 `StatusException` / `StatusRuntimeException` **两个具体类** ⇒ 认不出 = **1015** | favorite 域实际 error **全部**来自 gRPC stub ⇒ 永远是这两个类 |

> ⚠️ 这两条**不是「Go 与 RAG 一致」也不是「不一致」**，而是**实现层的必然差异**。
> ⇒ 已登记在 [`../../Intentional_Deviations/favorite/common.md`](../../Intentional_Deviations/favorite/common.md)（待签字）。
> ⇒ 探针把它们放在 **OBS 段**：不参与逐字节对拍，但**断言差异仍然存在** ——
> 若哪天 Java 侧改成与 Go 一致，探针 **exit 2** 提示更新台账，而不是让它静默漂移。

---

## 3. 探针：`tools/errmsg-probe/favorite/`

> AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 favorite 域的落地。
> 兄弟目录 `tools/errmsg-probe/`（根目录那份）是 **article 域**版本，**只覆盖 article**；
> 本目录是 favorite 域的**独立副本**，两者互不覆盖（并发工作树下不碰共享工具）。

```bash
bash tools/errmsg-probe/favorite/run-parity.sh
echo $?   # 0 = 一致（OBS 段差异仍在登记状态）；1 = 主段漂移；2 = 环境问题 / OBS 差异已消失
```

| 项 | 值 |
|---|---|
| 真源 sha256 | `f943c1d6734688a02e4efd500e3e92c086d01917f3836de3915d67ecc3d179f1`（与复制件逐字节相同） |
| 主段行数 | **108**（CONST 24 + TABLE 24 + FALLBACK 19 + WIRE 24 + BIZCODE 17） |
| 双侧 sha256 | 均为 `03be9aa6e55916453e015e0b9c11139d1a64f7118256ab5da46939a139150f81` |
| OBS 段 | 2 条（Go/Java 值各自打印，差异存在性被断言） |

### ⚠️ 与 article 版探针的关键差异：favorite 的真源 **import 了 grpc**

| # | article 版 | favorite 版 |
|---|---|---|
| 依赖 | 真源 `errmsg.go` **零 import** ⇒ 临时模块零依赖 | 真源 **import 了 `codes` + `status`** ⇒ 临时模块**必须有 grpc** ⇒ `go mod tidy`（`GOPROXY=off`，从本地 module cache 解析）生成 `go.sum` |
| 版本 | — | grpc 版本**从真源 `go.mod` 动态读取**（当前 v1.81.1）⇒ 真源升版本时脚本自动跟随 |
| 为何不用 stub | — | `BizCodeFromError` 的语义**依赖** `status.FromError` 的判定规则；用 stub 会变成「拿我的 stub 验我的 stub」（§3.3 假绿） |

---

## 4. 证据边界（明确未验的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| 码表 / 回落 / wire 形态 / 业务码反查 | ✅ **双侧活体对拍** | 探针 108 行逐字节一致 |
| `BizCodeFromError` 在 **HTTP 层**的落点 | ✅ 已验（在 `favorite-api` 侧） | Go 的 `codeFromRPCError` ⇒ `favoritecommon.BizCodeFromError`；RAG 的 `FavoriteApiSupport.invokeRpc` ⇒ `FavoriteGrpcError.bizCodeFromError`。见 [`api.md`](./api.md) |
| `GRPCError` 在**真实 gRPC 链路上**的 description 保真 | ⚠️ **未做活体对拍** | 探针验的是「构造出的 Status 对象」，不是「真 channel 上传输后的字节」。grpc-java 对 description 逐字节保真，但**本轮未实测** ⇒ 留作后续（like 域曾在此踩坑，见 `docs/DRIFT/like/`） |
| `favorite-common` **不消费任何 proto message** | ✅ 已确认 | `FavoriteGrpcError` 全文只操作 `io.grpc.Status` ⇒ 该组件在 proto 消费链路上是空的（见 [`proto.md`](./proto.md)） |

---

## 5. 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite   # align/favorite 工作树

# 双侧活体对拍（需要 Go toolchain + 本地 module cache 有 grpc）
bash tools/errmsg-probe/favorite/run-parity.sh

# 本组件单测（25 例：码表 6 + 文案 9 + 往返 10）
mvn -o -pl service/favorite/favorite-common test
```

> ⚠️ 探针**没有**挂进 `mvn test`：它需要 Go toolchain + module cache，进 CI 等于给流水线引入新依赖。
> 它是独立脚本，由人（或后续 agent）在改了 favorite 码表后手动跑。
