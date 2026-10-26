# global 第十六轮 —— X-9 收敛：「Go 是否读 body」判据只剩一份实现 + 补 Go 的 `maxBodyLen = 8 MiB`

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`61d723c`
> **性质**：**共享层真缺陷修复 + 跨域收敛（hot / article）+ 双侧活体探针 + 真容器守门 + 台账**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（剩一份复制品 ⇒ **永久残留，记账 + 告知 owner**）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二（X-9）：
「Go 是否读 body」这条规则**在仓库里有 3 份生产复制品**（favorite / hot / article），
共享层 `GoStyleJsonBodyConverter.goReadsBody` 自称唯一真源，但**没人调它**。

⇒ 风险不是「今天行为错」（今天三份都取原始头值 ⇒ 行为是对的），
而是**判据有多份实现 ⇒ 迟早分叉 ⇒ 分叉后没有任何测试会红**。

---

## 二、🔴 取证：先建「双侧活体探针」，再动手

§3.3 要求「凡写『与 Go 一致』，必须有**两侧同时跑的**探针」。
本轮新建 `tools/json-body-probe/`（Go 半边 + Java 半边 + `run-parity.sh`）。

### ① 为什么探 hot 域

Go 的 hot 路由是 **`GET /hot/v1/articles`**（`routes.go:16-24`）⇒ 容易误以为「GET 不读 body」；
但 go-zero 的 `httpx.Parse` 里 **`ParseJsonBody` 是无条件调用的**（`requests.go:63-65`）
⇒ GET **也会**读 JSON body。
且 `HotArticlesReq` **只有 form tag、没有 json tag**（`types.go:22-25`）
⇒ JSON 字段被整体忽略 ⇒ 本端点的 JSON body **只影响「能不能解析」这一个判定**。

### ② 20 条用例（Go 半边直接调 `httpx.Parse`，期望值不是人写的）

判据矩阵（大小写 / charset / `+json` 后缀 / 逗号多值头 / 缺头 / `Content-Length` 0 / chunked -1）·
顶层形态（数组 / null / 数字 / 字符串 / 尾随垃圾）· 9 MiB 单个 JSON 对象。

### ③ 🔴 首跑结果：**19/20 一致，J20 不一致**

```
J20 9MiB 单个 JSON 对象 + JSON 头 | Go REJECTED | RAG ACCEPTED | XX 判定不一致
```

Go 侧错误详情长度 = **8388643 = 8 MiB + 3** ⇒ **截断点精确在 8 MiB**。

⇒ **坐实一条真漂移**（不是推测）：

| | Go | RAG（修前） |
|---|---|---|
| > 8 MiB 的 JSON body | `io.LimitReader(r.Body, 8MiB)` **静默截断** ⇒ 半个文档 ⇒ **报错** | `readAllBytes()` **整个读进堆里** ⇒ **解析成功** |

⇒ 而这条**不是 hot 独有**：`maxBodyLen` 在 go-zero 的 `httpx` 包里，
⇒ **所有**走 `httpx.ParseJsonBody` 的域都有 ⇒ RAG 侧**所有走共享转换器的域**
（like / follow / message / task / user / admin / article）都缺这道闸
⇒ ⇒ **必须修在共享层**，一次覆盖 7 个域 ⇒ 这也正是它属 global 收口域的原因。

### ④ 🔴 但「端到端可达性」要单独算（**本轮实测补的层次**）

修完代码后跑端到端时发现：**hot 的 9 MiB body 端到端是 413，不是 5001** ⇒ 于是回头查了 Go 的 MaxBytes：

```
go-zero v1.10.2 rest/config.go:53   MaxBytes   int64 `json:",default=1048576"`   ← 1 MiB 默认值
go-zero v1.10.2 rest/config.go:21   Middlewares.MaxBytes bool `json:",default=true"`
go-zero v1.10.2 rest/engine.go:163-164
    if ng.conf.Middlewares.MaxBytes {
        chn = chn.Append(handler.MaxBytesHandler(ng.checkedMaxBytes(fr.maxBytes)))
    }                                                       ← ← 挂在**引擎**上 ⇒ 全局生效
```

⇒ Go 的 MaxBytes **默认 1 MiB 且对所有路由生效** ⇒ 一个 9 MiB 的 body
在 Go 侧**先被 MaxBytes 拦成裸 413**（net/http 的 `requestTooLarge()`），
**根本走不到** `ParseJsonBody`。

⇒ **逐域算「8 MiB 这一段端到端可不可达」**（RAG 侧各 api 的 `max-bytes` 配置）：

| api 模块 | RAG `max-bytes` | 8–10 MiB 段端到端可达？ |
|---|---|---|
| `article-api` | **10485760**（10 MiB） | 🟢 **可达** ⇒ 但已被 `GoStyleJsonBodyLimitFilter` 覆盖（显式 400，已签字 DRIFT-006） |
| `hot-api` / `comment-api` / `follow-api` / `like-api` / `message-api` / `task-api` | 1 MiB（Go 默认） | ⛔ 不可达（MaxBytes 先拒） |
| `user-api` / `admin-api` / `favorite-api` | 未配 ⇒ 走共享 `GoStyleMaxBytesFilter` 的默认值 | ⛔ 不可达 |

⇒ **结论（必须诚实写出来）**：
- 这次修的是**解析层**与 Go 的对齐（探针直接验的就是这一层）；
- **端到端**今天**没有任何域会因它而改变行为**（唯一可达的 article 段已被它自己的 filter 覆盖）；
- ⇒ 价值 = **解析层忠实** + **去掉共享层的无上限读** + **防「哪天 MaxBytes 被调大」**；
- ⇒ 风险 = **零**（今天行为不变）。

⚠️ **这个层次区分本身就是本轮的一条产出**：差点把「解析层漂移」写成「端到端漂移」，
而端到端那条**根本不存在**（MaxBytes 先拒）⇒ 「跑绿 ≠ 对」的另一个形态：
**探针测的层 ≠ 端到端跑的层**。

---

## 三、改动

### ① 共享层 `service/common`（唯一真源）

**`GoStyleJsonBodyConverter`**

| 新增 | 作用 |
|---|---|
| `goReadsBody(HttpServletRequest)` | servlet 版判据 —— 连「怎么从请求里取头值 / 长度」一起收口 |
| `GO_MAX_BODY_LEN = 8 MiB` | Go `rest/httpx/requests.go:21` 的 `maxBodyLen`（**不可配置**，Go 侧是框架常量） |
| `goLimitedBody(InputStream)` | Go `io.LimitReader(r.Body, maxBodyLen)` 的等价物 |
| `SanitizedJsonContentTypeInputMessage#getBody` | 改走 `goLimitedBody` ⇒ 转换器只读前 8 MiB |

⚠️ **为什么用流式限流而不是 `readNBytes(8MiB)`**：两者判定结果相同，但
`readNBytes` 会**一直读到 8 MiB 或 EOF** ⇒ 客户端「发一个小 JSON 然后停住」时
RAG 白占线程，而 Go 的 `json.Decoder` 早就返回了 ⇒ 流式才与 Go 同形。

**取头值的承重事实（逐行实测，非推测）**：

```
org/apache/catalina/connector/Request.java:1081-1083  getContentType() → coyoteRequest.getContentType()
org/apache/coyote/Request.java:694-700                 → contentTypeMB.toStringType()
org/apache/tomcat/util/buf/MessageBytes.java:214-229   → byteC.toString()
org/apache/tomcat/util/buf/ByteChunk.java:602-609      → 只做字符集解码（REPLACE）
```

⇒ 全链**没有**小写化 / MIME 归一 ⇒ `Content-Type: Application/JSON` 到达应用层仍是原样
⇒ `contains("application/json")` 保持**大小写敏感** ⇒ 与 Go 的 `strings.Contains` 同形。

⚠️ **另修一处 int / int64 不一致**：hot 的副本原先用 `request.getContentLength()`（**int**），
而 `coyote/Request.java:664-671` 在 length ≥ `Integer.MAX_VALUE` 时返回 **-1**
⇒ 与 Go 的 `r.ContentLength`（**int64**）判成相反 ⇒ 改用 `getContentLengthLong()`。

### ② `service/hot/hot-api`（X-9 收敛 + 补闸）

`GoFormValues.requireParsableJsonBody`：
- 判据改调 `GoStyleJsonBodyConverter.goReadsBody(request)`（**不再本地抄一份**）；
- 读 body 改走 `GoStyleJsonBodyConverter.goLimitedBody(...)` ⇒ 补上 Go 的 8 MiB 闸；
- 解析从 `readTree(byte[])` 改为 `readTree(InputStream)`（流式 ⇒ 不等满 8 MiB）。

### ③ `service/article/article-api`（X-9 收敛）

`GoStyleJsonBodyLimitFilter`：删掉本地那份 `goReadsJsonBody` 判据，改调唯一真源。
（该 Filter **仍在链上** —— 它守的是 Go 的 `maxBodyLen`，与判据收敛是两件事。）

---

## 四、🔴 守门（每条都做过变异）

| # | 守门 | 位置 | 性质 |
|---|---|---|---|
| G1 | **双侧活体探针** 20 例 | `tools/json-body-probe/` | Go 半边真调 `httpx.Parse`；**无白名单** ⇒ 任一条不一致即 exit 1 |
| G2 | 真 Tomcat：原始 Content-Type 不被归一 | `service/common` `GoStyleJsonBodyRawHeaderTomcatTest` | **真容器 + 裸 socket**（MockMvc / MockHttpServletRequest 都不够） |
| G3 | 共享转换器只读前 8 MiB | `service/common` `GoStyleJsonBodyMaxBodyLenTest` | 边界卡到 **±1 字节** + 「底层流最多被读走 8 MiB」 |
| G4 | hot 域两道闸 | `service/hot/hot-api` `HotApiJsonBodyGateTest` | 9 MiB 端到端 ⇒ **413**（MaxBytes，Go 同形）；**解析层**直接调用 ⇒ 5001（8 MiB 截断）；大小写 ⇒ 不读 |
| G5 | article 域闸门走共享判据 | `service/article/article-api` `ArticleApiJsonBodyLimitCaseTest` | 大小写 ⇒ 闸门**不触发**（判别式：body 含 `is not set` 而非 `request entity too large`） |
| G6 | 判据只有一份实现 | `tools/difftest/check-go-json-body-predicate.py` | 复制品白名单只留 favorite 一条 |

### 变异证据（§3.3：绿测试可能是「错误答案的守卫」）

| 变异 | 预期转红 | 实测 |
|---|---|---|
| M1 共享真源判据改成**大小写不敏感**（`toLowerCase().contains`） | G2 / G4 / G5 + 探针 J02/J03/J04 | ✅ **common 5 条 · hot 2 条 · article 1 条 · 探针 3 条不一致** |
| M2 `GO_MAX_BODY_LEN` 改成 1 MiB | G3（8 MiB 边界） | 🔴 **首跑 0 红 ⇒ 抓到我自己守门的同义反复** ⇒ 修正后**复验转红**：common 228 条里 `maxBodyLenConstantMatchesGo` 失败 + `exactlyMaxBodyLenIsAccepted` 报错 |
| M3 某域重新抄一份判据 | G6 | ✅ `EXIT 1` |
| M4 去掉共享转换器的限流 | G3 + 探针 J20 | ✅ **common 3 条转红**（`oneByteOverMaxBodyLenIsRejected` / `nineMiBJsonObjectIsRejected` / `underlyingStreamIsCappedAtEightMiB`） |

#### 🔴 M2 抓到的是**我自己写的守门**的同义反复（本轮最重要的一条）

首版 G3（`GoStyleJsonBodyMaxBodyLenTest`）**用 `GoStyleJsonBodyConverter.GO_MAX_BODY_LEN` 这个常量**
去构造边界 body ⇒ 变异把常量改成 1 MiB 后，**测试构造的 body 也跟着缩到 1 MiB**
⇒ **恒绿** ⇒ 也就是说：**这条守门当时根本没在守那个上限值**。

⇒ 修正两处：
1. 边界构造一律用**字面值** `8 * 1024 * 1024`（Go 出处 `rest/httpx/requests.go:21` 的 `maxBodyLen = 8 << 20`）；
2. 另配一条断言把**常量本身**钉住（`maxBodyLenConstantMatchesGo`）⇒ 它成为 M2 的唯一拦截点。

⚠️ **探针抓不到 M2**（实测：探针 20/20 仍绿）⇒ 因为探针比的是**判定**（读 / 不读、拒绝 / 接受），
⇒ 上限从 8 MiB 改成 1 MiB 时，**两侧都判「拒绝」** ⇒ 判定一致 ⇒ 探针无感
⇒ ⇒ **限值这类「同一个判定、不同阈值」的差异，只能由边界测试守** ⇒ 这是探针的能力边界，要写进文档。

⚠️ **变异脚本本身踩了两个坑**（⇒ 已补进 PIT-GLOBAL-014 的判据 4 / 5）：
1. **同一 reactor 里上游失败 ⇒ 下游被 Maven 跳过** ⇒ 第一版脚本只看到 `common` 的红，
   `hot-api` / `article-api` **压根没跑** ⇒ 「没转红」是**假阴性**；
2. **还原用 `git checkout --` ⇒ 把本轮未提交的共享层改动一起抹了**
   ⇒ 共享层文件被回滚到 HEAD ⇒ hot / article 的调用点立刻编译不过
   ⇒ **看起来像「变异把代码改坏了」，其实是还原动作干的** ⇒ 改用 `cp` 备份副本还原。

---

## 五、Go 出处汇总

| 事实 | 出处 |
|---|---|
| `withJsonBody` 判据（字面子串 + 大小写敏感 + 原始头值） | go-zero v1.10.2 `rest/httpx/requests.go:153-155` |
| `maxBodyLen = 8 << 20` | 同上 `:21` |
| `io.LimitReader(r.Body, maxBodyLen)` | 同上 `:119` |
| `ParseJsonBody` **无条件**被 `Parse` 调用 | 同上 `:63-65` |
| 顶层非 map ⇒ `errUnsupportedType` | `core/mapping/unmarshaler.go:975-998` |
| 顶层数组 ⇒ `errTypeMismatch` | 同上 `:989-992` |
| `HotArticlesReq` 只有 form tag | `Sea-RideTheWind_golang/service/hot/api/internal/types/types.go:22-25` |
| hot 路由是 GET | 同上 `service/hot/api/internal/handler/routes.go:16-24` |

---

## 六、favorite 域的待办（**本轮零触碰**）

| 项 | 说明 |
|---|---|
| `favorite-api` 的 `FavoriteController.java:146` 那份复制品 | 用户重申 favorite 零权限 ⇒ **永久残留**。当前取原始头值 ⇒ 判定仍与 Go 一致 ⇒ **当前行为无偏差**；风险仅在「未来被改成 MediaType 解析」⇒ 已进 G6 白名单 |
| 修法 | 改成 `GoStyleJsonBodyConverter.goReadsBody(httpRequest)`（需 favorite 域 owner 执行；**前提**：`favorite-api` 的 classpath 上有 Jackson 3 —— `spring-boot-starter-web` 已带，实测 hot-api 同款可用） |

---

## 七、验证清单

- [x] `mvn -o test -pl service/common` 全绿（228 条）
- [x] `mvn -o test -pl service/hot/hot-api` 全绿（96 条）
- [x] `mvn -o test -pl service/article/article-api` 全绿（90 条）
- [x] 其余 8 个 api 模块全绿（like / follow / message / comment / task / user / admin / **favorite** ⇒ **零触碰**）
- [x] `bash tools/json-body-probe/run-parity.sh` ⇒ 20/20 一致
- [x] `python3 tools/difftest/check-go-json-body-predicate.py` ⇒ EXIT=0
- [x] 变异 M1–M4 ⇒ 对应守门转红（M2 首跑抓到同义反复 ⇒ 修正后复验转红）

## 八、📌 待维护者合并到**共享索引**（global 不写，§5.3b）

| 位置 | 要加什么 |
|---|---|
| `AGENTS.md` §7.2 **工具档**表 | ① `tools/json-body-probe/` —— 「Go 是否读 body」**双侧对拍探针**（`run-parity.sh`）② `tools/difftest/check-go-json-body-predicate.py` —— **判据只能有一份实现**的复制品守卫（白名单只留 favorite 一条） |
| `AGENTS.md` §0 域表 `global` 行 | X-9 由「🟡 当前行为正确，但是定时炸弹」⇒ 改为「✅ 第十六轮已收敛到 1 份」 |
| `docs/DRIFT/_INDEX.md` | global 域的台账变更记录指针 |

⚠️ **favorite 域的 owner 需要知道的两件事**（本轮零触碰，但别静默）：

1. `favorite-api` 的 `FavoriteController.java:146` 那份判据**永久残留** ⇒ 当前取原始头值 ⇒ **行为无偏差**；
   修法 = 改成 `GoStyleJsonBodyConverter.goReadsBody(httpRequest)`
   （**前提**：`favorite-api` 的 classpath 上有 Jackson 3 —— `spring-boot-starter-web` 已带，实测 `hot-api` 同款可用）；
2. `favorite-rpc` 的 `spring.flyway.baseline-version: 0` 缺失（**第十五轮**记的账，仍未修）⇒ 非首个启动者时服务起不来。
