<!-- @move AGENTS.md §3.4 · proto 复制一致性的实测数据与回归网状态 -->

# proto 复制一致性：实测数据与回归网状态

> 从 AGENTS.md §3.4 搬出。**规则与效力边界留在 AGENTS.md §3.4**，本文是它的证据。

---


**实测方法（可复现）**：剥离 `option` 行、注释、缩进、空行后逐文件 `diff`，6/6 输出为空；
`message`+`enum` 声明数与含字段号声明数两侧逐个相等：

| proto | message+enum（Go = RAG） | 含字段号声明（Go = RAG） |
|---|---|---|
| `admin` | 28 = 28 | 61 = 61 |
| `article` | 14 = 14 | 63 = 63 |
| `hot` | 11 = 11 | 18 = 18 |
| `like` | 16 = 16 | 47 = 47 |
| `message` | 22 = 22 | 111 = 111 |
| `user` → `users` | 20 = 20 | 47 = 47 |

⇒ **proto 不是 §3.1 意义上的「翻译点」，它没有可漂移的表达层。**
§3.1 要求覆盖的「proto 字段号、message 名称」两项由此覆盖。

⚠️ **三档回归网状态（勿混为一谈，2026-10-23 实跑）**：

| 档 | proto | 证据 | 能否防回退 |
|---|---|---|---|
| ✅ 有网 | `user` / `admin` | `tools/difftest/check-proto-static.sh` → `EXIT=0`，脚本逐字节比对字段名 / 字段号 / message 名 / rpc 签名 | ✅ 改坏即红 |
| ⚠️ **只有一次性实测** | `article` / `hot` / `like` / `message` | 2026-10-23 首次人工比对，一致；**但脚本一个都没覆盖** | ❌ **无网，随时可被改坏而无人知晓** |
| 🚫 **不做（非本项目范围）** | `comment` / `favorite` / `follow` / `points` / `security` / `task` | Go 侧 `proto/` 共 12 个，RAG 侧只做 6 个；缺的这 6 个**不在范围内**，见 §1「微服务范围」 | 不适用 |

⇒ **待办**：`check-proto-static.sh` 应扩到剩余 4 个 proto（照 `user` / `admin` 的写法加 `verdict` 即可），
否则这 4 个的「一致」只是一次性快照，不是可持续的结论。
