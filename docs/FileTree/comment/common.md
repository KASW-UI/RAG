# comment-common — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/comment/common`
> 目标根包：`com.rag.comment.comment.common.errmsg`
> 推导日期：2026-10-23
> 状态：**理论推导**。RAG 侧 `service/comment/comment-common` **只有 `pom.xml`、0 行 Java**
> （实测 `pom.xml` 仅 `<parent>` 8 行 + `<artifactId>comment-common</artifactId>` 12 行，`<dependencies>` 为空）。
>
> **本文件覆盖 Go 侧全部 1 个文件（`errmsg.go`，103 行），已逐行读完。**
> 本组件是 `comment` 域里**唯一的非 api/rpc 组件**，与 `FileTree/common.md`（`service/common`，跨服务共享）
> **不是同一个东西** —— 两者的边界声明见 §12.1。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG `service/comment/comment-common/`：

| RAG 位置 | 现状 | 对应 Go 包 |
|---|---|---|
| `service/comment/comment-common/pom.xml` | 存在，**`<dependencies>` 为空** | — |
| `src/main/java/…` | **0 个文件**（`find service/comment -type f` 无 java 命中） | `common/errmsg/` |
| `src/test/java/…` | 目录不存在 | — |
| `service/comment/pom.xml:18` | 已把 `comment-common` 列为 `<module>` | 模块拓扑已定，不提出第 5 个模块 |

### ⚠️ 边界声明：本文件与 `FileTree/common.md` 是两个不同组件

全仓有**两个**都叫 `common` 的东西，混淆会导致把跨服务共享能力错当成 comment 专属、或反之：

| | 本文件 | `FileTree/common.md` |
|---|---|---|
| Go 路径 | `service/comment/common/` | `service/common/` |
| RAG 位置 | `service/comment/comment-common/` | `service/common/` |
| 文件数 | **1**（`errmsg.go` 103 行） | 42 个 Java 文件（实测 `find service/common/src/main/java -name '*.java'`） |
| 消费者 | **comment-api + comment-rpc 两方**（见 §12.3 可达性实测） | 所有业务模块 |
| 内容 | 业务码表 + gRPC 错误双向转换 | 通用中间件 / 响应信封 / 日志 / 雪花 ID / 事实链路 |
| 是否有 errmsg | **有**（`errmsg.go`） | **无**（`FileTree/common.md` 全文未出现 errmsg） |

### 可直接复用的现有能力（逐条给出打开过的路径行号）

| RAG 组件 | 状态 | 说明 |
|---|---|---|
| `com.rag.common.exception.BusinessException` | ✅ 已落地 | `exception/BusinessException.java:26`（unchecked）+ `:30-33`（`(int code, String msg)`）+ `:35-37`（`getCode()`）。与 Go `errmsg.CodeError{Code,Msg}`（`errmsg.go:10-13`）**形状同构** |
| `com.rag.common.response.Response` | ✅ 已落地 | `response/Response.java:14-18`，字段名与 Go envelope 逐字一致（仅 api 侧使用，common 不需要） |
| `io.grpc` 的 `Status` / `StatusRuntimeException` / `Metadata` | ✅ 根 pom 统一管理版本（`pom.xml:155-181` 的 `<grpc.version>`） | `LikeGrpcError.java:5-7` 就是直接用这三个类，无需自己封装 |

### 先例对照：本仓库 5 个 `-common` 的 errmsg 落点（**必须逐个核，不能照抄**）

| Go 侧 errmsg | 有 `NewGrpcErr`（业务码塞 grpc-status）？ | `BizCodeFromError`？ | RAG 落点 | 类数 |
|---|---|---|---|---|
| `service/users/common/errmsg/errmsg.go` | ❌ | ❌ | `users-common/errmsg/ErrorCode`（enum 内直接带 msg） | 1 |
| `service/article/common/errmsg/errmsg.go` | ❌ | ❌ | `article-common/errmsg/ErrorCode` | 1 |
| `service/message/common/errmsg/errmsg.go` | ✅ | ✅ | `MessageErrorCode` + `MessageErrorMessages` + `MessageGrpcError`（内含 `CodeError`） | 3 |
| `service/like/common/errmsg/errmsg.go` | ✅ | ❌ | `CodeError` + `LikeErrorCode` + `LikeErrorMessages` + `LikeGrpcError` | 4 |
| **`service/comment/common/errmsg/errmsg.go`** | ✅ **34 个调用点** | ❌ | **本文件：3 个类** | **3** |
| （对照）`service/hot/common/errmsg.go` | ❌ 零调用点 | ✅ 零调用点 | `HotErrorCode` + `HotErrorMessages` | 2 |

⇒ 三条可复用判据（都是从这张表读出来的）：
1. **码与文案分离**。`message` / `like` 都拆开了；`users` / `article` 能合成一个类，
   是因为它们的 Go 源本来就没有文案反查需求。comment 有独立的 `codeMsg` 表（`errmsg.go:67-95`）⇒ **拆**。
2. **gRPC 错误类只在真的被调用时才建**。`FileTree/hot/common.md` §12.5 判据 2 立于此，
   而 `like` 在 Go 源**没有** `BizCodeFromError` 的情况下建了一个 258 行的 `LikeGrpcError` ——
   那份文档把它列为**反面样本（超建）**。
   ⚠️ **但 comment 的情况与两者都不同**：`NewGrpcErr` 有 **34 个真实调用点**（§12.3 实测），
   且 `get_comment_logic.go` 之外的每一个 rpc 方法都要靠它把业务码送上线路。
   ⇒ **建 `CommentGrpcError` 有 grep 级依据，不是超建**；但只建**用得到的那一半**（见 §12.5 判据 3）。
3. **异常载体复用 `BusinessException`，不自建 `CommentCodeError`**。
   依据是 `FileTree/hot/common.md` §12.5 判据 3 + 本域的可达性实测：
   comment 的 `CodeError` **只有 1 个引用点**（`api/commentservice.go:50` 的 `errors.As` 目标），
   且 `.Code`/`.Msg` 两个字段正是 `BusinessException.getCode()`/`getMessage()` 的形状（§12.5 逐条论证）。

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **27 个常量全留还是只留可达的 14 个** | ✅ **全留**（推导建议，依据：`errmsg.go:67-95` 的 `codeMsg` 是**字面量表**，
`GetErrMsg` 的未知码回退（`:97-103`）依赖 `codeMsg[Error]` 存在；
且这 27 个数值是**跨服务契约**，删项等于改契约。13 个零引用常量（§12.3）**保留但不视为已对齐**） |
| **13 个零引用常量算不算「已对齐」** | ⚠️ 按 AGENTS.md §0 的判读纪律，只能说「**码表完整**」，不能说「这些码在用」 |
| **`CommentGrpcError` 的 trailer key 名** | 🟡 推导建议 `rag-comment-biz-code`（与 `LikeGrpcError.BIZ_CODE_TRAILER_KEY = "rag-like-biz-code"`（`:78`）同款命名法：`rag-` 前缀 + 域名 + 语义）。**本文件未拍板** |
| **`mapToGrpcCode` 的标准码选择** | 🔴 **必须避开熔断失败名单**（`DeadlineExceeded`/`Internal`/`Unavailable`/`DataLoss`/`Unimplemented`/`ResourceExhausted`），
否则每次业务错误都会被计成依赖故障、熔断误开。`LikeGrpcError.java:110-115` 已给出「`ERROR_INPUT_WRONG → INVALID_ARGUMENT`、其余 → `UNKNOWN`」的选择，
**comment 侧是否照抄需拍板**（comment 域的 `ERROR_INPUT_WRONG=2001` 与 like 同值，见 §12.3） |
| **`com.rag.common` 反向依赖** | ⚠️ `BusinessException` 在 `common` 模块，`comment-common` 要引它；`common` 是下层，不会反向引 `comment-common`
⇒ 无循环依赖（与 `users-common/errmsg/ErrorCode` 的 javadoc 记录的同一约束一致） |

---

## 12.2 第二部分：Go 原始文件树

```
service/comment/common/
└── errmsg.go          # 唯一文件（103 行）：CodeError struct + Error() + 3 个工厂 + 27 常量 + 27 条 codeMsg + GetErrMsg
```

**本组件无 `etc/`、无 `internal/`、无 proto、无数据库、无配置、无外部交互** ——
它是一个**纯内存的值定义 + 纯函数库**。没有任何副作用、没有 IO、没有全局可变状态（`codeMsg` 虽是包级 `var`，
但只被读）。

---

## 12.3 第三部分：Go 文件职责分析

### `errmsg.go` 全量清单（逐段）

| 段 | 行号 | 内容 | 可达性（实测） |
|---|---|---|---|
| `CodeError` struct | `:10-13` | `Code int` / `Msg string`，两个字段都带 `json:"code"` / `json:"msg"` | **1 处引用**：`api/commentservice.go:50` 的 `errors.As(err, &e)` 目标 |
| `(*CodeError).Error()` | `:15-17` | 返回 `"ErrCode:%d, Errmsg:%s"` | 随 `CodeError` 走；无直接调用点 |
| `NewErrCode(code)` | `:19-24` | `Msg = GetErrMsg(code)` | **12 处**（全在 api 侧 6 个 logic 的两处） |
| `NewErrCodeMsg(code,msg)` | `:26-31` | 自带 msg，不查表 | **6 处**（api 侧 6 个 logic 各一处） |
| `NewGrpcErr(code,msg)` | `:33-35` | `status.Error(codes.Code(code), msg)` | **34 处**（全在 rpc 侧 6 个 logic） |
| 常量块 | `:37-65` | **27 个** | 14 可达 / 13 零引用 |
| `codeMsg` | `:67-95` | **27 条** `code → 文案`，**逐条互不相同**（实测无重复） | 随 `GetErrMsg` 全域可达 |
| `GetErrMsg(code)` | `:97-103` | 查表；未命中回退 `codeMsg[Error]` = `"FAIL"` | **2 处**：`api/commentservice.go:42`（成功）与 `:49`（兜底） |

### 27 个常量与文案（**L1 冻结值，逐字抄录**）

| 常量 | 值 | `codeMsg` 文案 | 可达性 |
|---|---|---|---|
| `Success` | 200 | `OK` | ✅ 2 处（`commentservice.go:41,42`） |
| `Error` | 500 | `FAIL` | 🟡 **间接**：作为 `GetErrMsg` 的未知码回退值（`errmsg.go:100`）；`errmsg.Error` 零引用 |
| `CodeServerBusy` | 1015 | `服务繁忙` | ❌ 零引用 |
| `ErrorServerCommon` | 5001 | `系统内部错误` | ✅ 13 处 |
| `ErrorDbUpdate` | 5002 | `更新数据库失败` | ✅ 8 处 |
| `ErrorDbSelect` | 5003 | `查询数据库失败` | ✅ 8 处 |
| `ErrorDbInsert` | 5004 | `数据库插入失败` | ✅ 3 处 |
| `ErrorDbTransaction` | 5005 | `事务操作失败` | ❌ 零引用 |
| `ErrorSnowflakeID` | 5006 | `雪花ID生成失败` | ✅ 2 处 |
| `ErrorJsonMarshal` | 5007 | `JSON序列化失败` | ✅ 2 处 |
| `ErrorJsonUnmarshal` | 5008 | `JSON反序列化失败` | ❌ 零引用 |
| `ErrorKafkaPush` | 5009 | `Kafka消息发送失败` | ✅ 2 处 |
| `ErrorDelayMsg` | 5010 | `延时消息发送失败` | ❌ 零引用 |
| `ErrorUserExist` | 1001 | `用户名已存在` | ❌ 零引用 |
| `ErrorLoginWrong` | 1002 | `用户名或密码错误` | ❌ 零引用 |
| `ErrorUserNotExist` | 1003 | `用户不存在` | ❌ 零引用 |
| `ErrorTokenNotExist` | 1004 | `TOKEN不存在` | ❌ 零引用 |
| `ErrorTokenTypeWrong` | 1005 | `TOKEN格式错误` | ❌ 零引用 |
| `ErrorTokenRuntime` | 1006 | `TOKEN已过期` | ✅ 12 处（api 侧 6 个 logic 的 uid 失败路径） |
| `ErrorTokenRefresh` | 1007 | `TOKEN刷新失败` | ❌ 零引用 |
| `ErrorUserNoRight` | 1008 | `权限不足` | ✅ 5 处 |
| `ErrorUserNoLogin` | 1009 | `未登录` | ❌ 零引用 |
| `ErrorUserLogined` | 1010 | `已登录` | ❌ 零引用 |
| `ErrorUserBanned` | 1011 | `用户已被封禁` | ❌ 零引用 |
| `ErrorSubjectNotExist` | 1012 | `评论区不存在` | ✅ 10 处 |
| `ErrorCommentNotExist` | 1013 | `评论不存在` | ✅ 9 处 |
| `ErrorInputWrong` | 2001 | `输入有误` | ✅ 17 处 |

⇒ **14 可达 + 13 零引用 = 27**。文案**全部是中文**（与 hot 域的英文文案不同，见 `FileTree/hot/common.md` §12.3 的三方冲突表）。

⚠️ **码表与 hot 域三方冲突**：`comment` 的 5002/5003/5004 与 hot 的 5002/5003/5004 语义**完全不同**
（comment：`更新数据库失败`/`查询数据库失败`/`数据库插入失败`；hot：`record not found`/`invalid parameter`/`unauthorized`），
且与 `users-common` / `article-common` 的 `ErrorCode.java` 也不一致
（见 `FileTree/hot/common.md` §12.3 的实测对照表）。
⇒ **comment 必须自持码表，禁止 REUSE 任何既有 `ErrorCode`**。这是本文件唯一的硬约束，不受任何拍板结果影响。

### 🔴 `NewGrpcErr` 的 34 个调用点逐条（**这是本文件最硬的约束**）

```
create_comment_logic.go        7 处   :66 2001/:73 2001/:82 1012/:89 5003/:98 5006/:122 5007/:130 5009
delete_comment_logic.go        9 处   :61 2001/:70 1013/:77 5003/:86 1012/:93 5003/:108 1013/:113 1008/:119 5002
like_comment_logic.go          5 处   :48 2001/:57 1012/:63 2001🔴/:80 1013/:86 5002
manage_comment_logic.go        4 处   :62 2001/:84 2001/:93 1013/:100 5002
report_comment_logic.go        3 处   :64 2001/:84 1013/:91 5004
update_subject_state_logic.go  6 处   :63 2001/:73 1012/:80 5003/:88 1008/:98 1012/:105 5002
get_comment_logic.go           0 处   ⚠️ 它 return 裸 error（:74），不走 NewGrpcErr
```

⚠️ **`like_comment_logic.go:63` 是唯一一处「日志码 ≠ 发出码」**：
`logger.LogBusinessErr(l.ctx, errmsg.ErrorDbInsert, err)`（`:62`，记 5004）但
`errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "DB更新失败")`（`:63`，**发 2001**）。
这是 Go 侧显式的不一致，**迁移时不得"顺手修好"**。

⚠️ **文案与码也是两套东西**：`NewGrpcErr(code, msg)` 的第二个参数是**手写的中文串**，
**不是** `GetErrMsg(code)`。例如 `NewGrpcErr(2001, "评论内容不能为空")`（`create:73`）
在码表里 2001 的文案是 `"输入有误"`。⇒ **码表文案与线路文案是两套 L1 契约，必须分别保留**，
Java 侧不能让 `grpcError(bizCode)` 去查表填 description。

### `NewErrCodeMsg(int(st.Code()), st.Message())` 的 6 个调用点（api 侧）

```
createcommentlogic.go:54   deletecommentlogic.go:51   likecommentlogic.go:52
managecommentlogic.go:53   reportcommentlogic.go:55   updatesubjectstatelogic.go:51
```

⇒ 这 6 处构成 **bizCode 的线路往返**：`rpc 的 grpc-status` → `api 的 envelope.code`。
🔴 **但 Go 侧的往返只是"看起来能过"**：Go 用的是 grpc-go，其 `codes.Code` 不校验范围；
**grpc-java 收不到 2001**（见 §12.4）。

### `GetErrMsg` 的 2 个调用点与**未知码回退的 L1 性质**

`api/commentservice.go:42`（成功信封的 `"OK"`）与 `:49`（`SetErrorHandler` 的 5001 兜底文案 `"系统内部错误"`）。
⇒ **回退值 `"FAIL"` 本身当前不可观察**（因为两个调用点传入的都是表内码）。
⚠️ 但 `api/commentservice.go:48-49` 的兜底是 `code = ErrorServerCommon`（表内），
若将来有路径传入表外码，`"FAIL"` 就会上线 ⇒ 保留回退（`FileTree/hot/common.md` §12.4 已把这条定为 L1）。

### `CodeError` 的消费方式（决定要不要自建异常类）

`api/commentservice.go:47-60`：
```go
httpx.SetErrorHandler(func(err error) (int, interface{}) {
    code := errmsg.ErrorServerCommon
    msg := errmsg.GetErrMsg(code)
    var e *errmsg.CodeError
    if errors.As(err, &e) { code = e.Code; msg = e.Msg }   // ← 唯一消费方式
    return http.StatusOK, &response.Response{Code: code, Msg: msg, Data: nil}
})
```
**只读 `.Code` 与 `.Msg` 两个字段**，`Error()` 的 `"ErrCode:%d, Errmsg:%s"` 格式串**不参与 wire**（响应体用的是 `e.Msg`）。
⇒ Java 侧 `BusinessException.getCode()`（`BusinessException.java:35-37`）+ `getMessage()`（继承自 `Throwable`，即 msg）
**完全等价**，且 `BusinessException` 无需任何 Jackson 注解就能不进 JSON（它不参与序列化 ——
`Response.java:15-17` 的 `data` 字段装的是业务 DTO，不是异常）。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| 27 个常量（`:38-64`） | `comment-common/errmsg/CommentErrorCode`（enum） | ADAPT | 值属 L1，逐字照抄；**必须整表保留**（`codeMsg` 的完整性依赖它） |
| `codeMsg` 表（`:68-94`） | `comment-common/errmsg/CommentErrorMessages`（私有不可变 Map） | ADAPT | **与码分离**——`message`/`like` 的共同做法（§12.5 判据 1） |
| `GetErrMsg(code)`（`:97-103`） | `CommentErrorMessages.getMsg(int)` | ADAPT | 未知码回退 `"FAIL"`，**回退值本身是 L1** |
| `CodeError` struct（`:10-13`） | `com.rag.common.exception.BusinessException` | **REUSE** | 同为 unchecked + `(int, String)`；`.Code`→`getCode()`、`.Msg`→`getMessage()`。见 §12.5 逐条论证 |
| `(*CodeError).Error()`（`:15-17`） | **不迁移** | — | `"ErrCode:%d, Errmsg:%s"` 只用于 Go 日志输出；**wire 上用的是 `e.Msg` 不是 `Error()`**（`commentservice.go:53`）⇒ 属表达层 |
| `NewErrCode(code)`（`:19-24`） | **不建工厂**（等价式 `new BusinessException(code, CommentErrorMessages.getMsg(code))`） | — | 12 个调用点都是 api 侧 6 个 logic 的内联写法；单独建一个工厂方法只是镜像 Go 的函数名（§11） |
| `NewErrCodeMsg(code,msg)`（`:26-31`） | **不建工厂**（等价式 `new BusinessException(code, msg)`） | — | 同上，6 个调用点 |
| `NewGrpcErr(code,msg)`（`:33-35`） | `comment-common/errmsg/CommentGrpcError.grpcError(int, String)` | **ADAPT（带平台限制）** | 🔴 **34 个调用点，必须建**。但 grpc-java 发不出越界 `grpc-status`（`LikeGrpcError.java:36-48` 实测）⇒ 必须实现 trailer 载体迁移 |
| （Go 侧不存在）业务码的**反向读取** | `CommentGrpcError.bizCodeFromError(Throwable)` | **NEW** | Go 侧靠 `int(st.Code())` 隐式完成；Java 侧**必须显式从 trailer 读**才能补回这条链路。**这是 Java 侧新增的能力，不是迁移** |
| （Go 侧不存在）业务码 → 标准码的**类别映射** | `CommentGrpcError.mapToGrpcCode(int)`（私有） | **NEW** | grpc-java 必须收到一个合法标准码；选错会让熔断误开（`LikeGrpcError.java:87-108` 的实测论证） |

⚠️ 三条 NEW 都必须**在 §3.2 留痕里显式声明为「平台补偿」**，不得写成「与 Go 一致」。

---

## 12.5 第五部分：理论 Java 文件树

```
service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/
├── CommentErrorCode.java          # enum：27 个码值 + getCode()，**只管码，不管文案**
├── CommentErrorMessages.java      # code→msg 表（27 条）+ getMsg(code)，未知码回退 "FAIL"
└── CommentGrpcError.java          # grpcError(bizCode,msg) + trailer 往返 + bizCodeFromError + mapToGrpcCode
```

配套测试：

```
service/comment/comment-common/src/test/java/com/rag/comment/comment/common/errmsg/
├── CommentErrorCodeContractTest.java      # 27 个码值逐字锁死 + 声明顺序 + 唯一性 + 总数
├── CommentErrorMessagesContractTest.java  # 27 条文案逐字锁死 + 双射 + 未知码回退 "FAIL"
└── CommentGrpcErrorTest.java              # bizCode 跨**真 in-process gRPC** 往返无损 + 熔断失败名单断言
```

**只有 3 个类**（比 like-common 的 4 个少一个，少的是 `CodeError`）。

### 为什么不建 `CommentCodeError`：逐条论证

| 判据 | comment 的实际情况 | 结论 |
|---|---|---|
| 引用点数 | `CodeError` 在整个 comment 域**只有 1 个引用点**（`api/commentservice.go:50`），而且是 `errors.As` 的类型目标，不是业务抛出点（业务返回的是 `NewErrCode` / `NewErrCodeMsg` 的返回值） | 自建异常类没有第二个使用场景 |
| 字段形状 | `Code int` / `Msg string` ↔ `BusinessException.java:28,30-33` 的 `final int code` + `super(msg)` | **形状同构** |
| 消费方式 | api 侧只读 `.Code`（→ envelope 的 `code`）与 `.Msg`（→ envelope 的 `msg`） | `getCode()` / `getMessage()` 等价 |
| Jackson 注解 | like 需要 `@JsonAutoDetect` + `@JsonProperty` 是因为它把 `CodeError` 当**可序列化载荷**（`LikeGrpcError.java:76-105`）。comment 的 `CodeError` **从不进 JSON** —— 进 JSON 的是 `Response`（`Response.java:15-17`） | **不需要 jackson-annotations 依赖** |
| 先例 | `FileTree/hot/common.md` §12.5 判据 3 已把「REUSE `BusinessException`、不自建 `CodeError`」定为判据；`users-common/errmsg/ErrorCode` 的 javadoc 记录的是同一做法 | 与已定判据一致 |

⇒ `comment-common/pom.xml` 因此**不需要** `jackson-annotations`（unlike `like-common/pom.xml:40-43`），
只需要 `grpc-stub`（compile）+ 测试三件套。**这是逐条推导出来的差异，不是抄 like。**

### `CommentGrpcError` 的三个方法（**本文件唯一的技术难点**）

```
1. grpcError(int bizCode, String message)  →  StatusException
   · 替代 Go 的 status.Error(codes.Code(bizCode), msg)
   · 🔴 平台限制：grpc-java 的 Status 只认 17 个标准码，comment 的 27 个码里凡 > 16 的都发不出去
     （实测结论引自 LikeGrpcError.java:36-48）
   · 方案（与 LikeGrpcError 同款「等价信息保全 + 载体迁移」）：
       - 业务码  → 自定义 trailer Metadata.Key<String>（ASCII 数值）
       - 文案    → 仍放 description，逐字不变（Go 手写的中文串，不是查表结果）
       - 标准码  → 仅用于「错误类别」，且必须避开熔断失败名单
2. bizCodeFromError(Throwable)  →  int
   · api 侧 6 个 logic 的 int(st.Code()) 的 Java 等价物：从 StatusRuntimeException 的 trailers 读业务码
   · 读不到时的回退顺序必须写死（Go 的回退是 NewErrCode(ErrorServerCommon)=5001，见 §12.3）
3. mapToGrpcCode(int bizCode)  →  Status.Code   [私有]
   · 必须避开 DeadlineExceeded/Internal/Unavailable/DataLoss/Unimplemented/ResourceExhausted
   · 候选：ERROR_INPUT_WRONG(2001) → INVALID_ARGUMENT；其余 → UNKNOWN
   · ⚠️ 本文件未拍板，见 §12.7
```

⚠️ **`grpcError` 的调用契约有一个易错点**：`LikeGrpcError.java:120` 的 javadoc 明确写
「**调用方必须交给 `observer.onError(...)`，不能 `throw`**」。
comment-rpc 的 `CommentServiceGrpcImpl` 也必须遵守同一条，否则 trailer 不会上线。

### `comment-common/pom.xml` 的依赖推导

| 依赖 | scope | 依据 |
|---|---|---|
| `io.grpc:grpc-stub` | compile | `CommentGrpcError` 构造 `StatusException`、解析 `StatusRuntimeException`、读 `Status.Code` 与 `Metadata`（全部在 `grpc-api`，`grpc-stub` 传递引入）。与 `like-common/pom.xml:27-30` 同源，版本由根 pom 管理，**不写死** |
| `com.fasterxml.jackson.core:jackson-annotations` | ❌ **不需要** | 不建 `CommentCodeError` ⇒ 无 Jackson 注解要钉（§12.5 论证） |
| `org.junit.jupiter:junit-jupiter` | test | 3 个契约测试类 |
| `io.grpc:grpc-inprocess` | test | 验证 bizCode 跨**真传输**往返，而非同进程内存过一遍（`like-common/pom.xml:70-75` 已记录这个理由） |
| `com.rag:common` | compile | 用 `BusinessException`（§12.5）；与 `users-common` 同一形态，无循环依赖 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `errmsg.go:38-64`（27 个常量） | `comment-common/errmsg/CommentErrorCode`（enum） | ADAPT | L1 冻结值，逐字照抄；**整表保留**（13 个零引用的也留） |
| `errmsg.go:68-94`（27 条 `codeMsg`） | `comment-common/errmsg/CommentErrorMessages` | ADAPT | 与码分离（§12.5 判据 1） |
| `errmsg.go:97-103` `GetErrMsg` | `CommentErrorMessages.getMsg` | ADAPT | 含 `"FAIL"` 回退语义 |
| `errmsg.go:10-13` `CodeError` | `com.rag.common.exception.BusinessException` | **REUSE** | 形状同构 + 仅 1 个引用点 + 不进 JSON（§12.5） |
| `errmsg.go:15-17` `(*CodeError).Error()` | 不迁移 | — | `"ErrCode:%d, Errmsg:%s"` 只用于 Go 日志；**wire 用的是 `e.Msg`**（`commentservice.go:53`），属表达层 |
| `errmsg.go:19-24` `NewErrCode` | 不建工厂，等价式 `new BusinessException(code, CommentErrorMessages.getMsg(code))` | — | 12 个调用点都是内联写法；单独建工厂只是镜像 Go 函数名 |
| `errmsg.go:26-31` `NewErrCodeMsg` | 不建工厂，等价式 `new BusinessException(code, msg)` | — | 同上，6 个调用点 |
| `errmsg.go:33-35` `NewGrpcErr` | `CommentGrpcError.grpcError` | **ADAPT** | 34 个真实调用点，**必须建**；但实现方式必须换成 trailer |
| （Go 无）业务码反向读取 | `CommentGrpcError.bizCodeFromError` | **NEW** | Java 侧新增的**平台补偿能力**，非迁移。必须在 §3.2 声明 |
| （Go 无）业务码 → 标准码类别映射 | `CommentGrpcError.mapToGrpcCode`（私有） | **NEW** | 同上；选错会让熔断误开 |
| —— | `service/comment/comment-common/pom.xml` | **需补齐** | 现为空 `<dependencies>`；加 `grpc-stub` + 3 个 test scope 依赖；**不加** `jackson-annotations`（§12.5） |
| —— | `src/test/java/.../CommentErrorCodeContractTest.java` | **新增** | 27 个码值逐条比对 + 声明顺序与 Go 常量块一致 + 唯一性 + 总数 + 裸 int 产出 |
| —— | `src/test/java/.../CommentErrorMessagesContractTest.java` | **新增** | 27 条文案逐条比对 + 表与码表双射 + 表内顺序 + 未知码回退 `"FAIL"` + 文案两两互异 + 中文语种 + **跨域同码不同义** |
| —— | `src/test/java/.../CommentGrpcErrorTest.java` | **新增** | ① bizCode 跨真 in-process gRPC 往返无损；② 业务码**永不**被映射到熔断失败名单；③ description 逐字保留 Go 的手写中文串；④ trailer 缺失时回退 5001 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 27 个码值 + 27 条码表文案逐字录于 §12.3（未引用任何既有域的表）；`GetErrMsg` 未知码回退 `"FAIL"`；**34 处 `NewGrpcErr` 的 (码, 手写文案) 逐条列出**；api 侧 6 处 `int(st.Code())` 原样回填；码表文案与线路文案**是两套契约**（§12.3 末） |
| L2 业务语义有承载位置 | ✅ `CodeError` 只被读 `.Code`/`.Msg` 两个字段（`commentservice.go:51-54`）这一事实，已作为「REUSE `BusinessException`」的判据；`NewErrCode` 与 `NewErrCodeMsg` 的 msg 来源不同（前者查表、后者自带）这一区别已由两个不同的等价式保留 |
| 框架吸收有机制依据 | ✅ 异常载体 REUSE `BusinessException`（`BusinessException.java:26,30-33`），未新建平行异常类；**没有**任何职责被笼统标成 ABSORB |
| 复用组件已明确 | ✅ `BusinessException`、`io.grpc.{Status,StatusRuntimeException,Metadata}`、`grpc-inprocess`（测试）—— 每项给出打开过的路径行号 |
| 新增文件有职责依据 | ✅ 3 个类的切分依据是**职责边界**而非文件名：`CommentErrorCode` 只管码、`CommentErrorMessages` 只管文案、`CommentGrpcError` 只管线路转换。判据来自本仓库 5 个 `-common` 的实测对照（§12.1 的表），不是「Go 有同名文件」 |
| 合并有组织依据 | ✅ 不建 `CommentCodeError`（4 类 → 3 类）的依据是 §12.5 的逐条论证 + `FileTree/hot/common.md` §12.5 判据 3，**不是**嫌 like 的 4 类不好 |
| 拆分可解释 | ✅ 码 / 文案 / 线路转换三者各自独立变化（码值是跨服务契约、文案可能改、线路受 grpc-java 版本约束） |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Errmsg.java`（Go 是包名不是文件名）；`CodeError` 被有意合并掉；`NewErrCode`/`NewErrCodeMsg` 被有意合并掉 |
| 未把未实现能力说成现有机制 | ✅ `comment-common` 现为空依赖 + 0 行 Java 已在 §12.1 声明；三条 NEW 全部标注为「**平台补偿，非迁移**」 |
| 与 api / rpc 两侧拓扑结论一致 | ✅ `FileTree/comment/api.md` §12.1 风险点 2 与 `FileTree/comment/rpc.md` §12.1 风险点 2 都指向本文件的 `CommentGrpcError`；三份文档对「业务码进 trailer、标准码只表示类别」这一结论**表述一致** |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **业务码无法在线路上表达（平台级）** | Go 把 27 个码里的越界值（1001~2001、5001~5010、1012、1013）直接塞进 `grpc-status`；
grpc-go 的 `codes.Code` 是 `int32` 不校验；**grpc-java 发不出**（`LikeGrpcError.java:36-48` 实测：
`fromCodeValue(5003)` → `UNKNOWN / value=2 / desc="Unknown code 5003"`，**连 description 都被覆盖**）。
⇒ 净差异 = `grpc-status` 字段数值不可复原；业务码、文案、错误类别三者仍无损。**实现前阻塞项** |
| 2 | **13 个零引用常量** | `CodeServerBusy`(1015)、`ErrorDbTransaction`(5005)、`ErrorJsonUnmarshal`(5008)、`ErrorDelayMsg`(5010)、
`ErrorUserExist`(1001)、`ErrorLoginWrong`(1002)、`ErrorUserNotExist`(1003)、`ErrorTokenNotExist`(1004)、
`ErrorTokenTypeWrong`(1005)、`ErrorTokenRefresh`(1007)、`ErrorUserNoLogin`(1009)、`ErrorUserLogined`(1010)、`ErrorUserBanned`(1011) —— 全域零引用。本文件**保留**（码表完整性 + 跨服务契约），但按 AGENTS.md 的判读纪律，**只能说「码表完整」，不能说「这些码在用」** |
| 3 | **码表与 hot / users / article 三方冲突** | 5002/5003/5004 在 comment / hot / users 三个域语义**两两不同**（§12.3 末）。禁止 REUSE 任何既有 `ErrorCode`。**这是硬约束，不受任何拍板结果影响** |
| 4 | **`likecommentlogic.go:62-63` 的「日志码 ≠ 发出码」** | 日志记 `ErrorDbInsert`(5004)、线路发 `ErrorInputWrong`(2001)。Go 侧显式不一致，**迁移时不得顺手修好** |
| 5 | **线路文案与码表文案是两套东西** | 18 处 `NewGrpcErr` 的第二个参数是手写中文串（如 `create:73` 的 `"评论内容不能为空"`），
而码表 2001 的文案是 `"输入有误"`。Java 侧**不能让 `grpcError(bizCode)` 查表填 description** —— 必须让调用方传原文案 |
| 6 | **`api/commentservice.go:48-49` 的 5001 兜底同时覆盖两类错误** | 「非 `CodeError` 的错误」里既包含 api 自己的解析失败，也包含 `getcommentlogic.go:74` 的裸 rpc 错误。
Java 侧兜底分支必须**同时**覆盖这两类，且都产出 `{code:5001, msg:"系统内部错误"}` |
| 7 | **`comment-common/pom.xml` 无任何依赖** | 缺 `grpc-stub`（compile）与 3 个 test scope 依赖。**实现前必须先补** |
| 8 | **与 like-common 的类数差异需要签字** | 本文件推导 **3 类**，like 落地的是 **4 类**（多一个 `CodeError`）。虽然 §12.5 给了逐条论证，
但这毕竟偏离了最近的一个先例，**应作为一次显式决定记录下来**，而不是当作"显然是 3 类" |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **trailer key 的最终命名** | 推导建议 `rag-comment-biz-code`（`LikeGrpcError.BIZ_CODE_TRAILER_KEY = "rag-like-biz-code"`，`LikeGrpcError.java:78`）。名字会出现在 wire 的 metadata 里，属**跨语言契约**（Go 侧若将来要读 RAG 发出的错误就需要它）。**未拍板** |
| **`mapToGrpcCode` 的标准码选择** | 必须避开 6 个熔断失败码。候选是 `ERROR_INPUT_WRONG(2001) → INVALID_ARGUMENT`、其余 → `UNKNOWN`（照 `LikeGrpcError.java:110-115`）。
⚠️ comment 域的 `ERROR_INPUT_WRONG` 值与 like 相同（都是 2001），照抄不会串码；但 comment 域**多出** 1012/1013 两个业务码，
它们在 like 的映射里没有先例（`UNKNOWN` 是安全默认）。**未拍板** |
| **`bizCodeFromError` 读不到 trailer 时的回退顺序** | Go 的回退是「非 status error → `NewErrCode(ErrorServerCommon)` = 5001」（`api/commentservice.go:48-49`）。
但 Java 侧还多一种 Go 侧不存在的情况：**status 存在但 trailer 缺失**（例如对端是别的实现）。
这种情况该回 5001 还是回 `Status.Code.value()`？**未定** |
| **`comment` 是否真的需要 `bizCodeFromError` 的完整形态** | Go 的反向映射是**隐式**的（读 `st.Code()`），没有 `BizCodeFromError` 这个函数。
本文件推导的是一个 Java 侧**显式**的读取器，形态参照 `MessageGrpcError.bizCodeFromError`（`:195-214`）。
但 message 域的 Go 源里本来就有这个函数，**形态不完全同源** ⇒ 是否需要额外支持 `StatusException`（checked）分支，需拍板 |
| api 侧是否有路径会让业务码**同时**出现在 trailer 和 `grpc-status`（标准码）上 | 若将来做双侧对拍，Go 侧读 `st.Code()` 会读到 `INVALID_ARGUMENT`(3) 或 `UNKNOWN`(2) 而不是 2001。
**双侧对拍必然失败**——与 `SD-2026-10-23-04` 的方法路径前缀问题是**两个独立的对拍阻塞点**，需一并拍板对拍方案 |
| `COMMENT_FACTS_ACCEPTANCE.md` 里是否有本文件未覆盖的验收约束 | 该文件在 Go 侧 `rpc/` 下（实测存在），本任务未读其内容（属 rpc 侧文档，不在 api/common 的 Go 源范围）。**未覆盖，需 rpc 侧文档补读** |