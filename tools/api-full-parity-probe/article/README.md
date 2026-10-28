# article 域 **HTTP 层**双侧活体对拍探针

> **落点理由**：`tools/api-full-parity-probe/article/` 是 **G3 真私有**目录（只被 article 域引用 ⇒ 按
> AGENTS.md §5.3f「共享判据 = 依赖度数，不是目录位置」⇒ **不属共享层** ⇒ G3 可写）。

## 目的

补 `docs/PhaseGoal-judge.md` §九 **G-3**（「article 域双侧活体 **0 条**」）。

在此之前，本域**从来没有过「两侧同时起服务」的证据** ⇒ ⇒ **字段序 / 缺失字段 / 错误码**这类
**只有真跑才看得见**的差异，一条都没被发现。本探针让**两侧同时起服务**（Go 真源栈 ＋ RAG 栈）
⇒ 同一请求 ⇒ 比对**字节**。

> 实跑的产物：**3 条 L4 双侧活体证据**（见下「实测结果」），
> 并**顺带暴露 2 个新缺陷**（`DRIFT-ARTICLE-API-011` / `-012`）—— 这两个缺陷在
> 「只跑单测」的世界里**根本不可能被看见**（一个让组件起不来，一个是字节序）。

## 用法

```bash
bash tools/api-full-parity-probe/article/run.sh          # EXIT=0 = 两侧逐字节一致
bash tools/api-full-parity-probe/article/mutate-parity.sh # 变异验证（M1~M3 ⇒ 必须精确转红）
```

退出码：`0` = 逐字节一致 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失。

可用 env 覆盖：`GO_REPO`（真源路径，默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`）·
`ETCD_PORT` / `KAFKA_PORT` / `MINIO_PORT` / `JAEGER_PORT` / `NACOS_PORT` ·
`GO_API_PORT` / `RAG_API_PORT` / `GO_RPC_PORT` / `GO_PROM_PORT` · `JWT_SECRET`。

## 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的生成码/二进制**；但真源 yaml 把主机名**写死**了
（`etcd:2379` / `postgres` / `kafka:9092` / `minio:9000` / `jaeger:4317`）⇒ 脚本生成**配置副本**，
把那些主机名改成宿主可达地址。

> 🔴 **脚本第 3 步会断言真源仓仍然干净**：
> `git status --porcelain -- service/article` 必须为空 ⇒ 否则 `die`（exit 2）。
> ⇒ **冻结的真源一个字都没被改。**

## 两侧各自的 Postgres（不互相污染）

| 侧 | 库 | 表从哪来 |
|---|---|---|
| Go | `admin/first_db` | **真源 DDL**：`article.sql` + `002_community_revision.sql` |
| RAG | `admin/first_db` | **它自己的 Flyway**（`article-rpc` 启动时建表） |

> ⚠️ `002_community_revision.sql` **必须先跑**，否则真源 `article-rpc` 启动即 fatal
> （真源 `service/article/rpc/internal/model/db.go:61-71`）。

## 实测结果（2026-10-29 · G3）

| CASE | 端点 | 两侧结果 | 逐字节 |
|---|---|---|---|
| `list_articles` | `GET /v1/articles` | `{"code":200,...}`（含 `author_name` 降级路径） | ✅ |
| `get_article_exists` | `GET /v1/article/9007199254740993` | `{"code":5001,"msg":"系统内部错误"}` | ✅ |
| `get_article_missing` | `GET /v1/article/does-not-exist` | `{"code":1002,"msg":"文章不存在"}` | ✅ |

冻结的期望值在 `expected.txt`（`CASE|<name>|<hex>`）⇒ **每次跑都要同时满足**：
`Go == RAG == expected.txt`。

### ⚠️ 这 3 条**不等于**「article 域双侧活体全闭合」

1. **成功路径只覆盖公开列表端点**。宿主上 `user.rpc` **两侧都不可达**
   ⇒ 单条详情端点两侧**同样降级成 5001** ⇒ `get_article_exists` 这条用例钉的是
   「**降级路径两侧一致**」，**不是**「取到了文章」。
   ⇒ **成功路径的完整字段语义仍未双侧验证** ⇒ 要补需让两侧的 `user.rpc` 都可用。
2. `judge` §九 G-3 原文要求「article 域双侧活体 **0 条**」⇒ 现 **3 条** ⇒ **部分闭合**，不得写成全闭合。
3. `list_articles` 的 `author_name` = `"用户 1001"` ⇒ **也是降级路径**（user 侧不可达时的兜底名）。

## 探针本身踩过的坑（写下来免得下一个人再踩一遍）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | Go rpc `panic: listen tcp 0.0.0.0:9002: bind: address already in use` | 真源 yaml 的 `Prometheus:` 段被 `core/service/serviceconf.go:62` `prometheus.StartAgent(sc.Prometheus)` 消费 ⇒ `core/prometheus/agent.go:39` 绑 `Host:Port` ⇒ **它和 `ListenOn` 是两个监听者**；脚本把 Prometheus 端口也改成 9002 ⇒ 它先绑住 ⇒ gRPC 撞车 | Prometheus 用**独立**端口（`GO_PROM_PORT`）＋ 脚本**断言**两者不相等 |
| 2 | 「40s 内没注册进 etcd」——**假阴性** | go-zero 的 etcd 键是**前缀族**：未显式配 `etcd.ID` 时键 = `<key>/<leaseID>`（`core/discov/publisher.go:191-196` + `core/discov/clients.go:39-41` `makeEtcdKey`）⇒ 实测 `article.rpc/7587898349098767861` ⇒ **精确键查永远查不到** | 按**前缀**查（`etcdctl get article.rpc --prefix`） |
| 3 | `docker exec psql` 成功，但宿主端口拒连 | `docker exec` 走**容器内 socket**，与 `-p` 发布**无关** ⇒ 容器健康 ≠ 宿主端口通 | 就绪判据改成**宿主端口 `nc -z`**；端口用**阶梯**逐个试 |
| 4 | etcdctl 在容器里连 `49319` 超时 | `docker exec` 里的 etcdctl 用的是**容器内端口** `2379`，不是宿主映射口 | `ETCD_CTL_ENDPOINT` 默认 `http://127.0.0.1:2379`，并在 step 0 **探一次**，探不过就 exit 2 |
| 5 | 上一轮的探针容器把本轮的库删了 | 容器名固定 ⇒ 别的轮/别的 lane 的 cleanup 可能晚到 ⇒ 删掉本轮的容器 | 容器名带 `$$`（每轮唯一） |
| 6 | `RAG_PIDS[@]: unbound variable` | macOS 自带 **bash 3.2**：`set -u` 下展开**空数组**报错 ⇒ cleanup 提前死 ⇒ **`docker rm -f` 没执行** ⇒ 容器残留 | 用 `${A[@]+"${A[@]}"}` 形式 |
| 7 | 后台任务里 `bind(("0.0.0.0", 0))` 报 `EADDRNOTAVAIL` | 后台任务沙箱里绑通配地址会被拒 ⇒ 「让 OS 挑空闲端口」不可用 | 改用**固定端口阶梯**（不依赖 python 挑端口） |
| 8 | `sed -e "s\|^  Port: 8889$|..."` 静默不命中 | 真源 yaml 里是 `Port: 8889`（**顶层、零缩进**）⇒ 写 `^  Port:` 永远匹配不上 ⇒ 副本端口没改 | 改对缩进 ＋ **逐条 grep 断言**（改了哪项就断言哪项） |
| 9 | 「注册进 etcd 了」≠「服务可用」 | go-zero **先注册 etcd 再 bind 端口**（`zrpc/internal/rpcpubserver.go:32-33`）⇒ 等到 etcd 有键时进程可能正要 panic | 脚本**先等 etcd 键、再等端口监听**，两步都要 |

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 |
|---|---|---|
| **M1** | 去掉 `Article` 的 `@JsonPropertyOrder` | `ArticleApiJsonFieldOrderTest` 的 `Article` 那条**转红** |
| **M2** | 去掉 `ListArticlesResp` 的 `@JsonPropertyOrder` | `ArticleApiJsonFieldOrderTest` 的 `ListArticlesResp` 那条**转红** |
| **M3** | 改 `expected.txt` 里一枚 hex | 探针 `run.sh` **EXIT=1** |

⇒ **M1/M2 证明「字段序断言」真有牙齿**；**M3 证明「对拍不是自己跟自己」**
（期望值被改 ⇒ 立刻报差异 ⇒ 期望值确实在参与判定）。
