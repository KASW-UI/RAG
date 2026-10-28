package com.rag.hot.hot.rpc.proto;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>hot proto 的契约回归网</b>（A7）—— 把「{@code .proto} 文件两侧一致」升级为
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
 *   <li><b>rpc 方法名与出入参类型</b>一致（= 服务路径 {@code /hot.HotService/GetHotArticles}）。</li>
 * </ul>
 * ⇒ 本类把这些**全部**钉成一条断言。
 *
 * <h2>🔴 期望值是「由真源生成」的，不是手抄的</h2>
 * <p>基准文件 {@code src/test/resources/proto-contract/hot-proto-contract.expected.txt}
 * 是<b>脚本从 Go 真源的 {@code proto/hot.proto} 生成</b>的
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
 * <b>编译产物</b>的契约是什么 ⇒ 这里走 {@link HotServiceGrpc#getServiceDescriptor()}
 * ⇒ 拿到的是<b>真正参与 wire 编码</b>的那份描述符。
 *
 * <h2>为什么放在 hot-rpc 而不是 hot-proto</h2>
 * <p>{@code hot-proto} 模块<b>一行 junit 依赖都没有</b>（pom 里只有生成代码所需的
 * protobuf / grpc）⇒ 往那儿加测试要先改 pom；放在消费方 {@code hot-rpc} 更省，
 * 且 proto 本来就是「给 rpc 用的」（与 favorite 域同款做法）。
 *
 * <h2>⚠️ 覆盖边界</h2>
 * <ul>
 *   <li><b>不覆盖</b> gRPC 帧 / HTTP2 帧（那需要两侧同时起服务）；</li>
 *   <li><b>不覆盖</b> wire 字节级（favorite 域有 {@code tools/proto-wire-probe/favorite/}
 *       那样的字节级探针；hot 域本轮只做契约层）。</li>
 * </ul>
 */
class HotProtoContractTest {

    /** 基准文件在 test classpath 下的位置（与 {@code generate.py} 同目录）。 */
    private static final String BASELINE = "/proto-contract/hot-proto-contract.expected.txt";

    @Test
    @DisplayName("🔴 A7：hot proto 契约（消息/字段号/类型/optional/repeated/map/枚举/rpc）与 Go 真源基准逐条一致")
    void protoContractMatchesGoBaseline() throws IOException {
        List<String> expected = readBaseline();
        List<String> actual = describe();

        assertTrue(expected.size() > 5,
                "基准文件内容异常（只有 " + expected.size() + " 行）⇒ 生成脚本可能没跑对");
        assertEquals(expected, actual,
                "hot proto 契约与 Go 真源基准不一致 ⇒ 字段号 / 类型 / presence / 枚举值 / rpc 签名发生了漂移");
    }

    /**
     * 把生成码的描述符渲染成与基准<b>同一套格式</b>的文本。
     *
     * <p>格式（与 {@code generate.py} 的输出一一对应）：
     * <pre>
     * package hot
     * message|GetHotArticlesRequest|top_k=1:int32
     * message|GetHotArticlesResponse|items=1:repeated HotArticleItem
     * service|HotService|GetHotArticles(GetHotArticlesRequest)-&gt;GetHotArticlesResponse|...
     * </pre>
     * <p>⚠️ <b>取的是 protobuf 的 {@link Descriptors.FileDescriptor}</b>，不是 grpc-java 的
     * {@code io.grpc.ServiceDescriptor}（后者<b>不暴露</b> message / enum / 字段号 ⇒ 比不了）。
     * <br>⇒ 走任一生成 message 的 {@code getDescriptor().getFile()} ⇒ 拿到的是<b>整个 .proto 文件</b>
     * 的描述符（含全部 message / enum / service）。
     */
    private static List<String> describe() {
        Descriptors.FileDescriptor file = GetHotArticlesRequest.getDescriptor().getFile();
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
        try (InputStream in = HotProtoContractTest.class.getResourceAsStream(BASELINE)) {
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
