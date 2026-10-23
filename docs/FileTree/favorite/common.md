# favorite-common — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/favorite/common`（**1 个非测试文件**，`errmsg.go` 138 行，本文件已逐行读完）
> 目标根包：`com.rag.favorite.favorite.common.errmsg`（**落点见 §12.5**）
> 推导日期：2026-10-23
>
> **本文件是「理论推导」，不是实施记录。** `service/favorite/favorite-common/pom.xml` 只有 `<artifactId>` 与 `<properties>`，**零依赖**，`src/` 下 **0 个 `.java` 文件**。
>
> **格式母版**：`docs/FileTree/hot/common.md`（同样是「单文件 common」组件，同样要回答"该建几个类"）。
> **结构母版**：`docs/FileTree/message/common` 对应的 `message-common`（Go 源与 favorite **逐符号同构**：都有 `codeMsg` + `bizCodeByMsg` + `GRPCError` + `BizCodeFromError`）。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

| RAG 位置 | 实测状态（2026-10-23） |
|---|---|
| `service/favorite/favorite-common/pom.xml:12-18` | 存在，**零生产依赖**、零测试依赖 |
| `service/favorite/favorite-common/src/` | **不存在** |
| `service/favorite/pom.xml:18` | 已声明 `<module>favorite-common</module>` ⇒ **无需新建 maven 模块** |

### ⚠️ 边界声明一：本文件与 `FileTree/common.md` 是两个不同组件

全仓有**三个**都叫 `common` 的东西，混淆会导致把跨服务共享能力错当 favorite 专属、或反之：

| | 本文件 | `FileTree/common.md` | 各域的 `-common` 模块 |
|---|---|---|---|
| Go 路径 | `service/favorite/common/` | `service/common/` | `service/{users,message,article,hot,like}/*-common/` |
| RAG 位置 | `service/favorite/favorite-common/` | `service/common/` | 同名各域模块 |
| 文件数 | **1**（`errmsg.go`） | 14（`communityfact` / `logger` / `observability` / `response` / `snowflake` / …） | 1–3 个 errmsg 类 |
| 消费者 | **favorite-api + favorite-rpc 双方** | 所有业务模块 | 各域自用 |
| 内容 | 业务码表 + gRPC 错误双向转换 | 通用中间件 / 信封 / 日志 / 雪花 ID | 业务码表 + 双向转换 |
| 是否有 errmsg | **有** | **无** | 有 |

### ⚠️ 边界声明二：Go 侧的**单文件形态** vs comment 域的**目录形态**

| | favorite（本文件） | comment |
|---|---|---|
| Go 路径 | `service/favorite/common/errmsg.go` | `service/comment/common/errmsg/errmsg.go`（假设形态，以 comment 域文档为准） |
| Go `package` 名 | **`common`**（`errmsg.go:1`） | `errmsg` |
| Go 侧是否存在 `internal/` 等子层 | ❌ 无。整个包只有这一个文件 | 有子目录 |

🔴 **这个形态差异不构成职责边界的证据**，理由三条：

1. **Go 的「单文件包」是打包产物，不是职责声明。** `package common` + 单文件意味着 Go 侧这个包**没有内部子层**；它承载的职责仍是「码表 + 双向转换」，与 message/hot 完全同构（§12.3 的逐符号对照）。
2. **职责边界由「谁消费」决定，不由目录深度决定。** favorite 的 `errmsg.go` 被 **api（7 个 handler + 5 个 logic + 1 个中间件）与 rpc（7 个 logic + `helper.go`）双方**引用（§12.3 的可达性实测）⇒ **必须自持 maven 模块**，且该模块**已存在**（`service/favorite/pom.xml:18`）。
3. **反过来，comment 那种目录形态也不构成"favorite 应该更小"的依据** —— 两者最终都是「一个域的码表被本域两个组件共用」。

⇒ 结论：**形态差异在本文件里被显式消解**，最终落点按 RAG 四域一致口径（见 §12.5）。

### 可直接复用的现有能力

| RAG 组件（`文件:行号`） | 说明 |
|---|---|
| `com.rag.common.exception.BusinessException`（`service/common/.../exception/BusinessException.java:26-38`） | unchecked + `(int code, String msg)`，与 Go 的 `CodeError` 形状同构。**但 favorite 的 Go 侧没有 `CodeError` 类型**（实测 `favoritecommon.CodeError` 在 api / rpc 各 0 调用点、0 定义）⇒ 本域**不需要**自建异常类 |
| `com.rag.common.response.Response`（`service/common/.../response/Response.java:14-18`） | api 侧信封，字段名与 Go `service/common/response/response.go:3-7` 逐字一致 |

### 🔴 码表跨域冲突：favorite 必须自持，禁止 REUSE

逐条实测 4 个已落地 `-common` 的 500x 段（2026-10-23）：

| 码值 | **favorite（Go 真源）** | `message-common/errmsg/MessageErrorCode.java` + `MessageErrorMessages.java` | `hot-common/errmsg/HotErrorCode.java` | `users-common/errmsg/ErrorCode.java` | `article-common/errmsg/ErrorCode.java` |
|---|---|---|---|---|---|
| 5001 | 系统内部错误（`errmsg.go:49`） | `ERROR_SERVER_COMMON` **internal server error**（`:38` / `:56`） | 同 message（`:52`） | 系统内部错误（`:11`） | 系统内部错误（`:163`） |
| 5002 | **not found**（`:50`） | `ERROR_NOT_FOUND` record not found（`:41` / `:57`） | 同 message（`:55`） | 🔴 **更新数据库失败**（`:12`） | 🔴 更新数据库失败（`:166`） |
| 5003 | 参数错误（`:51`） | `ERROR_INVALID_PARAM` invalid parameter（`:45` / `:58`） | 同 message（`:58`） | 🔴 **查询数据库失败**（`:13`） | 🔴 查询数据库失败（`:169`） |
| 5004 | 未登录或登录已失效（`:52`） | `ERROR_UNAUTHORIZED` unauthorized（`:49` / `:59`） | 同 message（`:61`） | 🔴 **数据库插入失败**（`:14`） | 🔴 **文件上传失败**（`:172`） |
| 5005 | 无权限操作（`:53`） | `ERROR_FORBIDDEN` forbidden（`:53` / `:60`） | 同 message（`:64`） | 🔴 **事务操作失败**（`:15`） | 🔴 文件删除失败（`:175`） |
| 5006 | 记录已存在（`:54`） | `ERROR_ALREADY_EXIST` already exists（`:57` / `:61`） | 同 message（`:67`） | 🔴 **雪花ID生成失败**（`:16`） | 🔴 文件下载失败（`:178`） |
| 5007 | 数据库查询失败（`:55`） | `ERROR_DB_SELECT` database query failed（`:60` / `:62`） | 同 message（`:70`） | 🔴 **JSON序列化失败**（`:17`） | —（无 5007） |
| 5008 | 数据库更新失败（`:56`） | `ERROR_DB_UPDATE` database update failed（`:63` / `:63`） | 同 message（`:73`） | 🔴 **JSON反序列化失败**（`:18`） | —（无 5008） |
| 5009 | **ID生成失败**（`:57`） | —（无 5009） | —（无 5009） | 🔴 **Kafka消息发送失败**（`:19`） | —（无 5009） |
| 语言 | 中文（**5002 是英文 `not found`**） | 全英文 | 全英文 | 全中文 | 全中文 |

三条结论：

1. 🔴 **5002–5006 上，favorite 与 `users` / `article` 语义完全不同**（DB 操作 / 文件操作 / Kafka），**与 `message` / `hot` 同位但文案语言不同**（英文 vs 中文）。⇒ **不存在「取一个通用码表」的选项**，`favorite-common` 必须自持 24 码整表。
2. ⚠️ **文案是 wire 可见的**：favorite 的 `msg` 会出现在 api 侧 HTTP envelope 的 `msg` 字段（7 个 handler 的 `GetErrMsg(code)`）与 rpc 侧 gRPC status description（`GRPCError`）。⇒ 文案语言差异**可被客户端观察到**，不可当"内部措辞"处理。
3. ⚠️ **5002 是 favorite 唯一的英文文案**（`errmsg.go:50` 的 `"not found"`），其余 23 条是中文。这是 Go 源码的事实，**不得"统一成中文"**（那会改掉客户端可见的 msg）。`hot/common.md` §12.3 也记录过同类现象（hot 全英文）。

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **包名落点**：`com.rag.favorite.favorite.common`（直译 Go 的 `package common`）还是 `…common.errmsg` | ✅ **已定论：`…common.errmsg`**。依据：RAG 已有 5 个 `-common` 模块**全部**把 errmsg 放在 `…common.errmsg` 包下（实测：`users-common/…/errmsg/ErrorCode.java`、`message-common/…/errmsg/MessageErrorCode.java`、`article-common/…/errmsg/ErrorCode.java`、`hot-common/…/errmsg/HotErrorCode.java`、`like-common/…/errmsg/LikeErrorCode.java`），5:0 一致。Go 的 `package common` 是**单文件包**的打包产物，直译成 Java 包名反而与全仓口径冲突 |
| **类数（该建 1/2/3/4 个）** | ✅ **已定论：3 个**。用 `hot/common.md` §12.5 沉淀的三条判据逐条套（见 §12.5「为什么是 3 个」），结论与 `message-common`（3 类）一致 |
| **是否需要 `CodeError` 异常类** | ✅ **不需要**。Go favorite **没有** `CodeError` 类型（实测 0 定义 0 引用），而 `message-common` / `like-common` 都有 ⇒ 那两个是**因自身 Go 源有该类型**而建，不是通例。`like` 在 Go 源没有 `BizCodeFromError` 的情况下建了 258 行的 `LikeGrpcError`，被 `hot/common.md` §12.5 列为**超建反面样本** |
| **`Success(200)` 在 rpc 侧零使用** | 实测：`favoritecommon.Success` 在 `rpc/` 非测试代码中 **0 处**（唯一使用点是 api 侧 7 个 logic 的返回值）。`favorite-common` **仍须保留该常量**（`errmsg.go:9`），因为 `bizCodeByMsg` 的构建（`:81-83`）要显式跳过它，且 `BizCodeFromError(nil)` 返回它（`:109-111`） |
| **是否需要 Jackson 依赖** | ✅ **不需要**。`message-common` / `like-common` 引 `jackson-annotations` 是为了给 `CodeError` 挂 `@JsonAutoDetect` / `@JsonProperty`（`MessageGrpcError.java:76-78`）；favorite 无 `CodeError` ⇒ `favorite-common` **只需 `grpc-stub`**（`MessageGrpcError.grpcError` 返回 `StatusException`） |

---

## 12.2 第二部分：Go 原始文件树

```
service/favorite/common/
└── errmsg.go      # 138 行，package common：24 常量 + codeMsg(24) + bizCodeByMsg + 5 个函数
```

**本组件无 `etc/`、无 `internal/`、无 proto、无数据库、无外部交互、无 `config`** —— 它是一个纯内存的值定义 + 纯函数库。

---

## 12.3 第三部分：Go 文件职责分析

### `errmsg.go` 全量清单（逐段）

| 段 | 行号 | 内容 |
|---|---|---|
| `package common` | `:1` | ⚠️ 包名是 `common`，**不是** `errmsg` |
| import | `:3-6` | 仅 `google.golang.org/grpc/codes` + `status` ⇒ **唯一的外部依赖就是 gRPC** |
| `const` 块 | `:8-41` | **24 个常量**，5 个分段 |
| `codeMsg` | `:43-76` | **24 条** code → 文案 |
| `bizCodeByMsg` | `:78-89` | 包初始化时从 `codeMsg` 反向构建 |
| `GetErrMsg(code)` | `:91-97` | 查表，未命中回退 `codeMsg[Error]` |
| `bizCodeFromMessage(msg)` | `:99-102` | 反查表查表 |
| `GRPCError(grpcCode, bizCode)` | `:104-106` | `status.Error(grpcCode, GetErrMsg(bizCode))` |
| `BizCodeFromError(err)` | `:108-138` | 错误 → 业务码反查，31 行 |

### 常量与文案表（L1 冻结值，逐字抄录，24 条）

| 段 | 常量 | 值 | 文案（`errmsg.go` 行号） |
|---|---|---|---|
| — | `Success` | 200 | `OK`（`:44`） |
| — | `Error` | 500 | `FAIL`（`:45`） |
| 通用 5001-5099 | `CodeServerBusy` | 1015 | 服务繁忙（`:48`） |
| | `ErrorServerCommon` | 5001 | 系统内部错误（`:49`） |
| | `ErrorNotFound` | 5002 | **`not found`**（`:50`，⚠️ 唯一英文） |
| | `ErrorInvalidParam` | 5003 | 参数错误（`:51`） |
| | `ErrorUnauthorized` | 5004 | 未登录或登录已失效（`:52`） |
| | `ErrorForbidden` | 5005 | 无权限操作（`:53`） |
| | `ErrorAlreadyExist` | 5006 | 记录已存在（`:54`） |
| | `ErrorDbSelect` | 5007 | 数据库查询失败（`:55`） |
| | `ErrorDbUpdate` | 5008 | 数据库更新失败（`:56`） |
| | `ErrorGenerateID` | 5009 | ID生成失败（`:57`） |
| 用户 5100-5199 | `ErrorUserNotFound` | 5101 | 用户不存在（`:60`） |
| | `ErrorUserExists` | 5102 | 用户已存在（`:61`） |
| | `ErrorPasswordInvalid` | 5103 | 密码错误（`:62`） |
| | `ErrorTokenInvalid` | 5104 | token无效（`:63`） |
| | `ErrorTokenExpired` | 5105 | token已过期（`:64`） |
| 收藏夹 5200-5299 | `ErrorFavoriteFolderNotFound` | 5201 | 收藏夹不存在（`:67`） |
| | `ErrorFavoriteFolderNameEmpty` | 5202 | 收藏夹名称不能为空（`:68`） |
| | `ErrorFavoriteFolderNameExists` | 5203 | 收藏夹名称已存在（`:69`） |
| 收藏 5300-5399 | `ErrorFavoriteNotFound` | 5301 | 收藏记录不存在（`:72`） |
| | `ErrorFavoriteAlreadyExist` | 5302 | 该内容已收藏（`:73`） |
| | `ErrorFavoriteTargetEmpty` | 5303 | 收藏目标不能为空（`:74`） |
| | `ErrorFavoriteHistoryBlocked` | 5304 | 收藏暂不可撤回（`:75`） |

### `bizCodeByMsg` 的两个关键性质

1. **它跳过 200 / 500**（`:81-83`）⇒ `OK` / `FAIL` 永不被反查成业务码。
2. **它是 `codeMsg` 的逆映射，且 24 条文案逐条互不相同**（已逐条核对：中文 22 条互异 + `"OK"` / `"FAIL"` / `"not found"` 三条互异且与任何中文都不重合）⇒ `:84-86` 的「先到先得」分支在 Go 里写法上**非确定**（Go map 遍历顺序随机），**但实际结果恒定**。
   ⇒ Java 侧用 `putIfAbsent` 循环即**完全等价**，**不必也不应**为了「消除随机性」改写（那会引入一个 Go 侧不存在的假设）。
   ⚠️ 反过来：**若日后有人给 `codeMsg` 加了两条相同文案**，`bizCodeByMsg` 在 Go 侧立刻变成真随机；Java 侧用 `putIfAbsent` 会悄悄「更确定」，从而**掩盖**这个 Go 缺陷 —— 属隐性行为差异，加文案时须留意。
   （`MessageGrpcError.java:158-161` 已为 message 域写下了同一条注意事项，本文件沿用。）

### `GRPCError` 与 `BizCodeFromError` 是一对互逆函数（**互逆是设计意图，不是巧合**）

`GRPCError(grpcCode, bizCode)`（`:104-106`）写入的 description 是 **`GetErrMsg(bizCode)`**，即**业务文案**，**不是**业务码数字。
`BizCodeFromError`（`:108-138`）正是靠 `bizCodeByMsg`（`:118-120`）把这段文案反查回 `bizCode`。
⇒ **`bizCodeByMsg` 存在的唯一理由就是支撑这个往返**，迁移时**不能当成可删项**。

🔴 这条往返链路在 favorite 域有一个**必须记住的推论**：**业务码在 gRPC status 的 code 字段里是「丢失」的**（被 `codes.Code` 的取值域限死），**只靠 description 的中文文案承载**。
⇒ 文案一旦被改动（例如"顺手把 `not found` 统一成中文"），**`BizCodeFromError` 的反查就会失效**，api 侧 5 个 logic（`api/internal/logic/favorite/helper.go:44-46` 的 `codeFromRPCError`）会把 rpc 错误码退化成 `CodeServerBusy(1015)`。
这正是 AGENTS.md §1.1 列的「错误码不是 API 契约、是被塞进 gRPC status 的业务码」同类问题的加强版。

### `BizCodeFromError` 的判定顺序（照抄，**顺序本身是语义**）

```
err == nil                                   → 200 (Success)                (:109-111)
status.FromError(err) 返回 !ok               → 1015 (CodeServerBusy)          (:113-116)
status.FromError 成功：
    bizCodeByMsg 命中 st.Message()           → 查到的码                      (:118-120)
    否则 switch st.Code()：                                          (:122-137)
        InvalidArgument  → 5003    Unauthenticated → 5004    PermissionDenied → 5005
        AlreadyExists    → 5006    NotFound        → 5002    Internal        → 5001
        default          → 1015
```

⚠️ **`bizCodeByMsg` 的命中优先于 code switch**（`:118` 在 `:122` 之前）。本表文案与码一一对应，两条路径结果相同，所以顺序变化当前不可观测；**但若日后文案与 code 表解耦，这个先后次序就会变成语义**。（`MessageGrpcError.java:202-206` 已把同一顺序实现为 `BIZ_CODE_BY_MSG.get(...)` 先于 `mapStatusCodeToBizCode(...)`。）

⚠️ **favorite 的 `BizCodeFromError` 比 message 的少一条分支**：`MessageGrpcError.bizCodeFromError`（`MessageGrpcError.java:195-214`）有 `err instanceof CodeError` 分支；**favorite 的 Go 侧没有 `CodeError` 类型**（实测 `favoritecommon.CodeError` 在 `api/` 与 `rpc/` 各 0 处），所以 `:108-138` 里**没有**这一分支。⇒ Java 侧**不得**照抄 message 的这条分支，否则会引入一个 Go 侧不存在的判定。

### L2 可达性（grep 实测，2026-10-23；api / rpc 两侧分别统计，rpc 已排除 `_test.go`）

| 符号 | api 侧调用点 | rpc 侧调用点 | 合计 |
|---|---|---|---|
| `Success` | 7（7 个 logic 的返回值） | **0** | 7 |
| `GetErrMsg` | 15（7 handler + 5 logic + 1 中间件） | 0（经 `GRPCError` 间接） | 15 |
| `ErrorServerCommon` | 14 | 6 | 20 |
| `ErrorInvalidParam` | 14 | 7 | 21 |
| `ErrorUnauthorized` | 16（1 中间件 + 7 logic + rpc 错误码） | 0 | 16 |
| `BizCodeFromError` | 1（`logic/favorite/helper.go:45`） | 10 | 11 |
| `GRPCError` | 0 | **56** | 56 |
| `ErrorDbSelect` / `ErrorDbUpdate` / `ErrorGenerateID` | 0 | 20 / 10 / 4 | 34 |
| `ErrorForbidden` / `ErrorFavoriteFolderNotFound` / `ErrorFavoriteFolderNameExists` / `ErrorFavoriteHistoryBlocked` | 0 | 11 / 8 / 8 / 4 | 31 |
| `ErrorInvalidParam`（rpc） | — | 7 | — |
| `ErrorNotFound` / `ErrorUserNotFound` / `ErrorFavoriteNotFound` / `ErrorFavoriteAlreadyExist` / `ErrorFavoriteTargetEmpty` / `ErrorFavoriteFolderNameEmpty` | 0 | 2 / 2 / 2 / 4 / 2 / 4 | 16 |
| `Error`（500） | **0** | **0** | 0 |
| `CodeServerBusy` / `ErrorAlreadyExist` / `ErrorUserExists` / `ErrorPasswordInvalid` / `ErrorTokenInvalid` / `ErrorTokenExpired` | 0 | **0**（仅经 `BizCodeFromError` 的 switch / `GetErrMsg` 间接可达） | 0 |
| `CodeError` / `NewErrCode` / `NewErrCodeMsg` | **0**（且 Go 侧**无定义**） | **0** | 0 |

**三条由可达性推出的结论：**

1. 🔴 **`GRPCError` + `BizCodeFromError` 都是高可达的**（56 + 11 处调用点）⇒ **必须建 gRPC 错误转换类**。
   ⇒ 这与 `hot` 域的情况**正好相反**：`hot/common.md` §12.5 判据 2 记录 hot 的 `GRPCError` / `BizCodeFromError` 经 grep 验证**全部零调用点**，因此只建 2 个类。⇒ **favorite 不能照抄 hot 的类数**。
2. ⚠️ **6 个常量（`CodeServerBusy` / `ErrorAlreadyExist` / `ErrorUserExists` / `ErrorPasswordInvalid` / `ErrorTokenInvalid` / `ErrorTokenExpired`）在 favorite 域没有直接调用点**，但**不能删**：
   - `CodeServerBusy` 与 `ErrorAlreadyExist` 是 `BizCodeFromError` switch 的两个落点（`:125,136` 与 `:129`）；
   - 其余 4 个（5102–5105）**只经 `GetErrMsg` 间接可达**（若某天有 code 传进来，会在 `codeMsg` 里查到文案）。
   ⇒ **整表 24 条保留**是 L1 对照的前提；删除会让 Go ↔ RAG 的码表长度对不上，且让 `bizCodeByMsg` 的构建基础改变。
3. 🔴 **`CodeError` / `NewErrCode` / `NewErrCodeMsg` 在 Go 侧连定义都没有**（`errmsg.go` 全文无这三个符号）⇒ **一律不迁**。
   ⇒ `favorite-common` 不需要 `CodeError` 异常类，也不需要 Jackson 注解依赖。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| 24 个常量（`:8-41`） | `favorite-common/errmsg/FavoriteErrorCode`（enum） | ADAPT | 值属 L1，逐字照抄；**必须整表保留**（见 §12.3 可达性结论 2） |
| `codeMsg` 表（`:43-76`） | `favorite-common/errmsg/FavoriteErrorMessages`（私有表 + `getMsg(int)`） | ADAPT | **与码分离**（判据见 §12.5）；文案语言与 `not found` 的英文例外**原样保留** |
| `GetErrMsg(code)`（`:91-97`） | `FavoriteErrorMessages.getMsg(code)` | ADAPT | 未命中回退 `codeMsg[Error]` = `"FAIL"`，**回退值本身是 L1** |
| `bizCodeByMsg`（`:78-89`） | `FavoriteGrpcError` 的 `private static final BIZ_CODE_BY_MSG` + `static{}` 块 | ADAPT | 唯一消费者是 `BizCodeFromError`。⚠️ **必须用 `putIfAbsent`**，与 Go 等价（§12.3 性质 2） |
| `bizCodeFromMessage(msg)`（`:99-102`） | `FavoriteGrpcError` 内的私有方法 | MERGE | Go 里它就是一个 2 行包装；与反查表同生命周期，合入同一类 |
| `GRPCError(grpcCode, bizCode)`（`:104-106`） | `FavoriteGrpcError.grpcError(Status.Code, int)` | ADAPT | 🔴 **签名方向必须与 Go 一致**（先 gRPC code、后业务码）；**description 必须是 `getMsg(bizCode)` 而不是码字符串** —— 这是往返链路的前提（§12.3「互逆」段） |
| `BizCodeFromError(err)`（`:108-138`） | `FavoriteGrpcError.bizCodeFromError(Throwable)` | ADAPT | 判定顺序逐条对齐（§12.3 的流程图） |
| `BizCodeFromError` 的 switch 尾（`:122-137`） | `FavoriteGrpcError.mapStatusCodeToBizCode(Status.Code)` | MERGE | Go 的 switch 与主函数同函数体；拆出来只是可读性，**顺序不变** |
| `BizCodeFromError` 的 `!ok → 1015`（`:113-116`） | `bizCodeFromError` 的兜底分支 | ADAPT | Go 的 `status.FromError` 对非 status error 返回 `ok=false`；Java 侧识别不到 status 时同样兜 1015。⚠️ **不得**加 message 的 `CodeError` 分支（favorite Go 侧无该类型） |
| **无** `CodeError` 类型 | **不建** | — | Go 侧不存在（实测 0 定义）；`like-common` 的 `CodeError.java` 是**超建反面样本**（`hot/common.md` §12.5 判据 2） |
| **无** `NewErrCode` / `NewErrCodeMsg` | **不建** | — | Go 侧不存在 |
| **无** `(*CodeError).Error()` 文案 | **不迁移** | — | Go 侧无此方法 |
| api 侧 `GetErrMsg(code)` 的调用点（15 处） | `FavoriteErrorMessages.getMsg(code)` | REUSE | 调用点留在 api 侧原位（7 handler + 5 logic + 1 中间件），不改落点 |
| api 侧 `codeFromRPCError`（`api/.../helper.go:44-46`） | `favorite-common` 的 `FavoriteGrpcError.bizCodeFromError` | REUSE | api 侧因此**必须**依赖 `favorite-common` |
| rpc 侧 `favoritecommon.GRPCError(...)`（56 处） | `favoriteGrpcError.grpcError(codes.X, code)` | REUSE | 7 个 logic + `logic/helper.go` 的 4 处跨域错误，全部原样替换 |
| rpc 侧 `favoritecommon.BizCodeFromError(err)`（10 处，仅用于日志的码） | `FavoriteGrpcError.bizCodeFromError` | REUSE | 例：`create_favorite_folder_logic.go:55,71` 把 err 还原成码喂给 `logger.LogBusinessErr` |
| `favorite-common/pom.xml` | 需补依赖 | — | 现状零依赖。**最少只需 `grpc-stub`**（`MessageGrpcError`/`LikeGrpcError` 都引它；父 pom 的 `dependencyManagement` 已管 `grpc-stub` 的版本，见 `like-common/pom.xml:29`）。test scope 加 `junit-jupiter` + `grpc-inprocess` + `jackson-databind`（若要为文案表写序列化契约测试） |

---

## 12.5 第五部分：理论 Java 文件树

**落点 = `favorite-common` 的 `errmsg` 子包**（依据见 §12.1「包名落点已定论」行）：

```
service/favorite/favorite-common/src/main/java/com/rag/favorite/favorite/common/errmsg/
├── FavoriteErrorCode.java       # enum：24 个码值 + getCode()，**只管码，不管文案**
└── FavoriteErrorMessages.java   # code→msg 表（24 条）+ getMsg(int)，未知码回退 "FAIL"
└── FavoriteGrpcError.java      # grpcError(Status.Code,int) + bizCodeFromError(Throwable) + BIZ_CODE_BY_MSG 反查表

service/favorite/favorite-common/src/test/java/com/rag/favorite/favorite/common/errmsg/
├── FavoriteErrorCodeContractTest.java       # 24 个码值逐字锁死 + 声明顺序 + 唯一性 + 总数
├── FavoriteErrorMessagesContractTest.java   # 24 条文案逐字锁死 + 双射 + 未知码回退 "FAIL" + 语言混排（22 中文 + 1 英文 + OK/FAIL）
└── FavoriteGrpcErrorRoundTripTest.java     # grpcError → bizCodeFromError 往返恒等（含"改文案即断链"的负向断言）
```

**只有 3 个类。**

### 为什么是 3 个而不是 1~4 个：五条已沉淀判据的逐条套用

`hot/common.md` §12.5 沉淀了三条判据，本文件再补两条，逐条套：

| # | 判据 | 套到 favorite 上的结果 |
|---|---|---|
| 1 | **码与文案必须分离**（`message` 与 `like` 都拆开了；`users` / `article` 之所以能合成一个类，是因为它们的 Go 源本来就没有文案反查需求） | favorite 有 `codeMsg` 表（`:43-76`）+ `GetErrMsg`（`:91-97`）⇒ **拆** |
| 2 | **gRPC 错误类只在 `GRPCError` / `BizCodeFromError` 真的被调用时才建**（`message` 建了 `MessageGrpcError` 是因为真在用；`like` 的 Go 源根本没有 `BizCodeFromError`，却建了 258 行的 `LikeGrpcError` —— **超建反面样本**） | favorite 的 `GRPCError` **56 处**、`BizCodeFromError` **11 处**（§12.3 可达性）⇒ **建**。⇒ 与 `hot`（零调用 ⇒ 不建）结论相反，**不能照抄 hot 的类数** |
| 3 | **异常类复用 `com.rag.common.exception.BusinessException`**（`users` 域已验证的做法） | ⚠️ **本条不适用**：favorite 的 Go 源**连 `CodeError` 类型都没有**（实测 0 定义）⇒ 本域根本不存在需要异常承载的职责 ⇒ **既不 REUSE 也不 NEW**（REUSE 一个无人使用的异常类是另一种形式的"为对称而建"） |
| 4（新增） | **`Success` / `Error` 之外的分段完整性** | favorite 有 5 个分段（200/500、1015、5001-5099、5100-5199、5200-5299、5300-5399），其中 **5100-5199 段的 5102–5105 在本域无直接调用点**但经 `GetErrMsg` 间接可达 ⇒ 整表 24 条保留，`FavoriteErrorCode` 的 javadoc 必须写明"5102–5105 当前不可达，保留是为 L1 对照 + 反查表完整性" |
| 5（新增） | **`favorite-common` 跨 api + rpc 双方使用** ⇒ 必须自持 maven 模块（已存在），**不能**并进 `favorite-api` 或 `favorite-rpc` | 与 `like` / `message` / `hot` / `article` / `users` 五域一致 |

⚠️ **类数随可达职责变化，不是随域变化。** 当前实测对照：

| Go 侧 errmsg 源 | `GRPCError` + `BizCodeFromError` 可达？ | RAG 落点 | 类数 | 是否成比例 |
|---|---|---|---|---|
| `service/user/common/errmsg/errmsg.go` | ❌ 无 | `users-common/errmsg/ErrorCode`（enum 内直接带 msg） | 1 | ✅ |
| `service/article/common/errmsg/errmsg.go` | ❌ 无 | `article-common/errmsg/ErrorCode` | 1 | ✅ |
| `service/message/common/errmsg/errmsg.go` | ✅ 有（真在用） | `MessageErrorCode` + `MessageErrorMessages` + `MessageGrpcError` | 3 | ✅ |
| **`service/favorite/common/errmsg.go`** | ✅ **有（56 + 11 处调用点）** | 本文件 | **3** | ✅ |
| `service/like/common/errmsg/errmsg.go` | ❌ Go 源**无** `BizCodeFromError` | `CodeError` + `LikeErrorCode` + `LikeErrorMessages` + `LikeGrpcError`（258 行） | 4 | ❌ **超建** |
| `service/hot/common/errmsg.go` | ✅ 有函数但**零调用点** | `HotErrorCode` + `HotErrorMessages` | 2 | ✅ |

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `FavoriteErrorCode` | enum：24 个码值 + `getCode()`。**只管码**。javadoc 必须写明分段、跨域同名不同义、以及"5102–5105 当前不可达但保留" | `errmsg.go:8-41` | 数值是**跨服务契约**，编译期就该拦下拼错；与文案分离（判据 1） |
| `FavoriteErrorMessages` | 私有 `Map<Integer,String>`（24 条）+ `getMsg(int)`，未知码回退 `"FAIL"` | `errmsg.go:43-76,91-97` | 文案有独立的完整性与语言约束（22 中文 + 1 英文 + `OK`/`FAIL`），与码的变更原因不同 |
| `FavoriteGrpcError` | `grpcError(Status.Code, int)` + `bizCodeFromError(Throwable)` + `private static final BIZ_CODE_BY_MSG`（`static{}` 块 `putIfAbsent` 构建） | `errmsg.go:78-89,99-102,104-106,108-138` | 往返链路（`GRPCError` 写文案 → `bizCodeByMsg` 反查码）是**一个不可拆的整体**：拆开后两个类都要各自维护反查表，必然漂移 |

### 必配测试（§3.3「不是跑绿，而是对」）

纯 Mockito 单测对本组件**几乎没有意义**（它是纯函数库）。真正的风险在**数值/文案漂移**与**往返链路断裂**，因此必须写**契约测试**：

| 测试 | 锁死什么 | 为什么必须 |
|---|---|---|
| `FavoriteErrorCodeContractTest` | 24 个码值逐条比对 + 声明顺序 + 唯一性 + 总数 = 24 | 手抄 24 个数字最容易漂；且 5002–5006 与 users/article 语义冲突（§12.1），一旦误改成别的域的文案就是**跨域串码** |
| `FavoriteErrorMessagesContractTest` | 24 条文案逐条比对 + 表内双射 + 未知码回退 `"FAIL"` + **文案语言混排（`not found` 保持英文）** + 文本两两互异 | 文案是 wire 可见的（HTTP envelope `msg` / gRPC description）；改文案 ⇒ 断往返链路（§12.3） |
| `FavoriteGrpcErrorRoundTripTest` | 对每个码：`grpcError(code→msg)` 产出的 description 经 `bizCodeFromError` **必须回到同一个码**（24/24 恒等）；**负向断言**：人为把某条文案改掉后往返必须失败 | 🔴 这条是本组件唯一真正有价值的测试 —— 它直接锁住「业务码只靠中文文案承载」这个反直觉的机制 |
| 🔴 **双侧探针**（§3.3 强制两条 2） | Go 侧直接调用真源的 `favoritecommon` 包；RAG 侧用 `favorite-common` 的真类 | **只跑 RAG 的探针只能证明"代码写成了这样"，证明不了"Go 是这样"**。最小形态：同一组 24 个码，两侧输出的 `(code, msg)` 与 `(bizCode → grpcError → bizCodeFromError)` 结果 `sha256` 逐字节比对 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `errmsg.go:8-41` 24 个常量 | `favorite-common/errmsg/FavoriteErrorCode`（enum） | ADAPT | L1 冻结值，逐字照抄，**整表 24 条保留** |
| `errmsg.go:43-76` `codeMsg` | `favorite-common/errmsg/FavoriteErrorMessages`（私有表） | ADAPT | 与码分离（判据 1） |
| `errmsg.go:91-97` `GetErrMsg` | `FavoriteErrorMessages.getMsg` | ADAPT | 含 `"FAIL"` 回退语义 |
| `errmsg.go:78-89` `bizCodeByMsg` | `FavoriteGrpcError.BIZ_CODE_BY_MSG`（`static{}` + `putIfAbsent`） | ADAPT | 往返链路的基础，不可删。⚠️ 必须 `putIfAbsent`（Go 的 map 遍历非确定，本表 24 条互异故结果恒定） |
| `errmsg.go:99-102` `bizCodeFromMessage` | `FavoriteGrpcError` 内的私有查表 | MERGE | 2 行包装，与反查表同生命周期 |
| `errmsg.go:104-106` `GRPCError` | `FavoriteGrpcError.grpcError(Status.Code, int)` | ADAPT | 🔴 签名方向与 description 内容均属 L1 |
| `errmsg.go:108-138` `BizCodeFromError` | `FavoriteGrpcError.bizCodeFromError(Throwable)` + `mapStatusCodeToBizCode` | ADAPT | 判定顺序逐条对齐；⚠️ **不加** message 的 `CodeError` 分支 |
| `errmsg.go` 全文 | **无** `CodeError` / `NewErrCode` / `NewErrCodeMsg` | — | Go 侧不存在 ⇒ 不建。`like-common` 的 `CodeError.java` 是超建反面样本 |
| `errmsg.go` 全文 | `com.rag.common.exception.BusinessException` | **不 REUSE** | 与 `users`/`hot` 的结论不同：那两个域的 Go 源有 `CodeError` 需要被覆盖，**favorite 没有** ⇒ REUSE 一个无人使用的异常类是另一种"为对称而建" |
| api 侧 15 处 `GetErrMsg` | `FavoriteErrorMessages.getMsg` | REUSE | 调用点留在 api 侧原位，不改落点 |
| api 侧 `codeFromRPCError`（`api/.../helper.go:44-46`） | `FavoriteGrpcError.bizCodeFromError` | REUSE | api 侧因此必须依赖 `favorite-common` |
| rpc 侧 56 处 `GRPCError` | `FavoriteGrpcError.grpcError` | REUSE | 调用点留在 rpc 侧原位 |
| rpc 侧 10 处 `BizCodeFromError` | `FavoriteGrpcError.bizCodeFromError` | REUSE | 用于把 err 还原成码喂给 `logger.LogBusinessErr` |
| `errmsg.go:1` `package common` | 包名 `…common.errmsg` | ADAPT | 表达层；5 个已落地 `-common` 模块 5:0 一致（§12.1 已定论） |
| —— | `favorite-common/pom.xml` | **需补依赖** | 现状零依赖。生产侧**最少只需 `grpc-stub`**；**不需要** `jackson-annotations`（无 `CodeError`）、**不需要** web / grpc-server / spring |
| —— | `FavoriteErrorCodeContractTest.java` | **新增** | 24 码值 + 顺序 + 唯一性 + 总数 |
| —— | `FavoriteErrorMessagesContractTest.java` | **新增** | 24 文案 + 双射 + 回退 + 语言混排 + 两两互异 + 跨域同名不同义 |
| —— | `FavoriteGrpcErrorRoundTripTest.java` | **新增** | 24/24 往返恒等 + 负向断言（改文案即断链） |
| —— | **双侧对拍脚本**（Go ↔ Java `sha256` 比对） | **新增** | §3.3 强制两条 2；本组件是**最需要**双侧探针的组件（纯数值/文案契约） |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 24 个码值 + 24 条文案**逐条录于 §12.3 的表格**（含行号）；`GetErrMsg` 未知码回退 `"FAIL"`；文案语言混排（22 中文 + `not found` 英文 + `OK`/`FAIL`） |
| L2 业务语义有承载位置 | ✅ 🔴 **码↔文案的往返机制**及其判定顺序已逐条留档（§12.3 的流程图），不是「知道有这功能」而是「知道它按什么顺序判」 |
| 框架吸收有机制依据 | ✅ 无（本组件是纯函数库，没有可被框架吸收的职责） |
| 复用组件已明确 | ✅ 本组件**不复用任何跨域码表**（§12.1 的三方冲突表已给出实测证据）；`FavoriteGrpcError` 的**签名形态**复用 `message-common` 的既有先例（`grpcError(Status.Code, int)`，`:135`），但**实现**独立 |
| 新增文件有职责依据 | ✅ 3 个类的切分依据是**职责边界**（码 / 文案 / 往返链路），不是文件名；类数由 §12.5 的 5 条判据推出，且逐条与 5 个已落地 `-common` 对照过 |
| 无「为对应 Go 文件而建」的文件 | ✅ **不建** `FavoriteCodeError`（Go 侧无 `CodeError`）；**不建** `FavoriteNewErrCode` / `FavoriteNewErrCodeMsg`（Go 侧无此函数）；**不 REUSE** `BusinessException`（无职责需要它） |
| 与同仓先例的比例性 | ✅ favorite 选 3 类；`message` 3 类（其 gRPC 转换真被调用）、`hot` 2 类（函数零调用点）、`users`/`article` 1 类（Go 源无双向转换）、`like` 4 类（**超建**）。类数随可达职责变化，不是随域变化 |
| 未把未实现能力说成现有机制 | ✅ `favorite-common` 如实标注为「空壳、0 个 Java 文件、0 依赖」；§12.1 的包名落点标注为**已定论**（5:0 先例），其余全部标为事实或推导 |
| 不为填满目录建空组件 | ✅ 3 个类全部有可达消费者；无骨架 / 占位 / 预留结构 |

### 不通过项

| # | 问题 | 详情 | 状态 |
|---|---|---|---|
| **1** | 🔴 **`favorite-common/pom.xml` 零依赖** | `FavoriteGrpcError.grpcError` 返回 `io.grpc.StatusException` ⇒ **必须有 `grpc-stub`**，否则编译不过。⚠️ 好消息：这是本仓 `-common` 里**依赖最轻**的一个（`message-common` 还要 `jackson-annotations`，`like-common` 还要 `jackson-annotations` + test 的 `grpc-inprocess`）⇒ 无 `CodeError` ⇒ **不需要 Jackson** | ⏸ §12.6 已列出：生产侧只需 `grpc-stub` |
| **2** | ⚠️ **`Error`（500）常量在 favorite 域零调用点，但 `GetErrMsg` 的回退值依赖它** | `errmsg.go:45` 定义 `Error = 500` + `:94` 的回退 `return codeMsg[Error]`。删除 `ERROR(500)` 会让 `getMsg(未命中)` 无处可回退 | ⏸ 已作为硬约束写入 `FavoriteErrorMessages` 的实现要求 |
| **3** | ⚠️ **文案语言与 message/hot 不一致（中文 vs 英文）** | favorite 全中文（除 5002 的 `"not found"`）；`message-common/MessageErrorMessages.java:55-63` 与 `hot-common` 全英文。**同名同义但文案不同 ⇒ wire 可见差异** | ⏸ 按 Go 真源逐字保留（§12.1 冲突表第 2 条已论证文案 wire 可见）；**不得"统一语种"** |
| **4** | ⚠️ **「改文案」是一个能静默断掉跨服务错误码的隐藏操作** | `BizCodeFromError` 靠中文文案反查码（`:118-120`）。若有人把 `ErrorFavoriteHistoryBlocked` 的文案从「收藏暂不可撤回」改成别的措辞，api 侧 5 个 logic 的 `codeFromRPCError` 会把 `5304` 退化成 `1015`（`:136` 的 `default`）⇒ **客户端看到的业务码静默变化，且两侧都不报错** | ⏸ 已在 `FavoriteErrorMessages` 与 `FavoriteGrpcError` 的 javadoc 中标注，并用 `FavoriteGrpcErrorRoundTripTest` 的负向断言锁死 |
| **5** | ⚠️ **5102–5105（`ErrorUserExists` / `ErrorPasswordInvalid` / `ErrorTokenInvalid` / `ErrorTokenExpired`）在 favorite 域无任何调用点** | 这 4 个码在 favorite 域是**纯占位**（从 users 域码表整段搬来）。它们既不在 `BizCodeFromError` 的 switch 里（`:122-137` 只覆盖 6 个码），也没有直接引用 | ⏸ **保留**（判据 4）；是否删除属 L1 契约变更，**需拍板**。本文件按 Go 原样保留 |

### UNKNOWN 清单

| # | 项 | 说明 | 状态 |
|---|---|---|---|
| U1 | **类数 3 个的最终拍板** | 本文件按 5 条判据推出 3 个（与 `message-common` 一致）。但 §12.5 的表只覆盖了 5 个已落地 `-common`，**未覆盖** `comment` / `favorite` / `follow` / `task` 四个新域 ⇒ 若新域后续统一成别的形态，本文件需同步 | **UNKNOWN**：推导建议 + 依据已给足（§12.5 五条判据逐条套用 + 6 域实测对照表），待拍板确认 |
| U2 | **包名落点 `…common.errmsg` vs `…common`** | §12.1 已给 5:0 的实测先例判为「已定论」。但 Go 的 `package common`（`errmsg.go:1`）直译过来是 `…common` ⇒ 这是一个**表达层偏离**，按 AGENTS.md §3.2 需在实施时留痕 | **UNKNOWN**：若改判为 `…common`，需同步 `favorite-api` / `favorite-rpc` 两处的 import 并登记 Intentional_Deviation |
| U3 | **码表文案在客户端是否被硬编码消费** | favorite 的 `msg` 出现在 HTTP envelope 与 gRPC description。本仓库内**无 favorite 的调用方**（`@GrpcClient` 清单里只有 message / user / article / like / hot / admin）⇒ 是否有前端按中文文案做分支判断，**无法从本仓取证** | **UNKNOWN**：若客户端确实按文案分支，那 24 条文案中任何一条的改动都会变成 breaking change |
| U4 | **`ErrorAlreadyExist`（5006）在 favorite 域仅由 `BizCodeFromError` 的 switch 间接可达** | `:129` 的 `AlreadyExists → 5006`。而 favorite 的 rpc 侧**从不**用 `GRPCError(codes.AlreadyExists, 5006)`（实测 `favoritecommon.ErrorAlreadyExist` 0 调用点）⇒ 这条映射在 favorite 域**实际不可达** | **UNKNOWN**：本文件按 Go 原样保留（switch 必须整条对齐）；该映射当前不可达，是否精简属 L1 契约变更，需拍板 |
| U5 | **`FavoriteErrorMessages.MSG_BY_CODE` 的可见性约束** | `MessageGrpcError.java:45-47` 的注释说明 `MSG_BY_CODE` 刻意 `package-private`，只通过 `getMsg(int)` 对外。favorite 侧是否沿用同样可见性未有任何强制依据 | **UNKNOWN**：推导建议是沿用（`FavoriteGrpcError` 与 `FavoriteErrorMessages` 同包 ⇒ package-private 够用），实施时按 `MessageErrorMessages` 的形态确认 |
| U6 | **5102–5105 是否删除** | `ErrorUserExists` / `ErrorPasswordInvalid` / `ErrorTokenInvalid` / `ErrorTokenExpired` 在 favorite 域零引用（实测），只经 `GetErrMsg` 间接可达 | **UNKNOWN**：本文件按 Go 原样保留（整表 24 条是 L1 对照前提）；删除属码表契约变更，需拍板 |

---

## 附：本文件与其它文档的关系

| 文档 | 关系 |
|---|---|
| `FileTree/favorite/api.md` | 消费方之一。api 侧 15 处 `GetErrMsg` + 1 处 `BizCodeFromError`（`api/internal/logic/favorite/helper.go:45`）都指向本文件 |
| `FileTree/favorite/rpc.md` | 消费方之二。rpc 侧 56 处 `GRPCError` + 10 处 `BizCodeFromError` 都指向本文件；`favorite-rpc` 因此**必须**依赖 `favorite-common`（与 `like-rpc/pom.xml:38-42` 同款） |
| `FileTree/common.md` | **同名不同物**。那份是 `service/common`（跨服务共享，14 文件），本文件是 `service/favorite/common`（favorite 专属，1 文件）。边界声明见 §12.1 |
| `FileTree/hot/common.md` | **判据母版**（类数推导的三条判据 + 单文件 common 的边界声明范式）。⚠️ 但 hot 的 2 类结论**不可照抄** —— hot 的 `GRPCError`/`BizCodeFromError` 零调用点，favorite 有 56 + 11 处（§12.5 表） |
| `service/message/message-common` | **结构母版**。Go 源与 favorite 逐符号同构（6 个函数 + `codeMsg` + `bizCodeByMsg`），RAG 落点也是 3 类。但 favorite **缺** `CodeError`（message 有），且 **`bizCodeFromError` 少一条 `CodeError` 分支** —— 照抄会引入 Go 侧不存在的判定 |
| `docs/Intentional_Deviations/` | 若采纳 U2 的「改为 `…common`」，**必须**登记（Go `package common` vs RAG `…common.errmsg`） |