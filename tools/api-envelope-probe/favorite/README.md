# api-envelope-probe/favorite：favorite api **响应字节**的双侧对拍探针

> 用途：把 favorite api 的响应体**逐字节**钉在 Go 真源上。
> 这是 AGENTS.md §3.3 要求的「**两侧同时跑的探针**」。

## 它验什么

favorite api 的响应只有一种写法（Go 侧 7 个 handler 全都走它）：

```go
httpx.OkJsonCtx(r.Context(), w, &response.Response{
    Code: code,
    Msg:  favoritecommon.GetErrMsg(code),
    Data: resp,
})
```

⇒ 响应字节由三件事决定：① `response.Response` 的字段与 tag；② `httpx.OkJsonCtx`
最终调用的 `jsonx.Marshal`；③ `types.go` 里各 DTO 的**字段 tag**（`string` / `optional`）。

本探针把这三件事**一起**在真源上跑一遍，产出逐字节的期望值。

## 为什么用「真 go-zero」而不是照着源码复刻

| 细节 | 只有真库才有 |
|---|---|
| `jsonx.Marshal` = `encoding/json` + `SetEscapeHTML(false)` + **去掉尾换行** | 手写复刻几乎必错（HTML 转义、尾换行） |
| `httpx.Parse` → `ParseForm` → `GetFormValues` 的 **`maxFormParamCount = 2048`** | 判据只存在于真库 |
| `handler.RecoverHandler` 的 500 响应体 | 真库是 **`w.WriteHeader(500)`**（空 body） |

⚠️ Go 侧的依赖版本**从真源 `go.mod` / `go.sum` 整体继承**（不写死、不 `go mod tidy`）：
`go mod tidy` 会去解析**依赖的测试依赖**（go-zero → yaml.v2 → check.v1 → rogpeppe），
那些未必在本地 module cache 里，而探针根本不需要它们。
⇒ 探针以 `CGO_ENABLED=0` 跑（纯 Go，不需要 cgo 工具链）。

## 怎么跑

```bash
bash tools/api-envelope-probe/favorite/run.sh
echo $?   # 0 = 双侧逐字节一致
```

- 第 1 步：从 Go 真源**逐字节复制** `response.go` / `types.go` / `errmsg.go`，做 **sha256 比对**
  ⇒ **真源变了探针立刻响**（不会变成「拿自己的一份副本自证」）。
- 第 2 步：跑 Go 侧 ⇒ 输出 10 条 `CASE|` + 1 条 `OBS|`。
- 第 3 步：与冻结的 `expected.txt` 逐字节 diff。
- 第 4 步：跑 Java 侧 `FavoriteApiEnvelopeParityTest`（**真实** advice / Response / DTO /
  **容器里那个** mapper + 真 HTTP）⇒ 断言的也是**同一份** `expected.txt`。

## 本轮补上的四件事

1. **「Go 侧「非空值 >2048」时的逐字节响应」** —— 原为 🟡 源码级 ⇒ 升级为**活体对拍**。
2. **「两种 int64 编码方式」**（`,string` ⇒ 字符串 / 无 `,string` ⇒ 数字）—— 原为读源码判定 ⇒ **实测**
   （且用 2^53+1 的雪花 ID 证明「写成数字会丢精度」）。
3. **「go-zero 的 `,optional` 不是 `omitempty`」** —— 原为读源码判定 ⇒ **实测**
   （`title`/`cover` 为空时**仍然出现**，值是 `""`）。
4. 🔴 **「Go 的 RecoverHandler 写 500 + text/plain」** —— **原结论是错的**：
   真库是 `w.WriteHeader(500)` ⇒ **空 body、无 Content-Type** ⇒ 已改正并降级为 OBS 段。

## OBS 段：500 兜底的 body 形态（两侧**本该**不同）

| | Go | RAG |
|---|---|---|
| 状态码 | 500 | 500 |
| Content-Type | **无** | `application/json; charset=utf-8` |
| body | **空** | `{"code":5001,"msg":"系统内部错误","data":null}` |

⇒ 登记在 [`docs/Intentional_Deviations/favorite/api.md`](../../../../docs/Intentional_Deviations/favorite/api.md)。
⚠️ **断言的是「差异仍然存在」** —— 哪天两侧变成一致 ⇒ 测试失败 ⇒ 逼人重新评估，而不是让它静默漂移。

## 一处**归一化**（不是漂移）

Go 写 `application/json; charset=utf-8`（分号后**有**空格），Spring 的 `MediaType#toString()`
渲染成 `application/json;charset=utf-8`（**无**空格）。

⚠️ HTTP 媒体类型参数分隔符两侧的空白是**可选**（RFC 9110 §5.6.2 的 OWS）⇒ 任何 HTTP 解析器
都会归一化 ⇒ **没有任何客户端能分辨** ⇒ 属**表达层**差异（AGENTS.md §3）。
⇒ 探针**只对 Content-Type 这一个字段**做 OWS 归一；**body 仍然逐字节比较**。

## 防「拿着自己的一份副本自证」

- Go 侧：三个真源文件**逐字节复制 + sha256 校验**；
- Java 侧：用**真实**的生产类（`GlobalResponseAdvice` / `com.rag.common.response.Response` /
  favorite-api 的 `dto/*`）+ **Spring 容器里那个** `ObjectMapper`（不是自己 `new` 一个）；
- 另有一条用例走**真 HTTP**（`webEnvironment = RANDOM_PORT`）反证
  「advice + 容器 mapper == 真实管线写出的字节」这条等价关系成立。

## 已知边界

- **不覆盖** gRPC 层的 wire（那是 `favorite-proto` 的事 ⇒
  [`tools/proto-wire-probe/favorite/`](../proto-wire-probe/favorite/)）。
- **不覆盖**真 Go favorite-api 进程（仍需 `etcd` + favorite-rpc + Redis）
  ⇒ 本探针覆盖的是**响应字节**这一层，**不需要**起 Go 服务。
