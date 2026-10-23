# Pitfalls 台账 · `service/users/admin/admin-rpc`

> **只写教训**：现象 → 根因（框架机制）→ 为什么测试抓不到 → 下次怎么防。
> 差异本体在 [`../../DRIFT/users/admin/rpc.md`](../../../DRIFT/users/admin/rpc.md)。
> 本文件 2026-10-23 首次落笔（此前为空文件）。
> **日期纪律**：任何「已修 / 未修」断言都带核对日期，否则按 `UNKNOWN` 处理。

---

## P-1 · `@Transactional` 的 self-invocation —— 本组件踩过一次，而且**同款形状还在原地**

**核对日期：2026-10-23**

### 现象

`CreateAdmin` 的 4 步原子事务（查 invite 行锁 → 查 username 冲突 → bcrypt → 插 admin → 标 invite 已用）
代码里明明有 `@Transactional`，但运行时是 **auto-commit**：W1/W2/W3/W4 各自独立提交。
外层只 catch 了一个异常，**前几步的写入留在库里**。
更坑的是：**没有任何异常、没有任何警告、日志一片正常**。

### 根因（框架机制）

`@Transactional` 靠 **AOP 代理**生效，而代理**只拦截从容器外部进来的调用**。
原先这 4 步是 `AdminDomainService` 自己的 public 方法，由同类的 `createAdmin(...)` 直接调用
（`this.createAdminAtomic(...)`）⇒ 不经代理 ⇒ 注解形同虚设。
Spring **不会**为此抛任何东西。

**这一条现在只差一个注解就复发**：`AdminCreateAtomicService.java:64-69` 的 6 参重载
通过 `this` 调 7 参重载。它当前安全**只是因为 6 参重载自己带了 `@Transactional`**，
事务已在栈外层开启。哪天有人「清理」掉 6 参重载上那个看起来多余的 `@Transactional`，
4 步就静默退化成 4 条 auto-commit —— 与原始 bug 完全同型、零症状。

### 为什么测试抓不到

- 纯 Mockito 单测把 mapper 整个 mock 掉，**既不装配 Spring 上下文也不执行 SQL**，
  事务开没开它都一样绿。
- 「Bean 存在」不等于「机制生效」—— 这是 AGENTS.md §3.3.3 列的四类静默失效之一。

### 下次怎么防

1. **要跨的事务步骤，单独成一个 Bean**，别放在同一个类里自调用。
2. 必须有一条**真的触发事务边界**的测试：真容器 + 真 DB，让中途某步失败，
   然后断言**前面步骤的写入没留下**。
   本组件现在有：`AdminRpcTransactionWiringTest`（真 PG 16 容器，4 条，W2/W3 两条失败档 +
   一条反向 happy path + 一条 W1 档）。
3. **看 surefire 报告里的 `Skipped`**，不只看 `Tests run`。
   `@EnabledIfDockerAvailable` 在没 Docker 时会**整类静默跳过**，
   看到 `Tests run: 4, Failures: 0` 但 `Skipped: 4` 就会得出完全错误的「已验证」结论。
   本轮特意核对了 `Skipped: 0` 才敢下结论。

---

## P-2 · `FieldStrategy.NEVER` + `LambdaUpdateWrapper` 让 `update_time` 永不推进 —— **user-rpc 修过，admin-rpc 漏修**

**核对日期：2026-10-23**

### 现象

管理员改密码 / 封禁用户之后，`users_user.update_time` 和建表那一刻一模一样，从没动过。
Go 侧每次 UPDATE 都会把它推到当次写入时刻。

### 根因（框架机制）

两条独立的机制叠在一起，**都不会报错**：

1. **GORM 的 `autoUpdateTime`**：Go 的 struct tag `gorm:"column:update_time;autoUpdateTime"`
   被 `gorm@v1.31.1/schema/field.go:304` 读成 `field.AutoUpdateTime`，
   之后 `callbacks/update.go` 的 **struct 形态**（:276-295）与 **map 形态**（:237-254）
   **两条分支都会**把该列塞进 SET —— 与业务字段是否零值无关。
2. **MyBatis-Plus 没有对应概念**：`updateStrategy = FieldStrategy.NEVER` 让该列永远不进 SQL；
   而 `LambdaUpdateWrapper` 只 set 你显式 `.set()` 的列，**没有任何 autoUpdateTime 兜底**。

### 为什么测试抓不到 —— 这一条值得单独说

**它已经在同一个仓库里被修过一次了。**
`service/users/user/user-rpc/.../mapper/UserMapper.java:81-127` 按**完全相同的 Go 出处**
修过（注释里直引 `autoUpdateTime` tag），修法是 `wrapper.setSql("update_time = CURRENT_TIMESTAMP")`，
并有回归测试 `UserRpcJsonbAndAvatarProjectionTest:113-148` 钉住「SET 子句必须含 update_time」。

**admin-rpc 的审计没有发现它**，因为它长得跟「文档写的是对的」一模一样：
三个 Entity 的 javadoc 明明白白列了 `create_time / update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
（L1 表结构冻结注释），读起来完全合理 —— **列存在 ≠ 列会被写**。

⇒ **元教训：按组件粒度逐个审计，必然会漏掉「同款漂移在别的组件还没修」这类项。**
一份「某组件已修 DRIFT-001」的记录，**不构成**「全仓的 DRIFT-001 都修完了」的证据。
修的时候要顺手 grep 一遍同款 struct tag + 同款 wrapper 模式。

### 下次怎么防

1. 遇到 `FieldStrategy.NEVER` 的列，**必须回答「谁在写它」**；答案是「没人」就是 bug。
2. 加一条**守恒断言**（不是行为断言）：抓 mapper 生成的 SQL，断言 `setSql` 里出现了该列。
   参考 `UserRpcJsonbAndAvatarProjectionTest` 的写法。
3. 审查时把「Go 的哪个 tag / 框架哪个字段」列成一张对照清单，逐项问「RAG 侧对应物在哪一行」。

---

## P-3 · 悲观锁的**归属描述**错位，会让人怀疑锁根本没生效

**核对日期：2026-10-23**

### 现象

`AdminInviteMapper.java:18-33` 与 `AdminMapper.java:19-38`、`UserMapper.java:21-31`
三处类注释都曾声称「复杂方法写在 Mapper 的 `default` 方法里，用 `@Select` / `@Update` +
`@Transactional` 实现」。**而三个接口实际一个方法都没有。**

后果不是代码错，是**审计时找不到锁**：
review 的人按注释去 Mapper 里找 `findAdminInviteByCodeForUpdate`，找不到，
于是怀疑「悲观锁是不是没加上」，进而可能把一段正确的锁当 bug 改掉 ——
或者反过来，不敢动它。

### 根因

注释是**手写的、不是编译器生成的**，没有任何机制保证它与代码同步。
Java 侧最容易被这样描述错的是「复杂 SQL 在哪」：MyBatis-Plus 把 CRUD 全塞进
`BaseMapper` 默认方法，于是「方法在哪」这个问题**根本没有 Mapper 答案**，
只能答「在 Service 里用 wrapper 拼」。

### 下次怎么防

1. 描述「方法在哪」时**以文件实际内容为准**，注释只当线索。
   `grep -n "<方法名>" <模块>` 的结果比注释可信（AGENTS.md §3.3「禁止拿现有 RAG 代码当基准」
   讲的是**行为**基准；「文件里有没有这个方法」是可 grep 的事实，不受那条限制）。
2. 断言归属描述时用反射/结构断言（本组件 `AdminUpdateSelfAndCreateAdminRegressionTest`
   就是这么做的），别靠人读。
3. 同款错误描述还残留在 `pom.xml:82-86` 与 `HUMAN_ONLY.MD:28,83-84`
   ⇒ **同一句话错了要一次改三处，只改一处等于埋下一颗雷。**

---

## P-4 · `@Import` 只让 Bean 存在 —— 「已修」的第一现场和原型案例都在本组件

**核对日期：2026-10-23 · ⚠️ 结论已修正（见下）**

### 原型经过

`AGENTS.md` §6.2 里那行「`RpcServerInterceptorFactory 在 user-rpc / admin-rpc 均未注册`」
曾经被标成 **「✅ 已修」**，一天后被二次更正为「本次『已修』不成立」。
当时的修法只加了 `@Import(RpcServerInterceptorFactory.class)`，
而 **grpc-spring-boot-starter 3.1.0 从不自动注册**容器里的 `ServerInterceptor`。
原测试之所以假绿，是因为它断言的是「**Bean 存在**」，
而不是「**gRPC server 用上了它**」。

### 2026-10-23 复核：现在是真的闭环了

`AdminRpcApplication.java:66` 有 `@Import(RpcServerInterceptorFactory.class)`，
**而且** :97-101 有第二个、也是真正生效的那个 Bean：

```java
@Bean
public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor) {
    return server -> server.intercept(rpcUnaryServerInterceptor);
}
```

`GrpcServerFactoryAutoConfiguration` 只注入 `List<GrpcServerConfigurer>`（`javap` 可证），
`GrpcServerProperties` 里压根没有 interceptor 字段 ⇒ **`GrpcServerConfigurer` 是唯一入口**。

### 下次怎么防

1. 「让一个 Bean 出现在容器里」和「让框架用它」是**两件事**。
   `@Import` / `@Component` 只解决前者。
2. 加 framework 能力时，**先查框架有没有自动装配**（`javap` 那个 autoconfiguration 类 /
   `AutoConfiguration.imports`），再决定要不要写 hook。
3. 测试要断言**消费点**（server 的 interceptor 链里有没有它），不是**声明点**（容器里有没有它）。

---

## P-5 · 79 条全绿，仍漏 6 条差异 —— 测试条数不是质量指标（再次实证）

**核对日期：2026-10-23**

### 事实

本轮 `mvn -pl service/users/admin/admin-rpc -am -o test`：**79 条 / 0 失败 / 0 跳过**，
其中 **29 条跑在真实 PostgreSQL 16 容器上**（`Skipped: 0`，逐条核过）。
同时本轮静态对照查出 **6 条 OBSERVABLE DIFFERENCE**，
其中 `update_time`（P-2）和 `UpdateUserResp.success`（DRIFT-ARPC-002）
在 79 条里**一条测试都没有**。

### 为什么绿测试抓不到这 6 条

| 漏掉的 | 为什么绿测试看不见 |
|---|---|
| ARPC-001 `update_time` 不推进 | 所有断言都在**返回值和异常**上，没有一条断言 DB 里的 `update_time` |
| ARPC-002 `UpdateUserResp.success` | 没有一条测试碰 `UpdateUserResp` 这个 message 的 `success` 字段 |
| ARPC-003 空 default-password | 测试固定用 `123456`，从不构造空配置 |
| ARPC-004 DDL 列类型 | 容器测试用的 PG 里列已经是 RAG 那套，比的是**行为**不是**schema** |
| ARPC-005 多建一张表 | 「建表能跑」通过 ≠ 「建的表集合 == Go 的表集合」 |
| ARPC-006 没有 audit span | 没有一条测试断言 telemetry 属性；`checkDeadline` 只测 deadline |

⇒ 79 条绿证明的是「**已写的那些行为没坏**」，不是「**行为与 Go 一致**」。
AGENTS.md §0 说的「4 条漂移里有 3 条曾被绿测试锁死」，这里又添一条反例。

### 下次怎么防

1. 测试断言要分层：**返回值 / 异常 / 外部状态 / 序列化形状** 四类各来一遍。
   上面 6 条里有 4 条属于后两类。
2. **schema 类差异要有一个读 `information_schema` 的守门脚本** ——
   本仓库已有 `tools/difftest/check-ddl-static.py`，但它只查 `data_type`，
   **明确标了 NOT CHECKED：索引 / 唯一约束 / NOT NULL / DEFAULT**。
   那三项至今无人查。
3. 序列化形状类差异要有一个**读字节的**断言，不是读反序列化后对象的断言。

---

## P-6 · 判「已修」不能只读注释 —— 本组件三条历史回归里有两条靠真 PG 容器测试才拿到证据

**核对日期：2026-10-23**

### 事实

复核 `AGENTS.md` §1.1 那 11 处历史回归里落在本组件的三条时：

| 回归 | 只读源码能得出的结论 | 加上真 PG 容器测试后 |
|---|---|---|
| `CreateAdmin` 事务 self-invocation 失效 | 「代码看着是对的：跨 Bean 调用 + `@Transactional` 在被调方法上」 | 真的撞了 uid 唯一约束、真的回滚了 ⇒ **代理确实生效**（P-1） |
| 邀请码缺 `FOR UPDATE` | 「SQL 片段里有 `FOR UPDATE`」 | 8 线程真并发 ⇒ 1 成功 7 拒 ⇒ **锁确实在事务内生效**（P-1/P-3） |
| Flyway 双服务版本冲突 | 「pom 里没有 flyway 依赖了」 | `check-flyway-ledger.py` 扫出 admin-rpc **不在**启用 Flyway 的模块列表里 ⇒ **账本问题整体消失** |

三条里，**前两条的「已修」如果只靠读源码就下断言，恰好就是 AGENTS.md §3.3 禁止的那件事**。

### 下次怎么防

1. 判「框架机制已生效」必须有**运行证据**。真 PG / 真 Redis / 真容器 / 真管线，
   任一种都比读注释强。
2. **先核对测试有没有真的跑过**（看 `target/surefire-reports/*.txt` 的时间戳与 `Skipped` 数），
   再引用它的结论。仓库里存着一批 2026-10-22 19:33 的旧报告，
   不看时间戳会把它们当成本轮证据。
3. 复核历史结论时**逐条独立复核**，不采信「已修」标签。本轮就有两处标签需要修正：
   - 「`RpcServerInterceptorFactory` 已修」⇒ 修正为「**现在**才真正闭环，缺的是 `GrpcServerConfigurer`」
   - 「proto 逐字段对照无独立留档」⇒ 修正为「已由 `check-proto-static.sh` 闭环」
   两处都在 DRIFT 台账里就地标注了「2026-10-23 复核：原结论修正为……」，原文保留。

---

## P-7 · `@Import` 的另一半：`GrpcServerConfigurer` 必须自己写（已闭环，但值得记形状）

**核对日期：2026-10-23** · 与 P-4 同源，单独列出是为了强调「**两个 Bean，缺一不可**」。

```
光有 @Import(RpcServerInterceptorFactory.class)
    → Bean 存在，gRPC server 不知道它
    ↓
再有 @Bean GrpcServerConfigurer  → server.intercept(it)
    → 真的挂上
```

AGENTS.md §6.3 问题 3 记的是「显式 `@Import`，不拆包」，那解决的是
**Bean 能不能被创建**（`@Component` 在只扫自己包的朴素 `@SpringBootApplication` 里不会被扫到）。
它**没有**解决「框架会不会自动注册」。两个问题，两个修法，缺一不可。

⚠️ 同款缺件是否还在别处：**未审**。`AGENTS.md` §3.3.3 已记
`service/like/like-rpc/pom.xml:120` 只引 `spring-kafka` 的同款问题，尚未修。
本组件不属于该清单。