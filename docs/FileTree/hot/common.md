# hot-common — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/hot/common`
> 目标根包：`com.rag.hot.hot.common.errmsg`（**落点已定**，2026-10-22 实施，见 §12.5）
> 推导日期：2026-10-22
>
> **本文件覆盖 Go 侧全部 1 个文件（`errmsg.go`，121 行），已逐行读完。**
> 本组件是 `hot` 域里**唯一的非 api/rpc 组件**，与 `FileTree/common.md`（`service/common`，跨服务共享）**不是同一个东西**，
> 两者的区别见 §12.1 的边界声明。
>
> ✅ **实施状态（2026-10-22）**：本组件已落地为
> `hot-common/src/main/java/com/rag/hot/hot/common/errmsg/HotErrorCode.java`（enum，11 个码）
> 与 `HotErrorMessages.java`（文案表 + `getMsg`），
> 外加 14 条契约测试（`HotErrorCodeContractTest` 5 条 + `HotErrorMessagesContractTest` 9 条）。
> Go ↔ Java 双侧实测对拍输出 **sha256 逐字节一致**；7 项变异验证全部被守门拦下。
> 本节以下的推导与判据仍然有效，且**已被实施结果反向验证**。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG `service/hot/hot-common/` **已落地**（2026-10-22 实施）：`pom.xml` + `src/main/java/.../errmsg/` 两个类
（`HotErrorCode`、`HotErrorMessages`）+ `src/test/java/.../errmsg/` 两个契约测试类（14 条用例）。

| RAG 位置 | 现状 | 对应 Go 包 |
|---|---|---|
| `service/hot/hot-common/pom.xml` | 存在，零生产依赖（仅 test scope 的 junit-jupiter） | — |
| `service/hot/hot-common/src/main/java/com/rag/hot/hot/common/errmsg/` | **2 个类**（码 / 文案分离） | `common/` |
| `service/hot/hot-common/src/test/java/com/rag/hot/hot/common/errmsg/` | **2 个测试类**（14 条用例） | — |

### ⚠️ 边界声明：本文件与 `FileTree/common.md` 是两个不同组件

全仓有**两个**都叫 `common` 的东西，混淆会导致把跨服务共享能力错当成 hot 专属、或反之：

| | 本文件 | `FileTree/common.md` |
|---|---|---|
| Go 路径 | `service/hot/common/` | `service/common/` |
| RAG 位置 | `service/hot/hot-common/` | `service/common/` |
| 文件数 | **1**（`errmsg.go`） | 14（`communityfact` / `logger` / `observability` / `response` / `snowflake`） |
| 消费者 | **仅 hot-api**（grep 验证，§12.3） | 所有业务模块 |
| 内容 | 业务码表 + gRPC 错误双向转换 | 通用中间件 / 响应信封 / 日志 / 雪花 ID |
| 是否有 errmsg | **有**（`errmsg.go`） | **无**（`FileTree/common.md` 全文未出现 `errmsg`） |

### 可直接复用的现有能力

| RAG 组件 | 状态 | 说明 |
|---|---|---|
| `com.rag.common.exception.BusinessException` | ✅ 已落地 | unchecked + `(int code, String msg)`，与 Go `CodeError` 形状同构，见 §12.4 |
| `com.rag.common.response.Response` | ✅ 已落地 | `{int code; String msg; Object data;}`，字段名与 Go envelope 逐字一致 |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **常量放 `hot-common` 还是 `hot-api` 的 `service` 包** | ✅ **已定论（2026-10-22 实施）：落在 `hot-common`**。三条互相独立的依据：① `hot-api/pom.xml` 早已声明对 `hot-common` 的依赖（消费侧已就位）；② `service/hot/pom.xml` 早已把 `hot-common` 列为 `<module>`；③ 本文件 §12.5 的推荐落点 + §12.7 三域口径不通过项 #3（users/message/article 三域均用独立 `-common`，三对一）。⇒ §12.5 的「待拍板」已消除，`api.md` 对应两处已同步 |
| **`CodeError` 异常载体** | ✅ **已定论：`com.rag.common.exception.BusinessException` REUSE，不自建**。依据是 `users` 域的同一做法——`users-common/errmsg/ErrorCode` 的 javadoc 明写用法 `new BusinessException(ec.getCode(), ec.getMsg())`。hot 侧两个可达成员（`CodeError` @ `hotcenter.go:56`、`NewErrCode` @ `getHotArticlesLogic.go:68,72`）均被它等价覆盖。⚠️ 副作用需知悉：码表在 hot 域、异常在 `com.rag.common`，形成「码在 hot、异常在 common」的跨模块依赖；这是 `users` 已接受的形态 |
| **类数（码与文案是否分离）** | ✅ **已定论：分离，2 个类**。判据来自三个已落地 `-common` 的实测对照，见 §12.5。**注意这与「落 `hot-common` 还是 `hot-api`」是两个独立决定**——落点已定（落在 `hot-common`），类数早已定（2 个） |
| **11 个常量的可见性** | Go 侧是 `package common` 内的**导出常量**，且 `bizCodeByMsg` 在**包初始化时**遍历 `codeMsg` 构建 ⇒ 迁移后必须保留**同一张表的完整性**，不能只搬 api 用得到的 4 个（否则 `GetErrMsg` 的未知码回退会失效，见 §12.3） |

---

## 12.2 第二部分：Go 原始文件树

```
service/hot/common/
└── errmsg.go          # 唯一文件：11 常量 + codeMsg + bizCodeByMsg + CodeError + 5 函数
```

**本组件无 `etc/`、无 `internal/`、无 proto、无数据库、无外部交互**——它是一个纯内存的值定义 + 纯函数库。

---

## 12.3 第三部分：Go 文件职责分析

### `errmsg.go` 全量清单（逐段）

| 段 | 行号 | 内容 |
|---|---|---|
| `CodeError` struct | `:10-13` | `Code int \`json:"code"\`` / `Msg string \`json:"msg"\`` |
| `(*CodeError).Error()` | `:15-17` | 返回 `"ErrCode:%d, Errmsg:%s"` |
| `NewErrCode(code)` | `:19-24` | 用 `GetErrMsg(code)` 填 Msg |
| `NewErrCodeMsg(code,msg)` | `:26-31` | 自带 msg，不查表 |
| 常量块 | `:33-46` | 11 个 |
| `codeMsg` | `:48-61` | 11 条码→文案 |
| `bizCodeByMsg` | `:63-74` | 包初始化时从 `codeMsg` 反向构建 |
| `GetErrMsg(code)` | `:76-82` | 查表，未命中回退 `codeMsg[Error]` = `"FAIL"` |
| `GRPCError(grpcCode, bizCode)` | `:84-86` | `status.Error(grpcCode, GetErrMsg(bizCode))` |
| `BizCodeFromError(err)` | `:88-121` | 错误→业务码反查，34 行 |

### 常量与文案表（L1 冻结值，逐字抄录）

| 常量 | 值 | `codeMsg` 文案 |
|---|---|---|
| `Success` | 200 | `OK` |
| `Error` | 500 | `FAIL` |
| `CodeServerBusy` | 1015 | `service busy` |
| `ErrorServerCommon` | 5001 | `internal server error` |
| `ErrorNotFound` | 5002 | `record not found` |
| `ErrorInvalidParam` | 5003 | `invalid parameter` |
| `ErrorUnauthorized` | 5004 | `unauthorized` |
| `ErrorForbidden` | 5005 | `forbidden` |
| `ErrorAlreadyExist` | 5006 | `already exists` |
| `ErrorDbSelect` | 5007 | `database query failed` |
| `ErrorDbUpdate` | 5008 | `database update failed` |

⚠️ **文案是英文**，与 users 域 / article 域的中文文案**不同语言**（详见 §12.3「码表三方冲突」）。

### `bizCodeByMsg` 的两个关键性质（迁移时必须知道）

1. **它跳过 200/500**（`:66-68`），所以 `OK` / `FAIL` 不会被反查成业务码。
2. **它是 `codeMsg` 的严格逆映射**——11 条文案**逐条互不相同**（已逐条核对），因此 `:69-71` 的「先到先得」分支在 Go 里虽然写法上是**非确定性**的（Go map 遍历顺序随机），**实际结果恒定**。
   ⇒ 迁移到 Java 用 `putIfAbsent` 循环即**完全等价**，**不必**也不应为了「消除随机性」改成别的写法——那会引入一个 Go 侧不存在的假设。
   ⚠️ 反过来说：**若日后有人给 `codeMsg` 加了两条相同文案**，`bizCodeByMsg` 在 Go 侧立刻变成真随机。Java 侧用 `putIfAbsent` 会悄悄「更确定」，从而**掩盖**这个 Go 缺陷 —— 这属于隐性行为差异，加文案时须留意。

### `GRPCError` 与 `BizCodeFromError` 是一对互逆函数

`GRPCError(codes.X, bizCode)` 写入的 message 是 `GetErrMsg(bizCode)`；`BizCodeFromError` 正是靠 `bizCodeByMsg`（`:101-103`）把它还原回 `bizCode`。
⇒ **`bizCodeByMsg` 存在的唯一理由就是支撑这个往返**，不是冗余代码，迁移时不能当成可删项。

### `BizCodeFromError` 的判定顺序（照抄，顺序本身是语义）

```
err == nil                              → 200                      (:89-91)
status.FromError(err) 返回 !ok：
    err 是 *CodeError                    → codeErr.Code             (:95-97)
    否则                                  → 1015                     (:98)
status.FromError 成功：
    bizCodeByMsg 命中 st.Message()        → 查到的码                 (:101-103)
    否则 switch st.Code()：
        InvalidArgument  → 5003 / Unauthenticated → 5004 / PermissionDenied → 5005
        AlreadyExists    → 5006 / NotFound         → 5002 / Internal       → 5001
        default          → 1015                                          (:105-119)
```

⚠️ **`bizCodeByMsg` 的命中优先于 code switch**（`:101` 在 `:105` 之前）。本表中文案与码一一对应，两条路径结果相同，所以顺序变化当前不可观测；但若日后文案与 code 表解耦，这个先后次序就会变成语义。

### L2 可达性（grep 验证，2026-10-22 实测）

在 `service/hot/` 全目录按 `hotcommon\.` 逐点核对：

| 符号 | api 侧调用点 | rpc 侧调用点 |
|---|---|---|
| `Success` | `api/hotcenter.go:47` | — |
| `GetErrMsg` | `api/hotcenter.go:48,55` | — |
| `ErrorServerCommon` | `api/hotcenter.go:54`；`api/internal/logic/hot/getHotArticlesLogic.go:67,68,72,73,140` | — |
| `CodeError` | `api/hotcenter.go:56` | — |
| `NewErrCode` | `api/internal/logic/hot/getHotArticlesLogic.go:68,72` | — |
| `CodeServerBusy` / `ErrorNotFound` / `ErrorInvalidParam` / `ErrorUnauthorized` / `ErrorForbidden` / `ErrorAlreadyExist` / `ErrorDbSelect` / `ErrorDbUpdate` / `NewErrCodeMsg` / `GRPCError` / `BizCodeFromError` | **零调用点** | **零调用点** |

**⇒ hot-rpc 对本文件零引用**（`grep -rn 'service/hot/common|hotcommon' rpc/` 结果为空）。这与 `FileTree/hot/rpc.md` §12.1 的既有断言一致，本次独立复核通过。

⚠️ 但「零调用点」不等于「可以删」：`codeMsg` 必须**整张保留**——`GetErrMsg` 的未知码回退（`:78-80`）依赖 `codeMsg[Error]` 存在，而 `bizCodeByMsg` 的构建（`:63-74`）在**包初始化时**遍历整张表，缺项会让未初始化就反查。

### 🔴 码表三方冲突（这是本文件最硬的约束）

**hot 的 5002/5003/5004 与另外两个域的同号码语义完全不同**：

| 码值 | **hot（Go 真源）** | `users-common/errmsg/ErrorCode.java` | `article-common/errmsg/ErrorCode.java` |
|---|---|---|---|
| 5002 | `record not found`（未找到） | `ERROR_DB_UPDATE` 「更新数据库失败」 | `ERROR_DB_UPDATE` 「更新数据库失败」 |
| 5003 | `invalid parameter`（参数非法） | `ERROR_DB_SELECT` 「查询数据库失败」 | `ERROR_DB_SELECT` 「查询数据库失败」 |
| 5004 | `unauthorized`（未认证） | `ERROR_DB_INSERT` 「数据库插入失败」 | `ERROR_MINIO_UPLOAD` 「文件上传失败」 |
| 语言 | **英文** | 中文 | 中文 |

⚠️ 前两域连 5004 都不同（DB 插入 vs MinIO 上传），**三方两两冲突**，不存在「取一个通用码表」的选项。
⚠️ 且 `ArticleErrorCodes.java` 的废止注释本身已写明：「1005/1006/5004-5006 在 users/like 域另有含义，跨模块推断极易出错」。
⇒ **hot 必须自持码表，禁止 REUSE 任何既有 `ErrorCode`**。这是本文件唯一的硬约束，不受 §12.5 拍板结果影响。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| 11 个常量（`:33-46`） | `hot-common/errmsg/HotErrorCode`（enum） | ADAPT | 值属 L1，逐字照抄；**必须整表保留**（见 §12.3 可达性警告） |
| `codeMsg` 表（`:48-61`） | `hot-common/errmsg/HotErrorMessages`（私有表） | ADAPT | **与码分离**——这是 `message`/`like` 的共同做法，users/article 之所以合一是因为它们的 Go 源没有文案反查需求（§12.5 判据 1） |
| `GetErrMsg(code)`（`:76-82`） | `HotErrorMessages.getMsg(code)` | ADAPT | 未知码回退 `"FAIL"`，**回退值本身是 L1** |
| `bizCodeByMsg`（`:63-74`） | **不迁移** | — | 唯一消费者是 `BizCodeFromError`，后者零调用点（§12.5 判据 2）。⚠️ 若日后迁移必须用 `putIfAbsent` 循环与 Go 等价（§12.3），**不得**顺手「修正」Go 侧 map 遍历的随机性 |
| `CodeError` 异常载体（`:10-13`） | `com.rag.common.exception.BusinessException` | REUSE | 同为 unchecked + `(int code, String msg)`；users 域已验证的同一做法，**不自建异常类** |
| `(*CodeError).Error()` 文案（`:15-17`） | **不迁移** | — | Go 的 `"ErrCode:%d, Errmsg:%s"` 仅用于 `logger.LogBusinessErr` 的日志输出；RAG 侧日志文案属表达层，且该调用点已由 `logger.LogBusinessErr(ctx, code, e)` 覆盖，**不构成可观察差异** |
| `NewErrCode(code)`（`:19-24`） | **不迁移**（等价于 `new BusinessException(code, HotErrorMessages.getMsg(code))`） | — | 唯一等价物，单独建一个工厂方法只是镜像 Go 函数名 |
| `NewErrCodeMsg(code,msg)`（`:26-31`） | **不迁移** | — | 零调用点（grep 验证） |
| `GRPCError(grpcCode, bizCode)`（`:84-86`） | **不迁移** | — | 零调用点（grep 验证）。message 域有真实调用才建 `MessageGrpcError`，hot 不照抄 |
| `BizCodeFromError(err)`（`:88-121`） | **不迁移，但保留设计** | — | 零调用点。§12.3 的判定顺序必须留在此文档，日后 hot 若要按 gRPC code 细分错误码时**照此实现，不要临时另写一套** |

⚠️ 四个函数的「不迁移」全部依据**当前零调用点**，属**静态 grep 结论**，不是运行期验证。若 hot 未来新增 gRPC 消费方，这四条要重新评估，并按 §12.5 的边界说明补 `HotErrorGrpcError`（照 `MessageGrpcError` 的形状）。

---

## 12.5 第五部分：理论 Java 文件树

**已定落点 = `hot-common`**（2026-10-22 实施；依据见 §12.7 不通过项 #1 与 #2）：

```
service/hot/hot-common/src/main/java/com/rag/hot/hot/common/errmsg/
├── HotErrorCode.java          # enum：11 个码值 + getCode()，**只管码，不管文案**
└── HotErrorMessages.java      # code→msg 表 + getMsg(code)，未知码回退 "FAIL"
```

配套测试：

```
service/hot/hot-common/src/test/java/com/rag/hot/hot/common/errmsg/
├── HotErrorCodeContractTest.java     # 11 个码值逐字锁死（防手抄漂移）
└── HotErrorMessagesContractTest.java # 11 条文案逐字锁死 + 未知码回退 "FAIL"
```

**只有 2 个类**。异常载体用 `com.rag.common.exception.BusinessException`，**不建** `HotCodeError` / `HotGrpcError`。

### 为什么是 2 个而不是 3~4 个：先例的实测对照

RAG 已有三个 `-common` 模块承载 errmsg，拆法**并不一致**，必须逐个核：

| Go 侧 errmsg | 有 `GRPCError` + `BizCodeFromError`？ | RAG 落点 | 类数 | 是否成比例 |
|---|---|---|---|---|
| `service/user/common/errmsg/errmsg.go` | ❌ 无 | `users-common/errmsg/ErrorCode`（enum 内直接带 msg） | 1 | ✅ |
| `service/article/common/errmsg/errmsg.go` | ❌ 无 | `article-common/errmsg/ErrorCode` | 1 | ✅ |
| `service/message/common/errmsg/errmsg.go` | ✅ 有 | `MessageErrorCode` + `MessageErrorMessages` + `MessageGrpcError` | 3 | ✅ |
| `service/like/common/errmsg/errmsg.go` | ❌ **无** | `CodeError` + `LikeErrorCode` + `LikeErrorMessages` + `LikeGrpcError`（**258 行**） | 4 | ❌ **超建** |
| **`service/hot/common/errmsg.go`** | ✅ **有** | 本文件 | **2** | ✅ |

三条可复用的判据（都是从这张表里读出来的，不是拍脑袋）：

1. **码与文案必须分离**——`message` 与 `like` 都拆开了；`users` / `article` 之所以能合成一个类，是因为它们的 Go 源本来就没有文案反查需求。hot 有 `codeMsg` 表和 `GetErrMsg`，**拆**。
2. **gRPC 错误类只在 `BizCodeFromError` 真的被调用时才建**。`message` 建了 `MessageGrpcError`（256 行）是因为 message 真在用；`like` 的 Go 源**根本没有 `BizCodeFromError`**，却建了一个 258 行的 `LikeGrpcError`——**这是本仓库现存的超建样本，不要照抄**。hot 的 `GRPCError` / `BizCodeFromError` / `NewErrCodeMsg` 经 grep 验证**全部零调用点**，因此**不建**。
3. **异常类复用 `com.rag.common.exception.BusinessException`**，这是 `users` 域已验证的做法（`users-common/errmsg/ErrorCode` 的 javadoc 明写用法 `new BusinessException(ec.getCode(), ec.getMsg())`）。hot 侧两个可达成员 `CodeError`（`hotcenter.go:56`）与 `NewErrCode`（`getHotArticlesLogic.go:68,72`）**都能被它等价覆盖**，无需自建。

⚠️ **一个必须说清的边界**：hot 的 Go 源与 message 的**逐符号同构**（同样 6 个函数、同样 `codeMsg`/`bizCodeByMsg`、同样前 11 个码 200/500/1015/5001-5008），所以**结构上 message 才是最贴近的先例**。选 2 类而非 3 类，**唯一的理由是可达性**——不是嫌 message 的拆法不好。若日后 hot 接上 gRPC 消费方、这两个函数转为可达，**应立即按 `MessageGrpcError` 的形状补第三个类**，而不是另创一套。

### 若改判为落在 `FileTree/hot/api.md` 原选择（**本次未采纳**）

若日后改判为落在 `hot-api` 的 `service` 包（而非 `hot-common`），本节改为：

```
service/hot/hot-api/src/main/java/com/rag/hot/hot/api/service/
├── HotErrorCode.java          # enum：11 个码值
└── HotErrorMessages.java      # code→msg 表 + getMsg(code)
```

⚠️ 无论选哪个模块落点，**「码 / 文案分离」这条不变**——两种落点下都是 2 个类。

⚠️ 两种落点**必居其一**，且选完必须同步回 `FileTree/hot/api.md` §12.1 / §12.5 的对应两处 —— 两份文档对同一份码表的落点描述不得不一致。
**本次已选 `hot-common`，`api.md` 两处已同步（2026-10-22）。**

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `errmsg.go`（11 个常量 `:33-46`） | `hot-common/errmsg/HotErrorCode`（enum） | ADAPT | L1 冻结值，逐字照抄，**整表保留** |
| `errmsg.go`（`codeMsg` `:48-61`） | `hot-common/errmsg/HotErrorMessages`（私有表） | ADAPT | 与码分离（§12.5 判据 1） |
| `errmsg.go`（`GetErrMsg` `:76-82`） | `HotErrorMessages.getMsg` | ADAPT | 含 `"FAIL"` 回退语义 |
| `errmsg.go`（`bizCodeByMsg` `:63-74`） | **不迁移** | — | 唯一消费者是 `BizCodeFromError`，后者零调用点（§12.5 判据 2）。⚠️ 若日后迁移必须用 `putIfAbsent` 循环，与 Go 等价（§12.3） |
| `errmsg.go`（`CodeError` `:10-13`） | `com.rag.common.exception.BusinessException` | REUSE | 同构；users 域已验证的同一做法 |
| `errmsg.go`（`NewErrCode` `:19-24`） | 不迁移，等价式 `new BusinessException(code, HotErrorMessages.getMsg(code))` | — | 唯一等价物，单独建工厂方法只是镜像 Go 函数名 |
| `errmsg.go`（`(*CodeError).Error()` `:15-17`） | 不迁移 | — | `"ErrCode:%d, Errmsg:%s"` 仅用于 Go 日志文案；调用点已由 `logger.LogBusinessErr(ctx, code, e)` 覆盖，属表达层 |
| `errmsg.go`（`NewErrCodeMsg` `:26-31`） | 不迁移 | — | 零调用点（grep 验证） |
| `errmsg.go`（`GRPCError` `:84-86`） | 不迁移 | — | 零调用点（grep 验证）。message 域有调用才建 `MessageGrpcError`，hot 不照抄 |
| `errmsg.go`（`BizCodeFromError` `:88-121`） | 不迁移，**但判定顺序必须留档** | — | 零调用点。§12.3 已录完整判定顺序，日后若转可达应按该顺序实现，不要临时另写一套 |
| —— | `service/hot/hot-common/pom.xml` | **已存在并已启用** | 原为空骨架（26 行）；本次落地时补齐零生产依赖说明、落点决策记录与 test scope 的 junit-jupiter（14 条契约测试需要） |
| —— | `service/hot/hot-common/src/test/java/.../errmsg/HotErrorCodeContractTest.java` | **新增** | 5 条：码值逐条比对、声明顺序、唯一性、总数、裸 int 产出 |
| —— | `service/hot/hot-common/src/test/java/.../errmsg/HotErrorMessagesContractTest.java` | **新增** | 9 条：文案逐条比对、双射、表内顺序、未知码回退、文案两两互异、英文语种、跨域同码不同义、表不可变 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 11 个码值 + 11 条文案逐字录于 §12.3；`GetErrMsg` 未知码回退 `"FAIL"`；msg 语言为英文 |
| L2 业务语义有承载位置 | ✅ 码表反查（`bizCodeByMsg`）与 gRPC 错误还原（`BizCodeFromError`）两套机制的**判定顺序**均已留档（§12.3），不是「知道有这功能」而是「知道它按什么顺序判」 |
| 框架吸收有机制依据 | ✅ 异常复用 `BusinessException`，未新建平行异常类 |
| 新增文件有职责依据 | ✅ 两个类的切分依据是**职责边界**而非文件名：`HotErrorCode` 只管码、`HotErrorMessages` 只管文案。切分判据来自本仓库三个已落地 `-common` 的实测对照（§12.5），不是「Go 有同名文件」 |
| 无「为对应 Go 文件而建」的文件 | ✅ **不建** `HotGrpcError`（`GRPCError`/`BizCodeFromError` 零调用点；`like` 域在 Go 源无 `BizCodeFromError` 的情况下建了 258 行的 `LikeGrpcError`，是**反面样本**）；**不建** `HotCodeError`（异常由 `BusinessException` 承担）；**不建** `HotErrorGrpcError` 的预留骨架（为死代码预留结构，等于把超建推迟到实施期） |
| 与同仓先例的比例性 | ✅ hot 选 2 类；`message` 3 类（其 gRPC 转换真被调用）、`users`/`article` 1 类（其 Go 源无 `bizCodeByMsg`）、`like` 4 类（**超建**）。类数随可达职责变化，不是随域变化 |
| 未把未实现能力说成现有机制 | ✅ 推导期：`hot-common` 当时如实标注为「空骨架、0 个 Java 文件」，§12.5 的落点标注为「**待拍板**」而非既成事实。**实施期（2026-10-22）**：2 个类 + 14 条契约测试已落地，落点已定；未迁移的 4 个 Go 函数（`bizCodeByMsg` / `NewErrCodeMsg` / `GRPCError` / `BizCodeFromError`）在 §12.4 / §12.6 中**逐条留档理由**，且**没有**为它们预留空壳 |

### 不通过项（**均已于 2026-10-22 实施时关闭**）

| # | 问题 | 详情 | 关闭方式 |
|---|---|---|---|
| **1** | 🔴 **`FileTree/hot/api.md` 引用的先例已被推翻** | `api.md` §12.1 原文：「按 `FileTree/article/api.md` 已批准的先例（`service/ArticleErrorCodes`），**不建** `-common` 模块」。**实测**：该文件现已改写为「**骨架占位 · 已废止，请勿在此实现常量**」，并注明真正落点是 `com.rag.article.article.common.errmsg.ErrorCode`（模块 `service/article/article-common`，15 个码，已与 Go 真源对拍）。⇒ **`api.md` 援引的先例已不存在**，其「不建 `-common` 模块」的结论失去了原有依据 | ✅ `api.md` §12.1 / §12.5 两处已改写为「落点 = hot-common」，不再援引该先例 |
| **2** | 🔴 **「不建 `-common` 模块」与磁盘现状矛盾** | `service/hot/hot-common/pom.xml` **已存在**。`api.md` 的表述暗示该模块不存在，而它就在那里 ⇒ 无论最终选哪种落点，**这句描述都必须改写** | ✅ `api.md` 已改为「`hot-common` 已存在且已落地」 |
| **3** | **落点口径三域不一致** | 现状：`users` / `message` / `article` 三域**都有**独立 `-common` 模块承载 errmsg；只有 `hot` 的文档说「不建」。四种做法里三对一，**建议统一到 `-common`**（本文件 §12.5 的推荐落点即基于此） | ✅ hot 落在 `hot-common`，四域口径统一 |

| **4** | **`article` 的废止注释已预判过这类冲突** | `ArticleErrorCodes.java` 原文警告「两处定义会造成『改了一处、另一处没改』的静默分叉」。同一模式已在 article 域真实发生过一次（占位类与真类并存）⇒ hot 若维持「两份文档各写一份码表」的形态，风险形态相同 |

### UNKNOWN 清单

| 项 | 说明 | 状态 |
|---|---|---|
| **常量落点（首要）** | 落 `hot-common/errmsg/`（2 个类）还是 `hot-api/service/`（2 个类）。影响：是否需要 `hot-api/pom.xml` 依赖 `hot-common`；是否承认「hot-rpc 零引用却共享同一模块」这一事实。**需拍板**。⚠️ 无论选哪个，**类数都是 2**——落点与职责切分是两个独立决定 | ✅ **已拍板：`hot-common`**（2026-10-22）。`hot-api/pom.xml` 的依赖已在位，`service/hot/pom.xml` 的 `<module>` 已在位 |
| **若选 in-module，hot-rpc 的零引用怎么处理** | 现状 rpc 侧零引用（grep 验证）。若码表进 `hot-common`，rpc 模块就有了「依赖了但不用」的模块；是否接受，需拍板 | ✅ **可接受**：`hot-rpc/pom.xml` **未**声明对 `hot-common` 的依赖 ⇒ 不存在「依赖了但不用」 |
| **`codeMsg` 中 5002-5008 是否保留** | 在 hot 内不可达（§12.3）。保留是为对齐 Go 常量表 + 支撑 `bizCodeByMsg` 整表构建；删除会让 Go ↔ RAG 对照时出现「Go 有、RAG 无」的表长差异。倾向保留，但**是否保留需拍板** | ✅ **保留 11 条整表**（已实施）。5002-5008 在 hot 域当前不可达，但整表保留是 L1 对照的前提，且删除会让 `bizCodeByMsg`（若日后启用）失去构建基础 |
| **`BizCodeFromError` 的 msg→code 反查** | 当前零调用。保留判定顺序留档（§12.3）即足够，**不建类**；但若 hot 未来接入 gRPC 消费方，需重新评估是否落地 | ⏸ **维持不迁移**（已实施）。判定顺序已留档于本文件 §12.3 |
| **`CodeError.Error()` 的日志文案** | `"ErrCode:%d, Errmsg:%s"` 未迁移。需确认 `logger.LogBusinessErr` 在 RAG 侧的实际输出不含该格式串（否则是可观察差异） | ✅ **已查实**（2026-10-22）：Go `logger.go:118-120` 用 `err.Error()`，Java `Logger.java:243` 用 `err.getMessage()` ⇒ `error_reason` 自由文本字段少一段冗余前缀（码值在两侧都另有结构化 `error_code` 字段承载，语义信息不丢）。**HTTP envelope 不受影响**：Go `hotcenter.go:56-59` 是把 `codeErr.Code` / `codeErr.Msg` 逐字段复制进 `response.Response`，客户端可见的 code/msg 与 Java 侧 `BusinessException(code, msg)` 逐字段一致 |


---

## 附：本文件与其它文档的关系

| 文档 | 关系 |
|---|---|
| `FileTree/common.md` | **同名不同物**。那份是 `service/common`（跨服务共享，14 文件），本文件是 `service/hot/common`（hot 专属，1 文件）。边界声明见 §12.1 |
| `FileTree/hot/api.md` | 本组件的**唯一消费者**。其 §12.1 / §12.3 / §12.5 / §12.6 / §12.7 中的 errmsg 相关行已改为指向本文件的指针 |
| `FileTree/hot/rpc.md` | 零引用。其 §12.1「错误码」一行指向本文件 |
| `service/message/message-common`、`service/users/users-common`、`service/article/article-common` | 三域的 errmsg 落点，是 §12.7 不通过项 #3「口径不一致」的对照样本 |