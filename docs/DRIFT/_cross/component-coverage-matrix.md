<!-- @new 2026-10-23 20:2x · 38 组件台账覆盖矩阵（一次真实执行产出） -->

# 组件级台账覆盖矩阵（38 组件）

> 🟥 **⚠️ 本文件是 2026-10-23 的快照，已过期（2026-10-25 10 域并发对齐合并后）**
> 「零台账 14 个 · 下一批主攻」这些结论**已被 10-25 的对齐轮解除** ⇒ **不要拿本表当现状读**。
> 当前状态**一律回各域台账**（`docs/DRIFT/<域>/<组件>.md`）与 [`../_INDEX.md`](../_INDEX.md) 的「按域索引」。
> ⚠️ 仍然有效的是本表里**不依赖那一轮的**部分：全量基线（26 模块 / 1630 测试）、`@EnabledIfDockerAvailable` 静默全跳的根因链、以及「rpc 必须先于 api」的排序约束。

> **核对时间：2026-10-23 20:06–20:30**（真实执行：`find` + `wc -l` 逐模块扫描 + `mvn -o -T1C install` + `mvn -o -T1C test` + surefire XML 汇总）
> **本文件回答一个问题：38 个组件里，哪些做过 Go ↔ RAG 对照、证据强度到哪一层、下一刀该切哪。**
>
> ⚠️ 按 §6.2 约定 2：**无核对日期的断言一律按 `UNKNOWN` 处理**。本文件所有数字都来自上列命令，不是推断。

---

## 🔴 本文件推翻的三条既有结论

| 既有断言 | 位置 | 实测 | 处置 |
|---|---|---|---|
| 「`follow-api` 与 `favorite` api/rpc **仍 0 行** ⇒ 3 个组件无对照对象」 | AGENTS.md §0 · `_INDEX.md` L32 · `new-domains-2026-10-23.md` L19 | 🔴 **已全部作废**：`follow-api` 27 文件/2850 行、`favorite-api` 31/2920、`favorite-rpc` 36/7057、`follow-rpc` 17/2655、`comment-rpc` 40/7455 | 四份文件已同步更正 |
| 「**api / rpc / common 共 11 个组件仍是空壳**」 | `_INDEX.md` L32 | 🔴 **作废**：38 个组件**无一是空壳**（`<10 行`的 java 全是 interface 声明或 3 字段 DTO，非骨架） | 本文件「全量基线」节 |
| 「11 个组件零台账」 | 本文件初稿口径 | ✅ **成立且扩大**：**14 个**组件零台账（`comment` 4 + `follow` 4 + `task` 3 + `favorite` 的 api/common/proto 3） | 见下方矩阵 |

---

## 全量基线（2026-10-23 20:1x–20:2x）

| 项 | 实测值 | 命令 |
|---|---|---|
| 全量编译 | ✅ **`EXIT=0`**（`mvn -o -q -DskipTests -T1C install`） | 38 个组件无一带病 |
| 全量测试 | ✅ **BUILD SUCCESS**，`1630 tests / 0 failures / 0 errors / 15 skipped` | `mvn -o -T1C test` + surefire XML 汇总 |
| 实际执行用例 | **1615**（1630 − 15 skip） | 同上 |
| 空骨架残留 | **0** —— `<10 行`的 java 共 11 个，全是 `interface` 单行声明（`LikeFactDispatchService` 等）或 3 字段 DTO | `find … -exec wc -l` 过滤 |

⚠️ **「1630 条全绿」不等于「对齐了 1630 条」**，见下方「绿灯底下的空洞」。

---

## 🔴 组件级矩阵（38 个）

**图例**：台账 = 有无专属台账文件 · 强度 = 证据强度分层 · 状态 = 对照结论

### 已有台账（16 个）

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

### 零台账（14 个）—— 下一批主攻

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

### 其余两个 common

| 组件 | main/LOC | tests | 状态 |
|---|---|---|---|
| `message-common` | 3 / 453 | **0** | 🔴 零测试 + 无专属台账（§_INDEX 状态更正 #4 已列「组件缺口」） |
| `users-common` | 11 / 995 | 11 | ✅ 被 [`users/_shared.md`](../users/_shared.md) 覆盖 |

### proto 层（10 个）

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

## 下一步：排序（2026-10-23 用户定）

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