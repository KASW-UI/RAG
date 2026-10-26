// rpc 侧直连探针 —— 调 **user-rpc 的 Logout**，看「Redis 不可达」时两侧的 gRPC 对外形状。
//
// ════════════════════════════════════════════════════════════════════════════
// 【它回答什么问题】
//   docs/DRIFT/users/user/rpc.md 的 **DRIFT-USERAPI-015**（Redis 命令超时 ≥ HTTP 预算）
//   目前只覆盖 **api 侧**（黑名单查询）。**rpc 侧对应面**此前登记为「未测 ⇒ 不下结论」。
//   本探针补这一格。
//
// 【为什么必须绕过 api 直连 gRPC】
//   api 侧有 3s HTTP 预算：RAG 的**黑名单查询**自己就要 ~3s ⇒ 请求**根本走不到** logout 的 rpc 调用
//   ⇒ 经 api 观测 rpc 侧会被 api 侧的预算**遮蔽** ⇒ 只能直连 gRPC。
//
// 【判决点：Go 的 Redis 失败延迟 vs 2s】
//   Go 真源结构：
//     service/common/observability/rpc.go:38  timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
//     service/common/observability/rpc.go:55  resp, err = handler(timeoutCtx, req)
//     ⇒ **handler 拿到的 ctx 带 2000ms deadline**
//     service/user/user/rpc/internal/logic/logoutlogic.go:37
//       err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
//     logoutlogic.go:38-41 ⇒ 写失败 ⇒ codes.Internal "注销失败,Redis写入异常"
//     rpc.go:59-72 ⇒ select{ <-done（handler 返回） ; <-timeoutCtx.Done()（2s 到点）}
//   ⇒ **Go 的 Redis 失败延迟 < 2s ⇒ 客户端看到 INTERNAL**；
//     **≥ 2s ⇒ ctx 先到期 ⇒ handler 与拦截器同时返回 ⇒ INTERNAL / DEADLINE_EXCEEDED 不定**。
//   RAG 侧：Redis 写只受 `spring.data.redis.timeout: 3s` 约束、**不受 gRPC deadline 约束**
//   ⇒ 拦截器的 2s 必然先到 ⇒ 客户端**确定**看到 DEADLINE_EXCEEDED。
//
// ════════════════════════════════════════════════════════════════════════════
// 【为什么用真源的真实 stub】
//   运行时由 `tools/difftest/exp-difftest-redis-neverup-rpc.sh` 生成 go.mod：
//     replace sea-try-go => ${GO_TRUTH_REPO}   ⇒ 直接 import
//     `sea-try-go/service/user/user/rpc/pb`（**真源生成的 grpc stub**，不是 internal/、可 import）
//   ⇒ 打两侧用的是**同一份**客户端代码 ⇒ wire 契约一致（两侧 proto 是复制的）⇒ 结果可直接比。
//   ⚠️ 仓里**不提交** go.mod（它含本机绝对路径）⇒ 与 `tools/semantic-probe/rpc-timeout/` 同款做法。
//   ⚠️ 构建必须 **CGO_ENABLED=0**（与真源二进制一致 ⇒ 纯 Go 解析器 ⇒ DNS 形态耗时可比）。
//
// 【用法】
//   ./redis-logout-rpc <addr> <token> [timeoutMs]
//   例：./redis-logout-rpc 127.0.0.1:19005 probe-token
package main

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"

	"sea-try-go/service/user/user/rpc/pb"
)

func main() {
	if len(os.Args) < 3 {
		fmt.Fprintln(os.Stderr, "usage: redis-logout-rpc <addr> <token> [timeoutMs]")
		os.Exit(2)
	}
	addr := os.Args[1]
	token := os.Args[2]

	callTimeout := 10 * time.Second
	if len(os.Args) >= 4 {
		ms, err := strconv.Atoi(os.Args[3])
		if err != nil {
			fmt.Fprintf(os.Stderr, "bad timeout %q\n", os.Args[3])
			os.Exit(2)
		}
		callTimeout = time.Duration(ms) * time.Millisecond
	}

	// 明文：两侧都是 gRPC 明文（Go user.yaml 无 TLS；RAG grpc server 也是明文）。
	cc, err := grpc.NewClient(addr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		fmt.Printf("DIAL_ERROR\t%v\n", err)
		os.Exit(1)
	}
	defer cc.Close()

	ctx, cancel := context.WithTimeout(context.Background(), callTimeout)
	defer cancel()

	start := time.Now()
	_, callErr := pb.NewUserServiceClient(cc).Logout(ctx, &pb.LogoutReq{Token: token})
	el := time.Since(start)

	if callErr != nil {
		st, _ := status.FromError(callErr)
		fmt.Printf("CODE=%s\tMSG=%s\telapsed_ms=%d\n", st.Code().String(), st.Message(), el.Milliseconds())
		return
	}
	fmt.Printf("CODE=OK\tMSG=\telapsed_ms=%d\n", el.Milliseconds())
}
