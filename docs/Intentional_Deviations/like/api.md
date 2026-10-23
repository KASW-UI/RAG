<!-- @new 2026-10-23 · like 域 api 层签字偏离 -->

# `like` 域 api 层签字偏离

> 归类：**「有意偏离」+「表述差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ 读之前先记住：**这是决策，不是缺陷。** 审查时不必重复质疑，**也不得「顺手改回 Go 的旧写法」**。

---

## ⏳ 待签字（2026-10-23 修复轮提出，**用户尚未确认**）

> ⚠️ **本节两条尚未获得用户签字**，故**不计入**「已签字偏离」总数。
> 保留在此是为了让下一轮复核**不会把它们当成新发现重新提一遍**。
> 处置依据见 [`../../DRIFT/like/api.md`](../../DRIFT/like/api.md) 的 `DRIFT-LIKEAPI-021 / -022`。

### ⏳ SD-2026-10-23-08（候选）：响应 `Content-Type` 不带 `charset=utf-8`

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（台账 §7.2 建议登记，尚未获批） |
| 原台账编号 | DRIFT-LIKEAPI-021（OBSERVABLE DIFFERENCE，🟢 LOW） |
| 组件 | `service/like/like-api`（6 个端点的成功与业务错误响应） |
| 性质 | **表述差异**（台账已判定语义等价） |

**差异**

| 侧 | `Content-Type` |
|---|---|
| Go | `application/json; charset=utf-8`（分号后**有**空格） |
| RAG | `application/json`（**无** charset 参数） |

**为什么不修**
按 RFC 9110，`application/json` 的 charset 参数**默认即 UTF-8** ⇒ 省略与显式声明 `utf-8`
**语义等价**；且省略后的 OWS 按规范不显著。
⚠️ 强行对齐会变成「与容器（Tomcat）对抗」—— `StringHttpMessageConverter` 的产出格式由容器决定。

**影响面**
仅当客户端做**字节级**的 Content-Type 断言时才会红。
⚠️ 本仓库现有测试用 `contentTypeCompatibleWith` / `MediaType.parse` 比对，**不受影响**。

**若将来出现按原始字符串比对的客户端** ⇒ 需要重新评估（那时它就不再是「表述差异」了）。

---

### ⏳ SD-2026-10-23-09（候选）：worker 池饱和时的**裸 503**

| 项 | 值 |
|---|---|
| 状态 | ⏳ **待签字**（台账 §7.2 建议登记，尚未获批） |
| 原台账编号 | DRIFT-LIKEAPI-022（OBSERVABLE DIFFERENCE，🟢 LOW） |
| 组件 | `service/like/like-api`（全部 6 个端点） |
| 性质 | **有意偏离**（RAG 独有的实现能力，Go 侧**不存在对应物**） |

**差异**

| 侧 | 行为 |
|---|---|
| Go | 每请求一个 goroutine（初始栈 2KB）、**无上限** ⇒ **不存在**「worker 池饱和」这个事件 |
| RAG | `HttpMiddleware` worker 池上限 **512**（`MAX_HTTP_WORKERS`）；饱和 ⇒ `RejectedExecutionException` ⇒ **裸 503，无 body、无 Content-Type** |

⚠️ 形状刻意与「超时 503」不同 —— 超时那条**有 body**，这条没有。

**为什么保留**
① 触发窗口窄：512 远大于 Tomcat 默认 `max-threads`（200），正常流量不该触发；
② `HttpMiddleware` 的源码注释已自陈这是「『拿不到 ctx 传播』这件事的兜底，
**不是 Go 语义的一部分**」—— 即它是**为框架限制而生的兜底**，不是对 Go 的误解。

**代价（必须随本条一起读）**
高并发 + 下游卡死时，RAG 会出现 Go 不会出现的裸 503。

---

## ✅ 已签字（同域）

| 编号 | 内容 |
|---|---|
| SD-2026-10-23-05 | `like.proto` 的 proto `package` 由 `pb` 归一为 `like`（见 [`proto.md`](proto.md)） |

## ✅ 已签字（like-rpc 侧，见 [`rpc.md`](rpc.md)）

| 编号 | 内容 | 性质 |
|---|---|---|
| SD-2026-10-23-06 | gRPC 业务码从 `grpc-status` 迁移到自定义 trailer `rag-like-biz-code` | **框架限制** |
| SD-2026-10-23-07 | Nacos 注册失败时**不退出进程**（Go 是 `os.Exit(1)`） | **有意偏离** |