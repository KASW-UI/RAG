# Go → Spring Boot：基础组件语义一致性验证 Prompt

> 本文件保留原始基础组件验证 Prompt 的全部主体检查项，并将验证方向明确为
> Go 参考实现 → Spring Boot 目标实现。请将本文件整体交给 Agent
> 使用，不要只摘取部分章节。

---

## ⚠️ 执行前必读：两处执行环境校正（非检查项删减）

以下两处是**让任务在本机可执行**所必需的技术校正，检查项本身一条未删。

### 校正 1：Go 参考仓库路径

原始 Prompt 写的 `/Users/pr/Documents/GitHub/Sea-RideTheWind_golang`
**在本机不存在**（`ls` 报 No such file or directory，AGENTS.md §7.1 已记录此校正）。

**真实路径**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`

本机无 `/Users/pr/Documents/GitHub/` 目录。Agent 一律使用上面的真实路径。

### 校正 2：交付位置改为 AGENTS.md 台账

原始 Prompt §31 要求输出一个 7 文件目录。但本项目的台账结构由 `AGENTS.md` §0 规定
为 `<域>/<api|rpc>.md` 双档：`docs/DRIFT/`（记差异）+ `docs/Pitfalls/`（记教训）。

**Agent 必须把 §32–§37 的分析维度全部保留，但组织进下面的台账文件**（明细见 §31-b）。

---

## 本次迁移的固定上下文

-   **Go 参考仓库**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`（见校正 1）
-   **Spring Boot 目标仓库**：`/Users/pr/Documents/java&golang/RAG`
-   **验证方向**：Go → Spring Boot。Go 用于还原已有行为基准；Spring Boot
    是待验证的迁移实现。
-   **任务性质**：只检查、分析与报告，**不修改源码**、不自动修复问题。
-   **目标**：在相同输入、等价外部环境与可比依赖状态下，判断 Spring Boot
    是否保留了 Go 实现中需要保留的可观察行为与业务语义。
-   **不要求结构一一对应**：不要求文件、类、方法、字段、包、调用层级相同。Spring
    Boot 可以用 Java/Spring
    的原生机制实现相同语义，但必须检查这些机制在目标项目中是否真实配置并生效。
-   **框架吸收的证据标准**：不能仅凭存在
    `@Transactional`、`@Configuration`、`@Component`、拦截器、Spring
    Security、Mapper 或某个 Bean
    就推断责任已经被承担；需要追踪真实配置、调用路径、代理生效条件、异常行为与运行证据。
-   **一致性与业务正确性分开判断**：Go 与 Spring Boot
    行为一致，不代表行为本身一定符合业务要求；行为不同，也不自动意味着不可接受。两项判断必须分别给出结论。
-   **字段差异规则**：字段、DTO、实体、配置对象或 API
    结构不同本身不构成语义不一致。只有当差异改变了输入解释、默认值、持久化、协议、结果、状态或副作用时，才作为行为差异继续验证。

以下主体内容继承原始 Prompt
的分析顺序、操作级检查、差异分类、测试思维、证据记录和验收纪律。除上述迁移方向与目标技术上下文外，不得以"改写"为理由删减检查项。

------------------------------------------------------------------------

只是检查，不做任何修改，对象是刚刚创建的模块，结果在会话中释放：# Go →
Spring Boot：基础组件语义一致性验证

## 0. 任务目标

你正在验证一个已经分别用 Go 与 Spring Boot
实现的**基础组件**，判断两边是否保持了相同的行为语义。

注意：

这不是：

-   Go → Spring Boot 代码翻译检查
-   Spring Boot 代码风格检查
-   Java/Spring 最佳实践检查
-   API 是否一一对应检查
-   类 / 方法 / package 是否一一对应检查
-   "Java 看起来像 Go"检查

你的唯一核心问题是：

> **给定相同的有效输入、相同的外部环境和相同的依赖状态，Go 组件和 Spring Boot 组件是否表现出相同的语义行为？**

也就是说：

``` text
Go Component
     ↓
Behavior
     ↑
Spring Boot Component
```

需要验证的是两者的 **Behavior 是否等价**，而不是实现方式是否相同。

------------------------------------------------------------------------

# 1. 验证对象

本次只验证：

``` text
<TARGET_COMPONENT>
```

例如：

``` text
Redis Cache Component
JWT Authentication Component
Database Repository Component
RPC Client Component
MQ Producer Component
Configuration Component
Error Mapping Component
Transaction Component
```

如果 `<TARGET_COMPONENT>` 内部又包含多个明显独立的基础组件，可以拆成：

``` text
COMPONENT-01
COMPONENT-02
COMPONENT-03
```

但不要扩大到整个业务系统。

------------------------------------------------------------------------

# 2. 严格范围

必须分析：

### Go 组件

-   Go component source
-   public API
-   internal logic
-   configuration
-   initialization
-   dependency
-   error handling
-   state
-   lifecycle
-   concurrency
-   timeout
-   retry
-   cache
-   transaction
-   serialization
-   external interaction

### Spring Boot 组件

-   Spring Boot component source
-   public API
-   Spring configuration
-   initialization
-   internal logic
-   dependency
-   error handling
-   state
-   lifecycle
-   concurrency
-   timeout
-   retry
-   serialization
-   external interaction

### 必须追踪到

> **足以判断组件行为的依赖边界。**

例如：

``` text
Java CacheService
    ↓
RedisTemplate
    ↓
Redis
```

不需要分析 Redis 本身。

但必须知道：

-   Java 调用了什么 Redis 操作
-   Go 调用了什么 Redis 操作
-   参数是否一致
-   返回结果如何处理
-   failure 如何处理

------------------------------------------------------------------------

# 3. 核心验证模型

不要直接比较代码。

先分别建立：

``` text
Go Component Model
```

和：

``` text
Spring Boot Component Model
```

然后比较：

```text
Input
  ↓
Precondition
  ↓
Processing
  ↓
External Interaction
  ↓
Result Mapping
  ↓
State Change
  ↓
Error / Failure
  ↓
Observable Behavior
```

最终判断：

```text
Go Semantic Model
        ⇅
Spring Boot Semantic Model
```

------------------------------------------------------------------------

# 4. 第一阶段：确定 Component Contract

首先回答：

``` text
Component:
Purpose:

这个组件解决什么问题？

它向调用方提供什么能力？

它依赖什么外部基础设施？

它维护什么状态？

它是否有副作用？

它的成功意味着什么？

它的失败意味着什么？
```

不要从类名推断。

必须根据实际代码确认。

------------------------------------------------------------------------

# 5. Public Contract

列出组件对外暴露的实际能力。

例如：

``` text
COMPONENT API

Operation:
Get(key)

Input:
key

Output:
value / not found / error

Side Effect:
None

External Dependency:
Redis
```

但是不要停留在：

``` text
Get()
Set()
Delete()
```

必须继续分析每个 operation 的行为。

------------------------------------------------------------------------

# 6. Operation-Level Analysis

对每一个重要 operation：

``` text
OP-01
OP-02
OP-03
...
```

建立完整模型。

格式：

```text
OP-ID:
Operation:

Purpose:

Input:

Precondition:

Trigger:

Processing:

External Calls:

External Call Parameters:

Result:

State Change:

Side Effects:

Error:

Timeout:

Retry:

Concurrency:

Ordering:

Observable Behavior:

Evidence:
```

------------------------------------------------------------------------

# 7. Go 行为模型

先单独分析 Go。

不要同时看 Java 就急着比较。

建立：

``` text
GO-SEMANTIC-MODEL
```

回答：

### Input

输入是什么？

包括：

-   参数
-   context
-   headers
-   metadata
-   configuration
-   internal state

------------------------------------------------------------------------

### Precondition

调用前必须满足什么？

例如：

```text
key != empty
connection initialized
transaction active
authenticated context exists
```

------------------------------------------------------------------------

### Processing

真正执行了哪些行为？

按语义顺序描述：

```text
1.
2.
3.
4.
```

不要复制 Go API。

例如不要写：

``` text
调用 redis.Client.Get()
```

而写：

```text
根据 key 查询缓存；
如果存在则返回缓存值；
如果不存在则进入 miss 分支。
```

API 可以作为 Evidence，但不是 Semantic Model。

------------------------------------------------------------------------

# 8. External Interaction

如果组件访问外部基础设施，必须记录：

```text
External System:

Operation:

Input:

Parameters:

Expected Result:

Failure Result:

Does Result Affect Branch:

Does Failure Block Operation:

Side Effect:
```

例如 Redis：

```text
External System:
Redis

Operation:
GET

Input:
cache key

Result:
value / nil

Semantic:
nil 表示 cache miss，而不是 error。

Failure:
Redis connection failure
→ operation failure
→ 不得被解释成 cache miss
```

这类区别必须重点检查。

------------------------------------------------------------------------

# 9. Result Semantics

必须区分：

```text
Success
Not Found
Empty
Null
Default
Failure
Timeout
Unauthorized
Invalid Input
Dependency Failure
```

不要假设这些概念相同。

例如：

```text
Redis:
nil
```

可能意味着：

```text
Cache Miss
```

而不是：

```text
System Failure
```

如果 Spring Boot 实现把：

```text
null
```

转换成：

```text
Exception
```

就可能发生语义变化。

------------------------------------------------------------------------

# 10. Error Semantics

这是重点验证项。

对每一种重要 failure 建立：

```text
ERROR FLOW

Failure:
触发条件：

Go:
如何处理？

Go:
最终输出什么？

Spring Boot:
如何处理？

Spring Boot:
最终输出什么？

Semantic Equivalent:
YES / NO / UNKNOWN
```

必须区分：

```text
Error
  ↓
Terminate
```

和：

```text
Error
  ↓
Fallback
```

以及：

```text
Error
  ↓
Retry
```

以及：

```text
Error
  ↓
Continue
```

------------------------------------------------------------------------

# 11. Error Identity

检查 Go 与 Spring Boot 是否保持：

```text
Error Category
Error Code
Error Meaning
Error Propagation
Error Mapping
Error Visibility
```

例如 Go：

```text
ErrNotFound
```

Java：

```text
Optional.empty()
```

这不一定是问题。

关键是：

> 调用方看到的语义是否一致？

如果：

```text
Go:
NotFound → continue

Spring Boot:
NotFound → throw RuntimeException
```

则：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

# 12. State Semantics

如果组件有状态，必须建立：

```text
Initial State
    ↓
Operation
    ↓
Condition
    ↓
New State
```

例如：

```text
Cache

MISS
 ↓
Load
 ↓
SUCCESS
 ↓
WRITE CACHE
 ↓
HIT
```

比较 Go / Spring Boot：

```text
Go State Flow
vs
Java State Flow
```

重点检查：

-   state transition
-   initial state
-   default state
-   invalid state
-   expiration
-   cleanup
-   persistence

------------------------------------------------------------------------

# 13. Cache Semantic Equivalence

如果目标组件涉及 Cache，必须专门验证：

```text
Cache Key
Key Construction
TTL
Expiration
Read Order
Write Order
Delete Order
Cache Hit
Cache Miss
Negative Cache
Serialization
Deserialization
Cache Failure
DB Fallback
Invalidation
```

尤其检查：

```text
Go:
Cache Miss → Query DB → Write Cache

Spring Boot:
Cache Miss → Query DB
```

如果 Java 少了 Write Cache：

> 这不是实现差异，而是行为差异。

------------------------------------------------------------------------

# 14. Authentication / Authorization Semantic Equivalence

如果组件涉及身份或权限，必须验证：

```text
Identity Source
Token Parsing
Claim Extraction
Claim Default
Token Expiration
Signature Validation
Role
Permission
Resource Ownership
Failure Mapping
```

例如：

```text
Go:
userId ← JWT claim

Spring Boot:
userId ← request parameter
```

即使最终 Java API 看起来能正常工作，也属于：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

# 15. Transaction Semantic Equivalence

如果组件涉及事务，比较的不是：

```text
Go Begin/Commit
vs
Java @Transactional
```

而是：

> **两边是否保护了相同的业务/数据操作集合？**

建立：

```text
Transaction Boundary

Start:
...

Operations Inside:
...

Commit Condition:
...

Rollback Condition:
...

Operations Outside:
...
```

然后比较：

```text
Go Transaction Boundary
        ⇅
Java Transaction Boundary
```

特别检查：

```text
Operation A
+
Operation B
```

Go 是否保证：

```text
A + B
all success
OR
A + B
all rollback
```

而 Java 是否变成：

```text
A commit
↓
B fail
```

如果是：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

# 16. Concurrency Semantic Equivalence

不要比较：

```text
sync.Mutex
vs
synchronized
```

而要比较：

> **并发执行时最终允许出现什么行为。**

检查：

```text
Concurrent Calls
Ordering
Mutual Exclusion
Atomicity
Duplicate Execution
Race
Lost Update
Visibility
Idempotency
```

例如：

```text
Go:
两个并发请求不能同时初始化同一个资源。

Spring Boot:
两个请求可以同时初始化。
```

即使 Java 没有使用 Mutex，也属于语义变化。

反过来：

> Go 使用 Mutex，但 Java 使用其他机制实现完全相同的并发约束。

这是：

```text
SEMANTIC EQUIVALENT
IMPLEMENTATION DIFFERENT
```

不是问题。

------------------------------------------------------------------------

# 17. Timeout Semantic Equivalence

比较：

```text
Timeout Source
Timeout Duration
Timeout Scope
Timeout Propagation
Timeout Result
```

例如：

```text
Go:
RPC timeout = 3s

Spring Boot:
HTTP client timeout = 3s
```

不能直接认为一致。

必须判断：

```text
3s 是整个 operation 的 timeout
还是单次 network read timeout？
```

如果 scope 不同：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

# 18. Retry Semantic Equivalence

比较：

```text
Retry Trigger
Retry Count
Backoff
Retryable Errors
Non-Retryable Errors
Retry Scope
Final Failure
```

例如：

```text
Go:
timeout → retry
validation error → no retry

Spring Boot:
所有 RuntimeException → retry
```

属于语义不一致。

------------------------------------------------------------------------

# 19. Serialization Semantic Equivalence

如果组件处理：

-   JSON
-   Protobuf
-   Redis value
-   MQ payload
-   RPC request
-   RPC response

必须比较：

```text
Field Mapping
Field Name
Field Type
Default Value
Null
Empty
Missing Field
Enum
Timestamp
Number
Encoding
Unknown Field
```

尤其注意：

```text
null
vs
empty
vs
missing
```

以及：

```text
0
vs
null
```

这些经常造成跨语言语义偏差。

------------------------------------------------------------------------

# 20. Configuration Semantic Equivalence

比较：

```text
Config Source
Default Value
Required / Optional
Environment Override
Parsing
Unit
Validation
Runtime Reload
```

例如：

```text
Go:
timeout = 5 seconds

Spring Boot:
timeout = 5 milliseconds
```

这不是配置风格差异。

是语义错误。

------------------------------------------------------------------------

# 21. Lifecycle Semantic Equivalence

检查：

```text
Initialization
Startup
Ready
Shutdown
Cleanup
Connection Close
Worker Stop
Resource Release
```

例如：

```text
Go:
startup → initialize Redis → start worker

Spring Boot:
startup → start worker → initialize Redis
```

如果 worker 可能在 Redis 初始化前执行：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

# 22. Observable Behavior

最终比较真正可观察的行为：

```text
Input
 ↓
Output
 ↓
Error
 ↓
External State
 ↓
Side Effects
```

包括：

-   return value
-   error
-   status
-   DB state
-   cache state
-   MQ message
-   RPC call
-   retry count
-   timing constraint
-   state transition

------------------------------------------------------------------------

# 23. 不要把"实现不同"误判成"语义不同"

例如：

```text
Go:
error return

Spring Boot:
exception
```

不自动判定错误。

必须继续问：

```text
调用方最终看到的行为是否一致？
```

同样：

```text
Go:
interface

Spring Boot:
interface
```

也不代表语义一致。

真正需要比较的是：

```text
Behavior
```

------------------------------------------------------------------------

# 24. Semantic Equivalence Test

对每个 Operation 做：

```text
                    Go                    Java

Input               X                       X
Precondition        X                       X
Processing          X                       X
External Call       X                       X
Branch              X                       X
Result              X                       X
State Change        X                       X
Error               X                       X
Side Effect         X                       X
Timeout             X                       X
Retry               X                       X
Concurrency         X                       X
```

然后给出：

```text
EQUIVALENT
MINOR DIFFERENCE
SEMANTIC MISMATCH
UNKNOWN
```

------------------------------------------------------------------------

# 25. 差异分类

所有差异必须分类。

## A. Implementation Difference

实现不同，但语义相同。

例如：

```text
Go error
vs
Java exception
```

只要最终行为一致：

```text
IMPLEMENTATION DIFFERENCE
```

------------------------------------------------------------------------

## B. Observable Difference

外部可以观察到差异。

例如：

```text
Go:
cache miss → DB fallback

Spring Boot:
cache miss → error
```

这是：

```text
OBSERVABLE DIFFERENCE
```

------------------------------------------------------------------------

## C. Semantic Mismatch

差异会改变系统应该如何行为。

例如：

```text
Go:
NotFound → continue

Spring Boot:
NotFound → terminate
```

这是：

```text
SEMANTIC MISMATCH
```

------------------------------------------------------------------------

## D. Uncertain

证据不足。

```text
UNKNOWN
```

不要猜。

------------------------------------------------------------------------

# 26. Counterfactual Test

对于发现的每个差异，问：

> 一个正常的 Java/Spring 开发者，如果没有 Go
> 源码，仅根据普通需求，是否很自然地写成现在这个 Spring Boot 行为？

如果：

```text
YES
```

则说明：

> 这是迁移过程中真正危险的语义漂移。

标记：

```text
HIGH-RISK DRIFT
```

例如：

```text
Go:
Redis miss → DB fallback

Spring Boot:
Redis miss → return null
```

这很容易发生，因为 Spring Boot 实现本身"看起来合理"。

------------------------------------------------------------------------

# 27. Drift Record

每一个真正的语义偏差使用：

```text
DRIFT-XXX

Component:

Operation:

Category:

Go Behavior:

Java Behavior:

Difference:

Why It Matters:

Observable Impact:

Business / Technical Impact:

Likely Cause:

Must Preserve:

Recommended Verification:

Evidence:
```

------------------------------------------------------------------------

# 28. 正确的判断方式

不要写：

```text
Go 使用 X
Java 使用 Y
所以不一致。
```

必须写：

```text
Go:
Input
 ↓
Condition
 ↓
Action
 ↓
Result

Spring Boot:
Input
 ↓
Condition
 ↓
Action
 ↓
Result

Difference:
...

Semantic Impact:
...

Conclusion:
EQUIVALENT / MISMATCH
```

------------------------------------------------------------------------

# 29. Test Case 思维

如果可能，为每一个重要行为建立：

```text
CASE-001
Input:
...

Environment:
...

Go:
...

Spring Boot:
...

Expected Semantic Result:
...

Equivalent:
YES / NO
```

特别针对边界情况：

```text
正常输入
空输入
null
不存在
重复
超时
依赖失败
权限失败
并发
重复调用
部分失败
```

不要只测试 happy path。

------------------------------------------------------------------------

# 30. 最重要的测试集合

至少检查以下情况：

```text
CASE-01 正常成功

CASE-02 空输入

CASE-03 不存在

CASE-04 重复

CASE-05 外部依赖失败

CASE-06 超时

CASE-07 Retry

CASE-08 Cache Hit

CASE-09 Cache Miss

CASE-10 权限失败

CASE-11 并发调用

CASE-12 重复调用

CASE-13 部分操作成功

CASE-14 Transaction Rollback

CASE-15 Serialization Edge Case
```

根据目标组件实际能力选择适用项。

不要为了凑数量测试不存在的行为。

------------------------------------------------------------------------

# 31. 输出结构

### 31-a. 通用原则

交付物落在两处（本项目台账结构，见校正 2）：

| 目录 | 记什么 | 写法 |
|---|---|---|
| `docs/DRIFT/<域>/<api\|rpc>.md` | **差异本体**：Go 行为 / Java 行为 / 差异 / 影响 / 证据 | `DRIFT-XXX` 编号条目 |
| `docs/Pitfalls/<域>/<api\|rpc>.md` | **教训本体**：为什么会踩、框架机制如何静默失效、怎么防 | 踩坑条目 |

两者的分工见 `AGENTS.md` §0：**DRIFT 记差异，Pitfalls 记教训**。

⚠️ **只写有证据的条目。** 没有实际读过两边源码得出的结论，一律记进 `unresolved.md`
维度（§37），**不得**写成 `SEMANTIC MISMATCH`。

### 31-b. §32–§37 各维度落到台账的哪个小节

台账文件内部按下列顺序组织，保证 §32–§37 一个维度不丢：

```text
docs/DRIFT/<域>/<api|rpc>.md

# <组件名> 语义对照台账        ← 文件头：范围 + 核对日期 + 证据强度声明
## 1. Component Contract                    ← §4 + §5
## 2. GO-SEMANTIC-MODEL                      ← §7（逐 OP，含 §6 的 18 个字段）
## 3. SPRINGBOOT-SEMANTIC-MODEL              ← §7 / §34（与 Go 完全相同结构）
## 4. Equivalence Matrix                     ← §24 + §35（核心表，Result 只用 5 个枚举值）
## 5. External Interaction & Error Flow      ← §8 + §10
## 6. 逐维度结论                              ← §12–§21 每小节一个
                                              （State / Cache / Auth / Transaction /
                                               Concurrency / Timeout / Retry /
                                               Serialization / Config / Lifecycle）
## 7. Semantic Drifts                         ← §27 + §36（DRIFT-XXX 条目）
## 8. Test Cases                             ← §29 + §30（CASE-XXX 条目）
## 9. Unresolved                             ← §37（证据不足的，必填）
## 10. 最终判定                               ← §39 的 18 项 checklist + 三值结论
```

`docs/Pitfalls/<域>/<api|rpc>.md` 独立成文，只写**教训**：每个坑写成
「现象 → 根因（框架机制）→ 为什么测试抓不到 → 下次怎么防」。

------------------------------------------------------------------------

# 32. component-contract.md

描述：

```text
Component Purpose
Public Operations
Inputs
Outputs
Dependencies
State
Side Effects
Failure Model
```

这是组件的共同语义边界。

**落点**：台账 `## 1. Component Contract`。

------------------------------------------------------------------------

# 33. go-semantic-model.md

只描述 Go：

```text
Operation
Input
Precondition
Processing
External Interaction
Branch
Result
State
Error
Side Effect
Timeout
Retry
Concurrency
Lifecycle
```

**落点**：台账 `## 2. GO-SEMANTIC-MODEL`。

------------------------------------------------------------------------

# 34. springboot-semantic-model.md

使用与 Go 完全相同的结构。

目的：

> 保证比较时不会因为两边文档结构不同而遗漏维度。

**落点**：台账 `## 3. SPRINGBOOT-SEMANTIC-MODEL`。

------------------------------------------------------------------------

# 35. equivalence-matrix.md

核心输出。

格式：

  Operation   Semantic Dimension   Go    Java   Result       Evidence
  ----------- -------------------- ----- ------ ------------ ----------
  Get         Input                ...   ...    EQUIVALENT   ...
  Get         Cache Miss           ...   ...    MISMATCH     ...
  Get         Timeout              ...   ...    EQUIVALENT   ...
  Set         TTL                  ...   ...    MISMATCH     ...

Result 只能使用：

```text
EQUIVALENT
IMPLEMENTATION DIFFERENCE
OBSERVABLE DIFFERENCE
SEMANTIC MISMATCH
UNKNOWN
```

**落点**：台账 `## 4. Equivalence Matrix`。

------------------------------------------------------------------------

# 36. semantic-drifts.md

只记录真正需要关注的差异。

例如：

```text
## DRIFT-001

Component:
UserCache

Operation:
Get

Category:
Cache Failure Semantics

Go Behavior:
Redis unavailable → return dependency error

Java Behavior:
Redis unavailable → treat as cache miss → query DB

Difference:
Java hides infrastructure failure as cache miss.

Impact:
Database load increases and caller cannot distinguish Redis failure.

Classification:
SEMANTIC MISMATCH

Risk:
HIGH

Must Preserve:
Redis failure ≠ cache miss

Evidence:
...
```

**落点**：台账 `## 7. Semantic Drifts`。

------------------------------------------------------------------------

# 37. unresolved.md

只记录：

> 已经检查相关代码，但无法确定 Go 或 Java 的真实语义。

例如：

```text
UNRESOLVED-001

Question:
Redis timeout 是否应该触发 retry？

Go Evidence:
...

Java Evidence:
...

Why Unknown:
...

Impact:
...

Required Confirmation:
...
```

**落点**：台账 `## 9. Unresolved`（**此节不得为空才允许下结论**；
确实无未决项时写「本轮无」）。

------------------------------------------------------------------------

# 38. 最终判断原则

最终不要回答：

> "Java 和 Go 的代码看起来差不多。"

必须回答：

> **"在什么输入、什么依赖状态、什么并发/失败条件下，两边的行为相同或不同。"**

最终结论必须能够归纳成：

```text
Component
   ↓
Operation
   ↓
Behavior Dimension
   ↓
Go Behavior
   ↓
Java Behavior
   ↓
Semantic Comparison
   ↓
Risk
```

------------------------------------------------------------------------

# 39. 最终 Reconstruction / Equivalence Test

完成后执行最终检查：

```text
□ 两边的 public behavior 是否一致？

□ 正常路径是否一致？

□ 边界输入是否一致？

□ NotFound / Empty / Null 是否一致？

□ Error meaning 是否一致？

□ Error propagation 是否一致？

□ External dependency failure 是否一致？

□ Cache hit / miss 是否一致？

□ Transaction boundary 是否一致？

□ State transition 是否一致？

□ Side effects 是否一致？

□ Ordering 是否一致？

□ Retry 是否一致？

□ Timeout scope 是否一致？

□ Concurrency semantics 是否一致？

□ Authentication / Authorization 是否一致？

□ Serialization semantics 是否一致？

□ Lifecycle 是否一致？
```

如果全部通过：

```text
SEMANTICALLY EQUIVALENT
```

如果存在会改变行为的差异：

```text
SEMANTICALLY NOT EQUIVALENT
```

如果证据不足：

```text
UNRESOLVED
```

**落点**：台账 `## 10. 最终判定`（18 项逐条打勾/标注，不允许整体略过）。

------------------------------------------------------------------------

# 40. 核心原则

整个任务只遵循一个判断标准：

> **不要比较 Go 与 Spring Boot
> 是不是"写得一样"，比较的是它们在相同条件下"做出来的事情是不是一样"。**

因此：

```text
实现不同
≠
语义不同

实现相似
≠
语义一致
```

真正需要验证的是：

```text
Input
  ↓
Condition
  ↓
Behavior
  ↓
External Interaction
  ↓
State
  ↓
Output / Error / Side Effect
```

两边这一整条链一致，才叫：

> **Semantic Equivalence**

---

# 附：本项目的额外硬约束（来自 `AGENTS.md`，优先级高于 Prompt 通用建议）

1. **「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」。**
   每条结论必须标注证据强度：真 PG / 纯 Mockito / 静态源码 / 双侧活体对拍。
   只读源码得出的结论**不得**写成已闭环。
2. **测试数不是质量指标。** 本项目已有多条漂移曾被绿测试锁死。
   「有绿测试」不构成 `EQUIVALENT` 的证据。
3. **只跑 RAG 侧的探针不算数。** 它只证明代码写成了这样，证明不了 Go 是这样。
4. **框架机制必须验证实际生效。** 「注解存在」「pom 里有依赖」「类上有 Bean」
   都不算证据；必须有真实配置 + 调用路径 + 代理生效条件。
   已实证的四类静默失效见 `AGENTS.md` §3.3.3。
5. **Go 的默认值可能藏在 struct tag 里**，不在 yaml 里。判断某个 Go 默认行为是否生效，
   必须读到**消费该 tag 的那段控制流**，不能只贴 struct 定义行。
6. **证据要给 `文件:行号`**，且行号指向**消费点**而不是声明点。
7. **禁止拿现有 RAG 代码或现有注释当行为基准**，也禁止「读两遍 Go 取并集」。
8. **真相是 Go**（2026-10-20 起，唯一真源）。两侧出现行为差异时，
   默认判定 **RAG 错**，而不是「两边各有一套」。