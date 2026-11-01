package com.rag.message.message.rpc.proto;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.util.JsonFormat;
import com.rag.message.message.proto.NotificationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>message proto 的契约回归网</b> —— 把「{@code .proto} 文件两侧一致」升级为
 * 「<b>生成码的契约</b>两侧一致」。
 *
 * <h2>为什么需要它（AGENTS.md §3.4 的效力边界）</h2>
 *
 * <p>§3.4 只保证 {@code .proto} <b>文件本身</b>两侧一致 ⇒ <b>推不出</b>：
 * <ul>
 *   <li><b>字段号</b>一致（改一个号 ⇒ wire 完全变，但文件「看着一致」）；</li>
 *   <li>proto3 {@code optional} 的 <b>presence 语义</b>一致
 *       （{@code optional string} 与 {@code string} 在文件里只差三个词，
 *       wire 上却是「有没有 presence」）；</li>
 *   <li><b>repeated / map</b> 的编码方式一致；</li>
 *   <li><b>枚举值</b>编号一致（枚举值编号<b>进 wire</b>）；</li>
 *   <li><b>rpc 方法名与出入参类型</b>一致（= 服务路径）。</li>
 * </ul>
 * ⇒ 本类把这些<b>全部</b>钉成断言。
 *
 * <h2>🔴 期望值是「由真源生成」的，不是手抄的</h2>
 *
 * <p>基准文件 {@code src/test/resources/proto-contract/message-proto-contract.expected.txt}
 * 是<b>脚本从 Go 真源的 {@code proto/message.proto} 生成</b>的
 * （同目录 {@code generate.py}，头部记录了真源的 sha256）⇒
 * 手抄一份字段表 = 把真源的实现细节抄一遍 ⇒ <b>抄错时看不出错</b>。
 * <p>⚠️ <b>基准文件不是手工编辑物</b>：真源 {@code .proto} 变了要重跑脚本 + 重新评审 diff。
 *
 * <h2>⚠️ 基准脚本的已知边界（嵌套 message）</h2>
 *
 * <p>本目录的 {@code generate.py} 是 {@code like} 域那份（<b>非贪婪正则</b>抓 message 体）
 * ⇒ 它<b>不解析嵌套 message</b>。
 * <p>⇒ 本 proto <b>一旦新增嵌套 message</b>，基准会失真 ⇒ 本类会<b>变红</b>（这是期望行为：
 * 红了就说明「该重跑生成脚本并重新评审」），<b>但不会假绿</b>。
 * <p>⇒ 若确需支持嵌套 message，请改用 {@code follow} 域那份（括号配对 + 递归解析）。
 *
 * <h2>🔴 proto {@code package} 两侧一致</h2>
 *
 * <p>Go 侧 {@code message} = RAG 侧 {@code message} ⇒
 * 本类把 {@code package} 行<b>一并纳入</b>逐行比对（不豁免）。
 *
 * <h2>为什么放在 message-rpc 而不是 message-proto</h2>
 *
 * <p>{@code message-proto} 模块<b>一行 junit 依赖都没有</b>（pom 里只有生成代码所需的
 * protobuf / grpc）⇒ 放在消费方 {@code message-rpc} 更省（与 article / hot / like / follow 域同款做法）。
 */
class MessageProtoContractTest {

    /** 基准文件在 test classpath 下的位置（与 {@code generate.py} 同目录）。 */
    private static final String BASELINE = "/proto-contract/message-proto-contract.expected.txt";

    /** Go 真源 proto 的 {@code package}（基准文件首行 {@code package} 之后那行）。 */
    private static final String GO_PACKAGE = "message";

    /** RAG 侧 proto 的 {@code package}。 */
    private static final String RAG_PACKAGE = "message";

    /** 本域 proto package 是否两侧不一致（不一致 ⇒ 排除 package 行 + 单独钉住签字值）。 */
    private static final boolean PACKAGE_DIVERGES = false;

    @Test
    @DisplayName("🔴 message proto 契约（消息/字段号/类型/optional/repeated/map/枚举/rpc）与 Go 真源基准逐条一致")
    void protoContractMatchesGoBaseline() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        // 🔴 结构性 sanity（**不是行数启发式**）：本域 proto 大小差异极大
        // （task 只有 5 行 ⇒ 「> 5 行」这种阈值会把**正确**的基准判成异常），
        // ⇒ 改为钉「基准必须含 package 行 + 至少一条 message + 至少一条 service」。
        assertTrue(expected.size() >= 4,
                "基准文件内容异常（只有 " + expected.size() + " 行）⇒ 生成脚本可能没跑对");
        assertTrue(expected.stream().anyMatch(l -> l.startsWith("package ")),
                "🔴 基准文件里没有 package 行 ⇒ 生成脚本可能没跑对");
        assertTrue(expected.stream().anyMatch(l -> l.startsWith("message|")),
                "🔴 基准文件里没有 message 行 ⇒ 生成脚本可能没跑对");
        assertTrue(expected.stream().anyMatch(l -> l.startsWith("service|")),
                "🔴 基准文件里没有 service 行 ⇒ 生成脚本可能没跑对");

        if (PACKAGE_DIVERGES) {
            // 🔴 排除 package 行（已签字偏离），其余**逐行**相等。
            assertEquals(stripPackage(expected), stripPackage(actual),
                    "message proto 契约与 Go 真源基准不一致 ⇒ 字段号 / 类型 / presence / 枚举值 / rpc 签名发生了漂移");
        } else {
            assertEquals(expected, actual,
                    "message proto 契约与 Go 真源基准不一致 ⇒ 字段号 / 类型 / presence / 枚举值 / rpc 签名发生了漂移");
        }
    }

    @Test
    @DisplayName("🔴 message proto package 必须是{@code message}（Go 侧 message ⇒ 两侧一致）")
    void protoPackageIsTheSignedNormalisedValue() throws IOException {
        List<String> expected = readBaseline();
        assertEquals(GO_PACKAGE, packageOf(expected),
                "🔴 基准文件里的 Go 真源 package 变了 ⇒ 要重新评审 message 域 proto package 的签字偏离");

        Descriptors.FileDescriptor file = NotificationItem.getDescriptor().getFile();
        assertEquals(RAG_PACKAGE, file.getPackage(),
                "🔴 RAG 侧 proto package 不是签字值 " + RAG_PACKAGE
                        + " ⇒ 要么是有人「顺手改回 Go 的 " + GO_PACKAGE + "」（违反签字偏离），要么是改成了别的值");
    }

    /**
     * 🔴 【PROTO 型 ①】<b>本 proto 没有 proto3 {@code optional} 字段</b> ⇒ presence 语义与 Go 一致。
     *
     * <p><b>为什么必须钉死</b>：Go 真源 {@code message.proto} 里<b>没有</b> {@code optional} 字段；
     * RAG 侧若<b>多出</b> {@code optional} ⇒ 该字段在 wire 上从「无 presence」变成「有 presence」
     * ⇒ 与 Go 侧<b>不同形</b>（Go 的 {@code string} 与 RAG 的 {@code optional string} 不能互换）。
     * <p>⇒ 本条把「没有 optional」这条事实钉成断言，<b>防后人顺手加 optional</b>。
     */
    @Test
    @DisplayName("🔴 message.proto 无 proto3 optional 字段 ⇒ presence 语义与 Go 一致（显式钉死，防后人加 optional）")
    void noProto3OptionalField() {
        Descriptors.FileDescriptor file = NotificationItem.getDescriptor().getFile();
        List<String> optionalFields = new ArrayList<>();
        for (Descriptors.Descriptor msg : file.getMessageTypes()) {
            for (Descriptors.FieldDescriptor f : msg.getFields()) {
                if (f.toProto().hasProto3Optional()) {
                    optionalFields.add(msg.getName() + "." + f.getName());
                }
            }
        }
        assertEquals(List.of(), optionalFields,
                "🔴 Go 真源 proto/message.proto 里**没有** proto3 optional 字段；"
                        + "RAG 侧若多出 optional ⇒ presence 语义与 Go 不同（wire 上「有值/无值」不可区分）");
    }

    /**
     * 🔴 【PROTO 型 ①·生成码侧】<b>生成码确实<b>没有</b>为 title 生成 {@code hasXxx()}</b>
     * ⇒ presence <b>没有被凭空加出来</b>。
     *
     * <p><b>为什么用反射钉</b>：描述符上的 {@code hasProto3Optional()} 只说明「{@code .proto} 里写了 optional」，
     * 而<b>生成码</b>才是真正参与 wire 编码的那份 ⇒ 必须看生成码<b>有没有</b> {@code hasXxx()}。
     *
     * <p>实测依据（protobuf-java 3.25.5）：proto3 <b>普通标量</b> ⇒ 生成码<b>只有</b> {@code getXxx()} /
     * {@code setXxx()}，<b>没有</b> {@code hasXxx()}；
     * 只有 {@code optional} 标量与 message 类型才有 {@code hasXxx()}。
     */
    @Test
    @DisplayName("🔴 message 生成码不得为普通标量 title 生成 hasXxx()（无 presence ⇒ 与 Go 同形）")
    void generatedCodeHasNoPresenceApiForPlainScalar() {
        assertThrows(NoSuchMethodException.class,
                () -> NotificationItem.class.getMethod("hasTitle"),
                "🔴 NotificationItem.title 是 proto3 普通 string（无 optional）"
                        + " ⇒ 生成码**不该**有 hasTitle()；有 ⇒ presence 语义与 Go 不同");

        Method getter = null;
        try {
            getter = NotificationItem.class.getMethod("getTitle");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("生成码缺少 getTitle() ⇒ 生成码与基准不是同一份契约", e);
        }
        assertNotNull(getter, "生成码缺少 getTitle()");
    }


    /**
     * 🔴 【PROTO 型 ③】<b>JSON 形态：普通标量<b>没有</b> presence ⇒ 设成默认值也<b>不出现</b>。</b>
     *
     * <p>用 protobuf 官方 {@code JsonFormat.printer().omittingInsignificantWhitespace()} 序列化<b>生成码</b>（不是手搓 JSON）⇒
     * 断言的是「<b>presence 在 JSON 上的可观察差异</b>」：
     * <p>⚠️ <b>必须用 omittingInsignificantWhitespace</b>：默认 printer 输出<b>带缩进换行</b>的
     * pretty JSON（空 message 也不是紧凑的空对象）⇒ 断言无法逐字比对。
     * <ul>
     *   <li>普通标量 {@code title}：设成默认值（""）⇒ JSON 里<b>没有</b>这个 key
     *       ⇒ 与 Go 的「非指针 + omitempty」同形；</li>
     *   <li>普通标量设成非默认值 ⇒ JSON 里<b>有</b>这个 key。</li>
     * </ul>
     *
     * <p>⚠️ 这正是 Go 的 {@code encoding/json} 对 {@code string} vs {@code *string} 的输出差异<b>同源</b>之处：
     * Go 侧 {@code *string}（指针）⇒ 即使指向 "" 也<b>会</b>输出（除非带 omitempty 且为 nil）；
     * Go 侧 {@code string} + omitempty ⇒ "" <b>不</b>输出。
     * <p>⇒ proto3 {@code optional} ⇒ 等价于 Go 的<b>指针</b>；proto3 普通标量 ⇒ 等价于 Go 的<b>值 + omitempty</b>。
     */
    @Test
    @DisplayName("🔴 message proto JSON 形态：普通标量 title 无 presence（设默认值也不出现）")
    void jsonFormHasNoPresenceForPlainScalar() throws Exception {
        // ① 设成默认值 ⇒ key 不出现（无 presence）
        String emptyJson = JsonFormat.printer().omittingInsignificantWhitespace().print(
                NotificationItem.newBuilder().setTitle("").build());
        assertEquals("{}", emptyJson,
                "🔴 NotificationItem 的 title 是 proto3 普通 string ⇒ 设成默认值后 JSON 里**不该**出现该 key"
                        + "（有 ⇒ 出现了 Go 侧不存在的 presence 语义）");

        // ② 设成非默认值 ⇒ key 出现
        String filledJson = JsonFormat.printer().omittingInsignificantWhitespace().print(
                NotificationItem.newBuilder().setTitle("t").build());
        assertTrue(filledJson.contains("\"title\":\"t\""),
                "🔴 NotificationItem 的 title 设成非默认值后 JSON 里应出现 " + "\"title\":\"t\""
                        + " ⇒ 实际得到 " + filledJson);
    }

    /**
     * 🔴 【PROTO 型 ④】<b>字段号无硬编码覆盖</b> —— 源码层扫描。
     *
     * <p>为什么必须查：protobuf 生成码允许用 {@code setField(FieldDescriptor, value)} /
     * {@code writeTo(CodedOutputStream)} 这类<b>按字段号</b>的低层 API 绕过生成的方法
     * ⇒ 一旦有人在业务代码里硬编码字段号，{@code .proto} 与生成码的契约比对<b>照样绿</b>
     * （它比的是<b>声明</b>），而 wire 已经变了。
     *
     * <p>⚠️ <b>扫描根必须真的找到</b>：找不到源码树时<b>直接失败</b>，不静默跳过
     * ⇒ 否则本条会退化成「永远绿」的假门（AGENTS.md §3.3.3）。
     */
    @Test
    @DisplayName("🔴 message 域源码不得硬编码 proto 字段号（setField/FIELD_NUMBER/CodedOutputStream 等）")
    void noHardcodedProtoFieldNumbersInSource() throws IOException {
        Path scanRoot = locateDomainSourceRoot();
        assertNotNull(scanRoot,
                "🔴 找不到 message 域源码目录 ⇒ 本条会退化成「永远绿」的假门 ⇒ 必须失败而不是跳过");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(scanRoot)) {
            for (Path file : files.filter(MessageProtoContractTest::isProductionSource).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (isProtobufLowLevelUsage(line)) {
                        violations.add(scanRoot.relativize(file) + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
        assertEquals(List.of(), violations,
                "🔴 message 域源码里出现了按字段号的 protobuf 低层 API ⇒ "
                        + "契约比对（比的是声明）抓不到它，但 wire 已经变了");
    }

    /**
     * 只认<b>生产源码</b>（{@code src/main/java}）。
     *
     * <p>🔴 <b>为什么必须限定生产源码</b>：本判据自己的源码里就写着
     * {@code FIELD_NUMBER} / {@code CodedOutputStream} 这些模式串（写在 javadoc 与判定函数里）
     * ⇒ 若扫描范围包含 {@code src/test/java}，扫描器会<b>命中它自己</b> ⇒ 每次跑都红 ⇒ <b>假红</b>。
     * <p>⇒ 双重收敛：① 只扫 {@code src/main/java}（判据在 {@code src/test/java} ⇒ 天然不重叠）；
     * ② 目标目录也排除了 {@code target/}（生成码里有大量 {@code FIELD_NUMBER} 常量，
     * 那是 protoc 产物、不是人写的硬编码）。
     */
    private static boolean isProductionSource(Path path) {
        String p = path.toString();
        return p.endsWith(".java")
                && p.contains(File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator);
    }

    /**
     * 判「protobuf 的按字段号低层用法」。
     *
     * <p>⚠️ <b>刻意排除 {@code ReflectionTestUtils.setField}</b>：那是 Spring 的
     * 「反射设 bean 字段」，与 protobuf 字段号<b>毫无关系</b> ⇒ 误判会把合规代码判成红。
     */
    private static boolean isProtobufLowLevelUsage(String line) {
        String trimmed = line.trim();
        if (trimmed.contains("ReflectionTestUtils.setField") || trimmed.contains("ReflectionTestUtils.getField")) {
            return false;
        }
        return trimmed.contains("FIELD_NUMBER")
                || trimmed.contains("setField(FieldDescriptor")
                || trimmed.contains("getField(FieldDescriptor")
                || trimmed.contains("CodedOutputStream")
                || trimmed.contains("UnknownFieldSet")
                || trimmed.contains("getUnknownFields()")
                || trimmed.contains(".writeTo(")
                || trimmed.contains("mergeFrom(");
    }

    /**
     * 定位本域的源码根 —— 从当前工作目录向上找「含 {@code service/message} 的目录」。
     *
     * <p>surefire 的 {@code user.dir} = 当前模块目录 ⇒ 向上最多 5 层即可命中仓库根。
     */
    private static Path locateDomainSourceRoot() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path candidate = cursor.resolve("service/message");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    // ==================================================================================
    // 基准 ↔ 生成码描述符 的同一套渲染
    // ==================================================================================

    /**
     * 把生成码的描述符渲染成与基准<b>同一套格式</b>的文本。
     *
     * <p>⚠️ 取的是 protobuf 的 {@link Descriptors.FileDescriptor}（<b>真正参与 wire 编码</b>的那份），
     * 不是 grpc-java 的 {@code io.grpc.ServiceDescriptor}（后者<b>不暴露</b> message / enum / 字段号）。
     */
    private static List<String> describe() {
        Descriptors.FileDescriptor file = NotificationItem.getDescriptor().getFile();
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
            List<String> fields = new ArrayList<>();
            for (Descriptors.FieldDescriptor f : msg.getFields()) {
                fields.add(f.getName() + "=" + f.getNumber() + ":" + typeToken(f));
            }
            lines.add("message|" + msg.getName() + "|" + String.join("|", fields));
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
        try (InputStream in = MessageProtoContractTest.class.getResourceAsStream(BASELINE)) {
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

    /** 🔴 排除 {@code package} 行 —— 仅在 package 两侧不一致（已签字偏离）时启用。 */
    private static List<String> stripPackage(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("package ")) {
                out.add(line);
            }
        }
        return out;
    }

    private static String packageOf(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("package ")) {
                return line.substring("package ".length()).trim();
            }
        }
        return null;
    }
}
