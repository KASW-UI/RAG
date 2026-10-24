<!-- 由 `align/follow` 工作树产出 · 基线 commit 8422116 -->

# follow-api 组件级语义对照（Go ↔ RAG）

> **核对日期：2026-10-24** · 基线 commit：`8422116`
> **真源**：`Sea-RideTheWind_golang/service/follow/api/`（冻结，只读）
> **被审**：`service/follow/follow-api/`
> **对照方式**：Go 侧 24 个源文件逐行读 + **双侧活体对拍**（Go follow-api 与 RAG follow-api 同跑）

## 结论

**首轮组件级对照：发现 2 条 wire 级可观察漂移（均已修）**，其余语义层 `SEMANTICALLY EQUIVALENT`。
**双侧活体对拍：协议层 20 条 + 数据路径 16 条 + 边界/鉴权 16 条 = 52/52 逐字节一致。**

| # | 漂移 | 级别 | 状态 |
|---|---|---|---|
| DRIFT-FOLLOW-001 | 空列表出参 Go=`"user_ids":null` / RAG=`"user_ids":[]` | **可观察（L1 wire）** | ✅ 已修 |
| DRIFT-FOLLOW-005 | recommend 出参**字段顺序** Go=`target_id` 在前 / RAG=`mutual_score` 在前 | **可观察（L1 wire 字节序）** | ✅ 已修 |
| DRIFT-FOLLOW-007 | 指标文本层：多一个 `application` tag / 直方图**零桶** | 可观察（抓取侧） | ✅ **已修**（tag + 桶）· ⚠️ 残留见下（**框架限制**） |
| DRIFT-FOLLOW-008 | MaxConns 闸门：**每路由一个**（Go）vs **全局一个**（RAG） | 可观察（并发行为） | 🔴 **未修**（`service/common` 共享模块 ⇒ 派单） |
| DRIFT-FOLLOW-009 | 下游挂死时：Go **2s ⇒ 200+1015**；RAG **3s ⇒ 503 "Request Timeout"** | **可观察（状态码 + 时刻）** | ✅ 已修 |
| ~~OPEN-001~~ | ~~Content-Type 不支持 ⇒ RAG 415 / Go 不报错~~ | 可观察 | ✅ **已关闭（415 分支实测不可达）**，见下 |

> **2026-10-24 第二轮（未知项 U1–U8 收口）新增 3 条**，全部有**双侧活体对拍**证据：
> 指标（U1）、保护层四道（U2）、客户端熔断（U3）、超时级联（U4）、
> 推荐算法深层分支（U5）、JWT 边界（U6）、黑名单 fail-open（U7）、并发竞态（U8）。
>
> ⚠️ **U3 / U4 的首轮数字已作废并重测**（测量装置有缺陷：黑洞读完 preface 就 close
> ⇒ 测的是「连不上」；etcd 前缀下两个键 ⇒ Go 侧轮询）。
> **重建装置后的受控结论**：U4 ⇒ DRIFT-FOLLOW-009（已修）；
> **U3 ⇒ 两侧熔断等价，无漂移**（见下方「U3 受控重测」节）。

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
| 指标口径（3 个 counter + 1 个 histogram，label 名一致） | `api/internal/metrics/metrics.go` | `metrics/FollowApiMetrics.java` | ✅ 一致（**U1 已双侧抓取比对**；文本层残留见 DRIFT-FOLLOW-007） |

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

## 双侧活体对拍（52/52 逐字节一致）

| 组 | 条数 | 覆盖 |
|---|---|---|
| 协议层 | 20 | 鉴权(401 空 body) / 解析失败 / 类型不匹配 / 必填缺失 / 自关系(5201) / 越权(5005) / 404 body / 405 body / 幂等 / 分页 / limit=0 |
| 数据路径 | 16 | 关注 / 关注列表 / 重复关注 / 拉黑 / **已拉黑再关注(5202)** / 黑名单 / 取消关注 / 空列表(null) / 推荐 / 解除拉黑 / 解除不存在的拉黑 / 粉丝列表 |
| **边界 + 鉴权** | **16** | JWT claim 五种类型（number/string/bool/1.5/数组/超 int64）· `bearer` 小写前缀 · `Bearer` 无空格 · 空 Authorization · offset 越界 · limit 超上限(1000) · limit 负数 · offset 负数 · **Content-Type: text/plain** · 无 Content-Type · **多层推荐（2→3→4，score=20）** · 推荐带 limit |

两侧**共用同一个 PG**、**调用同一个下游 user-rpc**、**同一批探针脚本** ⇒ body 逐字节可比。

## DRIFT-FOLLOW-005 🔴 recommend 出参**字段顺序**与 Go 相反（已修）

**现象（双侧活体对拍抓到）**

```
Go  : {"users":[{"target_id":"4","mutual_score":20}]}
RAG : {"users":[{"mutual_score":20,"target_id":"4"}]}      ← 修复前，字段顺序反了
```

**根因：Jackson 在「属性全部显式命名」时按字母序排**

- Go 的 `encoding/json` 按 **struct 字段声明序**输出
  （`types.go:20-23`：`TargetId` 在前、`MutualScore` 在后）⇒ `target_id` 在前。
- Jackson 默认走「字段声明序」分支 —— **但当所有属性都带 `@JsonProperty` 显式命名时，
  落入按名字排序的分支** ⇒ `mutual_score` < `target_id`（字母序）⇒ 顺序反过来。
- 本类两个字段都带 `@JsonProperty`（为了 snake_case，Boot 4/Jackson 3 无全局 snake_case 策略）
  ⇒ 正好命中这条分支。

⚠️ **这不是语义差异**（JSON 对象无序），但它是 **wire 级字节差异** ——
做 body 快照比对 / 签名校验 / 严格字符串匹配的客户端会失配。

**修法**：`dto/RecommendUser.java` 加
`@JsonPropertyOrder({"target_id", "mutual_score"})`。

**受影响面核查**：follow 域其余出参 DTO 的字段数 ——
`FollowUserListResp`（1 个）、`RecommendListResp`（1 个）、`RelationActionResp`（1 个）
⇒ **单字段，无序问题**；envelope `Response`（`service/common`）**没有** `@JsonProperty`
⇒ 走声明序 ⇒ `code, msg, data` 与 Go 一致 ⇒ **不受影响**（对拍已逐字节确认）。

**本仓同款先例**：`like-rpc` 的 `LikeEventContracts`、`comment-rpc` 的 `CommentCacheService`
早已用 `@JsonPropertyOrder` 固定顺序 ⇒ **follow 域是漏网的一个**。

**防回归**：新增 `FollowApiMvcPipelineTest#recommendFieldOrderMatchesGo`（断言字节序列）。

---

## ~~OPEN-001~~ ✅ 已关闭：Content-Type 不支持 ⇒ 415 分支**实测不可达**

**原记录**：`GlobalExceptionHandler.java:189-210` 的注释把「Content-Type 不支持 ⇒ Spring 415」
写成「已知偏离（OBSERVABLE DIFFERENCE）」，并说「取舍与 message-api 一致：保留 Spring 默认」。

**本轮实测（2026-10-24 双侧活体对拍）结论：两侧一致，415 分支走不到。**

| 探针 | Go | RAG | 一致？ |
|---|---|---|---|
| 动作端点 + `Content-Type: text/plain` | 200 + `{code:5001}` | 200 + `{code:5001}` | ✅ |
| 动作端点 + **无** Content-Type | 200 + `{code:5001}` | 200 + `{code:5001}` | ✅ |
| list 端点 + `Content-Type: text/plain` | 200 + 正常流 | 200 + 正常流 | ✅ |

**原因**：共享的 `com.rag.common.http.GoStyleJsonBodyConverter`（扫到即生效）
已把「Go 是否读这个 body」的判据收进本模块的 MVC 管线 ⇒
非 `application/json` 时**按 Go 的规则丢弃 body**（而非让 Spring 判 415）
⇒ 4 个动作端点得到 200 + 5001（`target_id` 缺失）、4 个 list 端点按零值继续
—— **这正是 Go 的行为**。

⇒ 该 handler 是**防御性分支**（不可达），**不是**行为漂移 ⇒ 从 DRIFT 撤下，
不需签字。⚠️ 但 `GlobalExceptionHandler` 里那段「已知偏离」的注释仍写着 415 与 Go 不同
⇒ **该注释已过期**，应改为「本分支在 `GoStyleJsonBodyConverter` 生效时不可达」。
（注释修订属本域文件，**未改动** ⇒ 列入下方待办。）


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

## DRIFT-FOLLOW-009 🔴 下游挂死时的超时级联：Go 200+1015 vs RAG 503（已修）

**现象（U4：黑洞下游实测）**

把 follow-rpc 换成一个「**连上但不回话**」的黑洞 TCP（Go 侧改 etcd 的 `follow.rpc`，
RAG 侧改 `--grpc.client.follow-rpc.address=static://127.0.0.1:19999`），两侧同时打
`/follow/v1/follow/list`：

| | HTTP | body | 耗时 |
|---|---|---|---|
| **Go** | **200** | `{"code":1015,"msg":"service busy","data":null}` | **2.01s** |
| **RAG（修前）** | **503** | `Request Timeout\n` | **3.07s** |
| **RAG（修后）** | **200** | `{"code":1015,"msg":"service busy","data":null}` | **2.47s** ✅ |

**根因：Go 侧是「两层预算」，RAG 侧只有一层**

- Go 的 **ctx 预算 3s**：rest 超时中间件
  （`service/follow/api/followcenter.go:40-42` `DisableNativeRestTimeout` + `NewHTTPMiddleware`；
  `service/common/observability/http.go:53` `context.WithTimeout(r.Context(), timeout)`，
  `:67-68` `r = r.WithContext(ctx)`）。
- Go 的 **zrpc 客户端超时 2s**：`zrpc/config.go:34`
  `Timeout int64 `json:",default=2000"``（`RpcClientConf`；`etc/followcenter.yaml` 未配 ⇒ 走 default）。
  客户端拦截器做的是 `context.WithTimeout(ctx, c.Timeout)` ⇒ **两层取更小的生效** ⇒ 2s 先到。
- ⇒ Go 侧下游挂死时，**api 拿到的是 rpc 错误**（`DeadlineExceeded`）⇒ 走业务错误通道 ⇒
  **HTTP 200 + 业务码 1015**；**根本走不到** HTTP 超时分支。
- RAG 侧此前**只有 ctx 预算**（`GoStyleRequestDeadline.applyTo` 把 HTTP 超时的 3s 预算
  变成 gRPC deadline）⇒ 下游挂死时一路顶到 HTTP 超时分支 ⇒ **503 "Request Timeout"**。

**⚠️ 修前还观测到一个更糟的现象：响应自相矛盾**
U3 的突发测试里抓到过 **`HTTP 200` + body `Request Timeout`** ——
业务线程与超时线程抢同一个 response，状态码与 body 来自**不同分支**。
这在 Go 侧不可能出现（Go 只有一个 goroutine 写响应）。

**修法**：`follow-api` 的 `FollowRpcClient#followRpc()` 在 ctx 预算之上再压一层
**2s 客户端超时**（`CLIENT_TIMEOUT_MILLIS = 2000L`，注释逐字引用 Go 的 struct tag）。
⚠️ **没有 ctx 预算时也要套 2s** —— Go 的客户端超时是**拦截器级别**的，
与有没有 HTTP 超时中间件无关；否则就是「有时有客户端超时、有时没有」。

**证据**：`service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/FollowRpcClient.java`
（`CLIENT_TIMEOUT_MILLIS` + `capByClientTimeout`）；
实测：黑洞下游下 RAG 返回 `HTTP=200 elapsed=2.47s body={"code":1015,...}`。

## U3 受控重测：客户端熔断 ✅ **等价**（首轮结论已撤回）

**首轮结论作废的原因**：首轮 U3/U4 的**测量装置有两处缺陷**，导致测出来的不是目标现象：

| 缺陷 | 后果 |
|---|---|
| 黑洞读完 preface 就 **close** | 客户端看到「**连不上**」（`connection error`，0.0ms）⇒ 测的是「连接被拒」，不是「下游不响应」 |
| etcd 前缀 `follow.rpc/` 下同时挂着「真 rpc」与「黑洞」**两个键** | Go 侧在两个地址之间**轮询** ⇒ 一部分「快失败」其实是打到了真 rpc |

⇒ 首轮得到的「Go 5/5 快失败 / RAG 1/5 快失败」**不可用**。
（根因与教训 ⇒ [`docs/Pitfalls/follow/api.md`](../../Pitfalls/follow/api.md) 的
PITFALL-FOLLOW-011 / PITFALL-FOLLOW-012。）

**重建后的受控装置**

1. 黑洞 **v3**：**完成 HTTP/2 握手**（回 SETTINGS + SETTINGS ACK）后**不再回任何业务响应**
   ⇒ 客户端只能等到自己的 deadline ⇒ 失败码是 `DeadlineExceeded`（=「下游活着但不响应」）。
2. etcd 前缀 `follow.rpc/` 下**只留一个键**（`follow.rpc/blackhole`）⇒ 寻址确定。
3. 两侧 api **都刚重启** ⇒ 熔断窗口（10s）为空 ⇒ **起始状态相同**。
4. 同一串请求：**12 并发 × 2 轮**（第 2 轮前隔 3s，让第 1 轮的失败全部落进窗口）。

> ⚠️ **为什么必须是「并发」**：googlebreaker 有 `protection=5` 冷启动保护
> （`dropRatio = (total - 5 - weightedAccepts)/(total+1)`）
> ⇒ 窗口内失败数 ≤5 时 `dropRatio ≤ 0` ⇒ **一律放行，熔断打不开**。
> 串行请求每发 2s，10s 窗口里最多攒 4~5 次失败 ⇒ **永远打不开** ⇒ 必须并发一批。

**受控对照结果（同一装置、同一串请求、同一起点）**

| | Go | RAG | 一致？ |
|---|---|---|---|
| 第 1 轮（12 并发，`total=0`） | **12/12 放行**，每发 **2.0s** | **12/12 放行**，每发 **2.0s** | ✅ |
| 失败码 | `DeadlineExceeded` | `DEADLINE_EXCEEDED` | ✅ |
| 第 2 轮（`total≈12`） | **8/12 拒绝**（`circuit breaker is open`）+ 4/12 放行 | **5/12 拒绝**（`[grpc-breaker] dropped, circuit open`）+ 7/12 放行 | ✅ 形状一致 |
| 对外 body | `{"code":1015,"msg":"service busy","data":null}` | 同左 | ✅ 逐字节一致 |

⇒ **结论：等价，无漂移，不需要改动。**
第 2 轮拒绝比例 8/12 vs 5/12 的差异**不是差异** ——
熔断是**概率型**（`rand() < dropRatio`，此处 dropRatio ≈ 0.54），
逐条不可比，比的是**形状**（第 1 轮全放行 ⇒ 冷启动保护生效；第 2 轮混合 ⇒ 熔断已打开）。

**运行时证据（RAG 侧，熔断机制确实在跑）**

```
[grpc-breaker] failure, method=follow.FollowService/GetFollowList, code=DEADLINE_EXCEEDED   ×19
[grpc-breaker] dropped, circuit open, method=follow.FollowService/GetFollowList               ×5
```

⇒ 拦截器**装了**（`@GrpcGlobalClientInterceptor` 被 devh 的
`AnnotationGlobalClientInterceptorConfigurer` 收集；`com.rag.common` 在
`FollowApiApplication` 的 `scanBasePackages` 内）、失败**在计**、熔断**会打开**、拒绝**会快失败**
⇒ 三件事都有运行证据，不是「注解存在即认为生效」。

**共享实现与 Go 的逐条对照**（`service/common/.../GoStyleBreaker.java`）

| Go `googlebreaker.go` | RAG `GoStyleBreaker.java` | 一致？ |
|---|---|---|
| `window=10s` / `buckets=40` / 250ms 每桶 | `BUCKET_DURATION_MS=250` / `BUCKETS=40` | ✅ |
| `k=1.5` / `minK=1.1` / `protection=5` | `K=1.5` / `MIN_K=1.1` / `PROTECTION=5` | ✅ |
| `forcePassDuration=1s` | `FORCE_PASS_DURATION_MS=1_000` | ✅ |
| `dropRatio=(total-protection-weightedAccepts)/(total+1)` | 同式 | ✅ |
| `dropRatio *= (buckets-workingBuckets)/buckets` | 同式 | ✅ |
| `RollingWindow.Reduce` 带 `ignoreCurrent`（跳过当前半桶） | `history()` 跳过 `currentIdx` | ✅ |
| `failingBuckets`/`workingBuckets` 是「连续段长度」而非计数 | 同语义（遇反向归零） | ✅ |

⇒ **算法等价 + 运行时形状等价 ⇒ U3 关闭，无漂移。**

## DRIFT-FOLLOW-008 🔴 MaxConns 闸门：Go 每路由一个，RAG 全局一个（跨域待办）

**现象（U2：把两侧阈值都压到 1 后实测）**

| 场景（max-conns=1） | Go | RAG |
|---|---|---|
| A 占住 routeA 的名额后打**同**路由 | 503 | 503 ✅ |
| B 占住 routeA 的名额后打**异**路由 | **200** | **503** ❌ |
| C 释放后再打 | 200 | 200 ✅ |

**根因**

- Go：`rest/engine.go:146` 在 **`bindRoute` 的每条路由链里**各建一次
  `handler.MaxConnsHandler(ng.conf.MaxConns)`；`rest/handler/maxconnshandler.go:20`
  `latch := syncx.NewLimit(n)` ⇒ **每路由一个闸门**。
  ⇒ routeA 被慢请求占满时，**routeB 不受影响**。
- RAG：`com.rag.common.protection.GoStyleMaxConnsFilter` 是 **一个全局 Semaphore**
  ⇒ routeA 占满 ⇒ **routeB 也被连带拒绝**。

**可观察后果**：一个慢路由能把**其它所有路由**一起拖垮（503）。
Go 侧不会 —— 这是**并发行为层面的真实差异**，不是表述差异。

**为什么本轮没修**：实现位于 **`service/common` 共享模块**
（`GoStyleMaxConnsFilter`），而 `service/common` 被 10 个微服务共用
⇒ 按 `docs/Worktree.md`，**域外改动要交主会话派单**（并发写会撞）。
**修法一句话**：把 `GoStyleMaxConnsFilter` 的 Semaphore 改成
**按 `method://path` 分桶的 Map**（与 Go 的 `breaker.NewBreaker(WithName(method+"://"+path))`
同一思路，`GoStyleBreakerFilter` 已经是这么做的，可直接照抄它的 `breakers` map 结构）。

## DRIFT-FOLLOW-007 🟡 指标文本层：多一个 tag + 直方图零桶（已修 + 框架限制残留）

**现象（U1：两侧归零重测后抓 /metrics 对拍）**

- Go 侧出口：`go-zero` 的 **DevServer**（`internal/devserver/server.go:79` `StartAgent`，
  由 `zrpc/server.go:63` 的 `c.SetUp()` ⇒ `core/service/serviceconf.go:77` 触发）
  ⇒ `http://<host>:16065/metrics`。
- RAG 侧出口：`/actuator/prometheus`（`management.endpoints.web.exposure.include`）。

**修前的三处差异**

1. **多一个 `application="follow-api"` 标签**：Go 的 `Subsystem: "follow_api"`
   **直接变成指标名前缀**（`follow_api_request_total`），RAG 侧 meter 名**已带同一前缀**
   ⇒ 再挂一个 tag 是**重复信息**，而且它会**改变时间序列的身份**
   ⇒ 同一份 PromQL（`sum by (le)`）在两侧结果**不同**。
   ⚠️ 原注释写的「子系统在 Micrometer 侧对应这个 tag」是**搞错了**（Micrometer 没有 subsystem 概念，
   本项目已用**名字前缀**承载它）⇒ 已删除该 tag（`application.yml`）。
2. **直方图零桶**：Go 侧 `prometheus.NewHistogramVec(..., Buckets: prometheus.DefBuckets)`
   ⇒ 12 个 `le` 边界（`.005 .01 .025 .05 .1 .25 .5 1 2.5 5 10 +Inf`）；
   RAG 侧 `registry.timer(name, tags)` 走 registry 默认配置
   ⇒ Prometheus 文本格式里**只有 `_count`/`_sum`/`_max`，没有 `_bucket`**
   ⇒ **`histogram_quantile()` 在本服务侧完全不可用**（实测 `_bucket` 行数：Go 96 / RAG **0**）。
   ⇒ 已改用 `Timer.builder(...).publishPercentileHistogram().serviceLevelObjectives(GO_DEF_BUCKETS)`
   （`GO_DEF_BUCKETS` **逐字抄** Go 的 `prometheus.DefBuckets`）。
3. **rpc 侧的 `publishPercentiles()`**：那是**分位数**开关，
   Prometheus 文本格式**不支持 quantile**（只有 OpenMetrics 才有）⇒ 对着抓取端是无效配置
   ⇒ 已一并改成 `publishPercentileHistogram()`。

**修后核验（两侧归零 → 同一请求序列 → 抓取比对）**

- 计数器与 reject：**名字 + 标签集 + 值逐条一致**（60 条 series 中 46 条非直方图 series 全部相等）。
- 直方图：Go 的 **12 个 `le` 边界全部存在**（数值化对齐后逐条命中）。

**⚠️ 残留（**框架限制**，不是可修的语义漂移）——2026-10-24 已查到源码级根因**

| 残留 | Go | RAG | 性质 |
|---|---|---|---|
| 浮点字面量 | `1` | `1.0` | Prometheus Java client 的渲染 |
| `le` 标签值 | `le="1"` / `le="5"` / `le="10"` | `le="1.0"` / `le="5.0"` / `le="10.0"` | 同上 ⇒ **按标签值匹配的 PromQL 两侧不等价**（`le="1"` 在 RAG 侧匹配到 **0** 条 series） |
| 桶边界总数 | **12**（11 个数值 + `+Inf`） | **80**（Go 的 11 个数值全在 + Micrometer 默认桶 **68** 个 + `+Inf`） | Micrometer `DistributionStatisticConfig.getHistogramBuckets()` **强制并集** |
| 额外 series | — | `_max` | Prometheus Java client 专有 |

> ⚠️ **桶边界数是可复现的确定值**：Go 的 11 个数值边界
> （0.005 / 0.01 / 0.025 / 0.05 / 0.1 / 0.25 / 0.5 / 1 / 2.5 / 5 / 10）
> **逐个都在** RAG 侧（`le="1"` 例外，它是 `le="1.0"`），
> RAG 侧多出的 **68** 个是 Micrometer 默认桶（0.001 … 30）。
> ⇒ **Go 的 12 个 `le` 边界 100% 覆盖**（数值口径），
> 差异只在「**多出来的桶**」与「**同值的写法**」两处。

#### 三条限制的源码级边界（分属两个库 ⇒ 可修性不同）

| # | 限制 | 根因（源码） | 绕过 Micrometer 能修吗 |
|---|---|---|---|
| **A** | 桶边界被**强制并集**（80 ≠ 12） | `micrometer-core 1.16.7` `DistributionStatisticConfig.getHistogramBuckets(boolean)`：<br>`buckets.addAll(PercentileHistogramBuckets.buckets(this))`（默认桶）**与** `buckets.addAll(serviceLevelObjectives)`（SLO 桶）**进同一个 `TreeSet`** ⇒ 取并集，**无任何开关**能让 SLO 独占 | ✅ **能** |
| **B** | `le` 渲染成 `1.0`（Go 是 `1`） | `prometheus-metrics-exposition-textformats 1.4.3` `TextFormatUtil.writeDouble`：`writer.write(Double.toString(d))` ⇒ `1.0`→`"1.0"`；Go 的 `client_golang` 用 `strconv.FormatFloat(v,'g',-1,64)` ⇒ `"1"` | ❌ **不能**（client_java 的文本渲染，换谁都绕不开） |
| **C** | 多一个 `_max` series | `PrometheusMeterRegistry` 自己加的 family：`new MicrometerCollector.Family<>(conventionName + "_max", ...)` ⇒ **不是** client_java 的原生产物 | ✅ **能** |

⚠️ **A 的二选一**（实测：修复前 `_bucket` 行数 0，修复后 640）：
`percentileHistogram=true` ⇒ 80 个桶（并集）；`false` ⇒ **`_bucket` 完全不产生**
⇒ Micrometer 里**只有这两个状态**，不存在「只要 SLO 的 12 个」。

⚠️ **B 只影响 1 / 5 / 10 三个值**：其余 8 个（0.005 / 0.01 / 0.025 / 0.05 / 0.1 / 0.25 / 0.5 / 2.5）
的 `Double.toString` 与 Go 的 `%g` 同形 ⇒ 实测「Go 有而 RAG 没有的 le」**恰好就是这三个**。

⇒ **「彻底修法」能做到什么、做不到什么**：
用 client_java 原生 `Histogram`（`io.prometheus.metrics.core.metrics.Histogram$Builder`
的 `classicOnly()` + `classicUpperBounds(double...)`，**API 已 javap 确认存在**）
注册进**同一个** `PrometheusRegistry` ⇒
✅ 桶边界**恰好 12 个**（classic 模式无并集）、
✅ **不再有 `_max`**；
❌ **`le="1.0"` 依旧** ⇒ **抓取文本做不到逐字节一致**。

⇒ **对抓取侧的实际影响**（这决定了「残留值不值得修」）：
- `histogram_quantile(0.95, sum by (le) (rate(..._bucket[5m])))` ⇒ **数值口径可用**，两侧可聚合；
- 任何**按 `le` 字符串精确匹配**的查询（`le="1"`）⇒ 两侧不等价（RAG 侧匹配到 0 条）
⇒ ⇒ **建议把这类查询统一改成数值口径**，而不是为了它去改客户端库。

⇒ **待用户拍板的点已收窄**（原表述「要不要绕过 Micrometer」过宽）：
真正的问题是「**愿不愿意让这 2 个指标脱离 Micrometer 抽象**」——
代价：拿不到 Micrometer 的 tag 规范化 / `MeterFilter` 链 / `registry.getMeters()` 统一管理，
且 `le` 渲染问题**依然存在**（收益只有「桶数对齐 + 去掉 `_max`」）。
⇒ **本轮未改**（用户 2026-10-24 未拍板）。

> **🧾 第二轮复核（2026-10-24 · 最全性）**：补录「逐文件对照索引」——
> 此前「源码逐条对照（24 个 Go ↔ 27 个 Java）」这条断言**无法从台账里核对**
> （**11 个类从未出现过**，含 `FollowJwtAuthInterceptor`、`FollowApiProperties`、
> `FollowGoStyleCoercion`、2 个入参 DTO、`AbstractFollowApiService` 及 6 个业务 Service）
> ⇒ 现每个源文件都有落点；**实测覆盖率：Java 侧 27/27、Go 侧 24/24（另 4 类落在通配/生成物/死代码说明里）**。
> **🧾 台账 ↔ 代码复核（2026-10-24）**：本文件的每条状态断言已逐条回代码/真源核对 ——
> 「✅ 已修」5 条（001 / 005 / 007 / 009 + OPEN-001）**逐条在代码里坐实**；
> 「🔴 未修」2 条（006/008）**仍然成立**；
> 约 25 条 `file:line` 引用（Go 侧 / go-zero 侧 / RAG 侧）**逐条命中**；
> 复核中发现 5 处表述偏差已当场订正（对拍条数、文件计数、指标数值对拍状态等）。

## 逐文件对照索引（27 个 Java ↔ 24 个 Go，**全量无遗漏**）

> ⚠️ 2026-10-24 复核补录：此前台账只列了「承重」文件，
> 「源码逐条对照（24 个 Go 文件 ↔ 27 个 Java 文件）」这条断言**无法从台账里核对**
> （**11 个类从未出现过**）⇒ 现补全索引，**每个源文件都有落点**。
> 结论列的 ✅ 表示「该文件的行为已在双侧对照中核过」。

| # | RAG 源文件（follow-api，27） | Go 对应物（follow/api，24） | 承重点 | 结论 |
|---|---|---|---|---|
| 1 | `FollowApiApplication.java` | `followcenter.go` | main 的 8 件事逐条对应 | ✅ |
| 2 | `config/FollowApiProperties.java` | `internal/config/config.go`（`UserAuth` 段） | `accessExpire` 本模块不读（Go 同样不读） | ✅ |
| 3 | `config/FollowGoStyleCoercion.java` | `internal/types/types.go` + go-zero `core/mapping` | `,string` 强转（字符串/数字等价） | ✅ |
| 4 | `config/WebMvcConfig.java` | `internal/handler/routes.go` | **JWT 在外、黑名单在内**；8 端点全要 JWT | ✅ |
| 5 | `controller/FollowController.java` | `internal/handler/follow/*handler.go`（8 个） | 8 路由方法 + 路径逐条一致 | ✅ |
| 6 | `client/FollowRpcClient.java` | `internal/svc/servicecontext.go` + `zrpc.MustNewClient` | 无 retry；熔断默认开；**客户端超时 2s**（DRIFT-009） | ✅ |
| 7 | `dto/FollowListReq.java` | `internal/types/types.go` `FollowListReq` | 三字段 optional；offset 类型不匹配被拒 | ✅ |
| 8 | `dto/RelationActionReq.java` | `internal/types/types.go` `RelationActionReq` | `target_id` **不校验 >0**（Go 同样不校验） | ✅ |
| 9 | `dto/FollowUserListResp.java` | `internal/types/types.go` `FollowUserListResp` | **空 ⇒ `null`**（DRIFT-001） | ✅ |
| 10 | `dto/RelationActionResp.java` | `internal/types/types.go` `RelationActionResp` | 单字段 | ✅ |
| 11 | `dto/RecommendListResp.java` | `internal/types/types.go` `RecommendListResp` | 单字段 | ✅ |
| 12 | `dto/RecommendUser.java` | `internal/types/types.go` `RecommendUser` | **字段顺序** `target_id` 在前（DRIFT-005） | ✅ |
| 13 | `interceptor/FollowJwtAuthInterceptor.java` | `rest/handler/authhandler.go` + `rest/token/tokenparser.go` | 验签失败 = **401 + 空 body**；`handler instanceof HandlerMethod` ⇒ 404 不被鉴权遮蔽；Bearer 前缀大小写不敏感；jjwt ≥32 字节 vs Go 不校验长度 | ✅ |
| 14 | `interceptor/FollowBlacklistInterceptor.java` | `internal/middleware/checkblacklistmiddleware.go` | key 前缀 `user:jwt_blacklist:`；fail-open；401 body 无 `data` | ✅ |
| 15 | `advice/GlobalExceptionHandler.java` | `followcenter.go` `httpx.SetErrorHandler` | 业务错误 = **HTTP 200 + envelope**；415 分支实测不可达（OPEN-001） | ✅ |
| 16 | `advice/GlobalResponseAdvice.java` | `followcenter.go` `httpx.SetOkHandler` | 成功 = `{code:200,msg:"OK",data:<v>}` | ✅ |
| 17 | `metrics/FollowApiMetrics.java` | `internal/metrics/metrics.go` | 3 counter + 1 histogram；label 名一致；**直方图桶**（DRIFT-007） | ✅ |
| 18 | `service/FollowApiSupport.java` | `internal/logic/follow/helper.go` | `extractUserID` 五种类型；`resolveUserID` 回落 | ✅ |
| 19 | `service/AbstractFollowApiService.java` | 8 个 logic 共同的 `defer metrics.ObserveRequest` 骨架 | 计时 + 成败计数；**reject 埋点在 return 之前**（顺序不可颠倒） | ✅ |
| 20 | `service/FollowService.java` | `internal/logic/follow/followlogic.go` | 拉黑检查（5202）；错误码映射 | ✅ |
| 21 | `service/UnfollowService.java` | `internal/logic/follow/unfollowlogic.go` | **不**校验存在性 | ✅ |
| 22 | `service/BlockService.java` | `internal/logic/follow/blocklogic.go` | 插 block + 双向删 follow | ✅ |
| 23 | `service/UnblockService.java` | `internal/logic/follow/unblocklogic.go` | **不**校验存在性 | ✅ |
| 24 | `service/GetFollowListService.java` | `internal/logic/follow/getfollowlistlogic.go` | 列表归一化三段序 | ✅ |
| 25 | `service/GetFollowerListService.java` | `internal/logic/follow/getfollowerlistlogic.go` | 列表归一化三段序 | ✅ |
| 26 | `service/GetBlockListService.java` | `internal/logic/follow/getblocklistlogic.go` | **归属校验**（只能查自己） | ✅ |
| 27 | `service/GetRecommendationsService.java` | `internal/logic/follow/getrecommendationslogic.go` | **归属校验** + 推荐算法 6 条硬语义 | ✅ |

## 证据强度

| 项 | 强度 |
|---|---|
| 源码逐条对照（24 个 Go 文件 ↔ 27 个 Java 文件） | ✅ 双向静态 |
| 真启动（真 Tomcat + 真 Redis + 真 Nacos） | ✅ **真启动**（`Started FollowApiApplication`） |
| HTTP 行为 | ✅ **双侧活体对拍** **52/52** 逐字节一致（协议层 20 + 数据路径 16 + 边界/鉴权 16） |
| 指标数值 | ✅ **双侧抓取比对**（归零重测；计数器同名同标签同值；直方图 Go 的 12 个 le 边界全在 ⇒ 残留见 DRIFT-FOLLOW-007） |
| 保护层四道（MaxConns/MaxBytes/CPU shedding/breaker） | ✅ **双侧活体实测**（阈值压到 1 后对拍）；breaker 为**受控并发对照**（见 U3 节） |
| 下游挂死的超时级联 | ✅ **双侧活体实测**（黑洞 v3：完成 HTTP/2 握手后挂住） |
| JWT 边界（过期/错签/alg=none/畸形/无 exp） | ✅ **双侧活体实测**（9 种逐字节一致） |
| 黑名单 fail-open | ✅ **双侧活体实测**（`docker stop Redis`） |
| 并发竞态（Follow × Block） | ✅ **双侧活体实测**（各 40 轮）⇒ **两侧共有缺陷**，记 Pitfalls 不记 DRIFT |

## 受控故障注入环境（可复现）

| 组件 | Go 侧 | RAG 侧 |
|---|---|---|
| api | `:18893`（`/tmp/go-follow-api -f /tmp/gofollow/followcenter.yaml`） | `:18896`（`java -cp <runtime classpath> FollowApiApplication`） |
| rpc | `:18092`（`/tmp/go-follow-rpc`） | `:18282`（`java -jar follow-rpc-0.0.1-SNAPSHOT.jar`） |
| 下游 user-rpc | 隔离 stub `/tmp/userstub`（`:19210`，实现 Go 真源 `GetUser`，恒 `found=true`） | 同左（同一进程，两边都调它） |
| PG / Redis / etcd / Nacos | `55450` / `56381` / `49329` / `8848`（容器 `followprobe-*`） | 同左 |
| **故障注入（黑洞）** | `127.0.0.1:19999`（`/tmp/blackhole3.py`：**完成 HTTP/2 握手后挂住**） | 同左 |

**接线（两侧必须完全对称，这是本轮翻车的根源）**

- Go 侧寻址走 **etcd 前缀 `follow.rpc/`** ⇒ 实验期间**该前缀下只能有一个键**
  （`follow.rpc/blackhole` → `127.0.0.1:19999`）。
  ⚠️ 裸键 `follow.rpc` **解析不到任何地址**（`makeKeyPrefix` = `key + '/'`）
  ⇒ 表现为 `produced zero addresses` + 立即 1015，**极易与熔断拒绝混淆**。
- RAG 侧用启动参数覆盖：`--grpc.client.follow-rpc.address=static://127.0.0.1:19999`。
- 两侧 api **都刚重启** ⇒ 熔断窗口（10s）为空 ⇒ 起始状态相同。

**探针脚本**

| 脚本 | 用途 |
|---|---|
| `/tmp/u3d_probe.sh <base> <label>` | **并发**口径（12 并发 × 2 轮）⇒ 测熔断的「打开」行为 |
| `/tmp/u3c_probe.sh <base> <label>` | **串行**口径（12 发 × 0.3s 间隔）⇒ 测超时级联 |
| `/tmp/probe.sh` / `probe_data.sh` / `probe_ext.sh` | 协议层 20 + 数据路径 16 + 边界 16（52/52 逐字节对拍） |
| `/tmp/cmp.sh` | 两侧同库复位 + 逐行 diff |

> ⚠️ **三个「假绿」陷阱**（详见 [`docs/Pitfalls/follow/api.md`](../../Pitfalls/follow/api.md)）：
> 探针固定 `-o` 文件 ⇒ 失败时 body 是上次残留（PITFALL-FOLLOW-010）；
> 黑洞不完成 HTTP/2 握手 ⇒ 测的是「连不上」（PITFALL-FOLLOW-011）；
> etcd 裸键 ⇒ 零地址伪装成熔断拒绝（PITFALL-FOLLOW-012）。

## 待办

1. ~~修订 `GlobalExceptionHandler` 里那段过期的「已知偏离（415）」注释~~ ——
   ✅ **已修**（2026-10-24，commit `d50750e`）：注释已改为「本分支在 `GoStyleJsonBodyConverter`
   生效时不可达 + 何时会重新变成偏离 + 守住它的回归网是哪两条测试」。
2. **DRIFT-FOLLOW-008（MaxConns 每路由 vs 全局）需派单到 `service/common`** ——
   实现位于共享模块，本域不得改（并发写会撞）。修法见该条目。
3. **DRIFT-FOLLOW-007 的框架限制残留：拍板点已收窄，待用户定** ——
   残留分三条（详见该条目）：**A 桶数并集**（Micrometer 层，可修）、
   **B `le="1.0"` 渲染**（client_java 层，**绕过 Micrometer 也修不掉**）、
   **C 多一个 `_max`**（Micrometer 层，可修）。
   ⇒ 「彻底修法」= 这 2 个指标改用 client_java 原生 `Histogram`
   （`classicOnly()` + `classicUpperBounds(...)`）⇒ **收益只有「桶数对齐 12 + 去掉 `_max`」**，
   `le` 写法问题依旧，且代价是脱离 Micrometer 抽象
   （tag 规范化 / `MeterFilter` 链 / `registry.getMeters()` 统一管理都拿不到）。
   ⇒ **本轮未改**（用户未拍板）。
   ⚠️ **对抓取侧的影响**：数值口径的 `histogram_quantile()` 两侧可用；
   **按 `le` 字符串精确匹配的查询两侧不等价** ⇒ 建议改数值口径，而不是为它改客户端库。
4. **DRIFT-FOLLOW-006（follow-rpc 指标无出口）：用户 2026-10-24 定 ⇒ 本轮不落地**，
   转「**全仓 rpc 层统一方案**」交主会话拍板（调查结论见 `docs/DRIFT/follow/rpc.md`）。
5. 🔴 **共享索引已过时 ⇒ 交主会话更新**（`AGENTS.md:32` 与 `docs/DRIFT/_INDEX.md:32,85`
   仍写 follow「零台账 / 仅 proto 层」）—— 详见 [`rpc.md` 的待办 6](./rpc.md)。
6. `follow-api` **没有 `spring-boot-maven-plugin`** ⇒ 打不出可执行 fat jar，
   只能用 `java -cp <runtime classpath> <MainClass>` 启动。
   与 `article-api` / `comment-api` / `hot-api` / `like-api` / `message-api` / `task-api` 同款
   （只有 `favorite-api` 有）⇒ 属**打包形态**问题，不是 Go↔RAG 语义漂移，**未改动**。
7. ⚠️ **故障注入装置应从 `/tmp` 提升为仓库资产** —— 本轮 U3/U4 的结论**反转了两次**，
   根因都是**装置不对**（黑洞不完成 HTTP/2 握手 / etcd 前缀下多个键 / 探针固定 `-o` 文件）。
   ⇒ 建议把下面三个脚本固化进 `tools/difftest/fault-harness/`（**本轮未做**，
   因为它们是**跨域**资产，放进 `tools/` 需主会话拍板）：

   | 脚本（当前只在 `/tmp`） | 作用 |
   |---|---|
   | `/tmp/blackhole3.py` | 黑洞 v3：**完成 HTTP/2 握手**后挂住 ⇒ 模拟「下游活着但不响应」 |
   | `/tmp/u3d_probe.sh` | 受控并发探针：12 并发 × 2 轮，比对熔断的「形状」 |
   | `/tmp/u3c_probe.sh` | 受控串行探针（用于测超时级联） |

   ⇒ 配套的**两条纪律**（已写进 [`docs/Pitfalls/follow/api.md`](../../Pitfalls/follow/api.md)）：
   ① 探针的 body 文件**每次请求前必须清空**，且比对前**断言没有 `HTTP=000`**；
   ② 故障装置必须**自证**（裸 HTTP/2 客户端跑一遍握手，确认收到 SETTINGS + SETTINGS ACK）。
