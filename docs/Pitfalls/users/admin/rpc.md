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
---

## P-8 · 同一个模块里**两张表的同名列类型不同** —— 改 DDL 时按方法名批量替换会改错表

> 2026-10-23 修复 DRIFT-ARPC-004① 时踩到（本轮**没有**踩进去，是靠逐处核对 Go 出处躲过的）。

### 现象

`admin-rpc` 的 `AdminDomainService` 里有**两处形状完全一样**的代码：

```java
wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(extraInfo));
```

- `:342` 在 `updateSelf` 里，wrapper 是 `LambdaUpdateWrapper<**AdminEntity**>` → 写 **`admin_admin.extra_info`**
- `:884` 在 `updateUser` 里，wrapper 是 `LambdaUpdateWrapper<**UserEntity**>` → 写 **`users_user.extra_info`**

这一轮把 `admin_admin.extra_info` 从 `jsonb` 改成 `text` ⇒ **`:342` 必须去掉 CAST，`:884` 必须保留**。
两行文本逐字相同，只有实体泛型不同。

### 为什么会踩

「`extra_info` 是 jsonb，改成 text」这句话在整个模块里是**半真**的：
`AdminEntity.extraInfo` 变 text，`UserEntity.extraInfo`（映射 `users_user`，**另一个域的表**）**仍是 jsonb**。
于是：

- `grep -rn "CAST({0} AS jsonb)"` → 2 处，**任何「全部替换」都会改错其中一处**；
- 改错 `:884` ⇒ `users_user` 那个 jsonb 列收到未 CAST 的字符串 ⇒
  `ERROR: column "extra_info" is of type jsonb but expression is of type character varying`
  ⇒ **user 域的 `updateUser` 100% 失败**，而 admin 域自己的测试**照样全绿**
  （admin 的测试不经过 `users_user` 的写路径）。

### 下次怎么防

1. **按「列的全名」而不是按「文本」定位**。判断依据是实体的 `@TableName` + wrapper 的泛型参数，
   不是方法名、不是 grep 到的字符串。
2. **凡是要改某一列的类型，先问「这个模块里还有哪些实体映射了同名列」**。
   本组件 3 个实体（`AdminEntity` / `AdminInviteEntity` / `UserEntity`）里
   `extra_info` 出现了**两次**，时间列出现了 **6 次**，分属两张表。
3. **跨域的表不要在本域顺手改**。`users_user` 的 DDL 归 users/user 域，
   即使 admin-rpc 也在读写它 —— 这一轮只报告、不改（见 `DRIFT/users/admin/rpc.md` 本轮 §6）。

### 同源的更大一类

`user-rpc` 侧 `users_user.extra_info` **也是 jsonb，而 Go 侧同样是 `serializer:json`（text）**
⇒ 同一个漂移在 users/user 域也存在，且**尚未修**。
本轮已作为跨域问题上报 —— 这条坑对整个 users 域都成立。

---

## P-9 · **签字档里的空占位文件**会让人把「已签字」误判成「未签字」

> 2026-10-23 实测，本轮的真实成本来源。

### 现象

2026-10-23 21:3x 的 admin-api 审计轮，在台账里写下：

> 「⚠️ `docs/Intentional_Deviations/users/**` 下 **6 个文件全是 0 字节**（空占位）
> ⇒ **没有任何一份正式的签字文件覆盖它**」

并据此判定 DRIFT-ADMINAPI-001 / -004「**仍需用户裁决**」。

**这个判定是错的。**
`docs/Intentional_Deviations/_cross/signed-deviations.md` 里的 **SD-2026-10-23-01**
标题明写「Go 恒 0 且**不校验 claim**，RAG **严格校验**」—— 两半都在签字范围内。

### 根因

签字信息**分散在三处**，而检索是**逐层下行**的：

```
docs/Intentional_Deviations/_INDEX.md      ← 索引
  └─ users/                                 ← 🔴 6 个 0 字节文件，断在这里
      ├─ admin/_shared.md   （原 0 字节）
      └─ _cross/signed-deviations.md        ← 真正的签字在 _cross 这一层，绕过了 users/
```

本域的签字（N-1 / N-4 / N-5）当时**只存在于 `docs/DRIFT/users/admin/_shared.md`** ——
那是**漂移台账**，按 §7.2 的读法不该当签字档用。
于是两个 agent 查同一个问题，一个查到「没签字」、一个查到「签了」。

### 为什么这条特别危险

它不是「漏查」这种可恢复的错误，而是**让审计结论反向**：
一个 agent 会把一条**用户已经拍板过的决策**当成新发现，
提出「要不要对齐 Go」的疑问 —— 而按 §5.3b 的纪律，
另一个 agent 可能就**「顺手修回旧行为」**，把用户的决策推翻。

### 下次怎么防

1. **签字档必须逐域落地**，不能只在 `_cross` 留一份转述。
   本轮已补齐 `docs/Intentional_Deviations/users/admin/{_shared,api,rpc}.md`。
2. **发现「某档下有 0 字节文件」时，先把它当成事故处理**，而不是当成「该域没有签字偏离」。
   两者在文件内容上完全一样，只能靠**别的域也有签字**来推断出「这是洞不是空」。
3. **写「未签字」三个字之前，必须真的走完 `_INDEX.md → 域 → 组件` 三层**，
   并且**再全文 grep 一次 `signed-deviations.md`**。本轮两次误判都栽在这一步。

> ⚠️ 同型洞仍在：`docs/Intentional_Deviations/users/user/{api,rpc}.md`
> 与 `users/_shared.md` **仍是 0 字节** ⇒ 归 users/user 域，未处理。

---

## P-10 · **实验失败时，它长得像结论** —— 「等待条件写错」让并发建表实验根本没发生

**核对日期：2026-10-23**（修 UNRESOLVED-ARPC-005 时踩到）

### 症状

跑 Go 侧并发建表实验（`tools/difftest/exp-go-concurrent-automigrate.sh`），
判定输出是：

```
  ? user-rpc 连库失败（配置问题，另查）
  ? admin-rpc 连库失败（配置问题，另查）
```

进程日志里是：

```
57P03 the database system is starting up
unexpected EOF
```

### 根因（等待条件写错，不是 Go 的问题）

原脚本等待数据库就绪用的是：

```bash
until docker exec gorace-pg pg_isready -U "$PG_USER" >/dev/null 2>&1; do sleep 1; done
```

🔴 **`pg_isready` 不带 `-d` 时，在 PG 的「初始化临时服务」阶段就已经返回 OK** ——
此时数据库系统**还没起来**，连上去必然报 `57P03`。
⇒ 脚本在库真正可用之前就把两个 rpc 放了出去 ⇒ **两边都连库失败、一张表都没建出来**。

### 为什么这个形态比「跑绿」更危险

判定输出写的是「**连库失败（配置问题，另查）**」——
读起来像「**Go 侧连不上、结论待查**」，是一个**待办**；
实际上**实验根本没发生**，既不能推出「Go 没问题」，也不能推出「Go 有问题」。

⇒ 与 AGENTS.md §3.3「跑绿 ≠ 结论对」同源，但**方向相反**：
§3.3 说的是「绿可能是错误答案的守卫」，这条是「**失败也可能是结论的伪装**」。
**两种情况下「脚本有输出」都不等于「脚本测到了东西」。**

### 怎么确认实验真的发生了（下次怎么防）

1. **等待条件必须指向「目标对象」而不是「同类里任意一个」**：
   `pg_isready` 要带 `-d "$PG_DB"`（等**目标库**可连接），不是只等 server 活着。
2. **断言「被测对象真的动了」**，而不只是「脚本跑完了」：
   本脚本最后会 `select ... from pg_tables` ⇒ **必须确认表列表非空**。
   判定列全是「?」时，先怀疑「实验没发生」，而不是「被测对象有问题」。
3. **失败时的输出里要能区分「连不上」和「连上了但结果不对」** ——
   前者是**实验基础设施坏了**，必须先修基础设施再谈结论。

### 留下的守门网

修法（已落地）：

```bash
until docker exec gorace-pg pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; do sleep 1; done
sleep 2
```

本轮修完后**连跑 4 轮**，每轮两进程间隔约 100ms（真并发），
4 轮全部「两边都未报建表失败 + 4 张表全建出」⇒ 这次才是真的测到了东西。

---

## P-11 · 「yml 里写了这个键」**不等于**「属性被绑进了 POJO」，更不等于「框架真的用它」

**核对日期：2026-10-23**（UNRESOLVED-ARPC-003 的第二层证据）

### 症状（与 §3.3 的「配置里没写 ≠ 没开」互为镜像）

`AdminRedisTimeoutConfigTest` 最初只有一条断言：

```java
assertEquals("5s", dig(root, "spring", "data", "redis", "connect-timeout"), ...);
```

它证明的是「**yml 里有这个键**」。
但这三件事**任何一件错了，Spring 都会静默保持默认值、不报错**：

1. 属性名写错（`connect-timeout` vs `connectTimeout` vs `connecttimeout`）；
2. relaxed binding 规则不匹配（`spring.data.redis.*` 前缀层级不对）；
3. 值塞不进 `DataRedisProperties.connectTimeout`（类型不匹配 / Boot 4 里这个字段被搬走了）。

⚠️ **Boot 4 特别容易踩**：`DataRedisProperties` 所在的 `RedisProperties` 已从
`spring-boot-autoconfigure` **拆出**到 `spring-boot-data-redis` 模块
⇒ 依赖没引对时，**整个属性类都不存在**，而应用照常启动。

### 怎么证伪 / 怎么确认

**不要自己解析 yml 再断言** —— 自己解析就等于「把自己的期望当成被验证对象」，
relaxed binding 是 Spring 自己实现的，绕开它就没有验证力。

用 **Spring Boot 真实的 `Binder`** 走一遍：

```java
MapConfigurationPropertySource src =
        new MapConfigurationPropertySource(flatten(load("application.yml")));
DataRedisProperties props = new Binder(src)
        .bind("spring.data.redis", DataRedisProperties.class)
        .orElseThrow(() -> new AssertionError("spring.data.redis 绑不上 —— "
                + "若属性名写错，Spring 会静默用默认值且不报错"));

assertEquals(Duration.ofSeconds(5), props.getConnectTimeout());
assertEquals(Duration.ofSeconds(3), props.getTimeout());
```

⚠️ 细节：yml 里 `host`/`port`/`password` 是 `${REDIS_PORT:6379}` 这种**占位符形态**，
不解析就没法把 `port` 绑成 `int` ⇒ 辅助方法按 Spring 语义取默认值。

### 下次怎么防

1. **配置类断言要分层**，每层一条测试：

   | 层 | 断言什么 |
   |---|---|
   | 文本层 | yml 里有这个键、值写对了 |
   | 绑定层 | **真 `Binder`** 能把它绑进配置 POJO |
   | 消费层 | 框架的客户端**真的**按这个值工作（socket 层实测） |

2. **从静态断言升到绑定断言**，成本极低（本例几十行），能挡掉一整类静默失效。
3. **消费层证据要诚实标边界**：本轮只做到绑定层，
   「Lettuce 在 socket 层真的按 5s 超时」**仍未证**（需指向黑洞 IP 实测失败耗时）
   ⇒ 台账里明确写「不得因为绑定层绿了就写『Redis 超时已完全对齐』」。

---

## P-12 · **两个超时值会互相遮蔽** —— 你测到的可能不是你想测的那一个

**核对日期：2026-10-24**（做 Lettuce socket 层实证时踩到）

### 现象

要证明 `spring.data.redis.connect-timeout: 5s` **真的在 socket 层生效**，
做法是「把连接指向黑洞 IP，量它多久失败」。

第一版测试**没有**覆盖 `spring.data.redis.timeout`（yml 里的 `3s`），结果实测 **3266ms** ——
那既不是 5s，也不是 10s，正好卡在 **`timeout: 3s`** 上。

### 根因（框架机制）

**Lettuce 的连接是异步的**：

```
getConnection()   → 立刻返回（实测耗时 0ms），真正的 TCP 握手在后台进行
ping()            → 命令被排在「还没连上的通道」上，命令超时开始计时
```

⇒ 真正先到期的，是 **命令超时（3s）**，而不是**连接超时（5s）**。
⇒ 「连不上要多久」这个可观测量，**默认测的是命令超时**。

### 为什么测试抓不到（它绿着，但测错了对象）

第一版测试的断言写的是「落在 [4000, 9500)」，实测 3266ms ⇒ **红了**。
但如果当时把区间写成 [2500, 3500)，它就**绿了**、而且绿得毫无意义
（它证明的是「命令超时是 3s」，不是「连接超时是 5s」）。

⇒ 🔴 **危险不在于红，在于「测错了对象还绿着」。**

### 下次怎么防

1. **要测哪一个超时，就把其他的挪开**。
   本轮的修法：测试里把 `spring.data.redis.timeout` 覆盖成 `30s`
   （远大于 connect 超时）⇒ **唯一**可能先到的就是 connect 超时。
2. **先确认「这个超时值在架构上到底会不会先到」** ——
   异步连接 + 命令超时 ⇒ 命令超时先到；同步连接 ⇒ 连接超时先到。
   读一遍框架源码（`LettuceConnectionFactory` / `RedisClient`）比试一次快。
3. **把「量到的耗时」和「预期的耗时」都写进断言消息** ——
   本轮的断言消息里列了四个可能的来源（5s / 10s / 3s / 75s）及其含义，
   红的时候不用回头查代码就知道测错的是哪一个。
4. **区分「配置层的等价」与「故障时的可观察时延的等价」** ——
   本例里两侧配置都是「连接 5s / 读 3s」，**配置层等价**；
   但 Redis 不可达时，用户可见的时延由 **3s** 主导 ⇒ **故障表现并不等价于配置的直觉**。
   写台账时要说清是哪一层。

### 留下的守门网

`AdminRedisConnectTimeoutSocketTest`：
① 配置消费点断言（`ClientOptions.socketOptions.connectTimeout == 5s`）—— 永远生效；
② socket 层时长断言（`[4000, 9500)`），且把 `timeout` 覆盖成 30s 以隔离出 connect 超时。

---

## P-13 · bash 在 UTF-8 locale 下会把**多字节字符并进变量名**

**核对日期：2026-10-24**（写实验脚本时踩到）

### 现象

脚本里这一行直接让脚本退出：

```bash
echo "    ✓ 两套 admin-rpc 就绪（Go $GO_ADMIN_PORT / RAG $RAG_ADMIN_PORT）"
```

```
exp-grpc-admin-deadline-probe.sh: line 118: RAG_ADMIN_PORT）: unbound variable
```

### 根因

在 UTF-8 locale（`LANG=C.UTF-8`）下，bash 的变量名解析**不把多字节字符当终止符**：

```bash
$ bash -c 'FOO=1; echo "$FOO）"'      # 输出空 —— 它去找名为 FOO） 的变量
$ bash -c 'FOO=1; echo "${FOO}）"'    # 正常输出 1）
```

⇒ **`$VAR` 后面紧跟全角括号 / 中文标点时，bash 会把它当成变量名的一部分**，
于是一个本来存在、刚在上一行赋过值的变量变成「未绑定」。

### 为什么它特别容易在**中文注释/文案密集的脚本**里踩到

这个仓库的脚本普遍是中文文案，`$VAR` 后面跟一个 `）` 或 `，` 的写法非常自然。
而且**报错信息指向的是那个看起来完全正常的变量名** ——
`RAG_ADMIN_PORT）: unbound variable` 里那个 `）` 肉眼很难注意到。

### 下次怎么防

1. **凡 `$VAR` 后面紧跟非 ASCII 字符，一律写成 `${VAR}`**。
   花括号没有任何副作用，纯粹是零成本保险。
2. **写完脚本先 `bash -n` 语法检查** —— ⚠️ 但 `bash -n` **抓不到这个**
   （它语法上合法，错误发生在**展开期**）⇒ 还得真跑一遍。
3. **自检脚本**（本轮用的就是这个）：

   ```bash
   python3 - << 'PY'
   import re
   for i, l in enumerate(open('script.sh', encoding='utf-8'), 1):
       for m in re.finditer(r'\$(\w+)(.?)', l):
           nxt = m.group(2)
           if nxt and ord(nxt) > 127:
               print(f"  第 {i} 行: ${m.group(1)} 后紧跟 U+{ord(nxt):04X}")
   PY
   ```

⇒ 同类教训还有一个（同一轮踩的）：**macOS 自带 bash 3.2，不支持 `declare -A` 关联数组**
⇒ `declare: -A: invalid option`。写脚本前先确认目标 shell 的版本，
或者干脆用普通变量（本轮就是这么做的）。

---

## P-14 · **用错仪器**比「没有仪器」更贵：采样窗口比事件本身还粗

**核对日期：2026-10-24**（做 ARPC-002 的 PG 侧阻塞探针时踩到）

### 现象

要测「客户端 deadline 到期后，服务端那条阻塞查询在 PG 上活了多久」，
第一版用 `pg_stat_activity` 轮询采样。跑出来的结果：

```
⚠️ [rag] 全程**没有观测到**阻塞查询 —— 实验可能没生效（表没被真正挡住）
```

⇒ 读起来像「**RAG 侧根本没发那条查询**」——
那是个**很强的结论**（会指向「RAG 的请求在到达 DB 前就被取消/退出了」）。

**而真相是：查询发了、也活了，只是采样没赶上。**

### 根因

每次采样都要起一个 `psql` 进程（`docker exec` + psql 启动 ≈ **200ms**），
采样间隔因此比**事件本身**还粗：

| 事件 | 时长 |
|---|---|
| RAG 侧那条查询在 1000ms 档下的可见窗口 | ≈ 100ms（客户端 deadline 1000ms 就返回了） |
| 一次采样的开销 | ≈ 200ms |

⇒ **采样周期 > 事件窗口 ⇒ 必然漏掉。**

### 为什么这比「没有仪器」更贵

「没有仪器」会让人知道「这里缺证据」；
「仪器失灵」会让人**得到一个看起来很像结论的东西**——
`全程没有观测到阻塞查询` 读起来完全像「查询压根没到 PG」，
而它真实的含义是「我的采样太慢」。

⇒ 与 P-10（实验失败时它长得像结论）同源，
但这次更隐蔽：**第一版还据此在台账里写了「RAG 侧可能没发查询」这种带方向的猜测。**

### 下次怎么防

1. **选仪器时先算「仪器开销 vs 事件时长」** ——
   采样周期必须**远小于**事件窗口，否则测的不是「没有」，是「没看到」。
2. **优先选「事件驱动」的仪器，而不是「轮询」** ——
   本轮换成 **PG 服务端日志**（`canceling statement due to user request` + `STATEMENT:` 行）：
   - 它给出**取消的确切时刻 + 语句原文**，精度是毫秒；
   - 而且「**没有日志条目**」本身就是强证据（= 那条查询根本没到 PG）——
     **这次的「没有」和上次的「没看到」是两回事，但只有换仪器才能区分。**
3. ⚠️ **别把「没观测到」写成「没发生」**。台账措辞上，
   「没观测到 ⇒ 可能是 X」必须显式带上「可能是」，并注明仪器的采样周期。
4. **让探针报绝对墙钟时刻**（`rpc_start_ms` / `rpc_end_ms`），
   别用「相对进程启动时刻」的偏移 —— 里面混着 JVM 启动时间（每次几百 ms 且每次都不同），
   直接比会得出错误的差值（本轮第一版就吃了这个亏）。

### 留下的守门网

`tools/difftest/exp-grpc-admin-deadline-probe.sh`：
主仪器 = PG 服务端日志；指标 = `Δ_start`（查询存活）与 `Δ_end`（客户端返回后仍占 PG 多久）。
