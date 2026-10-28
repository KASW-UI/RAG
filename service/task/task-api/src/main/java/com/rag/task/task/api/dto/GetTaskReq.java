package com.rag.task.task.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 请求体 —— 对应 Go {@code api/internal/types/types.go:52-54}：
 *
 * <pre>
 *   type GetTaskReq struct {
 *       Userid UserIDParam `json:"id"`
 *   }
 * </pre>
 *
 * <h2>本类承载「{@code UserIDParam}」的两段逻辑，不只是一个字符串字段</h2>
 *
 * <p>Go 侧 {@code UserIDParam}（{@code types.go:14}）是一个 {@code string} 命名类型，
 * 带两个方法，<b>两段都必须搬</b>，且它们的<b>报错时机不同</b>：
 * <table border="1">
 *   <caption>UnmarshalJSON 与 Int64 的分工</caption>
 *   <tr><th>段</th><th>Go 出处</th><th>判据</th><th>失败文案</th></tr>
 *   <tr>
 *     <td>① 反序列化</td>
 *     <td>{@code types.go:16-36} {@code UnmarshalJSON}</td>
 *     <td>空 / {@code null} → {@code ""}；先试 string（<b>TrimSpace</b>），
 *         再试 json.Number（<b>保留字面量文本</b>），都不成 → 报错</td>
 *     <td>{@code "id must be a string or integer"}</td>
 *   </tr>
 *   <tr>
 *     <td>② 取值</td>
 *     <td>{@code types.go:38-50} {@code Int64}</td>
 *     <td>再 TrimSpace → 空则报错；{@code strconv.ParseInt(v,10,64)}；
 *         失败或 {@code <=0} → 报错</td>
 *     <td>{@code "user_id invalid"}</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ 为什么①必须发生在「反序列化时」而不是延迟到取值时</h2>
 * Go 的 {@code UnmarshalJSON} 返回 error 会让 {@code decoder.Decode(&req)} 整段失败
 * （{@code gettaskhandler.go:21-24}）⇒ 报文是 {@code 400} +
 * {@code "id must be a string or integer\n"}。
 * <p>若把①拖到 {@link #userIdOrThrow()} 里做，{@code {"id":true}} 会变成
 * {@code "user_id invalid"} —— <b>状态码相同、文案不同</b>，仍是可观察漂移。
 * ⇒ 本类在 {@link #setId(Object)}（Jackson 的写入入口）里就完成①并抛异常，
 * 由 {@code advice/GlobalExceptionHandler} 接住。
 *
 * <h2>⚠️ setter 形参必须是 {@code Object}</h2>
 * Go 的 {@code UnmarshalJSON} 拿到的是<b>原始 JSON token 字节</b>，两种形态都要试。
 * 写成 {@code String} 会让 Jackson 先做类型强转，{@code {"id":123}} 直接被判为类型不匹配。
 * 声明 {@code Object} 让 Jackson<b>不做任何强转</b>，由本类完全接管判定。
 *
 * <h2>⚠️ 为什么「数字」要保留文本而不是直接 {@code longValue()}</h2>
 * Go 侧存的是 json.Number 的<b>字面量文本</b>（{@code types.go:31} {@code numeric.String()}），
 * 浮点写法则会把 {@code "1.5"} 原样存下，交给②的 {@code ParseInt} 拒掉。
 * 若这里直接 {@code longValue()}，{@code {"id":1.5}} 会变成 uid=1 ⇒
 * <b>凭空多出一批成功响应</b>。
 * ⇒ 数字一律存 {@code String.valueOf(...)}：整型得到原样数字，浮点型得到
 * {@code "1.5"} / {@code "1.0E20"} 这类<b>必定带 '.' 或 'E'} 的文本 ⇒ ②必然拒。
 * 两段合起来的可观察结果与 Go 一致，且不需要在①里特判「是不是浮点」。
 *
 * <h2>⚠️ 未知字段被忽略（不是本类的功劳，是必须<b>不</b>去配的东西）</h2>
 * Go 的 handler 用裸 {@code json.NewDecoder(r.Body)}（{@code gettaskhandler.go:19-20}），
 * <b>没有</b> {@code DisallowUnknownFields} ⇒ {@code {"id":"1","zzz":2}} 在 Go 侧成功。
 * Boot 的 Jackson 默认也是忽略，两者一致；此处显式声明只是把该约定钉在代码上，
 * 防止将来有人给本模块打开 FAIL_ON_UNKNOWN_PROPERTIES 时无声改变契约。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetTaskReq {

    /** Go {@code types.go:41,46} 的错误文案（②的唯一文案）。 */
    public static final String USER_ID_INVALID = "user_id invalid";

    /** Go {@code types.go:35} 的错误文案（①的唯一文案）。 */
    public static final String ID_MUST_BE_STRING_OR_INTEGER = "id must be a string or integer";

    /**
     * 归一化后的 {@code id} 文本；{@code null} / 缺席 / {@code "null"} 字面量都落成 {@code ""}。
     *
     * <p>用 {@code String} 而不是原始 {@code Object}：①的所有判定都在
     * {@link #setId(Object)} 里做完并把结果存成文本，②只面对文本。
     */
    @JsonProperty("id")
    private String id = "";

    public GetTaskReq() {
    }

    /**
     * Jackson 的写入入口 —— 对应 Go {@code types.go:16-36} 的 {@code UnmarshalJSON}。
     *
     * @param raw Jackson 绑定后的原始值（{@code String} / 任意 {@code Number} /
     *            {@code Boolean} / {@code List} / {@code Map} / {@code null}）
     * @throws BadIdTypeException 既不是字符串也不是数字 —— Go {@code types.go:35}
     */
    @JsonProperty("id")
    public void setId(Object raw) {
        this.id = normalize(raw);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * 取 uid —— 对应 Go {@code types.go:38-50} 的 {@code Int64()}。
     *
     * <p>三条失败路径（空 / 非法字面量 / {@code <=0}）<b>共用同一个文案</b>
     * {@code "user_id invalid"}，这是 Go 的原样行为，<b>不要</b>为了排障把它们拆成不同文案。
     *
     * @throws IllegalArgumentException 任一失败路径；由 advice 映成 400 text/plain
     */
    public long userIdOrThrow() {
        String value = id == null ? "" : id.trim(); // Go types.go:39 的 TrimSpace
        if (value.isEmpty()) {                     // Go types.go:40-42
            throw new IllegalArgumentException(USER_ID_INVALID);
        }

        final long uid;
        try {
            // Go strconv.ParseInt(v, 10, 64)：
            //   接受可选的 '+'；拒绝 "" / " 12"(已 trim) / "1.5" / "1.0E20" / 超 long 范围。
            //   Java Long.parseLong 的这几条判据与之一致（含 '+'），不需额外处理。
            uid = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(USER_ID_INVALID); // Go types.go:45-46
        }
        if (uid <= 0) {
            throw new IllegalArgumentException(USER_ID_INVALID); // Go types.go:45-47
        }
        return uid;
    }

    // ============== ① 的实现 ==============

    private static String normalize(Object raw) {
        if (raw == null) {
            return "";                              // Go types.go:18-21：null → ""
        }
        if (raw instanceof String s) {
            return s.trim();                        // Go types.go:24-27
        }
        if (raw instanceof Number n) {
            return numberLiteral(n);                // Go types.go:29-33
        }
        // Boolean / 数组 / 对象：Go types.go:35
        throw new BadIdTypeException(ID_MUST_BE_STRING_OR_INTEGER);
    }

    /**
     * 把一个 JSON 数字还原成 Go 侧的<b>字面量文本</b>。
     *
     * <p>整型给原样数字；浮点给 {@code String.valueOf} 的结果，<b>必定</b>带
     * {@code '.'} 或 {@code 'E'}（Java 的 {@code Double.toString} 对非整数绝不会省略小数点），
     * 于是 {@link #userIdOrThrow()} 的 {@code ParseInt} 必然拒绝 —— 与 Go 对
     * {@code 1.5} / {@code 1.0} / {@code 1e2} 的处理逐条一致。
     */
    private static String numberLiteral(Number n) {
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
            return String.valueOf(n.longValue());
        }
        return String.valueOf(n); // BigInteger / BigDecimal / Double / Float
    }

    /**
     * 「{@code id} 既不是字符串也不是数字」—— 对应 Go {@code types.go:35} 的
     * {@code errors.New("id must be a string or integer")}。
     *
     * <p>⚠️ <b>必须是独立类型，不能用 {@code IllegalArgumentException}</b>：
     * advice 靠这个类型在 {@code HttpMessageNotReadableException} 的 cause 链里
     * 找回 Go 的原文（Jackson 会把 setter 抛出的异常包一层，
     * 直接用 {@code e.getMessage()} 会拿到 "JSON parse error: ..." 那种包装文案）。
     */
    public static class BadIdTypeException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public BadIdTypeException(String message) {
            super(message);
        }
    }
}