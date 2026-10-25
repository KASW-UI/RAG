# L1 · `align/global`（本体）—— X-4 like 半边（社区事实投递链路）

> 本文件既是**任务书**也是本 lane 的**台账片段**：开工前读它，做完把结论追加在本文件末尾。
> 协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
>
> ⚠️ **本 lane 就是 `align/global` 本体**（工作树 `../RAG.worktrees/global`）——
> **已有一个会话在上面跑**，且它是 **L2~L5 的集成目标分支** ⇒
> ① **不要另开 `align/global-delivery`**（重复劳动）；
> ② **提交前只 `git add` 自己改过的文件**，⛔ **不要 `git add -A`**（会扫进别人的在途编辑，见 PIT-GLOBAL-020）。

## 一、目标

把 `global` 域待办 **A5 + B4** 做完：
RAG 的 like 域**另起炉灶**写了一套 outbox + stream + scheduler，与 Go 侧共享组件 `service/common/communityfact`
是**两份实现** ⇒ 要么收敛到唯一真源，要么证明等价。

**判据（B4）= 四条调度契约逐条对拍**：

| # | 契约 | Go 出处 |
|---|---|---|
| 1 | 投递端点 `POST /v1/events` | Go `service/common/communityfact/wire.go` |
| 2 | 1s 周期 | Go `cmd/fact-dispatch/main.go:67` `time.NewTicker(time.Second)` |
| 3 | 每轮 16 条 | Go `cmd/fact-dispatch/main.go:74` `for i := 0; i < 16; i++` |
| 4 | **单条失败即 break** | Go `cmd/fact-dispatch/main.go:75-79` |

## 二、起点：`align/global` 上**已有一半**（不要从零写）

`align/global` HEAD 上**已提交 811 行**半成品（`align/global-delivery` 从它切 ⇒ 继承）：

| 文件 | 状态 |
|---|---|
| `service/like/like-rpc/.../service/LikeFactDispatchService.java` | 已实现 `Dispatcher.DispatcherImpl` |
| `service/like/like-rpc/.../service/LikeDeliveryValidator.java` | 新增（383 行） |
| `service/like/like-rpc/.../mq/LikeFactDispatchScheduler.java` | 已接 `Dispatcher.runAsync` |
| `service/like/like-rpc/.../mapper/LikeDomainFactOutboxMapper.java` | 已改 |
| `service/like/like-rpc/.../config/LikeRpcProperties.java` | 已加配置 |
| `service/like/like-rpc/src/main/resources/application.yml` | 已加一行 |
| `service/like/like-rpc/src/test/.../LikeFactDeliveryGoSemanticsGateTest.java` | **新增守门（557 行）** |
| `service/comment/comment-rpc/.../service/CommentFactDeliveryService.java` | 仅 javadoc 订正（指向 like 侧） |

⚠️ **这半截在合并前是「未提交」状态** ⇒ 第一件事是确认它在 `align/global` 上已落成提交，否则你的分支继承不到。

## 三、必读（定向读，⛔ 不要通读 4 个规范台账）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| 域定义 + 纪律 | `docs/DRIFT/global/_README.md` | §〇 / §七 |
| 本域待办清单 | `docs/DRIFT/global/_README.md` | §七之二 的 **A5** / **B4** 两行 |
| X-4 逐条取证 | `docs/DRIFT/global/cross-domain.md` | **§二·十**（X-4 已确证那节） |
| like 侧已确证的漂移 | `docs/DRIFT/global/cross-domain.md` | §二·十 ②「RAG 的 like 域另起炉灶」 |
| 坑 | `docs/Pitfalls/global/cross-domain.md` | 全部（不长） |
| Go 真源 | `Sea-RideTheWind_golang/service/common/communityfact/`（`wire.go` / `process.go` / `logging.go`）+ `cmd/fact-dispatch/main.go` | 逐字读 |

## 四、写权范围（白名单，出了就是越权）

**只写这些**：
- `service/like/like-rpc/**`
- `service/comment/comment-rpc/**`
- `service/common/communityfact/Dispatcher.java`（共享层非阻塞 API —— **本域对 `service/common` 有单写权**，但**只碰这一个文件**）
- `docs/DRIFT/global/lanes/L1-delivery.md`（本文件）

**⛔ 禁区**：
- `service/common/**` 的**其余**文件（`http/` · `observability/` · `protection/` · `pom.xml` 等）
- `service/favorite/**` 与 `docs/*/favorite/`（零写权）
- `service/{article,hot,message,users,follow,task}/**`（别的 lane / 别的域）
- `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md`（共享文件，整合者统一更新）

## 五、交付物

1. **代码**：让四条调度契约**逐条**成立（或证伪一条并说明 Go 出处）。
2. **守门**：`LikeFactDeliveryGoSemanticsGateTest` 补齐到**四条契约各一条断言**，且：
   - 真 PG（不是 mock）——
   - 每条断言都能被**变异**打红，且**红在目标断言上**（不是红在更早的 `assertThrows` 上，cf. PIT-GLOBAL-018）。
3. **台账**：本文件末尾追加「结论 + 证据（`文件:行号`）+ 变异结果 + 未证成的部分」。

## 六、验收标准

- [ ] `mvn -o -pl service/like/like-rpc,service/comment/comment-rpc -am test` 全绿，**且 `skipped = 0`**
- [ ] 四条契约各有断言，且**都做过变异**（红在目标断言上）
- [ ] 每个 Go 事实都带 `文件:行号`，行号指向**消费点**不是声明点（§3.3.1）
- [ ] 明确写出：**哪些是已证等价、哪些是已知残留、哪些是本轮未覆盖**
- [ ] **真启动**验证过一次（like-rpc 的 fact dispatch 开关打开后，进程起得来、投递真的发生）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **半截的 stale 编译** | 合并前曾出现 `Dispatcher.runAsync` 「找不到符号」⇒ 那是 **stale 产物**（PIT-GLOBAL-019 同款），重跑即绿 ⇒ **遇到先重跑，别急着改代码** |
| **`@PostConstruct` 阻塞** | `Dispatcher.run` 是**阻塞式** ⇒ 在 `@PostConstruct` 里调它 ⇒ **Spring 上下文永远刷新不完** ⇒ 组件起不来 ⇒ 必须 `runAsync`（已改，但**守门要能抓**这个形态） |
| **开关默认关闭 ⇒ 测不出来** | `like.fact.dispatch-enabled` 默认 false ⇒ 「绿测试 + 起不来」是经典形态 ⇒ 必须有**真启动**守门 |
| **上游资源不进 reactor** | 改 `like-common` 的 DDL 只跑 `like-rpc` ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 必须 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false` |
