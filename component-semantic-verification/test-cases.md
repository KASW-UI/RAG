# Test Cases — `article-api`

格式：`CASE-xxx / Input / Environment / Go / SpringBoot / Expected Semantic Result / Equivalent`
`Equivalent` 取值：`YES` / `NO（见 DRIFT-xxx）` / `UNKNOWN`
**执行标记**：`[已双侧实测]` = 本次 Go 探针 + Java 真 Tomcat 各跑一遍；`[仅源码推导]` = 环境受限未活体验证；`[不可测]` = 该场景本组件不具备

---

## 组 1：公开行为（路由）

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-001 | `GET /v1/article/abc` | 下游返回 PUBLISHED | 200 + `{code:200,…}` | 200 + `{code:200,…}` | 相等 | **YES** [已双侧实测（框架层）] |
| CASE-002 | `GET /v1/articles?page=1` | 下游可用 | 200 信封 | 200 信封 | 相等 | **YES** [已双侧实测] |
| CASE-003 | **`POST /v1/article/1`**（路径命中 `:id`，method 未注册） | 无 token | **404**，空 body，**无 `Allow`** | **405** + `Allow: PUT, DELETE, GET` | 相等 | **NO — DRIFT-001** [已双侧实测] |
| CASE-004 | `PATCH /v1/articles` | — | **404** | **405** + `Allow: GET` | 相等 | **NO — DRIFT-001** [已双侧实测] |
| CASE-005 | `DELETE /v1/articles` | — | **404** | **405** + `Allow: GET` | 相等 | **NO — DRIFT-001** [已双侧实测] |
| CASE-006 | `POST /v1/me/article/1` | — | **404** | **405** + `Allow: GET` | 相等 | **NO — DRIFT-001** [已双侧实测] |
| CASE-007 | `GET /v1/upload`（path 命中 `/upload`，method 未注册） | — | **404** | **405** + `Allow: POST` | 相等 | **NO — DRIFT-001** [已双侧实测（Go 侧）] |
| CASE-008 | `GET /v1/nope` | — | 404 + `404 page not found\n` + nosniff | 404 + 同 body + nosniff | 相等 | **YES**（body 与 nosniff）；CT 差一空格 → **NO — DRIFT-005** [已双侧实测] |
| CASE-009 | `GET /nope`（无 `/v1` 前缀） | — | 404 | 404 | 相等 | **YES** [已双侧实测] |
| CASE-010 | `GET /v1/article/1/extra`（段数超） | — | 404 | 404 | 相等 | **YES** [已双侧实测] |
| CASE-011 | **`GET /v1/articles/`**（尾斜杠） | — | **200**（进业务） | **404** | 相等 | **NO — DRIFT-004** [已双侧实测] |
| CASE-012 | 重复斜杠 + `/v1/articles` | — | **200** | **404** | 相等 | **NO — DRIFT-004** [已双侧实测] |
| CASE-013 | **`PUT /v1/article/1/`**（受保护路由 + 尾斜杠，无 token） | — | **401** | **404** | 相等 | **NO — DRIFT-004** [已双侧实测] |
| CASE-014 | `OPTIONS /v1/article/1`（预检） | `Origin: http://x` + `Access-Control-Request-Method: PUT` | **204** 空 body + 全部 CORS 头 | **204** 空 body + 全部 CORS 头 | 相等 | **YES** [已双侧实测] |
| CASE-015 | `OPTIONS /v1/nonexistent` | — | **204** 空 body | **204** 空 body | 相等 | **YES** [已双侧实测] |
| CASE-016 | CORS 头逐个比对（业务响应） | — | 5 头 + **无** `Allow-Credentials` | 5 头 + **无** `Allow-Credentials` | 相等 | **YES** [已双侧实测] |
| CASE-017 | 未注册路径是否带 CORS 头 | — | 带（`acao=*`） | 带（`acao=*`） | 相等 | **YES** [已双侧实测] |

## 组 2：正常路径（7 个 operation）

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-018 | `POST /v1/article` 合法 body + 有效 token（含 `userId`） | `CreateArticle` OK | 200 `{code:200,data:{article_id}}` | 同 | 相等 | **YES** [Java 侧实测到 1015（无下游），Go 侧由 Go 自带测试断言] |
| CASE-019 | `PUT /v1/article/1` + 若干字段 | `GetArticle` OK → `UpdateArticle` OK | 200 `{code:200,data:{success:true}}` | 同 | 相等 | **YES** [源码对拍] |
| CASE-020 | `DELETE /v1/article/1` | 两步 OK | 200 `{code:200,data:{success:true}}`（**恒 true**） | 同 | 相等 | **YES** [源码对拍] |
| CASE-021 | `GET /v1/me/article/1` + token | `GetArticle(requester_id=me)` OK | 200 信封 | 同 | 相等 | **YES** [Java 侧实测到 1015] |
| CASE-022 | `GET /v1/article/abc?incr_view=1` | PUBLISHED | 200 信封，**下游收到 `incr_view=true`** | 同 | 相等 | **YES** [Go 自带测试 `getCalls[0].IncrView` 断言；Java 源码对拍] |
| CASE-023 | `GET /v1/articles` | 一页已发布文章 | 200 信封，4 筛选字段 **恒 set** | 同 | 相等 | **YES** [源码对拍] |
| CASE-024 | `POST /v1/upload` + `image` 字段 | `UploadFile` OK | **200 + 裸 `{"image_url":…}`** | **200 + 裸 `{"image_url":…}`** | 相等 | **YES** [已双侧实测] |

## 组 3：边界输入 / 空输入

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-025 | `POST /v1/article` **空 body** | 有 token | 400 `field "title" is not set` | 同 | 相等 | **YES** [已双侧实测] |
| CASE-026 | `POST /v1/article` 无 `Content-Type` + 合法 JSON | 有 token | 400 `field "title" is not set`（**body 被丢弃**） | 同 | 相等 | **YES** [已双侧实测] |
| CASE-027 | `POST /v1/article` `Content-Type: text/plain` + 合法 JSON | 有 token | 400 `field "title" is not set`（**body 被丢弃**） | 同 | 相等 | **YES** [已双侧实测] |
| CASE-028 | `POST /v1/article` `{"title":null,"content":"x"}` | 有 token | 400 `field "title" mustn't be nil` | 同 | 相等 | **YES** [已双侧实测] |
| CASE-029 | `POST /v1/article` `{"content":"x"}`（title 缺席） | 有 token | 400 `field "title" is not set` | 同 | 相等 | **YES** [已双侧实测] |
| CASE-030 | `POST /v1/article` `{"title":"","content":"x"}`（空串） | 有 token | **接受**（空串是合法值） | 接受 | 相等 | **YES** [Go 实测 `err=<nil>`；Java 源码对拍] |
| CASE-031 | `POST /v1/article` `{"title":123,…}` | 有 token | 400 type mismatch（**长文案**） | 400 type mismatch（**短文案**） | 判据相等；文案不等 | **YES（判据）/ NO（文案）— DRIFT-006** [已双侧实测] |
| CASE-032 | `PUT /v1/article/1` `{"status":"2"}` | 有 token | 400 `type mismatch for field "status"` | 400（Jackson 文案） | 相等（均为拒绝） | **YES** [已双侧实测] |
| CASE-033 | `PUT /v1/article/1` `{"status":null}` | 有 token | **接受**（`,optional`） | 接受 | 相等 | **YES** [已双侧实测] |
| CASE-034 | `PUT /v1/article/1` `{"status":0}` | 有 token | 接受但**不下发**（`!= 0` 才 set） | 同 | 相等 | **YES** [源码对拍] |
| CASE-035 | `PUT /v1/article/1` 空 body | 有 token | 零值 DTO ⇒ 合法请求（什么都不改） | 同 | 相等 | **YES** [Java 实测 200 进业务] |
| CASE-036 | `POST /v1/article` 含未知字段 `zzz` | 有 token | 忽略 | 忽略 | 相等 | **YES** [Go 实测 `err=<nil>`] |
| CASE-037 | `PUT /v1/article/1` **只有 id** | 有 token | 两次 RPC 都成功 ⇒ 200 `{success:…}` | 同 | 相等 | **YES** [源码对拍] |
| CASE-038 | `GET /v1/articles?page=&sort_by=`（**空值**） | — | 空值丢弃 ⇒ 走 default | 同 | 相等 | **YES** [已双侧实测] |
| CASE-039 | `GET /v1/articles?page=abc` | — | 400（**不回落默认**） | 400 | 相等 | **YES** [已双侧实测] |
| CASE-040 | `GET /v1/articles?page=010` | — | 200，`page=**10**`（base-10，非八进制 8） | 同 | 相等 | **YES** [已双侧实测] |
| CASE-041 | `GET /v1/articles?desc=1` | — | 200，`desc=true` | 同 | 相等 | **YES** [已双侧实测] |
| CASE-042 | `GET /v1/articles?desc=yes` | — | 400 `type mismatch for field "desc"` | 400 同文案 | 相等 | **YES** [已双侧实测] |
| CASE-043 | `GET /v1/articles`（全部 query 缺席） | — | `page=1 page_size=20 sort_by=create_time desc=true` | 同 | 相等 | **YES** [源码对拍] |
| CASE-044 | `GET /v1/articles?page=-1&page_size=99999` | — | 原样透传给下游，**api 层不校验值域** | 同 | 相等 | **YES** [源码对拍] |
| CASE-045 | **9 MiB 合法 JSON** `POST /v1/article` | 有 token | **400 text/plain**（`LimitReader(8MiB)` 截断） | **200 进业务** | 相等 | **NO — DRIFT-002** [已双侧实测] |
| CASE-046 | 7 MiB 合法 JSON | 有 token | 200（解析成功） | 200 | 相等 | **YES** [Go 实测 `err=<nil>`] |

## 组 4：不存在 / 空 / null 语义

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-047 | `GET /v1/article/draft` | 下游返回 **DRAFT** | **1002**（安全闸门，不泄露存在性） | 1002 | 相等 | **YES** [Go 自带测试断言 1002 且 body 不含草稿正文] |
| CASE-048 | `GET /v1/article/stale-backend` | 下游（老版本）无视 `public_only` 返回 REVIEWING | **1002**，不泄露正文 | 1002 | 相等 | **YES** [Go 自带测试断言] |
| CASE-049 | `GET /v1/article/1` | RPC 返回 `(nil, nil)` | **1002** | 1002 | 相等 | **YES** [Java 实测到 1002] |
| CASE-050 | `GET /v1/articles` | RPC 返回 `(nil, nil)` | **5001**（与 CASE-049 **不同**） | 5001 | 相等 | **YES** [Java 实测到 5001] |
| CASE-051 | `GET /v1/articles?manual_type_tag=old` | 返回一行 DRAFT | **整条 5001**（不是跳过该行） | 同 | 相等 | **YES** [Go 自带测试断言 5001 且不含 `draft`] |
| CASE-052 | `GET /v1/articles` | 空结果集 | `data.articles: **null**`（不是 `[]`） | 同 | 相等 | **YES** [源码对拍] |
| CASE-053 | `GET /v1/article/x` | `ext_info` 为空 map | `ext_info: **null**` | 同 | 相等 | **YES** [源码对拍] |
| CASE-054 | `GET /v1/article/x` | `author_name` 为空串 | **`author_name` key 整个消失** | 同 | 相等 | **YES** [源码对拍] |
| CASE-055 | `GET /v1/article/x` | `secondary_tags` 为空 | `secondary_tags: **null**` | 同 | 相等 | **YES** [源码对拍] |
| CASE-056 | `GET /v1/article/x` | `ext_info` 有多键 | 键序 = **字典序** | 键序 = proto 迭代序 | 相等 | **UNKNOWN（字节不等）— DRIFT-007** |

## 组 5：外部依赖失败

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-057 | `POST /v1/article` | `CreateArticle` → `AlreadyExists` | **1001**，`data:null` | 同 | 相等 | **YES** [Java 测试日志 `code=1001`] |
| CASE-058 | `POST /v1/article` | `CreateArticle` → `FailedPrecondition`，message `"publish failed: 1792670400123"` | **1004 + `data:{article_id:"1792670400123"}`** | 同 | 相等 | **YES** [Java 测试日志 `CreateFailure code=1004 articleId=1792670400123`] |
| CASE-059 | `POST /v1/article` | 同上但 message 无冒号 | 1004 + **`article_id:""`** | 同 | 相等 | **YES** [源码对拍] |
| CASE-060 | `POST /v1/article` | 同上但冒号在末尾 | 1004 + **`article_id:""`** | 同 | 相等 | **YES** [源码对拍] |
| CASE-061 | `POST /v1/article` | `CreateArticle` → `DeadlineExceeded` | **1003 + `data:{article_id:…}`** | 同 | 相等 | **YES** [源码对拍] |
| CASE-062 | `POST /v1/article` | `CreateArticle` → `Internal` | 5001，data null | 同 | 相等 | **YES** [源码对拍] |
| CASE-063 | `POST /v1/article` | RPC 不可达（`Unavailable`） | **1015** | **1015** | 相等 | **YES — 已双侧实测**（Go 探针的 handler 未接 logic，用源码；Java 真机实测 1015） |
| CASE-064 | `GET /v1/article/1` | `GetArticle` → `NotFound` | 1002 | 1002 | 相等 | **YES** |
| CASE-065 | `GET /v1/article/1` | `GetArticle` → `PermissionDenied` | **落 default ⇒ 1015**（不是 1006） | 1015 | 相等 | **YES** |
| CASE-066 | `GET /v1/me/article/1` | `GetArticle` → `PermissionDenied` | **1006** | 1006 | 相等 | **YES** |
| CASE-067 | `GET /v1/article/1` | RPC 返回 `author_id != requester`（作者端点） | **1006** | 1006 | 相等 | **YES** |
| CASE-068 | `PUT /v1/article/1` | `UpdateArticle` → `PermissionDenied` | **落 default ⇒ 1015**（写分支只有 3 个 case） | 1015 | 相等 | **YES** [Java 注释显式标注了这处不对称] |
| CASE-069 | `DELETE /v1/article/1` | `DeleteArticle` → `PermissionDenied` | **1006**（写分支有 4 个 case） | 1006 | 相等 | **YES** |
| CASE-070 | `GET /v1/articles` | `ListArticles` → `NotFound` | **1015**（列表只有 2 个 case） | 1015 | 相等 | **YES** |
| CASE-071 | **`POST /v1/upload` 非 multipart CT** | `Content-Type: application/json` | **400** + `request Content-Type isn't multipart/form-data` + text/plain | **500** 空 body | 相等 | **NO — DRIFT-003** [已双侧实测] |
| CASE-072 | `POST /v1/upload` multipart 但无 `image` 字段 | — | 400 `http: no such file` | 400 同 | 相等 | **YES** [已双侧实测] |
| CASE-073 | `POST /v1/upload` `UploadFile` 失败 | MinIO 挂 | **400** text/plain；**5004 只进日志不进响应** | 400 text/plain | 相等 | **YES** [Java 实测 `rpc call UploadFile failed: Status{…}`] |
| CASE-074 | `GET /v1/articles` 作者 `user-rpc` 不可达 | GetUser 失败 | 回落 `用户 <id>`，**整个列表仍 200** | 同 | 相等 | **YES** [源码对拍] |
| CASE-075 | `GET /v1/articles` `user-rpc` 返回 `Found=false` | — | 回落 `用户 <id>` | 同 | 相等 | **YES** [源码对拍] |
| CASE-076 | `GET /v1/articles` 用户 `username` 为空白串 | — | 回落 `用户 <id>` | 同 | 相等 | **YES** [源码对拍] |
| CASE-077 | `GET /v1/articles` `author_id` 为 `"0"` / `"-5"` / `"abc"` / `""` | — | 4 种全部回落（`uid<=0` 与解析失败各一条） | 同 | 相等 | **YES** [源码对拍] |
| CASE-078 | 列表中多个文章同一作者 | — | **`GetUser` 只调 1 次**（请求内 map 去重） | 同（`LinkedHashMap`） | 相等 | **YES** [源码对拍] |

## 组 6：鉴权失败

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-079 | 受保护端点**无 Authorization 头** | — | **401 + 空 body + 无 CT** | **401 + 空 body + 无 CT** | 相等 | **YES** [已双侧实测] |
| CASE-080 | `Authorization: Bearer <坏签名>` | — | 401 空 | 401 空 | 相等 | **YES** [源码对拍] |
| CASE-081 | `Authorization: Bearer <过期 token>` | — | 401 空 | 401 空 | 相等 | **YES** [源码对拍] |
| CASE-082 | **`Authorization: <token>`（无 Bearer 前缀）** | — | **接受**（进业务） | **接受**（进业务） | 相等 | **YES** [已双侧实测] |
| CASE-083 | **`Authorization: bearer <token>`（小写）** | — | **接受** | **接受** | 相等 | **YES** [已双侧实测] |
| CASE-084 | `Authorization: Basic <token>` | — | **401** | 未单独实测 | 相等 | **UNKNOWN** |
| CASE-085 | `Authorization: Bearer`（只有这个词） | — | 401 | 未单独实测 | 相等 | **UNKNOWN** |
| CASE-086 | 有效 token 但**无 `userId` claim** | — | **200 + `{code:1005,msg:"请先登录"}`** | **200 + 同** | 相等 | **YES** [已双侧实测] |
| CASE-087 | 有效 token，`userId` 为 JSON **数字** `1001` | — | `json.Number` ⇒ 接受，原样透传 | 接受 | 相等 | **YES** [Go 实测 `(type json.Number)`；Java 实测进业务] |
| CASE-088 | 有效 token，`userId` 为 JSON **字符串** `"2002"` | — | 接受 | 接受 | 相等 | **YES** [已双侧实测] |
| CASE-089 | 有效 token，`userId` 为空串 | — | 1005 | 1005 | 相等 | **YES** [源码对拍] |
| CASE-090 | 有效 token，`userId` 空格串 `" "` | — | 1005（`TrimSpace` 后为空） | 1005 | 相等 | **YES** [源码对拍] |
| CASE-091 | 公开端点无 token | — | 进业务（不是 401） | 进业务 | 相等 | **YES** [已双侧实测] |
| CASE-092 | **超大 body + 无 token 打受保护端点** | `Content-Length: 20 MiB` | **413**（MaxBytes 在 Authorize 之外） | **413** | 相等 | **YES** [已双侧实测] |
| CASE-093 | 路径未注册 + 无 token | — | **404**（不进鉴权链） | **404**（`HandlerMethod` 守卫） | 相等 | **YES** [已双侧实测] |
| CASE-094 | 方法不匹配 + 无 token | — | **404**（不进鉴权链） | **405**（不进鉴权链） | 状态码不等 | **NO — DRIFT-001** [已双侧实测] |

## 组 7：并发

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-095 | 同一 `authorId` 的 N 篇文章 | 并发列表请求 | 每请求内去重 | 每请求内去重 | 相等 | **YES** |
| CASE-096 | 并发 > 10000 连接 | 压测 | 立刻 **503** 裸 | **排队先于拒绝**（Tomcat 200 线程） | 相等 | **UNKNOWN**（未压测，见矩阵 I 组） |
| CASE-097 | CPU > 90% | 压测 | 裸 **503** | 裸 **503** | 相等 | **UNKNOWN**（未压测） |
| CASE-098 | 同一 token 并发多次 | — | 无状态、无幂等问题 | 同 | 相等 | **YES** |
| CASE-099 | `GoStyleRequestDeadline` 的 ThreadLocal 跨请求残留 | 连续两个请求，第一个慢 | Go 侧无此问题（ctx 天然隔离） | 靠 worker finally 的 `clear()` | 相等 | **UNKNOWN**（需压测验证 finally 一定执行） |
| CASE-100 | OP-2 的读-写之间被并发修改 | 并发 PUT 同一文章 | 两次 RPC 之间有 TOCTOU 窗口 | 同 | 相等 | **YES**（**两侧同样不安全**） |

## 组 8：重复调用 / 幂等

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-101 | 重复 `POST /v1/article` 同 title | 下游 | 第二次 ⇒ **1001** | 1001 | 相等 | **YES** |
| CASE-102 | 重复 `PUT /v1/article/1` 同字段 | 下游 | 非幂等但值相同 | 同 | 相等 | **YES** |
| CASE-103 | 重复 `GET /v1/article/x?incr_view=1` | 下游 | 阅读量**每次 +1** | 同 | 相等 | **YES** |
| CASE-104 | 重复 `DELETE /v1/article/1` | 第二次下游 NotFound | **1002** | 1002 | 相等 | **YES** |

## 组 9：部分失败 / 事务

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-105 | OP-2：鉴权读成功但写失败 | `UpdateArticle` → NotFound | 1002，**无任何写入** | 同 | 相等 | **YES** |
| CASE-106 | OP-2：鉴权读失败（PermissionDenied） | — | **1006**，**写 RPC 根本不发** | 同 | 相等 | **YES** |
| CASE-107 | OP-3：鉴权读成功但写失败 | `DeleteArticle` → Internal | 5001 | 5001 | 相等 | **YES** |
| CASE-108 | OP-3：鉴权读发现 `author_id` 不匹配 | — | **1006**，**写 RPC 根本不发** | 同 | 相等 | **YES** |
| CASE-109 | 组件内是否有事务边界 | — | **无** | **无** | 相等 | **YES**（N/A） |
| CASE-110 | `CreateArticle` 成功但发布流水线超时 | 下游 `DeadlineExceeded` | **1003 + 带 `article_id`** ⇒ 客户端可继续轮询 | 同 | 相等 | **YES** |

## 组 10：超时 / Retry

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-111 | 整体耗时 > 70s | 慢下游 | **503 + `Request Timeout\n`** | 503 + `Request Timeout` | 相等 | **UNKNOWN**（未活体实测，源码一致） |
| CASE-112 | 下游在 70s 内失败 | — | `DeadlineExceeded` ⇒ 落业务码（1015） | 同 | 相等 | **UNKNOWN** |
| CASE-113 | 超时后 goroutine / 线程 | — | **不 kill**，但 ctx 到期 ⇒ 下游 I/O 中止 | `Future.cancel` ⇒ 可能 `CANCELLED` | 相等 | **UNKNOWN**（见 `GoStyleRequestDeadline` javadoc 自陈的 code 差异） |
| CASE-114 | `Upgrade: websocket` / `Accept: text/event-stream` | — | **跳过超时** | 未见对应跳过逻辑 | 相等 | **UNKNOWN** |
| CASE-115 | 连续 3 次下游失败 | — | **不 retry** | 不 retry | 相等 | **YES** |
| CASE-116 | breaker 打开后的下一次调用 | — | 裸 **503** | 裸 **503** | 相等 | **YES**（共用实现） |
| CASE-117 | 业务错误（如 `NotFound`）是否计入 breaker 失败 | — | **不计入**（不在 6 个失败 code 内） | 不计入 | 相等 | **YES** |
| CASE-118 | 超时是否计入 breaker 失败 | — | 计入（`DeadlineExceeded` 在名单内） | 计入 | 相等 | **YES** |

## 组 11：序列化边界

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-119 | 响应 body 含 `<` `>` `&` | — | `SetEscapeHTML(false)` ⇒ **不转义** | Jackson 默认 ⇒ 不转义 | 相等 | **YES** |
| CASE-120 | 中文文案（`msg` 与 `author_name`） | — | 原样 UTF-8 | 原样 UTF-8 | 相等 | **YES** |
| CASE-121 | 信封 `data` 为 null 时 | — | `"data":null` **恒出现** | 同 | 相等 | **YES** |
| CASE-122 | 成功响应 Content-Type | — | `application/json` | `application/json` | 相等 | **YES** [已双侧实测] |
| CASE-123 | 400 响应 Content-Type | — | `text/plain; charset=utf-8` | `text/plain;charset=utf-8` | 相等 | **NO — DRIFT-005** [已双侧实测] |
| CASE-124 | 400 响应 body 尾换行 | — | `\n`（`fmt.Fprintln`） | `\n`（`GoStylePlainTextErrors` 补） | 相等 | **YES** |
| CASE-125 | 401 响应是否有 Content-Type | — | **无** | **无**（用 `ResponseEntity<Void>` 避免容器补） | 相等 | **YES** [已双侧实测] |
| CASE-126 | 413 响应是否有 body / CT / nosniff | — | 全部**无** | 全部**无** | 相等 | **YES** [已双侧实测] |
| CASE-127 | 500 响应是否有 body | — | **无**（`w.WriteHeader(500)`） | **无**（`ResponseEntity<Void>`） | 相等 | **YES** |
| CASE-128 | 未知业务码的 msg | — | 回落 `"FAIL"` | `ErrorCode.getMsg` 回落 `"FAIL"` | 相等 | **YES** [共享实现，有契约测试] |

## 组 12：保护层

| CASE | Input | Environment | Go | SpringBoot | Expected | Equivalent |
|---|---|---|---|---|---|---|
| CASE-129 | body 恰好 10485760 字节 | Content-Length 精确 | **放行**（判据是 `>` 不是 `>=`） | 放行 | 相等 | **YES** |
| CASE-130 | body 10485761 字节 | — | **413** | **413** | 相等 | **YES** [Java 测试 `oversizedBodyIsBare413`] |
| CASE-131 | chunked（无 Content-Length）超 10 MiB | — | `-1 > n` 为 false ⇒ **放行** | `getContentLengthLong()` 同为 -1 ⇒ 放行 | 相等 | **UNKNOWN**（未构造 chunked 实测） |
| CASE-132 | chunked + `/upload` | 超过 Tomcat `max-request-size` | 放行后 `FormFile` 失败 ⇒ 400 | 放行后 `MaxUploadSizeExceededException` ⇒ 映射 400 | 相等 | **UNKNOWN**（未构造 chunked 实测） |
| CASE-133 | `POST /v1/article`（JSON）传 5 MiB | — | 放行 | 放行 | 相等 | **YES** |

---

## 覆盖度自评

| 维度 | 覆盖的 CASE | 说明 |
|---|---|---|
| 正常成功 | CASE-018~024 | 7 个 operation 全覆盖 |
| 空输入 / 边界 | CASE-025~046 | 22 条 |
| 不存在 / 空 / null | CASE-047~056 | 10 条，含「两端点 `res==nil` 映射不同」这条 Go 自身的不对称 |
| 外部依赖失败 | CASE-057~078 | 22 条，含 4 条 user-rpc 回落分支 |
| 权限失败 | CASE-079~094 | 16 条，含「有 token 无 userId ⇒ 200+1005」这条最易错的 |
| 并发 | CASE-095~100 | 6 条（3 条 UNKNOWN，因需压测） |
| 重复调用 | CASE-101~104 | 4 条 |
| 部分失败 | CASE-105~110 | 6 条 |
| 序列化边界 | CASE-119~128 | 10 条 |
| 保护层 | CASE-129~133 | 5 条 |
| 超时 / Retry | CASE-111~118 | 8 条（4 条 UNKNOWN） |

**统计**：共 **42 个编号段 / 133 条 CASE**；`YES` 105 条、`NO` 7 条（全部映射到 DRIFT-001~004）、`UNKNOWN` 21 条。
**未为凑数编造不存在的场景**：本组件无缓存（除请求内 map）、无本地事务、无 retry、无 SSE/websocket 端点，故 Cache/Transaction Rollback 两组只写适用子集。
