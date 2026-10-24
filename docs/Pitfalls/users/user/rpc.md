# 踩坑台账 · `service/users/user/user-rpc`

> **本文件此前为空**（0 字节）。以下为本轮（2026-10-23）新增，格式遵循
> `AGENTS.md` 的分工：**DRIFT 记差异（[`../../DRIFT/users/user/rpc.md`](../../../DRIFT/users/user/rpc.md)），
> Pitfalls 记教训**。每条写成「现象 → 根因（框架机制）→ 为什么测试抓不到 → 下次怎么防」。
> **只写本组件实际踩到 / 实际差点踩到的**，别把别处的教训复制过来充数。

---

## P-01 「`!= null &&` 这个防御写法，在 Go 那边对应的是**相反**的分支

**现象**：`UserDomainService.getUser` 有一段显式守卫，注释写着

> 「绝不 fallback 成 0 —— status=0 在 identity 解析里意为「活跃」，把 NULL 默认成活跃是朝错误方向的 fail-open」

同一个类里的 `login` 却写着 `if (user.getStatus() != null && user.getStatus() == 1L)`。
**两个写法都是「防御 null」，但只有一个方向是对的。**

**根因**：Go 侧 `Status int64` 是**值类型**，列里是 NULL 时 GORM 扫值直接失败 ⇒
整条查询返回非 sentinel 错误 ⇒ 上层走 `db_error` 分支 ⇒ 登录被拒。
Java 侧 `Long` 是**引用类型**，扫不出问题，于是「防御 null」自然写成了「跳过这一段」——
**而 Go 的事实是「这整条查询已经失败了，根本走不到这里」。**
⇒ 两侧「null 时会发生什么」根本不是同一个语义模型，防御写法无法逐字迁移。

**为什么测试抓不到**：`UserDomainServiceTest#login_bannedUser_returnsStatusTwo`
用的是 Mockito mock，`user.setStatus(1L)`；`getUser_nullStatus_throwsInternalNotNPE`
测的是 **getUser**，不是 login。**两条用例各自都绿，但没有任何一条把
「login 遇到 null status」当作一个需要判定的分支。**

**下次怎么防**：
1. 迁移任何「值类型 ↔ 引用类型」的字段时，**先问一句「Go 侧读到 NULL 的那一刻发生的是什么」**，
   而不是问「Java 侧该怎么防 null」。这两个问题的答案经常不同。
2. 「防御性判空」在两侧**方向相反**时，判空本身就是要审的高危点 —— 尤其当同一份代码里
   另一处对同一字段做了相反选择时（这几乎是一个免费的红旗）。
3. `Long` + `!= null` 出现在**条件分支**里（而不只是 `setXxx` 前）时，
   默认假设它是「跳过」语义，去 Go 侧找对应的**失败**语义，找不到就是漂移。

**关联**：`DRIFT-USERRPC-V-001`（SEMANTIC MISMATCH，fail-open 方向）、V-002（NOT NULL 差异）。
⚠️ 两条必须一起判：V-002 让 V-001 在 RAG 侧不可达，单独判任一条都会错。

---

## P-02 `@Transactional` 挂在 **mapper 接口的 `default` 方法**上，靠的是一件很容易被误读的事

**现象**：`UserAvatarHistoryMapper` 的 `createAvatarHistory` / `selectAvatarHistory`
把 `@Transactional` 标在 **接口的 default 方法**上。这在直觉上是「肯定不生效」的那一类 ——
毕竟 `message-rpc` 的 6 处 self-invocation 刚被记成 P0。

**根因（为什么这里恰好生效）**：它生效**不是**因为 default 方法有什么特殊待遇，而是因为
**入口在另一个 Bean**：

```
UserDomainService.uploadAvatar()            ← 不同 Bean
  └→ avatarHistoryMapper.createAvatarHistory(...)   ← 经 mapper 代理 ⇒ 事务开启
       ├→ userMapper.selectByUidRaw(uid)    ← 另一个 Bean，自带线程绑定连接
       └→ this.clearCurrentByUid(uid)       ← 同 Bean 自调用，但事务已在代理边界开启
```

方法体内的 `this.xxx()` 确实是 self-invocation，但**它不需要自己开事务** ——
事务是上一层代理开的，它只是复用当前线程上 `SqlSessionTemplate` → `DataSourceUtils`
绑定的连接。⇒ **入口一旦被改成同 Bean 自调用，立刻静默失效，且没有任何异常。**

**为什么测试抓不到（以及这次差点没抓到）**：
- `UserDomainServiceTest`（43 条）全部是纯 Mockito，**不装配 Spring 上下文**，天然测不到代理。
- 真正闭合这条的是 `AvatarTransactionWiringTest`（装配层）+ `UserRpcContainerWiringTest`（真 PG 真实行回滚），
  两个类各司其职。**只有后者能证明「PG 里那一行真的还在」。**
- ⚠️ **仍然没被测到的一点**：现有用例全部从 `UserDomainService` 侧发起，
  **没有任何一条断言「入口必须经代理」**。有人把调用改成 `this.xxx()`，这两组测试**可能照样绿**。
  对照 `admin-rpc` 抽 `AdminCreateAtomicService` 的做法，并加一条
  「4/5 步事务必须能被独立 Bean 触发」的结构断言。

**下次怎么防**：
1. `@Transactional` 在**接口 default 方法**上，先确认**调用方是另一个 Bean**；这一条要写进注释，
   因为「同 Bean 调自己」是之后最容易被「顺手重构」掉的形态。
2. 验事务**必须分两层**：① 装配层（bean 是不是代理、advisor 有没有命中）② 数据层（PG 里那一行还在不在）。
   只做① 会得到「调用过 rollback」的假结论；只做② 会在换数据源时失效。**两层都要，且要能互相当变异靶子。**
3. 「本层不需要开事务、只需复用上层事务」是**正确**的 self-invocation，
   与「本层需要开事务却 self-invoke 了」的缺陷**在代码上长得一样**。
   区分它们的唯一办法是看**谁该是事务入口**，所以入口必须是独立 Bean —— 命名上就该看得出来。

---

## P-03 依赖是否真的被移除，**要查 classpath 而不是读 pom 的注释**

**现象**：`AGENTS.md` §6.3 问题 1 记「users 域把 Flyway 丢掉了，两个 rpc 的 Flyway 三件套全部从 pom 移除」，
并且反复警告「**只删 `flyway-core` 不够**，只要 `spring-boot-flyway` 还在 classpath 上，
`FlywayAutoConfiguration` 就会用默认值自动装配并 migrate」。

**根因（本轮实测这条警告是对的）**：Spring Boot 4 把自动配置拆成了按技术分模块的 artifact。
`flyway-core` 只是引擎，**不提供任何 Spring 自动配置**；真正让它生效的是
`spring-boot-flyway`。⇒ 判断「有没有被移除」时，**看 pom 的 dependencies 块是不够的** ——
它可能通过传递依赖进来。

**下次怎么防（可复现的一条命令）**：

```bash
mvn -o -pl <module> dependency:list | grep -i flyway     # 必须零命中
```

⚠️ 用 `dependency:list` 而不是 `dependency:tree -Dincludes=...`：
后者在**一个都没匹配到时也输出 `BUILD SUCCESS`**，grep 不到东西很容易被读成「命令没跑对」。
本轮两条都跑了，`dependency:list` 零命中才下结论。

---

## P-04 「真 PG 测试跑绿了」和「这段行为与 Go 一致」是**两句话**

**现象**：本轮 `mvn -o -pl service/users/user/user-rpc test` 出 **91/91 全绿**，
其中 12 条跑在 Testcontainers `postgres:16-alpine` 上。作为「组件已验证」的证据看起来非常充分。

**根因**：这 91 条**全部只证明 RAG 一侧**。它们证明的是
「`JsonbTypeHandler` 在真 PG 上能写 jsonb」「`@Transactional` 在真 PG 上真回滚」
——**没有一条读过 Go**。同一个模块里，
`login` 对 `status == NULL` 的分支走向与 Go **相反**（`DRIFT-USERRPC-V-001`），
而 `login_bannedUser_returnsStatusTwo` 一路绿。

**下次怎么防**：
1. 台账里每条结论标**证据强度**（真 PG / 纯 Mockito / 静态源码 / 双侧活体对拍），
   并在组件判定处显式写「本轮**双侧活体对拍 = 缺**」。
2. 看到「N 条全绿 + 真容器」时，**先问一句「其中有几条读过 Go」** —— 通常是 0。
3. 本项目已有 4 条漂移曾被绿测试锁死（`AGENTS.md` §0），
   `users` 域的 `message-rpc` 也有「21 条全绿却一次没起过」。
   ⇒ **绿测试的作用是「防回退」，不是「证明等价」。**

---

## P-05 「Go 源码里的错误文案是乱码」—— 拿它当基准之前，先把字节打印出来

**现象**：`internal/logic/` 下 3 个文件（`uploadavatarlogic.go` / `selectavatarlogic.go` /
`getavatarhistorylogic.go`）的 9 条 `status.Error` message 在**源码里就是 GBK 双重编码乱码**，
且尾字节已丢（`鐢ㄦ埛涓嶅瓨鍦?` 结尾是一个字面量 `?` = `0x3f`，**还原不回来**）。

**为什么这条值得单独记**：终端 / 工具链会把 UTF-8 正确显示，把「文件里本来就是乱码」
和「显示时被转码搞坏」**混为一谈**。第一次用 `cat` / grep 看时，两者的输出**完全一样**。

**下次怎么防（可复现的两步）**：

```bash
# 1. 按字节读、打印 hex —— 这一步排除「显示层转码」的可能
python3 -c "print(open('selectavatarlogic.go','rb').read().decode('utf-8').splitlines()[42])"
# → 'return nil, status.Error(codes.NotFound, "鐢ㄦ埛涓嶅瓨鍦?")'
#   hex: ...e990a2e384a6e59f9be6b693e5b685e793a8e98da63f...   ← 结尾 63 3f 是 'c' '?'，是真的

# 2. 反向还原，确认它确实是 GBK 双重编码、且信息有损
python3 -c "print('鐢ㄦ埛涓嶅瓨鍦?'.encode('gbk').decode('utf-8','replace'))"
# → '用户不�?': 意图可辨，但**末字已不可恢复**
```

⇒ 判定「这是 Go 侧源码缺陷」还是「我这边显示坏了」，**只能靠 hex**。
本仓已把它登记为 **N-2（用户 2026-10-21 签字：RAG 保留正确中文）**，
见 [`../../DRIFT/users/user/api.md`](../../../DRIFT/users/user/api.md) —— **下一个 agent 不要把它当新发现再「修」一遍**，
也不要反过来把 RAG 改成乱码。

---

## P-06 「无出处 idiomatization」的**更贵形态**：给了一个**不存在的** Go 出处

**现象**：`UserExceptions.TokenBlacklistedException` 的 javadoc 写着

> 「对应 Go 端 LogoutLogic：用户 token 在黑名单中 → Status.PERMISSION_DENIED」

而 **Go 的 `logoutlogic.go` 全文 35 行里没有任何 token 有效性检查** ——
`in.Token == ""` 之外一路直通 Redis Setex。

**为什么它比「无出处」更贵**（`AGENTS.md` §3.2 的升级版）：
- **无出处**时，review 会去查、查不到就删掉；
- **假出处**时，review 直接采信，并据此**主动把 RAG 改坏**去「恢复」一个 Go 从未有过的行为。

⇒ 一个从未执行过的异常类 + 一段指向不存在代码的注释，
**足以在下一个审计轮次里制造一处新的 SEMANTIC MISMATCH。**

**怎么发现**：本轮用的是最朴素的一招 ——
对「疑似死代码」的符号做全仓 `grep`：

```
grep -rn 'PasswordMismatchException\|UsernameExistsException\|TokenBlacklistedException\|findCurrentAvatar\|selectCurrentByUid' user-rpc/src
→ 7 处命中，全部是「类声明 / import / 方法定义」，**零调用点**
```

**下次怎么防**：
1. 注释里出现「对应 Go 的 XXX」时，**必须去 Go 源码确认那个 XXX 真的存在**，而不是确认「文件名对得上」。
2. **零调用点的类 / 方法 + 一段解释性 javadoc**，是「有出处的 idiomatization」的经典未完成形态 ——
   编译器不会报错，静态扫描也不会报，只有 grep 调用点能发现。
3. 反向纪律（本轮也适用）：本组件**故意不复刻**的 Go 可疑形态也要留档
   （如 `LogoutResp.success` 两侧都是 `false` —— Go 自己就不填），
   否则下一个 agent 会把它当 bug 修掉，制造出**新的**漂移。

---

## P-07 本组件的「不要动」清单（避免下一个 agent 制造新漂移）

以下是**看起来像缺陷、实际是与 Go 一致**的点。改动它们会**新增**漂移，不是修复。

| 看着像 bug | 实际 | Go 出处 |
|---|---|---|
| `LogoutResp.success` 恒为 `false` | Go 也恒为 false（返回 `&pb.LogoutResp{}`） | `logoutlogic.go:43` |
| `LoginResp.status` 三态失败都 `err=nil` | go-zero 的业务码设计，不是 gRPC code | `loginlogic.go:46-72` |
| `Login` 用户不存在与密码错**共用** `status=1` | 防用户名枚举的有意设计 | `loginlogic.go:46` vs `:60` |
| `UpdateUser` 把 username 改成自己的现值也报 `AlreadyExists` | 查重**不排除自己** | `updateuserlogic.go:36-39` |
| `DeleteUser` 不删头像历史，留下孤儿行 | 两侧 DDL 都无 FK，是共有行为 | `model/user.go:54-57` + `02_create_user_avatar_history.sql` |
| `UserInfo.score` 永远不填 | proto 有这个字段，两端都不填 | `getuserlogic.go:48-54` |
| 头像并发下可能出现两条 `is_current=true` | 两侧都无 `FOR UPDATE`，是共有缺陷 | `model/user.go:134-169` |
| `UserRpcContainerWiringTest` 会建出 `admin_admin` / `admin_invite` | 有意为之：对齐 Go 的「每个 rpc 都 AutoMigrate 全套」 | `db.go:33`（Go 侧 `users` 排第 1，`admin` 排第 3，**靠时序侥幸**） |

⇒ 这张表的唯一用途：**让「看起来该修的东西」先在这里查一遍再动手。**
