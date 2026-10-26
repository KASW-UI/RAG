# global 第十四轮 —— X-6 落地：共享层按「Go 逐域注册的拒绝形状」复刻 form 值上限闸门

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`ed17341`
> **性质**：**代码改动（共享层 6 个类 + 9 个域各一行 yaml）+ 守门 + 台账改写**
> **本轮不碰**：`favorite` 域的**代码 / 配置 / 测试 / 台账**（用户重申「你没有 favorite 的所有权限」）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二·十一（X-6 / `DRIFT-HOTAPI-030`）：

> **Go 数「非空值」（2048），Tomcat 数「参数总数」** ⇒ 残余窗口「总数 ≥ 2048 且非空值 ≤ 2048」
> ⇒ **Go 放行 / RAG 被 Tomcat 先拒（400 + HTML 错误页）** ⇒ 同一个请求在 10 个域里 10 种行为。

第十一轮已定案「**共享层修法存在 ⇒ 可修、favorite 零触碰**」，第十三轮写好了 spec，
但**修法草案里有一个隐含前提在落地前被取证推翻** ⇒ 本轮落地的是**修正后的**结构。

---

## 二、🔴 核心发现：**Go 的拒绝形状是「按域而异」的**

Go 的 `httpx.Parse` 失败 → `httpx.ErrorCtx` → `httpx.SetErrorHandler` 出口
（go-zero `rest/httpx/responses.go:141-172` 的 `doHandleError`）：

```go
if handler == nil {                       // ← 该 api 没注册 SetErrorHandler
    http.Error(w, err.Error(), http.StatusBadRequest)     // 400 + text/plain
    return
}
code, body := handler(err)                // ← 注册了 ⇒ 业务信封（200 + {code,msg,data}）
```

| Go 侧 | 域（出处） | wire 形状 |
|---|---|---|
| ✅ 注册了 | `hot/api/hotcenter.go:53` · `follow/api/followcenter.go:53` · `like/api/likecenter.go:48` · `message/api/messagecenter.go:53` · `comment/api/commentservice.go:47` | **200** + `{"code":5001,"msg":"<该域文案>","data":null}` |
| ❌ 没注册 | `article/api` · `user/api` · `task/api` · `favorite/api` | **400** + `text/plain; charset=utf-8` + `nosniff` + Go 原文 |

⚠️ **各域 5001 文案不同**：hot / follow / message = `"internal server error"`；like / comment = `"系统内部错误"`。

⇒ ⇒ **共享层写不出信封**（文案在域自己的错误码表里）⇒ 拒绝形状**必须由域自己渲染**。

---

## 三、改动

### ① `service/common` 6 个新件

| 件 | 职责 |
|---|---|
| `GoStyleFormValues` | **Go 判据**（`net/url` 三条错误 + 2048 个**非空值**计数 + Go 原文）——**只出判据，不出响应**（从 `article-api` 的旧 filter 迁来 + 重写 javadoc） |
| `GoStyleFormLimitMode` | 模式 `off` / `raw` / `envelope`；**非法值在构造期就炸**（不静默兜底） |
| `GoStyleFormLimitExceededException` | envelope 模式抛出；**刻意不继承 `BusinessException`** |
| `GoStyleFormValueLimitInterceptor` | 闸门本体（**`HandlerInterceptor`**，不是 servlet `Filter`） |
| `GoStyleFormLimitWebMvcConfig` | 注册拦截器（`order = HIGHEST_PRECEDENCE`） |
| `GoStyleTomcatParameterLimitCustomizer` | 容器上限 → **10001**；**仅非 `off` 模式生效** |

### ② 9 个域各一行 yaml

```yaml
rag:
  http:
    go-form-limit:
      mode: raw        # 或 envelope
```

| 模式 | 域 |
|---|---|
| `raw` | article · user-api · admin-api · task |
| `envelope` | hot · like · follow · message · comment |
| **缺省（off）** | **favorite** ⇒ **容器上限也不动、闸门也不装 ⇒ 行为逐字节不变** |

### ③ 顺带订正 `service/common/pom.xml`

`spring-web` / `spring-test` 原先**硬编码 `7.0.0`**，而 `spring-boot-starter-web` 4.0.8 带的是 **7.0.9**
⇒ 同一条 classpath 上两个 Spring Framework 版本 ⇒ `ObservabilityWiringTest` 加载时
`NoClassDefFoundError: org/springframework/web/accept/ApiVersionHolder`（该类 7.0.0 没有、7.0.9 才有）
⇒ **去掉显式 version，交 BOM 管**（与同文件里 `spring-webmvc` 的既有写法一致）。

---

## 四、Go 出处（逐条）

| 事实 | 出处 |
|---|---|
| form 值上限 2048 | go-zero `rest/httpx/util.go:15` `maxFormParamCount = 2048` |
| **空值不计数** | `rest/httpx/util.go:44-45` `if len(v) == 0 { continue }` |
| 超限文案 | `rest/httpx/util.go:48-53` `fmt.Errorf("too many form values, error: %s", r.Form.Encode())` |
| query 段数上限 10000 | Go 1.25 `net/url/url.go:1001` `defaultMaxParams = 10000`（`:1020-1022` 在解析**前**就 return） |
| 分号 / 转义两条错误 | `net/url/url.go:1023-1054` |
| 拒绝形状按域而异 | go-zero `rest/httpx/responses.go:141-172` + 各域 `SetErrorHandler` 注册处 |
| 5001 文案按域而异 | `hot/common/errmsg.go:53` · `follow/common/errmsg.go:60` · `message/common/errmsg.go:56`（英文）· `like/common/errmsg/errmsg.go:73` · `comment/common/errmsg/errmsg.go:71`（中文） |
| Tomcat 数的是「总数」 | tomcat-embed-core 11.0.24 `tomcat/util/http/Parameters.java:79`（`-1` = 不限）`:247`（`limit > -1 && parameterCount >= limit`） |
| Tomcat 惰性解析 | `Parameters.processParameters` ⇒ 只在应用调 `getParameter*` 时才解析并计数 |
| 容器上限可运行期改 | `catalina/connector/Connector.java:538` `setMaxParameterCount(int)`；默认 1000 在 `:251` |
| Boot 接线顺序 | Boot 4.0.8 `org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer.getOrder()` **= 0** ⇒ 共享层取 `LOWEST_PRECEDENCE` ⇒ 必然在后 |

---

## 五、实测依据（§3.3 真容器 / 真 Spring MVC）

| 守门 | 覆盖 | 结果 |
|---|---|---|
| `GoStyleParamLimitGateTest`（raw，真 Tomcat + 真 MVC 链） | 容器上限 = 10001 · 2048 放行 · 2049 ⇒ 400 + Go 原文**逐字** · **3000 个空值 ⇒ 200（X-6 窗口）** · 10001 段 ⇒ 400 + 段数文案 | ✅ 5/5 |
| `GoStyleFormLimitEnvelopeGateTest`（envelope，真 Tomcat + 真 MVC + 真 advice） | 2049 ⇒ **200 + 业务信封**（`code=5001` / msg 由**域的替身**提供 / `data:null`）· 2048 放行 · 3000 空值放行 · 容器上限 10001 | ✅ 4/4 |
| `GoStyleFormLimitModeTest` | `off` 放行且不写响应 · `raw` 写 400 + Go 原文 · `envelope` 抛异常且**不写**响应 · 非法值构造期炸 | ✅ 8/8 |
| **变异测试** | M1 `MAX_PARAMETER_COUNT` 10001→2048 ⇒ **3000 空值那条转红**；M2 非空值计数恒 0 ⇒ **2049 那条转红** | ✅ 两条机制都有判别力 |
| `tools/difftest/check-tomcat-limits.py` | 新增第四旋钮「form-gate 模式」+ **favorite 无 mode 的零触碰守卫** | ✅ EXIT=0 |
| 9 个 api 模块既有测试 | article / hot / like / follow / message / comment / task / user-api / admin-api | ✅ 全绿（article 的 1 条**过期断言**已按 §3.3「先改错误断言」更新） |

---

## 六、⚠️ 残留（有界）

1. **鉴权与闸门的先后**：Go 是「先鉴权（`rest.WithJwt` 中间件）后解析（handler 内 `httpx.Parse`）」；
   RAG 的拦截器 `order = HIGHEST_PRECEDENCE` ⇒ 「先解析后鉴权」
   ⇒ 「既无合法 token、又带 2049 参数」的请求：**Go 401 / RAG 400 或 5001**。
   ⚠️ **修之前就存在**（`article-api` 的同名 filter 一直跑在鉴权之前）⇒ 本轮**刻意不改**（避免扩大 scope）。
2. **未匹配路由不触发闸门**（Interceptor 只对匹配到的 handler 生效）⇒ **与 Go 一致** ⇒ 是**修正**，不是残留。

---

## 七、不做（明确排除）

- ❌ **不动 `favorite` 的任何文件** ⇒ 它的 yaml **一个字节没改** ⇒ 容器上限仍是 2048、闸门不装。
- ❌ **不写 `docs/DRIFT/article/api.md`**（不在 global 的台账写权内）⇒ 该文件里对旧类名
  `GoStyleGetFormValuesFilter` 的引用**留给 article 域 owner** 顺带订正（已在 global 台账标注）。
- ❌ **不改 10 份 yaml 里的 `max-parameter-count: 2048`** ⇒ 它是「名义值」，运行期真值由共享层接管；
  静态检查已断言「yaml 统一 2048 + mode 齐备」。
- ❌ **不碰三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`**（不在 global 写权内）。

---

## 八、验证

- `mvn -o -pl service/common -am test` ⇒ **全绿**（含 3 个新守门类）
- `mvn -o test -pl <9 个 api 模块>` ⇒ **全绿**
- `python3 tools/difftest/check-tomcat-limits.py` ⇒ **EXIT=0**
- 变异测试 M1 / M2 ⇒ **各有一条转红**
