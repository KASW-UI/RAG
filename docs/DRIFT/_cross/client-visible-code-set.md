# 客户端可见码的「**集合**」—— Go 侧在「请求 deadline 已过期」时客户端能看到哪些码（2026-10-30 · 合并收口执行者落仓）

> **先给结论**：**Go 侧客户端可见码集合 = {1015（`DeadlineExceeded`）, 5001（`Internal`）}**，
> 其中 5001 是**窄窗口**（实测 200000 轮 ⇒ 命中 22 次 ≈ 0.011%）。
> ⇒ ⇒ **「handler 级差异不可观察 ⇒ 不构成漂移」这个前提不成立**。
> ⇒ ⇒ **RAG 保留域内 `checkDeadline()` 守卫 ⇒ RAG 的集合 = {1015} = Go 集合的真子集** ⇒ ⇒ 仍是一处差异；
> ⇒ ⇒ **RAG 删掉守卫 ⇒ RAG 的集合 = {1015, 5001} = 与 Go 同集合 ⇒ 才是同形**。
>
> ⚠️ **本文件不是差异台账**（不进派生层的条目面）⇒ ⇒ 标题与表格**不写编号 token**、行内不写状态标记。

---

## 一、探针落点（可复算）

| 项 | 值 |
|---|---|
| 位置 | `tools/semantic-probe/client-visible-code/`（Go 半边）＋ `run-client-visible-code.sh` |
| 跑法 | `bash tools/semantic-probe/client-visible-code/run-client-visible-code.sh` |
| 退出码 | `0` = 形态与已登记一致（集合含 4 与 13）· `1` = **形态变了**（集合不含 13 ⇒ 前提要回源头重核）· `2` = 环境不满足 |
| 建模对象 | 真源 `service/common/observability` 的 `NewUnaryServerInterceptor`（**经 `replace` 直接引用冻结仓**，不改写一个字节） |
| 依赖 | Go toolchain ＋ 本地 module cache 里的 go-zero v1.10.2 ＋ grpc-go；`CGO_ENABLED=0` ＋ `GOPROXY=off`（离线） |

**三条纪律**（与 `deadline-downstream/` 同款）：

1. **只提交 `go/main.go`**；`go.mod` 由脚本在运行时生成（`replace sea-try-go => <冻结仓>`）⇒ ⇒ **仓库里不出现任何机器相关的绝对路径**。
2. **`CGO_ENABLED=0`** —— 本机没有 Xcode 头文件 ⇒ cgo 会报 `'stdlib.h' file not found`。
3. **期望值必须有台账出处** —— 没有出处的期望值 = 无出处的断言 ⇒ 不许进来。

---

## 二、实测（2026-10-30 · 本机实测 · 可复算）

| case | 建模 | code_4（`DeadlineExceeded`） | code_13（`Internal`） | 码集合 |
|---|---|---|---|---|
| `fast_handler` | 请求 deadline **已过期** ⇒ 真源的 Redis 写**立刻失败** ⇒ handler 立刻返回 `codes.Internal` | 199978 | **22** | **{4, 13}** |
| `slow_handler` | handler 跑满 50ms ⇒ 父 goroutine 先看到 `timeoutCtx.Done()` | 200000 | 0 | **{4}** |
| 轮数 | — | 200000 | 200000 | — |

⚠️ **轮数为什么是 200000**：窄窗口的命中概率由**调度竞争**决定（handler goroutine 能不能赶在父 goroutine 走到 `select` 之前完成）⇒ ⇒ 命中概率在千分之几量级 ⇒ ⇒ 轮数不够就会「一次都没命中」⇒ ⇒ 门禁**假红** ⇒ ⇒ 200000 轮下期望命中数十次 ⇒ 「一次都没命中」在这种量级上才是**真信号**。

---

## 三、真源机制（逐行取证）

- `service/common/observability/rpc.go:38` ⇒ `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`
  ⇒ ⇒ **`timeoutCtx` 的 deadline = min(父 ctx 的 deadline, now+timeout)** ⇒ ⇒ **父 ctx 已过期时，`timeoutCtx` 一创建就是到期的** ⇒ `Done()` 立即闭。
- `service/common/observability/rpc.go:47-57` ⇒ handler goroutine：`lock.Lock()`（`:53`）⇒ `handler(timeoutCtx, req)`（`:55`）⇒ `close(done)`（`:56`）。
  ⇒ ⇒ handler 拿到的是 `timeoutCtx` ⇒ ⇒ **ctx-aware 的出向 I/O 立刻失败**（go-redis `internal/pool/pool.go:1098-1104` 的 `waitTurn` fast path 无条件先查 `ctx.Done()`）⇒ ⇒ **handler 立刻失败**（这正是 `fast_handler` 这个 case 的建模依据）。
- `service/common/observability/rpc.go:59-72` ⇒ **本探针的核心**：
  - `:63` `case <-done:` ⇒ `:64` `lock.Lock()` ⇒ `:67` **`return resp, err`（返回 handler 的 err）**；
  - `:68` `case <-timeoutCtx.Done():` ⇒ `:69` `status.Error(codes.DeadlineExceeded, …)` ⇒ `:71` 返回 1015。
  ⇒ ⇒ **两个分支可能同时就绪**（父 ctx 已过期 ⇒ `timeoutCtx.Done()` 立即闭；handler 立刻失败 ⇒ `done` 也很快闭）
  ⇒ ⇒ **Go 的 `select` 在多个就绪分支之间是随机选的** ⇒ ⇒ **走 `done` 分支时，客户端拿到的是 handler 的 `codes.Internal`**（业务码 5001）。
- `service/user/admin/rpc/internal/logic/logoutlogic.go:38-41` ⇒ `SetexCtx(l.ctx, …)` 写失败 ⇒ `status.Error(codes.Internal, "注销失败,Redis写入异常")`
  ⇒ ⇒ **Go 的 handler 级码 = `codes.Internal`**（业务码 `service/user/common/errmsg/errmsg.go:7` = 5001）。

---

## 四、为什么这条证据必须落仓（不可复算口径的教训）

- 这个数**曾在一次临时 module 里跑出来**（N=20000 ⇒ 1015=19995 / 5001=5），
  但**探针源码没有落仓**（只存在于当时的 `/tmp`）⇒ ⇒ 「跑某条命令会得到 N」变成了**不可复算**的口径
  ⇒ ⇒ 谁都没法复核、谁都没法判断前提变没变。
- ⇒ ⇒ **纪律**：凡台账里写「跑某条命令会得到 N」，**必须把那条命令 / 那个探针落仓**（否则它是**不可复算的口径**，不是证据）。
- ⚠️ 本轮第一次跑本探针时**编译不过**（`grpc.UnaryHandler` 的签名是 `(ctx, req)` 两参；`status.FromError` 在本 grpc-go 版本返回**两个**值）⇒ ⇒ **探针自己也会有编译期错** ⇒ ⇒ 落仓后才能被复跑 ⇒ ⇒ 「跑一次就完」是不成立的。

---

## 五、判据式（这个数怎么用）

1. **漂移判定对象 = 客户端可见面** ⇒ ⇒ 比较必须用**集合**，不能用单点。
2. **「RAG 的集合是 Go 集合的真子集」也是一处差异**（窄，但**可观察**）⇒ ⇒ 不能用「几乎总是」当「必然」
   ⇒ ⇒ 凡断言「某个分支**永远**走不到」，要么给一条**可运行的判据**，要么把它降级成「实践中如此」。
3. **探针是形状守门，不是数值守门**：断言的是**集合里有没有那个码**（形态），**不是**精确比例（数值）
   ⇒ ⇒ 形态变了就红 ⇒ ⇒ 逼人**回源头重核**，而不是把期望值改成新的数。
4. **两侧的集合要分别测**：本文件只测 **Go 侧**；RAG 侧「保留守卫」与「删守卫」两个变体的集合，由 users 域的域侧探针承担 ⇒ ⇒ **合并门 = 两侧集合相同**。

---

## 六、效力边界

- 本文件管**一个面**：客户端可见码的**集合**（Go 侧）。
- **不**管：handler 级码的**实现细节**（那是 `service/users/admin/admin-rpc/**` 的域面）· 共享层代理的生效路径（那是 `service/common/**` 的共享面）· 派生层与台账口径。
- ⇒ ⇒ 合并门（M-6）的判据见 [`../../Worktree.md`](../../Worktree.md) §3-1 / §3-2。
