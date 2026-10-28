# proto-wire-probe/hot：hot proto **消息体 wire 字节**的双侧对拍探针

> 用途：把 hot 的 protobuf 消息体**逐字节**钉在 Go 真源上。
> 这是 AGENTS.md §3.3 要求的「**两侧同时跑的探针**」⇒ 闭合 `docs/DRIFT/hot/proto.md` §3 的 `H-3`。

## 它补的是哪个缺口

AGENTS.md §3.4 只保证「`.proto` **文件本身**两侧一致」，
**推不出**「两侧**生成码**产出的 wire 字节一致」：

- 字段号错一个 ⇒ wire 完全不同，但 `.proto` 文件「看着一致」；
- 零值落不落 wire（proto3 的隐式 presence）—— 只有序列化才看得见；
- `repeated` 的编码方式（tag + length-delimited）—— 同上；
- **`bool` 的零值**（`success=false`）—— hot 域最容易漂的一处，见下。

⇒ 本探针用**同一组消息**在两侧各序列化一次，比对**十六进制字节**。

⚠️ **两侧的运行时不同**：Go 侧是 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
Java 侧是 `protobuf-java`（protoc 的 Java 运行时）⇒ 这**不是**「同一份实现自证」。

## Go 侧用的是**真源真正在用的生成码**

复制的是 `service/hot/rpc/pb/hot.pb.go`（Go 侧**真正在用**的那份，
**不是**重新 `protoc` 一遍）⇒ 验的是**生产字节**。
复制件做 **sha256 比对** ⇒ **真源换了生成码探针立刻响**。

## 怎么跑

```bash
bash tools/proto-wire-probe/hot/run.sh
echo $?   # 0 = 双侧逐字节一致
```

- 第 1 步：复制真源生成码 + sha256 校验；依赖版本**从真源 `go.mod`/`go.sum` 整体继承**。
- 第 2 步：跑 Go 侧 ⇒ 输出 13 条 `WIRE|<name>|<hex>`。
- 第 3 步：与冻结的 `expected.txt` 逐字节 diff。
- 第 4 步：跑 Java 侧 `HotProtoWireParityTest`（**真实**的 hot-proto 生成码）⇒
  断言的也是**同一份** `expected.txt`。

## 十三条用例各钉什么

| 用例 | 钉住的事实 |
|---|---|
| `req_full` | `int32 top_k` 的 varint 编码 |
| `req_zero` | 🔴 proto3 的标量零值**不落 wire** ⇒ 必须是**空字节** |
| `req_negative` | 🔴 **负 int32** 是 **10 字节 varint**（符号扩展成 64 位补码）⇒ 抓「被编成 zigzag/sint32」 |
| `item_full` | 🔴 **`uint32` 最大值**（0xFFFFFFFF）是 **5 字节 varint** ⇒ 抓「被当 int32 装」（会变成 -1 ⇒ 10 字节） |
| `item_partial` | 🔴 **只设了的字段才落 wire**（空字符串**不**落 —— 那是 JSON 的行为，不是 proto3 的） |
| `resp_two` | 🔴 `repeated` 的编码（两次 tag 1 + length-delimited，不是 JSON 数组） |
| `resp_empty` | 空 `repeated` ⇒ **空字节**（tag 都不落） |
| `like_req` | 点赞请求：两个 `string` 字段 |
| `like_resp_true` | `bool` = true ⇒ varint 1 |
| `like_resp_false` | 🔴🔴 **`bool` = false ⇒ 完全不落 wire ⇒ 整条消息是空字节** |
| `comment_req` | 评论请求：与点赞请求同形 |
| `coin_req` | 投币请求：`string + string + int32`（`coin_count` 是 varint，不是字符串） |
| `share_req` | 分享请求：三个 `string`（`channel` 是自由文本，不做枚举） |

### 🔴 `like_resp_false` 是 hot 域最该盯住的一条

api 层交给客户端的 JSON 是 `{"success": false}`，
而 **proto3 的零值完全不落 wire** ⇒ 响应在 wire 上是**空字节** ⇒
接收端只能靠「**没这个字段**」判 false。
⇒ 两层的表达**本就不同** ⇒ 只有字节级对拍才钉得住这件事。
⇒ 若哪一侧给 `false` 写了 presence（proto2 语义 / `optional`）⇒ 字节不同 ⇒ 这里立刻红。

## 它把哪条证据升级了

`docs/DRIFT/hot/proto.md` §3 的 `H-3`（proto **消息体** wire 探针）此前**只到生成码级**，
本探针把它升级为**字节级**（两侧同时跑的探针 ⇒ L4）。

⚠️ §3 的另一半仍未做：**proto 的**消费方式**（`optional` presence / 字段号硬编码 / Jackson 配置）**
—— 那是 `judge` §九 的 `G-19`（**10 个 proto 全部**），**不是**本探针能覆盖的。

## 它有没有牙齿（变异验证）

```bash
bash tools/proto-wire-probe/hot/mutate-wire.sh
echo $?   # 0 = 三条变异都精确转红 + 还原后转绿
```

| 变异 | 打的哪一段 | 期望 |
|---|---|---|
| **M1** | 冻结的 `expected.txt` 被改 | 第 3 步（Go 侧 diff）**exit 1** |
| **M2** | Go 探针程序 `main.go` 的取值被改 | 第 3 步（Go 侧 diff）**exit 1** ⇒ 证明 `expected.txt` 不是「谁都能写出来」的摆设 |
| **M3** | **RAG 侧 `hot.proto` 的字段号被改** | 第 4 步（Java 侧断言）**exit 2** ⇒ **最重要的一条**：证明 Java 侧真的在拿字节跟 Go 真源比，不是在「自己跟自己」 |

⚠️ **M3 只改字段号、不改字段名** ⇒ RAG 侧代码照常编译 ⇒ 红的原因**只可能**是字节不同
（若改成字段名 ⇒ 编译失败 ⇒ 那是另一种红，不能证明本条）。
⚠️ **M3 变异前先 `mvn -pl service/hot/hot-proto clean`** ⇒ 否则 protoc 可能不重新生成
⇒ 变异「**没打中**」⇒ 假绿（同 `docs/Pitfalls/hot/api.md` **P-HOTAPI-25**）。
⚠️ **还原校验用内容哈希，不用 `git status`** —— 这三个文件在脚本运行时可能还是未跟踪的新文件
⇒ `git status` 只报「?? 路径」⇒ **看不出内容变没变** ⇒ 恒绿假绿。

## 为什么 Java 侧住在 hot-rpc 而不是 hot-proto

⚠️ `hot-proto` 模块**一行 junit 依赖都没有**（pom 里只有生成代码所需的 protobuf / grpc）
⇒ 往那儿加测试要先改 pom。
⇒ 放在**消费方** hot-rpc：它本来就依赖 hot-proto，且 proto 本来就是「给 rpc 用的」。
⇒ 副作用是好的：**hot-proto 从「只有生成码级证据」变成「有字节级守门」**。

## 已知边界

- **不覆盖** gRPC 帧（HTTP2 / length-prefix / status 帧）⇒ 那需要两侧同时起服务。
- **不覆盖** proto 的**服务定义**（`HotService` 的 4 个 RPC 方法名 / 路径）⇒ 那由
  `tools/difftest/check-proto-static.sh` 负责。
- **不覆盖** proto 的**消费方式**（`optional` presence / 字段号硬编码 / Jackson）⇒ 那是 `G-19`。
