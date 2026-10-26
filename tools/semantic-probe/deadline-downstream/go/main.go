// ============================================================================
// 【global 域 · 第四十六轮】E38② 重估的 **Go 半边活体探针**
//
// 定位
// ----
// E38② 原修法是「handler 挪受控 executor + 超时 interrupt」⇒ 本轮要回答的是：
// **这一层还剩下什么可观察面？**
//
// 本探针测的是一个具体行为：
//
//     上游请求的 deadline 已经过期 ⇒ 业务代码**再发一次下游 gRPC 调用** ⇒ 会怎样？
//
// Go 真源侧的关键事实（全部逐行取证，文件:行号）：
//   · service/common/observability/rpc.go:38
//       timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
//     ⇒ timeoutCtx **携带 deadline**（父 ctx 的 deadline 更早时直接继承父的）
//   · service/common/observability/rpc.go:47-57
//       go func() { resp, err = handler(timeoutCtx, req); close(done) }()
//     ⇒ handler 拿到的就是 timeoutCtx ⇒ **出向调用可以拿到 deadline**
//   · service/common/observability/rpc.go:68-71
//       case <-timeoutCtx.Done(): return nil, status.Error(codes.DeadlineExceeded, ...)
//     ⇒ deadline 到点 ⇒ **客户端立刻收到 DEADLINE_EXCEEDED**
//     ⇒ 但 handler goroutine **继续跑**（Go 不能杀 goroutine）⇒ 这就是第③层的形状：
//       **业务代码继续跑，但它的 ctx 已经到期 ⇒ 所有 ctx-aware 的下游 I/O 立即中止**
//
//   而 Go 的出向 rpc 调用确实是 ctx-aware 的（真源逐行取证）：
//   · service/favorite/rpc/internal/logic/helper.go:98
//       svcCtx.UserRpc.GetUser(depCtx, &userservice.GetUserReq{Uid: userID})
//     ⇒ depCtx = otel.Tracer(...).Start(ctx, "...") 的返回值
//       ⇒ otel 的 span ctx 是「包一层 value」⇒ **父 ctx 的 deadline 原样保留**
//   · service/follow/rpc/internal/logic/helper.go:82   svcCtx.UserRpc.GetUser(ctx, ...)
//   · service/like/rpc/internal/logic/likeactionlogic.go:380
//       l.svcCtx.MessageRpc.SendNotification(l.ctx, ...)
//   · service/message/rpc/internal/logic/helper.go:144  svcCtx.UserRpc.GetUser(ctx, ...)
//   · service/comment/rpc/internal/logic/subject_fallback.go:18
//       articleRPC.GetArticle(ctx, &articleservice.GetArticleRequest{...})
//
//   ⇒ grpc-go 的 `cc.Invoke(ctx, ...)` 直接吃 ctx ⇒ **出向调用继承请求 deadline**
//
// 本探针在 Go 半边**实测**两件事（不是靠记忆断言）：
//   ① `downstream_deadline_failfast`
//      deadline 过期后发起的下游调用，**是否立刻失败**（失败 = 拿到 error）
//   ② `downstream_deadline_carrier`
//      deadline 过期后，业务侧持有的 ctx **是否还带着 deadline**（带 = 有上限）
//
// ⚠️ 为什么独立 module + replace
//    冻结仓是**只读**的，不能往里写探针文件 ⇒ 用 `replace sea-try-go => <冻结仓>`
//    直接**引用**它（不复制、不改写）⇒ 跑的就是冻结仓那份源码。
//
// ⚠️ 离线构建
//    - `CGO_ENABLED=0`：本机没有 Xcode 头文件，cgo 会报 'stdlib.h' file not found
//    - `GOPROXY=off`：全部依赖必须已在 module cache 里
//
// 输出格式：`PROBE|<case>|<key>=<value>|...`
//   ⚠️ **只输出稳定值**（布尔 / 码数字）⇒ 不输出 trace-id、端口、耗时
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

const (
	// timeoutMs —— 上游服务端的超时预算（真源拦截器的 timeout 参数）
	timeoutMs = 300
	// slowThresholdMs —— 真源拦截器的 slowThreshold 参数（与 RAG 半边同值）
	slowThresholdMs = 750
	// downstreamFailFastWindowMs ——
	// 「出向调用是否立刻失败」的判定窗口。
	// ⚠️ 必须**远大于**一次本地回环 RPC 的正常耗时（~1-5ms），
	//    又必须**小于**下游 hang server 的响应时间（30s）⇒ 400ms 是安全档。
	downstreamFailFastWindowMs = 400
	// expiryGraceMs —— 等到 deadline 过期之后再多等这么久，确保 ctx 真的到期
	expiryGraceMs = 50
)

// hangingLikeService 是**下游**服务：一直阻塞 ⇒ 出向调用若带着已过期的 deadline
// 就应当**立刻**失败（grpc-go 在 ctx 已到期时直接返回），若没带 deadline 就会挂住。
type hangingLikeService struct {
	pb.UnimplementedLikeServiceServer
}

func (hangingLikeService) GetLikeCount(ctx context.Context, _ *pb.GetLikeCountReq) (*pb.GetLikeCountResp, error) {
	time.Sleep(30 * time.Second)
	return nil, nil
}

// downstreamResult —— 出向调用的观测结果 + 业务侧 ctx 的特征
type downstreamResult struct {
	failedFast bool
	code       uint32
	// deadlineSet —— 业务侧持有的 ctx 是否**还带着** deadline
	//   ⚠️ Go 侧恒为 true（ctx.WithTimeout 从一开始就带 deadline）
	//   ⇒ 这是「有没有上限」这一维，不是「还有多久」那一维
	deadlineSet bool
	// expired —— 该 deadline 是否**已经过期**
	expired bool
	// carrierState —— 载体状态的三档枚举
	//   present_expired / present_live / absent
	//   ⚠️ 跨栈可比 ⇒ 不打印绝对剩余毫秒（两侧时基与测量点都不同）
	carrierState string
}

// downstreamCallingService 是**上游**服务：
//   等到自己的 deadline 过期 ⇒ 然后用**同一个 ctx** 发起一次下游调用 ⇒ 记录结果。
//
// ⚠️ 这里用的就是 Go 生产写法的形状（`svcCtx.XxxRpc.Method(ctx, ...)`）：
//    出向调用**吃 handler 手上那个 ctx** ⇒ ctx 过期 ⇒ 调用应当立刻失败。
type downstreamCallingService struct {
	pb.UnimplementedLikeServiceServer

	downstream pb.LikeServiceClient
	result    chan downstreamResult
}

func (s downstreamCallingService) GetLikeCount(ctx context.Context, _ *pb.GetLikeCountReq) (*pb.GetLikeCountResp, error) {
	// ① 等到有效期限过去
	//    ⚠️ 用 ctx.Deadline()（真源算出来的有效期限，rpc.go:38）而不是固定 sleep
	//    ⇒ 探针对「真源算的期限」敏感，若真源改了这行就会暴露出来。
	dl, hasDeadline := ctx.Deadline()
	if hasDeadline {
		if d := time.Until(dl); d > 0 {
			time.Sleep(d + expiryGraceMs*time.Millisecond)
		}
	} else {
		time.Sleep(expiryGraceMs * time.Millisecond)
	}

	// ② 出向调用：用**同一个 ctx**（生产形状）
	done := make(chan error, 1)
	go func() {
		_, err := s.downstream.GetLikeCount(ctx, &pb.GetLikeCountReq{})
		done <- err
	}()

	res := downstreamResult{}
	res.deadlineSet = hasDeadline
	res.expired = ctx.Err() != nil
	// 载体状态三档（与 RAG 半边逐字一致）
	switch {
	case !hasDeadline:
		res.carrierState = "absent"
	case res.expired:
		res.carrierState = "present_expired"
	default:
		res.carrierState = "present_live"
	}
	select {
	case err := <-done:
		res.failedFast = true
		if err != nil {
			if st, ok := status.FromError(err); ok {
				res.code = uint32(st.Code())
			}
		}
	case <-time.After(downstreamFailFastWindowMs * time.Millisecond):
		res.failedFast = false
	}
	select {
	case s.result <- res:
	default:
	}
	// handler 返回后上游 call 已经被真源拦截器关掉了 ⇒ 这里直接返回即可
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
	// ── 下游 hang server ────────────────────────────────────────────────
	downLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Fprintf(os.Stderr, "downstream listen failed: %v\n", err)
		os.Exit(2)
	}
	downSrv := grpc.NewServer()
	pb.RegisterLikeServiceServer(downSrv, hangingLikeService{})
	go func() { _ = downSrv.Serve(downLis) }()
	defer downSrv.Stop()

	downConn, err := grpc.NewClient("dns:///"+downLis.Addr().String(),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Fprintf(os.Stderr, "downstream dial failed: %v\n", err)
		os.Exit(2)
	}
	defer downConn.Close()

	// ── 上游 server（真源拦截器）────────────────────────────────────────
	upLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Fprintf(os.Stderr, "upstream listen failed: %v\n", err)
		os.Exit(2)
	}
	svc := downstreamCallingService{
		downstream: pb.NewLikeServiceClient(downConn),
		result:    make(chan downstreamResult, 4),
	}
	upSrv := grpc.NewServer(
		// ← 被测的那一行：Go 真源的拦截器
		grpc.UnaryInterceptor(observability.NewUnaryServerInterceptor(
			time.Duration(timeoutMs)*time.Millisecond,
			time.Duration(slowThresholdMs)*time.Millisecond)),
	)
	pb.RegisterLikeServiceServer(upSrv, svc)
	go func() { _ = upSrv.Serve(upLis) }()
	defer upSrv.Stop()

	upConn, err := grpc.NewClient("dns:///"+upLis.Addr().String(),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Fprintf(os.Stderr, "upstream dial failed: %v\n", err)
		os.Exit(2)
	}
	defer upConn.Close()

	// ── 客户端不带 deadline ⇒ 让**服务端自己的**超时来触发 ────────────────
	//    ⚠️ 这样才能观测到「真源拦截器在 deadline 时刻返回，而 handler goroutine 继续跑」
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_, callErr := pb.NewLikeServiceClient(upConn).GetLikeCount(ctx, &pb.GetLikeCountReq{})
	if callErr == nil {
		fmt.Fprintf(os.Stderr, "probe: upstream call unexpectedly succeeded\n")
		os.Exit(2)
	}
	if st, ok := status.FromError(callErr); !ok || st.Code() != 4 { // 4 = codes.DeadlineExceeded
		fmt.Fprintf(os.Stderr, "probe: expected DeadlineExceeded from upstream, got %v\n", callErr)
		os.Exit(2)
	}

	// ── 等 handler goroutine 的观测结果 ─────────────────────────────────
	var res downstreamResult
	select {
	case res = <-svc.result:
	case <-time.After(5 * time.Second):
		fmt.Fprintf(os.Stderr, "probe: handler never reported the downstream result\n")
		os.Exit(2)
	}

	// ── case 1：出向调用在 deadline 过期后是否**立刻失败** ─────────────────
	//    Go 侧：出向调用吃 handler 的 ctx ⇒ ctx 已到期 ⇒ 应当立刻失败
	//
	//    🔴 Go 只有**一个**载体（ctx）⇒ outgoing_carrier 与 sql_carrier 同值；
	//       RAG 侧有两个载体（GoStyleRequestDeadline / DeadlineState）⇒ 这正是差异所在。
	report("downstream_deadline_failfast",
		fmt.Sprintf("failed_fast=%t", res.failedFast),
		fmt.Sprintf("code=%d", res.code),
		fmt.Sprintf("outgoing_carrier=%s", res.carrierState),
		fmt.Sprintf("sql_carrier=%s", res.carrierState))
}
