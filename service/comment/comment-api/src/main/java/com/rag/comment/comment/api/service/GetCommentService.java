package com.rag.comment.comment.api.service;

import com.rag.comment.comment.api.client.CommentRpcClient;
import com.rag.comment.comment.api.dto.CommentItem;
import com.rag.comment.comment.api.dto.GetCommentReq;
import com.rag.comment.comment.api.dto.GetCommentResp;
import com.rag.comment.comment.api.dto.SubjectInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 拉评论列表 —— 对应 Go {@code api/internal/logic/comment/getcommentlogic.go}。
 *
 * <p><b>⚠️ 本类是 7 个 logic 里唯一一个行为形状不同的，必须逐条照 Go 走</b>，
 * 不能照其余 6 个的模板"顺手统一"。四处差异：
 *
 * <h3>差异 1：不解析身份</h3>
 * Go 全篇<b>没有</b> {@code extractUserID}，proto 请求里也没有 {@code user_id}。
 * ⇒ 本类<b>不</b>调 {@code support.userId(request)}。
 * 这意味着：token 里缺 {@code userId} claim 时，本端点<b>不会</b>返回 1006，
 * 而其余 6 个端点会。这是 Go 侧既有的不对称，原样保留。
 *
 * <h3>差异 2：入参校验抛的是<b>裸 gRPC status error</b></h3>
 * <pre>
 * if strings.TrimSpace(req.TargetType) == "" {
 *     return nil, status.Error(codes.InvalidArgument, "target_type is required")
 * }
 * </pre>
 * 该 error <b>不是</b> {@code *errmsg.CodeError} ⇒ Go 的
 * {@code SetErrorHandler} 里 {@code errors.As} 判否 ⇒ 落进兜底分支
 * ⇒ 客户端实际收到的是 <b>HTTP 200 + {code:5001, msg:"系统内部错误"}</b>，
 * <b>而不是</b> {@code "target_type is required"}，也不是 3（InvalidArgument）。
 * <p>⇒ Java 侧<b>不能</b>把它转成 {@code BusinessException(3, "target_type is required")}：
 * 那会让客户端看到一个 Go 从未输出过的码与文案。
 * 这里原样抛 {@code StatusRuntimeException}，由
 * {@code CommentGlobalExceptionHandler#handleGrpcStatus} 兜底成 5001 —— 与 Go 同路径。
 *
 * <h3>差异 3：RPC 失败<b>不</b>做 code/msg 转换</h3>
 * Go {@code getcommentlogic.go:72-75} 是<b>裸 {@code return nil, err}</b>，
 * 没有 {@code status.FromError} + {@code NewErrCodeMsg} 那三行。
 * ⇒ 下游返回 {@code NotFound} 时，本端点给客户端的是 <b>5001</b>，
 * 而其余 6 个端点会给 <b>5</b>。两个端点的错误契约就是不一样，
 * 照抄「统一模板」会破坏它。
 *
 * <h3>差异 4：五处入参兜底</h3>
 * <pre>
 * if req.SortType != 0 && req.SortType != 1 { req.SortType = 0 }   // 只认 0=热度 / 1=时间
 * if req.Page     <= 0 { req.Page     = 1 }
 * if req.PageSize <= 0 { req.PageSize = 20 }
 * if req.PageSize > 50 { req.PageSize = 50 }
 * if req.RootId   <  0 { req.RootId   = 0 }                        // 负数无意义 ⇒ 查根评论
 * </pre>
 * 这是<b>业务层</b>兜底，发生在解析层（"字段必填"）之后，两道关卡互不替代。
 *
 * <p><b>⚠️ 校验只判空、<b>不</b>回写 trim 结果</b>：
 * Go 只用 {@code TrimSpace(...)} 做判空，<b>没有</b> {@code req.TargetType = ...}，
 * 所以带空格的 target_type 会<b>原样</b>发给 rpc。若 Java 侧顺手 {@code strip()} 后回写，
 * 下游收到的值就变了 —— 可能命中与原值不同的评论区。
 */
@Service
public class GetCommentService {

    /** Go {@code getcommentlogic.go:51}：{@code if req.PageSize <= 0 { req.PageSize = 20 }}。 */
    private static final long DEFAULT_PAGE_SIZE = 20L;
    /** Go {@code getcommentlogic.go:53}：{@code if req.PageSize > 50 { req.PageSize = 50 }}。 */
    private static final long MAX_PAGE_SIZE = 50L;

    private final CommentRpcClient commentRpcClient;

    public GetCommentService(CommentRpcClient commentRpcClient) {
        this.commentRpcClient = commentRpcClient;
    }

    public GetCommentResp get(GetCommentReq body) {
        // ── 差异 2：入参校验，裸 gRPC status error ──
        // ⚠️ 只判空不回写：Go 没有 trim 后的赋值。
        if (isBlank(body.getTargetType())) {
            throw io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("target_type is required")
                    .asRuntimeException();
        }
        if (isBlank(body.getTargetId())) {
            throw io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("target_id is required")
                    .asRuntimeException();
        }

        // ── 差异 4：五处入参兜底 ──
        long sortType = body.getSortType() == 0L || body.getSortType() == 1L ? body.getSortType() : 0L;
        long page = body.getPage() <= 0L ? 1L : body.getPage();
        long pageSize = body.getPageSize() <= 0L
                ? DEFAULT_PAGE_SIZE
                : (body.getPageSize() > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : body.getPageSize());
        long rootId = body.getRootId() < 0L ? 0L : body.getRootId();

        // ── 差异 3：失败不转换，裸抛 ──
        com.rag.comment.comment.proto.GetCommentResp rpcResp = commentRpcClient.commentRpc().getComment(
                com.rag.comment.comment.proto.GetCommentReq.newBuilder()
                        .setTargetType(body.getTargetType())
                        .setTargetId(body.getTargetId())
                        .setSortType(sortType)
                        .setRootId(rootId)
                        .setPage(page)
                        .setPageSize(pageSize)
                        .build());

        GetCommentResp resp = new GetCommentResp();
        // Go: resp = &types.GetCommentResp{ Comment: make([]types.CommentItem, 0) }
        //     ⇒ comment 恒为 []，即使下游一条评论都没返回
        List<CommentItem> items = new ArrayList<>();
        for (com.rag.comment.comment.proto.CommentItem item : rpcResp.getCommentList()) {
            items.add(toCommentItem(item));
        }
        resp.setComment(items);

        // Go: 只有 rpcResp.GetSubject() != nil 才赋值；SubjectInfo 是**值类型**，
        //     所以没返回时输出的是一个全零对象而不是 null / 省略。
        //     proto3 的 message 字段无「null」形态 ⇒ 用 hasSubject() 表达 Go 的判空。
        SubjectInfo subject = new SubjectInfo();
        if (rpcResp.hasSubject()) {
            subject.setTargetType(rpcResp.getSubject().getTargetType());
            subject.setTargetId(rpcResp.getSubject().getTargetId());
            subject.setTotalCount(rpcResp.getSubject().getTotalCount());
            subject.setRootCount(rpcResp.getSubject().getRootCount());
            subject.setState(rpcResp.getSubject().getStateValue());
            subject.setAttribute(rpcResp.getSubject().getAttribute());
            // ⚠️⚠️ 恒为 0：Go getcommentlogic.go:91 写的是字面量 OwnerId: 0，
            //         **没有** 读 rpcResp.GetSubject().GetOwnerId()。
            //         proto 里明明有 int64 owner_id = 7，Go 却丢弃了它。
            //         这是客户端可观察的行为（拿到的 owner_id 一直是 0），
            //         按 AGENTS.md §15 不擅自修复，原样保留。
            subject.setOwnerId(0L);
        }
        resp.setSubject(subject);

        return resp;
    }

    /**
     * 复刻 Go {@code convertPbCommentItemToAPI}（{@code getcommentlogic.go:106-135}）。
     *
     * <p><b>⚠️ state 用 {@code getStateValue()} 取数值</b>：Go 写的是
     * {@code State: int32(in.GetState())} —— 把 proto 枚举<b>降成数值</b>。
     * 用 {@code getState()} 再 {@code getNumber()} 结果等价，
     * 但 {@code *Value()} 直接就是 wire 上那个 int，且对未识别枚举值不会 NPE。
     *
     * <p><b>⚠️ children 递归且每层都先建空切片</b>（Go {@code Children: make(..., 0)}），
     * 所以任意深度的叶子节点都输出 {@code "children":[]}，绝不是 {@code null}。
     */
    private static CommentItem toCommentItem(com.rag.comment.comment.proto.CommentItem in) {
        CommentItem out = new CommentItem();
        out.setId(in.getId());
        out.setUserId(in.getUserId());
        out.setContent(in.getContent());
        out.setRootId(in.getRootId());
        out.setParentId(in.getParentId());
        out.setLikeCount(in.getLikeCount());
        out.setDislikeCount(in.getDislikeCount());
        out.setReplyCount(in.getReplyCount());
        out.setAttribute(in.getAttribute());
        out.setState(in.getStateValue());
        out.setCreatedAt(in.getCreatedAt());
        out.setMeta(in.getMeta());

        List<CommentItem> children = new ArrayList<>();
        for (com.rag.comment.comment.proto.CommentItem child : in.getChildrenList()) {
            children.add(toCommentItem(child));
        }
        out.setChildren(children);
        return out;
    }

    /**
     * Go {@code strings.TrimSpace(s) == ""} 的等价判定。
     *
     * <p>用 {@link String#isBlank()}（底层 {@code strip()}，按 Unicode 空白判定），
     * 与 Go 的 {@code unicode.IsSpace} 覆盖面一致。
     */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}