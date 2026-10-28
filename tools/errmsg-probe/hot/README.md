# hot 域错误码契约 · Go ↔ Java 双侧逐字节对拍

> **基线归属**：[`docs/PhaseGoal-judge.md`](../../../../docs/PhaseGoal-judge.md) §九 **G-5**（`hot-common` 的 R4 判定）
> **所属 lane**：`align/g3-article-comment-favorite-hot`（G3 · hot 域）
> **产出日期**：2026-10-29

## 这个探针为什么存在

hot-common 域**早就**做过一次双侧逐字节对拍（结论：sha256 一致），但那次探针**放在 `/tmp`**：

- `/tmp` 不在版本控制内 ⇒ 结论只剩台账里的一行字，**没有可复算的入口**；
- 本机实测（2026-10-29）：`/tmp` 下**已无**该探针 ⇒ 「声明的结论」**今天既跑不出来、也没了载体**；
- ⇒ 基线据此把 `hot-common` 的 **R4（真·双侧活体）判定降为 R3**（§九 G-5）。

本目录把那次的结论**变成仓内可复算的证据**（AGENTS.md §3.3：证据的载体必须在版本控制内，否则强度不可复现、不可传递、不可审计）。

## 真源 ↔ 被测

| | 位置 | 关键锚点 |
|---|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/service/hot/common/errmsg.go` | const 块 `:33-46`（11 个码）· `codeMsg` 表 `:48-61` · `GetErrMsg` `:76-82` |
| **RAG 被测** | `service/hot/hot-common/src/main/java/com/rag/hot/hot/common/errmsg/` | `HotErrorCode`（enum，**声明顺序 = Go 书写顺序**）· `HotErrorMessages`（`LinkedHashMap`，包级私有） |

## 用法

```bash
bash tools/errmsg-probe/hot/run-parity.sh          # 对拍
bash tools/errmsg-probe/hot/mutate-parity.sh       # 变异验证（证明守门非假绿）
```

`GO_REPO=/path/to/Sea-RideTheWind_golang` 可覆盖真源路径（默认取 RAG 的同级兄弟目录）。

**依赖**：mvn（离线 `-o`）· JDK 21+ · Go ≥1.25 · **Go module cache 需已有 grpc v1.81.1**。

## 它验的是什么

两侧各输出一份**同格式 dump**，逐字节比对（sha256 + `diff -u`）：

```
CONST|<code>              码常量表登记的码值（升序）
TABLE|<code>|<msg>        codeMsg 表的每一条，取值走 GetErrMsg（升序）
FALLBACK|<code>|<msg>     未登记码走 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
```

- **11 个已登记码**（200 / 500 / 1015 / 5001~5008）+ **12 个未登记码**
  （`0 / -1 / 1 / 999 / 1016 / 2001 / 3001 / 5009 / 5010 / 9999 / ±2147483647`）
  ⇒ 未登记码在两侧都必须回落到 **`"FAIL"`**（Go `errmsg.go:78-80` 的 `if !ok { return codeMsg[Error] }`）
  —— ⚠️ 这条**回退值本身属 L1**：hot-api 的 `httpx.SetErrorHandler`（`hotcenter.go:53-66`）靠它产出 `code=5001 / msg="internal server error"`。

### 三条「不能靠手抄期望值」的约束

1. **Go 腿不抄期望值**：它把真源 `errmsg.go` **逐字节复制**到临时模块（同包编译 ⇒ 能读到未导出的 `codeMsg`），并用 **sha256 校验复制件 == 真源**（不一致直接 exit 2）。
   Go 侧改名/删常量 ⇒ **编译失败**；Go 侧加常量 ⇒ Go 自检（const 集合 vs 表键集合）报错。
2. **Java 腿不抄期望值**：它调**真实的** `HotErrorCode` / `HotErrorMessages`（`hot-common/target/classes`），自己不持有任何码值/文案常量。
3. **两侧表键独立枚举**：Go 腿枚举 `codeMsg` 的键，Java 腿枚举 `HotErrorMessages.MSG_BY_CODE` 的键（**不是**枚举值）⇒ ⇒ 「表里多一条 / 少一条」也会被逐字节比对抓到。

## 实测结果（2026-10-29）

| 项 | 值 |
|---|---|
| 对拍 | **EXIT=0** · Go sha256 = Java sha256 = `bc1db3bf4c9090ce100a2bc76ec6e932f47ecb236c4379ee4fb1129686481bb3` |
| 行数 | 34（CONST 11 + TABLE 11 + FALLBACK 12） |
| 变异验证 | **4/4 精确转红**（exit 1）：M1 码值漂移 · M2 文案漂移 · M3 回落语义漂移 · M4 表多一条；还原后重新转绿 ⇒ **非假绿** |

## 与域内契约测试的关系（不重复）

| 层 | 手段 | 覆盖 |
|---|---|---|
| **跨语言逐字节** | **本探针** | Go 运行时 ↔ Java 运行时的**输出字节** |
| 域内契约 | `HotErrorCodeContractTest` / `HotErrorMessagesContractTest` | 码值 / 声明顺序 / 唯一性 / 总数 / 文案 / 双射 / 表内顺序 / 未知码回退 / 文案互异 / 跨域守卫 |
| 变异 | 域内契约测试另有 **11/11 全红** | 守门自身有效性 |

⇒ 本探针**补的是「跨语言那一层」**：域内契约测试的期望值是**抄自 Go 源码**的静态文本，而本探针是**真的把 Go 那份源码编译起来跑**——两边都不是抄的，才谈得上「双侧」。

## ⚠️ 边界

- 本探针**只覆盖错误码层**（码值 / 文案 / 未知码回落）⇒ **不覆盖** HTTP 响应体形状、gRPC 错误码、事务边界。
- Go 腿依赖 **Go module cache**（grpc v1.81.1）⇒ 离线机器首次运行需先备好 cache。
- 探针**不写 Go 真源仓库**（AGENTS.md §一：真源冻结）⇒ 真源侧零改动。
