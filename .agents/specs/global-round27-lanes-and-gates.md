# global 第二十七轮 —— **6 条 lane 并行**（9 条「能直接动手」的未知项）+ **整合者修两处跨 lane 守门缺陷**

> **分支**：`align/global` · **基线**：`499cca1`（第二轮编排提交）
> **性质**：**并行对齐轮**（6 条 lane）+ **整合者修缺陷**（跨 lane 的守门缺陷 + 1 处自曝错账）
> **对 favorite 的改动量**：**0 个文件**（代码 / 配置 / 测试 / 台账全零改动）⇒ 共享层影响**记账 + 告知 owner**
> **需拍板**：**0 条**（登记类事项登记完就躺着；D1 已落地、D2 已于 2026-10-24 拍板「先记账不改」⇒ 不重复上报）

---

## 一、这一轮在做什么

第二十六轮盘点出 **9 条**「落点在 global 写权内、不需要拍板」的未知项。本轮**一次性打完**：

| # | 未知项 | 来源 | 落点 | lane | 状态 |
|---|---|---|---|---|---|
| ① | **4 个 rpc 组件的 X-5 轴真 server 守门**（admin / article / comment / like） | L2 缺口① | 4 个模块的 `src/test/**` | **L6 + L7** | ✅ 已补齐 |
| ② | **slow threshold 守门推广到其余 8 个 rpc 组件** | L2 缺口② | 8 个模块的 `src/test/**` | **L7 + L8 + L9** | ✅ 已补齐 |
| ③ | **「主动打断 handler 线程」的定性** | L2 缺口⑤ | 只读 `service/common` | **L9** | ✅ **框架限制成立** ⇒ 销账 |
| ④ | **admin-rpc slow 守门前提失效复评** | L2 缺口⑦ | 只读 `service/common` | **L6** | ✅ 已复评 + 已加固 |
| ⑤ | **触发器函数体逐条核对** | L3 盲区① | `tools/difftest/check-ddl-static.py` | **L10** | ✅ 已闭合 |
| ⑥ | **task / message 两域 DDL runner 重建** | L3 盲区② | `tools/difftest/**` | **L10** | ✅ 已闭合 |
| ⑦ | **「三个真 rpc 服务同时起」形态** | L4 边界① | `tools/difftest/flyway*` | **L11** | 见台账 |
| ⑧ | **迁移中途失败 / 回滚 + 同模块两副本** | L4 边界② | `tools/difftest/flyway*` | **L11** | 见台账 |
| ⑨ | **多轮压力 / 随机延迟注入** | L4 边界③ | `tools/difftest/flyway*` | **L11** | 见台账 |

⇒ **生产代码零改动**（本轮只加守门与脚本；唯一的 `src/main` 改动是**一处过期注释**）。

---

## 二、编排：6 条 lane 的拆分与守门

### 2.1 拆分依据（与前一轮不同）

前一轮（L2~L5）按**文件**切；本轮按**模块**切 ⇒ 因为 **L2 的教训**：同模块两个 lane 会**互踩 `target/`**。

| lane | 分支 | 模块 |
|---|---|---|
| **L6** | `align/global-x5gate` | admin-rpc · article-rpc |
| **L7** | `align/global-slowgate` | comment-rpc · like-rpc |
| **L8** | `align/global-slowgate2` | user-rpc · message-rpc · hot-rpc |
| **L9** | `align/global-interrupt` | task-rpc · follow-rpc |
| **L10** | `align/global-ddldeep` | `tools/difftest/` 的 DDL 部分（`ddl*` 前缀） |
| **L11** | `align/global-flywaydeep` | `tools/difftest/` 的 Flyway 部分（`flyway*` 前缀） |

⇒ **9 个互不相交的 maven 模块 + 2 条脚本 lane** ⇒ 文件级与模块级**双零重叠**。

### 2.2 机器守门：`check-global-lane-ownership.py` 拆成两档

🔴 **起因**：L2 曾拥有 `user-rpc/src/test/**`、L1 曾拥有 `like-rpc/**` ⇒ 与在跑的 L7/L8 **必然重叠**
⇒ 但**已合回**的 lane **不再是并发方** ⇒ 重叠只在「在跑」的 lane 之间算违规。

⇒ 守门拆成 `ACTIVE`（L6~L11，重叠判定）/ `CLOSED`（L1~L5，单列归档）
⇒ ⚠️ **不分档 ⇒ 守门恒红 ⇒ 恒红的守门等于没有守门**。

**变异 3/3 全部转红且红在目标断言上**：M1 在跑的 lane 互相重叠 · M2 共享文件被占用（`AGENTS.md`）· M3 favorite 禁区被占用。

---

## 三、🔴 整合者修的两处**跨 lane** 守门缺陷（+ 自曝错账 #4）

两处**都不是某条 lane 的问题**（是 6 条 lane 各自没看出来的共性）⇒ 由整合者统一修。

### 3.1 慢轴守门缺「预热」⇒ 绿是**执行顺序的副产品**

| 项 | 内容 |
|---|---|
| **症状** | L9 实测：`follow-rpc` 的「快调用必须**没有** `sea.slow`」那条**首调就红**（`sea.duration_ms = 138.333` > 阈值 100ms） |
| **根因** | JVM 里**第一次** gRPC 调用要把 protobuf 编解码 / netty 缓冲 / 拦截器链的类全部加载一遍 ⇒ **这些成本落在 span 的 duration 里** |
| **扩散面** | 整合者核查发现：**7 份 slow 轴守门里有 5 份没有预热**（admin / article / user / message / hot）⇒ 它们之所以绿，**只因 JUnit 默认按声明序** ⇒ 「慢用例先跑」把类预热了 ⇒ **顺序依赖的假稳定** |
| **修法** | 每个守门在被测调用前先做一次**预热调用**，再 `EXPORTER.clear()` ⇒ ⚠️ 预热必须用**独立 probe / 独立 server**（否则「handler 真被调用过」那条判别式断言会**永远为真** ⇒ 守门空转） |
| **教训** | **PIT-GLOBAL-027** |

### 3.2 慢轴守门不断言「前提成立」

| 项 | 内容 |
|---|---|
| **前提** | 「`GlobalOpenTelemetry` 在 Spring 上下文 refresh **之前**被换成本测试的 SDK」 |
| **为什么脆弱** | `GoStyleOtelAgent`（`service/common`）有「**已 set 就放弃**」分支 ⇒ 这个前提**靠「没人先 set」成立**，**不是时序保证** ⇒ 将来有人先装了别的 SDK ⇒ 守门的 exporter 收不到 span ⇒ 用例以**「找不到 span」**这种**看不懂的形态**变红 |
| **修法** | 把前提**显式断言** ⇒ 前提不成立时直接说「前提不成立」（L6 §7.3 缺口④ 的建议） |
| **剩余加固建议** | 装 SDK 改用 `ApplicationContextInitializer`（早于所有 BFPP）· 专属上下文缓存键 · 根治需改 `service/common`（注入 Tracer 而非取全局）⇒ 归 `global/common-tails` T-15 |

### 3.3 🔴 自曝错账 #4：断言 `get() == sdk` **永远不成立**

整合者第一版写成 `assertSame(testSdk, GlobalOpenTelemetry.get(), …)` ⇒ **两条用例全红**
⇒ 看起来像「生产代码有缺陷」，其实**是断言写错了**：

```java
// opentelemetry-api-1.55.0 GlobalOpenTelemetry.set()
globalOpenTelemetry = new ObfuscatedOpenTelemetry(openTelemetry);   // ← 存的是**包装器**
```

⇒ `get()` **永远不等于** sdk 本身（包装器的设计目的就是**阻止**用 `==` 比较底层实例）
⇒ 正确判据是比 **`getTracerProvider()`**（包装器原样委托 ⇒ 同一实例）。

⇒ **教训**（**PIT-GLOBAL-026**）：断言「某个全局单例是不是我装的那个」之前，**先读那个单例的 `set()` 实现**
—— `set()` 存进去的**未必**是你传进去的那个对象。

---

## 四、各 lane 产出（摘要）

### 4.1 L6（admin-rpc / article-rpc）

- **X-5 轴**：`AdminRpcGrpcInterceptorWiringTest`（3 用例 / 14 断言）· `ArticleGrpcInterceptorWiringTest`（3 / 14）
- **slow 轴**：`ArticleRpcSlowThresholdGateTest`（2 / 6）
- **变异 8/8 全红且红在目标断言上**（含「生产 `application.yml` 的超时值被改」这类**配置侧**变异）
- 🔴 **缺口④ 复评**：
  - ① **Boot 的 OTel 自动配置不在依赖里** —— `opentelemetry-sdk-extension-autoconfigure` 全仓 pom **零命中** · m2 里连 jar 都没有 · `AutoConfiguredOpenTelemetrySdk` 全 m2 零命中 · `spring-boot-autoconfigure-4.0.8.jar` 里**零个** `*OpenTelemetry*` 类 ⇒ 本仓 OTel 装配是**自研**的 `GoStyleOtelAutoConfiguration`
  - ② 前提今天成立但属**「侥幸成立」** ⇒ 建议把前提变成断言（✅ 本轮已在整合阶段落地）
- ⚠️ **一条易误判点已订正**：**article 域的生产超时是显式 `70000`**（users 域才是「未配 ⇒ go-zero 默认 `2000`」）⇒ 守门**分别**断言，未混用

### 4.2 L7（comment-rpc / like-rpc）

- **X-5 轴**：`CommentRpcGrpcInterceptorWiringTest`（3 / 14）· `LikeRpcGrpcInterceptorWiringTest`（3 / 14）
- **slow 轴**：`CommentRpcSlowThresholdGateTest`（2 / 6）· `LikeRpcSlowThresholdGateTest`（2 / 6）
- **变异 4 条生产代码 + 1 条自测退化探针**（D1：把自家测试改回**裸 `ServerCallHandler`** ⇒ 2 条红在「handler 必须真的被调用过」）⇒ **同类兄弟用例仍绿** ⇒ 判别力成立
- 模块全量回归：comment-rpc **94** + like-rpc **88** ⇒ failures=0 errors=0 skipped=0

### 4.3 L8（user-rpc / message-rpc / hot-rpc）—— **slow 轴**

- `UserRpcSlowThresholdGateTest` · `MessageRpcSlowThresholdGateTest` · `HotRpcSlowThresholdGateTest`（各 2 用例）
- **变异 9/9**（3 域 × 3 变异：恒真 / 恒假 / **忽略本域配置**）
- 🟢 **新发现**：「slow 阈值的**配置被忽略**」是**可静默发生**的退化 ⇒ 守门的占用时长必须 **< Go 兜底 500ms** ⇒ **PIT-GLOBAL-028**

### 4.4 L9（task-rpc / follow-rpc）—— **slow 轴 + 缺口③ 定性**

- `TaskRpcSlowThresholdGateTest` · `FollowRpcSlowThresholdGateTest`（各 2 用例 / 10 断言）
- **变异 4/4**（`> 0` ⇒ `< 0` / `> 0` ⇒ `true`，慢/快两条各打一次）
- ✅ **缺口③ 四问结论**：
  1. `RpcInterceptor.java:196-210`（超时只 `close`）· `:287-314`（`onCancel` 不打断）· `:395/:406`（唯一 interrupt 在 `handle(...)` **非 gRPC 入口**）⇒ **没有打断**
  2. Go 侧：`rpc.go:38-39` `context.WithTimeout` · `:47-57` handler goroutine 拿 `timeoutCtx` · `:68-71` 超时分支**不等** goroutine ⇒ **Go 也不杀 goroutine**；`rpc.go:21` `conf.Timeout=0` ⇒ go-zero 自带超时未挂上
  3. 仅在「**取消自动传播到在途操作**」这一维不一致 ⇒ **框架限制**
  4. **L2 的 javadoc 自陈成立** ⇒ **不是新发现** ⇒ ⇒ **L2 缺口⑤ 销账**（没有「应当被打断」的可观察行为可守）

### 4.5 L10（DDL 两条盲区）

- `check-ddl-static.py` 新增 **触发器形式**（`pg_get_triggerdef` ⇒ 结构化属性，**补上一个旧盲区：查了却从不比对**）+ **触发器函数体**（`pg_proc` ⇒ 函数名 / 返回类型 / 参数 / volatility / `RETURNS trigger` / 语言，**不逐字比 SQL 文本**）
- **单边触发器不重复计失败**（防污染退出码）· **无可比对时输出「⚠️ 什么都没比」**（**不冒充「已对齐」**）
- `task` / `message` 两域改用 **runner 自管容器**（`ddl-task-pg` / `ddl-message-pg`）⇒ 退出码语义与其它域统一
- **人工逐条核对 3 对函数体**（like / favorite ×2 / article 上下文）⇒ **三对逐字节相同** ⇒ Go 侧对应物逐条判定
- **变异 4/4 全红**（`STABLE` 注入 / 函数改名 / `BEFORE→AFTER` / 去掉计失败行）
- 全 9 域复跑 ⇒ **逐域退出码与 L3 基线一致**，新维度**增加 0 处差异**

### 4.6 L11（Flyway 三条边界）

见 [`docs/DRIFT/global/lanes/L11-flywaydeep.md`](../docs/DRIFT/global/lanes/L11-flywaydeep.md)。
🔴 本 lane 挖出的**生产阻塞级新发现**已由整合者先行落档（见 §五）。

---

## 五、🔴 L11 挖出的**生产阻塞级**新发现（整合者已落 `AGENTS.md` §3.5）

| 发现 | 证据 | 影响 |
|---|---|---|
| 🔴 **`discovery://<name>`（**两斜杠**）在 5/7 个 rpc 模块的 `application.yml` 里** | devh 的 `DiscoveryClientResolverFactory` 要求 `targetUri.getPath()` **长度 > 1**；`discovery://admin-rpc` 的 path 是**空串** ⇒ 抛 `Incorrectly formatted target uri` ⇒ **检查发生在任何网络调用之前** ⇒ **与 Nacos 在不在线无关** | `message-rpc` / `like-rpc` / `article-rpc` / `comment-rpc` / `favorite-rpc` **真 fat jar 起不来** ⇒ **连 Flyway 都跑不到**（grpc client bean post-processor 先失败）⇒ 只有 `follow-rpc` 用了正确的三斜杠 |
| 🔴 **`like-rpc` / `article-rpc` 的 `grpc-inprocess` 是 `test` scope** ⇒ 不在 fat jar 里 | 实测 `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` | 这两个模块**真启动必炸** |
| 🔴 **`task-rpc` 的 jar 没有 `Main-Class`** | 实测 | 该模块**不能**以 jar 形态启动 |

⇒ **这是 §3.3「先让它真的启动一次」纪律的直接产物** ——
L4 的**最小 Spring 上下文**验证**看不到**这些问题（它不起真正的 gRPC client bean）。

⇒ ⛔ **只登记，不修**（本轮 lane 无 `service/**` 写权；修法需逐模块改 yaml ⇒ 落在各域 owner）
⇒ ⇒ **「9 个 rpc 组件的守门全绿」与「真进程起不来」可以同时成立** ⇒ 再次印证 **「跑绿」≠「对」**。

---

## 六、台账与索引的整合

| 文件 | 本轮改动 |
|---|---|
| `docs/DRIFT/global/cross-domain.md` | 新增 **§二·十五**（X-5 / slow 逐域判定 + 整合者修的两处缺陷 + 缺口③ 四问 + 5 条新发现） |
| `docs/DRIFT/global/common-tails.md` | 新增 **T-14**（`RpcInterceptor` javadoc 的「两层补救」并列陈述不成立）· **T-15**（全局 OTel 前提**侥幸成立**） |
| `docs/DRIFT/global/_README.md` | 新增 **§七之四**（E11~E18）· 变更记录 +1 行 |
| `docs/DRIFT/_INDEX.md` | global 行：26 → **27 轮** · 补 6 份 lane 台账指针 |
| `docs/Pitfalls/global/cross-domain.md` | 新增 **PIT-GLOBAL-026 / 027 / 028** |
| `docs/Pitfalls/_INDEX.md` | global 坑位数 24 → **27** |
| `AGENTS.md` | §0 global 行（27 轮 / 11 条 lane）· §3.5 表新增「rpc 真进程可启动性」· §7.2 工具表（DDL 维度 + 写权守门）⇒ **压回 32 KB 预算内** |
| `tools/difftest/check-global-lane-ownership.py` | 拆 `ACTIVE` / `CLOSED` 两档 |

---

## 七、验收与证据边界

- [x] 6 条 lane 的写权**文件级 + 模块级双零重叠** ⇒ 机器守门 `EXIT=0` + 变异 3/3
- [x] **生产代码零改动**（唯一 `src/main` 改动 = `LikeRpcApplication` 的**过期注释**订正）
- [x] `service/common` **零改动**
- [x] **favorite 零改动**
- [x] 每条守门**都做过变异**且**红在目标断言上**
- [x] 整合者的修复也**做过变异**（M-A：前提断言 ⇒ EXIT=1 且红在目标断言上）
- [ ] ⚠️ **本轮仍未做 Go ↔ RAG 双侧活体对拍** ⇒ ⇒ **不能**据此宣称 Go↔RAG 已对齐（守门证明的是「RAG 侧机制生效」）
- [ ] ⚠️ **X-5 轴全仓仍未穷尽** ⇒ ⛔ 只剩 `favorite-rpc`（域零写权 ⇒ 记账）
- [ ] ⚠️ **slow 轴全仓仍未穷尽** ⇒ ⛔ 只剩 `favorite-rpc`
- [ ] ⚠️ **逐方法未穷尽**（每域只验 1 个代表方法）

---

## 八、下一轮的自然入口

1. 🔴 **`discovery://` 两斜杠** ⇒ 5 个 rpc 模块真进程起不来 ⇒ **需各域 owner 修 yaml**（本轮只登记）
2. 🔴 **`like-rpc` / `article-rpc` 的 `grpc-inprocess` 是 `test` scope** ⇒ 真启动必炸
3. 🔴 **`task-rpc` 的 jar 没有 `Main-Class`**
4. 🟡 **`favorite-rpc`**：X-5 轴 + slow 轴两处守门仍缺（域零写权 ⇒ 永久登记）
5. 🟡 **`service/common` 的 T-14 / T-15**（`RpcInterceptor` javadoc + OTel 前提加固）⇒ 共享层只有 `align/global` 能写
