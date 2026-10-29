# metrics-probe/follow：follow 域 **指标抓取面**双侧活体对拍探针

> 落点编号：**S1-FOLLOW-001** · 对应台账条目：**DRIFT-FOLLOW-006**
> · 落地日期：**2026-10-29** · 域 lane：**G1 / follow**

## 它验什么

`follow-rpc` 的 5 个 `follow_rpc_*` 指标**全部在 rpc 层上报**
（Go：`service/follow/rpc/internal/logic/*.go` 的 `defer`；RAG：`FollowDomainService` /
`FollowRecommendationService`）⇒ 驱动流量必须**打到 rpc 层**（api 层只是转发）。

本探针一次性回答三个问题：

| # | 问题 | 判据 |
|---|---|---|
| ① | **RAG 侧真抓得到 `follow_rpc_*` 吗？** | `/actuator/prometheus` 必须 200 且含 `follow_rpc_*`（此前 follow-rpc 是 `WebApplicationType.NONE` 的纯 gRPC 进程 ⇒ **零抓取出口**） |
| ② | 两侧的 **series 集合**一致吗？ | 指标名 + 标签集合逐条比对（`le` 按**浮点数值**归一化 ⇒ `1` 与 `1.0` 视为同一个桶） |
| ③ | 两侧的**计数**一致吗？ | `request_total` / `relation_action_total` / `db_error_total` / `recommendation_size` / `…_count` 的**值**必须逐条相等 |

⚠️ **直方图的 `_bucket` / `_sum` 值不比** —— 它们取决于真实耗时 ⇒ 两侧必然不同
⇒ 比了就是假红。**只比它们的 series 集合**（桶边界 + method 标签）。

## 怎么跑

```bash
bash tools/metrics-probe/follow/run.sh
echo $?   # 0 = 一致 / 1 = 守门失效 / 2 = 环境问题（判定无效）
```

可选环境变量：

| 变量 | 作用 |
|---|---|
| `GO_REPO` | Go 真源路径（默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`） |
| `SKIP_BUILD=1` | **只**跳过 RAG 的 maven 构建（Go 二进制与 gRPC 客户端每次都重建 ⇒ 不会拿旧二进制自证） |
| `KEEP_WORK=1` | 保留临时目录（默认不留） |
| `PORT_SEG_START` / `PORT_SEG_END` | 端口段（默认 **18171-18180**，本 lane 专用） |
| `ETCD_PORT` / `NACOS_PORT` / `REDIS_PORT` / `JAEGER_PORT` | 宿主中间件端口 |

依赖：docker（宿主已在跑 `Sea-etcd` / `Sea-nacos` / `Sea-redis`）· Go toolchain（≥1.25）·
JDK 21+ · mvn（离线 `-o`）· curl · python3 · lsof · nc。

## 它怎么搭起「双侧可比」

### 两侧各一套、且**必须各一个 Postgres**

共用一个库 ⇒ Go 侧先跑完写路径 ⇒ RAG 侧再跑同一批请求会撞上「已存在 / 已删除」
⇒ 两侧的 `result` 标签不同 ⇒ **伪造出指标差异**。
⇒ 探针起**两个**隔离 PG 容器（`r4mprobe-go-pg-$$` / `r4mprobe-rag-pg-$$`），
两侧**播同一批行**（follow 4 / block 1 / users 6）。

### 驱动流量用**同一份** gRPC 客户端

`go/client/main.go` 在构建时从**冻结真源** `service/follow/rpc/pb/` 现拷现用
⇒ 同一份 proto 契约同时驱动 **Go 侧**与 **RAG 侧** ⇒ 不存在「两套客户端本身不可比」的问题。

请求矩阵（两侧**同序**执行，每步都比对两侧的 gRPC 状态码 + 响应体）：

* **成功路径 8 步**：8 个方法各一次成功
* **失败路径 10 步**：`user_id=0`（参数非法）、`target_id=0`（参数非法）、
  自关系、目标用户不存在 ⇒ 让 8 个方法**都**同时出现 `result="success"` 与 `result="fail"`

⚠️ **响应必须先一致**：两侧响应不一致 ⇒ 指标差异**不可归因于指标本身**
⇒ 探针在那一层直接 `exit 2`（环境/行为问题），不会把行为差异伪装成指标差异。

### 故障注入覆盖 `db_error_total` 的 (action, type) 维度

业务级失败**不会**产生 `db_error_total`（那是 DB 错误专用）。
⇒ 探针**改名表**（`ALTER TABLE … RENAME TO …_x`，用完原样恢复）制造真实 DB 错误：

| 改名的表 | 调用的方法 | 命中的 (action, type) |
|---|---|---|
| `follow_relation` | `GetFollowList` | `get_follow_list` / `list_follow_targets` |
| `block_relation` | `GetBlockList` | `get_block_list` / `list_block_targets` |
| `follow_relation` | `GetFollowerList` | `get_follower_list` / `list_follower_users` |
| `follow_relation` | `GetRecommendations` | `get_recommendations` / `list_direct_follows` |
| `block_relation` | `GetRecommendations` | `get_recommendations` / `list_block_targets` |
| `block_relation` | `Follow` | `follow` / `exists_any_block` |
| `follow_relation` | `Follow` | `follow` / `create_follow` |
| `follow_relation` | `Unfollow` | `unfollow` / `delete_follow` |
| `block_relation` | `Block` | `block` / `create_block_and_cleanup` |
| `block_relation` | `Unblock` | `unblock` / `delete_block` |

⇒ 覆盖 **11 个 type 中的 8 个**（两侧各 10 条 `db_error_total` series，**集合逐条相同**）。

### 顺带守住「Nacos 不能注册观测端口」

RAG 侧引入 web 容器后，`spring-cloud-starter-alibaba-nacos-discovery` 的**自动注册**
注册的是 **web 容器端口** ⇒ Nacos 里会出现「follow-rpc → 观测口」的错实例
⇒ `follow-api` 连过去就是**断链**（业务码塌成 1015）。
⇒ 探针断言：**Nacos 里 follow-rpc 只出现 gRPC 端口**（观测端口出现 ⇒ 直接 `exit 2`）。

## 冻结的期望差异（`expected-diff.txt`）

探针的实际差异必须与它**逐条相同**（多一条 / 少一条都算守门失效）。

当前唯一差异：**RAG 侧多 8 条 `follow_rpc_request_duration_seconds_max`**
（Micrometer 的 Timer max gauge；Go 的 `client_golang` 不导出 `_max`）⇒
**纯增项**，不影响 Go 侧任何 PromQL 语义 ⇒ 定级 **P3 / D 类**（已登记，不修）。

## 已知边界

* **`list_layer_follow_targets` / `list_blocker_users` 两个 type 覆盖不到** ——
  它们要求「第一次查询成功、第二次查询失败」，而改名表会让**第一次**也失败
  ⇒ 黑盒故障注入**到不了**那两个点。（不构成漂移：两侧都到不了。）
* **直方图的值不比**（见上）。
* **不覆盖 api 层**（`follow-api` 的 HTTP 面由 `tools/api-full-parity-probe/follow/` 负责）。

## 为什么这套东西当初是「假绿」的

* `spring-boot-starter-actuator` **只提供 endpoint + 自动配置，不提供 servlet 容器**
  ⇒ classpath 上没有 `jakarta.servlet.Servlet` ⇒ `WebApplicationType` 推导为 `NONE`
  ⇒ `management.endpoints.web.exposure.include: prometheus` 一直是**死配置**
  （**不报错、不生效**）⇒ 「配了」≠「抓得到」。
* 本探针的**就绪判据是「真抓到 `/actuator/prometheus` 且含 `follow_rpc_*`」**，
  不是「进程起来了」/「端口开了」⇒ 这正是 AGENTS.md §3.3 的「跑绿 ≠ 对」的反面。

## 防「拿着自己的一份副本自证」

* `pb` **不复制进版本库**：构建时从冻结真源现拷 ⇒ 真源改 pb，探针立刻响。
* Go 侧配置是**副本**（真源 yaml 写死主机名）⇒ 探针断言
  `git status --porcelain -- service/follow service/user` **为空** ⇒ 真源一个字节都没动。
* 期望差异是**冻结文件**，不是「跑出来什么就录什么」。
