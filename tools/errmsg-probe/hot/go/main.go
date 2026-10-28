// ⚠️ 本文件是 tools/errmsg-probe/hot 的 Go 半边，**不是生产代码**。
//
// 它输出 hot 域错误码契约的**规范化 dump**，交给 run-parity.sh 与 Java 半边
// 逐字节比对（sha256 + diff）。
//
// 真源：Sea-RideTheWind_golang/service/hot/common/errmsg.go
//
// 输出格式（每行一条，\n 结尾，UTF-8）：
//
//	CONST|<code>              Go const 块登记的码值（升序）
//	TABLE|<code>|<msg>        codeMsg 表的每一条，取值走 GetErrMsg（升序）
//	FALLBACK|<code>|<msg>     未登记码走 GetErrMsg 的回落结果（顺序 = cases 文件顺序）
//
// ⚠️ TABLE 一节走的是 GetErrMsg 而不是直接读 map 值 ——
//    这样探针验的是**对外函数**的行为，而不是「表的内部结构恰好长这样」。
package main

import (
	"bufio"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"

	"hoterrmsgprobe/common"
)

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "usage: errmsg-probe <cases-file>")
		os.Exit(2)
	}

	cases, err := readCases(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "read cases:", err)
		os.Exit(2)
	}

	consts := append([]int(nil), common.ProbeConstCodes()...)
	sort.Ints(consts)
	table := common.ProbeTableCodes()

	// Go 侧自检：const 块与 codeMsg 表必须登记**同一组**码值。
	//
	// ⚠️ 这条是 Go 内部不变量的守门，不是 Java 的事：
	//    Go 的 api 层 handler 传的是 const、查的是 codeMsg 表，
	//    两者若不一致 ⇒ Go 自己的信封 msg 就会意外回落到 "FAIL"。
	//    Java 侧枚举把两者合一了（结构差异，非语义差异），
	//    所以这条不一致**只能**在 Go 侧发现。
	if !sameInts(consts, table) {
		fmt.Fprintln(os.Stderr, "GO-INTERNAL-CHECK FAILED: const 集合与 codeMsg 键集合不相等")
		fmt.Fprintf(os.Stderr, "  const: %v\n", consts)
		fmt.Fprintf(os.Stderr, "  table: %v\n", table)
		os.Exit(1)
	}

	var b strings.Builder
	for _, c := range consts {
		fmt.Fprintf(&b, "CONST|%d\n", c)
	}
	for _, c := range table {
		fmt.Fprintf(&b, "TABLE|%d|%s\n", c, common.GetErrMsg(c))
	}
	for _, c := range cases {
		fmt.Fprintf(&b, "FALLBACK|%d|%s\n", c, common.GetErrMsg(c))
	}

	if _, err := os.Stdout.WriteString(b.String()); err != nil {
		fmt.Fprintln(os.Stderr, "write stdout:", err)
		os.Exit(2)
	}
}

// readCases 读 cases 文件：一行一个 int，空行与 # 注释行忽略。
func readCases(path string) ([]int, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var out []int
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		n, err := strconv.Atoi(line)
		if err != nil {
			return nil, fmt.Errorf("bad int %q: %w", line, err)
		}
		out = append(out, n)
	}
	return out, sc.Err()
}

func sameInts(a, b []int) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
