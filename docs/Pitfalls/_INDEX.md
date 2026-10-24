# Pitfalls 台账总索引

> **这里记「踩过的坑」**：框架机制静默失效、绿测试锁死错误答案、基础设施误用……
> 与 [`../DRIFT/_INDEX.md`](../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「与 Go 不一致」（需要对齐的差异），Pitfalls 记「为什么会踩」（教训本身）。**
> 每条都应回答：**症状像什么 · 真实原因 · 怎么证伪 / 怎么确认 · 留下的守门网**。

> 📌 **更新日期：2026-10-25（10 域并发对齐合并后）**
> 合并前索引只列了 5 条，合并后各域产出大量新坑位，本索引已按实际内容重写。

## 目录结构与 `docs/DRIFT` 同构（`<域>/<api|rpc>.md`），空文件即待填坑位

## 已有内容（按域）

| 域 | 已填 | 主要坑 |
|---|---|---|
| `users` | 6 份 | MyBatis-Plus 配置前缀写错**静默归零**；脚本批量改 N 处须配**守恒断言**；双侧活体对拍编排（admin-rpc 3s dialTimeout 导致的启动顺序硬约束） |
| `task` | 1 份 | Boot 4 拆模块缺依赖是**硬启动失败**不是静默失效；`@EnabledIfDockerAvailable` 把「拉不到镜像」与「Docker 不可用」压成同一个 skip ⇒ 15 条真 PG 用例静默全跳 |
| `message` | 2 份 | 响应已 committed 后 `setHeader` 被静默忽略 ⇒ text/plain 400 丢 `Content-Type`（4 轮对拍全绿仍漏） |
| `like` | 2 份 | Boot 4 把 Kafka 自动配置整体搬出 `spring-boot-autoconfigure` ⇒ `@KafkaListener` **完全静默忽略** |
| `hot` | 3 份 | 重桶 `hash()` 仿错影响 49.5% 的 key；Jackson 3 ⇒ `ObjectMapper` Bean 缺失 |
| `article` | 2 份 | `@Transactional(readOnly=true)` 在真 PG 触发 `25006`；jsonb 恒 `null` 而 101 条测试全绿 |
| `comment` | 2 份 | `comment-rpc` 的 17 个类曾是空壳而全量编译 `EXIT=0` |
| `follow` | 2 份 | —— |
| `common` | 5 份 | 请求保护层四层：**recover → breaker → shedding → 业务观测**；链的定义点必须在共享模块，否则 10 个 rpc server 各写一遍 = 10 个可能写错的地方 |
| `_cross` | 4 份 | Flyway 账本撞号；框架机制静默失效实证；「跑绿 ≠ 对」的元教训 |

## 待填（空文件即坑位）

`article/{common}` · `common/{_signed-deviations}` · `hot/…` 已全填 ·
`like/…` 已全填 · `message/…` 已全填 · `task/{api,common,proto}` ·
`users/admin/…` 已全填 · `users/user/…` 已全填 ·
`favorite/*`（本域本轮未做）· `follow/common` 已填、`follow/proto` ·
`comment/{common,proto}` 已填、`comment/proto` ·
`_cross/{message-domain-wide,open-items,signed-deviations}`

## 与 AGENTS.md §3.3 的关系

AGENTS.md **§3.3 保留全部规则条文**（那是每次都会进上下文的纪律），
案例与证据在本目录。规则里凡提到具体案例处，均已改为指向本目录的链接。

## 搬移来源

每段内容首行为 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵，正文为原样字节拷贝。
逐段台账见 [`../DRIFT/_MANIFEST.json`](../DRIFT/_MANIFEST.json)（39 段），
可重跑脚本 `tools/drift-move.py`（`extract` / `verify` / `delete`）。
