// ⚠️ tools/errmsg-probe/favorite 的 Go 半边，**不是生产代码**。
//
// 它输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// 输出格式必须与 Java 半边（java/Probe.java）逐字节相同：
//
//	CONST|<code>                 const 块登记的码值（升序）
//	TABLE|<code>|<msg>           每个码经 GetErrMsg 取回的文案（升序）
//	FALLBACK|<code>|<msg>        未登记码经 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//	WIRE|<bizCode>|<grpcCode>|<description>   每个码经 GRPCError 构造出的 wire 形态
//	BIZCODE|<case>|<code>        BizCodeFromError 的还原结果（见下方矩阵）
//	OBS|<case>|<value>           **已知不可达差异**（两侧不同，单列，见 README）
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调 GetErrMsg / GRPCError / BizCodeFromError，
//     自己不持有任何码值/文案常量（否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
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

	grpcodes "google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"favoriteerrmsgprobe/common"
)

// bizCodeCase 是一条 BizCodeFromError 的还原样本。
type bizCodeCase struct {
	name string
	err  error
}

// customStatusErr 模拟「实现了 GRPCStatus() 但不是 grpc-go 标准 error」的第三方 error。
//
// ⚠️ 这条是 **OBS 案例**，不是 bug：Go 的 status.FromError 只看顶层是否实现
// GRPCStatus() 接口（errmsg.go 依赖的 grpc-go v1.81.1 行为），
// 而 Java 侧的 FavoriteGrpcError#extractStatus 只认 StatusException /
// StatusRuntimeException 两个具体类 ⇒ 两侧对这种 error 的判定不同。
type customStatusErr struct{}

func (customStatusErr) Error() string { return "custom grpc-status error" }

func (customStatusErr) GRPCStatus() *status.Status {
	return status.New(grpcodes.Internal, "数据库查询失败")
}

// bizCodeCases 是 BizCodeFromError 的还原矩阵。
//
// ⚠️ 两侧必须**同构**：同样的 name、同样的 error 形态 ⇒ 期望同样的还原码。
// 其中 4 条专门钉「文案反查优先于 gRPC 码类别」这条语义：
//
//	desc_5304        FAILED_PRECONDITION 不在 Go 的 switch 白名单里 ⇒ 只有反查能给 5304
//	desc_5304_unauth UNAUTHENTICATED **在**白名单（否则会给 5004）⇒ 反查优先必须给 5304
//	desc_ok_text     "OK" 是 Success 的文案，但 Success/Error 被排除在反查表外
//	desc_fail_text   "FAIL" 同理 ⇒ 都落到 INTERNAL 的兜底 5001
var bizCodeCases = []bizCodeCase{
	{"desc_5304", status.Error(grpcodes.FailedPrecondition, "收藏暂不可撤回")},
	{"desc_5007", status.Error(grpcodes.Internal, "数据库查询失败")},
	{"desc_5008", status.Error(grpcodes.Internal, "数据库更新失败")},
	{"desc_5203", status.Error(grpcodes.AlreadyExists, "收藏夹名称已存在")},
	{"desc_5301", status.Error(grpcodes.NotFound, "收藏记录不存在")},
	{"desc_5101", status.Error(grpcodes.NotFound, "用户不存在")},
	{"desc_1015", status.Error(grpcodes.Internal, "服务繁忙")},
	{"desc_5001", status.Error(grpcodes.Internal, "系统内部错误")},
	{"desc_unknown", status.Error(grpcodes.Internal, "未知文案xyz")},
	{"desc_unknown_failedpre", status.Error(grpcodes.FailedPrecondition, "未知文案xyz")},
	{"desc_unknown_unavail", status.Error(grpcodes.Unavailable, "未知文案xyz")},
	{"desc_unknown_unauth", status.Error(grpcodes.Unauthenticated, "未知文案xyz")},
	{"desc_ok_text", status.Error(grpcodes.Internal, "OK")},
	{"desc_fail_text", status.Error(grpcodes.Internal, "FAIL")},
	{"desc_5304_unauth", status.Error(grpcodes.Unauthenticated, "收藏暂不可撤回")},
	{"plain_error", errors.New("boom")},
	{"nil_error", nil},
}

// obsCases 是**两侧必然不同**的已知差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
//
// ⚠️ 这两条不是「期望 Go 与 Java 一致」，而是「把已知的实现层差异钉死」：
// 若将来 Java 侧改了语义（不再抛 / 开始识别 GRPCStatus），OBS 段的值会变 ⇒ 探针变红
// ⇒ 逼人重新评估这条差异是否还成立，而不是让它静默漂移。
var obsCases = []bizCodeCase{
	// Go: status.Error(grpcodes.OK, msg) 返回 **nil** ⇒ BizCodeFromError(nil) ⇒ 200。
	// Java: grpcError(OK, ...) 返回一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底。
	{"obs_ok_status", status.Error(grpcodes.OK, "系统内部错误")},
	// Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ 文案反查命中 ⇒ 5007。
	// Java: extractStatus 只认 StatusException / StatusRuntimeException ⇒ 认不出 ⇒ 1015。
	{"obs_custom_grpcstatus", customStatusErr{}},
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

	// WIRE：每个码经 GRPCError 构造出的 wire 形态。
	// ⚠️ 这是 favorite 域最核心的 L1 契约：**业务码不在 grpc-status 里，而在 description 里**。
	for _, c := range sorted {
		err := common.GRPCError(grpcodes.Internal, c)
		st, ok := status.FromError(err)
		if !ok {
			b.WriteString("WIRE|" + strconv.Itoa(c) + "|UNKNOWN|" + "" + "\n")
			continue
		}
		b.WriteString("WIRE|" + strconv.Itoa(c) + "|" + strings.ToLower(st.Code().String()) +
			"|" + st.Message() + "\n")
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
