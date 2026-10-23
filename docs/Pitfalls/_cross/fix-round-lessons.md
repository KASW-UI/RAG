<!-- @move AGENTS.md §「2026-10-23 修复轮」 · 本轮最值得记住的 4 条（元教训） -->

# 修复轮元教训：4 条最值得记住的（2026-10-23）

> **这 4 条都是「写测试写出来的」，不是审出来的。**

---

🔴 **本轮最值得记住的 4 条**（都是「写测试写出来的」，不是审出来的）：

1. **`<where>` 元素在已有 `WHERE` 的基础 SQL 上会生成两个 `WHERE`** ⇒ PG 语法错。
   且它**被更早的一道错误挡在后面**（参数名 typo 在解析期就抛 `BindingException`）——
   ⇒ **修掉外层错误会露出内层错误**，一轮只能走一层。
2. **MP 的 `strictUpdateFill` 在读-改-写路径上永远不生效**：
   `strictFillStrategy` 的消费点是 `if (metaObject.getValue(fieldName) == null)`，
   而实体是从库里读出来的 ⇒ 非 null ⇒ 跳过。**与 GORM 的 `== nil` 看着同款、语义不同**
   （GORM 问「本次调用有没有赋值」，MP 问「字段现在是不是空」）。
3. **同一个 GORM 语义在两个模块需要两种 Java 修法**：
   `updateById(entity)` 形态 → `MetaObjectHandler`；`update(null, wrapper)` 形态 → `setSql`
   （后者实体为 null，**handler 根本不会被调用**）。判断依据是**写路径的实体形态**，不是「哪个更地道」。
4. **一条绿测试把 `update_time` 不推进锁死了**（`AdminSemanticDriftRegressionTest`）。
   修漂移时它会**先红**，容易让人以为修复方向错了 ⇒ 严格执行 §3.3.2「**先改错误断言，再改实现**」。
