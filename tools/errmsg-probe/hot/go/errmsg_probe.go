package common

import "sort"

// ⚠️ 本文件是 tools/errmsg-probe/hot 的「同包探针」，**不属于 hot 域的生产代码**。
//
// 它被 run-parity.sh 复制到临时模块里，与真源 errmsg.go **同包同目录**编译，
// 因此可以读到 errmsg.go 里**未导出**的 codeMsg 表 —— 这是「同包」唯一的目的。
//
// 为什么不写成 import "sea-try-go/service/hot/common"？
//   1. Go 侧「真源不接受写入」⇒ 探针不能进 Go 仓（AGENTS.md §一：真源冻结）；
//   2. 跨包 import 拿不到未导出的 codeMsg ⇒ 只能验 GetErrMsg 的输出，
//      验不到「表里有没有多余/缺失的条目」；
//   3. 真源 errmsg.go **import gRPC**（codes/status，用于 GRPCError / BizCodeFromError）
//      ⇒ 临时模块必须继承真源的 go.mod / go.sum ⇒ 依赖从 module cache 离线解析。
//
// 代价与对策：run-parity.sh 用 sha256 校验复制件与真源**逐字节相同**，不匹配直接 exit 2。
//
// ⚠️ 与 article 版探针的差别（照抄会踩）：
//   hot 的 errmsg.go 里**有 grpc import** ⇒ 临时模块**不能**用
//   「module xxx + go 1.25」这种零依赖 go.mod（会因缺 grpc 而编译失败）
//   ⇒ 必须继承真源 go.mod（改 module 行）+ go.sum。

// ProbeConstCodes 返回 Go `const` 块登记的全部码值（Go 书写顺序）。
//
// ⚠️ 这是**编译期守卫**，不是运行时数据源：
//   - Go 侧改名或删除任一常量 ⇒ 本文件**编译失败** ⇒ 探针立刻响，不会静默继续；
//   - Go 侧**新增**常量 ⇒ 本文件仍能编译，但与 codeMsg 的键集合不再相等
//     ⇒ main 里的 Go 自检报错；
//   - Go 侧给常量**换值** ⇒ codeMsg 用的是常量标识符作键，表会跟着变
//     ⇒ 两侧自检都发现不了 ⇒ 由**与 Java 侧的逐字节比对**兜住（Java 侧是写死的数值）。
//
// 换句话说：Go 侧 const 块的任何改动都会让探针变红，而不会像「手抄期望值」那样
// 需要人工记得同步 —— 这正是 AGENTS.md §3.3「绿测试可能是错误答案的守卫」的反面做法。
func ProbeConstCodes() []int {
	return []int{
		Success,
		Error,
		CodeServerBusy,
		ErrorServerCommon,
		ErrorNotFound,
		ErrorInvalidParam,
		ErrorUnauthorized,
		ErrorForbidden,
		ErrorAlreadyExist,
		ErrorDbSelect,
		ErrorDbUpdate,
	}
}

// ProbeTableCodes 返回 `codeMsg` 表的全部键（**升序**）。
//
// ⚠️ 必须排序：Go 的 map 遍历顺序是**随机**的，不排序两侧输出无法逐字节比较。
func ProbeTableCodes() []int {
	keys := make([]int, 0, len(codeMsg))
	for k := range codeMsg {
		keys = append(keys, k)
	}
	sort.Ints(keys)
	return keys
}
