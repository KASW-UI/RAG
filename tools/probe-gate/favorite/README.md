# 探针基建守门网 · favorite 域

> **建档**：2026-10-25（第十四轮收尾 · 探针健壮性加固）
> **触发事件 A**：favorite 域 9 个探针**串行连跑**时，`authority-live` 探针**假红**
> （`EXIT=2 · PG 没起来`），而**单跑绿** ⇒ 同 commit 同代码、单跑绿连跑红 ⇒ **不是被测代码的问题**。
> **触发事件 B**：全量测试里真 Tomcat 用例**随机 8 例全红**（`HTTP/1.1 header parser received no bytes`），
> 而**单跑绿 / 同 JVM 里后一个 Tomcat 也绿** ⇒ **端口被别的会话的容器抢号**。

本目录有**两份**守门网，分别卡「环境闸门」的两类失准：

| 守门网 | 卡什么 | 关联坑位 |
|---|---|---|
| `check-pg-readiness.sh` | **PG 就绪等待**（4 个探针各一份 `wait_pg_tcp` 副本） | **PIT-FAV-RPC-020** |
| `check-port-ownership.sh` | **端口选取 / 端口归属**（2 个探针的端口分配） | **PIT-FAV-API-008** |

## 跑法

```bash
bash tools/probe-gate/favorite/check-pg-readiness.sh     # PG 就绪等待
bash tools/probe-gate/favorite/check-port-ownership.sh    # 端口选取 / 端口归属
```

退出码：`0` = 守规矩；`1` = 有副本 / 实现判错。

---

## 守门网 ① · `check-pg-readiness.sh` —— PG 就绪等待

favorite 域有 **4 个探针**会起真 PG 容器，各自带一份 `wait_pg_tcp`（域内不共享 `tools/` 根 ⇒ **4 份副本**）：

| 探针 | 用途 |
|---|---|
| `tools/authority-live-probe/favorite/` | 权威读 HTTP 面双侧活体对拍 |
| `tools/ddl-parity-probe/favorite/` | DDL 列级对照（sql-first / app-first 两顺序） |
| `tools/grpc-contract-probe/favorite/` | gRPC 帧层 + 契约双侧活体对拍 |
| `tools/api-full-parity-probe/favorite/` | api 整域双侧活体对拍（两个 `java -jar` 真进程） |

`wait_pg_tcp` 是这 4 个探针的**唯一环境闸门** ⇒ 它一旦失准，后果是双向的：

- **假红**：PG 真的没起来，被判成「没就绪」⇒ 探针报红 ⇒ 长得跟「真源漂移」一模一样；
- **假绿**（更坏）：PG 还没真正就绪就被判成「就绪」⇒ 探针带着半就绪的 PG 往下跑 ⇒ **这一轮的结论本身不可信**。

⇒ 本守门网卡的就是这两件事。

### 三个场景（每个探针都跑）

| # | 场景 | 期望 | 抓什么 |
|---|---|---|---|
| ① | 容器**根本不存在** | 返回 `1` | **假就绪**（没容器却报就绪 ⇒ 探针带空环境往下跑 ⇒ 假绿） |
| ② | 容器**启动即退**（`--rm` ⇒ 退会被删掉） | 返回 `1` 且 **< 30s** | **白等满超时**（180s 卡死一轮） |
| ③ | **真 PG 容器** | 返回 `0` | **把真就绪判成没就绪**（探针永远跑不起来） |

⚠️ 做法是**从每个 `run.sh` 原样 `sed` 出真函数**（`awk '/^wait_pg_tcp\(\) \{/,/^\}$/'`），
**不重写、不 mock** ⇒ 测的就是探针实际跑的那份 ⇒ 4 份副本**漂移**也会被场景①③抓到。

### 变异验证（守门网自己不是橡皮章）

2026-10-25 实测：把 `authority-live` 的 `wait_pg_tcp` 改成**永远 `return 0`**
⇒ 守门网 **EXIT=1**，且**只**报 `authority-live` 这一份红（场景① 假就绪 + 场景② 误判就绪），
其余 3 份仍绿 ⇒ 守门网**能定位到具体副本**，不是「全红」。

---

## 守门网 ② · `check-port-ownership.sh` —— 端口选取 / 端口归属

| 探针 | 取几个端口 |
|---|---|
| `tools/api-full-parity-probe/favorite/` | **4 个**（Go rpc / Go api / RAG rpc / RAG api） |
| `tools/grpc-contract-probe/favorite/` | **2 个**（Go server / RAG server） |

⚠️ **为什么探针的端口也要管**（背景 ⇒ **PIT-FAV-API-008**）：
OS 随机端口在**临时端口段 49152–65535** ⇒ **正是 Docker 随机发布容器端口的段**；
macOS 上 `127.0.0.1:P` 会**抢占**服务绑的 `0.0.0.0:P` ⇒ 客户端连 `127.0.0.1:P`
会**打到别人的服务**（本机实测那次打到了 PostgreSQL）
⇒ ⚠️ 探针侧比 JUnit 侧更危险：`wait_port` **只验「连得上」** ⇒ **连到别人的服务也算就绪**
⇒ 探针结论会**整个不可信**（而不只是报错）。

### 四个场景（每个探针都跑）

| # | 场景 | 期望 | 抓什么 |
|---|---|---|---|
| ① | 裸调 | 返回一个**落在 21400–21599** 的端口 | 用了 OS 临时端口段（⇒ 会与 Docker 抢） |
| ② | 带「已用端口」再调 | 返回**互不相同**的端口 | 两个服务抢同一个端口 |
| ③ | 有人占着 `127.0.0.1:P` | **必须跳过 P** | Docker 发布端口那种占用 |
| ④ | 有人占着 `0.0.0.0:P` | **必须跳过 P** | 另一个 Tomcat 占通配地址那种占用 |

⚠️ 做法是**从各探针 `run.sh` 里原样抽出第一段 python**（`<<'PY'` … `PY`，
**只取第一段** —— run.sh 里有好几段 python heredoc）⇒ 测的就是探针实际跑的那份
⇒ 任一探针的端口选取被改坏 ⇒ 这里立刻响。

---

## 与坑档的关系

⇒ 完整经过与判据 ⇒
- **PG 就绪** ⇒ [`docs/Pitfalls/favorite/rpc.md`](../../../docs/Pitfalls/favorite/rpc.md) 的 **PIT-FAV-RPC-020**（探针的「环境红」不是「被测代码红」—— 等待过短 / 探活探错目标 ⇒ 假红）
- **端口归属** ⇒ [`docs/Pitfalls/favorite/api.md`](../../../docs/Pitfalls/favorite/api.md) 的 **PIT-FAV-API-008**（`RANDOM_PORT` 挑的端口可能根本不是你的）

## ⚠️ 域作用域（别的域要自己复制）

本目录**只管 favorite**。别的域的探针若有同款 `wait_pg_tcp` / 同款端口选取（大概率有），
需要**各自复制**这两份守门网 ⇒ 归 `global` 收口域登记（本域只写自己这份）。
