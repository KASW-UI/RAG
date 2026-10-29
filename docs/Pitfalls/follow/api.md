<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-api 踩坑档（为什么会踩）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> 本档只记「**为什么会这样**」，具体漂移条目在 [`docs/DRIFT/follow/api.md`](../../DRIFT/follow/api.md)。

> 🆕 **2026-10-29 台账对齐（W-B lane · follow + task 域）**
>
> - **编号归一**：本档条目的**旧号** `PITFALL-FOLLOW-NNN` **原样保留**在每条的「旧号」行里；**新号** = `PIT-FOLLOW-NN`（与旧号后三位一一对应）⇒
>   ⚠️ **旧号是检索用的历史标识** —— 仓内其它文档（DRIFT / Stage1 / 共享层 javadoc / `tools/`）按**旧号**检索，本档**两套号都在** ⇒ 旧号**不可删**。
> - **每条补三字段**：**状态** · **核对日期（2026-10-29）** · **是否影响当前轮次**（填「否」时必须写明**不适用的具体原因**）。
> - **状态判定口径**（本档统一）：
>   - **已闭环** = 该坑在**本域的具体触发点已被消除**（代码 / 配置 / 脚本已改）**且有证据指针**（`文件:行号` 或守门的实测条数）⇒ **残留的通用教训不改变这个判定**（通用教训留在正文里）。
>   - **仍生效** = 该坑的**触发条件本身**仍在（框架 / 语言 / 真源的事实，或本域的编排脚本仍未改）⇒ 今天照旧做法还会踩。
>   - **已撤销** = 用户 / 决策撤销。**待核实** = 判不了（须写清缺什么）⇒ **不许默认任何一侧**。
> - ⚠️ **状态标记取值域**：`✅` / `🟡` / `🔴` / `⚫`（与仓内既有取值域一致）。

## PIT-FOLLOW-006 · 【已闭环】 proto3 的空 repeated 字段在 wire 上**"消失"** ⇒ Go 得 nil、Java 得空 List

> **旧号**：`PITFALL-FOLLOW-006` ⇒ **新号**：`PIT-FOLLOW-006`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 空集合语义已按 Go 的 nil 落码并被断言钉住 ⇒ 触发条件已消除 ⇒ 本轮只核台账、不改实现 ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-api/src/main/java/com/rag/follow/follow/api/service/FollowApiSupport.java:265` `out.setUserIds(null)` ＋ `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiMvcPipelineTest.java:625-626` 断言 `"user_ids":null`

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

## PIT-FOLLOW-007 · 【已闭环】 测试可以**锁死错误答案**（绿测试比没有测试更危险）

> **旧号**：`PITFALL-FOLLOW-007` ⇒ **新号**：`PIT-FOLLOW-007`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 错误断言已改写（不再是「锁死错误答案」的守卫）⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`FollowApiMvcPipelineTest.java:625-626`（断言已从 `"user_ids":[]` 改为 `"user_ids":null`） ＋ `:614-615` 注释写明依据（Go 侧反序列化得 nil ⇒ `encoding/json` 输出 null）

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

## PIT-FOLLOW-009 · 【已闭环】 `@JsonProperty` 显式命名的属性之间，Jackson 按**字母序**排

> **旧号**：`PITFALL-FOLLOW-009` ⇒ **新号**：`PIT-FOLLOW-009`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 出参 DTO 已显式钉死字段顺序 ⇒ 本轮只核台账、不改 DTO ⇒ 不影响本轮产出
> **证据**：`service/follow/follow-api/src/main/java/com/rag/follow/follow/api/dto/RecommendUser.java:57` `@JsonPropertyOrder({"target_id", "mutual_score"})`

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

## PIT-FOLLOW-008 · 【已闭环】 `follow-api` 没有可执行 fat jar

> **旧号**：`PITFALL-FOLLOW-008` ⇒ **新号**：`PIT-FOLLOW-008`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`⚠️`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 打包形态已改（fat jar 可执行）⇒ 触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出。⚠️ **原台账「处置：未改动 —— 仅登记」已过期**（2026-10-24 留痕，本次对齐按现状订正）
> **证据**：`service/follow/follow-api/pom.xml:196` 已有 `spring-boot-maven-plugin`（`:193` 注释写明靠它打成可执行 jar） ＋ `service/common/src/test/java/com/rag/common/spring/HttpPoolShutdownWithoutServletGateTest.java` 同款产物层守门已在共享层

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

## PIT-FOLLOW-010 · 【已闭环】 探针用**固定** `-o` 文件 ⇒ 请求失败时读到的是**上一次的残留 body**

> **旧号**：`PITFALL-FOLLOW-010` ⇒ **新号**：`PIT-FOLLOW-010`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴🔴`
> **状态**：**已闭环** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 探针已改为「每请求一个唯一文件 + `http_code==000` 守卫」⇒ 残留 body 的触发条件已消除 ⇒ 本轮只核台账 ⇒ 不影响本轮产出
> **证据**：`tools/api-full-parity-probe/follow/run.sh:529`（`out="$(mktemp "${WORK}/body.XXXXXX")"`） ＋ `run.sh:579-581`（`http_code == 000` 守卫） ＋ `tools/api-full-parity-probe/follow/mutate-parity.sh:161-173`（**M5** 专门把假绿复现出来）

**症状**：一次「双侧逐字节一致 ✅」的对拍，**两侧其实都没发出一个请求**：

```
01-无Authorization|HTTP=000|{"code":200,"msg":"OK","data":{"user_ids":[2002,999999]}}
```

⇒ `HTTP=000`（curl 压根没连上），body 却是一段**正常的 200 响应**。

**根因：两个缺陷叠在一起**

探针取 body 的写法（⚠️ **2026-10-29 真实性复核**：原引 **`/tmp/probe.sh` 的第 3 行** —— 该行号指向**本机一次性临时探针脚本、不随仓留存 ⇒ 本机不可核验**；**故不再作为仓内可复算指针** ⇒ 下方**脚本内容已整段内联**，可逐字核对；**仓内可复算的等价落点**见上方「证据」行：`tools/api-full-parity-probe/follow/run.sh:529`（`mktemp` 唯一 body 文件）＋ `:579-581`（`http_code == 000` 守卫）＋ `tools/api-full-parity-probe/follow/mutate-parity.sh:161-173`（**M5** 把这条假绿复现出来）—— **三处本轮均已当场逐行核过，行内容与断言一致**）：

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

## PIT-FOLLOW-011 · 【仍生效】 故障模拟器**不完成 HTTP/2 握手** ⇒ 测的是「连不上」，不是「挂住」

> **旧号**：`PITFALL-FOLLOW-011` ⇒ **新号**：`PIT-FOLLOW-011`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— **故障注入装置至今未落仓**（`tools/api-full-parity-probe/follow/` 只有 `run.sh` / `expected.txt` / `mint-jwt.py` / `mutate-parity.sh` / `README.md` 五件，无黑洞模拟器；旧的 `/tmp/blackhole3.py` 已不存在）⇒ 触发条件仍在 ⇒ 但本轮**只核台账、不建装置** ⇒ 不影响本轮台账产出
> **证据**：`tools/api-full-parity-probe/follow/` 目录清单（2026-10-29 实测） ＋ `docs/DRIFT/follow/api.md:16-19`（「52/52 逐字节一致」已**降级为历史声明，不可复算**） ＋ `docs/DRIFT/follow/stage1-admission.md:361`（故障注入类仍**无落仓入口**）

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

## PIT-FOLLOW-012 · 【仍生效】 go-zero 的 etcd 键是「**前缀 + `/`**」⇒ 裸键解析到**零地址**

> **旧号**：`PITFALL-FOLLOW-012` ⇒ **新号**：`PIT-FOLLOW-012`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 这是**真源（Go）侧的事实**（go-zero 的 etcd 键语义）⇒ RAG 侧无从修、也不该改 ⇒ 与本轮台账对齐无关 ⇒ 不影响本轮产出
> **证据**：Go `go-zero@v1.10.2/core/discov/internal/registry.go:517` `makeKeyPrefix`（拼 `key + '/'`） ＋ `:309` `cli.Get(ctx, makeKeyPrefix(key), clientv3.WithPrefix())`

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

---

## PIT-FOLLOW-015 · 【仍生效】 Micrometer 的 `serviceLevelObjectives` 是「**追加**」，不是「限定」

> **旧号**：`PITFALL-FOLLOW-015` ⇒ **新号**：`PIT-FOLLOW-015`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— **follow-api 侧仍在同时开两个开关** ⇒ 桶数仍是并集 ⇒ 触发条件仍在 ⇒ 但本轮**只核台账、不改指标配置**（改配置属阶段 2 建设项）⇒ 不影响本轮产出
> **证据**：`service/follow/follow-api/src/main/java/com/rag/follow/follow/api/metrics/FollowApiMetrics.java:129-133` **同时**开 `.publishPercentileHistogram()` 与 `.serviceLevelObjectives(GO_DEF_BUCKETS)` ⇒ 按 Micrometer `DistributionStatisticConfig.getHistogramBuckets()` 是**强制并集** ⇒ 桶边界总数 80（`docs/DRIFT/follow/api.md` 的 `DRIFT-FOLLOW-007` 残留表已记 12 vs 80；**本轮未复测桶数**，未跑 `/actuator/prometheus`） ⚠️ **同仓 follow-rpc 已修** ⇒ 「框架限制不可修」在 rpc 侧**已被证伪**：`service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/metrics/FollowRpcMetrics.java:118-120`（明写「不要再加 `publishPercentileHistogram()`」） ＋ `:134-145`（只用 `serviceLevelObjectives`）⇒ 12 桶

**症状**：为了把 Prometheus 直方图的桶对齐到 Go 的 `prometheus.DefBuckets`（12 个边界），
写下 `.publishPercentileHistogram().serviceLevelObjectives(GO_DEF_BUCKETS)` ——
**结果不是 12 个桶，而是 80 个**。

```
Go  : le 边界 12 个（0.005 0.01 0.025 0.05 0.1 0.25 0.5 1 2.5 5 10 +Inf）
RAG : le 边界 80 个（上面 11 个全在 + Micrometer 默认桶 68 个 + Inf）
```

**根因（`micrometer-core 1.16.7` `DistributionStatisticConfig.getHistogramBuckets(boolean)`）**

```java
NavigableSet<Double> buckets = new TreeSet<>();
if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
    buckets.addAll(PercentileHistogramBuckets.buckets(this));   // ← 默认桶
    buckets.add(minimumExpectedValue);
    buckets.add(maximumExpectedValue);
}
if (serviceLevelObjectives != null) {
    for (double sloBoundary : serviceLevelObjectives) buckets.add(sloBoundary);  // ← SLO 桶
}
return buckets;
```

⇒ 两个来源 **addAll 进同一个 `TreeSet`** ⇒ **强制并集**，
⇒ **没有任何开关**能让 SLO 独占（`percentileHistogram` 只有 true/false 两态：
true ⇒ 并集；false ⇒ **`_bucket` 根本不产生**）。

**教训**

1. ⚠️ **API 名字会骗人**：`serviceLevelObjectives` 读起来像「把桶限定成这些」，
   实际是「**再加这些桶**」。
   ⇒ **凡是「要精确控制某个第三方库产出什么」的场合，必须去读它的源码，
   不能凭 API 名字或文档标题推断**（本仓 §3.3「不能仅凭注解名称断定机制已启用」的同源问题）。
2. ⚠️ **这类问题只有实测能兜住**：改完不看 `_bucket` 的实际行数与 `le` 边界，
   就只能靠「代码看起来对」自我安慰。
   本轮的实测口径：`grep -c '_bucket'`（Go 96 / 修前 RAG 0 / 修后 RAG 640）
   + 抽出两侧的 `le` 集合做**逐值比对**（不能只看数量）。
3. ⚠️ **修之前先确认「修到什么程度算够」**：
   本例里即使绕开 Micrometer，`le="1.0"` vs Go 的 `le="1"`（client_java 的 `Double.toString`）
   **依然修不掉** ⇒ 若目标是「抓取文本逐字节一致」，那么**这条路根本走不通**，
   应该早点把「做不到」这件事说清楚，而不是埋头改桶。
   ⇒ **手段与目标要对齐**：目标若是「`histogram_quantile()` 两侧可比」，
   那数值口径就够了，**不需要**为它付出脱离 Micrometer 抽象的代价。

---

## PIT-FOLLOW-016 · 【仍生效】 对拍**装置**自身会骗人：端口撞车 / 时钟跳变 / 启动时序 / 端口未释放

> **旧号**：`PITFALL-FOLLOW-016` ⇒ **新号**：`PIT-FOLLOW-016`
> **原标题标记**（2026-10-24 台账留痕，保留不改写）：`🔴`
> **状态**：**仍生效** · **核对日期**：2026-10-29 · **是否影响当前轮次**：否 —— 端口撞车 / 时钟跳变 / 启动时序 / 端口未释放是**环境面** ⇒ 永远可能发生 ⇒ 但本轮**不跑双侧对拍**（只核台账）⇒ 不影响本轮产出
> **证据**：`docs/Pitfalls/follow/api.md` §`PIT-FOLLOW-010` 教训 3 的三种假绿形态表（本条为其总纲） ＋ `docs/DRIFT/follow/vs-common.md:59`（A5 轴「`RANDOM_PORT` 面仍是记账缺口」）

**症状一：单测红了，但代码没动**

```
FollowGrpcInterceptorWiringTest » ContainerLaunch
  Container startup failed for image postgres:16-alpine
  → failed to bind host port 0.0.0.0:56583/tcp: address already in use
```

⇒ 镜像在本地、代码没改 ⇒ **重跑一次即过**（实测：第 1 次重试 `EXIT=0`）
⇒ 这是 **Testcontainers 随机端口与宿主已占端口撞车**，**不是代码缺陷**。

**症状二：JWT「突然过期」**

⇒ 本轮 07:13 签的 token，07:19 就 401 了 ⇒ 查 `exp` 发现
**系统时钟从 07:23 跳到了 18:00（≈10.5 小时）**（机器挂起/唤醒或时间同步事件）
⇒ **所有基于时间的判据同时失效**：JWT 的 `exp`、etcd 的租约、
gRPC 连接的 keepalive、Nacos 的实例心跳。

**症状三：「下游找不到」被误判成漂移**

| 场景 | 表面现象 | 真相 |
|---|---|---|
| RAG api 启动**早于** follow-rpc 注册进 Nacos | `1015 service busy` | 通道还没发现到实例 ⇒ **装置时序问题** |
| Go api 启动**早于** etcd 里有 `follow.rpc/` 键 | `produced zero addresses` | 同上（见 PITFALL-FOLLOW-012） |

**症状四：重启后新进程起不来**

```
java.net.BindException: Address already in use
```

⇒ 老进程被 `kill` 了，但 **Nacos 优雅关停 10s + Tomcat 优雅关停**还没结束 ⇒ 端口没释放
⇒ 必须**轮询等端口真正空闲**再启新进程（`lsof -iTCP:<port> -sTCP:LISTEN` 为空）。

**教训**

1. ⚠️ **失败先分三类再动手**：**代码缺陷** / **装置缺陷** / **环境缺陷**（时钟、端口、资源）。
   本轮症状一、二、三、四**全是后两类** —— 只有分清了，才不会去「修」不该修的地方。
2. ⚠️ **装置必须能自证**：每轮对拍**开始前**先单侧打一发，确认响应的**形态**符合预期
   （业务 200？200+1015？401？），不对就**先修装置**、再谈结论。
   ⇒ 这是 PITFALL-FOLLOW-011 的延伸：**装置不对 ⇒ 结论必错**。
3. ⚠️ **长任务要防时钟跳变**：挂起/唤醒后所有时间相关判据作废
   ⇒ 重跑前先 `date` 看一眼；token、租约、窗口、TTL 全部重签/重建。
4. ⚠️ **「进程被杀」≠「端口已释放」** ⇒ 重启脚本里必须有「等端口空闲」的轮询。
5. ⚠️ **测试红了先看是不是 flake**：Testcontainers / 端口 / 资源类报错，
   **重跑一次再判断**；只有「重跑仍红」才当作代码缺陷去查。

---

## 🆕 **2026-10-29 真实性复核回执**（W-B lane · follow 域 · 本文件）

> **口径**：逐条核「被描述的那个行为现在还在不在」（Go 真源 + RAG 两侧）＋「证据指针那一行是不是真的在讲这件事」。
> **原值一律保留**；明细订正已追加在各条正文（见 `PIT-FOLLOW-010`）。
> **机器判据**：`ledger-claim-audit.py --domain follow` ⇒ **🔴 落空 = 0**（本文件原有 1 处真落空 **`/tmp/probe.sh` 的第 3 行** ⇒ 已按 §三·5 处理：**标注「一次性临时探针、不随仓留存 ⇒ 本机不可核验」并改指仓内可复算落点**）。

| 编号 | 状态（本轮核后） | 依据（核对日期 2026-10-29） | 改判 |
|---|---|---|---|
| `PIT-FOLLOW-006` | 已闭环（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/service/FollowApiSupport.java:265` = `out.setUserIds(null);` ＋ `…/FollowApiMvcPipelineTest.java:625-626` = 断言 `"user_ids":null` ⇒ 触发条件（proto3 空 repeated 在 wire 上消失）已消除 | 否 |
| `PIT-FOLLOW-007` | 已闭环（不变） | 同上：断言已从 `"user_ids":[]` 改成 `"user_ids":null`（`:625-626`）⇒ **「锁死错误答案」的断言已改** | 否 |
| `PIT-FOLLOW-009` | 已闭环（不变） | RAG `…/follow-api/…/dto/RecommendUser.java:57` = `@JsonPropertyOrder({"target_id", "mutual_score"})` ⇒ Jackson 的字母序分支已被 `JsonPropertyOrder` 固定 | 否 |
| `PIT-FOLLOW-008` | 已闭环（不变） | RAG `service/follow/follow-api/pom.xml:196` = `spring-boot-maven-plugin` ⇒ 产物为可执行 fat jar | 否 |
| `PIT-FOLLOW-010` | 已闭环（不变） | 仓内可复算落点逐行核过：`tools/api-full-parity-probe/follow/run.sh:529` = `out="$(mktemp "${WORK}/body.XXXXXX")" \|\| return 1` ＋ `:579-581` = `http_code == 000` 守卫 ＋ `tools/api-full-parity-probe/follow/mutate-parity.sh:161-173` = **M5** 把这条假绿复现出来 ⇒ 触发条件已消除 | 否（**指针形态已改**：原 **`/tmp/probe.sh` 的第 3 行** ⇒ 标注不可核验并改指仓内落点） |
| `PIT-FOLLOW-011` | 仍生效（不变） | 探针形态属**一次性外部装置** ⇒ 本轮未复跑；**触发条件（故障模拟器不完成 HTTP/2 握手）在本仓探针里仍在** ⇒ 维持「仍生效」 | 否 |
| `PIT-FOLLOW-012` | 仍生效（不变） | 属 **go-zero 库行为**（etcd 键 = 前缀 + `/`）⇒ 本轮未复跑 Go 探针；库版本（`go-zero v1.10.2`）未变 ⇒ 维持「仍生效」 | 否 |
| `PIT-FOLLOW-015` | 仍生效（不变 · **适用范围已写明**） | RAG `…/follow-api/…/metrics/FollowApiMetrics.java:131` = `.publishPercentileHistogram()` ＋ `:132` = `.serviceLevelObjectives(GO_DEF_BUCKETS)` ⇒ **api 侧两个开关同开 ⇒ 并集仍在（80 桶）** ⇒ 触发条件在；⚠️ 已在条目内写明「**follow-rpc 只开 SLO ⇒ 12 桶 ⇒ 该条不适用于单开 SLO 的形态**」 | 否 |
| `PIT-FOLLOW-016` | 仍生效（不变） | 属**方法论**（对拍装置自身会骗人）⇒ 本轮未复跑；同类形态在本域探针里仍需逐条防（见 `PIT-FOLLOW-010` 的 M5 变异） | 否 |

### 仍未核到（**待核实**）

| 编号 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `PIT-FOLLOW-011` / `-012` / `-016` 的**触发条件复现** | 需外部装置 / Go 侧进程 / 完整对拍 ⇒ 本轮**未复跑** | 只核了台账与仓内证据面（引用文件零缺失）⇒ 状态**沿用 2026-10-29 对齐 lane 的结论**，未独立改判 |
