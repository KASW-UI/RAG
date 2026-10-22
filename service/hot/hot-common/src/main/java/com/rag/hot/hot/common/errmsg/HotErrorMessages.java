package com.rag.hot.hot.common.errmsg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * hot 域 code → msg 文案表 —— 1:1 对应 Go 端
 * {@code service/hot/common/errmsg.go:48-61}（{@code var codeMsg}）
 * 与 {@code errmsg.go:76-82}（{@code func GetErrMsg}）。
 *
 * <p><b>Go 端行为（必须保留）</b>：
 * <pre>
 * func GetErrMsg(code int) string {
 *     msg, ok := codeMsg[code]
 *     if !ok {
 *         return codeMsg[Error]   // ← 未知码回退到 "FAIL"，不是空串、也不是 code 本身
 *     }
 *     return msg
 * }
 * </pre>
 * 「未知码返回 {@code "FAIL"}」是客户端可观察的：Go hot-api 的
 * {@code httpx.SetErrorHandler}（{@code hotcenter.go:53-66}）在错误不是
 * {@code *CodeError} 时就走这条回退，把 {@code code=5001 / msg="internal server error"}
 * 写进 envelope ⇒ 回退值本身属 L1，{@link #getMsg(int)} 必须回退，
 * <b>不能</b>返回 {@code null} 或空串。
 *
 * <p><b>键类型说明</b>：Go 用 {@code map[int]string}；这里用 {@code Map<Integer, String>}，
 * 写入顺序按 Go {@code codeMsg} 的书写顺序（{@link LinkedHashMap}），
 * 便于 review 时与 Go 源码逐行对照，也让 {@code HotErrorMessagesContractTest}
 * 能断言「表内顺序与 Go 书写顺序一致」。
 *
 * <p><b>文案是英文，与 users / article 域的中文文案不同语言</b>（那是<b>不同域</b>的独立码表，
 * 不是同一套码的翻译）。hot 侧的 5002/5003/5004 与那两个域的同号码语义冲突，
 * 详见 {@link HotErrorCode} 的类注释。
 *
 * <p><b>对应 Go 什么</b>：Go 里这是个包级 {@code var codeMsg}，所有引用方都走它；
 * Java 侧改用不可变 Map，是因为 {@code static final} + 不可变包装能在并发读路径下省掉同步成本，
 * 也防止有人意外在运行时改它。
 */
public final class HotErrorMessages {

    private HotErrorMessages() {
        // 工具类，禁止 new
    }

    /**
     * 对应 Go {@code codeMsg} 整张表（11 条）；对外只读。
     *
     * <p><b>可见性：包级私有</b>，对应 Go 里 {@code var codeMsg} 也是包级私有变量。
     * 同包的契约测试可以遍历它做「码表与码常量表严格一一对应」的断言，
     * 但对外业务模块只通过 {@link #getMsg(int)} 访问，不暴露原始 map。
     */
    static final Map<Integer, String> MSG_BY_CODE;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // === 通用（Go errmsg.go:49-52）===
        map.put(HotErrorCode.SUCCESS.getCode(), "OK");
        map.put(HotErrorCode.ERROR.getCode(), "FAIL");
        map.put(HotErrorCode.CODE_SERVER_BUSY.getCode(), "service busy");
        // === 系统与基础设施（Go errmsg.go:53-60）===
        map.put(HotErrorCode.ERROR_SERVER_COMMON.getCode(), "internal server error");
        map.put(HotErrorCode.ERROR_NOT_FOUND.getCode(), "record not found");
        map.put(HotErrorCode.ERROR_INVALID_PARAM.getCode(), "invalid parameter");
        map.put(HotErrorCode.ERROR_UNAUTHORIZED.getCode(), "unauthorized");
        map.put(HotErrorCode.ERROR_FORBIDDEN.getCode(), "forbidden");
        map.put(HotErrorCode.ERROR_ALREADY_EXIST.getCode(), "already exists");
        map.put(HotErrorCode.ERROR_DB_SELECT.getCode(), "database query failed");
        map.put(HotErrorCode.ERROR_DB_UPDATE.getCode(), "database update failed");
        MSG_BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 对应 Go {@code func GetErrMsg(code int) string}（{@code errmsg.go:76-82}）。
     *
     * <p>未知码回退 {@link HotErrorCode#ERROR} 的 {@code "FAIL"}，
     * 与 Go {@code if !ok { return codeMsg[Error] }} 同向。
     */
    public static String getMsg(int code) {
        String msg = MSG_BY_CODE.get(code);
        if (msg != null) {
            return msg;
        }
        return MSG_BY_CODE.get(HotErrorCode.ERROR.getCode());
    }
}
