# `tools/json-body-probe` —— 「Go 是否读 body」双侧对拍探针

> 定位：**AGENTS.md §3.3「两侧同时跑的探针」**的落地。
> 与 `tools/semantic-probe/`（message 域的 DTO 解析语义）是**两件事**，互不替代。

---

## 一、它守什么

Go 侧「Go 何时读请求体」这条规则，落在 go-zero v1.10.2：

```go
// rest/httpx/requests.go:153-155
func withJsonBody(r *http.Request) bool {
    return r.ContentLength > 0 && strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
}
// rest/httpx/requests.go:117-124
func ParseJsonBody(r *http.Request, v any) error {
    if withJsonBody(r) {
        reader := io.LimitReader(r.Body, maxBodyLen)   // maxBodyLen = 8 << 20（:21）
        return mapping.UnmarshalJsonReader(reader, v)
    }
    return mapping.UnmarshalJsonMap(nil, v)
}
```

⇒ **三个特征**：① 字面子串匹配（`+json` 后缀不命中）· ② **大小写敏感**（`Application/JSON` 不命中）
· ③ **原始头值**（不是 MIME 解析后的 `MediaType`）。

RAG 侧的唯一真源是
`service/common/src/main/java/com/rag/common/http/GoStyleJsonBodyConverter.goReadsBody(...)`。

⇒ 本探针验的就是「RAG 的这层与 Go 的这层**逐条同形**」。

---

## 一之二、🔴 本探针的**覆盖边界**（别拿它当万能网）

| 覆盖 | 不覆盖 |
|---|---|
| **hot 域**的 JSON body 判据与解析层（`GoFormValues.requireParsableJsonBody`） | **共享转换器** `GoStyleJsonBodyConverter` 的 8 MiB 闸（`GO_MAX_BODY_LEN`）⇒ 那条由 `service/common` 的 `GoStyleJsonBodyMaxBodyLenTest` 守 |
| 「读 / 不读 body」这个**判定** | 「阈值是 8 MiB 还是 1 MiB」⇒ 实测：把上限改成 1 MiB，**探针 20/20 仍绿**（两侧都判「拒绝」）⇒ **限值类差异只能由边界测试守** |

⇒ 换句话说：**探针守「判定」，边界测试守「阈值」** ⇒ 两者缺一不可。

---

## 二、怎么用

```bash
bash tools/json-body-probe/run-parity.sh
```

依赖：`mvn`（离线 `-o`）、JDK 21、Go toolchain + 本地 module cache 里的 `go-zero v1.10.2`。

⇒ `EXIT=0` 表示 20 条用例两侧全部一致；`EXIT=1` 表示有真漂移。

---

## 三、🔴 为什么本探针**没有** known-dev 白名单

`tools/semantic-probe/run-parity.sh` 有一份「已签字偏离」白名单 —— 因为它面对的差异**是决策、不可修**。

**本探针没有** ⇒ 因为这 20 条全是「Go 的字面语义」，两侧**必须逐条一致**，
任何一条不一致都是**真漂移** ⇒ 一律计入失败，不允许白名单。

---

## 四、🔴 探针测的是「解析层」，不是「端到端」

⚠️ **这是本探针最容易误读的一点**（踩坑见 `docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-015）。

探针**直接调** `httpx.Parse`（最里层的那道闸），而**端到端**跑的是整条链：

```
MaxConns → Breaker → Shedding(CPU) → Timeout → MaxBytes → … → handler 内的 Parse / ParseJsonBody
                                                              ↑ 8 MiB 的 maxBodyLen 在这一层之后
```

⇒ Go 的 `MaxBytes` **默认 1 MiB 且挂在引擎上全局生效**
（`rest/config.go:53` + `rest/engine.go:163-164`）
⇒ 一个 9 MiB 的 body 在 Go 侧**先被拦成裸 413** ⇒ **根本走不到** `ParseJsonBody`
⇒ ⇒ **端到端那条漂移并不存在**（两侧都是 413）。

⇒ 本探针的红 ⇒ **解析层**不一致；**不自动等于**端到端不一致。

---

## 五、用例矩阵（20 条）

| 组 | 用例 | 判什么 |
|---|---|---|
| ① 判据（大小写 / charset / `+json` 后缀 / 逗号多值头 / 畸形头 / 缺头） | J01–J11 | 「Go 是否读 body」这条规则本身 |
| ② 判据（`Content-Length` 0 / chunked −1） | J12–J13 | 长度这一半 |
| ③ 顶层形态（数组 / null / 数字 / 字符串 / 尾随垃圾 / 合法对象） | J14–J19 | Go 只接受 JSON **对象**；且 `json.Decoder` 只读**一个**值 |
| ④ 8 MiB 上限 | J20 | `maxBodyLen` 的截断点（Go 侧错误详情长度 = 8388643 = 8 MiB + 3） |

---

## 六、结构

```
tools/json-body-probe/
├── go/
│   ├── go.mod / go.sum      ← module jsonbodyprobe，require go-zero v1.10.2（GOPROXY=off 离线）
│   └── main.go              ← Go 半边：真调 httpx.Parse，输出 <用例名>|<判定>|<细节>
├── java/
│   └── Probe.java           ← Java 半边：走 hot 域**真实实现**（GoFormValues.requireParsableJsonBody）
└── run-parity.sh            ← 跑两侧 + 逐行对拍
```

⚠️ **Java 半边的 classpath 用 `target/classes` 而不是 `~/.m2` 里的 jar**
（同 `tools/semantic-probe/run-parity.sh` 的注释）⇒ 否则会验证到旧产物
⇒ 症状是 `NoSuchMethodError`（**运行时**错误，不是编译错误）。
