// ⚠️ tools/errmsg-probe/follow 的 Go 半边，**不是生产代码**。
//
// 它输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// 输出格式必须与 Java 半边（java/Probe.java）逐字节相同：
//
//	CONST|<code>                 const 块登记的码值（升序）
//	TABLE|<code>|<msg>           每个码经 GetErrMsg 取回的文案（升序）
//	FALLBACK|<code>|<msg>        未登记码经 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//	CODEERR|<code>|<errText>     NewErrCode(code) 的 Error() 文本（升序）
//	WIRE|<bizCode>|<grpcCode>|<desc>   每个已登记码经 GRPCError 构造出的 wire 形态
//	WIREX|<grpcCode>|<bizCode>|<desc>  grpc 码 × 业务码 的 wire 矩阵
//	BIZCODE|<case>|<code>        BizCodeFromError 的还原结果（见下方矩阵）
//	OBS|<case>|<value>           **已知不可达差异**（两侧不同，单列，见 README）
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调 GetErrMsg / GRPCError / BizCodeFromError / NewErrCode，
//     自己不持有任何码值/文案常量（否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
//     ⚠️ 例外：bizCodeCases 里的 description 是**反查表的输入**，必须写字面量才能命中某条文案；
//     但这些字面量的**期望输出码**由真源自己算出来，不是手抄的。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. grpc code 用小写名 —— Go 的 grpcodes.Code.String() 是 "FailedPrecondition"，
//     Java 的 Status.Code.name() 是 "FAILED_PRECONDITION" ⇒ 两侧统一小写。
package main

import (
	"errors"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
	"unicode"

	grpcodes "google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"followerrmsgprobe/common"
)

// bizCodeCase 是一条 BizCodeFromError 的还原样本。
type bizCodeCase struct {
	name string
	err  error
}

// customStatusErr 模拟「实现了 GRPCStatus() 但不是 grpc-go 标准 error」的第三方 error。
//
// ⚠️ 这条是 **OBS 案例**，不是 bug：Go 的 status.FromError 只看顶层是否实现
// GRPCStatus() 接口（grpc-go v1.81.1 的真实行为，见 module cache
// google.golang.org/grpc@v1.81.1/status/status.go 的 FromError），
// 而 Java 侧的 FollowGrpcError#resolveStatus 只认 StatusException /
// StatusRuntimeException 两个具体类 ⇒ 两侧对这种 error 的判定不同。
type customStatusErr struct{}

func (customStatusErr) Error() string { return "custom grpc-status error" }

func (customStatusErr) GRPCStatus() *status.Status {
	return status.New(grpcodes.NotFound, "user not found")
}

// bizCodeCases 是 BizCodeFromError 的还原矩阵。
//
// ⚠️ 两侧必须**同构**：同样的 name、同样的 error 形态 ⇒ 期望同样的还原码。
// 其中多条专门钉「文案反查优先于 gRPC 码类别」这条语义：
//
//	desc_5101        NOT_FOUND 在 Go 的 switch 白名单里（否则会给 5002）⇒ 反查必须给 5101
//	desc_5201        PERMISSION_DENIED 在白名单（否则会给 5005）⇒ 反查必须给 5201
//	desc_5201_unauth UNAUTHENTICATED **在**白名单（否则会给 5004）⇒ 反查优先必须给 5201
//	desc_ok_text     "OK" 是 Success 的文案，但 Success/Error 被排除在反查表外
//	desc_fail_text   "FAIL" 同理 ⇒ 都落到 INTERNAL 的兜底 5001
//	desc_unknown_*   文案不在表里 ⇒ 落到 switch 白名单映射（6 个码逐条验）
//	desc_unknown_unavail / desc_unknown_deadline
//	                 Unavailable / DeadlineExceeded **不在**白名单 ⇒ default 1015
//	code_error_wrapped
//	                 Go 的 `err.(*CodeError)` 是**顶层直接类型断言**（不走 cause 链）⇒ 包一层就认不出 ⇒ 1015
var bizCodeCases = []bizCodeCase{
	{"nil_error", nil},
	{"plain_error", errors.New("boom")},
	{"code_error", common.NewErrCodeMsg(common.ErrorInvalidParam, "invalid parameter")},
	{"code_error_wrapped", fmt.Errorf("wrapped: %w", common.NewErrCodeMsg(common.ErrorInvalidParam, "invalid parameter"))},
	{"desc_5001", status.Error(grpcodes.Internal, "internal server error")},
	{"desc_5002", status.Error(grpcodes.NotFound, "record not found")},
	{"desc_5007", status.Error(grpcodes.Internal, "database query failed")},
	{"desc_5101", status.Error(grpcodes.NotFound, "user not found")},
	{"desc_5201", status.Error(grpcodes.PermissionDenied, "self relation is not allowed")},
	{"desc_5201_unauth", status.Error(grpcodes.Unauthenticated, "self relation is not allowed")},
	{"desc_5002_runtime", status.Error(grpcodes.NotFound, "record not found")},
	{"desc_5002_exception", status.Error(grpcodes.NotFound, "record not found")},
	{"wrapped_unknown", fmt.Errorf("wrapped: %w", status.Error(grpcodes.NotFound, "record not found"))},
	{"desc_unknown", status.Error(grpcodes.Internal, "unknown text xyz")},
	{"desc_unknown_invalid", status.Error(grpcodes.InvalidArgument, "unknown text xyz")},
	{"desc_unknown_unauth", status.Error(grpcodes.Unauthenticated, "unknown text xyz")},
	{"desc_unknown_perm", status.Error(grpcodes.PermissionDenied, "unknown text xyz")},
	{"desc_unknown_exists", status.Error(grpcodes.AlreadyExists, "unknown text xyz")},
	{"desc_unknown_notfound", status.Error(grpcodes.NotFound, "unknown text xyz")},
	{"desc_unknown_unavail", status.Error(grpcodes.Unavailable, "unknown text xyz")},
	{"desc_unknown_deadline", status.Error(grpcodes.DeadlineExceeded, "unknown text xyz")},
	{"desc_ok_text", status.Error(grpcodes.Internal, "OK")},
	{"desc_fail_text", status.Error(grpcodes.Internal, "FAIL")},
}

// obsCases 是**两侧必然不同**的已知差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
//
// ⚠️ 这三条不是「期望 Go 与 Java 一致」，而是「把已知的实现层差异钉死」：
// 若将来 Java 侧改了语义，OBS 段的值会变 ⇒ 探针变红
// ⇒ 逼人重新评估这条差异是否还成立，而不是让它静默漂移。
var obsCases = []bizCodeCase{
	// Go: status.Error(codes.OK, msg) 返回 **nil**（grpc-go internal/status/status.go 的
	//     `func (s *Status) Err() error`：Code()==OK ⇒ return nil）⇒ BizCodeFromError(nil) ⇒ 200。
	// Java: grpcError(OK, ...) 造出来的是一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底。
	{"obs_ok_status", status.Error(grpcodes.OK, "internal server error")},
	// Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ 文案反查命中 ⇒ 5101。
	// Java: resolveStatus 只认 StatusException / StatusRuntimeException ⇒ 认不出 ⇒ 1015。
	{"obs_custom_grpcstatus", customStatusErr{}},
	// Go: fmt.Errorf("%w") 的 Error() = "外层文本: 内层文本"，内层是 status error 时
	//     内层文本恒为 "rpc error: code = ... desc = ..." ⇒ 文案反查**必然落空** ⇒ switch 兜底 5002。
	// Java: new RuntimeException(msg, cause).getMessage() 恒等于外层文本 ⇒ 反查**能命中** ⇒ 5101。
	// ⇒ 这是 Go 的 fmt.Errorf 文本格式 vs Java 的 Throwable#getMessage() 的**结构性**差异，
	//    FollowGrpcError#resolveStatus 的 javadoc 已自登记为「残留的跨语言差异」。
	{"obs_wrapped_outer_msg", fmt.Errorf("user not found: %w", status.Error(grpcodes.NotFound, "user not found"))},
}

// wireGrpcCodes 是 WIREX 矩阵用到的 grpc 码。
//
// ⚠️ 取值理由：前 6 个**在** Go 的 BizCodeFromError switch 白名单里（errmsg.go:118-132），
// 后 2 个（Unavailable / DeadlineExceeded）**不在** ⇒ 用来钉「default ⇒ 1015」。
var wireGrpcCodes = []struct {
	name string
	code grpcodes.Code
}{
	{"invalid_argument", grpcodes.InvalidArgument},
	{"unauthenticated", grpcodes.Unauthenticated},
	{"permission_denied", grpcodes.PermissionDenied},
	{"already_exists", grpcodes.AlreadyExists},
	{"not_found", grpcodes.NotFound},
	{"internal", grpcodes.Internal},
	{"unavailable", grpcodes.Unavailable},
	{"deadline_exceeded", grpcodes.DeadlineExceeded},
}

// wireBizCodes 是 WIREX 矩阵用到的业务码。
//
// ⚠️ 取值理由：5101 / 5201 是 follow 域**专属**码（别的域没有 ⇒ 撞车即漂移），
// 5007 是 DB 码 ⇒ 三者合起来覆盖「文案反查能还原的粗粒度码」与「switch 兜底码」两类。
var wireBizCodes = []int{
	common.ErrorUserNotFound,
	common.ErrorSelfRelation,
	common.ErrorDbSelect,
}

// canonicalCodeName 把 grpc 码名归一成 **snake_case 小写**。
//
// ⚠️ 为什么需要归一（否则两侧永远对不上）：
//   Go 的 grpcodes.Code.String() 是 CamelCase —— "InvalidArgument" / "DeadlineExceeded"；
//   Java 的 Status.Code.name() 是 UPPER_SNAKE —— "INVALID_ARGUMENT" / "DEADLINE_EXCEEDED"。
//   而 grpc 官方 proto 的 enum 名（canonicalString，见 module cache
//   google.golang.org/grpc@v1.81.1/codes/code_string.go）就是 "INVALID_ARGUMENT"
//   ⇒ 归一到小写 snake_case 后，两侧都等于官方 canonical 形态。
//
// ⚠️ 未知码（如 codes.Code(5003)）的 String() 是 "Code(5003)" ⇒ 本函数给出 "code(5003)"；
//   Java 侧根本没有未知码这个概念（枚举只有 17 个）⇒ 该形态只在 comment 域出现。
func canonicalCodeName(c grpcodes.Code) string {
	s := c.String()
	var b strings.Builder
	for i, r := range s {
		if i > 0 && unicode.IsUpper(r) {
			b.WriteByte('_')
		}
		b.WriteRune(unicode.ToLower(r))
	}
	return b.String()
}

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "usage: probe <cases-file>")
		os.Exit(2)
	}
	cases, err := readCases(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "read cases:", err)
		os.Exit(2)
	}

	// ── Go 侧自检：const 块 ≡ codeMsg 表的键集合 ────────────────────────────
	// ⚠️ 这一步是「真源自检」：它不需要 Java 参与就能发现真源自身的漂移
	//    （新增常量忘了补表 / 补了表忘加常量 / 键值写错）。
	if err := selfCheck(); err != nil {
		fmt.Fprintln(os.Stderr, "GO SELF-CHECK FAILED:", err)
		os.Exit(3)
	}

	var b strings.Builder

	// CONST：const 块登记的全部码值（升序）。
	constCodes := common.ProbeConstCodes()
	sorted := append([]int(nil), constCodes...)
	sort.Ints(sorted)
	for _, c := range sorted {
		b.WriteString("CONST|" + strconv.Itoa(c) + "\n")
	}

	// TABLE：每个码经 GetErrMsg 取回的文案（升序）。
	for _, c := range common.ProbeTableCodes() {
		b.WriteString("TABLE|" + strconv.Itoa(c) + "|" + common.GetErrMsg(c) + "\n")
	}

	// FALLBACK：未登记码的回落结果（顺序 = cases 文件顺序）。
	for _, c := range cases {
		b.WriteString("FALLBACK|" + strconv.Itoa(c) + "|" + common.GetErrMsg(c) + "\n")
	}

	// CODEERR：NewErrCode(code) 的 Error() 文本（升序）。
	for _, c := range sorted {
		b.WriteString("CODEERR|" + strconv.Itoa(c) + "|" + common.NewErrCode(c).Error() + "\n")
	}

	// WIRE：每个已登记码经 GRPCError(Internal, code) 构造出的 wire 形态。
	// ⚠️ 这是 follow 域最核心的 L1 契约：**业务码不在 grpc-status 里，而在 description 里**。
	for _, c := range sorted {
		err := common.GRPCError(grpcodes.Internal, c)
		st, ok := status.FromError(err)
		if !ok {
			b.WriteString("WIRE|" + strconv.Itoa(c) + "|UNKNOWN|" + "" + "\n")
			continue
		}
		b.WriteString("WIRE|" + strconv.Itoa(c) + "|" + canonicalCodeName(st.Code()) +
			"|" + st.Message() + "\n")
	}

	// WIREX：grpc 码 × 业务码 的 wire 矩阵。
	for _, gc := range wireGrpcCodes {
		for _, bc := range wireBizCodes {
			err := common.GRPCError(gc.code, bc)
			st, ok := status.FromError(err)
			if !ok {
				b.WriteString("WIREX|" + gc.name + "|" + strconv.Itoa(bc) + "|UNKNOWN|" + "" + "\n")
				continue
			}
			b.WriteString("WIREX|" + gc.name + "|" + strconv.Itoa(bc) + "|" +
				canonicalCodeName(st.Code()) + "|" + st.Message() + "\n")
		}
	}

	// BIZCODE：业务码还原矩阵。
	for _, c := range bizCodeCases {
		b.WriteString("BIZCODE|" + c.name + "|" + strconv.Itoa(common.BizCodeFromError(c.err)) + "\n")
	}

	// OBS：已知不可达差异（两侧不同，单列）。
	for _, c := range obsCases {
		b.WriteString("OBS|" + c.name + "|" + strconv.Itoa(common.BizCodeFromError(c.err)) + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// selfCheck 校验 Go 侧 const 块与 codeMsg 表的键集合一致。
func selfCheck() error {
	constSet := map[int]bool{}
	for _, c := range common.ProbeConstCodes() {
		constSet[c] = true
	}
	tableSet := map[int]bool{}
	for _, c := range common.ProbeTableCodes() {
		tableSet[c] = true
	}
	for c := range constSet {
		if !tableSet[c] {
			return errors.New("const 块里的码 " + strconv.Itoa(c) + " 不在 codeMsg 表中")
		}
	}
	for c := range tableSet {
		if !constSet[c] {
			return errors.New("codeMsg 表里的码 " + strconv.Itoa(c) + " 不在 const 块中")
		}
	}
	return nil
}

// readCases 读 cases 文件：一行一个 int，空行与 # 注释行忽略。
func readCases(path string) ([]int, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var out []int
	for _, line := range strings.Split(string(raw), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		v, err := strconv.Atoi(line)
		if err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, nil
}
