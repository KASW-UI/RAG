# follow 域 **HTTP 层**双侧活体对拍探针

> **落点理由**：`tools/api-full-parity-probe/follow/` 是 **follow 域私有**目录（只被 follow 域引用
> ⇒ 按 AGENTS.md §5.3f「共享判据 = 依赖度数，不是目录位置」⇒ **不属共享层** ⇒ follow lane 可写）。

## 目的

补 `docs/RoundPlan/Round-2.md` **T-2.5**（证据缺口 **G-1**）。

在此之前，follow 域台账写的是「**52/52 逐字节一致**」，但那批探针脚本
（`/tmp/probe.sh` / `probe_data.sh` / `probe_ext.sh` / `cmp.sh` / `u3d_probe.sh` / `u3c_probe.sh` /
`blackhole3.py`）**全在 `/tmp` 且已消失** ⇒ 台账自己写明「故障注入装置应从 `/tmp` 提升为仓库资产
—— **本轮未做**」⇒ 「52/52」**今天完全不可复算**。

本探针把「**两侧同时起服务 + 两侧各自播种同一行 + 同一请求 + 比对字节**」搬进版本控制，
并把期望值**冻结**进 `expected.txt` ⇒ 判定条件是 **Go == RAG == expected.txt** 三方相等
（期望值本身参与判定，不是摆设）。

## 用法

```bash
bash tools/api-full-parity-probe/follow/run.sh          # EXIT=0 = Go == RAG == expected.txt 逐字节一致
bash tools/api-full-parity-probe/follow/mutate-parity.sh # 变异验证（M1~M5 ⇒ 必须按预期表现）
```

退出码：`0` = 三方逐字节一致 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失。

可用 env 覆盖：`GO_REPO`（真源路径，默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`）·
`ETCD_PORT` / `REDIS_PORT` / `JAEGER_PORT` / `NACOS_PORT` ·
`GO_API_PORT` / `RAG_API_PORT` / `GO_RPC_PORT` / `GO_USER_RPC_PORT` / `RAG_RPC_PORT` ·
`GO_PG_PORT` / `RAG_PG_PORT` · `JWT_SECRET` · `JWT_USER_ID` ·
`RECORD=1`（用 **Go 真源** 重录 `expected.txt`）· `KEEP_WORK=1`（保留临时目录）。

## 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的生成码/二进制**；但真源 yaml 把主机名**写死**了
（`etcd:2379` / `postgres` / `redis:6379` / `jaeger:4317`）⇒ 脚本生成**配置副本**，
把那些主机名改成宿主可达地址。

> 🔴 **脚本第 3 步会断言真源仓仍然干净**：
> `git status --porcelain -- service/follow service/user` 必须为空 ⇒ 否则 `die`（exit 2）。
> ⇒ **冻结的真源一个字都没被改。**

## 拓扑（两侧同时跑）

```
                    ┌──────────────── Go 真源栈（Sea-RideTheWind_golang）─────────────────┐
  客户端 ──HTTP──▶  │ follow-api :18101 ──gRPC(etcd follow.rpc)──▶ follow-rpc :18103      │
                    │        │                                        │                │
                    │        └──gRPC(etcd user.rpc)──▶ user-rpc :18104 ─┘  PG :18106     │
                    └───────────────────────────────────────────────────────────────┘
                                     │ 同一个 user-rpc 进程
                    ┌──────────────── RAG 栈（本仓库）────────────────────┐
  客户端 ──HTTP──▶  │ follow-api :18102 ──gRPC(Nacos follow-rpc)──▶ follow-rpc :18105  PG :18107
                    └───────────────────────────────────────────────────────────────┘
```

| 侧 | 进程 | 端口 | 服务发现 | 库 |
|---|---|---|---|---|
| Go | `follow-api` | 18101 | — | — |
| Go | `follow-rpc` | 18103 | etcd `follow.rpc` | PG :18106 |
| Go | `user-rpc`（**下游，两侧共用同一个**） | 18104 | etcd `user.rpc` **+ Nacos `user-rpc`** | PG :18106 |
| RAG | `follow-api` | 18102 | — | — |
| RAG | `follow-rpc` | 18105 | Nacos `follow-rpc` | PG :18107（自己的 Flyway 建表） |

### 为什么必须把 **user-rpc** 一起拉起来

follow 域 8 个 RPC 里有 **6 个**会调 `ensureUserExists`（Go `service/follow/rpc/internal/logic/helper.go:81-94`
⇒ `UserRpc.GetUser`）：`Follow` / `Block` / `GetFollowList` / `GetBlockList` / `GetFollowerList` / `GetRecommendations`。
**只有 `Unfollow` 与 `Unblock` 不调**。

⇒ 若下游 user-rpc 不可达，这 6 个 logic 全部落到 Go `helper.go:87-89` 的**分支②**
（`codes.Internal` + `ErrorServerCommon`）⇒ 业务码塌成 **5001** ⇒
**所有读路径用例都变成「两侧都是 5001」** ⇒ 既测不到数据、又分不出「真一致」与「都挂了」。

⇒ 本探针**直接跑真源的 `service/user/user/rpc`**（GORM AutoMigrate 自建 `users` 表 ⇒ 无需外部 DDL）
⇒ 两侧调用**同一个** user-rpc 进程（Go 侧走 etcd、RAG 侧走 Nacos）。

## 播种（**两侧各自的库里各一份**）

| 表 | 行 | 用途 |
|---|---|---|
| `follow_relation` | (1001→1002) (1001→1003) (1002→1001) (1002→1004) | 关注 / 粉丝 / 推荐三张列表的**非空**样本 |
| `block_relation` | (1001→1005) | 黑名单样本 |
| `users`（只在 Go 侧 PG） | uid 1001~1005 | user-rpc 的存在性校验（两侧**共用**这一个 user-rpc ⇒ 只需一份） |

> ⚠️ **`create_time` 全部互不相同** ⇒ `ORDER BY create_time desc`（Go `model/follow.go:78-92`）的顺序确定。
> 同值时 PG 不保证顺序 ⇒ 输出顺序会随机 ⇒ 探针会**假红**。

> ⚠️ **绝不拿空库当证据**：「两侧都空」分不出「相同」与「都空」⇒ 必须播种同一批行
> （`mutate-parity.sh` 的 **M3** 专门盯这一条：让 RAG 侧多一行 ⇒ 探针必须转红）。

## 实测结果（2026-10-29 · `align/r2-follow`）

**20 条用例 · 逐字节三方一致（Go 真源 == RAG == 冻结期望值）**：

| 组 | 条数 | 用例 |
|---|---|---|
| 读路径（数据） | 9 | `follow_list_seeded` / `follower_list_seeded` / `block_list_seeded` / `follow_list_empty` / `recommendations_seeded` / `follow_list_limit_zero` / `follow_list_limit_max` / `follow_list_offset_neg` / `block_list_wrong_owner` |
| 协议层 | 6 | `no_auth_header` / `bad_jwt` / `follow_missing_target` / `follow_bad_target_type` / `follow_unknown_path` / `follow_wrong_method` |
| 写路径 | 5 | `follow_self_relation` / `follow_target_not_found` / `follow_new_relation` / `unfollow_seeded` / `unblock_seeded` |

钉住的**字节级差异点**（都是「只有真跑才看得见」的）：

| 用例 | 冻结的字节 | 为什么值得钉 |
|---|---|---|
| `follow_list_empty` | `{"code":200,"msg":"OK","data":{"user_ids":null}}` | **空列表是 `null` 不是 `[]`** —— 中间隔着一次 protobuf 序列化（proto3 repeated 空 ⇒ 字段缺失 ⇒ Go 侧解成 nil） |
| `recommendations_seeded` | `{"target_id":"1004","mutual_score":20}` | `target_id` 带 `,string` ⇒ **出参是字符串**；且字段序 = **struct 声明序**（Jackson 全显式命名时走**字母序** ⇒ 需 `@JsonPropertyOrder` 钉死，见 DRIFT-FOLLOW-005） |
| `follow_list_seeded` | `"user_ids":[1003,1002]` | `user_ids` **不带** `,string` ⇒ 出参是**数字**（与 `target_id` 相反的不对称） |
| `no_auth_header` / `bad_jwt` | HTTP **401 + 空 body** | Go `rest/handler/authhandler.go:106-122` 只 `WriteHeader(401)`，不写字节 |
| `follow_unknown_path` | HTTP 404 + `404 page not found\n`（38 字节） | go-zero `router/patrouter.go:93-97` |
| `follow_wrong_method` | HTTP **405 + 空 body** | go-zero `router/patrouter.go:79-82` 是**裸状态码**（与 404 形状相反） |
| `block_list_wrong_owner` | `{"code":5005,"msg":"forbidden"}` | `block/list` 与 `recommendations` 有**归属校验**，另两个 list 端点**没有**（Go 侧刻意的不对称） |
| `follow_self_relation` | `{"code":5201,...}` | 自关系在 **rpc 层**判（`helper.go:36-41`），api 层不判 |
| `follow_target_not_found` | `{"code":5101,"msg":"user not found"}` | user-rpc 明确回 NOT_FOUND ⇒ 分支①（≠ 下游挂掉的 5001） |

### ⚠️ 这 20 条**不等于**「follow 域双侧活体全闭合」

1. **只覆盖 HTTP 层**。gRPC wire 层（`follow-rpc` 的 8 个方法的 proto 字节）**不在本探针范围**
   ⇒ 域台账里 `follow-proto` / rpc 层的证据仍是各自独立的那几条，**不要与本探针混记**。
2. **写路径用例是「顺序敏感」的**：放在矩阵最后（先读后写），且各用不同 `target_id`
   ⇒ 任何一条写用例改了库，后续读用例就会漂移 ⇒ **用例顺序不可随意调整**。
3. **JWT 是探针自签的**（`mint-jwt.py`，同一把 secret）：follow 域**只验签、不签发**
   （签发方是 user-api）⇒ 探针不必把整个 user 域拉起来。
4. **`user_ids` / `target_id` 的 `,string` 不对称**只在本探针的 2 条用例里被间接覆盖；
   `,string` 的**入参**兼容（数字/字符串都收）只在 `{"user_id":"1001"}` 这条里被覆盖。

## 探针本身踩过的坑（写下来免得下一个人再踩一遍）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | RAG 侧所有读用例塌成 **5001** | Nacos 临时实例靠**客户端心跳**保活；探针是 `curl` 手工注册 ⇒ 没人发心跳 ⇒ ~15s 后被摘掉 ⇒ 表现为「**服务名在、hosts 为空**」⇒ RAG 侧 `No servers found for user-rpc` | 后台**保活循环**每 5s 重注册一次；且**注册完必须断言实例在列表里**（`nacos_assert_instance`） |
| 2 | Nacos 持久实例注册返回 **500**：`The Raft Group [naming_persistent_service_v2] did not find the Leader node` | `ephemeral=false` 走 Nacos 的 **Raft** 持久化组 ⇒ 本机 Nacos 无 leader | **不用持久实例**；走 ephemeral + 保活（见 #1） |
| 3 | **所有用例都被误判为「Go != RAG」**（但 body 一模一样） | 本机 `rm` 被回收站守卫接管（`mavis-trash`）且**往 stdout 打印** `mavis-trash: moved to trash: …` ⇒ 污染了收 body 的函数的返回值 | 收 body 的临时文件一律落在 `${WORK}` 且**不删**（`rm -rf` 也被拦 ⇒ 临时目录留给系统回收） |
| 4 | 请求失败时读到**上一次的残留 body** ⇒ **假绿** | 旧探针用**固定 `-o` 文件**（= **FAILING-010**） | 每个请求一个 **`mktemp` 唯一文件** + **`http_code == 000` 守卫**（两条都去掉就会假绿，见 `mutate-parity.sh` 的 **M5**） |
| 5 | 就绪闸门放行了「两侧都是 5001」 | 判据写成「非 1015 即就绪」；但本域的 **5001 = 下游 user-rpc 挂了**（Go `helper.go:87-89` 分支②） | 判据收紧为 **`"code":200`** 才算就绪 |
| 6 | 「注册进 etcd 了」≠「服务可用」 | go-zero **先注册 etcd 再 bind 端口**（`zrpc/internal/rpcpubserver.go:32-33`） | 先等 etcd 键、**再**等端口监听，两步都要 |
| 7 | etcd 精确键查**永远查不到** | go-zero 的键是**前缀族** `<key>/<leaseID>`（`core/discov/publisher.go:191-196` + `clients.go:39-41`） | 按**前缀**查（`get <prefix> --prefix`） |
| 8 | RAG api 起不来：`Can't connect to channel follow-rpc` | api 的 `immediate-connect-timeout: 3s`（= Go `zrpc.MustNewClient` 的 3s 阻塞拨号，DRIFT-G1-002）**被冷启动的 Nacos 客户端初始化吃光**（实测：Nacos client 建于 08:02:30.5，08:02:33.5 即超时） | 探针专用覆盖 `--grpc.client.follow-rpc.immediate-connect-timeout=60s`（**只放宽启动期**，请求路径一字未改）+ 启动重试 3 次 |
| 9 | Go api 启动即 panic | Go 侧 `zrpc.MustNewClient` 启动时**阻塞拨号 3s**，下游没完全就绪就 panic ⇒ 探针假挂 | 起 api 前 `sleep 3`（等下游端口真正监听） |
| 10 | 配置副本的端口**没改掉** ⇒ 端口一被占就 panic | `sed -e "s|^  Port: 8889$|..."` 这类**缩进写错**的规则会**静默不命中** | 改对缩进 + **逐条 grep 断言**（改了哪项就断言哪项）；另外真源 yaml 的 **`DevServer.Port`**（16064 / 16065 / 9094）也要改，否则与别的 lane 撞端口 |
| 11 | 容器名固定 ⇒ 别的 lane 的 cleanup 晚到把本轮的库删了 | 容器名冲突 | 容器名带 `$$`（每轮唯一），前缀 `r2follow` |
| 12 | 输出顺序随机 ⇒ 假红 | `ORDER BY create_time desc` 在 `create_time` **同值**时顺序不保证 | 播种时 `create_time` **全部互不相同** |
| 13 | `RAG_PIDS[@]: unbound variable` | macOS 自带 **bash 3.2**：`set -u` 下展开**空数组**报错 ⇒ cleanup 提前死 ⇒ 容器残留 | 用 `${A[@]+"${A[@]}"}` 形式 |
| 14 | 清理临时目录时脚本自杀 | `rm -rf` 在本机被回收站守卫**整体拒绝** | 用 `mktemp -d` 并**留着**由系统清理 |
| 15 | `Go 侧 PG 起不来（:18106）` | 容器名带 `$$` ⇒ **上一轮的容器不会被本轮 cleanup 删掉**（上一轮若被强杀）⇒ 残留容器**即使 Exited 也仍占着发布端口**（docker-proxy 还在）⇒ `docker run -p` 报 `port is already allocated` | 启动前**清掉自己前缀**（`r2follow-`）的残留容器 ＋ `sleep 3` 等 docker-proxy 释放；再配**端口阶梯**（18106→18116→18126 / 18107→18117→18127）兜底 |
| 16 | 探针崩在第 3 步：`sed: unescaped newline inside substitute pattern` | 端口阶梯的**诊断信息打到了 stdout** ⇒ 而端口值是**命令替换**取回的 ⇒ 端口号被污染成「诊断文本 + 端口号」⇒ 后续 sed 的替换串里带**换行** | 阶梯的诊断信息一律走 **stderr**；端口值只走 stdout |
| 17 | 守卫「静默失效」⇒ 探针报**绿** | 测试钩子依赖的 `RAG_API_PID` 在脚本后半段被**重新置空** ⇒ 「杀 RAG 侧」永远不发生 ⇒ 失败路径根本没被触发 ⇒ 探针全绿（**看起来像通过**） | 守卫用的变量**只在初始化处置空**；⇒ **凡是「探针报绿」的结论，都要确认守卫真的触发过**（本探针在钩子里打 `🧪` 日志，M4 断言这条日志出现） |

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 |
|---|---|---|
| **M1** | 去掉 `RecommendUser` 的 `@JsonPropertyOrder` | 探针 **EXIT=1**，且 `recommendations_seeded` 那条被判不等（字段序漂移被抓到） |
| **M2** | 改 `expected.txt` 里一枚 hex | 探针 **EXIT=1**，且 `follow_list_seeded` 与冻结值不符（**期望值确实在参与判定**） |
| **M3** | 让 **RAG 侧**多灌一行 | 探针 **EXIT=1**，且 `follow_list_seeded` 与 Go 不等（**探针真在读两侧各自的库**，不是「两边都空」） |
| **M4** | RAG 侧在矩阵**中途挂掉**（`PROBE_STOP_RAG_AFTER`） | 探针 **EXIT=1**，且下一条报「**请求失败**」⇒ **不会**沿用上一次的残留 body |
| **M5** | 把 `run.sh` 改回旧写法（**固定 `-o` 文件 + 去掉 `000` 守卫 + 只比 body**） | 同场景下探针**误判成 ✅（假绿）** ⇒ **复现 FAILING-010** ⇒ 证明 M4 的红确实是守卫带来的<br>⚠️ **三处必须一起改**才等于「旧探针」：旧探针**只比 body**（`article` 探针的 `run_case` 就是如此）⇒ 本探针多比的 HTTP 状态码会让「200 vs 000」先把差异挡下来 ⇒ 假绿复现不出来 |

⇒ **M1/M2** 证明「RAG 侧字节」与「冻结期望值」两条链都有牙齿；
**M3** 证明「播种的数据」真的进了比较；**M4/M5** 证明「失败路径不会被当成通过」。
