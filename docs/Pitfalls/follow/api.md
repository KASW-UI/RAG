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
