# favorite 域「outbox 写入方」盘点机检（V7 上线前置）

把「V7 上线前必须确认没有域外写入方」从**人工盘点**变成**机检**。

```bash
python3 tools/outbox-writer-audit/favorite/check-outbox-writers.py
echo $?   # 0 = 没有域外写入方且所有 INSERT 都绑了两列；1 = 有问题；2 = 工具自身出错
```

## 背景

`favorite_fact_outbox` 的 `created_at` / `updated_at` 两列在 **V7** 里被去掉了
`DEFAULT now()`（对齐 Go 侧：GORM `autoCreateTime` 建的列**没有** DEFAULT ——
见 `V7__align_favorite_outbox_time_columns_with_go.sql` 的注释）。

⇒ **任何直接写这张表、却没绑这两列的代码，会立刻 `NOT NULL` 违例**（硬失败，不静默兜底）。
⇒ 领域内的写入方已全部改绑（生产代码 `insertFact` + 6 处测试裸 INSERT），
   但**域外**的写入方（人工 SQL / 报表 / 另一个服务 / 数据修复脚本 / 别的域的 mapper）
   **从 favorite 域内部看不见** ⇒ 只能靠盘点。

## 判定口径

| 检查 | 判定 | 为什么 |
|---|---|---|
| **归属**：INSERT 站点在 `favorite-rpc` 模块内吗？ | 不在 ⇒ 🔴 **域外写入方** ⇒ exit 1 | 域外的写入方不受本域控制 ⇒ 上线前必须人工确认/改造 |
| **绑列**：同一 INSERT 语句绑了 `created_at` 与 `updated_at` 吗？ | 没绑 ⇒ 🔴 exit 1 | V7 之后没有 DEFAULT ⇒ 不绑就 `NOT NULL` 违例 |
| `UPDATE` / `DELETE` 站点 | **只登记，不判失败** | 只有 INSERT 会插入新行 ⇒ 只有 INSERT 会撞 NOT NULL |
| DDL 迁移脚本 | 不算写入方 | 建表 / 改表，不是数据写入 |
| `tools/**` | 不算写入方 | 探针设施（`seed.sql` 是探针自己用的） |

⚠️ **绑列的判定只看 INSERT 的列清单**（`INSERT INTO t ( ... ) VALUES`）——
否则 WHERE 子句里的同名列会误判。

## 变异验证

去掉任意一个 INSERT 的 `created_at` / `updated_at` 绑定 ⇒ **EXIT=1** 并点名该文件行号；
复原 ⇒ EXIT=0。⇒ **不是假绿**。

## 边界（**必须一并读**）

⚠️ 本工具能看见的只有**本仓库的代码**。
**它看不见**：人工在 psql 里跑的 SQL、报表工具、数据修复脚本、
**另一个仓库**（如 `Sea-RideTheWind_golang` 或未来新增的仓库）、部署期的一次性运维命令。

⇒ **EXIT=0 不等于「可以放心上 V7」** —— 它只证明
「**本仓库里没有漏网的写入方**」。
⇒ **上线前仍需人工确认一次**（域外的写入方清单）。

## 相关

- `docs/DRIFT/favorite/rpc.md` §13.6 —— V7 破坏性变更与「域外写入方未盘点」的原始登记
- `service/favorite/favorite-rpc/src/main/resources/db/migration/V7__align_favorite_outbox_time_columns_with_go.sql`
- `tools/ddl-parity-probe/favorite/` —— DDL 逐列双侧活体对拍（V7 的依据）
