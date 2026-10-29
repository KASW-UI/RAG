# users 域 **HTTP 层**双侧活体对拍探针（Go 真源 ↔ RAG）

> **落点理由**：`tools/api-full-parity-probe/users/` 是 **users 域私有**目录
> （只被 users 域引用 ⇒ 按 AGENTS.md §5.3f「共享判据 = 依赖度数，不是目录位置」
> ⇒ **不属共享层** ⇒ users 域 lane 可写）。
> **口径依据**：`docs/RoundPlan/Round-0.md` **T-0.6**「按探针种类分层 `tools/<kind>/<域>/`」
> ⇒ 与 `article` / `like` / `follow` / `favorite-rpc` 同构，**不混进 `tools/difftest/`**
> ⇒ **不触发 §6.3 守门计数漂移**。

## 目的

补 `docs/RoundPlan/Round-0.md` **T-0.4**（P0 #4 的覆盖清单必须按**实跑**改写）
里 **users 域那一格**的可复算证据。

上一轮（2026-10-29 之前）跑 `tools/difftest/run.sh` 得到的是
`steps=39 asserted=9 byte_equal=8 inconclusive=7 unexpected=15` ⇒ **15 条未归因**。
本探针把同一套场景表跑到 **`unexpected=0`**，并把「那 15 条到底是什么」查清楚了。

## 结论一句话

**那 15 条 unexpected 不是 RAG 的业务缺陷，是探针的 HTTP 传输层不认识 1xx。**

RAG 共享层的 `GoStyleClientDisconnectProbe`（**已签字偏离 `SD-2026-10-25-04`**）
会在响应提交**之前**往 socket 裸写 `HTTP/1.1 103 Early Hints`；
而 **Python 的 `http.client` 3.11 不跳过 1xx**（把它当终态 ⇒ body 空）
⇒ **任何耗时 > 500ms 的请求**，RAG 侧都读不到最终响应
⇒ ⇒ token 抓不到 ⇒ 后续 admin 步骤全 401 ⇒ 一片假 DIFF。

> 证据：RAG 仓自己的 javadoc 已写明这一点 ——
> `service/common/.../GoStyleClientDisconnectProbe.java` 类注释：
> 「⚠️ 把 1xx 当最终响应的客户端（实测：Python `http.client` 3.11 看到 `status=103`、
> body 为空 ⇒ **1 行即失效**）⇒ ⇒ 这类客户端在慢请求上**本来就**读不到最终响应」。

## 用法

```bash
bash tools/api-full-parity-probe/users/run.sh              # 完整跑（含构建）
bash tools/api-full-parity-probe/users/run.sh --no-build   # 复用已有 jar / Go 二进制
bash tools/api-full-parity-probe/users/run.sh --keep       # 跑完不收摊（便于手工 curl）
bash tools/api-full-parity-probe/users/run.sh --serve-only # 起完两套栈就停（供取证脚本用）
bash tools/api-full-parity-probe/users/mutate-parity.sh    # 变异验证（6 条）
```

退出码：`0` = 两侧一致（且 register 落库对照通过）· `1` = 有未预期差异 ·
`2` = **环境问题 / 真源漂移 / 依赖缺失 / 本轮不可判定**（见下「超时形态」一节）。

端口**全部可用 env 覆盖**（默认落在 **18211~18224**，与其它 lane 错开）：
`UPPROBE_PG_PORT` / `UPPROBE_REDIS_PORT` / `UPPROBE_GO_{USER,ADMIN}_{RPC,API}_PORT` /
`UPPROBE_RAG_{USER,ADMIN}_{RPC,API}_PORT` / `UPPROBE_CONTAINER_PREFIX`。
探针本体（`diff.py`）的端口用 `PROBE_GO_USER_URL` / `PROBE_GO_ADMIN_URL` /
`PROBE_RAG_USER_URL` / `PROBE_RAG_ADMIN_URL` 覆盖。

## 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的生成码/二进制**；但真源 yaml 把主机名**写死**了
（`postgres` / `redis:6379` / `jaeger:4317`）⇒ 脚本生成**配置副本**
（`conf/go/*.yaml` → `${RUN_DIR}/conf/go/`），把那些主机名与端口改成宿主可达地址。

> 🔴 **脚本第 0 步会断言真源仓仍然干净**：
> `git status --porcelain -- service/` 必须为空 ⇒ 否则 `exit 2`。
> ⇒ **冻结的真源一个字都没被改。**

## 拓扑（两侧同时跑）

```
  客户端 ──HTTP──▶  │ Go: user-api :18215 ──gRPC(Endpoints 直连)──▶ user-rpc :18213 ┐
                    │ Go: admin-api :18216 ──gRPC(Endpoints 直连)──▶ admin-rpc :18214┤
                    └──────────────────────────── 同一个 PG :18211（go_db） ─────────┘
  客户端 ──HTTP──▶  │ RAG: user-api :18219 ──gRPC(static://)──▶ user-rpc :18217    ┐
                    │ RAG: admin-api :18220 ──gRPC(static://)──▶ admin-rpc :18218    ┤
                    └──────────────────────────── 同一个 PG :18211（rag_db） ────────┘
```

| 侧 | 组件 | 端口 | 服务发现 | 库 |
|---|---|---|---|---|
| Go | `user-rpc` | 18213 | 无（Endpoints 直连） | PG :18211 / `go_db` |
| Go | `admin-rpc` | 18214 | 无 | 同上 |
| Go | `user-api` | 18215 | — | — |
| Go | `admin-api` | 18216 | — | — |
| RAG | `user-rpc` | 18217 | `static://`（绕开 Nacos） | PG :18211 / `rag_db` |
| RAG | `admin-rpc` | 18218 | 同上 | 同上 |
| RAG | `user-api` | 18219 | — | — |
| RAG | `admin-api` | 18220 | — | — |

### 表名差异**不是**差异（已签字偏离 A-03）

Go 用 GORM AutoMigrate 建 `users` / `user_avatar_history`；
RAG 建 `users_user` / `users_user_avatar_history`
⇒ **物理上无法共用 schema** ⇒ 对拍只能落在 **HTTP wire 层**。

## 探针的三处关键改动（相对 `tools/difftest/diff.py`）

`tools/difftest/**` 是**主线共享面** ⇒ **一个字没动**。本目录是**域内副本**，
相对原文件改了三处，**每一处都对应一个实测出来的坑**：

### ① 端口可覆盖

原文件的 `BASE` 是**硬编码**的（`18888` / `18889` / `59900` / `58889`）
⇒ 并行实例会打到**别人的**服务上 ⇒ 本副本全部走 env（默认仍是原值 ⇒ 老调用方零改动）。

### ② 传输层换成 `http1xx.py`（**RFC 9110 感知：跳过 interim 1xx**）

`http1xx.py` 是本探针的核心新增件：

* 按 RFC 9110 §15.2，**1xx 是 informational，客户端必须忽略它并继续等最终响应**
  ⇒ 与 JDK `java.net.http` / Go `net/http` / curl / 浏览器**所见一致**；
* **每个请求独立开一条连接**（`Connection: close`）
  ⇒ RAG 侧的 1xx 预算（`GoStyleClientDisconnectProbe` 的 **按请求**计数）
  **不会跨请求互相吃**；
* **把跳过的 1xx 记下来**（`interim` 字段）⇒ 报告里能数出「命中 1xx 的 step 数」
  ⇒ **1xx 不是被当成「不存在」，是被当成「已签字偏离 + 留痕」**。

### ③ 预热 + 有界重试 + 计数

* **预热**（`diff.py` 的 `warmup()`）：在**取 token / 打第一个业务请求之前**，
  先打一发**只读**请求（登录一个不存在的用户 ⇒ 不写库）把**冷启动首请求**消化掉。
  命中 1xx 的预热轮次**照实计数**（不隐藏）。
* **有界重试**（`Runner._send()`）：传输层失败或终态仍是 1xx ⇒ **重试一次**
  （`MAX_RETRY=1`，可用 `PROBE_MAX_RETRY` 覆盖）。
  ⚠️ **判据不许放宽**：
  * 重试**有上界** ⇒ 不会变成「无限重试直到绿」；
  * **重试后仍不一致 ⇒ 必须红**；
  * 重试**只**针对**信号性问题**（传输失败 / 1xx）⇒ 业务码差异（401 / 1006…）
    **不触发**重试 ⇒ 否则真缺陷会被洗成绿。
* **计数必报**：`1xx_命中_step数` / `1xx_总行数` / `因1xx重试_step数` / `重试_step数` /
  `预热命中1xx轮数`。

## ⚠️ 「超时形态」：把**环境负载**与**语义漂移**分开（退出码 2）

探针区分三种**退出码**：

| 退出码 | 含义 | 判据 |
|---|---|---|
| **0** | 两侧一致 | 零差异 ＋ 落库对照通过 |
| **1** | **有漂移** | 存在非超时形态的差异 |
| **2** | **本轮不可判定**（环境问题） | 有差异，**且** RAG 侧出现过**超时形态** |

**超时形态** = 两种之一（`diff.py` 的 `timeout_shaped()`）：

* HTTP **503** + body `Request Timeout` ⇒ 共享层 `HttpMiddleware` 的 HTTP 超时分支
  （Go 侧同一份文案 `httpTimeoutBody`）；
* 业务码 **1015**「服务繁忙」⇒ gRPC deadline 超时。

⚠️ **传输层失败（HTTP 0 / `<<transport error>>`）** ⇒ **不在**超时形态里
⇒ ⇒ **「一侧被 kill 掉」必须判红（exit 1）**，不能被当成「环境问题」打发
（2026-10-29 实测踩过：M2 变异曾被误判成 exit 2 ⇒ 修正后 M2 = `EXIT=1`）。
⇒ 理由：**「对方没答话」是硬差异**；「对方答了但太慢」才是负载形态。

⚠️ **为什么必须分开**（2026-10-29 实测踩过）：
本机**负载飙到 load average 78** 时，RAG 侧的请求会撞上
**2s gRPC deadline / 3s HTTP 超时**，而 **Go 侧不撞**
⇒ 探针报出 **5 条 DIFF**（`user.get` 503 · `user.logout` 1015 · `admin.logout` 1015
＋ 两条连带的 `afterlogout`）
⇒ ⇒ **那是环境问题，不是漂移** ⇒ 报成「漂移」就是把**跑红当缺陷**（AGENTS.md §3.3.3）。

⇒ **三条硬规矩**：
1. 超时形态**只用于分类与退出码** ⇒ **每一步的判定结果一字不改**（DIFF 照样逐条打印）；
2. 退出码 **2 绝不是 0** ⇒ 「超时」永远不会被当成「两侧一致」；
3. 若差异**不是**超时形态（哪怕同时有别的步骤超时）⇒ 仍按 **1（漂移）**报。

⇒ **实操**：探针**必须在一台空载机器上跑**。重载时先看 `uptime`
（`load average` ≫ 核数 ⇒ 结果不可用）。

## 额外的一件：**register 落库对照**

`check_register_landed.py` 单独回答一个问题：
**「RAG 的 `user.register` 到底有没有写进库？」**

上一轮留下一个未归因的疑点「RAG 的 `user.register` 实际没落库」。
本脚本把它变成可复算的判据：

1. 两侧各注册一个**专用**用户名（`probe_landed_user`）⇒ **不碰**矩阵用的 `dt_user1`；
2. 直接查**两侧各自的库**（`go_db.users` / `rag_db.users_user`）；
3. 断言：**两侧都恰好 1 行**，且 **HTTP 说成功 ⇒ 库里就有行**。

> ⚠️ **用户名绝不能以 `dt_` 开头**：矩阵里的 `admin.getuserlist.keyword` 用
> `keyword='dt_'`（LIKE `'%dt_%'`）⇒ 本脚本造的账号会被那个查询捞进去
> ⇒ 多出来的行 ⇒ uid 没被归一化 ⇒ **矩阵假红**（2026-10-29 实测踩过）。

### 实测结论（2026-10-29 · `align/r4-users-probe`）

```
-- go 侧 --   HTTP 200  interim1xx=无
              body : {"code":200,"msg":"OK","data":{"uid":"2115696593824362496"}}
-- rag 侧 --  HTTP 200  interim1xx=无
              body : {"code":200,"msg":"OK","data":{"uid":"2115696595347931136"}}
-- 库里的行 --
   go   go_db.users → 1 行
        uid=2115696593824362496 username=probe_landed_user email=landed@example.com
        extra_info={"city":"上海"} password=$2a$10$…
   rag  rag_db.users_user → 1 行
        uid=2115696595347931136 username=probe_landed_user email=landed@example.com
        extra_info={"city":"上海"} password=$2a$10$…
✅ 落库对照通过：两侧 HTTP 成功且各恰好 1 行 ⇒ **register 落库正常，不是缺陷**
```

⇒ **「register 没落库」这个疑点是上一轮的误判**：它是由「103 空 body ⇒ 没抓到 uid」
推出来的，不是由**落库事实**推出来的。
⇒ **两侧的 `extra_info` 逐字节相同**（都是 `{"city":"上海"}`）⇒
   排除了「RAG 用 JacksonTypeHandler 写 text 列、Go 用 serializer:json ⇒ 键序/转义不同」这条猜想。

## 实测结果（2026-10-29 · `align/r4-users-probe`）

```
steps=39  asserted=10  byte_equal=29  inconclusive=0  unexpected=0
1xx_命中_step数=5  1xx_总行数=5  因1xx重试_step数=0  重试_step数=0  预热命中1xx轮数=1
EXIT=0
```

* **39 步全过**：user 链路 17 步 + admin 链路 22 步（场景表与 `tools/difftest/diff.py` 一致）。
* `asserted=10` = 走「按已签字边界**分别断言两侧**」这条路的步数
  （N-1 的 7 条 401 + DRIFT-006 的 3 条 400 text/plain）；
  `byte_equal=29` = 真正跨端**逐字比对**的步数。
  ⚠️ **两个数分开报** ⇒ 不要把「靠断言通过」读成「两侧 body 逐字相同」。
* `inconclusive=0` ⇒ 没有任何一步因为「前置模板没抓到」而不可判定。

### 覆盖到哪 39 步

| 组 | 步数 | 覆盖 |
|---|---|---|
| user 鉴权 | 7 | 注册 / 重复注册 / 畸形 JSON / 登录 / 错密码 / 不存在 / 无 token / 坏 token |
| user 资料 | 8 | 取本人 / 改邮箱 / 上传头像 / 切头像 / 头像历史 / 注销 / 注销后再取 / 注销账号 |
| admin 鉴权 | 5 | 登录 / 取自身 / 无 token / 坏 token / 注销后再取 |
| admin 邀请码 | 2 | 生成邀请码 / 用邀请码建管理员 / 畸形 JSON |
| admin 用户管理 | 12 | 按 uid 查 / 缺 uid / 非法 uid / 分页列表 / 前缀过滤 / 匹配不到 / 缺 keyword / 封禁 / 解封 / 重置密码 / 改自身 / 改指定用户 / 删除 |
| 必填校验 | 1 | 注册缺 email |

### ⚠️ 这 39 步**不等于**「users 域双侧活体全闭合」

1. **只覆盖 HTTP 层**。gRPC wire 层（`user-rpc` / `admin-rpc` 的 proto 字节）**不在本探针范围**
   ⇒ 域台账里 rpc 层的证据仍是各自独立的那几条，**不要与本探针混记**。
2. **不是幂等的**：`admin.update` 会把 admin 密码改成 `newadmin123`，
   `admin.create` 会建固定用户名 `dt_admin2`
   ⇒ **每轮必须是全新库**（`run.sh` 每轮 `down -v`）⇒
   这也是为什么**不能**用 `tools/difftest/reset-admin-fixtures.sh`（那脚本是给「复用同一个库」的旧用法准备的）。
3. **JWT 是两侧各自签发的**：Go 用真源 yaml 里的 `AccessSecret`，
   RAG 用**每次运行现生成**的随机 `USER_JWT_SECRET` / `ADMIN_JWT_SECRET`
   ⇒ 两侧 token **互不通用**，探针也**各用各的**（`diff.py` 的两个 Runner 各自推进状态机）。
   ⇒ ⚠️ RAG 侧**必须**传这两个变量（P0 #8 的 fail-closed 守卫：缺变量 ⇒ **进程起不来**）。
4. `user.avatar` / `user.avatar.select` 覆盖了「4/5 步事务」但**没有**逐字段比对
   `extra_info` 的**回读**内容 ⇒ 那一面由 `check_register_landed.py` 的 `extra_info` 对照间接覆盖。

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 | 实测（2026-10-29 · `align/r4-users-probe`） |
|---|---|---|---|
| **M0** 基线 | 无 | 绿（`unexpected=0`） | ✅ `EXIT=0` · `steps=39 asserted=10 byte_equal=29 inconclusive=0 unexpected=0` |
| **M1** 篡改一侧的库 | 把 RAG 侧 `users_user` 里 `dt_user1` 的 email 改掉 | 红，且**精确**指出 `user.get` | ✅ `EXIT=1` · `unexpected=1` · DIFF 就在 `user.get` |
| **M2** 停掉一侧的进程 | `kill -9` RAG 侧 `user-api` | 红（不得「一侧挂了还报绿」） | ✅ `EXIT=1` · `unexpected=13` · **10 步传输层失败**（⇒ 判红，不是「环境问题」） |
| **M3a** 让一侧变慢 | 给 RAG 侧 `users_user` 加 `ACCESS EXCLUSIVE` 锁持 2s ⇒ 请求必 > 500ms ⇒ **必命中 1xx** | **绿**（1xx 被跳过，最终响应正常） | ✅ `EXIT=0` · `unexpected=0` · `1xx_命中_step数=1`（2 行） |
| **M4** 同 M3a，但**关掉探针的 1xx 感知**（`PROBE_DISABLE_1XX_SKIP=1 PROBE_MAX_RETRY=0`） | | **红**（1xx 被当成终态 ⇒ 读不到最终响应） | ✅ `EXIT=1` · `unexpected=12` · `1xx_命中_step数=3` |
| **M5** 还原后再跑基线 | 无 | 绿 | ✅ `EXIT=0` · `unexpected=0` |

⇒ **6/6 全按预期**（`mutate-parity.sh` ⇒ `PASS=6 FAIL=0`）。

⇒ **M3a / M4 这一对是本探针最关键的证据**：
**同一个场景（同样慢、同样命中 1xx），只因为「1xx 怎么处理」不同 ⇒ 一个绿一个红**
⇒ ⇒ 证明「探针变绿**确实是** RFC 9110 感知带来的」，而不是碰巧；
⇒ 同时证明「**1xx 不是被当成『允许任意差异』**」：
关掉感知 + 不许重试 ⇒ 1xx **直接判红**。

⚠️ **四个变异都不改任何产品代码**：改一行库 / kill 一个进程 / 加一把锁 / 切一个 env
⇒ **源码零改动** ⇒ 天然可还原 ⇒ 不会污染工作树。

### 踩过的坑（写下来免得下一个人再踩一遍）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 探针崩在第 1 步：`RUN_DIR?: unbound variable` | **macOS 自带 bash 3.2**：`$VAR` 后面紧跟**多字节字符**（中文逗号/括号）时，那个字符被算进**变量名** ⇒ `$RUN_DIR，` 变成变量 `RUN_DIR，` | 全部改成 `${VAR}`；落仓前用正则扫一遍（`\$[A-Za-z_][A-Za-z0-9_]*(?=[^\x00-\x7f])`） |
| 2 | 探针崩在第 2 步：`stat …/service/admin/admin/rpc: directory not found` | Go 侧的路径是 `service/user/{user,admin}/{rpc,api}` ⇒ **`admin` 在 `user` 下面** | 映射写成完整路径，不用拼接 |
| 3 | **`--no-build` 跑不起来**：`缺少产物 …/bin/user-rpc` | `--no-build` 时产物**不在** `${RUN_DIR}/bin` ⇒ 存在性检查直接 die | `--no-build` 改成「**能复用就复用，缺了就照样构建**」；jar 的**拷贝**与 BUILD 无关（产物在仓内 `target/` 里） |
| 4 | **M2 变异没让探针变红**（第一版） | `kill`（SIGTERM）触发 Spring Boot 的**优雅停机** ⇒ JVM 还会活好几秒 ⇒ 矩阵后续步骤**照样答得上** ⇒ 探针**误报绿** | 改用 `kill -9` ⇒ 监听口立刻消失 |
| 5 | **落库对照把矩阵搞红了**（`admin.getuserlist.keyword` 报 uid 不同） | 落库对照造的账号 `dt_probe_landed` 被矩阵的 `keyword='dt_'`（LIKE `'%dt_%'`）捞进去 ⇒ 多出来的行 ⇒ uid 没被归一化 | 用户名改成**不以 `dt_` 开头**的 `probe_landed_user`；脚本里加一道**硬断言**（以 `dt_` 开头 ⇒ 直接 exit 2） |
| 6 | 落库对照第一版报「RAG 侧：库里有行但 HTTP 没返回成功」（503 `Request Timeout`） | 本脚本的注册是**打到 RAG 侧的第一个业务请求** ⇒ **冷启动** ≥ 3s ⇒ 撞上共享层的 **HTTP 超时分支** ⇒ 客户端收到 503，**而那一行其实已经写进库了** | 加**预热**（只读路径）⇒ 注册变成 200。<br>⚠️ 这也说明：**「HTTP 超时」与「没落库」是两件事**，必须查库才知道 |
| 7 | 端口被占 ⇒ 预检失败 | 别的 lane 也在跑（`r2like` / `r2probe` / `ddl-*` / `r4m-*`） | 全部落到 **18211~18224** 段，且**全部可 env 覆盖**；容器名带前缀 `r4upprobe` |
| 8 | 收摊时进程没死 | `kill` 发的是 SIGTERM ⇒ JVM 优雅停机要几秒 | 收摊用 `kill`（SIGTERM）即可，但**别在 kill 后立刻重跑同一端口**；必要时 `kill -9` + 等 `lsof` 空（`nc -z` 会误报空闲） |
| 9 | 起栈要用 `exec` | `( cd X && cmd ) &` 的 `$!` 是**子 shell** 的 PID ⇒ `kill` 不到真进程 | `( cd X && exec cmd ) &` ⇒ `$!` 就是真进程 |
| 10 | Go 侧 Prometheus 端口撞车 | Go 的 `DevServer.Port` 是**另一个监听者** ⇒ 与 `ListenOn` 改成同一个 ⇒ `zrpc/server.go:91` panic | 四个 DevServer 端口一起搬走（18221~18224） |
| 11 | **连跑两轮 ⇒ 第二轮直接 EXIT=2**（`端口 18211 已被占用（com.docke）`） | 上一轮的 PG/Redis 容器**还在跑**（仍占着发布端口）⇒ 端口预检把它当成「别人占用」⇒ 直接 exit 2 ⇒ **同一个前缀永远跑不了第二次** | ① **预检之前**先 `docker compose down -v` 拆掉残留容器；② 预检**豁免「自己的」**（compose project 标签 + 命令行含 `${RUN_DIR}/`）；③ 收摊时 `wait_ports_free()` **轮询到 `lsof` 空**（⚠️ `nc -z` 会误报空闲）|
| 12 | 变异套件里 M1/M2/M3 全是 EXIT=2 | 同 #11 ⇒ **第一个失败会连累后面全部**（端口一直占着） | 同 #11；⇒ **变异套件必须逐轮等端口释放**（`wait_ports_free`） |
| 13 | **重载时探针报 5 条 DIFF**（`user.get` 503 · `user.logout` 1015 …） | 机器有其它负载 ⇒ RAG 侧撞 2s gRPC deadline / 3s HTTP 超时，而 Go 侧不撞 ⇒ **根因 = RAG 侧多一层 500ms 的「切片等待」量化**（两侧层数**相同**，都是 api→rpc→PG ⇒ **不是**跳数差） | 探针按「**超时形态**」分类 ⇒ 退出码 **2（环境问题）**而不是 1（漂移）；**实操 = 换空载机器复跑**（见下） |

### 13 的补充：两次运行的对照（**同一台机器，不同负载**）

| 运行 | `load average` | 结果 | 退出码 |
|---|---|---|---|
| 第一次（低载） | ~低 | `steps=39 asserted=10 byte_equal=29 inconclusive=0` **unexpected=0** | **0** |
| 第二次（重载） | **78** | `unexpected=5`（`user.get` 503 · `user.logout` 1015 · `admin.logout` 1015 ＋ 两条连带的 `afterlogout`） | 2（超时形态 ⇒ 环境问题） |
| 第三次（中等载） | **31** | `unexpected=2`（`admin.logout` 1015 ＋ 连带一条） | 2（超时形态 ⇒ 环境问题） |

⇒ ⇒ **探针本身能 EXIT=0；机器忙时 EXIT=2** ⇒ 这是**探针对负载敏感**，**不是业务漂移**。

#### 延迟形态的**实测**（不是猜「撞了」）

探针逐请求打印**两侧的端到端耗时**：

| 侧 | n | min | med | max |
|---|---|---|---|---|
| **Go 真源** | 39 | 0 | **50** | **319** |
| **RAG** | 39 | 4 | **51** | **2046** |

⇒ **中位数几乎相同**（50 vs 51ms ⇒ 快路径等价）
⇒ **RAG 的尾部被拉长 6.4 倍**（319 → 2046ms，且**紧贴 2s gRPC deadline**）。

⇒ **根因（消费点）**：`service/common/.../HttpMiddleware.java:415-419`
—— 容器线程**每片最多等 500ms**（`GoStyleClientDisconnectProbe.probeIntervalMillis()`，
间隔常量见 `GoStyleClientDisconnectProbe.java:187`）⇒
worker 若在片边界**之后**才完成 ⇒ 响应被**多等最多一片** ⇒
⇒ **延迟被向上量化到 500ms 的整数倍**。实测的每个慢值都落在整数倍附近：
`admin.logout` 2046 ≈ 4 片 · `admin.getself` 1986 ≈ 4 片 · `user.logout` 1337 ≈ 3 片 ·
`user.get` 1333 ≈ 3 片 · `admin.login` 910 ≈ 2 片 · `user.register` 778 ≈ 2 片。
⇒ **Go 侧没有这个机制**（`net/http` handler 返回即写响应 ⇒ **不量化**）。

⇒ ⚠️ **两侧的调用链层数相同**（都是 api → rpc → PG **三跳**）
⇒ ⇒ **差在「RAG 侧多一层 500ms 的等待量化」**，**不是**「Go 两跳 vs RAG 三跳」。

⇒ 已按 `docs/twoStage.md` §10 登记为 **`DRIFT-USERS-LAT-001`（P3 · D 类）**
⇒ `docs/DRIFT/users/_shared.md`（**本轮落账**）。
⇒ **怎么解除**：在**空载机器**上复跑 ⇒ `unexpected=0` ⇒ 归为纯负载敏感；
复跑**仍**超时 ⇒ 才是**真差异**，需另立项。
⚠️ **落点在共享层 `service/common`** ⇒ 若要动，**写权归 G4**。

## 依赖

`docker`（PG + Redis）· Go toolchain（≥1.25）· JDK 21+ · `mvn`（离线 `-o`）· `curl` · `python3`（仅标准库）。
