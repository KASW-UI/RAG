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

---

## PIT-FAV-API-009 · 🔴 **守门网断言了「共享层的绝对值」⇒ 共享层一次合法调整就把它打成假红**

**触发**：2026-10-26 第三十一轮（合并当天）—— 把 `align/favorite` 合进 `do-my-own-gh`
（`global` 的合并 `4f839f9` **已先落地**）⇒ 合并树全量 `mvn test` 在 `favorite-api` 红：

```
FavoriteApiProtectionWiringTest.serverSideProtectionFiltersAreBeansInGoChainOrder:128
  MaxConns 应最先 ==> expected: <-2147483647> but was: <-2147483644>
```

⇒ `-2147483647 = Ordered.HIGHEST_PRECEDENCE + 1`（我断言的旧值）·
`-2147483644 = + 4`（共享层的新值）。

### 现象

1. 🔴 **本分支自始至终全绿** —— `align/favorite` 上 30 轮、94 例全绿
   ⇒ **红只在「把别人的合并拿进来」时出现**。
2. 🔴 **红的是一个「顺序」断言**，而**顺序恰恰是 Go 语义要求的东西**
   ⇒ 第一反应是「共享层把 Go 的链序改坏了」⇒ **差点按回归去「修」**。
3. ⇒ 但逐条核证后：**共享层的改动是对的**（`global` 第十九轮 T-8：
   把保护层四件套整体后移 `+1..+6` ⇒ `+4..+8`，好让 `GoStyleTraceparentFilter`
   落到「CORS 之后、MaxConns 之前」——Go 侧 Trace 是**链内第一个**，
   go-zero v1.10.2 `rest/engine.go:135`）⇒ **相对次序完全没变**。

### 根因：**断言口径选错了层**

| 层 | 内容 | 该由谁断言 |
|---|---|---|
| **语义层** | 四个保护中间件的**相对次序** == Go 的 `MaxConns(:146) → Breaker(:149) → Shedding(:152) → MaxBytes(:164)` | ✅ **消费方（本域）** —— 它决定本域请求「先被谁拒」⇒ **本域可观察** |
| **排版层** | 这四个 filter 的 `@Order` **绝对数值**（`+1` 还是 `+4`） | 🔴 **共享层（`global`）** —— 那是 `service/common` 的内部排版，**与 Go 语义无关** |

⇒ 我把**两层一起断言了**（绝对值）⇒ 于是**共享层改排版 ⇒ 我的守门网假红**。

### 怎么不再踩

⇒ **判据（一句话）**：
> **断言「语义」，不锁「排版」。**
> 只要一个数值**换掉它不改变 Go 语义**，它就是**排版** ⇒ **不许断言绝对值**，
> 要断言就断言**关系**（大小 / 先后 / 集合包含）。

⇒ **落地的三个动作**：
1. **把绝对值断言改成关系断言** ⇒ `assertTrue(orderOf(a) < orderOf(b), …)`，
   失败信息**带上实际值**（「实际 MaxConns=-2147483646 Breaker=-2147483647」）
   ⇒ 这样「红」才携带**定位信息**，而不是只告诉你「错了」（PIT-FAV-RPC-036）。
2. **一行断言一个测试方法** ⇒ 交换两个 `@Order` 时**只有一条**红 ⇒
   **立刻知道是哪一对的相对序被破坏了**。
3. **变异自证**：把共享层两个 filter 的 `@Order` 对调 ⇒ 守门**必须转红** ⇒
   再 `git checkout -- service/common/` 还原 ⇒ 复跑**必须绿** ⇒
   ⇒ **「跑绿」不等于「判据活着」**（同 `PIT-FAV-RPC-037` 的牙齿检查思路）。

⇒ **附带一条跨域划线**（本轮踩到才发现的）：
> **改法落在 `service/common` 的不变量，归 `global` 域；本域只锁「本域可观察」的那部分。**
> ⇒ 「四个保护 filter 整块相对 Trace/Log/Prometheus 的位置」已**回执 `global`**，
> 请其在**所有者**侧加门禁 ⇒ **别让 10 个消费域各自去断言共享层的排版**。

⇒ 台账 ⇒ [`docs/DRIFT/favorite/api.md`](../../DRIFT/favorite/api.md) **§12**。

### 同族

- `PIT-FAV-API-002`：**「Bean 存在」证明不了「机制生效」** —— 本轮是它的**镜像**：
  「**数值相等**」同样证明不了「**语义一致**」。
- `PIT-FAV-RPC-036`：**一条断言一个测试方法** —— 本轮是它的一次实战应用。
- `PIT-FAV-RPC-037`：**工具输出是线索不是结论** —— 本轮是它的**代码侧版本**：
  **「测试红了」也只是线索**，红的**原因**可能是守门网自己写错了口径。

---

## PIT-FAV-API-010 · 🔴 **共享层一改，本域守门的「变异守护」就哑了**（0 例转红 ⇒ 那条断言其实已无人守护）

**触发**：2026-10-27 第十八轮 —— 合并 G4 的 `cd893d1`（容器上限**无条件**抬到 10001）之后，
复验 `FavoriteApiRealTomcatGateTest` 的变异表（台账 §2.1），发现：

| 变异 | 合并前 | 合并后（**实测**） | 原因 |
|---|---|---|---|
| **MUT-A**（去掉 `parameterMap` 的异常归一） | 2 例转红 | 🔴 **0 例转红** | 容器不再抛「参数过多」⇒ 这层归一**永不触发** |
| **MUT-C**（yaml `max-parameter-count` 2048 → 1000） | 1 例转红 | 🔴 **0 例转红** | yaml **不再决定运行期值**（共享层无条件抬到 10001） |
| MUT-B / MUT-D / MUT-E | 2 / 4 / 1 红 | 2 / 4 / **8 error** | 前两条不受影响；MUT-E 的错值如今在**上下文启动期**就被拒 ⇒ 波及面从 1 例扩到 8 例 |
| **MUT-F**（**新增** · 域内判据「数非空值」→「数总值」） | —— | ✅ **1 例转红** | 本轮**替掉** MUT-A / MUT-C 对该断言的守护 |

### 现象

1. 🔴 **守门全绿**（8/8）⇒ 看起来「判据活着」；
2. 🔴 但**两条变异已经转不动了** ⇒ ⇒ 那两条断言（尤其
   `containerParameterLimitIsSurfacedAsGoParseError`）**从此无人守护**；
3. ⇒ 最危险的一层：这一轮**同一条断言本来就该改**（它守的是 RAG 独有的 `5003` 形态，
   而 Go 在同一输入下是「放行」）⇒ ⇒ 若不是被共享层变更撞出来，
   **它会继续绿着、守一条漂移**（AGENTS.md §3.3：「绿测试可能是『错误答案的守卫』」）。

### 根因：**变异守护的「活性」本身依赖共享层的运行期行为**

| 层 | 谁拥有 | 变了我域里什么会哑 |
|---|---|---|
| **运行期行为**（容器上限到底多少） | 🔴 **共享层**（`service/common`） | 凡以「容器拒 / 容器放行」为判据的变异（**MUT-A / MUT-C**）**全部转不动** |
| **语义判据**（Go 数**非空值** vs 数**总值**） | ✅ **本域**（`FavoriteApiSupport#goFormFirstValue`） | 本域自己的变异（**MUT-F**）**照常转红** ⇒ **这才是可靠的守护位** |

⇒ ⇒ **判据（一句话）**：
> **变异要挑「所有者在本域」的那一层去动。**
> 共享层拥有运行期行为时，**就别拿运行期行为当变异点** ⇒
> 改用**本域自己拥有的语义判据**当变异点。

⇒ 本轮据此新增 **MUT-F**（把域内判据从「数非空值」改成「数总值」= 把容器的旧口径搬进应用层）
⇒ ✅ **1 例转红**（失败消息点名「不得落 5003」，实际 body = `{"code":5003,…}`）⇒ 断言重新有人守护。

### 怎么不再踩

1. **合并共享层之后，变异表必须重跑一遍** ⇒ 逐条记「红几条」⇒ **失效的当场换成新变异**
   ⇒ 别让表里躺着 0 红的行（0 红的行 = 一颗定时炸弹）；
2. **变异点优先选「所有者在本域」的那一层**（本域的判据 / 本域的解析口径），
   而不是共享层的运行期数值；
3. **台账里的变异表是会过期的资产** ⇒ 每次合并共享层都要复算
   （与 §2.1「本轮修复」表的纪律同款）。

### 同族

- `PIT-FAV-API-009`：**守门网断言了「共享层的绝对值」⇒ 共享层一次合法调整就把它打成假红**
  ⇒ 本条是它的**姊妹形态**：009 是「**假红**」（断言绑错了层），
  本条是「**假绿的温床**」（**变异**绑错了层 ⇒ 断言无人守护）。
- AGENTS.md §3.3：**「跑绿」≠「对」** ⇒ 本条再加一句：**「跑绿」≠「判据活着」**
  ⇒ 「变异还转不转得红」才是判据是否活着的体检项。
- AGENTS.md §3.3.3：**「绿测试可能是错误答案的守卫」** —— 本条是它的一个**可操作版本**：
  守卫的「牙齿检查」（变异转红）会**随共享层变更悄悄掉光** ⇒ 牙齿要**定期体检**。

---

## PIT-FAV-API-011 · 🔴 **yaml 里没写 `lettuce.pool.enabled` ≠ 池没开** —— Boot 4 的判据是 **classpath**，不是配置

> 登记于 2026-10-27（G2 挖出「Lettuce 池形态」· 本域独立复核）⇒ 权威面 = [`docs/DRIFT/favorite/api.md` §15](../../DRIFT/favorite/api.md#15-🔴-redis-连接池形态漂移go-有池-vs-rag-无池-2026-10-27-登记g2-挖出--本域独立复核--只登记不落码)

### 现象

某个组件的 `application.yml` 的 `spring.data.redis` 段**没有** `lettuce:` 子段 ⇒ 直觉结论：「**池没开**，用的是 Lettuce 单连接共享」。

**这个结论可能是错的**，而且**没有任何报错**。

### 根因：Boot 4 的 `enabled` 判据会**回落到 classpath 探测**

Boot **4.0.8** 的 `spring-boot-data-redis-4.0.8.jar`：

- `LettuceConnectionConfiguration#createBuilder(DataRedisProperties$Pool)` 字节码：
  `offset 2 invokevirtual isPoolEnabled` → `offset 5 ifeq 20` ⇒ **真** ⇒ `offset 8 new PoolBuilderFactory`（**有池**）；**假** ⇒ `offset 20 LettuceClientConfiguration.builder()`（**无池**）。
- **`DataRedisConnectionConfiguration#isPoolEnabled`** 字节码才是判据本体：
  `getEnabled()` → **`ifnull 16`** → `getstatic COMMONS_POOL2_AVAILABLE` ⇒ ⇒
  **`enabled` 没配（null）时，判据变成「classpath 上有没有 `org.apache.commons:commons-pool2`」**。
- `DataRedisProperties$Pool.enabled` 的类型是 `java.lang.Boolean` ⇒ **可以为 null** ⇒ 「没配」是常态，不是异常。

⇒ ⇒ **任何一个传递依赖**（某个 starter 顺带带进 `commons-pool2`）都会让该组件**从「单连接共享」变成「有池」**，而 **yaml 里一个字符都不用改** ⇒ **配置面完全看不出这个变化**。

### 本域实测（2026-10-27 · 逐组件，不是整域推断）

| 组件 | `lettuce` 段 | classpath 上 `commons-pool2` | 实际池形态 |
|---|---|---|---|
| `favorite-api` | **无** | **0 命中** | ❌ 无池 |
| `favorite-rpc` | **无** | **0 命中** | N/A（**不接 Redis**） |
| `hot-rpc` | **无** | **0 命中** | ❌ 无池 |
| `comment-rpc` | `pool.enabled: true` | **1 命中**（2.12.1） | ✅ 有池 |

⇒ ⇒ **同一段判据、不同结果** ⇒ **整域推断必然出错** ⇒ 必须**逐组件**测。

### 怎么不再踩

1. 判「池开没开」**只看 yaml 不算数** ⇒ 必须查 classpath：
   `mvn -B -o -pl <模块> -am dependency:tree -Dincludes=org.apache.commons:commons-pool2`
   （`-am` 必须带：域内模块依赖 `service/common` 等 reactor 内模块，不带会解析失败）
2. **逐组件测**，不要按域推断（上表四个组件三种结果）。
3. **依赖树是会变的资产** ⇒ 池形态会**静默翻转** ⇒ 要有**守门**断言 `LettuceConnectionFactory.getClientConfiguration().isPoolEnabled()`
   （当前**没有** ⇒ 登记为候选自修项 `L-10`，见 `docs/DRIFT/favorite/api.md` §15.5）。
4. 顺带记一条**启动期硬失败**：Boot 4 的 starter **不传递** `commons-pool2` ⇒ 配了 `pool.enabled: true` **还必须显式引依赖**，
   否则 `NoClassDefFoundError: GenericObjectPoolConfig` ⇒ **组件起不来**（先例：`like-rpc`；本域 `comment-rpc` 已显式引）。

### 同族

- **AGENTS.md §3.3.1**：「配置里没写」≠「没开」，也≠「没做」⇒ 本条是它在 **Boot 4 Redis 池**上的具体实例。
- `PIT-FAV-API-009`（守门网断言了共享层的绝对值 ⇒ 假红）/ `PIT-FAV-API-010`（变异绑错层 ⇒ 断言无人守护）
  ⇒ 本条是**第三种形态**：**判据的来源根本不在你以为的那一层**（不在 yaml，在 classpath）。
- 同源的通用纪律：**「你以为的那个量」与「系统实际吃到的那个量」不是同一个**
  ⇒ 与「两口径交叉验先查报告新鲜度」「跨树引用要带 commit id」同族。

---

## PIT-FAV-API-012 · 🔴 **守门断言的期望值若引用「被测代码的常量」⇒ 那条守门是哑的**（实测 0 红）

> **一句话**：断言的期望值**不能与被测代码同源** ⇒ 改常量 ⇒ 业务代码与断言**一起变** ⇒ 断言恒成立 ⇒ **守门看着全绿，实际什么都没守住**。

### 现象（2026-10-27 实测 · favorite 域 span 守门）

第一版 `FavoriteApiSpanWiringTest` 的期望值写成 **`FavoriteSpans.API_CREATE_FAVORITE`**（**生产代码的常量**）：

```java
SpanData span = requireSpan(FavoriteSpans.API_CREATE_FAVORITE);
assertEquals(PROBE_USER_ID, longAttr(span, FavoriteSpans.ATTR_USER_ID));
```

变异 **MUT-E** 把那个常量的**值**从 `"FavoriteAPI.CreateFavorite"` 改成 `"FavoriteAPI.CreateFavoriteV2"`
⇒ ⇒ **业务代码（`FavoriteCreateItemService`）跟着用新名字** ⇒ ⇒ **导出的 span 名与断言期望值一起变**
⇒ ⇒ **守门 0 红**（其余 6 个变异都正常转红 ⇒ 一开始差点把它当成「变异点选错层」放过）。

### 机制

- 变异点选在哪一层 ⇒ 决定变异**转不转得动**；
- **期望值同源** ⇒ 决定断言**转不转得红** —— 这是**另一层**，两者要**各自**查。

⇒ 所以「7 个变异里 6 个转红、1 个 0 红」时，**不能**只归因于「变异点选错层」，**还要查期望值是不是同源**。

### 修法

1. **期望值一律用 Go 真源的字面量**（不是生产代码常量）：

   ```java
   // Go 出处：api/internal/logic/favorite/create_favorite_logic.go:34
   private static final String GO_API_CREATE_FAVORITE = "FavoriteAPI.CreateFavorite";
   // Go 出处：api/internal/logic/favorite/*.go:42~47（硬编码字面量，Go 侧无集中常量表）
   private static final String GO_ATTR_USER_ID = "biz.user_id";
   ```

   ⇒ 守门与被测代码**零同源** ⇒ 「常量被改坏」「业务代码被改坏」「属性 key 被改坏」「tracer 名被改坏」四类变异都会**精确转红**。

2. **本地常量名带 `GO_` 前缀 ＋ 每个都写 Go 侧 `文件:行号`** ⇒ 一眼能看出「这是真源抄来的，不是从代码里引的」。

### 判据式

> **写守门时问一句：这条断言的期望值，能不能在**不改被测代码**的前提下被单独改掉？**
> **不能 ⇒ 它就是同源的 ⇒ 它可能哑。**

### 同族

- **PIT-FAV-API-010**（共享层一改，本域守门的变异守护就哑了）—— 那条讲**变异点**选在**上游拥有的层**；
  本条讲**期望值**与**被测代码**同源 ⇒ ⇒ **两条是「哑」的两个不同来源**，查的时候都要查。
- AGENTS.md §3.3.3「跑绿 ≠ 判据活着」⇒ ⇒ 本条是那条纪律的**一个具体失效形态**。

---

## PIT-FAV-API-013 · 🔴 **JDK `HttpClient` 的 1xx 上限是 8 条且硬编码** ⇒ 撞上 Tomcat 11 的 `103 Early Hints` ⇒ **测试整体失明**

> **一句话**：`ProtocolException: Too many interim responses received: 9 > 8` **不是**「服务端行为异常」，
> **也不是**「请求没打到」⇒ ⇒ **是客户端自己的 1xx 容忍度用完了** ⇒ ⇒ **测试读不到应用层的任何响应**。

### 现象（2026-10-27 实测 · favorite 域 4 条真 Tomcat 用例长期红）

```
java.net.ProtocolException: Too many interim responses received: 9 > 8
```

⇒ ⇒ 但这条异常当时被测试自己的 `catch (IOException)` 翻译成了
「**端口 N 上读到了 0 字节（本请求没打到本测试的 Tomcat？）**」⇒ ⇒ **症状指向了错的方向**
⇒ ⇒ 我据此把根因归到「共享层把容器上限抬到 10001 ⇒ Tomcat 多发 100-Continue」⇒ ⇒ **那个归因是错的**。

### 真根因（裸 socket 实测，不是推测）

```
GET /favorite/v1/item/list?folderId=0&…（2049 个参数）
← HTTP/1.1 103 Early Hints   × 11
← HTTP/1.1 200  {"code":5003,…}
```

⇒ ⇒ ① Tomcat 11.0.24 发的是 **`103 Early Hints`**（**不是** `100 Continue`）；
⇒ ⇒ ② JDK `java.net.http.HttpClient` 对 interim(1xx) 的上限 = **8 条**、**硬编码不可配**
（`jdk.httpclient.*` 属性表里没有对应开关 ⇒ 只能换客户端）；
⇒ ⇒ ③ **被测行为是对的**（200 + 5003 = Go 语义）⇒ ⇒ **红的是测试的传输层**。

### 修法（域内自修 · 零共享层改动）

新增 `favorite-api/src/test/.../support/RealTomcatHttp.java`：JDK 客户端优先 ⇒ 撞「Too many interim responses」
且请求**无 body** 时 ⇒ **回落 `HttpURLConnection`**（透明忽略 1xx）⇒ ⇒ **调用点一行没改**。

### 三条可复用的判据

1. 🔴 **`ProtocolException extends IOException`** ⇒ ⇒ 凡是把 `IOException` 一律翻译成
   「没打到服务端的 0 字节」的**诊断层**，都会**把协议错误误报成连不上** ⇒ ⇒ **症状指向错方向 ⇒ 归因跑偏**。
   ⇒ ⇒ **诊断层要按异常类型分流**，不要一个大 catch。
2. 🔴 **「服务端发了什么」必须看原始字节**（裸 socket），不能只看客户端的异常文案 ⇒
   ⇒ 客户端异常只说「我收到的 1xx 超了」，**不说那 1xx 是什么**。
3. ⚠️ **回落只是让测试能观测到应用层** ⇒ ⇒ **不等于 wire 层偏差已修** ⇒ ⇒
   偏差本身（Go 不发 103 / RAG 发 11 条）**另案上报**，别在「修好测试」时顺手销账。

### 同族

- **PIT-FAV-API-008**（`RANDOM_PORT` 挑的端口可能根本不是你的）—— 那条是「打到了别人」；
  本条是「**打到了、但自己先死了**」⇒ ⇒ **两类都是「请求没到达被测逻辑」** ⇒ ⇒ 症状相似、根因完全不同 ⇒
  ⇒ **判据要能分开它们**（本条的分界 = 异常类型：`ProtocolException` vs `IOException` 且「读到 0 字节」）。

## PIT-FAV-API-014 · 🔴 **守门类 `import` 了被测依赖 ⇒ 抽依赖的变异只能拿到「编译失败」** ⇒ ⇒ 那**不是**「运行时退化」的证据

### 现象（2026-10-28 · 给 favorite-api 落池时实测）

跑「**抽掉 pom 的 `commons-pool2`**」这条变异 ⇒ ⇒ **测试根本没跑到** ⇒ maven 在 **test-compile** 阶段就挂：

```
[ERROR] FavoriteApiRedisPoolContractTest.java:[8,37] 程序包org.apache.commons.pool2.impl不存在
[ERROR] Failed to execute goal ...maven-compiler-plugin:3.14.1:testCompile ... Compilation failure
```

⇒ ⇒ **拿不到任何「红在目标断言」的证据** ⇒ ⇒ 而这条变异的本意恰恰是回答
「**这条依赖承不承重？**」⇒ ⇒ **工具在这一格上失灵了**。

### 根因（两层，一层套一层）

1. **守门类直接 `import org.apache.commons.pool2.impl.GenericObjectPoolConfig`**
   —— 为了断言 `getMaxTotal()` / `getMaxIdle()` / `getMinIdle()` / `getMaxWaitDuration()`。
2. ⇒ ⇒ 抽掉依赖 ⇒ **编译期就挂** ⇒ ⇒ **运行时那条路根本没走到**
   （真正要验的是：Boot 的 `LettuceConnectionConfiguration$PoolBuilderFactory` 构造
   `GenericObjectPoolConfig` ⇒ 类不在 ⇒ 启动期 `NoClassDefFoundError`）。

⇒ ⇒ 🔴 **推论**：「这条依赖是承重的」这个**结论仍然成立**（甚至更强）⇒
但它证明的是「**对本守门不可省（编译期）**」，**不是**「**应用启动期会 `NoClassDefFoundError`**」。

### 🔴 危险处：证据强度会被悄悄写高

变异记录里如果写「**1 红，`NoClassDefFoundError` ⇒ 启动期直接失败**」，
而那条证据**只可能来自一个「绕开了编译期」的探针** ⇒ ⇒
**看记录的人无法判断它跑的是哪条路** ⇒ ⇒ **证据强度被写高了一格**。

⇒ ⇒ **本仓既有实例**：`hot-rpc` 台账 H-9 的 MUT-E 记的就是「1 红 `NoClassDefFoundError` ⇒ 启动期直接失败」
⇒ ⇒ 而 `HotRpcRedisPoolContractTest.java:8` **也直接 import 了该类** ⇒ ⇒ **它记的取证方法跑不出这个结果**
⇒ ⇒ 本轮已用「应用启动探针」重验并**订正取证路径**（见 `docs/DRIFT/hot/vs-common.md` H-9 的 📌 注记
＋ `docs/DRIFT/favorite/api.md` §19.6）⇒ ⇒ **原结论保留，只把取证路径与证据强度改对**。

### 正确做法（可复用，三步）

1. **把「import 了该类」的守门挪走**（否则编译期先挂，跑不到运行时）；
2. 跑**真 Spring 上下文**的守门（不是 MockMvc、不是纯 Mockito）；
3. 看**异常链**（`Caused by: java.lang.NoClassDefFoundError: …` ＋ `ClassNotFoundException`）
   ⇒ ⇒ **这条才是 L4（真启动实测）**，而源码推断只能写 L1/L3。

⇒ ⇒ 本轮实测的两个探针（**均 8 errors**）：

| 域 | 探针 | 注解 |
|---|---|---|
| favorite-api | `FavoriteApiRealTomcatGateTest` | `@SpringBootTest(webEnvironment = DEFINED_PORT)` |
| hot-rpc | `HotRpcSpringContextWiringTest` | `@SpringBootTest(webEnvironment = NONE)` |

⇒ ⇒ 链路（两侧一致）：`redisConnectionFactory`（`LettuceConnectionConfiguration`）
⇒ `Factory method 'redisConnectionFactory' threw exception with message: org/apache/commons/pool2/impl/GenericObjectPoolConfig`
⇒ ⇒ **启动期硬失败**，**不是**「静默变无池」。

### 判据（下次照着做）

- 一条变异要算「**守门识别该退化**」，必须**编译通过 ＋ 红在目标断言**
  ⇒ ⇒ 「编译失败」只能算「**这条依赖对本守门不可省**」，**两件事别混写**。
- 看到变异记录里写着**运行时异常**（`NoClassDefFoundError` / `ClassNotFoundException`）
  ⇒ ⇒ **先问一句「守门类 `import` 了它吗」** ⇒ import 了 ⇒ ⇒ **那条证据必然来自别的探针**
  ⇒ ⇒ **要么把那个探针补上，要么把记录降级**。

### 同族

- 与 **PIT-FAV-API-012**（守门断言的期望值若引用「被测代码的常量」⇒ 那条守门是哑的）**同源**：
  都是「**守门自己站在被测对象身上**」⇒ 一改就一起变 ⇒ 守门就哑了。
  本条是「守门站在**依赖**上」；那条是「守门站在**常量**上」。
- 与 **PIT-FAV-API-011**（yaml 里没写 `lettuce.pool.enabled` ≠ 池没开 —— Boot 4 的判据是 **classpath**）**互补**：
  那条讲「**classpath 决定池开不开**」；本条讲「**classpath 上的类被 `import` 进来，
  会把「classpath 缺了」这件事挡在编译期**」⇒ ⇒ **同一个 classpath 事实，两种观测面**。

## PIT-FAV-API-015 · 🔴 **真 Tomcat 守门挂了几百秒 ⇒ 先用【同一个请求】换传输层复跑，再判「代码有 bug」还是「环境假挂死」**

> 登记日期 **2026-10-28** · 发生域 **favorite-api** · 类型 **环境 flake（假红）**
> · 证据强度 **L4（同一 JVM 里三种传输层同时跑）**

### 现象（原始记录，不修饰）

第一次跑 `FavoriteApiRealTomcatGateTest`（新增「query 段数闸门」那 5 条之后）：

```
Tests run: 13, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 1020 s <<< FAILURE!
querySegmentLimitIsUniformAcrossEndpoints -- Time elapsed: 982.5 s <<< ERROR!
java.net.http.HttpTimeoutException: request timed out
```

⇒ ⇒ **两个数字自相矛盾**：请求超时只配了 **30 s**（`HttpRequest.timeout(30s)`），
**却过了 982.5 s 才抛超时** ⇒ ⇒ ⇒ **「超时」这件事本身没按时发生**
⇒ ⇒ 所以「服务端慢」这个解释**不成立**（服务端再慢，30 s 也该超时了）。

### 🔴 关键判别动作：**用【同一个请求】换传输层复跑**

不是重跑一遍（重跑还是可能挂），而是**换观测面**：裸 socket ＋ JDK `HttpClient` ＋
`HttpURLConnection` 各打一次**完全相同的 URL**（同段数 · 同 token）：

| 用例 | 段数 | token | 传输层 | 实测 |
|---|---|---|---|---|
| B | 10001 | 有 | 裸 socket | **5.2 s** ⇒ `4×103 Early Hints` ＋ `HTTP/1.1 200` ＋ `{"code":5003,"msg":"参数错误","data":null}` |
| F | 10001 | 有 | JDK `HttpClient` | **5.4 s** ⇒ `200` ＋ `{"code":5003,…}` |
| G | 10001 | 有 | `HttpURLConnection` | **5.3 s** ⇒ `200` ＋ `{"code":5003,…}` |

⇒ ⇒ **三种传输层、秒级、结论一致** ⇒ ⇒ ⇒ **服务端行为是确定的** ⇒ ⇒
**那 982.5 s 不是「代码有 bug」**。

### 真正的成因：JVM 被饿住 ⇒ **超时计时器线程拿不到 CPU**

第一次跑的时候，**同时**有三条 maven 在跑：G4 的全量回归 ＋ G1 的 6 模块回归 ＋ 我这一条
⇒ ⇒ 24 GB 机器上 3 条 `-Xmx2g` 的 maven ＋ surefire JVM ⇒ ⇒ **CPU 饥饿**
⇒ ⇒ ⇒ **JDK 的超时是「定时器线程到期才触发」** ⇒ ⇒ 那个线程拿不到 CPU ⇒ ⇒
**30 s 的超时被拖到 982.5 s 才生效** ⇒ ⇒ ⇒ 与现象逐字吻合。

⇒ ⇒ **复跑（独占窗口，无并发）** ⇒ `FavoriteApiRealTomcatGateTest` ⇒
**13 条 / 0 failures / 0 errors / 0 skipped / 42.87 s / BUILD SUCCESS** ⇒ ⇒ **坐实是环境**。

### 判据（下次照着做）

1. **「超时」类失败，先怀疑「超时有没有按时触发」** ⇒ ⇒ 比对**「配置的超时值」与「实际耗时」**
   ⇒ ⇒ **两者差一个数量级 ⇒ 说明计时器没被调度 ⇒ 先查环境（并发跑批 / 机器负载），不要先改代码**；
2. **判「服务端挂 vs 客户端挂」⇒ 换观测面，不是重跑** ⇒ ⇒ **裸 socket** 是最硬的一档
   （拿到未经客户端加工的原始字节 ⇒ 连共享层写的 `103 Early Hints` 都看得见）；
3. **同一个探针要能连一个「已知可用对端」** ⇒ ⇒ 本次三传输层互为对照 ⇒ ⇒ 结论不靠推测；
4. ⚠️ **别用「日志里有没有 ERROR」判红红** ⇒ ⇒ 本次日志里满屏 `ERROR`
   （`[otel] error: …` / `Failed to export spans` / `gRPC handler panic`）**全部是故意的测试场景**
   ⇒ ⇒ **只认 surefire 三项 ＋ mvn 退出码**。

### 落点

- 诊断探针**已常驻仓内**：`service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/probe/QuerySegmentRawSocketProbe.java`
  （**类名不带 `Test` 后缀 ⇒ 不进 surefire 默认 includes ⇒ 对常规回归零影响** ⇒ 需时显式跑：
  `mvn -B -o -pl service/favorite/favorite-api -am test -Dtest='QuerySegmentRawSocketProbe' -Dsurefire.failIfNoSpecifiedTests=false`）；
- 事件台账：[`docs/DRIFT/favorite/api.md`](../../DRIFT/favorite/api.md) **§21.8**。

### 同族

- 与 **PIT-FAV-API-008**（`RANDOM_PORT` 挑的端口可能根本不是你的 ⇒ 请求打到 PostgreSQL 上）
  **同源**：都是「**请求没打到你以为的地方**」⇒ ⇒ 那条是「打到了别的进程」；
  本条是「**根本没到服务端**（客户端侧被环境拖死）」⇒ ⇒ 两条的判别动作一致：
  **换一个观测面，确认字节真的到了该到的地方**。
- 与 **PIT-FAV-API-013**（JDK `HttpClient` 的 1xx 上限 ⇒ 撞上 Tomcat 11 的 `103 Early Hints` ⇒ 测试整体失明）
  **同源且互补**：那条讲「**客户端读不到应用层响应**」；本条讲「**客户端等不到超时**」
  ⇒ ⇒ 两条都是「**客户端这一侧的静默失效**」⇒ ⇒ 而 Go 侧 `net/http` 两种都不存在。
- 与 **PF-ARTICLE-RPC-016**（Testcontainers PG 宿主口错位 ⇒ `EOFException` 环境 flake 真签名）
  **同族**：都是**环境 flake** ⇒ ⇒ ⇒ 处置纪律一致：
  **先证明「代码是对的」，再把 flake 登记成 flake**，⇒ ⇒ **不许拿「跑红」当「代码有 bug」去改被测对象**。

---

## PIT-FAV-API-016 · 🔴 **拿到「U+FFFD 受保护名单」⇒ 先分两类、再核前提**：**「题材/被测对象」要留真字节，「台账引述的损坏样本」必须换成可见记号**

> 触发场景：2026-10-28 参与 G2 牵头的**全仓 U+FFFD 全域扫**（G2 给出「受保护名单 19 处」+ 「4 条树间读数」）。

### 现象（原始记录，不修饰）

- G2 的名单写「**受保护名单 = 19 处**」＝ 题材/被测对象 **6** ＋ G1 域台账证据表 **13** ⇒ 并注明「**这 19 处不许批量替换**」。
- 我逐条复核（**在目标树实测**）⇒ ⇒ **那 13 条在扫描源树里根本不存在**（实测 `docs/DRIFT/users/**` 的 U+FFFD ＝ **文件 0 / 处数 0**）。
- 原因：**G1 已把这 13 处清零**，并且**刻意不把真字节写回文档** —— 改用**可见记号** `⟪U+FFFD×n⟫`（原文见 `docs/DRIFT/users/admin/rpc.md:2483-2487`）：
  > ⚠️ **本表里用 `⟪U+FFFD×3⟫` 记号表示损坏样本，刻意不把真的替换字符写进本文件** —— 否则将来加「全仓字节合法性扫描」时，**本文件自己会变成假阳性**，守门网会被一条「文档里引用了损坏样本」的记录挡住。
- ⇒ ⇒ **真·受保护名单 = 6 处**（题材那 6）⇒ ⇒ 整合者若按 19 条去核对，**会对着不存在的 13 条找**。

### 🔴 根因：**「受保护」这个词盖住了两类【处理方式相反】的对象**

| 类 | 是什么 | 真字节该不该留 | 留了会怎样 |
|---|---|---|---|
| **① 题材 / 被测对象** | 测试**断言里**的字符串（`assertFalse(body.contains("«FFFD»"))`）、**测试载荷本身** | **必须留** | 改了断言 ⇒ **永远为真** ⇒ 静默失效；改了载荷 ⇒ **永远找不到** ⇒ 假红 |
| **② 台账里引述的损坏样本** | 文档**展示**损坏字节的证据表 | **必须换成可见记号** | 留着 ⇒ **全仓字节扫描自己变假阳性** ⇒ 守门网被一条「文档引用了损坏样本」挡住 |

⇒ ⇒ **一份不分类的名单 ⇒ 处置必然出错**：要么让人白找不存在的条目，要么让人把 ① 的真字节当垃圾清掉（**静默失效比红更坏**）。

### 判据（下次照着做）

1. **名单类事项必核前提** —— 名单是**快照**；它来自哪棵树，那棵树可能已经变了 ⇒ ⇒ **在目标树实测该处是否真存在**，别照单核对。
2. **先分类再动手** —— ① 题材/被测对象（真字节留）vs ② 引述/证据表（换可见记号）⇒ 两类**处理方式相反**。
3. **凡要在文档里展示损坏字节，一律用 `⟪U+FFFD×n⟫` 之类可见记号**（G1 已有先例 ＋ 原文论证）⇒ ⇒ **全仓字节扫描才不会假阳性**。
4. **引用损坏样本的行号必须带「树」限定** —— 同一物理位置在**不同树行号可能差 1**（本例 `UserApiGoStyleBodyContentTypeTest.java` 的 `assertFalse(body.contains(...))` 在我树/G2 树/G4 树是 `:376`，G1 树是 `:377`）⇒ ⇒ 否则会把「**行号漂移**」误判成「**引用错误**」。

### 本域实测（G3 树 @ `cf55cff` · 2026-10-28）

| 项 | 值 |
|---|---|
| 全树 U+FFFD | **10 文件 / 23 处**（G2 报的 34 − **11** ⇒ tools 6 + component-semantic 5 ⇒ **已随 `cc6e38e` / `f68a9cd` 随合并带进我树** ⇒ 实测 `tools/` **0 处** + `component-semantic-verification/` **0 处**）|
| 分类 | 题材 **6**（受保护）· G1 域源 **8** · G2 域源 **9**（两类 = **真损坏**，非题材）⇒ 归各域自修 |
| **G3 域** | **0 处待办** —— 唯一 2 处 = `service/article/article-api/.../ArticleApiMvcPipelineTest.java:1143,1159`（**测试载荷本身就是 U+FFFD**）⇒ 受保护 ⇒ **不动** |
| G2 信里另两条读数 | 「G3 工作区 49 个未提交改动」⇒ **不成立**（`git status --porcelain` = **0 行**）·「只有 G3 树停在 `69e3262`」⇒ **不成立**（`cc6e38e`/`f68a9cd` **已在我树**，`git branch --contains` 我树带 `*`）|

### 同族

- 与「**台账里的『已修 / 未修』断言必须带核对日期，否则按 `UNKNOWN` 处理**」**同源** —— 名单也是**快照**，不是事实。
- 与「**你以为的现状 ≠ 实际现状**」「**零命中是假信号**」**同源** —— 别人给你的读数是**它那棵树在它那刻**的读数。
- 与「**行号是快照不是锚点**」**同族** ⇒ ⇒ 引用必须带**树 + commit** 限定。
