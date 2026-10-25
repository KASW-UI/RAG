# L2 · `align/global-rpcgate` —— X-5 残留（rpc 拦截器守门复查 + slow threshold 独立守门）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **A3 的残留**做完。第十七轮已修 `task` + `follow` 两域，但台账明写两条残留：

1. 🟡 **Go 的 slow threshold 分支仍无独立守门**（超时阈值是另一条路径，与「在飞处理被打断」不是同一条）。
2. 🟡 **其余 8 个 rpc 域的同名测试未逐一复查** ⇒ 「还有没有别的空转形态」**未穷尽**。

### 本 lane 的复查范围（精确到文件）

全仓 `*GrpcInterceptorWiringTest` 共 5 份，`task` + `follow` 已做 ⇒ **剩 3 份**：

| 文件 | 状态 |
|---|---|
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/GrpcInterceptorWiringTest.java` | ⬜ 待查 |
| `service/message/message-rpc/src/test/java/com/rag/message/message/rpc/MessageGrpcInterceptorWiringTest.java` | ⬜ 待查 |
| `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/HotGrpcInterceptorWiringTest.java` | ⬜ 待查 |

⚠️ `article-rpc` / `like-rpc` / `comment-rpc` / `admin-rpc` / `favorite-rpc` **没有这个测试类**
⇒ 「8 个域」里的其余几个域要么用别的类名、要么**根本没测** ⇒ **后者要登记为缺口**（这本身就是发现）。

## 二、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| 机制取证（为什么裸 `ServerCallHandler` 不被 dispatch） | `docs/DRIFT/global/cross-domain.md` | **§二·八**（X-5 落地那节，含 ① 机制 / ② 为什么测试空转 / ④ 修法 / ⑤ 落地） |
| 波及面扫描结果 | `docs/DRIFT/global/cross-domain.md` | §二·八 ③ |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **A3** 行 |
| 坑 | `docs/Pitfalls/global/cross-domain.md` | **PIT-GLOBAL-018**（变异脚本坑）· 其它相关 |
| 已修的两个域的**样板** | `service/task/task-rpc/src/test/.../TaskGrpcInterceptorWiringTest.java`<br>`service/follow/follow-rpc/src/test/.../FollowGrpcInterceptorWiringTest.java` | 逐字读 ⇒ 这就是目标形态 |
| Go 真源 | `Sea-RideTheWind_golang` 的 grpc interceptor 注册处 + 超时阈值设置处 | 逐字读 |

## 三、判据（什么算「空转」）

一个 rpc 域的拦截器守门，若**只**断言「超时 ⇒ 抛 `DEADLINE_EXCEEDED`」，则它可能是空转的 ⇔
**它可能根本没走 grpc-java 的标准派发入口**，于是超时机制压根没参与 ⇒ 绿得毫无意义。

⇒ **判别式断言**（这三条是「真的在跑」的证据）：
1. 处理函数**确实被调用过**（`entered` 计数 / latch）
2. 取消发生时**它仍在跑**（不是已经返回了才收到取消）
3. 异常是**从服务端派发链路**出来的，不是客户端自己造的

## 四、写权范围（白名单）

**只写这些**：
- `service/users/user/user-rpc/src/test/**`
- `service/message/message-rpc/src/test/**`
- `service/hot/hot-rpc/src/test/**`
- `docs/DRIFT/global/lanes/L2-rpcgate.md`（本文件）

⚠️ **本 lane 是「守门修复」，不是行为修复** ⇒ **生产代码一行都不该动**。
若发现必须动生产代码才能写出那三条断言 ⇒ **停下来**，把理由写进本文件，交整合者决策。

**⛔ 禁区**：
- `service/common/**` · `service/like/**` · `service/comment/**`（L1 的）· `service/favorite/**`（零写权）
- `service/{article,task,follow}/**`（已修域 / 别的域）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **3 个域**逐个判定：`空转 / 正常 / 缺测试` ⇒ 写成一张表。
2. 若是空转 ⇒ 照 `task`/`follow` 的样板改（换 `ServerCalls.asyncUnaryCall(...)` + 补两条断言 + `sleep` 换 latch）。
3. **slow threshold 分支的独立守门**（至少选 1 个域做样板，⚠️ 选一个**不与上面 3 个域重叠**的，避免与 L1/L2 自己撞）。
4. **每个改动都做过变异**（M1 去 `server.intercept` ⇒ 变红在目标断言上）。
5. 台账：本文件末尾追加「逐域判定表 + 变异结果 + 缺口清单」。

## 六、验收标准

- [ ] `mvn -o -pl service/users/user/user-rpc,service/message/message-rpc,service/hot/hot-rpc -am test` 全绿，**且 `skipped = 0`**
- [ ] 每个改过的守门**都做过变异**，且**红在目标断言上**
- [ ] 明确写出「8 个域」的完整结论（哪些有测试、哪些没有、哪些空转）
- [ ] **未覆盖 / 未穷尽**的部分要显式列出（不许写「已全部对齐」）

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **PIT-GLOBAL-018** | 变异脚本首版写成**编译不过**的表达式 lambda ⇒ maven **压根没跑测试** ⇒ surefire 里留着上一轮 XML ⇒ **报成假绿**。⇒ 必须三道闸：**跑前删旧 XML + 看 maven 退出码 + 查 `COMPILATION ERROR`** |
| **变异红在更早的断言** | M3「完成但**不回包**」首版 ⇒ handler 秒完成并正常回包 ⇒ 客户端拿到 OK ⇒ 红在**更早**的 `assertThrows` 上 ⇒ **目标断言压根没被执行到** ⇒ 变异必须**红在目标断言** |
| **`sleep(10_000)` 方案** | 两条新断言（「handler 被调用过」「取消时仍在跑」）在 sleep 方案下**根本写不出来** ⇒ 必须换 latch（`probe.release.await(30, SECONDS)`） |
| **上游资源不进 reactor** | 改上游模块的资源只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 必须 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false` |
