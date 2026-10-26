# L13 · `align/global-rpcboot-b` —— message-rpc / task-rpc 的**生产启动阻塞** + **产物不可执行**

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。
> ⚠️ 本 lane **不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写（生产配置）** | `service/message/message-rpc/src/main/resources/application.yml` |
| | `service/task/task-rpc/src/main/resources/application.yml`（**若需**）· `service/task/task-rpc/pom.xml` |
| **可写（守门测试）** | `service/message/message-rpc/src/test/**` · `service/task/task-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L13-rpcboot-message-task.md` |
| **只读** | `service/favorite/**`（⛔ 零写权）· `service/common/**` · `tools/difftest/**`（L15 拥有）· `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` |

**分支**：`align/global-rpcboot-b` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-rpcboot-b` · **基线**：`align/global` HEAD

---

## 一、目标

修 **2 个 rpc 模块**的生产阻塞，其中 task-rpc 还有一条**产物层**的缺陷。

| # | 模块 | 缺陷 | 落点 |
|---|---|---|---|
| ① | **message-rpc** | 🔴 `discovery://user-rpc` / `discovery://admin-rpc` **两斜杠** ×2 | `application.yml:148,153` |
| ② | **task-rpc** | 🔴 `grpc-inprocess` 是 `<scope>test</scope>` | `pom.xml:180-185` |
| ③ | **task-rpc** | 🔴 **jar 产物不可执行**（**没有 `Main-Class`** ⇒ `java -jar` 起不了） | `pom.xml` 缺 `spring-boot-maven-plugin` |

⚠️ **③ 是本轮新挖出的**（L11 只报了 task-rpc 的这一条，**漏报了 hot-rpc 的同款** ⇒ hot-rpc 由 **L14** 负责）。
⚠️ **hot-rpc 也有同款 ③** ⇒ **L13 不许碰 hot-rpc**（按模块零重叠）。

---

## 二、🔴 缺陷形态

### 2.1 两斜杠（message-rpc）

同 L12 §2.1 ⇒ **判据出处**：`net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.java`
的 `newNameResolver`（取 `targetUri.getPath()`，要求非 null / 长度 > 1 / 以 "/" 开头）。
⇒ `discovery://user-rpc` ⇒ `path=""` ⇒ 抛 `IllegalArgumentException` ⇒ **与 Nacos 在不在线无关** ⇒
**真 fat jar 起不来**（`grpcClientBeanPostProcessor` 先炸 ⇒ 连 Flyway 都跑不到）。

⚠️ **message-rpc 的特殊性**：它的**两个** client（`user-rpc` / `admin-rpc`）**都是被依赖方**，
而 RAG 侧的 `users` 域有 `user-rpc` + `admin-rpc` 两个进程 ⇒ 改地址**不影响**服务名（`user-rpc` / `admin-rpc` 保持不变）。

### 2.2 `grpc-inprocess` test scope（task-rpc）

同 L12 §2.2 ⇒ **修法 = 去掉 `<scope>test</scope>`**，`<version>${grpc.version}</version>` **必须保留**
（根 pom 的 `dependencyManagement` 不管 `grpc-inprocess`）。

### 2.3 🔴 jar 产物没有 `Main-Class`（task-rpc）

**实测证据**（第 27 轮后本工作树的构建产物）：

```
service/task/task-rpc/target/task-rpc-0.0.1-SNAPSHOT.jar | 83,827 B | <<无 Main-Class>>
service/message/message-rpc/target/message-rpc-0.0.1-SNAPSHOT.jar | 69,793,492 B | Main-Class: org.springframework.boot.loader.launch.JarLauncher
```

⇒ task-rpc 打出的是 **library jar**（84 KB）而不是 **Spring Boot fat jar**（70 MB）
⇒ `java -jar task-rpc.jar` 直接报「**中没有主清单属性**」（no main manifest attribute）
⇒ **该模块从来没有被打成过可部署产物**。

**根因**：`task-rpc/pom.xml` 的 `<build>` 段**只有** `maven-surefire-plugin`（Ryuk 关闭配置），
**没有** `spring-boot-maven-plugin` ⇒ 没有 `repackage` goal。

⚠️ **修法**：照 `message-rpc/pom.xml:230-235` 的写法，在 task-rpc 的 `<build><plugins>` 里
**加** `spring-boot-maven-plugin`（**不写 `<mainClass>`** —— 与 message-rpc 一致，让它自动探测
`TaskRpcApplication`）：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

⚠️ **必须保留**已有的 `maven-surefire-plugin` 段（Ryuk 关闭配置，**别删** —— 删了容器测试会挂 4.5 分钟）。

---

## 三、🔴 守门要求

### 3.1 `MessageRpcProductionAddressTest`（照抄 L12 的样板）

- **message-rpc 有两个 client**（`user-rpc` / `admin-rpc`）⇒ 守门**必须两个都查**（缺一个 = 漏检）。
- 期望：`discovery:///user-rpc` / `discovery:///admin-rpc`。
- **必须真的调 devh 的 `newNameResolver`**（`assertDoesNotThrow` + 两斜杠 `assertThrows` ⇒ 证明非恒真）。
- **必须交叉核对 `target/classes` 里打包的副本**。

⚠️ **task-rpc 没有 `grpc.client` 段**（实测：`service/task/task-rpc/src/main/resources/application.yml` 里**零** `address: discovery`）
⇒ **task-rpc 不需要 `ProductionAddressTest`** ⇒ **别造一个断言不存在配置的测试**。

### 3.2 task-rpc 的**产物可执行性**守门

- **不能只看 pom 里有没有 `spring-boot-maven-plugin`**（§3.3：「注解/配置存在」≠「机制生效」）⇒
  **必须解包真实产物**看 `META-INF/MANIFEST.MF` 的 `Main-Class`。
- ⚠️ `tools/difftest/check-jar-executable.sh` **目前只扫 `*-api`** ⇒ **rpc 模块从来没有被它查过**
  ⇒ **本 lane 不许改那个脚本**（L15 拥有）⇒ **本 lane 自建一个 task-rpc 专属的产物守门**，
  并在台账里写明「**该缺口由 L15 统一补进 `check-jar-executable.sh`**」。

### 3.3 真启动守门

- `grpc-inprocess` 的问题**只在 fat jar 里才暴露** ⇒ 真启动守门**必须用 fat jar**。
- ⚠️ task-rpc 的 fat jar **修好之前**根本打不出来 ⇒ 修完才能起 ⇒ **顺序：先修 pom，再跑真启动**。

### 3.4 🔴 变异测试（**必须**）

| 变异 | 预期红在哪 |
|---|---|
| **M1** message-rpc 的 `discovery:///user-rpc` 改回 `discovery://user-rpc` | message-rpc 的 `ProductionAddressTest` 变红 |
| **M2** task-rpc 的 `grpc-inprocess` 加回 `<scope>test</scope>` | task-rpc 的真启动守门变红（`NoClassDefFoundError`） |
| **M3** task-rpc 的 pom 去掉 `spring-boot-maven-plugin` | task-rpc 的产物守门变红（**无 `Main-Class`**） |

⚠️ **PIT-GLOBAL-018 三道闸**（删旧 XML / 看 maven 退出码 / grep `COMPILATION ERROR`）
⚠️ **PIT-GLOBAL-021**：判定**只认 surefire XML**。
⚠️ **PIT-GLOBAL-020**：变异**只在 lane 自己的工作树**跑；提交前 `git diff` 逐个确认已还原。

---

## 四、验收标准

- [ ] message-rpc 2 处 yaml 改完 ⇒ **该模块零两斜杠**
- [ ] task-rpc 的 `grpc-inprocess` 去 test scope
- [ ] task-rpc 的 pom 补上 `spring-boot-maven-plugin`（**保留** surefire 段）
- [ ] `MessageRpcProductionAddressTest` 落地，**覆盖两个 client** ⇒ 全绿
- [ ] task-rpc 的**产物守门**落地（真解包 MANIFEST）⇒ 全绿
- [ ] task-rpc 的**真启动**守门落地（fat jar）⇒ 全绿
- [ ] 变异 **M1/M2/M3 全部变红且红在目标断言上**（贴 surefire XML 证据）
- [ ] `service/favorite/**` **零改动**
- [ ] 两个模块全量测试仍绿
- [ ] 台账（本文件）写完

## 五、🔴 纪律提醒

- **Go 是唯一真源**：改地址**不改变服务名** ⇒ 这不是「与 Go 对齐」，是「RAG 侧写法非法导致起不来」的修复。
- **§3.3.2**：断言里若出现「Go: …」措辞，**必须**带 `文件:行号`；否则改成中性描述。
- **不要 `git add -A`** ⇒ 逐个 `git add` 白名单里的文件。
- ⚠️ **PIT-GLOBAL-019**：改了上游模块的源码 ⇒ 必须把上游**拉进同一 reactor**（`-am`）。

---

## 七、✅ 完成态（2026-10-26 · 由整合者收尾）

> ⚠️ **本 lane 的子进程在收尾阶段被取消**（`runtime-shutdown`）⇒
> **生产改动与两个守门类已完整落地**（未提交）⇒ **由整合者接手**：跑守门 + 做 M1/M2/M3 三条变异 + 补写本节 + 提交。

### 7.1 缺陷与修法

| # | 缺陷 | 修法 | 证据 |
|---|---|---|---|
| ① | 🔴 `message-rpc` 的 2 处 `discovery://user-rpc` / `discovery://admin-rpc` **两斜杠** | → `discovery:///user-rpc` / `discovery:///admin-rpc`（**服务名一个字没改**） | `service/message/message-rpc/src/main/resources/application.yml:164,171` |
| ② | 🔴 `task-rpc` 的 `io.grpc:grpc-inprocess` 是 `<scope>test</scope>` ⇒ 运行时 classpath 上整个消失 | 去掉 `<scope>test</scope>`（**保留** `<version>${grpc.version}</version>`） | `service/task/task-rpc/pom.xml` |
| ③ | 🔴 `task-rpc` 的 jar **没有 `Main-Class`** ⇒ `java -jar` 起不了 | `<build><plugins>` 里加 `spring-boot-maven-plugin`（**不写 `<mainClass>`** ⇒ 自动探测 `TaskRpcApplication`），**保留** surefire 段 | `service/task/task-rpc/pom.xml` |

**修前 / 修后实测（整合者独立复核）**：

```
task-rpc 修前： 83,827 B · 零个 Main-Class
task-rpc 修后：100,871,012 B
        Main-Class: org.springframework.boot.loader.launch.JarLauncher
        Start-Class: com.rag.task.task.rpc.TaskRpcApplication
        BOOT-INF/lib/grpc-inprocess-1.63.0.jar   ← 修前不在
```

### 7.2 🟢 一个**关键区分**（L13 子进程自己查出来的，比任务书的假设更准）

任务书假设 `task-rpc` 与 `article-rpc`/`like-rpc` 是**同款**缺陷（client 侧 `InProcessChannelFactory` 链接失败）。
**L13 实测发现两者消费点不同**：

- `article-rpc` / `like-rpc` ⇒ 有 **client** starter ⇒ 消费点是 devh client 的
  `InProcessChannelFactory` ⇒ 硬引用 `io.grpc.inprocess.InProcessChannelBuilder`
  ⇒ 报 `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`
- `task-rpc` ⇒ **只有 server starter，没有 client starter** ⇒ 消费点是
  `GrpcServerFactoryAutoConfiguration` 的 `InProcessGrpcServerFactory`
  ⇒ 硬引用 `io.grpc.inprocess.InProcessServerBuilder`（`javap` 实测 4 处引用）
  ⇒ 报 `NoClassDefFoundError: io/grpc/inprocess/InProcessServerBuilder`

⇒ **根因相同（Maven「最近者优先」），但报的类名与消费点不同** ⇒ 台账与注释必须分开写，
**不能照抄 client 侧的类名**。⇒ L12 与 L13 的 pom 注释各自独立取证，**没有互相照抄**。

### 7.3 🔴 守门（2 个类 / 8 条用例）

| 类 | 阶段 | 断言 |
|---|---|---|
| `MessageRpcProductionAddressTest`（4 条） | **test** | ① `grpc.client.user-rpc.address` 必须是 `discovery:///user-rpc` ② `grpc.client.admin-rpc.address` 必须是 `discovery:///admin-rpc` ③ 服务名 == `path.substring(1)` ④ **真的调 devh 的 `newNameResolver`**：生产地址不抛 + 两斜杠**必须**抛 `IllegalArgumentException`（⇒ 证明守门非恒真）⑤ 🟢 **额外一条** `noTwoSlashDiscoveryAddressRemains` —— 扫全树防**新增**两斜杠 |
| `TaskRpcFatJarArtifactGateIT`（4 条） | **verify**（package 之后） | ① `Main-Class` == `org.springframework.boot.loader.launch.JarLauncher` ② `Start-Class` == `com.rag.task.task.rpc.TaskRpcApplication` ③ `BOOT-INF/lib/` 里有 `grpc-inprocess` ④ 体积 ≫ library jar |

⚠️ **产物层守门绑在 `verify`**：surefire 的 `test` 阶段跑在 `package` **之前** ⇒ 那一刻 jar 还不存在。
⇒ pom 里加**第二个 surefire execution**（`<id>artifact-gate</id>` · `<phase>verify</phase>`）
⇒ 报告仍落在 `target/surefire-reports/`（与本仓判定口径一致，PIT-GLOBAL-021）。
⚠️ **failsafe 3.5.6 不在本地 m2**（只有 3.5.4）⇒ **离线不可用** ⇒ 改用第二个 surefire execution。

### 7.4 🔴 变异（3 条，全部红在目标断言上 · 整合者亲跑）

| 变异 | 预期 | 实际 |
|---|---|---|
| **M1** message-rpc 的 `user-rpc` 地址改回两斜杠 | `MessageRpcProductionAddressTest` 红 | ✅ **4/4 全红**（含 `noTwoSlashDiscoveryAddressRemains`）· maven EXIT=1 |
| **M2** task-rpc 的 `grpc-inprocess` 加回 `<scope>test</scope>` | 产物守门的「`BOOT-INF/lib/` 有 grpc-inprocess」红 | ✅ **红在目标断言**（其余 3 条绿）· maven EXIT=1 ⇒ 独立确认：变异态产物里 `grpc-inprocess` **确实从 `BOOT-INF/lib/` 消失** |
| **M3** task-rpc 的 pom 去掉 `spring-boot-maven-plugin` | 产物守门的「`Main-Class`」红 | ✅ **4/4 全红**（`Main-Class` / `Start-Class` / 体积 / grpc-inprocess 四条一起红）· maven EXIT=1 ⇒ 独立确认：产物 85,903 B 且**无 `Main-Class`** |

⇒ **还原后**：message-rpc 守门 4/4 绿 · task-rpc 守门 4/4 绿 · 产物 100,871,012 B。

### 7.5 🟥 整合者发现并修的一处**守门自身**的隐患（与 L14 同族）

L13 子进程在写的过程中**自己发现**了这个问题并修了（值得记录）：
**守门里断言的字符串，可能同时出现在被测文件的注释里** ⇒ 断言会**恒真**。
⇒ 它的 `noTwoSlashDiscoveryAddressRemains` 因此改成了**先剥 YAML 注释**再扫。

⚠️ **同类隐患在 L14 也真实发生了**（`pomMustDeclareSpringBootMavenPlugin` 没剥 XML 注释
⇒ M1 变异时它**居然是绿的**）⇒ **整合者在 L14 修掉了**（见 `L14-rpcboot-hot.md` §8.3.1）。
⇒ 教训 ⇒ `docs/Pitfalls/global/`（**PIT-GLOBAL-031**）。

### 7.6 交整合者

1. ✅ `check-jar-executable.sh` 纳入 rpc —— **L15 已做**（合回 `ae53f56`）⇒ 本 lane 修完后该脚本对 task-rpc 转绿。
2. ✅ baseline 里的 10 条清理 —— **待整合者做**（依赖 L12/L13 合回）。
3. ⚠️ `TaskRpcFatJarArtifactGateIT` 绑在 `verify` 阶段 ⇒ **只跑 `mvn test` 的 CI 不会执行它** ⇒ CI 需跑 `verify`。
4. ⚠️ **进程级真启动守门未做**（按整合者的收敛指令放弃）⇒ L13 子进程**已实测过**修好的 task-rpc fat jar
   **能起来**（Kafka group 已加入 ⇒ 上下文真的启动了）⇒ 该形态**已被人工实测覆盖**，但**没有自动守门** ⇒ 登记为缺口。
