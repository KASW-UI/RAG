// users-common 双侧活体对拍探针 —— **Go 真源侧**。
//
// ⚠️ **本文件永远不写进真源仓**：`run.sh` 用 `go build -overlay=<json>` 把它
// **虚拟挂进**真源模块树（`service/user/user/rpc/cmd/userscommonprobe/main.go`），
// ⇒ ① 真源仓 `git status` 保持干净（脚本第 3 步会断言）；② 能 import 真源的
// `internal/model`（Go 的 internal 规则按**导入路径**判定 ⇒ 挂在
// `service/user/user/rpc/` 之下才合法）。
//
// 真源（**只读**）：
//   service/user/common/errmsg/errmsg.go      —— 错误码 → 文案
//   service/user/common/jwt/jwt.go            —— GetToken（HS256）
//   service/user/common/cryptx/password.go    —— bcrypt 散列 / 比对
//   service/user/user/rpc/internal/model/*.go —— GORM AutoMigrate（DDL 真源路径）
//
// 依赖的**库**对照面（不是本仓实现，但同样是 Go 侧的真实路径）：
//   github.com/zeromicro/go-zero@v1.10.2/core/mapping —— `,string` / 必填校验
//   github.com/zeromicro/go-zero@v1.10.2/core/jsonx   —— UseNumber 读 body
//   github.com/golang-jwt/jwt/v4@v4.5.2/request       —— Authorization 头取 token
//
// 输出：每行 `case_id<TAB>value`，供 run.sh 与 RAG 侧**逐字节**比对。
package main

import (
	"bufio"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	gojwt "github.com/golang-jwt/jwt/v4"
	"github.com/golang-jwt/jwt/v4/request"
	"github.com/zeromicro/go-zero/core/jsonx"
	"github.com/zeromicro/go-zero/core/mapping"
	"gorm.io/gorm"

	"sea-try-go/service/user/common/cryptx"
	"sea-try-go/service/user/common/errmsg"
	truthjwt "sea-try-go/service/user/common/jwt"
	"sea-try-go/service/user/user/rpc/internal/model"
)

// ── 固定夹具（两侧共用；任何一项改动都会让 expected.txt 失配）────────────────
const (
	// 固定口令的固定 bcrypt hash（由真源 cryptx.PasswordEncrypt 生成一次后冻结）。
	// 目的：**播种进库的值必须两侧逐字节相同** ⇒ 不能用每次随机 salt 的 hash。
	fixedBcryptHash = "$2a$10$k5Q0/H16AzxsOsbbng9Mz.S0S21mTo6/xYlKwLm.9wUw9UGJPoBMm"
	fixedPassword   = "probe-pass-123"

	// 固定 iat / exp：用于「原始字节」观测（必须可复算 ⇒ 不能用 now）。
	fixedIat    = int64(1700000000)
	fixedExp    = int64(1700003600)
	fixedUserId = int64(1234567890)

	// 交叉验签用的 userId = 2^53+1（顺带钉死 UNRESOLVED-USERSCOMMON-005 的精度问题）。
	xverifyUserId = int64(9007199254740993)

	// 播种进库的业务主键（与 xverifyUserId 相同 ⇒ 两侧同一行）。
	seedUid = int64(9007199254740993)
)

// keyLens = JWT 密钥长度档（覆盖 jjwt 的 HS256/HS384/HS512 三个阈值）。
var keyLens = []int{15, 16, 31, 32, 36, 47, 48, 63, 64, 65, 128}

// xverifyLens = 参与交叉验签的密钥档（与台账 §3 的 6 档一致）。
var xverifyLens = []int{15, 32, 36, 47, 48, 64}

// errmsgCodes = 真源 errmsg.go 里定义的全部常量 + 若干未登记码（回落路径）。
var errmsgCodes = []int{
	200, 500, 1015,
	5001, 5002, 5003, 5004, 5005, 5006, 5007, 5008, 5009, 5010,
	1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012,
	1016, 1017, 1018,
	3001, 3002, 3003,
	// 未登记码 ⇒ GetErrMsg 回落成 codeMsg[Error] = "FAIL"
	0, 1, 999, 1014, 1019, 4000, 99999, -1,
}

// secretFor = 造出指定字节数的密钥（字节内容确定 ⇒ 两侧算出同一签名）。
func secretFor(n int) string {
	const alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	var b strings.Builder
	for i := 0; i < n; i++ {
		b.WriteByte(alphabet[i%len(alphabet)])
	}
	return b.String()
}

// ── 输出器 ────────────────────────────────────────────────────────────────────
type emitter struct {
	w *bufio.Writer
}

func newEmitter(path string) *emitter {
	f, err := os.Create(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "cannot create %s: %v\n", path, err)
		os.Exit(2)
	}
	return &emitter{w: bufio.NewWriter(f)}
}

// emit 写一行 `id<TAB>value`；value 里的 TAB / 换行做转义（保证「一行一 case」）。
func (e *emitter) emit(id, value string) {
	v := strings.ReplaceAll(value, "\\", "\\\\")
	v = strings.ReplaceAll(v, "\t", "\\t")
	v = strings.ReplaceAll(v, "\n", "\\n")
	v = strings.ReplaceAll(v, "\r", "\\r")
	fmt.Fprintf(e.w, "%s\t%s\n", id, v)
}

func (e *emitter) close() { e.w.Flush() }

// ── JWT 观测工具 ──────────────────────────────────────────────────────────────

// splitToken 拆出 header / payload 的**原始** base64url 段。
func splitToken(tok string) (string, string, string, error) {
	parts := strings.Split(tok, ".")
	if len(parts) != 3 {
		return "", "", "", fmt.Errorf("bad token shape")
	}
	return parts[0], parts[1], parts[2], nil
}

func decodeSegment(seg string) (string, error) {
	raw, err := base64.RawURLEncoding.DecodeString(seg)
	if err != nil {
		return "", err
	}
	return string(raw), nil
}

// canonicalJSON = 键排序后的紧凑 JSON（两侧用同一套规则构造 ⇒ 可逐字节比）。
func canonicalJSON(obj map[string]any) string {
	keys := make([]string, 0, len(obj))
	for k := range obj {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var b strings.Builder
	b.WriteByte('{')
	for i, k := range keys {
		if i > 0 {
			b.WriteByte(',')
		}
		kb, _ := json.Marshal(k)
		b.Write(kb)
		b.WriteByte(':')
		vb, _ := json.Marshal(obj[k])
		b.Write(vb)
	}
	b.WriteByte('}')
	return b.String()
}

// canonSig = 对「规范化 signing input」的 HMAC-SHA256（hex）。
//
// 用途：Go 与 RAG 的 JWT **原始字节**必然不同（JSON 键序，见台账
// DRIFT-USERSCOMMON-002/003），所以 token 本身没法逐字节比。
// 这里把 header/payload 都**按键排序**后再签名 ⇒ 若两侧算出的 hex 相同，
// 就证明「差异仅来自 JSON 键序，签名原语与 claim 内容逐字节一致」。
func canonSig(secret string, header, payload string) string {
	input := base64.RawURLEncoding.EncodeToString([]byte(header)) + "." +
		base64.RawURLEncoding.EncodeToString([]byte(payload))
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(input))
	return hex.EncodeToString(mac.Sum(nil))
}

// ── 主流程 ────────────────────────────────────────────────────────────────────

func main() {
	outPath := flagOr("--out=", "")
	tokensPath := flagOr("--tokens=", "")
	peerTokensPath := flagOr("--peer-tokens=", "")
	pgHost := flagOr("--pg-host=", "127.0.0.1")
	pgPort := flagOr("--pg-port=", "5432")
	pgUser := flagOr("--pg-user=", "admin")
	pgPass := flagOr("--pg-pass=", "sea")
	pgDb := flagOr("--pg-db=", "first_db")
	// phase 1 = 库面 + DDL 面；phase 2 = 只做交叉验签（避免重复输出同一批 case）。
	phase := flagOr("--phase=", "1")

	if outPath == "" {
		fmt.Fprintln(os.Stderr, "usage: probe --out=<file> [--tokens=<file>] [--peer-tokens=<file>] [--pg-*]")
		os.Exit(2)
	}

	em := newEmitter(outPath)

	if phase == "2" {
		emitJwt(em, tokensPath, peerTokensPath)
		emitCryptx(em, tokensPath, peerTokensPath)
		em.close()
		return
	}

	emitErrmsg(em)
	emitJwt(em, tokensPath, peerTokensPath)
	emitCryptx(em, tokensPath, peerTokensPath)
	emitBearer(em)
	emitInt64(em)
	emitRequired(em)
	if pgPort != "" {
		emitDdl(em, pgHost, pgPort, pgUser, pgPass, pgDb)
	}

	em.close()
}

// flagOr = 极简参数解析（避免引入 flag 包的标准输出噪声）。
func flagOr(prefix, def string) string {
	for _, a := range os.Args[1:] {
		if strings.HasPrefix(a, prefix) {
			return strings.TrimPrefix(a, prefix)
		}
	}
	return def
}

// ── G1 errmsg ─────────────────────────────────────────────────────────────────
func emitErrmsg(em *emitter) {
	for _, code := range errmsgCodes {
		em.emit(fmt.Sprintf("c.errmsg.%d", code), errmsg.GetErrMsg(code))
	}
}

// ── G2 jwt ────────────────────────────────────────────────────────────────────
func emitJwt(em *emitter, tokensPath, peerTokensPath string) {
	// G2a：每档密钥的 token 形状（**不含**会随时间变化的 iat/exp ⇒ 值可复算）。
	for _, n := range keyLens {
		secret := secretFor(n)
		tok, err := truthjwt.GetToken(secret, fixedIat, fixedExp-fixedIat, fixedUserId)
		if err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		hSeg, pSeg, sSeg, err := splitToken(tok)
		if err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		headerRaw, err := decodeSegment(hSeg)
		if err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		payloadRaw, err := decodeSegment(pSeg)
		if err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		var header map[string]any
		if err := decodeJSONUseNumber([]byte(headerRaw), &header); err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		var payload map[string]any
		if err := decodeJSONUseNumber([]byte(payloadRaw), &payload); err != nil {
			em.emit(fmt.Sprintf("c.jwt.error.%d", n), "ERR:"+err.Error())
			continue
		}
		em.emit(fmt.Sprintf("c.jwt.alg.%d", n), fmt.Sprintf("%v", header["alg"]))
		em.emit(fmt.Sprintf("c.jwt.typ.%d", n), fmt.Sprintf("%v", header["typ"]))
		em.emit(fmt.Sprintf("c.jwt.header_keys.%d", n), joinSortedKeys(header))
		em.emit(fmt.Sprintf("c.jwt.payload_keys.%d", n), joinSortedKeys(payload))
		em.emit(fmt.Sprintf("c.jwt.siglen.%d", n), strconv.Itoa(len(sSeg)))
		// wire 层的事实（两侧可比）：userId 在 JSON 里是**数字**而不是字符串。
		em.emit(fmt.Sprintf("c.jwt.userid_json_type.%d", n), wireJSONTypeOf(payload["userId"]))
		// Go 内部类型（**Go 侧独有**）：go-zero 的 tokenparser 用 jwt.WithJSONNumber()
		// （rest/token/tokenparser.go:122-123）⇒ claim 是 json.Number，不是 float64。
		em.emit(fmt.Sprintf("g.jwt.userid_go_type.%d", n), jsonTypeOf(payload["userId"]))
		// 规范化 signing input 的签名（证明「差异仅来自 JSON 键序」）。
		em.emit(fmt.Sprintf("c.jwt.canon_sig.%d", n),
			canonSig(secret, canonicalJSON(header), canonicalJSON(payload)))
	}

	// G2b：原始字节（**键序**差异 ⇒ 两侧不同 ⇒ 登记为 REG 行，不参与「必须相同」判定）。
	{
		secret := secretFor(36)
		tok, _ := truthjwt.GetToken(secret, fixedIat, fixedExp-fixedIat, fixedUserId)
		hSeg, pSeg, _, _ := splitToken(tok)
		hRaw, _ := decodeSegment(hSeg)
		pRaw, _ := decodeSegment(pSeg)
		em.emit("c.jwt.raw.header", hRaw)
		em.emit("c.jwt.raw.payload", pRaw)
	}

	// G2c：交叉验签（真源签的 → 本侧验；本侧签的 → 真源验）。
	if tokensPath != "" {
		var lines []string
		for _, n := range xverifyLens {
			secret := secretFor(n)
			tok, err := truthjwt.GetToken(secret, time.Now().Unix(), 3600, xverifyUserId)
			if err != nil {
				continue
			}
			lines = append(lines, fmt.Sprintf("%d %s", n, tok))
		}
		writeLines(tokensPath, lines)
	}
	if peerTokensPath != "" {
		self := map[int]bool{}
		for _, n := range xverifyLens {
			self[n] = true
		}
		for _, line := range readLines(peerTokensPath) {
			f := strings.SplitN(line, " ", 2)
			if len(f) != 2 {
				continue
			}
			n, err := strconv.Atoi(f[0])
			if err != nil || !self[n] {
				continue
			}
			secret := secretFor(n)
			uid, err := verifyToken(f[1], secret)
			em.emit(fmt.Sprintf("c.jwt.xverify.go.%d", n),
				fmt.Sprintf("peer=%s,uid=%d", err2str(err), uid))
		}
	}
}

// verifyToken = 用真源真实路径验签（WithJSONNumber ⇒ claim 数值不失真）。
func verifyToken(tok, secret string) (int64, error) {
	parser := gojwt.NewParser(gojwt.WithJSONNumber())
	claims := gojwt.MapClaims{}
	_, err := parser.ParseWithClaims(tok, claims, func(t *gojwt.Token) (any, error) {
		return []byte(secret), nil
	})
	if err != nil {
		return 0, err
	}
	raw, ok := claims["userId"]
	if !ok {
		return 0, fmt.Errorf("userId claim missing")
	}
	num, ok := raw.(json.Number)
	if !ok {
		return 0, fmt.Errorf("userId claim is not json.Number (%T)", raw)
	}
	return num.Int64()
}

func err2str(err error) string {
	if err == nil {
		return "VERIFIED"
	}
	return "REJECTED:" + err.Error()
}

func joinSortedKeys(m map[string]any) string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return strings.Join(keys, ",")
}

func jsonTypeOf(v any) string {
	switch v.(type) {
	case float64:
		return "number(float64)"
	case json.Number:
		return "number(json.Number)"
	case string:
		return "string"
	case bool:
		return "bool"
	case nil:
		return "null"
	default:
		return fmt.Sprintf("%T", v)
	}
}

// wireJSONTypeOf = 只区分「wire 上是数字还是字符串」（跨语言可比的那一层）。
func wireJSONTypeOf(v any) string {
	switch v.(type) {
	case float64, json.Number:
		return "number"
	case string:
		return "string"
	case bool:
		return "bool"
	case nil:
		return "null"
	default:
		return fmt.Sprintf("%T", v)
	}
}

// decodeJSONUseNumber = 用 json.Number 解析（对齐 go-zero 的 jwt.WithJSONNumber()）。
func decodeJSONUseNumber(raw []byte, v any) error {
	dec := json.NewDecoder(strings.NewReader(string(raw)))
	dec.UseNumber()
	return dec.Decode(v)
}

// ── G3 cryptx ─────────────────────────────────────────────────────────────────

// bcryptFixtures = 固定的 hash 夹具（含畸形形态）。
var bcryptFixtures = []struct{ name, hash, pwd string }{
	{"match", fixedBcryptHash, fixedPassword},
	{"mismatch", fixedBcryptHash, "wrong-password"},
	{"empty_hash", "", fixedPassword},
	{"not_bcrypt", "abc", fixedPassword},
	{"truncated", "$2a$10$tooshort", fixedPassword},
	{"cost_zero", "$2a$00$" + fixedBcryptHash[7:], fixedPassword},
	{"cost_overflow", "$2a$99$" + fixedBcryptHash[7:], fixedPassword},
	{"prefix_2b", "$2b$10$" + fixedBcryptHash[7:], fixedPassword},
	{"prefix_2y", "$2y$10$" + fixedBcryptHash[7:], fixedPassword},
	{"prefix_2x", "$2x$10$" + fixedBcryptHash[7:], fixedPassword},
	{"prefix_1", "$1$10$" + fixedBcryptHash[7:], fixedPassword},
	{"empty_pwd", fixedBcryptHash, ""},
	{"unicode_pwd", fixedBcryptHash, "密码🔒"},
	{"pwd_72", fixedBcryptHash, strings.Repeat("a", 72)},
}

func emitCryptx(em *emitter, tokensPath, peerTokensPath string) {
	// 产出形状：bcrypt 的 salt 是随机的 ⇒ 只能断言「形状」，不能断言字节。
	h, err := cryptx.PasswordEncrypt(fixedPassword)
	if err != nil {
		em.emit("c.cryptx.encrypt_shape", "ERR:"+err.Error())
	} else {
		em.emit("c.cryptx.encrypt_shape",
			fmt.Sprintf("prefix=%s,len=%d,charset=%s", h[:7], len(h), bcryptCharsetOK(h)))
	}

	// 判定谱（固定夹具 ⇒ 两侧结果必须逐字节相同）。
	for _, f := range bcryptFixtures {
		em.emit("c.cryptx.check."+f.name, strconv.FormatBool(cryptx.CheckPassword(f.hash, f.pwd)))
	}

	// 交叉验签：本侧生成的 hash ⇒ 对侧能不能认。
	if tokensPath != "" {
		peer, err := cryptx.PasswordEncrypt(fixedPassword)
		if err == nil {
			writeLines(tokensPath+".cryptx", []string{peer})
		}
	}
	if peerTokensPath != "" {
		for _, line := range readLines(peerTokensPath + ".cryptx") {
			if line == "" {
				continue
			}
			em.emit("c.cryptx.xverify.go", strconv.FormatBool(cryptx.CheckPassword(line, fixedPassword)))
		}
	}
}

func bcryptCharsetOK(h string) string {
	const cs = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
	body := h[7:]
	for i := 0; i < len(body); i++ {
		if !strings.ContainsRune(cs, rune(body[i])) {
			return "bad"
		}
	}
	return "ok"
}

// ── G4 bearer ─────────────────────────────────────────────────────────────────

// bearerInputs = 11 个畸形/边界输入（与台账 §4 的 11 个一致）。
var bearerInputs = []string{
	"Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"BEARER eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"Bearer  eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"XXBearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"Bearer ",
	"Bearer",
	"eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"",
	" Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
	"Bearer\teyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.sig",
}

func emitBearer(em *emitter) {
	for i, in := range bearerInputs {
		// 验签路径：golang-jwt 的 **stripBearerPrefixFromTokenString**（真源链路上的那个 filter）。
		//
		// ⚠️ 为什么取 `request.AuthorizationHeaderExtractor.Filter` 而不是直接
		//    `AuthorizationHeaderExtractor.ExtractToken(req)`：
		//    后者多带了一层 `HeaderExtractor`（request/extractor.go:25-33）的
		//    「header 为空 ⇒ ErrNoTokenInRequest」判定，而 RAG 侧
		//    `BearerTokenExtractor.tokenForSignatureVerification` 只对应 **filter 本身**
		//    （真源出处 request/oauth2.go:7-12；RAG 侧 javadoc 同款）。
		//    `PostExtractionFilter.Filter` 是**导出字段** ⇒ 直接调到那个未导出的函数，
		//    **不重写实现**（AGENTS.md §3.1：不拿「看起来等价」的写法替代真源）。
		tok, err := request.AuthorizationHeaderExtractor.Filter(in)
		if err != nil {
			tok = "ERR:" + err.Error()
		}
		em.emit(fmt.Sprintf("c.bearer.sig.%d", i), tok)

		// 黑名单路径：strings.TrimPrefix(authHeader, "Bearer ")。
		em.emit(fmt.Sprintf("c.bearer.trim.%d", i), strings.TrimPrefix(in, "Bearer "))
	}
}

// ── G5 `,string` int64 ───────────────────────────────────────────────────────

type int64Req struct {
	Value int64 `json:"value,string"`
}

// int64Bodies = 17 个输入（与台账 §4 的 17 条一致）。
var int64Bodies = []string{
	`{"value":"123"}`,
	`{"value":123}`,
	`{"value":1.5}`,
	`{"value":1.0}`,
	`{"value":1e2}`,
	`{"value":true}`,
	`{"value":null}`,
	`{"value":"abc"}`,
	`{"value":""}`,
	`{"value":" 12"}`,
	`{"value":"+123"}`,
	`{"value":"9223372036854775807"}`,
	`{"value":"9223372036854775808"}`,
	`{"value":"-9223372036854775808"}`,
	`{"value":"-9223372036854775809"}`,
	`{"value":9223372036854775807}`,
	`{"value":9223372036854775808}`,
}

func emitInt64(em *emitter) {
	for i, body := range int64Bodies {
		// 真源路径：jsonx（UseNumber）读 body → mapping.UnmarshalJsonMap。
		var m map[string]any
		if err := jsonx.Unmarshal([]byte(body), &m); err != nil {
			em.emit(fmt.Sprintf("c.int64.%d", i), "REJECT:parse")
			continue
		}
		var req int64Req
		if err := mapping.UnmarshalJsonMap(m, &req); err != nil {
			em.emit(fmt.Sprintf("c.int64.%d", i), "REJECT")
			continue
		}
		em.emit(fmt.Sprintf("c.int64.%d", i), fmt.Sprintf("OK:%d", req.Value))
	}
}

// ── G6 必填校验 ───────────────────────────────────────────────────────────────

type requiredReq struct {
	Name  string            `json:"name"`
	Extra map[string]string `json:"extra_info"`
}

func emitRequired(em *emitter) {
	cases := []struct {
		id   string
		body string
	}{
		{"scalar_absent", `{}`},
		{"scalar_empty", `{"name":""}`},
		{"scalar_explicit_null", `{"name":null}`},
		{"map_absent", `{"name":"x"}`},
	}
	for _, c := range cases {
		var m map[string]any
		if err := jsonx.Unmarshal([]byte(c.body), &m); err != nil {
			em.emit("c.required."+c.id, "ERR:parse")
			continue
		}
		var req requiredReq
		if err := mapping.UnmarshalJsonMap(m, &req); err != nil {
			em.emit("c.required."+c.id, err.Error())
			continue
		}
		em.emit("c.required."+c.id, "OK")
	}
}

// ── G7 DDL + 播种（真源路径 = GORM AutoMigrate）────────────────────────────────

func emitDdl(em *emitter, host, port, user, pass, dbName string) {
	// ⚠️ **pgconn 的 DSN 解析坑（2026-10-29 实测）**：
	//   `password=`（**空值**）会让 pgconn 把**下一个 key=value 整段吃进密码**⇒
	//   实测 `password= dbname=x` ⇒ Database=""、Password="dbname=x"
	//   ⇒ 连接落到「与用户名同名的库」⇒ 报 `database "pr" does not exist`。
	//   真源的 `model.InitDB`（service/user/user/rpc/internal/model/db.go:21-27）
	//   **无条件**写 `password=%s` ⇒ 它自己有同样的隐患（空密码时 dbname 被吃）。
	//   本探针侧的对策：**密码为空时填一个占位值**（trust 认证下不影响）。
	if pass == "" {
		pass = "r2ucprobe"
	}
	conf := model.DBConf{
		Host:     host,
		Port:     port,
		User:     user,
		Password: pass,
		DBName:   dbName,
		Mode:     "disable",
	}
	db := model.InitDB(conf)

	// 播种（**两侧同一行** ⇒ 不是「两边都空」那种无信息量的对拍）。
	u := model.User{
		Uid:      seedUid,
		Username: "probe-user",
		Password: fixedBcryptHash,
		Email:    "probe@example.invalid",
		Status:   1,
		Score:    7,
		ExtraInfo: map[string]string{
			"a": "1",
			"b": "2",
		},
	}
	if err := db.Create(&u).Error; err != nil {
		em.emit("c.seed.users_user.error", "ERR:"+err.Error())
		return
	}
	h := model.UserAvatarHistory{
		HistoryID:   "probe-history-1",
		Uid:         seedUid,
		AvatarURL:   "https://example.invalid/a.png",
		ContentType: "image/png",
		SizeBytes:   4096,
		IsCurrent:   true,
	}
	if err := db.Create(&h).Error; err != nil {
		em.emit("c.seed.users_user_avatar_history.error", "ERR:"+err.Error())
		return
	}

	// 读回（走真源的 GORM 读路径）。
	var got model.User
	if err := db.Where("uid = ?", seedUid).First(&got).Error; err != nil {
		em.emit("c.seed.users_user.error", "ERR:"+err.Error())
		return
	}
	em.emit("c.seed.users_user.uid", strconv.FormatInt(got.Uid, 10))
	em.emit("c.seed.users_user.username", got.Username)
	em.emit("c.seed.users_user.password", got.Password)
	em.emit("c.seed.users_user.email", got.Email)
	em.emit("c.seed.users_user.status", strconv.FormatInt(got.Status, 10))
	em.emit("c.seed.users_user.score", strconv.FormatInt(int64(got.Score), 10))
	em.emit("c.seed.users_user.extra_info", canonicalJSON(map[string]any{
		"a": got.ExtraInfo["a"],
		"b": got.ExtraInfo["b"],
	}))

	var gotH model.UserAvatarHistory
	if err := db.Where("history_id = ?", "probe-history-1").First(&gotH).Error; err != nil {
		em.emit("c.seed.users_user_avatar_history.error", "ERR:"+err.Error())
		return
	}
	em.emit("c.seed.users_user_avatar_history.history_id", gotH.HistoryID)
	em.emit("c.seed.users_user_avatar_history.uid", strconv.FormatInt(gotH.Uid, 10))
	em.emit("c.seed.users_user_avatar_history.avatar_url", gotH.AvatarURL)
	em.emit("c.seed.users_user_avatar_history.content_type", gotH.ContentType)
	em.emit("c.seed.users_user_avatar_history.size_bytes", strconv.FormatInt(gotH.SizeBytes, 10))
	em.emit("c.seed.users_user_avatar_history.is_current", strconv.FormatBool(gotH.IsCurrent))

	// DDL 列级快照（从 information_schema 读 ⇒ 与「谁建的表」无关，只看结果）。
	emitColumns(em, db, "users", "users_user")
	emitColumns(em, db, "user_avatar_history", "users_user_avatar_history")
}

// emitColumns = 把一张表的列定义拍平成一行（列名:类型:可空:默认值,…）。
//
// ⚠️ `column_default` 读的是 **DDL 里的字面量**（PG 原样存文本）⇒
//   `DEFAULT false`（Go / GORM）与 `DEFAULT FALSE`（RAG 脚本）会**字面不同**。
//   ⇒ 这里**逐列**输出**归一化后**的值（`c.ddl.col.*`：小写 + 抹掉序列名）
//   ⇒ 绝大多数列是「两侧逐字节相同」的硬断言。
func emitColumns(em *emitter, db *gorm.DB, goTable, ragTable string) {
	type row struct {
		ColumnName    string
		DataType      string
		IsNullable    string
		ColumnDefault string
	}
	var rows []row
	if err := db.Raw(
		"SELECT column_name, data_type, is_nullable, COALESCE(column_default, '') AS column_default "+
			"FROM information_schema.columns "+
			"WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position",
		goTable).Scan(&rows).Error; err != nil {
		em.emit("c.ddl.col."+ragTable+".__error", "ERR:"+err.Error())
		return
	}
	// 逐列（**默认值已归一化**：序列名抹掉、大小写统一）⇒ 绝大多数列是「两侧逐字节相同」
	for _, r := range rows {
		em.emit(fmt.Sprintf("c.ddl.col.%s.%s", ragTable, r.ColumnName),
			fmt.Sprintf("%s:%s:%s", r.DataType, r.IsNullable, normalizeDefault(r.ColumnDefault)))
	}
}

// normalizeDefault = 抹掉序列名（nextval('users_id_seq') → nextval(...)）并统一小写。
var nextvalRe = regexp.MustCompile(`nextval\('[^']*'(::regclass)?\)`)

func normalizeDefault(def string) string {
	d := nextvalRe.ReplaceAllString(def, "nextval(...)")
	return strings.ToLower(d)
}

// ── 小工具 ────────────────────────────────────────────────────────────────────

func writeLines(path string, lines []string) {
	f, err := os.Create(path)
	if err != nil {
		return
	}
	defer f.Close()
	w := bufio.NewWriter(f)
	for _, l := range lines {
		fmt.Fprintln(w, l)
	}
	w.Flush()
}

func readLines(path string) []string {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()
	var out []string
	s := bufio.NewScanner(f)
	s.Buffer(make([]byte, 0, 64*1024), 4*1024*1024)
	for s.Scan() {
		out = append(out, s.Text())
	}
	return out
}
