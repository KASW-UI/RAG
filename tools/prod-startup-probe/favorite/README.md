# 生产启动形态 · 真 fat jar 活体探针 · favorite 域

> **建档**：2026-10-26（第十七轮 · `align/favorite` 工作树）
> **触发事件**：`global` 收口域移交过来的两条**生产启动阻塞**——
> 🔴 (1) gRPC client 地址写成**两斜杠** `discovery://user-rpc` ⇒ devh 的 URI 校验在启动期抛异常；
> 🔴 (2) classpath 上有 `jakarta.servlet` ⇒ Boot 判定本进程是 **SERVLET web app** ⇒ 但没有 servlet 容器 ⇒ 启动即死。
>
> **本探针验的是哪一层**：
> 「**生产配置原样**能不能真起得来」——
> 这是 `@SpringBootTest` **永远证明不了**的一层（见 **PIT-FAV-RPC-018** / **PIT-FAV-RPC-026**）。

---

## 〇、为什么需要它（这不是重复劳动）

favorite 域此前的 **85 个测试全绿**，而服务**根本起不来**。原因只有一个：

> 模块里**所有** `@SpringBootTest` 都用 `webEnvironment = NONE`，
> 并把 client 地址覆盖成 `static://127.0.0.1:1`
> ⇒ **生产 `application.yml` 从未被任何测试跑过**。

⇒ 「跑绿」与「能启动」之间隔着**一个配置文件**（AGENTS.md §3.3「跑绿 ≠ 对」）。
⇒ 本探针把这个洞补上：**真 fat jar + 生产 yaml 原样 + 真 PG**。

---

## 一、三个 case（同一个 jar，唯一变量是**那一个参数**）

| case | 参数 | 期望 | 验的是 |
|---|---|---|---|
| **A** | `--grpc.client.{user,article}-rpc.address=discovery://<name>`（**按字节复现修前值**） | 🔴 **启动即炸**（`Incorrectly formatted target uri`） | 两斜杠确实致命 |
| **B** | **不传任何 `--grpc.client.*` 覆盖** | ✅ **真起来**（`Started FavoriteRpcApplication` + `lsof` 核对端口 LISTEN）**且** 观测端口 `/actuator/prometheus` 回 **200**（**指标抓取载体在**） | 生产 yaml 原样可用 **＋ 载体确实在** |
| **C** | `--spring.main.web-application-type=none`（把载体**显式关掉**） | ✅ **照样起来**（web 类型**不影响** gRPC 业务面）**但** 观测端口**抓不到** `/actuator/prometheus`（载体不在） | B 里那个载体**确实由 servlet 容器承载**（不是别的东西顺带提供的） |

> 🔴 **C 的语义在 2026-10-30 被换掉了**（旧语义已随 Actuator 载体落地而**失效**）：
> 【旧 C】把 `web-application-type` 覆盖成 `servlet` ⇒ 期望**启动即炸** ⇒ **为什么失效**：2026-10-30 补了 `spring-boot-starter-web` ⇒ **容器有了** ⇒ 覆盖成 `servlet` **不再炸**（它就是当前默认形态）⇒ ⇒ 旧 C 恒判「不成立」⇒ **探针假红**（产品无缺陷，是探针过期）。
> 【新 C】反过来 —— 覆盖成 `none` ⇒ **载体被关掉** ⇒ 期望「**抓不到**」。
> ⇒ 脚本侧有完整说明（`run.sh:13-32`）＋ 台账侧有对应记载（`docs/DRIFT/favorite/rpc.md` 的「探针 case C 的语义换掉了（2026-10-30）」）。

⚠️ **B case 绝不覆盖 client 地址**——那必须是生产 yaml 的**原值**，
否则 B 证明的是「覆盖值能起」，不是「生产值能起」。

### 为什么用「参数复现旧值」而不是「git 改回旧 yaml」

- 三个 case 跑的是**同一个 jar** ⇒ 唯一变量就是**那一个参数** ⇒ 差异**只能**来自该参数；
- 参数值与修复前的生产值**逐字节相同**（`discovery://user-rpc`）；
- 不会在探针里动工作树 ⇒ 不污染 git 状态。

---

## 二、跑法

```bash
# ① 先出包（探针要的是**真 fat jar**）
mvn -o -pl service/favorite/favorite-rpc -am package -DskipTests

# ② 跑探针
bash tools/prod-startup-probe/favorite/run.sh
```

退出码：`0` = A 炸(uri) 且 B 起来（载体在）且 C 起来（载体不在）；`1` = 不成立；`2` = 环境问题。

---

## 三、判据出处（**字节码实测**，不是看文档猜的）

devh `grpc-client-spring-boot-starter` **3.1.0.RELEASE** 的
`net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.newNameResolver`：

```java
if ("discovery".equals(uri.getScheme())) {
    String path = uri.getPath();
    if (path == null || path.length() <= 1 || !path.startsWith("/")) {
        throw new IllegalArgumentException("Incorrectly formatted target uri; ...");
    }
    return newNameResolver(path.substring(1), args);   // ← 服务名 = path 去掉首斜杠
}
```

⇒ `discovery://user-rpc` 的 `path` = `""`（长度 0）⇒ **不合格**；
⇒ `discovery:///user-rpc` 的 `path` = `"/user-rpc"` ⇒ 合格，
且服务名 = `"user-rpc"` ⇒ **Nacos 上的名字一个字都没改**。

⚠️ 这个检查发生在**任何网络调用之前** ⇒ 与 Nacos 在不在线**无关**
⇒ 且炸点在 gRPC client bean 后处理 ⇒ **连 Flyway 都跑不到**。

---

## 四、探针自身的纪律（不是形式主义）

| 项 | 做法 | 关联坑位 |
|---|---|---|
| **PG 就绪** | 探 **TCP**（`pg_isready -h 127.0.0.1`）+ **180s** 上限 + 容器退出即放弃 | **PIT-FAV-RPC-020** |
| **端口** | 专用段 **21300–21399**（Java 侧）· **双绑定自检**（`0.0.0.0:P` 与 `127.0.0.1:P` 都要能绑） | **PIT-FAV-API-008** |
| **进程回收** | `( cd X && **exec** java ... ) &` ⇒ `$!` 就是 java 的 pid ⇒ `kill` 干净（否则 java 变孤儿，继续占端口 ⇒ 下一个 case 被干扰） | 本轮新踩 |
| **端口释放** | 每个 case 结束后等端口真正释放（≤30s）⇒ case 之间不互相污染 | 同上 |
| **Nacos** | 指向 `127.0.0.1:1`（故意不可达）⇒ 证明「**注册中心不在**也能起来」⇒ 本探针验的是**启动形态**，不是连通性 | — |
| **只覆盖环境项** | PG 连接 / Nacos 地址 / gRPC 监听端口 —— **三项**；其余一律用生产 yaml 原值 | — |

---

## 五、⚠️ 本探针**不**证明什么

- ❌ **不证明**下游 `user-rpc` / `article-rpc` 能连上 ⇒ 本探针里它们**根本不存在**；
  「下游在场」那条分支由 [`tools/grpc-contract-probe/favorite/`](../../grpc-contract-probe/favorite/) 第二轮负责（§25）。
- ❌ **不证明**业务行为正确 ⇒ 本探针只到「进程起来了、端口 LISTEN 了」。
- ❌ **不覆盖** Go 侧那个 **16062 的 DevServer 面**（`/metrics` `/healthz` pprof）⇒
  RAG 侧走 **Actuator**（`/actuator/prometheus`，**路径前缀不同**）⇒ ⚠️ **该面已于 2026-10-30 落地**
  （`web-application-type: none` 已从 `application.yml` 删除 ⇒ 载体在）⇒ ⇒ 本探针**只验「载体在不在」**、
  **不比对两侧的路径/格式** ⇒ 详见 `docs/DRIFT/favorite/rpc.md` 的「指标抓取载体落地 + U9 销账」追加节。
