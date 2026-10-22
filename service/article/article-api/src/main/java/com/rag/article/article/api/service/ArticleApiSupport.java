package com.rag.article.article.api.service;

import com.rag.article.article.api.dto.Article;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.common.exception.BusinessException;
import com.rag.article.article.api.interceptor.JwtAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三个共用能力的落点：<b>身份解析</b>、<b>作者昵称补全</b>、<b>proto → DTO 映射</b>。
 *
 * <p>对应 Go {@code internal/logic/article/helper.go} 的 {@code extractCurrentUserID} /
 * {@code enrichArticleAuthor} / {@code enrichArticleAuthors} / {@code resolveAuthorName} /
 * {@code fallbackAuthorName} / {@code cloneStringMap}。
 *
 * <h2>⚠️ 这 6 个 Go 函数<b>不是</b>「6 个 helper 要照搬成 6 个类」</h2>
 * <p>它们共用同一组数据（都只需要 {@code svcCtx}），且被 6 个 logic 以不同组合调用。
 * 在 Java 侧拆成 6 个 {@code @Component} 只会让「读一个请求要注入 6 个 Bean」。
 * ⇒ 收进<b>一个</b> support 类（与 message-api 的 {@code MessageApiSupport} 同款约定），
 * 方法之间的调用关系保持 Go 原样。
 */
@Component
public class ArticleApiSupport {

    // ============== 身份解析（helper.go:17-39 extractCurrentUserID） ==============

    /**
     * Go {@code extractCurrentUserID} 的 Java 等价物。
     *
     * <pre>
     * Go:
     *   func extractCurrentUserID(ctx context.Context) (string, int) {
     *       uid := ctx.Value("userId")
     *       if uid == nil { return "", errmsg.ErrorUnauthorized }
     *       switch value := uid.(type) {
     *       case json.Number:
     *           if id := strings.TrimSpace(value.String()); id != "" { return id, errmsg.Success }
     *       case string:
     *           if id := strings.TrimSpace(value); id != "" { return id, errmsg.Success }
     *       default:
     *           if id := strings.TrimSpace(fmt.Sprintf("%v", uid)); id != "" &amp;&amp; id != "&lt;nil&gt;" {
     *               return id, errmsg.Success
     *           }
     *       }
     *       return "", errmsg.ErrorUnauthorized
     *   }
     * </pre>
     *
     * <h3>为什么返回的是 <b>String</b>，而不是 long</h3>
     * <ul>
     *   <li>Go 的「原样透传」语义：claim 是 {@code json.Number} 就取其字面量、
     *       是 {@code string} 就取整串 —— <b>不做任何数值转换</b>。
     *       这个 id 随后作为 {@code Article.author_id} / {@code requester_id} 发给 rpc，
     *       也作为 {@code Article.author_id} 原样写进响应。转换会改变字面量。</li>
     *   <li>{@code author_id} 在 proto 里本来就是 {@code string}，Go 侧从不把它变成数字。</li>
     * </ul>
     *
     * <h3>失败形态：<b>HTTP 200 + {code:1005}</b>，不是 401</h3>
     * ⚠️ 这是最容易搞错的一处。三种「取不到 userId」要分清：
     * <table border="1">
     *   <caption>三种「没有身份」</caption>
     *   <tr><th>情况</th><th>Go</th><th>RAG</th><th>HTTP</th></tr>
     *   <tr>
     *     <td>没有 Authorization 头 / 签名错 / 过期</td>
     *     <td>{@code handler.Authorize} 短路</td>
     *     <td>{@code AuthenticationException}</td><td><b>401 空 body</b></td>
     *   </tr>
     *   <tr>
     *     <td>签名过了但 <b>没有 {@code userId} claim</b>（或为空串）</td>
     *     <td colspan="2"><b>两侧一致</b>：logic 里 {@code extractCurrentUserID} 返回 1005</td>
     *     <td><b>200 + 1005</b></td>
     *   </tr>
     * </table>
     * ⇒ 拦截器<b>不能</b>把 claim 取值一并校验（已在
     * {@code JwtAuthInterceptor} 的类注释里写明理由）。
     */
    public String userId(HttpServletRequest request) {
        Object claim = request.getAttribute(JwtAuthInterceptor.CLAIM_ATTRIBUTE);
        String resolved = resolveUserIdClaim(claim);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.ERROR_UNAUTHORIZED.getCode(), msg(ErrorCode.ERROR_UNAUTHORIZED));
        }
        return resolved;
    }

    /**
     * Go {@code extractCurrentUserID} 里那个 type switch 的等价物，返回 {@code null} 表示「无身份」。
     *
     * <p>⚠️ <b>{@code <nil>} 这条判定不能省</b>：Go 的 {@code default} 分支用
     * {@code fmt.Sprintf("%v", uid)}，而 fmt 对 nil 产出字面量 {@code "<nil>"}。
     * Go 作者显式排除了它。Java 侧不会真的拿到 {@code "<nil>"}（claim 缺失时是 {@code null}），
     * 但<b>若上游把 null 序列化成字符串塞进 claim</b>，Go 会拒绝而 Java 会接受
     * ⇒ 这里保留该判定，让两侧对同一种畸形输入给出同一个答案。
     */
    static String resolveUserIdClaim(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof byte[] bytes) {
            return trimToNull(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        // 其余一律走 Go 的 default 分支语义：fmt.Sprintf("%v", uid) + TrimSpace
        String rendered = String.valueOf(claim);
        if ("<nil>".equals(rendered)) {
            return null;
        }
        return trimToNull(rendered);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ============== 作者昵称补全（helper.go:41-103） ==============

    /**
     * 单篇补全 —— Go {@code enrichArticleAuthor}。
     *
     * <p>⚠️ <b>永不抛异常</b>：{@code resolveAuthorName} 的三条失败分支
     * （RPC 失败 / 没找到 / username 空）全部退回 {@code fallbackAuthorName}。
     * 「昵称补全失败」绝不能变成「文章读不出来」。
     */
    public void enrichArticleAuthor(Article article, ArticleAuthorResolver resolver) {
        if (article == null) {
            return;
        }
        article.setAuthorName(resolver.resolveAuthorName(article.getAuthorId()));
    }

    /**
     * 批量补全 —— Go {@code enrichArticleAuthors}。
     *
     * <p>逐条对应 {@code helper.go:48-69}：
     * <ul>
     *   <li>空列表 → 直接 return（Go: {@code if len(articles) == 0 { return }}）；</li>
     *   <li>空 authorId → {@code fallbackAuthorName("")}，<b>不发 RPC</b>
     *       （Go: 显式 {@code if authorID == "" { … = fallbackAuthorName(""); continue }}）；</li>
     *   <li>已查过 → 复用（Go: {@code cache[authorID]}）；</li>
     *   <li>其余 → 查 RPC 并写入缓存。</li>
     * </ul>
     *
     * ⚠️ 去重缓存是<b>请求内</b>的（Go 用的是普通 map，不是 redis）。
     * 提成 Spring bean 缓存就是<b>新增行为</b>：昵称变更后旧值会长期留在缓存里。
     */
    public void enrichArticleAuthors(List<Article> articles, ArticleAuthorResolver resolver) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        Map<String, String> cache = new LinkedHashMap<>();
        for (Article article : articles) {
            String authorId = trimToNull(article.getAuthorId());
            if (authorId == null) {
                article.setAuthorName(fallbackAuthorName(""));
                continue;
            }
            String cached = cache.get(authorId);
            if (cached != null) {
                article.setAuthorName(cached);
                continue;
            }
            String authorName = resolver.resolveAuthorName(authorId);
            cache.put(authorId, authorName);
            article.setAuthorName(authorName);
        }
    }

    /**
     * Go {@code fallbackAuthorName}（{@code helper.go:98-103}）。
     *
     * <pre>
     *   func fallbackAuthorName(authorID string) string {
     *       if authorID = strings.TrimSpace(authorID); authorID != "" { return "用户 " + authorID }
     *       return "未知作者"
     *   }
     * </pre>
     * ⚠️ 拼接的是<b>中文全角冒号后的半角空格</b>（{@code "用户 "}），逐字照抄，
     * 中文文案里多一个空格客户端就会显示异常。
     */
    public static String fallbackAuthorName(String authorId) {
        String trimmed = trimToNull(authorId);
        if (trimmed != null) {
            return "用户 " + trimmed;
        }
        return "未知作者";
    }

    // ============== proto → DTO 映射 ==============

    /**
     * Go {@code cloneStringMap}（{@code helper.go:105-115}）。
     *
     * <pre>
     *   func cloneStringMap(source map[string]string) map[string]string {
     *       if len(source) == 0 { return nil }        // ← 空 map 变 nil
     *       cloned := make(map[string]string, len(source)); …
     *   }
     * </pre>
     *
     * <p>⇒ <b>空 map 必须变 null</b>，否则响应里是 {@code "ext_info":{}} 而不是
     * {@code "ext_info":null}（Go 的 {@code Article.ExtInfo} 无 {@code ,omitempty}）。
     * <p><b>⚠️ 这里必须是 {@link java.util.TreeMap}（排序），不能用 {@link LinkedHashMap}。</b>
     * Go 的 {@code encoding/json} 对 {@code map[string]string} 的 key <b>按字典序输出</b>
     * （{@code encoding/json/encode.go} 的 mapEncoder 走 {@code sort.Slice}），
     * 同一份数据每次序列化字节一致。protobuf 的 {@code getExtInfoMap()} 是<b>无序</b> HashMap，
     * {@code LinkedHashMap} 只能保住<b>迭代顺序</b>（不等于字典序），透传会让 key 顺序漂移。
     *
     * <p><b>本注释自身曾被写成「用 LinkedHashMap 保持字典序」—— 那是事实错误</b>，
     * 且把独立验证者引向了错误结论（据此判定 DRIFT-007「Java 不排序」，
     * 实际代码是 {@code TreeMap}，行为本来就是对的）。
     * ⇒ AGENTS.md §3.3 的又一次发作：<b>注释里的断言也会误导人，且没有测试会红</b>。
     */
    public static Map<String, String> cloneStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return new java.util.TreeMap<>(source);
    }

    /**
     * proto {@code Article} → DTO {@code Article}。
     *
     * <p>四个逐字抄自 Go 的映射点：
     * <pre>
     *   get_article_logic.go:59-76            Content ← MarkdownContent；ExtInfo ← cloneStringMap
     *   get_author_article_logic.go:63-71    同上
     *   list_articles_logic.go:69-86         同上
     *   create/list:  Status ← int32(res.Article.Status)     ← 取数值，不是枚举名
     * </pre>
     *
     * <h3>⚠️ {@code secondary_tags}：proto 的空 List 必须映射成 null</h3>
     * <pre>
     *   proto:  repeated string secondary_tags = 7;   → getSecondaryTagsList() 永远非 null（空 List）
     *   Go:     SecondaryTags []string `json:"secondary_tags"`（无 omitempty）
     *           → nil slice 序列化成 "secondary_tags":null
     * </pre>
     * ⇒ 直接 {@code setSecondaryTags(proto.getSecondaryTagsList())} 会让空标签的文章
     * 输出 {@code []} 而不是 {@code null}，与 Go 不一致。
     * 转换在 {@code Go 的 cloneStringMap 同款位置}，见下。
     */
    public static Article toArticle(com.rag.article.article.proto.Article in) {
        Article out = new Article();
        out.setId(in.getId());
        out.setTitle(in.getTitle());
        out.setBrief(in.getBrief());
        // Go: Content: res.Article.MarkdownContent
        out.setContent(in.getMarkdownContent());
        out.setCoverImageUrl(in.getCoverImageUrl());
        out.setManualTypeTag(in.getManualTypeTag());
        out.setSecondaryTags(cloneStringList(in.getSecondaryTagsList()));
        out.setAuthorId(in.getAuthorId());
        out.setCreateTime(in.getCreateTime());
        out.setUpdateTime(in.getUpdateTime());
        // Go: int32(res.Article.Status) —— 数值，不是 "PUBLISHED"
        out.setStatus(in.getStatusValue());
        out.setViewCount(in.getViewCount());
        out.setLikeCount(in.getLikeCount());
        out.setCommentCount(in.getCommentCount());
        out.setShareCount(in.getShareCount());
        out.setExtInfo(cloneStringMap(in.getExtInfoMap()));
        return out;
    }

    /** 与 {@link #cloneStringMap(Map)} 同构：空 → null。 */
    public static List<String> cloneStringList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return List.copyOf(source);
    }

    /**
     * 「是不是已发布」的判据 —— Go 三处 {@code item.Status != articlepb.ArticleStatus_PUBLISHED}。
     *
     * <p>见 {@code ArticleGetService} / {@code ArticleListService} 的类注释：
     * 这条<b>不是</b>冗余防御，而是「老版本 rpc 忽略 {@code public_only}」时的兜底闸门。
     *
     * @param status proto 枚举值；用 {@code int} 接收是为了与「未知枚举值」共存
     *               （proto3 的未知数值不会抛异常，会落进 {@code UNRECOGNIZED}）
     */
    public static boolean isPublished(int status) {
        return status == ArticleStatus.PUBLISHED.getNumber();
    }

    // ============== 文案查表 ==============

    /**
     * 唯一的「码 → 文案」入口。
     *
     * <p>Go 侧是 {@code errmsg.GetErrMsg(code)}（{@code errmsg.go:39-45}）：
     * 查表未命中时回落 {@code codeMsg[Error]} 即 {@code "FAIL"}。
     * {@code ErrorCode.getMsg(int)} 已经实现了这条回落（含「未知码 → FAIL」）。
     *
     * <p>⚠️ <b>不要</b>在这里按业务分支拼文案（如「文章 X 不存在」）：
     * Go 侧的文案表是 15 条<b>静态</b>文案，没有一条带变量。
     */
    public static String msg(ErrorCode code) {
        return ErrorCode.getMsg(code.getCode());
    }

}
