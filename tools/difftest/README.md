# difftest —— Go ↔ Spring Boot 双端运行时对拍

把 `Sea-RideTheWind_golang`（真理之源）与本仓库的 Spring Boot 重构**同时起进程**，
喂**同一串请求序列**，逐字段比对两边的 HTTP 响应。

这是 AGENTS.md §3.3「框架机制必须验证实际生效」的终极形态：
单测跑在 mock 数据层上，双端对拍跑在**两套真实进程 + 两个真实数据库**上。

```
./run.sh                # 完整跑（含 mvn + go build）
./run.sh --no-build     # 复用 /tmp/difftest/bin 下的产物
./run.sh --keep         # 跑完不收摊，便于手工 curl
python3 diff.py --scenario user    # 只跑 user 链路 9 端点
python3 diff.py --scenario admin   # 只跑 admin 链路 13 端点
```

退出码 0 = 没有未预期的差异。

---

## 1. 对拍面：为什么只有 HTTP wire 层

| | Go | RAG |
|---|---|---|
| 迁移方式 | GORM `AutoMigrate` | Flyway |
| 用户表 | `users` | `users_user` |
| 头像表 | `user_avatar_history` | `users_user_avatar_history` |

表名差异是 AGENTS.md §3.4 已签字的有意偏离（A-03），
**两边物理上无法共用 schema** ⇒ DB 层对拍不存在，唯一可比面是 HTTP 响应。

两侧各跑一遍完整序列（各自维护自己的 token / uid / 邀请码状态机），
不能交错执行 —— 交错会让后一侧捡到前一侧已经消费掉的邀请码。

易变值（服务端生成的 uid、history_id、token、邀请码、过期时间、时间戳）
由 `Normalizer` 统一映射成 `<UID#1>` / `<TS>` 之类的占位符后再比对，
**类型差异不抹平**：Go 的 uid 是字符串（`json:"uid,string"`），
若 RAG 侧是数字，会作为一个真实差异报出来。

---

## 2. 两侧怎么绕开服务发现

两侧**都**摘掉服务发现层，这样才是对称的：

- **Go**：`zrpc/config.go:91-95` 的 `RpcClientConf.BuildTarget()` 对 `Endpoints` 短路，
  不进 Etcd 分支；server 侧删掉 `Etcd` 整块，`zrpc/server.go:42` 的
  `if c.HasEtcd()` 不成立 ⇒ 不注册。
- **RAG**：`SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false` 关掉自动注册，
  gRPC 下游地址用 `--grpc.client.*.address=static://127.0.0.1:<port>` 直连。

> ⚠️ **RPC 模块必须保留注册逻辑可装配。** user-rpc / admin-rpc 里的
> `NacosRegistration` 是自定义 `@Component`，构造注入 `NacosServiceManager` bean；
> 把 `spring.cloud.nacos.discovery.enabled` 关掉会让上下文起不来。
> 它们的注册失败被 Java 侧 try/catch 吞掉（只打 ERROR 日志、不 rethrow），进程照常服务。
> 这也是为什么诊断「Nacos 连不上」时**不能**认定进程挂了 —— 要看
> `Started XxxApplication` 那行有没有出现。

---

## 3. 端口规划

两侧都必须独占，且**不能碰本机既有服务**。实测踩过：

- RAG 默认 admin-rpc `8081` / admin-api `8889` 与 Go 相同 → 直接撞
- RAG 默认 user-rpc `49210` / user-api `49900` 被本机 docker-compose 的
  `Sea-user-rpc` / `Sea-user-api` 容器占着
- `8081` / `8889` 上还跑着 **`Sea-RideTheWind_java`** 的进程
  （AGENTS.md §1 明确排除在 RAG 范围外的另一个实现）—— **绝不能去 kill 它**

| | user-rpc | admin-rpc | user-api | admin-api |
|---|---|---|---|---|
| Go | 19004 | 18081 | 18888 | 18889 |
| RAG | 59210 | 58081 | 59900 | 58889 |

基础设施：PostgreSQL `55432`、Redis `56379`（同样避开本机 docker-compose）。

---

## 4. 踩过的坑（每一条都真实浪费过时间）

### 4.1 探针把「404」当「就绪」

`curl` 拿到任何 HTTP 响应都 exit 0。上一版把「打到别人服务上的 404」误判成本服务就绪，
**整个对拍结果因此变成 12 条噪声 DIFF**。
探针必须显式拒绝 `000` 和 `404`。

### 4.2 `curl -w '%{http_code}'` 失败时输出 `000`

再加一层 `|| echo 000` 会拼成 `000000`，被 `!= 000` 判成「就绪」。
`code=$(curl ...) || code=""` 才是对的。

### 4.3 `VAR=x $SOME_VAR java -jar ...` 会报 command not found

bash 在**解析期**就决定命令的赋值前缀边界，`$SOME_VAR` 展开出来的
`NAME=value` 语法上不是赋值词，于是被当成命令名去执行。
正确写法是 `env NAME=value java -jar ...`。

### 4.4 `assert_started()` 声明了 `name` 参数却从没赋值

`set -u` 下只在**真触发失败分支**时才炸 ⇒ 平时一路绿灯，把失败信息吃掉了。
本轮修好之后立刻暴露了下一个问题（见 4.5）。

### 4.5 「进程起来了但 schema 是空的」是一类静默失败

Flyway 认为 schema 已是 v3、跳过自己的迁移、`users_user` 根本不存在，
却照样打出 `Started UserRpcApplication`。RPC 照常监听，只有真正打 SQL 时才炸，
而那时候报错长得像业务 bug。
`run.sh` 里现在有 `assert_tables`，逐张表验 `information_schema.tables`。

### 4.6 `${POSTGRES_PASSWORD}` 在 Go 配置里不展开

冻结仓库 `user.yaml:13` / `admin.yaml:14` 都用了 `${...}` 占位符，
但入口是 `conf.MustLoad(*configFile, &c)`，**没带 `conf.UseEnv()`**，
go-zero 不会 `os.ExpandEnv`。详见 AGENTS.md §6.2.5 的留档。
副本里已换成字面值。

### 4.7 `AdminSeederBootstrap` 是 `@PostConstruct`

它在 `finishBeanFactoryInitialization` 阶段就 SELECT/INSERT，
表不存在时直接 `BeanCreationException: ... relation "admin_admin" does not exist`。
所以 admin 的 DDL 必须在 **admin-rpc 启动之前**建好，不能靠 `sleep` 猜。

### 4.8 RAG 两个 rpc 共用一个库时 Flyway 会炸

`flyway_schema_history` 一个 schema 只能有一本账，而两个模块的
`V1/V2` 版本号相同、内容不同。当前绕法是 admin-rpc 关 Flyway + 手工应用 DDL。
详见 AGENTS.md §6.2.5 DRIFT-NEW-015。

### 4.9 Read 工具会吃掉连续空格

`wait_http "..." "rag admin-api" 120` 里是两个空格。
按肉眼看到的单空格去 `edit` 会匹配失败，用 `python3` 做替换或 `od -c` 先验。

---

## 5. 这轮对拍抓出来的东西

跑通本身就是结论：**39 步 / 36 同 / 3 已签字 / 0 未预期差异**。

但它同时抓出了三个静态审查完全看不到的缺陷，都写进 AGENTS.md §6.2.5 了：

- **DRIFT-NEW-014（P0）** `user-api/application.yml` 漏了 `negotiation-type: plaintext`
  ⇒ user-api 连不上 user-rpc，所有业务码塌成 `1015 服务繁忙`。
  `admin-api` 当年踩过并修了（DRIFT-NEW-008），user 侧被漏掉。
- **DRIFT-NEW-015（P0）** Flyway 迁移版本号撞车 ⇒ `admin-rpc` 无法启动，
  而它按设计必须与 user-rpc 共用 `users_user`。
- **DRIFT-NEW-016（P2）** Go 的 `httpx.Parse` 把字段缺失当硬错误
  （只认 `,optional`，`,omitempty` 不算），RAG 无此校验。

---

## 6. 文件

| 文件 | 作用 |
|---|---|
| `run.sh` | 编排：基建 → Go 栈 → RAG 栈 → 对拍 → 收摊 |
| `diff.py` | 场景表 + 归一化 + 逐字段差分 |
| `docker-compose.yml` | 只起 postgres + redis（刻意不起 etcd / Nacos / jaeger） |
| `initdb.sql` | 建 `go_db` 与 `rag_db` |
| `conf/go/*.yaml` | **冻结 Go 仓库的副本**，运行时用 `-f` 指过去；真源一个字节都不动 |

> **纪律**：Go 侧绝不写任何文件进冻结仓库。真源 yaml 的 `Log.Path: ../../../../log/...`
> 是相对 `etc/` 的，会直接落进仓库里，所以副本已改成 `Mode: console`。