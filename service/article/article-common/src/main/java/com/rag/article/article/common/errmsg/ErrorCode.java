package com.rag.article.article.common.errmsg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * article 域业务错误码 —— 1:1 对应 Go 端 {@code service/article/common/errmsg/errmsg.go} 整个包。
 *
 * <p>Go 真源全文只有三样东西，本类逐条对应，不多不少：
 * <pre>
 * const ( Success … ErrorMinioDownload )        → 15 个枚举常量（errmsg.go:3-19）
 * var codeMsg = map[int]string{ … }            → 常量上的 msg 字段   （errmsg.go:21-37）
 * func GetErrMsg(code int) string              → {@link #getMsg(int)} （errmsg.go:39-45）
 * </pre>
 *
 * <h2>⚠️ Go 端这个包<b>没有</b> CodeError，也没有 NewGrpcErr</h2>
 * 这与 {@code service/like/common/errmsg} 不同——后者有 {@code CodeError} 结构体和
 * {@code NewGrpcErr}，所以 like 域才需要拆成「枚举 + 文案表 + 错误载体 + gRPC 转换」四个类。
 * <b>article 域的 Go 侧二者皆无</b>（{@code errmsg.go} 全文 45 行，零 import），
 * 所以这里<b>只建一个枚举</b>，不复制 like 的类数量——那属于照搬另一个域的文件结构，
 * 而不是迁移本组件。
 *
 * <h2>article 的错误在链路上长什么样（决定了本类被谁消费）</h2>
 * article 域与 like 域的 gRPC 错误模型<b>相反</b>：
 * <ul>
 *   <li><b>like</b>：业务码直接塞进 {@code grpc-status} 上线（{@code status.Error(codes.Code(5003), …)}）；
 *       api 侧 {@code status.FromError} 后取 {@code int(st.Code())} 反推回业务码。</li>
 *   <li><b>article</b>：rpc 侧发的是<b>标准 gRPC 码</b>（{@code status.Error(codes.Internal, err.Error())}），
 *       api 侧在 logic 里显式映射回业务码：
 *       <pre>
 *       get_author_article_logic.go:44-52
 *         codes.NotFound         → errmsg.ErrorArticleNone
 *         codes.PermissionDenied → errmsg.ErrorArticleForbidden
 *         codes.Internal         → errmsg.ErrorServerCommon
 *         default                → errmsg.CodeServerBusy
 *       </pre>
 *       ⇒ <b>业务码从不上线</b>，因此不需要 gRPC 错误转换类。</li>
 * </ul>
 *
 * <h2>两个消费方，用途不同（不要混用）</h2>
 * <ol>
 *   <li><b>api 层 → HTTP 信封</b>：Go logic 返回 {@code (resp, code int)}，
 *       handler 侧统一包成 {@code response.Response{Code: code, Msg: GetErrMsg(code), Data: resp}}
 *       并用 {@code httpx.OkJson} 写出 ⇒ <b>恒 HTTP 200</b>，业务码只在 body 的 {@code code} 字段里。
 *       例：{@code get_article_handler.go:28-32}。</li>
 *   <li><b>rpc / mq 层 → 结构化日志字段</b>：{@code logger.LogBusinessErr(ctx, code, err, opts…)}
 *       只把 code 当作 {@code error_code} 这个<b>日志字段</b>写出去
 *       （{@code service/common/logger/logger.go:145}），<b>该路径从不调用 GetErrMsg</b>。
 *       ⇒ 运行时若只有 rpc 侧日志，文案不在日志里，别误以为查表失效。</li>
 * </ol>
 *
 * <h2>为什么是「单枚举」而不是像 like 那样拆多类</h2>
 * 本项目已有两处同类组件先例，形态不同：
 * <ul>
 *   <li>{@code users-common/errmsg/ErrorCode} —— <b>单枚举 + (code, msg) + of() + getMsg(int)</b>。
 *       这是最贴近本组件的形态，{@code com.rag.common.exception.BusinessException} 的 javadoc
 *       明确指向它（“调用方传 {@code ErrorCode.X.getCode()} + {@code getMsg()} 即可”），
 *       本类沿用同一约定，保证 article 域调用点写法与 users 域一致。</li>
 *   <li>{@code like-common/errmsg} —— 拆四类，因为 like 的 Go 侧真的多出 {@code CodeError}
 *       与 {@code NewGrpcErr} 两个构件。article 侧没有，不适用。</li>
 * </ul>
 *
 * <h2>⚠️ 跨域码值撞车：数值可复用，含义绝不可跨域推断</h2>
 * article 与 users / like 是<b>不同域但共用同一段 int 值空间</b>，且若干数值含义完全相反。
 * 这些码由 api 层直接透传进 HTTP body 的 {@code code} 字段，是<b>客户端可见</b>的：
 * <table border="1">
 *   <caption>数值相同、语义不同的码</caption>
 *   <tr><th>码值</th><th>article（本类）</th><th>users / like 中的含义</th></tr>
 *   <tr><td>1005</td><td>请先登录</td><td>users: TOKEN格式错误 · like: TOKEN格式错误</td></tr>
 *   <tr><td>1006</td><td>只能编辑或删除自己的文章</td><td>users: TOKEN已过期 · like: TOKEN已过期</td></tr>
 *   <tr><td>5004</td><td>文件上传失败</td><td>users: 数据库插入失败</td></tr>
 *   <tr><td>5005</td><td>文件删除失败</td><td>users: 事务操作失败</td></tr>
 *   <tr><td>5006</td><td>文件下载失败</td><td>users: 雪花ID生成失败</td></tr>
 * </table>
 * ⇒ <b>排查线上问题或写告警规则时，必须指明是哪个域</b>，不能因为「5005 就是事务失败」
 * 就跨域套用。这条由 {@code ArticleErrorCodeContractTest#codesDoNotCollideWithUsersDomainMeaning}
 * 钉死，防止后人「好心」把 article 的 5004 改成数据库插入失败。
 *
 * <h2>框架接管了什么，本类还负责什么</h2>
 * <ul>
 *   <li><b>已由 {@code com.rag.common} 承担，本类不重复实现</b>：
 *       异常载体 {@code BusinessException(int code, String msg)}、
 *       信封 {@code Response(code, msg, data)}、
 *       业务错误日志 {@code Logger.logBusinessErr(ctx, int code, Throwable, LogOption…)}。
 *       三者都<b>只吃 int 业务码</b>，所以本类唯一必须提供的对外能力就是
 *       {@link #getCode()}（把枚举还原成 int）。</li>
 *   <li><b>本类负责</b>：码值本身、文案本身、以及<b>未知码的回落语义</b>——
 *       这是本组件唯一有分支的行为，见 {@link #getMsg(int)}。</li>
 * </ul>
 * <p>因此本类<b>不依赖</b> {@code com.rag.common}：只靠调用点
 * {@code new BusinessException(ec.getCode(), ec.getMsg())} 即可对接，
 * 保持 {@code article-common} 作为零依赖的纯契约模块（与 Go 侧零 import 对称）。
 */
public enum ErrorCode {

    // ===== 通用（Go errmsg.go:4-6）=====
    /**
     * Go {@code Success = 200}。
     *
     * <p>注意这是<b>信封里的 code 字段值</b>，不是 HTTP 状态码——
     * Go 侧所有业务成败都走 {@code httpx.OkJson}，HTTP 恒 200。
     */
    SUCCESS(200, "OK"),

    /**
     * Go {@code Error = 500}。同时是 {@link #getMsg(int)} 未知码的兜底文案 {@code "FAIL"}。
     *
     * <p>⚠️ 它有两个身份：既是「通用失败」这个码，也是查表未命中时的回落值。
     * 回落取的是 <b>500 号的 msg</b>（{@code "FAIL"}），不是空串、也不是把 code 转成字符串。
     */
    ERROR(500, "FAIL"),

    /**
     * Go {@code CodeServerBusy = 1015}。
     *
     * <p>在 api 侧是 gRPC {@code default} 分支的落点（RPC 返回了非
     * NotFound/PermissionDenied/Internal 的任何码都归到这里），
     * 属于<b>兜底</b>而非具体故障类型。
     */
    CODE_SERVER_BUSY(1015, "服务繁忙"),

    // ===== 文章业务（Go errmsg.go:7-10）=====
    /** Go {@code ErrorArticleExist = 1001}。 */
    ERROR_ARTICLE_EXIST(1001, "文章已存在"),

    /** Go {@code ErrorArticleNone = 1002}。也是 api 侧 {@code codes.NotFound} 的落点。 */
    ERROR_ARTICLE_NONE(1002, "文章不存在"),

    /** Go {@code ErrorArticlePublishing = 1003}。 */
    ERROR_ARTICLE_PUBLISHING(1003, "文章处理中，请稍后查看"),

    /** Go {@code ErrorArticlePublishFailed = 1004}。 */
    ERROR_ARTICLE_PUBLISH_FAILED(1004, "文章发布失败"),

    /**
     * Go {@code ErrorUnauthorized = 1005}，文案「请先登录」。
     *
     * <p>⚠️ <b>这是 api 层的业务码，不是 HTTP 401</b>。Go 侧
     * {@code extractCurrentUserID}（{@code logic/article/helper.go:19-34}）在
     * {@code ctx} 里取不到 userId 时返回 {@code ErrorUnauthorized}，
     * 该值随 logic 的 {@code (resp, code)} 双返回流到 handler，被包成
     * {@code {"code":1005,"msg":"请先登录"}} 且 <b>HTTP 状态码仍是 200</b>。
     *
     * <p>⇒ article-api <b>不要</b>拿 {@code com.rag.common.exception.AuthenticationException}
     * 承接这个场景：那个类型的语义是「HTTP 401」，与 Go 的 200 + 信封 1005 相反，
     * 套用会引入真实行为漂移（客户端从 200 变 401）。
     */
    ERROR_UNAUTHORIZED(1005, "请先登录"),

    /**
     * Go {@code ErrorArticleForbidden = 1006}，文案「只能编辑或删除自己的文章」。
     *
     * <p>两个来源：① api 侧 {@code codes.PermissionDenied} 的落点；
     * ② <b>业务级 ownership 判定</b>——{@code get_author_article_logic.go:60-62} 与
     * {@code update_article_logic.go:65-67} 显式比对 {@code Article.AuthorId != 当前用户}。
     * 即：rpc 没拦住的越权，由 api 层用这个码兜住，二者不可互相省略。
     */
    ERROR_ARTICLE_FORBIDDEN(1006, "只能编辑或删除自己的文章"),

    // ===== 系统与基础设施（Go errmsg.go:13-18）=====
    /** Go {@code ErrorServerCommon = 5001}。也是 api 侧 {@code codes.Internal} 的落点。 */
    ERROR_SERVER_COMMON(5001, "系统内部错误"),

    /** Go {@code ErrorDbUpdate = 5002}。 */
    ERROR_DB_UPDATE(5002, "更新数据库失败"),

    /** Go {@code ErrorDbSelect = 5003}。 */
    ERROR_DB_SELECT(5003, "查询数据库失败"),

    /** Go {@code ErrorMinioUpload = 5004}。⚠️ 跨域同名不同义，见类级说明。 */
    ERROR_MINIO_UPLOAD(5004, "文件上传失败"),

    /** Go {@code ErrorMinioDelete = 5005}。⚠️ 跨域同名不同义，见类级说明。 */
    ERROR_MINIO_DELETE(5005, "文件删除失败"),

    /** Go {@code ErrorMinioDownload = 5006}。⚠️ 跨域同名不同义，见类级说明。 */
    ERROR_MINIO_DOWNLOAD(5006, "文件下载失败");

    private final int code;

    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 业务码的 int 值。
     *
     * <p><b>这个 getter 是必需的，不是便利方法</b>：本项目现有的两个消费入口都<b>只吃 int</b>——
     * {@code com.rag.common.exception.BusinessException(int code, String msg)} 与
     * {@code com.rag.common.logger.Logger.logBusinessErr(ctx, int code, Throwable, LogOption…)}。
     * 没有它，调用点无法把枚举喂进这两条既有链路（Go 侧传的就是裸 int）。
     */
    public int getCode() {
        return code;
    }

    /**
     * 本枚举对应的提示语（实例版）。
     *
     * <p>用于 {@code new BusinessException(ec.getCode(), ec.getMsg())} 这类
     * 「调用点已经知道自己要哪个码」的场景，省掉一次查表。
     */
    public String getMsg() {
        return msg;
    }

    /** code → 枚举常量缓存，避免每次 {@code values()} 新建数组 + O(n) 遍历。 */
    private static final Map<Integer, ErrorCode> CODE_MAP;

    static {
        // HashMap 而非 EnumMap：这里是「错误码 int → 枚举」的反向索引，键是 Integer 不是枚举类型。
        Map<Integer, ErrorCode> map = new HashMap<>();
        for (ErrorCode ec : values()) {
            map.put(ec.code, ec);
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * code → 枚举常量；<b>未命中返回 {@link #ERROR}</b>，不返回 {@code null}。
     *
     * <p>与 {@link #getMsg(int)} 同源的回落策略：Go 侧只有一层回落（{@code GetErrMsg}），
     * 这里把回落后的文案也一并提供，免得调用点拿到 {@code null} 后再 NPE。
     */
    public static ErrorCode of(int code) {
        return CODE_MAP.getOrDefault(code, ERROR);
    }

    /**
     * <b>对应 Go {@code func GetErrMsg(code int) string}（{@code errmsg.go:39-45}）。</b>
     *
     * <pre>
     * func GetErrMsg(code int) string {
     *     msg, ok := codeMsg[code]
     *     if !ok {
     *         return codeMsg[Error]     // ← 未命中回落到 "FAIL"
     *     }
     *     return msg
     * }
     * </pre>
     *
     * <p><b>回落语义是本组件唯一有分支的行为，必须逐字对齐</b>：
     * 未知码返回 {@link #ERROR} 的文案 {@code "FAIL"}，
     * <b>不是</b> {@code null}、<b>不是</b>空串、<b>不是</b>把 code 拼成字符串。
     *
     * <p>可观察后果：任何把「RPC 侧来的、未在本表登记」的码透传到 api 信封的路径，
     * 用户看到的一定是 {@code "FAIL"}。若改成返回 null，信封的 {@code msg} 会变成 {@code "null"}
     * 或被序列化层省略——那是客户端可见的行为漂移。
     */
    public static String getMsg(int code) {
        return of(code).msg;
    }
}