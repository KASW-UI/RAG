// ⚠️ tools/errmsg-probe/like 的 Go 半边，**不是生产代码**。
//
// 它输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// 输出格式必须与 Java 半边（java/Probe.java）逐字节相同：
//
//	CONST|<code>                      const 块登记的码值（升序）
//	TABLE|<code>|<msg>                每个码经 GetErrMsg 取回的文案（升序）
//	FALLBACK|<code>|<msg>             未登记码经 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//	WIRE|<bizCode>|<description>       每个码经 NewGrpcErr 构造出的 wire 形态（文案载体）
//	WIRECODE|<bizCode>|<carrier>      grpc-status 承载体的形态（**两侧必然不同** ⇒ 白名单豁免）
//	BIZCODE|<case>|<code>             业务码还原矩阵（error → 码）
//	OBS|<case>|<value>                **两侧必然不同**的语义差异（单列，见 README）
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调 GetErrMsg / NewGrpcErr，
//     自己不持有任何码值/文案常量（否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. grpc code 用小写名 —— Go 的 grpcodes.Code.String() 是 "Code(5003)"（越界码的表示法），
//     Java 的 Status.Code.name() 是 "UNKNOWN" ⇒ 两侧统一小写。
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

	"likeerrmsgprobe/errmsg"
)

// bizCodeCase 是一条「业务码还原」样本。
type bizCodeCase struct {
	name string
	err  error
}

// customStatusErr 模拟「实现了 GRPCStatus() 但不是 grpc-go 标准 error」的第三方 error。
//
// ⚠️ 这条是 **OBS 案例**，不是 bug：Go 的 status.FromError 只看顶层是否实现
// GRPCStatus() 接口（errmsg.go 依赖的 grpc-go v1.81.1 行为），
// 而 Java 侧的 LikeGrpcError#extractStatus 只认 StatusException /
// StatusRuntimeException 两个具体类 ⇒ 两侧对这种 error 的判定不同。
type customStatusErr struct{}

func (customStatusErr) Error() string { return "custom grpc-status error" }

func (customStatusErr) GRPCStatus() *status.Status {
	return status.New(grpcodes.Internal, "数据库查询失败")
}

// bizCodeCases 是「业务码还原」矩阵。
//
// ⚠️ 两侧必须**同构**：同样的 name、同样的 error 形态 ⇒ 期望同样的还原码。
//
// own_* 四条钉的是**本域自己的错误**（Go: errmsg.NewGrpcErr(code, msg) ⇒ grpc-status 里带业务码；
// Java: LikeGrpcError.grpcError(code, msg) ⇒ trailer 里带业务码）⇒ 两侧都必须还原出同一个业务码。
//
// foreign_* 五条钉的是**下游发来的、不是本域业务码的错误**：Go like-api 直接把原始 gRPC 码
// 当业务码（st.Code()），RAG 侧则收敛到自己的代表码 ⇒ 两侧**必然不同** ⇒ 白名单豁免
// （依据：docs/DRIFT/like/vs-common.md 的 D-7，已登记、待拍板）。
var bizCodeCases = []bizCodeCase{
	{"own_5003", errmsg.NewGrpcErr(errmsg.ErrorDbSelect, errmsg.GetErrMsg(errmsg.ErrorDbSelect))},
	{"own_2001", errmsg.NewGrpcErr(errmsg.ErrorInputWrong, errmsg.GetErrMsg(errmsg.ErrorInputWrong))},
	{"own_1015", errmsg.NewGrpcErr(errmsg.CodeServerBusy, errmsg.GetErrMsg(errmsg.CodeServerBusy))},
	{"own_3001", errmsg.NewGrpcErr(errmsg.ErrorBuildOutbox, errmsg.GetErrMsg(errmsg.ErrorBuildOutbox))},
	{"own_1003", errmsg.NewGrpcErr(errmsg.ErrorUserNotExist, errmsg.GetErrMsg(errmsg.ErrorUserNotExist))},
	{"foreign_internal", status.Error(grpcodes.Internal, "数据库查询失败")},
	{"foreign_notfound", status.Error(grpcodes.NotFound, "用户不存在")},
	{"foreign_invalid_argument", status.Error(grpcodes.InvalidArgument, "输入有误")},
	{"foreign_unauthenticated", status.Error(grpcodes.Unauthenticated, "未登录")},
	{"foreign_plain", errors.New("boom")},
}

// obsCases 是**两侧必然不同**的语义差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
//
// ⚠️ 这三条不是「期望 Go 与 Java 一致」，而是「把已知的实现层差异钉死」：
// 若将来 Java 侧改了语义（不再抛 / 开始识别 GRPCStatus），OBS 段的值会变 ⇒ 探针变红
// ⇒ 逼人重新评估这条差异是否还成立，而不是让它静默漂移。
var obsCases = []bizCodeCase{
	// Go: 还原函数只在 `if err != nil` 之内被调用（like-api 的调用点）⇒ nil 走不到
	//     status.FromError ⇒ 落到兜底分支 = 5001（ErrorServerCommon）。
	// Java: bizCodeFromError(null) 显式返回 SUCCESS(200)（防御性取值）。
	{"obs_nil_error", nil},
	// Go: status.Error(codes.OK, msg) 返回 **nil**（grpc-go 行为）⇒ 与 nil 同形 ⇒ 兜底 5001。
	// Java: asException() 得到一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底 ⇒ 1015。
	{"obs_ok_status", status.Error(grpcodes.OK, "系统内部错误")},
	// Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ ok=true ⇒ 还原出原始 gRPC 码 13。
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

	sorted := append([]int(nil), errmsg.ProbeConstCodes()...)
	sort.Ints(sorted)

	// CONST：const 块登记的全部码值（升序）。
	for _, c := range sorted {
		b.WriteString("CONST|" + strconv.Itoa(c) + "\n")
	}

	// TABLE：每个码经 GetErrMsg 取回的文案（升序）。
	for _, c := range sorted {
		b.WriteString("TABLE|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	// FALLBACK：未登记码的回落结果（顺序 = cases 文件顺序）。
	for _, c := range cases {
		b.WriteString("FALLBACK|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	// WIRE / WIRECODE：每个码经 NewGrpcErr 构造出的 wire 形态。
	//
	// ⚠️ 这里输入的 msg 取自**真源自己的码表**（GetErrMsg）⇒ 这条记录同时钉住
	//    「码表文案」与「文案载体透传」两件事；若两侧码表不同，TABLE 层已经先红。
	//
	// ⚠️ WIRECODE 记的是「业务码放在哪个载体上」：Go 放在 grpc-status（越界数值，
	//    grpc-go 无校验强转），Java 放在自定义 trailer ⇒ **两侧必然不同** ⇒ 白名单豁免
	//    （依据：docs/Intentional_Deviations/like/rpc.md 的 SD-2026-10-23-06）。
	for _, c := range sorted {
		msg := errmsg.GetErrMsg(c)
		gerr := errmsg.NewGrpcErr(c, msg)
		st, _ := status.FromError(gerr)
		b.WriteString("WIRE|" + strconv.Itoa(c) + "|" + st.Message() + "\n")
		b.WriteString("WIRECODE|" + strconv.Itoa(c) + "|status=" +
			strings.ToLower(st.Code().String()) + ";trailer=-\n")
	}

	// BIZCODE：业务码还原矩阵。
	for _, c := range bizCodeCases {
		b.WriteString("BIZCODE|" + c.name + "|" + strconv.Itoa(goApiBizCode(c.err)) + "\n")
	}

	// OBS：已知不可达差异（两侧不同，单列）。
	for _, c := range obsCases {
		b.WriteString("OBS|" + c.name + "|" + strconv.Itoa(goApiBizCode(c.err)) + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// goApiBizCode 复刻 Go like-api 的「从 gRPC 错误还原业务码」这一步。
//
// 出处（消费点，非声明点）：
// Sea-RideTheWind_golang/service/like/api/internal/logic/like/likeactionlogic.go:63-68
//
//	if err != nil {
//		st, ok := status.FromError(err)
//		if ok {
//			return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message())
//		}
//		logger.LogBusinessErr(l.ctx, errmsg.ErrorServerCommon, err)
//		return nil, errmsg.NewErrCode(errmsg.ErrorServerCommon)
//	}
//
// 同形调用点共 6 处（getuserlikelistlogic.go:58 / getusertotallikelogic.go:55 /
// gettargetlikerlistlogic.go:42 / likeactionlogic.go:63 / getlikecountlogic.go:41 /
// getlikestatelogic.go:58）。
//
// ⚠️ 本函数**连 `err != nil` 的守卫一起复刻**：调用点在那句守卫之内 ⇒ nil error 走不到
//    status.FromError ⇒ 本函数对 nil 返回兜底码 5001。
//    （注：grpc-go v1.81.1 的 status.FromError(nil) 返回 (nil, true) —— 与更早版本不同 ——
//     若守卫去掉，int(st.Code()) 会得到 0，这正是 OBS 段 obs_nil_error 记录的另一侧形状。）
//
// ⚠️ 本函数只做「还原」这一步（不构造 envelope）—— envelope 的形状不是本探针的对拍对象
//    （那属于 api 组件的契约）。
func goApiBizCode(err error) int {
	if err == nil {
		return errmsg.ErrorServerCommon
	}
	st, ok := status.FromError(err)
	if ok {
		return int(st.Code())
	}
	return errmsg.ErrorServerCommon
}

// selfCheck 校验 Go 侧 const 块与 codeMsg 表的键集合一致。
func selfCheck() error {
	constSet := map[int]bool{}
	for _, c := range errmsg.ProbeConstCodes() {
		constSet[c] = true
	}
	tableSet := map[int]bool{}
	for _, c := range errmsg.ProbeTableCodes() {
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
