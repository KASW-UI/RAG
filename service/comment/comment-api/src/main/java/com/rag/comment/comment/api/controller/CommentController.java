package com.rag.comment.comment.api.controller;

import com.rag.comment.comment.api.dto.CreateCommentReq;
import com.rag.comment.comment.api.dto.CreateCommentResp;
import com.rag.comment.comment.api.dto.DeleteCommentReq;
import com.rag.comment.comment.api.dto.DeleteCommentResp;
import com.rag.comment.comment.api.dto.GetCommentReq;
import com.rag.comment.comment.api.dto.GetCommentResp;
import com.rag.comment.comment.api.dto.LikeCommentReq;
import com.rag.comment.comment.api.dto.LikeCommentResp;
import com.rag.comment.comment.api.dto.ManageCommentReq;
import com.rag.comment.comment.api.dto.ManageCommentResp;
import com.rag.comment.comment.api.dto.ReportCommentReq;
import com.rag.comment.comment.api.dto.ReportCommentResp;
import com.rag.comment.comment.api.dto.UpdateSubjectStateReq;
import com.rag.comment.comment.api.dto.UpdateSubjectStateResp;
import com.rag.comment.comment.api.service.CreateCommentService;
import com.rag.comment.comment.api.service.DeleteCommentService;
import com.rag.comment.comment.api.service.GetCommentService;
import com.rag.comment.comment.api.service.LikeCommentService;
import com.rag.comment.comment.api.service.ManageCommentService;
import com.rag.comment.comment.api.service.ReportCommentService;
import com.rag.comment.comment.api.service.UpdateSubjectStateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * comment-api 的 7 个 HTTP 端点 —— 对应 Go
 * {@code service/comment/api/internal/handler/routes.go} 的路由表 + 7 个 handler。
 *
 * <p><b>路由逐条对齐（Go {@code routes.go:16-56}）</b>：
 * <pre>
 *   Go: rest.WithPrefix("/comment/v1") + 相对 path   →  Spring: 类级前缀 + 方法级相对 path
 *
 *   Go  POST /comment/create        →  Spring POST /comment/v1/comment/create
 *   Go  POST /comment/delete        →  Spring POST /comment/v1/comment/delete
 *   Go  POST /comment/like          →  Spring POST /comment/v1/comment/like
 *   Go  POST /comment/list          →  Spring POST /comment/v1/comment/list      ← 注意不是 /get
 *   Go  POST /comment/manage        →  Spring POST /comment/v1/comment/manage
 *   Go  POST /comment/report        →  Spring POST /comment/v1/comment/report
 *   Go  POST /subject/state         →  Spring POST /comment/v1/subject/state     ← 挂在 /subject 下
 * </pre>
 *
 * <p><b>⚠️ 两处最容易写错的路径</b>：
 * <ol>
 *   <li><b>{@code /comment/list} 不是 {@code /comment/get}</b> —— Go 的 handler 叫
 *       {@code GetCommentHandler}，但它挂的 path 是 {@code "/comment/list"}
 *       （{@code routes.go:33-37}）。按方法名推路径会得到 Go 从未注册过的地址。</li>
 *   <li><b>{@code /subject/state} 在 {@code /comment/v1} 下</b>，不在 {@code /subject/v1} 下
 *       —— 因为 prefix 是整个路由表共用的 {@code /comment/v1}，而不是按资源分组。</li>
 * </ol>
 *
 * <p><b>全部 7 个端点都是 POST + JSON body</b>（Go 的 {@code httpx.Parse} 对
 * {@code MethodPost} 走 {@code ParseJsonBody}）。
 *
 * <p><b>方法返回裸 DTO 而不是 {@code ResponseEntity}</b>：
 * 成功信封由 {@code CommentGlobalResponseAdvice} 统一包成
 * {@code {code:200, msg:"OK", data:...}}，对应 Go 的 {@code httpx.OkJsonCtx}。
 *
 * <p><b>本类不捕获任何异常</b>：所有错误出口由
 * {@code CommentGlobalExceptionHandler} 统一渲染，行为才与 Go 的
 * {@code SetErrorHandler} 一致（HTTP 200 + envelope）。
 */
@RestController
@RequestMapping("/comment/v1")
public class CommentController {

    private final CreateCommentService createCommentService;
    private final DeleteCommentService deleteCommentService;
    private final LikeCommentService likeCommentService;
    private final GetCommentService getCommentService;
    private final ManageCommentService manageCommentService;
    private final ReportCommentService reportCommentService;
    private final UpdateSubjectStateService updateSubjectStateService;

    public CommentController(CreateCommentService createCommentService,
                             DeleteCommentService deleteCommentService,
                             LikeCommentService likeCommentService,
                             GetCommentService getCommentService,
                             ManageCommentService manageCommentService,
                             ReportCommentService reportCommentService,
                             UpdateSubjectStateService updateSubjectStateService) {
        this.createCommentService = createCommentService;
        this.deleteCommentService = deleteCommentService;
        this.likeCommentService = likeCommentService;
        this.getCommentService = getCommentService;
        this.manageCommentService = manageCommentService;
        this.reportCommentService = reportCommentService;
        this.updateSubjectStateService = updateSubjectStateService;
    }

    /** 对应 Go {@code routes.go:18-22} → {@code CreateCommentHandler}。 */
    @PostMapping("/comment/create")
    public CreateCommentResp create(HttpServletRequest request,
                                    @RequestBody CreateCommentReq body) {
        body.assertGoRequiredFields();
        return createCommentService.create(request, body);
    }

    /** 对应 Go {@code routes.go:23-27} → {@code DeleteCommentHandler}。 */
    @PostMapping("/comment/delete")
    public DeleteCommentResp delete(HttpServletRequest request,
                                    @RequestBody DeleteCommentReq body) {
        body.assertGoRequiredFields();
        return deleteCommentService.delete(request, body);
    }

    /** 对应 Go {@code routes.go:28-32} → {@code LikeCommentHandler}。 */
    @PostMapping("/comment/like")
    public LikeCommentResp like(HttpServletRequest request,
                                @RequestBody LikeCommentReq body) {
        body.assertGoRequiredFields();
        return likeCommentService.like(request, body);
    }

    /**
     * 对应 Go {@code routes.go:33-37} → {@code GetCommentHandler}。
     *
     * <p>⚠️ path 是 {@code /comment/list} 而<b>不是</b> {@code /comment/get}。
     * 另注意本端点<b>不传 HttpServletRequest</b>：Go 侧是 7 个 logic 里唯一
     * 不解析身份的（没有 {@code extractUserID}），见 {@code GetCommentService} 差异 1。
     */
    @PostMapping("/comment/list")
    public GetCommentResp list(@RequestBody GetCommentReq body) {
        body.assertGoRequiredFields();
        return getCommentService.get(body);
    }

    /** 对应 Go {@code routes.go:38-42} → {@code ManageCommentHandler}。 */
    @PostMapping("/comment/manage")
    public ManageCommentResp manage(HttpServletRequest request,
                                    @RequestBody ManageCommentReq body) {
        body.assertGoRequiredFields();
        return manageCommentService.manage(request, body);
    }

    /** 对应 Go {@code routes.go:43-46} → {@code ReportCommentHandler}。 */
    @PostMapping("/comment/report")
    public ReportCommentResp report(HttpServletRequest request,
                                    @RequestBody ReportCommentReq body) {
        body.assertGoRequiredFields();
        return reportCommentService.report(request, body);
    }

    /** 对应 Go {@code routes.go:47-52} → {@code UpdateSubjectStateHandler}。 */
    @PostMapping("/subject/state")
    public UpdateSubjectStateResp updateSubjectState(HttpServletRequest request,
                                                    @RequestBody UpdateSubjectStateReq body) {
        body.assertGoRequiredFields();
        return updateSubjectStateService.updateState(request, body);
    }
}