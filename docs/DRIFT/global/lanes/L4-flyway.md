# L4 · `align/global-flyway` —— X-1 残留（三进程同库共存真 PG 验证）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **B1** 的残留做完：**「三进程并发同库共存」这个运行时形态从未验证**。

### 已完成的部分（不要重做）

第十五轮已查明并修掉：
- 7 个 Flyway rpc 模块**全部连同一个库**（默认 DSN 都是 `first_db`）⇒ **同库共存是常态，不是边缘场景**。
- `comment` / `follow` / `like` 三域缺 `spring.flyway.baseline-version: 0`
  ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表建不出来 ⇒ 服务起不来** ⇒ **已修 + 各配真 PG 守门（变异 6 条全红）**。
- `article-rpc` **逐模块判定后排除**（最低迁移版本 V8 > baseline 默认值 1）⇒ **差点误修一个不存在的缺陷**。

### 本 lane 的任务

**验证「三个进程同时起、同一个库、同一 schema」这个形态** ⇒ 现在只验过「单模块」。

## 二、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| X-1 逐条取证 | `docs/DRIFT/global/cross-domain.md` | **§二·十二**（含 ① 取证「同库共存是常态」/ ② 缺陷形态 / ③ 逐模块判定 / ④ 修法+守门 / ⑤ favorite 同款缺陷 / **⑥ 残留**） |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **B1** 行 |
| 现有账本隔离检查 | `tools/difftest/check-flyway-ledger.py` | 全文（⚠️ 它只查**表名两两不同**，**不查运行时共存**） |
| 已有真 PG 守门样板 | `service/like/like-rpc/src/test/.../LikeFlywaySharedSchemaGateTest.java`<br>`service/comment/comment-rpc/src/test/.../CommentFlywaySharedSchemaGateTest.java` | 逐字读（**真 PG 16 + 真 Flyway 引擎 + 配置取自模块自己的 yaml**） |
| Go 真源 | `Sea-RideTheWind_golang/service/<域>/**/model/*.go` 的 `AutoMigrate` | 逐字读 |

## 三、要回答的问题（不是「跑一遍绿」）

1. **三个 rpc 进程并发启动**（同一库、同一 schema `public`、三本独立账本）
   ⇒ 首张表是否都建出来了？有没有互相 baseline 掉？
2. **启动顺序无关**（谁先起都行）⇒ 六种顺序是否等价？⇒ 至少覆盖「A 先」「C 先」两端。
3. **账本表互不覆盖**：三本账本各自的 `flyway_schema_history_*` 表**都在**、**内容对**。
4. `message` / `like` / `article` 三域（原台账点名的那三个）与 `comment` / `follow` 三域
   ⇒ **哪些域的 V1 依赖 `baseline-version: 0`**？⇒ 写成矩阵。

## 四、写权范围（白名单）

**只写这些**：
- `tools/difftest/check-flyway-concurrent.sh`（**新建**，名字固定 ⇒ 别与 L3 撞）
- `tools/difftest/` 下**新**建的、按本 lane 需要的小脚本
- `docs/DRIFT/global/lanes/L4-flyway.md`（本文件）

⚠️ 若验证结论要求改某个域的 `application.yml`（比如还要补一行 baseline 配置）
⇒ **先在台账写明理由**，并**只改一个域**（其余域留给整合者或后续轮次），避免与其他 lane 抢同一批 yaml。

**⛔ 禁区**：
- `service/favorite/**` 与 `docs/*/favorite/`（**`favorite-rpc` 同款缺陷永久阻塞 ⇒ 只记账 + 告知 owner，不修**）
- `service/common/**`
- `tools/difftest/check-ddl-static.py`（L3 的）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **可重跑的脚本** `tools/difftest/check-flyway-concurrent.sh` ⇒ 非 0 退出表示有差异。
2. **结论矩阵**：哪些域可以安全并发同库、哪些不行、不行的缺什么。
3. 若发现**新的真漂移** ⇒ 登记（不修）。
4. 台账：本文件末尾追加「实测步骤 + 结果 + 结论 + 未覆盖的部分」。

## 六、验收标准

- [ ] 脚本**连跑两次结果一致**（幂等），且**会清理自己的库/容器**（不留下脏状态给别的 lane）
- [ ] 至少覆盖「三域并发 + 不同启动顺序」两种形态
- [ ] **真 PG 16**，不是 H2、不是 mock（§3.3）
- [ ] 明确写出**哪些域没验**（favorite 是必然的 ⇒ 显式说明）
- [ ] 若结论依赖某个域的 yaml 改动 ⇒ 在台账里写清「谁改了什么、为什么、影响面」

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **「单模块确证」≠「多本账能同时跑」** | 7 个模块各有独立账本表名（`flyway_schema_history_<模块>`）⇒ 撞号病根已消除，但**运行时共存从未验证** ⇒ 这正是本 lane 的存在理由 |
| **baseline 语义** | `baseline-on-migrate=true` + 非空 schema + 无账本 ⇒ Flyway 在 **version 1** 处打 BASELINE ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来 ⇒ 服务起不来 |
| **`article-rpc` 是反例** | 最低迁移版本 V8 > baseline 默认值 1 ⇒ **不受影响** ⇒ 判定必须**逐模块**做，不能套同款结论 |
| **Docker 抢资源** | 全仓并行跑会互抢容器 ⇒ 本 lane **必须用自己独立的容器名/网络**，别抢 `difftest-postgres` |
| **上轮遗留** | `docs/Pitfalls/_cross/flyway-ledger-collision.md` 记录了「文档声称由 `FlywayLedgerIsolationTest` 负责，**该测试类全仓不存在**」⇒ 写脚本时**别引用不存在的类** |
