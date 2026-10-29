<!-- @new 2026-10-23 · align/common 工作树产出 · 基线 commit 8422116 -->

# 保护层在测试里「按 CPU 关门」：并行构建会造出看起来像回归的假失败

> **产出方**：`align/common` 工作树，基线 commit **`8422116`**。
> 相关漂移：见 [`../../DRIFT/common/alignment-2026-10-23.md`](../../DRIFT/common/alignment-2026-10-23.md)。

---

## PIT-COMMON-13 · 保护层在测试里「按 CPU 关门」：并行构建会造出看起来像回归的假失败  ·  🔴 **仍生效**  ·  核对日期 **2026-10-29**  ·  本轮影响：**否**

> **2026-10-29 对齐**：🔴 **仍生效**（核对日期 **2026-10-29**）· **本轮影响：否** —— 本轮**不跑并行构建** ⇒ 「保护层按 CPU 关门 ⇒ 造出假失败」本轮不触发。
> **旧号**：`（本文件原无编号 · 单篇记录）` ⇒ **新号**：`PIT-COMMON-13`（本域统一编号 `PIT-COMMON-<两位序号>`；**旧号原样保留**）。
> **证据**：`service/common/src/main/java/com/rag/common/protection/GoStyleCpuSheddingFilter.java`（机制仍在 ⇒ **触发条件仍在**）＋ 本条 §现象（`mvn -o -T1C test` 并行 + 全量 testcontainers 真 PG ⇒ `UserApiGoStyleBodyContentTypeTest` 报 503 假失败）
## 现象

全仓 `mvn -o -T1C test`（并行 + 全量 testcontainers 真 PG）跑出**一条**失败：

```
service/users/user/user-api
  UserApiGoStyleBodyContentTypeTest.zeroFieldDtoSucceedsWithNonJsonContentType
  Status expected:<200> but was:<503>
```

该用例与本轮任何改动**毫无关系**（改的是 gRPC 拦截器超时 + communityfact 两个纯函数/异常判定）。
单跑同一个测试类：**6 tests / 0 failures / BUILD SUCCESS**。

## 根因

`GoStyleCpuSheddingFilter` 的 `doFilter` 里（**基线 `8422116` 的第 92-96 行**）：

```java
if (!shedder.allow()) {
    // sheddinghandler.go:29-35
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);   // ← 503
    return;
}
```

> ⚠️ **行号会移位**：`align/users-user` 给这个文件加了 20 行 ⇒ 合并后同一段落在 **112-116 行**。
> ⇒ 引用时**同时给出方法名**（`GoStyleCpuSheddingFilter#doFilter`）与基线行号，不要只给行号。

这是**忠实复刻** go-zero `rest` 的 CPU shedding（`GoStyleCpuSheddingFilter` 类 javadoc
直接引了 Go 侧 `promise, err := shedder.Allow()` → `w.WriteHeader(http.StatusServiceUnavailable)`）。
**它按真实 CPU 占用决策** ⇒ 构建机越忙，越容易关门。

`-T1C` 会把 CPU 跑满（编译 + surefire + testcontainers 容器同时抢核）⇒ 关门概率上升。

## 为什么这条特别容易误判

1. 失败点在 **`user-api`**，而本轮改的是 `service/common` 的 gRPC / communityfact ⇒ 看起来像「改坏了别人」。
2. 失败形态是 **503**，而本轮另一个真实缺陷（DRIFT-COMMON-003）恰好也是「404 误降级成 503」⇒ 极易串味。
3. reactor 报 **BUILD FAILURE** 并跳过后续模块（`comment-api` / `comment-rpc` 直接 `SKIPPED`），
   ⇒ 一次负载抖动会让「本来要跑的模块」根本没跑，看起来像通过。

## 纪律

> **并行构建下的 HTTP 层断言失败，先单独复跑再判定。**
>
> 判定顺序：
> 1. 单独复跑该测试类 —— 转绿 ⇒ 负载问题，不是回归；
> 2. 若确认为负载问题，**降并行度重跑全量**（本轮改用 `-T2`）拿干净结论；
> 3. **不要**因为「单跑绿了」就跳过全量 —— 被 reactor 跳过的模块仍然需要一次完整执行。

⚠️ 附带一条：这类「负载敏感」的保护层测试，**在 CI 上会比本地更不稳定**。
若将来要接入持续集成，应给 CPU shedding 的阈值提供测试可覆盖的注入点，
否则每逢 CI 机器繁忙都会看到随机红灯 —— 而红灯会训练团队忽略红灯。

---

## 与本轮其它坑的关系

- [./observability.md](./observability.md) 坑 1：**同一份代码两个环境两种可见性**（单测看得见 KV，生产看不见）。
- [./observability.md](./observability.md) 坑 2：**「断言 Bean 存在」被当成「机制生效」**。
- 本条：**「保护层按真实 CPU 决策」让测试结果依赖机器负载**。

三条合起来指向同一个元教训：
**测试通过与否，取决于「在什么环境下跑」，而不只是「代码写了什么」。**
判读任何红灯之前，先问三句：在哪个环境跑的？跑了几遍？被跳过几个？