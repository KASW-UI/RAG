# `tools/g3-anchor-check` —— Go 出处引用漂移守门（域内）

## 它挡什么

RAG 的 javadoc / `@DisplayName` / 断言消息里经常写「**Go: 某某.go:123**」这种出处引用。
这类断言是**高危项**（AGENTS.md §3.3.2）：它断言了一个事实，
**却改一行断言都不会有任何测试变红** ⇒ ⇒ **绿测试可能守着一个错的事实**。

本守门抽掉 RAG 源码里全部 `*.go / *.sql / *.yaml / *.yml:行号` 形态的引用，
把 `文件:行号` **拿回真源读那一行**，判定：

| 分类 | 含义 |
|---|---|
| `OK` | 该行存在且非空 |
| `AMBIGUOUS` | 同名文件有多个候选（机器判不了，**需人读**） |
| `LINE_OOB` | **行号越界**（末尾越过 EOF）⇒ 典型是「文件改了 / 行号写错」 |
| `FILE_MISSING` | 文件找不到，**或出处缺文件名**（如 `_test.go:35`）⇒ 不可核 |
| `EMPTY_LINE` | 引用指到一行空白 |
| `VERSION_MISMATCH` | **引的不是真源 `go.mod` pin 的那一份** ⇒ 它指到**另一份内容不同的副本** ⇒ 行号照样「在范围内」⇒ **别的判据抓不到** |

## 判据口径（零误报）

**只要求「那一行存在且非空」** ⇒ **不做内容匹配** ⇒ 零误报。
⚠️ 代价：判据是「行存在」，**不是「行内容对得上」** ⇒
**内容级错配不在覆盖内**（例如「引用了 `sdk/trace/span.go:631-655`，但那一段讲的不是本件事」）⇒
⇒ **这类必须人读**，不得因为守门绿了就认为出处全对。

## 覆盖的真源根（缺一个就会把「我解析不了」伪装成「引用是错的」）

- Go 真源仓 `Sea-RideTheWind_golang`
- Go **标准库**（`$GOROOT/src`）
- **module cache** —— 按真源 `go.mod` pin 的版本（go-zero v1.10.2 / gorm **v1.31.1** /
  prometheus client_golang **v1.23.2** / golang-jwt v4.5.2 / otel v1.44.0 / grpc v1.81.1 …）
- **RAG 自己的**文件（`application.yml` / `db/migration/*.sql`）

解析器要处理多种书写形态，缺一个就会误报：

- **多段**模块路径（`gorm.io/gorm/...`）
- 省略 **host 段**（`golang-jwt/jwt/v4/...`、`prometheus/client_golang/...`）
- `@版本` 粘在**前缀区任意一段**上（`gorm.io/gorm@v1.31.1/...`、`go-redis@v9.20.0/...`）
- `@版本` **独立成段**（`v1.31.2/callbacks/update.go`）
- 末尾大版本段可省（`go-redis` ⇒ `go-redis/v9`）
- `.../` 是「同上」省略前缀

## 用法

```bash
# 扫 G3 四域（默认）
python3 tools/g3-anchor-check/check-anchors.py

# 扫指定域 / 全部域
python3 tools/g3-anchor-check/check-anchors.py --domains article
python3 tools/g3-anchor-check/check-anchors.py --all

# 变异验证（证明守门非假绿）
bash tools/g3-anchor-check/mutate-anchors.sh
```

退出码：`0` = 无漂移；`1` = 有待查项；`2` = 自身故障（例如一份 java 都没扫到）。

## 变异验证（非假绿）

`mutate-anchors.sh` 跑四条变异，**每条都必须精确转红**，且**还原后必须重新转绿**：

| 变异 | 期望分类 |
|---|---|
| M1 行号越界（末尾越过 EOF） | `LINE_OOB` |
| M2 文件指错（行号对 · 文件错） | `LINE_OOB` |
| M3 版本写错（引的不是真源 pin 的那一份） | `VERSION_MISMATCH` |
| M4 出处缺文件名 | `FILE_MISSING` |

## 写权

按 AGENTS.md §5.3f「**共享判据 = 依赖度数，不是目录位置**」：
本目录**只被 G3 四域引用** ⇒ **真私有** ⇒ **G3 域侧落码合法**。
（`tools/difftest/check-wave-lane-ownership.py` 的 `SHARED` 清单里只有 `tools/difftest/README.md` 一条。）

## 台账落点

四域各自的订正明细见 `docs/DRIFT/<域>/vs-common.md` §7（守门本体说明见 `article/vs-common.md` §7.1）。
