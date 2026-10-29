<!-- @new 2026-10-23 20:2x · 38 组件台账覆盖矩阵（一次真实执行产出） -->

# 组件级台账覆盖矩阵（38 组件）

> 🟥 **⚠️ 本文件有两层时间**：
> - 🟢 **「当前态」节（2026-10-25 实测）= 现状** ⇒ **要拿现状读这里**；
> - 🕘 「🕘 历史」各节 = **2026-10-23 的快照**，已被 10-25 的 10 域并发对齐 + 10-25 第十三轮**部分推翻** ⇒ **只作追溯用**。
>
> ⚠️ **为什么需要这一层**：AGENTS.md §0 与 [`../_INDEX.md`](../_INDEX.md) 的「按域索引」都把本文件当作
> **组件级现状表**指过去，而本文件原先顶部写着「已过期、不要当现状读」⇒ **指针与内容打架**。
> ⇒ 2026-10-25 补「当前态」节，把指针重新接上；历史部分**不删**（§6.2 纪律：失效的断言要留口径与时间）。
>
> ⚠️ 仍然有效的是本文件里**不依赖 10-25 那一轮**的部分：
> `@EnabledIfDockerAvailable` 静默全跳的根因链（**已修**，Ryuk 缺口已补）、以及「rpc 必须先于 api」的排序约束。

> **核对时间：2026-10-23 20:06–20:30**（真实执行：`find` + `wc -l` 逐模块扫描 + `mvn -o -T1C install` + `mvn -o -T1C test` + surefire XML 汇总）
> **本文件回答一个问题：38 个组件里，哪些做过 Go ↔ RAG 对照、证据强度到哪一层、下一刀该切哪。**
>
> ⚠️ 按 §6.2 约定 2：**无核对日期的断言一律按 `UNKNOWN` 处理**。本文件所有数字都来自上列命令，不是推断。

---

# 🟢 当前态（2026-10-25 一次真实执行产出）

> **核对时间**：2026-10-25 01:45–01:50（真实执行：`find` + `wc -l` 逐组件扫 `src/main/java` ·
> `mvn -o test` 两段全量回归（`EXIT=0`）的模块级汇总 · `ls` 台账文件字节数）
> **本节回答**：38 个组件**今天**各自有没有台账、证据强度到哪层、当前结论是什么。
>
> ⚠️ **计数口径**（避免下一轮又对不上）：
> - **tests** 取**回归日志的模块级汇总**（`[INFO] Tests run: N, Failures: 0, …`），
>   **不**取 `target/surefire-reports/TEST-*.xml` 的求和 ——
>   实测 `user-rpc` 的 XML 求和是 **129** 而实际只跑了 **125**，
>   差额 4 来自一个 **10-23 的陈旧报告文件**（`UserRpcJsonbAndAvatarProjectionTest.xml`，
>   测试类已改名但旧文件没清）⇒ **按 XML 汇总会多算**。
> - **proto 组件** `src/main/java` 为 0（代码由 protoc 生成，不手写）⇒ 记 `—`。
> - **台账** 列的「0 字节」= **文件存在但没写过** ⇒ **不是「已审完」，是「从未对照」**。

| 域 | 组件 | main 文件/LOC | tests | 台账 | 证据强度 | 当前结论 |
|---|---|---|---|---|---|---|
| `article` | `article-api` | 41 / 5,759 | 86 | [`api.md`](../article/api.md) | 静态源码 + 真 Tomcat | 🔴 **NOT EQUIVALENT**（第十二轮，不变）· 残留 `DRIFT-006`(LOW) / `-007`(极低) + 若干 `UNRESOLVED` |
| `article` | `article-rpc` | 39 / 8,041 | 138 | [`rpc.md`](../article/rpc.md) | 真 PG | 🔴 **NOT EQUIVALENT**（第十一轮，不变）· 残留 `U-3`/`U-4`/`U-9`/`U-4'`/`S-5` + `P-U-1`（需 etcd） |
| `article` | `article-common` | 1 / 257 | 15 | [`common.md`](../article/common.md) | 双侧对拍 | ✅ 一致 |
| `article` | `article-proto` | — | 0 | [`proto.md`](../article/proto.md) | §3.4 + 逐字段 | ✅ 复制忠实 **且** 消费方式已验（`P-U-2` 已关） |
| `comment` | `comment-api` | 33 / 3,018 | 45 | [`api.md`](../comment/api.md) | 首轮组件级 | 🔴 **NOT EQUIVALENT** |
| `comment` | `comment-rpc` | 42 / 8,119 | 87 | [`rpc.md`](../comment/rpc.md) | 首轮 + 真 PG 修复轮 | 🔴 首轮判定；**7 条 MISMATCH 已全部修复**（各配真 PG 守门网 + 变异验证） |
| `comment` | `comment-common` | 4 / 567 | 27 | [`common.md`](../comment/common.md) | 双侧对拍 | ✅ **EQUIVALENT** |
| `comment` | `comment-proto` | — | 0 | [`proto.md`](../comment/proto.md) | §3.4 + 消费方式 | ✅ **EQUIVALENT** |
| `favorite` | `favorite-api` | 31 / 2,920 | 69 | [`api.md`](../favorite/api.md) | 真 PG + 真 Tomcat | 🟢 **已对照**（183 KB 台账；🟢 **2026-10-29 订正** —— 原写「0 字节 / 从未对照」**已被实测推翻**） |
| `favorite` | `favorite-common` | 3 / 456 | 25 | [`common.md`](../favorite/common.md) | 契约测试 | 🟢 **已对照**（🟢 **2026-10-29 订正** —— 原写「0 字节 / 从未对照」**已被实测推翻**） |
| `favorite` | `favorite-proto` | — | 0 | [`proto.md`](../favorite/proto.md) | `FavoriteProtoWireParityTest` ＋ `check-proto-static.sh` | 🟢 **已对照**（🟢 **2026-10-29 订正** —— 原写「0 字节 / 从未对照」**已被实测推翻**；⚠️ §3.4 的「复制忠实」与「消费方式已验」**仍是两回事**） |
| `favorite` | `favorite-rpc` | 36 / 7,106 | 58 | [`rpc.md`](../favorite/rpc.md) | 真 PG + 真启动 | 🔴 已修 6 处 L2 语义偏差；**7 条未决**（3 条待签字） |
| `follow` | `follow-api` | 27 / 3,047 | 50 | [`api.md`](../follow/api.md) | **双侧活体对拍** | ✅ **EQUIVALENT** |
| `follow` | `follow-rpc` | 17 / 2,692 | 70 | [`rpc.md`](../follow/rpc.md) | **双侧活体对拍 + 真 PG** | ✅ **EQUIVALENT** |
| `follow` | `follow-common` | 4 / 562 | 37 | [`common.md`](../follow/common.md) | 双侧对拍 | ✅ **EQUIVALENT**（16 码 16 文案逐条一致，零漂移） |
| `follow` | `follow-proto` | — | 0 | [`proto.md`](../follow/proto.md) | §3.4 + 消费方式 | ✅ **EQUIVALENT** |
| `hot` | `hot-api` | 17 / 2,434 | 89 | [`api.md`](../hot/api.md) | **双侧活体对拍** | 🔴 三组件已全部对拍；**第十三轮已修 D21/D22/D23**；残留 `DRIFT-HOTAPI-030`/`-031` |
| `hot` | `hot-rpc` | 15 / 2,535 | 89 | [`rpc.md`](../hot/rpc.md) | 真 Kafka + 2×真 Redis + 真 gRPC | ✅ D1 闭合；`DRIFT-COMMON-004` 已修（拦截器接线） |
| `hot` | `hot-common` | 2 / 172 | 14 | [`common.md`](../hot/common.md) | 双侧对拍 | ✅ 一致 |
| `hot` | `hot-proto` | — | 0 | ❌ 无专属台账 | §3.4 复制忠实 | ⚠️ **消费方式未验**（§3.4 效力边界第 1 条） |
| `like` | `like-api` | 35 / 3,094 | 81 | [`api.md`](../like/api.md) | **双侧活体对拍** | 🔴 **9 MISMATCH**（本仓最高） |
| `like` | `like-rpc` | 42 / 5,845 | 59 | [`rpc.md`](../like/rpc.md) | 真 PG | 🔴 4 MISMATCH + 2 OBS |
| `like` | `like-common` | 4 / 515 | 18 | ❌ 无专属台账 | — | ⚠️ **本轮未单独审查** |
| `like` | `like-proto` | — | 0 | ❌ 无专属台账 | §3.4 复制忠实 | ⚠️ **消费方式未验** |
| `message` | `message-api` | 47 / 4,490 | 88 | [`api.md`](../message/api.md) | 静态源码 | 🔴 5 MISMATCH + 1 OBS |
| `message` | `message-rpc` | 20 / 3,869 | 76 | [`rpc.md`](../message/rpc.md) | 真 PG | 🔴 1 MISMATCH + 5 OBS |
| `message` | `message-common` | 3 / 526 | 21 | [`common.md`](../message/common.md) | 契约测试 | ✅ 逐条等价（`FIX-MSG-COMMON-002` 已修） |
| `message` | `message-proto` | — | 0 | [`proto.md`](../message/proto.md) | §3.4 + 消费方式 | ✅ **EQUIVALENT**（第十轮升档） |
| `task` | `task-api` | 11 / 1,496 | 90 | [`api.md`](../task/api.md) | 首轮组件级 | ✅ **EQUIVALENT**（2 条漂移已修） |
| `task` | `task-rpc` | 29 / 4,351 | 108 | [`rpc.md`](../task/rpc.md) | 首轮 + Kafka 端到端 + HTTP 双侧对拍 | ✅ 首轮 5 条漂移**已全部修复并复核**（效力边界见该文件 §五） |
| `task` | `task-proto` | — | 0 | [`proto.md`](../task/proto.md) | §3.4 + 消费方式 | ✅ 文件零差异 + 消费方式首次核对未发现差异 |
| `users` | `user-api` | 40 / 3,030 | 107 | [`api.md`](../users/user/api.md) | 静态源码 | 🔴 **NOT EQUIVALENT**（部分已修；`DRIFT-USERAPI-009` ② 仍 OPEN） |
| `users` | `user-rpc` | 16 / 2,595 | 125 | [`rpc.md`](../users/user/rpc.md) | 真 PG | 🔴 **NOT EQUIVALENT** |
| `users` | `admin-api` | 52 / 3,467 | 129 | [`api.md`](../users/admin/api.md) | 静态源码 | 🔴 **NOT EQUIVALENT** |
| `users` | `admin-rpc` | 22 / 3,556 | 101 | [`rpc.md`](../users/admin/rpc.md) | 真 PG | 🔴 **NOT EQUIVALENT**（DDL 半边 ✅ **2026-10-25 实测已对齐**：admin 域 4 张表时间列全 `TIMESTAMPTZ`、类型 0 差异） |
| `users` | `users-common` | 11 / 1,037 | 33 | [`common.md`](../users/common.md) | 真 PG | ✅ 被 [`_shared.md`](../users/_shared.md) 覆盖 |
| `users` | `user-proto` | — | 0 | ❌ 无专属台账 | §3.4 复制忠实 | ⚠️ **消费方式未验** |
| `users` | `admin-proto` | — | 0 | ❌ 无专属台账 | §3.4 复制忠实 | ⚠️ **消费方式未验** |
| 跨服务 | `service/common` | 44 / 7,529 | 174 | [`alignment-2026-10-23.md`](../common/alignment-2026-10-23.md) | 多层 | 🔴 **不是「已审计完成」** —— 5 个可比包 **3 个 NOT EQUIVALENT**；**28 条编号未修** + 5 项未编号 + 9 项交接 |

### 当前态的三条结论

1. ~~**38 个组件里，3 个仍是「台账 0 字节」** ⇒ `favorite-api` / `favorite-common` / `favorite-proto`
   **从未对照**（`favorite-rpc` 有台账）⇒ **favorite 域是 38 组件里唯一整块空白**。~~
   🟢 **2026-10-29 订正（G-11 闭合）**：三份台账**全部在库且非空**（`api.md` 183 KB / `common.md` 10 KB / `proto.md` 15 KB）
   ⇒ ⇒ **favorite 域不是「整块空白」** ⇒ 上表三行已改为 ✅。
   ⚠️ **教训**：本条**当时**是真的（2026-10-25 快照），但**没人在快照过期后重核** ⇒ ⇒ 「台账 0 字节」这种**可机器核的事实**也必须**定期重测**，否则索引就会**长期骗人**。
2. **台账齐 ≠ 已对齐**：仍有 🔴 的域 = `article`（2 组件）· `like`（2）· `message`（2）· `users`（4）
   · `favorite-rpc` · `service/common`；已 ✅ 的域 = `comment`（3/4）· `follow`（4/4）· `hot`（2/3）· `message-common/proto`。
3. **8 个 `*-proto` 只有 §3.4 的「复制忠实」**（`hot` / `like` / `favorite` / `user` / `admin` / `task` / `comment` / `follow` 各自的 proto），
   **§3.4 效力边界第 1 条「RAG 消费生成代码的方式与 Go 一致」未验**（已验的只有 `article` / `comment` / `follow` / `message` / `task`）。

---

## 🔴 本文件推翻的三条既有结论

| 既有断言 | 位置 | 实测 | 处置 |
|---|---|---|---|
| 「`follow-api` 与 `favorite` api/rpc **仍 0 行** ⇒ 3 个组件无对照对象」 | AGENTS.md §0 · `_INDEX.md` L32 · `new-domains-2026-10-23.md` L19 | 🔴 **已全部作废**：`follow-api` 27 文件/2850 行、`favorite-api` 31/2920、`favorite-rpc` 36/7057、`follow-rpc` 17/2655、`comment-rpc` 40/7455 | 四份文件已同步更正 |
| 「**api / rpc / common 共 11 个组件仍是空壳**」 | `_INDEX.md` L32 | 🔴 **作废**：38 个组件**无一是空壳**（`<10 行`的 java 全是 interface 声明或 3 字段 DTO，非骨架） | 本文件「全量基线」节 |
| 「11 个组件零台账」 | 本文件初稿口径 | ✅ **成立且扩大**：**14 个**组件零台账（`comment` 4 + `follow` 4 + `task` 3 + `favorite` 的 api/common/proto 3） | 见下方矩阵 |

---

## 🕘 历史（2026-10-23 快照）：全量基线

| 项 | 实测值 | 命令 |
|---|---|---|
| 全量编译 | ✅ **`EXIT=0`**（`mvn -o -q -DskipTests -T1C install`） | 38 个组件无一带病 |
| 全量测试 | ✅ **BUILD SUCCESS**，`1630 tests / 0 failures / 0 errors / 15 skipped` | `mvn -o -T1C test` + surefire XML 汇总 |
| 实际执行用例 | **1615**（1630 − 15 skip） | 同上 |
| 🟢 **2026-10-25 复核** | ✅ **2110 tests / 0 failures / 0 errors / 0 skipped**（**29 模块**）—— 15 条 skip **已清零**（`task-rpc` 的 Ryuk 缺口已补）⇒ **满足「绿灯必须额外断言 `skipped=0`」纪律** | `mvn -o test` + surefire XML 汇总（`service/**/target/surefire-reports/TEST-*.xml`） |
| 空骨架残留 | **0** —— `<10 行`的 java 共 11 个，全是 `interface` 单行声明（`LikeFactDispatchService` 等）或 3 字段 DTO | `find … -exec wc -l` 过滤 |

⚠️ **「1630 条全绿」不等于「对齐了 1630 条」**，见下方「绿灯底下的空洞」。

---

## 🕘 历史（2026-10-23 快照）：组件级矩阵（38 个）

**图例**：台账 = 有无专属台账文件 · 强度 = 证据强度分层 · 状态 = 对照结论

### 🕘 历史（2026-10-23 快照）：已有台账（16 个）

| 域 | 组件 | main/LOC | tests | 台账 | 证据强度 | 结论 |
|---|---|---|---|---|---|---|
| `article` | `article-api` | 38 / 4,666 | 50 | [`api.md`](../article/api.md) | 静态源码 | 🔴 2 MISMATCH |
| `article` | `article-rpc` | 38 / 7,534 | 105 | [`rpc.md`](../article/rpc.md) | 真 PG | 🔴 4 MISMATCH + 4 OBS |
| `article` | `article-common` | 1 / 256 | 15 | [`common.md`](../article/common.md) | 双侧对拍 | ✅ 一致 |
| `hot` | `hot-api` | 16 / 1,931 | 59 | [`api.md`](../hot/api.md) | **双侧活体对拍** | 🔴 1 MISMATCH |
| `hot` | `hot-rpc` | 15 / 2,339 | 52 | [`rpc.md`](../hot/rpc.md) | 真启动 | 🔴 已修 4 条启动阻塞 |
| `hot` | `hot-common` | 2 / 172 | 14 | [`common.md`](../hot/common.md) | 双侧对拍 | ✅ 一致 |
| `like` | `like-api` | 32 / 2,638 | 62 | [`api.md`](../like/api.md) | **双侧活体对拍** | 🔴 **9 MISMATCH** |
| `like` | `like-rpc` | 41 / 5,413 | 46 | [`rpc.md`](../like/rpc.md) | 真 PG | 🔴 4 + 2 |
| `message` | `message-api` | 46 / 4,302 | 78 | [`api.md`](../message/api.md) | 静态源码 | 🔴 5 MISMATCH + 1 OBS |
| `message` | `message-rpc` | 20 / 3,752 | 49 | [`rpc.md`](../message/rpc.md) | 真 PG | 🔴 1 MISMATCH + 5 OBS |
| `users` | `user-api` | 40 / 2,636 | 69 | [`api.md`](../users/user/api.md) | 静态源码 | 🔴 NOT EQUIVALENT |
| `users` | `user-rpc` | 16 / 2,358 | 102 | [`rpc.md`](../users/user/rpc.md) | 真 PG | 🔴 NOT EQUIVALENT |
| `users` | `admin-api` | 52 / 3,285 | 119 | [`api.md`](../users/admin/api.md) | 静态源码 | 🔴 NOT EQUIVALENT |
| `users` | `admin-rpc` | 20 / 3,041 | 84 | [`rpc.md`](../users/admin/rpc.md) | 真 PG | 🔴 NOT EQUIVALENT |
| `favorite` | `favorite-rpc` | 36 / 7,057 | 56 | [`rpc.md`](../favorite/rpc.md) | 真 PG + 真启动 | 🔴 已修 6 处 L2 |
| 跨服务 | `service/common` | 44 / 7,529 | 146 | [`common/`](../common/audit.md) | 多层 | ✅ 审计完成 |

### 🕘 历史（2026-10-23 快照）：零台账（14 个）—— 下一批主攻（**已于 2026-10-25 解除**）

| 域 | 组件 | main/LOC | tests | 台账 | 结论 |
|---|---|---|---|---|---|
| `task` | `task-rpc` | 29 / 4,015 | 77（**15 skip**） | ❌ 无 | 🔴 未对照 · ⚠️ 见「空洞」 |
| `task` | `task-api` | 9 / 1,233 | 78 | ❌ 无 | 🔴 未对照 |
| `task` | `task-proto` | — / — | — | ❌ 无（§3.4 覆盖复制忠实） | 🔴 消费方式未验 |
| `follow` | `follow-rpc` | 17 / 2,655 | 65 | ❌ 无 | 🔴 未对照 |
| `follow` | `follow-api` | 27 / 2,850 | 49 | ❌ 无 | 🔴 未对照 |
| `follow` | `follow-common` | 4 / 558 | 37 | ❌ 无 | 🔴 未对照 |
| `follow` | `follow-proto` | — / — | — | ❌ 无 | 🔴 消费方式未验 |
| `favorite` | `favorite-api` | 31 / 2,920 | 69 | ❌ 无 | 🔴 未对照 |
| `favorite` | `favorite-common` | 3 / 453 | 25 | ❌ 无 | 🔴 未对照 |
| `favorite` | `favorite-proto` | — / — | — | ❌ 无 | 🔴 消费方式未验 |
| `comment` | `comment-rpc` | 40 / 7,455 | 46 | ❌ 无 | 🔴 未对照 |
| `comment` | `comment-api` | 33 / 2,838 | 22 | ❌ 无 | 🔴 未对照 |
| `comment` | `comment-common` | 4 / 537 | 27 | ❌ 无 | 🔴 未对照 |
| `comment` | `comment-proto` | — / — | — | ❌ 无 | 🔴 消费方式未验 |

### 🕘 历史（2026-10-23 快照）：其余两个 common

| 组件 | main/LOC | tests | 状态 |
|---|---|---|---|
| `message-common` | 3 / 453 | **0** | 🔴 零测试 + 无专属台账（§_INDEX 状态更正 #4 已列「组件缺口」） |
| `users-common` | 11 / 995 | 11 | ✅ 被 [`users/_shared.md`](../users/_shared.md) 覆盖 |

### 🕘 历史（2026-10-23 快照）：proto 层（10 个）

✅ §3.4 已验「复制忠实」（message 名/字段名/字段号/类型/枚举 10/10 逐条一致，实测产出 88 java + 4 `*Grpc`）。
⚠️ **§3.4 效力边界第 1 条仍然有效**：**不证明 RAG 消费生成代码的方式**与 Go 一致（`optional` presence、字段号被硬编码、Jackson 配置）。
⇒ `task` / `follow` / `favorite` / `comment` 四域的 proto 连 §3.4 都没过（12:25 才搬入，仅做过一次性逐行 diff）。

---

## 🔴 绿灯底下的空洞：`@EnabledIfDockerAvailable` 静默全跳

**实测（2026-10-23 20:2x）**：`task-rpc` 的 `TaskProgressUpsertParityTest` **15 条全部 skipped**，
surefire XML 里的原因字面写着：

```
<skipped message="Docker is not available"/>
```

**但 Docker 明明可用** —— 同一次构建里，另外三个真起容器的测试类全部正常执行：

| 类 | 结果 |
|---|---|
| `FavoriteSchemaParityTest` | `tests=13 skipped=0` ✅ 真起 PG |
| `CommentSchemaPgGateTest` | `tests=18 skipped=0` ✅ 真起 PG |
| `FollowRpcWiringTest` | `tests=20 skipped=0` ✅ 真起 PG |
| `TaskProgressUpsertParityTest` | `tests=15 skipped=15` 🔴 **静默全跳** |

**唯一差别**：只有它加了 `@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable`（`TaskProgressUpsertParityTest.java:69`）。

⚠️ **危险等级特别高**，三个原因叠加：

1. **类名带 `Parity`** —— 读报告的人极容易把「有个叫 Parity 的测试类」误读成「已做 Go 对照」，而它一条都没跑；
2. **reactor 报 `BUILD SUCCESS`** —— 没有任何一处红灯提示你少跑了 15 条**唯一直接测 MyBatis-Plus upsert 与 Flyway 建表**的用例；
3. **它测的正是 AGENTS.md §3.3 点名的盲区** —— 注释里自己写着「这一步不能省：验证 Flyway 是否真跑 + MyBatis-Plus 是否真产出 SqlSessionFactory —— 这两条都是『编译通过 + 纯 mock 单测全绿』永远发现不了的装配问题」。

### ✅ 根因已定性（2026-10-23 20:31 实测，不是推测）

**不是 Docker 不可用，是 Ryuk 回收容器拉不下来。** 最小探针（裸 `java`，非 surefire 环境）实测：

```java
DockerClientFactory.instance().client();
// ❌ org.testcontainers.containers.ContainerFetchException :
//    Can't get Docker image: RemoteDockerImage(imageName=testcontainers/ryuk:0.14.0)
```

**完整因果链**（每一环都有实测）：

1. `@EnabledIfDockerAvailable` 在**类级别**求值 ⇒ 在测试方法跑之前就要拿 Docker client；
2. 该注解的实现（`testcontainers-junit-jupiter:2.0.5` → `DockerAvailableDetector.isDockerAvailable()`）
   调的是 **`DockerClientFactory.instance().client()`**，而 `client()` 会**顺带确保 Ryuk 资源回收容器可用**；
3. Ryuk 镜像拉取失败 ⇒ 抛 `ContainerFetchException`；
4. `DockerAvailableDetector` 的实现是 `catch (Throwable ex) { return false; }` ——
   **把「基础设施拉镜像失败」和「Docker 真不可用」压成同一个 `false`**；
5. ⇒ 整个测试类被判 `disabled("Docker is not available")`，**15 条一条不跑**，而 reactor 报 `BUILD SUCCESS`。

**为什么别的 Testcontainers 类没事**：`task-rpc` 是**唯一漏配 Ryuk 关闭的容器模块**。
全仓实测（`grep RYUK_DISABLED` 逐 pom 扫描）：

| 有 `RYUK_DISABLED` surefire 注入 | 无 |
|---|---|
| `article-rpc` `comment-rpc` `favorite-api` `favorite-rpc` `follow-rpc` `like-rpc` `message-rpc` `admin-api` `admin-rpc` `user-api` `user-rpc` | 🔴 **`task-rpc`** |

其余类不加 `@EnabledIfDockerAvailable`，容器是**懒启动**的 ⇒ 判定阶段不触发 Ryuk ⇒ 照常跑（`skipped=0`）。

### 🔴 元教训（比这个 bug 本身更重要）

1. **`@EnabledIfDockerAvailable` 把两类完全不同的失败压成同一个 skip** ——
   「环境不支持 Docker」与「基础设施拉不到镜像」在报告里长得一模一样（`Docker is not available`）。
   ⇒ **它的 `message` 不可信，不要拿它当诊断依据。**
2. **新模块加 `@Testcontainers` 时若漏配 Ryuk，会静默 disable 而不是报错** ——
   且**恰好是唯一负责真 PG 对照的那个类**。
3. **绿灯必须额外断言 `skipped 数 = 0`**（见下）——「tests 全绿」不构成「真 PG 已验」的证据。

### 修法（照同仓既有写法，不重新设计）

给 `service/task/task-rpc/pom.xml` 补与 `favorite-rpc` / `follow-rpc` **同款**的 surefire
`environmentVariables` 注入（各模块 pom 内已有详细注释说明「`testcontainers.properties` 与 `-D` 均无效」，
**不要改成别的方式**），并删除 `TaskProgressUpsertParityTest.java:69` 的 `@EnabledIfDockerAvailable`——
Ryuk 已禁用时该注解不再有任何作用，留着只会继续掩盖失败。

⚠️ **本条只诊断，未实施**：修它属于「改代码」，超出本轮「更新文档状态」的范围，等你点头再动。

### 对「真 PG 守门测试」这条证据强度的直接影响

2026-10-23 用户为本轮选定的证据强度是**真 PG 守门 + 双侧活体对拍**（§3.3 理想态）。
⇒ **在 `@EnabledIfDockerAvailable` 这个写法存在的前提下，「tests 全绿」不能作为「真 PG 已验」的证据** ——
必须额外断言「该类 skipped 数 = 0」。这条应写进守门脚本，而不能靠人眼扫 reactor。

---

## 🕘 历史（2026-10-23 用户定的排序）—— **已执行完毕**

**用户决策**：① 主攻**零台账的 14 个**；② **一个微服务一个微服务**做；③ 证据强度取**最严**（真 PG 守门 + 双侧活体对拍）。

### 微服务内部排序（硬约束：rpc 必须先于 api）

§3.5 的排序约束「rpc 必须先于 api」在本轮**强制生效** ——
Go 侧边界是 api→rpc，rpc 没验对就先验 api，产出的是「RAG api 行为正确」的假结论。

| 序 | 微服务 | 本域要做的组件 | 理由 |
|---|---|---|---|
| 1 | **`task`** | `task-rpc` → `task-api` → proto 消费 | **无 `-common`**（Go 侧同样没有，不是遗漏）⇒ 依赖面最小，最适合起步；且它有 15 条静默 skip 的 PG 用例要先救活 |
| 2 | **`follow`** | `follow-rpc` → `follow-common` → `follow-api` → proto 消费 | `follow-rpc` 已有 20 条真 PG 容器用例 ⇒ 证据基础最厚 |
| 3 | **`favorite`** | `favorite-rpc` **续台账** → `favorite-api` → `favorite-common` | **唯一横跨两批的域**：rpc 已有 190 行台账可续，api/common 是空白 |
| 4 | **`comment`** | `comment-rpc` → `comment-common` → `comment-api` → proto 消费 | LOC 最大（rpc 7455）；`comment-api` 缺 gRPC client 熔断（Go `zrpc` `Breaker` 默认 `true`）已登记未修 |

### 每域开刀前的固定动作（§3.3 纪律，不可省）

1. **先让它真的启动一次** —— `2026-10-23 修复轮之前`实测 `hot-rpc`/`like-rpc` **根本起不来而测试全绿**；
2. **确认 skip 数 = 0** —— 绿灯不等于真 PG 跑过（见「空洞」节）；
3. **断言 `file:行号` 指向消费点**，不是声明点；
4. **先改/删错误断言，再改实现** —— 否则会先撞上「测试红了」而误以为自己改错了。

---

## 与既有文件的关系

| 文件 | 关系 |
|---|---|
| [`../_INDEX.md`](../_INDEX.md) | 本文件是它的「组件级」展开；`_INDEX.md` 只留一行指针 |
| [`new-domains-2026-10-23.md`](./new-domains-2026-10-23.md) | 记录四域 **proto 层**首轮对照；本文件记录四域 **api/rpc/common 落地与覆盖** |
| [`open-items.md`](./open-items.md) | 已知未修表（本文件不重复其内容，只在「证据强度」维度引用） |
| AGENTS.md §0 / §3.5 | 只留结论 + 指向本文件 |