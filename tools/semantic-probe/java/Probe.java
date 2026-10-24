import com.rag.message.message.api.config.MessageGoStyleCoercion;
import com.rag.message.message.api.dto.AdminSendNotificationReq;
import com.rag.message.message.api.dto.AdminSendNotificationResp;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.dto.UserSendChatReq;
import com.rag.users.common.http.GoRequiredFieldException;
import com.rag.users.common.http.GoStyleRequiredFields;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;

/**
 * RAG 侧解析语义探针 —— 用项目<b>真实的</b> DTO 与<b>真实的</b> Jackson 3 mapper。
 *
 * <p>⚠️ 本文件是 AGENTS.md §3.3「两侧同时跑的探针」的 RAG 半边（另一半是 {@code ../go/main.go}）。
 * 定位是<b>回归守门</b>：DRIFT-MSGAPI-001/002 已于 2026-10-22 修完，
 * 两侧的一致率是<b>预期基线</b>，不是「现在还有 N 个 bug」。
 *
 * <h3>五条踩过的坑（改动本文件前务必先读）</h3>
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
 *   <li><b>值比对的格式必须两侧逐字相同</b>：整数用十进制无分隔、bool 用
 *       {@code true}/{@code false}、map 按<b>键排序</b>后拼 {@code k=v}。
 *       格式不一致会被误判成漂移（Go 侧见 main.go 的 mapStr）。</li>
 *   <li><b>map 的 null 与「map 本身为 null」要分开打印</b>：Go 侧 mapStr(nil) 返回
 *       {@code "null"}，本侧必须一致，否则 B 组会出现假红。</li>
 * </ol>
 *
 * <h3>2026-10-23 扩充</h3>
 * 补 A / B / C / D / X 五组（A~D 覆盖 DRIFT-MSGAPI-005~008，X 覆盖出参序列化面），
 * 并把判定从「只比 ACCEPTED/REJECTED」升级为<b>同时比对解析出的值</b>。
 * X1/X2 是<b>已知签字偏离 SD-2026-10-23-03</b>，在 run-parity.sh 的 known-dev 清单里，
 * 预期为红且不计入失败。
 */
public class Probe {

    /** 项目真实的 mapper：共享 GoStyleCoercion + message 域三条补强。 */
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
    static Object read(String json, Class<?> c) {
        try {
            Object o = mapper().readValue(json, c);
            if (o instanceof GoStyleRequiredFields g) {
                // ⚠️ 坑 ②：漏掉这行，「必填缺席」类用例会假通过
                g.assertGoRequiredFields();
            }
            return o;
        } catch (GoRequiredFieldException e) {
            throw new ProbeRejected(firstLine(e.getMessage()));
        } catch (Exception e) {
            // 带上原因：漂移复现时表里要能直接看出 RAG 为什么拒，而不是只有一个 REJECTED。
            throw new ProbeRejected(firstLine(e.getMessage()));
        }
    }

    static void t(String label, Class<?> c, String json) {
        try {
            read(json, c);
            System.out.printf("%s|ACCEPTED%n", label);
        } catch (ProbeRejected e) {
            System.out.printf("%s|REJECTED  %s%n", label, e.getMessage());
        }
    }

    /**
     * 与解析出的值一并打印（对照 Go 侧 tv()）。
     *
     * <p>为什么不能只比 ACCEPTED/REJECTED：首轮探针正是只比判定，
     * 于是「两侧都接受、但落的值不同」这一类漂移一条都抓不到
     * （{@code affected} 的出参类型漂移就是靠补这一面才暴露的）。
     */
    static <T> void tv(String label, Class<T> c, String json, Function<T, String> extract) {
        try {
            T o = c.cast(read(json, c));
            System.out.printf("%s|ACCEPTED %s%n", label, extract.apply(o));
        } catch (ProbeRejected e) {
            System.out.printf("%s|REJECTED  %s%n", label, e.getMessage());
        }
    }

    /** 出参序列化（对照 Go 侧 ts()）。用<b>同一个真实 mapper</b>，不 new 第二个。 */
    static void ts(String label, Object v) {
        try {
            System.out.printf("%s|ACCEPTED %s%n", label, mapper().writeValueAsString(v));
        } catch (Exception e) {
            System.out.printf("%s|REJECTED  %s%n", label, firstLine(e.getMessage()));
        }
    }

    /** Go 侧 mapStr 的逐字镜像：null → "null"，否则按键排序拼 {k=v,...}。 */
    static String mapStr(Map<String, String> m) {
        if (m == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(m).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append('}').toString();
    }

    static AdminSendNotificationResp notifResp(long affected) {
        AdminSendNotificationResp r = new AdminSendNotificationResp();
        r.setAffected(affected);
        return r;
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

        // ══════════════════════════════════════════════════════════════════
        // A 组（2026-10-23）：布尔字段收 JSON **数字** —— DRIFT-MSGAPI-005
        // Go 出处：core/mapping/unmarshaler.go processFieldPrimitiveWithJSONNumber
        //        的 `switch typeKind` 无 reflect.Bool 分支 ⇒ default ⇒ 一律 REJECTED。
        // 业务面：AdminSendNotificationReq.broadcast 决定「定向」还是「**全量广播**」。
        // ══════════════════════════════════════════════════════════════════
        tv("A1 unread_only 收 JSON 数字 1", PageReq.class, "{\"unread_only\":1}", p -> "unread_only=" + p.unreadOnly());
        tv("A2 unread_only 收 JSON 数字 0", PageReq.class, "{\"unread_only\":0}", p -> "unread_only=" + p.unreadOnly());
        tv("A3 unread_only 收 JSON 数字 2", PageReq.class, "{\"unread_only\":2}", p -> "unread_only=" + p.unreadOnly());
        tv("A4 unread_only 收 JSON 数字 1.0", PageReq.class, "{\"unread_only\":1.0}", p -> "unread_only=" + p.unreadOnly());
        tv("A5 unread_only 收 JSON 数字 1.5", PageReq.class, "{\"unread_only\":1.5}", p -> "unread_only=" + p.unreadOnly());
        tv("A7 broadcast 收 JSON 数字 1", AdminSendNotificationReq.class, "{\"broadcast\":1,\"title\":\"t\",\"content\":\"c\"}", n -> "broadcast=" + n.broadcast());
        tv("A8 broadcast 收 JSON 数字 0", AdminSendNotificationReq.class, "{\"broadcast\":0,\"title\":\"t\",\"content\":\"c\"}", n -> "broadcast=" + n.broadcast());

        // ══════════════════════════════════════════════════════════════════
        // B 组：map[string]string 的**元素级**判定 —— DRIFT-MSGAPI-006 / -007
        // Go 出处：core/mapping/unmarshaler.go:385-417 generateMap 的 default 分支
        //   json.Number → setValueFromString ⇒ **数字收下并字符串化**
        //   bool       → 要求 elem kind == Bool ⇒ 对 string map 报错
        //   nil/其它   → 要求 elem kind == 值 kind ⇒ Invalid ≠ String ⇒ 报错
        // ⚠️ 与顶层标量字段的判据**不同**，这正是 006/007 的全部根因。
        // ══════════════════════════════════════════════════════════════════
        tv("B1 extra 值收 JSON 数字 1", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":1}}", n -> "extra=" + mapStr(n.extra()));
        tv("B2 extra 值收 JSON 数字 1.5", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":1.5}}", n -> "extra=" + mapStr(n.extra()));
        tv("B3 extra 值显式 null", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":null}}", n -> "extra=" + mapStr(n.extra()));
        tv("B4 extra 值收 JSON bool", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":true}}", n -> "extra=" + mapStr(n.extra()));
        tv("B5 extra 值收 JSON 空串", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":\"\"}}", n -> "extra=" + mapStr(n.extra()));
        tv("B6 extra 值收 JSON 数组", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":[]}}", n -> "extra=" + mapStr(n.extra()));
        tv("B7 extra 键收 JSON 数字", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"1\":\"v\"}}", n -> "extra=" + mapStr(n.extra()));
        tv("B8 extra 整体收 JSON 字符串", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":\"x\"}", n -> "extra=" + mapStr(n.extra()));
        // B9/B10 钉住「**保留原始字面量**」而不是数值规范化（Go 走 json.Number.String()）
        tv("B9 extra 值收 JSON 数字 1.0", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":1.0}}", n -> "extra=" + mapStr(n.extra()));
        tv("B10 extra 值收 JSON 数字 1e2", AdminSendNotificationReq.class, "{\"title\":\"t\",\"content\":\"c\",\"extra\":{\"k\":1e2}}", n -> "extra=" + mapStr(n.extra()));

        // ══════════════════════════════════════════════════════════════════
        // C 组：,optional 标量字段的**显式 null** —— DRIFT-MSGAPI-008
        // Go 出处：core/mapping/unmarshaler.go:854-863
        //     if mapValue == nil { if opts.optional() { return nil }; return mustn't-be-nil }
        // ⚠️ 与「必填字段收 null 必须仍被拒」是两条独立路径。
        // ══════════════════════════════════════════════════════════════════
        tv("C1 offset 显式 null", PageReq.class, "{\"offset\":null}", p -> "offset=" + p.offset());
        tv("C2 limit 显式 null", PageReq.class, "{\"limit\":null}", p -> "limit=" + p.limit());
        tv("C3 unread_only 显式 null", PageReq.class, "{\"unread_only\":null}", p -> "unread_only=" + p.unreadOnly());
        tv("C4 kind 显式 null", AdminSendNotificationReq.class, "{\"kind\":null,\"title\":\"t\",\"content\":\"c\"}", n -> "kind=" + n.kind());
        tv("C5 ,string optional target_id 显式 null", AdminSendNotificationReq.class, "{\"target_id\":null,\"title\":\"t\",\"content\":\"c\"}", n -> "target_id_present=" + (n.targetId() != null && n.targetId() != 0L));
        tv("C6 offset 收 int32 上限", PageReq.class, "{\"offset\":2147483647}", p -> "offset=" + p.offset());
        tv("C7 offset 收 int32 上限+1", PageReq.class, "{\"offset\":2147483648}", p -> "offset=" + p.offset());
        tv("C8 offset 收负数 -1", PageReq.class, "{\"offset\":-1}", p -> "offset=" + p.offset());

        // ══════════════════════════════════════════════════════════════════
        // D 组：`,string` 值解析的边界形态（首轮 README 自认一条都没覆盖）
        // ══════════════════════════════════════════════════════════════════
        tv("D1 ,string 收 \"+123\"", ConversationMessagesReq.class, "{\"conversation_id\":\"+123\"}", c -> "conversation_id=" + c.conversationId());
        tv("D2 ,string 收 \"0012\"", ConversationMessagesReq.class, "{\"conversation_id\":\"0012\"}", c -> "conversation_id=" + c.conversationId());
        tv("D3 ,string 收 \" 12\"", ConversationMessagesReq.class, "{\"conversation_id\":\" 12\"}", c -> "conversation_id=" + c.conversationId());
        tv("D4 ,string 收空串 \"\"", ConversationMessagesReq.class, "{\"conversation_id\":\"\"}", c -> "conversation_id=" + c.conversationId());
        tv("D5 ,string 收 \"0x10\"", ConversationMessagesReq.class, "{\"conversation_id\":\"0x10\"}", c -> "conversation_id=" + c.conversationId());
        tv("D6 ,string 收 1e2", ConversationMessagesReq.class, "{\"conversation_id\":1e2}", c -> "conversation_id=" + c.conversationId());

        // ══════════════════════════════════════════════════════════════════
        // X 组：**出参**序列化 —— 首轮完全没有这一面
        // ⚠️ X1/X2 是**已知签字偏离 SD-2026-10-23-03**（见 run-parity.sh 的 known-dev 清单）：
        //    Go tag 无 `,string` ⇒ 输出数字；RAG 按签字结论输出字符串。预期为红，不计入失败。
        // ══════════════════════════════════════════════════════════════════
        ts("X1 出参 affected=3", notifResp(3));
        ts("X2 出参 affected=0", notifResp(0));
    }

    /** 把「被拒」建模成受检异常，避免 t/tv 内部自造 null 返回值造成假通过。 */
    static final class ProbeRejected extends RuntimeException {
        ProbeRejected(String message) {
            super(message);
        }
    }
}