// follow 域 **指标抓取面**双侧对拍的 gRPC 驱动客户端。
//
// 定位：follow-rpc 的 5 个 `follow_rpc_*` 指标**全部在 rpc 层上报**
//   （Go: service/follow/rpc/internal/logic/*.go 的 defer；RAG: service/follow/follow-rpc 的
//   FollowDomainService / FollowRecommendationService）
//   ⇒ 驱动流量必须**打到 rpc 层**（api 层只是转发）⇒ 本客户端直连 gRPC。
//
// 为什么用 Go 写：Go 真源 `service/follow/rpc/pb/` 里已有**生成好的** pb 与 grpc stub
//   ⇒ 同一份 proto 契约可以同时驱动 **Go 侧**与 **RAG 侧**两个服务器
//   ⇒ 不需要「两套客户端」（两套客户端本身就可能是不可比的原因）。
//
// ⚠️ **pb 不复制进本目录**：run.sh 在构建时从**冻结真源** `service/follow/rpc/pb/` 现拷现用，
//   并对两个文件做 sha256 断言 ⇒ 真源改了 pb，探针立刻响（退出码 2）。
//
// 用法：
//   client -addr 127.0.0.1:18171 -method GetFollowList -user 1001
//   client -addr 127.0.0.1:18171 -method Follow -user 1001 -target 1004
//
// 输出（单行，机器可解析）：
//   RESULT|<method>|<grpc_status_code>|<resp_code>|<resp_msg>|<err_msg>
//
//   - grpc_status_code：gRPC 状态码（0 = OK）
//   - resp_code：响应体 BaseResp.code（成功路径才有；错误路径响应体为 nil）
//   - err_msg：gRPC 错误消息（错误路径才有）
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"followmetricsprobe/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
)

func main() {
	addr := flag.String("addr", "", "gRPC 地址 host:port")
	method := flag.String("method", "", "RPC 方法名")
	userID := flag.Int64("user", 0, "user_id")
	targetID := flag.Int64("target", 0, "target_id（仅写路径用）")
	offset := flag.Int64("offset", 0, "offset")
	limit := flag.Int64("limit", 0, "limit")
	timeout := flag.Duration("timeout", 20*time.Second, "单次调用超时")
	flag.Parse()

	if *addr == "" || *method == "" {
		fmt.Fprintln(os.Stderr, "!! 必填：-addr / -method")
		os.Exit(2)
	}

	conn, err := grpc.NewClient(*addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Printf("RESULT|%s|CLIENT_ERROR||||%v\n", *method, err)
		os.Exit(2)
	}
	defer conn.Close()

	c := pb.NewFollowServiceClient(conn)
	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()

	var respCode int32
	var respMsg string
	var errMsg string

	// ⚠️ **逐方法显式分派**（不反射、不按名拼方法）：pb 的 stub 是 8 个具名方法
	//   ⇒ 用「反射按方法名找」会让「方法名写错」变成运行期 panic 而不是编译期错误。
	switch *method {
	case "Follow":
		r, err := c.Follow(ctx, &pb.RelationReq{UserId: *userID, TargetId: *targetID})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "Unfollow":
		r, err := c.Unfollow(ctx, &pb.RelationReq{UserId: *userID, TargetId: *targetID})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "Block":
		r, err := c.Block(ctx, &pb.RelationReq{UserId: *userID, TargetId: *targetID})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "Unblock":
		r, err := c.Unblock(ctx, &pb.RelationReq{UserId: *userID, TargetId: *targetID})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "GetFollowList":
		r, err := c.GetFollowList(ctx, &pb.ListReq{UserId: *userID, Offset: int32(*offset), Limit: int32(*limit)})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "GetBlockList":
		r, err := c.GetBlockList(ctx, &pb.ListReq{UserId: *userID, Offset: int32(*offset), Limit: int32(*limit)})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "GetFollowerList":
		r, err := c.GetFollowerList(ctx, &pb.ListReq{UserId: *userID, Offset: int32(*offset), Limit: int32(*limit)})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	case "GetRecommendations":
		r, err := c.GetRecommendations(ctx, &pb.ListReq{UserId: *userID, Offset: int32(*offset), Limit: int32(*limit)})
		if err == nil {
			respCode, respMsg = r.GetCode(), r.GetMsg()
		} else {
			errMsg = err.Error()
		}
	default:
		fmt.Fprintf(os.Stderr, "!! 未知方法：%s\n", *method)
		os.Exit(2)
	}

	st, _ := status.FromError(err)
	fmt.Printf("RESULT|%s|%d|%d|%s|%s\n", *method, int(st.Code()), respCode, respMsg, errMsg)
}
