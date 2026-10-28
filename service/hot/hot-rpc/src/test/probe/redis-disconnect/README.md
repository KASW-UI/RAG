# redis-disconnect 探针（Go 腿 ＋ 双侧对照）

> **规格来源**：[`docs/DRIFT/hot/rpc.md`](../../../../../docs/DRIFT/hot/rpc.md) **§十二.9**（本探针就是那节的实现）
> **落点说明**：本目录在 **`service/hot/hot-rpc/src/test/probe/`** 下 ⇒ ⇒ **属域侧写权面**
> ⇒ ⇒ `tools/redis-disconnect-probe/` 是**共享层**写法（`tools/**` 归 G4）⇒ ⇒
> ⇒ ⇒ **两份实现不要同时存在** ⇒ ⇒ 归口见 `docs/DRIFT/hot/rpc.md` §12.9 的 📌 归口注记。

## 它回答什么

Redis 连接被**服务端优雅关闭**（`docker stop` = SIGTERM ⇒ FIN，不是 RST）之后，
客户端**第一条命令**多久失败？

| 腿 | 期望 | 实测（2026-10-28） |
|---|---|---|
| **Go**（本探针）| **快速失败**（go-redis 重试退避后拒连）| hot **1546 / 1682 / 1741 ms** · comment **1722 / 1742 ms** ⇒ `class=fast-fail` |
| **RAG**（域内既有守门）| **1 ms 立即拒单**（共享层 `REJECT_COMMANDS`）| `HotRedisDisconnectFastRejectGateTest` / `CommentRedisDisconnectFastRejectGateTest` |

⇒ ⇒ **两侧语义相同（都「快速失败」），量级不同** ⇒ ⇒ **判据是语义，不是毫秒数**。

## 怎么跑

```bash
# 只跑 Go 腿（**免 maven** ⇒ 任何时候都能跑）
bash service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh --mode hot
bash service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh --mode comment

# 三段式全跑（Go 腿 → 与 expected.txt 逐字节 diff → Java 腿 ⇒ **要 maven**）
bash service/hot/hot-rpc/src/test/probe/redis-disconnect/run.sh --mode hot --full
```

**退出码**：`0` = 与冻结基线一致 · `1` = 结论不一致（真源漂移或环境变了）· `2` = 环境/参数问题。

## 三条纪律（别踩）

1. 🔴 **Options 按真源逐字取，其余全走库默认** ⇒ 不要自己发明字段
   - `hot` ⇒ `service/hot/rpc/internal/svc/service_context.go:17-20` ⇒ `{Addr, Password}`
   - `comment` ⇒ `service/comment/rpc/internal/cache/redis.go:17-19` ⇒ `{Addr}`（Password 留空）
   - `zeroway` ⇒ `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40` ⇒ `{MaxRetries:3, MinIdleConns:8}`
   - ⚠️ **「库默认」= go-redis v9.20.0 的默认值**（`ReadTimeout 3s` · `DialTimeout 5s` · `PoolSize 10×GOMAXPROCS` · `PoolTimeout 4s`）
2. 🔴 **`go.mod` / `go.sum` 整体继承真源**（只改 module 名）⇒ ⇒ **不要 `go mod tidy`**
   （tidy 会去解析依赖的**测试依赖**（go-zero → yaml.v2 → check.v1 → rogpeppe），那些未必在本地 module cache 里 ⇒ tidy 必然失败，而探针根本不需要它们）
   ⇒ ⇒ 本目录的 `go.mod` 与真源的差异 = **只有 module 名那一行**；`go.sum` **逐字节一致** ✔
3. 🔴 **期间不发任何命令** ⇒ ⇒ 静默等 1000ms 之后才让外部 `docker stop` ⇒ ⇒
   ⇒ 否则会**掩盖**「断连后第一条命令」的排队窗口 ⇒ ⇒ 量出来的数不是那个数。

## 端口纪律

- 每组**各自**取一个空闲端口（容器还在占着 ⇒ 不能复用同一个）
- 取值范围 **20000–29000** ⇒ ⇒ **避开 OS 临时端口段 49152–65535**
  ⇒ ⇒ 那个口**不可能**被别的进程临时占走 ⇒ ⇒ 消灭「回应我的那个口上不是我的容器」那类 flake
  （同款纪律见 `docs/Pitfalls/article/rpc.md` 的 `PF-ARTICLE-RPC-016`）
- 取之前**先 bind 自检** ⇒ 占了就换下一个
- ⚠️ **绝不碰 `difftest5k-redis` / `cdifftest-redis` 等共享容器** ⇒ ⇒ 本脚本**自己起、自己收**（`docker rm -f`）

## 输出标记

| 标记 | 含义 |
|---|---|
| `PROBE-READY` | 已连上且 PING 成功（**这一步不过 ⇒ 测的是「连不上」⇒ 无效**）|
| `PROBE-DOCKER-STOP err=… out=…` | 外部优雅关闭的结果 |
| `PROBE-RESULT mode=… first_command_ms=… err=…` | **第一条命令的失败耗时**（观测量）|
| `PROBE-VERDICT mode=… class=… err_present=…` | **归一化结论**（判据 ⇒ 与 `expected.txt` 逐字节 diff）|
| `PROBE-FAIL …` | 环境/参数问题 |

## 与域内既有守门的关系（**不重复造**）

- 域内已有：`HotRedisDisconnectFastRejectGateTest`（hot）· `CommentRedisDisconnectFastRejectGateTest`（comment）⇒ ⇒ 守的是 **RAG 腿**；
- 本探针守的是 **Go 腿 ＋ 双侧对照** ⇒ ⇒ **不同轴**；
- ⚠️ **不覆盖**：① 池耗尽那形态（由各域 `max-wait` 那条覆盖）② 「断连时 gRPC 响应层客户端看到什么」（要真发一次 gRPC 调用）③ 每个域**自己**跑一遍（**不外推** —— 各域域上下文不同 ⇒ customizer 数量可能不同）。
