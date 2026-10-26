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

favorite-api 第三轮之前的 **76 例**测试**全部绿**，但它们**一次都没跑过** servlet filter 链
（`@SpringBootTest(webEnvironment = MOCK)` ⇒ MockMvc ⇒ 不经过 `Filter`）。

⇒ 本域的服务端保护层（MaxConns / Breaker / CpuShedding / MaxBytes 四层）
在测试里**完全没被触发过**。

### 为什么会踩

「`@Component` + `@Order` 齐了 ⇒ 过滤器在链上」—— 这在**真容器**里成立，
在 **MockMvc** 里**不成立**。而 MockMvc 是 Spring 测试的默认选择 ⇒ 极容易默认它「测过了」。

### 怎么不再踩

✅ **本轮第三轮已补上真容器验证** ⇒ 新增 `FavoriteApiRealTomcatGateTest`
（`webEnvironment = RANDOM_PORT` ⇒ **真 Tomcat**，8 例）：
MaxBytes → **413 + 空 body + 无 Content-Type**、413 **压过** 401、未超限仍 401、
容器三旋钮各自生效、容器层参数上限的出口形态。
⚠️ 参考仓内现成工具：`tools/difftest/check-plaintext400-headers.sh`
（它就是为了「MockMvc 测不到真 Tomcat」才存在的）。

⇒ **纪律**：凡是「保护层 / 拦截器 / 过滤器」类机制，**MockMvc 那份不算数**；
要判「机制生效」必须有**真容器**的运行证据（§3.3）。
⚠️ 而 MockMvc 那份**仍然要留着** —— 它快、且能覆盖「响应体形状」这类不需要真容器的东西。

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

---

## PIT-FAV-API-006 · 🕳️ **容器的「参数过多」异常不在 `IllegalArgumentException` 家族 ⇒ 直接绕过 handler**

### 现象

本轮修的 D4（`docs/DRIFT/favorite/api.md` §2）**在动手前是这样表现的**：

| | 期望（Go） | 实得（RAG 修复前） |
|---|---|---|
| 状态码 | **200** | **500** |
| body | `{code:5003, msg:"参数错误", data:null}` | `{code:5001, msg:"系统内部错误", data:null}` |

⇒ 看着像「业务码映射错了」，实际是**异常根本没进业务异常处理器**。

### 为什么会踩

Spring 的 `@ExceptionHandler(IllegalArgumentException.class)` **只按类型匹配**。
而 Tomcat 抛的是 `org.apache.tomcat.util.http.InvalidParameterException`
—— ⚠️ **它不是 `IllegalArgumentException` 的子类**（也不属于任何常见的 `IllegalArgument*` 家族）
⇒ 直接穿过 `handleIllegalArgument`，落到 `handleAny(Exception)` 兜底 ⇒ **500**。

⇒ 三个叠加因素让它极易漏：
1. **触发条件很窄**（要真的把参数堆到 2048 个）⇒ 常规功能测试永远碰不到；
2. **失败形态长得像「兜底」**（500 + 5001）⇒ 很容易被当成「这个域就是有 500 兜底」；
3. **MockMvc 碰不到**（见 PIT-FAV-API-004）⇒ 当时的 76 例绿测试全都不覆盖。

### 怎么不再踩

1. **凡是要复用「参数非法 ⇒ 200 + 5003」这条出口，就得确认异常类型能进那个 handler** ——
   本域的做法是在**调用点**把容器异常归一，而不是去改 handler 的匹配面：
   ```java
   private static Map<String, String[]> parameterMap(HttpServletRequest request) {
       try {
           return request.getParameterMap();
       } catch (RuntimeException e) {
           throw new IllegalArgumentException("too many request parameters: " + e.getMessage(), e);
       }
   }
   ```
   ⚠️ catch `RuntimeException` 而**不是** Tomcat 的具体类：避免把编译期依赖绑到
   `org.apache.tomcat.util.http`（非公开 API 面，Boot 换版本就可能挪位置）。
2. **兜底 handler（`handleAny`）一旦真的被触发，就是信号** —— Go 侧的 **7 个 handler 全部
   `httpx.OkJsonCtx` ⇒ handler 自身永不产出 500** ⇒ RAG 侧出现 500 = 有东西没被接住。
   ⚠️ **但要说清「不可达」的边界**（2026-10-25 第六轮更正）：Go 的 handler 逻辑虽不产出 500，
   **panic 仍会走默认中间件 `RecoverHandler` ⇒ 500 + 空 body** ⇒ **那条路径是可达的**
   （见 [`DEV-FAV-API-002`](../../Intentional_Deviations/favorite/api.md)）⇒
   「不可达」只对「**handler 自己返回 5xx**」成立，对「**panic**」不成立。
3. **回归网要挑「容器层的上限」而不是「应用层的上限」** ——
   应用层上限（2048 个**非空**值）会**被容器先拒**（实测同值 ⇒ 两层都触发，测不出归一是否生效）；
   真正只走容器层的形状是「**总「值」数超限、但非空值很少**」（本域用 `folderId=7` + 2048 个空值）。
   ⚠️ 本轮踩过这个坑：最初用「2049 个非空值」当回归用例，变异时**两条一起红**，
   一度以为是自己没写对。

---

## PIT-FAV-API-007 · 🕳️ **「读源码判定」不是「已验」—— 一条判错、另一条碰巧判对**

### 现象

2026-10-25 第六轮做响应字节对拍时，本台账里两条标着「🟡 源码级」的结论被实测复核：

| # | 原结论（读源码判的） | 实测（真库跑的） |
|---|---|---|
| 1 | 「Go 的 `RecoverHandler` 写 **500 + text/plain**（`http.Error`）」 | go-zero v1.10.2 的 `rest/handler/recoverhandler.go:17` 是 **`w.WriteHeader(500)`** ⇒ **空 body、无 Content-Type** |
| 2 | 「`title`/`cover` 的 `,optional` 是 go-zero mapping 的选项 ⇒ 不是 `omitempty` ⇒ **永远出现**」 | ✅ 这条**判对了** —— 但**方法不可靠**：`encoding/json` **忽略**它不认识的 tag 选项这一点，只有**真序列化一次**才看得见 |

⇒ 第 1 条还带出**两个连带错误**：
「Go 的 logic **不会 panic** ⇒ 该分支不可达 ⇒ 不构成漂移」——
**也不成立**：`RecoverHandler` 是 go-zero 的**默认中间件**（`rest/engine.go:158`）⇒ **可达**。

### 为什么会踩

1. **「读源码」和「跑一遍」被当成了同一档证据** —— 但本台账的 §4 明明写着
   「🟡 源码级」**不等于**「已验」，**却仍被当成结论用了**（还据此写了「不构成漂移」）。
2. ⚠️ **框架的默认行为不在框架的文档里**：`http.Error` / `WriteHeader` 的差别、
   `encoding/json` 忽略未知 tag 选项 —— 这些都**只能从真库的源码或行为里拿**，
   **不能靠「Go 侧一般怎么写」去推**。
3. ⚠️ **「不可达」是一个需要证据的断言**，不是观察 —— 而本条当时**没有**任何证据
   （既没跑 Go，也没有查 go-zero 的默认中间件链）。

### 怎么不再踩

⇒ **凡是「两侧的字节/形态」类结论，一律用「真库跑一次」取证**：
新建 `tools/api-envelope-probe/favorite/` —— Go 侧直接调**真的** go-zero 函数
（`httpx.OkJsonCtx` / `httpx.Parse` / `handler.RecoverHandler`）+ **从真源逐字节复制**的类型定义
（sha256 校验）⇒ 期望值落在 `expected.txt`；RAG 侧用**真实**的生产类 + **容器里那个** mapper。

⇒ **「不可达」要单独拿证据**：要么证明那条路径在两侧都走不到（那就得**两侧都跑**），
要么承认它**可达**、按「真差异」登记 ⇒ 本条按后者处理（DEV-FAV-API-002）。

⚠️ **教训的形状**：「结论恰好正确」也会掩盖「方法不可靠」——
第 2 条就是**对的**，可它是用**不可靠的方法**得到的 ⇒ 下一次同样的方法就会错。
⇒ 所以要盯的是**方法**，不是**这次对了没有**。

⚠️ 另有一条**同源的坑**：`jsonx.Marshal` 会**去掉尾换行**（`encoding/json` 的 `Encoder.Encode`
会加一个）⇒ 手写复刻 Go 的 JSON 输出时，**多一个或少一个 `\n` 就是字节不一致**。

---

## PIT-FAV-API-008 · 🔴 **`RANDOM_PORT` 挑的端口可能根本不是你的**（同号端口被别的会话的容器抢走 ⇒ 请求打到 PostgreSQL 上）

### 现象

2026-10-25 第十四轮收尾跑全量测试，`favorite-api` 的
`FavoriteApiRealTomcatGateTest` **8 个用例全部 ERROR**：

```
java.io.IOException: HTTP/1.1 header parser received no bytes
  Caused by: java.io.EOFException: EOF reached while reading
```

⇒ 8 个用例**秒挂**（总共 0.829s），而同一 commit：
- 单独跑该类 ⇒ **全绿**（8/8）；
- 同 JVM 里 **5 秒后**启动的另一个 Tomcat（`FavoriteApiEnvelopeParityTest`）⇒ **全绿**；
- 前 20 分钟的同一次全量跑 ⇒ **全绿**。

⇒ **同一份代码、同一个执行顺序**，绿↔红翻转 ⇒ 第一反应不该是「被测代码坏了」。

### 根因（本机实测实证，不是推断）

1. `@SpringBootTest(webEnvironment = RANDOM_PORT)` ⇒ **OS 从临时端口段（macOS：49152–65535）里挑**。
   ⚠️ **Docker 随机发布容器端口用的就是同一段** ⇒ 本机是**多会话并行**的机器，
   别的会话的容器随时可能在 `127.0.0.1:P` 上挂一个 PG / Redis。
   ⇒ 2026-10-25 09:10 那次，**别的会话的 `cdifftest-postgres` 已经连续 26 小时占着
   `127.0.0.1:55440`**（`docker ps` 实测），而本测试的 Tomcat **恰好挑中了 55440**。
2. **macOS/BSD 的 socket 语义让这件事「静默」**（本机 Python 实测）：
   - `127.0.0.1:P` 与 `0.0.0.0:P` **可以共存**（都带 `SO_REUSEADDR`）
     ⇒ **Tomcat 绑 `0.0.0.0:55440` 会照样启动成功**，启动日志照样打
     「Tomcat started on port 55440」⇒ **它并不知道这个端口号还有另一个更具体的监听者**；
   - 客户端连 `127.0.0.1:P` ⇒ **命中更具体的那个**（实测命中 `specific-127.0.0.1`，
     **不是** `wildcard-0.0.0.0`）。
3. ⇒ 于是客户端把 HTTP 请求打到了 **PostgreSQL** 上 ⇒ 协议不匹配 ⇒
   **一个字节都不回就关连接** ⇒ 客户端报 `EOFException` ⇒
   表现成 `HTTP/1.1 header parser received no bytes`。

⇒ **最阴的地方**：Tomcat **确实起来了**、**端口号确实是我们挑的**、**日志一切正常** ——
只有「客户端连的地址」被别人截胡了 ⇒ **不查端口归属就永远查不出来**。

### 怎么不再踩

⇒ **判据**：
> 「端口号是我挑的」**不等于**「这个端口是我的」。
> macOS 上**必须同时**满足：`0.0.0.0:P` 可绑（没别的 Tomcat 抢通配地址）
> **且** `127.0.0.1:P` 可绑（没别的容器抢更具体的地址）。

⇒ 本域落地（`service/favorite/favorite-api/src/test/java/.../support/RealTomcatPort.java`）：
- **自己挑端口**：专用端口段 **21300–21399**（刻意**避开** OS 临时端口段
  ⇒ 与 Docker 随机发布端口**不同段**）；
- **双绑定自检**：先把 `0.0.0.0:P` 与 `127.0.0.1:P` **同时**绑上（绑不上 ⇒ 跳过该端口）
  ⇒ 任何第三方持有者都会被排除（实测：Docker 占着 `127.0.0.1:55440` 时，
  绑 `127.0.0.1:55440` 报 `EADDRINUSE`）；
- 两个测试类 ⇒ `DEFINED_PORT` + `@DynamicPropertySource`（⚠️ **两个都要探**：
  只探 `127.0.0.1` 会漏掉「上一个测试类的 Tomcat 还占着通配地址」⇒ 两个类拿到同一个端口
  ⇒ 后一个类 `PortInUseException` **启动失败** —— 这是本条落地时**实测踩到**的第二个坑）；
- **失败时要说人话**：请求层把 `IOException` 包装成
  「端口 N 上读到了 0 字节（本请求没打到本测试的 Tomcat？）；该端口的监听者=[lsof 输出]」
  ⇒ 下一次看到就是一眼定位。

⚠️ **推广面（归 `global` 收口域）**：全仓实测 **7 个域 / 14 个测试类**在用 `RANDOM_PORT`
（`message-api` 4 · `favorite-api` 2(+1 本条的支撑类) · `hot-api` 2 · `article-api` 2 ·
`users/user/user-api` 1 · `task-api` 1 · `like-api` 1）⇒ **同款暴露面全仓都在**
⇒ 需要一份**共享**的端口选取实现 ⇒ 落点在 `service/common` 的 test-jar 或各域各自复制
⇒ **本域只写自己这份**（`service/common/**` 是共享面，本域不碰）。

⚠️ **同源的第二处**：本域的**探针**（`tools/api-full-parity-probe` / `tools/grpc-contract-probe`）
原先也用 `bind(("127.0.0.1", 0))` 拿 OS 随机端口 ⇒ **同样的暴露面**
⇒ 已一并改成「专用端口段 21400–21599 + 双绑定自检」。
⇒ 探针侧更危险：探针的 `wait_port` 只验「连得上」⇒ **连到别人的服务也算就绪**
⇒ 探针结论会**整个不可信**（而不只是报错）。

### ⚠️ 附一条：**守门测试自己也会被环境左右**

修完之后给它配守门测试时，第一版写法是「**占住端口段的第一个端口**，然后断言 `pick()` 绕开它」
⇒ **单独跑绿，全量套件里直接红**：

```
RealTomcatPortTest.skipsPortHeldOnWildcard » BindException: Address already in use
```

⇒ 原因：**同一个 JVM 里，上一个 Spring 测试类的 Tomcat 还活着**
（Spring 会**缓存**测试上下文 ⇒ 它的端口**本来就是**端口段的第一个）
⇒ 测试想占的那个端口**早就被占着** ⇒ 守门测试自己被环境左右。

⇒ **怎么不再踩**：
> 守门测试**不许去抢「被测对象正在用的资源」** ——
> 它必须用**独立的脚手架资源**（这里是另一段端口）⇒ 与「谁还活着」完全无关。

⇒ 本域落地：判定逻辑 `RealTomcatPort.isFree(int)` 改成**包级可见** ⇒ 守门测试直接断言它；
「被占」样本用**独立脚手架端口段 21950–21999**（不碰生产用的 21300–21399）。
⇒ 附带一条通用判据：**「守门网在单跑与全量里结论不同」⇒ 先怀疑守门网自己依赖了共享状态**，
而不是先怀疑被测代码。
