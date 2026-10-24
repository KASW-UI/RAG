# 有意偏离 · `service/users/users-common`

> **这里是「决策」，不是缺陷。** 与 [`../../DRIFT/users/common.md`](../../DRIFT/users/common.md) 的分工：
> DRIFT 记「需要对齐的差异」，本文件记「**决定不对齐**的差异」。
>
> ⚠️ **读之前先记住两条**：
> 1. 本文件里的条目**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
> 2. 审查时**先来这里确认**，再决定某条红色差异是不是回归。

---

## SD-USERSCOMMON-001（2026-10-24 用户签字）—— bcrypt 只认 `$2a$` / `$2b$` / `$2y$`，**不放宽**到 Go 放行的 `$1$` / `$2x$`

| 项 | 值 |
|---|---|
| 对应 DRIFT | `DRIFT-USERSCOMMON-005` |
| 签字日期 | **2026-10-24** |
| 决定 | **保留 RAG 现状**（拒收 `$1$` / `$2x$` 等非 `$2` 前缀），**不对齐 Go 的 fail-open** |
| 性质 | ① 有意偏离 —— RAG 侧做了**更严**的一侧，方向是**更安全**，不是「忘了做」 |
| 影响面 | **仅「库里存着外部导入/历史遗留的非 `$2a$` hash」这一种数据**；系统自己写入的 hash **两侧完全一致** |

### Go 侧事实（消费点，非声明点）

`golang.org/x/crypto@v0.53.0/bcrypt/bcrypt.go:265-279`：

```go
func (p *hashed) decodeVersion(sbytes []byte) (int, error) {
	if sbytes[0] != '$' { return -1, InvalidHashPrefixError(sbytes[0]) }
	if sbytes[1] > majorVersion { return -1, HashVersionTooNewError(sbytes[1]) }  // :269 只在「严格大于」时拒
	p.major = sbytes[1]
	n := 3
	if sbytes[2] != '$' { p.minor = sbytes[2]; n++ }                             // :274-276 minor 存了但**从不校验**
	return n, nil
}
```

两条事实：
1. `:269` 是 `>` 而非 `>=` ⇒ **major 小于 `'2'` 的前缀（`$0$` / `$1$`）一律放行**；
2. `:274-276` **minor 版本位存下来却没有任何地方读它做校验** ⇒ `$2x$` / `$2z$` 一律放行。

### RAG 侧事实

Spring Security 的 `BCryptPasswordEncoder.BCRYPT_PATTERN`
= `\A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}`
⇒ 前缀只认 `2a` / `2y` / `2b`（或无 minor 的 `2`）。

### 双侧活体对照（同一 cost=10 合法 hash 为基底，只换前缀）

| 用例 | Go `CheckPassword` | RAG `PasswordUtil.check` |
|---|---|---|
| `$2a$10$` | `true` | `true` |
| `$2b$10$` | `true` | `true` |
| `$2y$10$` | `true` | `true` |
| **`$2x$10$`** | **`true`** | **`false`** |
| **`$1$10$`** | **`true`** | **`false`** |

### 为什么签字保留 RAG 的 fail-closed（不放宽正则）

1. **方向是「更严」，不是「更松」。** 差异的实际表现是
   **Go 判「密码正确」放行 ⇒ RAG 判「密码错」拒绝** ——
   RAG 只会**少放行一个用户**，不会**多放行一个用户**。
   ⇒ 这不是安全漏洞方向。
2. **`$1$` 是 MD5-crypt（crypt(3)）**，是**已被淘汰的哈希原语**，
   且**没有 bcrypt 的盐长度/代价参数**。
   为了「与 Go 一致」而放宽它，等于**主动让 RAG 接受比 Go 更弱的密码存储**，
   代价是**扩大**弱哈希的使用面 —— 与本项目「RAG 侧做更严/更安全那一侧」的既有取向相反。
3. **触发面极窄，且是数据问题而非代码问题。**
   两侧自己的 `PasswordEncrypt` / `encrypt` **都只产出 `$2a$`**
   （实测 `encrypt_shape` 两侧一致：`$2a$10$` / 60 字节）
   ⇒ **由本系统写入的 hash 永远不会触发这条差异**。
   只在「库里有外部导入或历史遗留的非 `$2a$` hash」时才触发。
4. **放宽正则的代价被低估。** `BCRYPT_PATTERN` 是 Spring Security 的**全局常量**，
   改它要么改 Spring 的行为（不可控），要么在 RAG 侧自己实现一份 bcrypt 校验
   （引入密码学实现风险）⇒ **为一个「遗留数据迁移问题」付这个代价不划算**。
   正确做法是**在数据层解决**（把遗留 hash 重置或迁移），不是放宽运行时校验。

### ⚠️ 效力边界（本条**不覆盖**什么）

1. **不覆盖「Go 侧能登录、RAG 侧登不进去」这个用户可见后果本身** ——
   本条签的是「**保留 RAG 的严格校验**」这个决定，
   **不是**「登不进去这件事没问题」。
   ⇒ 若线上真出现这类账号，正确处置是**重置密码 / 数据迁移**，
   **不是**回头改这条正则。
2. **不覆盖 `DRIFT-USERSCOMMON-001`**（`ErrorCode.getMsg(1017/1018)` 的中文文案）——
   那条是**潜伏差异**（`users/user` 内三个调用点全是日志码、wire 观察不到），
   需**跨域裁决**（admin 域签字），与本条无关，不要混谈。
3. **不覆盖 `DRIFT-USERSCOMMON-003`**（JWT claim 顺序）——
   那条已判定为**字节级语义等价**，属「表述差异」而非偏离。

### 守门网

`service/users/users-common` 的密码相关用例覆盖 `$2a$` / `$2b$` / `$2y$` 三个前缀的**正常路径**。
本条的「拒收 `$1$` / `$2x$`」方向由 `PasswordUtil` 自身的形状校验保证
（畸形 hash 不抛异常、返回 `MISMATCHED`），
其用例在 `service/users/user/user-rpc/.../LoginMalformedHashDrift004Test`（4 条）。

⚠️ **守门网的作用是「守住 RAG 现状」，不是「守住 Go 一致」** ——
若有人为了「与 Go 对齐」去放宽 `BCRYPT_PATTERN` 或改 `PasswordUtil`，
`LoginMalformedHashDrift004Test` 应当变红。

### 关闭条件

本条**不设自动关闭条件** —— 它是长期决策。
若将来要撤销（即改为接受 Go 的 `$1$` / `$2x$`），必须同时：
1. 改 `PasswordUtil` 的校验路径（或引入自实现的 bcrypt 校验），
   **并明确评估引入密码学实现风险**；
2. 在本文件追加一条「撤销记录」，保留历史；
3. ⚠️ 更优先的替代路径：**先解决遗留数据**（重置/迁移那批 hash），
   那样本条就**不再需要被撤销**。
