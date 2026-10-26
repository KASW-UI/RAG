# global 第十八轮 —— B10 落地：`users_user.extra_info` 写路径 jsonb → text（Go 成功 / RAG 5001）

> **域**：`global`（收口域）· **分支**：`align/global` · **基线**：`20172b8`
> **性质**：**users 域真漂移修复 + 真 PG 活体坐实 + SQLSTATE 订正 + 台账**
> **本轮不碰**：`favorite` 域（**完全无交集** ⇒ 零改动）
> ⚠️ **跨域**：落点跨 `users-common`（DDL 资源）+ `admin-rpc`（写路径）⇒ **同一次提交**（§5.3c）

---

## 一、要解决什么

`docs/DRIFT/global/cross-domain.md` §二·九 ③（第九轮只读审计产出）：

> `users_user.extra_info` 的写路径仍按 **jsonb** 处理
> ⇒ 原注释写「该列**仍是 jsonb**，CAST 必须保留」
> ⇒ **但那个前提早已失效** —— 该列**早已是 TEXT**。

⇒ 这是一条**真漂移**（Go 成功 / RAG 5001），且**代码注释在骗人**。

---

## 二、🔴 取证：先真 PG 复现，再动手

台账要求「**需真 PG 复现后修**」⇒ 先用独立 PG 16 容器把机制打实。

### ① 探针（独立 PG 16 容器，非本仓测试）

```sql
CREATE TABLE t_text  (extra_info TEXT);
CREATE TABLE t_jsonb (extra_info JSONB);

-- Go 的路径：encoding/json 产出字符串 → 落进 text 列
INSERT INTO t_text(extra_info) VALUES ('{"k":"\u0000"}');
SELECT length(extra_info) FROM t_text;      -- ⇒ 14

-- RAG 修复前的路径：CAST 成 jsonb
INSERT INTO t_jsonb(extra_info) VALUES (CAST('{"k":"\u0000"}' AS jsonb));
```

**实测**：

```
(a) text 列  → INSERT 0 1，内容 = [{"k":"\u0000"}]，字节数 = 14
(b) CAST jsonb → ERROR:  22P05: unsupported Unicode escape sequence
                  DETAIL:  \u0000 cannot be converted to text.
                  LOCATION:  json_errsave_error, jsonfuncs.c:634
```

⇒ **坐实两件事**：

1. **Go 成功 / RAG 5001**（同一份 payload，两条路径一成一败）；
2. 🔴 **SQLSTATE 是 `22P05`，不是 `22P02`** ⇒ **台账与所有旧注释都写错了**。

### ② 为什么是 `22P05` 而不是 `22P02`

| 码 | 含义 | 这里该是谁 |
|---|---|---|
| `22P02` | `invalid_text_representation` | ❌ **不是**（那是「文本不是合法数字/日期」那类） |
| **`22P05`** | `json_bad_unicode_escape` | ✅ **就是它**（`json_errsave_error`） |

⚠️ 旧注释写「22P02 unsupported Unicode escape sequence」—— **把「消息文本」和「SQLSTATE」配错了对**。
⇒ 这是一个**到处复制**的错误（台账 2 处 + 代码注释 2 处 + DDL 注释 1 处 + users 域台账 3 处）。

### ③ 差异②：jsonb 会**规范化** JSON

`{"k":"v"}`（Jackson 产出，无空格）⇒ 若 CAST 成 jsonb 再落进 text 列 ⇒ 存的是 **`{"k": "v"}`**（**冒号后有空格**）
⇒ **落库字节与 Go 不同** ⇒ 这条比 22P05 更隐蔽：**语法不报错、测试也可能照样绿**。

---

## 三、改了什么

### ① `service/users/admin/admin-rpc`

| # | 动作 |
|---|---|
| ① | `AdminDomainService.updateUser`：`extra_info = CAST({0} AS jsonb)` ⇒ **`extra_info = {0}`**（与 `updateSelf` 同款） |
| ② | `admin-rpc/domain/UserEntity.java:129`：`JsonbTypeHandler` ⇒ **`JsonTextTypeHandler`** |
| ③ | **删除** `admin-rpc/mapper/JsonbTypeHandler.java` |
| ④ | 订正 5 处失效前提的注释 + 5 处 `22P02` → `22P05` |

⚠️ **为什么 ② 安全**：admin-rpc 写 `users_user` 的**每一条**语句都是
`userMapper.update(null, wrapper)`（实体为 **null**）⇒ 实体的 typeHandler **在写路径上从未被用到**
⇒ 真正的分歧**只有**那句 CAST。② 是「让实体与真实列型一致」的收尾。

⚠️ **为什么 ③ 能删**：`JsonbTypeHandler` 的**全部存在理由**是
「jsonb 列拒收 varchar 绑参」⇒ 该列变 text 后**这个理由连同它描述的报错都不复存在**
⇒ 与 user-rpc 2026-10-23 的改名（`JsonbTypeHandler` ⇒ `JsonTextTypeHandler`）**同型**
⇒ 类名继续叫 `Jsonb…` 就是在对下一个读代码的人撒谎。

### ② `service/users/user/user-rpc`（**仅注释**）

`UserAvatarHistoryMapper` 里一句「PG 会拒绝 varchar → jsonb」⇒ 对 text 列**已不成立**
⇒ 只改注释，**不动行为**。

### ③ `service/users/users-common`

`03_create_admin.sql` 注释里的 `22P02` ⇒ `22P05`。

---

## 四、🔴 守门：真 PG 16 + 真业务链

新增 2 条（`AdminRpcColumnTypeParityTest`）：

| 用例 | 断言 |
|---|---|
| `userExtraInfoColumnIsTextAndAcceptsJsonbHostilePayload` | ① `users_user.extra_info` 的**库内真实列型** = `text`（查 `information_schema`，不是查脚本文本）② **自证**：这个 payload 对 jsonb **确实敌意**（PG 报 `unsupported Unicode escape sequence`）③ `updateUser` 收下含 `\u0000` 的 payload ⇒ 行还在、库里存着 6 个 ASCII 字符的 `\u0000` |
| `userExtraInfoLandsVerbatimNotJsonbNormalized` | 落库**逐字节** = `{"k":"v"}`（若变成 `{"k": "v"}` ⇒ jsonb 的规范化还在链路上） |

⚠️ **为什么不碰「键序」那条轴**：Go 的 `json.Marshal` 按字典序排键、Jackson 保持插入序
⇒ 那是**既有**差异，user-rpc 的 `JsonTextTypeHandler` 类注释已写明**不改** ⇒ 本轮不碰。

---

## 五、变异（三条全部转红，且**红在目标断言上**）

| # | 变异 | 结果 |
|---|---|---|
| **M1** | 把 `updateUser` 的 CAST 加回去 | 🔴 **RED** —— 两条新用例各红在自己的目标断言上 |
| **M2** | DDL 把 `users_user.extra_info` 改回 jsonb（`:41` + `:103` 同时改） | 🔴 **RED** —— 红在列类型断言：`expected: <text> but was: <jsonb>` |
| **M3** | 把 `updateSelf` 的 CAST 也加回去（**旧守门对照**） | 🔴 **RED** —— 红在**旧**用例 `extraInfoAcceptsPayloadJsonbWouldReject` ⇒ 证明旧守门仍有效、且本轮**没顺手动** `updateSelf` |

### 🔴 M2 首跑是 **GREEN 假阴性** —— 变异脚本的**第三个**坑

| 事实 | 后果 |
|---|---|
| `01_create_user.sql` 是 **`users-common` 的资源** | 只跑 `admin-rpc` 时，Maven 从 `~/.m2` 解析**旧的** `users-common` jar |
| ⇒ 改 `users-common` 的源文件**到不了运行代码** | ⇒ 变异「没效果」⇒ 报成 **GREEN**（假阴性） |
| ⇒ 修法 | 把 `users-common` **拉进同一个 reactor**：`mvn -o test -pl service/users/users-common,service/users/admin/admin-rpc` |

顺带发现 **surefire 参数换代**：`-DfailIfNoTests=false` **管不住** `-Dtest` 模式
⇒ 上游模块会因「没匹配到测试」直接 **BUILD FAILURE**
⇒ 要加 **`-Dsurefire.failIfNoSpecifiedTests=false`**。

⇒ 同族：`docs/Pitfalls/global/cross-domain.md` §PIT-GLOBAL-016。

---

## 六、Go 出处汇总

| 事实 | 出处 |
|---|---|
| `ExtraInfo map[string]string` + `gorm:"column:extra_info;serializer:json"` | `Sea-RideTheWind_golang/service/user/admin/rpc/internal/model/data_model.go:43`（`admin_admin` 在 `:11`） |
| `serializer:json` ⇒ `field.DataType = String` ⇒ 列按**字符串**建 | `gorm.io/gorm@v1.31.1/schema/field.go`（`SERIALIZER` tag 分支） |
| `toUpdate.ExtraInfo = in.ExtraInfo` ⇒ **整列覆盖** | `service/user/admin/rpc/internal/logic/updateuserlogic.go:84-86` |
| Go 侧 admin-rpc **也管** `users_user`（`AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`） | `service/user/admin/rpc/internal/model/db.go:11-18` |

---

## 七、验证清单

- [x] 独立 PG 16 探针 ⇒ `text` 收下 / `CAST jsonb` 被拒（**22P05**）
- [x] `mvn -o test -pl service/users/users-common,service/users/admin/admin-rpc,service/users/admin/admin-api,service/users/user/user-rpc` ⇒ **390 条全绿**
- [x] 全仓 `mvn -o test -fae` ⇒ 全绿
- [x] 变异 M1 / M2 / M3 ⇒ 三条全红，**红在目标断言上**
- [x] 全仓**无残留引用**被删掉的 `JsonbTypeHandler`（只剩注释里的历史说明）
- [x] favorite 域零触碰；共享索引零改动

---

## 八、⛔ 交该域 owner 的账（**不在 global 写权内**）

| 位置 | 内容 |
|---|---|
| `docs/DRIFT/users/admin/rpc.md:483` | 写「PG jsonb 拒收（22P02 / unsupported…」⇒ **SQLSTATE 错**，应为 22P05 |
| `docs/DRIFT/users/admin/rpc.md:732` | 写「jsonb 失败是 22P02」⇒ 同上 |
| `docs/Pitfalls/users/admin/api.md:386` | 写「INTERNAL: DB更新失败（22P02）」⇒ 同上 |

⇒ §5.3b：global **只写** `docs/DRIFT/global/` + `docs/Pitfalls/global/` + `docs/Intentional_Deviations/global/`
⇒ 这三处**记账 + 交 owner**，**不静默**。
⇒ 另：users 域台账 `docs/DRIFT/users/admin/api.md:1660` 只列了「仍开放」、
**归因缺失**（没落到「DDL 归属」这条因果链上）⇒ 本轮已把归因写进 global 档。

---

## 九、📌 待维护者合并到**共享索引**（global 不写，§5.3b）

| 位置 | 要加什么 |
|---|---|
| `AGENTS.md` §0 域表 `global` 行 | X-5 ⇒ 「✅ 第十七轮已落地」；B10 ⇒ 「✅ 第十八轮已落地」 |
| `docs/DRIFT/_INDEX.md` | global 域的台账变更记录指针 |
