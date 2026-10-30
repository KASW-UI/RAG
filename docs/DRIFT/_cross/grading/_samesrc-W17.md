# W17 · 同源判定（15 条）

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `E65` | `favorite` | 同一件事 | favorite 这条讲「出向 gRPC 调用不继承请求 deadline」（落点 `service/common` 的 `GoStyleRequestDeadline.applyTo` ＋ `HttpMiddleware.begin`），与 global 域 `E65` 同现象同落点，且 `favorite/rpc.md` §37 自述来源＝global 第四十六轮新登记 ⇒ 属同一件事，合并为一条不重复派单 | `DRIFT/global/_README.md:430`（global 域本体）＋ `DRIFT/global/vs-common.md:30`（共享层同条表行） |
| `PIT-FAV-RPC-036` | `favorite` | 同一件事 | 三处来源讲的是同一个坑「一条断言一个测试方法 ＋ 跨域回执给的量化口径也要核 ＋ 理由也是结论的一部分」，编号自带域前缀 `FAV` 且全仓无域外同号 ⇒ DRIFT 侧 §36.7 与 §37.7 两处只是引用坑档本体 | `Pitfalls/favorite/rpc.md:1778`（坑档本体，新号 `PIT-FAV-57`） |
| `F-1` | `favorite` | 不同的事 | favorite 这条讲「`U7` rpc 层整域双侧活体对拍未做」（落点 favorite-rpc 探针与 rpc.md §9.3），article 那条讲「`DRIFT-ARTICLE-API-001` 上传失败恒 400」（落点 article-api）⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）；article 同号条目 `DRIFT/article/api.md:1288` |
| `F-2` | `favorite` | 不同的事 | favorite 这条讲「§5 交付物清单第 0 步：组件真的启动一次」未完成（`tools/ddl-parity-probe/favorite` 全程不启 Java 进程），article 那条讲「`DRIFT-ARTICLE-API-002` `user-rpc` 超时 2s vs 70s」⇒ 现象与落点都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）；article 同号条目 `DRIFT/article/api.md:1290` |
| `F-3` | `favorite` | 不同的事 | favorite 这条讲「`DRIFT-HOTAPI-030` 后续格：总『值』数超过 Go 的 10000 段上限，RAG 翻转点 10002 段 vs Go 10001 段」，article 那条讲「`DRIFT-ARTICLE-API-003` 重复 query key → 400」⇒ 现象与落点全不同 ⇒ 纯编号撞号，保持拆开 | `DRIFT/article/api.md:1291`（article 域 `F-3` · 非同一件事；favorite 这条的真身是 global 的 `CD-6`／`DRIFT-HOTAPI-030`，见 `DRIFT/global/_README.md:134`） |
| `F-4` | `favorite` | 不同的事 | favorite 这条讲「P0 #8 的共享面半边：根级 compose 的 `:?` ＋ 根 README 的 `.env.example`」，article 那条讲「`DRIFT-ARTICLE-API-004` 锁死错误答案的绿测试」⇒ 落点与现象都不同 ⇒ 纯编号撞号，保持拆开 | （未派工 ⇒ 无本体）；article 同号条目 `DRIFT/article/api.md:1292` |
| `G4-1` | `favorite` | 同一件事 | favorite 这条正文以「🔴 `E65`：出向 gRPC 不继承请求 deadline」开头，落点同为 `service/common` 的 `GoStyleRequestDeadline.applyTo` ＋ `HttpMiddleware.begin` ⇒ 与 global 域 `E65` 同一件事（global 台账已写明「不重复派单：已是 global 台账的 OPEN 项 ⇒ 重复派会造出两个编号指同一件事」）⇒ 合并为一条 | `DRIFT/global/_README.md:430`（global 域 `E65` 本体；同源同族＝comment 域 `G4-3`） |
| `G4-2` | `favorite` | 同一件事 | 两处讲的完全是同一件事：共享层「MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制，全仓无任何测试证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了执行链」，落点同为 `service/common/.../persistence/DeadlineQueryTimeoutInjector.java` ⇒ 同一件事，合并为一条 | `DRIFT/article/vs-common.md:275`（article 域本体；同源同族＝comment 域 `G4-1`） |
| `G4-5` | `favorite` | 不同的事 | favorite 这条讲「`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径 ⇒ favorite-rpc 出向调用无熔断」，article 那条讲「`AGENTS.md` §6.3 问题 4 的记载不完整（漏记 `article-rpc` 缺 `baseline-version`）」⇒ 落点与现象都不同 ⇒ 撞号，保持拆开 | `DRIFT/article/vs-common.md:278`（article 域 `G4-5` · 非同一件事；favorite 这条的真身是 article 域 `G4-6` ＋ comment 域 `G4-2`） |
| `G4-6` | `favorite` | 不同的事 | favorite 这条讲「本地 `~/.m2` 的 `service/common` jar 是 E62 修前旧版 ⇒ 单模块不带 `-am` 跑法伪装成 SQL deadline 漂移」，article 那条讲「`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径 ⇒ article-rpc 出向调用无熔断」⇒ 现象与落点都不同 ⇒ 撞号，保持拆开 | `DRIFT/article/vs-common.md:279`（article 域 `G4-6` · 非同一件事） |
| `L-4` | `favorite` | 不同的事 | favorite 这条讲「补 favorite 域自己的 otel 业务 span ＋ `biz.*` 属性」（落点 `favorite-api`／`favorite-rpc` 域内 Service），article 那条讲「A3 `rag.protection.*` 阈值 —— 无需改」⇒ 各自域流水号，现象与落点都不同 ⇒ 撞号，保持拆开 | （未派工 ⇒ 无本体）；article 同号条目 `DRIFT/article/vs-common.md:73` |
| `L-7` | `favorite` | 不同的事 | favorite 这条讲「删 favorite-rpc 的域侧 MyBatis-Plus 排序桥（`MybatisPlusOrderingBridgeAutoConfiguration` ＋ 本域 `imports` 整份）」，article 那条讲「删 article-api 的域内路径归一副本（`GoStylePathNormalizingFilter`）」⇒ 落点组件与现象都不同 ⇒ 撞号，保持拆开 | `DRIFT/article/vs-common.md:97`（article 域 `L-7` · 非同一件事；favorite 这条的真身是 article 域 `L-6`「删本域 rpc 组件的域侧排序桥」） |
| `L-8` | `favorite` | 不同的事 | favorite 这条讲「favorite-api 的 JWT 验签被计入 3s 预算」（落点 `FavoriteJwtAuthInterceptor` ＋ 新增 `@Order(+6)` Filter ＋ 守门 `FavoriteApiJwtVerificationScopeTest`），article 那条讲「article-api 的 JWT 验签被计入 70s 预算」（落点 `JwtAuthInterceptor`）⇒ 同一配方但组件、预算数值、落点文件、守门各不同 ⇒ 各自域一条，不合并 | `DRIFT/article/vs-common.md:98`（article 域 `L-8` · 非同一件事） |
| `G4-3` | `favorite` | 不同的事 | favorite 这条讲「A9 轴定义的措辞缺陷（轴定义写 `audit.*`，实测共享层 `sea.*`／admin `audit.*`／favorite `biz.*`）」，article 那条讲「`SqlTimeoutStatusTranslator` 的 javadoc『Go 出处』段归因错误」⇒ 落点与现象都不同 ⇒ 撞号，保持拆开 | `DRIFT/article/vs-common.md:276`（article 域 `G4-3` · 非同一件事；favorite 这条的真身是 article 域 `G4-4` ＋ hot 域 `G4-2`） |
| `G4-4` | `favorite` | 不同的事 | favorite 这条讲「A3 form 闸门：共享层 `GoStyleFormLimitMode` 的语义可达性判据与 `tools/difftest/check-tomcat-limits.py` 的 `FORBIDDEN_FORM_LIMIT_MODE_MODULES` 互相打架」，article 那条讲「A9 轴定义的措辞缺陷」⇒ 落点与现象都不同 ⇒ 撞号，保持拆开 | （未派工 ⇒ 无本体）；article 同号条目 `DRIFT/article/vs-common.md:277` |

---

### 判定口径（本 lane 自查）

- **本体来源**：任务书清单给的「本体位置」＝派工清单 `_lanes.json` 里被派去定级的那条；本轮实测 `_lanes.json` 只有 `W1`~`W14`（无 `W15`~`W17`），故 7 条带本体的条目改用「同编号在其它域的登记」逐条开正文对照，8 条「未派工 ⇒ 无本体」的条目改用「台账正文自述的来源」追溯本体（`E65`→global、`PIT-FAV-RPC-036`→坑档、`F-1`/`F-2`/`F-4`/`L-4`/`G4-4`→article 同号条目）。
- **「同一件事」的判据**：同现象 ＋ 同落点 ＋ 同组件 ⇒ 判为同一件事（`E65`、`PIT-FAV-RPC-036`、`G4-1`、`G4-2` 共 4 条）。**「配方同类但落点不同」不算同一件事**（`L-8`：favorite-api 3s 预算 vs article-api 70s 预算 ⇒ 各自域一条）。
- **跨域同号同内容的三处「真身错位」**（收口时需改指针，否则会重复派单）：favorite `G4-5`＝client breaker（真身在 article `G4-6`／comment `G4-2`）；favorite `G4-3`＝A9 轴措辞（真身在 article `G4-4`／hot `G4-2`）；favorite `L-7`＝删域侧排序桥（真身在 article `L-6`）。
- **计数**：同一件事 **4** 条（`E65`／`PIT-FAV-RPC-036`／`G4-1`／`G4-2`）· 不同的事 **11** 条 · 无法判定 **0** 条。
