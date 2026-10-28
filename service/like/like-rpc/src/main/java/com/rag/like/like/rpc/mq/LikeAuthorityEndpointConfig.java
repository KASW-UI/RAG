package com.rag.like.like.rpc.mq;

// [骨架占位 · 暴露形态未实现] AuthorityHandlerFactory.register("rtw.like-mq", …)；合并 Go P4；
// ⚠️ 监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，待拍板）
// 推导依据：FileTree/like/rpc.md §12.5
//
// ─────────────────────────────────────────────────────────────────────────────
// 🔴 2026-10-29 G2 更新：**语义层已实现，只剩「暴露形态」这一半**
//
//   · 本文件从「整个权威读都没做」缩到「**只差 HTTP 暴露形态**」：
//     语义层见 {@link com.rag.like.like.rpc.service.LikeAuthorityLookupService}
//     （Go `service/like/rpc/internal/model/fact_authority.go:12-146` 的 12 道校验逐字翻译，
//      零 servlet 依赖；守门 `LikeAuthorityLookupServiceGateTest` 11 条全绿）。
//
//   · 🔴 为什么**本文件仍然不做**（三个硬约束叠加，逐条核过）：
//     ① 共享层 `com.rag.common.communityfact.AuthorityHandler` 与
//        `com.rag.common.spring.AuthorityHandlerFactory` **依赖 servlet** ⇒ like-rpc 的
//        classpath **无 servlet-api** ⇒ `implements AuthorityHandler.AuthorityLookup`
//        会让 **Spring 上下文起不来** ⇒ 「为了暴露形态把整个模块搞挂」不可接受；
//     ② 给 like-rpc 加 `spring-boot-starter-web` ⇒ 本模块 **4 个未声明 `webEnvironment`
//        的 `@SpringBootTest`** 会默认起 Tomcat ⇒ 为形态变更拿既有测试面冒险不值；
//     ③ Go 侧形态是**独立进程** `service/like/rpc/cmd/fact-authority/main.go`
//        （`LIKE_DATABASE_URL` + `LIKE_FACT_AUTHORITY_LISTEN` + `LIKE_FACT_AUTHORITY_TOKEN`，
//        token 长度 ≥32 否则启动失败）⇒ 若选「同进程独立端口」⇒ **wire 面变更**。
//
//   · ⇒ **形态决策（独立进程 vs 同进程独立端口）待用户/收口执行者拍板**
//     （登记见 `docs/DRIFT/like/rpc.md` §2 的 `DRIFT-LIKE-003-C`）。
//     ⇒ 在拍板前，本文件**故意保持骨架状态**，不预支阶段二的形态实现。
//   ⇒ ⚠️ **不要**因为看到「骨架」就以为语义层也没做 ⇒ 语义层已完成，见上。
