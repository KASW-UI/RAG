package com.rag.like.like.rpc.exception;

import com.rag.like.like.common.errmsg.LikeErrorCode;

/**
 * like-rpc 业务异常集合 —— 承载 Go 侧各处 {@code errmsg.NewGrpcErr(code, msg)} 的那两个参数。
 *
 * <p>采用「持有式 + 嵌套静态类」而不是「每种异常一个文件」，与本项目
 * {@code message-rpc/exception/MessageExceptions.java} 保持同一约定。
 *
 * <h2>为什么要先落成领域异常，再由 gRPC 边界转换</h2>
 *
 * <p>Go 的 logic 直接 {@code return nil, errmsg.NewGrpcErr(...)}，因为 Go 的一等公民就是
 * 「返回值里带 error」。Java 没有这个语言机制，等价物只有两条路：
 * <ul>
 *   <li>方法签名 {@code throws} 业务异常 —— 每个调用点都被迫写 try/catch，
 *       而本域这些方法还要在 gRPC 与 MQ 两条链路间复用；</li>
 *   <li>unchecked 领域异常 + <b>单一出口转换</b> —— 业务码的「取值点」与「上线路径」分开，
 *       由 {@code LikeServiceGrpcImpl} 一处转成 {@code LikeGrpcError.grpcError(bizCode, msg)}。</li>
 * </ul>
 * 选后者：wire 上最终仍是<b>同一个业务码 + 同一句原文</b>，可观察行为不变。
 *
 * <p>⚠️ <b>msg 一律用调用点的原文，不查表</b>。Go 侧两者在多处不同：
 * code=5003 查表得「查询数据库失败」，而 logic 里传的是「DB查询失败」。
 * 这个差值对调用方可见，必须原样搬运（查表会把它改掉）。
 */
public final class LikeExceptions {

    private LikeExceptions() {
        // 异常工厂，禁止实例化
    }

    /**
     * 携带 like 业务码的领域异常。
     *
     * <p>{@link #getMessage()} 采用与 {@code CodeError} / {@code LikeGrpcError} description
     * 同一套 {@code ErrCode:%d, Errmsg:%s} 格式，便于全链路 grep。
     */
    public static class Rpc extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int bizCode;

        /** <b>原始</b>文案（不含 {@code ErrCode:…} 前缀）。 */
        private final String msg;

        public Rpc(int bizCode, String msg) {
            super(String.format("ErrCode:%d, Errmsg:%s", bizCode, msg));
            this.bizCode = bizCode;
            this.msg = msg;
        }

        public Rpc(int bizCode, String msg, Throwable cause) {
            super(String.format("ErrCode:%d, Errmsg:%s", bizCode, msg), cause);
            this.bizCode = bizCode;
            this.msg = msg;
        }

        /** like 业务码，取值见 {@link LikeErrorCode}。 */
        public int getBizCode() {
            return bizCode;
        }

        /**
         * <b>原始</b>文案 —— gRPC 边界必须用这个，不能用 {@link #getMessage()}。
         *
         * <p>{@link #getMessage()} 是给日志看的包装格式（{@code ErrCode:X, Errmsg:Y}）；
         * 若把它当成 msg 传给 {@code LikeGrpcError.grpcError(bizCode, msg)}，
         * 会得到 {@code ErrCode:X, Errmsg:ErrCode:X, Errmsg:Y} 的<b>双重包装</b>，
         * 客户端拿到的 msg 与 Go 不一致。
         */
        public String getMsg() {
            return msg;
        }
    }

    // ==================== 与 Go 侧调用点一一对应的工厂 ====================

    /**
     * 对应 {@code getlikecountlogic.go:44} / {@code getlikestatelogic.go:45}：
     * {@code errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "输入需要查询的ID为空")}。
     */
    public static Rpc inputWrong(String msg) {
        return new Rpc(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), msg);
    }

    /**
     * 对应 {@code getusertotallikelogic.go:41}：
     * {@code errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "非法的用户ID")}。
     *
     * <p>与 {@link #inputWrong} 同码不同文案 —— Go 就是这样，合并会改掉调用方看到的 msg。
     */
    public static Rpc illegalUserId(String msg) {
        return new Rpc(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), msg);
    }

    /** 对应 {@code likeactionlogic.go:172}：{@code errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "参数异常")}。 */
    public static Rpc invalidActionParam(String msg) {
        return new Rpc(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), msg);
    }

    /** 对应 {@code errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")} —— <b>读路径</b> DB 失败。 */
    public static Rpc dbSelect(String msg) {
        return new Rpc(LikeErrorCode.ERROR_DB_SELECT.getCode(), msg);
    }

    /**
     * 对应 {@code likeactionlogic.go:199}：
     * {@code errmsg.NewGrpcErr(errmsg.ErrorRedisUpdate, "Redis更新失败")}。
     *
     * <p><b>只有写路径的 Redis 失败才致命</b>。读路径的 Redis 失败一律被降级成 cache miss
     * 并回源 DB（见 {@code LikeDomainService}），不走这里。这个不对称是 Go 的刻意设计。
     */
    public static Rpc redisUpdate(String msg) {
        return new Rpc(LikeErrorCode.ERROR_REDIS_UPDATE.getCode(), msg);
    }
}