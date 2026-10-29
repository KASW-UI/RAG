# `task-api` 踩坑档（为什么会踩）

> **本域暂无已登记坑位**（核对日期 2026-10-29 · 已核 `grep -cE 'PITFALL-TASK|PIT-TASK' docs/DRIFT/task/api.md` ⇒ **0**；
> 且 `grep -cE '^## PIT-' docs/Pitfalls/task/rpc.md` ⇒ **34** 条全部落在 `rpc.md` 一个组件上 ⇒ **本文件无独立条目**）。
>
> ⚠️ **不是「task-api 没有陷阱」** —— `docs/Pitfalls/task/rpc.md` 里有 **4 条**坑的**触发点其实落在 task-api 侧**
> （`PIT-TASK-04` urllib 自动补 `Content-Type` · `PIT-TASK-11` 方法签名对了 ≠ 方法会被调到 ·
> `PIT-TASK-12` 配置项名字写错 = 静默失效 · `PIT-TASK-13` 探针自己触发了被测行为），
> 但它们是**同一批教训**、**已由 task-rpc 的踩坑档统一登记**（避免同一条教训在两个文件里各写一遍、彼此漂移）⇒ ⇒ **本档不重复登记**。
>
> **task-api 侧的守门网**（实测条数取自 2026-10-29 的 surefire XML）：
> `TaskApiContentTypeTest` **13** · `TaskApiFailurePathMatrixTest` **33** · `TaskApiFormLimitReachabilityTest` **6** ·
> `TaskApiMvcPipelineTest` **20** · `TaskApiPathNormalizationControlTest` **2** · `TaskApiPathNormalizationRealTomcatTest` **3** ·
> `TaskApiWiringTest` **5** · `GrpcStatusHttpMappingTest` **23** · `GetTaskReqParsingTest` **23** · `TaskGetServiceTest` **7**
> ⇒ **合计 135 条 / 0 failures / 0 errors / 0 skipped**（task-api）。
>
> ⚠️ **若将来 task-api 真踩坑**（例如真 Tomcat 与 MockMvc 观测面再次分叉、`TaskApiFailurePathMatrixTest` 的端点×失败维度矩阵出现空洞）⇒ **在本文件登记一条 `PIT-TASK-NN`**。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/api.md`](../../DRIFT/task/api.md)
