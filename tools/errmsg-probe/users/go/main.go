// ⚠️ tools/errmsg-probe/users 的 Go 半边，**不是生产代码**。
//
// 它输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// 输出格式必须与 Java 半边（java/Probe.java）逐字节相同：
//
//	CONST|<code>            const 块登记的码值（升序）
//	TABLE|<code>|<msg>      每个码经 GetErrMsg 取回的文案（升序；**按 const 块的码集合**遍历）
//	FALLBACK|<code>|<msg>   未登记码经 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调 GetErrMsg，自己不持有任何码值/文案常量
//     （否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. TABLE 层按 **const 块的码集合**遍历（而不是按 codeMsg 的键集合）——
//     这样「声明了码但没登记文案」的那两个码（1017 / 1018）也会被比到，
//     差异因此**显式可见**（而不是被「按表遍历」悄悄跳过）。
package main

import (
	"errors"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"

	"usererrmsgprobe/errmsg"
)

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

	// ── Go 侧自检 ────────────────────────────────────────────────────────────
	// ⚠️ users 域真正成立的不变量是「**codeMsg 的键 ⊆ const 块的码集合**」
	//    （不像 like 域那样要求两者完全相等 —— users 的 const 块比表多 1017 / 1018 两个码）。
	//    这一步不需要 Java 参与就能发现真源自身的漂移（表里出现未声明的码 ⇒ 真源自身有 bug）。
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
	// ⚠️ 1017 / 1018 在 codeMsg 里**没有登记** ⇒ GetErrMsg 回落成 "FAIL"；
	//    RAG 侧枚举自带中文文案 ⇒ 这两行必然不同 ⇒ 由白名单逐条豁免（见 run-parity.sh）。
	for _, c := range sorted {
		b.WriteString("TABLE|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	// FALLBACK：未登记码的回落结果（顺序 = cases 文件顺序）。
	for _, c := range cases {
		b.WriteString("FALLBACK|" + strconv.Itoa(c) + "|" + errmsg.GetErrMsg(c) + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// selfCheck 校验 codeMsg 表的键全部都在 const 块里声明过。
//
// ⚠️ 刻意**不**要求 const ≡ 表键：users 域的 1017 / 1018 只声明常量、没登记文案
//    （Go service/user/common/errmsg/errmsg.go:30-31 vs :38-68）
//    ⇒ 这两个码的 GetErrMsg 回落成 "FAIL"，RAG 侧有真文案 ⇒ 由白名单逐条豁免。
func selfCheck() error {
	constSet := map[int]bool{}
	for _, c := range errmsg.ProbeConstCodes() {
		constSet[c] = true
	}
	for _, c := range errmsg.ProbeTableCodes() {
		if !constSet[c] {
			return errors.New("codeMsg 表里的码 " + strconv.Itoa(c) + " 不在 const 块中")
		}
	}
	// 提示（写到 stderr，不进比对流）：const 块里哪些码**没有**登记文案。
	var only []int
	for _, c := range errmsg.ProbeConstCodes() {
		if !contains(errmsg.ProbeTableCodes(), c) {
			only = append(only, c)
		}
	}
	if len(only) > 0 {
		parts := make([]string, 0, len(only))
		for _, c := range only {
			parts = append(parts, strconv.Itoa(c))
		}
		fmt.Fprintln(os.Stderr, "GO NOTE: const 块里这些码未登记进 codeMsg（GetErrMsg 对它们回落成 \"FAIL\"）:",
			strings.Join(parts, ", "))
	}
	return nil
}

// contains 判断切片里有没有目标值。
func contains(xs []int, v int) bool {
	for _, x := range xs {
		if x == v {
			return true
		}
	}
	return false
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
