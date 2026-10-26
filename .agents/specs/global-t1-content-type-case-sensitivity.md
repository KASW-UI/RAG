<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 58e8314（上一轮 24fc832） -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global T-1 —— Content-Type 大小写敏感判定（对齐 go-zero `withJsonBody`）

> **PR 范围**：`align/global` → `do-my-own-gh`（**由用户自己合并**，agent 不碰主干）
> **域**：`global`（收口域，§3.5）· **议题档**：`common-tails`（+ `cross-domain`）
> **基线**：`align/global` @ `24fc832`（上一轮 CYCLE-001 闭环）

---

## 一、要解决什么

**Go 判据说「这是 JSON body」时用的是客户端发来的原始头值；RAG 用的是 Spring 解析后的小写化结果。**

| | 事实 |
|---|---|
| **Go 出处** | `go-zero@v1.10.2/rest/httpx/requests.go:153-155`<br>`return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)`<br>常量：`rest/internal/header/headers.go:5` ⇒ `ApplicationJson = "application/json"` |
| **RAG（修前）** | `service/common/http/GoStyleJsonBodyConverter.java:115-117`<br>`inputMessage.getHeaders().getContentType().toString()`<br>⇒ **Spring 的 `MediaType.toString()` 已把 type/subtype 小写化** |
| **可观察差异** | `Content-Type: Application/JSON` ⇒<br>**Go：不读 body**（走 `mapping.UnmarshalJsonMap(nil, v)`，body 被丢弃）<br>**RAG（修前）：读 body**（进 Jackson 反序列化） |

⇒ **两侧行为方向相反** ⇒ 不是「RAG 更严」那种可签字的偏离，是**行为漂移**。

---

## 二、改什么（**一行 + 注释**）

| 文件 | 改动 |
|---|---|
| `service/common/http/GoStyleJsonBodyConverter.java` | `read()` 里把 `getContentType().toString()` 换成 **`getFirst(HttpHeaders.CONTENT_TYPE)`**（原始头值）<br>+ 在 `goReadsBody` 的 javadoc 里写明「**必须传原始头值，不要传 `MediaType.toString()`**」这条契约 |

⇒ **落点全在 `service/common`** ⇒ 不需要改任何别的域的代码。
⚠️ 已核对：`like-api` 的 `LikeApiGoStyleBodyContentTypeTest#goReadsBodyBoundaries` 的 **7 条断言全是已小写值**
⇒ 修复**不改变任何现有期望值**（实测该模块测试全绿）。

---

## 三、证据（Go ↔ RAG 逐行对照）

| 事实 | 证据 |
|---|---|
| Go 读**原始**头值 | `r.Header.Get(...)` 返回客户端发来的原始字符串，**不做大小写归一** |
| Spring 会**小写化** | `HttpHeaders.getContentType()` 解析成 `MediaType`；其 `toString()` 输出小写的 type/subtype |
| `getFirst(CONTENT_TYPE)` 给**原始**值 | Spring `HttpHeaders` 的第一个 header 值，**未经 MediaType 解析** |

### 🧪 守门测试

新增 `service/common/src/test/java/com/rag/common/http/GoStyleJsonBodyConverterContentTypeCaseTest.java`（**6 条**）

| # | 断言 |
|---|---|
| ① | `Application/JSON` ⇒ **不读 body**（返回 `null` = Go 的 `UnmarshalJsonMap(nil, v)`） |
| ① | `APPLICATION/JSON` / `application/JSON` ⇒ 同样**不读** |
| ① | `application/json` ⇒ **读 body**（修复没把正常路径打掉） |
| ① | `application/json; charset=utf-8` / `application/json;charset=utf-8` ⇒ **读 body**（Go 是 Contains 不是精确相等） |
| ② | `application/json;;charset`（Spring 能容忍的头内畸形）⇒ **不抛异常**（Go 只做字面 Contains、不会抛） |
| ③ | `goReadsBody` 直接断言 8 个边界，**含新增的「大小写 ⇒ false」** |

⇒ **刻意不用 Mockito**（§3.3.3）：被测的机制正是「**`HttpHeaders` 到底返回什么**」⇒
mock 掉等于把被测对象本身删掉 ⇒ 用**真 `HttpHeaders` + 真 converter**，
只把两方法的 `HttpInputMessage` 用最小真实实现顶掉。

**🧪 变异测试**（证明守门网非空转）：把修复改回 `MediaType.toString()` ⇒ **转红**
⇒ `expected: <null> but was: <{target_type=article, target_ids=[1]}>`。

---

## 四、⚠️ 本轮**没有**修掉的残余（已登记，不藏）

### T-1 残余 ①：**判定层修好了，解析层还会碰 MediaType**

| | 事实 |
|---|---|
| **Go** | `strings.Contains(raw, "application/json")` ⇒ **纯字面**，**不做 MIME 解析** ⇒ 畸形头**不会抛**，命中就照常读 body |
| **RAG（修后）** | 判定用原始值 ✔；但命中后 `super.read(...)` 内部仍调 `getHeaders().getContentType()` 做 MediaType 解析 ⇒ **`application/json, application/xml` 仍抛 `InvalidMediaTypeException`**（实测：`Invalid token character ','`）⇒ 请求 500 |
| **建议修法** | 命中后把传给 `super.read(...)` 的 `HttpInputMessage` **包一层**，让 Spring 看到**已净化的** `Content-Type`（如 `application/json`）⇒ 顺带修掉「`charset=gbk` 时 RAG 按 GBK 解码、Go 恒按 UTF-8」的另一条偏差（Go 的 `encoding/json` 只认 UTF-8） |

⚠️ **本轮刻意没做**：那会**扩大改动面**（要包 `HttpInputMessage`），而本轮授权范围是「修 T-1 的大小写判定」
⇒ 按 §5.3「不擅自扩大 scope」登记待办。
⚠️ **守门测试也刻意不断言该行为** —— 断言它「会抛」等于把一个**已知与 Go 不一致**的行为
**锁成标准**（§3.3.3：绿测试可能是「错误答案的守卫」）。

---

## 五、附带发现（本轮记账，不改别域代码）

### X-9：「Go 是否读 body」判据有 **3 份生产复制品**

| # | 位置 | 取的头值 | 当前是否与 Go 一致 |
|---|---|---|---|
| 1 | `favorite-api/.../FavoriteController.java:242` | `getHeader("Content-Type")` ⇒ 原始 | ✅ 一致 |
| 2 | `hot-api/.../GoFormValues.java:272` | `getContentType()` ⇒ 原始 | ✅ 一致 |
| 3 | `article-api/.../GoStyleJsonBodyLimitFilter.java:123` | `getContentType()` ⇒ 原始 | ✅ 一致 |

⇒ **T-1 修复前，RAG 内部是自相矛盾的**：
`Content-Type: Application/JSON` ⇒ 三份复制品判「不读」（对），唯一真源判「读」（错）
⇒ 同一个请求，**闸门与解析器结论相反**。
⇒ **T-1 修复后已一致。**

⚠️ **根治需各域自己收敛**（把三份复制品改成调用 `goReadsBody(...)`）⇒ 那三个文件分别在
favorite / hot / article 域 ⇒ **不是 global 的写权范围**（§5.3b 规则 1 的反向也成立：
**global 也不该去改别的域的业务代码**）⇒ **本轮只记账 + 在唯一真源的 javadoc 里点名这三处**。

---

## 六、附带落地：T-2 / T-5 用户签字（2026-10-25）

| # | 动作 | 落点 |
|---|---|---|
| ① | 候选 `SD-2026-10-25-CAND-11` 转正为 **`SD-2026-10-25-01`**，状态「已签字」 | `docs/Intentional_Deviations/global/common-tails.md` |
| ② | 代码 javadoc 的「有意的表达层收敛」自述 ⇒ **改成指向该签字档的指针**（并加「不要因为 Go 是 per-route 就顺手改回」） | `service/common/protection/GoStyleMaxConnsFilter.java` |
| ③ | 从 DRIFT 侧**移除**该条（只留一行链接）⇒ §5.3b 硬边界 2 | `docs/DRIFT/global/common-tails.md` + `_README.md` |

⇒ **签字决定 = 保留 RAG 的全局闸门，不改成 per-route**。
⚠️ **签字 ≠ 已解决**：残留风险（作用域日后被悄悄改样）仍在 ⇒ 已记「补一条钉住作用域的断言」为待办。

---

## 七、台账产出（§5.3b 三档）

| 档 | 文件 | 内容 |
|---|---|---|
| DRIFT | `docs/DRIFT/global/common-tails.md` | T-1 已修（含修法 + 守门 + 变异）· **T-1 残余 ①** 新登记 · T-2/T-5 移出只留指针 |
| DRIFT | `docs/DRIFT/global/cross-domain.md` | **X-9** 新登记（3 份生产复制品 + 内部自相矛盾证据） |
| DRIFT | `docs/DRIFT/global/_README.md` | 主索引同步 |
| Intentional_Deviations | `docs/Intentional_Deviations/global/common-tails.md` | `CAND-11` → **`SD-2026-10-25-01`** 已签字 |
| Pitfalls | `docs/Pitfalls/global/common-tails.md` | PIT-GLOBAL-002 补一句「已由签字收口」 |

⚠️ **不碰**：三个 `_INDEX.md` · `AGENTS.md` · `docs/DRIFT/_MANIFEST.json` ⇒ 合并后由主会话统一更新。

---

## 八、验收标准

- [x] `mvn -pl service/common -am package` 通过
- [x] `mvn -pl service/like/like-api -am package` 通过（验证跨域调用方的期望值未受影响）
- [x] 全量 `mvn clean verify` 通过
- [x] 新增守门 6 条全绿，**且**变异（改回 `MediaType.toString()`）转红
- [x] 台账三档写回（只写 `global` 子目录）
- [x] spec 已落仓

---

## 九、本 PR **不做**什么

| 不做 | 原因 |
|---|---|
| 修 T-1 残余 ①（解析层包一层 `HttpInputMessage`） | 会扩大改动面 ⇒ 已登记为待办 |
| 收敛 X-9 的 3 份复制品 | 那三个文件属 favorite / hot / article 域 ⇒ **各域自己改**（global 只记账） |
| 修 T-9（熔断 target 前缀） | 需先做「devh starter 能否拿到 channel target」的可行性探针 |
| 补 T-2/T-5 的「钉住作用域」断言 | 签字刚落地 ⇒ 记为待办，下一轮做 |
| 动 `service/common` 以外的域代码 | §5.3b：别的域的代码，别的域自己改 |
| **合并进 `do-my-own-gh`** | ⚠️ **用户明确要求由他自己合并** |
