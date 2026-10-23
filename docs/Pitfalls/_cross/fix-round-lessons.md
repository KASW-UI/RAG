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

---

## 第二轮元教训（2026-10-23 · HIGH 批次）

**同样是「写测试写出来的」，但这 4 条与第一轮的 4 条<b>不重叠</b>。**

5. 🔴 **同一个字段名，MP lambda 与手写 OGNL 是<b>两套解析规则</b>**
   （由 `DRIFT-MSG-V-017` 的连带修复推出）
   - `E::isRead`（MP lambda）→ 按 `TableInfo.propertyMap`，key = **字段名**
   - `#{isRead}`（手写 `@Insert`）→ 按 **JavaBeans 属性名**（由 getter 定）
   - 把字段从 `isRead` 改成 `read` 修好 lambda，**同时打挂了 5 条既有测试**
     （`ReflectionException: There is no getter for property named 'isRead'`）。
   ⇒ **改字段名后必须跑全量，不能只跑新写的守门测试** —— 那 5 条红只有全量才看得见。

6. 🔴 **字段初始化值（`= 0L`）+ `callSettersOnNulls` 默认 false ⇒ NULL 读出来是 `0` 而不是 `null`**
   （由 `DRIFT-USERRPC-V-001` 推出，**台账原本只记到了分支那一层**）
   - MyBatis `DefaultResultSetHandler` 遇 NULL **跳过 setter**
     ⇒ 字段保持初始化值。
   - 后果：`if (x != null && …)` 这类<b>防御写法永远不触发</b>；
     当时 `getUser` 里那段「绝不 fallback 成 0」的注释<b>本身就是死代码</b>。
   ⇒ **写 `!= null` 判据前必须先证明 null 真能到**；
     守门测试里要有一条<b>前提断言</b>（本轮正是那条先红的，直接证伪了原假设）。

7. 🔴 **台账里「Go 侧未实跑」的推断级条目，不能照着去修**
   （由 `DRIFT-ARTICLE-RPC-004` 的复核推出）
   - 该条声称「Go 报错、RAG 静默改」，Go 侧证据栏自己标着**未实跑**。
   - 实测：`finisher_api.go:117-121` 的 0 行回落是 `ON CONFLICT DO UPDATE`（**不报错**），
     且 `callbacks/create.go:366-402` 证明 `deleted_at` 会被写回同一个非零值
     ⇒ **两边都是静默覆盖**。
   - **照台账修（加 `deleted_at IS NULL`）反而会制造一条新漂移**：
     调用方不检查 `RowsAffected` ⇒ 变成「完全不写」，而 Go 是「写了」。
   ⇒ 推断级证据的条目**必须在标题里标注**。**照错误结论修，比不修更糟。**

8. 🔴 **修完只跑新测试是不够的**（第 5 条的方法论面）
   本轮三次「改完即红」都来自**既有测试**：`isRead` 占位符（5 条）、
   硬编码 bcrypt hash 对不上明文、种子里「1 条已读」没算进 UPDATE 后的总数。
   ⇒ 这类红**不是回归**，是守门测试自己写错；
   但**分不清「守门测试写错」与「实现改错」就会去回滚正确修复**。
   判据：看断言消息指向的是「被测代码」还是「测试自身的前置条件」。

### 第一轮与第二轮的关系

两轮共修 8 条（含 1 条撤销、1 条潜伏）。**8 条里有 6 条是「写测试/跑全量撞出来的」，
只有 2 条是审出来的** —— 这个比例本身就是「跑绿 ≠ 对」的最强证据。

---

## 第三轮（DDL 批次）新增元教训

9. 🔴 **「照抄同文件已有模式」不自动成立 —— 外形像 ≠ 语义像**
   （由 `DRIFT-USERRPC-V-004` 推出）

   台账原话是「补一条幂等 ALTER 自愈，**照抄同文件 `:50-52` 的 R3 模式**」。
   那三条 R3 是 `ALTER COLUMN … TYPE text`：varchar 是 text 的**超集**、无 `USING`、
   **不重新解释既有取值** ⇒ 天然幂等。
   照抄到 `timestamp → timestamptz` 就坏了：需要 `USING`，而 `USING` **会重新解释取值**，
   于是「幂等」这个性质整个不成立：

   ```
   timestamptz --AT TIME ZONE 'UTC'--> timestamp(UTC 墙钟)
                --按会话 TimeZone 铸回--> timestamptz(可能被平移)
   ```

   在**无版本账本、每次启动原样重放**的 `SchemaInitializer` 模型下（`SchemaInitializer.java:118-122`），
   会话时区非 UTC 时**每启动一次整列偏移一次，永不收敛**。
   ⇒ 照抄前先问一句：**新语句与被抄语句在「取值是否被重新解释」这一点上同型吗？**

10. 🔴 **无版本账本的 `ScriptUtils` 执行器与 PL/pgSQL 块不兼容**
    （由上一条引出的条件守卫需求推出，**实测炸启动**）

   想要「只在列真是无时区类型时才转」的条件守卫，最自然的写法是 `DO $$ … $$` + `information_schema`。
   **实测直接失败**：
   - `SchemaInitializer` 用 Spring `ScriptUtils` 切分语句，分隔符是 `;`；
   - 反编译 spring-jdbc **7.0.9** 的 `ScriptUtils.class`，常量池里**不存在 `$$`** ⇒
     **没有 PostgreSQL 美元引号支持**，块内分号照样被切断；
   - 结果：DDL 语法错 → `applyAll` 抛异常 → **`users_user` 由 user-rpc 与 admin-rpc 共同建表，
     两个组件双双起不来**，实测 **34 个测试全部红在启动阶段**（不是断言失败）。

   ⇒ **判据**：往 `db/*.sql` 里加东西前，先确认执行器能不能切分它。
   本仓 users 域的 DDL **只能放单条语句**（无块、无 dollar quoting、无存储过程定义）。
   跨语句的条件只能靠 PG 的**函数式语法**或**多条语句 + `SET`/`RESET`** 表达。
   ⇒ 可行的替代：`SET TIME ZONE 'UTC';` 包裹转换 + `RESET TIME ZONE;`，
   让首迁与重放两条路径都恒等，**无需任何条件分支**（并用一条用例钉住 TZ 确实被还原）。

11. 🔴 **台账里的「改动清单」也要复核，不能只复核「结论」**
    （由第 7 条的同源问题推出）

    `_shared.md` 当时列的第 3 部分是「格式化点改发带偏移的 RFC3339，主代码实测只有 3 处：
    `AvatarDomainService`(2)、`UserApiSupport`(1)`。
    复核发现**两处都归错了**：
    - `AvatarDomainService.fromModel` 的形参是 `UserAvatarHistoryEntity`
      ⇒ 格式化的是 **`users_user_avatar_history`**，那份 DDL 本轮**根本没改**；
    - `UserApiSupport:69` 是 proto string → DTO string 的**原样透传**，不是格式化点。

    ⇒ 后果很具体：**`users_user` 的时间值不进任何 wire 字段，本轮改动无可观察差异**，
    是纯 schema / 类型层对齐。若照原清单去改这两处，
    就会造出一个「为 `users_user` 改的」却落在**另一张表**上的提交。
    ⇒ 台账的**证据栏**可信，**行动清单**仍需逐条回到源码核对（§3.3.1 同一纪律）。

### 第三轮与前两轮的关系

本轮修 1 条（含 2 次自查回退）。**值得注意的是：真正拦住事故的不是审，而是跑。**
- 裸 `ALTER` 的重放漂移：写 DDL 时自查发现，但**靠变异测试**才拿到硬证据（差 8 小时）。
- `DO $$` 块：写完第一次跑就炸，34 个测试红在启动阶段 ⇒ 若只跑新测试，很可能带病上线。
