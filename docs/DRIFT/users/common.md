# DRIFT 台账 · `service/users/users-common`

> **本组件是 users 域首次单独做组件级对照的 common**（AGENTS.md §0「5 个 `*-common` 本轮未单独审查」在本组件上失效）。
> 另两个 `*-common`（`hot/common`、`common`）的结论**不得**外推到本文件 —— §3.4 的效力边界同型。
>
> **为什么 common 不是可选项**：本模块被 `user-rpc` / `admin-rpc` / `user-api` / `admin-api` **四方共用**，
> 它同时是错误码表、请求绑定规则、JWT 编解码、建表执行器的**唯一基准**。
> §3.5 明确：少审 common =「拿一面没人校过的镜子照自己」，与 §3.1 禁止的
> 「拿现有 RAG 代码当对照基准」同源。本轮 5 条差异里有 3 条是**此前三个组件台账都没发现**的。

---

## 0. 元信息与证据强度

| 项 | 值 |
|---|---|
| 核对日期 | **2026-10-23**（22:00–22:25 实跑） |
| 组件 | `service/users/users-common`（12 个 main 类 + 4 份 DDL + 2 个测试类，1,532 行） |
| Go 对照面 | `service/user/common/{cryptx/password.go, errmsg/errmsg.go, jwt/jwt.go}`（**全部 3 个文件，无遗漏**） |
| **证据强度** | ✅ **双侧活体对拍**（Go 进程 ↔ JVM 进程，逐条逐字节比对；非静态源码、非单侧推断） |
| Go 侧探针 | `/tmp/commonprobe/go/`（`errmsg`/`jwt`/`cryptx` 72 行）、`go3/`（bearer/`core/mapping` 46 行）、`xverify/`（交叉验签）、`alcheck/`（alg 固定消费者） |
| RAG 侧探针 | `/tmp/commonprobe/CommonProbe.java`（**调用 `users-common` 的真实类**，无重写）、`XVerify.java`、`SigDebug.java` |
| 冻结仓完整性 | `go.mod` 用 `replace` 指令**只读引用**真源包；跑完 `git status` 与探针前完全一致（仅 2 个先前存在的未跟踪文件） |
| 依赖版本 | Go：`golang-jwt/jwt/v4@v4.5.2`、`go-zero@v1.10.2`、`x/crypto@v0.53.0`、Go 1.25.11<br>Java：**jjwt 0.12.6**、`spring-security-crypto 7.0.7`、JDK 21 |

### 本轮的方法论要点（踩过的坑都写在这）

1. **探针自己也会假红** —— 首次 jwt 探针把 `decodeSegment` 的成功/失败返回长度写反（1 vs 2），
   导致 `alg`/`exp` 全部静默变成 `null`，而**探针仍然 exit 0**。
   ⇒ 「跑通」不等于「测到了」，这是 `docs/Pitfalls/_cross/testing-pitfalls.md` 的又一实例。
2. **过期时间戳会掩盖真正的判据** —— 交叉验签首轮用固定 `iat=1700000000`（2023-11-14），
   两侧全部报「已过期」，**签名是否一致这一真正的问题一次都没被测到**。
   ⇒ 与 `docs/DRIFT/users/user/api.md` §4 记录的「残留 `dt_user1` 导致 3 条 unexpected」同型。
3. **推理不能替代实测**（§3.3）—— 见到 `padToHmacKeySize` 的 zero-pad，
   按密码学常识应当推出「两侧 token 互相验不过」。
   **实测结论相反**（见 §3 的 `PAD-01`）。若只做静态推理，本轮会写出一条**假漂移**进台账。

---

## 1. Component Contract（对照范围）

| 维度 | Go `service/user/common` | RAG `service/users/users-common` |
|---|---|---|
| 包数 | 3（`cryptx` / `errmsg` / `jwt`） | 6（`cryptx` / `errmsg` / `jwt` / `http` / `ddl`） |
| 行数 | 18 + 76 + 15 = **109** | 1,389 main（另 4 份 DDL 308 行、2 个测试 229 行） |
| 消费者 | user-rpc / admin-rpc / user-api / admin-api | 同（**四方共用**） |
| 超集部分 | — | `http/*`（go-zero `core/mapping` 的 Java 等价物）、`ddl/*`（GORM `AutoMigrate` 的等价物）**Go 侧没有同名包**，对照面在 go-zero 与 gorm 的库代码 |

> ⚠️ **`http/*` 与 `ddl/*` 的 Go 对照面不在 `service/user/common`**，而在 go-zero / gorm 的**库源码**。
> 本轮对这两组的取证走的是「**直接调用库的真源函数**」（`core/mapping.UnmarshalJsonMap`、
> `gorm` 的建表），不是抄源码重跑 —— 见 §3 的 `MAPPING-01` / `BEARER-01`。

---

## 2. 逐条对照结果总表

| 组 | 对照条数 | 一致 | 差异 |
|---|---|---|---|
| `errmsg`（码值 → 文案） | 39 | 37 | **2** |
| `jwt`（header/payload/alg） | 11 长度档 × 2 面 | 9 | **2**（另 1 条被证伪） |
| `cryptx`（bcrypt 判定谱） | 19 | 15 | **2**（另 2 条判为等价） |
| `bearer`（两套取法） | 11 × 2 | **22** | **0** |
| `,string` int64 | 17 | **17** | **0** |
| 必填校验（两条文案） | 4 | **4** | **0** |
| 交叉验签（6 个密钥档） | 6 × 2 向 | **12** | **0** |
| **合计** | **176** | **159** | **6**（含 4 条降级 / 1 条证伪） |

---

## 3. 差异明细

### DRIFT-USERSCOMMON-001 🔴 `ErrorCode.getMsg(1017/1018)` 有文案，Go 的 `codeMsg` 里**没有这两条** —— ⏸️ **潜伏，跨域待裁决**

> **本域（users/user）内结论**：三个调用点**全是日志码、wire 观察不到** ⇒ 在本组件范围内**不构成可观察漂移**。
> 真正会进 wire 的是 `admin-api` 的两处 ⇒ **需 admin 域签字**，属跨域项。详见本节末尾「处置」。

**性质：OBSERVABLE DIFFERENCE（潜伏 · 在 `users/user` 内不进 wire）**

**Go 侧事实（双侧活体）**：

```
{"code":1017,"msg":"FAIL"}     ← errmsg.go:30 定义了常量，但 :38-68 的 codeMsg 没有它
{"code":1018,"msg":"FAIL"}     ← errmsg.go:31 同上
```

**根因（Go 源码，逐行）**：
- `errmsg.go:30-31` 定义 `ErrorRequestParam = 1017` / `ErrorAvatarHistoryNotExist = 1018`
- `errmsg.go:38-68` 的 `codeMsg` map **逐条核对后确认不含这两个键**
- `errmsg.go:70-76` `GetErrMsg`：`msg, ok := codeMsg[code]; if !ok { return codeMsg[Error] }`
  ⇒ 未登记的码回落成 `codeMsg[Error]` = **`"FAIL"`**

**RAG 侧事实**：`ErrorCode.java:34-35` 给两个码都配了中文文案
（`ERROR_REQUEST_PARAM(1017, "请求参数错误")` / `ERROR_AVATAR_HISTORY_NOT_EXIST(1018, "头像历史不存在")`）。

**影响面（本轮实测，**必须按调用点判，不能只看 primitive**）**：

| 调用点 | 用途 | 是否进 wire |
|---|---|---|
| `user-rpc/UserDomainService.java:198` | `logger.logBusinessErr(...)` | ❌ 仅日志 |
| `user-rpc/UserDomainService.java:623` | `logger.logBusinessErr(...)` | ❌ 仅日志 |
| `user-rpc/UserDomainService.java:686` | `logger.logBusinessErr(...)` | ❌ 仅日志 |
| `admin-api/AdminApiSupport.java:55-56,62-63` | `new BusinessException(1017, "请求参数错误")` | ✅ **进 wire** |

⇒ **在 `users/user` 的三个调用点全部是日志码，wire 上观察不到。**
`admin-api` 那两处会进 envelope —— **属 admin 域，且本轮用户口径是「跨域项只取证不动手」**。

**为什么仍要留档（不降级为「无影响」）**：
1. Go 侧 1017/1018 **全仓零调用点**（`grep -rn "ErrorRequestParam\|ErrorAvatarHistoryNotExist"` 只命中 `errmsg.go` 的两行定义）——
   即 **Go 的这两个码是死码，永远不会被返回**；RAG 却有活跃调用点。
2. 一旦将来有任一处把日志码改成 wire 码，差异立刻变成双侧可见，且**没有任何测试会红**。
3. `AdminApiSupport.java:44-49` 的 javadoc 写着「与参考侧 `parseLongOrNull` → `LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM)`」
   —— 按 §3.1「**不要拿代码注释当对照基准**」，这条出处指的是 Java 镜像期，不是 Go 真源，
   且 Go 侧该码**压根不会被返回**。这条注释有误导性。

**处置**：⏸️ **本轮不改**。改法有两种且后果相反：
- 对齐 Go ⇒ 1017/1018 的 msg 变 `"FAIL"` ⇒ **同时改变 admin-api 的 wire 行为**（跨域，需 admin 会话签字）
- 保留现状 ⇒ 登记为潜伏差异 + 补一条守门测试锁住「日志码不得进 wire」

⇒ **需与 `align/users-admin` 会话合并裁决**（与 `DRIFT-USERRPC-V-002` 属同一类「DDL/枚举归属跨域」的裁决）。

---

### DRIFT-USERSCOMMON-002 🔴 JWT header 缺 `"typ":"JWT"` —— ✅ **已修（首轮，`JwtUtil.java:24` `.header().add("typ", "JWT")`）**

> 守门网：`JwtUtilParityTest`（**22 条**，含 11 档密钥长度 × alg 必为 HS256 + 5 档签名长度 + 6 条其余断言）。

**性质：OBSERVABLE DIFFERENCE（wire 可见 · 字节级）**

**双侧活体，wire 原始文本**：

| 密钥 | Go `HEADER` | RAG `HEADER` |
|---|---|---|
| 15 B | `{"alg":"HS256","typ":"JWT"}` | `{"alg":"HS256"}` |
| 36 B | `{"alg":"HS256","typ":"JWT"}` | `{"alg":"HS256"}` |
| 48 B | `{"alg":"HS256","typ":"JWT"}` | `{"alg":"HS384"}` |
| 64 B | `{"alg":"HS256","typ":"JWT"}` | `{"alg":"HS512"}` |

**Go 出处（消费点，非声明点）**：`golang-jwt/jwt/v4@v4.5.2/token.go` 的 `Token.SignedString`
→ `SigningMethodHS256.Sign()` 写死 `{"alg":"HS256","typ":"JWT","kid":...}`（kid 为空则省略）后 base64url。
⇒ **`typ` 是 Go 侧无条件写出的**。

**RAG 修法**：`Jwts.builder()` 默认 header 只有 `alg`（jjwt 0.12 不自动补 `typ`），
需显式 `.header().add("typ", "JWT")`。

**影响面**：绝大多数验签方忽略 `typ`，故当前**不导致验签失败**（§3 的交叉验签 12/12 全通即证据）；
但它是 JWT header 的**第一个标准字段**，第三方工具链 / 网关 / 审计日志会看到差异。

---

### DRIFT-USERSCOMMON-003 ⚠️ JWT payload 的 claim **顺序**不同（语义等价，字节不同） —— ✅ **判为不修（字节级语义等价）**

> **性质修正**：它不是「漂移」，而是**表述差异**（JSON 对象键序无语义，claim 集合与取值两侧完全一致）。

**性质：OBSERVABLE DIFFERENCE（字节级 · 语义等价）**

| 侧 | `PAYLOAD` |
|---|---|
| Go | `{"exp":1700003600,"iat":1700000000,"userId":1234567890}` |
| RAG | `{"iat":1700000000,"exp":1700003600,"userId":1234567890}` |

- Go 侧顺序 = `jwt.go:9-11` 的**字面量书写顺序**（`exp` → `iat` → `userId`），golang-jwt 用 `MapClaims`（`map[string]interface{}`），
  序列化时**按键名排序** ⇒ `exp < iat < userId`。
- RAG 侧顺序 = jjwt 按 builder 的调用顺序输出（`issuedAt` → `expiration` → `claim`）。

⇒ claim **集合与取值完全一致**（两侧都是 JSON 数字型，非字符串型），JSON 对象键序无语义。
**不建议改**：jjwt 没有暴露「自定义键序」的 API；要对齐只能手工拼 base64，等于绕过 jjwt 的签名实现。

---

### DRIFT-USERSCOMMON-004 🔴 JWT `alg` 在密钥 ≥48 字节时**静默分叉**（证伪了既有台账的说法） —— ✅ **已修（首轮）**

> 修法**必须连 key 一起钉**：显式 `new SecretKeySpec(padToHmacKeySize(secretKey), "HmacSHA256")`
> + `signWith(key, SignatureAlgorithm.HS256)` —— 只加后者不够，
> `Keys.hmacShaKeyFor` 返回的 `SecretKeySpec` 的 JCA 算法名才是 jjwt 真正采信的来源。
> 守门网：同上 `JwtUtilParityTest`（22 条，含 11 档密钥长度）。

**性质：SEMANTIC MISMATCH（条件性 · 触发条件已量化）**

**Go 出处**：`service/user/common/jwt/jwt.go:12` `token := jwt.New(jwt.SigningMethodHS256)`
—— **显式写死**，任何密钥长度下都是 HS256。

**RAG 出处**：`JwtUtil.java:21` `.signWith(key)` **不传 alg** ⇒ jjwt 按 `Keys.hmacShaKeyFor` 返回的
`SecretKeySpec.getAlgorithm()` 自动选算法，阈值即三个 HMAC 算法的最小密钥长度。

**双侧活体，逐档实测**（`secret_len` = 密钥字节数）：

| 密钥长度 | Go `alg` | RAG `alg` | RAG `sig_len` | Go `sig_len` |
|---|---|---|---|---|
| 15 | HS256 | HS256 | 43 | 43 |
| 16 / 31 / 32 / 36 / 47 | HS256 | HS256 | 43 | 43 |
| **48 / 63** | HS256 | **HS384** | 64 | 43 |
| **64 / 65 / 128** | HS256 | **HS512** | 86 | 43 |

**🔴 本条同时**证伪**了既有台账 `users/user/api.md` §9 `UNRESOLVED-002` 的断言**：

> 原断言：「一旦触发 ⇒ user-api 签的 token 在 Go 侧（及任何按 HS256 验签的组件）**全部验不过**。」

**实测交叉验签（12 次，Go↔RAG 双向）**：

```
RAG 验 Go 签的： admin_15B/s32B/s47B/s48B/s64B/user_36B  → 全部 VERIFIED
Go 验 RAG 签的： 同上                                     → 全部 VERIFIED
```

**为什么证伪**：两侧库**都按 token header 的 `alg` 选 MAC，且都不做算法白名单**
（golang-jwt v4 `Parse` 直接 `jwt.GetSigningMethod(alg)`；jjwt 0.12 同样取 header 的 alg）。
⇒ 各自签、各自验，**互验反而通过**。

**但风险是真的，只是触发面窄得多** —— 实测「按 RFC 8725 §3.2 硬校验 alg 的消费者」验 RAG 签的 token：

| 密钥长度 | alg 固定消费者的判定 |
|---|---|
| 15 / 32 / 36 / 47 | ✅ `ACCEPTED` |
| **48** | 🔴 `REJECTED: unexpected signing method HS384` |
| **64** | 🔴 `REJECTED: unexpected signing method HS512` |

⇒ **准确表述**：分叉**不会**打断 Go↔RAG 互验；它打断的是
**任何固定 `alg` 白名单的第三方消费者**（而这恰是 JWT 安全最佳实践的推荐姿态）。

**当前是否已触发**：**未触发**。实测两侧配置的密钥：
- user：`application.yml:91` / Go `usercenter.yaml:11` ⇒ `ae0536f9-6450-4606-8e13-5a19ed505da0` = **36 字节**
- admin：`application.yml:82` / Go `admincenter.yaml:11` ⇒ `yU-hCm)2*?NQmWG` = **15 字节**

⇒ **两侧密钥均 < 48 字节，当前 `alg` 一致**。这是一个**潜伏分叉**，触发条件是「密钥加长到 ≥48 字节」。

**处置**：✅ **本轮已修**（显式钉 HS256），详见 §5。修法必须保证 ≥48 字节时不再漂移 ⇒ 守门测试覆盖到 128 字节档。

---

### DRIFT-USERSCOMMON-005 ⚠️ bcrypt 接受 `$1$` / `$2x$` 前缀，RAG 拒收 —— ✅ **已签字（2026-10-24）⇒ `SD-USERSCOMMON-001`**

> 决定：**保留 RAG 的 fail-closed**（拒收 `$1$` / `$2x$`），**不对齐 Go 的 fail-open**。
> 签字档 ⇒ [`docs/Intentional_Deviations/users/common.md`](../../Intentional_Deviations/users/common.md) 的 `SD-USERSCOMMON-001`。
> ⚠️ **签字的是「保留严格校验」这个决定，不是「登不进去这件事没问题」** ——
> 真出现这类账号，正确处置是**重置密码 / 数据迁移**，不是回头放宽正则。

**性质：OBSERVABLE DIFFERENCE（数据依赖 · fail-closed 方向）**

**Go 出处（`golang.org/x/crypto@v0.53.0/bcrypt/bcrypt.go`）**：

```go
// :265-279
func (p *hashed) decodeVersion(sbytes []byte) (int, error) {
	if sbytes[0] != '$' { return -1, InvalidHashPrefixError(sbytes[0]) }
	if sbytes[1] > majorVersion { return -1, HashVersionTooNewError(sbytes[1]) }  // :269 只在「严格大于」时拒
	p.major = sbytes[1]
	n := 3
	if sbytes[2] != '$' { p.minor = sbytes[2]; n++ }                          // :274-276 minor 存了但**从不校验**
	return n, nil
}
```

两条事实：
1. `:269` 是 `>` 而非 `>=` ⇒ **major 小于 `'2'` 的前缀（`$0$` / `$1$`）一律放行**；
2. `:274-276` **minor 版本位存下来却没有任何地方读它做校验** ⇒ `$2x$` / `$2z$` 一律放行。

**双侧活体**（以同一个 cost=10 的合法 hash 为基底，只换前缀）：

| 用例 | Go `CheckPassword` | RAG `PasswordUtil.check` |
|---|---|---|
| `$2a$10$`（原生） | `true` | `true` |
| `$2b$10$` | `true` | `true` |
| `$2y$10$` | `true` | `true` |
| **`$2x$10$`** | **`true`** | **`false`** |
| **`$1$10$`** | **`true`** | **`false`** |

**RAG 出处**：`BCryptPasswordEncoder` 的 `BCRYPT_PATTERN` = `\A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}`
⇒ 前缀只认 `2a` / `2y` / `2b`（或无 minor 的 `2`）。

**影响面（诚实边界）**：
- 两侧自己的 `PasswordEncrypt` / `encrypt` **都只产出 `$2a$`**（实测 `encrypt_shape` 两侧一致：`$2a$10$` / 60 字节），
  ⇒ **由本系统写入的 hash 不会触发**。
- 只在「**库里有外部导入/历史遗留的非 `$2a$` hash**」时触发，且方向是 **fail-closed**
  （Go 判「密码正确」放行 ⇒ RAG 判「密码错」拒绝）。
- 🔴 **注意方向性**：这是**认证能力收窄**，不是安全放宽。但若存在这样的历史账号，
  用户在 Go 上能登录、在 RAG 上登录不了 —— 跨栈迁移时会表现为「随机一批用户无法登录」。

**处置**：⏸️ **本轮不改**。修法需要绕过 `BCryptPasswordEncoder` 的正则（自建一个接受
`$1$` / `$2x$` 的前缀归一化，或改用 `BCrypt` 的底层 `checkpw`），
**这属于改安全原语**，必须单独一轮 + 专门回归网，不能夹带在本轮里。

---

### PAD-01 ✅✅ `padToHmacKeySize` 的 zero-pad **可证明无害**（本轮最重要的一个「证伪」）

**这是本轮最反直觉、也最重要的一条结论**：`JwtUtil.java:44-53` 把短密钥补零到 32 字节，
按密码学常识应当推出「**两侧 token 互相验不过**」。**实测结论相反。**

**机理（RFC 2104）**：HMAC 对**短于分组尺寸（64 字节）的密钥先做零填充**再走 `K' = K ⊕ ipad`。
⇒ 15 字节原密钥与「15 字节 + 17 个 0」拼成的 32 字节密钥，
补到 64 字节分组后**得到完全相同的 `K'`**。

**直接实证**（`SigDebug.java`，同一 signing input）：

```
raw  len=15 hex=79552d68436d29322a3f4e516d5747
pad  len=32 hex=79552d68436d29322a3f4e516d57470000000000000000000000000000000000
identical=false
sig(rawKey)    = 19a0e1d0ee5bbc1cf0043765f462f5d9f51a19502a4fae899f06c6792f94a2a6
sig(paddedKey) = 19a0e1d0ee5bbc1cf0043765f462f5d9f51a19502a4fae899f06c6792f94a2a6   ← 逐字节相同
```

**交叉验签（6 档 × 2 向 = 12 次，全通）**：
`admin_15B`(15) / `s32B`(32) / `s47B`(47) / `s48B`(48) / `s64B`(64) / `user_36B`(36) ⇒ **12/12 VERIFIED**。

⇒ `JwtUtil.java:37-40` 那句 javadoc 断言（「token 本身的 payload、claim、过期行为与 Go 完全一致」）
**成立**，但它给的理由（「jjwt 强制要求的纯运行环境约束」）**不完整** ——
真正让它安全的是 **HMAC 自身的零填充不变量**，而这条不变量此前**没有写在任何地方**。

⚠️ **不变量边界（必须写进代码注释，否则下一个人会「优化」掉它）**：
1. 补零**只对 ≤64 字节（HMAC 分组尺寸）以内的密钥与原始密钥等价**；
2. 一旦密钥 **>64 字节**，HMAC 不再零填充、改用密钥本身 ⇒ 该不变量失效
   （此时 `Keys.hmacShaKeyFor` 也会切到 HS512，见 `DRIFT-USERSCOMMON-004`）；
3. 若有人把 `padToHmacKeySize` 改成「填充非零字节」或「截断到 16 字节」，
   **admin（15 字节密钥）的 token 会立刻与 Go 互验失败**，且**没有任何现有测试会红**。

**处置**：✅ 已把该不变量写进 `JwtUtil.padToHmacKeySize` 的 javadoc，并配守门测试（§5）。

---

### ENC-01 ✅ `PasswordUtil.encrypt` >72 字节的失败机制不同，但**可观察行为一致**

primitive 层差异（双侧活体）：

| 用例 | Go | RAG |
|---|---|---|
| 明文 72 字节 | ✅ 成功 | ✅ 成功 |
| 明文 73 字节 | 返回 `error("bcrypt: password length exceeds 72 bytes")`（`bcrypt.go:87` `ErrPasswordTooLong`） | **抛** `IllegalArgumentException("password cannot be more than 72 bytes")` |

**但 primitive 的差异在调用点被完全吸收**（逐行核对）：

- Go `service/user/user/rpc/internal/logic/registerlogic.go:56-62`：
  `metrics.UserRegisterCount.WithLabelValues("internal_error").Inc()` →
  `logger.LogBusinessErr(spanCtx, errmsg.ErrorServerCommon, err)` → `span.RecordError(err)` →
  `status.Error(codes.Internal, "密码生成失败")`
- RAG `UserDomainService.java:281-291`：结构**逐条同构**，终态同为
  `Status.INTERNAL.withDescription("密码生成失败")`（metrics / span / 日志三处副作用同型）

⇒ **判定为「表达层差异（框架限制）」，不是行为漂移**。记录在此以免下一轮把它当成新 bug 重查一遍。

⚠️ **残留契约差异（对新调用方有效）**：`PasswordUtil.encrypt` **没有**像 Go 那样返回 error，
而是抛异常。若将来有调用方忘记 `try/catch`，一个 73 字节的口令会变成未捕获异常而不是受控的
`INTERNAL`。现有 4 个调用点（`admin-rpc` 3 处 + `user-rpc` 2 处）**均已包 try/catch**，本轮已核对。

---

## 4. 已确认**一致**的部分（同样是证据，不是「没查」）

| 组 | 条数 | 结论 | 取证方式 |
|---|---|---|---|
| `errmsg` 其余 37 个码（含 200/500/1015/5001-5010/1001-1012/1016/3001-3003 与 7 个未定义码的回落） | 37 | ✅ **逐条逐字节相同**，含未知码回落 `"FAIL"` | 双侧活体 |
| `BearerTokenExtractor` 两套取法 | 22 | ✅ **11 个输入 × 2 方法全一致**。含易错的 4 个边界：`XXBearer ` **不剥离**（验签路径）、`Bearer `(7 字符) **剥成空串**、`Bearer`(6 字符) **不剥**、`bearer ` 在黑名单路径**不剥**（大小写不对称被完整保留） | 双侧活体（Go 侧用 `golang-jwt` 真 `AuthorizationHeaderExtractor` + `strings.TrimPrefix`） |
| `GoStyleStringInt64` | 17 | ✅ **17/17 一致**。含 `1.0` 与 `1e2` 被拒（Go `strconv.ParseInt` 对字面量 `"1.0"`/`"1e2"` 失败）、`+123` 被接受、`±2^63` 边界（`Long.MIN_VALUE` 合法 / `2^63` 越界）判定一致 | 双侧活体（Go 侧用真 `core/mapping.UnmarshalJsonMap` + `json:",string"` struct tag） |
| `GoStyleRequired` 两条文案 | 4 | ✅ 一致。`field(x, null)` → `is not set`；空串**不报错**；`explicitNull(x, present=true, null)` → `mustn't be nil`；`present=false` **不报错** | 双侧活体（Go 侧用真 `core/mapping` 的缺席 / 显式 null / 空串三态） |
| `cryptx` 判定谱其余 15 项 | 15 | ✅ 一致：匹配/不匹配/双空串/非 bcrypt/截断 hash/`$2b$`/`$2y$`/cost 越界/cost=0/null 字节/72 与 73 字节明文/unicode 明文 | 双侧活体 |
| `cryptx` 产出形状 | 1 | ✅ 两侧 `$2a$10$` + 60 字节（cost 10 一致） | 双侧活体 |
| 交叉验签（≤64 字节全档） | 12 | ✅ 全通 | 双侧活体 |

---

## 5. 本轮已实施的修改

> 用户口径：**DDL 三项（V-002 / V-003 / V-011）一条不动，只取证**；
> common 自身的漂移按「确认即修」处理，修法均为**行为对齐 Go** 且**带真对照守门测试**。

### 5.1 `DRIFT-USERSCOMMON-004` —— 显式钉死 HS256 ✅

**Go 出处**：`service/user/common/jwt/jwt.go:12` `jwt.New(jwt.SigningMethodHS256)`。

**改法**：`JwtUtil.getToken` 显式传 `SignatureAlgorithm.HS256`，
并且**不再**让 `Keys.hmacShaKeyFor` 决定签名算法 ——
改为自己构造 `new SecretKeySpec(padToHmacKeySize(secretKey), "HmacSHA256")`。

⚠️ **关键点**：只加 `signWith(key, HS256)` 而**保留** `Keys.hmacShaKeyFor` 是**不够的** ——
该工厂在密钥 ≥48 字节时返回 JCA 算法名为 `HmacSHA384` 的 `SecretKeySpec`，
必须连 key 的算法名一起钉死，否则 alg 仍会被拖走。

**修后实测**：jwt 组 **11 个密钥档（15/16/31/32/36/47/48/63/64/65/128）全部与 Go 一致**，
`alg=HS256`、`typ=JWT`、`sig_len=43`、`claim_keys=[exp,iat,userId]` 逐项相同。

### 5.2 `DRIFT-USERSCOMMON-002` —— 补 `"typ":"JWT"` ✅

**Go 出处**：`golang-jwt/jwt/v4@v4.5.2/token.go` 的 `SigningMethodHS256.Sign()` 无条件写 `typ`。

**改法**：builder 上 `.header().add("typ", "JWT").and()`。

⚠️ **残留的字节级差异（已知、不修、已登记）**：
header 键序 Go 是 `{"alg","typ"}`、RAG 是 `{"typ","alg"}`（jjwt 按插入顺序输出，且未暴露排序 API）。
与 §3 的 `DRIFT-USERSCOMMON-003`（claim 键序）**同型**：JSON 对象键序无语义，
对齐它需要手工拼 base64、绕过 jjwt 的签名实现，**收益为负**。

### 5.3 `PAD-01` —— 把 HMAC 零填充不变量写进 javadoc（§3.2 要求的 idiomatization 留痕）✅

原 javadoc 给的理由（jjwt 强制 key ≥256 bit）**不完整**，且**掩盖了一个真实的脆弱点**：
它没有说明「补零为什么不会破坏与 Go 的互通」。本轮补上 RFC 2104 机理 + 三条边界条件。

### 5.4 守门网 `JwtUtilParityTest`（22 条，**skipped = 0**）

| # | 用例 | 锁住什么 |
|---|---|---|
| 1-11 | `algIsAlwaysHs256RegardlessOfKeyLength`（11 个密钥档参数化） | `DRIFT-USERSCOMMON-004` |
| 12-16 | `signatureLengthStaysHs256`（5 档） | alg 分叉的**第二个独立观测量**（sig_len 43 vs 64/86） |
| 17 | `headerCarriesTypJwt` | `DRIFT-USERSCOMMON-002` |
| 18 | `headerKeySetIsExactlyAlgAndTyp` | header 不得多出 Go 没有的 `kid` / `cty` |
| 19 | `zeroPaddingPreservesHmacForShortKeys` | `PAD-01` 的 RFC 2104 不变量（含「补的必须是 0」逐字节断言） |
| 20 | `paddingIsIdentityAbove32Bytes`（5 档） | 不得截断（截断会让验签直接失败） |
| 21 | `tokenRoundTrips` | 修 token 仍可验签，`userId`/`exp`/`iat` 正确 |
| 22 | `claimSetMatchesGo` | claim 集合恰为 `{exp, iat, userId}`，与 `jwt.go:9-11` 一致 |

### 5.5 ✅ 变异验证（§3.3「绿测试必须是错误答案的守卫」）

把 `getToken` 改回修复前的写法（`Keys.hmacShaKeyFor` + 单参 `signWith(key)` + 无 `typ`）后复跑：

```
Tests run: 22, Failures: 9, Errors: 0, Skipped: 0
  algIsAlwaysHs256RegardlessOfKeyLength: 密钥 48/63 字节时 alg 不是 HS256，实际 header={"alg":"HS384"}
  algIsAlwaysHs256RegardlessOfKeyLength: 密钥 64/65/128 字节时 alg 不是 HS256，实际 header={"alg":"HS512"}
  headerCarriesTypJwt: header 缺 typ:JWT：{"alg":"HS256"}
  signatureLengthStaysHs256: 密钥 48 字节时签名长度不是 43（实际 64）／64/128 字节时实际 86
```

⇒ 守门网**实证能变红**，且报错精确指出分叉点；恢复修复后 33/33 全绿。
⚠️ **第一次变异注入因语法错误（`Keys.Key` 不存在）导致编译失败、未真正执行测试**，
已修正后重跑 —— 变异本身也必须能编译，否则「变红」证明不了任何事（§3.3 同类）。

### 5.6 下游未受影响（`JwtUtil` 是**四方共用**，改它必须验下游）

| 模块 | 结果 |
|---|---|
| `users-common` | **33** tests / 0 failures / 0 errors / **skipped = 0** |
| `user-api` | **76** tests / 0 failures / 0 errors（与 `user/api.md` 第三轮记录的 76 一致） |
| `admin-api` | BUILD SUCCESS |

---

## 6. Unresolved（本组件，**此节不得为空**）

| # | Question | Why Unknown | Required Confirmation |
|---|---|---|---|
| **UNRESOLVED-USERSCOMMON-001** | `DRIFT-USERSCOMMON-001`（1017/1018 文案）要不要改成 `"FAIL"` | 改法会**同时改变 admin-api 的 wire 行为**，属跨域裁决 | 与 `align/users-admin` 会话合并裁决；本轮已取证，未动手 |
| **UNRESOLVED-USERSCOMMON-002** | `DRIFT-USERSCOMMON-005`（`$1$`/`$2x$`）的实际数据面 | 需查生产库是否存在非 `$2a$` 的 hash（两侧自产的都是 `$2a$`） | 真 PG 上跑 `SELECT DISTINCT left(password,4) FROM users_user WHERE password LIKE '$%'` |
| **UNRESOLVED-USERSCOMMON-003** | `http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域 | 它通过 `JsonMapperBuilderCustomizer` 全局施加；注释自陈「规则是全局的，误伤了 `GetUserListReq.keyword`」 | 需在 message/like/article 等域各打一次带标量的请求矩阵；本轮只审了 users 域 |
| **UNRESOLVED-USERSCOMMON-004** | `ddl/SchemaInitializer` 的四份脚本与 Go `AutoMigrate` 的**逐列等价性** | 本轮按用户口径**不动 DDL**，只审了 Java 侧脚本内容；`ADVISORY_LOCK_KEY` 的并发正确性未做双侧对拍 | 真 PG 下同时起 `user-rpc` + `admin-rpc` 打同一空库（`UNRESOLVED-USERRPC-005` 仍是同一个未闭环项） |
| **UNRESOLVED-USERSCOMMON-005** | `jwt.GetToken` 的 **claim 数值类型**在跨语言消费时是否一致 | 两侧 wire 上都是 JSON 数字（实测 `userId:1234567890`），但 Go 解析后是 `float64`、jjwt 是 `long` | 对「userId 超过 2^53」的值做一次双侧解析比对（本轮探针只用了 `1234567890`） |

---

## 7. 最终判定（§39 三值）

```text
SEMANTICALLY NOT EQUIVALENT
```

- **SEMANTIC MISMATCH：1 条** —— `DRIFT-USERSCOMMON-004`（`alg` 条件性分叉）→ ✅ **本轮已修 + 22 条守门 + 9 条变异验证**
- **OBSERVABLE DIFFERENCE：3 条**
  - `001`（1017/1018 文案）—— **潜伏**：`users/user` 内三个调用点全是日志码，wire 观察不到；⏸️ 待与 admin 域合并裁决
  - `003`（claim 键序）—— 字节级、语义等价；**判为不修**（对齐需绕过 jjwt 签名实现）
  - `005`（bcrypt `$1$`/`$2x$`）—— 数据依赖、fail-closed；⏸️ 待单独一轮（需改安全原语）
- **已修且残留字节差（不计为差异）**：`002` 的 header 键序（`typ`/`alg` 顺序），与 `003` 同型
- **未决：5 条**（§6）
- **对照覆盖**：176 条，**全部双侧活体实测**；证据强度为本仓 users 域组件最高
  （对比：`user-api` 上限为静态源码、`user-rpc` 为真 PG + 真启动）

> ⚠️ **本判定的效力边界**：本轮只对照了 **common 自身**。
> 「common 一致」**推不出**「调用方一致」——
> §3.5 的跨组件不变量（`users_user` 建表责任交叉、api↔rpc 的 client 契约）**不在本组件判定权内**，
> 仍须与 `user/rpc.md`、`user/api.md` 及 admin 域合并判读。

---

## 8. 本轮的复现方式

```bash
# Go 侧（只读引用冻结仓，不写入一个字节）
cd /tmp/commonprobe/go   && GOFLAGS=-mod=mod go run . > go_out.jsonl
cd /tmp/commonprobe/go3  && GOFLAGS=-mod=mod go run .

# RAG 侧（调用 users-common 真实类）
cd /tmp/commonprobe && java -cp "classes:$RAG_CP" CommonProbe > rag_out.jsonl
python3 diff.py go_out.jsonl rag_out.jsonl
```

> 探针目录在 `/tmp`，**不入仓**。若需长期回归，守门测试已在仓内落地（见 §5）。
---

<!-- @fix 2026-10-23 第五轮：UNRESOLVED-USERSCOMMON-005 关闭（双侧真实路径实测） -->

## 2026-10-23 第五轮：`UNRESOLVED-USERSCOMMON-005` 关闭（`userId` 超 2^53 的精度）

### 结论：**两侧都无精度损失，本条不是漂移**

**Go 侧的真实路径（三段，缺一不可）**：

1. `go-zero@v1.10.2/rest/handler/authhandler.go:51` `parser := token.NewTokenParser()`（无 opts）
2. `go-zero@v1.10.2/rest/token/tokenparser.go:81-86` `doParseToken` → `request.WithParser(newParser())`
3. 🔴 **`go-zero@v1.10.2/rest/token/tokenparser.go:122-123`**：
   ```go
   func newParser() *jwt.Parser {
       return jwt.NewParser(jwt.WithJSONNumber())     // ← 关键：开了 UseJSONNumber
   }
   ```

⇒ `claims["userId"]` 的动态类型是 **`json.Number`**（**不是** `float64`）
⇒ `service/user/user/identity/resolve.go:70` 的 `claim, ok := ctx.Value("userId").(json.Number)` 断言成立
⇒ `:73` `claim.Int64()` **精确**，无 float64 舍入。

⚠️ **本条差点被写成假发现，过程记录在案**：
第一版探针用**裸 `json.Unmarshal`** 解析 payload，得到 `float64`，
实测 `9007199254740993` 变成 `9007199254740992`（**差 1**），
若采信就会写出一条「Go 在 2^53 以上丢精度」的**假漂移**。
⇒ 是 `tokenparser.go:123` 那行 `jwt.WithJSONNumber()` 推翻了它。
**教训：探针必须复现真实调用路径，不能用「看起来等价」的库调用替代。**

**RAG 侧的真实路径**：`JwtAuthInterceptor` `claim instanceof Number → ((Number) claim).longValue()`
⇒ 实测 jjwt 给出的动态类型：

| 签名时 `userId` | jjwt 给出的类型 | `longValue()` 结果 | 精确 |
|---|---|---|---|
| `1234567890` | `Integer` | 1234567890 | ✅ |
| `9007199254740993`（2^53+1） | **`Long`** | 9007199254740993 | ✅ |
| `-9007199254740993` | `Long` | -9007199254740993 | ✅ |
| `Long.MAX_VALUE` / `MIN_VALUE`（±2^63） | `Long` | 原值 | ✅ |

⇒ **jjwt 对 int64 全域精确**，`longValue()` 无截断。

### 唯一的理论分歧点：**超出 int64 的值**

| 输入 | Go | RAG |
|---|---|---|
| `2^64 + 100`（18446744073709551716） | `claim.Int64()` **报错** ⇒ `ErrInvalidClaim`（1006） | Jackson 给 `BigInteger`，`.longValue()` **静默回绕** ⇒ 可能得到一个正数小值 |

⚠️ **但不可达**：`uid` 来自 Snowflake（量级 ~1e18 < 2^63），
**任何一侧都不可能签出 ≥2^64 的 userId** ⇒ 该分歧点**当前不可观察**，不构成漂移。

⇒ **本条关闭。** 若将来引入新的 uid 生成器（超过 2^63），**必须重开本条**。
