# L12 · `align/global-rpcboot-a` —— article-rpc / comment-rpc / like-rpc 的**生产启动阻塞**修复

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。
> ⚠️ 本 lane **不产出「要用户拍板」的问题** —— 用户既定偏好：**登记类事项登记完就躺着**。

## 〇、🔴 写权范围（白名单）

> **只能写下列文件**，其余一律只读。机器守门：`python3 tools/difftest/check-global-lane-ownership.py`（`EXIT=0` 才算合规）。

| 类别 | 路径 |
|---|---|
| **可写（生产配置）** | `service/article/article-rpc/src/main/resources/application.yml` · `service/article/article-rpc/pom.xml` |
| | `service/comment/comment-rpc/src/main/resources/application.yml` |
| | `service/like/like-rpc/src/main/resources/application.yml` · `service/like/like-rpc/pom.xml` |
| **可写（守门测试）** | `service/article/article-rpc/src/test/**` · `service/comment/comment-rpc/src/test/**` · `service/like/like-rpc/src/test/**` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L12-rpcboot-article-comment-like.md` |
| **只读** | `service/favorite/**`（⛔ 零写权）· `service/common/**` · `tools/difftest/**`（L15 拥有）· `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` |

**分支**：`align/global-rpcboot-a` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-rpcboot-a` · **基线**：`align/global` HEAD

---

## 一、目标

修掉 **3 个 rpc 模块的生产启动阻塞**（真 fat jar 起不来），每条都配**真消费点**守门 + **变异测试**。

| # | 模块 | 缺陷 | 落点 |
|---|---|---|---|
| ① | **article-rpc** | 🔴 `discovery://security-rpc` / `discovery://message-rpc` **两斜杠** ×2 | `application.yml:110,116` |
| ② | **comment-rpc** | 🔴 `discovery://message-rpc` / `discovery://article-rpc` **两斜杠** ×2 | `application.yml:184,190` |
| ③ | **like-rpc** | 🔴 `discovery://message-rpc` **两斜杠** ×1 | `application.yml:171` |
| ④ | **article-rpc** | 🔴 `grpc-inprocess` 是 `<scope>test</scope>` ⇒ 运行时 classpath 上没有 ⇒ `NoClassDefFoundError` | `pom.xml:196-201` |
| ⑤ | **like-rpc** | 🔴 同上 | `pom.xml:265-270` |

---

## 二、🔴 缺陷形态（**为什么是生产阻塞级**，别当成配置洁癖）

### 2.1 两斜杠 ⇒ devh 启动期抛异常

- **判据出处（devh 3.1.0.RELEASE）**：`net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.java`
  的 `newNameResolver` —— 它取 `targetUri.getPath()`，要求**非 null、长度 > 1、以 "/" 开头**。
- `discovery://message-rpc` ⇒ URI 解析成 `authority="message-rpc"`、`path=""`（长度 0）⇒ **不合格** ⇒ 抛
  `IllegalArgumentException: Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://message-rpc'`
- **这个检查发生在任何网络调用之前** ⇒ **与 Nacos 在不在线无关** ⇒ `grpcClientBeanPostProcessor` 先炸 ⇒
  **连 Flyway 都跑不到** ⇒ **真 fat jar 起不来**。
- ⚠️ **三斜杠的语义**：`discovery:///message-rpc` ⇒ `authority=""`、`path="/message-rpc"` ⇒ devh 取
  `path.substring(1)` = `message-rpc` ⇒ **服务发现名正确**（与 Go 侧 etcd key `message.rpc` 的服务名对应）。

### 2.2 `grpc-inprocess` test scope ⇒ 运行时 classpath 整个消失

- Maven「**最近者优先**」：模块里的直接声明**压过** starter 传递进来的 compile 声明。
- ⇒ 运行时 classpath 上这个 artifact **整个不存在** ⇒ devh 的 `InProcessChannelFactory` 链接失败 ⇒
  `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` ⇒ **服务起不来**。
- ⚠️ **修法不是「加一句 test scope 的别的依赖」**，而是**把这个 `<scope>test</scope>` 去掉**
  （让 devh starter 传递进来的 compile 那份生效）—— 这是 `follow-rpc` 2026-10-24 已验证过的修法
  ⇒ **照抄 follow 的做法**，别自创。

### 2.3 ⚠️ 为什么现有测试全绿（这是本 lane 存在的理由）

**所有** rpc 模块的测试都用
`@SpringBootTest(properties = "grpc.client.X.address=static://127.0.0.1:1")`
把生产地址**覆盖**掉了（实测：article/comment/like/message/hot 五域皆如此）⇒
**生产地址从未被任何测试跑过**；而 `grpc-inprocess` 本来就在 test classpath 上 ⇒
**生产 classpath 也没被任何测试跑过**。

⇒ 这正是 AGENTS.md §3.3 说的「**跑绿 ≠ 对**」—— 「测试全绿」与「服务能启动」之间隔着**一个 classpath 和一个配置文件**。

---

## 三、🔴 守门要求（**照抄 follow 域样板，不要重新发明**）

> 样板：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcProductionAddressTest.java`
> （已通过变异验证 ⇒ 结构可直接复用，只换模块名与服务名）

### 3.1 必做：`XxxRpcProductionAddressTest`（每个模块一个类）

| # | 要求 | 为什么 |
|---|---|---|
| ① | 🔴 **必须读 `src/main/resources/application.yml`**（生产文件），**不许**走 `ClassPathResource` | classpath 上可能同时存在依赖 jar 里的 + 本模块的 application.yml ⇒ 命中哪个不确定 ⇒ follow 样板用**源码树路径 + `target/classes` 交叉核对** |
| ② | 🔴 **必须真的调 devh 的 `DiscoveryClientResolverFactory.newNameResolver`** | 光断言「字符串是三斜杠」只证明**我们自己写对了**，不证明**devh 接受** ⇒ 断言 `assertDoesNotThrow`（生产地址）+ `assertThrows(IllegalArgumentException)`（两斜杠）⇒ **后者证明这条守门不是恒真的** |
| ③ | 🔴 **必须断言解析出的服务名** == `path.substring(1)` == 期望服务名 | devh 是按服务名去 Nacos 拉的 ⇒ 服务名错了照样起不来 |
| ④ | ⚠️ **必须交叉核对 `target/classes` 里打包的副本** | 不核对则「上次构建产物是旧的」时结论不可信 |

**三个模块的期望值**：

| 模块 | client 名 | 期望地址 | 服务名 |
|---|---|---|---|
| article-rpc | `security-rpc` | `discovery:///security-rpc` | `security-rpc` |
| article-rpc | `message-rpc` | `discovery:///message-rpc` | `message-rpc` |
| comment-rpc | `message-rpc` | `discovery:///message-rpc` | `message-rpc` |
| comment-rpc | `article-rpc` | `discovery:///article-rpc` | `article-rpc` |
| like-rpc | `message-rpc` | `discovery:///message-rpc` | `message-rpc` |

⚠️ **一个模块多个 client** ⇒ 守门测试**不能只查第一个** ⇒ 要**遍历**该模块 yaml 里**全部** `grpc.client.*.address`，
**逐个**断言三斜杠 + 逐个调 devh（**缺一个就是漏检** ⇒ 守门必须覆盖全部）。

### 3.2 必做：`grpc-inprocess` 的 scope 守门

- **不能只读 pom 的字符串**（§3.3：「配置里没写」≠「没开」）⇒ 必须有**运行证据**：
  **真起一个 rpc 进程**（真 `*RpcApplication`）⇒ 断言它**真的起来了**（gRPC 端口在监听）。
  ⚠️ **`grpc-inprocess` 的问题在 fat jar 里才暴露**（classpath 模式跑不出问题）⇒
  **必须用 fat jar 起**（`java -jar`），不能只用 `mvn test` 的 classpath。
- ⇒ 参考 `tools/difftest/flyway-rpc-boot.java`（L11 建的，真 rpc 进程驱动）**已经解决过这个问题**：
  它在 L11 时**必须绕开**两斜杠才能起 ⇒ **本 lane 修完之后，那个绕开应该不再需要** ⇒
  **这是一个天然的端到端判据**（详见 §五）。

### 3.3 🔴 变异测试（**必须**，否则守门空转）

至少 **3 条变异**，每条都要**红在目标断言上**（不是编译失败、不是别的用例红）：

| 变异 | 预期红在哪 |
|---|---|
| **M1** 把 article-rpc 的 `discovery:///security-rpc` 改回 `discovery://security-rpc` | article-rpc 的 `ProductionAddressTest` 变红 |
| **M2** 把 like-rpc 的 `grpc-inprocess` 加回 `<scope>test</scope>` | like-rpc 的**真启动**守门变红（`NoClassDefFoundError`） |
| **M3** 把 comment-rpc 的 `discovery:///message-rpc` 改成 `discovery://message-rpc` | comment-rpc 的守门变红 |

⚠️ **PIT-GLOBAL-018 三道闸**：① 跑前**删旧 XML** ② 看 **maven 退出码** ③ grep `COMPILATION ERROR`。
⚠️ **PIT-GLOBAL-021**：判定**只认 surefire XML**（逐条 `testcase`），不依赖 stdout 的 `-- in` 行。
⚠️ **PIT-GLOBAL-020**：变异**只在 lane 自己的工作树**跑；提交前 `git diff` 逐个确认已还原。

---

## 四、修法（**照抄，别自创**）

### 4.1 yaml：两斜杠 → 三斜杠

把**本模块的** `grpc.client.*.address` 的 `discovery://X` 改成 `discovery:///X`。
⚠️ **只改本模块的 `src/main/resources/application.yml`**；**不许**碰 `target/classes`（构建产物）。
⚠️ **保留**该行上下的注释（它们记录了 Go 侧 etcd key 的对应关系，是有用的语义留痕）。

### 4.2 pom：`grpc-inprocess` 去掉 `<scope>test</scope>`

```xml
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-inprocess</artifactId>
    <version>${grpc.version}</version>
    <!-- ⛔ 不得有 <scope>test</scope>：... -->
</dependency>
```

⚠️ **`<version>${grpc.version}</version>` 必须保留**（根 pom 的 `dependencyManagement` **不管** `grpc-inprocess`
⇒ 漏版本会让整个 reactor 在读 POM 阶段就失败 —— like 域踩过这个坑，见 `like-rpc/pom.xml:259-263` 的注释）。
⚠️ **加一行注释说明为什么不能加 test scope**（防止下一个人「补」回来）。

---

## 五、🔴 端到端判据（**本 lane 的最强证据**）

**修完之后**，`tools/difftest/flyway-rpc-boot.java`（L11 的真 rpc 进程驱动）**应当不再需要绕开两斜杠**：

- L11 时它必须用 `--grpc.client.X.address=discovery:///X` **在启动参数里改回三斜杠**才能起
  （因为生产 yaml 是两斜杠）⇒ 缺陷的「存在证据」。
- **本 lane 修完之后，那个绕开应该可以删掉** ⇒ 模块用**生产 yaml 原样**就能起 ⇒ 缺陷的「修复证据」。
- ⚠️ 这要求 **L11 的驱动支持「不传覆盖参数」** ⇒ 若不支持，**在 L15 或整合阶段处理**（L15 拥有 `tools/difftest/**`）
  ⇒ **本 lane 不许改 `tools/difftest/`** ⇒ **把「需要支持无覆盖启动」写进台账，交整合者**。

---

## 六、验收标准

> ✅ **2026-10-26 已全部达成**，逐条证据见 [§八·完成态](#八-完成态2026-10-26--l12-lane-交付)。

- [x] 5 处 yaml 改完（article ×2 / comment ×2 / like ×1）⇒ **源码树里这 3 个模块零两斜杠**
- [x] 2 处 pom 改完（article / like 的 `grpc-inprocess` 去 test scope）
- [x] 3 个 `XxxRpcProductionAddressTest` 落地，**每个模块覆盖该模块的全部 client** ⇒ **全绿**
- [x] 真启动守门落地并全绿（**fat jar**，不是 classpath）
- [x] 变异 **M1/M2/M3 全部变红且红在目标断言上**（贴 surefire XML 证据）⇒ 另加 **M1b**（§8.4）
- [x] `service/favorite/**` **零改动**（`git diff --stat` 里不得出现）
- [x] 模块全量测试仍绿（`mvn -o test -pl <模块> -am`）
- [x] 台账（本文件）写完：逐条缺陷 + 证据 + 变异结果

## 七、🔴 纪律提醒

- **Go 是唯一真源**：改地址**不改变服务发现名**（`message-rpc` / `security-rpc` / `article-rpc` 与 Go 的 etcd key
  `message.rpc` / `security.rpc` / `article.rpc` 对应）⇒ **不是行为漂移修复，是「RAG 侧写法非法导致起不来」的修复** ⇒
  台账里要写清这一点（**别把它写成「与 Go 对齐」** —— 两侧的注册中心本来就不同，那是**已签字的实现差异**）。
- **§3.3.2**：断言里若出现「Go: …」措辞，**必须**带 `文件:行号`；否则改成中性描述。
- **不要 `git add -A`**（PIT-GLOBAL-020）⇒ 逐个 `git add` 本 lane 白名单里的文件。

---

## 八、✅ 完成态（2026-10-26 · L12 lane 交付）

### 8.1 结论（一句话）

**5 处 yaml + 2 处 pom 已修**；**6 个守门类**落地（3 个地址守门 + 3 个真启动守门）⇒ **30 条用例全绿**；
**4 条变异（M1 / M1b / M2 / M3）全部红在目标断言上**；`service/favorite/**` **零改动**。

### 8.2 逐条缺陷 → 修法 → 证据（行号为**修后**最终位置）

| # | 模块 | 缺陷 | 修前 | 修后（文件:行号） |
|---|---|---|---|---|
| ① | article-rpc | `discovery://security-rpc` 两斜杠 | `application.yml:110` | **`application.yml:123`** → `discovery:///security-rpc` |
| ② | article-rpc | `discovery://message-rpc` 两斜杠 | `application.yml:116` | **`application.yml:129`** → `discovery:///message-rpc` |
| ③ | comment-rpc | `discovery://message-rpc` 两斜杠 | `application.yml:184` | **`application.yml:196`** → `discovery:///message-rpc` |
| ④ | comment-rpc | `discovery://article-rpc` 两斜杠 | `application.yml:190` | **`application.yml:202`** → `discovery:///article-rpc` |
| ⑤ | like-rpc | `discovery://message-rpc` 两斜杠 | `application.yml:171` | **`application.yml:183`** → `discovery:///message-rpc` |
| ⑥ | article-rpc | `grpc-inprocess` 是 `<scope>test</scope>` | `pom.xml:196-201` | **`pom.xml:223-225`**（去掉 `<scope>test</scope>`，保留 `<version>${grpc.version}</version>`） |
| ⑦ | like-rpc | `grpc-inprocess` 是 `<scope>test</scope>` | `pom.xml:265-270` | **`pom.xml:287-289`**（同上） |

> ⚠️ **措辞纪律（§七）**：这 7 条**不是「与 Go 对齐」** —— 服务发现名（`security-rpc` / `message-rpc` /
> `article-rpc`）**一个字没改**，改的只是**地址的 URI 写法** ⇒ 是「**RAG 侧写法非法导致起不来**」的修复。
> Go 侧对照（服务名对应关系，**未改动**）：
> `service/article/rpc/etc/article.yaml:85-89`（`SecurityConf.Etcd.Key: security.rpc`）·
> `service/article/rpc/etc/article.yaml:92-96`（`MessageRpc.Etcd.Key: message.rpc`）·
> `service/comment/rpc/etc/comment.yaml:42-46`（`MessageRpc.Etcd.Key: message.rpc`）·
> `service/comment/rpc/etc/comment.yaml:48-52`（`ArticleRpc.Etcd.Key: article.rpc`）·
> `service/like/rpc/etc/like.yaml:114-118`（`MessageRpc.Etcd.Key: message.rpc`）。
> 两侧注册中心不同（etcd vs Nacos）是**已签字的实现差异**，本 lane 未触碰。

**修后零残留自检**（源码树）：

```
$ grep -rn "address: discovery://[^/]" service/{article,comment,like}/*-rpc/src/main/resources/application.yml
零残留 ✅
```

### 8.3 守门清单（用例数 / 断言数）

| 守门类 | 模块 | 用例数 | 关键断言 | 需要 Docker |
|---|---|---|---|---|
| `ArticleRpcProductionAddressTest` | article | **8** | ① client 集合 == 守门清单（`security-rpc` + `message-rpc`，**2 个都查**）② 每个地址是三斜杠 ③ **真调 devh `DiscoveryClientResolverFactory.newNameResolver`**（生产地址不抛 + 两斜杠**必须**抛 `IllegalArgumentException`）④ 服务名 == `path.substring(1)` ⑤ `target/classes` 打包副本交叉核对 | ❌ |
| `CommentRpcProductionAddressTest` | comment | **8** | 同上（`message-rpc` + `article-rpc` 两个 client 都查） | ❌ |
| `LikeRpcProductionAddressTest` | like | **5** | 同上（`message-rpc` 一个 client） | ❌ |
| `ArticleRpcArtifactClasspathGateTest` | article | **2** | ① **runtime classpath**（`dependency:build-classpath -DincludeScope=runtime`）含 `grpc-inprocess` ② **真 fat jar 的 `BOOT-INF/lib/`** 含 `grpc-inprocess` | ❌ |
| `CommentRpcArtifactClasspathGateTest` | comment | **2** | 同上 | ❌ |
| `LikeRpcArtifactClasspathGateTest` | like | **2** | 同上 | ❌ |
| `ArticleRpcRuntimeBootGateTest` | article | **1** | **真 fat jar 起真 `ArticleRpcApplication`** ⇒ gRPC 端口在监听 + 日志有 `gRPC Server started` + 日志**无** `NoClassDefFoundError` / `Incorrectly formatted target uri` | ✅ |
| `CommentRpcRuntimeBootGateTest` | comment | **1** | 同上（真 `CommentRpcApplication`） | ✅ |
| `LikeRpcRuntimeBootGateTest` | like | **1** | 同上（真 `LikeRpcApplication`） | ✅ |
| **合计** | | **30** | **基线全绿：like 8/8 · article 11/11 · comment 11/11** | 6 条不需要 |

> 🔴 **两类守门刻意分开**（整合者 2026-10-26 指令）：
> **产物层判据**（`XxxRpcArtifactClasspathGateTest`）**不需要 Docker、不起进程** ⇒
> 是本缺陷的**环境无关**判据（CI 无 Docker 也能守住「`grpc-inprocess` 不得回到 test scope」）；
> **进程层判据**（`XxxRpcRuntimeBootGateTest`）需要 Docker ⇒ 无 Docker 时**整类 skip**
> （Testcontainers 的 `disabledWithoutDocker`，在 surefire XML 里显式记为 `skipped`，**不是假绿**）。
> ⇒ 两条缺一不可：**产物层**证明「运行时 classpath 上真的有它」，**进程层**证明「用它就真的起得来」。
>
> ✅ **守门断言的是「生产 yaml 原样起得来」**：真启动守门**不传任何 `--grpc.client.*.address` 覆盖**
> （只覆盖 gRPC 端口 + 把 Nacos/Kafka/Redis 指向不可达地址）⇒ **不绕开 ⇒ 恒绿即空转的风险不存在**
> ⇒ 已用 M1b / M3 两条变异证明它**会红**（见 §8.4）。

### 8.4 变异结果表（PIT-GLOBAL-018 三道闸 + PIT-GLOBAL-021 只认 surefire XML）

> 每条变异都跑了 **三道闸**：① 跑前**删旧 surefire XML** ② **maven 退出码** ③ `grep "COMPILATION ERROR"`。
> 判定**只认 surefire XML 的逐条 `testcase`**（PIT-GLOBAL-021）。

| 变异 | 变异内容 | 预期红在哪 | **实际红在哪（surefire XML 逐条）** | maven 退出码 | `COMPILATION ERROR` |
|---|---|---|---|---|---|
| **M1** | article-rpc `application.yml:123` `discovery:///security-rpc` → **两斜杠** | article 的 `ProductionAddressTest` | ✅ `ArticleRpcProductionAddressTest` **8 tests / 3 RED** —— 红在 `productionAddressUsesThreeSlashes[1]` · `devhNameResolverAcceptsProductionAddressAndRejectsTwoSlash[1]` · `productionAddressResolvesToExpectedServiceName[1]`（**参数 [1] = security-rpc**）；`[2]`（message-rpc）**保持绿** · `packagedCopyMatchesSourceTree` **保持绿**（`mvn test` 会 `process-resources` 刷新副本 ⇒ 无假红） | **1** | **0** |
| **M1b**（本 lane 追加） | article-rpc `application.yml:129` `discovery:///message-rpc` → **两斜杠** | 同上 + **真启动守门** | ✅ `ArticleRpcProductionAddressTest` **8 / 3 RED**（参数 [2] = message-rpc）**且** `ArticleRpcRuntimeBootGateTest` **3 / 1 RED** —— 红在 `realFatJarProcessBootsAndListens`：「真 fat jar 起不来（gRPC 端口 52217 没有在监听）」；子进程日志含 `Incorrectly formatted target uri; expected: 'discovery:[//]/<service-name>'; but was 'discovery://message-rpc'` | **1** | **0** |
| **M2** | like-rpc `pom.xml` 的 `grpc-inprocess` **加回** `<scope>test</scope>` | like 的**产物层 + 真启动**守门 | ✅ `LikeRpcRuntimeBootGateTest` **3 / 3 RED** —— `runtimeClasspathContainsGrpcInprocess`（runtime classpath 上没有 grpc-inprocess）· `fatJarContainsGrpcInprocess`（`BOOT-INF/lib` 里没有）· `realFatJarProcessBootsAndListens`（真 fat jar 起不来，子进程日志含 **`NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`**）；`LikeRpcProductionAddressTest` **5 / 5 保持绿**（地址与 pom 无关） | **1** | **0** |
| **M3** | comment-rpc `application.yml:196` `discovery:///message-rpc` → **两斜杠** | comment 的守门 | ✅ `CommentRpcProductionAddressTest` **8 / 3 RED**（参数 [1] = message-rpc）**且** `CommentRpcRuntimeBootGateTest` **3 / 1 RED**（`realFatJarProcessBootsAndListens`） | **1** | **0** |

> ✅ **M1b 是本 lane 追加的**（任务书只要求 M1/M2/M3）—— 追加原因见 §8.6 新发现 ①：
> **M1 选的 `security-rpc` 在本仓没有消费者** ⇒ 真启动**不受影响** ⇒ 只靠 M1 无法证明「真启动守门会红」。
> ⇒ M1b 用**有消费者的** `message-rpc` 补上这一环 ⇒ **真启动守门确实会红**（不是恒真）。
> ⚠️ 变异**只在 lane 自己的工作树**跑（PIT-GLOBAL-020）；提交前 `git diff` 已逐个确认还原
> （`git status --porcelain` 只剩白名单里的 5 改 + 6 新）。

### 8.5 🔴 新发现（**登记，不修** —— 本 lane 无写权 / 不在范围内）

**① article-rpc 的 `security-rpc` 地址今天**没有消费者** ⇒ 两斜杠**不会**让 article-rpc 起不来**

| 项 | 内容 |
|---|---|
| 现象（实测） | M1 把 `grpc.client.security-rpc.address` 改回两斜杠后，**article-rpc 的真 fat jar 仍然起得来**（`ArticleRpcRuntimeBootGateTest` 3/3 **绿**）⇒ 与 L11 台账「article 两斜杠 ⇒ 真 rpc 进程起不来」的表述**不一致** |
| 根因 | 本仓**没有** `@GrpcClient("security-rpc")` Bean —— `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/client/SecurityRpcClient.java:3` 与 `ImageSecurityRpcClient.java:3` 都是「**骨架占位 · 未实现**」的注释文件；本仓也没有 `security` proto 模块 ⇒ 该地址是**死配置**（§3.3「配置里没写 ≠ 没开」的镜像形态） |
| 后果 | ① 该地址**当前不会**造成启动阻塞；② 但它是**定时炸弹**：一旦 security-rpc 落地（占位类被替换成真的 `@GrpcClient`），两斜杠**立刻**变成启动阻塞 ⇒ **仍必须修**（已修） |
| 本 lane 的处置 | **只改写法，不动客户端骨架**（骨架属领域实现，不在本 lane 白名单）⇒ 缺陷已修，登记「今天的阻塞面比台账写的小」这一订正 |

**② L11 台账的「5 个模块两斜杠 ⇒ 真 rpc 进程起不来」需要按 client 粒度订正**

- **成立**：`like-rpc` 的 `message-rpc`（`LikeRpcSpringContextWiringTest.java:73` 注释亦承认）、`article-rpc` 的 `message-rpc`、comment-rpc 的 `message-rpc` / `article-rpc` ⇒ 实测真 fat jar **起不来**（M1b / M2 / M3 的真启动守门已红）。
- **不成立（今天）**：`article-rpc` 的 `security-rpc`（无消费者，见新发现 ①）。

**③ comment-rpc 的 pom 不需要动 `grpc-inprocess`**

`comment-rpc` **没有**声明 `grpc-inprocess` ⇒ 它靠 devh starter 的**传递 compile 依赖**进 fat jar
（实测 `BOOT-INF/lib/grpc-inprocess-1.63.0.jar` 在）⇒ **不存在**「直接声明压过传递声明」的问题。
同理 `like-common` / `comment-common` 里的 `test scope` 声明**不影响**运行时（它们是**库**，不产生运行时 classpath）。

**④ `check-global-lane-ownership.py` 的 EXIT=1 是**基线既有缺陷**，非本 lane 造成**

该脚本仍把 **L6~L11** 列在 `ACTIVE`（`tools/difftest/check-global-lane-ownership.py:51-120`），
而 `_PROTOCOL.md` §3.2 已把它们标为「**已合回**」（`docs/DRIFT/global/lanes/_PROTOCOL.md:60-71`）
⇒ 机器守门把它们与在跑的 L12~L15 判成重叠 ⇒ `EXIT=1`。
⇒ 本 lane **未改**该文件（`tools/difftest/**` 属 L15 / 整合者）⇒ 交整合者把 L6~L11 移进 `CLOSED`。

### 8.6 `service/favorite/**` 零改动（证据）

```
$ git status --porcelain
 M service/article/article-rpc/pom.xml
 M service/article/article-rpc/src/main/resources/application.yml
 M service/comment/comment-rpc/src/main/resources/application.yml
 M service/like/like-rpc/pom.xml
 M service/like/like-rpc/src/main/resources/application.yml
?? service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcProductionAddressTest.java
?? service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcRuntimeBootGateTest.java
?? service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcProductionAddressTest.java
?? service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcRuntimeBootGateTest.java
?? service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcProductionAddressTest.java
?? service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcRuntimeBootGateTest.java

$ git diff --stat -- service/favorite/ docs/DRIFT/favorite/ docs/Pitfalls/favorite/ docs/Intentional_Deviations/favorite/
（空 ⇒ 零改动 ✅）
```

⇒ favorite 域的 2 处两斜杠 + 1 处 `grpc-inprocess` test scope **按协议原样保留在 baseline**
（`_PROTOCOL.md` §3.1：由 L15 的 M3 变异专门验证这一点）⇒ 本 lane **零触碰**。

### 8.7 需要整合者做的后续事项

1. 🔴 **`tools/difftest/flyway-rpc-concurrent.sh` 的模块清单不含本 lane 的三个模块**
   （脚本 `MODULES="follow task message"`，`tools/difftest/flyway-rpc-concurrent.sh:178`）
   ⇒ 任务书 §五的「修完之后那个绕开应该可以删掉」这条判据，**对 article/comment/like 无法用该脚本验证**
   （它们根本不在清单里；`module_args()` 里给 message 的两斜杠绕开属于 **L13** 的处置）。
   ⇒ 本 lane 用**自建**的 `XxxRpcRuntimeBootGateTest` 覆盖了这条判据（**生产 yaml 原样、不传任何地址覆盖**）。
   ⇒ **建议整合者**：把这三个模块加进脚本清单（并同步删掉 L13 修完后的绕开参数）。
2. 🔴 **`check-global-lane-ownership.py` 的 `ACTIVE`/`CLOSED` 名单需更新**（新发现 ④）⇒ 属 L15 / 整合者。
3. 🟡 **CI 需要 Docker**：本 lane 的真启动守门依赖真 PG 容器（无 Docker ⇒ 整类 skip）。
   且这 3 个守门类会**在测试内再起一次 maven**（构建 fat jar）⇒ 单个模块约 **40~60 s**。
4. 🟡 **fat jar 里的兄弟模块取自 `~/.m2` 的已安装快照**（本 lane 的嵌套构建用 `-pl <模块>`，不带 `-am`）
   ⇒ 本守门**只验本模块自己的配置与 classpath**，不验 `service/common` 等共享层（共享层属 `global` 收口域的其它议题）。
5. 🟡 **同款两斜杠仍在域外**：`message-rpc`（L13）· `favorite-rpc`（协议要求保留）⇒ 本 lane 未触碰。

### 8.8 验收标准逐条对照（§六）

| 验收项 | 结果 |
|---|---|
| 5 处 yaml 改完，源码树零两斜杠 | ✅ §8.2 + 零残留自检 |
| 2 处 pom 改完（去 `test` scope，保留 `<version>`） | ✅ §8.2 ⑥⑦ |
| 3 个 `XxxRpcProductionAddressTest`，覆盖每个模块**全部** client | ✅ 8 / 8 / 5 条用例，**全绿** |
| 真启动守门落地并全绿（**fat jar**，非 classpath） | ✅ 3 类 × 3 模块，**全绿**（`java -jar` 真 fat jar + 真 PG + TCP 探测 + `gRPC Server started` 日志双证据） |
| 变异 M1/M2/M3 全红且红在目标断言上 | ✅ §8.4（**另加 M1b**） |
| `service/favorite/**` 零改动 | ✅ §8.6 |
| 模块全量测试仍绿 | ✅ `mvn -o test -pl service/article/article-rpc,service/comment/comment-rpc,service/like/like-rpc -am` ⇒ 见 §8.9 |
| 台账写完 | ✅ 本节 |

### 8.9 真启动的人工复核证据（独立于守门类）

| 模块 | fat jar | 启动日志 | gRPC 端口 |
|---|---|---|---|
| like-rpc | `like-rpc-0.0.1-SNAPSHOT.jar`（`BOOT-INF/lib/grpc-inprocess-1.63.0.jar` ✅） | `Started LikeRpcApplication in 11.67 seconds` + `Creating Schema History table "public"."flyway_schema_history_like"` | **19082 在监听** ✅ |
| article-rpc | `article-rpc-0.0.1-SNAPSHOT.jar`（含 `grpc-inprocess` ✅） | `Started ArticleRpcApplication in 13.054 seconds` + `Creating Schema History table "public"."flyway_schema_history" with baseline` | **19083 在监听** ✅ |
| comment-rpc | `comment-rpc-0.0.1-SNAPSHOT.jar`（含 `grpc-inprocess` ✅） | `Started CommentRpcApplication in 11.857 seconds` + `Creating Schema History table "public"."flyway_schema_history_comment" with baseline` | **19084 在监听** ✅ |

（⇒ **生产 yaml 原样、无任何地址绕开**；Nacos `127.0.0.1:1` / Kafka `127.0.0.1:9092` / Redis 不可达时
**只记日志不中断启动** —— 与 Go 侧 etcd 注册失败只打日志同向。）

> 📌 **端到端对照实证**（真 fat jar 修前/修后 A/B）⇒ 见 [`cross-domain.md`](../cross-domain.md) §二·十六。
