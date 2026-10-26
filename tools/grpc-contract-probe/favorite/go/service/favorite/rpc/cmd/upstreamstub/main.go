// ============================================================================
// upstreamstub —— favorite 域 gRPC 契约探针自带的**最小上游桩**
// ----------------------------------------------------------------------------
// 🔴 **它不是 user-rpc / article-rpc 的实现，也不代表这两个域的行为。**
//
// 它只做一件事：按**请求里的 uid / article_id** 查一张**写死的表**，返回对应的响应，
// 好让 favorite 域的
//   ① 「create 成功路径」（落库 / 雪花 ID / favorite_fact_outbox 联动）
//   ② ensureUserExists 的**五条分支**（Go helper.go:99-120）
// 能在**两侧同时**被跑到。
//
// ── 为什么是「两侧共同上游」而不是「两侧各自的桩」───────────────────────────
// 两侧必须打**同一个**上游：只有这样，两侧收到的字节才相同，
// 差出来的才是「favorite 域自己的行为差异」，而不是「两个桩的差异」。
//
// ── 桩的边界（必须先说清）───────────────────────────────────────────────────
//   · 期望值**不能**从「真 user-rpc」取，只能由「Go 真源 favorite-rpc + 真 PG」产出；
//   · 桩返回的 `published_revision_id` 用了 `<id>:r<N>` 这个**favorite 侧写死的约定**
//     （Go helper.go:159-166 / RAG FavoriteArticleSnapshotResolver:166-207），
//     所以本探针**证明**的是「favorite 在一条**合规**的上游响应下的处理两侧一致」，
//     **不证明**「article 域产出这个约定的行为两侧一致」。
//
// ── 为什么用 Go 写 ───────────────────────────────────────────────────────────
// 探针的 Go module 里已经有**真源逐字节复制件**
// （service/user/user/rpc/pb、service/article/rpc/pb），
// 用它们当桩的 wire 契约 ⇒ 桩发的字节就是真 proto 的字节。
// ⚠️ 本文件**不是**复制件（是探针代码），所以**不在** go/filelist.txt 的 sha256 清单里
//    —— 与 service/favorite/rpc/cmd/schemaprep/main.go 同款。
// ============================================================================
package main

import (
	"context"
	"flag"
	"fmt"
	"net"
	"os"

	articlepb "sea-try-go/service/article/rpc/pb"
	userpb "sea-try-go/service/user/user/rpc/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// ── uid 约定（两侧探针用例共用同一张表）──────────────────────────────────
const (
	// uidActive：正常用户 —— status=0 且**字段存在**（presence）⇒ 走成功路径。
	uidActive int64 = 2001
	// uidNotFoundResp：RPC 正常返回但 found=false ⇒ Go helper.go:109-112 ⇒ NotFound/5101。
	uidNotFoundResp int64 = 2002
	// uidStatusAbsent：User 存在但 **status 字段缺失**（wire 上没有该字段）
	// ⇒ Go helper.go:113-116（Status == nil）⇒ Unavailable/5001。
	// 🔴 这条专门盯 **presence** 语义：Java 侧若用 getStatus()!=0 而不是 hasStatus()，
	//    会得到 PermissionDenied/5005 ⇒ 方向相反的漂移。
	uidStatusAbsent int64 = 2003
	// uidForbidden：status=1（非 active）⇒ Go helper.go:117-120 ⇒ PermissionDenied/5005。
	uidForbidden int64 = 2004
	// uidMismatch：User 存在但 **uid 与请求不符** ⇒ Go helper.go:113-116 ⇒ Unavailable/5001。
	uidMismatch int64 = 2005
	// uidNotFoundRpc：RPC 直接返回 NotFound ⇒ Go helper.go:103-106 ⇒ NotFound/5101。
	uidNotFoundRpc int64 = 2006
	// uidInternalRpc：RPC 返回 Internal ⇒ Go helper.go:107 ⇒ Internal/5001。
	uidInternalRpc int64 = 2007
)

// uidMismatchValue：uidMismatch 这条桩**故意**返回的「另一个用户」的 uid。
const uidMismatchValue int64 = 999999

// ── article 约定 ───────────────────────────────────────────────────────────
const (
	// articleTitle：桩返回的文章标题（两侧冻结出来的 title 必须都是它）。
	articleTitle = "probe-article-title"
	// articleCover：走 `cover_image_url` 这条一级回退。
	articleCover = "probe-article-cover"
	// articleExtCoverId：这个 article_id **不返回** cover_image_url，
	// 只在 ext_info["cover"] 里给封面 ⇒ 逼出 `articleCover` 的二级回退
	// （Go helper.go:186-196 / RAG FavoriteArticleSnapshotResolver:240-259）。
	articleExtCoverId = "art-2"
	// articleExtCover：ext_info 里的封面值。
	articleExtCover = "probe-article-cover-from-ext"
	// articleRevisionNumber：`<id>:r<N>` 的 N（N≠0 是 Go helper.go:162 的硬要求）。
	articleRevisionNumber = "7"
)

// ── user-rpc 桩 ────────────────────────────────────────────────────────────

type userStub struct {
	userpb.UnimplementedUserServiceServer
}

// GetUser 只按 uid 查表 —— **不含任何 favorite 域的语义**。
func (s *userStub) GetUser(ctx context.Context, in *userpb.GetUserReq) (*userpb.GetUserResp, error) {
	switch in.GetUid() {
	case uidActive:
		// 🔴 Status 显式置 0（proto3 optional ⇒ 字段**存在**且值为 0）。
		//    这正是 favorite 侧 presence 判据要区分的「存在且为 0」与「不存在」。
		return &userpb.GetUserResp{
			Found: true,
			User:  &userpb.UserInfo{Uid: uidActive, Status: proto.Int64(0)},
		}, nil
	case uidNotFoundResp:
		return &userpb.GetUserResp{Found: false}, nil
	case uidStatusAbsent:
		// 🔴 Status **不设** ⇒ 生成的 Go 结构体里是 nil ⇒ wire 上没有这个字段。
		return &userpb.GetUserResp{
			Found: true,
			User:  &userpb.UserInfo{Uid: uidStatusAbsent},
		}, nil
	case uidForbidden:
		return &userpb.GetUserResp{
			Found: true,
			User:  &userpb.UserInfo{Uid: uidForbidden, Status: proto.Int64(1)},
		}, nil
	case uidMismatch:
		return &userpb.GetUserResp{
			Found: true,
			User:  &userpb.UserInfo{Uid: uidMismatchValue, Status: proto.Int64(0)},
		}, nil
	case uidNotFoundRpc:
		return nil, status.Errorf(codes.NotFound, "probe stub: user %d not found", in.GetUid())
	case uidInternalRpc:
		return nil, status.Errorf(codes.Internal, "probe stub: user %d internal failure", in.GetUid())
	default:
		return nil, status.Errorf(codes.Unavailable, "probe stub: no rule for uid %d", in.GetUid())
	}
}

// ── article-rpc 桩 ────────────────────────────────────────────────────────

type articleStub struct {
	articlepb.UnimplementedArticleServiceServer
}

// GetArticle 只按 article_id 回显 —— **不含任何 favorite 域的语义**。
func (s *articleStub) GetArticle(ctx context.Context, in *articlepb.GetArticleRequest) (*articlepb.GetArticleResponse, error) {
	id := in.GetArticleId()
	article := &articlepb.Article{
		Id:     id,
		Title:  articleTitle,
		Status: articlepb.ArticleStatus_PUBLISHED,
		ExtInfo: map[string]string{
			// favorite 侧写死的修订号约定（Go helper.go:159-166）。
			"published_revision_id": id + ":r" + articleRevisionNumber,
		},
	}
	if id == articleExtCoverId {
		// 刻意**不设** CoverImageUrl ⇒ 逼出 ext_info 回退。
		article.ExtInfo["cover"] = articleExtCover
	} else {
		article.CoverImageUrl = articleCover
	}
	return &articlepb.GetArticleResponse{Article: article}, nil
}

// ── main ──────────────────────────────────────────────────────────────────

func main() {
	addr := flag.String("addr", "127.0.0.1:0", "监听地址")
	flag.Parse()

	lis, err := net.Listen("tcp", *addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "upstreamstub: listen failed: %v\n", err)
		os.Exit(1)
	}

	// 🔴 用**裸** grpc.NewServer（不走 go-zero 的 zrpc）：
	//    桩只负责「按契约应答」，不引入任何框架层行为（拦截器 / metrics / 熔断），
	//    否则测出来的差异可能是桩的框架差异，而不是 favorite 的差异。
	srv := grpc.NewServer()
	userpb.RegisterUserServiceServer(srv, &userStub{})
	articlepb.RegisterArticleServiceServer(srv, &articleStub{})

	fmt.Printf("upstream-stub listening on %s\n", lis.Addr().String())
	if err := srv.Serve(lis); err != nil {
		fmt.Fprintf(os.Stderr, "upstreamstub: serve failed: %v\n", err)
		os.Exit(1)
	}
}
