# W22 · 同源判定（9 条）

> 判据 = [`docs/DRIFT/_cross/grading/_README.md`](./_README.md) 与任务书 §2 ·
> **本体位置** = 派工清单 `_lanes.json` 记录的那条台账条目（`tools/ledger-derive/gen-identity-groups.py` 的 `body_domains()` 按它取本体域）·
> ⚠️ **未派工的编号工具侧取不到本体域** ⇒ 下表「本体位置」列补出**实际本体**（含跨域本体），供收口时补进 `identity-groups.txt` 的域列表。

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `DRIFT-MSG-V-030` | `article` | 同一件事 | 本体在 message 域（`DRIFT/message/rpc.md` 的 `DRIFT-MSG-V-030` §6「已关闭」＝跨 6 域的「6 个 api 模块 jar 不可执行」），article 这半只是同一缺陷在本域的落点（`service/article/article-api/pom.xml` 补 `<build>` 段 ⇒ 68 KB library jar 变 69 MB 可执行 fat jar）⇒ 同一件事的**域内那半** | （未派工 ⇒ 无本体）／实际本体 = message 域 `DRIFT/message/rpc.md:2774` |
| `DRIFT-MSG-V-030` | `comment` | 同一件事 | 与上条同一件事：本体是 message 域的 `DRIFT-MSG-V-030`（跨 6 域那一条），comment 这半只登记 `service/comment/comment-api/pom.xml` 补 `<build>` 段后产物由 52 KB library jar 变 68 MB 可执行 fat jar ⇒ 现象与落点都是本体那件事在本域的局部 | （未派工 ⇒ 无本体）／实际本体 = message 域 `DRIFT/message/rpc.md:2774` |
| `E65` | `comment` | 同一件事 | 本体是 global 域的 `E65`（`DRIFT/global/vs-common.md:30`「🔴 OPEN」＝出向 gRPC 不继承请求 deadline），comment 这半在 `DRIFT/comment/rpc.md:257` 标题即写「D8（= global **E65**）」⇒ 同一现象，只是落本域的 2 个裸 stub（`ArticleRpcClient.java:54/65` · `MessageRpcClient.java:49`）⇒ 域侧那半 | （未派工 ⇒ 无本体）／实际本体 = global 域 `DRIFT/global/vs-common.md:30` |
| `G4-1` | `comment` | 不同的事 | 同号撞车：comment 的 `G4-1` 讲「MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制」（＝G4 的**单 1**），而 article 的 `G4-1`（`DRIFT/article/vs-common.md:274`）已撤销、讲的是 `DRIFT-ARTICLE-API-006` 的共享 helper 文案口径，favorite 的 `G4-1`（`DRIFT/favorite/vs-common.md:108`）讲 `E65` 出向 deadline ⇒ 现象与落点都不同 ⇒ 只是编号撞了；comment 这条真正的同题孪生是 article／favorite 的 `G4-2`（＝单 1） | （未派工 ⇒ 无本体）／同号对照 = article `DRIFT/article/vs-common.md:274` ／ favorite `DRIFT/favorite/vs-common.md:108` |
| `G4-2` | `comment` | 不同的事 | 本体（article 的 `G4-2` ＝ `DRIFT/article/vs-common.md:275`）讲「MyBatis-Plus 自动收集 `Interceptor[]` Bean」＝G4 的**单 1**，comment 这条 `G4-2` 讲的却是「`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径」＝G4 的**单 2**（落点 `GoStyleGrpcClientBreakerInterceptor.java:103-106`）⇒ 现象与落点都不同 ⇒ **同号不同单**；它的同题孪生是 article 的 `G4-6` ／ favorite 的 `G4-5` | `DRIFT/article/vs-common.md:275`（＝单 1）⇒ **不是** comment 这条讲的单 2 |
| `G4-3` | `comment` | 不同的事 | 本体（article 的 `G4-3` ＝ `DRIFT/article/vs-common.md:276`）讲 `SqlTimeoutStatusTranslator` 的「Go 出处」段归因错误＝G4 的**单 3**，comment 这条 `G4-3` 讲的却是 `E65`（出向 gRPC 不继承请求 deadline · 落点 `GoStyleRequestDeadline.java:110-120`）⇒ 现象与落点都不同 ⇒ **同号不同单**；它的同题孪生是 favorite 的 `G4-1`（＝`E65`） | `DRIFT/article/vs-common.md:276`（＝单 3）⇒ **不是** comment 这条讲的 `E65` |
| `A-1` | `article` | 不同的事 | article 这条 `A-1` 讲「article-api／article-rpc 两侧同时起的双侧活体对拍」，而同号的 users 域 `A-1`（`DRIFT/users/admin/api.md:1329`）讲「user-api 的 400 响应缺 charset」、Intentional_Deviations 的 `A-1@common`（`Intentional_Deviations/common/_signed-deviations.md:30`）讲「Go `os.Exit(1)` vs RAG `FatalError`」⇒ 三者现象与落点都不同 ⇒ 只是编号撞了（派生层 §1.6 同号表也把这 3 个 `A-1` 并排记成 3 种状态） | （未派工 ⇒ 无本体）／同号对照 = users 域 `DRIFT/users/admin/api.md:1329` ／ common 档 `Intentional_Deviations/common/_signed-deviations.md:30` |
| `G4-4` | `article` | 不同的事 | article 这条 `G4-4` 讲「A9 轴定义的措辞缺陷」（共享层 `sea.*` vs 域特有 `audit.*`／`biz.*`），而同号的 comment `G4-4`（`DRIFT/comment/vs-common.md:89`）讲「共享层 2 处 U+FFFD 注释损坏」、favorite `G4-4`（`DRIFT/favorite/vs-common.md:111`）讲「A3 form 闸门」⇒ 现象与落点都不同 ⇒ **同号不同单**；article 这条的同题孪生是 favorite 的 `G4-3`（＝global 的**建议项 2**） | （未派工 ⇒ 无本体）／同号对照 = comment `DRIFT/comment/vs-common.md:89` ／ favorite `DRIFT/favorite/vs-common.md:111` |
| `PF-ARTICLE-RPC-016` | `article` | 同一件事 | 两处讲同一件事：`Pitfalls/article/rpc.md:950` 是这条坑的踩坑记录本体（自记「旧号 `PF-ARTICLE-RPC-016` ⇒ 新号 `PIT-ARTICLE-38」· 现象＝Testcontainers 宿主口落进 OS 临时段 ⇒ `EOFException`），`DRIFT/article/rpc.md:2072` 是同一坑在 article 域把根因治掉的记录（宿主口钉口 `22850–22869`）⇒ 踩坑档 + DRIFT 档是同一条的两个登记 | （未派工 ⇒ 无本体）／本体 = `Pitfalls/article/rpc.md:950`（现行号 `PIT-ARTICLE-38`） |

---

## ⚠️ 收口提示（给合并收口执行者 · 不在本 lane 写权内）

1. **`G4-n` 是「各域各自流水号」，不是全局单号** ⇒ 同号必然撞车：
   **单 1**（MyBatis-Plus 自动收集 `Interceptor[]` Bean）= comment `G4-1` ＝ article `G4-2` ＝ favorite `G4-2`；
   **单 2**（client breaker 在 rpc 侧无装配路径）= comment `G4-2` ＝ article `G4-6` ＝ favorite `G4-5`；
   **单 3**（`SqlTimeoutStatusTranslator` javadoc 归因）= article `G4-3`；
   **`E65`** = comment `G4-3` ＝ favorite `G4-1` ＝ global `E65`；
   **建议项 2**（A9 轴措辞）= article `G4-4` ＝ favorite `G4-3`。
   ⇒ ⇒ **同源组按「编号 + 域」建 ⇒ 跨编号的同题（单 1／单 2／建议项 2）工具侧合不了** ⇒ 这些仍会各判一次。
2. **`DRIFT-MSG-V-030` 的本体域 `message` 未进派工清单** ⇒ `gen-identity-groups.py` 的 `body_domains()` 取不到它 ⇒
   即便本表判「同一件事」，生成的组也只有 `article`／`comment`（＋`like`，若 W20 同判）⇒
   **message 域那条「✅ 已关闭」仍单列** ⇒ 派生层仍会看到一条 🔴／🟡 ⇒ **需手工把 `message` 补进 `identity-groups.txt` 的域列表**。
3. **`G4-2` ／ `G4-3` 的本体域（`article`）会被工具无条件并入同源组**（`body_domains()` 按 `_lanes.json` 的首条来源取）⇒
   本表把 comment 的 `G4-2`／`G4-3` 判为「不同的事」正是为了**阻止**它与 article 的 `G4-2`／`G4-3` 被错误合并。
