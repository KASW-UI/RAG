# MappingTable/README.MD — 目录元结构

> **本目录的目的**：把 `Sea-RideTheWind_golang`（A 列 Go 真理之源）映射到 `Sea-RideTheWind_java`（B 列 1:1 Go 镜像）再映射到 `RAG`（C 列 Spring idiom 目标架构）。
> B 与 C 是同一个仓库树 `service/users/...`，但包结构、框架依赖、Spring 抽象层不同。

---

## 目录该记什么（5 个维度）

每个子文档（`users.md` 之类的子系统文件）应按这 5 节组织：

| 节 | 记什么 | 为什么 |
|---|---|---|
| 1. 包结构对照 | A/B/C 三列文件清单 + 映射类型（1:1 / 1:N / N:1） + 备注 | 翻某个文件时一眼能找到三方对应 |
| 2. 持久层差异 | ORM 注解 / Repository vs Mapper / Schema 演进 / 事务 | 面试 JPA vs MyBatis-Plus 时直接调 |
| 3. HTTP / DI 差异 | 路由注册 / Handler 风格 / Logic 中间层 / 双返回 / 配置 | 面试 Spring MVC vs go-zero 时直接调 |
| 4. 公共模块对照 | ErrorCode / Response / JwtUtil / BusinessException | 跨子系统共享的代码在哪 |
| 5. 决策锁 | 每节末尾一段"N 处同时改 + 为什么换" | 复刻到下一个子系统时按这个清单改，不漏 |

每个子文档末尾必须有 **跨模块全局约定** 表（proto 字段号 / 错误码 / JWT claims / 表名 / HTTP path）——这五项所有子系统共享，不会因为改子系统而漂移。

---

## 形式约定（写新子文档时按这个格式）

### 表头：5 列

| A (Go 真理之源) | B (Java, 类 Go 文件树) | C (Java, Spring Boot 文件树) | 对应关系 | 备注/差异 |
|---|---|---|---|---|
| `service/user/user/rpc/internal/logic/loginlogic.go` | `service/users/user/user-rpc/src/main/java/.../internal/logic/LoginLogic.java` | `service/users/user/user-rpc/src/main/java/.../service/UserDomainService.java`（9 方法合 1） | N:1 | B 1 个文件 1 个 case；C 多个 case 合到 1 个 DomainService |

### 映射类型（固定 3 种，不发明新词）

- **1:1** —— 一个 A 文件对应一个 B 文件对应一个 C 文件
- **1:N** —— 一个 A 文件拆成 N 个 C 文件（B 通常保持 1 个或也拆开）
- **N:1** —— 多个 A 文件合到 1 个 C 文件（B 通常已经是 1 个）

如果出现"一对多交叉"（A 一个 → B 一个 + C 一个，但 C 内部 N 个），按 C 实际文件数标 1:N，并在备注里写明。

### 决策锁格式（每节末尾固定 2 段）

```text
**换 XXX（N 处同时改）**：
1. 第一处改什么
2. 第二处改什么
3. ...

**为什么换**：一句话讲清与 Go 原版形态对比的关键差异。
```

例：

```text
**换持久层（JPA → MyBatis-Plus）4 处同时改**：
1. pom 删 spring-boot-starter-data-jpa + 加 mybatis-plus-bom 3.5.14 + mybatis-plus-boot-starter + flyway-core
2. Entity 换注解：@Entity @Id @GeneratedValue → @TableName("xxx") @TableId(type=IdType.ASSIGN_ID)
3. Repository 接口删，新建 Mapper extends BaseMapper<Entity>；调用点 repo.findById(...) → mapper.selectById(...)
4. Schema 来源：JPA ddl-auto=update 隐式建表 → Flyway V1/V2/V3 显式迁移

**为什么换**：JPA 隐式 DDL 不适合多人多环境；MyBatis-Plus LambdaQueryWrapper 比 Spring Data 方法名派生更直观；Flyway 显式管 schema 历史。
```

---

## 文件命名约定

每个 Java 子系统一个文件：

| 子系统 | 文件 |
|---|---|
| users（user-rpc / user-api / admin-rpc / admin-api） | `users.md` |
| task | `task.md`（待写） |
| article | `article.md`（待写） |
| favorite | `favorite.md`（待写） |
| follow | `follow.md`（待写） |
| hot | `hot.md`（待写） |
| points | `points.md`（待写） |
| message | `message.md`（待写） |
| like | `like.md`（待写） |
| security | `security.md`（待写） |

子系统按 AGENTS.md 第 7 节"翻版工作流"推进，每完成一个 Phase 在对应的 `<子系统>.md` 加 1 节。

---

## 阅读路径

1. **第一次看**：`users.md`（已写 223 行）—— 用户域是 5 个 Phase 全部完成的最大子系统，覆盖最全
2. **写新子系统**：抄 `users.md` 的 5 节模板，按目标子系统的 A/B/C 实际文件填
3. **面试复述**：从 `users.md` 末尾"一句话总结"开始，按层说"持久层 JPA 换 MyBatis-Plus / DI 容器 static 换 Bean / Logic 中间层删除 / ApiTypes 单文件拆 DTO / Filter 合并 Interceptor"

---

## 一句话总览

`Sea-RideTheWind_java` = Go 镜像 + JPA + static ServiceContext + Logic 中间层 + ApiTypes 单文件 + 2 个 Filter；
`RAG` = 同名 Java 包 + MyBatis-Plus + Flyway + 全 Spring Bean + Controller 直接调 Service + 每 case 一 DTO + 1 个 Interceptor。
proto 字段号 / 错误码 / JWT claims / 表名 / HTTP path 全部 1:1 复刻 Go，不偏离。