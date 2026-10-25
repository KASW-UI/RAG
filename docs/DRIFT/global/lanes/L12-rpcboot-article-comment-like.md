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

- [ ] 5 处 yaml 改完（article ×2 / comment ×2 / like ×1）⇒ **源码树里这 3 个模块零两斜杠**
- [ ] 2 处 pom 改完（article / like 的 `grpc-inprocess` 去 test scope）
- [ ] 3 个 `XxxRpcProductionAddressTest` 落地，**每个模块覆盖该模块的全部 client** ⇒ **全绿**
- [ ] 真启动守门落地并全绿（**fat jar**，不是 classpath）
- [ ] 变异 **M1/M2/M3 全部变红且红在目标断言上**（贴 surefire XML 证据）
- [ ] `service/favorite/**` **零改动**（`git diff --stat` 里不得出现）
- [ ] 模块全量测试仍绿（`mvn -o test -pl <模块> -am`）
- [ ] 台账（本文件）写完：逐条缺陷 + 证据 + 变异结果

## 七、🔴 纪律提醒

- **Go 是唯一真源**：改地址**不改变服务发现名**（`message-rpc` / `security-rpc` / `article-rpc` 与 Go 的 etcd key
  `message.rpc` / `security.rpc` / `article.rpc` 对应）⇒ **不是行为漂移修复，是「RAG 侧写法非法导致起不来」的修复** ⇒
  台账里要写清这一点（**别把它写成「与 Go 对齐」** —— 两侧的注册中心本来就不同，那是**已签字的实现差异**）。
- **§3.3.2**：断言里若出现「Go: …」措辞，**必须**带 `文件:行号`；否则改成中性描述。
- **不要 `git add -A`**（PIT-GLOBAL-020）⇒ 逐个 `git add` 本 lane 白名单里的文件。
