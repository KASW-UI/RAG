# Pitfalls · `service/article/article-rpc`

> 本文件只记**教训**：现象 → 根因（框架机制）→ 为什么现有测试抓不到 → 下次怎么防。
> 差异本体在 [`docs/DRIFT/article/rpc.md`](../../DRIFT/article/rpc.md)。
>
> **写入日期 = 2026-10-23**（第二轮组件级验证）→ 2026-10-24 续写。
> 编号 `PF-ARTICLE-RPC-001~009`。
> ⚠️ **2026-10-24 修正**：原「改的是 bean，读的是 Environment」那条被误编号为 `005`
> （与「依据被证伪 vs 结论被推翻」撞号）⇒ 已改为 **008**；新条目从 **009** 起。

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

## PF-ARTICLE-RPC-008 —— 「改的是 bean，读的是 Environment」：一种不会变红的假绿

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

---

## PF-ARTICLE-RPC-009 —— 「某个判据 ⇒ 两者必相等」：这种句式写进台账，会让漂移**看起来已经被证明过**

**现象**
2026-10-24 补 `U-5`（`acceptSyncResult` 成功路径的真 PG 用例）时，
台账里**早就**记着一条「通知标题来源」的差异，判词是：

> `article.Title`（当前行） vs `approved.title`（冻结 payload）
> → **IMPLEMENTATION DIFFERENCE**
> → 静态（在 `last_sync_event_id` + `last_sync_version_ms` 双重判据约束下两者必相等）

读起来像一条**已经论证过的等价性证明**，而且结论很"安全"（"两者必相等，不用管"）。
⇒ 于是这条差异被搁置了。

**直到补上真 PG 用例、把两个标题故意设成不同值，它才转红** ——
`expected: <作者改过的标题> but was: <冻结时的标题>`。

**根因（不是框架机制，是**论证本身**错了）**
那条论证的形状是：「有判据 P ⇒ A 与 B 必相等」。
错在 **P 到底约束了哪个字段**：

| | 内容 |
|---|---|
| P（双重判据） | `ext_info.last_sync_event_id == result.EventID` **且** `ext_info.last_sync_version_ms == result.VersionMs` |
| P 约束的字段 | **event_id** 与 **version_ms** |
| A 与 B | 当前行标题 vs 冻结 payload 标题 |
| ⇒ | P **根本不约束标题** ⇒ "必相等"是个**空推论** |

而真正让两者能分叉的机制是**时序**：
改标题**立即写进行**（`update_article_logic.go:142-144`），
但新 `event_id` **不会立刻**写（`enqueueReview` 只推 Kafka，新 event_id 等审核回调才写）
⇒ 存在一个「标题已改、event_id 未换」的窗口，旧结果到达时判据依然成立。

**为什么测试抓不到**
因为**压根没有测**：这条差异被判成"两者必相等"之后，
就没有人会去为"两者不等"写用例。
⇒ 绿测试不是漏报，是**问题在写测试之前就被论证掉了**。

**元教训**
> **「某个判据 ⇒ 两者必相等」这种句式，在写进台账前必须问一句：
> 这个判据约束的是**那个**字段吗？**
>
> 它特别危险的原因有三条：
> 1. 它**长得**像一条严谨的证明（有具体字段名、有"双重"这种强调）；
> 2. 它写进了**台账**，下一个 agent 会读成"已证明等价，不必处理"；
> 3. 它把一条**可达**的漂移降级成了 `IMPLEMENTATION DIFFERENCE` ——
>    而 `IMPLEMENTATION DIFFERENCE` 在本项目的语义是"行为一致，只是写法不同"。
>
> ⇒ 与 AGENTS.md §3.1「不要拿未验证的结论当对照基准」同源，
> 但**更隐蔽**：那个结论不是"拿现有 RAG 代码当基准"，
> 而是**自己造了一条看着有依据、实则空推的等价性论证**。
> ⇒ 对照 §3.3.1：Go 的 struct tag 那种"藏在声明里"的行为要读到消费点；
>    **同理，自己写的等价性论证也要读到"消费点"** ——
>    问「判据最终比较的是哪两个值」。

**顺带一条：Go 侧同一件事可能有**两个不同的源**，混用即错**

| 用途 | Go 的源 | 位置 |
|---|---|---|
| revision 的标题 | `approved.Title`（冻结 payload） | `article_sync_result_consumer.go:115` |
| 通知的标题 | `article.Title`（**当前行**） | `article_sync_result_consumer.go:147` |

RAG 侧两处都用了 `approved.title`，看起来"统一"了，实际上把 Go 刻意分开的两个源**合并**了。
⇒ **"两边用了同一个值"不是"两边等价"** ——
统一到**错误的**那个源，会同时让两个用途都偏离 Go。

**为什么测试抓不到（第二层）**
如果只断言"通知标题 == 冻结标题"，用例照样绿（它测的是 RAG 的现状，不是 Go 的语义）。
⇒ 真正有分量的断言是**同时**断言两个源：

```java
assertEquals("冻结时的标题", revision.getTitle());   // Go :115
assertEquals("作者改过的标题", outcome.title());      // Go :147
```

⇒ **任一取错源都会红** ⇒ 这才叫"钉住了语义"，而不是"钉住了现状"。

**下次怎么防**
1. 写「A 与 B 等价」这类结论进台账前，先问：**我依据的那个判据，比对的是不是 A 和 B 本身？**
   不是 ⇒ 这条论证不成立，要么补论证，要么降级为 OPEN。
2. Go 侧同一语义点若有**多个来源**（行 / 冻结输入 / 派生值），
   对照时**逐个用途**建立映射，不要按"这个字段"整体对。
3. 台账里的 `IMPLEMENTATION DIFFERENCE` **不是**"低优先级"的同义词 ——
   在本项目里它表示"**行为一致**"。判不准的，写 `UNKNOWN`，别写 IMPLEMENTATION DIFFERENCE。
4. 补一条缺失的测试时，**先假设它会转红** ——
   转红的位置往往不是你想补的那条缺口，而是**另一条被论证掉的漂移**
   （本轮就是这样：`U-5` 补网 ⇒ 撞出 `DRIFT-ARTICLE-RPC-009`）。

---

## PF-ARTICLE-RPC-010 —— 启动期校验挂在哪个钩子上：Spring 的「启动后钩子」比「刷新前钩子」**晚了一个生命周期阶段**

**现象**
`ArticleSchemaGate` 原本用 `@EventListener(ApplicationReadyEvent.class)` 做启动期校验。
它确实**会**在 schema 缺失时让进程启动失败（fail-fast 语义没问题），
但**失败之前，gRPC server 已经绑定端口、已经对外服务过**了。

**根因（框架机制 —— Spring 的生命周期阶段顺序）**

`AbstractApplicationContext.refresh()`（spring-framework 7.0.9）：

```
:621  finishBeanFactoryInitialization(beanFactory)
        └─ DefaultListableBeanFactory.preInstantiateSingletons()
             ├─ 实例化全部单例（Flyway 的 FlywayMigrationInitializer 在此 afterPropertiesSet）
             └─ :1144-1147  SmartInitializingSingleton.afterSingletonsInstantiated()  ← 可用
:624  finishRefresh()
        └─ :1013  getLifecycleProcessor().onRefresh()
              └─ 启动全部 SmartLifecycle ⇒ devh GrpcServerLifecycle.start()
                                              ⇒ localServer.start() ⇒ **端口绑定**
```

而 `ApplicationReadyEvent` 由 `SpringApplication.run()` 在 **`refresh()` 返回之后**才发布
⇒ 它落在 `finishRefresh()` **之后** ⇒ 端口早绑了。

⇒ **一句话**：`ApplicationReadyEvent` 的语义是「应用已经就绪，可以接流量了」，
**恰恰是 fail-fast 校验最不该用的那个时机**。

**为什么测试抓不到（第一层）**
原来的测试只断言"schema 缺失时启动失败" ⇒ **绿**。
它完全没问"失败之前有没有对外服务过"。
⇒ **"启动失败"与"从未对外服务"是两件事**，前者不蕴含后者。

**为什么测试抓不到（第二层，更隐蔽）**
想补一条"断言端口没被绑过"的用例时，**事后查端口是查不出来的**：
失败启动后 Spring 会 `cancelRefresh` → 停掉已启动的 lifecycle → 端口**最终也是关着的**。
⇒ "从没启动过"与"启动过又关了"在事后观察上**完全同形** ⇒ 这类断言天生**假绿**。

**元教训**
> **验证"某个组件有没有启动过"，必须用一手信号，不能用事后状态。**
> ⇒ 一手信号 = 组件**自己**在启动那一刻发出的东西（事件 / 日志 / 计数器）。
> ⇒ 本例用的是 devh 的 `GrpcServerStartedEvent`：它在 `localServer.start()`（端口已绑）**之后**发布
> ⇒ **收到它 == 确实对外服务过**。
> ⇒ 对照：**关掉端口、查连接、查进程** 都是事后状态，都会假绿。

**顺带一条：负向断言必须配一条正例（对照）**
"断言事件**没**发生"这种用例，如果**监听器本身坏了**，会永远绿。
⇒ 必须有一条正例："schema 完好 ⇒ 该事件**确实**发生了" ⇒ 证明监听器是活的。
⇒ 这与本项目已记的「对照变异」是同一个思路：**证明你的观测手段本身是有效的**。

**下次怎么防**
1. 写"启动期 X 必须在 Y 之前"这类约束时，
   先查**框架的阶段顺序**（读 `refresh()` 源码，不要凭直觉），
   再选钩子。常见可选点（由早到晚）：
   | 钩子 | 时机 | 适合 |
   |---|---|---|
   | `BeanFactoryPostProcessor` | 单例都还没建 | ❌ DataSource/Flyway 都没好，不能查库 |
   | `SmartInitializingSingleton.afterSingletonsInstantiated()` | 单例已就绪、`SmartLifecycle` **未**启动 | ✅ **要"先校验再对外服务"就用这个** |
   | `ContextRefreshedEvent` | `onRefresh()` **之后** | ❌ 太晚 |
   | `ApplicationReadyEvent` | `refresh()` 返回之后 | ❌ **更晚**（"已就绪"语义） |
   | `ApplicationRunner` | `refresh()` 之后、ready 之前 | ⚠️ 仍晚于 lifecycle 启动 |
2. 断言"没启动过"要用**组件自己发的启动事件**，不要用"事后端口/连接/进程状态"。
3. 每条负向用例配一条**正例**，证明观测手段有效。

---

## PF-ARTICLE-RPC-011 —— `publishPercentileHistogram()` **不是**"用我给的桶"，而是"**额外**塞一整套默认桶"

**现象**
给 MinIO 耗时的直方图对齐 Go 的分桶时，代码里同时写了：

```java
Timer.builder("article_service_minio_request_duration")
        .publishPercentileHistogram()
        .serviceLevelObjectives(MINIO_BUCKET_DURATIONS)   // ← Go 的 8 个桶
```

注释还写着「换算后数值一致，因此两侧的分桶边界相同」。
⇒ **实测：RAG 77 个桶，Go 9 个**（8 + 固有的 `+Inf`）。

**根因（框架机制，读到消费点）**
micrometer-core **1.16.7**，`DistributionStatisticConfig.getHistogramBuckets(...)`（`:101-117`）：

```java
if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
    buckets.addAll(PercentileHistogramBuckets.buckets(this));   // ← 默认百分位桶（额外的一整套）
    buckets.add(minimumExpectedValue);                           // → 0.001
    buckets.add(maximumExpectedValue);                           // → 30.0
}
if (serviceLevelObjectives != null) {
    for (double sloBoundary : serviceLevelObjectives) buckets.add(sloBoundary);   // ← 只是"再加"
}
```

⇒ **是并集，不是"SLO 优先"**。
`publishPercentileHistogram()` 的语义是"我要 Prometheus 原生的百分位直方图"，
而 Prometheus 的原生桶表是**固定的一整套**（Micrometer 用 4 的幂 + 1/3 步长生成，共 77 个含 `+Inf`），
**与你给的 SLO 桶无关** —— 它只会**叠加**上去。

**为什么测试抓不到**
原来的断言只比对 `# TYPE` 行里的**指标名**（`# TYPE xxx histogram`），
**桶在 `_bucket` 系列的 `le=` 标签里**，压根不在被断言的那几行里。
⇒ 名对了、桶错了，测试照样绿。

**元教训**
> **Micrometer 的 `serviceLevelObjectives` 与 `publishPercentileHistogram` 不是二选一，是叠加。**
> ⇒ 只要目标是"桶边界与 Go 逐条一致"，就**只**写 `serviceLevelObjectives`。
> ⇒ 凡是要与 Go 的 `HistogramOpts.Buckets` 对齐，**不要**加 `publishPercentileHistogram()`。
>
> **更通用的一条**：
> **注释里那句"因此两侧等价"最危险** —— 它既像结论，又是**不查证就下结论的理由**。
> 本轮正是这句注释让一条 8 倍的差异在台账里以 `EQUIVALENT` 的面目待着。
> ⇒ 对照 AGENTS.md §3.3.2：**任何"因此一致"的断言，都必须配一条能把它证伪的用例**，
> 否则它只是在**阻止**别人去查。

**为什么这条必须用<b>真抓取</b>断言**
桶集是 Micrometer 内部一层层算出来的（`DistributionStatisticConfig` ⇒
`PercentileHistogramBuckets` ⇒ 注册表的 exposition），
**源码层面推不出最终结果**（还要过 `includeInfinityBucket`、Prometheus 客户端自己补 `+Inf` 等几道）。
⇒ 必须抓一次真实的 Prometheus 文本，逐行读 `le=` 标签。

**下次怎么防**
1. 对齐 Go 的 `HistogramOpts.Buckets` ⇒ **只用 `serviceLevelObjectives`**，且**不**加 `publishPercentileHistogram()`。
2. 断言要**抓 exposition**，逐行读 `le=`，而不是只对 `# TYPE` 的名字。
3. 断言时把 `+Inf` 单独处理（`Double.parseDouble("+Inf")` 会抛 `NumberFormatException`，
   Prometheus 的标签字面量是 `"+Inf"` 而不是 `"Infinity"`）。
4. 凡是"框架 A 的这个开关 + 框架 B 的那个配置"组合使用，
   **先查清楚两者的合并语义**（并集？覆盖？优先级？）—— Micrometer 这里就是**并集**。

---

## PF-ARTICLE-RPC-012 —— 并发用例**只对结果敏感**，它测不出锁序

### 现象（本轮实际发生的一件事）

本轮为 `U-2`（锁顺序反转）补了一条**并发真 PG 用例**：
同一篇文章并发「降级」与「删除」，断言「恰好一个成功 + 后到者撞 pointer conflicts +
指针版本单调到 2 + 域事件只有 1 条」。

用例**一次就绿**，看起来把「锁序」这件事证伪了。

然后做**变异验证** —— 把 `retractAndUpdate` 的 article 行锁拿掉
（`selectByIdForUpdate` → `selectById`），锁序因此从 `article → publication`
变成 `publication → article`（**这是一个真 PG 上已实测会死锁的锁序**）：

| 用例 | 结果 |
|---|---|
| `concurrentRetractAndDeleteOnSameArticleSerializesWithoutDeadlock` | ⚠️ **仍全绿** |
| `lockOrderIsArticleThenPublication`（另一条探针） | ✅ **转红** |

⇒ 前一条用例**测不出锁序**。

### 根因（不是测试写得不好，是**断言的东西不对**）

并发用例断言的是**最终结果**（谁成功、指针版本几、事件几条）。
而**只要两条路径的锁序彼此一致，结果就一样** ——
顺序变了，但「两个事务被串行化」这件事没变 ⇒ 结果不变 ⇒ 用例当然不变红。

⇒ 更一般地说：**用例只对它断言的那个维度敏感。**
断言结果 ⇒ 对机制不敏感；断言机制（锁序 / 隔离级别 / 可见性）⇒ 必须有**针对机制**的断言。

### 为什么这在本项目特别危险

本仓已经栽过两次同类的坑：

| 案例 | 绿测试断言了什么 | 漏掉了什么 |
|---|---|---|
| `article-rpc` 101 条全绿 | 流程跑通 | jsonb 列**恒 `null`**（PF-ARTICLE-RPC-001） |
| `admin-rpc` 绿测试 | `update_time` 有值 | 把 `update_time` **不推进**锁死了 |
| **本条** | 并发结果正确 | **锁序反了**（Go 侧已实测会死锁） |

⇒ AGENTS.md §3.3 那句「**跑绿 ≠ 对**」在这里的具体形态：
**绿测试是"错答案的守卫"**（本例中，绿测试守的是一个**恰好也成立**的断言），
比没有测试更危险 —— 因为它让后来者以为这件事已经验过了。

### 下次怎么防

1. **先问一句：「要让这条用例转红，我得改坏什么？」**
   ⇒ 如果答案是「改不出东西」⇒ 这条用例**没在测你以为的东西**。
   （本轮：改坏锁序它不红 ⇒ 它没在测锁序。）

2. **要断言机制，就让机制直接可观测。**
   本轮的做法（**确定性**，不靠线程时序碰运气）：
   **同时扣住要区分的那两把锁**（`article` 行 + `article_publication` 行），
   让被测代码去跑 ⇒ 它必然卡在**它最先要的那把锁**上；
   再用 `pg_stat_activity`（`wait_event_type='Lock'`）读出被阻塞后端**正在执行的 SQL**
   ⇒ **那条 SQL 就是它的第一把锁**。

   ⚠️ 两个细节：
   - 扣锁的连接必须是**另一条连接**（同一连接上的事务锁不住自己）；
   - 判别子串要**够具体**（本例用 `"FROM article WHERE"`，**带 WHERE** ——
     `article_publication` 是下划线连接，不含该子串，否则两条 SQL 互相误判）。

3. **并发用例与机制探针要分开写、分开变异。**
   前者守「结果对不对」，后者守「机制对不对」；
   **拿机制变异去试结果用例**，如果它不红，说明结果用例不覆盖该机制（如本轮）——
   这本身就是有用结论，要**写进台账**，不要默默认为「验过了」。

4. **变异验证要三态判定**（AGENTS.md §3.3）：
   「转红」✅ ／「仍全绿」⚠️（= 覆盖不到，必须记录并另找探针）／「变异无效」❌。
   本轮的「仍全绿」正是**新写了两条探针**的直接动因 ——
   若没有做变异验证，这条错误的锁序可以一直绿着。


---

## PF-ARTICLE-RPC-013 —— 「配置面与生效面不一致」**永远不会**让任何行为测试变红

### 现象（本轮实际发生的一件事）

本轮处理 `DRIFT-ARTICLE-RPC-007`（四个 Kafka 通道的 `brokers` 在 Spring Kafka 下没有消费点）。
先写了一条**启动期校验器**：四个通道的 `brokers` 与全局 `spring.kafka.bootstrap-servers`
不一致就**启动失败**（把「静默忽略」变成「响亮报错」）。

把校验器装进真实上下文后 —— **4 个 `@SpringBootTest` 类的上下文当场起不来**：

```
Caused by: IllegalStateException: article-rpc 的 Kafka 通道「kq-pusher-conf（生产 article-creation-topic）」
  的 brokers=[[kafka:9092]] 与全局 spring.kafka.bootstrap-servers=[[127.0.0.1:1]] 不一致，但该值**不会生效**。
```

⇒ 这 4 个类都把全局 `spring.kafka.bootstrap-servers` 改成 `127.0.0.1:1`
（让 listener 惰性重试、不阻塞上下文启动），
而**四个通道的 `brokers` 键还留在 yml 默认的 `kafka:9092`**。

⚠️ **这个矛盾已经存在很久了，而且所有测试一直全绿。**

### 根因（不是测试写得不好，是**这类差异在行为上不可见**）

`brokers` 这个键在 Spring Kafka 下**没有任何消费点** ——
`KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` 都是**全局单例**，统一吃全局那份。

⇒ 「通道的 brokers 写错了」这件事**不产生任何行为差异**：
服务照样起、消息照样收发、测试照样全绿。

⇒ 一般化：
> **凡是「配置声明」与「实际生效」不一致、但不改变运行结果的差异，
> 行为测试在原理上就抓不到它。**

这类差异的典型形态：
- 声明了但没消费的配置键（**本例**）；
- 框架接管了某个开关，配置文件里的那份没人读；
- 测试环境的配置只改了一半。

### 为什么这在本项目特别危险

| 案例 | 全部测试的结果 | 实际漏掉的东西 |
|---|---|---|
| `article-rpc` 101 条全绿 | 🟢 全绿 | jsonb 列**恒 `null`**（PF-ARTICLE-RPC-001） |
| `admin-rpc` 绿测试 | 🟢 全绿 | 把 `update_time` **不推进**锁死了 |
| `message-rpc` 21 条全绿 | 🟢 全绿 | 模块**一次都没真正启动过** |
| **本例** | 🟢 全绿 | 4 处「配置说了 A、实际生效的是 B」，长期无人察觉 |

⇒ 最贵的后果不是「测试没抓住」，而是**运维在生产上按错误的假设操作**：
本例的直译就是「以为流量已经切到新 broker，实际还连在老 broker 上」。

### 下次怎么防

1. **配置类差异不能靠行为测试守，要靠「启动即失败」的守卫守。**
   守卫本身也要有**守恒断言**（覆盖全部 N 处，而不是抽查两处 ——
   本轮 `everyOneOfTheFourChannelsIsGuarded` 就是为此补的）。

2. **守卫要挂在「对外收流量之前」的钩子上。**
   本轮用 `@PostConstruct`（Bean 初始化期）⇒ 早于端口绑定、早于 `SmartInitializingSingleton`
   阶段的 schema 门禁 ⇒ 配置分叉时根本不会「先收流量再报错」。
   ⚠️ 与 `PF-ARTICLE-RPC-010` 呼应：**钩子选错 ⇒ 校验点落在错误的生命周期阶段**。

3. **守卫的报错必须能独立看懂**：哪个通道、实际值、全局值、
   「这个值不会生效」、**Go 出处（文件:行号）**、以及出路。
   ⇒ 因为它往往是你**唯一一次**告诉运维「你理解错了」的机会。

4. **别让守卫误伤合法部署**：先证明「当前配置下守卫必然通过」——
   本轮用「四个通道同源（同一个 `${KAFKA_BROKERS:...}` 占位符）」这个前提
   ＋ 一条**读 shipped yml** 的断言（`shippedYamlKeepsAllFourChannelsOnTheSameBrokerPlaceholder`）
   把这个前提钉死 ⇒ 守卫不可能误伤。

5. ⚠️ **「守卫装上后立刻翻出旧矛盾」是好事，不是麻烦。**
   本轮它一次翻出 4 处 ⇒ 这 4 处正是被「静默忽略」掩盖的真实问题。
   ⇒ **不要因为「守卫让测试起不来了」就去放宽守卫** ——
   该做的是**把测试配置改自洽**（本轮 4 个类同时覆盖全局与四个通道）。

### 附：同一轮里踩到的第二条 —— 「仍全绿」的第一嫌疑是**变异没打中**

本轮变异验证中，「MUT-5（normalize 加 trim）」第一次跑出**仍全绿**。
第一反应通常是「测试不够好」，但实际是**变异本身无效**：

```java
// 原变异（无效）：只改了过滤条件
if (s != null && !s.trim().isEmpty()) { out.add(s); }   // ← add 的还是未清洗的值！

// 正确变异（有效）：条件与取值一起改
if (s != null && !s.trim().isEmpty()) { out.add(s.trim()); }
```

⇒ 变异**根本没实现 trim** ⇒ 测试当然不变红。
⇒ 与 PF-ARTICLE-RPC-012 里那条「三态判定」互补：
**「仍全绿」要先排除「变异无效」，再谈「测不到」。**
⇒ 本轮另一条 MUT-6 则是**真·测不到**（「全局为空就跳过」那个分支零覆盖）
⇒ 补了一条用例后才转红 ⇒ 这就是「批量改 N 处必须配一条覆盖全部 N 处的守恒断言」的具体形态。

---

## PF-ARTICLE-RPC-014 —— 「ORM 的字段策略」属于**只能实测**的层，读文档必然推错

### 现象（本轮实际发生的一件事）

台账里挂了一条「未展开」的相邻差异，推理链是：

> Go 的 `Save` 走 `finisher_api.go` 的 `Selects = append(Selects, "*")`
> ——「update 时使用**全部**字段，**含零值字段**」；
> MP `updateById` 默认 `updateStrategy = NOT_NULL`，**只写非 null 字段**。
> ⇒ 一个「把某列显式置为**零值**」的更新，**两侧结果不同**。

本轮把它展开，实测（Go 侧 gorm v1.31.1 真 PG 探针 + RAG 侧真 PG 对拍）：

| 侧 | 探针/用例 | 实测 |
|---|---|---|
| Go | 探针 C：`Brief=""` / `ViewCount=0` 后 `Save` | 写成 **`''` / `0`** |
| RAG | 用例 1：同场景 `updateById` | 写成 **`''` / `0`** |

⇒ ⭐ **两侧在零值上本来就一致** ⇒ **那条差异的主要论据直接不成立**。

⚠️ 错在哪：`NOT_NULL` 跳过的只是 **null**；
**零值（`""` / `0` / `false`）不是 null** ⇒ 照样进 SET。
（`int` 计数列更是**原始类型，永远非 null** ⇒ 一定会被写。）

### 根因（不是 MP 的错，是**推理的错**）

「`NOT_NULL` = 只写非 null」这句话本身是对的，
但它**只说明「null 会被跳过」**，**不蕴含「零值会被跳过」** ——
而「零值被跳过」这一步是**推出来的、没验过的**。

⇒ 更一般地说：
> **凡是需要知道「哪些列进了 SET」的判断，都是「只能实测」的层。**
> 声明点（注解名 / 配置项 / 文档）**只告诉你策略叫什么**，
> 不告诉你**这一次 UPDATE 实际写了哪些列**。

⇒ 而这条恰好是本项目最容易被「读文档就下结论」坑到的层：
ORM 的行为在本仓已经反复「意外」过 ——
GORM `Save` 在 0 行时**退化成 `Create` 再 `ON CONFLICT DO UPDATE`**（不是报错）、
MP `publishPercentileHistogram` 是「**额外**塞一整套默认桶」、
MP 的 `@ResultMap` 缺失时**静默跳过该属性**。

### 为什么这在本项目特别危险

台账里这条推理一旦成立，就会去登记一条 **`SEMANTIC MISMATCH`** ——
而按 AGENTS.md §1「行为以 Go 为准」，**登记在案就要修** ⇒ **照着一条错推理去改代码，
会制造一个不存在的新漂移**。

⇒ 本轮同款：「DRIFT-004 的相邻差异」原本的措辞是
「**这才是真实差异**」—— 若不复核就照着修，
会把「RAG 少写一个零值」这种**不存在**的差异修出来。

### 下次怎么防

1. **ORM 语义（哪些列进 SET / 哪些被跳过）⇒ 真 PG 上「写一行 → 读回来」。**
   最省事的形态：
   - Go 侧：一个探针程序，用**仓库里那个版本的** ORM + 真 PG，写完把**原始行** dump 出来
     （⚠️ 记得区分「SQL NULL」与「jsonb `'null'`」—— 用 `IS NULL` + `::text` + `pg_typeof` 三列一起看）；
   - RAG 侧：一个真 PG 用例，**真 Mapper**（不是 mock、不是 H2），写完同样 dump 原始行。

2. **dump 原始行时，区分「值的三种形态」**：SQL NULL / jsonb `'null'` / jsonb `'[]'`。
   只看 Java 侧读回的值会把它们**全部看成同一个 null** ⇒ 差异就看不见了。

3. **引用依赖版本之前，先读 `go.mod` / `pom.xml` 确认真实版本。**
   本轮就踩到：台账引 `gorm@v1.31.2`，仓库实际是 **v1.31.1** ⇒ 行号也全错。
   （⚠️ 同一个 Go 仓库的 `like` / `users-*` 三个域台账里也有同类错引用，已登记为跨域待办。）

4. **「不可观察」也是一个结论，必须正面证明。**
   本轮的完整结论不是「差异不存在」，而是：
   **存储表示不同（SQL NULL vs jsonb `'null'`）⇒ 但读回一致 + 过滤一致 ⇒ API 不可观察。**
   ⇒ 「读回一致」和「过滤一致」**各要一条断言**，少一条结论就不完整。

5. ⇒ 落成一句可复用的判据：
   **「读文档」得到的是策略名，「真 PG 写一行」得到的才是这次 UPDATE 实际写了什么。**

---

## PF-ARTICLE-RPC-015 —— 「同一列」在两侧可能存着**不同的物理形态**，而 wire 层会**自动抹平**它

### 现象（本轮实际发生的一件事）

Go 与 RAG 写**同一张** `article` 表，但**同一列**的落库形态不同：

| 侧 | `secondary_tags` 的落库形态 | 原因 |
|---|---|---|
| Go | **jsonb `'null'`** | `create_article_logic.go:94` `SecondaryTags: model.StringArray(in.SecondaryTags)` —— **直接类型转换**（不是 `append(nil, ...)`）⇒ 空请求 ⇒ **nil slice** ⇒ `json.Marshal(nil)` = `"null"` |
| RAG | **jsonb `'[]'`** | `ArticleDomainService:144` `new ArrayList<>(in.getSecondaryTagsList())` ⇒ 空 list ⇒ **非 null** ⇒ MP 的 `NOT_NULL` 照样写 ⇒ `'[]'` |

⇒ **灰度期两侧写同一张表 ⇒ 同一列里两种形态并存。**

⚠️ 而 RAG 读 Go 建的行时：`readList("null")` ⇒ **null** ⇒ 映射到 proto 时
（`repeated string`）⇒ **字段不存在**；Go 读自己建的行时：`SecondaryTags` 是 nil
⇒ proto3 `repeated`（**无 presence**）⇒ **字段不存在**。

⇒ **wire 字节逐字节相同。**

### 根因（不是 bug，是**三个独立事实叠在一起**）

1. **两侧的「空」表示不同**：Go 的空 = **nil**、RAG 的空 = **空集合** ⇒ 落库形态不同；
2. **proto3 的 `repeated` / `map` 没有 presence** ⇒ 「空集合」与「不存在」在 wire 上**不可区分**；
3. **两侧的映射都把 null/空处理成「不写入」** ⇒ 于是两侧在 wire 上又**对齐了**。

⇒ 三者叠加 ⇒ **存储层有差异、wire 层没差异。**

### 为什么这在本项目特别危险

这类差异**极难被测出来**，因为：

- **行为测试测不到**：两条路径（读 Go 建 / 读 RAG 建的行）产出的 wire **完全相同** ⇒ 断言相等永远是绿的；
- **DDL 对照测不到**：DDL 两侧一致（列都是 `jsonb`）⇒ DDL diff 不会报；
- **存储层对拍测不到**：只有直接查 `secondary_tags::text` 才能看到 `'null'` vs `'[]'`。

⇒ 而本仓的审计流程里，**恰最容易跑的就是前两种**。

### 下次怎么防

1. **跨栈写同一张表时，先把「两侧的空怎么表示」列出来。**
   本例的清单：nil slice / 空 slice / 空字符串 / 空 map / SQL NULL / jsonb `'null'` / jsonb `'[]'`。
   ⇒ 两侧的表示**只要有一处不同**，落库形态就可能不同。

2. **评估「存储层差异能不能被看到」时，要一路追到最外面那一层。**
   中间任何一层的「无 presence」语义都可能把它**自动抹平**（本例是 proto3）。
   ⇒ 追到**用户真正拿到的东西**（本例是 wire 字节）才算判完。

3. **反过来也要记住**：**没有 presence 的字段会把「空」与「缺」混为一谈**
   （proto3 的 `repeated`/`map`、可空标量）。
   ⇒ 想区分它们，只能在**更靠近存储的那一层**去判。

4. **要留证据时，用「字节级」而不是「集合相等」。**
   本轮的新网 `ArticleNullJsonbToProtoMappingTest` 就是比
   `expected.toByteArray()` vs `mapped.toByteArray()` ——
   ⇒ 「空集合相等」只能证明 Java 层一样，**证不了 wire 一样**。

5. ⇒ 落到一条可复用的判据：
   **「存储形态不同」≠「用户能看到不同」。**
   两者之间隔着**若干层可能自动抹平的语义**（proto3 无 presence、
   JSON 空值、空集合归一……）⇒ 判「是否可观察」必须**逐层过**。

---

## PF-ARTICLE-RPC-016 🔴 `EOFException` 出现在 `PGStream.receiveChar` / `ConnectionFactoryImpl.enableSSL` ⇒ **不是「PG 挂了」**，是「**回应我的那个端口上不是我要的那个 PG**」

> **来源**：2026-10-28 · G1 lane 通报 ⇒ G3 本域独立复核后登记。
> ⚠️ **本域暴露面**：`article-rpc` 有 **9** 个测试文件用 Testcontainers PG ·
> `comment-rpc` 有 **11** 个 ⇒ ⇒ 这两域最易撞；`favorite-api` / `hot-rpc` 实测 **0** 个 ⇒ **不暴露**。

### 现象

跑批（全量或整域回归）时出现 PG 连接层异常，栈顶形如
`java.io.EOFException` @ `org.postgresql.core.v3.ProtocolConnection` / `PGStream.receiveChar`
· `org.postgresql.ssl.NonBlockingIO SSLReader` / `ConnectionFactoryImpl.enableSSL`
⇒ ⇒ 读起来像「**PG 服务挂了**」⇒ ⇒ 于是**第一反应会去改业务代码**（或改 DDL）⇒ ⇒ **改错方向**。

### 根因

**不是 PG 进程的问题，是「端口对错了」**：
- Testcontainers 抢的**随机宿主口**落在 **OS 临时端口段**（macOS `49152–65535`）
  ⇒ ⇒ 而**手工起的对拍容器**（如 `difftest5k-*`）发布的宿主口**也在同一段**
  ⇒ ⇒ 两者抢到同一个口 ⇒ ⇒ **连到了一个「不是我要的那个 PG」的监听者**
  ⇒ ⇒ PG 协议握手失败 ⇒ ⇒ 抛 `EOFException`（连接被对端立刻关掉，不是「连不上」）。
- ⚠️ 这与「端口被占 ⇒ 连不上」是**不同**的形态：这里 `create_connection` **成功**了，**失败发生在协议握手阶段**。

### 🔴 判据（三条齐 ⇒ 才可定性为环境）

1. **同一测试类单独复跑能过**；
2. **本域零代码变更**（本轮 diff 为空）；
3. **共享层变更面不涉及本域**。

⇒ ⇒ 三条齐 ⇒ 是环境 flake ⇒ **不是回归**。
⇒ ⇒ 🔴 **「重跑绿了」≠「已修」** —— 它只说明**这次没撞上**，不说明问题不存在。

### 🟢 同源旁证（两条独立 lane 撞同一个 ⇒ 铁证是环境不是代码）

- **G4** 的第十四次全量撞的是**同一类**（`-rf :common` 续跑 ⇒ `common` 没复现）；
- **G1** 域也撞了（`admin-rpc` 5 条 error ⇒ 单独复跑 **5/5** 绿）。

### 根因治理（**落码是各域自己的活**）

G4 已分配 **Testcontainers 宿主口段**（全域 `22700–22999` 按域分子段）：
`users 22700–22729` · `follow 22730–22759` · `message 22760–22789` · `like 22790–22819` ·
`task 22820–22849` · **article `22850–22879`** · **comment `22880–22909`** ·
**favorite `22910–22939`** · **hot `22940–22969`** · 预留 `22970–22999`。

⇒ ⇒ **钉口是各域自己的落码项** ⇒ G1 已把落码口径写死三条：
① **不写死端口**（同段内要能并行）② **占用要可自愈** ③ **改后必须复跑全量**。
⇒ ⇒ ⚠️ **Testcontainers 没有「宿主口段」概念**（配置项零个 `port`）⇒ ⇒ 只能逐容器固定口
⇒ ⇒ 正确形态 ＝ `PostgreSQLContainer` ＋ `withCreateContainerCmdModifier(...)` 显式绑口
⇒ ⇒ 🔴 **「显式绑口后 `getMappedPort()` 是否返回那个固定口」尚未实测** ⇒ ⇒ **域侧落地必须先写探针验证，不能照抄**。

### ⚠️ 顺带一条探针用法坑

`ps -p <端口>` **查不到 ≠ 进程死了**（端口与 pid 是**两个命名空间**）
⇒ ⇒ 查进程要用 `lsof -nP -iTCP -sTCP:LISTEN -a -p <pid>` **反查**。
⇒ ⇒ G1 曾把端口当 pid 记进自己台账 ⇒ 已订正 ⇒ ⇒ 本域写台账时**别把端口当 pid 记**。

