# Pitfalls · `service/article/article-rpc`

> 本文件只记**教训**：现象 → 根因（框架机制）→ 为什么现有测试抓不到 → 下次怎么防。
> 差异本体在 [`docs/DRIFT/article/rpc.md`](../../DRIFT/article/rpc.md)。
>
> **写入日期 = 2026-10-23**（第二轮组件级验证）。编号 `PF-ARTICLE-RPC-001~007`。

---

## PF-ARTICLE-RPC-001 —— `@Select` 没有 `@ResultMap`，jsonb 列**静默变 null**

### 现象

`ArticleMapper.selectAuthorView` / `ArticleRevisionMapper.selectByRevisionId` /
`ArticlePublicMapper.selectRevisionsForPublished` 三个方法返回的行里，
`secondary_tags` 与 `ext_info` **恒为 `null`**。

对外表现为：作者列表丢标签 + 丢 `ext_info`；**公开详情丢 `secondary_tags`**；
公开列表丢标签。而 title / brief / markdown / 时间戳 / status **全部正常** ——
所以从响应上看"只差一点"，没有任何报错。

### 根因（框架机制）

MyBatis 有**两套**互不相通的结果映射机制：

| 机制 | 挂在哪 | 何时生效 |
|---|---|---|
| `@TableName(autoResultMap = true)` | MyBatis-Plus **自己生成**的 SQL | 只有 `BaseMapper.selectById` 等 MP 内置方法 |
| `@Results` / `@ResultMap` | **你自己写的** `@Select` | 注解式 SQL 走这里 |

对**没有** `@ResultMap` 的 `@Select`，MyBatis 走 `autoMappingBehavior=PARTIAL` 的
**自动映射**：按「Java 类型 + JDBC 类型」去 `TypeHandlerRegistry` 全局注册表里查。
`List<String>` / `Map<String,String>` 在那张表里**不存在** ⇒ 查不到 ⇒
**静默跳过该属性**。没有异常，没有警告。

### 为什么测试抓不到（本项目已实测的完整链条）

1. **既有 79 条测试里，涉及这两列的全是纯 Mockito** —— mapper 被 mock，
   `SELECT` 根本没被 MyBatis 解析过。
2. 补的真 PG 用例 `ArticleTransactionWiringTest#jsonbRoundTripsFaithfully`
   （`:270-304`）确实测了 jsonb 往返，但它调的是 **`selectById`** ——
   恰好是**唯一挂对了 `@ResultMap`** 的那个方法。测的是对照组，不是缺陷点。
3. 补的真 PG 用例 `authorViewDefaultsToNewestFirst`（`:447-462`）确实调了
   `selectAuthorView`，但种子数据是 `'[]'::jsonb` / `'{}'::jsonb`，
   断言只看 `getId()` ⇒ 空数组也能"通过"。
4. **最狠的一层**：三个类的 javadoc **自己写明了这个坑**
   （`ArticleMapper.java:55-65`：「查不到就静默跳过该属性」，
   `ArticleEntity.java:19-25`：「autoResultMap 只挂在 MP 自己生成的 SELECT 上」）
   —— **写清楚了坑，然后自己踩了**。
   ⇒ 文档写对**不能**替代机制验证，这和 AGENTS.md §3.3「注解存在 ≠ 机制生效」同源。

### 下次怎么防

- **规则**：凡返回实体（且实体带 `@TableField(typeHandler=…)`）的**自定义 `@Select`，
  必须显式写 `@ResultMap` 引用一个已声明的 `@Results`**。把这条做成
  code review 清单的第一项，比注释有用。
- **守门测试的形状**：对**每一个**自定义 `@Select` 逐个断言
  "有 typeHandler 的字段非 null 且值正确"，而不是只测一个代表方法。
  可以用 `@Results(id=…)` 的存在性做静态扫描，但**别只做静态**。
- **反模式**：用 `'[]'::jsonb` / `'{}'::jsonb` 当种子数据。
  空值会让"读不出来"与"本来就是空"**不可区分**。
  种子必须用**非空**值（`["alpha","中文标签"]`），断言必须比对**内容**。

---

## PF-ARTICLE-RPC-002 —— "ORM 会自动维护 `updated_at`"是**没有**的默认能力

### 现象

`article.updated_at` 自创建后再不变化。改标题、改正文、拒稿、发布 ——
时间戳全部停在创建那一刻。对外表现为 `GetArticle` 的 `update_time` 恒等于创建时间。

### 根因（框架机制）

Go 侧 `UpdatedAt time.Time \`gorm:"autoUpdateTime"\``（`model/article.go:31`）
由 **GORM 自己在 Save 时赋值**。RAG 侧换成 MyBatis-Plus 后，
`updateById` **只会把实体里已有的值原样写回**：

- `ArticleEntity.updatedAt` **没有** `@TableField(fill = FieldFill.INSERT_UPDATE)`；
- 全仓 **零** `MetaObjectHandler`（`grep MetaObjectHandler|FieldFill` 在
  `service/article/**` 命中 0；对照 message-rpc 的 `ConversationEntity.java:64,67` 是有的）；
- MP 默认 `updateStrategy = NOT_NULL`，于是"非空就写" ⇒ 把旧值写回去。

三者缺一，结果都是"时间戳不动"，而**没有任何报错**。

### 为什么测试抓不到

`ArticlePublicDomainServiceViewTest` 与 `ArticleDomainServiceSemanticsTest`
断言的都是**方法返回值**（`resp.getArticle().getViewCount()`、`assertEquals(6, ...)`），
`update_time` 从来没进过断言。
真 PG 用例断言的是 `status` / `view_count` / `revision 数量`，
**没有一条断言 `updated_at`**。

### 下次怎么防

- **规则**：迁移到新 ORM 时，把「真源 ORM 的**自动**维护的字段」列成清单，
  逐个在目标侧找到**对应机制**（注解 + handler / SQL / 触发器），三者都没有就**记漂移**。
  本项目已有一个成熟先例：`user-rpc` 的 `UserMapper.java:88` 就明确写了
  「本仓既无 MetaObjectHandler（本模块零命中）」—— **同一仓库里已有答案，却没被复用**。
- **守门测试的形状**：预置一个**明显不同**的历史值 → 改一个业务字段 →
  断言该时间列**已推进**。不要断言"时间戳非空"。

---

## PF-ARTICLE-RPC-003 —— 「Go 显式 `status.Error` 的分支」对齐了，**「Go 用一段 catch 统一映射」的分支**漏了

### 现象

公开读路径上的 DB 失败，客户端收到的业务码是 **1015（服务繁忙）**，
Go 侧是 **5001（系统内部错误）**。
同时 `ArticleExceptions.incrementPublicViewFailed()` 这个工厂方法**全仓零调用点** ——
"这条 INTERNAL 曾被设计出来，但接线漏了"。

### 根因（框架机制 / 语言映射）

Go 有一类写法是：

```go
published, err := repo.FindPublicOne(ctx, id)
if err == gorm.ErrRecordNotFound { return NotFound }
if err != nil { return Internal("read publication failed") }   // ← 一整类错误被归一
```

它的语义是「**这段里的任何底层错误** ⇒ `INTERNAL`」，
而不是"某几个点返回 INTERNAL"。迁移时若只对着**显式 `status.Error` 的行**逐条对照，
这一整类就会**整体漏掉**：Java 侧没有对应的 catch，
底层 `DataAccessException` 直接冒到 gRPC 边界的兜底 `catch (RuntimeException)` ⇒ `UNKNOWN`。

### 为什么测试抓不到

- 现有 `ArticlePublicDomainServiceViewTest#publicViewIncrementFailureIsFatal`
  名字写着"自增失败 ⇒ 致命 Internal"，**实际只测了 `RowsAffected == 0`**（`:180`）
  那条 `NOT_FOUND` 分支。**测试名承诺的覆盖面 > 实际覆盖面**，review 时极易被当成已覆盖。
- `ArticleSemanticsRegressionTest` 的 S-6 三例测的是 `invoke` 的**异常分派**，
  不经过真实 mapper ⇒ 永远不会抛出 `DataAccessException`。

### 下次怎么防

- **规则**：Go 侧凡是「一段 `if err != nil { return X }` 把一整类错误归一」的写法，
  在对照表里**单独列一行**，标注「归一映射」，而不是拆到各个 `return` 点。
- **反模式（本次的教训）**：**测试名里写了 "Internal" 却只断言 NOT_FOUND**。
  `@DisplayName` 声称的语义必须逐字对得上断言，否则它会变成给后人设的陷阱
  （AGENTS.md §3.3.2 的另一种形态：名字本身在骗人）。
- **守门测试的形状**：让 mapper **抛异常**（不是返回 0），断言 gRPC code。

---

## PF-ARTICLE-RPC-004 —— 不开 `@TableLogic`，就要把「哪些写需要软删过滤」写进 SQL

### 现象

`updateById` 会**成功改写已软删的行**（探针：`rows=1`，`brief` 被写回）。
Go 的 `Save` 会追加 `deleted_at IS NULL`，命中 0 行后退化成 `Create` ⇒ 撞主键 ⇒ **报错**。

### 根因

项目里刻意**不用** `@TableLogic`（理由本身是对的：不能让"要不要看已删行"变成全局开关）。
但"哪些**读**要 Unscoped"被显式落实成了两个方法
（`selectById` / `selectByIdUnscoped`），
**"哪些写需要软删过滤"却一条都没落实**。

⇒ **纪律只在一个方向上兑现了。** 这是"做对一半"比"完全没做"更危险的地方：
读侧的测试会让人误以为软删除语义已经整体对齐。

### 为什么测试抓不到

`softDeleteVisibilityMatchesGoMethodSplit`（真 PG）测的是**读**；
写路径的真 PG 用例都是在**未删除**的行上做的。
`rejectAsAd` / `handleDeleteResult` 等调用点都先判过 `deletedAt`，
所以缺陷只在"判过之后、写入之前发生并发删除"的窄窗口里可达。

### 下次怎么防

- **规则**：放弃 `@TableLogic` 之后，把软删除语义列成**一张读写双向的表**：
  「哪个方法 → 带不带 `deleted_at IS NULL` → Go 出处」。只做完读侧不算做完。
- **守门测试的形状**：软删一行 → 调该写方法 → 断言**影响 0 行或抛错**。

---

## PF-ARTICLE-RPC-005 —— 「依据被证伪」和「结论被推翻」是两回事，必须分开记录

### 现象（本轮实际发生的一件事）

S-3 的修复**结论**（对齐 Go 的 `ForceCommit=true`，失败即提交位点）经复核**成立**；
但 RAG 代码里那段**论证**引用了 spring-kafka 的
`commit(consumer, container, offset+1)` 分支，
而该分支**只在 `commitRecovered=true` 时执行**
（`SeekUtils.java:229`），`FailedRecordProcessor.java:57` 默认 false，
本模块**从未**开启 ⇒ **引用的是一条不在本模块执行路径上的代码**。

如果照着这段注释去"验证"，会得到一个看起来正确、其实无效的结论。

### 为什么测试抓不到

锁测试 `kafkaContainersCarryTheForceCommitErrorHandler` 断言的是
**属性值** `isSeekAfterError() == true`。

⚠️ **断言属性值 ≠ 断言该属性值产生了 Go 的行为。**
属性值断言只能证明"代码按预期写了配置"，证明不了"这个配置等价于 Go"。
这是 AGENTS.md §3.3「Bean 存在 ≠ 机制生效」**再往上一层**：
**Bean 存在 + 属性值正确 ≠ 行为等价**。

### 下次怎么防

- **规则**：凡是「照抄框架某个开关值来对齐真源」的修复，
  必须同时回答两个问题：① 框架源码里**消费**这个值的控制流在哪（AGENTS.md §3.3.1 的老规矩）；
  ② 那个开关在本项目**实际可达**吗（默认参数 / 未开启的分支 / 未引入的模块）。
- **守门测试的形状**：断言**行为**（失败一次后该 offset 是否推进、是否再投递），
  不是断言 getter。

---

## PF-ARTICLE-RPC-006 —— 「方向相反」的注释会让后来者去修一个**已经正确**的实现

### 现象

`pom.xml:108` 与 `ArticleReviewConsumer` 的类级注释都写着：

> ⚠️ ack 语义方向相反：Go 是「返回 error ⇒ 不确认」，Java 是「抛异常 ⇒ 不提交」。

这句话**字面正确、结论误导**。机制方向确实相反，
但两侧的**可观察结果相同**（都丢弃失败消息）⇒ 它是 `IMPLEMENTATION DIFFERENCE`，**不是 MISMATCH**。

一个只读到这里的人，很可能会得出"那得改点什么"的结论 ——
而正确做法是**什么都不改**（先证明等价性，见 PF-ARTICLE-RPC-005）。

### 为什么测试抓不到

测试不读注释。反过来说：**注释里"⚠️ + 警告语气"的段落是最容易被当成待办的地方**，
所以它必须经得起独立判断，而不是写完就当结论。

### 下次怎么防

- **规则**：注释里出现「⚠️ 方向相反 / 不一致 / 不对称」这类词时，
  必须在同一段里给出**判定结论**（等价的 / 不等价的 + 证据），而不是只描述现象。
- **本轮结论**（可复用）：`ForceCommit=true` + `FixedBackOff(0,0)` + recoverer 跳过 seek
  ⇒ 失败消息**两侧都被丢弃**；机制不同、结果相同。

---

## PF-ARTICLE-RPC-007 —— javadoc 里写的「锁死用例」可能**根本不存在**

### 现象

本模块至少 **3 处** javadoc 声称由某个测试类锁死：

| 出处 | 声称的用例 | 2026-10-23 实测 |
|---|---|---|
| `JsonbTypeHandler.java:46` | `ArticleJsonbMappingTest` | ❌ 不存在 |
| `ArticleEntity.java:25` | `ArticleJsonbMappingTest` | ❌ 不存在 |
| `ArticlePublicDomainService.java:157` | `ArticlePublicListSnapshotTest` | ❌ 不存在 |

`src/test` 下只有 **7** 个测试类，这三个名字一个都没有。

### 为什么这件事在本项目特别危险

因为**真正需要它们守护的缺陷确实存在**（PF-ARTICLE-RPC-001 的 jsonb 映射）。
文档说得斩钉截铁、代码确实有洞、而唯一的 jsonb 真 PG 用例走的是对照组
⇒ **三道防线里，唯一那道写着"有防线"的其实是空的**。

这与 AGENTS.md §3.3.2 记录的形态同源：**以断言形式写下的、不存在的证据**。

### 下次怎么防

- **规则**：写「锁死用例：X」之前先 `ls` 一次；**更稳的做法是把这条纳入守门脚本**
  （例如扫描 `src/**` 的 javadoc 里的 `XxxTest` 标识符，校验每个名字都能在
  `src/test` 找到对应文件）。本项目已经有 `tools/difftest/` 这类脚本的位置。
- **推广**：任何"某个测试锁住了 X"的措辞，review 时一律要求
  `grep -rn "class X" src/test` 命中，否则按 **UNKNOWN** 处理。

---

## 附：本轮三条通用纪律（可复用到其他组件）

1. **探针优先于注释。** 本轮三个最高危的缺陷（jsonb 静默 null、`updated_at` 不动、
   软删行可写）**全部是跑一次真 PG 才暴露的**，其中两个在代码注释里被写反了。
   在 Docker 可用的机器上，**为每个组件造一个 `/tmp` 里的 scratch 探针**
   （不改仓库任何源码，只往 `target/classes` 放编译产物），
   比再写 20 条 Mockito 单测有效得多。
2. **种子数据不能用空值。** `'[]'::jsonb` / `'{}'::jsonb` 会让
   「读不出来」与「本来就是空」不可区分。凡是断言"某列被正确读出"的用例，
   种子必须**非空**、断言必须比对**内容**。
3. **对照表要有「归一映射」这一行。** Go 用一段 `if err != nil` 把一整类错误
   归一到某个 code 的写法，逐个 `return` 点对照是**对不完的**（PF-ARTICLE-RPC-003）。
---

## PF-ARTICLE-RPC-005 —— 「改的是 bean，读的是 Environment」：一种不会变红的假绿

**现象**
给 outbox 中继的 `@Scheduled` 补「配置 ≤ 0 ⇒ 兜底 1s」的语义时，
本轮写了这样一条测试：

```java
@Bean ArticleRpcProperties articleRpcProperties() {
    ArticleRpcProperties props = new ArticleRpcProperties();
    props.getArticleSyncOutbox().setPollIntervalMs(0);   // ← 只改了 **bean**
    return props;
}
```

容器正常起来，测试全绿。
**但把修复改回去（`@Scheduled` 退回裸占位符）—— 测试仍然全绿。**
⇒ 变异验证判成「**假绿**」。

**根因**
`@Scheduled(fixedRateString = "${article-rpc.article-sync-outbox.poll-interval-ms:1000}")`
里的 `${…}` 走的是 **`Environment`（PropertySource）**，
与 `ArticleRpcProperties` 这个 **bean 的字段**是**两条不同的数据通路**：

| 通路 | 谁读 | 本轮误改的地方 |
|---|---|---|
| `Environment` | `@Scheduled` 的占位符、`@Value`、`@ConfigurationProperties` 绑定 | ❌ 没改到 |
| `ArticleRpcProperties` bean | 业务代码 `properties.getArticleSyncOutbox().getPollIntervalMs()` | ✅ 改了（但没人读它） |

⇒ 「配置写 0」这个场景**根本没进到**被测的那条通路上
⇒ Environment 里没有这个键 ⇒ 占位符走默认值 `:1000` ⇒ 一切正常。

**为什么测试抓不到**
断言写成「`normalizeIntervalMs(0) == 1000`」—— 那是**纯函数断言**，
它只证明**兜底函数本身**是对的，与「`@Scheduled` 真的会用到它」**无关**。
⇒ 真正的行为断言是「**容器在配置为 0 时能不能起来**」，而它被绕过了。

**元教训**
> **「改了配置对象」≠「配置被读到」。**
> 配置有**至少两条**通路（Environment / 绑定后的 bean），
> 而**框架注解（`@Scheduled` / `@Value` / SpEL）几乎总是读 Environment**。
> ⇒ 写「某个配置值生效」的测试时，必须先确认**被测代码到底从哪条通路取值**，
> 再决定改哪里。
> ⇒ 判据：**把被测的读取路径断掉，看测试会不会红** —— 不会红就说明没测到它。

**下次怎么防**
1. 「配置写 0 / 写错」这类场景，一律用
   `@TestPropertySource(properties = "…")` / `properties = {"…"}` 落在 **Environment** 上，
   **不要**只 `new` 一个配置对象改字段。
2. 断言要覆盖**装配期行为**（容器起不起来 / 调度是否解析成功），
   而不是只断一个纯函数的返回值。
3. ⚠️ **变异验证必须包含「把框架注解改回原样」这一档** ——
   只变异业务代码会漏掉「注解根本没读到你改的那个值」这类假绿。
