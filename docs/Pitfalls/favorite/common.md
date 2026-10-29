<!-- @new 2026-10-25 · 由 `align/favorite` 工作树产出 · 基线 commit 58e8314 -->

# 踩坑档 · `service/favorite/favorite-common`

> **核对时间：2026-10-25**
> 本档记的是「**为什么会踩**」—— 机制性陷阱，不是缺陷清单。
> ⚠️ 每条都要能回答：「下次怎么不再踩？」

---

## PIT-FAV-COMMON-001 · 🕳️ **「拿我的 stub 验我的 stub」—— 真源 import 了 grpc 就不可能 stub** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-COMMON-001` ⇒ **新号**：`PIT-FAV-18`


### 现象

本域要验的核心机制是 `BizCodeFromError`（业务码的还原规则）。
最省事的做法是：写一个 Go 的 **stub**（把 `status.FromError` 的判定规则手抄一份），
再写一个 Java 的 stub，两边对拍。

### 为什么会踩

`favorite/common/errmsg.go` **import 了 grpc**（`codes` + `status`）⇒
「业务码靠什么还原」这件事**本身就依赖 `status.FromError` 的判定规则**：

- Go 只认**顶层**实现了 `GRPCStatus()` 的 error（**不**沿 cause 链回溯）；
- grpc-go 的 `status.Error(codes.OK, msg)` 返回的是 **nil**（不是 error）；
- `status.Error` 的 description 会被 `BizCodeFromError` **反查**回业务码。

⇒ 只要手抄判定规则，就等于**把自己的理解当成被测对象**
⇒ 手抄错一个分支（最常见：忘了「不沿 cause 链」），
探针会**全绿**并给出「两侧一致」的错误结论。

⚠️ 更隐蔽的一层：article 域的同类探针**没有这个问题**（它的真源**零 import**），
所以「照抄 article 版的做法」到 favorite 就会踩。

### 怎么不再踩

1. **真源 import 了什么，探针就得真依赖什么** ⇒ favorite 版探针用**真 grpc 库**
   （版本从真源 `go.mod` **动态读取**，当前 v1.81.1），
   `GOPROXY=off` + `go mod tidy` 从本地 module cache 解析。
2. **探针不能进 `mvn test`**：它需要 Go toolchain + module cache ⇒ 进 CI 等于给流水线引入新依赖
   ⇒ 它是**独立脚本**，由人（或后续 agent）在改了码表后手动跑。
3. **不可达的差异要单独放 OBS 段**：不参与逐字节对拍，但**断言差异仍然存在**
   ⇒ 若哪天 Java 侧改成与 Go 一致，探针 **exit 2** 提示更新台账，
   而不是让它**静默漂移**。

---

## PIT-FAV-COMMON-002 · 🕳️ **「description 保真」不能靠「构造出的 Status 对象」推断** · 状态：**仍生效** · 核对日期：**2026-10-29** · 影响当前轮次：**是**

> **旧号**：`PIT-FAV-COMMON-002` ⇒ **新号**：`PIT-FAV-19`


### 现象

双侧探针验的是「`FavoriteGrpcError.grpcError(...)` **构造出的** `Status` 对象」，
⇒ 它证明的是「**构造**这一段两侧一致」，
**证明不了**「这个 description 在**真 channel 上传输之后**还是不是那个字节序列」。

### 为什么会踩

本域的业务码**只存在于 description 文案里**（`5304` 走 `FAILED_PRECONDITION`，
而它**不在** Go 的 `BizCodeFromError` switch 白名单里 ⇒ 只能靠文案反查）。
⇒ description 一旦在传输中变形，业务码就**永久退化成 1015**，而且**不报错**。

⚠️ 而「grpc-java 对 description 逐字节保真」这句话本身**是推测**，不是证据
（like 域曾在同一环上踩过坑）。

### 怎么不再踩

⇒ **真 in-process gRPC 跑一次**（`FavoriteGrpcErrorWireFidelityTest`，4 例）：
起真 server + 真 client，让错误**真的过一次 channel**，
然后断言 ① description 与 `FavoriteErrorMessages.getMsg(码)` **逐字节相同**；
② 经 `bizCodeFromError` **能还原回原业务码**；③ 标准 gRPC 码保真；
④ **5304**（唯一只能靠反查的码）也能还原。

⚠️ **并且要做变异**：把 `withDescription(...)` 去掉 ⇒ 3 例转红，
且错误信息会精确展示因果链（`5304→1015`、`5007→5001`）
⇒ 这才证明「测试确实在测这件事」。

⚠️ **另一条同源纪律**：`FavoriteGrpcError.grpcError` 的 javadoc 明写
「**必须交给 `responseObserver.onError(...)`，不能 `throw`**」——
在 `UnaryServerCallListener.onHalfClose` 里 `throw` 会让 description 被替换成
`"Application error processing RPC"` ⇒ **文案当场全丢**。
⇒ 「正确的调用姿势」本身就是一条需要被守住的契约。
