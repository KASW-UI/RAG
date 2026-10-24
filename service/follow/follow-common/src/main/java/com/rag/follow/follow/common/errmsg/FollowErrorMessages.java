package com.rag.follow.follow.common.errmsg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * follow 域 code → msg 查表 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/follow/common/errmsg.go:55-74}（{@code var codeMsg}）
 * 与 {@code errmsg.go:89-95}（{@code func GetErrMsg}）。
 *
 * <p><b>Go 端行为（必须保留）</b>：
 * <pre>
 * func GetErrMsg(code int) string {
 *     msg, ok := codeMsg[code]
 *     if !ok {
 *         return codeMsg[Error]      // ← 未知码回退到 500 号的文案 "FAIL"
 *     }
 *     return msg
 * }
 * </pre>
 * 「未知码返回 {@code "FAIL"}」是客户端可观察的：follow-api 的
 * {@code followcenter.go:48,55} 直接把 {@code GetErrMsg(...)} 的结果写进 HTTP envelope 的
 * {@code msg} 字段，follow-rpc 的 {@code successBaseResp()} 也用它填 {@code BaseResp.msg}
 * （{@code rpc/internal/logic/helper.go:61}）。
 * 所以 {@link #getMsg(int)} 必须回退，<b>不能</b>返回 {@code null}、空串或码的字符串。
 *
 * <p><b>键类型说明</b>：Go 用 {@code map[int]string}；这里用 {@code Map<Integer, String>}，
 * 写入顺序按 Go {@code codeMsg} 的书写顺序（{@link LinkedHashMap}），便于 review 时逐行对照，
 * 并由 {@code FollowErrorMessagesContractTest#tableOrderMatchesGoSourceOrder} 钉死。
 *
 * <p><b>本域整表英文</b>：16 条文案无一例外是英文小写短语
 * （{@code "internal server error"} / {@code "invalid parameter"} / {@code "user not found"}）。
 * 与 favorite 域的「中文为主」形态、users/article 域的整表中文都不同 ——
 * 别按邻近域的直觉补中文。
 */
public final class FollowErrorMessages {

    private FollowErrorMessages() {
        // 工具类，禁止 new
    }

    /** 对应 Go {@code codeMsg} 整张表（16 条）；对外只读。 */
    public static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // === 通用（Go errmsg.go:56-57）===
        map.put(FollowErrorCode.SUCCESS.getCode(), "OK");
        map.put(FollowErrorCode.ERROR.getCode(), "FAIL");
        // === 通用错误码（Go errmsg.go:59-67）===
        map.put(FollowErrorCode.CODE_SERVER_BUSY.getCode(), "service busy");
        map.put(FollowErrorCode.ERROR_SERVER_COMMON.getCode(), "internal server error");
        map.put(FollowErrorCode.ERROR_NOT_FOUND.getCode(), "record not found");
        map.put(FollowErrorCode.ERROR_INVALID_PARAM.getCode(), "invalid parameter");
        map.put(FollowErrorCode.ERROR_UNAUTHORIZED.getCode(), "unauthorized");
        map.put(FollowErrorCode.ERROR_FORBIDDEN.getCode(), "forbidden");
        map.put(FollowErrorCode.ERROR_ALREADY_EXIST.getCode(), "already exists");
        map.put(FollowErrorCode.ERROR_DB_SELECT.getCode(), "database query failed");
        map.put(FollowErrorCode.ERROR_DB_UPDATE.getCode(), "database update failed");
        // === 用户（Go errmsg.go:69）===
        map.put(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode(), "user not found");
        // === 关注（Go errmsg.go:70-73）===
        map.put(FollowErrorCode.ERROR_SELF_RELATION.getCode(), "self relation is not allowed");
        map.put(FollowErrorCode.ERROR_RELATION_BLOCKED.getCode(), "blocked relation exists");
        map.put(FollowErrorCode.ERROR_FOLLOW_NOT_FOUND.getCode(), "follow relation not found");
        map.put(FollowErrorCode.ERROR_BLOCK_NOT_FOUND.getCode(), "block relation not found");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code func GetErrMsg(code int) string}：未知码回退到 {@code "FAIL"}。
     *
     * <p><b>不能用 {@code getOrDefault(code, null)}</b> —— 那会让上游 handler 把
     * {@code null} 写进 HTTP envelope 的 {@code msg}，客户端拿到 {@code {"code":…, "msg":null}}，
     * 与 Go 的 {@code {"code":…, "msg":"FAIL"}} 不同。这是 L1 外部契约。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg == null) {
            // Go: return codeMsg[Error] —— 500 号的文案，即 "FAIL"
            return MSG_BY_CODE.get(FollowErrorCode.ERROR.getCode());
        }
        return msg;
    }
}