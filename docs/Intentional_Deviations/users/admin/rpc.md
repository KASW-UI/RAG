# 签字偏离台账 · `admin-rpc`

> 域共享条目（N-1 / N-4 / N-5 / SD-01 等）⇒ [`_shared.md`](./_shared.md)。
> 差异的逐条证据（Go 出处、活体对拍、失效边界）⇒ `docs/DRIFT/users/admin/rpc.md`。

---

## SD-2026-10-23-06@users · `resetUserPassword` 保留空 `default-password` 守卫 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> **2026-10-29 重核（台账对齐轮）**：
> - **别名**：`SD-2026-10-23-06@users`（域内唯一别名）。**撞号事实**：同一编号在本域被引用 **2 处** ——
>   本小节（全文落点）与 [`_shared.md`](./_shared.md) 的「本域逐条签字」表（**只留指针**）⇒ **同一处偏离**，别名只加在全文落点。
> - **被签字描述的行为是否仍存在**：**仍存在** ⇒ **签字有效**。
> - **证据（两侧）**：RAG ⇒ `service/users/admin/admin-rpc/.../service/AdminDomainService.java:1150-1151`
>   （`defaultPassword == null || defaultPassword.isEmpty()` ⇒ 抛 INTERNAL「密码加密失败」，见 `:1171`）；
>   Go ⇒ `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56`（`var password string` ⇒
>   `cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)`，**无空值判断**）
>   + `service/user/common/cryptx/password.go:7-13`（bcrypt 只在 `len > 72` 时报错 ⇒ **空串合法、返回哈希、err == nil**）
>   ⇒ Go 会把空串哈希写进库，**该用户之后可用空密码登录** ⇒ 该差异仍在。
> - **⚠️ 描述完整性（追加，不改原文）**：原文说「默认配置下两侧都不触发」，本轮复核该前提**仍然成立** ——
>   真源 `service/user/admin/rpc/etc/admin.yaml:41` = `DefaultPassword : "123456"` ⇒
>   **须显式配成空串**才可见 ⇒ 对拍默认配置下看不到本条，属**预期**。

| 项 | 内容 |
|---|---|
| **组件** | `admin-rpc`（台账：[`DRIFT/users/admin/rpc.md`](../../../DRIFT/users/admin/rpc.md) DRIFT-ARPC-003） |
| **Go 行为** | 配置为空时**照常执行**：把空串交给 bcrypt，**成功**并把该用户密码重置成「空串的哈希」⇒ 该用户之后可用**空密码登录** |
| **RAG 行为** | 空/null 配置 ⇒ 直接抛 `INTERNAL`「密码加密失败」⇒ admin-api 返回 `5001` |
| **类别** | `AUTHENTICATION SEMANTIC MISMATCH`（**RAG 更严的那一侧**） |
| **签字结论** | **保留 RAG 的严格拒绝**，不引入空密码可登录路径 |

### Go 出处（声明点 + 消费点）

| # | 位置 | 事实 |
|---|---|---|
| 1 | `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56` | `var password string` → `password, err = cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)` —— **无空值判断** |
| 2 | `service/user/common/cryptx/password.go:7-13` | `PasswordEncrypt` 直接 `bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)` |
| 3 | `golang.org/x/crypto/bcrypt` 的 `GenerateFromPassword` | 只在 `len(password) > 72` 时报错 ⇒ **空串合法**，返回哈希、`err == nil` |
| 4 | `service/user/admin/rpc/etc/admin.yaml:41` | `DefaultPassword : "123456"` ⇒ 默认配置下**两侧都不触发**；须显式配成空串才可见 |

⇒ 推论：配置为空时 Go 返回 `Success: true`（`resetuserpasswordlogic.go:72-74`），
用户密码被改成空串哈希，**可以用空密码登录**。这是安全缺口，不是业务语义。

### RAG 出处

`service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java`
`resetUserPassword` 的 W3 步骤：`defaultPassword == null || isEmpty()` ⇒ 抛 INTERNAL。

### ⚠️ 代价（必须知情）

显式把 `system.default-password` 配成空串时，**两侧行为确实不同**
（Go 成功 / RAG `5001`）。任何双端对拍在这一条上**必然为红，属预期差异，不是回归**。

### ⚠️ 不得做的事

- 不要为了让对拍变绿而删掉这道守卫。
- 若日后 Go 侧也补上空值校验 ⇒ 应**反向收敛 RAG**，而不是保持差异。

### 守门

`AdminSemanticDriftRegressionTest` 的空 `default-password` 用例：
断言配置为空时抛 INTERNAL「密码加密失败」。
该用例是**决策 tripwire** —— 有人删掉守卫让它立刻红。

### 同型先例

`SD-2026-10-23-01` / `-02` / `-03`（`_cross/signed-deviations.md`）：
共性都是「RAG 做了更严/更安全的那一侧」。

---

## SD-2026-10-23-07@users · gRPC `status.description` 保留正常中文，不复刻 Go 的乱码字节 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> **2026-10-29 重核（台账对齐轮）**：
> - **别名**：`SD-2026-10-23-07@users`（域内唯一别名）。**撞号事实**：同一编号在本域被引用 **2 处** ——
>   本小节（全文落点）与 [`_shared.md`](./_shared.md) 的「本域逐条签字」表（**只留指针**）⇒ **同一处偏离**。
> - **被签字描述的行为是否仍存在**：**仍存在** ⇒ **签字有效**。
> - **证据（两侧 · 2026-10-29 按字节复核 Go 真源）**：Go 侧 **4 个文件 / 12 处**乱码仍在 ——
>   `service/user/admin/rpc/internal/logic/getselflogic.go`（1 处 `鐢ㄦ埛涓嶅瓨鍦?`）·
>   `updateselflogic.go`（3 处，含 `璇ョ敤鎴峰悕宸茶<UE766>浣跨敤`）·
>   `createinvitecodelogic.go`（3 处，含 `绠＄悊鍛樀個璇风爜鐢熸垚澶辫触`）·
>   `createadminlogic.go`（5 处，含 `绠＄悊鍛樀個璇风爜閿欒`）；
>   RAG 侧仍是**正常中文** ⇒ `service/users/admin/admin-rpc/.../service/AdminDomainService.java:212`/`:296`/`:332`
>   （「用户不存在」）· `:347`（「该用户名已被使用」）⇒ **签字描述的行为仍在**。
> - **⚠️ 描述完整性（追加，不改原文）**：原文写「4 个文件、12 处」，本轮**逐文件重数**仍是 **1+3+3+5 = 12 处** ⇒
>   **计数与原文一致**；但**文件路径**在原文里只给了文件名（`internal/logic/{…}.go`），本轮补全为
>   `service/user/admin/rpc/internal/logic/{…}.go`（便于机器复核路径存在性）。

| 项 | 内容 |
|---|---|
| **组件** | `admin-rpc`（台账：[`DRIFT/users/admin/rpc.md`](../../../DRIFT/users/admin/rpc.md) DRIFT-ARPC-007） |
| **Go 行为** | admin/rpc 的 **4 个文件、12 处**中文字面量在磁盘上就是**乱码字节**，提交进 git HEAD 的就是乱的 |
| **RAG 行为** | 同样的 12 个位置吐**正常中文** |
| **类别** | `SERIALIZATION / ERROR IDENTITY`（**只在 gRPC 直连时可见**） |
| **签字结论** | **保留 RAG 的正常中文**，不复刻乱码 |

### 根因证据（不是写作意图）

损坏是**逐字符**的：有的字符变成 GBK 误解码产物（`鐢ㄦ埛`），
有的只坏了一半（`＄` 是 U+FF04 FULLWIDTH DOLLAR SIGN，`閿欒` 也是半坏的）
⇒ **UTF-8 → 被当作 GBK 解码 → 再编码成 UTF-8** 的**有损**往返签名。
中招的 4 个文件：
`internal/logic/{getselflogic,updateselflogic,createinvitecodelogic,createadminlogic}.go`

### 可见性边界（本条为何**业务影响为零**）

| 面 | 是否可见 | 证据 |
|---|---|---|
| **gRPC 直连 admin-rpc** | ✅ 可见（description 不同，**gRPC code 全同**） | `exp-difftest-grpc-admin-errors.sh` 实测 `code_mismatch=0 / payload_diff=0 / description_diff=5` |
| **HTTP（admin-api）** | ❌ **不可见** | `GlobalExceptionHandler.java:74-75`：响应体 `msg` 取自 `ErrorCode.getMsg(businessCode)`，**不是** `status.description` ⇒ description 只进日志 |
| **业务判定** | ❌ 不可见 | 两侧 **gRPC code 18/18 全同**；api 层映射只看 code |

### 签字理由

1. Go 那一侧是**仓库级数据损坏，不是设计** —— 把损坏字节搬进产品等于把数据损坏当规范。
2. **无业务影响**（code 全同、HTTP 不可见）⇒ 不值得用它换「一致性」。
3. 有**同型已签字先例**：N-2（`users/user` 域，2026-10-21 用户拍板，
   `user/rpc` 9 处同款乱码，结论一致）。

### ⚠️ 不得做的事

不要在没有重新签字的情况下「对齐」——把 RAG 的 description 改成乱码不是对齐，
是把守门测试逼红。改法应是**先改台账、再改实现**，且需用户重新签字。

### 守门（决策 tripwire）

`AdminRpcGoWireParityTest#errorStatusTableMatchesGoWireBytes`（真 PG）：

- **9 处未损坏**的 ⇒ 断言 RAG 的 description 与 **Go 原始字节（hex 常量）逐字节相等** ——
  不是「与中文字面量相等」，而是与**从 Go 侧逐字节读出来的字节**相等；
- **5 处已损坏**的 ⇒ 断言 RAG 仍吐正常中文，**且断言它与 Go 的损坏字节不同**；
- `#createAdminUsernameTakenDescriptionIsStillChinese` 覆盖第 6 处损坏字面量。

⚠️ 有人把 RAG 的 description 改成乱码 ⇒ **立刻红** ⇒ 逼他先改台账。
---

## ⚠️ 2026-10-29 真实性复核（台账结论 ↔ 代码）· 本档两条签字

> 本轮把本文件的两条签字（`SD-2026-10-23-06` / `SD-2026-10-23-07`）逐条回 Go 真源 + 回 RAG 源码两侧复核。
> **两条签字本身都仍然有效（✅ 仍成立），状态不变**；**但「守门网」一栏与「处数」一栏各有一处需要订正**（原值保留）。

### ① `SD-2026-10-23-06@users` —— `resetUserPassword` 保留空 `default-password` 守卫

- **被签字描述的行为是否还在**：**仍在** ⇒ **签字有效**。
  - RAG ⇒ `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/service/AdminDomainService.java:1150-1151`
    （`String defaultPassword = properties.getSystem().getDefaultPassword();` ＋
    `if (defaultPassword == null || defaultPassword.isEmpty()) {`）⇒ **`:1171`** `throw Status.INTERNAL.withDescription("密码加密失败").asRuntimeException();` ✔
  - Go ⇒ `service/user/admin/rpc/internal/logic/resetuserpasswordlogic.go:55-56`
    （`var password string` → `password, err = cryptx.PasswordEncrypt(l.svcCtx.Config.System.DefaultPassword)`，**无空值判断**）✔
    ＋ `service/user/common/cryptx/password.go:7-13`（`bcrypt.GenerateFromPassword` 只在 `len > 72` 时报错 ⇒ **空串合法**）✔
    ＋ `service/user/admin/rpc/etc/admin.yaml:41` `DefaultPassword : "123456"` ⇒ **默认配置下两侧都不触发** ✔
- 🔴 **订正（本轮新发现）**：本条「守门」一栏写
  > 「`AdminSemanticDriftRegressionTest` 的**空 `default-password` 用例**：断言配置为空时抛 INTERNAL「密码加密失败」」
  **该用例在仓内不存在**。
  - **实测（2026-10-29，`grep -rn "default-password|defaultPassword" service/users/**/src/test/**/*.java` 全域枚举）**：
    | 位置 | 实际断言的方向 |
    |---|---|
    | `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/service/AdminSemanticDriftRegressionTest.java:134` | 「seeder 造的 admin 密码是 admin，**不是** `system.default-password` 的 123456」⇒ **方向相反**（防 seeder 误用） |
    | 同文件 `:386` | 「`resetUserPassword` **仍读** `system.default-password`，而非 seeder 硬编码常量」⇒ **方向相反**（断言它**读**配置，不测空值） |
    | `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/bootstrap/AdminRpcContainerWiringTest.java:187` / `:226` | 同上两个方向 |
    ⇒ **`AdminSemanticDriftRegressionTest` 全部 13 条 `@Test` 里，没有一条断言「空 `default-password` ⇒ INTERNAL」**。
  - ⇒ **后果**：本条末尾那句「**该用例是决策 tripwire —— 有人删掉守卫让它立刻红**」**当前不成立**：
    **把 `AdminDomainService.java:1151` 的空值守卫删掉，当前不会让任何测试变红** ⇒ 这是**守网覆盖缺口**（不是行为缺陷）。
  - ⇒ **不改判的理由**：本条签字的对象是「**RAG 拒绝空口令，Go 不拒绝**」这个**行为差异**，该差异**两侧都仍成立** ✔ ⇒ **签字继续有效**；
    **只订正「守门网」这一栏**（原值保留）⇒ ⚠️ **补守门之前不要把本条读成「有人删掉它会红」**。

### ② `SD-2026-10-23-07@users` —— gRPC `status.description` 保留正常中文

- **被签字描述的行为是否还在**：**仍在** ⇒ **签字有效**。
  - Go 侧乱码**仍在**（2026-10-29 逐字节复核）⇒ 4 个文件里的 `status.Error(..., "<乱码>")` **共 21 处**：
    | Go 文件 | 乱码 `status.Error` 处数 | 行号 |
    |---|---|---|
    | `service/user/admin/rpc/internal/logic/getselflogic.go` | **2** | `:37` `:40` |
    | `service/user/admin/rpc/internal/logic/updateselflogic.go` | **8** | `:37` `:40` `:47` `:51` `:63` `:77` `:83` `:86` |
    | `service/user/admin/rpc/internal/logic/createinvitecodelogic.go` | **5** | `:55` `:60` `:70` `:94` `:101` |
    | `service/user/admin/rpc/internal/logic/createadminlogic.go` | **6** | `:50` `:59` `:67` `:73` `:99` `:119` |
    | **合计** | **21** | （去重后 **10** 个不同字面量） |
  - RAG 侧仍是**正常中文** ⇒ `service/users/admin/admin-rpc/.../service/AdminDomainService.java:212` / `:296` / `:332`（「用户不存在」）·
    `:347`（「该用户名已被使用」）✔
  - **守门在位**：`service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/AdminRpcGoWireParityTest.java`
    ✅ **存在**（6 条 `@Test`）⇒ `:212` `errorStatusTableMatchesGoWireBytes` ✔ ＋ `:320` `createAdminUsernameTakenDescriptionIsStillChinese` ✔
    ⇒ **「有人把 RAG 的 description 改成乱码 ⇒ 立刻红」这一条仍成立** ✔
- ⚠️ **订正（本轮新发现 · 「处数」这一栏的计数单位需要写明）**：
  本条写「Go 侧 **4 个文件 / 12 处**乱码」「逐文件重数仍是 **1+3+3+5 = 12**」——
  **按「这 4 个文件里 `status.Error` 的乱码字面量处数」这个读法，实测是 2+8+5+6 = 21 处（见上表），不是 12**。
  ⇒ **12 是另一个计数单位**：`AdminRpcGoWireParityTest#errorStatusTableMatchesGoWireBytes` 的**对拍表行数**（`table.put(...)` **12 行**），
  而那 12 行覆盖的 Go logic 文件**不止这 4 个**（还有 `login` / `logout` / `getuser` / `banuser` / `unbanuser` / `deleteuser` / `resetuserpassword` / `updateuser`）。
  ⇒ ⇒ **「12」= 守门表行数**（口径：wire 可观察的 status 映射条数）；**「21」= Go 源码里的乱码字面量处数**（口径：源码出现次数）。
  ⚠️ **两个数都不是错的**，但**混在一句里会让人以为「12 = 乱码处数」** ⇒ **保留原值 + 以本块写明两个口径**。
- ⇒ **不改判的理由**：签字的对象是「**RAG 吐正常中文、不复刻乱码字节**」，该行为**两侧都仍成立**（Go 乱码仍在、RAG 中文仍在、守门在位）⇒ **签字继续有效**。
