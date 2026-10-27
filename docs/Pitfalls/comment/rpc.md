<!-- @new 2026-10-24 · comment 域 rpc 组件踩坑档 -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# 踩坑档 · `service/comment/comment-rpc`

> 归类：**「为什么会踩」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「哪里不一致」，本文记「为什么会踩成这样」。**
>
> ⚠️ **三档之间不要互相搬运**：一条问题只在**一个**档里落笔，在另一档里**链接**过去。

---

## PIT-COMMENT-RPC-01 🔴 · Spring 的回滚规则会**吃掉 Go 的显式 `tx.Commit()`**

### 一句话

**Go 用「手动事务」表达「这个失败状态必须落盘」，Java 用 `@Transactional` 表达「失败就回滚」——
1:1 直译会把「必须落盘」变成「绝不落盘」，而且编译器与测试都不会报错。**

### 具体形态

Go 侧的事实投递有一个反复出现的形态：**「记一个失败状态，然后提交事务，最后返回错误」**。

| Go 位置 | 形态 |
|---|---|
| `internal/model/fact_delivery.go:79-89` | 置 `delivery_status = DeliveryBlocked` → **`tx.Commit()`** → 返回 `ErrFrozenEnvelopeBlocked` |
| `internal/model/fact_delivery.go:99-110` | 置 `delivery_status = DeliveryFailed` + `retry_count + 1` → **`tx.Commit()`** → 返回发送错误 |

⇒ **Go 的语义是「状态落盘 + 向上报错」两件事同时成立。**

Java 侧的对应实现（`CommentFactDeliveryService.java:70` 起）用了 `@Transactional`，
然后在同一个事务里「UPDATE 状态」+「抛异常」：

```java
@Transactional                       // ← 默认：RuntimeException ⇒ 回滚
public void dispatchOnce(...) {
    ...
    outboxMapper.markBlocked(...);  // ← 这个 UPDATE 会被回滚
    throw Wire.ERR_FROZEN_ENVELOPE_BLOCKED;
}
```

⇒ **UPDATE 被回滚 ⇒ 状态从未落盘。**

### 为什么「看起来」像对的

- `@Transactional` 在 Spring 里是**声明式**的：抛异常 ⇒ 回滚，是**最符合直觉**的默认行为。
- 写这段代码时，脑子里想的是「Go 那边是提交了的」—— 但**注解只表达了一个意图**，不表达 Go 的那个**两段式**语义。
- **没有任何报错**：编译绿、上下文能起、单测也能绿（因为单测往往不真跑事务）。

### 怎么识别（可复用的判据）

> **凡 Go 侧出现「`tx.Commit()` 之后 return error」这个形态，Java 侧就不能用 `@Transactional` 表达。**

Go 侧的这个形态在本仓有**明确同款先例**：
`favorite-rpc` 的 `FavoriteFactDispatchService` 有 **3 处显式 `tx.Commit()` 之后返回错误**
⇒ 那里的修法是**用 `TransactionTemplate` 显式 commit/rollback，不用 `@Transactional`**
（理由原文见 `docs/DRIFT/favorite/rpc.md` §1「事务语义的一处刻意偏离（机制层，非行为层）」）。

⇒ **comment-rpc 应当照搬 favorite-rpc 的既有写法**，而不是自己发明第三种。

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) 的 **MISMATCH-01 / MISMATCH-02**。

---

## PIT-COMMENT-RPC-02 🔴 · javadoc 里「对应 Go xxx.go:行号」的断言**本身可能与真源相反**

### 一句话

**注释写得越详细，越容易让人跳过核对；而最危险的一类注释是「我查过了，Go 就是这样」—— 它可能把事实说反了，
而读者（与后续 agent）会当成既成事实直接继承。**

### 具体形态（本轮实证）

RAG 侧 `comment-api/.../service/CommentApiSupport.java:59-64` 写：

> 「**为什么 Java 侧看不到 `json.Number` 分支**：golang-jwt/v4 用 `encoding/json` 把 claims 解进 `MapClaims`，
> 数字字面量默认解成 **`float64`** —— `json.Number` 只在调用方显式用了 `Decoder.UseNumber()` 时才出现，
> **golang-jwt 没有这么做**。」

**实测真源**（go-zero v1.10.2，`/Users/pr/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）：

```
rest/token/tokenparser.go:123:	return jwt.NewParser(jwt.WithJSONNumber())
```

⇒ **go-zero 恰恰用了 `WithJSONNumber()`** ⇒ Go 侧的数值 claim **就是 `json.Number`**。
⇒ Go 的 `helper.go:17-18` `case json.Number: return value.Int64()` **是真路径**（不是死代码），
而 `case float64` 才是死代码。
⇒ `json.Number.Int64()` = `strconv.ParseInt` ⇒ **非整数 / 越界值都报错 ⇒ 1006**；
RAG 侧却**截断 / 回绕**（`CommentApiSupport.java:82-93`）⇒ 静默产出错误身份。

⇒ **RAG 侧的注释把事实说反了，并据此把一个真分支当死代码删掉了。**

### 为什么这条特别危险

1. AGENTS.md §3.3.2 已经点名：「以『Go: …』形式写在 `@DisplayName` / javadoc 里的断言，本身是**高危项**」。
2. 本轮**不是**遇到「注释没写出处」这种低级问题 —— 恰恰相反，注释**极其详尽、还标了行号**。
3. **详尽的错误断言比没有注释更危险**：没有注释时人会去查；有了「已核对」的注释，人会直接继承。

### 可复用的核对动作（本轮实际用上的）

> **凡 javadoc 断言了「某个 Go 依赖的内部行为」，必须去那个依赖的源码里读一遍 ——
> go-zero / golang-jwt 这类第三方库的源码在 `~/go/pkg/mod/` 下，是可读的。**

```bash
grep -E "zeromicro/go-zero" go.mod                                    # 拿精确版本
grep -rn "WithJSONNumber\|jwt.NewParser" \
  ~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2/rest/            # 读消费点
```

⇒ 同样的手法验了另外两条：`zrpc/config.go:31`（`Timeout` 默认 2000ms）、
`zrpc/internal/config.go:14`（`Breaker` 默认 true）—— **两条都推翻了 RAG 侧注释里的假设**。

### 对应本轮的漂移

[`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) 的 **MISMATCH-API-02**。

---

## PIT-COMMENT-RPC-03 · 「类级注解覆盖了框架级配置」—— 严格性是被自己关掉的

### 一句话

**一个类上的 `@JsonIgnoreProperties(ignoreUnknown = true)` 会覆盖掉 mapper 上的
`FAIL_ON_UNKNOWN_PROPERTIES=true` —— 而两者都是「宽松」配置，方向一致时**互相掩护**，
直到某天需要严格性时才发现它早就不在了。**

### 具体形态

RAG 侧有**两个** ObjectMapper：

| mapper | 配置 | 用途 |
|---|---|---|
| `Wire.MAPPER` | `FAIL_ON_UNKNOWN_PROPERTIES = true`（`Wire.java:115-116`） | 事实信封与载荷的**严格解码**（对齐 Go 的 `StrictDecode`） |
| 共享注入的 `ObjectMapper` | 宽松（`ignoreUnknown = true`） | Kafka 审计消息 |

`CommentDeliveryValidator.java:38-42` 注入的是**共享的宽松 mapper**，
而 `CommentDomainFactService.java:220` 又在 `CommentFact` 上挂了
`@JsonIgnoreProperties(ignoreUnknown = true)` ⇒ **类级注解把 mapper 级的严格性关掉了**。

🔴 **讽刺点**：`CommentDeliveryValidator.java:30-36` 的 javadoc 恰好写着
「用错 mapper 会让『载荷里多一个字段』这种篡改悄悄通过校验」——
**而它自己正是那个「用错 mapper」的现场**。

### 可复用的判据

> **凡「严格性」是某个类的关键属性，就不要同时存在两个来源（mapper 级配置 + 类级注解）能把它覆盖掉。
> 严格性只能有一个来源，且必须写在「最靠近消费点」的地方。**

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) 的 **MISMATCH-04 / MISMATCH-05 / MISMATCH-06**。

---

## PIT-COMMENT-RPC-04 · 「Go 故意留零 / Go 故意不填」是**契约**，不是待修的 bug

### 一句话

**Java 侧看到 proto 里有 `owner_id` / `subject_count` / `like_count` 而 Go 没填，
很容易「顺手补上」—— 那是新增可观察行为，且在某些场景是安全问题。**

### 本域的三处（全部**刻意保留**，不得修）

| 字段 | Go 的事实 | 为什么不能补 |
|---|---|---|
| `GetCommentResp.subject.owner_id` | `get_comment_logic.go:152-159` 逐字段赋值，**没有** `owner_id`（proto 里却有 `owner_id=7`） | 补上 = 把 **up 主 ID 暴露给所有读者** |
| `CreateCommentResp.subject_count` | `create_comment_logic.go:139` 恒 `0` | 落库发生在 Kafka 消费侧，此刻**没有**任何计数可读；补真实值是**新增** |
| `LikeCommentResp.like_count` | `like_comment_logic.go:94-96` 只设 `Success: true` | 同上 |

⇒ 本域 RAG 侧**三处都正确地留了零/不填**（`CommentDomainService.java` 类级 javadoc 有集中说明）✅
⇒ 这条记下来，是为了**下一个人**别去「修」它。

### 同类还有几处「反直觉但必须照搬」

| 判定点 | Go 的事实 |
|---|---|
| `GetComment` 的 `sort_type == 0` ⇒ **热度序**（不是时间序） | `get_comment_logic.go:78-81` |
| `GetComment` 的 `OnlyNormal` **恒 false** ⇒ 已删 / 审核中的评论**也在列表里** | `:98` |
| `GetComment` 的 index / content **按下标配对** ⇒ 缺 content 行会**串位** | `:122-127` |
| `LikeComment` 的 DB 兜底**发 2001 而不是 5004**（日志记 5004、线上发 2001） | `like_comment_logic.go:61-64` |
| `ManageComment` **完全没有鉴权** | `manage_comment_logic.go:59-86` |
| `UpdateSubjectState` 的越权文案是「**非up主无法评论**」（与「改评论区状态」无关） | `update_subject_state_logic.go:88` |
| `resolveOwnerID` 回落失败时返回**原始 err**（⇒ 1012 而非 5003） | `create_comment_logic.go:152-155` |

---

## PIT-COMMENT-RPC-05 🔴 · 「因错误原因而绿」的假守卫 —— 断言对了，路径错了

### 一句话

**一条守门网可以断言出正确的结果，却是因为一条与被守行为无关的路径 ⇒ 变异测试一做就露馅。
只看「测试是不是绿的」永远发现不了。**

### 本轮实证（MISMATCH-06 的守门网第一版）

要守的是「Go 的 `encoding/json` 对类型不匹配报错 ⇒ 载荷被数字化 ⇒ 必须 BLOCKED」。

第一版守门网挑的字段是**载荷的 `schema_version`**（Go 侧是 `string`）：

```java
String payload = payloadJson(...).replace("\"schema_version\":\"rtw.community-fact.v1\"",
                                          "\"schema_version\":1");
assertThatThrownBy(...).isInstanceOf(Wire.FrozenEnvelopeBlockedException.class);
```

🔴 **这条断言恒绿 —— 但不是因为强转禁令生效**：

| 步骤 | 删掉强转禁令后（= 修复前的状态） | 结果 |
|---|---|---|
| 宽松解码 `1` → `String` | 得 `"1"` | 解码**成功** |
| `payload.schemaVersion != "rtw.community-fact.v1"` | `"1" != "rtw.community-fact.v1"` | ⇒ **判非法** |
| 最终 | **BLOCKED** | **断言照样通过** |

⇒ 它守的是「**版本号不匹配**」，不是「**类型不合法**」。把强转禁令整段删掉，测试**照样绿**。

### 怎么改才守得住

换成一个「数字化之后仍然合法」的字段：**载荷的 `target_type`**（Go 侧是 `string`）。

| 步骤 | 删掉强转禁令后 | 结果 |
|---|---|---|
| 宽松解码 `5` → `String` | 得 `"5"` | 解码**成功** |
| `isBlank(payload.targetType)` | `"5"` 非空 | **通过** |
| 全部交叉校验 | 通过 | ⇒ **这条事实会被照常投递** |

⇒ 有强转禁令：解码失败 ⇒ BLOCKED ⇒ 断言通过；
无强转禁令：事实被**投递** ⇒ `mustNotBeCalled()` 的 sender 抛 `AssertionError` ⇒ **测试变红**。

### 可复用的判据

> **一条守门网要能区分「被守的行为」与「别的行为」，必须先问：
> 把被守行为拿掉之后，它**是不是仍然会绿**？**
> 如果仍然绿 ⇒ 它守的不是那件事。
>
> **判据的落地方式就是变异测试**（AGENTS.md §3.3：「绿测试可能是错误答案的守卫」）——
> **变异测试不是「额外加一道保险」，它是「守门网本身是否守得住」的唯一检验手段。**

⚠️ 附带一条元教训：**变异脚本自己也会造假绿**。
本轮第一版脚本把 `restore` 写在了「跑测试」的函数里 ⇒ 同组第二条测试实际跑在**未变异的基线**上
⇒ 两条「守门网失效」是**脚本的 bug**，不是代码的问题。
⇒ **变异脚本必须保证「每条变异只影响它自己那一条断言」**（跑完立刻还原，且还原动作不能和跑测试耦合在一起）。

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) 的 **MISMATCH-06**。

---

## PIT-COMMENT-RPC-06 🔴 · 「编程式事务」的守门网必须让 mapper **真的**参与那个事务

### 一句话

**用 `TransactionTemplate` 换掉 `@Transactional` 之后，测试里如果 mapper 走的是**另一条连接**，
那么「事务提交了没有」这件事在测试里根本不成立 —— 守门网会恒绿。**

### 形态

生产（Spring Boot + MyBatis-Plus）里 mapper 与服务共享**同一条连接**，靠的是
`DataSourceTransactionManager` 绑连接 + `SqlSessionTemplate` 取会话。

测试里手搭夹具时，**两条路都长得像，但只有一条是对的**：

| 写法 | 结果 |
|---|---|
| `sqlSessionFactory.openSession()` | 每次 mapper 调用**自己开一条连接**、自己提交 ⇒ **不在 Spring 事务里** ⇒ 断言「回滚后状态没落盘」**恒不成立** |
| `new SqlSessionTemplate(factory)` + `template.getMapper(X.class)` | 会话被 `SqlSessionUtils` 绑到当前事务 ⇒ **与生产同构** ✅ |

⇒ 关键三件套：`SpringManagedTransactionFactory`（`Environment` 里）+ `SqlSessionTemplate` + 同一个 `DataSource` 实例。

### 可复用的判据

> **断言「事务语义」的测试，必须能证明「被断言的那条 SQL 与被断言的那个事务在同一条连接上」。
> 最好的证明是：让测试真的回滚一次，看状态有没有变。**

### 附带：本域的两处版本陷阱

| 陷阱 | 真相 |
|---|---|
| `mybatis-spring` 4.x 把 `SqlSessionTemplate` 从 `org.springframework.mybatis.spring` 挪到了 **`org.mybatis.spring`**；`SpringManagedTransactionFactory` 从 `org.apache.ibatis.transaction.managed` 挪到了 **`org.mybatis.spring.transaction`** | mybatis-spring **4.0.0**（由 `mybatis-plus-spring-boot3-starter:3.5.17` 引入） |
| Jackson 2 的 `LogicalType` 常量是 **`Textual`**，**没有 `Text`** | 抄 `Jackson2ObjectMapperConfiguration` 时极易写成 `LogicalType.Text` ⇒ 编译不过（这类错误是**好的**错误） |

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) 的 **MISMATCH-01 / MISMATCH-02**。

---

## PIT-COMMENT-RPC-07 🔴 · 对拍 harness 必须**等异步落库**，否则会把**时序**当成**语义差异**

### 一句话

**两侧的「写」都是异步链路（推 Kafka → 消费端落库）时，请求返回 ≠ 已落库；
harness 若不等，就会在同一轮里把「还没落库」读成「RAG 语义不对」。**

### 形态（本轮实测）

`comment` 域的 `CreateComment` **不写库**：Go 侧 `create_comment_logic.go:102-132` 只推 Kafka，
落库在 `mqs/audit_consumer.go`；RAG 侧 `CommentDomainService.java:177-199` 同样只推 Kafka，
落库在 `CommentAuditListener`。
⇒ **架构是同构的**，但**落库时刻取决于消费者什么时候拿到分区**。

本轮第一轮对拍跑出 **24 条差异**，其中 **20 条是同一根因**：

```
T03-list-after-create  go=1  rag=0     ← RAG 侧库里当时还没有这一行
T04-like               go=200 rag=1013 ← 「评论不存在」
T12-delete             go=200 rag=1013
```

⇒ 而矩阵跑完 1 分钟后去查库，**RAG 的 `comment_index` 有 1 行** ⇒ **行是有的，只是当时还没提交**。

### 怎么识别（可复用的判据）

> **判据：差异出现时，先去查「被读的那张表现在有没有这一行」。**
> **有 ⇒ 是时序问题（harness 缺等待）；没有 ⇒ 才是语义问题。**

配套判据：**固定 `sleep(3)` 不可靠**。本轮实测 RAG 侧迟 **19s**（见 PIT-08）⇒
必须**轮询到期望状态为止**（本域的 harness 用「轮询 list 直到评论可见 / 消失」，上限 40s / 15s）。

### 对应本轮的漂移

无（**不是**漂移，是 harness 缺陷）。但它**直接决定了对拍结论的可信度** ——
第一轮 24 条差异里有 20 条是假的，第二轮才是 6 条。

---

## PIT-COMMENT-RPC-08 🔴 · Kafka topic **不预建** ⇒ 消费者组 rebalance 退避 ⇒ 迟到 19 秒

### 一句话

**消费者订阅了一个「还不存在」的 topic 时，Kafka 4.x 的 rebalance 会走指数退避；
harness 若在启动前把 topic 删掉，RAG 侧的消费者组要十几秒才能拿到分区 ⇒ 落库迟到 ⇒ 又变成假差异。**

### 形态（本轮实测时间线）

| 时刻 | 事件 |
|---|---|
| `18:22:52` | harness 删掉两个 topic |
| `18:23:00` | RAG 消费者 `Subscribed to topic(s): comment-audit-topic-rag`（**此时 topic 不存在**） |
| `18:23:15` | 矩阵开始，生产者首次发消息 ⇒ **topic 被自动创建** |
| `18:23:34.182` | **`Successfully joined group with generation Generation{generationId=2}`** ← **迟了 19 秒** |
| `18:23:34.379` | `comment pipeline finished` ⇒ 消息这才被消费 |

⇒ 消费者**早就订阅好了**，只是**19 秒后**才拿到 `comment-audit-topic-rag-0` 的分区。

### 为什么

Kafka 4.x 的组协调在 JoinGroup 阶段拿不到 topic 元数据 ⇒ **指数退避重试**
（`group.initial.rebalance.delay.ms` 默认 3000 起，上限 `group.rebalance.backoff.max.ms` 默认 30000）。
⇒ topic 缺失 13 秒 ⇒ 退避累加到十几秒。

⚠️ **Go 侧不受影响**：kafka-go 的 `Reader` 不走组协调 ⇒ 同样的条件下**毫秒级**
（本轮 Go 侧 `comment pipeline finished` 与请求**同毫秒**）。

### 怎么识别（可复用的判据）

> **判据：RAG 侧「明明消息已经发出去了，消费者却迟迟不动」⇒ 先查消费者有没有 join 成功、
> 什么时候 join 成功的。日志里 `Successfully joined group` 的时间戳就是答案。**

修法：harness 改成 **「先删后建」**（`kafka-topics.sh --create --partitions 1`）⇒
消费者订阅时 topic **已存在** ⇒ 立刻 join。

### ⚠️ 产品层面的残留

这虽是 harness 缺陷，但**暴露了一个真实的产品差异**：
**Kafka 客户端实现不同 ⇒ 同样条件下 RAG 的审计管道延迟远高于 Go**。
本轮实测 19s vs ~0ms。⇒ 值得在生产容量评估时记一笔（本轮未量化，也未改）。

---

## PIT-COMMENT-RPC-09 🔴 · Boot 4 的三连坑：**编译通过 ≠ 能启动**，且**报错点离病根很远**

### 一句话

**起真源对拍才暴露的三个启动阻塞，每个的报错信息都指向别的地方；
「`mvn package` 通过」和「单测全绿」都测不出来。**

### 三个坑（本轮实测）

| # | 现象（报错） | 真病根 | 判据 |
|---|---|---|---|
| **P-1** | `APPLICATION FAILED TO START: Unable to start web server` | 本模块 pom 里的 `jakarta.servlet:jakarta.servlet-api`（provided）被 Spring Boot repackage 打进 `BOOT-INF/lib` ⇒ `WebApplicationType.deduceFromClasspath()` 判成 **SERVLET** ⇒ 找不到 `ServletWebServerFactory`。**报错在说「web server 起不来」，真病根是「它以为自己该是个 web 应用」** | 检查 `BOOT-INF/lib` 里有没有 servlet-api；rpc 模块加 `spring.main.web-application-type: none` |
| **P-2** | `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` | `MybatisPlusAutoConfiguration`（3.5.17）的 `@AutoConfigureAfter` 指向 **Boot 3** 的 `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`；**Boot 4 挪到了 `org.springframework.boot.jdbc.autoconfigure...`** ⇒ **排序引用悬空** ⇒ MP 在 DataSource 之前求值 ⇒ `@ConditionalOnSingleCandidate(DataSource)` 失败。**报错在说「没有 SqlSessionFactory」，真病根是「自动配置的顺序断了」** | 看启动日志里那句 `Failed to introspect meta-annotation @AutoConfigureAfter on class MybatisPlusAutoConfiguration: TypeNotPresentException` |
| **P-3** | `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` | ⚠️ **是本仓自己引入的**：把 `grpc-inprocess` 声明成 `<scope>test</scope>`，按 Maven **「最近者优先」**规则**顶掉**了 devh 传递进来的 **compile 作用域**那份 ⇒ 运行期缺失。**单测完全正常**（test 作用域那份还在） | 改回「不声明，让传递依赖说话」 |

### 可复用的判据

> **判据 1：依赖缺失类问题，看「最终运行时的 classpath」，不是看 pom。**
> **判据 2：`NoClassDefFoundError` 优先怀疑「作用域」而不是「依赖没引」。**
> **判据 3：Boot 4 下看到 `@AutoConfigureAfter` 的 `TypeNotPresentException` ⇒ 排序桥没了，要补桥。**

### 附带：**同仓 8 个用 mybatis-plus 的 rpc 模块里，只有 `comment-rpc` 漏了桥**

⇒ 「照抄同仓已正确的那个模块」比「自己发明一个」可靠。

### 对应本轮的漂移

无（**不是**漂移，是启动阻塞）。已在
[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) §4.6.2 记录。

---

## PIT-COMMENT-RPC-10 🔴 · GORM 的 `default:` 标签会**吃掉** `autoCreateTime` ⇒ 看 struct 定义会得出**相反**的结论

### 一句话

**「字段名叫 `CreatedAt`」会让 GORM 的命名约定打开 `autoCreateTime`，
但 `default:` 标签又让 INSERT **跳过**这一列 —— 两个规则互相覆盖，
只贴 struct 定义行必然得出与活体相反的结论。**

### 形态（本轮实测）

```go
// rpc/internal/model/domain_fact.go:29
CreatedAt time.Time `gorm:"column:created_at;not null;default:CURRENT_TIMESTAMP"`
```

**看起来** ⇒ GORM 命名约定（`schema/field.go:294-295`：`field.Name == "CreatedAt"` ⇒ `AutoCreateTime`）
⇒ INSERT 会**显式带上** `created_at` = Go 进程当时。

**实际上** ⇒ `callbacks/create.go:268-270`：

```go
if field := stmt.Schema.FieldsByDBName[db]; !field.HasDefaultValue || field.DefaultValueInterface != nil {
    ... 把该列加进 INSERT ...
}
```

`HasDefaultValue = true`（`schema/field.go:210-213` 解析 `DEFAULT` tag 时置位）
且 `DefaultValueInterface == nil`（`schema/field.go:277-281` 试图 `now.Parse("CURRENT_TIMESTAMP")` **失败** ⇒ 不赋值）
⇒ **整个条件为 false** ⇒ **`created_at` 根本不进 INSERT 列清单** ⇒ **DB 默认生效**。

### 活体证据（不靠推理）

Go 的 `DeleteCommentTx` 里 `comment_index` 的 `state=2` UPDATE（`comment.go:288`，推进 `updated_at`）
**先于** `appendCommentFact` 的 outbox INSERT（`comment.go:338`），两者在**同一个事务**里
⇒ 若 `created_at` 是 GORM 在 INSERT 时刻填的，它必须 **≥** `comment_index.updated_at`。

实测：`outbox.created_at = 10:39:06.06046` **<** `comment_index.updated_at = 10:39:06.089807`
⇒ **`created_at` 来自 DB 默认（事务开始时刻）**，不是 GORM 填的。✅

### 后果

⇒ 原先登记的漂移 D1（「RAG 走 DDL 默认 = 事务开始时刻，Go 走进程当时 ⇒ 值不同」）是**误判** ⇒ **撤销**，RAG 代码不动。

### 可复用的判据

> **判据：看到 struct tag 里有 `default:`，就不要假设 GORM 会替你填值 —— 必须读到 `callbacks/create.go` 那个消费点。**
> 这正是 AGENTS.md §3.3.1 的「必须读到**消费该 tag 的那段控制流**，只贴 struct 定义行不算证据」。

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) §3.1（D1 撤销）。

---

## PIT-COMMENT-RPC-11 🔴 · **已应用的 Flyway 迁移文件连注释都不能改** —— 签字记录写进去 = 校验失败

### 一句话

**「给已应用的迁移文件加一行签字说明」看起来是无害的注释，
但 Flyway 的 checksum 是对文件**全文**算的 ⇒ 改一个字符 ⇒ 已落库的账本立刻对不上 ⇒ **组件起不来。**

### 本轮实测（本仓自己踩的）

`433dbbd` 那次签字 commit 里，给 `V5__create_comment_report_record.sql` 追加了 5 行签字说明：

```
-- ✅ **2026-10-24 用户已签字（Jason）**：**接受本偏离，保留本脚本**。
```

⇒ checksum 从 **733247998** 变成 **1073033367** ⇒ 与 `flyway_schema_history_comment` 里已应用的 **733247998** 不符。

### 🟢 活体双向实验（**正反两面都跑了**，2026-10-24）

| 实验 | 操作 | 结果 |
|---|---|---|
| **正向** | 用**恢复原字节**的 V5（checksum **733247998**）启动 `comment-rpc`，连**已有账本**的 `rag_comment` 库 | ✅ `Successfully validated 8 migrations` → `Schema "public" is up to date. No migration necessary.` ⇒ **组件正常启动** |
| **反向** | 把账本里 V5 的 checksum 改成 **1073033367**（= `433dbbd` 那版文件的值）再启动 | 🔴 **`Validate failed: Migrations have failed validation` / `Migration checksum mismatch for migration version 5`** ⇒ **组件起不来**（随后已把账本改回 733247998） |

⇒ **结论**：`433dbbd` 那笔 commit **一旦合入，任何在它之前应用过 V5 的环境都会在启动时直接失败**。
⇒ 修复动作 = **把 V5 恢复成原始字节并提交**（签字记录改放 Java javadoc + 台账）。

### 校验 checksum 的正确姿势（Flyway 的算法不是「文件 CRC32」）

Flyway 的 `ChecksumCalculator` 是**逐行**累加 CRC32，**不含行尾符** ⇒
和「整个文件的 CRC32」**不是一回事**。本轮用下面这段脚本对 V1~V8 **8/8 全部与库中一致**（用它反证算法正确）：

```python
import zlib
def flyway_crc(text: str) -> int:
    crc = 0
    for line in text.split('\n'):          # Java readLine() 的语义：不含行尾符
        crc = zlib.crc32(line.encode('utf-8'), crc)
    return zlib.crc32(b'', crc) & 0xffffffff
def to_int(v):                            # PG 存的是有符号 int32
    return v - 4294967296 if v > 2147483647 else v
```

> ⚠️ **判据**：校验 checksum 前，**先拿这个算法把「当前所有迁移文件」跑一遍，与库里的值对一遍** ——
> 算法对了再谈哪条对不上，否则容易把「算法不对」误判成「文件被改过」。

### 正确做法

| 想记的东西 | ❌ 不能放哪 | ✅ 该放哪 |
|---|---|---|
| 签字偏离的结论 | ❌ **已应用的 `.sql` 迁移文件**（连注释都不行） | ✅ **Java javadoc**（如 `ReportRecordMapper.java`）+ **台账** |
| 迁移脚本的语义说明 | ❌ 已应用文件 | ✅ **新写的迁移文件**（或台账） |

⚠️ **Flyway 只增不改** ⇒ 想撤销一条已应用的迁移，正确做法是**新写 V9 DROP TABLE**，**不是**改 V5。

### 对应本轮的漂移

无（**不是**漂移，是**流程坑**）。已在 [`../../Intentional_Deviations/comment/rpc.md`](../../Intentional_Deviations/comment/rpc.md) 的
`SD-2026-10-24-CAND-02` 记签字结论，签字说明落在 `ReportRecordMapper.java` 的 javadoc。

---

## PIT-COMMENT-RPC-12 🔴 · 基础设施挂掉时，**对拍报告会把故障当成语义差异**

### 一句话

**Kafka 容器被 OOM kill（exit 137）⇒ 两侧审计管道全断 ⇒ 报告里全是「差异」——
而那些差异没有一个是 comment 域的语义问题。**

### 本轮实测

容器 `cdifftest-kafka` 被 OOM kill（`docker ps` 显示 `Exited (137)`）后那一轮对拍产出：

| 现象 | 真实原因 |
|---|---|
| 「create 返回 200 但库里没有」 | 消费者没起来 ⇒ 审计消息无人消费 ⇒ 永不落库 |
| 「RAG 侧 2s deadline exceeded」 | Kafka 客户端在重连 ⇒ 拖慢了整条链路 |

⇒ **两个都不是 comment 域的漂移**，但报告会把它们**当成差异列出来**（还带着「RAG 比 Go 慢」的结论倾向）。

### 修法：**前置健康检查，不健康就中止**

`tools/difftest/comment/run_diff.sh` 现在在跑矩阵**之前**做三件事，任一不过 ⇒ **`exit 4` 中止**（而不是跑出一堆假差异）：

1. 三个容器 `cdifftest-postgres` / `cdifftest-redis` / `cdifftest-kafka` 的状态必须是 `running`；
2. PG 可达（`psql -c "select 1"`）、Redis 可达（`PING` ⇒ `PONG`）；
3. Kafka 里 `comment-audit-topic*` **数量 ≥ 2**。

### 可复用的判据

> **判据：任何「双侧对拍 / 集成测试」在跑之前，先验基础设施；
> 「跑出一堆差异」的第一反应应该是「我的环境是不是挂了」，而不是「对方写错了」。**
>
> **判据 2：docker 容器 `Exited (137)` = OOM kill**，不是「正常退出」⇒ `docker start` 恢复即可，但根因（内存上限）要单独处理。

### 对应本轮的漂移

无（**不是**漂移，是 harness 坑）。已在 [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) §4.6.5 记录。

---

## PIT-COMMENT-RPC-13 🔴 · 「某域把异常改写了」这类登记，**必须逐条核 Go 侧** —— 否则会让**本来正确的代码去改本来正确的代码**

### 一句话

**看到 RAG 侧有一堆 `catch → 改写成业务码`，不等于 RAG 写错了 ——
必须回到 Go 侧看它自己的 catch-all 有没有同样改写；Go 也改写 ⇒ 两侧一致 ⇒ 不是漂移。**

### 形态（2026-10-26 实测）

global 域第三十三轮登记 **E47**：「业务层把『SQL 被 deadline 取消』改写/吞掉」⇒ 记成
「RAG 把 DEADLINE_EXCEEDED 改写/吞掉 ⇒ 24 处硬改写 + 3 处静默降级」
⇒ **数目对了，定性方向反了**：它**只看了 RAG 侧的 catch 形态，没核 Go 侧**。

第三十六轮逐条核到 Go 行号后 ⇒ **改写型域（comment / like / follow / article）的 Go 侧同样改写**
⇒ 那 21 处**与 Go 一致 ⇒ 销案**；真正的漂移在别处（`user-rpc`/`admin-rpc` 是**反向**：Go 改写成
`codes.Internal`，RAG 却原样重抛 DEADLINE_EXCEEDED ⇒ **RAG 这一侧更准确**）。

### 本域的具体形态（comment 域 10 处）

Go 侧**每一条 DB 错误路径都改写成业务码**，**不区分 deadline**：

| Go 位置 | 改写成的业务码 |
|---|---|
| `report_comment_logic.go:79-92` | `errmsg.NewGrpcErr(errmsg.ErrorDbInsert, "DB插入失败")` |
| `delete_comment_logic.go:76-77` | `errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")` |
| `update_subject_state_logic.go:79-80` | `errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")` |
| `like_comment_logic.go:62-63` | `errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "DB更新失败")` |

⇒ RAG 侧对应 10 处（`CommentDomainService.java:161/434/447/474/523/541/590/651/690/710`）
**全部是** `catch (RuntimeException e) { result = "sys_fail"; … throw CommentExceptions.dbXxx(); }`
⇒ **与 Go 的 catch-all 逐字同款** ⇒ ✅ **一致，不是漂移 ⇒ 销案 ⇒ 代码零改动**。

### 可复用的判据

> **判据 1：登记「某域把异常改写了」之前，先去 Go 侧看它自己的 catch-all 是不是也改写。**
> 「RAG 有 N 处 catch」**不是**漂移的证据 —— 要看**两侧同一条路径**的**最终可观察输出**。
>
> **判据 2：catch 的「宽窄」不是判据，「改写后的码」才是。**
> Go 的 `if err != nil { NewGrpcErr(ErrorDbSelect, …) }` 与 RAG 的
> `catch (RuntimeException e) { throw CommentExceptions.dbSelect…(); }` **宽窄不同、行为同款** ⇒ 一致。
>
> **判据 3：全仓现在「两种口径各占一半」** —— comment/like/follow/article 与 Go 一致（改写）·
> user/admin/message 保留 DEADLINE_EXCEEDED ⇒ ⇒ **签字与修复必须按域逐条看，不能一刀切。**

### 对应本轮的漂移

无（本域**销案**）。已在 [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) §4.6.6 记为
**只读指针**（归属 global 的 **E47**）⇒ **本条是「差点改错」的记录，不是缺陷**。

---

## PIT-COMMENT-RPC-14 🔴 · 「Go 侧某文件里零命中」**推不出「整条链路不用 X」** —— 链路的另一半在**共享层**

### 一句话

**在 Go 侧某个**域内文件**里 grep「零命中」，只能证明那个文件没有；
证明不了**整条链路**没有 —— 链路的一半收口在**共享层**（`service/common/…`），
而共享层那一段**通常不在你第一眼 grep 的范围里** ⇒ ⇒
只查域内文件就下「整条链路零 X」的断言 ⇒ **结论可能整个方向反过来**。

### 形态（2026-10-27 实测 · D10 的依据差点写错）

本域 fact 投递链路的两个 span 属性（`comment.fact.event_id` / `comment.fact.aggregate_version`）
⇒ 初判写的是「**Go 侧 comment 的 fact 投递链路根本不用 span**」，
依据是 `service/comment/rpc/internal/model/fact_delivery.go` 里 `span`/`Span`/`trace`/`Trace` **零命中**。

⇒ **那个文件确实零命中**（这条断言本身是真的）⇒ ⇒ **但结论错了**：
Go 侧**有**这个 span —— 它建在**共享层** `service/common/communityfact/process.go:26`：

```go
attempt, span := otel.Tracer("sea.rtw.communityfact").Start(ctx, "community.fact_delivery.dispatch")
```

（该文件全文 **69** 行、**零** `SetAttributes` ⇒ ⇒ span **不带属性**）
⇒ 且 RAG 侧**等价 span 也在共享层**（`service/common/.../communityfact/Dispatcher.java:186`，**同名**同 tracer）
⇒ ⇒ **span 拓扑两侧其实是对齐的**，差的只是那两个属性。

⇒ 🔴 **真正的根因在共享层的实现细节里**，不在域内文件里：
共享层把 span 放进 **Context**（`Dispatcher.java:189` `ctx.with(span)`）而**从不 `makeCurrent`**，
且投递跑在**另一个线程**（`:172-176` `communityfact-dispatch` 单线程池 ＋ `:190` `supplyAsync`）
⇒ OTel 默认 `ThreadLocalContextStorage` 用的是**普通** `ThreadLocal`（**不继承**）
⇒ ⇒ 投递线程上 `Span.current()` 恒为 **invalid span**
⇒ ⇒ 域内那两行 `setAttribute` **永远写不进去**（`if (span != null)` 恒真 ⇒ 落到 invalid span ⇒ 静默丢弃）。

### 可复用的判据

> **判据 1：「零命中」只对**被 grep 的那个作用域**成立。**
> 要断言「整条链路零 X」，必须把**链路的每一段**都核过 —— 包括**共享层**那一段。
> ⇒ 域内文件是链路的**一段**，不是**整条** ⇒ ⇒ 「某文件零命中」⇒ **不等于**「链路零命中」。
>
> **判据 2：链路常在**共享层**收口 ⇒ 查链路先查**收口点 / 入口点**。**
> 本例的收口点是共享层的 `Dispatcher`（域侧只提供 `dispatchOnce` 实现）
> ⇒ 「span 叫什么」「span 是不是 current」「投递跑在哪个线程」**全在收口点**
> ⇒ ⇒ **不看收口点 ⇒ 根因必然找错**（本例就会把「根因在共享层的 ctx 传播方式」写成「Go 侧不用 span」）。
>
> **判据 3：断言「X 从来不生效」之前，先确认 X **可能**生效的那条路径真的被堵死了。**
> 本例若只查到「域内 `Span.current()` 够不到共享层那个 span」就收工 ⇒ **方向对**；
> 但若把依据写成「Go 侧不用 span」⇒ ⇒ **依据本身是假的**
> ⇒ ⇒ 一旦有人后来在共享层补了 `makeCurrent`，那两行属性就会**真的**生效
> ⇒ ⇒ **假依据会掩盖真回归**（而且会让后人以为「Go 根本没有这个 span ⇒ 加了也没关系」）。

### 对应本轮的漂移

[`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) §3.7（**D10**）⇒
**结论不变**（那两个属性是 RAG 侧独有的、且写不进去 ⇒ 删掉是**零行为变化**的清理，不是改语义），
**但依据整条换掉**（§3.7 内已订正，并写明「未提交过 ⇒ 直接改正，不是事后翻案」）
⇒ ⇒ **本条是「差点把依据写错」的记录，不是缺陷**。

---

## PIT-COMMENT-RPC-15 🔴 · **「把池打开」会引入比 Go 更糟的行为** —— 池耗尽的等待上限（`max-wait`）**不可省**

**症状**：本域 Go 侧 Redis 客户端**池耗尽时 4s 就失败**；RAG 侧改成「有池」之后，**池耗尽变成无限等**
⇒ 请求永久挂起（连超时都没有）⇒ **比改之前更糟**。

**根因（三段都要查，缺一段就会掉坑）**：

1. **Go 侧有上限** —— `go-redis@v9.20.0/internal/pool/pool.go:1113`
   `err := p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`
   ⇒ `PoolTimeout` 未设 ⇒ `options.go:380-386` `= ReadTimeout + 1s` ⇒ ReadTimeout 3s ⇒ **4s**
   ⇒ 池耗尽 ⇒ 4s 后报 `ErrPoolTimeout`。
2. **RAG 侧默认无上限** —— 不配 `max-wait` ⇒ commons-pool2 2.12.1
   `BaseObjectPoolConfig:59,68` `DEFAULT_MAX_WAIT = Duration.ofMillis(-1L)`
   ⇒ 消费点 `GenericObjectPool.java:306`
   `p = negativeDuration ? idleObjects.takeFirst() : idleObjects.pollFirst(remainingWaitDuration)`
   ⇒ **负值 ⇒ `takeFirst()` ⇒ 阻塞到有人还连接为止**。
3. 🔴 **「命令超时会兜住」是错的** —— `CommandExpiryWriter.write(command)` **才起算**计时器
   （实测 lettuce-core 6.8.2 字节码），而**借连接发生在写命令之前**
   ⇒ ⇒ **池耗尽时，3s 命令超时根本还没开始** ⇒ ⇒ **无限挂起**。

⇒ **修法**：`spring.data.redis.lettuce.pool.max-wait: 4s`（**必配**，与 `enabled` 同级）。

⚠️ **本域还踩到第二个坑**：`min-idle` 原先写 **4**，而本域 Go 侧走 **go-redis 直连**
（`service/comment/rpc/internal/cache/redis.go:17-19` 只设 `Addr`）
⇒ `MinIdleConns` 未设 ⇒ `options.go:218-222` **无 default** ⇒ **0**
⇒ ⇒ **4 是对 Go 的偏离**（go-zero 的 `idleConns = 8` 只适用于**走 go-zero** 的域）
⇒ ⇒ ⚠️ **别把别域的池参数当本域的基准**（AGENTS.md §3.1：不得拿「现有 RAG 代码」当对照基准）。

⇒ **通用教训**：① **「打开一个池」不是单点改动** —— 至少同时改了「并发度 / 总量上限 / **耗尽时的等待语义**」三件事
⇒ ⇒ 改之前**逐轴对着上游库的默认值**核；② **「超时兜底」要查它从什么时候开始计时** ——
超时机制存在 ≠ 它在这个场景下生效（**差一个阶段 ⇒ 兜底失效**）。
📌 **同款机制在 G3 的另一域已落码**（`hot-rpc`：形态对齐 + `max-wait` + 守门 4 条 + 变异 5 条全红）
⇒ **完整机制与坑档** ⇒ [`../../Pitfalls/hot/rpc.md` 的 P-HOTRPC-21](../../Pitfalls/hot/rpc.md)。

## PIT-COMMENT-RPC-16 🔴 · **Redis 断连活体对拍**在本域的**适配点** —— 照抄别的域的配方 ⇒ **上下文起不来**

**背景**：本域要证明共享层那份 `REJECT_COMMANDS`「**在 comment-rpc 里真的生效**」⇒ ⇒ 必须**活体断连**。
⇒ **配方本体（三处易错点）见 [`../../Pitfalls/hot/rpc.md` 的 P-HOTRPC-24](../../Pitfalls/hot/rpc.md)**，
本条只记**本域特有的适配差异** —— ⇒ 🔴 **照抄 hot 域那份 ⇒ 本域上下文起不来**。

| 项 | hot-rpc | **comment-rpc（本域）** | 为什么不一样 |
|---|---|---|---|
| **排 Flyway** | 🔴 **不能** `spring.autoconfigure.exclude=…FlywayAutoConfiguration`（**本模块无 Flyway 依赖** ⇒ 排一个 **classpath 上不存在**的自动配置类 ⇒ **Boot 直接启动失败**）⇒ ⇒ 只能 `spring.flyway.enabled=false` | ✅ **可以** exclude（**本模块有** Flyway 依赖）⇒ ⇒ 排除后 `CommentSchemaGate` 仍会打一条「表不存在」错误日志，但它**只 log 不阻断** ⇒ ⇒ **上下文仍能起** | ⇒ **「排 Flyway」不是通用配方** ⇒ ⇒ **先看该模块 classpath 上有没有** ⇒ ⇒ **各域不能照抄** |
| **出向 gRPC client** | 本域**无**出向 gRPC 调用 ⇒ 无需处理 | 🔴 **两个出向 client 必须提前指向不可达地址**：`grpc.client.message-rpc.address=static://127.0.0.1:1` ＋ `grpc.client.article-rpc.address=static://127.0.0.1:1` ⇒ ⇒ **devh 在 Bean 初始化期就建 channel** ⇒ ⇒ 不覆盖则**上下文起不来** | ⇒ ⇒ **「不需要出向依赖」的域**与**需要**的域，**测试注解写法不同** |
| **PG** | 本域无 PG | 本域有 PG ⇒ ⇒ 用 `POSTGRES_HOST=127.0.0.1` / `POSTGRES_PORT=1` 指向**不可达端口** ⇒ ⇒ **任何摸库都快速失败**（不挂起）⇒ ⇒ 不需要 Testcontainers | ⇒ ⇒ **「摸库要挂起」会让活体对拍变成超时测试** ⇒ ⇒ 用不可达端口 |

⇒ **可复用判据**：**同一个活体对拍配方，不同模块的「适配点」不同** ⇒ ⇒
① **排自动配置前先确认它在不在 classpath 上**（不在 ⇒ 排它 = 启动失败）；
② **有出向 client 的模块要在 Bean 初始化期就把它指向不可达地址**；
③ **有 DB 的模块要把数据源指向不可达端口**（快速失败 ≫ 挂起）。

⇒ **本域实测（本轮现跑）**：RAG 腿 **A(REJECT_COMMANDS) = 1 ms** vs **B(DEFAULT) = 3005 ms** ⇒ 差 **3004 ms**；
Go 腿 **1708–1776 ms**（4 轮）⇒ ⇒ **两侧都是「快速失败」，语义等价**（Go 的 `MaxRetries=3` 重试面另属他档 ⇒ **不混**）。
⇒ **守门** `CommentRedisDisconnectFastRejectGateTest`（**2 条**）· **本域台账** ⇒ [`../../DRIFT/comment/rpc.md` §十二](../../DRIFT/comment/rpc.md)。
⇒ ⚠️ **仍待验**：**gRPC 响应层**（断连时客户端到底看到什么 ⇒ 要真发一次 gRPC 调用）；
⚠️ **Go 探针未入仓**（`tools/**` 属 G4 写权面 ⇒ 已派单 ⇒ ⇒ 仓内**不可复现** ⇒ 证据强度只能写「L4-一次性」）。

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | 本文件各坑对应的**漂移条目** |
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | PIT-02 / PIT-03 对应的 api 侧漂移 |
| [`../_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md) | 全仓级的「绿测试 ≠ 对」—— **共享文件，本工作树不碰** |
