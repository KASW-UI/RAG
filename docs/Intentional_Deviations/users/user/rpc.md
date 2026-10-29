# 有意偏离 · `service/users/user/user-rpc`

> **这里是「决策」，不是缺陷。** 与 [`../../DRIFT/users/user/rpc.md`](../../../DRIFT/users/user/rpc.md) 的分工：
> DRIFT 记「需要对齐的差异」，本文件记「**决定不对齐**的差异」。
>
> ⚠️ **读之前先记住两条**：
> 1. 本文件里的条目**不必重复质疑**，但也**不得「顺手修正回旧行为」**。
> 2. 审查时**先来这里确认**，再决定某条红色差异是不是回归。
>
> ⚠️ **本文件此前是 0 字节空占位**（2026-10-29 台账对齐轮补齐）。
> 空洞的代价有实测记录：`docs/Pitfalls/users/admin/rpc.md` 的 **P-9**（`PIT-USERSADM-26`）——
> 「**空占位文件**会让人把『已签字』误判成『未签字』」⇒ 本域的签字原先**只存在于
> `docs/DRIFT/users/user/api.md:2135` 的搬移留档表里**（那是**漂移台账**，按 §7.2 的读法不该当签字档用）
> ⇒ 检索走 `docs/Intentional_Deviations/_INDEX.md → users/ → user/` 这一层会**查不到** ⇒ 本轮补齐。

---

## N-2@users（2026-10-21 用户拍板）—— Go 真源的中文错误消息是**乱码字节**，RAG **保留正确中文**，不复刻 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

| 项 | 值 |
|---|---|
| **组件** | `service/users/user/user-rpc` |
| **对应 DRIFT** | `DRIFT-USERRPC-V-007` 的姊妹项（乱码文案不属超时路径）· 双侧活体对拍结论见 [`docs/DRIFT/users/user/rpc.md`](../../../DRIFT/users/user/rpc.md) §9.1.2 |
| **签字日期** | **2026-10-21**（用户拍板） |
| **决定** | **保留 RAG 的正常中文**，不复刻 Go 的乱码字节 |
| **性质** | ① 有意偏离 —— RAG 侧**输出的是正确信息**，不是「忘了做」；Go 侧那一份是**仓库级数据损坏**，不是设计 |
| **影响面** | **仅 gRPC `status.description`**（Go api 层只按 `status.Code` 分支、不透传 message ⇒ **不进 HTTP body**） |
| **守门网** | `tools/difftest/exp-difftest-grpc-user9.sh` 双侧活体对拍：**43 场景 / 39 等价 / 4 处差异，4 处全是本条已签字的 Go 乱码** ⇒ 对拍脚本与本条共用一次证据 |

### 2026-10-29 重核（台账对齐轮）

| 项 | 结论 | 证据 |
|---|---|---|
| **别名** | `N-2@users` | 本条是 `N-` 编号（非 `SD-`）；**域内唯一别名**，原编号 `N-2` **原样保留**。**撞号检查**：本域**无**第二个 `N-2` 指向别的偏离（`docs/Intentional_Deviations/users/**` 内 `N-2` 仅此一处） |
| **被签字描述的行为是否仍存在** | ✅ **仍存在 ⇒ 签字有效**（真源冻结，乱码不会消失） | Go 侧逐文件**按字节重数**（2026-10-29 实测）：**乱码消息 9 处 / 3 个文件** —— `service/user/user/rpc/internal/logic/uploadavatarlogic.go` **3 处**（`history_id鐢熸垚澶辫触` / `鐢ㄦ埛涓嶅瓨鍦?` / `DB鎻掑叆澶辫触`）· `selectavatarlogic.go` **3 处**（`鐢ㄦ埛涓嶅瓨鍦?` / `澶村儚鍘嗗彶涓嶅瓨鍦?` / `DB鏇存柊澶辫触`）· `getavatarhistorylogic.go` **3 处**（`鐢ㄦ埛涓嶅瓨鍦?` / `DB鏌ヨ澶辫触` ×2）⇒ **与原文「3 文件 · 9 处」逐条一致** |
| **对照组（正常 UTF-8 的那些）** | ✅ 仍正常 | 同目录 `deleteuserlogic.go` / `getuserlogic.go` / `loginlogic.go` / `logoutlogic.go` / `registerlogic.go` / `updateuserlogic.go` 的中文 message **仍是正常 UTF-8**（2026-10-29 实测）⇒ 乱码是**局部**损坏，不是整目录编码问题 |
| **RAG 侧** | ✅ 仍是正常中文 | RAG 的对应位置写正常中文（例：`service/users/user/user-rpc/.../service/UserDomainService.java` 的「用户不存在」「头像历史不存在」）⇒ **签字描述的行为两侧都仍在** |

### 为什么签字保留（不改成乱码）

1. **Go 那一侧是仓库级数据损坏，不是设计** —— 把损坏字节搬进产品等于把数据损坏当规范；
2. **Go 已冻结**（§2 不接受写入）⇒ 真源侧**无法修** ⇒ 若 RAG 也不复刻，则两侧永远不可互认；
3. **可追溯性**：乱码不可读，落到日志与 gRPC status message 上会直接妨碍排障；
4. **有同型已签字先例**：`docs/Intentional_Deviations/users/admin/rpc.md` 的 `SD-2026-10-23-07@users`
   （admin 侧 4 文件 12 处同款乱码，结论一致）。

### 效力边界

- 本条**只覆盖 gRPC `status.description` 的文案**；
- **不覆盖** gRPC **code**（两侧 code 全同）；
- **不覆盖** HTTP body（Go api 层不透传 message）；
- ⚠️ **不得做的事**：不要在没有重新签字的情况下把 RAG 的文案改成乱码 —— 那不是「对齐」，是把守门测试逼红；
  正确顺序是**先改台账、再改实现**，且需用户重新签字。

### 关闭条件

若日后 Go 侧修正了编码 ⇒ 需同步 RAG 的 9 条 message（本条即失效）；
在此之前，本条**长期有效**。

---

## 相关指针（同域 · 别当成本文件的条目）

| 编号 | 落点 | 关系 |
|---|---|---|
| **N-1**（`users/user` 域 · **401 响应体形状**） | 权威原文在 [`docs/DRIFT/users/user/api.md:2135`](../../../DRIFT/users/user/api.md) 的搬移留档表（**签字留痕**）· 域内现行转录见 [`users/user/api.md` 的 `SD-USERAPI-003@users`](./api.md) | **不属于 `user-rpc`**（N-1 是 HTTP 鉴权面的偏离，落在 user-api）⇒ 本文件**不重复登记**，只留指针 |
| **`SD-USERAPI-001@users` / `SD-USERAPI-002@users` / `SD-USERAPI-003@users`** | [`users/user/api.md`](./api.md) | 同一域（`users/user`）的 user-api 侧签字 ⇒ **与本文件分属两个组件** ⇒ 别混谈 |

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-21 | 🏷 **签字拍板** —— 用户拍板「RAG 保留正确中文」，不复刻 Go 的乱码（原文留痕在 `docs/DRIFT/users/user/api.md:2135` 的 **N-2** 行） |
| **2026-10-29** | 🆕 **台账对齐轮** —— 本文件由 **0 字节空占位**补齐为正式签字档；逐条重核「被签字描述的行为是否仍存在」（**仍存在 ⇒ 签字有效**）· 加域内唯一别名 **`N-2@users`** · 补 Go 侧 **9 处 / 3 文件**的逐文件字节复核证据与「对照组仍是正常 UTF-8」的实测证据 |
