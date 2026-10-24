// task 域 gRPC 层 **跨语言**活体对拍 —— Go 客户端同时打 Go 服务端与 RAG 服务端
//
// 【为什么必须有这个文件】
// 仓库里已有的 GrpcTaskDualPortRunner.java 是 **Java 客户端**打两侧服务端：
//   RAG client → Go server   （跨语言的一种方向）
//   RAG client → RAG server  （同语言）
// ⇒ **缺**「Go 客户端 → RAG 服务端」这一方向。
// 而这一方向测的是**另一种互操作风险**：
//   · grpc-go 与 grpc-java 的 wire 行为（metadata / 压缩 / deadline / error 传播）；
//   · go-zero 的 zrpc 客户端是否能把 RAG 端的错误码/描述原样带回来；
//   · RAG 端在 **grpc-go 客户端**的调用形态下（长连接、keep-alive）是否行为一致。
// ⇒ 两个方向的互操作风险**不是同一件事**，必须各测一次。
//
// 【判定口径】与 GrpcTaskDualPortRunner.java **逐条对齐**（便于把两份报告放一起读）：
//   1. code 不同              ⇒ CODE_MISMATCH（硬失败）
//   2. code 相同、message 不同 ⇒ DESCRIPTION_DIFF（记录并单独计数，不判失败）
//   3. OK 路径比逐字段载荷（渲染格式与 Java 版**逐字相同**）
//
// 【不写进冻结真源】程序在真源之外，用 go.mod 的 replace 引用真源的生成桩。
//
// 用法：
//   GOFLAGS=-mod=mod go run .            # 默认 Go=19005 / RAG=59005
//   GO_PORT=19005 RAG_PORT=59005 go run .
package main

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"

	taskpb "sea-try-go/service/task/rpc/pb"
)

type result struct {
	code    string
	desc    string
	payload string
}

var (
	codeDiff    int
	descDiff    int
	payloadDiff int
	okCases     int
	errCases    int
)

func main() {
	goPort := env("GO_PORT", "19005")
	ragPort := env("RAG_PORT", "59005")
	host := env("HOST", "127.0.0.1")

	goConn, err := grpc.NewClient(host+":"+goPort,
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Println("FATAL: 连 Go rpc 失败:", err)
		os.Exit(2)
	}
	ragConn, err := grpc.NewClient(host+":"+ragPort,
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Println("FATAL: 连 RAG rpc 失败:", err)
		os.Exit(2)
	}
	defer goConn.Close()
	defer ragConn.Close()

	fmt.Println("== task 域 gRPC 层跨语言对拍（Go 客户端 → 两端服务端）==")
	fmt.Printf("   Go  task-rpc : %s:%s\n", host, goPort)
	fmt.Printf("   RAG task-rpc : %s:%s\n\n", host, ragPort)

	// 与 GrpcTaskDualPortRunner.java **完全同一组** uid：
	// 前三组在 HTTP 面上永远走不到，只能直连 rpc 才验得到。
	uids := []int64{
		0,             // => InvalidArgument "user_id invalid"
		-1,            // => 同上（uid <= 0）
		-9223372036854775808, // => 同上
		9223372036854775807,  // => OK
		1,             // => OK
		1001,          // => OK（种子数据里有两行）
		1003,          // => OK（DB 无行）
	}
	for _, uid := range uids {
		compare(fmt.Sprintf("uid=%d", uid), uid, goConn, ragConn)
	}

	fmt.Println("\n---- 汇总 ----")
	fmt.Printf("用例            : %d\n", okCases+errCases)
	fmt.Printf("  OK 路径      : %d   载荷不一致: %d\n", okCases, payloadDiff)
	fmt.Printf("  错误路径     : %d\n", errCases)
	fmt.Printf("  CODE_MISMATCH        : %d\n", codeDiff)
	fmt.Printf("  DESCRIPTION_DIFF     : %d\n", descDiff)

	if codeDiff == 0 && payloadDiff == 0 {
		os.Exit(0)
	}
	os.Exit(1)
}

func compare(label string, uid int64, goConn, ragConn *grpc.ClientConn) {
	g := call(taskpb.NewTaskServiceClient(goConn), uid)
	r := call(taskpb.NewTaskServiceClient(ragConn), uid)

	fmt.Println("── " + label)
	fmt.Printf("   Go  code=%s%s\n", g.code, descSuffix(g))
	fmt.Printf("   RAG code=%s%s\n", r.code, descSuffix(r))

	if g.code != r.code {
		codeDiff++
		fmt.Println("   ⇒ 🔴 CODE_MISMATCH（硬失败）")
		return
	}
	if g.code == codes.OK.String() {
		okCases++
		if g.payload != r.payload {
			payloadDiff++
			fmt.Println("   ⇒ 🔴 PAYLOAD_MISMATCH")
			fmt.Println("      Go  payload=" + g.payload)
			fmt.Println("      RAG payload=" + r.payload)
		} else {
			fmt.Println("   ⇒ ✅ 载荷逐字段一致")
		}
		return
	}
	errCases++
	if g.desc != r.desc {
		descDiff++
		fmt.Println("   ⇒ ⚠️ DESCRIPTION_DIFF（不判失败，记录）")
		fmt.Printf("      Go  desc  = %q\n", g.desc)
		fmt.Printf("      RAG desc  = %q\n", r.desc)
	} else {
		fmt.Println("   ⇒ ✅ code 与 description 逐字节一致")
	}
}

func descSuffix(r result) string {
	if r.desc == "" {
		return ""
	}
	return fmt.Sprintf("  desc=%q", r.desc)
}

func call(c taskpb.TaskServiceClient, uid int64) result {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	resp, err := c.GetTask(ctx, &taskpb.GetTaskReq{UserId: uid})
	if err != nil {
		st, _ := status.FromError(err)
		return result{code: st.Code().String(), desc: st.Message()}
	}
	return result{code: codes.OK.String(), payload: render(resp)}
}

/** 渲染格式与 GrpcTaskDualPortRunner.java 的 render() **逐字相同** ⇒ 两份报告可直接并读。 */
func render(resp *taskpb.GetTaskResp) string {
	var parts []string
	for _, t := range resp.GetTask() {
		parts = append(parts, fmt.Sprintf(
			"{name=%s, desc=%s, taskId=%d, completionProgress=%d, requiredProgress=%d}",
			t.GetName(), t.GetDesc(), t.GetTaskId(),
			t.GetCompletionProgress(), t.GetRequiredProgress()))
	}
	return strings.Join(parts, " ; ")
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

var _ = strconv.Itoa
