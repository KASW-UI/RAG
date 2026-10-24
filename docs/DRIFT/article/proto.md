<!-- @new 2026-10-24 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

# DRIFT 台账 · `service/article/article-proto`

> **本文件是 38 组件覆盖矩阵里 `article` 域最后一个无专属台账的组件** ——
> 此前 `article-proto` 只被 AGENTS.md §3.4 的「proto 是复制粘贴」一条覆盖，
> 而 §3.4 **效力边界第 1 条**明写：「复制忠实 **≠** 消费方式已验」。
>
> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树，从 `do-my-own-gh` 切出）。
> 证据强度三档：**生成产物实测** · **源码逐行对照（两侧 + 框架消费点）** · **未做活体对拍**。

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
⇒ **两侧判 presence 的写法必须一一对应**，否则「字段没传」与「字段传了空串」会混淆。

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
- ⚠️ **本组件的证据强度上限 = 源码逐行对照 + 生成产物实测 + 单侧真 PG**；
  **没有**跨栈活体对拍（`P-U-1`）⇒ 按 §6 状态表纪律，
  本文件所有 ✅ 一律读作「**在源码级对照 + 单侧真 PG 证据下一致**」，不是「已闭环」。
