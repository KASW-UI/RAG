# Component Contract — `article-api`

> 结构对称要求：`go-semantic-model.md` 与 `springboot-semantic-model.md` 使用**完全相同**的章节骨架，
> 以免「某一侧没写某个维度」被误读成「该维度不适用」。

---

## 1. Purpose

对外提供 7 个文章相关的 HTTP 操作。其中 4 个是登录用户对**自己**的文章的写操作与作者视角读操作（需 JWT），
3 个是公开读操作（读详情、列表、图片上传）。

组件本身**不持有业务数据**，不直接访问数据库 / 对象存储；它做的是：
1. 路由与鉴权边界判定；
2. 输入解释（path / query / JSON / multipart）；
3. 把解释结果翻译成 3 个 gRPC 下游的请求；
4. 把下游的 gRPC status 翻译成 15 选 1 的业务码；
5. 按本域特有的响应形状（6 个信封 + 1 个裸响应）序列化出去。

---

## 2. Public Operations

| # | Method | Path | 鉴权 | 输入通道 | 输出形状 |
|---|---|---|---|---|---|
| OP-1 | POST | `/v1/article` | **JWT** | JSON body | `200 + {code,msg,data}` |
| OP-2 | PUT | `/v1/article/{id}` | **JWT** | path + JSON body | `200 + {code,msg,data}` |
| OP-3 | DELETE | `/v1/article/{id}` | **JWT** | path | `200 + {code,msg,data}` |
| OP-4 | GET | `/v1/me/article/{id}` | **JWT** | path | `200 + {code,msg,data}` |
| OP-5 | GET | `/v1/article/{id}` | 公开 | path + query | `200 + {code,msg,data}` |
| OP-6 | GET | `/v1/articles` | 公开 | query（8 个） | `200 + {code,msg,data}` |
| OP-7 | POST | `/v1/upload` | 公开 | multipart（字段名 `image`） | `200 + 裸 {image_url}`（**不走信封**） |

### 2.1 每个 Operation 的契约摘要

**OP-1 CreateArticle**
- Purpose：创建一篇文章，返回新文章 id
- Input：JSON body `{title*, brief, content*, cover_image_url, manual_type_tag, secondary_tags}`（`*` = Go tag 无 `,optional` ⇒ 必填）
- External Call：`ArticleService/CreateArticle`，参数 `title` / `brief`(恒 set) / `markdown_content` / `cover_image_url`(恒 set) / `manual_type_tag` / `secondary_tags` / `author_id`=当前登录者
- Side Effect：写文章、触发发布流水线
- 特殊：**两个失败分支携带 `data.article_id`**（1003 / 1004），id 从 gRPC status 文案最后一个冒号之后取出

**OP-2 UpdateArticle**
- Purpose：更新自己的一篇文章
- Input：path `id` + JSON body `{title, brief, content, cover_image_url, manual_type_tag, secondary_tags, status}`（**全部 `,optional`**）
- External Calls：**两次** — 先 `GetArticle(incr_view=false, requester_id=me)` 做鉴权读，再 `UpdateArticle`
- Side Effect：写文章
- 特殊：可选字段**仅在非空串时下发**；`status` 仅在 `!= 0` 且是已知枚举值时下发

**OP-3 DeleteArticle**
- Purpose：删除自己的一篇文章
- Input：path `id`
- External Calls：**两次** — `GetArticle`（鉴权读）→ `DeleteArticle`
- Side Effect：软删文章
- 特殊：成功时 `success` **恒为 true**（不存在 `success=false` 的响应）

**OP-4 GetAuthorArticle**
- Purpose：作者读自己的文章（可读草稿）
- Input：path `id`（`incr_view` 复用同一个 Go struct 但被忽略）
- External Call：`GetArticle(requester_id=me)`，`public_only=false`、`incr_view=false`
- Side Effect：无
- 特殊：**不拦非 PUBLISHED**；归属不符 → 1006

**OP-5 GetArticle（公开读详情）**
- Purpose：公开读一篇已发布文章
- Input：path `id` + query `incr_view`
- External Call：`GetArticle(public_only=true, incr_view=<query>)`
- Side Effect：`incr_view=true` 时下游给阅读量 +1
- 特殊：**安全闸门** — 状态非 PUBLISHED 一律 1002（不泄露「存在但未发布」）

**OP-6 ListArticles（公开列表）**
- Purpose：分页列出已发布文章
- Input：query `manual_type_tag` / `secondary_tag` / `related_game_id` / `author_id` / `page`(默认 1) / `page_size`(默认 20) / `sort_by`(默认 `create_time`) / `desc`(默认 true)
- External Call：`ListArticles(public_only=true, 4 个筛选字段恒以 optional 形式下发)`
- Side Effect：无
- 特殊：**任一行非 PUBLISHED ⇒ 整条请求 5001**（不是跳过该行）；已 append 的行全部丢弃

**OP-7 UploadImage**
- Purpose：上传一张图片，返回可访问 URL
- Input：multipart，字段名固定 `image`
- External Call：`ArticleService/UploadFile(content, file_name=原始 filename 不清洗)`
- Side Effect：写对象存储
- 特殊：**成功返回裸 `{"image_url":…}`；失败返回 400 text/plain**（业务码 5004 只进日志，不进响应）

---

## 3. Inputs（共同维度）

| 通道 | 出现于 | 解释规则（两侧必须一致的部分） |
|---|---|---|
| path `{id}` | OP-2/3/4/5 | 原样字符串，不校验格式 |
| query 标量 | OP-5/6 | 空值**整体丢弃**（等价于缺席）；bool 真值表 `1/0/true/false`（大小写不敏感）；整数 base-10、32 位；String 原样透传（含空白） |
| query 默认值 | OP-6 | 只在 key **缺席**时填；`?page=abc` 是 400 而非回落默认 |
| JSON body | OP-1/2 | 读 body 的前提是 `Content-Length > 0` **且** `Content-Type` 含子串 `application/json`；否则**丢弃整个 body** 当空对象处理 |
| JSON 必填 | OP-1 | `title` / `content` 必填；缺席 → `field "x" is not set`；显式 null → `field "x" mustn't be nil`；类型不符 → `type mismatch for field "x"` |
| JSON `,optional` | OP-1/2 | **显式 null 被接受**（不同于 `,omitempty`） |
| JSON 未知字段 | OP-1/2 | 忽略 |
| JWT | OP-1/2/3/4 | `Authorization` 头；`Bearer ` 前缀**大小写不敏感且可省略**；验签 HS256 + `exp` |

---

## 4. Outputs（共同维度）

**信封结构**（OP-1~6）：`{"code": int, "msg": string, "data": <T|null>}`，HTTP **恒 200**（连 1002/1005/1006/5001 也是 200）。

**业务码表**（15 个，`service/article/common/errmsg/errmsg.go`）：
`200 OK` / `500 FAIL` / `1015 服务繁忙` / `1001 文章已存在` / `1002 文章不存在` / `1003 文章处理中，请稍后查看` / `1004 文章发布失败` / `1005 请先登录` / `1006 只能编辑或删除自己的文章` / `5001 系统内部错误` / `5002 更新数据库失败` / `5003 查询数据库失败` / `5004 文件上传失败` / `5005 文件删除失败` / `5006 文件下载失败`。
未知码回落 `500 → "FAIL"`。

**`Article` 投影的 null/缺席契约**：
| 字段 | 零值时的 wire 形态 |
|---|---|
| `author_name` | **key 整个消失**（Go `,omitempty`） |
| `secondary_tags` | `"secondary_tags": null`（nil slice，无 omitempty） |
| `ext_info` | `"ext_info": null`（空 map 在 Go 侧被克隆成 nil，无 omitempty） |

**非信封的响应形状（两侧各 3 种 + 1 种上传专用）**：

| 形状 | 触发 | Go 来源 |
|---|---|---|
| `400` + `text/plain; charset=utf-8` + `nosniff` + 文案 + 尾换行 | 解析失败 / 上传下游失败 | `http.Error`（`httpx.doHandleError` 的 `handler==nil` 分支） |
| `401` + **空 body**、无 Content-Type | JWT 缺失/无效 | `authhandler.unauthorized` 只 `WriteHeader(401)` |
| `404` + `404 page not found\n` + nosniff | 未注册路径 | `http.NotFound` |
| `404` + **空 body**、无 Content-Type、**无 Allow** | 路径命中但方法不匹配 | `cors.NotAllowedHandler`（`WithCors` 覆盖了 go-zero 默认的 405） |
| `413` / `503` / `204` | 保护层 / 超时 / CORS 预检 | `w.WriteHeader(...)` 裸写 |

---

## 5. Dependencies（外部基础设施）

| 依赖 | 用途 | 失败是否阻断 | 寻址 |
|---|---|---|---|
| `article-rpc` | 6 个端点的全部业务数据 | **是**（除作者昵称外） | Go: etcd key `article.rpc`；Java: Nacos `discovery:///article-rpc` |
| `user-rpc` | **仅**作者昵称补全（`GetUser`） | **否** — 四条失败分支全部回落 `用户 <id>` / `未知作者`，绝不冒泡 | Go: etcd key `user.rpc`；Java: `discovery:///user-rpc` |
| `security-rpc` | **Go 侧配置了但零消费者** | — | Go: etcd key `security.rpc`；Java: **刻意不建 client** |
| etcd / Nacos | 服务发现 | 是（启动期） | |
| PostgreSQL / MinIO / Kafka | **不在本组件范围**（属于 article-rpc） | — | |

⚠️ **依赖失败是否被伪装成别的结果**：本文档重点核查过——**没有**。
`user-rpc` 失败伪装成「昵称回落」是**有意的**（两侧同构，且 Go 侧同样如此）；
`article-rpc` 的每个失败分支都被显式翻译成业务码，不存在「RPC error 被当成空结果」的情况。

---

## 6. State

**组件内无可变状态。**

| 项 | Go | Java |
|---|---|---|
| 请求级 deadline | `r.Context()`（observability 中间件注入 70s） | `GoStyleRequestDeadline` 的 `ThreadLocal<Long>`（`HttpMiddleware` 注入，`begin`/`clear` 配对） |
| 跨请求缓存 | 无 | 无 |
| 客户端连接 | `zrpc.MustNewClient` 创建，启动期常驻 | devh `@GrpcClient` 注入，启动期创建 |
| 熔断器状态 | `breaker.NewBreaker(method+path)` 每路由一个 | `GoStyleBreaker` 每路由一个 |

唯一的「状态转换」是 `user-rpc` 失败时的**回落**：`(尝试查昵称) → (得到 "用户 1001")`，两条侧四条分支完全一致。

---

## 7. Side Effects

| Side Effect | 触发条件 | 位置 |
|---|---|---|
| 文章阅读量 +1 | OP-5 且 `incr_view` 解析为 true | 透传给下游，两侧一致 |
| 写文章 | OP-1 / OP-2 / OP-3 | 透传给下游 |
| 写对象存储 | OP-7 | 透传给下游 |
| 熔断器计数 | 每次请求（HTTPLayer breaker） | 两侧同构 |

**关键约束**：`user-rpc` 的调用**不得**产生可观察的副作用失败 —— 两条侧都把它做成「永不冒泡」。

---

## 8. Failure Model（统一形状表）

| Failure | HTTP | Body | 是否走信封 |
|---|---|---|---|
| JWT 缺失 / 签名错 / 过期 / token 格式错 | **401** | 空 | 否 |
| JWT 通过但无 `userId` claim（或空串 / `"<nil>"`） | **200** | `{code:1005}` | 是 |
| 必填 JSON 字段缺席 | **400** | `field "x" is not set\n` | 否 |
| 必填 JSON 字段显式 null | **400** | `field "x" mustn't be nil\n` | 否 |
| JSON 字段类型不符 | **400** | `type mismatch for field "x"…\n` | 否 |
| query bool/int 值非法 | **400** | `type mismatch for field "x"` / ParseInt 错误 | 否 |
| 请求体 > 10 MiB（Content-Length） | **413** | 空 | 否 |
| 请求体 > 8 MiB 且是 JSON | **400** | `string: …, error: unexpected EOF` | 否 |
| multipart 缺 `image` 字段 | **400** | `http: no such file` | 否 |
| 上传下游 RPC 失败 | **400** | `rpc call UploadFile failed: …` | 否（5004 只进日志） |
| 未注册路径 | **404** | `404 page not found\n` | 否 |
| 路径命中、方法不匹配 | **404** | **空**（Go）/ 空（Java，但状态码 405） | 否 |
| 并发连接 > 10000 | **503** | 空 | 否 |
| CPU > 90% | **503** | 空 | 否 |
| 熔断打开 | **503** | 空 | 否 |
| 整体超时（70s） | **503** | `Request Timeout\n` | 否 |
| panic / 未预期异常 | **500** | 空 | 否 |
| 下游 gRPC 各类 code | **200** | `{code,msg,data}` | 是 |
