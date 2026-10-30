<!-- @move AGENTS.md L1755-L1769 · §3.3.3 元教训：把没看过的东西变成看过了 -->
**元教训（2026-10-22 同日）**：**「把没看过的东西变成看过了」本身就能挖出真 bug。**

这一条值得单独说，因为它的收益方向和直觉相反 ——
E-1 立项时只是「补 10 步的 body 比对」，看着是个机械活，
结果新断言一上线就红了，**当场挖出一条连续 4 轮对拍都没看见的真实漂移**。

⇒ **不要因为「把待办清干净」而降低验证强度。**
一个长期挂着「已知无法比对」的条目，往往不是技术债，
而是**当年就没想清楚该比对什么**的信号。
本次的做法可复用：
1. 先问「这条为什么**注定**不同」—— 把不可比的理由**写成断言**（Go 侧必须命中它自己的形态之一），
   而不是用「跳过」代替；
2. 为了断言而**扩到此前从未采集的数据**（这次是响应头），往往就撞出新缺陷；
3. 撞到之后**别急着改断言去迁就现状**——先确认是现状错了还是断言错了。
   本次正是靠「MockMvc vs 真实 Tomcat 的差异」才判定的。
<!-- @move AGENTS.md L1773-L1785 · §3.3.3 H2 案例：断言「Bean 存在」≠ 断言「机制生效」 -->
`ObservabilityWiringTest#grpcInterceptorBeanExists` 长期为绿，因为它断言的是
`ctx.getBean(ServerInterceptor.class)` 非空 —— 而真实缺陷是
**grpc-spring-boot-starter 3.1.0 根本不会把用户提供的 `ServerInterceptor` Bean 挂到 server 上**
（`javap` 证据见 §6.4 勘误小节）。Bean 在容器里、starter 不用它，两件事都对，机制照样是死的。

⇒ 凡依赖**框架自动注册**的机制（starter 自动装配、`AutoConfiguration.imports`、拦截器链），
测试必须断言二者之一：
- **运行时的可观测后果**（最强）：本例即「客户端是否 ≈2s 返回 1015」，由
  `tools/difftest/exp-b1-timeout-cancel.sh` 端到端覆盖；
- **对象确实进入了框架的接管点**（次强）：如 `verify(serverBuilder).intercept(interceptor)`，
  由 `GrpcInterceptorWiringTest` 锁住。

锁死用例：`user-rpc` 的 `GrpcInterceptorWiringTest`（2 条）。
---

<!-- @move AGENTS.md §3.3.3 · `,string` 漂移：绿测试给「错误答案」发通行证 -->

## 🔴 绿测试可能是「错误答案的守卫」，比没有测试更危险

**起因**：`service/message/message-api` 首轮实现把 Go 的 `,string` int64 请求字段做成了
「**只接受 JSON 字符串**」，而 Go 侧 `json.Number` 的 `reflect.Kind()` 是 `string`
（`type Number string`），所以 Go **同时接受字符串与数字**。
本项目这边两处都「有据可依」地写错了：

1. DTO javadoc 断言「`json.Number` 的 Kind 是 number」—— 事实错误（有完整推导，不是笔误）；
2. `MessageApiMvcPipelineTest#int64AsJsonNumberIsRejected` 把 **RAG 的实际输出**断言成了
   「与 Go 一致」—— 这条测试会**主动阻止**正确修复。

⇒ **测试断言的是「RAG 现在的行为」，不是「Go 的行为」。**
当测试名 / `@DisplayName` 里出现「对齐 Go」「与 Go 一致」，而断言写的是 RAG 的输出时，
它要么是**巧合**，要么是**在给未验证的假设发通行证**。

**危害的方向和直觉相反**：不是「测试没抓到 bug」，而是**修复时会先撞上这两条红测试**，
且测试名会让人误判「修复」才是漂移方向。⇒ 修复漂移时**先改/删错误断言，再改实现**。

---

## 🔴 陈旧 surefire 报告会**静默抬高**测试计数 ⇒ 「回归了多少条」这个数字本身可能是假的

**症状**：回归报告说「本模块 N 条测试全绿」，但 `mvn test` 的实际输出是另一个数，
**差值恰好等于某个「源码里已经不存在的测试类」的条数**。

**本仓的活体样本（2026-10-25 实测，**至今仍可复现**）**：
`service/users/user/user-rpc/target/surefire-reports/` 里同时躺着两份 JSON/avatar 投影测试的报告：

| 报告文件 | 条数 | 时间 | 源码里的类 |
|---|---|---|---|
| `TEST-…UserRpcJsonTextAndAvatarProjectionTest.xml` | 5 | 10-25 01:25 | ✅ 在（改名后的当前类） |
| `TEST-…UserRpcJsonbAndAvatarProjectionTest.xml` | **4** | **10-23 21:41** | ❌ **不存在**（类已改名 ⇒ 报告成孤儿） |

⇒ **XML 求和 = 129，而本轮实际执行 = 125，差 4** —— 差的正好是那份孤儿报告的条数。

**根因**：Maven **不会**因为测试类被改名/删除而清掉 `target/surefire-reports/` 里的旧 XML；
而 `target/` 在 `.gitignore` 里（版本控制也清理不到它）⇒ **旧报告能一直活到下一次 `mvn clean`，
而下一次 `mvn clean` 往往没人跑**（日常回归都是 `mvn -o test`）。
⇒ 任何「数 XML 求和」的口径，都可能把**从未在本轮跑过**的用例算进去。

**为什么这条特别贵**：它污染的正是**最需要精确的那个数** ——
「本轮回归 N 条 / 0 failures」是台账里**所有「✅ 全绿」断言的唯一支撑**。
计数虚高 ⇒ 「0 failures」听起来更漂亮 ⇒ 但「实际跑过多少」已经不准 ⇒ 绿测试覆盖率被高估。

**怎么防（口径，2026-10-25 定）**：
1. **测试计数只取「回归日志的模块级汇总」**（`mvn test` 输出里的 `Tests run: X, Failures: …` 行），
   **不取** `target/surefire-reports/TEST-*.xml` 求和；
2. 确需数 XML 时，**必须**先按类名比对 `src/test/java` 剔除「源码里已不存在的类」的报告，或**先 `mvn clean`**；
3. 报「0 failures」时**同时**断言 `skipped=0`（§3.3 纪律）—— ⚠️ 但**这一条挡不住本坑**：
   陈旧报告的 `skipped` 属性也是 `0` ⇒ **只能靠口径 1**。

**判据的可复现形态**（一条命令即可验）：
```bash
# XML 求和 vs 剔除孤儿后的真实执行数
python3 - <<'EOF'
import glob,os,re
mod="service/users/user/user-rpc"
src={f"{os.path.dirname(p).split('/src/test/java/')[1].replace('/','.')}.{os.path.basename(p)[:-5]}"
     for p in glob.glob(mod+"/src/test/java/**/*.java",recursive=True)}
tot=orphan=0
for f in glob.glob(mod+"/target/surefire-reports/TEST-*.xml"):
    n=int(re.search(r'tests="(\d+)"',open(f,errors="replace").read()).group(1)); tot+=n
    if os.path.basename(f)[5:-4] not in src: orphan+=n; print("孤儿:",os.path.basename(f),n)
print(f"XML求和={tot} 孤儿={orphan} 真实={tot-orphan}")
EOF
```

⚠️ **反面样本（同期实测）**：上一轮统计「**2110** tests」与本轮「**2109** tests」差 1，
逐模块对账后确认是**统计口径差**（不是测试少了 1 条）⇒ 说明**只要口径不固定，同一批代码两次统计就能得出不同的数**。
⇒ **引用测试数时必须同时写出口径**（本仓现行口径见本条第 1 点）。

---

## 🔴 守门脚本的**排除清单是架构假设** ⇒ 架构一变就**假阳性**（与「假绿」互为镜像）

**症状**：守门脚本突然报一堆差异，但逐条看**全是同一类**（例如「仅 RAG 有：xxx」），
而**业务上根本不存在这种差异** ⇒ 实际上什么都没漂移。

**本仓的活体样本（2026-10-25 实测）**：
`tools/difftest/check-ddl-static.py` 跑 `users` 域时返回 **`EXIT=2`（列集合不一致）**，
报「仅 RAG 有：`flyway_schema_history.*`」⇒ 看起来像「RAG 多建了表」。

**根因**：脚本的 `exclude_tables`（排除 Flyway 迁移账本表）在**当时**是对的 ——
「users 域没有 Flyway 账本」这个假设只在 **2026-10-25 之前**成立。
10-25 落地「每模块独立账本表名 `spring.flyway.table: flyway_schema_history_<模块>`」后，
**所有 rpc 模块共用同一个 `rag_db`** ⇒ `message-rpc` 的账本表 `flyway_schema_history_message`
就落进了 users 域检查所读的库里 ⇒ **旧假设失效**。
⇒ **脚本本身没坏，是它对架构的假设过期了。**

**修法**：`exclude_tables` 改为**前缀匹配** `("flyway_schema_history",)`（一条覆盖 7 个模块的账本表），
而不是逐个列出表名（逐个列 ⇒ 下一个模块接入时又失效）。
⚠️ 脚本里的排除逻辑是 `tbl == x or tbl.startswith(x)` ⇒ **前缀写法天然覆盖未来新增的账本表**。

**怎么快速识别这类假阳性**（判据）：
1. 报出的差异**全部落在同一张表/同一类对象上** ⇒ 先怀疑「这是脚本的排除项漏了」；
2. 去库里查那张表**是不是工具产物**（Flyway 账本、Testcontainers 临时表、审计表）而不是业务表；
3. 脚本头部若写着「**假设**：某域没有 X」⇒ **先核对这条假设是否还成立**，再读它的结论
   （⇒ 与 §3.3.1「配置里没写 ≠ 没开」同源：**脚本的结论只在它的前提成立时有效**）。

⚠️ **与「假绿」的对比**（两条一起记）：
| | 症状 | 危害 | 修法 |
|---|---|---|---|
| **假绿**（变异打在 `target/classes`） | 守门说「通过」，其实机制没生效 | 漏掉真缺陷 | 变异打在**源码**侧 + 断言守门网自己会红 |
| **假阳性**（排除清单过期） | 守门说「有差异」，其实没差异 | **把真差异淹在噪声里** ⇒ 人会开始忽略守门输出 | 排除项用**前缀匹配**；每次架构变更后**重跑一遍守门**并确认输出形态没变 |

⇒ **守门脚本的输出必须能被质疑**：看到一堆同形态的差异，先问「**这是真差异，还是我的假设过期了**」。



---

## 🔴 **变异锚点会随生产代码演进而失效** ⇒ 变异空转 ⇒ 判据失去判别力（同一处**连续三轮**）

**症状**：守门脚本跑出来是红的，但**红的原因不是判据被证伪**，而是**变异根本没发生**
⇒ 变异脚本若只判「退出码非 0」，就会把「锚点失配」误报成「判据有效」。

**本仓的活体样本（2026-10-30 · 同一处 `tools/difftest/mutate-deadline-downstream.py` 的 M1，连续三轮）**：
| 轮次 | 锚点串 | 命中 | 现象 |
|---|---|---|---|
| ① | `DeadlineState.bindToCurrent(deadlineEpochMs)` | **0** | 生产代码在 `1f62a245` 已改成 `bindRemainingNanos(...)` ⇒ 变异空转 |
| ② | `try (DeadlineState.Scope … bindRemainingNanos(effectiveTimeoutNanos)) {`（`handle(...)` 的 worker 绑定点） | 1 | 命中了，**但探针仍绿** ⇒ 根因 = **观测点与绑定点不在同一条路径**（探针 handler 跑在 `onHalfClose` 上） |
| ③ | 上一轮修好的 `onHalfClose` 锚点 | **0** | 共享层波次把该处改成 `bindAbsoluteNanos(absoluteDeadlineNanos)` ⇒ **刚修好的锚点又失效** |

⚠️ **第三轮还多一层**：那一版的变异语义是「**加**绑定」（= 候选修法的第一半）；
而修法**落地之后**，绑定**已经在生产代码里** ⇒ 「再加一次」是同义反复 ⇒
⇒ **变异语义必须整体翻转成「**拆掉**已落地的绑定」**（⇒ `Scope.NOOP`）⇒
⇒ 让被观测的键**退回旧值** ⇒ 漂移重现 ⇒ 守门红 ⇒ **证明这条键仍在观测它该观测的东西**。

**判定式**：
1. **「锚点命中 0 处」与「判据被证伪」是两件事** ⇒ 必须**分开报**
   （脚本里先断言 `count(old) == 1`，不满足就报「锚点失配」，**不要**报成「变异后仍绿」）。
2. **变异点必须落在被测代码真正执行的路径上** ⇒ 先问「**探针的 handler 跑在哪条路径上**」，
   再选锚点 ⇒ 别拿「看起来相关」的绑定点。
3. **锚点是会被生产代码演进而打失效的** ⇒ **每次共享层/被测代码变更后都要重跑变异自测**，
   且**把「锚点重定基」写进锚点历史的注释里**（本仓已按三轮写全）⇒ 否则下一个人只会看到「变异空转」，
   不知道它是第几次。
4. **修法落地 ⇒ 变异语义可能整体翻转** ⇒ 不是「换个锚点串」，是**换一个判据方向**。

**与「假绿」的对比**：
- 假绿 = 变异打在错的地方（`target/classes` / 非生效路径）⇒ 守门说通过，其实没测到。
- **本条** = 变异**打空了**（锚点已失效）⇒ 守门说红，其实红的原因不是判据 ⇒ **比假绿更隐蔽**
  （它会让人以为「守门有牙」）。
⇒ **两者都指向同一条纪律**：**判据的红与绿，必须能指到「哪一行、为什么」**。
