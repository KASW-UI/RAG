# favorite 域 · Go 断言锚点审计器

> **政策出处**：AGENTS.md §3.3.2 ——「任何以「Go: …」措辞出现的断言，必须满足 §3.3.1 的三条，
> 否则**不得**用该措辞，改成中性描述（写「本模块要求的语义：…」）或直接删掉该断言。」
> §3.3.1 要求：判断某个 Go 默认行为是否生效，必须读到**消费该 tag 的那段控制流**，
> 只贴 struct 定义行不算证据 ⇒ **断言必须能指到具体行**。

本目录把这条从「靠人读」变成「机器可验」。**它审计的是注释，不是行为**——
行为漂移由 `docs/DRIFT/favorite/` 的台账与六个双侧探针负责。

---

## 一、跑法

```bash
cd <RAG 仓库根>
python3 tools/assertion-audit/favorite/check-assertion-sources.py
```

退出码：`0` = 无门禁级发现 · `1` = 有门禁级发现 · `2` = 环境不满足。

常用参数：

| 参数 | 作用 |
|---|---|
| `--json` | 输出机器可读结果（门禁发现存在时 EXIT=1） |
| `--strict` | 把提示级（I\*）也算失败 |
| `--root PATH` | 指定 RAG 仓库根（默认由脚本位置推导） |
| `--go-root PATH` | 指定 Go 真源根（默认 `<仓库父目录>/Sea-RideTheWind_golang`） |
| `--modcache PATH` | 指定 Go module cache（默认 `go env GOMODCACHE`） |

---

## 二、锚点怎么解析

锚点形如 `<路径片段>.go:<起>[-<止>]`。解析顺序：

1. **Go 真源**（`Sea-RideTheWind_golang/`）—— 业务代码。
2. **锁定版本的依赖** —— 真源 `go.mod` 里 pin 住的版本，在本机 module cache
   （`$(go env GOMODCACHE)`）里取对应目录。
   ★ 这条是关键：go-zero 的 `rest/handler/authhandler.go` 这类**裸文件名**不写全路径
   读者根本定位不到，而锚点定位不到 = 断言不可核验 = §3.3.2 的高危项。
3. **Go 标准库** —— `$(go env GOROOT)/src`（同时提供 `src/…` 前缀写法）。

多候选时的消歧顺序：**同域 → 同模块 → basename**。

- **同域**：优先 `service/favorite/**`。
- **同模块**：favorite 的 api 侧与 rpc 侧**各有同名 logic 文件**
  （`create_favorite_logic.go` 等各一份）。只按同域筛仍剩两个候选 ⇒
  按 Java 文件所属模块（`favorite-api` ⇒ api 侧，`favorite-rpc` ⇒ rpc 侧）再筛一次。

---

## 三、发现分类

### 门禁级（EXIT=1）

| 类 | 含义 | 判据 |
|---|---|---|
| **F1** | 缺锚点 | 断言块里一个 `*.go:NN` 都没有 |
| **F2** | 锚点悬空 | 锚点里的 .go 文件在真源、锁定依赖、标准库里都找不到 |
| **F3** | 行号越界 | 文件找到了，但锚点行号超出该文件行数（行号过期） |
| **F4** | 引用不符 | 块里 `<pre>` 引用的 Go 源码**落在锚点行段之外** ⇒ 锚点指到了别的函数 |

### 提示级（EXIT=0）

| 类 | 含义 |
|---|---|
| **I1** | 内联注释里的 Go 断言没有锚点（§3.3.2 字面只点名 javadoc / @DisplayName，故只提示） |
| **I2** | 锚点不能唯一定位（同名文件存在于多处） |
| **I3** | `<pre>` 引用的 Go 源码在锚点文件里找不到（多半是节选或改写） |
| **I4** | 锚点只写了文件名（消歧后唯一，读者需按约定推断） |

---

## 四、F4 是最有价值的一类

它是唯一能自动抓出「**锚点指到了别的函数**」的检查 —— 人工 review 极易漏。

做法：把 javadoc `<pre>` 里形似 Go 的片段逐字归一（解实体、去注释前缀、tab→空格、去空行），
在锚点文件里定位它的**真实行号**，然后断言这个行号落在锚点声明的行段内。

**实测抓到过的真缺陷**（favorite 域第九轮）：

| 缺陷 | 现象 | 真值 |
|---|---|---|
| `rest/httpx/requests.go:129-137` 被标为 `ParseJsonBody` | 实际 129-137 行是 `ParsePath` | `ParseJsonBody` 在 **117-124** |
| `zrpc/config.go:29` 被标为 `Timeout` 字段 | 实际 29 行是 `Token` | `Timeout` 在 **31** |

两条都是**断言内容正确、锚点指错行** —— 读者按锚点跳过去会看到不相干的代码。

---

## 五、已知的判定边界

1. **只审计注释，不审计行为。** 一个断言没有锚点，不代表行为错；
   它只代表这条断言**不可核验**。
2. **I3 多数是「节选」而非「错误」。** javadoc 里引用的 Go 片段常被重排成单行
   （`if p == "" { return "/" }`），逐字比对必然不匹配 ⇒ 只提示不拦。
   但如果注释声称「逐字复刻」，就该把引用补成逐字版，或改措辞为「等价复刻」。
3. **消歧靠约定，不靠语义。** 「同域 → 同模块 → basename」是**本域**的约定；
   跨域引用（如 users 域的 `checkblacklistmiddleware.go`）会被报成 I2 —— 那是真的，
   因为它与 favorite 的 `checkblacklist_middleware.go` 只差一个下划线。
4. **标准库按 GOROOT 当前版本解析。** Go 升级后行号可能变 ⇒ F3 会报出来。

---

## 六、为什么不用现成的 lint

`golangci-lint` / `checkstyle` 都不校验「注释里的 Go 断言能否被核验」——
它们不知道 Go 真源在哪、也不知道哪些行号是「消费点」而非「声明点」。
§3.3.1 要的正是后者：**行号指向消费点**。

---

## 七、域内调用点

favorite 域的四个组件（api / rpc / common / proto）都在扫描范围内。
脚本的扫描目录写死在 `DEFAULT_SCAN_GLOBS`：

```python
DEFAULT_SCAN_GLOBS = (
    "service/favorite/*/src/main/java",
    "service/favorite/*/src/test/java",
)
```

**新增组件时**（例如以后加 `favorite-worker`）记得把它的路径补进来 ——
否则它的注释会**静默地不被审计**（这正是 §3.3 反复强调的「静默失效」形态）。
