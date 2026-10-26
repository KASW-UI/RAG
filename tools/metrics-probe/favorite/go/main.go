// ⚠️ tools/metrics-probe/favorite 的 Go 半边，**不是生产代码**。
//
// 它验证一条**Go 语言语义**，并据此判定 favorite 域的 U5 条目：
//
//	Go 的 logic 头部是
//	    func (l *XxxLogic) Xxx(in *pb.Req) (resp *pb.Resp, err error) {
//	        started := time.Now()
//	        defer func() { metrics.ObserveRPC(module, action, started, err) }()
//	        ...
//	    }
//	而错误分支写成
//	    return nil, favoritecommon.GRPCError(codes.Internal, favoritecommon.ErrorDbSelect)
//
//	⚠️ **Go 的 return 语句会把返回值赋给对应的命名返回值**
//	⇒ defer 里的 `err` 非 nil ⇒ ObserveRPC 记的是 **"fail"**，不是 "success"。
//
// 2026-10-23 的台账把这里判成「Go 侧 21 处错误返回因未赋值命名返回值 err 被记成 success」，
// **该结论被本探针证伪** ⇒ U5 撤销（Go 与 RAG 本来就一致）。
//
// ⚠️ 本文件只验「语言语义」，**不**验 favorite 的业务逻辑（那需要 DB / etcd / Kafka）。
//    RAG 侧的对偶守门是 FavoriteRpcMetricsResultTagTest（断言每个失败分支都记 fail）。
package main

import "fmt"

// observeRPC 的 result 判定 —— **逐字复制** Go 真源
// Sea-RideTheWind_golang/service/favorite/rpc/internal/metrics/metrics.go:56-64：
//
//	func ObserveRPC(module, action string, started time.Time, err error) {
//	    result := "success"
//	    if err != nil {
//	        result = "fail"
//	    }
//	    FavoriteRequestCounterMetric.WithLabelValues(module, action, result).Inc()
//	    FavoriteRequestSecondsCounterMetric.WithLabelValues(module, action).Add(time.Since(started).Seconds())
//	}
//
// ⚠️ run.sh 会把真源里这段判定抽出来做 sha256 比对 ⇒ 真源改了本探针立刻响。
func observeRPC(err error) string {
	result := "success"
	if err != nil {
		result = "fail"
	}
	return result
}

// grpcError 模拟 favoritecommon.GRPCError(...)：返回一个非 nil 的 error。
type grpcError struct{ code string }

func (e grpcError) Error() string { return "rpc error: " + e.code }

// ── 下面每个用例都复刻 Go 真源 logic 的**同一形态**：
//    命名返回值 err + defer 里 ObserveRPC(module, action, started, err)
// ── 而**错误分支的写法**按真源逐条复刻（见每条注释里的 Go 出处）。

// case "return-stmt-only"：真源的形态 —— `return nil, favoritecommon.GRPCError(...)`
// （**不**先赋值给 err）。出处：create_favorite_folder_logic.go:86 / :94 / :108 / :112
//      等共 22 处 `return nil, favoritecommon.GRPCError(`。
func caseReturnStmtOnly() (resp string, err error) {
	defer func() { fmt.Printf("RESULT|return-stmt-only|%s\n", observeRPC(err)) }()
	return "", grpcError{"Internal"}
}

// case "assigned-first"：真源的另一种形态 —— 先 `err = ...` 再 return。
// 出处：create_favorite_folder_logic.go:52 / :61 / :68 / :76
func caseAssignedFirst() (resp string, err error) {
	defer func() { fmt.Printf("RESULT|assigned-first|%s\n", observeRPC(err)) }()
	err = grpcError{"InvalidArgument"}
	return "", err
}

// case "success"：成功路径，err 保持 nil。
func caseSuccess() (resp string, err error) {
	defer func() { fmt.Printf("RESULT|success|%s\n", observeRPC(err)) }()
	return "ok", nil
}

// case "nil-nil"：既没有 resp 也没有 err（真源里不该出现；留作边界）。
func caseNilNil() (resp string, err error) {
	defer func() { fmt.Printf("RESULT|nil-nil|%s\n", observeRPC(err)) }()
	return "", nil
}

func main() {
	caseReturnStmtOnly()
	caseAssignedFirst()
	caseSuccess()
	caseNilNil()
}
