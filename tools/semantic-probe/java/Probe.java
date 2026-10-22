import com.rag.message.message.api.config.MessageGoStyleCoercion;
import com.rag.message.message.api.dto.AdminSendNotificationReq;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.dto.UserSendChatReq;
import com.rag.users.common.http.GoRequiredFieldException;
import com.rag.users.common.http.GoStyleRequiredFields;
import tools.jackson.databind.json.JsonMapper;

/**
 * RAG 侧解析语义探针 —— 用项目<b>真实的</b> DTO 与<b>真实的</b> Jackson 3 mapper。
 *
 * <p>⚠️ 本文件是 AGENTS.md §3.3「两侧同时跑的探针」的 RAG 半边（另一半是 {@code ../go/main.go}）。
 * 定位是<b>回归守门</b>：DRIFT-MSGAPI-001/002 已于 2026-10-22 修完，
 * 两侧 23/23 一致是<b>预期基线</b>，不是「现在还有 23 个 bug」。
 *
 * <h3>三条踩过的坑（改动本文件前务必先读）</h3>
 * <ol>
 *   <li><b>不能自己 new 一个 mapper。</b>必须用项目真实的
 *       {@link MessageGoStyleCoercion}，否则测的是「代码写成了这样」而不是「服务真实行为」。
 *       Jackson 3 的 coercion 只能在 builder 上配，{@code JsonMapper.builder(...)}
 *       <b>没有</b>接受 lambda 的重载，必须先拿 builder 再 applyTo。</li>
 *   <li><b>反序列化之后必须调 {@code assertGoRequiredFields()}。</b>
 *       go-zero 的必填校验在 {@code UnmarshalJsonReader} 内部一次完成；
 *       RAG 侧把它放在 Controller 层（{@link GoStyleRequiredFields}）。
 *       漏掉这步 ⇒ 所有「必填缺席」类用例在 RAG 侧<b>假通过</b>（探针永远绿，守门失效）。</li>
 *   <li><b>用例标签必须与 Go 侧逐字一致</b>（含空格与引号），因为 run-parity.sh 按
 *       {@code |} 之前的文本配对两侧结果。</li>
 * </ol>
 */
public class Probe {

    /** 项目真实的 mapper：共享 GoStyleCoercion + message 域两条补强。 */
    static JsonMapper mapper() {
        var b = JsonMapper.builder();
        MessageGoStyleCoercion.applyTo(b);
        return b.build();
    }

    /** 与 Go 侧一致：原因只取第一行（Go 侧也是 strings.Split(err, "\n")[0]）。 */
    static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return (i < 0) ? s : s.substring(0, i);
    }

    /** 与 Go 的 UnmarshalJsonReader 对齐：反序列化 + go-zero 必填校验，一步完成。 */
    static void t(String label, Class<?> c, String json) {
        String status;
        try {
            Object o = mapper().readValue(json, c);
            if (o instanceof GoStyleRequiredFields g) {
                // ⚠️ 坑 ②：漏掉这行，「必填缺席」类用例会假通过
                g.assertGoRequiredFields();
            }
            status = "ACCEPTED";
        } catch (GoRequiredFieldException e) {
            status = "REJECTED  " + firstLine(e.getMessage());
        } catch (Exception e) {
            // 带上原因：漂移复现时表里要能直接看出 RAG 为什么拒，而不是只有一个 REJECTED。
            status = "REJECTED  " + firstLine(e.getMessage());
        }
        System.out.printf("%s|%s%n", label, status);
    }

    public static void main(String[] a) {
        String base = "{\"conversation_id\":\"1\"";

        // ── Go 的 ,string 是「入参宽松（string 与 number 都收）、出参才严格」 ──
        // 根因：encoding/json 的 `type Number string` ⇒ json.Number 的 reflect.Kind() 是 string。
        t(",string int64 收 JSON 字符串 \"123\"", ConversationMessagesReq.class, "{\"conversation_id\":\"123\"}");
        t(",string int64 收 JSON 数字 123", ConversationMessagesReq.class, "{\"conversation_id\":123}");
        t(",string int64 收 JSON 数字 1.5", ConversationMessagesReq.class, "{\"conversation_id\":1.5}");
        t(",string int64 收 JSON 数字 1.0", ConversationMessagesReq.class, "{\"conversation_id\":1.0}");
        t(",string int64 收 JSON bool", ConversationMessagesReq.class, "{\"conversation_id\":true}");
        t(",string int64 收超范围大整数", ConversationMessagesReq.class, "{\"conversation_id\":12345678901234567890123}");

        // ── 无 ,string 的字段：Jackson 默认会放行字符串强转，Go 直接 type mismatch ──
        t("无 ,string int32 收 JSON 字符串 \"10\"", PageReq.class, "{\"offset\":\"10\"}");
        t("无 ,string bool 收 JSON 字符串 \"true\"", PageReq.class, "{\"unread_only\":\"true\"}");
        t("无 ,string int32 收 JSON 数字 10", PageReq.class, "{\"offset\":10}");
        t("无 ,string int32 收 JSON 数字 1.5", PageReq.class, "{\"offset\":1.5}");
        t("无 ,string int32 limit 收 JSON 字符串 \"20\"", PageReq.class, "{\"limit\":\"20\"}");
        t("无 ,string int32 limit 收 JSON 数字 20", PageReq.class, "{\"limit\":20}");

        // ── String 字段 / 未知字段 / 必填 ──
        t("String 字段 content 收 JSON 数字", UserSendChatReq.class, base + ",\"content\":123}");
        t("未知字段被忽略", UserSendChatReq.class, base + ",\"content\":\"x\",\"nope\":1}");
        t("required 缺席(conversation_id)", ConversationMessagesReq.class, "{\"content\":\"x\"}");
        t("required 显式 null(conversation_id)", ConversationMessagesReq.class, "{\"conversation_id\":null,\"content\":\"x\"}");
        // ⚠️ 这里刻意用 recipient_id（不是 conversation_id）：UserSendChatReq 的两个 id 都是
        //    ,optional，只有 content 必填；用 conversation_id 会让人误以为在测 conversation_id 必填。
        //    Go 侧对应 body 走 StrReq（见 go/main.go），两侧 body 不同但判定维度相同：content 缺席。
        t("content 缺席(必填)", UserSendChatReq.class, "{\"recipient_id\":\"1\"}");

        // ── admin 通知：extra 的显式 null 合法（,optional ≠ ,omitempty）──
        t("extra 显式 null", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":null}");
        t("extra 缺席", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\"}");
        t("admin bool 收 JSON 字符串 \"true\"", AdminSendNotificationReq.class, "{\"broadcast\":\"true\",\"title\":\"t\",\"content\":\"c\"}");
        t("admin int32 kind 收 JSON 字符串 \"1\"", AdminSendNotificationReq.class, "{\"kind\":\"1\",\"title\":\"t\",\"content\":\"c\"}");
        // ── ,string + ,optional 组合：types.go:14 target_id ──
        t(",string optional target_id 收 JSON 字符串 \"9\"", AdminSendNotificationReq.class, "{\"target_id\":\"9\",\"title\":\"t\",\"content\":\"c\"}");
        t(",string optional target_id 收 JSON 数字 9", AdminSendNotificationReq.class, "{\"target_id\":9,\"title\":\"t\",\"content\":\"c\"}");
    }
}