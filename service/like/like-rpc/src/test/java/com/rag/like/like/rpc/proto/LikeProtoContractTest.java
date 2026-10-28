package com.rag.like.like.rpc.proto;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.rag.like.like.proto.GetLikeCountReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>like proto 的契约回归网</b> —— 把「{@code .proto} 文件两侧一致」升级为
 * 「<b>生成码的契约</b>两侧一致」。
 *
 * <h2>为什么需要它（基线 §十 #14 的降级理由）</h2>
 *
 * <p>基线原话：{@code like-proto} / {@code follow-proto} 「<b>无字段级 / wire 级仓内断言</b>
 * （只有 {@code check-surface-parity.py} 的 rpc 方法集合）⇒ R3 的 ⑤ 不满足」。
 *
 * <p>而 {@code .proto} 文件两侧一致<b>推不出</b>：
 * <ul>
 *   <li><b>字段号</b>一致（改一个号 ⇒ wire 完全变，但文件「看着一致」）；</li>
 *   <li>proto3 {@code optional} 的 <b>presence 语义</b>一致
 *       （{@code optional string} 与 {@code string} 在文件里只差三个词，wire 上却是「有没有 presence」）；</li>
 *   <li><b>repeated / map</b> 的编码方式一致；</li>
 *   <li><b>枚举值</b>编号一致（枚举值编号<b>进 wire</b>）；</li>
 *   <li><b>rpc 方法名与出入参类型</b>一致（= 服务路径 {@code /like.LikeService/*}）。</li>
 * </ul>
 * ⇒ 本类把这些<b>全部</b>钉成一条断言。
 *
 * <h2>🔴 期望值是「由真源生成」的，不是手抄的</h2>
 *
 * <p>基准文件 {@code src/test/resources/proto-contract/like-proto-contract.expected.txt}
 * 是<b>脚本从 Go 真源的 {@code proto/like.proto} 生成</b>的
 * （同目录 {@code generate.py}，头部记录了真源的 sha256）⇒
 * <ul>
 *   <li>手抄一份字段表 = 把真源的实现细节抄一遍 ⇒ <b>抄错时看不出错</b>；</li>
 *   <li>脚本生成 ⇒ 抄不错；真源变了 ⇒ 重跑脚本 ⇒ diff 直接暴露契约变更。</li>
 * </ul>
 * ⚠️ <b>基准文件不是手工编辑物</b>：真源 {@code .proto} 变了要重跑脚本 + 重新评审 diff。
 *
 * <h2>🔴 proto {@code package} 行<b>必须排除</b>（已签字偏离 SD-2026-10-23-05）</h2>
 *
 * <p>Go 侧是 {@code package pb;}，RAG 侧是 {@code package like;}
 * ⇒ 这是<b>已签字的</b>表达层归一（{@code docs/Intentional_Deviations/like/proto.md}）
 * ⇒ 归一后 gRPC 方法路径由 {@code /pb.LikeService/*} 变为 {@code /like.LikeService/*}（<b>wire 层可见</b>）。
 *
 * <p>⚠️ 签字文件里已写明：「{@code check-proto-static.sh} 若扩到 {@code like}，
 * 其 {@code verdict} <b>必须排除 {@code package} 行</b>，否则会把本条已签字偏离判成回退」。
 * ⇒ 本类照此办理：<b>基准比对排除 package 行</b>，
 * 同时<b>单独钉住</b> RAG 侧的 package 必须是签字值 {@code like}
 * ⇒ 偏离被<b>钉成「签字偏离」</b>，而不是被<b>静默忽略</b>。
 *
 * <h2>为什么放在 like-rpc 而不是 like-proto</h2>
 *
 * <p>{@code like-proto} 模块<b>一行 junit 依赖都没有</b>（pom 里只有生成代码所需的 protobuf / grpc）
 * ⇒ 放在消费方 {@code like-rpc} 更省（与 hot 域同款做法）。
 */
class LikeProtoContractTest {

    /** 基准文件在 test classpath 下的位置（与 {@code generate.py} 同目录）。 */
    private static final String BASELINE = "/proto-contract/like-proto-contract.expected.txt";

    /**
     * 已签字偏离 SD-2026-10-23-05 定的 RAG 侧 proto {@code package}。
     *
     * <p>Go 侧是 {@code pb}（遗留包名，{@code option go_package = "./pb"}）。
     */
    private static final String SIGNED_RAG_PACKAGE = "like";

    @Test
    @DisplayName("🔴 like proto 契约（消息/字段号/类型/optional/repeated/map/枚举/rpc）与 Go 真源基准逐条一致")
    void protoContractMatchesGoBaseline() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        assertTrue(expected.size() > 5,
                "基准文件内容异常（只有 " + expected.size() + " 行）⇒ 生成脚本可能没跑对");

        // 🔴 排除 package 行（已签字偏离 SD-2026-10-23-05），其余**逐行**相等。
        assertEquals(stripPackage(expected), stripPackage(actual),
                "like proto 契约与 Go 真源基准不一致 ⇒ 字段号 / 类型 / presence / 枚举值 / rpc 签名发生了漂移");
    }

    @Test
    @DisplayName("🔴 proto package 必须是已签字的归一值 like（Go 侧 pb 是已签字偏离，不得静默忽略）")
    void protoPackageIsTheSignedNormalisedValue() throws IOException {
        List<String> expected = readBaseline();
        String goPackage = packageOf(expected);

        assertEquals("pb", goPackage,
                "🔴 基准文件里的 Go 真源 package 变了 ⇒ 要重新评审 SD-2026-10-23-05 这条签字偏离");

        Descriptors.FileDescriptor file = GetLikeCountReq.getDescriptor().getFile();
        assertEquals(SIGNED_RAG_PACKAGE, file.getPackage(),
                "🔴 RAG 侧 proto package 不是签字值 " + SIGNED_RAG_PACKAGE
                        + " ⇒ 要么是有人「顺手改回 Go 的 pb」（违反签字偏离），要么是改成了别的值");
    }

    @Test
    @DisplayName("🔴 like.proto 无 proto3 optional 字段 ⇒ presence 语义与 Go 一致（显式钉死，防后人加 optional）")
    void noProto3OptionalField() {
        Descriptors.FileDescriptor file = GetLikeCountReq.getDescriptor().getFile();
        List<String> optionalFields = new ArrayList<>();
        for (Descriptors.Descriptor msg : file.getMessageTypes()) {
            for (Descriptors.FieldDescriptor f : msg.getFields()) {
                if (f.toProto().hasProto3Optional()) {
                    optionalFields.add(msg.getName() + "." + f.getName());
                }
            }
        }
        assertEquals(List.of(), optionalFields,
                "🔴 Go 真源 proto/like.proto 里**没有** proto3 optional 字段；"
                        + "RAG 侧若多出 optional ⇒ presence 语义与 Go 不同（wire 上「有值/无值」不可区分）");
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
    @DisplayName("🔴 like 域源码不得硬编码 proto 字段号（setField/FIELD_NUMBER/CodedOutputStream 等）")
    void noHardcodedProtoFieldNumbersInSource() throws IOException {
        Path scanRoot = locateLikeDomainSourceRoot();
        assertNotNull(scanRoot,
                "🔴 找不到 like 域源码目录 ⇒ 本条会退化成「永远绿」的假门 ⇒ 必须失败而不是跳过");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(scanRoot)) {
            for (Path file : files.filter(LikeProtoContractTest::isProductionSource).toList()) {
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
                "🔴 like 域源码里出现了按字段号的 protobuf 低层 API ⇒ "
                        + "契约比对（比的是声明）抓不到它，但 wire 已经变了");
    }

    /**
     * 只认<b>生产源码</b>（{@code src/main/java}）。
     *
     * <p>🔴 <b>为什么必须限定生产源码</b>（2026-10-29 实测踩到）：本判据自己的源码里就写着
     * {@code FIELD_NUMBER} / {@code CodedOutputStream} 这些模式串（写在 javadoc 与判定函数里）
     * ⇒ 若扫描范围包含 {@code src/test/java}，扫描器会<b>命中它自己</b>
     * ⇒ 每次跑都红 ⇒ <b>假红</b>。
     *
     * <p>⇒ 双重收敛：① 只扫 {@code src/main/java}（判据在 {@code src/test/java} ⇒ 天然不重叠）；
     * ② 目标目录也排除了 {@code target/}（生成码里有大量 {@code FIELD_NUMBER} 常量，
     * 那是 protoc 产物、不是人写的硬编码）。
     *
     * <p>⚠️ 口径本身也是对的：字段号硬编码只可能出现在<b>生产代码</b>里
     * ⇒ 测试代码里出现不构成生产漂移。
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
     * 「反射设 bean 字段」，与 protobuf 字段号<b>毫无关系</b>
     * ⇒ 误判会把合规代码判成红（本仓 like-api 的 {@code LikeRpcTimeoutCapTest} 就是这种写法）。
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
     * 定位 {@code service/like} 的源码根 —— 从当前工作目录向上找「含 {@code service/like} 的目录」。
     *
     * <p>surefire 的 {@code user.dir} = 当前模块目录（{@code .../service/like/like-rpc}）
     * ⇒ 向上最多 4 层即可命中仓库根。
     */
    private static Path locateLikeDomainSourceRoot() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5 && cursor != null; i++) {
            Path candidate = cursor.resolve("service/like");
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
        Descriptors.FileDescriptor file = GetLikeCountReq.getDescriptor().getFile();
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
        try (InputStream in = LikeProtoContractTest.class.getResourceAsStream(BASELINE)) {
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

    /** 🔴 排除 {@code package} 行 —— 已签字偏离 SD-2026-10-23-05（Go {@code pb} → RAG {@code like}）。 */
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
