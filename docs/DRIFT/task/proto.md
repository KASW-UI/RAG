<!-- @new 2026-10-23 · task-proto 消费方式对照 · 由 `align/task` 工作树产出 -->

# `task-proto` 消费方式对照台账

> **基线 commit**：`8422116`（`do-my-own-gh`）· **工作树**：`RAG.worktrees/task`，分支 `align/task`
> **范围**：`AGENTS.md §3.4` 只保证「`.proto` **文件本身**两侧一致」。
> 本文件补的是 **§3.4 效力边界第 1 条**：**RAG 消费生成代码的方式**是否与 Go 一致。
> ⚠️ 这两件事是不同的，§3.4 的 ✅ **推不出**本文件的结论。

---

## 〇、结论

| 项 | 结论 |
|---|---|
| `.proto` 文件本身 | ✅ **行为层零差异**（本轮重跑逐行 diff 复核） |
| 生成代码是否真产出 | ✅ 真构建实测：7 个 java + `TaskServiceGrpc` |
| **消费方式** | ✅ **本轮首次核对，未发现差异**（3 个风险面逐条核，见下） |
| 已知未跑 | `tools/difftest/check-proto-static.sh` **未覆盖 task** ⇒ 「一致」是一次性快照，不可持续 |

---

## 一、`.proto` 文件本身（复核）

**方法**：剥掉 `option` 行 → 每行首尾空白 → 丢空行 → 逐行 `diff`。

```
diff <(Go 侧规范化) <(RAG 侧规范化)
  → 无输出 ⇒ 行为层零差异
```

| 声明 | Go = RAG |
|---|---|
| message 数 | 3 = 3（`Task` / `GetTaskReq` / `GetTaskResp`） |
| 含字段号声明 | 7 = 7 |
| 字段号 | `name=1 desc=2 task_id=3 completion_progress=4 required_progress=5`；`user_id=1`；`task=1` |
| service | `TaskService { rpc GetTask(GetTaskReq) returns (GetTaskResp); }` |

**唯一差异在 option 块**（§3.4 允许的表达层）：Go `option go_package = "."` → RAG
`option java_package = "com.rag.task.task.proto"` + `java_multiple_files = true`。
⚠️ 与 `comment` 域不同，**task 的 `package` 两侧都是 `task`，未归一** ⇒ 不存在 `comment` 那样的签字偏离。

---

## 二、消费方式（§3.4 效力边界第 1 条）—— 本轮首次核对

### ✅ 风险面 1：`SERVICE_NAME` / 方法全名（**跨语言 wire 契约，最关键**）

| | 值 | 出处 |
|---|---|---|
| Go | `ServiceName: "task.TaskService"`<br>`TaskService_GetTask_FullMethodName = "/task.TaskService/GetTask"` | `pb/task_grpc.pb.go:111` / `:22` |
| RAG（生成代码） | `SERVICE_NAME = "task.TaskService"`<br>fullMethodName = `SERVICE_NAME + '/' + "GetTask"` | `target/generated-sources/.../TaskServiceGrpc.java:15` / `:22` |

⇒ ✅ **一致**。这是本域 proto 消费侧**唯一真正会咬人**的地方：
`package` 一旦被归一（像 `comment` 域那样），两端就再也调不通了，且编译期无任何提示。

### ✅ 风险面 2：`optional` presence 语义

```bash
grep -nE 'optional|oneof|map<|repeated' task.proto
  → 20: repeated Task task = 1;      （仅此一处）
```

- **无 `optional`** ⇒ proto3 默认的「标量零值 = 未设置」在两侧**同义**，无 presence 差异面。
- **无 `oneof` / `map<`** ⇒ 无 oneof 选择子、无 map key/value 编解码差异。
- **唯一一处 `repeated`**：`GetTaskResp.task`，两侧都是「重复字段按序 append、空则缺省」，
  无 presence 语义 ⇒ ✅ 一致。
- ⇒ **§3.4 效力边界第 1 条在本域的 `optional` 子项上不适用**（不是"没验"，是"不存在这个面"）。

### ✅ 风险面 3：字段号被硬编码 / 被别处覆盖

| 检查点 | 结论 |
|---|---|
| RAG 侧是否有用字面量数字构造/索引 proto 字段 | ✅ 无。`TaskDomainService` / `TaskGetService` / 两个 sink 全部用生成代码的 `getUserId()` / `getTaskId()` 等访问器 |
| 是否有 XML / JSON / 配置里写死字段号做映射 | ✅ 无。proto 与 `application.yml` 之间无字段号耦合 |
| RAG 是否有第二份手写 DTO 承载 proto 语义 | ⚠️ 有 `task-api` 的 `TaskInfo`，但它承载的是 **HTTP JSON** 契约（`name`/`desc`/`taskId`/…），不是 proto；字段由 `TaskGetService` 逐个显式搬运，无字段号依赖 |

---

## 三、生成产物（真构建实测，非"pom 里有依赖"）

`mvn -pl service/task/task-proto -am package` 实测产出：

| 项 | 值 |
|---|---|
| 生成的 java | **7** 个 |
| `*Grpc` | `TaskServiceGrpc` |
| 落包 | `com.rag.task.task.proto` |
| `SERVICE_NAME` | `task.TaskService` ✅ |
| 消费方 | `TaskServiceGrpcImpl extends TaskServiceGrpc.TaskServiceImplBase`（server）/ `TaskServiceGrpc.TaskServiceBlockingStub`（client） |

⚠️ **protoc 插件接线**：`task-proto/pom.xml` 的 `<build>` 段已接
`os-maven-plugin` + `protobuf-maven-plugin`（compile + compile-custom）+ `build-helper-maven-plugin` + `maven-enforcer-plugin`，
与 `article-proto` / `message-proto` 逐行同构。
（12:25 时这 4 个 `*-proto` 此前只有空 `pom.xml`，产出空 jar —— 那已是历史，见
[`../_cross/new-domains-2026-10-23.md`](../_cross/new-domains-2026-10-23.md)。）

---

## 四、证据边界

1. ⚠️ **`check-proto-static.sh` 未覆盖 task** ⇒ 本文件所有 ✅ 都是**一次性快照**，
   proto 改了不会有任何测试变红。
2. 🔴 **wire 层未活体验证**：RAG 侧 `task-api` 打 RAG 侧 `task-rpc` 可以通（本域两个组件都在），
   但**两侧都未与 Go 互调过**。Go↔RAG 的 gRPC 互调（真实跨语言）**未做**。
3. ⚠️ 本文件**不覆盖** api 的 JSON 契约（`tasks` / `name` / `desc` / `taskId` / `completionProgress` / `requiredProgress`）——
   那属于 [`api.md`](./api.md) 的范围，且它是 **HTTP JSON 契约**，与 proto 是两套东西。

---

## 五、待办

1. **`tools/difftest/check-proto-static.sh` 扩到 `task`**（照 `user` / `admin` 的写法加 `verdict`）——
   否则本文件无法持续。
2. Go↔RAG 跨语言 gRPC 互调（Go 客户端打 RAG server，验 `SERVICE_NAME` 与字段号在真实网络上成立）。
3. 若将来有人想把 `package task` 改成 `task.proto` 之类（像 `comment` 域那样归一），
   **必须先改本文件并同步改 Go 侧** —— 那会直接断链。

---

## 六、相关文件

| 关系 | 文件 |
|---|---|
| 本域 rpc 台账（proto 的服务端消费方） | [`rpc.md`](./rpc.md) |
| 本域 api 台账（proto 的客户端消费方 + HTTP 契约） | [`api.md`](./api.md) |
| 四域 proto 首轮复制忠实度 | [`../_cross/new-domains-2026-10-23.md`](../_cross/new-domains-2026-10-23.md) |
| §3.4 全局结论 | `AGENTS.md §3.4` |