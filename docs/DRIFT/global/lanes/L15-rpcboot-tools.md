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

- [ ] `check-grpc-runtime-classpath.py` 排除 `target/` ⇒ **不重复计数**
- [ ] `check-jar-executable.sh` 枚举纳入 `*-rpc` ⇒ **会报出 task-rpc / hot-rpc**（这 2 条由 L13/L14 修）
- [ ] `check-jar-executable.sh` 的文案/用法同步订正（api ⇒ api + rpc；`.py` ⇒ `.sh`）
- [ ] 变异 **M1/M2/M3 全部转红/转绿符合预期**（贴命令输出证据）
- [ ] baseline **favorite 的 3 条仍在**（`grep favorite` 必须命中 3 行）
- [ ] 台账（本文件）写完：**必须写明「① 脚本恒红（存量缺陷）② rpc 产物从来没被守门查过（漏报 hot-rpc 的根因）」**

## 六、🔴 与其他 lane 的时序（**重要**）

| 事项 | 谁做 | 何时 |
|---|---|---|
| 排除 `target/`（缺口 ②） | **L15** | 本 lane 内 |
| 枚举纳入 rpc（缺口 ①） | **L15** | 本 lane 内 |
| baseline 清理（缺口 ③） | **L15 或整合者** | ⚠️ **依赖 L12/L13 的修复结果** ⇒ 若并行 ⇒ **由整合者在合并后做** ⇒ 本 lane 至少要**准备好要删的清单**（§三·③ 已列） |

⚠️ **本 lane 不许碰 `service/**`** ⇒ **task-rpc / hot-rpc 的 pom 由 L13/L14 改**。
⚠️ **本 lane 不许改 `tools/difftest/README.md`**（整合者拥有）⇒ 脚本用法变更**写进本 lane 台账**，由整合者同步到 README + `AGENTS.md` §7.2。
