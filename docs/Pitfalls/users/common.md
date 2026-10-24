# Pitfalls · `service/users/users-common`

> 记录「为什么会踩」，每条回答：**症状像什么 · 真实原因 · 怎么证伪 / 怎么确认 · 留下的守门网**。
> 语义差异本身在 [`../../DRIFT/users/common.md`](../../DRIFT/users/common.md)。

---

## PIT-USERSCOMMON-001 🔴 过期时间戳会在验签**之前**短路掉断言，用例照样「绿」

**症状**：要验证「两侧 token 能否互相验签」，写完用例跑出来是绿的，
但断言**根本没执行到** —— `parseSignedClaims` 在读 payload 之前就先抛了 `ExpiredJwtException`，
而用例只断言了「没抛异常之外的东西」，于是**什么都没测到却是绿的**。

**真实原因**：JWT 的 `exp` 校验发生在 jjwt 的解析流程**早期**，
一旦过期就直接短路；用固定字面量（`iat=1700000000` = 2023-11-14）写测试，
在 2026 年跑就是必然过期。

**本轮踩了两次**（同一天、同一件事）：
1. `/tmp/commonprobe/XVerify.java` 交叉验签 —— 两侧都返回「已过期」，
   看输出像是「互验失败」，实际是**签名压根没被比对**；
   若当时直接采信，会把结论写成「两侧 token 互验失败」——
   而**真实结论是互验全通**（`PAD-01`）。
2. `JwtUtilParityTest.tokenRoundTrips` / `claimSetMatchesGo` —— 首跑 2 个 ERROR，
   才暴露出来。

**怎么确认**：
- 断言必须**区分**「过期」与「验签失败」两种异常，而不是笼统的「能解析」。
- 或让 `iat` 取 `Instant.now()`，让签名校验真正跑到。

**留下的守门网**：`JwtUtilParityTest` 里 `nowEpochSecond()` + 注释说明了为什么；
`header`/`alg` 这类**不需要验签**的断言仍用固定 `IAT`（它们不经过 `exp` 校验路径）。

> 与 [`../../DRIFT/users/user/api.md`](../../DRIFT/users/user/api.md) §4 记录的
> 「库里残留上一轮的 `dt_user1` ⇒ 3 条 unexpected」同型：**探针/用例自己也会假红，
> 而假红的成因往往和被测对象无关**。

---

## PIT-USERSCOMMON-002 ⚠️ 双侧探针的输出格式不同构，比对结果全是「假差异」

**症状**：Go 侧与 Java 侧探针都跑通了，逐行 diff 却报 11 条 jwt 差异。
逐条看下来 `alg` / `typ` / `sig_len` **值全对**。

**真实原因**：两侧的输出形态不一致：
- Go 侧一开始用 `json.Unmarshal(raw, &v)` 解析 header/payload ⇒ 得到的是 **Go 的内存类型**（`float64`），
  而**内存类型不是 wire 事实**；
- Java 侧从原始 JSON 文本提取并标了 `INT:` / `STRING:` 前缀。
⇒ 同一个 wire 值，两侧打印成不同形态。

**另一层**：Java 侧的 `jsonKeys()` 键扫描器**把字符串值也收成了键**
（`["HS256","JWT","alg","typ"]` 而非 `["alg","typ"]`），
判据缺了「闭合引号后紧跟冒号」这一条。

**怎么证伪**：比对之前先确认两侧探针**输出同构**——
同一份判据写两遍，或者更省事：**两侧都从原始字节出发**，不要一侧解析、一侧读文本。

**留下的守门网**：`/tmp/commonprobe/go/main.go` 的 `jsonField` / `jsonKeys` 与
`CommonProbe.java` 的同名方法**判据逐条对齐**（含「键必须后跟冒号」这条）。

---

## PIT-USERSCOMMON-003 ⚠️ 变异验证本身也可能编译失败 —— 「变红」不等于「证明了什么」

**症状**：注入变异后 Maven 报的是 `COMPILATION ERROR: 找不到符号`，不是测试失败。

**真实原因**：变异写法本身有语法错误（`io.jsonwebtoken.security.Keys.Key` 不存在，
`Keys` 的返回类型是 `java.security.Key`）。

**怎么确认**：变异验证的判据是「**测试用例**变红」，不是「构建变红」。
构建失败与断言失败必须分开看。

**留下的守门网**：`docs/DRIFT/users/common.md` §5.5 同时记了两次尝试（编译失败 / 9 条断言失败）。

---

## PIT-USERSCOMMON-004 ⚠️ `Keys.hmacShaKeyFor` 会**顺带决定签名算法**，显式传 alg 还不够

**症状**：按 §3.2 补上 `signWith(key, SignatureAlgorithm.HS256)` 后，
密钥 ≥48 字节时 `alg` **仍然是 HS384**。

**真实原因**：jjwt 的 `signWith(Key)` 单参重载会**照 `SecretKeySpec.getAlgorithm()` 的 JCA 名选算法**；
而 `Keys.hmacShaKeyFor()` 在密钥 ≥48 字节时返回的就是 `HmacSHA384` 的 `SecretKeySpec`。
⇒ 只钉 builder 的 alg 参数、却复用该工厂的 key，**等于没钉**。

**怎么确认**：不要只看 builder 调用，要看**最终 wire 上的 header**。
本轮是对 15/16/31/32/36/47/48/63/64/65/128 共 11 个密钥档逐一解 header 实测才发现。

**留下的守门网**：`JwtUtilParityTest` 用「alg」+「sig_len」**两个独立观测量**覆盖 11 档；
变异验证确认退回旧写法后 **9 条变红**。

> 与 AGENTS.md §3.3「注解存在 ≠ 机制生效」同型：
> **参数传对了不等于它真的在起作用** —— 这里生效的是 key 的算法名，不是你传的参数。