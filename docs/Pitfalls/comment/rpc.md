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

## 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`../../DRIFT/comment/rpc.md`](../../DRIFT/comment/rpc.md) | 本文件各坑对应的**漂移条目** |
| [`../../DRIFT/comment/api.md`](../../DRIFT/comment/api.md) | PIT-02 / PIT-03 对应的 api 侧漂移 |
| [`../_cross/testing-pitfalls.md`](../_cross/testing-pitfalls.md) | 全仓级的「绿测试 ≠ 对」—— **共享文件，本工作树不碰** |
