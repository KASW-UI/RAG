# W20 · 同源判定（11 条）

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `DRIFT-MSG-V-030` | `like` | 同一件事 | like 这段开篇自述「**登记方在 message 域** … 修法落在本模块自己的 `pom.xml`」⇒ 讲的正是本体那条「6 个 api 模块的 jar 根本不可执行」在 like-api 的**域内那半**（补 `<build>` + `spring-boot-maven-plugin` ⇒ 68 MB 可执行 fat jar）⇒ 同一件事，但**只覆盖域内那半**（本体的另外 5 域不在本段内） | （未派工 ⇒ 无本体）· 本体在 message 域 = `DRIFT/message/rpc.md:2513`／`:2774` |
| `FB-04` | `message` | 同一件事 | message 这段是「FB-04 对本域 = N/A」的回执（commit `853767b`），讲的正是 global 那张单（A9：gRPC handler 执行期拿不到当前 server span ⇒ `onHalfClose` 里让 `makeCurrent()` 的 scope 包住 handler 执行期）⇒ 同一张单，但本段**只覆盖「message 域不受影响」这一面**（两侧 `SpanFromContext` ／ `Span.current()` 各 0 命中） | （未派工 ⇒ 无本体）· 本体 = global `DRIFT/global/vs-common.md:910`（派单出处 = like 域 `DRIFT/like/vs-common.md:94`） |
| `D-4` | `message` | 不同的事 | message 的 D-4 讲「删除 **message-rpc** 的域侧 MyBatis-Plus 排序桥（须等 G4 共享层桥落码）」，而同号在 like 域讲的是「like-api 仍用 `RANDOM_PORT`」⇒ 现象与落点都不同 ⇒ 纯撞号（同一「排序桥删除」模板在 like ／ task 域登记为 **D-6**） | （未派工 ⇒ 无本体）· 同号对照 = like 域 `DRIFT/like/vs-common.md:70` |
| `FB-01` | `message` | 同一件事 | message 这张单与 global 本体逐条同形（Go `patrouter.go:48`／`:60` 的 `path.Clean` 归一 ⇒ 共享层 `GoStylePathNormalizingFilter` 缺位 ⇒ 其余 9 个 api 受影响），且本段自述「与 like 域 `D-3` ／ task 域 `C-2` 是同一条」⇒ 同一张单，本段**只覆盖 message 域的现状与取证** | `DRIFT/global/vs-common.md:352` |
| `D-2` | `like` | 不同的事 | like 的 D-2 讲「A9 like 域 span 属性全缺（8 处落点 + 13 个属性名）」，同号在 message 域讲「A5 测试端口段 3 处 `RANDOM_PORT`」、在 task 域讲「`TaskProtectionChainWiringTest` 覆盖面」⇒ 三个现象互不相干 ⇒ 撞号 | （未派工 ⇒ 无本体）· 同号对照 = message `DRIFT/message/vs-common.md:70` ／ task `DRIFT/task/vs-common.md:62` |
| `D-6` | `like` | 不同的事 | like 的 D-6 讲「删除 **like-rpc** 的域侧排序桥」，同号在 task 域讲「删除 **task-rpc** 的域侧排序桥」⇒ 同一模板但落点各属本域（删除爆炸半径 like 3 件：桥类 + `AutoConfiguration.imports` 注册行 + `LikeRpcSpringContextWiringTest` 断言文本；task 2 件）⇒ 各自独立可执行、状态不可互相继承 ⇒ 保持拆开 | （未派工 ⇒ 无本体）· 同号对照 = task 域 `DRIFT/task/vs-common.md:66` |
| `D-7` | `like` | 不同的事 | like 的 D-7 讲「like-api 下游 gRPC status → 业务码映射与 Go 不同（INTERNAL：Go 13 vs RAG 1015；熔断打开：Go 5001 vs RAG 1015）」，同号在 task 域讲「A5 测试端口段 2 处 `RANDOM_PORT`」⇒ 现象与落点都不同 ⇒ 撞号 | （未派工 ⇒ 无本体）· 同号对照 = task 域 `DRIFT/task/vs-common.md:68` |
| `FB-03` | `like` | 同一件事 | like 这段是「合并 G4 共享层 + FB-03 域侧守门升级」，讲的正是 global 本体那张单（A2 保护链「链构成」只读入口 `compose(props)` ／ `layerNames(chain)`）⇒ 同一张单，本段**只覆盖域侧消费与验收那半**（`LikeProtectionChainWiringTest` 2 ⇒ 4 条 ⇒ 本域 A2 轴 L1 ⇒ L2） | `DRIFT/global/vs-common.md:402` |
| `FB-05` | `like` | 不同的事 | like 域的 FB-05 讲「A1 Redis 调用不感知 deadline 在 like 域成立（Go 侧 20 处带 `l.ctx` vs RAG 侧 11 处无 ctx）」，而同号在 global 本体讲「`mode: off` 在 YAML 里被解析成布尔 false（`c15dfeb` 已落码）」⇒ 现象与落点都不同 ⇒ 撞号；**注意** like 这条实为 global **E66**（`DRIFT/global/vs-common.md:31`）的域内附单 ⇒ 若要与 E66 同源应走 E66 号 | （未派工 ⇒ 无本体）· 同号本体 = global `DRIFT/global/vs-common.md:1014` |
| `FB-01` | `like` | 同一件事 | like 这张单与 global 本体逐条同形（同为 A3 路径归一缺位的派单：Go `patrouter.go:48`／`:60` ⇒ 共享层 `GoStylePathNormalizingFilter` 缺位 ⇒ 9 个 api 缺 Go 的 `path.Clean` 归一），且本段自述「与 message 域 `FB-01` ／ task 域 `C-2` 是同一条」⇒ 同一张单，本段**只覆盖 like 域的现状与取证** | `DRIFT/global/vs-common.md:352` |
| `UNRESOLVED-005` | `message` | 不同的事 | message 的 UNRESOLVED-005 问「`extra` 落库后的**键序**是否影响客户端（Go `encoding/json` 按 key 排序 vs RAG `LinkedHashMap` 包 proto map）」，同号在 article 域问「405→404 空 body 是否仍无 `Content-Type`」、在 users 域问「user-rpc 的事务/并发语义」⇒ 三者互不相干 ⇒ 撞号 | （未派工 ⇒ 无本体）· 同号对照 = article `DRIFT/article/api.md:1068` ／ users `DRIFT/users/user/api.md:2104` |

## 口径说明（3 条）

1. **本体怎么定的**：`docs/DRIFT/_cross/grading/_lanes.json`（第 1／2 轮派工）＋ `/tmp/rag-verify/lane-briefs/round3-body.json`（canonical body 映射）⇒ 本 lane 11 条里 **4 条有本体**（`FB-01`×2 → global:352 · `FB-03` → global:402）· **7 条「未派工 ⇒ 无本体」**；对这 7 条，改以**同号在别域的登记**作对照（对照点已逐条写进「本体位置」列）。
2. **只读台账、不读 Go**：本轮判据是**台账文本本身讲的是什么**（落点 ／ 组件 ／ 现象）⇒ 全部对照基于 `docs/DRIFT/**` 正文，未打开 Go 真源。
3. **合并时的覆盖边界**：判「同一件事」的三条只覆盖**域内那一半**（`DRIFT-MSG-V-030` = 域内补记 · `FB-04` = 域内适用性回执 · `FB-03` = 域侧守门升级）⇒ 合并成一条时**不得**拿域内那半的状态覆盖本体整条，否则会反向制造假缺口。
