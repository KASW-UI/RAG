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
| **5** | 能按**实际项目说明**完成构建 / 配置 / 启动 | **域内**：4 个组件可构建、可用 fat jar 起（2026-10-27 真启动 ✅ 6.4s/9.7s）。**项目级**：🟡 **2026-10-29 订正 —— 本行原记的三条事实（「`README.md` **只有 7 字节** ⇒『实际项目说明』不存在」/「compose 强制要求 `.env` 而**仓内无 `.env`、无 `.env.example`**」/「`Infrastructure.md` 描述的路径**均已不存在**」）已被 **2026-10-29 R4 轮推翻** ⇒ 现：`README.md` = **9815 字节**的完整说明书（含「5 分钟跑起来」/ 环境变量表 / 启动顺序 / 验证命令）· `.env.example` **已入库**（6626 字节）· `.gitignore` **实有 3 条 `.env` 规则**（`.env` / `.env.*` / `!.env.example`）。**残留**：根 `Infrastructure.md` §1 对照表与 §6 变量表仍引用**不存在的 `api-gateway/`**（仓内 10 个域里无该模块）与**旧库名 `sea_user` / 旧路径 `service/user-rpc/`**（实际 = `first_db` / `admin` / `service/users/user/user-rpc/`）⇒ 照它操作仍会走错（但**入口已是 README** ⇒ 不再阻断） | 🟡 **部分成立**（域内 ✅ · 项目级**说明已存在**，**残留文档 1 处待订正**） | ✅ **L1 静态可证**（`wc -c README.md` = 9815 · `.gitignore` 3 条规则 · `.env.example` 已入库）· ⚠️ 残留项**无独立守门** | 见 §3 `S1-USERS-001`（逐条实测清单 · 2026-10-29 订正块） | 🟡 **不再阻断**（原 🔴 A 类 ⇒ 说明已补，「按说明复现」不再无路可走） | 🔴 **域外**（残留落在根 `Infrastructure.md` ⇒ **不在 G1 写权面**）⇒ **只登记 + 上报** | 🟡 **残留部分可暂缓**（README 已能独立复现；残留项不影响启动路径） |
| **6** | 核心业务有可执行自动化测试，且断言真实业务结果 | 测试文件 108 份（31+27+27+23）；**4 个变异实测变红** ⇒ 非假绿 | ✅ 正确 | ✅ 已执行 | [`user/rpc.md`](user/rpc.md) §6.4 变异表 · [`_shared.md`](_shared.md) 变异实测节 | ❌ 不影响 | ❌ 不需要 | — |
| **7** | 模块职责清晰，核心调用链可追踪 | 4 组件职责边界清晰；台账有**逐文件对照索引** | ✅ | ✅ | [`user/api.md`](user/api.md) §5 · [`admin/rpc.md`](admin/rpc.md) §7 | ❌ 不影响 | ❌ 不需要 | — |
| **8** | 关键配置和敏感信息无明显不安全处理 | 9 个 rpc 模块的 datasource 口令默认值**分裂成 2 种**（7×`password` + **2×`Sea-TryGo`**，两处都在本域）；JWT secret 的 fallback 与 Go 侧硬编码值**逐字一致**且可 env 覆盖 | 🟡 **部分不成立** | ✅ 静态可证 | 见 §3 `S1-USERS-002` | 🟡 **域内那 2 处可自修**；域外另有 `.gitignore` 未排除 `.env`（`S1-USERS-003`） | ✅ **域内修 2 行**（§4 任务 T2） | 🟡 域外部分登记 |
| **9** | 已知严重缺陷 / 无法验证的关键能力 / 运行前提明确记录 | 三档台账（DRIFT / Intentional_Deviations / Pitfalls）+ `vs-common.md` 齐备；本轮新增登记见 §3 | ✅ | ✅ | [`_shared.md`](_shared.md) · [`vs-common.md`](vs-common.md) · [`../../Pitfalls/users/_shared.md`](../../Pitfalls/users/_shared.md) | ❌ 不影响 | ❌ 不需要 | — |

### 1.1 P0 判定小结

- 🟡 **2026-10-29 订正**：P0#5 原是本域唯一的 A 类阻断项（**项目级**，不在 G1 写权面）—— **该阻断已解除**（README / `.env.example` / `.gitignore` 三条已被 2026-10-29 R4 轮闭合 ⇒ §1 P0#5 行由 🔴 降为 🟡「部分成立」）⇒ **残留** = 根 `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处（B 类 · 域外上报）⇒ 见 §3 `S1-USERS-001` 与 §6 上报项。
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

### `S1-USERS-001` 🟡 **A 类（部分闭合）** · 项目说明缺失 ⇒ P0#5 不成立 —— **2026-10-29 订正：三条陈旧事实已被 R4 轮推翻，残留 1 处文档订正**

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-001` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧无「说明文档」这一契约；**RAG 侧的对应义务来自 `twoStage.md` P0#5**（「项目能够按照**实际项目说明**完成构建、配置和启动」）· 契约落点 = 根 `README.md` |
| 3 | 原始行为 | Go 真源仓的 `README.md` 描述了完整的构建/配置/启动流程（`Sea-RideTheWind_golang/README.md`） |
| 4 | 阶段 1 当前行为 | 🔴 **2026-10-29 订正（本行原值保留于左）**：原记「`README.md` **只有 7 字节**（`本项目绑定spring`）⇒ **说明书实质不存在**；`Infrastructure.md` … `api-gateway/`、`.env`、`service/user-rpc/`、`service/admin-rpc/` **全部不存在**，端口（`49432`/`sea_user`/`sea`）与实际 compose（`5432`/`first_db`/`admin`）不符」—— **其中「README 只有 7 字节」「`.env` 不存在」两条已被 2026-10-29 R4 轮推翻**：现 `README.md` = **9815 字节**（`wc -c`，含「〇、5 分钟跑起来」/ 环境变量表 / `manage.sh up` 三步 / 验证命令）· `.env` 与 `.env.example` **均已存在**（`.env.example` **已入库**，6626 字节）· `Infrastructure.md` 头部已加「2026-10-29 现状订正（R1·T-1.1）」块并声明 §3/§4/§6/§7 已按实测现状重写。**残留（仍成立）**：`Infrastructure.md` §1 对照表 `:26` 仍写 `user-rpc / admin-rpc 用 ${POSTGRES_ADDR:localhost}:49432/sea_user`（实际 = `${POSTGRES_ADDR:postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB:first_db}` + `POSTGRES_USER:admin`，见 `service/users/user/user-rpc/src/main/resources/application.yaml:13-14`）· `:24`/`:33`/`:35`/`:163`/`:312` 仍引用**仓内不存在的 `api-gateway/`** ⇒ **照 `Infrastructure.md` 操作仍会走错**，但**照 README 操作不会** |
| 5 | 具体差异 | 🔴 **2026-10-29 订正**：① **无 README ⇒ 已消除**（README 现为 9815 字节完整说明书）；② **基础设施文档与事实全面矛盾 ⇒ 已消除大半**（`Infrastructure.md` 已按现状重写，**残留** = `api-gateway/` 与 `sea_user` 两处，见上格）；③ **「仓内既无 `.env` 也无模板」⇒ 已消除**（`.env.example` **已入库**，`README.md` 的「三步」第 1 步即 `cp .env.example .env`）；④ `manage.sh:37-46` 硬校验 `.env` 必须含 `POSTGRES_PASSWORD` + `USER_JWT_SECRET` ⇒ **仍成立**，但**现在有模板可抄**（`.env.example`）⇒ 不再「无从得知」 |
| 6 | 暂缓 / 简化理由 | **无法暂缓** —— 它不是「高级工程能力」，是 P0#5 定义的**准入条件本身** |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响运行时行为；影响**可评估性**：面试官/验收者按说明**无法复现启动** ⇒ 阶段 1「核心业务链路能够真实运行」**无法被外部复现**，只能靠内部台账自证 |
| 8 | 是否阻断阶段 1 | 🔴 **是（A 类）** |
| 9 | 阶段 2 的恢复或替换方案 | 阶段 1 先补**最小可复现说明书**（README：架构一句话 / 模块清单 / 环境变量表 / 启动顺序 / 验证命令 / 已知限制），阶段 2 再扩成完整运维手册 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：全新克隆 ⇒ 只读 README ⇒ 能在**不询问作者**的前提下起 PG+Redis+Nacos+应用并打通一条核心链路；通过标准 = 另一人照做成功且无需口头补充 |
| 11 | 当前证据及剩余证据缺口 | **证据（原值保留）**：`README.md` = 7 字节（`wc -c`）；`Infrastructure.md:74-88` 列的 16 服务中 5 个路径实测不存在（`ls` 逐条）；`docker-compose.yaml:15` 的 `:?` 强制；`manage.sh:37-46` 的硬校验；仓内 `find . -name '*.env*'` = 0 命中。**缺口**：README / `.env.example` / `.gitignore` 均**不在 G1 写权面** ⇒ 本 lane 只能登记与上报。<br>🟢 **2026-10-29 复测（原证据已全部失效，改为当前值）**：`wc -c README.md` = **9815**（非 7）· `.env.example` **已入库**（6626 字节，`git ls-files \| grep -c '^\.env'` = **1**）· `.env` **存在且被忽略**（6849 字节，本地文件，`.gitignore` 实有 `.env` / `.env.*` / `!.env.example` 三条规则）· `find . -name '*.env*'` ≠ 0。**剩余缺口（唯一）**：根 `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处残留 ⇒ 该文件**仍不在 G1 写权面** ⇒ **只能登记 + 上报给主线/收口执行者** |
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 A 类（部分闭合）—— 三条陈旧事实已被 R4 轮推翻，残留 1 处文档订正** ⇒ **现行态 = 🟡 维持部分闭合**（**HIGH 条目 · 残留那几行本轮逐行重核，仍在**）。依据：**残留文档行**（根文件，**不在 G1 写权面 ⇒ 只登记**）`Infrastructure.md:24` = `| **服务发现 / 配置中心** | … | user-rpc / admin-rpc / api-gateway 全部 Nacos discovery + config`（含 `api-gateway`）· `Infrastructure.md:26` = `… ${POSTGRES_ADDR:localhost}:49432/sea_user`（含 `sea_user`）· `Infrastructure.md:33` = `… user-rpc / admin-rpc / api-gateway 暴露 /actuator/prometheus` · `Infrastructure.md:163` = `┌─ api-gateway (Spring Cloud Gateway :49290)` · `Infrastructure.md:312` = `| Spring Boot 进程入口（user / admin / gateway） | …` ⇒ **仓内确无 `api-gateway` 模块 ⇒ 残留仍在**。**已闭合的三条本轮复核仍在**：`README.md` = **9815 字节**（`wc -c README.md`）· `.env.example` = **6626 字节**（已入库）· `.gitignore:46` = `.env` · `:47` = `.env.*` · `:48` = `!.env.example` ⇒ **3 条 `.env` 规则齐在**。⇒ 维持 🟡、维持「不再阻断」。核对日期 2026-10-30。

### `S1-USERS-002` 🟡 **B 类** · datasource 口令 fallback（全仓 10 处；**G1 面 3 处已取消** · 残余 7 处跨域）

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-002` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 侧 `service/user/user/rpc/etc/user.yaml:13` + `service/user/admin/rpc/etc/admin.yaml:14` ⇒ `Password: "${POSTGRES_PASSWORD}"` / `DataSource: "postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@..."`（**无默认值**） |
| 3 | 原始行为 | go-zero `core/conf/config.go:63` = `loader([]byte(os.ExpandEnv(string(content))), v)` ⇒ `os.ExpandEnv` **不认 `${VAR:default}` 语法** ⇒ 未设 `POSTGRES_PASSWORD` 时**展开为空串** ⇒ 用**空口令**连库 ⇒ 认证失败 ⇒ **fail-fast** |
| 4 | 阶段 1 当前行为 | RAG 侧 9 个 rpc 模块用 Spring 的 `${POSTGRES_PASSWORD:password}`（7 处）与 `${POSTGRES_PASSWORD:Sea-TryGo}`（2 处 = **本域**）⇒ 未设 env 时**静默用硬编码口令**（与 Go 的 fail-fast **相反**）；且**两种默认值并存** ⇒ 同一套 PG，裸跑时 9 个模块里**最多一组能连上** |
| 5 | 具体差异 | ① 失败时机：Go = 连库时失败；RAG = 用错口令连库（更晚、更难懂）；② 默认值分裂：`Sea-TryGo` ×2 vs `password` ×7；③ 硬编码凭据进版本库（**P0#8 面**） |
| 6 | 暂缓 / 简化理由 | 域内可自修 ⇒ **不暂缓**。🔴 **2026-10-30 16:5x 订正**：原写「『完全取消 fallback』这一条**暂缓到阶段 2**（**要连带改 `tools/difftest` 装置**与 9 个模块 ⇒ 跨域 ⇒ 不在 G1 写权面）」⇒ ⇒ **该前提不成立** —— 第 8 / 第 9 / 第 11 行**已实测** compose / `manage.sh` / 对拍装置**都显式注入** `POSTGRES_PASSWORD`（`tools/difftest/run.sh:196` 导出 `POSTGRES_PASSWORD=difftest`；根级 compose 用 `${POSTGRES_PASSWORD:?…}`）⇒ ⇒ **取消 fallback 根本不需要改装置** ⇒ ⇒ **G1 面 3 处本轮已取消**（user-rpc `application.yaml:26` / admin-rpc `application.yml:24` / follow-rpc `application.yml:38` · ⚠️ **行号因本轮加注释位移 +2**）⇒ ⇒ 残余 **7 处**属 **G2/G3 面** ⇒ **跨域上报**（不再写「暂缓到阶段 2」） |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | 不影响已正确配置环境的运行；**放大误配置**：缺 env 时不早失败，而是拿一个**已知错误**的口令去连 ⇒ 故障点远离根因 |
| 8 | 是否阻断阶段 1 | ❌ **否**（compose / `manage.sh` / 对拍装置**都显式注入** `POSTGRES_PASSWORD` ⇒ 文档化路径永远不落 fallback） |
| 9 | 阶段 2 的恢复或替换方案 | ① 全仓统一为**一个**来源（`.env` ⇒ env 注入，**取消 fallback**，与 Go 的 fail-fast 对齐）⇒ **G1 面 3 处已落码（2026-10-30）· 残余 7 处待 G2/G3**；② `.env.example` **已入库**（`:45-47` 已有 `POSTGRES_PASSWORD` 键）；③ 对拍装置已导出 `POSTGRES_PASSWORD`（`tools/difftest/run.sh:196`）⇒ **取消 fallback 不破坏现有装置**（这一条**实测**过，不是推断） |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：rpc 模块在**不设** `POSTGRES_PASSWORD` 时**启动失败并给出明确的配置缺失错误**（不是 SQL 认证错误）；通过标准 = 缺 env 时的失败信息指向「缺配置」而非「口令错」。⚠️ **2026-10-30 实测口径**：RAG 侧改后是**启动即失败**（Spring 解析不到占位符 ⇒ DataSource bean 创建失败）⇒ ⇒ **与 Go 的「启动成功、连库时失败」在失败时刻上不同** ⇒ 该残余属**有意偏离候选（RAG 更严的一侧）**，与 JWT 的 fail-closed 守卫同族 |
| 11 | 当前证据及剩余证据缺口 | **证据**：`grep` 逐文件枚举 10 处（排除 `target/` 副本，见 §9 复现）；`go-zero@v1.10.2/core/conf/config.go:63`（`os.ExpandEnv` ⇒ **不认 `${VAR:default}`** ⇒ 缺变量即空串 ⇒ fail-fast）；Go 侧**全部** rpc yaml **无一处**字面量默认值（`user.yaml:13` · `admin.yaml:14` · `follow.yaml:33` · `article.yaml:13` · `article.compose.yaml:13` · `favorite.yaml:13` · `message.yaml:32` · `points.yaml:8`）；`tools/difftest/run.sh:196` 导出 `POSTGRES_PASSWORD=difftest` ⇒ **装置不依赖 fallback**。**缺口（2026-10-30 更新）**：① **G1 面 3 处已取消 fallback**（本轮落码）⇒ 残余 **7 处**在 G2/G3 面；② ⚠️ **现有凭据守门 `tools/difftest/check-no-prod-secrets.py` 不覆盖这一形态**（全文无 `POSTGRES_PASSWORD` 相关判据 ⇒ **对本条零判别力**）⇒ **建议扩到该脚本**（落点 `tools/` = 共享面 ⇒ 转 G4 / 收口） |

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）**：原判 **🟡 B 类** ⇒ **现行态 = 🟡 维持（本条的「未闭环」半边仍在）**；
> ⚠️ **但第 4 / 第 5 行的描述已被 2026-10-29 R4 轮的落码作废 ⇒ 属陈旧快照**。
> - **实测（2026-10-29，全仓 grep 非 target 副本）**：**9 个 rpc 模块现已全部**是 `${POSTGRES_PASSWORD:password}`，**共 10 处**：
>   `like-rpc:20` · `comment-rpc:53` · `follow-rpc:27` · `message-rpc:29` · `favorite-rpc:55` ·
>   `users/admin/admin-rpc:21` · `users/user/user-rpc:24` · `task-rpc:16` · `article-rpc:20` 与 `article-rpc:171`（**该模块 2 处**）
>   ⇒ **「`Sea-TryGo` ×2 vs `password` ×7」这个「默认值分裂」已不存在** ⇒ 第 4 / 第 5 行写的是**修复前**的形态。
> - **仍然成立的半边（⇒ 所以本条维持 🟡 而不是改判 ✅）**：**fallback 本身没取消** ——
>   ⚠️ **2026-10-30 16:5x 再更新**：**G1 面 3 处已取消 fallback**（user-rpc · admin-rpc · follow-rpc ⇒ 现为 `password: ${POSTGRES_PASSWORD}`）⇒ ⇒ **残余 7 处 / 6 个模块仍写** `${POSTGRES_PASSWORD:password}` ⇒ **未设 env 时静默用硬编码口令 `password`**
>   （与 Go 的 `os.ExpandEnv` 展开成空串 ⇒ fail-fast 相反）⇒ **「失败时机」这半边差异仍在**。
> - ⇒ **本条现状应读作**：「**默认值分裂已消除（域内自修已完成）** ＋ **fallback 未取消（跨域，属阶段 2）」**；
>   修复动作已记录在本文件 **§4.1 `T2`**（⚠️ **2026-10-30 16:5x 指针重锚**：原写 `application.yaml:24` / `admin-rpc application.yml:21` ⇒ **行号因本轮加注释位移 +2** ⇒ 现为 `application.yaml:26` / `admin-rpc application.yml:24` · 且**这两处 + follow-rpc 的 1 处已按 Go 同形取消 fallback**）。
> - ⚠️ **复算入口**（机器可复算，不依赖本条的文字）：
>   `grep -rn "POSTGRES_PASSWORD:" --include="*.yml" --include="*.yaml" service/ | grep -v "/target/"`
>   ⇒ **应得 10 行、且全部为 `${POSTGRES_PASSWORD:password}`**（2026-10-29 实测一致）。

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/run.sh` ⇒ **EXIT=0**（2026-10-30 03:29:45 · 137s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」） ⏐ PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ==> 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。

### `S1-USERS-003` ✅ **B 类（已闭合）** · `.env` 不在 `.gitignore` ⇒ 照说明做会把密钥提交进仓 —— **2026-10-29 订正：`.gitignore` 已实有 `.env` 规则、`.env.example` 已入库 ⇒ 本条已闭环**

| # | 项 | 内容 |
|---|---|---|
| 1 | 唯一编号 | `S1-USERS-003` |
| 2 | 原始项目中的文件 / 方法 / 业务契约 | Go 真源仓的 `.gitignore` 排除 `.env`（Go 侧 `.env` 是真源仓的一部分，**不进版本库**） |
| 3 | 原始行为 | Go 侧 `git ls-files \| grep '^\.env'` = 0 命中 ⇒ 密钥文件**从未**进过版本库 |
| 4 | 阶段 1 当前行为 | 🔴 **2026-10-29 订正（本行原值保留于左）**：原记「RAG 侧 `.gitignore` **不含任何 `.env` 规则**（实测 `grep -nE '\.env' .gitignore` = 0 命中；`git ls-files` 也 = 0 命中 ⇒ 只是**还没人提交过**，不是**被保护**）」—— **该事实已被 2026-10-29 R4 轮推翻**：现 `.gitignore` 末尾「### 本地环境变量」段**实有 3 条规则**：`.env`（忽略本地密钥文件）· `.env.*`（忽略其余变体）· `!.env.example`（**放行模板**）⇒ 实测 `grep -nE '\.env' .gitignore \| wc -l` = **4**（3 条规则 + 1 行说明注释）、`grep -nE '^\.env$' .gitignore` = **1** ⇒ **`.env` 现已被保护**；且 `git ls-files \| grep -c '^\.env'` = **1**（**只有 `.env.example` 入库** ⇒ 密钥文件未入库） |
| 5 | 具体差异 | `manage.sh:37-46` **强制**要求仓根存在 `.env` 且含 `POSTGRES_PASSWORD` + `USER_JWT_SECRET` ⇒ **照项目说明做**的人必然创建 `.env` ⇒ 而 `.gitignore` 不拦它 ⇒ `git add -A` 即把**真实口令与 JWT 签名密钥**提交进版本库 |
| 6 | 暂缓 / 简化理由 | **无法暂缓**：一行 `.gitignore` + 一个 `.env.example` 即可根治 |
| 7 | 对业务正确性 / 安全性 / 数据一致性 / 可靠性的影响 | **安全性**：这是 P0#8 唯一一条**「照说明做就会中招」**的缺陷 ⇒ 影响最直接 |
| 8 | 是否阻断阶段 1 | 🟡 **否，但属 P0#8 面的强命中**（记为 B 类，**修法只差两行**） |
| 9 | 阶段 2 的恢复或替换方案 | ① `.gitignore` 加 `.env`（+ `.env.*` 但放行 `.env.example`）；② 提交 `.env.example`（只含键名与占位，**不含值**）；③ `manage.sh` 在 `.env` 缺失时**自动从 `.env.example` 复制**并提示填写 |
| 10 | 阶段 2 的测试场景和通过标准 | 场景：创建含真实口令的 `.env` ⇒ `git status` **不出现** `.env`；`.env.example` 存在且**不含任何真实值**；通过标准 = 两条断言同时成立 |
| 11 | 当前证据及剩余证据缺口 | **证据（原值保留）**：`.gitignore` 实测无 `.env` 规则；`manage.sh:37-46` 实测强制要求；`git ls-files` 无 `.env`。**缺口**：`.gitignore` / `.env.example` **不在 G1 写权面**。<br>🟢 **2026-10-29 复测（原证据已失效，改为当前值）**：`.gitignore` **实有 3 条 `.env` 规则**（`.env` / `.env.*` / `!.env.example`）· `.env.example` **已入库**（6626 字节；`git ls-files \| grep -c '^\.env'` = **1**，且该 1 条**就是** `.env.example`）· 本地 `.env`（6849 字节）**被忽略**（`git check-ignore -q .env` 命中）⇒ **「照说明做会把密钥提交进仓」这一风险已消除** ⇒ **本条已闭环**。**剩余缺口**：`.gitignore` / `.env.example` **不在 G1 写权面**（本轮只做**复核与订正台账**，不改这两个文件） |
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ B 类（已闭合）—— `.gitignore` 已实有 `.env` 规则、`.env.example` 已入库 ⇒ 本条已闭环** ⇒ **现行态 = ✅ 维持已闭合**（**HIGH 条目 · 两条前提本轮重新取证，仍在**）。依据：`.gitignore:46` = `.env` · `.gitignore:47` = `.env.*` · `.gitignore:48` = `!.env.example`（`:45` = 注释「模板见 .env.example（**已入库**）；本地 .env 由使用者自建」）⇒ **3 条规则齐在**；`.env.example` **已入库且为 6626 字节**（`ls -la .env.example`）⇒ **两个前提都仍在 ⇒ 维持已闭合**。核对日期 2026-10-30。

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
> ⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 B 类 · 头像链路的事务正确性依赖调用方永远跨 Bean 调用（无报错保护）** ⇒ **现行态 = 🟡 维持**（**现状正确、缺防退化这一判断本轮复核仍在**）。依据：**RAG** `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:145` = `@Transactional`（`createAvatarHistory`）· `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:215` = `@Transactional`（`selectAvatarHistory`）⇒ **事务边界在 mapper 层的 Spring 代理上** ⇒ **同 Bean 内部自调用会绕过代理** ⇒ 「依赖调用方永远跨 Bean 调用」这一判断**仍成立**。⚠️ **指针订正**：原文引的 `UserAvatarHistoryMapper.java:118`/`:174` 与 `UserDomainService.java:584`/`:648` **均为过期行号** ⇒ 现行为 `:145`/`:215` 与 `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:704`（`selectAvatar`）/`:760`（`uploadAvatar`）（原文不删）。核对日期 2026-10-30。

### `S1-USERS-005` ✅ **B 类（已闭合）** · `follow-rpc` 的 gRPC 端口**硬编码**，本机被占 ⇒ 无法并行真启动 —— **2026-10-29 订正：与 §6.3 表的「✅ 已修（`${FOLLOW_RPC_PORT:8082}`）」收敛为同一状态**

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

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）· `S1-USERS-001` / `-003` / `-004` / `-005` 四条一并核过**
> （`S1-USERS-002` 另见上一块的独立订正块）。
> | 条目 | 原判 | 现行态 | 2026-10-29 实测依据（两侧/两侧或源码+活体） |
> |---|---|---|---|
> | `S1-USERS-001` | 🟡 A 类（部分闭合） | 🟡 **维持** | README.md **9815 字节** ✓；`.env.example` **6626 字节**且**已入库**（`git ls-files \| grep '^\.env'` = **1**，就是 `.env.example`）✓；`.gitignore:45-48` 实有 `.env` / `.env.*` / `!.env.example` 三条规则 ✓ ⇒ **「三处陈旧事实已被推翻」这半边成立**；**残留**仍是 `Infrastructure.md` 里的 `api-gateway/` 与 `sea_user` 两处 ⇒ **A 类未闭合** |
> | `S1-USERS-003` | ✅ B 类（已闭合） | ✅ **维持** | 同上三行 `.gitignore` 规则 + `.env.example` 已入库 ⇒ **「照说明做会把密钥提交进仓」这一风险确已消除** ✓ |
> | `S1-USERS-004` | 🟡 B 类 | 🟡 **维持** | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:146` `default UserAvatarHistoryEntity createAvatarHistory(` ＋ `:216` `default UserAvatarHistoryEntity selectAvatarHistory(...)` ⇒ **`@Transactional` 仍挂在 mapper 接口的 `default` 方法上**（**必须经 Spring 代理**才生效）⇒ **本条描述的形状仍在** ⇒ 维持 🟡 |
> | `S1-USERS-005` | ✅ B 类（已闭合） | ✅ **维持** | `service/follow/follow-rpc/src/main/resources/application.yml:143` `port: ${FOLLOW_RPC_PORT:8082}` ⇒ **已可 env 覆盖** ✓（`:131` 是 `${FOLLOW_RPC_MANAGEMENT_PORT:16065}`，不是本条对象） |
> - **不改判的理由**：四条断言的都是「**某一个具体事实还在不在**」，逐条实测后**全部与台账所写一致** ⇒ 状态全部维持。
> - ⚠️ **指针漂移一处**：`S1-USERS-004` 第 4 行写 `UserAvatarHistoryMapper.java:118` / `:174` ⇒ **现行 `:146` / `:216`**（`:118` / `:174` 现是 javadoc）。

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
| **实际执行结果** | ⏳ **落码完成，验证待 §5 回填**（本轮初稿曾在此处直接写「已全绿」，属**先写结论后取证据** ⇒ 已改为待回填）<br>· 静态唯一化断言 ✅（§9 R2 实测：G1 面 3 处取消**前** = `10 ${POSTGRES_PASSWORD:password}` / 9 个模块；**取消后 = `7 …` / 6 个模块** ⇒ 残余 7 处属 G2/G3 面）<br>· 域级回归 ⏳ · 真启动连库 ⏳ |
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
| **尚未解决的问题** | 根级 `docker-compose.yaml` **没有**这 5 个 api 的服务定义 ⇒ compose 起不来 ⇒ 只能裸 jar 起（`S1-FOLLOW-004`）<br>🟡 **2026-10-29 订正**：此处原并列引用 `S1-USERS-001` —— 但 **`S1-USERS-001` 的「说明缺失」已闭合**（README 已补）⇒ **本行残留的是「compose 编排缺 5 个 api 服务」这一独立事实**（归属 `S1-FOLLOW-004` / 根级 compose），**不再由 `S1-USERS-001` 承担** |
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
| 1 | 取消 datasource 口令 fallback | ⚠️ **2026-10-30 订正**：原写「**跨域**（9 个模块 / 8 个域）⇒ 不在 G1 写权面」⇒ ⇒ **G1 面 3 处已取消**（user-rpc · admin-rpc · follow-rpc）⇒ ⇒ 残余 **7 处 / 6 个模块**属 **G2/G3 面** ⇒ 登记 `S1-USERS-002` 第 9 项 |
| 2 | ~~改 `README.md` / `.gitignore` / 补 `.env.example`~~ ⇒ **2026-10-29 R4 轮已完成**（README 9815 字节 · `.gitignore` 3 条 `.env` 规则 · `.env.example` 已入库）<br>🟡 **剩余**：改 `Infrastructure.md`（`api-gateway/` + `sea_user` 两处） | **根级共享文件** ⇒ 不在 G1 写权面 ⇒ **只登记 + §6 上报**（`S1-USERS-001` 残留项 / `S1-USERS-003` 已闭合） |
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
| 🆕 16 | `mvn … -DskipTests … package`（**先决**：§5.4 的 `check-jar-executable.sh` 必须先有 fat jar） | **0** | **BUILD SUCCESS · 12.484 s** ⇒ **8 个 jar 产出**（user-api/user-rpc/admin-api/admin-rpc/users-common/follow-api/follow-rpc/follow-common）|
| 🆕 17 | `bash tools/difftest/check-jar-executable.sh` | **0** | 🟢 **本 lane 的 6 个 api+rpc 模块全部 ✅**（`follow-api` 80M · `follow-rpc` 64M · `admin-api` 80M · `admin-rpc` 79M · `user-api` 80M · `user-rpc` 79M，均含 `Main-Class: org.springframework.boot.loader.launch.JarLauncher`）⇒ ⚠️ 其余 14 个 api/rpc 模块报「没有产物（未构建？）」= **别的 lane 的模块**，**不算失败也不计通过** ⇒ 基线 §九 `G-10` 的「未构建 ⇒ 跳过且不计失败」缺口**在本 lane 已闭合**（本 lane 覆盖 6/6）|
| 🆕 18 | `bash tools/difftest/exp-difftest-grpc-user9.sh --no-build`（**双侧活体对拍**） | **1** | 🔴 **43 场景 / 39 等价 / 4 处差异** ⇒ **4 处全部是 N-2 已签字的 Go 源码乱码文案**（`鐢ㄦ埛涓嶅瓨鍦?` ×3 + `澶村儚鍘嗗彶涓嶅瓨鍦?` ×1）⇒ **无新漂移**；与 2026-10-24 基线（`users/user/rpc.md` §9.1.2「43 场景 / 39 等价 / 4 处差异」）**逐条同形** ⇒ **本轮 = 独立复现**（详见 §5.4bis）|
| 🆕 19 | `check-no-prod-secrets.py`（**G4 新增守门** `19b2edf`，脚本取自 `align/g4-service-common`） | **1** | 🔴 **6 条未豁免命中，全部在 `service/users/users-common/**`** ⇒ **G4 写权面**（该脚本明写 users-common **永不可豁免**）⇒ **我域（G1 白名单内）未豁免命中 = 0** ✅；已豁免 **91** 条（我域 25 条，落在 `src/test/**` 夹具面 + `src/main/java/**` javadoc 面 + `application.yml` 默认值面）⇒ ⚠️ **6 条红是「树间版本差」**：G4 分支已把 `users-common/…/JwtUtil.java` 去明文（`git diff` = **5 insertions / 13 deletions**）⇒ **合并后应归零** ⇒ 登记为**合并收口回归时复验项**，**不是本域缺陷**（见 §6.3 `S1-USERS-013`）|

### 5.4 ⛔ 本轮**明确未跑**的命令（诚实边界）

| 命令 | 为什么没跑 | 它的结论**归谁** |
|---|---|---|
| `bash tools/difftest/run.sh`（users 双侧活体 HTTP wire 对拍） | 需 **Go 栈 + RAG 栈双栈编排**（起 etcd / PG / Redis + 两个 Go 二进制 + 两个 RAG jar）⇒ 本轮未起 | **沿用台账既有记录**（2026-10-24/27 的 52/52 等）⇒ **本轮未复验** ⇒ 证据强度**不提升** |
| `python3 tools/difftest/matrix.py`（users DTO 字段笛卡尔） | 同上（需双栈在跑）⇒ 未跑 | 同上 |
| ✅ `bash tools/difftest/exp-difftest-grpc-user9.sh --no-build` | ✅ **本轮已跑**（2026-10-29 02:4x）⇒ **43 场景 / 39 等价 / 4 处差异**（4 处全是 N-2 已签字的 Go 乱码文案）⇒ 见 §5.4bis | **本轮已复验** ⇒ 证据强度**提升**（用户域双侧活体：**可复算**）|
| `bash tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up` | 需**双栈编排**（沿用 `run.sh` 的两套 admin-rpc）⇒ 本轮未跑。⚠️ **资产在版本控制内**（`exp-difftest-grpc-admin-errors.sh` + `GrpcAdminDualPortRunner.java` 均 `git ls-files` 命中）⇒ **可复算**，只是本轮未复跑 | 沿用台账既有记录（2026-10-23 的 18 cases）⇒ 本轮未复验 ⇒ 证据强度**不提升** |
| `bash tools/difftest/exp-difftest-redis-blacklist.sh` | 需双栈 + **故障注入**（停 Redis / `CLIENT PAUSE`）⇒ 本轮未跑。⚠️ **资产在版本控制内** ⇒ 可复算 | 同上 |
| ✅ `bash tools/difftest/check-jar-executable.sh` | ✅ **本轮已跑**（**先 `mvn package`** ⇒ 8 个 jar 产出）⇒ **本 lane 6/6 模块全 ✅**（详见 §5.3 第 17 行）| — |

⇒ ⚠️ **口径**：以上命令**未跑**者的结论**一律按台账原有日期与证据强度读**，**不得因本轮回归全绿而说成「本轮已复验」**。

### 5.4bis ✅ **订正上一轮的一处推论错误**：「本轮未跑」**不等于**「域内未取证」（2026-10-29 · G1 lane）

**上一轮我写的**：「双侧活体六条命令未跑 ⇒ users 域「真实运行 + 行为等价」**未取证**」⇒ **这个推论过重了**。

**逐条核「探针是否在版本控制内」（`git ls-files`）**：

| 双侧活体命令 | 资产是否在库 | 本轮是否跑 | 真实状态 |
|---|---|---|---|
| `exp-difftest-grpc-user9.sh` + `grpc-user9-dual-port-runner.java` | ✅ **都在库** | ✅ **已跑** | 🟢 **可复算 + 本轮复现 43 场景**（含 4/4 变异验证记录，`users/user/rpc.md` §9.1.5）⇒ **R4 的 ⑦⑧ 均成立** |
| `run.sh` + `diff.py`（users HTTP wire） | ✅ 在库 | ❌ 未跑 | 🟡 可复算，本轮未复验 |
| `matrix.py`（users DTO 笛卡尔） | ✅ 在库 | ❌ 未跑 | 🟡 同上 |
| `exp-difftest-grpc-admin-errors.sh` + `GrpcAdminDualPortRunner.java` | ✅ 在库 | ❌ 未跑 | 🟡 同上 |
| `exp-difftest-redis-blacklist.sh` | ✅ 在库 | ❌ 未跑 | 🟡 同上 |
| **follow 域 52/52 探针**（`/tmp/probe.sh` + `/tmp/probe_data.sh` + `/tmp/probe_ext.sh`） | ❌ **不在库**（且 **本轮实测 `/tmp` 里已消失** ⇒ `/tmp/gofollow` 与 `/tmp/userstub` 现为空目录）| ❌ 无法跑 | 🔴 **不可复算** ⇒ 这才是基线 §九 `G-1` 的**真正落点**（`follow-api`/`follow-rpc` 的 R4 不成立）|

⇒ **订正后的准确表述**：
- **users 域**（user-rpc 43 场景 + 其余 4 条在库脚本）⇒ **双侧活体证据链在版本控制内、可复算**；本轮**复现了 user-rpc 那条** ⇒ 用户域「真实运行 + 行为等价」**已取证**（原结论「未取证」**过重**，撤回）。
- **follow 域** ⇒ 0 个落仓入口且 `/tmp` 探针**已消失** ⇒ **不可复算**（事实比上一轮记录的更硬：上一轮只说「不在版本控制内」，本轮实测**连源头都没了**）⇒ 但基线 A-2 已按 **R3** 计入 ⇒ **不改变组件判定，也不阻塞阶段一**。
- ⇒ **基线措辞需订正**：基线 P0#1 现状栏写「`like` / `follow` / `users-common` 的双侧活体探针不在版本控制内（G-1/G-2/G-4）」—— **该句本身没点名 users 域**，但 `docs/PhaseGoal/g1.md` §3 把它读成「users 域双侧活体未取证」⇒ **本节 §5.4bis 是订正后的权威口径** ⇒ 登记 `S1-USERS-012`（基线措辞订正，收口执行者）。


---

## 6. 阶段 1F：独立验收（§5.6）+ 阶段归属（§10）

### 6.0 验收结论：**🟡 有条件通过**（域级）

> **§5.6 的四种结论里选「有条件通过，但必须明确有限例外及其影响」** ⇒ 例外逐条列在 §6.2。
> 🟡 **2026-10-29 订正**（原句保留于左）：~~⚠️ **不是「通过」** —— 因为存在 **A 类阻断项**（`S1-USERS-001`，**项目级**）：**「实际项目说明」不存在**~~ ⇒ **该 A 类阻断已解除**（2026-10-29 R4 轮已补 README 9815 字节完整说明书 · `.env.example` · `.gitignore` 的 `.env` 规则）⇒ **现在「按说明复现」的路径已通**；**残留** = `Infrastructure.md` 的 2 处陈旧引用（🟡 B 类 · 域外）
> ⇒ 外部无法按说明复现启动。⚠️ **也不是「未通过」** —— 因为该阻断项**不在本域写权面**
> （根级 `README.md` 无人拥有）⇒ **本域能做的都做完了**，剩下的是**移交**而不是**本域未完成**。

| §5.6 检查项 | 结论 | 依据 |
|---|---|---|
| P0 准入条件是否满足 | ✅ **域内 8/9 满足**（#1/#2/#3/#4/#6/#7/#8/#9）· 🔴 **#5 项目级不成立** | §1 的逐条判定 + §5.1~§5.3 的证据 |
| P1 是否至少有两到三个代表性机制完成验证闭环 | ✅ **有**（RPC 超时/deadline · 事务与并发写入 · 鉴权与黑名单 fail-open · 错误码契约 · proto wire 契约） | 各域台账的 L3/L4 条目 + 本轮 `FollowProtoContractTest`（**本轮新增第 5 个闭环**）|
| 核心业务是否能够实际运行 | 🟡 **域内能**（4 组件可构建可起）· **项目级按说明**：~~不能（说明不存在）~~ ⇒ 🟢 **2026-10-29 已能**（README 9815 字节完整说明书） | `S1-USERS-001`（**残留**：`Infrastructure.md` 2 处）|
| 测试是否真实断言业务结果 | ✅ **是**（1133 条 · 0F/0E/0S · **含变异实测**） | §5.1 + §5.2 + 域台账变异章节 |
| 关键失败路径是否具有明确行为 | ✅ **是**（401/400/413/503/Redis 断连与超时逐条有断言） | §1 #4 + 域台账 |
| 是否存在严重安全或数据正确性问题 | 🟡 **1 条**（空签名密钥可起栈 ⇒ **P0#8**）—— **域外**（根级 compose 缺 `:?`）+ 域内已收窄（口令默认值统一） | `S1-USERS-002` + 基线 §11.1 `D-01` |
| 项目是否能够按实际步骤构建、配置和启动 | 🟡 **是（2026-10-29 订正）** —— ~~🔴 否（项目级）~~ ⇒ README 已补齐「三步跑起来」；**残留** = `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处（照它操作会走错，照 README 不会） | `S1-USERS-001` |
| 所有已知缺陷和关键风险是否已记录 | ✅ **是**（§3 差异清单 + §6.1 上报 + 域台账 + vs-common 9 轴） | §3 + §6 |
| 暂缓项是否具备明确的阶段 2 处理方案 | ✅ **是**（每条都有第 9/10 项） | §3 |

### 6.0.1 🆕 **P0 九条逐条对照（2026-10-29 收敛核验轮 · 按本轮实测证据重判）**

> 判据原文 = `docs/PhasedGoal.md:38-52`；职责分摊 = `docs/PhaseGoal/g1.md` §3。
> ⚠️ **口径**：「✅ 域内」= 本 lane 写权面内已闭合且有证据；「🟡」= 域内部分闭合，残余在域外；「🔴」= 域内不成立。

| P0 | 内容 | 本 lane 责任 | 本轮证据 | **域内判定** |
|---|---|---|---|---|
| **1** | 核心业务链路**真实运行** | 协作 | 🟢 **users 域双侧活体证据链在版本控制内**（`exp-difftest-grpc-user9.sh` + runner 均 `git ls-files` 命中）⇒ **本轮实跑 43 场景 / 39 等价 / 4 处差异（全为已签字 N-2）**；其余 4 条对拍脚本同样在库；🟢 **6 个 api/rpc 模块 fat jar 全部可执行**（§5.3 第 16/17 行）<br>🔴 **follow 域**：探针**不在库**且 **`/tmp` 源头已消失** ⇒ 不可复算（§5.4bis） | 🟡 **域内部分闭合**（users ✅ / follow 🔴）⇒ **不改变组件判定**（follow-api 已按 R3 计入 ✅） |
| **2** | 参数校验 / 业务规则 / 认证 / 权限**四项**有效 | **主责** | 🟢 **follow 域本轮闭合**（`T11`：**62 条**失败路径矩阵，真 Tomcat，8 端点 × 4 类失败 + 7 条单点）⇒ 域台账 §4.6 / §5.2.1<br>🟢 users 域既有域内断言链<br>🟡 `article`/`task`/`hot` **无独立失败路径矩阵** ⇒ **域外**（G3/G2） | ✅ **域内闭合**（follow 的缺口已补）· ⚠️ **P0#2 本身仍非 ✅**（其余 3 域未补 ⇒ 不因此判成 ✅） |
| **3** | 持久化 / 事务边界 / 无数据丢失重复状态错误 | **主责** | 🟢 `check-ddl-static.py --domain users --indexes` ⇒ **16 处**（类型/长度 **0** · 可空性 1 · DEFAULT 8 · 索引 7）**逐项对得上** `AGENTS.md` §0 第 2 条 ⇒ **无新增漂移**；`--domain follow --indexes` ⇒ **0 处**<br>🟢 **约束集合两侧完全一致**（4 个唯一约束 ≡ 唯一索引，仅载体不同）<br>🟡 残余：该脚本比的是**活库**不是仓库 `.sql`（基线原话）⇒ **工具性质 ⇒ 域外** | ✅ **域内闭合**（索引/唯一约束/触发器三面已机器核过） |
| **4** | 核心失败路径有明确行为，不无声吞异常 / 返回误导性成功 | 协作 | 🟢 follow 62 条矩阵（**含 Redis fail-open、404/405 逐字节、拦截器顺序**）⇒ §5.2.1 | ✅ **域内闭合** |
| **5** | 项目能按**实际项目说明**构建、配置、启动 | —（收口） | 🟡 **2026-10-29 订正**：~~🔴 `README.md` 7 字节 · `Infrastructure.md` 与事实全面矛盾 · compose 要 `.env` 而仓内无 `.env`/`.env.example` ⇒ **项目级不成立**~~ ⇒ **三条均已被 2026-10-29 R4 轮推翻**（README 9815 字节完整说明书 · `.env.example` 已入库 · `.gitignore` 实有 3 条 `.env` 规则）⇒ **残留** = `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处 | 🟡 **域外残留**（`S1-USERS-001`） |
| **6** | 核心业务有可执行自动化测试，且断言真实业务结果 | **主责** | 🟢 域级回归（**带 `clean`**）**两轮**：EXIT=0 · BUILD SUCCESS · 08:56 / 10:01 min · **1195 条 / 0F / 0E / 0S**（逐模块一致）<br>🟢 定向复验 7 条 + 矩阵 62 条全绿 + 域台账的**变异实测**记录 | ✅ **域内闭合** |
| **7** | 清晰的主要模块职责，可追踪核心调用链 | —（收口） | — | —（收口判定） |
| **8** | 关键配置与敏感信息无明显不安全处理 | **主责** | 🟡 **fallback 未全仓取消**（本轮 G1 面 3 处已取消 ⇒ 余 **7 处**：article×2 / comment / favorite / like / message / task ⇒ 属 **G2/G3 面**）<br>🟡 `.gitignore` **未排 `.env`**（根级 ⇒ 收口）<br>🟢 **本轮新守门 `check-no-prod-secrets.py`：我域未豁免命中 = 0**（25 条豁免在夹具面/javadoc 面/默认值面）<br>🔴 **2026-10-30 16:5x 口径订正（本行原写的「Go 真源 yaml 是**字面量默认值**，恒能起栈 ⇒ 取消 fallback ⇒ RAG 比 Go 更严 ⇒ 属**有意偏离**（需签字），不是纯域侧自修」—— 这句话的**前提是错的**）**：Go 侧**全部** rpc yaml（`service/user/user/rpc/etc/user.yaml:13` · `service/user/admin/rpc/etc/admin.yaml:14` · `service/follow/rpc/etc/follow.yaml:33` · `service/article/rpc/etc/article.yaml:13` · `service/article/rpc/etc/article.compose.yaml:13` · `service/favorite/rpc/etc/favorite.yaml:13` · `service/message/rpc/etc/message.yaml:32` · `service/points/rpc/etc/points.yaml:8`）**无一处**是字面量默认值 ⇒ 全部是 `"${POSTGRES_PASSWORD}"` ⇒ 且 go-zero `core/conf/config.go:63` 用 `os.ExpandEnv`（**不认 `${VAR:default}`**）⇒ 缺变量 ⇒ **空串** ⇒ 连库认证失败 ⇒ **fail-fast** ⇒ ⇒ **Go 侧「恒能起栈」不成立** ⇒ ⇒ **取消 fallback 不是「让 RAG 更严」，而是「把 RAG 从『比 Go 松』改回『与 Go 同形』」⇒ ⇒ 真漂移，不是有意偏离 ⇒ ⇒ 不需要为「保留 fallback」签字**。⚠️ **取消后的残余差异（RAG 更严的一侧）**：Go = 启动成功、**连库时**失败；RAG = **启动即失败**（Spring 解析不到占位符）⇒ **失败时刻**不同 ⇒ 这一条残余才属有意偏离候选（与 JWT fail-closed 守卫同族） | 🟡 **域内 3 处已修**（user-rpc / admin-rpc / follow-rpc · 2026-10-30）· 残余 7 处 + `.gitignore` 交 G2/G3 / 收口 |
| **9** | 已知严重缺陷 / 无法验证的关键能力 / 运行前提**已明确记录** | —（收口） | 🟢 §3 差异清单 + §6.1 上报项 + §6.2 有限例外 + 域台账（`vs-common.md` 9 轴 + `stage1-admission.md`）| ✅ **域内闭合** |

⇒ **域内收敛结论**：**P0 九条里，域内主责/协作项全部有证据且无新增缺陷**；
⇒ **域内唯一未达标项 = `follow-rpc` 的 `DRIFT-FOLLOW-006`**（5 个 `follow_rpc_*` 指标无抓取出口 ⇒ **C 类阶段 2 建设项** ⇒ 用户 2026-10-24 已定「本轮不落地」⇒ **待重新拍板**）
⇒ **域外未闭合项 8 条**（§6.1 + §6.3）⇒ **G1 lane 判定维持 🟡**（口径 = 基线 §8.1 的 `MIN(本 lane 组件状态)`；**判据未被本轮改动**）。

### 6.1 需**域外**处理的上报项（不在 G1 写权面 ⇒ 交合并收口执行者）

| 编号 | 事项 | 为什么必须域外 | 影响 |
|---|---|---|---|

> ⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **EXIT=1**（2026-10-30 03:53:22 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**） ⏐ ==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源） ⏐ ==> [3/5] 构建（Go 二进制 + RAG jar） ⏐ ==> [4/5] 起两套 user-rpc（Go :19005 / RAG :59211） ⏐ ==> [5/5] 编译并跑双端口对拍；<br>`tools/difftest/run.sh` ⇒ **EXIT=0**（2026-10-30 03:29:45 · 137s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」） ⏐ PASS —— 未复现差异（两侧 Δ_end 一致 ⇒ RAG 侧继承了客户端 deadline） ⏐ ⇒ E38① 的运行期结论成立 ⏐ ⇒ 退出码 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ⇒ 判定退出码 = 0（0=PASS / 1=FAIL / 2=INCONCLUSIVE） ⏐ ==> 。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。
| `S1-USERS-001` | ~~根 `README.md` 补**最小可复现说明书**~~ ⇒ **2026-10-29 R4 轮已补**（9815 字节完整说明书，含 5 分钟跑起来 / 环境变量表 / 启动顺序 / 验证命令） | 根文件无人拥有（G1/G2/G3/G4 白名单都不含）⇒ **已由主线/收口执行者补齐** | 🟢 **已闭合**（原判为「A 类 · 阻断阶段 1」⇒ **本轮撤销该阻断**）|
| `S1-USERS-001` | `Infrastructure.md` 与事实对齐 —— **2026-10-29 已重写大部**（§3/§4/§6/§7 按实测现状重写 + 头部加「现状订正」块）⇒ **残留 2 处**：`:26` 的 `sea_user`（实际 `first_db` / `admin`）与 `:24`/`:33`/`:35`/`:163`/`:312` 的 `api-gateway/`（**仓内无该模块**） | 同上（根文件无人拥有 ⇒ **不在 G1 写权面**） | 🟡 **B 类 · 不再阻断**（README 已可独立复现启动 ⇒ 残留只影响「照 Infrastructure.md 操作会走错」）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 B 类 · 不再阻断（README 已可独立复现启动 ⇒ 残留只影响「照 Infrastructure.md 操作会走错」）** ⇒ **现行态 = 🟡 维持**。可复算指针（补本行 PTRLESS 缺口）：**残留仍在** ⇒ `Infrastructure.md:24`（`api-gateway`）· `Infrastructure.md:26`（`sea_user`）· `Infrastructure.md:33`（`api-gateway`）· `Infrastructure.md:163`（`┌─ api-gateway (Spring Cloud Gateway :49290)`）· `Infrastructure.md:312`（`（user / admin / gateway）`）⇒ **仓内无 `api-gateway` 模块**；**已闭合部分仍在** ⇒ `.gitignore:46`/`:47`/`:48`（3 条 `.env` 规则）· `.env.example`（6626 B）· `README.md`（9815 B）。核对日期 2026-10-30。|
| ~~`S1-USERS-003`~~ | ~~`.gitignore` 加 `.env` + 提交 `.env.example`~~ ⇒ **2026-10-29 R4 轮已做**：`.gitignore` 实有 3 条 `.env` 规则（`.env` / `.env.*` / `!.env.example`）· `.env.example` **已入库** | ~~同上~~ ⇒ **已由主线/收口执行者做掉** | 🟢 **已闭合**（原 🟡 B 类 · P0#8 强命中）|
| `S1-USERS-002` | 取消 9 处 datasource 口令 fallback（与 Go 的 fail-fast 对齐） | 跨 8 个域 ⇒ 需收口统一改 | 🟡 B 类<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 B 类（取消 9 处 datasource 口令 fallback）** ⇒ **现行态 = 🟡 维持 B 类**，⚠️ **但本轮实测把「口径」改写了：「9 处 / 2 种默认值」已过期**。**2026-10-30 16:5x 再订正（本行的指针清单与计数已过期）**：**G1 面 3 处已按 Go 同形取消 fallback** ⇒ ⇒ **现在实跑只有 `7 ${POSTGRES_PASSWORD:password}` / 6 个模块**（`service/like/like-rpc/.../application.yml:20` · `service/comment/comment-rpc/.../application.yml:53` · `service/message/message-rpc/.../application.yml:29` · `service/favorite/favorite-rpc/.../application.yml:55` · `service/task/task-rpc/.../application.yml:16` · `service/article/article-rpc/.../application.yml:20` 与 `:171`（第二块 datasource））⇒ ⇒ **已取消的 3 处**（指针按现行行号）= `service/users/user/user-rpc/src/main/resources/application.yaml:26` · `service/users/admin/admin-rpc/src/main/resources/application.yml:24` · `service/follow/follow-rpc/src/main/resources/application.yml:38` ⇒ 三处现值均为 `password: ${POSTGRES_PASSWORD}`（**无默认值**）⇒ ⇒ ⚠️ **旧指针 `application.yaml:24` / `admin-rpc application.yml:21` / `follow-rpc application.yml:27` 已失效**（行号因本轮加注释位移）⇒ ⇒ **维持 🟡 B 类**（残余 7 处仍开着）⇒ **落点 = G2/G3 面，跨域上报**。核对日期 2026-10-30。|
| 🆕 `S1-USERS-006` | **`check-doc-links.py` 有 1 项既存锚点失效** —— `docs/PhaseGoal-judge.md:99` **自指**锚点 `#三矩阵-a终点指标与当前判定39-行` 与 §三 的实际标题不符 ⇒ **主工作区同样 FAIL** ⇒ **非本轮引入** | `docs/PhaseGoal-judge.md` 属**基线文件**（非 G1 写权面）⇒ 交收口 | 🟡 **B 类**（文档链接面；⚠️ **只影响锚点跳转，不影响任何判定**）|
| 🆕 `S1-USERS-007` | **`docs/PhaseGoal-judge.md` 的 A-2 `follow-api` 行 + 机器生成的 `docs/PhaseGoal/g1.md` 仍把已签字偏离记成开放缺陷** ⇒ 照它执行会让 G4 去改掉用户已决定保留的行为<br>⚠️ **【2026-10-31 订正 · G1 lane】危害已消解 ⇒ 本条销号**：机器生成物的「开放项归属」列现已改为 `**→ 非开放（已签字 · 不是缺陷）**` ⇒ ⇒ **不再产出「→ G4」派单指令** ⇒ ⇒ 上文那句风险描述**只作原写留档** | 基线文件 + 机器生成物 ⇒ 交收口订正 + 重跑 `check-phasegoal-lanes.py --write`（**2026-10-31 实测校验器 EXIT=0**） | ✅ **本轮销号**（2026-10-31 · G1 lane）—— **原判「🔴 A 类风险」当时成立**（本条落笔 **02:12:47**，早于共享面复核戳 **02:15:45**）⇒ ⇒ 「**原写当时成立 · 事后被共享面订正**」第三形态 ⇒ ⇒ **现行态 = ✅ 已销号**；⚠️ **残留 = 基线那格的字面原值仍在 —— 且【不可改】**（共享面刻意「原值保留」⇒ ⇒ 派工判据 `route_open()` **正认**「不计入本行的开放残余差异」这句 ⇒ ⇒ **把它改掉反而让判据失配** ⇒ ⇒ **不是待办，勿再提清它**）⇒ ⇒ **另：事实层已关、定级层未翻** —— 派生层里本条仍是 `stage=A` / `blocking=是`（`blocking` 取自显式定级叠加层、不随 `gap` 自动翻转 ⇒ ⇒ 按既有先例只关事实、不擅自翻定级 ⇒ ⇒ 定级复判并入 Jason 那一批）⇒ ⇒ **本条不再算「事实未闭合」，但仍挂在阶段一阻断清单上** · 详见 follow 档 `S1-FOLLOW-007` |
| 🆕 `S1-USERS-008` | **5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED`**（`article-api`/`comment-api`/`hot-api` ⇒ G3；`like-api`/`task-api` ⇒ G2）⇒ 在 Docker Hub 不可达的环境里**整类 error** | 各归 G2 / G3 ⇒ G1 只登记上报（本域 `follow-api` 已自修） | 🟡 **B 类**（测试基础设施）· 详见 follow 档 `S1-FOLLOW-009` |
| 🆕 `S1-USERS-009` | **404 body 的字节数在两处注释里写成「20」，实测是 19** —— ① 共享层 `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83`（⇒ **G4**）② like 域 `service/like/like-api/.../LikeApiRouterErrorRealTomcatTest.java:46` 与 `:187`（⇒ **G2**）。⚠️ **两处的断言/常量本身都对**（like 那条按**实际长度**比）⇒ **只影响注释**，但**已经产生过一次实际误判**（本轮新写的矩阵测试照抄「20」⇒ 当场红 ⇒ 才发现） | 共享层 ⇒ **G4**；like 域 ⇒ **G2** ⇒ 均**不在 G1 写权面** ⇒ G1 只登记上报 | 🟡 **P3 文档面**（**不影响行为**）· 详见 follow 档 `S1-FOLLOW-010` / `S1-FOLLOW-011` |

### 6.2 「有条件通过」的**有限例外**逐条（§5.6 要求：必须明确例外及其影响）

| # | 例外 | 范围 | 影响 | 归属 |
|---|---|---|---|---|
| **E1** | 🟡 **项目说明缺失 ⇒ 已消除**（2026-10-29 订正：原记「`README.md` 7 字节 · `Infrastructure.md` 与事实全面矛盾 · compose 要 `.env` 而仓内无模板」—— **三条均已被 2026-10-29 R4 轮推翻**：README 现 9815 字节完整说明书 · `.env.example` 已入库 · `.gitignore` 已保护 `.env`）⇒ **残留** = `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处 | **项目级**（全 10 域） | 🟡 **外部已可按 README 复现启动**（原「只能靠内部台账自证」已不成立）⇒ 残留项只影响「照 `Infrastructure.md` 操作会走错」 | **B 类** · 收口（原 A 类 ⇒ **降级**，依据 = §1 P0#5 行的 2026-10-29 订正）|
| **E2** | **空签名密钥可起栈**（10 处 yaml 带明文 JWT secret 默认值 + 根级 compose 缺 `:?`） | **跨域** | token 可伪造 ⇒ P0#8 强命中 | **B 类**（基线 `D-01`）· 收口（根级）＋ 各域（yaml） |
| **E3** | **`follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口** | follow 域 | 可观察性缺一环 ⇒ **`follow-rpc` = 🟡** ⇒ **G1 lane 判定 = 🟡** | **C 类**（阶段 2 建设项）· ⚠️ **用户 2026-10-24 已定「本轮不落地」⇒ 本轮登记待重新拍板** |
| **E4** | ~~**双侧活体对拍在本轮未复验**~~ → 🟢 **部分已复验**（2026-10-29） | users + follow | ✅ **user-rpc 43 场景本轮实跑**（39 等价 + 4 处已签字 N-2）⇒ **users 域证据强度提升**；🟡 其余 4 条（`run.sh`/`matrix.py`/`admin-errors`/`redis-blacklist`）**资产在库但本轮未跑** ⇒ 沿用原日期 | **B 类**（补跑手段）· **可解除**（跑一次即销）|
| **E5** | **`follow` 域 0 个落仓双侧对拍入口** —— 探针在 `/tmp` 且 **本轮实测 `/tmp` 源头已消失**（`/tmp/probe.sh` 等 3 个不存在，`/tmp/gofollow` 与 `/tmp/userstub` 现为空目录）⇒ **不可复算**（比上一轮记录的更硬）· 基线 §九 `G-1` | follow 域 | `follow-api`/`follow-rpc` 的 **R4 不成立**（已按 **R3** 计入 ⇒ 组件判定仍是 ✅）<br>🟡 要恢复可复算 = **重写 52 条探针 + 编排落仓**（`tools/difftest/follow/`，用户 2026-10-27 已授权该子目录）⇒ **本轮判定「不值得」**：组件判定 ✅ R3 已达标、**不阻塞阶段一** ⇒ 记为**阶段 2 建设项** | **B 类**（非阻断）· 收口或域内 |
| **E6** | **proto「消费方式」未取证**（`optional` presence 的**构造点**语义）· 基线 §九 `G-19` | 全 10 proto | **取证缺口 ⇒ 不降 R**（§1.1 ⑥）| **B 类** · 各域 |
| 🆕 **E7** | ~~**follow 域无独立失败路径矩阵**~~ · 基线 §九 `G-13` | follow 域 | ✅ **本轮闭合（`T11`）** —— 8 端点 × 4 类失败 × **真 Tomcat** = **62 条**全绿（§4.6 / §5.2.1）；⚠️ **P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因此判成 ✅** | ✅ **本轮闭合** |

### 6.3 问题分类与阶段归属汇总（§10：P0–P3 定严重程度 · A–D 定阶段归属）

| 编号 | 问题类型 | 严重程度 | **阶段归属** | 状态 |
|---|---|---|---|---|
| `S1-USERS-001` | 项目说明缺失（**README 与 `.env.example` 已补**；残留 = `Infrastructure.md` 的 `api-gateway/` 与 `sea_user`） | **P0** | **A**（阶段 1 准入阻断） | 🟡 **部分闭合**（2026-10-29 订正）· 残留项 **域外上报**（根 `Infrastructure.md` 不在 G1 写权面）⇒ **不再阻断**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 部分闭合（2026-10-29 订正）· 残留项域外上报 ⇒ 不再阻断** ⇒ **现行态 = 🟡 维持**。可复算指针（补本行 PTRLESS 缺口）：**残留** ⇒ `Infrastructure.md:26`（`sea_user`）· `Infrastructure.md:163`（`api-gateway`）· `Infrastructure.md:24`/`:33`（`api-gateway`）· `Infrastructure.md:312`（`gateway`）；**已闭合** ⇒ `.gitignore:46`/`:47`/`:48` · `.env.example`（6626 B）· `README.md`（9815 B）⇒ **维持部分闭合 + 不再阻断**。核对日期 2026-10-30。|
| `S1-USERS-002` | datasource 口令 fallback | P2 | **B**（竞争力补强） | 🟡 域内 2 行已改 · 全仓取消 fallback = 跨域 ⇒ 上报<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 域内 2 行已改 · 全仓取消 fallback = 跨域 ⇒ 上报** ⇒ **现行态 = 🟡 维持**，⚠️ **口径订正：「9 处」⇒ 实为 10 处 / 9 个模块，且默认值已统一为 `password`**。🔴 **2026-10-30 16:5x 订正 + 指针重锚**：可复算指针（补本行 PTRLESS 缺口）—— **G1 面 2 处已按 Go 同形取消 fallback** ⇒ **RAG** `service/users/user/user-rpc/src/main/resources/application.yaml:26` = `password: ${POSTGRES_PASSWORD}`（**无默认值**）· `service/users/admin/admin-rpc/src/main/resources/application.yml:24` = 同款 ⇒ **行号 +2 是本轮加注释所致**（旧锚点 `:24` / `:21` **已失效** ⇒ 典型 `P-18` 锚点漂移）；**残余 7 处的可复算样本** = `service/article/article-rpc/src/main/resources/application.yml:171` 与 `:20`（article-rpc 的**第二块 datasource**）⇒ ⇒ **「分裂成 2 种」已消除**；**「取消 fallback」本轮已完成 G1 面 3 处**（users 2 + follow 1）⇒ **残余 7 处 / 6 个模块属 G2/G3 面** ⇒ 维持 🟡 上报。核对日期 2026-10-30。|
| `S1-USERS-003` | `.env` 未进 `.gitignore` | **P1** | **B** | 🟢 **已闭合**（2026-10-29 订正：`.gitignore` 实有 3 条 `.env` 规则 · `.env.example` 已入库）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟢 已闭合（2026-10-29 订正：`.gitignore` 实有 3 条 `.env` 规则 · `.env.example` 已入库）** ⇒ **现行态 = 🟢 维持已闭合**。可复算指针（补本行 PTRLESS 缺口）：`.gitignore:46` = `.env` · `.gitignore:47` = `.env.*` · `.gitignore:48` = `!.env.example` ⇒ **3 条规则逐条在位**；`.env.example` **已入库（6626 B）** ⇒ 维持已闭合。核对日期 2026-10-30。|
| `S1-USERS-004` | 头像事务依赖跨 Bean 调用（无报错保护） | P2 | **B** | 🟡 登记（当前实现正确，缺防退化）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 登记（当前实现正确，缺防退化）** ⇒ **现行态 = 🟡 维持**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:145` = `@Transactional`（`createAvatarHistory`）· `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/mapper/UserAvatarHistoryMapper.java:215` = `@Transactional`（`selectAvatarHistory`）· `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:704`（`selectAvatar`）· `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/service/UserDomainService.java:760`（`uploadAvatar`）⇒ **编排层与事务层是两个 Bean ⇒ 代理生效；但「同 Bean 自调用会绕过代理」这一退化口未加保护** ⇒ 维持 🟡 登记。⚠️ **指针订正**：原文的 `:118`/`:174`/`:584`/`:648` 均已过期。核对日期 2026-10-30。|
| `S1-USERS-005` | `follow-rpc` 端口硬编码 | P2 | **D**（已实现） | ✅ 已修（`${FOLLOW_RPC_PORT:8082}`）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已修（`${FOLLOW_RPC_PORT:8082}`）** ⇒ **现行态 = ✅ 维持已修**。可复算指针（补本行 PTRLESS 缺口）：**RAG** `service/follow/follow-rpc/src/main/resources/application.yml:143` = `port: ${FOLLOW_RPC_PORT:8082}` ⇒ **端口已是 env 可覆盖**（`:128` = 注释「gRPC 仍是 `grpc.server.port`（8082 / `${FOLLOW_RPC_PORT}`）」）⇒ 维持已修。核对日期 2026-10-30。|
| `S1-FOLLOW-001` | rpc 层指标无出口 | P1 | **C**（阶段 2 建设项） | 🟡 用户 2026-10-24 定不落地 ⇒ **待重新拍板**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🟡 用户 2026-10-24 定不落地 ⇒ 待重新拍板** ⇒ **现行态 = 🟡 维持待拍板**，⚠️ **但「无抓取出口」这一表述已过期 ⇒ 口径订正**。可复算指针（补本行 PTRLESS 缺口）：**指标本身在位** ⇒ `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/metrics/FollowRpcMetrics.java:42` = `follow_rpc_request_total` · `:45` = `follow_rpc_request_duration_seconds` · `:48` = `follow_rpc_relation_action_total` ⇒ **抓取出口也已具备** ⇒ `service/follow/follow-rpc/src/main/resources/application.yml:193` = `management:` · `:199` = `include: health,info,prometheus,metrics` · `:207` = `prometheus:` + `enabled: true` ⇒ **「5 个 `follow_rpc_*` 指标无抓取出口」不再成立**（⚠️ **「指标确实被 prometheus endpoint 导出」本轮未复跑验证** ⇒ 这一层仍按 §0 读作在该证据强度下一致）。⇒ **本轮只改台账口径、不改状态**（该条属 follow 域事项，用户 2026-10-24 的「不落地」决策仍在）。核对日期 2026-10-30。|
| `S1-FOLLOW-002` | MaxConns 闸门粒度 | —— | **D**（已签字偏离 ⇒ 可不实现） | ✅ **已销号**（`SD-2026-10-25-01`）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **✅ 已销号（`SD-2026-10-25-01`）** ⇒ **现行态 = ✅ 维持已销号**。可复算指针（补本行 PTRLESS 缺口）：**签字档在位** ⇒ `docs/Intentional_Deviations/global/common-tails.md:11` = `## SD-2026-10-25-01：`MaxConns` 闸门作用域 —— RAG **全局一个**，Go **每路由一个** ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**`（`:118` = 唯一别名 `SD-2026-10-25-01@global`）⇒ **签字仍有效 ⇒ 维持销号**（⚠️ 本条与 follow 域 `DRIFT-FOLLOW-008` 是**同一条**，编号不同而已）。核对日期 2026-10-30。|
| `S1-FOLLOW-003` | 纯 gRPC 进程关闭路径抛 `NoClassDefFoundError` | P2 | **B** | 🔴 OPEN · **派 G4**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 派 G4** ⇒ **现行态 = ✅ 维持「共享层已落码修复」⇒ 本条可销号**（**本轮改判**）。依据：**RAG** `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:34` = `⇒ 关闭路径<b>不碰</b> {@code HttpMiddleware} ⇒ 不再有 {@code NoClassDefFoundError}。` ⇒ **修法是结构性的**：本类在 `com.rag.common.spring` ⇒ **rpc 进程一律不扫 `com.rag.common`** ⇒ **纯 gRPC 进程里这个 Bean 根本不会被注册** ⇒ 关闭路径不加载 servlet 类；`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:208` = `public static void shutdown() {` · `:209` = `HTTP_EXECUTOR.get().shutdownNow();` ⇒ **该静态方法本身不碰 servlet**。**守门在位** ⇒ `service/common/src/test/java/com/rag/common/spring/HttpPoolShutdownWithoutServletGateTest.java:43`（`剔掉 servlet-api 的 classpath 上，关闭钩子必须干净返回（不抛 NoClassDefFoundError）`）+ `:66`（`doesNotContain("NoClassDefFoundError")`）· `service/common/src/test/java/com/rag/common/observability/ObservationPoolSelfHealingGateTest.java:32`（`修之前，纯 gRPC 进程的 @PreDestroy 先抛 NoClassDefFoundError`）⇒ **问题在共享层已修 + 有守门 ⇒ 本条可销号**（⚠️ **本轮未复跑**那两条守门 ⇒ 按 §0 读作在该证据强度下一致）。核对日期 2026-10-30。|
| `S1-FOLLOW-004` | follow 域无 compose 编排 | P2 | **B** | 🔴 OPEN · 域外上报<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 域外上报** ⇒ **现行态 = 🔴 维持仍开着**（**本轮重核：根 compose 里确实没有 follow 服务定义**）。可复算指针（补本行 PTRLESS 缺口）：`docker-compose.yaml`（**根级**）⇒ 本轮 `grep -n "follow" docker-compose.yaml` ⇒ **0 命中** ⇒ **根级 compose 确实没有 follow 域的服务定义** ⇒ **只能裸 jar 起** ⇒ 维持 OPEN + 维持域外上报。核对日期 2026-10-30。|
| `S1-FOLLOW-007` | 基线仍把已签字偏离记成开放缺陷 | **P1** | **A**（派单错误会改坏共享层） | ✅ **本轮销号**（2026-10-31 · G1 lane）—— **危害已消解 ⇒ 不再阻断阶段 1**<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 上报收口** ⇒ 原写那版的「现行态」写的是 **🔴 维持仍开着**（**当时成立** —— 本条落笔 **02:12:47**，早于共享面给基线加复核戳的 **02:15:45**）。可复算指针（补本行 PTRLESS 缺口）：**基线文件** `docs/PhaseGoal-judge.md:192`（`follow-api` 的 A-2 行）⇒ 其「残留」列仍写着 `DRIFT-FOLLOW-008`（MaxConns 闸门粒度）作为**跨组件残留** ⇒ 而该条**已由 `SD-2026-10-25-01` 签字销号**（`docs/Intentional_Deviations/global/common-tails.md:11`）⇒ ⇒ **「基线仍把已签字偏离记成开放缺陷」的危害已消解** —— 机器生成物该行的「开放项归属」列现为 `**→ 非开放（已签字 · 不是缺陷）**` ⇒ **不再产出「→ G4」派单指令**（判据 = 该校验器的「不计入本行的开放残余差异 / 有意偏离…不是缺陷」分支，**2026-10-31 实测 EXIT=0**）⇒ ⇒ **现行态 = ✅ 已销号**；⚠️ **【2026-10-31 订正 · G1 lane】本条属「**原写当时成立 · 事后被共享面订正**」第三形态** ⇒ ⇒ **不是「原写就错」**（与「事后位移」「原写就错」是三件事 ⇒ 判定须逐条实测**落笔时刻**）⇒ ⚠️ **残留 = 基线那格的字面原值仍在 —— 且【不可改】**（共享面刻意「原值保留」⇒ ⇒ 派工判据 `route_open()` **正认**「不计入本行的开放残余差异」这句 ⇒ ⇒ **把它改掉反而让判据失配** ⇒ ⇒ **不是待办，勿再提清它**）⇒ ⇒ **另：事实层已关、定级层未翻** —— 派生层里本条仍是 `stage=A` / `blocking=是`（`blocking` 取自显式定级叠加层、不随 `gap` 自动翻转 ⇒ ⇒ 按既有先例只关事实、不擅自翻定级 ⇒ ⇒ 定级复判并入 Jason 那一批）⇒ ⇒ **本条不再算「事实未闭合」，但仍挂在阶段一阻断清单上**。核对日期 2026-10-31。|
| `S1-FOLLOW-008` | `check-surface-parity.py` 两个静默假绿口子 | P2 | **B** | 🔴 OPEN · 上报（共享工具）<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 上报（共享工具）** ⇒ **现行态 = 🔴 维持仍开着**（**工具在位；但「两个静默假绿口子」本轮未逐条复现**）。可复算指针（补本行 PTRLESS 缺口）：`tools/difftest/check-surface-parity.py`（**文件在位，23215 字节，2026-10-29 03:27**）⇒ **工具存在**；⚠️ **「两个口子」的具体位置本轮未逐条定位、未复跑** ⇒ 这一层按 §0 读作在该证据强度下一致 ⇒ **维持 OPEN + 维持上报**。核对日期 2026-10-30。|
| `S1-FOLLOW-009` | ~~5 个 api 模块缺 ryuk-disabled~~ | P2 | **B** | ✅ **已闭合（2026-11-01 · G1 lane 回写）** —— **本条前提不成立 ⇒ 证伪关闭**（非「已修」，是**派单事实从一开始就不存在**）<br>⚠️ **2026-11-01 复核（G1 lane · 逐条实测，不采信转述）**：① **那 5 个 api 模块根本不用 Testcontainers** ⇒ 不需要 ryuk-disabled —— `service/article/article-api` · `service/comment/comment-api` · `service/hot/hot-api` · `service/like/like-api` · `service/task/task-api`：**pom 里 testcontainers 依赖 = 0** ＋ **`src/test` 里 `@Container`／`GenericContainer`／`@ServiceConnection`／`RedisContainer`／`PostgreSQLContainer` 痕迹 = 0 个文件** ＋ `testcontainers` 关键字 = 0 命中 ⇒ **不拉 ryuk 镜像 ⇒ 不需要禁 ryuk**；② **原复核的 grep 口径锚错了文件类型** —— 原写 `grep -rln "TESTCONTAINERS_RYUK_DISABLED" service/ --include='*.yml' --include='*.yaml'` ⇒ **0 命中**，但 **ryuk 全仓配在 `pom.xml` 的 surefire `systemPropertyVariables`**（实测 **16 个模块**命中，如 `service/follow/follow-api/pom.xml:223` · `service/follow/follow-rpc/pom.xml:297`）⇒ **yml/yaml 全仓零命中是正常现象**（没有模块会那样配）⇒ ⇒ **原判「那 5 个模块均无 ⇒ 派单事实仍在」= 把「配在 pom」误读成「没配」** ⇒ ⇒ **本条自 2026-10-30 起就是假红** ⇒ **销账**。<br>⚠️ **保留重开触发条件**：若将来某个 api 模块**真的引入** Testcontainers，则「该模块 pom 的 surefire 是否注入 `TESTCONTAINERS_RYUK_DISABLED=true`」重新成为检查项（**锚 pom 的 surefire 段，不是 yml**）。核对日期 2026-11-01。 |
| `S1-FOLLOW-010` | 共享层 404 body 字节数注释写成「20」（实测 19） | **P3** | **B**（文档面） | 🔴 OPEN · 上报 G4<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 上报 G4** ⇒ **现行态 = ✅ 维持「已订正」⇒ 本条可销号**（**本轮改判**）。依据：**RAG** `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:80` = `* 所以<b>带一个尾随换行</b>，全长 {@code 19} 字节` ⇒ **注释已改成 19**；`service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83` = `* <p>🔴 <b>2026-10-29（R3 轮）订正</b>：本 javadoc 原写「全长 {@code 20} 字节」⇒ <b>错</b>` ⇒ **订正说明本身就在位**（`:84` = `⇒ 是 {@code GoStyleRouterErrorResponsesContractGateTest#notFoundMatchesGoByteShape}` · `:85` = `按字节数组实测抓出来的（{@code hasSize(19)}）⇒ ⇒ <b>字节数必须实测，不能手数</b>`）⇒ **错数已被订正 ⇒ 可销号**（⚠️ 本轮**未复跑**那条字节实测守门）。核对日期 2026-10-30。|
| `S1-FOLLOW-011` | like 域真 Tomcat 测试沿用同一错数（「20」） | **P3** | **B**（文档面） | 🔴 OPEN · 上报 G2<br>⚠️ **2026-10-30 真实性复核（users lane）**：原判 **🔴 OPEN · 上报 G2** ⇒ **现行态 = ✅ 维持「已订正」⇒ 本条可销号**（**本轮改判**）。依据：**RAG** `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java:46` = `* ⇒ body = "404 page not found\n"，含尾随换行，全长 19 字节` · `:47` = `*          （"404 page not found" = 18 字符 + 尾随 '\n' = 1 ⇒ 共 19；` · `:48` = `*            ⚠️ 2026-10-29 订正：原写「20 字节」是**错的** ⇒ 已由 G1 跨域发现并上报，` ⇒ **like 域那处错数注释也已订正为 19** ⇒ **可销号**（⚠️ 本轮**未复跑**该真 Tomcat 测试）。核对日期 2026-10-30。<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/exp-difftest-grpc-user9.sh` ⇒ **EXIT=1**（2026-10-30 03:53:22 · 20s）—— 🔴 **探针判定为「不一致」**；覆盖断言：==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**） ⏐ ==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源） ⏐ ==> [3/5] 构建（Go 二进制 + RAG jar） ⏐ ==> [4/5] 起两套 user-rpc（Go :19005 / RAG :59211） ⏐ ==> [5/5] 编译并跑双端口对拍。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| 🆕 `S1-FOLLOW-012` | **follow 域无独立失败路径矩阵** · 基线 §九 `G-13` | **P1**（P0#2 的证据面） | **D**（已实现） | ✅ **本轮闭合**（`T11` · 8 端点 × 4 类失败 × **真 Tomcat** = **62 条**全绿）⇒ ⚠️ **P0#2 本身仍受其余 3 域未补的制约 ⇒ 不因此判成 ✅** |
| 🆕 `S1-FOLLOW-013` | **follow 域本轮新增文件自己的断言消息写着「全长 20 字节」**（`FollowApiFailurePathMatrixTest.java:417`）⇒ **躲过了 G1 第一轮自查**（模式选错 + `head -40` 截断）⇒ 已二次订正并重扫 | **P3**（仅文案） | **D**（已实现） | ✅ **本轮自修**（矩阵 62/62 绿）|
| 🆕 `S1-USERS-010` | **`admin-api` 的注释把 503 body 的字节数写成 15，断言用的是 16** —— `AdminApiRealTomcatResponseBytesTest.java:266` 写「线上是 Content-Length: **15**」，而紧邻的断言用 `GO_TIMEOUT_CONTENT_LENGTH = "Request Timeout".length() + 1` = **16** ⇒ 注释**漏算了 `fmt.Fprintln` 补的那个 `\n`** ⇒ 与 follow 域的「20 vs 19」**同族**（字节数少算尾随换行） | **P3**（**仅注释**，断言本身正确 ⇒ 一直绿） | **D**（已实现） | ✅ **本轮自修并复跑**（`AdminApiRealTomcatResponseBytesTest` **3/3 绿**）|
| 🆕 `S1-USERS-011` | 🔴 **本轮的一次误判（已撤回）**：把 Go 真源乱码字面量当「**新发现 + 需新签字**」⇒ 差点去登记一条**已签字**的偏离 | **P3**（**零代码影响** · 未改任何代码） | **D**（已澄清） | ✅ **已撤回** —— 依据：`docs/Pitfalls/users/user/rpc.md:127` **P-05**（明写「已登记为 **N-2（用户 2026-10-21 签字：RAG 保留正确中文）**」+「**下一个 agent 不要把它当新发现再「修」一遍**」）· admin 域同型 = `SD-2026-10-23-07` ⇒ **不需新签字、不动代码**；本轮 43 场景对拍的 4 处差异**就是这条签字项** ⇒ **复现，不是缺陷** |
| 🆕 `S1-USERS-012` | **基线措辞订正**：基线 P0#1 现状栏 + 机器生成的 `docs/PhaseGoal/g1.md` §3 把「双侧活体未取证」读成**含 users 域** ⇒ 与事实不符（users 域探针**在版本控制内**，且本轮**实跑复现**） | P2（**仅文档面**） | **B** | 🔴 OPEN · **上报收口**（基线 + 机器生成物）⇒ 权威口径见 §5.4bis |
| 🆕 `S1-USERS-013` | **`check-no-prod-secrets.py` 的 6 条未豁免命中**（`service/users/users-common/**` 的 `JwtUtil` 3 处 + 2 个测试类 3 处）⇒ 该脚本明写 users-common **永不可豁免** ⇒ **G4 写权面** | **P1**（**密钥泄露面**） | **B** | 🟡 **树间版本差** —— G4 分支已把 `users-common/…/JwtUtil.java` 去明文（`git diff` = **+5/−13**）⇒ **合并后应归零** ⇒ **不是本域缺陷**；**我域未豁免命中 = 0** ✅ ⇒ 合并收口回归时复验 |
| 🆕 `S1-USERS-014` | **台账陈旧引用**：`admin/rpc.md` 多处按**现在时**描述 `RpcInterceptor` 的 `deadlineEpochMs` ⇒ G4 `1f62a24`（DRIFT-COMMON-005 结案）后**该变量已移除**（改 `DeadlineState` 单调钟 + 纳秒精度）⇒ 合并后这些引用会过期 | **P3**（**仅文档面**） | **D**（已实现） | ✅ **本轮订正**（在 `admin/rpc.md` 加订正注）⇒ ⚠️ **合并后**需再核一次行号/符号名 |
| 🆕 `S1-USERS-015` | **`admin/rpc.md` 两张表：表头声明 4 列，但多数表体行是 5 格** ⇒ GFM 多渲染出一列**无名列**（表A `:1364` 起 21 行中 11 行为 5 格 · 表B `:1580` 起 27 行中 5 行为 5 格）⇒ **既存**（HEAD 已有）⇒ 本轮**自建结构校验**时顺带查出 | **P3**（**仅渲染面** · 不影响任何判定） | **D**（已实现） | 🟡 **已登记 · 本轮不修** —— 第 4 格的语义**不统一**（表A 有 3 行第 4 格有内容、表B 全部为空）⇒ **补表头名 = 猜语义** ⇒ 不猜；修法：统一第 4 格的用法后补表头列名（**收口或下一轮域内自修均可**）|

---

## 7. 冻结基线（§5.7）

| §5.7 要求 | 本轮的值 |
|---|---|
| **实际代码状态及版本标识** | 分支 `align/g1-users-follow` · 基线 `29a0bce`（= `do-my-own-gh`）· **首轮提交 `e18ef72`（38 文件 +1660/−70）** ＋ **🆕 本轮增量（`T11`）= 2 个新增源文件（`RealTomcatPort` ＋ `FollowApiFailurePathMatrixTest`）+ 1 个修改文件（`BlindPortGateTest`）+ 5 份台账** ⇒ **提交后以 commit hash 为准**（见 §7 末） |
| **构建和测试结果** | **域级回归**：`mvn … clean test` ⇒ **EXIT=0 · BUILD SUCCESS · 08:56 min（第二轮 10:01 min · 同一批代码 · 逐模块一致）· 1195 条 / 0F / 0E / 0S**（§5.1 · 🆕 较上一轮 **+62** ＝ 失败路径矩阵）· **定向复验 7 条全绿**（§5.2）＋ 🆕 **矩阵复验 62 条全绿**（§5.2.1）· **收口验收命令 15 条**：13 条 EXIT=0、1 条 EXIT=1（**既存**锚点失效）、1 条 EXIT=1（**users DDL 16 处已知差异**，非缺陷）<br>🆕 **收敛核验轮（本轮）**：**`mvn … package` ⇒ BUILD SUCCESS · 12.484 s · 8 个 jar** · **`check-jar-executable.sh` ⇒ EXIT=0 · 本 lane 6/6 模块 ✅** · **`exp-difftest-grpc-user9.sh --no-build` ⇒ 43 场景 / 39 等价 / 4 处差异（全为已签字 N-2）** · **`check-no-prod-secrets.py` ⇒ 我域未豁免命中 0**（6 条红在 `users-common` = G4 面 = 树间版本差）|
| **核心业务链路的验收结果** | `user-api → user-rpc → PG/Redis` 与 `admin-api → admin-rpc → PG/Redis` 两条链路的**域内实测**沿用域台账（`user/rpc.md` §6.4 / `admin/rpc.md` §15，L3/L4）<br>🆕 **本轮新增**：`user-rpc` 的**双侧活体对拍已实跑**（43 场景，与 2026-10-24 基线逐条同形 ⇒ **证据强度提升**）；`admin-rpc` 的对拍脚本**在库但本轮未跑** ⇒ 按原日期读（§5.4bis）|
| **P0 / P1 / P2 最终状态** | **P0**：域内 ✅ 8/9 · 项目级 🔴 1 项（`S1-USERS-001`）· **P1**：✅ 至少 5 个机制闭环（RPC 超时 / 事务并发 / 鉴权与黑名单 / 错误码契约 / proto wire 契约）· **P2**：6 项登记（其中 1 项已销号、1 项已修）<br>🆕 **P0 九条逐条对照见 §6.0.1**（本轮实测重判）⇒ **域内主责/协作项全部有证据且无新增缺陷**；**域内唯一未达标 = `follow-rpc` 的 `DRIFT-FOLLOW-006`**（C 类阶段 2 项，待用户重新拍板）⇒ **lane 判定维持 🟡** |
| **全部已知缺陷和未验证风险** | §3 差异清单（`S1-USERS-001~005` + `S1-FOLLOW-001~009`）＋ §6.1 的 `S1-USERS-009` + §6.2 六条例外 + 域台账 + [`vs-common.md`](vs-common.md) 9 轴 + 基线 §九 20 条缺口（**G1 相关：G-1 / G-4 / G-6 / G-8 / ~~G-13~~ / G-19** ⇒ 🆕 **`G-13` 在 follow 域已闭合**，其余 3 域归 G2 / G3） |
| **完整的差异与暂缓项清单** | 本文件 §3（users 5 条 + follow 9 条，**每条 11 项齐全**） |
| **阶段 2 任务及其依赖** | ① `S1-FOLLOW-001` 指标出口方案（**待用户重新拍板**，依赖：全仓 rpc 统一方案）② `S1-USERS-004` 事务边界上移（依赖：阶段 1 不动已验证边界）③ `S1-USERS-001` 说明书（依赖：**收口**）④ `S1-FOLLOW-007/008/009`（依赖：**收口 / G2 / G3**）⇒ ⚠️ **【2026-10-31 订正 · G1 lane】其中 `S1-FOLLOW-007` 已销号**（派单危害已消解 ⇒ 不再是待办）⇒ ⇒ 余 `S1-FOLLOW-008/009` 两条⑤ 🆕 基线 `G-13` 的其余 **3 域**（`article` / `task` / `hot`）补失败路径矩阵（依赖：**G3 / G2**，本轮已把 follow 域那一份做完，**矩阵形态可直接复用**）⑥ 🆕 `S1-FOLLOW-010` / `S1-FOLLOW-011` 两处错数注释订正（依赖：**G4 / G2**） |
| **尚未处理的原始业务行为** | ① `follow-rpc` 的 `/metrics` 出口（Go DevServer 有 · RAG 无）② MaxConns 的 per-route 语义（**用户已签字不修** ⇒ 不是「尚未处理」，是「决定不处理」）③ Go 侧 `os.ExpandEnv` 的 fail-fast 连库语义（RAG 仍保留 fallback） |
| **下一次执行应从哪里开始** | ① **收口**：`S1-USERS-001` 说明书 + 基线 `S1-FOLLOW-007` 订正（**A 类，阻断阶段 1**）⇒ ⚠️ **【2026-10-31 订正 · G1 lane】`S1-FOLLOW-007` 已销号** —— 机器生成物的「开放项归属」列已改为 `→ 非开放（已签字 · 不是缺陷）` ⇒ ⇒ **派单危害已消解 ⇒ 不再是阻断项**（残留 = 基线那格的字面原值仍在 ⇒ 归**合并收口执行者**做字面消解 ⇒ 危害已无）⇒ ⇒ 上文「A 类，阻断阶段 1」**只作原写留档**② **G4**：收 `S1-FOLLOW-003` + `DRIFT-USERAPI-013` 的 ②③ 半边 ＋ `S1-FOLLOW-010` ③ **G2/G3**：5 个 api 模块补 ryuk-disabled ＋ `S1-FOLLOW-011` ＋ **基线 `G-13` 的其余 3 域矩阵**（`article` / `hot` ⇒ G3；`task` ⇒ G2 ⇒ **形态可直接抄 follow 这份**）④ **G1 自身**：若用户改主意重开 `S1-FOLLOW-001` ⇒ 则 `follow-rpc` 需给 rpc 组件开独立 management 端口 ⇒ **那是全仓方案，不是 G1 单方面能定的** |

---

## 8. 证据强度声明

| 结论 | 证据强度 | 说明 |
|---|---|---|
| P0#1 / #2 / #3 / #4 / #6 / #7 判定 | **L4**（活体） | 真 PG / 真 Redis / 真 Nacos / 真进程 / 真 Tomcat；Go↔RAG 逐字对照 + 双侧对拍<br>🆕 **本轮：`user-rpc` 的双侧活体已实跑复现**（43 场景）⇒ 该条**证据强度提升**；**其余 4 条**（`run.sh`/`matrix.py`/`admin-errors`/`redis-blacklist`）**本轮未跑** ⇒ **沿用台账原日期**，**不提升**（§5.4bis）|
| 🆕 **本轮新增：`user-rpc` 双侧活体对拍复现** | **L4**（**双侧活体** · 真进程 + 真 PG + 真 Redis） | **43 场景 / 39 等价 / 4 处差异**（4 处**全部**是已签字的 **N-2** Go 源码乱码文案）⇒ **无新漂移**；与 2026-10-24 基线**逐条同形** ⇒ **独立复现**<br>⚠️ **探针与 runner 均在版本控制内**（`git ls-files` 命中）⇒ 基线 §1.1 **R4 的 ⑦⑧ 均成立**（⑦ 可复算 · ⑧ 变异 4/4 记录在 `users/user/rpc.md` §9.1.5）⇒ **这是本轮对「用户域双侧活体」的结论升级点** |
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
| R2 | `find service -path '*/src/main/resources/application.y*ml' -not -path '*/target/*' -exec grep -hoE '\$\{POSTGRES_PASSWORD:[^}]*\}' {} + \| sort \| uniq -c` | ⚠️ **2026-10-30 16:5x 更新（原写「`10 ${POSTGRES_PASSWORD:password}`，覆盖 9 个模块」⇒ 那是 G1 面 3 处取消 fallback **之前**的数 ⇒ 照跑会得到不同的数 ⇒ 陷阱已修）**：**`7 ${POSTGRES_PASSWORD:password}` / 6 个模块**（like · comment · message · favorite · task · article[2 处]）⇒ **G1 面 3 处已按 Go 同形取消**（user-rpc · admin-rpc · follow-rpc）⇒ 残余 7 处属 G2/G3 面<br>⚠️ **不要**写成 `grep -rhoE … \| grep -v target` —— `-o` 输出**不带文件名** ⇒ `grep -v target` **滤不掉** `target/` 下的旧副本（2026-10-28 实测：漏滤时读出 `2×Sea-TryGo` 的**假分裂**） |
| R3 | `grep -n 'port:' service/follow/follow-rpc/src/main/resources/application.yml` | `${FOLLOW_RPC_PORT:8082}`（T3 后） |
| R4 | `wc -c README.md` | ~~**7**（`S1-USERS-001` 的证据）~~ ⇒ 🟢 **2026-10-29 订正：现为 9815**（完整说明书）⇒ **本行期望值已变更**，复算时以现值为准 |
| R5 | `grep -nE '\.env' .gitignore \| wc -l` | ~~**0**（`S1-USERS-003` 的证据）~~ ⇒ 🟢 **2026-10-29 订正：现为 4**（3 条规则 `.env` / `.env.*` / `!.env.example` ＋ 1 行说明注释）⇒ **本行期望值已变更**；只看规则条数请用 `grep -cE '^!?\.env' .gitignore` = **3** |
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
| **R16** | `mvn -o -B -DskipTests -Dmaven.test.skip=true -pl <本 lane 8 模块> -am package` | `BUILD SUCCESS` · **12.484 s** · **8 个 jar 产出**（R17 的前置） |
| **R17** | `bash tools/difftest/check-jar-executable.sh` | `EXIT=0` ⇒ **本 lane 6 个 api+rpc 模块全 ✅**（`follow-api` 80M · `follow-rpc` 64M · `admin-api` 80M · `admin-rpc` 79M · `user-api` 80M · `user-rpc` 79M）<br>⚠️ **其余 14 个 api/rpc 模块报「没有产物（未构建？）」** —— 那是**别的 lane 的模块**，本树没构建 ⇒ **不计失败也不计通过**（基线 §九 `G-10` 的已知口径）|
| **R18** | `bash tools/difftest/exp-difftest-grpc-user9.sh --no-build` | **43 场景 / 39 等价 / 4 处差异** ⇒ 4 处**全是 N-2 已签字的 Go 乱码文案**（`鐢ㄦ埛涓嶅瓨鍦?`×3 · `澶村儚鍘嗗彶涓嶅瓨鍦?`×1）⇒ **无新漂移**<br>⚠️ **脚本自带容器/库/端口**（`uudifftest-postgres:55434` / `uudifftest-redis:56382` / `uu_go_db` / `uu_rag_db` / 19005 / 59211）⇒ **不碰共享 `go_db`/`rag_db`** ⇒ 可与其他 lane 并行 |
| **R19** | `git ls-files tools/difftest/exp-difftest-grpc-user9.sh tools/difftest/grpc-user9-dual-port-runner.java` | **两行都命中** ⇒ **探针与 runner 在版本控制内** ⇒ 基线 §1.1 **R4 的 ⑦**（探针在库且现在可复算）成立（⑧ 变异 4/4 见 `users/user/rpc.md` §9.1.5）|
| **R20** | `ls -l /tmp/probe.sh /tmp/probe_data.sh /tmp/probe_ext.sh` | **3 个都不存在**（`/tmp/gofollow` 与 `/tmp/userstub` 现为空目录）⇒ **follow 域 52/52 探针的源头已消失** ⇒ 不可复算（`S1-FOLLOW-010` 口径，见 follow 档）|
| **R21** | `git show align/g4-service-common:tools/difftest/check-no-prod-secrets.py > tools/difftest/check-no-prod-secrets.py && python3 tools/difftest/check-no-prod-secrets.py; rm -f tools/difftest/check-no-prod-secrets.py` | `EXIT=1`（**6 条未豁免命中，全部在 `service/users/users-common/**`**）⇒ **我域（G1 白名单内）未豁免命中 = 0** ✅；已豁免 **91** 条（我域 25 条）<br>⚠️ **脚本落在 `tools/`（G4 写权面）⇒ 只可临时复制 + 跑完即删**（`mavis-trash`）⇒ **不得提交** |

---

## 10. 🟢 **开工轮（2026-10-29 03:45 · Jason 批准）**：修 §8.7 登记的 G1 **23 条**「Go 出处」真缺陷

### 10.1 开工记录与批准依据

| 项 | 值 |
|---|---|
| **批准来源** | Jason 于 **2026-10-29 03:45** 在**本 lane 会话内**明示「继续」⇒ **开工批准** |
| **批准范围** | `docs/CLOSEOUT-2026-10-29.md` §8.7 登记的 **G1 23 条**（15 版本写错 ＋ 5 越界 ＋ 3 路径）· ⑤ 批准记录由**调度员**代填（回执人**不得自批**；**代填 ≠ 代批准**） |
| **本轮改动性质** | **纯注释 / javadoc / 字符串** ⇒ **零业务码改动** ⇒ 行为面零影响（§10.6 回归实测佐证） |
| **改动面** | **8 个 Java 文件 / 25 处引用点**（23 条缺陷 ＋ 2 处同族「版本续写」引用，见 §10.3） |
| **不在范围（明确边界）** | ① 阶段二（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7）② `admin_invite.update_time` 不推进（回执 ③-4 · **未纳入**）③ `S1-FOLLOW-001` 指标出口（**待用户重新拍板**）④ 共享面（`tools/**` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）⇒ **只登记上报，不改** |

### 10.2 修法：**不是「只改版本号」**（§8.7 的批量规则不足）

🔴 **关键事实：v1.31.1 与 v1.31.2 的行号不同** ⇒ §8.7「只是引到另一份副本」的描述**不足** ⇒ 必须在 v1.31.1 里**重新定位**。

真源 `Sea-RideTheWind_golang/go.mod:28` = **`gorm.io/gorm v1.31.1`**（`:27` = `gorm.io/driver/postgres v1.6.0`）。
模块缓存实测行数：`schema/field.go` **1004**（v1.31.2 = 1011，**Δ=7**）· `scan.go` **369**（两版同，**Δ=0**）· `callbacks/update.go` **313**（v1.31.2 = 318，**Δ=5**）。

| 被引内容 | v1.31.2 | v1.31.1（真源 pin） | 逐字对照结论（2026-10-29 实测） |
|---|---|---|---|
| `case **int64:` setter（`SetInt(**data)`） | `schema/field.go:616-622` | `:616-622` | **逐字相同** ⇒ 只改版本号 |
| `db.AddError(rows.Scan(values...))` | `scan.go:153` | `:153` | **逐字相同** ⇒ 只改版本号 |
| `prepareValues` 的 `reflect.New(reflect.PointerTo(field.FieldType))` | `scan.go:21` ⚠️ **指向的是 `}`** | `scan.go:19` | 🔴 **原引用在 v1.31.2 里就指错了行** —— 两版 `:21` 都是闭合大括号 ⇒ §8.7 的「只改版本号、行号不动」会**留下一个仍指错的引用** ⇒ 本轮按**内容锚定**（§8.3 第 ③ 条）改到 `:19`（两版真身语句**都在 `:19`**） |
| `field.NewValuePool = poolInitializer(reflect.PointerTo(field.IndirectFieldType))` | `field.go:1009` | `field.go:1002` | v1.31.2（1011 行）里 `:1009` **合法** ⇒ v1.31.1（1004 行）里**越界** ⇒ 重新锚定 `:1002` |
| `Updates(map)` / `Update(col, val)` 的 autoUpdateTime 消费点 | `callbacks/update.go:243-259` | `callbacks/update.go:238-254` | **偏移 −5**（v1.31.2 多 5 行）⇒ **行号也要改**（调度员 §8.7 表已独立算出同值 `:238-254`） |
| `Updates(struct)` 的 autoUpdateTime 消费点 | `callbacks/update.go:279-281` | `callbacks/update.go:274-276` | **偏移 −5** ⇒ **行号也要改** |

⇒ ⇒ **两条对 §8.7 的订正（采信）**：① 「只改版本号」这条批量规则在 **`scan.go:21`** 上会留下错引用 ⇒ 本轮改 `:19`；② `callbacks/update.go` 两段区间在 v1.31.1 里**不是**逐字相同 ⇒ 必须重新定位。

### 10.3 逐条修复记录（**8 文件 / 25 处引用点** / 8 组修法）

| 组 | 分类 | 处数 | 引用点（**HEAD 行号** · diff 可定位） | 原引用 | 现引用 | 真源依据 |
|---|---|---|---|---|---|---|
| **①** | 版本写错 | **6** | `users/user/rpc/domain/UserEntity.java:61` · `users/user/rpc/service/UserDomainService.java:190,257,447` · `users/user/rpc/UserRpcLoginNullStatusReadsAsZeroTest.java:51` · `users/user/rpc/service/UserDomainServiceTest.java:696` | `gorm[.io/]@v1.31.2/schema/field.go:616-622` | `…@v1.31.1/schema/field.go:616-622` | v1.31.1 `schema/field.go:616-622` = `case reflect.Int, …Int64:` → `case **int64:` → `SetInt(**data)`（**逐字相同**） |
| **②** | 版本写错 | **2** | `UserEntity.java:86` · `UserRpcLoginNullStatusReadsAsZeroTest.java:35` | `gorm@v1.31.2/scan.go:153` | `gorm@v1.31.1/scan.go:153` | v1.31.1 `scan.go:153` = `db.AddError(rows.Scan(values...))`（**逐字相同**） |
| **③** | 版本写错 ＋ **行号订正** | **4** | `UserEntity.java:64` · `UserDomainService.java:192` · `UserDomainServiceTest.java:699` · `UserRpcLoginNullStatusReadsAsZeroTest.java:44` | `gorm[.io/]@v1.31.2/scan.go:21` | `gorm[.io/]@v1.31.1/scan.go:19` | v1.31.1 `scan.go:19` = `values[idx] = reflect.New(reflect.PointerTo(field.FieldType)).Interface()`；⚠️ **原 `:21` 在两版都是 `}`** ⇒ 见 §10.2 |
| **④** | 版本写错 ＋ **行号订正** | **3** | `users/admin/rpc/service/AdminDomainService.java:1312` · `users/admin/rpc/AdminRpcUpdateTimeAdvanceWiringTest.java:42` · `users/admin/rpc/service/AdminSemanticDriftRegressionTest.java:411` | `gorm@v1.31.2/callbacks/update.go:243-259` | `gorm@v1.31.1/callbacks/update.go:238-254` | v1.31.1 `:238` = `if field.AutoUpdateTime > 0 && value[field.Name] == nil && …` → `:254` = 闭合（**逐字对应，偏移 −5**） |
| **⑤** | 版本写错 ＋ **行号订正**（**版本续写引用**） | **2** | `AdminDomainService.java:1312`（下一行 `:1313`）· `AdminRpcUpdateTimeAdvanceWiringTest.java:42` | `{@code :279-281}`（**续写**上一行的 v1.31.2 文件） | `AdminDomainService.java` ⇒ `gorm@v1.31.1/callbacks/update.go:274-276`；`AdminRpcUpdateTimeAdvanceWiringTest.java` ⇒ `{@code :274-276}`（续写同一文件） | v1.31.1 `:274` = `if v, ok := selectColumns[field.DBName]; …` → `:275` = `value, isZero := field.ValueOf(…)` → `:276` = `if !stmt.SkipHooks && field.AutoUpdateTime > 0 {`（**与 v1.31.2 `:279-281` 逐字对应**） |
| **⑥** | **行号越界** | **5** | `UserEntity.java:65` · `UserDomainService.java:192,447` · `UserDomainServiceTest.java:699` · `UserRpcLoginNullStatusReadsAsZeroTest.java:45` | `schema/field.go:1009` | `schema/field.go:1002`（部分补上模块前缀 `gorm@v1.31.1/`） | v1.31.1 `schema/field.go:1002` = `field.NewValuePool = poolInitializer(reflect.PointerTo(field.IndirectFieldType))`；⚠️ 该文件**只有 1004 行** ⇒ 原 `:1009` 越界 |
| **⑦** | **路径不精确** | **2** | `users/admin/rpc/service/AdminDomainService.java:439` · `users/admin/rpc/service/AdminSemanticDriftRegressionTest.java:340` | `internal/logic/admin/createadminlogic.go`（**多写了 `admin/`**） | `service/user/admin/rpc/internal/logic/createadminlogic.go` | 真身 = `Sea-RideTheWind_golang/service/user/admin/rpc/internal/logic/createadminlogic.go`（**126 行** · `:44-45` = 两行 `strings.TrimSpace` · `:44-46` 有效） |
| **⑧** | **路径不存在** | **1** | `users/admin/api/service/AdminCreateService.java:20` | `service/user/admin/api/internal/logic/admin/createadminlogic.go` 的 `CreateAdminLogic`（**该文件在 Go 仓根本不存在**） | `service/user/admin/api/internal/logic/admin/createlogic.go:34` 的 `CreateLogic.Create` | 真身 = `Sea-RideTheWind_golang/service/user/admin/api/internal/logic/admin/createlogic.go`（**61 行** · `:34` = `func (l *CreateLogic) Create(req *types.CreateAdminReq) (resp *types.CreateAdminResp, code int) {`） |
| | | **25** | **8 个文件** | | | |

🆕 **同时修掉的两处「断言本身错误」**（只改行号会留下错事实 ⇒ AGENTS.md §3.3.3「先改/删错误断言」）：
① `UserEntity.java` ＋ `UserRpcLoginNullStatusReadsAsZeroTest.java` 原文断言「`prepareValues` 与 `NewValuePool` **都是** `poolInitializer(...)`」⇒ **错** ⇒ 改为「`prepareValues` 用 `reflect.New(reflect.PointerTo(field.FieldType))`；`NewValuePool` 用 `poolInitializer(reflect.PointerTo(field.IndirectFieldType))` ⇒ 两者都让 dest 是 **int64**」；
② `AdminDomainService.java:1313` 的 `{@code :279-281}` **续写**引用原会随上一行版本漂移 ⇒ 本轮补上完整模块前缀。

### 10.4 判据（**可判定类**）与它的边界

⚠️ **判据脚本未落仓 ⇒ 用它得出的结论是「声明的」，不是「可复算的」**（§8.4 口径）。
脚本 = `/tmp/g1log/g1-anchor-verify.py`；**为什么不落仓**：`tools/**` 按机器守门 `check-wave-lane-ownership.py` 的口径**无 lane 拥有**（§8.7 G4 回执订正）⇒ 共享面 ⇒ **本 lane 只登记、不写** ⇒ **建议收口执行者把它提升为 `tools/difftest/check-*.py`**（⚠️ 提升则**必须同时改 `docs/PhaseGoal-judge.md` §6.3**，否则立刻制造 **B14**）。

| 判据要素 | 实现 |
|---|---|
| **输入探针先行 ＋ 硬失败** | 模块缓存 / GOROOT / Go 真源仓 / `go.mod` 任一不可见 ⇒ `sys.exit(2)` ⇒ **绝不静默降级成「0 条 = 一致」**（否则同一份代码两次跑出两个结论） |
| **版本 pin 从 `go.mod` 现读** | 不写死版本 ⇒ 真源改版即自动跟随 ⇒ 不会拿着旧 pin 判出「版本不符」的假红 |
| **可判定类 ①** | **版本不符**：引用写的模块版本 ≠ 真源 `go.mod` 的 pin |
| **可判定类 ②** | **行号越界**：被引行 > 该版本该文件的总行数 |
| **可判定类 ③** | **内容锚定**：被引区间**必须逐字包含**注释所声称的构造（7 条锚点，逐条实测） |
| **可判定类 ④** | **路径不存在**：`service/` / `proto/` 开头的**完整仓内路径**在 Go 真源仓里不存在 |
| **可判定类 ⑤** | **路径不精确**：裸路径在 Go 仓 ＋ 已 pin 模块缓存里**基名唯一** ⇒ 真身存在，但引用**不是**它的段边界后缀 |
| **模块缩写名映射** | 引用里写 `go-zero` / `sdk` / `golang-jwt/jwt/v4` 等缩写 ⇒ 按**段边界后缀**在 `go.mod` pin 表里**唯一**匹配 ⇒ 匹配不唯一 ⇒ **跳过**（不报红） |

**双向验证（判据自身要能自证）**：

| 方向 | 结果 |
|---|---|
| **当前代码**（本轮修完） | `users` **可判定 339 条 ⇒ OK 327 · FAIL 12**；`follow` **可判定 80 条 ⇒ OK 79 · FAIL 1** ⇒ **批准范围内 23 条已清零**（12 + 1 条全部 = §10.5 的**新发现**，非本轮范围） |
| **反测**（HEAD 旧引用，`git show HEAD:<file>` 还原 8 个改动文件） | **FAIL 20** = **15 版本不符 ＋ 5 行号越界** ⇒ 与 §8.7 的「G1 = 15 ＋ 5」**逐条吻合** ⇒ **判据有牙齿**（不是恒真） |

⚠️ **判据的两条已知失效模式（必须随结论一起读）**：
① **机器判不了的量级**：`users` **304 条** ＋ `follow` **245 条** 跳过（模块相对路径 / 裸文件名的 B 桶）⇒ ⇒ **不得**据此宣称「我域零错」，只能写「**在可判定口径下**未发现错误」；
② **假阳性簇已踩并已修**：`postgres.go` / `redis.go` 这类基名在 **Go 仓与模块缓存各有一份** ⇒ 若基名索引只扫 Go 仓，就会撞上同名不同内容的另一份文件 ⇒ **判成假红**（= §8.7 记录的 `postgres.go` 19 条 / `goka` 10 条 / `processor.go` 4 条那个簇的同源坑）⇒ **基名索引必须含全部已 pin 的模块缓存**；修完这三条后当前代码 FAIL 数从 50 降到 12（其余 38 条全是假阳性）。

### 10.5 🟡 **本轮新发现（同族 · 不在批准范围 · 未修）**

⚠️ **为什么没修**：① 不在 Jason 批准的封闭集合（**23 条**）内；② 其中 2 条**必须做语义判定**才能改（`③` 有**两个同名候选**、`⑧` 真身未定）⇒ **改错 = 造出一条新的错引用**，比现状更坏；⇒ **只登记，不改**。

| # | 引用处 | 现引用（**在 Go 仓不存在**） | 真身候选 | 备注 |
|---|---|---|---|---|
| 1 | `users/admin/api/service/AdminCreateInviteCodeService.java:19` | `service/user/admin/api/internal/logic/admin/createinvitecodelogic.go` | `service/user/admin/**rpc**/internal/logic/createinvitecodelogic.go` | 与 §8.7 ⑧ 同型（api 侧引了 rpc 侧的文件） |
| 2 | `users/admin/api/service/IdentityResolver.java:16` | `service/user/admin/api/internal/identity/resolve.go` | `service/user/**user**/api/internal/identity/resolve.go` | `admin/api` 树里**没有** `identity/` 目录 |
| 3 | `users/admin/api/service/AdminDeleteUserService.java:19` | `service/user/admin/api/internal/logic/admin/deleteuserlogic.go` | ⚠️ **两个同名候选**：`service/user/admin/**rpc**/internal/logic/deleteuserlogic.go` 与 `service/user/**user**/api/internal/logic/user/deleteuserlogic.go` | **需语义判定** ⇒ 本轮不修 |
| 4 | `users/admin/api/service/AdminUpdateSelfService.java:20` | `service/user/admin/api/internal/logic/admin/updateselflogic.go` | `service/user/admin/**rpc**/internal/logic/updateselflogic.go` | 名字完全一致，只差服务段 |
| 5 | `users/user/rpc/domain/UserEntity.java:14` · `users/user/rpc/mapper/UserMapper.java:17` · `users/user/rpc/mapper/UserAvatarHistoryMapper.java:22`（3 处） | `service/user/model/user.go` | `service/user/user/rpc/internal/model/user.go` | 真身存在，只是**少了 `user/rpc/internal` 段** |
| 6 | `users/user/rpc/exception/UserExceptions.java:6` | `service/user/model/vars.go` | `service/user/user/rpc/internal/model/vars.go` | 同上 |
| 7 | `users/user/api/test/UserApiBindingSurfaceParityTest.java:59` | `service/user/user/api/internal/types.go` | `service/user/user/api/internal/**types/**types.go` | **少一层目录** |
| 8 | `users/user/api/interceptor/JwtAuthInterceptor.java:21` | `service/user/user/api/internal/middleware/jwtauth.go` | ⚠️ **真身未定** —— `user/api` 的 `middleware/` 下只有 `checkblacklistmiddleware.go` | **需先定位** ⇒ 本轮不修 |
| 9 | `users/admin/api/test/AdminApiRealTomcatResponseBytesTest.java:48` | `ind_golang/service/user/admin/api/internal/handler/admin/createhandler.go:20-21` | `service/user/admin/api/internal/handler/admin/createhandler.go`（**存在**） | 多了 `ind_golang/` 前缀 |
| 10 | `follow/follow-rpc/service/FollowUserExistenceService.java:78` | `service/user/user/rpc/.../getuserlogic.go` | `service/user/user/rpc/internal/logic/getuserlogic.go` | 引用里带 `...` 占位 |

⇒ **合计 13 处**（`users` 12 ＋ `follow` 1）· **建议下一轮一次性修完**（多数是「只差服务段/目录层」的机械映射 + 2 条需语义判定）。

🟢 **判据没误报的旁证**：`users/user/user/api/service/IdentityResolver.java:15` 引的是 `service/user/user/api/internal/identity/resolve.go` —— **该文件在 Go 仓存在** ⇒ 判据**只**报了 admin-api 那个（`service/user/admin/api/internal/identity/resolve.go` 不存在）⇒ ⇒ **不是「见 `service/` 就报错」**。

### 10.6 本轮回归与守门

| 项 | 结果 |
|---|---|
| **整域回归（第 5 轮 · 带 `clean` · 近终态）** | `EXIT=0` · **1212 条 / 0F / 0E / 0S**（**195 个 XML**）· 逐模块：`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · `follow-api` 136 · `follow-rpc` 86 · `follow-common` 37 · `common` **354**<br>⚠️ 比 §9 R12 记的 1195 **多 17 条**，**全部落在 `common`** ⇒ 那是 **G4 已合入主干**的工作（`b5673f3` / `6f39bea`）⇒ **不是本 lane 的增量** |
| **整域回归（第 6 轮 · 带 `clean` · 最终树）** | `BUILD FAILURE` · 11:40 min · **1211 条 / 0F / 3E / 0S**（195 个 XML）· `admin-rpc` 122 条/**1E** · `user-rpc` 161 条/**2E** ⇒ **3 条 error 是同一个根因**（见 §10.6.2）⇒ **不是代码缺陷** |
| **定向复验（3 个失败类 · ×3）** | **×3 全绿**（`EXIT=0`）⇒ ⇒ 按 `docs/Pitfalls/global/cross-domain.md` **PIT-GLOBAL-038** 三步法判定为**端口占用型假红**（§10.6.2） |
| **整域回归（第 7 轮 · 带 `clean` · 最终树）** | ✅ **BUILD SUCCESS** · 10:15 min · **1212 条 / 0F / 0E / 0S**（**195 个 XML**）· **与第 5 轮逐模块完全一致**（§10.6.1）⇒ ⇒ **最终树的干净口径已建立** |
| **台账守门** | `check-ledger-tables.py` / `check-ledger-selfcheck.py` / `check-ledger-count.py` / `check-wave-lane-ownership.py` / `check-agents-budget.py` ⇒ **全 EXIT=0** · `check-doc-links.py` ⇒ **PASS**（1438 个链接全可达）· 台账表格结构自建校验 ⇒ **本轮引入 0 处偏离** |

### 10.6.2 🟡 第 6 轮那 3 条 error 的定性（**端口占用型假红 · 已登记**）

🔴 **3 条 error 同根因**：`Failed to bind to address 0.0.0.0/0.0.0.0:22261` ⇒ `java.net.BindException: Address already in use`
⇒ 失败类：`com.rag.users.admin.rpc.AdminRpcLogoutClientVisibleGrpcStatusTest` · `com.rag.users.user.rpc.UserRpcLogoutClientVisibleGrpcStatusTest` · `com.rag.users.user.rpc.UserRpcSlowThresholdGateTest`（**三个都是「真 gRPC server 启动」类测试**）。

按 **PIT-GLOBAL-038** 三步法定性：

| 步 | 做什么 | 本轮结论 |
|---|---|---|
| ① **单独复跑** | `mvn -o -B -Dtest='AdminRpcLogoutClientVisibleGrpcStatusTest,UserRpcLogoutClientVisibleGrpcStatusTest,UserRpcSlowThresholdGateTest' -pl service/users/admin/admin-rpc,service/users/user/user-rpc -am test` | 🟢 **×3 全绿**（`EXIT=0`）⇒ ⇒ **假红** |
| ② **查端口被谁占** | `lsof -nP -iTCP:22261 -sTCP:LISTEN` | 复跑期间**空闲** ⇒ 占用是**瞬时**的（不是常驻系统服务）|
| ③ **端口段归属** | users 域四组件的 `RealGrpcPort` 段：`user-api` 22200–22209 · `user-rpc` 22220–22239 · `admin-api` 22240–22249 · `admin-rpc` 22260–22279 | `22261` 落在 **admin-rpc 段内** ⇒ **同段自撞**（本 lane 自己的 admin-rpc 与 user-rpc 两模块 + **G2 lane 同时在跑 Maven** ⇒ 抢同一段的端口）|

⇒ ⇒ **为什么第 5 轮全绿、第 6 轮红**：两轮**代码相同**（仅差 3 处 javadoc 行号字符串）⇒ ⇒ **不是本轮改动引起的** ⇒ 属**环境时序型**（PIT-GLOBAL-038 第 ③ 条「别的域会话在跑东西」）⇒ **本轮不动代码**（纪律第 ① 条：看到 `BindException` 先定性，**不要**直接改代码）。
⚠️ **纪律第 ④ 条（统计口径）**：失败模块的用例数在 `[ERROR]` 汇总行与 `[INFO]` 行**不一致** ⇒ 本轮 `user-rpc` 的 `[INFO]` 口径 162 → `[ERROR]` 口径 161（`UserRpcSlowThresholdGateTest` 原本 2 条，context 加载失败后计 1 条 error）⇒ ⇒ **必须用 XML 口径求和**（本轮 1211 = 1212 − 2 + 1）⇒ **不能只 grep `[INFO]`**。

### 10.6.1 第 7 轮整域回归（最终树 · 带 `clean`）

| 模块 | XML | Tests | F | E | S |
|---|---|---|---|---|---|
| `service/users/user/user-api` | 24 | 127 | 0 | 0 | 0 |
| `service/users/user/user-rpc` | 28 | 162 | 0 | 0 | 0 |
| `service/users/admin/admin-api` | 20 | 149 | 0 | 0 | 0 |
| `service/users/admin/admin-rpc` | 25 | 122 | 0 | 0 | 0 |
| `service/users/users-common` | 4 | 39 | 0 | 0 | 0 |
| `service/follow/follow-api` | 11 | 136 | 0 | 0 | 0 |
| `service/follow/follow-rpc` | 14 | 86 | 0 | 0 | 0 |
| `service/follow/follow-common` | 3 | 37 | 0 | 0 | 0 |
| `service/common` | 66 | 354 | 0 | 0 | 0 |
| **合计** | **195** | **1212** | **0** | **0** | **0** |

⇒ **与第 5 轮逐模块逐条一致**（`user-api` 127 · `user-rpc` 162 · `admin-api` 149 · `admin-rpc` 122 · `users-common` 39 · `follow-api` 136 · `follow-rpc` 86 · `follow-common` 37 · `common` 354）⇒ ⇒ **两轮之间的唯一差异 = 3 处 javadoc 行号字符串**，**测试面零变化**（与「50 行改动全为注释 · 0 行业务码」的机器核验互相印证）。

### 10.7 🟢 **追加批准项**：`admin_invite.update_time` —— 核实结论 = **已修、且已有真 PG 守门网** ⇒ **本轮不改代码**

| 项 | 值 |
|---|---|
| **批准来源** | Jason 于 **2026-10-29 04:0x 追加拍板** ⇒ 纳入本轮（调度员已改两份台账的 ⑤ 批准范围 ＋ ③ 第 4 项，并提交 `2ec9830`）|
| **事项** | `admin-rpc` 的 `admin_invite.update_time` 不推进（`admin/rpc.md:1783`）⇒ 与 `docs/Pitfalls/users/admin/rpc.md` **P-2** 同型（**`FieldStrategy.NEVER` + `LambdaUpdateWrapper` ⇒ 永不推进**；**user-rpc 修过、admin-rpc 漏修**）|
| **核实 ①：代码** | ✅ **已在** —— `AdminCreateAtomicService.java:201` = `.setSql("update_time = CURRENT_TIMESTAMP")`（W4「标记 invite 已用」那条 UPDATE，**无条件**进 SET）|
| **核实 ②：守门网** | ✅ **已有** —— `AdminRpcGoWireParityTest.createAdminAdvancesInviteUpdateTimeLikeGo()`（`:419`）= **真 PG ＋ 真 gRPC** ⇒ 真查 `select extract(epoch from update_time)::bigint from admin_invite where code = ?` **前后对比**并断言 `after > before`；断言消息直接写明「若本断言失败，说明 W4 的 setSql("update_time = CURRENT_TIMESTAMP") 被删了」|
| **核实 ③：台账** | ✅ `docs/DRIFT/users/admin/rpc.md` §2「🔴 新发现（台账**从未记录**）」已记 **✅ 已补**（`.setSql(...)` ＋ 守门测试）· 另含连带订正：「**5 处 UPDATE**」这个计数是错的 ⇒ 全组件正确总数 = **6 处**（本类 5 ＋ `AdminCreateAtomicService` 1）|
| ⇒ **本轮结论** | 🟢 **本项已闭合 ⇒ 本轮不改代码** —— 再「修」一遍等于把已修的东西重做，**只会新增噪声**（且违背 AGENTS.md §3.3.3「先改/删错误断言，再改实现」的前置条件：**这里没有错误断言可改**）⇒ 改为**补做「这条网到底有没有牙」的变异测**（§10.7.1）|

### 10.7.1 🟢 变异测：删掉 W4 的 `setSql` ⇒ 守门必须转红（证明这条网有牙）

> **为什么做这个而不是「再写一条测试」** —— 调度员要求「必须配真断言（改前必红 / 改后必绿）」。
> ⚠️ 但这条**已经有真断言**（真 PG ＋ 真 gRPC）⇒ ⇒ 正确的满足方式**不是**再叠一条冗余测试，而是**证明现有这条网会红** ⇒ 变异测试。

| 步 | 做什么 | 结果 |
|---|---|---|
| ① 变异 | 把 `AdminCreateAtomicService.java:201` 的 `.setSql("update_time = CURRENT_TIMESTAMP")` **移除**（保留语句终止符 ⇒ **仍可编译**） | ⚠️ **第一次变异失败**：把整行连同 `;` 一起注释 ⇒ **编译错误**（`需要';'`）⇒ **测试根本没跑**（surefire 报告还是上一轮的残留）⇒ ⇒ **失败的变异什么都证明不了** ⇒ 改为「保留 `;`、只移除调用」后重做 |
| ② 跑守门 | `mvn -o -B -Dtest='AdminRpcGoWireParityTest' -pl service/users/admin/admin-rpc -am test` | **EXIT=1 · BUILD FAILURE** · **0 条编译错误** ⇒ 变异**可编译** ⇒ 红的才是真红 |
| ③ 判据 | 断言**必须转红**，且红在 `createAdminAdvancesInviteUpdateTimeLikeGo` | ✅ **精确命中**：`Tests run: 5, Failures: 1` ⇒ **红的就是那 1 条**（其余 4 条仍绿 ⇒ 不是「整类崩」）；失败消息 = 「W4 标记 invite 已用时必须同时推进 update_time … **before=1577808000 after=1577808000**」⇒ **前后值相等 = 确实没推进** |
| ④ 还原 | 撤销变异 | ✅ `git diff --stat` 对该文件 **为空** ⇒ 与 `HEAD` 逐字一致 ⇒ **本轮未动它**（`grep MUTATION-PROBE` = 0 命中）|

⇒ ⇒ **结论**：🟢 **这条网有牙** —— 删掉 `setSql` ⇒ **精确一条用例转红** ⇒ ⇒ **`admin_invite.update_time` 这一项确实已闭合**（修法在、网在、网会红）⇒ ⇒ **本轮不需要改任何代码**。
⇒ ⇒ **附带的一条元教训**：**变异测试先验「变异后仍能编译」** —— 本轮第一次变异就踩了（注释掉链式调用的最后一行 ⇒ 分号也没了 ⇒ 编译失败）⇒ **「BUILD FAILURE」有两种：编译失败（变异无效）与断言失败（变异有效）⇒ 必须看失败落点**，否则会把「没跑」当成「没红」。

### 10.7.2 🟡 **同型排查**：「user-rpc 修过、admin-rpc 漏修」还有没有？（P-2 的形状 = 「一处修了、另一处漏」）

枚举两侧**全部 UPDATE 入口**（**机器枚举**：`grep -rn "update(null\|\.update(\|updateById\|@Update" …/src/main/java`）：

| 域 | 入口 | 表 | `update_time` 推进 | 守门网 |
|---|---|---|---|---|
| user-rpc | `UserMapper.updateUserById` | `users_user` | ✅ `setSql` | ✅ `UserRpcJsonTextAndAvatarProjectionTest:113-148` |
| user-rpc | `UserAvatarHistoryMapper` ×2 `@Update` | `users_user_avatar_history` | ✅ SQL 内显式 | ✅ |
| user-rpc | `UserAvatarHistoryMapper.updateUserExtraInfo` `@Update` | `users_user` | ✅ SQL 内显式 | ✅ |
| admin-rpc | `AdminDomainService:688`（`doBanUser`） | `users_user` | ✅ | ✅ `AdminRpcUpdateTimeAdvanceWiringTest` |
| admin-rpc | `AdminDomainService:730`（`doUnBanUser`） | `users_user` | ✅ | ✅ 同上 |
| admin-rpc | `AdminDomainService:1040`（`doUpdateUser`） | `users_user` | ✅ | ✅ 同上 |
| admin-rpc | `AdminDomainService:1192`（`doResetUserPassword`） | `users_user` | ✅ | ✅ 同上 |
| admin-rpc | `AdminCreateAtomicService:202`（W4） | `admin_invite` | ✅ | ✅ `AdminRpcGoWireParityTest:419` |
| **admin-rpc** | **`AdminDomainService:409`（updateSelf）** | **`users_admin`** | ✅ **有 `setSql`** | 🔴 **无守门网** ← **新发现** |

⇒ ⇒ **同型排查结论**：**行为面 9/9 全部推进 `update_time`（无漏修）**；但**守门网 8/9** ⇒ **缺 `updateSelf`（`users_admin.update_time`）那一条** —— 现存 `updateSelf` 的三个用例（`AdminUpdateSelfAndCreateAdminRegressionTest`）**只断言 `email` 列**，没有一条断言 `update_time` ⇒ ⇒ 按调度员指令（「若还有 ⇒ **登记为下一条**，别在本轮无限扩」）⇒ **登记，不在本轮补**。

🟡 **登记项 `S1-USERS-012`** —— `admin-rpc` 的 `updateSelf` 缺 `users_admin.update_time` 推进的守门网（**行为已对，缺网**）：
- **影响面**：若将来有人删掉 `AdminDomainService:406` 的 `setSql("update_time = CURRENT_TIMESTAMP")` ⇒ **没有任何测试会红** ⇒ 漂移静默回归（**这正是 P-2 的原始形状**）；
- **建议修法**：照 `AdminRpcUpdateTimeAdvanceWiringTest` 的现成形态，在 `admin-rpc` 加一条**真 PG** 用例（`updateSelf` 前后读 `extract(epoch from update_time)::bigint from users_admin` 断言推进）⇒ **不动实现**；
- **归属**：**域侧可自修**（`service/users/admin/admin-rpc/**` 在 G1 写权内）⇒ **待下一轮批准**；
- **本轮为何不做**：调度员明示「若还有 ⇒ 登记为下一条，别在本轮无限扩」。