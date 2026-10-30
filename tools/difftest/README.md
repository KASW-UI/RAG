# difftest —— Go ↔ Spring Boot 双端运行时对拍

把 `Sea-RideTheWind_golang`（真理之源）与本仓库的 Spring Boot 重构**同时起进程**，
喂**同一串请求序列**，逐字段比对两边的 HTTP 响应。

这是 AGENTS.md §3.3「框架机制必须验证实际生效」的终极形态：
单测跑在 mock 数据层上，双端对拍跑在**两套真实进程 + 两个真实数据库**上。

```
./run.sh                # 完整跑（含 mvn + go build）
./run.sh --no-build     # 复用 /tmp/difftest/bin 下的产物
./run.sh --keep         # 跑完不收摊，便于手工 curl
python3 diff.py --scenario user    # 只跑 user 链路 9 端点
python3 diff.py --scenario admin   # 只跑 admin 链路 13 端点
```

退出码 0 = 没有未预期的差异。

---

## 1. 对拍面：为什么只有 HTTP wire 层

| | Go | RAG |
|---|---|---|
| 迁移方式 | GORM `AutoMigrate` | Flyway |
| 用户表 | `users` | `users_user` |
| 头像表 | `user_avatar_history` | `users_user_avatar_history` |

表名差异是 AGENTS.md §3.4 已签字的有意偏离（A-03），
**两边物理上无法共用 schema** ⇒ DB 层对拍不存在，唯一可比面是 HTTP 响应。

两侧各跑一遍完整序列（各自维护自己的 token / uid / 邀请码状态机），
不能交错执行 —— 交错会让后一侧捡到前一侧已经消费掉的邀请码。

易变值（服务端生成的 uid、history_id、token、邀请码、过期时间、时间戳）
由 `Normalizer` 统一映射成 `<UID#1>` / `<TS>` 之类的占位符后再比对，
**类型差异不抹平**：Go 的 uid 是字符串（`json:"uid,string"`），
若 RAG 侧是数字，会作为一个真实差异报出来。

---

## 2. 两侧怎么绕开服务发现

两侧**都**摘掉服务发现层，这样才是对称的：

- **Go**：`zrpc/config.go:91-95` 的 `RpcClientConf.BuildTarget()` 对 `Endpoints` 短路，
  不进 Etcd 分支；server 侧删掉 `Etcd` 整块，`zrpc/server.go:42` 的
  `if c.HasEtcd()` 不成立 ⇒ 不注册。
- **RAG**：`SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false` 关掉自动注册，
  gRPC 下游地址用 `--grpc.client.*.address=static://127.0.0.1:<port>` 直连。

> ⚠️ **RPC 模块必须保留注册逻辑可装配。** user-rpc / admin-rpc 里的
> `NacosRegistration` 是自定义 `@Component`，构造注入 `NacosServiceManager` bean；
> 把 `spring.cloud.nacos.discovery.enabled` 关掉会让上下文起不来。
> 它们的注册失败被 Java 侧 try/catch 吞掉（只打 ERROR 日志、不 rethrow），进程照常服务。
> 这也是为什么诊断「Nacos 连不上」时**不能**认定进程挂了 —— 要看
> `Started XxxApplication` 那行有没有出现。

---

## 3. 端口规划

两侧都必须独占，且**不能碰本机既有服务**。实测踩过：

- RAG 默认 admin-rpc `8081` / admin-api `8889` 与 Go 相同 → 直接撞
- RAG 默认 user-rpc `49210` / user-api `49900` 被本机 docker-compose 的
  `Sea-user-rpc` / `Sea-user-api` 容器占着
- `8081` / `8889` 上还跑着 **`Sea-RideTheWind_java`** 的进程
  （AGENTS.md §1 明确排除在 RAG 范围外的另一个实现）—— **绝不能去 kill 它**

| | user-rpc | admin-rpc | user-api | admin-api |
|---|---|---|---|---|
| Go | 19004 | 18081 | 18888 | 18889 |
| RAG | 59210 | 58081 | 59900 | 58889 |

基础设施：PostgreSQL `55432`、Redis `56379`（同样避开本机 docker-compose）。

---

## 4. 踩过的坑（每一条都真实浪费过时间）

### 4.1 探针把「404」当「就绪」

`curl` 拿到任何 HTTP 响应都 exit 0。上一版把「打到别人服务上的 404」误判成本服务就绪，
**整个对拍结果因此变成 12 条噪声 DIFF**。
探针必须显式拒绝 `000` 和 `404`。

### 4.2 `curl -w '%{http_code}'` 失败时输出 `000`

再加一层 `|| echo 000` 会拼成 `000000`，被 `!= 000` 判成「就绪」。
`code=$(curl ...) || code=""` 才是对的。

### 4.3 `VAR=x $SOME_VAR java -jar ...` 会报 command not found

bash 在**解析期**就决定命令的赋值前缀边界，`$SOME_VAR` 展开出来的
`NAME=value` 语法上不是赋值词，于是被当成命令名去执行。
正确写法是 `env NAME=value java -jar ...`。

### 4.4 `assert_started()` 声明了 `name` 参数却从没赋值

`set -u` 下只在**真触发失败分支**时才炸 ⇒ 平时一路绿灯，把失败信息吃掉了。
本轮修好之后立刻暴露了下一个问题（见 4.5）。

### 4.5 「进程起来了但 schema 是空的」是一类静默失败

Flyway 认为 schema 已是 v3、跳过自己的迁移、`users_user` 根本不存在，
却照样打出 `Started UserRpcApplication`。RPC 照常监听，只有真正打 SQL 时才炸，
而那时候报错长得像业务 bug。
`run.sh` 里现在有 `assert_tables`，逐张表验 `information_schema.tables`。

### 4.6 `${POSTGRES_PASSWORD}` 在 Go 配置里不展开

冻结仓库 `user.yaml:13` / `admin.yaml:14` 都用了 `${...}` 占位符，
但入口是 `conf.MustLoad(*configFile, &c)`，**没带 `conf.UseEnv()`**，
go-zero 不会 `os.ExpandEnv`。详见 AGENTS.md §6.2.5 的留档。
副本里已换成字面值。

### 4.7 `AdminSeederBootstrap` 是 `@PostConstruct`

它在 `finishBeanFactoryInitialization` 阶段就 SELECT/INSERT，
表不存在时直接 `BeanCreationException: ... relation "admin_admin" does not exist`。
所以 admin 的 DDL 必须在 **admin-rpc 启动之前**建好，不能靠 `sleep` 猜。

### 4.8 RAG 两个 rpc 同时启动时会撞 PG 的 DDL 竞态

去掉 Flyway 后，两个 rpc 都靠 `@PostConstruct` 里的 `SchemaInitializer`
把同一批 `CREATE TABLE IF NOT EXISTS` 打到同一个空库上。
**PG 的 `IF NOT EXISTS` 在并发下并不安全**，后到者报：

```
ERROR: duplicate key value violates unique constraint "pg_type_typname_nsp_index"
```

单测测不到（每次只起一个服务），只有本工具同时起两套栈才复现。
修法是 `SchemaInitializer` 取 PG 会话级 advisory lock，见 AGENTS.md §6.2.5。

> 本工具**故意让两个 rpc 同时启动**（不加 sleep 错开），就是为了守住这条竞态 ——
> 生产里 `docker-compose up` 也是同时起。

### 4.9 Read 工具会吃掉连续空格

`wait_http "..." "rag admin-api" 120` 里是两个空格。
按肉眼看到的单空格去 `edit` 会匹配失败，用 `python3` 做替换或 `od -c` 先验。

---

## 5. 这轮对拍抓出来的东西

跑通本身就是结论：**39 步 / 36 同 / 3 已签字 / 0 未预期差异**。

但它同时抓出了三个静态审查完全看不到的缺陷，都写进 AGENTS.md §6.2.5 了：

- **DRIFT-NEW-014（P0）** `user-api/application.yml` 漏了 `negotiation-type: plaintext`
  ⇒ user-api 连不上 user-rpc，所有业务码塌成 `1015 服务繁忙`。
  `admin-api` 当年踩过并修了（DRIFT-NEW-008），user 侧被漏掉。
- **DRIFT-NEW-015（P0）** Flyway 迁移版本号撞车 ⇒ `admin-rpc` 无法启动，
  而它按设计必须与 user-rpc 共用 `users_user`。已修：用户拍板去掉 Flyway，
  改为 `SchemaInitializer` 幂等重放 DDL + advisory lock 串行化。
- **PG 的 `CREATE TABLE IF NOT EXISTS` 并发不安全** —— 丢掉账本丢掉的那层保护，
  由 advisory lock 补回（见 §4.8）。
- **DRIFT-NEW-016（P2）** Go 的 `httpx.Parse` 把字段缺失当硬错误
  （只认 `,optional`，`,omitempty` 不算），RAG 无此校验。

---

## 6. 文件

| 文件 | 作用 |
|---|---|
| `run.sh` | 编排：基建 → Go 栈 → RAG 栈 → 对拍 → 收摊 |
| `diff.py` | 场景表 + 归一化 + 逐字段差分 |
| `docker-compose.yml` | 只起 postgres + redis（刻意不起 etcd / Nacos / jaeger） |
| `initdb.sql` | 建 `go_db` 与 `rag_db` |
| `conf/go/*.yaml` | **冻结 Go 仓库的副本**，运行时用 `-f` 指过去；真源一个字节都不动 |
| **`check-wave-lane-ownership.py`** | 🆕 **波次工作树写权守门（G1~G4）**（2026-10-27 01:44 重整）—— 4 棵工作树并行推进（`align/g1-users-follow` / `align/g2-message-like-task` / `align/g3-article-comment-favorite-hot` / `align/g4-service-common`）⇒ 本脚本断言：① **G1~G4 白名单两两不重叠**（glob 展开成实际文件集合后求交集 ⇒ 目录级写法也抓得到）② 13 个**共享文件**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `_cross/**` / `docs/Worktree.md` / 本 README）**无人拥有** ③ **`favorite` 只归 G3** ④ **共享层（`service/common` + `users-common`）只归 G4** ⑤ **每条白名单 pattern 必须命中真实文件**（路径写错 ⇒ 守门对这一面完全失明，实测踩到 `service/users/user/src/**`）⑥ **每条 lane 的分支必须真实存在**（盯一个已删分支 ⇒ 恒绿假绿）。⇒ `EXIT=0` 才算合规；已做 6 条变异（抢 favorite / 抢共享层 / 抢 user-api / 抢 users-common / 路径写错 / lane 不存在）**全部转红且红在目标断言上** |
| **`check-wave-role-model.py`** | 🆕 **G4 角色模型守门**（2026-10-27 02:23 Jason 订正后建）—— 原文档把 G4 写成「第 4 波、**必须最后**」⇒ 配套「落码窗口只在 G4 波激活」+「G1~G3 期间改共享层 ⇒ **那波结论作废**」；**订正后 G4 = 常设的共享层执行者**（随时接收 G1/G2/G3 反馈单 ⇒ 执行落码 ⇒ 全量 2411 ⇒ 回执），**不在域波顺序里**（域波顺序仍是 G1→G2→G3），且「基线不可变 / 结论作废」**被撤销**（改为：收口回归在波末跑 ⇒ 唯一窗口 = 收口回归后～合并前 ⇒ 变了就重跑一次收口回归）⇒ 本脚本把**角色模型**变成可复算断言：A0 权威文档存在 · A1 新模型标记传播到 6 份文档 · A2 `AGENTS.md` **§5.3e / §5.3d 两块**的措辞 · A3 域波顺序里**不得有 G4** · A4 规则表 **C2/C7** 在位 · A5 **退休措辞不得作为断言回流**（只允许出现在「引用并纠正」的行）· A6 `Worktree.md` 顺序行 · A7 4 份域台账的**规范锚点**。⇒ `EXIT=0` 才算合规；**7 条变异全部转红且红在目标断言上**（已做 `--mutation-test`）。⚠️ **粒度教训**：断言必须**段落块级 / 规范锚点级** —— 第一版用「文件含 `常设` 就算过」⇒ 变异改掉锚点行后别处仍有该词 ⇒ **守门恒绿（假绿）** ⇒ 现改为块级定位 + 规范锚点 |
| **`check-global-lane-ownership.py`** | 🆕 **多 lane 写权守门** —— `global` 域拆成多条 lane 并行（协议见 `docs/DRIFT/global/lanes/_PROTOCOL.md`）⇒ 本脚本断言：① **在跑**的 lane 的**白名单两两不重叠**（glob 展开成实际文件集合后求交集 ⇒ 目录级写法也抓得到）② 10 个**共享文件**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / 4 个规范台账 / 本 README）**无人拥有**（归合并收口执行者统一更新）③ **`favorite` 域零写权**。⇒ `EXIT=0` 才算合规；已做 4 条变异（抢文件 / 占用共享文件 / 碰 favorite / 同名脚本）**全部转红且红在目标断言上**。⚠️ **2026-10-27 01:44 退役**：并发形态改为 **G1~G4 四棵波次工作树** ⇒ 本守门退回为 **`global` 域内历史 lane（L1~L15）的归档**（`ACTIVE` 档现为空 ⇒ 断言 1 空跑）；**并发不变量的守门已迁到 `check-wave-lane-ownership.py`**。⚠️ **分「在跑 / 已合回」两档**（2026-10-26 补）—— **已合回的 lane 不再是并发方** ⇒ 它们与在跑 lane 的重叠**不算违规**（否则守门恒红 ⇒ 恒红的守门等于没有守门）|
| **`ddl-parity.sh`** | 🆕 **五域 DDL 双侧活体逐列对照编排**（第二十四轮 L3 lane）—— 为 `check-ddl-static.py` 的新增域预设（`article` / `like` / `hot` / `follow` / `favorite`）准备**两侧真库**：Go 侧 `AutoMigrate` + RAG 侧 Flyway 迁移 ⇒ 再由 `check-ddl-static.py --domain <域> --indexes` 逐列比对（类型/长度 + 可空性 + DEFAULT + 索引/约束 + 触发器）⇒ ⚠️ **`hot` = N/A**（两侧都无数据库 ⇒ 脚本给独立退出码 **3**，不许用 `EXIT=0` 冒充「已对齐」）⇒ 逐条 ⇒ [`docs/DRIFT/global/lanes/L3-ddl.md`](../../docs/DRIFT/global/lanes/L3-ddl.md) |
| **`check-flyway-concurrent.sh`** | 🆕 **三进程并发同库共存验证**（第二十四轮 L4 lane）—— 真 PG 16 起**三个 rpc 进程**同时连同一个库 ⇒ 断言**各本账互不干扰**（独立 `spring.flyway.table` + advisory lock **按账本表名**隔离）⇒ 配套 `flyway-concurrent-boot.java`（启动器）+ `flyway-concurrent-verify.py`（账本/表断言）⇒ 结论矩阵 ⇒ [`docs/DRIFT/global/lanes/L4-flyway.md`](../../docs/DRIFT/global/lanes/L4-flyway.md) §8.4；⚠️ **`favorite-rpc` 不在其中**（缺 `spring.flyway.baseline-version: 0` ⇒ **起得来、首次查库才炸** ⇒ 只记账不修） |
| **`check-jar-executable.sh`** | 🆕 **产物可执行性守门**（第二十八轮 L15 lane）—— 解包每个模块的**真实产物**看 `META-INF/MANIFEST.MF` 有没有 `Main-Class` ⇒ 缺了 ⇒ `java -jar` 报「中没有主清单属性」⇒ **该模块从来没被打成过可部署产物**。⚠️ **2026-10-26 起枚举从 `*-api` 扩到 `*-api` + `*-rpc`（10 → 20 个模块）** ⇒ 此前**10 个 rpc 模块的产物可执行性从来没被任何守门查过**（⇒ 第二十七轮 L11 手工盘点时**漏报了 `hot-rpc`** ⇒ 教训 **PIT-GLOBAL-030**）。⚠️ **`service/common` 刻意不入枚举**（它是 **library** 模块，被 10 个微服务依赖 ⇒ repackage 会**污染下游依赖**） |
| **`check-grpc-runtime-classpath.py`** | 🆕 **devh client 运行时 classpath + 服务发现地址门禁**（2026-10-24 follow 域建 · 2026-10-26 L15 修）—— ① 用了 `grpc-client-spring-boot-starter` 的模块，`io.grpc:grpc-inprocess` **不得**是 `test` scope（Maven「最近者优先」⇒ 直接声明压过传递声明 ⇒ **运行时 classpath 上整个 artifact 消失**）② `discovery:` 地址**必须三斜杠**（devh 判 `uri.getPath()` 长度 > 1 ⇒ 两斜杠让 path 留空 ⇒ 启动期抛 `IllegalArgumentException` ⇒ **与 Nacos 在不在线无关**）。⇒ **baseline 机制**（`baselines/grpc-runtime-classpath.txt` 记「已知未修」，只打印不失败）⇒ 🔴 **2026-10-26 修了一个存量恒红缺陷**：原实现 `os.walk(service/)` **没排除 `target/`** ⇒ 上一次构建的**旧副本**被当源码扫 ⇒ 同一违规算两遍 ⇒ 键永远匹配不上 ⇒ 门禁**恒红**（⇒ 教训 **PIT-GLOBAL-029**）⇒ 修后 `EXIT=0`，已知未修只剩 `favorite` 的 3 条（**零写权，保留**） |

| **`mutate-drift-move-verify.py`** | 🆕 **`drift-move.py verify` 的变异测试**（2026-10-30 · 合并收口执行者）—— `verify` 的判据 = 「**哨兵 ＋ 内容指纹 sha256**」，它**只锁「搬移块自登记以来没被改过」**（不证明与已流失的源快照逐字节相同 ⇒ 见 manifest 的 `_src_anchor.effective_snapshot_reproducible`）⇒ ⇒ **必须配变异测试证明它有牙**，否则就是再造一个恒绿假绿 ⇒ ⇒ 本脚本在**影子副本**上跑（`--root`，**真仓零改动**；git 历史面固定取自真仓）5 组：**① 基线绿 ② 改块内一行 ⇒ 必红**（报「指纹不符」）**③ 删哨兵 ⇒ 必红**（报「找不到标签哨兵」）**④ 换另一个文件改 ⇒ 仍必红**（证明判据没写死在一个文件上）**⑤ 块外追加 ⇒ 仍绿**（不误报）⇒ ⇒ **5/5 全过**才算数；⚠️ 「脚本崩溃」**不等于**「判据红」⇒ ⇒ 崩了单独判掉 |
| **`mutate-pointer-consistency.py`** | 🆕 **指针漂移守门的变异测试**（2026-10-30 · 合并收口执行者）—— `tools/ledger-derive/check-pointer-consistency.py` 是**棘轮**守门（只对「新增漂移」报红）⇒ ⇒ 它天然有「永远绿」的风险（判据若根本没在测漂移，它也会一直绿）⇒ ⇒ 本脚本在**影子副本**上往域台账**插一行** ⇒ ⇒ 必须立刻红，且红的原因是「🆕 新增例外」⇒ ⇒ 另测「影子副本未改动 ⇒ 仍绿（无副作用）」⇒ ⇒ 说明见 [`docs/DRIFT/_cross/pointer-drift.md`](../../docs/DRIFT/_cross/pointer-drift.md) |
| **`check-datasource-password-form.py`** | 🆕 **rpc 模块口令占位符形态守门**（2026-10-30 · 合并收口执行者）—— Go 侧**全部** rpc yaml 的口令占位符是**裸** `${POSTGRES_PASSWORD}`，且 go-zero 用 `os.ExpandEnv`（**不认 `:default` 语法** ⇒ 实测带默认值写法展开成**空串** ⇒ 缺变量即 fail-fast）⇒ ⇒ RAG 的 `${POSTGRES_PASSWORD:password}` = **比 Go 松** = 真漂移 ⇒ ⇒ 本脚本拦「rpc 模块的运行配置里出现带默认值的口令占位符」（**一次覆盖全仓 10 个 rpc 模块**）⇒ ⇒ **只管形态不管明文** ⇒ ⇒ 与 `check-no-prod-secrets.py` **互补不重叠**；内置变异自测 `--mutation-test`（6/6）含「扫 0 份 ⇒ 退出码 2（无法判定，不是绿）」；白名单 = `conf/datasource-password-whitelist.txt`（**豁免不是免检**：新违例一律红） |
| **`check-rpc-common-scan-exclusion.py`** | 🆕 **rpc 域「不扫 `com.rag.common`」+「servlet 形态必须配 `rag.side: rpc`」跨域静态守门**（2026-10-30 · 合并收口执行者 · 接 G4 第九次订正 `6a7b1a2b` 的连带缺口）—— 共享层 13 个 api 侧 web Bean 分两个**装配面**：**自动配置面 3 个**（`GoStylePathNormalizingFilter` · `GoStyleJsonContentTypeAdvice` · `GoStyleRouteTemplateResolver` ⇒ 走 `AutoConfiguration.imports` ⇒ **不靠扫描** ⇒ **rpc 域只要是 servlet 形态就会注册**）＋ **扫描面 10 个**（`@Component` ⇒ **靠组件扫描** ⇒ 域侧不扫 `com.rag.common` ⇒ 本来就不注册）⇒ ⇒ ⚠️ **2026-10-30 二次订正**：`@ConditionalOnApiSide` 已**逐个核实挂满 13 个类** ⇒ ⇒ 「扫描面那 10 个 `rag.side=rpc` 救不了」**已过期** ⇒ ⇒ 扫描面现为**双保险**（域侧不扫 ＋ 门）⇒ ⇒ **形态面加一行 `rag.side: rpc` ⇒ 一次封掉全部 13 个**；⇒ ⇒ 本脚本两条断言：① **结构面** = 任何 rpc 域的 `@SpringBootApplication` 不得带 `scanBasePackages` 含 `com.rag.common`（**剥注释后**读真注解 ⇒ javadoc 里写「不能用 `scanBasePackages`」不算违规）② **形态面** = servlet 形态（**有 `spring-boot-starter-web` 且没有 `web-application-type: none`** —— 两个条件都要；⚠️ **2026-10-30 第三次订正**：web starter 必须在**剥掉 XML 注释**后的 pom 里命中（**pom 侧对称于 Java 侧的 `strip_comments()`**）⇒ ⇒ 否则 pom **注释**里那句「本模块不引 `spring-boot-starter-web`」会被当成**真依赖** ⇒ ⇒ 后果三重：**假违例 + 假豁免 + 派错工单**（本次 = `user-rpc`/`like-rpc`/`message-rpc`/`task-rpc` 四个域被误判 ⇒ ⇒ 形态面违例实际只有 **follow-rpc** 1 个）⇒ ⇒ 守门另带**自带自检**（注释里的假依赖若被判成 servlet 形态 ⇒ ⇒ 直接报红）⇒ 必须显式 `rag.side: rpc`（**平铺与嵌套两种 YAML 写法都认**）⇒ ⇒ 为何是真偏离：Go 的 rpc 侧全部 `zrpc.MustNewServer` ⇒ **一个 rest 中间件都没挂** ⇒ ⚠️ **会「拒请求」的两个**（2026-10-30 二次订正 · G1 逐类核过）= `GoStyleCpuSheddingFilter`（CPU 超阈**拒抓取请求** ⇒ 直接威胁载体的目的）＋ `GoStyleMaxConnsFilter`（连接数超限 ⇒ 同样拒请求）⇒ 另 `GoStyleTomcatParameterLimitCustomizer` 是**容器级**（**非请求路径组件 ⇒ 不拒请求**）但管着 `DRIFT-HOTAPI-030` 那条轴 ⇒ **两类风险分开说**；与 G3 的**域内运行期**守门（`*RpcSharedLayerWebBeanGateTest`）**互补不重叠**（静态 = 结构面 · 运行期 = 容器面）；内置变异自测 `--mutation-test`（**3/3 精确命中**：加 `scanBasePackages` ⇒ 结构面红 · 写 `rag.side: api` ⇒ 形态面红 · 把 `web-application-type: none` 改成 servlet ⇒ 形态面红（且该域不在白名单 ⇒ 测的是真分支不是豁免分支））；白名单 = `conf/rpc-common-scan-whitelist.txt`（**豁免不是免检**：新违例一律红 · 条目已无命中会报「可剪枝」）⇒ ⚠️ **影子副本 = HEAD ＋ 工作区增量**（纯 `git archive HEAD` 会漏掉新增的白名单文件 ⇒ 变异会**因错误的原因红** ⇒ 得出「判据有效」的假结论） |
### `exp-*` 实验脚本（**各自带完整的设计说明在脚本头部**）

> ⚠️ `exp-drift015-flyway-flags.sh` 是**被证伪方案**的实证记录，**不要当修复用**（脚本头部已写明）。
> ⚠️ 这些脚本**自建容器/端口**，不依赖也不干扰别的会话的共享栈。

| 脚本 | 回答什么问题 |
|---|---|
| `exp-b1-timeout-cancel.sh` | 超时与客户端取消：两侧是否都在各自的 `http-timeout` 附近返回 |
| `exp-difftest-concurrent-schema.sh` | `UNRESOLVED-USERRPC-005`：`users_user` 建表责任交叉的**双侧对称**实验 |
| `exp-difftest-grpc-admin-errors.sh` | admin-rpc 的 **gRPC 层**双侧活体对拍编排 |
| `exp-difftest-grpc-errors-message.sh` | message 域 Go ↔ RAG **双端口**对拍编排 |
| `exp-difftest-grpc-errors.sh` | §6.2.6 E-2 动态半的编排脚本 |
| `exp-difftest-grpc-user9.sh` | `UNRESOLVED-USERRPC-007`：9 个 RPC 的 gRPC 层错误 message 逐字对拍 |
| `exp-difftest-redis-blacklist.sh` | user-api 侧 `U-004` 的**真 Redis 故障注入**（正常 / 断连 / 很慢 三形态 × 双侧并行探测 + 「被拉黑 token」对照组）|
| `exp-difftest-redis-failfast.sh` | `UNRESOLVED-USERRPC-004②` 的真故障注入实验（启动期 Redis 不可用）|
| **`exp-difftest-redis-neverup.sh`** | **「Redis 从未连上过」的双侧对拍**：两种「连不上」的形态（`REDIS_HOST=127.0.0.1` 端口拒连 / `REDIS_HOST=redis` 主机名解析失败 = 生产默认值）× 3 轮（首个请求 ≠ 稳态）|
| **`exp-difftest-redis-neverup-rpc.sh`** | **「Redis 从未连上过」的 rpc 侧对拍**（`DRIFT-USERAPI-015` 的 rpc 侧对应面 ⇒ **实测无漂移**）：**直连 gRPC** 打 `Logout`（经 api 会被 3s HTTP 预算遮蔽）× **三形态**（`REDIS_HOST=127.0.0.1` 拒连 / `REDIS_HOST=redis` 解析失败 / `REDIS_MODE=hanging` **连上不回包 ⇒ 命令超时路径**）。客户端 = `tools/difftest/redis-logout-rpc/go`（`replace` 引真源 ⇒ 用**真源真实 stub**）|
| `exp-drift015-flyway-flags.sh` | 🔴 **被证伪方案**的实证记录，**不要当修复用** |
| `exp-go-concurrent-automigrate.sh` | Go 侧并发 `AutoMigrate` 的行为 |

> **纪律**：Go 侧绝不写任何文件进冻结仓库。真源 yaml 的 `Log.Path: ../../../../log/...`
> 是相对 `etc/` 的，会直接落进仓库里，所以副本已改成 `Mode: console`。