# W16 · 同源判定（20 条）

> **本 lane 的 20 条全部落在 `common` 域、且全部在 `DRIFT/common/alignment-2026-10-23.md` 一个文件里。**
> 派工清单（`_lanes.json`，第 1/2 轮共 184 条）**没有一条**含这 20 个编号
> ⇒ 按任务书口径「未派工 ⇒ 无本体」⇒ 本体列如实写「无本体」，
> 但**实质的「本体」（同一件事的另一份登记）在同一个文件里、只是编号写法不同** ⇒ 已逐条找到并写进「本体位置」列。
>
> ⚠️ **判定口径**：本文件里同一件事被登记了两次 —— §五／§十四·一 用长编号 `DRIFT-CF-*` / `DRIFT-LOG-*` / `DRIFT-SF-*`，
> §十三·一 用短编号 `CF-*` / `LOG-*` / `SF-*`。**台账自己声明了这层对应关系**：
> §十四·一 的「11 字段所在」列把每条 `DRIFT-CF-00x` 直接指向「§十三·一 CF-00x」
> ⇒ 两段讲的是**同一落点 / 同一组件 / 同一现象** ⇒ 判「同一件事」（**不是**跨域撞号）。
>
> ⚠️ **派生层的实际代价（已实测，不是推断）**：同一件事的两份登记**状态不一致** ⇒ 派生层产出两条 ⇒ 虚增缺口。
> 例：`DRIFT-CF-001`（`:375`）= 🔴 未闭合，而 `CF-001`（`:834`）= 🟡 部分闭合（`docs/DRIFT/_cross/stage1-derivation.tsv` 实测）。
> 更极端的三条：`CF-011` / `LOG-004` / `CF-003` 这几行**在派生层 TSV 里根本没有被登记**（grep 命中 0），
> ⇒ 只剩 §五 那份 🔴 ⇒ §十三·一 判的「D 类 ⇒ 经分析确认不实现」对派生层**不可见** ⇒ 假缺口成立。

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `CF-001` | `common` | 同一件事 | 与同文件 `:375` 的 `DRIFT-CF-001` 是**同一处**：同一 Go 出处 `logging.go:43-49`／`comment/rpc/cmd/fact-authority/main.go:29`、同一 RAG 落点 `ProcessLogger.java:150-151`（工厂内 `meta.validate()`）、同一现象（Go 先建 logger 后校验 ⇒ 空值落 `unresolved` 信封；RAG 抛 `IllegalArgumentException` 且域侧静默降级）；台账 §十四·一 `:962` 自己把 `DRIFT-CF-001` 的「11 字段所在」指到「§十三·一 CF-001」⇒ 同一件事的两种编号写法 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:375` |
| `CF-002` | `common` | 同一件事 | 与同文件 `:376` 的 `DRIFT-CF-002` 是**同一处**：同一 Go 出处 `comment/rpc/cmd/fact-authority/main.go:29`＋`:71`、同一 RAG 落点 `AuthorityHandlerFactory.java:44`→`AuthorityHandler.java:95-97`（§五 那份写 `:48`＝同一调用点，行号漂移）、同一现象（authority handler 拿到的是伪造 logger ⇒ 6 个 metadata 键无保证）；§十四·一 `:963` 明写「11 字段所在 = §十三·一 CF-002」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:376` |
| `CF-005` | `common` | 同一件事 | 与同文件 `:377` 的 `DRIFT-CF-005` 是**同一处**：同一 Go 出处 `process.go:28-30`＋`wire.go:97`、同一 RAG 落点 `Dispatcher.java` 的单线程池 `:279`＋`future.get(10s)` `:315`＋`cancel(true)` `:320`、同一现象（10s 超时只发线程中断 ⇒ 一次不可中断的 JDBC 挂死让投递链路永久停滞）；§十四·一 `:966` 明写「11 字段所在 = §十三·一 CF-005」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:377` |
| `CF-006` | `common` | 同一件事 | 与同文件 `:378` 的 `DRIFT-CF-006` 是**同一处**：同一 Go 出处 `process.go:41`（ticker 创建即起算）、同一 RAG 落点 `Dispatcher.java:399-404`（`stopSignal.get(1s)` ⇒ 每批后再等满 1s）、同一现象（投递节奏 = 绝对 1s 栅格 vs 批次耗时＋1s）；§十四·一 `:967` 明写「11 字段所在 = §十三·一 CF-006」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:378` |
| `CF-008` | `common` | 同一件事 | 与同文件 `:379` 的 `DRIFT-CF-008` 是**同一处**：同一 Go 出处 `authority_http.go:30-37`＋`logging.go:75-81`、同一 RAG 落点 `AuthorityHandler.java:128-132`（建 span 从不 `makeCurrent()`）、同一现象（authority 三条日志通常无 `trace_id`）；§十四·一 `:968` 明写「11 字段所在 = §十三·一 CF-008」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:379` |
| `CF-012` | `common` | 同一件事 | 与同文件 `:388` 的 `DRIFT-CF-012` 是**同一处**：同一 Go 出处 `logging.go:53`（纳秒）、同一 RAG 落点 `ProcessLogger.java:85-98`＋`:253`（毫秒）、同一现象（时间戳精度 纳秒 vs 毫秒）；§十四·一 `:971` 明写「11 字段所在 = §十三·一 CF-012」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:388` |
| `CF-013` | `common` | 同一件事 | 与同文件 `:389` 的 `DRIFT-CF-013` 是**同一处**：同一 Go 出处 `wire.go:87`＋`authority_http.go:24`（`len()` = UTF-8 字节数）、同一 RAG 落点 `Wire.java:191`＋`AuthorityHandler.java:87`（`String.length()` = UTF-16 码元）、同一现象（token 长度阈值口径不同 ⇒ RAG 更严）；§十四·一 `:972` 明写「11 字段所在 = §十三·一 CF-013」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:389` |
| `CF-014` | `common` | 同一件事 | 与同文件 `:390` 的 `DRIFT-CF-014` 是**同一处**：同一 Go 出处 `wire.go:84-89`（畸形 endpoint 归一为统一文案）、同一 RAG 落点 `Wire.java:194`（`URI.create` 直接抛 `IllegalArgumentException`）、同一现象（畸形 endpoint 的错误形态＋文案不同）；§十四·一 `:973` 明写「11 字段所在 = §十三·一 CF-014」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:390` |
| `CF-015` | `common` | 同一件事 | 与同文件 `:391` 的 `DRIFT-CF-015` 是**同一处**：同一 Go 出处 `wire.go:116`（固定前缀 `decode DataCenter event receipt: `）、同一 RAG 落点 `Wire.java:240`（抛 Jackson 原始异常）、同一现象（receipt 解码错误少一层前缀）；§十四·一 `:974` 明写「11 字段所在 = §十三·一 CF-015」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:391` |
| `CF-017` | `common` | 同一件事 | 与同文件 `:392` 的 `DRIFT-CF-017` 是**同一处**：同一 Go 出处 `logging.go:104-135`（`InstallFrameworkLogger`＋键改名）、同一 RAG 落点 `ProcessLogger.java:46-47`＋`:251`（`log_source` 恒为 `"application"`）、同一现象（框架日志未并入 OBS-r3 信封）；§十四·一 `:975` 明写「11 字段所在 = §十三·一 CF-017」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:392` |
| `LOG-001` | `common` | 同一件事 | 与同文件 `:413` 的 `DRIFT-LOG-001` 是**同一处**：同一 Go 出处 `logger.go:148-157`＋`:168`（KV 进日志行）、同一 RAG 落点 `Logger.java:263-281`／`Logger.java:284-289`（KV 挂在 `ILoggingEvent.getKeyValuePairs()` 上）＋全仓无 `logback-spring.xml`、同一现象（数据进了 logback 事件却生产 stdout 渲染不出来）；§十四·一 `:977` 明写「11 字段所在 = §十三·一 LOG-001」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:413` |
| `LOG-002` | `common` | 同一件事 | 与同文件 `:414` 的 `DRIFT-LOG-002` 是**同一处**：同一 Go 出处 `logger.go:139`＋`:320-323`（`runtime.Callers` skip=3，尾部恒有包装帧）、同一 RAG 落点 `Logger.java:263`＋`:499-520`（`StackWalker`）＋`:148-149`（`FILTER_PREFIXES` 滤掉 logger 自身帧；§五 那份写 `:148`＋`:494-509`，同一处）、同一现象（`call_chain` 尾部少一帧）；§十四·一 `:978` 明写「11 字段所在 = §十三·一 LOG-002」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:414` |
| `LOG-005` | `common` | 同一件事 | 与同文件 `:417` 的 `DRIFT-LOG-005` 是**同一处**：同一 Go 出处 go-zero `core/logx/richlogger.go:210`／`:217-220`／`:222-225`（自动附加 `caller`／`trace`／`span`）、同一 RAG 落点 `Logger.java:274-281`（`doLogBusinessErr` 字段集无 `caller` 无 `span`）、同一现象（可观测性字段净损失 = `caller` ＋ `span`）；§十四·一 `:981` 明写「11 字段所在 = §十三·一 LOG-005」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:417` |
| `LOG-006` | `common` | 同一件事 | 与同文件 `:418` 的 `DRIFT-LOG-006` 是**同一处**：同一 Go 出处 `service/user/user/rpc/etc/user.yaml:6-7`（`Mode: file` 落盘＋滚动）／同款 `service/like/rpc/etc/like.yaml:8-11`、同一 RAG 落点 `service/like/like-rpc/src/main/resources/application.yml:332`（`logging:` 块只有 `level:`）＋无 `logback-spring.xml`、同一现象（Go 落盘滚动 vs RAG 只写 stdout）；§十四·一 `:982` 明写「11 字段所在 = §十三·一 LOG-006」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:418` |
| `LOG-007` | `common` | 同一件事 | 与同文件 `:419` 的 `DRIFT-LOG-007` 是**同一处**：同一 Go 出处 `logger.go:118-125`（恒取 `Error()` ⇒ 空 message 得 `""`）、同一 RAG 落点 `Logger.java:243`＋`:398`（`getMessage()==null` 时退 `toString()`）、同一现象（`error_reason` 在 message 为 null 的异常上退化成类全名）；§十四·一 `:983` 明写「11 字段所在 = §十三·一 LOG-007」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:419` |
| `SF-002` | `common` | 同一件事 | 与同文件 `:421` 的 `DRIFT-SF-002` 是**同一处**：同一 Go 出处 `snowflake.go:53-59`＋`:62-84`（workerId 只有 env ＋ hash 两档）、同一 RAG 落点 `SnowflakeBean.java:59-78`＋`SnowflakeProperties.java:26-27`＋`ArticleApiApplication.java:81-84`（多出 `snowflake.worker-id` 一档且在 article-api 里是活的）、同一现象（workerId 选取多一档 = 表达层扩展）；§十四·一 `:984` 明写「11 字段所在 = §十三·一 SF-002」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:421` |
| `SF-003` | `common` | 同一件事 | 与同文件 `:422` 的 `DRIFT-SF-003` 是**同一处**：同一 Go 出处 `snowflake.go:22-42`（懒加载于首次 `GetID`）＋`:56`（`strconv.Atoi` 不 trim）、同一 RAG 落点 `SnowflakeBean.java:40-43`（refresh 期就 `setWorkerId`）＋`Snowflake.java:389`（`trim()`）、同一现象（初始化时机不同 ＋ `SNOWFLAKE_NODE_ID` 空白容错相反）；§十四·一 `:985` 明写「11 字段所在 = §十三·一 SF-003」⇒ 同一件事 | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:422` |
| `DRIFT-CF-009` | `common` | 同一件事 | 与同文件 `:841` 的 `CF-009`（§十三·一）是**同一处**：同一 Go 出处 `process.go:64-69`＋`wire.go:35`、同一 RAG 落点 `Dispatcher.java:531-545`（按类型判）／`:368-382`、同一现象（`deliveryErrorCode` Go 认哨兵实例、RAG 认类型）；且 `CF-009` 那行自己写「台账首版主张『`error_code` 取值范围更宽』**被证伪**」⇒ 它指的**就是** §五 `:386` 这条 ⇒ 同一件事（状态分叉：`:386` = 🔴 vs `:841` = ✅ 已一致 ⇒ 派生层两条并存 ⇒ 虚增一条假缺口） | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:841` |
| `DRIFT-CF-011` | `common` | 同一件事 | 与同文件 `:842` 的 `CF-011`（§十三·一）是**同一处**：同一 Go 出处 `logging.go:50-58`／`:61-65`／`:78`、同一 RAG 落点 `ProcessLogger.java:246-269`、同一现象（OBS-r3 信封 JSON 键序与 Go 相反 = metadata 块前移 ＋ trace 块前移到 KV 之前）；`CF-011` 那行给的 D 类理由「JSON 键序无语义 ＋ Go 侧无顺序断言」正是对这条的定性 ⇒ 同一件事（⚠️ `:842` 这行**未进派生层** ⇒ 只剩 `:387` 的 🔴 ⇒ 假缺口） | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:842` |
| `DRIFT-LOG-004` | `common` | 同一件事 | 与同文件 `:857` 的 `LOG-004`（§十三·一）是**同一处**：同一 Go 出处 `logger.go:63-70`（`sync.Once` 失败即毒化）＋`sync/once.go:50-51,77`、同一 RAG 落点 `Logger.java:171-181`（失败不置位 ⇒ 可重试成功）、同一现象（`Init` 失败后可恢复性相反）；§十四·一 `:980` 明写「`DRIFT-LOG-004` 的 11 字段所在 = §十三·一 LOG-004」⇒ 同一件事（⚠️ `:857` 这行**未进派生层** ⇒ 只剩 `:416` 的 🔴 ⇒ 假缺口） | （未派工 ⇒ 无本体）· 别名体 `DRIFT/common/alignment-2026-10-23.md:857` |

## 收口提示（供主线判定用，本 lane 不越界改任何文件）

1. **这 20 条的「同源」不是跨域同源，而是同文件内的编号别名。**
   `tools/ledger-derive/identity-groups.txt` 的合并键是**编号**（`<编号> TAB <域1>,<域2>`），
   而本 lane 的 20 条每条都**只出现在 `common` 一个域** ⇒ 即使全判「同一件事」，
   `gen-identity-groups.py` 算出的 `len(same) == 1` ⇒ **不会生成任何同源组** ⇒ 派生层不会因此自动合并。
   ⇒ **要真正消掉这批重复，需要的是「编号别名」机制（长短编号归一）或台账侧统一编号**，不是同源组。
2. **现状是真的有代价（实测，非推断）**：同一件事的两份登记状态不一致 ⇒ 派生层两条并存 ⇒ 虚增缺口。
   `docs/DRIFT/_cross/stage1-derivation.tsv` 里 `DRIFT-CF-001` = 🔴 未闭合（`:375`）、`CF-001` = 🟡 部分闭合（`:834`），
   同族 17 条皆然；`DRIFT-CF-009`（🔴）与 `CF-009`（✅ 已一致）并列；`CF-003`／`CF-011`／`LOG-004` 三行**未进派生层** ⇒ 只剩 🔴 那份。
3. **同源组的价值在别处**：本 lane 的判定不产生同源组，但 **W15 那条 lane 判的正是这 20 条的别名体**
   （`DRIFT-CF-001` 等长编号）⇒ 两边若都判「同一件事」，结论自洽（同一件事被登记两次），
   但仍需靠第 1 条的「编号别名归一」才能在派生层落地。
4. **本 lane 未写任何台账、未跑任何写盘脚本、未 `git add`／`git commit`**；只产出本文件。
