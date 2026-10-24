package com.rag.article.article.api.service;

import com.rag.article.article.api.client.UserRpcClient;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.common.logger.WithUserID;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

/**
 * 作者昵称解析 —— 对应 Go {@code logic/article/helper.go:71-103} 的
 * {@code resolveAuthorName} 与 {@code fallbackAuthorName}。
 *
 * <h2>本组件唯一「失败也不冒泡」的能力</h2>
 *
 * <pre>
 * Go:
 *   func resolveAuthorName(ctx context.Context, svcCtx *svc.ServiceContext, authorID string) string {
 *       authorID = strings.TrimSpace(authorID)
 *       if authorID == "" { return fallbackAuthorName("") }
 *
 *       uid, err := strconv.ParseInt(authorID, 10, 64)
 *       if err != nil || uid &lt;= 0 { return fallbackAuthorName(authorID) }        // ① 非数字 / 非正数
 *
 *       resp, rpcErr := svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: uid})
 *       if rpcErr != nil {                                                          // ② RPC 失败
 *           logger.LogBusinessErr(ctx, errmsg.Error, rpcErr, logger.WithUserID(authorID))
 *           return fallbackAuthorName(authorID)
 *       }
 *       if resp == nil || !resp.Found || resp.User == nil {                         // ③ 没找到
 *           return fallbackAuthorName(authorID)
 *       }
 *       username := strings.TrimSpace(resp.User.Username)
 *       if username == "" { return fallbackAuthorName(authorID) }                   // ④ 用户名为空
 *       return username
 *   }
 * </pre>
 *
 * ⇒ <b>四条失败路径全部返回同一个 {@code fallbackAuthorName}，没有一个会变成 HTTP 错误。</b>
 * 这是一个刻意的设计：文章正文能不能读到，<b>不</b>取决于昵称服务是否可用。
 * 把它写成「RPC 失败就抛异常」是<b>新增行为</b>，而且是最坏的那种 ——
 * user-rpc 抖一下，整个文章列表页就 500 了。
 *
 * <h2>⚠️ 分成独立一个 Bean 的理由</h2>
 * <p>Go 把这几个函数放在 {@code helper.go} 的自由函数里，通过 {@code svcCtx} 拿客户端。
 * Java 侧若照抄成静态方法，就必须到处传 {@code UserRpcClient}；
 * 做成 Bean 后由构造器注入一次，{@code ArticleApiSupport#enrichArticleAuthor} 只需要
 * 一个参数。⇒ 结构由 Java 的依赖注入决定，行为逐条不变。
 */
@Component
public class ArticleAuthorResolver {

    private final UserRpcClient userRpcClient;
    private final LoggerFactoryBean logger;

    /**
     * ⚠️ 注入 {@link LoggerFactoryBean} 而<b>不是</b>静态 {@code Logger.logBusinessErr}：
     * 静态入口走 {@code Logger.globalLogger()}，而它<b>要求</b>有人先调过 {@code Logger.init(serviceName)}。
     * 实测全仓<b>只有</b> {@code service/common} 的单元测试调过它 ——
     * 生产路径里 {@code LoggerFactoryBean} 只 {@code new Logger(serviceName)} 持有<b>自己</b>的实例
     * （其 javadoc 明说「不共享 serviceName 状态」），<b>不会</b>初始化那个全局单例。
     *
     * <p>后果不是「日志丢一条」，而是 <b>整条请求 500</b>：
     * {@code globalLogger()} 抛 {@code IllegalStateException: logger not initialized}，
     * 而这行代码位于「GetUser 失败 → 回落昵称」这条<b>永不冒泡</b>的路径上，
     * 于是 user-rpc 抖动会把整个文章列表打成 500 —— 与 Go 的语义正好相反。
     * （第一次实现就是踩了这个坑，由
     * {@code ArticleApiMvcPipelineTest#authorNameFallbackOnRpcFailure} 抓到。）
     */
    public ArticleAuthorResolver(UserRpcClient userRpcClient, LoggerFactoryBean logger) {
        this.userRpcClient = userRpcClient;
        this.logger = logger;
    }

    /**
     * 返回「可展示的作者名」，<b>永不抛异常</b>。
     *
     * @param authorId 文章里的 {@code author_id}（字符串，可能为 null / 空 / 非数字）
     */
    public String resolveAuthorName(String authorId) {
        String trimmed = authorId == null ? null : authorId.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return ArticleApiSupport.fallbackAuthorName("");
        }

        // Go: strconv.ParseInt(authorID, 10, 64)；err != nil || uid <= 0 → fallback
        // ⚠️ 「<= 0」这条不能只靠「能否解析」覆盖：authorId = "0" / "-5" 都能解析成功。
        long uid;
        try {
            uid = Long.parseLong(trimmed, 10);
        } catch (NumberFormatException e) {
            return ArticleApiSupport.fallbackAuthorName(trimmed);
        }
        if (uid <= 0L) {
            return ArticleApiSupport.fallbackAuthorName(trimmed);
        }

        GetUserResp resp;
        try {
            resp = userRpcClient.userRpc().getUser(GetUserReq.newBuilder().setUid(uid).build());
        } catch (StatusRuntimeException e) {
            // Go 分支 ②：记业务日志 + 回落，**不**冒泡
            // ⚠️ 日志调用本身也必须包 try/catch：Go 的 logger 不会让请求失败，
            //    而 Java 侧任何一次日志异常都不该把「回落昵称」变成 500。
            try {
                logger.logBusinessErr(io.opentelemetry.context.Context.current(),
                        ErrorCode.ERROR.getCode(), e, WithUserID.of(trimmed));
            } catch (RuntimeException loggingFailure) {
                // 刻意吞掉：见上。这里唯一的职责是「不要因为日志失败而改变响应」
            }
            return ArticleApiSupport.fallbackAuthorName(trimmed);
        }

        // Go 分支 ③：resp == nil || !resp.Found || resp.User == nil
        // ⚠️ Go 的 GetUserResp.User 是 *UserInfo，可以为 nil；
        //    protobuf Java 侧对应的是 hasUser()（presence），两者语义一致。
        if (resp == null || !resp.getFound() || !resp.hasUser()) {
            return ArticleApiSupport.fallbackAuthorName(trimmed);
        }

        // Go 分支 ④：username trim 后为空 → 回落
        String username = resp.getUser().getUsername();
        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername == null || trimmedUsername.isEmpty()) {
            return ArticleApiSupport.fallbackAuthorName(trimmed);
        }
        return trimmedUsername;
    }
}