package com.rag.hot.hot.api.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.exception.BusinessException;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.common.errmsg.HotErrorMessages;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * query string 的<b>输入解释规则</b> —— Go {@code httpx.ParseForm} 的 Java 等价物。
 *
 * <p>本组件唯一的入参面就是 {@code GET /hot/v1/articles?page=&page_size=}（Go
 * {@code routes.go:16-24} + {@code types.go:22-25}），所以这一层规则直接决定 L1。
 *
 * <h2>为什么必须自己写（不能交给 Spring 的默认类型转换）</h2>
 * 写成 {@code @RequestParam(defaultValue = "20") int pageSize} 会得到三条与 Go 不同的行为，
 * 每一条都是<b>「Go 成功 / Java 失败」</b>方向的可观察漂移：
 *
 * <h3>规则 1：空值被<b>整个丢掉</b>（不是报错、也不是「空串」）</h3>
 * <pre>
 *   httpx/util.go:35-62   GetFormValues
 *       for _, v := range values {
 *           if len(v) == 0 { continue }              // ← ?page= 与 ?page 都命中这里
 *       }
 *       if len(filtered) > 0 { params[name] = filtered }   // ← 值为空的 key 根本不进 map
 * </pre>
 * ⇒ {@code ?page=} 与 {@code ?page} 与<b>完全不传</b>三者<b>完全等价</b>：都取 tag 默认值
 * {@code 1}，<b>不报错</b>。
 * <p>Spring 侧 {@code @RequestParam(defaultValue="20") int} 拿到 {@code ""}（不是 null）
 * ⇒ 转换失败抛 {@code MethodArgumentTypeMismatchException}
 * ⇒ 这是一条 Go 根本不存在的拒绝路径（{@code ?page=} 在 Go 是成功的 200）。
 *
 * <h3>规则 2：整数按 {@code strconv.ParseInt(str, 10, 32)} 解释</h3>
 * <pre>
 *   core/mapping/utils.go:104-125   convertTypeFromString
 *       case reflect.Int32: return strconv.ParseInt(str, 10, 32)
 *   （调用链：httpx/requests.go:85-92 ParseForm → formUnmarshaler.Unmarshal
 *     → unmarshaler.go:880 fromString 分支 → :1099 fillPrimitive
 *     → :521 setValueFromString → convertTypeFromString）
 * </pre>
 * Spring 的默认转换走 {@code NumberUtils.parseNumber}，对 {@code Integer} 走
 * {@code Integer.decode}（base 0 语义）⇒ 三条分歧：
 * <table border="1">
 *   <caption>base 0 vs base 10</caption>
 *   <tr><th>输入</th><th>Go（base 10）</th><th>Spring 默认</th></tr>
 *   <tr><td>{@code ?page=010}</td><td><b>10</b></td><td><b>8</b>（八进制）</td></tr>
 *   <tr><td>{@code ?page=0x10}</td><td><b>解析失败</b> → 5001</td><td><b>16</b>，静默通过</td></tr>
 *   <tr><td>{@code ?page=99999999999}</td><td>超出 int32 → 解析失败 → 5001</td><td>溢出后截断</td></tr>
 * </table>
 * ⇒ 这里显式用 {@link Integer#parseInt(String, int)}（base 10、32 位）。
 *
 * <h3>规则 3：解析失败的出口是 <b>HTTP 200 + {code:5001}</b>，不是 400</h3>
 * Go 的 handler（{@code getHotArticlesHandler.go:18-21}）把 {@code httpx.Parse} 的错误交给
 * {@code httpx.ErrorCtx}，而 {@code hotcenter.go:53-66} 的 {@code SetErrorHandler}
 * <b>返回 {@code http.StatusOK}</b>，且该错误<b>不是</b> {@code *hotcommon.CodeError}
 * ⇒ {@code errors.As} 取不到 code ⇒ 用默认值 {@code ErrorServerCommon(5001)} +
 * {@code GetErrMsg(5001)="internal server error"}，{@code Data} 恒 {@code nil}。
 * <p>⇒ 本类在解析失败时<b>直接抛 {@link BusinessException}(5001, "internal server error")}</b>：
 * 由 {@code GlobalExceptionHandler} 走与 Go 完全相同的出口。
 * <b>不要</b>抛「400 类」异常 —— 那会变成裸 400，与 Go 不一致。
 *
 * <h3>规则 4：同名多值取<b>首值</b>（不是逗号拼接、不是末值、不是报错）</h3>
 * <pre>
 *   httpx/util.go:60        params[name] = filtered          // 同名多值收进一个 []string
 *   core/mapping/unmarshaler.go:835-846   fromArray 分支
 *       fieldKind != Slice/Array &amp;&amp; 值是数组 ⇒ mapValue = val.Index(0).Interface()
 *   （httpx/requests.go:27-31 的 formUnmarshaler 带 mapping.WithFromArray()）
 * </pre>
 * ⇒ {@code ?page=5&page=3} ⇒ <b>page=5</b>（不是 {@code "5,3"}）。
 * <p>⚠️ Spring 的 {@code @RequestParam String} 在同名多值时拿到的是
 * {@code String[] → String} 的转换结果（{@code ArrayToStringConverter}，用 {@code ","} 拼接）
 * ⇒ {@code "5,3"} ⇒ {@code Integer.parseInt} 失败 ⇒ 整包 5001。
 * ⇒ 这里改为直接读 {@link HttpServletRequest#getParameterValues(String)} 取第 0 个。
 *
 * <h3>规则 5：form 值个数上限 2048</h3>
 * <pre>
 *   httpx/util.go:15   maxFormParamCount = 2048
 *   httpx/util.go:48-53
 *       if n &lt; maxFormParamCount { filtered = append(filtered, v); n++ }
 *       else { return nil, fmt.Errorf("too many form values, ...") }
 * </pre>
 * ⇒ 第 <b>2049</b> 个非空 form 值即整体报错 → {@code SetErrorHandler} → 200 + 5001。
 * <p>⚠️ 计数器 {@code n} 是<b>跨所有 key</b> 的全局计数，且<b>空值不计数</b>。
 *
 * <h3>规则 6：畸形 / 非对象 JSON body 是一条真实的拒绝路径</h3>
 * <pre>
 *   httpx/requests.go:66-68   ParseJsonBody（Parse 的最后一步，**无条件**执行）
 *   httpx/requests.go:153-155 withJsonBody(r) = r.ContentLength &gt; 0 &amp;&amp;
 *                                       strings.Contains(Content-Type, "application/json")
 *   core/mapping/jsonunmarshaler.go:45-53
 *       jsonx.UnmarshalFromReader(reader, &amp;m)   // ← 先做 JSON 语法校验
 *       unmarshaler.Unmarshal(m, v)                 // ← 再按 json tag 填字段
 * </pre>
 * ⇒ {@code Content-Type: application/json} + <b>畸形</b> body ⇒ 语法错误 ⇒ 5001；
 * {@code null} / {@code [..]} / {@code 123} / {@code "x"} ⇒ 顶层不是对象 ⇒ 5001；
 * 只有<b>对象</b>才通过（且对象里的字段因无 json tag 而<b>整体被忽略</b>，
 * 见 {@code core/mapping/utils.go:589-597} 的 {@code usingDifferentKeys}）。
 *
 * <h2>本类<b>不做</b>值域校验（与 article 域的同名工具刻意不同）</h2>
 * {@code page=-1} / {@code page_size=99999} 在 Go 侧<b>不会</b>在解析阶段被拒：
 * 它们先落到 {@code types.HotArticlesReq}，再由 logic 的 {@code normalizePagination}
 * 归一化（{@code <=0 → 默认}、{@code >50 → 50}）。提前在 Web 层夹一次会让
 * 「logic 的归一化逻辑」失去可测性，也与 Go 的分层不符。
 *
 * <h2>与 {@code com.rag.article.article.api.form.GoFormValues} 的关系</h2>
 * article-api 有一份同名工具，实现的是<b>同一批 go-zero 解析规则</b>
 * （空值丢弃 / bool 真值表 {@code 1|0} / base-10 整数）。本组件<b>不</b>跨模块依赖它：
 * {@code hot-api} 依赖 article-api 会把两个业务域耦在一起，而把该工具下沉到
 * {@code service/common} 属于<b>超出本次 hot/api 迁移范围</b>的改动
 * （会同时改动 article-api）。此处按「同一规则、同域内自持」处理，
 * 两份各自带测试锁定；若日后要统一，那是独立的一次决策。
 */
public final class GoFormValues {

    /**
     * Go {@code httpx/util.go:15} 的 {@code maxFormParamCount = 2048}。
     *
     * <p>⚠️ 这是 go-zero 的<b>库级常量</b>，不在 hot 的 yaml 里 —— 照 yaml 对永远看不见。
     */
    public static final int MAX_FORM_PARAM_COUNT = 2048;

    /**
     * Go {@code httpx/util.go:59-61} 的数组记法后缀：{@code ?page[]=5} 会被剥掉 {@code []}
     * 再归到同名 key {@code page}。
     */
    private static final String ARRAY_SUFFIX = "[]";

    /**
     * 只做 JSON <b>语法与顶层类型</b>判定用的 mapper。
     *
     * <p>刻意<b>不</b>复用 Spring 运行时那个 mapper：本方法只回答「这个 body 是不是一个
     * 合法的 JSON 对象」，与「序列化成什么形状」无关 ⇒ 任何序列化定制都不影响判定结果。
     * （这正是 {@code HotApiWireContractTest} 踩过的坑的镜像：那边的问题是<b>用错了</b> mapper，
     * 这边的问题是<b>用哪个都行</b>。）
     */
    private static final ObjectMapper JSON_SYNTAX_CHECKER = new ObjectMapper();

    private GoFormValues() {
    }

    /**
     * Go {@code GetFormValues} 的「空值丢弃」规则（{@code httpx/util.go:44-45}）。
     *
     * @return {@code null} 表示「这个 key 在 Go 侧<b>不存在</b>」→ 调用方走 tag 默认值
     */
    public static String raw(String queryValue) {
        if (queryValue == null || queryValue.isEmpty()) {
            return null;
        }
        return queryValue;
    }

    /**
     * Go {@code strconv.ParseInt(str, 10, 32)} 对应的 {@code int32} form 字段解析
     * （{@code types.go:23-24} 的 {@code page} / {@code page_size}）。
     *
     * @param rawValue    Spring {@code @RequestParam} 拿到的<b>原始字符串</b>（可能是 {@code null} 或 {@code ""}）
     * @param fieldName   Go tag 上的键名（{@code "page"} / {@code "page_size"}），只用于日志可读性
     * @param defaultValue Go tag 上的 {@code ,default=...}（{@code 1} / {@code 20}）
     * @return 解析后的值；key 缺席或值为空时返回 {@code defaultValue}
     * @throws BusinessException 解析失败 → HTTP 200 + {@code {code:5001,msg:"internal server error",data:null}}
     */
    public static int int32(String rawValue, String fieldName, int defaultValue) {
        String v = raw(rawValue);
        if (v == null) {
            return defaultValue;
        }
        try {
            // Go: strconv.ParseInt(str, 10, 32) —— base 10、32 位，越界即 error
            return Integer.parseInt(v, 10);
        } catch (NumberFormatException e) {
            // ⚠️ msg 必须**恰好**是 GetErrMsg(5001) = "internal server error"。
            // Go 侧 parse 错误不是 *CodeError ⇒ errors.As 取不到 ⇒ msg 走 GetErrMsg(code) 兜底，
            // 响应里不会出现任何解析细节。把字段名/原值拼进 msg 会造成 L1 字节级漂移，
            // 因此细节只挂到 cause 上（日志可见，wire 不可见）。
            throw parseFailure(new NumberFormatException(
                    "unmarshal field \"" + fieldName + "\": strconv.ParseInt: parsing \""
                            + v + "\": invalid syntax"));
        }
    }

    /**
     * Go {@code httpx/util.go:44-45 + :59-61} 与 {@code unmarshaler.go:835-846}：
     * <b>同名多值取首值</b>，空值丢弃，{@code []} 数组记法后缀等价于无后缀。
     *
     * <p>⚠️ <b>不能用 {@code @RequestParam String}</b>：Spring 在同名多值时会把
     * {@code String[]} 用 {@code ","} 拼成 {@code "5,3"} ⇒ 后续 {@code parseInt} 失败 ⇒ 整包 5001，
     * 而 Go 是「取第 0 个 ⇒ page=5」。
     *
     * @param request 当前请求
     * @param name    Go tag 上的键名（{@code "page"} / {@code "page_size"}）
     * @return 首个非空值；不存在时返回 {@code null}（⇒ 调用方走 tag 默认值）
     */
    public static String firstFormValue(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            // Go: httpx/util.go:59-61 —— "?page[]=5" 剥掉 [] 后缀后归到 key "page"
            values = request.getParameterValues(name + ARRAY_SUFFIX);
        }
        if (values == null) {
            return null;
        }
        for (String v : values) {
            // Go: httpx/util.go:44-45 —— 空值被整个丢弃（?page= 与 ?page 都落这里）
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * Go {@code httpx/util.go:48-53} 的 form 值个数上限：第 <b>2049</b> 个非空值即整体报错。
     *
     * <p>⚠️ 计数器 {@code n} 是<b>跨所有 key</b> 的全局计数，且<b>空值不计数</b>
     * （空值在 {@code :44-45} 就被 {@code continue} 掉了）。
     */
    public static void requireFormValueCountWithinLimit(HttpServletRequest request) {
        int n = 0;
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            for (String v : entry.getValue()) {
                if (v == null || v.isEmpty()) {
                    continue; // Go: 空值不计数
                }
                n++;
                if (n > MAX_FORM_PARAM_COUNT) {
                    // Go: return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode())
                    // ⚠️ Go 的错误里带了整份 form 的编码结果（可能极长），
                    //    但它<b>不会出现在 wire 上</b>：SetErrorHandler 只取 code/msg，
                    //    非 *CodeError ⇒ 恒 5001 + "internal server error"。
                    // ⇒ 这里只把细节挂到 cause 上，不进 msg。
                    // Go: fmt.Errorf("too many form values, error: %s", r.Form.Encode())
                    //      —— 无 %w ⇒ 是**普通 error**（不是 NumError），故 cause 用 IllegalStateException
                    throw parseFailure(new IllegalStateException(
                            "too many form values, error: " + entry.getKey()));
                }
            }
        }
    }

    /**
     * Go {@code httpx/requests.go:66-68} 的 {@code ParseJsonBody}：本端点的 JSON body
     * <b>只用于「能不能解析」这一个判定</b>，解析出来的字段<b>整体被忽略</b>
     * （字段只有 form tag、没有 json tag ⇒ {@code core/mapping/utils.go:589-597}
     * 的 {@code usingDifferentKeys} 让 json unmarshaler 整字段跳过）。
     *
     * <p>⇒ 需要复刻的只有 Go 的<b>拒绝路径</b>：
     * <ul>
     *   <li>{@code ContentLength &gt; 0} 且 {@code Content-Type} 含 {@code application/json}
     *       （{@code requests.go:153-155}）才检查；否则 Go 根本不读 body。</li>
     *   <li>body <b>不是合法 JSON</b> ⇒ 语法错误 ⇒ 5001。</li>
     *   <li>body 合法但<b>顶层不是对象</b>（{@code null} / 数组 / 数字 / 字符串）⇒ 5001。</li>
     *   <li>body 是<b>对象</b> ⇒ 通过（字段被忽略）。</li>
     * </ul>
     *
     * <p>⚠️ <b>不</b>开 {@code FAIL_ON_TRAILING_TOKENS}：Go 用的是
     * {@code json.Decoder.Decode}（{@code core/jsonx/json.go:74-77}），
     * 它<b>只读一个值</b>、<b>不</b>拒绝尾随内容 ⇒ {@code {"a":1} garbage} 在 Go 是<b>通过</b>的。
     * 开了那个特性反而会造出一条 Go 没有的拒绝路径。
     */
    public static void requireParsableJsonBody(HttpServletRequest request) {
        // Go: httpx/requests.go:153-155 withJsonBody(r)
        if (request.getContentLength() <= 0) {
            return;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains("application/json")) {
            return;
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readAllBytes();
        } catch (IOException e) {
            throw parseFailure(new IllegalArgumentException("read json body failed: " + e.getMessage(), e));
        }
        JsonNode node;
        try {
            node = JSON_SYNTAX_CHECKER.readTree(body);
        } catch (Exception e) {
            // Go: jsonx.UnmarshalFromReader 语法错误 → "unexpected EOF" 之类
            // Go: jsonx.formatError → fmt.Errorf("string: `%s`, error: `%w`", ...) ⇒ 带包装
            throw parseFailure(new IllegalArgumentException("json body parse failed: " + e.getMessage(), e));
        }
        if (node == null || !node.isObject()) {
            // Go: unmarshaler.Unmarshal(m, v) —— m 不是 map 时报
            //      "type mismatch" / "unsupported type on setting field value"
            throw parseFailure(new IllegalStateException("json body must be an object"));
        }
    }

    /**
     * 构造 Go 语义的解析失败（{@code httpx.Parse} 返回的 error）。
     *
     * <p>⚠️ 这类错误<b>不是</b> {@code *hotcommon.CodeError} ⇒ Go 的
     * {@code SetErrorHandler} 取不到 code ⇒ 恒 {@code 5001 + "internal server error"}，
     * HTTP <b>200</b>。
     */
    private static BusinessException parseFailure(Throwable cause) {
        int code = HotErrorCode.ERROR_SERVER_COMMON.getCode();
        BusinessException failure = new BusinessException(code, HotErrorMessages.getMsg(code));
        failure.initCause(cause);
        return failure;
    }
}
