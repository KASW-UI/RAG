# L15 · `align/global-rpcboot-tools` —— **守门缺口**：rpc 产物可执行性 + `check-grpc-runtime-classpath.py` 的 `target/` 重复计数

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。
> ⚠️ **favorite 域本 lane 零改动**（域零写权，§五·4）。
> ⚠️ 本 lane **不产出「要用户拍板」的问题**。

## 〇、🔴 写权范围（白名单）

| 类别 | 路径 |
|---|---|
| **可写** | `tools/difftest/check-jar-executable.sh` · `tools/difftest/check-grpc-runtime-classpath.py` · `tools/difftest/baselines/grpc-runtime-classpath.txt` |
| **可写（台账）** | `docs/DRIFT/global/lanes/L15-rpcboot-tools.md` |
| **只读** | `service/**`（**只读**，不改任何服务代码）· `service/favorite/**`（⛔）· `tools/difftest/README.md`（**整合者拥有**）· `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` · `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` |

**分支**：`align/global-rpcboot-tools` · **工作树**：`/Users/pr/Documents/java&golang/RAG.worktrees/global-rpcboot-tools` · **基线**：`align/global` HEAD

---

## 一、目标

三件事，全部是**守门缺口**（不是行为缺陷）：

| # | 缺口 | 落点 |
|---|---|---|
| ① | 🔴 **`check-jar-executable.sh` 只扫 `*-api`** ⇒ **10 个 rpc 模块的产物可执行性从来没被守门查过** ⇒ L11 手工查 pom 时**漏报了 hot-rpc** | `tools/difftest/check-jar-executable.sh` |
| ② | 🔴 **`check-grpc-runtime-classpath.py` 扫到 `target/classes`** ⇒ 同一处违规被算**两遍**（源码 + 构建产物）⇒ **门禁恒红**（实测 `EXIT=1`，而源码树只有 9 处违规） | `tools/difftest/check-grpc-runtime-classpath.py` |
| ③ | 🔴 **baseline 里已修好的条目没人清** ⇒ 修完后脚本会一直打「已失效」提示 | `tools/difftest/baselines/grpc-runtime-classpath.txt` |

---

## 二、🔴 缺口 ② 的实测证据（本 lane 最重要的发现）

**跑法**：`python3 tools/difftest/check-grpc-runtime-classpath.py`（**第 27 轮后的当前 HEAD**）

**结果**：**`EXIT=1`**，报「发现 9 处**未登记**违规」—— 但这 9 条**全部**指向
`service/*/*-rpc/**target**/classes/application.yml`，**没有一条**指向 `src/main/resources/`。

**根因**：`iter_ymls()` 用 `os.walk(service/)` 遍历，**没有排除 `target/`** ⇒
构建产物 `target/classes/application.yml`（**上一次构建留下的旧副本**）被当成源码扫了一遍。

**实测数量**（本工作树）：

```
脚本会扫到的 application.yml 总数: 40
  其中在 target/ 下的:      20   ← 不该扫
  在 src/main/resources 下的: 20   ← 该扫的
```

**排除 `target/` 后**，源码树里的两斜杠违规是 **9 处**（与 baseline 里的 9 条 yml 记录**逐条对应**）：

```
service/like/like-rpc/src/main/resources/application.yml:171      discovery://message-rpc
service/comment/comment-rpc/src/main/resources/application.yml:184  discovery://message-rpc
service/comment/comment-rpc/src/main/resources/application.yml:190  discovery://article-rpc
service/message/message-rpc/src/main/resources/application.yml:148  discovery://user-rpc
service/message/message-rpc/src/main/resources/application.yml:153  discovery://admin-rpc
service/favorite/favorite-rpc/src/main/resources/application.yml:88  discovery://user-rpc   ← favorite 禁区
service/favorite/favorite-rpc/src/main/resources/application.yml:94  discovery://article-rpc ← favorite 禁区
service/article/article-rpc/src/main/resources/application.yml:110  discovery://security-rpc
service/article/article-rpc/src/main/resources/application.yml:116  discovery://message-rpc
```

⇒ **这个门禁目前是恒红的** ⇒ 一个恒红的守门**等于没有守门** ⇒ §3.3 的「跑绿 ≠ 对」的反面：
**「跑红」也可能是假红**。

⚠️ **这个缺陷在第 27 轮之前就存在**（脚本是 2026-10-24 follow 域建的）⇒ **不是本轮引入的**
⇒ 台账里要写成「**存量缺陷，本轮修复**」，不是「本轮引入」。

---

## 三、🔴 修法

### 3.1 缺口 ②：`iter_ymls` / `iter_poms` 排除 `target/`

```python
def _skip_target(dirpath: str) -> bool:
    """构建产物目录必须排除：target/classes 里的是**上一次构建的旧副本**。"""
    return os.sep + "target" + os.sep in dirpath + os.sep
```

⚠️ **为什么要排**：构建产物会**滞后于源码** ⇒ 源码改了、产物没重建 ⇒ 门禁会拿**旧字节**判当前代码
⇒ 结论不可信（这跟 PIT-GLOBAL-019「构建进行中改源码 ⇒ 绿的是旧字节码」是同源问题）。

⚠️ **改完必须复跑**，期望：**只报源码树里的违规**，且**不重复计数**（同一条违规在 src 与 target 各出现一次 ⇒ 现在是 2 条）。

### 3.2 缺口 ①：`check-jar-executable.sh` 纳入 rpc 模块

```bash
ls -d service/*/*-api service/*/*-rpc service/users/*/*-api service/users/*/*-rpc 2>/dev/null | sort
```

⚠️ **注意**：
- `service/favorite/favorite-rpc` **已经在枚举里了** ⇒ favorite-rpc 的产物**是可执行的**（实测 66 MB fat jar）⇒
  **不会**因这个改动而变红 ⇒ **favorite 零影响**（但仍要**记账** ⇒ 台账里写一句）。
- `service/common` **不是** `*-api` 也不是 `*-rpc` ⇒ **不在枚举里** ⇒ **library 模块不会被要求 repackage**
  ⇒ 不会污染下游依赖。⚠️ **但要显式在脚本注释里写明这一点**，防止下一个人把 `service/common` 加进去。
- 脚本的**标题/用法/输出文案**要从「api 模块」改成「api + rpc 模块」⇒ 顺带把 `check-jar-executable.sh`
  的用法说明改对（现在文档里写的是 `.py`，实际是 `.sh` ⇒ **顺手订正**）。

⚠️ **本 lane 不许改 `service/**`** ⇒ **产物不可执行的 rpc 模块由 L13/L14 修**（task-rpc / hot-rpc）
⇒ **本 lane 的脚本改完后，在 L13/L14 合回前会报这 2 个模块红** ⇒ 这是**正确的**（守门该报的必须报）
⇒ 台账里要写明「**本 lane 合回时该脚本预期对 task-rpc / hot-rpc 报红，由 L13/L14 修复后转绿**」。

### 3.3 缺口 ③：baseline 清理

baseline 当前 13 条：

```
service/article/article-rpc/pom.xml|pom|grpc-inprocess                              ← L12 修
service/article/article-rpc/src/main/resources/application.yml|yml|discovery://message-rpc      ← L12 修
service/article/article-rpc/src/main/resources/application.yml|yml|discovery://security-rpc    ← L12 修
service/comment/comment-rpc/src/main/resources/application.yml|yml|discovery://article-rpc       ← L12 修
service/comment/comment-rpc/src/main/resources/application.yml|yml|discovery://message-rpc      ← L12 修
service/favorite/favorite-rpc/pom.xml|pom|grpc-inprocess                             ← ⛔ favorite 禁区，保留
service/favorite/favorite-rpc/src/main/resources/application.yml|yml|discovery://article-rpc    ← ⛔ favorite 禁区，保留
service/favorite/favorite-rpc/src/main/resources/application.yml|yml|discovery://user-rpc       ← ⛔ favorite 禁区，保留
service/like/like-rpc/pom.xml|pom|grpc-inprocess                                    ← L12 修
service/like/like-rpc/src/main/resources/application.yml|yml|discovery://message-rpc            ← L12 修
service/message/message-rpc/src/main/resources/application.yml|yml|discovery://admin-rpc        ← L13 修
service/message/message-rpc/src/main/resources/application.yml|yml|discovery://user-rpc         ← L13 修
service/task/task-rpc/pom.xml|pom|grpc-inprocess                                    ← L13 修
```

⚠️ **关键时序**：baseline 的清理**依赖 L12/L13 的修复结果** ⇒
**L15 必须在 L12/L13 合回之后再清**，否则会**误清还没修的条目** ⇒
**清完必须复跑 `check-grpc-runtime-classpath.py`**，期望：
- **不报任何「未登记违规」**（`EXIT=0`）
- **仍报 favorite 的 3 条**（在 baseline 里 ⇒ 只打印、不失败）
- **不再报「已失效」提示**（除了本 lane 自己在 baseline 里修的那些 ⇒ 修完就消失了）

⇒ **若 L15 与 L12/L13 并行开工** ⇒ baseline 清理**延后到合并后由整合者做**，或
**L15 先只做缺口 ①②，baseline 清理留到整合阶段** ⇒ **本任务书选后者**（见 §六）。

---

## 四、🔴 变异测试（**必须**）

| 变异 | 预期 |
|---|---|
| **M1** 把 `iter_ymls` 的 `target/` 排除去掉 | 脚本**重新报出** `target/classes` 的重复违规 ⇒ 证明排除逻辑**真的在起作用**（否则它可能是恒真的） |
| **M2** 把 `check-jar-executable.sh` 的枚举去掉 `*-rpc` | 脚本**不再报** task-rpc / hot-rpc ⇒ 证明 rpc 枚举**真的在起作用** |
| **M3** 把 baseline 里 favorite 的 3 条删掉 | 脚本**报 favorite 为「未登记违规」⇒ `EXIT=1`** ⇒ 证明 baseline 的「不失败」语义**真的在起作用**（favorite 禁区条目必须仍然只打印不失败 ⇒ **删了它反而会红** ⇒ 这是**故意**的：验证 favorite 违规仍是「已知未修」而非「新增违规」） |

⚠️ **M3 特别说明**：变异完**必须还原** ⇒ favorite 的 3 条**必须留在 baseline 里**
⇒ 这是「favorite 零写权」的**技术保证**（不是靠自律）。

⚠️ **PIT-GLOBAL-018 / 021 / 020** 同前。

---

## 五、🔴 验收标准

- [x] `check-grpc-runtime-classpath.py` 排除 `target/` ⇒ **不重复计数**（M1 变异已证）
- [x] `check-jar-executable.sh` 枚举纳入 `*-rpc` ⇒ **会报出 task-rpc / hot-rpc**（M2 变异已证；这 2 条由 L13/L14 修）
- [x] `check-jar-executable.sh` 的文案/用法同步订正（api ⇒ api + rpc；`.py` ⇒ `.sh`）
- [x] 变异 **M1/M2/M3 全部转红/转绿符合预期**（贴命令输出证据）—— 见 §七·4
- [x] baseline **favorite 的 3 条仍在**（`grep favorite` 必须命中 3 行）
- [x] 台账（本文件）写完：**必须写明「① 脚本恒红（存量缺陷）② rpc 产物从来没被守门查过（漏报 hot-rpc 的根因）」**

## 六、🔴 与其他 lane 的时序（**重要**）

| 事项 | 谁做 | 何时 |
|---|---|---|
| 排除 `target/`（缺口 ②） | **L15** | 本 lane 内 |
| 枚举纳入 rpc（缺口 ①） | **L15** | 本 lane 内 |
| baseline 清理（缺口 ③） | **L15 或整合者** | ⚠️ **依赖 L12/L13 的修复结果** ⇒ 若并行 ⇒ **由整合者在合并后做** ⇒ 本 lane 至少要**准备好要删的清单**（§三·③ 已列） |

⚠️ **本 lane 不许碰 `service/**`** ⇒ **task-rpc / hot-rpc 的 pom 由 L13/L14 改**。
⚠️ **本 lane 不许改 `tools/difftest/README.md`**（整合者拥有）⇒ 脚本用法变更**写进本 lane 台账**，由整合者同步到 README + `AGENTS.md` §7.2。

---

## 七、🔴 完成态（2026-10-26，L15 实测）

> 本节是本 lane 的**完成态台账**（§一~§六 是任务书原文，未改动）。
> **本 lane 只碰 `tools/difftest/` 的 3 个文件 + 本台账** ⇒ **`service/**` 零改动**（含 `service/favorite/**` 零改动）。

### 1. 🔴 缺口 ② 的定性 —— **存量缺陷，本轮修复**（**不是**本轮引入）

**缺陷形态**：`iter_ymls()` / `iter_poms()` 用 `os.walk(service/)` 遍历，**没有排除 `target/`**
⇒ 构建产物 `target/classes/application.yml`（**上一次构建留下的旧副本**）被当成源码扫了一遍
⇒ 同一处两斜杠违规在 `src/main/resources/` 与 `target/classes/` 各数一次
⇒ 两者的 baseline 键不同（`<路径>|yml|<值>` 里路径不同）⇒ `target/` 那份**不在 baseline 里**
⇒ ⇒ **门禁恒红**（**恒红的守门等于没有守门** ⇒ §3.3「跑绿 ≠ 对」的反面：**跑红也可能是假红**）。

**实测证据（修复前，真实树 = 有构建产物的树）**：

```
$ python3 tools/difftest/check-grpc-runtime-classpath.py --root <有 target/ 的树>
❌ 发现 9 处**未登记**违规（门禁失败）：
  · service/like/like-rpc/target/classes/application.yml:151: address `discovery://message-rpc` 只有 2 个斜杠 …
  · service/comment/comment-rpc/target/classes/application.yml:164: …
  · service/comment/comment-rpc/target/classes/application.yml:170: …
  · service/message/message-rpc/target/classes/application.yml:148: …
  · service/message/message-rpc/target/classes/application.yml:153: …
  · service/favorite/favorite-rpc/target/classes/application.yml:88: …
  · service/favorite/favorite-rpc/target/classes/application.yml:94: …
  · service/article/article-rpc/target/classes/application.yml:110: …
  · service/article/article-rpc/target/classes/application.yml:116: …
退出码 1
EXIT=1
```

⚠️ **9 条全部指向 `target/classes/`，没有一条指向 `src/main/resources/`** ⇒ 这正是任务书 §二 的实测结论。

**yml 计数（与任务书一致）**：

```
total: 40   在 target/ 下的: 20（不该扫）   在 src/main/resources/ 下的: 20（该扫）
```

⚠️ **环境说明（如实记录）**：这 40/20/20 的计数**在有构建产物的树上复现**；**本 lane 的工作树是干净检出
⇒ 没有 `target/` ⇒ 只有 20 个 yml（全部在 `src/main/resources/`）** ⇒ 在本工作树上「修前」是 `EXIT=0`、
「修后」也是 `EXIT=0`。⇒ **变异测试因此改用合成 fixture + 真实产物 stage**（§七·4），
否则在干净工作树上 M1 **观察不到任何差异**（恒真的假证明）。

**修法**（`tools/difftest/check-grpc-runtime-classpath.py`）：新增 `_skip_target(dirpath)`，
`iter_ymls()` / `iter_poms()` 各加一行 `if _skip_target(dirpath): continue`。
用**路径分段**判定（`"target" in os.path.normpath(dirpath).split(os.sep)`）而不是子串匹配
⇒ `mytarget/` 这类同前缀目录不会被误伤。

**修复后（同一棵真实树）**：

```
$ python3 tools/difftest/check-grpc-runtime-classpath.py --root <有 target/ 的树>
✅ 无新增违规：
  · 用了 devh grpc-client-starter 的模块，grpc-inprocess 均未降级成 test scope
  · 所有 `discovery:` 形式的 gRPC client 地址均为三斜杠
⚠️ 已知未修（登记在 baseline，不计失败）——13 处：…
EXIT=0
```

⇒ **`target/` 违规归零，EXIT 从 1 变 0；源码树的 13 处已知未修项一条不少、且仍只打印不失败。**

### 2. 🔴 缺口 ① —— **10 个 rpc 模块的产物可执行性从来没有被任何守门查过**

**根因**：`check-jar-executable.sh` 的模块枚举是 `ls -d service/*/*-api service/users/*/*-api`
⇒ **只扫 api** ⇒ **10 个 rpc 模块的产物可执行性从来没被查过**
⇒ ⇒ **第 27 轮 L11 手工查 pom 时漏报了 `hot-rpc`**（手工必漏 ⇒ 这正是要机器守门的理由）。

**实测证据（真实构建产物，逐个解包 `META-INF/MANIFEST.MF`）**：

| 模块 | jar 大小 | `Main-Class` |
|---|---|---|
| `article-rpc` | 104,260,827 B | `…JarLauncher` |
| `comment-rpc` | 104,062,890 B | `…JarLauncher` |
| `favorite-rpc` | 66,464,649 B | `…JarLauncher` ⇒ **favorite 零影响** |
| `follow-rpc` | 63,963,382 B | `…JarLauncher` |
| `hot-rpc` | **58,873 B** | **<<无 Main-Class>>** ⇒ 🔴 缺陷（L14 修） |
| `like-rpc` | 99,851,759 B | `…JarLauncher` |
| `message-rpc` | 69,789,425 B | `…JarLauncher` |
| `task-rpc` | **83,827 B** | **<<无 Main-Class>>** ⇒ 🔴 缺陷（L13 修） |
| `admin-rpc` | 83,171,711 B | `…JarLauncher` |
| `user-rpc` | 83,143,376 B | `…JarLauncher` |

**修法**（`tools/difftest/check-jar-executable.sh`）：

```bash
ls -d service/*/*-api service/*/*-rpc service/users/*/*-api service/users/*/*-rpc 2>/dev/null | sort
```

⇒ 枚举从 **10 个 api** 扩到 **20 个（10 api + 10 rpc）**。

**文案/用法同步订正**（§三·3.2 要求）：

| 位置 | 改前 | 改后 |
|---|---|---|
| 标题 | 守门：**每个 api 模块的 jar** | 守门：**每个 api + rpc 模块的 jar** |
| 用法 | `… # 检查全部 api 模块` | `… # 检查全部 api + rpc 模块`（并补 `service/task/task-rpc` 示例） |
| 输出行 | `检查 %d 个 api 模块的产物可执行性` | `检查 %d 个 api + rpc 模块的产物可执行性` |
| 收尾行 | 全部 api 模块的 jar 均可执行 ✓ | 全部 api + rpc 模块的 jar 均可执行 ✓ |

⚠️ **`service/common` 刻意不在枚举里**（已在脚本注释里**显式写明**，防止下一个人加进去）：
它既不是 `*-api` 也不是 `*-rpc` ⇒ 是 **library 模块**（被别的模块依赖）
⇒ **不得**给它加 `spring-boot-maven-plugin` 的 repackage ⇒ 否则打出的 jar 变成 fat jar
⇒ **污染下游依赖**（依赖方拿到一个内嵌了全部依赖的胖 jar）。

⚠️ **本 lane 合回时该脚本预期对 `task-rpc` / `hot-rpc` 报红** ⇒ 这是**正确的**（守门该报的必须报）
⇒ 由 **L13 / L14** 修好 pom 后转绿。**整合者不要因为「合回就红」而把这两个模块加回豁免。**

### 3. 🔴 缺口 ③ —— **baseline 清理：本 lane 只准备清单，不动 baseline**

**结论：baseline 提交态保持 13 条原样**（favorite 3 条 + 其余 10 条都在）。

**实测依据（为什么现在不能清）** —— 本 lane 实跑了一次「验证性清理」（删掉那 10 条）：

```
$ # 删掉 L12/L13 的 10 条，只留 favorite 3 条
$ python3 tools/difftest/check-grpc-runtime-classpath.py
❌ 发现 10 处**未登记**违规（门禁失败）：
  · service/like/like-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/task/task-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/article/article-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/like/like-rpc/src/main/resources/application.yml:171: …
  · service/comment/comment-rpc/src/main/resources/application.yml:184: …
  · service/comment/comment-rpc/src/main/resources/application.yml:190: …
  · service/message/message-rpc/src/main/resources/application.yml:148: …
  · service/message/message-rpc/src/main/resources/application.yml:153: …
  · service/article/article-rpc/src/main/resources/application.yml:110: …
  · service/article/article-rpc/src/main/resources/application.yml:116: …
退出码 1
EXIT=1
```

⇒ **那 10 处违规在 L12/L13 合回前仍真实存在** ⇒ 提前清 baseline 会让门禁变红（= 把「已知未修」误报成「新增违规」）
⇒ **必须等 L12/L13 合回后再清**。⇒ **本次提交已把 baseline 还原为 13 条原样**
（`git diff -- tools/difftest/baselines/grpc-runtime-classpath.txt` = 空）。

#### 🔴 要整合者在 L12/L13 合回后删掉的 10 条（**清单**）

```
service/article/article-rpc/pom.xml|pom|grpc-inprocess                              ← L12 修
service/article/article-rpc/src/main/resources/application.yml|yml|discovery://message-rpc      ← L12 修
service/article/article-rpc/src/main/resources/application.yml|yml|discovery://security-rpc    ← L12 修
service/comment/comment-rpc/src/main/resources/application.yml|yml|discovery://article-rpc       ← L12 修
service/comment/comment-rpc/src/main/resources/application.yml|yml|discovery://message-rpc      ← L12 修
service/like/like-rpc/pom.xml|pom|grpc-inprocess                                    ← L12 修
service/like/like-rpc/src/main/resources/application.yml|yml|discovery://message-rpc            ← L12 修
service/message/message-rpc/src/main/resources/application.yml|yml|discovery://admin-rpc        ← L13 修
service/message/message-rpc/src/main/resources/application.yml|yml|discovery://user-rpc         ← L13 修
service/task/task-rpc/pom.xml|pom|grpc-inprocess                                    ← L13 修
```

#### ⛔ 必须保留的 3 条（**favorite 禁区**）

```
service/favorite/favorite-rpc/pom.xml|pom|grpc-inprocess
service/favorite/favorite-rpc/src/main/resources/application.yml|yml|discovery://article-rpc
service/favorite/favorite-rpc/src/main/resources/application.yml|yml|discovery://user-rpc
```

⇒ **favorite 域对 global 零写权** ⇒ 这 3 条**永远不能被本 lane 或整合者「顺手清掉」**
⇒ 删掉它们 ⇒ favorite 的已知未修会变成「新增违规」⇒ **门禁变红**（M3 变异已证）。
⇒ **favorite 域自己的修复由 favorite lane 做**，修好后**由 favorite lane 自己**从 baseline 删这 3 条。

### 4. 🔴 变异测试结果（**全部符合预期**）

> ⚠️ **前提**：本 lane 的工作树是**干净检出 ⇒ 没有 `target/`、没有构建产物** ⇒
> 「修前 vs 修后」在干净工作树上**观察不到差异**（M1 会变成假证明，M2 会因「没有产物」而无输出差异）。
> ⇒ 故搭了**两个隔离 stage**（均在 `/tmp`，**不落仓、不碰 `service/**`**）：
> · **`/tmp/l15-fixture`** —— 自包含合成 fixture（1 个模块：src 与 target 各一份同内容 `application.yml` + 1 个 test-scope pom）⇒ 给 M1；
> · **`/tmp/l15-jarstage`** —— `service` 软链到**有真实构建产物的树** ⇒ 给 M2。

| 变异 | 变异内容 | 预期 | **实际** | 证据 |
|---|---|---|---|---|
| **M1** | `check-grpc-runtime-classpath.py` 的 `_skip_target()` 改成 `return False`（= 去掉 `target/` 排除） | 脚本**重新报出** `target/classes` 的重复违规（2 处 ⇒ 3 处） | ✅ **符合预期**：2 处 ⇒ **3 处**，多出 `service/demo/demo-rpc/target/classes/application.yml:4` | 见下 |
| **M2** | `check-jar-executable.sh` 的枚举去掉 `service/*/*-rpc` 与 `service/users/*/*-rpc` | 脚本**不再报** task-rpc / hot-rpc（⇒ 假绿） | ✅ **符合预期**：20 个模块 ⇒ **10 个模块**，**task-rpc / hot-rpc 完全消失**，`EXIT=1` ⇒ **`EXIT=0`**（假绿） | 见下 |
| **M3** | 从 baseline 删掉 favorite 的 3 条 | 脚本报 favorite 为「未登记违规」⇒ `EXIT=1` | ✅ **符合预期**：**3 处未登记违规**（favorite 的 pom + 2 条 yml）⇒ **`EXIT=1`** | 见下 |

**M1 证据**（`--root /tmp/l15-fixture --no-baseline`）：

```
# 修复后
❌ 发现 2 处**未登记**违规（门禁失败）：
  · service/demo/demo-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/demo/demo-rpc/src/main/resources/application.yml:4: address `discovery://demo-rpc` 只有 2 个斜杠 …
退出码 1

# M1 变异后
❌ 发现 3 处**未登记**违规（门禁失败）：
  · service/demo/demo-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/demo/demo-rpc/target/classes/application.yml:4: address `discovery://demo-rpc` 只有 2 个斜杠 …   ← ★ 变异「长」出来了
  · service/demo/demo-rpc/src/main/resources/application.yml:4: address `discovery://demo-rpc` 只有 2 个斜杠 …
退出码 1
```

⇒ **证明排除逻辑真的在起作用**（不是恒真的摆设）：去掉它 ⇒ `target/` 那条立刻出现。
⇒ ⚠️ 变异**已还原**（`grep` 确认 `return "target" in os.path.normpath(dirpath).split(os.sep)` 在位）。

**M2 证据**（`/tmp/l15-jarstage`，真实产物）：

```
# 修复后
检查 20 个 api + rpc 模块的产物可执行性
  ✅  service/article/article-rpc   99M   Main-Class: …JarLauncher
  ✅  service/favorite/favorite-rpc 63M   Main-Class: …JarLauncher      ← favorite 零影响
  ❌  service/hot/hot-rpc            60K   **没有 Main-Class ⇒ java -jar 起不来**   ← L14 修
  ✅  service/task/task-api          63M   Main-Class: …JarLauncher
  ❌  service/task/task-rpc          84K   **没有 Main-Class ⇒ java -jar 起不来**   ← L13 修
  ✅  service/users/user/user-rpc    79M   Main-Class: …JarLauncher
EXIT=1

# M2 变异后（枚举去掉 *-rpc）
检查 10 个 api + rpc 模块的产物可执行性
  …（只有 10 个 api，全部 ✅）
全部 api + rpc 模块的 jar 均可执行 ✓
EXIT=0
```

⇒ **证明 rpc 枚举真的在起作用**：去掉它 ⇒ 两个真实缺陷（hot-rpc / task-rpc）**当场消失** ⇒ 守门变假绿。
⇒ ⚠️ 变异**只在 `/tmp` 的 stage 副本上做**，工作树文件**全程未被污染**（`diff` 确认 stage 副本已还原为工作树版本）。

**M3 证据**（工作树本体）：

```
# M3 变异后（favorite 3 条从 baseline 移除）
❌ 发现 3 处**未登记**违规（门禁失败）：
  · service/favorite/favorite-rpc/pom.xml: `grpc-inprocess` 的 scope 是 `test` …
  · service/favorite/favorite-rpc/src/main/resources/application.yml:88: address `discovery://user-rpc` 只有 2 个斜杠 …
  · service/favorite/favorite-rpc/src/main/resources/application.yml:94: address `discovery://article-rpc` 只有 2 个斜杠 …
退出码 1
EXIT=1
```

⇒ **证明 baseline 的「不失败」语义真的在起作用**：favorite 的 3 条在 baseline 里 ⇒ 只打印不失败；
删掉 ⇒ 立刻变红。⇒ 这是「favorite 零写权」的**技术保证**（不是靠自律）。
⇒ ⚠️ 变异**已还原**：baseline 恢复 13 条，`grep -c 'service/favorite/'` = **3**。

### 5. 🔴 修完后的门禁实测输出（关键行）

```
# A) classpath 门禁（本工作树）
$ python3 tools/difftest/check-grpc-runtime-classpath.py
✅ 无新增违规：
  · 用了 devh grpc-client-starter 的模块，grpc-inprocess 均未降级成 test scope
  · 所有 `discovery:` 形式的 gRPC client 地址均为三斜杠
⚠️ 已知未修（登记在 baseline，不计失败）——13 处：…
EXIT=0

# B) classpath 门禁（真实树，含 target/ 构建产物）—— 修前 EXIT=1，修后 EXIT=0
EXIT=0        # 且输出里 `target/` 命中 0 次

# C) jar 门禁（本工作树，无构建产物 ⇒ 20 个模块都提示「没有产物（未构建？）」）
$ bash tools/difftest/check-jar-executable.sh
检查 20 个 api + rpc 模块的产物可执行性
  ⚠️  service/article/article-api                没有产物（未构建？）
  …（20 行）
EXIT=0

# D) jar 门禁（真实产物）—— 预期对 task-rpc / hot-rpc 报红，由 L13/L14 修
$ bash tools/difftest/check-jar-executable.sh
  ❌  service/hot/hot-rpc                         60K   **没有 Main-Class ⇒ java -jar 起不来**
  ❌  service/task/task-rpc                       84K   **没有 Main-Class ⇒ java -jar 起不来**
EXIT=1
```

### 6. 🔴 要整合者做的事（**本 lane 无权做**）

| # | 事项 | 为什么本 lane 不能做 | 建议落点 |
|---|---|---|---|
| 1 | **L12/L13 合回后**删掉 baseline 里那 **10 条**（清单见 §七·3） | 依赖 L12/L13 的修复结果；提前清 ⇒ 门禁变红 | `tools/difftest/baselines/grpc-runtime-classpath.txt` |
| 2 | **保留** favorite 的 3 条 | favorite 域对 global **零写权** | — |
| 3 | 订正 `AGENTS.md` §7.2 工具档：**`check-jar-executable` 是 `.sh` 不是 `.py`** | `AGENTS.md` 是共享文件，整合者拥有 | `AGENTS.md` 第 328 行那一行的 brace 展开写成了 `check-{grpc-interceptor-wiring,jar-executable,tomcat-limits,global-lane-ownership}.py`，**实际 `check-jar-executable.sh` 是 `.sh`** ⇒ 「复现」列的 `python3 tools/difftest/check-*.py` **跑不到它** |
| 4 | 把 `check-grpc-runtime-classpath.py` **补进** `AGENTS.md` §7.2 工具档 | 同上 | ⚠️ 实测：**该脚本目前在 `AGENTS.md` 里完全没有被提及** ⇒ 一个**没被索引的守门**（follow 域建的，2026-10-24） |
| 5 | 同步 `tools/difftest/README.md` 的脚本用法 | README 由整合者拥有 | ⚠️ 实测：**README 里也没有** `check-jar-executable.sh` / `check-grpc-runtime-classpath.py` 的条目 |
| 6 | `check-global-lane-ownership.py` 的**存量** 5 项写权重叠 | 不是本 lane 的白名单 | ⚠️ **实测现状 `EXIT=1`**：`L6∩L12`(16) · `L7∩L12`(29) · `L8∩L13`(18) · `L8∩L14`(16) · `L9∩L13`(13) ⇒ **5 项全部与 L15 无关**；**L15 与其余 9 条在跑 lane 的交集均为 0**（逐对实测）⇒ **L15 白名单合规** |

### 7. ⚠️ 本 lane 的「未做/不做」声明（诚实记录）

- **未清 baseline**（缺口 ③）⇒ **按任务书 §六 的时序决定**：清理依赖 L12/L13 的修复结果
  ⇒ **清单已备好**（§七·3）⇒ **由整合者在 L12/L13 合回后执行**。
  ⚠️ **本 lane 实跑过一次「验证性清理」并已还原** ⇒ 实测证明**提前清会让门禁变红**（EXIT=1 / 10 处未登记违规）。
- **未改 `service/**`**（含 `service/favorite/**`）⇒ 任务书的 ②③④ 缺口里 **task-rpc / hot-rpc 的 pom 不归本 lane**（L13/L14）。
- **未改 `tools/difftest/README.md` / `AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json`**
  ⇒ 共享文件归整合者（清单见 §七·6）。
- **未改 `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`**。
- **本次提交的文件（4 个，全在白名单内）**：
  `tools/difftest/check-grpc-runtime-classpath.py`（改）·
  `tools/difftest/check-jar-executable.sh`（改）·
  `tools/difftest/baselines/grpc-runtime-classpath.txt`（**净改动 = 0**，仅用于 M3 变异，已还原）·
  `docs/DRIFT/global/lanes/L15-rpcboot-tools.md`（改）。
