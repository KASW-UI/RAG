// =============================================================================
// cmd/token —— **探针代码**（不是真源复制件）
// ----------------------------------------------------------------------------
// 用途：为「api 整域活体对拍」签出探针要用的 JWT。
//
// 🔴 为什么必须用 **Go** 签、而不是在 Java 侧签：
//   · Go 侧的验签器是 go-zero `rest/token.TokenParser.doParseToken`
//     （rest/token/tokenparser.go:80-84）⇒ `request.ParseFromRequest(r,
//     request.AuthorizationHeaderExtractor, …, jwt.WithJSONNumber())`
//     ⇒ **claim 里的数字被解析成 json.Number**（不是 float64），
//     而真源 api 的 extractUserID（service/favorite/api/internal/logic/favorite/helper.go:14-34）
//     对 json.Number 走 `v.Int64()` 分支。
//   · RAG 侧的验签器是 jjwt（FavoriteJwtAuthInterceptor:167-178）⇒ 数字 claim 是 Integer/Long。
//   ⇒ **同一个 token 必须能被两侧各自的解析器都还原成同一个 int64 userId**，
//     才谈得上「同一个请求矩阵」；用本探针签 ⇒ 两侧拿到的是**逐字节相同**的 token。
//
// 🔴 签发算法必须是 **HS256**（Go 侧 `jwt.SigningMethodHS256` 写死；
//     RAG 侧 JwtUtil.getToken 也钉死 HS256 —— 见 users-common 的 JwtUtil 注释）。
//
// 🔴 secret 长度：Go 侧 golang-jwt **不校验** key 长度；jjwt 强制 ≥32 字节 ⇒
//     探针统一用真源 default secret `ae0536f9-6450-4606-8e13-5a19ed505da0`（36 字节）
//     ⇒ 两侧都满足，且 **不需要任何 padding**（padding 的等价性论证见 JwtUtil 的 javadoc）。
//
// 输出格式（每行 `KEY=VALUE`，run.sh 直接 `eval`）：
//
//	VALID_USER1=<token>     userId=1001（种子数据的归属用户）
//	VALID_USER2=<token>     userId=2002（种子数据里「别人的」收藏夹归属者）
//	EXPIRED=<token>         userId=1001，exp 在过去
//	BADSIG=<token>          userId=1001，**用另一个 secret** 签 ⇒ 验签必失败
// =============================================================================
package main

import (
	"fmt"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v4"
)

const (
	defaultSecret = "ae0536f9-6450-4606-8e13-5a19ed505da0"
	// 另一个 secret：只用于制造「签名错」这一档。
	// ⚠️ 必须**长度相同**（36 字节）⇒ jjwt 侧不会因为 key 长度不同而先抛 WeakKeyException，
	//    而是老老实实走「签名不匹配」⇒ 这才是我们要测的那一档。
	wrongSecret = "00000000-0000-0000-0000-000000000000"
)

func main() {
	secret := os.Getenv("PROBE_JWT_SECRET")
	if secret == "" {
		secret = defaultSecret
	}

	fmt.Printf("VALID_USER1=%s\n", sign(secret, 1001, time.Now().Add(24*time.Hour).Unix()))
	fmt.Printf("VALID_USER2=%s\n", sign(secret, 2002, time.Now().Add(24*time.Hour).Unix()))
	// 过期：exp 已是过去 ⇒ golang-jwt 判 Valid=false ⇒ Go 走 unauthorized()；
	// jjwt 同样判过期 ⇒ RAG 也走 unauthorized() ⇒ 两侧同为 401。
	fmt.Printf("EXPIRED=%s\n", sign(secret, 1001, time.Now().Add(-1*time.Hour).Unix()))
	// 签名错：secret 不同 ⇒ 两侧都验签失败 ⇒ 401。
	fmt.Printf("BADSIG=%s\n", sign(wrongSecret, 1001, time.Now().Add(24*time.Hour).Unix()))
}

// sign 用 HS256 签一个带 userId 数字 claim 的 token。
//
// ⚠️ claim 里的 userId 必须是**数字**（不是字符串）：
// 真源 api 的 extractUserID 对 string 会走 strconv.ParseInt 分支（也能过），
// 但 Go 侧验签器用 jwt.WithJSONNumber() ⇒ 数字 claim 才是与生产完全一致的形态。
func sign(secret string, userId int64, exp int64) string {
	claims := jwt.MapClaims{
		"userId": userId,
		"iat":    time.Now().Unix(),
		"exp":    exp,
	}
	token := jwt.New(jwt.SigningMethodHS256)
	token.Claims = claims
	signed, err := token.SignedString([]byte(secret))
	if err != nil {
		fmt.Fprintln(os.Stderr, "签发失败:", err)
		os.Exit(1)
	}
	return signed
}
