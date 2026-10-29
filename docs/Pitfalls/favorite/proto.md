<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 踩坑档 · `service/favorite/favorite-proto`

> **核对时间：2026-10-25**
> 本档记的是「**为什么会踩**」—— 机制性陷阱，不是缺陷清单。

---

## PIT-FAV-PROTO-001 · 🕳️ **「proto 是复制粘贴」⇒ 复制忠实**不等于**消费方式已验** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-PROTO-001` ⇒ **新号**：`PIT-FAV-20`


### 现象

`favorite-proto` 的全部工作就是把 Go 的 `proto/favorite.proto` **原样复制**过来
（AGENTS.md §3.4：只允许改 option 块与空白层面）。

⇒ 于是「逐行 diff 一致」就成了这个组件**唯一的**验收动作。

### 为什么会踩

AGENTS.md §3.4 的**效力边界第 1 条**写得很清楚：「复制忠实 ≠ 消费方式已验」。
但这一条**很容易被读成「proto 只管复制，别的归生成器管」** ⇒
于是**没人去核对**「RAG 侧拿到生成码之后是怎么用的」。

本域实际要核的四件事（见 [`../../DRIFT/favorite/proto.md`](../../DRIFT/favorite/proto.md) §1）：

| 检查 | 本域结果 | 不查会怎样 |
|---|---|---|
| `optional` presence 语义 | **0 个 optional** ⇒ 该风险天然不适用 | 若有 optional，两侧的 `hasXxx()` 语义可能分叉 |
| 字段号硬编码 | **0 命中** | 绕过生成码写死字段号 ⇒ 字段号一改就静默错位 |
| message 构造方式 | **16 个逐条一致** | 一侧用 `nullToEmpty`、另一侧不设 ⇒ wire 不一致 |
| RPC 方法与调用点 | **7 个逐条一致** | 名字/路径对不上 ⇒ 调用直接失败 |

⚠️ ⚠️ **命名陷阱**：`types.go:18-19` / `:54-55` 的 `json:"title,optional"` 是
**go-zero HTTP 映射层**的 optional（与 protobuf 无关）；
`favorite.proto` 里**一个 `optional` 关键字都没有**（`favorite.proto:20-21` 的「可选」是**中文注释**）。
⇒ review 时极易混谈 ⇒ 「本域无 optional」这句话必须**逐文件 grep 过**才能写。

### 怎么不再踩

1. **「逐行一致」只是入场券，不是结论** ⇒ 组件级对照要把**消费点**也列出来
   （构造点、调用点、字段号硬编码、presence 语义）。
2. **逐条清点时给出「清点范围」**（哪些文件、哪些检索式、命中几处），
   否则「0 命中」和「没搜」分不开。
3. ⚠️ **判定「0 命中」时排除同名干扰**：本域搜 `getField()` 命中 10 处，
   但**全是校验异常的 `getField()`**，与 protobuf 无关
   ⇒ 「检索式命中」必须**逐条看一眼**再写进台账。

---

## PIT-FAV-PROTO-002 · 🕳️ **「默认值不落 wire」只能从生成码里看 —— 而生成码不在版本库** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-PROTO-002` ⇒ **新号**：`PIT-FAV-21`


### 现象

「Go 侧 struct literal 无条件写零值」与「RAG 侧显式设零值」在**隐式 presence** 下是否等价？
这个问题**不能靠 protobuf 规范回答**，只能看**生成码**里 `writeTo` 的条件表达式。

### 为什么会踩

`favorite-proto/target/generated-sources/` **不在版本库内**（`target/` 被忽略）⇒
⇒ 「本仓无生成码级证据」这句话很容易被写成「默认成立」
⇒ 而 §3.3 的纪律是「**凡引入框架机制，必须有运行证据**」。

### 怎么不再踩

⇒ **跑一次构建再读生成码**（2026-10-25 已做）：

```bash
mvn -o -pl service/favorite/favorite-proto -am package
# 读 target/generated-sources/protobuf/java/com/rag/favorite/favorite/proto/FavoriteFolder.java
# 的 writeTo：零值有条件表达式 ⇒ 零值确实不落 wire
```

⇒ 证据原文已抄进 [`../../DRIFT/favorite/proto.md`](../../DRIFT/favorite/proto.md) §2。

⚠️ **顺带确认的一件事**：生成码 extends `GeneratedMessageV3`（**protobuf-java full runtime**），
而 pom 里**同时**声明了 `protobuf-javalite` ⇒ 两者 `com.google.protobuf.*` 包名重叠。
实测**全仓 10 个 `*-proto` 都是这个模板** ⇒ **不是本域的新问题**，
且 `message-proto` 已被验证过不会遮蔽生成码
⇒ **不记入漂移条目**，只留指针，避免后人重复排查。
