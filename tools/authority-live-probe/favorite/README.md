# 权威读 HTTP 面 · 双侧活体对拍探针（favorite 域）

把 `favorite.fact.authority` 权威读端点的 **HTTP 行为**从「源码级推理 + RAG 侧单侧断言」
升级为**两侧真代码 + 真 PG 的活体对拍**。

```bash
bash tools/authority-live-probe/favorite/run.sh
echo $?   # 0 = 两侧逐字节一致；1 = 有差异；2 = 环境问题 / 真源漂移
```

## 为什么以前做不了（2026-10-25 第十二轮推翻了这个判定）

favorite 域此前判「环境受限 ⇒ 拿不到双侧活体对拍」，理由是
「Go 侧 favorite-rpc 依赖 `etcd` + Kafka + Redis + `article-rpc` / `user-rpc`」。

⚠️ **那个理由只对 gRPC 整域成立**，对**权威读 HTTP 面不成立**：

| 依赖 | Go `cmd/fact-authority/main.go` | RAG `FavoriteAuthorityEndpointConfig` |
|---|---|---|
| etcd / Kafka / Redis | ❌ 都不需要（`:32-37` 只读 3 个环境变量） | ❌ 都不需要（favorite 域零 Redis 零 Kafka） |
| `user-rpc` / `article-rpc` | ❌ 不需要 —— `AuthoritativeFavoriteFact` 只查 `favorite_fact_outbox` + `favorite_item` | ❌ 不需要 —— `FavoriteAuthorityLookupService` 的依赖只有 3 个 mapper/validator |
| **PG + HTTP** | ✅ | ✅ |

⇒ **两侧都是 HTTP + PG ⇒ 真双侧活体对拍可行**，且不依赖任何外部服务。

## 两侧怎么跑

| | Go 侧 | Java 侧 |
|---|---|---|
| handler | **真源** `internal/authority/http.go`（逐字节复制件 + sha256 校验） | **真** `FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet` |
| 数据 | **真源** `internal/model/*` + **真 PG** | **真** `FavoriteAuthorityLookupService`（真 MyBatis）+ **真 PG**（Testcontainers） |
| schema | 真源的 AutoMigrate + 001..004（sql-first 顺序） | 真 Flyway V1..V7 |
| 数据来源 | **同一份** `seed.sql` | **同一份** `seed.sql` |
| transport | `httptest.NewRequest` + `ResponseRecorder` | `MockHttpServletRequest` + `MockHttpServletResponse` |

### 种子数据为什么是「真源生成的」

`seed.sql` 由 Go 侧 `--emit-seed` 模式产出，走的是**真源自己的写入路径**：

- `model.InsertFolder` / `model.InsertFavorite` ⇒ payload 的信封形状由真源决定；
- `model.DispatchFavoriteFactOnce` + 一个 `FavoriteFactSender` stub
  ⇒ `technical_input_hash` 由 **DC 端算法**（JCS(payload) 的 sha256）给出；
  ⇒ **真源随后自己用 `favoriteJCSHash(row.Payload)` 复核**
  （`favorite_authority.go:160-165`）⇒ **hash 算错的话真源自己就会拒绝这行**
  ⇒ 探针**自证**，不靠探针自己断言「hash 对不对」。

⇒ 种子**冻结**在 `seed.sql`（不是每次重新生成）⇒ 重新生成会带新的时间戳 ⇒ 两侧数据就不同了。

## 判定口径

`expected.txt` 是**冻结的 Go 侧实测值**（三列：`case_id \t kind \t value`）：

| kind | 含义 |
|---|---|
| `SAME` | 两侧必须**逐字节相同** |
| `OBS` | 两侧**必须不同**（已登记的有意偏离；哪天变成一样 ⇒ 测试失败 ⇒ 逼人重评） |
| `SKIP` | **容器层**行为，探针的 transport 覆盖不到 ⇒ 只要求「两侧都跑过」，不要求字节相同 |

`expected.txt` 同时是三件事的真源：

1. **Go 真源变了探针立刻响** —— `run.sh` 把 Go 侧输出与它逐行比对；
2. **Java 侧断言的唯一真源** —— `FavoriteAuthorityLiveParityTest` 读它；
3. **两侧比对的口径** —— `SAME` / `OBS` / `SKIP` 的判定规则。

## 当前结果（2026-10-25）

**21 个 case：20 个逐字节完全一致 + 1 个 SKIP。**

| case | 覆盖 |
|---|---|
| `v1_ok` / `v2_ok` | 200 + JSON（v1 三字段 `subject_ref` / v2 两字段 `issuer`+`subject_id`） |
| `auth_*`（7 条） | 缺失 / 无 `Bearer` 前缀 / 错 token / 空 token / **重复 Authorization 头** / 大小写错前缀 |
| `mux_*`（5 条） | path 清理 301 / 少一段 / 多一段 / 前缀错 / **方法不匹配 405** |
| `query_*`（2 条） | 带 query ⇒ 404（Go `http.go:40-43`，**不是** 400） |
| 数据面（6 条） | 事件不存在 / producer 不匹配 / **v1 端点读 v2 schema** / v2 端点读 v1 schema / event_id 超长 / 含 `%2F` |

### 🔴 本探针抓出并已修的 1 条真缺陷

`v1_ok` / `v2_ok`：Go 的 `json.NewEncoder(w).Encode(fact)` **必定追加一个 `"\n"`**
（encoding/json 的 `Encoder.Encode` 契约），RAG 侧原实现用
`STRICT_JSON.writeValueAsBytes(fact)` ⇒ **少了那一个字节** ⇒ 响应体逐字节不一致
（`Content-Length` 也差 1）。

⇒ 已修（`FavoriteAuthorityEndpointConfig` 补写 `'\n'`）。
⚠️ 判它是**缺口**而不是有意偏离的依据：同一类的错误路径
（`writeGoError` / `writeMovedPermanently`）**刻意**补了尾部 `"\n"` 以对齐 Go
⇒ **同一件事上的不一致** ⇒ 不是设计选择。

### 🔴 唯一的 SKIP：`mux_method_not_allowed`

Go 的 405 来自 **`http.ServeMux` 层**（带 `Allow: GET, HEAD` + `text/plain` + `nosniff`
+ body `"Method Not Allowed\n"`）；RAG 侧是 **Servlet 层**（只有 `doGet`）：

- 在 **Mock** 下 ⇒ 空 body、无 header；
- 在**真 Tomcat** 下 ⇒ `sendError(405, msg)` 会渲染**容器的 HTML 错误页**
  ⇒ 形态取决于**部署形态**（favorite-rpc **默认无 web 容器** ⇒ 该端点默认关闭）。

⇒ 属 `DEV-FAV-RPC-004`「权威端点的部署形态」的下游后果 ⇒ 不在本探针覆盖面
⇒ **SKIP 的理由已写进 `expected.txt` 的注释**，不许「默默忽略」。

## 证据边界（本探针**不**覆盖的）

| 项 | 为什么覆盖不到 | 归属 |
|---|---|---|
| **容器层**（Tomcat 的重复头合并、405 错误页渲染、chunked 传输） | favorite-rpc 是**纯 gRPC 进程，不带 web starter** ⇒ 本模块**没有**可起的嵌入式容器 | `DEV-FAV-RPC-004`（部署形态） |
| **gRPC 帧层**（HTTP2 / length-prefix / status 帧） | 库级差异 | 不属本域 |
| **Go 的 HTML 转义**（`<` `>` `&` → `\u003c` …） | 权威读响应里**实际不可触发**：payload 是 `json.RawMessage` ⇒ 原样写出；顶层字段只有 id / hex / 固定串 | 已在 `FavoriteRpcSupport.STRICT_JSON` 的 javadoc 说明（且被 JCS 归一化掉） |
| **gRPC 整域**（7 个 RPC 的业务编排） | Go gRPC server 注册走 etcd ⇒ 真受阻 | 已拆成 6 项局部对拍 |

⚠️ `MockHttpServletRequest` **不是** Mockito mock —— 它是 Spring 的**真实实现类**，
servlet 的路径解析 / 鉴权 / JSON 序列化**全是真代码在跑**；
它替代的只是**容器**。

## 变异验证

本探针断言的是「**逐条等值**」，不是「某个字段存在」⇒ 把 RAG 的
`writeGoError` 去掉尾随 `"\n"`、或把 `subject_ref` 改回三字段、或改 token 判定
⇒ 必红 ⇒ **不是假绿**。

## 相关

- `tools/authority-path-probe/favorite/` —— 权威读的 **path 形态 + 错误响应**（静态）
- `tools/api-envelope-probe/favorite/` —— api 侧的**响应字节**（真 HTTP）
- `tools/ddl-parity-probe/favorite/` —— **DDL 逐列**（真 PG 双侧）
- `docs/DRIFT/favorite/rpc.md` §19 —— 本探针的台账记录
