<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-api 组件级语义对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> **真源**：`Sea-RideTheWind_golang/service/follow/api/`（冻结，只读）
> **被审**：`service/follow/follow-api/`
> **对照方式**：Go 侧 24 个源文件逐行读 + **双侧活体对拍**（Go follow-api 与 RAG follow-api 同跑）

## 结论

**首轮组件级对照：发现 1 条 wire 级可观察漂移（已修）**，其余语义层 `SEMANTICALLY EQUIVALENT`。
**双侧活体对拍：协议层 20 条 + 数据路径 16 条 = 36/36 逐字节一致。**

| # | 漂移 | 级别 | 状态 |
|---|---|---|---|
| DRIFT-FOLLOW-001 | 空列表出参 Go=`"user_ids":null` / RAG=`"user_ids":[]` | **可观察（L1 wire）** | ✅ 已修 |
| OPEN-001 | Content-Type 不支持 ⇒ RAG 415 / Go 不报错（按零值继续） | 可观察 | 🔴 **待签字**（见下） |

## DRIFT-FOLLOW-001 🔴 空列表出参：Go 是 `null`，RAG 是 `[]`（已修）

**现象（双侧活体对拍抓到）**

```
Go  : {"code":200,"msg":"OK","data":{"user_ids":null}}      ← 空关注列表 / 空粉丝列表
RAG : {"code":200,"msg":"OK","data":{"user_ids":[]}}         ← 修复前
```

**根因：漏了中间那次 protobuf 编解码**

Go 侧的 `rpcResp.UserIds` 是**反序列化之后**的切片：

```
follow-rpc 进程内： UserListResp{UserIds: make([]int64, 0, 0)}   ← 非 nil 空切片
     │  protobuf wire：proto3 的 repeated 为空 ⇒ 字段整体不上 wire
     ▼
follow-api 进程内： rpcResp.UserIds == nil                       ← Go 反序列化得 nil
     │  encoding/json（tag 无 omitempty）
     ▼
HTTP body：         {"user_ids":null}
```

⚠️ **RAG 原注释的判断错在哪**：`FollowUserListResp` 的 javadoc 写
「rpc 侧是非 nil 空切片 ⇒ 输出 `[]`」—— 那是 **rpc 进程内**的值，
**中间隔着一次 protobuf wire**（proto3 空 repeated ⇒ 字段缺失 ⇒ 客户端得 nil）。
⇒ 只读源码看不出来，**必须双侧活体对拍**才能抓到。

**修法**

1. `service/FollowApiSupport.java#toUserListResp`：`resp.getUserIdsCount() == 0` 时
   `out.setUserIds(null)`（非空才填 list）。
2. `service/dto/FollowUserListResp.java`：字段不再初始化为空 list，**允许为 null**。
3. ⚠️ **同步改掉一条「锁死错误答案」的测试断言**
   （AGENTS.md §3.3 点名的形态）：
   `FollowApiMvcPipelineTest#userIdsAreSerializedAsNumbers` 原断言
   `"user_ids":[]` ⇒ 改为 `"user_ids":null`。
   ⇒ **先改断言、再改实现**（§3.3 规则：否则会先撞上"测试红了"而误以为自己改错了）。

**修后实测**：Go ↔ RAG 数据路径 16/16 逐字节一致 ✅

**不受影响的对称项**：`RecommendListResp.users` 在 Go 侧是
`users := make([]types.RecommendUser, 0, len(rpcResp.Users))`（`getrecommendationslogic.go:70-81`）
—— **在 api 进程内重新 make 的**，恒非 nil ⇒ 输出 `[]`。
⇒ RAG 侧也输出 `[]` ✅ **一致，不改**（对拍实测确认）。

## 业务语义层：零漂移（逐条对照 + 活体对拍）

| 维度 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| 8 个路由（方法 + 路径） | `api/internal/handler/routes.go:16-64` | `controller/FollowController.java:97-145` | ✅ 一致（全 POST） |
| 前缀 `/follow/v1` | `rest.WithPrefix("/follow/v1")` | `@RequestMapping("/follow/v1")` | ✅ 一致 |
| **8 个端点全部要 JWT**，无 public 路径 | `rest.WithJwt(...)` 挂在整段 AddRoutes 上 | `config/WebMvcConfig.java:73-79`（`/follow/v1/**`，无 exclude） | ✅ 一致 |
| 中间件顺序：JWT 在外、黑名单在内 | `rest/engine.go` chain 顺序 | 同上注册顺序 | ✅ 一致 |
| 黑名单 fail-open（Redis 故障 ⇒ 放行） | `middleware/checkblacklistmiddleware.go:41` | `interceptor/FollowBlacklistInterceptor.java:150-161` | ✅ 一致 |
| 黑名单 key 前缀 `user:jwt_blacklist:` | 同上 `:39` | `:105` | ✅ 一致 |
| 黑名单 401 body = `{"code":5004,"msg":"unauthorized"}`（**无 data**） | 同上 `:42-48` | `:169-179` | ✅ 一致 |
| 验签失败 = **401 + 空 body** | `rest/handler/authhandler.go:106` | `advice/GlobalExceptionHandler.java:91-95` | ✅ 一致 |
| 业务错误 ⇒ **HTTP 200 + envelope** | `api/followcenter.go:53-66`（`httpx.SetErrorHandler`） | `GlobalExceptionHandler.java:112-116` | ✅ 一致 |
| 成功 ⇒ `{code:200,msg:"OK",data:<v>}` | `followcenter.go:45-51` | `advice/GlobalResponseAdvice.java:65-77` | ✅ 一致 |
| 未注册路径 ⇒ 404（**不被鉴权遮蔽**） | go-zero router 404 | `handler instanceof HandlerMethod` 判据 | ✅ 一致（body `404 page not found` 逐字节相同） |
| GET 打到 POST 路由 ⇒ 405（裸状态码、无 body） | go-zero patrouter | `GoStyleRouterErrorResponses.methodNotAllowed` | ✅ 一致（body 空） |
| `extractUserID`：json.Number / string / int64 / int / float64 五种类型 | `api/internal/logic/follow/helper.go:13-33` | `service/FollowApiSupport.java:104-143` | ✅ 一致 |
| `resolveUserID`：requested ≤ 0 ⇒ 回落当前用户 | `helper.go:43-47` | `FollowApiSupport.java:162-167` | ✅ 一致 |
| **归属校验只加在 2 个端点**（block/list + recommendations） | `getblocklistlogic.go:46-50` / `getrecommendationslogic.go:46-50` | `service/GetBlockListService.java` / `GetRecommendationsService.java` | ✅ 一致（关注列表 / 粉丝列表**可查他人**，故意保留的不对称） |
| 失败时用 rpc 回传的 msg，不查本地码表 | `NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg)` | `FollowApiSupport.requireRpcSuccess` | ✅ 一致 |
| `,string` int64 入参**同时接受字符串与数字** | go-zero `mapping` 的 `json.Number` Kind==String | `users-common` 的 `GoStyleStringInt64` | ✅ 一致（见下） |
| gRPC client 无 retry | `zrpc.RpcClientConf` 无 `Retries` 字段 | `client/FollowRpcClient.java` | ✅ 一致 |
| gRPC client 熔断默认开 | `zrpc/internal/config.go:14` `Breaker default=true` | `GoStyleGrpcClientBreakerInterceptor` | ✅ 一致 |
| 指标口径（3 个 counter + 1 个 histogram，label 名一致） | `api/internal/metrics/metrics.go` | `metrics/FollowApiMetrics.java` | ✅ 一致（数值未对拍） |

### `,string` 入参语义的实测核对（go-zero v1.10.2 真源）

Go 的 `httpx.Parse` → `ParseJsonBody` → `mapping.UnmarshalJsonReader`（**不是**标准 `encoding/json`），
所以 `,string` 的行为与标准库**相反**：

| 输入 | Go v1.10.2 | RAG | 一致？ |
|---|---|---|---|
| `{"target_id": 123}`（JSON 数字） | ✅ 接受 | ✅ 接受 | ✅ |
| `{"target_id": "123"}`（JSON 字符串） | ✅ 接受 | ✅ 接受 | ✅ |
| `{"target_id": "1.5"}` / `1.5` / `1e2` / `1.0` | ❌ 拒绝（`ParseInt` 失败） | ❌ 拒绝 | ✅ |
| `{"target_id": true}` | ❌ 拒绝 | ❌ 拒绝 | ✅ |
| `{"target_id": null}` | ❌（必填校验拒绝） | ❌ | ✅ |

依据：`go-zero@v1.10.2/core/mapping/utils.go:376-377`（`string` option ⇒ `fieldOpts.FromString = true`）
+ `unmarshaler.go:888-896`（`valueKind != reflect.String` 才报错；`json.Number` 的 Kind **就是** String）
+ `unmarshaler.go:1115-1122`（`case string` / `case json.Number` 都走 `ParseInt`）。
RAG 侧实现：`service/users/users-common/.../GoStyleStringInt64.java`（与 message-api 的同名类逐行一致）。

## 双侧活体对拍（36/36 逐字节一致）

| 组 | 条数 | 覆盖 |
|---|---|---|
| 协议层 | 20 | 鉴权(401 空 body) / 解析失败 / 类型不匹配 / 必填缺失 / 自关系(5201) / 越权(5005) / 404 body / 405 body / 幂等 / 分页 / limit=0 |
| 数据路径 | 16 | 关注 / 关注列表 / 重复关注 / 拉黑 / **已拉黑再关注(5202)** / 黑名单 / 取消关注 / 空列表(null) / 推荐 / 解除拉黑 / 解除不存在的拉黑 / 粉丝列表 |

两侧**共用同一个 PG**、**调用同一个下游 user-rpc**、**同一批探针脚本** ⇒ body 逐字节可比。

## OPEN-001 🔴 Content-Type 不支持：RAG 415 / Go 不报错（待签字）

**现象**：请求带 `Content-Type: text/plain`（但 body 是合法 JSON）⇒
RAG 走 Spring 消息转换器 ⇒ **HTTP 415**；Go 侧 `withJsonBody` 只判 Content-Type **是否包含**
`application/json`，不含时**不报错**，走 `UnmarshalJsonMap(nil, v)`：
- 4 个动作端点（有必填 `target_id`）⇒ 必填校验失败 ⇒ **200 + 5001**
- 4 个 list 端点（三字段全 optional）⇒ 按零值继续 ⇒ 正常业务流

`GlobalExceptionHandler.java:189-210` 的注释已把这条写成「已知偏离（OBSERVABLE DIFFERENCE）」，
并说明取舍与 message-api 一致。

⚠️ **但它没有经过签字** ⇒ 按 `docs/Worktree.md` 的判据
（「这条差异要不要『修回去』？要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations」），
**未经签字的偏离必须留在 DRIFT 档**，否则后来者会把它当决策、拒绝修正。
⇒ 本轮登记为 **DRIFT OPEN**，请用户签字后再迁到 `docs/Intentional_Deviations/follow/api.md`。
（签字后的处理方式：把 415 改成「按 Go 规则丢弃 body」，与 `GoStyleJsonBodyConverter` 的既有行为统一。）

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（24 个 Go 文件 ↔ 23 个 Java 文件） | ✅ 双向静态 |
| 真启动（真 Tomcat + 真 Redis + 真 Nacos） | ✅ **真启动**（`Started FollowApiApplication`） |
| HTTP 行为 | ✅ **双侧活体对拍** 36/36 逐字节一致 |
| 指标数值 | ⚠️ 未对拍 |

## 待办

1. **OPEN-001 待签字**（415）。
2. `follow-api` **没有 `spring-boot-maven-plugin`** ⇒ 打不出可执行 fat jar，
   只能用 `java -cp <runtime classpath> <MainClass>` 启动。
   与 `article-api` / `comment-api` / `hot-api` / `like-api` / `message-api` / `task-api` 同款
   （只有 `favorite-api` 有）⇒ 属**打包形态**问题，不是 Go↔RAG 语义漂移，**未改动**。
