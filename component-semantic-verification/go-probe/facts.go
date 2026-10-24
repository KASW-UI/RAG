package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"

	"github.com/golang-jwt/jwt/v4"
	"github.com/zeromicro/go-zero/core/mapping"
	"github.com/zeromicro/go-zero/rest/httpx"
	"github.com/zeromicro/go-zero/rest/token"
)

type createReqProbe struct {
	Title   string   `json:"title"`
	Brief   string   `json:"brief,optional"`
	Content string   `json:"content"`
	Status  int32    `json:"status,optional"`
	Tags    []string `json:"tags,optional"`
}

func parseCase(label, ct string, cl int64, body string) {
	var req createReqProbe
	r := httptest.NewRequest(http.MethodPost, "/v1/article", strings.NewReader(body))
	if ct != "" {
		r.Header.Set("Content-Type", ct)
	}
	r.ContentLength = cl
	err := httpx.Parse(r, &req)
	fmt.Printf("%-44s -> err=%v req=%+v\n", label, err, req)
}

func jwtCase(label, authHeader string) {
	secret := "ae0536f9-6450-4606-8e13-5a19ed505da0"
	p := token.NewTokenParser()
	r := httptest.NewRequest(http.MethodGet, "/v1/me/article/1", http.NoBody)
	if authHeader != "" {
		r.Header.Set("Authorization", authHeader)
	}
	real := signTok(secret, `{"userId":1001,"exp":4102444800}`)
	h := authHeader
	switch {
	case strings.HasPrefix(h, "Bearer "):
		h = "Bearer " + real
	case strings.HasPrefix(h, "bearer "):
		h = "bearer " + real
	case h == "abc.def.ghi":
		h = real
	case h == "Basic abc":
		h = "Basic " + real
	case h == "Bearer":
		h = "Bearer"
	default:
		h = real
	}
	r.Header.Set("Authorization", h)
	tok, err := p.ParseToken(r, secret, "")
	if err != nil {
		fmt.Printf("%-44s -> REJECTED err=%v\n", label, err)
		return
	}
	claims, _ := tok.Claims.(jwt.MapClaims)
	fmt.Printf("%-44s -> ACCEPTED userId=%v (type %T)\n", label, claims["userId"], claims["userId"])
}

func signTok(secret, claims string) string {
	tok := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"userId": json.Number("1001"),
		"exp":    json.Number("4102444800"),
	})
	signed, err := tok.SignedString([]byte(secret))
	if err != nil {
		return "bad"
	}
	return signed
}

func uploadCase(label string, ct string, body string) {
	r := httptest.NewRequest(http.MethodPost, "/v1/upload", strings.NewReader(body))
	if ct != "" {
		r.Header.Set("Content-Type", ct)
	}
	_, _, err := r.FormFile("image")
	fmt.Printf("%-44s -> err=%v\n", label, err)
}

func jsonLimitCase(label string, n int) {
	pad := strings.Repeat("a", n)
	body := `{"title":"t","content":"x","brief":"` + pad + `"}`
	parseCase(label, "application/json", int64(len(body)), body)
}

func miscFacts() {
	for _, c := range []struct {
		label string
		m     map[string]any
	}{
		{`{"status":"2"}`, map[string]any{"status": "2"}},
		{`{"status":null}`, map[string]any{"status": nil}},
		{`{"brief":null}`, map[string]any{"brief": nil}},
		{`{"title":123}`, map[string]any{"title": 123}},
		{`{}`, map[string]any{}},
	} {
		var m createReqProbe
		err := mapping.UnmarshalJsonMap(c.m, &m)
		fmt.Printf("%-44s -> err=%v req=%+v\n", "mapping "+c.label, err, m)
	}
}
