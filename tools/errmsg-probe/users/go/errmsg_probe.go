package errmsg

import "sort"

// ⚠️ 本文件是 tools/errmsg-probe/users 的「同包探针」，**不属于 users 域的生产代码**。
//
// 它被 run-parity.sh 复制到临时模块里，与真源 errmsg.go **同包同目录**编译，
// 因此可以读到 errmsg.go 里**未导出**的 codeMsg 表 —— 这是「同包」唯一的目的。
//
// ⚠️ 真源是 Sea-RideTheWind_golang/service/user/common/errmsg/errmsg.go
//   （⚠️ 注意是 **user** 不是 users —— Go 侧 users 域 = service/user/）。
//   该文件**零 import**（只有 const / map / 一个函数）⇒ 临时模块**零外部依赖**
//   ⇒ 不需要 `go mod tidy` 拉任何东西，也就不受 module cache 间歇性不可读的影响。
//
// ⚠️ 与 like 版探针的关键差异：users 真源的 **const 块（31 个码）比 codeMsg 表（29 条）多两个**
//   （ErrorRequestParam=1017 / ErrorAvatarHistoryNotExist=1018 只声明了常量、没登记文案）
//   ⇒ 「const ≡ 表键集合」这个 like 版的自检断言在 users 域**不成立**，
//     这里改成「表键 ⊆ const 码集合」（users 域真正成立的不变量），
//     而那两个多出来的码由 TABLE 层（Go 回落 "FAIL" vs RAG 有真文案）显式暴露出来。

// ProbeConstCodes 返回 Go `const` 块登记的全部码值（Go 书写顺序）。
//
// ⚠️ 这是**编译期守卫**，不是运行时数据源：
//   - Go 侧改名或删除任一常量 ⇒ 本文件**编译失败** ⇒ 探针立刻响，不会静默继续；
//   - Go 侧给常量**换值** ⇒ 本文件仍能编译，但与 codeMsg 的键集合不再自洽
//     ⇒ main 里的 Go 自检报错；
//   - Go 侧**新增**常量 ⇒ 与 Java 枚举的码集合不再相等 ⇒ CONST 层对拍变红。
func ProbeConstCodes() []int {
	return []int{
		Success,
		Error,
		CodeServerBusy,
		ErrorServerCommon,
		ErrorDbUpdate,
		ErrorDbSelect,
		ErrorDbInsert,
		ErrorDbTransaction,
		ErrorSnowflakeID,
		ErrorJsonMarshal,
		ErrorJsonUnmarshal,
		ErrorKafkaPush,
		ErrorDelayMsg,
		ErrorUserExist,
		ErrorLoginWrong,
		ErrorUserNotExist,
		ErrorTokenNotExist,
		ErrorTokenTypeWrong,
		ErrorTokenRuntime,
		ErrorTokenRefresh,
		ErrorUserNoRight,
		ErrorUserNoLogin,
		ErrorUserLogined,
		ErrorUserBanned,
		ErrorRedisUpdate,
		ErrorAdminInviteCodeWrong,
		ErrorRequestParam,
		ErrorAvatarHistoryNotExist,
		ErrorPointsInsufficient,
		ErrorPointsRetryExceeded,
		ErrorPointsTimeout,
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
