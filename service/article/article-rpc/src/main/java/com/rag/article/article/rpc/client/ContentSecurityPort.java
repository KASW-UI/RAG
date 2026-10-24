package com.rag.article.article.rpc.client;

/**
 * 内容安全审计端口 —— 对应 Go 端 {@code mqs/article_consumer.go:153-206} 的
 * {@code auditArticle} 里的 <b>文本</b>部分（{@code SecurityRpc.SanitizeContent}）
 * 与 <b>图片</b>部分（{@code ImageSecurityRpc.DetectImageAd}）。
 *
 * <h2>🔴 为什么是接口而不是 {@code @GrpcClient} stub（诚实说明，不是偷懒）</h2>
 * Go 侧的 {@code security.rpc} 是<b>另一个微服务</b>的 gRPC 面，它的消息类型由
 * {@code proto/security.proto} 生成。本仓（RAG）当前<b>没有 {@code service/security} 模块</b>
 * ——
 * <ul>
 *   <li>{@code service/pom.xml} 的 {@code <modules>} 里没有 {@code security}；
 *       注释写明 {@code task / favorite / follow / points / security} 五个模块的
 *       工作区目录<b>已被删除</b>（{@code git status} 显示为 staged deletion）。</li>
 *   <li>{@code ~/.m2} 里的 {@code com.rag:security-proto} 是一个<b>空壳 jar</b>
 *       （只有 META-INF，无任何生成的 class），不能作为依赖使用。</li>
 * </ul>
 *
 * 而<b>创建 security 模块属于本组件范围之外</b>：那是另一个微服务（需要它自己的
 * rpc / api / proto 三件套与 DDL），AGENTS.md §20 要求「不要顺手修无关模块」。
 *
 * <p>⇒ 因此本组件声明<b>窄端口</b>（两个方法，签名照 Go 的调用点逐字段对齐），
 * 具体 gRPC 实现留给 security 模块落地后接入。
 * 这不是"先造个接口以后再说"——
 * <b>这个接口的形状就是 wire 契约的最小充分投影</b>，
 * 且它是本组件<b>唯一</b>与 security 交互的边界：
 * 把外部依赖收敛到一个端口后，审核流程的其余部分（幂等、状态机、事务）
 * 都可以脱离 security 单独验证。
 *
 * <h2>⚠️ 端口方法的语义必须与 Go 调用点逐条对齐</h2>
 * <table border="1">
 *   <caption>Go 调用点 → 端口方法</caption>
 *   <tr><th>Go</th><th>端口</th><th>不可改的点</th></tr>
 *   <tr>
 *     <td>{@code article_consumer.go:158-166}<br>SanitizeContent</td>
 *     <td>{@link #sanitizeText}</td>
 *     <td>四个开关<b>全部为 true</b>：ad / html / unicode / whitespace</td>
 *   </tr>
 *   <tr>
 *     <td>{@code article_consumer.go:167-170}<br>err != nil ⇒ return err</td>
 *     <td>抛 {@link SecurityAuditException}</td>
 *     <td><b>RPC 失败必须抛出</b>（触发 Kafka 重投），不可吞</td>
 *   </tr>
 *   <tr>
 *     <td>{@code article_consumer.go:171-173}<br>!Success ⇒ error</td>
 *     <td>抛 {@link SecurityAuditException}</td>
 *     <td>Success=false 也算失败，<b>与 RPC 错误同等对待</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code article_consumer.go:174-182}<br>IsAd ⇒ REJECTED</td>
 *     <td>{@link TextAuditResult#isAd()}</td>
 *     <td>文本判广告 ⇒ 拒稿并停（不继续查图）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code article_consumer.go:184-203}<br>逐图 DetectImageAd</td>
 *     <td>{@link #detectImageAd}</td>
 *     <td>阈值 <b>0.7</b>、启用文字提取；任一张是广告 ⇒ 拒稿</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 这两条失败路径必须抛出而不是返回 false</b>：Go 侧
 * {@code auditArticle} 返回 error 会让 {@code Consume} 返回 error ⇒ Kafka <b>不确认</b> ⇒ 重投。
 * 若在 Java 侧改成"返回 false 表示审核不可用"，就会变成
 * "审核不可用时放行"——那是一条<b>全新的、危险的</b>放行路径。
 */
public interface ContentSecurityPort {

    /**
     * 对应 Go {@code SecurityRpc.SanitizeContent}。
     *
     * @param text 待审正文（MinIO 里读到的 markdown 原文）
     * @return 审核结果；<b>RPC 失败或 {@code Success == false} 时抛异常</b>
     * @throws SecurityAuditException RPC 失败、{@code Success == false}，
     *                                或 security 客户端不可用
     */
    TextAuditResult sanitizeText(String text);

    /**
     * 对应 Go {@code ImageSecurityRpc.DetectImageAd}。
     *
     * @param imageDataUri {@code data:<mime>;base64,<...>} 形式的图片
     * @return 审核结果；<b>RPC 失败或 {@code Success == false} 时抛异常</b>
     * @throws SecurityAuditException 同上
     */
    ImageAuditResult detectImageAd(String imageDataUri);

    /** 对应 Go {@code security.SanitizeContentResponse} 中本组件实际读取的四个字段。 */
    final class TextAuditResult {

        private final boolean success;
        private final boolean ad;
        private final float adConfidence;
        private final String sanitizedText;
        private final String errorMessage;

        /**
         * @param success 对应 Go 的 {@code result.Success}（article_consumer.go:171-173）。
         *                  <b>false 一定要抛错重投</b>，不是「返回一个正常结果」。
         * @param ad      对应 Go 的 {@code result.IsAd}。
         */
        public TextAuditResult(boolean success, boolean ad, float adConfidence,
                               String sanitizedText, String errorMessage) {
            this.success = success;
            this.ad = ad;
            this.adConfidence = adConfidence;
            this.sanitizedText = sanitizedText;
            this.errorMessage = errorMessage;
        }

        /**
         * Go 侧的 {@code if !result.Success { return fmt.Errorf(...) }}（article_consumer.go:171-173）。
         *
         * <p>⚠️ 这个字段之前在本接口<b>不存在</b>，导致 Go 的这条分支在 Java 侧
         * <b>无处放置</b>：未来接上真实实现时，「调用成功但业务失败」会被当成
         * 「不是广告」放行 ⇒ <b>fail-open</b>。现在 {@link ArticleReviewConsumer#auditArticle}
         * 已强制消费它。
         *
         * @see SecurityAuditException
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 对应 Go {@code result.IsAd} —— 唯一影响业务分支的字段。
         *
         * <p>⚠️ Go 只读 {@code IsAd}，<b>不</b>使用 {@code SanitizedText}
         * （审核不会改写正文）。本类保留 {@link #getSanitizedText()} 仅供排障。
         */
        public boolean isAd() {
            return ad;
        }

        public float getAdConfidence() {
            return adConfidence;
        }

        public String getSanitizedText() {
            return sanitizedText;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /** 对应 Go {@code imagesecurity.DetectImageAdResponse} 中本组件实际读取的三个字段。 */
    final class ImageAuditResult {

        private final boolean success;
        private final boolean ad;
        private final float adConfidence;

        /**
         * @param success 对应 Go 的 {@code resp.GetSuccess()}（article_consumer.go:263-265）。
         *                  <b>false 一定要抛错重投</b>（整篇重投，不是跳过这张图）。
         */
        public ImageAuditResult(boolean success, boolean ad, float adConfidence) {
            this.success = success;
            this.ad = ad;
            this.adConfidence = adConfidence;
        }

        /** @see TextAuditResult#isSuccess() */
        public boolean isSuccess() {
            return success;
        }

        /** 对应 Go {@code resp.IsAd}。 */
        public boolean isAd() {
            return ad;
        }

        /** 对应 Go {@code resp.AdConfidence}。⚠️ Go 读取它但<b>不用于判定</b>（阈值在服务端）。 */
        public float getAdConfidence() {
            return adConfidence;
        }
    }

    /**
     * 审核不可用 / 审核判定为失败。
     *
     * <p><b>必须是 unchecked</b>：它要穿过 {@code Consume} 的方法签名向上传播
     * 到 Kafka listener，由 listener 抛出以触发重投。
     * 若声明成 checked exception，会强迫调用方写 try/catch，
     * 而<b>漏写 catch 就等于把"重投"变成"丢弃"</b>——那是一条静默丢消息路径。
     */
    class SecurityAuditException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public SecurityAuditException(String msg) {
            super(msg);
        }

        public SecurityAuditException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}