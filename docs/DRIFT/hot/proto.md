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
| **复刻度 R / 证据强度 E** | 基线**已回填 R4 / E3**（[`docs/PhaseGoal-judge.md`](../../PhaseGoal-judge.md) **§三 矩阵 A-2** 的 `hot-proto` 行 · **2026-10-31 · G3 第二十二轮** ⇒ **R3 → R4**）⇒ ✅ **本轮独立复跑实测 R4 的 ⑦⑧**（**双侧同时运行**的探针对拍通过 ＋ **在版本控制内、现在可复算** ＋ **有变异验证记录**）⇒ ⇒ **R4 成立**；⚠️ **原写「基线是 SHARED 面 ⇒ 本 lane 不改 ⇒ 登记给合并收口执行者」＝ 已过期** —— 按 [`docs/Worktree.md`](../../Worktree.md) §二「G3 ⇒ `g3.md` ＋ **judge 的四域行**」⇒ **judge 的 hot 行归本 lane 写** ⇒ 本 lane 直接回填 |
| **复制忠实度** | ✅ **剥掉 `option` / `package` / 注释 / 空行后逐行 diff 为空** ⇒ message 名 / 字段名 / 字段号 / 类型 / 枚举值 / rpc 签名全部一致 |
| **表达层差异** | `option go_package`（Go 专用，Java 不消费）· 加 `java_package` + `java_multiple_files=true`（§3.4 允许） |
| **回归网** | ✅ **域内 `HotProtoContractTest`**（期望值由 `generate.py` **从 Go 真源生成并冻结**）＋ **契约面守门** `check-surface-parity.py --domain hot` |
| **wire 字节级** | ✅ **已闭合（2026-10-29 · G3）** —— `tools/proto-wire-probe/hot/` ⇒ Go 真源生成码 == `expected.txt` == RAG 生成码 **逐字节一致**（13 条用例）＋ 变异 **3/3 精确转红** ⇒ **A7 = L4** ⇒ `hot-proto` **R3 → R4**（✅ **基线已回填** —— 2026-10-31 · G3 第二十二轮 ⇒ judge **§三 矩阵 A-2** 的 `hot-proto` 行 = **R4**；⚠️ **原写「登记给收口执行者改基线」＝ 已过期**：judge 的四域行归本 lane 写） |
| **仍未覆盖** | ✅ **消费方式已取证（2026-10-31 · G3 第十九轮 · hot 域）** —— **G-19 的三个子项逐条有结论**：① **`optional` presence`** ⇒ **已被两个回归网钉住**（`HotProtoContractTest` 比 `Descriptors.FileDescriptor` 的 `optional ` 前缀 ＋ `HotProtoWireParityTest` 的零值不落 wire）② **字段号硬编码** ⇒ **本域不存在**（域侧代码零命中；命中全在 `target/generated-sources` 的 protoc 生成码里）③ **Jackson 配置** ⇒ **不适用**（hot 的 proto 只走 gRPC 二进制通道：`JsonFormat` / `protobuf-java-util` **零命中**；Jackson 只用于 Kafka 载荷）⇒ ⇒ **hot 域的 G-19 无剩余待取证项**（⚠️ **其余 9 个 proto 仍开** ⇒ 基线 §九那句「10 个 proto 全部未取证」**对 hot 已不成立**）· ⚠️ **gRPC 帧 / HTTP2 帧**（需两侧同时起服务） |

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
| **wire 字节级** | `tools/proto-wire-probe/hot/{run.sh,mutate-wire.sh,go/main.go,expected.txt}` ＋ `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/proto/HotProtoWireParityTest.java` | 🔴 **proto 消息体**的 **wire 字节**：同一组 **13 条**消息在两侧各序列化一次，比对**十六进制字节** ⇒ 钉住 **字段号** · **零值不落 wire**（proto3 隐式 presence）· **负 int32 = 10 字节 varint** · **`uint32` 最大值 = 5 字节 varint** · **`repeated` 的编码** · 🔴 **`bool=false` ⇒ 整条消息是空字节** | ✅ **EXIT=0**（Go 真源 `hot.pb.go` sha256 `c5b28a8415151ca3…` == 复制件 ⇒ 验的是**生产字节**）· 变异 **3/3 精确转红**（M3 改 **RAG 侧 proto 字段号** ⇒ Java 侧断言红 ⇒ 证明不是「自己跟自己」）· **在版本控制内、可复算** ⇒ 满足基线 §1.1 的 **R4 ⑦⑧** |
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
| ~~**H-3**~~ | ~~**wire 字节级**对拍探针~~ ⇒ ✅ **已闭合（2026-10-29 · G3 域侧落码）** | 取证缺口 → ✅ **已闭合** | ✅ **已落码**：`tools/proto-wire-probe/hot/`（`run.sh` + `mutate-wire.sh` + `go/main.go` + `expected.txt` + `README.md`）＋ Java 侧 `HotProtoWireParityTest`（**7 例**）⇒ 对拍 **EXIT=0** ＋ 变异 **3/3 精确转红** ⇒ **A7 = L4** ⇒ `hot-proto` **R3 → R4**（✅ **基线已回填** —— 2026-10-31 · G3 第二十二轮 ⇒ judge **§三 矩阵 A-2** 的 `hot-proto` 行 = **R4**；⚠️ **原写「基线 §四 A-2 属 SHARED 面 ⇒ 登记给合并收口执行者」＝ 已过期**：judge 的四域行归本 lane 写）<br>⚠️ **归属订正**：本行原写「`tools/**` 共享面 ⇒ G3 只读」⇒ **该归属判断已作废** —— 按 AGENTS.md §5.3f「**共享判据 = 依赖度数**」⇒ `tools/proto-wire-probe/hot/` **只被 hot 域引用** ⇒ **真私有 ⇒ G3 域侧落码合法** |
| **G-19** | proto 的**消费方式**取证 —— AGENTS.md §3.4 的效力边界「复制忠实 ≠ 消费方式已验」 | 取证缺口 → ✅ **已闭合（2026-10-31 · G3 第十九轮 · hot 域）**（**不阻断阶段一**） | ✅ **已闭合（域内取证 · 非行为漂移）**：三个子项逐条落地 —— ① **`optional` presence`** ⇒ `HotProtoContractTest`（比对 `Descriptors.FileDescriptor`，含 `optional ` 前缀）＋ `HotProtoWireParityTest`（7 例，含零值不落 wire）⇒ 本轮实跑 **8 例 / 0F / 0E / 0S**；② **字段号硬编码** ⇒ **本域不存在**（域侧代码零命中；命中全在 protoc 生成码）；③ **Jackson 配置** ⇒ **不适用**（proto 不走 JSON：`JsonFormat` / `protobuf-java-util` 零命中）⇒ ⇒ **hot 域无剩余待取证项**（⚠️ **其余 9 个 proto 的 G-19 仍开** ⇒ 基线 §九「10 个 proto 全部未取证」**对 hot 已不成立**） |
| **G-15 / G-18** | ~~「hot-proto 无自动守门」与「已落码 `HotProtoContractTest`」自相矛盾~~ ＋ ~~判定记录只在 `vs-common.md` 不在 `proto.md`~~ | 记录缺口 | ✅ **2026-10-29 · G3 已订正**：`vs-common.md` A7 行回填 ＋ **本文件建立** ⇒ 两处口径统一 |

---

## 4. 与既有台账的关系

| 文件 | 关系 |
|---|---|
| [`docs/DRIFT/hot/vs-common.md`](vs-common.md) | **H-1 行 = 落码与取证过程的权威记录**（本文件只给判定结论，不重复） |
| [`docs/DRIFT/hot/common.md`](common.md) | 本域 `hot-common` 组件台账（**另一个组件**） |
| [`docs/DRIFT/_cross/proto-verification.md`](../_cross/proto-verification.md) | §3.4 的**全仓级**验证 —— **共享文件**，本工作树不碰（⇒ 其 `:45` 行「脚本一个都没覆盖」已过期，属收口订正项） |
