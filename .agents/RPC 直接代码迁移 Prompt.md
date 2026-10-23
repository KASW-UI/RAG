# Go → Java：单一微服务单一组件 / RPC 直接代码迁移 Prompt

你现在执行的不是代码翻译，也不是架构文档生成。

你的任务是：

> 从一个 Go 微服务中选定的一个 Component / RPC 出发，
> 理解它真实的运行语义，
> 在已有的 Java 同名/对应微服务中，
> 直接实现具有等价外部行为和业务语义的 Java 版本。

最终产物必须是：
**Java 项目中的实际代码修改。**

不要把生成 Markdown、迁移设计文档、语义说明文档作为主要交付物。

---

# 0. 输入

Go 项目：

''/Users/pr/Documents/java&golang/Sea-RideTheWind_golang''

Go 微服务：

<GO_SERVICE>

本次迁移目标 Component / RPC：

<GO_COMPONENT>

Java 项目：

‘/Users/pr/Documents/GitHub/Sea-RideTheWind_java’

Java 对应微服务：

<JAVA_SERVICE>


---

# 1. 最重要的迁移原则

## 1.1 Component 是“语义分析边界”，不是“Java 文件映射边界”

不要假设：

Go 文件 A
→ Java 文件 A

Go struct
→ Java class

Go interface
→ Java interface

Go middleware
→ Java middleware

Go service context
→ Java service context

Go function
→ Java method

这些对应关系都不成立，除非目标 Java 项目本身已经采用这种结构。

你需要迁移的是：

> Go Component / RPC 的可观察行为、业务语义以及行为相关的技术语义。

而不是 Go 的文件结构。

---

# 2. 严格禁止机械翻译

禁止：

- 逐函数翻译
- 逐文件翻译
- 逐 struct 翻译
- 逐 interface 翻译
- 为了保持 Go 目录结构而创建 Java 包
- 为了保持 Go 文件数量而创建 Java 文件
- 把 Go 的 middleware 原样复制成 Java middleware
- 把 Go 的 service context 原样复制成 Java context
- 把 Go 的 dependency wiring 原样复制成 Java wiring
- 看到一个 Go helper 就机械创建一个 Java helper
- 看到一个 Go interface 就机械创建一个 Java interface

尤其禁止：

> “Go 中有这个文件，所以 Java 中也应该有一个对应文件。”

这是错误的迁移依据。

---

# 3. Java/Spring 是独立的目标运行环境

迁移前必须先理解 Java 微服务当前已经存在的架构。

优先检查：

- Controller / RPC Entry
- Service
- Repository / DAO
- Entity / DTO / VO
- Mapper
- Exception
- Global Exception Handler
- Security / Authentication
- Authorization
- Filter
- Interceptor
- AOP
- Transaction
- Cache
- RPC Client
- MQ
- Configuration
- Dependency Injection
- Common utilities
- Existing tests
- Existing base classes
- Existing generated code
- Existing framework conventions

然后判断：

> Go 中某个机制，在 Java 项目中是否已经被 Spring 或现有基础设施吸收。

例如：

Go middleware
→ 可能已经由 Spring Security / Filter / Interceptor / AOP 实现

Go service context
→ 可能已经由 Spring DI / Request Context / SecurityContext 等机制承担

Go transaction helper
→ 可能已经由 @Transactional 或已有事务层实现

Go error wrapper
→ 可能已经由 Java Exception + Global Exception Handler 实现

Go config initialization
→ 可能已经由 Spring Boot Configuration / Properties 实现

Go dependency wiring
→ 可能已经由 Spring Bean / Constructor Injection 实现

Go RPC registration
→ 可能已经由 Java RPC framework / Controller / generated stub 实现

这些只是可能性。

**不要根据上述例子强行映射。
但不能将“不强行映射”理解为“不主动寻找 Spring 实现”。
必须先确认行为需求，再主动检查目标项目实际使用的 Spring 版本及相关框架能力。存在适用的 Spring 方案时，默认优先采用；只有验证不适用或存在明确缺口时，才选择自定义实现。**

必须先检查实际 Java 项目。

## 3.1 Spring 优先的实现机制选择（强制规则）

**本项目已经决定采用 Spring 作为 Java 微服务的基础框架。Spring 不是可选方案之一，而是 Java 实现时的默认技术体系。**

迁移 Go 组件时，必须区分：

- **必须保留的行为**：由 Go 源码、调用链、配置、测试确定的外部契约、业务规则和必要的运行时保证。

- **允许替换的实现机制**：Go 为实现这些行为使用的 middleware、context、goroutine、helper、手动依赖管理等机制。

- **Java 应优先采用的实现机制**：目标项目已有的基础设施、Spring 能力、Java 标准库及项目已经使用的技术组件。


### 3.1.1 不允许只检查现有 Java 代码

在决定自己编写某个基础组件前，必须主动检查 Spring 及目标项目现有依赖是否已经提供合适的实现方式。

不能因为 Java 项目目前没有对应实现，就直接认定需要自己写。

必须依次完成：

1. 确认目标微服务实际使用的 Spring Boot、Spring Framework 及相关组件版本。

2. 找出 Go 中当前机制真正承担的职责，以及这些职责需要保证的行为。

3. 检查目标 Java 项目已有的实现、依赖和配置。

4. 对照实际版本的官方文档或源码，确认 Spring / Java 是否提供合适的实现机制，以及它的适用范围和限制。

5. 优先采用与当前业务入口和运行生命周期匹配的 Spring 机制。

6. 只有当现有框架能力无法满足必要行为，或使用框架会造成明确的功能缺失、集成冲突或不合理复杂度时，才补充自定义实现。


### 3.1.2 选择 Spring，不等于只替换一个 API

例如，Go 中使用 middleware、context、goroutine 和手动超时控制时，不得直接把它们分别翻译成 Java 类、Thread、Executor 和 sleep 轮询。

应先判断这些机制分别解决什么问题，再检查适合当前场景的 Spring / Java 实现方式。

例如：

- HTTP 请求入口：检查 Spring Security、Servlet Filter、Interceptor 或 Spring MVC 的请求处理机制。

- 身份和权限：检查 Spring Security 及项目现有认证授权体系。

- 事务：检查 Spring 事务管理及现有事务边界。

- 配置和依赖装配：检查 Spring Boot Configuration、Properties、Bean 和依赖注入。

- 缓存：检查项目现有缓存抽象及相应实现。

- RPC、MQ：检查项目实际采用的框架及其拦截器、生命周期和错误处理机制。


以上仅是检查方向，不代表所有场景都应该使用某个特定 API。

### 3.1.3 必须说明框架接管了什么、自定义代码还负责什么

对于本次迁移中涉及的每项重要基础机制，内部实现决策必须能够回答：

- Go 原本用什么机制实现？

- 它实际保证什么行为？

- Java 项目现有能力能否承担这项职责？

- 如果没有现成实现，Spring / Java 提供什么候选方案？

- 最终选择了什么方案，依据是什么？

- 哪些职责交给框架，哪些仍由业务代码或自定义组件承担？

- 框架方案有哪些限制？这些限制是否影响原有行为？


不要求为每个简单方法单独写报告，但必须完成会影响实现选择的判断。

### 3.1.4 自定义实现必须有具体理由

禁止仅仅因为以下原因就自行重写基础设施：

- Go 中有对应的自定义组件。

- 当前 Java 项目还没有封装该能力。

- 自己编写更容易控制。

- 使用 Spring API 需要额外学习或查文档。

- 想让 Java 代码在结构上对应 Go。


如果最终决定不用 Spring 现有能力，必须指出具体缺口，说明为什么现有机制不适用，以及自定义实现如何补齐缺口。

**不要为了使用 Spring 而强行套用不匹配的组件；也不要在 Spring 已有合适能力时，未经比较就重复造轮子。**

最终原则：

> Go 源码决定需要保留什么行为；Spring 优先决定如何实现这些行为；只有框架能力存在明确缺口时，才编写必要的自定义代码。

---

# 4. 迁移目标：语义等价，而不是结构等价

使用以下四层判断什么必须保持：

## L1：外部契约

必须优先保持：

- RPC / HTTP endpoint
- method
- path
- protobuf
- request schema
- response schema
- HTTP status
- error code
- error response
- authentication information
- authorization requirements
- event schema
- DB schema
- ID / key semantics
- serialization behavior

如果 Java 项目已经通过生成代码或现有框架固定了这些内容，应优先复用现有定义。

---

## L2：业务语义

必须保持：

- 业务规则
- 条件判断
- 状态转换
- 成功条件
- 失败条件
- 分支意义
- 数据修改
- 数据删除
- 数据创建
- side effect
- idempotency
- business ordering
- business atomicity

尤其注意：

Go 中：

if A {
return error
}

不能因为 Java 中写法不同就变成：

if A {
continue
}

或者：

if A {
ignore
}

只要这个分支改变了业务结果，就是语义变化。

---

## L3：行为相关技术语义

必须检查并尽可能保持：

- transaction
- atomicity
- isolation requirement
- consistency
- cache hit/miss
- cache invalidation
- timeout
- retry
- cancellation
- concurrency
- ordering
- authentication
- authorization
- idempotency
- RPC failure handling
- MQ failure handling
- serialization
- error propagation
- error conversion
- resource lifecycle

这些虽然属于“技术实现”，但如果改变了运行结果，就属于迁移必须保留的语义。

---

## L4：实现机制

允许改变：

- package
- class structure
- method structure
- interface 数量
- helper 数量
- design pattern
- ORM API
- Spring API
- dependency injection style
- exception implementation
- framework abstraction
- Go-specific implementation

Java/Spring 应该按照目标项目自身的合理方式实现。

---

# 5. 核心原则：Go Component → Java Behavior

不要执行：

    Go Component
        ↓
    Go 文件列表
        ↓
    Java 文件列表
        ↓
    翻译

而应该执行：

    Go Component / RPC
        ↓
    实际运行路径
        ↓
    业务语义
        ↓
    行为相关技术语义
        ↓
    Java 项目已有能力
        ↓
    Java/Spring 实现方案
        ↓
    实际修改 Java 代码
        ↓
    编译 / 测试 / 行为验证

---

# 6. 第一步：确定 Go Component 的真实边界

先找到：

<GO_COMPONENT>

不要只看这个文件。

向调用链和依赖链扩展，直到能够解释这个 Component / RPC 的完整行为。

至少检查：

1. Entry Point
2. Caller
3. Main business logic
4. Repository / DAO
5. DB interaction
6. Cache
7. RPC
8. MQ / Event
9. Authentication
10. Authorization
11. Transaction
12. Configuration
13. Error definitions
14. Shared utilities
15. Tests

但不要无限制地分析整个 Go 项目。

原则：

> 只追踪那些可能改变当前 Component / RPC 行为的依赖。

如果某个依赖只是普通工具函数，而且其行为不会影响迁移决策，可以停止追踪。

---

# 7. 第二步：追踪完整运行路径

不要只分析“函数调用”。

建立真实执行顺序：

    Request / RPC
        ↓
    Authentication
        ↓
    Authorization
        ↓
    Validation
        ↓
    Business Decision
        ↓
    Data Read
        ↓
    Cache / DB / RPC / MQ
        ↓
    State Change
        ↓
    Side Effect
        ↓
    Response / Error

重点回答：

- 谁触发？
- 什么条件下进入？
- 谁验证身份？
- 谁判断权限？
- 输入如何进入业务逻辑？
- 哪些数据被读取？
- 哪些条件导致不同分支？
- 哪些状态发生变化？
- 哪些外部系统被调用？
- 外部调用失败怎么办？
- DB 操作是否必须一起成功？
- cache 是 source of truth 还是 optimization？
- RPC 失败是否阻塞当前业务？
- error 是否会被转换？
- 哪些副作用已经发生？
- 最终返回什么？

---

# 8. 第三步：分析 Authentication / Authorization

绝对不能因为“Spring Security 会处理”就跳过。

必须确认 Go 中：

- identity 从哪里来
- JWT / token 如何解析
- claims 有哪些
- user ID 如何获得
- role / permission 如何获得
- authentication 在哪里完成
- authorization 在哪里完成
- resource ownership 是否检查
- tenant 是否检查
- permission failure 如何返回
- authentication failure 如何返回
- 未登录和无权限是否区分

然后检查 Java 项目：

> 这些能力已经由哪个现有机制承担？

如果 Java 项目已有 Security Filter / SecurityContext / annotation / interceptor：

**复用它。**

不要为了“对应 Go 代码”重新实现一套认证授权。

但是：

> 如果 Go RPC 有额外的业务级权限判断，而 Java Spring Security 没有覆盖，则必须在 Java 中补齐。

---

# 9. 第四步：分析 Transaction / Atomicity

不能只搜索：

    Begin
    Commit
    Rollback

必须理解：

> 哪些业务操作必须作为一个不可分割的整体？

例如：

    修改订单
    ↓
    扣库存
    ↓
    写状态
    ↓
    删除缓存

如果 Go 的语义要求这些操作具有某种原子性或一致性：

Java 必须保留这个行为。

但实现方式可以改变。

例如：

Go transaction helper
→ Java @Transactional

是允许的。

但：

Go 没有 transaction
→ Java 为了“保险”随意加一个大事务

是不允许的。

因为这可能改变：

- 锁范围
- isolation
- failure behavior
- performance
- concurrency
- visibility

---

# 10. 第五步：分析 Cache

如果 Component 使用 cache，必须确认：

- key 如何生成
- key 包含什么
- hit 后返回什么
- miss 后从哪里读取
- cache 是否 source of truth
- DB 更新后是否更新 cache
- DB 更新后是否删除 cache
- 删除失败怎么办
- stale cache 是否可能出现
- cache failure 是否阻塞业务
- cache failure 是否被吞掉
- TTL 是否影响业务行为

不要简单翻译成：

    Go Redis
    ↓
    Java RedisTemplate

必须迁移的是 cache semantics。

Java 可以使用项目已有 cache abstraction。

---

# 11. 第六步：分析 RPC / MQ / External Dependency

对于当前 Component 调用的外部服务，只分析：

> 当前 Component 为了做出正确行为所需要知道的部分。

不要把另一个微服务整个迁移过来。

对每个外部调用确认：

- target
- purpose
- input
- output
- timeout
- retry
- failure
- error mapping
- whether failure blocks current operation
- side effects
- ordering
- idempotency

特别注意：

    external failure
        ↓
    return error

和：

    external failure
        ↓
    log
        ↓
    continue

语义完全不同。

---

# 12. 第七步：检查 Java 目标微服务

在修改代码前，必须先检查 Java 微服务现有结构。

重点寻找：

- 当前已有的同类 RPC
- 当前已有的同类业务 Service
- 当前已有的 DAO / Repository
- 当前已有的 Entity
- 当前已有的 DTO
- 当前已有的 Exception
- 当前已有的 Error Handler
- 当前已有的 Security
- 当前已有的 Cache
- 当前已有的 RPC Client
- 当前已有的 Transaction
- 当前已有的 Config
- 当前已有的测试
- 当前已有的公共基础设施
- 当前微服务的 Spring Boot / Spring Framework 版本及实际依赖
- 目标组件相关的 Spring 官方能力、项目现有封装及其适用限制。

回答：

> 当前 Component 在 Java 微服务中应该落在哪里？

注意：

这个位置必须根据 Java 项目当前架构决定。

不是根据 Go 文件位置决定。

---

# 13. Java 架构映射规则

允许以下情况：

### 情况 A：Go 一个 Component → Java 一个类

可以。

### 情况 B：Go 一个 Component → Java 多个类

可以。

例如：

Go：

    handler
      ↓
    logic
      ↓
    model

Java：

    Controller
      ↓
    Service
      ↓
    Repository

完全正常。

---

### 情况 C：多个 Go 机制 → Java 一个机制

可以。

例如：

Go：

    middleware
    context
    auth helper
    permission helper

Java：

    Spring Security
    +
    SecurityContext

只要行为等价即可。

---

### 情况 D：Go 机制 → Java 中完全不存在对应代码

也可以。

如果 Spring / Java runtime / generated infrastructure 已经承担了该职责：

> 不要重新创建代码。

---

### 情况 E：Go 代码在 Java 中不需要存在

允许删除/不创建。

判断标准：

> 该 Go 代码承担的行为是否已经由 Java 现有基础设施等价承担？

如果已经承担，则不要重复实现。

---

# 14. “Existing Java Architecture Wins”

如果目标 Java 微服务已经存在成熟机制：

优先复用。

如果目标 Java 微服务目前没有对应的基础机制，不代表应该立即自行实现。

先检查 Spring 及项目已引入的相关框架能否承担该职责。现有项目约定与 Spring 默认能力都应纳入技术选择；最终方案应优先符合当前项目的 Spring 技术体系，而不是为了复刻 Go 实现而额外建立平行机制。

例如已有：

- BaseService
- Repository pattern
- Exception hierarchy
- GlobalExceptionHandler
- SecurityContext
- Cache abstraction
- RPC client abstraction
- Transaction convention
- DTO convention

不要因为 Go 的结构不同而破坏 Java 项目结构。

但注意：

> “Existing Java Architecture Wins” 不意味着 Java 现有架构可以覆盖 Go 语义。

如果现有 Java 机制无法表达 Go 中的必要行为：

**必须扩展 Java 实现。**

---

# 15. 不要擅自修复 Go 的行为

迁移任务不是重构。

如果 Go 中存在一个看起来不优雅但实际上具有行为意义的逻辑：

必须保留。

例如：

- 特殊错误码
- 特殊错误转换
- 特殊 nil handling
- 特殊 retry
- 特殊 timeout
- 特殊 cache invalidation
- 特殊 ordering
- 特殊状态转换
- 特殊 permission check
- 特殊 fallback

不要因为：

“Java 更优雅”

“Spring 推荐这样写”

“我觉得这个逻辑应该优化”

而改变行为。

如果发现明显 bug：

先判断：

> 这个 bug 是否已经构成现有系统的可观察行为？

如果是：

默认保持。

除非用户明确要求修复。

---

# 16. 允许 Java 实现方式与 Go 完全不同

例如：

Go：

    middleware
        ↓
    context
        ↓
    service

Java 可以：

    Spring Security
        ↓
    Controller
        ↓
    Service

又例如：

Go：

    transaction helper

Java：

    @Transactional

又例如：

Go：

    manual dependency construction

Java：

    constructor injection

都属于正常迁移。

不要为了“看起来像 Go”而写出不符合 Java 项目习惯的代码。

---

# 17. 处理不确定性

如果发现一个行为无法确定：

不要直接猜。

先继续检查：

1. caller
2. callee
3. tests
4. config
5. proto
6. DB schema
7. error definitions
8. middleware
9. shared component
10. related implementation

如果仍然无法确定：

判断这个不确定性是否可能改变 Java 行为。

如果不会：

选择合理实现并继续。

如果会：

**不要静默猜测。**

暂停该部分实现，并明确告诉用户：

- 哪个行为无法确定
- 检查了什么
- 缺少什么证据
- 为什么它会影响迁移

---

# 18. 直接修改 Java 项目

完成语义理解后：

**立即进入代码实现。**

你可以：

- 修改已有 Java 文件
- 创建新的 Java 文件
- 删除不需要的迁移产物
- 修改配置
- 修改 DTO
- 修改 Entity
- 修改 Repository
- 修改 Service
- 修改 Controller / RPC Handler
- 修改 Exception
- 修改 Security
- 修改 Cache
- 修改 RPC Client
- 修改测试

前提：

> 这些修改必须服务于当前 Component / RPC 的迁移。

不要顺手重构整个 Java 微服务。

---

# 19. 不要为了迁移生成大量文档

不要创建：

- migration.md
- semantic.md
- architecture.md
- decision.md
- flow.md
- service-map.md

除非我明确要求。

你的主要工作对象是：

> Java source code

不是 Markdown。

你可以在内部完成完整语义分析，但不需要把分析过程全部写成文档。

---

# 20. 修改范围控制

默认只允许修改：

1. 当前 Component / RPC 必需代码
2. 当前 Component / RPC 必需依赖
3. 当前 Component / RPC 必需配置
4. 当前 Component / RPC 必需测试

不要修改：

- 无关业务
- 无关微服务
- 全局架构
- 无关依赖
- 无关命名
- 无关代码风格
- 无关性能优化

如果发现 Java 现有代码存在明显问题，但与当前迁移无关：

不要顺手修。

---

# 21. 测试迁移原则

Go 测试是重要的语义证据。

但不要机械地：

    Go test
        ↓
    Java test

逐行翻译。

应该提取测试真正验证的行为：

例如：

    given:
        user exists
        permission missing

    when:
        call RPC

    then:
        permission error
        no DB mutation

Java 应验证相同的行为。

重点覆盖：

- normal path
- validation failure
- authentication failure
- authorization failure
- business branch
- not found
- duplicate
- DB failure
- cache failure
- RPC failure
- timeout
- retry
- transaction rollback
- side effect
- idempotency

如果 Java 项目没有足够测试：

创建最小必要的 focused tests。

不要为了增加测试数量而大量制造无意义测试。

---

# 22. 编译与验证

代码修改完成后必须尽可能执行：

1. Java compile
2. relevant unit tests
3. relevant integration tests
4. static checks（如果项目已有）
5. Go reference tests（如果可运行）

至少验证：

### Contract

Java 输入输出是否符合 Go？

### Business

相同输入和前置条件下：

Go：

    result / state / error

Java：

    result / state / error

是否一致？

### Security

认证、授权、ownership、tenant 等是否一致？

### Data

DB / cache / external RPC 的行为是否一致？

### Error

错误条件、错误传播、错误转换是否一致？

### Transaction

失败时状态是否一致？

### Side Effect

成功 / 失败情况下产生的副作用是否一致？

---

# 23. 行为等价验证

不要只验证：

    Java 能编译

也不要只验证：

    Java test passed

真正要验证的是：

> 在等价输入、前置状态、依赖返回值下，Go 与 Java 是否产生等价的可观察行为。

可以使用：

    Input
      ↓
    Preconditions
      ↓
    Processing
      ↓
    External Interaction
      ↓
    State Change
      ↓
    Result / Error
      ↓
    Side Effect

逐项比较。

---

# 24. 迁移完成标准

只有满足以下条件，才认为 Component / RPC 迁移完成：

[ ] Go Component 的真实运行路径已经理解

[ ] 必要依赖已经检查

[ ] Authentication 已确认

[ ] Authorization 已确认

[ ] Business branches 已确认

[ ] DB behavior 已确认

[ ] Cache behavior 已确认

[ ] RPC / MQ behavior 已确认

[ ] Transaction / atomicity 已确认

[ ] Error behavior 已确认

[ ] Side effects 已确认

[ ] Java 现有架构已经检查

[ ] 没有机械复制 Go 文件结构

[ ] 没有重复实现 Java 已经提供的基础设施

[ ] L1 外部契约保持

[ ] L2 业务语义保持

[ ] L3 行为相关技术语义保持

[ ] L4 实现方式根据 Java 项目重新选择

[ ] Java 代码已经实际修改

[ ] Java 可以编译

[ ] 相关测试已经运行

[ ] 关键行为已经验证

[ ] 重要基础机制已经检查目标项目实际使用的 Spring / Java 能力

[ ] 已优先选择适用的 Spring 机制，而不是机械翻译 Go 的实现方式

[ ] 对于未采用现有框架能力的部分，已经确认存在具体的不适用原因或功能缺口

[ ] 没有仅因 Java 项目目前缺少封装，就重复实现 Spring 已经能够承担的职责

---

# 25. 最终输出

完成代码修改和验证后，不要输出长篇架构文档。

只需要简洁报告：

## Changed

列出实际修改的 Java 文件，以及每个文件为什么修改。

## Semantic Preservation

用简短列表说明本次迁移保留了哪些关键行为：

- authentication
- authorization
- business branches
- DB
- cache
- RPC
- transaction
- error
- side effects

只列实际涉及的内容。

## Verification

说明：

- compile
- tests
- relevant verification

以及结果。

## Remaining Issues

只有确实存在未解决问题时才写。

如果没有：

    None

---

# 26. 最终判断原则

整个任务只遵守一句话：

> **不要把 Go 的代码结构搬到 Java；把 Go 的行为搬到 Java。**

进一步：

> Go Component 决定“迁移什么”。

> Java 项目决定“应该怎么实现”。

> L1/L2/L3 决定“什么不能变”。

> L4 决定“什么可以重新设计”。

> Spring 已经承担的职责，不要重复实现。

> Go 中具有行为意义的机制，即使 Java 没有对应文件，也必须保留其语义。

> 最终交付物是可运行的 Java 代码，而不是描述 Java 代码应该怎么写的 Markdown。