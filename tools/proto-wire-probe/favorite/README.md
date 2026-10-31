# proto-wire-probe/favorite：favorite proto **消息体 wire 字节**的双侧对拍探针

> 用途：把 favorite 的 protobuf 消息体**逐字节**钉在 Go 真源上。
> 这是 AGENTS.md §3.3 要求的「**两侧同时跑的探针**」。

## 它补的是哪个缺口

AGENTS.md §3.4 只保证「`.proto` **文件本身**两侧一致」，
**推不出**「两侧**生成码**产出的 wire 字节一致」：

- 字段号错一个 ⇒ wire 完全不同，但 `.proto` 文件「看着一致」；
- 零值落不落 wire（proto3 的隐式 presence）—— 只有序列化才看得见；
- `repeated` 的编码方式（tag + length-delimited）—— 同上。

⇒ 本探针用**同一组消息**在两侧各序列化一次，比对**十六进制字节**。

⚠️ **两侧的运行时不同**：Go 侧是 `google.golang.org/protobuf`（protoc-gen-go 的运行时），
Java 侧是 `protobuf-java`（protoc 的 Java 运行时）⇒ 这**不是**「同一份实现自证」。

## Go 侧用的是**真源真正在用的生成码**

复制的是 `service/favorite/rpc/pb/favorite.pb.go`（Go 侧**真正在用**的那份，
**不是**重新 `protoc` 一遍）⇒ 验的是**生产字节**。
复制件做 **sha256 比对** ⇒ **真源换了生成码探针立刻响**。

## 怎么跑

```bash
bash tools/proto-wire-probe/favorite/run.sh
echo $?   # 0 = 双侧逐字节一致
```

- 第 1 步：复制真源生成码 + sha256 校验；依赖版本**从真源 `go.mod`/`go.sum` 整体继承**。
- 第 2 步：跑 Go 侧 ⇒ 输出 6 条 `WIRE|<name>|<hex>`。
- 第 3 步：与冻结的 `expected.txt` 逐字节 diff。
- 第 4 步：跑 Java 侧 `FavoriteProtoWireParityTest`（**真实**的 favorite-proto 生成码）⇒
  断言的也是**同一份** `expected.txt`。

## 六条用例各钉什么

| 用例 | 钉住的事实 |
|---|---|
| `folder_full` | 全字段的 varint / length-delimited 编码（用 2^53+1 的雪花 ID ⇒ **int64 在 wire 上不丢精度**） |
| `folder_zero` | 🔴 proto3 的标量零值**不落 wire** ⇒ 必须是**空字节** |
| `item_full` | 8 个字段的完整编码 |
| `item_partial` | 🔴 **只设了的字段才落 wire**（空字符串**不**落 —— 那是 JSON 的行为，不是 proto3 的） |
| `folders_resp_two` | 🔴 `repeated` 的编码（两次 tag 1 + length-delimited，不是 JSON 数组） |
| `create_req` | 请求消息：`int64` 在 wire 上是 **varint**，**不是**字符串（字符串是 api 层的 JSON 约定） |

## 它把哪条证据升级了

`docs/DRIFT/favorite/proto.md` §2 此前记的是「零值不落 wire」的**生成码级**证据
（读 `FavoriteFolder.writeTo` 的条件表达式）⇒ **本探针升级为字节级**。

⚠️ §4 的「真 channel 上的 wire 字节」还剩**一半**：
**消息体**这一层已由本探针闭合；
**gRPC status 帧 / HTTP2 帧**那一层仍未做（那需要两侧同时起服务）⇒ 见该文件 §4。

## 变异验证：证明这条链**不是假绿**（`mutate-wire.sh`）

> 🔴 **为什么必须有它**：一条「跑绿」的探针，可能只是**自己跟自己比**——
> 若 `expected.txt` 是照着 Java 侧现编的，那它**永远绿**，却证明不了任何事。
> ⇒ 必须**改实现 ⇒ 探针必红**（`docs/PhaseGoal-judge.md` §1.1 **R4 判据 ⑧**）。

```bash
bash tools/proto-wire-probe/favorite/mutate-wire.sh
echo $?   # 0 = 3 条变异都精确转红 + 还原后转绿 + 三个文件内容哈希回到变异前
```

| # | 变异打在哪 | 期望谁转红 | 实测 |
|---|---|---|---|
| M1 | 冻结的 `expected.txt` 的一枚 hex（`item_full` 首字节） | 第 3 步的 Go 侧 diff | ✅ exit 1（`Go 侧输出与 expected.txt 不一致`） |
| M2 | Go 探针 `go/main.go` 的一个取值（`folders_resp_two` 的第一个 `UserId`） | 同上 | ✅ exit 1 ⇒ 证明 `expected.txt` 不是「谁都能写出来」的摆设 |
| M3 | **RAG 侧** `favorite.proto` 的**字段号**（`FavoriteItem.created_at`：`8 → 9`） | 第 4 步的 Java 侧断言 | ✅ exit 2（`FavoriteProtoWireParityTest.favoriteItemWireMatchesGo` 失败 ⇒ 字节尾段 `40` → `48`） |

⚠️ **M3 只改字段号、不改字段名**：改字段名 ⇒ 编译失败 ⇒ 那是另一种红，
**证不了**「Java 侧在拿字节跟 Go 真源比」。
⚠️ **M3 前必须 `mvn -pl service/favorite/favorite-proto clean`**：否则 protoc 可能不重新生成
⇒ 变异「没打中」（**PIT-HOTAPI-25**：变异代码被静默沿用 ⇒ 假绿）。
⚠️ **还原不用 `git checkout`**（会连未提交的落码一起回滚）⇒ 用「备份 + 复制」还原；
**还原判定用内容哈希**（不用 `git status`——未跟踪的新文件在 `git status` 里看不出内容变没变 ⇒ 恒绿假绿）。

## 为什么 Java 侧住在 favorite-rpc 而不是 favorite-proto

⚠️ `favorite-proto` 模块**一行 junit 依赖都没有**（pom 里只有生成代码所需的 protobuf / grpc）
⇒ 往那儿加测试要先改 pom。
⇒ 放在**消费方** favorite-rpc：它本来就依赖 favorite-proto，且 proto 本来就是「给 rpc 用的」。
⇒ 副作用是好的：**proto 组件从「0 例测试」变成「有 1 个字节级守门」**。

## 已知边界

- **不覆盖** gRPC 帧（HTTP2 / length-prefix / status 帧）⇒ 那需要两侧同时起服务。
- **不覆盖** proto 的**服务定义**（7 个 RPC 的方法名 / 路径）⇒ 那由
  `tools/difftest/check-proto-static.sh` 负责。
