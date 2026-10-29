# like 域 **双侧活体对拍探针**（Go 真源栈 + RAG 栈 ⇒ 同一请求 ⇒ 逐字节比对）

> **落点理由**：`tools/api-full-parity-probe/like/` 是 **R2 真私有**目录（只被 like 域引用 ⇒ 按
> AGENTS.md §5.3f「共享判据 = 依赖度数，不是目录位置」⇒ **不属共享层** ⇒ 本 lane 可写）。

## 目的

补三条**只在「两侧同时跑」时才能看见**的缺口：

| 缺口 | 追问的问题 | 探针阶段 |
|---|---|---|
| `DRIFT-LIKE-003-A`（事实投递链路） | 两侧投给 DataCenter 的**字节**是不是同一条？ | 阶段 B |
| `E60`（双失败错误传播） | Redis 与 DB **同时**失败时，两侧是不是都记了业务错误 `5011`？ | 阶段 C |
| 域内 HTTP 面（§3.5 组件级） | 6 个端点 + 2 条失败路径，两侧**逐字节**一致吗？ | 阶段 A |

## 用法

```bash
bash tools/api-full-parity-probe/like/run.sh          # EXIT=0 = 三段全绿
bash tools/api-full-parity-probe/like/mutate-parity.sh # 变异验证（M1~M3 ⇒ 必须精确转红）
```

退出码：`0` = 三段全绿 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失。

可用 env 覆盖：`GO_REPO`（真源路径，默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`）·
`ETCD_PORT` / `KAFKA_PORT` / `NACOS_PORT` / `REDIS_HOST_PORT` / `JAEGER_PORT` ·
`JWT_SECRET` / `USER_JWT_SECRET` / `DC_TOKEN` ·
`PORT_WINDOW_START` / `PORT_WINDOW_END`（端口窗口，默认 18161-18170）·
`PG_READY_TIMEOUT` / `REDIS_READY_TIMEOUT`（**墙钟秒**，默认 240 / 60 —— 机器负载高时 PG 初始化会慢）。

### 端口：**自动挑空闲的**（不许硬编码撞车）

端口窗口 = **18161-18170**（可用 `PORT_WINDOW_START` / `PORT_WINDOW_END` 改）⇒
脚本在窗口内**自动挑 9 个空闲口**：5 个服务口（Go api · Go rpc · RAG api · RAG rpc · 假 DC）
+ 4 个容器宿主口（两侧各 PG + Redis）⇒ **窗口内不够 9 个 ⇒ 直接 exit 2**（环境问题，不越界抢别人的口）。

> ⚠️ **不占用** like 域 Testcontainers 段 `22790-22819`（已被域内测试类逐个占死 ⇒ `LikeContainerFixedHostPortSegmentGateTest` 守门）。
> ⚠️ **不占用** OS 临时段 `49152-65535`（宿主中间件 Sea-etcd/kafka/nacos/redis/jaeger 在那儿）。
> ⚠️ **绝不按端口无差别 kill**：容器发布端口时是 `com.docker.backend` 持有该宿主口 ⇒
> 无差别 kill 会**当场杀掉 Docker daemon** ⇒ 脚本只杀**签名匹配本探针**的进程，
> 别人的进程占口 ⇒ **报错退出（exit 2）**，不越界动手。

### 收尾复核：**端口零残留**（不绿就不算跑完）

探针跑完 ⇒ 先 `teardown`（kill → 等端口关 → 删容器 → 按签名兜底清）⇒ 再
**断言本探针**真正用过的 **9 个端口**（5 服务口 + 4 容器宿主口）
在 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 下**一个都不剩** ⇒ 有残留 ⇒ **EXIT=2**。

> ⚠️ 断言**只算「真正用过的」端口**，**不含**候选池里没绑上的口 ⇒
> 否则别的 lane 的容器恰好在候选池里 ⇒ 会把**别人的**占用算成「本探针的残留」⇒ 假红。

### 验签密钥（**起进程时必须传**）

`USER_JWT_SECRET` = 与签发同值（默认取 `JWT_SECRET`）。
⚠️ RAG 侧 `service/like/like-api/src/main/resources/application.yml:85` 写的是
`access-secret: ${USER_JWT_SECRET}`（**无默认值**）⇒ 守卫 `LikeApiProperties#verifyAccessSecret()`
**fail-closed** ⇒ **不传 ⇒ like-api 起不来 ⇒ 探针 EXIT=2**。
⚠️ **只有 like-api 需要它** —— like-rpc 全模块零 JWT 引用。

## 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的源码与生成码**（`go build ./service/like/api ./service/like/rpc ./service/like/rpc/cmd/fact-dispatch`），
但真源 yaml 把主机名**写死**了 ⇒ 脚本生成**配置副本**，把主机名改成宿主可达地址。

> 🔴 **脚本第 3 步会断言真源仓仍然干净**：
> `git status --porcelain -- service/like` 必须为空 ⇒ 否则 `die`（exit 2）。
> ⇒ **冻结的真源一个字都没被改。**

### ⚠️ `${POSTGRES_*}` 不会被 go-zero 展开

真源 `service/like/rpc/etc/like.yaml` 里写的是 `${POSTGRES_USER}` 这类占位符，但
go-zero 的 `core/conf/config.go:60-66` **只在 `conf.WithEnv()` 时**才 `os.ExpandEnv`，
而真源 `service/like/rpc/internal/config/like.go:28` 调的是 `conf.MustLoad(file, &c)`（**没有** `WithEnv()`）
⇒ 真源跑起来时这些占位符是**字面量** ⇒ 脚本在配置副本里替换（而不是指望 env 生效）。

## 两侧各自的 Postgres / Redis（互不污染）

| 侧 | PG | Redis | 事实投递接收端 |
|---|---|---|---|
| Go | 容器 `r2like-go-pg-$$`（宿主口**自动挑**） | 容器 `r2like-go-redis-$$`（**自动挑**） | 假 DC `dc-go/` 输出目录 |
| RAG | 容器 `r2like-rag-pg-$$`（**自动挑**） | 容器 `r2like-rag-redis-$$`（**自动挑**） | 假 DC `dc-rag/` 输出目录 |

两侧**播种同一批数据**（`lib/seed_fact.py` 生成同一份 payload + 信封 + INSERT SQL）⇒
**绝不用空库**（空库 ⇒ 两侧都返回「没数据」⇒ 假绿）。

## 冻结的期望值（`expected.txt`）

格式：`CASE|<name>|<status>|<hex>`（hex 为**响应体**的小写 hex；`no_token` 的 hex **合法为空**）。

| CASE | HTTP | 内容 |
|---|---|---|
| `getlikecount` | 200 | `{"code":200,"msg":"OK","data":{"counts":{"9007199254740994":{"like_count":2,"dislike_count":1}}}}` |
| `getlikestate` | 200 | `{"code":200,"msg":"OK","data":{"states":{"9007199254740994":1}}}` |
| `gettargetlikerlist` | 200 | 点赞者列表（含 `timestamp`） |
| `getuserlikelist` | 200 | 目标列表（含 `timestamp`） |
| `getusertotallike` | 200 | `{"total_like_count":5}` |
| `likeaction` | 200 | `{"success":true,"message":"action success","like_count":3,"dislike_count":1}` |
| `no_token` | 401 | 空 body（合法） |
| `missing_required` | 200 | `{"code":5001,"msg":"系统内部错误","data":null}` |
| `likeaction_double_failure` | 200 | E60 场景：`like_count:4`（**两侧响应字节相同**） |

每次跑都要同时满足：`Go == RAG == expected.txt`（**逐字节**，不是「JSON 语义相同」）。

## 三段结构（任一差异即 EXIT=1）

- **阶段 A**（第 8 步）· HTTP 业务面：8 个 case 逐字节 + 状态码。
- **阶段 C**（第 9 步）· E60 双失败：Redis `ACL SETUSER default -zcard` ＋ `DROP TABLE like_record`
  ⇒ **两侧同时**踩到 Redis+DB 双失败 ⇒ 断言两侧都记业务错误（Go `"error_code":5011` · RAG `business_error` 行）。
- **阶段 B**（第 10 步）· 事实投递（003-A）：两侧各自 `fact-dispatch` ⇒ 假 DC 收到**同一条 event_id** 的字节
  ⇒ 比对**字节数 + sha256** + DB outbox 终态（排除墙钟列）。

## ⚠️ 证据边界（**必须一起读**，否则会把这 9 条读成「全闭合」）

1. **阶段 C 只能观测到「走了记业务错误这条分支」，读不到码值。**
   Go 侧 go-zero 文件日志是 JSON 行 ⇒ `"error_code":5011` **码值可直接读**；
   RAG 侧 SLF4J 的 key-value（`service/common/logger/Logger.java:273` `addKeyValue("error_code", code)`）
   **不进默认 logback pattern**（全仓无 logback 配置）⇒ stdout 里只有 `business_error` 四个字。
   ⇒ **码值 5011 由进程内守门 `LikeUserLikeTotalDoubleFailureGateTest`（Logback ListAppender）钉住**。
   ⇒ 「key-value 不进默认 pattern」是**十个组件共性** ⇒ **不在本域单独修**（单改会造成本域与其它九域不一致）。
2. **`likeaction` 是写路径** ⇒ 每个侧**只发一次**请求（一次 `curl` 同时拿 body + 状态码）；
   发两次会改两次计数 ⇒ 与 `expected.txt` 的 `like_count` 对不上。
3. **`likeaction` 的 `author_id` 固定取 `"0"`** ⇒ 避免触发 `notifyArticleLiked` → `message.rpc`
   （宿主没有该服务 ⇒ Go 等 client timeout 吃满 3s REST 预算、两侧耗时不可比）
   ⇒ **「通知文章被赞」这条分支明确不在本探针的比较面内**。
4. **RAG 侧 Kafka listener 必须关**（`--spring.kafka.listener.auto-startup=false`）
   ⇒ 否则 RAG 会消费 Go 侧推的 `like_promotion_topic` 消息、在 RAG 库多写一条事实 ⇒ 两侧状态不可比。
5. **事实投递比对按 `event_id` 定位文件**，不按文件序号 ⇒ 与投递顺序无关；DB 终态比对**排除墙钟列**
   （`technical_received_at` / `delivered_at`）。

## 探针本身踩过的坑（写下来免得下一个人再踩一遍）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | Go rpc 连 PG 报「用户 admin 不存在」 | 真源 yaml 的 `${POSTGRES_*}` 占位符**不被 go-zero 展开**（`conf.Load` 需 `conf.WithEnv()`，真源没传） | 在**配置副本**里替换 |
| 2 | Go rpc 多绑一个 `9098` 导致撞端口 | go-zero `zrpc` 的 `ServiceConf.SetUp()` 会 `devserver.StartAgent` | 副本里关 DevServer |
| 3 | RAG like-rpc「起不来」，JDBC URL 变成 `jdbc:postgresql://127.0.0.1:   · :18133 …` | `pick_port` 在 `$( )` 里执行 ⇒ 它的 `say` 写 **stdout** ⇒ 提示文字被混进端口变量 | `say` 走 **stderr** |
| 4 | `docker run -p` 报 `port is already allocated` | 容器刚删时 docker-proxy 仍占宿主口 | 端口**阶梯**重试 + 打 docker 原始报错 |
| 5 | 上轮残留容器删不掉（`docker rm -f` 报 no such container） | 容器名带 `$$` ⇒ 上一轮名字与本轮不同 | 启动前按 `r2like-` **前缀**清残留 |
| 6 | RAG 的 `like.fact.dispatch-enabled` **静默不生效** | Spring env 松弛绑定把 `dispatch-enabled` 映射成 `DISPATCHENABLED`（**无下划线**） | 一律用**命令行参数** `--like.fact.…` |
| 7 | RAG 库多出一条事实 ⇒ 两侧终态对不上 | RAG like-rpc 带 `@KafkaListener` ⇒ 会消费 Go 推的消息 | `--spring.kafka.listener.auto-startup=false` |
| 8 | 首请求 1015「No servers found for like-rpc」 | Nacos 残留实例 + 发现缓存 | 「首个**非 1015** 响应」就绪闸门 |
| 9 | fact-dispatch 秒退，报错只有 `DELIVERY_FAILED` | `RTW_SERVICE_VERSION` 必须是 **40 位小写 hex**（`communityfact/logging.go:19`），写 `1.0.0` 直接校验失败 | 写 40 位小写 hex |
| 10 | 「120s 没 ACCEPTED」 | 第二个 like-rpc JVM 与第一个抢 `18124` ⇒ BindException | 启第二个前先 kill 第一个 |
| 11 | Redis 故障注入报错 `ERR syntax error` | Redis 7 里 `CONFIG SET rename-command` 不可用 | 改用 `ACL SETUSER default -zcard`（确定性失败，且不影响 Lua 的 ZADD/HGET/INCR） |
| 12 | 测 gRPC `UNAUTHENTICATED` 时**读出空响应体** | `HttpURLConnection` 对 401 有隐式认证协商 ⇒ **吃掉响应体** | 用 JDK `HttpClient` |
| 13 | `RAG_PIDS[@]: unbound variable` | macOS 自带 **bash 3.2**：`set -u` 下展开**空数组**报错 ⇒ cleanup 提前死 ⇒ 容器残留 | 用 `${A[@]+"${A[@]}"}` 形式 |
| 14 | 探针 **EXIT=2**（`RAG like-api` 起不来） | 探针只导了 `JWT_SECRET`（**签发侧**）⇒ **没给验签侧**传 `USER_JWT_SECRET` ⇒ RAG 侧 `application.yml:85` 是 `access-secret: ${USER_JWT_SECRET}`（**无默认值**）⇒ 守卫 `LikeApiProperties#verifyAccessSecret()` **fail-closed** ⇒ 进程起不来 | 探针补 `USER_JWT_SECRET="${USER_JWT_SECRET}"`（与签发同值） |
| 15 | 跑完一轮后 `:18121`/`:18122` **仍在 LISTEN** ⇒ 下一轮撞端口 | `( cd X && cmd ) &` ⇒ `$!` 是**子 shell** 的 PID（bash 对**异步**子 shell 会**再 fork 一次**）⇒ 真服务进程是它的**儿子** ⇒ `kill $!` 只杀得掉子 shell ⇒ **孤儿继续占口** | 启动侧改 `exec`（子 shell 被**替换**成服务进程）＋ 收尾按端口复核 ＋ 收尾后 `lsof` 断言零残留 |
| 16 | 负载高时（load average 到 **36**）「PG 起来了但没就绪」⇒ 探针误判成环境坏了（EXIT=2） | 就绪等待按「**迭代次数**」（`60 次 × sleep 1`）⇒ 负载高时单次 `docker exec` 要 1~3s ⇒ **实际远小于 60 秒** ⇒ PG 还没 init 完就被判失败 | 改成**墙钟 deadline**（`PG_READY_TIMEOUT`，默认 240s）＋ 轮询里看**容器状态**（`exited`/`dead` ⇒ 立刻失败，别白等） |
| 17 | `nc -z` 说口是空的，`docker run -p` 却报 `port is already allocated` | `nc -z` 只问「能不能连上」⇒ docker-proxy 保留着口时它**误报已释放** | 等端口释放一律用 **`lsof -nP -iTCP:<port> -sTCP:LISTEN`**（问「谁在 LISTEN」）判据 |

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：探针必须有**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 | 证明什么 |
|---|---|---|---|
| **M1** | 改 `expected.txt` 里一枚 hex（`7b22`→`7b23`） | `run.sh` **EXIT=1** | 「冻结的期望值」确实在参与判定，不是摆设 |
| **M2** | 改 RAG `LikeErrorMessages` 的 5001 文案（「系统内部错误」→「内部错误」） | `run.sh` **EXIT=1** | 「Go == RAG」不是恒等式，RAG 侧的字节真的在比 |
| **M3** | 把 `LikeTaskProgressPublisher` 的 `logger.logBusinessErr(...)` 降级成普通 `LOG.warn`（≈ E60 修前行为） | `run.sh` **EXIT=1** | 「E60 已销案」不是台账上的空话（业务错误分支没被观测到就会红） |

> ⚠️ 还原一律用「文件备份 + 复制回来」，**绝不用 `git checkout --`** —— 本 lane 的改动**还没提交** ⇒
> `git checkout` 会把它们一起回滚掉。
> ⚠️ M3 的替换串**绝不能**含 `business_error` 子串 —— 探针阶段 C 就是 grep 这个标记
> （`service/common/logger/Logger.java:273`）⇒ 留着它变异后照样绿 ⇒ 变异「不精确」。
