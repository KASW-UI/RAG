package com.rag.common.http;

import com.rag.common.web.ConditionalOnApiSide;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 把 {@link GoStyleJsonBodyConverter} 插到 Spring Boot 默认 Jackson 转换器<b>之前</b>。
 *
 * <h2>为什么是「插在它前面」而不是「add(0)」</h2>
 *
 * <p>本转换器认领 {@code * / *}，若直接 {@code add(0, …)}，它会盖过
 * {@code ByteArrayHttpMessageConverter} / {@code StringHttpMessageConverter}。
 * 虽然本仓库当前没有 raw {@code String} / {@code byte[]} 请求体（已 grep 确认），
 * 但那是<b>现状</b>而不是<b>约束</b> —— 一个后来者随手加一个 {@code @RequestBody String}
 * 就会静默改变行为。
 * ⇒ 只插到<b>第一个</b> {@link JacksonJsonHttpMessageConverter} 的位置，影响面限定在 JSON 相关请求上。
 *
 * <h2>为什么保留 Boot 的那个转换器不删</h2>
 *
 * <p>它是本转换器未覆盖路径的兜底（本转换器只覆写读侧、写侧完全委托给父类）。
 * 删掉它会让「响应序列化」少一层保护。
 * <p>⚠️ <b>2026-10-30 · G4 lane：本类属 api 侧 rest 中间件 ⇒ 已挂 {@link com.rag.common.web.ConditionalOnApiSide}</b>
 * ⇒ ⇒ 此前「rpc 域不注册本类」的依据是<b>域侧不扫 {@code com.rag.common} 这道约定</b>（不是本类的条件）
 * ⇒ ⇒ 现由共享层自己兜住 ⇒ ⇒ Go 出处与语义等价论证见该注解。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnApiSide
public class GoStyleJsonBodyConverterConfig implements WebMvcConfigurer {

    private final JsonMapper jsonMapper;

    /**
     * ⚠️ 注入 Boot 配置好的 {@code JsonMapper}，<b>不是</b>自己 new 一个。
     * 理由见 {@link GoStyleJsonBodyConverter} 的类注释：各 api 模块的
     * {@code ,string} / {@code options=1|2|3|4} 语义都在这个 mapper 上。
     */
    public GoStyleJsonBodyConverterConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        int insertAt = -1;
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> c = converters.get(i);
            if (c instanceof JacksonJsonHttpMessageConverter) {
                insertAt = i;
                break;
            }
        }
        if (insertAt < 0) {
            // Boot 没有注册 Jackson 转换器（例如模块刻意排除了 web 栈）。
            // 此时不擅自插入 —— 那会造出一个「本模块根本没打算支持 JSON」的转换器。
            return;
        }
        converters.add(insertAt, new GoStyleJsonBodyConverter(jsonMapper));
    }
}