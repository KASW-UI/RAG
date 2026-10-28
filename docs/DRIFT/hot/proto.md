<!-- @new 2026-10-29 · hot 域 proto 组件台账（补齐 G-18「判定记录位置不统一」的落点缺口） -->
<!-- 产出归属：`align/g3-article-comment-favorite-hot` 工作树 · 基线 commit `29a0bce` -->

# DRIFT 台账 · `service/hot/hot-proto`

> **核对时间：2026-10-29**
> **真源：** `Sea-RideTheWind_golang/proto/hot.proto`
> **本轮性质：** 阶段 1（简历可展示项目）· **只做本域** · **不碰阶段二**
>
> ⚠️ **为什么本文件现在才出现**：本组件此前的判定记录**只写在**
> [`docs/DRIFT/hot/vs-common.md`](vs-common.md) 的 **§3 H-1** 行 ⇒ ⇒ **台账位置不统一**
> （基线 §九 **G-18**）⇒ ⇒ **别的域都有 `proto.md`，hot 域没有** ⇒ ⇒ 读 `hot/proto.md` 的人
> 会以为「本组件从未对照过」⇒ ⇒ 本文件把**判定结论**落到本组件自己的台账，
> **内容不重复登记**，只链接到 `vs-common.md` 的 H-1（那行是**落码与取证过程**的权威记录）。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| **总体判定** | ✅ **一致**（行为层零差异） |
| **行为差异** | **0 条** |
| **复刻度 R / 证据强度 E** | **R3 / E3**（与基线 [`docs/PhaseGoal-judge.md`](../../PhaseGoal-judge.md) §四 A-2 的 `hot-proto` 行一致） |
| **复制忠实度** | ✅ **剥掉 `option` / `package` / 注释 / 空行后逐行 diff 为空** ⇒ message 名 / 字段名 / 字段号 / 类型 / 枚举值 / rpc 签名全部一致 |
| **表达层差异** | `option go_package`（Go 专用，Java 不消费）· 加 `java_package` + `java_multiple_files=true`（§3.4 允许） |
| **回归网** | ✅ **域内 `HotProtoContractTest`**（期望值由 `generate.py` **从 Go 真源生成并冻结**）＋ **契约面守门** `check-surface-parity.py --domain hot` |
| **未覆盖** | ⚠️ **wire 字节级**（H-3 ⇒ `tools/**` 共享面 ⇒ 已登记）· ⚠️ **消费方式**（`optional` presence / 字段号硬编码 / Jackson 配置 ⇒ 基线 §九 **G-19**） |

---

## 1. 复制忠实度

| 项 | Go 侧 | RAG 侧 | ✅ |
|---|---|---|---|
| `package` | `package pb;` | `package hot;`（归一） | ✅ 表达层 |
| `option` 块 | `option go_package = ...` | `java_package` ＋ `java_multiple_files = true` | ✅ 表达层 |
| **行为层** | — | — | ✅ **diff 为空** |

> 📌 判定过程与逐条 diff 记录在 [`docs/DRIFT/hot/vs-common.md`](vs-common.md) 的 **A7 行**（**2026-10-29 已订正**）。

---

## 2. 回归网（本组件的**可复算**证据）

| 网 | 落点 | 覆盖 | 状态 |
|---|---|---|---|
| **字段级契约** | `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoContractTest.java` ＋ `src/test/resources/proto-contract/{generate.py,hot-proto-contract.expected.txt}` | **生成码的描述符**（`Descriptors.FileDescriptor` ⇒ 真正参与 wire 编码的那份契约）↔ **由真源脚本生成并冻结**的基准：package · 11 个 message 的字段名/字段号/类型 · `repeated` · 枚举值 · 5 个 rpc 的方法名与出入参 | ✅ **在版本控制内**（`git ls-files` 命中两个资源文件）⇒ 期望值**不是手抄的** |
| **契约面** | `python3 tools/difftest/check-surface-parity.py --domain hot` | HTTP 端点集合 ＋ gRPC 方法集合（大小写归一） | ✅ **EXIT=0**：HTTP **Go=1 ≡ RAG=1**（Go 侧 JWT 保护 **0** 条 ⇒ hot-api 两侧都匿名）· gRPC 方法 **Go=5 ≡ RAG=5** |

> ⚠️ **复算命令必须带 `GO_REPO`**：`check-surface-parity.py` 的默认 Go 仓路径是
> `os.path.join(os.path.dirname(REPO), "Sea-RideTheWind_golang")` ⇒ 在**工作树**里
> `dirname(REPO)` = `…/RAG.worktrees` ⇒ ⇒ **指不到真源** ⇒ ⇒ 报「Go routes.go 不存在」
> ⇒ **量到的是 0 vs 0**（`hot` 域 EXIT=1，其他域可能 EXIT=0 的**假绿**）
> ⇒ ⇒ **共享面缺陷**（`tools/**` 不在 G3 写权面）⇒ 已登记给 G4 / 合并收口执行者。
> ⇒ **正确写法**：`GO_REPO=<Sea-RideTheWind_golang 的绝对路径> python3 tools/difftest/check-surface-parity.py --domain hot`。

---

## 3. 未决 / 待办（**阶段 1 内登记，不预支阶段 2**）

| # | 事项 | 类型 | 归属 |
|---|---|---|---|
| **H-3** | **wire 字节级**对拍探针（favorite 域已有 `tools/proto-wire-probe/favorite/`） | 取证缺口（**不阻断阶段一**） | 🟡 `tools/**` 共享面 ⇒ **登记 + 反馈单**，G3 只读 |
| **G-19** | proto 的**消费方式**未取证（`optional` presence / 字段号硬编码 / Jackson 配置）—— AGENTS.md §3.4 的效力边界「复制忠实 ≠ 消费方式已验」在 **10 个 proto 上全部成立** | 取证缺口（**不阻断阶段一**） | 🟡 按域补「生成码 presence / 消费点构造」断言（`ArticleProtoContractTest` 已示范） |
| **G-15 / G-18** | ~~「hot-proto 无自动守门」与「已落码 `HotProtoContractTest`」自相矛盾~~ ＋ ~~判定记录只在 `vs-common.md` 不在 `proto.md`~~ | 记录缺口 | ✅ **2026-10-29 · G3 已订正**：`vs-common.md` A7 行回填 ＋ **本文件建立** ⇒ 两处口径统一 |

---

## 4. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/DRIFT/hot/vs-common.md`](vs-common.md) | **H-1 行 = 落码与取证过程的权威记录**（本文件只给判定结论，不重复） |
| [`docs/DRIFT/hot/common.md`](common.md) | 本域 `hot-common` 组件台账（**另一个组件**） |
| [`docs/DRIFT/_cross/proto-verification.md`](../_cross/proto-verification.md) | §3.4 的**全仓级**验证 —— **共享文件**，本工作树不碰（⇒ 其 `:45` 行「脚本一个都没覆盖」已过期，属收口订正项） |
