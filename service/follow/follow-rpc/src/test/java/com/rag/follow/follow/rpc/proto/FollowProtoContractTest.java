package com.rag.follow.follow.rpc.proto;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.rag.follow.follow.proto.RelationReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>follow proto 的契约回归网</b> —— 把「{@code .proto} 文件两侧一致」升级为
 * 「<b>生成码的契约</b>两侧一致」。
 *
 * <h2>为什么需要它（AGENTS.md §3.4 的效力边界）</h2>
 * <p>§3.4 只保证 {@code .proto} <b>文件本身</b>两侧一致 ⇒ <b>推不出</b>：
 * <ul>
 *   <li><b>字段号</b>一致（改一个号 ⇒ wire 完全变，但文件「看着一致」）；</li>
 *   <li><b>proto3 optional 的 presence 语义</b>一致（{@code optional string} 与 {@code string}
 *       在<b>文件里只差三个词</b>，在 wire 上却是「有没有 presence」）；</li>
 *   <li><b>repeated / map</b> 的编码方式一致；</li>
 *   <li><b>枚举值</b>的编号一致（枚举值编号<b>进 wire</b>）；</li>
 *   <li><b>rpc 方法名与出入参类型</b>一致（= 服务路径 {@code /follow.FollowService/Follow}）。</li>
 * </ul>
 * ⇒ 本类把这些**全部**钉成一条断言。
 *
 * <h2>🔴 期望值是「由真源生成」的，不是手抄的</h2>
 * <p>基准文件 {@code src/test/resources/proto-contract/follow-proto-contract.expected.txt}
 * 是<b>脚本从 Go 真源的 {@code proto/follow.proto} 生成</b>的
 * （同目录 {@code generate.py}，头部记录了真源的 sha256）⇒
 * <ul>
 *   <li>手抄一份字段表 = 把真源的实现细节抄一遍 ⇒ <b>抄错时看不出错</b>
 *       （「期望值必须由真源生成」这条教训）；</li>
 *   <li>脚本生成 ⇒ 抄不错；真源变了 ⇒ 重跑脚本 ⇒ diff 直接暴露契约变更。</li>
 * </ul>
 * <p>⚠️ <b>基准文件不是手工编辑物</b>：真源 {@code .proto} 变了要重跑脚本 + 重新评审 diff。
 *
 * <h2>🔴 本类比对的是「生成码的描述符」，不是「RAG 的 .proto 文本」</h2>
 * <p>读文本只能证明「文件写了什么」；读 {@link Descriptors.Descriptor} 才能证明
 * <b>编译产物</b>的契约是什么 ⇒ 这里走 {@code RelationReq.getDescriptor().getFile()}
 * ⇒ 拿到的是<b>真正参与 wire 编码</b>的那份描述符。
 *
 * <h2>🔴 为什么本域要自己写生成脚本，而不是复用 hot 域那份</h2>
 * <p>hot 域的 {@code generate.py} 用<b>非贪婪正则</b>抓 message 体
 * （{@code message\s+(\w+)\s*\{(.*?)\}}）⇒ 遇到<b>嵌套 message</b> 时会在<b>内层</b>的
 * {@code }} 处截断。
 * <p>⚠️ <b>实测（2026-10-29 · G1 lane）</b>：对 {@code follow.proto} 会产出
 * <pre>
 * message|RecommendResp|code=1:int32|msg=2:string|target_id=1:int64|mutual_score=2:int32
 * </pre>
 * —— 内层 {@code RecommendUser} 的字段被<b>平铺进父级</b>，
 * 而父级自己的 {@code users=3:repeated RecommendUser} <b>整条丢失</b>
 * ⇒ 这样的基准 = <b>假契约</b>，且<b>恰好把本域最该钉住的那条放过了</b>。
 * <p>⇒ follow 域的 {@code generate.py} 改用<b>括号配对</b>扫描 + <b>递归</b>解析，
 * 产出 {@code message|RecommendResp.RecommendUser|...} 的独立行
 * ⇒ 与本类的 {@link Descriptors.Descriptor#getNestedTypes()} 一一对应。
 *
 * <h2>为什么放在 follow-rpc 而不是 follow-proto</h2>
 * <p>{@code follow-proto} 模块<b>一行 junit 依赖都没有</b>（pom 里只有生成代码所需的
 * protobuf / grpc）⇒ 往那儿加测试要先改 pom；放在消费方 {@code follow-rpc} 更省，
 * 且 proto 本来就是「给 rpc 用的」（与 hot / article / favorite 域同款做法）。
 *
 * <h2>⚠️ 覆盖边界</h2>
 * <ul>
 *   <li><b>不覆盖</b> gRPC 帧 / HTTP2 帧（那需要两侧同时起服务）；</li>
 *   <li><b>不覆盖</b> wire 字节级（favorite 域有 {@code tools/proto-wire-probe/favorite/}
 *       那样的字节级探针；follow 域本轮只做契约层）；</li>
 *   <li><b>不覆盖</b>「消费方式」（{@code optional} presence 的**构造点**语义）——
 *       那是 AGENTS.md §3.4 效力边界之外的**取证缺口**（基线 §九 G-19），
 *       本网<b>不降</b>该判定，也不声称已覆盖。</li>
 * </ul>
 */
class FollowProtoContractTest {

    /** 基准文件在 test classpath 下的位置（与 {@code generate.py} 同目录）。 */
    private static final String BASELINE = "/proto-contract/follow-proto-contract.expected.txt";

    @Test
    @DisplayName("🔴 follow proto 契约（消息/字段号/类型/optional/repeated/map/枚举/rpc）与 Go 真源基准逐条一致")
    void protoContractMatchesGoBaseline() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        assertTrue(expected.size() > 5,
                "基准文件内容异常（只有 " + expected.size() + " 行）⇒ 生成脚本可能没跑对");
        assertEquals(expected, actual,
                "follow proto 契约与 Go 真源基准不一致 ⇒ 字段号 / 类型 / presence / 枚举值 / rpc 签名发生了漂移");
    }

    /**
     * 🔴 <b>嵌套 message 必须被单独钉住</b> —— 本域的专属回归网。
     *
     * <p>为什么单独一条：{@code follow.proto} 是本仓 10 个 proto 里
     * <b>唯一带嵌套 message</b> 的（{@code RecommendResp.RecommendUser}）
     * ⇒ 而 hot 域的生成脚本正是在这里<b>解析错</b>（内层字段被平铺进父级、
     * 父级自己的 {@code users} 字段丢失）⇒ ⇒ 这条断言就是那份错误的**专属守卫**。
     *
     * <p>三条同时成立才算过：
     * <ol>
     *   <li>父级 {@code RecommendResp} <b>带着自己的</b> {@code users=3:repeated RecommendUser}；</li>
     *   <li>嵌套类型<b>单独一行</b> {@code RecommendResp.RecommendUser}，字段号逐条钉住；</li>
     *   <li><b>没有</b>「嵌套字段被平铺进父级」的形态
     *       （父级行里不得出现 {@code target_id} / {@code mutual_score}）。</li>
     * </ol>
     */
    @Test
    @DisplayName("🔴 嵌套 message（RecommendResp.RecommendUser）必须单独成行 ⇒ 不许被平铺进父级")
    void nestedMessageIsPinnedSeparately() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        assertTrue(actual.contains("message|RecommendResp|code=1:int32|msg=2:string|users=3:repeated RecommendUser"),
                "父级 RecommendResp 必须带自己的 users=3:repeated RecommendUser 字段；实际渲染 = " + actual);
        assertTrue(actual.contains("message|RecommendResp.RecommendUser|target_id=1:int64|mutual_score=2:int32"),
                "嵌套 RecommendUser 必须单独成行（字段号逐条钉住）；实际渲染 = " + actual);

        String parent = actual.stream()
                .filter(l -> l.startsWith("message|RecommendResp|"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有 RecommendResp 行；实际渲染 = " + actual));
        assertTrue(!parent.contains("target_id") && !parent.contains("mutual_score"),
                "嵌套字段被平铺进父级了（这正是 hot 域生成脚本的解析错形态）；实际 = " + parent);

        // 基准侧同样要成立 ⇒ 否则「基准本身写错了」也会被当成通过
        assertTrue(expected.contains("message|RecommendResp.RecommendUser|target_id=1:int64|mutual_score=2:int32"),
                "基准文件里必须有嵌套类型的独立行；实际基准 = " + expected);
    }

    /**
     * 🔴 <b>判据自检（变异验证）</b> —— 证明本守门<b>不是空转</b>。
     *
     * <p>做法：把基准里<b>任意一个字段号</b> +1 ⇒ 断言它与生成码描述符<b>不再相等</b>
     * ⇒ ⇒ 「字段号真的参与了比较」这件事被钉住；
     * ⇒ 顺带证明「零命中 / 恒等比较」这种假绿形态<b>不会</b>溜过去。
     */
    @Test
    @DisplayName("🔴 判据自检：把任一字段号 +1 ⇒ 必须与生成码描述符不等（证明本守门不是空转）")
    void mutatedFieldNumberIsDetected() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        List<String> mutated = new ArrayList<>();
        boolean mutatedOne = false;
        java.util.regex.Pattern fieldNo = java.util.regex.Pattern.compile("=(\\d+):");
        for (String line : expected) {
            if (!mutatedOne && line.startsWith("message|")) {
                // 把第一个字段的字段号 +1（形如 `name=7:type` ⇒ `name=8:type`）
                java.util.regex.Matcher m = fieldNo.matcher(line);
                if (m.find()) {
                    int num = Integer.parseInt(m.group(1));
                    m.replaceFirst("=" + (num + 1) + ":");
                    mutated.add(m.group());
                    mutatedOne = true;
                    continue;
                }
            }
            mutated.add(line);
        }

        assertTrue(mutatedOne, "基准里一条 message 行都没有 ⇒ 基准本身异常");
        assertNotEquals(expected, mutated, "变异没有生效（+1 之后仍相等 ⇒ 判据自检本身坏了）");
        assertNotEquals(mutated, actual,
                "字段号 +1 之后仍然等于生成码描述符 ⇒ 本守门抓不到字段号漂移（空转）");
    }

    /**
     * 把生成码的描述符渲染成与基准<b>同一套格式</b>的文本。
     *
     * <p>格式（与 {@code generate.py} 的输出一一对应）：
     * <pre>
     * package follow
     * message|RelationReq|user_id=1:int64|target_id=2:int64
     * message|RecommendResp|code=1:int32|msg=2:string|users=3:repeated RecommendUser
     * message|RecommendResp.RecommendUser|target_id=1:int64|mutual_score=2:int32
     * service|FollowService|Follow(RelationReq)-&gt;BaseResp|...
     * </pre>
     * <p>⚠️ <b>取的是 protobuf 的 {@link Descriptors.FileDescriptor}</b>，不是 grpc-java 的
     * {@code io.grpc.ServiceDescriptor}（后者<b>不暴露</b> message / enum / 字段号 ⇒ 比不了）。
     * <br>⇒ 走任一生成 message 的 {@code getDescriptor().getFile()} ⇒ 拿到的是<b>整个 .proto 文件</b>
     * 的描述符（含全部 message / enum / service）。
     */
    private static List<String> describe() {
        Descriptors.FileDescriptor file = RelationReq.getDescriptor().getFile();
        List<String> lines = new ArrayList<>();
        lines.add("package " + file.getPackage());

        for (Descriptors.EnumDescriptor en : file.getEnumTypes()) {
            List<String> values = new ArrayList<>();
            for (Descriptors.EnumValueDescriptor v : en.getValues()) {
                values.add(v.getName() + "=" + v.getNumber());
            }
            lines.add("enum|" + en.getName() + "|" + String.join("|", values));
        }

        for (Descriptors.Descriptor msg : file.getMessageTypes()) {
            renderMessage(msg, "", lines);
        }

        for (Descriptors.ServiceDescriptor svc : file.getServices()) {
            List<String> methods = new ArrayList<>();
            for (Descriptors.MethodDescriptor m : svc.getMethods()) {
                methods.add(m.getName()
                        + "(" + m.getInputType().getName() + ")"
                        + "->" + m.getOutputType().getName());
            }
            lines.add("service|" + svc.getName() + "|" + String.join("|", methods));
        }
        return lines;
    }

    /**
     * 渲染一个 message —— <b>递归</b>渲染嵌套类型（紧跟在父行之后）。
     *
     * <p>⚠️ <b>map 的合成 entry 类型必须跳过</b>：protobuf 会为每个 map 字段生成一个
     * <b>合成</b>的嵌套类型（{@code XxxEntry}，带 {@code map_entry=true} 选项），
     * 它在 {@code .proto} 文本里<b>并不存在</b> ⇒ 若不跳过，Java 侧会多渲染一行
     * ⇒ 与基准永久不等（本域当前**没有** map 字段，但守门要为将来挡住）。
     */
    private static void renderMessage(Descriptors.Descriptor msg, String prefix, List<String> lines) {
        String name = prefix.isEmpty() ? msg.getName() : prefix + "." + msg.getName();

        List<String> fields = new ArrayList<>();
        for (Descriptors.FieldDescriptor f : msg.getFields()) {
            fields.add(f.getName() + "=" + f.getNumber() + ":" + typeToken(f));
        }
        lines.add("message|" + name + "|" + String.join("|", fields));

        for (Descriptors.Descriptor nested : msg.getNestedTypes()) {
            if (nested.getOptions().getMapEntry()) {
                continue;   // map 的合成 entry 类型 —— .proto 文本里没有它
            }
            renderMessage(nested, name, lines);
        }
    }

    /**
     * 字段的类型标记 —— 与 {@code generate.py} 的归一规则逐条对应。
     *
     * <p>⚠️ <b>map 要先判</b>：protobuf 里 map 字段的 label 也是 {@code LABEL_REPEATED}
     * ⇒ 若先判 repeated，map 会被渲染成 {@code repeated FooEntry} ⇒ 与基准对不上。
     */
    private static String typeToken(Descriptors.FieldDescriptor f) {
        if (f.isMapField()) {
            Descriptors.Descriptor entry = f.getMessageType();
            Descriptors.FieldDescriptor key = entry.findFieldByName("key");
            Descriptors.FieldDescriptor value = entry.findFieldByName("value");
            assertNotNull(key, "map 字段的 entry 没有 key");
            assertNotNull(value, "map 字段的 entry 没有 value");
            return "map<" + scalarOrNamed(key) + ", " + scalarOrNamed(value) + ">";
        }
        String base = scalarOrNamed(f);
        if (f.toProto().hasProto3Optional()) {
            // proto3 显式 presence（`optional string x = 1;`）—— 与普通 `string` 的 wire 语义不同
            return "optional " + base;
        }
        if (f.toProto().getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            return "repeated " + base;
        }
        return base;
    }

    /** 标量取小写类型名；message / enum 取<b>类型名</b>（与 .proto 文本里的写法一致）。 */
    private static String scalarOrNamed(Descriptors.FieldDescriptor fd) {
        switch (fd.getJavaType()) {
            case MESSAGE:
                return fd.getMessageType().getName();
            case ENUM:
                return fd.getEnumType().getName();
            default:
                return fd.getType().name().toLowerCase(Locale.ROOT).replace("type_", "");
        }
    }

    /** 读基准文件；**注释行（{@code #} 开头）不参与比对**（那是溯源信息，不是契约）。 */
    private static List<String> readBaseline() throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = FollowProtoContractTest.class.getResourceAsStream(BASELINE)) {
            assertNotNull(in, "找不到基准文件：" + BASELINE);
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }
}
