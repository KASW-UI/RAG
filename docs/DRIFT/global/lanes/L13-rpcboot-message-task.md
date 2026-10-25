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
