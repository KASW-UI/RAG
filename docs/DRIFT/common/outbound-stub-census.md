<!-- 本文件是「出向 gRPC stub 普查」的可复算台账（G4 lane · 2026-10-31）。 -->

# 出向 gRPC stub 普查（三个数的**唯一权威口径**）

> **对象**：RAG 侧**所有出向 gRPC 调用**的形态普查 ⇒ 产出三个数：**出向 stub 类** / **出向 RPC 调用点** / **零 per-call 载体调用点**。
>
> **为什么单独建档**：这三个数在台账里曾长期并存多个读数（`30 处` / `27 处` / `10 处` / `9 处` / `11 类` / `7 类`），
> 且每轮都被写成「实测数」。根因**不是数错了**，而是**每次数的口径不同**（数的是「provider 调用点」还是
> 「service 层出向语句」还是「class 数」？）—— **口径没写死 ⇒ 数字无法互相比较 ⇒ 每次订正都像在推翻上一轮**。
> ⇒ 本档把口径**写死**并附**可复算脚本**，使任何第三方都能重跑得到同一组数。
>
> **关联**：[`../../DRIFT/global/vs-common.md`](../../DRIFT/global/vs-common.md) §4.19.54（本轮的登记与作废声明）·
> [`./protection.md`](./protection.md)（共享层例外集守门 `GoStyleClientCallTimeoutSingleSourceGateTest`）·
> [`./deadline.md`](./deadline.md)（HTTP 超时取消语义 · 另一轴）

---

## 一、三个数的口径（**定死** · 2026-10-31）

| # | 量 | 口径（**逐字**） |
|---|---|---|
| **①** | **出向 stub 类** | 含**真实** `@GrpcClient("…")` 注入的域侧 client 类。**骨架占位类不算** —— `article-rpc` 的 `SecurityRpcClient` / `ImageSecurityRpcClient` 只在**注释**里写了 `@GrpcClient`，无注入 ⇒ 全仓 **23** 个 `*RpcClient.java` 里只有 **21** 个算数。 |
| **②** | **出向 RPC 调用点** | **一条出向语句**（一行）= 一次可能真正发出的出向 RPC。载体形态共 **4 种，全部计入**：<br>**A** `clientVar.provider().rpcMethod(…)`（provider 模式）· **B** `clientVar.businessMethod(…)`（client 类直接暴露业务出口，RPC 在其内部）· **C** `localProvider().rpcMethod(…)`（本地/继承的二级 provider，如 `follow-api` 的 `rpc()`）· **D** `injectedField.rpcMethod(…)`（类内直连注入的 stub 字段）。<br>⇒ **provider 本身不算调用点**（`rpc()` 只是取 stub，RPC 在其后）⇒ ⇒ **同一行只算 1 次**（A 路与 C 路可能指向同一条语句 ⇒ 按「文件 + 行」去重）。 |
| **③** | **零 per-call 载体调用点** | 该调用点拿到的 stub **没有**显式 per-call 载体。载体来源分**三档**（这是台账此前把两种来源混在一起的地方）：<br>· **共享层载体** = `GoStyleRequestDeadline.applyTo(…)` / `GoStyleGrpcCallDeadline.apply(…)`<br>· **域侧自带 cap** = 域内出现 `withDeadlineAfter(…)` ⇒ ⇒ **违反共享层判据④**（⇒ 进例外集）<br>· **零载体** = 两者皆无 ⇒ 靠共享层 `GoStyleOutboundDeadlineInterceptor` 的组件级 `cap-millis` / `cap-millis-by-service` 兜底 |

⚠️ **③ 的语义边界（不要读成行为漂移）**：
「零 per-call 载体」是**表达层形态**的量，**不是**「零上界」。
- **是否继承请求预算** ⇒ 由 devh 的 `@GrpcGlobalClientInterceptor` 装在 **channel** 上决定 ⇒ ⇒ **零载体调用点照样继承**请求 deadline（运行期证据见 `vs-common.md` §4.19.15）。
- **是否有客户端硬上界** ⇒ 由组件 `cap-millis` / `cap-millis-by-service` 决定 ⇒ ⇒ 本轮实测 **16 个有出向调用的组件全部有硬上界** ⇒ ⇒ **「真零上界」= 0**。
⇒ ⇒ **「零 per-call 载体」≠「零 deadline」≠「零上界」** —— 三个是**不同的量**，不得混用（这正是旧读数打架的第二个来源）。

---

## 二、实测读数（2026-10-31 · G4 lane · 本层自己跑）

```
① 出向 stub 类                = 21
② 出向 RPC 调用点              = 80
③ 载体：共享层 42 · 域侧自带cap 1 · 零载体 37
   ⇒【零 per-call 载体】= 37
```

### 逐组件

| 组件 | 出向 RPC 调用点 | 共享层载体 | 域侧自带 cap | 零载体 | 硬上界来源 |
|---|---|---|---|---|---|
| `article-api` | 10 | 9 | **1** ⚠ | 0 | `cap-millis-by-service`（`article.ArticleService:70000` + `user.UserService:2000`）· **组件级刻意不配** |
| `article-rpc` | 1 | 0 | 0 | 1 | 组件级 `cap-millis=2000` |
| `comment-api` | 7 | 0 | 0 | 7 | 组件级 `cap-millis=2000` |
| `comment-rpc` | 2 | 0 | 0 | 2 | 组件级 `cap-millis=2000` |
| `favorite-api` | 7 | 0 | 0 | 7 | 组件级 `cap-millis=2000` |
| `favorite-rpc` | 2 | 0 | 0 | 2 | 组件级 `cap-millis=2000` |
| `follow-api` | 8 | 8 | 0 | 0 | 组件级 `cap-millis=2000` |
| `follow-rpc` | 2 | 0 | 0 | 2 | 组件级 `cap-millis=2000` |
| `hot-api` | 2 | 2 | 0 | 0 | 组件级 `cap-millis=2000` |
| `like-api` | 6 | 0 | 0 | 6 | 组件级 `cap-millis=2000` |
| `like-rpc` | 2 | 0 | 0 | 2 | 组件级 `cap-millis=2000` |
| `message-api` | 3 | 3 | 0 | 0 | 组件级 `cap-millis=2000` |
| `message-rpc` | 6 | 0 | 0 | 6 | 组件级 `cap-millis=2000` |
| `task-api` | 2 | 0 | 0 | 2 | 组件级 `cap-millis=2000` |
| `admin-api` | 12 | 12 | 0 | 0 | 组件级 `cap-millis=2000` |
| `user-api` | 8 | 8 | 0 | 0 | 组件级 `cap-millis=2000` |
| **合计** | **80** | **42** | **1** | **37** | **16/16 组件有硬上界 ⇒「真零上界」= 0** |

### 唯一的「域侧自带 cap」点位

| 文件:行 | 形态 | 说明 |
|---|---|---|
| `service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleAuthorResolver.java:102` | `userRpcClient.userRpc().getUser(…)` | 载体在 `service/article/article-api/src/main/java/com/rag/article/article/api/client/UserRpcClient.java:87`（`withDeadlineAfter(effective, …)`）⇒ ⇒ **与共享层例外集里那唯一一条是同一条**（三方独立吻合：例外集守门 `KNOWN_INLINE_CAP_CALL_SITES` = 1 条 · 本轮普查「域侧自带 cap」= 1 条 · 点位一致） |

⚠️ **该点位不可剪**：Go 侧 `service/article/api/etc/article-api.yaml` 的 `UserRpcConf` 段**没有** `Timeout`
⇒ ⇒ 走 `zrpc` struct tag 默认 **2000ms**；而同文件 `ArticleRpcConf.Timeout: 70000` ⇒ ⇒ **两个方向真值不同**
⇒ ⇒ 组件级单值表达不了 ⇒ ⇒ 共享层已提供 `cap-millis-by-service` 分档 ⇒ ⇒ **该点位是「分档落码前的过渡形态」**，
**不是「待剪的副本」**（剪掉它 ⇒ user-rpc 方向会退回共享层组件级 —— 而 article-api 组件级**刻意不配** ⇒ ⇒ 反而**失去**上界）。

---

## 三、旧读数并排 + **不可复算**的证明

| 量 | 台账旧读数（出处） | 本轮实测 | 判定 |
|---|---|---|---|
| 出向 stub 类 | **21**（`vs-common.md` 附·四表 · 2026-10-30） | **21** | ✅ **一致**（两个独立实现同数） |
| 出向调用点 | **30**（`vs-common.md` `:3812` B 组标题 · 2026-10-29）<br>**27**（G1 反馈单 1/3 · 2026-10-30） | **80** | 🔴 **旧数不可复算 ⇒ 作废**（见下表） |
| 零 per-call 载体调用点 | **30** / **10**（`vs-common.md` `:6916` 第四节 · 2026-10-31 局部订正） | **37** | 🔴 **旧数不可复算 ⇒ 作废** |
| 零 per-call 载体**类**数 | **11** / **9** / **7** | **10 个组件**（**12 个 client 类**） | 🔴 **旧数不可复算 ⇒ 作废** |
| 「零上界」类 | **11** / **7** | **0** | ✅ **与 `vs-common.md` `:6916` 的「真零上界 = 0」同向**（该节已独立订正过） |
| `admin-api` 出向调用点 | **12**（B-11 · 2026-10-30） | **12** | ✅ **一致** |
| `user-api` 出向调用点 | **8**（B-10 · 2026-10-30） | **8** | ✅ **一致** |

### 候选口径检验（**为什么说旧数不可复算**）

本层把「旧数可能是哪个口径」逐个**实现**了一遍（不是猜）：

| # | 候选口径 | 读数 | 是否命中旧数 |
|---|---|---|---|
| ① | 全部出向 RPC 调用点 | **80** | ✘ |
| ② | 零任何 per-call 载体（无 shared / 无 domain） | **37** | ✘ |
| ③ | 零 `applyTo` 载体（只认 `GoStyleRequestDeadline.applyTo`） | **39** | ✘ |
| ④ | 零载体的**文件**数 | **34** | ✘ |
| ⑤ | 零载体的**组件**数 | **10** | ✘ |
| ⑥ | 零载体 且在 **api** 组件 | **22** | ✘ |
| ⑦ | 零载体 且在 **rpc** 组件 | **15** | ✘ |
| ⑧ | **rpc** 组件的全部调用点 | **15** | ✘ |
| ⑨ | **api** 组件的全部调用点 | **65** | ✘ |
| ⑩ | 零载体 且是「纯 client 类内直连」 | **6** | ✘ |

⇒ ⇒ **10 个候选口径无一给出 30 / 27 / 11 / 7 / 9** ⇒ ⇒ **旧读数无法由任何可实现的判据复算**
⇒ ⇒ **结论：它们是「手工作累加」的产物，且累加过程未留口径** ⇒ ⇒ **一律作废**，以本档 §二 为唯一权威读数。

⚠️ **这一条本身才是本轮最值钱的产出**：台账此前每次订正都在「推翻上一轮的数字」，但**没有任何一次说清「上一轮那个数到底是怎么数的」** ⇒ ⇒ 于是数字可以无限推翻、永远对齐不上。⇒ ⇒ **口径写死 + 脚本可复算**才是止血的唯一办法。

---

## 四、复算方式

```bash
cd <仓库根>
python3 docs/DRIFT/common/outbound-stub-census.py
```

⇒ 打印三个数 + 逐组件表，并把 **80 条清单**落成机器层
[`./outbound-stub-census.tsv`](./outbound-stub-census.tsv)（列：`comp` · `rel` · `line` · `outlet` · `carrier` · `kind`）。

⚠️ **脚本的两条硬约束**：
1. **只读**：只读 `service/**/src/main/**` 与 `*.proto`，**不写任何产品代码** ⇒ ⇒ 可随时重跑。
2. **依赖 Go 真源**：proto rpc 方法名全集取自 `Sea-RideTheWind_golang`（+ RAG 侧 `*.proto`）⇒ ⇒
   若 Go 仓路径变动，只需改脚本头部 `GO_ROOT` 一行。

### 抽样核对（人工逐条核过，结论与脚本一致）

| 抽样 | 期望 | 实测 |
|---|---|---|
| `article-api` 全部出向调用点 | 10 条（9 共享层 + 1 域侧 cap） | ✅ 10（9 + 1） |
| `admin-api` | 12 条，**全部**经 `adminRpc()`（共享层 `applyTo`） | ✅ 12 / 12 |
| `user-api` | 8 条，**全部**经 `userRpc()` | ✅ 8 / 8 |
| `follow-api` | 8 条（`rpc().follow/unfollow/block/unblock/getFollowList/getFollowerList/getBlockList/getRecommendations`） | ✅ 8 |
| `hot-api` | 2 条（`GoStyleGrpcCallDeadline.apply` 载体 ⇒ 共享层档） | ✅ 2 / 2 共享层 |
| `like-rpc` | 2 条（`stub.sendNotification` 类内直连 + `sendArticleLiked` 业务出口） | ✅ 2 / 2 零载体 |
| `message-rpc` | 6 条（`adminStub.getSelf` / `adminStub.getUserList` / `userStub.getUser` 三条类内直连 + 三条业务出口） | ✅ 6 / 6 零载体 |

---

## 五、Go 侧对照（**这一轴的漂移形态**）

| 项 | Go 真源 | RAG 侧 | 判定 |
|---|---|---|---|
| 客户端硬上界 | `go-zero@v1.10.2/zrpc/config.go:31`（`Timeout int64` 的 struct tag **默认值 2000**）⇒ **每个 client 段一个**，缺省 2000<br>`go-zero@v1.10.2/zrpc/client.go:70-71` `if c.Timeout > 0 { opts = append(opts, WithTimeout(…)) }` ⇒ **无条件**装上界<br>`go-zero@v1.10.2/zrpc/internal/client.go:115-116` + `zrpc/internal/config.go:15`（`Timeout bool` 的 struct tag **默认值 true**）⇒ 客户端超时中间件**默认开** | 组件级 `cap-millis` + `cap-millis-by-service` 分档（共享层 `GoStyleOutboundDeadlineInterceptor` 统一施加） | ✅ **行为面已对齐**（16/16 组件有硬上界 ⇒ **真零上界 = 0**）<br>⚠️ **表达面仍有 37 处「零 per-call 载体」** ⇒ 那不是行为漂移（共享层拦截器兜底）⇒ ⇒ **登记为形态差异，不登记为漂移** |
| per-client 分档 | 各 client 段可各自覆盖（`article-api.yaml` 的 `ArticleRpcConf.Timeout: 70000` vs `UserRpcConf` 无 ⇒ 2000 ⇒ **差 35 倍**） | `cap-millis-by-service`（按下游 gRPC 服务全名覆盖组件级，**覆盖语义而非 min**） | ✅ **机制已具备**（共享层 `GoStyleOutboundDeadlinePerServiceCapGateTest` 四形态钉死）<br>⚠️ **仅 `article-api` 一个组件切到分档** ⇒ 其余 15 个组件的 Go 真值恰好都 = 2000 ⇒ ⇒ **不切也不算漂移** |

---

## 六、本档的诚实边界（四条）

1. **「出向 RPC 调用点」是静态口径** —— 它数的是**代码里的出向语句**，**不是**运行期实际发出的 RPC 次数。
   ⚠️ 有调用点被条件分支包住（⇒ 运行期可能 0 次），本口径**不区分** ⇒ ⇒ 读它是「代码面出口数」，不是「流量」。
2. **载体判定是「静态形态」** —— 「有载体」只说明**代码里写了** `applyTo` / `withDeadlineAfter`，
   **不保证**运行期载体非空（例如 `applyTo` 在无请求预算时给 1ns，语义见 `GoStyleRequestDeadline`）⇒ ⇒
   ⚠️ **行为层的验证仍是运行期守门的事**（`GoStyleOutboundDeadline*GateTest` 家族）⇒ ⇒ 本档**不替代**那些守门。
3. **脚本用正则解析 Java** ⇒ ⇒ 对**非常规写法**（如泛型化的 provider、反射取 stub）可能漏
   ⇒ ⇒ 本轮已用**两条独立实现 + TSV 逐字节对拍 + 7 处人工抽样**交叉验证 ⇒ ⇒ **当前无已知漏项**，
   ⚠️ 但**这不是形式化证明** ⇒ ⇒ 域侧新增出向形态时**必须重跑本脚本**（否则这三个数会静默过期）。
4. **「零上界 = 0」这一格不来自本脚本** —— 它来自「各域 `application.yml` 的 `cap-millis` / `cap-millis-by-service`」的逐组件核对
   （15 个组件配了组件级 + `article-api` 配了分档两方向 ⇒ **16/16**）⇒ ⇒ **该格随域侧改 yml 而变** ⇒ ⇒ 同样是**需要重跑**的量。

---

## 七、本档**不**主张什么（防止被读成「已对齐」）

- ❌ **不主张**「出向 deadline 已全对齐」 ⇒ ⇒ **X-6 判据整体仍 🔴**（残余 = 客户端硬上界**按 Go 逐方向**配齐，
  即 P2-T04；`article-rpc` 的 security 方向当前**不可观测** —— 安全客户端是骨架占位、全仓零引用）。
- ❌ **不主张**「37 处零载体 = 37 处行为漂移」 ⇒ ⇒ 它们**继承**请求预算、**有**硬上界 ⇒ ⇒ **形态差异，非行为漂移**。
- ❌ **不主张**「本档取代 `vs-common.md` 的既有条目」 ⇒ ⇒ 本档只提供**三个数的权威口径与读数**；
  各条漂移的**定级与阶段归属**仍以 `vs-common.md` 为准。
