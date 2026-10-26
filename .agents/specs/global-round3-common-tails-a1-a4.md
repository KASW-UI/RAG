<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit d899d77 -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global 第三轮 —— common 尾巴 A1 / A2 / A3 / A4

> **PR 范围**：`align/global` → `do-my-own-gh`
> **域**：`global`（收口域，§3.5）
> **议题档**：`common-tails`（A1 / A2 / A3）+ `cycle-deps`（A4）
> **基线**：`align/global` @ `d899d77`（`do-my-own-gh` @ `58e8314`）
> **写权**：全部落在 `service/common/**` + `tools/difftest/` + `docs/{DRIFT,Pitfalls,Intentional_Deviations}/global/`
> ⇒ **不碰** 其它域的业务代码，**不碰** 三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`（共享面，合并后统一更新）

---

## 一、要解决什么

用户 2026-10-25 指令：**「修 a1 a2 a3 a4；global 里的微服务只有 favorite 不是；B 和 C 先等下」**
⇒ 本轮 = 把可修清单里的**前 4 条**落地（**favorite 一律不碰**）。

| # | 条目 | 一句话 |
|---|---|---|
| **A1** | T-1 残余 ① | 「判定」修好了，但**解析层**仍走 MediaType ⇒ 畸形 `Content-Type` 仍抛（Go 不抛） |
| **A2** | T-9 | 熔断计数键**缺 target 前缀** ⇒ 不同下游服务调同名方法会**共用一份熔断器** |
| **A3** | T-4 | 客户端断连**完全不可见**（无 499 记录），且在飞 gRPC **不取消** |
| **A4** | CYCLE-002 | `service/common` 与别的域之间**是否还有** CYCLE-001 同型循环（未查） |

---

## 二、改什么

### A1 —— `GoStyleJsonBodyConverter#read`：包一层已净化的 `HttpInputMessage`

命中判据（`goReadsBody`）后，把传给 `super.read(...)` 的 `HttpInputMessage`
包成新的私有静态类 `SanitizedJsonContentTypeInputMessage`
⇒ 让 Spring 看到的 `Content-Type` 是**已净化的** `application/json`（**不带 charset**）。

| 场景 | 改前 | 改后 | Go |
|---|---|---|---|
| `application/json, application/xml` | 🔴 抛 `InvalidMediaTypeException`（500） | ✅ 正常读 body | ✅ 不抛 |
| `application/json; charset=gbk` | 🔴 按 GBK 解码 | ✅ **回归 UTF-8** | ✅ `encoding/json` 只认 UTF-8 |
| `Application/JSON` | ✅ 不读 body | ✅ 同左 | ✅ 同左 |

### A2 —— `GoStyleGrpcClientBreakerInterceptor`：熔断键补 target 前缀

`breakerName := path.Join(cc.Target(), method)`（Go）
⇒ RAG 改为 `goBreakerName(targetOf(next), fullMethodName)`（折叠连续斜杠，对齐 `path.Join`）。

**身份来源（实测，非推测）**：

| 问 | 答 |
|---|---|
| `ManagedChannel.getTarget()` | ❌ **grpc-java 1.63 没有**（`javap` 实测） |
| 用什么 | `Channel.authority()` ⇒ devh `DiscoveryClientNameResolver.getServiceAuthority()` 返回 `uri.getPath().substring(1)` ⇒ `discovery:///message-rpc` 下 = **`message-rpc`**（≡ Go 的 `key=message.rpc`）；直连 = `host:port` |
| ⚠️ 坑 | **in-process channel 的 authority 恒为 `"localhost"`** ⇒ 会塌缩 ⇒ 守门改用**直连地址**的真 channel |

⇒ **服务端侧不加 target**（Go `serverinterceptors/breakerinterceptor.go:29` 用 `info.FullMethod`）
⇒ `GoStyleRpcBreakerInterceptor` **未动**。

### A3 —— `HttpMiddleware`：499 判据改成「**filter 写 socket 失败**」

**根因**：RAG 的 499 由「worker 线程里抛 IOException」驱动，
而 worker **从不写 socket**（`ObservabilityResponseWriter` 写内存缓冲，真正写 socket 的是 filter 的 `flushTo`）
⇒ `panicHolder[0]` 永远 null ⇒ **永远进不了 499 分支**。

**改法**（新增 `flushCaptured(...)`，替换两处裸 `rec.flushTo(httpResp)`）：

1. 先定 `Content-Length`（**保形**）⇒ `rec.flushTo(...)` ⇒ **再显式 `flush()`**；
2. 写失败且是客户端断连 ⇒ **记 499**（`markTimeout(..., httpAttrs(..., 499))`，对齐 Go 的 `MarkTimeout`）
   + 打一条 **`[HTTP] 499 - <method> <uri> - <ip> - duration <n>ms`** 日志
   （Go 侧靠 go-zero 的 access log 可见；RAG **没有 access log** ⇒ 只写 span 不够）；
3. 非断连类 IOException 照旧上抛。

另：`writeTimeoutResponse(...)` 的三处调用改走 `writeTimeoutResponseQuietly(...)`
⇒ 写失败不再把异常栈抛给容器（对齐 Go：`http.Error` 内部的 `Fprintln` **忽略写错误**）。

⚠️ **保形（否则引入新的 wire 差异）**：提前 `flush()` ⇒ 响应**提前提交**
⇒ Tomcat 再也没机会算 `Content-Length` ⇒ 会退化成 **`Transfer-Encoding: chunked`**
（Go 是 `Content-Length`）⇒ **必须自己先定 `Content-Length`**（1xx/204/304 除外）。

### A4 —— `tools/difftest/check-cross-domain-holds.py`（**可重跑**，不是一次性结论）

三条规则：① 依赖不倒置（common 不得 import 业务域）② 无跨域 javadoc `{@link}`
③ `@Deprecated` + 外部引用（只提示、不判失败）。
⇒ 实测 **0 命中** ⇒ `service/common` 的 `@Deprecated` 归零、无反向 import、无跨域 `{@link}`。

---

## 三、Go 出处（逐条，L2 框架层 `go-zero@v1.10.2`）

| 条目 | 出处 |
|---|---|
| A1 | `rest/httpx/requests.go:153-155` `withJsonBody` + `rest/internal/header/headers.go:5` ⇒ `strings.Contains(raw, "application/json")` **纯字面**、**不做 MIME 解析** |
| A2 | `zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18` `breakerName := path.Join(cc.Target(), method)`；服务端侧 `zrpc/internal/serverinterceptors/breakerinterceptor.go:29` 用 `info.FullMethod`（**无 target**） |
| A3 | L1 `service/common/observability/http.go:80-88`：`case <-ctx.Done():` ⇒ `errors.Is(ctx.Err(), context.Canceled)` ⇒ 499 ⇒ `MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)` ⇒ `http.Error(w, httpTimeoutBody, statusCode)` |
| A4 | ——（自检规则，非 Go 对照项） |

---

## 四、实测依据（**不是读代码推断**）

1. **Tomcat 断连信号不可得** —— 真 Tomcat 11.0.24 嵌入式探针：
   `startAsync()` + `AsyncListener` + 裸 socket `SO_LINGER{1,0}` RST
   ⇒ **`onError` / `onTimeout` / `onComplete` 一个都不触发**；
   Tomcat 源码同证：`AbstractProcessorLight.java:62` 明写 **"Do nothing here"**。
2. **499 观测依赖真网络 flush** —— 真 Tomcat 实测：去掉显式 `flush()`
   ⇒ 小 body 只进 8192 缓冲 ⇒ socket 一个字节没发 ⇒ **记录仍是 200**。
3. **flush 会改 framing** —— 真 Tomcat 实测：只加 flush ⇒ 响应变 **`Transfer-Encoding: chunked`**
   ⇒ 加 `setContentLength` 后恢复，且与改前**逐字一致**（23 字节 / 0 字节 / 204 三种）。
4. **grpc-java 1.63 的 `ManagedChannel` 无 `getTarget()`** —— `javap` 实测。
5. **devh 的 `authority()` 语义** —— `javap` 读 `DiscoveryClientNameResolver.getServiceAuthority()`。

---

## 五、守门测试 + 变异测试（**§3.3：绿测试必须证明不是空转**）

| 守门 | 位置 | 条数 | 变异 | 结果 |
|---|---|---|---|---|
| A1 | `GoStyleJsonBodyConverterContentTypeCaseTest` | **8** | 去掉净化层 ⇒ 逗号头抛异常 + GBK 被成功解码 | 🔴 转红（双向） |
| A2 | `GoStyleGrpcClientBreakerTargetKeyTest` | **6** | 去掉 target 前缀 | 🔴 转红（`expected: <2> but was: <1>` + **跨服务污染复现**） |
| A3 | `HttpClientDisconnectRealTomcatTest`（**真 Tomcat**） | **3** | **M1** 去掉 499 判定<br>**M2** 去掉显式 flush<br>**M3** 去掉 `setContentLength` | 🔴 **三组全转红** |
| A4 | `tools/difftest/check-cross-domain-holds.py` | 3 规则 | 注入违规 import + `{@link}` ⇒ `EXIT=1` **精准命中两条规则**；还原 ⇒ `EXIT=0` | 🔴 转红 |

⚠️ **A2 守门网曾有洞并已修正**：第一版「自己算 key 再查 breaker」⇒ **变异不转红**（等于自证）
⇒ 改为**只调 `interceptCall` 再看 `trackedMethods()`**。

⚠️ **A3 为什么必须真 Tomcat**：`MockHttpServletResponse` / MockMvc 的 `write()` **永远不失败**
⇒ 断连分支**根本触发不了** ⇒ 那种测试是「错误答案的守卫」，比没有测试更危险。

---

## 六、验证

- `mvn -o -pl service/common test` ⇒ **全绿**（含新增 3 个测试类，原有 20+ 测试类无回归）；
- `mvn -o -pl service/common -am test -Dtest=…` ⇒ A1/A2/A3 三组守门**分别 3/3、6/6 全绿**；
- 变异体 **M1 / M2 / M3 / A2 / A4 五组全部转红**，且**均已还原**；
- `python3 tools/difftest/check-cross-domain-holds.py` ⇒ `EXIT=0`；
- ⚠️ **全量 `mvn -o clean verify -fae`** 在本 PR 提交前跑（56 个 reactor 模块）。

---

## 七、**不在**本次范围（明确不做）

| 项 | 为什么 |
|---|---|
| `favorite` 域的一切 | 用户明确：「global 里的微服务只有 favorite 不是」⇒ **一律不碰** |
| **C 类**（DRIFT-COMMON-005 拆分歧题 · DRIFT-HOTAPI-030 · HOTAPI-031 · users 域 DEFAULT 补签字） | 用户明确：**先等下** |
| **B 类**（未知项转已知） | 用户明确：**先等下**（下一轮再做） |
| `T-6 / T-7 / T-8` | 🟡 待拍板（同落点 `GoStyleRouterErrorResponses.java`）⇒ 不与本轮混改 |
| 「断连后在飞 gRPC 提前取消」 | **框架限制** ⇒ 落待签字候选 `SD-2026-10-25-CAND-12`，**不自行批准** |
| Jackson 的 UTF-16 BOM 自动探测 | 框架限制，改动面落在 Jackson 内部 ⇒ 不在本轮 |
| 共享面（三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`） | §5.3b：**合并后由主会话统一更新** ⇒ 本分支不碰 |
| `do-my-own-gh` | **合并由用户自己执行** ⇒ 本分支**永不**合入主干 |
