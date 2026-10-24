<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 踩坑档 · `service/favorite/favorite-api`

> **核对时间：2026-10-25**
> 本档记的是「**为什么会踩**」—— 不是缺陷清单，是**机制性陷阱**。
> ⚠️ 每条都要能回答：「下次怎么不再踩？」

---

## PIT-FAV-API-001 · 🕳️ **javadoc 里的「Go: …」断言会变成免检通道**

### 现象

本轮在 favorite-api 里同时撞到**两条**形如以下的 javadoc：

```java
// ① 「⚠️ 注意 Go 把「nil」与「success=false」合并成同一条 5001 分支，本实现用同一条出口保持一致」
// ② 「⚠️ client 熔断：Go 侧有且默认开，Java 侧尚未接入（已登记的已知偏离）」
```

**两条都是错的**：

- ① Go 侧其实是**两条独立分支**（判定不同、错误措辞也不同）；
  ② 熔断**早已生效**（运行证据），而「已登记」的 `docs/Intentional_Deviations/favorite/` **至今 0 字节**。

### 为什么会踩

AGENTS.md §3.3.2 点名的形态：注释写得很完整、很像是核对过的 ⇒ **review 时天然被当成「已核对」**。
而 §3.1 又明令「**不要拿代码注释当对照基准**」—— 但这条纪律通常被理解成「别信**别人**的注释」，
**不包括「别信本仓上一轮自己写的注释」**。

⚠️ 更狠的是第 ② 条：它同时断言了两件**可验证的事**（熔断没接 / 已登记），
而这两件**一件是假、一件从未发生**。如果 review 只读注释不跑代码，它会被完美放过。

### 后果

- 按 ① 的注释去「统一出口」，等于把 Go 的两条分支合并 ⇒ 服务端日志少一条 Go 侧存在的措辞；
- 按 ② 的注释去「补熔断」，等于**把已经生效的东西再接一遍** ⇒ 双重熔断。
  （⚠️ 本域当时正是这个风险：若照注释动手，很可能接出两个 `ClientInterceptor` 叠在一起。）

### 怎么不再踩

1. **凡要写「Go 把 X 与 Y 合并 / Go 只有一条分支」这类<b>结构性断言</b>，
   必须先回真源数一遍分支数</b> —— 本域的正确数法是：7 个 logic 里，
   4 个判 `rpcResp == nil`、3 个判 `rpcResp == nil || !rpcResp.Success`。
2. **凡要写「已登记 / 已签字」，必须给出<b>台账文件名 + 条目号</b>**，
   且该条目**必须真的存在**（本域当时三个文件全是 0 字节）。
3. **引用条款号前先确认条款讲的是这件事**：② 引用了 **§3.4**（proto 复制），
   而「表达层差异登记」根本不在 §3.4 ⇒ 条款号也成了装饰。

---

## PIT-FAV-API-002 · 🕳️ **「Bean 存在」证明不了「机制生效」—— 这次靠读注解就会判错**

### 现象

`FavoriteRpcClient` 的注释说「client 熔断**尚未接入**」。
而实际上 `GoStyleGrpcClientBreakerInterceptor` 是 `@Component` + `@GrpcGlobalClientInterceptor`，
favorite-api 的 `scanBasePackages` **含 `com.rag.common`** ⇒ 它**早就在上下文里**、
**早就在 starter 的 `GlobalClientInterceptorRegistry` 里** ⇒ 熔断**早已生效**。

### 为什么会踩

「某个机制没接」和「某个机制早就接了」**在源码上看起来一模一样** ——
注解都在、包都在、扫描范围都覆盖。
⇒ 只能靠**运行证据**分辨，而源码 review 时最省事的做法就是「看注释」。

### 怎么不再踩

⇒ **装配层测试**，且必须断言到「**消费方**」而不是「Bean 本身」：

```java
@Autowired private GlobalClientInterceptorRegistry registry;   // starter 自己的收集器
@Autowired private GoStyleGrpcClientBreakerInterceptor interceptor;

assertTrue(registry.getClientInterceptors().contains(interceptor));
```

只写 `assertNotNull(interceptor)` 或 `assertTrue(context.containsBean(...))` 是**假绿**：
它只证明容器里有这个对象，**证明不了 channel factory 会不会消费它**。

⚠️ 本轮还发现：`favorite-rpc` 侧**已经有**一条同类装配测试
（`FavoriteProtectionChainWiringTest`，含 `DRIFT-MSG-V-026` 那条「熔断/降载链必须真的挂到 gRPC server 上」）
⇒ **同一个域的两端，一端验了、另一端没验** ⇒ 这就是「域内不对称的验证密度」的典型形态。

---

## PIT-FAV-API-003 · 🕳️ **Go 侧有两层超时预算时，Java 侧只补一层就够了吗？**

### 现象

Go 侧 favorite-api → favorite-rpc 有**两层**预算：
- ctx 预算 **3s**（`rest/config.go:55` `Timeout default=3000`）
- zrpc client 超时 **2s**（`zrpc/config.go:29` `RpcClientConf.Timeout default=2000`）

RAG 侧原本**只**套了 ctx 预算（3s）⇒ 下游挂死时一路顶到 HTTP 超时 ⇒ **503 "Request Timeout"**，
而 Go 是 **2s 先到** ⇒ **200 + 业务码 1015**。

### 为什么会踩

「两层预算取更小的生效」听起来像**一条**规则 ⇒ 容易只实现「明显的那层」（HTTP 超时），
把「不太显眼的那层」（client 拦截器级超时）漏掉。
⚠️ 而且漏掉之后**没有任何报错**：编译过、上下文起得来、测试全绿，只有下游真挂死时行为不同。

### 怎么不再踩

1. **Go 侧每一条预算都要单独找出来**（`struct default` / `yaml` / `interceptor` 三处都可能藏）；
   本域的 2s 藏在 **`zrpc/config.go` 的 struct tag** 里，`favorite.yaml` 根本没这个字段。
2. **Java 侧必须显式取「更小者」**，不能靠「上层自然更早触发」——
   上层触发时**状态码与 body 可能不自洽**（503 + "Request Timeout" vs 200 + 信封）。
3. **验收要用真 in-process channel 读回 deadline**，不能断言常量等于 2000 ——
   那是「拿 RAG 的值验 RAG 的值」。

⚠️ 顺带：`GoStyleRequestDeadline.applyTo` 在**没有** ctx 预算时**原样返回 stub**（不设 deadline），
所以 client 层那 2s **必须自己套**（Go 的客户端超时是**拦截器级别**的，与有没有 HTTP 超时中间件无关）。

---

## PIT-FAV-API-004 · 🕳️ **MockMvc 不走 servlet filter 链**

### 现象

favorite-api 的 76 例测试**全部绿**，但它们**一次都没跑过** servlet filter 链
（`@SpringBootTest(webEnvironment = MOCK)` ⇒ MockMvc ⇒ 不经过 `Filter`）。

⇒ 本域的服务端保护层（MaxConns / Breaker / CpuShedding / MaxBytes 四层）
在测试里**完全没被触发过**。

### 为什么会踩

「`@Component` + `@Order` 齐了 ⇒ 过滤器在链上」—— 这在**真容器**里成立，
在 **MockMvc** 里**不成立**。而 MockMvc 是 Spring 测试的默认选择 ⇒ 极容易默认它「测过了」。

### 怎么不再踩

⚠️ **本轮没有做真容器验证**（已在 `docs/DRIFT/favorite/api.md` §4 明确标为「未验」）。
要落实需要：真 Tomcat 启动（`webEnvironment = RANDOM_PORT`），
然后对**每层**各打一个超限请求，断言响应形态与 Go 侧一致。
⚠️ 参考仓内现成工具：`tools/difftest/check-plaintext400-headers.sh`
（它就是为了「MockMvc 测不到真 Tomcat」才存在的）。

---

## PIT-FAV-API-005 · 🕳️ **「,string」既是出参形态，也是入参解析器**

### 现象

Go 的 `types.go` 里 `,string` 有两个方向：
- **出参**：`json:"folderId,string"` ⇒ int64 序列化成 **JSON 字符串**；
- **入参**：同一个 tag 也让 go-zero 的解析器接受**字符串**形式的 int64。

RAG 侧的出参用「getter 返回 `String`」实现 ✅；
但**入参**如果走 Jackson 默认绑定，`setFolderId(Long)` 就**只接受数字** ⇒ 与 Go 不一致。

### 怎么不再踩

⚠️ 别用 Jackson 默认绑定去接 Go 的 `,string` 入参；
本域的做法是**自己解析**（`GoStyleStringInt64.parse` + `GoStyleRequired.field`），
并由 `FavoriteApiInputContractTest`（33 例）守住。

⚠️ 另一条同源陷阱：**出参里 `,string` 只加在部分字段上** ——
`folderId`/`favoriteId` 是字符串，而 `createdAt`/`updatedAt` 是**数字**
⇒ 混掉任何一个都会改 L1 出参。
