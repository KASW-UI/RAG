# gRPC 级可观测契约 · 双侧活体对拍探针 · favorite 域

> **用途**：把台账 [`docs/DRIFT/favorite/rpc.md`](../../../../docs/DRIFT/favorite/rpc.md) §20 里那条
> 「🔴 未验：帧层（HTTP2 / length-prefix / gRPC status 帧）」**拆成两半**分别给结论：
>
> | 层 | 内容 | 本探针的判定 |
> |---|---|---|
> | **L0 帧原始字节** | HPACK 编码 / 帧序 / WINDOW_UPDATE / PING | 🔴 **不做字节比对** —— 库级差异、客户端不可观测 |
> | **L1 帧层之上、消息层之下** | gRPC status code / description / 响应消息字节 / metadata key | ✅ **本探针做双侧活体对拍** |
> | **L2 消息层** | protobuf 消息体 wire 字节 | ✅ 已在第六轮闭（[`tools/proto-wire-probe/favorite/`](../proto-wire-probe/favorite/)） |
>
> **退出码**：`0` = 契约一致；`1` = 有差异；`2` = 环境问题 / 真源漂移。

---

## 〇、探针现在跑**两轮**（2026-10-25 第十六轮起）

| | **第一轮**（`expected.txt` · 36 case） | **第二轮**（`expected-success.txt` · 15 case） |
|---|---|---|
| **上游** | **故意不接** user-rpc / article-rpc（指 `127.0.0.1:1` 死地址） | **接上**探针自带的**最小桩**（user-rpc + article-rpc） |
| **测的是** | 「**下游不可用**」这条契约分支（`c04`/`c09` ⇒ `Internal/5001`） | 「**下游在场**」这条：create **成功路径** + `ensureUserExists` 的**其余四条分支** + **落库等价性** |
| **Go 真源 server** | 第 1 个（`ListenOn` = 端口 A） | 第 2 个（同一份二进制，`UserRpc/ArticleRpc` 改指桩） |
| **冻结结论** | SAME 33 / SAME_CODE 2 / OBS 1 | **SAME 15 / SAME_CODE 0 / OBS 0** |

⇒ **为什么要两个 server 而不是改第一个**：两条分支**都要有活体证据**
⇒ 改第一个会把「下游不可用」那条证据**弄丢** ⇒ 冻结值互不影响。

⚠️ **第二轮的桩有明确边界**（读结论前必看）：

> 桩 ≠ 真 user-rpc / 真 article-rpc ⇒ 第二轮证明的是
> 「**favorite 域自己在这条路径上的行为，两侧一致**」，
> **不**证明「favorite 与 user-rpc / article-rpc 之间的交互契约」。
> ⇒ 期望值**只能**由「Go 真源 favorite-rpc + 真 PG」产出。
> ⇒ 桩的实现见 [`go/service/favorite/rpc/cmd/upstreamstub/main.go`](go/service/favorite/rpc/cmd/upstreamstub/main.go)
> （**探针代码，不是真源复制件** ⇒ 不在 `go/filelist.txt` 的 sha256 清单里）。

---

## 一、为什么用「同一个客户端打两侧」

帧层的**原始字节**由 gRPC 库决定，grpc-go 与 grpc-java 必然不同，**而且客户端不可观测**
（客户端的 HTTP/2 实现会重新编码对端帧）⇒ 那部分不比。

但 L1 这一层是**客户端可观测**的，而且**由 favorite 域的代码决定**：

- Go：`service/favorite/common/errmsg.go:104-106` 的 `GRPCError(grpcCode, bizCode)` → `status.Error(code, GetErrMsg(bizCode))`
- RAG：`favorite-common/.../FavoriteGrpcError.java:94-98` 的 `grpcError(code, bizCode)` → `Status.fromCode(code).withDescription(getMsg(bizCode))`

⇒ 要测 L1，必须把「客户端自身差异」消掉 ⇒ **两侧用同一个 grpc-java 客户端、同一份 stub、同一套 metadata 处理**。
否则测到的是「两个 gRPC 实现之间的差异」，而不是「RAG 是否忠实复刻 Go」。

⚠️ 本域把**业务码藏在 description 文案里**（业务码本身不上线），客户端靠文案反查还原
⇒ **description 一旦变形，业务码直接丢** ⇒ L1 是本域责任最高的一层。

---

## 二、两侧怎么跑

| | Go 侧 | RAG 侧 |
|---|---|---|
| 代码 | 真源 `service/favorite/rpc/favorite.go` 等 **42 个逐字节复制件**（run.sh 逐个 sha256 校验） | favorite-rpc 的**真 Spring 上下文**（devh 真 netty gRPC server） |
| 接线 | 真源自己的 `zrpc.MustNewServer` + `RegisterFavoriteServiceServer` + `AddUnaryInterceptors` | 真 `@GrpcService` + 真 `GrpcServerConfigurer` 拦截器链 |
| 数据 | **真 PG**（sql-first 部署顺序） | **真 PG**（真 Flyway V1..V7）+ 真 MyBatis |
| 种子 | `seed.sql`（同一份） | `seed.sql`（同一份） |

### 为什么 Go 侧**不需要 etcd**（这是本轮推翻了旧判定的关键）

| 事实 | 出处 |
|---|---|
| `BuildTarget()` 优先级 = **Endpoints > Target > Etcd** | go-zero `zrpc/config.go:92-114` |
| `if c.HasEtcd()` 才注册 etcd ⇒ `Etcd.Key` 留空就不注册 | go-zero `zrpc/server.go:42-43` |
| `MustNewClient` 默认 `NonBlock=true` ⇒ **启动不需要对方在线** | go-zero `zrpc/config.go`（`RpcClientConf.NonBlock`） |
| 7 个 RPC 里**只有 `internal/logic/helper.go`** 引用 `UserRpc` / `ArticleRpc` | 真源 `service/favorite/rpc/internal/logic/helper.go` |

⇒ 探针 yaml 里 `UserRpc` / `ArticleRpc` 填 `Endpoints: ["127.0.0.1:1"]`（**故意是死地址**），
既绕开 etcd，又让「下游不可用」这条契约分支成为被测对象。

### Go 侧 schema 为什么要 **sql-first**

真源的 `model.InitDB`（`internal/model/db.go:20-41`）只做 AutoMigrate，**建不出**手写 SQL 里的
CHECK / 部分索引 / 两个不可变触发器 / `favorite_legacy_fact_marker` 整张表。
真源文档（`DELIVERY_ACCEPTANCE.md:7`）指定的是 **sql-first**（运维先跑 001..004，应用后起）
⇒ 探针用 `cmd/schemaprep`（探针代码）复刻该顺序，与
[`tools/ddl-parity-probe/favorite/`](../ddl-parity-probe/favorite/) 的 `SCENARIO=sql-first` 一致。

---

## 三、`expected.txt` 的四档

格式：`case_id \t kind \t value`，`value` 是 **Go 真源 server 被同一个客户端观测到的完整值**
（`code=…|desc=…|body=…|rhdr=…|rtrl=…`）⇒ 它同时是「Go 真源变了探针立刻响」的哨兵 + 两侧比对基准。

| kind | 含义 |
|---|---|
| `SAME` | 两侧**逐字节相同** |
| `SAME_CODE` | 两侧 **status code 相同**（语义等价），description 措辞允许不同 —— 只用于「措辞由 gRPC 库决定」的用例 |
| `OBS` | 两侧**必须不同**（已登记的框架/库层差异）⇒ 变成一样反而是回归 |
| `SKIP` | 显式跳过，理由必须写在文件里 |

### 响应头为什么要过滤 `grpc-*`

业务用例的 `rhdr` **只保留非 `grpc-*` 的 key**：

- `grpc-accept-encoding` / `grpc-encoding` / `grpc-status` / `grpc-message` 由 **gRPC 协议与传输库**产生，
  **不是 favorite 域的行为**（实测：grpc-java 的响应头多出 `grpc-accept-encoding` 与 `grpc-encoding`，
  grpc-go 不发 ⇒ 见 grpc-java `io/grpc/internal/GrpcUtil.java:111-112` 的 `MESSAGE_ACCEPT_ENCODING_KEY`）。
- 把这类噪声混进业务用例 ⇒ 8 个本来逐字节一致的用例退化成 OBS ⇒ **白白丢掉消息字节的对比能力**。
- ⇒ 折中：业务用例只比对**非 `grpc-*` 的头**（含 `content-type` 的值），
  同时用**一个专门的用例**（`c30_response_header_surface`）把完整头集合记下来、显式登记为 OBS。
  ⇒ **任何非 `grpc-*` 的头差异仍然会被抓出来**（没有把真差异藏进过滤器）。

---

## 四、探针自身的几个坑（都真实踩过）

1. **用例顺序 = 副作用顺序** ⇒ 「读同一行的用例」必须排在「写同一行的用例」之前。
   第一版把「响应头全集」用例放在最后 ⇒ 它读的收藏夹已被改名用例改过 ⇒ `updated_at` 变成
   「本次运行的时刻」⇒ 冻结值每次都不同 ⇒ **假红**。
2. **错误分支的 shape 渲染必须 null-safe** ⇒ 声明「预期成功」但服务端实际报错时，
   若不判空会抛 NPE，把「两侧行为不一致」这个**真结论**变成看不懂的测试崩溃。
3. 🔴 **端口排除列表必须包含「本轮已分配的全部端口」**（第十六轮）⇒ 第一版漏了第一轮的
   `RAG_PORT` ⇒ **桩抢到了 RAG server 的端口** ⇒ 客户端打到桩上 ⇒ 报
   「unknown service favorite.FavoriteService」（症状离根因很远）。
   ⇒ 现已加**互异性自检**（5 个端口去重后必须仍是 5 个）。
   ⇒ 详见 [`docs/Pitfalls/favorite/rpc.md`](../../../../docs/Pitfalls/favorite/rpc.md) **PIT-FAV-RPC-023**。
4. 🔴 **种子不能显式写自增主键**（第十六轮）⇒ 显式 `id` **不会**推进 PostgreSQL 的序列
   ⇒ 种子灌完后第一次 INSERT 撞 `favorite_folder_pkey` ⇒ 而业务码把 23505 翻译成
   「名称已存在 5203」⇒ **症状伪装成重名误报**。
   ⇒ 现已只写业务列，让序列自己走（空表上值不变）。
   ⇒ 详见 **PIT-FAV-RPC-024**。
5. 🐚 **bash 3.2 的 `${arr[@]+"${arr[@]}"}` 多一个 `"` ⇒ 整份脚本 `unexpected EOF`**
   （报错行号指向最后那个 `)`，离真凶很远）⇒ 新增端口一律**显式传参**。
   ⇒ 详见 **PIT-FAV-RPC-025**。

---

## 五、复现

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite
bash tools/grpc-contract-probe/favorite/run.sh
echo $?   # 0 = 一致；1 = 有差异；2 = 环境问题

# 可覆盖
GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/grpc-contract-probe/favorite/run.sh
# KEEP_TMP=1 ⇒ 保留临时目录（go-server.log / schemaprep.log / mvn.log / stub.log）
```

⚠️ **必须带 `-am`**（run.sh 已带）：`-pl` 不带 `-am` 会用过期上游产物 ⇒ 假红
（见 [`docs/Pitfalls/favorite/rpc.md`](../../../../docs/Pitfalls/favorite/rpc.md) PIT-FAV-RPC-013）。

## 六、单独跑 Java 侧（回归门禁）

两个测试类**自己**会读各自的冻结期望判定 ⇒ **直接 `mvn test` 也是一道回归门禁**：

```bash
mvn -o -pl service/favorite/favorite-rpc -am test \
  -Dtest=FavoriteGrpcContractParityTest -Dsurefire.failIfNoSpecifiedTests=false

mvn -o -pl service/favorite/favorite-rpc -am test \
  -Dtest=FavoriteCreateSuccessParityTest -Dsurefire.failIfNoSpecifiedTests=false
```

但它们**需要编排环境**（第一轮：`PROBE_GO_TARGET` / `PROBE_RAG_PORT` / `PROBE_JDBC_*` /
`PROBE_SEED` / `PROBE_OUT_GO` / `PROBE_OUT_RAG` / `PROBE_EXPECTED`；
第二轮再加 `PROBE_*_SUCCESS` 一组 + `PROBE_USER_RPC_ADDR` / `PROBE_ARTICLE_RPC_ADDR`）
⇒ 单跑会被 `@EnabledIfEnvironmentVariable` 跳过（**跳过 ≠ 通过**，别把 skip 当绿）。

⚠️ **两个测试类共用同一套观测代码**（`FavoriteGrpcContractParityTest` 的
`observe` / `Shape` / `Case` / `keys` / `hex` 已放宽成 package-private 后被第二个类**原样复用**）
⇒ 两个探针的「观测口径」**不可能漂移**。
（若另写一套渲染逻辑，两份冻结值的格式就会各走各的 ⇒ 失守。）
