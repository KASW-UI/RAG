# Pitfalls 台账总索引

> **这里记「踩过的坑」**：框架机制静默失效、绿测试锁死错误答案、基础设施误用……
> 与 [`../DRIFT/_INDEX.md`](../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「与 Go 不一致」（需要对齐的差异），Pitfalls 记「为什么会踩」（教训本身）。**
> 每条都应回答：**症状像什么 · 真实原因 · 怎么证伪 / 怎么确认 · 留下的守门网**。

## 目录结构与 `docs/DRIFT` 同构（`<域>/<api|rpc>.md`），空文件即待填坑位

## 已有内容

| 文件 | 坑 | 状态 |
|---|---|---|
| [`_cross/flyway-ledger-collision.md`](_cross/flyway-ledger-collision.md) | 🔴 多服务共用同一 PG schema ⇒ **Flyway 账本必然撞号**，后启动者的迁移被**静默跳过**（跨 message/like/article 三域） | **已定案，未实施** |
| [`_cross/testing-pitfalls.md`](_cross/testing-pitfalls.md) | 「把没看过的东西变成看过了」本身就能挖出真 bug；断言「Bean 存在」≠ 断言「机制生效」 | 已有 2 段 |
| [`users/_shared.md`](users/_shared.md) | ① 命令行属性**前缀写错 ⇒ 静默归零**（MyBatis-Plus 是 `mybatis-plus` 不是 `mybatis`）；② 脚本批量改 N 处 ⇒ 必须配**守恒断言**而非逐点测试 | 已有 2 段 |
| [`common/http.md`](common/http.md) | 「代码里写了」≠「客户端收到」：响应已 committed 后 `setHeader` 被**静默忽略** ⇒ text/plain 400 丢 `Content-Type`（4 轮对拍全绿） | 已有 1 段 |
| [`like/rpc.md`](like/rpc.md) | Boot 4 把 Kafka 自动配置**整体搬出** `spring-boot-autoconfigure` ⇒ 只引 `spring-kafka` 时 `@KafkaListener` **完全静默忽略** | 已有 1 段 |

## 待填（空文件即坑位）

`article/{api,common,rpc}` · `common/{_signed-deviations,audit,deadline,protection}` ·
`hot/{api,common,rpc}` · `like/api` · `message/{api,rpc}` ·
`users/admin/{_shared,api,rpc}` · `users/user/{api,rpc}` · `_cross/{message-domain-wide,open-items,signed-deviations}`

## 与 AGENTS.md §3.3 的关系

AGENTS.md **§3.3 保留全部规则条文**（那是每次都会进上下文的纪律），
案例与证据在本目录。规则里凡提到具体案例处，均已改为指向本目录的链接。

## 搬移来源

每段内容首行为 `<!-- @move AGENTS.md L<a>-L<b> · 标题 -->` 哨兵，正文为原样字节拷贝。
逐段台账见 [`../DRIFT/_MANIFEST.json`](../DRIFT/_MANIFEST.json)（39 段），
可重跑脚本 `tools/drift-move.py`（`extract` / `verify` / `delete`）。
