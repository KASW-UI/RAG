<!-- @move AGENTS.md §二 · HUMAN_ONLY.MD 的存在性状态与校正记录 -->

# HUMAN_ONLY.MD 存在性台账

> 从 AGENTS.md §二 搬出（**定义留在 AGENTS.md，状态在这里**）。
> ⚠️ 改这份清单前**先跑一次 `glob **/HUMAN_ONLY.MD`**，别照抄上一版 ——
> 「文件已写」同样是需要证据的断言（AGENTS.md §3.3 同款形态）。

---

当前已落 HUMAN_ONLY.MD 的子模块（**2026-10-22 用 `glob **/HUMAN_ONLY.MD` 实测校正，全仓共 4 份**）：

- **service/users/user**：user-rpc / user-api
- **service/users/admin**：admin-rpc / admin-api

⚠️ **本节此前写的「共 7 份」是错的**（§7.2 的文件清单也同步错了）。以下 3 条**被登记为已存在、实际不存在**：
`service/users/users-common/HUMAN_ONLY.MD`、`service/message/message-rpc/HUMAN_ONLY.MD`、
`service/message/message-api/HUMAN_ONLY.MD`。
⇒ **「文件已写」同样是需要证据的断言**（§3.3 同款形态）：改这些清单前先跑一次 glob，别照抄上一版。

未覆盖的子模块：**users-common / message-rpc / message-api**（虽已实现并做过 Go ↔ RAG 对照，但**文档缺失**）
+ **Go 有、RAG 尚未翻译的 3 个微服务**：`knowledge` / `points` / `security`
（🚫 **不在本项目范围内，不是待办** ⇒ §1「微服务范围」）
+ **2026-10-23 12:25 新纳入范围但只有 proto 层的 4 个域**：`comment` / `favorite` / `follow` / `task`
—— 它们的 **4 api + 4 rpc + 3 common 共 11 个组件仍是空壳**（只有 `pom.xml`、0 行 Java）⇒ 无可对照对象
+ **已翻译但对照未闭环的组件**：`article-api`（从未对照）、`article-rpc`（首轮组件级完成）、
`hot-api`（仅源码级对照）、以及 `like-common` / `message-common`
（**连 Go ↔ RAG 对照都还没做过**，见 §0）。前者建议按 phase 补文档，后者暂按 Go 行为对齐、进入对应 phase 再补。
  - ⚠️ **2026-10-23 校正**：此处原把「6 个 `*-proto`」也列进"从未做过"，**该条已过期**——
    10 个 proto 实为**从 Go 侧复制**（非翻译），2026-10-23 实测**语义层 10 个中 9 个逐条一致**，
    `comment` 有 **1 处已签字偏离**（proto `package` 归一，`SD-2026-10-23-04`），
    详见 **§3.4** 与 [`../../Intentional_Deviations/comment/proto.md`](../../Intentional_Deviations/comment/proto.md)；
    其中 `user` / `admin` 有守门脚本，另 8 个**只有一次性实测、无回归网**。
⚠️ **2026-10-23 实测校正**：原枚举写作「article / comment / favorite / follow / hot / like / points / security / task / common」——
① **漏了 `knowledge`**：Go 侧是**独立微服务**（含 api 组件，依赖 Neo4j + Milvus），RAG 侧**完全未翻译**，
仅 [`Infrastructure.md`](../../../Infrastructure.md) 标「❌ 未实装」；② **`hot` / `like` / `common` 三项已过期**——
§0 记录它们已完成或部分完成对照，不该继续列在「从未做过」里。

`HUMAN_ONLY.MD` 第 2 部分的"Java 理论文件树"是 aspirational design，从未被代码落地过。RAG 的本次重构就是把这个目标架构在 RAG 上落地一遍。

**2026-10-20 起取消「反向同步」**：真源（Go）不接受任何方向的写入或修正。此前允许 RAG 反向同步到 Java 镜像，等于让真源变成**双向可变**——这比单向漂移更危险，因为任何一侧都无法再被信任为判定标准。RAG 产出的是 Spring 表达经验（值得沉淀进 `HUMAN_ONLY.MD`），不是行为定义。
