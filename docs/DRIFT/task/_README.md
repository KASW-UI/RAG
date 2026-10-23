<!-- @new 2026-10-23 · task 域台账导航 · 由 `align/task` 工作树产出 -->

# task 域台账导航

> **基线 commit**：`8422116` · **工作树**：`RAG.worktrees/task`（分支 `align/task`）
> **首轮核对日期**：2026-10-23 · **最近更新**：2026-10-24（DRIFT-TASK-API-003 收口）
> **Go 真源**：`Sea-RideTheWind_golang`（go-zero `v1.10.2`、goka `v1.1.16`）

| 组件 | 台账 | 最新结论 | 证据强度 |
|---|---|---|---|
| `task-rpc` | [`rpc.md`](./rpc.md) | 🔴 NOT EQUIVALENT · 已修 **5** 条 | ✅✅✅ Kafka 端到端活体对拍 · ✅✅✅ Kafka 畸形消息活体对拍（五个用例）· ✅✅ gRPC 双向跨语言对拍 · ✅ 真启动 + 真 PG 16 + 真 Redis |
| `task-api` | [`api.md`](./api.md) | ✅ **EQUIVALENT** · 已修 **3** 条 | ✅✅ 双侧活体对拍（44 条，MATCH 44/DIFF 0）· ✅✅ 真 Tomcat 守门（12 条 + 变异测试）· ✅✅ Nacos 真注册中心（注册/注销实测）· ✅ 真实 MVC 管线（MockMvc，20 条）· ⚠️ gRPC 边界在单测里是 mock |
| `task-proto` | [`proto.md`](./proto.md) | ✅ 一致（消费方式本轮首次核对） | ✅ 双侧静态 diff（`check-proto-static.sh` EXIT=0）+ 真构建 |

**task 域没有 `-common`** —— Go 侧 `service/task` 下只有 `api` 与 `rpc`，**不是遗漏**。
⇒ 错误全部是标准 gRPC code（`INVALID_ARGUMENT` / `UNKNOWN`），不需要 like/message 那种业务码 ↔ gRPC code 转换类。

## 本轮改了什么（首轮 7 个 + 第二轮 5 个 commit）

### 首轮（2026-10-23）

| commit | 内容 |
|---|---|
| `e5948c9` | 救活 `TaskProgressUpsertParityTest` 的 15 条静默 skip（Ryuk 未禁用） |
| `4241690` | `task.definitions` 四个 TTL 键是死配置（relaxed binding 键名对不上） |
| `6dc9b4c` | `TaskCountStore` 跨 group 串号 + 重启归零；`task-api` 端口回 Go 原值 18886；4 处 `Go:` 行号引用校正 |
| `38ce9da` | 三档台账首轮（rpc/api/proto）+ 4 处 `Go:` 行号引用校正 |
| `2865ffe` | HTTP 层双侧活体对拍（43 用例）+ gRPC 单组件对拍 runner（7 用例） |
| `25ca5f5` | 🔴 **`RedisPipelines` 把 Go 的 pipeline 当成 MULTI/EXEC 事务 ⇒ 整条消息消费链路是死的**（`ERR EXEC without MULTI`）+ 真 Redis 守门测试（9 条，含阳性对照 + 变异测试） |
| `2900360` | Kafka 端到端对拍脚本（`tools/difftest/parity-task-kafka.py`）+ gRPC runner 入库 |
| `a42f606` | 台账补 DRIFT-TASK-RPC-004/005 与 5 条踩坑（06~10）+ §四·三/四·四 端到端对拍证据 |
| `c6a4902` | `_README` 的 commit 清单换成真实哈希 |
| `6fc2d54` | Kafka 端到端编排入仓（`run-task-kafka-e2e.sh` 等 4 文件），从零跑通 `EXIT=0` |
| `9a95c65` | `api.md` 修正一处自相矛盾 + 补对拍编排的复现指引 |
| `7d9e90a` | DRIFT-TASK-RPC 编号归并（消掉 004 空洞），同步锚点与全部引用 |

### 第二轮（2026-10-24 · 修 DRIFT-TASK-API-003 + 验证未知）

| commit | 内容 |
|---|---|
| `364d8c3` | 🔴 **修 DRIFT-TASK-API-003** —— form-urlencoded / multipart 的 JSON body 被 Spring 拒（Go 收）。根因是 `ServletServerHttpRequest.getBody()` 的 form-post 分支 + `EmptyBodyCheckingHttpInputMessage` **构造期**就消费 body ⇒ 转换器层修不了；修在**消息输入层**（`createInputMessage`）+ multipart 配置层。真 Tomcat 守门 12 条（含阳性对照）+ **两次变异测试** |
| `e378114` | **HTTP 侧双侧活体对拍一键编排入仓**（`run-task-http-parity.sh`）+ `parity-task.py` 三处改造（补上一直没实现的「不带 Content-Type」用例 / 有 DIFF 返回非零 / 按媒体类型归一后判定）⇒ **44 条 MATCH 44 / DIFF 0** |
| `37486c0` | 三个静态守门脚本扩到 task 域：`check-proto-static.sh`（EXIT=0）、`check-ddl-static.py`（11 列逐字一致）、`check-plaintext400-headers.sh`（task 段三条断言全过） |
| `a4d772c` | 台账：API-003 收口 + **推翻上一版的根因误判** + 5 条新踩坑（11~15） |
| `243b5e5` | **gRPC 跨语言互调** —— Go 客户端 → 两端服务端（补齐第二个跨语言方向；用真源**自己生成**的桩，真源零写入）⇒ 7/7 一致 |
| `ca37b4e` | **Kafka 畸形消息活体对拍**（五个用例，两侧完全一致）⇒ 并**推翻本仓 javadoc 里一条错误断言**（「Go 会被 kafka-go 的自动提交抵消」—— kafka-go 用 `FetchMessage` 时自动提交不生效） |
| `8d89170` | **编排脚本**：pkill 后只 sleep 2 ⇒ 新进程抢不到端口，症状却是「api 503」⇒ 改成轮询等端口释放 / 等端口监听 |
| `a50b6cb` | 台账：rpc.md 加 §四·五（跨语言 gRPC + 畸形消息活体对拍）+ 证据边界与待办更新 |
| `546457b` | **Nacos 真注册中心对拍**（注册 → 注销，EXIT=0）+ 修既有 `run.sh` 里 2 处同款潜伏 bug（`$var` 紧跟多字节字符） |
| `（本次）` | 台账：api.md 加 §四·六（Nacos 证据）+ Pitfalls 15·补 / 17 + 本 README |

**最终测试**：`task-api` **90**（含新增 12 条真 Tomcat）/ `task-rpc` **90**（含 15 条真 PG + **9 条真 Redis**）/ `common` **146**
⇒ **326 条全部 0 failures / 0 errors / 0 skipped**。

**静态守门**：`check-proto-static.sh` EXIT=0 · `check-ddl-static.py --domain task` EXIT=0（11 列 0 差异）· `check-plaintext400-headers.sh` EXIT=0。

**活体对拍**：`parity-task.py` 44 条 **MATCH 44 / DIFF 0**（`EXIT=0`）· `parity-task-kafka.py` 最终态完全一致（`EXIT=0`）·
`run-task-kafka-malformed.sh` 五个畸形消息用例两侧一致（`EXIT=0`）·
`run-task-grpc-xlang.sh` 跨语言 7/7 一致（`EXIT=0`）· `run-task-nacos-discovery.sh` 注册/注销符合预期（`EXIT=0`）。

### 「验证未知」10 条的完成情况

| # | 项 | 状态 |
|---|---|---|
| 1 | `check-proto-static.sh` 扩 task | ✅ 完成（EXIT=0） |
| 2 | `check-ddl-static.py` 扩 task | ✅ 完成（11 列 0 差异，EXIT=0）；顺带补上 NOT NULL/DEFAULT 对照 |
| 3 | `check-plaintext400-headers.sh` 覆盖 task-api | ✅ 完成（EXIT=0） |
| 4 | API-003 彻底修法 | ✅ 完成（`364d8c3`） |
| 5 | Go↔RAG 跨语言 gRPC 互调 | ✅ 完成（`243b5e5`，Go 客户端 → 两端服务端） |
| 6 | Kafka 畸形消息活体对拍 | ✅ 完成（`ca37b4e`，五个用例） |
| 7 | NacosDiscovery 真注册中心行为 | ✅ 完成（Nacos 注册 → 注销） |
| 8 | 「Kafka 写入 → task-api 读到」闭环 | ⚠️ **不做** —— 两侧读写 Redis key **本来就不一致**（Go 侧也读不到自己写的缓存）⇒ 不是 RAG 的缺陷 |
| 9 | `parity-task-kafka.py` 纳入 CI | ⚠️ **未做** —— 要用 Testcontainers 改造**共享**构建基建，范围超出 task 域，需与其它域协调 |
| 10 | `task:goka:count:*` 保留策略 | ⚠️ **未做** —— 要给 Redis key 加 TTL/清理策略是**新增行为**，按 AGENTS.md 需单独拍板 |

## 读本域台账前必读

1. [`../../AGENTS.md`](../../../AGENTS.md) §3.1（Go 是唯一基准）/ §3.3（框架机制要运行证据）/ §3.5（**rpc 必须先于 api**）
2. 本域 [`../../Intentional_Deviations/task/rpc.md`](../../Intentional_Deviations/task/rpc.md)
   —— 里面有 **4 项「不是偏离但极易被当成回归修回去」**的东西
3. 本域 [`../../Pitfalls/task/rpc.md`](../../Pitfalls/task/rpc.md)
   —— 「跑绿」在本域曾经意味着「15 条真 PG 用例一条没跑」；
   2026-10-23 又添了 **5 条**（PITFALL-06 ~ 10），其中 06/07 记的是
   **「整条消息链路是死的，而 81 条测试全绿」**这件事是怎么发生的；
   2026-10-24 再添 **5 条**（PITFALL-11 ~ 15），记的是
   **「上一版台账的根因结论是误判」**这件事是怎么发生的

## 共享索引未更新（按 `docs/Worktree.md` §3 留给合并后统一处理）

- `docs/DRIFT/_INDEX.md`（task 域行）
- `docs/DRIFT/_cross/component-coverage-matrix.md`（14 个零台账 → 12 个）
- `AGENTS.md` §0 / §3.5
- `docs/DRIFT/_MANIFEST.json`

## ⚠️ 本分支改到了**共享工具**（合并时留意）

`tools/difftest/` 下三个脚本是**多域共用**的，本轮为 task 域做了扩展：

| 文件 | 改动 | 对其它域的影响 |
|---|---|---|
| `check-proto-static.sh` | 加 task 域 + 退出码改成有差异返回 1 | users/admin 的判定内容不变；**退出码语义变了**（原先恒 0） |
| `check-ddl-static.py` | 改 `--domain` 参数化 + 加 NOT NULL/DEFAULT 对照 + 排除 Flyway 账本表 | users 域默认参数与原有输出**不变**（多一节补充对照） |
| `check-plaintext400-headers.sh` | 加 task 域 + 端口不可达判 SKIP | user 域的判定与默认值**不变** |
| `run.sh` | **修 2 处既有潜伏 bug**（`$var` 紧跟多字节字符 ⇒ `set -u` 下硬失败） | 只改了 2 个错误分支的 echo 文案，判定逻辑不变 |
| `parity-task.py` | task 域私有脚本 | 无 |

⇒ 合并时若其它域也在改这三个文件，需要人工合。
