// ⚠️ tools/errmsg-probe/comment 的 Go 半边，**不是生产代码**。
//
// 它输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// 输出格式必须与 Java 半边（java/Probe.java）逐字节相同：
//
//	CONST|<code>                 const 块登记的码值（升序）
//	TABLE|<code>|<msg>           每个码经 GetErrMsg 取回的文案（升序）
//	FALLBACK|<code>|<msg>        未登记码经 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//	CODEERR|<code>|<errText>     NewErrCode(code) 的 Error() 文本（升序）
//	CODEERRMSG|<code>|<msg>|<errText>  NewErrCodeMsg(code, msg) 的 Error() 文本
//	WIRE|<bizCode>|<desc>        NewGrpcErr(code, msg) 的 description（业务码在 description 里）
//	OBS|<case>|<bizCode>|<wireGrpcStatusValue>|<wireGrpcStatusName>|<desc>|<recoveredBizCode>
//	                              **已知不可达差异**（业务码的 wire 载体不同，单列，见 README）
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调 GetErrMsg / NewErrCode / NewErrCodeMsg / NewGrpcErr，
//     自己不持有任何码值常量（否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
//     ⚠️ 例外：prodCalls 里的文案是**生产调用点的输入**（不是期望输出），
//     必须写字面量才能复刻真实链路上的「码 → 文案」组合。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. grpc code 归一成 snake_case 小写（Java 的 Status.Code.name() 就是这个形态）。
package main

import (
	"errors"
	"os"
	"sort"
	"strconv"
	"strings"
	"unicode"

	grpcodes "google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"commenterrmsgprobe/errmsg"
)

// prodCall 是 Go comment-rpc 的**真实调用点**：业务码 + 该调用点传入的文案。
//
// ⚠️ 这些文案**故意不等于** codeMsg 表里的文案：
//   ErrorDbSelect 的表文案是「查询数据库失败」（errmsg.go:73），
//   而 rpc 层传的是「DB查询失败」（create_comment_logic.go:89）⇒ 两侧都保留了这个差值
//   （见 docs/DRIFT/comment/common.md §2.2 的说明）。
//   ⇒ 用它们做 WIRE / CODEERRMSG 的输入，验的是**线上真实链路**上的「码 → 文案」组合，
//   而不是表里的理想值 —— 这才是客户端真正看到的字节。
//
// ⚠️ 出处：service/comment/rpc/internal/logic/ 下 19 处 NewGrpcErr 调用点
//   （create_comment_logic.go / delete_comment_logic.go / like_comment_logic.go /
//     manage_comment_logic.go / report_comment_logic.go / update_subject_state_logic.go）。
type prodCall struct {
	code int
	msg  string
}

var prodCalls = []prodCall{
	{errmsg.ErrorCommentNotExist, "评论不存在"},
	{errmsg.ErrorDbInsert, "DB插入失败"},
	{errmsg.ErrorDbSelect, "DB查询失败"},
	{errmsg.ErrorDbUpdate, "DB修改失败"},
	{errmsg.ErrorDbUpdate, "DB更新失败"},
	{errmsg.ErrorInputWrong, "DB更新失败"},
	{errmsg.ErrorInputWrong, "Type和ID不能为空"},
	{errmsg.ErrorInputWrong, "操作类型输入错误"},
	{errmsg.ErrorInputWrong, "目标类型和ID不能为空"},
	{errmsg.ErrorInputWrong, "评论ID不能为空"},
	{errmsg.ErrorInputWrong, "评论ID和目标不能为空"},
	{errmsg.ErrorInputWrong, "评论内容不能为空"},
	{errmsg.ErrorJsonMarshal, "Json序列化失败"},
	{errmsg.ErrorKafkaPush, "Kafka推送失败"},
	{errmsg.ErrorSnowflakeID, "雪花算法生成ID出错"},
	{errmsg.ErrorSubjectNotExist, "评论区不存在"},
	{errmsg.ErrorUserNoRight, "无权执行删除操作"},
	{errmsg.ErrorUserNoRight, "非up主无法评论"},
}

// obsCalls 是**两侧必然不同**的已知差异：业务码在 wire 上的**载体**不同。
//
// ⚠️ Go 侧把业务码**直接写进 grpc-status**（errmsg.go:33-35 的
// `status.Error(codes.Code(code), msg)`）⇒ 线上真实字节是 `grpc-status: 1012`；
//   Java 侧 grpc-java **只能发 17 个标准码**（0-16），发不出 1012
//   ⇒ RAG 把业务码挪到自定义 trailer `rag-comment-biz-code`（CommentGrpcError.java:72）
//   ⇒ grpc-status 字段的数值**不可复原**，业务码与文案仍无损。
//
// ⚠️ 这条是 **OBSERVABLE DIFFERENCE**，已在 docs/DRIFT/comment/common.md §3-D1 登记。
type obsCall struct {
	name string
	code int
	msg  string
}

var obsCalls = []obsCall{
	{"obs_wire_grpcstatus_1012", errmsg.ErrorSubjectNotExist, "评论区不存在"},
	{"obs_wire_grpcstatus_2001", errmsg.ErrorInputWrong, "目标类型和ID不能为空"},
	{"obs_wire_grpcstatus_5003", errmsg.ErrorDbSelect, "DB查询失败"},
}

// canonicalCodeName 把 grpc 码名归一成 **snake_case 小写**。
//
// ⚠️ Go 的 grpcodes.Code.String() 对**越界码**返回 "Code(1012)"（module cache
// google.golang.org/grpc@v1.81.1/codes/code_string.go 的 default 分支）
// ⇒ 本函数给出 "code(1012)"；Java 侧只可能是 17 个标准码之一（如 "unknown"）。
// ⇒ 两侧在这一格上**必然不同** —— 这正是 OBS 段要钉的东西。
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
		os.Stderr.WriteString("usage: probe <cases-file>\n")
		os.Exit(2)
	}
	cases, err := readCases(os.Args[1])
	if err != nil {
		os.Stderr.WriteString("read cases: " + err.Error() + "\n")
		os.Exit(2)
	}

	// ── Go 侧自检：const 块 ≡ codeMsg 表的键集合 ────────────────────────────
	// ⚠️ 这一步是「真源自检」：它不需要 Java 参与就能发现真源自身的漂移
	//    （新增常量忘了补表 / 补了表忘加常量 / 键值写错）。
	if err := selfCheck(); err != nil {
		os.Stderr.WriteString("GO SELF-CHECK FAILED: " + err.Error() + "\n")
		os.Exit(3)
	}

	var b strings.Builder

	// CONST：const 块登记的全部码值（升序）。
	constCodes := errmsg.ProbeConstCodes()
	sorted := append([]int(nil), constCodes...)
	sort.Ints(sorted)
	for _, c := range sorted {
		b.WriteString("CONST|" + strconv.Itoa(c) + "\n")
	}

	// TABLE：每个码经 GetErrMsg 取回的文案（升序）。
	for _, c := range errmsg.ProbeTableCodes() {
		b.WriteString("TABLE|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	// FALLBACK：未登记码的回落结果（顺序 = cases 文件顺序）。
	for _, c := range cases {
		b.WriteString("FALLBACK|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	// CODEERR：NewErrCode(code) 的 Error() 文本（升序）。
	for _, c := range sorted {
		b.WriteString("CODEERR|" + strconv.Itoa(c) + "|" + errmsg.NewErrCode(c).Error() + "\n")
	}

	// CODEERRMSG：NewErrCodeMsg(code, msg) 的 Error() 文本（生产调用点的真实组合）。
	for _, pc := range prodCalls {
		b.WriteString("CODEERRMSG|" + strconv.Itoa(pc.code) + "|" + pc.msg + "|" +
			errmsg.NewErrCodeMsg(pc.code, pc.msg).Error() + "\n")
	}

	// WIRE：NewGrpcErr(code, msg) 的 description。
	// ⚠️ 这一层验的是「**文案逐字节进 description**」——两侧都必须一致。
	//    而「业务码进不进 grpc-status」是两侧**必然不同**的 ⇒ 放 OBS 段。
	for _, pc := range prodCalls {
		st, ok := grpcStatusOf(errmsg.NewGrpcErr(pc.code, pc.msg))
		if !ok {
			b.WriteString("WIRE|" + strconv.Itoa(pc.code) + "|" + "" + "\n")
			continue
		}
		b.WriteString("WIRE|" + strconv.Itoa(pc.code) + "|" + st.Message() + "\n")
	}

	// OBS：业务码的 wire 载体不同（两侧必然不同，单列）。
	for _, oc := range obsCalls {
		st, ok := grpcStatusOf(errmsg.NewGrpcErr(oc.code, oc.msg))
		if !ok {
			b.WriteString("OBS|" + oc.name + "|" + strconv.Itoa(oc.code) +
				"|0||" + "" + "|0\n")
			continue
		}
		// ⚠️ Go comment-api 的消费点（service/comment/api/internal/logic/comment/
		//    createcommentlogic.go:52-55 等 6 处）：
		//        st, ok := status.FromError(err)
		//        if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }
		//    ⇒ 业务码就是 grpc-status 上的整数 ⇒ 这里用 int(st.Code()) 还原。
		b.WriteString("OBS|" + oc.name + "|" + strconv.Itoa(oc.code) + "|" +
			strconv.Itoa(int(st.Code())) + "|" + canonicalCodeName(st.Code()) + "|" +
			st.Message() + "|" + strconv.Itoa(int(st.Code())) + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// grpcStatusOf 取 error 里的 gRPC status。
//
// ⚠️ 这里**刻意不复刻** Go comment-api 那句 `int(st.Code())` 的消费点 ——
//    消费点的语义由 OBS 段单独记录（Go 侧那一列就是 int(st.Code())）。
func grpcStatusOf(err error) (*status.Status, bool) {
	if err == nil {
		// ⚠️ status.Error(codes.OK, msg) 在 grpc-go 里返回 **nil**（internal/status/status.go
		//    的 `func (s *Status) Err() error`：Code()==OK ⇒ return nil）。
		//    本探针用的码都不是 0 ⇒ 不会走到这里；留分支只是防止将来加码时 panic。
		return nil, false
	}
	st, ok := status.FromError(err)
	if !ok || st == nil {
		return nil, false
	}
	return st, true
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
