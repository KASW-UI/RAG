# L14 · `align/global-rpcboot-hot` —— hot-rpc 的**产物不可执行**（L11 漏报的那一条）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。
> ⚠️ 本 lane **不产出「要用户拍板」的问题**。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写（生产配置）** | `service/hot/hot-rpc/pom.xml` |
| **可写（守门测试）** | `service/hot/hot-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L14-rpcboot-hot.md` |
| **只读** | `service/favorite/**`（⛔ 零写权）· `service/common/**` · `tools/difftest/**`（L15 拥有）· `service/task/**` · `service/message/**` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` |

**分支**：`align/global-rpcboot-hot` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-rpcboot-hot` · **基线**：`align/global` HEAD

---

## 一、目标

修 **hot-rpc 的产物层缺陷**：jar 打出来是 **library jar**（59 KB，无 `Main-Class`），不是可部署的 Spring Boot fat jar。

| # | 缺陷 | 落点 |
|---|---|---|
| ① | 🔴 **jar 产物不可执行** ⇒ `java -jar hot-rpc.jar` 报「中没有主清单属性」 | `service/hot/hot-rpc/pom.xml` 的 `<build>` 段 |

---

## 二、🔴 这条为什么是**本轮新发现**（**台账订正，必须写**）

### 2.1 L11 的漏报

L11 lane（第二十七轮）在 `docs/DRIFT/global/lanes/L11-flywaydeep.md` 里把「`task-rpc` 的 jar 没有 `Main-Class`」
记为 **N8**，标 🔴「重大新发现」。

**但 L11 只报了 10 个 rpc 模块里的 1 个。** 逐个解包实测（第 27 轮后本工作树的构建产物）：

```
article-rpc   | 104,264,894 B | Main-Class: ...JarLauncher
comment-rpc   | 104,066,957 B | Main-Class: ...JarLauncher
favorite-rpc  |  66,468,716 B | Main-Class: ...JarLauncher
follow-rpc    |  63,967,449 B | Main-Class: ...JarLauncher
hot-rpc       |      58,873 B | <<无 Main-Class>>      ← L11 没报
like-rpc      |  99,855,826 B | Main-Class: ...JarLauncher
message-rpc   |  69,793,492 B | Main-Class: ...JarLauncher
task-rpc      |      83,827 B | <<无 Main-Class>>      ← L11 报了
admin-rpc     |  83,175,778 B | Main-Class: ...JarLauncher
user-rpc      |  83,147,447 B | Main-Class: ...JarLauncher
```

⇒ **实际是 2 个模块**（hot-rpc + task-rpc），L11 漏了 **hot-rpc**。

### 2.2 L11 为什么会漏（**根因 = 守门缺口**，不是手滑）

`tools/difftest/check-jar-executable.sh` 的模块枚举是：

```bash
ls -d service/*/*-api service/users/*/*-api 2>/dev/null | sort
```

⇒ **只扫 `*-api`** ⇒ **10 个 rpc 模块的产物可执行性从来没有被任何守门查过**
⇒ L11 是**手工**逐个看 pom 才发现 task-rpc ⇒ 手工必漏。

⇒ **本轮的系统性修法**：L15 把 rpc 模块纳入 `check-jar-executable.sh` 的枚举
⇒ **本 lane 只负责 hot-rpc 这个模块本身**，并把「漏报」这件事写进台账。

⚠️ **hot-rpc 的 yaml 里没有 `grpc.client` 段**（实测：`service/hot/hot-rpc/src/main/resources/application.yml`
里**零** `address: discovery`）⇒ **不需要 `ProductionAddressTest`** ⇒ 别造一个断言不存在配置的测试。

---

## 三、🔴 修法

在 `service/hot/hot-rpc/pom.xml` 的 `<build><plugins>` 里**加**：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

- **不写 `<mainClass>`** ⇒ 自动探测 `HotRpcApplication`
  （`service/hot/hot-rpc/src/main/java/com/rag/hot/hot/rpc/HotRpcApplication.java:69`）。
- ⚠️ **必须保留**已有的 `maven-surefire-plugin` 段（Ryuk 关闭配置 + 那段长注释，
  在 `pom.xml:206-244`）⇒ **别删**。
- ⚠️ **`service/common` 不能加 `spring-boot-maven-plugin`** —— 它是 **library** 模块（被 10 个微服务依赖），
  给它 repackage 会**污染下游依赖**（下游会拿到一个 fat jar）⇒ **本条不是本 lane 的范围**，写进台账交整合者。

---

## 四、🔴 守门要求

| # | 要求 | 为什么 |
|---|---|---|
| ① | 🔴 **必须解包真实产物**看 `META-INF/MANIFEST.MF` 的 `Main-Class` | §3.3：「pom 里有 plugin」**不等于**「产物可执行」⇒ 必须验**产物** |
| ② | 🔴 **必须断言 `Main-Class` 是 `JarLauncher` 且产物是 fat jar**（体积 ≫ library jar） | 只断言「有 Main-Class」会放过「一个手写的空 Main-Class」 |
| ③ | ⚠️ **不能用 `mvn package` 成功当证据** | 「package 成功」证明不了 jar 能跑（§3.3）⇒ 必须解包 |
| ④ | 🔴 变异测试 | 去掉 `spring-boot-maven-plugin` ⇒ 守门必须变红 |

⚠️ **本 lane 不许改 `tools/difftest/check-jar-executable.sh`**（L15 拥有）⇒
**自建一个 hot-rpc 专属的产物守门测试**，并在台账里写明「**该缺口由 L15 统一补进脚本**」。

### 变异（**必须**）

| 变异 | 预期红在哪 |
|---|---|
| **M1** 把 hot-rpc pom 里的 `spring-boot-maven-plugin` 删掉 | hot-rpc 的产物守门变红（**无 `Main-Class`**） |

⚠️ **PIT-GLOBAL-018 三道闸** / **PIT-GLOBAL-021 只认 surefire XML** / **PIT-GLOBAL-020 变异只在 lane 工作树**。

---

## 五、验收标准

- [ ] hot-rpc pom 补上 `spring-boot-maven-plugin`（**保留** surefire 段）
- [ ] 重新 `mvn -o package -pl service/hot/hot-rpc -am` ⇒ 产物 **fat jar** + `Main-Class: ...JarLauncher`
- [ ] hot-rpc 的产物守门落地并全绿
- [ ] 变异 **M1 变红且红在目标断言上**（贴 surefire XML 证据）
- [ ] hot-rpc 全量测试仍绿
- [ ] `service/favorite/**` **零改动**
- [ ] 台账（本文件）写完：**必须写明「N8 实际是 2 个模块，L11 漏报了 hot-rpc」+ 根因（守门只扫 api）**

## 六、🔴 纪律提醒

- **不要 `git add -A`** ⇒ 逐个 `git add` 白名单里的文件。
- ⚠️ **PIT-GLOBAL-019**：改了上游 ⇒ 拉进同一 reactor（`-am`）。
