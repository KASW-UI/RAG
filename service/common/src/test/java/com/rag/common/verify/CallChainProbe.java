package com.rag.common.verify;

import com.rag.common.logger.LogOption;
import com.rag.common.spring.LoggerFactoryBean;
import io.opentelemetry.context.Context;

/**
 * {@code call_chain} 断言的<b>调用点</b>，单独放在一个不被
 * {@code com.rag.common.logger.Logger#FILTER_PREFIXES} 过滤的包里。
 *
 * <p><b>为什么需要这个类</b>：{@code FILTER_PREFIXES} 含 {@code com.rag.common.spring.}，
 * 目的是滤掉 Bean 自身与委托层的帧。若把断言写在
 * {@code com.rag.common.spring} 的测试里，<b>测试方法自己的帧也会被滤掉</b>，
 * 于是「链首是否落在业务调用方」根本无从断言。
 *
 * <p>本类所在包 {@code com.rag.common.verify} 不在过滤清单内，因此它的方法名会出现在
 * {@code call_chain} 里，可作为「链首落点」的探针。
 */
public final class CallChainProbe {

    private CallChainProbe() {
    }

    /** 唯一的业务调用点：链首应当落在这里。 */
    public static void invokeBean(LoggerFactoryBean bean) {
        bean.logBusinessErr(Context.root(), 500, new RuntimeException("probe"), new LogOption[0]);
    }
}