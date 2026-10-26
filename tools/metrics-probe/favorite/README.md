# metrics-probe/favorite：favorite 域 **指标 `result` 标签**的语义锚定探针

> 用途：钉死一条 **Go 语言语义**，并据此判定 favorite 域 2026-10-23 台账里的 **U5** 条目。
>
> ⚠️ **本工具不是双侧对拍**，定位见下。

## 它验什么

Go 侧 favorite-rpc 的每个 logic 头部是：

```go
func (l *XxxLogic) Xxx(in *pb.Req) (resp *pb.Resp, err error) {
    started := time.Now()
    defer func() { metrics.ObserveRPC(module, action, started, err) }()
    ...
    return nil, favoritecommon.GRPCError(codes.Internal, favoritecommon.ErrorDbSelect)
}
```

而 `ObserveRPC`（`metrics.go:56-64`）的判定是：

```go
result := "success"
if err != nil {
    result = "fail"
}
```

⚠️ **Go 的 `return` 语句会把返回值赋给对应的命名返回值** ⇒ `err` 非 nil
⇒ defer 里 `ObserveRPC` 记的是 **`fail`**，**不是** `success`。

⇒ **2026-10-23 台账的 U5（"Go 侧 21 处错误返回被记成 success"）前提被证伪**：
① 那个数字是 `return nil, GRPCError(...)` 的出现处数（实测 **22** 处，不是 21）；
② 更关键的是语义判断反了 —— 那些 return **都会**赋值命名返回值。
⇒ **Go 与 RAG 在这一点上本来就一致，不存在偏离，无需签字**。

## 怎么跑

```bash
bash tools/metrics-probe/favorite/run.sh
echo $?   # 0 = 语义如预期（错误路径记 fail）
```

输出：

```
RESULT|return-stmt-only|fail
RESULT|assigned-first|fail
RESULT|success|success
RESULT|nil-nil|success
```

## 为什么不是双侧对拍

它验的是「**Go 语言语义**」这一层，不是 favorite 的业务逻辑
（后者需要真 DB / etcd / Kafka，Go 侧起不来）。

⇒ 两边的分工：

| 层 | 谁守 |
|---|---|
| Go 侧的语义事实（错误路径记 `fail`） | **本探针**（Go 半边） |
| RAG 侧每个失败分支都记 `fail`（真正的回归风险） | `FavoriteRpcMetricsResultTagTest`（4 例，**运行时**断言） |

⚠️ **RAG 侧才是真正的风险点**：Java 没有「命名返回值 + defer」组合，
RAG 靠 try/catch/finally + 每个失败分支显式设 `RESULT_FAIL` 达成同一语义
⇒ **新增失败分支时漏设，指标就会把失败记成 success**。

## 防「拿着自己的一份副本自证」

`run.sh` 的第 1 步会**从 Go 真源**（`service/favorite/rpc/internal/metrics/metrics.go`）
抽取 `ObserveRPC` 的 result 判定片段，与本探针预期的那份做 **sha256 比对**
⇒ **真源改了探针立刻响**（退出码 2，并打印 diff）。

⚠️ 这比「复制一份进探针」强：复制的那份一旦与真源漂移，探针会**静默继续**。

## 已知边界

- **不覆盖** `ObserveOp` / `ObserveDBError` 的标签取值（那两条两侧都是显式传参，无语言语义差异）。
- **不覆盖** Go 的 22 处具体分支（需要真 model + DB）。
  ⇒ 那些分支的**逐条**对齐由 `docs/DRIFT/favorite/rpc.md` §9 与
  `FavoriteErrorCodeMappingTest`（14 例）负责。
