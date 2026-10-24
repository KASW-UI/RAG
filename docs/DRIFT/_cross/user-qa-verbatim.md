<!-- @move AGENTS.md §4.3 · 用户问答原话（备查，防止后续被覆盖） -->

# 用户问答原话（备查）

> 从 AGENTS.md §4.3 搬出。**保留原话是为了防止后续 agent 把它「优化」掉。**


| 问题 | 用户答案 |
|---|---|
| ORM 这一层怎么处理？ | 严格按 HUMAN_ONLY：JPA → MyBatis-Plus（重写数据层） |
| Logic 类怎么处理？ | 严格按 HUMAN_ONLY：9 合 1 |
| DataModel.java 怎么映射？ | 严格按 HUMAN_ONLY：拆成两个 Mapper（仅在选 MyBatis-Plus 时适用） |
| SnowflakeBootstrap 与 Flyway 脚本要不要落地？ | 落地：从 UserRpcApplication 抽 SnowflakeBootstrap + 引入 Flyway + 写两份 DDL。**⚠️ 2026-10-21 补充：Bootstrap 保留，Flyway 已被移除**（有状态账本不兼容双 rpc 共库），DDL 改为幂等重放 |
| 全量 5 阶段怎么走？ | 你先修改 `/Users/pr/Documents/java&golang/RAG/AGENTS.md`，说明是什么情况 |
| 重构以什么形式落到 RAG 仓库？ | 先等下（暂停，等本文件落地后再议） |
