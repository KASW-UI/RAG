// Go 侧「Go 是否读 body」探针 —— 真源直接调用 go-zero v1.10.2 的 rest/httpx.Parse。
//
// ⚠️ 本文件是 AGENTS.md §3.3「凡写『与 Go 一致』，必须有两侧同时跑的探针」的 Go 半边。
// 它的价值不只是回归守门，更是**把 Go 的行为取成基准**：Java 半边（java/Probe.java）
// 的期望值不是人写的，是这份输出 ⇒ 避免「拿 RAG 现有实现当对照基准」（§3.1 明令禁止）。
//
// ══════════════════════════════════════════════════════════════════════
// 为什么探的是 hot 域（GET /hot/v1/articles）
// ══════════════════════════════════════════════════════════════════════
// 探针对象选 hot，因为它同时具备三个「最容易看漏」的条件：
//   ① Go 的路由是 **GET**（不是 POST）⇒ 容易误以为「GET 不读 body」；
//   ② 但 go-zero 的 httpx.Parse 里 **ParseJsonBody 是无条件调用的**
//      （rest/httpx/requests.go:63-65）⇒ GET 也会读 JSON body；
//   ③ HotArticlesReq **只有 form tag、没有 json tag**（types.go:22-25）
//      ⇒ JSON 字段被整体忽略 ⇒ 本端点的 JSON body 只影响「能不能解析」这一个判定。
//
// 探针的判定口径 = **httpx.Parse 返不返错**：
//   返回错 ⇒ Go 读了 body 且解析失败（或类型不匹配）⇒ REJECTED
//   不返错 ⇒ Go 没读 body（或读了且解析成功）⇒ ACCEPTED
//
// 输出格式：<用例名>|<判定>|<细节>   —— 用例名必须与 Java 半边**逐字一致**
package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"

	"github.com/zeromicro/go-zero/rest/httpx"
)

// 真源镜像：Sea-RideTheWind_golang/service/hot/api/internal/types/types.go:22-25
//
// ⚠️ 必须逐字镜像（tag 也要一致）：go-zero 的 formUnmarshaler 只认 `form` tag，
//    没有 json tag ⇒ JSON body 里的同名字段**不会**覆盖 form 值
//    （core/mapping/utils.go:589-597 的 usingDifferentKeys）。
type HotArticlesReq struct {
	Page     int32 `form:"page,default=1"`
	PageSize int32 `form:"page_size,default=20"`
}

type probeCase struct {
	name string
	// contentType：空串 ⇒ **不设** Content-Type 头
	contentType string
	body        string
	// contentLength：<0 ⇒ 强制 -1（Go 对 chunked 请求就是 -1）
	contentLength int64
}

func main() {
	// 9 MiB 的**单个** JSON 对象（一个 JSON 值整体超过 8 MiB）
	// ⚠️ 这条是「Go 的 maxBodyLen=8MiB 静默截断」的判定用例
	big := `{"brief":"` + strings.Repeat("x", 9*1024*1024) + `"}`

	cases := []probeCase{
		// ── ① 判据矩阵：Content-Type 字面匹配（Go: strings.Contains，大小写敏感）──
		{name: "J01 标准 JSON + 畸形 body", contentType: "application/json", body: "{", contentLength: 1},
		{name: "J02 大写 JSON（Application/JSON）+ 畸形 body", contentType: "Application/JSON", body: "{", contentLength: 1},
		{name: "J03 全大写 JSON（APPLICATION/JSON）+ 畸形 body", contentType: "APPLICATION/JSON", body: "{", contentLength: 1},
		{name: "J04 混合大小写（application/JSON）+ 畸形 body", contentType: "application/JSON", body: "{", contentLength: 1},
		{name: "J05 带 charset + 畸形 body", contentType: "application/json; charset=utf-8", body: "{", contentLength: 1},
		{name: "J06 无空格 charset + 畸形 body", contentType: "application/json;charset=utf-8", body: "{", contentLength: 1},
		{name: "J07 +json 后缀（application/vnd.api+json）+ 畸形 body", contentType: "application/vnd.api+json", body: "{", contentLength: 1},
		{name: "J08 非 JSON（text/plain）+ 畸形 body", contentType: "text/plain", body: "{", contentLength: 1},
		{name: "J09 无 Content-Type + 畸形 body", contentType: "", body: "{", contentLength: 1},
		{name: "J10 逗号多值头（application/json, application/xml）+ 畸形 body", contentType: "application/json, application/xml", body: "{", contentLength: 1},
		{name: "J11 畸形头内分号（application/json;;charset）+ 畸形 body", contentType: "application/json;;charset", body: "{", contentLength: 1},

		// ── ② 判据矩阵：Content-Length ──
		{name: "J12 Content-Length=0 + JSON 头", contentType: "application/json", body: "", contentLength: 0},
		{name: "J13 chunked（Content-Length=-1）+ JSON 头 + 畸形 body", contentType: "application/json", body: "{", contentLength: -1},

		// ── ③ 顶层形态（Go 只接受 JSON **对象**）──
		{name: "J14 顶层数组 + JSON 头", contentType: "application/json", body: "[1,2]", contentLength: 5},
		{name: "J15 顶层 null + JSON 头", contentType: "application/json", body: "null", contentLength: 4},
		{name: "J16 顶层数字 + JSON 头", contentType: "application/json", body: "123", contentLength: 3},
		{name: "J17 顶层字符串 + JSON 头", contentType: "application/json", body: `"x"`, contentLength: 3},
		{name: "J18 尾随垃圾（{\"a\":1} garbage）+ JSON 头", contentType: "application/json", body: `{"a":1} garbage`, contentLength: 15},
		{name: "J19 合法对象 + JSON 头", contentType: "application/json", body: `{"page":9,"page_size":3}`, contentLength: 25},

		// ── ④ 🔴 Go 的 maxBodyLen = 8MiB 静默截断 ──
		{name: "J20 9MiB 单个 JSON 对象 + JSON 头", contentType: "application/json", body: big, contentLength: int64(len(big))},
	}

	for _, c := range cases {
		verdict, detail := run(c)
		fmt.Printf("%s|%s|%s\n", c.name, verdict, truncate(detail, 160))
	}
}

// truncate 截断细节 —— ⚠️ 不是为了好看：
//
//	go-zero 的 jsonx.formatError（core/jsonx/json.go:78-80）把**整个 body** 塞进错误文本
//	⇒ 9 MiB 那条用例的错误详情就是 9 MiB ⇒ 输出文件会被撑爆 ⇒ 对拍脚本读不动。
func truncate(s string, n int) string {
	s = strings.ReplaceAll(s, "\n", "\\n")
	if len(s) <= n {
		return s
	}
	return s[:n] + fmt.Sprintf("...(len=%d)", len(s))
}

func run(c probeCase) (string, string) {
	// ⚠️ 用 httptest.NewRequest：它给出真 *http.Request（真 Header、真 Body）
	//    ⇒ 判据落在 go-zero 的真代码上，不是自己复刻一份。
	r := httptest.NewRequest(http.MethodGet, "/hot/v1/articles?page=5", strings.NewReader(c.body))
	if c.contentType != "" {
		r.Header.Set("Content-Type", c.contentType)
	}
	// ⚠️ 必须**覆盖** ContentLength：
	//    httptest.NewRequest 会按 reader 长度自动填 ⇒ 想测 0 / -1 就得自己改。
	r.ContentLength = c.contentLength

	var v HotArticlesReq
	if err := httpx.Parse(r, &v); err != nil {
		return "REJECTED", err.Error()
	}
	return "ACCEPTED", fmt.Sprintf("page=%d page_size=%d", v.Page, v.PageSize)
}
