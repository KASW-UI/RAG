package com.rag.favorite.favorite.common.errmsg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * favorite 域 code → msg 查表 —— 1:1 对应 Go 端
 * {@code Sea-RideTheWind_golang/service/favorite/common/errmsg.go:43-76}（{@code var codeMsg}）
 * 与 {@code errmsg.go:91-97}（{@code func GetErrMsg}）。
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
 * 「未知码返回 {@code "FAIL"}」是客户端可观察的：favorite-api 任何拿到未知码的分支
 * （例如 {@code api/internal/handler/favorite/get_favorite_folders_handler.go:31} 的
 * {@code Msg: favoritecommon.GetErrMsg(code)}）最终都会把 {@code "FAIL"} 呈现给用户。
 * 所以 {@link #getMsg(int)} 必须回退，<b>不能</b>返回 {@code null}、空串或码的字符串。
 *
 * <p><b>键类型说明</b>：Go 用 {@code map[int]string}；这里用 {@code Map<Integer, String>}，
 * 写入顺序按 Go {@code codeMsg} 的书写顺序（{@link LinkedHashMap}），
 * 便于 review 时与 Go 源码逐行对照，并由 {@code FavoriteErrorMessagesContractTest} 钉死。
 *
 * <p><b>本域文案是中文为主、但 5002 是英文</b>：{@code ErrorNotFound = 5002} 的文案是
 * {@code "not found"}（{@code errmsg.go:50}），不是「记录不存在」。
 * 这是 hot 域整表英文、users/article 域整表中文之外的第三种形态，别按直觉补中文。
 */
public final class FavoriteErrorMessages {

    private FavoriteErrorMessages() {
        // 工具类，禁止 new
    }

    /** 对应 Go {@code codeMsg} 整张表（24 条）；对外只读。 */
    public static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // === 通用（Go errmsg.go:44-45）===
        map.put(FavoriteErrorCode.SUCCESS.getCode(), "OK");
        map.put(FavoriteErrorCode.ERROR.getCode(), "FAIL");
        // === 通用错误码（Go errmsg.go:48-57）===
        map.put(FavoriteErrorCode.CODE_SERVER_BUSY.getCode(), "服务繁忙");
        map.put(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode(), "系统内部错误");
        map.put(FavoriteErrorCode.ERROR_NOT_FOUND.getCode(), "not found");
        map.put(FavoriteErrorCode.ERROR_INVALID_PARAM.getCode(), "参数错误");
        map.put(FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode(), "未登录或登录已失效");
        map.put(FavoriteErrorCode.ERROR_FORBIDDEN.getCode(), "无权限操作");
        map.put(FavoriteErrorCode.ERROR_ALREADY_EXIST.getCode(), "记录已存在");
        map.put(FavoriteErrorCode.ERROR_DB_SELECT.getCode(), "数据库查询失败");
        map.put(FavoriteErrorCode.ERROR_DB_UPDATE.getCode(), "数据库更新失败");
        map.put(FavoriteErrorCode.ERROR_GENERATE_ID.getCode(), "ID生成失败");
        // === 用户（Go errmsg.go:60-64）===
        map.put(FavoriteErrorCode.ERROR_USER_NOT_FOUND.getCode(), "用户不存在");
        map.put(FavoriteErrorCode.ERROR_USER_EXISTS.getCode(), "用户已存在");
        map.put(FavoriteErrorCode.ERROR_PASSWORD_INVALID.getCode(), "密码错误");
        map.put(FavoriteErrorCode.ERROR_TOKEN_INVALID.getCode(), "token无效");
        map.put(FavoriteErrorCode.ERROR_TOKEN_EXPIRED.getCode(), "token已过期");
        // === 收藏夹（Go errmsg.go:67-69）===
        map.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NOT_FOUND.getCode(), "收藏夹不存在");
        map.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EMPTY.getCode(), "收藏夹名称不能为空");
        map.put(FavoriteErrorCode.ERROR_FAVORITE_FOLDER_NAME_EXISTS.getCode(), "收藏夹名称已存在");
        // === 收藏（Go errmsg.go:72-75）===
        map.put(FavoriteErrorCode.ERROR_FAVORITE_NOT_FOUND.getCode(), "收藏记录不存在");
        map.put(FavoriteErrorCode.ERROR_FAVORITE_ALREADY_EXIST.getCode(), "该内容已收藏");
        map.put(FavoriteErrorCode.ERROR_FAVORITE_TARGET_EMPTY.getCode(), "收藏目标不能为空");
        map.put(FavoriteErrorCode.ERROR_FAVORITE_HISTORY_BLOCKED.getCode(), "收藏暂不可撤回");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code func GetErrMsg(code int) string}
     * （{@code service/favorite/common/errmsg.go:91}）：未知码回退到 {@code "FAIL"}。
     *
     * <p><b>不能用 {@code getOrDefault(code, null)}</b> —— 那会让上游 handler 把
     * {@code null} 写进 HTTP envelope 的 {@code msg}，客户端拿到 {@code {"code":…, "msg":null}}，
     * 与 Go 的 {@code {"code":…, "msg":"FAIL"}} 不同。这是 L1 外部契约。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg == null) {
            // Go: return codeMsg[Error] —— 500 号的文案，即 "FAIL"
            return MSG_BY_CODE.get(FavoriteErrorCode.ERROR.getCode());
        }
        return msg;
    }
}