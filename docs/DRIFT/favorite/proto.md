<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# DRIFT 台账 · `service/favorite/favorite-proto`

> **核对时间：2026-10-25**
> **本轮性质**：§3.4 **效力边界第 1 条**（「RAG 侧使用生成代码的方式与 Go 一致」）的首次核对。
> **证据强度**：✅ **双侧静态全文 diff**（proto 文件本身）+ ✅ **消费点逐条清点**（源码级）
> + ✅ **双侧活体对拍**（protobuf 消息体的 wire 字节，2026-10-25 第六轮 ⇒ §5）。
> ⚠️ **帧层**：**第十三轮已重新评估并拆成三层** ⇒ L0（帧原始字节）库级、不做比对；
> L1（status code / description / 消息字节 / metadata key）**已双侧活体对拍** ⇒ 见 [`rpc.md`](./rpc.md) §20。

---

## 0. 结论摘要

| 项 | 值 |
|---|---|
| 结论 | ✅ **EQUIVALENT**（在「文件本身 + 消费点构造方式」这一证据强度下） |
| proto 文件 | Go `proto/favorite.proto`（107 行）↔ RAG `favorite-proto/src/main/proto/favorite.proto`（108 行）—— **message/字段/字段号/RPC 逐条同序**（仅 option 块与整体行偏移 1 行之差） |
| `optional` 字段 | **0 个** ⇒ §3.4 效力边界第 1 条在本域**天然不适用** |
| 字段号硬编码 | **无命中** |
| message 构造 | **16 个 message 逐条一致**（字段集合无差异） |
| RPC 方法 | **7 个逐条一致** |
| **wire 字节** | ✅ **双侧活体对拍**（6 组消息，Go 真源生成码 vs favorite-proto 生成码，**两个不同运行时**）⇒ §5 |

---

## 1. §3.4 效力边界第 1 条在本域的结论

AGENTS.md §3.4 的效力边界第 1 条写明：「复制忠实 ≠ 消费方式已验」——
本节逐条核对 favorite 域的**消费方式**。

### 1.1 `optional` presence 语义 ⇒ **本域不存在该风险**

| 检查 | 范围 | 结果 |
|---|---|---|
| RAG proto 有无 `optional` | `service/favorite/**` 全部 `.proto` | **0 命中** |
| Go proto 有无 `optional` | `proto/favorite.proto:1-107` | **0 命中**（`favorite.proto:20-21` 的「可选」是**中文注释**，不是关键字） |
| Go 生成码有无 presence 痕迹 | `favorite.pb.go`（`oneof`/`optional`/`proto3_optional`/`presence`） | **0 命中**；全部字段是 proto3 **隐式 presence** |

⇒ 两侧所有字段都是**隐式 presence** ⇒ 不存在「`hasXxx()` 语义分叉」这一类风险。
⇒ **§3.4 效力边界第 1 条在 favorite 域的第一小项（optional presence）天然不适用。**

> ⚠️ **命名陷阱**：`types.go:18-19` / `:54-55` 的 `json:"title,optional"` 是
> **go-zero HTTP 映射层**的 optional（与 proto 无关）；
> `favorite.proto` 里**一个 `optional` 都没有**。review 时极易混谈。

### 1.2 字段号硬编码 ⇒ **无命中**

RAG 侧 `service/favorite/**` 全量搜索
`setField(\d` / `getField(\d` / `FieldDescriptor` / `FieldNumber` / `fieldNumber` /
`proto3_optional` / `hasField(` / `clearField(` / `getNumber()` / `WireFormat` /
`getDescriptorForType` / `UnknownFieldSet` ⇒ **0 命中**（`e.getField()` 命中 10 处全是
**校验异常的 `getField()`**，与 protobuf 无关）。

⇒ **没有任何一处绕过生成代码硬编码字段号。**

### 1.3 message 构造逐条对照（16 个）

| message | Go 构造点 | RAG 构造点 | 字段集合差异 |
|---|---|---|---|
| `CreateFavoriteFolderReq` | `api/…/create_favorite_folder_logic.go:47-50` | `FavoriteCreateFolderService.java:39-43` | 无 |
| `CreateFavoriteReq` | `api/…/create_favorite_logic.go:50-57` | `FavoriteCreateItemService.java:43-59` | 无 |
| `DeleteFavoriteFolderReq` | `api/…/delete_favorite_folder_logic.go:47-50` | `FavoriteDeleteFolderService.java:39-43` | 无 |
| `DeleteFavoriteReq` | `api/…/delete_favorite_logic.go:47-50` | `FavoriteDeleteItemService.java:42-46` | 无 |
| `UpdateFavoriteFolderNameReq` | `api/…/update_favorite_folder_name_logic.go:48-52` | `FavoriteUpdateFolderNameService.java:39-44` | 无 |
| `GetFavoriteFoldersReq` | `api/…/get_favorite_folders_logic.go:44` | `FavoriteListFoldersService.java:43-46` | 无 |
| `GetFavoritesByFolderReq` | `api/…/get_favorites_by_folder_logic.go:47-50` | `FavoriteListItemsService.java:45-49` | 无 |
| `FavoriteFolder` | `rpc/…/helper.go:65-71` | `favorite-rpc/…/FavoriteRpcSupport.java:174-180` | 无 |
| `FavoriteItem` | `rpc/…/helper.go:75-84` | `favorite-rpc/…/FavoriteRpcSupport.java:190-199` | 无（两侧都**无** `updated_at`，proto 本身也没有） |
| `CreateFavoriteFolderResp` | `rpc/…/create_favorite_folder_logic.go:117` | `favorite-rpc/…/FavoriteDomainService.java:176` | 无 |
| `CreateFavoriteResp` | `rpc/…/create_favorite_logic.go:169` | `favorite-rpc/…/FavoriteDomainService.java:328` | 无 |
| `DeleteFavoriteFolderResp` | `rpc/…/delete_favorite_folder_logic.go:95` | `favorite-rpc/…/FavoriteDomainService.java:399` | 无 |
| `DeleteFavoriteResp` | `rpc/…/delete_favorite_logic.go:95` | `favorite-rpc/…/FavoriteDomainService.java:476` | 无 |
| `UpdateFavoriteFolderNameResp` | `rpc/…/update_favorite_folder_name_logic.go:94` 与 `:126`（**2 处**） | `favorite-rpc/…/FavoriteDomainService.java:528` 与 `:565`（**2 处**） | 无（数量与位置都对应） |
| `GetFavoriteFoldersResp` | `rpc/…/get_favorite_folders_logic.go:62-69` | `favorite-rpc/…/FavoriteDomainService.java:604-612` | 无（两侧都是**无条件 make** ⇒ 空集合是 `[]` 不是 nil） |
| `GetFavoritesByFolderResp` | `rpc/…/get_favorites_by_folder_logic.go:90-97` | `favorite-rpc/…/FavoriteDomainService.java:665-673` | 无（同上） |

> ✅ **HTTP DTO 映射**：两侧**都丢弃 `user_id`**（Go `types.go:42-47` 无该字段；RAG `FavoriteFolder` 亦无）。

### 1.4 RPC 方法（7 个）

proto 定义 `favorite.proto:100-107`（Go）↔ `:101-108`（RAG）—— 名称 / 请求 / 响应类型逐条一致。

| RPC | Go 实现点 | RAG 实现点 | Go api 调用点 | RAG api 调用点 |
|---|---|---|---|---|
| `CreateFavoriteFolder` | `rpc/…/favorite_service_server.go:26-29` | `FavoriteServiceGrpcImpl.java:71-75` | `api/…/create_favorite_folder_logic.go:47` | `FavoriteCreateFolderService.java:46` |
| `CreateFavorite` | `server:31-34` | `FavoriteServiceGrpcImpl.java:77-81` | `api/…/create_favorite_logic.go:50` | `FavoriteCreateItemService.java:62` |
| `DeleteFavoriteFolder` | `server:36-39` | `FavoriteServiceGrpcImpl.java:83-87` | `api/…/delete_favorite_folder_logic.go:47` | `FavoriteDeleteFolderService.java:46` |
| `DeleteFavorite` | `server:41-44` | `FavoriteServiceGrpcImpl.java:89-93` | `api/…/delete_favorite_logic.go:47` | `FavoriteDeleteItemService.java:49` |
| `UpdateFavoriteFolderName` | `server:46-49` | `FavoriteServiceGrpcImpl.java:95-99` | `api/…/update_favorite_folder_name_logic.go:48` | `FavoriteUpdateFolderNameService.java:47` |
| `GetFavoriteFolders` | `server:51-54` | `FavoriteServiceGrpcImpl.java:101-105` | `api/…/get_favorite_folders_logic.go:44` | `FavoriteListFoldersService.java:49` |
| `GetFavoritesByFolder` | `server:56-58` | `FavoriteServiceGrpcImpl.java:107-111` | `api/…/get_favorites_by_folder_logic.go:47` | `FavoriteListItemsService.java:52` |

---

## 2. 「显式设零值」是否等价 —— 两侧都是「总是设」

RAG 侧 7 处用 `nullToEmpty(...)` / `nullToZero(...)` 显式设零值
（`FavoriteCreateItemService:57-58`、`FavoriteCreateFolderService:42`、
`FavoriteUpdateFolderNameService:43`、`FavoriteDeleteItemService:45`、
`FavoriteDeleteFolderService:42`、`FavoriteListItemsService:48`、`FavoriteRpcSupport:196-197`）。

⇒ Go 侧**同样是 struct literal 无条件写零值**（`Title: req.Title, Cover: req.Cover` 等）
⇒ **两侧都是「总是设」而非「有时不设」** ⇒ 在隐式 presence 下 wire 等价。

> ✅ **已验（2026-10-25 第四轮，生成码级证据）**：
> 读 `favorite-proto/target/generated-sources/protobuf/java/com/rag/favorite/favorite/proto/FavoriteFolder.java`
> 的 `writeTo`，**零值有条件表达式**：
> ```
> if (folderId_ != 0L) { output.writeInt64(1, folderId_); }
> if (userId_  != 0L) { output.writeInt64(2, userId_); }
> if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(name_)) {
>     com.google.protobuf.GeneratedMessageV3.writeString(output, 3, name_);
> }
> if (createdAt_ != 0L) { output.writeInt64(4, createdAt_); }
> if (updatedAt_ != 0L) { output.writeInt64(5, updatedAt_); }
> ```
> ⇒ **零值确实不落 wire** ⇒ 与 Go 侧「struct literal 无条件写零值」在隐式 presence 下**wire 等价**。
> ⇒ ⚠️ 取证方法记在这里：生成码**不在版本库内**（`target/` 被忽略）⇒
> 要复现必须先 `mvn -o -pl service/favorite/favorite-proto package` 生成再读 `writeTo`。
>
> ⚠️ 顺带确认：生成码 extends `GeneratedMessageV3`（**protobuf-java full runtime**），
> 而 `favorite-proto/pom.xml` **同时**声明了 `protobuf-javalite`
> ⇒ 两者 `com.google.protobuf.*` 包名重叠。
> ⇒ **实测全仓 10 个 `*-proto` 都是这个模板**（§3）⇒ **不是 favorite 域的新问题**，
> 且 `message-proto` 已被验证过不会遮蔽生成码 ⇒ **不记入漂移条目**。

---

## 3. ⚠️ 一个**不是 favorite 域问题**的观察：`protobuf-java` 与 `protobuf-javalite` 并存

`favorite-proto/pom.xml:33-36`（`protobuf-java`）与 `:52-57`（`protobuf-javalite`）**同时**声明，
两者 `com.google.protobuf.*` 包名重叠。

⇒ **实测：全仓 10 个 `*-proto` 模块全部如此**（`article` / `comment` / `favorite` / `follow` /
`hot` / `like` / `message` / `task` / `users-user` / `users-admin`）⇒ **仓库既有模板**，非本域新问题。
⇒ 其中 `message-proto` 已在 `component-coverage-matrix.md` 记为 ✅ EQUIVALENT（**含消费方式**）
⇒ 该模板已被验证过不会遮蔽生成码。

> ⇒ **本条不记入 favorite 台账的漂移条目**，只留此指针，避免后人重复排查。

---

## 4. 证据边界

| 项 | 状态 |
|---|---|
| proto 文件逐行一致性 | ✅ 双侧静态全文 diff（2026-10-23 已验，本轮复核行号） |
| 消费点构造方式 | ✅ 16 个 message + 7 个 RPC 逐条清点 |
| `optional` presence | ✅ **本域零 optional** ⇒ 不适用 |
| 字段号硬编码 | ✅ 无命中 |
| **「默认值不落 wire」的生成码级证据** | ✅ **已验**（2026-10-25 第四轮 ⇒ 见 §2 的 `writeTo` 条件表达式） |
| **「默认值不落 wire」的【字节级】证据** | ✅ **已验**（2026-10-25 第六轮 ⇒ 探针 `tools/proto-wire-probe/favorite/`：**零值消息在 wire 上是空字节**）⇒ 见 §5 |
| **protobuf 消息体的 wire 字节** | ✅ **已验**（2026-10-25 第六轮 ⇒ **双侧活体对拍**：Go 真源的 `favorite.pb.go`（protoc-gen-go 运行时）vs favorite-proto 的生成码（protobuf-java 运行时）⇒ **6 组消息逐字节一致** ⇒ 见 §5） |
| **真 channel 上的 wire 字节**（gRPC 帧 / status 帧） | 🟡 **已重新评估并拆成三层**（第十三轮 ⇒ [`rpc.md`](./rpc.md) §20.1）<br>· **L0 帧原始字节**（HPACK / 帧序 / WINDOW_UPDATE）⇒ 🔴 **不做比对**：库级、客户端不可观测（已量化到行号：percent-encoding 的 `~` 边界差 1 字节 ⇒ 本域 24 条文案无 `~` ⇒ 不可观测）<br>· **L1 帧层之上、消息层之下**（status code / description / 消息字节 / metadata key）⇒ ✅ **已双侧活体对拍**（`tools/grpc-contract-probe/favorite/`，36 个 case：33 SAME / 2 SAME_CODE / 1 OBS ⇒ **业务契约逐字节一致**）<br>· **L2 消息体字节** ⇒ ✅ 见本表上一行 |

---

## 5. ✅ 第六轮新增：protobuf 消息体的 wire 字节**双侧活体对拍**

> 🔴 **这一节把 §4 的最后一条「🔴 未验」拆成了两半**：**消息体字节**这一半已闭合，
> **帧层**那一半仍未闭（需要两侧同时起服务）。

**新工具**：`tools/proto-wire-probe/favorite/`（**EXIT=0**）

| | Go 侧 | RAG 侧 |
|---|---|---|
| 代码 | **真源真正在用的** `service/favorite/rpc/pb/favorite.pb.go`（**逐字节复制 + sha256 校验**） | favorite-proto 的生成码 |
| 运行时 | `google.golang.org/protobuf`（protoc-gen-go 的运行时） | `protobuf-java`（protoc 的 Java 运行时） |

⇒ ⚠️ **两侧运行时不同** ⇒ 这**不是**「同一份实现自证」。

| # | 用例 | 钉住的事实 | 结果 |
|---|---|---|---|
| 1 | `folder_full` | 全字段的 varint / length-delimited（用 2^53+1 的雪花 ID ⇒ **int64 在 wire 上不丢精度**） | ✅ 逐字节一致 |
| 2 | `folder_zero` | 🔴 proto3 的标量零值**不落 wire** ⇒ 必须是**空字节** | ✅ 逐字节一致（两侧都是**空**） |
| 3 | `item_full` | 8 个字段的完整编码 | ✅ 逐字节一致 |
| 4 | `item_partial` | 🔴 **只设了的字段才落 wire**（空字符串**不**落 —— 那是 JSON 的行为，不是 proto3 的） | ✅ 逐字节一致 |
| 5 | `folders_resp_two` | 🔴 `repeated` 的编码（两次 tag 1 + length-delimited，不是 JSON 数组） | ✅ 逐字节一致 |
| 6 | `create_req` | 请求消息：`int64` 在 wire 上是 **varint**，**不是**字符串（字符串是 api 层的 JSON 约定） | ✅ 逐字节一致 |

**回归网**：`FavoriteProtoWireParityTest`（**6 例**，`favorite-rpc` 模块）
⚠️ **为什么住在 favorite-rpc 而不是 favorite-proto**：`favorite-proto` 的 pom 里
**一行 junit 依赖都没有** ⇒ 往那儿加测试要先改 pom ⇒ 放在**消费方**（它本来就依赖 proto）。
⇒ 副作用：proto 组件从「**0 例测试**」变成「有 1 个字节级守门」。

**它把哪条证据升级了**：§2 此前记的「零值不落 wire」只有**生成码级**证据
（读 `FavoriteFolder.writeTo` 的条件表达式）⇒ **本轮升级为字节级**。

**仍未闭的**：**帧层**（HTTP2 / length-prefix / gRPC status 帧）⇒ 需要两侧同时起服务。
⇒ ✅ **第十三轮已闭**（见 [`rpc.md`](./rpc.md) §20）：L0 判定为库级、不可观测；**L1 已双侧活体对拍**。

---

## 6. 复现命令

```bash
cd /Users/pr/Documents/java&golang/RAG.worktrees/favorite

# proto 文件逐行对照（剥 option / 去空白 / 丢空行后 diff）
bash tools/difftest/check-proto-static.sh

# 生成码产出（33 java + FavoriteServiceGrpc）
mvn -o -pl service/favorite/favorite-proto -am package

# 🔴 消息体 wire 字节的双侧活体对拍（Go 真源生成码 vs favorite-proto 生成码）
bash tools/proto-wire-probe/favorite/run.sh
echo $?   # 0 = 6 组消息逐字节一致
```
