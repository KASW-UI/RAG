package com.rag.favorite.favorite.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.common.logger.LogOption;
import com.rag.common.logger.WithArticleID;
import com.rag.common.logger.WithUserID;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.favorite.favorite.api.dto.FavoriteFolder;
import com.rag.favorite.favorite.api.dto.FavoriteItem;
import com.rag.favorite.favorite.api.interceptor.FavoriteJwtAuthInterceptor;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import com.rag.favorite.favorite.common.errmsg.FavoriteGrpcError;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Map;

/**
 * favorite-api 的共享支撑：身份解析 + proto→DTO 映射 + 业务日志。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/api/internal/logic/favorite/helper.go:14-42}
 * <pre>
 * func extractUserID(ctx context.Context) (int64, error) {
 *     raw := ctx.Value("userId")
 *     if raw == nil { return 0, fmt.Errorf("ctx userId is nil") }
 *     switch v := raw.(type) {
 *     case json.Number: return v.Int64()
 *     case string:     return strconv.ParseInt(v, 10, 64)
 *     case int64:      return v, nil
 *     case int:        return int64(v), nil
 *     case float64:    return int64(v), nil
 *     default:         return 0, fmt.Errorf("ctx userId has unexpected type %T", raw)
 *     }
 * }
 * </pre>
 */
@Component
public class FavoriteApiSupport {

    private static final Logger log = LoggerFactory.getLogger(FavoriteApiSupport.class);

    /**
     * Go 侧 7 个 logic 在 {@code extractUserID} 失败时统一返回的码：
     * {@code favoritecommon.ErrorUnauthorized = 5004}
     * （如 {@code create_favorite_folder_logic.go:36-40}）。
     */
    private static final int ERROR_UNAUTHORIZED = FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode();

    private final LoggerFactoryBean logger;

    public FavoriteApiSupport(LoggerFactoryBean logger) {
        this.logger = logger;
    }

    /**
     * 解析当前请求的 userId —— 对应 Go 的 {@code extractUserID(ctx)}。
     *
     * <p><b>⚠️ 本层刻意【不】校验 claim 的取值</b>：Go 侧
     * {@code handler.Authorize}（{@code rest/handler/authhandler.go:71-79}）只验签名与 exp，
     * 随后把 claim <b>原样</b>塞进 ctx；claim 能不能变成 int64 是<b>业务层</b>的判定。
     * ⇒ 解析失败走的是 <b>HTTP 200 + {code:5004}</b>，
     * <b>不是</b> 401（401 只由鉴权层的验签/黑名单失败产生）。
     *
     * <h3>为什么 {@code Double} 判为失败</h3>
     * <p>Go 侧 go-zero 的 token 解析器是
     * {@code rest/token/tokenparser.go:122-124} 的
     * {@code jwt.NewParser(jwt.WithJSONNumber())} ⇒ JWT 里的数字 claim 一律是
     * {@code json.Number}（<b>string 的命名类型</b>），<b>永远不是 float64</b>。
     * ⇒ Go 走的是 {@code case json.Number: return v.Int64()}，
     * 而 {@code json.Number("1.5").Int64()} 会<b>失败</b>（{@code strconv.ParseInt("1.5")} 报错）。
     * <p>所以 Go 对 {@code "userId": 1.5} 的行为是<b>失败 → 5004</b>，
     * <b>不是</b>「截断成 1」。本实现保持一致：{@link Double} / {@link Float} 一律判失败。
     * （Go 里那个 {@code case float64} 分支在 JWT 路径上<b>不可达</b>，保留只是历史兼容。）
     */
    public long userId(HttpServletRequest request) {
        Object raw = request.getAttribute(FavoriteJwtAuthInterceptor.CLAIM_ATTRIBUTE);
        if (raw == null) {
            // Go: ctx userId is nil
            log.warn("ctx userId is nil, uri={}", request.getRequestURI());
            throw unauthorized();
        }
        if (raw instanceof String s) {
            // Go: strconv.ParseInt(v, 10, 64)
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                log.warn("ctx userId is not a valid int64 : {}", s);
                throw unauthorized();
            }
        }
        if (raw instanceof BigInteger bi) {
            // Go: json.Number("...").Int64() ⇒ strconv.ParseInt 越界即失败。
            // BigInteger.bitLength() >= 64 ⟺ 越出 int64 值域（与 GoStyleStringInt64 同判据）。
            if (bi.bitLength() >= 64) {
                log.warn("ctx userId is out of int64 range : {}", bi);
                throw unauthorized();
            }
            return bi.longValue();
        }
        if (raw instanceof Byte || raw instanceof Short
                || raw instanceof Integer || raw instanceof Long) {
            // Go: json.Number("123").Int64() —— 整数字面量精确等价
            return ((Number) raw).longValue();
        }
        if (raw instanceof Double || raw instanceof Float) {
            // Go: json.Number("1.5").Int64() 失败 ⇒ 5004（见类注释）
            log.warn("ctx userId is a non-integer number : {}", raw);
            throw unauthorized();
        }
        // Go: default 分支 —— unexpected type
        log.warn("ctx userId has unexpected type : {}", raw.getClass().getName());
        throw unauthorized();
    }

    /**
     * proto 的 {@code favorite.FavoriteFolder} → HTTP 出参 DTO。
     *
     * <p>Go 出处：{@code get_favorite_folders_logic.go:58-66}。
     * ⚠️ <b>刻意不映射 {@code user_id}</b>：Go 的 {@code types.FavoriteFolder}
     * （{@code types.go:42-47}）也没有这个字段 —— 它只存在于 rpc 层。
     */
    public FavoriteFolder toFolderDto(com.rag.favorite.favorite.proto.FavoriteFolder proto) {
        return new FavoriteFolder(
                proto.getFolderId(),
                proto.getName(),
                proto.getCreatedAt(),
                proto.getUpdatedAt());
    }

    /**
     * proto 的 {@code favorite.FavoriteItem} → HTTP 出参 DTO。
     *
     * <p>Go 出处：{@code get_favorites_by_folder_logic.go:64-75}。
     * ⚠️ <b>刻意不映射 {@code user_id}</b>：同 {@link #toFolderDto}。
     */
    public FavoriteItem toItemDto(com.rag.favorite.favorite.proto.FavoriteItem proto) {
        return new FavoriteItem(
                proto.getFavoriteId(),
                proto.getFolderId(),
                proto.getTargetId(),
                proto.getTargetType(),
                proto.getTitle(),
                proto.getCover(),
                proto.getCreatedAt());
    }

    /**
     * 业务日志 —— 对应 Go 的 {@code logger.LogInfo(ctx, msg, userLogOption(userID), ...)}
     * （{@code service/common/logger/logger.go:85}）。
     *
     * <p>⚠️ <b>日志失败不得影响业务</b>：Go 侧的 logger 写不出去不会让请求失败，
     * 本实现用 try/catch 包住，保证语义一致（与 hot-api 的 {@code logQuietly} 同款处理）。
     */
    public void logInfo(String msg, Long userId, String articleId) {
        try {
            logger.logInfo(Context.current(), msg, options(userId, articleId));
        } catch (Throwable t) {
            log.warn("logInfo failed : {}", t.getMessage());
        }
    }

    /**
     * 业务错误日志 —— 对应 Go 的
     * {@code logger.LogBusinessErr(ctx, code, err, userLogOption(userID), articleLogOption(...))}
     * （{@code service/common/logger/logger.go:77}）。
     */
    public void logBusinessErr(int code, Throwable err, Long userId, String articleId) {
        try {
            logger.logBusinessErr(Context.current(), code, err, options(userId, articleId));
        } catch (Throwable t) {
            log.warn("logBusinessErr failed : {}", t.getMessage());
        }
    }

    /**
     * 构造 Go 的两个 log option：
     * {@code userLogOption} = {@code logger.WithUserID(strconv.FormatInt(userID, 10))}
     * （{@code service/common/logger/logger.go:35}）、
     * {@code articleLogOption} = {@code logger.WithArticleID(strings.TrimSpace(articleID))}
     * （{@code service/common/logger/logger.go:42}）。
     */
    private static LogOption[] options(Long userId, String articleId) {
        LogOption[] opts = new LogOption[2];
        opts[0] = WithUserID.of(userId == null ? "" : String.valueOf(userId));
        opts[1] = WithArticleID.of(articleId == null ? "" : articleId.trim());
        return opts;
    }

    /**
     * 调用下游 gRPC，并把失败<b>还原成 Go 的业务码</b>。
     *
     * <p>Go 出处（7 个 logic 完全同构，以 {@code create_favorite_logic.go:58-63} 为例）：
     * <pre>
     *   rpcResp, rpcErr := l.svcCtx.FavoriteRpc.CreateFavorite(ctx, &amp;favoriteservice.CreateFavoriteReq{...})
     *   if rpcErr != nil {
     *       span.RecordError(rpcErr)
     *       code = codeFromRPCError(rpcErr)          // ← favoritecommon.BizCodeFromError
     *       logger.LogBusinessErr(ctx, code, rpcErr, userLogOption(userID), articleLogOption(req.TargetId))
     *       return nil, code
     *   }
     * </pre>
     *
     * <p>⇒ 失败一律是 <b>HTTP 200 + {code, msg, data:null}</b>，
     * <b>不是</b> 5xx。<b>任何</b>异常都走这条路径（含非 gRPC 状态的普通异常 ——
     * Go 侧 {@code status.FromError} 对普通 error 返回 {@code ok=false} ⇒ 1015）。
     */
    public <T> T invokeRpc(RpcCall<T> call, Long userId, String articleId) {
        try {
            return call.invoke();
        } catch (Exception e) {
            int code = FavoriteGrpcError.bizCodeFromError(e);
            logBusinessErr(code, e, userId, articleId);
            throw new BusinessException(code, FavoriteErrorMessages.getMsg(code));
        }
    }

    /**
     * 下游返回 {@code null} 响应 —— 对应 Go 的 {@code if rpcResp == nil { ... 5001 }}。
     *
     * <p><b>Go 出处（4 个 logic：create ×2 + get ×2）</b>：
     * <ul>
     *   <li>{@code create_favorite_folder_logic.go:57-62}、</li>
     *   <li>{@code create_favorite_logic.go:64-69}、</li>
     *   <li>{@code get_favorite_folders_logic.go:51-56}、</li>
     *   <li>{@code get_favorites_by_folder_logic.go:57-62}。</li>
     * </ul>
     * <pre>
     *   if rpcResp == nil {
     *       err = fmt.Errorf("favorite rpc returned nil response")
     *       ... ErrorServerCommon(5001)
     *   }
     * </pre>
     * ⚠️ <b>这 4 个 logic 只判 {@code nil}</b>：它们的响应消息里<b>根本没有 {@code success} 字段</b>
     * ⇒ 不存在「success=false」这条分支。
     *
     * <p>⚠️ <b>为什么与 {@link #nullOrUnsuccessfulResponse} 分开成两个出口</b>：
     * Go 的<b>另 3 个</b> logic（delete ×2 + update ×1）判的是
     * {@code rpcResp == nil || !rpcResp.Success}，且错误文案是<b>另一句</b>
     * （{@code "favorite rpc returned unsuccessful response"}）。
     * ⇒ Go 侧这两类是<b>分开的两条分支</b>（判定不同、措辞不同），
     * 本实现逐条对齐，<b>不合并</b> —— 合并会让服务端业务日志少掉一条 Go 侧存在的措辞。
     */
    public BusinessException nullResponse(Long userId, String articleId) {
        IllegalStateException cause = new IllegalStateException("favorite rpc returned nil response");
        logBusinessErr(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(), cause, userId, articleId);
        int code = FavoriteErrorCode.ERROR_SERVER_COMMON.getCode();
        return new BusinessException(code, FavoriteErrorMessages.getMsg(code));
    }

    /**
     * 下游返回 {@code null} <b>或</b> {@code success=false} —— 对应 Go 的
     * {@code if rpcResp == nil || !rpcResp.Success { ... 5001 }}。
     *
     * <p><b>Go 出处（3 个 logic：delete ×2 + update ×1）</b>：
     * <ul>
     *   <li>{@code delete_favorite_logic.go:57-62}、</li>
     *   <li>{@code delete_favorite_folder_logic.go:57-62}、</li>
     *   <li>{@code update_favorite_folder_name_logic.go:59-64}。</li>
     * </ul>
     * <pre>
     *   if rpcResp == nil || !rpcResp.Success {
     *       err = fmt.Errorf("favorite rpc returned unsuccessful response")
     *       ... ErrorServerCommon(5001)
     *   }
     * </pre>
     * ⚠️ <b>Go 的措辞是 {@code "unsuccessful"}（不含 "nil or"}</b> ⇒ 本实现用同一句。
     */
    public BusinessException nullOrUnsuccessfulResponse(Long userId, String articleId) {
        IllegalStateException cause = new IllegalStateException("favorite rpc returned unsuccessful response");
        logBusinessErr(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(), cause, userId, articleId);
        int code = FavoriteErrorCode.ERROR_SERVER_COMMON.getCode();
        return new BusinessException(code, FavoriteErrorMessages.getMsg(code));
    }

    /**
     * {@code null} → {@code ""}，对齐 Go 的字符串零值。
     *
     * <p><b>为什么需要它</b>：Go 的 {@code string} <b>没有 null</b>，缺字段就是 {@code ""}；
     * 而 protobuf 生成的 {@code setXxx(String)} 内部是 {@code Objects.requireNonNull(value)}
     * ⇒ 直接把 DTO 的 {@code null} 透进去会抛 NPE，让一个本该 200 的请求变成 500。
     * <p>两种触发路径：
     * <ul>
     *   <li><b>optional 字段缺席 / 显式 null</b>：Go 侧保持零值 {@code ""}
     *       （{@code core/mapping/unmarshaler.go:856-862} 的
     *       {@code if mapValue == nil { if opts.optional() { return nil } ... }}），
     *       Java 侧 DTO 里是 {@code null} ⇒ <b>必须</b>归一；</li>
     *   <li><b>必填字段</b>：必填判定已保证非 null，这条路径实际不可达；
     *       仍统一走本方法，是为了让「Go 的 string 不可能为 null」这条语义在类型上就成立，
     *       而不是散落在每个调用点的隐含前提。</li>
     * </ul>
     */
    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * {@code null} → {@code 0}，对齐 Go 的 int64 零值。
     *
     * <p><b>为什么需要它</b>：protobuf 的 {@code setFolderId(long)} 是<b>原始类型</b>入参，
     * 而 DTO 里是包装类型 {@code Long} ⇒ 直接传进去会触发<b>自动拆箱 NPE</b>。
     * 与 {@link #nullToEmpty(String)} 同理：Go 的 int64 也没有 null，缺字段就是 0。
     * <p>必填字段实际不可达 null（必填判定先行），但统一归一能让「Go 没有 null」
     * 这条语义在类型上成立，而不是散落在每个调用点的隐含前提。
     */
    public static long nullToZero(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * 取 Go 的 form 通道里某个 key 的<b>首值</b> —— 完整复刻 go-zero 的
     * {@code GetFormValues} + {@code WithFromArray} 两段语义。
     *
     * <p><b>⚠️ 为什么不能用 {@code @RequestParam String} 一把梭</b>：
     * Spring 对重复参数会<b>用逗号拼成一个字符串</b>，而 Go 的两段语义合起来是
     * 「先滤掉空值，再取下标 0」。两者在重复参数上结论完全不同：
     * <pre>
     *   ?folderId=123&amp;folderId=456
     *     Spring @RequestParam → "123,456" → Long.parseLong 失败 → 5003
     *     Go                  → 123        → 200
     * </pre>
     *
     * <h3>Go 出处（两段都必须落，缺一不可）</h3>
     * <p><b>① 先滤空值</b> —— {@code rest/httpx/util.go:37-53} 的 {@code GetFormValues}：
     * <pre>
     *   for _, v := range values {
     *       if len(v) == 0 { continue }        // ← 空值整个丢掉（注释原文：ignore empty values）
     *       if n &lt; maxFormParamCount { filtered = append(filtered, v); n++ }
     *       else { return nil, fmt.Errorf("too many form values, ...") }
     *   }
     *   if len(filtered) &gt; 0 { params[name] = filtered }
     * </pre>
     * <p><b>② 再取首值</b> —— {@code rest/httpx/requests.go:27-31} 构造 form 通道的
     * unmarshaler 时开了 {@code mapping.WithFromArray()}，于是
     * {@code core/mapping/unmarshaler.go:835-846}：
     * <pre>
     *   if u.opts.fromArray {
     *       ...
     *       if val.Len() &gt; 0 { mapValue = val.Index(0).Interface() }   // ← 下标 0
     *   }
     * </pre>
     *
     * <p><b>⚠️ 顺序不能反</b>。真源探针（go-zero v1.10.2 + 逐字 {@code types.go} 结构体）实测：
     * <pre>
     *   ?folderId=123&amp;folderId=456  → 123   （首值生效）
     *   ?folderId=&amp;folderId=456     → 456   （空值先被滤掉，456 成了首值）
     *   ?folderId=456&amp;folderId=     → 456
     *   ?folderId=&amp;folderId=        → 字段缺席 ⇒ 5003
     *   ?folderId=abc&amp;folderId=456  → 5003（在首值 abc 上就 ParseInt 失败）
     *   ?folderid=123&amp;folderId=456  → 456   （key 大小写敏感，只认 folderId）
     * </pre>
     * 若把两步反过来（先取下标 0 再判空），{@code ?folderId=&amp;folderId=456} 会退化成
     * 「字段缺席」⇒ 5003，与 Go 的 200 相反。
     *
     * <p><b>⚠️ 计数是跨所有 key 累计的</b>，不是按 key —— 见上面 {@code n++} 在
     * {@code for name, values} 外层之外。超过 {@value #GO_MAX_FORM_PARAM_COUNT} 个非空值时
     * Go 直接报错 ⇒ 本域落到 5003。
     *
     * @param request 当前请求（{@code getParameterMap()} 同时覆盖 query string 与
     *                {@code application/x-www-form-urlencoded} 请求体，与 Go 的 {@code r.Form} 同源）
     * @param name    目标 key（<b>大小写敏感</b>）
     * @return 清洗后的首值；{@code null} 表示「该 key 缺席或只有空值」
     * @throws IllegalArgumentException 非空值总数超过 {@value #GO_MAX_FORM_PARAM_COUNT}
     */
    public static String goFormFirstValue(HttpServletRequest request, String name) {
        int seen = 0;
        String selected = null;
        for (Map.Entry<String, String[]> entry : parameterMap(request).entrySet()) {
            boolean target = entry.getKey().equals(name);
            for (String value : entry.getValue()) {
                // Go: `if len(v) == 0 { continue }` —— 空值在计数与取值之前就被丢掉。
                if (value == null || value.isEmpty()) {
                    continue;
                }
                // Go: `if n < maxFormParamCount { ... n++ } else { return nil, fmt.Errorf(...) }`
                // ⇒ 恰好放行 2048 个，第 2049 个触发报错。
                if (seen >= GO_MAX_FORM_PARAM_COUNT) {
                    throw new IllegalArgumentException("too many form values");
                }
                seen++;
                // 取该 key 的【第一个】非空值 —— Go 是 filtered 数组的 index 0。
                if (target && selected == null) {
                    selected = value;
                }
            }
        }
        return selected;
    }

    /**
     * 取 {@code request.getParameterMap()}，并把 <b>Tomcat 的「参数过多」异常</b>转成
     * {@link IllegalArgumentException}。
     *
     * <p><b>🔴 为什么必须转</b>（2026-10-25 真 Tomcat 实测发现的漂移）：
     * <ul>
     *   <li>Tomcat 在请求的参数<b>条目数</b>超过 {@code server.tomcat.max-parameter-count}
     *       时，会在 {@code getParameterMap()} 里抛
     *       {@code org.apache.tomcat.util.http.InvalidParameterException}
     *       （它<b>不是</b> {@code IllegalArgumentException} 的子类）。</li>
     *   <li>⇒ 它<b>绕过了</b> {@code GlobalExceptionHandler#handleIllegalArgument}
     *       ⇒ 落到 {@code handleAny} 兜底 ⇒ <b>HTTP 500 + {code:5001}</b>。</li>
     *   <li>而 Go 侧的对应场景是 {@code httpx.GetFormValues} 超过
     *       {@code maxFormParamCount=2048} ⇒ {@code httpx.Parse} 失败
     *       ⇒ handler 落 <b>HTTP 200 + {code:5003, msg:"参数错误"}</b>。</li>
     * </ul>
     * ⇒ 两侧在<b>同一个输入</b>下是<b>不同状态码 + 不同业务码</b>（500/5001 vs 200/5003）——
     * 这是可观察的 L1 差异。
     *
     * <p><b>⇒ 本方法把「容器层的参数上限」与「应用层的参数上限」统一收敛到 Go 的出口</b>：
     * 无论是谁先发现「参数太多」，对外都只呈现 Go 的那一种形态（<b>200 + 5003</b>）。
     *
     * <p><b>⚠️ 计数口径仍与 Go 差 1</b>（<b>未修，见 DRIFT-FAV-API-001</b>）：
     * Tomcat 数的是<b>参数条目数</b>（key + value 合计），Go 的 {@code GetFormValues} 数的是
     * <b>非空值个数</b> ⇒ 同名 2048 个值时，Tomcat 拒、Go 放行。
     * ⇒ 修它要动 {@code server.tomcat.max-parameter-count}，而该值由<b>全仓</b>守门脚本
     * {@code tools/difftest/check-tomcat-limits.py} 统一期望为 2048 ⇒ 需跨域拍板，
     * <b>不</b>在 favorite 域单方面改。
     *
     * <p><b>⚠️ 为什么 catch 的是 {@link RuntimeException} 而不是 Tomcat 的类</b>：
     * 本方法<b>只包住 {@code getParameterMap()} 这一行</b>（范围极窄），
     * 且不把 favorite-api 的编译期依赖绑到 Tomcat 的内部类上
     * （{@code org.apache.tomcat.util.http} 不是 Tomcat 的公开 API 面）。
     */
    private static Map<String, String[]> parameterMap(HttpServletRequest request) {
        try {
            return request.getParameterMap();
        } catch (RuntimeException e) {
            // Tomcat 的 InvalidParameterException（参数条目数超过 max-parameter-count）在此抛出。
            // ⚠️ 不把原异常吞掉：挂到 cause 上，日志里仍能看到「是哪个容器限制触发的」。
            throw new IllegalArgumentException("too many request parameters: " + e.getMessage(), e);
        }
    }

    /**
     * Go 的 form 值总数上限 —— {@code rest/httpx/util.go:15}：
     * <pre>
     * const maxFormParamCount = 2048
     * </pre>
     */
    private static final int GO_MAX_FORM_PARAM_COUNT = 2048;

    /** 下游 gRPC 调用的函数式接口（stub 方法不声明 checked exception，故不抛）。 */
    @FunctionalInterface
    public interface RpcCall<T> {
        T invoke();
    }

    /**
     * 构造 Go 的 {@code favoritecommon.ErrorUnauthorized}（5004）业务异常。
     *
     * <p>Go: {@code return nil, favoritecommon.ErrorUnauthorized}
     * （{@code service/favorite/common/errmsg.go:17}）⇒
     * handler 写 {@code {code:5004, msg:"未登录或登录已失效", data:null}}，<b>HTTP 200</b>。
     */
    private static BusinessException unauthorized() {
        return new BusinessException(ERROR_UNAUTHORIZED,
                FavoriteErrorMessages.getMsg(ERROR_UNAUTHORIZED));
    }
}
