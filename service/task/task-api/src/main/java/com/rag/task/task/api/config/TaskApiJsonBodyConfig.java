package com.rag.task.task.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 让 {@code @RequestBody} <b>无视 Content-Type</b>照样读 body。
 *
 * <h2>为什么需要它：Go 侧根本没有「媒体类型协商」这回事</h2>
 * Go {@code api/internal/handler/task/gettaskhandler.go:19-20}：
 * <pre>
 *   decoder := json.NewDecoder(r.Body)
 *   decoder.UseNumber()
 *   if err := decoder.Decode(&amp;req); err != nil { ... }
 * </pre>
 * 裸 {@code encoding/json}，<b>没有任何 Content-Type 判据</b>。于是
 * <pre>
 *   POST /taskcenter/v1/task/get
 *     Content-Type: text/plain                （或干脆不写）
 *     body: {"id":"123"}
 *   ⇒ Go: 解析成功 → 200 {"tasks":[...]}
 * </pre>
 *
 * <h2>Spring 默认会把它变成 415（这就是不写本类时的漂移）</h2>
 * {@code @RequestBody} 走 {@code RequestResponseBodyMethodProcessor}，
 * 先在已注册的 {@code HttpMessageConverter} 里找「能读 {@code text/plain} 且目标是 POJO」的：
 * {@code StringHttpMessageConverter} 只支持 {@code String}/{@code byte[]} ⇒ 找不到
 * ⇒ {@code HttpMediaTypeNotSupportedException}（415）。
 * <b>Go 返回 200、RAG 返回 415</b> —— 一条可观察的 L1 差异，且只在「客户端 Content-Type
 * 写错时」出现，恰恰是最容易在联调时踩到的那一类。
 *
 * <h2>⚠️ 本类<b>不是</b> {@code com.rag.common.http.GoStyleJsonBodyConverter}</h2>
 * 共享的那个复刻的是 go-zero <b>{@code httpx.Parse}</b> 的 {@code withJsonBody} 判据
 * （{@code ContentLength > 0 && Content-Type 含 "application/json"}），
 * <b>不满足时丢弃整个 body</b>。那对 like / message / users 域是<b>正确</b>的（它们都走
 * {@code httpx.Parse}），搬到 task-api 就是错的：task-api 走裸 decoder，
 * 任何 Content-Type 都<b>要读</b>。共享实现在判据不满足时返回 {@code null}
 * ⇒ DTO 变成零值 ⇒ {@code id} 缺失 ⇒ 400，而 Go 是 200。
 * <p>⇒ {@code TaskApiApplication} 用 {@code excludeFilters} 把
 * {@link com.rag.common.http.GoStyleJsonBodyConverterConfig} 排除在组件扫描之外，
 * 由本类接手。<b>只排除那一个 {@code @Configuration}</b>，
 * {@code com.rag.common} 的其余组件（HTTP 中间件、四道保护闸门、404/405 形状）照常生效。
 *
 * <h2>⚠️ 必须插在 Boot 的 Jackson 转换器<b>之前</b></h2>
 * {@code extendMessageConverters} 给的是「已注册列表」，Spring 按顺序取第一个
 * {@code canRead} 为真的转换器。排在 Boot 的 Jackson 转换器<b>后面</b>就永远轮不到它，
 * 415 依旧会抛。
 * <p>位置与共享实现一致：插到<b>第一个</b> {@link JacksonJsonHttpMessageConverter} 的下标处，
 * 而不是 {@code add(0, …)}——后者会盖过 {@code ByteArray}/{@code String} 转换器。
 *
 * <h2>⚠️ 只认领<b>读</b>侧，写侧一律交回 Spring 默认转换器</h2>
 * 本模块的失败响应是 {@code text/plain}（见 {@code advice/GlobalExceptionHandler}）。
 * 若转换器的写侧也认领 {@code MediaType.ALL}，它会抢在
 * {@code StringHttpMessageConverter} 之前把裸文案<b>按 JSON 字符串写出</b> ⇒
 * body 从 {@code user_id invalid\n} 变成 {@code "user_id invalid"\n}，
 * <b>状态码与 Content-Type 都对、只有 body 被转义</b>——极具迷惑性的一类回归。
 */
@Configuration(proxyBeanMethods = false)
public class TaskApiJsonBodyConfig implements WebMvcConfigurer {

    private final JsonMapper jsonMapper;

    /**
     * ⚠️ 注入 Boot 配置好的 {@code JsonMapper}，<b>不是</b>自己 {@code new} 一个。
     * 保持与本模块其它 JSON 行为（{@code FAIL_ON_UNKNOWN_PROPERTIES} 的默认、
     * 数值绑定策略）走<b>同一个</b> mapper 实例。
     */
    public TaskApiJsonBodyConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        int insertAt = -1;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof JacksonJsonHttpMessageConverter) {
                insertAt = i;
                break;
            }
        }
        if (insertAt < 0) {
            // Boot 没注册 Jackson 转换器（例如 web 栈被排除）。不擅自插入：
            // 那会造出一个「本模块根本没打算支持 JSON」的转换器。
            return;
        }
        converters.add(insertAt, new ContentTypeAgnosticJsonBodyConverter(jsonMapper));
    }

    /**
     * 认领<b>全部</b>媒体类型的<b>读</b>侧 Jackson 转换器。
     *
     * <p>与 {@code GoStyleJsonBodyConverter} 的唯一区别：它不丢弃任何 body。
     */
    static final class ContentTypeAgnosticJsonBodyConverter extends JacksonJsonHttpMessageConverter {

        ContentTypeAgnosticJsonBodyConverter(JsonMapper mapper) {
            super(mapper);
            setSupportedMediaTypes(List.of(MediaType.ALL));
        }

        /**
         * 无条件认领所有媒体类型 —— 判据只有一个：<b>这里就是终点</b>，
         * 内容全部交给 {@code super.read} 按 JSON 解析。
         * <p>如果不放开，Spring 会在「选择转换器」阶段就抛 415，{@link #read} 根本不会被调用。
         */
        @Override
        public boolean canRead(ResolvableType type, MediaType mediaType) {
            return true;
        }

        // ---------- 写侧：三个入口全部关掉 ----------

        /**
         * ⚠️ 三参 {@code canWrite} 是 {@code SmartHttpMessageConverter} 的<b>抽象</b>方法，
         * {@code MessageWriter} 调的正是它；两参版只是它的 {@code default} 委托。
         * ⇒ 只关两参版会「看着关了其实没关」（admin-api 实测踩过：状态码与 Content-Type
         * 都对，只有 body 被 JSON 转义）。
         */
        @Override
        public boolean canWrite(ResolvableType type, Class<?> clazz, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return false;
        }

        /**
         * 保留父类读侧逻辑（按 JSON 解析），此处仅为把签名显式化：
         * Spring 7 / Jackson 3 的签名是
         * {@code read(ResolvableType, HttpInputMessage, Map)}（Jackson 2 时代是三参含 Class）。
         */
        @Override
        public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints)
                throws IOException {
            return super.read(type, inputMessage, hints);
        }
    }
}