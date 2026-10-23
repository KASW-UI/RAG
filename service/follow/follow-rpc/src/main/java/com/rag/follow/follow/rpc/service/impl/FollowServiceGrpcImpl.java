package com.rag.follow.follow.rpc.service.impl;

import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.FollowServiceGrpc;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.RelationReq;
import com.rag.follow.follow.proto.UserListResp;
import com.rag.follow.follow.rpc.service.FollowDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * gRPC 服务入口 —— 对应 Go 端 {@code service/follow/rpc/internal/server/followserviceserver.go}：
 * 8 个方法，每个方法只有两行（构造 logic + 转发），<b>server 层没有任何业务逻辑</b>。
 *
 * <p>业务全在 {@link FollowDomainService}。本类唯一职责是
 * <b>把业务层抛出的 {@code StatusException} 原样写上 wire</b>。
 *
 * <h2>⚠️ 错误出口：必须 {@code onError(...)}，不能让它逃逸</h2>
 *
 * <p>grpc-java 在 {@code UnaryServerCallListener.onHalfClose} 里调用业务方法。
 * 若在那里 {@code throw}，异常会逃出流监听回调，被当成<b>传输层内部错误</b>，
 * description 被替换成 {@code "Application error processing RPC"}
 * —— <b>业务文案当场全丢</b>。
 *
 * <p>而 follow 域的业务码<b>唯一的载体就是 description 文案</b>：
 * {@code FollowGrpcError} 把 bizCode 翻译成英文文案写进 description，
 * follow-api 再靠 {@code BIZ_CODE_BY_MSG} 反查还原（见
 * {@code FollowGrpcError} 类注释的表格：5201 / 5202 / 5101 / 5007 / 5008
 * <b>这 5 类全靠文案反查</b>才能还原出正确业务码）。
 * ⇒ description 一丢，follow-api 拿到的就只剩 switch 兜底的粗粒度码
 * （全部退化成 5003 / 5005 / 5002 / 5001）。这正是 {@code onError} 不可省的原因。
 *
 * <h2>⚠️ 兜底必须是 5001 而不是「把异常原文发给客户端」</h2>
 * <p>原生异常消息会泄漏 SQL / 连接串等内部信息。兜底路径把任何非业务异常
 * （含 NPE、序列化失败、DB 驱动异常未在业务层转换的漏网之鱼）
 * 折叠成 {@code INTERNAL + "internal server error"}，
 * 与 Go 的 {@code BizCodeFromError} 默认分支（{@code → CodeServerBusy/1015}）<b>码值不同</b>，
 * 但两者的<b>可见面相同</b>：都不把内部细节外泄。
 * 之所以取 5001 而不是 1015，是因为 5001 有专门的业务文案、且是 follow-api
 * switch 兜底能落到 {@code ErrorServerCommon} 的那个码。
 */
@GrpcService
@Component
public class FollowServiceGrpcImpl extends FollowServiceGrpc.FollowServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(FollowServiceGrpcImpl.class);

    private final FollowDomainService domainService;

    public FollowServiceGrpcImpl(FollowDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void follow(RelationReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domainService.follow(request), responseObserver);
    }

    @Override
    public void unfollow(RelationReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domainService.unfollow(request), responseObserver);
    }

    @Override
    public void block(RelationReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domainService.block(request), responseObserver);
    }

    @Override
    public void unblock(RelationReq request, StreamObserver<BaseResp> responseObserver) {
        invoke(() -> domainService.unblock(request), responseObserver);
    }

    @Override
    public void getFollowList(ListReq request, StreamObserver<UserListResp> responseObserver) {
        invoke(() -> domainService.getFollowList(request), responseObserver);
    }

    @Override
    public void getBlockList(ListReq request, StreamObserver<UserListResp> responseObserver) {
        invoke(() -> domainService.getBlockList(request), responseObserver);
    }

    @Override
    public void getFollowerList(ListReq request, StreamObserver<UserListResp> responseObserver) {
        invoke(() -> domainService.getFollowerList(request), responseObserver);
    }

    @Override
    public void getRecommendations(ListReq request, StreamObserver<RecommendResp> responseObserver) {
        invoke(() -> domainService.getRecommendations(request), responseObserver);
    }

    /**
     * 统一出口模板。
     *
     * <p><b>两层 catch 的顺序不可调换</b>：
     * <ol>
     *   <li>{@link StatusRuntimeException} —— <b>业务错误的正常载体</b>。
     *       领域层经 {@code FollowRpcSupport.rpcError(...)} 构造，它持有的
     *       {@code Status} 与 follow-common 的 {@code GRPCError} 产出<b>逐字段相同</b>
     *       （见该方法的 javadoc：为什么不直接抛 checked 的 {@code StatusException}）。
     *       这里<b>原样上 wire</b>，不再包一层 —— 二次包装会破坏 description，
     *       而 description 正是 follow 域业务码的唯一载体。</li>
     *   <li>{@link RuntimeException} —— 兜底 5001，绝不把异常原文发给客户端
     *       （原生消息会泄漏 SQL / 连接串）。</li>
     * </ol>
     *
     * <p>⚠️ <b>兜底没有单独 catch {@link StatusException}</b>：领域层已全部转成
     * unchecked 形式，checked 的那一个不可能到这里。若将来新增代码直接抛 checked 版本，
     * 编译期就会拦下（无法塞进 {@link Supplier}），而不是静默走兜底丢掉业务码。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (StatusRuntimeException business) {
            observer.onError(business);
        } catch (RuntimeException e) {
            LOG.error("follow-rpc handler failed", e);
            observer.onError(FollowGrpcError.grpcError(
                            Status.Code.INTERNAL, FollowErrorCode.ERROR_SERVER_COMMON.getCode())
                    .getStatus().asRuntimeException());
        }
    }
}