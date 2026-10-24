# `comment` 域双侧活体对拍 harness

> **归属：`align/comment` 工作树 · 2026-10-24**
> 本目录是 `comment` 域**专属**的对拍工具。`tools/difftest/` 下的共享文件（`README.md` / `diff.py` /
> `matrix.py` / `conf/go/`）**本工作树不碰**。
>
> **产出台账** ⇒ [`docs/DRIFT/comment/rpc.md`](../../../docs/DRIFT/comment/rpc.md) §4.6 ·
> [`docs/DRIFT/comment/api.md`](../../../docs/DRIFT/comment/api.md) §4.6
> **踩坑** ⇒ [`docs/Pitfalls/comment/rpc.md`](../../../docs/Pitfalls/comment/rpc.md) PIT-COMMENT-RPC-07 / -08 / -09

## 它做什么

**同时起四个进程**（Go 真源 + RAG 各一套 rpc/api），跑**同一套 17 步请求矩阵**，
**逐字段差分**（响应码 / 业务码 / 文案 / 列表长度 / 每个计数字段）。

这是 AGENTS.md §3.3「两侧同时跑的探针」的**组件级**形态 ——
本仓其它绝大多数 ✅ 结论都只是「在该证据强度下一致」，**只有这里的结论是「两侧同时在跑时确实一致」**。

## 怎么跑

```bash
cd tools/difftest/comment

# ① 起 PG / Redis / Kafka
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d
#    （Kafka 用 apache/kafka:3.7.2 KRaft，**双 listener**：PLAINTEXT→127.0.0.1:59993 给宿主、
#      INTERNAL→localhost:19092 给容器内探针）

# ② Go 侧产物（CGO_ENABLED=0，静态编译）
GO=/Users/pr/Documents/java\&golang/Sea-RideTheWind_golang/service/comment
(cd "$GO/rpc"  && CGO_ENABLED=0 go build -o "$OLDPWD/bin/go-comment-rpc"  .)
(cd "$GO/api"  && CGO_ENABLED=0 go build -o "$OLDPWD/bin/go-comment-api"  .)

# ③ RAG 侧产物
WT=/Users/pr/Documents/java\&golang/RAG.worktrees/comment
(cd "$WT" && mvn -o -q -pl service/comment/comment-rpc -am -DskipTests package)
cp "$WT/service/comment/comment-rpc/target/comment-rpc-0.0.1-SNAPSHOT.jar" bin/rag-comment-rpc.jar
(cd "$WT/service/comment/comment-api" && mvn -o -q -DskipTests compile \
   && mvn -o -q dependency:build-classpath -Dmdep.includeScope=runtime \
      -Dmdep.outputFile="../../../tools/difftest/comment/api-cp.txt")

# ④ 跑
bash run_diff.sh
```

⚠️ `run_diff.sh` **会先检查前置产物**，缺哪个直接退出并告诉你缺哪个。

### 附：proto 复制忠实度守门（**不需要起任何容器**）

```bash
python3 tools/difftest/comment/check-proto-parity.py   # EXIT=0 = 一致
```

> 判据：剥掉 `option` / `package` + **归一化空白**（§3.4 允许缩进与空行统一）后逐行比对，
> 外加断言 `package` 仍归一（已签字偏离 `SD-2026-10-23-04`）+ RAG 侧无残留 `go_package`。
> ⇒ 把「proto 是复制粘贴」从**一次性快照**变成**可持续结论**。
> ⚠️ **只校验文件本身**，不校验消费方式（§3.4 效力边界第 1 条）—— 那部分由
> `PbCommentServiceAliasGateTest` 与 `docs/DRIFT/comment/proto.md` 的台账守。

## 隔离矩阵（隔离不对 ⇒ 差异全是串味）

| 要素 | Go 侧 | RAG 侧 |
|---|---|---|
| rpc / api 端口 | `19001` / `18888` | `59001` / `58888` |
| 数据库 | `go_comment` | `rag_comment`（同一 PG 容器 `127.0.0.1:55440`） |
| Kafka topic | `comment-audit-topic-go` | `comment-audit-topic-rag` |
| Kafka 消费组 | `comment-audit-group-go` | `comment-audit-group-rag` |
| **Redis 库** | **db0** | **db1**（`--spring.data.redis.database=1`） |
| 下游 `article-rpc` / `message-rpc` | `127.0.0.1:19999`（**死端点**） | `127.0.0.1:19999`（**同一个死端点**） |

- ⚠️ **Redis 必须分库**：两侧 key 格式**逐字一致**（`comment:subject:%s` 等）⇒ 不分库必然串味。
- ⚠️ **下游必须指向同一个死端点**：指向不同端点 ⇒ 超时路径不可比。
- Go 侧 yaml 的 `Etcd` 整块删除（`zrpc/config.go:91-95` 的 `BuildTarget` 对 `Endpoints` 短路）。
- RAG 侧关 Nacos 注册：`SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false`。

## ⚠️ 三个会把「时序」误读成「语义」的坑

| 坑 | 现象 | 本 harness 的处置 |
|---|---|---|
| **① Kafka topic 不预建** | 消费者订阅了**不存在的 topic** ⇒ Kafka 4.x 的 rebalance **指数退避** ⇒ 消费者组 **19 秒后**才拿到分区 ⇒ 落库迟到 ⇒ 后面的步骤全读到「还没落库」 | **先删后建**（`--create --partitions 1`）⇒ 订阅时 topic 已存在 ⇒ 立刻 join |
| **② 不等异步落库** | `CreateComment` **两侧都不写库**（只推 Kafka），落库在消费端 ⇒ 请求返回 ≠ 已落库 ⇒ 固定 `sleep(3)` 不够（实测迟 19s） | `diff_comment.py` 的 `settle()` **轮询**到「可见 / 已消失」为止（上限 40s / 15s） |
| **③ Go 侧 `Offset: last` 会跳过消息** | kafka-go 的 `LastOffset` 把起始位点定到**消息之后** ⇒ 消费组无成员时消息被跳过 | harness 侧改用 `Offset: first` + 每轮删 topic。⚠️ 这是 **harness 侧的规避**，不是产品缺陷 |

## 结果怎么读

- `步数 17 / 一致 N / 差异 M` —— **M 大不等于 RAG 有 M 个缺陷**。
- 先查「被读的那张表现在有没有这一行」：**有 ⇒ 时序问题；没有 ⇒ 才是语义问题**。
- 2026-10-24 那一轮：**15 一致 / 6 差异**，6 条差异**全在 2 个步骤**上，
  且**两侧根因都在 Go 侧**（Go 的 `attribute & ~?` 在 PG 上必报错 + Go 缺 `report_record` 表）
  ⇒ 转为**待签字偏离候选**，见 `docs/Intentional_Deviations/comment/rpc.md`。

## 已知未覆盖

矩阵只发**正常 JSON + 合法 HS256 JWT**，且下游是**死端点**（立即拒连）⇒
以下判定点**未被触发**，其状态码在 DRIFT 台账里**不变**：
`D1`（非 JSON Content-Type）、`D2`（Bearer 长度边界）、`MISMATCH-API-03`（2s deadline 窗口）、
`U7`（缺 `exp` / HS384 / HS512）、`U8`（未注册路径 404/405）。
