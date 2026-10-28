<!-- 由 `align/g1-users-follow` 工作树产出 · 基线 commit 7ec6fc6（= `do-my-own-gh`）· 核对日期 2026-10-28 -->

# 阶段 1 准入判定 · `follow` 域

> **目标口径**：[`docs/PhasedGoal.md`](../../PhasedGoal.md)（= [`twoStage.md`](../../twoStage.md) §2.1 **逐字相同**）· **流程义务**：`twoStage.md` §5.1~§5.7。
> **本文件只覆盖阶段 1**（AGENTS.md §5.3g：lane 只做阶段 1、只做本域）⇒ ⛔ 不碰 `twoStage.md` §1.3 / §4 / §6 / §7。
> **被审范围**：`service/follow/{follow-rpc,follow-api,follow-common,follow-proto}`。
> ⚠️ `follow-common` 是**本域私有**（7 个真私有 `<域>-common` 之一，守门 `check-wave-lane-ownership.py` 已证无跨域引用）⇒ **写权归 G1**；
> 🔴 但 `service/common`（**Go 侧 `service/common`，跨服务共享层**）**只读** ⇒ 其缺陷走反馈单转派 G4。
> **真源**：`Sea-RideTheWind_golang/service/follow/{api,rpc}/`（冻结，只读）。
> ⚠️ **本文件里的 ✅ 一律读作「在该证据强度下一致」**，不是已闭环。

---

## 0. 本域事实基线

| 组件 | Maven 模块 | 对外协议 | 端点 / 方法 | 主源码 | 测试文件 |
|---|---|---|---|---|---|
| `follow-rpc` | `service/follow/follow-rpc` | gRPC | 8 个（follow / unfollow / block / unblock / getFollowList / getFollowerList / getBlockList / getRecommendations） | 16 | 15 |
| `follow-api` | `service/follow/follow-api` | HTTP `/follow/v1/*` | 8（follow / unfollow / block / unblock / follow/list / follower/list / block/list / recommendations） | 31 | 12 |
| `follow-common` | `service/follow/follow-common` | 域内共享（DTO / 错误码） | — | 4 | 3 |
| `follow-proto` | `service/follow/follow-proto` | proto 契约 | — | — | — |

**核心调用链**

```
follow-api (HTTP :8891)  ──gRPC plaintext──▶  follow-rpc (:8082)  ──▶ PG  follow_relation / block_relation
                                                              └─▶ user-rpc (gRPC)  ensureUserExists（service/follow/rpc/internal/logic/helper.go:82）
```

- **跨域依赖**：`follow-rpc` → `user-rpc`（**用户存在性校验**）⇒ follow 的可用性**依赖 users 域** ⇒ users 域的 P0 判定会传导到本域。
- **持久化**：Flyway 2 个迁移（`V1__create_follow_relation.sql` / `V2__create_block_relation.sql`）· **独立账本** `flyway_schema_history_follow`（§6.3 问题 4）。
- **幂等性的物理载体**：`uk_follow_pair` / `uk_block_pair` **唯一索引** ＋ 写入走 `ON CONFLICT (user_id, target_id) DO NOTHING`（`FollowRelationMapper#insertIgnoreConflict`）⇒ **重复关注 / 重复拉黑 = 静默成功**（与 Go 同形，**不是** 5008）。
- **事务**：`FollowBlockTransactionService.java:70` `@Transactional(rollbackFor = Exception.class)` ⇒ **事务边界放在独立 bean 上**，从结构上**规避了 AOP 自调用陷阱**（本文件 §3 `S1-FOLLOW-006` 引为正面范例）。

---

## 1. 阶段 1A：P0 九条逐条判定（§5.1 七项口径）

| # | P0 要求 | 当前实现情况 | 实现是否正确 | 是否已有验证证据 | 证据位置 | 是否影响简历项目准入 | 是否需要修改 | 可否暂缓至阶段 2 |
|---|---|---|---|---|---|---|---|---|
| **1** | 核心业务链路真实运行，无骨架 / 模拟数据 / 硬编码成功 | 8 个 RPC + 8 个端点全部实装；推荐算法（`getRecommendations`）含完整分支逻辑 | ✅ | ✅ **L4**：双侧活体对拍 **52/52 逐字节一致**（协议 20 + 数据路径 16 + 边界/鉴权 16） | [`api.md`](api.md) 结论节 · [`rpc.md`](rpc.md) 结论节 | ❌ 不影响 | ❌ 不需要 | — |
| **2** | 参数校验 / 业务规则 / 身份认证 / 权限控制有效 | 验签在 `GoStyleJwtAuthFilter`（**3s 预算之外** ⇒ `DRIFT-FOLLOW-010` 已修）；黑名单 **fail-open**（Go 同形）；uid 只来自 token | ✅ | ✅ L4 | [`api.md`](api.md) §7.3（3s 预算作用域）/ U2 / U6 / U7 | ❌ 不影响 | ❌ 不需要 | — |
| **3** | 关键数据正确持久化，事务边界合理 | DDL 与 GORM 产物**逐字节一致**（三重确认）；唯一索引即幂等载体；事务在**独立 bean** 上 | ✅ | ✅ L4 | [`rpc.md`](rpc.md) 「DDL：与 GORM 产物逐字节一致」节 · V1/V2 迁移文件注释 | ❌ 不影响 | ❌ 不需要 | — |
| **4** | 核心失败路径有明确行为 | 下游挂死 ⇒ Go 2s ⇒ 200+1015，RAG 3s ⇒ 503 ⇒ **`DRIFT-FOLLOW-009` 已修**；Redis **超时**口径 ⇒ **`DRIFT-FOLLOW-011` 已修**；Redis **断连** ⇒ Lettuce 默认排队挂到 3s ⇒ 503 ⇒ **`DRIFT-FOLLOW-012` 已修**（改回 Go 的 fail-open 200） | ✅ | ✅ L4（受控故障注入） | [`api.md`](api.md) §DRIFT-FOLLOW-009/011/012 三节 | ❌ 不影响 | ❌ 不需要 | — |
| **5** | 能按**实际项目说明**完成构建 / 配置 / 启动 | 域内：4 个组件可构建；`follow-rpc` / `follow-api` 可裸 jar 起。⚠️ **域内无编排**：`docker-compose.yaml` 只编排 `user-rpc` + `user-api` ⇒ **follow 域没有 compose 入口**；且项目级「实际项目说明」不存在（见 §3 `S1-FOLLOW-004` + users 档 `S1-USERS-001`） | 🟡 **域内可启动 · 无编排无说明** | ⚠️ 无「按说明启动」的证据（说明不存在） | [`api.md`](api.md) §7.5（2026-10-27 真启动 A/B/C 对照） | 🟡 **域级不阻断**（能构建能起）· **项目级阻断**（同 users 档 `S1-USERS-001`） | 🔴 域外 ⇒ 上报 | 🟡 编排可暂缓 |
| **6** | 核心业务有可执行自动化测试，且断言真实业务结果 | 30 个测试文件（15 + 12 + 3）；含 `FollowServiceGrpcImplTest` / `FollowRecommendationServiceTest` / `FollowUserExistenceServiceTest` / `FollowErrorCodeContractTest` / `FollowErrorMessagesContractTest` 等 | ✅ | ✅ 已执行 | 见 §5.1 | ❌ 不影响 | ❌ 不需要 | — |
| **7** | 模块职责清晰，核心调用链可追踪 | 4 组件职责边界清晰；台账有**逐文件对照索引**（**32 个 Java ↔ 24 个 Go，全量无遗漏**） | ✅ | ✅ | [`api.md`](api.md) §「逐文件对照索引」 | ❌ 不影响 | ❌ 不需要 | — |
| **8** | 关键配置和敏感信息无明显不安全处理 | `follow-rpc` 的 datasource 口令默认值**已是全仓多数派** `password`（本轮 T2 前就已一致）⇒ 本域**无分裂**；JWT secret 的 fallback 与 Go 侧硬编码值逐字一致且可 env 覆盖 | ✅ | ✅ 静态可证 | §9 R2 | ❌ 不影响 | ❌ 不需要 | — |
| **9** | 已知严重缺陷 / 无法验证的关键能力 / 运行前提明确记录 | 三档台账 + `vs-common.md` 齐备；本轮新增登记见 §3 | ✅ | ✅ | [`_shared` 类档](../users/_shared.md) 同体例 · 本域 [`api.md`](api.md) / [`rpc.md`](rpc.md) / [`common.md`](common.md) / [`proto.md`](proto.md) | ❌ 不影响 | ❌ 不需要 | — |

### 1.1 P0 判定小结

- **本域 P0#1~#4 / #6~#9 全部满足**，且证据强度是 **L4（活体对拍 + 真栈）**。
- **P0#5 域级不阻断**（能构建能起），**项目级阻断**（说明书不存在 ⇒ 与 users 域同一个 `S1-USERS-001`）。
- 本域**没有 A 类阻断项**；A 类阻断项只有一条，且是**项目级 + 域外**。

---

## 2. 阶段 1B：最小必要实施计划（§5.2 八项口径）

| 任务 | 关联问题 | 修改范围 | 理由 | 依赖条件 | 预期行为 | 验收条件 | 验证命令 / 测试方案 |
|---|---|---|---|---|---|---|---|
| **T3** | `S1-FOLLOW-005` | `service/follow/follow-rpc/src/main/resources/application.yml:103` | 端口硬编码 ⇒ 本机 8082 被 34 天前的 `python -m http.server` 占着 ⇒ **组件无法真启动**；且同域 `follow-api` 已有 `${FOLLOW_API_PORT:8891}` 先例 | 无 | 默认端口**一字不变**（8082），可 env 覆盖 | 默认不变 + 覆盖后**注册地址与监听端口一致** | §5.2 真启动断言 |
| **T5** | — | 域级回归 + 真启动 | 阶段 1E | T3 后 | 4 组件真进程起来 + 核心链路通 | §5 验收表 | §5.1 / §5.2 |
| **T6** | — | P1 机制闭环（3 个） | 阶段 1E | — | 9 问闭环 | §4.3 | §4.3 |
| **T7** | — | 台账回写 | 阶段 1C | — | 落 `docs/DRIFT/follow/**` | 守门绿 | §9 R7 |
| **T8** | `S1-FOLLOW-003`（**`S1-FOLLOW-002` 已销号** —— 2026-10-25 签字偏离 `SD-2026-10-25-01` ⇒ 不修、不派单） | 🔴 **共享层**（`service/common`）⇒ **本域只登记** | 该缺陷的修法落在 `service/common` ⇒ **写权唯一属 G4** | G4 落码 | — | 反馈单 7 项齐全 | §6 反馈单 |

---

## 3. 阶段 1C：差异与暂缓项清单（§5.3 十一项，逐条填满）

### `S1-FOLLOW-001` 🟡 **B 类** · `follow-rpc` 的 5 个指标**无任何抓取出口**（跨域方案待定）

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-001`（= 台账里的 `DRIFT-FOLLOW-006`） |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/follow/rpc/internal/metrics/metrics.go` + go-zero `DevServer` ⇒ Go 的 follow-rpc **有** `/metrics` 出口（实测抓到 127 行 `follow_rpc_*`） |
| 3 | 原始行为 | Go 的 rpc 进程同时起一个 DevServer 暴露指标 |
| 4 | 阶段 1 当前行为 | RAG 侧 `FollowRpcMetrics.java` 定义了 4 类 5 个 `follow_rpc_*` 指标（`request_total` / `relation_action_total` / `db_error_total` / `recommendation_size` / Timer），`application.yml:159-180` 也配了 `management.endpoints.web.exposure.include: prometheus,metrics` ⇒ **但 `follow-rpc` 是纯 gRPC 进程，没有 HTTP server** ⇒ `/actuator/prometheus` **无处可开** ⇒ **零出口** |
| 5 | 具体差异 | 指标**在产生**（`MeterRegistry` 正常注册）但**抓不到** ⇒ 可观察性缺一环。⚠️ 同款缺口覆盖 **5 个 rpc 模块**（`favorite-rpc` / `message-rpc` / `like-rpc` / `user-user-rpc` / `user-admin-rpc`）⇒ **不是一个域的缺陷** |
| 6 | 暂缓 / 简化理由 | **修法是「全仓 rpc 层统一方案」**（给哪些 rpc 开 management 口？端口怎么排？`prometheus/settings.yml` 的 scrape job 怎么加？），2026-10-24 用户已定「本轮不落地，转全仓统一方案」⇒ **G1 单方面落地 = 替全仓做技术决策** ⇒ 不做 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **不影响**业务行为；影响**问题定位能力**（rpc 侧出问题看不到指标） |
| 8 | 是否阻断阶段 1 | ❌ **否**（P0 不要求指标；P1 的 9 个候选机制里可另选 3 个 ⇒ 不靠它） |
| 9 | 阶段 2 的恢复或替换方案 | ① 统一方案：给所有 rpc 组件开**独立 management 端口**（与管理 HTTP 端口分离，避开 `management.server.port: -1` 的现做法）⇒ ② `prometheus/settings.yml` 加对应 scrape job；③ 对齐 Go 的 DevServer 语义 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：起 `follow-rpc` ⇒ `curl :<mgmt-port>/actuator/prometheus` 能抓到 5 类 `follow_rpc_*`；Prometheus 端能抓到；通过标准 = 抓到的**指标名与标签集**与 Go 侧 127 行逐条对齐 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`FollowRpcMetrics.java`（4 类 5 指标）· `application.yml:159-180`（exposure 已配）· `follow-rpc` **无 `server.port`**（纯 gRPC）⇒ 出口不存在 · 同款缺口 5 处见 `follow/rpc.md` 「跨域派单（5 处同款缺口）」节。**缺口**：全仓统一方案未拍板 |

### `S1-FOLLOW-002` ✅ **已签字偏离 ⇒ 不修**（**2026-10-29 订正**：原写「🔴 共享层待办 · 派 G4」）

> 🔴 **本条在本轮（2026-10-29 · G1 lane 阶段 1 复核）被推翻**：原先登记为
> 「🔴 共享层缺陷 ⇒ 修法落在 `service/common` ⇒ 派 G4 落码」
> ⇒ **该登记是陈旧的**：该差异已于 **2026-10-25 经用户签字**为**有意偏离**。
> ⇒ ⇒ **不是缺陷、不进差异与暂缓项清单、不派单**（AGENTS.md §0 第 1 条 / §5.3b：
> 签字偏离**不重复质疑**，更不得「顺手改回 Go 行为」）。
> ⇒ **决策真源** ⇒ [`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md)
> 的 **`SD-2026-10-25-01`**（签字决定 = **保留 RAG 的全局闸门，不改成 per-route**；
> 签字档「对应的 DRIFT 条目」栏**明写 = T-2 = `DRIFT-FOLLOW-008` = 本条**）。

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-002`（= 台账里的 `DRIFT-FOLLOW-008`）**⇒ 已销号（转为签字偏离 `SD-2026-10-25-01`）** |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12` ⇒ 闸门**按 `route.Path` 建立** ⇒ 每条路由各有一份并发上限 |
| 3 | 原始行为 | 限流**按路由** ⇒ `/follow/v1/follow` 与 `/follow/v1/recommendations` 各自独立计数 |
| 4 | 阶段 1 当前行为 | RAG 侧 `service/common` 的 `GoStyleMaxConnsFilter` 是**全局一个**闸门 ⇒ 所有路由共享一份并发计数 |
| 5 | 具体差异 | ① 并发语义：Go 侧单路由打满**不影响**其它路由；RAG 侧**互相拖累**；② 与 users 侧的 `DRIFT-USERAPI-013` **同族** ⇒ 两条必须一起对账，**不能各修各的**（**同族 ≠ 同一条**：users 侧那条的 MaxConns 半边**也已签字** ⇒ 真正还开放的只剩 breaker 键空间半边 ⇒ 归 users 侧那条） |
| 6 | 暂缓 / 简化理由 | —— **不是暂缓，是「决定不修」**（签字偏离） |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **签字时的判断**：① Tomcat 默认 `max-threads=200` ⇒ 请求**先在容器层排队** ⇒ 理论上到不了 10000 在途 ⇒ 两种口径在当前部署下**不可观察**；② **方向上 RAG 更严**（全局总量受限 vs 单路由受限）⇒ 不是「放宽」类风险 ⇒ ⇒ **不影响阶段 1 准入** |
| 8 | 是否阻断阶段 1 | ❌ **否**（且**不是**待办项 ⇒ 不进差异清单） |
| 9 | 阶段 2 的恢复或替换方案 | —— **阶段 2 也不做**（签字决定保留全局闸门）<br>⚠️ **若将来要重开**：必须**先撤销签字**（用户拍板）⇒ 再按「两侧都应是 Y = 每路由一个 latch」落码 ⇒ 且与 breaker 键空间半边**同批** |
| 10 | 阶段 2 的测试场景和通过标准 | —— **不适用**（不实施）<br>⚠️ 若撤销签字后重开：场景 = 并发打满路由 A ⇒ 路由 B **仍可服务**；通过标准 = A 被拒时 B 的成功率不下降（与 Go 侧同口径对拍） |
| 11 | 当前证据及剩余证据缺口 | **证据**：签字档 `SD-2026-10-25-01`（2026-10-25 用户签字 · L1 决策记录）· 共享层代码 javadoc `GoStyleMaxConnsFilter.java:67-72`（已指向签字档 + 明文「**不要**因为『Go 是 per-route』就把它当缺陷顺手改回」）· global 收口档 `docs/DRIFT/global/_README.md:88`（T-2 ✅ 已签字 ⇒ 已移出 DRIFT 档）· hot 域 `docs/DRIFT/hot/vs-common.md:31`（`D27` 同族已闭合）· users 域 `docs/DRIFT/users/vs-common.md:2735`（「MaxConns 半边 = 已签字偏离」）· 本域已同步订正 [`api.md`](api.md) + [`vs-common.md`](vs-common.md) §1/§1.1/§A3/§6.2。**缺口**：**基线层**（`docs/PhaseGoal-judge.md` 的 A-2 `follow-api` 行 + 机器生成的 `docs/PhaseGoal/g1.md`）**仍把它记成开放缺陷 ⇒ 待重新拍板** ⇒ **不在 G1 写权面 ⇒ 已上报收口** |

### `S1-FOLLOW-003` 🔴 **共享层** · 纯 gRPC 进程缺 `servlet-api` ⇒ **关闭路径**抛 `NoClassDefFoundError`

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-003`（= 踩坑档里的 `PITFALL-FOLLOW-005`） |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 rpc 进程**没有 servlet 依赖** ⇒ 关闭路径不会触碰任何 web 类型 |
| 3 | 原始行为 | Go 的 rpc 进程关闭时只做 etcd / PG / Redis 的收尾，**不可能**因缺 web 类型而炸 |
| 4 | 阶段 1 当前行为 | RAG 的 `service/common`：`HttpMiddleware.java:8` `import jakarta.servlet.Filter` + `ObservabilityLifecycle.java:54` 的 **destroy 路径**会触及该类型 ⇒ 而 `follow-rpc` 的运行时 classpath **无 servlet-api** ⇒ **进程关闭时**抛 `NoClassDefFoundError: jakarta/servlet/Filter` |
| 5 | 具体差异 | ① **关闭路径**（不是启动路径）⇒ 服务**能正常起**、**能正常服务** ⇒ 不影响 P0#1；② 但**关闭日志被污染** ⇒ 关闭原因不可辨；③ 受害面 = **所有纯 gRPC 进程**，不只 follow |
| 6 | 暂缓 / 简化理由 | **修法落在共享层 `service/common`** ⇒ 🔴 写权归 G4 ⇒ 本域只登记 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响业务行为；影响**关闭路径的可诊断性**（例如优雅停机失败被误判成「业务没关干净」） |
| 8 | 是否阻断阶段 1 | ❌ **否**（不影响启动与服务；是「关闭路径卫生」问题） |
| 9 | 阶段 2 的恢复或替换方案 | ① 把 `ObservabilityLifecycle` 的关闭路径与 **web 类型**解耦（延迟类加载 / 反射隔离 / 拆出 web-only 的 lifecycle 组件）；② 补一条守门：纯 gRPC 进程的 classpath **不得**含 servlet-api ⇒ 关闭路径不得触及 web 类型 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：起 `follow-rpc` ⇒ **正常关闭** ⇒ 关闭日志**零** `NoClassDefFoundError`；通过标准 = 关闭 stderr 为空且退出码 0 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`follow/vs-common.md` §1 表（本轮已复核仍在）· `follow/rpc.md` 待办 2（同一件事的更早记录）。**缺口**：共享层修法需 G4 落码；`service/common` 的**关闭路径**当前无守门 |

### `S1-FOLLOW-004` 🟡 **B 类** · `follow` 域**无编排入口**（compose 只含 users 两个组件）

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-004` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `manage.sh` 有 **16 个服务**的端口分配与启动编排（`get_service_port()`）⇒ 每个域都有 `service/<域>/<api\|rpc>` 两个可启动进程 |
| 3 | 原始行为 | Go 侧 `follow/api`（:8891）与 `follow/rpc`（:16065）都是**可独立启动的进程** |
| 4 | 阶段 1 当前行为 | RAG 侧 `docker-compose.yaml` 只编排 `user-rpc` + `user-api`（外加 6 个基础设施容器）⇒ **`follow-rpc` / `follow-api` / `admin-rpc` / `admin-api` 都没有 compose 入口** ⇒ 只能裸 jar 起（可行，但**无一条命令可复制**） |
| 5 | 具体差异 | ① 编排覆盖 **2/6** 个业务组件；② 项目说明缺失（`S1-USERS-001`）⇒ 「怎么把 follow 跑起来」**没有任何文档答案**；③ 验证只能靠手写 env + 裸 jar |
| 6 | 暂缓 / 简化理由 | 扩 compose 到 4 个组件 ⇒ 触及 `docker-compose.yaml`（**根级共享文件**，不在 G1 写权面）⇒ 登记 + 上报；**阶段 1 不阻塞**（裸 jar 可起） |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响运行时；影响**可演示性 / 可复现性**（简历项目最看重的那条） |
| 8 | 是否阻断阶段 1 | ❌ **否**（域级）；🔴 **项目级**与 `S1-USERS-001` 合并处理 |
| 9 | 阶段 2 的恢复或替换方案 | ① `docker-compose.yaml` 补 `follow-rpc` / `follow-api` / `admin-rpc` / `admin-api` 四个服务（端口沿用各自 yml）；② `prometheus/settings.yml` 补 scrape job（与 `S1-FOLLOW-001` 同批） |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：`docker compose up -d` ⇒ 6 个业务组件全部起来并注册进 Nacos；通过标准 = 一条命令拉起全栈且 `manage.sh status` 全绿 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`docker-compose.yaml` 的服务清单实测（`postgres`/`redis`/`nacos`/`jaeger`/`prometheus`/`user-rpc`/`user-api` + `default` 网络）· `manage.sh:77-82` 只构建 `user-rpc + user-api`。**缺口**：根级文件不在 G1 写权面 |

### `S1-FOLLOW-005` ✅ **已修** · `follow-rpc` 的 gRPC 端口硬编码 ⇒ 本轮补 env 覆盖

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-005` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/follow/rpc/etc/follow.yaml:2` = `ListenOn: 0.0.0.0:8082` |
| 3 | 原始行为 | 端口写在 yaml（固定值） |
| 4 | 阶段 1 当前行为（修前） | RAG 侧 `application.yml:103` = `port: 8082`（硬编码）⇒ 本机 8082 被一个**运行了 34 天**的 `python -m http.server 8082` 占着（实测 PID 9391）⇒ **该组件无法真启动**（不动用户进程的前提下）⇒ **阶段 1E 的真启动被卡死** |
| 5 | 具体差异 | 域内不一致：`follow-api` 已是 `${FOLLOW_API_PORT:8891}`（**可覆盖**），`follow-rpc` 不可覆盖 |
| 6 | 暂缓 / 简化理由 | **不暂缓** —— 域内 1 行，且与同域既有先例一致 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响默认行为（默认端口**一字不变**）；影响**可验证性 / 并行开发** |
| 8 | 是否阻断阶段 1 | ❌ 否（但**阻塞了本轮的真启动手段** ⇒ 属「验证前提」） |
| 9 | 阶段 2 的恢复或替换方案 | ① 阶段 1 已修（`${FOLLOW_RPC_PORT:8082}`）；② 阶段 2 把 6 个组件端口**统一**为可覆盖（`users`/`admin` 侧仍硬编码：`49210` / `8081` / `8889` / `49900`） |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：`FOLLOW_RPC_PORT=18082` 启动 ⇒ **Nacos 注册地址同步为 18082** 且 `follow-api` 能发现 ⇒ 链路通；通过标准 = 注册地址 ≡ 监听端口，且链路通 |
| 11 | 当前证据及剩余证据缺口 | **证据**：修前 `application.yml:103` 硬编码 + `lsof` 实测占用者 + `follow-api` 的 `${FOLLOW_API_PORT:8891}` 先例；修后见 §4.2 与 §5.2 的真启动实测。**缺口**：`users`/`admin` 侧端口仍不可覆盖（阶段 2） |

### `S1-FOLLOW-006` 🟢 **正面范例**（非缺陷）· 事务边界放在**独立 bean** 上 ⇒ 结构性规避 AOP 自调用陷阱

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-FOLLOW-006`（**不是缺陷**，是本轮**可复用的正面结论**） |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/follow/rpc/internal/model/follow.go` 的写路径由 GORM 闭包显式开启事务 |
| 3 | 原始行为 | 事务边界由**数据层显式闭包**决定 ⇒ 与调用方无关 |
| 4 | 阶段 1 当前行为 | RAG 侧 `FollowBlockTransactionService.java:70` = `@Transactional(rollbackFor = Exception.class)`，且**该类单独成 bean**（`FollowDomainService` 通过**注入**调用它 ⇒ **经代理**）；类注释还显式写明了「`@Transactional` 靠 AOP 代理生效，代理只拦截从容器外部进来的调用」这一陷阱 |
| 5 | 具体差异 | **无差异** ⇒ ✅ 且是**结构性正确**：不依赖「调用方永远从别的 Bean 调进来」这一**口头约定** |
| 6 | 暂缓 / 简化理由 | —— |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **正面**：本域的拉黑/取消拉黑多步写入是原子的 |
| 8 | 是否阻断阶段 1 | ❌ 否（✅ 满足 P0#3） |
| 9 | 阶段 2 的恢复或替换方案 | ✅ **无**；⚠️ **反向使用**：本条是 users 域 `S1-USERS-004`（头像链路事务依赖跨 Bean 调用）的**修法参照** ⇒ 阶段 2 把 users 侧改成同款「独立 bean 承载事务」 |
| 10 | 阶段 2 的测试场景和通过标准 | 参照 users 侧 `S1-USERS-004` 第 10 项（变异验证） |
| 11 | 当前证据及剩余证据缺口 | **证据**：`FollowBlockTransactionService.java:24`（陷阱注释）+ `:70`（`@Transactional`）· `follow/rpc.md` 逐维度结论。**缺口**：无 |

---

## 4. 阶段 1D：执行实际改造（§5.4 九项，逐条填满）

### 4.1 `T3` · `follow-rpc` 的 gRPC 端口改为可 env 覆盖

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `service/follow/follow-rpc/src/main/resources/application.yml:103` = `port: 8082`（**硬编码**）⇒ 本机 8082 被占 ⇒ 该组件**无法真启动**（不动用户进程的前提下）⇒ 阶段 1E 的真启动被卡死 |
| **具体问题** | 域内不一致（`follow-api` 用 `${FOLLOW_API_PORT:8891}`，`follow-rpc` 不可覆盖）⇒ 同一域两个组件端口策略不同；且**多棵 lane / 本机已有服务**时无法起进程 |
| **修改后的实现** | `port: ${FOLLOW_RPC_PORT:8082}` ⇒ **默认端口一字不变**（行为零变化），可 env 覆盖 |
| **修改理由** | ① 与同域 `follow-api` 的**既有先例**一致（不是新发明）；② 解除「端口被占 ⇒ 组件不可验证」的死结（**阶段 1E 的前提**）；③ 表达层改动，**不触碰 Go 行为**（Go 侧端口在 yaml 固定为 8082，RAG 侧默认值仍是 8082）⇒ 不构成漂移 |
| **实际变更文件** | `service/follow/follow-rpc/src/main/resources/application.yml`（1 行 + 5 行注释） |
| **测试场景和断言** | ① 静态：默认仍是 `8082`（§9 R3）；② 运行：`FOLLOW_RPC_PORT=<空闲口>` 真启动 ⇒ **Nacos 注册地址与监听端口一致**（devh 用同一端口既监听又注册 ⇒ 不同步 ⇒ `follow-api` 找到旧地址 ⇒ 业务码塌成 1015）⇒ §5.2 断言 |
| **实际执行结果** | ⏳ **落码完成，验证待 §5 回填**（本轮初稿曾直接写「已全绿」，属**先写结论后取证据** ⇒ 已改为待回填）<br>· 域级回归 ⏳ · 真启动 + 注册地址一致性 ⏳ |
| **尚未解决的问题** | `users` / `admin` 侧端口仍硬编码（`49210` / `8081` / `8889` / `49900`）⇒ 阶段 2 统一（`S1-FOLLOW-005` 第 9 项） |
| **对阶段 2 的影响** | 无冲突；阶段 2 可把 6 个组件的端口统一为可覆盖 |

### 4.2 `T11` · 🆕 **本轮落码 · 域侧自修** · 补齐**独立失败路径矩阵** ⇒ 基线 §九 `G-13` 在本域**闭合**

> **背景**：`docs/PhaseGoal-judge.md` P0 #2 的判据原文（「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限四项都有效」；且失败路径**域覆盖不全**）＋ §九 `G-13`（`article` / `follow` / `task` / `hot` 四域无独立失败路径矩阵）。
> ⇒ 本轮**在 follow 域补齐那份矩阵**，且**用真 Tomcat 跑**（原先 10 个测试类**全部** `webEnvironment = MOCK` ⇒ **真容器覆盖率为零**）。

| 落点 | 内容 | Go 出处（期望值逐条抄自 Go 源码） |
|---|---|---|
| 🆕 `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/support/RealTomcatPort.java` | 真 Tomcat 的**端口选取 + 双地址 bind 自检**（通配 `0.0.0.0` **与** 具体 `127.0.0.1` **两个都要探** ⇒ 只探一个会漏「静默被抢」）· 段 = **22340–22359**（follow 域预分配段 22300–22399 内、且**不与** `follow-api` 的 `RealGrpcPort` 22300–22319 / `follow-rpc` 的 22320–22339 重叠） | **测试设施** ⇒ Go 侧无对应物 ⇒ **不存在语义对照义务**（只影响测试能否稳定跑到被测代码，不影响任何对外行为）|
| 🆕 `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java` | **62 条**失败路径矩阵用例（**8 端点 × 4 类失败** + 基础设施类 + 路由字节级） | 见下表 |
| 🆕 `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java` | ② 段的「端口类个数」断言 **8 → 9**（新增 follow-api 的 `RealTomcatPort`）⇒ **同批更新，不留雷** | — |

**矩阵的四个失败类 × 8 端点（期望值逐条带 Go `文件:行号`，写在测试的 `Endpoint` 表里）**：

| 类 | 判据（Go 出处） | 可观察形态 | 覆盖 |
|---|---|---|---|
| **认证** | `rest/handler/authhandler.go:106`（401 空 body）· `middleware/checkblacklistmiddleware.go:42-48`（401 两字段）· `logic/follow/helper.go:13-33`（claim 取不到 ⇒ **5004**）| ① 无 token ⇒ **401 + 空 body + 无 Content-Type** ② 黑名单 ⇒ **401 + `{"code":5004,"msg":"unauthorized"}` + `application/json;charset=utf-8`**（**逐字节**）③ claim 缺失 ⇒ **HTTP 200 + `{code:5004}`**（**不是 401**）| **8 × 3 = 24** |
| **权限** | `getblocklistlogic.go:45-50` 与 `getrecommendationslogic.go:45-50`（**只有这两个**有 Forbidden 分支）| 查别人 ⇒ **200 + `{code:5005,msg:"forbidden"}`**，且 **RPC 完全不被调用**；`getfollowlistlogic.go:45` / `getfollowerlistlogic.go:45` **没有**该分支 ⇒ **公开可查** | **8** |
| **参数校验** | `types/types.go:25-27`（`RelationActionReq.target_id` **无 optional ⇒ 必填**）vs `types/types.go:6-10`（`FollowListReq` 三字段**全 optional**）| 动作端点缺必填 ⇒ **200 + `{code:5001}`**（**不是 400** —— follow api 注册了 `httpx.SetErrorHandler`）；list 端点 ⇒ 按**零值**继续 | **8** |
| **业务规则** | 8 个 logic 里逐条相同的 `if rpcResp.Code != int32(followcommon.Success) { return nil, followcommon.NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg) }` | **原样透传** rpc 的 code + msg | **8** |
| **基础设施** | `common/errmsg.go` `BizCodeFromError`（描述文本优先 → 再按 gRPC code 映射 → 兜底 1015）| rpc 抛 `UNAVAILABLE` ⇒ **200 + `{code:1015,msg:"service busy"}`** | **8** |

**另有 7 条单点用例**：404 逐字节（`"404 page not found\n"` ＋ `text/plain;charset=utf-8` ＋ `nosniff`）· 405 裸状态码（**无 body、无 Content-Type**、带 `Allow`）· 404/405 **不经过鉴权** · 拦截器顺序（**JWT 在外、黑名单在内** ⇒ 签名无效的 token 恒 401 空 body 且**不查 Redis**）· Redis 故障 **fail-open** · 端口段自检（防止 `webEnvironment` 被改回 `RANDOM_PORT`）。

⚠️ **证据强度口径**：本类的期望值**全部抄自 Go 源码**（每条带 `文件:行号`）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥ ⇒ **按 R3 计入**；
⚠️ **不按 R4 计**（R4 要求**双侧活体对拍 + 变异**）⇒ 本类**不是**双侧对拍，**不得**据此宣称「与 Go 逐字节一致」。
⇒ ⚠️ **P0#2 的证据覆盖因此提升**（4 域中 **follow 域已补**），但 **P0#2 本身仍受其余 3 域（article / task / hot）未补的制约** ⇒ **不因本条把 P0#2 判成 ✅**。

⚠️ **本轮没在 `tools/` 落脚本（主动选择 · 不是遗漏）**：用户已授权可写 `tools/difftest/follow/`（域专属子目录），但本轮**判定为不值得写**，三条理由：
① **价值近零** —— 矩阵的入口就是一条 `mvn -Dtest=…` 命令（§5.2.1 ＋ R14），再包一层 shell 脚本**不增加任何能力**；
② **会造第二份真源** —— `docs/PhaseGoal/g1.md` §5 已经是「本 lane 的收口命令清单」，再写一份 `run-*.sh` ⇒ **两份清单会漂移**（且 `g1.md` 是**机器生成物** ⇒ 由收口执行者重生成 ⇒ 手写脚本必然对不上）；
③ **写权归属模糊** —— `tools/difftest/follow/` **不在任何 lane 的白名单里**（`check-wave-lane-ownership.py` 的 `ACTIVE` 只声明了 `service/**` 与 `docs/DRIFT/<域>/**`）⇒ 落进去就是一个**无人拥有**的文件，落在共享工具树里 ⇒ 与 AGENTS.md §5.3c 的「共享面只有 G4 / 收口能写」冲突。
⇒ ⇒ **结论**：可复算入口写进**本域台账**（§5.2.1 ＋ §9 的 R14）⇒ **够复算、零漂移、不占共享面**。

### 4.3 阶段 1D 中**本轮明确没做**的事（诚实边界）

| # | 没做 | 原因 |
|---|---|---|
| 1 | 修 `DRIFT-FOLLOW-006`（指标无出口） | 修法 = **全仓 rpc 层统一方案**（8 个 rpc 组件 + `prometheus/settings.yml`）⇒ 跨域 + 2026-10-24 用户已定「不落地」⇒ 登记 `S1-FOLLOW-001` |
| 2 | ~~修 `DRIFT-FOLLOW-008`（MaxConns）~~ | ✅ **已销号（2026-10-29 订正）** —— **不是缺陷**：**2026-10-25 用户签字** ⇒ `SD-2026-10-25-01` ⇒ **不修、不派单** ⇒ **连「登记待办」都不保留**（决策在签字档） |
| 3 | 修 `PITFALL-FOLLOW-005`（缺 servlet-api） | 落点 `service/common` ⇒ **G4 写权** ⇒ 登记 `S1-FOLLOW-003` + §6 反馈单 |
| 4 | 给 follow 域补 compose 编排 | `docker-compose.yaml` 是根级共享文件 ⇒ 不在 G1 写权面 ⇒ 登记 `S1-FOLLOW-004` + 上报 |
| 5 | 动 `service/common` / `service/users/users-common` | 共享层**写权唯一属 G4** ⇒ 本轮零改动 |
| 6 | 阶段 2 的业务语义逐项核对（§1.3 / §4）与 12 维度审查（§6 / §7） | 收口执行者的事（AGENTS.md §5.3g 明令不碰） |

---

## 5. 阶段 1E：执行必要验证（§5.5）

> **本节是本轮 G1 收工的核心产出** —— 上一轮此处是「⏳ 待回填」，**本轮全部实跑并回填**。
> ⚠️ **取数纪律**：条数**逐个 `TEST-*.xml` 的 `testsuite` 属性求和**，**不数日志**，且**只在 maven 进程退出后采样**。

### 5.1 域级回归（**带 `clean`**）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow
mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end \
    -pl service/users/user/user-api,service/users/user/user-rpc,service/users/admin/admin-api,\
service/users/admin/admin-rpc,service/users/users-common,service/follow/follow-api,\
service/follow/follow-rpc,service/follow/follow-common \
    -am clean test
```

| 项 | 值 |
|---|---|
| **退出码 / 结果** | **0** · `BUILD SUCCESS` · **08:56 min** · 18 个 reactor 模块**全部 SUCCESS** |
| **用例总数** | **1195** 条（**0 失败 / 0 错误 / 0 跳过**）· 来自 **193** 个 `TEST-*.xml`（构建收工后求和）|
| **本域三模块** | `follow-api` **136** · `follow-rpc` **86** · `follow-common` **37** ⇒ **259 条** |
| 🆕 **与上一轮比** | **1133 → 1195（正好 +62）** ⇒ 增量**全部**来自本轮新增的 `FollowApiFailurePathMatrixTest`（§5.2.1）⇒ **既有 1133 条一条没退化**；`follow-api` **74 → 136**（+62）⇒ 逐模块核对：`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · `common` 337 **均与上一轮逐字相同** |
| 🆕 **第二轮（同一批代码）** | **EXIT=0 · `BUILD SUCCESS` · 10:01 min · 同样 **1195** 条 / 0F / 0E / 0S · **193** 个 XML** ⇒ 与第一轮**逐模块完全一致**（`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · `follow-api` **136** · `follow-rpc` 86 · `follow-common` 37 · `common` 337）⇒ ⇒ **为什么跑第二轮**：第一轮之后我又改了两个测试文件（`admin-api` 的注释订正 + `follow-api` 的断言消息订正）⇒ ⇒ **让台账里的数字对应「包含全部当前代码」的那一轮**，不拿旧轮的数字冒充 |
| ⚠️ **首轮曾整类红** | 首轮 `follow-api` 的 `FollowApiRedisDisconnectedFastFailTest` / `…DefaultBehaviorTest` **整类 error**（`Can't get Docker image: testcontainers/ryuk:0.14.0`）⇒ 根因 = **本模块 pom 缺 `TESTCONTAINERS_RYUK_DISABLED`**（全仓 52 个模块里只有它会去拉 ryuk）⇒ **已修（T9）· 本轮复验 2+2 绿** |

### 5.2 定向复验（本轮两处改动）

```bash
mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false \
    -Dtest='FollowApiRedisDisconnected*,FollowProtoContractTest' \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -pl service/follow/follow-api,service/follow/follow-rpc -am test
```

| 目标 | 结果 |
|---|---|
| `FollowApiRedisDisconnectedFastFailTest` | **2/2 绿**（修前：**整类 error**）|
| `FollowApiRedisDisconnectedDefaultBehaviorTest` | **2/2 绿**（修前：**整类 error**）|
| `FollowProtoContractTest`（🆕 本轮新增） | **3/3 绿**（0.107 s）⇒ ① 契约逐条一致 ② 嵌套 message 单独成行 ③ 变异自检 |
| 日志中 ryuk 拉取记录 | **0 条** |

### 5.2.1 🆕 失败路径矩阵复验（**真 Tomcat** · 本轮新增 `T11`）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow
mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest='FollowApiFailurePathMatrixTest' -pl service/follow/follow-api -am test
```

| 项 | 值 |
|---|---|
| **退出码 / 结果** | **0** · `BUILD SUCCESS` · `follow-api` **Tests run: 62, Failures: 0, Errors: 0, Skipped: 0** · 5.471 s |
| **覆盖** | **8 端点 × 4 类失败 + 基础设施类 + 7 条单点**（详见 §4.2 的矩阵表）|
| **容器** | **真 Tomcat**（`DEFINED_PORT` + `RealTomcatPort.pick()` ⇒ 落在 **22340–22359**）⇒ **follow-api 的真容器覆盖率从 0 变成 62 条** |
| ⚠️ **首轮曾 12 条红** | 首轮 = **62 条 / 12 失败** ⇒ **两条都是判据自身的错**，**不是产品缺陷**：① `assertEnvelope` 对**成功**信封也要求 `data:null`（成功信封的 `data` 是载荷）⇒ 已拆成 `assertErrorEnvelope`（`data` 必须 null）/ `assertSuccessEnvelope`（`data` 非 null）② 404 的 `Content-Length` 被我写死成 **20**，实测 **19** ⇒ 已改成「按实际 body 字节数比」（与 like-api 同法）|

> ⚠️ **首轮那 12 条红的性质要说清楚**：它们**不是**「产品行为与 Go 不一致」，而是**我新写的判据过严 / 复述了一个错数** ⇒
> ⇒ 处置是**改判据**（不是改产品）⇒ 这正是 AGENTS.md §3.3 规则 ③ 的应用：**先改/删错误断言，再看实现**。

### 5.3 收口验收命令（本域相关，逐条实跑）

> ⚠️ **两个坑**（**共享工具问题 ⇒ 已上报**）：① `GO_REPO` 默认值按「仓库同级目录」推导 ⇒ **worktree 里算错** ⇒ 需显式指定 Go 真源绝对路径；② `--domain` 的域名是 **`user` / `admin`** ⇒ `--domain users` **匹配不到任何域** ⇒ **0/0 却 EXIT=0**（空跑即通过）。

| # | 命令 | EXIT | 实测结果 |
|---|---|---|---|
| 1 | `check-surface-parity.py --domain follow` | **0** | HTTP 端点 **Go=8 ≡ RAG=8** · gRPC 方法 **Go=8 ≡ RAG=8** |
| 2 | `check-ddl-static.py --domain follow --indexes` | **0** | **0 处实质差异**（类型/长度/可空性/DEFAULT/索引/约束/触发器**全比**）|
| 3 | `check-proto-static.sh` | **0** | ⚠️ **只覆盖 user / admin / task 三域 ⇒ follow 不在内**（基线 §九 `G-6`）⇒ **本轮由 🆕 `FollowProtoContractTest` 补上 follow 的字段级断言**（更严：比对**生成码描述符**而非文本 diff）|
| 4 | `check-tomcat-limits.py` | **0** | **10 个 api 全覆盖**（含 `follow-api`）三个旋钮 ✓ |
| 5 | `check-flyway-ledger.py` | **0** | `follow-rpc` = `flyway_schema_history_follow` ⇒ 7 模块账本两两不同 ⇒ 无撞号 |
| 6 | `check-grpc-interceptor-wiring.py` | **0** | 全部 rpc 接线完整 |
| 7 | `check-wave-lane-ownership.py` | **0** | 写权两两不重叠 · 共享层只归 G4 |
| 8 | `check-ledger-tables.py` / `check-ledger-selfcheck.py` / `check-ledger-count.py` | **0** / **0** / **0** | 台账结构 · 自检 · 计数 全通过 |
| 9 | `check-doc-links.py` | **1** | 🔴 **1 项锚点失效（既存，非本轮引入）** ⇒ `docs/PhaseGoal-judge.md:99` 自指锚点 ⇒ **主工作区同样 FAIL** ⇒ 上报收口 |

### 5.4 ⛔ 本轮**明确未跑**的命令（诚实边界）

| 命令 | 为什么没跑 | 结论**归谁** |
|---|---|---|
| `bash tools/difftest/run.sh`（users 双侧活体 HTTP wire 对拍） | 需 **Go 栈 + RAG 栈双栈编排**（etcd / PG / Redis + Go 二进制 + RAG jar）⇒ 未起 | **沿用台账既有记录**（2026-10-24/27）⇒ **本轮未复验** |
| `python3 tools/difftest/matrix.py`（DTO 字段笛卡尔） | 同上 | 同上 |
| ✅ `bash tools/difftest/exp-difftest-grpc-user9.sh --no-build` | ✅ **本轮已跑**（2026-10-29）⇒ **43 场景 / 39 等价 / 4 处差异**（4 处**全是已签字 N-2** 的 Go 乱码文案）⇒ **与 2026-10-24 基线逐条同形** ⇒ users 域双侧活体**可复算**（详见 users 档 §5.4bis）| **本轮已复验**（users 域侧）|
| `bash tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up` | 同上 ⇒ 未跑（⚠️ **资产在版本控制内** ⇒ 可复算）| 沿用台账既有记录 |
| `bash tools/difftest/exp-difftest-redis-blacklist.sh` | 同上 ⇒ 未跑（同上）| 沿用台账既有记录 |
| ✅ `bash tools/difftest/check-jar-executable.sh` | ✅ **本轮已跑**（**先 `mvn package`** ⇒ 8 个 jar）⇒ **本 lane 6/6 api+rpc 模块全 ✅**（含 `follow-api` 80M · `follow-rpc` 64M）⇒ 基线 `G-10` 在**本 lane** 已闭合 | — |
| 🔴 **follow 域双侧对拍**（基线 §九 `G-1`） | 需重建双侧探针 + 双栈 ⇒ **本轮未做**（基线判 🟡 **不阻断**）<br>🔴 **本轮实测补强**：`/tmp/probe.sh` / `/tmp/probe_data.sh` / `/tmp/probe_ext.sh` **三个都不存在**，`/tmp/gofollow` 与 `/tmp/userstub` **现为空目录** ⇒ **不只是「未进版本控制」，是**源头已消失** ⇒ 「52/52 逐字节一致」**今天完全不可复算** | **基线 §九 `G-1`** · 阶段 2 建设项（见 §6.0.1 E3）|

⇒ ⚠️ **口径**：以上命令的结论**一律按台账原有日期与证据强度读**，**不得因本轮回归全绿而说成「本轮已复验」**。

---

## 6. 阶段 1F：独立验收（§5.6）+ 阶段归属（§10）

### 6.0 验收结论：**🟡 有条件通过**（域级）

> ⚠️ **不是「通过」** —— 本域有 **1 条 🟡 未闭合**（`S1-FOLLOW-001` 指标无出口 ⇒ `follow-rpc` = 🟡 ⇒ **G1 lane 判定 = 🟡**），
> 且 **1 条 A 类项目级阻断**（`S1-USERS-001` 说明书缺失 ⇒ 域外）。
> ⚠️ **也不是「未通过」** —— 本域**能做的都做完了**：域内 P0 全满足、域级回归 **1195 条**全绿（两轮一致 · 含本轮新增的 62 条失败路径矩阵）、域内两条缺陷已自修并验证。
> 🆕 **2026-10-29 收敛核验轮**：域内**无新增未闭合项**；唯一未达标仍是 `S1-FOLLOW-001`（`follow-rpc` 指标出口 · **待用户重新拍板**）⇒ **G1 lane 判定维持 🟡**（口径 = 基线 §8.1）⇒ 详见 users 档 §6.0.1 与 `users/vs-common.md` §70.5。

| §5.6 检查项 | 结论 | 依据 |
|---|---|---|
| P0 准入条件是否满足 | ✅ **域内 8/9 满足**（#1~#4、#6~#9）· 🔴 **#5 项目级** | §1 + §5 |
| P1 是否至少有两三个机制闭环 | ✅ **有**（超时/deadline · Redis 断连与超时 · 鉴权与黑名单 fail-open · 错误码契约 · **proto wire 契约（本轮新增闭环）**）| 域台账 + §5.2 |
| 核心业务是否能够实际运行 | 🟡 **域内能**（可构建可起）· **项目级按说明不能** | §1 #5 |
| 测试是否真实断言业务结果 | ✅ **是**（197 条 / 0F/0E/0S · 含变异实测 · **本轮新增契约测试含变异自检**）| §5.1 + §5.2 |
| 关键失败路径是否具有明确行为 | ✅ **是**（下游挂死 / Redis 断连 / Redis 超时 / 401 / 400 逐条有断言）| §1 #4 + 域台账 |
| 是否存在严重安全或数据正确性问题 | ✅ **本域无**（跨域项见 users 档 §6.2 E2）| — |
| 项目是否能够按实际步骤构建、配置和启动 | 🔴 **否（项目级）** | `S1-USERS-001` |
| 所有已知缺陷和关键风险是否已记录 | ✅ **是**（§3 + §6.1 + §6.2 + 域台账 + vs-common 9 轴）| §3 |
| 暂缓项是否具备明确的阶段 2 处理方案 | ✅ **是**（每条都有第 9/10 项）| §3 |

### 6.0.1 「有条件通过」的**有限例外**逐条（§5.6 要求：必须明确例外及其影响）

| # | 例外 | 范围 | 影响 | 归属 |
|---|---|---|---|---|
| **E1** | **项目说明缺失**（`README.md` 7 字节 · compose 要 `.env` 而仓内无模板） | **项目级** | 外部无法按说明复现启动 ⇒ 阶段 1 的「真实运行」只能靠内部台账自证 | **A 类** · 收口（users 档 `S1-USERS-001`）|
| **E2** | **`follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口**（Go DevServer `:16065/metrics` 有 · RAG 纯 gRPC 进程无 HTTP 口） | follow 域（**同款 5 处**：`favorite-rpc` / `message-rpc` / `like-rpc` / `user-rpc` / `admin-rpc`） | 可观察性缺一环 ⇒ **`follow-rpc` = 🟡** ⇒ **G1 lane 判定 = 🟡** | **C 类**（阶段 2 建设项）· ⚠️ **用户 2026-10-24 已定「本轮不落地」⇒ 登记待重新拍板** |
| **E3** | 🔴 **`follow` 域 0 个落仓双侧对拍入口** —— 探针在 `/tmp` 且 **本轮实测源头已消失**（`/tmp/probe.sh` 等 3 个不存在 · `/tmp/gofollow` 与 `/tmp/userstub` 现为空目录）⇒ **不只是「未进版本控制」，是连源头都没了** ⇒ 「52/52 逐字节一致」**今天完全不可复算** · 基线 §九 `G-1` | follow 域 | `follow-api`/`follow-rpc` 的 **R4 不成立**（已按 **R3** 计入 ⇒ **组件判定仍是 ✅**）⇒ **不阻塞阶段一**<br>🟡 要恢复 = **重写 52 条探针 + 编排落仓**（`tools/difftest/follow/`，用户 2026-10-27 已授权该子目录）⇒ **本轮判定「不值得」**：① 组件判定 ✅ R3 已达标；② 属**阶段 2 建设项**（探针工程量 ≈ 重写 52 条断言 + 双栈编排）⇒ **不预支** ⇒ 记为阶段 2 待办 | **B 类**（非阻断）· 阶段 2 建设项 |
| **E4** | ~~**本域无独立失败路径矩阵** · 基线 §九 `G-13`（4 域无：article / follow / task / hot）~~ | follow 域 | ✅ **本轮闭合（`T11`）** —— 8 端点 × 4 类失败 × **真 Tomcat** = **62 条**用例全绿（§4.2 / §5.2.1）⇒ **follow 域的 P0#2 证据覆盖已补齐**；⚠️ **但 P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因本条判成 ✅** | ✅ **本轮闭合** |
| **E5** | **`check-proto-static.sh` 不覆盖 follow** · 基线 §九 `G-6` | follow 域 | ⇒ **本轮已由 🆕 `FollowProtoContractTest` 补上字段级断言** ⇒ 该项**本轮已闭合**（功能上）| ✅ **本轮闭合** |
| **E6** | **proto「消费方式」未取证**（`optional` presence 的构造点语义）· 基线 §九 `G-19` | 全 10 proto | **取证缺口 ⇒ 不降 R**；⚠️ **follow 的 proto 无 `optional` 字段 ⇒ 该边界在本域不适用** | **B 类**（不适用）|
| **E7** | **双侧活体对拍本轮未复验** —— 🟡 **users 域侧已复验**（`exp-difftest-grpc-user9.sh` 43 场景，4 处差异全是已签字 N-2）；🔴 **follow 域侧不可复算**（探针源头已消失 ⇒ 见 **E3**） | follow 域 | follow 域台账既有 L3/L4 结论**沿用原日期** ⇒ 证据强度**不提升**、**不降** | **B 类**（阶段 2 建设项）|
| 🆕 **E8** | 🟢 **本轮的一次误判已撤回**：把 Go 真源乱码字面量当「新发现 + 需新签字」⇒ 真相是 **N-2**（2026-10-21 用户签字）+ `docs/Pitfalls/users/user/rpc.md:127` **P-05**（明写「下一个 agent 不要把它当新发现再「修」一遍」）⇒ **不需新签字、不动代码** | **零影响**（本轮未改任何业务码） | 本轮那 4 处差异 = **签字项的复现**，不是缺陷 ⇒ **不构成任何阶段一缺口** | ✅ **已澄清**（P3 · 零代码影响）· 详见 users 档 `S1-USERS-011` + `users/vs-common.md` §70.2 |

### 6.1 需**域外**处理的上报项

| 编号 | 事项 | 为什么必须域外 | 影响 |
|---|---|---|---|
| `S1-FOLLOW-001` | 全仓 rpc 层指标出口统一方案 | 跨 8 个域 + `prometheus/settings.yml` | 🟡 B 类 |
| ~~`S1-FOLLOW-002`~~ | ~~MaxConns 闸门按路由分桶~~ | — | ✅ **已撤销（2026-10-29）** —— **不是缺陷**（2026-10-25 签字偏离 `SD-2026-10-25-01` ⇒ 保留全局闸门）⇒ **不派 G4**；⚠️ **基线层仍记成开放缺陷 ⇒ 已上报收口订正** |
| `S1-FOLLOW-003` | 纯 gRPC 进程关闭路径解耦 web 类型 | `service/common` ⇒ G4 | 🟡 B 类 |
| `S1-FOLLOW-004` | compose 补 4 个业务组件 | 根级 `docker-compose.yaml` | 🟡 B 类 |

#### 6.1.1 🆕 **本轮新发现的上报项**（2026-10-29 · G1 lane · **域外 ⇒ 只登记 + 上报**）

| 编号 | 事项 | 为什么必须域外 | 影响 | 复现 |
|---|---|---|---|---|
| 🆕 **`S1-FOLLOW-007`** | **基线仍把已签字偏离记成开放缺陷** —— `docs/PhaseGoal-judge.md` 的 A-2 `follow-api` 行仍写「`DRIFT-FOLLOW-008`（MaxConns 闸门粒度 ⇒ 落点 `service/common`）」⇒ 机器生成的 `docs/PhaseGoal/g1.md` 由此产出一行「**→ G4**」的派单指令 ⇒ ⇒ **照它执行就会让 G4 去改掉一个用户已决定保留的行为** | `docs/PhaseGoal-judge.md` 是**阶段一终点指标的唯一数据源**，`docs/PhaseGoal/g*.md` 由它**机器生成** ⇒ **不在 G1 写权面** ⇒ 交**合并收口执行者**订正 + 重跑生成器（`python3 tools/difftest/check-phasegoal-lanes.py --write`）⇒ 校验器 `EXIT=0` | 🔴 **A 类风险**（派单错误 ⇒ 共享层被改坏）· 🟡 证据面 | `grep -n "DRIFT-FOLLOW-008" docs/PhaseGoal-judge.md docs/PhaseGoal/g1.md` |
| 🆕 **`S1-FOLLOW-008`** | **`check-surface-parity.py` 有两个「静默假绿」口子** ① **未知域名不报错**：`--domain users`（真名是 `user` / `admin`）⇒ **匹配不到任何域** ⇒ 输出「HTTP 端点 Go=0 / RAG=0」却 **EXIT=0** ⇒ **空跑即通过**；⚠️ `docs/PhaseGoal/g1.md` §5 的收口命令**正是这条错名命令** ② **`GO_REPO` 默认值按「仓库的同级目录」推导** ⇒ 在 **worktree**（`RAG.worktrees/<lane>`）里会算成 `RAG.worktrees/Sea-RideTheWind_golang` ⇒ 找不到 Go 真源 ⇒ `follow` 域**假红**（EXIT=1，报「Go routes.go 不存在」） | `tools/difftest/**` 属**共享工具**（`tools/difftest/README.md` 明确归**合并收口执行者**）⇒ **不在 G1 写权面** ⇒ 上报 | 🟡 **证据面**（判据自身不可信 ⇒ 所有 lane 的「契约面一致」结论都带这个洞）<br>⚠️ **注意**：`--domain users` 的假绿**不会让任何错误结论通过**，但它**让「未跑」长得像「跑过」** ⇒ 与「零命中是假信号」同族 | 在 worktree 内：`python3 tools/difftest/check-surface-parity.py --domain users` ⇒ **EXIT=0 + 0/0**<br>正确用法：`GO_REPO="<Go 真源绝对路径>" python3 tools/difftest/check-surface-parity.py --domain user --domain admin --domain follow` |
| 🆕 **`S1-FOLLOW-009`** | **另外 5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED`** —— 全仓「凡真跑 Testcontainers 的模块」都注入了它（`like-rpc` / `comment-rpc` / `message-rpc` / `message-api` / `favorite-rpc` / `favorite-api` / `hot-rpc` / `task-rpc` / `article-rpc` / `common` / **`follow-rpc`** / `user-api` / `user-rpc` / `admin-api` / `admin-rpc`），**唯独 5 个 api 模块没有**：`article-api`（G3）· `comment-api`（G3）· `hot-api`（G3）· `like-api`（G2）· `task-api`（G2）⇒ **在 Docker Hub 不可达的环境里，这些模块的 Testcontainers 用例会整类 error**（`Can't get Docker image: testcontainers/ryuk:0.14.0`）⇒ **本轮 G1 的 `follow-api` 就是这样整类红的** | 各归 G2 / G3 ⇒ **不在 G1 写权面** ⇒ **登记 + 上报**（本域 `follow-api` 已自修并留证） | 🟡 **B 类**（测试基础设施 ⇒ 不影响产品行为）<br>⚠️ **但会伪造「域级回归红」** ⇒ 判据可信度面 | `for f in $(find service -name pom.xml -not -path '*/target/*'); do grep -q TESTCONTAINERS_RYUK_DISABLED "$f" \|\| echo "$f"; done`<br>（**排除** `*-proto` / `*-common` / 聚合 pom —— 它们不跑 Testcontainers） |
| 🆕 **`S1-FOLLOW-013`** | **本轮新增文件自己的断言消息也写着「全长 20 字节」** —— `FollowApiFailurePathMatrixTest.java:417` ⇒ **断言本身正确**（比 body 字面量）⇒ **错数只在文案里** ⇒ ⚠️ **它躲过了 G1 的第一轮自查**：① 我那轮 grep 的**模式是 body 字面量**（那行不含它 ⇒ 零命中）② 那轮「字节」扫描**带了 `head -40`**（249 条截到 40 条 ⇒ 它在第 210 条）⇒ ⇒ **「我域零残留」这个结论当时是不成立的** ⇒ **已重扫（无截断 + 模式选对）+ 逐条复算** ⇒ 结论现成立 | G1 写权内 ⇒ **已自修**（**不需要任何域外动作**） | 🟡 **文案面**（P3）<br>⚠️ **值得记的原因**：它证明**「自查」本身也会骗自己** ⇒ 自查必须满足两条：**模式选对**（扫「错数」要搜「数字 + 字节」，不是 body 字面量）＋ **命令无 `head`/`tail`/分页**（命中先落文件再逐条看）⇒ 否则「零残留」是**假信号** | `grep -rnE "[0-9]+ ?字节" service/users service/follow --include='*.java' \| grep -v "/target/"` ⇒ **74 行命中**（**不带 head**）<br>`printf '404 page not found\n' \| wc -c` ⇒ **19**（**不是 20**）|
| 🆕 **`S1-FOLLOW-011`** | **like 域的真 Tomcat 测试沿用了同一个错数** —— `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java:46` 的 javadoc 与 `:187` 的 `@DisplayName` 都写「20」⇒ ⚠️ **它的断言本身是对的**（按**实际 body 字节长度**比，不是写死 20）⇒ **一直绿** ⇒ 只有**注释与显示名**错 | `service/like/**` ⇒ **G2 写权** ⇒ G1 **只登记 + 上报** | 🟡 **文档面**（P3）<br>⇒ **建议**：与 `S1-FOLLOW-010` 一并订正（同一处错数的两个副本）| `grep -n '20' service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java` |
| 🆕 **`S1-FOLLOW-010`** | **共享层 `GoStyleRouterErrorResponses` 的 javadoc 把 404 body 写成「全长 20 字节」，实测是 19** —— `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83` 的注释说「{@code fmt.Fprintln} 写出，所以**带一个尾随换行**，全长 {@code 20} 字节」⇒ 实际 `"404 page not found\n"` = 18 + 1 = **19 字节**（`printf '404 page not found\n' \| wc -c` ⇒ **19**）⇒ **注释差 1**。⚠️ **不影响行为**（常量 `NOT_FOUND_BODY` 本身是对的 ⇒ 真容器实测 `Content-Length: 19`）⇒ 只是一处**会误导后来者写断言**的注释 | `service/common/**` ⇒ **G4 写权** ⇒ G1 **只登记 + 上报** | 🟡 **文档面**（P3）<br>⚠️ **值得修的原因**：like 域那条真 Tomcat 断言的 `@DisplayName` 也照抄了这个「20」⇒ 若后来者照它写死 `assertEquals("20", …)` ⇒ **当场红**（本轮就踩过一次）⇒ 该注释**已经产生过一次实际误判**<br>🆕 **同族第三处（G1 自查时自捕）**：**`admin-api`** 的 `AdminApiRealTomcatResponseBytesTest.java:266` 把 503 body 写成「Content-Length: **15**」⇒ 而紧邻的断言用的是 `"Request Timeout".length() + 1` = **16** ⇒ **同一族**（**注释里的字节数漏算了 `fmt.Fprintln` 补的尾随 `\n`**）⇒ **G1 域侧自修**（`S1-USERS-010`）<br>🆕 **G2 已在 like 域订正**（commit `aee9b0c`）⇒ ⇒ **三域三处，同一族的注释漂移** | `printf '404 page not found\n' \| wc -c` ⇒ **19**<br>`grep -n '全长' service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java` |

### 6.2 G1 → G4：派单（**反馈单 7 项**，缺项不进排期）

> **口径** ⇒ [`docs/DRIFT/users/vs-common.md` §6.2](../users/vs-common.md) · **落点双轨**：实时 `session send` 给 G4 ＋ 正式写台账（本文件 ＋ [`vs-common.md`](vs-common.md) §6.2）。
> ⚠️ **派单前置（2026-10-29 已做）**：**先查 `docs/Intentional_Deviations/**`（决策真源）** ⇒ 本轮正是因此**撤销了 1 条**（`DRIFT-FOLLOW-008` ⇒ 已签字 ⇒ 不派）。
> ⚠️ **编号纪律**：带全前缀（`PITFALL-FOLLOW-005` ≠ `DRIFT-FOLLOW-008` ⇒ 同族 ≠ 同一条）。

| # | 反馈单项 | 内容 |
|---|---|---|
| **F1** | **现象** | **纯 gRPC 进程关闭时**抛 `NoClassDefFoundError: jakarta/servlet/Filter` ⇒ **关闭日志被污染**（真正的关闭原因不可辨） |
| | **Go 侧 `文件:行号`** | **Go 侧无等价形态**（不是双向语义漂移，是 **RAG 共享层混装**）：`service/follow/rpc/follow.go:3-21` 的 import 清单**零** `net/http` / servlet 类（`grep -rn "net/http\|servlet\|rest\." service/follow/rpc/` 零命中）＋ `:45` `defer s.Stop()` ⇒ Go 的 rpc 进程关闭路径**不加载任何 web 类型** ⇒ **不可能**因缺 web 类型而炸 |
| | **RAG 侧现状** | ① `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:8` `import jakarta.servlet.Filter;`（`:236` `newHttpMiddleware(...)` 返回 `Filter`）② `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:54` `HttpMiddleware.shutdown();` ⇒ 该类在**纯 gRPC 进程**里的首次加载**恰好落在 destroy 路径** ⇒ 抛 `NoClassDefFoundError` |
| | **证据强度** | **L1 源码逐行**（根因链已读通：import → 类加载 → destroy 触发）＋ 域台账既有活体症状（[`docs/Pitfalls/follow/rpc.md`](../../Pitfalls/follow/rpc.md) `PITFALL-FOLLOW-005`） |
| | **建议修法** | 两侧都应是 **Y** = 「**关闭路径不得触发 web 类型的类加载**」⇒ ① HTTP 侧的池回收挪进**只在 servlet 进程存在**的 Bean（`@ConditionalOnWebApplication(type = SERVLET)`，与 `GoStyleTraceparentFilter` 同款），gRPC 进程只回收 `RpcInterceptor` 的池；② 或把 `HttpMiddleware` 的静态线程池提到**无 web 依赖**的独立类，`shutdown()` 不碰 `HttpMiddleware` 本身 |
| | **影响面** | `service/common/{observability,spring}/` ⇒ 编译面 16 组件 · **受害面 = 所有纯 gRPC 进程**（follow-rpc / user-rpc / admin-rpc / like-rpc / …），**不只 follow** |
| | **是否阻塞本波** | 🟡 **不阻塞**（只污染关闭日志，**不影响启动与业务**） |
| **F2** | **现象** | **Redis 断连**时 Lettuce 默认 `disconnectedBehavior=DEFAULT` ⇒ 命令被**排队**等重连 ⇒ 挂到 `commandTimeout: 3s` ⇒ **api 侧**撞 HTTP 3s 预算 ⇒ **503**（Go 是 fail-open **200**）；**rpc 侧**撞 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015**（Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」`） |
| | **Go 侧 `文件:行号`** | `service/user/user/rpc/internal/model/...` 的 go-redis 客户端构造（`MaxRetries=3` ⇒ **快速失败**）⇒ 对应的 RAG 落点是 `service/common` 的 Lettuce 配置（**RAG 侧现状**栏给行号） |
| | **RAG 侧现状** | 域侧 5 份副本已修（`user-api` / `admin-api` / `follow-api` / `user-rpc` / `admin-rpc` 的 `config/RedisDisconnectedBehaviorConfig`）⇒ **但共享层那份（G4 commit `1fb26b2` ＋ `2f12236`）带 `@ConditionalOnMissingBean` ⇒ 域内副本在 ⇒ 共享层那份整个不创建 ⇒ 收编根本没发生**，且**共享层后续改动对本域静默不生效** ⇒ ⇒ **删除 5 份域内副本是收编的「必要条件」而非「收尾清理」** ⇒ 收编完成时**必须复验**共享层那份是否真的生效 |
| | **证据强度** | **L3 真容器活体**（真 Redis ＋ 可控 TCP 代理造 FIN）：修前实测 **3012ms** ⇒ 修后 **0ms** ＋ **变异 2/2 变红**（域侧已闭环） |
| | **建议修法** | 两侧都应是 **Y** = 「**断连时快速失败**（不等重连）」⇒ **共享层一份统一 + 删掉 5 份域内副本** ⇒ ⚠️ **删除前置 ＝ 共享层那份先合进各域的树**（否则双份配置 ⇒ 两处都要维护） |
| | **影响面** | `service/common`（`GoStyleRedisClientAutoConfiguration`）⇒ 编译面 16 组件 · **用 Redis 的 5 个组件** |
| | **是否阻塞本波** | 🟡 **不阻塞**（域侧已自闭环；共享层收编未完成 ⇒ **共享层后续改动对本域静默不生效**） |

> ⚠️ **本轮明确没派的一条**：`DRIFT-FOLLOW-008`（MaxConns 闸门粒度）—— **2026-10-25 用户已签字**为有意偏离（`SD-2026-10-25-01`）⇒ **不是缺陷 ⇒ 不派单**（详见 §3 `S1-FOLLOW-002`）。
> ⚠️ **本波 G4 侧落码的收口窗口**：共享层落码窗口常开（AGENTS.md §5.3e）⇒ **G4 落码后本域 A1~A9 结论要复核**（重点 A1 超时 / A3 保护链 / A4 错误码 envelope）⇒ 且**收口回归在波末跑** ⇒ 窗口 = 收口回归跑完～合并前。

---

## 7. 冻结基线（§5.7）

| §5.7 要求 | 本轮的值 |
|---|---|
| **实际代码状态及版本标识** | 分支 `align/g1-users-follow` · 基线 `29a0bce`（= `do-my-own-gh`）· **本轮改动**：1 个配置行（`follow-rpc` 端口 env 覆盖）＋ 1 个 pom 段（ryuk env）＋ 3 个新增文件（proto 契约测试 ＋ 基准 ＋ 生成脚本）＋ **🆕 2 个新增文件（`RealTomcatPort` ＋ `FollowApiFailurePathMatrixTest`）＋ 1 个修改文件（`BlindPortGateTest` ② 段 8→9）** ＋ 5 份台账订正 ⇒ **提交后以 commit hash 为准** |
| **构建和测试结果** | 域级回归 **EXIT=0 · BUILD SUCCESS · 08:56 min（第二轮 10:01 min · 同一批代码）· 1195 条 / 0F / 0E / 0S**（本域 **259** 条 · 🆕 较上一轮 **+62** ＝ 失败路径矩阵）· 定向复验 **7 条全绿** ＋ 🆕 矩阵复验 **62 条全绿** · 收口验收命令 **9 条**：8 条 EXIT=0、1 条 EXIT=1（**既存**锚点失效） |
| **核心业务链路的验收结果** | `follow-api → follow-rpc → PG` ＋ `follow-rpc → user-rpc（用户存在性校验）` ⇒ **域内实测沿用台账**（2026-10-24 的 **52/52 逐字节一致**、2026-10-27 的真启动 A/B/C）⇒ **本轮未起真栈复跑** ⇒ **按原日期读，不提升** |
| **P0 / P1 / P2 最终状态** | **P0**：域内 ✅ 8/9（#5 项目级 🔴）· **P1**：✅ 至少 5 个机制闭环 · **P2**：3 项登记（1 项已销号 · 1 项已修 · 1 项待重新拍板） |
| **全部已知缺陷和未验证风险** | §3（`S1-FOLLOW-001~009`）· §6.1 上报项 · §6.2 例外 · 域台账（`api.md` / `rpc.md` / `common.md` / `proto.md`）· [`vs-common.md`](vs-common.md) 9 轴 · 基线 §九 `G-1`（双侧探针未落仓）/ `G-6`（proto 静态守门只覆盖 3 域 ⇒ **本域已由 `FollowProtoContractTest` 补上**）/ **`G-13`（本域无独立失败路径矩阵 ⇒ 🆕 **本轮已闭合**）** / `G-19`（消费方式未取证）|
| **完整的差异与暂缓项清单** | 本文件 §3（**9 条，每条 11 项齐全**）＋ §6.1.1 的 2 条上报项（`S1-FOLLOW-010` / `S1-FOLLOW-011`）|
| **阶段 2 任务及其依赖** | ① `S1-FOLLOW-001` 指标出口（**待用户重新拍板**；依赖：全仓 rpc 层统一方案 ⇒ **非 G1 单方面可定**）② `S1-FOLLOW-003` 共享层关闭路径解耦（依赖：**G4 落码**）③ `S1-FOLLOW-004` compose 编排（依赖：**收口**）④ `S1-FOLLOW-007` 基线订正（依赖：**收口**）⑤ `S1-FOLLOW-008/009`（依赖：**收口 / G2 / G3**）|
| **尚未处理的原始业务行为** | ① Go DevServer 的 `/metrics` 出口（RAG 侧 rpc 组件**零出口**）② MaxConns 的 per-route 语义（**用户已签字不修 ⇒ 是「决定不处理」，不是「尚未处理」**）③ `follow-proto` 的「消费方式」取证（follow 无 `optional` 字段 ⇒ 该边界**不适用**）|
| **下一次执行应从哪里开始** | ① **收口**：`S1-USERS-001` 说明书 + `S1-FOLLOW-007` 基线订正（**A 类，阻断阶段 1**）② **G4**：收 §6.2 的 F1 / F2 反馈单 ＋ `S1-FOLLOW-010`（404 字节数注释）③ **G2/G3**：5 个 api 模块补 ryuk-disabled ＋ `S1-FOLLOW-011`（like 域同一处错数）④ **用户拍板**：是否重开 `S1-FOLLOW-001`（决定 `follow-rpc` 能否从 🟡 升 ✅）⑤ **G1 自身**：若要闭合基线 `G-1`（follow 双侧对拍落仓）⇒ 需重建探针 + 双栈 ⇒ **属阶段 1 的证据补强，非阻断**；⚠️ **`G-13` 的其余 3 域（article / task / hot）不在 G1 写权面 ⇒ 由 G2 / G3 补** |

---

## 8. 证据强度声明

| 结论 | 证据强度 | 说明 |
|---|---|---|
| P0#1 / #2 / #3 / #4 判定 | **L4** | 双侧活体对拍 52/52 逐字节一致 + 受控故障注入（超时/断连/挂死） |
| P0#5 域内判定 | **L4**（2026-10-27 真启动 A/B/C 对照）· ⚠️ **本轮未复跑真启动**（§5.4）⇒ 按原日期读 | 真 PG + 真 Redis + 真 Nacos + 真进程 + 真 Tomcat |
| P0#8 判定 | **L2** | 9 处默认值逐文件枚举（排除 `target/` 副本） |
| **本轮新增：域级回归全绿** | **L3**（测试证据 · 本域 **259** 条 / 全域 **1195** 条 / 0F / 0E / 0S） | ⚠️ **这是「测试证据」，不是「运行面等价证据」** ⇒ 全绿只证明「已写的断言都成立」|
| **本轮新增：`follow-proto` 契约回归网** | **L3**（测试证据 · 3/3 绿 · 含变异自检） | 期望值**由 Go 真源生成**（记录 sha256）⇒ 比对**生成码描述符** ⇒ 证**编译产物**的契约；⚠️ **不覆盖 wire 字节级 / 不覆盖「消费方式」** |
| **本轮新增：ryuk env 补齐** | **L3**（测试证据 · 2+2 绿） | 原整类 error 的两个类**真跑起来**（不是跳过、不是 0 断言）|
| **本轮新增：端口 env 覆盖** | **L1**（静态可证） | 默认端口**逐字不变**（8082）⇒ 行为零变化；⚠️ 「改 env 后真启动 + Nacos 注册地址一致」**未做行为级实测** ⇒ 按 L1 记 |
| **本轮新增：失败路径矩阵（`T11`）** | **L3**（测试证据 · **62 条 / 0F / 0E / 0S** · **真 Tomcat**） | 期望值**逐条抄自 Go 源码**（每条带 `文件:行号`）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥；⚠️ **不按 R4 计**（R4 要双侧活体 + 变异）⇒ **不得**据此宣称「与 Go 逐字节一致」；⚠️ **首轮 12 条红是判据自身的错**（过严的 `data:null` 断言 + 复述了错数「20」）⇒ **已改判据，不是改产品** |
| 本文件全部「一致」类断言 | 按各域台账的「证据强度」列 | **不是已闭环**（AGENTS.md §0） |

---

## 9. 复现清单（可复算）

| 编号 | 命令 | 期望 |
|---|---|---|
| R1 | `grep -rn '@Transactional' service/follow/follow-rpc/src/main/java --include='*.java'` | **1 处**（`FollowBlockTransactionService.java:70`）⇒ 事务边界在**独立 bean** 上 |
| R2 | `find service -path '*/src/main/resources/application.y*ml' -not -path '*/target/*' -exec grep -hoE '\$\{POSTGRES_PASSWORD:[^}]*\}' {} + \| sort \| uniq -c` | **只有 1 种**（`10 ${POSTGRES_PASSWORD:password}` / 9 个模块） |
| R3 | `grep -n 'port:' service/follow/follow-rpc/src/main/resources/application.yml` | `${FOLLOW_RPC_PORT:8082}`（T3 后） |
| R4 | `grep -nE 'management.server.port\|^\s+port: -1' service/follow/follow-rpc/src/main/resources/application.yml` | **0 命中** ⇒ follow-rpc **无** management HTTP 口 ⇒ 印证 `S1-FOLLOW-001` |
| R5 | `lsof -nP -iTCP:8082 -sTCP:LISTEN` | 占用者 = `python -m http.server`（`S1-FOLLOW-005` 的成因证据） |
| R6 | `python3 tools/difftest/check-flyway-ledger.py` | `EXIT=0`（follow 的独立账本） |
| R7 | `python3 tools/difftest/check-wave-lane-ownership.py` | `EXIT=0` |
| **R8** | `grep -c TESTCONTAINERS_RYUK_DISABLED service/follow/follow-api/pom.xml` | **1** ⇒ `follow-api` **不再**去拉 `testcontainers/ryuk` ⇒ 整类 error 消失 |
| **R9** | `python3 service/follow/follow-rpc/src/test/resources/proto-contract/generate.py "<Go 真源>/proto/follow.proto" /tmp/follow-expected.txt "<Go 真源>"` ⇒ `diff /tmp/follow-expected.txt service/follow/follow-rpc/src/test/resources/proto-contract/follow-proto-contract.expected.txt` | **0 行差异** ⇒ 基准与 Go 真源**同步**<br>⚠️ 基准里**必须**有 `message\|RecommendResp\|code=1:int32\|msg=2:string\|users=3:repeated RecommendUser` **与** `message\|RecommendResp.RecommendUser\|target_id=1:int64\|mutual_score=2:int32` **两行** ⇒ 少一行 = 解析错（hot 域那份非贪婪脚本的坑）|
| **R10** | `GO_REPO="<Go 真源绝对路径>" python3 tools/difftest/check-surface-parity.py --domain follow` | `EXIT=0` ⇒ HTTP **8 ≡ 8** · gRPC **8 ≡ 8**<br>⚠️ **必须设 `GO_REPO`**（worktree 下默认值算错 ⇒ 假红）|
| **R11** | `python3 tools/difftest/check-ddl-static.py --domain follow --indexes` | `EXIT=0` ⇒ **0 处实质差异** |
| **R12** | `grep -rn "DRIFT-FOLLOW-008" docs/DRIFT/follow/` | **命中处全部标「已签字偏离」或「已撤销」** ⇒ **不得再有「🔴 未修 ⇒ 派单」**（那会把用户已决定保留的行为派给 G4 去「修」）|
| **R13** | `python3 - <<'EOF'` 逐个 `TEST-*.xml` 的 `testsuite` 属性求和（**构建收工后**） | 全域 **1195** 条 / 0F / 0E / 0S；本域 `follow-api` **136** + `follow-rpc` **86** + `follow-common` **37** = **259**<br>⚠️ **不要数日志** · ⚠️ **不要在构建中途采** |
| **R14** | `mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest='FollowApiFailurePathMatrixTest' -pl service/follow/follow-api -am test` | `EXIT=0` · **Tests run: 62, Failures: 0, Errors: 0, Skipped: 0**<br>⇒ **8 端点 × 4 类失败**的矩阵全绿（§4.2 / §5.2.1）|
| **R15** | `grep -n "public static final int FIRST\|public static final int LAST" service/follow/follow-api/src/test/java/com/rag/follow/follow/api/support/RealTomcatPort.java` | **22340 / 22359** ⇒ 落在 follow 域预分配段 **22300–22399** 内、且**不与** `follow-api` 的 `RealGrpcPort`（22300–22319）/ `follow-rpc` 的（22320–22339）重叠 |
| **R16** | `printf '404 page not found\n' \| wc -c` | **19**（**不是 20**）⇒ `S1-FOLLOW-010`（共享层 javadoc）/ `S1-FOLLOW-011`（like 域 DisplayName）的**错数来源** |
