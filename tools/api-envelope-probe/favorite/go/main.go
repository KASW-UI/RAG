// tools/api-envelope-probe/favorite 的 Go 半边。
//
// ⚠️ 本文件**不是**生产代码，它是「把 Go 真源的响应字节原样算出来」的探针。
//
// 它用**真的** go-zero 库（rest/httpx + rest/handler）与**从 Go 真源逐字节复制**的
// 三个文件（response.go / types.go / errmsg.go）算出 favorite api 的响应字节。
// 复制件由 run.sh 做 sha256 比对 ⇒ **真源变了探针立刻响**。
//
// 输出格式（两侧必须逐字节相同）：
//
//	CASE|<name>|<status>|<content-type>|<body>   参与逐字节对拍
//	OBS|<name>|<status>|<content-type>|<body>    **两侧本该不同**的已知差异（不参与对拍）
//
// ⚠️ 为什么用「真 go-zero」而不是照着源码复刻：
//  ① httpx.OkJsonCtx → jsonx.Marshal（encoding/json + SetEscapeHTML(false) + **去尾换行**）
//     —— 这几个细节（HTML 转义、尾换行）手写复刻几乎必错；
//  ② httpx.Parse → ParseForm → GetFormValues 的 **maxFormParamCount = 2048** 判据
//     只存在于真库里；
//  ③ handler.RecoverHandler 的 500 响应体（**空 body**）也只在真库里。
package main

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strconv"
	"strings"

	favoritecommon "favoriteapienvelopeprobe/favoritecommon"
	"favoriteapienvelopeprobe/common/response"
	"favoriteapienvelopeprobe/types"

	"github.com/zeromicro/go-zero/rest/handler"
	"github.com/zeromicro/go-zero/rest/httpx"
)

// emit 把一条用例的**完整可观察响应**打出来。
//
// ⚠️ Content-Type 取的是「写完之后」的值（httptest.ResponseRecorder 在 WriteHeader
// 时对 header 做了快照，但 Header() 仍返回同一个 map）⇒ 与真实写出后的头部一致。
func emit(name string, rec *httptest.ResponseRecorder) {
	fmt.Printf("CASE|%s|%d|%s|%s\n",
		name,
		rec.Code,
		rec.Header().Get("Content-Type"),
		rec.Body.String())
}

// ok 用**真的** httpx.OkJsonCtx 写一个成功/失败的 envelope。
//
// ⚠️ 这就是 Go 真源 7 个 handler 唯一的响应写法：
//
//	httpx.OkJsonCtx(r.Context(), w, &response.Response{Code: code, Msg: GetErrMsg(code), Data: resp})
func ok(name string, code int, msg string, data any) {
	rec := httptest.NewRecorder()
	httpx.OkJsonCtx(context.Background(), rec, &response.Response{
		Code: code,
		Msg:  msg,
		Data: data,
	})
	emit(name, rec)
}

func main() {
	// ── 1. 成功：收藏夹（folderId 带 ,string ⇒ JSON 字符串；createdAt/updatedAt 不带 ⇒ JSON 数字）──
	//
	// ⚠️ 特意用 9007199254740993 = 2^53+1：**裸 JSON 数字会丢精度**（变成 9007199254740992）
	// ⇒ 这条用例能抓到「把 ,string 写成数字」的错误。
	ok("ok_folder_snowflake", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.FavoriteFolder{
			FolderId:  9007199254740993,
			Name:      "技术",
			CreatedAt: 1700000000123,
			UpdatedAt: 1700000000456,
		})

	// ⚠️ 零值：Go 的 encoding/json **不省略**零值（没有 omitempty）⇒ 必须原样写出。
	ok("ok_folder_zero_values", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.FavoriteFolder{})

	// ── 2. 成功：收藏条目（title/cover 带 ,optional）──
	ok("ok_item_full", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.FavoriteItem{
			FavoriteId: 9007199254740993,
			FolderId:   7001,
			TargetId:   "2001",
			TargetType: "article",
			Title:      "标题",
			Cover:      "https://example.invalid/c.png",
			CreatedAt:  1700000000123,
		})

	// 🔴 **最容易被写错的一条**：go-zero mapping 的 `,optional` **不是** encoding/json 的
	// `omitempty` ⇒ **encoding/json 会忽略这个未知选项** ⇒ title/cover **永远出现**，
	// 为空时是 `""`（不是 null、也不是消失）。
	// ⇒ 本用例把这条钉死。
	ok("ok_item_empty_optional", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.FavoriteItem{
			FavoriteId: 1,
			FolderId:   2,
			TargetId:   "3",
			TargetType: "article",
		})

	// ── 3. 列表：空集合是 [] 不是 null（Go 侧无条件 make）──
	ok("ok_folders_empty_list", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.GetFavoriteFoldersResp{Folders: []types.FavoriteFolder{}})

	ok("ok_folders_one", favoritecommon.Success, favoritecommon.GetErrMsg(favoritecommon.Success),
		types.GetFavoriteFoldersResp{Folders: []types.FavoriteFolder{{
			FolderId:  7,
			Name:      "收藏夹",
			CreatedAt: 1,
			UpdatedAt: 2,
		}}})

	// ── 4. 失败：data 为 nil ⇒ 仍是三字段 envelope（"data":null）──
	ok("err_invalid_param", favoritecommon.ErrorInvalidParam,
		favoritecommon.GetErrMsg(favoritecommon.ErrorInvalidParam), nil)
	ok("err_unauthorized", favoritecommon.ErrorUnauthorized,
		favoritecommon.GetErrMsg(favoritecommon.ErrorUnauthorized), nil)
	ok("err_not_found", favoritecommon.ErrorNotFound,
		favoritecommon.GetErrMsg(favoritecommon.ErrorNotFound), nil)

	// ── 5. httpx.Parse 失败（参数过多）⇒ handler 走 5003 分支 ──
	// ⚠️ 这是 api.md 记的「Go 侧「非空值 >2048」时的逐字节响应」那条 🟡 源码级未验项。
	// 这里用**真的** httpx.Parse + **真的** 2048 判据 ⇒ 双侧活体。
	{
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/favorite/v1/item/list?"+manyParams(2049), nil)
		var parsed types.GetFavoritesByFolderReq
		err := httpx.Parse(req, &parsed)
		if err == nil {
			fmt.Fprintln(os.Stderr, "!! httpx.Parse 竟然没报错（真库的 2048 判据没生效？）")
			os.Exit(1)
		}
		// Go 真源 handler 的写法（create_favorite_folder_handler.go:18-26）
		httpx.OkJsonCtx(context.Background(), rec, &response.Response{
			Code: favoritecommon.ErrorInvalidParam,
			Msg:  favoritecommon.GetErrMsg(favoritecommon.ErrorInvalidParam),
			Data: nil,
		})
		emit("err_too_many_form_params", rec)
	}

	// ⚠️ 对照组（**不产生输出行**）：2048 个参数恰好在界内 ⇒ 必须**不报错**。
	//    它证明上一条不是「参数一多就报错」，而是**超过** 2048 才报错。
	//    ⚠️ 它**不进 expected.txt**：它的 Go 响应是合成的（真实响应会带业务数据），
	//    与 RAG 侧不可逐字节比较 ⇒ 只作为 Go 侧的内部断言存在。
	{
		req := httptest.NewRequest(http.MethodGet, "/favorite/v1/item/list?"+manyParams(2048), nil)
		var parsed types.GetFavoritesByFolderReq
		if err := httpx.Parse(req, &parsed); err != nil {
			fmt.Fprintf(os.Stderr, "!! 2048 个参数竟然报错了：%v\n", err)
			os.Exit(1)
		}
	}

	// ── 6. panic ⇒ go-zero 默认链的 RecoverHandler ──
	// 🔴 这条**推翻**了台账里「Go 的 RecoverHandler 写 500 + text/plain」的说法：
	// 真库的 handler.RecoverHandler 是 `w.WriteHeader(500)` ⇒ **空 body、无 Content-Type**。
	// 且它是**默认中间件**（rest/engine.go:158，在 conf.Middlewares.Recover 为真时挂上）
	// ⇒ 这条分支**不是**「Go logic 不会 panic ⇒ 不可达」。
	//
	// ⇒ 两侧**本该不同** ⇒ 走 OBS 段（不参与逐字节对拍，但差异消失时要报错）。
	{
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/favorite/v1/folder/list", nil)
		panicking := handler.RecoverHandler(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			panic("boom")
		}))
		panicking.ServeHTTP(rec, req)
		fmt.Printf("OBS|panic_recover_500|%d|%s|%s\n",
			rec.Code, rec.Header().Get("Content-Type"), rec.Body.String())
	}
}

// manyParams 造 n 个**非空**的 query 参数，**第一个固定是 folderId**。
//
// ⚠️ ① 必须是「非空值」：go-zero 的 GetFormValues 数的是**非空值个数**
//    （`if len(v) == 0 { continue }`）⇒ 空值不计数。
// ⚠️ ② 必须带一个**合法的 folderId**：types.GetFavoritesByFolderReq 的
//    `form:"folderId,string"` 是**必填** ⇒ 缺了它，httpx.Parse 会先因
//    「field "folderId" is not set」失败 ⇒ 那测的就不是「参数个数上限」而是「缺字段」了。
//    ⇒ 带一个 folderId ⇒ 应用层永不拒 ⇒ 结果只可能来自 2048 那条判据。
func manyParams(n int) string {
	var b strings.Builder
	b.WriteString("folderId=123")
	for i := 1; i < n; i++ {
		b.WriteString("&p")
		b.WriteString(strconv.Itoa(i))
		b.WriteString("=v")
	}
	return b.String()
}

var _ = url.Values{}
