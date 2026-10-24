package main

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/zeromicro/go-zero/core/breaker"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// ---------------------------------------------------------------------------
// ⚠️⚠️ 下面是 go-zero **internal 包**里那份判据的**逐字副本**。
//
//   真源：github.com/zeromicro/go-zero@v1.10.2/zrpc/internal/codes/accept.go
//   （`zrpc/internal/...` 是 internal 包 ⇒ **外部模块无法 import** ⇒ 只能复制）
//
// ⇒ run-parity.sh 会**从本地 module cache 里把 accept.go 原样抽出来逐字比对**，
//   不一致就 **exit 1** ⇒ go-zero 改了这份判据时，探针会立刻炸，不会悄悄失配。
// ===========================================================================

// acceptable 是 Go `zrpc/internal/codes.Acceptable` 的逐字副本（accept.go:9-17）。
func acceptable(err error) bool {
	switch status.Code(err) {
	case codes.DeadlineExceeded, codes.Internal, codes.Unavailable, codes.DataLoss,
		codes.Unimplemented, codes.ResourceExhausted:
		return false
	default:
		return true
	}
}

// allCodes 的**顺序**必须与 Java 侧 Probe.java 逐条一致（探针按序号配对两侧结果）。
var allCodes = []codes.Code{
	codes.OK, codes.Canceled, codes.Unknown, codes.InvalidArgument, codes.DeadlineExceeded,
	codes.NotFound, codes.AlreadyExists, codes.PermissionDenied, codes.ResourceExhausted,
	codes.FailedPrecondition, codes.Aborted, codes.OutOfRange, codes.Unimplemented,
	codes.Internal, codes.Unavailable, codes.DataLoss, codes.Unauthenticated,
}

func main() {
	out := bufio.NewWriter(os.Stdout)
	defer out.Flush()

	// ── A 组：失败判据（6 码白名单）—— 必须**逐条相同** ────────────────────
	// ⚠️ 打印**序号**而不是码名：Go 的 codes.Code.String()（"DeadlineExceeded"）与
	//    Java 的 Status.Code.name()（"DEADLINE_EXCEEDED"）拼写不同 ⇒ 用序号配对才可靠。
	for i, c := range allCodes {
		fmt.Fprintf(out, "A %d acceptable=%v\n", i, acceptable(status.Error(c, "")))
	}

	// ── B 组：锚点 —— 冷启动保护窗口内**必须全部放行**（dropRatio<=0，与随机源无关）──
	brk := breaker.NewBreaker(breaker.WithName("probe"))
	prefix := ""
	for i := 0; i < 5; i++ {
		if err := brk.DoWithAcceptable(func() error { return nil }, acceptable); err != nil {
			prefix += "D"
			continue
		}
		prefix += "A"
	}
	fmt.Fprintf(out, "B prefix=%s\n", prefix)

	// ── C 组：分布 —— 大量失败后的拒绝率（只能按统计比对，见 README）────────
	// ⚠️ 失败必须用**不可接受**的错误（INTERNAL 在 6 码名单里）⇒ 才会被记成 failure。
	//    若用 fmt.Errorf("boom")（普通 error ⇒ status.Code=UNKNOWN ⇒ 可接受）
	//    ⇒ Go 会记成 **success** ⇒ 与 RAG 侧 markFailure 不同源 ⇒ 分布不可比。
	brk2 := breaker.NewBreaker(breaker.WithName("probe2"))
	for i := 0; i < 50; i++ {
		_ = brk2.DoWithAcceptable(func() error { return nil }, acceptable)
	}
	// ⚠️ 必须跨过一个桶边界（>250ms）：Go 的 history() **忽略当前未完成桶**
	//    （collection/rollingwindow.go:70-72「ignore the current bucket, because of partial data」）
	//    ⇒ 不跨桶的话失败根本不进窗口 ⇒ 拒绝率恒为 0，测不出任何东西。
	time.Sleep(300 * time.Millisecond)
	for i := 0; i < 50; i++ {
		_ = brk2.DoWithAcceptable(func() error { return status.Error(codes.Internal, "boom") }, acceptable)
	}
	time.Sleep(300 * time.Millisecond)
	// ⚠️ 测量请求用「成功」（nil）：放行时记 success ⇒ accepts 上涨 ⇒ dropRatio **缓慢衰减**
	//    ⇒ 短时间内幅度很小；而 N=20 的循环在两侧都是**亚毫秒**⇒ 不会跨桶
	//    ⇒ 测到的拒绝率 ≈ 建状态时的 dropRatio。
	//    ⚠️ 判定「被熔断拒绝」必须用 errors.Is(err, breaker.ErrServiceUnavailable)，
	//    不能用 err != nil（测量请求恒返回 nil 时才等價；一旦请求本身会失败就会混淆）。
	const N = 20
	rejected := 0
	for i := 0; i < N; i++ {
		// ⚠️⚠️ 必须用 errors.Is(err, breaker.ErrServiceUnavailable) 判定「被熔断拒绝」，
		//    **不能**用 err != nil：测量请求本身恒返回 INTERNAL 错误 ⇒ err 恒非 nil
		//    ⇒ 用 err != nil 会把「请求失败」也算成「被拒绝」⇒ 拒绝率恒为 1.0（本探针第一版的 bug）。
		if err := brk2.DoWithAcceptable(func() error { return nil }, acceptable); errors.Is(err, breaker.ErrServiceUnavailable) {
			rejected++
		}
	}
	fmt.Fprintf(out, "C rejected=%d total=%d rate=%.4f\n", rejected, N, float64(rejected)/float64(N))
}
