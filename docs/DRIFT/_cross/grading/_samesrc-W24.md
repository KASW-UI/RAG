# W24 · 同源判定（2 条）

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `SD-2026-10-23-09` | `like` | 不同的事 | `like/api.md:54` 这条讲「worker 池饱和 ⇒ 裸 503」（落点 `HttpMiddleware` 的 `MAX_HTTP_WORKERS=512`）＝ 台账自称的唯一别名 `SD-2026-10-23-09@like`（`like/api.md:56` 声明同号在别处只是**引用本条**、不是另一条偏离 ⇒ 本身不构成跨域撞号）；而 `L5-signoff.md:190` §十 讲的**是另一件事** —— deadline 毫秒取整那条（`-10`）的描述订正，且它自己在 `:198`／`:236` 写明「`-09` 是 worker 池饱和的裸 503 那条，与 deadline 无关」⇒ 现象与落点都不同 ⇒ 只是编号撞了，保持拆开（global 那段应随 `-10` 走） | `DRIFT/global/lanes/L5-signoff.md:190` |
| `SD-2026-10-24-10` | `like` | 同一件事 | `common-tails.md:547` §附 讲的就是「`like` 域 `SD-2026-10-24-10` 的**描述**需订正」，其 `:551` 直接点名「待订正的出处 ① `docs/Intentional_Deviations/like/api.md:77-111` 的 **`SD-2026-10-24-10`**（⏳ 待签字候选）」⇒ 与 `like/api.md:87` 是同一条候选：同一落点（`LikeRpcClient.java` 调 `GoStyleRequestDeadline.remainingMillis()` 的只读毫秒粒度）、同一组件（`like-api` 全部 6 个端点的下游调用）、同一现象（有效 deadline 取整 <1ms 不可观察）⇒ global 那段只是**跨域引用 + 措辞订正**，不是另一条偏离 | `DRIFT/global/common-tails.md:547` |
