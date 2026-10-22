package main

import (
	"context"
	"os"
	"io"
	"strconv"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/rest"
)

const yamlBody = `
Name: article-api
Host: 0.0.0.0
Port: 18899
Timeout: 70000
MaxBytes: 10485760
Auth:
  AccessSecret: ae0536f9-6450-4606-8e13-5a19ed505da0
  AccessExpire: 31536000
`

const SLASH = "/"

type cfg struct {
	rest.RestConf
	Auth struct {
		AccessSecret string
		AccessExpire int64
	}
}

func newServer(withCors bool) *rest.Server {
	var c cfg
	if err := conf.LoadFromYamlBytes([]byte(yamlBody), &c); err != nil {
		panic(err)
	}
	c.Middlewares.Timeout = false
	httpTimeout := time.Duration(c.Timeout) * time.Millisecond

	var opts []rest.RunOption
	if withCors {
		opts = append(opts, rest.WithCors())
	}
	svr := rest.MustNewServer(c.RestConf, opts...)

	svr.Use(func(next http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			if httpTimeout <= 0 {
				next(w, r)
				return
			}
			ctx, cancel := context.WithTimeout(r.Context(), httpTimeout)
			defer cancel()
			req := r.WithContext(ctx)
			done := make(chan struct{})
			rec := newBuf()
			go func() { next(rec, req); close(done) }()
			select {
			case <-done:
				rec.flushTo(w)
			case <-ctx.Done():
				rec.timedOut = true
				http.Error(w, "Request Timeout", http.StatusServiceUnavailable)
			}
		}
	})

	svr.AddRoutes([]rest.Route{
		{Method: http.MethodPost, Path: "/article", Handler: jsonHandler()},
		{Method: http.MethodPut, Path: "/article/:id", Handler: jsonHandler()},
		{Method: http.MethodDelete, Path: "/article/:id", Handler: jsonHandler()},
		{Method: http.MethodGet, Path: "/me/article/:id", Handler: jsonHandler()},
	}, rest.WithJwt(c.Auth.AccessSecret), rest.WithPrefix("/v1"), rest.WithTimeout(70*time.Second))

	svr.AddRoutes([]rest.Route{
		{Method: http.MethodGet, Path: "/article/:id", Handler: jsonHandler()},
		{Method: http.MethodGet, Path: "/articles", Handler: jsonHandler()},
	}, rest.WithPrefix("/v1"))

	svr.AddRoutes([]rest.Route{
		{Method: http.MethodPost, Path: "/upload", Handler: jsonHandler()},
	}, rest.WithPrefix("/v1"), rest.WithMaxBytes(10*1024*1024))

	return svr
}

func jsonHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"code":200,"msg":"OK","data":null}`)
	}
}

type buf struct {
	keys     []string
	vals     map[string][]string
	code     int
	body     strings.Builder
	timedOut bool
}

func newBuf() *buf {
	return &buf{vals: map[string][]string{}, code: http.StatusOK}
}

func (p *buf) Header() http.Header { return p.hdrMap() }
func (p *buf) hdrMap() http.Header  { return http.Header(p.vals) }
func (p *buf) Write(b []byte) (int, error) {
	if p.timedOut {
		return 0, http.ErrHandlerTimeout
	}
	return p.body.Write(b)
}
func (p *buf) WriteHeader(c int) { p.code = c }
func (p *buf) flushTo(dst http.ResponseWriter) {
	for k, vv := range p.vals {
		for _, v := range vv {
			dst.Header().Add(k, v)
		}
	}
	if p.code != http.StatusOK {
		dst.WriteHeader(p.code)
	}
	if p.body.Len() > 0 {
		fmt.Fprint(dst, p.body.String())
	}
}

type probeCase struct {
	name, method, path string
	hdr                map[string]string
	body               string
}

type zeroReader struct{}

func (zeroReader) Read(p []byte) (int, error) {
	for i := range p {
		p[i] = 32
	}
	return len(p), nil
}

func hit(base, name, method, path string, hdr map[string]string, body string) {
	req, err := http.NewRequest(method, base+path, strings.NewReader(body))
	if err != nil {
		fmt.Println("req err", err)
		return
	}
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range hdr {
		if k == "__CL" {
			continue
		}
		req.Header.Set(k, v)
	}
	if v, ok := hdr["__CL"]; ok {
		n, _ := strconv.ParseInt(v, 10, 64)
		req.ContentLength = n
		req.Body = io.NopCloser(io.LimitReader(zeroReader{}, n))
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		fmt.Printf("%-42s -> ERR %v\n", name, err)
		return
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(resp.Body)
	fmt.Printf("%-42s -> %d ct=%q nosniff=%q allow=%q acao=%q body=%q\n",
		name, resp.StatusCode,
		resp.Header.Get("Content-Type"),
		resp.Header.Get("X-Content-Type-Options"),
		resp.Header.Get("Allow"),
		resp.Header.Get("Access-Control-Allow-Origin"),
		string(rb))
}

func main() {
	base := "http://127.0.0.1:18899"
	type hc struct {
		name, method, path string
		hdr                map[string]string
		body               string
	}
	cs := []hc{
		{"registered GET public", "GET", "/v1/article/abc", nil, ""},
		{"registered GET list", "GET", "/v1/articles?page=1", nil, ""},
		{"MISMATCH POST /v1/article/1", "POST", "/v1/article/1", nil, ""},
		{"MISMATCH PATCH /v1/articles", "PATCH", "/v1/articles", nil, ""},
		{"MISMATCH DELETE /v1/articles", "DELETE", "/v1/articles", nil, ""},
		{"MISMATCH POST /v1/me/article/1", "POST", "/v1/me/article/1", nil, ""},
		{"UNREGISTERED /v1/nope", "GET", "/v1/nope", nil, ""},
		{"UNREGISTERED /nope", "GET", "/nope", nil, ""},
		{"UNREGISTERED /v1/article/1/extra", "GET", "/v1/article/1/extra", nil, ""},
		{"JWT no token POST /v1/article", "POST", "/v1/article", nil, ""},
		{"JWT no token GET me", "GET", "/v1/me/article/1", nil, ""},
		{"OPTIONS preflight PUT", "OPTIONS", "/v1/article/1",
			map[string]string{"Origin": "http://x", "Access-Control-Request-Method": "PUT"}, ""},
		{"OPTIONS unregistered", "OPTIONS", "/v1/nope", map[string]string{"Origin": "http://x"}, ""},
		{"double slash path", "GET", SLASH + SLASH + "v1/articles", nil, ""},
		{"trailing slash", "GET", "/v1/articles/", nil, ""},
		{"413 protected route (20MB CL)", "POST", "/v1/article",
			map[string]string{"__CL": "20971520"}, ""},
		{"413 public route (20MB CL)", "GET", "/v1/article/1",
			map[string]string{"__CL": "20971520"}, ""},
		{"413 upload route (20MB CL)", "POST", "/v1/upload",
			map[string]string{"__CL": "20971520"}, ""},
		{"MISMATCH GET /v1/upload", "GET", "/v1/upload", nil, ""},
		{"9MB JSON body valid token-less", "POST", "/v1/article",
			map[string]string{"Content-Type": "application/json"}, strings.Repeat(" ", 9*1024*1024)},
		{"trailing slash on protected", "PUT", "/v1/article/1/", nil, ""},
		{"query only empty value", "GET", "/v1/articles?page=&sort_by=", nil, ""},
	}
	fmt.Println("########## httpx.Parse / token / FormFile facts ##########")
	parseCase("full JSON ok", "application/json", 20, `{"title":"t","content":"x"}`)
	parseCase("missing title", "application/json", 20, `{"content":"x"}`)
	parseCase("title null", "application/json", 24, `{"title":null,"content":"x"}`)
	parseCase("title=123", "application/json", 25, `{"title":123,"content":"x"}`)
	parseCase("brief null (optional)", "application/json", 34, `{"title":"t","content":"x","brief":null}`)
	parseCase("CT=text/plain valid JSON", "text/plain", 26, `{"title":"t","content":"x"}`)
	parseCase("no Content-Type", "", 26, `{"title":"t","content":"x"}`)
	parseCase("CL=0 empty body", "application/json", 0, ``)
	parseCase(`status="2" +title+content`, "application/json", 40, `{"title":"t","content":"x","status":"2"}`)
	parseCase(`status=null +title+content`, "application/json", 42, `{"title":"t","content":"x","status":null}`)
	parseCase(`status=2 numeric`, "application/json", 38, `{"title":"t","content":"x","status":2}`)
	parseCase(`tags=["a"]`, "application/json", 34, `{"title":"t","content":"x","tags":["a"]}`)
	parseCase(`tags="a" (wrong type)`, "application/json", 32, `{"title":"t","content":"x","tags":"a"}`)
	parseCase(`unknown field zzz`, "application/json", 40, `{"title":"t","content":"x","zzz":1}`)
	parseCase(`title="" empty string`, "application/json", 35, `{"title":"","content":"x"}`)
	jsonLimitCase("7MiB JSON body", 7*1024*1024)
	jsonLimitCase("9MiB JSON body", 9*1024*1024)
	jsonLimitCase("11MiB JSON body", 11*1024*1024)
	fmt.Println()
	jwtCase("Bearer <tok>", "Bearer abc.def.ghi")
	jwtCase("raw <tok> no Bearer prefix", "abc.def.ghi")
	jwtCase("lowercase bearer <tok>", "bearer abc.def.ghi")
	jwtCase("Basic abc", "Basic abc")
	jwtCase("Bearer alone", "Bearer")
	fmt.Println()
	uploadCase("upload CT=application/json {}", "application/json", `{}`)
	uploadCase("upload multipart no image part", "multipart/form-data; boundary=xx",
		"--xx\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\nxx\r\n--xx--\r\n")
	fmt.Println()
	miscFacts()
	os.Exit(0)

	svr := newServer(true)
	go svr.Start()
	time.Sleep(1200 * time.Millisecond)
	for _, c := range cs {
		hit(base, c.name, c.method, c.path, c.hdr, c.body)
	}
	fmt.Println("DONE")
}
