package com.rag.common.communityfact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public final class Wire {

    /**
     * 投递状态枚举。
     */
    public enum DeliveryStatus {
        DELIVERY_PENDING(0),
        DELIVERY_ACCEPTED(1),
        DELIVERY_FAILED(2),
        DELIVERY_BLOCKED(3);

        private final int code;

        DeliveryStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static DeliveryStatus fromCode(int code) {
            for (DeliveryStatus s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
            return DELIVERY_PENDING;
        }
    }

    /**
     * 错误：frozen envelope 被阻止投递。
     */
    public static  RuntimeException ERR_FROZEN_ENVELOPE_BLOCKED =
        new RuntimeException("community fact frozen envelope blocked");
    /**
     * 错误：authority 不可用。
     */
    public static  RuntimeException ERR_AUTHORITY_UNAVAILABLE =
        new RuntimeException("community fact authority unavailable");



    private static final Pattern RECEIPT_HASH = Pattern.compile("^[a-f0-9]{64}$");

    /**
     ObjectMapper 是 Jackson 库的类，负责 java->json 互相转化
     .configure() 是 ObjectMapper 的一个
     */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    // DTO,一个清单
    // @JsonProperty 把 java 和 json 连接起来
    public static final class Event {
        @JsonProperty("event_id") public String eventId; // 把 eventId 和 event_id 联系起来
        @JsonProperty("event_type") public String eventType;
        @JsonProperty("schema_version") public int schemaVersion;
        @JsonProperty("producer") public String producer;
        @JsonProperty("aggregate_id") public String aggregateId;
        @JsonProperty("aggregate_version") public long aggregateVersion;
        @JsonProperty("operation_id") public String operationId;
        @JsonProperty("occurred_at") public String occurredAt;
        @JsonProperty("payload") public Object payload;

        public Event() {} // 默认需要无参构造
    }


    public static final class TechnicalReceipt {
        @JsonProperty("event_id") public String eventId;
        @JsonProperty("producer") public String producer;
        @JsonProperty("technical_status") public String technicalStatus;
        @JsonProperty("receipt_id") public String receiptId;
        @JsonProperty("input_hash") public String inputHash;
        @JsonProperty("offset") public long offset;
        @JsonProperty("received_at") public String receivedAt;

        public TechnicalReceipt() {}
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static final class SubjectRef {
        @JsonProperty("issuer") public String issuer;
        @JsonProperty("subject_id") public String subjectId;
    }

    public static final class AuthorityFact {
        @JsonProperty("event") public Event event;
        @JsonProperty("subject_ref") public SubjectRef subjectRef;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("predecessor_event_id") public String predecessorEventId;

        @JsonProperty("technical_receipt") public TechnicalReceipt technicalReceipt;
        @JsonProperty("source_event_hash") public String sourceEventHash;

        public AuthorityFact() {}
    }

    /* 接口，*/
    @FunctionalInterface
    public interface Sender {
        // 发出 ctx、event、raw，返回 TechnicalReceipt
        TechnicalReceipt send(Context ctx, Event event, byte[] raw)
                throws Exception;
    }

    public static final class DCEventSender implements Sender {
        private final String endpoint;
        private final String token;
        private final HttpClient client;

        private DCEventSender(String endpoint, String token, HttpClient client) {
            this.endpoint = endpoint;
            this.token = token;
            this.client = client;
        }

        /**
         * 对应 Go 版 {@code func NewDCEventSender(endpoint, token string, client *http.Client) (*DCEventSender, error)}。
         * 校验 endpoint 协议/路径 + token 长度 + client 非空。
         */
        public static DCEventSender create(String endpoint, String token, HttpClient client) {
            if (endpoint == null || token == null || client == null || token.length() < 32) {
                throw new IllegalArgumentException("community DataCenter event sender is not configured");
            }
            URI parsed = URI.create(endpoint);
            String scheme = parsed.getScheme();
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || parsed.getHost() == null
                    || parsed.getUserInfo() != null
                    || (parsed.getQuery() != null && !parsed.getQuery().isEmpty())
                    || (parsed.getFragment() != null && !parsed.getFragment().isEmpty())
                    || !"/v1/events".equals(parsed.getPath())) {
                throw new IllegalArgumentException("community DataCenter event sender is not configured");
            }
            return new DCEventSender(parsed.toString(), token, client);
        }

        /**
         * 对应 Go 版 {@code func (s *DCEventSender) Send(ctx, event, raw) (TechnicalReceipt, error)}。
         *
         * <p>非 200/201 状态码按 Go 版语义抛 {@link DataCenterAcceptanceRejectedException}，
         * 不再返"伪 receipt"以避免调用方误把错误的 receipt 当成合法的 acceptance。
         */
        @Override
        public TechnicalReceipt send(Context ctx, Event event, byte[] raw) throws Exception {
            if (client == null) {
                throw new IllegalStateException("community DataCenter event sender is not configured");
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", event.eventId == null ? "" : event.eventId)
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(raw));
            // otel trace propagation：把当前 ctx 的 traceparent 写进 header
            // 对应 Go 版 otel.GetTextMapPropagator().Inject(ctx, propagation.HeaderCarrier(request.Header))
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(ctx, builder, BuilderSetter.INSTANCE);

            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 201 && status != 200) {
                // 对应 Go 版 io.Copy(io.Discard, io.LimitReader(response.Body, 64<<10)) 丢弃 body 防连接泄漏
                try (InputStream in = response.body()) {
                    discardUpTo(in, 64 * 1024);
                }
                throw new DataCenterAcceptanceRejectedException(status, "DataCenter event acceptance status " + status);
            }
            // 对应 Go 版 io.LimitReader(response.Body, 64<<10)：流式读取最多 64KB
            try (InputStream in = response.body()) {
                byte[] limited = readLimitUpTo(in, 64 * 1024);
                return strictDecode(new java.io.ByteArrayInputStream(limited), TechnicalReceipt.class);
            }
        }

        /**
         * 流式读取最多 {@code maxBytes} 字节；超过则截断；输入流被消费到底或抛 IOException。
         * 对应 Go 版 {@code io.LimitReader} 的语义。
         */
        private static byte[] readLimitUpTo(InputStream in, int maxBytes) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(Math.min(maxBytes, 1024));
            byte[] chunk = new byte[1024];
            int total = 0;
            while (total < maxBytes) {
                int allowed = Math.min(chunk.length, maxBytes - total);
                int n = in.read(chunk, 0, allowed);
                if (n < 0) break;
                buf.write(chunk, 0, n);
                total += n;
            }
            // 截断后丢弃剩余字节，防止 keep-alive 连接卡住
            if (total == maxBytes) {
                discardUpTo(in, Integer.MAX_VALUE);
            }
            return buf.toByteArray();
        }

        /** 对应 Go 版丢弃 body 防止连接泄漏。 */
        private static void discardUpTo(InputStream in, int maxBytes) throws IOException {
            byte[] sink = new byte[1024];
            int total = 0;
            while (total < maxBytes) {
                int n = in.read(sink, 0, Math.min(sink.length, maxBytes - total));
                if (n < 0) break;
                total += n;
            }
        }

        private static final class BuilderSetter implements TextMapSetter<HttpRequest.Builder> {
            static final BuilderSetter INSTANCE = new BuilderSetter();
            @Override
            public void set(HttpRequest.Builder carrier, String key, String value) {
                if (carrier != null) {
                    carrier.header(key, value);
                }
            }
        }
    }

    /**
     * 对应 Go 版 {@code func StrictDecode(reader io.Reader, value any) error}：
     * 用 {@code DisallowUnknownFields()} 反序列化，禁止尾部多余 JSON。
     */
    public static <T> T strictDecode(InputStream in, Class<T> valueType) throws IOException {
        try (JsonParser parser = MAPPER.getFactory().createParser(in)) {
            T result = parser.readValueAs(valueType);
            if (parser.nextToken() != null) {
                throw new IOException("trailing JSON");
            }
            return result;
        }
    }

    /** Reader 重载，方便上游按字符串反序列化时调用。 */
    public static <T> T strictDecode(Reader reader, Class<T> valueType) throws IOException {
        try (JsonParser parser = MAPPER.getFactory().createParser(reader)) {
            T result = parser.readValueAs(valueType);
            if (parser.nextToken() != null) {
                throw new IOException("trailing JSON");
            }
            return result;
        }
    }

    /**
     * 对应 Go 版 {@code func CanonicalHash(raw []byte) (string, error)}：
     * JCS 规范化 → SHA-256 → hex（RFC 8785）。
     */
    public static String canonicalHash(byte[] raw) throws Exception {
        // 对应 Go 版 github.com/cyberphone/json-canonicalization 的 JsonCanonicalizer.Transform
        // Java 等价物 org.erdtman.jcs.JsonCanonicalizer（JCS，RFC 8785）
        // 用法：new JsonCanonicalizer(byte[]) → getEncodedString() / getEncodedUTF8()
        String canonical = new JsonCanonicalizer(raw).getEncodedString();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] sum = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sum);
    }

    /**
     * 对应 Go 版 {@code func ValidateReceipt(event Event, receipt TechnicalReceipt) (time.Time, bool)}：
     * 校验 event/producer/status/receiptId/inputHash/offset，并通过 RFC3339Nano 解析 received_at。
     */
    public static Result validateReceipt(Event event, TechnicalReceipt receipt) {
        if (receipt == null || receipt.eventId == null || !receipt.eventId.equals(event.eventId)
                || receipt.producer == null || !receipt.producer.equals(event.producer)
                || !"accepted".equals(receipt.technicalStatus)
                || receipt.receiptId == null || receipt.receiptId.isEmpty()
                || !validHash(receipt.inputHash)
                || receipt.offset < 1L) {
            return new Result(null, false);
        }
        Instant received;
        try {
            received = Instant.parse(receipt.receivedAt);
        } catch (DateTimeParseException e) {
            return new Result(null, false);
        }
        return new Result(received, true);
    }

    /**
     * 对应 Go 版 {@code func ValidHash(value string) bool}：sha256 hex 校验。
     */
    public static boolean validHash(String value) {
        return value != null && RECEIPT_HASH.matcher(value).matches();
    }

    /**
     * 对应 Go 版 {@code func ParseRTWSubject(value string) (SubjectRef, bool)}：
     * 解析 "rtw.identity/platform/{uid}" 形式，校验 uid > 0 且为整数字面量。
     */
    public static Result parseRTWSubject(String value) {
        final String prefix = "rtw.identity/platform/";
        if (value == null || !value.startsWith(prefix)) {
            return new Result(null, false);
        }
        String id = value.substring(prefix.length());
        long uid;
        try {
            uid = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return new Result(null, false);
        }
        if (uid <= 0 || !String.valueOf(uid).equals(id)) {
            return new Result(null, false);
        }
        return new Result(new SubjectRef("rtw.identity", id), true);
    }

    /**
     * 对应 Go 版多返回值 (time.Time, bool) / (SubjectRef, bool) 的 Java 包装。
     */
    public static final class Result<T> {
        public final T value;
        public final boolean ok;

        public Result(T value, boolean ok) {
            this.value = value;
            this.ok = ok;
        }

        public T get() {
            return value;
        }

        public boolean isOk() {
            return ok;
        }
    }

    /**
     * DC 拒绝 frozen envelope 的 sentinel 异常。
     * <p>对应 Go 版 {@code fmt.Errorf("DataCenter event acceptance status %d", response.StatusCode)}；
     * Java 用 sentinel + 状态码字段，让业务层可以 {@code instanceof} 识别并附带 http status。
     */
    public static final class DataCenterAcceptanceRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        public DataCenterAcceptanceRejectedException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    private Wire() {
    }
}
