<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-api 踩坑档（为什么会踩）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> 本档只记「**为什么会这样**」，具体漂移条目在 [`docs/DRIFT/follow/api.md`](../../DRIFT/follow/api.md)。

## PITFALL-FOLLOW-006 🔴🔴 proto3 的空 repeated 字段在 wire 上**"消失"** ⇒ Go 得 nil、Java 得空 List

**症状（双侧活体对拍抓到的唯一一条真漂移）**

```
Go  : {"code":200,"msg":"OK","data":{"user_ids":null}}     ← 空关注列表
RAG : {"code":200,"msg":"OK","data":{"user_ids":[]}}        ← 修复前
```

**根因：中间隔着一次 protobuf 编解码，而两侧的"空集合"语义不同**

```
follow-rpc 进程内： UserListResp{UserIds: make([]int64, 0, 0)}   ← 非 nil 空切片
     │  protobuf wire：proto3 的 repeated 为空 ⇒ 该字段整体不上 wire
     ▼
follow-api 进程内： rpcResp.UserIds == nil                       ← Go 反序列化得 nil
     │  encoding/json（tag 无 omitempty）⇒ nil ⇒ null
     ▼
HTTP body：         {"user_ids":null}
```

Java 侧则相反：protobuf-java 的 `getUserIdsList()` **永远返回非 null 的空 List**
⇒ 转成 DTO 的空 list ⇒ Jackson 输出 `[]`。

**为什么源码级对照看不出来**

RAG 原注释（`FollowUserListResp`）写：

> 「rpc 侧是非 nil 空切片 ⇒ 输出 `[]`」

—— 那是 **rpc 进程内**的值。**中间那次 wire 往返被漏掉了**。
两边单看源码都"自洽"，**只有真跑起来才能发现**。

**教训**

1. ⚠️ **跨进程边界看「空集合」**：proto3 repeated / map 为空 ⇒ **字段缺失** ⇒
   Go 反序列化得 **nil**、Java 得 **空集合**。
   ⇒ **凡是把 proto 字段透传到 JSON 出参的地方，nil-vs-空集合就是 wire 级差异。**
2. ⚠️ **判据**：Go 侧那个值是「**进程内 make 出来的**」还是「**反序列化得来的**」？
   - 反序列化得来的 ⇒ 空 ⇒ nil ⇒ JSON `null`
   - 进程内 make 的 ⇒ 空 ⇒ 非 nil ⇒ JSON `[]`
   （follow 域两处正好各占一边：`user_ids` 是前者、`users` 是后者 ⇒ **同一个域里两种输出**。）
3. ⇒ **这类差异不可能靠"读两遍代码"发现** —— AGENTS.md §3.3 的
   「两侧同时跑的探针」在此处是**唯一**的发现手段。

---

## PITFALL-FOLLOW-007 🔴 测试可以**锁死错误答案**（绿测试比没有测试更危险）

**症状**：`FollowApiMvcPipelineTest#userIdsAreSerializedAsNumbers` 里有一条断言：

```java
assertTrue(emptyBody.contains("\"user_ids\":[]"), "空 user_ids 必须是 []，实际 body = " + emptyBody);
```

它**把错误答案写成了"规范"**，还配了一句注释「⚠️ 空列表不能是 null」。

**危害**：测试全绿 ⇒ 任何后来人改回 `null` 都会红 ⇒ **错误答案被测试锁死**。

**处置（AGENTS.md §3.3 强制顺序）**

> **修复漂移时先改/删错误断言，再改实现** —— 否则会先撞上「测试红了」而误以为自己改错了。

本轮顺序：
1. 先把断言改成 `"user_ids":null`（并在注释里写清**为什么**原先是错的、依据是哪次对拍）；
2. 再改 `FollowApiSupport#toUserListResp`（空列表 ⇒ `setUserIds(null)`）与 DTO；
3. 最后重跑：49 tests / 0 failures ✅ + 双侧对拍 16/16 ✅。

**教训**

1. ⚠️ **断言里带「⚠️」的注释不代表它对** —— 恰恰相反：
   「⚠️ 这里必须这样」的断言往往是在**固化一个当时没被验证过的判断**。
   ⇒ 写「⚠️」之前先问：**这条断言的依据是源码、是文档、还是实测？**
2. ⇒ **凡是「看起来反直觉」的断言，应该有一条对拍或探针兜底**，
   而不是把反直觉本身写成规范。

---

## PITFALL-FOLLOW-009 🔴 `@JsonProperty` 显式命名的属性之间，Jackson 按**字母序**排

**症状（双侧活体对拍抓到）**

```
Go  : {"users":[{"target_id":"4","mutual_score":20}]}
RAG : {"users":[{"mutual_score":20,"target_id":"4"}]}      ← 字段顺序反了
```

**根因**

- Go 的 `encoding/json` 按 **struct 字段声明序**输出。
- Jackson 默认也按「字段声明序」—— **但当所有属性都带 `@JsonProperty` 显式命名时，
  落入按名字排序的分支** ⇒ `mutual_score` < `target_id`（字母序）⇒ 顺序反过来。
- 本仓 Boot 4 / Jackson 3 **没有全局 snake_case 策略** ⇒ 每个字段都必须写 `@JsonProperty`
  ⇒ **凡是有 ≥2 个字段的出参 DTO，都会命中这条**。

**后果等级**：⚠️ **不是语义差异**（JSON 对象无序，解析器都认），
但它是 **wire 级字节差异** —— 做 body 快照比对 / 签名校验 / 严格字符串匹配的客户端会失配。

**修法**：DTO 上加 `@JsonPropertyOrder({"target_id", "mutual_score"})`（显式钉死）。

**本仓现状**

| DTO | 字段数 | 是否命中 | 处理 |
|---|---|---|---|
| `follow-api` 的 `RecommendUser` | 2 | 🔴 **命中** | ✅ 已加 `@JsonPropertyOrder`（DRIFT-FOLLOW-005） |
| `follow-api` 的 `FollowUserListResp` / `RecommendListResp` / `RelationActionResp` | 1 | — | 单字段无序问题 |
| `service/common` 的 `Response`（envelope） | 3 | ❌ **不命中**（无 `@JsonProperty` ⇒ 走声明序）⇒ `code, msg, data` 与 Go 一致 | 不动 |

**教训**

1. ⚠️ **「加了 `@JsonProperty`」不只影响 key 名，还影响 key 的顺序** —— 这一条在
   「只关心能不能解析」的测试里**永远测不出来**。
2. ⇒ **凡是要与 Go 逐字节对齐的出参 DTO**：
   - ≥2 个字段 ⇒ 必须显式 `@JsonPropertyOrder`；
   - 断言要断**整个片段的字节序列**（本轮加的 `recommendFieldOrderMatchesGo` 就是断
     `"target_id":"4","mutual_score":20` 这个**连续子串**，而不是分别断两个 key 存在）。
3. ⚠️ 本仓**已有同款先例**（`like-rpc` 的 `LikeEventContracts`、`comment-rpc` 的
   `CommentCacheService` 早已用 `@JsonPropertyOrder`）⇒ **follow 域是漏网的一个**，
   说明这个模式**不是新问题，是覆盖率问题** ⇒ 其它域值得同样查一遍。

---

## PITFALL-FOLLOW-008 ⚠️ `follow-api` 没有可执行 fat jar

**现象**：`mvn install` 产出的 `follow-api-0.0.1-SNAPSHOT.jar` 只有 **53 KB**（普通 jar），
`java -jar` 跑不起来 ⇒ 必须用
`java -cp <runtime classpath> com.rag.follow.follow.api.FollowApiApplication` 启动。

**根因**：`service/follow/follow-api/pom.xml` **没有 `spring-boot-maven-plugin`**
（`follow-rpc/pom.xml` 有）。

**范围**：`article-api` / `comment-api` / `hot-api` / `like-api` / `message-api` / `task-api` 同款；
只有 `favorite-api` 有该插件。

**处置**：**未改动** —— 这是**打包形态**问题，不是 Go↔RAG 语义漂移，
且改动会牵动部署方式（超出对齐 scope）⇒ 仅登记。

---

## PITFALL-FOLLOW-010 🔴🔴 探针用**固定** `-o` 文件 ⇒ 请求失败时读到的是**上一次的残留 body**

**症状**：一次「双侧逐字节一致 ✅」的对拍，**两侧其实都没发出一个请求**：

```
01-无Authorization|HTTP=000|{"code":200,"msg":"OK","data":{"user_ids":[2002,999999]}}
```

⇒ `HTTP=000`（curl 压根没连上），body 却是一段**正常的 200 响应**。

**根因：两个缺陷叠在一起**

探针取 body 的写法（`/tmp/probe.sh:3`）：

```bash
p(){ n="$1"; shift; printf "%s|" "$n"; code=$(curl -sS -o /tmp/pb.txt -w "%{http_code}" "$@" 2>/dev/null); printf "HTTP=%s|" "$code"; printf "%s\n" "$(head -c 200 /tmp/pb.txt)"; }
```

1. `-o` 的目标是**固定文件名** `/tmp/pb.txt`，**每次请求前不清空**；
2. ⚠️ **curl 连接失败时不会创建、也不会清空 `-o` 目标文件**（实测）：

```bash
$ printf 'PRIOR-CONTENT\n' > /tmp/curltest.txt
$ curl -sS -m 3 -o /tmp/curltest.txt -w "http=%{http_code}\n" http://127.0.0.1:1/nope
http=000
$ cat /tmp/curltest.txt
PRIOR-CONTENT          ← 原样保留！
```

⇒ 于是 **body 列显示的是上一次请求的响应**。

**为什么后果严重**：对拍脚本对两侧**跑同一串探针**，
只要 BASE 传错（两侧指向同一个不存在的地址），**两侧输出逐字节相同**
⇒ `diff` 判定「✅ 两侧逐字节一致」⇒ **假绿**。
而且 body 看起来是**正常的 200 JSON** ⇒ 人眼更难怀疑。

**复现（本轮实测）**

```bash
$ bash /tmp/probe.sh "http://127.0.0.1:1/follow/v1" > /tmp/fg-a.txt
$ bash /tmp/probe.sh "http://127.0.0.1:1/follow/v1" > /tmp/fg-b.txt
$ diff -q /tmp/fg-a.txt /tmp/fg-b.txt && echo "完全一致"
完全一致        ⇒ 会被判为「✅ 两侧逐字节一致」
```

**教训**

1. ⚠️ **探针的「失败」必须与「成功」在输出上可区分**：
   - `http_code == 000` ⇒ curl 没拿到任何响应 ⇒ **该行无效**，
     比对前必须先断言「输出里没有 000」；
   - body 文件**每次请求前先 `rm -f`**，或改用**唯一文件名**（`mktemp`）。
2. ⚠️ **「两侧一致」不等于「两侧都测了」** ——
   对拍脚本必须**先单侧自检装置**（打一发看是否 200），**再**跑对拍。
   否则一次打错地址，就得到一份「全绿」的假报告。
3. ⚠️⚠️ **本轮实际踩到的三种「假绿」，一次比一次隐蔽**：

   | # | 假绿形态 | 表面现象 | 真相 | 判据 |
   |---|---|---|---|---|
   | a | BASE 传错 | `✅ 两侧逐字节一致（20 行）` | 两边都 `HTTP=000`（连都没连上） | 断言**无 `HTTP=000`** |
   | b | **JWT 过期** | `✅ 两侧逐字节一致（52 行）` | 两边都 **401**（数据路径一条没跑成） | 断言**至少 N 条业务成功体**（`{"code":200`） |
   | c | body 残留 | body 显示**正常的 200 JSON** | 那是**上一次请求**的响应 | 每次请求前 `rm -f` body 文件 |

   ⇒ b 最隐蔽：**52 条探针全部命中「两侧都失败」**，diff 照样全绿。
   ⇒ **通用判据：对拍必须断言「探针真的跑到了业务路径」**，
   即：输出里存在业务成功响应、且不存在无效响应行。
   ⇒ **建议把这条断言写进对拍脚本本身**（本轮已加在收尾复核里，
   但 `/tmp/cmp.sh` 本身还没内置 ⇒ 属待办）。
4. ⇒ 推广：**任何「逐字节比对」类脚本，都要能区分「值相同」与「两边都没取到值」。**
   本条与 PITFALL-FOLLOW-011、013 同源：**假绿比红贵**。

---

## PITFALL-FOLLOW-011 🔴🔴 故障模拟器**不完成 HTTP/2 握手** ⇒ 测的是「连不上」，不是「挂住」

**症状**：U3（gRPC 客户端熔断）首轮实测得到「Go 5/5 快失败、RAG 1/5 快失败」，
据此写下「RAG 的熔断几乎不生效」的结论。

**重测后结论完全反转**：两侧熔断行为**等价**
（第 1 轮都全放行、第 2 轮都是概率性混合、失败码都是 `DeadlineExceeded`）
⇒ 详见 [`docs/DRIFT/follow/api.md`](../../DRIFT/follow/api.md) 的 U3 受控对照段。

**根因：模拟器的故障形态选错了**

| 版本 | 模拟器行为 | 客户端看到的 | 实际测到的是 |
|---|---|---|---|
| v1 | 读完 preface 就 **close** | `Unavailable: connection error`（0.0ms） | **连不上** |
| v2 | 读完 preface 就持有不关 | 卡在 **HTTP/2 握手**（等服务器 SETTINGS） | 通道**建不起来** |
| **v3（本轮采用）** | **完成 HTTP/2 握手**（回 SETTINGS + SETTINGS ACK）后**不再回任何业务响应** | `DeadlineExceeded`（等满客户端超时） | **下游活着但不响应** |

⚠️ **v2 最隐蔽**：连接还「挂着」，看起来像挂死；
但客户端卡在**握手阶段**，此时 grpc-java 会 fail-fast（`UNAVAILABLE`，~70ms），
而 grpc-go 会一直等到 deadline（`DeadlineExceeded`，2s）
⇒ **两个 gRPC 库的默认差异被当成了业务差异**。

**教训**

1. ⚠️ **故障注入必须复刻「故障发生的那一刻」**，而不是「连接看起来还在」：
   - 「下游无响应」= **握手已完成 + 之后不回应**；
   - 「下游不可用」= **握手就失败**。
   两者在客户端上的表现**完全不同**。
2. ⚠️ **装置要能自证**：本轮用裸 HTTP/2 客户端手动跑一遍握手，
   确认收到了 `SETTINGS`（type=0x04 flags=0x00）与 `SETTINGS ACK`（flags=0x01）
   ⇒ 才敢说装置挂的是「业务层」。
3. ⚠️ **熔断是概率型**（`dropRatio` 阈值 + `rand() < dropRatio`），
   且有 **`protection=5` 冷启动保护**（窗口内失败数 ≤ 5 时 `dropRatio ≤ 0` ⇒ 一律放行）
   ⇒ **串行**请求永远打不开熔断（10s 窗口里攒不够 6 次失败）
   ⇒ **必须并发**发一批，才能让窗口内瞬间攒够 `total > 5`。
   ⇒ **探针形态选错 ⇒ 结论直接反转**（本轮就是这么反转的）。

---

## PITFALL-FOLLOW-012 🔴 go-zero 的 etcd 键是「**前缀 + `/`**」⇒ 裸键解析到**零地址**

**症状**：把 etcd 的键写成裸 `follow.rpc` 后，Go 侧每个请求都**立刻**返回 1015：

```
rpc error: code = Unavailable desc = last resolver error: produced zero addresses
```

**根因**：`go-zero@v1.10.2/core/discov/internal/registry.go:517`

```go
func makeKeyPrefix(key string) string {
	return fmt.Sprintf("%s%c", key, Delimiter)   // Delimiter = '/'
}
```

`registry.go:309`：`cli.Get(ctx, makeKeyPrefix(key), clientv3.WithPrefix())`
⇒ 实际查的是**前缀 `follow.rpc/`**，裸键 `follow.rpc` **不在这个前缀范围内** ⇒ **零个地址**。

⇒ 正确的键形态是 **`follow.rpc/<任意后缀>`**（本轮用 `follow.rpc/blackhole`）。

**为什么这个坑特别危险**：零地址与**熔断拒绝**在对外表现上**几乎一样**：

| 真实原因 | Go 日志里的 `error_reason` | 对外 body | 耗时 |
|---|---|---|---|
| 寻址失败（零地址） | `Unavailable desc = ... produced zero addresses` | `{"code":1015,...}` | **0.0ms** |
| 熔断打开 | `circuit breaker is open` | `{"code":1015,...}` | **0.0ms** |

⇒ **光看响应体和耗时分不出来**，必须去日志里看 `error_reason`。
本轮就因为没看日志，把「寻址失败」当成「熔断生效」解读了一整轮。

**另一层污染**：`registry.go:309` 用的是**前缀查询** ⇒ **同一前缀下的多个键会被当成多个候选地址**。
本轮一开始 `follow.rpc/` 下面同时挂着「真 rpc」与「黑洞」两个键
⇒ Go 侧在两个地址之间**轮询** ⇒ 测出来的「快失败」有一部分其实是**打到了真 rpc**。
⇒ **实验接线必须保证「前缀下只有一个键」。**

**教训**

1. ⚠️ **同一个业务码（1015 service busy）背后是完全不同的原因**：
   下游不可用 / 寻址失败 / 熔断打开 / 客户端超时 —— 四者在 HTTP 层都是 `200 + 1015`。
   ⇒ **对拍必须同时抓两侧日志里的 `error_reason`**，不能只看响应体。
2. ⚠️ **go-zero 的 etcd 键语义是「前缀」**：键必须是 `prefix/<后缀>`，
   且**前缀下只应有一个有效地址**，否则寻址结果不确定。
