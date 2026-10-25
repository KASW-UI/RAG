# L3 · `align/global-ddl` —— X-7 残留（5 个域的 DDL 双侧活体逐列对照）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **B2** 做完：DDL 双侧活体逐列对照现在**只有 4 个域预设**
（`users` / `task` / `message` / `admin`）⇒ 其余域**只到源码级**。

**剩余 5 域**：`article` / `like` / `hot` / `follow` / `favorite`

⚠️ **`favorite` 是硬禁区** ⇒ **只建库、不碰其代码**（§七之二 ⛔ 条）⇒
本 lane 对 favorite 的口径 = **只跑对照、不提修复**。

## 二、现状（别重复已做的）

| 域 | 状态 | 出处 |
|---|---|---|
| `users` / `admin` | ✅ 类型/长度 0 差异 | `tools/difftest/check-ddl-static.py` 的 `DOMAINS` |
| `task` | ✅ 表名两侧相同 ⇒ 映射表为空 | 脚本注释（`AutoMigrate` vs Flyway，**表名完全一致**） |
| `message` | ✅ 已补 | 脚本注释 + `tools/difftest/run-message-ddl-parity.sh`（⚠️ message 的基建是 `msgddl-pg`，不是 `difftest-postgres`） |

⚠️ **踩过的坑（照抄即可）**：
- `admin` 侧是**两端都改名**（不是一端改）⇒ 写错映射会让脚本**静默「列集合不一致」而不报错**。
- 脚本**默认不开**某些比对（已知差异会刷屏）⇒ 别把默认行为当「没差异」。

## 三、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| X-7 的逐条实测 | `docs/DRIFT/global/cross-domain.md` | **§二·五**（含「① 已有域预设本身已失效」/「④ 工具改动」） |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **B2** 行 |
| 工具现状 | `tools/difftest/check-ddl-static.py` | 全文（**脚本头注释就是使用说明**，含 `--domain` / `--indexes` / 容器参数） |
| runner 样板 | `tools/difftest/run-message-ddl-parity.sh` | 全文（message 域的专用基建怎么拉起来的） |
| DDL 结论（已知未修项） | `docs/DRIFT/_cross/ddl-type-divergences.md` | 全文 |
| Go 真源 | `Sea-RideTheWind_golang/service/<域>/**/model/*.go` 的 `AutoMigrate` | 逐字读 |

## 四、写权范围（白名单）

**只写这些**：
- `tools/difftest/check-ddl-static.py`（扩 `DOMAINS` 预设）
- `tools/difftest/` 下**新**建的脚本（命名自取，但**不得覆盖** L4 的 `check-flyway-concurrent.sh`）
- `docs/DRIFT/global/lanes/L3-ddl.md`（本文件）

**⛔ 禁区**：
- `service/**`（**本 lane 一行生产代码都不改** —— 这是**对照**任务，不是修复任务）
- `service/favorite/**` 与 `docs/*/favorite/`
- `tools/difftest/check-flyway-concurrent.sh`（L4 的）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **5 个域的逐列对照结果**（类型 / 长度 / 可空性 / DEFAULT / 索引与约束）。
2. 扩域后的 `check-ddl-static.py` ⇒ **能重跑**、**幂等**、**退出码有语义**（有差异 ⇒ 非 0）。
3. 一张「差异表」：哪些是真漂移、哪些是已签字偏离、哪些是「只记不修（性能非行为）」。
4. 真漂移**登记**（不修 —— 修是别的 lane / 别的域的事）⇒ 写进本文件末尾。
5. 台账：本文件末尾追加「逐域结果 + 差异分类 + 证据（`文件:行号`）」。

## 六、验收标准

- [ ] `python3 tools/difftest/check-ddl-static.py --domain article` 等 **5 个域**都能跑（favorite 除外 ⇒ 4 个 + favorite 只建库口径）
- [ ] 脚本**幂等**：连跑两次结果一致
- [ ] 每个差异都归类到「真漂移 / 已签字 / 只记不修」，**不许留「未知」**
- [ ] 明确写出**证据强度**：本轮是**双侧活体**（真 PG 两边建库后比），不是源码级
- [ ] 若某域跑不起来（基建缺失）⇒ **显式登记为「未覆盖 + 缺什么」**，不许静默跳过

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **基建不是同一个** | message 域用 `msgddl-pg`（不是 `difftest-postgres`）⇒ 每个域的容器/DSN 都要**逐域确认**，不要假设 |
| **映射表写错 ⇒ 静默不一致** | admin 侧两端都改名 ⇒ 映射写错时脚本**不报错**，只是列集合对不上 ⇒ 必须**逐域核对映射表** |
| **已知差异刷屏** | 脚本默认不开某些比对 ⇒ 「没差异」可能只是没查 ⇒ 扩域时要把该开的都打开并说明 |
| **Docker 抢资源** | 全仓并行跑会互抢容器（实测 `hot-rpc` 拉不到 `apache/kafka:3.7.2`、`user-api` 503 —— 单跑都绿，是环境层面既有现象）⇒ 本 lane 建议**串行跑自己的域**，别与其他 lane 同时起容器 |
