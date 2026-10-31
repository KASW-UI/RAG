<!-- @new 2026-10-24 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

# DRIFT 台账 · `service/article/article-proto`

> **本文件是 38 组件覆盖矩阵里 `article` 域最后一个无专属台账的组件** ——
> 此前 `article-proto` 只被 AGENTS.md §3.4 的「proto 是复制粘贴」一条覆盖，
> 而 §3.4 **效力边界第 1 条**明写：「复制忠实 **≠** 消费方式已验」。
>
> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树，从 `do-my-own-gh` 切出）。
> 证据强度三档：**生成产物实测** · **源码逐行对照（两侧 + 框架消费点）** · **未做活体对拍**。
>
> 📌 **本文件是单轮台账**（没有历史轮次）⇒ **全文即「现状权威面」**，不存在「哪一节才是现状」的歧义。
> ⇒ 域内其它组件是**多轮**台账（各有「怎么读这份台账」的指针 + **跨文件轮次对照表**）：
> [`api.md`](api.md) · [`rpc.md`](rpc.md)。

## 0. 结论

**✅ 一致 —— 本组件无漂移。**

但这个结论**不是** §3.4 的复制 diff 给的，是本轮**逐条补做消费方式核对**后得出的。
§3.4 本身只覆盖了「文件两侧一致」，本文件覆盖的是它**明确不覆盖**的三件事：

| §3.4 效力边界的三项 | 本轮结论 | 证据档 |
|---|---|---|
| ① `optional` presence 语义 | ✅ 一致（11 个 optional 字段逐个核对） | 源码逐行对照 |
| ② 字段号是否被硬编码覆盖 | ✅ 无任何硬编码 | 全仓 grep |
| ③ Jackson / protobuf 配置差异 | ✅ 不适用（proto 只走 binary，不进 Jackson） | 全仓 grep |

**2026-10-24 第十一轮补充**：`P-U-2` 关闭 ——
`Article` message 的两个 jsonb 列在 proto 层是 **`repeated string secondary_tags = 7`**
与 **`map<string, string> ext_info = 16`**（**两侧逐字节一致**）；
两侧的实体→proto 映射都是「**null / 空 ⇒ 不写入**」⇒ **wire 字节一致** ⇒ 非漂移。
⇒ 详见 §6 的 `P-U-2` 条目。

---

## 1. 组件事实

| 项 | 值 | 证据 |
|---|---|---|
| Go 真源 | `Sea-RideTheWind_golang/proto/article.proto`（**133 行**） | 实测 |
| RAG 目标 | `service/article/article-proto/src/main/proto/article.proto`（**134 行**） | 实测 |
| proto 语法 | 两侧均 `syntax = "proto3"` | 逐行 |
| proto `package` | 两侧均 **`article`** | 逐行 |
| service / rpc | `ArticleService` + 6 个 rpc，两侧同名同序 | 逐行 |
| message 数 | 14（含 `Article` + 13 请求/响应） | 逐行 |
| `optional` 字段数 | **11** | 逐行 |
| 生成产物 | **29 个 java**（含 `ArticleServiceGrpc.java`） | `target/generated-sources/` 实测 |
| 消费方 | `article-rpc`（pom 依赖）+ `article-api`（pom 依赖） | pom 实测 |

### 1.1 逐行 diff 的**唯一**差异

```
3c3,4
< option go_package = ".";
---
> option java_package = "com.rag.article.article.proto";
> option java_multiple_files = true;
```

⇒ **零 message / 零字段名 / 零字段号 / 零类型 / 零枚举值差异。**
（`go_package` 是 Go 专用项，Java 不消费；`java_package` + `java_multiple_files` 是 Java 专用项。）

### 1.2 ⭐ 关键结论：**article 的 proto `package` 没有被归一**

> **这与 `comment` / `like` 两域相反，值得单独记一笔。**

| 域 | Go 侧 proto `package` | RAG 侧 | wire 方法路径 | 是否需签字偏离 |
|---|---|---|---|---|
| `comment` | `pb`（遗留名） | `comment`（归一） | `/pb.X/*` vs `/comment.X/*` | ✅ `SD-2026-10-23-04` |
| `like` | `pb`（遗留名） | `like`（归一） | `/pb.X/*` vs `/like.X/*` | ✅ `SD-2026-10-23-05` |
| **`article`** | **`article`** | **`article`** | **`/article.ArticleService/*`（两侧一致）** | ❌ **不需要** |

⇒ `java_package = "com.rag.article.article.proto"` 只影响 **Java 包名**，
**不影响 wire 格式** —— wire 上的 service 全名由 proto `package` + service 名决定。
⇒ **article 域跨栈 gRPC 互调不会失效**，不需要登记 `Intentional_Deviations`。

---

## 2. 效力边界第 ① 项：`optional` presence 语义（11 个字段）

proto3 的 `optional` 引入**显式 presence**。Go 侧生成的是**指针**，Java 侧生成的是 `hasXxx()`。
⇒ **两侧判 presence 的写法必须一一对应**，否则「字段没传」与「字段传了空串」会混淆。🆕 **【2026-10-31 · G3 第二十一轮】** 本节是**源码级**对照；**字节级**证据（两侧**不同运行时**产出的 wire 字节逐字节一致，含「**显式设的空串落 wire / 没设的不落**」这一对）⇒ 见 **§8**。

### 2.1 api 层：**下发**侧（Go `.api` tag 的 `optional` ≠ proto 的 `optional`）

⚠️ 本域有两套「optional」，**不是同一个东西**，混起来必错：

| 层 | Go 的写法 | 生成的类型 |
|---|---|---|
| **HTTP 层**（`internal/types/types.go`） | `Brief string \`json:"brief,optional"\`` | **普通 `string`，不是指针** |
| **gRPC 层**（`proto/article.proto`） | `optional string brief = 2;` | **指针 `*string`** |

⇒ HTTP 层的 `optional` 只表示「JSON 里可以没有」；**真正的 presence 是在 gRPC 层由 `&req.X` 决定的**。

| 字段 | Go 下发写法 | RAG 下发写法 | 判定 |
|---|---|---|---|
| `CreateArticleRequest.brief` | `Brief: &req.Brief`（**恒非 nil** ⇒ presence 恒 true） | `.setBrief(nullToEmpty(...))`（恒 set ⇒ `hasBrief()` 恒 true） | ✅ |
| `CreateArticleRequest.cover_image_url` | `CoverImageUrl: &req.CoverImageUrl` | `.setCoverImageUrl(nullToEmpty(...))` | ✅ |
| `ListArticlesRequest` 的 4 个筛选字段 | `ManualTypeTag: &req.ManualTypeTag` 等（**恒非 nil**） | `.setManualTypeTag(...)` 等（恒 set） | ✅ |
| `UpdateArticleRequest` 的 5 个字符串 | `if req.X != "" { rpcReq.X = &req.X }`（**非空才下发**） | `setIfNotEmpty(builder, ...)` | ✅ |
| `UpdateArticleRequest.secondary_tags` | `SecondaryTags: req.SecondaryTags`（**恒下发**） | `builder.addAllSecondaryTags(...)` | ✅ |
| `UpdateArticleRequest.status` | `if req.Status != 0 { rpcReq.Status = status.Enum() }` —— `Enum()` 对**未知枚举值返 nil** ⇒ 不下发 | `if (ArticleStatus.forNumber(status) != null) builder.setStatus(...)` | ✅ |

> ⚠️ **最后一行是两个域各自最容易错的地方**：
> Go 的 `status.Enum()` 与 Java 的 `forNumber()` **都**对未知数值返回「无值」，
> 于是「非法状态」在两侧都变成「**不下发**」而不是「以非法值下发」。
> 若 Java 侧写成 `setStatusValue(7)`，就凭空多了一条 Go 没有的路径。

### 2.2 rpc 层：**读取**侧（Go 指针 `!= nil` ↔ Java `hasXxx()`）

| 语义 | Go（`rpc/internal/logic/update_article_logic.go`） | RAG（`ArticleDomainService`） | 判定 |
|---|---|---|---|
| 标题是否更新 | `if in.Title != nil { article.Title = *in.Title; sourceChanged = true }` | `if (in.hasTitle()) { …; sourceChanged = true; }` | ✅ |
| 简介 / 正文 / 封面 / 手工标签 | 同上（`in.Brief != nil` 等） | `in.hasBrief()` / `in.hasMarkdownContent()` / `in.hasCoverImageUrl()` / `in.hasManualTypeTag()` | ✅ |
| 状态 | `in.Status != nil && *in.Status != __.ArticleStatus_ARTICLE_STATUS_UNSPECIFIED` | `in.hasStatus() && in.getStatus() != ArticleStatus.ARTICLE_STATUS_UNSPECIFIED` | ✅ |
| **二级标签** | `if len(in.SecondaryTags) > 0 { … }` | `if (in.getSecondaryTagsCount() > 0) { … }` | ✅ |

> ⭐ **最后一行是本轮最容易被「统一风格」改错的一条**：
> `secondary_tags` 在 proto 里是 **`repeated`**（不是 `optional`）⇒ **没有 presence**。
> Go 用 `len() > 0` 判「有没有标签」，Java 侧对应的是 `getSecondaryTagsCount() > 0`，
> **不是** `hasSecondaryTags()`（repeated 字段根本没有 `hasXxx()` 方法，编译期就会暴露）。
> RAG 源码里已就此留了注释（`ArticleDomainService.java` 的「Go 用 len() > 0 判定」）。

### 2.3 `sourceChanged` 触发条件（逐字段对照）

Go（`update_article_logic.go:140-190`）与 RAG（`ArticleDomainService.applySourceUpdates`）
**6 个触发点逐条一致**：标题 / 简介 / 正文 / 封面 / 手工标签 / 二级标签，
且都是「**该字段 presence 成立即置 true**」，**不是**「值发生变化才置 true」。

> ⚠️ 这条也是易错形态：若 Java 侧写成「新旧值不同才置 true」，
> 「把简介改成和原来一样的字符串」在 Go 侧仍会触发 review 判定，Java 侧则不会。

---

## 3. 效力边界第 ② 项：字段号硬编码

`grep -rnE "getFieldNumber|getDescriptor\(\)\.getFields|findFieldByNumber|FIELD_NUMBER"` 于
`service/article/**`（排除 target）⇒ **零命中**。

⇒ 没有任何地方绕过生成代码直接按字段号取值 ⇒ **字段号未被硬编码覆盖**。

## 4. 效力边界第 ③ 项：Jackson / protobuf 配置差异

| 检查 | 结果 |
|---|---|
| proto 对象是否被 Jackson 直接序列化 | ❌ 否 —— `ArticleServiceGrpc` 只作为 gRPC stub 使用；HTTP 响应走本域自有的 `dto/Article.java` |
| `spring.jackson.*` 是否影响 wire 格式 | ❌ 否 —— wire 是 protobuf binary，与 Jackson 无关 |
| `ObjectMapper` 自定义是否触及 proto | ❌ 否 —— `WebMvcConfig` 的 Jackson 定制只作用于 HTTP DTO |

⇒ **该项不成立**，不存在「Jackson 配置漂移影响 proto 消费」的可能。

---

## 5. 「生成确实发生」的证据

> ⚠️ 本项是 §3.3 的典型形态：**文件看起来对 ≠ 能力存在**。

- 本仓历史上 `article-proto` 的 `pom.xml` **只有 `<properties>` 段**，`<dependencies>` 与 `<build>` 全空
  ⇒ **从未生成过任何代码**，产出的是一个**空 jar**；
  `article-api` 要调 `ArticleService`，在补上 protoc 插件段之前**连 import 都写不出来**。
- 2026-10-23 12:25 那轮已把插件段接上（与 `message-proto` 逐行同构 —— 那是本仓**唯一已验证能产出 `*Grpc.java`** 的模板）。
- **本轮实测**：`service/article/article-proto/target/generated-sources/protobuf/` 下
  **29 个 java**，含
  `grpc-java/com/rag/article/article/proto/ArticleServiceGrpc.java`
  与 `java/com/rag/article/article/proto/` 下的 14 个 message + 14 个 `*OrBuilder`。
- `article-rpc` / `article-api` 的 pom **都**声明了 `article-proto` 依赖 ⇒ 生成物确实被消费。

---

## 6. 未决

| ID | Question | Why Unknown | Required Confirmation |
|---|---|---|---|
| **P-U-1** | 跨栈 gRPC 互调（Go article-rpc ⇄ RAG article-rpc）未实测 | 本机拉不起 `etcd:2379`；且本组件**只有客户端/服务端两侧各自跑**，没有真实跨栈调用 | 起 etcd + 双栈，实测 `/article.ArticleService/GetArticle` 的 wire 互通 |
| **P-U-2** | `Article` message 的 **jsonb 两列**（`secondary_tags` / `ext_info`）在 proto 层是 `repeated string` / `string`？ | ~~本轮只核对了**请求/响应消息**的 presence，**未逐字段核对 `Article` message 本身**~~ | ✅ **已关闭**（2026-10-24 第十一轮，见下）—— 逐字段核对完成：`repeated string secondary_tags = 7` / `map<string, string> ext_info = 16`（**两侧逐字节一致**）；两侧的实体→proto 映射都是「null / 空 ⇒ 不写入」⇒ **wire 上完全一致** |

### ✅ `P-U-2` 关闭 —— `Article` message 的 jsonb 两列：逐字段核对 + wire 层判读（2026-10-24）

#### ① proto 层：`Article` message 两侧**逐字节一致**

```
Go:    Sea-RideTheWind_golang/proto/article.proto
RAG:   service/article/article-proto/src/main/proto/article.proto
```

| 字段 | Go | RAG | 一致 |
|---|---|---|---|
| `secondary_tags` | `repeated string secondary_tags = 7;` | 同 | ✅ |
| `ext_info` | `map<string, string> ext_info = 16;` | 同 | ✅ |

⇒ 整个 `Article` message（16 个字段，字段号 / 类型 / 名字）**两侧逐字节一致**
（去掉 `option` 与注释后 `diff` 为空）⇒ **P-U-2 的问句有明确答案**：
`secondary_tags` 是 **`repeated string`**、不是 `string`；`ext_info` 是 **`map<string, string>`**。

#### ② 消费侧：两侧的「null / 空 ⇒ 不写入」处置**等价** ⇒ wire 一致

| 侧 | 出处 | 处置 |
|---|---|---|
| Go | `internal/logic/get_article_logic.go:112` `SecondaryTags: article.SecondaryTags` | nil slice ⇒ proto3 `repeated` **无 presence** ⇒ **字段不存在** |
| Go | `internal/logic/get_article_logic.go:191-201` `cloneStringMap` | `len(source)==0 ⇒ return nil` ⇒ proto3 `map` **无 presence** ⇒ **字段不存在** |
| RAG | `ArticleDomainService:261-263` `if (a.getSecondaryTags() != null) { addAllSecondaryTags(...) }` | null ⇒ 不写 ⇒ **字段不存在** |
| RAG | `ArticleDomainService:265-267` `if (a.getExtInfo() != null && !isEmpty()) { putAllExtInfo(...) }` | null / 空 ⇒ 不写 ⇒ **字段不存在** |
| RAG | `ArticlePublicDomainService:394-399` | 同（公开路径同样有守卫） |

⇒ **wire 字节完全一致** ⇒ 这条**不是**漂移。

#### ③ 顺带把 rpc 台账第十轮那条「不可观察」结论**加了第三条独立论据**

rpc 台账第十轮关掉的那条差异（`secondary_tags` 存 **SQL NULL** vs **jsonb `'null'`**），
当时给的是「**读回**一致 + **过滤**一致」两条论据。
⇒ 本轮从 **proto / wire** 层给出**第三条**：

- Go 创建文章时（`create_article_logic.go:94`）请求不带标签 ⇒ `model.StringArray(nil)`
  ⇒ `json.Marshal(nil)` ⇒ 落库就是 **jsonb `'null'`**；
- RAG 读到 `'null'` ⇒ `readList` ⇒ **null** ⇒ 映射时 **null 守卫**跳过 ⇒ **字段不存在**；
- Go 侧同一行 ⇒ `SecondaryTags` 是 nil ⇒ proto3 `repeated` **字段不存在**；
- ⇒ **两侧 wire 字节逐字节相同** ⇒ 存储层那点差异**在 wire 上也不可观察**。

⇒ 新增守门网 `ArticleNullJsonbToProtoMappingTest`（真 PG，3 条，含 **wire 字节级**比对）。

⚠️ **本条关闭的边界**：`P-U-1`（跨栈活体对拍）**仍 OPEN** ——
上面全部是**源码级 + 单侧真 PG**证据，**不是**「Go 栈与 RAG 栈真的互调过」。
⇒ 本文件所有 ✅ 仍一律读作「**在源码级对照 + 单侧真 PG 证据下一致**」。

> ⚠️ **本台账的结论只覆盖「proto 文件 + 其消费方式」**，
> **不覆盖** wire 层的端到端行为 —— 后者归 [`article/rpc.md`](./rpc.md) 与 [`article/api.md`](./api.md)。

---

## 7. 结论

- `article-proto`：**无漂移**，且**不需要**签字偏离（proto `package` 两侧一致 ⇒ wire 路径相同）。
- 本轮补齐的是 §3.4 **明写不覆盖**的三项（presence / 字段号硬编码 / Jackson 配置），
  **2026-10-24 第十一轮**又补了 `P-U-2`（`Article` message 逐字段 + wire 字节级判读）。
- ⚠️ **本组件的证据强度上限 = 源码逐行对照 + 生成产物实测 + 双侧 wire 字节级对拍（两个运行时 · 7 组消息 · 变异已验）+ 单侧真 PG**；
  **没有**跨栈活体对拍（`P-U-1`）⇒ 按 §6 状态表纪律，
  本文件所有 ✅ 一律读作「**在源码级对照 + 双侧 wire 字节级对拍 + 单侧真 PG 证据下一致**」，不是「已闭环」。

---

## 8. 🔴 2026-10-31 · G3 第二十一轮：**wire 字节级**双侧对拍 ＋ 变异验证（§3.4 效力边界第 ① 项的字节级补齐）

> 🔴 **它补的是哪个缺口**：§2 的 `optional` presence 对照是**源码级**的
> （读 Go 的指针 / Java 的 `hasXxx()`）；而 AGENTS.md §3.4 明写
> 「**复制忠实 ≠ 消费方式已验**（`optional` presence / 字段号硬编码 / Jackson 差异）」
> ⇒ ⇒ **文件里 `optional string brief = 2;` 与 `string brief = 2;` 只差三个词，
> 在 wire 上却是「显式设的空串落不落 wire」** ⇒ **只有字节级才看得见**。
> ⇒ 本节把 §2 的结论从**源码级**升到**字节级**（两侧**不同运行时** ⇒ 不是自证）。

**新工具**：`tools/proto-wire-probe/article/`（**EXIT=0**）

| | Go 侧 | RAG 侧 |
|---|---|---|
| 代码 | **真源真正在用的** `service/article/rpc/pb/article.pb.go`（**逐字节复制 + sha256 校验**） | article-proto 的生成码 |
| 运行时 | `google.golang.org/protobuf`（protoc-gen-go 的运行时） | `protobuf-java`（protoc 的 Java 运行时） |

⇒ ⚠️ **两侧运行时不同** ⇒ 这**不是**「同一份实现自证」。

| # | 用例 | 钉住的事实 | 结果 |
|---|---|---|---|
| 1 | `article_full` | 全字段编码：varint / length-delimited / **枚举值编号进 wire** / `repeated string`（**两条独立 tag**，不是 packed blob）/ `map`（**单条目**）/ int64 用 2^53+1 ⇒ **不丢精度** | ✅ 逐字节一致 |
| 2 | `article_zero` | 🔴 proto3 **隐式 presence** 的零值**不落 wire** ⇒ 必须是**空字节** | ✅ 逐字节一致（两侧都是**空**） |
| 3 | `create_req_optional_present` | 🔴🔴 **显式 presence**：两个 `optional` 字段**显式设成空串也落 wire**（Go 侧 = **非 nil 的指针**；Java 侧 = `hasBrief()` 为真）⇒ 期望字节里有 `12 00` 与 `22 00` 两个「长度 0」标记 | ✅ 逐字节一致 |
| 4 | `create_req_optional_absent` | 🔴🔴 与第 3 条**同一组字段、只是没设过** ⇒ 那两个字段**一个字节都不落** ⇒ 两条的字节差 = **presence 标记本身** | ✅ 逐字节一致 |
| 5 | `create_req_full` | 请求消息全字段（含两个 optional 都设了非空值 ＋ `repeated string` 两条） | ✅ 逐字节一致 |
| 6 | `list_resp` | `repeated message`（两次 tag 1 的 length-delimited）＋ 分页 varint | ✅ 逐字节一致 |
| 7 | `upload_req` | 🔴 `bytes` 字段（**不是 string**）⇒ length-delimited 的**原始字节** | ✅ 逐字节一致 |

**回归网**：`ArticleProtoWireParityTest`（**7 例**，**住在 `article-rpc` 模块**）
⚠️ **为什么住在 article-rpc 而不是 article-proto**：`article-proto` 的 pom 里
**没有测试依赖** ⇒ 往那儿加测试要先改 pom ⇒ 放在**消费方**（它本就依赖 proto）。
⇒ 副作用：proto 组件从「**只有契约层守门**」（`ArticleProtoContractTest` 比**描述符**）
变成「**契约层 + 字节层**」两道守门。

**变异验证**（`tools/proto-wire-probe/article/mutate-wire.sh` ⇒ **EXIT=0**）：

| # | 变异打在哪 | 期望谁转红 | 实测 |
|---|---|---|---|
| M1 | 冻结的 `expected.txt` 的一枚 hex（`upload_req`） | 第 3 步的 Go 侧 diff | ✅ **exit 1 精确转红** |
| M2 | Go 探针 `go/main.go` 的一个取值（`article_full` 的 `LikeCount`） | 同上 | ✅ **exit 1 精确转红** ⇒ 证明 `expected.txt` 不是「谁都能写出来」的摆设 |
| M3 | **RAG 侧** `article.proto` 的**字段号**（`CreateArticleRequest.cover_image_url`：`4 → 8`） | 第 4 步的 Java 侧断言 | ✅ **exit 2 精确转红** |

⇒ 还原后重新转绿（exit 0）＋ 三个文件内容哈希**回到变异前**（sha256 **3/3**）⇒ **探针非假绿**。
⚠️ **M3 选的字段是 `optional`** ⇒ 这一条同时证明「**presence** 那条断言也有牙齿」。
⚠️ **M3 只改字段号、不改字段名** ⇒ 代码照常编译 ⇒ 红的原因**只可能**是字节不同
（改字段名会编译失败 ⇒ 那是另一种红，**证不了**「Java 侧在拿字节跟 Go 真源比」）。

### 8.1 ⚠️ 本节**没有**关闭什么（边界声明）

- **`P-U-1`（跨栈 gRPC 互调）仍 OPEN** ⇒ 本探针是**两侧各自序列化**，
  **不是**「Go article-rpc ⇄ RAG article-rpc 的真实跨栈调用」⇒ ⇒ **不构成 `P-U-1` 的证据**。
- **帧层**（HTTP2 / length-prefix / gRPC status 帧）仍未比对 ⇒ 那需要两侧同时起服务
  ⇒ 归 [`article/rpc.md`](./rpc.md) 与 [`article/api.md`](./api.md)。
- **`map` 只做单条目** ⇒ 两个运行时的 map 遍历顺序都**不保证**（Go 的 map 随机序、
  Java 的 `MapField` 未定义序）⇒ 多条目逐字节比对 = **假红**。

### 8.2 复现命令

```bash
# 🔴 消息体 wire 字节的双侧对拍（Go 真源生成码 vs article-proto 生成码）
bash tools/proto-wire-probe/article/run.sh
echo $?   # 0 = 7 组消息逐字节一致

# 🔴 变异验证：证明上面这条链**不是假绿**（M1/M2 打 Go 侧 diff · M3 打 Java 侧断言）
bash tools/proto-wire-probe/article/mutate-wire.sh
echo $?   # 0 = 3 条变异都精确转红 + 还原后转绿 + 三个文件内容哈希回到变异前
```
