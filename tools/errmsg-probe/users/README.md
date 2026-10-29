# `users` 域错误码契约探针（Go ↔ Java 双侧逐字节对拍）

> **定位：回归守门。** 任何人改了 `users` 域的**码值 / 文案 / 未知码回落语义**，
> 本探针立刻变红（`run-parity.sh` → exit 1）。
> ⚠️ **「跑绿」≠「对」** ⇒ 本目录另带 `mutate-parity.sh`（变异测试）证明它**不是假绿**。

---

## 0. 先回答一个结构问题：**RAG 侧的码→文案映射在哪个类里？**

**在 `service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java:7-38` —— 枚举本身就是那张表。**

| 项 | 位置 | 说明 |
|---|---|---|
| 码表 + 文案表（**同一个类**） | `ErrorCode.java:7-38` | 每个枚举常量**同时**带 code 与 msg（如 `ERROR_DB_SELECT(5003, "查询数据库失败")`）⇒ **没有独立的文案表类**（与 like 域的 `LikeErrorMessages` 不同） |
| 反查表 | `ErrorCode.java:74-85` | `HashMap<Integer, ErrorCode>` 的 code→枚举 缓存（⚠️ 用 HashMap ⇒ 同值常量会被**静默吞掉** ⇒ 探针的 Java 自检专门断言「无重复码」） |
| 查表入口 | `ErrorCode.java:93-95`（`getMsg(int)`）→ `:88-90`（`of(int)`） | 未知码 ⇒ `of(code)` 返回 `ERROR` ⇒ 文案 `"FAIL"` |
| 单例取值 | `ErrorCode.java:58-60`（`getCode()`）· `:69-71`（`getMsg()`） | 调用方（`user-api` / `user-rpc` / `admin-api` / `admin-rpc`）统一用这两个 |

⚠️ `users-common/…/errmsg/` 目录下**只有 `ErrorCode.java` 这一个类**（已 `ls` 核实）⇒
**users 域没有 `*GrpcError` 这样的 gRPC 错误载体类** ⇒ 本探针**没有** WIRE / BIZCODE 层（理由见 §3）。

---

## 1. 真源与被测对象

| 项 | 值 |
|---|---|
| Go 真源（**只读、冻结**） | `Sea-RideTheWind_golang/service/user/common/errmsg/errmsg.go` ⚠️ **是 `user` 不是 `users`** —— Go 侧 users 域 = `service/user/`（`user` 与 `admin` **共用**这一份 errmsg） |
| 真源 sha256 | `508a862a52127e0dbfbd0b206b4b121d346cad3213f0f415ab471025929675d1` |
| 真源包名 | `package errmsg` |
| 真源结构 | `const` 块 **31** 个码（`:3-36`）+ `var codeMsg` **29** 条（`:38-68`）+ `GetErrMsg`（`:70-76`）—— ⚠️ **零 import** |
| 被测 RAG 类 | `service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java`（**唯一一个**） |
| RAG 模块（`mvn -pl`） | `service/users/users-common` |
| 覆盖的 RAG 组件 | `user-api` / `user-rpc` / `admin-api` / `admin-rpc` —— 四个组件**全部**用这一份 `ErrorCode`（已 grep 核实：全仓 `service/users/**` 里**没有第二张**错误码表 / 第二个枚举） |
| 探针半边 | `go/errmsg_probe.go` + `go/main.go`（Go）· `java/Probe.java`（Java） |

### Go 出处 ↔ RAG 出处（语义等价论证，AGENTS.md §3.2）

| 层 | Go 出处 | RAG 出处 | 等价性怎么被证明 |
|---|---|---|---|
| 常量表 | `errmsg.go:3-36`（`const` 块，**31** 个码） | `ErrorCode.java:7-38`（枚举，**31** 个码） | 探针**同包编译**真源并现算 const 块的码集合 ⇒ 与枚举逐条比对（`CONST|`，升序） |
| 码→文案 | `errmsg.go:38-68`（`var codeMsg`，**29** 条）+ `:70-76`（`GetErrMsg`） | `ErrorCode.java:7-38`（枚举自带 msg，**31** 条）+ `:93-95` | `TABLE|`，**按码集合（31 个码）**逐条比对中文文案（UTF-8 字节级）⇒ 两侧条数差 2 的那两行（1017 / 1018）**必然暴露**，不会被「按表遍历」悄悄跳过 |
| 未知码回落 | `errmsg.go:70-76`（未登记 ⇒ `codeMsg[Error]` = `"FAIL"`） | `ErrorCode.java:88-95`（未登记 ⇒ `of(code)` 返回 `ERROR` ⇒ `"FAIL"`） | `FALLBACK|`，24 个跨域撞车码**码值 + 文案**一起比 |

---

## 2. 对拍了哪几层

探针两侧各输出一份**规范化 dump**，脚本逐字节 `diff`：

```
CONST|<code>            31 行 —— 码值集合（升序）
TABLE|<code>|<msg>      31 行 —— 码→文案（中文逐字节）；其中 2 行白名单豁免
FALLBACK|<code>|<msg>   24 行 —— 未知码回落（码值 + 文案）
```

主比对流 = **84 行**（31 CONST + 29 TABLE + 24 FALLBACK；2 条 TABLE 被白名单豁免），
双侧 sha256 相同 ⇒ `239a306dc15afb31634171b95662545bbde69d4987d195a606fd550c1c241c97`。

⚠️ **探针自己不持有任何码值/文案常量** —— Go 半边只调 `GetErrMsg`，
Java 半边只调 `ErrorCode.getMsg(int)` ⇒ 不会出现「拿 RAG 的值验 RAG 的值」的必然假绿（AGENTS.md §3.3）。

⚠️ **Go 侧自检与 like 域不同**：like 域断言「const ≡ codeMsg 键集合」；
users 域的 const 块（31）**比** codeMsg 表（29）多 `1017` / `1018` 两个码
⇒ 这里断言的是 users 域真正成立的不变量「**codeMsg 的键 ⊆ const 的码集合**」，
多出来的那两个由 TABLE 层显式暴露（Go 侧还会往 stderr 打一行提示，见脚本输出）。

---

## 3. OBS 段：**本域 0 条**（理由写明，免得被当成「漏做」）

| 判据 | 本域情况 |
|---|---|
| 有没有「两侧行为必然不同、但语义等价」的不可达差异？ | **没有**。users 域的 errmsg 契约只有三样东西：`const` / `codeMsg` / `GetErrMsg`；真源**零 import** ⇒ **不存在** gRPC 错误载体（业务码放 grpc-status 还是 trailer）这类结构性差异。 |
| 那唯一的已知差异为什么不进 OBS？ | 它（1017 / 1018 的文案）属**已登记的 primitive 级差异**（`DRIFT-USERSCOMMON-001`，2026-10-27 已销号）⇒ 走**白名单**（逐条豁免 + 逐条理由 + 出处 + 「差异仍然存在」断言），比 OBS 更贴切（OBS 段钉的是**行为**差异，白名单钉的是**取值**差异）。 |

> ⚠️ 脚本第 7 步会明确打印「本域无 OBS 条目」及上述理由 ⇒ **不是漏做**。

---

## 4. 白名单（**逐条**豁免，每条带类别 + 理由 + 出处）

白名单是**假绿源** ⇒ 脚本对每条豁免做三重自检：
① 该 key 两侧都必须命中记录（写错 key ⇒ exit 2）；
② 两侧命中的记录集合必须一致；
③ **差异必须仍然存在**（差异消失 ⇒ 豁免失效 ⇒ exit 2 ⇒ 逼人更新台账，而不是让它静默漂移）。

| # | 豁免 key | 类别 | 为什么两侧必然不同 | 出处 |
|---|---|---|---|---|
| 1 | `TABLE\|1017` | **DRIFT-REGISTERED**（已登记 · 2026-10-27 已销号） | Go 真源的 `const` 块声明了 `ErrorRequestParam = 1017`，但 `codeMsg` 表里**没有登记**它 ⇒ `GetErrMsg(1017)` 回落成 `"FAIL"`；RAG 侧的枚举给每个码都带了文案 ⇒ `"请求参数错误"`。⇒ 两侧的 1017 在 wire 上**都不可观察**（Go 侧是**零调用点的死码**；RAG 侧 `user-rpc` 三处**仅日志**、`admin-api` 的 `parseUid` 在 HTTP 入口**不可达**）⇒ 该差异已被判为「**不成立**」并**销号**（不需要任何人拍板）。 | Go `service/user/common/errmsg/errmsg.go:30`（常量）+ `:38-68`（表里没有）+ `:70-76`（回落）；RAG `service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java:34`；销号依据 [`docs/DRIFT/users/common.md`](../../../docs/DRIFT/users/common.md) 的 **DRIFT-USERSCOMMON-001** |
| 2 | `TABLE\|1018` | DRIFT-REGISTERED | 同上：`ErrorAvatarHistoryNotExist = 1018` 声明了但没登记文案 ⇒ Go 回落 `"FAIL"`；RAG 侧是 `"头像历史不存在"`。⇒ 两侧均不可观察 ⇒ 差异不成立（已销号）。 | Go `errmsg.go:31` + `:38-68` + `:70-76`；RAG `ErrorCode.java:35`；同上（DRIFT-USERSCOMMON-001） |

> ⚠️ **这两条不是「已签字偏离」**（`docs/Intentional_Deviations/users/` 里没有错误码相关的签字项；
> `SD-USERSCOMMON-001` 签的是 **bcrypt 前缀**，与本探针无关）⇒ 类别列如实写成 `DRIFT-REGISTERED`，
> 免得后来人把它们当成「已批准的不一致」。
> ⚠️ **没有整域豁免**：2 条豁免各自对应一条具体记录，理由与出处各自不同。

---

## 5. CASES 清单（24 个未登记码）为什么这么选

`FALLBACK|` 层测的是**未知码回落语义**（Go `errmsg.go:70-76` ⇒ `"FAIL"`）。
样本来源是**跨域撞车码** —— 这些码值在别的域是**真实存在的业务码**，但在 `users` 域**没有登记**
⇒ 两侧都必须回落到 `"FAIL"`：

| 样本 | 来源域 | 在源域的语义 | 为什么有价值 |
|---|---|---|---|
| `1013` | `comment`（`ErrorCommentNotExist`） | 真实业务码 | **近邻码**：`users` 登记到 1012（`ErrorRedisUpdate`）、1016~1018；1013 空着 ⇒ 最容易被人「顺手补上」 |
| `2001` / `2002` | `like` / `comment`（`ErrorInputWrong` / `ErrorTypeTransfer`） | 真实业务码 | 2xxx 段的跨域撞车（`users` 域没有 2xxx 码段） |
| `5011` / `5012` | `like`（`ErrorRedisSelect` / `ErrorRedisUpdate`） | 真实业务码 | **近邻码 + 同义码**：`users` 用 **1012** 表示「Redis更新失败」，`like` 用 **5012** ⇒ 号段不同、语义相同 ⇒ 最容易在对拍时看错 |
| `5101`~`5105` | `favorite` / `follow` / `message`（用户不存在 / 已存在 / 密码错 / TOKEN 错 / TOKEN 过期） | 真实业务码 | 5xxx 段的跨域撞车 |
| `5201` / `5202` / `5203` / `5204` / `5205` / `5207` | `favorite` / `follow` / `message`（会话 / 通知 / 待发 / 管理员专属 / 首条过长 / 角色不支持） | 真实业务码 | 5xxx 段 |
| `5301` / `5304` | `favorite`（收藏记录不存在 / 收藏暂不可撤回） | 真实业务码 | 5xxx 段 |
| `0` / `-1` / `1` / `999` | 人工构造 | — | 边界/平凡值：最容易漏的回落分支 |
| `2147483647` / `-2147483648` | 人工构造 | — | **int32 边界**：Go 的 `int` 是 64 位、Java 的 `int` 是 32 位 ⇒ 只取两侧都放得下的值 |

> ⚠️ **不能用本域已登记的码当回落样本**：`users` 登记了 `1012` / `1016`~`1018` / `3001`~`3003`
> ⇒ 测的是「已登记码」而不是「回落语义」。
> ⚠️ **特别提醒 `3001`**：它在 `users` 域是「积分余量不足」，在 `like` 域是「构建 Outbox 消息载荷失败」
> ⇒ **同码不同义** ⇒ 不能当回落样本，只能当「本域已登记码」看 TABLE 层。

---

## 6. 变异测试（证明**不是假绿**）

`mutate-parity.sh` 把 **RAG 侧**的 `ErrorCode.java`（码表 + 文案表是同一个类）
**复制到临时目录** ⇒ 在**副本**上改坏 ⇒ 编译成「影子类」目录
⇒ **前置**到探针的 classpath ⇒ 跑对拍 ⇒ 必须**精确转红**（exit 1）。

> ⚠️ 为什么用影子类而不是直接改仓内源码（`hot` 版 `mutate-parity.sh` 的做法）：
> ① 本 lane 的写权**不含** `service/**`（生产代码一个字都不能动）；② 变异窗口内仓里是坏代码，
> 进程被杀就留下残留；③ 影子类只在临时目录存在 ⇒ **仓内零残留**，可反复跑、可并发跑。
> 影子类与真实类**同 FQN** ⇒ classpath 前置即覆盖 ⇒ 变异对象仍然是「RAG 侧实现」。

### 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-b
bash tools/errmsg-probe/users/mutate-parity.sh ; echo "EXIT=$?"
```

### 实测输出（2026-10-29 · ⚠️ 退出码已于 2026-10-29 统一为 **0 = 变异测试通过**，下面日志里的 `exit 1` 是**统一前**的原始输出）

```
== 基线（未变异）必须转绿 ==
   ✅ 基线绿（exit 0）
   classpath 已取（长度 5739）

== M1 文案漂移：「查询数据库失败」→「查询数据库失败（变异）」==
   ✅ 精确转红（exit 1），红色输出明确指出「查询数据库失败（变异）」
      --- 红色输出关键行 ---
      | 22:+TABLE|5003|查询数据库失败（变异）

== M2 码值漂移：ERROR_DB_SELECT(5003) → (5013) ==
   ✅ 精确转红（exit 1），红色输出明确指出「5013」
      --- 红色输出关键行 ---
      | 29:+CONST|5013
      | 45:+TABLE|5013|查询数据库失败

== M3 回落语义漂移：未知码回落 "FAIL" → "" ==
   ✅ 精确转红（exit 1），红色输出明确指出「-FALLBACK|0|FAIL」
      --- 红色输出关键行 ---
      | 50:-FALLBACK|0|FAIL

✅ 三条变异全部精确转红 ⇒ 探针非假绿（退出码约定：**0 = 全部精确转红 = 通过** · 1 = 有变异没转红 = 守门失效 · 2 = 环境/输入根问题）
```

> ✅ **退出码约定（2026-10-29 已与全仓统一）**：
> `mutate-parity.sh` **exit 0 = 全部变异精确转红（测试通过）** · exit 1 = 有变异没转红（守门是假绿）/ 基线不绿 · exit 2 = 环境 / 输入根探针问题。
> ⇒ **与其余 7 个域（hot / comment / follow / message / like / task）完全一致** ⇒ 可直接 `&& echo ok` 判成功。

### 变异前后的 sha256

| 变异 | 变异前（Go/Java 主比对流 sha256） | 变异后 |
|---|---|---|
| M1 文案 | `239a306dc15afb31634171b95662545bbde69d4987d195a606fd550c1c241c97`（两侧同） | 两侧不等 ⇒ diff 出 `TABLE\|5003` 一行 |
| M2 码值 | 同上 | 两侧不等 ⇒ diff 出 `CONST\|5013` / `TABLE\|5013` 两行 |
| M3 回落 | 同上 | 两侧不等 ⇒ 24 条 `FALLBACK\|` 全部不同 |

（脚本每次都会打印当次 Go / Java 两侧的 sha256 —— 上表左列是基线值，右列是「两侧不等」的定性结论；
变异后的具体 sha256 每次运行都不同，因为临时目录路径会进 diff 的文件头。）

---

## 7. 复现命令（对拍本体）

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/r3-errmsg-b
bash tools/errmsg-probe/users/run-parity.sh ; echo "EXIT=$?"
```

实测（2026-10-29）：

```
   真源 sha256 = 508a862a52127e0dbfbd0b206b4b121d346cad3213f0f415ab471025929675d1  ✓ 与复制件一致
   真源零 import ⇒ 临时 Go 模块零外部依赖 ⇒ 无需 module cache（GOMODCACHE=/Users/pr/go/pkg/mod）
== 2/7 跑 Go 侧（真源 errmsg.go 同包编译 · 零外部依赖）==
   Go 侧提示（stderr）：
   | GO NOTE: const 块里这些码未登记进 codeMsg（GetErrMsg 对它们回落成 "FAIL"）: 1017, 1018
...
   Go   sha256 = 239a306dc15afb31634171b95662545bbde69d4987d195a606fd550c1c241c97
   Java sha256 = 239a306dc15afb31634171b95662545bbde69d4987d195a606fd550c1c241c97
   行数 = 84（CONST + TABLE + FALLBACK）
...
✅ 双侧逐字节一致（sha256 相同，diff 无输出）；白名单 2 条豁免项差异仍在；本域无 OBS 条目（理由见 README §3）
```

**依赖**：`mvn`（离线 `-o`）、JDK 21+、Go toolchain ≥1.25。**不需要** PG / Kafka / Redis / 任何容器。
**环境变量**：`GO_REPO=<真源路径>`（默认向上 4 层搜索 `Sea-RideTheWind_golang`）。

---

## 8. 本探针**没有**覆盖的部分（写明以免被当成「全对」）

| 未覆盖面 | 为什么 | 出处 |
|---|---|---|
| **rpc 层的 gRPC description 文案**（业务码在哪、文案逐字是什么） | 那些文案是**硬编码在 rpc logic 里**的，不在 `errmsg.go` 里 ⇒ 不属于 errmsg 契约；且 Go 侧那些文件 import go-zero ⇒ 无法在临时模块里同包编译 | Go `service/user/user/rpc/internal/logic/*.go`；RAG `service/users/user/user-rpc/.../UserDomainService.java` |
| ⚠️ **观测到的 Go 侧乱码（在本探针范围外，单独登记）** | Go `service/user/user/rpc/internal/logic/getavatarhistorylogic.go:35` 的 description 是 **`"鐢ㄦ埛涓嶅瓨鍦?"`**（UTF-8 被当 GBK 解码后再编码的有损往返），`:38` / `:44` 是 **`"DB鏌ヨ\ue1d7澶辫触"`**；而 RAG 侧对应位置是正常中文 `"头像历史不存在"`（`UserDomainService.java:726` / `:801`）。⇒ **两侧 description 字节不同**，但这是 **Go 源码自身的乱码**（真源冻结，RAG 不应复制乱码），且它**不在 errmsg 契约内** ⇒ 本探针不覆盖、也不判定对错，**只登记事实**。⚠️ 同型签字先例 = `N-2`（`docs/Intentional_Deviations/users/admin/rpc.md:88` 引用）。 | 见左 |
| HTTP envelope 形状（`code`/`msg` 字段名、HTTP 状态码选择） | 属 **api 组件**的契约，不是 errmsg 契约 | — |
| `admin-api/AdminApiSupport.java:55-56,62-63` 里**硬编码**的 `"请求参数错误"` | 那两处直接 new `BusinessException(1017, "请求参数错误")`（绕过了 `ErrorCode` 的文案）⇒ 属 admin 组件的调用点；且该路径在 HTTP 入口**不可达**（`parseUid` 收不到 null/非数字）⇒ 与 §4 的白名单同源 | `docs/DRIFT/users/common.md` 的 DRIFT-USERSCOMMON-001 订正块 |
| `service/common` 的 `BusinessException`（code + msg 载体） | 属**共享层**（`align/g4-service-common` 的写权面）⇒ 本 lane 不碰 | `service/common/src/main/java/com/rag/common/exception/BusinessException.java` |
