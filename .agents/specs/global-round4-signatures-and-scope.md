<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit c6ab92f -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global 第四轮 —— 两处签字（CAND-12 / X-10）+ 写权范围登记 + #8 销账

> **PR 范围**：`align/global` → `do-my-own-gh`
> **域**：`global`（收口域，§3.5）· **议题档**：`common-tails` + `cross-domain`
> **基线**：`align/global` @ `c6ab92f`
> **写权**：用户 2026-10-25 明确授权 ——「**你有对除了 favorite 外其他所有代码的权限**」

---

## 一、要解决什么

用户 2026-10-25 的三条决定 + 一个盘点：

1. `SD-2026-10-25-CAND-12` 是什么 ⇒ **解释 + 落账**；
2. **X-10 保留 RAG 的复合索引并签字**；
3. **写权扩展**：除 `favorite` 外所有代码的权限；
4. global 域内还剩哪些可修漂移 / 未知项。

---

## 二、改什么

### ① `SD-2026-10-25-CAND-12` → **`SD-2026-10-25-02`（已签字）**

**它是什么**：`T-4`（客户端断连）的**资源面**残留 ——
「客户端断连后，**在飞的 gRPC 不会被提前取消**」
（Go 侧 `net/http` 取消 `r.Context()` ⇒ 断连后 1~3ms 内中止下游调用；RAG 做不到）。

**为什么不可修**（真 Tomcat 11.0.24 实测 + 源码取证）：
Servlet 容器**不向应用暴露「客户端已断连」信号**
（`startAsync()` + `AsyncListener` + 裸 socket RST ⇒ `onError`/`onTimeout`/`onComplete` **一个都不触发**；
Tomcat 源码 `AbstractProcessorLight.java:62` 对 `SocketEvent.DISCONNECT` 明写 *"Do nothing here"*）
⇒ 断连的唯一可观测时刻 =「往 socket 写失败」⇒ 只能**事后**知道，**无法提前取消**。

**为什么它不需要决策空间**：
按 `docs/Intentional_Deviations/_INDEX.md` 的三分口径 ——
「有意偏离」= 主动选择与 Go 不同（须签字）；「**框架限制**」= 想对齐但语言/框架做不到。
⇒ 本条属**后者** ⇒ **不存在「修不修 / 改哪边」的选择题**
⇒ 它是**已定性的事实登记**，不是待拍板项 ⇒ 签字只**认账这个事实**，**不改任何代码**。

### ② X-10 → **`SD-2026-10-25-03`（已签字，用户拍板）**

**签字决定**：**保留 RAG 的 `(recipient_id, is_read)` 复合索引**，**不改回** Go 的单列 `(is_read)`。

**根因在 Go 侧**（`service/message/rpc/internal/model/data_model.go:32,38,41`）：
`is_read` 声明的复合索引 `idx_notification_recipient_read` **只有 `priority:2`、没有 `priority:1`**，
而 `recipient_id` 的 `priority:1` 被写到了**另一个索引名**（`idx_notification_recipient_time`）上
⇒ GORM 只建出**单列**索引 ⇒ **是声明笔误的产物**，不是设计意图。

**为什么签字而不是改回 Go**：
① 改回 Go = **把 RAG 改差**（丢掉一个覆盖「按 recipient 过滤未读」的可用索引）；
② 根因在真源 ⇒ **笔误不该被对齐**（§3.1 的反面：不能把真源的笔误当规范）；
③ 影响面 = 1 张表的 1 个索引，**无 wire 差异**。

**配套落地**：
① 迁移脚本里那条「对应 Go 端 …`@gorm:index:idx_notification_recipient_read,priority:2`」的注释**订正**
（它会让人误以为 1:1）⇒ 改成**说明 Go 侧笔误 + 指向签字档的指针**；
② `docs/DRIFT/global/cross-domain.md` 的 X-10 **移出 DRIFT 档**（§5.3b 硬边界 2），只留指针。

### ③ 写权范围登记（`_README.md` §七 纪律 1b）

⇒ `global` **可以**改**除 `favorite` 外**任何域的代码与文档；
⚠️ **但 `favorite` 一律不碰**；
⚠️ **单写权方向相反且仍成立**：`service/common/**` **只有 `align/global` 能写**，别的域只读
⇒ ⇒ **跨域改动必须同一次提交**。

### ④ #8 销账 —— `docs/Pitfalls/common/_signed-deviations.md`

确认是 **0 字节**的错放文件（真名在 `docs/Intentional_Deviations/common/_signed-deviations.md`）
⇒ **直接删除**（0 字节 ⇒ 删掉不可能丢内容）。

---

## 三、验证

- `mvn -o -pl service/message/message-rpc -am test` ⇒ 绿（本轮只改 SQL **注释** + 文档）
- 本轮**无 Java 逻辑改动** ⇒ 不需要重跑全量 verify
- 台账一致性：`_README.md`（§五 / §六 / §七 / §八）× `cross-domain.md` × `Intentional_Deviations/global/*` 同步

---

## 四、**不在**本次范围

| 项 | 为什么 |
|---|---|
| `favorite` 域的一切 | 用户明确不碰 |
| C 类（`DRIFT-HOTAPI-030` / `HOTAPI-031` / `DRIFT-COMMON-005` / users 域 DEFAULT 补签字） | 用户明确「先等下」⇒ 本轮**未**动 |
| 共享面（三个 `_INDEX.md` / `AGENTS.md` / `_MANIFEST.json`） | §5.3b：合并后由主会话统一更新<br>⇒ ⚠️ **`docs/Intentional_Deviations/_INDEX.md` 的「CAND 族 10 条未签字」计数已过期**（本轮转正 1 条、签 1 条）⇒ **留给主会话** |
| X-1 / X-2 / X-5 / X-7 剩余部分 | 各自独立一轮（见盘点回复） |
