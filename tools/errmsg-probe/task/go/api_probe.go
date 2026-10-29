// ⚠️ tools/errmsg-probe/task 的 Go 半边（**api 层**），**不是生产代码**。
//
// 它**直调真源 handler** `sea-try-go/service/task/api/internal/handler/task.GetTaskHandler`
// （用 `httptest.ResponseRecorder` 接住响应），输出规范化 dump，
// 交给 run-parity.sh 与 Java 半边逐字节比对。
//
// ⚠️ 本文件会被 run-parity.sh 复制到临时模块的
//    `<tmp>/service/task/api/probe/main.go` —— **必须**落在 `service/task/api/` 下面，
//    否则 Go 的 internal 可见性规则不允许 import
//    `sea-try-go/service/task/api/internal/{handler/task,svc}`。
//
// 输出格式必须与 Java 半边（java/ApiProbe.java）逐字节相同：
//
//	API|<case>|<httpStatus>|<escaped body>   一次真实 HTTP 请求的**状态码 + body 逐字节**
//	OBS|<case>|<value>                       **两侧必然不同**的已知不可达差异
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— body 是真源 handler **真的写出来的字节**，本探针只做转义。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. body 里可能有 \n / | / \ ⇒ 统一转义后才落进一行（两侧同一套转义规则）。
package main

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"

	"google.golang.org/grpc"
	grpcodes "google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	apihandler "sea-try-go/service/task/api/internal/handler/task"
	apisvc "sea-try-go/service/task/api/internal/svc"
	taskpb "sea-try-go/service/task/rpc/pb"
)

// fakeTaskClient 是 `taskpb.TaskServiceClient` 的**受控**实现。
//
// ⚠️ 真源的 api 层是「纯转发」：`gettasklogic.go:36-41`
//
//	rpcResp, err := l.svcCtx.TaskCli.GetTask(l.ctx, &taskpb.GetTaskReq{UserId: uid})
//	if err != nil { return nil, err }
//
//	⇒ 要把「rpc 的错误往上抛时保不保得住 code」这一层测出来，
//	就必须能**控制**下游返回什么错误 ⇒ 这里注入一个受控的 gRPC 错误。
//
// ⚠️ 接口定义见真源 `service/task/rpc/pb/task_grpc.pb.go:28-30`（只有一个方法）。
type fakeTaskClient struct {
	err error
}

func (f *fakeTaskClient) GetTask(ctx context.Context, in *taskpb.GetTaskReq,
	opts ...grpc.CallOption) (*taskpb.GetTaskResp, error) {
	if f.err != nil {
		return nil, f.err
	}
	return &taskpb.GetTaskResp{}, nil
}

// escape 把一段文本压成**单行**可逐字节比对的形态。
//
// ⚠️ 转义规则两侧必须完全一致（Go 与 Java 各一份实现）：
//
//	`\` → `\\`   `|` → `\|`   LF → `\n`   CR → `\r`
func escape(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch r {
		case '\\':
			b.WriteString(`\\`)
		case '|':
			b.WriteString(`\|`)
		case '\n':
			b.WriteString(`\n`)
		case '\r':
			b.WriteString(`\r`)
		default:
			b.WriteRune(r)
		}
	}
	return b.String()
}

// apiCase 是一条 HTTP 用例。
type apiCase struct {
	name string
	body string
	// rpcCode / rpcDesc：下游 rpc 要返回的错误（rpcCode == "-" ⇒ 不注入错误）。
	rpcCode string
	rpcDesc string
}

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "usage: apiprobe <cases-file>")
		os.Exit(2)
	}
	cases, err := readCases(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "read cases:", err)
		os.Exit(2)
	}

	var b strings.Builder

	for _, c := range cases {
		status, body := run(c)
		b.WriteString("API|" + c.name + "|" + status + "|" + escape(body) + "\n")
	}

	// ── OBS：两侧必然不同的已知不可达差异 ─────────────────────────────────────
	for _, c := range obsCases() {
		status, body := run(c)
		b.WriteString("OBS|" + c.name + "|" + status + "|" + escape(body) + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// run 跑一次**真实**的 HTTP 请求（真源 handler），返回 (状态码, body)。
func run(c apiCase) (string, string) {
	var rpcErr error
	if c.rpcCode != "-" {
		rpcErr = status.Error(c.grpcCodeValue(), c.rpcDesc)
	}

	svcCtx := &apisvc.ServiceContext{TaskCli: &fakeTaskClient{err: rpcErr}}
	h := apihandler.GetTaskHandler(svcCtx)

	req := httptest.NewRequest(http.MethodPost, "/taskcenter/v1/task/get", strings.NewReader(c.body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	return fmt.Sprintf("%d", rec.Code), rec.Body.String()
}

// grpcCodeValue 把 cases 文件里的**数字**码转成 grpcodes.Code。
//
// ⚠️ 用数字（而不是名字）⇒ 两侧共用同一张表，不会各自维护名字映射而悄悄错位。
func (c apiCase) grpcCodeValue() grpcodes.Code {
	n, err := strconv.Atoi(c.rpcCode)
	if err != nil {
		fmt.Fprintln(os.Stderr, "bad rpc code:", c.rpcCode)
		os.Exit(2)
	}
	return grpcodes.Code(n)
}

// obsCases 是**两侧必然不同**的已知差异。
//
// ⚠️ 这两条都是**库/框架形态差异**，不是 RAG 的行为漂移：
//
//	obs_api_empty_body
//	  Go：真源 `gettaskhandler.go:21-24` 的 `decoder.Decode(&req)` 对空 body 返回
//	      `io.EOF` ⇒ `httpx.ErrorCtx` ⇒ 400 + "EOF\n"。
//	  Java：Spring 的 `HttpRequestBodyRequiredException`
//	      ⇒ advice 的 `handleUnreadableBody` ⇒ 400 + "Required request body is missing\n"。
//	  ⇒ **状态码与 Content-Type 一致，文案不同**（两个运行时的「读不到 body」措辞不同）。
//
//	obs_api_malformed_json
//	  Go：`encoding/json` 的措辞（如 `invalid character 'x' looking for beginning of value`）。
//	  Java：Jackson 的措辞（如 `Unexpected character ...`）+ Spring 的 `JSON parse error: ` 前缀。
//	  ⇒ **状态码与 Content-Type 一致，文案不同**（两个 JSON 库的措辞不同）。
//
//	⚠️ 这两条在 `docs/DRIFT/task/api.md` 的「错误响应形状」一节已被登记为
//    「全仓 api 域的共性差异，不在本域单独修」⇒ 归 OBS 段（单列 + 差异消失即 exit 2），
//    **不是** `docs/Intentional_Deviations/task/**` 里的签字偏离 ⇒ 不进白名单。
func obsCases() []apiCase {
	return []apiCase{
		{name: "obs_api_empty_body", body: "", rpcCode: "-"},
		{name: "obs_api_malformed_json", body: "{", rpcCode: "-"},
	}
}

// readCases 读 cases 文件：`name|body|rpcCode|rpcDesc` 一行一条。
//
// ⚠️ rpcCode 取 Go 的 `codes.Code` **数字**（如 3 / 13），两边都用数字 ⇒ 不必各自维护名字表。
//    rpcCode == "-" ⇒ 不注入下游错误（用例应在 uid 解析阶段就失败）。
func readCases(path string) ([]apiCase, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var out []apiCase
	for _, line := range strings.Split(string(raw), "\n") {
		line = strings.TrimRight(line, "\r")
		if strings.TrimSpace(line) == "" || strings.HasPrefix(strings.TrimSpace(line), "#") {
			continue
		}
		parts := strings.SplitN(line, "|", 4)
		if len(parts) != 4 {
			return nil, fmt.Errorf("bad case line (want name|body|rpcCode|rpcDesc): %q", line)
		}
		out = append(out, apiCase{name: parts[0], body: parts[1], rpcCode: parts[2], rpcDesc: parts[3]})
	}
	return out, nil
}
