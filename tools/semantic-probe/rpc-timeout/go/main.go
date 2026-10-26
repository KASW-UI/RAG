// ============================================================================
// 【global 域 · 第三十轮】E30 的 **Go 半边活体探针**
//
// 定位
// ----
// AGENTS.md §3.3 规则 2：「凡写『与 Go 一一致』，必须有**两侧同时跑的探针**」
// ⇒ 本文件把 **Go 真源**（冻结仓的 `service/common/observability`）跑起来，
//   与 RAG 侧**生产类**（`RpcInterceptor`）逐条对拍 gRPC 超时路径。
//
// ⚠️ 跑的是**真源**，不是照抄逻辑：
//    - 拦截器 = `sea-try-go/service/common/observability.NewUnaryServerInterceptor`
//      （冻结仓 `service/common/observability/rpc.go:25-74` 的导出函数）
//    - 服务 = 冻结仓**真实生成**的 `pb.LikeService`（`service/like/rpc/pb`）
//    - 真 gRPC server（grpc-go）+ 真 client stub ⇒ 走**真实网络栈**
//    - 真超时（300ms）⇒ 触发 `rpc.go:68-71` 的 select 超时分支
//
// 为什么独立 module + replace
// ---------------------------
// 冻结仓是**只读**的，不能往里写探针文件 ⇒ 用 `replace sea-try-go => <冻结仓>`
// 直接**引用**它（不复制、不改写）⇒ 跑的就是冻结仓那份源码。
//
// ⚠️ 离线构建
//    - `CGO_ENABLED=0`：本机没有 Xcode 头文件，cgo 会报 'stdlib.h' file not found
//    - `GOPROXY=off`：全部依赖必须已在 module cache 里
//
// 输出格式：`PROBE|<case>|<key>=<value>|...`
//   ⚠️ **只输出稳定值**（code 数字 / 文案）⇒ 不输出 trace-id、端口、耗时
// ============================================================================

package main

import (
	"context"
	"fmt"
	"net"
	"os"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"

	"sea-try-go/service/common/observability"
	"sea-try-go/service/like/rpc/pb"
)

// 与 RAG 侧守门 `RpcInterceptorTimeoutDescriptionGateTest` 同一组参数
const (
	timeoutMs       = 300
	slowThresholdMs = 750
	// clientDeadlineMs —— E38「客户端 deadline 档」用的客户端 deadline
	// ⚠️ 必须**早于** timeoutMs(300ms) ⇒ 才能区分「继承客户端」与「只算服务端自己的」
	//    （若客户端更长，min 就等于服务端 300ms ⇒ 两侧看不出差别 ⇒ 探针失效）
	clientDeadlineMs = 100
	// clientDeadlineBucketMaxMs —— 分档阈值：剩余 ≤ 此值 ⇒ 判定为「继承了客户端 deadline」
	// ⚠️ 阈值取客户端 deadline(100ms) 与服务端 timeout(300ms) 之间 ⇒ 两档互斥
	clientDeadlineBucketMaxMs = 150
)

// hangingLikeService 只实现一个方法：一直阻塞 ⇒ 只有**服务端超时**能结束这次调用。
//
// ⚠️ handler **必须永不返回**：Go 真源 rpc.go:59-72 的 select 有三个分支
// （done / panicChan / timeoutCtx.Done()）。若 handler 在 ctx.Done() 时就返回，
// 则 done 分支与 timeout 分支**同时就绪** ⇒ Go 的 select 随机选
// ⇒ 可能走 done 分支返回 `context.DeadlineExceeded`（**非 status error**）
// ⇒ 客户端拿到 code=Unknown 而不是 DEADLINE_EXCEEDED ⇒ 探针变成随机。
// 真实的慢 handler 就是**一直不返回** ⇒ 这里照做。
type hangingLikeService struct {
	pb.UnimplementedLikeServiceServer
}

func (hangingLikeService) GetLikeCount(ctx context.Context, _ *pb.GetLikeCountReq) (*pb.GetLikeCountResp, error) {
	time.Sleep(30 * time.Second)
	return nil, nil
}

// observingLikeService 记录 handler 看到的 ctx 特征（E38 的「客户端 deadline 档」）
//
// ⚠️ 这里**只观察**不改行为：真源拦截器传给 handler 的就是 `timeoutCtx`
// （rpc.go:55 `handler(timeoutCtx, req)`）⇒ handler 里看到的 deadline
// 就是**真源计算出来的有效期限** ⇒ 直接可观测，不用猜。
type observingLikeService struct {
	pb.UnimplementedLikeServiceServer

	seen chan ctxSnapshot
}

type ctxSnapshot struct {
	deadlineSet     bool
	remainingMillis int64
	err             string
}

func (o observingLikeService) GetLikeCount(ctx context.Context, _ *pb.GetLikeCountReq) (*pb.GetLikeCountResp, error) {
	snap := ctxSnapshot{}
	if dl, ok := ctx.Deadline(); ok {
		snap.deadlineSet = true
		snap.remainingMillis = time.Until(dl).Milliseconds()
	}
	// ctx.Err() 在 deadline 到点后为 DeadlineExceeded（parent 被 cancel 时为 Canceled）
	// ⚠️ 未取消时 ctx.Err() 返回 **nil** ⇒ 不能直接 .Error() ⇒ 必须判空
	if e := ctx.Err(); e != nil {
		snap.err = e.Error()
	} else {
		snap.err = "nil"
	}
	select {
	case o.seen <- snap:
	default:
	}
	time.Sleep(30 * time.Second)
	return nil, nil
}

func report(caseName string, kv ...string) {
	out := ""
	for i, k := range kv {
		if i > 0 {
			out += "|"
		}
		out += k
	}
	fmt.Printf("PROBE|%s|%s\n", caseName, out)
}

func main() {
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Fprintf(os.Stderr, "listen failed: %v\n", err)
		os.Exit(2)
	}
	srv := grpc.NewServer(
		// ← 被测的那一行：Go 真源的拦截器
		grpc.UnaryInterceptor(observability.NewUnaryServerInterceptor(
			time.Duration(timeoutMs)*time.Millisecond,
			time.Duration(slowThresholdMs)*time.Millisecond)),
	)
	pb.RegisterLikeServiceServer(srv, hangingLikeService{})
	go func() { _ = srv.Serve(lis) }()
	defer srv.Stop()

	conn, err := grpc.NewClient("dns:///"+lis.Addr().String(),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Fprintf(os.Stderr, "dial failed: %v\n", err)
		os.Exit(2)
	}
	defer conn.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, err = pb.NewLikeServiceClient(conn).GetLikeCount(ctx, &pb.GetLikeCountReq{})

	st, ok := status.FromError(err)
	if err == nil || st == nil {
		fmt.Fprintf(os.Stderr, "probe: expected a status error, got err=%v ok=%v\n", err, ok)
		os.Exit(2)
	}

	// ── case 1：wire 面（客户端真正收到的 status）─────────────────────────
	// Go 真源 rpc.go:69 ⇒ description = timeoutCtx.Err().Error()
	//                  = "context deadline exceeded"
	report("timeout_wire",
		fmt.Sprintf("code=%d", uint32(st.Code())),
		fmt.Sprintf("desc=%s", st.Message()))

	// ── case 2：遥测面（Go 的 MarkTimeout 取 err.Error()）──────────────────
	// Go 真源 observability.go:108-112 ⇒ msg = err.Error()
	// 而 status.Error(...).Error() = "rpc error: code = <Code.String()> desc = <msg>"
	//（grpc-go 的 codes.Code.String() 是 CamelCase —— code_string.go:31-59）
	//
	// ⚠️ 客户端侧 st.Err().Error() 与服务端 MarkTimeout 拿到的 err.Error()
	//    **逐字节相同**（同一个 status error）⇒ 不需要 otelgrpc 就能观测到。
	report("timeout_telemetry",
		fmt.Sprintf("code=%d", uint32(st.Code())),
		fmt.Sprintf("msg=%s", st.Err().Error()))

	// ── case 3（E38）：「客户端 deadline 早于服务端 timeout」这一档 ──────────
	//
	// Go 真源 rpc.go:38 `context.WithTimeout(ctx, timeout)` 里 **ctx 携带客户端 deadline**
	// ⇒ Go 标准库语义 ⇒ 有效期限 = min(客户端 deadline, now+timeout)
	// ⇒ handler 看到的 ctx.Deadline() 应当是 **客户端的那个**（≈1s），而不是服务端 2s。
	//
	// ⚠️ 观测点：真源拦截器把 timeoutCtx 传给 handler（rpc.go:55）
	//    ⇒ handler 里 ctx.Deadline() 就是**真源算出来的有效期限** ⇒ 直接可观测。
	runClientDeadlineCase()
}

// runClientDeadlineCase —— E38 的 Go 半边事实采集
//
// 观察两个量：
//  ① handler 看到的 ctx 是否有 deadline（deadline_set）
//  ② 那个 deadline 还剩多少毫秒（remaining_ms）⇒ 与服务端 2s 比对
//
// ⚠️ 只用「剩余毫秒落在哪个区间」做判定，不打印绝对值 ⇒ 两侧可稳定比对
//    （剩余毫秒受传输耗时影响，落在区间里才是可比的）。
func runClientDeadlineCase() {
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Fprintf(os.Stderr, "client-deadline case: listen failed: %v\n", err)
		os.Exit(2)
	}
	obs := observingLikeService{seen: make(chan ctxSnapshot, 4)}
	srv := grpc.NewServer(
		grpc.UnaryInterceptor(observability.NewUnaryServerInterceptor(
			time.Duration(timeoutMs)*time.Millisecond,
			time.Duration(slowThresholdMs)*time.Millisecond)),
	)
	pb.RegisterLikeServiceServer(srv, obs)
	go func() { _ = srv.Serve(lis) }()
	defer srv.Stop()

	conn, err := grpc.NewClient("dns:///"+lis.Addr().String(),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Fprintf(os.Stderr, "client-deadline case: dial failed: %v\n", err)
		os.Exit(2)
	}
	defer conn.Close()

	// 客户端 deadline = 1s（早于服务端 2s）⇒ Go 侧有效期限应为 min(1s, 2s) = 1s
	ctx, cancel := context.WithTimeout(context.Background(), clientDeadlineMs*time.Millisecond)
	defer cancel()
	go func() {
		_, _ = pb.NewLikeServiceClient(conn).GetLikeCount(ctx, &pb.GetLikeCountReq{})
	}()

	select {
	case snap := <-obs.seen:
		// 分档：剩余 ≤ clientDeadlineBucketMaxMs(150ms) ⇒ 继承的是**客户端**的 deadline
		//      （若只算服务端自己的 300ms ⇒ 剩余 ≈ 300ms ⇒ 落在另一档）
		bucket := "server_own"
		if snap.deadlineSet && snap.remainingMillis <= clientDeadlineBucketMaxMs {
			bucket = "client_100ms"
		}
		report("client_deadline_ctx",
			fmt.Sprintf("deadline_set=%t", snap.deadlineSet),
			fmt.Sprintf("bucket=%s", bucket))
	case <-time.After(5 * time.Second):
		fmt.Fprintf(os.Stderr, "client-deadline case: handler never observed\n")
		os.Exit(2)
	}
}

