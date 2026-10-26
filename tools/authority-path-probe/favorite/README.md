# 权威读端点的 path 形态 / 错误响应探针（favorite 域）

> 用途：把 Go 的 `http.ServeMux` + `http.Error` / `http.NotFound` / `http.Redirect`
> 行为，与 RAG 侧手写的 `FavoriteAuthorityServlet` **逐条对拍**。

## 为什么需要它

RAG 侧的权威读端点是**手写的 servlet**（`FavoriteAuthorityEndpointConfig`），
而 Go 侧是 `http.ServeMux` + 一个 handler。⇒ 两者之间有**两层**判定：

| 层 | Go | RAG（本探针对照的对象） |
|---|---|---|
| **mux 层** | ① path 需要清理 ⇒ **301** + `Location`；② 模式不匹配 ⇒ **404 page not found** | 原实现**两层都没有** |
| **handler 层** | 鉴权 401 ⇒ `http.Error`；带 query ⇒ `http.NotFound`；其它 error ⇒ 503 | 形状接近，但 body/头部/顺序有差 |

⇒ 手写 servlet 极易漏掉 mux 层（本轮就漏了 4 处），
而**只比状态码**又看不出 body 与顺序的差 ⇒ 必须逐字节比对。

## 跑法

```bash
bash tools/authority-path-probe/favorite/run.sh
echo $?   # 0 = 两侧一致；1 = 不一致；2 = 环境问题
```

## 结构

| 文件 | 作用 |
|---|---|
| `go/main.go` | Go 侧：只用**标准库**复刻 `http.ServeMux` + `http.Error` / `http.NotFound` / `http.Redirect`，输出 15 行矩阵 |
| `expected.txt` | Go 侧输出的**冻结副本**（15 行）⇒ Go 真源变了 ⇒ `diff` 会失败 |
| `run.sh` | ① 跑 Go 侧并与 `expected.txt` 比对；② 跑 Java 侧断言 |

Java 侧的断言在
`service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/fact/FavoriteAuthorityPathParityTest.java`
（12 条矩阵 + 1 条「少一段 path 不得抛异常」）。

## 覆盖的 15 条（Go 侧）

| 组 | 条数 | 内容 |
|---|---|---|
| `MUX` | 10 | path 形状：正常 / `//` / `/./` / `/../` / 少一段 / 多一段 / 尾斜杠 / `%2F` / `%2E%2E` |
| `HANDLER` | 5 | 无 token / 错 token / 正确 token / 带 query / 少一段 |

## ⚠️ 三条「照抄 Go」而不是「照抄正确」的

1. **Go 的 `Location` 会二次转义**：`/…//a%2Fb` ⇒ `Location: /…/a%252Fb`
   （Go 构造 `url.URL{Path: escapedPath}` 时没设 `RawPath` ⇒ `EscapedPath()` 又把 `%` 转义成 `%25`）。
   ⇒ 这看起来像 Go 的缺陷，但**偏离它会让客户端跟随到不同路径** ⇒ 照抄，并在台账标注。
2. **Go 的 301 body 末尾是**两个** `\n`**（`http.Redirect` 先拼一个 `\n` 再交给 `fmt.Fprintln`）。
3. **Go 的 404/401 body 末尾都有一个 `\n`**（`http.Error` 用 `fmt.Fprintln`）。

## 效果边界

- 本探针只覆盖**错误/重定向**形状；**200 的 body** 是真实权威事实（两侧结构不同）⇒ 不在本探针范围。
- 本探针不覆盖 **v2 端点**（`/internal/v2/...`）：v2 与 v1 共用同一套 mux 与 handler 逻辑，
  只有「多一段 path 时的额外 404」是 v2 专属（Go 侧 `http.go:75-79`），而该分支在 Go 侧**不可达**
  （Go 的 reader 实现了 V2）⇒ 本域两边都恒支持 V2 ⇒ 无差异。
