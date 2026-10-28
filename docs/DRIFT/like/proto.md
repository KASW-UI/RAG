<!-- 由 `align/g2-message-like-task` 工作树产出 · 基线 commit `29a0bce` · 2026-10-29 -->

# `like` 域 proto 层台账（`like-proto`）

> **本文件是 `like-proto` 的专属台账** —— 此前该组件**没有**专属台账，
> 判定记录散落在 `docs/DRIFT/_cross/component-coverage-matrix.md:63`
> 与 [`docs/PhaseGoal-judge.md`](../../../PhaseGoal-judge.md) 的 A-2 行里
> ⇒ 属**记录缺口**（基线 §九 G-17 那一类），本文件把它补上。
>
> ⚠️ **与 `docs/Intentional_Deviations/like/proto.md` 的分工**：
> 那边记「**决定不对齐**的差异」（本域 1 条：proto `package` 归一）；
> 本文件记「**已核对且一致**的部分」+「未取证的部分」。

## 一、组件判定

| 项 | 值 |
|---|---|
| 组件 | `service/like/like-proto`（`like/rpc` 消费它） |
| 终点指标 | **【PROTO 型】①–⑤**（⑥ wire 字节对拍 = N/A，见 §四） |
| 判定命令（可复算） | `mvn -o -pl service/like/like-rpc -am test -Dtest=LikeProtoContractTest` |
| 辅助判定命令 | `python3 tools/difftest/check-surface-parity.py --domain like`（gRPC 方法集合面） |
| 证据强度 | **E3**（测试证据 · 有可定位的测试与实跑结果） |
| 核对日期 | **2026-10-29** |
| 基线 commit | `29a0bce` |
| 状态 | ✅ **R3**（此前 R2 —— 降级理由已消除，见 §三） |

## 二、两侧真源

| 侧 | 位置 | 说明 |
|---|---|---|
| **Go 真源** | `Sea-RideTheWind_golang/proto/like.proto` | sha256 `347301dbb4ed90265fcd3813be54701537901f9908d117e61615c73bdb8a447e` |
| **RAG** | `service/like/like-proto/src/main/proto/like.proto` | 复制自 Go（§3.4「复制粘贴，非翻译」） |

⚠️ **proto `package` 两侧不同**（Go `pb` / RAG `like`）⇒ 已签字偏离
**SD-2026-10-23-05** ⇒ 见 [`docs/Intentional_Deviations/like/proto.md`](../../Intentional_Deviations/like/proto.md)。
**本台账的一切比对都排除 `package` 行**（该签字文件里已写明这条要求）。

## 三、逐条结论（【PROTO 型】①–⑤）

| 轴 | 结论 | 证据 | 证据强度 |
|---|---|---|---|
| **①** `.proto` 剥 `option` / 空白后行为层零差异 | ✅ | 两侧逐行比对：19 个 message + 1 个 enum + 1 个 service 的**字段名 / 字段号 / 类型 / label / 枚举值 / rpc 签名**全部一致；差异**仅** `package`（已签字）+ `option` 块（`go_package` → `java_package`/`java_multiple_files`） | E3 |
| **②** message 名 / 字段名 / 字段号 / rpc 方法逐条一致 | ✅ | `LikeProtoContractTest#protoContractMatchesGoBaseline`：**期望值由 `generate.py` 从 Go 真源 `.proto` 生成**（不是手抄），冻结在 `service/like/like-rpc/src/test/resources/proto-contract/like-proto-contract.expected.txt`（22 行，含真源 sha256）；测试比对的是**生成码的 `FileDescriptor`**（真正参与 wire 编码的那份），不是 RAG 的 `.proto` 文本 | E3 |
| **③** `optional` presence 语义逐条核对 | ✅ **N/A** | `proto/like.proto` 里**没有** proto3 `optional` 字段（全量：19 个 message 的字段全是标量 / `repeated` / `map` / 枚举）⇒ presence 语义无差异面。**已显式钉死**：`LikeProtoContractTest#noProto3OptionalField` 断言「RAG 侧 proto3 optional 字段集合 = 空集」⇒ 后人若加 `optional` 会**立刻红** | E3 |
| **④** 字段号**无硬编码覆盖** | ✅ | ① 生产源码扫描：`service/like/**/src/main/java` 里**零命中** `FIELD_NUMBER` / `setField(FieldDescriptor` / `CodedOutputStream` / `UnknownFieldSet` / `getUnknownFields()` / `.writeTo(` / `mergeFrom(` ⇒ 业务代码只走生成码的 Builder 与 getter。守门：`LikeProtoContractTest#noHardcodedProtoFieldNumbersInSource`（**扫描根找不到时直接失败**，不静默跳过 ⇒ 不会退化成「永远绿」的假门）② 生成码自身的 `*_FIELD_NUMBER` 常量是 protoc 产物，不是人写的硬编码 ⇒ 已排除 | E3 |
| **⑤** 消费点构造方式与 Go 一致 | ✅ | RAG 生产源码：`newBuilder()` **21 处**，`parseFrom` / `JsonFormat` / 按字段号低层 API **0 处** ⇒ 消费方式 = 生成 Builder + getter（与 Go 的 struct literal + protobuf runtime **同一语义层**：都按**字段名**存取，不按字段号） | E3（源码级统计，未做双侧活体对拍 ⇒ 见 §四） |
| **⑥** wire 层字节对拍 | ⬜ **N/A（本轮不做）** | 需要两侧同时起服务的字节级探针（仓内只有 `favorite` 域有 `tools/proto-wire-probe/favorite/`）⇒ **阶段 2 建设项（C 类）**，本轮不预支 | — |

## 四、未取证项与边界（**如实记录，不写「已对齐」**）

| # | 项 | 现状 | 归口 | 阶段归属 |
|---|---|---|---|---|
| **P-U-1** | ⑥ wire 层字节对拍 | 未做（需双栈活体） | like 域 | **C 类**（阶段 2 建设项） |
| **P-U-2** | ⑤ 的**双侧活体**取证 | 本轮只做了**源码级**统计（Builder vs 低层 API），**没有**两侧同时跑同一请求矩阵 | like 域 | **C 类**（证据缺口，**非**行为差异） |
| **P-U-3** | `tools/difftest/check-proto-static.sh` 未覆盖 `like` | 该脚本只覆盖 user/admin/task/comment 四域 ⇒ `like`/`follow`/`hot` **连静态逐字节守门都没有** | 共享面（`tools/`） | **C 类**；⚠️ 该脚本若扩到 `like`，**`verdict` 必须排除 `package` 行**（SD-2026-10-23-05 的明文要求）⇒ 否则会把已签字偏离判成回退 |

> ⚠️ **P-U-2 不影响组件状态**：⑤ 的**行为层**（按字段名构造、不按字段号）已由源码统计 + ④ 的硬编码扫描钉死；
> 缺的是「两侧同时跑」的**活体**证据 ⇒ 属**取证缺口**，不构成行为差异 ⇒ 按基线 §1.3 **不计入必相对齐项**。

## 五、复现

```bash
# ① 契约回归网（4 条：基准比对 / package 签字值 / optional 集合 / 字段号硬编码扫描）
mvn -o -pl service/like/like-rpc -am test -Dtest=LikeProtoContractTest

# ② 重新生成基准（真源变了才跑；**基准不是手工编辑物**）
python3 service/like/like-rpc/src/test/resources/proto-contract/generate.py \
  "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto/like.proto" \
  service/like/like-rpc/src/test/resources/proto-contract/like-proto-contract.expected.txt \
  "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"

# ③ 契约面（gRPC 方法集合）
python3 tools/difftest/check-surface-parity.py --domain like
```

## 六、本轮结论（2026-10-29）

- **基线 §十 #14 的降级理由已消除**：原判「`like-proto` 无字段级 / wire 级仓内断言 ⇒ R3 的 ⑤ 不满足」
  ⇒ 本轮补上**字段级**（`generate.py` + `LikeProtoContractTest`，期望值由 Go 真源生成）
  ⇒ **R2 → R3**。
- ⚠️ **未做的两件事已如实登记**（P-U-1 wire 字节、P-U-2 双侧活体）⇒ **不写「R4」**，不写「已完全对齐」。
- ⚠️ **本文件的存在本身是补一条记录缺口**（此前 `like-proto` 无专属台账 ⇒ 基线 §九 G-17 那一类）
  ⇒ 记录缺口**不是**行为缺口 ⇒ 不降 R。
