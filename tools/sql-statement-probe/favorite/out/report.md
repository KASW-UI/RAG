# SQL 语句层 · 双侧活体对拍报告（favorite 域）

> 取证：**PG 服务端日志**（`log_statement=all`）⇒ 两侧**零改动**，看到的是真发出去的 SQL。
> 分流：`db` + `application_name`（Go 侧 `PGAPPNAME=go_side` / RAG 侧 JDBC `ApplicationName=rag_side`）。
> 分段：驱动每例前发 `SQLMARK::<side>::<case>::<seq>` ⇒ 逐用例切片。

## 0. 采集统计

| 项 | 值 |
|---|---|
| 日志行数 | 1837 |
| 日志记录数 | 855 |
| 语句记录数 | 594 |
| DETAIL 记录数（扩展协议参数） | 246 |
| marker 语句数 | 46 |
| parse/bind 记录数（PG16 实测为 0） | 0 |
| go 侧语句数（db=godb, app=go_side） | 164 |
| rag 侧语句数（db=first_db, app=rag_side） | 302 |

## 1. 两侧业务出口（必须一致，否则 SQL 层不可比）

| 用例 | Go | RAG |
|---|---|---|
| `r01_list_folders_ok` | OK | OK |
| `r02_list_folders_empty` | OK | OK |
| `r03_list_items_ok` | OK | OK |
| `r04_list_items_notfound` | NOT_FOUND:收藏夹不存在 | NOT_FOUND:收藏夹不存在 |
| `r05_list_items_forbidden` | PERMISSION_DENIED:无权限操作 | PERMISSION_DENIED:无权限操作 |
| `r06_delete_item_notfound` | NOT_FOUND:收藏记录不存在 | NOT_FOUND:收藏记录不存在 |
| `r07_delete_item_forbidden` | PERMISSION_DENIED:无权限操作 | PERMISSION_DENIED:无权限操作 |
| `r08_delete_folder_notfound` | NOT_FOUND:收藏夹不存在 | NOT_FOUND:收藏夹不存在 |
| `r09_delete_folder_forbidden` | PERMISSION_DENIED:无权限操作 | PERMISSION_DENIED:无权限操作 |
| `w01_create_folder_ok` | OK | OK |
| `w02_create_folder_duplicate` | ALREADY_EXISTS:收藏夹名称已存在 | ALREADY_EXISTS:收藏夹名称已存在 |
| `w03_create_item_ok` | OK | OK |
| `w04_create_item_2nd_ok` | OK | OK |
| `w05_create_item_duplicate` | ALREADY_EXISTS:该内容已收藏 | ALREADY_EXISTS:该内容已收藏 |
| `w06_update_folder_ok` | OK | OK |
| `w07_delete_created_item_ok` | OK | OK |
| `w08_delete_seed_item_ok` | FAILED_PRECONDITION:收藏暂不可撤回 | FAILED_PRECONDITION:收藏暂不可撤回 |
| `w09_delete_folder_empty_ok` | OK | OK |
| `w10_delete_folder_cascade_ok` | FAILED_PRECONDITION:收藏暂不可撤回 | FAILED_PRECONDITION:收藏暂不可撤回 |
| `z01_list_folders_created` | OK | OK |
| `z02_list_items_created` | OK | OK |
| `z03_list_folders_user1001_after_rename` | OK | OK |
| `w11_delete_created_folder_ok` | OK | OK |

## 1b. L1 响应形状（**屏蔽雪花 ID**；时间戳按「**有值 / 无值**」分类 ⇒ 跨进程可比）

> 🔴 **为什么时间戳要分类**（第二十三轮改）：两侧是**两个独立进程** ⇒ 同一时刻的毫秒值
> **必然不同** ⇒ 精确相等**永远不成立** ⇒ 这个信号**恒红** ⇒ 判别不了「修没修」
> ⇒ 真正有语义差别的是「**有没有值**」：Go 侧 GORM 的 autoCreateTime 给真实毫秒；
> RAG 侧若漏绑 ⇒ NULL ⇒ `unixMillis(null)` ⇒ **0**。
> ⇒ 规则：`c=` / `u=` 后是 **≥2 位的数字** ⇒ `<ts>`；是 `0` ⇒ 保持 `0`（= 没有值）。

| 用例 | Go | RAG | 一致 |
|---|---|---|---|
| `z01_list_folders_created` | n=1 [SqlProbeFolder|c=1793010248183|u=1793010248183] | n=1 [SqlProbeFolder|c=1793010248211|u=1793010248211] | ✅ |
| `z02_list_items_created` | n=1 [t-78|post|req-title-2|c=1793010248292] | n=1 [t-78|post|req-title-2|c=1793010248308] | ✅ |
| `z03_list_folders_user1001_after_rename` | n=1 [Alpha2|c=1767294245678|u=1793010248352] | n=1 [Alpha2|c=1767294245678|u=1793010248367] | ✅ |

⇒ **L1 响应不同：0 例**（这是「SQL 层差异」对外可观察的直接证据）

## 2. 逐用例语句序列

| 用例 | Go 条数 | RAG 条数 | Go 序列 | RAG 序列 | 差异 |
|---|---|---|---|---|---|
| `r01_list_folders_ok` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r02_list_folders_empty` | 1 | 1 | SELECT | SELECT | ✅ 一致 |
| `r03_list_items_ok` | 2 | 2 | SELECT → SELECT | SELECT → SELECT | 🟡 1 处 ORM 噪声 |
| `r04_list_items_notfound` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r05_list_items_forbidden` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r06_delete_item_notfound` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r07_delete_item_forbidden` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r08_delete_folder_notfound` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `r09_delete_folder_forbidden` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `w01_create_folder_ok` | 2 | 2 | SELECT → INSERT | SELECT → INSERT | 🟡 3 处 ORM 噪声 |
| `w02_create_folder_duplicate` | 1 | 1 | SELECT | SELECT | 🟡 1 处 ORM 噪声 |
| `w03_create_item_ok` | 7 | 7 | SELECT → SELECT → BEGIN → SELECT → INSERT → INSERT → COMMIT | SELECT → SELECT → BEGIN → SELECT → INSERT → INSERT → COMMIT | 🟡 5 处 ORM 噪声 |
| `w04_create_item_2nd_ok` | 7 | 7 | SELECT → SELECT → BEGIN → SELECT → INSERT → INSERT → COMMIT | SELECT → SELECT → BEGIN → SELECT → INSERT → INSERT → COMMIT | 🟡 5 处 ORM 噪声 |
| `w05_create_item_duplicate` | 2 | 2 | SELECT → SELECT | SELECT → SELECT | 🟡 2 处 ORM 噪声 |
| `w06_update_folder_ok` | 3 | 3 | SELECT → SELECT → UPDATE | SELECT → SELECT → UPDATE | 🟡 3 处 ORM 噪声 |
| `w07_delete_created_item_ok` | 7 | 7 | SELECT → BEGIN → SELECT → SELECT → INSERT → DELETE → COMMIT | SELECT → BEGIN → SELECT → SELECT → INSERT → DELETE → COMMIT | 🟡 3 处 ORM 噪声 |
| `w08_delete_seed_item_ok` | 6 | 6 | SELECT → BEGIN → SELECT → SELECT → SELECT → ROLLBACK | SELECT → BEGIN → SELECT → SELECT → SELECT → ROLLBACK | 🟡 3 处 ORM 噪声 |
| `w09_delete_folder_empty_ok` | 7 | 7 | SELECT → BEGIN → SELECT → SELECT → DELETE → DELETE → COMMIT | SELECT → BEGIN → SELECT → SELECT → DELETE → DELETE → COMMIT | 🟡 3 处 ORM 噪声 |
| `w10_delete_folder_cascade_ok` | 7 | 7 | SELECT → BEGIN → SELECT → SELECT → SELECT → SELECT → ROLLBACK | SELECT → BEGIN → SELECT → SELECT → SELECT → SELECT → ROLLBACK | 🟡 3 处 ORM 噪声 |
| `w11_delete_created_folder_ok` | 9 | 9 | SELECT → BEGIN → SELECT → SELECT → SELECT → INSERT → DELETE → DELETE → COMMIT | SELECT → BEGIN → SELECT → SELECT → SELECT → INSERT → DELETE → DELETE → COMMIT | 🟡 3 处 ORM 噪声 |
| `z01_list_folders_created` | 1 | 1 | SELECT | SELECT | ✅ 一致 |
| `z02_list_items_created` | 2 | 2 | SELECT → SELECT | SELECT → SELECT | 🟡 1 处 ORM 噪声 |
| `z03_list_folders_user1001_after_rename` | 1 | 1 | SELECT | SELECT | ✅ 一致 |

## 3. 差异明细

### `r01_list_folders_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-- ping` | `-` | ORM-GENERIC | 已剥离的GO噪声：ORM 注释语句（GORM 的连接 ping） |

### `r03_list_items_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r04_list_items_notfound`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r05_list_items_forbidden`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r06_delete_item_notfound`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r07_delete_item_forbidden`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r08_delete_folder_notfound`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `r09_delete_folder_forbidden`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `w01_create_folder_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `BEGIN … COMMIT` | `-` | ORM-GENERIC | 已剥离的GO噪声：GORM 默认事务：BEGIN/COMMIT 只包了**一条**语句 ⇒ 与自动提交**原子性等价** |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #1 returning | `id` | `-` | ORM-GENERIC | RETURNING 子句不同（自增代理键，域内不使用） |

### `w02_create_folder_duplicate`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `w03_create_item_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #1 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #3 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #4 returning | `id` | `-` | ORM-GENERIC | RETURNING 子句不同（自增代理键，域内不使用） |
| #5 write_cols | `aggregate_version,created_at,delivered_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_offset,technical_receipt_id,technical_received_at,updated_at` | `aggregate_version,created_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_receipt_id,updated_at` | EQUIVALENT | 写入列集不同（仅 Go: delivered_at,technical_offset,technical_received_at ｜ 仅 RAG: 无）⇒ **落库值逐列相同**（technical_offset=0；technical_received_at=<NULL>；delivered_at=<NULL>）⇒ 等价 |

### `w04_create_item_2nd_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #1 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #3 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #4 returning | `id` | `-` | ORM-GENERIC | RETURNING 子句不同（自增代理键，域内不使用） |
| #5 write_cols | `aggregate_version,created_at,delivered_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_offset,technical_receipt_id,technical_received_at,updated_at` | `aggregate_version,created_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_receipt_id,updated_at` | EQUIVALENT | 写入列集不同（仅 Go: delivered_at,technical_offset,technical_received_at ｜ 仅 RAG: 无）⇒ **落库值逐列相同**（technical_offset=0；technical_received_at=<NULL>；delivered_at=<NULL>）⇒ 等价 |

### `w05_create_item_duplicate`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #1 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `w06_update_folder_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `BEGIN … COMMIT` | `-` | ORM-GENERIC | 已剥离的GO噪声：GORM 默认事务：BEGIN/COMMIT 只包了**一条**语句 ⇒ 与自动提交**原子性等价** |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #1 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `w07_delete_created_item_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #2 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #4 write_cols | `aggregate_version,created_at,delivered_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_offset,technical_receipt_id,technical_received_at,updated_at` | `aggregate_version,created_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_receipt_id,updated_at` | EQUIVALENT | 写入列集不同（仅 Go: delivered_at,technical_offset,technical_received_at ｜ 仅 RAG: 无）⇒ **落库值逐列相同**（technical_offset=0；technical_received_at=<NULL>；delivered_at=<NULL>）⇒ 等价 |

### `w08_delete_seed_item_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #2 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #4 projection | `favorite_id,user_id,folder_id,target_type,target_id,target_revision,source_snapshot_sha256,approval_ref,approved_at` | `favorite_id,user_id,folder_id,target_type,target_id,target_revision,source_snapshot_sha256,approval_ref` | EQUIVALENT | 投影列集不同（仅 Go: approved_at ｜ 仅 RAG: 无）⇒ **已人工判定等价**：approved_at ⇒ Go 的 struct 只有 8 个字段、无 approved_at ⇒ Go 的 SELECT * 取回后**在 struct 映射层就丢弃** ⇒ 判定条件从不引用它 ⇒ 行为等价（Go 出处 service/favorite/rpc/internal/model/favorite_fact.go:28-37） |

### `w09_delete_folder_empty_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #2 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

### `w10_delete_folder_cascade_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #2 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #5 projection | `favorite_id,user_id,folder_id,target_type,target_id,target_revision,source_snapshot_sha256,approval_ref,approved_at` | `favorite_id,user_id,folder_id,target_type,target_id,target_revision,source_snapshot_sha256,approval_ref` | EQUIVALENT | 投影列集不同（仅 Go: approved_at ｜ 仅 RAG: 无）⇒ **已人工判定等价**：approved_at ⇒ Go 的 struct 只有 8 个字段、无 approved_at ⇒ Go 的 SELECT * 取回后**在 struct 映射层就丢弃** ⇒ 判定条件从不引用它 ⇒ 行为等价（Go 出处 service/favorite/rpc/internal/model/favorite_fact.go:28-37） |

### `w11_delete_created_folder_ok`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| noise | `-` | `SHOW TRANSACTION ISOLATION LEVEL` | ORM-GENERIC | 已剥离的RAG噪声：会话查询 |
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #2 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |
| #5 write_cols | `aggregate_version,created_at,delivered_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_offset,technical_receipt_id,technical_received_at,updated_at` | `aggregate_version,created_at,event_id,favorite_id,payload,retry_count,status,technical_input_hash,technical_receipt_id,updated_at` | EQUIVALENT | 写入列集不同（仅 Go: delivered_at,technical_offset,technical_received_at ｜ 仅 RAG: 无）⇒ **落库值逐列相同**（technical_offset=0；technical_received_at=<NULL>；delivered_at=<NULL>）⇒ 等价 |

### `z02_list_items_created`

| 维度 | Go | RAG | 分档 | 说明 |
|---|---|---|---|---|
| #0 order_by | `id` | `-` | ORM-GENERIC | GORM 的 First 隐式加 ORDER BY <主键>；本语句的 WHERE 已钉住唯一键 ⇒ **等价** |

## 4. 结论

- **语义差异（SEMANTIC）：0 处**
- **落库值不可判定（UNDECIDABLE）：0 处**
- **ORM 生成噪声 / 已证明等价（ORM-GENERIC / EQUIVALENT）：49 处**
- **L1 响应不同：0 例**

✅ 两侧在**语句层**的语义签名一致（锁 / 事务边界 / WHERE / 写集合 / 投影 / 排序）。

## 附录 A. 原始 SQL（逐用例）

> ⚠️ 绑定参数（`params`）是**扩展协议 DETAIL 行**里的真实值 ⇒ 人工核证时一并看。
> 两侧的**雪花 ID 必然不同** ⇒ 参数值里的 ID 属正常，不是差异。

### `r01_list_folders_ok`

**GO 侧**

```sql
-- [0]
-- ping
-- [1]  -- params: {'1': '1001'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '1001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1
	ORDER BY create_time DESC
```

### `r02_list_folders_empty`

**GO 侧**

```sql
-- [0]  -- params: {'1': '4242'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '4242'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1
	ORDER BY create_time DESC
```

### `r03_list_items_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9001', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '9001'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '9001'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1
	ORDER BY create_time DESC
```

### `r04_list_items_notfound`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9999', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9999'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
```

### `r05_list_items_forbidden`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9001', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
```

### `r06_delete_item_notfound`

**GO 侧**

```sql
-- [0]  -- params: {'1': '999999', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '999999'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
```

### `r07_delete_item_forbidden`

**GO 侧**

```sql
-- [0]  -- params: {'1': '7001', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '7001'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
```

### `r08_delete_folder_notfound`

**GO 侧**

```sql
-- [0]  -- params: {'1': '999999', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '999999'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
```

### `r09_delete_folder_forbidden`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9001', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
```

### `w01_create_folder_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2001', '2': 'SqlProbeFolder', '3': '1'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 AND name = $2 ORDER BY "favorite_folder"."id" LIMIT $3
-- [1]
begin
-- [2]  -- params: {'1': '2114664366449553408', '2': '2001', '3': 'SqlProbeFolder', '4': '2026-10-26 18:24:08.183291+08', '5': '2026-10-26 18:24:08.183291+08'}
INSERT INTO "favorite_folder" ("folder_id","user_id","name","create_time","update_time") VALUES ($1,$2,$3,$4,$5) RETURNING "id"
-- [3]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2001', '2': 'SqlProbeFolder'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1 AND name = $2
	LIMIT 1
-- [1]  -- params: {'1': '2114664366570917888', '2': '2001', '3': 'SqlProbeFolder', '4': '2026-10-26 18:24:08.21149+08', '5': '2026-10-26 18:24:08.21149+08'}
INSERT INTO favorite_folder (folder_id, user_id, name, create_time, update_time)
	VALUES ($1, $2, $3, $4, $5)
```

### `w02_create_folder_duplicate`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2001', '2': 'SqlProbeFolder', '3': '1'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 AND name = $2 ORDER BY "favorite_folder"."id" LIMIT $3
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2001', '2': 'SqlProbeFolder'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1 AND name = $2
	LIMIT 1
```

### `w03_create_item_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '2114664366449553408', '2': 't-77', '3': 'post', '4': '1'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 AND target_id = $2 AND target_type = $3 ORDER BY "favorite_item"."id" LIMIT $4
-- [2]
begin
-- [3]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2 FOR UPDATE
-- [4]  -- params: {'1': '2114664366722183168', '2': '2114664366449553408', '3': '2001', '4': 't-77', '5': 'post', '6': None, '7': 'req-title', '8': 'req-cover', '9': '2026-10-26 18:24:08.249209+08'}
INSERT INTO "favorite_item" ("favorite_id","folder_id","user_id","target_id","target_type","target_revision","title","cover","create_time") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING "id"
-- [5]  -- params: {'1': 'favorite.2114664366722183168.v1', '2': '2114664366722183168', '3': '1', '4': '{"payload": {"event_id": "favorite.2114664366722183168.v1", "folder_id": "2114664366449553408", "operation": "assert", "target_id": "t-77", "event_time": "2026-10-26T10:24:08.250264Z", "source_ref": "rtw.favorite/2114664366722183168", "favorite_id": "2114664366722183168", "subject_ref": {"tenant_id": "platform", "subject_id": "2001", "authority_id": "rtw.identity"}, "target_type": "post", "available_at": "2026-10-26T10:24:08.250264Z", "schema_version": 1, "target_revision": null}, "event_id": "favorite.2114664366722183168.v1", "producer": "rtw.community.favorite", "event_type": "rtw.favorite.assert", "occurred_at": "2026-10-26T10:24:08.250264Z", "aggregate_id": "2114664366722183168", "operation_id": "favorite.2114664366722183168.v1", "schema_version": 1, "aggregate_version": 1}', '5': '0', '6': '0', '7': '', '8': '', '9': '0', '10': None, '11': None, '12': '2026-10-26 18:24:08.250312+08', '13': '2026-10-26 18:24:08.250312+08'}
INSERT INTO "favorite_fact_outbox" ("event_id","favorite_id","aggregate_version","payload","status","retry_count","technical_receipt_id","technical_input_hash","technical_offset","technical_received_at","delivered_at","created_at","updated_at") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
-- [6]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '2114664366570917888', '2': 't-77', '3': 'post'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1 AND target_id = $2 AND target_type = $3
	LIMIT 1
-- [2]
SHOW TRANSACTION ISOLATION LEVEL
-- [3]
BEGIN
-- [4]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
	FOR UPDATE
-- [5]  -- params: {'1': '2114664366805798912', '2': '2114664366570917888', '3': '2001', '4': 't-77', '5': 'post', '6': None, '7': 'req-title', '8': 'req-cover', '9': '2026-10-26 18:24:08.274192+08'}
INSERT INTO favorite_item
	    (favorite_id, folder_id, user_id, target_id, target_type, target_revision, title, cover,
	     create_time)
	VALUES ($1, $2, $3, $4, $5,
	        $6, $7, $8,
	        $9)
-- [6]  -- params: {'1': 'favorite.2114664366805798912.v1', '2': '2114664366805798912', '3': '1', '4': '{"event_id":"favorite.2114664366805798912.v1","event_type":"rtw.favorite.assert","schema_version":1,"producer":"rtw.community.favorite","aggregate_id":"2114664366805798912","aggregate_version":1,"operation_id":"favorite.2114664366805798912.v1","occurred_at":"2026-10-26T10:24:08.275361Z","payload":{"schema_version":1,"event_id":"favorite.2114664366805798912.v1","subject_ref":{"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"2001"},"target_type":"post","target_id":"t-77","target_revision":null,"operation":"assert","source_ref":"rtw.favorite/2114664366805798912","event_time":"2026-10-26T10:24:08.275361Z","available_at":"2026-10-26T10:24:08.275361Z","favorite_id":"2114664366805798912","folder_id":"2114664366570917888"}}', '5': '2026-10-26 18:24:08.277582+08', '6': '2026-10-26 18:24:08.277582+08'}
INSERT INTO favorite_fact_outbox
	    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
	     technical_receipt_id, technical_input_hash,
	     created_at, updated_at)
	VALUES ($1, $2, $3, $4::jsonb, 0, 0,
	        '', '',
	        $5, $6)
-- [7]
COMMIT
```

### `w04_create_item_2nd_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '2114664366449553408', '2': 't-78', '3': 'post', '4': '1'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 AND target_id = $2 AND target_type = $3 ORDER BY "favorite_item"."id" LIMIT $4
-- [2]
begin
-- [3]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2 FOR UPDATE
-- [4]  -- params: {'1': '2114664366902538240', '2': '2114664366449553408', '3': '2001', '4': 't-78', '5': 'post', '6': None, '7': 'req-title-2', '8': 'req-cover-2', '9': '2026-10-26 18:24:08.29217+08'}
INSERT INTO "favorite_item" ("favorite_id","folder_id","user_id","target_id","target_type","target_revision","title","cover","create_time") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING "id"
-- [5]  -- params: {'1': 'favorite.2114664366902538240.v1', '2': '2114664366902538240', '3': '1', '4': '{"payload": {"event_id": "favorite.2114664366902538240.v1", "folder_id": "2114664366449553408", "operation": "assert", "target_id": "t-78", "event_time": "2026-10-26T10:24:08.292597Z", "source_ref": "rtw.favorite/2114664366902538240", "favorite_id": "2114664366902538240", "subject_ref": {"tenant_id": "platform", "subject_id": "2001", "authority_id": "rtw.identity"}, "target_type": "post", "available_at": "2026-10-26T10:24:08.292597Z", "schema_version": 1, "target_revision": null}, "event_id": "favorite.2114664366902538240.v1", "producer": "rtw.community.favorite", "event_type": "rtw.favorite.assert", "occurred_at": "2026-10-26T10:24:08.292597Z", "aggregate_id": "2114664366902538240", "operation_id": "favorite.2114664366902538240.v1", "schema_version": 1, "aggregate_version": 1}', '5': '0', '6': '0', '7': '', '8': '', '9': '0', '10': None, '11': None, '12': '2026-10-26 18:24:08.292621+08', '13': '2026-10-26 18:24:08.292621+08'}
INSERT INTO "favorite_fact_outbox" ("event_id","favorite_id","aggregate_version","payload","status","retry_count","technical_receipt_id","technical_input_hash","technical_offset","technical_received_at","delivered_at","created_at","updated_at") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
-- [6]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '2114664366570917888', '2': 't-78', '3': 'post'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1 AND target_id = $2 AND target_type = $3
	LIMIT 1
-- [2]
SHOW TRANSACTION ISOLATION LEVEL
-- [3]
BEGIN
-- [4]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
	FOR UPDATE
-- [5]  -- params: {'1': '2114664366969376768', '2': '2114664366570917888', '3': '2001', '4': 't-78', '5': 'post', '6': None, '7': 'req-title-2', '8': 'req-cover-2', '9': '2026-10-26 18:24:08.308578+08'}
INSERT INTO favorite_item
	    (favorite_id, folder_id, user_id, target_id, target_type, target_revision, title, cover,
	     create_time)
	VALUES ($1, $2, $3, $4, $5,
	        $6, $7, $8,
	        $9)
-- [6]  -- params: {'1': 'favorite.2114664366969376768.v1', '2': '2114664366969376768', '3': '1', '4': '{"event_id":"favorite.2114664366969376768.v1","event_type":"rtw.favorite.assert","schema_version":1,"producer":"rtw.community.favorite","aggregate_id":"2114664366969376768","aggregate_version":1,"operation_id":"favorite.2114664366969376768.v1","occurred_at":"2026-10-26T10:24:08.309435Z","payload":{"schema_version":1,"event_id":"favorite.2114664366969376768.v1","subject_ref":{"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"2001"},"target_type":"post","target_id":"t-78","target_revision":null,"operation":"assert","source_ref":"rtw.favorite/2114664366969376768","event_time":"2026-10-26T10:24:08.309435Z","available_at":"2026-10-26T10:24:08.309435Z","favorite_id":"2114664366969376768","folder_id":"2114664366570917888"}}', '5': '2026-10-26 18:24:08.309643+08', '6': '2026-10-26 18:24:08.309643+08'}
INSERT INTO favorite_fact_outbox
	    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
	     technical_receipt_id, technical_input_hash,
	     created_at, updated_at)
	VALUES ($1, $2, $3, $4::jsonb, 0, 0,
	        '', '',
	        $5, $6)
-- [7]
COMMIT
```

### `w05_create_item_duplicate`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '2114664366449553408', '2': 't-77', '3': 'post', '4': '1'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 AND target_id = $2 AND target_type = $3 ORDER BY "favorite_item"."id" LIMIT $4
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '2114664366570917888', '2': 't-77', '3': 'post'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1 AND target_id = $2 AND target_type = $3
	LIMIT 1
```

### `w06_update_folder_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9001', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '1001', '2': 'Alpha2', '3': '1'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 AND name = $2 ORDER BY "favorite_folder"."id" LIMIT $3
-- [2]
begin
-- [3]  -- params: {'1': 'Alpha2', '2': '2026-10-26 18:24:08.352408+08', '3': '9001'}
UPDATE "favorite_folder" SET "name"=$1,"update_time"=$2 WHERE folder_id = $3
-- [4]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '1001', '2': 'Alpha2'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1 AND name = $2
	LIMIT 1
-- [2]  -- params: {'1': 'Alpha2', '2': '2026-10-26 18:24:08.367237+08', '3': '9001'}
UPDATE favorite_folder
	SET name = $1, update_time = $2
	WHERE folder_id = $3
```

### `w07_delete_created_item_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366722183168', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2
-- [1]
begin
-- [2]  -- params: {'1': '2114664366722183168', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2 FOR UPDATE
-- [3]  -- params: {'1': '2114664366722183168', '2': '1'}
SELECT * FROM "favorite_fact_outbox" WHERE favorite_id = $1 AND aggregate_version = 1 LIMIT $2 FOR UPDATE
-- [4]  -- params: {'1': 'favorite.2114664366722183168.v2', '2': '2114664366722183168', '3': '2', '4': '{"payload": {"event_id": "favorite.2114664366722183168.v2", "folder_id": "2114664366449553408", "operation": "retract", "target_id": "t-77", "event_time": "2026-10-26T10:24:08.383019Z", "source_ref": "rtw.favorite/2114664366722183168", "favorite_id": "2114664366722183168", "subject_ref": {"tenant_id": "platform", "subject_id": "2001", "authority_id": "rtw.identity"}, "target_type": "post", "available_at": "2026-10-26T10:24:08.383019Z", "schema_version": 1, "target_revision": null}, "event_id": "favorite.2114664366722183168.v2", "producer": "rtw.community.favorite", "event_type": "rtw.favorite.retract", "occurred_at": "2026-10-26T10:24:08.383019Z", "aggregate_id": "2114664366722183168", "operation_id": "favorite.2114664366722183168.v2", "schema_version": 1, "aggregate_version": 2}', '5': '0', '6': '0', '7': '', '8': '', '9': '0', '10': None, '11': None, '12': '2026-10-26 18:24:08.383033+08', '13': '2026-10-26 18:24:08.383033+08'}
INSERT INTO "favorite_fact_outbox" ("event_id","favorite_id","aggregate_version","payload","status","retry_count","technical_receipt_id","technical_input_hash","technical_offset","technical_received_at","delivered_at","created_at","updated_at") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
-- [5]  -- params: {'1': '2114664366722183168'}
DELETE FROM "favorite_item" WHERE favorite_id = $1
-- [6]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366805798912'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
-- [1]
SHOW TRANSACTION ISOLATION LEVEL
-- [2]
BEGIN
-- [3]  -- params: {'1': '2114664366805798912'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
	FOR UPDATE
-- [4]  -- params: {'1': '2114664366805798912'}
SELECT event_id, favorite_id, aggregate_version, payload, status, retry_count,
	       technical_receipt_id, technical_input_hash, technical_offset,
	       technical_received_at, delivered_at, created_at, updated_at
	FROM favorite_fact_outbox
	WHERE favorite_id = $1 AND aggregate_version = 1
	LIMIT 1
	FOR UPDATE
-- [5]  -- params: {'1': 'favorite.2114664366805798912.v2', '2': '2114664366805798912', '3': '2', '4': '{"event_id":"favorite.2114664366805798912.v2","event_type":"rtw.favorite.retract","schema_version":1,"producer":"rtw.community.favorite","aggregate_id":"2114664366805798912","aggregate_version":2,"operation_id":"favorite.2114664366805798912.v2","occurred_at":"2026-10-26T10:24:08.41054Z","payload":{"schema_version":1,"event_id":"favorite.2114664366805798912.v2","subject_ref":{"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"2001"},"target_type":"post","target_id":"t-77","target_revision":null,"operation":"retract","source_ref":"rtw.favorite/2114664366805798912","event_time":"2026-10-26T10:24:08.41054Z","available_at":"2026-10-26T10:24:08.41054Z","favorite_id":"2114664366805798912","folder_id":"2114664366570917888"}}', '5': '2026-10-26 18:24:08.410705+08', '6': '2026-10-26 18:24:08.410705+08'}
INSERT INTO favorite_fact_outbox
	    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
	     technical_receipt_id, technical_input_hash,
	     created_at, updated_at)
	VALUES ($1, $2, $3, $4::jsonb, 0, 0,
	        '', '',
	        $5, $6)
-- [6]  -- params: {'1': '2114664366805798912'}
DELETE FROM favorite_item WHERE favorite_id = $1
-- [7]
COMMIT
```

### `w08_delete_seed_item_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '7001', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2
-- [1]
begin
-- [2]  -- params: {'1': '7001', '2': '1'}
SELECT * FROM "favorite_item" WHERE favorite_id = $1 ORDER BY "favorite_item"."id" LIMIT $2 FOR UPDATE
-- [3]  -- params: {'1': '7001', '2': '1'}
SELECT * FROM "favorite_fact_outbox" WHERE favorite_id = $1 AND aggregate_version = 1 LIMIT $2 FOR UPDATE
-- [4]  -- params: {'1': '7001', '2': '1'}
SELECT * FROM "favorite_legacy_fact_marker" WHERE favorite_id = $1 LIMIT $2 FOR UPDATE
-- [5]
rollback
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '7001'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
-- [1]
SHOW TRANSACTION ISOLATION LEVEL
-- [2]
BEGIN
-- [3]  -- params: {'1': '7001'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE favorite_id = $1
	LIMIT 1
	FOR UPDATE
-- [4]  -- params: {'1': '7001'}
SELECT event_id, favorite_id, aggregate_version, payload, status, retry_count,
	       technical_receipt_id, technical_input_hash, technical_offset,
	       technical_received_at, delivered_at, created_at, updated_at
	FROM favorite_fact_outbox
	WHERE favorite_id = $1 AND aggregate_version = 1
	LIMIT 1
	FOR UPDATE
-- [5]  -- params: {'1': '7001'}
SELECT favorite_id, user_id, folder_id, target_type, target_id, target_revision,
	       source_snapshot_sha256, approval_ref
	FROM favorite_legacy_fact_marker
	WHERE favorite_id = $1
	LIMIT 1
	FOR UPDATE
-- [6]
ROLLBACK
```

### `w09_delete_folder_empty_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9002', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]
begin
-- [2]  -- params: {'1': '9002', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2 FOR UPDATE
-- [3]  -- params: {'1': '9002'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 ORDER BY favorite_id FOR UPDATE
-- [4]  -- params: {'1': '9002'}
DELETE FROM "favorite_item" WHERE folder_id = $1
-- [5]  -- params: {'1': '9002'}
DELETE FROM "favorite_folder" WHERE folder_id = $1
-- [6]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9002'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]
SHOW TRANSACTION ISOLATION LEVEL
-- [2]
BEGIN
-- [3]  -- params: {'1': '9002'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
	FOR UPDATE
-- [4]  -- params: {'1': '9002'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1
	ORDER BY favorite_id
	FOR UPDATE
-- [5]  -- params: {'1': '9002'}
DELETE FROM favorite_item WHERE folder_id = $1
-- [6]  -- params: {'1': '9002'}
DELETE FROM favorite_folder WHERE folder_id = $1
-- [7]
COMMIT
```

### `w10_delete_folder_cascade_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '9003', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]
begin
-- [2]  -- params: {'1': '9003', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2 FOR UPDATE
-- [3]  -- params: {'1': '9003'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 ORDER BY favorite_id FOR UPDATE
-- [4]  -- params: {'1': '7003', '2': '1'}
SELECT * FROM "favorite_fact_outbox" WHERE favorite_id = $1 AND aggregate_version = 1 LIMIT $2 FOR UPDATE
-- [5]  -- params: {'1': '7003', '2': '1'}
SELECT * FROM "favorite_legacy_fact_marker" WHERE favorite_id = $1 LIMIT $2 FOR UPDATE
-- [6]
rollback
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '9003'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]
SHOW TRANSACTION ISOLATION LEVEL
-- [2]
BEGIN
-- [3]  -- params: {'1': '9003'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
	FOR UPDATE
-- [4]  -- params: {'1': '9003'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1
	ORDER BY favorite_id
	FOR UPDATE
-- [5]  -- params: {'1': '7003'}
SELECT event_id, favorite_id, aggregate_version, payload, status, retry_count,
	       technical_receipt_id, technical_input_hash, technical_offset,
	       technical_received_at, delivered_at, created_at, updated_at
	FROM favorite_fact_outbox
	WHERE favorite_id = $1 AND aggregate_version = 1
	LIMIT 1
	FOR UPDATE
-- [6]  -- params: {'1': '7003'}
SELECT favorite_id, user_id, folder_id, target_type, target_id, target_revision,
	       source_snapshot_sha256, approval_ref
	FROM favorite_legacy_fact_marker
	WHERE favorite_id = $1
	LIMIT 1
	FOR UPDATE
-- [7]
ROLLBACK
```

### `w11_delete_created_folder_ok`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]
begin
-- [2]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2 FOR UPDATE
-- [3]  -- params: {'1': '2114664366449553408'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 ORDER BY favorite_id FOR UPDATE
-- [4]  -- params: {'1': '2114664366902538240', '2': '1'}
SELECT * FROM "favorite_fact_outbox" WHERE favorite_id = $1 AND aggregate_version = 1 LIMIT $2 FOR UPDATE
-- [5]  -- params: {'1': 'favorite.2114664366902538240.v2', '2': '2114664366902538240', '3': '2', '4': '{"payload": {"event_id": "favorite.2114664366902538240.v2", "folder_id": "2114664366449553408", "operation": "retract", "target_id": "t-78", "event_time": "2026-10-26T10:24:08.618138Z", "source_ref": "rtw.favorite/2114664366902538240", "favorite_id": "2114664366902538240", "subject_ref": {"tenant_id": "platform", "subject_id": "2001", "authority_id": "rtw.identity"}, "target_type": "post", "available_at": "2026-10-26T10:24:08.618138Z", "schema_version": 1, "target_revision": null}, "event_id": "favorite.2114664366902538240.v2", "producer": "rtw.community.favorite", "event_type": "rtw.favorite.retract", "occurred_at": "2026-10-26T10:24:08.618138Z", "aggregate_id": "2114664366902538240", "operation_id": "favorite.2114664366902538240.v2", "schema_version": 1, "aggregate_version": 2}', '5': '0', '6': '0', '7': '', '8': '', '9': '0', '10': None, '11': None, '12': '2026-10-26 18:24:08.618161+08', '13': '2026-10-26 18:24:08.618161+08'}
INSERT INTO "favorite_fact_outbox" ("event_id","favorite_id","aggregate_version","payload","status","retry_count","technical_receipt_id","technical_input_hash","technical_offset","technical_received_at","delivered_at","created_at","updated_at") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
-- [6]  -- params: {'1': '2114664366449553408'}
DELETE FROM "favorite_item" WHERE folder_id = $1
-- [7]  -- params: {'1': '2114664366449553408'}
DELETE FROM "favorite_folder" WHERE folder_id = $1
-- [8]
commit
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]
SHOW TRANSACTION ISOLATION LEVEL
-- [2]
BEGIN
-- [3]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
	FOR UPDATE
-- [4]  -- params: {'1': '2114664366570917888'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1
	ORDER BY favorite_id
	FOR UPDATE
-- [5]  -- params: {'1': '2114664366969376768'}
SELECT event_id, favorite_id, aggregate_version, payload, status, retry_count,
	       technical_receipt_id, technical_input_hash, technical_offset,
	       technical_received_at, delivered_at, created_at, updated_at
	FROM favorite_fact_outbox
	WHERE favorite_id = $1 AND aggregate_version = 1
	LIMIT 1
	FOR UPDATE
-- [6]  -- params: {'1': 'favorite.2114664366969376768.v2', '2': '2114664366969376768', '3': '2', '4': '{"event_id":"favorite.2114664366969376768.v2","event_type":"rtw.favorite.retract","schema_version":1,"producer":"rtw.community.favorite","aggregate_id":"2114664366969376768","aggregate_version":2,"operation_id":"favorite.2114664366969376768.v2","occurred_at":"2026-10-26T10:24:08.638391Z","payload":{"schema_version":1,"event_id":"favorite.2114664366969376768.v2","subject_ref":{"authority_id":"rtw.identity","tenant_id":"platform","subject_id":"2001"},"target_type":"post","target_id":"t-78","target_revision":null,"operation":"retract","source_ref":"rtw.favorite/2114664366969376768","event_time":"2026-10-26T10:24:08.638391Z","available_at":"2026-10-26T10:24:08.638391Z","favorite_id":"2114664366969376768","folder_id":"2114664366570917888"}}', '5': '2026-10-26 18:24:08.638595+08', '6': '2026-10-26 18:24:08.638595+08'}
INSERT INTO favorite_fact_outbox
	    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
	     technical_receipt_id, technical_input_hash,
	     created_at, updated_at)
	VALUES ($1, $2, $3, $4::jsonb, 0, 0,
	        '', '',
	        $5, $6)
-- [7]  -- params: {'1': '2114664366570917888'}
DELETE FROM favorite_item WHERE folder_id = $1
-- [8]  -- params: {'1': '2114664366570917888'}
DELETE FROM favorite_folder WHERE folder_id = $1
-- [9]
COMMIT
```

### `z01_list_folders_created`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2001'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1
	ORDER BY create_time DESC
```

### `z02_list_items_created`

**GO 侧**

```sql
-- [0]  -- params: {'1': '2114664366449553408', '2': '1'}
SELECT * FROM "favorite_folder" WHERE folder_id = $1 ORDER BY "favorite_folder"."id" LIMIT $2
-- [1]  -- params: {'1': '2114664366449553408'}
SELECT * FROM "favorite_item" WHERE folder_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '2114664366570917888'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE folder_id = $1
	LIMIT 1
-- [1]  -- params: {'1': '2114664366570917888'}
SELECT id, favorite_id, folder_id, user_id, target_id, target_type,
	       target_revision, title, cover, create_time
	FROM favorite_item
	WHERE folder_id = $1
	ORDER BY create_time DESC
```

### `z03_list_folders_user1001_after_rename`

**GO 侧**

```sql
-- [0]  -- params: {'1': '1001'}
SELECT * FROM "favorite_folder" WHERE user_id = $1 ORDER BY create_time desc
```

**RAG 侧**

```sql
-- [0]  -- params: {'1': '1001'}
SELECT id, folder_id, user_id, name, create_time, update_time
	FROM favorite_folder
	WHERE user_id = $1
	ORDER BY create_time DESC
```

