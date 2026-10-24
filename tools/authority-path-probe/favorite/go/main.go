// Command main 复刻 Go 侧权威读端点的**两层**行为，输出可与 Java 侧逐行比对的矩阵。
//
// Go 出处：service/favorite/rpc/internal/authority/http.go:27-99
//
//	mux := http.NewServeMux()
//	mux.HandleFunc("GET /internal/v1/favorite/facts/{producer}/{event_id}", func(w, r) {
//	    w.Header().Set("Cache-Control", "no-store")
//	    value := r.Header.Values("Authorization")
//	    if len(value) != 1 || !strings.HasPrefix(value[0], "Bearer ") || … { http.Error(w, "unauthorized", 401); return }
//	    if r.URL.RawQuery != "" { http.NotFound(w, r); return }
//	    ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
//	    defer cancel()
//	    fact, err := reader.AuthoritativeFavoriteFact(ctx, …)
//	    if errors.Is(err, model.ErrFavoriteFactUnavailable) { http.NotFound(w, r); return }
//	    if err != nil { http.Error(w, "source unavailable", 503); return }
//	    w.Header().Set("Content-Type", "application/json")
//	    …
//	})
//
// 本探针要暴露的是 **Java 侧手写 servlet 最容易漏掉的三层**：
//  ① 【mux 层】path 需要清理 ⇒ 301 + Location（**在鉴权之前**）；模式不匹配 ⇒ 404（**也在鉴权之前**）；
//  ② 【handler 层】鉴权 401 ⇒ body 末尾带 "\n"（http.Error 用 fmt.Fprintln）；
//  ③ 【handler 层】404 ⇒ "404 page not found\n" + text/plain + nosniff（http.NotFound → http.Error）。
//
// 只用标准库 ⇒ 无外部依赖 ⇒ 可离线跑。
package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
)

const token = "favorite-authority-test-token-0123456789"

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /internal/v1/favorite/facts/{producer}/{event_id}", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		value := r.Header.Values("Authorization")
		if len(value) != 1 || !strings.HasPrefix(value[0], "Bearer ") ||
			value[0][len("Bearer "):] != token {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if r.URL.RawQuery != "" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, "producer=%s event_id=%s", r.PathValue("producer"), r.PathValue("event_id"))
	})

	// ── ① mux 层：path 形状 ──────────────────────────────────────────────
	muxPaths := []string{
		"/internal/v1/favorite/facts/rtw.community.favorite/ev",
		"/internal/v1/favorite/facts/rtw.community.favorite//ev",
		"/internal/v1/favorite/facts/rtw.community.favorite/ev/extra",
		"/internal/v1/favorite/facts/rtw.community.favorite",
		"/internal/v1/favorite/facts/rtw.community.favorite/",
		"/internal/v1/favorite/facts/rtw.community.favorite/a%2Fb",
		"/internal/v1/favorite/facts/rtw.community.favorite/%2E%2E",
		"/internal/v1/favorite/facts/rtw.community.favorite//a%2Fb",
		"/internal/v1/favorite/facts/rtw.community.favorite/./ev",
		"/internal/v1/favorite/facts/rtw.community.favorite/x/../ev",
	}
	for _, p := range muxPaths {
		// 无 Authorization ⇒ 命中 handler 时应是 401；未命中（mux 404/301）时与鉴权无关。
		req := httptest.NewRequest(http.MethodGet, p, nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		print("MUX", p, "", rec)
	}

	// ── ② handler 层：鉴权 / query ─────────────────────────────────────
	handlerCases := []struct {
		path string
		auth string
	}{
		{"/internal/v1/favorite/facts/rtw.community.favorite/ev", ""},
		{"/internal/v1/favorite/facts/rtw.community.favorite/ev", "Bearer wrong-token"},
		{"/internal/v1/favorite/facts/rtw.community.favorite/ev", "Bearer " + token},
		{"/internal/v1/favorite/facts/rtw.community.favorite/ev?x=1", "Bearer " + token},
		{"/internal/v1/favorite/facts/rtw.community.favorite", ""},
	}
	for _, tc := range handlerCases {
		req := httptest.NewRequest(http.MethodGet, tc.path, nil)
		if tc.auth != "" {
			req.Header.Set("Authorization", tc.auth)
		}
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)
		print("HANDLER", tc.path, tc.auth, rec)
	}
}

func print(layer, path, auth string, rec *httptest.ResponseRecorder) {
	fmt.Printf("%s|path=%s|auth=%s|status=%d|location=%s|ct=%s|nosniff=%s|body=%q\n",
		layer, path, auth, rec.Code,
		rec.Header().Get("Location"),
		rec.Header().Get("Content-Type"),
		rec.Header().Get("X-Content-Type-Options"),
		rec.Body.String())
}
