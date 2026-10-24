# follow-common — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/follow/common`
> RAG 模块：`service/follow/follow-common`
> 目标根包：`com.rag.follow.follow.common.errmsg`
> 推导日期：2026-10-23
> 文档性质：**理论推导**。RAG 侧 `follow-common` 当前**只有 `pom.xml`、0 行 Java**，本文件是待实施的目标形态。
>
> **本文件覆盖 Go 侧全部 1 个文件（`service/follow/common/errmsg.go`，134 行），已逐行读完。**
> 与 `FileTree/hot/common.md` 的边界声明同样适用：本组件是 **follow 域专属**的码表库，
> 与跨服务共享的 `service/common`（`response` / `exception` / `observability` / `logger` …）**不是同一个东西**。

---

## 12.1 第一部分：目标 Java 项目组织依据

### RAG 当前文件树（事实快照，2026-10-23 实测）

```
service/follow/follow-common/
└── pom.xml                        # 19 行，无 <dependencies>、无 <build>
```

`service/follow/pom.xml:9-14` 已把 `follow-common` 列为 `<module>` ⇒ **模块骨架已在位，只是空的**。
`follow-common/pom.xml` 与 `follow-api/pom.xml` / `follow-rpc/pom.xml` **逐行同构**（19 行、零依赖），
与 `like` 域 2026-10-21 时的状态完全一致（见 `FileTree/like/api.md:13` 对该状态的记录）。

### 现有包（实际检查到）

`follow-common` 自身**零 Java 文件** ⇒ 包结构完全由已落地兄弟模块推导。下表全部为**实际打开过的路径**：

| 参照模块 | RAG 位置（实测） | 提供的一致性依据 |
|---|---|---|
| `message-common` | `message/message-common/src/main/java/com/rag/message/message/common/errmsg/` | **3 个类**：`MessageErrorCode`（enum，18 个码）、`MessageErrorMessages`、`MessageGrpcError`（内含嵌套 `MessageGrpcError.CodeError` + `grpcError(Status.Code, int)` + `bizCodeFromError(Throwable)`） |
| `like-common` | `like/like-common/src/main/java/com/rag/like/like/common/errmsg/` | **4 个类**：`CodeError` / `LikeErrorCode` / `LikeErrorMessages` / `LikeGrpcError` |
| `hot-common` | `hot/hot-common/src/main/java/com/rag/hot/hot/common/errmsg/` | **2 个类**：`HotErrorCode` / `HotErrorMessages`（**无** gRPC 转换类，因其 Go 源无 `BizCodeFromError`） |
| `users-common` | `users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java` | **1 个类**，码与文案合一（其 Go 源无 `codeMsg` 反查需求） |
| `article-common` | `article/article-common/src/main/java/com/rag/article/article/common/errmsg/ErrorCode.java` | **1 个类**，同上 |

**⇒ 类数不是按域定的，是按「Go 源里有哪些可达职责」定的**（这条判据出自 `FileTree/hot/common.md:212-230`，本文件独立复核后沿用）。

### 已有组件（跨模块可复用）

| 组件 | RAG 位置 | 判定 |
|---|---|---|
| `com.rag.common.exception.BusinessException` | `service/common/.../exception/BusinessException.java:26-38`（`extends RuntimeException`，构造 `(int code, String msg)`，暴露 `getCode()`） | **可用但不选它**，见 §12.5 |
| `com.rag.common.exception.AuthenticationException` | `.../exception/AuthenticationException.java:47`（`extends BusinessException`） | **REUSE**：follow-api 鉴权失败出口复用它（详见 `FileTree/follow/api.md`） |
| `com.rag.common.response.Response` | `.../response/Response.java:14-17`（`int code` / `String msg` / `Object data`） | **REUSE**：follow-api 的信封 |
| `users-common/errmsg/ErrorCode` | `users/users-common/.../errmsg/ErrorCode.java` | **禁止复用**，见下方「码表冲突」 |

### 已确认的框架机制

- 本组件是**纯内存值定义 + 纯函数库**：无 HTTP、无 gRPC 监听、无数据库、无外部交互
  （Go 侧唯一 import 是 `fmt` 与 `google.golang.org/grpc/{codes,status}`，`errmsg.go:6-7`）。
- Java 侧唯一需要的依赖是 **`io.grpc:grpc-api`**（`Status` / `StatusRuntimeException` / `StatusException`），
  由 `follow-rpc` 的 `grpc-server-spring-boot-starter` 传递而来；`follow-api` 若只用 `FollowCodeError`
  与码表，则**不需要** grpc 依赖 ⇒ **api / rpc 对本模块的依赖面不同**，见 §12.7 不通过项 #3。

### 当前 Component 可以直接复用的能力

| 能力 | 依据 |
|---|---|
| `grpcError(Status.Code, int bizCode)` 的**双向映射骨架** | `message-common/…/MessageGrpcError.java:135`（`grpcError`）+ `:195`（`bizCodeFromError`）已实现「description 反查 → 失败再按 gRPC code class 兜底」的两段式，与 follow 的 Go 源**逐行同构** |
| 契约测试的写法 | `hot-common/src/test/…/HotErrorCodeContractTest.java` + `HotErrorMessagesContractTest.java`（14 条：码值逐字、文案逐字、双射、未知码回退、跨域同码不同义、表不可变） |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **`CodeError` 落点：独立类、还是像 message 那样嵌进 `…GrpcError`** | ✅ **推导建议：独立顶层类 `FollowCodeError`**。依据：follow 的 `CodeError` **只有 api 侧用**（rpc 侧 0 引用，实测 §12.3），rpc 侧一律用 gRPC status error ⇒ 把它命名为 `…GrpcError` 的嵌套类是**命名撒谎**。message 域之所以能嵌进去，是 message 的 rpc 侧也会产出 `CodeError`；follow 不满足该前提 |
| **是否复用 `com.rag.common.exception.BusinessException` 当 `CodeError`** | ⚠️ **推导建议：不复用**（与 hot 的结论相反）。`BusinessException` 无 `getMsg()`（只有 `getMessage()`），而 Go 的 `CodeError.Msg` 与 `Code` 是**两个独立字段**、msg 可与码表文案不同（`NewErrCodeMsg` 的唯一用途就是带自定义 msg）。`message` 与 `like` 两个已落地域都自建了带 `getCode()+getMsg()` 的 `CodeError`（`MessageGrpcError.CodeError:78`、`like/…/CodeError.java:33,61,66`）⇒ 三域两对一，选自建 |
| **`GRPCError` 的参数顺序** | ✅ **已定：`(Status.Code grpcCode, int bizCode)`**，与 `MessageGrpcError.grpcError` 同款。**不是** like 的 `grpcError(int bizCode, String msg)` + trailer 形态。依据见 §12.3「为什么 follow 不该抄 like 的形状」 |
| **`follow-common` 的 maven 坐标与包名** | ✅ **推导建议：`com.rag.follow:follow-common`，包 `com.rag.follow.follow.common.errmsg`**（`follow-proto` 已固定 `com.rag.follow.follow.proto`，`service/follow/pom.xml:4-8` 的 parent 是 `com.rag:service`） |
| **`follow-api` 是否依赖 `follow-common`** | ✅ 必然依赖（码表 + `FollowCodeError`）。**但 grpc 依赖要不要一起进来**见 §12.7 不通过项 #3 |

---

## 12.2 第二部分：Go 原始文件树

```
service/follow/common/
└── errmsg.go          # 唯一文件：18 常量 + codeMsg + bizCodeByMsg + CodeError + 6 函数 = 134 行
```

**本组件无 `etc/`、无 `internal/`、无 proto、无数据库、无外部交互。**

作为调用链上下文，两个消费方（本文件只声明引用关系，分析在各自文档）：

```
shared:
service/follow/api/        → followcenter.go:47,48,54,56 与 8 个 logic 共 12 个文件引用本包
service/follow/rpc/        → follow.go 与 9 个 logic 共 10 个文件引用本包
```

---

## 12.3 第三部分：Go 文件职责分析

### `errmsg.go` 全量清单（逐段）

| 段 | 行号 | 内容 |
|---|---|---|
| `CodeError` struct | `:10-13` | `Code int \`json:"code"\`` / `Msg string \`json:"msg"\`` |
| `(*CodeError).Error()` | `:15-17` | 返回 `"ErrCode:%d, Errmsg:%s"` |
| `NewErrCode(code)` | `:19-24` | `Msg = GetErrMsg(code)`（**查表**） |
| `NewErrCodeMsg(code,msg)` | `:26-31` | `Msg = msg`（**不查表**） |
| 常量块 | `:33-53` | **18 个**（比 hot 多 7 个：5101 + 5201~5204） |
| `codeMsg` 表 | `:55-74` | 18 条码→文案，**与常量块一一对应** |
| `bizCodeByMsg` | `:76-87` | 包初始化时从 `codeMsg` 反向构建，**跳过 200/500** |
| `GetErrMsg(code)` | `:89-95` | 查表，未命中回退 `codeMsg[Error]` = `"FAIL"` |
| `GRPCError(grpcCode, bizCode)` | `:97-99` | `status.Error(grpcCode, GetErrMsg(bizCode))` |
| `BizCodeFromError(err)` | `:101-133` | 错误→业务码反查，**33 行、4 层判定** |

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
| `ErrorUserNotFound` | 5101 | `user not found` |
| `ErrorSelfRelation` | 5201 | `self relation is not allowed` |
| `ErrorRelationBlocked` | 5202 | `blocked relation exists` |
| `ErrorFollowNotFound` | 5203 | `follow relation not found` |
| `ErrorBlockNotFound` | 5204 | `block relation not found` |

⚠️ **文案全部是英文**，与 `users` / `article` 域的中文文案不同语言。
⚠️ 前 11 个码（200 / 500 / 1015 / 5001-5008）与 `service/hot/common/errmsg.go:33-46` **逐条同值同文案**，
但 **`ErrorUnauthorized` 的可达性完全不同**（见下方「码表冲突」）。

### `BizCodeFromError` 的判定顺序（照抄，顺序本身是语义）

```
err == nil                                 → 200                        (:102-104)
status.FromError(err) 返回 ok=false：
    err 是 *CodeError                        → codeErr.Code              (:107-110)
    否则                                      → 1015 CodeServerBusy      (:111)
status.FromError 成功：
    bizCodeByMsg[st.Message()] 命中           → 查到的码                   (:114-116)
    否则 switch st.Code()：                                            (:118-132)
        InvalidArgument  → 5003 / Unauthenticated → 5004 / PermissionDenied → 5005
        AlreadyExists    → 5006 / NotFound         → 5002 / Internal       → 5001
        default          → 1015
```

⚠️ **`bizCodeByMsg` 的命中优先于 code switch**（`:114` 在 `:118` 之前）。
本表中 18 条文案**两两互不相同** ⇒ 两条路径当前不会给出不同结果；但**顺序本身是语义**，迁移时不得调换。

### `GRPCError` 与 `BizCodeFromError` 是一对互逆函数（本组件存在的根本理由）

`GRPCError(codes.X, bizCode)` 写入 status 的 **message 是 `GetErrMsg(bizCode)`**（`:98`）；
`BizCodeFromError` 正是靠 `bizCodeByMsg[st.Message()]`（`:114`）把它还原回 `bizCode`。
⇒ **`bizCodeByMsg` 唯一存在的理由就是支撑这对往返**，不是冗余代码，迁移时不能当可删项。

### L2 可达性（实测，2026-10-23）

对 `service/follow/` 全树按 `followcommon.<符号>` 逐点计数（排除 `rpc/pb/` 生成物）：

| 符号 | api 侧引用数 | rpc 侧引用数 | 判定 |
|---|---|---|---|
| `Success` | 10 | 6 | 可达 |
| `GetErrMsg` | 3 | 3 | 可达 |
| `ErrorServerCommon` | 17 | 1 | 可达 |
| `ErrorUnauthorized` | **18** | 0 | **api 独占**（8 logic ×2 + 黑名单中间件 ×2） |
| `ErrorForbidden` | 4 | 0 | **api 独占**（2 个 logic ×2：`getblocklistlogic.go:48,49`、`getrecommendationslogic.go:48,49`） |
| `NewErrCode` | **36** | 0 | **api 独占** |
| `NewErrCodeMsg` | **8** | 0 | **api 独占** |
| `CodeError` | 1（`followcenter.go:56`） | 0 | **api 独占** |
| `GRPCError` | 0 | **19** | **rpc 独占** |
| `BizCodeFromError` | 1（`helper.go:40`） | **14** | **两侧共用** |
| `ErrorInvalidParam` | 0 | 2 | rpc 独占 |
| `ErrorSelfRelation` | 0 | 1 | rpc 独占 |
| `ErrorUserNotFound` | 0 | 2 | rpc 独占 |
| `ErrorRelationBlocked` | 0 | 2 | rpc 独占 |
| `ErrorDbSelect` | 0 | 16 | rpc 独占 |
| `ErrorDbUpdate` | 0 | 8 | rpc 独占 |
| `Error`（500） | 0 | 0 | 间接可达：`GetErrMsg` 未知码回退（`:92`）+ `bizCodeByMsg` 跳过（`:79`） |
| `CodeServerBusy`（1015） | 0 | 0 | **间接可达**：`BizCodeFromError` 两条 default 分支（`:111`、`:132`） |
| `ErrorNotFound`（5002） | 0 | 0 | **间接可达**：`BizCodeFromError` 的 `codes.NotFound` 分支（`:127-128`） |
| `ErrorAlreadyExist`（5006） | 0 | 0 | **间接可达**：`BizCodeFromError` 的 `codes.AlreadyExists` 分支（`:125-126`） |
| `ErrorFollowNotFound`（5203） | 0 | 0 | 🔴 **彻底死码**：无调用点，且 `BizCodeFromError` 的 switch 也不产出它 |
| `ErrorBlockNotFound`（5204） | 0 | 0 | 🔴 **彻底死码**：同上 |

⚠️ **5203 / 5204 必须整表保留**（理由与 hot 的 `FileTree/hot/common.md:154` 同）：
`codeMsg` 是 `bizCodeByMsg` 的**构建源**（`:78` 遍历整张表），删掉两条会让
`GetErrMsg(5203)` 的结果从 `"follow relation not found"` 变成回退值 `"FAIL"` ——
**那是一条 L1 可观察差异**（若日后 follow 加上「关注不存在」的分支，会拿到错误文案）。

### 🔴 码表冲突：follow 与其它域的同号码语义**部分相同、部分相反**

| 码值 | **follow（Go 真源）** | `users-common/errmsg/ErrorCode.java` | `hot-common/HotErrorCode.java` | `like-common/LikeErrorCode.java` |
|---|---|---|---|---|
| 5002 | `record not found`（未找到） | `ERROR_DB_UPDATE`「更新数据库失败」 | `ERROR_NOT_FOUND`「record not found」 | `ERROR_DB_UPDATE` |
| 5003 | `invalid parameter` | `ERROR_DB_SELECT`「查询数据库失败」 | `ERROR_INVALID_PARAM`「invalid parameter」 | `ERROR_DB_SELECT` |
| 5004 | `unauthorized` | `ERROR_DB_INSERT`「数据库插入失败」 | `ERROR_UNAUTHORIZED`「unauthorized」 | `ERROR_DB_INSERT` |
| 5007 | `database query failed` | `ERROR_DB_UPDATE`? | `ERROR_DB_SELECT` | `ERROR_JSON_MARSHAL` |
| 语言 | 英文 | 中文 | 英文 | 中文 |

⚠️ **前 4 个码 follow 与 hot 完全一致**，但 **5007 之后开始分叉**（follow 5007/5008 = DB 读写，hot 5007/5008 = DB 读写，like 5005+ 完全不同）。
⇒ **follow 必须自持码表**；「因为前 4 个和 hot 一样就 REUSE `HotErrorCode`」是**错误结论**，
一旦 follow 用到 5007/5008 就会静默取到 hot 的值（当前恰好也相同）而看不出问题，
等 follow 追加 51xx/52xx 码时就会分叉。

### ❗ 为什么 follow 的 `FollowGrpcError` **不该抄 like 的形状**

这是本文件最容易被先例带偏的一处，逐条对照：

| 维度 | **follow（Go 真源）** | like（Go 真源） | 结论 |
|---|---|---|---|
| 构造函数 | `GRPCError(grpcCode codes.Code, bizCode int)`（`errmsg.go:97`） | `NewGrpcErr(code, msg)`（`like/…/errmsg.go:33-35`） | 参数顺序相反 |
| status code 字段装的是 | **标准 gRPC code**（`codes.InvalidArgument` 等） | **业务码**（`codes.Code(5101)`，一个非法的 gRPC 码值） | **根本分歧** |
| status message 字段装的是 | `GetErrMsg(bizCode)`，即**码表文案** | 调用方传入的自定义文案 | |
| 反查手段 | `bizCodeByMsg[description]`（`:114`） | metadata trailer `rag-like-biz-code`（`LikeGrpcError.java:78-81`） | |

⚠️ **like 的形状在 Java 侧不可复刻**：`io.grpc.Status` 只接受 `Status.Code` 枚举，
`Status.fromCodeValue(5101)` 会静默退化成 `UNKNOWN` ⇒ 「把业务码塞进 status code」这条链路在 grpc-java 上**根本不存在**。
like 域正是因此才改用 trailer（`LikeGrpcError.java:78-81`）。
**follow 不需要走这条歪路** —— 它的 Go 源本来就是「标准 gRPC code + 码表文案作 message」，
⇒ 直接照 `MessageGrpcError` 的两段式实现即可**逐行保真**，无需 trailer、无需任何偏离留档。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| 18 个常量（`:33-53`） | `follow-common/errmsg/FollowErrorCode`（enum） | ADAPT | 值属 L1，逐字照抄；**必须整表保留**（5203/5204 虽死但参与 `bizCodeByMsg` 构建，见 §12.3） |
| `codeMsg` 表（`:55-74`） | `follow-common/errmsg/FollowErrorMessages`（私有不可变表） | ADAPT | 与码**分离**：Go 有 `GetErrMsg` 反查需求 ⇒ 判据同 hot（`FileTree/hot/common.md:226`） |
| `GetErrMsg(code)`（`:89-95`） | `FollowErrorMessages.getMsg(int)` | ADAPT | **未知码回退 `"FAIL"` 是 L1**，必须保留 |
| `bizCodeByMsg`（`:76-87`） | `FollowGrpcError` 的**私有静态逆表** | SPLIT | 它服务的是 gRPC 往返，不是文案查询；放 `FollowErrorMessages` 会让文案表知道 gRPC 的存在 |
| `bizCodeByMsg` 的「跳过 200/500」（`:79`） | 同上，建表时跳过 | ADAPT | 保证 `OK`/`FAIL` 不会被反查成业务码 |
| `bizCodeByMsg` 的「先到先得」（`:82-84`） | `putIfAbsent` 循环 | ADAPT | 与 Go 的 map 遍历**等价**：18 条文案两两互异 ⇒ 实际结果恒定。⚠️ **不得**顺手「消除随机性」 |
| `CodeError` struct（`:10-13`） | `follow-common/errmsg/FollowCodeError` | ADAPT | 需要 `getCode()` + `getMsg()` 双访问器；`BusinessException` 只有 `getCode()`/`getMessage()`，语义不同（见 §12.1） |
| `(*CodeError).Error()`（`:15-17`） | **不迁移** | — | `"ErrCode:%d, Errmsg:%s"` 只进 Go 日志；RAG 侧 `com.rag.common.logger.Logger` 有自己的结构化字段（`error_code` / `error_reason`），客户端可见的 `code`/`msg` 不经此路径 ⇒ 非可观察差异 |
| `NewErrCode(code)`（`:19-24`） | `FollowCodeError.newCode(int)` | ADAPT | 36 个 api 调用点，必须有等价工厂 |
| `NewErrCodeMsg(code,msg)`（`:26-31`） | `FollowCodeError.newCodeMsg(int, String)` | ADAPT | 8 个 api 调用点；**不查表**是它存在的全部理由 |
| `GRPCError(grpcCode, bizCode)`（`:97-99`） | `FollowGrpcError.grpcError(Status.Code, int)` | ADAPT | 19 个 rpc 调用点；形状照 `MessageGrpcError:135`，**不照 like**（理由见 §12.3） |
| `BizCodeFromError(err)`（`:101-133`） | `FollowGrpcError.bizCodeFromError(Throwable)` | ADAPT | api 1 处 + rpc 14 处；**必须连同 §12.3 的四层判定顺序一起迁移** |
| `BizCodeFromError` 的 `err == nil → 200`（`:102-104`） | 同上 | ADAPT | api 侧 `codeFromRPCError` 只在 `rpcErr != nil` 时调用（`followlogic.go:49-50`），实际不可达；保留成本为零 |
| `BizCodeFromError` 的 `!ok && *CodeError`（`:107-110`） | 同上 | ADAPT | api 侧不传 `CodeError` 进来（api 的 `CodeError` 由自己抛出，不会再进 `codeFromRPCError`）⇒ **api 侧不可达、rpc 侧不可达**；仍保留（等价成本为零，且删掉会让本类不再等价于 Go） |
| `BizCodeFromError` 的 `!ok` 兜底 1015（`:111`） | 同上 | ADAPT | 与 like 的 `LikeApiSupport.rpcError` 不同（那里 Go 给的是 5001）。**follow 给的是 1015**，必须照抄 |
| `BizCodeFromError` 的 code switch（`:118-132`） | 同上 | ADAPT | 6 个映射 + default 1015，逐条照抄 |
| `follow-common` 的 maven 模块与包路径 | `service/follow/follow-common/src/main/java/com/rag/follow/follow/common/errmsg/` | ADAPT | 先例：`message-common` / `like-common` / `hot-common` 三域均已落地 `-common` 模块；`service/follow/pom.xml:11` 已列 module |

---

## 12.5 第五部分：理论 Java 文件树

```
service/follow/follow-common/src/main/java/com/rag/follow/follow/common/errmsg/
├── FollowErrorCode.java        # enum：18 个码值 + getCode()，**只管码，不管文案**
├── FollowErrorMessages.java    # code→msg 表 + getMsg(int)，未知码回退 "FAIL"
├── FollowCodeError.java        # unchecked 异常：getCode() + getMsg() + newCode/newCodeMsg 工厂
└── FollowGrpcError.java        # grpcError(Status.Code, int) + bizCodeFromError(Throwable)
                                # + 私有 bizCodeByMsg 逆表（构建时跳过 200/500）

配套契约测试（照 hot-common 的 14 条先例）：
service/follow/follow-common/src/test/java/com/rag/follow/follow/common/errmsg/
├── FollowErrorCodeContractTest.java      # 18 个码值逐字锁死 + 声明顺序 + 唯一性 + 总数
├── FollowErrorMessagesContractTest.java  # 18 条文案逐字 + 双射 + 未知码回退 "FAIL" + 表不可变
└── FollowGrpcErrorContractTest.java      # grpcError↔bizCodeFromError 往返 + 4 层判定顺序
```

配套改动（非 Java，但实现时必须同时做）：
- `service/follow/follow-common/pom.xml`：补 `<dependencies>`（生产侧仅 `io.grpc:grpc-api`；
  测试侧 `junit-jupiter`）。⚠️ 见 §12.7 不通过项 #3 对 grpc 依赖可见性的讨论。

### 为什么是 4 个类：先例的实测对照

| Go 侧 errmsg | 有 `GRPCError` + `BizCodeFromError`？ | `CodeError` 被谁用？ | RAG 落点 | 类数 |
|---|---|---|---|---|
| `service/hot/common/errmsg.go` | ✅ 有（但**零调用点**） | 仅 api | 2 类 | ✅ 成比例 |
| `service/message/common/errmsg/` | ✅ 有（**真被调用**） | api + rpc | 3 类（`CodeError` 嵌在 `MessageGrpcError` 内） | ✅ 成比例 |
| `service/like/common/errmsg/` | ❌ **无** | 仅 api | 4 类 | ❌ **超建**（`FileTree/hot/common.md:221` 已判定为反面样本） |
| **`service/follow/common/errmsg.go`** | ✅ **有，且 19 + 14 = 33 个真实调用点** | **仅 api**（rpc 0 引用） | **本文件：4 类** | ✅ 成比例 |

四条可复用判据（均从这张表读出，非拍脑袋）：

1. **码与文案必须分离** —— `message` / `hot` 都拆开了；`users` / `article` 合一是因为它们的 Go 源无 `codeMsg` 反查需求。follow 有 ⇒ 拆。
2. **gRPC 转换类只在 `GRPCError` / `BizCodeFromError` 真被调用时才建** —— follow 有 33 个调用点 ⇒ 建。这条把 follow 与 like 明确区分开。
3. **`CodeError` 自建而非 REUSE `BusinessException`** —— 需要 `getCode()+getMsg()` 双访问器；`message` / `like` 均自建 ⇒ 三域两对一。
4. **`CodeError` 不塞进 `FollowGrpcError` 内部** —— 因为 follow 的 rpc 侧**零引用** `CodeError`（实测 §12.3），塞进去是命名撒谎。这是 follow 与 message 的唯一结构差异，且**有实测依据**。

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `errmsg.go`（18 个常量 `:33-53`） | `follow-common/errmsg/FollowErrorCode`（enum） | ADAPT | L1 冻结值逐字照抄，**整表保留**（含死码 5203/5204） |
| `errmsg.go`（`codeMsg` `:55-74`） | `follow-common/errmsg/FollowErrorMessages`（私有表） | ADAPT | 与码分离（§12.5 判据 1） |
| `errmsg.go`（`GetErrMsg` `:89-95`） | `FollowErrorMessages.getMsg(int)` | ADAPT | 含 `"FAIL"` 回退语义（L1） |
| `errmsg.go`（`bizCodeByMsg` `:76-87`） | `FollowGrpcError` 的私有静态逆表 | SPLIT | 它服务 gRPC 往返而非文案查询 |
| `errmsg.go`（`bizCodeByMsg` 的跳过与 putIfAbsent `:79-84`） | 同上，建表循环 | ADAPT | 与 Go 等价；⚠️ 不得「消除 map 遍历随机性」 |
| `errmsg.go`（`CodeError` struct `:10-13`） | `follow-common/errmsg/FollowCodeError` | ADAPT | `getCode()+getMsg()`；不复用 `BusinessException`（理由 §12.5 判据 3） |
| `errmsg.go`（`NewErrCode` `:19-24`） | `FollowCodeError.newCode(int)` | ADAPT | 36 个 api 调用点 |
| `errmsg.go`（`NewErrCodeMsg` `:26-31`） | `FollowCodeError.newCodeMsg(int, String)` | ADAPT | 8 个 api 调用点；**不查表**是它存在的理由 |
| `errmsg.go`（`(*CodeError).Error()` `:15-17`） | **不迁移** | — | 仅 Go 日志文案；客户端可见字段不经此路径 |
| `errmsg.go`（`GRPCError` `:97-99`） | `FollowGrpcError.grpcError(Status.Code, int)` | ADAPT | 19 个 rpc 调用点；形状照 message，**不照 like** |
| `errmsg.go`（`BizCodeFromError` `:101-133`） | `FollowGrpcError.bizCodeFromError(Throwable)` | ADAPT | api 1 + rpc 14；**四层判定顺序必须逐条迁移** |
| —— | `com.rag.common.exception.AuthenticationException` | REUSE | follow-api 的 401 出口用它（api 侧文档另述） |
| —— | `com.rag.common.response.Response` | REUSE | follow-api 的信封（api 侧文档另述） |
| —— | `com.rag.common.exception.BusinessException` | **不选** | 无 `getMsg()`；与 Go `CodeError` 的双字段语义不同（§12.5 判据 3） |
| —— | `hot-common/HotErrorCode` / `users-common/ErrorCode` | **禁止复用** | 5007 之后语义分叉（§12.3「码表冲突」） |
| —— | `like-common/LikeGrpcError` | **禁止复用其形状** | like 把业务码塞进 status code，Java 侧不可复刻；follow 不需要（§12.3） |
| —— | `service/follow/follow-common/pom.xml` | ADAPT | 现为 19 行空壳；需补 `grpc-api` + test scope 的 junit |
| —— | `FollowErrorCodeContractTest` / `FollowErrorMessagesContractTest` / `FollowGrpcErrorContractTest` | NEW | 照 `hot-common` 的 14 条契约测试先例，防止手抄漂移 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 18 个码值 + 18 条文案逐字录于 §12.3；`GetErrMsg` 未知码回退 `"FAIL"`；文案语种为英文；`CodeError` 的 `{code,msg}` 双字段形状 |
| L2 业务语义有承载位置 | ✅ `bizCodeByMsg` 反查与 `BizCodeFromError` 互逆关系的**判定顺序**已留档（4 层，含「逆表命中优先于 code switch」）；`bizCodeByMsg` 跳过 200/500 的原因（否则 `OK`/`FAIL` 会被反查成业务码）已留档 |
| L3 技术语义有承载方案 | ✅ gRPC status 双向转换可 1:1 复刻（标准 code + 码表文案作 description），**无需 trailer、无需偏离**；`!ok` 兜底 1015 与 like 的 5001 差异已显式记录 |
| 业务分支与异常路径已纳入 | ✅ `BizCodeFromError` 的 4 类入口（nil / 非 status + CodeError / 非 status 其它 / status + 逆表命中 / status + 未命中 switch）全部纳入 |
| 框架吸收有机制依据 | ✅ 本组件**无框架吸收项**（纯值定义 + 纯函数）；唯一「被框架承担」的是 `Status` 的构造，由 grpc-java 承担 |
| 复用组件已明确 | ✅ `AuthenticationException`、`Response`（api 侧）；明确**不选** `BusinessException`、**不复用** `HotErrorCode` / `LikeGrpcError` |
| 新增文件有职责依据 | ✅ 4 个类的切分依据是**职责边界**（码 / 文案 / 业务异常 / gRPC 转换），不是文件名；类数由 §12.5 的四域实测对照表推出 |
| 合并有组织依据 | ✅ `bizCodeByMsg` 并入 `FollowGrpcError`（唯一消费者是 `BizCodeFromError`，与 hot 的判据 2 同源） |
| 拆分可解释 | ✅ `bizCodeByMsg` 从「与文案表同放」拆到「与 gRPC 转换同放」，因为它的存在理由是 gRPC 往返 |
| 无「为对应 Go 文件而建」的文件 | ✅ **未创建** `Errmsg.java` / `CodeError` 的 Go 同名包 / `ServiceContext.java`；死码 5203/5204 保留在**码表**里而不是为它们建类 |
| 未把未实现能力说成现有机制 | ✅ `follow-common` 当前**只有 pom、0 行 Java**，已在 §12.1 声明；§12.5 全部为**推导建议**，未描述成既成事实 |
| 错误文案未误判为可观察 | ✅ `(*CodeError).Error()` 标为「不迁移」并给出理由（日志路径），未声称「等价」 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | 🔴 **`follow-api` 对 grpc 依赖的可见性未定** | `FollowGrpcError` 需要 `io.grpc.Status` / `StatusException`。若 `follow-api` 的 pom 只依赖 `follow-common` 而 `follow-common` 的 grpc 依赖是 `compile` scope，**grpc 会被传递进 api 模块**（message 域的现状即如此：`MessageGrpcError` 在 message-common 里，message-api 依赖它）。这会让一个**纯 HTTP 网关**拿到 gRPC 栈。两个可选解：(a) 接受传递（与 message 一致，零改动）；(b) 把 `FollowGrpcError` 拆到第二个 maven 模块。**本文件选择 (a)**（一致性优先，message 已这么做），但需在实施时确认 `follow-api` 的启动日志里没有多余的 gRPC 自动配置 |
| 2 | 🔴 **`RpcServerInterceptorFactory` 的 javadoc 与实测结论冲突** | `service/common/…/spring/RpcServerInterceptorFactory.java:14-16` 的 javadoc 声称「grpc-spring-boot-starter **会自动**注册所有 `ServerInterceptor` Bean」，而 `:18-23` 的同一段又说「必须 `@Import` 显式引入」；`message-rpc/…/MessageRpcApplication.java:104-108` 则提供了**第三种**、也是唯一被真实验证过的做法：`GrpcServerConfigurer` + `server.intercept(...)`。⇒ 同一仓库三处说法不一致。**本文件不依赖这个组件**（`follow-common` 不产 Bean），但 `FileTree/follow/rpc.md` 必须采用 `MessageRpcApplication` 的第三种做法 |
| 3 | ⚠️ **`GetErrMsg` 的未知码回退值在 Java 侧容易被写成 `null`** | Go `:90-94` 的回退是 `codeMsg[Error]`（= `"FAIL"`）。若 Java 用 `Map.getOrDefault(code, "FAIL")` 之外的写法（例如 `Optional.ofNullable(map.get(code)).orElse(null)`），未知码会回成 `null` msg ⇒ HTTP 信封里出现 `"msg":null`，这是**客户端可见的 L1 差异**。必须由 `FollowErrorMessagesContractTest` 的「未知码回退」用例锁死 |
| 4 | ⚠️ **5203 / 5204 是死码，容易在「清理」时被删** | 一旦被删，`GetErrMsg(5203)` 回退成 `"FAIL"`，且 `bizCodeByMsg` 少两条。这两条都是可观察的（只是当前无人调用）。⇒ 契约测试必须断言**总数 == 18** 且文案两两互异 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **5203 / 5204 的业务意图** | `ErrorFollowNotFound` / `ErrorBlockNotFound` 在 Go 侧无任何调用点。是「预留未实现」还是「实现后被删」无法从源码判断。删除 `Follow` / `Unfollow` / `Block` / `Unblock` 是否应当返回「关系不存在」错误，**需业务拍板**（当前 Go 侧这 4 个 RPC 对「删不存在的行」一律返回成功） |
| **`GRPCError` 产生的 status 在客户端是否被区分** | `bizCodeByMsg` 依赖 description 反查业务码。grpc-java 的 `StatusRuntimeException` 在跨语言传输时 description 会被原样保留，但**若中间有代理/网关改写 message，逆查会失效并退化到 code switch**。RAG 侧无真流量 ⇒ 未验证 |
| **`BizCodeFromError` 在 Java 侧对「非 status 异常」的判定** | Go 用 `status.FromError(err)` 的 `ok` 标志。grpc-java 的 `Status.fromThrowable(Throwable)` 对非 status 异常返回 `null`、对 `StatusException`/`StatusRuntimeException` 返回对应 `Status`——语义**大体**对齐，但 `ok` 标志与「返回 null」的等价关系需在实施时用真探针核对（AGENTS.md §3.3.2：此类以「Go: …」措辞的断言必须能给消费点证据） |
| **`follow-common` 是否需要独立 `<version>` 管理** | 现有 4 个 `-common` 模块（message / like / users / article）均由根 `service/pom.xml` 的 `dependencyManagement` 统一管版本。follow 沿用即可，**无需新机制**；此处仅作记录 |