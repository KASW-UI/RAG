# favorite 域 **gRPC 层（rpc 层）**双侧活体对拍探针

> **落点**：`tools/api-full-parity-probe/favorite-rpc/`
> ⚠️ **与 `tools/api-full-parity-probe/favorite/`（HTTP 层）完全分开** —— 那条链验 **api 面**
> （路由 / JWT / 响应头 / JSON 字节）；本链验 **rpc 面**
> （gRPC status code ＋ description ＋ **响应消息在 wire 上的原始字节**）。
> **两条链的证据不混记。**

## 〇、一句话

把 RoundPlan `docs/RoundPlan/Round-2.md` **T-2.3 · F-1**「`favorite-rpc` 整域双侧活体对拍」
从「🔴 环境受限（Go 侧 etcd / Kafka 拉不起来）」变成
「✅ **两侧同时起真服务 · 同一请求 · 逐字节一致 · 有冻结期望值 · 有变异验证**」。

> ⚠️ **旧判定「环境受限」已作废**：2026-10-29 实测，宿主上
> `Sea-etcd`（宿主映射口 **49319**）· `Sea-nacos`（**49848**）· `Sea-postgres`（**49432**）·
> `Sea-kafka`（**49992**）· `Sea-jaeger`（**49317**）**全部在跑** ⇒
> Go 真源栈**能起**、RAG 栈**能起**、两侧**能同时在跑**。
> ⇒ **不再继承旧判定**；本探针就是它的替代品。

---

## 一、它比的是什么（**字节，不是「字段看起来一样」**）

| 层 | 比什么 | 怎么取到 |
|---|---|---|
| **gRPC status** | 码（`INVALID_ARGUMENT` / `NOT_FOUND` / `PERMISSION_DENIED` / `ALREADY_EXISTS` / `FAILED_PRECONDITION` / `INTERNAL` / `OK`） | 客户端 `StatusRuntimeException.getStatus().getCode()` |
| **gRPC description** | **逐字节**（本域把**业务码藏在 description 文案里** ⇒ 文案变形 = 业务码丢失） | 同上，`getDescription()` 的 UTF-8 字节 |
| **响应消息 wire 字节** | **服务端真正发在 wire 上的那串字节** | 🔴 **截获原始字节**，不是「解析后重新序列化」（见下） |

### 🔴 为什么必须是「wire 上的原始字节」

`grpc-java` 的 `ClientCallImpl` 在把消息交给上层**之前**，会把**未经解析的消息体字节**
交给 `MethodDescriptor.parseResponse(stream)` ⇒ 只要把 `responseMarshaller` 换成
「先把这串字节读出来、再委托解析」的实现，就能拿到**服务端真正发的那串字节**。

```java
// tools/api-full-parity-probe/favorite-rpc/java/FavoriteRpcParityClient.java · RawCapture
public Object parse(InputStream stream) {
    byte[] raw = stream.readAllBytes();   // ← wire 上的原始字节
    last = raw;
    return delegate.parse(new ByteArrayInputStream(raw));
}
```

⇒ **若改成「解析后重新序列化」**：两侧的**字段顺序**会被客户端库抹平成同一份
⇒ **字段序漂移就看不见了** ⇒ 探针变成假绿（AGENTS.md §3.3.3 的同款形态）。

### 为什么「同一个客户端打两侧」

帧层的原始字节由 gRPC 库决定（grpc-go ≠ grpc-java），**客户端不可观测**；
要测的是「favorite 域自己的行为」⇒ 必须消掉客户端自身差异
⇒ **两侧共用本客户端、同一份 stub**（与 `tools/grpc-contract-probe/favorite/` 同款纪律）。

---

## 二、两侧怎么跑（4 个进程 + 2 个中间件 + 2 个 PG 容器）

```
   docker: postgres(Go)          Go favorite-rpc (真源副本) ──PG(godb)──┐
   docker: postgres(RAG)         真源 gRPC :GoRpcPort                 │
                                                        同一个 grpc-java 客户端
   Sea-etcd 49319 ← Go 侧注册    ┌─────────────────────────┐          │  （同一套用例矩阵）
   Sea-nacos 49848 ← RAG 侧注册  │ RAG favorite-rpc (jar)  │          │
                                └─────────────────────────┘          ▼
                                                  逐字节比 Go vs RAG vs expected.txt
```

| 侧 | 组成 | 服务发现 | schema 来源 |
|---|---|---|---|
| **Go** | 真源 `service/favorite/rpc` 的**只读副本**（`go build` 产物落临时目录） | **etcd**（`Etcd.Key: favorite.rpc` ⇒ 键 = `favorite.rpc/<leaseID>`） | 真源 `InitDB()`（AutoMigrate）+ 真源 4 个 `.sql`（**sql-first** 基准） |
| **RAG** | `favorite-rpc-0.0.1-SNAPSHOT.jar`（**可部署产物本身**） | **Nacos**（服务名 `favorite-rpc`） | 它自己的 **Flyway V1..V7**（账本 `flyway_schema_history_favorite`） |

⇒ **两侧的服务发现机制不同**（这是**已签字的有意偏离**）⇒ 影响见 §五 的 **AMBIENT 档**。

### 真源冻结 ⇒ 只改「副本」

Go 侧用的是**真源真正在用的生成码/二进制**；真源 yaml 把主机名写死了
（`etcd:2379` / `postgres` / `jaeger:4317`）⇒ 脚本生成**配置副本**改成宿主可达地址。
脚本**逐条 `grep` 断言**改了哪一项（缩进写错的 `sed` 会静默不命中 ⇒ 副本端口没改 ⇒ 端口一被占就 panic ⇒ 探针假挂）。

> 🔴 脚本**每次**都断言 `git status --porcelain -- service/favorite` 为空 ⇒ **真源一个字都没被改**。

---

## 三、Go 侧 schema：为什么是「sql-first」

真源 `InitDB`（`internal/model/db.go:33`）只做 `AutoMigrate` ⇒ **三张表一起建**
⇒ 手写 `001_favorite_fact_outbox.sql` 的 `CREATE TABLE IF NOT EXISTS` 会变成 **no-op**
⇒ 它的 `CHECK (aggregate_version IN (1,2))` 与部分索引**建不出来**
⇒ **不是**本仓已钉死的判定基准（`tools/ddl-parity-probe/favorite/README.md:36`）。

⇒ 本探针的做法：**预热起一次**（AutoMigrate 建表）⇒ 停 ⇒ **`DROP TABLE favorite_fact_outbox`**
⇒ 跑 **001..004** ⇒ 再起 ⇒ 于是 001 执行时表集合 = `{favorite_folder, favorite_item}`（与基准一致）。

> ⚠️ 003 要 `ALTER favorite_item` ⇒ **必须**在 `favorite_item` 存在之后 ⇒ 这就是「预热那一次」的作用。

---

## 四、矩阵（28 条 · 2 档）

| 档 | 条数 | 判据 | 用例 |
|---|---|---|---|
| **FROZEN** | **27** | **Go == RAG == `expected.txt`**（逐字节） | a 读收藏夹（3）· b 读条目（2）· c 读错误（5）· d 改名（7）· e 删除（5）· f 建收藏夹（2）· h 建条目（2）· g 级联删除（1） |
| **AMBIENT** | **1** | 只判 **Go == RAG**（不一致**只告警、不判红**） | `f03_create_downstream_absent`（走 `ensureUserExists` ⇒ 结果取决于下游 `user.rpc` 在不在场） |

> ⚠️ **用例顺序 = 副作用顺序**：只读全在前、写在后，且写之后不再读
> （「改名」会推进 `update_time` ⇒ 之后的读会拿到「本次运行的时刻」⇒ 冻结值必假红）。
>
> ⚠️ **h 组（建条目）排在 g（级联删除）之前**：h 两条虽是写意图，但都在**任何下游调用之前**
> 就返回（`create_favorite_logic.go:54-60` / `:62-70`）⇒ **零副作用** ⇒ 不破坏「g 是最后一条
> 有副作用」的不变量。

### 🔴 为什么 `f03` 必须是 AMBIENT 档（**用实测数据钉的，不是推理**）

| 轮次 | Go 侧 | RAG 侧 | 结论 |
|---|---|---|---|
| 第 1 轮（冻结时） | `INTERNAL` / 系统内部错误 | `INTERNAL` / 系统内部错误 | 两侧都「下游不可用」 |
| 第 2 轮 | `NOT_FOUND` / 用户不存在 | `NOT_FOUND` / 用户不存在 | 两侧都「下游在场但用户不存在」 |
| 第 3 轮 | `NOT_FOUND` / 用户不存在 | `INTERNAL` / 系统内部错误 | **两侧不一致** |

⇒ 根因：**两侧用的是两个不同的注册中心** —— Go 侧走 **etcd**（`Etcd.Key: user.rpc`），
RAG 侧走 **Nacos**（`discovery:///user-rpc` ⇒ 服务名 `user-rpc`）
⇒ 「下游在不在场」是**环境态**（别的 lane 的探针会往两个注册中心各注册一个 `user-rpc` 桩）
⇒ **冻结它 = 冻结一个环境态 ⇒ 下一轮必假红**。

⇒ 故：`f03` **不入 `expected.txt`**（`FREEZE=1` 只冻结 FROZEN 档），
比对时**只判 Go == RAG**，不一致时**告警但不判红**（并在台账里写明这条边界）。

---

## 五、实测结果（2026-10-31 · G3 lane）

### 5.1 命令与结果

```bash
$ bash tools/api-full-parity-probe/favorite-rpc/run.sh
...
   ✓ Go  侧 gRPC 自检通过（INVALID_ARGUMENT）
   ✓ RAG 侧 gRPC 自检通过（INVALID_ARGUMENT）
   ✅ a01_get_folders_user1001  Go == RAG == expected.txt（OK · 响应字节 128 B）
   ✅ a02_get_folders_user1002  Go == RAG == expected.txt（OK · 响应字节 49 B）
   ✅ a03_get_folders_user9999  Go == RAG == expected.txt（OK · 响应字节 0 B）
   ✅ b01_get_items_folder1     Go == RAG == expected.txt（OK · 响应字节 168 B）
   ✅ b02_get_items_folder2     Go == RAG == expected.txt（OK · 响应字节 0 B）
   ✅ c01_get_folders_uid0      Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ c02_get_folders_uid_neg   Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ c03_get_items_uid0        Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ c04_get_items_folder_missing Go == RAG == expected.txt（NOT_FOUND · 响应字节 0 B）
   ✅ c05_get_items_other_user  Go == RAG == expected.txt（PERMISSION_DENIED · 响应字节 0 B）
   ✅ d01_update_uid0           Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ d02_update_name_empty     Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ d03_update_not_found      Go == RAG == expected.txt（NOT_FOUND · 响应字节 0 B）
   ✅ d04_update_other_user     Go == RAG == expected.txt（PERMISSION_DENIED · 响应字节 0 B）
   ✅ d05_update_conflict_name  Go == RAG == expected.txt（ALREADY_EXISTS · 响应字节 0 B）
   ✅ d06_update_same_name      Go == RAG == expected.txt（OK · 响应字节 2 B）
   ✅ d07_update_new_name       Go == RAG == expected.txt（OK · 响应字节 2 B）
   ✅ e01_delete_folder_uid0    Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ e02_delete_folder_not_found Go == RAG == expected.txt（NOT_FOUND · 响应字节 0 B）
   ✅ e03_delete_folder_other_user Go == RAG == expected.txt（PERMISSION_DENIED · 响应字节 0 B）
   ✅ e04_delete_item_not_found Go == RAG == expected.txt（NOT_FOUND · 响应字节 0 B）
   ✅ e05_delete_item_other_user Go == RAG == expected.txt（PERMISSION_DENIED · 响应字节 0 B）
   ✅ f01_create_uid0           Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ f02_create_name_empty     Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ⚠️  f03_create_downstream_absent  Go != RAG（AMBIENT 档 · **不判红**）
   ✅ h01_create_item_uid0       Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ h02_create_item_target_empty Go == RAG == expected.txt（INVALID_ARGUMENT · 响应字节 0 B）
   ✅ g01_delete_folder_cascade Go == RAG == expected.txt（FAILED_PRECONDITION · 响应字节 0 B）
== 9/9 结论 ==
⚠️  1 条 AMBIENT 用例两侧不一致（**不判红**，见上）
✅ 双侧逐字节一致：Go 真源 == RAG == 冻结期望值（FROZEN 档 27 条用例 · gRPC wire 层）
EXIT=0
```

### 5.2 覆盖到的事实（**不是「跑绿」**）

| 事实 | 证据 |
|---|---|
| 收藏夹列表的**字段序 + 时间戳单位**逐字节一致 | `a01` 响应 **128 B**（3 个收藏夹：`create_time` DESC ⇒ 顺序也是字节序的一部分） |
| 收藏条目的**全部 8 个字段**（含 `target_id` / `target_type` / `title` / `cover`）逐字节一致 | `b01` 响应 **168 B** |
| **8 种业务错误**的 gRPC 码 ＋ **中文 description 文案**逐字节一致 | `c01`/`c04`/`c05`/`d02`/`d05`/`e04`/`g01`/`h02`（`参数错误` / `收藏夹不存在` / `无权限操作` / `收藏夹名称不能为空` / `收藏夹名称已存在` / `收藏记录不存在` / `收藏暂不可撤回` / `收藏目标不能为空`） |
| **`CreateFavorite` 的两段入参校验**（**在任何下游调用之前**）逐字节一致 | `h01`（`user_id<=0` ⇒ `参数错误`）/ `h02`（`target` 为空 ⇒ `收藏目标不能为空`）⇒ ⇒ 两侧的**校验顺序与文案同构**（Go `create_favorite_logic.go:54-60` / `:62-70`；RAG `FavoriteDomainService#createFavorite` 同序）⇒ ⇒ **7/7 个 rpc 方法全部被对拍客户端调过** |
| **级联删除**走的是「历史未和解 ⇒ 不可撤回」那条分支 | `g01` = `FAILED_PRECONDITION` ＋ `收藏暂不可撤回`（5304）⇒ 两侧**都**被 `favorite_legacy_fact_marker` 挡住 ⇒ 说明两侧的**事实/遗留标记表语义一致** |
| **同名改名直接返回成功、不写库** | `d06`（`OK`，2 B = `success=true`） |
| **重名 ⇒ `AlreadyExists`** | `d05` |

### 5.3 证据强度

| 项 | 强度 | 说明 |
|---|---|---|
| **Go 侧** | **L4（双侧活体）** | 真源二进制 + 真源配置副本 + 真 PG + 真 etcd 注册 |
| **RAG 侧** | **L4（双侧活体）** | 真 fat jar + 真 Flyway + 真 MyBatis + 真 PG + 真 Nacos 注册 |
| **比对口径** | **wire 层逐字节**（不是字段级） | 原始字节截获（§一） |
| **期望值** | **冻结**（`expected.txt`） | 27 条 FROZEN ⇒ 真源一变立刻 exit 1 |
| **变异** | ✅ 3 条**精确转红** + 还原后转绿 | 见 §七 |
| ⚠️ **整体** | **不是「favorite 域全部行为已闭合」** | 边界见 §六 |

---

## 六、证据边界（明确**未验**的部分）

| 项 | 状态 | 说明 |
|---|---|---|
| **下游 `user.rpc` 在场**的分支 | 🟡 **AMBIENT 档** | 两侧注册中心不同 ⇒ 在场与否是环境态 ⇒ 只判 Go == RAG（§四） |
| **下游 `article.rpc` 在场**的分支 | 🔴 **未验** | `createFavorite` 的文章快照分支需要真 article-rpc ⇒ 本探针未接 |
| **create 成功路径**（落库 + 雪花 ID + 事实写入） | 🔴 **未验** | 依赖下游 `user.rpc` ⇒ 与 `tools/grpc-contract-probe/favorite/` 的第二轮（自带桩）互补，**不重复取证** |
| **帧层原始字节**（HPACK / 帧序 / WINDOW_UPDATE） | 🔴 **不做** | 由 gRPC 库决定，客户端不可观测（与 `tools/grpc-contract-probe/favorite/` 同一取舍） |
| **NULL 的 `title` / `cover`** | 🔴 **未验** | 种子**一律给非 NULL**（Go 的 `toProtoItem` 直接塞 `string`，RAG 走 `nullToEmpty` ⇒ NULL 列在两边的扫描语义不同）⇒ 排除在本探针之外 |
| **`update_time` 推进后的读** | ⬜ **有意不做** | 「改名」会推进 `update_time` ⇒ 之后的读拿到「本次运行的时刻」⇒ 冻结值必假红 ⇒ 读用例全部排在写用例之前 |

---

## 七、变异验证（`mutate-parity.sh`）

AGENTS.md §3.3.3：新增的每个探针都必须配一个**能真正触发它**的变异，否则无法排除「假绿」。

| 变异 | 手法 | 期望 |
|---|---|---|
| **M1** | 改 `expected.txt` 里一枚 hex | 探针 `run.sh` **EXIT=1**（Go 侧与冻结值不符） |
| **M2** | RAG 侧 `favorite.proto` 的 **`FavoriteItem.favorite_id` 字段号 1 → 9** | 探针 **EXIT=1**（**wire 字节**漂移被抓到 ⇒ 证明「比的是 wire 字节」而不是「解析后字段看起来一样」） |
| **M3** | RAG 侧 `FavoriteErrorMessages` 的 **5003 文案「参数错误」→「参数错误X」** | 探针 **EXIT=1**（description 漂移被抓到 ⇒ 本域业务码的载体有牙齿） |

> ⚠️ **M2 的字段号必须取未被占用的**（`FavoriteItem` 占了 **1..8** ⇒ 取 **9**）：
> 取 7 会与 `cover = 7` 撞号 ⇒ **protoc 直接报错** ⇒ 构建失败 ⇒ 探针 `exit 2`
> ⇒ 症状看起来像「探针看不见漂移」，**其实是变异本身非法**（2026-10-29 实测踩过）。

⇒ 还原一律用「**文件备份 + 复制回来**」（绝不用 `git checkout --`）⇒ 且脚本**退出时无条件还原**
（万一中途被 kill，**产品文件**不能留在变异态）。

⚠️ ⚠️ **不要在脚本运行期间改它** —— bash 是**按字节偏移增量读取**脚本的 ⇒
改动文件长度会让后续读取落到**行中间** ⇒ `unexpected EOF while looking for matching '"'`
（2026-10-29 实测踩过：改 M2 时把正在跑的脚本改崩了）。

---

## 八、探针本身踩过的坑（2026-10-29 全部实测）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 脚本按端口 `kill` 残留进程 ⇒ **Docker daemon 当场挂掉** | 宿主上**发布端口**的容器会让 `com.docker.backend services` **持有该端口** ⇒ `lsof -ti tcp:<port>` 看到的是 docker 后端 ⇒ 无差别 kill 把它杀了 | 先删**自己前缀**（`r2fav-`）的容器，再查端口；只杀命令行命中本探针特征的进程，别人的进程 ⇒ **报错退出**，不越界动手 |
| 2 | 「按端口无差别 kill」误杀 ⇒ 整轮报废 | 同上 | 同上 |
| 3 | Go 侧 `panic: listen tcp ...: bind: address already in use` ⇒ 但 etcd 键与端口检查**都通过** | `( cd X && cmd ) &` ⇒ `$!` 是**子 shell** 的 PID ⇒ `kill $!` 杀的是子 shell，**真服务还活着** ⇒ 端口不释放 ⇒ 第二次启动撞车；而旧进程还在服务 ⇒ 检查全过 ⇒ 探针以为起来了 | 子 shell 里用 **`exec`**（把子 shell 替换成服务 ⇒ `$!` 就是服务 PID）＋ 杀完**轮询等端口关闭** |
| 4 | 端口段被**别的 lane 抢走** ⇒ RAG 侧 `BindException: Address already in use` | 宿主上多个 lane 的探针**同时跑** ⇒ 端口是抢占式的（实测：任务书给的 18131-18135 被 `r2like-*` 容器占满） | 自动挑**全空闲**的 5 连号端口（起点按 `$$` 打散）＋ **每个服务 bind 失败就换端口重试 3 次** |
| 5 | `seq: needs negative decrement` | macOS 的 `seq` 是 **BSD 版**（`seq [first [incr]] last`），GNU 是 `first last step` ⇒ 写成 GNU 序就炸 | 改用 `while` 循环，不依赖 `seq` 的参数序 |
| 6 | PG 容器「就绪超时」但日志看着正常 | ① `docker exec pg_isready` 走 **unix socket** ⇒ PG 初始化阶段的**临时 server** 会**过早**报 ready；② 只探宿主口 ⇒ **docker-proxy 起来那一刻**就能连（实测 **0s 就 OPEN**）⇒ 同样过早；③ 宿主 load average 实测 **103** ⇒ initdb 要几十秒 | 判据 = `docker exec pg_isready -h 127.0.0.1 -p 5432`（容器内 **TCP**）＋ 宿主口；上限 **180s**、重试 3 次 |
| 7 | Go 侧进程活着、端口在监听，但**所有** RPC 返回 `UNAVAILABLE / io exception` | 该文案来自 **grpc-java 客户端**的 netty 传输层 ⇒ **客户端连不上**（不是服务端返回的）；实测出现在 Go 侧**空闲约 95s** 之后（早于矩阵） | ① 把 **Go 侧启动挪到矩阵前**（空闲窗口压到秒级）② 矩阵前做**存活自检**（失败 ⇒ **重启一次**再自检）③ 自检不过 ⇒ **exit 2「环境故障」**，**绝不**报成 28 条漂移 |
| 8 | `io grpc.ClientCalls` 找不到符号 | `ClientCalls` 在 **`io.grpc.stub`** 包，不在 `io.grpc` | 改正 import |
| 9 | go-zero 的日志**不在 stdout** | 真源 yaml `Log.Mode: file` ⇒ logx 落在 `${WORK}/log/favorite-rpc/` ⇒ 只 dump stdout 会漏掉真正的报错现场 | `dump_logs` 追加 dump **go-zero 的日志文件** |
| 10 | `dependency:build-classpath` 把 `favorite-proto` 解析成 **~/.m2 里的快照 jar**（可能是旧的） | Maven 的单模块依赖解析 | 把该条目换成本仓**刚构建出来**的产物（与被测 jar 同一批） |
| 11 | bash 3.2：`set -u` 下展开**空数组**报 `unbound variable` | macOS 自带 bash 3.2 | `${A[@]+"${A[@]}"}` 形式 |
| 12 | `rm -rf` 被本机回收站守卫拦下 | — | **不删临时目录**（`mktemp -d` 留给系统清理） |
| 13 | 变异 M2「没转红」，实测是 `exit 2`（构建失败） | `favorite_id = 7` 与 `cover = 7` **撞号** ⇒ **protoc 报错** ⇒ 变异本身非法 | 字段号取**未占用**的 **9** ⇒ 「没转红」要先看 **exit 码**：1 = 探针判定；2 = 环境/构建故障 |
| 14 | 正在跑的脚本被改 ⇒ `unexpected EOF` | bash **按字节偏移增量读取**脚本 ⇒ 改长度 ⇒ 后续读取落到行中间 | **不要在脚本运行期间改它** |

---

## 九、复现

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r2-favorite   # align/r2-favorite 工作树
bash tools/api-full-parity-probe/favorite-rpc/run.sh           # EXIT=0 = 两侧逐字节一致
bash tools/api-full-parity-probe/favorite-rpc/mutate-parity.sh  # 变异验证（M1~M3 ⇒ 必须精确转红）

# 重新冻结期望值（真源行为变了才做）
FREEZE=1 bash tools/api-full-parity-probe/favorite-rpc/run.sh
```

⚠️ 需要：docker（真 PG）· Go toolchain（≥1.25）· JDK 21+ · mvn（**离线 `-o`**，且**必须带 `-am`**）·
宿主中间件：`Sea-etcd`（49319）· `Sea-nacos`（49848）·（`Sea-jaeger` 49317 · `Sea-postgres` 49432）。

可用 env 覆盖：`GO_REPO` · `ETCD_PORT` · `ETCD_CTL_ENDPOINT` · `NACOS_PORT` · `JAEGER_PORT` ·
`PORT_BLOCK_START` · `GO_RPC_PORT` / `RAG_RPC_PORT` / `GO_DEV_PORT` / `GO_PG_PORT` / `RAG_PG_PORT` ·
`KEEP_WORK=1`（保留临时目录）· `FREEZE=1`。
