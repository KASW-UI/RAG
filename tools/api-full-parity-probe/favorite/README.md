# api 整域 · 双侧活体对拍探针 · favorite 域

> **一句话**：把「Go 真源 favorite-api ↔ RAG favorite-api 从未活体对过」这条 🔴 未验，
> 变成「✅ 已验」—— **整个 HTTP 面**：路由 / method / JWT 中间件 / 黑名单中间件 /
> **gRPC 往返** / 状态码 / 响应头。

```bash
bash tools/api-full-parity-probe/favorite/run.sh
echo $?   # 0 = 两侧一致；1 = 有差异；2 = 环境问题 / 真源漂移
```

---

## 一、它补的是哪个缺口

[`docs/DRIFT/favorite/api.md`](../../../docs/DRIFT/favorite/api.md) §4 第一行写着：

> 🔴 **未做** —— 双侧活体对拍（真 Go favorite-api vs RAG）

已有的 [`tools/api-envelope-probe/favorite/`](../api-envelope-probe/favorite/) 只验**响应字节**，
而且 **RAG 侧是单侧断言**（没有 Go 侧参与）。本探针验的是**整个 HTTP 面**。

**为什么这条链值得单独活体对**（本探针的增量价值）：

> favorite-api 的**业务码不在 gRPC status 里，而在 description 文案里**
> （Go `favoritecommon.BizCodeFromError` 反查 ⇒ RAG `FavoriteGrpcError.bizCodeFromError`）。
> ⇒ 「api 的业务码对不对」这件事，**只有真的把请求从 HTTP 打通到 gRPC 再打回来**才能验。
> 在此之前，它只被 rpc 级对拍（[`tools/grpc-contract-probe/favorite/`](../grpc-contract-probe/favorite/)）
> **间接**覆盖 —— api 级从未活体对过。

---

## 二、两侧怎么跑（4 个应用进程 + 2 个中间件）

```
                    ┌──────────────────── 探针编排（run.sh）────────────────────┐
                    │                                                            │
  docker: postgres  │   Go favorite-rpc (真源副本) ──PG(godb)──┐                │
  docker: redis     │            ▲ Endpoints                 │                │
  （**两侧共用**）   │            │                             │                │
                    │   Go favorite-api (真源副本) ─────────────┘                │
                    │            │  HTTP :port                                 │
                    │            │                                             │
                    │            │  同一个 java.net.http.HttpClient              │
                    │            │  （同一套请求矩阵）                           │
                    │            │                                             │
                    │            │                          ┌──────────────────┴───┐
                    │            │                          │  RAG favorite-rpc      │
                    │            │                          │  (java -jar 真进程)     │
                    │            │                          │        │ static://      │
                    │            │                          │        ▼              │
                    │            │                          │  RAG favorite-api     │
                    │            │                          │  (java -jar 真进程)    │
                    └───────────────────────────────────────────────────────────────┘
```

| 侧 | 组成 | 证据强度 |
|---|---|---|
| **Go** | 真源 `service/favorite/api` + `service/favorite/rpc` 的**逐字节复制件**（run.sh 逐个 sha256 校验）⇒ 真 `rest.MustNewServer`（HTTP）+ 真 `zrpc.MustNewServer`（gRPC）+ 真 PG | ✅ 真源代码 |
| **RAG** | `java -jar favorite-api.jar` + `java -jar favorite-rpc.jar`（**可部署产物本身**）+ 真 Flyway + 真 MyBatis + 真 PG | ✅ 真部署形态 |

⚠️ **两侧共用同一个 Redis 容器** ⇒ 黑名单 key（`user:jwt_blacklist:<token>`）在**同一个命名空间**里
⇒ 「同一个被拉黑的 token」两侧必然查到同一条记录 ⇒ 这同时验了**黑名单 key 的拼法**
（前缀 + 大小写敏感的 `TrimPrefix`）。

⚠️ **两侧共用同一份 `seed.sql`** ⇒ 读到同一批行 ⇒ 响应字节可比。

⚠️ **同一个 `java.net.http.HttpClient` 打两侧** ⇒ 消掉客户端自身差异（与 rpc 级探针同款纪律）。

---

## 三、为什么**不需要** fake etcd / 内嵌 redis

| 依赖 | 处置 | 依据 |
|---|---|---|
| **etcd** | 探针 yaml 填 `FavoriteRpc.Endpoints` ⇒ **完全绕开** | go-zero `zrpc/config.go:92-114` 的 `BuildTarget()` 优先级 = **Endpoints > Target > Etcd** |
| **Redis** | 起一个**真容器**即可 | go-zero `redis.NewRedis` 只在 `!conf.NonBlock` 时探连接，而 `NonBlock` 默认 true ⇒ 启动不依赖 Redis 在线 |
| **user-rpc / article-rpc** | 指向**死地址** `127.0.0.1:1` | 故意走「下游不可用」这条契约分支（Go `helper.go:103-107` / RAG `FavoriteUserGuard`） |

---

## 四、矩阵（32 个 case · 8 组）

| 组 | 用例 | 验什么 |
|---|---|---|
| **鉴权** | a01–a06 | 无头 / 签名错 / 过期 / 只有前缀 / **小写 bearer**（Go 验签大小写不敏感）/ **被拉黑** |
| **路由** | r01–r03 | 未知路径 404 / 两个 405（method 不匹配）⇒ **404/405 不经鉴权** |
| **解析** | p01–p07 | 非法 JSON / 缺必填 / 类型不对 / 缺 form 参数 / 非数字 / **有 body 但没有 JSON Content-Type** / DELETE 缺 id |
| **业务** | b01–b07 | 穿过 **gRPC 往返**：下游缺席 5001 / 不存在 5201·5301 / 无权限 5005 |
| **只读成功** | s01–s04 | 收藏夹列表 / 条目列表 / 空集合 / 另一个用户的列表（**int64 以字符串出参**、时间戳逐字节） |
| **响应头面** | h01 | 完整响应头 key 集合（🔴 **2026-10-30 由 `OBS` 改档为 `SAME`** ⇒ 差异已闭合 ⇒ 变成**守住闭合态**的等值断言） |
| **写入** | w01–w03 | 改名 / 删条目（5304 收藏暂不可撤回）/ 删收藏夹 |
| **写入生效验证** | z01 | 写完之后再读一次 ⇒ 验证副作用真的一致 |

⚠️ **用例顺序 = 副作用顺序** —— 只读全在前、写入全在后、最后补一个只读验证写入生效。
（这是 PIT-FAV-RPC-015 的同款纪律。）

---

## 五、`expected.txt` 的五档

| 档 | 断言 | 用在哪 |
|---|---|---|
| **SAME** | status + Content-Type + body **全等** | 25 条（解析组 / 业务组 / 只读组 / 写入组 / 404 / 小写 bearer / **h01 响应头面**） |
| **SAME_CODE** | status 全等 **且** 业务码全等 | a06（黑名单命中）⇒ 两侧业务码都是 5004，body 形态不同（**已签字** DEV-FAV-API-001） |
| **SAME_BODY** | status 全等 **且** body 全等（**Content-Type 允许不同**） | r02 / r03（405）⇒ **新发现的跨域漂移**，见下 |
| **SAME_STATUS** | **只**要求 status 全等 | a01–a04（Go 的 401 是**空 body** ⇒ 取不到业务码；形态差异**已签字** DEV-FAV-API-001） |
| **OBS** | 两侧**必须不同** | ⚫ **当前 0 条** —— h01 原为「Go 多一个 W3C `traceparent`」⇒ 🔴 **2026-10-30 活体对拍实测两侧已相同**（共享层 T-8 已于 2026-10-25 落地）⇒ **改档 `SAME`** ⇒ ⚠️ 改档前这个用例会把「RAG 少发 `traceparent`」的回归**静默放过** |

冻结的列是 **Go 侧**的完整渲染值 ⇒ 真源行为一变，探针立刻 exit 1（是「Go 变了」，不是 RAG 回归）。

---

## 六、本轮查出来的三件事

### ① 两个**启动阻塞**（RAG 侧的真实缺陷，已修）

favorite-rpc / favorite-api **从未被验证能以真实进程启动** ⇒ 一 `java -jar` 就暴露两个硬失败：

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | `no ServletWebServerFactory bean defined` | pom 只有 `spring-boot-web-server` + `jakarta.servlet-api`，**没有** `spring-boot-starter-web` ⇒ Boot 默认按 SERVLET 起 | 启动加 `--spring.main.web-application-type=none`（对齐 Go 的「纯 gRPC、零 HTTP 端口」） |
| 2 | `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` | devh 的 channel factory 在**运行期**引用它，而 `grpc-inprocess` 只有 test scope（api 干脆没声明） | scope 改 `runtime` / 补声明 |

⇒ 两者都被 `@SpringBootTest` **完全掩盖**（webEnvironment=NONE + test classpath）
⇒ 详见 [`docs/Pitfalls/favorite/rpc.md`](../../../docs/Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-018**。

### ② 一条**跨域漂移**（405 多一个 Content-Type）

`GoStyleRouterErrorResponses.methodNotAllowed`（**service/common**）的 javadoc 明写
「容器不会补 Content-Type」，**实测被证伪**：Spring 仍会选出一个 MediaType
⇒ RAG 的 405 多一项 `Content-Type: application/json;charset=utf-8`（Go 是**裸状态码**）。

⇒ 落点在 **共享面** ⇒ 本域**只登记不修**，收口在 `global`。

### ③ 一个~~观测层差异~~（Go 响应多 `traceparent`）—— 🔴 **2026-10-30：该差异已闭合**

go-zero 的 otelhttp 会在**响应**里回显 W3C `traceparent`；**RAG 侧当时没有**
⇒ 框架/可观测层差异 ⇒ 单开一个 OBS 用例量化。

> 🔴 **2026-10-30 活体对拍轮订正**：**「RAG 侧没有」已经陈旧** ——
> 根因是**共享层 T-8 已于 2026-10-25 第十九轮落地**（`service/common` 新增
> `GoStyleTraceparentFilter` ⇒ 10 个 api 全生效）⇒ **两侧现在都有 `traceparent`**。
> ⇒ 活体实测（`bash tools/api-full-parity-probe/favorite/run.sh`）报
> 「**OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账**」⇒ **该差异已闭合**。
> ⇒ 本用例档位已由 `OBS` 改为 `SAME`（见 §五）⇒ 现在**守住闭合态**。
> ⇒ 台账侧同步订正：`docs/DRIFT/favorite/rpc.md` / `docs/DRIFT/favorite/api.md`。

---

## 七、证据边界（明确**未验**的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| **create 成功路径** | 🔴 **未验** | 本探针**故意不接** user-rpc ⇒ `ensureUserExists` 必失败 ⇒ create 落到「下游不可用」5001 ⇒ **create 成功**没被测到。<br>⇒ 但 create 成功路径的**出参字节契约**（`folderId` 以字符串出参）已由 `api-envelope-probe` 覆盖（真 go-zero 库实测） |
| **Redis 故障 / 变慢** | 🔴 未验 | 本探针的 Redis 是健康的 ⇒ 黑名单查询不会失败 ⇒ fail-open 那条分支未测（users 域的 `exp-difftest-redis-blacklist.sh` 覆盖过同款语义） |
| **真源 user-rpc / article-rpc 在场** | 🔴 未验 | 同上，两侧都故意缺席 ⇒ 只验「下游不可用」这条分支 |
| **Nacos 注册** | ⬜ 不适用 | 服务发现不在本域 scope（AGENTS.md §3.3 框架层） |
| **响应头** | ✅ 已量化 | 只比 `status + Content-Type + body`；完整头 key 集合另有 h01 用例（`Date` 每次都变 ⇒ 不比）<br>🔴 **2026-10-30 订正**：h01 原为 **OBS**（要求两侧不同）⇒ 实测两侧已相同 ⇒ **改档 `SAME`** |

---

## 八、复现

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite   # align/favorite 工作树
bash tools/api-full-parity-probe/favorite/run.sh
```

⚠️ 需要：go toolchain + mvn + docker（真 PG + 真 Redis）。
⚠️ `mvn` 命令**必须带 `-am`**（否则用过期上游产物 ⇒ 假红，见 PIT-FAV-RPC-013）。
⚠️ 端口全部**随机分配** ⇒ 不与别的会话撞。
⚠️ `KEEP_TMP=1` ⇒ 保留临时目录（`go-api.log` / `rag-api.log` / `go.txt` / `rag.txt`）。
