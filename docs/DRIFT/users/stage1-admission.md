<!-- 由 `align/g1-users-follow` 工作树产出 · 基线 commit 7ec6fc6（= `do-my-own-gh`）· 核对日期 2026-10-28 -->

# 阶段 1 准入判定 · `users` 域（`user` + `admin` 两套 · **不含 `users-common`**）

> **目标口径**：[`docs/PhasedGoal.md`](../../PhasedGoal.md)（= [`twoStage.md`](../../twoStage.md) §2.1 **逐字相同**）· **流程义务**：`twoStage.md` §5.1~§5.7。
> **本文件只覆盖阶段 1**（AGENTS.md §5.3g：lane 只做阶段 1、只做本域）
> ⇒ ⛔ **不碰** `twoStage.md` §1.3 / §4 / §6 / §7（阶段 2 的业务语义逐项核对 + 12 维度生产级审查 ⇒ 收口执行者的事）。
> **被审范围**：`service/users/user/{user-rpc,user-api}` + `service/users/admin/{admin-rpc,admin-api}`。
> 🔴 **`service/users/users-common/**` 是共享层（AGENTS.md §5.3f）⇒ 本文件只读，其缺陷一律登记反馈单转派 G4**。
> **真源**：`Sea-RideTheWind_golang/service/user/{user,admin}/{api,rpc}/`（冻结，只读）。
> ⚠️ **本文件里的 ✅ 一律读作「在该证据强度下一致」**，不是已闭环（AGENTS.md §0 证据强度分层）。

---

## 0. 本域事实基线（阶段 0 味道，但只限本域）

| 组件 | Maven 模块 | 对外协议 | 端点数 | 主源码 | 测试文件 |
|---|---|---|---|---|---|
| `user-rpc` | `service/users/user/user-rpc` | gRPC | 9 个 RPC（`UserServiceGrpcImpl`） | 15 | 31 |
| `user-api` | `service/users/user/user-api` | HTTP `/user/v1/*` | 9（register/login/get/update/delete/avatar/avatar-history/avatar-select/logout） | 43 | 27 |
| `admin-rpc` | `service/users/admin/admin-rpc` | gRPC | 13 个 RPC（`AdminServiceGrpcImpl`） | 20 | 27 |
| `admin-api` | `service/users/admin/admin-api` | HTTP `/admin/v1/*` | 13（login/logout/getself/getuser/getuserlist/create/updateuser/delete/banuser/unbanuser/resetuserpassword/invite-create） | 56 | 23 |

**核心调用链（可追踪，AGENTS.md P0#7）**

```
user-api  (HTTP :49900)  ──gRPC plaintext──▶  user-rpc (:49210)  ──▶ PG  users_user / users_user_avatar_history
                                                                  └─▶ Redis  黑名单（写）
admin-api (HTTP :8889)   ──gRPC plaintext──▶  admin-rpc (:8081)  ──▶ PG  users_user / users_invite_code
                                                                  └─▶ Redis  黑名单（写）
```

- **持久化**：MyBatis-Plus + 显式 `setSql` 复刻 Go 侧 GORM `autoUpdateTime` 行为（**不依赖 PG 侧 `DEFAULT`** ⇒ 见 `users/user/rpc.md` §6.2）。
- **事务**：见 §1 第 3 条（头像链路在 **mapper 接口的 `default` 方法**上，入口**跨 Bean** ⇒ 经代理生效）。
- **服务发现**：Nacos（`discovery:///user-rpc` 三斜杠 · `discovery:///admin-rpc`）—— Go 侧是 etcd，属 **AGENTS.md §4.1 锁定的整组替换**（已签字 `DRIFT-NEW-001`）。

**基础设施接入（本机实测，2026-10-28）**

| 组件 | 地址 | 凭据（实测来源） |
|---|---|---|
| PostgreSQL | `Sea-postgres` → `127.0.0.1:49432` | user=`sea` / password=`sea` / db=`sea_user`（`docker inspect Sea-postgres`） |
| Redis | `Sea-redis` → `127.0.0.1:49380` | 无密码 |
| Nacos | `Sea-nacos` → `127.0.0.1:49848`（HTTP）/ `50848`（gRPC） | 无 auth · namespace `sea-rtw` · group `USER_GROUP` |

⚠️ **本机 5432 上还有一个 homebrew 的 PG**（role `pr`，与本项目无关）⇒ **探针必须显式指端口**，否则会连错库。

---

## 1. 阶段 1A：P0 九条逐条判定（§5.1 七项口径）

| # | P0 要求 | 当前实现情况 | 实现是否正确 | 是否已有验证证据 | 证据位置 | 是否影响简历项目准入 | 是否需要修改 | 可否暂缓至阶段 2 |
|---|---|---|---|---|---|---|---|---|
| **1** | 核心业务链路真实运行，无接口骨架 / 模拟数据 / 硬编码成功 | 4 个组件全部实装；主源码 **零** `TODO`/`FIXME`/`UnsupportedOperationException`/假实现（2026-10-28 逐文件静态扫描，见 §9 复现） | ✅ 正确 | ✅ L1 源码对照 + L4 活体 | [`user/rpc.md`](user/rpc.md) §2（10 个 OP 逐条 Go 语义模型）· [`admin/rpc.md`](admin/rpc.md) §2/§3 · [`user/api.md`](user/api.md) §2.3/§3.2 · [`admin/api.md`](admin/api.md) §2/§3 | ❌ 不影响 | ❌ 不需要 | — |
| **2** | 参数校验 / 业务规则 / 身份认证 / 权限控制有效 | 身份**只**来自 JWT claim `userId`（Go `authhandler.go:72-79` 同款）；Go 显式忽略 `aud/exp/sub/...`，RAG 只读 `userId` ⇒ 一致。`admin-api` 的 4 条认证差异（`DRIFT-ADMINAPI-001~004`）**全部已签字** ⇒ RAG 侧**更严**（claim 无效 ⇒ 401 且不发 RPC） | ✅ 正确（差异均为**已签字偏离**，非缺陷） | ✅ L1 + L4 | [`admin/api.md`](admin/api.md) §6 表 · §12.9 签字复核（2026-10-24 订正「仍需用户裁决」是**错的**）· 签字档 [`Intentional_Deviations/users/_shared.md`](../../Intentional_Deviations/users/_shared.md) | ❌ 不影响 | ❌ 不需要 | — |
| **3** | 关键数据正确持久化，事务边界合理 | `user-rpc` 头像链路事务在 mapper `default` 方法上，入口 `UserDomainService.uploadAvatar:648` / `.selectAvatar:584` **跨 Bean** ⇒ 经代理生效；`admin-rpc` 仅 `CreateAdmin` 有事务（4 步原子），其余 12 用例**两侧都无事务** ⇒ 一致 | ✅ 正确 | ✅ L4 真 PG（W3 撞 `UNIQUE(history_id)` ⇒ W2 的 UPDATE 真回滚） | [`user/rpc.md`](user/rpc.md) §6.4 事务对照表 · [`admin/rpc.md`](admin/rpc.md) §6 §15 行 | ⚠️ **有脆弱性**（见 §3 `S1-USERS-004`）：事务正确性依赖「调用方永远从别的 Bean 调进来」，改成同 Bean 自调用**不会报错** | 🟡 可加固（非阻断） | 🟡 可暂缓 |
| **4** | 核心失败路径有明确行为，不无声吞异常 | 401 族（缺头 / 坏签名 / claim 无效）· 400 族（非 JSON / 缺必填）· 413（超限 body）· 503（下游挂死）· Redis 断连与超时 —— 逐条有断言 | ✅ 正确 | ✅ L4 真 Tomcat + 真 Redis | [`user/api.md`](user/api.md) §5.3 错误流 · §6.3 鉴权 · 各 `*RedisDisconnected*Test` / `*RedisTimeoutConfigTest` | ❌ 不影响 | ❌ 不需要 | — |
| **5** | 能按**实际项目说明**完成构建 / 配置 / 启动 | **域内**：4 个组件可构建、可用 fat jar 起（2026-10-27 真启动 ✅ 6.4s/9.7s）。**项目级**：⚠️ `README.md` **只有 7 字节**（"本项目绑定spring"）⇒ **「实际项目说明」不存在**；`Infrastructure.md` 描述的 `api-gateway/`、`.env`、`service/user-rpc/` 路径**均已不存在**；compose 强制要求 `.env` 而**仓内无 `.env`、无 `.env.example`** | ❌ **域内正确 · 项目级不成立** | ❌ 项目级**无证据**（说明本身不存在） | 见 §3 `S1-USERS-001`（逐条实测清单） | 🔴 **是（项目级）** ⇒ 决定整个项目能否过阶段 1 | 🔴 **域外**（`README.md` / `Infrastructure.md` / `.env.example` / `.gitignore` **不在 G1 写权面**）⇒ **登记 + 上报** | ❌ **不可暂缓**（A 类） |
| **6** | 核心业务有可执行自动化测试，且断言真实业务结果 | 测试文件 108 份（31+27+27+23）；**4 个变异实测变红** ⇒ 非假绿 | ✅ 正确 | ✅ 已执行 | [`user/rpc.md`](user/rpc.md) §6.4 变异表 · [`_shared.md`](_shared.md) 变异实测节 | ❌ 不影响 | ❌ 不需要 | — |
| **7** | 模块职责清晰，核心调用链可追踪 | 4 组件职责边界清晰；台账有**逐文件对照索引** | ✅ | ✅ | [`user/api.md`](user/api.md) §5 · [`admin/rpc.md`](admin/rpc.md) §7 | ❌ 不影响 | ❌ 不需要 | — |
| **8** | 关键配置和敏感信息无明显不安全处理 | 9 个 rpc 模块的 datasource 口令默认值**分裂成 2 种**（7×`password` + **2×`Sea-TryGo`**，两处都在本域）；JWT secret 的 fallback 与 Go 侧硬编码值**逐字一致**且可 env 覆盖 | 🟡 **部分不成立** | ✅ 静态可证 | 见 §3 `S1-USERS-002` | 🟡 **域内那 2 处可自修**；域外另有 `.gitignore` 未排除 `.env`（`S1-USERS-003`） | ✅ **域内修 2 行**（§4 任务 T2） | 🟡 域外部分登记 |
| **9** | 已知严重缺陷 / 无法验证的关键能力 / 运行前提明确记录 | 三档台账（DRIFT / Intentional_Deviations / Pitfalls）+ `vs-common.md` 齐备；本轮新增登记见 §3 | ✅ | ✅ | [`_shared.md`](_shared.md) · [`vs-common.md`](vs-common.md) · [`../../Pitfalls/users/_shared.md`](../../Pitfalls/users/_shared.md) | ❌ 不影响 | ❌ 不需要 | — |

### 1.1 P0 判定小结

- **P0#5 是本域唯一的 A 类阻断项**（且它在**项目级**，不在 G1 写权面）⇒ 见 §3 `S1-USERS-001` 与 §6 上报项。
- **P0#2 的 4 条 admin 认证差异全部已签字** ⇒ 「Auth 维度 NOT EQUIVALENT」是**决策**不是缺陷（AGENTS.md §0 第 1 条：签字偏离**不重复质疑**）。
- **P0#3 有一处脆弱性**（事务依赖跨 Bean 调用）⇒ 记为 B 类（`S1-USERS-004`），不阻断。
- **P0#8 域内 2 行可自修** ⇒ §4 任务 T2。

---

## 2. 阶段 1B：最小必要实施计划（§5.2 八项口径）

| 任务 | 关联问题 | 修改范围 | 理由 | 依赖条件 | 预期行为 | 验收条件 | 验证命令 / 测试方案 |
|---|---|---|---|---|---|---|---|
| **T1** | — | 只做**事实核**（不改码） | 阶段 1A 的 7 项证据已在本轮重新核过一遍（配置 / 路由 / 事务 / 失败路径） | — | — | 结论逐条带 `文件:行号` | 见 §9 复现清单 |
| **T2** | `S1-USERS-002` | `service/users/user/user-rpc/src/main/resources/application.yaml:15` + `service/users/admin/admin-rpc/src/main/resources/application.yml:12` | 消除**全仓 9 处 datasource 口令默认值的分裂**（本域 2 处是少数派） | 无（纯配置，env 优先，**不改默认行为**） | 缺 `POSTGRES_PASSWORD` 时用 `password`（与其余 7 个 rpc 模块一致） | 9 处默认值**收敛为 1 种** | `grep -rhoE '\$\{POSTGRES_PASSWORD:[^}]*\}' service/ --include='*.yml' --include='*.yaml' \| grep -v target \| sort \| uniq -c` |
| **T3** | `S1-USERS-005` | `service/follow/follow-rpc/src/main/resources/application.yml:103`（**follow 域**，同批） | 本机 8082 被一个 34 天前的 `python -m http.server` 占着 ⇒ 无法真启动；且 `follow-api` 已有 `${FOLLOW_API_PORT:8891}` 的**同款可覆盖先例** ⇒ 域内补齐一致性 | 无 | 默认仍是 8082（**行为不变**），可 env 覆盖 | 默认端口不变 + 可覆盖生效 | 真启动探针（§5） |
| **T4** | `S1-USERS-001` | **域外**：`README.md` / `Infrastructure.md` / `.env.example` / `.gitignore` | P0#5 的项目级缺口 | 🔴 **不在 G1 写权面** ⇒ 交合并收口执行者 | — | — | 见 §6 上报项 |
| **T5** | — | 域级回归 + 真启动 | 阶段 1E | T2/T3 落码后 | 6 组件真进程起来 + 核心链路打通 | §5 验收表 | §5.2 / §5.3 |
| **T6** | — | P1 机制闭环 | 阶段 1E 选 3 个机制 | — | 9 问闭环 | §4.3 | §4.3 |
| **T7** | — | 台账回写 | 阶段 1C | — | 全部落 `docs/DRIFT/users/**` | 守门绿 | `python3 tools/check-ledger-*.py` + `python3 tools/check-doc-links.py` |
| **T8** | ——（**不是 §5.3 差异项**：行为中立的表达层改动 ⇒ 只进 §5.4 改造记录） | users / admin **四组件**的端口改为可 env 覆盖（`user-api` / `admin-api` / `user-rpc` / `admin-rpc`） | **上一轮已落码但台账未记** ⇒ 本轮**补记 + 核验**（§4.3）；默认端口**一字不变** ⇒ 行为零变化 | 无 | 默认端口不变 + 可覆盖生效 | 静态：四处默认端口逐字不变；运行：改 env 后**监听口与 Nacos 注册口一致** | §4.3 + §9 R3 |
| **T9** | ——（**不是 §5.3 差异项**：测试基础设施缺口，不影响产品行为 ⇒ 只进 §5.4） | `service/follow/follow-api/pom.xml` 补 `TESTCONTAINERS_RYUK_DISABLED` | **域级回归实测**：`follow-api` 两个 Testcontainers 类**整类 error**（ryuk 镜像拉不到 · Docker Hub 不可达）⇒ **域侧能自修 ⇒ 域侧修**（§4.4） | 无 | 两个类**真跑起来**（不是跳过、不是 0 断言） | `Tests run: 2 + 2` 全绿 且 日志无 ryuk 拉取 | §4.4 的验证命令 |
| **T10** | — | `follow-proto` 补**仓内契约回归网**（`FollowProtoContractTest` ＋ 真源生成的基准 ＋ follow 专属生成脚本） | 基线判 `follow-proto` = **R2**（R3 的 ⑤ 不满足）⇒ **域侧能自修 ⇒ 域侧修**（§4.5） | 无 | 生成码描述符 ≡ Go 真源基准 | **3 条用例全绿**（含嵌套 message 单独成行 ＋ 变异自检） | §4.5 的验证命令 |

---

## 3. 阶段 1C：差异与暂缓项清单（§5.3 十一项，逐条填满）

### `S1-USERS-001` 🔴 **A 类** · 项目说明缺失 ⇒ P0#5 不成立

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-001` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧无「说明文档」这一契约；**RAG 侧的对应义务来自 `twoStage.md` P0#5**（「项目能够按照**实际项目说明**完成构建、配置和启动」）· 契约落点 = 根 `README.md` |
| 3 | 原始行为 | Go 真源仓的 `README.md` 描述了完整的构建/配置/启动流程（`Sea-RideTheWind_golang/README.md`） |
| 4 | 阶段 1 当前行为 | `README.md` **只有 7 字节**（`本项目绑定spring`）⇒ **说明书实质不存在**；`Infrastructure.md` 自称「以 `docker-compose.yaml` / `application.yml` / `.env` / `manage.sh` 为唯一依据」，但它写的 `api-gateway/`、`.env`、`service/user-rpc/`、`service/admin-rpc/` **全部不存在**，端口（`49432`/`sea_user`/`sea`）与实际 compose（`5432`/`first_db`/`admin`）不符，「16 个服务里只有 3 个能跑」与现状（10 微服务 / 38 组件）矛盾 ⇒ **照它操作必然走错** |
| 5 | 具体差异 | ① 无 README；② 基础设施文档与事实全面矛盾；③ compose 要求 `.env`（`${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}`）而**仓内既无 `.env` 也无模板** ⇒ 不知道该填什么；④ `manage.sh:37-46` 硬校验 `.env` 必须含 `POSTGRES_PASSWORD` + `USER_JWT_SECRET` ⇒ 同样无从得知 |
| 6 | 暂缓 / 简化理由 | **无法暂缓** —— 它不是「高级工程能力」，是 P0#5 定义的**准入条件本身** |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响运行时行为；影响**可评估性**：面试官/验收者按说明**无法复现启动** ⇒ 阶段 1「核心业务链路能够真实运行」**无法被外部复现**，只能靠内部台账自证 |
| 8 | 是否阻断阶段 1 | 🔴 **是（A 类）** |
| 9 | 阶段 2 的恢复或替换方案 | 阶段 1 先补**最小可复现说明书**（README：架构一句话 / 模块清单 / 环境变量表 / 启动顺序 / 验证命令 / 已知限制），阶段 2 再扩成完整运维手册 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：全新克隆 ⇒ 只读 README ⇒ 能在**不询问作者**的前提下起 PG+Redis+Nacos+应用并打通一条核心链路；通过标准 = 另一人照做成功且无需口头补充 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`README.md` = 7 字节（`wc -c`）；`Infrastructure.md:74-88` 列的 16 服务中 5 个路径实测不存在（`ls` 逐条）；`docker-compose.yaml:15` 的 `:?` 强制；`manage.sh:37-46` 的硬校验；仓内 `find . -name '*.env*'` = 0 命中。**缺口**：README / `.env.example` / `.gitignore` 均**不在 G1 写权面** ⇒ 本 lane 只能登记与上报 |

### `S1-USERS-002` 🟡 **B 类** · datasource 口令默认值全仓分裂（本域 2 处是少数派）

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-002` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/user/user/rpc/etc/user.yaml:13` + `service/user/admin/rpc/etc/admin.yaml:14` ⇒ `Password: "${POSTGRES_PASSWORD}"` / `DataSource: "postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@..."`（**无默认值**） |
| 3 | 原始行为 | go-zero `core/conf/config.go:63` = `loader([]byte(os.ExpandEnv(string(content))), v)` ⇒ `os.ExpandEnv` **不认 `${VAR:default}` 语法** ⇒ 未设 `POSTGRES_PASSWORD` 时**展开为空串** ⇒ 用**空口令**连库 ⇒ 认证失败 ⇒ **fail-fast** |
| 4 | 阶段 1 当前行为 | RAG 侧 9 个 rpc 模块用 Spring 的 `${POSTGRES_PASSWORD:password}`（7 处）与 `${POSTGRES_PASSWORD:Sea-TryGo}`（2 处 = **本域**）⇒ 未设 env 时**静默用硬编码口令**（与 Go 的 fail-fast **相反**）；且**两种默认值并存** ⇒ 同一套 PG，裸跑时 9 个模块里**最多一组能连上** |
| 5 | 具体差异 | ① 失败时机：Go = 连库时失败；RAG = 用错口令连库（更晚、更难懂）；② 默认值分裂：`Sea-TryGo` ×2 vs `password` ×7；③ 硬编码凭据进版本库（**P0#8 面**） |
| 6 | 暂缓 / 简化理由 | 域内 2 行可自修（T2）⇒ **不暂缓**；「完全取消 fallback」这一条**暂缓到阶段 2**（要连带改 `tools/difftest` 装置与 9 个模块 ⇒ 跨域 ⇒ 不在 G1 写权面） |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响已正确配置环境的运行；**放大误配置**：缺 env 时不早失败，而是拿一个**已知错误**的口令去连 ⇒ 故障点远离根因 |
| 8 | 是否阻断阶段 1 | ❌ **否**（compose / `manage.sh` / 对拍装置**都显式注入** `POSTGRES_PASSWORD` ⇒ 文档化路径永远不落 fallback） |
| 9 | 阶段 2 的恢复或替换方案 | ① 全仓 9 处统一为**一个**来源（`.env` ⇒ env 注入，**取消 fallback**，与 Go 的 fail-fast 对齐）；② 补 `.env.example`；③ 对拍装置已导出 `POSTGRES_PASSWORD`（`tools/difftest/run.sh:196`）⇒ **取消 fallback 不破坏现有装置** |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：9 个 rpc 模块在**不设** `POSTGRES_PASSWORD` 时**全部启动失败并给出明确的配置缺失错误**（不是 SQL 认证错误）；通过标准 = 缺 env 时的失败信息指向「缺配置」而非「口令错」 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`grep` 逐文件枚举 9 处（排除 `target/` 副本，见 §9 复现）；`go-zero@v1.10.2/core/conf/config.go:63`；`tools/difftest/run.sh:196` 导出 `POSTGRES_PASSWORD=difftest` ⇒ **装置不依赖 fallback**。**缺口**：取消 fallback 的跨域改动不在 G1 写权面 |

### `S1-USERS-003` 🟡 **B 类** · `.env` 不在 `.gitignore` ⇒ 照说明做会把密钥提交进仓

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-003` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 真源仓的 `.gitignore` 排除 `.env`（Go 侧 `.env` 是真源仓的一部分，**不进版本库**） |
| 3 | 原始行为 | Go 侧 `git ls-files \| grep '^\.env'` = 0 命中 ⇒ 密钥文件**从未**进过版本库 |
| 4 | 阶段 1 当前行为 | RAG 侧 `.gitignore` **不含任何 `.env` 规则**（实测 `grep -nE '\.env' .gitignore` = 0 命中；`git ls-files` 也 = 0 命中 ⇒ 只是**还没人提交过**，不是**被保护**） |
| 5 | 具体差异 | `manage.sh:37-46` **强制**要求仓根存在 `.env` 且含 `POSTGRES_PASSWORD` + `USER_JWT_SECRET` ⇒ **照项目说明做**的人必然创建 `.env` ⇒ 而 `.gitignore` 不拦它 ⇒ `git add -A` 即把**真实口令与 JWT 签名密钥**提交进版本库 |
| 6 | 暂缓 / 简化理由 | **无法暂缓**：一行 `.gitignore` + 一个 `.env.example` 即可根治 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **安全性**：这是 P0#8 唯一一条**「照说明做就会中招」**的缺陷 ⇒ 影响最直接 |
| 8 | 是否阻断阶段 1 | 🟡 **否，但属 P0#8 面的强命中**（记为 B 类，**修法只差两行**） |
| 9 | 阶段 2 的恢复或替换方案 | ① `.gitignore` 加 `.env`（+ `.env.*` 但放行 `.env.example`）；② 提交 `.env.example`（只含键名与占位，**不含值**）；③ `manage.sh` 在 `.env` 缺失时**自动从 `.env.example` 复制**并提示填写 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：创建含真实口令的 `.env` ⇒ `git status` **不出现** `.env`；`.env.example` 存在且**不含任何真实值**；通过标准 = 两条断言同时成立 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`.gitignore` 实测无 `.env` 规则；`manage.sh:37-46` 实测强制要求；`git ls-files` 无 `.env`。**缺口**：`.gitignore` / `.env.example` **不在 G1 写权面** |

### `S1-USERS-004` 🟡 **B 类** · 头像链路的事务正确性**依赖调用方永远跨 Bean 调用**（无报错保护）

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-004` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/user/user/rpc/internal/model/user.go:72` / `:134` = `m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {...})` ⇒ **事务边界写在数据层** |
| 3 | 原始行为 | Go 的事务由 GORM 闭包**显式开启** ⇒ 无论谁调用，边界都在 |
| 4 | 阶段 1 当前行为 | RAG 侧 `@Transactional` 标在 **mapper 接口的 `default` 方法**上（`UserAvatarHistoryMapper.java:118` / `:174`）⇒ **必须经 Spring 代理**才生效；入口 `UserDomainService.uploadAvatar:648` / `.selectAvatar:584` 是**跨 Bean 调用** ⇒ 当前**经代理** ⇒ 事务生效 |
| 5 | 具体差异 | 两处 `clearCurrentByUid` / `insertHistory` / `updateUserExtraInfo` 是**同 Bean 自调用**（此刻事务已在代理边界内，靠 `SqlSessionTemplate` → `DataSourceUtils` 的线程绑定连接参与同一事务 ⇒ **当前正确**）；⚠️ 但**若有人把入口改成同 Bean 自调用，事务会静默失效且不报错** |
| 6 | 暂缓 / 简化理由 | 当前实现**正确**，缺的是**防退化**；加固手段（把 `@Transactional` 移到 `UserDomainService` 的 public 方法上，或加一条「入口必须经代理」的断言测试）会改动已验证的事务边界 ⇒ 阶段 1 不动，阶段 2 做 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **当前无影响**；**未来退化的影响 = 头像状态机被拆成两个事务** ⇒ 出现「current 已清、history 未插」的半截数据 |
| 8 | 是否阻断阶段 1 | ❌ **否** |
| 9 | 阶段 2 的恢复或替换方案 | ① 事务边界上移到 `UserDomainService` 的 public 入口（与 Go 的「数据层显式闭包」同形）；② 补一条**守门测试**：断言入口方法上的 `@Transactional` 存在且在代理上（防止后人把它挪进自调用） |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：把入口改成同 Bean 自调用 ⇒ 守门测试**必须变红**（变异验证）；通过标准 = 变异后红、还原后绿 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`users/user/rpc.md` §6.4 事务对照表（含「经代理 / 无 self-invocation 风险」逐条）+ 真 PG 回滚实测。**缺口**：现有 `AvatarTransactionWiringTest#userMapperIsNotProxied_butStillJoinsAmbientTransaction` **测不到**「入口必须经代理」这一条（它测的是 UserMapper 不被代理）⇒ 台账已明写此缺口 |

### `S1-USERS-005` 🟡 **B 类** · `follow-rpc` 的 gRPC 端口**硬编码**，本机被占 ⇒ 无法并行真启动

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-005` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/follow/rpc/etc/follow.yaml:2` `ListenOn: 0.0.0.0:8082`（yaml 固定） |
| 3 | 原始行为 | Go 的端口写在 yaml 里 ⇒ 改端口要改文件 |
| 4 | 阶段 1 当前行为 | RAG 侧 `service/follow/follow-rpc/src/main/resources/application.yml:103` = `port: 8082`（**硬编码，无 env 覆盖**）⇒ 本机 8082 被一个 34 天前的 `python -m http.server 8082` 占着（实测 PID 9391）⇒ 无法在不动用户进程的前提下真启动 |
| 5 | 具体差异 | 域内不一致：`follow-api` 已经是 `${FOLLOW_API_PORT:8891}`（**可覆盖**），`follow-rpc` 不可覆盖 ⇒ 同一域内两个组件的端口策略不同 |
| 6 | 暂缓 / 简化理由 | 域内 1 行可自修（T3）⇒ **不暂缓** |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响默认运行（8082 默认不变）；影响**可验证性**与**并行开发**（多棵 lane / 本机已有服务时无法起进程） |
| 8 | 是否阻断阶段 1 | ❌ **否**（真启动可用 env 覆盖绕开；且这只影响**验证手段**，不影响产品行为） |
| 9 | 阶段 2 的恢复或替换方案 | 域内 1 行改完（`${FOLLOW_RPC_PORT:8082}`）⇒ 本轮即做（T3）；阶段 2 可把 6 个组件的端口**统一**为可覆盖（`users`/`admin` 侧也补） |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：`FOLLOW_RPC_PORT=18082` 启动 ⇒ Nacos 注册地址**同步**为 18082 且 `follow-api` 能发现；通过标准 = 注册地址与监听端口一致且链路通 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`application.yml:103` 硬编码；`lsof -nP -iTCP:8082 -sTCP:LISTEN` ⇒ PID 9391 = `python -m http.server 8082`（已运行 34 天）；`follow-api` 的 `${FOLLOW_API_PORT:8891}` 先例。**缺口**：注册地址是否随 env 端口同步，待 T3 落码后真启动实测 |

---

## 4. 阶段 1D：执行实际改造（§5.4 九项，逐条填满）

> 本节只记录**本轮实际改的**。**共享层（`service/common` / `users-common`）本轮零改动** ⇒ 其缺陷一律走 §6 反馈单。

### 4.1 `T2` · 统一 datasource 口令默认值（users 两个 rpc 模块）

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `service/users/user/user-rpc/src/main/resources/application.yaml:15` = `password: ${POSTGRES_PASSWORD:Sea-TryGo}`；`service/users/admin/admin-rpc/src/main/resources/application.yml:12` = `password: ${POSTGRES_PASSWORD:Sea-TryGo}` ⇒ 未设 env 时用**硬编码口令 `Sea-TryGo`** |
| **具体问题** | 全仓 9 个 rpc 模块的同一配置项有**两种默认值**（7×`password` / 2×`Sea-TryGo`）⇒ 同一套 PG，裸跑时**最多一组能连上**；且本域是少数派（`S1-USERS-002`） |
| **修改后的实现** | 两处改为 `password: ${POSTGRES_PASSWORD:password}`（**与另外 7 个 rpc 模块逐字一致**） |
| **修改理由** | ① 消除全仓默认值分裂（本域 2 处是唯一异类）⇒ 域侧能自修的最大面；② **不改变已配置环境的行为**（env 存在时 Spring 优先取 env ⇒ compose / `manage.sh` / 对拍装置的注入值**一字不变**）；③ 默认为 8082 式「与多数派一致」比「与真实基础设施不一致」更诚实 —— 真实基础设施口令是 `sea`（实测），**两个默认值都不是它** ⇒ 真正该做的是取消 fallback（阶段 2，跨域） |
| **实际变更文件** | `service/users/user/user-rpc/src/main/resources/application.yaml`（1 行）<br>`service/users/admin/admin-rpc/src/main/resources/application.yml`（1 行） |
| **测试场景和断言** | ① 静态断言：全仓 `${POSTGRES_PASSWORD:*}` 唯一化为 1 种（§9 复现 R2）；② 运行断言：6 组件真启动后连库成功（§5.2）⇒ **证明改默认值没有破坏任何启动路径** |
| **实际执行结果** | ⏳ **落码完成，验证待 §5 回填**（本轮初稿曾在此处直接写「已全绿」，属**先写结论后取证据** ⇒ 已改为待回填）<br>· 静态唯一化断言 ✅（§9 R2 实测：`10 ${POSTGRES_PASSWORD:password}` / 9 个模块）<br>· 域级回归 ⏳ · 真启动连库 ⏳ |
| **尚未解决的问题** | 真实基础设施口令是 `sea`，**不等于** `password` ⇒ 裸跑（不设 env）仍连不上真实库 ⇒ 这是「取消 fallback」要解决的，属阶段 2 跨域项（`S1-USERS-002` 第 9 项） |
| **对阶段 2 的影响** | 无冲突；阶段 2 做「取消 fallback + 全仓统一 + `.env.example`」时，本轮这一行是**中间态**，会被那一步取代 |

### 4.2 `T3` · `follow-rpc` 的 gRPC 端口改为可 env 覆盖（follow 域，同批落码）

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `service/follow/follow-rpc/src/main/resources/application.yml:103` = `port: 8082`（**硬编码**）⇒ 本机 8082 被占 ⇒ 该组件**无法真启动**（不动用户进程的前提下） |
| **具体问题** | 域内不一致：`follow-api` 用 `${FOLLOW_API_PORT:8891}`，`follow-rpc` 不可覆盖（`S1-USERS-005`） |
| **修改后的实现** | `port: ${FOLLOW_RPC_PORT:8082}` ⇒ **默认端口一字不变**（行为零变化），可 env 覆盖 |
| **修改理由** | ① 与同域 `follow-api` 的**既有先例**一致；② 解除「端口被占 ⇒ 组件不可验证」的死结（阶段 1E 的真启动需要它）；③ 表达层改动，**不触碰 Go 行为**（Go 侧端口在 yaml 固定，RAG 侧默认值仍是 8082） |
| **实际变更文件** | `service/follow/follow-rpc/src/main/resources/application.yml`（1 行） |
| **测试场景和断言** | ① 静态：默认仍是 8082（§9 R3）；② 运行：`FOLLOW_RPC_PORT=<空闲口>` 真启动 ⇒ **Nacos 注册地址与监听端口一致**（否则 `follow-api` 发现的是旧口 ⇒ 链路断）⇒ §5.2 断言 |
| **实际执行结果** | ✅ 真启动 + 注册地址一致性实测（§5.2） |
| **尚未解决的问题** | `users` / `admin` 侧端口仍硬编码（`49210` / `8081` / `8889` / `49900`）⇒ 阶段 2 统一 |
| **对阶段 2 的影响** | 无冲突；阶段 2 可把 6 个组件的端口统一为可覆盖 |

### 4.3 `T8` · users / admin 四组件的端口改为**可 env 覆盖**（**上一轮落码 · 本轮补记**）

> ⚠️ **本节是「补记」**：这 4 处改动在 2026-10-28 就已落码，但**上一轮的 §4 只记了 T2/T3**
> ⇒ **落码与台账不同步**（AGENTS.md §6：「已改」≠「已验证」⇒ 台账是审计面，必须对齐）⇒ **本轮补齐**。

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `user-api` `port: 49900` · `admin-api` `port: 8889` · `user-rpc` `port: 49210` · `admin-rpc` `port: 8081` ⇒ **四处全是写死字面量** |
| **具体问题** | ① 与同域 `follow-api`（`${FOLLOW_API_PORT:8891}`）/ `follow-rpc`（本轮 T3）的**可覆盖策略不一致**；② **本机该口被占 ⇒ 组件无法真启动**（实测：49210 被 `Sea-user-rpc` 容器占着 · 8081/8889 上还跑着 `Sea-RideTheWind_java` 的进程 —— **AGENTS.md §1 明确排除在 RAG 范围外 ⇒ 绝不能去 kill 它**）⇒ ⇒ **阶段 1E 的「真启动」验证手段被卡死** |
| **修改后的实现** | 四处改为 `${USER_API_PORT:49900}` / `${ADMIN_API_PORT:8889}` / `${USER_RPC_PORT:49210}` / `${ADMIN_RPC_PORT:8081}` ⇒ **默认端口一字不变 ⇒ 行为零变化** |
| **修改理由** | ① 域内**策略一致**（与 `follow-api`/`follow-rpc` 同款）；② 解除「端口被占 ⇒ 组件不可验证」的死结（阶段 1E 的前提）；③ **表达层改动，不触碰 Go 行为**（Go 侧端口写在 yaml 固定值，RAG 侧默认值不变） |
| **实际变更文件** | `service/users/user/user-api/src/main/resources/application.yml`（1 行）<br>`service/users/admin/admin-api/src/main/resources/application.yml`（1 行）<br>`service/users/user/user-rpc/src/main/resources/application.yaml`（1 行）<br>`service/users/admin/admin-rpc/src/main/resources/application.yml`（1 行 · 与 T2 同一文件） |
| **测试场景和断言** | ① 静态：四处**默认端口逐字不变**（§9 R3）；② 运行：改 env 后**监听端口与 Nacos 注册端口一致**（见下条「已核」） |
| **实际执行结果** | ✅ **默认端口已核为逐字不变**（§9 R3）；✅ **注册/监听不会脱钩**（已核：三个 rpc 组件的 `spring.cloud.nacos.discovery` **没有独立的 `port` 字段** ⇒ 注册端口随 `grpc.server.port` 走 ⇒ **env 覆盖两边同步** ⇒ 不存在「监听改了、注册没改」的断链）<br>⚠️ **「改 env 后真启动 + 注册地址一致」的行为级实测本轮未做**（要动到本机已被占的端口 + Nacos 注册表 ⇒ 属阶段 2 的验证手段）⇒ **按 L1 静态记，不按 L3 活体记** |
| **尚未解决的问题** | 根级 `docker-compose.yaml` **没有**这 5 个 api 的服务定义 ⇒ compose 起不来 ⇒ 只能裸 jar 起（`S1-USERS-001` / `S1-FOLLOW-004`） |
| **对阶段 2 的影响** | 无冲突；阶段 2 可把端口治理统一成一份策略（含 `prometheus` / `Nacos` 注册端口的一致性检查） |

### 4.4 `T9` · `follow-api` 的 pom 补 `TESTCONTAINERS_RYUK_DISABLED`（**本轮落码 · 域侧自修**）

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `service/follow/follow-api/pom.xml` **完全没有 `maven-surefire-plugin` 配置段** ⇒ Testcontainers 会去启动 **Ryuk 资源回收器** ⇒ 它要**从 Docker Hub 拉 `testcontainers/ryuk:0.14.0`** ⇒ **本机 `registry-1.docker.io` 不可达**（实测 `curl` 15s 超时）⇒ ⇒ **`follow-api` 的两个 Testcontainers 类整类 error**（`FollowApiRedisDisconnectedFastFailTest` / `FollowApiRedisDisconnectedDefaultBehaviorTest` ⇒ `ContainerFetchException: Can't get Docker image`） |
| **具体问题** | 全仓「凡真跑 Testcontainers 的模块」**都**注入了 `TESTCONTAINERS_RYUK_DISABLED=true`（15 个模块：`common` / `user-rpc` / `user-api` / `admin-rpc` / `admin-api` / `follow-rpc` / `like-rpc` / `comment-rpc` / `message-rpc` / `message-api` / `favorite-rpc` / `favorite-api` / `hot-rpc` / `task-rpc` / `article-rpc`）⇒ **唯独 `follow-api` 没有** ⇒ ⇒ **它是全仓 52 个模块里唯一会去拉 ryuk 的** ⇒ ⇒ **域级回归里只有它整类红** |
| **修改后的实现** | `follow-api/pom.xml` 增加 `maven-surefire-plugin` 段 + `<environmentVariables><TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED></environmentVariables>`（与 `follow-rpc/pom.xml:246-254` **同款**） |
| **修改理由** | ① **域侧能自修 ⇒ 域侧修**（AGENTS.md §5.3e「不甩单、不留半截」）⇒ 不占用 G4；② 这是**测试基础设施缺口**，不是产品缺陷 ⇒ 不进 P0；③ 与同域 `follow-rpc` 保持一致 |
| **实际变更文件** | `service/follow/follow-api/pom.xml`（+18 行，含说明注释） |
| **测试场景和断言** | ① **正向**：原报错的两个类**必须真跑起来**（不是跳过、不是 0 断言）⇒ 断言数 = **2 + 2**；② **反向**：日志里**不得再出现** `Pulling docker image: testcontainers/ryuk` |
| **实际执行结果** | ✅ **实测通过**：`mvn -o -B -pl service/follow/follow-api,service/follow/follow-rpc -am test -Dtest='FollowApiRedisDisconnected*,FollowProtoContractTest' -DfailIfNoTests=false` ⇒ `FollowApiRedisDisconnectedFastFailTest` **2/2 绿** · `FollowApiRedisDisconnectedDefaultBehaviorTest` **2/2 绿** ⇒ **整类 error 消失** ⇒ 且日志**无 ryuk 拉取记录** |
| **尚未解决的问题** | **另外 5 个 api 模块同样缺**（`article-api` / `comment-api` / `hot-api` ⇒ G3；`like-api` / `task-api` ⇒ G2）⇒ **不在 G1 写权面 ⇒ 已登记上报**（follow 档 `S1-FOLLOW-009`） |
| **对阶段 2 的影响** | 无冲突；阶段 2 可把这条做成**机器守门**（扫全部 pom ⇒ 凡有 Testcontainers 依赖的模块必须有该 env）⇒ **根治「新增模块忘加」** |

### 4.5 `T10` · `follow-proto` 补**仓内契约回归网**（**本轮落码 · 域侧自修**）

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `follow-proto` **一行 junit 依赖都没有**（pom 里只有生成代码所需的 protobuf / grpc）⇒ **本组件 0 例测试** ⇒ 基线判 `follow-proto` = **R2**（R3 的第 ⑤ 条「RAG 侧有自动化测试断言该行为」**不满足**）⇒ 且 `check-proto-static.sh` **只覆盖 user / admin / task 三域** ⇒ **follow 的 proto 漂了不会红**（基线 §九 `G-6`） |
| **具体问题** | 「`.proto` 文件两侧一致」≠「**生成码的契约**一致」（字段号 / `optional` presence / `repeated` / 枚举值编号 / rpc 签名 —— AGENTS.md §3.4 的效力边界）⇒ **follow 域缺这条回归网** |
| **修改后的实现** | ① 新增 `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/proto/FollowProtoContractTest.java`（**3 条用例**）② 新增 `service/follow/follow-rpc/src/test/resources/proto-contract/follow-proto-contract.expected.txt`（**由脚本从 Go 真源生成**）③ 新增 `service/follow/follow-rpc/src/test/resources/proto-contract/generate.py`（**follow 专属**） |
| **修改理由** | ① **期望值必须由真源生成**（手抄 = 第二份可能漂移的副本 ⇒ 「期望值必须由真源生成」这条教训）⇒ 基准文件记录 Go 真源 `proto/follow.proto` 的 **sha256**；② **放在消费方 `follow-rpc`**（`follow-proto` 无 junit 依赖 ⇒ 加测试要先改 pom；与 hot / article / favorite 域**同款做法**）；③ **比对的是生成码的描述符**（`RelationReq.getDescriptor().getFile()`）而不是 `.proto` 文本 ⇒ 证的是**编译产物**的契约 |
| **实际变更文件** | 上述 3 个文件（新增） |
| **测试场景和断言** | ① **契约逐条一致**：生成码描述符渲染的 `message` / `field=号:类型` / `service` 序列 **逐行等于** Go 真源基准（含 `optional` / `repeated` / `map` / 枚举值编号 / rpc 签名）；② **嵌套 message 单独成行**：本域 proto 是全仓 10 个里**唯一带嵌套 message** 的（`RecommendResp.RecommendUser`）⇒ 断言它**单独成行**且父级**带着自己的** `users=3:repeated RecommendUser`；③ **变异自检**：把任一字段号 +1 ⇒ **必须与生成码描述符不等**（证明守门不是空转） |
| **实际执行结果** | ✅ **实测 3/3 绿**（`Tests run: 3, Failures: 0, Errors: 0` · 0.107 s）<br>⚠️ **实测发现并修掉了一个「假基准」风险**：**hot 域那份 `generate.py` 的非贪婪正则会解析错嵌套 message**（`message\s+(\w+)\s*\{(.*?)\}` ⇒ 在内层 `}` 处截断 ⇒ 对 follow.proto 产出 `message\|RecommendResp\|code=1:int32\|msg=2:string\|target_id=1:int64\|mutual_score=2:int32` ⇒ **内层字段被平铺进父级、父级自己的 `users` 字段整条丢失**）⇒ ⇒ **follow 必须用自己的脚本**（括号配对 + 递归）⇒ 本轮已实现并验证 |
| **尚未解决的问题** | ① **`check-proto-static.sh` 仍未覆盖 follow**（属**共享脚本** ⇒ 不在 G1 写权面 ⇒ 已登记上报）—— 但**功能上已由本测试覆盖**（更严：它比对的是**生成码描述符**而非文本 diff）；② **「消费方式」（`optional` presence 的**构造点**语义）仍未取证** ⇒ 基线 §九 `G-19`（**取证缺口，不降 R**）—— follow 的 proto **没有 `optional` 字段** ⇒ 该边界在本域**不适用** |
| **对阶段 2 的影响** | 无冲突；阶段 2 可把 `generate.py` 的递归解析**回灌 hot / article / favorite 三域**（它们目前用的是非贪婪版 ⇒ 若它们的 proto 有嵌套 message，同一问题已在潜伏） |

### 4.6 `T11` · 🆕 **本轮落码 · 域侧自修** · 补齐 follow 域**独立失败路径矩阵** ⇒ 基线 §九 `G-13` 在本域**闭合**

> **背景**：基线 P0 #2 的判据原文（「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限四项都有效」；且失败路径**域覆盖不全**）＋ §九 `G-13`（`article` / `follow` / `task` / `hot` 四域无独立失败路径矩阵）。
> ⇒ 本轮**在 follow 域补齐那份矩阵**，且**用真 Tomcat 跑**（原先 10 个测试类**全部** `webEnvironment = MOCK` ⇒ **真容器覆盖率为零**）。

| 项 | 内容 |
|---|---|
| **修改前的实现和行为** | `follow-api` 的 **10 个测试类全部是 MockMvc** ⇒ ① **真 Tomcat 覆盖率 = 0** ⇒ Content-Type 的**容器归一化**（`text/plain; charset=utf-8` → `text/plain;charset=utf-8`）/ `Content-Length` / **容器会不会补 body** 三处**一律测不到**（**DRIFT-024 当初漏网即因此**）② **失败路径只覆盖了部分端点**（认证只测 `/follow/list`、业务规则只测 `/follow`）⇒ P0#2 的四项**推不出来** |
| **具体问题** | 「每个端点都有 ≥1 条失败路径测试」≠「四项失败机制都有效」；且「MockMvc 绿」≠「线上字节一致」（容器会改写 Content-Type）|
| **修改后的实现** | ① 新增 `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/support/RealTomcatPort.java`（端口段 **22340–22359** · **双地址** bind 自检）② 新增 `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java`（**62 条**）③ 改 `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java` 的 ② 段（「端口类个数」断言 **8 → 9** ⇒ **同批更新，不留雷**）|
| **修改理由** | ① **期望值逐条抄自 Go 源码**（每条带 `文件:行号`，写在测试的 `Endpoint` 表里）⇒ 满足基线 §1.1 **R3** 的 ④⑤；② **必须真容器** ⇒ MockMvc 系统性地测不到上面三处；③ **端口必须自己挑** ⇒ `RANDOM_PORT` 会落进 OS 临时段（与 Docker 发布口同段）⇒ 域内守门 `BlindPortGateTest` ① 会红 |
| **实际变更文件** | 上述 3 个文件（2 新增 + 1 修改）|
| **测试场景和断言** | **8 端点 × 4 类失败** ＋ 基础设施类（rpc 异常映射）＋ 7 条单点。矩阵逐条 ⇒ 见 [`docs/DRIFT/follow/stage1-admission.md` §4.2](../follow/stage1-admission.md) 的矩阵表 |
| **实际执行结果** | ✅ **62/62 绿**（`Tests run: 62, Failures: 0, Errors: 0, Skipped: 0` · 5.471 s · **真 Tomcat**）<br>⚠️ **首轮曾 12 条红** ⇒ **两条都是判据自身的错，不是产品缺陷**：① `assertEnvelope` 对**成功**信封也要求 `data:null` ② 404 的 `Content-Length` 被写死成 **20**（实测 **19**）⇒ **已改判据**（AGENTS.md §3.3 规则 ③：先改/删错误断言，再看实现）|
| **尚未解决的问题** | ① **P0#2 本身仍未判成 ✅** —— 其余 3 域（`article` / `task` / `hot`）**仍无**独立失败路径矩阵 ⇒ 本条只闭合 **follow 域**；② **本类不是双侧对拍** ⇒ 按 **R3** 计入，**不按 R4** ⇒ **不得**据此宣称「与 Go 逐字节一致」；③ 共享层与 like 域两处「20 字节」注释差 1 ⇒ **上报 G4 / G2**（`S1-FOLLOW-010` / `S1-FOLLOW-011` / `S1-USERS-009`）|
| **对阶段 2 的影响** | 无冲突；阶段 2 可把同款矩阵**回灌** `article` / `task` / `hot` 三域（基线 §九 `G-13` 的其余三域）|

### 4.7 阶段 1D 中**本轮明确没做**的事（诚实边界）

| # | 没做 | 原因 |
|---|---|---|
| 1 | 取消 datasource 口令 fallback | **跨域**（9 个模块 / 8 个域）⇒ 不在 G1 写权面 ⇒ 登记 `S1-USERS-002` 第 9 项 |
| 2 | 改 `README.md` / `Infrastructure.md` / `.gitignore` / 补 `.env.example` | **根级共享文件** ⇒ 不在 G1 写权面 ⇒ 登记 `S1-USERS-001` / `S1-USERS-003` + §6 上报 |
| 3 | 动 `service/common` / `users-common` | 共享层**写权唯一属 G4** ⇒ 本轮零改动，只登记反馈单（§6） |
| 4 | 把头像链路的事务边界上移 / 加「入口必须经代理」守门 | 会改动**已验证**的事务边界 ⇒ 阶段 1 不动（`S1-USERS-004`） |
| 5 | 任何 `docs/DRIFT/_INDEX.md` / `AGENTS.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` 改动 | 合并收口执行者的事（AGENTS.md §5.3d / Worktree.md §1d） |
| 6 | 阶段 2 的业务语义逐项核对（`twoStage.md` §1.3 / §4）与 12 维度生产级审查（§6 / §7） | 收口执行者的事（AGENTS.md §5.3g 明令不碰） |

---

## 5. 阶段 1E：执行必要验证（§5.5）

> **本节是本轮 G1 收工的核心产出** —— 上一轮此处是「⏳ 待回填」，**本轮全部实跑并回填**。
> ⚠️ **取数纪律**（AGENTS.md §3.3 + 「条数必须在构建收工后采」）：条数**逐个 `TEST-*.xml` 的
> `testsuite` 属性求和**，**不数日志**（`-am` 会先构建依赖模块 ⇒ 按日志分段聚合会算错），
> 且**只在 maven 进程退出后采样**（构建中途采到的是「既不是旧的、也不是新的」的半成品）。

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
| **用例总数** | **1195** 条（**0 失败 / 0 错误 / 0 跳过**）· 来自 **193** 个 `TEST-*.xml`（构建收工后求和） |
| **逐模块** | `user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · **`follow-api` 136** · `follow-rpc` 86 · `follow-common` 37 · `common` 337 |
| 🆕 **第二轮（同一批代码）** | **EXIT=0 · `BUILD SUCCESS` · 10:01 min · 同样 **1195** 条 / 0F / 0E / 0S · **193** 个 XML** ⇒ 与第一轮**逐模块完全一致**（`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · `follow-api` **136** · `follow-rpc` 86 · `follow-common` 37 · `common` 337）⇒ ⇒ **为什么跑第二轮**：第一轮之后我又改了两个测试文件（`admin-api` 的注释订正 + `follow-api` 的断言消息订正）⇒ ⇒ **让台账里的数字对应「包含全部当前代码」的那一轮**，不拿旧轮的数字冒充 |
| ⚠️ **必须带 `clean`** | 否则 stale 的 `target/` 会同时伪造「当前的红」与「当前的条数」⇒ 本轮**两轮都带 `clean`** |

> ⚠️ **与基线的差异**：基线（`docs/PhaseGoal-judge.md` §十二）记全仓 52 模块 **2860 条 / 0F / 0E / 4S**；
> 本轮是 **G1 波次的 9 个有测试的模块**（+ `common` 依赖）⇒ **1195 条**，**不是**全仓数，**不可与 2860 直接比**。
> ⚠️ **本轮新增**：`follow-rpc` 的 86 条里含**本轮新增的 3 条**（`FollowProtoContractTest`）⇒ 该模块**修前 83**；
> 🆕 **`follow-api` 的 136 条里含本轮新增的 62 条**（`FollowApiFailurePathMatrixTest`）⇒ 该模块**修前 74** ⇒ ⇒ **两条增量相加 = 1133 → 1195（+65）** ⇒ **既有 1133 条一条没退化**。

### 5.2 定向复验（本轮两处改动）

```bash
mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false \
    -Dtest='FollowApiRedisDisconnected*,FollowProtoContractTest' \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -pl service/follow/follow-api,service/follow/follow-rpc -am test
```

| 目标 | 结果 |
|---|---|
| `FollowApiRedisDisconnectedFastFailTest` | **2/2 绿**（修前：**整类 error** ⇒ `Can't get Docker image: testcontainers/ryuk:0.14.0`）|
| `FollowApiRedisDisconnectedDefaultBehaviorTest` | **2/2 绿**（同上）|
| `FollowProtoContractTest`（🆕 本轮新增） | **3/3 绿**（0.107 s）⇒ ① 契约逐条一致 ② 嵌套 message 单独成行 ③ 变异自检 |
| 日志中 ryuk 拉取记录 | **0 条**（⇒ 补上 env 后不再拉镜像）|

### 5.2.1 🆕 失败路径矩阵复验（**真 Tomcat** · 本轮新增 `T11`）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow
mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest='FollowApiFailurePathMatrixTest' -pl service/follow/follow-api -am test
```

| 项 | 值 |
|---|---|
| **退出码 / 结果** | **0** · `BUILD SUCCESS` · `follow-api` **Tests run: 62, Failures: 0, Errors: 0, Skipped: 0** · 5.471 s |
| **覆盖** | **8 端点 × 4 类失败**（认证 / 权限 / 参数校验 / 业务规则）＋ 基础设施类（rpc 异常映射）＋ 7 条单点（404/405 字节级 · 拦截器顺序 · Redis fail-open · 端口段自检）|
| **容器** | **真 Tomcat**（`DEFINED_PORT` + `RealTomcatPort.pick()` ⇒ 落在 **22340–22359**）⇒ **follow-api 的真容器覆盖率从 0 变成 62 条** |
| ⚠️ **首轮曾 12 条红** | **两条都是判据自身的错，不是产品缺陷**：① `assertEnvelope` 对**成功**信封也要求 `data:null`（**11 条**假红）② 404 的 `Content-Length` 被写死成 **20**（实测 **19**，**1 条**假红）⇒ ⇒ **改判据**，不是改产品（AGENTS.md §3.3 规则 ③：先改/删错误断言，再看实现）|

⇒ 逐条矩阵与 Go 出处 ⇒ [`docs/DRIFT/follow/vs-common.md` §13](../follow/vs-common.md) ＋ [`docs/DRIFT/follow/stage1-admission.md` §4.2](../follow/stage1-admission.md)。

### 5.3 收口验收命令（`docs/PhaseGoal/g1.md` §5 逐条实跑）

> ⚠️ **两个必须先知道的坑**（本轮实测踩到，**都是共享工具的问题 ⇒ 已上报**）：
> ① **`GO_REPO` 默认值按「仓库同级目录」推导** ⇒ 在 **worktree**（`RAG.worktrees/<lane>`）里会算成
> `RAG.worktrees/Sea-RideTheWind_golang` ⇒ **找不到 Go 真源** ⇒ `follow` 域**假红**（EXIT=1，报「Go routes.go 不存在」）。
> ⇒ **正确用法**：`GO_REPO="/Users/pr/Documents/java&golang/Sea-RideTheWind_golang" python3 tools/difftest/check-surface-parity.py --domain user --domain admin --domain follow`
> ② **`--domain` 的域名是 `user` / `admin`，不是 `users`** ⇒ `--domain users` **匹配不到任何域** ⇒ 输出
> 「HTTP 端点 Go=0 / RAG=0」却 **EXIT=0** ⇒ **空跑即通过**（⚠️ `docs/PhaseGoal/g1.md` §5 写的就是这条错名命令）。

| # | 命令 | EXIT | 实测结果 |
|---|---|---|---|
| 1 | `check-surface-parity.py --domain user` | **0** | HTTP 端点 **Go=9 ≡ RAG=9** · gRPC 方法 **Go=9 ≡ RAG=9**（已签字偏离域单列，不计失败）|
| 2 | `check-surface-parity.py --domain admin` | **0** | HTTP **13 ≡ 13** · gRPC **13 ≡ 13**（同上）|
| 3 | `check-surface-parity.py --domain follow` | **0** | HTTP **8 ≡ 8** · gRPC **8 ≡ 8** |
| 4 | `check-ddl-static.py --domain users --indexes` | **1** | **16 处实质差异**，逐项：**类型/长度 0** · 可空性 **1**（`users_user.status` go=YES/rag=NO）· DEFAULT **8**（`DEFAULT CURRENT_TIMESTAMP`）· 索引 **7** ⇒ **全部对得上 AGENTS.md §0 第 2 条**（DEFAULT 残留**刻意保留** · status 可空性**未修** · 索引**性能非行为**）⇒ **无新增漂移** |
| 5 | `check-ddl-static.py --domain follow --indexes` | **0** | **0 处实质差异**（类型/长度/可空性/DEFAULT/索引/约束/触发器全比）|
| 6 | `check-proto-static.sh` | **0** | `user`(9 RPC) · `admin`(13 RPC) · `task`(1 RPC) **逐字节一致** ⇒ ⚠️ **只覆盖 3 域 ⇒ follow 不在内**（基线 §九 `G-6`）⇒ **本轮由 🆕 `FollowProtoContractTest` 补上 follow 的字段级断言**（更严：比对的是**生成码描述符**而非文本 diff）|
| 7 | `check-grpc-interceptor-wiring.py` | **0** | 全部 rpc 进程「拦截器既被 `@Import` 产出，也被 `Configurer` 挂到 server」|
| 8 | `check-tomcat-limits.py` | **0** | **全部 10 个 api**（含 `follow-api`）三个旋钮 ✓ |
| 9 | `check-flyway-ledger.py` | **0** | 每个 (库, schema) 内账本表名两两不同 ⇒ 无撞号（7 模块独立账本，`follow-rpc` = `flyway_schema_history_follow`）|
| 10 | `check-wave-lane-ownership.py` | **0** | G1~G4 写权两两不重叠 · 13 个共享文件无人拥有 · 共享层只归 G4 |
| 11 | `check-ledger-tables.py` | **0** | 台账表格结构完好 |
| 12 | `check-ledger-selfcheck.py` | **0** | 层 0~4 全部通过 |
| 13 | `check-ledger-count.py` | **0** | 索引条目计数与仓库现状一致 |
| 14 | `check-doc-links.py` | **1** | 🔴 **1 项锚点失效（既存，非本轮引入）**：`docs/PhaseGoal-judge.md:99` 自指锚点 `#三矩阵-a终点指标与当前判定39-行` ⇒ **主工作区同样 FAIL** ⇒ **属基线文件 ⇒ 不在 G1 写权面 ⇒ 上报收口** |
| 15 | `check-agents-budget.py` | **0** | `AGENTS.md` 32,721 B / 279 行 ⇒ 在预算内（余量 47 B）|

### 5.4 ⛔ 本轮**明确未跑**的命令（诚实边界）

| 命令 | 为什么没跑 | 它的结论**归谁** |
|---|---|---|
| `bash tools/difftest/run.sh`（users 双侧活体 HTTP wire 对拍） | 需 **Go 栈 + RAG 栈双栈编排**（起 etcd / PG / Redis + 两个 Go 二进制 + 两个 RAG jar）⇒ 本轮未起 | **沿用台账既有记录**（2026-10-24/27 的 52/52 等）⇒ **本轮未复验** ⇒ 证据强度**不提升** |
| `python3 tools/difftest/matrix.py`（users DTO 字段笛卡尔） | 同上（需双栈在跑）⇒ 未跑 | 同上 |
| `bash tools/difftest/exp-difftest-grpc-user9.sh` | 同上 | 同上 |
| `bash tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up` | 同上 | 同上 |
| `bash tools/difftest/exp-difftest-redis-blacklist.sh` | 同上 | 同上 |
| `bash tools/difftest/check-jar-executable.sh` | 需**先 `mvn package`**（本轮只跑了 `test` ⇒ 无 fat jar）⇒ 未跑 ⇒ ⚠️ 基线 §九 `G-10` 记的「未构建 ⇒ 跳过且不计失败」缺口**对本轮同样成立** | — |

⇒ ⚠️ **口径**：以上命令的结论**一律按台账原有日期与证据强度读**，**不得因本轮回归全绿而说成「本轮已复验」**。

---

## 6. 阶段 1F：独立验收（§5.6）+ 阶段归属（§10）

### 6.0 验收结论：**🟡 有条件通过**（域级）

> **§5.6 的四种结论里选「有条件通过，但必须明确有限例外及其影响」** ⇒ 例外逐条列在 §6.2。
> ⚠️ **不是「通过」** —— 因为存在 **A 类阻断项**（`S1-USERS-001`，**项目级**）：**「实际项目说明」不存在**
> ⇒ 外部无法按说明复现启动。⚠️ **也不是「未通过」** —— 因为该阻断项**不在本域写权面**
> （根级 `README.md` 无人拥有）⇒ **本域能做的都做完了**，剩下的是**移交**而不是**本域未完成**。

| §5.6 检查项 | 结论 | 依据 |
|---|---|---|
| P0 准入条件是否满足 | ✅ **域内 8/9 满足**（#1/#2/#3/#4/#6/#7/#8/#9）· 🔴 **#5 项目级不成立** | §1 的逐条判定 + §5.1~§5.3 的证据 |
| P1 是否至少有两到三个代表性机制完成验证闭环 | ✅ **有**（RPC 超时/deadline · 事务与并发写入 · 鉴权与黑名单 fail-open · 错误码契约 · proto wire 契约） | 各域台账的 L3/L4 条目 + 本轮 `FollowProtoContractTest`（**本轮新增第 5 个闭环**）|
| 核心业务是否能够实际运行 | 🟡 **域内能**（4 组件可构建可起）· **项目级按说明不能**（说明不存在） | `S1-USERS-001` |
| 测试是否真实断言业务结果 | ✅ **是**（1133 条 · 0F/0E/0S · **含变异实测**） | §5.1 + §5.2 + 域台账变异章节 |
| 关键失败路径是否具有明确行为 | ✅ **是**（401/400/413/503/Redis 断连与超时逐条有断言） | §1 #4 + 域台账 |
| 是否存在严重安全或数据正确性问题 | 🟡 **1 条**（空签名密钥可起栈 ⇒ **P0#8**）—— **域外**（根级 compose 缺 `:?`）+ 域内已收窄（口令默认值统一） | `S1-USERS-002` + 基线 §11.1 `D-01` |
| 项目是否能够按实际步骤构建、配置和启动 | 🔴 **否（项目级）** | `S1-USERS-001` |
| 所有已知缺陷和关键风险是否已记录 | ✅ **是**（§3 差异清单 + §6.1 上报 + 域台账 + vs-common 9 轴） | §3 + §6 |
| 暂缓项是否具备明确的阶段 2 处理方案 | ✅ **是**（每条都有第 9/10 项） | §3 |

### 6.1 需**域外**处理的上报项（不在 G1 写权面 ⇒ 交合并收口执行者）

| 编号 | 事项 | 为什么必须域外 | 影响 |
|---|---|---|---|
| `S1-USERS-001` | 根 `README.md` 补**最小可复现说明书** | 根文件无人拥有（G1/G2/G3/G4 白名单都不含） | 🔴 **A 类 · 阻断阶段 1** |
| `S1-USERS-001` | `Infrastructure.md` 与事实对齐（或标注「历史文档，已废」+ 指路 README） | 同上 | 🔴 **A 类** |
| `S1-USERS-003` | `.gitignore` 加 `.env` + 提交 `.env.example` | 同上 | 🟡 B 类 · P0#8 强命中 |
| `S1-USERS-002` | 取消 9 处 datasource 口令 fallback（与 Go 的 fail-fast 对齐） | 跨 8 个域 ⇒ 需收口统一改 | 🟡 B 类 |
| 🆕 `S1-USERS-006` | **`check-doc-links.py` 有 1 项既存锚点失效** —— `docs/PhaseGoal-judge.md:99` **自指**锚点 `#三矩阵-a终点指标与当前判定39-行` 与 §三 的实际标题不符 ⇒ **主工作区同样 FAIL** ⇒ **非本轮引入** | `docs/PhaseGoal-judge.md` 属**基线文件**（非 G1 写权面）⇒ 交收口 | 🟡 **B 类**（文档链接面；⚠️ **只影响锚点跳转，不影响任何判定**）|
| 🆕 `S1-USERS-007` | **`docs/PhaseGoal-judge.md` 的 A-2 `follow-api` 行 + 机器生成的 `docs/PhaseGoal/g1.md` 仍把已签字偏离记成开放缺陷** ⇒ 照它执行会让 G4 去改掉用户已决定保留的行为 | 基线文件 + 机器生成物 ⇒ 交收口订正 + 重跑 `check-phasegoal-lanes.py --write` | 🔴 **A 类风险**（派单错误 ⇒ 共享层被改坏）· 详见 follow 档 `S1-FOLLOW-007` |
| 🆕 `S1-USERS-008` | **5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED`**（`article-api`/`comment-api`/`hot-api` ⇒ G3；`like-api`/`task-api` ⇒ G2）⇒ 在 Docker Hub 不可达的环境里**整类 error** | 各归 G2 / G3 ⇒ G1 只登记上报（本域 `follow-api` 已自修） | 🟡 **B 类**（测试基础设施）· 详见 follow 档 `S1-FOLLOW-009` |
| 🆕 `S1-USERS-009` | **404 body 的字节数在两处注释里写成「20」，实测是 19** —— ① 共享层 `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83`（⇒ **G4**）② like 域 `service/like/like-api/.../LikeApiRouterErrorRealTomcatTest.java:46` 与 `:187`（⇒ **G2**）。⚠️ **两处的断言/常量本身都对**（like 那条按**实际长度**比）⇒ **只影响注释**，但**已经产生过一次实际误判**（本轮新写的矩阵测试照抄「20」⇒ 当场红 ⇒ 才发现） | 共享层 ⇒ **G4**；like 域 ⇒ **G2** ⇒ 均**不在 G1 写权面** ⇒ G1 只登记上报 | 🟡 **P3 文档面**（**不影响行为**）· 详见 follow 档 `S1-FOLLOW-010` / `S1-FOLLOW-011` |

### 6.2 「有条件通过」的**有限例外**逐条（§5.6 要求：必须明确例外及其影响）

| # | 例外 | 范围 | 影响 | 归属 |
|---|---|---|---|---|
| **E1** | **项目说明缺失**（`README.md` 7 字节 · `Infrastructure.md` 与事实全面矛盾 · compose 要 `.env` 而仓内无模板） | **项目级**（全 10 域） | **外部无法按说明复现启动** ⇒ 阶段 1「核心业务链路能真实运行」**只能靠内部台账自证**，不能被外部复现 | **A 类** · 收口 |
| **E2** | **空签名密钥可起栈**（10 处 yaml 带明文 JWT secret 默认值 + 根级 compose 缺 `:?`） | **跨域** | token 可伪造 ⇒ P0#8 强命中 | **B 类**（基线 `D-01`）· 收口（根级）＋ 各域（yaml） |
| **E3** | **`follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口** | follow 域 | 可观察性缺一环 ⇒ **`follow-rpc` = 🟡** ⇒ **G1 lane 判定 = 🟡** | **C 类**（阶段 2 建设项）· ⚠️ **用户 2026-10-24 已定「本轮不落地」⇒ 本轮登记待重新拍板** |
| **E4** | **双侧活体对拍在本轮未复验**（§5.4 六条命令需双栈编排 ⇒ 未跑） | users + follow | 台账既有 L3/L4 结论**沿用原日期** ⇒ 证据强度**不提升**、**不降** | **B 类**（补跑手段）· 可解除 |
| **E5** | **`follow` 域 0 个落仓双侧对拍入口**（探针仍在 `/tmp` ⇒ 已不可复算）· 基线 §九 `G-1` | follow 域 | `follow-api`/`follow-rpc` 的 **R4 不成立**（已按 R3 计入） | **B 类**（非阻断）· 收口或域内 |
| **E6** | **proto「消费方式」未取证**（`optional` presence 的**构造点**语义）· 基线 §九 `G-19` | 全 10 proto | **取证缺口 ⇒ 不降 R**（§1.1 ⑥）| **B 类** · 各域 |
| 🆕 **E7** | ~~**follow 域无独立失败路径矩阵**~~ · 基线 §九 `G-13` | follow 域 | ✅ **本轮闭合（`T11`）** —— 8 端点 × 4 类失败 × **真 Tomcat** = **62 条**全绿（§4.6 / §5.2.1）；⚠️ **P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因此判成 ✅** | ✅ **本轮闭合** |

### 6.3 问题分类与阶段归属汇总（§10：P0–P3 定严重程度 · A–D 定阶段归属）

| 编号 | 问题类型 | 严重程度 | **阶段归属** | 状态 |
|---|---|---|---|---|
| `S1-USERS-001` | 项目说明缺失 | **P0** | **A**（阶段 1 准入阻断） | 🔴 OPEN · **域外上报** |
| `S1-USERS-002` | datasource 口令 fallback | P2 | **B**（竞争力补强） | 🟡 域内 2 行已改 · 全仓取消 fallback = 跨域 ⇒ 上报 |
| `S1-USERS-003` | `.env` 未进 `.gitignore` | **P1** | **B** | 🔴 OPEN · 域外上报 |
| `S1-USERS-004` | 头像事务依赖跨 Bean 调用（无报错保护） | P2 | **B** | 🟡 登记（当前实现正确，缺防退化） |
| `S1-USERS-005` | `follow-rpc` 端口硬编码 | P2 | **D**（已实现） | ✅ 已修（`${FOLLOW_RPC_PORT:8082}`）|
| `S1-FOLLOW-001` | rpc 层指标无出口 | P1 | **C**（阶段 2 建设项） | 🟡 用户 2026-10-24 定不落地 ⇒ **待重新拍板** |
| `S1-FOLLOW-002` | MaxConns 闸门粒度 | —— | **D**（已签字偏离 ⇒ 可不实现） | ✅ **已销号**（`SD-2026-10-25-01`）|
| `S1-FOLLOW-003` | 纯 gRPC 进程关闭路径抛 `NoClassDefFoundError` | P2 | **B** | 🔴 OPEN · **派 G4** |
| `S1-FOLLOW-004` | follow 域无 compose 编排 | P2 | **B** | 🔴 OPEN · 域外上报 |
| `S1-FOLLOW-007` | 基线仍把已签字偏离记成开放缺陷 | **P1** | **A**（派单错误会改坏共享层） | 🔴 OPEN · 上报收口 |
| `S1-FOLLOW-008` | `check-surface-parity.py` 两个静默假绿口子 | P2 | **B** | 🔴 OPEN · 上报（共享工具）|
| `S1-FOLLOW-009` | 5 个 api 模块缺 ryuk-disabled | P2 | **B** | 🔴 OPEN · 上报 G2/G3 |
| `S1-FOLLOW-010` | 共享层 404 body 字节数注释写成「20」（实测 19） | **P3** | **B**（文档面） | 🔴 OPEN · 上报 G4 |
| `S1-FOLLOW-011` | like 域真 Tomcat 测试沿用同一错数（「20」） | **P3** | **B**（文档面） | 🔴 OPEN · 上报 G2 |
| 🆕 `S1-FOLLOW-012` | **follow 域无独立失败路径矩阵** · 基线 §九 `G-13` | **P1**（P0#2 的证据面） | **D**（已实现） | ✅ **本轮闭合**（`T11` · 8 端点 × 4 类失败 × **真 Tomcat** = **62 条**全绿）⇒ ⚠️ **P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因此判成 ✅** |
| 🆕 `S1-FOLLOW-013` | **follow 域本轮新增文件自己的断言消息写着「全长 20 字节」**（`FollowApiFailurePathMatrixTest.java:417`）⇒ **躲过了 G1 第一轮自查**（模式选错 + `head -40` 截断）⇒ 已二次订正并重扫 | **P3**（仅文案） | **D**（已实现） | ✅ **本轮自修**（矩阵 62/62 绿）|
| 🆕 `S1-USERS-010` | **`admin-api` 的注释把 503 body 的字节数写成 15，断言用的是 16** —— `AdminApiRealTomcatResponseBytesTest.java:266` 写「线上是 Content-Length: **15**」，而紧邻的断言用 `GO_TIMEOUT_CONTENT_LENGTH = "Request Timeout".length() + 1` = **16** ⇒ 注释**漏算了 `fmt.Fprintln` 补的那个 `\n`** ⇒ 与 follow 域的「20 vs 19」**同族**（字节数少算尾随换行） | **P3**（**仅注释**，断言本身正确 ⇒ 一直绿） | **D**（已实现） | ✅ **本轮自修并复跑**（`AdminApiRealTomcatResponseBytesTest` **3/3 绿**）|

---

## 7. 冻结基线（§5.7）

| §5.7 要求 | 本轮的值 |
|---|---|
| **实际代码状态及版本标识** | 分支 `align/g1-users-follow` · 基线 `29a0bce`（= `do-my-own-gh`）· **首轮提交 `e18ef72`（38 文件 +1660/−70）** ＋ **🆕 本轮增量（`T11`）= 2 个新增源文件（`RealTomcatPort` ＋ `FollowApiFailurePathMatrixTest`）+ 1 个修改文件（`BlindPortGateTest`）+ 5 份台账** ⇒ **提交后以 commit hash 为准**（见 §7 末） |
| **构建和测试结果** | **域级回归**：`mvn … clean test` ⇒ **EXIT=0 · BUILD SUCCESS · 08:56 min（第二轮 10:01 min · 同一批代码 · 逐模块一致）· 1195 条 / 0F / 0E / 0S**（§5.1 · 🆕 较上一轮 **+62** ＝ 失败路径矩阵）· **定向复验 7 条全绿**（§5.2）＋ 🆕 **矩阵复验 62 条全绿**（§5.2.1）· **收口验收命令 15 条**：13 条 EXIT=0、1 条 EXIT=1（**既存**锚点失效）、1 条 EXIT=1（**users DDL 16 处已知差异**，非缺陷） |
| **核心业务链路的验收结果** | `user-api → user-rpc → PG/Redis` 与 `admin-api → admin-rpc → PG/Redis` 两条链路的**域内实测**沿用域台账（`user/rpc.md` §6.4 / `admin/rpc.md` §15，L3/L4）⇒ **本轮未起真栈复跑**（§5.4）⇒ **按原日期读** |
| **P0 / P1 / P2 最终状态** | **P0**：域内 ✅ 8/9 · 项目级 🔴 1 项（`S1-USERS-001`）· **P1**：✅ 至少 5 个机制闭环（RPC 超时 / 事务并发 / 鉴权与黑名单 / 错误码契约 / proto wire 契约）· **P2**：6 项登记（其中 1 项已销号、1 项已修） |
| **全部已知缺陷和未验证风险** | §3 差异清单（`S1-USERS-001~005` + `S1-FOLLOW-001~009`）＋ §6.1 的 `S1-USERS-009` + §6.2 六条例外 + 域台账 + [`vs-common.md`](vs-common.md) 9 轴 + 基线 §九 20 条缺口（**G1 相关：G-1 / G-4 / G-6 / G-8 / ~~G-13~~ / G-19** ⇒ 🆕 **`G-13` 在 follow 域已闭合**，其余 3 域归 G2 / G3） |
| **完整的差异与暂缓项清单** | 本文件 §3（users 5 条 + follow 9 条，**每条 11 项齐全**） |
| **阶段 2 任务及其依赖** | ① `S1-FOLLOW-001` 指标出口方案（**待用户重新拍板**，依赖：全仓 rpc 统一方案）② `S1-USERS-004` 事务边界上移（依赖：阶段 1 不动已验证边界）③ `S1-USERS-001` 说明书（依赖：**收口**）④ `S1-FOLLOW-007/008/009`（依赖：**收口 / G2 / G3**）⑤ 🆕 基线 `G-13` 的其余 **3 域**（`article` / `task` / `hot`）补失败路径矩阵（依赖：**G3 / G2**，本轮已把 follow 域那一份做完，**矩阵形态可直接复用**）⑥ 🆕 `S1-FOLLOW-010` / `S1-FOLLOW-011` 两处错数注释订正（依赖：**G4 / G2**） |
| **尚未处理的原始业务行为** | ① `follow-rpc` 的 `/metrics` 出口（Go DevServer 有 · RAG 无）② MaxConns 的 per-route 语义（**用户已签字不修** ⇒ 不是「尚未处理」，是「决定不处理」）③ Go 侧 `os.ExpandEnv` 的 fail-fast 连库语义（RAG 仍保留 fallback） |
| **下一次执行应从哪里开始** | ① **收口**：`S1-USERS-001` 说明书 + 基线 `S1-FOLLOW-007` 订正（**A 类，阻断阶段 1**）② **G4**：收 `S1-FOLLOW-003` + `DRIFT-USERAPI-013` 的 ②③ 半边 ＋ `S1-FOLLOW-010` ③ **G2/G3**：5 个 api 模块补 ryuk-disabled ＋ `S1-FOLLOW-011` ＋ **基线 `G-13` 的其余 3 域矩阵**（`article` / `hot` ⇒ G3；`task` ⇒ G2 ⇒ **形态可直接抄 follow 这份**）④ **G1 自身**：若用户改主意重开 `S1-FOLLOW-001` ⇒ 则 `follow-rpc` 需给 rpc 组件开独立 management 端口 ⇒ **那是全仓方案，不是 G1 单方面能定的** |

---

## 8. 证据强度声明

| 结论 | 证据强度 | 说明 |
|---|---|---|
| P0#1 / #2 / #3 / #4 / #6 / #7 判定 | **L4**（活体） | 真 PG / 真 Redis / 真 Nacos / 真进程 / 真 Tomcat；Go↔RAG 逐字对照 + 双侧对拍<br>⚠️ **本轮未复跑双侧活体**（§5.4 六条命令需双栈编排）⇒ **沿用台账原日期**，**不提升** |
| P0#5 域内判定 | **L4** | 2026-10-27 真启动（6.4s / 9.7s）· **本轮未复跑真启动**（§5.4）⇒ 按原日期读 |
| P0#5 项目级判定 | **L2**（静态可证） | README 7 字节 / 路径实测不存在 / compose `:?` 强制 ⇒ 静态即可证「说明不存在」 |
| P0#8 判定 | **L2** | 9 处默认值逐文件枚举 + go-zero `os.ExpandEnv` 语义（源码级）|
| **本轮新增：域级回归全绿** | **L3**（测试证据 · **1195** 条 / 0F / 0E / 0S） | ⚠️ **这是「测试证据」，不是「运行面等价证据」** —— 全绿只证明「已写的断言都成立」⇒ **不等于**「行为与 Go 等价」 |
| **本轮新增：`follow-proto` 契约回归网** | **L3**（测试证据 · 3/3 绿 · 含变异自检） | 期望值**由 Go 真源生成**（记录 sha256）⇒ 比对的是**生成码描述符** ⇒ 证的是**编译产物**的契约；⚠️ **不覆盖 wire 字节级、不覆盖「消费方式」** |
| **本轮新增：端口 env 覆盖** | **L1**（静态可证） | 四处默认端口**逐字不变** + 「无独立 `discovery.port` ⇒ 注册端口随 `grpc.server.port` 走」⇒ **注册/监听不会脱钩**；⚠️ **「改 env 后真启动 + 注册地址一致」未做行为级实测** |
| **本轮新增：ryuk env 补齐** | **L3**（测试证据） | 原报错的两个类**真跑起来**（2+2 绿）⇒ **不是跳过、不是 0 断言**；且日志无 ryuk 拉取 |
| **本轮新增：follow 域失败路径矩阵（`T11`）** | **L3**（测试证据 · **62 条 / 0F / 0E / 0S** · **真 Tomcat**） | 期望值**逐条抄自 Go 源码**（带 `文件:行号`）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥；⚠️ **不按 R4**（R4 要双侧活体 + 变异）⇒ **不得**据此宣称「与 Go 逐字节一致」；⚠️ **P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因此判成 ✅** |
| 本文件全部「一致」类断言 | 按各域台账的「证据强度」列 | **不是已闭环**（AGENTS.md §0） |

---

## 9. 复现清单（可复算）

| 编号 | 命令 | 期望 |
|---|---|---|
| R1 | `grep -rnE 'TODO\|FIXME\|UnsupportedOperationException' service/users/user/*/src/main service/users/admin/*/src/main --include='*.java'` | **0 命中**（P0#1 无骨架） |
| R2 | `find service -path '*/src/main/resources/application.y*ml' -not -path '*/target/*' -exec grep -hoE '\$\{POSTGRES_PASSWORD:[^}]*\}' {} + \| sort \| uniq -c` | **只有 1 种**（T2 后实测：`10 ${POSTGRES_PASSWORD:password}`，覆盖 9 个模块）<br>⚠️ **不要**写成 `grep -rhoE … \| grep -v target` —— `-o` 输出**不带文件名** ⇒ `grep -v target` **滤不掉** `target/` 下的旧副本（2026-10-28 实测：漏滤时读出 `2×Sea-TryGo` 的**假分裂**） |
| R3 | `grep -n 'port:' service/follow/follow-rpc/src/main/resources/application.yml` | `${FOLLOW_RPC_PORT:8082}`（T3 后） |
| R4 | `wc -c README.md` | **7**（`S1-USERS-001` 的证据） |
| R5 | `grep -nE '\.env' .gitignore \| wc -l` | **0**（`S1-USERS-003` 的证据） |
| R6 | `git ls-files \| grep -c '^\.env'` | **0** |
| R7 | `python3 tools/difftest/check-wave-lane-ownership.py` | `EXIT=0` |
| **R8** | `grep -n 'port:' service/users/user/user-api/src/main/resources/application.yml service/users/admin/admin-api/src/main/resources/application.yml service/users/user/user-rpc/src/main/resources/application.yaml service/users/admin/admin-rpc/src/main/resources/application.yml` | 四个默认值**逐字不变**：`${USER_API_PORT:49900}` · `${ADMIN_API_PORT:8889}` · `${USER_RPC_PORT:49210}` · `${ADMIN_RPC_PORT:8081}`（T8）|
| **R9** | `grep -c TESTCONTAINERS_RYUK_DISABLED service/follow/follow-api/pom.xml` | **1**（T9 ⇒ `follow-api` 不再去拉 ryuk 镜像）|
| **R10** | `python3 service/follow/follow-rpc/src/test/resources/proto-contract/generate.py "<Go 真源>/proto/follow.proto" /tmp/follow-expected.txt "<Go 真源>"` ⇒ `diff /tmp/follow-expected.txt service/follow/follow-rpc/src/test/resources/proto-contract/follow-proto-contract.expected.txt` | **0 行差异** ⇒ 基准文件与 Go 真源**同步**（真源变了 ⇒ diff 立刻暴露）|
| **R11** | `GO_REPO="<Go 真源绝对路径>" python3 tools/difftest/check-surface-parity.py --domain user --domain admin --domain follow` | `EXIT=0` ⇒ HTTP **9≡9 / 13≡13 / 8≡8** · gRPC **9≡9 / 13≡13 / 8≡8**<br>⚠️ **必须设 `GO_REPO`**（worktree 下默认值算错 ⇒ 假红）· ⚠️ **域名是 `user`/`admin`**，写 `users` ⇒ **0/0 假绿** |
| **R12** | `python3 - <<'EOF'` 逐个 `TEST-*.xml` 的 `testsuite` 属性求和（**构建收工后**） | **1195** 条 / **0** 失败 / **0** 错误 / **0** 跳过（193 个 XML）<br>逐模块：`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · **`follow-api` 136** · `follow-rpc` 86 · `follow-common` 37 · `common` 337<br>⚠️ **不要数日志**（`-am` 会先构建依赖模块 ⇒ 按日志分段聚合会算错）· ⚠️ **不要在构建中途采**（拿到的是半成品）|
| **R13** | `mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest='FollowApiFailurePathMatrixTest' -pl service/follow/follow-api -am test` | `EXIT=0` · **Tests run: 62, Failures: 0, Errors: 0, Skipped: 0** ⇒ **8 端点 × 4 类失败**的矩阵全绿（§4.6 / §5.2.1）|
| **R14** | `grep -n 'public static final int FIRST\|public static final int LAST' service/follow/follow-api/src/test/java/com/rag/follow/follow/api/support/RealTomcatPort.java` | **22340 / 22359** ⇒ 落在 follow 域预分配段 **22300–22399** 内、且**不与** `follow-api` 的 `RealGrpcPort`（22300–22319）/ `follow-rpc` 的（22320–22339）重叠 |
| **R15** | `printf '404 page not found\n' \| wc -c` | **19**（**不是 20**）⇒ `S1-FOLLOW-010` / `S1-FOLLOW-011` / `S1-USERS-009` 的**错数来源** |
