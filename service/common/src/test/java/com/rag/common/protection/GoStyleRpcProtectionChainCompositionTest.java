package com.rag.common.protection;

import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Metadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门：保护链的<b>链构成</b>可被<b>只读</b>查询入口观测（G2 反馈单 FB-03）。
 *
 * <h2>这条守门守的是什么</h2>
 * <p>{@code GoStyleRpcProtectionChain.build} 的链构成由<b>两个条件分支</b>决定：
 * <ul>
 *   <li>shedding 要 {@code enabled && maxCpuPermille() > 0}；</li>
 *   <li>breaker 要 {@code enabled}。</li>
 * </ul>
 * ⇒ ⇒ 链的构成是<b>配置相关</b>的，不是固定的。
 * <p>⇒ 域级守门若想<b>分别</b>断言「breaker 在链上」「shedding 在链上」，
 * 就需要一个能看见构成的入口；否则只能靠「关掉降载来换取确定性」间接判断
 * ⇒ ⇒ <b>断言被迫退化</b>：名义上写 breaker/shedding，实际只证明了 breaker。
 *
 * <h3>Go 出处</h3>
 * <pre>
 *   zrpc/server.go:157-159   Breaker  —— Middlewares.Breaker 默认 true
 *   zrpc/server.go:160-163   Shedding —— CpuThreshold &gt; 0 才挂
 * </pre>
 *
 * <h2>🔴 本守门同时锁住「单一来源」这条纪律</h2>
 * <p>{@code compose(props)} 是构成的<b>唯一计算点</b>，{@code build} 内部用的就是它
 * ⇒ ⇒ 「配置推出的构成」与「实际构造出的链」<b>不可能不一致</b>。
 * <p>⇒ 本类的第 ④ 条断言把这条性质<b>钉死</b>：任何一边偷偷改条件 ⇒ 立刻红。
 */
@DisplayName("🔴 保护链的链构成可被只读入口观测（FB-03 · 零行为改动）")
class GoStyleRpcProtectionChainCompositionTest {

    /** 一个什么都不做的业务拦截器（代表最内层的业务观测拦截器）。 */
    private static final ServerInterceptor BUSINESS = new ServerInterceptor() {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    };

    @Test
    @DisplayName("① 默认配置 ⇒ 链上三层齐全（recover → breaker → shedding），且 compose 与实际链一致")
    void defaultConfigHasAllThreeLayers() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();

        GoStyleRpcProtectionChain.Composition composition = GoStyleRpcProtectionChain.compose(props);
        assertTrue(composition.recover(), "recover 恒在（Go: Middlewares.Recover 默认 true）");
        assertTrue(composition.breaker(), "breaker 默认在（Go: Middlewares.Breaker 默认 true）");
        assertTrue(composition.shedding(), "降载默认在（Go: CpuThreshold 900 > 0）");

        assertEquals(List.of("recover", "breaker", "shedding"),
                GoStyleRpcProtectionChain.layerNames(
                        GoStyleRpcProtectionChain.build(BUSINESS, props)),
                "实际构造出的链的层名（由外到内）必须与 compose() 一致");
    }

    @Test
    @DisplayName("② 关掉熔断 ⇒ 链上只剩 recover + shedding（域级守门可据此**分别**断言两层）")
    void breakerOffRemovesOnlyBreaker() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getBreaker().setEnabled(false);

        GoStyleRpcProtectionChain.Composition composition = GoStyleRpcProtectionChain.compose(props);
        assertFalse(composition.breaker(), "熔断已关");
        assertTrue(composition.shedding(), "降载不受影响（两个开关互相独立）");

        assertEquals(List.of("recover", "shedding"),
                GoStyleRpcProtectionChain.layerNames(
                        GoStyleRpcProtectionChain.build(BUSINESS, props)),
                "关掉熔断只应去掉 breaker 一层");
    }

    @Test
    @DisplayName("③ 关掉降载 ⇒ 链上只剩 recover + breaker（**不牵连**熔断层）")
    void sheddingOffRemovesOnlyShedding() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        GoStyleRpcProtectionChain.Composition composition = GoStyleRpcProtectionChain.compose(props);
        assertFalse(composition.shedding(), "降载已关");
        assertTrue(composition.breaker(), "熔断不受影响（这正是原来守门断言退化的那一条）");

        assertEquals(List.of("recover", "breaker"),
                GoStyleRpcProtectionChain.layerNames(
                        GoStyleRpcProtectionChain.build(BUSINESS, props)),
                "关掉降载只应去掉 shedding 一层");
    }

    @Test
    @DisplayName("④ 降载开着但阈值 = 0 ⇒ 仍然不挂（Go: zrpc/server.go:160-163 的 CpuThreshold > 0 才挂）")
    void zeroThresholdDoesNotAttachShedding() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setCpuThreshold(0L);

        assertEquals(0L, props.maxCpuPermille(), "阈值 0 ⇒ maxCpuPermille 为 0");
        assertFalse(GoStyleRpcProtectionChain.compose(props).shedding(),
                "Go 侧 CpuThreshold &gt; 0 才挂降载 ⇒ 阈值 0 时不挂");

        assertEquals(List.of("recover", "breaker"),
                GoStyleRpcProtectionChain.layerNames(
                        GoStyleRpcProtectionChain.build(BUSINESS, props)),
                "阈值 0 ⇒ 链上没有降载层");
    }

    @Test
    @DisplayName("⑤ 四种开关组合下，compose() 与实际构造出的链**逐一吻合**（单一来源）")
    void compositionAlwaysAgreesWithTheBuiltChain() {
        for (boolean breakerOn : new boolean[]{true, false}) {
            for (boolean sheddingOn : new boolean[]{true, false}) {
                GoStyleProtectionProperties props = new GoStyleProtectionProperties();
                props.getBreaker().setEnabled(breakerOn);
                props.getCpuShedding().setEnabled(sheddingOn);

                GoStyleRpcProtectionChain.Composition composition = GoStyleRpcProtectionChain.compose(props);
                List<String> actual = GoStyleRpcProtectionChain.layerNames(
                        GoStyleRpcProtectionChain.build(BUSINESS, props));

                assertEquals(composition.layerNames(), actual,
                        "compose() 与实际链必须一致（breaker=" + breakerOn
                                + ", shedding=" + sheddingOn + "）");
            }
        }
    }

    @Test
    @DisplayName("⑥ 只读性：查询入口不改链、不改配置，重复查询结果相同")
    void queryIsReadOnlyAndRepeatable() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        ServerInterceptor chain = GoStyleRpcProtectionChain.build(BUSINESS, props);

        List<String> first = GoStyleRpcProtectionChain.layerNames(chain);
        List<String> second = GoStyleRpcProtectionChain.layerNames(chain);

        assertEquals(first, second, "重复查询结果必须相同（只读入口）");
        assertEquals(List.of("recover", "breaker", "shedding"), first,
                "查询不得改变链的构成");
        assertTrue(GoStyleRpcProtectionChain.compose(props).shedding(),
                "查询不得改变配置推出的构成");
    }

    @Test
    @DisplayName("⑦ 最内的业务拦截器**不在**层名列表里（它不是保护层）")
    void businessInterceptorIsNotALayer() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        List<String> names = GoStyleRpcProtectionChain.layerNames(
                GoStyleRpcProtectionChain.build(BUSINESS, props));

        assertFalse(names.contains("business"), "业务拦截器不属于保护链的层");
        assertEquals(3, names.size(), "默认配置下保护链恰好三层");
    }
}
