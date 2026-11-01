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
bash tools/api-full-parity-probe/article/mutate-parity.sh # 变异验证（M1~M4 ⇒ 必须精确转红）
bash tools/api-full-parity-probe/article/run-success.sh   # 成功路径（2026-11-01 新增）
```

退出码：`0` = 逐字节一致 · `1` = 有差异 · `2` = 环境问题 / 真源漂移 / 依赖缺失。

可用 env 覆盖：`GO_REPO`（真源路径，默认 `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`）·
`ETCD_PORT` / `KAFKA_PORT` / `MINIO_PORT` / `JAEGER_PORT` / `NACOS_PORT` ·
`GO_API_PORT` / `RAG_API_PORT` / `GO_RPC_PORT` / `GO_PROM_PORT` · `JWT_SECRET`。

> `run-success.sh` 另可用 `FREEZE=1`（重建 `expected-success.txt`）与 `KEEP_WORK=1`（保留临时目录）。

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

1. 🔴 **2026-11-01 归因订正（读 Go 真源 + RAG 源码双侧逐行证实）**：原文写「宿主上 `user.rpc`
   **两侧都不可达** ⇒ 单条详情端点两侧**同样降级成 5001**」⇒ **该归因不成立**。
   **真实根因 = legacy 分支读 MinIO 失败**：
   - Go `service/article/rpc/internal/logic/get_article_logic.go:139-153`
     —— `published.Revision == nil` ⇒ `MinioClient.GetObject(bucket, article.Content)` 失败
     ⇒ `:146` `status.Error(codes.Internal, "read legacy article failed")`
     ⇒ api 层 `service/article/api/internal/logic/article/get_article_logic.go:47-48` ⇒ **5001**；
   - RAG `service/article/article-rpc/.../service/ArticlePublicDomainService.java:135`
     ⇒ `:311` `readLegacyMarkdown` ⇒ `:315` `ArticleExceptions.readLegacyArticleFailed()`
     （`ArticleExceptions.java:125`）⇒ INTERNAL ⇒ **5001**；
   - **触发条件** = 种子行 `content='正文'` **不是合法 MinIO object key**。
   - ⇒ **`user.rpc` 不在成败链路上**：Go `get_article_logic.go:78` `enrichArticleAuthor`
     在 rpc **成功返回之后**才填 `AuthorName`，失败走 `helper.go:98-103` `fallbackAuthorName`
     ⇒ **永不抛异常**；RAG `ArticleAuthorResolver.resolveAuthorName` javadoc 自述「**永不抛异常**」。
   - ⇒ **成功路径的开关 = 有没有 revision 指针**（**不需要** `user.rpc` 可用）。
2. `judge` §九 G-3 原文要求「article 域双侧活体 **0 条**」⇒ 现 **3 条** ⇒ **部分闭合**，不得写成全闭合。
3. `list_articles` 的 `author_name` = `"用户 1001"` ⇒ 是**回落名**（user 侧不可达时的兜底名）。

## ✅ 成功路径探针（`run-success.sh` · 2026-11-01 · G3）

> **为什么单独一个脚本 · 不动 `run.sh`**：`run.sh` 的 `expected.txt` 冻结了 3 条 L4 证据
> ⇒ 改它会让那 3 条的期望值失效 ⇒ 本脚本**独立**起栈 + 独立种子 + 独立期望值文件
> （`expected-success.txt`）⇒ **老证据不受影响**。

### 补的缺口

`run.sh` 的 `get_article_exists` 钉的是「**降级路径**两侧一致」（两侧都 5001），**不是**「取到了文章」。
⇒ **成功路径（返回完整字段）的完整字段语义**此前从未双侧活体验证过。

### 做法

种子多插两行 —— `article_revision` + `article_publication`（`state=published` + `current_revision`）
⇒ 命中 Go `service/article/rpc/internal/model/article_public.go:20-26` `publicArticlePredicate`
的**第一支** ⇒ 走 revision 分支（Go `get_article_logic.go:137-139` /
RAG `ArticlePublicDomainService:130-132`）⇒ **正文不读 MinIO** ⇒ 详情端点返回**完整字段**。

### 「确实走了 revision 分支」的 4 个独立信号

> 种子里 `article` 行与 `article_revision` 行的这些字段**故意填成不同值**
> ⇒ 信号互相独立 ⇒ **不靠「两侧一致」自证**。

| # | 信号 | legacy 路径 | revision 路径 |
|---|---|---|---|
| S1 | `ext_info` | `{"publication_gap":"legacy_revision_missing"}` | `{"published_revision_id":"rev-succ-0001"}` |
| S2 | `update_time` | `article.updated_at` | **`revision.published_at`**（Go `get_article_logic.go:185`） |
| S3 | `content` | MinIO 对象正文 | **`revision.markdown`** |
| S4 | `title`/`brief`/`cover_image_url`/`manual_type_tag` | `article` 行 | **`revision` 行** |

### 实测结果（2026-11-01 · G3）

| CASE | 端点 | 两侧结果 | 逐字节 |
|---|---|---|---|
| `get_article_success` | `GET /v1/article/9007199254740994` | `{"code":200,...}`（**成功路径 · 完整字段**） | ✅ |
| `list_articles_success` | `GET /v1/articles` | `{"code":200,...}`（含 revision 派生字段） | ✅ |

⇒ **8 个信号（4 信号 × 2 侧）全部通过** ⇒ 两侧确实都没走 legacy 分支。
冻结的期望值在 `expected-success.txt`（`CASE|<name>|<hex>`）⇒ 每次跑都要同时满足
`Go == RAG == expected-success.txt`。

> ⚠️ **剩余的唯一一格**：本轮 `author_name` 两侧同为**回落值** `"用户 1001"`
> ⇒ **「`user.rpc` 可用时取真实昵称」那一支仍未双侧验证** ⇒ 需 users 域等价（Go 侧 `user-rpc` 可达）
> ⇒ 属**取证缺口**（judge §1.1 ⑥ 第 3 类 ⇒ 不降 R）⇒ **不阻断**。

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
| 10 | 🔴 **`RAG article-rpc 没监听`（假失败）—— 判据自匹配** | 就绪判据写成「`service/list` 里**有没有这个服务名**」⇒ **上一轮的残留实例**也会命中（实例在心跳超时前仍算「健康」）⇒ 脚本在 java **还在启动**（`rag-rpc.log` **0 字节**）时就去查端口 ⇒ 误判「没监听」 | ① 判据改成**数「注册端口 == 本轮 gRPC 端口」的实例个数**（RAG 侧注册的是 gRPC 端口：`bootstrap/NacosRegistration.java` 用 `GrpcServerProperties.getPort()`）② 起进程**前**确认同端口残留实例已清干净（清不掉 ⇒ 直接 `die`，不假跑）③ 报错时**带上日志行数**（0 字节 ⇒ 进程根本没起）④ Go 侧同理：etcd 注册值里必须带**本轮端口**（双保险） |
| 11 | `FREEZE=1` 跑完，期望值文件里**只剩最后一条** | 冻结时用 `>` **覆盖写** ⇒ 每个用例都把上一个冲掉 ⇒ 文件「看起来有」但**内容不完整** ⇒ 后续校验会拿不全的期望值去比 | 冻结前**先截断**（`: > file`），再逐条**追加**（`>>`） |
| 12 | 两个探针脚本**端口互撞** | `run.sh` 用 8889 / 18889 / 9002 / 9001 / 19092，`run-success.sh` 若沿用 ⇒ 同时跑必撞（Go rpc `panic: address already in use`） | `run-success.sh` 用 **article 域预分配的 22400 段**（22411~22417）⇒ 两个脚本可先后/并行 |
| 13 | Go 真源连不上 PG（口令是字面 `${POSTGRES_PASSWORD}`） | Go 的 `conf.MustLoad` **不**做 `os.ExpandEnv` ⇒ `${POSTGRES_PASSWORD}` 被当**变量名**查 ⇒ 空串 ⇒ 连库失败（且**不报错**，只是连不上） | 配置副本里把它**替换成字面值**（`sea`）＋ 脚本**逐条 grep 断言**（改了哪项就断言哪项） |

## 变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 |
|---|---|---|
| **M1** | 去掉 `Article` 的 `@JsonPropertyOrder` | `ArticleApiJsonFieldOrderTest` 的 `Article` 那条**转红** |
| **M2** | 去掉 `ListArticlesResp` 的 `@JsonPropertyOrder` | `ArticleApiJsonFieldOrderTest` 的 `ListArticlesResp` 那条**转红** |
| **M3** | 改 `expected.txt` 里一枚 hex | 探针 `run.sh` **EXIT=1** |
| **M4** 🆕 | 改 **rpc 层**的公开视角映射（`ArticlePublicDomainService.toProtoArticle` 的 `title` → `brief`）⇒ RAG 侧 HTTP 输出漂移 | 探针 `run.sh` **EXIT=1** ⇒ **证明比较面真的穿过 rpc 层**（M1/M2 打的是 api 层 DTO ⇒ **不构成 rpc 层的变异证据**） |

⇒ **M1/M2 证明「字段序断言」真有牙齿**；**M3 证明「对拍不是自己跟自己」**
（期望值被改 ⇒ 立刻报差异 ⇒ 期望值确实在参与判定）；**M4 证明「比较面真的穿过 rpc 层」**
（打的是 **rpc 层**的映射 ⇒ rpc 层漂移 ⇒ HTTP 输出漂移 ⇒ 探针立刻报差异 ⇒
⇒ 对 **`article-rpc` 组件**而言，这一条才是 judge §1.1 ⑧「变异验证」的**组件内**证据）。
⚠️ **M4 会临时改生产码**（`ArticlePublicDomainService.java`）⇒ 脚本自带 **EXIT 兜底还原** ＋ **sha256 还原核验**（4 个文件全查）⇒ 还原没生效即 `EXIT=1`。

### 成功路径探针的变异（`mutate-success.sh` · 2026-11-01 · G3）

> **为什么不并进 `mutate-parity.sh`**：`mutate-parity.sh` 的 **M1~M4** 已被
> `docs/PhaseGoal-judge.md` §十二 引用为 article 域的**既有变异证据** ⇒ 改它会动到已被引用的证据
> ⇒ **独立成文件**，一个字节都不动它。

| 变异 | 手法 | 期望 | 证明了什么 |
|---|---|---|---|
| **M5** | 改 `expected-success.txt` 里一枚 hex | 探针 **EXIT=1** | 「**不是自己跟自己**」⇒ 冻结期望值确实在参与判定 |
| **M6** | 改 rpc 层 revision 分支写进 `ext_info` 的键名（`published_revision_id` → `published_revision_id_MUT`） | 探针 **EXIT=1**（Go == RAG 破裂 ＋ **S1 信号转红**） | 「**比较面真的穿过 revision 分支**」—— 成功路径的判据不是空的 |
| **M7** | 把正文从 `revision.getMarkdown()` 换成 `article.getContent()` | 探针 **EXIT=1**（Go == RAG 破裂 ＋ **S3 信号转红**） | 「**4 个 revision 分支信号有牙齿**」 |

⇒ **实测（2026-11-01）**：基线绿 → M5/M6/M7 **3/3 精确转红** → 还原后绿 → **sha256 2/2 还原核验通过** ⇒ `EXIT=0`。
⚠️ **M6/M7 会临时改生产码**（`ArticlePublicDomainService.java`）⇒ 同样自带 **EXIT 兜底还原** ＋ **sha256 还原核验**。
