<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit 8d441b3 -->
<!-- spec 落仓：按 AGENTS.md §5.1「提交前 spec 已落仓」 -->

# spec · global B2 —— X-3「事实投递链路」定位与闭合

> **PR 范围**：`align/global` → `do-my-own-gh`
> **域**：`global`（收口域，§3.5）· **议题档**：`cross-domain`（X-3）
> **基线**：`align/global` @ `8d441b3`
> **写权**：**只有 `docs/DRIFT/global/`**（本轮**零代码改动**）

---

## 一、要解决什么

X-3 登记为「链路末端在**第三个仓库** ⇒ 本轮未定位那个仓库」——
一条**完全未知**的项：链路的两端（RAG 的 `like` api ↔ rpc）都知道，
**中间那个接收方是谁、在哪、契约是什么**，全不知道。

---

## 二、结论：末端**不是仓库**，是**仓外的一个 HTTP 服务**

```go
// Sea-RideTheWind_golang/service/like/rpc/cmd/fact-dispatch/main.go:54-56
dsn      := os.Getenv("LIKE_DATABASE_URL")
endpoint := os.Getenv("DC_PLATFORM_EVENT_URL")
token    := os.Getenv("DC_PLATFORM_SERVICE_TOKEN")
```

⇒ 末端 = **DC 平台的 `POST /v1/events`** ⇒ **在本仓库之外**。

⇒ 契约**不在仓内代码里**，而是由 **env + 构造期参数校验**钉死：

```go
// service/common/communityfact/wire.go:83-91
if parsed.Scheme != "http" && parsed.Scheme != "https" || parsed.Host == "" ||
   parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" ||
   parsed.Path != "/v1/events" || len(token) < 32 || client == nil {
    return nil, errors.New("community DataCenter event sender is not configured")
}
```

⇒ 请求头（`wire.go:97-104`）：`Content-Type: application/json`
+ **`Idempotency-Key: <event.EventID>`** + **`Authorization: Bearer <token>`** + OTel trace 注入。

---

## 三、仓内两侧**都有**，且**逐条对齐**

| 环节 | Go | RAG |
|---|---|---|
| 共享包 | `service/common/communityfact/`（4 文件） | `service/common/.../communityfact/`（`AuthorityHandler` / `Dispatcher` / `ProcessLogger` / `Wire`）✅ **4 ↔ 4** |
| `/v1/events` 路径校验 | `wire.go:87` | `Wire.java:201` ✅ |
| authority 端点 | `GET /internal/v1/community/facts` | `AuthorityHandlerFactory.AUTHORITY_PATH` ✅ 1:1 |
| 调度形状 | `1s` ticker / 每轮 `16` 条 / 单次 `10s` / 单条失败即 `break` | `Dispatcher.java:122,178` + 各域 `@Scheduled` ⇒ **四项逐条对齐**（javadoc 标了 Go 行号） |
| 接入域 | `like` / `comment` / `favorite`（各有 `cmd/fact-authority` + `cmd/fact-dispatch`） | 三域**都接了线**（`application.yml` 读同一组 env）✅ |

---

## 四、唯一差异 = **部署形态**（表达层，不修）

| | Go | RAG |
|---|---|---|
| dispatch | 独立进程 `cmd/fact-dispatch` | 进程内 `@Scheduled` |
| authority | 独立进程 `cmd/fact-authority` | rpc 进程内的一个 HTTP 端点 |

⇒ javadoc 已留痕：「只是**吞吐上限**从『1 进程』变成『每 JVM 16 × N 副本』」
⇒ Spring Boot 单 jar 无法再起第二个 main 而不改产物形态 ⇒ **属部署形态差异，不是语义差异**。

---

## 五、处置

⇒ **X-3 关闭**（✅ 已闭合）：**不是漂移，是架构事实 + 仓外边界**。
⇒ ⚠️ 仍需外部知识补齐的一点：DC 平台**自己**的行为（怎么存、怎么回执）不在本仓库内
⇒ 两侧对它**只能对齐到 env + 端点契约这一层** ⇒ 这是**边界**，不是缺口。

---

## 六、验证

本轮**零代码改动** ⇒ 无需重跑构建；台账一致性由
`docs/DRIFT/global/_README.md` §五 与 `docs/DRIFT/global/cross-domain.md` 同步更新保证。

---

## 七、**不在**本次范围

| 项 | 为什么 |
|---|---|
| X-1（三模块同库共存运行时验证） | 需真 PG + 三进程并发 ⇒ 独立一轮 |
| X-2（建表责任交叉 + client 契约） | 需先读 users 域台账 ⇒ 独立一轮 |
| X-4 / X-5 | 归 like / task 域 ⇒ 各域自行收敛，global 只记账 |
| X-6 | **等用户拍板** |
| X-10（`message` 域索引列差异） | 修法落在 `service/message/message-rpc` ⇒ 属 message 域 ⇒ global 不写别域代码；**等用户拍板** |
| B/C 其余条目 | 用户明确「先等下」 |
