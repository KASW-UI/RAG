package com.rag.users.admin.api.service;

import com.rag.users.admin.api.dto.AdminInfo;
import io.grpc.Status;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * admin-api 共用 helper —— 跨 13 个 Service / Interceptor / Controller 的工具方法聚合点。
 *
 * 对应 user-api 端 UserApiSupport 同款位置与职责。
 *
 * <p>框架职责：
 * <ul>
 *   <li>{@code claimedUID}：from request attribute "userId"（由 AdminJwtAuthInterceptor 写入）。
 *       【DEAD-CODE-001 已删】原 extractAdminId 是 IdentityResolver.claimedUID 的零调用方薄包装，
 *       Go 侧无对应物。按 §3.4.1 D-2 先例删除，调用方直接用 claimedUID。</li>
 *   <li>{@code parseUid}：uid 字符串解析（6 个 Service 共用同一份实现）</li>
 *   <li>{@code fromProto(AdminInfo proto)} → DTO 转换</li>
 *   <li>{@code fromProto(UserInfo proto)} → DTO 转换（admin.proto.UserInfo，与 dto UserInfo 同名用全限定名）</li>
 * </ul>
 *
 * <p><b>2026-10-21 删除 {@code codeFromRPCError}</b>（AGENTS.md §6.2 P3）：
 * 它与 {@code GlobalExceptionHandler.mapRPCStatusToCode} 是<b>两份逐条相同的映射</b>，
 * 且经 grep 确认<b>零调用方</b>（只有自身声明与 javadoc 提到它）。
 *
 * <p>留着的实际风险不是「重复」本身，而是<b>它是一份可以被误用的错误映射</b>：
 * 任何 Service 新增 {@code catch (StatusRuntimeException)} 时若顺手调它，
 * 就会绕过该端点自己的 Go switch，落进通用默认值 —— 这正是 user-api 的
 * {@code AvatarSelectService} 已经踩过的坑（把 Go 的 {@code NOT_FOUND → 500}
 * 变成 {@code 1003}，DRIFT-003）。
 * 业务码精确映射必须在 Service 内显式写出 Go 的整个 switch，
 * 通用 fallback 只保留 advice 里那一份。
 */
@Component
public class AdminApiSupport {

    /**
     * uid 字段从 String 解析（JSON 序列化为字符串避免精度丢失）。
     *
     * <p>解析失败 / 为空 → 直接抛 {@code BusinessException(1017 请求参数错误)}。
     *
     * <p><b>⚠️ 2026-10-27 订正：原 javadoc 写的「与参考侧 {@code parseLongOrNull} →
     * {@code LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM)} 的语义 1:1」—— 这条出处
     * <b>无效</b>（AGENTS.md §3.1「不要拿代码注释当对照基准」）：「参考侧」指的是
     * <b>Java 镜像期</b>的实现，<b>不是 Go 真源</b>；而 Go 真源里
     * {@code ErrorRequestParam = 1017} <b>零调用点</b>
     * （{@code service/user/common/errmsg/errmsg.go:30} 只定义常量，
     * {@code :38-68} 的 {@code codeMsg} 里也没有它 ⇒ {@code GetErrMsg} 会回落到
     * {@code codeMsg[Error]} = {@code "FAIL"}）⇒ <b>Go 侧永远不可能返回 1017</b>。
     *
     * <p><b>Go 侧的真实语义</b>（{@code service/user/admin/api/internal/types/types.go:67-69}
     * 的 {@code Uid int64 `form:"uid,string"`}）：非法 uid 在 <b>go-zero 绑定层</b>就被拒
     * ⇒ <b>HTTP 400</b>（{@code strconv.ParseInt: parsing "abc": invalid syntax}），
     * <b>没有「业务码」这一层</b> ⇒ ⇒ 本方法抛 1017 这一层，在 Go 侧<b>没有对应物</b>。
     *
     * <p><b>⇒ 本方法在 HTTP 入口上不可达（2026-10-27 实测，不是推断）</b>：
     * <ol>
     *   <li>非数字 uid（{@code "abc"}）⇒ Jackson 反序列化就抛 {@code InvalidFormatException}
     *       ⇒ <b>400 + text/plain</b>，请求到不了本方法；</li>
     *   <li>uid 缺席 / 显式 null ⇒ {@code GoStyleRequest.normalize(req, …)} ⇒
     *       {@code assertGoRequiredFields()} ⇒ {@code GoStyleRequired.field("uid", uid)}
     *       ⇒ <b>400 {@code field "uid" is not set}</b>，同样到不了本方法；</li>
     *   <li>⇒ 故 {@code uidStr == null} 与 {@code NumberFormatException} 两个分支
     *       <b>都不会从 HTTP 入口到达</b> ⇒ ⇒ <b>1017 不进 wire</b>
     *       （⇒ {@code DRIFT-USERSCOMMON-001} 的「影响面」表原写「admin-api 那两处 ✅ 进 wire」
     *       是错的，已于 2026-10-27 订正为「不可达」）。</li>
     * </ol>
     * ⚠️ 本方法保留（防未来新增调用点绕过上面两层），但<b>它抛的 1017 在 Go 侧没有对应物</b>
     * ⇒ 若将来有端点绕过 {@code normalize} 直连本方法，<b>那才会产生新的 wire 漂移</b>
     * ⇒ 守门见 {@code AdminApiMvcPipelineTest} 的
     * {@code missingUidIs400TextPlainEndToEnd} / {@code explicitNullIsRejected} /
     * {@code nonNumericUidIsRejectedByJacksonNotByRequiredCheck}（三条都断言 rpc stub
     * {@code never()} 被调用 ⇒ 错误发生在到达业务层之前）。
     *
     * <p>曾经返回 0L 而不报错：那会把"客户端传了非法 uid"静默降级成"查询 uid=0 的用户"，
     * 客户端拿到的是 {@code 1003 用户不存在} 而不是 {@code 1017 请求参数错误}，
     * 坏请求被误分类成业务不存在，且掩盖了调用方的参数拼装 bug。
     */
    public static long parseUid(String uidStr) {
        if (uidStr == null || uidStr.isEmpty()) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getCode(),
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getMsg());
        }
        try {
            return Long.parseLong(uidStr.trim());
        } catch (NumberFormatException e) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getCode(),
                    com.rag.users.common.errmsg.ErrorCode.ERROR_REQUEST_PARAM.getMsg());
        }
    }

    /**
     * proto AdminInfo → DTO AdminInfo。
     *
     * <p><b>A-02（已修）· email 无条件输出</b>：Go 端 {@code types.AdminInfo} 的
     * {@code Email string `json:"email"`} —— <b>没有 omitempty</b>，管理员邮箱为空时
     * 序列化成 {@code "email":""}（字段出现、值为空串）。
     * 本方法此前的 {@code if (!proto.getEmail().isEmpty())} 会让字段整个消失。
     */
    public AdminInfo fromProto(com.rag.users.admin.proto.AdminInfo proto) {
        if (proto == null) return null;
        AdminInfo dto = new AdminInfo();
        dto.setUid(proto.getUid());
        dto.setUsername(proto.getUsername() == null ? "" : proto.getUsername());
        // 总是 set：Go 的 AdminInfo.email 无 omitempty，空值也要输出 ""（DTO 侧 NON_NULL 不拦空串）
        dto.setEmail(proto.getEmail() == null ? "" : proto.getEmail());
        if (proto.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>(proto.getExtraInfoMap());
            dto.setExtraInfo(map);
        }
        return dto;
    }

    /**
     * proto UserInfo → DTO UserInfo。
     *
     * <p>注：admin.proto UserInfo 多一个 score 字段，但 admin-api DTO 不暴露 score（与 admin-rpc 端 1:1 保留 Go 行为）。
     *
     * <p><b>注意与 {@link #fromProto(com.rag.users.admin.proto.AdminInfo)} 的差别</b>：
     * Go 端 {@code types.UserInfo.Email} 的 tag 是 {@code `json:"email,omitempty"`} —
     * <b>带 omitempty</b>，所以这里空值时<b>不</b> set，字段省略。
     * 两者的差别只在 email 一处，不要顺手统一。
     */
    public com.rag.users.admin.api.dto.UserInfo fromProto(com.rag.users.admin.proto.UserInfo proto) {
        if (proto == null) return null;
        com.rag.users.admin.api.dto.UserInfo dto = new com.rag.users.admin.api.dto.UserInfo();
        dto.setUid(proto.getUid());
        dto.setStatus(proto.getStatus());
        dto.setUsername(proto.getUsername() == null ? "" : proto.getUsername());
        if (!proto.getEmail().isEmpty()) dto.setEmail(proto.getEmail());
        if (proto.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>(proto.getExtraInfoMap());
            dto.setExtraInfo(map);
        }
        return dto;
    }
}
