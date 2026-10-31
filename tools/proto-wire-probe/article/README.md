# proto-wire-probe/article：article proto **消息体 wire 字节**的双侧对拍探针

> 用途：把 article 的 protobuf 消息体**逐字节**钉在 Go 真源上。
> 这是 AGENTS.md §3.3 要求的「**两侧同时跑的探针**」。

## 它补的是哪个缺口

本域**已有**一道契约层守门（`ArticleProtoContractTest` ⇒ 比**生成码的描述符**），
但它**自己写明**「**不覆盖 wire 字节级**」⇒ AGENTS.md §3.4 的效力边界第 ① 条
「**复制忠实 ≠ 消费方式已验**」在本域**只剩一半**。

⇒ 而 article 是**四域里唯一有 proto3 `optional`（显式 presence）的域**
（`docs/DRIFT/article/proto.md` §2 列了 **11 个** optional 字段）⇒
「presence 语义两侧是否同形」**只有字节级才看得见**：
文件里 `optional string brief = 2;` 与 `string brief = 2;` **只差三个词**，
在 wire 上却是「**显式设的空串落不落 wire**」。

⇒ 本探针用**同一组消息**在两侧各序列化一次，比对**十六进制字节**。

⚠️ **两侧的运行时不同**：Go 侧是 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
Java 侧是 `protobuf-java`（protoc 的 Java 运行时）⇒ 这**不是**「同一份实现自证」。

## Go 侧用的是**真源真正在用的生成码**

复制的是 `service/article/rpc/pb/article.pb.go`（Go 侧**真正在用**的那份，
**不是**重新 `protoc` 一遍）⇒ 验的是**生产字节**。
复制件做 **sha256 比对** ⇒ **真源换了生成码探针立刻响**。

## 怎么跑

```bash
bash tools/proto-wire-probe/article/run.sh
echo $?   # 0 = 双侧逐字节一致
```

- 第 1 步：复制真源生成码 + sha256 校验；依赖版本**从真源 `go.mod`/`go.sum` 整体继承**。
- 第 2 步：跑 Go 侧 ⇒ 输出 7 条 `WIRE|<name>|<hex>`。
- 第 3 步：与冻结的 `expected.txt` 逐字节 diff。
- 第 4 步：跑 Java 侧 `ArticleProtoWireParityTest`（**真实**的 article-proto 生成码）⇒
  断言的也是**同一份** `expected.txt`。

## 七条用例各钉什么

| 用例 | 钉住的事实 |
|---|---|
| `article_full` | 全字段编码：varint / length-delimited / **枚举值编号进 wire** / `repeated string`（**两条独立 tag**，不是 packed blob）/ `map`（**单条目**）/ int64 用 2^53+1 ⇒ **不丢精度** |
| `article_zero` | 🔴 proto3 **隐式 presence** 的零值**不落 wire** ⇒ 必须是**空字节** |
| `create_req_optional_present` | 🔴🔴 **显式 presence**：两个 `optional` 字段**显式设成空串也落 wire**（Go 侧 = **非 nil 的指针**；Java 侧 = `hasBrief()` 为真）⇒ 期望字节里有 `12 00` 与 `22 00` 两个「长度 0」标记 |
| `create_req_optional_absent` | 🔴🔴 与上一条**同一组字段、只是没设过** ⇒ 那两个字段**一个字节都不落** ⇒ 两条的字节差 = **presence 标记本身** |
| `create_req_full` | 请求消息全字段（含两个 optional 都设了非空值 ＋ `repeated string` 两条） |
| `list_resp` | `repeated message`（两次 tag 1 的 length-delimited）＋ 分页 varint |
| `upload_req` | 🔴 `bytes` 字段（**不是 string**）⇒ length-delimited 的**原始字节** |

⚠️ **`create_req_optional_present` 与 `create_req_optional_absent` 这一对最有鉴别力**：
若某一侧把 `optional` 当成普通 `string`（隐式 presence）⇒ 「显式设空串」与「没设」
会产出**同样的字节** ⇒ 这一对就**失去鉴别力** ⇒ Java 侧专门有一条
`assertNotEquals` 把这件事钉死。

## 变异验证：证明这条链**不是假绿**（`mutate-wire.sh`）

> 🔴 一条「跑绿」的探针可能只是**自己跟自己比** ⇒ 必须**改实现 ⇒ 探针必红**
> （`docs/PhaseGoal-judge.md` §1.1 **R4 判据 ⑧**）。

```bash
bash tools/proto-wire-probe/article/mutate-wire.sh
echo $?   # 0 = 3 条变异都精确转红 + 还原后转绿 + 三个文件内容哈希回到变异前
```

| # | 变异打在哪 | 期望谁转红 | 实测 |
|---|---|---|---|
| M1 | 冻结的 `expected.txt` 的一枚 hex（`upload_req`） | 第 3 步的 Go 侧 diff | ✅ exit 1（`Go 侧输出与 expected.txt 不一致`） |
| M2 | Go 探针 `go/main.go` 的一个取值（`article_full` 的 `LikeCount`） | 同上 | ✅ exit 1 ⇒ 证明 `expected.txt` 不是「谁都能写出来」的摆设 |
| M3 | **RAG 侧** `article.proto` 的**字段号**（`CreateArticleRequest.cover_image_url`：`4 → 8`） | 第 4 步的 Java 侧断言 | ✅ exit 2（`ArticleProtoWireParityTest` 失败） |

⚠️ **M3 只改字段号、不改字段名**：改字段名 ⇒ 编译失败 ⇒ 那是另一种红，
**证不了**「Java 侧在拿字节跟 Go 真源比」。
⚠️ **M3 前必须 `mvn -pl service/article/article-proto clean`**：否则 protoc 可能不重新生成
⇒ 变异「没打中」（**PIT-HOTAPI-25**：变异代码被静默沿用 ⇒ 假绿）。
⚠️ **M3 选的字段是 `optional`** ⇒ 这一条同时证明「**presence** 那条断言也有牙齿」。
⚠️ **还原不用 `git checkout`**（会连未提交的落码一起回滚）⇒ 用「备份 + 复制」还原；
**还原判定用内容哈希**（不用 `git status`——未跟踪的新文件在 `git status` 里看不出内容变没变 ⇒ 恒绿假绿）。

## 为什么 Java 侧住在 article-rpc 而不是 article-proto

⚠️ `article-proto` 模块**没有测试依赖**（pom 里只有生成代码所需的 protobuf / grpc）
⇒ 往那儿加测试要先改 pom。
⇒ 放在**消费方** article-rpc：它本来就依赖 article-proto，且 proto 本来就是「给 rpc 用的」。
⇒ 与 favorite 域同款做法。

## 已知边界

- **不覆盖** gRPC 帧（HTTP2 / length-prefix / status 帧）⇒ 那需要两侧同时起服务。
- **不覆盖** proto 的**服务定义**（6 个 RPC 的方法名 / 路径）⇒ 那由
  `tools/difftest/check-proto-static.sh` 与 `ArticleProtoContractTest` 负责。
- **`map` 只做单条目** ⇒ 两个运行时的 map 遍历顺序都**不保证**（Go 的 map 随机序、
  Java 的 `MapField` 未定义序）⇒ 多条目逐字节比对 = **假红**。
- **不覆盖跨栈 gRPC 互调**（`docs/DRIFT/article/proto.md` 的 `P-U-1`）⇒ 那需要起 etcd ＋ 双栈。
