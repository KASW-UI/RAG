package com.rag.message.message.rpc;

import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.rpc.support.GrpcTestLifecycle;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **优雅拆除的守门**（`DRIFT-MSG-V-032` 加固的**变异验证载体** · 2026-10-30）
 *
 * <h2>为什么需要这条独立守门</h2>
 * <p>{@code DRIFT-MSG-V-032} 的症状是<b>偶发</b>的（2026-10-27 全量并发回归里偶发一次
 * {@code UNAVAILABLE: Network closed for unknown reason}）⇒ ⇒
 * 「跑一次绿」<b>证明不了</b>加固生效，「不绿」也未必是加固失效。
 * <p>⇒ 2026-10-30 实测：把加固<b>回退</b>成旧的 {@code shutdownNow()} 之后，
 * 该测试类<b>照样 2/2 绿</b> ⇒ ⇒ <b>单次跑对这条加固完全无判别力</b>
 * ⇒ ⇒ 必须有一条<b>能机械检出</b>「优雅关闭真的生效」的守门。
 *
 * <h2>本守门的判别式</h2>
 * <p>用 gRPC-java 的<b>两种关闭语义</b>的<b>可观察差异</b>做判据：
 * <ul>
 *   <li><b>优雅关闭</b>（{@code shutdown()}）：<b>在途调用正常完成</b>
 *       （grpc-java 文档：不再接受新调用，<b>已有调用继续跑完</b>）；</li>
 *   <li><b>硬拆</b>（{@code shutdownNow()}）：在途调用<b>立刻被取消</b>
 *       ⇒ 客户端收到 {@code CANCELLED} / {@code UNAVAILABLE}。</li>
 * </ul>
 * ⇒ 所以：让 handler <b>短暂占用</b>（在途）⇒ 调
 * {@link GrpcTestLifecycle#shutdownGracefully} ⇒ 断言<b>这次在途调用成功返回</b>。
 * <p>⇒ 变异验证：把 {@code shutdownGracefully} 降级成 {@code shutdownNow()}
 * ⇒ 在途调用被取消 ⇒ <b>本守门红</b> ⇒ 证明判据<b>不是恒真</b>。
 *
 * <h2>⚠️ 为什么必须测 {@code GrpcTestLifecycle} 这个正身</h2>
 * <p>§3.3：守门必须测「生产用的那个函数」⇒ 若关闭逻辑复制一份到守门里，
 * 两份可以分叉 ⇒ 「你测的是 A、跑的是 B」⇒ 故本守门直接调
 * {@link GrpcTestLifecycle#shutdownGracefully}，与
 * {@link MessageRpcSlowThresholdGateTest} <b>共用同一个函数</b>。
 *
 * <h2>本守门不验什么</h2>
 * <p>它<b>不</b>断言 slow 标记 / 阈值比较 / 业务语义（那是
 * {@link MessageRpcSlowThresholdGateTest} 的职责）⇒ ⇒ 本类<b>不引入新的行为口径</b>，
 * 只保证「被测环境的拆除时序」这一层是健康的。
 */
class MessageRpcSlowThresholdTeardownGateTest {

    /** 被测 handler 的占用时长（毫秒）—— 必须显著大于 0，使「在途」成立。 */
    private static final long HANDLER_HOLD_MILLIS = 300L;

    @Test
    @DisplayName("优雅关闭时，在途调用必须**正常完成**（⇒ 证明用的不是 shutdownNow 硬拆）")
    void inFlightCallMustCompleteAcrossGracefulShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        ServerCallHandler<MarkNotificationReq, BaseResp> handler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    entered.countDown();
                    try {
                        // 🔴 短暂占用 ⇒ 让这次调用在关闭发生时**真的还在飞**
                        Thread.sleep(HANDLER_HOLD_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    completed.set(true);
                    observer.onNext(BaseResp.getDefaultInstance());
                    observer.onCompleted();
                });
        io.grpc.ServiceDescriptor onlyMarkRead = io.grpc.ServiceDescriptor
                .newBuilder(MessageServiceGrpc.getServiceDescriptor().getName())
                .addMethod(MessageServiceGrpc.getMarkNotificationReadMethod())
                .build();
        ServerServiceDefinition service = ServerServiceDefinition.builder(onlyMarkRead)
                .addMethod(MessageServiceGrpc.getMarkNotificationReadMethod(), handler)
                .build();

        Server server = ServerBuilder.forPort(0).addService(service).build().start();
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            // 🔴 在途调用：**异步** stub ⇒ 拿到 Future ⇒ 不等它返回 ⇒ 拆除发生时它**仍在飞**
            //    ⚠️ 必须用 async stub：blocking stub 的 get() 是同步的 ⇒ 拿不到「在途」这个状态。
            var pending = MessageServiceGrpc.newFutureStub(channel)
                    .withDeadlineAfter(20, TimeUnit.SECONDS).markNotificationRead(
                            MarkNotificationReq.newBuilder().setUserId(1L).setNotificationId(2L).build());

            // 前提断言：handler 必须**真的被调用过** ⇒ 否则「在途」不成立 ⇒ 判据空转
            assertTrue(entered.await(10, TimeUnit.SECONDS),
                    "handler 必须真的被调用过 ⇒ 否则这次调用不在途 ⇒ 本守门判据空转。");

            // 🔴 核心动作：优雅关闭（先 channel 后 server）⇒ 在途调用应**自然收尾**
            GrpcTestLifecycle.shutdownGracefully(channel, server);

            // 核心断言：在途调用必须**成功返回**
            // ⚠️ 若把 shutdownGracefully 降级成 shutdownNow() ⇒ 在途调用被取消
            //    ⇒ 这里抛 StatusRuntimeException（CANCELLED / UNAVAILABLE）⇒ **本守门红**
            BaseResp resp = pending.get(20, TimeUnit.SECONDS);
            assertTrue(resp != null, "在途调用必须正常返回（优雅关闭 ⇒ 不取消在途调用）");
            assertTrue(completed.get(),
                    "handler 必须**跑完**（被优雅关闭等到了）⇒ 若为 false，说明在途调用被中途取消。");
        } finally {
            // 兜底：万一上面抛了异常，这里强制拆干净（不影响判据，判据在 try 内已完成）
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
