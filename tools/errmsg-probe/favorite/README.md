# errmsg-probe/favorite：favorite 域错误码契约的 Go ↔ Java 双侧对拍

AGENTS.md §3.3 要求的「**两侧同时跑的探针**」在 **favorite 域**的落地实现。

> 兄弟目录 `tools/errmsg-probe/`（根目录那份）是 **article 域**的版本，**只覆盖 article**。
> 本目录是 favorite 域的**独立副本**，两者互不覆盖、互不影响。

## 怎么跑

```bash
bash tools/errmsg-probe/favorite/run-parity.sh
echo $?          # 0 = 双侧逐字节一致（OBS 段差异仍在登记状态）
                 # 1 = 主段有漂移
                 # 2 = 环境问题 / OBS 段差异已消失（需重新评估并更新台账）
```

Go 真源默认**向上搜索** `Sea-RideTheWind_golang`（主工作区与 worktree 两种布局都能定位）；也可用
`GO_REPO=/path/to/Sea-RideTheWind_golang` 覆盖。

依赖：`mvn`（离线 `-o`）、JDK 21+、Go toolchain（≥1.25）、**本地 Go module cache 里要有 grpc**。

## 与 article 版的三点关键差异

| # | article 版 | favorite 版 | 为什么 |
|---|---|---|---|
| 1 | 真源 `errmsg.go` **零 import** ⇒ 临时模块零依赖，`GOPROXY=off` 直接跑 | 真源 **import 了 grpc**（`codes` + `status`）⇒ 临时模块**必须有 grpc 依赖** | favorite 的 `BizCodeFromError` / `GRPCError` 直接依赖 grpc 的 `status` 语义 |
| 2 | 只验码表 + 回落（3 段） | 多验 **WIRE**（wire 形态）+ **BIZCODE**（业务码反查矩阵）（5 段） | favorite 域最核心的 L1 契约是「**业务码藏在 description 文案里**」，只验码表覆盖不到 |
| 3 | 无 OBS 段 | 多一个 **OBS 段** | 有两条**两侧必然不同**的已知不可达差异，需要显式登记而不是静默 |

### 关于依赖：为什么用真 grpc 而不是 stub

favorite 的 `errmsg.go` 里 `BizCodeFromError` 依赖 `status.FromError(err)` 的判定规则
（只看**顶层**是否实现 `GRPCStatus()` 接口，**不**沿 cause 链回溯）。
若用 stub 复刻这个接口，探针就变成「拿我的 stub 验我的 stub」——
必然自洽、必然假绿（AGENTS.md §3.3）。

⇒ 本探针用**真 grpc 库**：临时模块 `go.mod` 里 `require google.golang.org/grpc <版本>`，
版本**从真源 `go.mod` 动态读取**（当前 v1.81.1）⇒ 真源升版本时本脚本自动跟随。
依赖由 `go mod tidy`（`GOPROXY=off`，从本地 module cache 解析）生成 `go.sum` ⇒ **不联网**。

## 结构

| 文件 | 角色 |
|---|---|
| `go/errmsg_probe.go` | **同包探针**（`package common`）：被复制到临时模块与真源同包编译 ⇒ 能读未导出的 `codeMsg` 表 |
| `go/main.go` | Go 半边：输出规范化 dump + Go 侧自检（`const` 块 ≡ `codeMsg` 键集合） |
| `java/Probe.java` | RAG 半边：跑 **favorite-common 的真实编译产物**（`FavoriteErrorCode` / `FavoriteErrorMessages` / `FavoriteGrpcError`），自己不持有任何码值/文案 |
| `run-parity.sh` | 校验真源 sha256 → 搭临时模块 → 跑两侧 → 比对 sha256 + diff |

## 输出格式（两侧必须逐字节相同）

```
CONST|<code>                          码值集合（升序）
TABLE|<code>|<msg>                    码 → 文案（升序），取值走对外函数而非直接读表
FALLBACK|<code>|<msg>                 未登记码的回落结果（顺序 = cases 文件顺序）
WIRE|<bizCode>|<grpcCode>|<desc>      每个码经 GRPCError 构造出的 wire 形态
BIZCODE|<case>|<code>                 BizCodeFromError 的还原矩阵
OBS|<case>|<value>                    **已知不可达差异**（两侧不同，单列，不参与逐字节对拍）
```

favorite 域当前 **108 行**（CONST 24 + TABLE 24 + FALLBACK 19 + WIRE 24 + BIZCODE 17），
双侧 sha256 均为 `03be9aa6e55916453e015e0b9c11139d1a64f7118256ab5da46939a139150f81`。

## BIZCODE 矩阵为什么是 17 条

它验的是 favorite 域**最容易被写错**的一条链：业务码不在 `grpc-status` 里，
只能靠 **description 文案反查**（`BizCodeFromError`）还原。

其中 4 条专门钉「**文案反查优先于 gRPC 码类别**」：

| case | 构造 | 期望 | 为什么这条有判别力 |
|---|---|---|---|
| `desc_5304` | `FAILED_PRECONDITION` + "收藏暂不可撤回" | 5304 | `FAILED_PRECONDITION` **不在** Go 的 switch 白名单 ⇒ 只有反查能给 5304 |
| `desc_5304_unauth` | `UNAUTHENTICATED` + "收藏暂不可撤回" | 5304 | `UNAUTHENTICATED` **在**白名单（否则会给 5004）⇒ 反查优先必须赢 |
| `desc_ok_text` | `INTERNAL` + "OK" | 5001 | `"OK"` 是 `Success` 的文案，但 `Success`/`Error` 被排除在反查表外 |
| `desc_fail_text` | `INTERNAL` + "FAIL" | 5001 | 同上 |

另有 `plain_error`（非 gRPC 错误 ⇒ 1015）与 `nil_error`（⇒ 200）两条边界。

## OBS 段：两条**两侧必然不同**的已知差异

OBS 段**不参与逐字节对拍**，但也不是「随便放过」：
`run-parity.sh` 断言 ① 两侧 OBS 的 case 名集合与脚本声明一致；
② 每条 OBS 的 **Go 值仍然 ≠ Java 值** ⇒ 若哪天差异消失，脚本 **exit 2** 提示更新台账。

| case | Go | Java | 为何不可达 |
|---|---|---|---|
| `obs_ok_status` | **200** | **5001** | Go 的 `status.Error(codes.OK, msg)` 返回 **nil** ⇒ `BizCodeFromError(nil)` = 200；Java 的 `grpcError(OK, …)` 返回一个 `code=OK` 的 `StatusException`（非 nil）⇒ 走反查/兜底。**favorite 域 56 个 `GRPCError` 调用点没有一个传 `codes.OK`** ⇒ 不可达 |
| `obs_custom_grpcstatus` | **5007** | **1015** | Go 的 `status.FromError` 认「顶层实现 `GRPCStatus()`」⇒ 文案反查命中 ⇒ 5007；Java 的 `FavoriteGrpcError#extractStatus` 只认 `StatusException` / `StatusRuntimeException` 两个具体类 ⇒ 认不出 ⇒ 1015。favorite 域实际 error 全部来自 gRPC stub ⇒ 不可达 |

## 已知边界

- **只覆盖 favorite 域**。users / like / hot / article / comment / follow / task 域的码表各自独立
  （且与 favorite **同号不同义**），跑本探针**不代表**那些域也对齐了。
- **验的是码表与业务码反查，不是 HTTP 边界**。Go api 层把 `errmsg.GetErrMsg(code)` 写进 envelope 的
  `msg` 字段、**HTTP 恒 200**；这一层的等价性由 favorite-api 的 MVC 管线测试覆盖
  （`FavoriteApiMvcPipelineTest`），**不**由本探针覆盖。
- **不覆盖 `BizCodeFromError` 在 HTTP 层的落点**：Go 侧 api 的 `codeFromRPCError` 调的就是
  `favoritecommon.BizCodeFromError`，RAG 侧对应 `FavoriteGrpcError#bizCodeFromError`
  —— 本探针验的是这个函数本身**两侧等价**，调用点是否正确接线由 api 侧台账负责。
