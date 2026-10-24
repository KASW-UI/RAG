<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 -->

# global · common 尾巴踩坑档（common-tails）

---

## PIT-GLOBAL-001 🔴 **查 Go 出处时，只在项目树里 grep 一定会落空**

### 现象

`global` 台账前几版给 T-1 / T-2 / T-5 / T-9 写的 Go 出处都形如「Go 侧 `service/common/...`」。
本轮逐条复核时，**这些位置在 Go 项目树里一个都不存在**：

| 台账写的 | 项目树实测 |
|---|---|
| `withJsonBody` | ❌ `grep -rn "withJsonBody" service/` → 空 |
| `MaxConnsHandler` | ❌ `grep -rn "MaxConns" service/` → 只命中 `knowledge` 域（**范围外**） |
| `path.Join(cc.Target(), method)` | ❌ `grep -rln "reaker" --include=*.go .` → **全仓空** |
| `UnmarshalJsonMap` | ❌ 空 |

⇒ 差点被误判成「台账幻觉」或「Go 侧没有这个行为 ⇒ 无差异」。

### 根因

**真源有两层**，AGENTS.md §3.1 的「Go 为唯一基准」没说清是哪一层：

| 层 | 位置 | 覆盖 |
|---|---|---|
| **L1 项目层** | `Sea-RideTheWind_golang/service/**` | 业务代码。`service/common` 只有 `logger` / `response` / `snowflake` / `observability` / `communityfact` |
| **L2 框架层** | `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` | **请求保护层四层**、HTTP body 解析、gRPC 中间件（超时/熔断/降载） |

⇒ Go 侧 `service/common` **根本没有** `http/` 与 `protection/` 子包
（`git log --all --diff-filter=A -- "service/common/http/*" "service/common/protection/*"` → **空**，从未有过）。
⇒ **RAG 的 `service/common/http/` + `service/common/protection/` 对应的是 go-zero，不是 Go 项目的 `service/common`。**

### 正确做法

```bash
GZ="$(go env GOMODCACHE)/github.com/zeromicro/go-zero@v1.10.2"   # ⚠️ 版本取自 go.mod
grep -rn "MaxConnsHandler" "$GZ/rest/"
grep -rn "path.Join" "$GZ/zrpc/internal/clientinterceptors/"
```

⚠️ **版本必须从 `go.mod` 读**（本项目是 `v1.10.2`），模块缓存里同时躺着 `v1.10.0` / `v1.10.2` / `v1.10.3` ⇒ 读错版本同样是漂移。

### 判定口诀

> **「请求保护层」「body 解析」「RPC 中间件」这三类 ⇒ 先去 L2（go-zero）找。**
> **「业务逻辑」「错误码」「表结构」「proto」⇒ L1（项目树）。**

### 连带教训（AGENTS.md §3.1 的老问题，这里再撞一次）

台账里的「Go 出处」也可能**本身就是错的** ⇒ 必须**回到 Go 源文件 line-by-line 复核**，
不能拿台账当基准（§3.1 明令：不要拿「现有 RAG 代码」或「注释」当对照基准）。
本轮就有两处 RAG javadoc **照抄了 Go 那一行、却没实现它**（见 `DRIFT/global/common-tails.md` T-9）。

### 出处

- 踩坑位置：`docs/DRIFT/global/common-tails.md` §〇、T-1、T-2/T-5、T-9
- 方法论归档：`docs/DRIFT/global/cross-domain.md` X-8
- 2026-10-25，`align/global` @ `58e8314`

---

## PIT-GLOBAL-002 ⚠️ **「档位错置」：代码自称「有意」，但没人签字**

### 现象

`service/common/protection/GoStyleMaxConnsFilter.java` 的 javadoc 自称：

> 「本类取**全局一个闸门**……**此处为有意的表达层收敛，已在此显式留痕**，不是等价性的隐藏缺口。」

而 `global` 台账把同一条记为 🔴 **仍未修**。
⇒ **同一件事，两个互斥的结论。**

### 根因

§5.3b 规定三档台账是**互斥**的，判据只有一条：

> **这条差异要不要「修回去」？要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations。**

而「有意」这件事**需要签字**（`docs/Intentional_Deviations/`）。
⇒ 这条差异**既不在 DRIFT 的「要修」侧，也不在签字档** ⇒ 是个**无归属的第三态**。
⇒ 代码注释里的「有意」**不构成签字** —— 注释是实现者的自述，不是决策记录。

### 正确处置

1. 发现代码自称「有意」而台账记「未修」⇒ **先判档位归属**，不要急着改代码；
2. 归 DRIFT ⇒ 修；
3. 归 Intentional_Deviations ⇒ **要用户签字**（签字权不在 agent 手上）⇒ 落 `docs/Intentional_Deviations/global/`，并从 DRIFT 侧移除；
4. ⚠️ **不许因为「代码里写了有意」就当它已获批**（§5.3b 的核心告诫：
   「不要因为签了本条就把它当成已解决」是同一条纪律的镜像面）。

### 出处

- 踩坑位置：`docs/DRIFT/global/common-tails.md` T-2 / T-5「附带发现：档位错置」
- 2026-10-25，`align/global` @ `58e8314`
