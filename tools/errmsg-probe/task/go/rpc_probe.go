// ⚠️ tools/errmsg-probe/task 的 Go 半边（**rpc 层**），**不是生产代码**。
//
// 它**直调真源逻辑包** `sea-try-go/service/task/rpc/internal/logic`，
// 输出规范化 dump，交给 run-parity.sh 与 Java 半边逐字节比对。
//
// ⚠️ 本文件会被 run-parity.sh 复制到临时模块的
//    `<tmp>/service/task/rpc/probe/main.go` —— **必须**落在 `service/task/rpc/` 下面，
//    否则 Go 的 internal 可见性规则不允许 import `service/task/rpc/internal/logic`。
//
// 输出格式必须与 Java 半边（java/RpcProbe.java）逐字节相同：
//
//	RPCUID|<case>|<grpcCode>|<desc>     uid <= 0 ⇒ 领域层抛 status error
//	RPCBARE|<case>|<grpcCode>|<desc>    裸 error（DB 回源失败）⇒ grpc-go 归 UNKNOWN
//	OBS|<case>|<value>                  **两侧必然不同**的已知不可达差异
//
// ⚠️ 三条纪律：
//  1. 不得抄写文案 —— 本探针只调真源的 `logic.GetTaskLogic.GetTask`，
//     自己不持有任何码值/文案常量（否则变成「拿 RAG 的值验 RAG 的值」，必然假绿）。
//  2. 必须显式用 \n —— fmt.Println 用平台行分隔符，跨平台会变成 \r\n。
//  3. grpc code 用小写名 —— Go 的 grpcodes.Code.String() 是 "InvalidArgument"，
//     Java 的 Status.Code.name() 是 "INVALID_ARGUMENT" ⇒ 两侧统一小写 snake_case。
package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
	"unicode"

	"github.com/redis/go-redis/v9"
	grpcodes "google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"sea-try-go/service/task/rpc/internal/logic"
	"sea-try-go/service/task/rpc/internal/svc"
	taskpb "sea-try-go/service/task/rpc/pb"
)

// ── 假 ConnPool：让真源逻辑的 DB 回源**以受控文案失败** ──────────────────────
//
// ⚠️ 为什么需要它：
//
//	Go 真源 `get_task_logic.go:58-60` 的裸 error 出口是
//	    if err != nil { return nil, err }
//	其中 err 来自 GORM 的 `Find(&rows).Error`。
//	要逐字节比对这个 error 的**文案**，就必须能**控制**它的文案；
//	而真实的 PG 故障文案（"dial tcp ..." / "pq: ..."）既依赖环境又不可控。
//
//	⇒ 这里给 GORM 塞一个「永远返回受控 error」的 ConnPool：
//	  · `postgres.New(postgres.Config{Conn: pool})` 让 dialector 直接采用它
//	    （module cache `gorm.io/driver/postgres@v1.6.0/postgres.go:90` 的
//	     `if dialector.Conn != nil { db.ConnPool = dialector.Conn }`）—— **不连库**；
//	  · `gorm.Open` 的自动 ping 只在 ConnPool 实现 `Ping() error` 时才发生
//	    （module cache `gorm.io/gorm@v1.31.1/gorm.go` 的 `if pinger, ok := ...`）
//	    ⇒ 本类型不实现 Ping ⇒ **零网络**。
//
//	⇒ 于是真源逻辑走的是**真实的那条代码路径**（Redis 未命中 → 回源查库 → 查库失败），
//	   只是「查库失败」的原因被换成了受控文案。
type failingConnPool struct {
	err error
}

func (p *failingConnPool) PrepareContext(ctx context.Context, query string) (*sql.Stmt, error) {
	return nil, p.err
}

func (p *failingConnPool) ExecContext(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	return nil, p.err
}

func (p *failingConnPool) QueryContext(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	return nil, p.err
}

// QueryRowContext 真源逻辑**不会**走到（它用的是 Find ⇒ QueryContext）。
// 返回 nil 而不是构造一个带 error 的 *sql.Row —— *sql.Row 没有公开构造器。
func (p *failingConnPool) QueryRowContext(ctx context.Context, query string, args ...interface{}) *sql.Row {
	return nil
}

// nopRedisLogger 把 go-redis 的连接池告警**静音**。
//
// ⚠️ 为什么必须静音：go-redis 的 pool logger 默认写 **stdout**
//    （`internal/log.go` 的 `Logging.Printf` ⇒ `log.New(os.Stdout, ...)`），
//    而本探针的 dump 也走 stdout ⇒ 日志行会混进 dump，把逐字节对拍搅黄。
//    ⚠️ 不是「把日志行过滤掉」，而是**从源头关掉**（过滤会掩盖真实异常）。
type nopRedisLogger struct{}

func (nopRedisLogger) Printf(ctx context.Context, format string, v ...interface{}) {}

func init() {
	redis.SetLogger(nopRedisLogger{})
}

// probeRedis 造一个**必然连不上**的 Redis 客户端。
//
// ⚠️ 真源 `get_task_logic.go:51` 的条件是 `if rerr != nil || len(pm) == 0`
//    ⇒ **Redis 报错也算未命中**，照样回源查库。
//    所以这里让 Redis 失败，正好走真源的「Redis 报错 ⇒ 回源」分支，
//    而且错误文案会被真源丢弃（`rerr` 只参与判断）⇒ 不污染 dump。
// ⚠️ Dialer 直接返回 error ⇒ **零网络**、**不 hang**；MaxRetries=-1 ⇒ 不重试。
func probeRedis() *redis.Client {
	return redis.NewClient(&redis.Options{
		Addr:       "127.0.0.1:1",
		MaxRetries: -1,
		Dialer: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return nil, errors.New("probe: redis unavailable")
		},
	})
}

// probeDB 造一个「查询必然失败」的 *gorm.DB。
func probeDB(err error) *gorm.DB {
	db, gerr := gorm.Open(postgres.New(postgres.Config{Conn: &failingConnPool{err: err}}),
		&gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if gerr != nil {
		fmt.Fprintln(os.Stderr, "GO PROBE: gorm.Open failed:", gerr)
		os.Exit(3)
	}
	return db
}

// canonicalCodeName 把 grpc 码名归一成 **snake_case 小写**。
//
// ⚠️ Go 的 grpcodes.Code.String() 是 CamelCase（"InvalidArgument"），
//    Java 的 Status.Code.name() 是 UPPER_SNAKE（"INVALID_ARGUMENT"）
//    ⇒ 两侧统一小写 snake_case 后才可比（官方 canonical 形态，见 module cache
//    `google.golang.org/grpc@v1.81.1/codes/code_string.go`）。
//
// ⚠️ **缩写串不能逐个大写前插下划线**：朴素的「每个大写字母前加 _」会把
//    "OK" 变成 "o_k"。这里用标准做法：
//    只有当「前一个字符不是大写」**或**「后一个字符是小写」时才插下划线。
//      "OK"             → "ok"
//      "InvalidArgument"→ "invalid_argument"
//      "HTTPStatus"     → "http_status"（若将来出现）
func canonicalCodeName(c grpcodes.Code) string {
	s := c.String()
	runes := []rune(s)
	var b strings.Builder
	for i, r := range runes {
		if i > 0 && unicode.IsUpper(r) {
			prevLower := !unicode.IsUpper(runes[i-1])
			nextLower := i+1 < len(runes) && unicode.IsLower(runes[i+1])
			if prevLower || nextLower {
				b.WriteByte('_')
			}
		}
		b.WriteRune(unicode.ToLower(r))
	}
	return b.String()
}

// escape 把一段文本压成**单行**可逐字节比对的形态。
//
// ⚠️ 转义规则两侧必须完全一致（Go 与 Java 各一份实现）：
//
//	`\` → `\\`   `|` → `\|`   LF → `\n`   CR → `\r`
//
// ⚠️ 为什么 rpc 层也要转义：description 是**错误原文**，可以是任意文本
//    （本探针刻意塞了一条含 `|` 的样本）⇒ 不转义就会把一行拆成两行。
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

// statusOf 从 error 里取出 (code, description)。
//
// ⚠️ 这里用的是 **grpc-go 自己的 status.FromError** —— 它就是「裸 error ⇒ UNKNOWN +
//    err.Error()」这条语义的**真源实现**（module cache
//    `google.golang.org/grpc@v1.81.1/status/status.go` 的 FromError：
//    非 status error ⇒ `New(codes.Unknown, err.Error())`）。
func statusOf(err error) (string, string) {
	if err == nil {
		return canonicalCodeName(grpcodes.OK), ""
	}
	st, _ := status.FromError(err)
	return canonicalCodeName(st.Code()), st.Message()
}

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: rpcprobe <uid-cases-file> <bare-cases-file>")
		os.Exit(2)
	}
	uidCases, err := readCases(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "read uid cases:", err)
		os.Exit(2)
	}
	bareCases, err := readCases(os.Args[2])
	if err != nil {
		fmt.Fprintln(os.Stderr, "read bare cases:", err)
		os.Exit(2)
	}

	ctx := context.Background()
	var b strings.Builder

	// ── RPCUID：uid <= 0 ⇒ 真源领域层直接抛 status error ─────────────────────
	// ⚠️ svcCtx 里 **不放** Rdb / Gdb：真源的 uid 校验在 `:43-45`，
	//    **早于**任何 Redis / DB 访问 ⇒ 两个 nil 字段根本不会被碰到。
	for _, c := range uidCases {
		uid, perr := strconv.ParseInt(c.value, 10, 64)
		if perr != nil {
			fmt.Fprintln(os.Stderr, "bad uid case:", c.name, c.value)
			os.Exit(2)
		}
		l := logic.NewGetTaskLogic(ctx, &svc.ServiceContext{})
		_, gerr := l.GetTask(&taskpb.GetTaskReq{UserId: uid})
		code, desc := statusOf(gerr)
		b.WriteString("RPCUID|" + c.name + "|" + code + "|" + escape(desc) + "\n")
	}

	// ── RPCBARE：DB 回源失败 ⇒ 真源 `return nil, err`（裸 error）────────────
	for _, c := range bareCases {
		l := logic.NewGetTaskLogic(ctx, &svc.ServiceContext{
			Rdb: probeRedis(),
			Gdb: probeDB(errors.New(c.value)),
		})
		_, gerr := l.GetTask(&taskpb.GetTaskReq{UserId: 1})
		code, desc := statusOf(gerr)
		b.WriteString("RPCBARE|" + c.name + "|" + code + "|" + escape(desc) + "\n")
	}

	// ── OBS：两侧必然不同的已知不可达差异 ─────────────────────────────────────
	for _, c := range obsCases() {
		b.WriteString("OBS|" + c.name + "|" + c.value + "\n")
	}

	os.Stdout.WriteString(b.String())
}

// obsCase 是一条 OBS 样本。
type obsCase struct {
	name  string
	value string
}

// obsCases 是**两侧必然不同**的已知差异（不可达 ⇒ 不构成行为漂移，但必须钉死）。
//
// ⚠️ 这两条不是「期望 Go 与 Java 一致」，而是「把已知的实现层差异钉死」：
//    若将来 Java 侧改了语义，OBS 段的值会变 ⇒ 探针变红
//    ⇒ 逼人重新评估这条差异是否还成立，而不是让它静默漂移。
func obsCases() []obsCase {
	// obs_ok_status：
	//   Go：status.Error(codes.OK, msg) 返回 **nil**（grpc-go
	//       `internal/status/status.go` 的 `func (s *Status) Err() error`：
	//       `if s.Code() == codes.OK { return nil }`）⇒ status.FromError(nil) ⇒ (nil, true)
	//       ⇒ 码是 OK、**没有 description**。
	//   Java：Status.OK.withDescription(msg).asRuntimeException() 造出来的是一个
	//       **非 nil**、code=OK、**带 description** 的 StatusRuntimeException。
	//   ⇒ 「OK 状态不是错误」是 grpc-go 的 API 形态，grpc-java 没有对应的塌缩。
	//   ⚠️ 不可达：真源领域层只会抛 codes.InvalidArgument 或裸 error。
	okCode, okDesc := statusOf(status.Error(grpcodes.OK, "user_id invalid"))

	// obs_wrapped_status：
	//   Go：fmt.Errorf("outer: %w", statusErr) —— grpc-go v1.81.1 的 FromError
	//       **会用 errors.As 穿透包装**找到内层 status（保住 code），
	//       但 **description 被换成外层 error 的全文**
	//       （`status/status.go`：`p.Message = err.Error()`）。
	//   Java：TaskServiceGrpcImpl 只认**直接抛出的** StatusRuntimeException
	//       ⇒ 包装过就落进 `catch (RuntimeException)` ⇒ 兜底成 UNKNOWN + 外层文案。
	//   ⇒ 「Go 穿透包装保 code / Java 不穿透」是跨语言的**结构性**差异。
	//   ⚠️ 不可达：真源的 api/rpc 两层都**原样透传**，没有一处做包装。
	wrapped := fmt.Errorf("outer: %w", status.Error(grpcodes.InvalidArgument, "user_id invalid"))
	wCode, wDesc := statusOf(wrapped)

	return []obsCase{
		{"obs_ok_status", okCode + "|" + escape(okDesc)},
		{"obs_wrapped_status", wCode + "|" + escape(wDesc)},
	}
}

// probeCase 是一条输入样本（name + value）。
type probeCase struct {
	name  string
	value string
}

// readCases 读 cases 文件：`name|value` 一行一条，空行与 # 注释行忽略。
func readCases(path string) ([]probeCase, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var out []probeCase
	for _, line := range strings.Split(string(raw), "\n") {
		line = strings.TrimRight(line, "\r")
		if strings.TrimSpace(line) == "" || strings.HasPrefix(strings.TrimSpace(line), "#") {
			continue
		}
		parts := strings.SplitN(line, "|", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("bad case line (want name|value): %q", line)
		}
		out = append(out, probeCase{name: parts[0], value: parts[1]})
	}
	return out, nil
}
