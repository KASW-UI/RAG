# proto-wire-probe/comment：comment proto **消息体 wire 字节**的双侧对拍探针

> 用途：把 comment 的 protobuf 消息体**逐字节**钉在 Go 真源上。
> 这是 AGENTS.md §3.3 要求的「**两侧同时跑的探针**」⇒ 闭合 `docs/DRIFT/comment/vs-common.md` 的
> `C-5`（**A7 半边**）。

## 它补的是哪个缺口

AGENTS.md §3.4 只保证「`.proto` **文件本身**两侧一致」，
**推不出**「两侧**生成码**产出的 wire 字节一致」：

- 字段号错一个 ⇒ wire 完全不同，但 `.proto` 文件「看着一致」；
- 零值落不落 wire（proto3 的隐式 presence）—— 只有序列化才看得见；
- `repeated` 的编码方式（tag + length-delimited）—— 同上；
- **枚举的零值**（`STATE_NORMAL`）—— 同上；
- **`bool` 的零值**（`success=false`）—— comment 域有 **5** 个这样的字段，见下。

⇒ 本探针用**同一组消息**在两侧各序列化一次，比对**十六进制字节**。

⚠️ **两侧的运行时不同**：Go 侧是 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
Java 侧是 `protobuf-java`（protoc 的 Java 运行时）⇒ 这**不是**「同一份实现自证」。

## Go 侧用的是**真源真正在用的生成码**

复制的是 `service/comment/rpc/pb/comment.pb.go`（Go 侧**真正在用**的那份，
**不是**重新 `protoc` 一遍）⇒ 验的是**生产字节**。
复制件做 **sha256 比对** ⇒ **真源换了生成码探针立刻响**。

## 怎么跑

```bash
bash tools/proto-wire-probe/comment/run.sh
echo $?   # 0 = 双侧逐字节一致
```

- 第 1 步：复制真源生成码 + sha256 校验；依赖版本**从真源 `go.mod`/`go.sum` 整体继承**。
- 第 2 步：跑 Go 侧 ⇒ 输出 16 条 `WIRE|<name>|<hex>`。
- 第 3 步：与冻结的 `expected.txt` 逐字节 diff。
- 第 4 步：跑 Java 侧 `CommentProtoWireParityTest`（**真实**的 comment-proto 生成码）⇒
  断言的也是**同一份** `expected.txt`。

## 十六条用例各钉什么

| 用例 | 钉住的事实 |
|---|---|
| `item_full` | `int64` 的 varint 编码 ＋ `string` 的 length-delimited ＋ **枚举非零**才落 wire |
| `item_zero` | 🔴 proto3 的标量零值**不落 wire** ⇒ 必须是**空字节** |
| `item_id_negative` | 🔴 **负 int64** 是 **10 字节 varint**（符号扩展成 64 位补码）⇒ 抓「被编成 zigzag/sint32」 |
| `item_id_max` | 🔴 **int64 上界**（`Long.MAX_VALUE`）是 **10 字节 varint** ⇒ 抓「被用 int32 装」（会截断 ⇒ 字节不同） |
| `item_state_normal` | 🔴 **枚举零值不落 wire** ⇒ 与「全零值」同形（空字节）⇒ 抓「被编成带 presence 的 optional」 |
| `item_state_deleted` | 枚举非零（`STATE_DELETED = 2`）⇒ tag 10（`0x50`）+ 值 |
| `item_state_blocked` | 枚举值本身进 wire（`STATE_BLOCKED = 3`） |
| `item_children` | 🔴 `repeated` **消息**字段的编码（tag 13 + length-delimited，不是 JSON 数组） |
| `resp_two` | 🔴 `repeated` ＋ **嵌套消息**（`GetCommentResp.comment` + `.subject`）同时验 |
| `resp_empty` | 空 `repeated` + 无嵌套消息 ⇒ **空字节**（tag 都不落） |
| `like_req_action_negative` | 🔴 **负 int32** 也是 **10 字节 varint** ⇒ 抓「被编成 sint32」（zigzag ⇒ 字节完全不同） |
| `like_resp_true` | `bool` = true ⇒ varint 1 |
| `like_resp_false` | 🔴🔴 **`bool` = false ⇒ 完全不落 wire ⇒ 整条消息是空字节** |
| `report_req_reason_other` | 枚举 **99**（`REASON_OTHER`）⇒ **单字节 varint**（`0x63`，因 99 < 128）⇒ 抓「被编成 string」 |
| `subject_full` | 嵌套消息 `SubjectInfo` 全字段（**两侧共有**那 6 个，见下） |
| `create_req_utf8` | 🔴 中文 UTF-8：length-delimited 的长度按**字节**算（不是字符数） |

### 🔴 `like_resp_false` 是 comment 域最该盯住的一条

api 层交给客户端的 JSON 是 `{"success": false}`，
而 **proto3 的零值完全不落 wire** ⇒ 响应在 wire 上是**空字节** ⇒
接收端只能靠「**没这个字段**」判 false。
⇒ 两层的表达**本就不同** ⇒ 只有字节级对拍才钉得住这件事。
⇒ 若哪一侧给 `false` 写了 presence（proto2 语义 / `optional`）⇒ 字节不同 ⇒ 这里立刻红。
⚠️ comment 域有 **5** 个这样的 `bool`（like / delete / manage / report / updateSubjectState）。

### 🔴 本探针**不**覆盖的一格：`owner_id`（两侧 proto 定义本身不等）

Go 真源的 `proto/comment.proto:105` 有 `int64 owner_id = 7`，
但 Go 侧**真正在用的生成码** `service/comment/rpc/pb/comment.pb.go` 里
**根本没有** `owner_id` / `OwnerId`（**连 rawDesc 都没有** ⇒ 该生成码比 `.proto` 旧）。

⇒ **两侧的 proto 定义本身就不等** ⇒ 这正是「`.proto` 一致 ⇒ 生成码一致」
这条假设的**反例**（AGENTS.md §3.4 效力边界第 1 条）。

⇒ **两侧都不落这个字段**：
- Go 侧**无字段可设**（生成码里没有）；
- RAG 侧虽生成出了 `ownerId`，但 rpc 层**刻意不设**
  （`CommentDomainService` 组装 `SubjectInfo` 时逐字段赋值、无 `owner_id`）
  ⇒ 与 Go `get_comment_logic.go:152-159`（同样只赋 6 个字段）**同构**。

⇒ ⇒ **该字段不进本探针的用例集**（Go 侧无法对称设置 ⇒ 比不了），
也不在 Java 测试的「用例集一致」断言里。
⚠️ **这是本域的既有事实、不是本轮新缺陷**：api 层早已把它对齐
（`GetCommentService` 刻意 `setOwnerId(0L)` ＋ `CommentApiBehaviorTest#ownerIdIsAlwaysZero` 守门）。

## 它把哪条证据升级了

`docs/DRIFT/comment/vs-common.md` 的 `C-5`（**A7 半边**）此前**只到静态对照**
（`tools/difftest/comment/check-proto-parity.py`），
本探针把它升级为**字节级**（两侧同时跑的探针 ⇒ L4）。

⚠️ **A6 半边**（错误码契约）此前已由 `tools/errmsg-probe/comment/` 闭合 ⇒
**两条半边合起来 = C-5 闭合**。

⚠️ 本探针**不**覆盖 proto 的**消费方式**（`optional` presence / 字段号硬编码 / Jackson 配置）
—— 那是 `judge` §九 的 `G-19`（**10 个 proto 全部**），**不是**本探针能覆盖的。

## 它有没有牙齿（变异验证）

```bash
bash tools/proto-wire-probe/comment/mutate-wire.sh
echo $?   # 0 = 三条变异都精确转红 + 还原后转绿
```

| 变异 | 打的哪一段 | 期望 |
|---|---|---|
| **M1** | 冻结的 `expected.txt` 被改 | 第 3 步（Go 侧 diff）**exit 1** |
| **M2** | Go 探针程序 `main.go` 的取值被改 | 第 3 步（Go 侧 diff）**exit 1** ⇒ 证明 `expected.txt` 不是「谁都能写出来」的摆设 |
| **M3** | **RAG 侧 `comment.proto` 的字段号被改**（`LikeCommentReq.action_type` 5 → 6） | 第 4 步（Java 侧断言）**exit 2** ⇒ **最重要的一条**：证明 Java 侧真的在拿字节跟 Go 真源比，不是在「自己跟自己」 |

⚠️ **M3 只改字段号、不改字段名** ⇒ RAG 侧代码照常编译 ⇒ 红的原因**只可能**是字节不同
（若改成字段名 ⇒ 编译失败 ⇒ 那是另一种红，不能证明本条）。
⚠️ **M3 的字段号必须取未被占用的**：`LikeCommentReq` 占了 **1..5** ⇒ 取 **6**。
⚠️ **M3 变异前先 `mvn -pl service/comment/comment-proto clean`** ⇒ 否则 protoc 可能不重新生成
⇒ 变异「**没打中**」⇒ 假绿（同 `docs/Pitfalls/hot/api.md` **P-HOTAPI-25**）。
⚠️ **还原校验用内容哈希，不用 `git status`** —— 这三个文件在脚本运行时可能还是未跟踪的新文件
⇒ `git status` 只报「?? 路径」⇒ **看不出内容变没变** ⇒ 恒绿假绿。

## 为什么 Java 侧住在 comment-rpc 而不是 comment-proto

⚠️ `comment-proto` 模块**一行 junit 依赖都没有**（pom 里只有生成代码所需的 protobuf / grpc）
⇒ 往那儿加测试要先改 pom。
⇒ 放在**消费方** comment-rpc：它本来就依赖 comment-proto，且 proto 本来就是「给 rpc 用的」。
⇒ 副作用是好的：**comment-proto 从「只有生成码级证据」变成「有字节级守门」**。

## 已知边界

- **不覆盖** gRPC 帧（HTTP2 / length-prefix / status 帧）⇒ 那需要两侧同时起服务
  （favorite 域那条链 `tools/api-full-parity-probe/favorite-rpc/` 才覆盖到帧层）。
- **不覆盖** proto 的**服务定义**（`CommentService` 的 7 个 RPC 方法名 / 路径）⇒ 那由
  `tools/difftest/check-proto-static.sh` 负责。
- **不覆盖** proto 的**消费方式**（`optional` presence / 字段号硬编码 / Jackson）⇒ 那是 `G-19`。
- **不覆盖** `owner_id`（见上：Go 侧生成码里没有该字段 ⇒ 无法对称比较）。
