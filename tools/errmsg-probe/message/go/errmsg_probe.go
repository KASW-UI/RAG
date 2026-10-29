package common

import "sort"

// ⚠️ 本文件是 tools/errmsg-probe/message 的「同包探针」，**不属于 message 域的生产代码**。
//
// 它被 run-parity.sh 复制到临时模块里，与真源 errmsg.go **同包同目录**编译，
// 因此可以读到 errmsg.go 里**未导出**的 `codeMsg` 表 —— 这是「同包」唯一的目的。
//
// 为什么不直接把探针写成 import "sea-try-go/service/message/common"？
//   1. Go 侧「真源不接受写入」⇒ 探针不能进 Go 仓；
//   2. 跨包 import 拿不到未导出的 codeMsg ⇒ 只能验 GetErrMsg 的输出，
//      验不到「表里有没有多余 / 缺失的条目」；
//   3. 真源的 bizCodeByMsg 同样未导出 ⇒ 跨包 import 验不了「文案反查」这条链。
//
// ⚠️ 真源的 errmsg.go **import 了 grpc**（codes + status）⇒ 临时模块**不能零依赖**
//   ⇒ run-parity.sh 继承真源 go.mod / go.sum（只改 module 行），
//     依赖 google.golang.org/grpc v1.81.1 —— 版本与 Go 真源 go.mod:27 完全一致。
//   ⇒ **不用 stub**：stub 的 status.FromError 语义一旦与真库不同，
//     探针就变成「拿我的 stub 验我的 stub」（AGENTS.md §3.3 的假绿陷阱）。
//
// 代价与对策：errmsg.go 本身只有 133 行、无任何包级状态，
// 所以「逐字节复制到临时模块再编译」与「在原模块里编译」产出完全相同的语义。
// run-parity.sh 会用 sha256 校验复制件与真源**逐字节相同**，不匹配直接 exit 2。

// ProbeConstCodes 返回 Go `const` 块登记的全部码值（Go 书写顺序）。
//
// ⚠️ 这是**编译期守卫**，不是运行时数据源：
//   - Go 侧改名或删除任一常量 ⇒ 本文件**编译失败** ⇒ 探针立刻响，不会静默继续；
//   - Go 侧给常量**换值** ⇒ 本文件仍能编译，但与 codeMsg 的键集合不再相等
//     ⇒ main 里的 Go 自检报错；
//   - Go 侧**新增**常量（无论是否同时补了 codeMsg）⇒ 键集合不相等 ⇒ 自检报错。
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
		ErrorUserNotFound,
		ErrorAdminNotFound,

		ErrorSelfChat,
		ErrorConversationMiss,
		ErrorNotificationMiss,
		ErrorChatPending,
		ErrorAdminOnlyInit,
		ErrorFirstMsgTooLong,
		ErrorUnsupportedRole,
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
