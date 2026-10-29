<!-- @new 2026-10-23 23:0x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-api` 签字偏离（域内索引）

> **本文件是索引，不是第二条记录。** 签字偏离的**正本**在
> [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) §「SD-2026-10-23-03」。
> 重复登记会让同一件事有两个版本，日后改一处忘一处（§5.3b 三条硬边界之二）。

## 本域已有的签字偏离：1 条

| SD 编号 | 一句话 | 正本 |
|---|---|---|
| `SD-2026-10-23-03@message` | `AdminSendNotification` 的 `affected`：Go 返 `int64`（JSON **数字**），RAG 输出**字符串** | [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) |

> **唯一别名**：`SD-2026-10-23-03@message`（⚠️ **撞号事实**：同一编号在 [`_cross/signed-deviations.md`](../_cross/signed-deviations.md) 是**正本**、在本文件是**域内索引** ⇒ 两者指向**同一条**偏离（不构成跨域撞号）；但 `comment` / `like` 域另有 `SD-2026-10-23-04@comment` / `SD-2026-10-23-05@like` ⇒ 为跨域唯一定位，本条加域内别名）
> **签字复核（核对日期 2026-10-29）**：**仍成立 ⇒ 签字有效，继续计入计数** —— 被签字描述的行为**今天仍存在**：RAG 侧 `AdminSendNotificationResp` 仍带 `@JsonFormat(shape = JsonFormat.Shape.STRING)` ⇒ 出参是 JSON **字符串** `"3"`；Go 侧出参是 JSON **数字** `3` ⇒ wire 契约差异**未消除**
> **依据**：`service/message/message-api/src/main/java/com/rag/message/message/api/dto/AdminSendNotificationResp.java:43`（`@JsonFormat(shape = JsonFormat.Shape.STRING)`）· Go `service/message/api/internal/types/types.go:22`（`Affected int64` + `json:"affected"` tag，**无** `,string`）⇒ **证据强度：源码行号（静态）**。


### 对本域审查者的三条提醒

1. **它必然让 `affected` 的 wire 对拍变红。** 看到 `{"affected":"3"}` 而非 `{"affected":3}` 时，
   先确认是不是这一条，**再**下结论。
2. **不要单方面改回数字。** 原 DRIFT 台账 `DRIFT-MSGAPI-009` 的
   「Must Preserve：affected 是 JSON 数字」一句已于 2026-10-23 作废 ——
   按 Go 改会把**已签字的偏离**变成**未受控的漂移**。
   若确要改回 Go 形态，**须重新签字**。
3. **不要把 javadoc 的错误描述当依据。** `AdminSendNotificationResp` 的类注释当年把 Go tag 抄成
   `json:"affected,string"`（真源 `types.go:21-23` 是 `json:"affected"`），
   那句错误的「Go: …」正是加 `@JsonFormat(STRING)` 的理由 —— §3.3.2 的典型样本。
   该注释已于本轮改正为中性描述 + 指向本条 SD。

### 本轮顺带关闭的一处台账矛盾

`DRIFT/message/api.md` 的 §7 仍把 `-009` 列为「**未修** · SEMANTIC MISMATCH」，
而签字档已按「保留 RAG 现状」拍板 ⇒ 两者直接矛盾。
按 §5.3b 的判据（**这条差异要不要「修回去」？**）：不要 ⇒ 属本档，不属 DRIFT。
DRIFT 侧的更正已写入 [`DRIFT/message/api.md`](../../DRIFT/message/api.md) 第三轮第五节
（保留原始差异记录、不删除，只更正状态）。

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 本来都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| 请求保护层的四层（MaxBytes / MaxConns / CPU 降载 / 熔断） | ❌ 不属偏离。首轮补齐时即按 Go `service/common/observability` 的语义实现，见 `DRIFT/message/api.md` §0 的「请求保护层：四层全部补齐」 |
