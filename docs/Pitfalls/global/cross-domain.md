<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · 跨域踩坑档（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：跨域的**方法论级**坑 —— 一次会横跨多个域、或影响所有域的判断失误。

---

## PIT-GLOBAL-005 ⚠️ **全量构建偶发红 ≠ 你的改动坏了**（判定要靠对照实验，不是靠猜）

### 现象（本轮实测）

`mvn -o clean verify`（全量 56 个 reactor 模块）在第 12 个模块 `user-rpc` 红：

```
UserRpcPrimaryKeySourceParityTest.schemaIsReal » IllegalState Failed to load ApplicationContext
Caused by: com.alibaba.nacos.shaded.io.grpc...: Connection refused: /127.0.0.1:1001
```

而本轮的改动只有两处：
① `service/common` 删一个**零调用方**的废弃方法；② `service/users/user/user-rpc` 改一段 **javadoc**。

### ⚠️ 那段 Nacos 栈是**红鲱鱼**

日志里 `Connection refused: /127.0.0.1:1001` 看着像根因，但：

- `127.0.0.1:1001` **不在仓库任何配置里**（全仓 yaml/yml/properties grep 无命中）⇒ 是 Nacos **客户端自身**的端口换算；
- 把它当根因会**误判成「Nacos 没起」** ⇒ 而实际 Nacos 容器（`nacos-standalone`）**在跑**、8848/9848 **在监听**；
- ⚠️ **决定性证据**：重跑时那段 WARN **依然出现**，而整轮**全绿** ⇒ ⇒ 它是**后台告警**，不是致命原因。

⇒ **教训**：`Caused by:` 栈的**最内层**不一定是**致命原因** ——
Nacos 客户端的重连线程打出来的 `StatusRuntimeException` 会混进日志流。
⇒ 判断根因要看**哪条断言真的失败了**，不是看日志里哪段栈最吓人。

### 正确的判定序列（三步，缺一不可）

| 步 | 动作 | 本轮结果 |
|---|---|---|
| ① | **同一条命令重跑**（换窄范围） | `mvn -pl service/users/user/user-rpc -am test` ⇒ **125/125 全绿**（含那个失败类） |
| ② | **基线对照**（另开一个**只读**基线树，跑同一条命令） | 基线 `58e8314` 同命令 ⇒ **4/4 全绿** ⇒ 差异不在代码 |
| ③ | **全量重跑** | `mvn -o clean verify -fae` ⇒ **BUILD SUCCESS**（17:50，56 模块，0 失败） |

⇒ 三步叠起来 ⇒ 第一次失败是**偶发环境问题**（Nacos 注册 / 容器时序），**与改动无关**。

### ⚠️ 为什么必须做 ②「基线对照」

因为**改动本身通常就长得像元凶**。没有基线对照时，人会本能地把红账算到刚改的那几行上
⇒ 结果要么改坏本来正确的代码，要么回退一个其实没问题的改动。

⇒ **对应 AGENTS.md §3.1 的老话**：「不要拿『现有 RAG 代码』当对照基准」——
同理：**不要拿『刚才那次构建』当对照基准**。

### 顺手发现的：**基线对照别用 `git stash`**

AGENTS.md §5.3 禁用 `git stash` 是因为**共享工作区**会被并发 agent 互偷。
⇒ 但「在**自己独占**的工作树里**另开一个**基线 worktree」是安全且更干净的：

```bash
cd <主仓库>
git worktree add /tmp/rag-baseline-check 58e8314     # 只读基线树，不动任何现有工作区
cd /tmp/rag-baseline-check && mvn -o -pl <module> -am test \
    -Dtest=<同一个类> -Dsurefire.failIfNoSpecifiedTests=false
git worktree remove /tmp/rag-baseline-check          # 用完即撤
```

⇒ 优势：**零状态污染**（不动 `stash`、不动主工作区、不动其它域的工作树）。

⚠️ **配套的小坑**：`-Dtest=<类名>` 跑多模块 reactor 时，**上游模块没有同名测试会直接失败**
（`No tests matching pattern ... were executed!`）⇒ 必须加
`-Dsurefire.failIfNoSpecifiedTests=false`。

### 出处

- 踩坑位置：本轮 `align/global` @ `58e8314` 的全量 `mvn clean verify`
- 关联：`docs/DRIFT/global/cycle-deps.md` §1.5（变异测试）、[`common-tails.md`](../../DRIFT/global/common-tails.md) §〇（真源分层）
- 2026-10-25

---

## PIT-GLOBAL-006 ⚠️ **「Go 出处」要分两层，只查项目树必然落空**

⇒ 详细记录见 [`docs/Pitfalls/global/common-tails.md`](common-tails.md) PIT-GLOBAL-001
⇒ 本档只留一句：**凡涉及「请求保护层 / body 解析 / RPC 中间件」的 Go 事实，
去 `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` 找，别在 `Sea-RideTheWind_golang` 里 grep。**

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 PIT-GLOBAL-005（全量构建偶发红的判定三步 + 基线对照的 worktree 做法）· PIT-GLOBAL-006（真源分层的指针） |
